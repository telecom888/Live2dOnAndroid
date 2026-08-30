# -*- coding: utf-8 -*-
"""Qwen3-TTS 参数对比：定位开头杂音。"""
import io, json, os, subprocess, sys
from pathlib import Path

def main():
    os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
    os.environ["OMP_NUM_THREADS"] = "1"
    os.environ["PYTHONIOENCODING"] = "utf-8"

    PROJECT_DIR = Path(r"D:\Anima Engine\.anima_engine\external\Qwen3-TTS-GGUF")
    MODEL_DIR = Path(r"D:\qwentts-1.7b-base-gguf")
    GGUF_BIN_DIR = PROJECT_DIR / "qwen_asr_gguf" / "bin"
    TOOLS = Path(r"D:\opencode-proj\Live2dOnAndroid\tools")
    OUT_DIR = TOOLS / "param_test"
    FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"

    os.chdir(PROJECT_DIR)
    sys.path.insert(0, str(PROJECT_DIR))
    os.environ["PATH"] = str(GGUF_BIN_DIR) + os.pathsep + os.environ.get("PATH", "")
    try:
        os.add_dll_directory(str(GGUF_BIN_DIR))
    except Exception:
        pass
    from qwen3_tts_gguf.inference import TTSEngine, TTSConfig

    ref_wav = TOOLS / "review_audio_local4" / "anon_ref.wav"
    ref_text = "私はもー無理だよ……他のバンドみんなすごすぎだし、リハの時ギター弾きながら歌ってる人いたし……"
    with io.open(Path(r"D:\opencode-proj\Live2dOnAndroid\app\src\main\assets\builtin_tips\tips_ja.json"), encoding="utf-8") as f:
        tips_ja = json.load(f)
    target = tips_ja["anon"][1]["text"]
    print("target:", target, flush=True)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    engine = TTSEngine(str(MODEL_DIR), verbose=False)
    try:
        stream = engine.create_stream()
        stream.set_voice(str(ref_wav), text=ref_text)
        stream.join()
        combos = {
            "A_current": dict(temperature=0.5, sub_temperature=0.5, seed=42, sub_seed=45, streaming=True, min_p=0.0, sub_do_sample=True),
            "B_minp_fix": dict(temperature=0.5, sub_temperature=0.5, seed=42, sub_seed=45, streaming=True, min_p=0.05, sub_do_sample=False),
            "C_offline": dict(temperature=0.5, sub_temperature=0.5, seed=42, sub_seed=45, streaming=False, min_p=0.05, sub_do_sample=False),
            "D_lowsub": dict(temperature=0.5, sub_temperature=0.3, seed=42, sub_seed=45, streaming=False, min_p=0.05, sub_do_sample=False),
        }
        for name, kw in combos.items():
            try:
                config = TTSConfig(**kw)
                result = stream.clone(target, config=config)
                stream.join()
                wav = OUT_DIR / ("%s.wav" % name)
                result.save(str(wav))
                mp3 = OUT_DIR / ("%s.mp3" % name)
                subprocess.run([FFMPEG, "-y", "-v", "error", "-i", str(wav), "-b:a", "128k", str(mp3)], check=True)
                print("OK", name, flush=True)
            except Exception as e:
                print("ERR", name, repr(e)[:200], flush=True)
    finally:
        engine.shutdown()
    print("DONE")

if __name__ == "__main__":
    main()
