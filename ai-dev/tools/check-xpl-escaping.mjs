#!/usr/bin/env node

import { readFileSync, existsSync, statSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, resolve, relative, dirname } from 'node:path';

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..');
const TMP_DIR = join(PROJECT_ROOT, '_tmp');
const D = String.fromCharCode(36); // $-sign, avoid template-literal clash

// Pattern: ${'$'}{...} - check if every usage is in correct Xpl-processed context

const SKIP_DIRS = new Set(['node_modules', '.git', 'target', '_dump', '.mvn', 'ai-dev', '_tmp', 'graphify-out']);

// File extensions to scan (longest first for suffix matching)
const SCAN_EXT_LIST = [
  '.view.xml', '.page.yaml', '.page.yml', '.page.json',
  '.xgen', '.xpl', '.xlib', '.json',
];



// ---------- helpers ----------

function scanFiles(dir, results) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name.startsWith('.') || SKIP_DIRS.has(entry.name)) continue;
      scanFiles(fullPath, results);
    } else if (entry.isFile()) {
      if (SCAN_EXT_LIST.some(ext => entry.name.endsWith(ext))) results.push(fullPath);
    }
  }
}

function fileCategory(fp) {
  if (fp.endsWith('.xgen')) return 'xgen';
  if (fp.endsWith('.xpl') || fp.endsWith('.xlib')) return 'xpl';
  if (fp.endsWith('.view.xml')) return 'view';
  if (fp.endsWith('.page.yaml') || fp.endsWith('.page.yml')) return 'page_yaml';
  if (fp.endsWith('.page.json')) return 'page_json';
  if (fp.endsWith('.json')) return 'json';
  return 'other';
}

function isGenerated(fp) {
  // Check /_gen/ anywhere in path, OR underscore-prefixed view.xml filename
  return fp.includes('/_gen/') || /\/_[a-z][a-zA-Z0-9]*\.view\.xml$/.test(fp);
}

