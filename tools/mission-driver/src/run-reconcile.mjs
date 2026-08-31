/**
 * run-reconcile.mjs — Stale-run reconciliation (read-only reuse of platform.mjs).
 *
 * PRINCIPLE (mirrors reap-orphans.mjs): NEVER judge by process name alone. The
 * only reliable single-machine fact of liveness is whether the PID is still
 * alive AND is still THIS run. Command-line identity check prevents PID reuse
 * from fooling us into sparing (or worse, leaving "running") a run whose PID
 * was recycled by an unrelated process.
 *
 * Safety boundary: when isAliveAndOurs(...) is true (PID alive AND cmdline is
 * ours), reconcileStaleRuns MUST NOT touch that run — this protects coexisting
 * active missions. See FSD §3.1 / §4.3.
 *
 * Idempotent: every function only mutates run-state whose status === "running";
 * already-aborted/completed/failed files are never rewritten.
 *
 * Zero npm dependencies — Node builtins + ./platform.mjs only.
 *
 * FSD: docs/design/stale-run-reconciliation-fsd.md §3.1 / §4.1 / §4.2 / §4.3
 */

import { readFileSync, writeFileSync, renameSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { isAlive, getAllProcesses } from "./platform.mjs";

// Old runs written before pid was persisted have no pid. We cannot decide
// liveness by PID, so we fall back to a conservative time threshold: only if
// updatedAt is older than this hard limit do we dare judge the run stale.
// 90min is deliberately larger than the single-step 60min auto-extend timeout
// (main.js executor) so a legitimately long-but-silent agent step is never
// misjudged. FSD §4.1 / R1.
const NO_PID_STALE_MS = 90 * 60 * 1000;

const MAIN_FILE = "run-state.json";
const SUBFLOW_PREFIX = "run-state-";

/**
 * Decide whether `pid` is alive AND still belongs to THIS run.
 *
 * Two-stage test:
 *   1. isAlive(pid) — cross-platform process.kill(pid, 0).
 *   2. The PID's command line must contain the tool's entry (`main.js`) AND one
 *      of runId / missionName. This is the PID-reuse guard: if an unrelated new
 *      process recycled the PID, its cmdline won't match → judged NOT ours →
 *      treated as dead.
 *
 * Fallback (FSD §3.1.2 / R2): if the command line cannot be obtained (the PID
 * is alive but getAllProcesses returned no entry / empty cmd — e.g. exotic WMI
 * failure), we degrade to "PID-alive-only" → return true (conservatively treat
 * as alive & ours, never mis-kill).
 *
 * @param {number} pid - PID persisted in run-state.json.
 * @param {string} runId - run-state.runId (identity token).
 * @param {string} missionName - run-state.missionName (identity token).
 * @param {Array<{pid:number,cmd:string}>} [processes] - Optional injected
 *   process snapshot (getAllProcesses() shape). Defaults to a live
 *   getAllProcesses() call. Injected for unit-testability and so callers (e.g.
 *   monitor, FSD R3) can share one cached snapshot per request.
 * @returns {boolean}
 */
export function isAliveAndOurs(pid, runId, missionName, processes) {
  if (!pid || typeof pid !== "number") return false;
  if (!isAlive(pid)) return false;

  const procs = Array.isArray(processes) ? processes : getAllProcesses();
  const proc = procs.find((p) => p && p.pid === pid);
  const cmd = proc ? (proc.cmd || "") : "";

  // Could not obtain a command line for a live PID → conservative fallback:
  // treat as alive (R2). Do not risk mis-killing / leaving stale an active run.
  if (!cmd) return true;

  const hasMain = /main\.js/i.test(cmd);
  const rid = runId != null ? String(runId) : "";
  const mname = missionName != null ? String(missionName) : "";
  const hasIdentity = (rid && cmd.includes(rid)) || (mname && cmd.includes(mname));
  return hasMain && hasIdentity;
}

/**
 * Mark a run as aborted: flip top-level status==="running" → "aborted", stamp
 * endedAt / updatedAt / abortReason, and tail any still-"running" step →
 * "aborted". Then do the same for co-located subflow files (run-state-*.json).
 *
 * Idempotent: files whose top-level status is not "running" are left untouched
 * (never rewritten). This makes repeated reconciliation safe under last-write-
 * wins concurrency (FSD §4.3).
 *
 * Atomic writes (tmp + renameSync) mirror engine._writeWorkflow().
 *
 * @param {string} runDir - Directory holding run-state.json (+ run-state-*.json).
 * @param {string} reason - Abort cause string (lets signal-handler vs
 *   reconciliation vs no-pid-stale sources be distinguished downstream).
 */
export function markAborted(runDir, reason) {
  if (!runDir) return;
  _abortStateFile(join(runDir, MAIN_FILE), reason);

  let entries;
  try {
    entries = readdirSync(runDir);
  } catch {
    return;
  }
  for (const entry of entries) {
    if (entry.startsWith(SUBFLOW_PREFIX) && entry.endsWith(".json")) {
      _abortStateFile(join(runDir, entry), reason);
    }
  }
}

/**
 * Scan `<projectRoot>/_tmp/<run>/run-state.json` for runs stuck in "running" and
 * reconcile them to "aborted" when their owning process is gone.
 *
 * Per-run decision:
 *   - has pid AND isAliveAndOurs(...)  → SKIP (active run, never harm it).
 *   - has pid AND dead / PID reused     → markAborted.
 *   - no pid AND updatedAt older than   → markAborted (conservative time
 *     NO_PID_STALE_MS                      fallback, FSD R1).
 *   - no pid AND recently updated       → SKIP (cannot prove stale).
 *
 * Idempotent end-to-end: once a run is "aborted" it is no longer "running" and
 * is skipped on subsequent scans.
 *
 * @param {string} projectRoot - Repository root (contains _tmp/).
 * @param {Array} [processes] - Optional shared process snapshot (FSD R3); when
 *   omitted, getAllProcesses() is called ONCE for the whole scan.
 * @returns {{ reconciled: Array<{runDir,runId,pid?,reason}>, skipped: Array<{runDir,runId,reason}> }}
 */
export function reconcileStaleRuns(projectRoot, processes) {
  const reconciled = [];
  const skipped = [];

  const snapshot = Array.isArray(processes) ? processes : getAllProcesses();
  const tmpRoot = join(projectRoot, "_tmp");

  let dirs;
  try {
    dirs = readdirSync(tmpRoot, { withFileTypes: true });
  } catch {
    return { reconciled, skipped };
  }

  for (const d of dirs) {
    if (!d.isDirectory()) continue;
    const runDir = join(tmpRoot, d.name);
    const mainFile = join(runDir, MAIN_FILE);

    let state;
    try {
      state = JSON.parse(readFileSync(mainFile, "utf8"));
    } catch {
      continue; // no run-state.json in this dir — not a run dir, skip
    }
    if (!state || state.status !== "running") continue;

    const runId = state.runId || null;
    const missionName = state.missionName || null;
    const pid = typeof state.pid === "number" ? state.pid : null;

    if (pid) {
      if (isAliveAndOurs(pid, runId, missionName, snapshot)) {
        skipped.push({ runDir, runId, pid, reason: "process alive" });
        continue;
      }
      markAborted(runDir, `reconciled: process not alive / pid reused (pid ${pid})`);
      reconciled.push({ runDir, runId, pid, reason: "process dead or pid reused" });
    } else {
      const updatedMs = state.updatedAt ? Date.parse(state.updatedAt) : NaN;
      if (!isNaN(updatedMs) && (Date.now() - updatedMs) > NO_PID_STALE_MS) {
        markAborted(runDir, "reconciled: no pid and updatedAt older than 90min (stale fallback)");
        reconciled.push({ runDir, runId, reason: "no pid, updatedAt > 90min" });
      } else {
        skipped.push({ runDir, runId, reason: "no pid, updatedAt < 90min (cannot prove stale)" });
      }
    }
  }

  return { reconciled, skipped };
}

// ── internal ──────────────────────────────────────────────────────────────

function _abortStateFile(file, reason) {
  let state;
  try {
    state = JSON.parse(readFileSync(file, "utf8"));
  } catch {
    return; // missing or corrupt — nothing to abort
  }
  if (!state || state.status !== "running") return; // idempotent

  const now = new Date().toISOString();
  state.status = "aborted";
  state.endedAt = now;
  state.updatedAt = now;
  state.abortReason = reason;
  if (Array.isArray(state.steps)) {
    for (const s of state.steps) {
      if (s && s.status === "running") s.status = "aborted";
    }
  }
  _atomicWrite(file, state);
}

function _atomicWrite(file, obj) {
  const tmp = file + ".tmp";
  writeFileSync(tmp, JSON.stringify(obj, null, 2) + "\n", "utf8");
  _renameWithRetry(tmp, file);
}

// Windows: renameSync over an existing file can intermittently throw EPERM /
// EBUSY / EACCES (antivirus scanning, filesystem indexing, briefly-held handles).
// These are transient — retry with a short backoff instead of dropping the abort.
// Mirrors the established retry pattern of the `write-file-atomic` package.
function _renameWithRetry(src, dest, retries = 6) {
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      renameSync(src, dest);
      return;
    } catch (err) {
      const retriable =
        err &&
        (err.code === "EPERM" || err.code === "EBUSY" || err.code === "EACCES");
      if (!retriable || attempt === retries) throw err;
      // tiny synchronous backoff: 5,10,20,40,80,160 ms (worst case ~315 ms)
      const until = Date.now() + 5 * Math.pow(2, attempt);
      while (Date.now() < until) {
        /* busy-wait */
      }
    }
  }
}
