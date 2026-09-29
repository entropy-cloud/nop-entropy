#!/usr/bin/env node
// DeepWiki Finalizer 自检（零依赖，Node 18+）
// 对应 SKILL.md Phase 5；检查项源自 survey [01][07][10] 的收尾验证共识 + plan 363 扩展。
//
//   node check-wiki.mjs <wiki-root> [--strict] [--verify-claims N] [--seed S] [--repo R]
//                       [--min-tables N] [--min-mermaid-module N]
//
// 检查项：
//   ERROR：断链（相对链接目标不存在）/ Mermaid 块类型不明或围栏不配对 / wiki-state 页面与实际文件不一致
//          / 残余空括号松格式链接（`[x]()`——应已被 gen-wiki-meta 重写，任何位置）
//          / github 模式下残余 "/repo-rel" 站点根绝对链接（历史重写产物，本地为死链）
//          / --verify-claims：行号越界（恒 ERROR）或容错窗内零关键词命中（机检防引用幻觉）
//   WARN ：页面缺 Sources 归属 / 密度不达标（表格/mermaid，quickstart/reading-guide 豁免）
//          / index.md 与页面清单漂移 / 缺 wiki-state.json / 覆盖率缺失
//          / blob 永久链接的仓库相对路径在本地仓库不存在
//   退出码：无 ERROR 为 0；--strict 时有 WARN 也为 1。

import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { join, relative, dirname, resolve } from 'node:path';
import { execSync } from 'node:child_process';

const rootArg = process.argv[2];
const strict = process.argv.includes('--strict');
function optOf(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : undefined;
}
if (!rootArg) {
  console.error('用法：node check-wiki.mjs <wiki-root> [--strict] [--verify-claims N] [--seed S] [--repo R] [--min-tables N] [--min-mermaid-module N]');
  process.exit(2);
}
const root = resolve(rootArg);
if (!existsSync(root) || !statSync(root).isDirectory()) {
  console.error(`目录不存在：${root}`);
  process.exit(2);
}
const MIN_TABLES = Number(optOf('--min-tables') || 2);
const MIN_MERMAID_MODULE = Number(optOf('--min-mermaid-module') || 3);
const EXEMPT_DENSITY = new Set(['quickstart.md', 'reading-guide.md', 'index.md', 'PLAN.md']); // 命令/步骤与路径导航页
// 目标仓库根（gen-wiki-meta 重写产物链接形如 "/repo/rel/path#L1-L2"，需从仓库根解析）
let repoRoot = optOf('--repo');
if (!repoRoot) {
  const planFile = join(root, 'PLAN.md');
  if (existsSync(planFile)) {
    const m = readFileSync(planFile, 'utf8').match(/^>\s*Target:\s*(.+?)\s+@\s*\S+/m);
    if (m) repoRoot = resolve(m[1].trim());
  }
}
let topLevel = repoRoot;
try { topLevel = resolve(execSync('git rev-parse --show-toplevel', { cwd: repoRoot || root }).toString().trim()); } catch {}
// 断言路径口径探测：模块根 → 页面目录祖先 → git 顶层；裸文件名/包内路径走"作用域后缀扫描"
//（wiki 根目录名即作用域，如 nop-orm——优先路径段精确命中，防裸 pom.xml 解析到仓库根 pom）
const SCOPE = root.split('/').pop().split('\\').pop();
function suffixScan(bareName, segKey) {
  const hits = [];
  const scan = (dir) => {
    if (hits.length > 8) return;
    let entries;
    try { entries = readdirSync(dir); } catch { return; }
    for (const n of entries) {
      if (hits.length > 8) return;
      const p = join(dir, n);
      if (statSync(p).isDirectory()) { if (n !== '.git' && !n.startsWith('.') && n !== '_tmp') scan(p); }
      else if (n === bareName) {
        const norm = p.split('\\').join('/');
        if (!segKey || norm.endsWith('/' + segKey) || norm.endsWith(segKey)) hits.push(norm);
      }
    }
  };
  if (repoRoot && existsSync(repoRoot)) scan(repoRoot);
  const scored = hits.map((h) => [h.split('/').some((seg) => seg === SCOPE) ? 0 : 1, h])
    .sort((a, b) => a[0] - b[0]);
  for (const [, h] of scored) if (!h.endsWith('.md')) return h;
  return null;
}
function resolveClaim(pageFile, relPath) {
  if (!relPath.includes('/')) {
    const hit = suffixScan(relPath, null);
    if (hit) return hit;
  }
  const candidates = [resolve(repoRoot || topLevel, relPath)];
  let dir = dirname(pageFile);
  while (true) {
    candidates.push(resolve(dir, relPath));
    if (dir === topLevel || dirname(dir) === dir) break;
    dir = dirname(dir);
  }
  for (const c of candidates) if (existsSync(c) && statSync(c).isFile() && !c.endsWith('.md')) return c;
  if (relPath.includes('/')) return suffixScan(relPath.split('/').pop(), relPath) ;
  return null;
}