// Extract XML context tag from a line in a view.xml file
function xmlContextForLine(lines, lineIdx) {
  const line = lines[lineIdx];
  if (!line) return { ctx: 'main-body', desc: 'view.xml main body' };

  // Check CDATA
  const trimmed = line.trim();
  if (trimmed.startsWith('//') || trimmed.startsWith('/*') || /^\s*\*/.test(line)) return null; // in JS comment — skip

  // Walk backwards to find enclosing XML block
  let depth = 0;
  let inGenControl = false;
  let inGenExtends = false;
  let inScript = false;

  for (let i = lineIdx; i >= 0 && i > lineIdx - 60; i--) {
    const l = lines[i];
    // Track tag openings/closings with a simple stack
    const opens = l.match(/<(\w[\w:-]*)(?=[\s>])/g);
    const closes = l.match(/<\/\w[\w:-]*>/g);

    if (closes) for (const c of closes) { depth--; }
    if (opens) for (const o of opens) {
      const tag = o.slice(1);
      if (tag === 'gen-control' || tag === 'renderer') inGenControl = true;
      else if (tag === 'x:gen-extends' || tag === 'x:post-extends') inGenExtends = true;
      else if (tag === 'c:script') inScript = true;
      else if (tag === '/gen-control' || tag === '/renderer') inGenControl = false;
      else if (tag === '/x:gen-extends' || tag === '/x:post-extends') inGenExtends = false;
      else if (tag === '/c:script') inScript = false;
      depth++;
    }
  }

  if (inScript) return { ctx: 'c-script', desc: '<c:script> block (XScript, not Xpl template)' };
  if (inGenControl) return { ctx: 'gen-control', desc: '<gen-control>/<renderer> block (xpl-xjson)' };
  if (inGenExtends) return { ctx: 'gen-extends', desc: '<x:gen-extends>/<x:post-extends> block (Xpl template)' };

  // Check if line contains a SchemaExpression attribute
  if (/(?:visibleOn|disabledOn|requiredOn|hiddenOn|staticOn)\s*=\s*["']/.test(line)) {
    return { ctx: 'schema-expr', desc: 'SchemaExpression attribute (not Xpl-processed)' };
  }

  return { ctx: 'main-body', desc: 'view.xml main body (not Xpl-processed)' };
}

function yamlContextForLine(lines, lineIdx) {
  const line = lines[lineIdx];
  if (!line) return { ctx: 'yaml-main', desc: 'page.yaml main body' };

  // Check if we are inside an x:gen-extends or x:post-extends block
  // Heuristic: find the nearest preceding top-level key
  let inBlock = false;
  for (let i = lineIdx - 1; i >= 0 && i > lineIdx - 30; i--) {
    const prev = lines[i];
    if (/^\s*(x:gen-extends|x:post-extends)\s*:\s*\|/.test(prev)) { inBlock = true; break; }
    // Stop at next top-level key
    if (i > 0 && /^\w/.test(prev.trim()) && prev.trim().includes(':') && !prev.trim().startsWith('-') && prev.trim().endsWith(':') && !prev.startsWith(' ')) break;
    if (i > 0 && /^\w/.test(prev.trim()) && prev.includes(':') && prev[0] !== ' ') break;
  }
  if (inBlock) return { ctx: 'yaml-gen-extends', desc: 'inside x:gen-extends/x:post-extends YAML string (Xpl-processed)' };
  return { ctx: 'yaml-main', desc: 'page.yaml main body (not Xpl-processed)' };
}

// ---------- analysis ----------

function analyzeFile(filePath) {
  const rel = relative(PROJECT_ROOT, filePath);
  const cat = fileCategory(filePath);
  const gen = isGenerated(filePath);
  let content;
  try { content = readFileSync(filePath, 'utf-8'); }
  catch { return []; }
  const lines = content.split('\n');
  const findings = [];

  for (let i = 0; i < lines.length; i++) {
    const lineNum = i + 1;
    const line = lines[i];

    // ---- Pattern 1: ${'$'}{...} escaping audit ----
    // Verify every ${'$'}{...} usage is in the correct Xpl-processed context.
    // In non-Xpl context (view.xml main body, YAML main body), ${'$'}{...} is over-escaped.
    const re = /\$\{'\$'\}\{/g;
    let m;
    while ((m = re.exec(line)) !== null) {
      let ctxRaw;
      if (cat === 'view') ctxRaw = xmlContextForLine(lines, i);
      else if (cat === 'page_yaml') ctxRaw = yamlContextForLine(lines, i);
      else if (['xpl', 'xgen', 'xlib'].includes(cat)) ctxRaw = xmlContextForLine(lines, i);
      else ctxRaw = { ctx: 'plain', desc: cat + ' file' };

      const ctx = ctxRaw && ctxRaw.ctx === 'main-body' && ['xpl', 'xgen', 'xlib'].includes(cat)
        ? { ctx: 'xpl-context', desc: 'Xpl template content' }
        : ctxRaw || { ctx: 'xpl-context', desc: 'Xpl template' };

      // Is this in a non-Xpl context (over-escaping)?
      const nonXpl = ['main-body', 'schema-expr', 'yaml-main', 'plain'];
      const isOver = nonXpl.includes(ctx.ctx);

      findings.push({
        file: rel, gen, line: lineNum, col: m.index,
        match: D + "{'" + D + "'}{",
        snippet: line.trim().substring(0, 100),
        ctx, cat,
        severity: isOver ? 'warning' : 'info',
        pattern: isOver ? 'ESCAPE_OVER' : 'ESCAPE_OK',
        msg: (isOver
          ? D + "{'" + D + "'}{...} in non-Xpl context (" + ctx.desc + ") - over-escaped; write " + D + "{xxx} directly"
          : D + "{'" + D + "'}{...} in " + ctx.desc + " - correct escaping"),
      });
    }
  }

  return findings;
}

// ---------- reporting ----------

function buildReport(allFindings) {
  const byFile = new Map();
  for (const f of allFindings) {
    if (!byFile.has(f.file)) byFile.set(f.file, []);
    byFile.get(f.file).push(f);
  }

  const sorted = [...byFile.keys()].sort();

  let output = '';
  let warnCount = 0;

  for (const file of sorted) {
    const items = byFile.get(file);
    const genTag = items[0]?.gen ? ' [GEN]' : '';
    output += `\n${file}${genTag}\n`;
    for (const f of items) {
      if (f.severity === 'warning') warnCount++;
      const sym = f.severity === 'warning' ? '!' : '.';
      const ctxTag = f.ctx?.ctx || '?';
      output += `  ${sym} L${f.line}:${f.col} [${ctxTag}] ${f.msg}\n`;
      if (f.snippet) output += `    \u2192 ${f.snippet}\n`;
    }
    output += '\n';
  }

  const fileCount = byFile.size;
  const totalCount = allFindings.length;
  const infoCount = totalCount - warnCount;

  output += `=== Summary ===\n`;
  output += `Files: ${fileCount}  Findings: ${totalCount}  Warnings: ${warnCount}  Info: ${infoCount}\n`;

  return { output, warnCount, infoCount, byFile };
}

// ---------- main ----------

function main() {
  const args = process.argv.slice(2);
  const outputJson = args.includes('--json');

  const files = [];
  scanFiles(PROJECT_ROOT, files);

  const allFindings = [];
  for (const f of files) {
    const r = analyzeFile(f);
    allFindings.push(...r);
  }

  const { output, warnCount, infoCount, byFile } = buildReport(allFindings);

  if (outputJson) {
    const ts = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19);
    mkdirSync(TMP_DIR, { recursive: true });
    const jsonPath = join(TMP_DIR, `xpl-escaping-${ts}.json`);
    const mdPath = join(TMP_DIR, `xpl-escaping-${ts}.md`);
    writeFileSync(jsonPath, JSON.stringify(allFindings, null, 2));
    writeFileSync(mdPath, output);
    console.log(`Report written to:`);
    console.log(`  JSON: ${jsonPath}`);
    console.log(`  MD:   ${mdPath}`);
  } else {
    console.log(output);
  }

  // Non-fatal: heuristic tool, never fails CI
  process.exit(0);
}

main();
