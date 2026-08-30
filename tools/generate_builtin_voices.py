# -*- coding: utf-8 -*-
"""预生成内置台词语音（mimo voiceclone TTS），封装进 assets/voices_builtin。
用法: set MIMO_API_KEY=... 然后 python tools/generate_builtin_voices.py
断点续传：已存在且非空的 wav 会跳过。"""
import base64
import io
import json
import os
import sys
import time
import urllib.request

BASE = "https://api.xiaomimimo.com/v1/chat/completions"
MODEL = "mimo-v2.5-tts-voiceclone"
ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "assets")
DEFAULT_SAMPLES = {
    "tomori": "tomori.mp3",
    "anon": "anon.mp3",
    "rana": "rana.mp3",
    "soyo": "soyo.mp3",
    "taki": "taki.mp3",
}


def synthesize(text, sample_path):
    with open(sample_path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("ascii")
    mime = "audio/wav" if sample_path.lower().endswith(".wav") else "audio/mpeg"
    body = {
        "model": MODEL,
        "messages": [
            {"role": "user", "content": ""},
            {"role": "assistant", "content": text},
        ],
        "audio": {"format": "wav", "voice": "data:%s;base64,%s" % (mime, b64)},
    }
    req = urllib.request.Request(
        BASE,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer %s" % os.environ["MIMO_API_KEY"],
        },
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return base64.b64decode(data["choices"][0]["message"]["audio"]["data"])


def main():
    langs = {"zh": "tips.json", "ja": "tips_ja.json"}
    total_ok = 0
    total_skip = 0
    for lang, file_name in langs.items():
        tips_path = os.path.join(ROOT, "builtin_tips", file_name)
        with io.open(tips_path, encoding="utf-8") as f:
            tips = json.load(f)
        for char, lines in tips.items():
            sample_name = DEFAULT_SAMPLES.get(char, char + ".mp3")
            sample = os.path.join(ROOT, "voices", sample_name)
            if not os.path.exists(sample):
                print("skip char %s (no sample)" % char, flush=True)
                continue
            out_dir = os.path.join(ROOT, "voices_builtin", char, lang)
            os.makedirs(out_dir, exist_ok=True)
            for i, item in enumerate(lines):
                target = os.path.join(out_dir, "%d.wav" % i)
                if os.path.exists(target) and os.path.getsize(target) > 100:
                    total_skip += 1
                    continue
                text = (item or {}).get("text", "")
                if not text:
                    continue
                ok = False
                for attempt in range(3):
                    try:
                        wav = synthesize(text, sample)
                        if wav and len(wav) > 100:
                            with open(target, "wb") as f:
                                f.write(wav)
                            total_ok += 1
                            print("OK %s %s %d %d" % (lang, char, i, len(wav)), flush=True)
                            ok = True
                            break
                    except Exception as e:
                        print("ERR %s %s %d %s" % (lang, char, i, repr(e)[:120]), flush=True)
                        time.sleep(2)
                if not ok:
                    print("FAIL %s %s %d" % (lang, char, i), flush=True)
                time.sleep(0.2)
    print("DONE ok=%d skip=%d" % (total_ok, total_skip), flush=True)


if __name__ == "__main__":
    main()
