# Live2dOnAndroid —— MyGO Live2D 桌面宠物 / 动态壁纸

把 BanG Dream! MyGO!!!!! 的 Live2D 模型放到 Android 桌面上的应用。

- **动态壁纸模式**：层级为「原系统壁纸 < Live2D 模型 < 桌面图标/小部件」
- **桌面宠物交互**：单击 / 滑动抚摸 / 双击输入 / 长按打断
- **大模型角色扮演**：OpenAI 兼容（默认 DeepSeek，测试用 opencode go mimo），thinking 可开关
- **音色克隆 TTS**：mimo-v2.5-tts-voiceclone（免费），支持语音样本管理与内置语音生成
- **历史对话**：每角色多个会话，可载入/新建/删除
- **多角色实验讨论**：主题提示词 + 终止策略 + 自动关闭

## 技术栈

- Kotlin + Jetpack Compose (Material 3)
- Rust JNI（EGL + LuaJIT + OpenGL ES 2 渲染循环）
- [EasyLive2D/Live2D-v2-Lua](https://github.com/EasyLive2D/Live2D-v2-Lua)（纯 Lua 的 Cubism 2.1/3 运行时，免官方 native 库）
- 模型：live2d-widget-mygo 子模块（MyGO 五人 74 套模型，Cubism 2.1）

## 构建

前置：JDK 17、Android SDK（compileSdk 35）、NDK r27、Rust + cargo-ndk、`ANDROID_NDK_HOME` 指向 NDK。

```bash
# 需要先放置（gitignore 不入库）：
#   third_party/Live2D-v2-Lua/   —— 从 EasyLive2D 克隆
#   app/src/main/jniLibs/arm64-v8a/libluajit.so
#   app/src/main/jniLibs/arm64-v8a/libzstd-jni-1.5.6-9.so
./gradlew :app:assembleDebug
```

模型资源在构建时从 `live2d-widget-mygo/public/model` 自动同步进 assets。

## 许可与致谢

本仓库基于 [BANDORI-PET-Android](https://github.com/HELPMEEADICE/BANDORI-PET-Android)（GPL-3.0）改造；Live2D-v2-Lua 为 LGPL-3.0；Live2D 模型来自 BanG Dream! 游戏数据包，仅供学习交流，请勿商用。

## 文档

- docs/BiliPai-技术栈与UI参考.md
- docs/DeepSeek-API调用规范.md
- docs/mimo-tts-voiceclone.txt


## 当前实现状态

已完成：
- [x] 动态壁纸渲染（MyGO 五人 74 套模型全量打包，Cubism 2.1 经 LuaJIT 纯 Lua 引擎渲染）
- [x] 层级：原系统壁纸 < 模型 < 桌面图标（原壁纸可捕获/恢复）
- [x] 手势：单击/滑动抚摸随机动画、双击悬浮输入框、长按停止（动作可选列表）
- [x] 待机随机动画（开关 + 多选 + 间隔）
- [x] 大模型对话：OpenAI 兼容（默认 DeepSeek；预设 DeepSeek / OpenCode Go / 小米 mimo），thinking 开关，动作标签驱动
- [x] mimo 音色克隆 TTS（免费，已实测），每角色预置语音样本，聊天语音 + 口型
- [x] 每角色多会话历史（应用内查看/新建/载入）
- [x] 移除悬浮窗模型模式

待办：
- [ ] 内置语音批量生成（tips.js 台词 → 克隆 TTS → 缓存，待机播放）
- [ ] 多角色实验讨论（需渲染器支持多模型同屏，Rust 改造）
- [ ] 语音样本管理 UI（每角色多样本增删查改 + 选择）
- [ ] 壁纸文字气泡（GL 文本渲染）
- [ ] 真机验证（libluajit.so 需 GC64；Android 15 16KB 对齐）
