#!/usr/bin/env node

import { mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { basename, join, relative, resolve } from 'node:path';

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..');
const TMP_DIR = join(PROJECT_ROOT, '_tmp');
const REPORT_JSON = join(TMP_DIR, 'i18n-en-xml-report.json');
const REPORT_MD = join(TMP_DIR, 'i18n-en-xml-report.md');

const STRICT = process.argv.includes('--strict');

const FILE_EXTENSIONS = new Set(['.xml', '.xmeta']);
const SKIP_DIRS = new Set(['.git', 'node_modules', 'target', '_tmp', '_dump']);

const CJK_RE = /[\u3400-\u9fff\uf900-\ufaff]/u;
const LATIN_RE = /[A-Za-z]/;
const ATTR_RE = /\bi18n-en:([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"/g;
const TAG_RE = /<([A-Za-z0-9:_-]+)(\s[^<>]*?)?>/g;
const ROOT_TAG_RE = /^\s*<([A-Za-z0-9:_-]+)(\s[^<>]*?)?>/;
const DISPLAY_NAME_RE = /\bdisplayName\s*=\s*"([^"]*)"/;
const I18N_DISPLAY_NAME_RE = /\bi18n-en:displayName\s*=\s*"([^"]*)"/;
const I18N_NAMESPACE_RE = /\bxmlns:i18n-en\s*=\s*"i18n-en"/;
const INCOMPLETE_EN_RE = /^(?:View|Add|Edit|Update|Query|Modify)\s*$/;
const XML_COMMENT_RE = /<!--[\s\S]*?-->/g;

const SUSPICIOUS_RULES = [
  { re: /\bWorflow\b/g, message: 'Possible typo: Workflow' },
  { re: /\bCamlCase\b/g, message: 'Possible typo: CamelCase' },
  { re: /\bTableName\b/g, message: 'Prefer spaced form: Table Name' },
  { re: /\bSql\b/g, message: 'Prefer acronym casing: SQL' },
  { re: /\bStd Sql Type\b/g, message: 'Prefer: Standard SQL Type or Std. SQL Type' },
  { re: /\bDep Module ID\b/g, message: 'Prefer fuller term: Dependent Module ID' },
  { re: /\bPrinciple Name\b/g, message: 'Possible typo: Principal Name' },
  { re: /\bEntity ID([0-9]+)\b/g, message: 'Prefer spaced numeric suffix, e.g. Entity ID 2' },
  { re: /\bsid\b/g, message: 'Prefer uppercase acronym: SID' },
  { re: /\bOauth\b/g, message: 'Prefer canonical acronym form: OAuth' },
];

function toPosix(path) {
  return path.replace(/\\/g, '/');
}

function lineNumberAt(content, index) {
  return content.slice(0, index).split('\n').length;
}

function collectFiles(dir, results = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) {
      continue;
    }

    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      collectFiles(fullPath, results);
      continue;
    }

    if (!entry.isFile()) {
      continue;
    }

    for (const ext of FILE_EXTENSIONS) {
      if (entry.name.endsWith(ext)) {
        results.push(fullPath);
        break;
      }
    }
  }
  return results;
}

function buildIssue(filePath, line, attrName, value, severity, code, message) {
  return {
    file: toPosix(relative(PROJECT_ROOT, filePath)),
    line,
    attrName,
    value,
    severity,
    code,
    message,
  };
}

function shouldRequireDisplayNameI18n(filePath) {
  const relativePath = toPosix(relative(PROJECT_ROOT, filePath));
  if (
    relativePath.includes('/demo/') ||
    relativePath.includes('/src/test/') ||
    relativePath.includes('/_gen/')
  ) {
    return false;
  }

  if (relativePath.endsWith('.action-auth.xml')) {
    return !basename(relativePath).startsWith('_');
  }

  return /(^|\/)model\/[^/]+\.(?:orm|api)\.xml$/.test(relativePath);
}

function getRequiredI18nTags(filePath) {
  const relativePath = toPosix(relative(PROJECT_ROOT, filePath));
  if (relativePath.endsWith('.action-auth.xml')) {
    return new Set(['resource']);
  }

  if (relativePath.endsWith('.orm.xml')) {
    return new Set(['entity']);
  }

  if (relativePath.endsWith('.api.xml')) {
    return new Set(['service', 'method', 'message']);
  }

  return new Set();
}

function analyzeValue(filePath, line, attrName, rawValue) {
  const value = rawValue.trim();
  const issues = [];

  if (!value) {
    issues.push(buildIssue(filePath, line, attrName, rawValue, 'warn', 'EMPTY_VALUE', 'Empty i18n-en value'));
    return issues;
  }

  if (CJK_RE.test(value)) {
    issues.push(
      buildIssue(filePath, line, attrName, rawValue, 'error', 'CONTAINS_CJK', 'Contains Chinese characters')
    );
  }

  if (!LATIN_RE.test(value)) {
    issues.push(
      buildIssue(filePath, line, attrName, rawValue, 'error', 'NO_LATIN', 'Does not contain Latin letters')
    );
  }

  if (INCOMPLETE_EN_RE.test(value)) {
    issues.push(
      buildIssue(filePath, line, attrName, rawValue, 'error', 'INCOMPLETE_LABEL', 'Incomplete English label')
    );
  }

  for (const rule of SUSPICIOUS_RULES) {
    if (rule.re.test(value)) {
      issues.push(buildIssue(filePath, line, attrName, rawValue, 'warn', 'SUSPICIOUS_TEXT', rule.message));
    }
    rule.re.lastIndex = 0;
  }

  return issues;
}

