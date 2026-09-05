import { appendFileSync, closeSync, mkdirSync, openSync, readSync, statSync, writeFileSync, readFileSync } from "node:fs";
import { spawn } from "node:child_process";
import { basename, dirname, resolve } from "node:path";
import { freemem } from "node:os";
import { snapshot as sysSnapshot } from "./sys-snapshot.mjs";
import { reapProcessGroup } from "./reap-orphans.mjs";
import { touchActiveRun } from "./active-run-registry.mjs";
import { IS_WIN32, killProcessTree } from "./platform.mjs";

function pad(n) {
  return String(n).padStart(2, "0");
}

function localTimeStr(d = new Date()) {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function localDateTimeStr(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${localTimeStr(d)}`;
}

function toGB(bytes) {
  return Math.round(bytes / 1024 / 1024 / 1024 * 10) / 10;
}

function genLogFile(config, label) {
  const ts = Date.now();
  const rand = Math.random().toString(36).slice(2, 8);
  return resolve(config.runDir, `${label}-${ts}-${rand}.log`);
}

// Decision: 心跳尾部读取采用方案 B —— 在 tailLines 内开独立只读 fd（`openSync(logFile, "r")`）
// 精确读取尾部 2KB，与 executor.js 心跳块的 append fd（"a" = O_WRONLY）隔离。
// 替代方案 (A) readFileSync 全量读 + slice(-2048) 对多 MB 日志每 5min 浪费 IO；否决。
// 替代方案 (C) 复用 line 59 append fd 改 "a+" —— 否决：改变子进程 stdio 语义风险不可控。
// 残留风险：尾部 2KB 可能截断首行（slice 边界），通过 split("\n") 后丢弃首段规避。
const TAIL_READ_BYTES = 2048;
const TAIL_MAX_LINES = 3;
const TAIL_MAX_CHARS = 500;

export function tailLines(logFile, currentSize, maxChars = TAIL_MAX_CHARS) {
  let rfd;
  try {
    rfd = openSync(logFile, "r");
    const readLen = Math.min(TAIL_READ_BYTES, Math.max(0, currentSize));
    const buf = Buffer.alloc(readLen);
    const read = readSync(rfd, buf, 0, readLen, Math.max(0, currentSize - readLen));
    const text = buf.toString("utf8", 0, read);
    const lines = text.split("\n");
    // 丢弃首段（slice 边界不完整行）
    const complete = lines.length > 1 ? lines.slice(1) : lines;
    // 过滤空行与 # header 注释行
    const filtered = complete
      .map((s) => s.replace(/\r$/, ""))
      .filter((s) => s.length > 0 && !s.startsWith("#"));
    let picked = filtered.slice(-TAIL_MAX_LINES);
    // 合计截断 ≤ maxChars（超出保留尾部 maxChars 字符）
    const joined = picked.join("\n");
    if (joined.length > maxChars) {
      const trimmedTail = joined.slice(-maxChars);
      const firstNl = trimmedTail.indexOf("\n");
      picked = (firstNl === -1 ? trimmedTail : trimmedTail.slice(firstNl + 1)).split("\n");
    }
    return picked;
  } catch {
    return [];
  } finally {
    if (rfd !== undefined) {
      try { closeSync(rfd); } catch {}
    }
  }
}

export function summarizeArg(a) {
  if (typeof a !== "string") a = String(a);
  if (a.length <= 80 && !a.includes("\n")) return a;
  const preview = a.replace(/\s+/g, " ").slice(0, 80);
  return `${preview}...(${a.length} chars)`;
}

const SIGKILL_DELAY = 10_000;
const LIVENESS_CHECK_MS = 5 * 60_000;
const BASE_TIMEOUT_MS = 60 * 60_000;
// mdr-1 Phase 1: rolling cap for the independently-captured stderr buffer.
// Keeps the last STDERR_CAP bytes — enough to retain a rate-limit/crash
// signature while bounding memory for a long-running process.
const STDERR_CAP = 16 * 1024;
const STDERR_TAIL_LINES = 10;
const STDERR_TAIL_CHARS = 800;
const LOG_TAIL_LINES = 15;

/**
 * mdr-1 Phase 1 — pure errorTail synthesis, extracted from the child `close`
 * handler so the empty-output / header-only / real-output branches are unit-
 * testable WITHOUT spawning a subprocess (Windows DLL-init races made real
 * spawns flaky under the concurrent `node --test` worker pool).
 *
 * Decision: when the log file is header-only (only "#" executor-header lines,
 * i.e. the child wrote no real output before exiting), synthesize a
 * diagnosable errorTail from the exit code + the independently-captured stderr
 * tail — so the dashboard shows WHY the step failed and Phase 2 signature
 * matching can read the signature. When the log has real content, return the
 * log tail (existing behaviour). A timeout always wins.
 *
 * @returns {string|null} null only when there is nothing to report.
 */
export function buildErrorTail({ logContent, stderrTail, exitCode, timedOut, timeoutMin, resultTag } = {}) {
  if (timedOut) {
    // L010 residual: tag-present timeouts are salvaged by engine.js `resolvedOk`
    // (extractTag hits the marker even after a kill, so they never reach this
    // branch via !ok). This branch therefore serves tag-ABSENT timeouts — agent
    // truly hung with no usable output — where the dashboard previously showed
    // only a fixed "[TIMEOUT] ... no log output" string and "cause unknown".
    // Synthesize a diagnosable tail: the actual wall-clock minutes killed, the
    // last real log lines, the captured stderr tail, and whether the resultTag
    // is present (so a human can tell a no-output hang from an output-but-no-tag
    // hang). The default tag is AI_STEP_RESULT (standard); executor threads the
    // step's resultTag via opts for custom-tag flows (NB3: resolvedOk still
    // backstops tag-present cases, so a stale default is harmless).
    const mins = timeoutMin != null ? timeoutMin : Math.round(BASE_TIMEOUT_MS / 60000);
    const tag = resultTag || "AI_STEP_RESULT";
    const logStr = logContent != null ? String(logContent) : "";
    const tagPresent = new RegExp(`<${tag.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}>`, "i").test(logStr);
    const logTailLines = logStr.split("\n").filter((l) => l.length > 0 && !l.startsWith("#")).slice(-LOG_TAIL_LINES);
    const stderr = stderrTail ? String(stderrTail) : "";
    const parts = [
      `[TIMEOUT] Process killed after ${mins}min of no log output`,
      `resultTag <${tag}> present: ${tagPresent ? "yes" : "no"}`,
    ];
    if (logTailLines.length > 0) {
      parts.push(`last log lines:\n${logTailLines.join("\n")}`);
    }
    if (stderr) {
      parts.push(`stderr tail:\n${stderr}`);
    }
    return parts.join("\n");
  }
  let logTail = null;
  let headerOnly = true;
  if (logContent != null) {
    const lines = String(logContent).split("\n").filter(Boolean);
    logTail = lines.slice(-LOG_TAIL_LINES).join("\n");
    headerOnly = logTail.length === 0 || logTail.split("\n").every((l) => l.startsWith("#"));
  }
  if (headerOnly) {
    const code = exitCode != null ? exitCode : -1;
    const tail = stderrTail ? String(stderrTail) : "";
    return `[exit=${code}] ${tail || "(no stderr captured)"}`;
  }
  return logTail;
}

/** Tail the captured stderr buffer into a compact string (last N lines, capped). */
function tailStderr(buf) {
  if (!buf) return "";
  return buf.split(/\r?\n/).filter(Boolean).slice(-STDERR_TAIL_LINES).join("\n").slice(-STDERR_TAIL_CHARS);
}
// OPT-7: a heartbeat setInterval freezes while the OS is asleep/hibernating.
// On wake the gap between two beats far exceeds LIVENESS_CHECK_MS. We treat an
// overshoot beyond this threshold (10min past the expected interval) as a
// system suspend. Normal beats have gap ≈ LIVENESS_CHECK_MS (5min), so they
// never trip; a real suspend (timer frozen for hours) overshoots by hours.
const SUSPEND_THRESHOLD_MS = 10 * 60_000;

// OPT-7: pure wall-clock jump detector. Returns { gapMs, overshootMs } when the
// gap between two heartbeats overshoots the expected interval by more than
// thresholdMs (i.e. the timer was frozen), or null otherwise. Backward clock
// skew (gap < 0, NTP correction) is ignored — not a suspend, just clock noise.
export function detectSuspendJump(now, lastBeatWallMs, intervalMs, thresholdMs = SUSPEND_THRESHOLD_MS) {
  if (!Number.isFinite(now) || !Number.isFinite(lastBeatWallMs) || !Number.isFinite(intervalMs)) return null;
  const gap = now - lastBeatWallMs;
  if (gap < 0) return null;
  const overshoot = gap - intervalMs;
  if (overshoot > thresholdMs) return { gapMs: gap, overshootMs: overshoot };
  return null;
}

// OPT-7: emit a `suspended` event to events.jsonl (monitor SSE stream) and
// notify the engine via the optional config.onSuspend callback so it can mark
// the current step record. Isolated so it is unit-testable without timers.
export function emitSuspendEvent(config, label, childPid, jump) {
  const eventsFile = config && config.runDir ? resolve(config.runDir, "events.jsonl") : null;
  const payload = {
    type: "suspended",
    ts: new Date().toISOString(),
    missionName: (config && config.missionName) || null,
    runId: config && config.runDir ? basename(config.runDir) : null,
    label,
    pid: childPid,
    gapMs: jump.gapMs,
    overshootMs: jump.overshootMs,
  };
  try { if (eventsFile) appendFileSync(eventsFile, JSON.stringify(payload) + "\n"); } catch {}
  if (config && typeof config.onSuspend === "function") {
    try { config.onSuspend(payload); } catch {}
  }
  return payload;
}

function killTree(pid) {
  killProcessTree(pid);
}

/**
 * dre-d7 G2 — resolve the per-step watchdog timeout. A positive finite
 * `opts.timeoutMs` overrides the 60min BASE_TIMEOUT_MS; any other value
 * (absent, 0, negative, non-numeric) falls back to the default so legacy
 * callers that never pass opts.timeoutMs are byte-for-byte unchanged.
 * Extracted as a pure function so the override/default logic is unit-testable
 * without spawning a subprocess (the deadline check runs inside a 5min
 * setInterval, which makes a real-spawn timeout test impractical).
 */
export function resolveTimeoutMs(opts = {}) {
  const v = opts.timeoutMs;
  return Number.isFinite(v) && v > 0 ? v : BASE_TIMEOUT_MS;
}

export function execute(config, label, cmd, args, opts = {}) {
  const logFile = genLogFile(config, label);
  const cwd = opts.cwd || config.projectRoot;

  // dre-d7 G2: per-step configurable timeout. opts.timeoutMs (when a positive
  // finite number) overrides the 60min BASE_TIMEOUT_MS; absent/invalid → default
  // (backward compatible). resultTag is threaded to buildErrorTail so the L010
  // tag-absent timeout diagnostic can report presence of the step's CUSTOM tag
  // (defaults to AI_STEP_RESULT for legacy callers).
  const effectiveTimeoutMs = resolveTimeoutMs(opts);
  const effectiveResultTag = opts.resultTag || null;

  mkdirSync(dirname(logFile), { recursive: true });
  const header = [
    `# cmd: ${cmd} ${args.map(summarizeArg).join(" ")}`,
    `# cwd: ${cwd}`,
    `# started: ${localDateTimeStr()}`,
    "",
  ].join("\n") + "\n";
  writeFileSync(logFile, header);

  const fd = openSync(logFile, "a");
  // mdr-3 Phase 2: opt-in stdin pipe. `opts.stdin` (a string) lets callers pass
  // an arbitrarily large prompt to the child via stdin instead of a positional
  // cmdline arg — Windows CreateProcess caps the cmdline at ~32767 chars, which
  // silently killed the opencode subprocess before it wrote any output (memory
  // L004, SEV1). When `opts.stdin` is absent (e.g. `runTool` for pnpm/mvn) stdin
  // stays "ignore" so the legacy behaviour is byte-for-byte unchanged.
  const useStdin = typeof opts.stdin === "string";
  let child;
  try {
    child = spawn(cmd, args, {
      cwd,
      // mdr-1 Phase 1: stderr captured on an INDEPENDENT pipe buffer (not
      // merged into the log-file fd). When the child writes nothing to stdout
      // before crashing, the log file is header-only and the old
      // `stdio: ["ignore", fd, fd]` left stderr unreachable — so errorTail was
      // empty and the engine misdiagnosed every empty-output crash as a rate
      // limit. The independent pipe keeps stderr available for both a
      // diagnosable errorTail and Phase 2 signature matching.
      // mdr-3: stdin is "pipe" only when the caller opted in via opts.stdin.
      stdio: [useStdin ? "pipe" : "ignore", fd, "pipe"],
      shell: opts.shell ?? false,
      detached: !IS_WIN32 && !opts.shell,
      // windowsHide: suppress the console window that Node otherwise pops when
      // launching a console-subsystem child (opencode / pnpm / mvn) on Windows.
      // Node's default is `false`; without this every real agent step flashes a
      // window (and worker spawns during local runs do too).
      windowsHide: true,
      env: { ...process.env, NO_COLOR: "1", FORCE_COLOR: "0", CLICOLOR: "0" },
    });
  } catch (err) {
    try { closeSync(fd); } catch {}
    return Promise.resolve({ ok: false, logFile, pid: null });
  }

  // mdr-3 Phase 2: write the prompt to the child's stdin then close it. opencode
  // reads a piped message from stdin when no positional message is given. Guard
  // against EPIPE (child exited before reading) and write errors so a dying
  // child can never crash the engine. `end()` (not `destroy()`) signals EOF so
  // the child finishes consuming the prompt; it does not affect the independent
  // stdout/stderr capture above.
  if (useStdin && child.stdin) {
    child.stdin.on("error", () => {}); // never let an EPIPE crash the engine
    try {
      child.stdin.end(opts.stdin);
    } catch {}
  }

  // Collect stderr into a capped rolling buffer so a long-running opencode
  // process cannot deadlock the pipe, while still retaining the most recent
  // stderr (the part that carries rate-limit / crash signatures).
  let stderrBuf = "";
  if (child.stderr) {
    child.stderr.on("data", (chunk) => {
      stderrBuf += chunk.toString("utf8");
      if (stderrBuf.length > STDERR_CAP) {
        stderrBuf = stderrBuf.slice(-STDERR_CAP);
      }
    });
    child.stderr.on("error", () => {}); // never let an EPIPE crash the engine
  }

  const childPid = child.pid;
  if (opts.onSpawn) opts.onSpawn(childPid, logFile);

  let lastLogSize = statSync(logFile).size;
  let deadline = Date.now() + effectiveTimeoutMs;
  // OPT-7: track the wall-clock of the last heartbeat so we can detect a
  // setInterval freeze (OS sleep): on wake the gap between beats overshoots
  // the expected interval by far more than normal jitter.
  let lastBeatWallMs = Date.now();
  let progressTimer = null;
  let sigkillTimer = null;
  let settled = false;
  let timedOut = false;

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

      const beatNow = Date.now();
      // OPT-7: wall-clock jump detection — setInterval freezes during system
      // sleep; on wake the gap between beats far exceeds the interval. A normal
      // beat has gap ≈ LIVENESS_CHECK_MS (5min); a suspend overshoots by hours.
      const jump = detectSuspendJump(beatNow, lastBeatWallMs, LIVENESS_CHECK_MS);
      lastBeatWallMs = beatNow;
      if (jump) {
        process.stderr.write(`  [SUSPEND] ${label} wall-clock gap ${Math.round(jump.gapMs / 60_000)}min (overshoot ${Math.round(jump.overshootMs / 60_000)}min) — system likely suspended\n`);
        emitSuspendEvent(config, label, childPid, jump);
      }

      let currentSize = 0;
      try { currentSize = statSync(logFile).size; } catch {}
      if (currentSize > lastLogSize) {
        lastLogSize = currentSize;
        deadline = Date.now() + effectiveTimeoutMs;
      }

      const ts = localTimeStr();
      const remainMin = Math.max(0, Math.round((deadline - Date.now()) / 60_000));
      process.stderr.write(`  [${ts}] ${label} running ... (pid ${childPid}, timeout in ${remainMin}min)\n`);

      try { sysSnapshot(config.runDir, `heartbeat:${label}`); } catch {}

      // Refresh this run's heartbeat in the global active-run registry so other
      // concurrent runs' reapers keep recognizing us as alive. Best-effort; a
      // missing entry (run never registered, e.g. missionName=null) is a no-op.
      try {
        if (config.runDir) touchActiveRun(basename(config.runDir), process.pid);
      } catch {}

      // Append heartbeat event to events.jsonl (monitoring event stream, FSD §4.1.3)
      try {
        if (config.runDir) {
          const eventsFile = resolve(config.runDir, "events.jsonl");
          appendFileSync(eventsFile, JSON.stringify({
            type: "heartbeat",
            ts: new Date().toISOString(),
            missionName: config.missionName || null,
            runId: basename(config.runDir),
            label,
            pid: childPid,
            timeoutMin: remainMin,
            logSizeBytes: currentSize,
            freeMemGB: toGB(freemem()),
          }) + "\n");
        }
      } catch {}

      // Append log tail lines to stderr (FSD §4.3 — 控制台心跳日志尾部增强)
      const tail = tailLines(logFile, currentSize);
      for (const tl of tail) {
        process.stderr.write(`  │ tail: ${tl}\n`);
      }

      if (Date.now() > deadline) {
        const killedMin = Math.round(effectiveTimeoutMs / 60000);
        process.stderr.write(`  [TIMEOUT] ${label} no output for ${killedMin}min, killing process tree ${childPid}\n`);
        timedOut = true;
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
      try { reapProcessGroup(childPid, config.runDir, childPid); } catch {}
      const ok = code === 0;
      // mdr-1 Phase 1: a tail of the independently-captured stderr, exposed on
      // the result so Phase 2 signature matching can classify the failure
      // regardless of whether the log file has real content.
      const stderrTail = tailStderr(stderrBuf);
      let errorTail = null;
      if (!ok) {
        let logContent = null;
        try { logContent = readFileSync(logFile, "utf8"); } catch {}
        errorTail = buildErrorTail({
          logContent, stderrTail, exitCode: code ?? -1, timedOut,
          timeoutMin: Math.round(effectiveTimeoutMs / 60000),
          resultTag: effectiveResultTag,
        });
      }
      resolveFn({ ok, logFile, pid: childPid, exitCode: code ?? -1, errorTail, stderrTail: stderrTail || null });
    });

    child.on("error", (err) => {
      if (settled) return;
      settled = true;
      cleanup();
      try { reapProcessGroup(childPid, config.runDir, childPid); } catch {}
      resolveFn({ ok: false, logFile, pid: childPid, exitCode: -1, errorTail: `[SPAWN_ERROR] ${err.message}`, stderrTail: tailStderr(stderrBuf) || null });
    });
  });
}
