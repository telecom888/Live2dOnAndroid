# -*- coding: utf-8 -*-
"""填充试验：正文+填充文字生成，分析语音段结构，确定裁剪逻辑。"""
import os
import sys
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["PYTHONIOENCODING"] = "utf-8"

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from generate_voices_local import (PROJECT_DIR, MODEL_DIR, GGUF_BIN_DIR, VOICE_DIR, ROLE_MAP,
                                   clean_audio, rms_windows, VAD_WINDOW)

TMP = ROOT / "tools" / "gen_tmp"
PAD = "。えーっと、あー、えーっと、あー"
TEXT = "ラッキーセットのおまけ。猫だった"
VOICE_RMS = 800
SILENCE_RMS = 400
MIN_SEG = 0.15  # 活跃段最小秒数
GAP = 0.25      # 段间静音阈值


def segments(rms):
    segs = []
    cur = None
    for i, r in enumerate(rms):
        active = r >= VOICE_RMS
        if active and cur is None:
            cur = [i, i]
        elif active and cur is not None:
            cur[1] = i
        elif not active and cur is not None:
            # 允许短于 GAP 的静音不打断
            if i - cur[1] > GAP / VAD_WINDOW:
                segs.append((cur[0], cur[1], (cur[1] - cur[0] + 1) * VAD_WINDOW))
                cur = None
        else:
            pass
    if cur is not None:
        segs.append((cur[0], cur[1], (cur[1] - cur[0] + 1) * VAD_WINDOW))
    return segs


def main():
    os.chdir(PROJECT_DIR)
    sys.path.insert(0, str(PROJECT_DIR))
    os.environ["PATH"] = str(GGUF_BIN_DIR) + os.pathsep + os.environ.get("PATH", "")
    try:
        os.add_dll_directory(str(GGUF_BIN_DIR))
    except Exception:
        pass
    from qwen3_tts_gguf.inference import TTSEngine, TTSConfig

    role = {v: k for k, v in ROLE_MAP.items()}["rana"]
    name = "抹茶生地に抹茶クリームと抹茶アイスと抹茶チョコ。仕上げにたっぷり抹茶ソース.mp3"
    ref_wav = TMP / "rana_pad_test_ref.wav"
    clean_audio(VOICE_DIR / role / name, ref_wav)
    engine = TTSEngine(str(MODEL_DIR), verbose=False)
    stream = engine.create_stream()
    stream.set_voice(str(ref_wav), text=name.rsplit(".", 1)[0])
    stream.join()
    try:
        for seed in (42, 43, 44):
            raw = TMP / f"rana_pad_test_{seed}.wav"
            config = TTSConfig(temperature=0.5, sub_temperature=0.5, seed=seed, sub_seed=45 + (seed - 42),
                               streaming=False, min_p=0.05, sub_do_sample=False)
            result = stream.clone(TEXT + PAD, config=config)
            stream.join()
            result.save(str(raw))
            rms, rate = rms_windows(raw)
            segs = segments(rms)
            print(f"== seed={seed} total={len(rms)*VAD_WINDOW:.2f}s segs={len(segs)}")
            for s, e, d in segs:
                print(f"   seg {s*VAD_WINDOW:.2f}-{e*VAD_WINDOW:.2f}s dur={d:.2f}s")
    finally:
        stream = None
        engine.shutdown()


if __name__ == "__main__":
    main()
