/**
 * monitor.js — Mission-Driver Monitor Server (Node built-in http + SSE).
 *
 * Zero npm dependencies: only uses node:http, node:fs, node:path.
 * Provides 6 REST endpoints, 1 SSE endpoint, and static file hosting for web/.
 *
 * FSD §5 (§5.1 lifecycle, §5.2 REST API, §5.3 SSE, §5.4 static hosting).
 * Business rules: BR-4 (read-only), BR-5 (same process), BR-7 (zero deps),
 * BR-6 (backward compat), NFR-9 (zero npm deps), NFR-11 (non-blocking).
 */

import { createServer } from "node:http";
import {
  existsSync,
  readFileSync,
  readdirSync,
  statSync,
  rmSync,
  openSync,
  readSync,
  closeSync,
  fstatSync,
  writeFileSync,
  renameSync,
  mkdirSync,
} from "node:fs";
import { join, resolve, basename, extname, relative, isAbsolute, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { getSpawner, __setSpawnerForTest } from "./spawner.mjs";
import { PLAN_STATUS_RE } from "./plan-check.mjs";
import { getAllProcesses, getDescendants } from "./platform.mjs";
import { reconcileStaleRuns, isAliveAndOurs, markAborted } from "./run-reconcile.mjs";
import { startDraftJob, readDraftJob, listDraftJobs, validateDraftDesc } from "./draft-job.mjs";
import {
  buildInjectionMap,
  listPrompts,
  listMemoryStores,
} from "./context-map.mjs";

// Testability seam for POST /api/runs AND POST /api/missions/draft: production
// uses the real child_process spawn; tests override this via
// {@link __setSpawnerForTest} (re-exported from the shared spawner.mjs seam) so
// no live engine process is launched. The seam is shared so ONE stub covers
// both handleStartRun and startDraftJob (FSD §8 R2 spawn safety — the tests
// must assert the spawn is invoked with shell:false + args array without
// actually spawning node main.js).
export { __setSpawnerForTest };

// Resolve the tool's own flows/ dir relative to this module, mirroring
// flow-loader.js TOOL_FLOWS_DIR. Robust regardless of the process CWD, so the
// scenario-definition endpoint finds flows/<flow>.json whether
// monitor.js runs under the engine or standalone.
const __dirname = dirname(fileURLToPath(import.meta.url));
const TOOL_ROOT = resolve(__dirname, "..");
const TOOL_FLOWS_DIR = resolve(__dirname, "..", "flows");
const TOOL_PROMPTS_DIR = resolve(__dirname, "..", "prompts");
const SELF_MEMORY_DIR = resolve(__dirname, "..", "memory");

// parseRoadmapMarkdown lives in a shared module so the Monitor Server and the
// FlowEngine (terminal reconciliation) use ONE parser. Re-exported here to keep
// the historical `import { parseRoadmapMarkdown } from "./monitor.js"` contract
// (monitor.test.js) intact.
import { parseRoadmapMarkdown } from "./roadmap-check.mjs";
export { parseRoadmapMarkdown };

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".mjs": "application/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".gif": "image/gif",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf",
  ".map": "application/json; charset=utf-8",
};

// ── HTTP helpers ──────────────────────────────────────────────────────────

function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
  });
  res.end(body);
}

function matchRoute(pathname, pattern) {
  const pp = pathname.split("/").filter(Boolean);
  const sp = pattern.split("/").filter(Boolean);
  if (pp.length !== sp.length) return null;
  const params = {};
  for (let i = 0; i < sp.length; i++) {
    if (sp[i].startsWith(":")) {
      params[sp[i].slice(1)] = decodeURIComponent(pp[i]);
    } else if (sp[i] !== pp[i]) {
      return null;
    }
  }
  return params;
}

function parseInt32(val, def) {
  const n = parseInt(val, 10);
  return Number.isFinite(n) ? n : def;
}

function isPathWithin(filePath, baseDir) {
  const rel = relative(baseDir, filePath);
  return rel === "" || (!rel.startsWith("..") && !isAbsolute(rel));
}

// Collect + JSON-parse a request body (for PUT/POST endpoints). Caps at 1MB to
// avoid unbounded buffering. `cb(err, parsed)` is invoked once on completion.
function readJsonBody(req, cb) {
  const chunks = [];
  let size = 0;
  const MAX = 1 * 1024 * 1024;
  req.on("data", (chunk) => {
    size += chunk.length;
    if (size > MAX) {
      req.destroy();
      cb(new Error("body too large (max 1MB)"));
      return;
    }
    chunks.push(chunk);
  });
  req.on("end", () => {
    const text = Buffer.concat(chunks).toString("utf8");
    if (!text.trim()) {
      cb(new Error("empty body"));
      return;
    }
    try {
      cb(null, JSON.parse(text));
    } catch (e) {
      cb(e);
    }
  });
  req.on("error", cb);
}

// ── Static file serving (FSD §5.4) ────────────────────────────────────────

function serveStaticFile(res, filePath, webDir) {
  const resolvedPath = resolve(filePath);
  const webRoot = resolve(webDir);
  if (!isPathWithin(resolvedPath, webRoot)) {
    sendJson(res, 403, { error: "forbidden" });
    return;
  }
  if (!existsSync(resolvedPath) || !statSync(resolvedPath).isFile()) {
    sendJson(res, 404, { error: "not found" });
    return;
  }
  const ext = extname(resolvedPath).toLowerCase();
  const mime = MIME_TYPES[ext] || "application/octet-stream";
  const content = readFileSync(resolvedPath);
  res.writeHead(200, { "Content-Type": mime, "Content-Length": content.length });
  res.end(content);
}

function serveIndex(res, webDir) {
  const indexFile = resolve(webDir, "index.html");
  if (existsSync(indexFile) && statSync(indexFile).isFile()) {
    const content = readFileSync(indexFile);
    res.writeHead(200, {
      "Content-Type": "text/html; charset=utf-8",
      "Content-Length": content.length,
    });
    res.end(content);
    return;
  }
  // Placeholder when web/dist/index.html doesn't exist yet (prod mode,
  // API-only degradation — FSD §8). Points the user at the build/dev commands.
  const placeholder =
    '<!DOCTYPE html>\n<html><head><meta charset="utf-8">' +
    "<title>Mission-Driver Monitor</title></head><body>" +
    "<p>Monitor server is running in API-only mode. The Vue UI was not found.</p>" +
    "<p>Build it: <code>npm --prefix web run build</code></p>" +
    "<p>Or run in dev mode: <code>--dev</code> flag + <code>npm --prefix web run dev</code> (vite at :5173)</p>" +
    "</body></html>";
  res.writeHead(200, {
    "Content-Type": "text/html; charset=utf-8",
    "Content-Length": Buffer.byteLength(placeholder),
  });
  res.end(placeholder);
}

// Dev mode (webDir === null): static hosting is disabled (vite dev server
// serves the frontend at :5173 and proxies /api → :9300). Static GETs return a
// hint JSON so a stray browser hit on :9300 explains the situation. API/SSE
// routes are matched earlier in handleRequest and are unaffected.
function serveDevHint(res) {
  sendJson(res, 200, {
    error: "dev mode: static hosting disabled, run vite dev at :5173",
  });
}

// ── Run discovery helpers ─────────────────────────────────────────────────

function listMissionRunDirs(projectRoot) {
  const tmpDir = resolve(projectRoot, "_tmp");
  if (!existsSync(tmpDir)) return [];
  try {
    return readdirSync(tmpDir)
      .filter((f) => f.endsWith("-mission-driver"))
      .sort()
      .reverse();
  } catch {
    return [];
  }
}

function findRunDir(projectRoot, runId) {
  const safe = basename(runId);
  const candidate = resolve(projectRoot, "_tmp", safe);
  if (existsSync(candidate) && statSync(candidate).isDirectory()) return candidate;
  return null;
}

function readRunState(runDir) {
  const stateFile = join(runDir, "run-state.json");
  if (!existsSync(stateFile)) return null;
  try {
    return JSON.parse(readFileSync(stateFile, "utf8"));
  } catch {
    return null;
  }
}

function readSubflowState(runDir, file) {
  if (!file) return null;
  const subFile = join(runDir, file);
  if (!existsSync(subFile)) return null;
  try {
    return JSON.parse(readFileSync(subFile, "utf8"));
  } catch {
    return null;
  }
}

