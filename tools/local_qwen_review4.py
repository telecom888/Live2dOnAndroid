# -*- coding: utf-8 -*-
"""本地 Qwen3-TTS 第四版：输出固定裁剪开头 0.1s + 时长保护（过短自动换种子重试）。"""
import io
import json
import os
import re
import subprocess
import sys
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["PYTHONIOENCODING"] = "utf-8"

PROJECT_DIR = Path(r"D:\Anima Engine\.anima_engine\external\Qwen3-TTS-GGUF")
MODEL_DIR = Path(r"D:\qwentts-1.7b-base-gguf")
GGUF_BIN_DIR = PROJECT_DIR / "qwen_asr_gguf" / "bin"
TOOLS = Path(r"D:\opencode-proj\Live2dOnAndroid\tools")
OUT_DIR = TOOLS / "review_audio_local4"
ASSETS_TIPS_JA = Path(r"D:\opencode-proj\Live2dOnAndroid\app\src\main\assets\builtin_tips\tips_ja.json")
VOICE_DIR = Path(r"E:\无用文件\创意工坊\语音包")
ROLE_MAP = {"高松燈": "tomori", "千早愛音": "anon", "要樂奈": "rana", "長崎素世": "soyo", "椎名立希": "taki"}
FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"
TRIM_SECONDS = 0.1
MIN_SECONDS = 0.4

if not PROJECT_DIR.exists() or not MODEL_DIR.exists():
    raise SystemExit("Qwen3-TTS 环境缺失")
if not GGUF_BIN_DIR.exists():
    raise SystemExit("GGUF DLL 目录缺失")

os.chdir(PROJECT_DIR)
if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))
os.environ["PATH"] = str(GGUF_BIN_DIR) + os.pathsep + os.environ.get("PATH", "")
try:
    os.add_dll_directory(str(GGUF_BIN_DIR))
except Exception:
    pass

from qwen3_tts_gguf.inference import TTSEngine, TTSConfig

STATEMENT_END = re.compile(r"[？?!！～…]$")
CUSTOM_LONG = {"tomori", "taki"}


def pick_reference(char_id):
    role = {v: k for k, v in ROLE_MAP.items()}[char_id]
    role_dir = VOICE_DIR / role
    cands = []
    for name in os.listdir(role_dir):
        if not name.lower().endswith(".mp3"):
            continue
        size = os.path.getsize(role_dir / name)
        if char_id in CUSTOM_LONG:
            if not (100_000 <= size <= 220_000):
                continue
            base = name.rsplit(".", 1)[0].strip()
            if STATEMENT_END.search(base):
                continue
            if len(base) < 12:
                continue
            cands.append((size, name))
        else:
            if 50_000 <= size <= 220_000:
                cands.append((size, name))
    if not cands:
        return None
    if char_id in CUSTOM_LONG:
        cands.sort(reverse=True)
        return cands[0][1]
    cands.sort()
    best = min(cands, key=lambda x: abs(x[0] - 90_000))
    return best[1]


def clean_audio(src, dst):
    subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-i", str(src), "-ar", "24000", "-ac", "1",
         "-af", "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.06,areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.06,areverse",
         str(dst)],
        check=True,
    )


def wav_seconds(path):
    proc = subprocess.run([FFMPEG, "-y", "-v", "error", "-i", str(path), "-f", "s16le", "-ac", "1", "-ar", "24000", "pipe:1"], capture_output=True)
    return len(proc.stdout) / 24000.0 / 2.0


def trim_head(src_wav, dst_mp3):
    subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-i", str(src_wav),
         "-af", "atrim=start=%f,asetpts=PTS-STARTPTS" % TRIM_SECONDS,
         "-b:a", "128k", str(dst_mp3)],
        check=True,
    )


def main():
    with io.open(ASSETS_TIPS_JA, encoding="utf-8") as f:
        tips_ja = json.load(f)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    engine = TTSEngine(str(MODEL_DIR), verbose=False)
    try:
        for role, char_id in ROLE_MAP.items():
            name = pick_reference(char_id)
            if name is None:
                print("skip", char_id); continue
            src_mp3 = VOICE_DIR / role / name
            ref_text = name.rsplit(".", 1)[0]
            ref_wav = OUT_DIR / ("%s_ref.wav" % char_id)
            clean_audio(src_mp3, ref_wav)
            print("ref %s: %s" % (char_id, ref_text[:50]), flush=True)

            stream = engine.create_stream()
            stream.set_voice(str(ref_wav), text=ref_text)
            stream.join()

            lines = tips_ja.get(char_id, [])
            for idx in range(min(2, len(lines))):
                text = lines[idx].get("text", "")
                if not text:
                    continue
                out_raw = OUT_DIR / ("%s_%d_raw.wav" % (char_id, idx + 1))
                out_mp3 = OUT_DIR / ("%s_%d.mp3" % (char_id, idx + 1))
                generated = False
                for seed_shift in range(3):
                    try:
                        config = TTSConfig(temperature=0.5, sub_temperature=0.5, seed=42 + seed_shift, sub_seed=45 + seed_shift, streaming=True)
                        result = stream.clone(text, config=config)
                        stream.join()
                        result.save(str(out_raw))
                        secs = wav_seconds(out_raw)
                        if secs < MIN_SECONDS:
                            print("too short %.2fs, retry seed %d" % (secs, seed_shift), flush=True)
                            continue
                        trim_head(out_raw, out_mp3)
                        print("OK %s_%d dur=%.2fs" % (char_id, idx + 1, max(secs - TRIM_SECONDS, 0.0)), flush=True)
                        generated = True
                        break
                    except Exception as e:
                        print("ERR %s_%d %s" % (char_id, idx + 1, repr(e)[:150]), flush=True)
                if not generated:
                    print("FAIL %s_%d" % (char_id, idx + 1), flush=True)
            stream = None
    finally:
        engine.shutdown()
    print("DONE")


if __name__ == "__main__":
    main()
