#!/usr/bin/env node
// check-lint-tool-migration-mapping.mjs
//
// Anti-drift gate for the checkstyle/pmd → nop-lint migration mapping
// (roadmap item 40, plan 2026-09-24-2350-1). Validates
// `nop-lint/docs/checkstyle-pmd-migration.md` §2 against the LIVE tool
// configs and rule library:
//   - enum-set  : the mapping row set ≡ the ACTIVE rule set parsed from
//                 checkstyle.xml + pmd-ruleset.xml (TreeWalker/Checker
//                 modules and <rule ref> entries; severity=ignore
//                 excluded) — a config row without a mapping row is drift,
//                 a mapping row without a config row is a ghost
//   - vocab     : every row's status is exactly one of landed /
//                 keep-checkstyle / keep-pmd / deferred (no "pending" —
//                 the gate enforces the no-TBD rule)
//   - landed    : every landed target id exists as the `id:` field of a
//                 rule file under the nop-lint-nop rules tree (id-field
//                 resolution, NOT path derivation — six rules deliberately
//                 live at paths that differ from their ids)
//   - self-test : positive control — a table with a dropped row, a bad
//                 status word, and a landed id pointing nowhere must each
//                 be REJECTED
//
// Usage:
//   node ai-dev/tools/check-lint-tool-migration-mapping.mjs            (all checks)
//   node ai-dev/tools/check-lint-tool-migration-mapping.mjs self-test  (positive control)
//
// Exit codes: 0 = green, 1 = violations, 2 = usage error.

import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = import.meta.dirname;
const PROJECT_ROOT = resolve(TOOL_DIR, '..', '..');
const DOC_FILE = join(PROJECT_ROOT, 'nop-lint', 'docs', 'checkstyle-pmd-migration.md');
const CHECKSTYLE_FILE = join(PROJECT_ROOT, 'checkstyle.xml');
const PMD_FILE = join(PROJECT_ROOT, 'pmd-ruleset.xml');
const RULES_ROOT = join(PROJECT_ROOT, 'nop-lint', 'nop-lint-nop', 'src', 'main',
  'resources', '_vfs', 'nop', 'lint', 'rules');

const STATUS_VOCAB = new Set(['landed', 'keep-checkstyle', 'keep-pmd', 'deferred',
  // facet-review disposition (tool-replacement roadmap item 3a): a style/optional
  // face row is archived as out-of-purpose — not a migration debt; reason required
  'out-of-purpose']);

function fail(errors, message) {
  errors.push(message);
}

// ---------------------------------------------------------------------------
// live tool config parsing (the enumeration authority)

export function parseCheckstyleRules(xml) {
  // active modules under <module name="TreeWalker"> plus the Checker-level
  // RegexpSingleline; severity="ignore" rows are excluded (not enforced)
  const rules = [];
  const treeWalkerStart = xml.indexOf('<module name="TreeWalker"');
  const treeWalkerEnd = xml.lastIndexOf('</module>');
  const walkerBlock = treeWalkerStart >= 0 ? xml.slice(treeWalkerStart, treeWalkerEnd) : xml;
  const outside = treeWalkerStart >= 0 ? xml.slice(0, treeWalkerStart) : xml;

  for (const m of outside.matchAll(/<module name="([^"]+)"\s*\/?>/g)) {
    if (m[1] === 'Checker' || m[1] === 'TreeWalker' || /Property|metadata/.test(m[1])) continue;
    if (!rules.includes('checkstyle:' + m[1])) rules.push('checkstyle:' + m[1]);
  }
  for (const blockMatch of walkerBlock.split('<module name="').slice(1)) {
    const name = blockMatch.match(/^([^"]+)"/)?.[1];
    if (!name || name === 'Checker' || name === 'TreeWalker') continue;
    const block = blockMatch.slice(0, blockMatch.indexOf('</module>') >= 0
      ? blockMatch.indexOf('</module>') : undefined);
    if (/severity\s*=\s*"ignore"/.test(block)) continue;
    if (!rules.includes('checkstyle:' + name)) rules.push('checkstyle:' + name);
  }
  return rules;
}

export function parsePmdRules(xml) {
  // the LAST path segment of the ref is the rule name
  // (e.g. category/java/errorprone.xml/EmptyCatchBlock)
  return [...xml.matchAll(/<rule ref="[^"]*\/([^"/]+)"/g)]
    .map(m => 'pmd:' + m[1]);
}

// ---------------------------------------------------------------------------
// mapping table parsing (the gated document)

export function parseMappingRows(docText) {
  // §2 table rows: | <source-token> | <status> | <target-or-—> | <note> |
  const rows = new Map();
  let inSection = false;
  for (const rawLine of docText.split('\n')) {
    const line = rawLine.trim();
    if (line.startsWith('## ')) {
      inSection = line.startsWith('## 2.');
      continue;
    }
    if (!inSection || !line.startsWith('|')) continue;
    if (/^\|[-\s|:]+$>$/.test(line) || /^\|[-\s|:]+$/.test(line)) continue;
    const cells = line.split('|').map(c => c.trim()).filter((_, i, a) => i > 0 && i < a.length);
    if (cells.length < 3) continue;
    if (cells[0] === 'source') continue;
    rows.set(cells[0], { status: cells[1], target: cells[2], note: cells[3] ?? '' });
  }
  return rows;
}

// ---------------------------------------------------------------------------
// live rule id index (id-FIELD based, per plan M2)

export function buildRuleIdIndex(rulesRoot) {
  const index = new Map();
  const walk = dir => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith('.rule.yml')) {
        const text = readFileSync(full, 'utf8');
        const m = text.match(/^id:\s*(\S+)\s*$/m);
        if (m) index.set(m[1], full);
      }
    }
  };
  walk(rulesRoot);
  return index;
}

