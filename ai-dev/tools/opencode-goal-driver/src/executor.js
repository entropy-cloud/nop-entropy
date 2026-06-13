import { closeSync, mkdirSync, openSync, statSync, writeFileSync } from "node:fs";
import { spawn, execSync } from "node:child_process";
import { dirname, resolve } from "node:path";

const IS_WIN32 = process.platform === "win32";

function pad(n) {
  return String(n).padStart(2, "0");
}

function localTimeStr(d = new Date()) {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function localDateTimeStr(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${localTimeStr(d)}`;
}

function genLogFile(config, label) {
  const ts = Date.now();
  const rand = Math.random().toString(36).slice(2, 8);
  return resolve(config.runDir, `${label}-${ts}-${rand}.log`);
}

const SIGKILL_DELAY = 10_000;
const LIVENESS_CHECK_MS = 5 * 60_000;
const BASE_TIMEOUT_MS = 60 * 60_000;

function killTree(pid) {
  try {
    if (IS_WIN32) {
      execSync(`taskkill /PID ${pid} /T /F`, { stdio: "ignore", timeout: 5_000 });
    } else {
      process.kill(-pid, "SIGKILL");
    }
  } catch {}
}

export function execute(config, label, cmd, args, opts = {}) {
  const logFile = genLogFile(config, label);
  const cwd = opts.cwd || config.projectRoot;

  mkdirSync(dirname(logFile), { recursive: true });
  const header = [
    `# cmd: ${cmd} ${args.join(" ")}`,
    `# cwd: ${cwd}`,
    `# started: ${localDateTimeStr()}`,
    "",
  ].join("\n") + "\n";
  writeFileSync(logFile, header);

  const fd = openSync(logFile, "a");
  let child;
  try {
    child = spawn(cmd, args, {
      cwd,
      stdio: ["ignore", fd, fd],
      shell: false,
      detached: !IS_WIN32,
    });
  } catch (err) {
    try { closeSync(fd); } catch {}
    return Promise.resolve({ ok: false, logFile, pid: null });
  }

  const childPid = child.pid;
  if (opts.onSpawn) opts.onSpawn(childPid);

  let lastLogSize = statSync(logFile).size;
  let deadline = Date.now() + BASE_TIMEOUT_MS;
  let progressTimer = null;
  let sigkillTimer = null;
  let settled = false;

  function killGroup() {
    try {
      if (IS_WIN32) {
        child.kill();
      } else {
        process.kill(-childPid, "SIGTERM");
      }
    } catch {}
    sigkillTimer = setTimeout(() => killTree(childPid), SIGKILL_DELAY);
  }

  if (!opts.quiet) {
    progressTimer = setInterval(() => {
      if (settled || child.exitCode !== null) return;

      let currentSize = 0;
      try { currentSize = statSync(logFile).size; } catch {}
      if (currentSize > lastLogSize) {
        lastLogSize = currentSize;
        deadline = Date.now() + BASE_TIMEOUT_MS;
      }

      const ts = localTimeStr();
      const remainMin = Math.max(0, Math.round((deadline - Date.now()) / 60_000));
      process.stderr.write(`  [${ts}] ${label} running ... (pid ${childPid}, timeout in ${remainMin}min)\n`);

      if (Date.now() > deadline) {
        process.stderr.write(`  [TIMEOUT] ${label} no output for 60min, killing process tree ${childPid}\n`);
        killGroup();
      }
    }, LIVENESS_CHECK_MS);
  }

  function cleanup() {
    if (progressTimer) { clearInterval(progressTimer); progressTimer = null; }
    if (sigkillTimer) { clearTimeout(sigkillTimer); sigkillTimer = null; }
    try { closeSync(fd); } catch {}
  }

  return new Promise((resolveFn) => {
    child.on("close", (code) => {
      if (settled) return;
      settled = true;
      cleanup();
      resolveFn({ ok: code === 0, logFile, pid: childPid });
    });

    child.on("error", (err) => {
      if (settled) return;
      settled = true;
      cleanup();
      resolveFn({ ok: false, logFile, pid: childPid });
    });
  });
}
