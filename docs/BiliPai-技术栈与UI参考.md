# BiliPai 参考项目：技术栈与 UI/动效设计参考文档

> 用途：为 Live2dOnAndroid 项目提供技术选型、UI 风格与动效设计的可读参考。
> 来源仓库：https://github.com/jay3-yy/BiliPai
> 分析日期：2026-08-30
> 上游版本：v0.2.3-beta.19（versionCode 331，README 更新 2026-08-15）
> 许可：非商业许可，仅学习参考

---

## 1. 项目定位

BiliPai 是一个**原生、纯净、可扩展的第三方 Bilibili Android 客户端**：

- 基于 Kotlin（100%）与 Jetpack Compose
- 覆盖视频、番剧、直播、动态、消息、离线缓存等日常主流程
- 强调播放体验、原生体验（Material You / Miuix / 液态玻璃）、插件扩展与大屏适配
- 插件生态：内置插件（10 个）+ JSON 规则插件 + 外部 .bpplugin 包 + 源码级插件

对我们项目最有参考价值的是它的 **design-system（设计系统）模块**和 **UI 设计规范文档**，而不是整个业务规模。

---

## 2. 技术栈总览

| 类别 | 选型 | 版本/说明 |
|---|---|---|
| 语言 | Kotlin | 2.4.0（AGP 9 内建 Kotlin） |
| 构建 | AGP 9.3.1 + Gradle 9.5 + JDK 21 | compileSdk 37 / targetSdk 37 / minSdk 26 / arm64-v8a |
| UI | Jetpack Compose + Material 3 + Miuix + Compose Cupertino | Compose BOM 2026.06.00；Material3 1.5.0-alpha25；Miuix 0.9.4；Cupertino 0.1.0-alpha04 |
| 导航 | Navigation3 runtime/UI + NavigationEvent | 1.2.0-alpha07 / 1.2.0-alpha03；61 个页面 Key |
| 网络 | Retrofit + OkHttp + Kotlinx Serialization + Brotli | Retrofit 3.0.0；OkHttp 5.3.2；kotlinx-serialization-json 1.11.0 |
| 存储 | Room + DataStore | Room 2.8.4；DataStore Preferences 1.2.1 |
| 媒体 | Media3 / ExoPlayer + MediaCodec | 1.10.1；DASH/HLS；MediaSession；自研 Dolby FFmpeg decoder |
| 图片 | Coil 3 | 3.5.0（compose + okhttp + gif） |
| 视觉 | Haze + Miuix Blur/Shader/Squircle + Liquid Glass | Haze 2.0.0-alpha03；自研液态玻璃与模糊预算 |
| 动画 | Compose Animation / SharedTransition + Lottie + 自研 | Lottie 6.7.1；自研 shimmer、粒子、入场/底栏/整卡动效 |
| 弹幕 | 自研 DanmakuRenderEngine | 独立模块 danmaku-engine |
| 后台任务 | WorkManager | 2.11.2 |
| 投屏 | Google Cast + NanoHTTPD（DLNA） | play-services-cast 22.3.1 |
| 主题 | Material You 动态取色 + AMOLED | material-kolor 4.1.1；Palette；ColorPicker |
| 测试 | JUnit4/5 + MockK + Turbine + Compose UI Test + Baseline Profile | 独立 baselineprofile 模块 |
| 其它 | CameraX、ZXing、Biometric、Pinyin4j、RichEditor、Firebase（可选）、LeakCanary（debug 可选） | 按需接入 |

---

## 3. 工程结构

Gradle 多模块：

| 模块 | 职责 |
|---|---|
| app | Application、Activity、业务 UI、导航、播放器、Repository、UseCase |
| design-system | 三风格主题、语义 Token、组件 facade、动效、模糊预算与自适应策略（不承载业务） |
| network-core | 网络回退与推荐策略 |
| settings-core | 可复用设置策略 |
| plugin-sdk | 插件接口与能力声明 |
| danmaku-engine | 弹幕渲染引擎 |
| baselineprofile | 启动/首页/详情性能基准 |

