#!/usr/bin/env node
// check-nop-stream-invariants.mjs
//
// Executable gate scanner for the nop-stream invariant loop (Cycle 1 / I1).
//
// Commands:
//   inventory         - two-way exact-equality diff: gate-inventory.json (the "table")
//                       vs live source enumeration of change-type methods (per I0 §4.1 classifier)
//   sync              - consistency check between I0 audit target set (invariant-catalog.md §4)
//                       and the gate table (catalog-listed classes/methods must be present in the table)
//   scan-iterations   - static scan: every iteration point of a Collections.synchronized* field must be
//                       inside a synchronized block whose monitor is the field, `this`, or a local alias
//                       of the same collection object (invariant #2). Pinned residuals are compared
//                       against ai-dev/audits/nop-stream-invariants/mjs-pins.json (violations ⊆ pins = green)
//   scan-output-contract - static scan of the output-contract family (invariant #6, PD-15):
//                       V1 class-level enumeration (every `implements Output` class in src/main/java,
//                       top-level + nested, must be in output-contract-registry.json implementationClasses;
//                       new class not in registry = red),
//                       V2 stale class (registry class no longer implements Output = red),
//                       V3 behavior drift (collect(OutputTag) method-body classification {forward,
//                       fail-fast, no-op} vs registry class classification {forward, fail-fast,
//                       pinned-known-violation}; body no-op + registry pinned-known-violation -> violation
//                       absorbed by transition pin = green overall; any other mismatch = red;
//                       unrecognized body form / missing collect(OutputTag) on a registered Output class
//                       = hard error, no silent classification),
//                       V4 new emission point (OutputTag-typed declarations field/local/param forms ->
//                       `.collect(<name>,` calls outside Output implementation class method bodies must be
//                       in the registry emissionPoints table; new = red; implementation-class internal
//                       forwarding calls like TimestampedCollector.java:98 are V1/V3 governed, NOT V4),
//                       V5 stale emission point (registry emission point no longer present = red).
//                       Violations ⊆ mjs-pins.json (p.key exact match) = green; pins may only cover
//                       cross-task instance classes (registry classification pinned-known-violation).
//   scan-wiring        - static scan of the production wiring-existence family (invariant #7, Cycle 3 / I1):
//                       V1 test-only injection detection (every registered service injection API must
//                       have >= 1 main call site, receiver-qualified to the AbstractStreamOperator
//                       (subclass) surface; method declarations/javadoc never count; disposition
//                       internal-creation = adjudicated carve-out, main zero call sites not red),
//                       V2 consumer enumeration completeness (main call sites of the registered service
//                       getters getProcessingTimeService()/getTimeServiceManager() and of
//                       registerTimerService( must be owned by consumerTable classes; new class = red),
//                       V3 stale wiring point (registry file:line no longer present in main = red),
//                       V4 new injection API (set* declaration with a registered service parameter type
//                       on AbstractStreamOperator or a main subclass, not in the registry = red),
//                       V5 stale consumer/service (registry class no longer exists = red; java.* services
//                       excluded). Parse failures are hard errors — no silent classification.
//                       Violations ⊆ mjs-pins.json (p.key exact match) = green; expected zero pins at I1.
//   check-wildcard-imports - wildcard-import gate (roadmap item 22): every nop-stream module's
//                       src/main/java + src/test/java trees must contain zero on-demand imports
//                       (`import x.y.*;` / `import static x.y.Z.*;`). Explicit imports only.
//                       `--module m1[,m2...]` scopes the scan (unknown module = hard error);
//                       no scope = all 10 nop-stream modules. Any wildcard import line = red.
//   self-test         - positive control: proves the scanners reject known-bad input (no silent skip)
//   init              - (maintainer tool) regenerate the `methods` arrays of gate-inventory.json
//                       from live source, preserving existing `exclusions`
//   (no argument)     - runs inventory + sync + scan-iterations + scan-output-contract + scan-wiring
//                       + check-wildcard-imports (all 10 modules) + self-test
//
// Style precedent: check-nop-stream-audit-manifest.mjs (subcommand based, strict exit codes,
// Rule #24 no-silent-skip: a missing file / unknown command / inconsistent table is a hard error).
//
// Exit code 0 = all gates green; non-zero = violations found.

import { readFileSync, existsSync, writeFileSync, readdirSync } from 'node:fs';
import { join, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = import.meta.dirname;
const PROJECT_ROOT = resolve(TOOL_DIR, '..', '..');
const INVARIANTS_DIR = join(PROJECT_ROOT, 'ai-dev', 'audits', 'nop-stream-invariants');
const INVENTORY_FILE = join(INVARIANTS_DIR, 'gate-inventory.json');
const CATALOG_FILE = join(INVARIANTS_DIR, 'invariant-catalog.md');
const PINS_FILE = join(INVARIANTS_DIR, 'mjs-pins.json');
const OUTPUT_REGISTRY_FILE = join(INVARIANTS_DIR, 'output-contract-registry.json');
const WIRING_REGISTRY_FILE = join(INVARIANTS_DIR, 'wiring-registry.json');
const FIXTURES_DIR = join(INVARIANTS_DIR, 'fixtures');

const GATE_MODULES = ['nop-stream-core', 'nop-stream-runtime', 'nop-stream-cep'];
const WILDCARD_GATE_MODULES = [
  'nop-stream-core', 'nop-stream-runtime', 'nop-stream-cep', 'nop-stream-flow',
  'nop-stream-connector', 'nop-stream-connector-batch', 'nop-stream-connector-jdbc',
  'nop-stream-connector-debezium', 'nop-stream-rocksdb', 'nop-stream-fraud-example'
];
const SRC_PREFIX = 'src/main/java/';

// I0 §4.1 classifier (mechanical form, mirrored by ChangeTypeMethodClassifier in
// nop-stream-core/src/test/java/io/nop/stream/core/test/):
//   include: public/protected (or package-private, i.e. no visibility modifier) methods
//            declared by the class/interface itself (not inherited, not nested-class members)
//   exclude: private, static, abstract-in-classes (abstract in interfaces is included),
//            constructors, synthetic/bridge methods, test-only accessors (getXForTesting/setXForTesting),
//            0-param read accessors named get*/is*/has* (getAnd*/getOr* are mutators, NOT excluded)
const EXCLUDED_NAMES = new Set(['equals', 'hashCode', 'toString', 'getClass', 'wait', 'notify', 'notifyAll', 'finalize', 'clone']);
const MODIFIERS = new Set(['public', 'protected', 'private', 'static', 'final', 'abstract', 'synchronized', 'native', 'default', 'strictfp', 'transient']);

// ---------------------------------------------------------------------------
// Java source preprocessing (single-pass: comments, strings, char literals, annotations)

function stripCommentsAndStrings(src) {
  let out = '';
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    if (c === '/' && src[i + 1] === '/') {
      while (i < src.length && src[i] !== '\n') i++;
    } else if (c === '/' && src[i + 1] === '*') {
      i += 2;
      while (i < src.length && !(src[i] === '*' && src[i + 1] === '/')) {
        if (src[i] === '\n') out += '\n'; // preserve line numbers for stable pin keys
        i++;
      }
      i += 2;
    } else if (c === '"' || c === '\'') {
      const quote = c;
      i++;
      while (i < src.length) {
        if (src[i] === '\\') {
          i += 2;
          continue;
        }
        if (src[i] === quote) break;
        i++;
      }
      i++;
    } else {
      out += c;
      i++;
    }
  }
  return out;
}

function stripAnnotations(src) {
  // @Ident or @Ident(args) — args may span lines; the corpus never nests parens inside annotation args.
  return src.replace(/@[A-Za-z_$][\w$]*(?:\([^)]*\))?/g, '');
}

// ---------------------------------------------------------------------------
// Method declaration extraction (brace-depth + declaration state machine)
//
// Only methods declared at brace depth 1 (directly inside the top-level class/interface body)
// are considered — nested/anonymous class members are excluded, mirroring Java
// Class#getDeclaredMethods() semantics.

function isTestOnlyName(name) {
  return /ForTest/i.test(name);
}

function isReadAccessorName(name) {
  return (name.startsWith('get') || name.startsWith('is') || name.startsWith('has'))
      && !name.startsWith('getAnd') && !name.startsWith('getOr');
}

function extractTopLevelTypeName(clean) {
  const m = clean.match(/\b(?:class|interface|enum)\s+([A-Za-z_$][\w$]*)/);
  return m ? m[1] : null;
}

function extractMethods(clean, isInterface, className) {
  const methods = new Map(); // name -> {paramCount, modifiers:Set}
  let depth = 0;
  let decl = null;       // Set of modifiers seen in the current candidate declaration
  let lastIdent = null;
  let prev = null;
  let parenDepth = 0;
  let paramCommas = 0;
  let angleDepth = 0;

  const tokenRe = /[A-Za-z_$][\w$]*|\{|\}|\(|\)|=|;|@|,|\.|<|>|\?|\[|\]|&/g;
  let m;
  while ((m = tokenRe.exec(clean)) !== null) {
    const tok = m[0];
    if (parenDepth > 0) {
      // inside the parameter list of a just-captured method declaration
      if (tok === '(') {
        parenDepth++;
      } else if (tok === ')') {
        parenDepth--;
        if (parenDepth === 0) {
          const paramCount = paramCommas === 0 ? 0 : paramCommas + 1;
          finalizeMethod(methods, decl, lastIdent, paramCount, isInterface, className);
          decl = null;
          lastIdent = null;
          prev = ')';
        }
      } else if (tok === '<') {
        angleDepth++;
      } else if (tok === '>') {
        angleDepth = Math.max(0, angleDepth - 1);
      } else if (tok === ',' && angleDepth === 0) {
        paramCommas++;
      }
      continue;
    }

    switch (tok) {
      case '{':
        depth++;
        decl = null;
        lastIdent = null;
        prev = '{';
        break;
      case '}':
        depth = Math.max(0, depth - 1);
        decl = null;
        lastIdent = null;
        prev = '}';
        break;
      case ';':
        decl = null;
        lastIdent = null;
        prev = ';';
        break;
      case '=':
        decl = null;
        lastIdent = null;
        prev = '=';
        break;
      case '(': {
        if (decl !== null && lastIdent !== null) {
          parenDepth = 1;
          paramCommas = 0;
          angleDepth = 0;
          // wait for the closing ')' to finalize with paramCount
        } else {
          prev = '(';
        }
        break;
      }
      case ')':
        decl = null;
        lastIdent = null;
        prev = ')';
        break;
      case ',':
        // keep decl (generic type args in return types); fields separated by commas
        // never produce a '(' after the identifier, so they cannot become false methods
        prev = ',';
        break;
      case '.':
        prev = '.';
        break;
      case '<':
        // generic method declaration start (e.g. interface `public <IN, K, W extends Window> X create(...)`
        // or modifier-less interface methods) — a `<` right after a declaration boundary begins a decl
        if (decl === null && depth === 1
            && (prev === null || prev === '{' || prev === '}' || prev === ';')) {
          decl = new Set();
        }
        angleDepth++;
        prev = '<';
        break;
      case '>':
        angleDepth = Math.max(0, angleDepth - 1);
        prev = '>';
        break;
      case '?':
      case '[':
      case ']':
      case '&':
        prev = tok;
        break;
      default: {
        // identifier
        if (depth === 1 && decl === null
            && (prev === null || prev === '{' || prev === '}' || prev === ';')) {
          decl = new Set();
        }
        if (decl !== null) {
          if (MODIFIERS.has(tok)) {
            decl.add(tok);
          }
          lastIdent = tok;
        }
        prev = tok;
      }
    }
  }
  return methods;
}

function finalizeMethod(methods, decl, lastIdent, paramCount, isInterface, className) {
  if (decl === null || lastIdent === null) return;
  if (lastIdent === className) return; // constructor
  if (decl.has('private') || decl.has('static')) return;
  if (!isInterface && decl.has('abstract')) return;
  if (EXCLUDED_NAMES.has(lastIdent)) return;
  if (isTestOnlyName(lastIdent)) return;
  if (isReadAccessorName(lastIdent)) return;
  const entry = methods.get(lastIdent);
  if (entry) {
    entry.paramCount = Math.max(entry.paramCount, paramCount);
  } else {
    methods.set(lastIdent, {paramCount, modifiers: decl});
  }
}

// ---------------------------------------------------------------------------
// Inventory vs live source enumeration

