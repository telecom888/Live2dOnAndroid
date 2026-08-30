# -*- coding: utf-8 -*-
"""无填充重生成：plain 文本（不加PAD/不加……），柔和裁剪保留首词，尾部短填充后处理裁掉。
用法: python tools/regenerate_plain2.py [--dry-run]
目标列表从 tools/regen_targets.json 读取: [["soyo",3],["rana",19],...]
"""
import json, os, re, subprocess, sys
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["PYTHONIOENCODING"] = "utf-8"

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from generate_voices_local import (PROJECT_DIR, MODEL_DIR, GGUF_BIN_DIR, ASSETS_ROOT, VOICE_DIR,
                                   ROLE_MAP, pick_reference, clean_audio, wav_seconds, fix_ha,
                                   rms_windows, VAD_WINDOW)
from generate_padded import REF_BY_CHAR, estimate_seconds, find_prefix_noise, split_segments, group_segments

TIPS_JA = ASSETS_ROOT / "builtin_tips" / "tips_ja.json"
OUT_DIR = ASSETS_ROOT / "voices_builtin"
TMP = ROOT / "tools" / "gen_tmp"
TARGETS_JSON = ROOT / "tools" / "regen_targets.json"
MAX_ATTEMPTS = 12
SEED_BASE = 42

def trim_plain(raw, out_mp3, exp):
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
    start = max(0.0, groups[start_idx][0] * VAD_WINDOW - 0.08)
    end = groups[-1][1] * VAD_WINDOW + 0.1
    dur = end - start
    if dur <= 0.05:
        return None
    subprocess.run(["E:/ffmpeg/bin/ffmpeg", "-y", "-v", "error", "-i", str(raw),
                    "-af", "atrim=start=%f:end=%f,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.01" % (start, end),
                    "-b:a", "128k", str(out_mp3)], check=True)
    return dur

def cut_trailing_filler(mp3):
    """裁掉尾部短填充（最后语音组<=0.6s且与前面间隔>=0.28s）。返回是否裁了。"""
    import math, struct
    RATE = 24000; WIN = 0.02; VOICE_RMS = 800; GAP_SEC = 0.28; MAX_TAIL = 0.6
    r = subprocess.run([r"E:\ffmpeg\bin\ffmpeg", "-v", "error", "-i", str(mp3),
                        "-f", "s16le", "-ac", "1", "-ar", str(RATE), "pipe:1"], capture_output=True)
    if r.returncode or not r.stdout:
        return False
    n = len(r.stdout)//2
    samples = struct.unpack("<%dh" % n, r.stdout[:n*2])
    chunk = int(RATE*WIN)
    rms = []
    i = 0
    while i < len(samples):
        seg = samples[i:i+chunk]
        rms.append(math.sqrt(sum(x*x for x in seg)/len(seg)))
        i += chunk
    segs = []
    cur = None
    for j, rr in enumerate(rms):
        if rr >= VOICE_RMS:
            if cur is None: cur = [j, j]
            else: cur[1] = j
        else:
            if cur is not None:
                segs.append((cur[0], cur[1])); cur = None
    if cur is not None: segs.append((cur[0], cur[1]))
    if not segs:
        return False
    groups = []
    for s in segs:
        if not groups:
            groups.append(s)
        elif (s[0]-groups[-1][1]-1)*WIN > GAP_SEC:
            groups.append(s)
        else:
            groups[-1] = (groups[-1][0], s[1])
    last = groups[-1]
    last_dur = (last[1]-last[0]+1)*WIN
    if len(groups) >= 2:
        gap = (last[0]-groups[-2][1]-1)*WIN
    else:
        gap = 0
    if last_dur <= MAX_TAIL and gap >= GAP_SEC:
        cut_at = last[0]*WIN - GAP_SEC + 0.08
        end_s = len(rms)*WIN
        if 0.05 <= cut_at < end_s - 0.05:
            tmp = mp3.with_suffix(".mp3.cut")
            subprocess.run([r"E:\ffmpeg\bin\ffmpeg", "-y", "-v", "error", "-i", str(mp3),
                            "-af", "atrim=end=%f,asetpts=PTS-STARTPTS,afade=t=out:st=%f:d=0.01" % (cut_at, max(0.0, cut_at-0.01)),
                            "-b:a", "128k", str(tmp)], check=True)
            tmp.replace(mp3)
            return True
    return False

def main():
    targets = json.loads(TARGETS_JSON.read_text(encoding="utf-8"))
    if "--dry-run" in sys.argv:
        tips = json.loads(TIPS_JA.read_text(encoding="utf-8"))
        for cid, idx in targets:
            exp = estimate_seconds(tips[cid][idx]["text"])
            print(f"{cid}/{idx} exp={exp:.2f}s")
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
        for cid, idx in targets:
            if cid not in streams:
                if cid in REF_BY_CHAR:
                    role, ref_name = REF_BY_CHAR[cid]
                    ref_wav = TMP / f"{cid}_p2_ref.wav"
                    clean_audio(VOICE_DIR / role / ref_name, ref_wav)
                    ref_text = ref_name.rsplit(".", 1)[0]
                else:
                    name = pick_reference(cid)
                    if name is None:
                        print(f"SKIP {cid}: no reference", flush=True)
                        continue
                    ref_wav = TMP / f"{cid}_p2_ref.wav"
                    clean_audio(VOICE_DIR / ROLE_MAP[cid] / name, ref_wav)
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
                raw = TMP / f"{cid}_{idx}_p2.wav"
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
                    dur = trim_plain(raw, target_mp3, exp)
                    if dur is None:
                        continue
                    cut_trailing_filler(target_mp3)
                    dur2 = wav_seconds(target_mp3)
                    print(f"  {cid}/{idx} seed={SEED_BASE+shift} raw={dur_raw:.2f}s out={dur2:.2f}s exp={exp:.2f}s", flush=True)
                    if 0.6 * exp <= dur2 <= 2.3 * exp:
                        print("  -> ACCEPT", flush=True)
                        best = ("accept", dur2)
                        break
                    if best is None or abs(dur2 - exp) < abs(best[1] - exp):
                        best = ("warn", dur2)
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
    print(f"done targets={len(targets)} warn={len(warns)}")

if __name__ == "__main__":
    sys.exit(main())
