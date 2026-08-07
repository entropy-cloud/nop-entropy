#!/usr/bin/env node
// check-nop-stream-audit-manifest.mjs
//
// Validator for the nop-stream independent audit "度量衡" (Stage 4):
//   - manifest : execute every manifest selection command and compare to expected denominator
//   - corpus   : check finding IDs unique, shard totals consistent, severity/domain vocabulary legal
//   - evidence : check evidence-row fields complete + disposition vocabulary legal
//   - self-test: positive control — proves each checker REJECTS known-bad input (no silent skip)
//
// Rule #24 (No Silent No-Op): a missing/unknown field or an out-of-vocabulary value is a hard error
// that exits non-zero; the validator never silently fixes or ignores.
//
// Usage:
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs manifest [--strict]
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs corpus   [--strict]
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence [--strict]
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs           (default: runs manifest+corpus+evidence)

import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = import.meta.dirname;
const PROJECT_ROOT = resolve(TOOL_DIR, '..', '..');
const AUDIT_DIR = join(PROJECT_ROOT, 'ai-dev', 'audits', 'nop-stream-independent-audit');
const MANIFEST_FILE = join(AUDIT_DIR, 'source-manifest.md');
const CORPUS_FILE = join(AUDIT_DIR, 'finding-corpus.md');
const SCHEMA_FILE = join(AUDIT_DIR, 'evidence-schema.md');

const SEVERITY_VOCAB = new Set(['P0', 'P1', 'P2', 'AR']);
const DOMAIN_VOCAB = new Set([
  'coordinator/runtime', 'checkpoint/state', 'window', 'CEP', 'connector', 'contract/test',
]);
const DISPOSITION_VOCAB = new Set([
  'e2e-proved', 'component-only', 'unverified', 'fail-fast', 'non-goal', 'residual-risk', 'blocked',
]);
const LANE_VOCAB = new Set(['unit', 'in-process', 'multi-jvm', 'none']);
const LANE_STRENGTH = { none: 0, unit: 1, 'in-process': 2, 'multi-jvm': 3 };

// ---------------------------------------------------------------------------
// Parsing helpers (shared block format: @@ENTRY / @@EVIDENCE ... @@END)
// ---------------------------------------------------------------------------

function parseBlocks(text, openMarker) {
  const blocks = [];
  const lines = text.split('\n');
  let cur = null;
  for (const line of lines) {
    if (line.trim() === openMarker) {
      cur = {};
    } else if (line.trim() === '@@END') {
      if (cur) blocks.push(cur);
      cur = null;
    } else if (cur) {
      const idx = line.indexOf(':');
      if (idx > -1 && /^[a-z_0-9]+:\s/.test(line)) {
        const key = line.slice(0, idx).trim();
        const val = line.slice(idx + 1).trim();
        cur[key] = val;
      }
    }
  }
  return blocks;
}

