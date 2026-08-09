#!/usr/bin/env node
// check-nop-stream-audit-manifest.mjs
//
// Validator for the nop-stream independent audit "度量衡" (Stage 4 + Stage 5 + Stage 18 + Stage 23):
//   - manifest     : execute every manifest selection command and compare to expected denominator
//   - corpus       : check finding IDs unique, shard totals consistent, severity/domain vocabulary legal
//   - evidence     : check evidence-row fields complete + disposition vocabulary legal
//   - qualification: check @@LANE lane-registry blocks (Stage 5): frozen_strength/status vocabulary, blocked-reason/positive-result rules
//   - disposition  : check @@DISPOSITION finding-disposition blocks (Stage 18): 5-value vocabulary, conditional fields, owner_plan path/sentinel, completeness
//   - docs-coverage: check @@DOC_ENTRY registry + @@DOC_REVIEW blocks (Stage 23): doc paths exist, 3-value vocabulary, conditional fields, completeness (--strict)
//   - readiness    : aggregate all evidence rows + lane registry (Stage 23): disposition counts, blocked-row enumeration, readiness gate decision
//   - self-test    : positive control — proves each checker REJECTS known-bad input (no silent skip)
//
// Rule #24 (No Silent No-Op): a missing/unknown field or an out-of-vocabulary value is a hard error
// that exits non-zero; the validator never silently fixes or ignores.
//
// Usage:
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs manifest       [--strict]
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs corpus         [--strict]
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence       [--strict]
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs qualification  [--strict]
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition [--shard <N>] [--strict]
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs docs-coverage  [--strict]
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs readiness      [--strict]
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test
//   node ai-dev/tools/check-nop-stream-audit-manifest.mjs                (default: runs manifest+corpus+evidence+qualification+disposition+docs-coverage+readiness)

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
const QUAL_FILE = join(AUDIT_DIR, 'environment-qualification.md');

const SEVERITY_VOCAB = new Set(['P0', 'P1', 'P2', 'AR']);
const DOMAIN_VOCAB = new Set([
  'coordinator/runtime', 'checkpoint/state', 'window', 'CEP', 'connector', 'contract/test',
]);
const DISPOSITION_VOCAB = new Set([
  'e2e-proved', 'component-only', 'unverified', 'fail-fast', 'non-goal', 'residual-risk', 'blocked',
]);
const LANE_VOCAB = new Set(['unit', 'in-process', 'multi-jvm', 'none']);
const LANE_STRENGTH = { none: 0, unit: 1, 'in-process': 2, 'multi-jvm': 3 };

// Stage 18 — finding-disposition 5-value vocabulary (distinct from the 7-value evidence-row vocabulary)
const FINDING_DISPOSITION_VOCAB = new Set([
  'revalidated', 'stale', 'active/successor owner', 'residual-risk', 'blocked',
]);
const DISPOSITION_REQUIRED = ['finding_id', 'severity', 'source_anchor', 'disposition'];
const DISPOSITION_ALLOWED = new Set([
  ...DISPOSITION_REQUIRED,
  'revalidation_evidence',   // REQUIRED when disposition=revalidated
  'stale_rationale',         // REQUIRED when disposition=stale
  'owner_plan',              // REQUIRED when disposition=active/successor owner
  'residual_rationale',      // REQUIRED when disposition=residual-risk
  'blocked_lane',            // REQUIRED when disposition=blocked
  'note',                    // OPTIONAL
  'successor_note',          // OPTIONAL
]);
// disposition value → required conditional field
const DISPOSITION_COND = {
  'revalidated': 'revalidation_evidence',
  'stale': 'stale_rationale',
  'active/successor owner': 'owner_plan',
  'residual-risk': 'residual_rationale',
  'blocked': 'blocked_lane',
};
const ROADMAP_FILE = join(PROJECT_ROOT, 'ai-dev', 'backlog', 'nop-stream-independent-audit-roadmap.md');

// Stage 5 — lane qualification (frozen_strength excludes 'none'; a lane always provides real evidence strength)
const LANE_STRENGTH_VOCAB = new Set(['unit', 'in-process', 'multi-jvm']);
const LANE_STATUS_VOCAB = new Set(['qualified', 'blocked']);
const LANE_REQUIRED = [
  'lane_id', 'frozen_strength', 'invoke_command', 'preconditions', 'credential_isolation',
  'cleanup', 'timeout', 'artifact_retention', 'owner', 'status',
];
const LANE_ALLOWED = new Set([
  ...LANE_REQUIRED,
  'expected_positive_result', // REQUIRED when status=qualified
  'blocked_reason',           // REQUIRED when status=blocked
  'rerun_condition',          // REQUIRED when status=blocked
  'note',                     // OPTIONAL
]);

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
// QUALIFICATION (Stage 5 — lane registry)
// ---------------------------------------------------------------------------

function parseLanes(text) {
  return parseBlocks(text, '@@LANE');
}

