#!/usr/bin/env node

/**
 * check-import-order.mjs
 *
 * Check import ordering in Java source files.
 * Expected order: io.nop.* → third-party (incl. jakarta.* and javax.*) → java.*
 * Groups should be separated by blank lines.
 * Static imports are expected last.
 *
 * NOTE: The expected order follows the actual codebase convention
 * (see AGENTS.md and ai-dev/audits/2026-07-31-0539-arm-MA4.2-nop-ai-style.md MA4.2-14).
 * This tool is advisory (AI code generation guidance), not a CI hard gate.
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

function classifyImport(importPath, isStatic) {
  if (isStatic) return 'static';
  if (importPath.startsWith('io.nop.')) return 'nop';
  if (importPath.startsWith('java.')) return 'java';
  return 'third'; // third-party, incl. jakarta.* / javax.*
}

const CATEGORY_ORDER = { nop: 0, third: 1, java: 2, static: 3 };

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
    const match = line.match(/^import\s+(static\s+)?([a-zA-Z0-9_.]+)/);
    if (match) {
      inImportBlock = true;
      imports.push({ lineNum: i + 1, importPath: match[2], raw: line, isStatic: !!match[1] });
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
    const category = classifyImport(imp.importPath, imp.isStatic);
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
    console.log('\nFix hint: Reorder imports in each file to: io.nop.* → third-party (incl. jakarta.*/javax.*) → java.*');
    console.log('Each group separated by a blank line. Static imports last.');
  }

  process.exit(totalErrors > 0 ? 1 : 0);
}

main();
