#!/usr/bin/env node

/**
 * check-eql-dialect-matrix-snapshot.mjs
 *
 * EQL WI0 dialect capability matrix snapshot gate
 * (plan: ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md, WI0 in
 * ai-dev/backlog/eql-cross-db-compat-roadmap.md).
 *
 * Ratchet rule: every `*.dialect.xml` under
 * `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/` must be
 * registered in `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`.
 * Adding a dialect XML without updating the snapshot turns this gate red
 * (exit 1). Symmetric check: a snapshot entry whose dialect XML no longer
 * exists is stale and also red.
 *
 * The JUnit twin (`TestDialectCapabilityMatrix`) additionally compares the
 * full snapshot content byte-for-byte against a live rebuild; this script is
 * the cheap structural gate runnable without a JVM.
 *
 * Exit codes:
 *   0 = snapshot exists, parses, and every dialect XML is registered
 *   1 = violations found (missing/stale registrations, malformed snapshot)
 *   2 = internal error
 *
 * Usage:
 *   node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs
 */

import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..');
const SNAPSHOT_REL = 'ai-dev/audits/eql-wi0/dialect-capability-matrix.json';
const DIALECT_DIR_REL = 'nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect';

function listDialectXmls() {
  const dir = join(PROJECT_ROOT, DIALECT_DIR_REL);
  if (!existsSync(dir)) {
    console.error(`Internal error: dialect dir not found: ${DIALECT_DIR_REL}`);
    process.exit(2);
  }
  return readdirSync(dir)
    .filter((name) => name.endsWith('.dialect.xml'))
    .map((name) => name.slice(0, -'.dialect.xml'.length))
    .sort();
}

function main() {
  const snapshotPath = join(PROJECT_ROOT, SNAPSHOT_REL);
  const issues = [];

  if (!existsSync(snapshotPath)) {
    console.error(`Snapshot not found: ${SNAPSHOT_REL}`);
    console.error('Regenerate with: ./mvnw test -pl :nop-dao -Dtest=TestDialectCapabilityMatrix -Dnop.dao.matrix.updateSnapshot=true');
    process.exit(1);
  }

  let snapshot;
  try {
    snapshot = JSON.parse(readFileSync(snapshotPath, 'utf-8'));
  } catch (e) {
    console.error(`Snapshot is not valid JSON: ${SNAPSHOT_REL}`);
    console.error(String(e && e.message ? e.message : e));
    process.exit(1);
  }

  const dialects = snapshot && typeof snapshot === 'object' ? snapshot.dialects : null;
  if (!dialects || typeof dialects !== 'object' || Array.isArray(dialects)) {
    console.error(`Snapshot has no "dialects" object: ${SNAPSHOT_REL}`);
    process.exit(1);
  }

  const dialectXmls = listDialectXmls();
  const registered = new Set(Object.keys(dialects));

  for (const name of dialectXmls) {
    if (!registered.has(name)) {
      issues.push({
        rule: 'UNREGISTERED_DIALECT',
        message: `dialect XML "${name}.dialect.xml" exists in ${DIALECT_DIR_REL} but is not registered in ${SNAPSHOT_REL} (add a dialect requires updating the snapshot)`,
      });
    } else {
      const entry = dialects[name];
      if (!entry || typeof entry !== 'object' || typeof entry.loadable !== 'boolean') {
        issues.push({
          rule: 'INVALID_SNAPSHOT_ENTRY',
          message: `snapshot entry "${name}" must be an object with a boolean "loadable" field`,
        });
      }
    }
  }

  const xmlNames = new Set(dialectXmls);
  for (const name of [...registered].sort()) {
    if (!xmlNames.has(name)) {
      issues.push({
        rule: 'STALE_SNAPSHOT_ENTRY',
        message: `snapshot registers "${name}" but no matching *.dialect.xml exists in ${DIALECT_DIR_REL} (remove the stale entry)`,
      });
    }
  }

  const loadable = [...registered].filter((n) => dialects[n] && dialects[n].loadable === true).length;
  const nonLoadable = [...registered].filter((n) => dialects[n] && dialects[n].loadable === false).length;

  // Human-readable report (style of check-doc-links.mjs)
  console.log('# EQL Dialect Capability Matrix Snapshot Check\n');
  console.log(`- **Snapshot**: ${SNAPSHOT_REL}`);
  console.log(`- **Dialect XMLs in repo**: ${dialectXmls.length}`);
  console.log(`- **Registered entries**: ${registered.size} (loadable: ${loadable}, non-loadable: ${nonLoadable})`);

  if (issues.length > 0) {
    console.log(`\n## Errors (${issues.length})\n`);
    for (const issue of issues) {
      console.log(`- **[${issue.rule}]** ${issue.message}`);
    }
    console.error(`\n${issues.length} error(s) found.`);
    process.exit(1);
  }

  console.log('\nNo errors found. Snapshot registrations match the dialect XML inventory.');
  process.exit(0);
}

try {
  main();
} catch (e) {
  console.error('Internal error:', e);
  process.exit(2);
}
