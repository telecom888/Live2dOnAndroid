# -*- coding: utf-8 -*-
"""后处理：裁掉语音尾部明显的短填充（如えっと/あー）。
规则：最后一个语音组时长<=0.6s 且与前面间隔>=0.28s 时裁掉（从间隔处开始保留0.08s尾音）。
只处理 tomori/taki/rana/soyo/anon 全部内置语音。"""
import json, math, struct, subprocess, sys
from pathlib import Path

ROOT = Path(r"D:\opencode-proj\Live2dOnAndroid")
VOICES = ROOT / "app/src/main/assets/voices_builtin"
FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"
RATE = 24000
WIN = 0.02
VOICE_RMS = 800
GAP_SEC = 0.28
MAX_TAIL_SEC = 0.6
TAIL_KEEP = 0.08

def decode(mp3):
    r = subprocess.run([FFMPEG, "-v", "error", "-i", str(mp3), "-f", "s16le", "-ac", "1", "-ar", str(RATE), "pipe:1"], capture_output=True)
    if r.returncode or not r.stdout: return None
    n = len(r.stdout)//2
    return struct.unpack("<%dh" % n, r.stdout[:n*2])

def segments(samples):
    chunk = int(RATE*WIN)
    rms=[]
    i=0
    while i < len(samples):
        seg = samples[i:i+chunk]
        rms.append(math.sqrt(sum(x*x for x in seg)/len(seg)))
        i += chunk
    segs=[]
    cur=None
    for j, r in enumerate(rms):
        if r >= VOICE_RMS:
            if cur is None: cur=[j,j]
            else: cur[1]=j
        else:
            if cur is not None:
                segs.append((cur[0],cur[1])); cur=None
    if cur is not None: segs.append((cur[0],cur[1]))
    return rms, segs

def analyze_tail(samples):
    rms, segs = segments(samples)
    if not segs: return None
    # 组：间隔 > GAP 切组
    groups=[]
    for s in segs:
        if not groups:
            groups.append(s)
        elif (s[0]-groups[-1][1]-1)*WIN > GAP_SEC:
            groups.append(s)
        else:
            groups[-1]=(groups[-1][0], s[1])
    last = groups[-1]
    last_dur = (last[1]-last[0]+1)*WIN
    if len(groups) >= 2:
        gap_before = (last[0]-groups[-2][1]-1)*WIN
    else:
        gap_before = 0
    return {"last_dur": last_dur, "gap_before": gap_before, "groups": len(groups),
            "last_start_s": last[0]*WIN, "end_s": len(rms)*WIN}

def cut(mp3, cut_at_s):
    tmp = mp3.with_suffix(".mp3.cuttmp")
    subprocess.run([FFMPEG,"-y","-v","error","-i",str(mp3),
                    "-af","atrim=end=%f,asetpts=PTS-STARTPTS,afade=t=out:st=%f:d=0.01"%(cut_at_s, max(0.0, cut_at_s-0.01)),
                    "-b:a","128k",str(tmp)], check=True)
    tmp.replace(mp3)

def main():
    dry = "--dry-run" in sys.argv
    changed=[]
    for cid in ["anon","rana","soyo","taki","tomori"]:
        d = VOICES/cid/"ja"
        if not d.exists(): continue
        for mp3 in sorted(d.glob("*.mp3"), key=lambda p:int(p.stem)):
            s = decode(mp3)
            if s is None: continue
            info = analyze_tail(s)
            if info is None: continue
            if info["last_dur"] <= MAX_TAIL_SEC and info["gap_before"] >= GAP_SEC:
                cut_at = (info["last_start_s"] - GAP_SEC + TAIL_KEEP)  # 从间隔中间偏后切
                cut_at = max(0.05, min(cut_at, info["end_s"]))
                if cut_at < info["end_s"] - 0.05:
                    print(f"{cid}/{mp3.stem}: last={info['last_dur']:.2f}s gap={info['gap_before']:.2f}s -> cut@ {cut_at:.2f}s (end {info['end_s']:.2f}s)")
                    if not dry:
                        cut(mp3, cut_at)
                        changed.append(f"{cid}/{mp3.stem}")
    print(f"changed={len(changed)}")

if __name__ == "__main__":
    main()