function moduleSrcRoot(module) {
  return join(PROJECT_ROOT, 'nop-stream', module, SRC_PREFIX);
}

function fqcnToFile(fqcn, module) {
  return join(moduleSrcRoot(module), fqcn.replace(/\./g, '/') + '.java');
}

function loadInventory() {
  if (!existsSync(INVENTORY_FILE)) {
    throw new Error(`gate table not found: ${INVENTORY_FILE} (run 'node ${relative(PROJECT_ROOT, fileURLToPath(import.meta.url))} init' to bootstrap)`);
  }
  return JSON.parse(readFileSync(INVENTORY_FILE, 'utf-8'));
}

function enumerateClassMethods(fqcn, module) {
  const file = fqcnToFile(fqcn, module);
  if (!existsSync(file)) {
    return null; // class not found on disk -> ghost entry
  }
  const src = readFileSync(file, 'utf-8');
  const clean = stripAnnotations(stripCommentsAndStrings(src));
  const isInterface = /(?:^|\s)interface\s+/.test(clean);
  const className = extractTopLevelTypeName(clean);
  const methods = extractMethods(clean, isInterface, className);
  const names = [...methods.keys()].sort();
  return {isInterface, names};
}

// Two-way exact equality: table vs live enumerated change-type method set.
function diffInventory(inventory) {
  const violations = [];
  const modules = inventory.modules || {};
  for (const module of GATE_MODULES) {
    const classes = modules[module] || {};
    for (const fqcn of Object.keys(classes).sort()) {
      const entry = classes[fqcn];
      const table = (entry.methods || []).slice().sort();
      const live = enumerateClassMethods(fqcn, module);
      if (live === null) {
        violations.push(`[${module}] class in table but missing from source: ${fqcn}`);
        continue;
      }
      const inTableNotLive = table.filter(n => !live.names.includes(n));
      const inLiveNotTable = live.names.filter(n => !table.includes(n));
      for (const n of inTableNotLive) {
        violations.push(`[${module}] table lists method not present in source (ghost): ${fqcn}#${n}`);
      }
      for (const n of inLiveNotTable) {
        violations.push(`[${module}] change-type method missing from table: ${fqcn}#${n}`);
      }
    }
  }
  return violations;
}

// ---------------------------------------------------------------------------
// sync: I0 catalog §4 target set ⊆ gate table

function parseCatalogClasses() {
  if (!existsSync(CATALOG_FILE)) {
    throw new Error(`catalog not found: ${CATALOG_FILE}`);
  }
  const content = readFileSync(CATALOG_FILE, 'utf-8');
  const rows = content.split('\n').filter(line => line.trim().startsWith('|'));
  const classes = []; // {module, fqcn, methods:Set}
  for (const row of rows) {
    const cells = row.split('|').map(c => c.trim()).filter(c => c.length > 0);
    if (cells.length < 2) continue;
    const pathCell = cells[0];
    const methodCell = cells[1];
    const m = pathCell.match(/`([^`]+\.java)`/); // tolerate trailing annotations like （接口）
    if (!m) continue;
    const path = m[1];
    // catalog rows use an abbreviated path with a '...' ellipsis, e.g.
    // nop-stream-core/.../datastream/WindowedStreamImpl.java
    const moduleMatch = path.match(/^(nop-stream-[a-z-]+)\/\.\.\.\/(.+)\.java$/);
    if (!moduleMatch) continue;
    const module = moduleMatch[1];
    if (!GATE_MODULES.includes(module)) continue; // out of gate scope (connector modules etc.)
    const fqcn = resolveFqcnFromTail(module, moduleMatch[2] + '.java');
    if (!fqcn) continue;
    const methodMatches = [...methodCell.matchAll(/`(\w+)`/g)].map(mm => mm[1]);
    if (methodMatches.length === 0) continue; // no method list (e.g. "待 I1 复核变更方法")
    classes.push({module, fqcn, methods: new Set(methodMatches)});
  }
  return classes;
}

// Resolve a catalog-ellipsis path tail (e.g. datastream/WindowedStreamImpl.java)
// against the module's source tree to obtain the fully-qualified class name.
function resolveFqcnFromTail(module, tail) {
  const root = moduleSrcRoot(module);
  const rel = findFileRecursive(root, tail);
  if (!rel) return null;
  return rel.slice(0, -'.java'.length).replace(/\//g, '.');
}

function findFileRecursive(dir, tail) {
  if (!existsSync(dir)) return null;
  const entries = readdirSync(dir, {withFileTypes: true});
  for (const entry of entries) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      const found = findFileRecursive(full, tail);
      if (found) return found;
    } else if (entry.isFile() && entry.name === tail && !full.endsWith('package-info.java')) {
      return full.slice(dir.length + 1);
    }
  }
  return null;
}

function syncCatalogWithTable(inventory) {
  const violations = [];
  const modules = inventory.modules || {};
  const catalogClasses = parseCatalogClasses();
  for (const {module, fqcn, methods} of catalogClasses) {
    const classes = modules[module] || {};
    const entry = classes[fqcn];
    if (!entry) {
      violations.push(`[sync] catalog §4 class missing from gate table: ${fqcn}`);
      continue;
    }
    const table = new Set(entry.methods || []);
    const exclusions = new Set((entry.exclusions || []).map(e => e.name));
    for (const name of [...methods].sort()) {
      if (!table.has(name) && !exclusions.has(name)) {
        violations.push(`[sync] catalog §4 lists ${fqcn}#${name} but gate table does not (add to methods or document an exclusion)`);
      }
    }
  }
  return violations;
}

// ---------------------------------------------------------------------------
// scan-iterations: invariant #2 static scan (synchronized collection iteration points)

function findSynchronizedFields(clean) {
  // field declarations: [modifiers] [type] name = Collections.synchronizedXxx(...)
  const fields = new Set();
  const fieldRe = /\b([A-Za-z_$][\w$]*)\s*=\s*(?:java\.util\.)?Collections\.synchronized(?:Map|List|Set)\s*\(/g;
  let m;
  while ((m = fieldRe.exec(clean)) !== null) {
    const name = m[1];
    // must be a field declaration (preceded by ';' '}' '{' or nothing, with type tokens before name)
    const before = clean.slice(Math.max(0, m.index - 120), m.index);
    if (/[;}\s{]\s*(?:private|public|protected)?\s*(?:final\s+)?(?:[\w$<>\[\],\s.]+)\s*$/.test(before) || /(?:^|[\s{;}])(?:private|public|protected)\s+/.test(before)) {
      fields.add(name);
    }
  }
  return [...fields];
}

function findIterationPoints(clean) {
  // iteration points of a collection: for-each, iterator(), entrySet(), values(), keySet(),
  // and copy constructors new TreeMap<>(f) / new HashMap<>(f) / new ArrayList<>(f) / new TreeSet<>(f)
  const points = []; // {name, index, kind}
  const copyRe = /\bnew\s+(?:java\.util\.)?(?:TreeMap|HashMap|LinkedHashMap|ArrayList|LinkedList|TreeSet|HashSet|LinkedHashSet)\s*<[^>]*>?\s*\(\s*([A-Za-z_$][\w$]*)\s*\)/g;
  let m;
  while ((m = copyRe.exec(clean)) !== null) {
    points.push({name: m[1], index: m.index, kind: 'copy-constructor'});
  }
  const iterRe = /\b([A-Za-z_$][\w$]*)\.(?:iterator|entrySet|values|keySet)\s*\(/g;
  while ((m = iterRe.exec(clean)) !== null) {
    points.push({name: m[1], index: m.index, kind: 'iteration'});
  }
  const foreachRe = /\bfor\s*\([^;]*?:\s*([A-Za-z_$][\w$]*)\s*\)/g;
  while ((m = foreachRe.exec(clean)) !== null) {
    points.push({name: m[1], index: m.index, kind: 'for-each'});
  }
  return points;
}

function findSynchronizedMonitors(clean) {
  // synchronized (expr) blocks: record expr + start index
  const monitors = []; // {expr, index}
  const syncRe = /\bsynchronized\s*\(\s*([^)]*)\s*\)\s*\{/g;
  let m;
  while ((m = syncRe.exec(clean)) !== null) {
    monitors.push({expr: m[1].trim(), index: m.index});
  }
  return monitors;
}

function scanIterationsModule(module) {
  const violations = [];
  const root = moduleSrcRoot(module);
  if (!existsSync(root)) return violations;
  const walk = dir => {
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith('.java')) {
        violations.push(...scanIterationsFile(full, module));
      }
    }
  };
  walk(root);
  return violations;
}

function scanIterationsFile(file, module) {
  const violations = [];
  const src = readFileSync(file, 'utf-8');
  const clean = stripAnnotations(stripCommentsAndStrings(src));
  const fields = findSynchronizedFields(clean);
  if (fields.length === 0) return violations;
  const rel = relative(PROJECT_ROOT, file);

  // local aliases: `X = field` or `X = getField()` where getField returns the synchronized field
  const aliasRe = /([A-Za-z_$][\w$]*)\s*=\s*(?:this\.)?([A-Za-z_$][\w$]*)\s*(?:;|\))/g;
  const aliases = new Map(); // aliasName -> Set<fieldName>
  let m;
  while ((m = aliasRe.exec(clean)) !== null) {
    const [alias, target] = [m[1], m[2]];
    if (fields.includes(target)) {
      if (!aliases.has(alias)) aliases.set(alias, new Set());
      aliases.get(alias).add(target);
    }
  }
  // alias sources: methods whose body returns the field directly (e.g. getPendingCommits)
  for (const f of fields) {
    const retRe = new RegExp(`return\\s+${f}\\s*;`);
    const methodRe = /\b(\w+)\s*\([^)]*\)\s*\{[^}]*\}/g;
    let mm;
    while ((mm = methodRe.exec(clean)) !== null) {
      if (retRe.test(mm[0])) {
        if (!aliases.has(mm[1])) aliases.set(mm[1], new Set());
        aliases.get(mm[1]).add(f);
      }
    }
  }

  const monitors = findSynchronizedMonitors(clean);
  const points = findIterationPoints(clean);
  for (const point of points) {
    if (!fields.includes(point.name) && !(aliases.get(point.name) && aliases.get(point.name).size > 0)) {
      continue;
    }
    // find the nearest enclosing synchronized block
    const enclosing = monitors
        .filter(sm => sm.index < point.index)
        .map(sm => ({...sm, depth: braceDepthAt(clean, sm.index)}));
    const valid = enclosing.some(sm => {
      const expr = sm.expr;
      if (expr === 'this') return true;
      if (expr === point.name) return true;
      if (aliases.has(expr) && aliases.get(expr).has(point.name)) return true;
      return false;
    });
    if (!valid) {
      const line = clean.substring(0, point.index).split('\n').length;
      violations.push(`${rel}:${line} iteration of synchronized collection '${point.name}' (${point.kind}) outside synchronized block`);
    }
  }
  return violations;
}

function braceDepthAt(clean, index) {
  let depth = 0;
  for (let i = 0; i < index; i++) {
    if (clean[i] === '{') depth++;
    else if (clean[i] === '}') depth--;
  }
  return depth;
}

function loadPins() {
  if (!existsSync(PINS_FILE)) {
    return {pinnedViolations: []};
  }
  return JSON.parse(readFileSync(PINS_FILE, 'utf-8'));
}

function compareViolationsToPins(violations, pins) {
  const pinned = pins.pinnedViolations || [];
  const pinnedKeys = new Set(pinned.map(p => p.key));
  const unpinned = violations.filter(v => !pinnedKeys.has(v));
  const stale = pinned.filter(p => !violations.includes(p.key)).map(p => p.key);
  return {unpinned, stale};
}

function runScanIterations() {
  const violations = [];
  for (const module of GATE_MODULES) {
    violations.push(...scanIterationsModule(module));
  }
  const pins = loadPins();
  // the shared mjs-pins.json holds pins for multiple scanners — scan-iterations only
  // considers its own pins (non output-contract ones, identified by the violation prefix)
  const ownPins = {pinnedViolations: (pins.pinnedViolations || []).filter(p => !String(p.key).includes('[scan-output-contract]'))};
  const {unpinned, stale} = compareViolationsToPins(violations, ownPins);
  return {violations, unpinned, stale};
}

