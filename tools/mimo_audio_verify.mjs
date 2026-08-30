#!/usr/bin/env node
// 临时工具：用 opencode go 线路 mimo-v2.5 核验音频（OpenAI 兼容 input_audio 格式）
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { homedir } from 'node:os';

function loadApiKey() {
  if (process.env.VISION_API_KEY) return process.env.VISION_API_KEY.trim();
  if (process.env.OPENCODE_API_KEY) return process.env.OPENCODE_API_KEY.trim();
  try {
    const p = process.env.OPENCODE_AUTH_PATH || resolve(homedir(), '.local', 'share', 'opencode', 'auth.json');
    const auth = JSON.parse(readFileSync(p, 'utf8'));
    const k = (auth['opencode-go'] || {}).key || (auth.opencode_go || {}).key;
    if (k) return k.trim();
  } catch (e) { /* ignore */ }
  return null;
}

function parseArgs(argv) {
  const opts = { files: [], model: 'mimo-v2.5', api: 'https://opencode.ai/zen/go/v1',
                 prompt: '请用日语逐字转录这段语音。只输出转录文本本身，不要解释，不要加引号。' };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--model') { opts.model = argv[++i]; }
    else if (a === '--api') { opts.api = argv[++i]; }
    else if (a === '--prompt') { opts.prompt = argv[++i]; }
    else if (!a.startsWith('--')) { opts.files.push(a); }
  }
  return opts;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.files.length) { console.error('no audio file'); process.exit(1); }
  const key = loadApiKey();
  if (!key) { console.error('no key'); process.exit(1); }

  const content = [{ type: 'text', text: args.prompt }];
  for (const f of args.files) {
    const buf = readFileSync(resolve(f));
    const mime = f.toLowerCase().endsWith('.wav') ? 'audio/wav' : 'audio/mpeg';
    content.push({ type: 'input_audio', input_audio: { data: 'data:' + mime + ';base64,' + buf.toString('base64') } });
  }

  const body = { model: args.model, messages: [{ role: 'user', content }], max_tokens: 1024 };
  const res = await fetch(args.api.replace(/\/+$/, '') + '/chat/completions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + key },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.text();
    console.error('API ' + res.status + ': ' + err.slice(0, 2000));
    process.exit(1);
  }
  const data = await res.json();
  const msg = (data.choices && data.choices[0] && data.choices[0].message) || {};
  console.log('--- content ---');
  console.log(msg.content || '(null)');
  if (msg.reasoning_content) { console.log('--- reasoning ---'); console.log(msg.reasoning_content.slice(0, 500)); }
  console.log('--- usage ---');
  console.log(JSON.stringify(data.usage || {}));
}
main();
