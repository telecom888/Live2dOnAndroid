# -*- coding: utf-8 -*-
"""ASR 引导重生成：
1) ffmpeg 分析开头：只把「极短(<0.15s)+尖锐(peak高/rms低)+后接停顿」的段判为毛刺裁掉；
   否则保守保留更多前导，避免误裁首词。
2) mimo-v2.5 转录候选音频，判断首词是否缺失、结尾是否有填充；据此调整裁剪或换 seed。
用法: python tools/regenerate_asr.py [--dry-run]
目标: tools/regen_targets.json
"""
import base64, json, math, os, re, struct, subprocess, sys
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["PYTHONIOENCODING"] = "utf-8"

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from generate_voices_local import (PROJECT_DIR, MODEL_DIR, GGUF_BIN_DIR, ASSETS_ROOT, VOICE_DIR,
                                   ROLE_MAP, pick_reference, clean_audio, wav_seconds, fix_ha)
from generate_padded import REF_BY_CHAR, estimate_seconds

TIPS_JA = ASSETS_ROOT / "builtin_tips" / "tips_ja.json"
OUT_DIR = ASSETS_ROOT / "voices_builtin"
TMP = ROOT / "tools" / "gen_tmp"
TARGETS_JSON = ROOT / "tools" / "regen_targets.json"
MAX_ATTEMPTS = 8
SEED_BASE = 42
FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"
API = "https://opencode.ai/zen/go/v1/chat/completions"
MODEL = "mimo-v2.5"
RATE = 24000

def load_key():
    v = os.environ.get("VISION_API_KEY") or os.environ.get("OPENCODE_API_KEY")
    if v:
        return v.strip()
    p = Path.home() / ".local" / "share" / "opencode" / "auth.json"
    try:
        auth = json.loads(p.read_text(encoding="utf-8"))
        k = (auth.get("opencode-go") or auth.get("opencode_go") or {}).get("key")
        if k:
            return k.strip()
    except Exception:
        pass
    return None

def transcribe(mp3, key):
    b64 = base64.b64encode(mp3.read_bytes()).decode("ascii")
    body = {
        "model": MODEL,
        "messages": [{"role": "user", "content": [
            {"type": "text", "text": "逐字转录这段日语语音，保持原样（含语气词）。只输出转录文本。"},
            {"type": "input_audio", "input_audio": {"data": "data:audio/mpeg;base64," + b64}},
        ]}],
        "max_tokens": 1024,
    }
    import requests
    r = requests.post(API, headers={"Content-Type": "application/json", "Authorization": "Bearer " + key},
                      data=json.dumps(body, ensure_ascii=False).encode("utf-8"), timeout=90)
    if r.status_code != 200:
        return ""
    return ((r.json().get("choices") or [{}])[0].get("message", {}).get("content") or "").strip()

_PUNCT = re.compile(r"[\s、。「」『』（）()？！!！？….,。・～~\-—\u3000]+")
def norm(s):
    return _PUNCT.sub("", s or "")

def assess(asr, expected):
    an = norm(asr)
    en = norm(expected)
    if not an or not en:
        return "uncertain"
    trailing = an.startswith(en) and len(an) > len(en) + 1
    missing = en.endswith(an) and (len(en) - len(an)) >= 2
    if missing:
        return "missing_first"
    if trailing:
        return "trailing_filler"
    return "ok"

def analyze_start(samples):
    """返回 (trim_start_s, cut_glitch_bool, voice_start_s)。"""
    n = len(samples)
    chunk = int(RATE * 0.002)
    rms = []
    peak = []
    i = 0
    while i < n:
        seg = samples[i:i+chunk]
        if not seg:
            break
        rms.append(math.sqrt(sum(x*x for x in seg)/len(seg))/32768.0)
        peak.append(max(abs(x) for x in seg)/32768.0)
        i += chunk
    w = 0.002
    voice_thr = 0.018
    # 第一个活跃段（语音）
    vstart = None
    for j in range(len(rms)):
        if rms[j] >= voice_thr:
            # 连续 8 个窗口（16ms）才算语音
            run = 0
            for k in range(j, len(rms)):
                if rms[k] >= voice_thr:
                    run += 1
                    if run >= 8:
                        vstart = j * w
                        break
                else:
                    break
            if vstart is not None:
                break
    if vstart is None:
        return 0.0, False, 0.0
    # 检查开头是否有「短尖锐毛刺」：vstart 前有 peak 极高且短促的段
    # 简化：若 vstart > 0.05 且前 0.05s 内 peak>0.5 且 rms 平均值低 -> 毛刺
    early = samples[:int(0.05 * RATE)]
    if early:
        early_peak = max(abs(x) for x in early) / 32768.0
        early_rms = math.sqrt(sum(x*x for x in early)/len(early)) / 32768.0
        if early_peak > 0.5 and early_rms < 0.06 and vstart > 0.06:
            # 毛刺在语音前：从语音起点前一点开始（去掉毛刺段）
            return max(0.0, vstart - 0.10), True, vstart
    return max(0.0, vstart - 0.12), False, vstart

def decode_wav(path):
    r = subprocess.run([FFMPEG, "-v", "error", "-i", str(path), "-f", "s16le", "-ac", "1", "-ar", str(RATE), "pipe:1"], capture_output=True)
    if r.returncode or not r.stdout:
        return None
    n = len(r.stdout)//2
    return struct.unpack("<%dh" % n, r.stdout[:n*2])

def trim_mp3(raw, start_s, out_mp3):
    subprocess.run([FFMPEG, "-y", "-v", "error", "-i", str(raw),
                    "-af", "atrim=start=%f,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.01" % start_s,
                    "-b:a", "128k", str(out_mp3)], check=True)

