#!/usr/bin/env node
// check-lint-migration-manifest.mjs
//
// Anti-corruption gate for the check-*.mjs migration manifest (roadmap item 28,
// plan 2026-09-22-0854-3). Style precedent: check-nop-stream-audit-manifest.mjs.
//
// Validates `ai-dev/design/nop-lint/12-check-scripts-migration-manifest.md`:
//   - enum-set  : the manifest's ledger rows enumerate EXACTLY the live
//                 ai-dev/tools/check-*.mjs set — an added, deleted or renamed
//                 script without a manifest row (and vice versa) is a hard error
//   - rows      : every ledger row carries all required fields (script name,
//                 sub-rule count, category, capability mapping, dependency item,
//                 switchover gate, decommission action) and a legal status value
//   - summary   : the classification summary line in the manifest matches the
//                 computed per-status counts
//   - self-test : positive control — feeds known-bad fixtures to every checker
//                 and proves each one REJECTS (no silent skip, Rule #24)
//
// Usage:
//   node ai-dev/tools/check-lint-migration-manifest.mjs            (default: enum-set + rows + summary)
//   node ai-dev/tools/check-lint-migration-manifest.mjs self-test  (positive control only)
//
// Exit codes: 0 = all green, 1 = violations found (details printed), 2 = usage error.

import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = import.meta.dirname;
const PROJECT_ROOT = resolve(TOOL_DIR, '..', '..');
const MANIFEST_FILE = join(PROJECT_ROOT, 'ai-dev', 'design', 'nop-lint',
  '12-check-scripts-migration-manifest.md');
const TOOLS_DIR = join(PROJECT_ROOT, 'ai-dev', 'tools');

const STATUS_VOCAB = new Set([
  'maintain-mjs',              // the mjs gate stays (with an recorded reason)
  'exclude',                   // not a code check / not a lint concern
  'migrated-pending-switchover', // the nop-lint equivalent rule landed; switchover pending
  'candidate',                 // migration scheduled, dependency item done
  'deferred',                  // deferred, dependency item not done or unscheduled
]);

const LEDGER_HEADER = '## 逐脚本账本';

function fail(errors, message) {
  errors.push(message);
}

// ---------------------------------------------------------------------------
// Manifest parsing

export function parseLedger(manifestText) {
  const lines = manifestText.split('\n');
  const startIndex = lines.findIndex((line) => line.trim().startsWith('## 逐脚本账本'));
  if (startIndex < 0) {
    return { error: `manifest missing the "${LEDGER_HEADER}" section` };
  }

  const rows = [];
  let headerSeen = false;
  let columnCount = 0;
  for (let i = startIndex + 1; i < lines.length; i++) {
    const line = lines[i];
    if (line.trim().startsWith('## ') && rows.length > 0) {
      break; // next section
    }
    if (!line.trim().startsWith('|')) {
      if (rows.length > 0) {
        break; // table ended
      }
      continue;
    }
    const cells = line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim());
    if (!headerSeen) {
      if (cells[1] === '脚本') {
        headerSeen = true;
        columnCount = cells.length;
      }
      continue;
    }
    if (/^-+$/.test(cells[0])) {
      continue; // alignment row
    }
    rows.push({ line: i + 1, cells, columnCount });
  }
  if (!headerSeen) {
    return { error: `manifest "${LEDGER_HEADER}" section has no header row` };
  }
  return { rows, columnCount };
}

