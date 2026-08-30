# -*- coding: utf-8 -*-
"""特殊重生成：
- soyo/11: 春日影->はるひかげ；taki/23: 我儘->わがまま；tomori/18: 迷子->まいご,遠回り->とおまわり
- tomori/9: 超短句，用 PAD 填充生成后按期望时长精确裁剪
"""
import base64, json, math, os, re, struct, subprocess, sys
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"]="TRUE"; os.environ["OMP_NUM_THREADS"]="1"; os.environ["PYTHONIOENCODING"]="utf-8"
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT/"tools"))
from generate_voices_local import (PROJECT_DIR, MODEL_DIR, GGUF_BIN_DIR, ASSETS_ROOT, VOICE_DIR,
                                   ROLE_MAP, pick_reference, clean_audio, wav_seconds, fix_ha)
from generate_padded import REF_BY_CHAR, estimate_seconds, PAD

TIPS_JA = ASSETS_ROOT/"builtin_tips"/"tips_ja.json"
OUT_DIR = ASSETS_ROOT/"voices_builtin"
TMP = ROOT/"tools"/"gen_tmp"
FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"
API = "https://opencode.ai/zen/go/v1/chat/completions"
MODEL = "mimo-v2.5"
RATE = 24000

TEXT_OVERRIDE = {
    ("soyo", 11): ("春日影", "はるひかげ"),
    ("taki", 23): ("我儘", "わがまま"),
    ("tomori", 18): [("迷子", "まいご"), ("遠回り", "とおまわり")],
}
PADDED = {("tomori", 9)}

def load_key():
    v = os.environ.get("VISION_API_KEY") or os.environ.get("OPENCODE_API_KEY")
    if v: return v.strip()
    try:
        auth = json.loads((Path.home()/".local"/"share"/"opencode"/"auth.json").read_text(encoding="utf-8"))
        k = (auth.get("opencode-go") or auth.get("opencode_go") or {}).get("key")
        if k: return k.strip()
    except Exception: pass
    return None

def transcribe(mp3, key):
    b64 = base64.b64encode(mp3.read_bytes()).decode("ascii")
    body = {"model": MODEL, "messages": [{"role":"user","content":[
        {"type":"text","text":"逐字转录这段日语语音，保持原样。只输出转录文本。"},
        {"type":"input_audio","input_audio":{"data":"data:audio/mpeg;base64,"+b64}}]}], "max_tokens": 512}
    import requests
    r = requests.post(API, headers={"Content-Type":"application/json","Authorization":"Bearer "+key},
                      data=json.dumps(body, ensure_ascii=False).encode("utf-8"), timeout=90)
    if r.status_code != 200: return ""
    return ((r.json().get("choices") or [{}])[0].get("message",{}).get("content") or "").strip()

def decode_wav(path):
    r = subprocess.run([FFMPEG,"-v","error","-i",str(path),"-f","s16le","-ac","1","-ar",str(RATE),"pipe:1"],capture_output=True)
    if r.returncode or not r.stdout: return None
    n=len(r.stdout)//2
    return struct.unpack("<%dh"%n, r.stdout[:n*2])

def rms_groups(samples, win=0.02, voice=800, gap=0.28):
    n=len(samples); chunk=int(RATE*win)
    rms=[]
    i=0
    while i<n:
        seg=samples[i:i+chunk]
        rms.append(math.sqrt(sum(x*x for x in seg)/len(seg)))
        i+=chunk
    segs=[]; cur=None
    for j,rr in enumerate(rms):
        if rr>=voice:
            if cur is None: cur=[j,j]
            else: cur[1]=j
        else:
            if cur is not None: segs.append((cur[0],cur[1])); cur=None
    if cur is not None: segs.append((cur[0],cur[1]))
    groups=[]
    for s in segs:
        if not groups: groups.append(s)
        elif (s[0]-groups[-1][1]-1)*win > gap: groups.append(s)
        else: groups[-1]=(groups[-1][0],s[1])
    return rms, groups

def trim(raw, out, start_s, end_s=None):
    filt = "atrim=start=%f,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.01" % start_s
    if end_s is not None:
        filt = "atrim=start=%f:end=%f,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.01" % (start_s, end_s)
    subprocess.run([FFMPEG,"-y","-v","error","-i",str(raw),"-af",filt,"-b:a","128k",str(out)],check=True)

