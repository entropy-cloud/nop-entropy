import { appendFileSync, closeSync, mkdirSync, openSync, writeFileSync } from "node:fs";
import { spawn } from "node:child_process";
import { dirname, resolve } from "node:path";

function genLogFile(config, label) {
  const ts = Date.now();
  const rand = Math.random().toString(36).slice(2, 8);
  return resolve(config.runDir, `${label}-${ts}-${rand}.log`);
}

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

  // 以追加模式打开日志文件，用作子进程的 stdout/stderr
  // 等价于 shell 的 >>logFile 2>&1，但避免 shell 转义问题
  const fd = openSync(logFile, "a");
  const child = spawn(cmd, args, { cwd, stdio: ["ignore", fd, fd], shell: false });

  // 每 30s 进度
  let progressTimer = null;
  if (!opts.quiet) {
    progressTimer = setInterval(() => {
      if (child.exitCode === null) {
        const ts = new Date().toISOString().slice(11, 19);
        process.stderr.write(`  [${ts}] ${label} 运行中 ... (pid ${child.pid})\n`);
      }
    }, 60000);
  }

  // 超时终止
  let timeoutTimer = null;
  if (timeout > 0) {
    timeoutTimer = setTimeout(() => {
      process.stderr.write(`  [TIMEOUT] ${label} 超时 ${timeout}ms，终止进程\n`);
      child.kill("SIGTERM");
    }, timeout);
  }

  return new Promise((resolveFn) => {
    child.on("close", (code) => {
      if (progressTimer) clearInterval(progressTimer);
      if (timeoutTimer) clearTimeout(timeoutTimer);
      try { closeSync(fd); } catch { }
      appendFileSync(logFile, `# exit: ${code}\n# finished: ${new Date().toISOString()}\n`);
      resolveFn({ ok: code === 0, logFile });
    });

    child.on("error", (err) => {
      if (progressTimer) clearInterval(progressTimer);
      if (timeoutTimer) clearTimeout(timeoutTimer);
      try { closeSync(fd); } catch { }
      appendFileSync(logFile, `# error: ${err.message}\n# finished: ${new Date().toISOString()}\n`);
      resolveFn({ ok: false, logFile });
    });
  });
}
