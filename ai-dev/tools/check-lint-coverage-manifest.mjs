#!/usr/bin/env node
// check-lint-coverage-manifest.mjs
//
// Anti-corruption gate for the PMD/ErrorProne coverage manifest (roadmap
// item 29, design 06 §7). Style precedent: check-lint-migration-manifest.mjs.
//
// Validates `nop-lint/nop-lint-nop/src/main/resources/manifest/pmd-errorprone-coverage.yml`:
//   - enum-set  : the manifest's source_rule set equals the source rule set
//                 parsed from the design 06 §1–§2 tables — a design row
//                 without a manifest entry (and vice versa) is a hard error
//   - fixtures  : every tier-1 fixture path points at a real suite directory
//                 (tier 2/3 fixtures are prospective paths per the N2
//                 adjudication and are NOT existence-checked)
//   - mechanism : every tier-2/3 entry names its L-layer (the N5 weak
//                 cross-check; tier semantics are audit-sampled)
//   - excluded  : every excluded/excluded-with-approximation entry carries a
//                 non-empty reason
//   - vocab     : tier values legal; duplicate source rules rejected
//   - self-test : positive control — known-bad fixtures must be REJECTED by
//                 each checker (no silent skip, Rule #24)
//
// Usage:
//   node ai-dev/tools/check-lint-coverage-manifest.mjs            (all checks)
//   node ai-dev/tools/check-lint-coverage-manifest.mjs self-test  (positive control)
//
// Exit codes: 0 = green, 1 = violations, 2 = usage error.