const errors = [];
const warns = [];
const err = (f, m) => errors.push(`${relative(root, f) || '.'}: ${m}`);
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

// 链接基准模式：wiki-state.links.mode === 'github' 时，源码引用应为 blob 永久链接，
// 历史产物 "/repo-rel" 站点根绝对链接视为 ERROR（本地与 GitHub 均为死链）
let githubMode = false;
let blobPrefix = null;
const stateFilePre = join(root, 'meta', 'wiki-state.json');
if (existsSync(stateFilePre)) {
  try {
    const st = JSON.parse(readFileSync(stateFilePre, 'utf8'));
    if (st.links && st.links.mode === 'github' && st.links.repoUrl) {
      githubMode = true;
      blobPrefix = st.links.repoUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '/blob/';
    }
  } catch {}
}

// --- 逐页检查：链接 / Mermaid / Sources / 密度 / 松格式残余 ---
const MERMAID_HEAD =
  /^(graph|flowchart|sequenceDiagram|stateDiagram-v2|stateDiagram|classDiagram|erDiagram|journey|gantt|pie|mindmap|timeline|gitGraph|requirementDiagram|C4Context)\b/;
const LINK_RE = /\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g;
const LOOSE_RE = /\[([^\[\]]+?):(\d+(?:-\d+)?(?:,\d+(?:-\d+)?)*)\]\(\s*\)/g;
const TABLE_ROW = /^\s*\|.+\|\s*$/;
const isModulePage = (rel) => rel.startsWith('modules/');

