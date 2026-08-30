# -*- coding: utf-8 -*-
"""tomori/taki 全量生成（plain 不填充，VAD 裁剪 + 前缀去噪）。

用法: python tools/generate_plain_batch.py [tomori|taki] [--skip-existing]
"""
import json
import os
import subprocess
import sys
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["PYTHONIOENCODING"] = "utf-8"

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from generate_voices_local import (PROJECT_DIR, MODEL_DIR, GGUF_BIN_DIR, ASSETS_ROOT, VOICE_DIR,
                                   clean_audio, wav_seconds, fix_ha, rms_windows, VAD_WINDOW)
from generate_padded import REF_BY_CHAR, estimate_seconds, find_prefix_noise, split_segments, group_segments

TIPS_JA = ASSETS_ROOT / "builtin_tips" / "tips_ja.json"
OUT_DIR = ASSETS_ROOT / "voices_builtin"
TMP = ROOT / "tools" / "gen_tmp"
MAX_ATTEMPTS = 8
SEED_BASE = 42


def trim(raw, out_mp3, exp):
    rms, rate = rms_windows(raw)
    segs = split_segments(rms)
    if not segs:
        return None
    groups = group_segments(segs)
    skip = find_prefix_noise(rms)
    start_idx = 0
    for gi, g in enumerate(groups):
        if g[1] * VAD_WINDOW >= skip:
            start_idx = gi
            break
    if start_idx >= len(groups):
        return None
    if skip > 0:
        start = max(0.0, skip * VAD_WINDOW - 0.05)
    else:
        start = max(0.0, groups[start_idx][0] * VAD_WINDOW - 0.05)
    end = groups[-1][1] * VAD_WINDOW + 0.1
    dur = end - start
    if dur <= 0.05:
        return None
    subprocess.run(["E:/ffmpeg/bin/ffmpeg", "-y", "-v", "error", "-i", str(raw),
                    "-af", "atrim=start=%f:end=%f,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.01" % (start, end),
                    "-b:a", "128k", str(out_mp3)], check=True)
    return dur


def mp3_dur(p):
    r = subprocess.run(["E:/ffmpeg/bin/ffprobe", "-v", "error", "-show_entries", "format=duration",
                        "-of", "default=noprint_wrappers=1:nokey=1", str(p)],
                       capture_output=True, text=True)
    try:
        return float(r.stdout.strip())
    except Exception:
        return None


def main():
    args = sys.argv[1:]
    chars = [a for a in args if a in ("tomori", "taki")]
    if not chars:
        chars = ["tomori", "taki"]
    skip_existing = "--skip-existing" in args

    tips = json.loads(TIPS_JA.read_text(encoding="utf-8"))
    os.chdir(PROJECT_DIR)
    sys.path.insert(0, str(PROJECT_DIR))
    os.environ["PATH"] = str(GGUF_BIN_DIR) + os.pathsep + os.environ.get("PATH", "")
    try:
        os.add_dll_directory(str(GGUF_BIN_DIR))
    except Exception:
        pass
    from qwen3_tts_gguf.inference import TTSEngine, TTSConfig

    engine = TTSEngine(str(MODEL_DIR), verbose=False)
    warns = []
    try:
        for cid in chars:
            role, ref_name = REF_BY_CHAR[cid]
            ref_wav = TMP / f"{cid}_batch_ref.wav"
            clean_audio(VOICE_DIR / role / ref_name, ref_wav)
            stream = engine.create_stream()
            stream.set_voice(str(ref_wav), text=ref_name.rsplit(".", 1)[0])
            stream.join()
            print(f"== voice {cid} ref={ref_name[:40]}", flush=True)
            out_dir = OUT_DIR / cid / "ja"
            out_dir.mkdir(parents=True, exist_ok=True)
            for idx, item in enumerate(tips[cid]):
                target = out_dir / f"{idx}.mp3"
                if skip_existing and target.exists() and target.stat().st_size > 10000:
                    print(f"  skip {cid}/{idx}", flush=True)
                    continue
                text = fix_ha(item.get("text", ""))
                if not text:
                    continue
                exp = estimate_seconds(text, pace=5.5 if cid == "tomori" else 6.5)
                best = None
                for shift in range(MAX_ATTEMPTS):
                    raw = TMP / f"{cid}_{idx}_batch.wav"
                    try:
                        config = TTSConfig(temperature=0.5, sub_temperature=0.5,
                                           seed=SEED_BASE + shift, sub_seed=45 + shift,
                                           streaming=False, min_p=0.05, sub_do_sample=False)
                        result = stream.clone(text, config=config)
                        stream.join()
                        result.save(str(raw))
                        dur = trim(raw, target, exp)
                        if dur is None:
                            continue
                        print(f"  {cid}/{idx} seed={SEED_BASE+shift} body={dur:.2f}s exp={exp:.2f}s", flush=True)
                        if 0.6 * exp <= dur <= 2.5 * exp:
                            best = ("accept", dur)
                            break
                        if best is None or abs(dur - exp) < abs(best[1] - exp):
                            best = ("warn", dur)
                    except Exception as e:
                        print(f"  {cid}/{idx} seed={SEED_BASE+shift} ERR {repr(e)[:120]}", flush=True)
                    finally:
                        if raw.exists():
                            raw.unlink()
                if best is None:
                    warns.append(f"{cid}/{idx} 全部失败")
                elif best[0] == "warn":
                    warns.append(f"{cid}/{idx} 时长未达标 best={best[1]:.2f}s exp={exp:.2f}s")
                print(f"DONE {cid}/{idx}", flush=True)
            stream = None
    finally:
        engine.shutdown()
    print("== WARN ==")
    for w in warns:
        print(w)
    print("BATCH_DONE")


if __name__ == "__main__":
    sys.exit(main())
