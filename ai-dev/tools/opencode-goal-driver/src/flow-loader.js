import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { execSync } from "node:child_process";

const __dirname = dirname(fileURLToPath(import.meta.url));
const TOOL_ROOT = resolve(__dirname, "..");

function detectStartPhase(delegates) {
  const config = delegates.config;
  try {
    const output = execSync("node ai-dev/tools/check-plan-status.mjs", {
      cwd: config.projectRoot,
      encoding: "utf8",
      timeout: 30_000,
    });
    const activeMatch = output.match(/Active:\s*(\d+)/);
    const activeCount = activeMatch ? parseInt(activeMatch[1], 10) : 0;

    if (activeCount > 0) return "execute";

    try {
      const roadmaps = execSync(
        `ls ${config.projectRoot}/ai-dev/design/*${config.moduleName}*/*roadmap* ${config.projectRoot}/ai-dev/design/*roadmap*${config.moduleName}* 2>/dev/null || true`,
        { encoding: "utf8", timeout: 5_000 },
      ).trim();
      if (roadmaps) return "roadmap";
    } catch {}

    const auditsDir = `${config.projectRoot}/ai-dev/audits`;
    const recentAudit = execSync(
      `ls -td ${auditsDir}/*${config.moduleName}* 2>/dev/null | head -1`,
      { encoding: "utf8", timeout: 5_000 },
    ).trim();
    if (recentAudit) return "plan";

    return "audit";
  } catch {
    return "roadmap";
  }
}

async function closureScriptCheck(delegates) {
  const { execSync: es } = await import("node:child_process");
  try {
    es("node ai-dev/tools/check-plan-checklist.mjs --active-only --quiet --strict", {
      cwd: delegates.config.projectRoot,
      encoding: "utf8",
      timeout: 30_000,
    });
    return "pass";
  } catch {
    return "fail";
  }
}

const SCRIPT_REGISTRY = {
  "detect-start-phase": (delegates) => detectStartPhase(delegates),
  "closure-script-check": (delegates) => closureScriptCheck(delegates),
};

function loadPrompt(promptPath) {
  const absPath = resolve(TOOL_ROOT, promptPath);
  return readFileSync(absPath, "utf8");
}

function resolveStepPrompts(steps) {
  for (const step of Object.values(steps)) {
    if (step.promptPath) {
      step.prompt = loadPrompt(step.promptPath);
    }
    if (step.steps) {
      resolveStepPrompts(step.steps);
    }
  }
}

function resolveStepScripts(steps) {
  for (const [name, step] of Object.entries(steps)) {
    if (step.type === "script" && step.scriptId) {
      const impl = SCRIPT_REGISTRY[step.scriptId];
      if (!impl) throw new Error(`Unknown scriptId: ${step.scriptId} in step ${name}`);
      step.run = impl;
    }
    if (step.steps) {
      resolveStepScripts(step.steps);
    }
  }
}

export function createGoalDriverFlow(flowPath) {
  const resolvedPath = flowPath
    ? resolve(TOOL_ROOT, flowPath)
    : resolve(TOOL_ROOT, "flows/goal-driver.json");

  const raw = JSON.parse(readFileSync(resolvedPath, "utf8"));

  resolveStepPrompts(raw.steps);
  resolveStepScripts(raw.steps);

  return raw;
}

export { SCRIPT_REGISTRY, TOOL_ROOT };
