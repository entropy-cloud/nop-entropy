import { readFileSync } from "node:fs";
import { execSync } from "node:child_process";
import { execute } from "./process-executor.js";
import { mockAgentResponse } from "./mock-responses.js";

function extractSessionId(text) {
  if (!text) return null;
  const m = text.match(/"session_id"\s*:\s*"([^"]+)"/);
  if (m) return m[1];
  const m2 = text.match(/"id"\s*:\s*"(ses_[^"]+)"/);
  if (m2) return m2[1];
  const m3 = text.match(/ses_[a-zA-Z0-9]+/);
  if (m3) return m3[0];
  return null;
}

function findLatestSessionId(projectRoot) {
  try {
    const out = execSync("opencode session list -n 1 --format json", {
      cwd: projectRoot,
      encoding: "utf8",
      timeout: 10_000,
    });
    const sessions = JSON.parse(out);
    if (Array.isArray(sessions) && sessions.length > 0 && sessions[0].id) {
      return sessions[0].id;
    }
  } catch {}
  return null;
}

export async function createRunner(config) {
  const realRun = async (stepName, prompt, system, sessionId) => {
    const model = `${config.model}`;

    process.stderr.write(`\n╔═══════════════════════════════════════════════\n`);
    process.stderr.write(`║ STEP: ${stepName}\n`);
    process.stderr.write(`║ Model: ${model}\n`);
    if (sessionId) process.stderr.write(`║ Session: ${sessionId.slice(0, 30)}...\n`);
    process.stderr.write(`╠═══════════════════════════════════════════════\n`);
    const preview = prompt.length > 500 ? prompt.slice(0, 500) + "..." : prompt;
    process.stderr.write(preview + "\n");
    process.stderr.write(`╚═══════════════════════════════════════════════\n`);

    const args = ["run", "-m", model, "--agent", config.agent, "--dangerously-skip-permissions", prompt];
    if (sessionId) {
      args.push("--session", sessionId);
    }
    const result = await execute(config, `oc-${stepName}`, "opencode", args, {
      cwd: config.projectRoot,
      timeout: 36_000_000,
    });

    let text = "";
    if (result.logFile) {
      try { text = readFileSync(result.logFile, "utf8").trim(); } catch { text = ""; }
    }

    let extractedSessionId = extractSessionId(text);
    if (!extractedSessionId && result.ok) {
      extractedSessionId = findLatestSessionId(config.projectRoot);
    }

    return { text, logFile: result.logFile, ok: result.ok, sessionId: extractedSessionId };
  };

  const mockRun = async (stepName, prompt, system) => {
    process.stderr.write(`\n╔═══════════════════════════════════════════════\n`);
    process.stderr.write(`║ MOCK STEP: ${stepName}\n`);
    process.stderr.write(`╚═══════════════════════════════════════════════\n`);

    const text = mockAgentResponse(stepName);
    return { text, logFile: null, ok: true, sessionId: null };
  };

  const runAgent = config.dryRun ? mockRun : realRun;

  async function runTool(stepName, command, opts = {}) {
    if (config.dryRun) {
      console.log(`[MOCK tool] ${command}`);
      return { ok: true, logFile: null };
    }
    return execute(config, stepName, "./mvnw",
      [...command.split(" ").filter(Boolean), "-pl", config.moduleName, "-am", "-T", "1C"],
      { cwd: config.projectRoot, timeout: opts.timeout || 1_800_000 },
    );
  }

  return {
    runAgent,
    runTool,
    runParseAgent: runAgent,
    async close() {},
  };
}
