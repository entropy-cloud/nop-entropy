#!/usr/bin/env node

/**
 * check-import-order.mjs
 *
 * Check import ordering in Java source files.
 * Expected order: java.* → jakarta.* → third-party → io.nop.*
 * Groups should be separated by blank lines.
 *
 * Usage:
 *   node check-import-order.mjs                          # check all modules
 *   node check-import-order.mjs --module nop-stream      # check single module
 *   node check-import-order.mjs --fix                    # show fix hints
 */

import { execFile } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

const PROJECT_ROOT = new URL('../..', import.meta.url).pathname;
const args = process.argv.slice(2);
const FIX_MODE = args.includes('--fix');
const moduleArg = args.find((a, i) => args[i - 1] === '--module');

function classifyImport(importPath) {
  if (importPath.startsWith('java.')) return 'java';
  if (importPath.startsWith('javax.') || importPath.startsWith('jakarta.')) return 'jakarta';
  if (importPath.startsWith('io.nop.')) return 'nop';
  return 'third';
}

const CATEGORY_ORDER = { java: 0, jakarta: 1, third: 2, nop: 3 };

async function getJavaFiles() {
  const target = moduleArg ? moduleArg : '.';
  const cwd = moduleArg ? PROJECT_ROOT : PROJECT_ROOT;
  try {
    const { stdout } = await execFileAsync('rg', [
      '--files', target,
      '-g', '*.java',
      '-g', '!*/_gen/*',
      '-g', '!*/target/*',
    ], { cwd, maxBuffer: 50 * 1024 * 1024 });
    return stdout.split('\n').filter(Boolean).map(f => `${PROJECT_ROOT}/${f}`);
  } catch {
    return [];
  }
}

function checkFileContent(content, filePath) {
  const lines = content.split('\n');
  const imports = [];
  let inImportBlock = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const match = line.match(/^import\s+(?:static\s+)?([a-zA-Z0-9_.]+)/);
    if (match) {
      inImportBlock = true;
      imports.push({ lineNum: i + 1, importPath: match[1], raw: line });
    } else if (inImportBlock && line.trim() === '') {
      // blank line between imports is fine
      continue;
    } else if (inImportBlock) {
      break; // end of import block
    }
  }

  const errors = [];
  let prevCategory = -1;

  for (const imp of imports) {
    const category = classifyImport(imp.importPath);
    const order = CATEGORY_ORDER[category];

    if (order < prevCategory) {
      errors.push({
        line: imp.lineNum,
        importPath: imp.importPath,
        message: `${category} import after higher-priority group`,
      });
    }
    prevCategory = order;
  }

  return errors;
}

async function main() {
  const files = await getJavaFiles();
  if (files.length === 0) {
    console.log('No Java files found.');
    process.exit(0);
  }

  let totalErrors = 0;
  const errorFiles = [];

  for (const filePath of files) {
    const content = await readFile(filePath, 'utf-8');
    const errors = checkFileContent(content, filePath);
    if (errors.length > 0) {
      totalErrors += errors.length;
      errorFiles.push(filePath);
      const relPath = filePath.replace(PROJECT_ROOT, '');
      for (const err of errors) {
        console.log(`ERROR: ${relPath}:${err.line} - ${err.importPath} (${err.message})`);
      }
    }
  }

  console.log(`\nChecked ${files.length} files, ${totalErrors} errors in ${errorFiles.length} files.`);

  if (totalErrors > 0 && FIX_MODE) {
    console.log('\nFix hint: Reorder imports in each file to: java.* → jakarta.* → third-party → io.nop.*');
    console.log('Each group separated by a blank line.');
  }

  process.exit(totalErrors > 0 ? 1 : 0);
}

main();
