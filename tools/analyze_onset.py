# -*- coding: utf-8 -*-
"""用 ffmpeg 分析全部内置语音的开头特征：毛刺(glitch)/截断(truncated)/首词缺失风险。"""
import json, math, os, struct, subprocess, sys, io
from pathlib import Path

ROOT = Path(r"D:\opencode-proj\Live2dOnAndroid")
VOICES = ROOT / "app/src/main/assets/voices_builtin"
FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"
RATE = 24000
WIN = 0.005  # 5ms windows

def decode_pcm(mp3):
    r = subprocess.run([FFMPEG, "-v", "error", "-i", str(mp3), "-f", "s16le", "-ac", "1", "-ar", str(RATE), "pipe:1"],
                       capture_output=True)
    if r.returncode != 0 or not r.stdout:
        return None
    n = len(r.stdout) // 2
    samples = struct.unpack("<%dh" % n, r.stdout[:n * 2])
    return samples

def analyze(samples):
    n = len(samples)
    if n == 0:
        return None
    chunk = int(RATE * WIN)
    rms = []
    i = 0
    while i < n:
        seg = samples[i:i + chunk]
        if not seg:
            break
        rms.append(math.sqrt(sum(x * x for x in seg) / len(seg)))
        i += chunk
    peak = max(abs(x) for x in samples) / 32768.0
    total_dur = n / RATE
    # first 100ms stats
    first_n = int(0.100 * RATE)
    first_peak = max(abs(x) for x in samples[:first_n]) / 32768.0 if first_n else 0.0
    first_rms = math.sqrt(sum(x * x for x in samples[:first_n]) / first_n) / 32768.0 if first_n else 0.0
    # body rms 0.15s..0.6s
    b0 = int(0.15 * RATE); b1 = int(0.6 * RATE)
    body = samples[b0:b1]
    body_rms = math.sqrt(sum(x * x for x in body) / len(body)) / 32768.0 if body else 0.0
    # onset: first window rms>threshold sustained 3 windows (15ms)
    thr = max(0.008, body_rms * 0.25)
    onset_idx = None
    run = 0
    for j, r in enumerate(rms):
        if r / 32768.0 >= thr:
            run += 1
            if run >= 3:
                onset_idx = j - run + 1
                break
        else:
            run = 0
    onset_ms = onset_idx * WIN * 1000 if onset_idx is not None else None
    # glitch: early spike (first 60ms) much higher than body, followed by silence dip before onset
    early_n = int(0.06 * RATE)
    early_peak = max(abs(x) for x in samples[:early_n]) / 32768.0 if early_n else 0.0
    glitch = False
    if early_peak > 0.25 and body_rms > 0 and early_peak / max(body_rms, 1e-6) > 4.0:
        # check dip after spike: any window in [60ms, onset] with rms < thr
        if onset_ms is not None and onset_ms > 80:
            dip = any(r / 32768.0 < thr for r in rms[12:int(onset_ms / (WIN * 1000))])
            if dip:
                glitch = True
    # truncated: onset < 30ms or first window already loud
    truncated = onset_ms is not None and onset_ms < 30
    return {
        "dur": round(total_dur, 3), "peak": round(peak, 3),
        "first_peak": round(first_peak, 3), "first_rms": round(first_rms, 4),
        "body_rms": round(body_rms, 4), "onset_ms": onset_ms,
        "glitch": glitch, "truncated": truncated,
    }

def main():
    chars = ["anon", "rana", "soyo", "taki", "tomori"]
    out = {}
    rows = []
    for cid in chars:
        d = VOICES / cid / "ja"
        if not d.exists():
            continue
        tips = json.loads((ROOT / "app/src/main/assets/builtin_tips/tips_ja.json").read_text(encoding="utf-8"))
        for idx, mp3 in enumerate(sorted(d.glob("*.mp3"), key=lambda p: int(p.stem))):
            i = int(mp3.stem)
            exp = tips.get(cid, [{}])[i].get("text", "") if i < len(tips.get(cid, [])) else ""
            samples = decode_pcm(mp3)
            if samples is None:
                rows.append({"char": cid, "index": i, "err": "decode_fail"})
                continue
            a = analyze(samples)
            a["char"] = cid; a["index"] = i; a["text"] = exp
            rows.append(a)
    # print summary
    gl = [r for r in rows if r.get("glitch")]
    tr = [r for r in rows if r.get("truncated")]
    print("== GLITCH ==")
    for r in gl:
        print(f"{r['char']}/{r['index']} dur={r['dur']} first_peak={r['first_peak']} body_rms={r['body_rms']} onset={r['onset_ms']}ms")
    print("== TRUNCATED (onset<30ms) ==")
    for r in tr:
        print(f"{r['char']}/{r['index']} dur={r['dur']} first_peak={r['first_peak']} first_rms={r['first_rms']} body_rms={r['body_rms']} onset={r['onset_ms']}ms")
    print(f"total={len(rows)} glitch={len(gl)} truncated={len(tr)}")
    (ROOT / "tools/onset_analysis.json").write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")

if __name__ == "__main__":
    main()
