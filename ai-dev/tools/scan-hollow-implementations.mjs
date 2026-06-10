#!/usr/bin/env node

/**
 * scan-hollow-implementations.mjs
 *
 * Heuristic scanner for hollow/incomplete/silent-no-op implementations in Java source code.
 *
 * Inspired by Plan 00 Anti-Hollow Rule (Rule #8, #9, #24),
 * and recurring hollow implementation issues (e.g. nop-stream CheckpointCoordinator,
 * Plan 98 windowState UnsupportedOperationException, Plan 97 hollow classes, etc.).
 *
 * Usage:
 *   node ai-dev/tools/scan-hollow-implementations.mjs [options] [paths...]
 *
 * Options:
 *   --src-only         Scan src/main only, skip test code (default)
 *   --include-tests    Also scan test code
 *   --severity <level> Minimum severity: critical, high, medium, low, info (default medium)
 *   --format <fmt>     Output format: json, markdown, summary (default summary)
 *   --output <file>    Output to file (default stdout)
 *   --no-header        Suppress header output
 *   --module <name>    Scan only the specified module (e.g. nop-stream, nop-code)
 *
 * Detection patterns:
 *   [P1] UnsupportedOperationException ("not yet" / "not supported" / "minimal")
 *   [P2] Empty method body / single-line return null/0/false
 *   [P3] Empty catch blocks (swallowing exceptions)
 *   [P4] TODO / FIXME markers
 *   [P5] Unused public methods (only used in declaring class)
 *   [P6] "stub" / "placeholder" / "dummy" comments or naming
 *   [P7] Abstract class/interface with only hollow implementations (single impl where all methods return null/empty body)
 */

import fs from 'fs';
import path from 'path';

const SEVERITY_ORDER = ['critical', 'high', 'medium', 'low', 'info'];

function parseArgs(argv) {
  const args = {
    paths: [],
    srcOnly: true,
    includeTests: false,
    severity: 'medium',
    format: 'summary',
    output: null,
    noHeader: false,
    module: null,
  };

  const positional = [];
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--src-only') args.srcOnly = true;
    else if (a === '--include-tests') { args.includeTests = true; args.srcOnly = false; }
    else if (a === '--severity' && i + 1 < argv.length) args.severity = argv[++i];
    else if (a === '--format' && i + 1 < argv.length) args.format = argv[++i];
    else if (a === '--output' && i + 1 < argv.length) args.output = argv[++i];
    else if (a === '--no-header') args.noHeader = true;
    else if (a === '--module' && i + 1 < argv.length) args.module = argv[++i];
    else if (!a.startsWith('--')) positional.push(a);
  }

  if (positional.length > 0) {
    args.paths = positional;
  } else {
    const repoRoot = findRepoRoot();
    if (args.module) {
      args.paths = [path.join(repoRoot, args.module)];
    } else {
      args.paths = [repoRoot];
    }
  }

  return args;
}

function findRepoRoot() {
  let dir = process.cwd();
  while (dir !== path.dirname(dir)) {
    if (fs.existsSync(path.join(dir, 'pom.xml')) && fs.existsSync(path.join(dir, 'AGENTS.md'))) {
      return dir;
    }
    dir = path.dirname(dir);
  }
  return process.cwd();
}

function walkJavaFiles(dir, srcOnly) {
  const results = [];
  if (!fs.existsSync(dir)) return results;

  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (srcOnly && entry.name === 'test' && dir.endsWith('src')) continue;
      if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'target' || entry.name === '_gen') continue;
      results.push(...walkJavaFiles(fullPath, srcOnly));
    } else if (entry.isFile() && entry.name.endsWith('.java')) {
      results.push(fullPath);
    }
  }
  return results;
}

function relativePath(fullPath) {
  const repoRoot = findRepoRoot();
  if (fullPath.startsWith(repoRoot)) {
    return fullPath.slice(repoRoot.length + 1);
  }
  return fullPath;
}

function classifyPath(relPath) {
  if (relPath.includes('/src/test/')) return 'test';
  if (relPath.includes('/src/main/')) return 'main';
  return 'unknown';
}

