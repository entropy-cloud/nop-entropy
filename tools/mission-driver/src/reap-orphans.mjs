/**
 * reap-orphans.mjs — Clean up surviving processes that the mission-driver spawned.
 *
 * PRINCIPLE: We NEVER kill by command-name pattern (too broad — the machine
 * has many opencode/node instances). We ONLY kill processes whose ancestry can be
 * traced back to a process that THIS mission-driver spawned.
 *
 * Cross-platform behavior:
 *   - Unix (macOS/Linux): kills by process group (PGID = child PID from
 *     detached spawn). Grandchildren that called setsid() are caught by
 *     the startup orphan scanner.
 *   - Windows: no Unix process groups exist. `executor.js` spawns with
 *     `detached: false`, so descendants are tracked by ParentProcessId
 *     chain. `taskkill /T /F` kills the tree. Orphaned processes (whose
 *     parent has died and been reparented) are identified by command-line
 *     signature [MISSION_DRIVER].
 *
 * Safety: the startup reaper only kills processes matching the distinctive
 * `[MISSION_DRIVER]` signature in their command line, which only THIS tool's
 * runner.js adds.
 */

import { appendFileSync } from "node:fs";
import {
  IS_WIN32,
  getAllProcesses,
  killPid,
  isAlive,
  sleepSync,
} from "./platform.mjs";
import { isAliveAndOurs } from "./run-reconcile.mjs";
import { loadActiveRunIndex } from "./active-run-registry.mjs";

const SIGTERM_GRACE_MS = 5_000;

/**
 * Kill all surviving processes in a specific process group (Unix) or
 * descendant tree (Windows), other than `excludePid` (the direct child
 * that already exited).
 *
 * @param {number} pgid - On Unix: the process group ID (= original child PID).
 *                        On Windows: the root child PID (descendants found by PPID walk).
 * @param {string} runDir - Directory for logging
 * @param {number} excludePid - PID to skip (the direct child, already dead)
 * @returns {{ killed: Array, survivors: Array }}
 */
export function reapProcessGroup(pgid, runDir, excludePid = null) {
  if (!pgid) return { killed: [], survivors: [] };

  const allProcs = getAllProcesses();

  let members;
  if (IS_WIN32) {
    // Windows: find descendants by walking the parent-child tree
    members = _getDescendants(pgid, allProcs);
  } else {
    // Unix: find processes in the same process group
    members = allProcs.filter(
      (m) => m.pgid === pgid && m.pid !== excludePid
    );
  }

  if (members.length === 0) return { killed: [], survivors: [] };

  const killed = [];

  for (const m of members) {
    process.stderr.write(
      `  [reaper] survivor: PID ${m.pid} RSS=${Math.round(m.rss_kb / 1024)}MB ppid=${m.ppid} ${m.name}\n`
    );
    killPid(m.pid, false);
    killed.push(m);
  }

  sleepSync(SIGTERM_GRACE_MS);

  for (const m of killed) {
    if (isAlive(m.pid)) {
      process.stderr.write(`  [reaper] PID ${m.pid} survived SIGTERM → force kill\n`);
      killPid(m.pid, true);
      m.sigkill = true;
    } else {
      m.sigkill = false;
    }
  }

  const reclaimedMB = killed.reduce((s, m) => s + Math.round(m.rss_kb / 1024), 0);
  process.stderr.write(
    `  [reaper] cleanup: reclaimed ${killed.length} survivor(s), ~${reclaimedMB} MB\n`
  );

  if (runDir) {
    try {
      appendFileSync(
        `${runDir}/sys-snapshot.log`,
        JSON.stringify({
          ts: new Date().toISOString(),
          action: "reap-process-group",
          pgid,
          killed: killed.map((m) => ({ pid: m.pid, rss_mb: Math.round(m.rss_kb / 1024), cmd: m.cmd.slice(0, 80), sigkill: m.sigkill })),
        }) + "\n"
      );
    } catch {}
  }

  return { killed, survivors: [] };
}

/**
 * Walk the parent-child process tree to find all descendants of `rootPid`.
 * @private
 */
function _getDescendants(rootPid, allProcs) {
  const result = [];
  const queue = [rootPid];
  const visited = new Set([rootPid]);
  while (queue.length > 0) {
    const currentPid = queue.shift();
    for (const p of allProcs) {
      if (p.ppid === currentPid && !visited.has(p.pid)) {
        visited.add(p.pid);
        result.push(p);
        queue.push(p.pid);
      }
    }
  }
  return result;
}

/**
 * Conservative fallback liveness for a tagged opencode process when no usable
 * registry entry exists. Walks the parent chain looking for a live mission-
 * driver driver (cmdline contains `main.js`).
 *
 * Returns true (→ SPARE) when:
 *   - a live `main.js` ancestor is found (the owning run is still active), OR
 *   - liveness is genuinely undecidable (a live ancestor whose cmdline cannot be
 *     obtained) — mirrors run-reconcile R2: never risk mis-killing an active run.
 * Returns false (→ reap) only when the parent chain positively dead-ends:
 *   - an ancestor PID is dead (isAlive false), or
 *   - the chain is reparented to init (ppid 0/1) without a main.js driver, or
 *   - the chain exhausts (bounded depth) without finding a live main.js driver.
 *
 * @private
 */
