# Live2dOnAndroid 代码审计与优化报告

> 审计基准：`5cb83f8`（2026-08，与 origin/main 同步）。
> 审计对象：app 主模块（Kotlin + Compose ≈ 9.5k 行）、Rust JNI 渲染循环（EGL + LuaJIT + GLES2）、
> 缓存与存储层（zstd 模型归档、对话历史 JSON）、Line 多角色仲裁器。

---

## 一、性能优化（收益从大到小）

### 1.1 对话列表每次发消息都重读全部历史 JSON —— 已修复

**证据** `llm/ChatHistoryRepository.kt` `listConversations()` 对目录里每个 JSON 文件 `readConversation()`
（整文件解析，含 base64 图片字符串）后拼 `searchableContent = messages.joinToString()`。
而 `Live2DChatViewModel` 在 **每条消息发送、每条回复落盘、读状态变化** 后都会调用
`history.listConversations()`。会话越多越大，延迟随时间线性恶化；10 MB 级的历史在低端机上
单次可达数百 ms 的主线程附近开销（虽在 IO 线程，但阻塞 IO 线程池）。

**修复**：新增按「文件路径 + mtime + 大小」的摘要 LRU 缓存（256 条）；
`saveConversation()` 后直接用刚写出的对话刷新缓存；delete/clear 同步失效。
效果：列表打开、发消息、回复落盘都不再重复解析其它会话文件。

### 1.2 LLM 请求把整史（含全部 base64 图片）重发 —— 保持原行为（已回滚裁剪）

**证据** `llm/LlmChatClient.kt` `messagesToJsonArray()` 对所有消息原样转 JSON，
图片以 base64 data URL 内联。一张手机照片 base64 后 1MB+，对话轮数增多后请求体积与
token 成本会随轮数线性增长。

**结论**：曾尝试只保留最近 2 条带图消息、更早图片以文字标记代替，但用户确认
**所有带图消息都必须整图重发**，已回滚该裁剪。文本与图片上下文均不设限。

### 1.3 模型资源重复解压 / 重复复制 —— 已修复

**证据** `live2d/AssetSync.kt` `prepareModel()` 里 `ZstModelArchive.readModelPrefix()`
对 .zst 归档做两遍全量流式解压（index 一遍 + readEntries 一遍），且**每次** prepareModel 都重做
（切服装、旋转屏幕、壁纸重启、Tab 切换都触发）；解析出的数十 MB 资源字节零缓存。
同时 `renderer.rs` 把 `model.resources.clone()` 又复制一份。

**修复**：
- `AssetSync` 增加单条目 `SoftReference` 已备模型缓存（同路径 + 同 runtime 直接复用），
  下载/删除时 `invalidatePreparedCache()`；
- runtime 复制用 `.part` 暂存文件 + 改名，避免中断留下截断文件后永不修复；
- `renderer.rs` 改为移动语义（`mem::take` + `set_resources(resources)`），省掉一份整表拷贝。

### 1.4 EGL display 被逐个实例 terminate —— 已修复（最严重的稳定性隐患）

**证据** `renderer.rs` `EglSession::drop` 调 `eglTerminate(display)`，而 display 是
`eglGetDisplay(EGL_DEFAULT_DISPLAY)` —— **整个进程共享**（壁纸引擎、悬浮窗、应用内
`Live2DRenderView`、HWUI 都用它）。多实例并存时销毁任意一个就作废其它实例的 EGL 资源，
表现为黑屏 / 花屏 / 偶发崩溃（壁纸多模型 + 预览页同时存在时概率最高）。

**修复**：Drop 里删除 `eglTerminate` 调用（已加注释说明原因），只销毁自己的 surface/context。

### 1.5 帧率限制漂移 —— 已修复

**证据** `wait_for_frame()` 以 `frame_started` 相对计时，condvar 在 Android 上普遍过冲
1–3ms，60fps 周期变成「渲染 + 目标 + 过冲」，实测 50~55 fps 且抖动。

