# -*- coding: utf-8 -*-
"""严格转写核验刚重新生成的 19 条。"""
import base64
import concurrent.futures as cf
import json
import os
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
VOICES = ROOT / "app" / "src" / "main" / "assets" / "voices_builtin"
API = "https://opencode.ai/zen/go/v1/chat/completions"
MODEL = "mimo-v2.5"
TARGETS = [
    ("rana", 4), ("rana", 5), ("rana", 10), ("rana", 11), ("rana", 13), ("rana", 15),
    ("rana", 17), ("rana", 33), ("rana", 35), ("rana", 36), ("rana", 37),
    ("soyo", 35),
    ("anon", 54), ("anon", 60), ("anon", 61), ("anon", 69),
    ("soyo", 9), ("soyo", 29), ("soyo", 48),
]


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


def transcribe(char_id, idx):
    mp3 = VOICES / char_id / "ja" / f"{idx}.mp3"
    b64 = base64.b64encode(mp3.read_bytes()).decode("ascii")
    prompt = ("这是日语角色语音。请把语音中每一个字都逐字转写出来，绝对不要省略开头，"
              "保留促音っ、长音ー、语气词和停顿，不要改写为标准语。只输出转写文本。")
    body = {"model": MODEL, "messages": [{"role": "user", "content": [
        {"type": "text", "text": prompt},
        {"type": "input_audio", "input_audio": {"data": "data:audio/mpeg;base64," + b64}},
    ]}], "max_tokens": 1024}
    r = requests.post(API, headers={"Content-Type": "application/json", "Authorization": "Bearer " + KEY},
                      data=json.dumps(body, ensure_ascii=False).encode("utf-8"), timeout=90)
    if r.status_code != 200:
        return char_id, idx, "ERR " + str(r.status_code), ""
    content = ((r.json().get("choices") or [{}])[0].get("message", {}).get("content") or "").strip()
    return char_id, idx, content, ""


KEY = load_key()
if __name__ == "__main__":
    if not KEY:
        print("no key")
        sys.exit(1)
    import requests
    tips = json.loads((ROOT / "app" / "src" / "main" / "assets" / "builtin_tips" / "tips_ja.json").read_text(encoding="utf-8"))
    out = []
    with cf.ThreadPoolExecutor(max_workers=6) as ex:
        futs = {ex.submit(transcribe, c, i): (c, i) for c, i in TARGETS}
        for fut in cf.as_completed(futs):
            c, i, text, _ = fut.result()
            out.append((c, i, text))
    out.sort(key=lambda x: (x[0], x[1]))
    for c, i, text in out:
        exp = tips[c][i]["text"]
        dur = round((VOICES / c / "ja" / f"{i}.mp3").stat().st_size / 16000, 2)
        print(f"== {c}/{i} == dur~={dur}s")
        print(f"  期望: {exp}")
        print(f"  转写: {text}")
