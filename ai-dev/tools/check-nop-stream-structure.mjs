#!/usr/bin/env node
// check-nop-stream-structure.mjs
//
// Structure-gate scanner for the nop-stream structure-refactor roadmap
// (ai-dev/backlog/nop-stream-structure-refactor-roadmap.md, WI12 gate hardening —
// plan 2436). Turns three structural invariants into executable checks so the
// CI can block regressions:
//
//   core-no-runtime-dep      - nop-stream-core/src (main + test, comments included)
//                              must contain zero occurrences of the string
//                              `io.nop.stream.runtime`. The comment face was
//                              zeroed by roadmap WI3, the reflection face by
//                              WI7 (WindowedStreamImpl Class.forName removal);
//                              this gate keeps both at zero (raw text match on
//                              purpose — a javadoc {@link} regeneration must red).
//   runtime-package-acyclicity - the four runtime packages transport / rpc /
//                              taskmanager / coordinator must form an acyclic
//                              import graph (WI6 broke the transport→rpc→
//                              taskmanager and coordinator↔taskmanager cycles;
//                              this gate keeps them broken). Edges are built
//                              from `import io.nop.stream.runtime.<pkg>.`
//                              declarations (regular + static) between the
//                              four packages only; edges to other packages
//                              (checkpoint, protocol, ...) are out of scope.
//                              A missing package directory is a hard error —
//                              a renamed package must not silently pass.
//   connector-test-fan-in    - WI5 disposition (b): every connector-family
//                              TEST dependency declared in nop-stream-runtime/
//                              pom.xml must have at least one live reference in
//                              the runtime test tree (import or string FQN).
//                              Reference matching is per-artifact and MUST NOT
//                              let a subpackage consumer substitute for the
//                              root artifact:
//                                nop-stream-connector           -> io.nop.stream.connector.<UpperCase> (root-package classes only)
//                                                                  or io.nop.stream.connector.file.
//                                nop-stream-connector-batch     -> io.nop.stream.connector.batch.
//                                nop-stream-connector-jdbc      -> io.nop.stream.connector.jdbc.
//                                nop-stream-connector-kafka     -> io.nop.stream.connector.kafka.
//                                nop-stream-connector-debezium  -> io.nop.message.debezium.
//                                nop-stream-rocksdb             -> io.nop.stream.rocksdb.
//                              NOTE (non-obvious, do not "fix"): the debezium
//                              mapping points at io.nop.message.debezium — that
//                              package is provided TRANSITIVELY by the
//                              connector-debezium artifact; the runtime pom has
//                              no direct nop-message-debezium dependency. The
//                              root-package pattern uses `connector.[A-Z]` so
//                              that `connector.batch.` / `connector.jdbc.` /
//                              `connector.kafka.` (lowercase subpackages) can
//                              never satisfy the nop-stream-connector assertion.
//                              The pom must also keep the WI5 registration
//                              marker "AR-2 test-scope exception register".
//                              An unknown connector-family artifact is a hard
//                              error (a new dep must be mapped, not ignored).
//
// Commands:
//   (no argument)  - run all three gates against the live repo
//   self-test      - positive control: synthetic fixtures prove every scanner
//                    rejects known-bad input and accepts clean input
//                    (no silent skip discipline, mirroring
//                    check-nop-stream-invariants.mjs)
//
// Exit code 0 = all gates green; 1 = violations; 2 = usage / hard error.

import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = import.meta.dirname;
const PROJECT_ROOT = resolve(TOOL_DIR, '..', '..');

const CORE_SRC = join(PROJECT_ROOT, 'nop-stream', 'nop-stream-core', 'src');
const RUNTIME_MAIN = join(PROJECT_ROOT, 'nop-stream', 'nop-stream-runtime', 'src', 'main', 'java', 'io', 'nop', 'stream', 'runtime');
const RUNTIME_TEST = join(PROJECT_ROOT, 'nop-stream', 'nop-stream-runtime', 'src', 'test');
const RUNTIME_POM = join(PROJECT_ROOT, 'nop-stream', 'nop-stream-runtime', 'pom.xml');

const CYCLE_PACKAGES = ['transport', 'rpc', 'taskmanager', 'coordinator'];
const AR2_MARKER = 'AR-2 test-scope exception register';