export function mergeSubflowChildren(runDir, steps) {
  for (const step of steps) {
    if (step.type !== "subflow") continue;

    // Build a per-forEachIndex map combining subflowRuns metadata + disk state.
    // The disk file is the LIVE source of truth for status/steps/currentStep;
    // subflowRuns provides forEachItem metadata (the disk file doesn't have it).
    //
    // Two bugs this unifies:
    //  (a) subflowRuns placeholder with file=null (non-forEach subflow start):
    //      engine writes {forEachIndex:0, status:"running", file:null} before
    //      the child's run-state-<stepName>-<visits>-0.json exists, then never
    //      updates file when the child starts writing. readSubflowState(null)
    //      returns null → child renders with empty steps.
    //  (b) forEach subflow with N completed + 1 in-flight: engine appends to
    //      subflowRuns on item COMPLETION only (WI5 _wfAppendSubflowRun), so
    //      while item N is running its disk file exists but is absent from
    //      subflowRuns.
    // Both are fixed by always scanning disk and MERGING live state over
    // whatever subflowRuns seeded (previously the disk scan was either skipped
    // entirely when subflowRuns was non-empty, or skipped per-index when the
    // index was already seeded).
    const childrenByIndex = new Map();

    // 1. Seed forEachItem metadata + live state from subflowRuns. When r.file
    //    is a valid path, read it directly (the authoritative source for
    //    completed items — works even when step.visits is missing, which the
    //    disk-scan prefix computation below cannot handle).
    if (Array.isArray(step.subflowRuns)) {
      for (const r of step.subflowRuns) {
        const cs = r.file ? readSubflowState(runDir, r.file) : null;
        childrenByIndex.set(r.forEachIndex, {
          forEachIndex: r.forEachIndex,
          forEachItem: r.forEachItem,
          file: r.file || null,
          status: (cs && cs.status) || r.status || "unknown",
          steps: (cs && Array.isArray(cs.steps)) ? cs.steps : [],
          currentStep: (cs && cs.currentStep) || null,
        });
      }
    }

    // 2. Scan disk for run-state-<stepName>-<visits>-<idx>.json files. MERGE
    //    live state (status/steps/currentStep) into existing seed entries
    //    (disk is fresher); ADD new entries for indices not in the seed
    //    (catches in-flight forEach items not yet appended to subflowRuns).
    //
    //    The filename encodes visits (engine.js _wfOpen increments visits
    //    on every entry, so real subflow steps always carry a numeric
    //    visits). When visits is absent (synthetic test step, deserialized
    //    legacy state, or corruption), the narrow `${step.name}-${step.visits}-`
    //    prefix would silently produce "SUB-undefined-" and match nothing —
    //    a silent no-op that drops the file:null placeholder case to empty
    //    steps (O5). Fall back to a broader stepName-only prefix in that
    //    case; the idx (trailing `-N` segment, parsed below) still
    //    disambiguates forEach items, and the seed loop's direct r.file
    //    read above remains the authoritative path when a real file
    //    pointer exists.
    const prefix = (step.visits == null)
      ? `${step.name}-`
      : `${step.name}-${step.visits}-`;
    let matching = [];
    try {
      matching = readdirSync(runDir)
        .filter(function (f) { return f.startsWith("run-state-") && f.endsWith(".json"); })
        .map(function (f) { return f.slice("run-state-".length, -".json".length); })
        .filter(function (id) { return id.startsWith(prefix); });
    } catch {}

    for (const id of matching) {
      const idx = parseInt(id.split("-").pop(), 10) || 0;
      const fileName = `run-state-${id}.json`;
      const cs = readSubflowState(runDir, fileName);
      const liveSteps = (cs && Array.isArray(cs.steps)) ? cs.steps : [];
      const liveStatus = (cs && cs.status) || null;
      const liveCurrentStep = (cs && cs.currentStep) || null;

      if (childrenByIndex.has(idx)) {
        // MERGE: disk state wins on live fields; seed keeps forEachItem
        // (disk file doesn't carry it). This fixes the file=null placeholder
        // case — the seed had no readable state, disk provides it.
        const existing = childrenByIndex.get(idx);
        if (liveStatus) existing.status = liveStatus;
        if (liveSteps.length > 0) existing.steps = liveSteps;
        if (liveCurrentStep) existing.currentStep = liveCurrentStep;
        if (!existing.file) existing.file = fileName;
      } else {
        // Disk-only: in-flight item not yet in subflowRuns. Read forEachItem
        // (plan path) from the child's own state file — engine.js _initWorkflow
        // persists it at child start time specifically so the monitor can
        // display the plan name before the parent appends to subflowRuns.
        childrenByIndex.set(idx, {
          forEachIndex: idx,
          forEachItem: (cs && cs.forEachItem) || null,
          file: fileName,
          status: liveStatus || "unknown",
          steps: liveSteps,
          currentStep: liveCurrentStep,
        });
      }
    }

    if (childrenByIndex.size > 0) {
      step.children = [...childrenByIndex.values()]
        .sort((a, b) => a.forEachIndex - b.forEachIndex);
    }

    // Flag forEach subflows so the frontend can show "Plan N" labels for ALL
    // children (including disk-only in-flight ones whose forEachItem is null).
    // Without this flag, the frontend can't distinguish a disk-only forEach
    // child (forEachItem=null because not yet in subflowRuns) from a non-forEach
    // subflow child (forEachItem=null because there's no iteration). Detection:
    // any subflowRuns entry carries a non-null forEachItem → forEach subflow.
    if (Array.isArray(step.subflowRuns) && step.subflowRuns.some((r) => r.forEachItem != null)) {
      step.forEach = true;
    }
  }
}

function readEventsArray(runDir) {
  const eventsFile = join(runDir, "events.jsonl");
  if (!existsSync(eventsFile)) return [];
  try {
    const content = readFileSync(eventsFile, "utf8");
    const events = [];
    for (const line of content.split("\n")) {
      if (!line.trim()) continue;
      try {
        events.push(JSON.parse(line));
      } catch {}
    }
    return events;
  } catch {
    return [];
  }
}

function synthesizeFromEvents(runDir, dirName, events) {
  const first = events[0] || {};
  const last = events.length > 0 ? events[events.length - 1] : {};
  const completed = events.find((e) => e.type === "run_completed");
  return {
    runId: first.runId || dirName,
    missionName: first.missionName || null,
    flowName: first.flowName || null,
    runDir,
    status: completed ? completed.status || "completed" : events.length > 0 ? "running" : "unknown",
    startedAt: first.ts || null,
    updatedAt: last.ts || null,
    endedAt: completed ? completed.ts || null : null,
    currentStep: null,
    steps: [],
    // WI5 — old runs without run-state.json (events-only fallback) get the
    // same default shape as runs with state, so the frontend never observes
    // undefined for these fields.
    auditRound: 0,
    maxAuditRounds: 0,
  };
}

function summarizeRun(state, runDir, dirName) {
  return {
    runId: state.runId || dirName,
    missionName: state.missionName || null,
    flowName: state.flowName || null,
    status: state.status || "unknown",
    startedAt: state.startedAt || null,
    updatedAt: state.updatedAt || null,
    endedAt: state.endedAt || null,
    currentStep: state.currentStep || null,
    stepCount: Array.isArray(state.steps) ? state.steps.length : 0,
    runDir: state.runDir || runDir,
    // WI5 — surface DEEP_AUDIT progress in the run list. `?? 0` falls back
    // for legacy run-state.json that predates WI1.
    auditRound: state.auditRound ?? 0,
    maxAuditRounds: state.maxAuditRounds ?? 0,
  };
}

// Lazy per-request reconciliation (FSD §3.1.4 G3 / §4.2 R3): if a run's state
// still says "running" but its owning process is gone, flip it to "aborted" on
// disk and re-read so the response reflects reality. The caller passes ONE
// getAllProcesses() snapshot shared across all "running" runs in the request
// (R3 optimisation). Safety (FSD §4.3):
//   - only runs with a persisted pid are tested here; pid-less runs defer to
//     the startup / time-based sweep (reconcileStaleRuns) so a freshly-started
//     run whose pid isn't written yet is never mis-killed;
//   - isAliveAndOurs(...) === true (alive AND ours) → NEVER touch (protects
//     coexisting active missions).
function lazyReconcileRun(runDir, state, processes) {
  if (!state || state.status !== "running") return state;
  const pid = typeof state.pid === "number" ? state.pid : null;
  if (!pid) return state;
  if (isAliveAndOurs(pid, state.runId, state.missionName, processes)) return state;
  try {
    markAborted(runDir, `reconciled: lazy check, owning process gone (pid ${pid})`);
    return readRunState(runDir) || state;
  } catch {
    return state;
  }
}

function listStepLogs(runDir) {
  let files;
  try {
    files = readdirSync(runDir);
  } catch {
    return [];
  }
  const result = [];
  for (const f of files) {
    // Agent log: oc-<step>-<ts>-<hash>.log
    const m = f.match(/^oc-(.+)-(\d+)-([a-z0-9]+)\.log$/);
    if (m) {
      try {
        const stat = statSync(join(runDir, f));
        result.push({ step: m[1], fileName: f, sizeBytes: stat.size, type: "log" });
      } catch {}
    }
    // Agent prompt: oc-<step>-<ts>-<hash>.log.prompt
    const p = f.match(/^oc-(.+)-(\d+)-([a-z0-9]+)\.log\.prompt$/);
    if (p) {
      try {
        const stat = statSync(join(runDir, f));
        result.push({ step: p[1], fileName: f, sizeBytes: stat.size, type: "prompt" });
      } catch {}
    }
  }
  return result.sort((a, b) => a.step.localeCompare(b.step) || a.type.localeCompare(b.type));
}

