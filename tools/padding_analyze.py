# -*- coding: utf-8 -*-
"""分析指定条目 padded 生成的语音段结构。"""
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
from generate_padded import PAD, REF_BY_CHAR, split_segments, group_segments, mora_count

TMP = ROOT / "tools" / "gen_tmp"


def main():
    targets = sys.argv[1:] or ["rana/11", "rana/33"]
    os.chdir(PROJECT_DIR)
    sys.path.insert(0, str(PROJECT_DIR))
    os.environ["PATH"] = str(GGUF_BIN_DIR) + os.pathsep + os.environ.get("PATH", "")
    try:
        os.add_dll_directory(str(GGUF_BIN_DIR))
    except Exception:
        pass
    from qwen3_tts_gguf.inference import TTSEngine, TTSConfig

    tips = json.loads((ROOT / "app" / "src" / "main" / "assets" / "builtin_tips" / "tips_ja.json").read_text(encoding="utf-8"))
    for t in targets:
        cid, _, idx = t.partition("/")
        idx = int(idx)
        role, ref_name = REF_BY_CHAR[cid]
        ref_wav = TMP / f"{cid}_an_ref.wav"
        clean_audio(VOICE_DIR / role / ref_name, ref_wav)
        engine = TTSEngine(str(MODEL_DIR), verbose=False)
        stream = engine.create_stream()
        stream.set_voice(str(ref_wav), text=ref_name.rsplit(".", 1)[0])
        stream.join()
        text = tips[cid][idx]["text"]
        print(f"== {cid}/{idx} 期望: {text}  mora={mora_count(text)} exp={mora_count(text)/5.5:.2f}s", flush=True)
        for seed in (42, 43):
            raw = TMP / f"{cid}_{idx}_an_{seed}.wav"
            config = TTSConfig(temperature=0.5, sub_temperature=0.5, seed=seed, sub_seed=45 + (seed - 42),
                               streaming=False, min_p=0.05, sub_do_sample=False)
            result = stream.clone(text + PAD, config=config)
            stream.join()
            result.save(str(raw))
            rms, rate = rms_windows(raw)
            segs = split_segments(rms)
            groups = group_segments(segs)
            print(f"  seed={seed} total={len(rms)*VAD_WINDOW:.2f}s segs={len(segs)} groups={len(groups)}")
            for g in groups:
                d = (g[1] - g[0] + 1) * VAD_WINDOW
                print(f"    group {g[0]*VAD_WINDOW:.2f}-{g[1]*VAD_WINDOW:.2f}s dur={d:.2f}s")
        stream = None
        engine.shutdown()


if __name__ == "__main__":
    import json
    main()
