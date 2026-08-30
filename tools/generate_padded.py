# -*- coding: utf-8 -*-
"""填充法生成：正文+填充文字 -> TTS -> VAD 分组 -> 保留第一组（正文），裁掉填充。

用法:
  python tools/generate_padded.py rana            # 重新生成 rana 指定截断条
  python tools/generate_padded.py tomori all      # 全量生成 tomori
  python tools/generate_padded.py taki all
"""
import json
import math
import os
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

TIPS_JA = ASSETS_ROOT / "builtin_tips" / "tips_ja.json"
OUT_DIR = ASSETS_ROOT / "voices_builtin"
TMP = ROOT / "tools" / "gen_tmp"
PAD = "。えーっと、あー、えーっと、あー"
MAX_ATTEMPTS = 14
SEED_BASE = 42
VOICE_RMS = 800
SILENCE_RMS = 400
MIN_ACTIVE_WINDOWS = 6          # 0.12s
SPLIT_GAP_WINDOWS = 8           # 段间静音 >0.16s 切段
GROUP_GAP_WINDOWS = 20          # 组间静音 >0.4s 才算新组（正文内部停顿容差）

# 参考音频（用户指定 / 锁定）
REF_BY_CHAR = {
    "tomori": ("高松燈", "……傷つかずに一生は、無理。傷だらけ……泥だらけになって、いっぱい、もがかなきゃ…….mp3"),
    "taki": ("椎名立希", "……そういうわけなんで、もし今日スタジオに空き出たら真っ先に教えてください.mp3"),
    "rana": ("要樂奈", "抹茶生地に抹茶クリームと抹茶アイスと抹茶チョコ。仕上げにたっぷり抹茶ソース.mp3"),
}
RANA_FIX = {4, 5, 10, 11, 13, 15, 17, 33, 35, 36, 37}

KANA = re.compile(r"[\u3041-\u3096\u30a1-\u30f6]")
KANJI = re.compile(r"[\u4e00-\u9fff]")
DIGIT = re.compile(r"[0-9０-９]")


def mora_count(text):
    n = 0
    for ch in text:
        if KANA.match(ch):
            n += 1
        elif KANJI.match(ch):
            n += 2
        elif DIGIT.match(ch):
            n += 2
    return max(1, n)


PACE_BY_CHAR = {"rana": 6.5, "soyo": 6.0, "anon": 6.0, "tomori": 5.5, "taki": 6.5}
_CUR_PACE = ["rana"]

def estimate_seconds(text, pace=None):
    if pace is None:
        pace = PACE_BY_CHAR.get(_CUR_PACE[0], 6.0)
    return max(0.6, mora_count(text) / pace)


def split_segments(rms):
    segs = []
    cur = None
    for i, r in enumerate(rms):
        if r >= VOICE_RMS:
            if cur is None:
                cur = [i, i]
            else:
                cur[1] = i
        else:
            if cur is not None and i - cur[1] > SPLIT_GAP_WINDOWS:
                if cur[1] - cur[0] + 1 >= MIN_ACTIVE_WINDOWS:
                    segs.append((cur[0], cur[1]))
                cur = None
    if cur is not None and cur[1] - cur[0] + 1 >= MIN_ACTIVE_WINDOWS:
        segs.append((cur[0], cur[1]))
    return segs


def group_segments(segs):
    groups = []
    for s in segs:
        if groups and s[0] - groups[-1][1] <= GROUP_GAP_WINDOWS:
            groups[-1] = (groups[-1][0], s[1])
        else:
            groups.append(s)
    return groups


def find_prefix_noise(rms):
    """检测 Qwen 固定前缀噪声：开头活跃段 <=0.35s，其后出现持续 >=0.05s 的活跃段。
    返回真实语音起始窗口下标；无前缀返回 0。"""
    n = len(rms)
    if n == 0:
        return 0
    i = 0
    while i < n and rms[i] < VOICE_RMS:
        i += 1
    if i >= n:
        return 0
    j = i
    while j < n and rms[j] >= VOICE_RMS:
        j += 1
    act_len = (j - i) * VAD_WINDOW
    if act_len > 0.35:
        return 0
    k = j
    while k < n:
        if rms[k] >= VOICE_RMS:
            kk = k
            while kk < n and rms[kk] >= VOICE_RMS:
                kk += 1
            if (kk - k) * VAD_WINDOW >= 0.05:
                return k
            k = kk
        else:
            k += 1
    return 0


