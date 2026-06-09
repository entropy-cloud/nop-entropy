import { readFileSync } from "node:fs";
import { execSync } from "node:child_process";
import { execute } from "./process-executor.js";

let _mockRoadmapCount = 0;
let _mockPlanAuditCount = 0;
let _mockClosureCount = 0;
let _mockDeepAuditCount = 0;
let _mockAdversarialCount = 0;

export function resetMockState() {
  _mockRoadmapCount = 0;
  _mockPlanAuditCount = 0;
  _mockClosureCount = 0;
  _mockDeepAuditCount = 0;
  _mockAdversarialCount = 0;
}

const STEP_KEY_MAP = {
  "FIX_TESTS": "fix-tests",
  "FIX_TESTS_RECOVERY": "fix-tests-recovery",
  "ROADMAP_CHECK": "roadmap-check",
  "PLAN_DRAFT": "plan-draft",
  "PLAN_AUDIT": "plan-audit",
  "EXECUTE_PLAN": "execute",
  "PLAN_CLOSURE": "closure-audit",
  "DEEP_AUDIT": "deep-audit",
  "ADVERSARIAL": "adversarial-review",
  "NEEDS_DEEP_AUDIT": "needs-deep-audit",
  "EXECUTE_PENDING_PLAN": "execute-pending",
  "VERIFY_PENDING_PLAN": "verify-pending",
  "AUDIT_PLAN_DRAFT": "audit-plan-draft",
  "AUDIT_PLAN_AUDIT": "audit-plan-audit",
  "AUDIT_EXECUTE": "audit-execute",
  "AUDIT_CLOSURE": "audit-closure",
};

function _normalizeStepName(stepName) {
  if (STEP_KEY_MAP[stepName]) return STEP_KEY_MAP[stepName];
  return stepName.toLowerCase().replace(/_/g, "-");
}

function mockAgentResponse(stepName) {
  const n = _normalizeStepName(stepName);

  if (n === "fix-tests") return "<TEST_RESULT>no_errors</TEST_RESULT>";
  if (n === "fix-tests-recovery") return "<TEST_RESULT>fixed</TEST_RESULT>";

  if (n === "roadmap-check") {
    _mockRoadmapCount++;
    return _mockRoadmapCount <= 1
      ? "<ROADMAP_RESULT>pending</ROADMAP_RESULT>\n<ROADMAP_ITEMS><item priority=\"P1\">mock: unimplemented feature</item></ROADMAP_ITEMS>"
      : "<ROADMAP_RESULT>complete</ROADMAP_RESULT>";
  }

  if (n === "plan-draft" || n === "audit-plan-draft") return "<PLAN_RESULT>created</PLAN_RESULT>";

  if (n === "plan-audit" || n === "audit-plan-audit") {
    _mockPlanAuditCount++;
    return _mockPlanAuditCount <= 1
      ? "<AUDIT_RESULT>issues</AUDIT_RESULT>\n<ISSUES><item severity=\"Major\">mock: Exit Criteria not verifiable</item></ISSUES>"
      : "<AUDIT_RESULT>approved</AUDIT_RESULT>";
  }

  if (n === "execute" || n === "execute-pending" || n === "audit-execute") return "<EXECUTE_RESULT>success</EXECUTE_RESULT>";

  if (n === "verify-pending") return "<VERIFY_RESULT>complete</VERIFY_RESULT>";

  if (n === "closure-audit" || n === "audit-closure") {
    _mockClosureCount++;
    return _mockClosureCount === 1
      ? "<CLOSURE_RESULT>incomplete</CLOSURE_RESULT>\n<REMAINING><item>mock: insufficient test coverage</item></REMAINING>"
      : "<CLOSURE_RESULT>complete</CLOSURE_RESULT>";
  }

  if (n === "needs-deep-audit") return "<DEEP_AUDIT_NEEDED>not_needed</DEEP_AUDIT_NEEDED>";

  if (n === "deep-audit") {
    _mockDeepAuditCount++;
    return _mockDeepAuditCount <= 1
      ? "<AUDIT_RESULT>issues</AUDIT_RESULT>"
      : "<AUDIT_RESULT>clean</AUDIT_RESULT>";
  }

  if (n === "adversarial-review") {
    _mockAdversarialCount++;
    return _mockAdversarialCount <= 1
      ? "<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>"
      : "<ADVERSARIAL_RESULT>clean</ADVERSARIAL_RESULT>";
  }

  return "##MOCK_OK";
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