app 内部按职责分层：app（启动装配）、core（网络/存储/播放器/主题/UI 公共能力）、data（API/数据库/Repository）、domain（UseCase/业务规则）、feature（业务场景）、navigation / navigation3（导航内核）。

关键设计原则：feature 层只消费不可变状态与事件；可测试决策抽成 Policy/UseCase；跨 feature 视觉能力进 design-system。

---

## 4. UI 风格设计

### 4.1 设计方向（三风格合同）

默认视觉以 **Miuix 为主基准**，同时保留 **Material 3** 与历史 **iOS** 两种风格：

- 三风格必须一致：功能、信息、状态、导航、无障碍、品牌语义
- 三风格可以不同：控件底层实现、字体细节、间距微调、转场曲线、导航栏造型
- 业务层只能通过 App* 语义组件消费风格差异，不得直接读取具体风格主题

一句话：同一间房的三套装修——家具样式可以不同，但门的位置和逃生通道不能变。

### 4.2 主题模式

| 模式 | 说明 |
|---|---|
| 浅色 | 日间可读性 |
| 深色 | 夜间舒适，避免纯白大面积高亮 |
| AMOLED | 纯黑背景变体，只改背景/表面策略 |
| 动态取色 | 从系统壁纸取角色色（Material You），品牌/错误语义不能失真 |

### 4.3 设计令牌（Design Tokens）

所有设计数值先走 Token，禁止业务代码直接写 10dp/14dp/18dp 等近似值。

间距：0 / 2 / 4 / 8 / 12 / 16 / 24 / 32 / 48 dp（None → TripleExtraLarge）

圆角（ContainerLevel）：

| 级别 | 语义 | 基准值 |
|---|---|---|
| Tag | 小标签、角标 | 4dp |
| Chip | 筛选项、小操作 | 6dp |
| Field | 输入框、搜索框 | 10dp |
| Card | 标准内容卡 | 12dp |
| Dialog | 弹窗 | 14dp |
| Sheet | 底部面板顶部 | 20dp |
| Floating | 悬浮栏、FAB | 28dp |
| Pill | 胶囊、分段选择 | 由风格决定 |

颜色按角色使用：background / surface / primary / on* / error / outline。错误色只表达错误语义，品牌粉色不能代表危险。

触摸目标：可点击区域必须 ≥ 48dp；紧凑控件可见外壳可 44dp，但外部布局要保证 48dp 触摸区。

### 4.4 视觉特征

- **液态玻璃（Liquid Glass）**：底栏、顶部区域、播放器面板等关键层接入毛玻璃/液态玻璃，用层级而不是阴影区分容器
- **iOS 风格底栏**：胶囊指示器 + 阻尼回弹 + 模糊背景，大屏自动切换侧边栏
- **模糊预算**：BlurBudgetPolicy 控制模糊成本，滚动列表禁止逐项叠加实时模糊
- **运行时视觉守卫**：RuntimeVisualGuardPolicy，低性能设备自动降级为半透明普通表面

### 4.5 组件体系（App* Facade）

design-system 提供统一语义组件入口，业务页面不直接用第三方组件：

AppCard、AppButton（AppPrimaryButton）、AppSurface、AppText、AppListItem、AppIcon、AppIconButton、AppBadge、AppCheckbox、AppRadioButton、AppSwitch、AppSlider、AppSegmentedControl、AppPreference、AppNavigationComponents、AppProgressIndicator、AppBackToTopButton、AppContentStateComponents（加载/空/失败状态）等。

---

## 5. 动效设计

### 5.1 动效目的与原则

| 目的 | 合理动效 | 禁止 |
|---|---|---|
| 操作反馈 | 按压、选中、开关变化 | 动画结束前不执行真实操作 |
| 空间连续 | 页面推入、预测返回、整卡过渡 | 来源不存在时强行播放假过渡 |
| 状态变化 | 加载转内容、展开/收起 | 无限闪烁或移动布局当装饰 |
| 注意提醒 | 一次性轻提示 | 持续脉冲抢占媒体 |