// ---------------------------------------------------------------------------
// scan-output-contract: output-contract family (invariant #6, PD-15) static scan
//
// V1 class-level enumeration: every `implements Output` class in src/main/java
//   (top-level + nested) must be in output-contract-registry.json implementationClasses;
//   a new class not in the registry = red.
// V2 stale class: a registry implementation class no longer `implements Output` = red.
// V3 behavior drift: collect(OutputTag) method-body classification (scanner vocabulary:
//   {forward, fail-fast, no-op}; precedence forward > fail-fast > no-op) vs registry class
//   classification ({forward, fail-fast, pinned-known-violation}): body no-op + registry
//   pinned-known-violation -> violation emitted, absorbed by transition pin (green overall);
//   any other mismatch -> red. Unrecognized method-body forms / a registered Output class
//   without a collect(OutputTag) method = hard error (no silent classification).
// V4 new emission point: OutputTag-typed variable declarations (field / local variable /
//   method parameter three forms) -> `.collect(<name>,` calls outside Output implementation
//   class method bodies must be in the registry emissionPoints table; new = red.
//   Implementation-class internal forwarding calls (e.g. TimestampedCollector.java:98,
//   parameter-form OutputTag) are V1/V3 governed, NOT V4. Method declaration lines
//   (void output(OutputTag, X)) and internal helper calls (e.g. WindowOperator.java:600
//   sideOutput(element)) never match `.collect(`, naturally excluded. Registry line
//   semantics = the emission point (call line), same as scan output line.
// V5 stale emission point: a registry emission point no longer present in live code = red.
// Pin matching: violations ⊆ mjs-pins.json (p.key exact match) = green; pins may only
//   cover cross-task instance classes (registry classification pinned-known-violation).

function loadOutputRegistry() {
  if (!existsSync(OUTPUT_REGISTRY_FILE)) {
    throw new Error(`output-contract registry not found: ${OUTPUT_REGISTRY_FILE} (scan-output-contract requires it)`);
  }
  return JSON.parse(readFileSync(OUTPUT_REGISTRY_FILE, 'utf-8'));
}

function outputContractModuleRoots() {
  const streamRoot = join(PROJECT_ROOT, 'nop-stream');
  if (!existsSync(streamRoot)) return [];
  const roots = [];
  for (const entry of readdirSync(streamRoot, {withFileTypes: true})) {
    if (!entry.isDirectory()) continue;
    const root = join(streamRoot, entry.name, SRC_PREFIX);
    if (existsSync(root)) roots.push(root);
  }
  return roots;
}

/**
 * Parse the type structure of a cleaned Java source: every class/interface/enum frame with
 * its opening-brace index (nested classes included), and whether the type `implements Output`
 * (exact word boundary — OutputTag does NOT match). Tokens `(` `)` `=` `;` between the type
 * name and the opening `{` reject the candidate (e.g. `String.class` member access), so no
 * false type frames are produced. Braces/extends/implements clause tokens are the only legal
 * content between a real type name and its body.
 */
function parseTypeStructure(clean) {
  const frames = [];
  const tokenRe = /[A-Za-z_$][\w$]*|\{|\}|<|>|\(|\)|;|=/g;
  let m;
  let candidate = null; // {name, nameIdx}
  while ((m = tokenRe.exec(clean)) !== null) {
    const tok = m[0];
    if (candidate) {
      if (tok === '{') {
        const implText = clean.slice(candidate.nameIdx, m.index);
        frames.push({
          name: candidate.name,
          bodyStart: m.index,
          implementsOutput: /\bimplements\b[^{]*\bOutput\b/.test(implText),
          // scan-wiring (invariant #7): direct-superclass simple names from the extends
          // clause (single inheritance — the first name after `extends`), used to compute
          // the transitive AbstractStreamOperator subclass closure for receiver-qualified
          // call-site matching. Additive field; scan-output-contract ignores it.
          // Fully-qualified parents (extends io.nop...AbstractStreamOperator) are reduced
          // to their last segment.
          extendsNames: [...implText.matchAll(/\bextends\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)/g)]
              .map(mm => {
                const fq = mm[1];
                const dot = fq.lastIndexOf('.');
                return dot >= 0 ? fq.substring(dot + 1) : fq;
              })
        });
        candidate = null;
      } else if (tok === '(' || tok === ')' || tok === '=' || tok === ';') {
        candidate = null; // member access / call / field init — not a type declaration
      }
      continue;
    }
    if (tok === 'class' || tok === 'interface' || tok === 'enum') {
      const next = tokenRe.exec(clean);
      if (next && /^[A-Za-z_$][\w$]*$/.test(next[0])) {
        candidate = {name: next[0], nameIdx: next.index};
      }
      continue;
    }
  }
  return frames;
}

/**
 * Compute the body end of every frame (matching brace) and the nested name path.
 * A frame with an unmatched opening brace is a hard parse failure (no silent skip).
 */
function finalizeTypeFrames(clean, frames) {
  for (const frame of frames) {
    let depth = 0;
    let bodyEnd = -1;
    for (let i = frame.bodyStart; i < clean.length; i++) {
      if (clean[i] === '{') depth++;
      else if (clean[i] === '}') {
        depth--;
        if (depth === 0) {
          bodyEnd = i;
          break;
        }
      }
    }
    if (bodyEnd < 0) {
      throw new Error(`parse failure: type frame '${frame.name}' without closing brace at ${frame.bodyStart}`);
    }
    frame.bodyEnd = bodyEnd;
  }
  for (const frame of frames) {
    const parents = frames
        .filter(f => f !== frame && f.bodyStart < frame.bodyStart && frame.bodyEnd < f.bodyEnd)
        .sort((a, b) => b.bodyStart - a.bodyStart)
        .map(p => p.name)
        .reverse();
    frame.namePath = [...parents, frame.name];
    frame.fqcnTail = frame.namePath.join('$');
  }
  return frames;
}

/**
 * Extract the collect(OutputTag, ...) method body of a type frame (direct member only).
 * Returns {bodyText, bodyStartIndex} or null when the method is absent. Nested parens in
 * the parameter list are tracked; a missing body is a hard parse failure (no silent skip).
 */
function findCollectOutputTagMethod(frame, clean) {
  const start = frame.bodyStart;
  const end = frame.bodyEnd;
  let depth = 0; // frame.bodyStart is the opening '{' — the first token consumes it, so members sit at depth 1
  let collectParenIdx = null; // index of the '(' right after a member-level `collect` identifier
  let parenDepth = 0;
  const tokenRe = /[A-Za-z_$][\w$]*|\{|\}|\(|\)/g;
  tokenRe.lastIndex = start;
  let m;
  while ((m = tokenRe.exec(clean)) !== null && m.index < end) {
    const tok = m[0];
    if (collectParenIdx !== null) {
      if (tok === '(') {
        parenDepth++;
      } else if (tok === ')') {
        if (parenDepth === 0) {
          const params = clean.slice(collectParenIdx, m.index);
          if (/\bOutputTag\b/.test(params)) {
            // find the body '{' after the declaration
            const bodyOpen = clean.indexOf('{', m.index);
            if (bodyOpen < 0 || bodyOpen > end) {
              throw new Error(`parse failure: collect(OutputTag) without body in ${frame.fqcnTail}`);
            }
            let d = 0;
            for (let i = bodyOpen; i < end; i++) {
              if (clean[i] === '{') d++;
              else if (clean[i] === '}') {
                d--;
                if (d === 0) {
                  return {bodyText: clean.slice(bodyOpen, i + 1), bodyStartIndex: bodyOpen};
                }
              }
            }
            throw new Error(`parse failure: unmatched brace in collect(OutputTag) body of ${frame.fqcnTail}`);
          }
          collectParenIdx = null;
        } else {
          parenDepth--;
        }
      }
      continue;
    }
    if (tok === '{') {
      depth++;
    } else if (tok === '}') {
      depth--;
    } else if (tok === 'collect' && depth === 1) {
      // candidate member-level method named collect; expect '(' right after
      const nextTok = tokenRe.exec(clean);
      if (nextTok && nextTok[0] === '(' && nextTok.index < end) {
        collectParenIdx = nextTok.index;
        parenDepth = 0;
      }
    }
  }
  return null;
}

