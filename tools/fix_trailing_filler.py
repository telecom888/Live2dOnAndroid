# -*- coding: utf-8 -*-
"""ASR 引导裁尾部填充：mimo 转录整段，若结尾是えっと/あー等填充词，用 ffmpeg 找到填充起点并裁掉，再 ASR 复核。
只处理 tomori/taki 中时长/期望比 > 1.3 的文件（疑似填充）。"""
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

FILLER = re.compile(r"(えっと|えーっと|ええと|あー|あっ|あーあ|うーん|んー|あのー|あのね|えーと|ええっと)\s*[。、…]*$")

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

def decode(mp3):
    r = subprocess.run([FFMPEG,"-v","error","-i",str(mp3),"-f","s16le","-ac","1","-ar",str(RATE),"pipe:1"],capture_output=True)
    if r.returncode or not r.stdout: return None
    n=len(r.stdout)//2
    return struct.unpack("<%dh"%n, r.stdout[:n*2])

def find_filler_group(mp3):
    samples = decode(mp3)
    if samples is None: return None
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
    # 组
    groups=[]
    for s in segs:
        if not groups: groups.append(s)
        elif (s[0]-groups[-1][1]-1)*WIN > 0.28: groups.append(s)
        else: groups[-1]=(groups[-1][0],s[1])
    if len(groups)<2: return None
    last=groups[-1]
    # 返回填充组起点（秒）与组数
    return {"cut_at": last[0]*WIN - 0.28 + 0.08, "last_dur": (last[1]-last[0]+1)*WIN, "groups": len(groups)}

def cut_at(mp3, cut_at_s):
    tmp = mp3.with_name(mp3.stem+".cut.mp3")
    subprocess.run([FFMPEG,"-y","-v","error","-i",str(mp3),
                    "-af","atrim=end=%f,asetpts=PTS-STARTPTS,afade=t=out:st=%f:d=0.01"%(cut_at_s,max(0.0,cut_at_s-0.01)),
                    "-b:a","128k",str(tmp)],check=True)
    tmp.replace(mp3)

def main():
    key = load_key()
    if not key: print("no key"); return 1
    dry = "--dry-run" in sys.argv
    # 只处理 tomori/taki
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
    changed=[]
    for cid in ["tomori","taki"]:
        d=VOICES/cid/"ja"
        for mp3 in sorted(d.glob("*.mp3"), key=lambda p:int(p.stem)):
            i=int(mp3.stem)
            exp=max(0.6, mora(tips[cid][i]["text"])/PACE[cid])
            r=subprocess.run([FFMPEG,"-v","error","-i",str(mp3),"-f","s16le","-ac","1","-ar",str(RATE),"pipe:1"],capture_output=True)
            dur=len(r.stdout)/RATE/2 if r.returncode==0 else 0
            if dur <= 0 or dur/exp <= 1.30:
                continue
            asr = transcribe(mp3, key)
            if not FILLER.search(asr):
                print(f"{cid}/{i}: ratio={dur/exp:.2f} ASR尾干净 -> skip", flush=True)
                continue
            info = find_filler_group(mp3)
            if info is None:
                print(f"{cid}/{i}: 检测到填充但找不到组边界 -> skip", flush=True)
                continue
            cut = info["cut_at"]
            print(f"{cid}/{i}: ratio={dur/exp:.2f} ASR尾={asr[-30:]!r} -> cut@{cut:.2f}s", flush=True)
            if dry:
                changed.append(f"{cid}/{i}")
                continue
            cut_at(mp3, cut)
            asr2 = transcribe(mp3, key)
            if FILLER.search(asr2):
                print(f"  复核仍有填充: {asr2[-30:]!r}", flush=True)
            else:
                print(f"  复核干净: {asr2[-40:]!r}", flush=True)
            changed.append(f"{cid}/{i}")
    print("changed:", changed)

if __name__ == "__main__":
    sys.exit(main())
