import { existsSync, mkdirSync, readdirSync, readFileSync, statSync } from "node:fs";
import { resolve, join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { loadMission } from "./mission-check.mjs";

// Engine root (parent of src/). Locates driver assets (e.g. the pi persona)
// relative to the running engine, so paths resolve for both this repo and
// consumers referencing the engine via MISSION_DRIVER_HOME. Mirrors the
// TOOL_ROOT pattern in flow-loader.js / main.js.
const TOOL_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

// pi driver convenience defaults: when driver=="pi" and the caller did not
// explicitly set driverArgs/promptMode, these apply so `--driver pi` switches
// without forcing the user to also pass driverArgs/promptMode. Explicit values
// (CLI/env/mission) always win — these are the lowest-priority fallback applied
// at each resolveConfig return point. agentFile is relative to TOOL_ROOT and
// resolved to an absolute path at use.
const PI_DEFAULTS = Object.freeze({
  driverArgs: "-p --model {model} --append-system-prompt @{agentFile} --tools read,write,edit,bash,grep,find,ls",
  promptMode: "stdin",
  agentFile: "agents/build.pi.md",
});

// cline driver convenience defaults: when driver=="cline" and the caller did
// not explicitly set driverArgs/promptMode, these apply so `--driver cline`
// switches without forcing extra config. cline's prompt is a positional arg
// (promptMode "arg", unlike pi's stdin). The persona (-s) is NOT part of this
// whitespace-split template because its content may contain newlines/spaces;
// runner.js injects it as a dedicated `-s <content>` argv pair after the split.
// Explicit args/env/mission values always win; agentFile resolves under TOOL_ROOT.
const CLINE_DEFAULTS = Object.freeze({
  driverArgs: "-m {model} --json --yolo --auto-approve true",
  promptMode: "arg",
  agentFile: "agents/build.cline.md",
});

// Apply driver defaults to driverArgs/promptMode/agentFile. `driverArgs` and
// `promptMode` are the values after args/env/mission fallback (may be undefined).
// promptMode is ALWAYS returned concrete (never undefined) so runner.js's
// `config.promptMode || "stdin"` fallback never silently triggers for opencode.
// opencode (non-pi/cline) path is byte-for-byte unchanged (undefined→undefined,
// "arg"). pi→stdin; cline→arg.
function resolveDriverFields(driver, driverArgs, promptMode) {
  const isPi = driver === "pi";
  const isCline = driver === "cline";
  return {
    driverArgs: driverArgs !== undefined
      ? driverArgs
      : (isPi ? PI_DEFAULTS.driverArgs : (isCline ? CLINE_DEFAULTS.driverArgs : undefined)),
    promptMode: promptMode !== undefined
      ? promptMode
      : (isPi ? PI_DEFAULTS.promptMode : (isCline ? CLINE_DEFAULTS.promptMode : "arg")),
    agentFile: isPi
      ? resolve(TOOL_ROOT, PI_DEFAULTS.agentFile)
      : (isCline ? resolve(TOOL_ROOT, CLINE_DEFAULTS.agentFile) : undefined),
  };
}

/**
 * Inject env vars from a mission/base `env` object into process.env.
 * Never overwrites existing vars (shell export wins).
 *
 * Proxy shorthand: if only `http_proxy` (or `HTTP_PROXY`) is set, the other
 * three case variants are derived from it automatically, so a single key is
 * enough in base.local.json.
 */
function injectEnv(env) {
  if (!env || typeof env !== "object") return;
  for (const [k, v] of Object.entries(env)) {
    if (process.env[k] === undefined) process.env[k] = String(v);
  }
  // Derive missing proxy variants from whichever one was set
  const proxy =
    process.env.HTTP_PROXY || process.env.http_proxy ||
    process.env.HTTPS_PROXY || process.env.https_proxy;
  if (proxy) {
    for (const k of ["HTTP_PROXY", "http_proxy", "HTTPS_PROXY", "https_proxy"]) {
      if (process.env[k] === undefined) process.env[k] = proxy;
    }
  }
  // Derive NO_PROXY variants
  const noProxy = process.env.NO_PROXY || process.env.no_proxy;
  if (noProxy) {
    if (process.env.NO_PROXY === undefined) process.env.NO_PROXY = noProxy;
    if (process.env.no_proxy === undefined) process.env.no_proxy = noProxy;
  }
}

/**
 * Mission-based config resolver.
 *
 * A "mission" is a fixed project config (missions/<name>.json) that tells the
 * generic engine where the roadmap lives, where plans live, what test/build
 * commands to run, etc. The engine makes zero project-specific assumptions;
 * every project path comes from the mission.
 *
 * CLI: node main.js <mission-name>
 *      node main.js <mission-name> --missions-dir ./missions
 *      node main.js --list-missions
 */

export function listMissionsString(missionsDir) {
  if (!existsSync(missionsDir)) return `(missions dir not found: ${missionsDir})`;
  const missions = readdirSync(missionsDir)
    .filter((f) => f.endsWith(".json"))
    .map((f) => "  " + f.replace(".json", ""));
  return missions.length ? missions.join("\n") : "(no missions found)";
}

/**
 * Resolve a target mission-driver run directory under `_tmp/`.
 *
 * @param {string} projectRoot
 * @param {string|true} sel  `true` → newest by mtime; string → exact > prefix > contains match
 * @returns {{ dir: string|null, id: string|null, isLatest: boolean }}
 *   `dir`/`id` are null with `isLatest:false` when `_tmp/` is empty/missing or no match.
 *   Only `*-mission-driver` directories are considered.
 */
export function resolveTargetRun(projectRoot, sel) {
  const tmp = resolve(projectRoot, "_tmp");
  let dirs;
  try {
    dirs = readdirSync(tmp)
      .filter((d) => d.endsWith("-mission-driver"))
      .map((d) => {
        const full = resolve(tmp, d);
        return { d, full, mtime: statSync(full).mtimeMs };
      });
  } catch {
    return { dir: null, id: null, isLatest: false };
  }
  if (dirs.length === 0) return { dir: null, id: null, isLatest: false };

  if (sel === true) {
    dirs.sort((a, b) => b.mtime - a.mtime);
    return { dir: dirs[0].full, id: dirs[0].d, isLatest: true };
  }
  const s = String(sel);
  const hit =
    dirs.find((x) => x.d === s) ||            // exact
    dirs.find((x) => x.d.startsWith(s)) ||    // prefix (suffix may be omitted)
    dirs.find((x) => x.d.includes(s));         // contains
  return hit
    ? { dir: hit.full, id: hit.d, isLatest: false }
    : { dir: null, id: null, isLatest: false };
}

/**
 * Load base.json + base.local.json and inject their env fields into process.env.
 * Uses raw JSON.parse (not loadMission) because base.json is a partial config
 * that intentionally omits required mission fields like name/roadmapPath/plansDir.
 * Merge order: base.json env → base.local.json env (local wins, shell wins over both).
 */
function loadBaseAndInjectEnv(missionsDir) {
  const baseFile = resolve(missionsDir, "base.json");
  let base = {};
  if (existsSync(baseFile)) {
    try { base = JSON.parse(readFileSync(baseFile, "utf8")); } catch { /* ignore */ }
  }
  injectEnv(base.env);
  const localFile = resolve(missionsDir, "base.local.json");
  if (existsSync(localFile)) {
    try {
      const local = JSON.parse(readFileSync(localFile, "utf8"));
      injectEnv(local.env);
    } catch { /* malformed local file — ignore */ }
  }
  return base;
}

/**
 * Infer a module name from a mission's `moduleDir` or its name, used to locate
 * the per-module memory store (`docs/memory/<module>/`). Returns the canonical
 * name (case preserved), or `null` when nothing can be inferred.
 *
 * Generic: no project-specific module-code list. `tools/<name>` sub-paths are
 * preserved verbatim; any other moduleDir resolves to its last path segment;
 * the mission name is the final fallback.
 *
 * Exported so the consumption-side memory injection in main.js reuses the
 * exact same inference as `resolveRunModule` (analysis-time), avoiding drift
 * between the two code paths.
 */
export function inferModuleName(moduleDir, missionName) {
  // 1. Tool modules under `tools/<name>`: preserve the sub-path so
  //    `tools/mission-driver` resolves to the `mission-driver` memory store.
  //    Matches both forward & backslash separators (Windows).
  const toolHit = String(moduleDir || "").match(/^tools[\/\\](.+)/i);
  if (toolHit) return toolHit[1];
  // 2. Otherwise derive from the moduleDir's last path segment, falling back to
  //    the mission name.
  const dir = String(moduleDir || "").trim();
  if (dir) {
    const seg = dir.split(/[\\/]/).filter(Boolean).pop();
    if (seg) return seg;
  }
  const name = String(missionName || "").trim();
  return name || null;
}

/**
 * Map a run directory to the business module it was working on, via:
 * `run-state.json`.missionName → `missions/<name>.json`.moduleDir → module name.
 *
 * Resilient: returns `null` (never throws) on missing files, unparseable JSON,
 * or when no module can be inferred.
 *
 * @returns {{ moduleName: string, moduleMemoryDir: string } | null}
 */
export function resolveRunModule(projectRoot, missionsDir, runDir) {
  try {
    const stateFile = resolve(runDir, "run-state.json");
    const state = JSON.parse(readFileSync(stateFile, "utf8"));
    const missionName = state.missionName;
    if (!missionName) return null;
    const mf = resolve(missionsDir, `${missionName}.json`);
    if (!existsSync(mf)) return null;
    // Resolve via loadMission so the `extends` chain is honored (moduleDir may
    // be inherited from base.json). projectRoot is intentionally NOT passed:
    // resolveRunModule only needs the moduleDir field, not full path validation
    // (a stale path on disk should not block module resolution). loadMission
    // still validates required fields and throws on invalid configs; that is
    // caught here and turned into a null return.
    const mission = loadMission(mf);
    const moduleDir = mission.moduleDir || "";
    const moduleName = inferModuleName(moduleDir, missionName);
    if (!moduleName) return null;
    const isTool = String(moduleDir || "").match(/^tools[\/\\]/);
    return {
      moduleName,
      // Tool modules (e.g. tools/mission-driver) keep their memory under
      // <tool>/memory/, which is already covered by selfMemoryIndex in the
      // prompt. Setting moduleMemoryDir to empty avoids double-injection.
      moduleMemoryDir: isTool
        ? ""
        : resolve(projectRoot, "docs", "memory", moduleName),
    };
  } catch {
    return null;
  }
}

/**
 * Read & parse a JSON file, returning `null` on any error (missing/unparseable).
 */
function safeReadJson(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch {
    return null;
  }
}

/**
 * Read a `.jsonl` file into an array of parsed objects. Lines that fail to
 * parse are skipped. Returns `[]` on missing/empty file.
 */
function safeReadJsonl(file) {
  try {
    const raw = readFileSync(file, "utf8");
    const out = [];
    for (const line of raw.split(/\r?\n/)) {
      const t = line.trim();
      if (!t) continue;
      try { out.push(JSON.parse(t)); } catch { /* skip malformed line */ }
    }
    return out;
  } catch {
    return [];
  }
}

/** Basename of an absolute/logFile path, or "" when falsy. */
function basenameOf(p) {
  if (!p) return "";
  const norm = String(p).replace(/\\/g, "/").replace(/\/+$/, "");
  const idx = norm.lastIndexOf("/");
  return idx === -1 ? norm : norm.slice(idx + 1);
}

/** Format a millisecond duration as a compact human string (e.g. "4h23m"). */
function fmtDuration(ms) {
  if (typeof ms !== "number" || !isFinite(ms)) return "?";
  const s = Math.round(ms / 1000);
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m${s % 60}s`;
  const h = Math.floor(m / 60);
  return `${h}h${m % 60}m`;
}

/**
 * Build a compact (≤ ~4KB) Markdown "run skeleton" from a run directory's
 * structured artifacts (`run-state.json` + `events.jsonl`), pre-digesting the
 * facts a postmortem agent needs so it need not parse megabytes of logs.
 *
 * Degrades gracefully: missing `run-state.json` and/or `events.jsonl` produce
 * a partial skeleton (with a note) rather than throwing.
 *
 * @param {string} runDir
 * @returns {string}
 */
export function buildRunSkeleton(runDir) {
  const state = safeReadJson(join(runDir, "run-state.json"));
  const events = safeReadJsonl(join(runDir, "events.jsonl"));
  const missing = [];
  if (!state) missing.push("run-state.json");
  if (events.length === 0 && !existsSync(join(runDir, "events.jsonl"))) missing.push("events.jsonl");

  const lines = [];

  if (state) {
    const wallMs = state.startedAt
      ? (state.endedAt ? new Date(state.endedAt) : new Date()).getTime() - new Date(state.startedAt).getTime()
      : null;
    lines.push(`Mission: ${state.missionName || "(unknown)"}`);
    lines.push(`Run: ${state.runId || basenameOf(runDir)}`);
    lines.push(
      `Status: ${state.status || "(unknown)"}` +
      (state.status === "running" ? " (未正常结束)" : "") +
      `  Total top-steps: ${(state.steps || []).length}` +
      (wallMs != null ? `  Wall: ~${fmtDuration(wallMs)}` : "")
    );
    // WI5 — surface DEEP_AUDIT round progress so postmortem agents can see how
    // many audit rounds the run executed. Skipped entirely when the flow has
    // no audit concept (maxAuditRounds === 0); legacy runs read as 0 via `?? 0`.
    const maxAuditRounds = state.maxAuditRounds ?? 0;
    if (maxAuditRounds > 0) {
      lines.push(`Audit rounds: ${state.auditRound ?? 0}/${maxAuditRounds}`);
    }
  } else {
    lines.push(`Mission: (run-state.json missing — cannot read)`);
    lines.push(`Run: ${basenameOf(runDir)}`);
  }

  // Event-derived aggregates
  const retries = events.filter((e) => e.type === "transition" && e.via === "retry");
  const fails = events.filter((e) => e.type === "step_failed");
  const limits = events.filter((e) => e.type === "limit_hit");
  const skipped = events.filter((e) => e.type === "step_skipped");
  // bfrv-2 C2a signal source #3: step_completed events whose marker is in
  // FAILISH. The engine emits step_completed{marker:"failed"} for a subflow
  // step that failed (engine.js:1655-1663) — these are INVISIBLE to the
  // step_failed filter (which only catches the top-level step_failed event
  // type). FAILISH mirrors engine.js:544 (`failed`, `max_*`, `ping_pong`) and
  // also includes raw `fail` (the pre-alias form agents emit).
  const FAILISH = new Set(["fail", "failed", "max_cycles", "max_retries", "max_total_steps", "ping_pong"]);
  const completedFails = events.filter(
    (e) => e.type === "step_completed" && typeof e.marker === "string" && FAILISH.has(e.marker),
  );
  lines.push(
    `Retries detected: ${retries.length}   Limit hits: ${limits.length}   Skipped steps: ${skipped.length}`
  );

  // Step timeline from run-state
  if (state && Array.isArray(state.steps) && state.steps.length > 0) {
    lines.push("");
    lines.push("Step timeline (name · visit · marker · durationMs · logFile):");
    for (const s of state.steps) {
      const name = String(s.name || "?").padEnd(13);
      const visit = `v${s.visits ?? "?"}`;
      const marker = String(s.marker || "(none)").padEnd(13);
      const dur = fmtDuration(s.durationMs);
      const log = s.logFile ? `  (log: ${basenameOf(s.logFile)})` : "";
      const produced = Array.isArray(s.produced) && s.produced.length
        ? `  → produced: ${s.produced.join(", ")}`
        : "";
      lines.push(`- ${name} ${visit} ${marker} ${dur}${log}${produced}`);
    }
  }

  // Red flags — the files an agent should actually dig into
  const redFlagFiles = new Map(); // basename → first reason label
  const addRedFlag = (file, reason) => {
    if (!file) return;
    const b = basenameOf(file);
    if (!b) return;
    if (!redFlagFiles.has(b)) redFlagFiles.set(b, reason);
  };
  // fail / retry from run-state steps
  if (state && Array.isArray(state.steps)) {
    for (const s of state.steps) {
      if (s.marker === "fail" || s.status === "failed") addRedFlag(s.logFile, `marker=${s.marker || "failed"}`);
    }
  }
  // fail/retry from events (carries step + visit, logFile resolved via run-state)
  const stepLogByVisit = new Map();
  if (state && Array.isArray(state.steps)) {
    for (const s of state.steps) {
      if (s.logFile) stepLogByVisit.set(`${s.name}#v${s.visits ?? 1}`, basenameOf(s.logFile));
    }
  }
  for (const e of fails) {
    const key = `${e.step}#v${e.visit ?? 1}`;
    addRedFlag(stepLogByVisit.get(key) || "", `step_failed marker=${e.marker || "fail"}`);
  }
  for (const e of limits) {
    const key = `${e.step}#v${e.visit ?? 1}`;
    addRedFlag(stepLogByVisit.get(key) || "", `limit_hit ${e.limitType || ""}`);
  }
  // bfrv-2 C2a signal source #3: step_completed{marker ∈ FAILISH} — the engine
  // emits these for subflow steps (the step_failed event type only fires at the
  // top level). Prefer the event's own logFile (engine.js:1662), fall back to
  // the top-level stepLogByVisit map.
  for (const e of completedFails) {
    const key = `${e.step}#v${e.visit ?? 1}`;
    const file = e.logFile || stepLogByVisit.get(key) || "";
    addRedFlag(file, `step_completed marker=${e.marker}`);
  }

  // bfrv-2 C2a signal source #1: read report.json verdict. A blocked/fail
  // summary is the authoritative verdict from AGGREGATE — surface it directly
  // so the skeleton never claims "RED FLAGS: none" when the run actually
  // blocked/failed. Points the agent at report.json + report.md.
  const report = safeReadJson(join(runDir, "report.json"));
  if (report && report.summary && ((report.summary.blocked || 0) > 0 || (report.summary.fail || 0) > 0)) {
    const s = report.summary;
    const verdict = (s.fail || 0) > 0 ? "failed" : "blocked";
    addRedFlag("report.json", `verdict=${verdict} (fail=${s.fail || 0} blocked=${s.blocked || 0}) → see report.json/report.md`);
  }

  // bfrv-2 C2a signal source #2: glob subflow state files. Failed steps often
  // live in subflow state (run-state-<STEP>-<visit>-<idx>.json), NOT in the
  // top-level run-state.json the loop above reads. Scan every run-state-*.json
  // (excluding run-state.json itself) and flag any step whose marker/status is
  // failed. The step's logFile is inside the subflow state, so we can resolve
  // it directly.
  let subflowStateFiles = [];
  try {
    subflowStateFiles = readdirSync(runDir)
      .filter((f) => f.startsWith("run-state-") && f !== "run-state.json" && f.endsWith(".json"));
  } catch {
    // runDir unreadable or missing — degenerate gracefully (no subflow signal)
  }
  for (const sf of subflowStateFiles) {
    const sub = safeReadJson(join(runDir, sf));
    if (!sub || !Array.isArray(sub.steps)) continue;
    for (const s of sub.steps) {
      if (s.marker === "failed" || s.marker === "fail" || s.status === "failed") {
        addRedFlag(s.logFile || "", `subflow ${sf}: marker=${s.marker || "failed"}`);
      }
    }
  }

  lines.push("");
  if (redFlagFiles.size > 0 || skipped.length > 0) {
    lines.push("RED FLAGS (fail+retry / limit_hit / skipped / verdict / subflow-state) — files to inspect:");
    for (const [b, reason] of redFlagFiles) lines.push(`- ${b}  (${reason})`);
    for (const e of skipped) {
      const key = `${e.step}#v${e.visit ?? 1}`;
      const b = stepLogByVisit.get(key) || `(no log: ${e.step})`;
      lines.push(`- ${b}  (skipped: ${e.reason || "n/a"})`);
    }
  } else {
    lines.push("RED FLAGS: none detected.");
  }

  if (missing.length > 0) {
    lines.push("");
    lines.push(`DATA COMPLETENESS NOTE: missing ${missing.join(", ")} — skeleton may be partial.`);
  }

  return lines.join("\n");
}

