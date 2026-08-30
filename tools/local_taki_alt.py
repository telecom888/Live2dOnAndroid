# -*- coding: utf-8 -*-
import io, json, os, subprocess, sys
from pathlib import Path
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["PYTHONIOENCODING"] = "utf-8"

PROJECT_DIR = Path(r"D:\Anima Engine\.anima_engine\external\Qwen3-TTS-GGUF")
MODEL_DIR = Path(r"D:\qwentts-1.7b-base-gguf")
GGUF_BIN_DIR = PROJECT_DIR / "qwen_asr_gguf" / "bin"
OUT_DIR = Path(r"D:\opencode-proj\Live2dOnAndroid\tools\review_audio_local4")
FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"

os.chdir(PROJECT_DIR)
sys.path.insert(0, str(PROJECT_DIR))
os.environ["PATH"] = str(GGUF_BIN_DIR) + os.pathsep + os.environ.get("PATH", "")
try:
    os.add_dll_directory(str(GGUF_BIN_DIR))
except Exception:
    pass
from qwen3_tts_gguf.inference import TTSEngine, TTSConfig

ref_wav = OUT_DIR / "taki_ref.wav"
ref_text = "燈の歌詞と歌は、本当にすごいと思ってる。自分の中にあるモヤモヤが、そのまま言葉になって、歌にされてるみたいな感じ"
target = "燈の歌、いいよね。私もこういうの、作ってみたいなって思う"
engine = TTSEngine(str(MODEL_DIR), verbose=False)
try:
    stream = engine.create_stream()
    stream.set_voice(str(ref_wav), text=ref_text)
    stream.join()
    result = stream.clone(target, config=TTSConfig(temperature=0.5, sub_temperature=0.5, seed=42, sub_seed=45, streaming=True))
    stream.join()
    out_wav = OUT_DIR / "taki_alt.wav"
    result.save(str(out_wav))
    subprocess.run([FFMPEG, "-y", "-v", "error", "-i", str(out_wav), "-af", "atrim=start=0.1,asetpts=PTS-STARTPTS", "-b:a", "128k", str(OUT_DIR / "taki_alt.mp3")], check=True)
    print("OK taki_alt")
finally:
    engine.shutdown()
