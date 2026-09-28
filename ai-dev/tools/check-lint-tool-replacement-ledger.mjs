#!/usr/bin/env node
// check-lint-tool-replacement-ledger.mjs
//
// Anti-corruption gate for the tool-replacement ledger (roadmap item 1,
// plan nop-lint/15). Style precedent: check-lint-migration-manifest.mjs.
//
// Validates `nop-lint/docs/tool-replacement-ledger.md`:
//   - verdict table : the tool-level verdict table enumerates EXACTLY the
//                     eight pinned tools (added/renamed/dropped rows are hard
//                     errors), every verdict is a legal vocabulary value
//                     (待裁 transitional state or one of the four final
//                     verdicts), and the backfill discipline is enforced:
//                     a final verdict WITHOUT an in-repo evidence anchor or
//                     with an empty residual-scope cell is a hard error
//                     (roadmap Hard constraint 3: no replacement claim
//                     without evidence)
//   - items grammar : the roadmap items cell is a bare item number or a
//                     contiguous range (N–N, en-dash)
//   - facet table   : the facet registration section exists and every facet
//                     annotation token is legal (core / out-of-purpose /
//                     out-of-principle / out-of-scope, or the 待裁 placeholder)
//   - self-test     : positive control — known-bad fixtures for every checker
//                     must be REJECTED (no silent skip, Rule #24)
//
// Usage:
//   node ai-dev/tools/check-lint-tool-replacement-ledger.mjs            (all checks)
//   node ai-dev/tools/check-lint-tool-replacement-ledger.mjs self-test  (positive control)
//
// Exit codes: 0 = green, 1 = violations, 2 = usage error.

import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = import.meta.dirname;
const PROJECT_ROOT = resolve(TOOL_DIR, '..', '..');
const LEDGER_FILE = join(PROJECT_ROOT, 'nop-lint', 'docs', 'tool-replacement-ledger.md');

// the eight pinned tool rows — exact cell strings, no more, no less
const PINNED_TOOLS = [
  'Checkstyle 10.21.1',
  'PMD 7.26.0',
  'check-\\*.mjs ×24',
  'SpotBugs 4.9.8.3',
  'SonarQube',
  'ErrorProne（未接线）',
  'NullAway / 空类型系统族',
  'ArchUnit',
];

const VERDICT_VOCAB = new Set([
  '待裁',                 // transitional: not yet adjudicated (allowed pre-wave)
  'core-face-replaced',  // core defect-discovery face carried by nop-lint
  'replaced-partial',    // core face partially carried, per-row attribution
  'keep-tool',           // core face mostly out of a per-file source engine's domain
  'out-of-scope',        // not a lint concern (coverage / CVE / formatting / git history)
]);

const FACET_VOCAB = new Set([
  'core',
  'out-of-purpose',
  'out-of-principle',
  'out-of-scope',
]);

const FACET_PLACEHOLDER = '待裁';
const ITEMS_GRAMMAR = /^\d+(–\d+)?$/; // bare item number or contiguous range (en-dash)

// rule-level facet table (roadmap item 2): RULE_FACET_VOCAB constrains the 分面 column,
// RULE_DISPOSITION_VOCAB the 处置 column. The census constant is a plan-period value
// (plan nop-lint/16): when a future plan adds/removes production rules it MUST bump
// RULE_FACET_CENSUS alongside TestProductionRuleCount and the catalog generator.
const RULE_FACET_VOCAB = new Set(['core', 'out-of-purpose']);
const RULE_DISPOSITION_VOCAB = new Set(['keep', 'demote-info', 'remove']);
const RULE_FACET_CENSUS = 73; // 69 live (67+2 item5 XNode) + 4 removed = 73 rows
const RULES_ROOT = join(PROJECT_ROOT, 'nop-lint', 'nop-lint-nop', 'src', 'main',
  'resources', '_vfs', 'nop', 'lint', 'rules');

function fail(errors, message) {
  errors.push(message);
}

// ---------------------------------------------------------------------------
// table parsing (anchor: the second header cell, unique per table)

