Android 8.0+，仅 arm64-v8a。

## 1.1.14

- 新增独立 Vertex AI 协议，可使用 Google Cloud 项目级 Gemini 接口，保留原有 Google（AI Studio）、OpenAI 和 Anthropic 协议。
- 修正 Vertex AI `generateContent` 和流式 `streamGenerateContent` 请求地址构造。
- 支持使用服务账号绑定的 Agent Platform API Key 调用受支持的 Vertex AI Gemini 模型。
- Vertex AI 模型暂时采用手动输入 ID；自动获取 Model Garden 列表功能不包含在此版本。
- 保留此前更新检查行为：手动检查更新时仅显示「下载」和「稍后」；「跳过此版」仍在自动检查弹窗中。

提示：模型是否可访问、API Key 是否有权限以及计费抵扣仍以 Google Cloud 实际配置为准。
