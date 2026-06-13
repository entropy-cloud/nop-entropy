#!/usr/bin/env node

import { readFileSync, existsSync, statSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, resolve, relative, dirname, normalize } from 'node:path';

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..');
const TMP_DIR = join(PROJECT_ROOT, '_tmp');
const DOC_DIRS = ['docs-for-ai', 'ai-dev'];
const ROOT_MD_FILES = ['AGENTS.md', 'README.md'];

const TOP_LEVEL_DIRS = new Set();
for (const entry of readdirSync(PROJECT_ROOT, { withFileTypes: true })) {
  if (entry.isDirectory() && !entry.name.startsWith('.')) {
    TOP_LEVEL_DIRS.add(entry.name);
  }
}

const MODULE_PREFIX_RE = new RegExp(
  '^(' + [...TOP_LEVEL_DIRS].sort((a, b) => b.length - a.length).join('|') + ')/'
);

const MD_LINK_RE = /\[([^\]]*)\]\(([^)]+)\)/g;
const BACKTICK_PATH_RE = /`([a-zA-Z0-9_][a-zA-Z0-9_./\-]*\.(?:md|xml|json|yaml|yml|java|txt|sh|cmd|mjs|js|properties|sql|graphql|g4|xlsx|xls|csv|ts|css|scss|html|kt|groovy|gradle|toml|py))`/g;
const HEADING_ANCHOR_RE = /#[a-z0-9-]+$/;
const URL_RE = /^(?:https?|mailto|ftp):/;
const ELLIPSIS_SEGMENT_RE = /\/\.\.\.(?:\/|$)/;

const BACKTICK_DIR_RE = new RegExp(
  '`((' + [...TOP_LEVEL_DIRS].sort((a, b) => b.length - a.length).join('|') + ')/[a-zA-Z0-9_./\\-]*[a-zA-Z0-9_/])`', 'g'
);

const PLACEHOLDER_RE = /^(?:Xxx[A-Z]|XXX|_Xxx|xx[A-Z]|XX[A-Z])/;
const GENERIC_XML_RE = /^_?(?:app|service|dao)\.(?:orm|beans)\.xml$|^_gen\/|^_Xxx\./;
const TEMPLATE_PATH_RE = /\/XX-|\/xxx[/.]/;

const DOC_FOR_AI_ROOT = join(PROJECT_ROOT, 'docs-for-ai');
const AI_DEV_ROOT = join(PROJECT_ROOT, 'ai-dev');

const SKIP_FILES = new Set([
  'ai-dev/plans/50-doc-link-check-fix-all.md',
]);

let filenameSearchCache = null;
function buildFilenameCache(rootDir) {
  if (filenameSearchCache) return filenameSearchCache;
  filenameSearchCache = new Map();
  function walk(dir) {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (entry.name === 'node_modules' || entry.name === '.git') continue;
      const full = join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else {
        const existing = filenameSearchCache.get(entry.name);
        if (existing) existing.push(full);
        else filenameSearchCache.set(entry.name, [full]);
      }
    }
  }
  walk(rootDir);
  return filenameSearchCache;
}

function collectMdFiles(dir) {
  const results = [];
  function walk(d) {
    for (const entry of readdirSync(d, { withFileTypes: true })) {
      if (entry.isDirectory()) {
        if (entry.name === 'node_modules' || entry.name === '.git') continue;
        walk(join(d, entry.name));
      } else if (entry.isFile() && entry.name.endsWith('.md')) {
        results.push(join(d, entry.name));
      }
    }
  }
  walk(dir);
  return results;
}

function toPosix(p) {
  return p.split(/\\/).join('/');
}

const SKIP_TARGETS = new Set([
  // 并称两种文件类型，不是单一文件路径
  'view.xml/page.yaml',
  // 文档模板中的占位路径
  'docs-for-ai/XX.md',
  // app-mall 外部项目的生成文件（不在本仓库）
  '_gen/_LitemallGoods.view.xml',
]);

// 前缀匹配：外部项目，不在本仓库
const SKIP_PREFIXES = [
  'app-mall-',
  'nop-app-mall/',
  'nop-chaos-flux/',
  'nop-chaos/',
];

