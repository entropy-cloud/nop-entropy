#!/usr/bin/env node
// DeepWiki Phase 5 元数据生成（零依赖，Node 18+）——SKILL.md Phase 5 的配套工具。
// 职责（顺序即 SKILL Phase 5：gen 先行、check 后行）：
//   1. 从 PLAN.md 页面契约表确定性生成 index.md（分组编号目录 + 全站 mindmap + commit 快照行）
//   2. 松格式引用重写：子代理输出的 `Sources: [path:10-40]()` 空括号条目，按
//      --repo/PLAN Target 解析目标文件，确定性重写为页面相对真链接（deepwiki-open
//      content.py 模式：正确性从模型责任改为代码责任）
//   3. 从各页 Sources（重写后）构建 meta/wiki-state.json 内容哈希指纹（非 .java 也收）
// 多次运行结果一致。
//
//   node gen-wiki-meta.mjs <wiki-root> [--plan PLAN.md] [--scope <子路径>]
//                          [--repo <目标仓库根>] [--repo-url <web URL>] [--dry]
//
// --repo 缺省时从 PLAN.md 的 "> Target: <路径> @ <commit>" 行解析。
// --repo-url 缺省时从 git origin remote 推导（gitee/github 等 web URL）；
//   推导成功时源码引用重写为 <repo-url>/blob/<commit>/<path>#L.. 永久链接
//   （deepwiki.com 同款：每条断言可点击跳转源码托管站）；失败时回退页面相对链接。
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