function findLatestRunForMission(projectRoot, missionName) {
  const dirs = listMissionRunDirs(projectRoot);
  for (const d of dirs) {
    const runDir = resolve(projectRoot, "_tmp", d);
    const state = readRunState(runDir);
    if (state && state.missionName === missionName) {
      return { status: state.status, runId: state.runId || d };
    }
    const events = readEventsArray(runDir);
    const match = events.find((e) => e.missionName === missionName);
    if (match) {
      const completed = events.find((e) => e.type === "run_completed");
      return { status: completed ? completed.status || "completed" : "running", runId: match.runId || d };
    }
  }
  return null;
}

// ── REST endpoint handlers (FSD §5.2) ─────────────────────────────────────

// GET /api/runs
function handleListRuns(projectRoot, query) {
  const limit = Math.min(100, Math.max(1, parseInt32(query.get("limit"), 20)));
  const offset = Math.max(0, parseInt32(query.get("offset"), 0));
  const dirs = listMissionRunDirs(projectRoot);
  const total = dirs.length;
  const page = dirs.slice(offset, offset + limit);
  const runs = [];
  // One getAllProcesses() snapshot per request, shared by all "running" runs on
  // this page (FSD §4.2 R3). Fetched lazily so a page with no running run pays
  // zero process-enumeration cost.
  let processes = null;
  for (const d of page) {
    const runDir = resolve(projectRoot, "_tmp", d);
    let state = readRunState(runDir);
    if (!state) {
      const events = readEventsArray(runDir);
      state = synthesizeFromEvents(runDir, d, events);
    }
    // Lazy reconciliation: a "running" run whose owner process died is flipped
    // to "aborted" on disk so the dashboard self-heals without a re-run.
    if (state && state.status === "running") {
      if (processes === null) processes = getAllProcesses();
      state = lazyReconcileRun(runDir, state, processes);
    }
    const summary = summarizeRun(state, runDir, d);
    // R1: legacy run-state.json / events without flowName fall back to the
    // mission config so older runs still surface their flow identity. Only
    // consulted when flowName is null (new runs already carry it), avoiding a
    // per-run config read in the common case.
    if (summary.flowName == null && state && state.missionName) {
      const cfg = readMissionConfig(projectRoot, state.missionName);
      if (cfg && cfg.flowName) summary.flowName = cfg.flowName;
    }
    runs.push(summary);
  }
  // total = all mission run dirs; hasMore lets the UI show a "load more" affordance
  // for older runs (offset-based paging over the reverse-sorted dir list).
  return { runs, total, offset, limit, hasMore: offset + page.length < total };
}

// Read a whitelisted mission config for display (FIX-1).
// Returns null when the mission file is missing or unparseable (graceful degrade).
function readMissionConfig(projectRoot, missionName) {
  if (!missionName) return null;
  const safeName = basename(missionName);
  const missionFile = resolve(projectRoot, "missions", `${safeName}.json`);
  let mission;
  try {
    mission = JSON.parse(readFileSync(missionFile, "utf8"));
  } catch {
    return null;
  }
  if (!mission || typeof mission !== "object") return null;
  // Whitelist only display-relevant fields; never leak runtime `workflow` or
  // internal `prompts` (FSD §3.1 security note).
  const cmds = mission.commands && typeof mission.commands === "object" ? mission.commands : {};
  return {
    name: mission.name || safeName,
    description: mission.description || "",
    roadmapPath: mission.roadmapPath || null,
    plansDir: mission.plansDir || null,
    planGuide: mission.planGuide || null,
    moduleDir: mission.moduleDir || null,
    flowName: mission.flowName || null,
    auditsDir: mission.auditsDir || null,
    contextDir: mission.contextDir || null,
    commands: {
      test: cmds.test || null,
      build: cmds.build || null,
      lint: cmds.lint || null,
      typecheck: cmds.typecheck || null,
    },
    commitFormat: mission.commitFormat || null,
  };
}

// GET /api/runs/:runId
function handleGetRun(projectRoot, runId) {
  const runDir = findRunDir(projectRoot, runId);
  if (!runDir) return null;
  let state = readRunState(runDir);
  const events = readEventsArray(runDir);
  if (!state) {
    state = synthesizeFromEvents(runDir, runId, events);
  }
  // Lazy reconciliation (single-run variant of handleListRuns, FSD §4.2 R3).
  if (state && state.status === "running") {
    state = lazyReconcileRun(runDir, state, getAllProcesses());
  }
  if (state && Array.isArray(state.steps)) {
    mergeSubflowChildren(runDir, state.steps);
  }
  const stepLogs = listStepLogs(runDir);
  const config = readMissionConfig(projectRoot, state && state.missionName);
  return { run: state, events, stepLogs, config };
}

// GET /api/runs/:runId/logs/:step
function handleGetLog(projectRoot, runId, step, query) {
  const runDir = findRunDir(projectRoot, runId);
  if (!runDir) return null;
  const safeStep = step.replace(/[^a-zA-Z0-9_-]/g, "");
  if (!safeStep) return { notFound: true };

  const tail = Math.max(1, parseInt32(query.get("tail"), 500));
  const offset = Math.max(0, parseInt32(query.get("offset"), 0));
  const type = query.get("type") || "log";
  const isPrompt = type === "prompt";

  function sliceFile(fileName) {
    const filePath = join(runDir, fileName);
    const content = readFileSync(filePath, "utf8");
    const allLines = content.split("\n");
    const totalLines = allLines.length;
    const endIdx = Math.max(0, totalLines - offset);
    const startIdx = Math.max(0, endIdx - tail);
    return {
      step: safeStep,
      fileName,
      filePath,
      totalLines,
      lines: allLines.slice(startIdx, endIdx),
      truncated: startIdx > 0,
    };
  }

  // When a specific file is requested (e.g. a particular EXECUTE visit's log),
  // return that file directly instead of searching by step-name prefix. The
  // frontend historically passed run-state's absolute `logFile` here; basename()
  // it so a path like C:\...\oc-CHECK-123-abc.log resolves to the bare filename
  // (the old code stripped the separators and produced a non-oc- name → 404).
  // On any validation miss, FALL THROUGH to the step-name prefix search instead
  // of hard-returning notFound (previously the mere presence of a `file` param
  // permanently disabled the prefix fallback).
  const fileParam = query.get("file");
  if (fileParam) {
    const safeFile = basename(fileParam).replace(/[^a-zA-Z0-9_\-\.]/g, "");
    const searchFile = isPrompt ? safeFile + ".prompt" : safeFile;
    const filePath = join(runDir, searchFile);
    if (safeFile.startsWith("oc-") && safeFile.endsWith(".log") && existsSync(filePath)) {
      return sliceFile(searchFile);
    }
    // else: fall through to prefix search below
  }

  const prefix = `oc-${safeStep}-`;
  const suffix = isPrompt ? ".log.prompt" : ".log";
  let files;
  try {
    files = readdirSync(runDir);
  } catch {
    return { notFound: true };
  }
  const matches = files
    .filter((f) => f.startsWith(prefix) && f.endsWith(suffix))
    .map((f) => {
      try {
        return { name: f, mtime: statSync(join(runDir, f)).mtimeMs };
      } catch {
        return { name: f, mtime: 0 };
      }
    })
    .sort((a, b) => b.mtime - a.mtime);
  if (matches.length === 0) return { notFound: true };
  return sliceFile(matches[0].name);
}

// GET /api/runs/:runId/sysmon
function handleSysmon(projectRoot, runId, query) {
  const runDir = findRunDir(projectRoot, runId);
  if (!runDir) return null;
  const limit = Math.min(10000, Math.max(1, parseInt32(query.get("limit"), 100)));
  const file = join(runDir, "sys-snapshot.log");
  if (!existsSync(file)) return { snapshots: [] };

  // Read run state for pid and status (OPT-3 process-tree filtering).
  const state = readRunState(runDir);
  const status = state ? state.status : null;
  const rootPid = state && typeof state.pid === "number" ? state.pid : null;
  const shouldEmptyProcs = status === "completed" || status === "aborted" || rootPid == null;

  // Build mission process-tree pid set for a running mission with a known pid.
  let missionPids = null;
  if (!shouldEmptyProcs && rootPid != null) {
    const allProcs = getAllProcesses();
    const descendants = getDescendants(rootPid, allProcs);
    missionPids = new Set([rootPid, ...descendants.map((p) => p.pid)]);
  }

  const snapshots = [];
  try {
    const content = readFileSync(file, "utf8");
    for (const line of content.split("\n")) {
      if (!line.trim()) continue;
      try {
        const snap = JSON.parse(line);
        let topProcs = Array.isArray(snap.topProcs) ? snap.topProcs.slice(0, 8) : [];
        if (shouldEmptyProcs) {
          topProcs = [];
        } else if (missionPids) {
          topProcs = topProcs.filter((p) => missionPids.has(p.pid));
        }
        snapshots.push({
          ts: snap.ts || null,
          label: snap.label || "",
          freeGB: snap.vm ? snap.vm.free_GB ?? null : null,
          totalRSS_GB: snap.totalRSS_GB ?? null,
          opencodeRSS_MB: snap.cohorts && snap.cohorts.opencode ? snap.cohorts.opencode.rss_mb ?? null : null,
          opencodeCount: snap.cohorts && snap.cohorts.opencode ? snap.cohorts.opencode.count ?? null : null,
          nodeRSS_MB: snap.cohorts && snap.cohorts.node ? snap.cohorts.node.rss_mb ?? null : null,
          nodeCount: snap.cohorts && snap.cohorts.node ? snap.cohorts.node.count ?? null : null,
          processCount: snap.processCount ?? null,
          memPressure: snap.memPressure ?? null,
          topProcs,
        });
      } catch {}
    }
  } catch {}
  return { snapshots: snapshots.slice(-limit) };
}

