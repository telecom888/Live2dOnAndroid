# Live2dOnAndroid

把 MyGO!!!!! 的 Live2D 模型搬到 Android 上的实验项目。

## 当前状态

- [x] 初始化 git 仓库
- [x] 引入 live2d-widget-mygo（子模块）：包含 MyGO 五人 74 套 Live2D 模型（Cubism 2.1 格式）
- [ ] 确定 Android 渲染方案（WebView + pixi-live2d-display / Cubism 2.1 SDK / 转 .moc3）
- [ ] Android 工程搭建

## 子模块

    git submodule update --init --recursive

模型目录：live2d-widget-mygo/public/model/

注意：模型来自 BanG Dream! 游戏数据包，仅供学习交流，请勿商用。

## 参考文档

- docs/BiliPai-技术栈与UI参考.md：BiliPai 参考项目的技术栈、UI 风格与动效设计说明

- docs/DeepSeek-API调用规范.md：DeepSeek 官方 API 调用规范（认证、参数、流式、思考模式、视觉、工具调用、限流、错误码、计费）
