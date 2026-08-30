# -*- coding: utf-8 -*-
"""核验日语语音（问答模式）：把音频+期望文本一起发给 mimo-v2.5，让模型判定一致/不一致。

用法:
  python tools/verify_voices_mimo_qa.py [角色...] [--limit N]
输出: tools/verify_qa_report.json / verify_qa_report.txt
"""
import base64
import concurrent.futures as cf
import json
import os
import pathlib
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
TIPS_JA = ASSETS / "builtin_tips" / "tips_ja.json"
VOICES = ASSETS / "voices_builtin"
API = "https://opencode.ai/zen/go/v1/chat/completions"
MODEL = "mimo-v2.5"
WORKERS = 20
RETRIES = 3
REFUSAL_MARKS = ("无法", "できません", "申し訳", "抱歉", "音声ファイルを聞くことができません", "cannot", "I can't", "I cannot")


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


def is_refusal(s):
    return any(m in s for m in REFUSAL_MARKS)


def qa_once(key: str, mp3_path: pathlib.Path, expected: str) -> dict:
    b64 = base64.b64encode(mp3_path.read_bytes()).decode("ascii")
    prompt = (
        "下面是一段日语角色语音。请把语音与下面的日语文本逐句对比：\n"
        + expected +
        "\n允许标点符号和汉字/假名表记差异，但不允许漏词、错词或顺序错误。\n"
        "请严格按两行输出：\n"
        "第一行：一致 或 不一致\n"
        "第二行：你实际听到的日语文本（逐字，尽量保留原样，包括人名和语气词）"
    )
    body = {
        "model": MODEL,
        "messages": [{"role": "user", "content": [
            {"type": "text", "text": prompt},
            {"type": "input_audio", "input_audio": {"data": "data:audio/mpeg;base64," + b64}},
        ]}],
        "max_tokens": 1024,
    }
    r = requests.post(API, headers={"Content-Type": "application/json", "Authorization": "Bearer " + key},
                      data=json.dumps(body, ensure_ascii=False).encode("utf-8"), timeout=90)
    if r.status_code != 200:
        return {"ok": False, "verdict": "api_error", "actual": "", "status": r.status_code, "err": r.text[:200]}
    data = r.json()
    content = ((data.get("choices") or [{}])[0].get("message", {}).get("content") or "").strip()
    return {"ok": True, "content": content}


def parse(content: str):
    verdict = "unknown"
    actual = ""
    for line in content.splitlines():
        line = line.strip()
        if not line:
            continue
        if "一致" in line or "不一致" in line:
            if "不一致" in line:
                verdict = "不一致"
            elif "一致" in line:
                verdict = "一致"
            # 继续取第二行作为听到的文本；若同行有内容也算
            rest = line.replace("不一致", "").replace("一致", "").strip(" ：:.-—")
            if rest:
                actual = rest
            continue
        if actual == "" and line:
            actual = line
    if verdict == "unknown":
        if content.startswith("一致"):
            verdict = "一致"
        elif content.startswith("不一致"):
            verdict = "不一致"
    return verdict, actual


def qa(key: str, mp3_path: pathlib.Path, expected: str) -> dict:
    last = None
    for attempt in range(RETRIES):
        try:
            res = qa_once(key, mp3_path, expected)
        except Exception as exc:
            last = {"ok": False, "verdict": "api_error", "actual": "", "err": repr(exc)[:200]}
            time.sleep(2 * (attempt + 1))
            continue
        if not res["ok"]:
            last = res
            time.sleep(2 * (attempt + 1))
            continue
        content = res["content"]
        verdict, actual = parse(content)
        if verdict in ("一致", "不一致"):
            return {"ok": True, "verdict": verdict, "actual": actual, "content": content}
        if is_refusal(content):
            last = {"ok": False, "verdict": "refusal", "actual": "", "content": content}
            time.sleep(2 * (attempt + 1))
            continue
        last = {"ok": False, "verdict": "parse_fail", "actual": "", "content": content}
    return last


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
            if limit is not None and i >= limit:
                break
            mp3 = VOICES / char_id / "ja" / f"{i}.mp3"
            if mp3.exists():
                tasks.append((char_id, i, line.get("text", ""), mp3))
    print(f"待核验 {len(tasks)} 条（问答模式）")
    results = []
    start = time.time()
    with cf.ThreadPoolExecutor(max_workers=WORKERS) as ex:
        futs = {ex.submit(qa, key, mp3, text): (char_id, i, text) for char_id, i, text, mp3 in tasks}
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
    (ROOT / "tools" / "verify_qa_report.json").write_text(
        json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = []
    bad = [r for r in results if r["verdict"] == "不一致"]
    other = [r for r in results if r["verdict"] not in ("一致", "不一致")]
    lines.append(f"总数={len(results)}  一致={len(results)-len(bad)-len(other)}  不一致={len(bad)}  异常={len(other)}")
    lines.append("")
    lines.append("== 不一致 ==")
    for r in bad:
        lines.append(f"  {r['char']}/{r['index']}")
        lines.append(f"    期望: {r['expected']}")
        lines.append(f"    听到: {r['actual']}")
    lines.append("")
    lines.append("== 异常（拒答/解析失败/API错误）==")
    for r in other:
        lines.append(f"  {r['char']}/{r['index']} type={r['verdict']}")
        lines.append(f"    content: {r.get('content','')[:200]}")
    txt = "\n".join(lines)
    (ROOT / "tools" / "verify_qa_report.txt").write_text(txt, encoding="utf-8")
    print(txt)
    return 0


import requests

if __name__ == "__main__":
    sys.exit(main())
