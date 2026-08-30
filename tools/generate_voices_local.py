# -*- coding: utf-8 -*-
"""批量生成日语内置语音（本地 Qwen3-TTS，参考配置已锁定，输出 mp3）。
用法: python tools/generate_voices_local.py [ja]
- 参考句：tomori/taki 用长陈述句；anon/rana/soyo 用约90KB句
- 单字は -> ハ（避免读成助词 wa）
- 输出裁剪开头 0.2s；短音频自动换种子重试，仍短则跳过
- 断点续传：已存在 mp3 跳过"""
import glob
import io
import json
import math
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
ASSETS_ROOT = Path(r"D:\opencode-proj\Live2dOnAndroid\app\src\main\assets")
ASSETS_TIPS_JA = ASSETS_ROOT / "builtin_tips" / "tips_ja.json"
VOICE_DIR = Path(r"E:\无用文件\创意工坊\语音包")
ROLE_MAP = {"高松燈": "tomori", "千早愛音": "anon", "要樂奈": "rana", "長崎素世": "soyo", "椎名立希": "taki"}
FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"
TRIM_SECONDS = 0.2
MIN_SECONDS = 0.4
STATEMENT_END = re.compile(r"[？?!！～…]$")
CUSTOM_LONG = {"tomori", "taki"}


def fix_ha(text):
    """日语：独立单字「は」是助词读音 wa；作为感叹词应读 ha，替换为片假名 ハ。"""
    t = text.strip()
    if re.fullmatch(r"は[？!！]?", t):
        return t.replace("は", "ハ", 1)
    return text


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
            if STATEMENT_END.search(base) or len(base) < 12:
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
    return min(cands, key=lambda x: abs(x[0] - 90_000))[1]


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

SILENCE_RMS = 400
VOICE_RMS = 800
SILENCE_WINDOWS = 12
VOICE_WINDOWS = 10
KEEP_BEFORE = 0.05
VAD_WINDOW = 0.02


def rms_windows(path):
    import struct as _struct
    import wave as _wave
    with _wave.open(str(path), "rb") as w:
        rate = w.getframerate()
        n = w.getnframes()
        data = w.readframes(n)
        nch = w.getnchannels()
        sw = w.getsampwidth()
        fmt = "<%dh" % (len(data) // sw)
        samples = _struct.unpack(fmt, data)
    chunk = max(1, int(rate * VAD_WINDOW))
    out = []
    for start in range(0, max(1, n - chunk + 1), chunk):
        seg = samples[start * nch:(start + chunk) * nch]
        if not seg:
            break
        out.append(math.sqrt(sum(x * x for x in seg) / len(seg)))
    return out, rate


def find_voice_start(rms):
    n = len(rms)
    run = 0
    silence_end = -1
    for i, r in enumerate(rms):
        if r < SILENCE_RMS:
            run += 1
            if run >= SILENCE_WINDOWS:
                silence_end = i + 1
                break
        else:
            run = 0
    if silence_end >= 0:
        for j in range(silence_end, n):
            if rms[j] >= VOICE_RMS:
                return j * VAD_WINDOW
        return None
    run = 0
    for i, r in enumerate(rms):
        if r >= VOICE_RMS:
            run += 1
            if run >= VOICE_WINDOWS:
                return (i - run + 1) * VAD_WINDOW
        else:
            run = 0
    return None


def vad_trim_to_mp3(raw_wav, target_mp3):
    rms, rate = rms_windows(raw_wav)
    start = find_voice_start(rms)
    t = max(0.0, (start if start is not None else 0.0) - KEEP_BEFORE)
    subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-i", str(raw_wav),
         "-af", "atrim=start=%f,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.01" % t,
         "-b:a", "128k", str(target_mp3)],
        check=True,
    )



def main():
    targets = sys.argv[1:] or list(ROLE_MAP.values())
    with io.open(ASSETS_TIPS_JA, encoding="utf-8") as f:
        tips_ja = json.load(f)
    tmp_dir = Path(r"D:\opencode-proj\Live2dOnAndroid\tools\gen_tmp")
    tmp_dir.mkdir(parents=True, exist_ok=True)

    os.chdir(PROJECT_DIR)
    sys.path.insert(0, str(PROJECT_DIR))
    os.environ["PATH"] = str(GGUF_BIN_DIR) + os.pathsep + os.environ.get("PATH", "")
    try:
        os.add_dll_directory(str(GGUF_BIN_DIR))
    except Exception:
        pass
    from qwen3_tts_gguf.inference import TTSEngine, TTSConfig

    engine = TTSEngine(str(MODEL_DIR), verbose=False)
    ok_count = 0
    skip_count = 0
    fail_count = 0
    try:
        for role, char_id in ROLE_MAP.items():
            if char_id not in targets:
                continue
            name = pick_reference(char_id)
            if name is None:
                print("skip char", char_id); continue
            ref_wav = tmp_dir / ("%s_ref.wav" % char_id)
            clean_audio(VOICE_DIR / role / name, ref_wav)
            ref_text = name.rsplit(".", 1)[0]
            print("== voice %s ref=%s" % (char_id, ref_text[:50]), flush=True)

            stream = engine.create_stream()
            stream.set_voice(str(ref_wav), text=ref_text)
            stream.join()

            out_dir = ASSETS_ROOT / "voices_builtin" / char_id / "ja"
            out_dir.mkdir(parents=True, exist_ok=True)
            lines = tips_ja.get(char_id, [])
            for idx, item in enumerate(lines):
                target_mp3 = out_dir / ("%d.mp3" % idx)
                if target_mp3.exists() and os.path.getsize(target_mp3) > 10_000:
                    skip_count += 1
                    continue
                text = fix_ha(item.get("text", ""))
                if not text:
                    continue
                raw_wav = tmp_dir / ("%s_%d.wav" % (char_id, idx))
                generated = False
                for shift in range(3):
                    try:
                        config = TTSConfig(temperature=0.5, sub_temperature=0.5, seed=42 + shift, sub_seed=45 + shift, streaming=False, min_p=0.05, sub_do_sample=False)
                        result = stream.clone(text, config=config)
                        stream.join()
                        result.save(str(raw_wav))
                        if wav_seconds(raw_wav) < MIN_SECONDS:
                            continue
                        vad_trim_to_mp3(raw_wav, target_mp3)
                        ok_count += 1
                        generated = True
                        print("OK %s %d" % (char_id, idx), flush=True)
                        break
                    except Exception as e:
                        print("ERR %s %d %s" % (char_id, idx, repr(e)[:120]), flush=True)
                if not generated:
                    fail_count += 1
                    print("FAIL %s %d" % (char_id, idx), flush=True)
            stream = None
    finally:
        engine.shutdown()
    print("DONE ok=%d skip=%d fail=%d" % (ok_count, skip_count, fail_count))


if __name__ == "__main__":
    main()
