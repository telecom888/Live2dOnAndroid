# -*- coding: utf-8 -*-
"""乐奈截断条目暴力重试：30 个 seed，保留时长最接近期望的候选。"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["PYTHONIOENCODING"] = "utf-8"

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from generate_voices_local import (PROJECT_DIR, MODEL_DIR, GGUF_BIN_DIR, ASSETS_ROOT, VOICE_DIR,
                                   ROLE_MAP, pick_reference, clean_audio, wav_seconds,
                                   vad_trim_to_mp3, fix_ha)

TIPS_JA = ASSETS_ROOT / "builtin_tips" / "tips_ja.json"
OUT_DIR = ASSETS_ROOT / "voices_builtin"
TARGETS = [("rana", 5), ("rana", 10), ("rana", 11), ("rana", 15), ("rana", 17),
           ("rana", 33), ("rana", 35), ("rana", 36), ("rana", 37)]
MAX_ATTEMPTS = 30
SEED_BASE = 42
PUNCT = re.compile(r"[\s、。「」『』（）()？！!！？….,。・～~\-—\u3000]")


def estimate_seconds(text):
    clean = PUNCT.sub("", text)
    return max(0.6, len(clean) / 7.0)


def main():
    tips = json.loads(TIPS_JA.read_text(encoding="utf-8"))
    tmp_dir = ROOT / "tools" / "gen_tmp"
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
    try:
        role = ROLE_MAP["要樂奈"]
        name = pick_reference("rana")
        ref_wav = tmp_dir / "rana_retry_ref.wav"
        clean_audio(VOICE_DIR / role / name, ref_wav)
        stream = engine.create_stream()
        stream.set_voice(str(ref_wav), text=name.rsplit(".", 1)[0])
        stream.join()
        print(f"== voice rana ref={name[:40]}", flush=True)

        for cid, idx in TARGETS:
            text = fix_ha(tips[cid][idx]["text"])
            exp = estimate_seconds(text)
            target_mp3 = OUT_DIR / cid / "ja" / f"{idx}.mp3"
            best = {"dur": -1, "seed": None}
            attempts = 0
            for shift in range(MAX_ATTEMPTS):
                raw = tmp_dir / f"rana_{idx}_retry.wav"
                try:
                    config = TTSConfig(temperature=0.5, sub_temperature=0.5,
                                       seed=SEED_BASE + shift, sub_seed=45 + shift,
                                       streaming=False, min_p=0.05, sub_do_sample=False)
                    result = stream.clone(text, config=config)
                    stream.join()
                    result.save(str(raw))
                    dur_raw = wav_seconds(raw)
                    if dur_raw < 0.3:
                        continue
                    vad_trim_to_mp3(raw, target_mp3)
                    dur = float(subprocess.run(
                        ["E:/ffmpeg/bin/ffprobe", "-v", "error", "-show_entries", "format=duration",
                         "-of", "default=noprint_wrappers=1:nokey=1", str(target_mp3)],
                        capture_output=True, text=True).stdout.strip() or 0)
                    attempts += 1
                    if 0.6 * exp <= dur <= 2.5 * exp:
                        if best["seed"] is None or abs(dur - exp) < abs(best["dur"] - exp):
                            best = {"dur": dur, "seed": SEED_BASE + shift}
                    # 已找到足够接近的就不继续
                    if best["seed"] is not None and abs(best["dur"] - exp) / exp < 0.15:
                        break
                except Exception as e:
                    print(f"  {cid}/{idx} seed={SEED_BASE+shift} ERR {repr(e)[:120]}", flush=True)
                finally:
                    if raw.exists():
                        raw.unlink()
            if best["seed"] is None:
                print(f"RESULT {cid}/{idx} 无达标 seed，保留原文件", flush=True)
            else:
                # 用 best seed 重新生成并保存最终文件
                raw = tmp_dir / f"rana_{idx}_retry.wav"
                config = TTSConfig(temperature=0.5, sub_temperature=0.5,
                                   seed=best["seed"], sub_seed=45 + (best["seed"] - SEED_BASE),
                                   streaming=False, min_p=0.05, sub_do_sample=False)
                result = stream.clone(text, config=config)
                stream.join()
                result.save(str(raw))
                vad_trim_to_mp3(raw, target_mp3)
                dur = float(subprocess.run(
                    ["E:/ffmpeg/bin/ffprobe", "-v", "error", "-show_entries", "format=duration",
                     "-of", "default=noprint_wrappers=1:nokey=1", str(target_mp3)],
                    capture_output=True, text=True).stdout.strip() or 0)
                raw.unlink()
                print(f"RESULT {cid}/{idx} seed={best['seed']} dur={dur:.2f}s exp={exp:.2f}s attempts={attempts}", flush=True)
        stream = None
    finally:
        engine.shutdown()


if __name__ == "__main__":
    main()