// ---------------------------------------------------------------------------
// checkers

function checkAll({ doc, checkstyleXml, pmdXml, ruleIndex }) {
  const errors = [];
  const rows = parseMappingRows(doc);

  // checkstyle side: after the item-3b switchover the config file is ABSENT.
  // A null sentinel (distinct from an empty parse) admits the absence ONLY
  // when every checkstyle mapping row is landed or out-of-purpose — a
  // keep-checkstyle/deferred row would mean the config was removed with
  // unmigrated rows (no silent deregistration). The pmd side keeps its
  // full bidirectional authority while pmd-ruleset.xml exists.
  const checkstyleAbsent = checkstyleXml == null;
  const pmdRules = parsePmdRules(pmdXml);

  if (checkstyleAbsent) {
    const unmigrated = [];
    for (const [source, row] of rows) {
      if (!source.startsWith('checkstyle:')) continue;
      const status = row.status.replace(/\s+/g, '');
      if (status !== 'landed' && status !== 'out-of-purpose') unmigrated.push(source);
    }
    if (unmigrated.length > 0) {
      fail(errors, `checkstyle.xml is absent but ${unmigrated.length} checkstyle mapping row(s)`
        + ` are still unmigrated (${unmigrated.slice(0, 3).join(', ')}...) — restore the config`
        + ` or migrate/archive the rows first (no silent deregistration)`);
    }
  } else {
    const checkstyleRules = parseCheckstyleRules(checkstyleXml);
    for (const rule of checkstyleRules) {
      if (!rows.has(rule)) {
        fail(errors, `config rule '${rule}' has no mapping row (drift: add it to the §2 table)`);
      }
    }
    for (const [source] of rows) {
      if (source.startsWith('checkstyle:') && !checkstyleRules.includes(source)) {
        fail(errors, `mapping row '${source}' matches no active rule in checkstyle.xml (ghost row)`);
      }
    }
  }

  for (const rule of pmdRules) {
    if (!rows.has(rule)) {
      fail(errors, `config rule '${rule}' has no mapping row (drift: add it to the §2 table)`);
    }
  }
  for (const [source] of rows) {
    if (source.startsWith('pmd:') && !pmdRules.includes(source)) {
      fail(errors, `mapping row '${source}' matches no active rule in pmd-ruleset.xml (ghost row)`);
    }
  }

  for (const [source, row] of rows) {
    const status = row.status.replace(/\s+/g, '');
    if (!STATUS_VOCAB.has(status)) {
      fail(errors, `mapping row '${source}' has illegal status '${row.status}'`
        + ` (vocabulary: ${[...STATUS_VOCAB].join(', ')})`);
      continue;
    }
    if (status === 'out-of-purpose') {
      const note = (row.note ?? '').trim();
      if (note.length === 0) {
        fail(errors, `mapping row '${source}' has status out-of-purpose without a facet reason`
          + ' (an archived row must state why it is not a migration debt)');
      }
    }
    if (status === 'landed') {
      for (const target of row.target.split('+').map(s => s.trim()).filter(s => s && s !== '—')) {
        if (!ruleIndex.has(target)) {
          fail(errors, `landed row '${source}' targets '${target}' which is not the id: field`
            + ` of any rule file under the rules tree`);
        }
      }
    }
  }
  return errors;
}

// ---------------------------------------------------------------------------

function buildLiveContext() {
  if (!existsSync(DOC_FILE)) {
    console.error(`migration document missing: ${DOC_FILE}`);
    process.exit(2);
  }
  return {
    doc: readFileSync(DOC_FILE, 'utf8'),
    // null sentinel = the config was switched over (item 3b); the absence
    // is admitted only when every checkstyle row is landed/out-of-purpose
    checkstyleXml: existsSync(CHECKSTYLE_FILE) ? readFileSync(CHECKSTYLE_FILE, 'utf8') : null,
    pmdXml: readFileSync(PMD_FILE, 'utf8'),
    ruleIndex: buildRuleIdIndex(RULES_ROOT),
  };
}

function main() {
  const mode = process.argv[2] ?? '';
  if (mode === 'self-test') {
    selfTest();
    return;
  }
  const ctx = buildLiveContext();
  const errors = checkAll(ctx);
  if (errors.length) {
    for (const e of errors) console.error('MAPPING-GATE FAIL: ' + e);
    process.exit(1);
  }
  console.log(`tool migration mapping gate ok: ${ctx.ruleIndex.size} rule ids indexed;`
    + ` mapping rows = ${parseMappingRows(ctx.doc).size}`);
}

