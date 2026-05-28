#!/usr/bin/env node
import { resolveConfig } from "./config.js";
import { createRunner } from "./runner.js";
import { executeWorkflow } from "./workflow.js";

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
  console.log(`Log:      ${config.logFile}`);
  console.log("");

  try {
    const result = await executeWorkflow(config, runner);
    console.log(`\nResult: ${result.status} (cycle ${result.cycle})`);

    switch (result.status) {
      case "completed":
        process.exit(0);
      case "failed":
        process.exit(1);
      case "max_cycles":
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