// artifactId -> reference patterns (string form; any occurrence counts: import,
// static import, string FQN, javadoc). See the file header for why the root
// pattern must be UpperCase-anchored and why debezium maps cross-module.
const FANIN_MAPPINGS = [
  {artifact: 'nop-stream-connector-debezium', patterns: [/io\.nop\.message\.debezium\./]},
  {artifact: 'nop-stream-connector-batch', patterns: [/io\.nop\.stream\.connector\.batch\./]},
  {artifact: 'nop-stream-connector-kafka', patterns: [/io\.nop\.stream\.connector\.kafka\./]},
  {artifact: 'nop-stream-connector-jdbc', patterns: [/io\.nop\.stream\.connector\.jdbc\./]},
  {artifact: 'nop-stream-connector', patterns: [/io\.nop\.stream\.connector\.[A-Z]/, /io\.nop\.stream\.connector\.file\./]},
  {artifact: 'nop-stream-rocksdb', patterns: [/io\.nop\.stream\.rocksdb\./]}
];
const FANIN_ARTIFACT_RE = /^nop-stream-(connector[\w-]*|rocksdb)$/;

// ---------------------------------------------------------------------------
// shared walkers

function collectFiles(dir, map, extension) {
  if (!existsSync(dir)) {
    throw new Error(`required directory missing: ${dir} (no silent skip)`);
  }
  const walk = current => {
    for (const entry of readdirSync(current, {withFileTypes: true})) {
      const full = join(current, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.isFile() && (!extension || entry.name.endsWith(extension))) {
        map.set(relative(PROJECT_ROOT, full), readFileSync(full, 'utf-8'));
      }
    }
  };
  walk(dir);
  return map;
}

// ---------------------------------------------------------------------------
// gate 1: core-no-runtime-dep

function scanCoreReverseDeps(files) {
  const violations = [];
  for (const [rel, src] of [...files.entries()].sort()) {
    const lines = src.split('\n');
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].includes('io.nop.stream.runtime')) {
        violations.push(`[core-no-runtime-dep] ${rel}:${i + 1}: ${lines[i].trim().slice(0, 120)}`);
      }
    }
  }
  return violations;
}

// ---------------------------------------------------------------------------
// gate 2: runtime-package-acyclicity

const IMPORT_EDGE_RE = /^\s*import\s+(?:static\s+)?io\.nop\.stream\.runtime\.([a-z]+)\./gm;

/** edges: Map<pkg, Set<pkg>> restricted to CYCLE_PACKAGES; unknown dep package ignored. */
function buildPackageEdges(files, packages) {
  const edges = new Map(packages.map(p => [p, new Set()]));
  for (const [rel, src] of files) {
    const from = rel.split('io/nop/stream/runtime/')[1]?.split('/')[0];
    if (!from || !edges.has(from)) continue;
    IMPORT_EDGE_RE.lastIndex = 0;
    let m;
    while ((m = IMPORT_EDGE_RE.exec(src)) !== null) {
      const to = m[1];
      if (edges.has(to) && to !== from) edges.get(from).add(to);
    }
  }
  return edges;
}

/** Returns the first cycle path (array) or null. Self-edges excluded upstream. */
function findCycle(edges) {
  const WHITE = 0, GRAY = 1, BLACK = 2;
  const color = new Map([...edges.keys()].map(p => [p, WHITE]));
  const path = [];
  let found = null;
  const dfs = node => {
    if (found) return;
    color.set(node, GRAY);
    path.push(node);
    for (const next of [...(edges.get(node) || [])].sort()) {
      if (color.get(next) === GRAY) {
        found = path.slice(path.indexOf(next));
        return;
      }
      if (color.get(next) === WHITE) dfs(next);
      if (found) return;
    }
    path.pop();
    color.set(node, BLACK);
  };
  for (const p of [...edges.keys()].sort()) {
    if (color.get(p) === WHITE) dfs(p);
    if (found) break;
  }
  return found;
}

function scanPackageCycles(files, packages) {
  const violations = [];
  for (const p of packages) {
    if (!existsSync(join(RUNTIME_MAIN, p))) {
      throw new Error(`[runtime-package-acyclicity] package directory missing: io.nop.stream.runtime.${p} (renamed? update CYCLE_PACKAGES — no silent skip)`);
    }
  }
  const cycle = findCycle(buildPackageEdges(files, packages));
  if (cycle) {
    violations.push(`[runtime-package-acyclicity] package import cycle: ${cycle.join(' -> ')} -> ${cycle[0]}`);
  }
  return violations;
}

// ---------------------------------------------------------------------------
// gate 3: connector-test-fan-in

/** Extract test-scope connector-family dependencies from pom XML text. */
function extractConnectorTestDeps(pomXml) {
  const deps = [];
  const blockRe = /<dependency>[\s\S]*?<\/dependency>/g;
  let m;
  while ((m = blockRe.exec(pomXml)) !== null) {
    const block = m[0];
    const artifact = block.match(/<artifactId>([^<]+)<\/artifactId>/)?.[1];
    if (!artifact || !FANIN_ARTIFACT_RE.test(artifact)) continue;
    const scope = block.match(/<scope>([^<]+)<\/scope>/)?.[1] || 'compile';
    if (scope !== 'test') continue;
    deps.push(artifact);
  }
  return deps;
}

