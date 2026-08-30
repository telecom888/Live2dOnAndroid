# -*- coding: utf-8 -*-
"""打印 mp3 开头每 100ms 窗口 RMS，判断开头是否有语音。"""
import math
import pathlib
import struct
import subprocess
import sys

FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"
ROOT = pathlib.Path(__file__).resolve().parent.parent
VOICES = ROOT / "app" / "src" / "main" / "assets" / "voices_builtin"


def rms_windows_mp3(path, win=0.1):
    p = subprocess.run([FFMPEG, "-v", "error", "-i", str(path), "-f", "s16le", "-ac", "1", "-ar", "24000", "pipe:1"],
                       capture_output=True)
    data = p.stdout
    fmt = "<%dh" % (len(data) // 2)
    samples = struct.unpack(fmt, data)
    rate = 24000
    chunk = int(rate * win)
    out = []
    for s in range(0, max(1, len(samples) - chunk + 1), chunk):
        seg = samples[s:s + chunk]
        out.append(math.sqrt(sum(x * x for x in seg) / len(seg)))
    return out


def main():
    files = sys.argv[1:] or ["rana/ja/4.mp3", "rana/ja/19.mp3", "soyo/ja/0.mp3", "soyo/ja/3.mp3",
                             "soyo/ja/35.mp3", "rana/ja/35.mp3", "soyo/ja/38.mp3", "anon/ja/8.mp3",
                             "rana/ja/23.mp3", "soyo/ja/51.mp3"]
    for f in files:
        mp3 = VOICES / f
        if not mp3.exists():
            print("MISS", f)
            continue
        rms = rms_windows_mp3(mp3)
        head = " ".join(f"{i*0.1:.1f}:{r:.0f}" for i, r in enumerate(rms[:15]))
        active = sum(1 for r in rms if r >= 800)
        print(f"== {f}  windows={len(rms)}  active={active}")
        print(f"   head: {head}")


if __name__ == "__main__":
    main()