const SKIP_PATTERNS = [
  /^src\/(?:main|test)\/(?:java|resources|kotlin)(?:\/|$)/,
  /^docs\/plans(?:\/|$)/,
  /^_tmp(?:\/|$)/,
];

const SKIP_MD_LINK_TARGETS = new Set([
  'path',
]);

function shouldSkip(rawTarget, ext) {
  if (ELLIPSIS_SEGMENT_RE.test(rawTarget)) return true;
  if (TEMPLATE_PATH_RE.test(rawTarget)) return true;

  const basename = rawTarget.split('/').pop();
  if (PLACEHOLDER_RE.test(basename)) return true;

  if (SKIP_TARGETS.has(rawTarget)) return true;

  for (const prefix of SKIP_PREFIXES) {
    if (rawTarget.startsWith(prefix)) return true;
  }

  for (const pat of SKIP_PATTERNS) {
    if (pat.test(rawTarget)) return true;
  }

  // Bare names (no /) are type/name mentions, not path references
  // Exception: .md bare names are doc cross-references that should be checked
  if (!rawTarget.includes('/') && ext !== 'md' && ext !== '') return true;

  return false;
}

function extractReferences(content) {
  const refs = [];
  const seen = new Set();

  function addRef(rawTarget, type, line) {
    const key = `${type}:${rawTarget}:${line}`;
    if (seen.has(key)) return;
    seen.add(key);

    const ext = rawTarget.match(/\.([a-z]+)$/)?.[1] || '';
    if (shouldSkip(rawTarget, ext)) return;

    refs.push({ rawTarget, type, line, ext });
  }

  let m;
  MD_LINK_RE.lastIndex = 0;
  while ((m = MD_LINK_RE.exec(content)) !== null) {
    let target = m[2].trim();
    target = target.replace(HEADING_ANCHOR_RE, '');
    if (!target || URL_RE.test(target)) continue;
    if (SKIP_MD_LINK_TARGETS.has(target)) continue;
    const line = content.substring(0, m.index).split('\n').length;
    addRef(target, 'md-link', line);
  }

  BACKTICK_PATH_RE.lastIndex = 0;
  while ((m = BACKTICK_PATH_RE.exec(content)) !== null) {
    const target = m[1];
    if (URL_RE.test(target)) continue;
    const line = content.substring(0, m.index).split('\n').length;
    addRef(target, 'backtick-path', line);
  }

  BACKTICK_DIR_RE.lastIndex = 0;
  while ((m = BACKTICK_DIR_RE.exec(content)) !== null) {
    const target = m[1];
    const line = content.substring(0, m.index).split('\n').length;
    if (!target.match(/\.[a-z]{1,4}$/)) {
      addRef(target, 'backtick-dir', line);
    } else {
      addRef(target, 'backtick-path', line);
    }
  }

  return refs;
}