function selfTest() {
  const ctx = buildLiveContext();
  const rows = parseMappingRows(ctx.doc);
  const sampleSources = [...rows.keys()].slice(0, 3);
  if (sampleSources.length < 3) {
    console.error('self-test needs at least 3 mapping rows');
    process.exit(2);
  }

  // control 1: a dropped row must be rejected (drift face) — anchored on a
  // pmd row because post-switchover the checkstyle side is absent-admitted
  // and its ghost check is intentionally vacuous
  let pmdSource = [...rows.keys()].find(k => k.startsWith('pmd:'));
  if (!pmdSource) pmdSource = sampleSources[0];
  const dropped = ctx.doc.split('\n')
    .filter(l => l.trim().startsWith('|') && l.includes(pmdSource)).join('\n');
  const withoutRow = ctx.doc.replace(dropped, '');
  if (checkAll({ ...ctx, doc: withoutRow }).length === 0) {
    console.error('self-test control 1 FAILED: dropped row was not detected');
    process.exit(1);
  }

  // control 5: the absent-checkstyle admission — a synthetic call with a
  // null config (post-switchover live state) must produce no absence error
  if (checkAll({ ...ctx, checkstyleXml: null }).length !== 0) {
    console.error('self-test control 5 FAILED: the absent-config admission path errored');
    process.exit(1);
  }

  // control 6: absence with an unmigrated row must be rejected — mutate one
  // checkstyle landed row back to keep-checkstyle, no real file involved
  let csSource = [...rows.keys()].find(k => k.startsWith('checkstyle:') && rows.get(k).status === 'landed');
  if (!csSource) {
    console.error('self-test control 6 FAILED: no landed checkstyle row to mutate');
    process.exit(1);
  }
  const unmigrated = ctx.doc.replace(`| ${csSource} | landed`, `| ${csSource} | keep-checkstyle`);
  const absentErrors = checkAll({ ...ctx, doc: unmigrated, checkstyleXml: null });
  if (!absentErrors.some(e => e.includes('no silent deregistration'))) {
    console.error('self-test control 6 FAILED: absent config with unmigrated rows was not rejected');
    process.exit(1);
  }

  // control 2: a bad status word must be rejected
  const badVocab = ctx.doc.replace(`| ${sampleSources[1]} | landed`,
    `| ${sampleSources[1]} | maybe-landed`)
    .replace(`| ${sampleSources[1]} | keep-checkstyle`, `| ${sampleSources[1]} | maybe-landed`)
    .replace(`| ${sampleSources[1]} | keep-pmd`, `| ${sampleSources[1]} | maybe-landed`)
    .replace(`| ${sampleSources[1]} | deferred`, `| ${sampleSources[1]} | maybe-landed`)
    .replace(`| ${sampleSources[1]} | out-of-purpose`, `| ${sampleSources[1]} | maybe-landed`);
  if (checkAll({ ...ctx, doc: badVocab }).length === 0) {
    console.error('self-test control 2 FAILED: bad status word was not detected');
    process.exit(1);
  }

  // control 3: a landed target pointing at a nonexistent id must be rejected
  let badTarget = ctx.doc;
  for (const [source, row] of rows) {
    if (row.status === 'landed') {
      badTarget = ctx.doc.replace(`| ${row.target} |`,
        `| no/such/rule-id-exists |`);
      badTarget = badTarget.replace(`| ${source} | landed | ${row.target}`,
        `| ${source} | landed | no/such/rule-id-exists`);
      break;
    }
  }
  if (checkAll({ ...ctx, doc: badTarget }).length === 0) {
    console.error('self-test control 3 FAILED: landed target to a nonexistent id was not detected');
    process.exit(1);
  }

  // control 4: an out-of-purpose row without its facet reason must be rejected
  const opRow = [...rows.entries()].find(([, row]) => row.status === 'out-of-purpose');
  if (!opRow) {
    console.error('self-test control 4 SKIPPED-FAIL: no out-of-purpose row in the live doc');
    process.exit(1);
  }
  const [opSource, opData] = opRow;
  const badReason = ctx.doc.replace(`| ${opSource} | out-of-purpose | ${opData.target} | ${opData.note} |`,
    `| ${opSource} | out-of-purpose | ${opData.target} |  |`);
  if (badReason === ctx.doc) {
    console.error('self-test control 4 FAILED: reason-blank mutation did not apply');
    process.exit(1);
  }
  if (checkAll({ ...ctx, doc: badReason }).length === 0) {
    console.error('self-test control 4 FAILED: reason-less out-of-purpose row was not detected');
    process.exit(1);
  }

  console.log('self-test ok: dropped-row / bad-vocab / bad-landed-target / reason-less-out-of-purpose / absent-admission / absent-unmigrated all REJECTED or verified');
}

main();
