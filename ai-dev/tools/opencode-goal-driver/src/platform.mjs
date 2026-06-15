/**
 * platform.mjs — Cross-platform abstraction for process management.
 *
 * Provides unified APIs for listing processes, killing process trees, and
 * checking liveness on macOS, Linux, and Windows (including Git Bash).
 *
 * Node.js detects the OS via `process.platform`, not the shell. On Windows 11
 * with Git Bash, `process.platform` is still `"win32"`, so we branch to
 * Windows-native tools (PowerShell, taskkill) regardless of the terminal.
 */

import { execSync } from "node:child_process";

export const IS_WIN32 = process.platform === "win32";
export const IS_MACOS = process.platform === "darwin";
export const IS_LINUX = process.platform === "linux";

/**
 * Execute a shell command, returning trimmed stdout or "" on failure.
 * Cross-platform — uses the OS default shell (cmd.exe on Windows, /bin/sh on Unix).
 */
export function safeExec(cmd, opts = {}) {
  try {
    return execSync(cmd, { encoding: "utf8", timeout: 8_000, ...opts }).trim();
  } catch {
    return "";
  }
}

/**
 * Run a PowerShell command on Windows.
 * Uses -NoProfile -NonInteractive for speed and safety.
 * Returns trimmed stdout or "" on failure.
 */
export function powershell(script, timeout = 15_000) {
  const escaped = script.replace(/'/g, "''");
  return safeExec(
    `powershell -NoProfile -NonInteractive -Command '${escaped}'`,
    { timeout }
  );
}

/**
 * Get all running processes as a normalized list.
 *
 * @returns {Array<{pid:number, ppid:number, pgid:number, rss_kb:number, name:string, cmd:string}>}
 *   - `pgid` is 0 on Windows (no Unix process groups).
 *   - `cmd` is the full command line on Windows (via CIM), the `comm` column on Unix.
 */
export function getAllProcesses() {
  if (IS_WIN32) return _getProcessesWindows();
  return _getProcessesUnix();
}

function _getProcessesUnix() {
  const raw = safeExec("ps -eo pid,ppid,pgid,rss,command");
  if (!raw) return [];
  const lines = raw.split("\n").slice(1);
  const result = [];
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const parts = trimmed.split(/\s+/);
    const pid = parseInt(parts[0], 10);
    const ppid = parseInt(parts[1], 10);
    const pgid = parseInt(parts[2], 10);
    const rss = parseInt(parts[3], 10);
    const cmd = parts.slice(4).join(" ");
    if (isNaN(pid)) continue;
    result.push({ pid, ppid, pgid, rss_kb: rss, name: cmd.slice(0, 60), cmd });
  }
  return result;
}

function _getProcessesWindows() {
  const raw = powershell(
    "Get-CimInstance Win32_Process | " +
    "ForEach-Object { " +
    "  $cl = if ($_.CommandLine) { $_.CommandLine } else { $_.Name }; " +
    "  Write-Output ($_.ProcessId.ToString() + '`t' + $_.ParentProcessId.ToString() + '`t' + $_.WorkingSetSize.ToString() + '`t' + ($cl -replace \"`t\",' ')) " +
    "}"
  );
  if (!raw) return [];
  const result = [];
  for (const line of raw.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const parts = trimmed.split("\t");
    if (parts.length < 4) continue;
    const pid = parseInt(parts[0], 10);
    const ppid = parseInt(parts[1], 10);
    const rssBytes = parseInt(parts[2], 10) || 0;
    const cmd = parts.slice(3).join("\t") || "";
    if (isNaN(pid)) continue;
    result.push({
      pid,
      ppid,
      pgid: 0,
      rss_kb: Math.round(rssBytes / 1024),
      name: cmd.slice(0, 60),
      cmd,
    });
  }
  return result;
}

/**
 * Kill a single process by PID.
 *
 * On Unix: sends SIGTERM (or SIGKILL if force=true).
 * On Windows: uses `taskkill /PID <pid> /T /F` which kills the entire
 *   descendant tree (Windows has no signal system).
 *
 * @returns {boolean} true if the signal was delivered without error.
 */
export function killPid(pid, force = false) {
  try {
    if (IS_WIN32) {
      execSync(`taskkill /PID ${pid} /T /F`, {
        stdio: "ignore",
        timeout: 5_000,
      });
    } else {
      process.kill(pid, force ? "SIGKILL" : "SIGTERM");
    }
    return true;
  } catch {
    return false;
  }
}

/**
 * Check whether a process is alive.
 * `process.kill(pid, 0)` is cross-platform — it does not actually kill.
 */
export function isAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

/**
 * Kill an entire process tree rooted at `rootPid`.
 *
 * On Unix: kills the process group (`process.kill(-rootPid)`).
 * On Windows: `taskkill /T /F` kills all descendants.
 */
export function killProcessTree(rootPid) {
  try {
    if (IS_WIN32) {
      execSync(`taskkill /PID ${rootPid} /T /F`, {
        stdio: "ignore",
        timeout: 5_000,
      });
    } else {
      process.kill(-rootPid, "SIGKILL");
    }
  } catch {}
}

/**
 * Find all descendant processes of `rootPid` by walking the parent-child tree.
 *
 * On Unix, this complements process-group kill (grandchildren that called
 * setsid escape the group). On Windows, this is the primary mechanism since
 * there are no process groups.
 *
 * @param {number} rootPid
 * @param {Array|null} allProcs — pre-fetched process list (avoids redundant calls)
 * @returns {Array} flat list of descendant process objects
 */
export function getDescendants(rootPid, allProcs = null) {
  const procs = allProcs || getAllProcesses();
  const result = [];
  const queue = [rootPid];
  const visited = new Set([rootPid]);

  while (queue.length > 0) {
    const currentPid = queue.shift();
    for (const p of procs) {
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
 * Sleep synchronously (busy-wait).
 * Used between SIGTERM and SIGKILL in reaper grace periods.
 */
export function sleepSync(ms) {
  const start = Date.now();
  while (Date.now() - start < ms) {}
}
