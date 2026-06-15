/**
 * reap-orphans.mjs — Clean up surviving processes that the goal-driver spawned.
 *
 * PRINCIPLE: We NEVER kill by command-name pattern (too broad — the machine
 * has many opencode instances). We ONLY kill processes whose ancestry can be
 * traced back to a process that THIS goal-driver spawned.
 *
 * Cross-platform behavior:
 *   - Unix (macOS/Linux): kills by process group (PGID = child PID from
 *     detached spawn). Grandchildren that called setsid() are caught by
 *     the startup orphan scanner.
 *   - Windows: no Unix process groups exist. `executor.js` spawns with
 *     `detached: false`, so descendants are tracked by ParentProcessId
 *     chain. `taskkill /T /F` kills the tree. Orphaned processes (whose
 *     parent has died and been reparented) are identified by command-line
 *     signature [GOAL_DRIVER].
 *
 * Safety: the startup reaper only kills processes matching the distinctive
 * `[GOAL_DRIVER]` signature in their command line, which only THIS tool's
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
 * Startup reaper: find and kill all processes from a previous crashed
 * goal-driver run.
 *
 * IDENTIFICATION: goal-driver spawns processes with a distinctive signature:
 *   opencode run -m <model> --agent <agent> --dangerously-skip-permissions [GOAL_DRIVER] <prompt>
 *
 * The interactive opencode is just `opencode` (no `run` subcommand), so it's
 * never matched. Combined with the constraint that only ONE goal-driver runs
 * at a time, any matching process at startup is definitively from a previous
 * crashed run.
 *
 * This function kills:
 *   1. All `opencode run` processes with `[GOAL_DRIVER]` marker + their
 *      descendants (catches MCP servers, Maven JVMs)
 *   2. Orphaned MCP servers (ppid=1 on Unix, or reparented on Windows) from
 *      opencode instances that already died
 *   3. Orphaned Maven JVMs that escaped process group / tree kill
 */
export function reapStartupOrphans(runDir, excludePpid = null) {
  const allProcs = getAllProcesses();

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

  // --- Phase 1: Find goal-driver opencode run processes ---
  const goalDriverPattern = /\[GOAL_DRIVER\]/;
  const ocPattern = /opencode\s+run\b/;
  const ocProcs = allProcs.filter(
    (p) =>
      ocPattern.test(p.cmd) &&
      goalDriverPattern.test(p.cmd) &&
      p.ppid !== excludePpid
  );

  for (const oc of ocProcs) {
    if (IS_WIN32) {
      // Windows: kill descendants by tree walk
      const descendants = _getDescendants(oc.pid, allProcs);
      for (const d of descendants) {
        _killOne(d, `descendant of goal-driver opencode PID ${oc.pid}`);
      }
    } else {
      // Unix: kill process group members
      const groupMembers = allProcs.filter(
        (p) => p.pgid === oc.pid && p.pid !== oc.pid
      );
      for (const m of groupMembers) {
        _killOne(m, `process group ${oc.pid} member (orphaned goal-driver child)`);
      }
    }
    _killOne(oc, "goal-driver opencode run (previous crashed run)");
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

  // --- Phase 3: Orphaned Maven JVMs that escaped process group / tree kill ---
  const jvmOrphans = allProcs.filter(
    (p) =>
      !killedPids.has(p.pid) &&
      /plexus\.classworlds|surefire|org\.apache\.maven/.test(p.cmd) &&
      (p.ppid === 1 || !pidsSet.has(p.ppid))
  );
  for (const j of jvmOrphans) {
    _killOne(j, "orphaned Maven JVM (parent process gone)");
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
      console.log("       reap-orphans.mjs --startup [runDir] [excludePpid] — kill orphaned goal-driver processes");
    }
  }
}
