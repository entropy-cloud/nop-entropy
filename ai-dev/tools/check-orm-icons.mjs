#!/usr/bin/env node

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..');

const INCLUDE_DIR_NAME = 'model';
const INCLUDE_SUFFIX = '.orm.xml';
const EXCLUDED_PATH_SEGMENTS = new Set([
  '.git',
  'node_modules',
  'target',
  '_gen',
  'src/test',
  'src\\test',
]);

const EXCLUDED_FILE_SUFFIXES = ['app.orm.xml', '_app.orm.xml'];

function toPosix(path) {
  return path.replace(/\\/g, '/');
}

function isExcludedDir(path) {
  const normalized = toPosix(path);
  for (const segment of EXCLUDED_PATH_SEGMENTS) {
    if (normalized.includes(`/${segment}/`) || normalized.endsWith(`/${segment}`)) {
      return true;
    }
  }
  return false;
}

function isSourceOrmModel(path) {
  const normalized = toPosix(path);
  if (!normalized.endsWith(INCLUDE_SUFFIX)) {
    return false;
  }
  const segments = normalized.split('/');
  if (segments.length < 2 || segments[segments.length - 2] !== INCLUDE_DIR_NAME) {
    return false;
  }
  for (const suffix of EXCLUDED_FILE_SUFFIXES) {
    if (normalized.endsWith(`/${suffix}`)) {
      return false;
    }
  }
  return true;
}

function collectSourceOrmFiles(dir, results = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (isExcludedDir(fullPath)) {
        continue;
      }
      collectSourceOrmFiles(fullPath, results);
      continue;
    }
    if (entry.isFile() && isSourceOrmModel(fullPath)) {
      results.push(fullPath);
    }
  }
  return results;
}

function lineNumberAt(content, index) {
  return content.slice(0, index).split('\n').length;
}

function compactWhitespace(value) {
  return value.replace(/\s+/g, ' ').trim();
}

function describeEntity(tagText) {
  const nameMatch = tagText.match(/\bname\s*=\s*"([^"]+)"/);
  const classNameMatch = tagText.match(/\bclassName\s*=\s*"([^"]+)"/);
  const displayNameMatch = tagText.match(/\bdisplayName\s*=\s*"([^"]+)"/);

  return {
    name: nameMatch?.[1] || classNameMatch?.[1] || '(unknown-entity)',
    displayName: displayNameMatch?.[1] || '',
  };
}

function scanFile(filePath) {
  const content = readFileSync(filePath, 'utf8');
  const entityTagRe = /<entity\b[\s\S]*?>/g;
  const missing = [];
  let match;

  while ((match = entityTagRe.exec(content)) !== null) {
    const tagText = match[0];
    if (/\bext:icon\s*=\s*"[^"]+"/.test(tagText)) {
      continue;
    }

    const entity = describeEntity(tagText);
    missing.push({
      filePath,
      line: lineNumberAt(content, match.index),
      name: entity.name,
      displayName: entity.displayName,
      tag: compactWhitespace(tagText),
    });
  }

  return missing;
}

function main() {
  const ormFiles = collectSourceOrmFiles(PROJECT_ROOT).sort();
  const missing = ormFiles.flatMap(scanFile);

  if (missing.length === 0) {
    console.log(`OK: all source ORM entities have ext:icon (${ormFiles.length} files checked)`);
    return;
  }

  console.error(`Missing ext:icon for ${missing.length} entities across ${ormFiles.length} source ORM files:`);
  for (const item of missing) {
    const rel = toPosix(relative(PROJECT_ROOT, item.filePath));
    const suffix = item.displayName ? ` (${item.displayName})` : '';
    console.error(`- ${rel}:${item.line} ${item.name}${suffix}`);
  }
  process.exitCode = 1;
}

main();
