# -*- coding: utf-8 -*-
"""测试 tomori/taki 不填充直接生成是否完整。"""
import os
import subprocess
import sys
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["PYTHONIOENCODING"] = "utf-8"

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from generate_voices_local import (PROJECT_DIR, MODEL_DIR, GGUF_BIN_DIR, VOICE_DIR, ASSETS_ROOT,
                                   clean_audio, wav_seconds, fix_ha, rms_windows, VAD_WINDOW)
from generate_padded import REF_BY_CHAR, estimate_seconds, find_prefix_noise, split_segments, group_segments

TMP = ROOT / "tools" / "gen_tmp"
OUT = ROOT / "tools" / "gen_tmp" / "plain"


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
    start = max(0.0, skip * VAD_WINDOW - 0.05) if skip > 0 else max(0.0, groups[start_idx][0] * VAD_WINDOW - 0.05)
    end = groups[-1][1] * VAD_WINDOW + 0.1
    dur = end - start
    subprocess.run(["E:/ffmpeg/bin/ffmpeg", "-y", "-v", "error", "-i", str(raw),
                    "-af", "atrim=start=%f:end=%f,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.01" % (start, end),
                    "-b:a", "128k", str(out_mp3)], check=True)
    return dur


def main():
    os.chdir(PROJECT_DIR)
    sys.path.insert(0, str(PROJECT_DIR))
    os.environ["PATH"] = str(GGUF_BIN_DIR) + os.pathsep + os.environ.get("PATH", "")
    try:
        os.add_dll_directory(str(GGUF_BIN_DIR))
    except Exception:
        pass
    from qwen3_tts_gguf.inference import TTSEngine, TTSConfig
    tips = json.loads((ASSETS_ROOT / "builtin_tips" / "tips_ja.json").read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    import re as _re
    raw = sys.argv[1:]
    if raw:
        targets = []
        for item in raw:
            cid, _, idxs = item.partition("/")
            for i in idxs.split(","):
                targets.append((cid, int(i)))
    else:
        targets = [("tomori", 0), ("tomori", 1), ("tomori", 2), ("taki", 0), ("taki", 1), ("taki", 2)]
    engine = TTSEngine(str(MODEL_DIR), verbose=False)
    try:
        for cid, idx in targets:
            role, ref_name = REF_BY_CHAR[cid]
            ref_wav = TMP / f"{cid}_plain_ref.wav"
            clean_audio(VOICE_DIR / role / ref_name, ref_wav)
            stream = engine.create_stream()
            stream.set_voice(str(ref_wav), text=ref_name.rsplit(".", 1)[0])
            stream.join()
            text = fix_ha(tips[cid][idx]["text"])
            exp = estimate_seconds(text, pace={"tomori": 5.5, "taki": 6.5}[cid])
            raw = TMP / f"{cid}_{idx}_plain.wav"
            out = OUT / f"{cid}_{idx}.mp3"
            config = TTSConfig(temperature=0.5, sub_temperature=0.5, seed=42, sub_seed=45,
                               streaming=False, min_p=0.05, sub_do_sample=False)
            result = stream.clone(text, config=config)
            stream.join()
            result.save(str(raw))
            dur = trim(raw, out, exp)
            print(f"{cid}/{idx} exp={exp:.2f} body={dur:.2f} mp3={out}", flush=True)
            stream = None
    finally:
        engine.shutdown()


if __name__ == "__main__":
    import json
    main()
