/**
 * draft-job.mjs — 异步 Mission Draft Job 子系统 (mdo-2 Phase 1).
 *
 * 让 `mission-driver draft <desc>` 可以作为后台子进程异步运行，并通过
 * `_tmp/{jobId}/draft-state.json` 暴露运行/完成/失败状态给 Monitor 与前端。
 *
 * 三个导出函数：
 *   - startDraftJob — 创建 jobDir，写 running state，spawn 子进程，返回 {jobId, pid}
 *   - readDraftJob  — 读回 state + draft.log 尾部（轮询用）
 *   - listDraftJobs — 列 _tmp/draft-* 目录，按 mtime 倒序，默认 9 条
 *
 * spawn 经模块级可注入 spawner（仿 monitor.js:39 `__setSpawnerForTest`），便于测试。
 *
 * 零 npm 依赖（仅 node:fs / node:path / node:child_process）。
 */

import {
  mkdirSync,
  writeFileSync,
  readFileSync,
  existsSync,
  readdirSync,
  statSync,
} from "node:fs";
import { resolve, basename, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { getSpawner, __setSpawnerForTest } from "./spawner.mjs";

// Re-export the shared testability seam so draft-job.test.js can inject a fake
// spawner directly (mirrors monitor.js). ONE seam covers both the run-launch
// and draft-launch spawn sites (see spawner.mjs).
export { __setSpawnerForTest };

const __dirname = dirname(fileURLToPath(import.meta.url));

/** draft.log 尾部最多读取的行数（与 monitor handleGetNodeDetail 的 200 行一致）。 */
const LOG_TAIL_LINES = 200;

/**
 * Deterministic pre-validation of the draft description (draft-robustness-design
 * §4.1 / WI1). Rejects empty / placeholder / too-short descriptions BEFORE
 * Stage 1 so the agent cannot pollute `docs/backlog/` and `missions/` with junk
 * artifacts. `minLen` accepts a value from `base.json`'s `draft.minDescLength`
 * but falls back to 4 when the value is missing, non-finite, or non-positive
 * (defends against a mistyped config like `"garbage"` / `null` / `NaN`).
 *
 * Deviation from design §4.1: placeholder check fires BEFORE length check.
 * Design's empty→length→placeholder order leaves 3-char blacklist entries
 * (`xxx`, `foo`, `bar`, `n/a`) unreachable — they always trip length first.
 * Swapping to empty→placeholder→length makes the blacklist actually useful,
 * since "xxx" is a more actionable rejection reason than "too short".
 *
 * NOT a semantic check — "is the description meaningful" is WI2's brief gate.
 *
 * Lives in this leaf module (not `main.js`) so `monitor.js` can import it
 * without forming a `monitor.js → main.js → monitor.js` cycle (`main.js`
 * statically imports `startMonitor` from `./monitor.js` at its top level).
 * `main.js` re-exports this function so existing consumers (e.g. test
 * imports from `main.js`) are unaffected.
 */
export function validateDraftDesc(desc, minLen) {
  const threshold = Number.isFinite(+minLen) && +minLen > 0 ? +minLen : 4;
  const trimmed = String(desc ?? "").trim();
  if (trimmed.length === 0) {
    return { ok: false, reason: "description is empty" };
  }
  if (/^(test|asdf|foo|bar|todo|xxx|none|null|n\/a)$/i.test(trimmed)) {
    return { ok: false, reason: `description looks like a placeholder ("${trimmed}")` };
  }
  if (trimmed.length < threshold) {
    return { ok: false, reason: `description too short (${trimmed.length} chars); need at least a phrase describing the mission goal` };
  }
  return { ok: true };
}

/**
 * Build a compact timestamp for the jobId: `YYYYMMDD-HHmmss-sss`.
 *
 * Millisecond precision (mdo-2) avoids jobId collisions when two draft jobs
 * start in the same second (e.g. a rapid double-submit from the UI); previously
 * second-precision let the second job overwrite the first's jobDir. The shape
 * keeps the `draft-` prefix / `-mission-draft` suffix that listDraftJobs keys on.
 */
function tsForJobId(now = new Date()) {
  const pad = (n, w = 2) => String(n).padStart(w, "0");
  return (
    `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-` +
    `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}-` +
    `${pad(now.getMilliseconds(), 3)}`
  );
}

/**
 * Start an async Mission Draft job.
 *
 * @param {object} opts
 * @param {string} opts.projectRoot
 * @param {string} opts.desc              Mission 描述（非空字符串，调用方负责校验）
 * @param {string} [opts.draftJobDir]     可选：固定 jobDir（测试用）；缺省生成 `_tmp/{jobId}`
 * @param {string} [opts.flowHint]        可选：用户/向导选择的 flow 名（mdo-4 P2）
 * @param {string} [opts.targetFile]      可选：目标文件项目相对路径（mdo-4 P2）
 * @param {boolean} [opts.skipBrief]      可选：跳过 brief 阶段，直接 draft（向后兼容）
 * @returns {{ jobId: string, pid: number|null, jobDir: string }}
 */
export function startDraftJob({ projectRoot, desc, draftJobDir, flowHint, targetFile, skipBrief } = {}) {
  const jobId = `draft-${tsForJobId()}-mission-draft`;
  const jobDir = draftJobDir || resolve(projectRoot, "_tmp", jobId);
  mkdirSync(jobDir, { recursive: true });

  const startedAt = new Date().toISOString();
  const state = {
    jobId,
    status: "running",
    startedAt,
    desc,
    // mdo-4 P2: phase starts at "brief" (two-stage) unless skipBrief collapses
    // to the legacy single "draft" stage. flowHint/targetFile are surfaced so
    // the wizard can display what was submitted.
    phase: skipBrief ? "draft" : "brief",
    flowHint: flowHint || null,
    targetFile: targetFile || null,
  };
  writeFileSync(resolve(jobDir, "draft-state.json"), JSON.stringify(state, null, 2));

  // Spawn `node main.js draft <desc> --draft-job-dir <jobDir> [...]` detached so
  // the draft outlives the monitor process. process.execPath = current node
  // binary; stdio ignored because cmdDraftMission writes its own draft-state.json
  // + log. mdo-4 P2: conditionally append --flow-hint / --target-file / --skip-brief
  // so cmdDraftMission receives the wizard's selections (argv is explicit + testable,
  // mirroring the existing --draft-job-dir pattern).
  const mainJsPath = resolve(__dirname, "main.js");
  const argv = [mainJsPath, "draft", desc, "--draft-job-dir", jobDir];
  if (flowHint) argv.push("--flow-hint", flowHint);
  if (targetFile) argv.push("--target-file", targetFile);
  if (skipBrief) argv.push("--skip-brief");
  const child = getSpawner()(
    process.execPath,
    argv,
    { shell: false, detached: true, stdio: "ignore", windowsHide: true },
  );
  child.unref();

  return { jobId, pid: typeof child.pid === "number" ? child.pid : null, jobDir };
}

/**
 * Resolve a jobId to its on-disk jobDir under `_tmp/`. jobId is basename-cleaned
 * (path-traversal defence, mirroring findRunDir in monitor.js:219-224).
 * @returns {string|null}
 */
function resolveJobDir(projectRoot, jobId) {
  const safe = basename(jobId);
  if (!safe || safe === "." || safe === ".." || safe.includes("..")) return null;
  const candidate = resolve(projectRoot, "_tmp", safe);
  if (existsSync(candidate) && statSync(candidate).isDirectory()) return candidate;
  return null;
}

/**
 * Read a draft job's state + draft log tail.
 *
 * @param {string} projectRoot
 * @param {string} jobId
 * @returns {{ state: object|null, logTail: string|null, jobId: string }}
 *   `state` is null when the jobDir/draft-state.json is missing (caller → 404).
 */
export function readDraftJob(projectRoot, jobId) {
  const jobDir = resolveJobDir(projectRoot, jobId);
  if (!jobDir) return { state: null, logTail: null, jobId: basename(jobId) };

  let state = null;
  const stateFile = resolve(jobDir, "draft-state.json");
  if (existsSync(stateFile)) {
    try {
      state = JSON.parse(readFileSync(stateFile, "utf8"));
    } catch {
      state = null;
    }
  }

  // draft.log is produced by cmdDraftMission via the runner (config.js
  // draftMission branch → logFile = runDir/mission-draft.log). Prefer the real
  // artifact name; fall back to the shorthand `draft.log` for robustness.
  let logTail = null;
  const logFile =
    existsSync(resolve(jobDir, "mission-draft.log"))
      ? resolve(jobDir, "mission-draft.log")
      : existsSync(resolve(jobDir, "draft.log"))
        ? resolve(jobDir, "draft.log")
        : null;
  if (logFile) {
    try {
      const content = readFileSync(logFile, "utf8");
      const lines = content.split(/\r?\n/);
      logTail = lines.slice(Math.max(0, lines.length - LOG_TAIL_LINES)).join("\n");
    } catch {
      logTail = null;
    }
  }

  return { state, logTail, jobId: basename(jobId) };
}

/**
 * List recent draft jobs under `_tmp/draft-*`, newest first.
 *
 * @param {string} projectRoot
 * @param {number} [limit=9]  与 mdo-1 configs 分页 limit 9 对齐。
 * @returns {{ jobs: Array<{ jobId: string, status: string, startedAt: string|null, desc: string|null, mtime: number }> }}
 */
export function listDraftJobs(projectRoot, limit = 9) {
  const tmpDir = resolve(projectRoot, "_tmp");
  if (!existsSync(tmpDir)) return { jobs: [] };
  let names;
  try {
    names = readdirSync(tmpDir).filter((f) => f.startsWith("draft-") && f.endsWith("-mission-draft"));
  } catch {
    return { jobs: [] };
  }
  const entries = names
    .map((name) => {
      const dir = resolve(tmpDir, name);
      let mtime = 0;
      try {
        mtime = statSync(dir).mtimeMs;
      } catch {
        mtime = 0;
      }
      // Surface lightweight summary fields; full state via readDraftJob.
      let status = "unknown";
      let startedAt = null;
      let desc = null;
      try {
        const st = JSON.parse(readFileSync(resolve(dir, "draft-state.json"), "utf8"));
        status = st.status || "unknown";
        startedAt = st.startedAt || null;
        desc = st.desc || null;
      } catch {
        // missing/corrupt state — keep defaults
      }
      return { jobId: name, status, startedAt, desc, mtime };
    })
    .sort((a, b) => b.mtime - a.mtime);

  const cap = Math.max(1, Math.min(100, Number(limit) || 9));
  return { jobs: entries.slice(0, cap) };
}