// The global audits dir: home of the human/AGENTS.md §8 manual audits and the
// 00-audit-execution-guide.md index. Mission auto-generated audits (from
// MULTI_AUDIT/OPEN_AUDIT) are NOT written here — they are isolated per mission
// (see resolveAuditsDir) to prevent openAudits() cross-mission contamination.
const GLOBAL_AUDITS_DIR = "docs/audits";

/**
 * Derive a per-mission auditsDir when the mission hasn't set an explicit one.
 * Mirrors plansDir's structure with the `plans` path segment swapped for
 * `audits` (docs/plans/{user}/{mission} → docs/audits/{user}/{mission}) so a
 * mission's auto-generated audit files co-locate with its plans, and
 * openAudits() only scans this mission's open audits (no cross-mission
 * contamination — the same class of bug that made plansDir per-mission).
 * Creates the directory so MULTI_AUDIT/OPEN_AUDIT can write to it immediately.
 *
 * Resolution:
 *   - Explicit non-default auditsDir → respected as-is (override).
 *   - auditsDir unset OR == GLOBAL_AUDITS_DIR, and plansDir has a `/plans/`
 *     segment → swap it for `/audits/`.
 *   - Otherwise → leave as-is (don't guess when plansDir doesn't follow the
 *     per-mission convention).
 *
 * Exported for unit testing.
 */
