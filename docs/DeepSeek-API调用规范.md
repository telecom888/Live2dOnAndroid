# DeepSeek API 调用规范

> 依据 DeepSeek 官方 API 文档（https://api-docs.deepseek.com/）整理
> 核对日期：2026-08-30
> 适用模型：deepseek-v4-flash / deepseek-v4-pro / deepseek-v4-flash-vision-exp

---

## 1. 基本信息

| 项目 | 值 |
|---|---|
| Base URL（OpenAI 格式） | https://api.deepseek.com |
| Base URL（Anthropic 格式） | https://api.deepseek.com/anthropic |
| Base URL（Beta 功能，如 strict tool call / prefix） | https://api.deepseek.com/beta |
| 认证方式 | HTTP Bearer Auth（Authorization: Bearer <API_KEY>） |
| 模型 | deepseek-v4-flash、deepseek-v4-pro、deepseek-v4-flash-vision-exp |
| 上下文长度 | 1M tokens |
| 最大输出 | 384K tokens（max_tokens 上限） |
| 默认思考模式 | 开启（thinking enabled），默认 effort = high |

模型版本说明：deepseek-v4-flash 已更新为 DeepSeek-V4-Flash-0731，deepseek-v4-pro 已更新为 DeepSeek-V4-Pro-0813，调用方式不变；deepseek-v4-flash-vision-exp 为实验视觉模型，额外接受图片输入。

## 2. 快速开始

### curl（非流式）

    curl https://api.deepseek.com/chat/completions \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${DEEPSEEK_API_KEY}" \
      -d '{
        "model": "deepseek-v4-pro",
        "messages": [
          {"role": "system", "content": "You are a helpful assistant."},
          {"role": "user", "content": "Hello!"}
        ],
        "thinking": {"type": "enabled"},
        "reasoning_effort": "high",
        "stream": false
      }'

### Python（OpenAI SDK）

    import os
    from openai import OpenAI

    client = OpenAI(api_key=os.environ.get("DEEPSEEK_API_KEY"), base_url="https://api.deepseek.com")
    response = client.chat.completions.create(
        model="deepseek-v4-pro",
        messages=[{"role": "system", "content": "You are a helpful assistant"},
                  {"role": "user", "content": "Hello"}],
        stream=False,
        reasoning_effort="high",
        extra_body={"thinking": {"type": "enabled"}}
    )
    print(response.choices[0].message.content)

### Node.js（OpenAI SDK）

    import OpenAI from "openai";
    const openai = new OpenAI({ baseURL: "https://api.deepseek.com", apiKey: process.env.DEEPSEEK_API_KEY });
    const completion = await openai.chat.completions.create({
      messages: [{ role: "system", content: "You are a helpful assistant." }],
      model: "deepseek-v4-pro",
      thinking: { type: "enabled" },
      reasoning_effort: "high",
      stream: false,
    });
    console.log(completion.choices[0].message.content);

## 3. 请求参数（POST /chat/completions）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| messages | object[] | 是 | 对话消息列表，>=1 条 |
| model | string | 是 | deepseek-v4-flash / deepseek-v4-pro / deepseek-v4-flash-vision-exp |
| thinking | object | 否 | {"type": "enabled"} 或 {"type": "disabled"}，默认 enabled |
| reasoning_effort | string | 否 | low / high / max；medium、xhigh 会映射到 high；默认 high |
| max_tokens | integer | 否 | 最大生成 token 数；输入+输出受 1M 上下文限制 |
| temperature | number | 否 | 0~2，默认 1；思考模式下不生效（兼容但无效） |
| top_p | number | 否 | 0~1，默认 1；思考模式下不生效 |
| stop | string/string[] | 否 | 最多 16 个停止序列 |
| stream | boolean | 否 | true 时 SSE 流式返回，data: [DONE] 结束 |
| stream_options | object | 否 | {include_usage: true} 让每个 chunk 都带 usage（最后 chunk 才有值） |
| response_format | object | 否 | {"type": "json_object"} 启用 JSON 输出 |
| tools | object[] | 否 | 函数工具列表，最多 128 个；strict 为 Beta |
| tool_choice | object/string | 否 | none / auto / required / {"type":"function","function":{"name":...}} |
| logprobs | boolean | 否 | 是否返回对数概率 |
| user_id | string | 否 | 用户隔离参数，正则 [a-zA-Z0-9-_]+，最长 512；SDK 里放 extra_body |

### messages 角色

- system：系统提示（content 为字符串）
- user：用户消息；content 可以是字符串，也可以是内容块数组（文本 / 图片 / 文件，视觉模型专用）
- assistant：模型回复；可带 reasoning_content（思考模式多轮工具调用时需回传）
- tool：工具执行结果；必须带 tool_call_id

