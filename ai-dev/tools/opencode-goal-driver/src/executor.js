import { appendFileSync, closeSync, mkdirSync, openSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { spawn } from "node:child_process";
import { dirname, resolve } from "node:path";

function genLogFile(config, label) {
  const ts = Date.now();
  const rand = Math.random().toString(36).slice(2, 8);
  return resolve(config.runDir, `${label}-${ts}-${rand}.log`);
}

const SIGKILL_DELAY = 10_000;

/**
 * Run any external command via fd redirect (equivalent to shell >>file 2>&1).
 *
 * Redirects stdout/stderr to a log file via file descriptors, avoiding
 * shell escaping issues. Child process output is identical to terminal execution.
 *
 * @param {object}   config         – global config (must have runDir)
 * @param {string}   label          – log file label (e.g. "mvnw", "oc-deep-audit")
 * @param {string}   cmd            – executable path (e.g. "./mvnw", "opencode")
 * @param {string[]} args           – command line arguments
 * @param {object}   [opts]
 * @param {string}   [opts.cwd]     – working directory (default config.projectRoot)
 * @param {number}   [opts.timeout] – timeout in ms (0 = none, default 0)
 * @param {boolean}  [opts.quiet]   – suppress progress output (default false)
 * @returns {{ ok: boolean, logFile: string }}
 */
export function execute(config, label, cmd, args, opts = {}) {
  const logFile = genLogFile(config, label);
  const cwd = opts.cwd || config.projectRoot;
  const timeout = opts.timeout || 0;

  // Log file header
  mkdirSync(dirname(logFile), { recursive: true });
  const header = [
    `# cmd: ${cmd} ${args.join(" ")}`,
    `# cwd: ${cwd}`,
    `# started: ${new Date().toISOString()}`,
    "",
  ].join("\n") + "\n";
  writeFileSync(logFile, header);

  const fd = openSync(logFile, "a");
  const child = spawn(cmd, args, { cwd, stdio: ["ignore", fd, fd], shell: false });

  // Progress every 60s
  let progressTimer = null;
  if (!opts.quiet) {
    progressTimer = setInterval(() => {
      if (child.exitCode === null) {
        const ts = new Date().toISOString().slice(11, 19);
        process.stderr.write(`  [${ts}] ${label} running ... (pid ${child.pid})\n`);
      }
    }, 60000);
  }

  // Timeout kill: SIGTERM → SIGKILL fallback
  let timeoutTimer = null;
  let sigkillTimer = null;
  if (timeout > 0) {
    timeoutTimer = setTimeout(() => {
      process.stderr.write(`  [TIMEOUT] ${label} timed out after ${timeout}ms, terminating\n`);
      try { child.kill("SIGTERM"); } catch { }
      sigkillTimer = setTimeout(() => {
        process.stderr.write(`  [TIMEOUT] ${label} did not exit after SIGTERM within 10s, sending SIGKILL\n`);
        try { child.kill("SIGKILL"); } catch { }
      }, SIGKILL_DELAY);
    }, timeout);
  }

  // Watchdog: periodically spawn an independent sub-agent to check subprocess status.
  // Does not do mtime/content checking on the driver side (fd-redirected logs have
  // heartbeat noise and are unreliable). Instead, an independent sub-agent inspects
  // the subprocess's opencode internal logs for intelligent status assessment.
  const watchdogIntervalMs = config.watchdogIntervalMs;
  const watchdogStallMs = config.watchdogStallMs;
  let watchdogTimer = null;
  let watchdogRunning = false;
  let lastWatchdogTime = Date.now();

  if (!opts.quiet && watchdogIntervalMs > 0 && watchdogStallMs > 0) {
    watchdogTimer = setInterval(async () => {
      if (child.exitCode !== null) return;
      if (watchdogRunning) return;
      const elapsed = Date.now() - lastWatchdogTime;
      if (elapsed < watchdogStallMs) return;

      try {
        watchdogRunning = true;
        if (child.exitCode !== null) { watchdogRunning = false; return; }

        process.stderr.write(`  [WATCHDOG] ${label} running for ${Math.round(elapsed / 60_000)} min, spawning independent sub-agent to check status ...\n`);
        appendFileSync(logFile, `\n# watchdog: running ${Math.round(elapsed / 60_000)}min, spawning check agent @ ${new Date().toISOString()}\n`);

        const wdResult = await spawnWatchdogAgent(config, label, logFile, child.pid);

        const summary = `action=${wdResult.action} diagnosis=${wdResult.diagnosis} log=${wdResult.logFile || "N/A"}`;
        appendFileSync(logFile, `# watchdog result: ${summary} @ ${new Date().toISOString()}\n`);
        process.stderr.write(`  [WATCHDOG] ${label} → ${summary}\n`);

        lastWatchdogTime = Date.now();
        watchdogRunning = false;
      } catch (e) {
        watchdogRunning = false;
        process.stderr.write(`  [WATCHDOG] error: ${e.message}\n`);
      }
    }, watchdogIntervalMs);
  }

  function cleanup() {
    if (progressTimer) clearInterval(progressTimer);
    if (timeoutTimer) clearTimeout(timeoutTimer);
    if (sigkillTimer) clearTimeout(sigkillTimer);
    if (watchdogTimer) {
      clearInterval(watchdogTimer);
    }
    try { closeSync(fd); } catch { }
  }

  let settled = false;
  return new Promise((resolveFn) => {
    child.on("close", (code) => {
      if (settled) return;
      settled = true;
      cleanup();
      appendFileSync(logFile, `# exit: ${code}\n# finished: ${new Date().toISOString()}\n`);
      resolveFn({ ok: code === 0, logFile });
    });

    child.on("error", (err) => {
      if (settled) return;
      settled = true;
      cleanup();
      appendFileSync(logFile, `# error: ${err.message}\n# finished: ${new Date().toISOString()}\n`);
      resolveFn({ ok: false, logFile });
    });
  });
}

/**
 * Spawn an independent opencode run sub-agent to determine whether a stalled process needs killing.
 *
 * Key design: the main driver does NOT make kill decisions or execute kills.
 * The sub-agent uses bash tools to inspect logs, assess status, and decide whether to kill.
 * If the sub-agent itself fails/times out, it has no impact on the main driver (watchdog simply didn't fire).
 *
 * @returns {{ action: string, diagnosis: string, logFile: string|null }}
 */
function spawnWatchdogAgent(config, label, stalledLogFile, stalledPid) {
  const driverPid = process.pid;

  const prompt = [
    `You are an independent watchdog sub-agent for the opencode goal driver.`,
    ``,
    `## Background`,
    `A sub-agent named "${label}" (PID=${stalledPid}) has been running for over 30 minutes and needs a status check.`,
    `Main driver process PID=${driverPid}.`,
    `FD-redirected log: ${stalledLogFile}`,
    ``,
    `## Your task`,
    `Proceed autonomously — do not ask for confirmation.`,
    ``,
    `### Step 1: Diagnosis`,
    `1. Run \`ps -o pid,ppid,stat,etime -p ${stalledPid}\` via bash to check if the process is still running`,
    `2. Find the subprocess's opencode internal log:`,
    `   - Run \`lsof -p ${stalledPid} -Fn 2>/dev/null | grep 'opencode/log' | grep '.log'\` to find the log file path`,
    `   - Or \`ls -t ~/.local/share/opencode/log/*.log | head -3\` to find the most recent log files`,
    `3. Read the last 80 lines of the opencode internal log (note: there will be many service=bus type=message.part.delta streaming heartbeats — ignore those)`,
    `4. Assess the sub-agent's status (look at the last non-bus-heartbeat entry):`,
    `   - Has recent service=tool, service=permission, or service=session.prompt entries → actively working, do NOT kill`,
    `   - Last dozens of lines are all service=bus type=message.part.delta with no tool/permission/prompt activity → LLM call is hung`,
    `   - Internal log has # exit: or process no longer exists → completed but signal was lost (zombie state)`,
    `5. If uncertain, also read the last 50 lines of the FD-redirected log ${stalledLogFile} as supplementary info`,
    ``,
    `### Step 2: Decision and Action`,
    `If diagnosis confirms the sub-agent is stuck/zombie/completed-but-signal-lost:`,
    `  Run \`kill ${stalledPid}\` via bash to terminate the stuck sub-agent`,
    `  Then run \`kill -0 ${stalledPid} 2>/dev/null && echo 'still alive' || echo 'dead'\` to confirm termination`,
    ``,
    `If diagnosis shows the sub-agent is actively working:`,
    `  Do nothing`,
    ``,
    `### Absolute Prohibitions`,
    `- Only kill PID=${stalledPid} (the sub-agent). NEVER kill PID=${driverPid} (the main driver)`,
    `- Do not kill any other process`,
    `- If ${stalledPid} no longer exists, no action is needed`,
    ``,
    `### Output`,
    `When done, output:`,
    `<WATCHDOG_RESULT>`,
    `  diagnosis: one-sentence diagnosis`,
    `  action: kill / no-action`,
    `  pid: ${stalledPid}`,
    `</WATCHDOG_RESULT>`,
  ].join("\n");

  const wdLogFile = genLogFile(config, `watchdog-${label}`);
  mkdirSync(dirname(wdLogFile), { recursive: true });
  writeFileSync(wdLogFile, `# watchdog agent for ${label} (target pid=${stalledPid})\n# started: ${new Date().toISOString()}\n`);

  const args = ["run", "-m", config.model, "--agent", config.agent, "--dangerously-skip-permissions", prompt];

  return new Promise((res) => {
    let settled = false;
    const fallback = { action: "unknown", diagnosis: "watchdog agent did not complete normally", logFile: wdLogFile };
    const done = (result) => {
      if (settled) return;
      settled = true;
      res(result);
    };

    let wdFd;
    try {
      wdFd = openSync(wdLogFile, "a");
    } catch (e) {
      process.stderr.write(`  [WATCHDOG] failed to open log file: ${e.message}\n`);
      done({ ...fallback, diagnosis: `watchdog agent log file open failed: ${e.message}` });
      return;
    }

    try {
      const wdChild = spawn("opencode", args, {
        cwd: config.projectRoot,
        stdio: ["ignore", wdFd, wdFd],
        shell: false,
      });

      let wdSigkillTimer;
      const wdTimeout = setTimeout(() => {
        process.stderr.write(`  [WATCHDOG] check agent timed out (10min), force-terminating\n`);
        try { wdChild.kill("SIGTERM"); } catch { }
        wdSigkillTimer = setTimeout(() => {
          try { wdChild.kill("SIGKILL"); } catch { }
        }, SIGKILL_DELAY);
        try { closeSync(wdFd); } catch { }
        done({ ...fallback, diagnosis: "watchdog agent itself timed out" });
      }, 10 * 60_000);

      wdChild.on("close", () => {
        clearTimeout(wdTimeout);
        if (wdSigkillTimer) clearTimeout(wdSigkillTimer);
        try { closeSync(wdFd); } catch { }
        appendFileSync(wdLogFile, `# watchdog agent finished: ${new Date().toISOString()}\n`);
        process.stderr.write(`  [WATCHDOG] check agent finished, log: ${wdLogFile}\n`);

        try {
          const text = readFileSync(wdLogFile, "utf8");
          const m = text.match(/<WATCHDOG_RESULT>([\s\S]*?)<\/WATCHDOG_RESULT>/);
          if (m) {
            const block = m[1].trim();
            const actionMatch = block.match(/action:\s*(kill|no-action)/i);
            const diagMatch = block.match(/diagnosis:\s*(.+)/i);
            done({
              action: actionMatch ? actionMatch[1].toLowerCase() : "unknown",
              diagnosis: diagMatch ? diagMatch[1].trim() : block,
              logFile: wdLogFile,
            });
            return;
          }
        } catch { }
        done({ ...fallback, diagnosis: "watchdog agent did not output WATCHDOG_RESULT tag" });
      });

      wdChild.on("error", (err) => {
        clearTimeout(wdTimeout);
        try { closeSync(wdFd); } catch { }
        process.stderr.write(`  [WATCHDOG] check agent failed to start: ${err.message}\n`);
        done({ ...fallback, diagnosis: `watchdog agent start failed: ${err.message}` });
      });
    } catch (e) {
      try { closeSync(wdFd); } catch { }
    process.stderr.write(`  [WATCHDOG] spawn error: ${e.message}\n`);
    done({ ...fallback, diagnosis: `watchdog agent spawn error: ${e.message}` });
    }
  });
}