export function resolveAuditsDir(auditsDir, plansDir, projectRoot) {
  const explicit = auditsDir && auditsDir !== GLOBAL_AUDITS_DIR;
  if (explicit) return auditsDir;
  const plansNorm = String(plansDir || "").replace(/\\/g, "/");
  if (!plansNorm.toLowerCase().includes("/plans/")) {
    return auditsDir || GLOBAL_AUDITS_DIR;
  }
  const derived = plansNorm.replace(/\/plans\//i, "/audits/");
  try { mkdirSync(resolve(projectRoot, derived), { recursive: true }); } catch {}
  return derived;
}

export function resolveConfig(args = {}) {
  const projectRoot = args.dir || process.env.PROJECT_ROOT || process.cwd();
  const missionsDir = args.missionsDir
    ? resolve(projectRoot, args.missionsDir)
    : resolve(projectRoot, "missions");
  const dryRun = args.dryRun === true;
  const testMode = args.testMode === true;

  // Dev mode: disable static hosting in the monitor (vite dev server serves
  // the frontend at :5173, proxying /api → :9300). Enabled by --dev or
  // MONITOR_DEV=1. FSD §3.5 Monitor Server adaptation.
  const devMode = args.dev === true || process.env.MONITOR_DEV === "1";

  const agent = args.agent || process.env.OPENCODE_AGENT || "build";
  // driver: drop the hard "opencode" default here so mission.driver (consulted
  // below in resolvedDriver) is not dead-coded; opencode remains the final
  // fallback at the return point.
  const driver = args.driver || process.env.MISSION_DRIVER_EXEC || undefined;
  const driverArgs = args.driverArgs || process.env.MISSION_DRIVER_ARGS || undefined;
  // promptMode: drop the hard "arg" default; each return point applies a
  // driver-aware fallback (pi→"stdin", else "arg") via resolveDriverFields so
  // the pi default can trigger. config.promptMode is always concrete on return.
  const promptMode = args.promptMode || process.env.MISSION_PROMPT_MODE || undefined;
  const autonomyMode = args.autonomyMode || process.env.AUTONOMY_MODE || "auto";
  const model = args.model || process.env.OPENCODE_MODEL || undefined;
  const parseModel = args.parseModel || process.env.OPENCODE_PARSE_MODEL || undefined;
  const maxCycles = args.maxCycles || Number(process.env.MAX_CYCLES) || undefined;
  const maxInnerCycles = args.maxInnerCycles || Number(process.env.MAX_INNER_CYCLES) || undefined;
  const maxTotalSteps = args.maxTotalSteps || Number(process.env.MAX_TOTAL_STEPS) || undefined;

  const monitorPort = args.monitorPort
    ? Number(args.monitorPort)
    : process.env.MONITOR_PORT
      ? Number(process.env.MONITOR_PORT)
      : 9300;
  const noMonitor = args.noMonitor === true || process.env.MONITOR_DISABLE === "1";
  // OPT-6: optional `--pure` startup path — spawn opencode without external
  // plugins (skips project-hooks etc.). Default false keeps behavior unchanged;
  // only enabled per-mission when measured to be safe + beneficial.
  const pure = args.pure === true
    || process.env.OPENCODE_PURE === "1"
    || process.env.OPENCODE_PURE === "true";

  if (args.draftMission) {
    // mdo-2 Phase 1 Decision: --draft-job-dir lets startDraftJob fix the runDir
    // so the spawned draft child writes draft-state.json into the SAME dir the
    // monitor created (config.js:329-330 baseline generated Date.now()-fresh
    // runDir, leaving the caller unable to predict it). Backward compat: the
    // legacy `draft <desc>` CLI (no flag) keeps the Date.now() behaviour.
    const runDir = args.draftJobDir
      ? resolve(projectRoot, args.draftJobDir)
      : resolve(projectRoot, "_tmp", `draft-mission-${Date.now()}`);
    mkdirSync(runDir, { recursive: true });
    // Load base config so driver, model, agent, env etc. from base.json are
    // available even when no specific mission is named (draft / analyze paths).
    const base = loadBaseAndInjectEnv(missionsDir);
    const drvDraft = args.driver || process.env.MISSION_DRIVER_EXEC || base.driver || "opencode";
    const drvFieldsDraft = resolveDriverFields(
      drvDraft,
      args.driverArgs || process.env.MISSION_DRIVER_ARGS || base.driverArgs,
      args.promptMode || process.env.MISSION_PROMPT_MODE || base.promptMode);
    return {
      projectRoot, missionsDir, runDir,
      missionName: null, mission: null,
      draftMission: args.draftMission,
      agent: args.agent || process.env.OPENCODE_AGENT || base.agent || "build",
      driver: drvDraft,
      driverArgs: drvFieldsDraft.driverArgs,
      promptMode: drvFieldsDraft.promptMode,
      agentFile: drvFieldsDraft.agentFile,
      model: args.model || process.env.OPENCODE_MODEL || base.model || "zhipuai-coding-plan/glm-5.2",
      dryRun, testMode,
      devMode,
      pure,
      logFile: resolve(runDir, "mission-draft.log"),
      // mdo-4 P2: carry the wizard's selections through to cmdDraftMission so
      // the two-stage brief/draft pipeline can branch on skipBrief and inject
      // flowHint/targetFile into the prompts.
      flowHint: args.flowHint || null,
      targetFile: args.targetFile || null,
      skipBrief: args.skipBrief === true,
    };
  }

  if (args.analyzeRun) {
    const { dir: targetRunDir, id: targetRunId, isLatest } =
      resolveTargetRun(projectRoot, args.analyzeRun);
    if (!targetRunDir) {
      throw new Error(
        `no matching run found under _tmp/ for: ${args.analyzeRun === true ? "(latest)" : args.analyzeRun}`
      );
    }
    const moduleInfo = resolveRunModule(projectRoot, missionsDir, targetRunDir);
    const runDir = resolve(projectRoot, "_tmp", `analyze-run-${Date.now()}`);
    mkdirSync(runDir, { recursive: true });
    const baseA = loadBaseAndInjectEnv(missionsDir);
    const drvAnalyze = args.driver || process.env.MISSION_DRIVER_EXEC || baseA.driver || "opencode";
    const drvFieldsAnalyze = resolveDriverFields(
      drvAnalyze,
      args.driverArgs || process.env.MISSION_DRIVER_ARGS || baseA.driverArgs,
      args.promptMode || process.env.MISSION_PROMPT_MODE || baseA.promptMode);
    return {
      projectRoot, missionsDir, runDir,
      missionName: null, mission: null,
      analyzeRun: true,
      targetRunDir,
      targetRunId,
      analyzeRunIsLatest: isLatest,
      moduleInfo,
      agent: args.agent || process.env.OPENCODE_AGENT || baseA.agent || "build",
      driver: drvAnalyze,
      driverArgs: drvFieldsAnalyze.driverArgs,
      promptMode: drvFieldsAnalyze.promptMode,
      agentFile: drvFieldsAnalyze.agentFile,
      model: args.model || process.env.OPENCODE_MODEL || baseA.model || "zhipuai-coding-plan/glm-5.2",
      dryRun, testMode,
      devMode,
      pure,
      logFile: resolve(runDir, "analyze-run.log"),
    };
  }

  if (args.listMissions) {
    console.log(`Missions in ${missionsDir}:`);
    console.log(listMissionsString(missionsDir));
    process.exit(0);
  }

  const missionName = args.mission || args.module || "";
  if (!missionName) {
    throw new Error(
      `mission name is required: mission-driver.sh <mission-name>\n` +
      `Available missions:\n${listMissionsString(missionsDir)}`
    );
  }

  const missionFile = resolve(missionsDir, `${missionName}.json`);
  if (!existsSync(missionFile)) {
    throw new Error(
      `mission '${missionName}' not found: ${missionFile}\n` +
      `Available missions:\n${listMissionsString(missionsDir)}`
    );
  }
  const mission = loadMission(missionFile, projectRoot);

  // Per-mission audit isolation (see resolveAuditsDir). Mutates the resolved
  // mission so both main.js delegates.vars.auditsDir and flow-loader's
  // openAudits() (which read mission.auditsDir) see the derived path.
  mission.auditsDir = resolveAuditsDir(mission.auditsDir, mission.plansDir, projectRoot);

  // mdr-fix-2: per-mission promptsDir override. Resolved to an absolute path
  // (empty when unset) so both main.js (createMissionDriverFlow) and
  // loadSubFlow read one flat config field — uniform with missionsDir/auditsDir.
  // Existence is validated by mission-check.mjs (promptsDir is in its
  // existence-checked list); resolving eagerly here keeps the chain consistent.
  const missionPromptsDir = mission.promptsDir
    ? resolve(projectRoot, mission.promptsDir)
    : "";

  // Inject mission.env into process.env (never overwrite existing vars).
  // base.local.json is the right place for machine-local config (proxy etc.) not committed to git.
  loadBaseAndInjectEnv(missionsDir);
  if (mission.env) injectEnv(mission.env);

  // Resolve model/parseModel/variant/max* with fallback chain: CLI > env > mission base > hard default
  const resolvedDriver = driver || mission.driver || "opencode";
  const resolvedAutonomyMode = (autonomyMode === "ask" || mission.autonomyMode === "ask") ? "ask" : "auto";
  const resolvedModel = model || mission.model || "zhipuai-coding-plan/glm-5.2";
  const resolvedParseModel = parseModel || mission.parseModel || undefined;
  const resolvedVariant = mission.variant || undefined;
  const resolvedAgent = agent || mission.agent || "build";
  const resolvedMaxCycles = maxCycles ?? mission.maxCycles ?? undefined;
  const resolvedMaxInnerCycles = maxInnerCycles ?? mission.maxInnerCycles ?? undefined;
  const resolvedMaxTotalSteps = maxTotalSteps ?? mission.maxTotalSteps ?? undefined;

  // mdr-1 Phase 3: transient provider-error (rate-limit/quota/overload) retry
  // budget. INDEPENDENT of onError.maxRetries so transient faults get a generous
  // retry budget without consuming the real-failure budget (memory L003, count=3:
  // onError carried the tightest budget for the faults that most needed retrying).
  // Resolution chain: env > mission.transient (extends-inherited from base.json)
  // > hard default. `enabled` defaults true unless explicitly disabled.
  const mTransient = (mission && mission.transient) || {};
  const transient = {
    enabled: process.env.TRANSIENT_RETRY_ENABLED !== "0" && mTransient.enabled !== false,
    maxRetries: Number(process.env.TRANSIENT_MAX_RETRIES) || mTransient.maxRetries || 6,
    backoffBaseMs: Number(process.env.TRANSIENT_BACKOFF_BASE_MS) || mTransient.backoffBaseMs || 5_000,
    backoffCapMs: Number(process.env.TRANSIENT_BACKOFF_CAP_MS) || mTransient.backoffCapMs || 120_000,
    // mdr-quota — quota/usage-limit exhaustion wait policy (see engine.js):
    // wait until the announced reset time + buffer (fallback below when the
    // reset time is unparseable), retry indefinitely. quotaMaxWaitMs caps the
    // TOTAL wait per step (0 = unlimited, the default — a quota condition is
    // time-bounded by the provider and must not fail the mission).
    quotaWaitFallbackMs: Number(process.env.TRANSIENT_QUOTA_WAIT_FALLBACK_MS) || mTransient.quotaWaitFallbackMs || 600_000,
    quotaResetBufferMs: Number(process.env.TRANSIENT_QUOTA_RESET_BUFFER_MS) || mTransient.quotaResetBufferMs || 60_000,
    quotaMaxWaitMs: Number(process.env.TRANSIENT_QUOTA_MAX_WAIT_MS) || mTransient.quotaMaxWaitMs || 0,
  };

  // mdo-3 Phase 2: Fast Run / Skip Steps (FSD §3.3.2A).
  // Resolution chain (highest priority first):
  //   skipSteps    : CLI --skip-steps A,B (comma split) · env SKIP_STEPS ·
  //                  mission.skipSteps (extends-inherited) → array
  //   fastRun      : CLI --fast · env FAST_RUN=1 · mission.fastRun → bool
  //   fastSkipSteps: mission.fastSkipSteps (extends-inherited from base.json,
  //                  default ["DEEP_AUDIT"]) → array
  // effectiveSkip = union(skipSteps, fastRun ? fastSkipSteps : []) as a Set,
  // consumed by engine.js run() step-entry check (before `when`).
  const splitCsv = (v) =>
    Array.isArray(v) ? v.filter(Boolean) :
    (typeof v === "string" && v.trim()) ? v.split(",").map((s) => s.trim()).filter(Boolean) :
    [];
  const skipSteps =
    splitCsv(args.skipSteps) ||
    splitCsv(process.env.SKIP_STEPS) ||
    splitCsv(mission.skipSteps);
  const fastRun =
    args.fastRun === true ||
    process.env.FAST_RUN === "1" ||
    process.env.FAST_RUN === "true" ||
    mission.fastRun === true;
  const fastSkipSteps = splitCsv(mission.fastSkipSteps);
  const effectiveSkip = new Set([
    ...skipSteps,
    ...(fastRun ? fastSkipSteps : []),
  ]);

  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const ts =
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}-` +
    `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const timestamp =
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}-` +
    `${pad(now.getHours())}${pad(now.getMinutes())}`;
  const runDir = args.runDir
    ? resolve(projectRoot, "_tmp", args.runDir)
    : resolve(projectRoot, "_tmp", `${ts}-mission-driver`);
  mkdirSync(runDir, { recursive: true });

  // pi driver defaults: lowest-priority fallback for driverArgs/promptMode +
  // computed agentFile (engine-relative absolute path). opencode path unchanged.
  const drvFields = resolveDriverFields(
    resolvedDriver,
    driverArgs || mission.driverArgs,
    promptMode || mission.promptMode,
  );

  return {
    projectRoot,
    missionsDir,
    missionName,
    mission,
    missionPromptsDir,
    runDir,
    timestamp,
    driver: resolvedDriver,
    driverArgs: drvFields.driverArgs,
    promptMode: drvFields.promptMode,
    agentFile: drvFields.agentFile,
    autonomyMode: resolvedAutonomyMode,
    agent: resolvedAgent,
    model: resolvedModel,
    variant: resolvedVariant,
    parseModel: resolvedParseModel,
    maxCycles: resolvedMaxCycles,
    maxInnerCycles: resolvedMaxInnerCycles,
    maxTotalSteps: resolvedMaxTotalSteps,
    transient,
    // mdo-3 Phase 2: fast/skip config + merged effectiveSkip (FSD §3.3.2A).
    fastRun,
    skipSteps,
    fastSkipSteps,
    effectiveSkip,
    monitorPort,
    noMonitor,
    devMode,
    pure,
    dryRun,
    testMode,
    // WI2/WI3: CLI-only flags consumed by main.js post-resolve to drive
    // engine.run(entryOverride) and the singleStep cap. ResolveConfig itself
    // is CLI-agnostic (draft/analyze branches don't pass these), so they
    // pass through verbatim (undefined when absent — no behavior change).
    entryStep: args.entryStep,
    fromStep: args.fromStep,
    logFile: resolve(runDir, `${missionName}.log`),
  };
}