function cellText(cell) {
  return cell.replace(/`/g, '').replace(/\*\*/g, '').trim();
}

// ---------------------------------------------------------------------------
// Checkers (each returns an array of violation strings for the given input)

export function checkRows(rows, columnCount) {
  const errors = [];
  const seenScripts = new Map();
  for (const row of rows) {
    const where = `manifest line ${row.line}`;
    if (row.cells.length !== columnCount) {
      fail(errors, `${where}: expected ${columnCount} cells, found ${row.cells.length}`);
      continue;
    }
    const script = cellText(row.cells[1]);
    if (!/^check-[a-z0-9-]+\.mjs$/.test(script)) {
      fail(errors, `${where}: script name '${script}' does not match check-*.mjs`);
      continue;
    }
    if (seenScripts.has(script)) {
      fail(errors, `${where}: duplicate ledger row for '${script}' (first at line ${seenScripts.get(script)})`);
    }
    seenScripts.set(script, row.line);

    // 子规则数 / 类别 / 目标映射 / 依赖 item / switchover 门禁 / decommission 动作 / 状态
    const subRules = cellText(row.cells[2]);
    if (!subRules || subRules === '—') {
      fail(errors, `${where}: '${script}' is missing its sub-rule count`);
    }
    const category = cellText(row.cells[3]);
    if (!category) {
      fail(errors, `${where}: '${script}' is missing its category`);
    }
    const mapping = cellText(row.cells[4]);
    if (!mapping) {
      fail(errors, `${where}: '${script}' is missing its capability mapping`);
    }
    const dependency = cellText(row.cells[5]);
    if (!dependency) {
      fail(errors, `${where}: '${script}' is missing its dependency item (use '—' when none)`);
    } else if (!/^—/.test(dependency) && !/item \d+/.test(dependency)) {
      fail(errors, `${where}: '${script}' dependency '${dependency}' is neither '—' nor an item reference`);
    }
    const gate = cellText(row.cells[6]);
    if (!gate) {
      fail(errors, `${where}: '${script}' is missing its switchover gate`);
    }
    const action = cellText(row.cells[7]);
    if (!action) {
      fail(errors, `${where}: '${script}' is missing its decommission action`);
    }
    const status = cellText(row.cells[8]);
    if (!STATUS_VOCAB.has(status)) {
      fail(errors, `${where}: '${script}' has illegal status '${status}' (legal: ${[...STATUS_VOCAB].join(', ')})`);
    }
  }
  return errors;
}

export function checkEnumSet(rows, liveScripts) {
  const errors = [];
  const manifestScripts = new Set(rows.map((row) => cellText(row.cells[1])));
  for (const script of liveScripts) {
    if (!manifestScripts.has(script)) {
      fail(errors, `live script '${script}' has no manifest ledger row (added or renamed without bookkeeping?)`);
    }
  }
  for (const script of manifestScripts) {
    if (!liveScripts.includes(script)) {
      fail(errors, `manifest row '${script}' does not exist under ai-dev/tools (deleted or renamed without bookkeeping?)`);
    }
  }
  return errors;
}

export function checkSummary(manifestText, rows) {
  const errors = [];
  const counts = {};
  for (const row of rows) {
    const status = cellText(row.cells[8]);
    counts[status] = (counts[status] ?? 0) + 1;
  }
  const line = manifestText.split('\n').find((l) => l.startsWith('**分类汇总**'));
  if (!line) {
    fail(errors, 'manifest is missing the **分类汇总** summary line');
    return errors;
  }
  const expected = Object.entries(counts).map(([status, count]) => `\`${status}\` ${count}`).join(' + ');
  const expectedTotal = rows.length;
  if (!line.includes(`= **${expectedTotal}**`)) {
    fail(errors, `summary total is not ${expectedTotal}: ${line.trim()}`);
  }
  for (const [status, count] of Object.entries(counts)) {
    if (!line.includes(`\`${status}\` ${count}`)) {
      fail(errors, `summary line does not account for ${count} × '${status}'`);
    }
  }
  return errors;
}

// ---------------------------------------------------------------------------
// self-test: every checker must REJECT its known-bad fixture (Rule #24)

