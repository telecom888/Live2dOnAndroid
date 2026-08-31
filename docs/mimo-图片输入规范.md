# MiMo 图片输入规范

> 依据小米 MiMo API 开放平台官方文档整理
> 来源：https://mimo.mi.com/docs/zh-CN/quick-start/usage-guide/multimodal-understanding/image-understanding
> 核对日期：2026-08-31

## 1. 支持模型

- 图片理解当前**仅支持 `mimo-v2.5`**（全模态：text / image / audio / video）。
- `mimo-v2.5-pro` 等纯文本系列不支持图片输入。

## 2. 传入方式

支持两种方式：

1. **图片 URL**：公网可访问的图片地址，单张文件大小不超过 50 MB。
2. **Base64 内联**：`data:{MIME_TYPE};base64,{BASE64}`，Base64 字符串大小不超过 50 MB。
   - `{MIME_TYPE}` 为图片 MIME（如 image/jpeg）。
   - `{BASE64}` 为纯 Base64 字符串（不含前缀）。

## 3. OpenAI Chat Completions 请求格式

图片放在 **user 消息** 的 `content` 内容块数组中，`image_url` 块 + `text` 块：

```json
{
  "role": "user",
  "content": [
    { "type": "image_url", "image_url": { "url": "https://example.com/a.png" } },
    { "type": "image_url", "image_url": { "url": "data:image/jpeg;base64,<BASE64>" } },
    { "type": "text", "text": "please describe the content of the image" }
  ]
}
```

- 多图：追加多个 `image_url` 块即可（受上下文长度限制，图片+文本总 token 需小于模型上下文）。
- `max_completion_tokens` / `max_tokens` 控制生成上限。

## 4. 图片限制

| 项目 | 值 |
|---|---|
| 支持格式 | JPEG、PNG、GIF、WebP、BMP |
| 单图大小（URL / Base64） | ≤ 50 MB |
| 图片数量 | 受模型上下文长度限制（图片+文本总 token < 上下文） |

## 5. 图片 Token 估算

参考官方 `calc_image_tokens`（PATCH_SIZE=16，SPATIAL_MERGE_SIZE=2）：

- 图片按 `h_bar * w_bar`（缩放后像素）换算 token，`num_tokens = (grid_h * grid_w) / (SPATIAL_MERGE_SIZE^2)`。
- 小图放大到至少 8192 像素，大图缩到最多 8388608 像素。
- 估算仅供参考，实际以 API 响应的 `usage.prompt_tokens_details.image_tokens` 为准。

## 6. 计费

总费用按输入（含图片折算 token）、输入（命中缓存）与输出 token 计算；价格参考官方「定价与限速」。

## 7. 注意事项

- 图片只允许出现在 **user 消息**；system / assistant 带图会报错。
- 本地文件上传不支持，需转 URL 或 Base64。
- 本项目对接：LLM 设置开启「图片输入」后，输入框可多选图片；发送时按上述 `image_url` 内容块格式组装请求体。
