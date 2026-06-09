#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { resolveConfig } from "./config.js";
import { createRunner } from "./opencode-runner.js";
import { resetMockState } from "./mock-responses.js";
import { FlowEngine } from "./engine.js";
import * as scripts from "./scripts.js";

const __dirname = dirname(fileURLToPath(import.meta.url));

function loadFlow() {
  const flowPath = resolve(__dirname, "goal-driver-flow.json");
  const mainFlow = JSON.parse(readFileSync(flowPath, "utf8"));
  return mainFlow;
}

const subFlowCache = new Map();

async function loadSubFlow(name) {
  if (subFlowCache.has(name)) return subFlowCache.get(name);
  const knownFlows = {
    "plan-lifecycle": "plan-lifecycle-flow.json",
  };
  const fileName = knownFlows[name];
  if (!fileName) throw new Error(`unknown sub-flow: ${name}`);
  const path = resolve(__dirname, fileName);
  const def = JSON.parse(readFileSync(path, "utf8"));
  subFlowCache.set(name, def);
  return def;
}

function parseArgs(argv) {
  const args = { module: "", dryRun: false, testMode: false };
  let i = 2;
  while (i < argv.length) {
    const a = argv[i];
    if (a === "--dry-run") args.dryRun = true;
    else if (a === "--dir") args.dir = argv[++i];
    else if (a === "--agent") args.agent = argv[++i];
    else if (a === "--model") args.model = argv[++i];
    else if (a === "--max-cycles") args.maxCycles = Number(argv[++i]);
    else if (a === "--max-inner-cycles") args.maxInnerCycles = Number(argv[++i]);
    else if (a === "--test") args.testMode = true;
    else if (!a.startsWith("--")) args.module = a;
    i++;
  }
  return args;
}

async function main() {
  const args = parseArgs(process.argv);
  const config = resolveConfig(args);
  const runner = await createRunner(config);

  console.log(`Module:   ${config.moduleName}`);
  console.log(`Agent:    ${config.agent}`);
  console.log(`Model:    ${config.model}`);
  console.log(`DryRun:   ${config.dryRun}`);
  console.log(`TestMode: ${config.testMode}`);
  console.log(`Watchdog: interval=${config.watchdogIntervalMs / 1000}s stall=${config.watchdogStallMs / 1000}s`);
  console.log(`Log:      ${config.logFile}`);
  console.log("");

  try {
    const flow = loadFlow();
    const delegates = {
      config,
      vars: { module: config.moduleName, projectRoot: config.projectRoot },
      scripts,
      loadSubFlow,
      runAgent: runner.runAgent,
      runTool: runner.runTool,
      runParseAgent: runner.runParseAgent,
      logFile: config.logFile,
    };

    resetMockState();
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    console.log(`\n════════════════════════════════════════`);
    console.log(`  Module:    ${config.moduleName}`);
    console.log(`  Status:    ${result.status}`);
    console.log(`  Steps:     ${result.stepCount}`);
    console.log(`  Elapsed:   ${result.elapsed}`);
    console.log(`════════════════════════════════════════`);

    switch (result.status) {
      case "completed": process.exit(0);
      case "failed": process.exit(1);
      case "max_cycles":
      case "max_total_steps":
      case "max_retries":
        process.exit(2);
    }
  } finally {
    await runner.close();
  }
}

main().catch((err) => {
  console.error("Fatal:", err.message);
  process.exit(1);
});
