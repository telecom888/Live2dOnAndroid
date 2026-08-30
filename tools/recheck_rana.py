# -*- coding: utf-8 -*-
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
TARGETS = [(4,5,10,11,13,15,17,33,35,36,37), ()]
TARGETS = [("rana", i) for i in (4,5,10,11,13,15,17,33,35,36,37)]


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
        return char_id, idx, "ERR " + str(r.status_code)
    return char_id, idx, ((r.json().get("choices") or [{}])[0].get("message", {}).get("content") or "").strip()


KEY = load_key()
if __name__ == "__main__":
    import requests
    tips = json.loads((ROOT / "app" / "src" / "main" / "assets" / "builtin_tips" / "tips_ja.json").read_text(encoding="utf-8"))
    out = []
    with cf.ThreadPoolExecutor(max_workers=6) as ex:
        futs = {ex.submit(transcribe, c, i): (c, i) for c, i in TARGETS}
        for fut in cf.as_completed(futs):
            c, i, text = fut.result()
            out.append((c, i, text))
    out.sort(key=lambda x: x[1])
    for c, i, text in out:
        print(f"== {c}/{i} ==")
        print(f"  期望: {tips[c][i]['text']}")
        print(f"  转写: {text}")