// GET /api/configs
// Supports offset-based paging (limit default 9, max 100; offset default 0).
// Sort: configs with a lastRunId rank first, then by `name` ascending (FSD §3.4.3).
// The `configs` array always carries the current page slice (back-compat: callers
// that only read `configs` keep working because the page is a valid subset).
function handleListConfigs(projectRoot, query) {
  const get = (key) => (query && typeof query.get === "function" ? query.get(key) : undefined);
  const limit = Math.min(100, Math.max(1, parseInt32(get("limit"), 9)));
  const offset = Math.max(0, parseInt32(get("offset"), 0));
  const missionsDir = resolve(projectRoot, "missions");
  if (!existsSync(missionsDir)) return { configs: [], total: 0, offset, limit, hasMore: false };
  let files;
  try {
    files = readdirSync(missionsDir).filter((f) => f.endsWith(".json"));
  } catch {
    return { configs: [], total: 0, offset, limit, hasMore: false };
  }
  const all = [];
  for (const f of files) {
    try {
      const mission = JSON.parse(readFileSync(join(missionsDir, f), "utf8"));
      // Skip non-mission files: base configs and local overrides lack `roadmapPath`.
      if (!mission.roadmapPath) continue;
      const lastRun = findLatestRunForMission(projectRoot, mission.name);
      all.push({
        name: mission.name || f.replace(".json", ""),
        description: mission.description || "",
        roadmapPath: mission.roadmapPath || "",
        moduleDir: mission.moduleDir || "",
        flowName: mission.flowName || null,
        lastRunStatus: lastRun ? lastRun.status : null,
        lastRunId: lastRun ? lastRun.runId : null,
        _mtime: statSync(join(missionsDir, f)).mtimeMs,
      });
    } catch {}
  }
  // Sort: by mtime descending (newest first).
  all.sort((a, b) => (b._mtime || 0) - (a._mtime || 0));
  const total = all.length;
  const page = all.slice(offset, offset + limit).map(({ _mtime, ...rest }) => rest);
  return { configs: page, total, offset, limit, hasMore: offset + page.length < total };
}

// GET /api/configs/base — return merged base.json + base.local.json.
function handleGetBaseConfig(projectRoot) {
  const missionsDir = resolve(projectRoot, "missions");
  const baseFile = join(missionsDir, "base.json");
  if (!existsSync(baseFile)) return { config: null, error: "base.json not found" };
  let base;
  try {
    base = JSON.parse(readFileSync(baseFile, "utf8"));
  } catch (e) {
    return { config: null, error: `base.json parse error: ${e.message}` };
  }
  const localFile = join(missionsDir, "base.local.json");
  let local = {};
  if (existsSync(localFile)) {
    try {
      local = JSON.parse(readFileSync(localFile, "utf8"));
    } catch {
      // local file parse failure is non-fatal
    }
  }
  const stripMeta = (obj) => Object.fromEntries(Object.entries(obj).filter(([k]) => !k.startsWith("_")));
  const merged = { ...stripMeta(base), ...stripMeta(local) };
  return { config: merged, sources: existsSync(localFile) ? ["base.json", "base.local.json"] : ["base.json"] };
}

// GET /api/configs/:name/roadmap
// parseRoadmapMarkdown is imported from ./roadmap-check.mjs (shared with the
// FlowEngine) and re-exported at the top of this file.

function handleGetRoadmap(projectRoot, name) {
  const safeName = basename(name);
  const missionFile = resolve(projectRoot, "missions", `${safeName}.json`);
  let roadmapPath = null;
  try {
    const mission = JSON.parse(readFileSync(missionFile, "utf8"));
    roadmapPath = mission.roadmapPath || null;
  } catch {}
  if (!roadmapPath) {
    return { roadmapPath: null, phases: [], overallProgress: 0 };
  }
  const absPath = resolve(projectRoot, roadmapPath);
  if (!existsSync(absPath)) {
    return { roadmapPath, phases: [], overallProgress: 0 };
  }
  try {
    const content = readFileSync(absPath, "utf8");
    return { roadmapPath, ...parseRoadmapMarkdown(content) };
  } catch {
    return { roadmapPath, phases: [], overallProgress: 0 };
  }
}

// GET /api/configs/:name/plans  (FIX-2)
// Lists non-index plan files under the mission's plansDir with their status,
// reusing PLAN_STATUS_RE from plan-check.mjs to avoid regex drift (FSD §3.2).
function handleListPlans(projectRoot, name) {
  const safeName = basename(name);
  const mission = readMissionConfig(projectRoot, safeName);
  if (!mission || !mission.plansDir) return { plans: [], plansDir: null };
  const plansDir = resolve(projectRoot, mission.plansDir);
  if (!existsSync(plansDir)) return { plans: [], plansDir: mission.plansDir };
  let files;
  try {
    files = readdirSync(plansDir);
  } catch {
    return { plans: [], plansDir: mission.plansDir };
  }
  const plans = files
    .filter((f) => f.endsWith(".md") && !f.startsWith("00-"))
    .map((f) => {
      const filePath = join(plansDir, f);
      let status = "unknown";
      try {
        const content = readFileSync(filePath, "utf8");
        const m = content.match(PLAN_STATUS_RE);
        if (m) status = m[1].trim().toLowerCase();
      } catch {}
      let st;
      try {
        st = statSync(filePath);
      } catch {
        st = { size: 0, mtimeMs: 0 };
      }
      return { fileName: f, status, sizeBytes: st.size, lastModified: st.mtimeMs };
    })
    .sort((a, b) => b.lastModified - a.lastModified);
  return { plans, plansDir: mission.plansDir };
}

// ── Scenario / node-detail endpoints ─────────────────────────────────
// Read-only scenario definition (flow JSON → nodes + edges), node detail
// (run-state.json step + step-log tail), and scenario config read/write.
// Zero new npm dependencies: pure flow-JSON parse + run-state read + fs write.

// GET /api/scenarios/:flowName — derive a frontend DAG structure from a flow
// JSON. Nodes come from `steps` (stepName, type, scriptId/promptPath); edges
// from `transitions.*.goto` / `done` as {from, to, marker} (FSD §9.3). The
// entry step is flagged so the frontend can highlight the start node.
function handleGetScenario(flowName) {
  const safeName = basename(flowName);
  if (!safeName || safeName.includes("..")) {
    return { error: "invalid flow name", flowName };
  }
  const flowFile = resolve(TOOL_FLOWS_DIR, `${safeName}.json`);
  if (!existsSync(flowFile)) {
    return { notFound: true };
  }
  let flow;
  try {
    flow = JSON.parse(readFileSync(flowFile, "utf8"));
  } catch (e) {
    return { error: `flow parse error: ${e.message}`, flowName: safeName };
  }
  const steps = (flow.steps && typeof flow.steps === "object") ? flow.steps : {};
  const entry = flow.entry || null;
  const nodes = [];
  const edges = [];
  for (const [stepName, stepDef] of Object.entries(steps)) {
    const sd = stepDef && typeof stepDef === "object" ? stepDef : {};
    nodes.push({
      stepName,
      type: sd.type || null,
      scriptId: sd.scriptId || null,
      promptPath: sd.promptPath || null,
      isEntry: stepName === entry,
    });
    const transitions = sd.transitions && typeof sd.transitions === "object" ? sd.transitions : {};
    for (const [marker, trans] of Object.entries(transitions)) {
      const t = trans && typeof trans === "object" ? trans : {};
      if (t.goto) {
        edges.push({ from: stepName, to: t.goto, marker });
      } else if (t.done) {
        // Terminal transition (e.g. ASSERT_SETTLE pass → done:completed).
        edges.push({ from: stepName, to: `done:${t.done}`, marker, terminal: true });
      }
    }
  }
  return { flowName: safeName, entry, nodes, edges };
}

