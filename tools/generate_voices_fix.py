# -*- coding: utf-8 -*-
"""修复版生成：针对指定角色/序号重新生成，解决 Qwen 短句第一句被截断问题。

策略：
- 按文本干净字符数估算期望时长（约 7 字符/秒）
- 生成后 VAD 裁剪，时长在 [0.65*exp, 2.5*exp] 内才接受；否则换 seed 重试（最多 8 次）
- 全部不达标则保留最接近期望的候选并打 WARN
- 强制覆盖已存在 mp3

用法: python tools/generate_voices_fix.py
"""
import io
import json
import os
import re
import subprocess
import sys
import time
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

# 需要重新生成的条目：(角色, index)
TARGETS = [
    ("rana", 4), ("rana", 5), ("rana", 10), ("rana", 11), ("rana", 13), ("rana", 15),
    ("rana", 17), ("rana", 33), ("rana", 35), ("rana", 36), ("rana", 37),
    ("soyo", 35),
    ("anon", 54), ("anon", 60), ("anon", 61), ("anon", 69),
    ("soyo", 9), ("soyo", 29), ("soyo", 48),
]

MAX_ATTEMPTS = 8
SEED_BASE = 42

PUNCT = re.compile(r"[\s、。「」『』（）()？！!！？….,。・～~\-—\u3000]")


def estimate_seconds(text: str) -> float:
    clean = PUNCT.sub("", text)
    return max(0.6, len(clean) / 7.0)


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--dry-run":
        tips = json.loads(TIPS_JA.read_text(encoding="utf-8"))
        for cid, idx in TARGETS:
            t = tips[cid][idx]["text"]
            exp = estimate_seconds(t)
            print(f"{cid}/{idx} exp={exp:.2f}s  {t[:50]}")
        return 0

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
    streams = {}
    warns = []
    try:
        for cid, idx in TARGETS:
            role = {v: k for k, v in ROLE_MAP.items()}[cid]
            name = pick_reference(cid)
            if name is None:
                print(f"SKIP {cid}: no reference")
                continue
            if cid not in streams:
                ref_wav = tmp_dir / (f"{cid}_fix_ref.wav")
                clean_audio(VOICE_DIR / role / name, ref_wav)
                ref_text = name.rsplit(".", 1)[0]
                s = engine.create_stream()
                s.set_voice(str(ref_wav), text=ref_text)
                s.join()
                streams[cid] = s
                print(f"== voice {cid} ref={ref_text[:40]}", flush=True)
            stream = streams[cid]

            text = fix_ha(tips[cid][idx]["text"])
            exp = estimate_seconds(text)
            target_mp3 = OUT_DIR / cid / "ja" / f"{idx}.mp3"
            target_mp3.parent.mkdir(parents=True, exist_ok=True)

            best = None
            for shift in range(MAX_ATTEMPTS):
                raw = tmp_dir / (f"{cid}_{idx}_fix.wav")
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
                    print(f"  {cid}/{idx} seed={SEED_BASE+shift} raw={dur_raw:.2f}s mp3={dur:.2f}s exp={exp:.2f}s", flush=True)
                    if 0.65 * exp <= dur <= 2.5 * exp:
                        print(f"  -> ACCEPT", flush=True)
                        best = ("accept", dur)
                        break
                    if best is None or abs(dur - exp) < abs(best[1] - exp):
                        best = ("warn", dur)
                except Exception as e:
                    print(f"  {cid}/{idx} seed={SEED_BASE+shift} ERR {repr(e)[:150]}", flush=True)
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
    return 0


if __name__ == "__main__":
    sys.exit(main())
