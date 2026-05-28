import { existsSync, mkdirSync } from "node:fs";
import { resolve } from "node:path";

export function resolveConfig(args = {}) {
  const projectRoot = args.dir || process.env.PROJECT_ROOT || resolve(import.meta.dirname, "../../../..");
  const moduleName = args.module || "";
  const dryRun = args.dryRun === true;
  const testMode = args.testMode === true;

  const agent = args.agent || process.env.OPENCODE_AGENT || "build";
  const model = args.model || process.env.OPENCODE_MODEL || "zhipuai-coding-plan/glm-5.1";
  const maxCycles = args.maxCycles || Number(process.env.MAX_CYCLES) || 10;
  const maxInnerCycles = args.maxInnerCycles || Number(process.env.MAX_INNER_CYCLES) || 5;

  if (!moduleName) throw new Error("module name is required");

  const moduleDir = resolve(projectRoot, moduleName);
  if (!existsSync(moduleDir)) throw new Error(`module '${moduleName}' not found at ${moduleDir}`);

  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const ts = `${now.getFullYear()}-${pad(now.getMonth()+1)}-${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const runDir = resolve(projectRoot, `_tmp/${ts}-goal-driver`);
  mkdirSync(runDir, { recursive: true });

  return { projectRoot, moduleName, moduleDir, runDir,
           agent, model, maxCycles, maxInnerCycles, dryRun, testMode,
           logFile: resolve(runDir, `${moduleName}.log`) };
}
