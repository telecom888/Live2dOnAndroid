图片理解

图片理解模型可以根据您传入的图片进行回答，支持图片 URL 和 Base64 编码两种传入方式，适用于图片描述、分类等场景。



快速开始

获取 API Key 等准备工作，请参考 首次调用API。



通过图片 URL 方式传入模型快速体验图片理解效果，示例代码如下。



Python SDK

Curl

import os

from openai import OpenAI



client = OpenAI(

&#x20;   api\_key=os.environ.get("MIMO\_API\_KEY"),

&#x20;   base\_url="https://api.xiaomimimo.com/v1"

)



completion = client.chat.completions.create(

&#x20;   model="mimo-v2.5",

&#x20;   messages=\[

&#x20;       {

&#x20;           "role": "system",

&#x20;           "content": "You are MiMo, an AI assistant developed by Xiaomi. Today is date: Tuesday, December 16, 2025. Your knowledge cutoff date is December 2024."

&#x20;       },

&#x20;       {

&#x20;           "role": "user",

&#x20;           "content": \[

&#x20;               {

&#x20;                   "type": "image\_url",

&#x20;                   "image\_url": {

&#x20;                       "url": "https://example-files.cnbj1.mi-fds.com/example-files/image/image\_example.png"

&#x20;                   }

&#x20;               },

&#x20;               {

&#x20;                   "type": "text",

&#x20;                   "text": "please describe the content of the image"

&#x20;               }

&#x20;           ]

&#x20;       }

&#x20;   ],

&#x20;   max\_completion\_tokens=1024

)



print(completion.model\_dump\_json())



响应结果



{

&#x20;   "id": "be319773af414195b27a9b1cefc3fe6f",

&#x20;   "choices": \[

&#x20;       {

&#x20;           "finish\_reason": "stop",

&#x20;           "index": 0,

&#x20;           "message": {

&#x20;               "content": "This is a serene, lush temperate old-growth forest scene, bathed in warm, golden hour light, likely early morning or late afternoon:\\\\n\\\\n1.  \*\*Foreground \& Stream\*\*: A shallow, clear stream winds through the scene, its surface catching the sunlight to create shimmering highlights. Smooth, rounded river rocks, many covered in thick, vibrant green moss, line the stream's banks and sit partially submerged in the water. On the left, a large, detailed fern with full, feathery fronds anchors the mossy shore.\\\\n2.  \*\*Midground \& Forest Floor\*\*: The forest floor is dense with verdant, mossy growth, small leafy shrubs, and undergrowth, creating a rich, textured green landscape. Ancient, gnarled trees with thick, textured trunks and sprawling, twisted branches rise up, their bases rooted in the mossy earth.\\\\n3.  \*\*Light \& Atmosphere\*\*: Soft, dramatic sunbeams, crepuscular rays, filter through the misty forest canopy, cutting through the hazy air. Tiny particles like pollen or dust catch the light, adding an ethereal, magical quality. Mist hangs between the trees, softening the background and lending the scene a quiet, tranquil, almost enchanted mood.\\\\n\\\\nThe image conveys a sense of peace, untouched natural beauty, and the quiet magic of an old, undisturbed forest.",

&#x20;               "role": "assistant",

&#x20;               "tool\_calls": null,

&#x20;               "reasoning\_content": "Got it, let's break down this image step by step. First, the setting is a lush, misty old-growth forest, early morning or late afternoon (golden hour, since the sunbeams are warm).\\\\n\\\\nStart with the foreground: a shallow, clear stream with smooth, moss-covered rocks lining its banks. The rocks are rounded, some partially submerged, with bright green moss growing thickly on them and the shore. On the left, a large, vibrant fern with detailed fronds sits on the mossy bank.\\\\n\\\\nThen the midground: the stream winds through the forest, with small shrubs and more mossy growth along its edges. The trees are ancient, with thick, gnarled trunks and sprawling, twisted branches, their bark textured and aged. The forest floor is covered in moss and undergrowth, feeling dense and verdant.\\\\n\\\\nThe background and lighting: soft, golden sunbeams (crepuscular rays) filter through the misty forest canopy, illuminating the air—you can see tiny particles (like dust or pollen) catching the light, creating a magical, ethereal glow. The mist hangs in the air between the trees, softening the background and adding depth, making the forest feel quiet, serene, and almost enchanted.\\\\n\\\\nOverall, it's a tranquil, lush temperate rainforest scene, full of natural detail, with warm, diffused light creating a peaceful, mystical atmosphere."

&#x20;           }

&#x20;       }

&#x20;   ],

&#x20;   "created": 1776850561,

&#x20;   "model": "mimo-v2.5",

&#x20;   "object": "chat.completion",

&#x20;   "usage": {

&#x20;       "completion\_tokens": 574,

&#x20;       "prompt\_tokens": 1085,

&#x20;       "total\_tokens": 1659,

&#x20;       "completion\_tokens\_details": {

&#x20;           "reasoning\_tokens": 288

&#x20;       },

&#x20;       "prompt\_tokens\_details": {

&#x20;           "cached\_tokens": 1081,

&#x20;           "image\_tokens": 1024

&#x20;       }

&#x20;   }

}



