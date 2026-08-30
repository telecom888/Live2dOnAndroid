# -*- coding: utf-8 -*-
"""批量核验预生成日语语音：用 opencode go 线路 mimo-v2.5 做音频转录，与源文本比对。

用法:
  python tools/verify_voices_mimo.py                # 核验全部
  python tools/verify_voices_mimo.py anon soyo      # 只核验指定角色
  python tools/verify_voices_mimo.py --limit 5      # 每角色只核验前 N 条（试跑）
输出: tools/verify_report.json / verify_report.txt
"""
import base64
import concurrent.futures as cf
import json
import os
import pathlib
import re
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
TIPS_JA = ASSETS / "builtin_tips" / "tips_ja.json"
VOICES = ASSETS / "voices_builtin"
API = "https://opencode.ai/zen/go/v1/chat/completions"
MODEL = "mimo-v2.5"
WORKERS = 6
RETRIES = 3


def load_key():
    v = os.environ.get("VISION_API_KEY") or os.environ.get("OPENCODE_API_KEY")
    if v:
        return v.strip()
    p = os.environ.get("OPENCODE_AUTH_PATH") or pathlib.Path.home() / ".local" / "share" / "opencode" / "auth.json"
    try:
        auth = json.loads(p.read_text(encoding="utf-8"))
        k = (auth.get("opencode-go") or auth.get("opencode_go") or {}).get("key")
        if k:
            return k.strip()
    except Exception:
        pass
    return None


def normalize(s: str) -> str:
    if not s:
        return ""
    # 全角→半角
    out = []
    for ch in s:
        code = ord(ch)
        if code == 0x3000:
            out.append(" ")
        elif 0xFF01 <= code <= 0xFF5E:
            out.append(chr(code - 0xFEE0))
        else:
            out.append(ch)
    t = "".join(out)
    # 去掉标点/空白/特殊符号
    t = re.sub(r"[\s、。「」『』（）()？！!？….,。・～~\-—\u3000]", "", t)
    # 音素归一：生成端把助词 は 写成 ハ 避免读 wa；核验时视为同一
    t = t.replace("ハ", "は").replace("ヵ", "か").replace("ヶ", "け")
    return t


def transcribe(key: str, mp3_path: pathlib.Path, expected: str) -> dict:
    b64 = base64.b64encode(mp3_path.read_bytes()).decode("ascii")
    prompt = ("这是日语角色语音。请用日语逐字转录这段语音，只输出转录文本本身，"
              "不要解释、不要加引号、不要输出其他任何内容。")
    body = {
        "model": MODEL,
        "messages": [{"role": "user", "content": [
            {"type": "text", "text": prompt},
            {"type": "input_audio", "input_audio": {"data": "data:audio/mpeg;base64," + b64}},
        ]}],
        "max_tokens": 1024,
    }
    last_err = None
    for attempt in range(RETRIES):
        try:
            r = requests.post(API, headers={"Content-Type": "application/json", "Authorization": "Bearer " + key},
                              data=json.dumps(body, ensure_ascii=False).encode("utf-8"), timeout=90)
            if r.status_code == 200:
                data = r.json()
                content = (data.get("choices") or [{}])[0].get("message", {}).get("content") or ""
                content = content.strip()
                return {"ok": True, "actual": content,
                        "match": normalize(content) == normalize(expected),
                        "status": 200}
            if r.status_code in (401, 402, 403, 429):
                return {"ok": False, "actual": "", "match": False, "status": r.status_code,
                        "err": r.text[:300]}
            last_err = f"{r.status_code}: {r.text[:200]}"
        except Exception as e:
            last_err = str(e)
        time.sleep(2 * (attempt + 1))
    return {"ok": False, "actual": "", "match": False, "status": 0, "err": last_err}


def main():
    limit = None
    argv = sys.argv[1:]
    if "--limit" in argv:
        li = argv.index("--limit")
        limit = int(argv[li + 1])
        del argv[li:li + 2]
    args = [a for a in argv if not a.startswith("--")]
    key = load_key()
    if not key:
        print("找不到 API Key")
        return 1

    tips = json.loads(TIPS_JA.read_text(encoding="utf-8"))
    tasks = []
    for char_id, lines in tips.items():
        if args and char_id not in args:
            continue
        for i, line in enumerate(lines):
            mp3 = VOICES / char_id / "ja" / f"{i}.mp3"
            if mp3.exists():
                tasks.append((char_id, i, line.get("text", ""), mp3))
        if limit:
            tasks = [t for t in tasks if t[0] != char_id or t[1] < limit]

    print(f"待核验 {len(tasks)} 条 -> {API} model={MODEL}")
    results = []
    start = time.time()
    with cf.ThreadPoolExecutor(max_workers=WORKERS) as ex:
        futs = {ex.submit(transcribe, key, mp3, text): (char_id, i, text) for char_id, i, text, mp3 in tasks}
        done = 0
        for fut in cf.as_completed(futs):
            char_id, idx, text = futs[fut]
            res = fut.result()
            res.update({"char": char_id, "index": idx, "expected": text})
            results.append(res)
            done += 1
            if done % 10 == 0 or done == len(tasks):
                print(f"  进度 {done}/{len(tasks)}  {time.time()-start:.0f}s")
    results.sort(key=lambda r: (r["char"], r["index"]))

    out = ROOT / "tools" / "verify_report.json"
    out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    txt = ROOT / "tools" / "verify_report.txt"
    lines = []
    fail = [r for r in results if not r.get("ok")]
    mismatch = [r for r in results if r.get("ok") and not r["match"]]
    lines.append(f"总数={len(results)}  API失败={len(fail)}  转录不一致={len(mismatch)}  一致={len(results)-len(fail)-len(mismatch)}")
    lines.append("")
    lines.append("== API 失败 ==")
    for r in fail:
        lines.append(f"  {r['char']}/{r['index']} status={r.get('status')} err={r.get('err','')[:120]}")
    lines.append("")
    lines.append("== 转录不一致（需人工复核）==")
    for r in mismatch:
        lines.append(f"  {r['char']}/{r['index']}")
        lines.append(f"    期望: {r['expected']}")
        lines.append(f"    转录: {r['actual']}")
    txt.write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines))
    return 0


import requests

if __name__ == "__main__":
    sys.exit(main())
