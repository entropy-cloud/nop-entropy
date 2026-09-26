#!/usr/bin/env node
// DeepWiki Phase 5 元数据生成（零依赖，Node 18+）——SKILL.md Phase 5 的配套工具。
// 确定性生成 index.md（从 PLAN.md 页面契约表），并从各页 Sources 链接构建 meta/wiki-state.json
// （含全部被引源文件的内容哈希指纹，非 .java 也收）。多次运行结果一致。
//
//   node gen-wiki-meta.mjs <wiki-root> [--plan PLAN.md] [--scope <子路径>] [--dry]
//
// 退出码：0 成功；2 参数/文件错误。

import { readFileSync, writeFileSync, readdirSync, statSync, existsSync, mkdirSync } from 'node:fs';
import { join, relative, dirname, resolve } from 'node:path';
import { createHash } from 'node:crypto';
import { execSync } from 'node:child_process';

function argOf(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : undefined;
}
const root = resolve(process.argv[2] || '.');
if (!existsSync(root) || !statSync(root).isDirectory()) {
  console.error(`wiki 根目录不存在：${root}`);
  process.exit(2);
}
const planPath = resolve(root, argOf('--plan') || 'PLAN.md');
if (!existsSync(planPath)) {
  console.error(`缺少 ${planPath}（结构契约是 index 生成的唯一依据）`);
  process.exit(2);
}
const dry = process.argv.includes('--dry');

// ---------- 1. 解析 PLAN.md 页面契约表 ----------
const plan = readFileSync(planPath, 'utf8');
const planTitle = (plan.match(/^# .*?([A-Za-z0-9_-]+)\s*$/m) || [, 'Project'])[1];
const pages = []; // {path,title,order}
for (const line of plan.split('\n')) {
  if (!line.trim().startsWith('|')) continue;
  const cells = line.split('|').map((c) => c.trim());
  const first = cells[1] || '';
  if (!/^[A-Za-z0-9][\w./-]*\.md$/.test(first)) continue; // 只取首列为页面路径的行
  pages.push({ path: first, title: cells[2] || first, order: pages.length });
}
if (!pages.length) {
  console.error('PLAN.md 页面契约表中没有解析到任何页面行（首列须为 xxx.md 路径）');
  process.exit(2);
}

// ---------- 2. 确定性生成 index.md ----------
const GROUP = (p) => (p.startsWith('modules/') ? '模块' : p.startsWith('topics/') ? '主题' : '指南');
const slug = (p) => relative(root, resolve(root, p)).replace(/\.md$/, '').replace(/[/_-]/g, ' ');
let md = `# ${planTitle} DeepWiki\n\n`
  + `> 目标：${argOf('--scope') || '全仓库'} · 结构契约：[PLAN.md](./PLAN.md) · 本文件由 gen-wiki-meta.mjs 生成，勿手改\n`;
let last = '';
for (const p of pages) {
  const g = GROUP(p.path);
  if (g !== last) { md += `\n## ${g}\n\n`; last = g; }
  md += `- [${slug(p.path)}](${p.path}) — ${p.title}\n`;
}
if (!dry) writeFileSync(join(root, 'index.md'), md);
console.log(`index.md：${pages.length} 条目${dry ? '（dry，未写入）' : ' 已写入'}`);

// ---------- 3. 从各页 Sources 构建 wiki-state.json ----------
const LINK = /\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g;
function walk(dir) {
  let out = [];
  for (const n of readdirSync(dir)) {
    const p = join(dir, n);
    if (statSync(p).isDirectory()) {
      if (n !== 'meta' && !n.startsWith('.')) out.push(...walk(p));
    } else if (n.endsWith('.md') && n !== 'index.md' && n !== 'PLAN.md') out.push(p);
  }
  return out;
}
const statePath = join(root, 'meta', 'wiki-state.json');
const prev = existsSync(statePath) ? JSON.parse(readFileSync(statePath, 'utf8')) : {};
const pageMap = {};
let missing = 0;
for (const f of walk(root)) {
  const text = readFileSync(f, 'utf8');
  const sec = text.match(/^##\s*Sources\b[\s\S]*$/m);
  if (!sec) continue;
  const fps = {};
  for (const L of sec[0].matchAll(LINK)) {
    const raw = L[1];
    if (/^(https?:|mailto:|repo:\/\/|#)/.test(raw)) continue;
    const t = decodeURI(raw).split('#')[0];
    if (!t) continue;
    const target = resolve(dirname(f), t);
    if (existsSync(target) && statSync(target).isFile() && !target.endsWith('.md')) {
      fps[relative(root, target)] =
        'sha1:' + createHash('sha1').update(readFileSync(target)).digest('hex').slice(0, 16);
    } else if (!target.endsWith('.md')) missing++;
  }
  pageMap[relative(root, f)] = {
    title: (pages.find((p) => p.path === relative(root, f)) || {}).title || '',
    sourceFiles: Object.keys(fps).sort(),
    fingerprints: fps,
  };
}
let commit = 'unknown';
try { commit = execSync('git rev-parse --short HEAD', { cwd: root }).toString().trim(); } catch {}
const state = {
  version: 1,
  target: { root: dirname(root), commit, scope: argOf('--scope') || '', generatedAt: new Date().toISOString() },
  config: prev.config || { language: 'zh', depth: 'standard' },
  analysis: {
    tool: 'grep',
    note: '结构提取仅用 Grep/构建文件/目录结构；fan-in 为文件级口径',
  },
  coverage: prev.coverage || { relevantFiles: 0, builtFrom: 0, dropped: [], note: '由编排者补录' },
  pages: pageMap,
};
if (!dry) {
  mkdirSync(join(root, 'meta'), { recursive: true });
  writeFileSync(statePath, JSON.stringify(state, null, 2));
}
const uniq = new Set(Object.values(pageMap).flatMap((p) => p.sourceFiles));
console.log(`wiki-state.json：${Object.keys(pageMap).length} 页 / ${uniq.size} 个指纹文件 / ${missing} 个无法解析的 Sources 链接${dry ? '（dry，未写入）' : ''}`);
if (missing > 0) console.warn(`注意：有 ${missing} 个 Sources 链接未解析到文件，先跑 check-wiki.mjs 定位断链。`);