function evaluateFanIn(pomXml, testFiles) {
  const violations = [];
  const hardErrors = [];
  if (!pomXml.includes(AR2_MARKER)) {
    violations.push(`[connector-test-fan-in] runtime pom lost the WI5 registration marker '${AR2_MARKER}' (re-add the disposition record)`);
  }
  const declared = extractConnectorTestDeps(pomXml);
  for (const artifact of declared) {
    const mapping = FANIN_MAPPINGS.find(mm => mm.artifact === artifact);
    if (!mapping) {
      hardErrors.push(`[connector-test-fan-in] unmapped connector-family test dependency '${artifact}' — add a FANIN_MAPPINGS entry (no silent skip)`);
      continue;
    }
    const consumers = [];
    for (const [rel, src] of testFiles) {
      if (mapping.patterns.some(re => re.test(src))) consumers.push(rel);
    }
    if (consumers.length === 0) {
      violations.push(`[connector-test-fan-in] test dependency '${artifact}' has no live consumer in the runtime test tree (WI5 disposition b: drop the dep or re-adjudicate with a registered exception)`);
    }
  }
  return {violations, hardErrors};
}

// ---------------------------------------------------------------------------
// self-test (positive control on synthetic fixtures — no repo writes)

function runSelfTest() {
  const failures = [];

  // gate 1: raw-text match must flag an import and a javadoc/comment mention, accept clean text
  const badCore = new Map([['f/A.java', 'import io.nop.stream.runtime.task.StreamTask;\n// see io.nop.stream.runtime.task.StreamTask\n']]);
  const g1Bad = scanCoreReverseDeps(badCore);
  if (g1Bad.length !== 2 || !g1Bad[0].includes('f/A.java:1') || !g1Bad[1].includes('f/A.java:2')) {
    failures.push(`gate1 self-test: expected import + comment hits at lines 1-2, got ${JSON.stringify(g1Bad)}`);
  }
  if (scanCoreReverseDeps(new Map([['f/Clean.java', 'package io.nop.stream.core;\nimport io.nop.stream.core.api.X;\n']])).length !== 0) {
    failures.push('gate1 self-test: clean core source must produce 0 violations');
  }

  // gate 2: three-node cycle must be found; the live-shaped DAG must pass.
  // Fixture rel paths must mirror the real tree layout (buildPackageEdges
  // derives the source package from the io/nop/stream/runtime/ segment).
  const mkFile = (pkg, imports) => [`src/main/java/io/nop/stream/runtime/${pkg}/X.java`,
    `package io.nop.stream.runtime.${pkg};\n` + imports.map(i => `import io.nop.stream.runtime.${i}.Y;\n`).join('') + 'class X {}\n'];
  const cyclic = new Map([mkFile('transport', ['rpc']), mkFile('rpc', ['taskmanager']), mkFile('taskmanager', ['transport'])]);
  const cyc = findCycle(buildPackageEdges(cyclic, CYCLE_PACKAGES));
  if (!cyc || cyc.length !== 3) {
    failures.push(`gate2 self-test: expected a 3-node cycle, got ${JSON.stringify(cyc)}`);
  }
  const dag = new Map([mkFile('transport', ['rpc']), mkFile('rpc', ['checkpoint']), mkFile('taskmanager', ['rpc', 'transport']), mkFile('coordinator', ['rpc'])]);
  const cyc2 = findCycle(buildPackageEdges(dag, CYCLE_PACKAGES));
  if (cyc2) {
    failures.push(`gate2 self-test: forward-only edges + out-of-scope dep must be acyclic, got ${JSON.stringify(cyc2)}`);
  }

  // gate 3: consumer present -> green; subpackage substitution -> red (must not
  // satisfy the root artifact); no consumer -> red; missing marker -> red;
  // unmapped artifact -> hard error.
  const pomOk = `<project><dependencies>
    <dependency><groupId>g</groupId><artifactId>nop-stream-connector</artifactId><scope>test</scope></dependency>
    <dependency><groupId>g</groupId><artifactId>nop-stream-connector-batch</artifactId><scope>test</scope></dependency>
    <dependency><groupId>g</groupId><artifactId>nop-stream-core</artifactId></dependency>
  </dependencies></project>`.replace('<project>', `<project><!-- ${AR2_MARKER} (WI5 plan 2415) -->`);
  const filesOk = new Map([
    ['t/Root.java', 'import io.nop.stream.connector.MessageSource;\n'],
    ['t/File.java', 'import io.nop.stream.connector.file.FileSink;\n'],
    ['t/Batch.java', 'import io.nop.stream.connector.batch.BatchLoaderSourceFunction;\n']
  ]);
  const ok = evaluateFanIn(pomOk, filesOk);
  if (ok.violations.length !== 0 || ok.hardErrors.length !== 0) {
    failures.push(`gate3 self-test: fully-consumed pom must be green, got ${JSON.stringify(ok)}`);
  }
  const filesSubstitute = new Map([['t/Batch.java', 'import io.nop.stream.connector.batch.BatchLoaderSourceFunction;\n']]);
  const sub = evaluateFanIn(pomOk, filesSubstitute);
  if (!sub.violations.some(v => v.includes("'nop-stream-connector'"))) {
    failures.push(`gate3 self-test: subpackage consumer must NOT satisfy the root connector artifact, got ${JSON.stringify(sub.violations)}`);
  }
  const empty = evaluateFanIn(pomOk, new Map([['t/None.java', 'class None {}\n']]));
  if (empty.violations.length !== 2) {
    failures.push(`gate3 self-test: both deps without consumers must be red, got ${JSON.stringify(empty.violations)}`);
  }
  if (evaluateFanIn(pomOk.replace(AR2_MARKER, 'gone'), filesOk).violations.filter(v => v.includes('marker')).length !== 1) {
    failures.push('gate3 self-test: missing AR-2 marker must be red');
  }
  const unmapped = pomOk.replace('<artifactId>nop-stream-connector</artifactId>', '<artifactId>nop-stream-connector-future</artifactId>');
  const hardResult = evaluateFanIn(unmapped, filesOk);
  if (hardResult.hardErrors.length !== 1 || !hardResult.hardErrors[0].includes('nop-stream-connector-future')) {
    failures.push(`gate3 self-test: unmapped connector artifact must be a hard error, got ${JSON.stringify(hardResult)}`);
  }
  const compileScope = '<dependency><groupId>g</groupId><artifactId>nop-stream-connector-jdbc</artifactId></dependency>';
  if (extractConnectorTestDeps(`<project>${compileScope}</project>`).length !== 0) {
    failures.push('gate3 self-test: compile-scope connector dep must not enter the test fan-in face');
  }

  return failures;
}