export function selfTest() {
  const errors = [];
  const goodRow = {
    line: 2,
    columnCount: 9,
    cells: ['1', '`check-demo.mjs`', '3', 'Java 源码', '维持 mjs', '—', '不适用', '保留', 'maintain-mjs'],
  };
  const manifestFixture = [
    '# fixture',
    '**分类汇总**（防腐门禁核对口径）：`maintain-mjs` 1 = **1**。',
  ].join('\n');

  // rows checker must reject each field violation
  const badRows = [
    ['wrong column count', { ...goodRow, cells: goodRow.cells.slice(0, 8) }],
    ['bad script name', { ...goodRow, cells: ['1', 'demo-script', ...goodRow.cells.slice(2)] }],
    ['missing sub-rule count', { ...goodRow, cells: [...goodRow.cells.slice(0, 2), '—', ...goodRow.cells.slice(3)] }],
    ['missing category', { ...goodRow, cells: [...goodRow.cells.slice(0, 3), '', ...goodRow.cells.slice(4)] }],
    ['bad dependency', { ...goodRow, cells: [...goodRow.cells.slice(0, 5), 'somewhere', ...goodRow.cells.slice(6)] }],
    ['illegal status', { ...goodRow, cells: [...goodRow.cells.slice(0, 8), 'done-because-why-not'] }],
  ];
  for (const [name, row] of badRows) {
    const violations = checkRows([row], 9);
    if (violations.length === 0) {
      fail(errors, `self-test: rows checker accepted '${name}' fixture — it is not guarding`);
    }
  }

  // enum-set checker must reject missing and extra scripts
  if (checkEnumSet([goodRow], ['check-demo.mjs', 'check-other.mjs']).length === 0) {
    fail(errors, 'self-test: enum-set checker accepted an unmanifested live script');
  }
  if (checkEnumSet([goodRow, { ...goodRow, cells: ['2', '`check-gone.mjs`', ...goodRow.cells.slice(2)] }],
    ['check-demo.mjs']).length === 0) {
    fail(errors, 'self-test: enum-set checker accepted a manifest row for a deleted script');
  }

  // summary checker must reject a wrong total and a missing status
  if (checkSummary(manifestFixture.replace('= **1**', '= **2**'), [goodRow]).length === 0) {
    fail(errors, 'self-test: summary checker accepted a wrong total');
  }
  if (checkSummary(manifestFixture.replace('`maintain-mjs` 1', '`maintain-mjs` 2'), [goodRow]).length === 0) {
    fail(errors, 'self-test: summary checker accepted a wrong status count');
  }

  if (errors.length > 0) {
    console.error('self-test FAILED (the gate is not guarding):');
    for (const error of errors) {
      console.error('  - ' + error);
    }
    return false;
  }
  console.log('self-test ok: all checkers reject their known-bad fixtures');
  return true;
}

// ---------------------------------------------------------------------------

function main() {
  const args = process.argv.slice(2);
  if (args.includes('--help') || args.includes('-h')) {
    console.log('usage: node check-lint-migration-manifest.mjs [self-test]');
    process.exit(0);
  }
  if (args[0] === 'self-test') {
    process.exit(selfTest() ? 0 : 1);
  }
  if (args.length > 0) {
    console.error(`unknown argument '${args[0]}'`);
    process.exit(2);
  }

  const errors = [];
  const manifestText = readFileSync(MANIFEST_FILE, 'utf8');
  const parsed = parseLedger(manifestText);
  if (parsed.error) {
    console.error(`manifest gate FAILED: ${parsed.error}`);
    process.exit(1);
  }

  // the gate itself matches check-*.mjs but is a new tool, not one of the
  // 24 legacy migration targets the ledger enumerates
  const GATE_SCRIPT = 'check-lint-migration-manifest.mjs';
  const liveScripts = readdirSync(TOOLS_DIR)
    .filter((name) => /^check-[a-z0-9-]+\.mjs$/.test(name) && name !== GATE_SCRIPT)
    .sort();

  errors.push(...checkRows(parsed.rows, parsed.columnCount));
  errors.push(...checkEnumSet(parsed.rows, liveScripts));
  errors.push(...checkSummary(manifestText, parsed.rows));

  if (errors.length > 0) {
    console.error(`manifest gate FAILED (${errors.length} violation(s)):`);
    for (const error of errors) {
      console.error('  - ' + error);
    }
    process.exit(1);
  }
  console.log(`manifest gate ok: ${parsed.rows.length} ledger rows == ${liveScripts.length} live check-*.mjs scripts, all fields complete`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main();
}
