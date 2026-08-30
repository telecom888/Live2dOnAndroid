# -*- coding: utf-8 -*-
"""重试转写 3 条之前拒答的音频。"""
import base64
import json
import os
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
VOICES = ROOT / "app" / "src" / "main" / "assets" / "voices_builtin"
API = "https://opencode.ai/zen/go/v1/chat/completions"
MODEL = "mimo-v2.5"
TARGETS = [("anon", 54), ("anon", 61), ("soyo", 48)]


def load_key():
    v = os.environ.get("VISION_API_KEY") or os.environ.get("OPENCODE_API_KEY")
    if v:
        return v.strip()
    p = os.environ.get("OPENCODE_AUTH_PATH") or pathlib.Path.home() / ".local" / "share" / "opencode" / "auth.json"
    try:
        auth = json.loads(p.read_text(encoding="utf-8"))
        k = (auth.get("opencode-go") or auth.get("opencode_go") or {}).get("key")
        return k.strip() if k else None
    except Exception:
        return None


def transcribe(char_id, idx, attempt=1):
    mp3 = VOICES / char_id / "ja" / f"{idx}.mp3"
    b64 = base64.b64encode(mp3.read_bytes()).decode("ascii")
    prompt = ("这是一段日语语音。请逐字转写，不要省略开头，保留促音、长音和语气词，"
              "不要改写为标准语，不要输出任何解释。只输出转写结果。")
    body = {"model": MODEL, "messages": [{"role": "user", "content": [
        {"type": "text", "text": prompt},
        {"type": "input_audio", "input_audio": {"data": "data:audio/mpeg;base64," + b64}},
    ]}], "max_tokens": 1024}
    r = requests.post(API, headers={"Content-Type": "application/json", "Authorization": "Bearer " + KEY},
                      data=json.dumps(body, ensure_ascii=False).encode("utf-8"), timeout=90)
    if r.status_code != 200:
        return f"ERR {r.status_code}: {r.text[:150]}"
    content = ((r.json().get("choices") or [{}])[0].get("message", {}).get("content") or "").strip()
    return content


KEY = load_key()
if __name__ == "__main__":
    import requests
    tips = json.loads((ROOT / "app" / "src" / "main" / "assets" / "builtin_tips" / "tips_ja.json").read_text(encoding="utf-8"))
    for c, i in TARGETS:
        print(f"== {c}/{i} ==")
        print(f"  期望: {tips[c][i]['text']}")
        for a in range(1, 4):
            t = transcribe(c, i, a)
            print(f"  尝试{a}: {t}")
            if t and not any(m in t for m in ("无法", "できません", "申し訳", "抱歉")):
                break
