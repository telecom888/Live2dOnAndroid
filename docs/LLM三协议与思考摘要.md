# LLM 三协议与思考摘要（1.1.4）

## 原因与修复

1.1.3 只扩展了响应 CoT 字段解析。原请求的思考开关仅发送 `thinking.type`，这是 DeepSeek 等服务的扩展；CLIProxyAPI 的 OpenAI → Gemini 路径读取 `reasoning_effort`，摘要可见性又是独立设置。因此模型可以在内部思考，但不返回可显示的摘要。新增字段解析无法解决请求端缺少摘要可见性的问题。

1.1.4 在「设置 → 模型提供商」加入显式协议选择。所有模型 ID 和网关别名均由用户填写，代码不按模型名匹配、替换模型或推断协议。原有配置默认 OpenAI，升级后无需重填。

| 协议 | 接口 | 鉴权 | 思考输出 |
| --- | --- | --- | --- |
| OpenAI | `/chat/completions` | `Authorization: Bearer` | 原有 `reasoning_content` / `reasoning` / `reasoning_details`；显式开关补充 `reasoning_effort` 与 `reasoning.summary`，保留原有 `thinking.type` 扩展 |
| Anthropic | `/v1/messages` | `x-api-key`、`anthropic-version` | 解析 `thinking` / `thinking_delta` 与 `text` / `text_delta`，忽略签名和加密块 |
| Google | `/v1beta/models/{用户模型ID}:streamGenerateContent?alt=sse` | `x-goog-api-key` | 开启时发送 `generationConfig.thinkingConfig.includeThoughts=true`，将 `parts[].thought=true` 的文本送入折叠思考区 |

Google 非流式调用使用 `:generateContent`；Anthropic 非流式调用使用 `stream=false`。流式和非流式均保留 HTTP 状态码、错误脱敏、超时及取消处理。系统提示、历史文本和本地图片转换为各自原生格式。未配置工具执行器时，原生协议返回工具调用会显示明确错误。

## 使用卷桥调用 Google

1. 选择 **Google** 协议。
2. Base URL 填卷桥的根地址（例如 `https://你的网关`）或 `/v1beta` 地址；旧 `/v1` 后缀也会转换为原生 Google 路径。
3. 模型 ID 填卷桥对外开放的实际 ID 或别名，密钥使用卷桥访问密钥。
4. 将思考设为开启并保存，进入完整对话页或悬浮聊天。接口实际返回摘要时显示可展开/收起的“深度思考”，正常回复后随消息保存。

摘要是供应商返回的生成内容，不是原始内部推理。关闭 Google 思考开关会关闭摘要输出；某些模型仍会在内部思考。默认思考模式不覆盖上游设置。原有桌面双击小窗依旧只展示正文。

Anthropic 可选择自适应或旧版预算思考；由用户根据服务能力选择，不猜测模型 ID。自适应模式显式请求 `display=summarized`；关闭时使用 `display=omitted`。预算模式使用 1024 思考 token，总输出上限至少为 2048；关闭时发送 `type=disabled`。不支持某种模式的服务会返回错误供 UI 展示。

## 验证及范围

- 本地 HTTP 测试检查三协议请求、路径、鉴权、别名、思考/正文事件分离、流式/非流式、403 脱敏、流内错误和空回复。
- CLIProxyAPI 参考源码的 `TestExtractSummaryConfig` 和 `TestApplySummaryConfig` 测试通过，确认 OpenAI 摘要设置到 Google `includeThoughts` 的映射。
- 未连接 Android 真机；本机默认网关/上游端口未监听，未完成实际模型联网复测。签名 APK 复用未修改的原生库。

参考：[Google OpenAI 兼容接口](https://ai.google.dev/gemini-api/docs/openai)、[Google 思考摘要](https://ai.google.dev/gemini-api/docs/generate-content/thinking)、[Anthropic 思考与显示控制](https://platform.claude.com/docs/en/build-with-claude/thinking)。
