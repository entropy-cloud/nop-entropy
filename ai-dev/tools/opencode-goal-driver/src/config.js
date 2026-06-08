import { existsSync, mkdirSync, readdirSync, statSync } from "node:fs";
import { resolve, join } from "node:path";

function findModuleDir(projectRoot, moduleName) {
  const direct = resolve(projectRoot, moduleName);
  if (existsSync(direct) && statSync(direct).isDirectory()) return direct;

  try {
    for (const entry of readdirSync(projectRoot, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue;
      const candidate = resolve(projectRoot, entry.name, moduleName);
      if (existsSync(candidate) && statSync(candidate).isDirectory()) return candidate;
    }
  } catch {}

  return null;
}

export function resolveConfig(args = {}) {
  const projectRoot = args.dir || process.env.PROJECT_ROOT || resolve(import.meta.dirname, "../../../..");
  const moduleName = args.module || "";
  const dryRun = args.dryRun === true;
  const testMode = args.testMode === true;

  const agent = args.agent || process.env.OPENCODE_AGENT || "build";
  const model = args.model || process.env.OPENCODE_MODEL || "zhipuai-coding-plan/glm-5.1";
  const maxCycles = args.maxCycles || Number(process.env.MAX_CYCLES) || 10;
  const maxInnerCycles = args.maxInnerCycles || Number(process.env.MAX_INNER_CYCLES) || 5;
  const _wdInterval = Number(process.env.WATCHDOG_INTERVAL_MS);
  const _wdStall = Number(process.env.WATCHDOG_STALL_MS);
  const watchdogIntervalMs = Number.isNaN(_wdInterval) ? 5 * 60_000 : _wdInterval;
  const watchdogStallMs = Number.isNaN(_wdStall) ? 30 * 60_000 : _wdStall;

  if (!moduleName) throw new Error("module name is required");

  const moduleDir = args.moduleDir
    ? resolve(projectRoot, args.moduleDir)
    : findModuleDir(projectRoot, moduleName);
  if (!moduleDir) throw new Error(`module '${moduleName}' not found under ${projectRoot} (searched root and one level deep)`);

  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const ts = `${now.getFullYear()}-${pad(now.getMonth()+1)}-${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const runDir = resolve(projectRoot, `_tmp/${ts}-goal-driver`);
  mkdirSync(runDir, { recursive: true });

  return { projectRoot, moduleName, moduleDir, runDir,
           agent, model, maxCycles, maxInnerCycles, dryRun, testMode,
           watchdogIntervalMs, watchdogStallMs,
           logFile: resolve(runDir, `${moduleName}.log`) };
}