硬性要求：动画参数必须来自 AppMotionTokens；动画不能成为提交/导航的唯一触发器；禁止无理由直接写 tween/spring；禁止同一元素叠加多个竞争动画。

### 5.2 动效令牌（AppMotionTokens / AppMotionEasing）

Easing（CubicBezier）：

| 名称 | 参数 | 用途 |
|---|---|---|
| EmphasizedEnter | 0.22, 1, 0.36, 1 | 强调入场 |
| EmphasizedExit | 0.32, 0, 0.67, 0 | 强调退出 |
| Continuity | 0.20, 0.90, 0.22, 1.00 | 标准连续 |
| GentleEnter | 0.18, 0.80, 0.20, 1.00 | 柔和入场 |
| SoftClear | 0.40, 0, 0.55, 0.30 | 景深返回柔化 |

时长按风格解析：

| 规格 | Miuix | Material3 |
|---|---|---|
| standard | 180ms | 200ms |
| emphasized | 240ms | 300ms |
| expressive | 150ms | 180ms |

Spring（物理手感）：

| 用途 | dampingRatio | stiffness |
|---|---|---|
| softLanding（柔和落位） | 0.86 | StiffnessMediumLow |
| interactiveSnap | 0.78 | 420 |
| pullRefreshRelease | 0.96 | 520 |
| expressiveSnap | 0.72 | 520 |
| pressFeedback（按压反馈） | 1.0 | 1000 |
| selection（选中） | 0.82 | 500 |
| indicator（指示器） | 0.7 | StiffnessMedium |
| spatial（空间过渡） | 0.82 | 380 |

### 5.3 入场动效（AppEntranceMotion）

- 用 spring 驱动单一 progress（0→1），由 progress 非线性派生 alpha / 位移 / 缩放
- alpha 提前到位（alphaLeadFraction ≈ 0.55）：先看清、再落位
- 列表错峰入场：staggerStepMs 22ms、上限 160ms（Normal 档）
- 按设备档位（MotionTier）调节：

| 档位 | damping | stiffness | offsetDp | initialScale | stagger |
|---|---|---|---|---|---|
| Reduced | 0.96 | 520 | 8 | 0.99 | 12ms / 72ms |
| Normal | 0.90 | 380 | 16 | 0.97 | 22ms / 160ms |
| Enhanced | 0.82 | 320 | 22 | 0.94 | 26ms / 220ms |

- 门控：应用开关关闭或系统 reduce-motion（ANIMATOR_DURATION_SCALE=0）时直接定格终态

### 5.4 底部导航动效（BottomBarMotionSpec）

这是 BiliPai 最有辨识度的动效，包含三部分：

1. 拖拽物理：基础阻力、过滚阻力、fling 投影时间、释放步进上限、位置吸附 spring
2. 折射/视差：移动速度阈值、速度进度除数、拖拽进度下限、面板最大偏移（4-6dp）
3. 胶囊指示器：scaleX 拉伸 + scaleY 压缩形变、速度感应拉伸、scale/drag spring

四种 profile：DEFAULT、IOS_FLOATING、ANDROID_NATIVE_FLOATING、MIUI_FLOATING，各自有微调参数（如 iOS 更弹、Android 更跟手）。

### 5.5 整卡过渡与预测返回

- Navigation3 SceneState 统一普通返回与预测返回
- 视频卡片创建不可变 TransitionSession，冻结几何（来源 key、边界、圆角、方向、封面）
- 整卡几何只有一个 shell/shared bounds 所有，不创建竞争 bounds
- 转场时钟：Opening → SettledHidden → BackPreview → Returning → Restoring
- 手势取消后缩放、透明度、模糊、播放器所有权必须完整恢复；来源刷新时降级为普通返回

### 5.6 玻璃与模糊降级