function resolveReference(refRaw, sourceFilePath, sourceType) {
  const sourceDir = dirname(sourceFilePath);

  if (refRaw.startsWith('./') || refRaw.startsWith('../')) {
    const abs = toPosix(normalize(join(sourceDir, refRaw)));
    return { resolvedAbsolute: abs, resolvedRelativeToRoot: toPosix(relative(PROJECT_ROOT, abs)), method: 'relative-to-file' };
  }

  if (refRaw.startsWith('docs-for-ai/') || refRaw.startsWith('ai-dev/')) {
    const abs = toPosix(normalize(join(PROJECT_ROOT, refRaw)));
    return { resolvedAbsolute: abs, resolvedRelativeToRoot: toPosix(relative(PROJECT_ROOT, abs)), method: 'project-root-relative' };
  }

  if (MODULE_PREFIX_RE.test(refRaw)) {
    const abs = toPosix(normalize(join(PROJECT_ROOT, refRaw)));
    if (existsSync(abs)) {
      return { resolvedAbsolute: abs, resolvedRelativeToRoot: toPosix(relative(PROJECT_ROOT, abs)), method: 'project-root-relative' };
    }
    const asFile = toPosix(normalize(join(sourceDir, refRaw)));
    return { resolvedAbsolute: asFile, resolvedRelativeToRoot: toPosix(relative(PROJECT_ROOT, asFile)), method: 'relative-to-file' };
  }

  if (refRaw.includes('/')) {
    const candidates = [
      { abs: join(PROJECT_ROOT, refRaw), method: 'project-root-relative' },
    ];
    if (sourceType === 'docs-for-ai') {
      candidates.push({ abs: join(DOC_FOR_AI_ROOT, refRaw), method: 'docs-for-ai-root-relative' });
    }
    if (sourceType === 'ai-dev') {
      candidates.push({ abs: join(DOC_FOR_AI_ROOT, refRaw), method: 'docs-for-ai-root-relative' });
    }
    candidates.push({ abs: join(sourceDir, refRaw), method: 'relative-to-file' });

    for (const c of candidates) {
      const abs = toPosix(normalize(c.abs));
      if (existsSync(abs)) {
        return { resolvedAbsolute: abs, resolvedRelativeToRoot: toPosix(relative(PROJECT_ROOT, abs)), method: c.method };
      }
    }

    const primary = toPosix(normalize(candidates[0].abs));
    return { resolvedAbsolute: primary, resolvedRelativeToRoot: toPosix(relative(PROJECT_ROOT, primary)), method: candidates[0].method };
  }

  // Bare filename - search for it
  const candidates = [
    { abs: join(sourceDir, refRaw), method: 'relative-to-file' },
  ];

  if (sourceType === 'docs-for-ai') {
    candidates.push({ abs: join(DOC_FOR_AI_ROOT, refRaw), method: 'docs-for-ai-root-relative' });
  }
  if (sourceType === 'ai-dev') {
    candidates.push({ abs: join(AI_DEV_ROOT, refRaw), method: 'ai-dev-root-relative' });
    candidates.push({ abs: join(DOC_FOR_AI_ROOT, refRaw), method: 'docs-for-ai-root-relative' });
  }

  for (const c of candidates) {
    const abs = toPosix(normalize(c.abs));
    if (existsSync(abs)) {
      return { resolvedAbsolute: abs, resolvedRelativeToRoot: toPosix(relative(PROJECT_ROOT, abs)), method: c.method };
    }
  }

  if (refRaw.endsWith('.md')) {
    const cache = buildFilenameCache(PROJECT_ROOT);
    const matches = cache.get(refRaw.split('/').pop());
    if (matches && matches.length > 0) {
      if (sourceType === 'docs-for-ai') {
        const inDfa = matches.find(p => p.includes('/docs-for-ai/'));
        if (inDfa) {
          return { resolvedAbsolute: toPosix(inDfa), resolvedRelativeToRoot: toPosix(relative(PROJECT_ROOT, inDfa)), method: 'filename-search-in-docs-for-ai' };
        }
      }
      return { resolvedAbsolute: toPosix(matches[0]), resolvedRelativeToRoot: toPosix(relative(PROJECT_ROOT, matches[0])), method: 'filename-search' };
    }
  }

  const primary = toPosix(normalize(candidates[0].abs));
  return { resolvedAbsolute: primary, resolvedRelativeToRoot: toPosix(relative(PROJECT_ROOT, primary)), method: candidates[0].method };
}

function checkBoundaryViolation(resolvedRel, sourceType, sourceRel, ext) {
  if (sourceType !== 'docs-for-ai') return null;

  if (resolvedRel.startsWith('ai-dev/')) {
    if (sourceRel.startsWith('docs-for-ai/90-maintenance/')) return null;
    return { rule: 'BOUNDARY: docs-for-ai referencing ai-dev', severity: 'error', message: `docs-for-ai files must not reference ai-dev/ paths. Found: \`${resolvedRel}\`` };
  }
  if (!resolvedRel.startsWith('docs-for-ai/')) {
    if (resolvedRel === 'AGENTS.md' || resolvedRel.startsWith('nop-entropy-e2e/')) return null;
    if (MODULE_PREFIX_RE.test(resolvedRel)) return null;
    return { rule: 'BOUNDARY: docs-for-ai referencing outside', severity: 'warning', message: `docs-for-ai files should only reference docs-for-ai/ paths. Found: \`${resolvedRel}\`` };
  }
  return null;
}

// 历史/归档文件：引用的源码/设计文档可能已被重构或移动，不应回溯修正
function isHistoricalFile(sourceRel) {
  if (sourceRel.startsWith('ai-dev/logs/')) return true;
  if (sourceRel.startsWith('ai-dev/analysis/')) return true;
  if (sourceRel.startsWith('ai-dev/audits/')) return true;
  if (/\/archived?\//.test(sourceRel)) return true;
  return false;
}

function isCompletedPlan(filePath) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    const first500 = content.substring(0, 500);
    return /^\s*>\s*Plan\s+Status:\s*completed/m.test(first500);
  } catch {
    return false;
  }
}