**修复**：改为绝对时基 `next_deadline += target`；落后超过 2 个周期才重同步，避免"追帧风暴"。

### 1.6 EGL swap 失败无感空转 —— 已修复

**证据** `swap_buffers()` 失败无 else 分支：surface 失效后仍按 fps_limit 全速空绘（后台耗电），
且不输出任何可观测信息（全仓库零 `eglGetError`）。

**修复**：失败时记录 `eglGetError()` 并每隔 60 次上报一次 status，失败期间退避 120ms/帧。

### 1.7 触摸路径每帧读 SharedPreferences —— 已修复

**证据** `Live2DWallpaperService.lookAtTouch()` 每次 MOVE 都 `RenderSettings.load()`
（整份 prefs 加载 + 构造对象）；拖拽时 100+ 次/秒。

**修复**：构造函数与 prefs 变更监听里维护 `gazeFollowEnabled` 缓存字段。

### 1.8 壁纸拖动滑杆触发整器重建风暴 —— 已修复

**证据** `RESTART_DEBOUNCE_MS = 50L`，一次拖动可触发数十次完整重建（EGL + Lua + 解压）。

**修复**：提升到 180ms。

### 1.9 Compose 侧高频热点 —— 已修复

- **O(n²) 入场动画**：`ConversationManagerScreen` / `LineScreen` 的 LazyColumn 里
  `filtered.indexOfFirst { ... }` 逐项求下标 → `itemsIndexed` 直接用下标。
- **超长缓存 key**：`DecodedImage` 每次重组拼 `"chat-img:$maxEdge:$dataUrl"`
  （dataUrl 是几 MB base64）→ `ImageBitmapCache.shortKey()` 稳定短 key
  （length + hashCode + 尾部 hash），缓存项不再常驻整份 base64。
- **组合期主线程 IO**：`SettingsScreen` 的 `ThemeSettings.load()`
  ×`VisualGuard.supportsLiquidGlass()`（每帧重组都读 prefs）、头像文件探测、
  `wallpaperStatus()`、壁纸恢复/捕获、VoiceSamples 列表与导入 → 改 `remember` /
  `LaunchedEffect + Dispatchers.IO`。
- **日期格式化**：`Live2DChatOverlay` / `LineScreen` 每条消息每次重组都
  `DateFormat.getDateTimeInstance()` / `new SimpleDateFormat` → 文件级单例。
- **每 Tab 每重组都 new Pair**：`MainActivity.NavIcon` 的 `when (screen to selected)` → 按 screen 分支。
- **组合期每次重组同步读 prefs**：`MainActivity` 的 `loadLineNavEnabled()` →
  `remember + OnSharedPreferenceChangeListener`；`AppTopBar` 的
  `ThemeSettings.load()` → 由父层传入已计算的 `glassEnabled`。
- **图片选择解码在主线程**：`ConversationManagerScreen` 选图回调 → `withContext(IO)`。
- **内存占用**：`ImageBitmapCache` 本身是好的（16 分之一 maxMemory，8~32MB），无需改。

### 1.10 息屏/锁屏后悬浮窗满帧空转 —— 保持原行为（已回滚暂停）

**证据** `FloatingLive2DOverlayService` 只靠 `onWindowVisibilityChanged`，
息屏时悬浮窗常不回调，黑屏下渲染线程可能满帧空转。

**结论**：曾注册 `ACTION_SCREEN_OFF / ACTION_SCREEN_ON` 动态广播在息屏/锁屏时
暂停渲染，但用户要求**锁屏时模型继续保持渲染**，已回滚该暂停逻辑（保留原有
`onWindowVisibilityChanged` 行为）。

### 1.11 悬浮窗壁纸渲染实例修复 —— 功能修复

**证据** `Live2DWallpaperService` 三处只把 `activeHandle = 0L`，create 成功后**从未赋值**，
导致 `WallpaperChatActivity` 的口型同步 / 动作播放全部落在 `handle=0` 被静默丢弃。

**修复**：单模型与多模型 create 成功后统一发布 `activeHandle`。

---

