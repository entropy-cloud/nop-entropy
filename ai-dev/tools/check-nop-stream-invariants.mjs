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
//   self-test         - positive control: proves the scanners reject known-bad input (no silent skip)
//   init              - (maintainer tool) regenerate the `methods` arrays of gate-inventory.json
//                       from live source, preserving existing `exclusions`
//   (no argument)     - runs inventory + sync + scan-iterations + scan-output-contract + self-test
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
const FIXTURES_DIR = join(INVARIANTS_DIR, 'fixtures');

const GATE_MODULES = ['nop-stream-core', 'nop-stream-runtime', 'nop-stream-cep'];
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
          implementsOutput: /\bimplements\b[^{]*\bOutput\b/.test(implText)
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
  const forwardRe = /(?:consumer\.accept\s*\(|\.collect\s*\(|\.accept\s*\()/;
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
        const failures = runSelfTest();
        ok = printViolations('self-test', failures) && ok;
        break;
      }
      default:
        console.error(`Unknown command: ${command}`);
        console.error('Usage: node ai-dev/tools/check-nop-stream-invariants.mjs [inventory|sync|scan-iterations|scan-output-contract|self-test|init|all]');
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
