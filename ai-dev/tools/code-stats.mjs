#!/usr/bin/env node

/**
 * code-stats.mjs
 *
 * Code statistics for nop-entropy modules.
 * Reports file counts, line counts, oversized files, and test ratios.
 *
 * Usage:
 *   node code-stats.mjs                        # all modules
 *   node code-stats.mjs --module nop-stream     # single module
 */

import { execFile } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

const PROJECT_ROOT = new URL('../..', import.meta.url).pathname;
const args = process.argv.slice(2);
const moduleArg = args.find((a, i) => args[i - 1] === '--module');

const JAVA_EXT = '.java';
const XML_EXT = '.xml';
const MD_EXT = '.md';

const IGNORED = ['_gen/', '/target/', 'node_modules/', '.git/'];

function formatNum(n) { return n.toLocaleString('en-US'); }

function bar(value, max, width = 30) {
  const filled = max > 0 ? Math.round((value / max) * width) : 0;
  return '\u2588'.repeat(filled) + '\u2591'.repeat(width - filled);
}

function countLines(content) {
  const lines = content.split('\n');
  const total = lines.length;
  let blank = 0, comment = 0, inBlock = false;
  for (const line of lines) {
    const t = line.trim();
    if (t === '') { blank++; continue; }
    if (inBlock) { comment++; if (t.includes('*/')) inBlock = false; continue; }
    if (t.startsWith('//') || t.startsWith('*') || t.startsWith('/*')) {
      comment++;
      if (t.startsWith('/*') && !t.includes('*/')) inBlock = true;
      continue;
    }
  }
  return { total, blank, comment, code: total - blank - comment };
}

function getModule(filePath) {
  const match = filePath.match(/^nop-[^/]+\//);
  return match ? match[0].replace('/', '') : '(root)';
}

function isTest(filePath) {
  return filePath.includes('/test/') || filePath.includes('/src/test/');
}

function isGenerated(filePath) {
  return filePath.includes('/_gen/') || filePath.includes('/target/');
}

async function getFiles() {
  const target = moduleArg ? moduleArg : '.';
  try {
    const { stdout } = await execFileAsync('rg', ['--files', target], {
      cwd: PROJECT_ROOT, maxBuffer: 100 * 1024 * 1024,
    });
    return stdout.split('\n').filter(Boolean).map(f => `${PROJECT_ROOT}/${f}`).filter(
      f => !IGNORED.some(p => f.includes(p))
    );
  } catch { return []; }
}

async function main() {
  const files = await getFiles();
  console.log(`\n  nop-entropy Code Statistics`);
  console.log(`  Generated: ${new Date().toISOString().slice(0, 10)}`);
  console.log(`  Tracked files: ${formatNum(files.length)}\n`);

  const javaFiles = files.filter(f => f.endsWith(JAVA_EXT) && !isGenerated(f));
  const xmlFiles = files.filter(f => f.endsWith(XML_EXT) && !isGenerated(f));
  const mdFiles = files.filter(f => f.endsWith(MD_EXT));

  console.log('  File type distribution:');
  console.log(`    Java source:  ${formatNum(javaFiles.length)} files`);
  console.log(`    XML:          ${formatNum(xmlFiles.length)} files`);
  console.log(`    Markdown:     ${formatNum(mdFiles.length)} files`);
  console.log(`    Other:        ${formatNum(files.length - javaFiles.length - xmlFiles.length - mdFiles.length)} files`);

  // Per-module stats
  const moduleData = new Map();
  for (const filePath of javaFiles) {
    const mod = getModule(filePath.replace(PROJECT_ROOT, ''));
    if (!moduleData.has(mod)) moduleData.set(mod, { files: 0, codeLines: 0, testFiles: 0, testCode: 0, srcFiles: 0, srcCode: 0 });
    const data = moduleData.get(mod);
    data.files++;
    try {
      const content = await readFile(filePath, 'utf-8');
      const { code } = countLines(content);
      data.codeLines += code;
      if (isTest(filePath)) { data.testFiles++; data.testCode += code; }
      else { data.srcFiles++; data.srcCode += code; }
    } catch { /* skip unreadable */ }
  }

  const sortedModules = [...moduleData.entries()].sort((a, b) => b[1].codeLines - a[1].codeLines);
  const maxCode = Math.max(...sortedModules.map(([, d]) => d.codeLines), 1);

  console.log('\n  Per-module Java statistics:');
  console.log(`  ${'Module'.padEnd(30)} ${'Files'.padStart(6)} ${'Code'.padStart(8)} ${'Src'.padStart(6)} ${'Test'.padStart(6)} ${'Ratio'.padStart(7)}  Size`);
  console.log('  ' + '-'.repeat(85));

  let totalFiles = 0, totalCode = 0, totalTest = 0, totalSrc = 0;
  for (const [mod, d] of sortedModules) {
    const ratio = d.srcCode > 0 ? (d.testCode / d.srcCode).toFixed(2) : '-';
    console.log(`  ${mod.padEnd(30)} ${formatNum(d.files).padStart(6)} ${formatNum(d.codeLines).padStart(8)} ${formatNum(d.srcFiles).padStart(6)} ${formatNum(d.testFiles).padStart(6)} ${ratio.padStart(7)}  ${bar(d.codeLines, maxCode, 10)}`);
    totalFiles += d.files; totalCode += d.codeLines; totalTest += d.testCode; totalSrc += d.srcCode;
  }

  console.log('  ' + '-'.repeat(85));
  const totalRatio = totalSrc > 0 ? (totalTest / totalSrc).toFixed(2) : '-';
  console.log(`  ${'TOTAL'.padEnd(30)} ${formatNum(totalFiles).padStart(6)} ${formatNum(totalCode).padStart(8)} ${''.padStart(6)} ${''.padStart(6)} ${totalRatio.padStart(7)}`);

  // Top files
  console.log('\n  Largest Java source files (by code lines, top 15):');
  const fileStats = [];
  for (const filePath of javaFiles) {
    if (isTest(filePath)) continue;
    try {
      const content = await readFile(filePath, 'utf-8');
      const { total, code } = countLines(content);
      fileStats.push({ path: filePath.replace(PROJECT_ROOT, ''), total, code });
    } catch { /* skip */ }
  }
  fileStats.sort((a, b) => b.code - a.code);
  console.log(`  ${'File'.padEnd(70)} ${'LOC'.padStart(6)} ${'Code'.padStart(6)}`);
  console.log('  ' + '-'.repeat(85));
  for (const f of fileStats.slice(0, 15)) {
    console.log(`  ${f.path.padEnd(70)} ${formatNum(f.total).padStart(6)} ${formatNum(f.code).padStart(6)}`);
  }

  console.log('\n');
}

main();
