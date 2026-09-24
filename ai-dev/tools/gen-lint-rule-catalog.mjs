#!/usr/bin/env node
// gen-lint-rule-catalog.mjs
//
// Deterministic rule-catalog generator for the nop-lint production library
// (roadmap item 42, plan 2026-09-24-2350-2).
//
//   node ai-dev/tools/gen-lint-rule-catalog.mjs           regenerate the doc
//   node ai-dev/tools/gen-lint-rule-catalog.mjs --check   exit 1 if the doc
//                                                         is stale (gate)
//   node ai-dev/tools/gen-lint-rule-catalog.mjs self-test positive control
//                                                         (tampered doc must
//                                                         be REJECTED)
//
// Contract (plan P3/P4/P7/P8/P9):
//   - scan root is pinned to src/main/resources (never target/classes)
//   - grouping is by DIRECTORY name (not metadata.category — the live
//     metadata values are fragmented); category/metadata divergence is
//     surfaced as a note column
//   - columns come from the live schema: top-level id/severity/message plus
//     metadata.version/autoFixable/source (source is a scalar)
//   - a message containing '|' or a newline is a hard error (the markdown
//     table must never silently break)
//   - --check compares the FULL generated text byte-for-byte
// Exit codes: 0 = green, 1 = stale/violation, 2 = usage error.

import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

const TOOL_DIR = import.meta.dirname;
const PROJECT_ROOT = resolve(TOOL_DIR, '..', '..');
const RULES_ROOT = join(PROJECT_ROOT, 'nop-lint', 'nop-lint-nop', 'src', 'main',
  'resources', '_vfs', 'nop', 'lint', 'rules');
const CATALOG_FILE = join(PROJECT_ROOT, 'nop-lint', 'docs', 'rule-catalog.md');

function scanRules(root) {
  const rules = [];
  for (const categoryDir of readdirSync(root, { withFileTypes: true })) {
    if (!categoryDir.isDirectory()) continue;
    const dir = join(root, categoryDir.name);
    for (const file of readdirSync(dir).sort()) {
      if (!file.endsWith('.rule.yml')) continue;
      const model = yaml.load(readFileSync(join(dir, file), 'utf8'));
      const meta = model.metadata ?? {};
      const message = String(model.message ?? '');
      if (message.includes('|') || message.includes('\n')) {
        console.error(`FAIL: rule '${model.id}' message contains a markdown-table`
          + ` breaker ('|' or newline); fix the message or the table contract`);
        process.exit(1);
      }
      rules.push({
        group: categoryDir.name,
        id: model.id,
        severity: model.severity,
        message,
        version: meta.version ?? '',
        autoFixable: meta.autoFixable === true,
        source: typeof meta.source === 'string' ? meta.source : '',
        categoryDivergence: meta.category && meta.category !== categoryDir.name
          ? meta.category : '',
      });
    }
  }
  rules.sort((a, b) => a.group === b.group
    ? a.id.localeCompare(b.id)
    : a.group.localeCompare(b.group));
  return rules;
}

export function generateCatalog(rules) {
  const lines = [];
  lines.push('# nop-lint 规则目录（rule catalog）');
  lines.push('');
  lines.push('> 由 `ai-dev/tools/gen-lint-rule-catalog.mjs` 从规则 metadata 确定性生成——'
    + '手改无效（`--check` 门禁强制再生成）。规则语义/severity/id 变更必须升 version 并在'
    + '规则头注记录（版本策略见 design 01 §2）。');
  lines.push('');
  let currentGroup = '';
  for (const rule of rules) {
    if (rule.group !== currentGroup) {
      currentGroup = rule.group;
      lines.push('');
      lines.push(`## ${currentGroup}`);
      lines.push('');
      lines.push('| id | severity | version | autoFixable | message | source |');
      lines.push('|---|---|---|---|---|---|');
    }
    const note = rule.categoryDivergence ? ` [metadata.category=${rule.categoryDivergence}]` : '';
    lines.push(`| ${rule.id} | ${rule.severity} | ${rule.version} | ${rule.autoFixable} |`
      + ` ${rule.message} | ${rule.source}${note} |`);
  }
  lines.push('');
  return lines.join('\n');
}

function regenerate() {
  return generateCatalog(scanRules(RULES_ROOT));
}

function run() {
  const mode = process.argv[2] ?? '';
  if (mode === 'self-test') {
    selfTest();
    return;
  }
  const generated = regenerate();
  if (mode === '--check') {
    const committed = readFileSync(CATALOG_FILE, 'utf8');
    if (committed !== generated) {
      console.error('rule catalog is STALE — regenerate with:'
        + ' node ai-dev/tools/gen-lint-rule-catalog.mjs');
      process.exit(1);
    }
    console.log('rule catalog gate ok: ' + generated.split('\n')
      .filter(l => l.startsWith('| ') && !l.includes('| id |')).length + ' rule rows in sync');
    return;
  }
  writeFileSync(CATALOG_FILE, generated);
  console.log('rule catalog written: ' + CATALOG_FILE);
}

function selfTest() {
  const rules = scanRules(RULES_ROOT);
  const generated = generateCatalog(rules);
  if (rules.length !== 62) {
    console.error('self-test expected the 62-rule library, found ' + rules.length);
    process.exit(2);
  }

  // control 1: a tampered catalog row must fail --check comparison
  const tampered = generated.replace('quality/no-system-out', 'quality/tampered-row');
  if (tampered !== generated && readFileSync(CATALOG_FILE, 'utf8') === generated) {
    // the committed catalog is in sync; a tampered regeneration differs —
    // the --check contract would flag it
  } else {
    console.error('self-test control 1 FAILED: tampering was not differentiated');
    process.exit(1);
  }

  // control 2: a dropped rule must change the generation
  const dropped = generateCatalog(rules.filter(r => r.id !== 'quality/no-system-out'));
  if (dropped === generated) {
    console.error('self-test control 2 FAILED: dropping a rule did not change the catalog');
    process.exit(1);
  }

  console.log('self-test ok: tampered-row / dropped-rule both differentiated; '
    + 'committed catalog is the deterministic regeneration');
}

run();
