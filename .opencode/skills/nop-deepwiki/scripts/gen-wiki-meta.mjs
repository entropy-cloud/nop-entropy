#!/usr/bin/env node
// DeepWiki Phase 5 元数据生成（零依赖，Node 18+）——SKILL.md Phase 5 的配套工具。
// 职责（顺序即 SKILL Phase 5：gen 先行、check 后行）：
//   1. 从 PLAN.md 页面契约表确定性生成 index.md（含全站 mindmap + commit 快照行）
//   2. 松格式引用重写：子代理输出的 `Sources: [path:10-40]()` 空括号条目，按
//      --repo/PLAN Target 解析目标文件，确定性重写为页面相对真链接（deepwiki-open
//      content.py 模式：正确性从模型责任改为代码责任）
//   3. 从各页 Sources（重写后）构建 meta/wiki-state.json 内容哈希指纹（非 .java 也收）
// 多次运行结果一致。
//
//   node gen-wiki-meta.mjs <wiki-root> [--plan PLAN.md] [--scope <子路径>]
//                          [--repo <目标仓库根>] [--dry]
//
// --repo 缺省时从 PLAN.md 的 "> Target: <路径> @ <commit>" 行解析。
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

// ---------- 0. 目标仓库根 ----------
const planRaw = readFileSync(planPath, 'utf8');
const planTitle = (planRaw.match(/^# .*?([A-Za-z0-9_-]+)\s*$/m) || [, 'Project'])[1];
let repoRoot = argOf('--repo');
if (!repoRoot) {
  const m = planRaw.match(/^>\s*Target:\s*(.+?)\s+@\s*\S+/m);
  if (m) repoRoot = m[1].trim();
}
repoRoot = repoRoot ? resolve(repoRoot) : null;
// git 顶层：松格式路径与重写链接的统一基准（页面作者口径混杂：仓库相对/模块相对并存）
let topLevel = repoRoot;
try { topLevel = resolve(execSync('git rev-parse --show-toplevel', { cwd: repoRoot || root }).toString().trim()); } catch {}

// ---------- 1. 解析 PLAN.md 页面契约表 ----------
// 新模板：| 路径 | 所属章 | 标题 | ...（header 含"所属章"）；旧模板：| 路径 | 标题 | ...
const planLines = planRaw.split('\n');
const hasSectionCol = planLines.some((l) => l.includes('|') && l.includes('所属章'));
const pages = []; // {path,title,section,order}
for (const line of planLines) {
  if (!line.trim().startsWith('|')) continue;
  const cells = line.split('|').map((c) => c.trim());
  const first = cells[1] || '';
  if (!/^[A-Za-z0-9][\w./-]*\.md$/.test(first)) continue; // 只取首列为页面路径的行
  if (hasSectionCol) {
    pages.push({ path: first, title: cells[3] || first, section: cells[2] || '', order: pages.length });
  } else {
    pages.push({ path: first, title: cells[2] || first, section: '', order: pages.length });
  }
}
if (!pages.length) {
  console.error('PLAN.md 页面契约表中没有解析到任何页面行（首列须为 xxx.md 路径）');
  process.exit(2);
}

// ---------- 2. 松格式引用重写（Sources 区内 `[path:10-40]()` → 真链接） ----------
// 语法钉死：text = <仓库相对路径>:<起行>[-<止行>]；空括号。路径必须落在 --repo 内。
const LOUSE_COUNT = /\[[^\[\]]+?:\d+(?:-\d+)?\]\(\s*\)/g;
const LOOSE = /\[([^\[\]]+?):(\d+(?:-\d+)?(?:,\d+(?:-\d+)?)*)\]\(\s*\)/g;
function resolveLoose(pageFile, relPath) {
  if (!repoRoot) return null;
  // 口径探测链：模块根 → 页面目录各级祖先 → git 顶层；第一个存在的文件即命中
  const candidates = [resolve(repoRoot, relPath)];
  let dir = dirname(pageFile);
  while (true) {
    candidates.push(resolve(dir, relPath));
    if (dir === topLevel || dirname(dir) === dir) break;
    dir = dirname(dir);
  }
  for (const target of candidates) {
    if (!existsSync(target) || !statSync(target).isFile()) continue;
    // wiki 自身页面（PLAN.md 除外）不允许被 Sources 引用（互链走正文本链接）；
    // PLAN.md/外部日志等 .md 是合法溯源目标（PLAN 仅此豁免，指纹仍跳过）
    if (target.endsWith('.md') && target !== resolve(root, 'PLAN.md') && (target.startsWith(root + '\\') || target.startsWith(root + '/'))) continue;
    const relFromTop = relative(topLevel, target);
    if (relFromTop.startsWith('..')) return null;
    return { abs: target, relFromTop };
  }
  // 裸文件名兜底：无目录分隔的 path 在目标树内递归按文件名查（首个命中，deepwiki-open basename 同款）
  if (!relPath.includes('/') && !relPath.includes('\\')) {
    const hits = [];
    const scan = (dir) => {
      if (hits.length > 4) return;
      for (const n of readdirSync(dir)) {
        if (hits.length > 4) return;
        const p = join(dir, n);
        if (statSync(p).isDirectory()) { if (n !== '.git' && !n.startsWith('.')) scan(p); }
        else if (n === relPath) hits.push(p);
      }
    };
    for (const base of [repoRoot, topLevel]) {
      if (base && existsSync(base)) scan(base);
      if (hits.length) break;
    }
    for (const abs of hits) {
      const relFromTop = relative(topLevel, abs);
      if (relFromTop.startsWith('..')) continue;
      if (abs.endsWith('.md') && abs !== resolve(root, 'PLAN.md') && (abs.startsWith(root + '\\') || abs.startsWith(root + '/'))) continue;
      return { abs, relFromTop };
    }
  }
  return null;
}
let rewritten = 0;
let unresolvedLoose = 0;
if (repoRoot && !dry) {
  for (const p of pages) {
    const f = resolve(root, p.path);
    if (!existsSync(f)) continue;
    const text = readFileSync(f, 'utf8');
    let changed = false;
    const out = text.replace(LOOSE, (whole, relPath, ranges) => {
      const hit = resolveLoose(f, relPath);
      if (!hit) {
        unresolvedLoose++;
        return whole; // 留给 check-wiki 报残余
      }
      rewritten++;
      changed = true;
      // GitHub 锚点只支持单区段：取第一个区段做锚，label 保留完整行号清单
      const first = ranges.split(',')[0];
      const [a, b] = first.split('-');
      const anchor = b ? `#L${a}-L${b}` : `#L${a}`;
      const linkPath = '/' + hit.relFromTop.split('\\').join('/');
      return `[${relPath}:${ranges}](${linkPath}${anchor})`;
    });
    if (changed) writeFileSync(f, out);
  }
} else if (!repoRoot) {
  // 无仓库根时统计松格式存量（不重写）
  for (const p of pages) {
    const f = resolve(root, p.path);
    if (!existsSync(f)) continue;
    const m = readFileSync(f, 'utf8').match(LOUSE_COUNT);
    if (m) unresolvedLoose += m.length;
  }
}
// ---------- 3. 确定性生成 index.md（分组 + mindmap + 快照行） ----------
const GROUP = (p) => (p.startsWith('modules/') ? '模块' : p.startsWith('flows/') ? '机制' : p.startsWith('topics/') ? '主题' : '指南');
const GROUP_ORDER = ['指南', '机制', '模块', '主题'];
const slug = (p) => relative(root, resolve(root, p)).replace(/\.md$/, '').replace(/[/_-]/g, ' ');
// mindmap 节点文本确定性清洗：ASCII 括号/引号/反引号会破坏语法，替换为安全字符
const cleanNode = (t) => String(t).replace(/[\[\]\(\)\{\}"'`]/g, ' ').replace(/\s+/g, ' ').trim() || '-';

let md = `# ${planTitle} DeepWiki\n\n`;
md += `> 目标：${argOf('--scope') || '全仓库'} · 结构契约：[PLAN.md](./PLAN.md) · 本文件由 gen-wiki-meta.mjs 生成，勿手改\n\n`;
// 全站 mindmap（PLAN 分组列 → 分组 → 页面）
md += '```mermaid\nmindmap\n';
md += `  root((${cleanNode(planTitle)}))\n`;
const byGroup = {};
for (const p of pages) {
  const g = GROUP(p.path);
  (byGroup[g] ||= []).push(p);
}
for (const g of GROUP_ORDER) {
  if (!byGroup[g]) continue;
  md += `    ${g}\n`;
  for (const p of byGroup[g]) md += `      ${cleanNode(p.title || slug(p.path))}\n`;
}
md += '```\n';
// 页脚快照
let commit = 'unknown';
try { commit = execSync('git rev-parse --short HEAD', { cwd: repoRoot || root }).toString().trim(); } catch {}
md += `\n> 快照：${argOf('--scope') || ''} @ ${commit} · ${new Date().toISOString().slice(0, 10)}\n`;
// 分组条目
let last = '';
for (const p of pages) {
  const g = GROUP(p.path);
  if (g !== last) { md += `\n## ${g}\n\n`; last = g; }
  md += `- [${slug(p.path)}](${p.path}) — ${p.title}\n`;
}
if (!dry) writeFileSync(join(root, 'index.md'), md);
console.log(`index.md：${pages.length} 条目${dry ? '（dry，未写入）' : ' 已写入'}（含 mindmap 与快照行）`);

// ---------- 4. 从各页 Sources 构建 wiki-state.json ----------
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
  let text = readFileSync(f, 'utf8');
  // On this page 锚点目录（deepwiki.com 全形态骨架；确定性追加，幂等：已有则先移除）
  if (!dry) {
    text = text.replace(/\n---\n\n## On this page\n[\s\S]*$/, '\n');
    const h2s = [...text.matchAll(/^##\s+(.+?)\s*$/gm)].map((m) => m[1].trim())
      .filter((t) => !/^Sources\b/.test(t) && !/^On this page/.test(t));
    if (h2s.length) {
      text = text.trimEnd() + '\n\n---\n\n## On this page\n\n'
        + h2s.map((t) => `- ${t}`).join('\n') + '\n';
      writeFileSync(f, text);
    }
  }
  const sec = text.match(/^##\s*Sources\b[\s\S]*$/m);
  if (!sec) continue;
  const fps = {};
  for (const L of sec[0].matchAll(LINK)) {
    const raw = L[1];
    if (/^(https?:|mailto:|repo:\/\/|#)/.test(raw)) continue;
    const t = decodeURI(raw).split('#')[0];
    if (!t) continue;
    // 真链接：页面相对路径穿越；"/repo-relative" 形式（本脚本重写产物）从仓库根解析
    const target = t.startsWith('/') && topLevel ? resolve(topLevel, '.' + t) : resolve(dirname(f), t);
    const insideWiki = target.startsWith(root);
    if (existsSync(target) && statSync(target).isFile() && (!target.endsWith('.md') || !insideWiki)) {
      fps[relative(topLevel || root, target)] =
        'sha1:' + createHash('sha1').update(readFileSync(target)).digest('hex').slice(0, 16);
    } else if (!target.endsWith('.md')) missing++;
  }
  pageMap[relative(root, f)] = {
    title: (pages.find((p) => p.path === relative(root, f)) || {}).title || '',
    sourceFiles: Object.keys(fps).sort(),
    fingerprints: fps,
  };
}
const state = {
  version: 1,
  target: { root: dirname(root), commit, scope: argOf('--scope') || '', generatedAt: new Date().toISOString() },
  config: prev.config || { language: 'zh', depth: 'standard' }, // depth 为语义档位（不含页数）
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
if (rewritten > 0) console.log(`松格式重写：${rewritten} 条 → 真链接`);
if (unresolvedLoose > 0) console.warn(`注意：${unresolvedLoose} 条松格式引用无法解析（缺 --repo/PLAN Target 行，或路径不存在）——check-wiki 会报残余 ERROR。`);