### 内容块（视觉模型 user 消息）

    {
      "type": "text",
      "text": "描述这张图片"
    }

    {
      "type": "image_url",
      "image_url": {
        "url": "https://example.com/a.jpg",
        "detail": "auto"
      }
    }

    {
      "type": "file",
      "file_id": "file-api-xxxxxxxxxxxxxxxx"
    }

    {
      "type": "file",
      "file_data": "data:image/jpeg;base64,<BASE64>",
      "filename": "a.jpg"
    }

- image_url.url：http(s) 链接（最长 8192 字符）或 base64 data URL
- detail：low（缩到 512x512，更快更省）/ high / original / auto（后三者等效原图）
- file_id 与 file_data 互斥

## 4. 响应格式

    {
      "id": "chatcmpl-...",
      "object": "chat.completion",
      "created": 1234567890,
      "model": "deepseek-v4-pro",
      "choices": [
        {
          "index": 0,
          "message": {
            "role": "assistant",
            "content": "最终回答",
            "reasoning_content": "思考过程（思考模式时返回）",
            "tool_calls": null
          },
          "finish_reason": "stop"
        }
      ],
      "usage": {
        "prompt_tokens": 100,
        "completion_tokens": 50,
        "total_tokens": 150
      }
    }

- content：最终答案（可能为 null，例如只发生工具调用）
- reasoning_content：思维链（与 content 同级）；非工具调用场景回传会被忽略、不拼接进上下文
- finish_reason：stop / length（达到 max_tokens 或上下文上限，内容可能被截断）/ tool_calls

## 5. 流式输出（SSE）

- 设置 stream: true，返回 data: 开头的 Server-Sent Events
- 每个 chunk 的 choices[0].delta 含 content 与 reasoning_content 增量
- 流结束标记：data: [DONE]
- 设置 stream_options.include_usage = true 时，最后一个内容 chunk 携带 usage

    data: {"id":"...","choices":[{"delta":{"role":"assistant","reasoning_content":"..."}}]}
    data: {"id":"...","choices":[{"delta":{"content":"你好"}}]}
    data: [DONE]

## 6. 思考模式（Thinking Mode）

- 默认开启，通过 {"thinking": {"type": "enabled"}} 控制；{"type": "disabled"} 关闭
- effort：low / high / max；映射关系：low→low、medium→high、high→high、xhigh→high、max→max
- 思考模式下 temperature / top_p / presence_penalty / frequency_penalty 不生效（不报错）
- 思维链通过 reasoning_content 返回
- 多轮规则：
  - 请求不带 tools：reasoning_content 无需回传，回传也会被忽略、不拼接上下文
  - 请求带 tools：之前所有轮次的 reasoning_content 必须回传，且会拼接进上下文
- 思考模式支持工具调用

## 7. JSON 输出

启用条件（缺一不可）：
1. response_format = {"type": "json_object"}
2. system 或 user 提示里包含单词 json，并给出期望的 JSON 示例
3. max_tokens 设置合理，避免 JSON 被截断

注意：JSON 模式下偶尔会返回空 content，可调整提示缓解；finish_reason=length 表示被截断。

## 8. 工具调用（Tool Calls）

- 工具定义使用 OpenAI 格式：{"type":"function","function":{"name","description","parameters"(JSON Schema)}}
- 模型返回 message.tool_calls，客户端执行后追加 {"role":"tool","tool_call_id":"...","content":"结果"} 再请求
- strict 模式（Beta）：base_url 用 https://api.deepseek.com/beta；所有 function 必须 strict=true；JSON Schema 必须 object 全属性 required 且 additionalProperties=false；支持的 Schema 类型：object / string / number / integer / boolean / array / enum / anyOf
- 思考模式下使用工具：需按第 6 节规则回传 reasoning_content

## 9. 多轮对话

DeepSeek API 是无状态 API，服务端不记录上下文：
- 每轮把完整历史 messages 传回
- 追加上一轮 assistant 的返回（content；如需工具则含 tool_calls），再追加新的 user 消息

## 10. 视觉（Vision，deepseek-v4-flash-vision-exp）

支持格式：JPEG / PNG / GIF / WebP（按文件实际内容识别，不按扩展名/MIME）

三种传图方式：
1. base64 内联：data:image/jpeg;base64,...（计入请求体大小限制）
2. 外部 URL：http(s) 链接，最长 8192 字符
3. Files API：先上传拿 file_id，再用 {"type":"file","file_id":...}（可复用、支持更大图）

限制表：

| 限制项 | 值 |
|---|---|
| 请求体大小 | 48 MiB |
| 单图最大（base64 / URL） | 32 MiB |
| 单图最大（Files API） | 64 MiB |
| 每请求最多图片数 | 600 |
| 每请求图片总大小 | 64 MiB（不含 file_id）；含 file_id 可到 200 MiB |
| 单边最大尺寸 | 8192 px（>=15 张图时为 4096 px） |
| 外部 URL 长度 | 8192 字符 |

