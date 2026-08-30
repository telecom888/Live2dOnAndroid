# -*- coding: utf-8 -*-
"""本地 Qwen3-TTS-GGUF 音色克隆样例生成（修复：UTF-8 子进程、参考句直接取自语音包）。"""
import io
import json
import os
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
OUT_DIR = TOOLS / "review_audio_local"
ASSETS_TIPS_JA = Path(r"D:\opencode-proj\Live2dOnAndroid\app\src\main\assets\builtin_tips\tips_ja.json")
VOICE_DIR = Path(r"E:\无用文件\创意工坊\语音包")
ROLE_MAP = {"高松燈": "tomori", "千早愛音": "anon", "要樂奈": "rana", "長崎素世": "soyo", "椎名立希": "taki"}
FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"

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


def pick_reference(char_id):
    role = {v: k for k, v in ROLE_MAP.items()}[char_id]
    role_dir = VOICE_DIR / role
    cands = []
    for name in os.listdir(role_dir):
        if not name.lower().endswith(".mp3"):
            continue
        size = os.path.getsize(role_dir / name)
        if 50_000 <= size <= 220_000:
            cands.append((size, name))
    if not cands:
        return None
    cands.sort()
    # 取最接近 90KB 的一句（完整句、长度适中）
    best = min(cands, key=lambda x: abs(x[0] - 90_000))
    return best[1]


def to_wav24k(src, dst):
    subprocess.run([FFMPEG, "-y", "-v", "error", "-i", str(src), "-ar", "24000", "-ac", "1", str(dst)], check=True)


def main():
    with io.open(ASSETS_TIPS_JA, encoding="utf-8") as f:
        tips_ja = json.load(f)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    engine = TTSEngine(str(MODEL_DIR), verbose=False)
    try:
        for role, char_id in ROLE_MAP.items():
            name = pick_reference(char_id)
            if name is None:
                print("skip", char_id, "no ref"); continue
            src_mp3 = VOICE_DIR / role / name
            ref_text = name.rsplit(".", 1)[0]
            wav24k = OUT_DIR / ("%s_ref.wav" % char_id)
            to_wav24k(src_mp3, wav24k)
            print("voice", char_id, "ref:", ref_text[:50], flush=True)

            stream = engine.create_stream()
            stream.set_voice(str(wav24k), text=ref_text)
            stream.join()

            config = TTSConfig(temperature=0.5, sub_temperature=0.5, seed=42, sub_seed=45, streaming=True)
            lines = tips_ja.get(char_id, [])
            for idx in range(min(2, len(lines))):
                text = lines[idx].get("text", "")
                if not text:
                    continue
                try:
                    result = stream.clone(text, config=config)
                    stream.join()
                    out_wav = OUT_DIR / ("%s_%d.wav" % (char_id, idx + 1))
                    result.save(str(out_wav))
                    out_mp3 = OUT_DIR / ("%s_%d.mp3" % (char_id, idx + 1))
                    subprocess.run([FFMPEG, "-y", "-v", "error", "-i", str(out_wav), "-b:a", "128k", str(out_mp3)], check=True)
                    print("OK", char_id, idx + 1, out_mp3, flush=True)
                except Exception as e:
                    print("ERR", char_id, idx + 1, repr(e)[:200], flush=True)
            stream = None
    finally:
        engine.shutdown()
    print("DONE")


if __name__ == "__main__":
    main()
