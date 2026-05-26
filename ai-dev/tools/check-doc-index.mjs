#!/usr/bin/env node

import { readFileSync, writeFileSync, existsSync, statSync, readdirSync, mkdirSync } from 'node:fs';
import { join, resolve, relative, dirname, basename, normalize } from 'node:path';

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..');
const TMP_DIR = join(PROJECT_ROOT, '_tmp');
const DOC_DIRS = ['docs-for-ai', 'ai-dev'];

function toPosix(p) { return p.split(/\\/).join('/'); }

const MD_LINK_RE = /\[([^\]]*)\]\(([^)]+)\)/g;
const BACKTICK_PATH_RE = /`([a-zA-Z0-9_][a-zA-Z0-9_./\-]*\.(?:md|xml|json|yaml|yml|java|txt|sh|cmd|mjs|js))`/g;
const BACKTICK_DIR_RE = /`((?:docs-for-ai|ai-dev)\/[a-zA-Z0-9_.\/\-]*[a-zA-Z0-9_\/])`/g;
const URL_RE = /^(?:https?|mailto|ftp):/;
const HEADING_ANCHOR_RE = /#[a-z0-9-]+$/;

function collectMdFiles(dir) {
  const results = [];
  if (!existsSync(dir)) return results;
  function walk(d) {
    for (const entry of readdirSync(d, { withFileTypes: true })) {
      const full = join(d, entry.name);
      if (entry.name === 'node_modules' || entry.name === '.git') continue;
      if (entry.isDirectory()) walk(full);
      else if (entry.isFile() && entry.name.endsWith('.md')) results.push(full);
    }
  }
  walk(dir);
  return results;
}

function collectAllFiles(dir) {
  const results = [];
  if (!existsSync(dir)) return results;
  function walk(d) {
    for (const entry of readdirSync(d, { withFileTypes: true })) {
      const full = join(d, entry.name);
      if (entry.name === 'node_modules' || entry.name === '.git') continue;
      if (entry.isDirectory()) walk(full);
      else if (entry.isFile()) results.push(full);
    }
  }
  walk(dir);
  return results;
}

function extractDocRefs(content) {
  const refs = new Set();
  let m;
  MD_LINK_RE.lastIndex = 0;
  while ((m = MD_LINK_RE.exec(content)) !== null) {
    let target = m[2].trim().replace(HEADING_ANCHOR_RE, '');
    if (!target || URL_RE.test(target)) continue;
    refs.add(target);
  }
  BACKTICK_PATH_RE.lastIndex = 0;
  while ((m = BACKTICK_PATH_RE.exec(content)) !== null) {
    if (URL_RE.test(m[1])) continue;
    refs.add(m[1]);
  }
  BACKTICK_DIR_RE.lastIndex = 0;
  while ((m = BACKTICK_DIR_RE.exec(content)) !== null) {
    if (!m[1].match(/\.[a-z]{1,4}$/)) refs.add(m[1]);
    else refs.add(m[1]);
  }
  return [...refs];
}

function resolveRelPath(refRaw, sourceFilePath, sourceType) {
  const sourceDir = dirname(sourceFilePath);
  if (refRaw.startsWith('./') || refRaw.startsWith('../')) {
    return toPosix(normalize(join(sourceDir, refRaw)));
  }
  if (refRaw.startsWith('docs-for-ai/') || refRaw.startsWith('ai-dev/')) {
    return toPosix(normalize(join(PROJECT_ROOT, refRaw)));
  }
  if (refRaw.includes('/')) {
    const candidates = [
      toPosix(normalize(join(PROJECT_ROOT, refRaw))),
    ];
    if (sourceType === 'docs-for-ai') {
      candidates.push(toPosix(normalize(join(PROJECT_ROOT, 'docs-for-ai', refRaw))));
    }
    if (sourceType === 'ai-dev') {
      candidates.push(toPosix(normalize(join(PROJECT_ROOT, 'ai-dev', refRaw))));
    }
    candidates.push(toPosix(normalize(join(sourceDir, refRaw))));
    for (const c of candidates) {
      if (existsSync(c)) return c;
    }
    return candidates[0];
  }
  const c1 = toPosix(normalize(join(sourceDir, refRaw)));
  if (existsSync(c1)) return c1;
  const c2 = toPosix(normalize(join(PROJECT_ROOT, refRaw)));
  if (existsSync(c2)) return c2;
  return c1;
}

