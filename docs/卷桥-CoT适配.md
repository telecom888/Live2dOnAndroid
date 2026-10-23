# 卷桥 CoT 适配（1.1.3）

后续 1.1.4 已补齐请求端摘要设置并加入三种协议选择，见 [LLM 三协议与思考摘要](LLM三协议与思考摘要.md)。以下记录 1.1.3 的响应解析范围。

卷桥的 Go SSE 转发实现（`beta/go-gateway/stream.go`）保留上游响应字段；模型别名仅改写协议模型字段。Android 使用 OpenAI Chat Completions 接口，CoT 字段取决于卷桥连接的上游。

## 接收格式

`choices[0].delta` 中按优先级读取 `reasoning_content`、`reasoning`、`reasoning_details`。每个事件只采用第一个有文本的字段，空值、null 或不支持的数据继续回退，避免上游同时提供多个别名时重复显示。这一顺序与本地 CLIProxyAPI 的 OpenAI 兼容提取逻辑一致。

支持字符串、文本对象及数组；读取 `text` 或 `summary`，识别 `reasoning.text`、`reasoning.summary`、`text`、`summary_text` 及未标注类型的文本对象。分片保留空格、换行和顺序。加密块、签名和元数据不会转换为可见文本。

提取结果复用原有 `LlmStreamEvent.Reasoning` 链路，在完整对话页及悬浮聊天中显示为可折叠的“深度思考”，正常回复完成后随消息保存。不会重新显示“思考中”或旋转进度提示。

这次适配没有增加桌面小窗的思维链 UI，也没有增加原生 Responses、Anthropic Messages 或 Gemini 接口；这些服务须通过上游的 Chat Completions 兼容接口调用。只有推理、没有正文的响应仍报空回复。

## 验证

- 55 项本地单元测试全部通过；新增覆盖字段回退、重复别名、结构化文本、摘要、加密块过滤，以及 SSE 多事件/EOF 到推理事件的完整请求解析。
- Android Lint：0 errors / 21 warnings。
- 本地签名 Release 构建复用未修改的原生库。未对用户的实际网关发送模型请求，未进行真机 UI 验证。