export function parseLedgerTable(ledgerText, secondHeaderCell, firstHeaderCell = '工具') {
  const lines = ledgerText.split('\n');
  let headerIndex = -1;
  let columnCount = 0;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line.startsWith('|')) continue;
    const cells = line.replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim());
    if (cells[0] === firstHeaderCell && cells[1] === secondHeaderCell) {
      headerIndex = i;
      columnCount = cells.length;
      break;
    }
  }
  if (headerIndex < 0) {
    return { error: `ledger has no tool table with header cells (工具, ${secondHeaderCell})` };
  }
  const rows = [];
  for (let i = headerIndex + 1; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line.startsWith('|')) break;
    const cells = line.replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim());
    if (/^-+$/.test(cells[0])) continue; // alignment row
    rows.push({ line: i + 1, cells, columnCount });
  }
  if (rows.length === 0) {
    return { error: `ledger table (工具, ${secondHeaderCell}) has no data rows` };
  }
  return { rows, columnCount };
}

// ---------------------------------------------------------------------------

export function checkPinnedToolRows(rows, columnCount, where) {
  const errors = [];
  const seen = new Map();
  for (const row of rows) {
    const at = `${where} line ${row.line}`;
    if (row.cells.length !== columnCount) {
      fail(errors, `${at}: expected ${columnCount} cells, found ${row.cells.length}`
        + ` (bare '|' inside a cell breaks the column layout — writing discipline violation)`);
      continue;
    }
    const tool = row.cells[0];
    if (seen.has(tool)) {
      fail(errors, `${at}: duplicate tool row '${tool}' (first at line ${seen.get(tool)})`);
    }
    seen.set(tool, row.line);
  }
  for (const tool of PINNED_TOOLS) {
    if (!seen.has(tool)) {
      fail(errors, `${where}: pinned tool row '${tool}' is missing`);
    }
  }
  for (const tool of seen.keys()) {
    if (!PINNED_TOOLS.includes(tool)) {
      fail(errors, `${where}: unknown tool row '${tool}' (pinned set has exactly ${PINNED_TOOLS.length} tools)`);
    }
  }
  return errors;
}

export function checkVerdictRows(rows) {
  const errors = checkPinnedToolRows(rows, 5, 'verdict table');
  for (const row of rows) {
    if (row.cells.length !== 5) continue;
    const tool = row.cells[0];
    const at = `verdict table line ${row.line} ('${tool}')`;
    const verdict = row.cells[1];
    if (!VERDICT_VOCAB.has(verdict)) {
      fail(errors, `${at}: illegal verdict '${verdict}' (legal: ${[...VERDICT_VOCAB].join(', ')})`);
      continue;
    }
    if (verdict !== '待裁') {
      const evidence = row.cells[3];
      if (!/\]\((\.\/|\.\.\/)/.test(evidence)) {
        fail(errors, `${at}: final verdict '${verdict}' without an in-repo evidence anchor link`
          + ` in the 证据 column — no verdict without evidence (roadmap Hard constraint 3)`);
      }
      const residual = row.cells[2];
      if (!residual || residual === '—') {
        fail(errors, `${at}: final verdict '${verdict}' with an empty 残余范围 cell`
          + ` (expected a delimited scope list, or 全工具 for out-of-scope rows)`);
      }
    }
    const items = row.cells[4];
    if (!ITEMS_GRAMMAR.test(items)) {
      fail(errors, `${at}: roadmap items cell '${items}' does not match the grammar`
        + ` N or N–N (contiguous range, en-dash)`);
    }
  }
  return errors;
}

export function checkFacetRows(rows) {
  const errors = checkPinnedToolRows(rows, 5, 'facet table');
  for (const row of rows) {
    if (row.cells.length !== 5) continue;
    const tool = row.cells[0];
    const annotation = row.cells[1];
    if (annotation === FACET_PLACEHOLDER) continue;
    const tokens = annotation.split('/').map((t) => t.trim()).filter((t) => t.length > 0);
    if (tokens.length === 0) {
      fail(errors, `facet table line ${row.line} ('${tool}'): empty facet annotation`);
      continue;
    }
    for (const token of tokens) {
      if (!FACET_VOCAB.has(token)) {
        fail(errors, `facet table line ${row.line} ('${tool}'): illegal facet token '${token}'`
          + ` (legal: ${[...FACET_VOCAB].join(', ')}, or placeholder ${FACET_PLACEHOLDER})`);
      }
    }
  }
  return errors;
}

