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
 * 通过文件描述符重定向 stdout/stderr → 日志文件，等价于 shell 重定向
 * `>>file 2>&1`，但避免 shell 转义问题。子进程的输出与终端执行一致。
 *
 * @param {object}   config         – 全局配置 (必须有 runDir)
 * @param {string}   label          – 日志文件标签 (如 "mvnw", "oc-deep-audit")
 * @param {string}   cmd            – 可执行路径 (如 "./mvnw", "opencode")
 * @param {string[]} args           – 命令行参数
 * @param {object}   [opts]
 * @param {string}   [opts.cwd]     – 工作目录 (默认 config.projectRoot)
 * @param {number}   [opts.timeout] – 超时毫秒 (0 = 不限, 默认 0)
 * @param {boolean}  [opts.quiet]   – 禁止进度输出 (默认 false)
 * @returns {{ ok: boolean, logFile: string }}
 */
export function execute(config, label, cmd, args, opts = {}) {
  const logFile = genLogFile(config, label);
  const cwd = opts.cwd || config.projectRoot;
  const timeout = opts.timeout || 0;

  // 日志文件头
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

  // 每 60s 进度
  let progressTimer = null;
  if (!opts.quiet) {
    progressTimer = setInterval(() => {
      if (child.exitCode === null) {
        const ts = new Date().toISOString().slice(11, 19);
        process.stderr.write(`  [${ts}] ${label} 运行中 ... (pid ${child.pid})\n`);
      }
    }, 60000);
  }

  // 超时终止: SIGTERM → SIGKILL fallback
  let timeoutTimer = null;
  let sigkillTimer = null;
  if (timeout > 0) {
    timeoutTimer = setTimeout(() => {
      process.stderr.write(`  [TIMEOUT] ${label} 超时 ${timeout}ms，终止进程\n`);
      try { child.kill("SIGTERM"); } catch { }
      sigkillTimer = setTimeout(() => {
        process.stderr.write(`  [TIMEOUT] ${label} SIGTERM 后 10s 未退出，发送 SIGKILL\n`);
        try { child.kill("SIGKILL"); } catch { }
      }, SIGKILL_DELAY);
    }, timeout);
  }

  // Watchdog: 定期 spawn 独立子 agent 智能检查子进程状态
  // 不在 driver 侧做 mtime/内容检测（fd 重定向日志有心跳噪声，不可靠），
  // 而是由独立子 agent 查看子进程的 opencode 内部日志来智能判断
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

        process.stderr.write(`  [WATCHDOG] ${label} 运行 ${Math.round(elapsed / 60_000)} 分钟，spawn 独立子 agent 检查状态 ...\n`);
        appendFileSync(logFile, `\n# watchdog: 运行 ${Math.round(elapsed / 60_000)}min，spawn 检查 agent @ ${new Date().toISOString()}\n`);

        const wdResult = await spawnWatchdogAgent(config, label, logFile, child.pid);

        const summary = `action=${wdResult.action} diagnosis=${wdResult.diagnosis} log=${wdResult.logFile || "N/A"}`;
        appendFileSync(logFile, `# watchdog result: ${summary} @ ${new Date().toISOString()}\n`);
        process.stderr.write(`  [WATCHDOG] ${label} → ${summary}\n`);

        lastWatchdogTime = Date.now();
        watchdogRunning = false;
      } catch (e) {
        watchdogRunning = false;
        process.stderr.write(`  [WATCHDOG] 异常: ${e.message}\n`);
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
 * Spawn 一个独立 opencode run 子 agent，让它自己判断是否需要 kill 卡住的进程。
 *
 * 关键设计：主 driver 不做 kill 决定，也不执行 kill。
 * 子 agent 通过 bash 工具自行检查日志、判断状态、决定是否 kill。
 * 如果子 agent 自身也失败/超时，对主 driver 无影响（只是 watchdog 没生效）。
 *
 * @returns {{ action: string, diagnosis: string, logFile: string|null }}
 */
function spawnWatchdogAgent(config, label, stalledLogFile, stalledPid) {
  const driverPid = process.pid;

  const prompt = [
    `你是 opencode goal driver 的独立 watchdog 子 agent。`,
    ``,
    `## 背景`,
    `一个名为 "${label}" 的子 agent (PID=${stalledPid}) 已运行超过 30 分钟，需要检查其状态。`,
    `主 driver 进程 PID=${driverPid}。`,
    `fd 重定向日志: ${stalledLogFile}`,
    ``,
    `## 你的任务`,
    `请自行检查并处理，不要请求确认。`,
    ``,
    `### 步骤 1: 诊断`,
    `1. 用 bash 执行 \`ps -o pid,ppid,stat,etime -p ${stalledPid}\` 检查进程是否仍在运行`,
    `2. 找到子进程的 opencode 内部日志：`,
    `   - 执行 \`lsof -p ${stalledPid} -Fn 2>/dev/null | grep 'opencode/log' | grep '.log'\` 找到日志文件路径`,
    `   - 或 \`ls -t ~/.local/share/opencode/log/*.log | head -3\` 找最近的日志文件`,
    `3. 读取 opencode 内部日志的最后 80 行（这是结构化日志，没有心跳噪声）`,
    `4. 判断子 agent 状态：`,
    `   - 内部日志有近期的 tool 调用（service=tool）、LLM 响应（service=llm）→ 正常工作中，不需要 kill`,
    `   - 内部日志长时间只有 service=bus 的心跳消息，无 tool/llm 活动 → LLM 调用挂起`,
    `   - 内部日志有 # exit: 或进程已不存在 → 已完成但信号丢失（僵尸态）`,
    `5. 如有疑问，再读 fd 重定向日志 ${stalledLogFile} 的最后 50 行作为补充`,
    ``,
    `### 步骤 2: 决策与执行`,
    `如果诊断确认子 agent 卡住/僵死/已完成但信号丢失：`,
    `  用 bash 执行 \`kill ${stalledPid}\` 来终止卡住的子 agent`,
    `  然后执行 \`kill -0 ${stalledPid} 2>/dev/null && echo 'still alive' || echo 'dead'\` 确认已终止`,
    ``,
    `如果诊断发现子 agent 正常工作中：`,
    `  不做任何操作`,
    ``,
    `### 绝对禁止`,
    `- 只能 kill PID=${stalledPid}（子 agent），绝对不能 kill PID=${driverPid}（主 driver）`,
    `- 不能 kill 任何其他进程`,
    `- 如果 ${stalledPid} 进程已不存在，不需要做任何操作`,
    ``,
    `### 输出`,
    `完成后输出：`,
    `<WATCHDOG_RESULT>`,
    `  diagnosis: 一句话诊断`,
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
    const fallback = { action: "unknown", diagnosis: "watchdog agent 未正常完成", logFile: wdLogFile };
    const done = (result) => {
      if (settled) return;
      settled = true;
      res(result);
    };

    let wdFd;
    try {
      wdFd = openSync(wdLogFile, "a");
    } catch (e) {
      process.stderr.write(`  [WATCHDOG] 打开日志文件失败: ${e.message}\n`);
      done({ ...fallback, diagnosis: `watchdog agent 日志文件打开失败: ${e.message}` });
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
        process.stderr.write(`  [WATCHDOG] 检查 agent 自身超时 (10min)，强制终止\n`);
        try { wdChild.kill("SIGTERM"); } catch { }
        wdSigkillTimer = setTimeout(() => {
          try { wdChild.kill("SIGKILL"); } catch { }
        }, SIGKILL_DELAY);
        try { closeSync(wdFd); } catch { }
        done({ ...fallback, diagnosis: "watchdog agent 自身超时" });
      }, 10 * 60_000);

      wdChild.on("close", () => {
        clearTimeout(wdTimeout);
        if (wdSigkillTimer) clearTimeout(wdSigkillTimer);
        try { closeSync(wdFd); } catch { }
        appendFileSync(wdLogFile, `# watchdog agent finished: ${new Date().toISOString()}\n`);
        process.stderr.write(`  [WATCHDOG] 检查 agent 执行完毕，日志: ${wdLogFile}\n`);

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
        done({ ...fallback, diagnosis: "watchdog agent 未输出 WATCHDOG_RESULT 标签" });
      });

      wdChild.on("error", (err) => {
        clearTimeout(wdTimeout);
        try { closeSync(wdFd); } catch { }
        process.stderr.write(`  [WATCHDOG] 检查 agent 启动失败: ${err.message}\n`);
        done({ ...fallback, diagnosis: `watchdog agent 启动失败: ${err.message}` });
      });
    } catch (e) {
      try { closeSync(wdFd); } catch { }
      process.stderr.write(`  [WATCHDOG] spawn 异常: ${e.message}\n`);
      done({ ...fallback, diagnosis: `watchdog agent spawn 异常: ${e.message}` });
    }
  });
}
