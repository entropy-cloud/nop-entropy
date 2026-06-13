import { readFileSync, readdirSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const TOOL_ROOT = resolve(__dirname, "..");

const PLAN_STATUS_RE = /^>\s*\*{0,2}(?:Plan\s+)?Status\*{0,2}:\s*\*{0,2}(.+?)\*{0,2}\s*$/m;
const ACTIVE_STATUSES = new Set([
  "in progress", "active", "planned", "partially completed",
]);

function planRouter(delegates, flowVars) {
  const projectRoot = delegates.config.projectRoot;
  const plansDir = resolve(projectRoot, "ai-dev", "plans");

  if (existsSync(plansDir)) {
    const files = readdirSync(plansDir)
      .filter(f => f.endsWith(".md") && !f.startsWith("00-"))
      .sort();

    for (const f of files) {
      const content = readFileSync(resolve(plansDir, f), "utf8");
      const m = content.match(PLAN_STATUS_RE);
      const status = m ? m[1].trim().toLowerCase() : "";
      if (ACTIVE_STATUSES.has(status)) {
        flowVars.set("PLAN_FILE", resolve(plansDir, f));
        return "execute";
      }
    }
  }

  return "roadmap";
}

async function closureScriptCheck(delegates, flowVars) {
  const planFile =
    flowVars?.get?.("PLAN_FILE") || delegates?.vars?.PLAN_FILE;
  if (!planFile) {
    console.error(
      "[closureScriptCheck] ERROR: no PLAN_FILE in flowVars — cannot verify specific plan"
    );
    return "fail";
  }

  try {
    const { inspectPlan } = await import(
      "../../check-plan-checklist.mjs"
    );
    const result = inspectPlan(planFile, { strict: true });

    if (result.passed) {
      if (flowVars?.set) flowVars.set("SCRIPT_CHECK_RESULT", "PASS");
      return "pass";
    }

    if (flowVars?.set) {
      flowVars.set("SCRIPT_CHECK_RESULT", "FAIL");
      flowVars.set("SCRIPT_CHECK_DETAILS", result.details.join("; "));
    }

    console.error(`[closureScriptCheck] FAIL: ${result.file}`);
    console.error(`  status: ${result.planStatus}`);
    console.error(`  ${result.totalUnchecked} unchecked / ${result.totalUnchecked + result.totalChecked} total`);
    if (result.planStatus === "completed" && result.totalUnchecked > 0) {
      console.error("  ERROR: plan is 'completed' but has unchecked items!");
    }
    for (const d of result.details) {
      console.error(`  - ${d}`);
    }
    for (const item of result.allUnchecked) {
      console.error(`    L${item.line}: - [ ] ${item.text}`);
    }
    return "fail";
  } catch (err) {
    console.error(`[closureScriptCheck] ERROR: ${err.message}`);
    return "fail";
  }
}

const SCRIPT_REGISTRY = {
  "plan-router": (delegates, flowVars) => planRouter(delegates, flowVars),
  "closure-script-check": (delegates, flowVars) => closureScriptCheck(delegates, flowVars),
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

const SUBFLOW_DIR = resolve(TOOL_ROOT, "flows");

export function loadSubFlow(name) {
  const baseDir = this?.config?.subflowDir || SUBFLOW_DIR;
  const filePath = resolve(baseDir, `${name}.json`);
  const raw = JSON.parse(readFileSync(filePath, "utf8"));
  resolveStepPrompts(raw.steps);
  resolveStepScripts(raw.steps);
  return raw;
}

export { SCRIPT_REGISTRY, TOOL_ROOT };