// GET /api/runs/:runId/nodes/:step — aggregate a node's runtime detail from
// run-state.json (step record) + the matching step-log file (tail). The script
// step's `text` output lands in oc-{STEP}-*.log, so logTail surfaces the
// input/response/assertion narrative (FSD §9.2 step 4 / NFR-7).
function handleGetNodeDetail(projectRoot, runId, step) {
  const runDir = findRunDir(projectRoot, runId);
  if (!runDir) return null;
  const safeStep = step.replace(/[^a-zA-Z0-9_-]/g, "");
  if (!safeStep) return { notFound: true };

  const state = readRunState(runDir);
  let stepRec = null;
  if (state && Array.isArray(state.steps)) {
    // Pick the latest visit for this step name.
    const matches = state.steps.filter((s) => s && s.name === safeStep);
    if (matches.length > 0) stepRec = matches[matches.length - 1];
  }

  // Find the newest matching step-log file and read its tail.
  let logFile = null;
  let logTail = null;
  let logSizeBytes = null;
  try {
    const files = readdirSync(runDir)
      .filter((f) => f.startsWith(`oc-${safeStep}-`) && f.endsWith(".log"))
      .map((f) => {
        try {
          return { name: f, mtime: statSync(join(runDir, f)).mtimeMs };
        } catch {
          return { name: f, mtime: 0 };
        }
      })
      .sort((a, b) => b.mtime - a.mtime);
    if (files.length > 0) {
      logFile = files[0].name;
      try {
        const content = readFileSync(join(runDir, logFile), "utf8");
        logSizeBytes = content.length;
        const tailLines = content.split("\n");
        logTail = tailLines.slice(Math.max(0, tailLines.length - 200)).join("\n");
      } catch {}
    }
  } catch {}

  // `vars`/`text` are not persisted per-step in run-state.json (script vars
  // merge into in-memory flowVars; `text` is the log). Surface them when a
  // future/persisted record carries them, else null (graceful degrade).
  return {
    stepName: safeStep,
    status: stepRec ? stepRec.status || null : null,
    marker: stepRec ? stepRec.marker ?? null : null,
    visits: stepRec ? stepRec.visits ?? null : null,
    startedAt: stepRec ? stepRec.startedAt || null : null,
    endedAt: stepRec ? stepRec.endedAt || null : null,
    durationMs: stepRec ? stepRec.durationMs ?? null : null,
    sessionId: stepRec ? stepRec.sessionId || null : null,
    produced: stepRec && Array.isArray(stepRec.produced) ? stepRec.produced : [],
    vars: stepRec && stepRec.vars ? stepRec.vars : null,
    text: stepRec && stepRec.text ? stepRec.text : null,
    logFile,
    logSizeBytes,
    logTail,
  };
}

// ── Context Explorer endpoints (P6 / FSD §3.5) ────────────────────────────
//
// Five GET handlers delegating to context-map.mjs static analysis. All are
// read-only and basename-clean the single path parameter to prevent traversal.
// The Memory GET/PUT store+file handlers (Phase 3) live further below with
// their own whitelist gate.

// GET /api/flows/:name/injection-map — per-step {{var}} provenance for a flow.
// Scans both missions/flows + the tool flows dir (closes the handleGetScenario
// gap which only scanned tool flows).
function handleGetInjectionMap(projectRoot, flowName) {
  const safe = basename(flowName);
  if (!safe || safe.includes("..")) {
    return { error: "invalid flow name", status: 400 };
  }
  const result = buildInjectionMap(safe, projectRoot);
  if (result.notFound) return { notFound: true };
  return result;
}

// GET /api/prompts — prompt library with reverse used-by index.
function handleListPrompts(projectRoot) {
  return listPrompts(projectRoot);
}

// GET /api/prompts/:name — full prompt text (basename-cleaned, prompts-dir
// whitelist only). 404 when the prompt is absent from every search dir.
// Tolerant on input: accepts "execute", "execute.md", or "prompts/execute.md"
// (listPrompts returns names WITHOUT the .md extension, so the gate must not
// require it — otherwise every Prompt Library click returns an error).
function handleGetPrompt(projectRoot, name) {
  const safe = basename(name);
  if (!safe || safe.includes("..")) {
    return { error: "invalid prompt name", status: 400 };
  }
  // Strip an optional .md extension so both "execute" and "execute.md" resolve.
  const base = safe.endsWith(".md") ? basename(safe, ".md") : safe;
  if (!base) return { error: "invalid prompt name", status: 400 };
  for (const dir of [resolve(projectRoot, "missions", "prompts"), TOOL_PROMPTS_DIR]) {
    const f = resolve(dir, `${base}.md`);
    if (existsSync(f)) {
      try {
        return { name: base, content: readFileSync(f, "utf8") };
      } catch (e) {
        return { error: `read failed: ${e.message}`, status: 500 };
      }
    }
  }
  return { notFound: true };
}

// GET /api/memory — self + per-module memory store inventory.
function handleListMemoryStores(projectRoot) {
  return listMemoryStores(projectRoot);
}

// ── Memory file GET/PUT (P6 Phase 3 / FSD §3.5.2 panel 2) ──────────────────
//
// store + file double-whitelist gate prevents path traversal and restricts
// writes to the known memory file set. PUT uses atomic rename (tmp → target)
// so an interrupted write never corrupts the original file.

const MEMORY_STORE_DIRS = (projectRoot) => {
  const dirs = {
    self: SELF_MEMORY_DIR,
    "mission-driver": SELF_MEMORY_DIR,
  };
  const docsMemoryDir = resolve(projectRoot, "docs", "memory");
  try {
    for (const e of readdirSync(docsMemoryDir, { withFileTypes: true })) {
      if (e.isDirectory()) dirs[e.name] = resolve(docsMemoryDir, e.name);
    }
  } catch {}
  return dirs;
};
const MEMORY_FILE_WHITELIST = new Set(["_index.md", "lessons.md", "runs.md", "README.md"]);

// Resolve (store, file) to an absolute path inside the store dir, or null when
// store/file is unknown or escapes the dir. The basename + isPathWithin pair
// is the same traversal defense used elsewhere.
function resolveMemoryPath(projectRoot, store, file) {
  const dirs = MEMORY_STORE_DIRS(projectRoot);
  const dir = dirs[store];
  if (!dir) return { error: "unknown store", status: 404 };
  const safeFile = basename(file);
  if (!safeFile || safeFile !== file || safeFile.includes("..")) {
    return { error: "invalid file name", status: 400 };
  }
  if (!MEMORY_FILE_WHITELIST.has(safeFile)) {
    return { error: `file not in whitelist: ${[...MEMORY_FILE_WHITELIST].join(", ")}`, status: 400 };
  }
  const abs = resolve(dir, safeFile);
  if (!isPathWithin(abs, dir)) {
    return { error: "file escapes store directory", status: 400 };
  }
  return { path: abs, dir };
}

// Best-effort: detect a running mission so the PUT can emit a non-blocking
// warning (editing memory live mid-run is allowed but risky). Scans _tmp for
// run-state.json files with status === "running".
function detectRunningMission(projectRoot) {
  const tmpDir = resolve(projectRoot, "_tmp");
  let entries;
  try {
    entries = readdirSync(tmpDir, { withFileTypes: true });
  } catch {
    return false;
  }
  for (const e of entries) {
    if (!e.isDirectory()) continue;
    const stateFile = join(tmpDir, e.name, "run-state.json");
    if (!existsSync(stateFile)) continue;
    try {
      const state = JSON.parse(readFileSync(stateFile, "utf8"));
      if (state && state.status === "running") return true;
    } catch {
      continue;
    }
  }
  return false;
}

// GET /api/memory/:store/:file — raw memory file text.
function handleGetMemoryFile(projectRoot, store, file) {
  const resolved = resolveMemoryPath(projectRoot, store, file);
  if (resolved.error) return { error: resolved.error, status: resolved.status };
  if (!existsSync(resolved.path)) return { notFound: true };
  try {
    return { content: readFileSync(resolved.path, "utf8") };
  } catch (e) {
    return { error: `read failed: ${e.message}`, status: 500 };
  }
}

// PUT /api/memory/:store/:file — atomic overwrite via tmp + rename.
// Body: { content: string }. Returns { ok: true, warning?: string }.
function handlePutMemoryFile(projectRoot, store, file, body) {
  const resolved = resolveMemoryPath(projectRoot, store, file);
  if (resolved.error) return { error: resolved.error, status: resolved.status };
  if (!body || typeof body !== "object" || typeof body.content !== "string") {
    return { error: "body.content must be a non-empty string", status: 400 };
  }
  if (!body.content.trim()) {
    return { error: "content must not be empty", status: 400 };
  }
  if (!existsSync(resolved.dir)) {
    return { error: "store directory not found", status: 404 };
  }
  const target = resolved.path;
  const tmp = target + ".tmp";
  try {
    writeFileSync(tmp, body.content, "utf8");
    renameSync(tmp, target);
  } catch (e) {
    // Best-effort cleanup of a half-written tmp file.
    try { if (existsSync(tmp)) rmSync(tmp, { force: true }); } catch {}
    return { error: `write failed: ${e.message}`, status: 500 };
  }
  const result = { ok: true };
  if (detectRunningMission(projectRoot)) {
    result.warning = "A mission is currently running; memory edits take effect on the next step injection.";
  }
  return result;
}