// ---------------------------------------------------------------------------
// live runners

function liveCoreFiles() {
  return collectFiles(CORE_SRC, new Map());
}

function liveRuntimeMainFiles() {
  return collectFiles(RUNTIME_MAIN, new Map(), '.java');
}

function liveRuntimeTestFiles() {
  // consumer census is a java-source notion (imports / string FQN in .java) —
  // a renamed or non-java file mentioning an FQN is not a classpath consumer
  return collectFiles(RUNTIME_TEST, new Map(), '.java');
}

function printViolations(title, violations) {
  if (violations.length > 0) {
    console.error(`${title}: ${violations.length} violation(s)`);
    for (const v of violations) console.error('  ' + v);
    return false;
  }
  return true;
}

function main() {
  const command = process.argv[2] || 'all';
  let ok = true;

  try {
    switch (command) {
      case 'all': {
        ok = printViolations('core-no-runtime-dep (io.nop.stream.runtime in nop-stream-core/src)', scanCoreReverseDeps(liveCoreFiles())) && ok;
        ok = printViolations('runtime-package-acyclicity (transport/rpc/taskmanager/coordinator)', scanPackageCycles(liveRuntimeMainFiles(), CYCLE_PACKAGES)) && ok;
        const {violations, hardErrors} = evaluateFanIn(readFileSync(RUNTIME_POM, 'utf-8'), liveRuntimeTestFiles());
        if (hardErrors.length > 0) {
          console.error(`connector-test-fan-in: ${hardErrors.length} hard error(s)`);
          for (const e of hardErrors) console.error('  ' + e);
          ok = false;
        }
        ok = printViolations('connector-test-fan-in (WI5 disposition b)', violations) && ok;
        break;
      }
      case 'self-test': {
        ok = printViolations('self-test', runSelfTest()) && ok;
        break;
      }
      default:
        console.error(`Unknown command: ${command}`);
        console.error('Usage: node ai-dev/tools/check-nop-stream-structure.mjs [all|self-test]');
        process.exit(2);
    }
  } catch (e) {
    console.error(`check-nop-stream-structure: ${e.message}`);
    process.exit(2);
  }

  if (!ok) process.exit(1);
  console.log(`${command}: OK`);
}

main();
