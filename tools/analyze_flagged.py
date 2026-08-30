# -*- coding: utf-8 -*-
"""分析 QA 标记不一致文件的 VAD 裁点：raw wav 语音起点 vs 最终 mp3 时长。"""
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from generate_voices_local import rms_windows, find_voice_start, VAD_WINDOW, VOICE_DIR, ASSETS_ROOT

TMP = ROOT / "tools" / "gen_tmp"
VOICES = ASSETS_ROOT / "voices_builtin"
FFPROBE = r"E:\ffmpeg\bin\ffprobe.exe"


def mp3_dur(p):
    r = subprocess.run([FFPROBE, "-v", "error", "-show_entries", "format=duration",
                        "-of", "default=noprint_wrappers=1:nokey=1", str(p)],
                       capture_output=True, text=True)
    try:
        return float(r.stdout.strip())
    except Exception:
        return None


def analyze(char_id, idx):
    raw = TMP / f"{char_id}_{idx}.wav"
    mp3 = VOICES / char_id / "ja" / f"{idx}.mp3"
    line = []
    if raw.exists():
        rms, rate = rms_windows(raw)
        start = find_voice_start(rms)
        # 总时长
        dur = len(rms) * VAD_WINDOW
        line.append(f"raw_dur={dur:.2f}s voice_start(raw)={start if start is None else round(start,3)}s")
    else:
        line.append("raw MISSING")
    if mp3.exists():
        d = mp3_dur(mp3)
        line.append(f"mp3_dur={d}s")
    return "  ".join(line)


def main():
    report = json.loads((ROOT / "tools" / "verify_qa_report.json").read_text(encoding="utf-8"))
    flagged = [r for r in report if r["verdict"] == "不一致"]
    for r in flagged:
        print(f"{r['char']}/{r['index']}  {analyze(r['char'], r['index'])}")
        print(f"    期望: {r['expected'][:60]}")
        print(f"    听到: {r['actual'][:80]}")


if __name__ == "__main__":
    main()
