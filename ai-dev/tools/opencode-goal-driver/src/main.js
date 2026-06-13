#!/usr/bin/env node
import { resolveConfig } from "./config.js";
import { createRunner, resetMockState } from "./runner.js";
import { FlowEngine } from "./engine.js";
import { createGoalDriverFlow, loadSubFlow } from "./flow-loader.js";

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
    else if (a === "--max-total-steps") args.maxTotalSteps = Number(argv[++i]);
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
      vars: { module: config.moduleName, projectRoot: config.projectRoot },
      runAgent: runner.runAgent,
      runTool: runner.runTool,
      runParseAgent: runner.runParseAgent,
      logFile: config.logFile,
      loadSubFlow,
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