// POST /api/runs — controlled launch of a whitelisted mission (itp2-5 / FSD §6).
// Writes any UI-injected targets to {runDir}/input-targets.json (read by the
// LOAD_TARGETS step's override path), then spawns the engine as a detached
// child. Returns { runId, missionName }.
//
// Security (FSD §8 R2):
//   - Whitelist gate: readMissionConfig must return non-null AND config.roadmapPath
//     must be non-empty. readMissionConfig does NOT filter by roadmapPath (base.json
//     and other non-runnable configs also return non-null), so the explicit check
//     prevents `POST { missionName: "base" }` from spawning an unrunnable mission.
//   - Mission name is basename()-sanitised (same as readMissionConfig:399).
//   - Targets are written to a FILE (never argv/shell).
//   - spawn uses process.execPath + args array + shell:false (executor.js:200-214
//     injection-safety pattern) ⇒ no user input ever reaches a shell string.
function handleStartRun(projectRoot, body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return { error: "request body must be a JSON object", status: 400 };
  }
  const config = readMissionConfig(projectRoot, body.missionName);
  if (!config || !config.roadmapPath) {
    return { error: "unknown or non-runnable mission", status: 400 };
  }
  const safeMissionName = basename(body.missionName);

  // Generate runDir with the same timestamp format as config.js:414-420
  // (runId = the runDir basename; the --dir flag controls the engine's runId).
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const ts =
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}-` +
    `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const runDir = resolve(projectRoot, "_tmp", `${ts}-mission-driver`);
  mkdirSync(runDir, { recursive: true });

  // Optional UI-injected targets → {runDir}/input-targets.json (LOAD_TARGETS override).
  if (body.targets != null) {
    if (!Array.isArray(body.targets)) {
      return { error: "targets must be an array", status: 400 };
    }
    for (const t of body.targets) {
      if (!t || typeof t !== "object" || (!t.key && !t.scenario)) {
        return { error: "each target must have a key or scenario", status: 400 };
      }
    }
    writeFileSync(
      resolve(runDir, "input-targets.json"),
      JSON.stringify({ targets: body.targets }, null, 2),
    );
  }

  // Spawn the engine in a detached, unref'd child so it outlives the monitor
  // process. process.execPath = current node binary (no PATH reliance); stdio
  // is ignored because the engine writes its own run-state/events/logs.
  // --dir = project root (to find missions/); --run-dir = the pre-created run
  // directory (so the engine writes run-state.json to the same dir the frontend
  // navigates to).
  const mainJsPath = resolve(__dirname, "main.js");
  const runDirRel = basename(runDir);
  const child = getSpawner()(
    process.execPath,
    [mainJsPath, safeMissionName, "--dir", projectRoot, "--run-dir", runDirRel],
    { shell: false, detached: true, stdio: "ignore", windowsHide: true },
  );
  child.unref();

  return { runId: basename(runDir), missionName: safeMissionName };
}

// ── Mission Draft endpoints (mdo-2 Phase 2) ───────────────────────────────
//
// POST /api/missions/draft        — start an async draft job (returns {jobId})
// GET  /api/missions/draft        — list recent draft jobs
// GET  /api/missions/draft/:jobId — read a draft job's state + log tail
//
// Spawn safety mirrors handleStartRun: the draft child runs detached with
// shell:false + args array (FSD §8 R2). jobId is basename-cleaned on read so
// `..` traversal is neutralised before any filesystem read.

// Maximum desc length accepted by POST /api/missions/draft (FSD §3.1.3: ≤2KB).
const DRAFT_DESC_MAX_BYTES = 2 * 1024;
// mdo-4 P2: flowHint whitelist (word chars + hyphens only, ≤128 chars).
const FLOW_HINT_RE = /^[\w-]{1,128}$/;
// mdo-4 P2: directories skipped by the controlled file browser (GET /api/browse).
const BROWSE_SKIP_DIRS = new Set(["node_modules", "_tmp", ".git", "dist", "target", "."]);
// mdo-4 P2: cap on entries returned by GET /api/browse (avoids huge directory dumps).
const BROWSE_MAX_ENTRIES = 200;

export function handleStartDraft(projectRoot, body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return { error: "request body must be a JSON object", status: 400 };
  }
  const desc = body.desc;
  if (typeof desc !== "string" || !desc.trim()) {
    return { error: "desc must be a non-empty string", status: 400 };
  }
  if (Buffer.byteLength(desc, "utf8") > DRAFT_DESC_MAX_BYTES) {
    return { error: `desc must be ≤ ${DRAFT_DESC_MAX_BYTES} bytes`, status: 400 };
  }

  // N2 (mdr-remediate-2): upstream pre-validation mirroring cmdDraftMission's
  // WI1 gate (design §4.1). Reads base.json's draft.minDescLength with the
  // same pattern as src/main.js:344-348 so the dashboard's POST /api/draft
  // path rejects placeholder/over-short desc BEFORE any jobDir creation /
  // state write / spawn. validateDraftDesc is the single source of truth
  // for the WI1 contract; this closes the "defense in depth on both ends"
  // pair (F2 downstream terminal-state write + N2 upstream monitor gate).
  let baseConfig = {};
  try {
    baseConfig = JSON.parse(readFileSync(resolve(projectRoot, "missions", "base.json"), "utf8"));
  } catch { baseConfig = {}; }
  const v = validateDraftDesc(desc, baseConfig?.draft?.minDescLength);
  if (!v.ok) {
    return { error: v.reason, status: 400 };
  }

  // mdo-4 P2: optional flowHint / targetFile / skipBrief pass-through.
  let flowHint = null;
  if (body.flowHint != null) {
    if (typeof body.flowHint !== "string" || !FLOW_HINT_RE.test(body.flowHint)) {
      return { error: "flowHint must match ^[\\w-]{1,128}$", status: 400 };
    }
    flowHint = body.flowHint;
  }

  let targetFile = null;
  if (body.targetFile != null) {
    if (typeof body.targetFile !== "string" || !body.targetFile.trim()) {
      return { error: "targetFile must be a non-empty string", status: 400 };
    }
    // Resolve against projectRoot and verify it stays within (reject ../ traversal).
    // Multi-segment relative paths are preserved (basename is NOT stripped) so the
    // wizard can target e.g. "docs/backlog/x.md".
    const resolved = resolve(projectRoot, body.targetFile);
    if (!isPathWithin(resolved, projectRoot)) {
      return { error: "targetFile must stay within projectRoot", status: 400 };
    }
    targetFile = body.targetFile;
  }

  const skipBrief = body.skipBrief === true;

  // startDraftJob reuses the shared spawner seam (spawner.mjs, set via the
  // monitor-re-exported __setSpawnerForTest) so tests inject one fake spawner
  // that covers BOTH run-launch and draft-launch — no real `node main.js draft`
  // is launched in CI.
  const result = startDraftJob({ projectRoot, desc, flowHint, targetFile, skipBrief });
  return { jobId: result.jobId, pid: result.pid };
}

function handleGetDraft(projectRoot, jobId) {
  // Reject any jobId whose decoded form contains path separators or dot-dirs.
  // basename alone would neutralise traversal
  // into a benign 404, but an explicit 400 makes the contract intentional.
  const raw = String(jobId || "");
  if (!raw || raw === "." || raw === ".." || /[\\/]/.test(raw) || raw.includes("..")) {
    return { forbidden: true };
  }
  const safe = basename(raw);
  if (!safe) return { forbidden: true };
  const result = readDraftJob(projectRoot, safe);
  if (!result.state) return { notFound: true };
  return result;
}

function handleListDrafts(projectRoot) {
  return listDraftJobs(projectRoot);
}

// ── Flow + Browse endpoints (mdo-4 P2) ────────────────────────────────────

/**
 * Scan missions/flows/*.json + the tool's built-in flows/*.json and return a
 * de-duplicated list of TOP-LEVEL flows for the wizard's Flow dropdown. Each
 * entry: { name, entry, stepCount }.
 *
 * Subflows (any flow referenced via a `subflow` step's `flow` field) are
 * excluded — they have no standalone entry semantics and are only invoked by
 * parent flows, so selecting one as a new mission's flowName would run a broken
 * context-less loop. Project flows override tool flows by name (same precedence
 * as flow-loader.js). No `kind` tag is emitted: a flow defines itself by its
 * name + steps; a dev/test label was heuristic noise.
 */
function handleListFlows(projectRoot) {
  const projectFlowsDir = resolve(projectRoot, "missions", "flows");

  // Pass 1: load every flow file (project flows win on name collisions).
  const byName = new Map(); // name → parsed flow data
  function scanDir(dir) {
    let names;
    try {
      names = readdirSync(dir);
    } catch {
      return;
    }
    for (const fname of names) {
      if (!fname.endsWith(".json")) continue;
      const name = fname.slice(0, -5);
      if (byName.has(name)) continue; // project flow wins (scanned first)
      let data;
      try {
        data = JSON.parse(readFileSync(join(dir, fname), "utf8"));
      } catch {
        continue;
      }
      byName.set(name, data);
    }
  }
  scanDir(projectFlowsDir);
  scanDir(TOOL_FLOWS_DIR);

  // Pass 2: derive the subflow name set from every flow's `subflow` steps.
  const subflowNames = new Set();
  for (const data of byName.values()) {
    const steps = data && data.steps && typeof data.steps === "object" ? data.steps : {};
    for (const stepDef of Object.values(steps)) {
      const sd = stepDef && typeof stepDef === "object" ? stepDef : {};
      if (sd.type === "subflow" && typeof sd.flow === "string") subflowNames.add(sd.flow);
    }
  }

  // Pass 3: emit only non-subflow (top-level runnable) flows.
  const flows = [];
  for (const [name, data] of byName) {
    if (subflowNames.has(name)) continue;
    const steps = data.steps && typeof data.steps === "object" ? data.steps : {};
    flows.push({ name, entry: data.entry || null, stepCount: Object.keys(steps).length });
  }
  return { flows };
}