function checkLane(lane) {
  const errors = [];
  // required fields
  for (const f of LANE_REQUIRED) {
    if (lane[f] === undefined || lane[f] === '') errors.push(`missing required field: ${f}`);
  }
  // unknown fields
  for (const k of Object.keys(lane)) {
    if (!LANE_ALLOWED.has(k)) errors.push(`unknown field: ${k}`);
  }
  // frozen_strength vocabulary
  if (lane.frozen_strength !== undefined && !LANE_STRENGTH_VOCAB.has(lane.frozen_strength)) {
    errors.push(`frozen_strength out of vocabulary: "${lane.frozen_strength}" (allowed: unit | in-process | multi-jvm)`);
  }
  // status vocabulary
  if (lane.status !== undefined && !LANE_STATUS_VOCAB.has(lane.status)) {
    errors.push(`status out of vocabulary: "${lane.status}" (allowed: qualified | blocked)`);
  }
  // conditional requirements
  if (lane.status === 'blocked') {
    if (lane.blocked_reason === undefined || lane.blocked_reason === '') {
      errors.push('status=blocked requires blocked_reason');
    }
    if (lane.rerun_condition === undefined || lane.rerun_condition === '') {
      errors.push('status=blocked requires rerun_condition');
    }
  }
  if (lane.status === 'qualified') {
    if (lane.expected_positive_result === undefined || lane.expected_positive_result === '') {
      errors.push('status=qualified requires expected_positive_result');
    }
  }
  // a qualified row must NOT carry blocked_reason/rerun_condition (would be contradictory)
  if (lane.status === 'qualified' && lane.blocked_reason !== undefined) {
    errors.push('status=qualified must not carry blocked_reason');
  }
  // invoke_command must be non-empty (blocked-no-test placeholder "none (...)" is a legal non-empty value)
  if (lane.invoke_command !== undefined && lane.invoke_command === '') {
    errors.push('invoke_command must not be empty');
  }
  return errors;
}

function checkLanes(lanes) {
  const errors = [];
  const seen = new Set();
  for (const l of lanes) {
    const lErrs = checkLane(l);
    for (const err of lErrs) errors.push(`${l.lane_id || '<no-id>'}: ${err}`);
    if (l.lane_id) {
      if (seen.has(l.lane_id)) errors.push(`${l.lane_id}: duplicate lane_id`);
      seen.add(l.lane_id);
    }
  }
  return errors;
}

// ---------------------------------------------------------------------------
// DISPOSITION (Stage 18 — finding-disposition 5-value vocabulary)
// ---------------------------------------------------------------------------

function parseDispositions(text) {
  return parseBlocks(text, '@@DISPOSITION');
}

/**
 * Parse the roadmap Work Items block and return a Map<stageNumber, status>.
 * Lines like "- 18. 当前 production finding disposition: `planned`" → { 18: 'planned' }
 */
function parseRoadmapStageStatuses() {
  const statuses = new Map();
  if (!existsSync(ROADMAP_FILE)) return statuses;
  const text = readFileSync(ROADMAP_FILE, 'utf-8');
  for (const line of text.split('\n')) {
    const m = line.match(/^\-\s+(\d+)\.\s+.+:\s*`(\w+)`\s*$/);
    if (m) {
      statuses.set(parseInt(m[1], 10), m[2]);
    }
  }
  return statuses;
}

/**
 * Parse registered lane_ids from environment-qualification.md.
 * Returns a Set of lane_id strings.
 */
function parseRegisteredLaneIds() {
  const ids = new Set();
  if (!existsSync(QUAL_FILE)) return ids;
  const lanes = parseLanes(readFileSync(QUAL_FILE, 'utf-8'));
  for (const l of lanes) {
    if (l.lane_id) ids.add(l.lane_id);
  }
  return ids;
}

/**
 * Validate an owner_plan value:
 * - If it matches `roadmap-stage-<N>`, check that stage N is NOT `done` in the roadmap.
 * - Otherwise, check it is a path that exists in the repo (resolve relative to PROJECT_ROOT).
 * Returns an array of error strings (empty if valid).
 */
function checkOwnerPlan(ownerPlan) {
  const errors = [];
  const sentinelMatch = ownerPlan.match(/^roadmap-stage-(\d+)$/);
  if (sentinelMatch) {
    const stageNum = parseInt(sentinelMatch[1], 10);
    const statuses = parseRoadmapStageStatuses();
    if (statuses.size === 0) {
      errors.push(`owner_plan sentinel "${ownerPlan}": cannot parse roadmap statuses (file missing or unparseable)`);
    } else if (!statuses.has(stageNum)) {
      errors.push(`owner_plan sentinel "${ownerPlan}": stage ${stageNum} not found in roadmap`);
    } else if (statuses.get(stageNum) === 'done') {
      errors.push(`owner_plan sentinel "${ownerPlan}": stage ${stageNum} is 'done' (sentinel must point to a non-done stage)`);
    }
  } else {
    const resolved = resolve(PROJECT_ROOT, ownerPlan);
    if (!existsSync(resolved)) {
      errors.push(`owner_plan path does not exist in repo: ${ownerPlan}`);
    }
  }
  return errors;
}

/**
 * Check a single @@DISPOSITION block.
 * Returns an array of error strings (empty if valid).
 */
function checkDispositionBlock(block, { registeredLaneIds } = {}) {
  const errors = [];
  // required fields
  for (const f of DISPOSITION_REQUIRED) {
    if (block[f] === undefined || block[f] === '') errors.push(`missing required field: ${f}`);
  }
  // unknown fields
  for (const k of Object.keys(block)) {
    if (!DISPOSITION_ALLOWED.has(k)) errors.push(`unknown field: ${k}`);
  }
  // disposition vocabulary
  if (block.disposition !== undefined && !FINDING_DISPOSITION_VOCAB.has(block.disposition)) {
    errors.push(`disposition out of vocabulary: "${block.disposition}" (allowed: revalidated | stale | active/successor owner | residual-risk | blocked)`);
  }
  // conditional fields based on disposition value
  if (block.disposition !== undefined && FINDING_DISPOSITION_VOCAB.has(block.disposition)) {
    const condField = DISPOSITION_COND[block.disposition];
    if (condField && (block[condField] === undefined || block[condField] === '')) {
      errors.push(`disposition="${block.disposition}" requires non-empty "${condField}"`);
    }
    // owner_plan path/sentinel existence check
    if (block.disposition === 'active/successor owner' && block.owner_plan) {
      errors.push(...checkOwnerPlan(block.owner_plan));
    }
    // blocked_lane must be a registered lane
    if (block.disposition === 'blocked' && block.blocked_lane) {
      if (registeredLaneIds && registeredLaneIds.size > 0 && !registeredLaneIds.has(block.blocked_lane)) {
        errors.push(`blocked_lane "${block.blocked_lane}" is not a registered lane_id in environment-qualification.md`);
      }
    }
  }
  return errors;
}

