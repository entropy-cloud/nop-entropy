/**
 * active-run-registry.mjs — Cross-run registry of active mission-driver runs.
 *
 * PURPOSE: lets a startup reaper (reap-orphans.mjs) tell the difference between
 * "an opencode process belonging to ANOTHER active mission-driver run" (must be
 * spared) and "an opencode process orphaned by a previous crashed run" (must be
 * reaped). Without this, the reaper assumed only one mission-driver runs at a
 * time and killed every concurrent run's opencode.
 *
 * SAFETY MODEL: the registry is a HINT, not the authority. The authoritative
 * liveness check is run-reconcile.isAliveAndOurs(driverPid, runId, missionName):
 * a stale registry entry whose driverPid is dead is correctly judged dead. So a
 * crash that leaves a registry file behind is harmless — the next run's reaper
 * detects the dead driverPid and reclaims. unregister is best-effort cleanup.
 *
 * LOCATION: <homedir>/.mission-driver/active/<runId>-<driverPid>.json — global
 * (per-user, not per-project) so cross-project parallel runs can see each other.
 * The driverPid suffix prevents file collisions when two runs share a runId
 * (runId is second-precision timestamp-only, so same-second launches collide).
 *
 * Zero npm dependencies — Node builtins only. Mirrors run-reconcile.mjs style.
 */

import { mkdirSync, writeFileSync, renameSync, readFileSync, readdirSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";

export const ACTIVE_RUNS_DIR = join(homedir(), ".mission-driver", "active");

/**
 * @typedef {Object} ActiveRunEntry
 * @property {string} runId
 * @property {number} driverPid
 * @property {string} missionName
 * @property {string} projectRoot
 * @property {string} startedAt  - ISO timestamp
 * @property {string} heartbeatTs - ISO timestamp (refreshed by executor heartbeat)
 */

function _filePath(dir, runId, driverPid) {
  return join(dir, `${runId}-${driverPid}.json`);
}

// Windows: renameSync / unlinkSync over a file can intermittently throw EPERM /
// EBUSY / EACCES (antivirus, indexing). Retry with a short backoff. Mirrors
// run-reconcile._renameWithRetry.
function _renameWithRetry(src, dest, retries = 6) {
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      renameSync(src, dest);
      return;
    } catch (err) {
      const retriable = err && (err.code === "EPERM" || err.code === "EBUSY" || err.code === "EACCES");
      if (!retriable || attempt === retries) throw err;
      const until = Date.now() + 5 * Math.pow(2, attempt);
      while (Date.now() < until) { /* busy-wait */ }
    }
  }
}

/**
 * Register (or refresh) the current run in the global registry. Best-effort:
 * any failure (e.g. homedir read-only) is swallowed — the run proceeds and the
 * reaper falls back to parent-process liveness for this run.
 *
 * @param {{ runId: string, driverPid: number, missionName: string, projectRoot: string, dir?: string }} entry
 */
export function registerActiveRun({ runId, driverPid, missionName, projectRoot, dir }) {
  if (!runId || !driverPid) return;
  const registryDir = dir || ACTIVE_RUNS_DIR;
  const now = new Date().toISOString();
  const record = {
    runId,
    driverPid,
    missionName: missionName || null,
    projectRoot: projectRoot || null,
    startedAt: now,
    heartbeatTs: now,
  };
  try {
    mkdirSync(registryDir, { recursive: true });
    const file = _filePath(registryDir, runId, driverPid);
    const tmp = file + ".tmp";
    writeFileSync(tmp, JSON.stringify(record, null, 2) + "\n", "utf8");
    _renameWithRetry(tmp, file);
  } catch { /* best-effort */ }
}

/**
 * Refresh heartbeatTs for an already-registered run. Best-effort / silent.
 *
 * @param {string} runId
 * @param {number} driverPid
 * @param {string} [dir]
 */
export function touchActiveRun(runId, driverPid, dir) {
  if (!runId || !driverPid) return;
  const registryDir = dir || ACTIVE_RUNS_DIR;
  const file = _filePath(registryDir, runId, driverPid);
  try {
    const existing = JSON.parse(readFileSync(file, "utf8"));
    existing.heartbeatTs = new Date().toISOString();
    const tmp = file + ".tmp";
    writeFileSync(tmp, JSON.stringify(existing, null, 2) + "\n", "utf8");
    _renameWithRetry(tmp, file);
  } catch { /* best-effort: file may not exist, homedir read-only, etc. */ }
}

/**
 * Remove a run's registry entry. Best-effort; ENOENT (never registered / already
 * removed) is silently ignored, making this idempotent and safe to call from a
 * finally block for runs that were never registered (e.g. missionName=null).
 *
 * @param {string} runId
 * @param {number} driverPid
 * @param {string} [dir]
 */
export function unregisterActiveRun(runId, driverPid, dir) {
  if (!runId || !driverPid) return;
  const registryDir = dir || ACTIVE_RUNS_DIR;
  try {
    unlinkSync(_filePath(registryDir, runId, driverPid));
  } catch (err) {
    if (err && err.code === "ENOENT") return; // never registered — expected no-op
    // other errors (EBUSY retry worthy) — one best-effort retry
    try { unlinkSync(_filePath(registryDir, runId, driverPid)); } catch { /* swallow */ }
  }
}

/**
 * Build an index of all registered active runs. Corrupt / unreadable files are
 * skipped. Multiple entries may share a runId (same-second launches); the reaper
 * spares a tagged opencode if ANY entry for its runId is alive-and-ours.
 *
 * @param {string} [dir]
 * @returns {Map<string, ActiveRunEntry[]>} runId -> entries (1+ when runId collides)
 */
export function loadActiveRunIndex(dir) {
  const registryDir = dir || ACTIVE_RUNS_DIR;
  const index = new Map();
  let entries;
  try {
    entries = readdirSync(registryDir);
  } catch {
    return index; // dir missing / unreadable → empty index → reaper uses fallback
  }
  for (const name of entries) {
    if (!name.endsWith(".json")) continue;
    let record;
    try {
      record = JSON.parse(readFileSync(join(registryDir, name), "utf8"));
    } catch {
      continue; // corrupt file — skip
    }
    if (!record || !record.runId || !record.driverPid) continue;
    const list = index.get(record.runId);
    if (list) list.push(record);
    else index.set(record.runId, [record]);
  }
  return index;
}