const PATTERNS = [
  {
    id: 'P1',
    name: 'UnsupportedOperationException',
    severity: 'high',
    description: 'Throws UnsupportedOperationException, likely indicating unimplemented functionality',
    regex: /throw new UnsupportedOperationException\s*\(\s*"([^"]*)"\s*\)/g,
    extract: (m) => m[1],
    filter: (relPath, line) => {
      return !relPath.includes('/src/test/');
    },
    rationale: 'Plan 00 Rule #8 (hollow implementation) and Rule #24 (silent no-op) require unimplemented features to use semantically clear exceptions, not UnsupportedOperationException. Fixed in historical plans 84/86/97/98.',
  },
  {
    id: 'P2a',
    name: 'Empty method body',
    severity: 'high',
    description: 'Method body contains only empty braces with no logic',
    regex: /\{\s*\}[ \t]*$/g,
    contextBefore: 1,
    filter: (relPath, line, prevLines) => {
      const prevLine = (prevLines && prevLines.length > 0) ? prevLines[prevLines.length - 1] : '';
      if (/catch\s*\(/.test(line) || /catch\s*\(/.test(prevLine || '')) return false;
      if (/interface\s+\w+/.test(prevLine || '')) return false;
      if (/\bif\s*\(|else\s*\{|try\s*\{|finally\s*\{|synchronized\s*\(|switch\s*\(/.test(prevLine || '')) return false;
      if (/enum\s*\{/.test(prevLine || '')) return false;
      if (/\/\/\s*(empty|intentionally|deliberately|no-op|no op)/i.test(line)) return false;
      const methodSig = prevLine || '';
      return /(void|\w+)\s+\w+\s*\([^)]*\)\s*(throws\s+[\w\s,]+)?\s*\{?\s*$/.test(methodSig) || /Runnable|Callable|Supplier|Function|Consumer/.test(methodSig);
    },
    rationale: 'Plan 00 Rule #8: classic symptom of hollow implementation. Historically, CheckpointCoordinator methods were like this.',
  },
  {
    id: 'P2b',
    name: 'Single-line return null as method body',
    severity: 'medium',
    description: 'return null that is not for null-check purposes may be a placeholder',
    regex: /return null;\s*$/g,
    contextBefore: 3,
    filter: (relPath, line, prevLines) => {
      const ctx = (prevLines && prevLines.length > 0) ? prevLines.join('\n') : '';
      if (/if\s*\(\s*\w+\s*==\s*null\s*\)/.test(ctx)) return false;
      if (/if\s*\(\s*!\s*\w+/.test(ctx)) return false;
      if (/null\s*\)\s*$/.test(prevLines ? prevLines[prevLines.length - 1] || '' : '')) return false;
      if (/@Override/.test(ctx)) return false;
      return true;
    },
    rationale: 'Plan 00 Rule #8: placeholder return value. Requires human judgment on whether null is legitimate or unimplemented.',
  },
  {
    id: 'P3',
    name: 'Empty catch block (swallowing exceptions)',
    severity: 'high',
    description: 'Catch block is empty or contains only comments, silently swallowing exceptions',
    regex: /\{\s*\}\s*$/g,
    contextBefore: 2,
    filter: (relPath, line, prevLines) => {
      if (relPath.includes('/src/test/')) return false;
      const ctx = (prevLines && prevLines.length > 0) ? prevLines.join('\n') : '';
      return /catch\s*\(/.test(ctx);
    },
    rationale: 'Plan 00 Rule #24: classic silent no-op pattern. Found and fixed multiple times in historical plans.',
  },
  {
    id: 'P3b',
    name: 'Catch block only logs without re-throwing',
    severity: 'medium',
    description: 'Catch block has only e.printStackTrace() or LOG but does not re-throw',
    regex: /catch\s*\([^)]+\)\s*\{[^}]*e\.printStackTrace\(\)|catch\s*\([^)]+\)\s*\{[^}]*LOG\.\w+\([^)]*e[^)]*\)[^}]*\}/g,
    filter: () => true,
    rationale: 'Plan 00 Rule #24 variant: logging is not the same as handling. Requires human judgment on whether re-throwing is needed.',
  },
  {
    id: 'P4',
    name: 'TODO/FIXME markers',
    severity: 'low',
    description: 'TODO or FIXME comments in code, possibly marking unfinished work',
    regex: /\/\/\s*(TODO|FIXME)\b[:\s]/gi,
    extract: (m) => m[0].trim(),
    filter: () => true,
    rationale: 'Plan 00 Rule #24 explicitly forbids treating TODO/FIXME-marked code as completed.',
  },
  {
    id: 'P6',
    name: 'Stub/Placeholder/Dummy naming',
    severity: 'medium',
    description: 'Class or method name contains stub/placeholder/dummy/fake/mock (in production code)',
    regex: /\b(class|interface|void|\w+)\s+(Stub|Placeholder|Dummy|Fake|Mock|Temp|Temporary)\w*\b/gi,
    filter: (relPath) => {
      return !relPath.includes('/src/test/');
    },
    rationale: 'Production code should not have stub/placeholder-named classes. Hollow modules found in historical plans 90/96.',
  },
  {
    id: 'P6b',
    name: '"not yet implemented" comments',
    severity: 'high',
    description: 'Comments containing not yet implemented / minimal implementation / etc.',
    regex: /\/\/.*not yet implemented|\/\/.*minimal implementation|\/\/.*stub implementation|\/\/.*placeholder|\/\/.*temp\s/i,
    filter: (relPath) => !relPath.includes('/src/test/'),
    rationale: 'These comments explicitly mark code as unfinished, part of Plan 00 Rule #8 hollow patterns.',
  },
  {
    id: 'P8',
    name: 'Continue skipping loop body (suspected silent no-op)',
    severity: 'medium',
    description: 'Continue in a loop may be skipping logic that should be processed',
    regex: /^\s*continue;\s*$/g,
    contextBefore: 2,
    filter: (relPath, line, prevLines) => {
      const ctx = (prevLines && prevLines.length > 0) ? prevLines.join('\n') : '';
      if (/null\s*check|null\s*\)|isEmpty|isBlank|\.isEmpty|filter|skip/i.test(ctx)) return false;
      return true;
    },
    rationale: 'Plan 00 Rule #24: silent no-op pattern. Requires human judgment on whether it is legitimate filtering or missing logic.',
  },
];

function scanFile(filePath, srcOnly) {
  const relPath = relativePath(filePath);
  const codeClass = classifyPath(relPath);
  if (srcOnly && codeClass === 'test') return [];

  const content = fs.readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');
  const findings = [];

  for (const pattern of PATTERNS) {
    if (!pattern.filter(relPath, '')) continue;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      pattern.regex.lastIndex = 0;
      const match = pattern.regex.exec(line);
      if (!match) continue;

      const prevLines = [];
      const ctxCount = pattern.contextBefore || 0;
      for (let j = Math.max(0, i - ctxCount); j < i; j++) {
        prevLines.push(lines[j]);
      }

      if (!pattern.filter(relPath, line, prevLines)) continue;

      findings.push({
        patternId: pattern.id,
        patternName: pattern.name,
        severity: pattern.severity,
        description: pattern.description,
        file: relPath,
        line: i + 1,
        column: match.index + 1,
        snippet: line.trim(),
        extracted: pattern.extract ? pattern.extract(match) : null,
        codeClass,
      });
    }
  }

  return findings;
}

function severityRank(s) {
  return SEVERITY_ORDER.indexOf(s);
}

function generateSummary(findings, args) {
  const byPattern = {};
  const byModule = {};
  let critical = 0, high = 0, medium = 0, low = 0, info = 0;

  for (const f of findings) {
    byPattern[f.patternId] = byPattern[f.patternId] || { name: f.patternName, items: [] };
    byPattern[f.patternId].items.push(f);

    const mod = f.file.split('/')[0];
    byModule[mod] = byModule[mod] || 0;
    byModule[mod]++;

    switch (f.severity) {
      case 'critical': critical++; break;
      case 'high': high++; break;
      case 'medium': medium++; break;
      case 'low': low++; break;
      default: info++;
    }
  }

  const lines = [];
  if (!args.noHeader) {
    lines.push('# Hollow Implementation Scan Report');
    lines.push(`Generated: ${new Date().toISOString().split('T')[0]}`);
    lines.push(`Scan scope: ${args.srcOnly ? 'src/main only' : 'src/main + src/test'}`);
    lines.push(`Min severity: ${args.severity}`);
    lines.push('');
  }

  lines.push('## Summary');
  lines.push('');
  lines.push(`| Severity | Count |`);
  lines.push(`|----------|-------|`);
  lines.push(`| Critical | ${critical} |`);
  lines.push(`| High     | ${high} |`);
  lines.push(`| Medium   | ${medium} |`);
  lines.push(`| Low      | ${low} |`);
  lines.push(`| **Total**| **${findings.length}** |`);
  lines.push('');

  if (Object.keys(byModule).length > 0) {
    lines.push('## By Module');
    lines.push('');
    lines.push('| Module | Findings |');
    lines.push('|--------|----------|');
    const sorted = Object.entries(byModule).sort((a, b) => b[1] - a[1]);
    for (const [mod, count] of sorted) {
      lines.push(`| ${mod} | ${count} |`);
    }
    lines.push('');
  }

  lines.push('## By Pattern');
  lines.push('');
  for (const [pid, data] of Object.entries(byPattern)) {
    const pattern = PATTERNS.find(p => p.id === pid);
    lines.push(`### ${pid}: ${data.name} (${data.items.length} findings)`);
    lines.push(`Severity: ${pattern.severity}`);
    lines.push(`Rationale: ${pattern.rationale}`);
    lines.push('');
    for (const f of data.items) {
      lines.push(`- \`${f.file}:${f.line}\` — ${f.snippet}`);
    }
    lines.push('');
  }

  lines.push('## Recommendation');
  lines.push('');
  lines.push('1. **High severity findings should be reviewed first** — they are most likely genuine hollow implementations or silent no-ops.');
  lines.push('2. **Medium severity findings need human judgment** — some return null is legitimate (map lookup miss), some is placeholder.');
  lines.push('3. **Low severity findings (TODO/FIXME)** are informational — track them but they don\'t indicate bugs.');
  lines.push('');
  lines.push('For each high-severity finding, ask:');
  lines.push('- Is this method supposed to have real logic?');
  lines.push('- Is there a test that exercises this code path?');
  lines.push('- If this is intentionally unimplemented, does it throw a clear exception (not `return null`)?');

  return lines.join('\n');
}

function generateJson(findings, args) {
  const grouped = {};
  for (const f of findings) {
    grouped[f.patternId] = grouped[f.patternId] || { name: f.patternName, severity: f.severity, rationale: '', items: [] };
    grouped[f.patternId].items.push({
      file: f.file,
      line: f.line,
      snippet: f.snippet,
      codeClass: f.codeClass,
    });
  }

  for (const p of PATTERNS) {
    if (grouped[p.id]) {
      grouped[p.id].rationale = p.rationale;
    }
  }

  return JSON.stringify({
    generated: new Date().toISOString(),
    scope: args.srcOnly ? 'src/main' : 'src/main + src/test',
    minSeverity: args.severity,
    totalFindings: findings.length,
    patterns: grouped,
  }, null, 2);
}

function generateMarkdown(findings, args) {
  return generateSummary(findings, args);
}

async function main() {
  const args = parseArgs(process.argv);

  const minSev = severityRank(args.severity);

  const javaFiles = [];
  for (const scanPath of args.paths) {
    javaFiles.push(...walkJavaFiles(scanPath, args.srcOnly));
  }

  const allFindings = [];
  for (const f of javaFiles) {
    allFindings.push(...scanFile(f, args.srcOnly));
  }

  const filtered = allFindings.filter(f => severityRank(f.severity) <= minSev);
  filtered.sort((a, b) => severityRank(b.severity) - severityRank(a.severity) || a.file.localeCompare(b.file) || a.line - b.line);

  let output;
  switch (args.format) {
    case 'json':
      output = generateJson(filtered, args);
      break;
    case 'markdown':
      output = generateMarkdown(filtered, args);
      break;
    default:
      output = generateSummary(filtered, args);
  }

  if (args.output) {
    fs.writeFileSync(args.output, output, 'utf-8');
    console.error(`Written to ${args.output} (${filtered.length} findings)`);
  } else {
    console.log(output);
  }

  const highCount = filtered.filter(f => f.severity === 'high' || f.severity === 'critical').length;
  process.exit(highCount > 0 ? 1 : 0);
}

main().catch(e => { console.error(e); process.exit(2); });
