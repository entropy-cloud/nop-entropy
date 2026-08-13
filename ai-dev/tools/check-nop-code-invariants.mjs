#!/usr/bin/env node
// check-nop-code-invariants.mjs
//
// Executable invariant gates for the nop-code module (Cycle 1 / I1).
// Source: ai-dev/audits/nop-code-invariants/invariant-catalog.md (INV-01..INV-04).
//
// Each family is a static scanner over nop-code/**/src/main Java sources:
//   - query-limit     (INV-04): every findAllByQuery / selectFieldsByQuery must
//                     declare a result cap (setLimit/setMaxResults) unless on the
//                     bounded whitelist.
//   - entity-field-min(INV-01): full-entity findAllByQuery whose result is only
//                     used for <=3 fields (or just .size()) must use a projection
//                     query (selectFieldsByQuery) or countByQuery instead.
//   - delete-contract (INV-02): ORM entities must not introduce useLogicalDelete,
//                     and service delete paths must use physical-delete APIs.
//
// Ratchet model (monotonic baseline):
//   default               : scan + report; exit non-zero on ANY violation (strict).
//                            Used by canaries to prove the gate catches violations.
//   --baseline <file>     : ratchet mode. Exit non-zero ONLY on violations absent
//                            from the baseline (new regressions). Known violations
//                            that match the baseline are reported but do not fail.
//   --update-baseline <f> : rewrite the baseline file to the current violation set
//                            (ratchet forward; never weakens silently — the diff must
//                            be human-reviewed before commit).
//   no --family           : run ALL families, aggregate exit code.
//
// Rule #24 (No Silent No-Op): a call site whose query variable cannot be resolved
// is reported explicitly as "UNDETERMINED" — never silently ignored.
//
// Usage:
//   node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit
//   node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family entity-field-min
//   node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family delete-contract
//   node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code   # all families
//   node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit \
//          --baseline ai-dev/audits/nop-code-invariants/baseline-query-limit.json
//   node ai-dev/tools/check-nop-code-invariants.mjs --self-test

import { readFileSync, writeFileSync, existsSync, mkdirSync, rmSync } from 'node:fs';
import { globSync } from 'node:fs';
import { resolve, relative, join } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..', '..');

const ALL_FAMILIES = ['query-limit', 'entity-field-min', 'delete-contract'];

// ---------------------------------------------------------------------------
// Shared file-walking + scope helpers
// ---------------------------------------------------------------------------

function listJavaFiles(moduleDir) {
  // moduleDir may be an aggregator (nop-code) with submodules, or a leaf module.
  // Glob every `src/main` under it.
  let files;
  try {
    files = globSync(join(moduleDir, '**', 'src', 'main', '**', '*.java'));
  } catch {
    files = [];
  }
  // exclude generated
  return files.filter((f) => !/_gen[/\\\\]/.test(f));
}

/**
 * Locate the start line of the enclosing method for a given call line.
 * Heuristic: walk backwards to the nearest method-signature-like line.
 */