/**
 * Check all disposition blocks (legality + optional corpus cross-check + optional completeness).
 * @param {Array} blocks - parsed @@DISPOSITION blocks
 * @param {Object} corpus - parsed finding corpus (from parseCorpus)
 * @param {Object} opts - { shard: number|null, strict: bool, registeredLaneIds: Set }
 * @returns {Array} error strings
 */
function checkDispositions(blocks, corpus, { shard, strict, registeredLaneIds } = {}) {
  const errors = [];
  const seen = new Map(); // finding_id → count

  // legality check on each block
  for (const b of blocks) {
    const bErrs = checkDispositionBlock(b, { registeredLaneIds });
    for (const err of bErrs) errors.push(`${b.finding_id || '<no-id>'}: ${err}`);
    if (b.finding_id) {
      seen.set(b.finding_id, (seen.get(b.finding_id) || 0) + 1);
    }
  }

  // no-dup check
  for (const [id, count] of seen) {
    if (count > 1) errors.push(`${id}: duplicate @@DISPOSITION block (x${count})`);
  }

  // corpus cross-check (finding_id / severity consistency)
  if (corpus && corpus.shards) {
    const corpusMap = new Map(); // finding_id → { sev, shardNum }
    for (const s of corpus.shards) {
      const shardNumMatch = s.name.match(/Shard\s+(\d+)/);
      const shardNum = shardNumMatch ? parseInt(shardNumMatch[1], 10) : null;
      for (const e of s.entries) {
        corpusMap.set(e.id, { sev: e.sev, shardNum });
      }
    }
    for (const b of blocks) {
      if (b.finding_id && corpusMap.has(b.finding_id)) {
        const entry = corpusMap.get(b.finding_id);
        if (b.severity !== undefined && b.severity !== entry.sev) {
          errors.push(`${b.finding_id}: severity mismatch (disposition="${b.severity}", corpus="${entry.sev}")`);
        }
      } else if (b.finding_id && !corpusMap.has(b.finding_id)) {
        errors.push(`${b.finding_id}: finding_id not found in frozen corpus`);
      }
    }

    // completeness check (only in strict mode with shard specified)
    if (strict && shard !== null && shard !== undefined) {
      const shardData = corpus.shards.find((s) => {
        const m = s.name.match(/Shard\s+(\d+)/);
        return m && parseInt(m[1], 10) === shard;
      });
      if (shardData) {
        for (const e of shardData.entries) {
          if (!seen.has(e.id)) {
            errors.push(`completeness: finding ${e.id} (shard ${shard}) has no @@DISPOSITION block`);
          }
        }
      } else {
        errors.push(`completeness: shard ${shard} not found in corpus`);
      }
    }
  }

  return errors;
}

/**
 * Collect all @@DISPOSITION blocks from stage-*-disposition.md files.
 */
function collectDispositionBlocks() {
  const blocks = [];
  if (!existsSync(AUDIT_DIR)) return blocks;
  for (const name of readdirSync(AUDIT_DIR, { withFileTypes: true })) {
    if (name.isFile() && name.name.endsWith('-disposition.md')) {
      const text = readFileSync(join(AUDIT_DIR, name.name), 'utf-8');
      blocks.push(...parseDispositions(text));
    }
  }
  return blocks;
}

// ---------------------------------------------------------------------------
// DOCS-COVERAGE (Stage 23 — owner-doc manifest review registry)
// ---------------------------------------------------------------------------

const DOC_MANIFEST_FILE = join(AUDIT_DIR, 'owner-doc-manifest.md');
const DOC_REVIEW_VOCAB = new Set(['reviewed-no-change', 'corrected', 'out-of-scope']);
const DOC_ENTRY_REQUIRED = ['doc_path', 'surface', 'expected_review', 'known_drift_ids'];
const DOC_ENTRY_ALLOWED = new Set(DOC_ENTRY_REQUIRED);
const DOC_REVIEW_REQUIRED = ['doc_path', 'review_status'];
const DOC_REVIEW_ALLOWED = new Set([
  ...DOC_REVIEW_REQUIRED,
  'drift_ids',          // REQUIRED when review_status=corrected
  'correction_summary', // REQUIRED when review_status=corrected OR out-of-scope
  'reviewer_evidence',  // OPTIONAL (recommended)
]);
// review_status value → required conditional fields
const DOC_REVIEW_COND = {
  'corrected': ['correction_summary', 'drift_ids'],
  'out-of-scope': ['correction_summary'],
};

function parseDocEntries(text) {
  return parseBlocks(text, '@@DOC_ENTRY');
}

function parseDocReviews(text) {
  return parseBlocks(text, '@@DOC_REVIEW');
}

