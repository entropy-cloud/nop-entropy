import { readFileSync } from "node:fs";
import { relative } from "node:path";
import { execute } from "./executor.js";
import { IS_WIN32, killProcessTree, isAlive } from "./platform.mjs";

async function killTree(pid) {
  try {
    if (IS_WIN32) {
      killProcessTree(pid);
      return;
    }
    // Phase 1: SIGTERM - allow graceful shutdown (Effect finalizers, MCP cleanup)
    process.kill(-pid, "SIGTERM");
    // Phase 2: Wait up to 6s for the process group to exit
    const deadline = Date.now() + 6000;
    while (Date.now() < deadline) {
      if (!isAlive(pid)) return; // process exited gracefully
      await new Promise(r => setTimeout(r, 100));
    }
    // Phase 3: SIGKILL - force kill if still alive
    process.stderr.write(`  [WARN] process group ${pid} did not exit after SIGTERM, sending SIGKILL\n`);
    process.kill(-pid, "SIGKILL");
  } catch {}
}

let _mockRoadmapCount = 0;
let _mockPlanAuditCount = 0;
let _mockClosureCount = 0;
let _mockDeepAuditCount = 0;

export function resetMockState() {
  _mockRoadmapCount = 0;
  _mockPlanAuditCount = 0;
  _mockClosureCount = 0;
  _mockDeepAuditCount = 0;
}

const STEP_KEY_MAP = {
  "FIX_BUILD": "fix-build",
  "ROADMAP_CHECK": "roadmap-check",
  "PLAN_DRAFT": "plan-draft",
  "PLAN_AUDIT": "plan-audit",
  "EXECUTE": "execute",
  "CLOSURE_AUDIT": "closure-audit",
  "DEEP_AUDIT": "deep-audit",
  "ADVERSARIAL": "adversarial-review",
  "BUILD_VERIFY": "build-verify",
};

function _normalizeStepName(stepName) {
  if (STEP_KEY_MAP[stepName]) return STEP_KEY_MAP[stepName];
  return stepName.toLowerCase().replace(/_/g, "-");
}

function mockAgentResponse(stepName) {
  const n = _normalizeStepName(stepName);

  if (n === "fix-build") return "<AI_STEP_RESULT>fixed</AI_STEP_RESULT>";

  if (n === "roadmap-check") {
    _mockRoadmapCount++;
    return _mockRoadmapCount <= 1
      ? "<AI_STEP_RESULT>pending</AI_STEP_RESULT>\n<NEXT_ITEM id=\"mock-P1\" layer=\"L1\" priority=\"P1\">mock: unimplemented feature</NEXT_ITEM>\n<ROADMAP_ITEMS><item id=\"mock-P1\" priority=\"P1\">mock: unimplemented feature</item></ROADMAP_ITEMS>"
      : "<AI_STEP_RESULT>complete</AI_STEP_RESULT>";
  }

  if (n === "plan-draft") return "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS>\n  <PLAN_FILE>ai-dev/plans/mock-plan.md</PLAN_FILE>\n</FLOW_VARS>";

  if (n === "plan-audit") {
    _mockPlanAuditCount++;
    return _mockPlanAuditCount <= 1
      ? "<AI_STEP_RESULT>issues</AI_STEP_RESULT>\n<ISSUES><item severity=\"Major\">mock: Exit Criteria not verifiable</item></ISSUES>"
      : "<AI_STEP_RESULT>approved</AI_STEP_RESULT>";
  }

  if (n === "execute") return "<AI_STEP_RESULT>success</AI_STEP_RESULT>";

  if (n === "closure-audit") {
    _mockClosureCount++;
    return _mockClosureCount === 1
      ? "<AI_STEP_RESULT>incomplete</AI_STEP_RESULT>\n<REMAINING><item>mock: insufficient test coverage</item></REMAINING>"
      : "<AI_STEP_RESULT>complete</AI_STEP_RESULT>";
  }

  if (n === "deep-audit") {
    _mockDeepAuditCount++;
    return _mockDeepAuditCount <= 1
      ? "<AI_STEP_RESULT>issues</AI_STEP_RESULT>"
      : "<AI_STEP_RESULT>clean</AI_STEP_RESULT>";
  }

  if (n === "adversarial-review") return "<AI_STEP_RESULT>clean</AI_STEP_RESULT>";

  if (n === "build-verify") return "<AI_STEP_RESULT>pass</AI_STEP_RESULT>";

  return "<AI_STEP_RESULT>ok</AI_STEP_RESULT>";
}

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
  let currentPid = null;

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

    const markedPrompt = `[GOAL_DRIVER] ${prompt}`;
    const args = ["run", "-m", model, "--agent", config.agent, "--dangerously-skip-permissions", markedPrompt];
    if (sessionId) {
      args.push("--session", sessionId);
    }
    const result = await execute(config, `oc-${stepName}`, "opencode", args, {
      cwd: config.projectRoot,
      onSpawn(pid) { currentPid = pid; },
    });
    currentPid = null;

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
    let parts = command.split(" ").filter(Boolean);
    // On Windows, ./mvnw must become mvnw.cmd (Node.js spawn with shell:false
    // cannot execute .cmd/.bat files directly — needs CreateProcess resolution).
    if (IS_WIN32) {
      if (parts[0] === "./mvnw" || parts[0] === "mvnw") {
        parts[0] = "mvnw.cmd";
      }
    }
    const cmd = parts[0];
    const cmdArgs = parts.slice(1);
    const plPath = config.moduleDir ? relative(config.projectRoot, config.moduleDir) : config.moduleName;
    const result = await execute(config, stepName, cmd,
      [...cmdArgs, "-pl", plPath, "-am", "-T", "1C"],
      { cwd: config.projectRoot, onSpawn(pid) { currentPid = pid; }, shell: IS_WIN32 },
    );
    currentPid = null;
    return result;
  }

  return {
    runAgent,
    runTool,
    runParseAgent: runAgent,
    async close() {
      if (currentPid) {
        await killTree(currentPid);
        currentPid = null;
      }
    },
  };
}
