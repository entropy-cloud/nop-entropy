import { appendFileSync, existsSync, readFileSync, writeFileSync, renameSync, readdirSync, mkdirSync } from "node:fs";
import { resolve, basename, isAbsolute, dirname } from "node:path";
import { snapshot as sysSnapshot } from "./sys-snapshot.mjs";
import { reapStartupOrphans } from "./reap-orphans.mjs";
import { registerActiveRun } from "./active-run-registry.mjs";
import { getAllProcesses } from "./platform.mjs";
import { evaluateExpression, isExpression, resolveTemplateVars } from "./expression.mjs";
import { roadmapAllDone } from "./roadmap-check.mjs";
/** Backoff helper for retry after short-duration failures (likely rate-limited). */
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function localTimeStr(d = new Date()) {
  const pad = (n) => String(n).padStart(2, "0");
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function durationStr(ms) {
  const s = Math.floor(ms / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (h > 0) return `${h}h${m}m${sec}s`;
  if (m > 0) return `${m}m${sec}s`;
  return `${sec}s`;
}

export function extractTag(text, tagName) {
  const re = new RegExp(`<${tagName}>([^<]+)</${tagName}>`, "g");
  const matches = [...text.matchAll(re)];
  if (matches.length === 0) return null;
  return matches[matches.length - 1][1].toLowerCase().trim();
}

// OPT-2: tolerant marker extraction — used as a second-chance pass before the
// expensive runParseAgent fallback. Tolerates (a) tag-name case (`i` flag),
// (b) whitespace between tag name and angle brackets (`< AI_STEP_RESULT >`),
// (c) output wrapped in markdown code fences (value capture `[^<]+` is unaffected
// by backtick fences). Value still takes the last match and is lowercased/trimmed,
// matching extractTag semantics. Does NOT replace extractTag (strict path kept).
export function extractTagTolerant(text, tagName) {
  const escaped = String(tagName).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const re = new RegExp(`<\\s*${escaped}\\s*>([^<]+)<\\s*/\\s*${escaped}\\s*>`, "gi");
  const matches = [...text.matchAll(re)];
  if (matches.length === 0) return null;
  return matches[matches.length - 1][1].toLowerCase().trim();
}

// Fuzzy tag extraction: scans for ANY XML-ish tag pair <OPEN>value</CLOSE> whose
// value matches one of the known marker values. Handles tag-name typos like
// <AIE_STEP_RESULT> instead of <AI_STEP_RESULT>, INCLUDING the case where the
// opening and closing tag names disagree (e.g. `<AIE_STEP_RESULT>done</AI_STEP_RESULT>`
// — observed in real runs; the mismatched-tag variant previously slipped past a
// `\1` backreference and hard-failed the whole mission). The tag-name char class
// is CASE-INSENSITIVE (`[A-Za-z][A-Za-z_]{4,}`, length >= 5) so the LLM recovery
// chain's mixed-case typo variant `<Ai_STEP_RESULT>done</AI_STEP_RESULT>` (lowercase
// i, observed in real runs — memory L009 SEV1) is also recovered. The HTML guard
// is preserved via the length>=5 floor + value whitelist (the primary guard):
// HTML short tags (<b>, <span>, <code>) cannot reach the minimum tag-name length
// nor carry a whitelisted marker value. Only invoked after strict and tolerant
// extraction both fail, as a last-resort before the LLM fallback.
export function extractTagFuzzy(text, validValues) {
  if (!validValues || validValues.length === 0) return null;
  const valuePattern = validValues
    .map(v => v.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join("|");
  // Independent open/close tag names (no backreference) so a mismatched-but-
  // clearly-a-result-tag pair is still recovered. Value ∈ whitelist keeps it safe.
  const re = new RegExp(`<[A-Za-z][A-Za-z_]{4,}>\\s*(${valuePattern})\\s*</[A-Za-z][A-Za-z_]{4,}>`, "g");
  const matches = [...text.matchAll(re)];
  if (matches.length === 0) return null;
  return matches[matches.length - 1][1].toLowerCase().trim();
}

// mdr-2 Phase 1 — strip ANSI escape sequences and common control characters from
// agent output BEFORE any tag extraction pass. Real opencode/CLI output is often
// log-colored with CSI sequences (e.g. `\x1b[31m...\x1b[0m`) which can sit inside
// a `<TAG>value</TAG>` capture and break the strict/tolerant `[^<]+` / `[^<]`
// value matchers (memory L009). Zero-dependency (no strip-ansi) — pure regex.
// Covers: CSI sequences `ESC [ ... letter`, OSC sequences `ESC ] ... BEL/ST`,
// other common ESC two-letter controls, and stray non-text C0 controls (except
// \t \n \r which are meaningful whitespace). Idempotent: stripping an already-
// clean string is a no-op.
export function stripAnsiControl(text) {
  if (!text) return "";
  return String(text)
    // CSI: ESC [ <params> <intermediate>* <final letter>
    .replace(/\x1b\[[0-9;?]*[ -\/]*[@-~]/g, "")
    // OSC: ESC ] ... terminated by BEL (\x07) or ST (ESC \)
    .replace(/\x1b\][^\x07\x1b]*(?:\x07|\x1b\\)/g, "")
    // Other two-char ESC sequences (ESC X) not caught above
    .replace(/\x1b[@-_]/g, "")
    // Stray C0 control chars except HT(\t=0x09) LF(\n=0x0a) CR(\r=0x0d)
    .replace(/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/g, "");
}

function extractXmlBlock(text, tagName) {
  const re = new RegExp(`<${tagName}>[\\s\\S]*?<\\/${tagName}>`);
  const m = text.match(re);
  return m ? m[0] : null;
}

// mdr-3 Phase 3: prompt size guard. Passing the prompt via stdin (mdr-3 Phase 2)
// removed the 32K cmdline ceiling, but an unbounded prompt still blows the model
// token budget and degrades tag recovery / fuzzy matching (memory L009, P1-3).
// The worst offender is the closure-audit `issues` append, which previously fed
// the FULL audit output (incl. `npm test` stdout) back into the next EXECUTE
// prompt. Decision: cap at 24KB (well under the CreateProcess 32K limit even if
// any positional fallback were ever reintroduced, and a sane model-context
// budget). When exceeded, keep the head and tail with a clear truncation marker
// so the agent retains both the opening instructions and the trailing
// AI_STEP_RESULT contract; the dropped middle is logged/emitted. Byte length is
// used (not char count) so multibyte CJK prompts are bounded correctly.
// Rejected: dropping only the tail loses the AI_STEP_RESULT contract; dropping
// only the head loses the task instructions — head+tail is the safe split.
const PROMPT_MAX_BYTES = 24 * 1024;
const PROMPT_KEEP_BYTES = 8 * 1024;

export function boundPromptSize(prompt, opts = {}) {
  if (!prompt) return prompt;
  const max = opts.maxBytes != null ? opts.maxBytes : PROMPT_MAX_BYTES;
  const keep = opts.keepBytes != null ? opts.keepBytes : PROMPT_KEEP_BYTES;
  const len = Buffer.byteLength(prompt, "utf8");
  if (len <= max) return prompt;
  const head = Buffer.from(prompt, "utf8").slice(0, keep).toString("utf8");
  const tail = Buffer.from(prompt, "utf8").slice(-keep).toString("utf8");
  const dropped = len - Buffer.byteLength(head, "utf8") - Buffer.byteLength(tail, "utf8");
  const marker = `\n\n[... PROMPT TRUNCATED: dropped ~${dropped} middle bytes to stay under the ${max}-byte budget. Head and tail retained; the AI_STEP_RESULT contract at the end is intact. ...]\n\n`;
  const bounded = head + marker + tail;
  if (typeof opts.onTruncate === "function") {
    try { opts.onTruncate({ originalBytes: len, boundedBytes: Buffer.byteLength(bounded, "utf8"), droppedBytes: dropped }); } catch {}
  }
  return bounded;
}

// mdr-1 Phase 2 — signatures that indicate a transient provider error
// (rate-limit / quota / overload) as emitted to stderr by the model CLI.
// Matched case-insensitively against the captured stderr tail.
const TRANSIENT_PROVIDER_SIGS = [
  /\b429\b/,
  /\btoo many requests\b/i,
  /rate[\s_-]?limit/i,
  /\bquota\b/i,
  /\boverloaded\b/i,
  /\bservice unavailable\b/i,
  // mdr-quota — quota/usage-limit exhaustion signatures, English + Chinese.
  // zhipu emits "已达到 5 小时的使用上限。您的限额将在 <ts> 重置。" —
  // previously MISSED by all signatures above, so a deterministic quota
  // exhaustion was misclassified as a genuine failure and consumed the
  // onError retry budget (memory L010: full-mission failure on quota reset).
  /usage[\s_-]?limit/i,
  /\b(?:daily|hourly|monthly|weekly)\s+(?:limit|quota)\b/i,
  /使用上限/,
  /限额/,
  /使用额度/,
  /额度(?:不足|已用尽|已用完|耗尽)/,
];

// mdr-quota — signatures that narrow a transient failure to quota/limit
// EXHAUSTION: a deterministic, time-bounded condition (the provider announces
// the reset time). Such failures get a wait-until-reset retry that does NOT
// consume the transient/onError budget and has NO retry cap by default.
const QUOTA_EXHAUSTION_SIGS = [
  /使用上限/,
  /限额/,
  /使用额度/,
  /额度(?:不足|已用尽|已用完|耗尽)/,
  /usage[\s_-]?limit/i,
  /\b(?:daily|hourly|monthly|weekly)\s+(?:limit|quota)\b/i,
];

/**
 * mdr-1 Phase 2 — pure classifier: is this subprocess failure a TRANSIENT
 * provider error (rate-limit / quota / overload), as evidenced by a signature
 * in the captured stderr tail?
 *
 * Decision: the OLD heuristic (`stepDur<60s && logLen<600` → "Likely rate
 * limit") collapsed cmdline overflow / CLI crash / genuine failure / real
 * rate-limit into one bucket (memory L001, count=4). It is replaced by a
 * SINGLE criterion: a stderr signature must actually be present. Empty output
 * with no stderr → `null` (cause unknown, NOT transient). A normal failure
 * with no signature → `null`. Rejected alternative: keep the duration/length
 * dual-condition as an OR — re-introduces the misdiagnosis this removes.
 *
 * Note on "non real agent fail marker": this function only inspects stderr. The
 * call site additionally guards on `!result.marker` — a genuine extracted
 * `<...>fail` marker is a real business failure and is never treated as
 * transient, even if the literal signature text happens to appear in stderr.
 *
 * @returns {string|null} the matched signature source (truthy = transient), or null.
 */
export function isTransientProviderError({ exitCode, stderrTail, stepDurMs, logLen } = {}) {
  const stderr = stderrTail ? String(stderrTail) : "";
  if (!stderr) return null;
  for (const re of TRANSIENT_PROVIDER_SIGS) {
    if (re.test(stderr)) return re.source;
  }
  return null;
}

/**
 * mdr-quota — pure classifier: is this transient failure a quota/usage-limit
 * EXHAUSTION (time-bounded, resolves at the announced reset time) rather than
 * a generic rate-limit/overload blip? Only consulted after
 * isTransientProviderError matched. Returns the matched signature source
 * (truthy = quota exhaustion), or null.
 */
export function isQuotaExhaustion(stderrTail) {
  const stderr = stderrTail ? String(stderrTail) : "";
  if (!stderr) return null;
  for (const re of QUOTA_EXHAUSTION_SIGS) {
    if (re.test(stderr)) return re.source;
  }
  return null;
}

// mdr-quota — reset-time extraction from a quota message. Supported forms:
//   "您的限额将在 2026-08-15 01:12:08 重置。"  → local time (the observed
//   zhipu form; Date.parse("YYYY-MM-DD HH:mm:ss") is interpreted as local)
//   "usage limit ... resets at 2026-08-15T01:12:08Z" → ISO (UTC / offset)
// A bare time-of-day without a date is unparseable → null (caller falls back
// to a fixed wait). The "重置"/"reset" proximity guard keeps unrelated
// business timestamps from being mistaken for a reset time.
const QUOTA_RESET_PROXIMITY_RE = /重置|reset/i;
const QUOTA_RESET_LOCAL_RE = /(20\d{2}-\d{2}-\d{2})\s+(\d{2}:\d{2}(?::\d{2})?)/;
const QUOTA_RESET_ISO_RE = /20\d{2}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?/;

/**
 * mdr-quota — extract the quota reset time (epoch ms) from the provider
 * message, or null when absent/unparseable. A reset already in the past
 * returns null too (stale message — fall back to the fixed wait).
 */
export function extractQuotaResetTime(stderrTail) {
  const stderr = stderrTail ? String(stderrTail) : "";
  if (!stderr || !QUOTA_RESET_PROXIMITY_RE.test(stderr)) return null;
  const now = Date.now();
  const iso = stderr.match(QUOTA_RESET_ISO_RE);
  if (iso) {
    const t = Date.parse(iso[0]);
    if (Number.isFinite(t) && t > now) return t;
  }
  const local = stderr.match(QUOTA_RESET_LOCAL_RE);
  if (local) {
    const t = Date.parse(`${local[1]} ${local[2]}` + (local[2].split(":").length === 2 ? ":00" : ""));
    if (Number.isFinite(t) && t > now) return t;
  }
  return null;
}

/**
 * mdr-quota — how long to wait before retrying a quota exhaustion: until the
 * announced reset time + buffer, or the fixed fallback (default 10 min) when
 * the reset time is unparseable.
 */
export function quotaWaitMs(stderrTail, cfg = {}, now = Date.now()) {
  const fallbackMs = cfg.quotaWaitFallbackMs || 600_000;
  const bufferMs = cfg.quotaResetBufferMs || 60_000;
  const reset = extractQuotaResetTime(stderrTail);
  if (reset != null) return reset + bufferMs - now;
  return fallbackMs;
}

/** Strip the executor-written "# ..." header lines; return the real body text. */
function bodyAfterHeader(text) {
  if (!text) return "";
  return String(text)
    .split(/\r?\n/)
    .filter((l) => l.trim().length > 0 && !/^#/.test(l))
    .join("\n")
    .trim();
}

// mdr-1 Phase 2 — below this body length, result.text is header-only / stray
// garbage with no real AI output for the parse agent to infer a marker from.
// Parsing it wastes a model call (memory L001: header-only logs repeatedly
// reached runParseAgent with no parseable content). The executor header is
// structured "# .../# .../# ..." so a header-only crash yields body="" here.
// The threshold is a small floor (not 50+) so genuine short AI output (e.g.
// "no tags here") still reaches the parse fallback — only truly empty/near-
// empty bodies are short-circuited.
const PARSE_MIN_BODY_CHARS = 10;

// mdo-3 Phase 2: resolve the next-hop target when a step is skipped via
// effectiveSkip (FSD §3.3.2A / §3.3.3A). Mirrors the intent of the when:false
// path but picks the FIRST non-retry transition as the skip destination:
//   1. scan stepDef.transitions for the first entry whose action is a plain
//      goto (has `goto`, not a `retry`) → { goto }
//   2. fall back to stepDef.otherwise (goto/done, non-retry)
//   3. default to { done: "completed" } — a skipped terminal step completes the
//      run rather than dead-ending (matches when:false's fallback).
// A `done` transition is intentionally NOT returned from step 1: a skip should
// advance the flow, not synthesize a terminal status from an unrelated marker's
// done action. `done` is only honoured via `otherwise` (an explicit skip-route).
function firstNonRetryTarget(stepDef) {
  const transitions = stepDef.transitions || {};
  for (const t of Object.values(transitions)) {
    if (t && t.goto && !t.retry) return { goto: t.goto };
  }
  if (stepDef.otherwise) {
    const o = stepDef.otherwise;
    if (o.goto && !o.retry) return { goto: o.goto };
    if (o.done) return { done: o.done };
  }
  return { done: "completed" };
}

export class FlowEngine {
  constructor(flowDef, delegates) {
    this.flow = flowDef;
    this.delegates = delegates;
    this.expressionFuncs = delegates?.expressionFuncs || {};
    this.context = new Map();
    this.flowVars = new Map();
    this.visitCounts = new Map();
    this.retryCounts = new Map();
    // mdr-1 Phase 3: independent retry counter for transient provider errors
    // (rate-limit/quota/overload). Kept separate from retryCounts so transient
    // retries do NOT consume the onError/fail transition budget (plan §Phase 3).
    this.transientCounts = new Map();
    // mdr-quota: accumulated wait time per step for quota-exhaustion
    // conditions, consumed against transient.quotaMaxWaitMs (0 = unlimited).
    this.quotaWaitTotals = new Map();
    this.appendBuffers = new Map();
    this.pingPongHistory = [];
    this.pingPongViaRetry = new Set();
    this.logEntries = [];
    this.startTime = null;
    this.lastSessionId = null;
  }

  _log(msg) {
    const line = `[${localTimeStr()}] ${msg}`;
    this.logEntries.push(line);
    console.log(line);
    const logFile = this.delegates.logFile;
    if (logFile) {
      try { appendFileSync(logFile, line + "\n"); } catch {}
    }
  }

  /**
   * mdr-1 Phase 3 — resolve the transient provider-error retry budget from the
   * run config (populated by config.js from env/mission/hard-default), with a
   * local hard fallback so the engine is safe even when config is absent (e.g.
   * unit tests that build a bare FlowEngine without a full config object).
   */
  _transientConfig() {
    const t = (this.delegates && this.delegates.config && this.delegates.config.transient) || {};
    return {
      enabled: t.enabled !== false,
      maxRetries: Number.isFinite(t.maxRetries) ? t.maxRetries : 6,
      backoffBaseMs: Number.isFinite(t.backoffBaseMs) ? t.backoffBaseMs : 5_000,
      backoffCapMs: Number.isFinite(t.backoffCapMs) ? t.backoffCapMs : 120_000,
      // mdr-quota — quota-exhaustion wait policy. quotaMaxWaitMs caps the
      // TOTAL wait time per step for quota conditions (0 = unlimited: keep
      // retrying until the quota resets — the condition is time-bounded by
      // the provider, so the mission must not fail for retry-count reasons).
      quotaWaitFallbackMs: Number.isFinite(t.quotaWaitFallbackMs) ? t.quotaWaitFallbackMs : 600_000,
      quotaResetBufferMs: Number.isFinite(t.quotaResetBufferMs) ? t.quotaResetBufferMs : 60_000,
      quotaMaxWaitMs: Number.isFinite(t.quotaMaxWaitMs) ? t.quotaMaxWaitMs : 0,
    };
  }

  /** Write a script step's output to an oc-*.log file in runDir so it shows up
   *  in the Log Viewer. Returns the absolute path. */
  _writeScriptLog(stepName, text, marker) {
    const cfg = this.delegates.config || {};
    const runDir = cfg.runDir;
    if (!runDir) return null;
    const ts = Date.now();
    const rand = Math.random().toString(36).slice(2, 8);
    const fileName = `oc-${stepName}-${ts}-${rand}.log`;
    const filePath = resolve(runDir, fileName);
    try {
      mkdirSync(dirname(filePath), { recursive: true });
      const header = `[${localTimeStr()}] Script step: ${stepName}\n[${localTimeStr()}] Result: ${marker}\n\n`;
      writeFileSync(filePath, header + text + "\n");
    } catch {}
    return filePath;
  }

  // ── workflow state tracking: run-state.json in {runDir} (separated from mission config, FSD §4.2) ──
  _workflowFile() {
    const cfg = this.delegates.config || {};
    if (!cfg.runDir) return null;
    if (cfg.subflowId) {
      return resolve(cfg.runDir, `run-state-${cfg.subflowId}.json`);
    }
    return resolve(cfg.runDir, "run-state.json");
  }

  _listPlans() {
    const cfg = this.delegates.config || {};
    const plansDir = cfg.mission && cfg.mission.plansDir && cfg.projectRoot
      ? resolve(cfg.projectRoot, cfg.mission.plansDir) : null;
    if (!plansDir) return [];
    try { return readdirSync(plansDir).filter((f) => f.endsWith(".md")).sort(); }
    catch { return []; }
  }

  _initWorkflow() {
    this.workflow = {
      missionName: this.missionName || null,
      flowName: this.flow.name || null,
      runId: this.runId || null,
      runDir: (this.delegates.config || {}).runDir || null,
      // Persist the main process PID so stale-run reconciliation can decide
      // liveness by PID survival (see src/run-reconcile.mjs, FSD §4.1). Old
      // run-state.json without this field fall back to a conservative time
      // threshold in reconcileStaleRuns (backward compatible).
      pid: process.pid,
      status: "running",
      startedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      endedAt: null,
      currentStep: null,
      steps: [],
      // WI1 — DEEP_AUDIT round counter (1-based once incremented). Persisted
      // so monitor / replay / the maxAuditRounds gate all read the authoritative
      // count. Old run-state.json without this field falls back to 0 elsewhere
      // via `|| 0` / `?? 0`. See design/step-execution-and-audit-count-design.md §4.1.
      auditRound: 0,
      maxAuditRounds: this.flow.maxAuditRounds ?? 0,
    };
    // For forEach subflow children: persist forEachItem (the plan path) at
    // init time so the monitor can display the plan name for in-flight
    // children whose placeholder hasn't been appended to the parent's
    // subflowRuns yet (engine appends on COMPLETION, not start). Without
    // this, the dashboard shows "Plan N" without the file name while the
    // child is still running.
    const vars = this.delegates.vars || {};
    if (vars.forEachItem != null) {
      this.workflow.forEachItem = vars.forEachItem;
    }
    this._wfCurrent = null;
    this._writeWorkflow();
  }

  _wfOpen(name, visits) {
    this._wfClose(null, this._wfCurrent ? "continued" : null);
    this._wfCurrent = { name, visits, startedAt: Date.now(), plansBefore: this._listPlans() };
    if (this.workflow) {
      this.workflow.currentStep = name;
      this.workflow.updatedAt = new Date().toISOString();
      // WI1 — increment the per-run audit round counter when the MAIN flow
      // enters its auditEntry step (the DEEP_AUDIT top-level step). Subflow
      // children share this code path but carry isSubflow:true in their
      // delegates.config, so their internal steps do not count. Incrementing
      // here (before _writeWorkflow) makes "audit in progress" crash-safe:
      // a mid-audit crash leaves run-state reflecting "round N in progress".
      // The maxAuditRounds gate (run()) reads the PRE-increment value with
      // `>=`, see design §5.2 写法 2.
      if ((this.delegates.config || {}).isSubflow !== true
        && name === (this.flow.auditEntry || this.flow.entry)) {
        this.workflow.auditRound = (this.workflow.auditRound || 0) + 1;
      }
      const stepDef = (this.flow.steps && this.flow.steps[name]) || {};
      const entry = {
        name,
        status: "running",
        visits,
        startedAt: new Date(this._wfCurrent.startedAt).toISOString(),
        endedAt: null,
        durationMs: null,
        marker: null,
        produced: [],
        sessionId: null,
      };
      if (stepDef.type === "subflow") {
        entry.type = "subflow";
        entry.subflowRuns = [];
      }
      this.workflow.steps.push(entry);
      this._writeWorkflow();
    }
  }

  _wfClose(marker, status, sessionId, meta = {}) {
    if (!this._wfCurrent || !this.workflow) return null;
    const c = this._wfCurrent;
    const now = Date.now();
    const produced = this._listPlans().filter((f) => !c.plansBefore.includes(f));
    const steps = this.workflow.steps;
    // Find the running placeholder to replace. Capture its live sessionId /
    // logFile / promptFile (set by _onAgentStepUpdate during execution via
    // onSpawn polling) as fallback when the close parameters are null — the
    // runner sometimes can't extract these from the result text even though
    // onSpawn polling already found them, and _wfClose builds a fresh record
    // that would otherwise overwrite the live values with null.
    let replaceIdx = -1;
    for (let i = steps.length - 1; i >= 0; i--) {
      if (steps[i].name === c.name && steps[i].visits === c.visits && steps[i].status === "running") {
        replaceIdx = i;
        break;
      }
    }
    const live = replaceIdx >= 0 ? steps[replaceIdx] : null;
    const record = {
      name: c.name,
      status: status || "completed",
      visits: c.visits,
      startedAt: new Date(c.startedAt).toISOString(),
      endedAt: new Date(now).toISOString(),
      durationMs: now - c.startedAt,
      marker,
      produced,
      // OPT-1: persist the opencode session id that executed this step so the
      // run can be replayed (opencode export <sessionId>). null for non-agent
      // steps (tool/script/subflow/group) and for legacy close points that
      // intentionally have no session (skipped/continued/finalize). Falls
      // back to the live value from _onAgentStepUpdate when the close
      // parameter is null (runner couldn't re-extract from result text).
      sessionId: sessionId || (live && live.sessionId) || null,
      // Same preservation for logFile / promptFile — _onAgentStepUpdate sets
      // these during execution; don't let _wfClose's fresh-record build drop them.
      ...(live && live.logFile ? { logFile: live.logFile } : {}),
      ...(live && live.promptFile ? { promptFile: live.promptFile } : {}),
      // OPT-7: carry forward the suspend flag (set on _wfCurrent by the
      // onSuspend handler during execution) so the closed record + monitor
      // timeline surface that the step was frozen by a system sleep. Omitted
      // entirely when not suspended → backward compatible with old run-state.json.
      ...(c.suspended ? { suspended: true, suspendGapMs: c.suspendGapMs ?? null } : {}),
      ...meta,
    };
    if (replaceIdx >= 0) {
      steps[replaceIdx] = record;
    } else {
      steps.push(record);
    }
    this._wfCurrent = null;
    this.workflow.updatedAt = new Date().toISOString();
    this._writeWorkflow();
    return record;
  }

  _finalizeWorkflow(status) {
    if (!this.workflow) return;
    // WI5 — map single_step_done to step-level "completed" so the run-state
    // step record does not contradict main.js exitMap (which maps
    // single_step_done → exit code 0, i.e. success). The top-level workflow
    // status below stays as the original `status` value, preserving the
    // single_step_done vs completed distinction for monitor / consumers.
    if (this._wfCurrent) this._wfClose(null, (status === "completed" || status === "single_step_done") ? "completed" : "failed");
    this.workflow.status = status;
    this.workflow.endedAt = new Date().toISOString();
    this.workflow.updatedAt = new Date().toISOString();
    this._writeWorkflow();
  }

  _writeWorkflow() {
    if (!this.workflow) return;
    const file = this._workflowFile();
    if (!file) return;
    try {
      const tmp = file + ".tmp";
      writeFileSync(tmp, JSON.stringify(this.workflow, null, 2) + "\n", "utf8");
      renameSync(tmp, file);
    } catch {}
  }

  // mdr-remediate-3 N3 (Decision — Option B, doc-only): this method matches on
  // `name + status === "running"` only, WITHOUT the `visits` guard that
  // `_wfAppendSubflowRun` (below) carries. That asymmetry is intentional and
  // safe under the current architecture: the run loop is strictly sequential
  // and `_wfOpen` (`:332`) closes the prior `_wfCurrent` before pushing the
  // next "running" entry, so at most ONE step record is ever "running" at a
  // time — a `name + status === "running"` match therefore always uniquely
  // resolves to the in-flight step. (`_wfClose` at `:400` also matches on
  // `name+visits+status` when replacing placeholders.) `_wfAppendSubflowRun`
  // keeps the visits guard because forEach subflow re-entry + the pre-run
  // placeholder pattern (mdr-remediate-4 H2) are closer to that method's
  // failure surface. If a future flow ever allows re-entrant / concurrent
  // agent steps (two same-named records both "running"), retrofit the
  // `name + visits + status === "running"` triple match from
  // `_wfAppendSubflowRun`.
  _onAgentStepUpdate({ stepName, logFile, promptFile, sessionId }) {
    if (!this.workflow) return;
    const steps = this.workflow.steps;
    for (let i = steps.length - 1; i >= 0; i--) {
      if (steps[i].name === stepName && steps[i].status === "running") {
        if (logFile) steps[i].logFile = basename(logFile);
        if (promptFile) steps[i].promptFile = basename(promptFile);
        if (sessionId) steps[i].sessionId = sessionId;
        this._writeWorkflow();
        break;
      }
    }
  }

  // draft-robustness WI5 — append a subflow run record to the placeholder entry
  // of the currently-running forEach step so the main run-state.json reflects
  // progress even if the parent process is killed mid-forEach. Mirrors the
  // "find the running record + patch + _writeWorkflow" pattern of
  // _onAgentStepUpdate above, but additionally requires `visits` to match:
  // the same stepName can be re-entered (visitCounts accumulates), and when
  // two visits both have status:"running" placeholders a stepName-only match
  // would write into the wrong entry. See design/draft-robustness-design.md
  // §4.5.1.
  _wfAppendSubflowRun(stepName, visits, run) {
    if (!this.workflow) return;
    const steps = this.workflow.steps;
    for (let i = steps.length - 1; i >= 0; i--) {
      if (steps[i].name === stepName && steps[i].visits === visits && steps[i].status === "running") {
        if (!Array.isArray(steps[i].subflowRuns)) steps[i].subflowRuns = [];
        steps[i].subflowRuns.push(run);
        this.workflow.updatedAt = new Date().toISOString();
        this._writeWorkflow();
        return;
      }
    }
  }

  _emitEvent(type, data = {}) {
    if (!this.eventsFile) return;
    try {
      const event = {
        type,
        ts: new Date().toISOString(),
        missionName: this.missionName || null,
        runId: this.runId || null,
        flowName: this.flow?.name || null,
        ...data,
      };
      appendFileSync(this.eventsFile, JSON.stringify(event) + "\n");
    } catch {}
  }

  // One-time migration: strip any residual `workflow` node from the mission JSON
  // config so runtime state no longer pollutes the (read-only) config file (FSD §4.2).
  _cleanMissionJsonWorkflow() {
    const cfg = this.delegates.config || {};
    if (!cfg.missionsDir || !cfg.missionName) return;
    const file = resolve(cfg.missionsDir, `${cfg.missionName}.json`);
    try {
      if (!existsSync(file)) return;
      const obj = JSON.parse(readFileSync(file, "utf8"));
      if (obj && obj.workflow) {
        delete obj.workflow;
        const tmp = file + ".tmp";
        writeFileSync(tmp, JSON.stringify(obj, null, 2) + "\n", "utf8");
        renameSync(tmp, file);
      }
    } catch {}
  }

   // mdo-3 Phase 3: terminal-state postmortem hook removed — postmortem is
   // now manual-only via `node src/main.js analyze [runId]`.
   async _result(status, stepCount, marker) {
      const finalStatus = this._reconcileTerminal(status);
      try { this._finalizeWorkflow(finalStatus); } catch {}
      try {
        this._emitEvent("run_completed", {
          status: finalStatus,
          stepCount,
          elapsed: this.startTime ? durationStr(Date.now() - this.startTime) : "N/A",
          marker: marker || null,
        });
      } catch {}

      return {
        status: finalStatus,
        stepCount,
        marker: marker || null,
        elapsed: this.startTime ? durationStr(Date.now() - this.startTime) : "N/A",
        history: this.logEntries,
      };
    }

  // Terminal reconciliation (§1.4-4): a fully-completed mission must not be
  // reported as failed just because a single terminal-step marker was lost (e.g.
  // a typo'd result tag). Before emitting a failure-ish terminal status, cross-
  // check the ground truth on disk: roadmap 100% done AND no active/draft plans
  // AND no open audits ⇒ downgrade to "completed". Opt-in per flow via
  // `reconcileOnTerminal: true` so SUBflows (plan-execution / deep-audit-loop)
  // — which legitimately fail — are never masked.
  _reconcileTerminal(status) {
    const FAILISH = new Set(["failed", "max_cycles", "max_retries", "max_total_steps", "ping_pong"]);
    if (!this.flow?.reconcileOnTerminal || !FAILISH.has(status)) return status;
    try {
      const ap = this.expressionFuncs?.activePlans?.() || [];
      const dp = this.expressionFuncs?.draftPlans?.() || [];
      const oa = this.expressionFuncs?.openAudits?.() || [];
      if (ap.length || dp.length || oa.length) return status;

      const vars = this.delegates?.vars || {};
      const roadmapPath = vars.roadmapPath;
      if (!roadmapPath) return status;
      const projectRoot = vars.projectRoot || this.delegates?.config?.projectRoot || ".";
      const abs = isAbsolute(roadmapPath) ? roadmapPath : resolve(projectRoot, roadmapPath);
      if (!existsSync(abs)) return status;
      if (!roadmapAllDone(readFileSync(abs, "utf8"))) return status;

      this._log(
        `  reconciliation: engine terminal "${status}" but roadmap is 100% done, ` +
        `no active/draft plans, no open audits → downgrading to "completed"`,
      );
      try {
        this._emitEvent("reconciled", {
          from: status,
          to: "completed",
          reason: "roadmap complete, no pending plans or open audits",
        });
      } catch {}
      return "completed";
    } catch (e) {
      this._log(`  reconciliation check failed (${e.message}) — keeping "${status}"`);
      return status;
    }
  }

  // WI4 — DRAFT_PLANS audit-gate (design §4.2.3 B-2 / §4.2.4 truth table).
  // Called from transition resolution when a step would `goto` the flow's
  // `auditEntry` (e.g. DRAFT_PLANS nothing → DEEP_AUDIT). Returns true only when
  // the per-run audit budget is exhausted AND there is no remaining work
  // (active plans / open audits) — in that case the run is allowed to complete
  // WITHOUT entering another audit round. Zero-intrusion on flows without an
  // `auditEntry` (returns false unconditionally).
  //
  // Truth-table row 4 (§4.2.4): if open audits exist, never short-circuit even
  // when the round budget is exhausted — those issues must be allowed to surface
  // through DEEP_AUDIT → DRAFT_PLANS → REVIEW_PLANS rather than be silently
  // dropped.
  _shouldCompleteOnAuditQuota(currentStep, marker, transition) {
    const auditEntry = this.flow?.auditEntry;
    if (!auditEntry) return false;
    if (!transition || transition.goto !== auditEntry) return false;
    if (marker !== "nothing") return false;
    const ap = this.expressionFuncs?.activePlans?.() || [];
    const oa = this.expressionFuncs?.openAudits?.() || [];
    const round = (this.workflow && this.workflow.auditRound) || 0;
    const max = this.flow?.maxAuditRounds ?? 0;
    // mdc-1 (convergence R2): early clean short-circuit. Once DEEP_AUDIT has run
    // at least once (auditRound >= 1) and left NO remediation work behind — no
    // active plans AND no open audits (P2-only audits self-mark `triaged`, which
    // openAudits() excludes) — the mission is done. We no longer wait for the
    // full `round >= maxAuditRounds` budget: that requirement made every
    // enter-pure-audit-mode run burn all rounds even when clean (the deleted
    // legacy DRAFT_PLANS `done` exit used to end on round 1). The
    // `round >= maxAuditRounds` ceiling still fires independently in run() as the
    // safety upper bound when audits keep surfacing P0/P1 work. The `auditRound
    // >= 1` guard preserves "audit at least once before completing" (cold start
    // with an already-empty roadmap still enters DEEP_AUDIT once first).
    return max > 0 && round >= 1 && ap.length === 0 && oa.length === 0;
  }

  _templateVar(str, vars) {
    if (typeof str !== "string") return str;
    const resolved = resolveTemplateVars(str, vars);
    if (/\{\{(\w+)\}\}/.test(resolved)) {
      const m = resolved.match(/\{\{(\w+)\}\}/);
      this._log(`  WARNING: unresolved template variable {{${m[1]}}}`);
    }
    return resolved;
  }

  _buildPrompt(stepName, stepDef) {
    let prompt = stepDef.prompt || "";
    const allVars = { ...(this.delegates.vars || {}), ...Object.fromEntries(this.flowVars) };
    prompt = this._templateVar(prompt, allVars);

    const buf = this.appendBuffers.get(stepName);
    if (buf) {
      prompt += "\n" + buf;
    }
    // mdr-3 Phase 3: enforce the prompt size guard AFTER all appends are folded
    // in, so an unbounded closure-audit feedback block (full npm-test stdout)
    // cannot inflate the next step's prompt past the model token budget. See
    // boundPromptSize for the threshold rationale.
    return boundPromptSize(prompt, {
      onTruncate: (info) => {
        this._log(`  [PROMPT_GUARD] ${stepName} prompt ${info.originalBytes}B → ${info.boundedBytes}B (dropped ~${info.droppedBytes}B middle)`);
        try {
          this._emitEvent("prompt_truncated", { step: stepName, originalBytes: info.originalBytes, boundedBytes: info.boundedBytes, droppedBytes: info.droppedBytes });
        } catch {}
      },
    });
  }

  _extractFlowVars(text) {
    const matches = [...text.matchAll(/<FLOW_VARS>([\s\S]*?)<\/FLOW_VARS>/g)];
    if (matches.length === 0) return {};
    const inner = matches[matches.length - 1][1];
    const vars = {};
    const re = /<(\w+)>([^<]*)<\/\1>/g;
    let match;
    while ((match = re.exec(inner)) !== null) {
      vars[match[1]] = match[2].trim();
    }
    return vars;
  }

  _fileExists(path) {
    try { return existsSync(path); } catch { return false; }
  }

  _evaluateCondition(condition, vars) {
    if (condition === undefined || condition === null) return true;
    if (typeof condition === "boolean") return condition;
    if (typeof condition === "string") {
      return !!evaluateExpression(condition, vars, this.expressionFuncs);
    }
    if (typeof condition === "object") {
      const val = vars[condition.var] !== undefined ? vars[condition.var] : "";
      const strVal = String(val);
      if (condition.present) return strVal !== "";
      if (condition.empty) return strVal === "";
      if (condition.eq !== undefined) return strVal === String(condition.eq);
      if (condition.ne !== undefined) return strVal !== String(condition.ne);
      return strVal !== "";
    }
    return true;
  }

  _markerAliases() {
    return this.flow.markerAliases || {};
  }

  _markPingPongRetry() {
    for (let i = this.pingPongHistory.length - 1; i >= 0; i--) {
      this.pingPongHistory[i].viaRetry = true;
    }
  }

  _tryAliasMarker(marker, transitions) {
    if (transitions[marker]) return marker;
    const alias = this._markerAliases()[marker];
    if (alias && transitions[alias]) return alias;
    if (marker && typeof marker === "string") {
      const lower = marker.toLowerCase();
      for (const key of Object.keys(transitions)) {
        if (key.toLowerCase() === lower) return key;
      }
    }
    return null;
  }

  async _executeAgentStep(stepName, stepDef, sessionId) {
    const prompt = this._buildPrompt(stepName, stepDef);
    // dre-d7 G2: thread per-step timeoutMs (agent executor watchdog) + resultTag
    // (so buildErrorTail's L010 tag-absent diagnostic detects the step's CUSTOM
    // tag). 5th arg (modelOverride) left undefined to keep the default model;
    // 6th opts is absent on legacy callers (main.js brief/draft) → defaults {}.
    const agentOpts = { timeoutMs: stepDef.timeoutMs, resultTag: stepDef.resultTag };
    const result = await this.delegates.runAgent(stepName, prompt, stepDef.system || "", sessionId, undefined, agentOpts);
    if (result && result.sessionId) this.lastSessionId = result.sessionId;

    if (!result || !result.text) {
      // mdr-1: propagate exit code / errorTail / stderrTail so the main-loop
      // failure diagnosis (and Phase 2/3 classification) can read them even on
      // the empty-output short-circuit path (previously dropped here).
      return { marker: null, vars: {}, ok: !!result?.ok, text: result?.text || "", sessionId: result?.sessionId || null, exitCode: result?.exitCode, errorTail: result?.errorTail, stderrTail: result?.stderrTail, logFile: result?.logFile || null };
    }

    // mdr-2 Phase 1: strip ANSI escape sequences + stray control chars from the
    // agent output ONCE, before every extraction pass (strict/tolerant/fuzzy,
    // the header-only body check, and the LLM fallback re-extract chain). Real
    // CLI output is frequently log-colored (`\x1b[31m...\x1b[0m`) and those CSI
    // bytes can sit inside a `<TAG>value</TAG>` capture, defeating the strict /
    // tolerant `[^<]+` matchers (memory L009). The raw `result.text` is still
    // returned for diagnostics; only extraction reads the cleaned text.
    const cleanText = stripAnsiControl(result.text);

    const vars = this._extractFlowVars(cleanText);

    let marker = null;
    const rTag = stepDef.resultTag || "AI_STEP_RESULT";
    marker = extractTag(cleanText, rTag);
    // OPT-2: strict extract missed — try tolerant regex (case/whitespace/fence)
    // before resorting to the expensive runParseAgent LLM fallback.
    if (!marker) {
      marker = extractTagTolerant(cleanText, rTag);
    }

    // Fuzzy: tag-name typo (e.g. <AIE_STEP_RESULT> or mixed-case <Ai_STEP_RESULT>).
    // Scan for any <TAG>value</TAG> where value is a known marker. Avoids the LLM
    // fallback for common AI typos.
    if (!marker) {
      const transitions = stepDef.transitions || {};
      const aliases = this.flow.markerAliases || {};
      const validValues = [...new Set([...Object.keys(transitions), ...Object.keys(aliases)])];
      const fuzzy = extractTagFuzzy(cleanText, validValues);
      if (fuzzy) {
        this._log(`  fuzzy tag match: extracted "${fuzzy}" from typo'd tag name`);
        marker = fuzzy;
      }
    }

    // mdr-1 Phase 2: guard before the expensive parse-agent fallback. When the
    // output is header-only / extremely short there is nothing for the parse
    // agent to infer a marker from — short-circuit to a null marker instead of
    // wasting a model call on noise (memory L001: header-only logs repeatedly
    // reached runParseAgent with no parseable content).
    if (!marker) {
      const body = bodyAfterHeader(cleanText);
      if (body.length < PARSE_MIN_BODY_CHARS) {
        this._log(`  output is header-only/empty (${body.length} chars body) — skipping parse fallback`);
        return { marker: null, vars, ok: result.ok, text: result.text, sessionId: result.sessionId || null, exitCode: result.exitCode, errorTail: result.errorTail, stderrTail: result.stderrTail, logFile: result.logFile || null };
      }
    }

    if (!marker && this.delegates.runParseAgent) {
      const parsePrompt = [
        `No <${rTag}> tag found in output. Read the AI output below and infer the result.`,
        `Expected values: ${Object.keys(stepDef.transitions || {}).join(", ")}`,
        `Output only <${rTag}>value</${rTag}> format, nothing else.`,
        ``,
        `AI output:`,
        cleanText,
      ].join("\n");
      const retry = await this.delegates.runParseAgent(
        `parse-${rTag}`, parsePrompt, stepDef.system || "",
      );
      // The parse agent can itself emit a typo'd / mismatched tag, and its output
      // may carry ANSI coloring. Strip + reuse the full strict → tolerant → fuzzy
      // chain instead of a bare strict extract, so the fallback is not defeated by
      // the same class of tag typo / ANSI noise it is meant to cure (mdr-2 Phase 1).
      const retryClean = stripAnsiControl(retry?.text || "");
      marker = extractTag(retryClean, rTag) || extractTagTolerant(retryClean, rTag);
      if (!marker) {
        const transitions = stepDef.transitions || {};
        const aliases = this.flow.markerAliases || {};
        const validValues = [...new Set([...Object.keys(transitions), ...Object.keys(aliases)])];
        marker = extractTagFuzzy(retryClean, validValues);
      }
    }

    if (marker) {
      const aliased = this._tryAliasMarker(marker, stepDef.transitions || {});
      if (aliased) marker = aliased;
    }

    const transitions = stepDef.transitions || {};

    if (marker) {
      if (!transitions[marker]) {
        marker = await this._runCorrectionAgent(
          marker, result.text, rTag, transitions, stepDef, this.lastSessionId,
        );
      }
    }

    // mdr-2 Phase 2: when strict parse missed but a fallback (tolerant/fuzzy/LLM
    // /correction) recovered a marker that is VALID for the current step's
    // transitions, the marker is the authoritative step outcome — the agent did
    // emit a usable result, so flip ok=true and take the normal marker
    // transition instead of onError. `ok` is otherwise bound to the raw
    // subprocess exit code and can be false even when a valid marker was
    // emitted (non-zero exit but recoverable output), which previously misrouted
    // a genuinely-completed step to onError/step_failed. The guard is strictly
    // "transition-valid marker": a null or invalid marker keeps the original ok
    // so real failures still route to onError (memory L003). Recovering
    // arbitrary noise never sets ok=true (Decision: 否决"恢复出任意 marker 即 ok")。
    const resolvedOk = (marker && transitions[marker]) ? true : result.ok;

    return { marker, vars, ok: resolvedOk, text: result.text, sessionId: result.sessionId || null, logFile: result.logFile || null, exitCode: result.exitCode, errorTail: result.errorTail, stderrTail: result.stderrTail };
  }

  async _runCorrectionAgent(marker, resultText, resultTag, transitions, stepDef, sessionId) {
    const maxRetries = stepDef.onUnknownMaxRetries ?? 2;
    let currentMarker = marker;

    for (let i = 0; i < maxRetries; i++) {
      const validValues = Object.keys(transitions).join(", ");
      this._log(`  marker "${currentMarker}" not in transitions, correction retry ${i + 1}/${maxRetries} (session=${sessionId ? sessionId.slice(0, 20) + "..." : "none"})`);

      const correctionPrompt = [
        `The value "${currentMarker}" in the <${resultTag}> tag from your last output is not valid.`,
        `Valid values are: ${validValues}`,
        `Output only <${resultTag}>valid_value</${resultTag}>, nothing else.`,
      ].join("\n");

      try {
        // OPT-3: correction is a lightweight classification task — route it
        // through runParseAgent (cheap parseModel) instead of the main runAgent.
        const corrected = await this.delegates.runParseAgent(
          `correct-${i + 1}`, correctionPrompt, stepDef.system || "", sessionId,
        );
        if (corrected && corrected.text) {
          const newMarker = extractTag(corrected.text, resultTag);
          if (newMarker) {
            const aliasedNew = this._tryAliasMarker(newMarker, transitions);
            if (aliasedNew) {
              this._log(`  corrected marker: ${newMarker} → ${aliasedNew}`);
              return aliasedNew;
            }
          }
        }
      } catch (e) {
        this._log(`  correction retry failed: ${e.message}`);
      }
    }

    return currentMarker;
  }

  async _executeToolStep(stepName, stepDef) {
    const command = this._templateVar(stepDef.command || "", this.delegates.vars || {});
    const timeout = stepDef.timeout || 0;
    const delegateResult = await this.delegates.runTool(stepName, command, { timeout });
    return {
      marker: delegateResult.ok ? "pass" : "fail",
      ok: true,
      vars: {},
      text: delegateResult.logFile || "",
    };
  }

  async _executeScriptStep(stepName, stepDef) {
    // dre-d7 G3: in-process wall-clock envelope for script steps. Script steps
    // run in-process (engine.js:835 `await stepDef.run(...)`) with NO executor
    // watchdog — a buggy/omitted internal timeout (no AbortController to cancel)
    // could hang the whole engine process. When stepDef.timeoutMs is a positive
    // finite number, race stepDef.run against a sleep; on timeout return
    // marker "fail" + a transcript reason (Decision: fail semantics ≈ "no
    // evidence gathered", EVALUATE reads the transcript). When absent → no race
    // (backward compatible, relies on the script's internal timeout). NB4: a
    // late stepDef.run() rejection AFTER the race settled on the timeout
    // sentinel would surface as unhandledRejection — attach a no-op .catch to
    // the run promise to swallow it.
    const timeoutMs = stepDef.timeoutMs;
    let ret;
    if (Number.isFinite(timeoutMs) && timeoutMs > 0) {
      const TIMEOUT_SENTINEL = Symbol("script_step_timeout");
      const runP = Promise.resolve().then(() => stepDef.run(this.delegates, this.flowVars));
      runP.catch(() => {}); // NB4: swallow late rejection if timeout wins
      const raced = await Promise.race([runP, sleep(timeoutMs).then(() => TIMEOUT_SENTINEL)]);
      if (raced === TIMEOUT_SENTINEL) {
        const failMarker = "fail";
        const failText = `script step ${stepName} exceeded timeoutMs=${timeoutMs} (in-process wall-clock envelope)`;
        const logFile = this._writeScriptLog(stepName, failText, failMarker);
        return { marker: failMarker, ok: true, vars: {}, text: failText, logFile };
      }
      ret = raced;
    } else {
      ret = await stepDef.run(this.delegates, this.flowVars);
    }
    let marker, vars, text;
    if (ret && typeof ret === "object" && ret.marker !== undefined) {
      marker = ret.marker;
      vars = ret.vars || {};
      text = ret.text || String(ret.marker);
    } else {
      marker = ret;
      vars = {};
      text = String(ret);
    }
    // Write script output to a log file so it's visible in the Log Viewer.
    // Without this, script steps (e.g. CLOSURE_SCRIPT_CHECK) have no log to
    // inspect when they fail — the reason is lost.
    const logFile = this._writeScriptLog(stepName, text, marker);
    return { marker, ok: true, vars, text, logFile };
  }

  async _executeScriptStepWithOverride(stepName, stepDef) {
    if (this.delegates.runScript) {
      const result = await this.delegates.runScript(stepName, stepDef);
      if (result !== undefined) {
        if (typeof result === "string") {
          return { marker: result, ok: true, vars: {}, text: String(result) };
        }
        if (typeof result === "object") {
          return {
            marker: result.marker,
            ok: true,
            vars: result.vars || {},
            text: result.text || String(result.marker),
          };
        }
      }
    }
    return this._executeScriptStep(stepName, stepDef);
  }

  _resolveForEachItems(forEachExpr, stepName) {
    if (isExpression(forEachExpr)) {
      const result = evaluateExpression(forEachExpr, this._allVars(), this.expressionFuncs);
      const items = Array.isArray(result) ? result : [];
      this._log(`  ${stepName}: forEach expression "${forEachExpr}" → ${items.length} item(s)`);
      return items;
    }
    const listRaw = this._allVars()[forEachExpr];
    if (Array.isArray(listRaw)) return listRaw;
    if (typeof listRaw === "string") {
      try { return JSON.parse(listRaw); } catch { return listRaw.split(",").map(s => s.trim()).filter(Boolean); }
    }
    return [];
  }

  async _executeForEach(stepName, stepDef) {
    const items = this._resolveForEachItems(stepDef.forEach, stepName);
    if (items.length === 0) {
      this._log(`  ${stepName}: forEach "${stepDef.forEach}" resolved to empty list → all_complete`);
      return { ok: true, marker: "all_complete", vars: {}, text: "all_complete" };
    }

    let completed = 0, failed = 0;
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      this._log(`  ${stepName}: forEach item ${i + 1}/${items.length}`);
      this.flowVars.set("forEachItem", item);
      this.flowVars.set("forEachIndex", i);
      this.flowVars.set("forEachTotal", items.length);

      let iterResult;
      try {
        if (stepDef.type === "agent") {
          iterResult = await this._executeAgentStep(stepName, stepDef, null);
        } else if (stepDef.type === "tool") {
          iterResult = await this._executeToolStep(stepName, stepDef);
        } else if (stepDef.type === "script") {
          iterResult = await this._executeScriptStepWithOverride(stepName, stepDef);
        } else if (stepDef.type === "group") {
          iterResult = await this._executeGroupStep(stepName, stepDef);
        } else {
          iterResult = { ok: false, marker: "error", vars: {} };
        }
      } catch (err) {
        this._log(`  ${stepName}: forEach item ${i + 1} error: ${err.message}`);
        iterResult = { ok: false, marker: "error", vars: {} };
      }

      if (iterResult.ok) {
        completed++;
      } else {
        failed++;
        if (stepDef.onItemError && stepDef.onItemError.stopOnError) break;
      }
    }

    this.flowVars.delete("forEachItem");
    this.flowVars.delete("forEachIndex");
    this.flowVars.delete("forEachTotal");

    let marker;
    if (failed === 0) marker = "all_complete";
    else if (completed === 0) marker = "all_failed";
    else marker = "some_failed";
    this._log(`  ${stepName}: forEach done (${completed} completed, ${failed} failed) → ${marker}`);
    return { ok: true, marker, vars: {}, text: marker };
  }

  async _executeSubflowStep(stepName, stepDef) {
    const flowName = this._templateVar(stepDef.flow || "", this._allVars());
    const flowDef = await this.delegates.loadSubFlow(flowName);
    if (!flowDef) throw new Error(`Subflow not found: ${flowName}`);

    let baseArgs = {};
    if (stepDef.flowArgs) {
      const allVars = this._allVars();
      for (const [k, v] of Object.entries(stepDef.flowArgs)) {
        baseArgs[k] = this._templateVar(String(v), allVars);
      }
    }

    if (stepDef.forEach) {
      const items = this._resolveForEachItems(stepDef.forEach, stepName);
      if (items.length === 0) {
        this._log(`  subflow ${stepName}: forEach "${stepDef.forEach}" resolved to empty list → all_complete`);
        return { ok: true, marker: "all_complete", vars: {}, text: "all_complete" };
      }

      const visit = this.visitCounts.get(stepName) || 1;
      let completed = 0, failed = 0;
      const aggregatedVars = {};
      const subflowRuns = [];

      const concurrency = Math.max(1, Number(stepDef.concurrency) || 1);

      if (concurrency === 1) {
        for (let i = 0; i < items.length; i++) {
          const item = items[i];
          this._log(`  subflow ${stepName}: forEach item ${i + 1}/${items.length}`);
          // Re-resolve flowArgs per iteration so forEachItem/forEachIndex/forEachTotal template vars work
          const iterArgs = {};
          if (stepDef.flowArgs) {
            const iterVars = { ...this._allVars(), forEachItem: item, forEachIndex: i, forEachTotal: items.length };
            for (const [k, v] of Object.entries(stepDef.flowArgs)) {
              iterArgs[k] = this._templateVar(String(v), iterVars);
            }
          }
          const childVars = { ...iterArgs, forEachItem: item, forEachIndex: i, forEachTotal: items.length, _subflowId: `${stepName}-${visit}-${i}` };
          const { childResult, childFlowVars, subflowFile } = await this._runChildSubflow(flowDef, childVars);
          Object.assign(aggregatedVars, childFlowVars);
          subflowRuns.push({ forEachIndex: i, forEachItem: item, file: subflowFile ? basename(subflowFile) : null, status: childResult.status });
          this._wfAppendSubflowRun(stepName, visit, subflowRuns[subflowRuns.length - 1]);
          this._log(`  subflow ${stepName}: forEach item ${i + 1} → ${childResult.status}`);
          if (childResult.status === "completed") {
            completed++;
          } else {
            failed++;
            if (stepDef.onItemError && stepDef.onItemError.stopOnError) break;
          }
        }
      } else {
        // Sliding-window dispatcher: keeps <=concurrency items in flight; as soon
        // as one resolves the next pending item dispatches (no batch barrier).
        // Wall clock approaches the concurrency lower bound max(sum_durations / concurrency, max_item).
        const runItem = async (i) => {
          const item = items[i];
          this._log(`  subflow ${stepName}: forEach item ${i + 1}/${items.length} dispatch`);
          const iterArgs = {};
          if (stepDef.flowArgs) {
            const iterVars = { ...this._allVars(), forEachItem: item, forEachIndex: i, forEachTotal: items.length };
            for (const [k, v] of Object.entries(stepDef.flowArgs)) {
              iterArgs[k] = this._templateVar(String(v), iterVars);
            }
          }
          const childVars = { ...iterArgs, forEachItem: item, forEachIndex: i, forEachTotal: items.length, _subflowId: `${stepName}-${visit}-${i}` };
          const { childResult, childFlowVars, subflowFile } = await this._runChildSubflow(flowDef, childVars);
          return { i, item, childResult, childFlowVars, subflowFile };
        };

        const recordResult = (r) => {
          Object.assign(aggregatedVars, r.childFlowVars);
          subflowRuns.push({ forEachIndex: r.i, forEachItem: r.item, file: r.subflowFile ? basename(r.subflowFile) : null, status: r.childResult.status });
          this._wfAppendSubflowRun(stepName, visit, subflowRuns[subflowRuns.length - 1]);
          this._log(`  subflow ${stepName}: forEach item ${r.i + 1} → ${r.childResult.status}`);
          if (r.childResult.status === "completed") {
            completed++;
          } else {
            failed++;
            if (stepDef.onItemError && stepDef.onItemError.stopOnError) stopRequested = true;
          }
        };

        let nextIndex = 0;
        let stopRequested = false;
        const inflight = new Set();

        const dispatch = () => {
          if (stopRequested) return;
          while (inflight.size < concurrency && nextIndex < items.length) {
            const idx = nextIndex++;
            const p = runItem(idx).then((r) => {
              inflight.delete(p);
              recordResult(r);
              // a slot freed -> try to start the next pending item immediately
              dispatch();
            });
            inflight.add(p);
          }
        };

        this._log(`  subflow ${stepName}: forEach sliding-window concurrency=${concurrency} (items=${items.length})`);
        dispatch();
        // Drain: wait for in-flight items; dispatch() recurses on each resolve,
        // refilling freed slots until stopRequested or every item has started.
        while (inflight.size > 0) {
          await Promise.allSettled([...inflight]);
        }
        // Results were collected in resolve order; restore forEachIndex order
        // (contract: subflowRuns stays ordered by forEachIndex for monitor.js / consumers).
        subflowRuns.sort((a, b) => a.forEachIndex - b.forEachIndex);
      }

      let marker;
      if (failed === 0) marker = "all_complete";
      else if (completed === 0) marker = "all_failed";
      else marker = "some_failed";
      this._log(`  subflow ${stepName}: forEach done (${completed} completed, ${failed} failed) → ${marker}`);
      return { ok: true, marker, vars: aggregatedVars, text: marker, subflowRuns };
    }

    const visit = this.visitCounts.get(stepName) || 1;
    const childArgs = { ...baseArgs, _subflowId: `${stepName}-${visit}-0` };
    // mdr-remediate-4 H2 — extend §4.5's incremental persistence to the
    // non-forEach (single-child) branch. Write a status:"running" placeholder
    // BEFORE awaiting the child so a mid-child SIGKILL leaves the main
    // run-state.json reflecting "in progress" instead of the initial `[]`.
    // After the child returns, run()'s caller (~:1799) builds completedMeta
    // from result.subflowRuns and _wfClose replaces this placeholder record
    // with the terminal-state record (no duplicate, no stale running entry).
    this._wfAppendSubflowRun(stepName, visit, { forEachIndex: 0, forEachItem: null, file: null, status: "running" });
    const { childResult, childFlowVars, subflowFile } = await this._runChildSubflow(flowDef, childArgs);
    const marker = childResult.status === "completed" ? "complete" : "failed";
    this._log(`  subflow ${stepName}: child ${childResult.status} → ${marker}`);
    return { ok: true, marker, vars: childFlowVars, text: marker, subflowRuns: [{ forEachIndex: 0, forEachItem: null, file: subflowFile ? basename(subflowFile) : null, status: childResult.status }] };
  }

  _allVars() {
    return { ...(this.delegates.vars || {}), ...Object.fromEntries(this.flowVars) };
  }

  async _runChildSubflow(flowDef, extraVars) {
    const parentDelegates = this.delegates;
    const subflowId = extraVars._subflowId || null;
    const childVars = { ...(parentDelegates.vars || {}), ...extraVars };
    const parentConfig = parentDelegates.config || {};
    const childConfig = subflowId ? { ...parentConfig, subflowId, isSubflow: true } : { ...parentConfig, isSubflow: true };
    const childDelegates = {
      ...parentDelegates,
      vars: childVars,
      config: childConfig,
      callLog: parentDelegates.callLog,
    };
    const childEngine = new FlowEngine(flowDef, childDelegates);

    // Wrap runAgent so in-flight updates (logFile/sessionId via onSpawn) route
    // to the CHILD engine, not the parent. Without this, the runner reads
    // config.onStepUpdate (bound to the parent engine at main.js:752), which
    // searches the parent's workflow.steps for the stepName — but the
    // subflow's step names (EXECUTE, BUILD_VERIFY, MULTI_AUDIT, …) aren't in
    // the parent's workflow, so logFile/sessionId updates are silently dropped.
    // Result: the dashboard showed no log button and no session button until
    // the step completed and _wfClose persisted them. The wrapper injects the
    // child engine's _onAgentStepUpdate via opts.onStepUpdate (runner.js
    // prefers opts over config), so each subflow step's live updates land in
    // the child's run-state-<subflowId>.json immediately.
    const parentRunAgent = typeof parentDelegates.runAgent === "function"
      ? parentDelegates.runAgent.bind(parentDelegates)
      : null;
    if (parentRunAgent) {
      childDelegates.runAgent = (stepName, prompt, system, sessionId, modelOverride, opts) =>
        parentRunAgent(stepName, prompt, system, sessionId, modelOverride, {
          ...(opts || {}),
          onStepUpdate: (payload) => childEngine._onAgentStepUpdate(payload),
        });
    }

    const childResult = await childEngine.run();
    if (childResult.history) {
      for (const line of childResult.history) {
        this._log(`  [child] ${line}`);
      }
    }
    const subflowFile = childEngine._workflowFile();
    return {
      childResult,
      childFlowVars: Object.fromEntries(childEngine.flowVars),
      subflowFile,
    };
  }

  async _executeSubStep(stepName, stepDef) {
    if (stepDef.type === "agent") {
      return await this._executeAgentStep(stepName, stepDef, null);
    }
    if (stepDef.type === "tool") {
      return await this._executeToolStep(stepName, stepDef);
    }
    if (stepDef.type === "script") {
      return await this._executeScriptStepWithOverride(stepName, stepDef);
    }
    if (stepDef.type === "subflow") {
      return await this._executeSubflowStep(stepName, stepDef);
    }
    throw new Error(`Unknown sub-step type: ${stepDef.type}`);
  }

  async _executeGroupStep(groupName, groupDef) {
    const maxRounds = groupDef.maxRounds || 3;
    const onExhausted = groupDef.onExhausted || "fail";
    const subSteps = groupDef.steps;
    const firstStepName = Object.keys(subSteps)[0];
    const accumulatedVars = {};

    for (let round = 1; round <= maxRounds; round++) {
      this._log(`  group ${groupName} (round ${round}/${maxRounds})`);
      let currentSub = firstStepName;

      while (true) {
        const subDef = subSteps[currentSub];
        if (!subDef) {
          this._log(`  group ${groupName}: unknown sub-step ${currentSub}`);
          return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
        }

        let result;
        try {
          result = await this._executeSubStep(`${groupName}.${currentSub}`, subDef);
        } catch (err) {
          this._log(`  group ${groupName}.${currentSub} error: ${err.message}`);
          const onError = subDef.onError;
          if (onError && onError.exit) {
            return { ok: true, marker: onError.exit, vars: accumulatedVars, text: onError.exit };
          }
          return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
        }

        if (!result.ok) {
          this._log(`  group ${groupName}.${currentSub} subprocess failed`);
          const onError = subDef.onError;
          if (onError && onError.exit) {
            return { ok: true, marker: onError.exit, vars: accumulatedVars, text: result.text || onError.exit };
          }
          if (onError && onError.goto === "_retry") {
            break;
          }
          return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
        }

        if (result.vars) {
          for (const [k, v] of Object.entries(result.vars)) {
            this.flowVars.set(k, v);
          }
          Object.assign(accumulatedVars, result.vars);
        }

        let marker = result.marker;
        if (!marker) {
          this._log(`  group ${groupName}.${currentSub} marker not found`);
          return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
        }

        if (marker) {
          const aliased = this._tryAliasMarker(marker, subDef.transitions);
          if (aliased) marker = aliased;
        }

        this._log(`  group ${groupName}.${currentSub} → ${marker}`);

        const transition = subDef.transitions[marker];
        if (!transition) {
          this._log(`  group ${groupName}.${currentSub}: no transition for marker "${marker}"`);
          return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
        }

        if (transition.exit) {
          this._log(`  group ${groupName} exit: ${transition.exit}`);
          const exitText = result.text || transition.exit;
          return { ok: true, marker: transition.exit, vars: accumulatedVars, text: exitText };
        }

        if (transition.goto === "_retry") {
          break;
        }

        if (transition.goto && subSteps[transition.goto]) {
          currentSub = transition.goto;
          continue;
        }

        this._log(`  group ${groupName}.${currentSub}: invalid sub-transition ${JSON.stringify(transition)}`);
        return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
      }
    }

    this._log(`  group ${groupName} exhausted (${maxRounds} rounds) → ${onExhausted}`);
    return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
  }

  _formatAppend(append, fromStep, result) {
    if (!append) return "";
    if (append === true) {
      return "\n\n" + (result.text || "");
    }
    if (typeof append === "string") {
      return "\n\n" + append;
    }
    if (typeof append === "object") {
      let content = "";
      if (append.extract) {
        content = extractXmlBlock(result.text || "", append.extract) || result.text || "";
      } else {
        content = result.text || "";
      }
      const template = append.template || "${output}";
      let text = "\n\n" + template.replace(/\$\{output\}/g, content)
        .replace(/\$\{logFile\}/g, result.logFile || "N/A");
      text = this._templateVar(text, this._allVars());
      return text;
    }
    return "";
  }

  _handleRetry(fromStep, transition, stepDef, result) {
    const targetStep = transition.retry;
    const maxRetries = transition.maxRetries || stepDef.maxRetries || 3;
    const retryKey = `${fromStep}→${targetStep}`;
    const count = (this.retryCounts.get(retryKey) || 0) + 1;
    this.retryCounts.set(retryKey, count);

    this._log(`  retry ${retryKey} (${count}/${maxRetries})`);

    if (count > maxRetries) {
      const onMax = stepDef.onMaxRetries || transition.onMaxRetries || { done: "max_retries" };
      this._log(`  maxRetries exceeded for ${retryKey} → ${JSON.stringify(onMax)}`);
      this._emitEvent("limit_hit", {
        limitType: "max_retries",
        step: fromStep,
        count,
        max: maxRetries,
      });
      return onMax;
    }

    const appendText = this._formatAppend(transition.append, fromStep, result);
    if (appendText) {
      const existing = this.appendBuffers.get(targetStep) || "";
      if (count > 1) {
        this.appendBuffers.set(targetStep, existing + "\n───────────────\n" + appendText);
      } else {
        this.appendBuffers.set(targetStep, appendText);
      }
    }

    return { goto: targetStep };
  }

  async run(entryOverride) {
    this.startTime = Date.now();
    const cfg = this.delegates.config || {};
    this.eventsFile = cfg.runDir ? resolve(cfg.runDir, "events.jsonl") : null;
    this.missionName = cfg.missionName || null;
    this.runId = cfg.runDir ? basename(cfg.runDir) : null;
    this._cleanMissionJsonWorkflow();
    this._initWorkflow();
    // OPT-7: receive wall-clock suspend signals from executor (the shared config
    // object is the same reference the runner→executor uses). Mark the currently
    // open step so the monitor timeline shows the freeze live + in the closed
    // record (carried by _wfClose). The run-state top-level status stays
    // "running" — a suspend does not change flow semantics, only observability.
    cfg.onSuspend = (payload) => {
      try {
        if (this._wfCurrent) {
          this._wfCurrent.suspended = true;
          if (payload && payload.gapMs != null) this._wfCurrent.suspendGapMs = payload.gapMs;
          if (this.workflow) {
            this.workflow.updatedAt = new Date().toISOString();
            this._writeWorkflow();
          }
        }
        this._emitEvent("step_suspended", {
          step: this._wfCurrent ? this._wfCurrent.name : null,
          gapMs: payload && payload.gapMs != null ? payload.gapMs : null,
          label: payload && payload.label ? payload.label : null,
        });
      } catch {}
    };
    let currentStep = entryOverride || this.flow.entry;
    const maxTotalSteps = cfg.maxTotalSteps ?? this.flow.maxTotalSteps ?? 100;
    // WI2: engine-level hard cap for `--step <STEP>` single-step mode. Replaces
    // the old main.js transition-rewrite hack that only covered `transitions`
    // and let onError/onUnknown/onMaxRetries escape the single-step boundary.
    // `Infinity` when not in single-step mode → zero behavioral change.
    const maxSteps = cfg.singleStep ? 1 : Infinity;
    const maxCycleVisits = cfg.maxCycles ?? this.flow.maxCycleVisits ?? 10;
    const maxAuditRounds = this.flow.maxAuditRounds ?? 0;
    const auditEntry = this.flow.auditEntry || this.flow.entry;
    // Ensure pingPongWindow is large enough that maxAuditRounds fires first.
    // The audit cycle alternates DRAFT_PLANS ↔ DEEP_AUDIT, so we need room for
    // at least maxAuditRounds+1 audit visits * 2 steps each before ping-pong triggers.
    const minPingPongWindow = maxAuditRounds > 0 ? (maxAuditRounds + 1) * 2 : 6;
    const pingPongWindow = Math.max(this.flow.pingPongWindow ?? 6, minPingPongWindow);
    let totalSteps = 0;

    const _runDir = cfg.runDir;

    // One getAllProcesses() snapshot shared by startup sysmon + reaper (saves
    // a duplicate execSync("ps ...") / PowerShell CIM query on every engine run).
    let _procSnapshot = null;
    const _getProcs = () => {
      if (!_procSnapshot) _procSnapshot = getAllProcesses();
      return _procSnapshot;
    };
    const _sysMon = (label) => {
      if (!_runDir) return;
      try { sysSnapshot(_runDir, label, _getProcs()); } catch {}
    };

    const _warnOrphans = () => {
      if (!_runDir) return;
      // Pass ownRunId so the reaper never reaps this run's own opencode via the
      // registry path (double protection alongside excludePpid = process.pid).
      try { reapStartupOrphans(_runDir, process.pid, _getProcs(), { ownRunId: this.runId }); } catch {}
    };

    // Subflow child engines skip startup diagnostics — the parent already did
    // sysmon + reaper, and children inherit the same process tree.
    if (cfg.isSubflow !== true) {
      // Register this run in the global active-run registry so other concurrent
      // mission-driver runs' reapers can recognize our opencode children as
      // belonging to an ACTIVE run and spare them. Guarded on runId AND
      // missionName both being non-null: isAliveAndOurs (used by the reaper)
      // matches the driver's cmdline on missionName (the driver cmdline carries
      // missionName but NOT runId), so a null-missionName run (draft/analyze)
      // must NOT be registered — it would be mis-judged dead and mis-killed.
      // Such runs still get the conservative _parentIsAliveDriver fallback.
      if (this.runId && this.missionName) {
        try {
          registerActiveRun({
            runId: this.runId,
            driverPid: process.pid,
            missionName: this.missionName,
            projectRoot: cfg.projectRoot || null,
          });
        } catch { /* best-effort: reaper falls back to parent-process check */ }
      }
      _sysMon(`START:${this.flow.name || "flow"}`);
      _warnOrphans();
    }

    this._emitEvent("run_started", {
      flowName: this.flow.name || null,
      runDir: cfg.runDir || null,
      startedAt: this.workflow ? this.workflow.startedAt : new Date().toISOString(),
      maxTotalSteps,
      maxCycleVisits,
    });

    while (totalSteps < maxTotalSteps && totalSteps < maxSteps) {
      const stepDef = this.flow.steps[currentStep];
      if (!stepDef) {
        this._log(`Unknown step: ${currentStep}`);
        return await this._result("unknown_step", totalSteps);
      }

      const visits = (this.visitCounts.get(currentStep) || 0) + 1;
      this.visitCounts.set(currentStep, visits);
      if (visits > maxCycleVisits) {
        this._log(`maxCycleVisits (${maxCycleVisits}) exceeded for step ${currentStep}`);
        this._emitEvent("limit_hit", {
          limitType: "max_cycles",
          step: currentStep,
          count: visits,
          max: maxCycleVisits,
        });
        return await this._result("max_cycles", totalSteps);
      }

      // WI1 — maxAuditRounds gate reads the PRE-increment workflow.auditRound
      // with `>=` (design §5.2 写法 2). Gate stays BEFORE totalSteps++ and
      // _wfOpen, so an exhausted iteration produces NO phantom step record,
      // NO extra totalSteps++, and NO step_started event. Counter trace for
      // maxAuditRounds=3: 1st entry gate sees 0 → _wfOpen bumps to 1 → run;
      // 2nd → 1 → 2 → run; 3rd → 2 → 3 → run; 4th → 3 >= 3 → completed (no
      // bump). Final auditRound === 3 === maxAuditRounds (quota exhausted).
      if (maxAuditRounds > 0 && currentStep === auditEntry) {
        const round = (this.workflow && this.workflow.auditRound) || 0;
        if (round >= maxAuditRounds) {
          this._log(`maxAuditRounds (${maxAuditRounds}) reached for ${currentStep} → completed`);
          return await this._result("completed", totalSteps);
        }
      }

      totalSteps++;
      this._wfOpen(currentStep, visits);
      // WI5 — surface auditRound on the main-flow auditEntry step. _log moved
      // after _wfOpen so events / log / run-state.json all observe the same
      // post-increment "round N in progress" value. Subflow child engines
      // never hit this branch (isSubflow:true), so internal steps stay clean.
      const isMainAuditEntry = currentStep === auditEntry && cfg.isSubflow !== true;
      const auditRoundSuffix = isMainAuditEntry
        ? ` (audit round ${(this.workflow && this.workflow.auditRound) || 0}/${maxAuditRounds})`
        : "";
      this._log(`[step ${totalSteps}] ${currentStep} (visit #${visits})${auditRoundSuffix}`);
      this._emitEvent("step_started", {
        step: currentStep,
        visit: visits,
        totalSteps,
        stepType: stepDef.type || null,
        runDir: cfg.runDir || null,
        ...(isMainAuditEntry ? {
          auditRound: this.workflow?.auditRound ?? 0,
          maxAuditRounds: this.flow.maxAuditRounds ?? 0,
        } : {}),
      });

      this.pingPongHistory.push({ step: currentStep, viaRetry: false });
      if (this.pingPongHistory.length > pingPongWindow) {
        this.pingPongHistory.shift();
        const names = this.pingPongHistory.map(e => e.step);
        const unique = new Set(names);
        if (unique.size === 2) {
          const [a, b] = [...unique];
          let sawAB = false, sawBA = false;
          for (let i = 0; i < this.pingPongHistory.length - 1; i++) {
            if (this.pingPongHistory[i].step === a && this.pingPongHistory[i + 1].step === b) sawAB = true;
            if (this.pingPongHistory[i].step === b && this.pingPongHistory[i + 1].step === a) sawBA = true;
          }
          if (sawAB && sawBA) {
            const hasRetry = this.pingPongHistory.some(e => e.viaRetry);
            if (hasRetry) {
              this._log(`ping-pong ${a} ↔ ${b} detected but has retry transitions (protected by maxRetries) — skipping`);
            } else {
              this._log(`ping-pong detected: ${a} ↔ ${b} over last ${pingPongWindow} steps → failed`);
              return await this._result("ping_pong", totalSteps);
            }
          }
        }
      }

      // mdo-3 Phase 2: skipSteps / fastRun check (FSD §3.3.2A / §3.3.3A).
      // Evaluated BEFORE `when` (skipSteps skips the whole step regardless of
      // any conditional). On hit: close the (already-opened) workflow record as
      // skipped, emit step_skipped with reason "skipSteps", and jump to the
      // first non-retry target (firstNonRetryTarget). Structure mirrors the
      // when:false skip path below (same _wfClose / _emitEvent / goto / done
      // shape) — only the trigger condition and reason differ.
      if (cfg.effectiveSkip && cfg.effectiveSkip.has(currentStep)) {
        this._log(`  skipSteps hit, skipping step ${currentStep}`);
        this._wfClose("skipped", "skipped");
        this._emitEvent("step_skipped", {
          step: currentStep,
          visit: visits,
          reason: "skipSteps",
        });
        const next = firstNonRetryTarget(stepDef);
        if (next.done) return await this._result(next.done, totalSteps);
        if (next.goto) {
          this._emitEvent("transition", {
            from: currentStep,
            to: next.goto,
            marker: "skipped",
            via: "skipSteps",
          });
          currentStep = next.goto;
          continue;
        }
        return await this._result("completed", totalSteps);
      }

      const whenCondition = stepDef.when;
      if (whenCondition !== undefined) {
        const pass = this._evaluateCondition(whenCondition, this._allVars());
        if (!pass) {
          this._log(`  when condition false, skipping step`);
          this._wfClose(null, "skipped");
          this._emitEvent("step_skipped", {
            step: currentStep,
            visit: visits,
            reason: "when condition false",
          });
          const otherwise = stepDef.otherwise || { done: "completed" };
          if (otherwise.done) return await this._result(otherwise.done, totalSteps);
          if (otherwise.goto) { currentStep = otherwise.goto; continue; }
          if (otherwise.retry) {
            const action = this._handleRetry(currentStep, otherwise, stepDef, { ok: true, marker: "skipped" });
            if (action.done) return await this._result(action.done, totalSteps);
            if (action.goto) {
              this._emitEvent("transition", {
                from: currentStep,
                to: action.goto,
                marker: "skipped",
                via: "retry",
              });
              currentStep = action.goto;
              continue;
            }
          }
          return await this._result("skipped", totalSteps);
        }
      }

      let result;
      try {
        if (stepDef.forEach && stepDef.type !== "subflow") {
          result = await this._executeForEach(currentStep, stepDef);
        } else if (stepDef.type === "agent") {
          result = await this._executeAgentStep(currentStep, stepDef, null);
        } else if (stepDef.type === "tool") {
          result = await this._executeToolStep(currentStep, stepDef);
        } else if (stepDef.type === "script") {
          result = await this._executeScriptStepWithOverride(currentStep, stepDef);
        } else if (stepDef.type === "group") {
          result = await this._executeGroupStep(currentStep, stepDef);
        } else if (stepDef.type === "subflow") {
          result = await this._executeSubflowStep(currentStep, stepDef);
        } else {
          return await this._result("unknown_type", totalSteps);
        }
      } catch (err) {
        this._log(`  error: ${err.message}`);
        const onError = stepDef.onError || { done: "failed" };
        if (onError.done) return await this._result(onError.done, totalSteps);
        if (onError.goto) { currentStep = onError.goto; continue; }
        return await this._result("failed", totalSteps);
      }

      this.context.set(currentStep, result);

      if (!result.ok) {
        const onError = stepDef.onError || { done: "failed" };
        this._log(`  subprocess failed → ${JSON.stringify(onError)}`);
        const failedMeta = result.subflowRuns ? { type: "subflow", subflowRuns: result.subflowRuns } : {};
        // Persist the log as a bare filename (relative to runDir, which holds
        // run-state.json) — not the absolute path. Logs are co-located with the
        // state file, so the directory is implied; storing basename keeps the
        // run dir portable and avoids leaking machine-specific absolute paths.
        if (result.logFile) failedMeta.logFile = basename(result.logFile);
        if (result.promptFile) failedMeta.promptFile = basename(result.promptFile);
        // Build a diagnostic error reason from exit code + log tail so the
        // dashboard can show WHY the step failed (timeout, API rate limit,
        // crash, etc.) — previously only a truncated full-log dump was stored.
        // mdr-1 Phase 2: classify transient provider errors via stderr SIGNATURE
        // (replaces the old `stepDur<60s && logLen<600` heuristic that mislabeled
        // every empty-output crash as a rate limit — memory L001, count=4).
        // A genuine extracted marker is a real business failure, never transient.
        const stepDurMs = this._wfCurrent ? Date.now() - this._wfCurrent.startedAt : null;
        const transientSig = !result.marker
          ? isTransientProviderError({
              exitCode: result.exitCode,
              stderrTail: result.stderrTail || result.errorTail,
              stepDurMs,
              logLen: result.text ? result.text.length : 0,
            })
          : null;
        // Build a diagnostic error reason from exit code + log tail so the
        // dashboard can show WHY the step failed (timeout, crash, rate-limit,
        // etc.). NEUTRAL by default — a rate-limit hint is added ONLY when a
        // stderr signature actually matches.
        if (result.errorTail || result.exitCode != null) {
           const parts = [];
           if (transientSig) {
             parts.push(`⚠ transient provider error (likely ${transientSig}) — see stderr tail`);
           } else if (result.exitCode != null && result.exitCode !== 0) {
             const durStr = stepDurMs != null ? ` (${Math.round(stepDurMs / 1000)}s)` : "";
             parts.push(`empty/short output, exit=${result.exitCode}${durStr} — cause unknown; see stderr tail`);
           }
            if (result.errorTail) parts.push(String(result.errorTail).slice(0, 800));
            failedMeta.error = parts.join(" — ") || "process exited with non-zero code";
         }
         // mdr-1 Phase 3: independent transient provider-error retry path.
         // A transient provider error (rate-limit / quota / overload, per the
         // stderr signature classified above) is retried on its OWN budget — it
         // does NOT consume onError.maxRetries, does NOT emit step_failed (it
         // emits transient_retry), and does NOT trip ping-pong / maxCycleVisits.
         // Exceeding the transient hard cap degrades to a real failure that
         // falls through to step_failed + onError below.
         // (memory L003, count=3: onError carried the tightest budget for the
         // faults that most needed retrying.)
         if (transientSig) {
           const tCfg = this._transientConfig();
           if (tCfg.enabled) {
             // mdr-quota: quota/usage-limit EXHAUSTION is a deterministic,
             // time-bounded condition (the provider announces the reset
             // time). Wait until reset + buffer (fallback: fixed wait) and
             // retry WITHOUT consuming the transient budget, WITHOUT a retry
             // cap (quotaMaxWaitMs=0 = unlimited), and without tripping
             // visit counts — the condition resolves by itself at the reset
             // time, so the mission must keep waiting rather than fail.
             const stderrText = result.stderrTail || result.errorTail || "";
             const quotaSig = isQuotaExhaustion(stderrText);
             if (quotaSig) {
               const waitMs = quotaWaitMs(stderrText, tCfg);
               const spent = (this.quotaWaitTotals.get(currentStep) || 0) + waitMs;
               if (tCfg.quotaMaxWaitMs > 0 && spent > tCfg.quotaMaxWaitMs) {
                 this._log(`  ⚡ quota wait cap reached for ${currentStep} (${Math.round(tCfg.quotaMaxWaitMs / 60000)}min) → degrading to real failure`);
               } else {
                 this.quotaWaitTotals.set(currentStep, spent);
                 this._log(`  ⏳ quota exhaustion (${quotaSig}) — waiting ${Math.round(waitMs / 1000)}s (reset-aware; budget NOT consumed, retries unlimited)`);
                 this._wfClose(result.marker || "transient", "quota_wait", result.sessionId, { transientSig, quotaSig, waitMs, error: failedMeta.error });
                 this._emitEvent("quota_wait", {
                   step: currentStep,
                   visit: visits,
                   signature: quotaSig,
                   waitMs,
                   totalWaitMs: spent,
                   maxWaitMs: tCfg.quotaMaxWaitMs,
                 });
                 this._emitEvent("backoff", {
                   step: currentStep,
                   retryStep: currentStep,
                   durationMs: waitMs,
                   reason: "quota_exhaustion_wait",
                 });
                 await sleep(waitMs);
                 // Roll back this iteration's visit increment so quota waits
                 // are invisible to maxCycleVisits (mirrors transient retries).
                 this.visitCounts.set(currentStep, visits - 1);
                 continue;
               }
             }
             const tCount = (this.transientCounts.get(currentStep) || 0) + 1;
             this.transientCounts.set(currentStep, tCount);
             if (tCount <= tCfg.maxRetries) {
               this._log(`  ⚡ transient provider error (${transientSig}) — independent retry ${tCount}/${tCfg.maxRetries} for ${currentStep} (does not consume onError budget)`);
               // Close this attempt as a transient (non-terminal) record so the
               // timeline shows it; the retry below reopens the step.
               this._wfClose(result.marker || "transient", "transient_retry", result.sessionId, { transientSig, error: failedMeta.error });
               this._emitEvent("transient_retry", {
                 step: currentStep,
                 visit: visits,
                 signature: transientSig,
                 attempt: tCount,
                 max: tCfg.maxRetries,
                 durationMs: stepDurMs,
               });
               // Exponential backoff: base * 2^(attempt-1), hard-capped.
               const backoffMs = Math.min(
                 tCfg.backoffBaseMs * 2 ** (tCount - 1),
                 tCfg.backoffCapMs,
               );
               this._log(`  ⏳ transient backoff ${Math.round(backoffMs / 1000)}s (exponential, cap ${Math.round(tCfg.backoffCapMs / 1000)}s)`);
               this._emitEvent("backoff", {
                 step: currentStep,
                 retryStep: currentStep,
                 durationMs: backoffMs,
                 reason: "transient_provider_retry",
               });
               await sleep(backoffMs);
               // Roll back this iteration's visit increment so transient retries
               // are invisible to maxCycleVisits. Ping-pong is structurally
               // impossible for same-step retries (detection needs 2 distinct
               // alternating steps), so no ping-pong exemption is required. The
               // next loop iteration re-runs the SAME step via normal dispatch.
               this.visitCounts.set(currentStep, visits - 1);
               continue;
             }
             // Transient budget exhausted → degrade to a real failure below.
             this._log(`  ⚡ transient retry budget exhausted for ${currentStep} (${tCfg.maxRetries}) → degrading to real failure`);
           }
         }
         const failedRec = this._wfClose(result.marker || "fail", "failed", result.sessionId, failedMeta);
        if (failedRec) {
          this._emitEvent("step_failed", {
            step: currentStep,
            visit: visits,
            marker: result.marker || "fail",
            durationMs: failedRec.durationMs,
            sessionId: failedRec.sessionId,
            error: failedMeta.error || (result.text ? String(result.text).slice(0, 500) : null),
          });
        }
        if (onError.done) return await this._result(onError.done, totalSteps);
        if (onError.goto) {
          if (onError.append) {
            const appendText = this._formatAppend(onError.append, currentStep, result);
            const existing = this.appendBuffers.get(onError.goto) || "";
            this.appendBuffers.set(onError.goto, existing + appendText);
          }
          currentStep = onError.goto;
          continue;
        }
        if (onError.retry) {
          const action = this._handleRetry(currentStep, onError, stepDef, result);
          if (action.done) return await this._result(action.done, totalSteps);
          if (action.goto) {
            if (action.append) {
              const appendText = this._formatAppend(action.append, currentStep, result);
              const existing = this.appendBuffers.get(action.goto) || "";
              this.appendBuffers.set(action.goto, existing + appendText);
            }
            // Backoff before retrying after a short-duration failure (likely
            // model API rate limit). Without this, immediate retries cascade-
            // fail because the rate limit window hasn't reset.
            if (failedRec && failedRec.durationMs < 60_000) {
              const retryCount = this.retryCounts.get(`${currentStep}→${action.goto}`) || 1;
              const retryBaseMs = cfg.retryBackoffBaseMs ?? 30_000;
              const backoffMs = Math.min(retryBaseMs * retryCount, cfg.retryBackoffCapMs ?? 120_000);
              this._log(`  ⏳ backing off ${backoffMs / 1000}s before retry (previous attempt lasted ${Math.round(failedRec.durationMs / 1000)}s — likely rate-limited)`);
              this._emitEvent("backoff", {
                step: currentStep,
                retryStep: action.goto,
                durationMs: backoffMs,
                previousDurationMs: failedRec.durationMs,
                reason: "short_failure_likely_rate_limit",
              });
              await sleep(backoffMs);
            }
            this._emitEvent("transition", {
              from: currentStep,
              to: action.goto,
              marker: result.marker || "fail",
              via: "retry",
            });
            currentStep = action.goto;
            this._markPingPongRetry();
            continue;
          }
        }
        return await this._result("failed", totalSteps);
      }

      // mdr-1 Phase 3: a successful (non-transient) resolution clears the
      // transient retry debt for this step so a later, independent transient
      // storm can still use its full budget.
      this.transientCounts.delete(currentStep);

      let marker = result.marker;

      if (result.vars) {
        const validations = this.flow.validateFlowVars;
        let rejected = false;
        let rejectedKey = "", rejectedValue = "";
        for (const [k, v] of Object.entries(result.vars)) {
          if (validations?.[k]?.exists && !this._fileExists(v)) {
            this._log(`  ERROR: ${k}="${v}" does not exist — AI returned placeholder`);
            rejected = true;
            rejectedKey = k;
            rejectedValue = v;
            continue;
          }
          this.flowVars.set(k, v);
        }
        if (rejected) {
          const key = `validate:${currentStep}`;
          const count = (this.retryCounts.get(key) || 0) + 1;
          this.retryCounts.set(key, count);
          const maxRetries = this.flow.maxValidationRetries ?? 3;
          if (count > maxRetries) {
            this._log(`  maxValidationRetries (${maxRetries}) exceeded for ${currentStep}; ${rejectedKey} left unset (invalid value not injected)`);
            // Do NOT inject the invalid value into flowVars — it would silently propagate a
            // placeholder path into downstream steps. Let marker processing decide the next
            // step: if the marker is "none" no var is needed; if it indicates success the
            // downstream will fail honestly on the missing file rather than consuming a fake path.
          } else {
            const feedback = `\n\nThe file you specified for ${rejectedKey} does not exist: "${rejectedValue}". Please create the file and then output its real path in a <FLOW_VARS> block.`;
            const existing = this.appendBuffers.get(currentStep) || "";
            this.appendBuffers.set(currentStep, existing + feedback);
            continue;
          }
        }
      }
      if (!marker) {
        this._log(`  marker not found in output`);
        // Observability (§6): even on a null-marker failure, persist logFile +
        // sessionId + a raw tail so the run is traceable back to the exact log
        // (previously this path returned without recording either).
        const unknownMeta = {};
        if (result.logFile) unknownMeta.logFile = basename(result.logFile);
        if (result.promptFile) unknownMeta.promptFile = basename(result.promptFile);
        const unknownRec = this._wfClose(null, "failed", result.sessionId, unknownMeta);
        if (unknownRec) {
          this._emitEvent("step_failed", {
            step: currentStep,
            visit: visits,
            marker: null,
            durationMs: unknownRec.durationMs,
            sessionId: unknownRec.sessionId,
            error: result.text ? String(result.text).slice(-500) : "no marker found in output",
          });
        }
        // Soft-landing: honor an explicit onUnknown; otherwise, if the step defines
        // an onMaxRetries route, degrade through it rather than hard-failing the
        // whole mission on a single unparseable marker (root cause class of §1).
        const onUnknown =
          stepDef.onUnknown ||
          (stepDef.onMaxRetries && (stepDef.onMaxRetries.goto || stepDef.onMaxRetries.done)
            ? stepDef.onMaxRetries
            : { done: "failed" });
        if (onUnknown.goto) { currentStep = onUnknown.goto; continue; }
        if (onUnknown.done) return await this._result(onUnknown.done, totalSteps);
        return await this._result("failed", totalSteps);
      }

      this._log(`  marker: ${marker}`);
      const completedMeta = result.subflowRuns ? { type: "subflow", subflowRuns: result.subflowRuns } : {};
      if (result.logFile) completedMeta.logFile = basename(result.logFile);
      if (result.promptFile) completedMeta.promptFile = basename(result.promptFile);
      const completedRec = this._wfClose(marker, "completed", result.sessionId, completedMeta);
      if (completedRec) {
        this._emitEvent("step_completed", {
          step: currentStep,
          visit: visits,
          marker,
          durationMs: completedRec.durationMs,
          produced: completedRec.produced,
          sessionId: completedRec.sessionId,
          logFile: completedRec.logFile || null,
        });
      }

      let transition = stepDef.transitions[marker];

      if (!transition) {
        const aliased = this._tryAliasMarker(marker, stepDef.transitions);
        if (aliased) {
          this._log(`  marker alias: ${marker} → ${aliased}`);
          marker = aliased;
          transition = stepDef.transitions[marker];
        }
      }

      if (!transition) {
        this._log(`  no transition for marker "${marker}"`);
        const onUnknown = stepDef.onUnknown || { done: "no_transition" };
        if (onUnknown.done) return await this._result(onUnknown.done, totalSteps);
        if (onUnknown.goto) { currentStep = onUnknown.goto; continue; }
        return await this._result("no_transition", totalSteps);
      }

      if (transition.done) {
        this._log(`  → done: ${transition.done}`);
        this._emitEvent("transition", {
          from: currentStep,
          to: null,
          marker,
          via: "done",
        });
        return await this._result(transition.done, totalSteps, result.marker);
      }

      if (transition.retry) {
        const action = this._handleRetry(currentStep, transition, stepDef, result);
        if (action.done) return await this._result(action.done, totalSteps);
        if (action.goto) {
          if (action.append) {
            const appendText = this._formatAppend(action.append, currentStep, result);
            const existing = this.appendBuffers.get(action.goto) || "";
            this.appendBuffers.set(action.goto, existing + appendText);
          }
          this._emitEvent("transition", {
            from: currentStep,
            to: action.goto,
            marker,
            via: "retry",
          });
          currentStep = action.goto;
          this._markPingPongRetry();
          continue;
        }
      }

      if (transition.goto) {
        // WI4 — audit-gate short-circuit (design §4.2.3 B-2 / §4.2.4 truth table).
        // When a step's `nothing` marker is bound for `auditEntry` (e.g.
        // DRAFT_PLANS nothing → DEEP_AUDIT), the gate decides whether another
        // audit round is warranted or whether the run is allowed to complete.
        // Zero-intrusion: `_shouldCompleteOnAuditQuota` returns false unless
        // `flow.auditEntry` exists AND DEEP_AUDIT has run at least once AND no
        // active plans or open audits remain (mdc-1 clean short-circuit; the
        // `round >= maxAuditRounds` ceiling still fires separately in run()).
        if (marker === "nothing" && this._shouldCompleteOnAuditQuota(currentStep, marker, transition)) {
          const round = (this.workflow && this.workflow.auditRound) || 0;
          const max = this.flow?.maxAuditRounds ?? 0;
          this._log(
            `  audit-gate: ${currentStep} nothing + audited >=1 round (auditRound=${round}/${max}) ` +
            `+ no active plans/open audits → completed (clean short-circuit)`,
          );
          this._emitEvent("transition", {
            from: currentStep,
            to: null,
            marker,
            via: "audit_gate",
          });
          return await this._result("completed", totalSteps, marker);
        }
        if (transition.append) {
          const appendText = this._formatAppend(transition.append, currentStep, result);
          // Replace (not accumulate): each cycle = template + this round's append only.
          // Cross-cycle transitions like ROADMAP→DRAFT must not grow the prompt unboundedly.
          // Template-first ordering is preserved by _buildPrompt (template then append),
          // so the stable prefix stays cache-friendly.
          this.appendBuffers.set(transition.goto, appendText);
        }
        if (transition.evidence) {
          this._log(`  recording evidence for ${currentStep}`);
        }
        this._emitEvent("transition", {
          from: currentStep,
          to: transition.goto,
          marker,
          via: "goto",
        });
        currentStep = transition.goto;
        continue;
      }

      this._log(`  invalid transition: ${JSON.stringify(transition)}`);
      return await this._result("invalid_transition", totalSteps);
    }

    // WI2: distinguish single-step cap from the regular maxTotalSteps cap.
    // When `--step X` is in effect, the loop exits after one executed step
    // regardless of which exit (transitions / onError / onUnknown /
    // onMaxRetries / retry) the step would have taken — the cap is physical.
    // `single_step_done` maps to exit code 0 in main.js (treated as success).
    if (cfg.singleStep && totalSteps >= maxSteps) {
      this._log(`single-step cap (maxSteps=${maxSteps}) reached → single_step_done`);
      return await this._result("single_step_done", totalSteps);
    }
    this._emitEvent("limit_hit", {
      limitType: "max_total_steps",
      step: currentStep,
      count: totalSteps,
      max: maxTotalSteps,
    });
    return await this._result("max_total_steps", totalSteps);
  }
}