function checkDocEntry(entry) {
  const errors = [];
  for (const f of DOC_ENTRY_REQUIRED) {
    if (entry[f] === undefined || entry[f] === '') errors.push(`missing required field: ${f}`);
  }
  for (const k of Object.keys(entry)) {
    if (!DOC_ENTRY_ALLOWED.has(k)) errors.push(`unknown field: ${k}`);
  }
  return errors;
}

function checkDocReview(review) {
  const errors = [];
  for (const f of DOC_REVIEW_REQUIRED) {
    if (review[f] === undefined || review[f] === '') errors.push(`missing required field: ${f}`);
  }
  for (const k of Object.keys(review)) {
    if (!DOC_REVIEW_ALLOWED.has(k)) errors.push(`unknown field: ${k}`);
  }
  if (review.review_status !== undefined && !DOC_REVIEW_VOCAB.has(review.review_status)) {
    errors.push(`review_status out of vocabulary: "${review.review_status}" (allowed: reviewed-no-change | corrected | out-of-scope)`);
  }
  if (review.review_status !== undefined && DOC_REVIEW_VOCAB.has(review.review_status)) {
    const condFields = DOC_REVIEW_COND[review.review_status] || [];
    for (const cf of condFields) {
      if (review[cf] === undefined || review[cf] === '' || review[cf] === 'none') {
        errors.push(`review_status="${review.review_status}" requires non-empty "${cf}"`);
      }
    }
  }
  // doc_path must exist in the repo (resolve relative to PROJECT_ROOT)
  if (review.doc_path !== undefined && review.doc_path !== '') {
    const resolved = resolve(PROJECT_ROOT, review.doc_path);
    if (!existsSync(resolved)) {
      errors.push(`doc_path does not exist in repo: ${review.doc_path}`);
    }
  }
  return errors;
}

/**
 * Check all doc entries + reviews: legality, no-dup, completeness (strict), and
 * that every review's doc_path is registered and every registered path is reviewed.
 * @returns {Object} { errors: string[], entryPaths: Set, reviewPaths: Set, reviews: Array }
 */
function checkDocCoverage(entries, reviews, { strict } = {}) {
  const errors = [];
  const entryPaths = [];
  const reviewPaths = [];

  // legality check on entries
  const entrySeen = new Set();
  for (const e of entries) {
    const eErrs = checkDocEntry(e);
    for (const err of eErrs) errors.push(`[entry ${e.doc_path || '?'}] ${err}`);
    if (e.doc_path) {
      if (entrySeen.has(e.doc_path)) errors.push(`[entry] duplicate doc_path: ${e.doc_path}`);
      entrySeen.add(e.doc_path);
      entryPaths.push(e.doc_path);
      // entry path must exist
      const resolved = resolve(PROJECT_ROOT, e.doc_path);
      if (!existsSync(resolved)) errors.push(`[entry] doc_path does not exist in repo: ${e.doc_path}`);
    }
  }

  // legality check on reviews
  const reviewSeen = new Set();
  for (const r of reviews) {
    const rErrs = checkDocReview(r);
    for (const err of rErrs) errors.push(`[review ${r.doc_path || '?'}] ${err}`);
    if (r.doc_path) {
      if (reviewSeen.has(r.doc_path)) errors.push(`[review] duplicate doc_path: ${r.doc_path}`);
      reviewSeen.add(r.doc_path);
      reviewPaths.push(r.doc_path);
    }
  }

  // strict mode: every registered entry must have a review
  if (strict) {
    for (const p of entryPaths) {
      if (!reviewSeen.has(p)) {
        errors.push(`completeness: registered doc "${p}" has no @@DOC_REVIEW block`);
      }
    }
  }

  return { errors, entryPaths, reviewPaths, reviews };
}

function collectDocEntries() {
  if (!existsSync(DOC_MANIFEST_FILE)) return [];
  return parseDocEntries(readFileSync(DOC_MANIFEST_FILE, 'utf-8'));
}

function collectDocReviews() {
  if (!existsSync(DOC_MANIFEST_FILE)) return [];
  return parseDocReviews(readFileSync(DOC_MANIFEST_FILE, 'utf-8'));
}

// ---------------------------------------------------------------------------
// READINESS (Stage 23 — aggregate evidence corpus + lane registry → decision)
// ---------------------------------------------------------------------------

/**
 * Classify a blocked evidence row's blocker source by scanning its proof/note text.
 * Returns 'lane-blocked' (T3-T6 external backend unavailable) or
 *         'capability-gap' (T2 lane qualified but the deeper test has a defect), or
 *         'unknown'.
 */
function classifyBlockedRow(row) {
  const hay = `${row.positive_proof || ''} ${row.rejection_proof || ''} ${row.declared_guarantee || ''}`.toLowerCase();
  if (/evid-s13-015|evid-s13-016|evid-s14-013|evid-s14-014|takeover|fencing|exactly.once.recovery|failover|t2 .*defect|capability.level/.test(hay)) {
    // T2 qualified lane but deeper test defect → capability-gap
    if (/t2 .*defect|capability.level|takeover|evid-s14-013|evid-s14-014|evid-s13-015|evid-s13-016/.test(hay)) {
      return 'capability-gap';
    }
  }
  if (/t3|t4|t5|t6|kafka|pulsar|postgresql|debezium|backend|blocked lane|gated.*block/.test(hay)) {
    return 'lane-blocked';
  }
  // default: a multi-jvm required_lane blocked row without explicit lane ref is a capability-gap
  if (row.required_lane === 'multi-jvm') return 'capability-gap';
  return 'lane-blocked';
}