支持的模型列表

当前仅支持 mimo-v2.5 模型。



图片传入方式

支持的图片传入方式如下：



图片 URL 传入：需提供公网可访问的图片 URL 地址。



Base64 编码传入：将图片转换为 Base64 编码字符串后再传入。



图片 URL 传入

通过公网可访问的图片 URL 地址直接传入图片，适用于图片已存储在公网可访问环境的场景。单张图片的文件大小不能超过 50 MB。



OpenAI Chat Completions API

Python SDK

Curl

import os

from openai import OpenAI



client = OpenAI(

&#x20;   api\_key=os.environ.get("MIMO\_API\_KEY"),

&#x20;   base\_url="https://api.xiaomimimo.com/v1"

)



completion = client.chat.completions.create(

&#x20;   model="mimo-v2.5",

&#x20;   messages=\[

&#x20;       {

&#x20;           "role": "system",

&#x20;           "content": "You are MiMo, an AI assistant developed by Xiaomi. Today is date: Tuesday, December 16, 2025. Your knowledge cutoff date is December 2024."

&#x20;       },

&#x20;       {

&#x20;           "role": "user",

&#x20;           "content": \[

&#x20;               {

&#x20;                   "type": "image\_url",

&#x20;                   "image\_url": {

&#x20;                       "url": "https://example-files.cnbj1.mi-fds.com/example-files/image/image\_example.png"

&#x20;                   }

&#x20;               },

&#x20;               {

&#x20;                   "type": "text",

&#x20;                   "text": "please describe the content of the image"

&#x20;               }

&#x20;           ]

&#x20;       }

&#x20;   ],

&#x20;   max\_completion\_tokens=1024

)



print(completion.model\_dump\_json())



Anthropic Messages API

Python SDK

Curl

import os

from anthropic import Anthropic



client = Anthropic(

&#x20;   api\_key=os.environ.get("MIMO\_API\_KEY"),

&#x20;   base\_url="https://api.xiaomimimo.com/anthropic"

)



message = client.messages.create(

&#x20;   model="mimo-v2.5",

&#x20;   max\_tokens=1024,

&#x20;   system="You are MiMo, an AI assistant developed by Xiaomi. Today is date: Tuesday, December 16, 2025. Your knowledge cutoff date is December 2024.",

&#x20;   messages=\[

&#x20;       {

&#x20;           "role": "user",

&#x20;           "content": \[

&#x20;               {

&#x20;                   "type": "image",

&#x20;                   "source": {

&#x20;                       "type": "url",

&#x20;                       "url": "https://example-files.cnbj1.mi-fds.com/example-files/image/image\_example.png"

&#x20;                   }

&#x20;               },

&#x20;               {

&#x20;                   "type": "text",

&#x20;                   "text": "please describe the content of the image"

&#x20;               }

&#x20;           ]

&#x20;       }

&#x20;   ]

)



