/**
 * mdo-3 Phase 1 — Reusable postmortem runner.
 *
 * Extracted verbatim (behaviour-preserving) from `main.js:cmdAnalyzeRun` so the
 * engine terminal-state hook (Phase 3) can drive the SAME postmortem pipeline
 * without duplicating the prompt-building / module-detection / tag-parsing
 * logic (FSD §3.3.3A).
 *
 * Contract (FSD §3.3.3A / §3.3.3D):
 *   runPostmortem({ projectRoot, missionsDir, targetRunDir, targetRunId, runner, opts })
 *     → { postmortemFile, memoryUpdated, text }
 *
 * `runner` is an object carrying `runAgent` (aligned with engine delegates'
 * `{runAgent}` shape and main.js's createRunner return — Phase 1 Decision:
 * pass the runner OBJECT, not just the fn, so future hooks can reuse `close`
 * etc. without a signature change).
 *
 * `opts.moduleInfo` may be supplied by the CLI analyze path (already resolved
 * by config.js). When absent — the engine terminal-hook path — the module is
 * re-resolved from `targetRunDir` via resolveRunModule, keeping engine.js free
 * of a config.js import (single responsibility: postmortem owns module detect).
 */
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { buildRunSkeleton, resolveRunModule } from "./config.js";
import { resolveTemplateVars } from "./expression.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));

// FSD §3.3.3D: parse the postmortem return tags the agent emits. Case-
// insensitive + tolerant of surrounding whitespace, mirroring extractTag
// semantics but local (no engine import → postmortem stays standalone).
const POSTMORTEM_FILE_RE = /<POSTMORTEM_FILE>\s*([^\s<]+)\s*<\/POSTMORTEM_FILE>/i;
const MEMORY_UPDATED_RE = /<MEMORY_UPDATED>([^<]+)<\/MEMORY_UPDATED>/i;

export async function runPostmortem({ projectRoot, missionsDir, targetRunDir, targetRunId, runner, opts } = {}) {
  if (!runner || typeof runner.runAgent !== "function") {
    throw new Error("runPostmortem: runner.runAgent is required");
  }
  if (!targetRunDir) {
    throw new Error("runPostmortem: targetRunDir is required");
  }

  // Module detection. CLI path passes the already-resolved moduleInfo (from
  // config.js analyzeRun branch); terminal-hook path omits it, so re-resolve
  // from the run dir here (FSD §3.3.3A — self-postmortem reuses the same
  // module mapping as the manual analyze command).
  let moduleInfo = opts && opts.moduleInfo ? opts.moduleInfo : null;
  if (!moduleInfo) {
    try {
      moduleInfo = resolveRunModule(projectRoot, missionsDir, targetRunDir);
    } catch {
      moduleInfo = null;
    }
  }
  const moduleMemoryDir = (moduleInfo && moduleInfo.moduleMemoryDir) || "";
  const moduleName = (moduleInfo && moduleInfo.moduleName) || "(unknown)";

  const promptFile = resolve(__dirname, "..", "prompts", "run-postmortem.md");
  const rawPrompt = readFileSync(promptFile, "utf8");

  // buildRunSkeleton degrades gracefully (partial skeleton) when run-state /
  // events are missing — no try/catch needed (FSD §3.3.4 boundary).
  const skeleton = buildRunSkeleton(targetRunDir);

  const selfMemoryDir = resolve(__dirname, "..", "memory");
  const prompt = resolveTemplateVars(rawPrompt, {
    projectRoot,
    targetRunDir,
    targetRunId,
    postmortemDir: resolve(projectRoot, "tools/mission-driver/docs/postmortems"),
    selfMemoryDir,
    moduleMemoryDir,
    moduleName,
    runSkeleton: skeleton,
  });

  const result = await runner.runAgent("analyze-run", prompt, "", null);
  const text = (result && result.text) || "";

  let postmortemFile = null;
  let memoryUpdated = null;
  const pfMatch = text.match(POSTMORTEM_FILE_RE);
  if (pfMatch) postmortemFile = pfMatch[1].trim();
  const muMatch = text.match(MEMORY_UPDATED_RE);
  if (muMatch) memoryUpdated = muMatch[1].trim();

  return { postmortemFile, memoryUpdated, text };
}
