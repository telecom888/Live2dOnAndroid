# -*- coding: utf-8 -*-
"""最终重生成 v2：
- 全部采用 正文(+……前缀) + PAD 填充生成（保证短句完整响应）
- 用 trim_body 裁掉尾部填充、跳过前缀噪声（首词保留）
"""
import json, os, re, subprocess, sys
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["PYTHONIOENCODING"] = "utf-8"

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from generate_voices_local import (PROJECT_DIR, MODEL_DIR, GGUF_BIN_DIR, ASSETS_ROOT, VOICE_DIR,
                                   ROLE_MAP, pick_reference, clean_audio, wav_seconds, fix_ha)
from generate_padded import (REF_BY_CHAR, estimate_seconds, trim_body, PAD, TMP)

TIPS_JA = ASSETS_ROOT / "builtin_tips" / "tips_ja.json"
OUT_DIR = ASSETS_ROOT / "voices_builtin"
MAX_ATTEMPTS = 12
SEED_BASE = 42

TARGETS = [
    # tomori/taki 带填充结尾 -> 重生成并裁掉尾部填充
    ("tomori", 0, False), ("tomori", 1, False), ("tomori", 2, False),
    ("tomori", 32, False), ("tomori", 37, False), ("tomori", 38, False),
    ("taki", 1, False), ("taki", 5, False), ("taki", 13, False),
    ("taki", 21, False), ("taki", 30, False), ("taki", 37, False),
    ("taki", 41, False), ("taki", 43, False), ("taki", 50, False), ("taki", 51, False),
    # 首词缺失 -> ……前缀
    ("rana", 18, True), ("rana", 19, True), ("rana", 23, True),
    ("rana", 26, True), ("rana", 27, True), ("rana", 29, True),
    ("rana", 32, True), ("rana", 34, True), ("rana", 38, True),
    ("rana", 41, True), ("rana", 42, True), ("rana", 46, True),
    ("rana", 47, True),
    ("soyo", 3, True), ("soyo", 13, True),
]

def main():
    if "--dry-run" in sys.argv:
        tips = json.loads(TIPS_JA.read_text(encoding="utf-8"))
        for cid, idx, ell in TARGETS:
            exp = estimate_seconds(tips[cid][idx]["text"])
            print(f"{cid}/{idx} ellipsis={ell} exp={exp:.2f}s")
        return 0
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
    from qwen3_tts_gguf.inference.proxy import DecoderProxy
    _orig_wait = DecoderProxy.wait_until_ready
    DecoderProxy.wait_until_ready = lambda self, timeout=10: _orig_wait(self, timeout=max(int(timeout), 180))
    engine = TTSEngine(str(MODEL_DIR), verbose=False, enable_speaker=False)

    streams = {}
    warns = []
    try:
        for cid, idx, ell in TARGETS:
            if cid not in streams:
                if cid in REF_BY_CHAR:
                    role, ref_name = REF_BY_CHAR[cid]
                    ref_wav = TMP / f"{cid}_final2_ref.wav"
                    clean_audio(VOICE_DIR / role / ref_name, ref_wav)
                    ref_text = ref_name.rsplit(".", 1)[0]
                else:
                    name = pick_reference(cid)
                    if name is None:
                        print(f"SKIP {cid}: no reference", flush=True)
                        continue
                    ref_wav = TMP / f"{cid}_final2_ref.wav"
                    clean_audio(VOICE_DIR / ROLE_MAP[cid] / name, ref_wav)
                    ref_text = name.rsplit(".", 1)[0]
                s = engine.create_stream()
                s.set_voice(str(ref_wav), text=ref_text)
                s.join()
                streams[cid] = s
                print(f"== voice {cid} ref={ref_text[:40]}", flush=True)
            stream = streams[cid]

            raw_text = tips[cid][idx]["text"]
            text = fix_ha(raw_text)
            if ell:
                text = "……" + text
            padded = text + PAD
            exp = estimate_seconds(raw_text)
            target_mp3 = OUT_DIR / cid / "ja" / f"{idx}.mp3"
            target_mp3.parent.mkdir(parents=True, exist_ok=True)

            best = None
            for shift in range(MAX_ATTEMPTS):
                raw = TMP / f"{cid}_{idx}_final2.wav"
                try:
                    config = TTSConfig(temperature=0.5, sub_temperature=0.5,
                                       seed=SEED_BASE + shift, sub_seed=45 + shift,
                                       streaming=False, min_p=0.05, sub_do_sample=False)
                    result = stream.clone(padded, config=config)
                    stream.join()
                    result.save(str(raw))
                    dur_raw = wav_seconds(raw)
                    if dur_raw < 0.3:
                        continue
                    dur = trim_body(raw, target_mp3, exp)
                    if dur is None:
                        continue
                    print(f"  {cid}/{idx} seed={SEED_BASE+shift} raw={dur_raw:.2f}s out={dur:.2f}s exp={exp:.2f}s", flush=True)
                    if 0.6 * exp <= dur <= 2.3 * exp:
                        print("  -> ACCEPT", flush=True)
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
    finally:
        for s in streams.values():
            s = None
        engine.shutdown()
    print("== WARN ==")
    for w in warns:
        print(w)
    print(f"done targets={len(TARGETS)} warn={len(warns)}")

if __name__ == "__main__":
    sys.exit(main())