function _parentIsAliveDriver(proc, allProcs) {
  if (!proc || typeof proc.ppid !== "number") return true; // undecidable → spare
  const pidsSet = allProcs.length ? new Set(allProcs.map((p) => p.pid)) : null;
  let currentPid = proc.ppid;
  let depth = 0;
  // 0 and 1 mean reparented to the system init (Unix) or a system process —
  // not a mission-driver driver. Bound the walk to avoid pathological trees.
  while (currentPid && currentPid > 1 && depth < 16) {
    depth++;
    const parent = allProcs.find((p) => p.pid === currentPid);
    if (!parent) {
      // Ancestor not in snapshot. If it is provably dead → chain broke → reap.
      // If alive but unlisted → cmdline unobtainable → conservative spare (R2).
      return isAlive(currentPid);
    }
    if (!isAlive(currentPid)) return false; // positively dead ancestor → orphan
    const cmd = parent.cmd || "";
    if (/main\.js/i.test(cmd)) return true; // live mission-driver driver → spare
    currentPid = parent.ppid;
  }
  // Reparented to init / chain exhausted with no live driver → orphan → reap.
  // (pidsSet unused guard keeps the snapshot-intent explicit for future readers.)
  void pidsSet;
  return false;
}

/**
 * Startup reaper: find and kill all processes from a previous crashed
 * mission-driver run, WITHOUT harming opencode children that belong to another
 * ACTIVE concurrent mission-driver run.
 *
 * IDENTIFICATION: mission-driver spawns processes with a distinctive signature:
 *   opencode run -m <model> --agent <agent> --dangerously-skip-permissions [MISSION_DRIVER:<runId>] <prompt>
 * (the legacy bare [MISSION_DRIVER] tag is also matched). The tag now carries a
 * runId so the reaper can ask "is the run that owns this opencode still alive?"
 *
 * PER-RUN ORPHAN JUDGMENT (replaces the old "only one mission-driver at a time"
 * assumption, which mis-killed concurrent runs):
 *   - own run          → skip (self-protection via ownRunId + excludePpid).
 *   - registry says    → isAliveAndOurs(driverPid,...): PID-reuse-safe liveness.
 *     run alive          If ANY registry entry for this runId is alive → SPARE.
 *   - no registry /    → _parentIsAliveDriver: walk the ppid chain for a live
 *     no alive entry     main.js driver. Found → SPARE; positively gone → reap;
 *                        undecidable → SPARE (conservative, mirrors R2).
 *
 * This kills:
 *   1. opencode run processes whose owning run is confirmed dead + descendants
 *      (catches MCP servers, build/test tooling)
 *   2. Orphaned MCP servers (ppid=1 on Unix, or reparented on Windows) from
 *      opencode instances that already died
 *   3. Orphaned build/test tooling (turbo/tsc/vite/vitest) that escaped
 *      process group / tree kill
 *
 * @param {string} runDir - Directory for logging
 * @param {number} [excludePpid] - current run's pid (self-protection fallback)
 * @param {Array} [procs] - injected process snapshot (testability / shared cache)
 * @param {{ ownRunId?: string, registryDir?: string, projectRoot?: string }} [opts]
 */