print(message.content)



Base64 编码传入

将图片文件转换为 Base64 编码字符串后传入，适用于图片无法通过公网 URL 访问的场景。转换后的 Base64 编码的字符串大小不能超过 50 MB。



下述示例中的 {MIME\_TYPE} 与 $BASE64\_IMAGE 均为占位符，运行前请替换为实际有效数据。



OpenAI Chat Completions API

请在 Base64 编码前携带前缀：data:{MIME\_TYPE};base64,$BASE64\_IMAGE



{MIME\_TYPE}：图像的 MIME 类型（媒体类型），用于标识图像格式，需替换为实际图像对应的 MIME 值。



$BASE64\_IMAGE：图像文件的纯 Base64 编码字符串（不含任何前缀）。



Python SDK

Curl

import os

from openai import OpenAI



client = OpenAI(

&#x20;   api\_key=os.environ.get("MIMO\_API\_KEY"),

&#x20;   base\_url="https://api.xiaomimimo.com/v1"

)



completion = client.chat.completions.create(

&#x20;   model="mimo-v2.5",

&#x20;   messages=\[

&#x20;       {

&#x20;           "role": "system",

&#x20;           "content": "You are MiMo, an AI assistant developed by Xiaomi. Today is date: Tuesday, December 16, 2025. Your knowledge cutoff date is December 2024."

&#x20;       },

&#x20;       {

&#x20;           "role": "user",

&#x20;           "content": \[

&#x20;               {

&#x20;                   "type": "image\_url",

&#x20;                   "image\_url": {

&#x20;                       "url": "data:{MIME\_TYPE};base64,$BASE64\_IMAGE"

&#x20;                   }

&#x20;               },

&#x20;               {

&#x20;                   "type": "text",

&#x20;                   "text": "please describe the content of the image"

&#x20;               }

&#x20;           ]

&#x20;       }

&#x20;   ],

&#x20;   max\_completion\_tokens=1024

)



print(completion.model\_dump\_json())



Anthropic Messages API

Python SDK

Curl

import os

from anthropic import Anthropic



client = Anthropic(

&#x20;   api\_key=os.environ.get("MIMO\_API\_KEY"),

&#x20;   base\_url="https://api.xiaomimimo.com/anthropic"

)



message = client.messages.create(

&#x20;   model="mimo-v2.5",

&#x20;   max\_tokens=1024,

&#x20;   system="You are MiMo, an AI assistant developed by Xiaomi. Today is date: Tuesday, December 16, 2025. Your knowledge cutoff date is December 2024.",

&#x20;   messages=\[

&#x20;       {

&#x20;           "role": "user",

&#x20;           "content": \[

&#x20;               {

&#x20;                   "type": "image",

&#x20;                   "source": {

&#x20;                       "type": "base64",

&#x20;                       "media\_type": "{MIME\_TYPE}",

&#x20;                       "data": "$BASE64\_IMAGE"

&#x20;                   }

&#x20;               },

&#x20;               {

&#x20;                   "type": "text",

&#x20;                   "text": "please describe the content of the image"

&#x20;               }

&#x20;           ]

&#x20;       }

&#x20;   ]

)



print(message.content)



多图输入

支持同时传入多张图像的公网 URL 或 Base64 编码字符串，模型能够解析图像内容并返回贴合图像语义的回复。



Python SDK

Curl

import os

from openai import OpenAI



client = OpenAI(

&#x20;   api\_key=os.environ.get("MIMO\_API\_KEY"),

&#x20;   base\_url="https://api.xiaomimimo.com/v1"

)