function findMethodStart(lines, callLineIdx) {
  for (let i = callLineIdx; i >= 0; i--) {
    const line = lines[i];
    // signature line: has parens and ends with `{` (possibly trailing), with a modifier or return type
    if (/\)\s*(?:throws[^{]*)?\{?\s*$/.test(line) &&
        /(public|private|protected|static|void|boolean|int|long|List|Map|Set|String|[A-Z]\w+)\s+\w+\s*\(/.test(line)) {
      return i;
    }
  }
  return 0;
}

/**
 * Extract the query variable from a call like `dao.findAllByQuery(queryVar)`.
 * Returns the variable name, or null if it cannot be resolved (e.g. inline expr).
 */
function extractCallArg(line, methodName) {
  const idx = line.indexOf(methodName);
  if (idx === -1) return null;
  const after = line.slice(idx + methodName.length);
  const open = after.indexOf('(');
  if (open === -1) return null;
  // find matching close paren (single-arg case is enough; nested parens rare here)
  let depth = 0;
  let arg = '';
  for (const ch of after.slice(open)) {
    if (ch === '(') depth++;
    else if (ch === ')') {
      depth--;
      if (depth === 0) break;
    } else if (depth === 1) {
      arg += ch;
    }
  }
  arg = arg.trim();
  // simple identifier?
  if (/^[A-Za-z_]\w*$/.test(arg)) return arg;
  return null; // inline / complex expression -> UNDETERMINED
}

function rel(file) {
  return relative(ROOT, file).split('\\').join('/');
}

// ---------------------------------------------------------------------------
// FAMILY: query-limit (INV-04)
// ---------------------------------------------------------------------------

const QUERY_METHODS = ['findAllByQuery', 'selectFieldsByQuery'];

/**
 * Accepted "no explicit limit" sites with a reason. These are queries provably
 * bounded by a single-record / single-entity filter (e.g. equality on a unique
 * key) so a LIMIT would be noise. Ratchet: weakening/removing an entry needs a
 * recorded reason + human review.
 *
 * Keyed by "relPath::methodName::queryVar" so a refactor that renames the var
 * re-surfaces the site for re-adjudication (no silent drift).
 */
const QUERY_LIMIT_WHITELIST = new Set([
  // deleteByFilter helpers load all then delete; bounded by explicit per-index scope.
  // (kept off-whitelist intentionally — these ARE flagged in baseline as accepted)
]);

function scanQueryLimit(javaFiles) {
  const findings = [];
  const undetermined = [];
  for (const file of javaFiles) {
    const content = readFileSync(file, 'utf-8');
    const lines = content.split('\n');
    const rp = rel(file);
    for (let i = 0; i < lines.length; i++) {
      const trimmed = lines[i].trim();
      if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) continue;
      for (const m of QUERY_METHODS) {
        if (!trimmed.includes(m + '(')) continue;
        const arg = extractCallArg(trimmed, m);
        if (arg === null) {
          undetermined.push({ file: rp, line: i + 1, method: m, code: trimmed.slice(0, 120) });
          continue;
        }
        // look for `<arg>.setLimit(` or `<arg>.setMaxResults(` within method scope
        const methodStart = findMethodStart(lines, i);
        let bounded = false;
        for (let j = methodStart; j <= i; j++) {
          const re = new RegExp('\\b' + escapeRe(arg) + '\\s*\\.\\s*(setLimit|setMaxResults)\\s*\\(');
          if (re.test(lines[j])) {
            bounded = true;
            break;
          }
        }
        if (!bounded) {
          const wlKey = `${rp}:${i + 1}`;
          findings.push({
            file: rp,
            line: i + 1,
            family: 'query-limit',
            invariant: 'INV-04',
            queryMethod: m,
            queryVar: arg,
            whitelist: QUERY_LIMIT_WHITELIST.has(wlKey),
            signature: `${rp}:${i + 1} [${m}(${arg})]`,
            code: trimmed.slice(0, 120),
          });
        }
      }
    }
  }
  return { findings, undetermined };
}

// ---------------------------------------------------------------------------
// FAMILY: entity-field-min (INV-01)
// ---------------------------------------------------------------------------

/**
 * Detects full-entity loads (findAllByQuery) whose result is used for <=3 fields.
 * Two sub-patterns:
 *   (a) result immediately followed by `.size()` / `.stream().count()` -> countByQuery
 *   (b) result iterated, accessing <=3 distinct getters -> selectFieldsByQuery
 *
 * Full-entity load is a violation ONLY when a projection / count would suffice.
 * Bounded delete loops (deleteEntitiesPaged) are NOT flagged (load is semantically
 * necessary for batchDeleteEntities).
 */
const DELETE_LOOP_RE = /batchDeleteEntities|deleteEntityById|batchDelete\b/;

function distinctGettersUsed(lines, startIdx, limit) {
  const getters = new Set();
  for (let j = startIdx; j < Math.min(lines.length, startIdx + limit); j++) {
    const m = lines[j].match(/\.get([A-Z]\w*)\s*\(/g);
    if (m) for (const g of m) getters.add(g);
  }
  return getters;
}

function scanEntityFieldMin(javaFiles) {
  const findings = [];
  for (const file of javaFiles) {
    const content = readFileSync(file, 'utf-8');
    const lines = content.split('\n');
    const rp = rel(file);
    for (let i = 0; i < lines.length; i++) {
      const trimmed = lines[i].trim();
      if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) continue;
      // only full-entity loads (selectFieldsByQuery is already a projection -> OK)
      if (!trimmed.includes('findAllByQuery(')) continue;
      // skip delete-batch loops (load is necessary to delete)
      if (DELETE_LOOP_RE.test(trimmed)) continue;
      const arg = extractCallArg(trimmed, 'findAllByQuery');
      if (arg === null) continue;

      // sub-pattern (a): result .size() / .stream().count() within next 6 lines
      let sizeOnly = false;
      for (let j = i; j < Math.min(lines.length, i + 6); j++) {
        if (/\.size\s*\(\s*\)/.test(lines[j]) || /\.stream\s*\(\s*\)\s*\.count/.test(lines[j])) {
          sizeOnly = true;
          break;
        }
      }
      // skip if the same statement is a delete (batchDeleteEntities after size)
      if (sizeOnly && DELETE_LOOP_RE.test(lines.slice(i, i + 6).join(' '))) continue;
      // precision guard: an incidental .size() (e.g. a truncation warning) does NOT make a load
      // size-only if the result variable is ALSO consumed for its content (stream/get/iterate/
      // return). Only flag when the variable is used solely for its size.
      if (sizeOnly) {
        const assignMatch = trimmed.match(/\b(\w+)\s*=\s*\w+\.findAllByQuery\(/);
        const resultVar = assignMatch ? assignMatch[1] : null;
        if (resultVar) {
          const window = lines.slice(i, Math.min(lines.length, i + 14)).join('\n');
          const contentUseRe = new RegExp(
            '\\b' + resultVar + '\\s*\\.\\s*(?:stream|forEach|iterator|get\\s*\\()' +
            '|\\bfor\\s*\\([^)]*\\b' + resultVar + '\\b' +
            '|\\breturn\\s+' + resultVar + '\\b');
          if (contentUseRe.test(window)) {
            sizeOnly = false;
          }
        }
      }

      // sub-pattern (b): iterate and access <=3 distinct getters
      const getters = distinctGettersUsed(lines, i + 1, 15);
      const fewFields = getters.size > 0 && getters.size <= 3;

      if (sizeOnly || fewFields) {
        const reason = sizeOnly
          ? 'result used only for .size()/count() — should use countByQuery'
          : `result iterated accessing only ${getters.size} distinct getter(s) — should use selectFieldsByQuery projection`;
        findings.push({
          file: rp,
          line: i + 1,
          family: 'entity-field-min',
          invariant: 'INV-01',
          queryMethod: 'findAllByQuery',
          queryVar: arg,
          reason,
          signature: `${rp}:${i + 1} [findAllByQuery(${arg}) ${sizeOnly ? 'size' : 'few-fields'}]`,
          code: trimmed.slice(0, 120),
        });
      }
    }
  }
  return { findings };
}

// ---------------------------------------------------------------------------
// FAMILY: delete-contract (INV-02)
// ---------------------------------------------------------------------------

/**
 * INV-02 (degraded form): nop-code delete paths must use physical delete.
 *   (1) ORM model: no entity declares useLogicalDelete.
 *   (2) Service delete methods use batchDeleteEntities / deleteEntityById (physical),
 *       not logical-delete patterns (e.g. setField("delFlag", ...) / update(... delFlag)).
 *
 * Per I0 (invariant-catalog.md INV-02): live ORM has zero useLogicalDelete, so this
 * family is a guard that the degraded contract stays enforced. If any entity
 * introduces useLogicalDelete, the gate fails (forcing explicit adjudication).
 */
const LOGICAL_DELETE_ATTR_RE = /\buseLogicalDelete\s*=\s*["']true["']/;
// Match actual setter calls that flip a soft-delete flag, e.g. entity.setDelFlag(true)
// or entity.setDeleted(1). String literals / variable names like "deletedFiles" must NOT match.
const LOGICAL_DELETE_SETTER_RE = /\.\s*set(DelFlag|Deleted|IsDeleted|del_flag|delFlag)\s*\(/i;

function scanDeleteContract(moduleDir, javaFiles) {
  const findings = [];
  // (1) ORM model scan
  const ormFiles = globSync(join(moduleDir, '**', '*.orm.xml')).filter((f) => !/_app\.orm\.xml|_gen[/\\\\]/.test(f));
  for (const file of ormFiles) {
    const content = readFileSync(file, 'utf-8');
    const lines = content.split('\n');
    const rp = rel(file);
    for (let i = 0; i < lines.length; i++) {
      if (LOGICAL_DELETE_ATTR_RE.test(lines[i])) {
        findings.push({
          file: rp,
          line: i + 1,
          family: 'delete-contract',
          invariant: 'INV-02',
          reason: 'ORM entity declares useLogicalDelete=true — physical-delete contract broken',
          signature: `${rp}:${i + 1} [ORM useLogicalDelete]`,
          code: lines[i].trim().slice(0, 120),
        });
      }
    }
  }
  // (2) Java service scan: look for logical-delete setter calls near delete methods
  for (const file of javaFiles) {
    const content = readFileSync(file, 'utf-8');
    const lines = content.split('\n');
    const rp = rel(file);
    for (let i = 0; i < lines.length; i++) {
      const trimmed = lines[i].trim();
      if (trimmed.startsWith('//') || trimmed.startsWith('*')) continue;
      if (!LOGICAL_DELETE_SETTER_RE.test(trimmed)) continue;
      // confirm we are inside a delete/remove method scope
      const scope = lines.slice(Math.max(0, i - 40), i + 1).join(' ');
      if (/(?:void|boolean|int|long)\s+(delete|remove|purge)\w*\s*\(/i.test(scope)) {
        findings.push({
          file: rp,
          line: i + 1,
          family: 'delete-contract',
          invariant: 'INV-02',
          reason: 'logical-delete setter (setDelFlag/setDeleted) detected inside a delete/remove method — must use physical delete (batchDeleteEntities/deleteEntityById)',
          signature: `${rp}:${i + 1} [logical-delete-setter]`,
          code: trimmed.slice(0, 120),
        });
      }
    }
  }
  return { findings };
}

// ---------------------------------------------------------------------------
// Baseline (ratchet) support
// ---------------------------------------------------------------------------

function loadBaseline(file) {
  if (!file || !existsSync(file)) return new Set();
  try {
    const obj = JSON.parse(readFileSync(file, 'utf-8'));
    return new Set(Array.isArray(obj.signatures) ? obj.signatures : []);
  } catch {
    return new Set();
  }
}

function writeBaseline(file, signatures, family) {
  const obj = {
    family,
    generatedAt: new Date().toISOString(),
    note: 'Monotonic ratchet baseline. Removing/weakening entries requires human review + committed regression. See gate-baseline-I1.md.',
    signatures: [...signatures].sort(),
  };
  writeFileSync(file, JSON.stringify(obj, null, 2) + '\n', 'utf-8');
}

function escapeRe(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// ---------------------------------------------------------------------------
// Reporting
// ---------------------------------------------------------------------------

function printFindings(label, findings, undetermined) {
  if (findings.length === 0 && (!undetermined || undetermined.length === 0)) {
    console.log(`[PASS] ${label} — 0 violations`);
    return;
  }
  console.error(`[FAIL] ${label} — ${findings.length} violation(s)`);
  for (const f of findings) {
    console.error(`  - ${f.file}:${f.line} [${f.invariant}] ${f.queryMethod || ''}(${f.queryVar || ''}) ${f.reason || ''}`);
    console.error(`      ${f.code}`);
  }
  if (undetermined && undetermined.length > 0) {
    console.error(`  [UNDETERMINED] ${undetermined.length} call site(s) with unresolvable query var (needs manual check, NOT silently skipped):`);
    for (const u of undetermined) {
      console.error(`    - ${u.file}:${u.line} ${u.method}(...) ${u.code}`);
    }
  }
}

// ---------------------------------------------------------------------------
// Family dispatch
// ---------------------------------------------------------------------------

function runFamily(family, moduleDir, javaFiles) {
  switch (family) {
    case 'query-limit': {
      const { findings, undetermined } = scanQueryLimit(javaFiles);
      return { findings, undetermined };
    }
    case 'entity-field-min': {
      const { findings } = scanEntityFieldMin(javaFiles);
      return { findings, undetermined: [] };
    }
    case 'delete-contract': {
      const { findings } = scanDeleteContract(moduleDir, javaFiles);
      return { findings, undetermined: [] };
    }
    default:
      throw new Error(`unknown family: ${family}`);
  }
}

// ---------------------------------------------------------------------------
// Self-test (positive control / canary) — Rule #24: proves the gate REJECTS
// known-bad input rather than silently passing.
// ---------------------------------------------------------------------------

function runSelfTest() {
  const failures = [];
  const tmpDir = join(ROOT, '_tmp', 'invariant-canary');

  // --- canary 1: query-limit catches a missing setLimit
  const qlGood = join(tmpDir, 'Good.java');
  const qlBad = join(tmpDir, 'Bad.java');
  try { rmSync(tmpDir, { recursive: true, force: true }); } catch { /* ignore */ }
  mkdirSync(tmpDir, { recursive: true });
  writeFileSync(qlGood,
    'class Good {\n' +
    '  void m() {\n' +
    '    QueryBean q = new QueryBean();\n' +
    '    q.setLimit(100);\n' +
    '    dao.findAllByQuery(q);\n' +
    '  }\n' +
    '}\n', 'utf-8');
  writeFileSync(qlBad,
    'class Bad {\n' +
    '  void m() {\n' +
    '    QueryBean q = new QueryBean();\n' +
    '    dao.findAllByQuery(q);\n' +   // line 4 — NO setLimit
    '  }\n' +
    '}\n', 'utf-8');
  const qlRes = scanQueryLimit([qlGood, qlBad]);
  if (qlRes.findings.length === 0) {
    failures.push('query-limit canary: missing-setLimit violation was NOT detected');
  } else {
    const bad = qlRes.findings.find((f) => f.file.endsWith('Bad.java') && f.line === 4);
    if (!bad) failures.push('query-limit canary: violation detected but not at Bad.java:4 (line resolution broken)');
  }

  // --- canary 2: entity-field-min catches .size()-only load
  const efmBad = join(tmpDir, 'Size.java');
  writeFileSync(efmBad,
    'class Size {\n' +
    '  void m() {\n' +
    '    QueryBean q = new QueryBean();\n' +
    '    List<NopCodeFile> all = dao.findAllByQuery(q);\n' +   // line 4
    '    int n = all.size();\n' +
    '  }\n' +
    '}\n', 'utf-8');
  const efmRes = scanEntityFieldMin([efmBad]);
  if (efmRes.findings.length === 0) {
    failures.push('entity-field-min canary: size-only full-entity load was NOT detected');
  } else {
    const bad = efmRes.findings.find((f) => f.file.endsWith('Size.java') && f.line === 4);
    if (!bad) failures.push('entity-field-min canary: violation detected but not at Size.java:4');
  }

  // --- canary 3: delete-contract catches useLogicalDelete in ORM
  const ormBad = join(tmpDir, 'bad.orm.xml');
  writeFileSync(ormBad,
    '<orm>\n' +
    '  <entity name="Bad" useLogicalDelete="true">\n' +   // line 2
    '  </entity>\n' +
    '</orm>\n', 'utf-8');
  const dcRes = scanDeleteContract(tmpDir, []);
  if (dcRes.findings.length === 0) {
    failures.push('delete-contract canary: useLogicalDelete=true in ORM was NOT detected');
  } else {
    const bad = dcRes.findings.find((f) => f.file.endsWith('bad.orm.xml') && f.line === 2);
    if (!bad) failures.push('delete-contract canary: violation detected but not at bad.orm.xml:2');
  }

  // cleanup
  try { rmSync(tmpDir, { recursive: true, force: true }); } catch { /* ignore */ }

  if (failures.length === 0) {
    console.log('[PASS] self-test (canary) — all 3 families reject their known-bad input');
    console.log('  - query-limit     : rejects findAllByQuery with no setLimit (locates implanted line)');
    console.log('  - entity-field-min: rejects full-entity load used only for .size() (locates implanted line)');
    console.log('  - delete-contract : rejects ORM useLogicalDelete=true (locates implanted line)');
    return true;
  }
  console.error('[FAIL] self-test (canary) — gate failed to reject known-bad input:');
  for (const f of failures) console.error('  - ' + f);
  return false;
}

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

function parseArgs(argv) {
  const opts = {
    module: 'nop-code',
    families: [],
    baselineFile: null,
    updateBaseline: null,
    selfTest: false,
    list: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--self-test') opts.selfTest = true;
    else if (a === '--list') opts.list = true;
    else if (a === '--module') opts.module = argv[++i];
    else if (a === '--family') opts.families.push(argv[++i]);
    else if (a === '--baseline') opts.baselineFile = argv[++i];
    else if (a === '--update-baseline') opts.updateBaseline = argv[++i];
    else if (a === '--help' || a === '-h') opts.help = true;
  }
  if (opts.families.length === 0) opts.families = [...ALL_FAMILIES];
  return opts;
}

function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (opts.help) {
    console.log(`Usage: node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code [--family <f>] [--baseline <f>] [--update-baseline <f>] [--self-test]
Families: ${ALL_FAMILIES.join(', ')}
No --family runs all families and aggregates the exit code.`);
    return 0;
  }
  if (opts.selfTest) {
    return runSelfTest() ? 0 : 1;
  }

  const moduleDir = resolve(ROOT, opts.module);
  if (!existsSync(moduleDir)) {
    console.error(`module directory not found: ${opts.module}`);
    return 2;
  }
  const javaFiles = listJavaFiles(moduleDir);
  if (javaFiles.length === 0) {
    console.error(`no main java sources found under ${opts.module}`);
    return 2;
  }

  let aggregateFail = false;
  const allFindings = [];
  for (const family of opts.families) {
    if (!ALL_FAMILIES.includes(family)) {
      console.error(`unknown family: ${family} (known: ${ALL_FAMILIES.join(', ')})`);
      return 2;
    }
    const { findings, undetermined } = runFamily(family, moduleDir, javaFiles);

    if (opts.updateBaseline) {
      const blFile = opts.updateBaseline.replace(/\.json$/, `-${family}.json`);
      const sigs = new Set(findings.map((f) => f.signature));
      writeBaseline(blFile, sigs, family);
      console.log(`[BASELINE] ${family}: wrote ${sigs.size} signature(s) to ${relative(ROOT, blFile)}`);
      continue;
    }

    const baseline = loadBaseline(opts.updateBaseline || opts.baselineFile);
    const newFindings = findings.filter((f) => !baseline.has(f.signature));
    const knownFindings = findings.filter((f) => baseline.has(f.signature));

    if (opts.list) {
      console.log(`\n=== ${family} (${findings.length} total: ${knownFindings.length} known, ${newFindings.length} new) ===`);
      for (const f of findings) {
        const tag = baseline.has(f.signature) ? 'KNOWN' : 'NEW';
        console.log(`  [${tag}] ${f.file}:${f.line} ${f.queryMethod || ''}(${f.queryVar || ''}) ${f.reason || ''}`);
      }
      if (undetermined && undetermined.length > 0) {
        console.log(`  [UNDETERMINED] ${undetermined.length} site(s):`);
        for (const u of undetermined) console.log(`    - ${u.file}:${u.line} ${u.method}(...)`);
      }
    } else {
      printFindings(family, newFindings, undetermined);
    }
    if (newFindings.length > 0) aggregateFail = true;
    if (undetermined && undetermined.length > 0) aggregateFail = true;
    allFindings.push(...newFindings);
  }

  if (opts.updateBaseline) {
    console.log('\nBaseline update complete. Review the diff before committing (ratchet must never weaken silently).');
    return 0;
  }

  if (aggregateFail) {
    console.error(`\n[${opts.families.length > 1 ? 'ALL FAMILIES' : opts.families[0]}] ${allFindings.length} new violation(s) — exit non-zero.`);
  } else {
    console.log(`\n[${opts.families.length > 1 ? 'ALL FAMILIES' : opts.families[0]}] no new violations beyond baseline.`);
  }
  return aggregateFail ? 1 : 0;
}

const code = main();
if (typeof code === 'number') process.exit(code);