// live production rule ids (the id: field of every *.rule.yml under the rules tree)
export function scanLiveRuleIds(rulesRoot) {
  const ids = new Set();
  const walk = (dir) => {
    const entries = readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith('.rule.yml')) {
        const m = readFileSync(full, 'utf8').match(/^id:\s*(\S+)\s*$/m);
        if (m) ids.add(m[1]);
      }
    }
  };
  walk(rulesRoot);
  return ids;
}

/**
 * The rule-level facet table (roadmap item 2) is a post-disposition invariant:
 * it is only consistent once the remove dispositions have actually landed
 * (delete the rules first, then let this checker gate the ledger).
 * Bidirectional consistency with the live rule set:
 *   - every live rule id has EXACTLY one row (no missing rows)
 *   - a non-remove row's id must exist live (no ghost rows)
 *   - a remove row's id must NOT exist live (no stale remove marks)
 *   - row count = live count + remove rows = the plan-period census constant
 */
export function checkRuleFacetRows(rows, liveRuleIds, census = RULE_FACET_CENSUS) {
  const errors = [];
  const seen = new Map();
  const removeIds = new Set();
  for (const row of rows) {
    const at = `rule facet table line ${row.line}`;
    if (row.cells.length !== 5) {
      fail(errors, `${at}: expected 5 cells, found ${row.cells.length}`
        + ` (bare '|' inside a cell breaks the column layout — writing discipline violation)`);
      continue;
    }
    const id = row.cells[0];
    if (seen.has(id)) {
      fail(errors, `${at}: duplicate row for rule '${id}' (first at line ${seen.get(id)})`);
    }
    seen.set(id, row.line);
    const facet = row.cells[1];
    if (!RULE_FACET_VOCAB.has(facet)) {
      fail(errors, `${at}: illegal facet '${facet}' (legal: ${[...RULE_FACET_VOCAB].join(', ')})`);
    }
    const disposition = row.cells[2];
    if (!RULE_DISPOSITION_VOCAB.has(disposition)) {
      fail(errors, `${at}: illegal disposition '${disposition}'`
        + ` (legal: ${[...RULE_DISPOSITION_VOCAB].join(', ')})`);
    }
    const live = liveRuleIds.has(id);
    if (disposition === 'remove') {
      removeIds.add(id);
      if (live) {
        fail(errors, `${at}: remove row for '${id}' but the rule still exists in the rules tree`
          + ` (stale remove mark — finish or revert the removal)`);
      }
      if (!row.cells[4] || row.cells[4] === '—') {
        fail(errors, `${at}: remove row for '${id}' without a re-evaluation trigger`
          + ' (Hard constraint 3: the removal record keeps its evidence chain)');
      }
    } else if (!live) {
      fail(errors, `${at}: row for '${id}' (disposition '${disposition}') but no such rule`
        + ' exists in the rules tree (ghost row — removed without bookkeeping?)');
    }
  }
  for (const id of liveRuleIds) {
    if (!seen.has(id)) {
      fail(errors, `rule facet table: live rule '${id}' has no facet row (missing bookkeeping)`);
    }
  }
  const expectedRows = liveRuleIds.size + removeIds.size;
  if (expectedRows !== census) {
    fail(errors, `rule facet table census drift: live (${liveRuleIds.size}) + remove rows`
      + ` (${removeIds.size}) = ${expectedRows}, expected ${census}`
      + ' (bump RULE_FACET_CENSUS when a plan changes the production library)');
  }
  return errors;
}

// ---------------------------------------------------------------------------
// self-test: every checker must REJECT its known-bad fixture (Rule #24)

function goodVerdictRows() {
  return PINNED_TOOLS.map((tool, i) => ({
    line: i + 2,
    cells: [tool, '待裁', '—', '—', '3'],
    columnCount: 5,
  }));
}