function kvBlockToObj(block) {
  const obj = {};
  for (const line of block.lines) {
    const idx = line.indexOf(':');
    if (idx > -1 && /^[a-z_0-9]+:\s/.test(line)) {
      obj[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
    }
  }
  return obj;
}

// ---------------------------------------------------------------------------
// MANIFEST
// ---------------------------------------------------------------------------

function parseManifest(text) {
  return parseBlocks(text, '@@ENTRY');
}

function parseIntegerOutput(stdout) {
  const matches = String(stdout).match(/-?\d+/g);
  if (!matches || matches.length === 0) return null;
  return parseInt(matches[matches.length - 1], 10);
}

function runManifestChecks(entries, { executor } = {}) {
  const run = executor || ((cmd) => {
    const out = execSync(cmd, { cwd: PROJECT_ROOT, stdio: ['pipe', 'pipe', 'pipe'], maxBuffer: 1024 * 1024 * 16 });
    return parseIntegerOutput(out);
  });
  const results = [];
  for (const e of entries) {
    const errors = [];
    if (!e.domain_id) errors.push('missing required field: domain_id');
    if (!e.command) errors.push('missing required field: command');
    if (e.expected_denominator === undefined) errors.push('missing required field: expected_denominator');
    // reject unknown fields
    const allowed = new Set(['domain_id', 'scope', 'command', 'expected_denominator', 'include', 'exclude', 'notes']);
    for (const k of Object.keys(e)) {
      if (!allowed.has(k)) errors.push(`unknown field: ${k}`);
    }
    if (e.command) {
      let actual;
      try {
        actual = run(e.command);
      } catch (err) {
        errors.push(`command failed: ${err.message.split('\n')[0]}`);
        actual = null;
      }
      if (actual === null) {
        errors.push('command produced no integer output');
      } else {
        const expected = parseInt(e.expected_denominator, 10);
        if (Number.isNaN(expected)) {
          errors.push(`expected_denominator not an integer: "${e.expected_denominator}"`);
        } else if (actual !== expected) {
          errors.push(`denominator mismatch: expected ${expected}, got ${actual}`);
        }
      }
    }
    results.push({ entry: e, errors });
  }
  return results;
}

// ---------------------------------------------------------------------------
// CORPUS
// ---------------------------------------------------------------------------

function parseCorpus(text) {
  const lines = text.split('\n');
  const shards = [];
  let cur = null;
  let topIds = null;
  for (const line of lines) {
    const shardHeader = line.match(/^##\s+Shard\s+(\d+)\b/);
    if (shardHeader) {
      if (cur) shards.push(cur);
      cur = { name: shardHeader[0].trim(), total: null, declaredIds: [], entries: [] };
      continue;
    }
    if (line.startsWith('## ') && cur) {
      shards.push(cur);
      cur = null;
    }
    if (!cur) continue;
    const totalMatch = line.match(/^- Total:\s*(\d+)\s*$/);
    if (totalMatch) cur.total = parseInt(totalMatch[1], 10);
    const idsMatch = line.match(/^- IDs:\s*(.+)$/);
    if (idsMatch) cur.declaredIds = idsMatch[1].split(',').map((s) => s.trim()).filter(Boolean);
    const entryMatch = line.match(/^- ID:\s*([^|]+)\|\s*sev:\s*([^|]+)\|\s*domain:\s*([^|]+)\|/);
    if (entryMatch) {
      cur.entries.push({
        id: entryMatch[1].trim(),
        sev: entryMatch[2].trim(),
        domain: entryMatch[3].trim(),
      });
    }
  }
  if (cur) shards.push(cur);
  return { shards };
}

function checkCorpus({ shards }) {
  const errors = [];
  if (shards.length === 0) {
    errors.push('no Shard sections found');
    return errors;
  }
  const globalIds = new Map(); // id -> shard name
  for (const s of shards) {
    if (s.total === null) {
      errors.push(`${s.name}: missing "- Total: N" line`);
    }
    // vocabulary check on entries
    for (const e of s.entries) {
      if (!SEVERITY_VOCAB.has(e.sev)) errors.push(`${s.name} ${e.id}: severity out of vocabulary: "${e.sev}"`);
      if (!DOMAIN_VOCAB.has(e.domain)) errors.push(`${s.name} ${e.id}: domain out of vocabulary: "${e.domain}"`);
      if (!e.id) errors.push(`${s.name}: entry with empty id`);
    }
    // declaredIds vs entries
    const entryIds = s.entries.map((e) => e.id);
    const declaredSet = new Set(s.declaredIds);
    const entrySet = new Set(entryIds);
    if (s.total !== null && s.total !== entryIds.length) {
      errors.push(`${s.name}: Total=${s.total} but ${entryIds.length} "- ID:" entries`);
    }
    if (s.total !== null && s.total !== s.declaredIds.length) {
      errors.push(`${s.name}: Total=${s.total} but "- IDs:" line lists ${s.declaredIds.length}`);
    }
    if (entryIds.length !== s.declaredIds.length || [...entrySet].some((id) => !declaredSet.has(id))) {
      errors.push(`${s.name}: "- IDs:" list does not match "- ID:" entries`);
    }
    // global uniqueness
    for (const id of entryIds) {
      if (globalIds.has(id)) {
        errors.push(`duplicate finding ID across shards: ${id} (in ${globalIds.get(id)} and ${s.name})`);
      } else {
        globalIds.set(id, s.name);
      }
    }
    // intra-shard uniqueness
    const intra = new Map();
    for (const id of entryIds) intra.set(id, (intra.get(id) || 0) + 1);
    for (const [id, n] of intra) {
      if (n > 1) errors.push(`${s.name}: duplicate finding ID within shard: ${id} (x${n})`);
    }
  }
  return errors;
}

// ---------------------------------------------------------------------------
// EVIDENCE
// ---------------------------------------------------------------------------

const EVIDENCE_REQUIRED = [
  'inventory_id', 'source_anchor', 'declared_guarantee', 'implementation_anchor',
  'runtime_wiring', 'positive_proof', 'rejection_proof',
  'environment_class', 'required_lane', 'finding_id', 'disposition',
];
const EVIDENCE_ALLOWED = new Set(EVIDENCE_REQUIRED);
const RUNTIME_WIRING_VOCAB = new Set(['wired', 'unwired', 'partial']);

function parseEvidence(text) {
  return parseBlocks(text, '@@EVIDENCE');
}

function checkEvidenceRow(row) {
  const errors = [];
  for (const f of EVIDENCE_REQUIRED) {
    if (row[f] === undefined || row[f] === '') errors.push(`missing required field: ${f}`);
  }
  for (const k of Object.keys(row)) {
    if (!EVIDENCE_ALLOWED.has(k)) errors.push(`unknown field: ${k}`);
  }
  if (row.disposition !== undefined && !DISPOSITION_VOCAB.has(row.disposition)) {
    errors.push(`disposition out of vocabulary: "${row.disposition}"`);
  }
  if (row.runtime_wiring !== undefined && !RUNTIME_WIRING_VOCAB.has(row.runtime_wiring)) {
    errors.push(`runtime_wiring out of vocabulary: "${row.runtime_wiring}"`);
  }
  if (row.environment_class !== undefined && !LANE_VOCAB.has(row.environment_class)) {
    errors.push(`environment_class out of vocabulary: "${row.environment_class}"`);
  }
  if (row.required_lane !== undefined && !LANE_VOCAB.has(row.required_lane)) {
    errors.push(`required_lane out of vocabulary: "${row.required_lane}"`);
  }
  // consistency: e2e-proved requires environment_class strength >= required_lane
  if (
    row.disposition === 'e2e-proved' &&
    LANE_VOCAB.has(row.environment_class) &&
    LANE_VOCAB.has(row.required_lane) &&
    LANE_STRENGTH[row.environment_class] < LANE_STRENGTH[row.required_lane]
  ) {
    errors.push(`disposition e2e-proved invalid: environment_class(${row.environment_class}) < required_lane(${row.required_lane})`);
  }
  return errors;
}

function checkEvidenceRows(rows) {
  const errors = [];
  const seen = new Set();
  for (const r of rows) {
    const rowErrs = checkEvidenceRow(r);
    for (const err of rowErrs) errors.push(`${r.inventory_id || '<no-id>'}: ${err}`);
    if (r.inventory_id) {
      if (seen.has(r.inventory_id)) errors.push(`${r.inventory_id}: duplicate inventory_id`);
      seen.add(r.inventory_id);
    }
  }
  return errors;
}

function collectEvidenceRows() {
  // Real evidence rows live in *.evidence.md files under the audit dir (none yet at Stage 4).
  const rows = [];
  if (!existsSync(AUDIT_DIR)) return rows;
  for (const name of readdirSync(AUDIT_DIR, { withFileTypes: true })) {
    if (name.isFile() && name.name.endsWith('.evidence.md')) {
      const text = readFileSync(join(AUDIT_DIR, name.name), 'utf-8');
      rows.push(...parseEvidence(text));
    }
  }
  return rows;
}

// ---------------------------------------------------------------------------
// SELF-TEST (positive control)
// ---------------------------------------------------------------------------

function runSelfTest() {
  const failures = [];

  // --- Manifest positive control: denominator mismatch must be rejected.
  const fakeEntries = [
    { domain_id: 'good', command: 'echo 5', expected_denominator: '5' },
    { domain_id: 'bad-denominator', command: 'echo 5', expected_denominator: '999' }, // should flag
    { domain_id: 'bad-missing-field', command: 'echo 5' }, // missing expected_denominator -> flag
    { domain_id: 'bad-unknown-field', command: 'echo 5', expected_denominator: '5', bogus: 'x' }, // unknown field -> flag
  ];
  // inject executor that always returns 5 (so we test comparison, not the shell)
  const mRes = runManifestChecks(fakeEntries, { executor: () => 5 });
  const mGood = mRes[0];
  const mBad = mRes.slice(1);
  if (mGood.errors.length !== 0) failures.push(`manifest positive control: good entry wrongly rejected: ${mGood.errors.join('; ')}`);
  if (!mBad.every((r) => r.errors.length > 0)) {
    failures.push('manifest positive control: known-bad entries were NOT all rejected (validator would silently pass bad denominators)');
  }

  // --- Corpus positive control: duplicate ID + Total mismatch must be rejected.
  const badCorpus = {
    shards: [
      {
        name: '## Shard X',
        total: 2,
        declaredIds: ['A-1', 'A-2'],
        entries: [
          { id: 'A-1', sev: 'P0', domain: 'CEP' },
          { id: 'A-1', sev: 'P1', domain: 'bad-domain' }, // duplicate id + bad domain + bad sev
        ],
      },
      {
        name: '## Shard Y',
        total: 5, // mismatch: 1 entry
        declaredIds: ['B-1', 'B-2'], // mismatch with entries
        entries: [{ id: 'A-1', sev: 'P2', domain: 'window' }], // global duplicate
      },
    ],
  };
  const cErrs = checkCorpus(badCorpus);
  if (cErrs.length === 0) failures.push('corpus positive control: known-bad corpus was NOT rejected');

  // --- Evidence positive control: missing field + out-of-vocab disposition must be rejected.
  const badRow = {
    inventory_id: 'BAD-1',
    source_anchor: 'x.java:1',
    declared_guarantee: 'g',
    implementation_anchor: 'none',
    runtime_wiring: 'wired',
    positive_proof: 'none',
    rejection_proof: 'none',
    environment_class: 'unit',
    required_lane: 'multi-jvm',
    // finding_id missing -> flag
    disposition: 'totally-made-up', // out of vocab -> flag
    extra: 'no', // unknown field -> flag
  };
  const eErrs = checkEvidenceRow(badRow);
  if (eErrs.length === 0) failures.push('evidence positive control: known-bad row was NOT rejected');

  // Also: e2e-proved with insufficient lane must be rejected.
  const laneRow = {
    inventory_id: 'BAD-2', source_anchor: 'x.java:1', declared_guarantee: 'g',
    implementation_anchor: 'none', runtime_wiring: 'wired', positive_proof: 'T#m',
    rejection_proof: 'T#r', environment_class: 'unit', required_lane: 'multi-jvm',
    finding_id: 'none', disposition: 'e2e-proved',
  };
  const lErrs = checkEvidenceRow(laneRow);
  if (lErrs.length === 0) failures.push('evidence positive control: e2e-proved with insufficient lane was NOT rejected');

  return failures;
}

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

function printResult(label, errors, { strict } = {}) {
  if (errors.length === 0) {
    console.log(`[PASS] ${label}`);
    return true;
  }
  console.error(`[FAIL] ${label} — ${errors.length} problem(s):`);
  for (const e of errors) console.error(`  - ${e}`);
  return false;
}

function cmdManifest({ strict } = {}) {
  if (!existsSync(MANIFEST_FILE)) {
    return printResult('manifest', [`manifest file not found: ${MANIFEST_FILE}`]);
  }
  const entries = parseManifest(readFileSync(MANIFEST_FILE, 'utf-8'));
  if (entries.length < 7) {
    return printResult('manifest', [`expected >=7 manifest entries (7 domains), found ${entries.length}`]);
  }
  const results = runManifestChecks(entries);
  const errors = [];
  for (const r of results) for (const e of r.errors) errors.push(`[${r.entry.domain_id || '?'}] ${e}`);
  return printResult('manifest', errors, { strict });
}

function cmdCorpus({ strict } = {}) {
  if (!existsSync(CORPUS_FILE)) {
    return printResult('corpus', [`corpus file not found: ${CORPUS_FILE}`]);
  }
  const corpus = parseCorpus(readFileSync(CORPUS_FILE, 'utf-8'));
  if (corpus.shards.length !== 5) {
    return printResult('corpus', [`expected exactly 5 shards (18-22), found ${corpus.shards.length}`]);
  }
  const errors = checkCorpus(corpus);
  return printResult('corpus', errors, { strict });
}

function cmdEvidence({ strict } = {}) {
  const errors = [];
  if (!existsSync(SCHEMA_FILE)) {
    errors.push(`evidence schema not found: ${SCHEMA_FILE}`);
    return printResult('evidence', errors, { strict });
  }
  const rows = collectEvidenceRows();
  if (rows.length === 0) {
    // Stage 4 state: schema frozen, no evidence rows yet. Validate the schema doc declares the
    // full disposition vocabulary (proves the schema is non-hollow).
    const schemaText = readFileSync(SCHEMA_FILE, 'utf-8');
    for (const v of DISPOSITION_VOCAB) {
      if (!schemaText.includes(`\`${v}\``)) {
        errors.push(`schema doc does not declare disposition value: ${v}`);
      }
    }
    if (errors.length === 0) {
      console.log('[PASS] evidence (schema frozen; 0 evidence rows yet — produced by Stages 6-22)');
      return true;
    }
    return printResult('evidence', errors, { strict });
  }
  errors.push(...checkEvidenceRows(rows));
  return printResult('evidence', errors, { strict });
}

function cmdSelfTest() {
  const failures = runSelfTest();
  if (failures.length === 0) {
    console.log('[PASS] self-test (positive control) — all 3 checkers reject their known-bad input');
    console.log('  - manifest: rejects bad/missing/unknown fields + denominator mismatch');
    console.log('  - corpus  : rejects duplicate IDs, shard total mismatch, out-of-vocab sev/domain');
    console.log('  - evidence: rejects missing/unknown fields, out-of-vocab disposition, insufficient lane');
    return true;
  }
  console.error('[FAIL] self-test (positive control) — validator failed to reject known-bad input:');
  for (const f of failures) console.error(`  - ${f}`);
  return false;
}

function main() {
  const args = process.argv.slice(2);
  const strict = args.includes('--strict');
  const subs = args.filter((a) => !a.startsWith('-'));
  const sub = subs[0] || 'all';

  let ok = true;
  if (sub === 'manifest') {
    ok = cmdManifest({ strict }) && ok;
  } else if (sub === 'corpus') {
    ok = cmdCorpus({ strict }) && ok;
  } else if (sub === 'evidence') {
    ok = cmdEvidence({ strict }) && ok;
  } else if (sub === 'self-test') {
    ok = cmdSelfTest() && ok;
  } else if (sub === 'all') {
    ok = cmdManifest({ strict }) && ok;
    ok = cmdCorpus({ strict }) && ok;
    ok = cmdEvidence({ strict }) && ok;
  } else {
    console.error(`Unknown subcommand: ${sub}`);
    console.error('Usage: check-nop-stream-audit-manifest.mjs [manifest|corpus|evidence|self-test|all] [--strict]');
    process.exit(2);
  }
  process.exit(ok ? 0 : 1);
}

const __filename = fileURLToPath(import.meta.url);
if (process.argv[1] === __filename || process.argv[1]?.endsWith('check-nop-stream-audit-manifest.mjs')) {
  main();
}

export {
  parseManifest, runManifestChecks, parseCorpus, checkCorpus,
  parseEvidence, checkEvidenceRow, checkEvidenceRows, runSelfTest,
  SEVERITY_VOCAB, DOMAIN_VOCAB, DISPOSITION_VOCAB,
};