// ---------- 0b. 源码链接基准（GitHub/Gitee blob 永久链接 vs 页面相对） ----------
function webUrlFromRemote(remote) {
  if (!remote) return null;
  let m = remote.match(/^https?:\/\/[^/]+\/(.+?)(?:\.git)?\/?$/);
  if (m) return 'https://' + remote.replace(/^https?:\/\//, '').split('/')[0] + '/' + m[1];
  m = remote.match(/^git@([^:]+):(.+?)(?:\.git)?$/);
  if (m) return `https://${m[1]}/${m[2]}`;
  m = remote.match(/^ssh:\/\/git@([^/]+)\/(.+?)(?:\.git)?$/);
  if (m) return `https://${m[1]}/${m[2]}`;
  return null;
}
let repoUrl = argOf('--repo-url') || null;
let linkMode = 'relative';
if (!repoUrl) {
  try {
    const remote = execSync('git remote get-url origin', { cwd: repoRoot || root }).toString().trim();
    repoUrl = webUrlFromRemote(remote);
  } catch {}
}
if (repoUrl) {
  repoUrl = repoUrl.replace(/\.git\/?$/, '').replace(/\/$/, '');
  linkMode = 'github';
}
// 快照 commit（blob 链接锚定生成时版本，保证行号不随后续代码漂移）
let commit = 'HEAD';
try { commit = execSync('git rev-parse --short HEAD', { cwd: repoRoot || root }).toString().trim(); } catch {}

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

// ---------- 2. 链接重写（空括号松格式 → 真链接；历史 "/repo-rel" 绝对链接 → 当前基准） ----------
// 语法钉死：label = <仓库相对路径>[:<起行>[-<止行>]]；空括号。目标必须可解析（repo 内或 wiki 内页面）。
const LOOSE = /\[([^\[\]]+?)\]\(\s*\)/g;
// 历史重写产物：[label](/repo-rel/path#L20-L29)——旧版站点根绝对链接，本地与 GitHub 上均为死链
const ROOTABS = /\[([^\]]+)\]\((\/[^)\s]+)\)/g;
// 行号后缀：支持逗号/中文顿号分隔的多区段（子代理常见写法 path:17-20、38-58）；容忍尾部分隔符
const LINE_SUFFIX = /:(\d+(?:-\d+)?(?:[,\s、]\s*\d+(?:-\d+)?)*[,\s、]?)$/;
// 作用域后缀扫描：裸文件名/包内相对路径在目标树内递归查后缀匹配文件；
// 优先路径段=wiki 作用域名（wiki 根目录名，如 nop-orm）的命中
const SCOPE = root.split('/').pop().split('\\').pop();
function scanHits(bareName, segKey) {
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
        if (!segKey || norm.endsWith('/' + segKey) || norm.endsWith(segKey)) hits.push(p);
      }
    }
  };
  for (const base of [repoRoot, topLevel]) {
    if (base && existsSync(base)) scan(base);
    if (hits.length) break;
  }
  hits.sort((a, b) => (a.split(/[/\\]/).some((seg) => seg === SCOPE) ? 0 : 1)
    - (b.split(/[/\\]/).some((seg) => seg === SCOPE) ? 0 : 1));
  for (const abs of hits) {
    const relFromTop = relative(topLevel, abs);
    if (relFromTop.startsWith('..')) continue;
    return { abs, relFromTop };
  }
  return null;
}
function resolveTarget(pageFile, label) {
  // 返回 {abs, relFromTop} 或 null。裸文件名先走作用域后缀扫描（防 pom.xml 解析到仓库根），
  // 其余路径走候选链：模块根 → 页面目录各级祖先 → git 顶层；未命中再走多段后缀扫描
  const relPath = label.replace(LINE_SUFFIX, '').trim();
  if (!relPath || /^[a-z]+:/i.test(relPath)) return null; // 空/带 URL scheme 的不是路径
  if (!relPath.includes('/')) {
    const scoped = scanHits(relPath, null);
    if (scoped) return scoped;
  }
  const candidates = [resolve(repoRoot || topLevel, relPath)];
  let dir = dirname(pageFile);
  while (true) {
    candidates.push(resolve(dir, relPath));
    if (dir === topLevel || dirname(dir) === dir) break;
    dir = dirname(dir);
  }
  for (const target of candidates) {
    if (!existsSync(target) || !statSync(target).isFile()) continue;
    const relFromTop = relative(topLevel, target);
    if (relFromTop.startsWith('..')) return null;
    return { abs: target, relFromTop };
  }
  const fallback = scanHits(relPath.split('/').pop(), relPath.includes('/') ? relPath : null);
  return fallback;
}
function formatLink(pageFile, target, label, linePart) {
  const insideWiki = target.abs.startsWith(root);
  if (insideWiki) {
    // wiki 自身页面/PLAN：页面相对链接（互链不入指纹，见第 4 节）
    let rel = relative(dirname(pageFile), target.abs).split('\\').join('/');
    return `[${label}](${rel})`;
  }
  const anchor = linePart ? '#' + (() => {
    // GitHub/Gitee 锚只支持单区段：取第一个区段，label 保留完整行号清单
    const seg = linePart.split(/[,、]\s*/)[0];
    const [a, b] = seg.split('-');
    return b ? `L${a}-L${b}` : `L${a}`;
  })() : '';
  if (linkMode === 'github') {
    return `[${label}](${repoUrl}/blob/${commit}/${target.relFromTop.split('\\').join('/')}${anchor})`;
  }
  // 无远端：页面相对路径 + 锚
  let rel = relative(dirname(pageFile), target.abs).split('\\').join('/');
  return `[${label}](${rel}${anchor})`;
}
function normalizeSourcesLines(text) {
  // 段末引用行归一化：连续多行 "> Sources:" 合并为一行（模板要求一行、多引用用、分隔）
  return text.replace(/(?:^> Sources: .*\n)+/gm, (m0) => {
    const items = m0.split('\n')
      .filter((l) => l.startsWith('> Sources:'))
      .map((l) => l.replace(/^> Sources: ?/, '').trim())
      .filter(Boolean);
    return '> Sources: ' + items.join('、') + '\n';
  });
}
// 行号边界机检：全部区段逐一校验（多区段任一越界即整条降级），越界清单退出时汇总——
// 越界锚在源码托管站上指向不存在的行（deepwiki-open _ground_citations 同类问题；
// 松格式无 snippet 无法重定位，降级为文件级链接是最诚实的处理）
const lineCountCache = new Map();
const degradedAnchors = [];
function anchorOutOfBounds(absFile, linePart) {
  if (!linePart) return false;
  let total = lineCountCache.get(absFile);
  if (total === undefined) {
    try { total = readFileSync(absFile, 'utf8').split('\n').length; } catch { return false; }
    lineCountCache.set(absFile, total);
  }
  return linePart.split(/[,、]\s*/).some((seg) => {
    if (!seg) return false;
    const [a, b] = seg.split('-').map(Number);
    if (!a || a < 1 || a > total) return true;
    if (b !== undefined && (b < a || b > total)) return true;
    return false;
  });
}
let rewritten = 0;
let unresolvedLoose = 0;
let migrated = 0;
if (!dry) {
  for (const p of pages) {
    const f = resolve(root, p.path);
    if (!existsSync(f)) continue;
    let text = readFileSync(f, 'utf8');
    text = normalizeSourcesLines(text);
    let changed = false;
    // a) 迁移历史 "[label](/repo-rel#L..)" 绝对链接（含已被旧版重写过的 Sources 条目）
    text = text.replace(ROOTABS, (whole, label, urlPath) => {
      const [hashless, anchor] = urlPath.split('#');
      const target = resolve(topLevel, '.' + hashless);
      if (!existsSync(target) || !statSync(target).isFile()) return whole;
      migrated++;
      changed = true;
      // 锚 L20-L29 / L20 → 行区段文本 20-29 / 20
      let linePart = anchor ? anchor.replace(/L(\d+)/g, '$1') : '';
      if (anchorOutOfBounds(target, linePart)) {
        degradedAnchors.push({ page: p.path, label, linePart });
        linePart = '';
      }
      return formatLink(f, { abs: target, relFromTop: relative(topLevel, target) }, label, linePart);
    });
    // b) 空括号松格式：path / path:lines → 真链接（GitHub blob 或页面相对）
    text = text.replace(LOOSE, (whole, label) => {
      const lm = label.match(LINE_SUFFIX);
      let linePart = lm ? lm[1] : '';
      const hit = resolveTarget(f, label);
      if (!hit) { unresolvedLoose++; return whole; } // 留给 check-wiki 报残余
      rewritten++;
      changed = true;
      if (anchorOutOfBounds(hit.abs, linePart)) {
        degradedAnchors.push({ page: p.path, label, linePart });
        linePart = '';
      }
      return formatLink(f, hit, label, linePart);
    });
    if (changed) writeFileSync(f, text);
  }
} else {
  // dry：统计存量
  for (const p of pages) {
    const f = resolve(root, p.path);
    if (!existsSync(f)) continue;
    const text = readFileSync(f, 'utf8');
    for (const m of text.matchAll(LOOSE)) {
      if (!resolveTarget(f, m[1])) unresolvedLoose++;
    }
  }
}
// ---------- 3. 确定性生成 index.md（章节 + mindmap + 快照行） ----------
// 章轴：PLAN 契约表"所属章"列优先（deepwiki.com 领域命名章同款——"控制面组件"式章名而非
// 文档类型学），空值回退路径前缀推断；章序 = PLAN 行序首次出现序，mindmap 与条目共用
const PATH_GROUP = (p) => (p.startsWith('modules/') ? '模块' : p.startsWith('flows/') ? '机制' : p.startsWith('topics/') ? '主题' : '指南');
const chapterOf = (p) => (p.section && p.section.trim()) || PATH_GROUP(p.path);
const slug = (p) => relative(root, resolve(root, p)).replace(/\.md$/, '').replace(/[/_-]/g, ' ');
// mindmap 节点文本确定性清洗：ASCII 括号/引号/反引号会破坏语法，替换为安全字符
const cleanNode = (t) => String(t).replace(/[\[\]\(\)\{\}"'`]/g, ' ').replace(/\s+/g, ' ').trim() || '-';

let md = `# ${planTitle} DeepWiki\n\n`;
md += `> 目标：${argOf('--scope') || '全仓库'} · 结构契约：[PLAN.md](./PLAN.md) · 本文件由 gen-wiki-meta.mjs 生成，勿手改\n\n`;
// 全站 mindmap（章 → 页面；章序同下方编号目录）
md += '```mermaid\nmindmap\n';
md += `  root((${cleanNode(planTitle)}))\n`;
const byGroup = {};
const groupSeq = [];
for (const p of pages) {
  const g = chapterOf(p);
  if (!byGroup[g]) { byGroup[g] = []; groupSeq.push(g); }
  byGroup[g].push(p);
}
for (const g of groupSeq) {
  md += `    ${cleanNode(g)}\n`;
  for (const p of byGroup[g]) md += `      ${cleanNode(p.title || slug(p.path))}\n`;
}
md += '```\n';
// 页脚快照
md += `\n> 快照：${argOf('--scope') || ''} @ ${commit} · ${new Date().toISOString().slice(0, 10)}\n`;
// 分组编号目录（deepwiki.com 章节编号同款：组号按 PLAN 行序首现序连续编号，页序号为组内序，
// 跨页叙述可写"见 2.1"）；链接文本 = 页面标题（标题为空回退路径 slug）
let last = '';
let gi = 0;
const inGroup = {};
for (const p of pages) {
  const g = chapterOf(p);
  if (g !== last) { gi++; md += `\n## ${gi}. ${g}\n\n`; last = g; }
  const n = (inGroup[g] = (inGroup[g] || 0) + 1);
  const linkText = cleanNode(p.title) || slug(p.path);
  md += `- ${gi}.${n} [${linkText}](${p.path})\n`;
}
if (!dry) writeFileSync(join(root, 'index.md'), md);
console.log(`index.md：${pages.length} 条目${dry ? '（dry，未写入）' : ' 已写入'}（分组编号目录 + mindmap + 快照行）`);

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
  // blob 永久链接还原：本脚本产物 <repoUrl>/blob/<commit>/<repoRel>#L.. → 本地文件指纹（增量更新依据）
  const blobRe = linkMode === 'github'
    ? new RegExp('^' + repoUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '/blob/[^/]+/(.+?)(?:#.*)?$')
    : null;
  for (const L of sec[0].matchAll(LINK)) {
    const raw = L[1];
    let target = null;
    if (blobRe) {
      const bm = raw.match(blobRe);
      if (bm) {
        const local = resolve(topLevel, decodeURIComponent(bm[1]));
        target = existsSync(local) ? local : null; // 远端删除/本地缺失 → missing 统计
        if (!target) missing++;
      } else if (/^https?:/.test(raw)) {
        continue; // 其他外部 URL（非本仓库 blob 链接）不入指纹
      }
    } else if (/^(https?:|mailto:|repo:\/\/|#)/.test(raw)) {
      continue;
    }
    if (!target) {
      const t = decodeURI(raw).split('#')[0];
      if (!t || /^[a-z]+:/i.test(t)) continue;
      // 真链接：页面相对路径穿越；"/repo-relative" 形式（历史重写产物）从仓库根解析
      target = t.startsWith('/') && topLevel ? resolve(topLevel, '.' + t) : resolve(dirname(f), t);
    }
    const insideWiki = target.startsWith(root);
    if (existsSync(target) && statSync(target).isFile() && (!target.endsWith('.md') || !insideWiki)) {
      fps[relative(topLevel || root, target)] =
        'sha1:' + createHash('sha1').update(readFileSync(target)).digest('hex').slice(0, 16);
    } else if (!target.endsWith('.md') && !/^[a-z]+:/i.test(raw)) {
      missing++;
    }
  }
  pageMap[relative(root, f)] = {
    title: (pages.find((p) => p.path === relative(root, f)) || {}).title || '',
    sourceFiles: Object.keys(fps).sort(),
    fingerprints: fps,
  };
}
const state = {
  version: 2,
  target: { root: dirname(root), commit, scope: argOf('--scope') || '', generatedAt: new Date().toISOString() },
  config: prev.config || { language: 'zh', depth: 'standard' }, // depth 为语义档位（不含页数）
  links: linkMode === 'github' ? { mode: 'github', repoUrl } : { mode: 'relative' },
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
console.log(`链接基准：${linkMode === 'github' ? `源码托管 blob 永久链接（${repoUrl} @ ${commit}）` : '页面相对链接（未发现 git origin 远端，可用 --repo-url 显式指定）'}`);
if (migrated > 0) console.log(`历史绝对链接迁移：${migrated} 条 "/repo-rel" → 当前基准`);
if (rewritten > 0) console.log(`松格式重写：${rewritten} 条 → 真链接`);
if (degradedAnchors.length > 0) {
  console.warn(`行号越界降级：${degradedAnchors.length} 条（锚越界，已降级为文件级链接）`);
  for (const d of degradedAnchors) console.warn(`  - ${d.page}: [${d.label}] 原行号 ${d.linePart}`);
}
if (unresolvedLoose > 0) console.warn(`注意：${unresolvedLoose} 条松格式引用无法解析（缺 --repo/PLAN Target 行，或路径不存在）——check-wiki 会报残余 ERROR。`);
