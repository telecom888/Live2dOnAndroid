# -*- coding: utf-8 -*-
"""乐奈剩余条目：多 seed 生成候选 -> mimo 转写择优 -> 写入最终 mp3。

用法: python tools/generate_rana_candidates.py
"""
import base64
import concurrent.futures as cf
import difflib
import json
import os
import pathlib
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
                                   clean_audio, wav_seconds, fix_ha, rms_windows, VAD_WINDOW)
from generate_padded import (PAD, REF_BY_CHAR, estimate_seconds, split_segments, group_segments,
                             find_prefix_noise, trim_body, mora_count)

TIPS_JA = ASSETS_ROOT / "builtin_tips" / "tips_ja.json"
OUT_DIR = ASSETS_ROOT / "voices_builtin"
TMP = ROOT / "tools" / "gen_tmp" / "cands"
CAND_IDS = [4, 10, 33, 35, 36, 37]
SEEDS = [42, 43, 44, 45, 46, 47, 48, 49]
API = "https://opencode.ai/zen/go/v1/chat/completions"
MODEL = "mimo-v2.5"
PUNCT = re.compile(r"[\s、。「」『』（）()？！!！？….,。・～~\-—\u3000]")


def load_key():
    v = os.environ.get("VISION_API_KEY") or os.environ.get("OPENCODE_API_KEY")
    if v:
        return v.strip()
    p = os.environ.get("OPENCODE_AUTH_PATH") or pathlib.Path.home() / ".local" / "share" / "opencode" / "auth.json"
    try:
        auth = json.loads(p.read_text(encoding="utf-8"))
        k = (auth.get("opencode-go") or auth.get("opencode_go") or {}).get("key")
        return k.strip() if k else None
    except Exception:
        return None


def normalize(s):
    t = PUNCT.sub("", s or "")
    return t.replace("ハ", "は").replace("ヵ", "か").replace("ヶ", "け").replace("ー", "")


def score(actual, expected):
    a, e = normalize(actual), normalize(expected)
    if not a:
        return 0.0
    return difflib.SequenceMatcher(None, a, e).ratio()


def transcribe(mp3, key):
    b64 = base64.b64encode(mp3.read_bytes()).decode("ascii")
    prompt = ("这是日语角色语音。请逐字转写，不要省略开头，不要改写为标准语，只输出转写文本。")
    body = {"model": MODEL, "messages": [{"role": "user", "content": [
        {"type": "text", "text": prompt},
        {"type": "input_audio", "input_audio": {"data": "data:audio/mpeg;base64," + b64}},
    ]}], "max_tokens": 1024}
    r = requests.post(API, headers={"Content-Type": "application/json", "Authorization": "Bearer " + key},
                      data=json.dumps(body, ensure_ascii=False).encode("utf-8"), timeout=90)
    if r.status_code != 200:
        return ""
    return ((r.json().get("choices") or [{}])[0].get("message", {}).get("content") or "").strip()


def main():
    key = load_key()
    if not key:
        print("no key")
        return 1
    import requests
    tips = json.loads(TIPS_JA.read_text(encoding="utf-8"))
    TMP.mkdir(parents=True, exist_ok=True)

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
        role, ref_name = REF_BY_CHAR["rana"]
        ref_wav = TMP.parent / "rana_cand_ref.wav"
        clean_audio(VOICE_DIR / role / ref_name, ref_wav)
        stream = engine.create_stream()
        stream.set_voice(str(ref_wav), text=ref_name.rsplit(".", 1)[0])
        stream.join()

        # 1) 生成候选
        cand_files = {}
        for idx in CAND_IDS:
            text = fix_ha(tips["rana"][idx]["text"])
            exp = estimate_seconds(text, pace=6.5)
            padded = text + PAD
            for seed in SEEDS:
                raw = TMP / f"r{idx}_{seed}.wav"
                out = TMP / f"r{idx}_{seed}.mp3"
                try:
                    config = TTSConfig(temperature=0.5, sub_temperature=0.5, seed=seed, sub_seed=45 + (seed - 42),
                                       streaming=False, min_p=0.05, sub_do_sample=False)
                    result = stream.clone(padded, config=config)
                    stream.join()
                    result.save(str(raw))
                    dur = trim_body(raw, out, exp)
                    if dur is not None:
                        cand_files.setdefault(idx, []).append((seed, out, dur))
                        print(f"  cand rana/{idx} seed={seed} dur={dur:.2f}s", flush=True)
                except Exception as e:
                    print(f"  cand rana/{idx} seed={seed} ERR {repr(e)[:100]}", flush=True)
                finally:
                    if raw.exists():
                        raw.unlink()
        stream = None
    finally:
        engine.shutdown()

    # 2) 转写择优
    print("== 转写择优 ==", flush=True)
    for idx in CAND_IDS:
        expected = tips["rana"][idx]["text"]
        items = cand_files.get(idx, [])
        if not items:
            print(f"rana/{idx} 无候选")
            continue
        results = []
        with cf.ThreadPoolExecutor(max_workers=6) as ex:
            futs = {ex.submit(transcribe, mp3, key): (seed, mp3, dur) for seed, mp3, dur in items}
            for fut in cf.as_completed(futs):
                seed, mp3, dur = futs[fut]
                actual = fut.result()
                results.append((score(actual, expected), seed, mp3, dur, actual))
        results.sort(key=lambda x: -x[0])
        best = results[0]
        final = OUT_DIR / "rana" / "ja" / f"{idx}.mp3"
        final.write_bytes(best[2].read_bytes())
        print(f"rana/{idx} 最优 seed={best[1]} score={best[0]:.2f} dur={best[3]:.2f}s")
        print(f"  期望: {expected}")
        print(f"  转写: {best[4]}")
        print(f"  次优: " + " | ".join(f"s{r[1]}:{r[0]:.2f}" for r in results[1:4]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
