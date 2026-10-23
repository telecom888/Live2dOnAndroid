Android 8.0+，仅 arm64-v8a。

- 新增 OpenAI、Anthropic、Google 三种接口协议选择；模型 ID 和网关别名由用户填写。
- 修复只适配返回字段、未正确请求 Gemini 思考摘要的问题。使用卷桥时可选择 Google 协议并开启思考。
- 原生 Google / Anthropic 推理文本接入可折叠的“深度思考”区域。
- 保留 HTTP 状态码、脱敏错误详情、重试及请求取消。

APK 由 GitHub Actions 编译和签名，沿用既有签名证书，可覆盖安装。下载 APK 安装；SHA256SUMS.txt 用于完整性校验，build-provenance.json 记录构建来源。

67 项本地单元测试通过；真机及实际网关调用仍需复测。桌面双击小窗仅显示正文。
