#!/usr/bin/env node

/**
 * check-oversized-files.mjs
 *
 * Check for oversized source files in the project.
 * Warns at WARN_LINES, errors at ERROR_LINES.
 *
 * Usage:
 *   node check-oversized-files.mjs                   # check all modules
 *   node check-oversized-files.mjs --module nop-stream  # check single module
 *   node check-oversized-files.mjs --warn 400 --error 600  # custom thresholds
 */

import { execFile } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

const PROJECT_ROOT = new URL('../..', import.meta.url).pathname;
const args = process.argv.slice(2);

const moduleArg = args.find((a, i) => args[i - 1] === '--module');
const warnArg = args.find((a, i) => args[i - 1] === '--warn');
const errorArg = args.find((a, i) => args[i - 1] === '--error');

const WARN_LINES = warnArg ? parseInt(warnArg, 10) : 500;
const ERROR_LINES = errorArg ? parseInt(errorArg, 10) : 700;

const JAVA_EXT = '.java';
const IGNORED_PARTS = ['_gen/', '/target/', 'node_modules/', '.git/'];

async function getFiles() {
  const target = moduleArg ? moduleArg : '.';
  try {
    const { stdout } = await execFileAsync('rg', [
      '--files', target,
      '-g', '*.java',
      '-g', '!*/_gen/*',
      '-g', '!*/target/*',
    ], { cwd: PROJECT_ROOT, maxBuffer: 50 * 1024 * 1024 });
    return stdout.split('\n').filter(Boolean).map(f => `${PROJECT_ROOT}/${f}`).filter(
      f => !IGNORED_PARTS.some(p => f.includes(p))
    );
  } catch {
    return [];
  }
}

function countLines(content) {
  const lines = content.split('\n');
  const total = lines.length;
  let blank = 0;
  let comment = 0;
  let inBlock = false;

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === '') { blank++; continue; }
    if (inBlock) {
      comment++;
      if (trimmed.includes('*/')) inBlock = false;
      continue;
    }
    if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) {
      comment++;
      if (trimmed.startsWith('/*') && !trimmed.includes('*/')) inBlock = true;
      continue;
    }
  }

  return { total, blank, comment, code: total - blank - comment };
}

async function main() {
  const files = await getFiles();
  if (files.length === 0) {
    console.log('No source files found.');
    return;
  }

  const warnings = [];
  const errors = [];

  for (const filePath of files) {
    const content = await readFile(filePath, 'utf-8');
    const { total, code } = countLines(content);
    const relPath = filePath.replace(PROJECT_ROOT, '');

    if (total > ERROR_LINES) {
      errors.push({ path: relPath, total, code });
    } else if (total > WARN_LINES) {
      warnings.push({ path: relPath, total, code });
    }
  }

  for (const e of errors) {
    console.log(`ERROR: ${e.path} - ${e.total} lines (${e.code} code) [threshold: ${ERROR_LINES}]`);
  }
  for (const w of warnings) {
    console.log(`WARN:  ${w.path} - ${w.total} lines (${w.code} code) [threshold: ${WARN_LINES}]`);
  }

  console.log(`\nChecked ${files.length} files. ${errors.length} errors, ${warnings.length} warnings.`);
  console.log(`Thresholds: warn=${WARN_LINES}, error=${ERROR_LINES} lines`);

  process.exit(errors.length > 0 ? 1 : 0);
}

main();