function analyzeFile(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const sourceRel = toPosix(relative(PROJECT_ROOT, filePath));
  const sourceType = sourceRel.startsWith('docs-for-ai/') ? 'docs-for-ai'
    : sourceRel.startsWith('ai-dev/') ? 'ai-dev' : 'other';

  const isHistorical = isHistoricalFile(sourceRel);
  const isPlanFile = sourceRel.startsWith('ai-dev/plans/');
  const isCompletedPlanFile = isPlanFile && isCompletedPlan(filePath);

  const skipBrokenLink = isHistorical || isCompletedPlanFile;
  const downgradeBrokenLink = isPlanFile && !isCompletedPlanFile && !isHistorical;

  const refs = extractReferences(content);
  const issues = [];
  const validRefs = [];

  for (const ref of refs) {
    const { resolvedAbsolute, resolvedRelativeToRoot, method } = resolveReference(ref.rawTarget, filePath, sourceType);

    if (resolvedRelativeToRoot.startsWith('..')) {
      issues.push({ file: sourceRel, line: ref.line, rawTarget: ref.rawTarget, type: ref.type, resolved: resolvedRelativeToRoot, rule: 'OUT_OF_PROJECT', severity: 'error', message: `Reference resolves outside project root: ${resolvedRelativeToRoot}` });
      continue;
    }

    const boundary = checkBoundaryViolation(resolvedRelativeToRoot, sourceType, sourceRel, ref.ext);
    if (boundary) {
      issues.push({ file: sourceRel, line: ref.line, rawTarget: ref.rawTarget, type: ref.type, resolved: resolvedRelativeToRoot, rule: boundary.rule, severity: boundary.severity, message: boundary.message });
    }

    const fileExists = existsSync(resolvedAbsolute) && statSync(resolvedAbsolute).isFile();
    const dirExists = existsSync(resolvedAbsolute) && statSync(resolvedAbsolute).isDirectory();

    if (!fileExists && !dirExists) {
      if (!skipBrokenLink) {
        const severity = downgradeBrokenLink ? 'warning' : 'error';
        issues.push({ file: sourceRel, line: ref.line, rawTarget: ref.rawTarget, type: ref.type, resolved: resolvedRelativeToRoot, rule: 'BROKEN_LINK', severity, message: `Referenced path does not exist: ${resolvedRelativeToRoot}` });
      }
    } else {
      validRefs.push({ file: sourceRel, line: ref.line, rawTarget: ref.rawTarget, type: ref.type, resolved: resolvedRelativeToRoot, existsAs: fileExists ? 'file' : 'directory', method });
    }
  }

  return { sourceRel, sourceType, totalRefs: refs.length, issues, validRefs };
}