function goodRuleFacetRows() {
  return [
    { line: 2, cells: ['antipattern/catch-npe', 'core', 'keep', 'r', '—'], columnCount: 5 },
    { line: 3, cells: ['quality/no-self-compare', 'core', 'keep', 'r', '—'], columnCount: 5 },
    { line: 4, cells: ['quality/gone-rule', 'out-of-purpose', 'remove', 'r', '风格面入 mandate'], columnCount: 5 },
  ];
}

function goodFacetRows() {
  return PINNED_TOOLS.map((tool, i) => ({
    line: i + 2,
    cells: [tool, '待裁', '—', '—', '待裁'],
    columnCount: 5,
  }));
}

export function selfTest() {
  const errors = [];

  // positive baseline: the pinned scaffolds must be ACCEPTED
  if (checkVerdictRows(goodVerdictRows()).length !== 0) {
    fail(errors, 'self-test: verdict checker rejected the legal pinned scaffold');
  }
  if (checkFacetRows(goodFacetRows()).length !== 0) {
    fail(errors, 'self-test: facet checker rejected the legal pinned scaffold');
  }

  const mutateVerdict = (toolIndex, cellIndex, value) => {
    const rows = goodVerdictRows();
    rows[toolIndex].cells[cellIndex] = value;
    return rows;
  };

  // verdict checker must reject each known-bad mutation (row 0 = Checkstyle)
  const badVerdicts = [
    ['illegal verdict value', mutateVerdict(0, 1, 'replaced')],
    ['final verdict without evidence anchor', mutateVerdict(0, 1, 'core-face-replaced')],
    ['final verdict with empty residual scope', mutateVerdict(1, 1, 'keep-tool')],
    ['illegal items grammar (prefixed)', mutateVerdict(2, 4, 'item 5')],
    ['illegal items grammar (ascii dash)', mutateVerdict(3, 4, '9-11')],
    ['illegal items grammar (non-contiguous)', mutateVerdict(4, 4, '12, 14')],
  ];
  for (const [name, rows] of badVerdicts) {
    if (checkVerdictRows(rows).length === 0) {
      fail(errors, `self-test: verdict checker accepted '${name}' fixture — it is not guarding`);
    }
  }

  // a final verdict WITH evidence and residual scope must be accepted
  // (row 4 = SonarQube in the pinned order)
  const legalFinal = goodVerdictRows();
  legalFinal[4].cells = ['SonarQube', 'out-of-scope', '覆盖率面 / 工作流面',
    '[对照记录](./checkstyle-pmd-migration.md)', '12–14'];
  if (checkVerdictRows(legalFinal).length !== 0) {
    fail(errors, 'self-test: verdict checker rejected a legal evidenced final verdict');
  }

  // facet checker must reject an illegal token
  const badFacet = goodFacetRows();
  badFacet[6].cells[1] = 'optional';
  if (checkFacetRows(badFacet).length === 0) {
    fail(errors, "self-test: facet checker accepted the illegal token 'optional' — it is not guarding");
  }
  // facet checker must reject an empty annotation
  const emptyFacet = goodFacetRows();
  emptyFacet[6].cells[1] = '—';
  if (checkFacetRows(emptyFacet).length === 0) {
    fail(errors, 'self-test: facet checker accepted an empty facet annotation');
  }
  // facet checker must accept a legal multi-token annotation
  const legalFacet = goodFacetRows();
  legalFacet[0].cells[1] = 'core / out-of-purpose';
  if (checkFacetRows(legalFacet).length !== 0) {
    fail(errors, 'self-test: facet checker rejected a legal multi-token facet annotation');
  }

  // pinned-set guard: dropped / extra / renamed rows must be rejected
  const dropped = goodVerdictRows().slice(1);
  if (checkVerdictRows(dropped).length === 0) {
    fail(errors, 'self-test: pinned-set guard accepted a dropped tool row');
  }
  const extra = [...goodVerdictRows(), {
    line: 99, cells: ['FindBugs', '待裁', '—', '—', '1'], columnCount: 5,
  }];
  if (checkVerdictRows(extra).length === 0) {
    fail(errors, 'self-test: pinned-set guard accepted an unknown tool row');
  }
  const renamed = goodVerdictRows();
  renamed[7].cells[0] = 'ArchUnit-Lite';
  if (checkVerdictRows(renamed).length === 0) {
    fail(errors, 'self-test: pinned-set guard accepted a renamed tool row');
  }

  // column guard: a broken cell layout must be rejected
  const broken = goodVerdictRows();
  broken[0].cells = ['Checkstyle 10.21.1', '待裁', '—', '—'];
  if (checkVerdictRows(broken).length === 0) {
    fail(errors, 'self-test: column guard accepted a row with missing cells');
  }

  // rule facet checker: positive baseline over a synthetic live set
  const liveIds = new Set(goodRuleFacetRows().filter(r => r.cells[2] !== 'remove').map(r => r.cells[0]));
  if (checkRuleFacetRows(goodRuleFacetRows(), liveIds, 3).length !== 0) {
    fail(errors, 'self-test: rule facet checker rejected the legal scaffold');
  }
  const mutateRule = (rowIndex, cellIndex, value) => {
    const rows = goodRuleFacetRows();
    rows[rowIndex].cells[cellIndex] = value;
    return rows;
  };
  const badRules = [
    ['illegal facet', mutateRule(0, 1, 'optional')],
    ['illegal disposition', mutateRule(0, 2, 'demote-warning')],
    ['ghost id on a non-remove row', mutateRule(1, 0, 'antipattern/ghost-rule')],
    ['remove row whose rule is still live', mutateRule(0, 2, 'remove')],
  ];
  for (const [name, rows] of badRules) {
    if (checkRuleFacetRows(rows, liveIds, 3).length === 0) {
      fail(errors, `self-test: rule facet checker accepted '${name}' fixture — it is not guarding`);
    }
  }
  // a live rule with no row must be rejected (missing bookkeeping)
  const droppedRow = goodRuleFacetRows().slice(1);
  if (checkRuleFacetRows(droppedRow, liveIds, 3).length === 0) {
    fail(errors, 'self-test: rule facet checker accepted a missing live-rule row');
  }
  // census drift must be rejected
  if (checkRuleFacetRows(goodRuleFacetRows(), new Set([...liveIds, 'quality/extra-rule']), 3).length === 0) {
    fail(errors, 'self-test: rule facet checker accepted a census drift');
  }

  // table parser must reject a missing table
  if (!parseLedgerTable('# ledger\n\nno tables here\n', '终裁').rows
    && !parseLedgerTable('# ledger\n\nno tables here\n', '终裁').error) {
    fail(errors, 'self-test: table parser returned neither rows nor an error for a missing table');
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
    console.log('usage: node check-lint-tool-replacement-ledger.mjs [self-test]');
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
  const ledgerText = readFileSync(LEDGER_FILE, 'utf8');

  const verdict = parseLedgerTable(ledgerText, '终裁');
  if (verdict.error) {
    console.error(`tool replacement ledger gate FAILED: ${verdict.error}`);
    process.exit(1);
  }
  const facet = parseLedgerTable(ledgerText, '分面标注');
  if (facet.error) {
    console.error(`tool replacement ledger gate FAILED: ${facet.error}`);
    process.exit(1);
  }

  const ruleFacet = parseLedgerTable(ledgerText, '分面', '规则');
  if (ruleFacet.error) {
    console.error(`tool replacement ledger gate FAILED: ${ruleFacet.error}`);
    process.exit(1);
  }
  const liveRuleIds = scanLiveRuleIds(RULES_ROOT);

  errors.push(...checkVerdictRows(verdict.rows));
  errors.push(...checkFacetRows(facet.rows));
  errors.push(...checkRuleFacetRows(ruleFacet.rows, liveRuleIds));

  if (errors.length > 0) {
    console.error(`tool replacement ledger gate FAILED (${errors.length} violation(s)):`);
    for (const error of errors) {
      console.error('  - ' + error);
    }
    process.exit(1);
  }
  console.log(`tool replacement ledger gate ok: ${verdict.rows.length} verdict rows`
    + ` + ${facet.rows.length} facet rows == ${PINNED_TOOLS.length} pinned tools;`
    + ` ${ruleFacet.rows.length} rule facet rows vs ${liveRuleIds.size} live rules, all values legal`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main();
}