function countTables(text) {
  let tables = 0, run = 0;
  for (const line of text.split('\n')) {
    if (TABLE_ROW.test(line)) { run++; if (run === 2) tables++; }
    else run = 0;
  }
  return tables;
}

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
  if (isModulePage(rel) && blocks.length < MIN_MERMAID_MODULE) {
    warn(file, `模块页 mermaid 仅 ${blocks.length} 张（< ${MIN_MERMAID_MODULE}）`);
  }

  // 密度：表格 + 词数（deepwiki.com 四形态页均 2200-3300 词，底线 800）
  if (!EXEMPT_DENSITY.has(rel) && rel !== 'PLAN.md') {
    const tables = countTables(text);
    if (tables < MIN_TABLES) warn(file, `表格仅 ${tables} 个（< ${MIN_TABLES}；实体/常量/阶段对照表任选）`);
    const units = (text.match(/[\u4e00-\u9fff]/g) || []).length + (text.match(/[A-Za-z0-9_]+/g) || []).length;
    if (units < 800) warn(file, `词数不足：约 ${units}（< 800；目标 1500-2500 词中位带）`);
  }

  // Sources 归属（index.md 与 PLAN.md 不要求）
  const isNavPage = rel === 'index.md' || rel === 'PLAN.md';
  if (!isNavPage) {
    if (!/^##\s*Sources\b/m.test(text) && !/\*\*Sources:\*\*/.test(text) && !/> \*\*Sources:\*\*/.test(text)) {
      warn(file, '缺少 Sources 归属（页尾 `## Sources` 或 `> **Sources:**`）');
    } else {
      // 有归属但无可点击源码链接（纯文本文件名清单——gen 无法重写，等于没有引用）
      const srcCtx = (text.match(/^> Sources: .*$/gm) || []).join('\n')
        + (text.match(/^##\s*Sources\b[\s\S]*$/m) || [''])[0];
      if (srcCtx && !/\]\(/.test(srcCtx)) {
        warn(file, 'Sources 仅有纯文本文件名、零可点击链接（应写成 [path:line]() 松格式交 gen 重写）');
      }
      sourceCount++;
    }
  }

  // Sources 区残余松格式（应已被 gen-wiki-meta 重写为真链接）
  const sec = text.match(/^##\s*Sources\b[\s\S]*$/m);
  if (sec) {
    for (const m of sec[0].matchAll(LOOSE_RE)) {
      err(file, `Sources 区残余松格式（未重写）：[${m[1]}:${m[2]}]()`);
    }
  }

  // 残余空括号链接（任何位置——空 href 即死链；gen-wiki-meta 未处理到的漏网）
  for (const m of text.matchAll(/\[[^\]]+\]\(\s*\)/g)) {
    err(file, `空括号死链（未被 gen-wiki-meta 重写）：${m[0]}`);
  }

  // 相对链接
  for (const m of text.matchAll(LINK_RE)) {
    const raw = m[1];
    if (/^(mailto:|repo:\/\/|#)/.test(raw)) continue;
    // blob 永久链接：仓库相对路径须在本地仓库存在（远端可点击性之外的本地一致性）
    if (blobPrefix && raw.includes(blobPrefix)) {
      const bm = raw.match(new RegExp(blobPrefix + '[^/]+/(.+?)(?:#.*)?$'));
      if (bm) {
        const local = resolve(topLevel, decodeURIComponent(bm[1]));
        if (!existsSync(local)) warn(file, `blob 链接路径在本地仓库不存在：${bm[1]}`);
      }
      continue;
    }
    if (/^https?:/.test(raw)) continue; // 其他外部 URL 不校验
    const hashless = decodeURI(raw).split('#')[0];
    if (!hashless) continue; // 纯锚点
    if (hashless.startsWith('/')) {
      if (githubMode) {
        err(file, `残余站点根绝对链接（github 模式下应为 blob 永久链接）：(${raw})`);
        continue;
      }
      let target;
      {
        // 重写产物 "/x" 的基准随历史版本可能为模块根或 git 顶层——多基线探测
        const cands = [];
        if (repoRoot) cands.push(resolve(repoRoot, '.' + hashless));
        let d = dirname(file);
        while (true) { cands.push(resolve(d, '.' + hashless)); if (topLevel && (d === topLevel || dirname(d) === d)) break; d = dirname(d); }
        if (topLevel) cands.push(resolve(topLevel, '.' + hashless));
        target = cands.find((c) => existsSync(c));
        if (!target) target = cands[cands.length - 1];
      }
      const targetMd = target.endsWith('.md') ? target : `${target}.md`;
      if (!existsSync(target) && !existsSync(targetMd)) {
        err(file, `断链：(${raw})`);
      }
      continue;
    }
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

// --- --verify-claims：断言行号真实性抽检（plan 363 A3/N1 机检） ---
const vcArg = optOf('--verify-claims');
if (vcArg) {
  const want = Number(vcArg);
  const seed = Number(optOf('--seed') || 42);
  let repoRoot = optOf('--repo');
  if (!repoRoot) {
    const planFile = join(root, 'PLAN.md');
    if (existsSync(planFile)) {
      const m = readFileSync(planFile, 'utf8').match(/^>\s*Target:\s*(.+?)\s+@\s*\S+/m);
      if (m) repoRoot = resolve(m[1].trim());
    }
  }
  if (!repoRoot) {
    err(root, '--verify-claims 需要目标仓库根（--repo 或 PLAN.md Target 行）');
  } else {
    // 可复现抽样：mulberry32
    let s = seed >>> 0;
    const rand = () => { s |= 0; s = (s + 0x6D2B79F5) | 0; let t = Math.imul(s ^ (s >>> 15), 1 | s); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
    // 收集抽样池：正文断言 `path:起-止行` + 松格式 Sources 条目
    //（行区段支持逗号/顿号多区段——子代理常见写法 path:23-30、43-49）
    const ASSERT_RE = /([A-Za-z0-9_][\w./\\-]*?\.(?:java|ts|js|py|go|rs|xml|md|yml|yaml|json|mjs|c|cpp|h)):(\d+(?:-\d+)?(?:[,、]\s*\d+(?:-\d+)?)*)/g;
    const pool = [];
    for (const file of files) {
      if (relative(root, file) === 'PLAN.md') continue;
      const text = readFileSync(file, 'utf8');
      // 断言池只收正文行：排除 Sources 聚合区（引用非断言）、页首/段末引用行
      //（> 开头）与含 markdown 链接的行（]( 形态）——引用条目的 path:line 不是断言
      const srcIdx = text.search(/^##\s*Sources\b/m);
      const bodyText = srcIdx >= 0 ? text.slice(0, srcIdx) : text;
      bodyText.split('\n').forEach((ln) => {
        if (ln.trimStart().startsWith('>') || ln.includes('](') || ln.trimStart().startsWith('|')) return;
        // 句级上下文：一行常含多个断言，按中英文句读切分，关键词只取所在句
        const sentences = ln.split(/(?<=[。；；！？!?.])/);
        for (const m of ln.matchAll(ASSERT_RE)) {
          const abs = resolveClaim(file, m[1].split('\\').join('/'));
          if (!abs) continue;
          // 找到包含该匹配的句（按累积偏移）
          let acc = 0, sent = ln;
          for (const sn of sentences) {
            if (m.index >= acc && m.index < acc + sn.length) { sent = sn; break; }
            acc += sn.length;
          }
          // 多区段（如 23-30、43-49）：窗口取所有区段的并集（关键词可能落在任一区段）
          const segs = m[2].split(/[,、]\s*/).map((r) => r.split('-').map(Number));
          const start = Math.min(...segs.map((x) => x[0]));
          const end = Math.max(...segs.map((x) => x[x.length - 1]));
          pool.push({ file, relPath: m[1].split('\\').join('/'), abs, start, end, line: sent, fileStem: m[1].split('/').pop().replace(/\.[^.]+$/, '') });
        }
      });
    }
    // 抽样
    const picked = [];
    const idx = pool.map((_, i) => i);
    while (picked.length < Math.min(want, pool.length) && idx.length) {
      picked.push(pool[idx.splice(Math.floor(rand() * idx.length), 1)[0]]);
    }
    let pass = 0, skip = 0;
    for (const claim of picked) {
      let lines;
      try { lines = readFileSync(claim.abs, 'utf8').split('\n'); } catch { err(claim.file, `断言目标不可读：${claim.relPath}`); continue; }
      if (claim.start > lines.length || claim.end > lines.length || claim.start < 1) {
        err(claim.file, `断言行号越界：${claim.relPath}:${claim.start}-${claim.end}（文件仅 ${lines.length} 行）`);
        continue;
      }
      // 关键词来自断言所在行（不是 ±200 字符——那会抓到相邻断言的路径 token）：
      // 反引号 code token 优先，其次 ≥4 字符 ASCII 标识符；排除路径自身 token、
      // 扩展名与文件名词干（类名很少出现在自己文件里）
      // 剥离行内全部路径串（含同行交叉引用的其他断言路径，如"见 X.java:1-9"）
      const lineNoPath = claim.line.replace(/[[\w./\\-]*\.(?:java|ts|js|py|go|rs|xml|md|yml|yaml|json|mjs|c|cpp|h)(:\d+(-\d+)?)?/g, ' ');
      let kws = [...lineNoPath.matchAll(/`([^`]{2,80})`/g)].map((m) => m[1])
        .filter((k) => /^[A-Za-z_][A-Za-z0-9_.]*$/.test(k)); // 须为纯 ASCII 标识符形态（含中文的反引号串是叙述非代码）
      if (!kws.length) kws = [...lineNoPath.matchAll(/[A-Za-z_][A-Za-z0-9_]{3,}/g)].map((m) => m[0]).filter((k) => !['java','type','file','line'].includes(k) && k !== claim.fileStem);
      if (!kws.length) { skip++; continue; }
      // 复合标识符拆子 token（Thing.run → Thing/run），任一命中即 PASS——
      // 源码里通常只出现方法名，不会出现 page 写的复合限定名
      const sub = new Set();
      for (const k of kws) for (const t of k.split(/[^A-Za-z0-9_]+/)) if (t.length >= 3) sub.add(t);
      for (const t of claim.relPath.split(/[^A-Za-z0-9_]+/)) sub.delete(t);
      if (!kws.some((k) => k === claim.fileStem)) sub.delete(claim.fileStem); // fileStem 排除仅限非反引号来源
      // 关键词集合只剩 fileStem 派生 token（如"X 为空接口"类断言）时机检无法区分真伪 → SKIP 交人工
      if (sub.size === 0 || (sub.size === 1 && sub.has(claim.fileStem))) { skip++; continue; }
      const win = lines.slice(Math.max(0, claim.start - 7), Math.min(lines.length, claim.end + 6)).join('\n');
      const winLower = win.toLowerCase();
      if ([...sub].some((k) => winLower.includes(k.toLowerCase()))) pass++;
      else err(claim.file, `断言抽检未命中：${claim.relPath}:${claim.start}-${claim.end} 窗口内无关键词 [${kws.slice(0, 3).join('|')}]`);
    }
    console.log(`verify-claims：抽样 ${picked.length}（池 ${pool.length}），PASS ${pass}，SKIP ${skip}，ERROR ${errors.filter((e) => e.includes('断言')).length}`);
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
