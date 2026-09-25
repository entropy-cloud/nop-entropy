#!/usr/bin/env node
// DeepWiki Finalizer 自检（零依赖，Node 18+）
// 对应 SKILL.md Phase 5 第 2 步；检查项源自 survey [01][07][10] 的收尾验证共识。
//
//   node check-wiki.mjs <wiki-root> [--strict]
//
// 检查项：
//   ERROR：断链（相对链接目标不存在）/ Mermaid 块类型不明或围栏不配对 / wiki-state 页面与实际文件不一致
//   WARN ：页面缺 Sources 归属 / index.md 与页面清单漂移 / 缺 wiki-state.json / 覆盖率缺失
//   退出码：无 ERROR 为 0；--strict 时有 WARN 也为 1。

import { readdirSync, readFileSync, existsSync, statSync } from 'node:fs';
import { join, relative, dirname, resolve } from 'node:path';

const rootArg = process.argv[2];
const strict = process.argv.includes('--strict');
if (!rootArg) {
  console.error('用法：node check-wiki.mjs <wiki-root> [--strict]');
  process.exit(2);
}
const root = resolve(rootArg);
if (!existsSync(root) || !statSync(root).isDirectory()) {
  console.error(`目录不存在：${root}`);
  process.exit(2);
}

const errors = [];
const warns = [];
const err = (f, m) => errors.push(`${relative(root, f)}: ${m}`);
const warn = (f, m) => warns.push(`${relative(root, f) || '.'}: ${m}`);

const SKIP_DIRS = new Set(['meta', 'node_modules', '.git']);

function walk(dir) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) {
      if (!SKIP_DIRS.has(name) && !name.startsWith('.')) out.push(...walk(p));
    } else if (name.endsWith('.md')) {
      out.push(p);
    }
  }
  return out;
}

const files = walk(root);

// --- 逐页检查：链接 / Mermaid / Sources ---
const MERMAID_HEAD =
  /^(graph|flowchart|sequenceDiagram|stateDiagram-v2|stateDiagram|classDiagram|erDiagram|journey|gantt|pie|mindmap|timeline|gitGraph|requirementDiagram|C4Context)\b/;
const LINK_RE = /\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g;

let sourceCount = 0;
for (const file of files) {
  const text = readFileSync(file, 'utf8');
  const rel = relative(root, file);

  // Mermaid 围栏配对
  const fences = (text.match(/^```/gm) || []).length;
  if (fences % 2 !== 0) err(file, `代码围栏不配对（\`\`\` 数量 ${fences} 为奇数）`);

  // Mermaid 块
  const blocks = [...text.matchAll(/```mermaid\r?\n([\s\S]*?)```/g)];
  for (const b of blocks) {
    const first = b[1].split('\n').map((l) => l.trim()).find((l) => l && !l.startsWith('%%'));
    if (!first || !MERMAID_HEAD.test(first)) {
      err(file, `Mermaid 块首行无法识别图表类型："${(first || '').slice(0, 40)}"`);
    }
  }

  // Sources 归属（index.md 与 PLAN.md 不要求）
  const isNavPage = rel === 'index.md' || rel === 'PLAN.md';
  if (!isNavPage) {
    if (!/^##\s*Sources\b/m.test(text) && !/\*\*Sources:\*\*/.test(text) && !/> \*\*Sources:\*\*/.test(text)) {
      warn(file, '缺少 Sources 归属（页尾 `## Sources` 或 `> **Sources:**`）');
    } else {
      sourceCount++;
    }
  }

  // 相对链接
  for (const m of text.matchAll(LINK_RE)) {
    const raw = m[1];
    if (/^(https?:|mailto:|repo:\/\/|#)/.test(raw)) continue;
    const hashless = decodeURI(raw).split('#')[0];
    if (!hashless) continue; // 纯锚点
    const target = resolve(dirname(file), hashless);
    const targetMd = target.endsWith('.md') ? target : `${target}.md`;
    if (!existsSync(target) && !existsSync(targetMd)) {
      err(file, `断链：(${raw})`);
    }
  }
}

// --- index.md 同步（索引漂移） ---
const indexFile = join(root, 'index.md');
const contentFiles = files.filter((f) => relative(root, f) !== 'index.md' && relative(root, f) !== 'PLAN.md');
if (!existsSync(indexFile)) {
  warn(root, '缺少 index.md（应由脚本确定性生成，见 SKILL.md Phase 5）');
} else {
  const indexText = readFileSync(indexFile, 'utf8');
  const linked = new Set();
  for (const m of indexText.matchAll(LINK_RE)) {
    const hashless = decodeURI(m[1]).split('#')[0];
    if (!hashless || /^(https?:)/.test(m[1])) continue;
    linked.add(relative(root, resolve(root, hashless)));
  }
  for (const f of contentFiles) {
    if (!linked.has(relative(root, f))) warn(f, '未出现在 index.md（索引漂移）');
  }
}

// --- wiki-state.json 一致性 ---
const stateFile = join(root, 'meta', 'wiki-state.json');
if (!existsSync(stateFile)) {
  warn(root, '缺少 meta/wiki-state.json（增量更新依据，见 SKILL.md Phase 5）');
} else {
  try {
    const state = JSON.parse(readFileSync(stateFile, 'utf8'));
    const statePages = new Set(Object.keys(state.pages || {}));
    const actual = new Set(contentFiles.map((f) => relative(root, f)));
    for (const p of statePages) if (!actual.has(p)) err(root, `wiki-state 记录的页面不存在：${p}`);
    for (const p of actual) if (!statePages.has(p)) err(root, `页面未登记进 wiki-state：${p}`);
    if (!state.coverage || typeof state.coverage.builtFrom !== 'number') {
      warn(root, 'wiki-state 缺少 coverage（诚实覆盖率要求）');
    }
    if (!state.target || !state.target.commit) warn(root, 'wiki-state 缺少 target.commit（版本锚点）');
  } catch (e) {
    err(root, `wiki-state.json 解析失败：${e.message}`);
  }
}

// --- 报告 ---
const relRoot = relative(process.cwd(), root) || '.';
console.log(`check-wiki: ${relRoot} — ${files.length} 个 md 页面，${sourceCount} 个含 Sources`);
for (const e of errors) console.log(`ERROR  ${e}`);
for (const w of warns) console.log(`WARN   ${w}`);
if (!errors.length && !warns.length) console.log('全部检查通过。');
const fail = errors.length > 0 || (strict && warns.length > 0);
console.log(`结果：${errors.length} ERROR / ${warns.length} WARN${strict ? '（--strict：WARN 也算失败）' : ''}`);
process.exit(fail ? 1 : 0);