completion = client.chat.completions.create(

&#x20;   model="mimo-v2.5",

&#x20;   messages=\[

&#x20;       {

&#x20;           "role": "system",

&#x20;           "content": "You are MiMo, an AI assistant developed by Xiaomi. Today is date: Tuesday, December 16, 2025. Your knowledge cutoff date is December 2024."

&#x20;       },

&#x20;       {

&#x20;           "role": "user",

&#x20;           "content": \[

&#x20;               {

&#x20;                   "type": "image\_url",

&#x20;                   "image\_url": {

&#x20;                       "url": "https://example-files.cnbj1.mi-fds.com/example-files/image/image\_example.png"

&#x20;                   }

&#x20;               },

&#x20;               {

&#x20;                   "type": "image\_url",

&#x20;                   "image\_url": {

&#x20;                       "url": "data:{MIME\_TYPE};base64,$BASE64\_IMAGE"

&#x20;                   }

&#x20;               },

&#x20;               {

&#x20;                   "type": "text",

&#x20;                   "text": "please describe the connections and differences between these two pictures"

&#x20;               }

&#x20;           ]

&#x20;       }

&#x20;   ],

&#x20;   max\_completion\_tokens=1024

)



print(completion.model\_dump\_json())



图片限制

图片格式：JPEG，PNG，GIF，WebP，BMP。



图像大小：



以 URL 方式传入时：单张图片文件大小不超过 50 MB。



以 Base64 编码传入时：单张图片 Base64 编码字符串大小不超过 50 MB。



图片数量：传入多张图片时，图片数量受模型上下文长度限制，所有图片和文本的总 Token 数必须小于模型的上下文长度。



注：计算图像的 Token 请参考 图片 Token 用量说明。模型上下文长度请参考 定价与限速。



图片 Token 用量及缩放规则说明

图片的计算规则较为复杂，Token 转化及缩放规则请参考以下代码。估算结果仅供参考，实际用量以 API 响应为准。



import math

from PIL import Image



PATCH\_SIZE = 16

SPATIAL\_MERGE\_SIZE = 2

TEMPORAL\_PATCH\_SIZE = 2

IMAGE\_MIN\_PIXELS = 8192

IMAGE\_MAX\_PIXELS = 8388608



def calc\_image\_tokens(image\_path: str) -> dict:

&#x20;   image = Image.open(image\_path)

&#x20;   height = image.height

&#x20;   width = image.width



&#x20;   factor = PATCH\_SIZE \* SPATIAL\_MERGE\_SIZE  # 32



&#x20;   h\_bar = round(height / factor) \* factor

&#x20;   w\_bar = round(width / factor) \* factor



&#x20;   if h\_bar \* w\_bar > IMAGE\_MAX\_PIXELS:

&#x20;       beta = math.sqrt((height \* width) / IMAGE\_MAX\_PIXELS)

&#x20;       h\_bar = math.floor(height / beta / factor) \* factor

&#x20;       w\_bar = math.floor(width / beta / factor) \* factor

&#x20;   elif h\_bar \* w\_bar < IMAGE\_MIN\_PIXELS:

&#x20;       beta = math.sqrt(IMAGE\_MIN\_PIXELS / (height \* width))

&#x20;       h\_bar = math.ceil(height / beta / factor) \* factor

&#x20;       w\_bar = math.ceil(width / beta / factor) \* factor



&#x20;   grid\_t = 1

&#x20;   grid\_h = h\_bar // PATCH\_SIZE

&#x20;   grid\_w = w\_bar // PATCH\_SIZE

&#x20;   num\_tokens = (grid\_t \* grid\_h \* grid\_w) // (SPATIAL\_MERGE\_SIZE \*\* 2)

&#x20;   return num\_tokens



if \_\_name\_\_ == "\_\_main\_\_":

&#x20;  token = calc\_image\_tokens(image\_path="xxx/test.jpg")

&#x20;  print(token)

