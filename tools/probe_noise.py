# -*- coding: utf-8 -*-
"""分析 mp3 开头 0.6s：每 10ms RMS + 过零率（判断是否高频摩擦音毛刺）。"""
import math
import pathlib
import struct
import subprocess
import sys

FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"
ROOT = pathlib.Path(__file__).resolve().parent.parent
VOICES = ROOT / "app" / "src" / "main" / "assets" / "voices_builtin"


def load_mono(path):
    p = subprocess.run([FFMPEG, "-v", "error", "-i", str(path), "-f", "s16le", "-ac", "1", "-ar", "24000", "pipe:1"],
                       capture_output=True)
    data = p.stdout
    fmt = "<%dh" % (len(data) // 2)
    return struct.unpack(fmt, data)


def analyze(path):
    samples = load_mono(path)
    rate = 24000
    win = int(rate * 0.01)  # 10ms
    rows = []
    for i in range(0, min(len(samples), int(rate * 0.7)) - win + 1, win):
        seg = samples[i:i + win]
        rms = math.sqrt(sum(x * x for x in seg) / len(seg))
        zcr = sum(1 for a, b in zip(seg, seg[1:]) if (a >= 0) != (b >= 0)) / len(seg)
        rows.append((i / rate, rms, zcr))
    return rows


def main():
    for f in sys.argv[1:]:
        mp3 = VOICES / f if not pathlib.Path(f).exists() else pathlib.Path(f)
        print(f"== {f} ==")
        for t, rms, zcr in analyze(str(mp3)):
            bar = "#" * min(40, int(rms / 300))
            print(f"  {t:.2f}s rms={rms:6.0f} zcr={zcr:.3f} {bar}")


if __name__ == "__main__":
    main()
