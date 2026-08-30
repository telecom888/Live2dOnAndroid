# -*- coding: utf-8 -*-
"""本地 Qwen3-TTS 克隆改进版：每角色 4 句参考拼接（裁剪句首静音）+ 生成日语样例。"""
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
OUT_DIR = TOOLS / "review_audio_local2"
ASSETS_TIPS_JA = Path(r"D:\opencode-proj\Live2dOnAndroid\app\src\main\assets\builtin_tips\tips_ja.json")
VOICE_DIR = Path(r"E:\无用文件\创意工坊\语音包")
ROLE_MAP = {"高松燈": "tomori", "千早愛音": "anon", "要樂奈": "rana", "長崎素世": "soyo", "椎名立希": "taki"}
FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"
REF_COUNT = 4
SILENCE_SECONDS = 0.12

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


def pick_references(char_id, count=REF_COUNT):
    role = {v: k for k, v in ROLE_MAP.items()}[char_id]
    role_dir = VOICE_DIR / role
    cands = []
    for name in os.listdir(role_dir):
        if not name.lower().endswith(".mp3"):
            continue
        size = os.path.getsize(role_dir / name)
        if 55_000 <= size <= 220_000:
            cands.append((size, name))
    cands.sort()
    if not cands:
        return []
    if len(cands) <= count:
        return [name for _, name in cands]
    step = (len(cands) - 1) / count
    picked = [cands[int(round(i * step))] for i in range(count)]
    return [name for _, name in picked]


def build_reference(char_id):
    role = {v: k for k, v in ROLE_MAP.items()}[char_id]
    role_dir = VOICE_DIR / role
    names = pick_references(char_id)
    if not names:
        return None, None
    pcm = bytearray()
    silence = bytes([0]) * int(24000 * 2 * SILENCE_SECONDS)  # s16le mono 24000
    texts = []
    for name in names:
        src = role_dir / name
        proc = subprocess.run(
            [FFMPEG, "-y", "-v", "error", "-i", str(src), "-ar", "24000", "-ac", "1", "-af", "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.08,areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.08,areverse", "-f", "s16le", "pipe:1"],
            capture_output=True,
        )
        if proc.returncode != 0 or not proc.stdout:
            continue
        if pcm:
            pcm.extend(silence)
        pcm.extend(proc.stdout)
        texts.append(name.rsplit(".", 1)[0])
    if not pcm:
        return None, None
    ref_wav = OUT_DIR / ("%s_ref.wav" % char_id)
    subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-f", "s16le", "-ar", "24000", "-ac", "1", "-i", "pipe:0", str(ref_wav)],
        input=bytes(pcm),
        check=True,
    )
    ref_text = "\n".join(texts)
    print("ref %s: %d 句, %.1fs, %s" % (char_id, len(texts), len(pcm) / 24000 / 2, ref_text[:60]), flush=True)
    return ref_wav, ref_text


def main():
    with io.open(ASSETS_TIPS_JA, encoding="utf-8") as f:
        tips_ja = json.load(f)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    engine = TTSEngine(str(MODEL_DIR), verbose=False)
    try:
        for role, char_id in ROLE_MAP.items():
            ref_wav, ref_text = build_reference(char_id)
            if ref_wav is None:
                print("skip", char_id); continue
            stream = engine.create_stream()
            stream.set_voice(str(ref_wav), text=ref_text)
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
                    print("OK", char_id, idx + 1, flush=True)
                except Exception as e:
                    print("ERR", char_id, idx + 1, repr(e)[:200], flush=True)
            stream = None
    finally:
        engine.shutdown()
    print("DONE")


if __name__ == "__main__":
    main()