- BlurBudgetPolicy / BlurIntensity / BlurStyles：先保证无模糊时表面可用，再叠加模糊
- blur/shader 失败或低性能档位 → 不透明/半透明普通表面降级
- 禁止大量装饰性光斑、渐变球体；禁止滚动列表逐项实时模糊

### 5.7 减少动效

持续、弹性、粒子、大幅缩放应关闭或减弱；必要状态反馈改为短淡入或立即切换；加载进度仍要可感知。

---

## 6. 对 Live2dOnAndroid 的借鉴建议

### 直接借鉴（性价比高）

1. **设计令牌体系**：AppSpacingTokens / AppShapes / AppChromeSizeTokens / AppSurfaceTokens，先定 Token 再写 UI
2. **动效令牌**：AppMotionTokens 的 easing + 时长 + spring 参数表，照搬即可
3. **入场动效**：单一 spring progress 驱动 alpha/位移/缩放 + 错峰，代码量小、观感专业
4. **底部导航胶囊指示器 + 液态玻璃**：Miuix + Haze 组合，适合我们的桌面宠物式 App
5. **App* 语义组件 facade**：AppCard/AppButton/AppSurface 等薄封装，防止硬编码
6. **主题**：Material3 + 动态取色 + 深色/AMOLED
7. **模糊预算与低动效降级**：RuntimeVisualGuardPolicy 思路，控制 Live2D 渲染 + 玻璃同屏的性能风险

### 不推荐直接抄

- 三风格全量（Miuix/iOS/Material3）——我们项目规模不需要
- Navigation3 + 61 页面体系——我们大概率单屏或少量页面
- 插件 SDK、弹幕引擎、媒体播放器——业务不同，抄了是负担

### 技术栈映射

| 能力 | BiliPai 用 | 我们建议 |
|---|---|---|
| 语言/UI | Kotlin + Compose + Material3 | Kotlin + Compose + Material3 |
| 动效 | AppMotionTokens + Lottie + 自研 | 照搬 AppMotionTokens；Lottie 可选 |
| 玻璃 | Haze + Miuix Blur | Haze 2（轻量）或系统模糊 |
| 图片 | Coil 3 | Coil 3（模型预览图/头像） |
| 主题 | material-kolor | material-kolor（动态取色） |
| 导航 | Navigation3 | 简单 NavHost 或 Navigation3 均可 |
| Live2D 渲染 | 无 | WebView + pixi-live2d-display 或 Cubism SDK（另见项目 README） |
| 存储 | Room + DataStore | DataStore（设置项）+ 本地模型目录 |

---

## 7. 源码入口速查（在上游仓库中的路径）

| 内容 | 路径 |
|---|---|
| 动效令牌 | design-system/src/main/java/com/android/purebilibili/core/ui/motion/AppMotionTokens.kt |
| 底栏动效 | design-system/.../motion/BottomBarMotionSpec.kt |
| 入场动效 | design-system/.../motion/AppEntranceMotion.kt |
| 动效档位 | design-system/.../adaptive/MotionTier.kt、MotionTierPolicy.kt |
| 模糊预算 | design-system/.../blur/BlurBudgetPolicy.kt、BlurStyles.kt |
| 液态玻璃 | design-system/.../effect/FullBarLiquidGlassModifier.kt |
| 组件 facade | design-system/.../components/AppCard.kt 等 |
| 视觉守卫 | design-system/.../adaptive/RuntimeVisualGuardPolicy.kt |
| 设计规范 | docs/wiki/ui-design/README.md（含 01 方向 / 02 令牌 / 03 主题 / 06 动效） |
| 架构说明 | docs/wiki/ARCHITECTURE.md |

---

## 8. 相关链接

- 上游仓库：https://github.com/jay3-yy/BiliPai
- Miuix（小米风格 Compose 组件）：https://github.com/yukonga/Miuix
- Haze（Compose 模糊）：https://github.com/chrisbanes/haze
- Compose Cupertino：https://github.com/alexzhirkevich/compose-cupertino
- 本项目 Live2dOnAndroid：子模块 live2d-widget-mygo（MyGO Live2D 模型 + Web 渲染）