/**
 * Controlled file browser for the wizard's "select target file" mode (mdo-4 P2).
 * Lists entries under `prefix` (project-relative, default = projectRoot), skipping
 * build/dependency dirs + hidden files. Returns { entries: [{name,isDir,path}] },
 * capped at BROWSE_MAX_ENTRIES.
 */
function handleBrowse(projectRoot, query) {
  const raw = query && typeof query.get === "function" ? query.get("prefix") : undefined;
  const prefixRaw = typeof raw === "string" && raw.trim() ? raw : ".";
  const absPrefix = resolve(projectRoot, prefixRaw);
  if (!isPathWithin(absPrefix, projectRoot)) {
    return { error: "prefix must stay within projectRoot", status: 400 };
  }
  let names;
  try {
    names = readdirSync(absPrefix);
  } catch {
    return { entries: [] };
  }
  const entries = [];
  for (const name of names) {
    if (name.startsWith(".")) continue; // hidden files
    let isDir = false;
    try {
      isDir = statSync(join(absPrefix, name)).isDirectory();
    } catch {
      continue;
    }
    if (isDir && BROWSE_SKIP_DIRS.has(name)) continue;
    const relPath = relative(projectRoot, join(absPrefix, name)).replace(/\\/g, "/");
    entries.push({ name, isDir, path: relPath });
    if (entries.length >= BROWSE_MAX_ENTRIES) break;
  }
  // Directories first (natural for tree navigation), then files, alpha-sorted.
  entries.sort((a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
    return a.name.localeCompare(b.name);
  });
  return { entries };
}

// ── SSE handler (FSD §5.3) ────────────────────────────────────────────────

function readFromPosition(filePath, fromByte) {
  let fd;
  try {
    fd = openSync(filePath, "r");
    const size = fstatSync(fd).size;
    if (size <= fromByte) return { text: "", size };
    const len = size - fromByte;
    const buf = Buffer.alloc(len);
    readSync(fd, buf, 0, len, fromByte);
    return { text: buf.toString("utf8"), size };
  } catch {
    return { text: "", size: fromByte };
  } finally {
    if (fd !== undefined) {
      try {
        closeSync(fd);
      } catch {}
    }
  }
}

function handleSSE(req, res, projectRoot, runId) {
  const SSE_HEADERS = {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache, no-transform",
    "Connection": "keep-alive",
    "X-Accel-Buffering": "no",
  };
  res.writeHead(200, SSE_HEADERS);

  function sendEvent(eventName, data) {
    try {
      res.write(`event: ${eventName}\ndata: ${JSON.stringify(data)}\n\n`);
    } catch {}
  }

  const runDir = findRunDir(projectRoot, runId);

  if (!runDir) {
    sendEvent("error", { message: "run not found", runId });
    res.end();
    return;
  }

  const stateFile = join(runDir, "run-state.json");
  const eventsFile = join(runDir, "events.jsonl");

  // 1. Snapshot: send current run-state.json
  if (existsSync(stateFile)) {
    try {
      const state = JSON.parse(readFileSync(stateFile, "utf8"));
      sendEvent("snapshot", state);
    } catch {}
  }

  // 2. Replay: send all historical events
  let lastPos = 0;
  if (existsSync(eventsFile)) {
    try {
      const content = readFileSync(eventsFile, "utf8");
      for (const line of content.split("\n")) {
        if (!line.trim()) continue;
        try {
          const ev = JSON.parse(line);
          sendEvent(ev.type || "message", ev);
        } catch {}
      }
      lastPos = statSync(eventsFile).size;
    } catch {}
  }

  // Track state mtime for change detection
  let lastStateMtime = 0;
  try {
    lastStateMtime = statSync(stateFile).mtimeMs;
  } catch {}
  let lastSubMtimes = {};

  // 3. Tail poll: 500ms interval (NFR-3)
  const timer = setInterval(() => {
    // Tail events.jsonl for new lines
    if (existsSync(eventsFile)) {
      const result = readFromPosition(eventsFile, lastPos);
      if (result.text) {
        lastPos = result.size;
        for (const line of result.text.split("\n")) {
          if (!line.trim()) continue;
          try {
            const ev = JSON.parse(line);
            sendEvent(ev.type || "message", ev);
          } catch {}
        }
      } else {
        lastPos = result.size;
      }
    }

    // Check run-state.json + subflow files mtime change → state_update event
    try {
      let changed = false;
      if (existsSync(stateFile)) {
        const mtime = statSync(stateFile).mtimeMs;
        if (mtime !== lastStateMtime) {
          lastStateMtime = mtime;
          changed = true;
        }
      }
      let subFiles = [];
      try { subFiles = readdirSync(runDir).filter(function (f) { return f.startsWith("run-state-") && f.endsWith(".json"); }); } catch {}
      for (const f of subFiles) {
        try {
          const mt = statSync(join(runDir, f)).mtimeMs;
          if (lastSubMtimes[f] !== mt) {
            lastSubMtimes[f] = mt;
            changed = true;
          }
        } catch {}
      }
      if (changed && existsSync(stateFile)) {
        const state = JSON.parse(readFileSync(stateFile, "utf8"));
        if (Array.isArray(state.steps)) mergeSubflowChildren(runDir, state.steps);
        sendEvent("state_update", state);
      }
    } catch {}
  }, 500);

  // Cleanup on client disconnect
  req.on("close", () => {
    clearInterval(timer);
  });
}

// ── Main request handler ──────────────────────────────────────────────────