def trim_body(raw_wav, target_mp3, exp):
    """去前缀噪声 + 累积组时长达到 exp*0.8 视为正文（剩余 <1s 并入），裁掉填充。"""
    rms, rate = rms_windows(raw_wav)
    segs = split_segments(rms)
    if not segs:
        return None
    groups = group_segments(segs)
    # 跳过前缀噪声：直接用检测到的前缀终点作为起点
    skip = find_prefix_noise(rms)
    start_idx = 0
    for gi, g in enumerate(groups):
        if g[1] * VAD_WINDOW >= skip:
            start_idx = gi
            break
    body_groups = []
    acc = 0.0
    threshold = exp * 0.8
    for gi in range(start_idx, len(groups)):
        g = groups[gi]
        body_groups.append(g)
        acc += (g[1] - g[0] + 1) * VAD_WINDOW
        if acc >= threshold:
            break
    # 剩余组总时长 <1s 时并入正文（避免把正文尾部短段当填充裁掉）
    remaining = 0.0
    for g in groups[len(body_groups):]:
        remaining += (g[1] - g[0] + 1) * VAD_WINDOW
    if remaining < 1.0:
        body_groups = groups[start_idx:]
    if not body_groups:
        return None
    if skip > 0:
        start = max(0.0, skip * VAD_WINDOW - 0.05)
    else:
        start = max(0.0, body_groups[0][0] * VAD_WINDOW - 0.05)
    end = min(len(rms) * VAD_WINDOW, body_groups[-1][1] * VAD_WINDOW + 0.1)
    dur = end - start
    if dur <= 0.05:
        return None
    subprocess.run(
        ["E:/ffmpeg/bin/ffmpeg", "-y", "-v", "error", "-i", str(raw_wav),
         "-af", "atrim=start=%f:end=%f,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.01" % (start, end),
         "-b:a", "128k", str(target_mp3)],
        check=True)
    return dur


def mp3_dur(p):
    r = subprocess.run(["E:/ffmpeg/bin/ffprobe", "-v", "error", "-show_entries", "format=duration",
                        "-of", "default=noprint_wrappers=1:nokey=1", str(p)],
                       capture_output=True, text=True)
    try:
        return float(r.stdout.strip())
    except Exception:
        return None


def generate(char_id, indexes):
    role, ref_name = REF_BY_CHAR[char_id]
    _CUR_PACE[0] = char_id
    tips = json.loads(TIPS_JA.read_text(encoding="utf-8"))
    ref_wav = TMP / f"{char_id}_pad_ref.wav"
    clean_audio(VOICE_DIR / role / ref_name, ref_wav)
    ref_text = ref_name.rsplit(".", 1)[0]

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
        stream = engine.create_stream()
        stream.set_voice(str(ref_wav), text=ref_text)
        stream.join()
        print(f"== voice {char_id} ref={ref_text[:40]}", flush=True)
        warns = []
        for idx in indexes:
            text = fix_ha(tips[char_id][idx]["text"])
            exp = estimate_seconds(text)
            padded = text + PAD
            target_mp3 = OUT_DIR / char_id / "ja" / f"{idx}.mp3"
            target_mp3.parent.mkdir(parents=True, exist_ok=True)
            best = None
            for shift in range(MAX_ATTEMPTS):
                raw = TMP / f"{char_id}_{idx}_pad.wav"
                try:
                    config = TTSConfig(temperature=0.5, sub_temperature=0.5,
                                       seed=SEED_BASE + shift, sub_seed=45 + shift,
                                       streaming=False, min_p=0.05, sub_do_sample=False)
                    result = stream.clone(padded, config=config)
                    stream.join()
                    result.save(str(raw))
                    dur = trim_body(raw, target_mp3, exp)
                    if dur is None:
                        continue
                    print(f"  {char_id}/{idx} seed={SEED_BASE+shift} body={dur:.2f}s exp={exp:.2f}s", flush=True)
                    if 0.6 * exp <= dur <= 2.5 * exp:
                        best = ("accept", dur)
                        break
                    if best is None or abs(dur - exp) < abs(best[1] - exp):
                        best = ("warn", dur)
                except Exception as e:
                    print(f"  {char_id}/{idx} seed={SEED_BASE+shift} ERR {repr(e)[:120]}", flush=True)
                finally:
                    if raw.exists():
                        raw.unlink()
            if best is None:
                warns.append(f"{char_id}/{idx} 全部失败")
            elif best[0] == "warn":
                warns.append(f"{char_id}/{idx} 时长未达标 best={best[1]:.2f}s exp={exp:.2f}s")
            print(f"DONE {char_id}/{idx}", flush=True)
        stream = None
        print("== WARN ==")
        for w in warns:
            print(w)
    finally:
        engine.shutdown()


def main():
    args = sys.argv[1:]
    if not args:
        print("用法: python tools/generate_padded.py rana|tomori|taki [all|idx,idx...]")
        return 1
    char_id = args[0]
    if len(args) > 1 and args[1] == "all":
        tips = json.loads(TIPS_JA.read_text(encoding="utf-8"))
        indexes = list(range(len(tips[char_id])))
    elif len(args) > 1:
        indexes = [int(x) for x in args[1].split(",")]
    else:
        indexes = sorted(RANA_FIX) if char_id == "rana" else []
    generate(char_id, indexes)
    return 0


if __name__ == "__main__":
    sys.exit(main())