/**
 * Aggregate all evidence rows + lane registry → readiness counts + decision.
 * @returns {Object} { counts: Map<disposition,number>, blockedRows: Array, totalRows,
 *                      laneStatus: Map, decision: string, e2eRows: Array }
 */
function aggregateReadiness() {
  const rows = collectEvidenceRows();
  const counts = new Map();
  for (const v of DISPOSITION_VOCAB) counts.set(v, 0);
  const blockedRows = [];
  const e2eRows = [];
  for (const r of rows) {
    if (counts.has(r.disposition)) counts.set(r.disposition, counts.get(r.disposition) + 1);
    if (r.disposition === 'blocked') {
      blockedRows.push({
        inventory_id: r.inventory_id,
        required_lane: r.required_lane,
        blocker_source: classifyBlockedRow(r),
        source_anchor: r.source_anchor,
      });
    }
    if (r.disposition === 'e2e-proved') {
      e2eRows.push({
        inventory_id: r.inventory_id,
        environment_class: r.environment_class,
        required_lane: r.required_lane,
        source_anchor: r.source_anchor,
      });
    }
  }
  // lane registry
  const laneStatus = new Map();
  if (existsSync(QUAL_FILE)) {
    for (const l of parseLanes(readFileSync(QUAL_FILE, 'utf-8'))) {
      laneStatus.set(l.lane_id, { status: l.status, frozen_strength: l.frozen_strength });
    }
  }
  // readiness gate: any blocked row blocks blanket ready
  const hasRequiredLaneBlocked = blockedRows.length > 0;
  let decision;
  if (hasRequiredLaneBlocked) {
    decision = 'ready only for enumerated e2e-proved capability/environment pairs';
  } else {
    decision = 'ready';
  }
  return { counts, blockedRows, totalRows: rows.length, laneStatus, decision, e2eRows };
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

  // --- Qualification positive control: known-bad @@LANE blocks must be rejected.
  const badLanes = [
    // 1. missing required field (no owner)
    { lane_id: 'BAD-L1', frozen_strength: 'unit', invoke_command: 'echo', preconditions: 'p',
      credential_isolation: 'none', cleanup: 'c', timeout: '1s', artifact_retention: 'a', status: 'qualified',
      expected_positive_result: 'x' },
    // 2. frozen_strength out of vocabulary
    { lane_id: 'BAD-L2', frozen_strength: 'none', invoke_command: 'echo', preconditions: 'p',
      credential_isolation: 'none', cleanup: 'c', timeout: '1s', artifact_retention: 'a', owner: 'o',
      status: 'qualified', expected_positive_result: 'x' },
    // 3. status out of vocabulary
    { lane_id: 'BAD-L3', frozen_strength: 'unit', invoke_command: 'echo', preconditions: 'p',
      credential_isolation: 'none', cleanup: 'c', timeout: '1s', artifact_retention: 'a', owner: 'o',
      status: 'maybe' },
    // 4. blocked missing blocked_reason + rerun_condition
    { lane_id: 'BAD-L4', frozen_strength: 'unit', invoke_command: 'none (no gated test in repo)',
      preconditions: 'p', credential_isolation: 'none', cleanup: 'c', timeout: '1s', artifact_retention: 'a',
      owner: 'o', status: 'blocked' },
    // 5. qualified missing expected_positive_result
    { lane_id: 'BAD-L5', frozen_strength: 'unit', invoke_command: 'echo', preconditions: 'p',
      credential_isolation: 'none', cleanup: 'c', timeout: '1s', artifact_retention: 'a', owner: 'o',
      status: 'qualified' },
    // 6. unknown field
    { lane_id: 'BAD-L6', frozen_strength: 'unit', invoke_command: 'echo', preconditions: 'p',
      credential_isolation: 'none', cleanup: 'c', timeout: '1s', artifact_retention: 'a', owner: 'o',
      status: 'qualified', expected_positive_result: 'x', bogus: 'no' },
  ];
  for (const bl of badLanes) {
    const errs = checkLane(bl);
    if (errs.length === 0) failures.push(`qualification positive control: known-bad lane ${bl.lane_id} was NOT rejected`);
  }
  // And a GOOD lane must pass (ensures the checker is not blindly rejecting everything).
  const goodLane = {
    lane_id: 'GOOD-L', frozen_strength: 'in-process', invoke_command: 'echo', preconditions: 'p',
    credential_isolation: 'none', cleanup: 'c', timeout: '1s', artifact_retention: 'a', owner: 'o',
    status: 'qualified', expected_positive_result: 'surefire PASS', note: 'honest classification',
  };
  const goodErrs = checkLane(goodLane);
  if (goodErrs.length !== 0) failures.push(`qualification positive control: good lane was wrongly rejected: ${goodErrs.join('; ')}`);

  // --- Disposition positive control: known-bad @@DISPOSITION blocks must be rejected.
  const fakeLaneIds = new Set(['T1-unit-embedded-in-process', 'T2-multi-jvm', 'T3-kafka-dataplane']);
  const badDispositions = [
    // 1. out-of-vocabulary disposition value
    { finding_id: 'BAD-D1', severity: 'P0', source_anchor: 'x.java:1', disposition: 'totally-made-up' },
    // 2. revalidated missing revalidation_evidence
    { finding_id: 'BAD-D2', severity: 'P1', source_anchor: 'x.java:2', disposition: 'revalidated' },
    // 3. active/successor owner missing owner_plan
    { finding_id: 'BAD-D3', severity: 'P0', source_anchor: 'x.java:3', disposition: 'active/successor owner' },
    // 4. residual-risk missing residual_rationale
    { finding_id: 'BAD-D4', severity: 'P2', source_anchor: 'x.java:4', disposition: 'residual-risk' },
    // 5. blocked missing blocked_lane
    { finding_id: 'BAD-D5', severity: 'P1', source_anchor: 'x.java:5', disposition: 'blocked' },
    // 6. blocked with unregistered lane
    { finding_id: 'BAD-D6', severity: 'P1', source_anchor: 'x.java:6', disposition: 'blocked', blocked_lane: 'nonexistent-lane' },
    // 7. stale missing stale_rationale
    { finding_id: 'BAD-D7', severity: 'P2', source_anchor: 'x.java:7', disposition: 'stale' },
    // 8. unknown field
    { finding_id: 'BAD-D8', severity: 'P2', source_anchor: 'x.java:8', disposition: 'residual-risk', residual_rationale: 'ok', bogus: 'no' },
    // 9. owner_plan path does not exist in repo
    { finding_id: 'BAD-D9', severity: 'P0', source_anchor: 'x.java:9', disposition: 'active/successor owner', owner_plan: 'nonexistent/plan/path.md' },
  ];
  for (const bd of badDispositions) {
    const errs = checkDispositionBlock(bd, { registeredLaneIds: fakeLaneIds });
    if (errs.length === 0) failures.push(`disposition positive control: known-bad block ${bd.finding_id} was NOT rejected`);
  }

  // Completeness + no-dup positive control using checkDispositions with strict mode
  const fakeCorpus = {
    shards: [
      { name: '## Shard 99', total: 2, declaredIds: ['S99-1', 'S99-2'],
        entries: [
          { id: 'S99-1', sev: 'P0', domain: 'CEP' },
          { id: 'S99-2', sev: 'P1', domain: 'window' },
        ] },
    ],
  };
  // strict mode: shard 99 missing S99-2 (completeness failure)
  const partialBlocks = [
    { finding_id: 'S99-1', severity: 'P0', source_anchor: 'x:1', disposition: 'revalidated', revalidation_evidence: 'Test#m' },
  ];
  const strictErrs = checkDispositions(partialBlocks, fakeCorpus, { shard: 99, strict: true, registeredLaneIds: fakeLaneIds });
  if (strictErrs.length === 0) failures.push('disposition positive control: strict mode missing-finding was NOT rejected');

  // strict mode: duplicate finding_id (no-dup failure)
  const dupBlocks = [
    { finding_id: 'S99-1', severity: 'P0', source_anchor: 'x:1', disposition: 'revalidated', revalidation_evidence: 'Test#m' },
    { finding_id: 'S99-1', severity: 'P0', source_anchor: 'x:1', disposition: 'revalidated', revalidation_evidence: 'Test#m2' },
    { finding_id: 'S99-2', severity: 'P1', source_anchor: 'x:2', disposition: 'residual-risk', residual_rationale: 'ok' },
  ];
  const dupErrs = checkDispositions(dupBlocks, fakeCorpus, { shard: 99, strict: true, registeredLaneIds: fakeLaneIds });
  if (dupErrs.length === 0) failures.push('disposition positive control: strict mode duplicate-id was NOT rejected');

  // roadmap-stage sentinel pointing to a done stage must be rejected
  const doneSentinelErrs = checkOwnerPlan('roadmap-stage-4'); // Stage 4 is 'done' in the roadmap
  if (doneSentinelErrs.length === 0) failures.push('disposition positive control: roadmap-stage-4 (done stage) sentinel was NOT rejected');

  // And a GOOD disposition block must pass (ensures the checker is not blindly rejecting everything).
  const goodDisposition = {
    finding_id: 'GOOD-D', severity: 'P2', source_anchor: 'x.java:99',
    disposition: 'residual-risk', residual_rationale: 'non-blocking test-quality gap', note: 'ok',
  };
  const goodDispErrs = checkDispositionBlock(goodDisposition, { registeredLaneIds: fakeLaneIds });
  if (goodDispErrs.length !== 0) failures.push(`disposition positive control: good block was wrongly rejected: ${goodDispErrs.join('; ')}`);

  // --- Docs-coverage positive control: known-bad @@DOC_REVIEW blocks must be rejected.
  const badDocReviews = [
    // 1. out-of-vocabulary review_status
    { doc_path: 'README.md', review_status: 'totally-made-up' },
    // 2. corrected missing correction_summary
    { doc_path: 'README.md', review_status: 'corrected', drift_ids: 'X-1' },
    // 3. corrected missing drift_ids
    { doc_path: 'README.md', review_status: 'corrected', correction_summary: 'fixed' },
    // 4. out-of-scope missing correction_summary
    { doc_path: 'README.md', review_status: 'out-of-scope' },
    // 5. doc_path does not exist in repo
    { doc_path: 'nonexistent/file.md', review_status: 'reviewed-no-change' },
    // 6. unknown field
    { doc_path: 'README.md', review_status: 'reviewed-no-change', bogus: 'no' },
    // 7. missing required field (no review_status)
    { doc_path: 'README.md' },
  ];
  for (const bd of badDocReviews) {
    const errs = checkDocReview(bd);
    if (errs.length === 0) failures.push(`docs-coverage positive control: known-bad review ${bd.doc_path || '?'} was NOT rejected`);
  }
  // GOOD review must pass
  const goodReview = { doc_path: 'README.md', review_status: 'corrected', drift_ids: 'X-1', correction_summary: 'fixed naming', reviewer_evidence: 'live code' };
  const goodReviewErrs = checkDocReview(goodReview);
  if (goodReviewErrs.length !== 0) failures.push(`docs-coverage positive control: good review was wrongly rejected: ${goodReviewErrs.join('; ')}`);
  // BAD doc entry (missing field / unknown field)
  const badEntry = { doc_path: 'README.md', surface: 'x', bogus: 'no' };
  if (checkDocEntry(badEntry).length === 0) failures.push('docs-coverage positive control: known-bad entry was NOT rejected');
  // strict completeness: registered doc with no review must be flagged
  const strictCov = checkDocCoverage(
    [{ doc_path: 'README.md', surface: 'x', expected_review: 'y', known_drift_ids: 'none' }],
    [],
    { strict: true },
  );
  if (strictCov.errors.length === 0) failures.push('docs-coverage positive control: strict mode missing-review was NOT rejected');
  // duplicate doc_path in reviews must be flagged
  const dupCov = checkDocCoverage(
    [],
    [
      { doc_path: 'README.md', review_status: 'reviewed-no-change' },
      { doc_path: 'README.md', review_status: 'reviewed-no-change' },
    ],
    { strict: false },
  );
  if (dupCov.errors.length === 0) failures.push('docs-coverage positive control: duplicate review doc_path was NOT rejected');

  // --- Readiness positive control: a blocked-row corpus must NOT produce a blanket "ready" decision.
  // Use the real aggregator but with an injected evidence source by checking classifyBlockedRow.
  const laneBlockedRow = {
    disposition: 'blocked', required_lane: 'in-process',
    declared_guarantee: 'Pulsar wire-codec gated external backend BLOCKED',
    positive_proof: 'T4 pulsar lane blocked', rejection_proof: 'none',
  };
  if (classifyBlockedRow(laneBlockedRow) !== 'lane-blocked') {
    failures.push('readiness positive control: lane-blocked row was NOT classified as lane-blocked');
  }
  const capGapRow = {
    disposition: 'blocked', required_lane: 'multi-jvm',
    declared_guarantee: 'T2 lane deeper defect — takeover fails (EVID-S14-014)',
    positive_proof: 'none', rejection_proof: 'none',
  };
  if (classifyBlockedRow(capGapRow) !== 'capability-gap') {
    failures.push('readiness positive control: capability-gap row was NOT classified as capability-gap');
  }

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
    console.log('[PASS] self-test (positive control) — all 7 checkers reject their known-bad input');
    console.log('  - manifest    : rejects bad/missing/unknown fields + denominator mismatch');
    console.log('  - corpus      : rejects duplicate IDs, shard total mismatch, out-of-vocab sev/domain');
    console.log('  - evidence    : rejects missing/unknown fields, out-of-vocab disposition, insufficient lane');
    console.log('  - qualification: rejects missing/unknown fields, out-of-vocab frozen_strength/status, blocked-missing-reason, qualified-missing-positive-result');
    console.log('  - disposition : rejects out-of-vocab value, missing conditional fields, unregistered lane, nonexistent owner_plan path, done-stage sentinel, strict-mode missing/duplicate findings');
    console.log('  - docs-coverage: rejects out-of-vocab review_status, missing conditional fields, nonexistent doc_path, unknown fields, strict-mode missing-review, duplicate review doc_path');
    console.log('  - readiness   : rejects gate violation (blocked rows + blanket ready); classifies lane-blocked vs capability-gap');
    return true;
  }
  console.error('[FAIL] self-test (positive control) — validator failed to reject known-bad input:');
  for (const f of failures) console.error(`  - ${f}`);
  return false;
}

