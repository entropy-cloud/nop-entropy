#!/usr/bin/env node

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..');

const INCLUDE_DIR_NAME = 'model';
const ORM_INCLUDE_SUFFIX = '.orm.xml';
const ACTION_AUTH_SUFFIX = '.action-auth.xml';
const EXCLUDED_PATH_SEGMENTS = new Set([
  '.git',
  'node_modules',
  'target',
  '_gen',
  'src/test',
  'src\\test',
]);

const EXCLUDED_ORM_FILE_SUFFIXES = ['app.orm.xml', '_app.orm.xml'];

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
  if (!normalized.endsWith(ORM_INCLUDE_SUFFIX)) {
    return false;
  }
  const segments = normalized.split('/');
  if (segments.length < 2 || segments[segments.length - 2] !== INCLUDE_DIR_NAME) {
    return false;
  }
  for (const suffix of EXCLUDED_ORM_FILE_SUFFIXES) {
    if (normalized.endsWith(`/${suffix}`)) {
      return false;
    }
  }
  return true;
}

function isSourceActionAuth(path) {
  const normalized = toPosix(path);
  if (!normalized.endsWith(ACTION_AUTH_SUFFIX)) {
    return false;
  }
  if (normalized.includes('/src/test/')) {
    return false;
  }
  const fileName = normalized.split('/').pop();
  if (!fileName || fileName.startsWith('_')) {
    return false;
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

function collectSourceActionAuthFiles(dir, results = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (isExcludedDir(fullPath)) {
        continue;
      }
      collectSourceActionAuthFiles(fullPath, results);
      continue;
    }
    if (entry.isFile() && isSourceActionAuth(fullPath)) {
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

function rootTag(content) {
  const match = content.match(/<orm\b[\s\S]*?>/);
  return match
    ? {
        tag: match[0],
        index: match.index,
      }
    : null;
}

function scanFile(filePath) {
  const content = readFileSync(filePath, 'utf8');
  const entityTagRe = /<entity\b[\s\S]*?>/g;
  const missing = [];
  let match;

  const root = rootTag(content);
  if (!root) {
    missing.push({
      filePath,
      line: 1,
      kind: 'orm',
      name: '(missing-root-tag)',
      tag: '(no <orm ...> root tag found)',
    });
  } else if (!/\bext:icon\s*=\s*"[^"]+"/.test(root.tag)) {
    missing.push({
      filePath,
      line: lineNumberAt(content, root.index),
      kind: 'orm',
      name: '(root-orm)',
      tag: compactWhitespace(root.tag),
    });
  }

  while ((match = entityTagRe.exec(content)) !== null) {
    const tagText = match[0];
    if (/\bext:icon\s*=\s*"[^"]+"/.test(tagText)) {
      continue;
    }

    const entity = describeEntity(tagText);
    missing.push({
      filePath,
      line: lineNumberAt(content, match.index),
      kind: 'entity',
      name: entity.name,
      displayName: entity.displayName,
      tag: compactWhitespace(tagText),
    });
  }

  return missing;
}

function scanActionAuthFile(filePath) {
  const content = readFileSync(filePath, 'utf8');
  const resourceTagRe = /<resource\b[\s\S]*?>/g;
  const missing = [];
  let match;

  while ((match = resourceTagRe.exec(content)) !== null) {
    const tagText = match[0];
    if (!/\bresourceType\s*=\s*"(?:TOPM|SUBM)"/.test(tagText)) {
      continue;
    }
    if (/\bicon\s*=\s*"[^"]+"/.test(tagText)) {
      continue;
    }

    const idMatch = tagText.match(/\bid\s*=\s*"([^"]+)"/);
    const displayNameMatch = tagText.match(/\bdisplayName\s*=\s*"([^"]+)"/);
    missing.push({
      filePath,
      line: lineNumberAt(content, match.index),
      kind: 'menu',
      name: idMatch?.[1] || '(unknown-resource)',
      displayName: displayNameMatch?.[1] || '',
      tag: compactWhitespace(tagText),
    });
  }

  return missing;
}

function main() {
  const ormFiles = collectSourceOrmFiles(PROJECT_ROOT).sort();
  const actionAuthFiles = collectSourceActionAuthFiles(PROJECT_ROOT).sort();
  const missing = ormFiles.flatMap(scanFile).concat(actionAuthFiles.flatMap(scanActionAuthFile));

  if (missing.length === 0) {
    console.log(
      `OK: all source ORM roots/entities and source TOPM/SUBM menus have icons (${ormFiles.length} ORM files, ${actionAuthFiles.length} action-auth files checked)`
    );
    return;
  }

  console.error(
    `Missing required icons for ${missing.length} source ORM root/entity tags or source TOPM/SUBM menu resources across ${ormFiles.length} ORM files and ${actionAuthFiles.length} action-auth files:`
  );
  for (const item of missing) {
    const rel = toPosix(relative(PROJECT_ROOT, item.filePath));
    const suffix = item.displayName ? ` (${item.displayName})` : '';
    console.error(`- ${rel}:${item.line} [${item.kind}] ${item.name}${suffix}`);
  }
  process.exitCode = 1;
}

main();
