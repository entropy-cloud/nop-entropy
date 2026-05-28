import { readFileSync } from "node:fs";
import { execute } from "./executor.js";

let _mockEvalCount = 0;
let _mockClosureCount = 0;
let _mockAuditCount = 0;

export async function createRunner(config) {
  const realRun = async (stepName, command, system, files = []) => {
    const model = `${config.model}`;

    let prompt = "";
    if (system) prompt += `${system}\n\n`;
    if (files.length > 0) {
      for (const f of files) prompt += `参考文件: ${f}\n`;
    }
    prompt += command;

    process.stderr.write(`\n╔═══════════════════════════════════════════════\n`);
    process.stderr.write(`║ STEP: ${stepName}\n`);
    process.stderr.write(`║ Model: ${model}\n`);
    process.stderr.write(`╠═══════════════════════════════════════════════\n`);
    const preview = prompt.length > 500 ? prompt.slice(0, 500) + "..." : prompt;
    process.stderr.write(preview + "\n");
    process.stderr.write(`╚═══════════════════════════════════════════════\n`);

    const args = ["run", "-m", model, "--agent", config.agent, prompt];
    const result = await execute(config, `oc-${stepName}`, "opencode", args, {
      cwd: config.projectRoot,
      timeout: 36_000_000, // 10 hours
    });

    let text = "";
    if (result.logFile) {
      try { text = readFileSync(result.logFile, "utf8").trim(); } catch { text = ""; }
    }
    return { text, logFile: result.logFile };
  };

  const mockRun = async (stepName, command, system, files = []) => {
    process.stderr.write(`\n╔═══════════════════════════════════════════════\n`);
    process.stderr.write(`║ MOCK STEP: ${stepName}\n`);
    process.stderr.write(`╠═══════════════════════════════════════════════\n`);
    process.stderr.write(`║ Command: ${command.slice(0, 120)}...\n`);
    if (files.length > 0) {
      for (const f of files) {
        process.stderr.write(`║ File: ${f}\n`);
      }
    }
    if (system) {
      process.stderr.write(`║ System prompt:\n`);
      process.stderr.write(system.split("\n").map(l => `║   ${l}`).join("\n") + "\n");
    }
    process.stderr.write(`╚═══════════════════════════════════════════════\n`);

    const mockResponses = {
      "fix-build": "<HEALTH_STATUS>fixed</HEALTH_STATUS>",
      "adversarial-review": "<ADVERSARIAL_RESULT>clean</ADVERSARIAL_RESULT>",
      plan: "<PLAN_RESULT>created</PLAN_RESULT>",
      execute: "<EXECUTE_RESULT>success</EXECUTE_RESULT>",
      "closure-audit": null,
      eval: null,
    };

    let text;
    if (stepName === "deep-audit") {
      _mockAuditCount = (_mockAuditCount || 0) + 1;
      text = _mockAuditCount <= 1
        ? "<AUDIT_RESULT>issues</AUDIT_RESULT>"
        : "<AUDIT_RESULT>clean</AUDIT_RESULT>";
    } else if (stepName === "closure-audit") {
      _mockClosureCount = (_mockClosureCount || 0) + 1;
      text = _mockClosureCount === 1
        ? "<CLOSURE_RESULT>incomplete</CLOSURE_RESULT>\n<REMAINING><item>mock: 测试覆盖不足</item></REMAINING>"
        : "<CLOSURE_RESULT>complete</CLOSURE_RESULT>";
    } else if (stepName === "eval") {
      _mockEvalCount++;
      text = _mockEvalCount <= 1
        ? "<EVAL_RESULT>continue</EVAL_RESULT>"
        : "<EVAL_RESULT>complete</EVAL_RESULT>";
    } else {
      text = mockResponses[stepName] || "##MOCK_OK";
    }
    return { text, logFile: null };
  };

  const runStep = config.dryRun ? mockRun : realRun;

  return {
    runStep,
    async close() {},
  };
}