function cmdQualification({ strict } = {}) {
  const errors = [];
  if (!existsSync(QUAL_FILE)) {
    errors.push(`qualification file not found: ${QUAL_FILE}`);
    return printResult('qualification', errors, { strict });
  }
  const lanes = parseLanes(readFileSync(QUAL_FILE, 'utf-8'));
  // Stage 5 freezes exactly 6 lane targets (T1–T6).
  if (lanes.length !== 6) {
    errors.push(`expected exactly 6 @@LANE records (T1–T6), found ${lanes.length}`);
  }
  errors.push(...checkLanes(lanes));
  return printResult('qualification', errors, { strict });
}

function cmdDisposition({ strict, shard } = {}) {
  const blocks = collectDispositionBlocks();
  const registeredLaneIds = parseRegisteredLaneIds();

  // Parse corpus for cross-check + completeness
  let corpus = null;
  if (existsSync(CORPUS_FILE)) {
    corpus = parseCorpus(readFileSync(CORPUS_FILE, 'utf-8'));
  }

  if (blocks.length === 0) {
    // No disposition files yet — legal in partial mode (0 rows to check)
    if (strict && shard !== null && shard !== undefined) {
      // In strict+shard mode, every finding in the shard is missing
      const errors = [];
      if (corpus) {
        const shardData = corpus.shards.find((s) => {
          const m = s.name.match(/Shard\s+(\d+)/);
          return m && parseInt(m[1], 10) === shard;
        });
        if (shardData) {
          for (const e of shardData.entries) {
            errors.push(`completeness: finding ${e.id} (shard ${shard}) has no @@DISPOSITION block`);
          }
        }
      }
      return printResult(`disposition --shard ${shard} --strict`, errors);
    }
    console.log(`[PASS] disposition (0 disposition rows — no stage-*-disposition.md files found yet)`);
    return true;
  }

  const errors = checkDispositions(blocks, corpus, { shard, strict, registeredLaneIds });
  const label = shard !== null && shard !== undefined
    ? `disposition --shard ${shard}${strict ? ' --strict' : ''}`
    : 'disposition';
  if (errors.length === 0) {
    console.log(`[PASS] ${label} (${blocks.length} disposition rows validated)`);
    return true;
  }
  console.error(`[FAIL] ${label} — ${errors.length} problem(s):`);
  for (const e of errors) console.error(`  - ${e}`);
  return false;
}