import { readFileSync, existsSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

const TOOL_DIR = import.meta.dirname;
const PROJECT_ROOT = resolve(TOOL_DIR, '..', '..');
const MANIFEST_FILE = join(PROJECT_ROOT, 'nop-lint', 'nop-lint-nop', 'src', 'main', 'resources',
  'manifest', 'pmd-errorprone-coverage.yml');
const DESIGN_FILE = join(PROJECT_ROOT, 'ai-dev', 'design', 'nop-lint',
  '06-pmd-errorprone-alignment.md');
const TEST_VFS_ROOT = join(PROJECT_ROOT, 'nop-lint', 'nop-lint-nop', 'src', 'test', 'resources',
  '_vfs');

const TIER_VOCAB = new Set([1, 2, 3, 'excluded', 'excluded-with-approximation']);
const LAYER_RE = /^L[1-4]\b|^L[1-4]\/L[1-4]:/;

function fail(errors, message) {
  errors.push(message);
}

// ---------------------------------------------------------------------------
// design 06 §1–§2 table parsing (the enumeration authority)

export function parseDesignRules(designText) {
  const rules = [];
  let source = null;
  for (const rawLine of designText.split('\n')) {
    const line = rawLine.trim();
    if (line.startsWith('## 1.')) {
      source = 'PMD';
      continue;
    }
    if (line.startsWith('## 2.')) {
      source = 'ErrorProne';
      continue;
    }
    if (line.startsWith('## 3.')) {
      break;
    }
    if (source === null || !line.startsWith('|')) {
      continue;
    }
    const m = line.match(/^\|\s*\*\*(.+?)\*\*\s*\|/);
    if (m) {
      rules.push(source + ':' + m[1].trim());
    }
  }
  return rules;
}

// ---------------------------------------------------------------------------
// manifest accessors

function manifestEntries(manifest) {
  return manifest?.entries ?? [];
}

export function checkEnumSet(entries, designRuleNames) {
  const errors = [];
  const manifestRules = new Set(entries.map((entry) => entry.source_rule));
  for (const name of designRuleNames) {
    if (!manifestRules.has(name)) {
      fail(errors, `design rule '${name}' has no manifest entry (the manifest must enumerate the`
        + ' full design 06 §1–§2 sampling)');
    }
  }
  for (const rule of manifestRules) {
    if (!designRuleNames.includes(rule)) {
      fail(errors, `manifest entry '${rule}' does not exist in the design 06 §1–§2 tables (stale`
        + ' or renamed without bookkeeping)');
    }
  }
  return errors;
}

export function checkFixtures(entries) {
  const errors = [];
  for (const entry of entries) {
    if (entry.tier !== 1) {
      continue;
    }
    if (!entry.fixture) {
      fail(errors, `tier-1 entry '${entry.source_rule}' is missing its fixture path (a landed`
        + ' rule without its acceptance fixture is not done)');
      continue;
    }
    const realPath = join(TEST_VFS_ROOT, entry.fixture.replace(/^\//, ''));
    if (!existsSync(realPath)) {
      fail(errors, `tier-1 entry '${entry.source_rule}' fixture '${entry.fixture}' does not`
        + ` exist under the test VFS (${realPath})`);
    }
  }
  return errors;
}

export function checkMechanism(entries) {
  const errors = [];
  for (const entry of entries) {
    if (entry.tier === 2 || entry.tier === 3) {
      const mechanism = entry.mechanism ?? '';
      if (!LAYER_RE.test(mechanism)) {
        fail(errors, `tier-${entry.tier} entry '${entry.source_rule}' mechanism '${mechanism}'`
          + ' does not name its L-layer (must start with L1..L4, N5 weak cross-check)');
      }
    }
  }
  return errors;
}

export function checkExcluded(entries) {
  const errors = [];
  for (const entry of entries) {
    if (entry.tier === 'excluded' || entry.tier === 'excluded-with-approximation') {
      const reason = entry.reason ?? '';
      if (typeof reason !== 'string' || reason.trim().length === 0) {
        fail(errors, `excluded entry '${entry.source_rule}' is missing its exclusion reason`
          + ' (design 06 §7: an exclusion without a reason must not load)');
      }
    }
  }
  return errors;
}

export function checkVocab(entries) {
  const errors = [];
  const seen = new Set();
  for (const entry of entries) {
    if (!TIER_VOCAB.has(entry.tier)) {
      fail(errors, `entry '${entry.source_rule}' has illegal tier '${entry.tier}' (legal:`
        + ` ${[...TIER_VOCAB].join(', ')})`);
    }
    if (seen.has(entry.source_rule)) {
      fail(errors, `duplicate manifest entry '${entry.source_rule}'`);
    }
    seen.add(entry.source_rule);
  }
  return errors;
}

// ---------------------------------------------------------------------------
// self-test: every checker must REJECT its known-bad fixture

export function selfTest() {
  const errors = [];
  const good = { source_rule: 'PMD:Demo', tier: 1, mechanism: 'L1 pattern: demo', fixture: '/x/' };
  const designNames = ['PMD:Demo'];

  if (checkEnumSet([good], designNames).length !== 0) {
    fail(errors, 'self-test: enum-set checker rejected a consistent set');
  }
  if (checkEnumSet([{ ...good, source_rule: 'PMD:Other' }], designNames).length === 0) {
    fail(errors, 'self-test: enum-set checker accepted a stale manifest rule');
  }
  if (checkFixtures([good]).length === 0) {
    fail(errors, 'self-test: fixture checker accepted a non-existent tier-1 fixture directory');
  }
  if (checkMechanism([{ ...good, tier: 2, mechanism: '数据流分析' }]).length === 0) {
    fail(errors, 'self-test: mechanism checker accepted an L-layer-less tier-2 entry');
  }
  if (checkMechanism([{ ...good, tier: 2, mechanism: 'L3: 数据流分析' }]).length !== 0) {
    fail(errors, 'self-test: mechanism checker rejected a proper L-layer tag');
  }
  if (checkExcluded([{ ...good, tier: 'excluded' }]).length === 0) {
    fail(errors, 'self-test: excluded checker accepted a reason-less exclusion');
  }
  if (checkVocab([{ ...good, tier: 9 }]).length === 0) {
    fail(errors, 'self-test: vocab checker accepted an illegal tier');
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
    console.log('usage: node check-lint-coverage-manifest.mjs [self-test]');
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
  const manifest = yaml.load(readFileSync(MANIFEST_FILE, 'utf8'));
  const entries = manifestEntries(manifest);
  const designText = readFileSync(DESIGN_FILE, 'utf8');
  const designRules = parseDesignRules(designText);

  errors.push(...checkEnumSet(entries, designRules));
  errors.push(...checkFixtures(entries));
  errors.push(...checkMechanism(entries));
  errors.push(...checkExcluded(entries));
  errors.push(...checkVocab(entries));

  if (errors.length > 0) {
    console.error(`coverage manifest gate FAILED (${errors.length} violation(s)):`);
    for (const error of errors) {
      console.error('  - ' + error);
    }
    process.exit(1);
  }
  const byTier = {};
  for (const entry of entries) {
    byTier[entry.tier] = (byTier[entry.tier] ?? 0) + 1;
  }
  console.log(`coverage manifest gate ok: ${entries.length} entries vs ${designRules.length}`
    + ` design rows; tiers: ${JSON.stringify(byTier)}`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main();
}