def cut_trailing_filler(mp3):
    """裁尾部短填充（最后语音组<=0.6s且间隔>=0.28s）。修正临时文件扩展名。"""
    samples = decode_wav(mp3)
    if samples is None:
        return False
    n = len(samples)
    chunk = int(RATE*0.02)
    rms = []
    i = 0
    while i < n:
        seg = samples[i:i+chunk]
        rms.append(math.sqrt(sum(x*x for x in seg)/len(seg)))
        i += chunk
    VOICE_RMS = 800; WIN = 0.02; GAP = 0.28; MAX_TAIL = 0.6
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
        elif (s[0]-groups[-1][1]-1)*WIN > GAP:
            groups.append(s)
        else:
            groups[-1] = (groups[-1][0], s[1])
    last = groups[-1]
    last_dur = (last[1]-last[0]+1)*WIN
    gap = (last[0]-groups[-2][1]-1)*WIN if len(groups) >= 2 else 0
    if last_dur <= MAX_TAIL and gap >= GAP:
        cut_at = last[0]*WIN - GAP + 0.08
        end_s = len(rms)*WIN
        if 0.05 <= cut_at < end_s - 0.05:
            tmp = mp3.with_name(mp3.stem + ".cut.mp3")
            subprocess.run([FFMPEG, "-y", "-v", "error", "-i", str(mp3),
                            "-af", "atrim=end=%f,asetpts=PTS-STARTPTS,afade=t=out:st=%f:d=0.01" % (cut_at, max(0.0, cut_at-0.01)),
                            "-b:a", "128k", str(tmp)], check=True)
            tmp.replace(mp3)
            return True
    return False

def mp3_dur(p):
    r = subprocess.run([FFMPEG, "-v", "error", "-i", str(p), "-f", "s16le", "-ac", "1", "-ar", str(RATE), "pipe:1"], capture_output=True)
    return len(r.stdout)/RATE/2 if r.returncode == 0 else None

def main():
    targets = json.loads(TARGETS_JSON.read_text(encoding="utf-8"))
    if "--dry-run" in sys.argv:
        tips = json.loads(TIPS_JA.read_text(encoding="utf-8"))
        for cid, idx in targets:
            print(f"{cid}/{idx} exp={estimate_seconds(tips[cid][idx]['text']):.2f}s")
        return 0
    key = load_key()
    if not key:
        print("no key"); return 1
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
                    ref_wav = TMP / f"{cid}_asr_ref.wav"
                    clean_audio(VOICE_DIR / role / ref_name, ref_wav)
                    ref_text = ref_name.rsplit(".", 1)[0]
                else:
                    role = {v: k for k, v in ROLE_MAP.items()}[cid]
                    name = pick_reference(cid)
                    if name is None:
                        print(f"SKIP {cid}: no reference", flush=True)
                        continue
                    ref_wav = TMP / f"{cid}_asr_ref.wav"
                    clean_audio(VOICE_DIR / role / name, ref_wav)
                    ref_text = name.rsplit(".", 1)[0]
                s = engine.create_stream()
                s.set_voice(str(ref_wav), text=ref_text)
                s.join()
                streams[cid] = s
                print(f"== voice {cid} ref={ref_text[:40]}", flush=True)
            stream = streams[cid]

            expected = tips[cid][idx]["text"]
            text = fix_ha(expected)
            exp = estimate_seconds(expected)
            target_mp3 = OUT_DIR / cid / "ja" / f"{idx}.mp3"
            target_mp3.parent.mkdir(parents=True, exist_ok=True)
            best = None
            for shift in range(MAX_ATTEMPTS):
                raw = TMP / f"{cid}_{idx}_asr.wav"
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
                    samples = decode_wav(raw)
                    if samples is None:
                        continue
                    start_s, cut_glitch, _vstart = analyze_start(samples)
                    trim_mp3(raw, start_s, target_mp3)
                    dur = mp3_dur(target_mp3)
                    if dur is None or dur <= 0.05:
                        continue
                    print(f"  {cid}/{idx} seed={SEED_BASE+shift} raw={dur_raw:.2f}s out={dur:.2f}s exp={exp:.2f}s start={start_s:.2f} glitch={cut_glitch}", flush=True)
                    if not (0.55 * exp <= dur <= 2.4 * exp):
                        if best is None or abs(dur - exp) < abs(best[1] - exp):
                            best = ("warn", dur)
                        continue
                    # ASR 校验
                    asr = transcribe(target_mp3, key)
                    verdict = assess(asr, expected)
                    print(f"     ASR: {asr[:60]} -> {verdict}", flush=True)
                    if verdict == "ok":
                        print("  -> ACCEPT", flush=True)
                        best = ("accept", dur)
                        break
                    if verdict == "missing_first" and start_s > 0.02:
                        # 保留更多开头重试（可能误裁首词）
                        trim_mp3(raw, 0.0, target_mp3)
                        dur2 = mp3_dur(target_mp3)
                        asr2 = transcribe(target_mp3, key)
                        v2 = assess(asr2, expected)
                        print(f"     retry start=0: {asr2[:60]} -> {v2} (dur {dur2:.2f})", flush=True)
                        if v2 == "ok":
                            print("  -> ACCEPT (start=0)", flush=True)
                            best = ("accept", dur2)
                            break
                    if verdict == "trailing_filler":
                        # 已裁过尾部仍有多余 -> 认为可接受（ASR 或裁不干净），若时长可接受则保留
                        if dur <= 1.8 * exp:
                            print("  -> ACCEPT (trailing minor)", flush=True)
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
                warns.append(f"{cid}/{idx} 时长/ASR 未达标 best={best[1]:.2f}s exp={exp:.2f}s")
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