function scanNamespaceIssues(filePath, content) {
  if (!content.includes('i18n-en:')) {
    return [];
  }

  const normalizedContent = content.replace(XML_COMMENT_RE, '');
  const rootMatch = normalizedContent.match(ROOT_TAG_RE);
  if (!rootMatch) {
    return [];
  }

  const [, , rawAttrs = ''] = rootMatch;
  if (I18N_NAMESPACE_RE.test(rawAttrs)) {
    return [];
  }

  return [
    buildIssue(
      filePath,
      1,
      'xmlns:i18n-en',
      '',
      'error',
      'MISSING_I18N_NAMESPACE',
      'File uses i18n-en:* attributes but root element is missing xmlns:i18n-en="i18n-en"'
    ),
  ];
}

function scanMissingI18n(filePath, content) {
  if (!shouldRequireDisplayNameI18n(filePath)) {
    return [];
  }

  const issues = [];
  const normalizedContent = content.replace(XML_COMMENT_RE, '');
  const requiredTags = getRequiredI18nTags(filePath);
  let match;

  while ((match = TAG_RE.exec(normalizedContent)) !== null) {
    const [, tagName, rawAttrs = ''] = match;
    const normalizedTag = tagName.includes(':') ? tagName.split(':').pop() : tagName;
    if (!requiredTags.has(normalizedTag)) {
      continue;
    }

    const displayNameMatch = rawAttrs.match(DISPLAY_NAME_RE);
    if (!displayNameMatch) {
      continue;
    }

    const displayName = displayNameMatch[1].trim();
    if (!displayName || !CJK_RE.test(displayName)) {
      continue;
    }

    const i18nDisplayNameMatch = rawAttrs.match(I18N_DISPLAY_NAME_RE);
    if (i18nDisplayNameMatch) {
      continue;
    }

    const line = lineNumberAt(normalizedContent, match.index);
    issues.push(
      buildIssue(
        filePath,
        line,
        'displayName',
        displayName,
        'error',
        'MISSING_I18N_EN_DISPLAY_NAME',
        'Chinese displayName is missing i18n-en:displayName'
      )
    );
  }

  return issues;
}

function scanFile(filePath) {
  const content = readFileSync(filePath, 'utf8');
  const issues = [...scanNamespaceIssues(filePath, content), ...scanMissingI18n(filePath, content)];
  let match;

  while ((match = ATTR_RE.exec(content)) !== null) {
    const [, attrName, value] = match;
    const line = lineNumberAt(content, match.index);
    issues.push(...analyzeValue(filePath, line, attrName, value));
  }

  return issues;
}

function summarize(issues) {
  const summary = {
    total: issues.length,
    errors: issues.filter((item) => item.severity === 'error').length,
    warnings: issues.filter((item) => item.severity === 'warn').length,
    files: new Set(issues.map((item) => item.file)).size,
  };
  return summary;
}

function toMarkdown(summary, issues) {
  const lines = [];
  lines.push('# i18n-en XML/XMeta Check');
  lines.push('');
  lines.push(`- Files with issues: ${summary.files}`);
  lines.push(`- Errors: ${summary.errors}`);
  lines.push(`- Warnings: ${summary.warnings}`);
  lines.push(`- Total issues: ${summary.total}`);
  lines.push('');

  for (const issue of issues) {
    lines.push(
      `- [${issue.severity.toUpperCase()}] ${issue.file}:${issue.line} ${issue.attrName}=${JSON.stringify(issue.value)} ${issue.code} ${issue.message}`
    );
  }

  return lines.join('\n') + '\n';
}

function main() {
  const files = collectFiles(PROJECT_ROOT).sort();
  const issues = files.flatMap(scanFile);
  const summary = summarize(issues);

  mkdirSync(TMP_DIR, { recursive: true });
  writeFileSync(REPORT_JSON, JSON.stringify({ summary, issues }, null, 2));
  writeFileSync(REPORT_MD, toMarkdown(summary, issues));

  if (issues.length === 0) {
    console.log(`OK: no invalid i18n-en values found in ${files.length} XML/XMeta files`);
    return;
  }

  console.log(`Checked ${files.length} XML/XMeta files`);
  console.log(`Errors: ${summary.errors}, warnings: ${summary.warnings}, files: ${summary.files}`);
  console.log(`Reports: ${toPosix(relative(PROJECT_ROOT, REPORT_JSON))}, ${toPosix(relative(PROJECT_ROOT, REPORT_MD))}`);

  for (const issue of issues.slice(0, 80)) {
    console.log(
      `[${issue.severity}] ${issue.file}:${issue.line} ${issue.attrName}=${JSON.stringify(issue.value)} ${issue.code} ${issue.message}`
    );
  }

  if (issues.length > 80) {
    console.log(`... truncated ${issues.length - 80} more issues in report files`);
  }

  if (STRICT && summary.errors > 0) {
    process.exitCode = 1;
  }
}

main();