function classifyCollectBody(bodyText) {
  // bodyText spans the method body INCLUDING the wrapping { ... } — strip them first
  const inner = bodyText.replace(/^\s*\{/, '').replace(/\}\s*$/, '');
  // HG-01 (2026-08-14): forward forms now also include emit/emitElement forwarding (RWO
  // wraps the tagged record into a SideOutputElement and calls writer.emitElement(...)).
  const forwardRe = /(?:consumer\.accept\s*\(|\.collect\s*\(|\.accept\s*\(|\.emit(?:Element)?\s*\()/;
  const failFastRe = /\bthrow\s/;
  if (forwardRe.test(inner)) return 'forward';
  if (failFastRe.test(inner)) return 'fail-fast';
  const stripped = inner
      .replace(/\/\/[^\n]*/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '');
  if (stripped.trim().length === 0) return 'no-op';
  throw new Error(`unrecognized collect(OutputTag) method body form (not forward/fail-fast/no-op): ${inner.slice(0, 120).trim()}`);
}

function lineOfIndex(text, index) {
  return text.substring(0, index).split('\n').length;
}

/**
 * Analyze one source file (cleaned) for the output-contract scan.
 * Returns {implClasses: [{fqcn, frame, body, bodyClass, line}], emissionPoints: [{line, name}]}.
 */
function analyzeOutputContractSource(clean, rel) {
  const pkgMatch = clean.match(/^package\s+([\w.]+)\s*;/m);
  const pkg = pkgMatch ? pkgMatch[1] : '';
  const frames = finalizeTypeFrames(clean, parseTypeStructure(clean));
  const implFrames = frames.filter(f => f.implementsOutput);
  const implClasses = [];
  for (const frame of implFrames) {
    const body = findCollectOutputTagMethod(frame, clean);
    if (!body) {
      throw new Error(`parse failure: registered Output implementation ${frame.fqcnTail} has no collect(OutputTag) method (${rel})`);
    }
    implClasses.push({
      fqcn: pkg ? `${pkg}.${frame.fqcnTail}` : frame.fqcnTail,
      frame,
      body,
      bodyClass: classifyCollectBody(body.bodyText),
      line: lineOfIndex(clean, body.bodyStartIndex)
    });
  }
  // V4: OutputTag-typed declarations (field / local / method parameter forms)
  const declared = new Set();
  const tagDeclRe = /\bOutputTag\s*<[^>]*>\s+(?:final\s+)?([A-Za-z_$][\w$]*)/g;
  let m;
  while ((m = tagDeclRe.exec(clean)) !== null) declared.add(m[1]);
  const tagParamRe = /\(\s*(?:final\s+)?OutputTag\s*<[^>]*>\s+([A-Za-z_$][\w$]*)/g;
  while ((m = tagParamRe.exec(clean)) !== null) declared.add(m[1]);
  const emissionPoints = [];
  for (const name of declared) {
    const callRe = new RegExp('\\.collect\\s*\\(\\s*' + name + '\\s*,', 'g');
    let cm;
    while ((cm = callRe.exec(clean)) !== null) {
      const insideImpl = implFrames.some(f => cm.index >= f.bodyStart && cm.index <= f.bodyEnd);
      if (insideImpl) continue; // implementation-class internal forwarding -> V1/V3 governed
      emissionPoints.push({line: lineOfIndex(clean, cm.index), name});
    }
  }
  return {implClasses, emissionPoints};
}

function outputContractLive(registry) {
  // returns {liveClasses: Map<fqcn, {rel, impl}>, liveEmissionPoints: Set<rel:line>}
  const liveClasses = new Map();
  const liveEmissionPoints = new Set();
  for (const root of outputContractModuleRoots()) {
    const walk = dir => {
      for (const entry of readdirSync(dir, {withFileTypes: true})) {
        const full = join(dir, entry.name);
        if (entry.isDirectory()) walk(full);
        else if (entry.name.endsWith('.java')) {
          const rel = relative(PROJECT_ROOT, full);
          const clean = stripAnnotations(stripCommentsAndStrings(readFileSync(full, 'utf-8')));
          const analyzed = analyzeOutputContractSource(clean, rel);
          for (const impl of analyzed.implClasses) {
            liveClasses.set(impl.fqcn, {rel, impl});
          }
          for (const point of analyzed.emissionPoints) {
            liveEmissionPoints.add(`${rel}:${point.line}`);
          }
        }
      }
    };
    walk(root);
  }
  return {liveClasses, liveEmissionPoints};
}

/**
 * V1-V5 evaluation against a registry and live data (shared by the disk scan and the
 * self-test fixtures, so fixtures exercise exactly the production code path).
 */
function evaluateOutputContract(registry, liveClasses, liveEmissionPoints) {
  const violations = [];
  const registeredFqcns = new Set((registry.implementationClasses || []).map(c => c.fqcn));

  // V1: live implementation class not in the registry -> red
  for (const [fqcn, {rel}] of [...liveClasses].sort()) {
    if (!registeredFqcns.has(fqcn)) {
      violations.push(`[scan-output-contract] V1 Output implementation class not in registry: ${fqcn} (${rel})`);
    }
  }
  // V2: registry class no longer implements Output -> red
  for (const entry of (registry.implementationClasses || [])) {
    if (!liveClasses.has(entry.fqcn)) {
      violations.push(`[scan-output-contract] V2 registry class no longer implements Output: ${entry.fqcn} (${entry.file})`);
    }
  }
  // V3: behavior drift — body classification vs registry classification. A `no-op` body on a
  // `pinned-known-violation` class IS a mismatch (violation emitted) but is absorbed by the
  // transition pin (green overall); any other mismatch is red. A flip to fail-fast/forward
  // changes the violation string, so the pin no longer matches -> unpinned violation + stale
  // pin = red, forcing the registry classification update together with the I4 fix.
  for (const entry of (registry.implementationClasses || [])) {
    const live = liveClasses.get(entry.fqcn);
    if (!live) continue; // V2 already reported
    const bodyClass = live.impl.bodyClass;
    const registryClass = entry.classification;
    if (bodyClass !== registryClass) {
      violations.push(`[scan-output-contract] V3 behavior drift: ${entry.fqcn} collect(OutputTag) `
          + `body-classification=${bodyClass} registry-classification=${registryClass} (${live.rel}:${live.impl.line})`);
    }
  }
  // V4: emission point not in the registry -> red
  const registryKeys = new Set((registry.emissionPoints || []).map(p => `${p.file}:${p.line}`));
  for (const point of [...liveEmissionPoints].sort()) {
    if (!registryKeys.has(point)) {
      violations.push(`[scan-output-contract] V4 emission point not in registry: ${point}`);
    }
  }
  // V5: registry emission point no longer present -> red
  for (const entry of (registry.emissionPoints || [])) {
    const key = `${entry.file}:${entry.line}`;
    if (!liveEmissionPoints.has(key)) {
      violations.push(`[scan-output-contract] V5 registry emission point no longer exists: ${key}`);
    }
  }
  return violations;
}

/** Pins carrying the scan-output-contract tag may only cover cross-task instance classes. */
function validateOutputContractPins(pins, registry) {
  const pinnedFqcns = new Set((registry.implementationClasses || [])
      .filter(c => c.classification === 'pinned-known-violation').map(c => c.fqcn));
  const errors = [];
  for (const p of (pins.pinnedViolations || [])) {
    if (String(p.key).includes('[scan-output-contract]') && ![...pinnedFqcns].some(fq => p.key.includes(fq))) {
      errors.push(`scan-output-contract pin not covering a cross-task instance class: ${p.key}`);
    }
  }
  return errors;
}

function runScanOutputContract() {
  const registry = loadOutputRegistry();
  const {liveClasses, liveEmissionPoints} = outputContractLive(registry);
  const violations = evaluateOutputContract(registry, liveClasses, liveEmissionPoints);
  const pins = loadPins();
  // only output-contract pins participate in this scan's pin matching
  const ownPins = {pinnedViolations: (pins.pinnedViolations || []).filter(p => String(p.key).includes('[scan-output-contract]'))};
  const {unpinned, stale} = compareViolationsToPins(violations, ownPins);
  return {violations, unpinned, stale, pinErrors: validateOutputContractPins(ownPins, registry)};
}

// ---------------------------------------------------------------------------
// scan-wiring: production wiring-existence family (invariant #7, Cycle 3 / I1)
//
// Registry: wiring-registry.json — services table (service fqcn x injection API
//   (class:method + paramType) x production wiring points (file:line +
//   injectionTiming) x consumer enumeration x disposition {production-wired,
//   internal-creation} x test-only exemption) + consumerTable (class,
//   getterOrRegistration, call lines, service, behaviorWithoutService).
//
// V1 test-only injection detection: every registered service injection API must
//   have >= 1 main call site. Call sites = member-access forms `.setX(` /
//   `this.setX(` / bare `setX(` / cast form `(Type)).setX(` — method DECLARATIONS
//   (the following char after the closing paren is `{` or `throws`) and javadoc
//   never count (a registered API's own declaration must not make main >= 1).
//   Receiver-qualified: the receiver's declared type (`this` = enclosing class,
//   cast = cast type) must be AbstractStreamOperator or a transitive main
//   subclass — CheckpointConfig.setStateBackend / StreamExecutionEnvironment
//   declarations and non-operator receivers never match (no cross-class false
//   greens). disposition = internal-creation: main zero call sites are NOT red
//   (adjudicated carve-out, reason registered in the registry, ratchet-
//   constrained); production-wired: main zero call sites = red regardless of
//   test injection (P0-01 original shape: main=0 test>0).
// V2 consumer enumeration completeness: main call sites (non-declaration) of the
//   registered service getters `getProcessingTimeService()` /
//   `getTimeServiceManager()` and of `registerTimerService(` must have their
//   owning top-level class (file fqcn) in the registry consumerTable; a new
//   consumer class = red.
// V3 stale wiring point: a registry wiringPoint (file:line) must still exist in
//   main code (file present + line present + line contains the injection method
//   name); otherwise = red (forces honest registry maintenance).
// V4 new injection API: a `set*` method DECLARATION in AbstractStreamOperator or
//   a main operator subclass whose parameter type is a registered service param
//   type (raw type in the registry + generic argument match for e.g.
//   Consumer<OperatorSnapshotResult>; setKeyContextElement1/2 (StreamRecord),
//   setCurrentKey (Object), setMailboxExecutor (MailboxExecutor),
//   setProgressMarker (Runnable) do NOT trigger) and that is not in the
//   registry's injectionApi set = red.
// V5 stale consumer / stale service: a registry consumerTable class or registry
//   service (java.* JDK services excluded) no longer exists as a type in main
//   code = red.
// Pin matching: violations ⊆ mjs-pins.json (p.key exact match) = green; expected
//   zero pins at I1 (internal-creation is a registry disposition, NOT a pin). A
//   pin whose violation no longer exists = stale-pin error. Parse failures
//   (unresolved receiver type / unrecognized continuation / unmatched braces)
//   are hard errors — no silent classification.

function loadWiringRegistry() {
  if (!existsSync(WIRING_REGISTRY_FILE)) {
    throw new Error(`wiring registry not found: ${WIRING_REGISTRY_FILE} (scan-wiring requires it)`);
  }
  return JSON.parse(readFileSync(WIRING_REGISTRY_FILE, 'utf-8'));
}

function wiringModuleRoots(kind) {
  const streamRoot = join(PROJECT_ROOT, 'nop-stream');
  if (!existsSync(streamRoot)) return [];
  const roots = [];
  for (const entry of readdirSync(streamRoot, {withFileTypes: true})) {
    if (!entry.isDirectory()) continue;
    const root = join(streamRoot, entry.name, `src/${kind}/java`);
    if (existsSync(root)) roots.push(root);
  }
  return roots;
}

/** All nop-stream module main + test sources: rel-path (from project root) -> raw source. */
function collectWiringSources() {
  const main = new Map();
  const test = new Map();
  const walk = (root, map) => {
    for (const entry of readdirSync(root, {withFileTypes: true})) {
      const full = join(root, entry.name);
      if (entry.isDirectory()) walk(full, map);
      else if (entry.name.endsWith('.java')) {
        map.set(relative(PROJECT_ROOT, full), readFileSync(full, 'utf-8'));
      }
    }
  };
  for (const root of wiringModuleRoots('main')) walk(root, main);
  for (const root of wiringModuleRoots('test')) walk(root, test);
  return {main, test};
}

function parseWiringFile(raw, rel) {
  const clean = stripAnnotations(stripCommentsAndStrings(raw));
  const pkgMatch = clean.match(/^package\s+([\w.]+)\s*;/m);
  const frames = finalizeTypeFrames(clean, parseTypeStructure(clean));
  const topLevel = frames.find(f => f.namePath.length === 1);
  return {
    rel,
    clean,
    frames,
    topLevelFqcn: topLevel && pkgMatch ? `${pkgMatch[1]}.${topLevel.name}` : null
  };
}

/** Transitive closure: AbstractStreamOperator + all frames whose extends clause names an operator class. */
function collectOperatorClassNames(parsedFiles) {
  const names = new Set(['AbstractStreamOperator']);
  let changed = true;
  while (changed) {
    changed = false;
    for (const pf of parsedFiles.values()) {
      for (const frame of pf.frames) {
        if (names.has(frame.name)) continue;
        if (frame.extendsNames.some(p => names.has(p))) {
          names.add(frame.name);
          changed = true;
        }
      }
    }
  }
  return names;
}

/** Member-level field names of every operator class (inherited-field receivers resolve against this). */
function collectOperatorFields(parsedFiles, operatorNames) {
  const fields = new Set();
  for (const pf of parsedFiles.values()) {
    for (const frame of pf.frames) {
      if (!operatorNames.has(frame.name)) continue;
      const memberDepth = braceDepthAt(pf.clean, frame.bodyStart) + 1;
      const fieldRe = /\b(?:private|public|protected)?\s*(?:static\s+)?(?:final\s+)?(?:transient\s+)?[A-Za-z_$][\w$]*(?:<[^>]*>)?\s+([A-Za-z_$][\w$]*)\s*[;=]/g;
      let m;
      while ((m = fieldRe.exec(pf.clean)) !== null) {
        if (m.index < frame.bodyStart || m.index >= frame.bodyEnd) continue;
        if (braceDepthAt(pf.clean, m.index) !== memberDepth) continue;
        fields.add(m[1]);
      }
    }
  }
  return fields;
}

/** Declared simple type name of a receiver identifier (field / local / for-each / method param), nearest before index. */
function declaredTypeOf(clean, name, index) {
  const patterns = [
    new RegExp(`\\b(?:private|public|protected)\\s+(?:static\\s+)?(?:final\\s+)?(?:transient\\s+)?([A-Za-z_$][\\w$]*)\\s*<[^>]*>\\s+${name}\\s*[;=]`, 'g'),
    new RegExp(`\\b(?:private|public|protected)\\s+(?:static\\s+)?(?:final\\s+)?(?:transient\\s+)?([A-Za-z_$][\\w$]*)\\s+${name}\\s*[;=]`, 'g'),
    new RegExp(`\\bfor\\s*\\(\\s*(?:final\\s+)?([A-Za-z_$][\\w$]*)\\s*<[^>]*>\\s+${name}\\s*:`, 'g'),
    new RegExp(`\\bfor\\s*\\(\\s*(?:final\\s+)?([A-Za-z_$][\\w$]*)\\s+${name}\\s*:`, 'g'),
    new RegExp(`\\b([A-Za-z_$][\\w$]*)\\s*<[^>]*>\\s+${name}\\s*=`, 'g'),
    new RegExp(`\\b([A-Za-z_$][\\w$]*)\\s+${name}\\s*=`, 'g'),
    new RegExp(`\\(\\s*(?:final\\s+)?([A-Za-z_$][\\w$]*)\\s*<[^>]*>\\s+${name}\\s*[,)]`, 'g'),
    new RegExp(`\\(\\s*(?:final\\s+)?([A-Za-z_$][\\w$]*)\\s+${name}\\s*[,)]`, 'g')
  ];
  let best = null;
  for (const re of patterns) {
    let m;
    while ((m = re.exec(clean)) !== null) {
      if (m.index < index) best = m[1];
    }
  }
  return best;
}

/**
 * Method-name occurrences: member access (identReceiver.method( / this.method( /
 * super.method(), parenthesized-receiver ((Type) expr).method(, bare method(.
 * Declarations are excluded (following char after the closing paren = `{` or
 * `throws`); any other continuation is either a call or a hard parse failure.
 */
function findCalls(clean, methodName) {
  const calls = [];
  const identRe = new RegExp(`\\b([A-Za-z_$][\\w$]*)\\.${methodName}\\s*\\(`, 'g');
  let m;
  while ((m = identRe.exec(clean)) !== null) {
    const openIdx = m.index + m[0].lastIndexOf('(');
    if (isCallOccurrence(clean, openIdx)) {
      calls.push({
        index: m.index,
        receiver: m[1] === 'this' || m[1] === 'super' ? {kind: 'this'} : {kind: 'ident', name: m[1]}
      });
    }
  }
  const parenRe = new RegExp(`\\)\\.${methodName}\\s*\\(`, 'g');
  while ((m = parenRe.exec(clean)) !== null) {
    const openIdx = m.index + m[0].lastIndexOf('(');
    if (isCallOccurrence(clean, openIdx)) {
      const closeIdx = m.index + m[0].indexOf(').');
      calls.push({index: m.index, receiver: {kind: 'paren', text: clean.slice(matchBack(clean, closeIdx), closeIdx + 1)}});
    }
  }
  const bareRe = new RegExp(`(?:^|[^A-Za-z0-9_$.])${methodName}\\s*\\(`, 'g');
  while ((m = bareRe.exec(clean)) !== null) {
    const openIdx = m.index + m[0].length - 1;
    if (isCallOccurrence(clean, openIdx)) {
      calls.push({index: m.index, receiver: {kind: 'this'}});
    }
  }
  return calls;
}

/** true = call occurrence; false = method declaration; unrecognized continuation = hard parse failure. */
function isCallOccurrence(clean, openIdx) {
  let depth = 0;
  for (let i = openIdx; i < clean.length; i++) {
    const c = clean[i];
    if (c === '(') {
      depth++;
    } else if (c === ')') {
      depth--;
      if (depth === 0) {
        let j = i + 1;
        while (j < clean.length && /\s/.test(clean[j])) j++;
        if (clean.startsWith('throws', j)) return false; // declaration
        const c2 = clean[j];
        if (c2 === '{') return false; // declaration (method body)
        if (c2 === undefined) {
          throw new Error(`parse failure: method-name occurrence without continuation at ${openIdx} (no silent classification)`);
        }
        if (';.,)=:<>!?&|+-*/%[]'.includes(c2)) return true; // call continuation
        throw new Error(`parse failure: unrecognized continuation '${c2}' after method-name occurrence at ${openIdx} (no silent classification)`);
      }
    }
  }
  throw new Error(`parse failure: unmatched '(' at ${openIdx} (no silent skip)`);
}

function matchBack(clean, closeIdx) {
  let depth = 0;
  for (let i = closeIdx; i >= 0; i--) {
    const c = clean[i];
    if (c === ')') depth++;
    else if (c === '(') {
      depth--;
      if (depth === 0) return i;
    }
  }
  throw new Error(`parse failure: unmatched ')' at ${closeIdx} (no silent skip)`);
}

/** Cast type of a parenthesized receiver, e.g. `(AbstractStreamOperator<?>) operators.get(i)` -> AbstractStreamOperator. */
function extractCastType(parenText) {
  let text = parenText;
  if (text.startsWith('(') && matchBack(text, text.length - 1) === 0) {
    text = text.slice(1, -1);
  }
  const m = text.match(/^\(\s*([A-Za-z_$][\w$]*)(?:\s*<[^>]*>)?\s*\)/);
  return m ? m[1] : null;
}

/**
 * Receiver qualification: the call target must be an AbstractStreamOperator (subclass) surface.
 *
 * <p>{@code lenient} = test-side diagnostic counting: an unresolvable identifier receiver is
 * still counted as a call site (the test-side figure only feeds the V1 violation message's
 * test=N statistic; a test-side parse gap must never crash the scanner when a real V1 red is
 * being reported). Strict mode (main side) hard-fails on unresolvable receivers — a production
 * call whose receiver cannot be classified is a genuine scan gap (no silent classification).
 * Non-cast parenthesized receivers (e.g. {@code operators.get(i)).setX(}) are deterministically
 * NOT operator-qualified casts on both sides — they never match.
 */
function receiverQualifies(receiver, callIndex, clean, parsedFile, operatorNames, operatorFields, lenient) {
  if (receiver.kind === 'this') {
    return parsedFile.frames.some(f => operatorNames.has(f.name));
  }
  if (receiver.kind === 'ident') {
    const t = declaredTypeOf(clean, receiver.name, callIndex);
    if (t !== null) return operatorNames.has(t);
    if (operatorFields.has(receiver.name)) return true; // inherited operator field
    if (lenient) return true; // diagnostic stat: count the call site
    throw new Error(`parse failure: cannot resolve declared type of receiver '${receiver.name}' in ${parsedFile.rel} (no silent classification)`);
  }
  const castType = extractCastType(receiver.text);
  if (castType === null) return false; // method-call / variable parenthesized receiver — not a cast
  return operatorNames.has(castType);
}

function countQualifiedCalls(methodName, parsedFiles, operatorNames, operatorFields, lenient) {
  let count = 0;
  for (const pf of parsedFiles.values()) {
    for (const call of findCalls(pf.clean, methodName)) {
      if (receiverQualifies(call.receiver, call.index, pf.clean, pf, operatorNames, operatorFields, lenient)) count++;
    }
  }
  return count;
}

function simpleNameOf(fqcn) {
  const dot = String(fqcn).lastIndexOf('.');
  return dot >= 0 ? String(fqcn).substring(dot + 1) : String(fqcn);
}

/** Split a single-param declaration text into {raw, generic} (qualified names + balanced angle brackets). */
function splitParamDeclaration(paramText) {
  const m = paramText.match(/^([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)([\s\S]*?)\s+([A-Za-z_$][\w$]*)$/);
  if (!m) return null;
  const rest = m[2].trim();
  if (rest === '') return {raw: m[1], generic: null};
  if (!rest.startsWith('<') || !rest.endsWith('>')) return null;
  return {raw: m[1], generic: rest.slice(1, -1)};
}

function paramTypeMatchesRegistered(paramTypeText, services) {
  const decl = splitParamDeclaration(paramTypeText);
  if (!decl) return false;
  for (const service of services) {
    const api = service.injectionApi || {};
    if (simpleNameOf(api.paramType) !== simpleNameOf(decl.raw)) continue;
    if (api.genericArg) {
      const want = simpleNameOf(String(api.genericArg));
      if (decl.generic !== null
          && (decl.generic === String(api.genericArg) || decl.generic === want
              || simpleNameOf(decl.generic) === want)) {
        return true;
      }
      continue; // generic mismatch — not the registered injection-API shape
    }
    return true;
  }
  return false;
}

/**
 * Member-level `set*` DECLARATIONS of an operator-class frame whose parameter type
 * matches a registered service param type. Returns [{method, paramType, index}].
 */
function findSetDeclarationsWithServiceParams(clean, frame, services) {
  const out = [];
  const memberDepth = braceDepthAt(clean, frame.bodyStart) + 1;
  const declRe = /\b(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?[A-Za-z_$][\w$]*(?:<[^>]*>)?\s+(set[A-Za-z_$][\w$]*)\s*\(\s*([^()]*?)\s*\)\s*\{/g;
  let m;
  while ((m = declRe.exec(clean)) !== null) {
    if (m.index < frame.bodyStart || m.index >= frame.bodyEnd) continue;
    if (braceDepthAt(clean, m.index) !== memberDepth) continue;
    if (paramTypeMatchesRegistered(m[2].trim(), services)) {
      out.push({method: m[1], paramType: m[2].trim(), index: m.index});
    }
  }
  return out;
}

/** Registry wiring point still present in the main source (file + line + method name on the line). */
function wiringPointPresent(src, line, methodName) {
  const lines = src.split('\n');
  if (line < 1 || line > lines.length) return false;
  return lines[line - 1].includes(methodName);
}

function evaluateWiring(registry, mainSources, testSources) {
  const violations = [];
  const services = registry.services || [];
  const registeredMethods = new Set(services.map(s => (s.injectionApi || {}).method));

  const parsedMain = new Map();
  for (const [rel, raw] of mainSources) parsedMain.set(rel, parseWiringFile(raw, rel));
  const parsedTest = new Map();
  for (const [rel, raw] of testSources) parsedTest.set(rel, parseWiringFile(raw, rel));

  // operator classes are collected from main + test (test-only receivers only feed V1 stats)
  const allParsed = new Map([...parsedMain, ...parsedTest]);
  const operatorNames = collectOperatorClassNames(allParsed);
  const operatorFields = collectOperatorFields(allParsed, operatorNames);

  // V1: test-only injection detection (main zero call sites = red for production-wired)
  for (const service of services) {
    const api = service.injectionApi || {};
    if (!api.method) {
      throw new Error(`scan-wiring: service ${service.service} missing injectionApi.method (no silent skip)`);
    }
    if (service.disposition === 'internal-creation') continue; // adjudicated carve-out
    const mainCalls = countQualifiedCalls(api.method, parsedMain, operatorNames, operatorFields, false);
    if (mainCalls === 0) {
      const testCalls = countQualifiedCalls(api.method, parsedTest, operatorNames, operatorFields, true);
      violations.push(`[scan-wiring] V1 test-only injection: ${service.service} injection API '${api.method}' `
          + `has 0 main call sites (production wiring missing); main=0 test=${testCalls} — P0-01 original shape (test-mock evasion)`);
    }
  }

  // V2: consumer enumeration completeness
  const consumerClasses = new Set((registry.consumerTable || []).map(r => r.class));
  const v2Forms = ['getProcessingTimeService', 'getTimeServiceManager', 'registerTimerService'];
  for (const pf of parsedMain.values()) {
    for (const form of v2Forms) {
      const calls = findCalls(pf.clean, form);
      if (calls.length > 0 && pf.topLevelFqcn && !consumerClasses.has(pf.topLevelFqcn)) {
        violations.push(`[scan-wiring] V2 consumer class not in registry: ${pf.topLevelFqcn} calls ${form}() `
            + `at ${pf.rel}:${lineOfIndex(pf.clean, calls[0].index)} (a new main consumer of a registered service must be registered)`);
      }
    }
  }

  // V3: stale wiring points
  for (const service of services) {
    const api = service.injectionApi || {};
    for (const point of (service.wiringPoints || [])) {
      const key = `${point.file}:${point.line}`;
      const src = mainSources.get(point.file);
      if (!src || !wiringPointPresent(src, Number(point.line), api.method)) {
        violations.push(`[scan-wiring] V3 stale wiring point: ${key} (${api.method}) no longer present in main code (sync the registry)`);
      }
    }
  }

  // V4: new service injection API on the AbstractStreamOperator (subclass) surface
  for (const pf of parsedMain.values()) {
    for (const frame of pf.frames) {
      if (!operatorNames.has(frame.name)) continue;
      for (const decl of findSetDeclarationsWithServiceParams(pf.clean, frame, services)) {
        if (!registeredMethods.has(decl.method)) {
          violations.push(`[scan-wiring] V4 new service injection API not in registry: ${pf.rel}:${lineOfIndex(pf.clean, decl.index)} `
              + `${decl.method}(${decl.paramType}) — parameter type is a registered service type (register the API or rename it)`);
        }
      }
    }
  }

  // V5: stale consumer / stale service
  const knownFqcns = new Set();
  for (const pf of parsedMain.values()) {
    const pkg = pf.topLevelFqcn ? pf.topLevelFqcn.slice(0, pf.topLevelFqcn.lastIndexOf('.')) : '';
    for (const frame of pf.frames) {
      knownFqcns.add(pkg ? `${pkg}.${frame.fqcnTail}` : frame.fqcnTail);
    }
  }
  for (const row of (registry.consumerTable || [])) {
    if (!knownFqcns.has(row.class)) {
      violations.push(`[scan-wiring] V5 registry consumer class no longer exists: ${row.class} (${row.file || 'unknown'})`);
    }
  }
  for (const service of services) {
    const f = String(service.service);
    if (f.startsWith('java.') || f.startsWith('javax.')) continue;
    if (!knownFqcns.has(f)) {
      violations.push(`[scan-wiring] V5 registry service no longer exists: ${f}`);
    }
  }

  return violations;
}

function runScanWiring() {
  const registry = loadWiringRegistry();
  const {main, test} = collectWiringSources();
  const violations = evaluateWiring(registry, main, test);
  const pins = loadPins();
  const ownPins = {pinnedViolations: (pins.pinnedViolations || []).filter(p => String(p.key).includes('[scan-wiring]'))};
  const {unpinned, stale} = compareViolationsToPins(violations, ownPins);
  return {violations, unpinned, stale};
}

// ---------------------------------------------------------------------------
// check-wildcard-imports: on-demand import gate (roadmap item 22)
//
// Scope: src/main/java + src/test/java of the requested nop-stream modules.
// Rule: no line may be an on-demand import — `import a.b.*;` or
// `import static a.b.C.*;`. Main side has been at zero since the core audit
// §1.2 #6 cleanup; the test side was swept to zero by plan 2026-09-03-1723-1.
// This gate keeps both at zero (regrowth anywhere = red).

const WILDCARD_IMPORT_RE = /^\s*import\s+(?:static\s+)?[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*\.\*\s*;/;

function scanWildcardImportsRaw(src, rel) {
  const violations = [];
  const lines = src.split('\n');
  for (let i = 0; i < lines.length; i++) {
    if (WILDCARD_IMPORT_RE.test(lines[i])) {
      violations.push(`[check-wildcard-imports] ${rel}:${i + 1} on-demand import not allowed: '${lines[i].trim()}' (use explicit imports)`);
    }
  }
  return violations;
}

function runWildcardImportScan(modules) {
  const violations = [];
  for (const module of modules) {
    if (!WILDCARD_GATE_MODULES.includes(module)) {
      throw new Error(`unknown module '${module}' (valid: ${WILDCARD_GATE_MODULES.join(', ')}) — no silent skip`);
    }
    for (const kind of ['main', 'test']) {
      const root = join(PROJECT_ROOT, 'nop-stream', module, `src/${kind}/java`);
      if (!existsSync(root)) continue;
      const walk = dir => {
        for (const entry of readdirSync(dir, {withFileTypes: true})) {
          const full = join(dir, entry.name);
          if (entry.isDirectory()) walk(full);
          else if (entry.name.endsWith('.java')) {
            violations.push(...scanWildcardImportsRaw(readFileSync(full, 'utf-8'),
                relative(PROJECT_ROOT, full)));
          }
        }
      };
      walk(root);
    }
  }
  return violations;
}

// ---------------------------------------------------------------------------
// self-test: positive control fixtures

function runSelfTest() {
  const failures = [];
  const run = (label, fn, expectFail) => {
    try {
      const result = fn();
      const failed = expectFail ? !result : result;
      if (failed) {
        failures.push(`${label}: expected ${expectFail ? 'non-zero' : 'zero'} violations but got ${result ? 'none' : 'violations'}`);
      }
    } catch (e) {
      if (!expectFail) failures.push(`${label}: unexpected error: ${e.message}`);
    }
  };

  // 1. classifier: a change-type method must be enumerated, a getter must not
  const sample = stripAnnotations(stripCommentsAndStrings(`
class Sample {
    private int x;
    public void mutate(int v) { this.x = v; }
    public int getX() { return x; }
    public long getAndIncrement() { return 0; }
    static void staticHelper() { }
    protected void protectedMutate() { }
    void packageMutate() { }
    Sample() { }
}`));
  const methods = extractMethods(sample, false, 'Sample');
  const names = [...methods.keys()].sort();
  if (JSON.stringify(names) !== JSON.stringify(['getAndIncrement', 'mutate', 'packageMutate', 'protectedMutate'])) {
    failures.push(`classifier: unexpected method set ${JSON.stringify(names)}`);
  }

  // 2. interface: modifier-less methods are public and must be enumerated
  const ifaceSample = stripAnnotations(stripCommentsAndStrings(`
public interface SampleIface {
    void register(String id);
    boolean isActive(String id);
    default void attach() { }
}`));
  const ifaceMethods = extractMethods(ifaceSample, true, 'SampleIface');
  if (JSON.stringify([...ifaceMethods.keys()].sort()) !== JSON.stringify(['attach', 'register'])) {
    failures.push(`interface classifier: unexpected method set ${JSON.stringify([...ifaceMethods.keys()].sort())}`);
  }

  // 3. iteration scanner must reject an unsynchronized iteration of a synchronized collection
  const badIteration = `
class BadSink {
    private final Map<Long, Object> pending = Collections.synchronizedMap(new TreeMap<>());
    public void save() {
        Map<Long, Object> copy = new TreeMap<>(pending);  // no synchronized block
    }
}`;
  const badClean = stripAnnotations(stripCommentsAndStrings(badIteration));
  const badFields = findSynchronizedFields(badClean);
  const badPoints = findIterationPoints(badClean);
  const badViolations = scanIterationsFileRaw(badClean, 'fixtures/BadSink.java');
  if (badFields.length !== 1 || badPoints.length !== 1 || badViolations.length !== 1) {
    failures.push(`iteration scanner: expected 1 violation for unsynchronized copy, got fields=${badFields.length} points=${badPoints.length} violations=${badViolations.length}`);
  }

  // 4. iteration scanner must ACCEPT a synchronized iteration via local alias
  const goodIteration = `
class GoodSink {
    private final Map<Long, Object> pending = Collections.synchronizedMap(new TreeMap<>());
    public Map<Long, Object> getPending() { return pending; }
    public void finish(long epoch) {
        Map<Long, Object> p = getPending();
        synchronized (p) {
            for (Map.Entry<Long, Object> e : p.entrySet()) { }
        }
    }
}`;
  const goodClean = stripAnnotations(stripCommentsAndStrings(goodIteration));
  const goodViolations = scanIterationsFileRaw(goodClean, 'fixtures/GoodSink.java');
  if (goodViolations.length !== 0) {
    failures.push(`iteration scanner: expected 0 violations for synchronized iteration, got ${JSON.stringify(goodViolations)}`);
  }

  // 4b. repo-observable fixture: scan the committed fixtures dir (BadSinkFixture.java contains
  // a deliberate un-synchronized copy iteration) — the scanner must flag exactly that point.
  if (existsSync(FIXTURES_DIR)) {
    const fixtureViolations = [];
    const walk = dir => {
      for (const entry of readdirSync(dir, {withFileTypes: true})) {
        const full = join(dir, entry.name);
        if (entry.isDirectory()) walk(full);
        else if (entry.name.endsWith('.java')) {
          const src = readFileSync(full, 'utf-8');
          const clean = stripAnnotations(stripCommentsAndStrings(src));
          fixtureViolations.push(...scanIterationsFileRaw(clean, `fixtures/${entry.name}`));
        }
      }
    };
    walk(FIXTURES_DIR);
    const badFixture = fixtureViolations.filter(v => v.includes('copy-constructor') && v.includes('pending'));
    if (badFixture.length === 0) {
      failures.push(`fixture scan: expected BadSinkFixture to be flagged for un-synchronized copy, got ${JSON.stringify(fixtureViolations)}`);
    }
  } else {
    failures.push('fixture scan: fixtures dir missing — cannot prove scanner can go red on injected violation');
  }

  // 5. pinned-residual semantics: a pinned violation is green, a new violation is red, a stale pin is reported
  const pins = {pinnedViolations: [{key: "fixtures/BadSink.java:5 iteration of synchronized collection 'pending' (copy-constructor) outside synchronized block"}]};
  const {unpinned} = compareViolationsToPins(badViolations, pins);
  if (unpinned.length !== 0) {
    failures.push(`pin comparison: expected pinned violation to be absorbed, got ${JSON.stringify(unpinned)}`);
  }
  const stalePins = {pinnedViolations: [{key: "fixtures/Gone.java:1 iteration of synchronized collection 'x' outside synchronized block"}]};
  const staleResult = compareViolationsToPins([], stalePins);
  if (staleResult.stale.length !== 1 || staleResult.unpinned.length !== 0) {
    failures.push(`stale-pin detection: expected 1 stale pin, got ${JSON.stringify(staleResult)}`);
  }

  // ---------------------------------------------------------------------------
  // scan-output-contract self-test fixtures (invariant #6): V1-V5 positive/negative

  // V3 positive: forward body + registry forward -> no V3 violation; registered emission point -> no V4
  const fwdFixture = `
package fixtures;
import java.util.function.Consumer;
class FwdOutput implements Output<StreamRecord<String>> {
    private final java.util.Map<OutputTag<?>, Consumer<StreamRecord<?>>> consumers;
    public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        Consumer<StreamRecord<X>> c = (Consumer<StreamRecord<X>>) (Consumer<?>) consumers.get(outputTag);
        c.accept(record);
    }
}`;
  const fwdLive = analyzeFixtureSources([{rel: 'fixtures/FwdOutput.java', src: fwdFixture}]);
  const fwdRegistry = {implementationClasses: [{fqcn: 'fixtures.FwdOutput', file: 'fixtures/FwdOutput.java', classification: 'forward'}], emissionPoints: []};
  const fwdViolations = evaluateOutputContract(fwdRegistry, fwdLive.liveClasses, fwdLive.liveEmissionPoints);
  if (fwdViolations.length !== 0) {
    failures.push(`output-contract V3/V4 positive: expected 0 violations for forward body + self-consistent registry, got ${JSON.stringify(fwdViolations)}`);
  }

  // V3 positive (emitElement form, HG-01 D8): RWO wraps the tagged record into a
  // SideOutputElement and forwards via writer.emitElement(...) — classifyCollectBody must
  // recognize the emit/emitElement form as forward (review M1: pre-extension this crashed).
  const emitFixture = `
package fixtures;
class EmitOutput implements Output<StreamRecord<String>> {
    private final RecordWriter<Object> writer;
    public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        writer.emitElement(new SideOutputElement(outputTag.getId(), record.copy(record.getValue())));
    }
}`;
  const emitLive = analyzeFixtureSources([{rel: 'fixtures/EmitOutput.java', src: emitFixture}]);
  const emitRegistry = {implementationClasses: [{fqcn: 'fixtures.EmitOutput', file: 'fixtures/EmitOutput.java', classification: 'forward'}], emissionPoints: []};
  const emitViolations = evaluateOutputContract(emitRegistry, emitLive.liveClasses, emitLive.liveEmissionPoints);
  if (emitViolations.length !== 0) {
    failures.push(`output-contract V3 emitElement positive: expected 0 violations for emitElement-form forward body + self-consistent registry, got ${JSON.stringify(emitViolations)}`);
  }

  // V3 negative (empty body not pinned): registry forward, body no-op -> behavior drift violation
  const emptyFixture = `
package fixtures;
class EmptyOutput implements Output<StreamRecord<String>> {
    public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        // comment only — empty body
    }
}`;
  const emptyLive = analyzeFixtureSources([{rel: 'fixtures/EmptyOutput.java', src: emptyFixture}]);
  const emptyRegistry = {implementationClasses: [{fqcn: 'fixtures.EmptyOutput', file: 'fixtures/EmptyOutput.java', classification: 'forward'}], emissionPoints: []};
  const emptyViolations = evaluateOutputContract(emptyRegistry, emptyLive.liveClasses, emptyLive.liveEmissionPoints);
  if (emptyViolations.length !== 1 || !emptyViolations[0].includes('V3 behavior drift')) {
    failures.push(`output-contract V3 negative: expected exactly 1 V3 behavior-drift violation for unpinned empty body, got ${JSON.stringify(emptyViolations)}`);
  }

  // V3 pinned absorption: registry pinned-known-violation + matching pin -> green; stale pin -> red
  const pinnedRegistry = {implementationClasses: [{fqcn: 'fixtures.EmptyOutput', file: 'fixtures/EmptyOutput.java', classification: 'pinned-known-violation'}], emissionPoints: []};
  const pinnedViolations = evaluateOutputContract(pinnedRegistry, emptyLive.liveClasses, emptyLive.liveEmissionPoints);
  if (pinnedViolations.length !== 1 || !pinnedViolations[0].includes('body-classification=no-op registry-classification=pinned-known-violation')) {
    failures.push(`output-contract V3 pin semantics: expected 1 pin-eligible V3 violation for pinned no-op, got ${JSON.stringify(pinnedViolations)}`);
  }
  const pinnedOk = {pinnedViolations: [{key: pinnedViolations[0], id: 'fixture-empty', file: 'fixtures/EmptyOutput.java', invariant: '#6', classification: 'known-violation'}]};
  const absorbed = compareViolationsToPins(pinnedViolations, pinnedOk);
  if (absorbed.unpinned.length !== 0 || absorbed.stale.length !== 0) {
    failures.push(`output-contract pin absorption: expected pinned violation absorbed, got ${JSON.stringify(absorbed)}`);
  }
  const staleOutPins = {pinnedViolations: [{key: pinnedViolations[0], id: 'fixture-empty', file: 'fixtures/EmptyOutput.java', invariant: '#6', classification: 'known-violation'}]};
  const staleOut = compareViolationsToPins([], staleOutPins);
  if (staleOut.stale.length !== 1) {
    failures.push(`output-contract stale-pin detection: expected 1 stale #6 pin, got ${JSON.stringify(staleOut)}`);
  }

  // V1 negative: live Output implementation class missing from the registry -> V1 violation
  const unregistered = evaluateOutputContract({implementationClasses: [], emissionPoints: []}, emptyLive.liveClasses, emptyLive.liveEmissionPoints);
  if (!unregistered.some(v => v.includes('V1 Output implementation class not in registry: fixtures.EmptyOutput'))) {
    failures.push(`output-contract V1 negative: expected V1 violation for class not in registry, got ${JSON.stringify(unregistered)}`);
  }

  // V4 positive + parameter-form: ProcessOperator-style param-declared OutputTag emission
  // detected as an emission point; registered -> green, unregistered -> red
  const emitterFixture = `
package fixtures;
class EmitterOperator {
    private Output<StreamRecord<Object>> output;
    public <X> void output(OutputTag<X> outputTag, X value) {
        output.collect(outputTag, new StreamRecord<>(value));
    }
}`;
  const emitterLive = analyzeFixtureSources([{rel: 'fixtures/EmitterOperator.java', src: emitterFixture}]);
  const emitterPoint = emitterLive.liveEmissionPoints.values().next().value;
  const emitterRegistry = {implementationClasses: [], emissionPoints: [{file: 'fixtures/EmitterOperator.java', line: Number(emitterPoint.split(':')[1])}]};
  const emitterGreen = evaluateOutputContract(emitterRegistry, emitterLive.liveClasses, emitterLive.liveEmissionPoints);
  if (emitterGreen.some(v => v.includes('V4'))) {
    failures.push(`output-contract V4 positive: registered parameter-form emission point must be green, got ${JSON.stringify(emitterGreen)}`);
  }
  const emitterRed = evaluateOutputContract({implementationClasses: [], emissionPoints: []}, emitterLive.liveClasses, emitterLive.liveEmissionPoints);
  if (!emitterRed.some(v => v.includes('V4 emission point not in registry'))) {
    failures.push(`output-contract V4 negative: unregistered emission point must be red, got ${JSON.stringify(emitterRed)}`);
  }

  // V4 exclusion: TimestampedCollector-style implementation-internal forwarding call
  // (parameter-form OutputTag inside an Output class method body) is NOT a V4 emission point
  const passThroughFixture = `
package fixtures;
class PassThroughOutput implements Output<StreamRecord<String>> {
    private Output<StreamRecord<String>> output;
    public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        output.collect(outputTag, record);
    }
}`;
  const passThroughLive = analyzeFixtureSources([{rel: 'fixtures/PassThroughOutput.java', src: passThroughFixture}]);
  if (passThroughLive.liveEmissionPoints.size !== 0) {
    failures.push(`output-contract V4 exclusion: implementation-internal forwarding must NOT be a V4 emission point, got ${JSON.stringify([...passThroughLive.liveEmissionPoints])}`);
  }
  const passThroughRegistry = {implementationClasses: [{fqcn: 'fixtures.PassThroughOutput', file: 'fixtures/PassThroughOutput.java', classification: 'forward'}], emissionPoints: []};
  const passThroughViolations = evaluateOutputContract(passThroughRegistry, passThroughLive.liveClasses, passThroughLive.liveEmissionPoints);
  if (passThroughViolations.length !== 0) {
    failures.push(`output-contract V1/V3 positive: pass-through impl class classified forward must be green, got ${JSON.stringify(passThroughViolations)}`);
  }

  // V5 negative: registry emission point no longer present -> red
  const v5Registry = {implementationClasses: [], emissionPoints: [{file: 'fixtures/Gone.java', line: 7}]};
  const v5 = evaluateOutputContract(v5Registry, emitterLive.liveClasses, emitterLive.liveEmissionPoints);
  if (!v5.some(v => v.includes('V5 registry emission point no longer exists: fixtures/Gone.java:7'))) {
    failures.push(`output-contract V5 negative: stale registry emission point must be red, got ${JSON.stringify(v5)}`);
  }

  // V2 negative: registry class no longer implements Output -> red
  const v2Registry = {implementationClasses: [{fqcn: 'fixtures.DeletedOutput', file: 'fixtures/DeletedOutput.java', classification: 'forward'}], emissionPoints: []};
  const v2 = evaluateOutputContract(v2Registry, emptyLive.liveClasses, emptyLive.liveEmissionPoints);
  if (!v2.some(v => v.includes('V2 registry class no longer implements Output: fixtures.DeletedOutput'))) {
    failures.push(`output-contract V2 negative: registry class missing from live code must be red, got ${JSON.stringify(v2)}`);
  }

  // no-silent-skip: an unrecognized collect(OutputTag) body form must be a hard error
  const weirdFixture = `
package fixtures;
class WeirdOutput implements Output<StreamRecord<String>> {
    public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        System.out.println("not forward, not fail-fast, not a comment-only body");
    }
}`;
  try {
    analyzeFixtureSources([{rel: 'fixtures/WeirdOutput.java', src: weirdFixture}]);
    failures.push('output-contract no-silent-skip: unrecognized collect(OutputTag) body must throw, but it did not');
  } catch (e) {
    if (!String(e.message).includes('unrecognized collect(OutputTag)')) {
      failures.push(`output-contract no-silent-skip: unexpected error: ${e.message}`);
    }
  }

  // committed fixture: OutputContractFixture.java contains a deliberate unpinned empty body
  // (V3) and an unregistered emission point (V4) — the scanner must flag both.
  const committedFixture = join(FIXTURES_DIR, 'OutputContractFixture.java');
  if (existsSync(committedFixture)) {
    const src = readFileSync(committedFixture, 'utf-8');
    const clean = stripAnnotations(stripCommentsAndStrings(src));
    const analyzed = analyzeOutputContractSource(clean, 'fixtures/OutputContractFixture.java');
    const fixtureLive = analyzeFixtureSources([{rel: 'fixtures/OutputContractFixture.java', src}]);
    const fixtureRegistry = {
      implementationClasses: (analyzed.implClasses).map(c => ({fqcn: c.fqcn, file: 'fixtures/OutputContractFixture.java', classification: 'forward'})),
      emissionPoints: []
    };
    const fixtureViolations = evaluateOutputContract(fixtureRegistry, fixtureLive.liveClasses, fixtureLive.liveEmissionPoints);
    if (!fixtureViolations.some(v => v.includes('V3 behavior drift'))) {
      failures.push(`output-contract fixture: expected V3 behavior drift for committed empty-body fixture, got ${JSON.stringify(fixtureViolations)}`);
    }
    if (!fixtureViolations.some(v => v.includes('V4 emission point not in registry'))) {
      failures.push(`output-contract fixture: expected V4 violation for committed unregistered emission point, got ${JSON.stringify(fixtureViolations)}`);
    }
  } else {
    failures.push('output-contract fixture: OutputContractFixture.java missing — cannot prove scanner can go red on committed violations');
  }

  // ---------------------------------------------------------------------------
  // scan-wiring self-test fixtures (invariant #7): V1-V5 positive/negative

  const wiringOpSrc = `
package fixtures;
class WiringPts {
}
class WiringOp extends io.nop.stream.core.operators.AbstractStreamOperator<String> {
    public void processElement(io.nop.stream.core.streamrecord.StreamRecord<String> element) {
    }
}`;
  const wiringMainSrc = `
package fixtures;
class WiringMain {
    private WiringPts pts;
    void wire(WiringOp op) {
        op.setProcessingTimeService(pts);
    }
}`;
  const wiringTestSrc = `
package fixtures;
class WiringTestOnly {
    void inject(WiringPts pts) {
        WiringOp op = new WiringOp();
        op.setProcessingTimeService(pts);
    }
}`;
  const mkWiringServices = wiringPoints => [{
    service: 'fixtures.WiringPts',
    injectionApi: {
      class: 'io.nop.stream.core.operators.AbstractStreamOperator',
      method: 'setProcessingTimeService',
      paramType: 'io.nop.stream.core.operators.ProcessingTimeService'
    },
    wiringPoints,
    consumers: [],
    disposition: 'production-wired',
    reason: 'fixture'
  }];
  const wiringGreenFixtureRegistry = {
    services: mkWiringServices([{
      file: 'fixtures/WiringMain.java',
      line: lineOfIndex(wiringMainSrc, wiringMainSrc.indexOf('op.setProcessingTimeService')),
      injectionTiming: 'fixture'
    }]),
    consumerTable: [],
    testOnlyExemptions: [],
    violationSemantics: {},
    pinSchema: {}
  };
  const wiringGreen = evaluateWiring(wiringGreenFixtureRegistry,
      new Map([['fixtures/WiringMain.java', wiringMainSrc], ['fixtures/WiringOp.java', wiringOpSrc]]),
      new Map());
  if (wiringGreen.length !== 0) {
    failures.push(`wiring V1-V5 positive: self-consistent registry + wired main must be green, got ${JSON.stringify(wiringGreen)}`);
  }

  // V1 negative: main zero call sites + test-only injection -> red (P0-01 original shape)
  const wiringV1Registry = {services: mkWiringServices([]), consumerTable: []};
  const wiringV1 = evaluateWiring(wiringV1Registry,
      new Map([['fixtures/WiringOp.java', wiringOpSrc]]),
      new Map([['fixtures/WiringTestOnly.java', wiringTestSrc]]));
  if (!wiringV1.some(v => v.includes('V1 test-only injection') && v.includes('setProcessingTimeService')
      && v.includes('main=0 test=1'))) {
    failures.push(`wiring V1 negative: test-only injection must be red with call-site stats, got ${JSON.stringify(wiringV1)}`);
  }

  // V2 negative: new main consumer class of a registered getter -> red
  const newConsumerSrc = `
package fixtures;
class NewConsumer {
    long now(io.nop.stream.core.operators.AbstractStreamOperator op) {
        return op.getProcessingTimeService().getCurrentProcessingTime();
    }
}`;
  const wiringV2Registry = {services: mkWiringServices([]), consumerTable: []};
  const wiringV2 = evaluateWiring(wiringV2Registry, new Map([['fixtures/NewConsumer.java', newConsumerSrc]]), new Map());
  if (!wiringV2.some(v => v.includes('V2 consumer class not in registry: fixtures.NewConsumer calls getProcessingTimeService()'))) {
    failures.push(`wiring V2 negative: unregistered consumer class must be red, got ${JSON.stringify(wiringV2)}`);
  }
  const wiringV2GreenRegistry = {services: mkWiringServices([]),
    consumerTable: [{class: 'fixtures.NewConsumer', getterOrRegistration: 'getProcessingTimeService()',
      service: 'fixtures.WiringPts', behaviorWithoutService: 'fixture'}]};
  const wiringV2Green = evaluateWiring(wiringV2GreenRegistry, new Map([['fixtures/NewConsumer.java', newConsumerSrc]]), new Map());
  if (wiringV2Green.some(v => v.includes('V2 '))) {
    failures.push(`wiring V2 positive: registered consumer class must be green, got ${JSON.stringify(wiringV2Green)}`);
  }

  // V3 negative: registry wiring point no longer present in main -> red
  const wiringV3Registry = {services: mkWiringServices([{file: 'fixtures/Gone.java', line: 7, injectionTiming: 'fixture'}]), consumerTable: []};
  const wiringV3 = evaluateWiring(wiringV3Registry, new Map([['fixtures/WiringMain.java', wiringMainSrc]]), new Map());
  if (!wiringV3.some(v => v.includes('V3 stale wiring point: fixtures/Gone.java:7'))) {
    failures.push(`wiring V3 negative: stale wiring point must be red, got ${JSON.stringify(wiringV3)}`);
  }

  // V4 negative: new set* service injection API on an operator subclass, unregistered -> red
  const v4OpSrc = `
package fixtures;
class WiringPts {
}
class V4Op extends io.nop.stream.core.operators.AbstractStreamOperator<String> {
    public void setCustomService(io.nop.stream.core.operators.ProcessingTimeService s) {
    }
}`;
  const wiringV4Registry = {services: mkWiringServices([]), consumerTable: []};
  const wiringV4 = evaluateWiring(wiringV4Registry, new Map([['fixtures/V4Op.java', v4OpSrc]]), new Map());
  if (!wiringV4.some(v => v.includes('V4 new service injection API not in registry: fixtures/V4Op.java')
      && v.includes('setCustomService'))) {
    failures.push(`wiring V4 negative: unregistered new service injection API must be red, got ${JSON.stringify(wiringV4)}`);
  }
  const v4NonServiceSrc = `
package fixtures;
class V4NonServiceOp extends io.nop.stream.core.operators.AbstractStreamOperator<String> {
    public void setMailboxExecutor(io.nop.stream.core.execution.MailboxExecutor m) {
    }
    public void setKeyContextElement(io.nop.stream.core.streamrecord.StreamRecord<?> r) {
    }
}`;
  const wiringV4NonService = evaluateWiring(wiringV4Registry, new Map([['fixtures/V4NonServiceOp.java', v4NonServiceSrc]]), new Map());
  if (wiringV4NonService.some(v => v.includes('V4 '))) {
    failures.push(`wiring V4 non-service exclusion: setMailboxExecutor/setKeyContextElement must NOT trigger V4, got ${JSON.stringify(wiringV4NonService)}`);
  }

  // V5 negative: registry consumer class no longer exists -> red
  const wiringV5Registry = {services: mkWiringServices([]),
    consumerTable: [{class: 'fixtures.GoneConsumer', getterOrRegistration: 'registerTimerService(',
      file: 'fixtures/GoneConsumer.java', service: 'fixtures.WiringPts', behaviorWithoutService: 'fixture'}]};
  const wiringV5 = evaluateWiring(wiringV5Registry, new Map([['fixtures/WiringMain.java', wiringMainSrc]]), new Map());
  if (!wiringV5.some(v => v.includes('V5 registry consumer class no longer exists: fixtures.GoneConsumer'))) {
    failures.push(`wiring V5 negative: stale registry consumer class must be red, got ${JSON.stringify(wiringV5)}`);
  }

  // ---------------------------------------------------------------------------
  // check-wildcard-imports self-test (roadmap item 22): the scanner must flag both
  // wildcard forms on a committed fixture (positive control — no silent skip) and
  // accept an explicit-imports sample.

  const wildcardBadInline = `
package fixtures;
import java.util.List;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BadWildcard {
    void check(List<String> names) { }
}`;
  const inlineViolations = scanWildcardImportsRaw(wildcardBadInline, 'fixtures/BadWildcard.java');
  if (inlineViolations.length !== 2
      || !inlineViolations[0].includes('import java.util.*')
      || !inlineViolations[1].includes('import static org.junit.jupiter.api.Assertions.*')) {
    failures.push(`wildcard-import scanner: expected exactly the java.util.* and Assertions.* lines flagged, got ${JSON.stringify(inlineViolations)}`);
  }
  const wildcardGoodInline = `
package fixtures;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
class GoodWildcard {
    void check(List<String> names) { assertTrue(names.isEmpty()); }
}`;
  const wildcardGoodViolations = scanWildcardImportsRaw(wildcardGoodInline, 'fixtures/GoodWildcard.java');
  if (wildcardGoodViolations.length !== 0) {
    failures.push(`wildcard-import scanner: expected 0 violations for explicit imports, got ${JSON.stringify(wildcardGoodViolations)}`);
  }

  // committed fixture: WildcardImportFixture.java contains a deliberate type wildcard
  // and a deliberate static wildcard — both must be flagged.
  const wildcardFixture = join(FIXTURES_DIR, 'WildcardImportFixture.java');
  if (existsSync(wildcardFixture)) {
    const fixtureViolations = scanWildcardImportsRaw(readFileSync(wildcardFixture, 'utf-8'), 'fixtures/WildcardImportFixture.java');
    if (fixtureViolations.length !== 2
        || !fixtureViolations.some(v => v.includes('import java.util.*'))
        || !fixtureViolations.some(v => v.includes('import static org.junit.jupiter.api.Assertions.*'))) {
      failures.push(`wildcard-import fixture: expected both wildcard forms flagged in WildcardImportFixture.java, got ${JSON.stringify(fixtureViolations)}`);
    }
  } else {
    failures.push('wildcard-import fixture: WildcardImportFixture.java missing — cannot prove scanner can go red on committed wildcard imports');
  }

  return failures;
}

function analyzeFixtureSources(sources) {
  const liveClasses = new Map();
  const liveEmissionPoints = new Set();
  for (const {rel, src} of sources) {
    const clean = stripAnnotations(stripCommentsAndStrings(src));
    const analyzed = analyzeOutputContractSource(clean, rel);
    for (const impl of analyzed.implClasses) {
      liveClasses.set(impl.fqcn, {rel, impl});
    }
    for (const point of analyzed.emissionPoints) {
      liveEmissionPoints.add(`${rel}:${point.line}`);
    }
  }
  return {liveClasses, liveEmissionPoints};
}

function scanIterationsFileRaw(clean, rel) {
  const violations = [];
  const fields = findSynchronizedFields(clean);
  const monitors = findSynchronizedMonitors(clean);
  const points = findIterationPoints(clean);
  const aliases = new Map();
  for (const f of fields) {
    const retRe = new RegExp(`return\\s+${f}\\s*;`);
    const methodRe = /\b(\w+)\s*\([^)]*\)\s*\{[^}]*\}/g;
    let mm;
    while ((mm = methodRe.exec(clean)) !== null) {
      if (retRe.test(mm[0])) {
        if (!aliases.has(mm[1])) aliases.set(mm[1], new Set());
        aliases.get(mm[1]).add(f);
      }
    }
  }
  for (const point of points) {
    if (!fields.includes(point.name) && !(aliases.get(point.name) && aliases.get(point.name).size > 0)) continue;
    const enclosing = monitors.filter(sm => sm.index < point.index);
    const valid = enclosing.some(sm => {
      if (sm.expr === 'this') return true;
      if (sm.expr === point.name) return true;
      if (aliases.has(sm.expr) && aliases.get(sm.expr).has(point.name)) return true;
      return false;
    });
    if (!valid) {
      const line = clean.substring(0, point.index).split('\n').length;
      violations.push(`${rel}:${line} iteration of synchronized collection '${point.name}' (${point.kind}) outside synchronized block`);
    }
  }
  return violations;
}

// ---------------------------------------------------------------------------
// init: regenerate methods arrays from live source

function initInventory() {
  let inventory = existsSync(INVENTORY_FILE) ? loadInventory() : {schemaVersion: 1, updated: new Date().toISOString().slice(0, 10), modules: {}};
  for (const module of GATE_MODULES) {
    if (!inventory.modules[module]) inventory.modules[module] = {};
    const classes = inventory.modules[module];
    for (const fqcn of Object.keys(classes)) {
      const live = enumerateClassMethods(fqcn, module);
      if (live) {
        classes[fqcn].methods = live.names;
      }
    }
  }
  inventory.updated = new Date().toISOString().slice(0, 10);
  writeFileSync(INVENTORY_FILE, JSON.stringify(inventory, null, 2) + '\n');
  return inventory;
}

// ---------------------------------------------------------------------------

function printViolations(title, violations) {
  if (violations.length > 0) {
    console.error(`${title}: ${violations.length} violation(s)`);
    for (const v of violations) console.error('  ' + v);
    return false;
  }
  return true;
}

function main() {
  const args = process.argv.slice(2);
  const command = args[0] || 'all';
  let ok = true;

  try {
    switch (command) {
      case 'inventory': {
        const inventory = loadInventory();
        ok = printViolations('inventory (gate table vs live source)', diffInventory(inventory)) && ok;
        break;
      }
      case 'sync': {
        const inventory = loadInventory();
        ok = printViolations('sync (catalog §4 vs gate table)', syncCatalogWithTable(inventory)) && ok;
        break;
      }
      case 'scan-iterations': {
        const {unpinned, stale} = runScanIterations();
        if (stale.length > 0) {
          console.error(`scan-iterations: ${stale.length} stale pin(s) (pinned violation no longer present — remove or update pin record)`);
          for (const s of stale) console.error('  ' + s);
          ok = false; // stale pins are a hard error: the pin record must reflect live reality
        }
        ok = printViolations('scan-iterations (synchronized collection iteration)', unpinned) && ok;
        break;
      }
      case 'scan-output-contract': {
        const {unpinned, stale, pinErrors} = runScanOutputContract();
        if (stale.length > 0) {
          console.error(`scan-output-contract: ${stale.length} stale pin(s) (pinned violation no longer present — remove or update pin record)`);
          for (const s of stale) console.error('  ' + s);
          ok = false;
        }
        if (pinErrors.length > 0) {
          console.error(`scan-output-contract: ${pinErrors.length} invalid pin(s) (pins may only cover cross-task instance classes)`);
          for (const s of pinErrors) console.error('  ' + s);
          ok = false;
        }
        ok = printViolations('scan-output-contract (invariant #6 V1-V5)', unpinned) && ok;
        break;
      }
      case 'scan-wiring': {
        const {unpinned, stale} = runScanWiring();
        if (stale.length > 0) {
          console.error(`scan-wiring: ${stale.length} stale pin(s) (pinned violation no longer present — remove or update pin record)`);
          for (const s of stale) console.error('  ' + s);
          ok = false;
        }
        ok = printViolations('scan-wiring (invariant #7 V1-V5)', unpinned) && ok;
        break;
      }
      case 'check-wildcard-imports': {
        const moduleArgIdx = args.indexOf('--module');
        let modules = WILDCARD_GATE_MODULES;
        if (moduleArgIdx >= 0) {
          const val = args[moduleArgIdx + 1];
          if (!val) {
            console.error('check-wildcard-imports: --module requires a value (comma-separated module names)');
            process.exit(2);
          }
          modules = val.split(',').map(s => s.trim()).filter(Boolean);
        }
        ok = printViolations(`check-wildcard-imports (scope: ${modules.join(',')})`, runWildcardImportScan(modules)) && ok;
        break;
      }
      case 'self-test': {
        const failures = runSelfTest();
        ok = printViolations('self-test', failures) && ok;
        break;
      }
      case 'init': {
        const inventory = initInventory();
        console.log(`Regenerated gate table: ${relative(PROJECT_ROOT, INVENTORY_FILE)} (${Object.values(inventory.modules).reduce((a, m) => a + Object.keys(m).length, 0)} classes)`);
        break;
      }
      case 'all': {
        const inventory = loadInventory();
        ok = printViolations('inventory', diffInventory(inventory)) && ok;
        ok = printViolations('sync', syncCatalogWithTable(inventory)) && ok;
        const {unpinned, stale} = runScanIterations();
        if (stale.length > 0) {
          console.error(`scan-iterations: ${stale.length} stale pin(s)`);
          for (const s of stale) console.error('  ' + s);
          ok = false;
        }
        ok = printViolations('scan-iterations', unpinned) && ok;
        const outputContract = runScanOutputContract();
        if (outputContract.stale.length > 0) {
          console.error(`scan-output-contract: ${outputContract.stale.length} stale pin(s)`);
          for (const s of outputContract.stale) console.error('  ' + s);
          ok = false;
        }
        if (outputContract.pinErrors.length > 0) {
          console.error(`scan-output-contract: ${outputContract.pinErrors.length} invalid pin(s)`);
          for (const s of outputContract.pinErrors) console.error('  ' + s);
          ok = false;
        }
        ok = printViolations('scan-output-contract', outputContract.unpinned) && ok;
        const wiring = runScanWiring();
        if (wiring.stale.length > 0) {
          console.error(`scan-wiring: ${wiring.stale.length} stale pin(s)`);
          for (const s of wiring.stale) console.error('  ' + s);
          ok = false;
        }
        ok = printViolations('scan-wiring', wiring.unpinned) && ok;
        // roadmap item 22: wildcard-import gate joined the default face after all 10
        // modules reached zero (plan 2026-09-03-1723-1 Phase 3) — keeps it at zero
        ok = printViolations('check-wildcard-imports (all 10 modules)', runWildcardImportScan(WILDCARD_GATE_MODULES)) && ok;
        const failures = runSelfTest();
        ok = printViolations('self-test', failures) && ok;
        break;
      }
      default:
        console.error(`Unknown command: ${command}`);
        console.error('Usage: node ai-dev/tools/check-nop-stream-invariants.mjs [inventory|sync|scan-iterations|scan-output-contract|scan-wiring|check-wildcard-imports [--module m1[,m2...]]|self-test|init|all]');
        process.exit(2);
    }
  } catch (e) {
    console.error(`check-nop-stream-invariants: ${e.message}`);
    process.exit(1);
  }

  if (!ok) process.exit(1);
  if (command !== 'all' && command !== 'init') {
    console.log(`${command}: OK`);
  }
}

main();