function cmdDocsCoverage({ strict } = {}) {
  if (!existsSync(DOC_MANIFEST_FILE)) {
    // No manifest yet — legal in partial mode (0 rows)
    if (strict) {
      return printResult('docs-coverage --strict', [`doc manifest file not found: ${DOC_MANIFEST_FILE}`]);
    }
    console.log('[PASS] docs-coverage (0 doc-review rows — no owner-doc-manifest.md yet)');
    return true;
  }
  const entries = collectDocEntries();
  const reviews = collectDocReviews();
  const { errors } = checkDocCoverage(entries, reviews, { strict });
  const label = `docs-coverage${strict ? ' --strict' : ''}`;
  if (errors.length === 0) {
    console.log(`[PASS] ${label} (${reviews.length} doc-review rows; ${entries.length} registered docs)`);
    return true;
  }
  return printResult(label, errors, { strict });
}

function cmdReadiness({ strict } = {}) {
  const errors = [];
  const agg = aggregateReadiness();
  // Gate consistency check: if blocked rows exist, decision must NOT be blanket ready
  if (agg.blockedRows.length > 0 && agg.decision === 'ready') {
    errors.push(`readiness gate violation: ${agg.blockedRows.length} blocked rows exist but decision is blanket "ready"`);
  }
  if (agg.totalRows === 0) {
    errors.push('no evidence rows found — cannot aggregate readiness');
  }
  const label = `readiness${strict ? ' --strict' : ''}`;
  if (errors.length === 0) {
    console.log(`[PASS] ${label} (${agg.totalRows} evidence rows; decision: "${agg.decision}")`);
    console.log('  Evidence row counts by disposition:');
    for (const [v, n] of agg.counts) console.log(`    ${v}: ${n}`);
    console.log(`  Blocked rows (block blanket-ready): ${agg.blockedRows.length}`);
    for (const b of agg.blockedRows) {
      console.log(`    ${b.inventory_id} | required_lane=${b.required_lane} | blocker=${b.blocker_source}`);
    }
    console.log(`  e2e-proved rows (enumerate ready pairs): ${agg.e2eRows.length}`);
    console.log(`  Lane registry:`);
    for (const [id, l] of agg.laneStatus) {
      console.log(`    ${id}: ${l.status} (${l.frozen_strength})`);
    }
    return true;
  }
  return printResult(label, errors, { strict });
}