function generateOutput(results, timestamp) {
  const allIssues = [];
  const allValidRefs = [];
  const fileSummaries = [];

  for (const r of results) {
    allIssues.push(...r.issues);
    allValidRefs.push(...r.validRefs);
    fileSummaries.push({ file: r.sourceRel, type: r.sourceType, totalRefs: r.totalRefs, validRefs: r.validRefs.length, issues: r.issues.length });
  }

  const errors = allIssues.filter(i => i.severity === 'error');
  const warnings = allIssues.filter(i => i.severity === 'warning');

  const byRule = {};
  for (const issue of allIssues) {
    byRule[issue.rule] = (byRule[issue.rule] || 0) + 1;
  }

  const jsonReport = {
    timestamp,
    projectRoot: toPosix(PROJECT_ROOT),
    rules: {
      'BOUNDARY: docs-for-ai referencing ai-dev': 'docs-for-ai/ files must not reference ai-dev/ paths',
      'BOUNDARY: docs-for-ai referencing outside': 'docs-for-ai/ files should only reference docs-for-ai/ paths (warning)',
      'BROKEN_LINK': 'Referenced file or directory does not exist',
      'OUT_OF_PROJECT': 'Reference resolves outside the project root',
    },
    summary: {
      filesScanned: fileSummaries.length,
      totalRefs: fileSummaries.reduce((s, f) => s + f.totalRefs, 0),
      validRefs: allValidRefs.length,
      totalIssues: allIssues.length,
      errors: errors.length,
      warnings: warnings.length,
      byRule,
    },
    fileSummaries,
    issues: allIssues,
    validRefs: allValidRefs,
  };

  let h = '';
  h += `# Doc Link Check Report\n\n`;
  h += `- **Timestamp**: ${timestamp}\n`;
  h += `- **Files scanned**: ${fileSummaries.length}\n`;
  h += `- **Total references**: ${jsonReport.summary.totalRefs}\n`;
  h += `- **Valid references**: ${allValidRefs.length}\n`;
  h += `- **Issues**: ${allIssues.length} (${errors.length} errors, ${warnings.length} warnings)\n\n`;

  if (errors.length > 0) {
    h += `## Errors (${errors.length})\n\n`;
    for (const e of errors) {
      h += `- **[${e.rule}]** \`${e.file}:${e.line}\` → \`${e.rawTarget}\` (resolves to \`${e.resolved}\`)\n`;
      h += `  > ${e.message}\n`;
    }
    h += '\n';
  }

  if (warnings.length > 0) {
    h += `## Warnings (${warnings.length})\n\n`;
    for (const w of warnings) {
      h += `- **[${w.rule}]** \`${w.file}:${w.line}\` → \`${w.rawTarget}\` (resolves to \`${w.resolved}\`)\n`;
      h += `  > ${w.message}\n`;
    }
    h += '\n';
  }

  h += `## Files with Issues\n\n`;
  h += `| File | Type | Refs | Valid | Issues |\n`;
  h += `|------|------|------|-------|--------|\n`;
  for (const f of fileSummaries.filter(f => f.issues > 0)) {
    h += `| \`${f.file}\` | ${f.type} | ${f.totalRefs} | ${f.validRefs} | **${f.issues}** |\n`;
  }
  h += '\n';

  if (Object.keys(byRule).length > 0) {
    h += `## Issues by Rule\n\n`;
    h += `| Rule | Count |\n`;
    h += `|------|-------|\n`;
    for (const [rule, count] of Object.entries(byRule)) {
      h += `| ${rule} | ${count} |\n`;
    }
    h += '\n';
  }

  return { jsonReport, humanReport: h };
}

function main() {
  const args = process.argv.slice(2);
  const strictMode = args.includes('--strict');
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const runLabel = `doc-link-check-${timestamp}`;

  const allFiles = [];
  for (const dir of DOC_DIRS) {
    const fullDir = join(PROJECT_ROOT, dir);
    if (existsSync(fullDir)) allFiles.push(...collectMdFiles(fullDir));
  }
  for (const rootFile of ROOT_MD_FILES) {
    const full = join(PROJECT_ROOT, rootFile);
    if (existsSync(full)) allFiles.push(full);
  }

  const scanSources = [...DOC_DIRS, ...ROOT_MD_FILES];
  console.log(`Scanning ${allFiles.length} markdown files in: ${scanSources.join(', ')}`);
  console.log(`Project root: ${PROJECT_ROOT}\n`);

  const results = [];
  for (const filePath of allFiles) {
    const rel = toPosix(relative(PROJECT_ROOT, filePath));
    if (SKIP_FILES.has(rel)) continue;
    results.push(analyzeFile(filePath));
  }

  const { jsonReport, humanReport } = generateOutput(results, timestamp);
  mkdirSync(TMP_DIR, { recursive: true });

  const jsonPath = join(TMP_DIR, `${runLabel}.json`);
  const mdPath = join(TMP_DIR, `${runLabel}.md`);
  writeFileSync(jsonPath, JSON.stringify(jsonReport, null, 2), 'utf-8');
  writeFileSync(mdPath, humanReport, 'utf-8');

  console.log(humanReport);
  console.log(`Output files:`);
  console.log(`  JSON: ${jsonPath}`);
  console.log(`  Markdown: ${mdPath}`);

  const errorCount = jsonReport.summary.errors;
  if (errorCount > 0) {
    console.error(`\n${errorCount} error(s) found.`);
    if (strictMode) process.exit(1);
  } else {
    console.log(`\nNo errors found.`);
  }
}

main();
