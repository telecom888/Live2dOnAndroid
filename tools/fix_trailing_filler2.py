# -*- coding: utf-8 -*-
"""v2：尾段(2.5s)截取 + 敏感提示词 ASR 检测尾部填充，ffmpeg 定位并裁剪。"""
import base64, json, math, os, re, struct, subprocess, sys
from pathlib import Path

ROOT = Path(r"D:\opencode-proj\Live2dOnAndroid")
VOICES = ROOT / "app/src/main/assets/voices_builtin"
FFMPEG = r"E:\ffmpeg\bin\ffmpeg.exe"
API = "https://opencode.ai/zen/go/v1/chat/completions"
MODEL = "mimo-v2.5"
RATE = 24000
WIN = 0.02
VOICE_RMS = 800
TAIL_SEC = 2.5

FILLER = re.compile(r"(えっと|えーっと|ええと|えーと|あー|あっ|あーあ|うーん|んー|あのー|あのね|ええっと)")

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
        {"type":"text","text":"这段是日语语音的结尾片段。逐字转录，特别注意有没有えっと/あー/えーっと等填充词。只输出转录。"},
        {"type":"input_audio","input_audio":{"data":"data:audio/mpeg;base64,"+b64}}]}], "max_tokens": 256}
    import requests
    r = requests.post(API, headers={"Content-Type":"application/json","Authorization":"Bearer "+key},
                      data=json.dumps(body, ensure_ascii=False).encode("utf-8"), timeout=90)
    if r.status_code != 200: return ""
    return ((r.json().get("choices") or [{}])[0].get("message",{}).get("content") or "").strip()

def decode(mp3):
    r = subprocess.run([FFMPEG,"-v","error","-i",str(mp3),"-f","s16le","-ac","1","-ar",str(RATE),"pipe:1"],capture_output=True)
    if r.returncode or not r.stdout: return None
    n=len(r.stdout)//2
    return struct.unpack("<%dh"%n, r.stdout[:n*2])

def dur(mp3):
    r = subprocess.run([FFMPEG,"-v","error","-i",str(mp3),"-f","s16le","-ac","1","-ar",str(RATE),"pipe:1"],capture_output=True)
    return len(r.stdout)/RATE/2 if r.returncode==0 else 0.0

def find_cut(samples, total_s):
    n=len(samples); chunk=int(RATE*WIN)
    rms=[]
    i=0
    while i<n:
        seg=samples[i:i+chunk]
        rms.append(math.sqrt(sum(x*x for x in seg)/len(seg)))
        i+=chunk
    segs=[]; cur=None
    for j,rr in enumerate(rms):
        if rr>=VOICE_RMS:
            if cur is None: cur=[j,j]
            else: cur[1]=j
        else:
            if cur is not None: segs.append((cur[0],cur[1])); cur=None
    if cur is not None: segs.append((cur[0],cur[1]))
    if not segs: return None
    groups=[]
    for s in segs:
        if not groups: groups.append(s)
        elif (s[0]-groups[-1][1]-1)*WIN > 0.28: groups.append(s)
        else: groups[-1]=(groups[-1][0],s[1])
    if len(groups)<2: return None
    last=groups[-1]
    last_dur=(last[1]-last[0]+1)*WIN
    if last_dur > 1.0:
        return None  # 尾部较长，不像填充
    return last[0]*WIN - 0.28 + 0.08

def cut_at(mp3, t):
    tmp = mp3.with_name(mp3.stem+".cut.mp3")
    subprocess.run([FFMPEG,"-y","-v","error","-i",str(mp3),
                    "-af","atrim=end=%f,asetpts=PTS-STARTPTS,afade=t=out:st=%f:d=0.01"%(t,max(0.0,t-0.01)),
                    "-b:a","128k",str(tmp)],check=True)
    tmp.replace(mp3)

def extract_tail(mp3, t, out):
    subprocess.run([FFMPEG,"-y","-v","error","-ss",str(max(0.0,t-TAIL_SEC)),"-i",str(mp3),
                    "-t",str(TAIL_SEC),"-b:a","128k",str(out)],check=True)

def main():
    key = load_key()
    if not key: print("no key"); return 1
    dry = "--dry-run" in sys.argv
    tips = json.loads((ROOT/"app/src/main/assets/builtin_tips/tips_ja.json").read_text(encoding="utf-8"))
    KANA=re.compile(r"[\u3041-\u3096\u30a1-\u30f6]"); KANJI=re.compile(r"[\u4e00-\u9fff]"); DIGIT=re.compile(r"[0-9０-９]")
    def mora(t):
        n=0
        for ch in t:
            if KANA.match(ch): n+=1
            elif KANJI.match(ch): n+=2
            elif DIGIT.match(ch): n+=2
        return max(1,n)
    PACE={"tomori":5.5,"taki":6.5}
    tmpdir = ROOT/"tools"/"gen_tmp"; tmpdir.mkdir(parents=True, exist_ok=True)
    changed=[]
    for cid in ["tomori","taki"]:
        d=VOICES/cid/"ja"
        for mp3 in sorted(d.glob("*.mp3"), key=lambda p:int(p.stem)):
            i=int(mp3.stem)
            exp=max(0.6, mora(tips[cid][i]["text"])/PACE[cid])
            dd = dur(mp3)
            if dd <= 0 or dd/exp <= 1.15:
                continue
            tail_mp3 = tmpdir/f"{cid}_{i}_tail.mp3"
            extract_tail(mp3, dd, tail_mp3)
            asr = transcribe(tail_mp3, key)
            if not FILLER.search(asr):
                print(f"{cid}/{i}: ratio={dd/exp:.2f} 尾干净 -> skip ({asr[:30]!r})", flush=True)
                tail_mp3.unlink(missing_ok=True)
                continue
            samples = decode(mp3)
            t = find_cut(samples, dd) if samples is not None else None
            print(f"{cid}/{i}: ratio={dd/exp:.2f} 尾={asr[:40]!r} cut@{t}", flush=True)
            tail_mp3.unlink(missing_ok=True)
            if t is None:
                continue
            if dry:
                changed.append(f"{cid}/{i}")
                continue
            cut_at(mp3, t)
            dd2 = dur(mp3)
            tail2 = tmpdir/f"{cid}_{i}_tail2.mp3"
            extract_tail(mp3, dd2, tail2)
            asr2 = transcribe(tail2, key)
            print(f"   复核: {asr2[:40]!r}", flush=True)
            tail2.unlink(missing_ok=True)
            changed.append(f"{cid}/{i}")
    print("changed:", changed)

if __name__ == "__main__":
    sys.exit(main())
