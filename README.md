# Live2dOnAndroid

基于 BanG Dream! 角色的 Android Live2D 动态壁纸与聊天应用，支持触摸互动、角色扮演对话和语音合成。项目基于 [BANDORI-PET-Android](https://github.com/HELPMEEADICE/BANDORI-PET-Android) 改造。

**运行要求：Android 8.0（API 26）及以上，当前仅打包 `arm64-v8a` 架构。**

## 功能

| 功能 | 说明 |
| --- | --- |
| Live2D 动态壁纸 | 角色显示在桌面图标和小部件下方，支持模型、服装和背景切换 |
| 角色互动 | 单击、滑动抚摸、双击输入、长按打断，以及可配置的随机待机动作 |
| LLM 对话 | OpenAI 兼容接口；提供 DeepSeek、OpenCode Go、小米 MiMo 预设，支持自定义服务商、模型和思考模式 |
| 聊天历史 | 每角色多个会话；我方消息编辑重发、回复重新生成和版本切换 |
| LINE Mode | 连续气泡、头像、名称与时间排版；可自定义双方气泡颜色、背景颜色或背景图片 |
| 多段回复 | 一次返回结构化文本，按可配置间隔显示多段气泡；朗读、重新生成和版本切换按整组处理 |
| TTS | 可配置服务商、模型和密钥；支持 MiMo 音色克隆、角色参考音频管理、内置语音生成与口型联动 |
| 个性化 | 消息时间、月/日、朗读按钮、重试图标、上下文信息和模型时间控制的显示开关 |
| 界面设置 | 简体中文、日本語、English；设置子页面统一过渡动画并保留返回时的滚动位置 |

LLM 与 TTS 使用远程接口，需要自行配置 API 密钥。服务额度、费用与模型可用性以所选服务商为准。

## 模型下载源

### 应用内下载与更新

在线 Live2D 模型来自 ModelScope（魔搭社区）的数据集：

**[HELPMEEADICE/BanG-Dream-Live2D](https://modelscope.cn/datasets/HELPMEEADICE/BanG-Dream-Live2D)**

当前代码使用的地址模板：

```text
https://modelscope.cn/datasets/HELPMEEADICE/BanG-Dream-Live2D/resolve/master/models/<角色 ID>.zst
```

例如：[`anon.zst`（千早爱音）](https://modelscope.cn/datasets/HELPMEEADICE/BanG-Dream-Live2D/resolve/master/models/anon.zst)。角色 ID 会先进行 UTF-8 URL 编码。

- 在应用的模型页面下载或更新，文件保存到应用内部的 `files/models/<角色 ID>.zst`。
- 已下载模型优先于 APK 内置资源；当前仅配置这一个在线下载源。
- 地址定义见 [`ZstModelArchive.downloadUrl()`](app/src/main/java/com/bangdream/pet/data/ZstModelArchive.kt)。

### APK 内置模型

内置资源来自 Git 子模块 **[panxuc/live2d-widget-mygo](https://github.com/panxuc/live2d-widget-mygo)** 的 `public/model/` 目录，由构建任务自动复制到 APK assets。

| 角色 ID | 角色 |
| --- | --- |
| `anon` | 千早爱音 |
| `rana` | 要乐奈 |
| `soyo` | 长崎素世 |
| `taki` | 椎名立希 |
| `tomori` | 高松灯 |

上游子模块说明模型资源来自 Bestdori 提供的 BanG Dream! 游戏数据包，并进行了适配。下载托管平台与素材版权来源分别说明，详见 [模型下载源](docs/模型下载源.md)。这里的模型指 Live2D 角色资源；LLM 和 TTS 通过接口调用。

## 使用与权限

1. 在模型页面选择角色和服装，需要时下载模型。
2. 在设置中配置 LLM；需要朗读时，在独立的 TTS 设置中配置服务商和参考音频。
3. 将应用设为动态壁纸，按需开启桌面文字气泡。
4. 在个性化中调整 LINE 配色、背景、显示项目和多段发送间隔。

设置页提供“在其他应用上层显示”的系统授权入口，桌面文字气泡与悬浮输入框需要该权限。捕获原系统壁纸时，Android 8～12 会按需申请读取权限；较新系统可能限制壁纸读取，可改为手动选择背景图片。

## 技术栈与目录

- Kotlin、Jetpack Compose、Material 3。
- Rust JNI：EGL、OpenGL ES 和 LuaJIT 渲染循环。
- [EasyLive2D/Live2D-v2-Lua](https://github.com/EasyLive2D/Live2D-v2-Lua)：Live2D Lua 运行时。
- Zstandard：在线 `.zst` 模型包解压。

```text
app/                         Android 应用、Kotlin 界面与 Rust JNI
live2d-widget-mygo/           内置 MyGO 模型子模块
third_party/Live2D-v2-Lua/    第三方 Lua 运行时（需自行准备，不入库）
characters/                  角色资料
lang/                        简体中文、日语、英语翻译
docs/                        功能说明、模型来源与构建文档
icon.png                     应用图标源文件
```

## 本地构建

### 环境

| 工具 | 要求 |
| --- | --- |
| JDK | 17 |
| Android SDK | Platform 35；配置 `local.properties` 中的 `sdk.dir` |
| Android NDK | NDK 27 系列；配置 `ANDROID_NDK_HOME` |
| Rust | 1.85 及以上，安装 `aarch64-linux-android` target |
| cargo-ndk | 安装并加入 PATH，Gradle 自动调用 |
| Gradle | 使用仓库自带 Wrapper |

### 获取代码与资源

```bash
git clone --recurse-submodules https://github.com/telecom888/Live2dOnAndroid.git
cd Live2dOnAndroid

# 已经 clone 时，补齐子模块
git submodule update --init --recursive

rustup target add aarch64-linux-android
cargo install cargo-ndk

git clone https://github.com/EasyLive2D/Live2D-v2-Lua.git third_party/Live2D-v2-Lua
```

还需准备 Android arm64 原生库，放入以下位置（已被 Git 忽略）：

```text
app/src/main/jniLibs/arm64-v8a/libluajit.so
app/src/main/jniLibs/arm64-v8a/libzstd-jni-1.5.6-9.so
```

LuaJIT 需启用 GC64，编译方法可参考 [上游构建说明](https://github.com/HELPMEEADICE/BANDORI-PET-Android#获取--编译原生库)。Zstd 原生库的提取步骤见 [zstd-native-lib.md](docs/zstd-native-lib.md)。

### 编译与测试

Windows PowerShell：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Linux / macOS 使用 `./gradlew` 执行相同任务。Gradle 自动编译项目的 Rust JNI，并同步模型、翻译和图标资源。

APK 输出到 `app/build/outputs/apk/debug/` 或 `app/build/outputs/apk/release/`。Release 签名需自行提供配置；未配置完整签名参数时生成未签名包。详见 [Release 签名配置](docs/Release签名配置.md)，密钥与密码请保留在本地。

当前多段回复实现已通过 35 项单元测试与 Release 构建；LINE 气泡、输入法布局、朗读及动态壁纸效果仍需在支持的设备上复核。多角色实验讨论尚未实现。

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [模型下载源](docs/模型下载源.md) | 在线地址、APK 内置模型与读取优先级 |
| [模型多段发送](docs/模型多段发送.md) | 开关、间隔、JSON 协议与整组操作 |
| [LINE Mode 自定义配色](docs/LineMode自定义配色.md) | 双方气泡、背景颜色与背景图片 |
| [设置与消息分支设计](docs/设置二级页-LineMode-消息分支设计.md) | 设置页面、LINE 排版和编辑重发设计 |
| [角色设定](docs/角色防ooc设定.md) | 角色扮演参考 |
| [Release 签名配置](docs/Release签名配置.md) | 本地签名参数与验证 |
| [Zstd 原生库](docs/zstd-native-lib.md) | Android 原生库准备 |
| [DeepSeek API 调用规范](docs/DeepSeek-API调用规范.md) | 接口使用参考 |
| [MiMo TTS](docs/mimo-tts-voiceclone.txt) | 语音克隆参考 |
| [MiMo 图片输入](docs/mimo-图片输入规范.md) | 多模态输入参考 |
| [性能审计](docs/performance-audit-2026.md) | 性能审计与优化记录 |
| [UI 技术参考](docs/BiliPai-技术栈与UI参考.md) | 界面技术与组件参考 |
| [脚本与产物归档](docs/工具脚本与测试产物归档说明.md) | 本地工具和测试产物说明 |

## 许可与致谢

本仓库代码采用 [GPL-3.0](LICENSE)，源自 [BANDORI-PET-Android](https://github.com/HELPMEEADICE/BANDORI-PET-Android)。Live2D-v2-Lua 使用 LGPL-3.0，其他依赖按各自许可使用。

角色模型、图像、台词与音频等素材的版权归各自权利人所有，不因代码开源而获得额外素材授权。内置模型上游注明仅供学习交流、请勿商用；使用素材时请查阅其来源说明。

感谢 BANDORI-PET-Android、EasyLive2D/Live2D-v2-Lua、panxuc/live2d-widget-mygo 及模型下载源维护者。
