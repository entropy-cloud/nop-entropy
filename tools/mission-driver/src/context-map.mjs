/**
 * context-map.mjs — Static analysis of flow injection points (P6 / FSD §3.5).
 *
 * Provides three pure functions consumed by the Context Explorer endpoints:
 *   - buildInjectionMap(flowName, projectRoot): for every step in a flow, list
 *     the {{var}} placeholders in its prompt, each resolved to a provenance
 *     source (the single VAR_PROVENANCE table) or flagged runtime.
 *   - listPrompts(projectRoot): prompt library with reverse used-by index.
 *   - listMemoryStores(projectRoot): self + per-module memory store inventory.
 *
 * The VAR_PROVENANCE table is the single source of truth for which variables
 * the engine injects (main.js delegates.vars). A drift unit-test hard-gates
 * that every main.js vars key is present in the table (FSD §7.4 residual
 * risk #2). New variables added to main.js MUST be registered here too.
 *
 * Zero npm dependencies — only node:fs / node:path / node:url.
 */

import { readFileSync, readdirSync, existsSync, statSync } from "node:fs";
import { resolve, dirname, basename, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const TOOL_ROOT = resolve(__dirname, "..");
const TOOL_FLOWS_DIR = resolve(TOOL_ROOT, "flows");
const TOOL_PROMPTS_DIR = resolve(TOOL_ROOT, "prompts");
const SELF_MEMORY_DIR = resolve(TOOL_ROOT, "memory");

// Same regex as resolveTemplateVars (expression.mjs:55) — keeping them in sync
// avoids drift between what the engine replaces and what the explorer reports.
const TEMPLATE_VAR_RE = /\{\{(\w+)\}\}/g;

/**
 * VAR_PROVENANCE — single source of truth for every variable the engine
 * injects into prompts (main.js:519-553 delegates.vars). Each entry carries:
 *   - source: human-readable provenance (config field / file path).
 *   - runtime: true when the value is only known at execution time
 *     (forEach item, timestamp, run directory) and cannot be statically
 *     resolved by the explorer.
 *
 * EXPECTED_VARS below mirrors the exact top-level keys of delegates.vars. The
 * drift test asserts these two stay in lock-step with main.js.
 */
export const VAR_PROVENANCE = {
  missionName:        { source: "config.missionName", runtime: false },
  projectRoot:        { source: "config.projectRoot", runtime: false },
  missionsDir:        { source: "config.missionsDir", runtime: false },
  roadmapPath:        { source: "mission.roadmapPath", runtime: false },
  plansDir:           { source: "mission.plansDir", runtime: false },
  planGuide:          { source: "mission.planGuide (fallback: plansDir + 00-plan-…guide.md)", runtime: false },
  auditsDir:          { source: "mission.auditsDir (fallback: 'audits')", runtime: false },
  contextDir:         { source: "mission.contextDir (fallback: '')", runtime: false },
  moduleContextFile:  { source: "{moduleDir}/CONTEXT.md probe — '(不存在)' suffix when absent", runtime: false },
  moduleDir:          { source: "mission.moduleDir (fallback: '')", runtime: false },
  testCmd:            { source: "mission.commands.test", runtime: false },
  buildCmd:           { source: "mission.commands.build (fallback: '')", runtime: false },
  lintCmd:            { source: "mission.commands.lint (fallback: '')", runtime: false },
  typecheckCmd:       { source: "mission.commands.typecheck (fallback: '')", runtime: false },
  checkCmd:           { source: "mission.commands.check (fallback: '')", runtime: false },
  commitFormat:       { source: "mission.commitFormat (fallback: '')", runtime: false },
  multiAuditPrompt:   { source: "mission.prompts.multiAudit (fallback: '')", runtime: false },
  openAuditPrompt:    { source: "mission.prompts.openAudit (fallback: '')", runtime: false },
  sourcePaths:        { source: "base.local.json sourcePaths (joined with \\n; per-developer)", runtime: false },
  TIMESTAMP:          { source: "config.timestamp (run start wall-clock)", runtime: true },
  runDir:             { source: "config.runDir (engine _tmp/<runId> directory)", runtime: true },
  selfMemoryIndex:    { source: "tools/mission-driver/memory/_index.md (Reflexion top rules)", runtime: false },
  moduleMemoryIndex:  { source: "docs/memory/<module>/_index.md ('' when module is the tool itself)", runtime: false },
  PLAN_FILE:          { source: "forEach activePlans() — current plan file path (subflow flowArgs)", runtime: true },
};

// Mirror of main.js:519-553 delegates.vars top-level keys. The drift test
// cross-checks this array against (1) VAR_PROVENANCE and (2) a live extraction
// from main.js source, so a forgotten sync on either side turns the test red.
// Lines reference main.js at the time of writing; update both when vars change.
export const EXPECTED_VARS = [
  "missionName",        // main.js:520
  "projectRoot",        // main.js:521
  "missionsDir",        // main.js:522
  "roadmapPath",        // main.js:523
  "plansDir",           // main.js:524
  "planGuide",          // main.js:525
  "auditsDir",          // main.js:526
  "contextDir",         // main.js:527
  "moduleContextFile",  // main.js:528
  "moduleDir",          // main.js:532
  "testCmd",            // main.js:533
  "buildCmd",           // main.js:534
  "lintCmd",            // main.js:535
  "typecheckCmd",       // main.js:536
  "checkCmd",           // main.js:537
  "commitFormat",       // main.js:538
  "multiAuditPrompt",   // main.js:538
  "openAuditPrompt",    // main.js:539
  "sourcePaths",        // main.js:540
  "TIMESTAMP",          // main.js:543
  "runDir",             // main.js:544
  "selfMemoryIndex",    // main.js:569
  "moduleMemoryIndex",  // main.js:572
  "PLAN_FILE",          // main.js (subflow flowArgs, main flow EXEC_PLANS.flowArgs)
];

/**
 * Extract the top-level keys of `delegates.vars` from main.js source text.
 * Used by the drift test to detect when a developer adds a var to main.js but
 * forgets to register it in EXPECTED_VARS / VAR_PROVENANCE.
 *
 * Resilient: returns [] if the block cannot be located (the test then falls
 * back to EXPECTED_VARS-only). Uses a brace-depth scan within the `vars: {`
 * block rather than a flat regex, so nested IIFEs (moduleContextFile /
 * moduleMemoryIndex) do not break extraction.
 *
 * @param {string} mainJsPath absolute path to src/main.js
 * @returns {string[]}
 */
export function extractVarsKeysFromMainJs(mainJsPath) {
  let src;
  try {
    src = readFileSync(mainJsPath, "utf8");
  } catch {
    return [];
  }
  const startIdx = src.indexOf("vars: {");
  if (startIdx === -1) return [];
  // Scan from the opening brace, tracking depth. A top-level key is an
  // identifier immediately followed by ':' that appears at depth 1 (directly
  // inside the vars object, not inside a nested IIFE object).
  let i = src.indexOf("{", startIdx);
  if (i === -1) return [];
  let depth = 0;
  const keys = [];
  const keyRe = /(\w+):/g;
  for (let j = i; j < src.length; j++) {
    const ch = src[j];
    if (ch === "{") depth++;
    else if (ch === "}") {
      depth--;
      if (depth === 0) break; // closed the vars object
    }
    // Only capture keys at depth 1 (immediate children of vars).
    if (depth === 1 && /[A-Za-z_]/.test(ch)) {
      keyRe.lastIndex = j;
      const m = keyRe.exec(src);
      if (m && m.index === j) {
        keys.push(m[1]);
        j = keyRe.lastIndex - 1;
      }
    }
  }
  return keys;
}

// ── Flow discovery (mirrors handleListFlows / flow-loader searchDirs) ──────

function flowSearchDirs(projectRoot) {
  const dirs = [];
  const projectFlows = resolve(projectRoot, "missions", "flows");
  if (existsSync(projectFlows)) dirs.push(projectFlows);
  dirs.push(TOOL_FLOWS_DIR);
  return dirs;
}

function findFlowFile(name, projectRoot) {
  const safe = basename(name);
  for (const dir of flowSearchDirs(projectRoot)) {
    const f = resolve(dir, `${safe}.json`);
    if (existsSync(f)) return f;
  }
  return null;
}

function promptSearchDirs(projectRoot) {
  const dirs = [];
  const projectPrompts = resolve(projectRoot, "missions", "prompts");
  if (existsSync(projectPrompts)) dirs.push(projectPrompts);
  dirs.push(TOOL_PROMPTS_DIR);
  return dirs;
}

/**
 * Resolve a flow's `promptPath` (e.g. "prompts/health-check.md") to an
 * absolute file path by searching project prompts dir then the tool prompts
 * dir. Mirrors flow-loader.loadPrompt precedence.
 * @returns {string|null} absolute path or null if not found
 */
function resolvePromptPath(promptPath, projectRoot) {
  if (!promptPath) return null;
  const base = basename(promptPath);
  for (const dir of promptSearchDirs(projectRoot)) {
    const f = resolve(dir, base);
    if (existsSync(f)) return f;
  }
  return null;
}

function readPromptText(promptPath, projectRoot) {
  const abs = resolvePromptPath(promptPath, projectRoot);
  if (!abs) return null;
  try {
    return readFileSync(abs, "utf8");
  } catch {
    return null;
  }
}

/** Extract unique {{var}} names from a prompt string, preserving first-seen order. */
function extractVarNames(text) {
  if (!text) return [];
  const seen = new Set();
  const out = [];
  let m;
  TEMPLATE_VAR_RE.lastIndex = 0;
  while ((m = TEMPLATE_VAR_RE.exec(text)) !== null) {
    if (!seen.has(m[1])) {
      seen.add(m[1]);
      out.push(m[1]);
    }
  }
  return out;
}

// ── buildInjectionMap ─────────────────────────────────────────────────────

/**
 * Extract directed transition edges from a flow's `steps` for state-machine
 * visualization. Each step's `transitions` object maps a marker (pass/fail/
 * created/all_complete/...) to a {goto|done|retry} target. `onError` is surfaced
 * as a dashed "error" edge so the exceptional path is visible too.
 *
 * Terminal targets (`done:<status>`) become `terminal:true` edges whose `to`
 * is a synthetic `done:<status>` node the frontend renders distinctly.
 *
 * @param {object} steps parsed flow.steps object
 * @returns {Array<{from:string, to:string, marker:string, terminal?:boolean, retry?:boolean, dashed?:boolean}>}
 */
function extractEdges(steps) {
  const edges = [];
  for (const [stepName, stepDef] of Object.entries(steps)) {
    const sd = stepDef && typeof stepDef === "object" ? stepDef : {};
    const transitions = (sd.transitions && typeof sd.transitions === "object") ? sd.transitions : {};
    for (const [marker, trans] of Object.entries(transitions)) {
      const t = trans && typeof trans === "object" ? trans : {};
      if (t.goto) {
        edges.push({ from: stepName, to: t.goto, marker, retry: !!t.retry });
      } else if (t.done) {
        edges.push({ from: stepName, to: `done:${t.done}`, marker, terminal: true });
      }
    }
    // onError is an exceptional path — surface as a dashed "error" edge so the
    // viewer can tell normal transitions from error handling at a glance.
    const oe = sd.onError;
    if (oe && typeof oe === "object") {
      if (oe.goto) edges.push({ from: stepName, to: oe.goto, marker: "error", dashed: true });
      else if (oe.done) edges.push({ from: stepName, to: `done:${oe.done}`, marker: "error", terminal: true, dashed: true });
      else if (oe.retry) edges.push({ from: stepName, to: oe.retry, marker: "error", retry: true, dashed: true });
    }
  }
  return edges;
}

/**
 * Build the flow-level injection map (FSD §3.5.2A). For each step in the flow,
 * read its prompt and report every {{var}} placeholder with its provenance,
 * plus memory/context/sourcePaths annotations. Subflow steps (`type:"subflow"`
 * with a `flow` field) are recursively expanded into `substeps` so the whole
 * execution tree is visible from the top-level flow (cycle-guarded). Top-level
 * `edges` (state-machine transitions) are returned for graph visualization.
 *
 * @param {string} flowName flow name (basename-cleaned internally)
 * @param {string} projectRoot absolute project root
 * @returns {{flowName:string, steps:Array, edges:Array, notFound?:boolean}}
 */
export function buildInjectionMap(flowName, projectRoot) {
  return buildInjectionMapInner(flowName, projectRoot, new Set());
}

function buildInjectionMapInner(flowName, projectRoot, visited) {
  const safeName = basename(flowName);
  // Cycle guard: a flow already on the current root→leaf path would recurse
  // forever. Each branch gets its own visited copy (new Set) so sibling
  // subflows that legitimately share a child flow don't false-positive block.
  if (visited.has(safeName)) {
    return { flowName: safeName, steps: [], edges: [], cycle: true };
  }
  const nextVisited = new Set(visited);
  nextVisited.add(safeName);

  const flowFile = findFlowFile(safeName, projectRoot);
  if (!flowFile) return { flowName: safeName, steps: [], edges: [], notFound: true };

  let flow;
  try {
    flow = JSON.parse(readFileSync(flowFile, "utf8"));
  } catch (e) {
    return { flowName: safeName, steps: [], edges: [], error: `flow parse error: ${e.message}` };
  }

  const steps = (flow.steps && typeof flow.steps === "object") ? flow.steps : {};
  const entry = flow.entry || null;
  const result = [];

  for (const [stepName, stepDef] of Object.entries(steps)) {
    const sd = stepDef && typeof stepDef === "object" ? stepDef : {};
    const promptPath = sd.promptPath || null;
    const text = promptPath ? readPromptText(promptPath, projectRoot) : null;
    const varNames = extractVarNames(text);

    const promptVars = varNames.map((name) => {
      const prov = VAR_PROVENANCE[name];
      return {
        name,
        source: prov ? prov.source : "(unknown — dynamic/forEach/contextual var, not in delegates.vars)",
        runtime: prov ? prov.runtime : true,
      };
    });

    // Memory blocks: steps that reference the Reflexion index placeholders.
    const memoryBlocks = promptVars
      .filter((v) => v.name === "selfMemoryIndex" || v.name === "moduleMemoryIndex")
      .map((v) => ({ name: v.name, source: v.source }));

    // Context files: steps referencing contextDir / moduleContextFile.
    const contextFiles = promptVars
      .filter((v) => v.name === "contextDir" || v.name === "moduleContextFile")
      .map((v) => ({ name: v.name, source: v.source }));

    // sourcePaths flag: whether the prompt surfaces dependency source roots.
    const usesSourcePaths = varNames.includes("sourcePaths");

    const stepEntry = {
      name: stepName,
      type: sd.type || null,
      isEntry: stepName === entry,
      promptPath,
      promptVars,
      memoryBlocks,
      contextFiles,
      sourcePaths: usesSourcePaths,
    };

    // Recurse into subflow steps so nested plan-execution / deep-audit-loop
    // nodes are visible from the top-level flow's injection map.
    if (sd.type === "subflow" && typeof sd.flow === "string") {
      stepEntry.subflowName = basename(String(sd.flow));
      const sub = buildInjectionMapInner(sd.flow, projectRoot, nextVisited);
      stepEntry.substeps = sub.steps || [];
      if (sub.notFound) stepEntry.subflowMissing = true;
    }

    result.push(stepEntry);
  }

  // Top-level transitions for the state-machine graph view. Computed from the
  // same `steps` object so nodes and edges stay in lock-step (subflow internals
  // don't contribute edges to the parent graph — they're a single subflow node).
  const edges = extractEdges(steps);

  return { flowName: safeName, entry, steps: result, edges };
}

// ── listPrompts (with reverse used-by index) ──────────────────────────────

/**
 * Collect every flow's step→promptPath references into a reverse index keyed
 * by prompt name (basename without extension). Used by listPrompts.
 * @returns {Map<string, Array<{flow:string, step:string}>>}
 */
function buildPromptUsedBy(projectRoot) {
  const index = new Map();
  const allFlows = [];
  const seen = new Set();
  for (const dir of flowSearchDirs(projectRoot)) {
    let names;
    try {
      names = readdirSync(dir);
    } catch {
      continue;
    }
    for (const fname of names) {
      if (!fname.endsWith(".json")) continue;
      const name = fname.slice(0, -5);
      if (seen.has(name)) continue;
      seen.add(name);
      try {
        allFlows.push({ name, data: JSON.parse(readFileSync(join(dir, fname), "utf8")) });
      } catch {
        continue;
      }
    }
  }

  for (const { name: flowName, data } of allFlows) {
    const steps = data.steps && typeof data.steps === "object" ? data.steps : {};
    for (const [stepName, sd] of Object.entries(steps)) {
      const def = sd && typeof sd === "object" ? sd : {};
      if (def.promptPath) {
        const promptName = basename(def.promptPath, ".md");
        if (!index.has(promptName)) index.set(promptName, []);
        index.get(promptName).push({ flow: flowName, step: stepName });
      }
    }
  }
  return index;
}

/**
 * List the prompt library with a summary, vars, and reverse used-by index.
 * Scans the tool prompts dir + optional project prompts dir (FSD §3.5.2 panel 3).
 *
 * @param {string} projectRoot absolute project root
 * @returns {{prompts:Array<{name,summary,vars,usedBy}>}}
 */
export function listPrompts(projectRoot) {
  const usedByIndex = buildPromptUsedBy(projectRoot);
  const seen = new Set();
  const prompts = [];

  for (const dir of promptSearchDirs(projectRoot)) {
    let names;
    try {
      names = readdirSync(dir).filter((n) => n.endsWith(".md")).sort();
    } catch {
      continue;
    }
    for (const fname of names) {
      const name = basename(fname, ".md");
      if (seen.has(name)) continue;
      seen.add(name);
      let text = "";
      try {
        text = readFileSync(join(dir, fname), "utf8");
      } catch {
        continue;
      }
      // Summary = first non-empty, non-heading paragraph.
      const summary = text
        .split(/\r?\n/)
        .map((l) => l.trim())
        .filter((l) => l && !l.startsWith("#") && !l.startsWith(">") && !l.startsWith("<!--"))
        .slice(0, 1)
        .join(" ")
        .slice(0, 160);
      const vars = extractVarNames(text);
      const usedBy = usedByIndex.get(name) || [];
      prompts.push({ name, summary, vars, usedBy });
    }
  }

  return { prompts };
}

// ── listMemoryStores ──────────────────────────────────────────────────────

/**
 * Parse the YAML-ish frontmatter of a memory _index.md to surface
 * lesson_count / updated in the browser. Tolerant of malformed frontmatter.
 * @returns {{lessonCount?:number, updated?:string, raw?:string}}
 */
function parseIndexFrontmatter(text) {
  if (!text) return {};
  const m = text.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!m) return {};
  const block = m[1];
  const out = {};
  const lc = block.match(/lesson_count:\s*(\d+)/);
  if (lc) out.lessonCount = Number(lc[1]);
  const up = block.match(/updated:\s*([^\n\r]+)/);
  if (up) out.updated = up[1].trim().replace(/['"]/g, "");
  out.raw = block;
  return out;
}

function scanMemoryStore(storeName, dir) {
  const files = [];
  let indexSummary = null;
  let exists = false;
  try {
    const names = readdirSync(dir).filter((n) => n.endsWith(".md")).sort();
    exists = names.length > 0;
    for (const name of names) {
      let sizeBytes = 0;
      try {
        sizeBytes = statSync(join(dir, name)).size;
      } catch {}
      files.push({ name, sizeBytes });
      if (name === "_index.md") {
        try {
          indexSummary = parseIndexFrontmatter(readFileSync(join(dir, name), "utf8"));
        } catch {}
      }
    }
  } catch {
    exists = false;
  }
  return { store: storeName, dir, exists, files, indexSummary };
}

/**
 * List self + per-module memory stores (FSD §3.5.2 panel 2). The "self" store
 * is the engine's own Reflexion memory; per-module stores live under
 * docs/memory/<MODULE>/.
 *
 * @param {string} projectRoot absolute project root
 * @returns {{stores:Array}}
 */
export function listMemoryStores(projectRoot) {
  const stores = [];

  // Self / engine store (aliased as both "self" and "mission-driver").
  stores.push(scanMemoryStore("self", SELF_MEMORY_DIR));

  // Per-module stores under docs/memory/*.
  const docsMemoryDir = resolve(projectRoot, "docs", "memory");
  let moduleNames = [];
  try {
    moduleNames = readdirSync(docsMemoryDir, { withFileTypes: true })
      .filter((e) => e.isDirectory())
      .map((e) => e.name)
      .sort();
  } catch {
    moduleNames = [];
  }
  for (const mn of moduleNames) {
    stores.push(scanMemoryStore(mn, resolve(docsMemoryDir, mn)));
  }

  return { stores };
}

// Re-exported so monitor.js (and the drift test) resolve the memory dir roots
// from one place, avoiding a second hard-coded path.
export const MEMORY_DIRS = {
  self: SELF_MEMORY_DIR,
};