function parseIndexEntries(indexPath) {
  const content = readFileSync(indexPath, 'utf-8');
  const entries = [];
  for (const line of content.split('\n')) {
    const tdMatch = line.match(/\|\s*`([^`]+)`\s*\|/g);
    if (tdMatch) {
      for (const td of tdMatch) {
        const inner = td.match(/`([^`]+)`/);
        if (inner) {
          const path = inner[1];
          if (path.match(/\.(md|mjs|js|sh|java|xml|yaml|yml)$/)) {
            entries.push(path);
          }
        }
      }
    }
    const linkMatch = line.match(/\[([^\]]*)\]\(([^)]+)\)/g);
    if (linkMatch) {
      for (const lm of linkMatch) {
        const inner = lm.match(/\(([^)]+)\)/);
        if (inner) {
          let target = inner[1].trim().replace(HEADING_ANCHOR_RE, '');
          if (!target || URL_RE.test(target)) continue;
          if (target.match(/\.(md|mjs|js|sh|java|xml|yaml|yml)$/)) {
            entries.push(target);
          }
        }
      }
    }
  }
  return [...new Set(entries)];
}

function parseRouteTable(indexPath) {
  const content = readFileSync(indexPath, 'utf-8');
  const routes = [];
  const lines = content.split('\n');
  for (const line of lines) {
    const cells = line.split('|').map(c => c.trim()).filter(c => c);
    if (cells.length >= 2) {
      const task = cells[0].replace(/\*\*/g, '');
      const doc = cells[1].replace(/`/g, '');
      if (doc.match(/\.(md|mjs|js|sh|java|xml|yaml|yml)$/) && !doc.match(/^---/) && !doc.match(/^用途/)) {
        routes.push({ task, doc, line: lines.indexOf(line) + 1 });
      }
    }
  }
  return routes;
}

function normalizePath(p, sourceDir, sourceType) {
  if (p.startsWith('./') || p.startsWith('../')) {
    return toPosix(relative(PROJECT_ROOT, normalize(join(sourceDir, p))));
  }
  if (p.startsWith('docs-for-ai/') || p.startsWith('ai-dev/')) return p;
  // For sub-indexes: bare filenames or subdir-relative paths
  // Try relative to source file's directory first
  const fromSource = toPosix(normalize(join(sourceDir, p)));
  if (existsSync(fromSource)) {
    return toPosix(relative(PROJECT_ROOT, fromSource));
  }
  // Try relative to the doc root
  if (sourceType === 'docs-for-ai') {
    const fromRoot = toPosix(normalize(join(PROJECT_ROOT, 'docs-for-ai', p)));
    if (existsSync(fromRoot)) return toPosix(relative(PROJECT_ROOT, fromRoot));
  }
  if (sourceType === 'ai-dev') {
    const fromRoot = toPosix(normalize(join(PROJECT_ROOT, 'ai-dev', p)));
    if (existsSync(fromRoot)) return toPosix(relative(PROJECT_ROOT, fromRoot));
  }
  return toPosix(relative(PROJECT_ROOT, fromSource));
}

function findIndexFiles() {
  const indices = [];
  for (const dir of DOC_DIRS) {
    const root = join(PROJECT_ROOT, dir);
    if (!existsSync(root)) continue;
    const rootIndex = join(root, 'INDEX.md');
    if (existsSync(rootIndex)) indices.push({ path: rootIndex, type: 'top-level', dir });
    const readme = join(root, 'README.md');
    if (existsSync(readme)) indices.push({ path: readme, type: 'top-level', dir });

    function walk(d) {
      for (const entry of readdirSync(d, { withFileTypes: true })) {
        if (entry.name === 'node_modules' || entry.name === '.git') continue;
        const full = join(d, entry.name);
        if (entry.isDirectory()) {
          const subReadme = join(full, 'README.md');
          if (existsSync(subReadme)) {
            indices.push({ path: subReadme, type: 'sub-index', dir });
          }
          walk(full);
        }
      }
    }
    walk(root);
  }
  return indices;
}

function gatherExistingFiles() {
  const files = new Map();
  for (const dir of DOC_DIRS) {
    const root = join(PROJECT_ROOT, dir);
    if (!existsSync(root)) continue;
    const mdFiles = collectMdFiles(root);
    for (const f of mdFiles) {
      const rel = toPosix(relative(PROJECT_ROOT, f));
      files.set(rel, f);
    }
  }
  return files;
}

function findDirectoryWithoutIndex(dir) {
  const results = [];
  if (!existsSync(dir)) return results;

  function walk(d) {
    const entries = readdirSync(d, { withFileTypes: true });
    const mdFiles = entries.filter(e => e.isFile() && e.name.endsWith('.md') && e.name !== 'README.md');
    const hasReadme = entries.some(e => e.isFile() && e.name === 'README.md');

    if (mdFiles.length >= 8 && !hasReadme) {
      results.push(toPosix(relative(PROJECT_ROOT, d)));
    }

    for (const entry of entries) {
      if (entry.name === 'node_modules' || entry.name === '.git') continue;
      const full = join(d, entry.name);
      if (entry.isDirectory()) walk(full);
    }
  }
  walk(dir);
  return results;
}

function checkOrphans(existingFiles, indexFiles) {
  const orphans = [];
  const indexedSet = new Set();

  for (const idx of indexFiles) {
    const entries = parseIndexEntries(idx.path);
    const sourceDir = dirname(idx.path);
    const sourceRel = toPosix(relative(PROJECT_ROOT, idx.path));
    const sourceType = sourceRel.startsWith('docs-for-ai/') ? 'docs-for-ai'
      : sourceRel.startsWith('ai-dev/') ? 'ai-dev' : 'other';

    for (const entry of entries) {
      const normalized = normalizePath(entry, sourceDir, sourceType);
      indexedSet.add(normalized);
    }
  }

  for (const [rel] of existingFiles) {
    if (rel.match(/\/00-.*-guide\.md$/)) continue;
    if (rel.match(/README\.md$/)) continue;
    if (rel.endsWith('INDEX.md')) continue;

    let found = false;
    for (const idx of indexedSet) {
      if (rel === idx || rel.endsWith('/' + idx) || idx.endsWith('/' + basename(rel))) {
        found = true;
        break;
      }
    }
    if (!found) {
      orphans.push(rel);
    }
  }
  return orphans;
}

function findDuplicatedRules() {
  const RULE_PATTERNS = [
    { pattern: /先模型.*(?:再|然后).*Delta.*(?:最后|然后).*Java/, label: '先模型再Delta最后Java' },
    { pattern: /不要修改.*_gen/, label: '不要修改生成物' },
    { pattern: /CrudBizModel/, label: 'CrudBizModel默认基类' },
    { pattern: /requireEntity\(\).*doFindList\(\).*doFindPage\(\)/, label: '安全查询API' },
    { pattern: /@BizMutation.*(?:自动|已自动).*事务/, label: '@BizMutation自动事务' },
    { pattern: /@Inject.*(?:不支持|不能).*private/, label: '@Inject不支持private' },
  ];

  const occurrences = [];
  for (const dir of DOC_DIRS) {
    const root = join(PROJECT_ROOT, dir);
    const mdFiles = collectMdFiles(root);
    for (const f of mdFiles) {
      const content = readFileSync(f, 'utf-8');
      const rel = toPosix(relative(PROJECT_ROOT, f));
      for (const { pattern, label } of RULE_PATTERNS) {
        if (pattern.test(content)) {
          occurrences.push({ rule: label, file: rel });
        }
      }
    }
  }

  const grouped = {};
  for (const o of occurrences) {
    if (!grouped[o.rule]) grouped[o.rule] = [];
    grouped[o.rule].push(o.file);
  }

  return Object.entries(grouped)
    .filter(([, files]) => files.length > 1)
    .map(([rule, files]) => ({ rule, files }));
}

function checkIndexSyncWithFiles() {
  const issues = [];
  const docsForAiRoot = join(PROJECT_ROOT, 'docs-for-ai');

  const indexFile = join(docsForAiRoot, 'INDEX.md');
  if (!existsSync(indexFile)) return issues;

  const indexContent = readFileSync(indexFile, 'utf-8');

  const defaultsFile = join(docsForAiRoot, '00-start-here', 'ai-defaults.md');
  if (existsSync(defaultsFile)) {
    const defaultsContent = readFileSync(defaultsFile, 'utf-8');

    const indexOrder = indexContent.match(/##\s*推荐查找顺序[\s\S]*?(?=##|$)/);
    const defaultsOrder = defaultsContent.match(/##\s*默认查找顺序[\s\S]*?(?=##|$)/);

    if (indexOrder && defaultsOrder) {
      const indexSteps = indexOrder[0].match(/\d+\.\s+.+/g) || [];
      const defaultsSteps = defaultsOrder[0].match(/\d+\.\s+.+/g) || [];

      const extractFileRefs = (steps) => steps.map(s => {
        const matches = s.match(/`([^`]+)`/g);
        return matches ? matches.map(m => m.replace(/`/g, '')) : [];
      }).flat();

      const indexRefs = extractFileRefs(indexSteps);
      const defaultsRefs = extractFileRefs(defaultsSteps);

      if (JSON.stringify(indexRefs) !== JSON.stringify(defaultsRefs)) {
        issues.push({
          severity: 'auto-fix',
          rule: 'INDEX_ORDER_MISMATCH',
          file: 'docs-for-ai/00-start-here/ai-defaults.md',
          message: 'ai-defaults.md 默认查找顺序与 INDEX.md 推荐查找顺序不一致',
          indexRefs,
          defaultsRefs,
        });
      }
    }

    const indexDefaultRules = indexContent.match(/##\s*默认规则[\s\S]*?(?=##|$)/);
    const defaultsDefaultRules = defaultsContent.match(/##\s*当前仓库的硬规则[\s\S]*?(?=##|$)/);

    if (indexDefaultRules && defaultsDefaultRules) {
      const indexRules = (indexDefaultRules[0].match(/^- .+$/gm) || []).sort();
      const defaultsRules = (defaultsDefaultRules[0].match(/^\|[^|]+\|[^|]+\|$/gm) || []).sort();

      if (indexRules.length > 0 && defaultsRules.length > 0) {
        issues.push({
          severity: 'warning',
          rule: 'RULE_DUPLICATION',
          files: ['docs-for-ai/INDEX.md', 'docs-for-ai/00-start-here/ai-defaults.md'],
          message: `默认规则在 INDEX.md 和 ai-defaults.md 中重复定义（INDEX ${indexRules.length} 条，ai-defaults ${defaultsRules.length} 条）`,
        });
      }
    }
  }

  const runbooksReadme = join(docsForAiRoot, '03-runbooks', 'README.md');
  if (existsSync(runbooksReadme)) {
    const runbookContent = readFileSync(runbooksReadme, 'utf-8');
    const runbookRules = runbookContent.match(/##\s*默认规则[\s\S]*?(?=##|$)/);
    if (runbookRules) {
      issues.push({
        severity: 'warning',
        rule: 'RULE_DUPLICATION',
        files: ['docs-for-ai/INDEX.md', 'docs-for-ai/03-runbooks/README.md'],
        message: '默认规则在 INDEX.md 和 03-runbooks/README.md 中重复定义',
      });
    }
  }

  return issues;
}

function checkLogIndexSync() {
  const issues = [];
  const logsDir = join(PROJECT_ROOT, 'ai-dev', 'logs');
  const yearDir = join(logsDir, '2026');
  const indexFile = join(logsDir, 'index.md');

  if (!existsSync(yearDir) || !existsSync(indexFile)) return issues;

  const actualFiles = readdirSync(yearDir)
    .filter(f => f.endsWith('.md'))
    .map(f => f.replace('.md', ''))
    .sort();

  const indexContent = readFileSync(indexFile, 'utf-8');
  const indexTreeFiles = [];
  const treeMatch = indexContent.match(/```text[\s\S]*?```/);
  if (treeMatch) {
    const lines = treeMatch[0].split('\n');
    for (const line of lines) {
      const m = line.match(/^\s*├──\s*(\d{2}-\d{2}\.md)\s*$/);
      const m2 = line.match(/^\s*└──\s*(\d{2}-\d{2}\.md)\s*$/);
      const file = (m || m2)?.[1]?.replace('.md', '');
      if (file) indexTreeFiles.push(file);
    }
  }

  const indexLinkedFiles = [];
  const linkRe = /\[(\d{2}-\d{2})\]/g;
  let lm;
  while ((lm = linkRe.exec(indexContent)) !== null) {
    indexLinkedFiles.push(lm[1]);
  }

  const missingFromTree = actualFiles.filter(f => !indexTreeFiles.includes(f));
  const missingFromIndex = actualFiles.filter(f => !indexLinkedFiles.includes(f));

  for (const f of missingFromTree) {
    issues.push({
      severity: 'auto-fix',
      rule: 'LOG_TREE_STALE',
      file: 'ai-dev/logs/index.md',
      message: `日志文件 2026/${f}.md 存在但目录树中未列出`,
      detail: f,
    });
  }

  for (const f of missingFromIndex) {
    issues.push({
      severity: 'auto-fix',
      rule: 'LOG_INDEX_STALE',
      file: 'ai-dev/logs/index.md',
      message: `日志文件 2026/${f}.md 存在但索引链接中未列出`,
      detail: f,
    });
  }

  return issues;
}

function fixLogIndex(missing) {
  const logsDir = join(PROJECT_ROOT, 'ai-dev', 'logs');
  const indexFile = join(logsDir, 'index.md');
  const yearDir = join(logsDir, '2026');

  const actualFiles = readdirSync(yearDir)
    .filter(f => f.endsWith('.md'))
    .map(f => f.replace('.md', ''))
    .sort()
    .reverse();

  let content = readFileSync(indexFile, 'utf-8');

  const treeMissing = missing.filter(m => m.rule === 'LOG_TREE_STALE').map(m => m.detail);
  if (treeMissing.length > 0) {
    const treeBlock = content.match(/```text\n([\s\S]*?)```/);
    if (treeBlock) {
      const indent = '    ';
      const treeLines = actualFiles.map((f, i) => {
        const connector = i === actualFiles.length - 1 ? '└──' : '├──';
        return `${indent}${connector} ${f}.md`;
      });
      const newTree = '```text\nai-dev/logs/\n├── index.md\n├── 00-log-writing-guide.md\n└── 2026/\n' + treeLines.join('\n') + '\n```';
      content = content.replace(/```text\n[\s\S]*?```/, newTree);
    }
  }

  const indexMissing = missing.filter(m => m.rule === 'LOG_INDEX_STALE').map(m => m.detail);
  if (indexMissing.length > 0) {
    const monthHeader = '### 2026-05';
    const sectionMatch = content.match(new RegExp(
      '(' + monthHeader.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?)(?=###|##|$)'
    ));

    if (sectionMatch) {
      const section = sectionMatch[1];
      const existingLinks = new Set();
      const linkRe = /- \[(\d{2}-\d{2})\]/g;
      let lm;
      while ((lm = linkRe.exec(section)) !== null) {
        existingLinks.add(lm[1]);
      }

      const mayFiles = actualFiles.filter(f => f.startsWith('05'));
      const newEntries = mayFiles
        .filter(f => !existingLinks.has(f))
        .map(f => `- [${f}](2026/${f}.md) — `);

      if (newEntries.length > 0) {
        const insertAfter = section.indexOf('\n') + 1;
        const updatedSection = section.slice(0, insertAfter) + newEntries.join('\n') + '\n' + section.slice(insertAfter);
        content = content.replace(sectionMatch[0], updatedSection);
      }
    }
  }

  writeFileSync(indexFile, content, 'utf-8');
}

function fixDefaultsOrder(indexRefs) {
  const defaultsFile = join(PROJECT_ROOT, 'docs-for-ai', '00-start-here', 'ai-defaults.md');
  if (!existsSync(defaultsFile)) return;

  let content = readFileSync(defaultsFile, 'utf-8');

  const orderSection = content.match(/##\s*默认查找顺序[\s\S]*?(?=##|$)/);
  if (!orderSection) return;

  const indexFile = join(PROJECT_ROOT, 'docs-for-ai', 'INDEX.md');
  const indexContent = readFileSync(indexFile, 'utf-8');
  const indexOrderSection = indexContent.match(/##\s*推荐查找顺序[\s\S]*?(?=##|$)/);
  if (!indexOrderSection) return;

  const newOrderSection = orderSection[0].replace(
    /(\d+\.\s+[^\n]+\n?)+/,
    indexOrderSection[0]
      .replace(/##\s*推荐查找顺序\n*/, '')
      .replace(/^\d+\.\s+先看本页\n?/m, '')
      .replace(/\n+/g, '\n')
      .split('\n')
      .filter(l => l.trim())
      .map((line, i) => `${i + 1}. ${line.replace(/^\d+\.\s*/, '')}`)
      .join('\n') + '\n'
  );

  content = content.replace(orderSection[0], '## 默认查找顺序\n' + newOrderSection.replace(/##\s*默认查找顺序\n*/, ''));
  writeFileSync(defaultsFile, content, 'utf-8');
}

function generateReport(allIssues, fixes) {
  const bySeverity = { 'error': [], 'warning': [], 'info': [], 'auto-fix': [] };
  for (const issue of allIssues) {
    const sev = issue.severity || 'info';
    if (!bySeverity[sev]) bySeverity[sev] = [];
    bySeverity[sev].push(issue);
  }

  let h = '';
  h += `# Doc Index Audit Report\n\n`;
  h += `- **Timestamp**: ${new Date().toISOString()}\n`;
  h += `- **Total findings**: ${allIssues.length}\n`;
  h += `- **Auto-fixed**: ${fixes.length}\n\n`;

  for (const sev of ['error', 'auto-fix', 'warning', 'info']) {
    const items = bySeverity[sev];
    if (!items || items.length === 0) continue;

    const label = sev === 'auto-fix' ? 'Auto-Fixed' : sev.charAt(0).toUpperCase() + sev.slice(1) + 's';
    h += `## ${label} (${items.length})\n\n`;
    for (const item of items) {
      const file = item.file || (item.files || []).join(', ');
      h += `- **[${item.rule}]** \`${file}\`\n`;
      h += `  > ${item.message}\n`;
      if (item.detail) h += `  > Detail: ${item.detail}\n`;
    }
    h += '\n';
  }

  return h;
}

function main() {
  const args = process.argv.slice(2);
  const fixMode = args.includes('--fix');
  const strictMode = args.includes('--strict');
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');

  console.log('=== Doc Index Audit ===\n');
  console.log(`Project root: ${PROJECT_ROOT}`);
  console.log(`Fix mode: ${fixMode ? 'ON' : 'OFF'}\n`);

  const allIssues = [];
  const fixes = [];

  // 1. Broken links from index entries
  console.log('1. Checking index entry links...');
  const indexFiles = findIndexFiles();
  for (const idx of indexFiles) {
    const entries = parseIndexEntries(idx.path);
    const sourceDir = dirname(idx.path);
    const sourceRel = toPosix(relative(PROJECT_ROOT, idx.path));
    const sourceType = sourceRel.startsWith('docs-for-ai/') ? 'docs-for-ai'
      : sourceRel.startsWith('ai-dev/') ? 'ai-dev' : 'other';

    for (const entry of entries) {
      const normalized = normalizePath(entry, sourceDir, sourceType);
      const absPath = normalized.includes(':') || normalized.startsWith('/')
        ? normalized
        : join(PROJECT_ROOT, normalized);
      if (!existsSync(absPath)) {
        allIssues.push({
          severity: 'error',
          rule: 'BROKEN_INDEX_LINK',
          file: sourceRel,
          message: `索引条目 \`${entry}\` 指向的文件不存在 (解析为 ${normalized})`,
          detail: entry,
        });
      }
    }
  }

  // 2. Orphan files
  console.log('2. Checking for orphan files...');
  const existingFiles = gatherExistingFiles();
  const orphans = checkOrphans(existingFiles, indexFiles);
  for (const orphan of orphans) {
    allIssues.push({
      severity: 'warning',
      rule: 'ORPHAN_FILE',
      file: orphan,
      message: `文件未被任何索引引用`,
    });
  }

  // 3. Directories without README exceeding threshold
  console.log('3. Checking directories without index...');
  for (const dir of DOC_DIRS) {
    const noIndex = findDirectoryWithoutIndex(join(PROJECT_ROOT, dir));
    for (const d of noIndex) {
      allIssues.push({
        severity: 'warning',
        rule: 'MISSING_SUB_INDEX',
        file: d,
        message: `目录下有 >=8 个 .md 文件但缺少 README.md`,
      });
    }
  }

  // 4. Duplicated rules
  console.log('4. Checking for duplicated rules...');
  const dups = findDuplicatedRules();
  for (const dup of dups) {
    allIssues.push({
      severity: 'warning',
      rule: 'DUPLICATED_RULE',
      files: dup.files,
      file: dup.files.join(', '),
      message: `规则"${dup.rule}"在 ${dup.files.length} 个文件中重复: ${dup.files.join(', ')}`,
    });
  }

  // 5. Index sync between INDEX.md and ai-defaults.md
  console.log('5. Checking index sync...');
  const syncIssues = checkIndexSyncWithFiles();
  allIssues.push(...syncIssues);

  // 6. Log index sync
  console.log('6. Checking log index sync...');
  const logIssues = checkLogIndexSync();
  allIssues.push(...logIssues);

  // Auto-fix
  if (fixMode) {
    console.log('\n--- Auto-fixing ---\n');

    const logFixes = logIssues.filter(i => i.severity === 'auto-fix');
    if (logFixes.length > 0) {
      console.log(`Fixing log index (${logFixes.length} items)...`);
      fixLogIndex(logFixes);
      fixes.push(...logFixes);
    }

    const orderFixes = syncIssues.filter(i => i.severity === 'auto-fix' && i.rule === 'INDEX_ORDER_MISMATCH');
    if (orderFixes.length > 0) {
      for (const fix of orderFixes) {
        console.log(`Fixing ${fix.file}: syncing lookup order with INDEX.md...`);
        fixDefaultsOrder(fix.indexRefs);
        fixes.push(fix);
      }
    }
  }

  // Generate report
  const report = generateReport(allIssues, fixes);
  mkdirSync(TMP_DIR, { recursive: true });
  const reportPath = join(TMP_DIR, `doc-index-audit-${timestamp}.md`);
  writeFileSync(reportPath, report, 'utf-8');

  console.log('\n=== Report ===\n');
  console.log(report);
  console.log(`Report saved to: ${reportPath}`);

  const errorCount = allIssues.filter(i => i.severity === 'error').length;
  if (errorCount > 0) {
    console.error(`\n${errorCount} error(s) found.`);
    if (strictMode) process.exit(1);
  } else {
    console.log('\nNo errors found.');
  }
}

main();