图片 token 换算：推理前自动缩放（小图放大到约 384x384，大图缩到约 800x800 总像素），单图 token 上限 384；多图各自独立计算。

限制与约束：
- 图片只允许出现在 user 消息；system / assistant 消息带图返回 400
- 只有视觉模型接受图片；其它模型带图返回 400（This model does not support image）
- 用户文本含保留的图片占位 token 会返回 400

Anthropic 格式图片块（base_url=https://api.deepseek.com/anthropic）：

    {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": "<BASE64>"}}

Responses API 图片块（input_image）：

    {"type": "input_image", "image_url": "https://example.com/a.jpg", "detail": "low"}

## 11. Files API（简述）

- 端点：POST /files（上传）、GET /files、GET /files/{id}、DELETE /files/{id}
- 上传图片后返回 file_id（形如 file-api-...）
- 适合：单请求超 48 MiB、图片 >32 MiB、同一图片多次复用

## 12. Responses API 与 Anthropic API（兼容）

- Responses API：OpenAI 兼容，POST /v1/responses（base_url 同 https://api.deepseek.com），支持 thinking、工具、视觉（input_image）、文件
- Anthropic API：base_url = https://api.deepseek.com/anthropic，Messages API 格式；思考模式控制参数：
  - 开关：{"reasoning": {"effort": "none/low/high/max"}}（none 关闭思考）
  - effort：{"output_config": {"effort": "low/high/max"}}
- 两种兼容接口的功能与 OpenAI 格式等价

## 13. 限流与隔离

| 模型 | 并发上限（每账号） |
|---|---|
| deepseek-v4-pro | 500 |
| deepseek-v4-flash | 2500 |
| deepseek-v4-flash-vision-exp | 2500 |

- 并发按账号计算，与使用哪个 API Key 无关；超限返回 HTTP 429
- user_id 参数：内容安全隔离、KV Cache 隔离、调度隔离；提升并发后每个 user_id 也有独立上限
- user_id 规则：字符串，匹配 [a-zA-Z0-9-_]+，最长 512，不要放隐私信息

## 14. 错误码

| 状态码 | 含义 | 处理 |
|---|---|---|
| 400 | Invalid Format（请求体格式错误） | 按提示修改请求体 |
| 401 | Authentication Fails（Key 错误） | 检查/重新创建 API Key |
| 402 | Insufficient Balance（余额不足） | 检查余额并充值 |
| 422 | Invalid Parameters（参数非法） | 按提示修改参数 |
| 429 | Rate Limit Reached（超限） | 降低请求频率，或扩容并发 |
| 500 | Server Error | 稍后重试，持续则联系支持 |
| 503 | Server Overloaded | 稍后重试 |

## 15. 计费（每 1M tokens，USD）

| 模型 | 输入（缓存命中，off-peak/peak） | 输入（未命中，off-peak/peak） | 输出（off-peak/peak） |
|---|---|---|---|
| deepseek-v4-flash | $0.007 / $0.014 | $0.22 / $0.44 | $0.66 / $1.32 |
| deepseek-v4-pro | $0.022 / $0.044 | $0.66 / $1.32 | $1.98 / $3.96 |
| deepseek-v4-flash-vision-exp | $0.007 / $0.014 | $0.22 / $0.44 | $0.66 / $1.32 |

- 高峰时段：UTC 周一至周五 01:00-04:00 与 06:00-10:00；其余为低峰（低峰半价）
- 图片按尺寸换算成 token，与文本一起按输入计费
- 扣费顺序：优先使用赠送余额，再用充值余额

## 16. 账户与余额

GET https://api.deepseek.com/user/balance（需要 Bearer 认证）

    {
      "is_available": true,
      "balance_infos": [
        { "currency": "CNY", "total_balance": "110.00", "granted_balance": "10.00", "topped_up_balance": "100.00" }
      ]
    }

## 17. Token 用量估算

- 1 个英文字符约 0.3 token；1 个中文字符约 0.6 token（不同模型有差异）
- 以 API 返回的 usage 为准
- 离线估算可用官方 deepseek_tokenizer（官方文档下载）；图片用官方 Image Token Calculator

## 18. 调用建议与注意事项

- 需要稳定结构化输出：用 JSON 输出（response_format）或工具调用 strict 模式，并在提示中给示例
- 需要快速响应：用 deepseek-v4-flash；需要最强能力：用 deepseek-v4-pro
- 需要看图：只有 deepseek-v4-flash-vision-exp
- 多轮必须自拼接历史；工具场景必须回传 reasoning_content
- 流式记得处理 data: [DONE] 与最后 chunk 的 usage
- 遇 429/500/503 做指数退避重试；402 提示充值
- 隐私：user_id 不要放用户隐私信息；Key 通过环境变量注入，不要写进代码或文档
