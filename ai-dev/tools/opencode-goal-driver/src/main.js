#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { resolveConfig } from "./config.js";
import { createRunner, resetMockState } from "./runner.js";
import { FlowEngine } from "./engine.js";
import { createGoalDriverFlow, loadSubFlow } from "./flow-loader.js";

function parseArgs(argv) {
  const args = { module: "", dryRun: false, testMode: false, listSteps: false };
  let i = 2;
  while (i < argv.length) {
    const a = argv[i];
    if (a === "--dry-run") args.dryRun = true;
    else if (a === "--dir") args.dir = argv[++i];
    else if (a === "--agent") args.agent = argv[++i];
    else if (a === "--model") args.model = argv[++i];
    else if (a === "--max-cycles") args.maxCycles = Number(argv[++i]);
    else if (a === "--max-inner-cycles") args.maxInnerCycles = Number(argv[++i]);
    else if (a === "--max-total-steps") args.maxTotalSteps = Number(argv[++i]);
    else if (a === "--test") args.testMode = true;
    else if (a === "--step") args.entryStep = argv[++i];
    else if (a === "--list-steps") args.listSteps = true;
    else if (!a.startsWith("--")) args.module = a;
    i++;
  }
  return args;
}

function getTopSteps() {
  const __dirname = dirname(fileURLToPath(import.meta.url));
  const flowFile = resolve(__dirname, "..", "flows", "goal-driver.json");
  const flow = JSON.parse(readFileSync(flowFile, "utf8"));
  return Object.keys(flow.steps || {});
}

function printStepList() {
  const steps = getTopSteps();
  console.log("Available top-level steps:");
  for (const s of steps) {
    console.log(`  ${s}`);
  }
  console.log("");
  console.log("Usage: --step <STEP_NAME> to start from a specific step");
}

async function main() {
  const args = parseArgs(process.argv);

  if (args.listSteps) {
    printStepList();
    return;
  }

  const config = resolveConfig(args);
  const runner = await createRunner(config);

  process.on("SIGTERM", async () => {
    process.stderr.write("\n[SIGTERM] cleaning up ...\n");
    await runner.close();
    process.exit(130);
  });
  process.on("SIGINT", async () => {
    process.stderr.write("\n[SIGINT] cleaning up ...\n");
    await runner.close();
    process.exit(130);
  });

  console.log(`Module:   ${config.moduleName}`);
  console.log(`Agent:    ${config.agent}`);
  console.log(`Model:    ${config.model}`);
  console.log(`DryRun:   ${config.dryRun}`);
  console.log(`TestMode: ${config.testMode}`);
  console.log(`Timeout:  60min (auto-extend on output)`);
  console.log(`Log:      ${config.logFile}`);
  console.log("");

  try {
    const flow = createGoalDriverFlow();
    const delegates = {
      config,
      vars: { module: config.moduleName, projectRoot: config.projectRoot, TIMESTAMP: config.timestamp },
      runAgent: runner.runAgent,
      runTool: runner.runTool,
      runParseAgent: runner.runParseAgent,
      logFile: config.logFile,
      loadSubFlow,
    };

    if (args.entryStep) {
      const step = flow.steps[args.entryStep];
      if (!step) {
        console.error(`ERROR: step "${args.entryStep}" not found in flow. Use --list-steps to see available steps.`);
        process.exitCode = 1;
        return;
      }
      console.log(`Step:     ${args.entryStep} (single-step mode)`);
      // Replace goto transitions with done so the flow stops after this step
      for (const [marker, t] of Object.entries(step.transitions || {})) {
        if (t.goto && !t.retry) {
          t.done = "completed";
          delete t.goto;
        }
      }
    }

    resetMockState();
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run(args.entryStep);

    console.log(`\n════════════════════════════════════════`);
    console.log(`  Module:    ${config.moduleName}`);
    console.log(`  Status:    ${result.status}`);
    console.log(`  Steps:     ${result.stepCount}`);
    console.log(`  Elapsed:   ${result.elapsed}`);
    if (result.marker) console.log(`  Last marker: ${result.marker}`);
    const tail = result.history.slice(-5);
    if (tail.length > 0) {
      console.log(`  Last activity:`);
      for (const line of tail) console.log(`    ${line}`);
    }
    console.log(`════════════════════════════════════════`);

    const exitMap = { completed: 0, failed: 1, max_cycles: 2, max_total_steps: 2, max_retries: 2 };
    const exitCode = exitMap[result.status];
    if (exitCode !== undefined) process.exitCode = exitCode;
  } finally {
    await runner.close();
  }
}

main().catch((err) => {
  console.error("Fatal:", err.message);
  process.exit(1);
});