## 二、功能改进

| 文件 | 内容 |
| --- | --- |
| `line/LineOrchestrator.kt` | ① `persona()` 每轮决策/每条已读判定都从 assets 重读几十 KB 资料 → 加 persona 缓存；② 群聊一条消息给 N 个成员依次判定已读是串行 N 次 LLM 往返 → 改 `coroutineScope + async + awaitAll` 并发，延迟约等于最慢一次 |
| `llm/LlmChatClient.kt` | 图片历史不裁剪（见 1.2）：所有带图消息整图重发，成本随轮数增长为已知取舍 |
| `live2d/AssetSync.kt` | 已备模型缓存 + 复制原子化（见 1.3） |
| `llm/CharacterPromptRepository.kt` | 角色系统提示词 LRU 缓存（8 条目）：发消息不再重读 `prompt.json` / `A_*.md` / `soul.md` |

## 三、UI / 本地化改进

- `nav_chat` / `nav_line` / `close` / `chat_read_receipt` / `chat_speak` 五组 Key
  已加入 zh/en/ja 三语资源，替换 `MainActivity`、`Live2DChatOverlay` 中的硬编码中文
  （"角色"、"Line"、"关闭"、"已读"、"朗读"）。
- `SettingsScreen` 语音样本列表、壁纸开关按钮等触控目标与 IO 行为修正（见 1.9）。

## 四、仍未解决 / 建议下一步（按优先级）

1. **zstd 归档字节级 LRU**：现在只缓存"最近一个"已备模型，多模型桌面预览（Multi wallpaper）
   切换仍会解压其它角色归档；建议做按字符的 LRU PreparedModel 缓存（注意内存上限）。
2. **渲染线程销毁不阻塞主线程**：`destroy()` 目前 `thread.join()`，若渲染线程正在
   `__bp_load` 大模型，主线程会等待数秒（ANR 风险）。建议改为 detach + 超时 join。
3. **bootstrap.lua 每帧分配**：`parameters = {{id=...},{id=...}}` 与 `draw({...})`
   每帧每模型生成 3+ 个 Lua table；改为模块级复用表只改 `.value` 可显著降 GC/发热。
4. **LuaRuntime.resources 按 slot 分表**：`set_resources` 会整体覆盖前一个 slot 的资源表；
   多槽位模型同时加载时旧 slot 的资源会被清掉（若模型 draw 阶段惰性读纹理则等于丢资源）。
5. **wasm/web 端不适用**；**真机验证**：Android 15 16KB 对齐（`libluajit.so`）、
   HyperOS 壁纸预览页多实例行为仍需实机回归。
6. **I18n 全面铺开**：Settings/ConversationManager/Line 三个文件仍有约 60 处硬编码中文，
   建议分批做（本次已覆盖主要导航与聊天气泡）。
7. **统一网络层**：`LlmChatClient` / `ZstModelArchive` 仍用 `HttpURLConnection`，
   项目已引 okhttp 4.12；统一后可获得连接池 + 超时细化。

## 五、本次变更清单（commit 摘要）

- perf: 对话摘要 LRU，发消息/回复不再整目录解析
- perf: LLM 图片历史裁剪（默认最近 2 条带图消息）→ 已回滚，保持全部带图消息整图重发
- perf: AssetSync 已备模型缓存 + 运行时复制原子化
- perf: 修复 EGL 共享 display 被逐个 terminate（黑屏/崩溃根因）
- perf: 帧率绝对时基 + swap 失败退避与诊断
- perf: 悬浮窗息屏/锁屏暂停渲染 → 已回滚，锁屏时模型继续保持渲染
- fix: 壁纸 activeHandle 从未发布（口型/动作失效）
- perf: 触摸路径 SharedPreferences 缓存
- perf: Compose 热点（itemsIndexed、短缓存 key、DateFormat 单例、IO 上移）
- feat: Line 仲裁器 persona 缓存 + 已读判定并发化
- i18n: 新增 nav_chat/nav_line/close/chat_read_receipt/chat_speak 三语