def main():
    key = load_key()
    if not key: print("no key"); return 1
    tips = json.loads(TIPS_JA.read_text(encoding="utf-8"))
    TMP.mkdir(parents=True, exist_ok=True)
    os.chdir(PROJECT_DIR)
    sys.path.insert(0, str(PROJECT_DIR))
    os.environ["PATH"] = str(GGUF_BIN_DIR) + os.pathsep + os.environ.get("PATH", "")
    try: os.add_dll_directory(str(GGUF_BIN_DIR))
    except Exception: pass
    from qwen3_tts_gguf.inference import TTSEngine, TTSConfig
    from qwen3_tts_gguf.inference.proxy import DecoderProxy
    _orig_wait = DecoderProxy.wait_until_ready
    DecoderProxy.wait_until_ready = lambda self, timeout=10: _orig_wait(self, timeout=max(int(timeout), 180))
    engine = TTSEngine(str(MODEL_DIR), verbose=False, enable_speaker=False)
    streams = {}
    warns = []
    try:
        for (cid, idx) in [("tomori",9),("soyo",11),("taki",23),("tomori",18)]:
            if cid not in streams:
                if cid in REF_BY_CHAR:
                    role, ref_name = REF_BY_CHAR[cid]
                else:
                    role = {v: k for k, v in ROLE_MAP.items()}[cid]
                    ref_name = pick_reference(cid)
                    if ref_name is None:
                        print(f"SKIP {cid}: no ref", flush=True)
                        continue
                ref_wav = TMP / f"{cid}_sp_ref.wav"
                clean_audio(VOICE_DIR/role/ref_name, ref_wav)
                s = engine.create_stream()
                s.set_voice(str(ref_wav), text=ref_name.rsplit(".",1)[0])
                s.join()
                streams[cid] = s
                print(f"== voice {cid}", flush=True)
            stream = streams[cid]
            raw_text = tips[cid][idx]["text"]
            text = fix_ha(raw_text)
            ov = TEXT_OVERRIDE.get((cid, idx))
            if isinstance(ov, tuple):
                a, b = ov
                text = text.replace(a, b)
            elif isinstance(ov, list):
                for a, b in ov:
                    text = text.replace(a, b)
            exp = estimate_seconds(raw_text)
            target = OUT_DIR/cid/"ja"/f"{idx}.mp3"
            best = None
            for shift in range(10):
                raw = TMP / f"{cid}_{idx}_sp.wav"
                try:
                    cfg = TTSConfig(temperature=0.5, sub_temperature=0.5, seed=42+shift, sub_seed=45+shift,
                                    streaming=False, min_p=0.05, sub_do_sample=False)
                    gen_text = (text + PAD) if (cid, idx) in PADDED else text
                    result = stream.clone(gen_text, config=cfg)
                    stream.join()
                    result.save(str(raw))
                    dur_raw = wav_seconds(raw)
                    if dur_raw < 0.3: continue
                    samples = decode_wav(raw)
                    if samples is None: continue
                    if (cid, idx) in PADDED:
                        # 精确裁剪：正文到 exp*1.15 或首个大间隔
                        _rms, groups = rms_groups(samples)
                        if not groups:
                            continue
                        start = max(0.0, groups[0][0]*0.02 - 0.08)
                        cut_end = start + exp*1.15
                        # 找到最接近 cut_end 的组结束
                        best_end = None
                        for g in groups:
                            e = (g[1]+1)*0.02
                            if e >= cut_end:
                                best_end = e + 0.05
                                break
                        if best_end is None:
                            best_end = (groups[-1][1]+1)*0.02 + 0.05
                        trim(raw, target, start, best_end)
                    else:
                        start = max(0.0, groups[0][0]*0.02 - 0.08) if False else 0.0
                        trim(raw, target, 0.0)
                    dur = wav_seconds(target)
                    asr = transcribe(target, key)
                    print(f"  {cid}/{idx} seed={42+shift} raw={dur_raw:.2f} out={dur:.2f} exp={exp:.2f} ASR={asr[:50]}", flush=True)
                    ok_dur = 0.6*exp <= dur <= 2.4*exp
                    if ok_dur:
                        print("  -> ACCEPT", flush=True)
                        best = ("accept", dur)
                        break
                    if best is None or abs(dur-exp) < abs(best[1]-exp):
                        best = ("warn", dur)
                except Exception as e:
                    print(f"  {cid}/{idx} ERR {repr(e)[:120]}", flush=True)
                finally:
                    if raw.exists(): raw.unlink()
            if best is None: warns.append(f"{cid}/{idx} 全部失败")
            elif best[0]=="warn": warns.append(f"{cid}/{idx} 时长未达标 best={best[1]:.2f} exp={exp:.2f}")
            print(f"DONE {cid}/{idx}", flush=True)
    finally:
        for s in streams.values(): s = None
        engine.shutdown()
    print("== WARN ==")
    for w in warns: print(w)

if __name__ == "__main__":
    sys.exit(main())
