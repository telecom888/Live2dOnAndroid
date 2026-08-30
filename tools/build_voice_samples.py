# -*- coding: utf-8 -*-
"""把语音包中每个角色的全部音频拼接为综合克隆源，并生成试听样例语音。
用法: set MIMO_API_KEY=... 然后 python tools/build_voice_samples.py
输出: tools/clone_samples/<char>/combined.mp3, tools/review_audio/<char>_1/2.mp3"""
import base64
import glob
import io
import json
import os
import subprocess
import sys
import time
import urllib.request

FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"
VOICE_DIR = r"E:\无用文件\创意工坊\语音包"
ROLE_MAP = {
    "高松燈": "tomori",
    "千早愛音": "anon",
    "要樂奈": "rana",
    "長崎素世": "soyo",
    "椎名立希": "taki",
}
TOOLS = os.path.dirname(os.path.abspath(__file__))
SAMPLE_DIR = os.path.join(TOOLS, "clone_samples")
REVIEW_DIR = os.path.join(TOOLS, "review_audio")
ASSETS_TIPS_JA = os.path.join(os.path.dirname(TOOLS), "app", "src", "main", "assets", "builtin_tips", "tips_ja.json")
MAX_SECONDS = 240
CLIP_SECONDS = 1.5
BASE = "https://api.xiaomimimo.com/v1/chat/completions"
MODEL = "mimo-v2.5-tts-voiceclone"


def safe(s):
    return s.encode("ascii", "replace").decode()


def run_ffmpeg(args, input_bytes=None):
    return subprocess.run([FFMPEG, "-y", "-v", "error"] + args, input=input_bytes, capture_output=True)


def build_combined(char_id, role):
    files = sorted(glob.glob(os.path.join(VOICE_DIR, role, "*.mp3")))
    pcm = bytearray()
    duration = 0.0
    used = 0
    for f in files:
        if duration >= MAX_SECONDS:
            break
        try:
            proc = subprocess.run(
                [FFMPEG, "-y", "-v", "error", "-i", f, "-t", str(CLIP_SECONDS), "-f", "s16le", "-ar", "44100", "-ac", "1", "pipe:1"],
                capture_output=True,
            )
        except Exception as e:
            continue
        if proc.returncode != 0:
            continue
        pcm.extend(proc.stdout)
        duration += len(proc.stdout) / 44100.0 / 2.0
        used += 1
    out = os.path.join(SAMPLE_DIR, char_id, "combined.mp3")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    proc = subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-f", "s16le", "-ar", "44100", "-ac", "1", "-i", "pipe:0", "-b:a", "64k", out],
        input=bytes(pcm),
        capture_output=True,
    )
    if proc.returncode != 0 or not os.path.exists(out):
        print("FAIL build_combined", char_id, proc.stderr.decode("utf-8", "replace")[:200])
        return None
    print("combined %s files=%d/%d dur=%.1fs size=%d" % (char_id, used, len(files), duration, os.path.getsize(out)))
    return out


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
        headers={"Content-Type": "application/json", "Authorization": "Bearer %s" % os.environ["MIMO_API_KEY"]},
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return base64.b64decode(data["choices"][0]["message"]["audio"]["data"])


def main():
    with io.open(ASSETS_TIPS_JA, encoding="utf-8") as f:
        tips_ja = json.load(f)
    os.makedirs(REVIEW_DIR, exist_ok=True)
    manifest = {}
    for role, char_id in ROLE_MAP.items():
        combined = build_combined(char_id, role)
        if combined is None:
            continue
        manifest[char_id] = {"combined": combined}
        lines = tips_ja.get(char_id, [])
        for idx in range(min(2, len(lines))):
            text = lines[idx].get("text", "")
            if not text:
                continue
            out_wav = os.path.join(REVIEW_DIR, "%s_%d.wav" % (char_id, idx + 1))
            out_mp3 = os.path.join(REVIEW_DIR, "%s_%d.mp3" % (char_id, idx + 1))
            ok = False
            for attempt in range(3):
                try:
                    wav = synthesize(text, combined)
                    if wav and len(wav) > 100:
                        with open(out_wav, "wb") as f:
                            f.write(wav)
                        run_ffmpeg(["-i", out_wav, "-b:a", "128k", out_mp3])
                        print("OK review %s_%d text=%s size=%d" % (char_id, idx + 1, safe(text)[:30], len(wav)))
                        ok = True
                        break
                except Exception as e:
                    print("ERR review %s_%d %s" % (char_id, idx + 1, repr(e)[:120]))
                    time.sleep(2)
            if not ok:
                print("FAIL review %s_%d" % (char_id, idx + 1))
            time.sleep(0.2)
    with io.open(os.path.join(SAMPLE_DIR, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print("DONE")


if __name__ == "__main__":
    main()