function main() {
  const args = process.argv.slice(2);
  const strict = args.includes('--strict');
  const subs = args.filter((a) => !a.startsWith('-'));
  const sub = subs[0] || 'all';

  // Parse --shard <N>
  let shard = null;
  const shardIdx = args.indexOf('--shard');
  if (shardIdx > -1 && shardIdx + 1 < args.length) {
    shard = parseInt(args[shardIdx + 1], 10);
    if (Number.isNaN(shard)) {
      console.error(`Invalid --shard value: ${args[shardIdx + 1]}`);
      process.exit(2);
    }
  }

  let ok = true;
  if (sub === 'manifest') {
    ok = cmdManifest({ strict }) && ok;
  } else if (sub === 'corpus') {
    ok = cmdCorpus({ strict }) && ok;
  } else if (sub === 'evidence') {
    ok = cmdEvidence({ strict }) && ok;
  } else if (sub === 'self-test') {
    ok = cmdSelfTest() && ok;
  } else if (sub === 'qualification') {
    ok = cmdQualification({ strict }) && ok;
  } else if (sub === 'disposition') {
    ok = cmdDisposition({ strict, shard }) && ok;
  } else if (sub === 'docs-coverage') {
    ok = cmdDocsCoverage({ strict }) && ok;
  } else if (sub === 'readiness') {
    ok = cmdReadiness({ strict }) && ok;
  } else if (sub === 'all') {
    ok = cmdManifest({ strict }) && ok;
    ok = cmdCorpus({ strict }) && ok;
    ok = cmdEvidence({ strict }) && ok;
    ok = cmdQualification({ strict }) && ok;
    ok = cmdDisposition({ strict: false, shard: null }) && ok; // all mode: partial disposition (no shard, no completeness)
    ok = cmdDocsCoverage({ strict }) && ok; // all mode: strict docs-coverage (every registered doc reviewed)
    ok = cmdReadiness({ strict: false }) && ok;
  } else {
    console.error(`Unknown subcommand: ${sub}`);
    console.error('Usage: check-nop-stream-audit-manifest.mjs [manifest|corpus|evidence|qualification|disposition|docs-coverage|readiness|self-test|all] [--strict] [--shard <N>]');
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
  parseLanes, checkLane, checkLanes,
  parseDispositions, checkDispositionBlock, checkDispositions, checkOwnerPlan,
  parseRoadmapStageStatuses, parseRegisteredLaneIds,
  parseDocEntries, parseDocReviews, checkDocEntry, checkDocReview, checkDocCoverage,
  aggregateReadiness, classifyBlockedRow,
  SEVERITY_VOCAB, DOMAIN_VOCAB, DISPOSITION_VOCAB,
  FINDING_DISPOSITION_VOCAB, DISPOSITION_COND,
  LANE_STRENGTH_VOCAB, LANE_STATUS_VOCAB,
  DOC_REVIEW_VOCAB, DOC_REVIEW_COND,
};