export function reapStartupOrphans(runDir, excludePpid = null, procs = null, opts = {}) {
  const allProcs = procs || getAllProcesses();
  const ownRunId = (opts && opts.ownRunId) || null;
  const activeIndex = loadActiveRunIndex(opts && opts.registryDir);

  const killed = [];
  const killedPids = new Set();

  const _killOne = (proc, reason) => {
    if (killedPids.has(proc.pid)) return;
    process.stderr.write(
      `  [reaper] killing PID ${proc.pid} RSS=${Math.round(proc.rss_kb / 1024)}MB ppid=${proc.ppid} — ${reason}\n`
    );
    killedPids.add(proc.pid);
    killPid(proc.pid, false);
    killed.push({ pid: proc.pid, rss_mb: Math.round(proc.rss_kb / 1024), cmd: proc.cmd.slice(0, 80), reason });
  };

  // --- Phase 1: mission-driver opencode run processes, judged per-run ---
  // Match BOTH the new [MISSION_DRIVER:<runId>] and legacy [MISSION_DRIVER] tag.
  const TAG_RE = /\[MISSION_DRIVER(?::([^\]]+))?\]/;
  const OC_RE = /opencode\s+run\b/;

  for (const oc of allProcs) {
    if (!OC_RE.test(oc.cmd)) continue;
    const m = oc.cmd.match(TAG_RE);
    if (!m) continue;
    const procRunId = m[1] || null;

    // (a) self-protection: never reap our own run's opencode.
    if (procRunId && procRunId === ownRunId) continue;
    if (oc.ppid === excludePpid) continue;

    // (b) is the run that owns this opencode still active?
    let alive = false;
    const entries = procRunId ? activeIndex.get(procRunId) : null;
    if (entries && entries.length > 0) {
      // Registry-seeded: PID-reuse-safe. Spare if ANY entry's driver is alive
      // (handles same-second runId collisions — multiple drivers, one runId).
      alive = entries.some((e) =>
        isAliveAndOurs(e.driverPid, procRunId, e.missionName, allProcs)
      );
    }
    if (!alive) {
      // No usable registry entry → conservative parent-process liveness check
      // (covers: registry loss, homedir unwritable, legacy bare tag, and
      // missionName=null runs that are deliberately not registered).
      alive = _parentIsAliveDriver(oc, allProcs);
    }

    if (alive) {
      process.stderr.write(
        `  [reaper] sparing PID ${oc.pid} — active concurrent run ${procRunId || "<legacy>"}\n`
      );
      continue;
    }

    // (c) confirmed dead run → reap descendants + self.
    if (IS_WIN32) {
      for (const d of _getDescendants(oc.pid, allProcs)) {
        _killOne(d, `descendant of mission-driver opencode PID ${oc.pid}`);
      }
    } else {
      for (const mem of allProcs.filter((p) => p.pgid === oc.pid && p.pid !== oc.pid)) {
        _killOne(mem, `process group ${oc.pid} member (orphaned mission-driver child)`);
      }
    }
    _killOne(oc, `orphaned mission-driver opencode (dead run ${procRunId || "<legacy>"})`);
  }

  // --- Phase 2: Orphaned MCP servers (parent died, MCP survived) ---
  // On Unix, orphaned processes get ppid=1. On Windows, they get reparented
  // to a system process (typically PID 4 on modern Windows, or remain with
  // the old parent PID until a reaper runs). We check for ppid=1 (Unix) or
  // parent not found in the current process list (Windows).
  const pidsSet = new Set(allProcs.map((p) => p.pid));
  const mcpOrphans = allProcs.filter(
    (p) =>
      !killedPids.has(p.pid) &&
      /zai-mcp-server|mcp-server/.test(p.cmd) &&
      (p.ppid === 1 || !pidsSet.has(p.ppid))
  );
  for (const m of mcpOrphans) {
    _killOne(m, "orphaned MCP server (parent process gone)");
  }

  // --- Phase 3: Orphaned build/test tooling that escaped process-group kill ---
  // Node/pnpm workspace equivalents of the upstream Maven JVM orphans.
  const toolingOrphans = allProcs.filter(
    (p) =>
      !killedPids.has(p.pid) &&
      /\b(turbo|tsc|tsgo|vite|vitest|esbuild|playwright)\b/.test(p.cmd) &&
      (p.ppid === 1 || !pidsSet.has(p.ppid))
  );
  for (const j of toolingOrphans) {
    _killOne(j, "orphaned build/test tooling (parent process gone)");
  }

  // --- Force-kill survivors after grace period ---
  if (killed.length > 0) {
    sleepSync(SIGTERM_GRACE_MS);
    for (const k of killed) {
      if (isAlive(k.pid)) {
        process.stderr.write(`  [reaper] PID ${k.pid} survived → force kill\n`);
        killPid(k.pid, true);
        k.sigkill = true;
      } else {
        k.sigkill = false;
      }
    }
  }

  const reclaimedMB = killed.reduce((s, k) => s + k.rss_mb, 0);
  if (killed.length > 0) {
    process.stderr.write(
      `  [reaper] startup cleanup: killed ${killed.length} orphaned process(es), ~${reclaimedMB} MB reclaimed\n`
    );
  } else {
    process.stderr.write(`  [reaper] startup cleanup: no orphaned processes found\n`);
  }

  if (runDir && killed.length > 0) {
    try {
      appendFileSync(
        `${runDir}/sys-snapshot.log`,
        JSON.stringify({
          ts: new Date().toISOString(),
          action: "reap-startup-orphans",
          platform: process.platform,
          killed: killed.map((k) => ({
            pid: k.pid,
            rss_mb: k.rss_mb,
            cmd: k.cmd,
            reason: k.reason,
            sigkill: k.sigkill,
          })),
        }) + "\n"
      );
    } catch {}
  }

  return { killed, warnings: [] };
}

export const warnStartupOrphans = reapStartupOrphans;

if (import.meta.url === `file://${process.argv[1]}`) {
  const arg2 = process.argv[2];
  const runDir = process.argv[3] || "_tmp";
  if (arg2 === "--startup") {
    const excludePpid = process.argv[4] ? parseInt(process.argv[4], 10) : null;
    console.log(JSON.stringify(reapStartupOrphans(runDir, excludePpid), null, 2));
  } else {
    const pgid = parseInt(arg2, 10);
    if (pgid) {
      console.log(JSON.stringify(reapProcessGroup(pgid, runDir), null, 2));
    } else {
      console.log("Usage: reap-orphans.mjs <pgid> [runDir]              — kill survivors in a process group/tree");
      console.log("       reap-orphans.mjs --startup [runDir] [excludePpid] — kill orphaned mission-driver processes");
    }
  }
}