function handleRequest(req, res, ctx) {
  let url;
  try {
    url = new URL(req.url, "http://localhost");
  } catch {
    sendJson(res, 400, { error: "invalid url" });
    return;
  }
  const pathname = url.pathname;
  const query = url.searchParams;

  // Static: root index
  if ((pathname === "/" || pathname === "/index.html") && req.method === "GET") {
    if (ctx.webDir === null) {
      serveDevHint(res);
      return;
    }
    serveIndex(res, ctx.webDir);
    return;
  }

  // REST API
  if (pathname.startsWith("/api/")) {
    let m;

    if (req.method === "GET" && pathname === "/api/runs") {
      return sendJson(res, 200, handleListRuns(ctx.projectRoot, query));
    }
    if (req.method === "DELETE" && pathname === "/api/runs") {
      return sendJson(res, 405, { error: "use DELETE /api/runs/:runId to delete a specific run" });
    }
    if (req.method === "POST" && pathname === "/api/runs") {
      return readJsonBody(req, (err, body) => {
        if (err) return sendJson(res, 400, { error: `invalid JSON body: ${err.message}` });
        const result = handleStartRun(ctx.projectRoot, body);
        return sendJson(res, result.status || 200, result);
      });
    }

    // Mission Draft endpoints (mdo-2 Phase 2). 3-segment `/api/missions/draft/:jobId`
    // is matched first; the bare 2-segment `/api/missions/draft` is split by method
    // (POST → start, GET → list), mirroring the existing `/api/runs` pattern.
    m = matchRoute(pathname, "/api/missions/draft/:jobId");
    if (req.method === "GET" && m) {
      const result = handleGetDraft(ctx.projectRoot, m.jobId);
      if (result.notFound) return sendJson(res, 404, { error: "draft job not found", jobId: m.jobId });
      if (result.forbidden) return sendJson(res, 400, { error: "invalid jobId", jobId: m.jobId });
      return sendJson(res, 200, result);
    }

    if (req.method === "POST" && pathname === "/api/missions/draft") {
      return readJsonBody(req, (err, body) => {
        if (err) return sendJson(res, 400, { error: `invalid JSON body: ${err.message}` });
        const result = handleStartDraft(ctx.projectRoot, body);
        return sendJson(res, result.status || 200, result);
      });
    }
    if (req.method === "GET" && pathname === "/api/missions/draft") {
      return sendJson(res, 200, handleListDrafts(ctx.projectRoot));
    }
    // mdo-4 P2: Flow dropdown + controlled file browser for the unified wizard.
    if (req.method === "GET" && pathname === "/api/flows") {
      return sendJson(res, 200, handleListFlows(ctx.projectRoot));
    }
    if (req.method === "GET" && pathname === "/api/browse") {
      const result = handleBrowse(ctx.projectRoot, query);
      if (result.status) return sendJson(res, result.status, result);
      return sendJson(res, 200, result);
    }
    if (req.method === "GET" && pathname === "/api/configs") {
      return sendJson(res, 200, handleListConfigs(ctx.projectRoot, query));
    }
    if (req.method === "GET" && pathname === "/api/configs/base") {
      return sendJson(res, 200, handleGetBaseConfig(ctx.projectRoot));
    }
    m = matchRoute(pathname, "/api/runs/:runId");
    if (req.method === "GET" && m) {
      const detail = handleGetRun(ctx.projectRoot, m.runId);
      if (!detail) return sendJson(res, 404, { error: "run not found", runId: m.runId });
      return sendJson(res, 200, detail);
    }
    if (req.method === "DELETE" && m) {
      const runDir = findRunDir(ctx.projectRoot, m.runId);
      if (!runDir) return sendJson(res, 404, { error: "run not found", runId: m.runId });
      try {
        const state = readRunState(runDir);
        if (state && state.status === "running") {
          return sendJson(res, 409, { error: "cannot delete a running mission", runId: m.runId });
        }
        rmSync(runDir, { recursive: true, force: true });
        return sendJson(res, 200, { ok: true, runId: m.runId });
      } catch (err) {
        return sendJson(res, 500, { error: "delete failed", message: err.message });
      }
    }

    m = matchRoute(pathname, "/api/runs/:runId/events");
    if (req.method === "GET" && m) {
      return handleSSE(req, res, ctx.projectRoot, m.runId);
    }

    m = matchRoute(pathname, "/api/runs/:runId/sysmon");
    if (req.method === "GET" && m) {
      const result = handleSysmon(ctx.projectRoot, m.runId, query);
      if (!result) return sendJson(res, 404, { error: "run not found", runId: m.runId });
      return sendJson(res, 200, result);
    }

    m = matchRoute(pathname, "/api/runs/:runId/logs/:step");
    if (req.method === "GET" && m) {
      const result = handleGetLog(ctx.projectRoot, m.runId, m.step, query);
      if (!result) return sendJson(res, 404, { error: "run not found", runId: m.runId });
      if (result.notFound) return sendJson(res, 404, { error: "log not found", step: m.step });
      return sendJson(res, 200, result);
    }

    m = matchRoute(pathname, "/api/configs/:name/roadmap");
    if (req.method === "GET" && m) {
      return sendJson(res, 200, handleGetRoadmap(ctx.projectRoot, m.name));
    }

    m = matchRoute(pathname, "/api/configs/:name/plans");
    if (req.method === "GET" && m) {
      return sendJson(res, 200, handleListPlans(ctx.projectRoot, m.name));
    }

    // Scenario definition (flow JSON → nodes + edges).
    m = matchRoute(pathname, "/api/scenarios/:flowName");
    if (req.method === "GET" && m) {
      const result = handleGetScenario(m.flowName);
      if (result.notFound) return sendJson(res, 404, { error: "scenario flow not found", flowName: m.flowName });
      if (result.error) return sendJson(res, 400, result);
      return sendJson(res, 200, result);
    }

    // Context Explorer — flow injection map (P6 / FSD §3.5).
    m = matchRoute(pathname, "/api/flows/:name/injection-map");
    if (req.method === "GET" && m) {
      const result = handleGetInjectionMap(ctx.projectRoot, m.name);
      if (result.notFound) return sendJson(res, 404, { error: "flow not found", flowName: m.name });
      if (result.status) return sendJson(res, result.status, result);
      return sendJson(res, 200, result);
    }

    // Context Explorer — prompt library + single prompt text (P6 / FSD §3.5).
    m = matchRoute(pathname, "/api/prompts/:name");
    if (req.method === "GET" && m) {
      const result = handleGetPrompt(ctx.projectRoot, m.name);
      if (result.notFound) return sendJson(res, 404, { error: "prompt not found", name: m.name });
      if (result.status) return sendJson(res, result.status, result);
      return sendJson(res, 200, result);
    }
    if (req.method === "GET" && pathname === "/api/prompts") {
      return sendJson(res, 200, handleListPrompts(ctx.projectRoot));
    }

    // Context Explorer — memory store inventory (P6 / FSD §3.5).
    if (req.method === "GET" && pathname === "/api/memory") {
      return sendJson(res, 200, handleListMemoryStores(ctx.projectRoot));
    }

    // Memory file GET/PUT (P6 Phase 3). 3-segment /api/memory/:store/:file.
    m = matchRoute(pathname, "/api/memory/:store/:file");
    if (req.method === "GET" && m) {
      const result = handleGetMemoryFile(ctx.projectRoot, m.store, m.file);
      if (result.notFound) return sendJson(res, 404, { error: "memory file not found", store: m.store, file: m.file });
      if (result.status) return sendJson(res, result.status, result);
      return sendJson(res, 200, result);
    }
    if (req.method === "PUT" && m) {
      return readJsonBody(req, (err, body) => {
        if (err) return sendJson(res, 400, { error: `invalid JSON body: ${err.message}` });
        const result = handlePutMemoryFile(ctx.projectRoot, m.store, m.file, body);
        if (result.status) return sendJson(res, result.status, result);
        return sendJson(res, 200, result);
      });
    }

    // Node detail (run-state step + step-log tail).
    m = matchRoute(pathname, "/api/runs/:runId/nodes/:step");
    if (req.method === "GET" && m) {
      const result = handleGetNodeDetail(ctx.projectRoot, m.runId, m.step);
      if (!result) return sendJson(res, 404, { error: "run not found", runId: m.runId });
      if (result.notFound) return sendJson(res, 404, { error: "invalid step", step: m.step });
      return sendJson(res, 200, result);
    }

    return sendJson(res, 404, { error: "not found", path: pathname });
  }

  // Static assets + SPA fallback
  if (req.method === "GET") {
    const relativePath = pathname.replace(/^\/+/, "");
    if (relativePath) {
      if (ctx.webDir === null) {
        serveDevHint(res);
        return;
      }
      const absPath = join(ctx.webDir, relativePath);
      // Real static asset → serve directly
      if (existsSync(absPath) && statSync(absPath).isFile()) {
        serveStaticFile(res, absPath, ctx.webDir);
        return;
      }
      // SPA fallback: no file extension → client-side route (e.g. /runs/:runId).
      // Serve index.html so Vue Router can handle it on page refresh / deep link.
      if (!extname(relativePath)) {
        serveIndex(res, ctx.webDir);
        return;
      }
      // Has extension but file missing → genuine 404 (broken asset link)
      sendJson(res, 404, { error: "not found", path: pathname });
      return;
    }
  }

  sendJson(res, 404, { error: "not found", path: pathname });
}

// ── Server lifecycle (FSD §5.1) ───────────────────────────────────────────

export function startMonitor({ projectRoot, runDir, missionName, port, webDir }) {
  // webDir === null ⇒ dev mode: no static hosting (vite dev server serves the
  // frontend). A string path ⇒ prod mode: serve webDir/index.html + assets.
  const resolvedWebDir = webDir === undefined ? null : webDir;
  const ctx = {
    projectRoot: projectRoot || process.cwd(),
    runDir,
    missionName,
    webDir: resolvedWebDir,
  };

  // Full reconciliation on startup: sweep stale "running" runs left by prior
  // crashes so the dashboard no longer shows ghost runs (FSD §3.1.4 G3).
  // Best-effort log; never blocks the monitor from starting.
  try {
    const { reconciled } = reconcileStaleRuns(ctx.projectRoot);
    if (reconciled.length > 0) {
      console.warn(
        `[monitor] reconciled ${reconciled.length} stale run(s): ` +
        `${reconciled.map((r) => r.runId).join(", ")}`
      );
    }
  } catch (err) {
    console.warn(`[monitor] startup reconciliation failed: ${err.message}`);
  }

  // Prod-mode startup check: if a webDir was given but its index.html is
  // missing (e.g. web/dist/ not built yet), warn and degrade to API-only
  // (GET / falls back to the placeholder; other static → 404). FSD §8.
  if (ctx.webDir !== null) {
    const indexFile = resolve(ctx.webDir, "index.html");
    if (!existsSync(indexFile) || !statSync(indexFile).isFile()) {
      console.warn(
        `[WARN] ${ctx.webDir} (index.html) not found — serving API-only`
      );
    }
  }

  const connections = new Set();

  const server = createServer((req, res) => {
    handleRequest(req, res, ctx);
  });

  server.on("connection", (socket) => {
    connections.add(socket);
    socket.on("close", () => connections.delete(socket));
  });

  function doClose() {
    return new Promise((r) => {
      for (const sock of connections) {
        try {
          sock.destroy();
        } catch {}
      }
      connections.clear();
      server.close(() => r());
    });
  }

  const basePort = port ?? 9300;

  return new Promise((resolveFn, reject) => {
    // Port 0 = OS-assigned (for tests), single attempt
    if (basePort === 0) {
      server.on("error", reject);
      server.listen(0, () => {
        resolveFn({ server, port: server.address().port, close: doClose });
      });
      return;
    }

    // Fixed port: retry +1 on EADDRINUSE (9300→9319, max 20 attempts, NFR-11)
    // Allows multiple project monitors to run concurrently.
    let attempt = 0;
    const maxAttempts = 20;

    function tryListen() {
      const tryPort = basePort + attempt;
      // Remove listeners from the previous (failed) attempt so stale callbacks
      // don't fire with the wrong port when the next attempt succeeds.
      server.removeAllListeners("error");
      server.removeAllListeners("listening");
      server.once("error", (err) => {
        if (err.code === "EADDRINUSE" && attempt < maxAttempts - 1) {
          attempt++;
          console.log(`[monitor] :${tryPort} in use, trying :${basePort + attempt}...`);
          tryListen();
        } else {
          reject(err);
        }
      });
      server.once("listening", () => {
        resolveFn({ server, port: tryPort, close: doClose });
      });
      server.listen(tryPort);
    }

    tryListen();
  });
}
