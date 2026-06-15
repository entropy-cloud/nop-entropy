import assert from "node:assert/strict";

export function makeMockDelegates(overrides = {}) {
  const responses = overrides.responses || {};
  const hasOverrideRunAgent = !!overrides.runAgent;

  const base = {
    config: { moduleName: "test-mod", projectRoot: "/tmp/test" },
    vars: { module: "test-mod", projectRoot: "/tmp/test" },
    logFile: null,
    callLog: [],

    async runAgent(stepName, prompt, system, sessionId) {
      this.callLog.push({ type: "agent", stepName, prompt, system, sessionId });
      if (stepName in responses) {
        const r = responses[stepName];
        if (typeof r === "function") return r(stepName, prompt);
        if (typeof r === "object" && r.text !== undefined) return r;
        return { text: String(r), ok: true };
      }
      return { text: "##MOCK_OK", ok: true };
    },

    async runTool(stepName, command, opts) {
      this.callLog.push({ type: "tool", stepName, command, opts });
      if (stepName in responses) {
        const r = responses[stepName];
        if (typeof r === "function") return r(stepName, command);
        return { ok: !!r, logFile: null };
      }
      return { ok: true, logFile: null };
    },

    async runParseAgent(stepName, prompt, system) {
      this.callLog.push({ type: "parse", stepName, prompt });
      return { text: "<MOCK_TAG>unknown</MOCK_TAG>", ok: true };
    },

    loadSubFlow(name) {
      const sub = overrides.subFlows?.[name];
      if (sub) return sub;
      throw new Error(`Mock subflow not found: ${name}`);
    },
  };

  if (hasOverrideRunAgent) {
    const result = { ...base, ...overrides };
    result.callLog = base.callLog;
    return result;
  }

  return { ...base, ...overrides };
}

export function simpleFlow(steps, entry = "START") {
  return { name: "test-flow", maxTotalSteps: 50, maxCycleVisits: 20, entry, steps };
}

export function mockSubFlows() {
  const planExec = {
    name: "plan-execution", entry: "EXECUTE", maxTotalSteps: 10,
    steps: {
      EXECUTE: {
        type: "agent", prompt: "execute {{PLAN_FILE}}",
        transitions: { pass: { goto: "CLOSURE_SCRIPT_CHECK" }, fail: { retry: "EXECUTE", maxRetries: 2 } },
        onMaxRetries: { goto: "CLOSURE_SCRIPT_CHECK" },
      },
      CLOSURE_SCRIPT_CHECK: {
        type: "agent", prompt: "script check {{PLAN_FILE}}",
        resultTag: "AI_STEP_RESULT",
        transitions: { pass: { goto: "BUILD_VERIFY" }, fail: { goto: "CLOSURE_AUDIT" } },
      },
      CLOSURE_AUDIT: {
        type: "agent", prompt: "closure audit",
        transitions: { approved: { goto: "BUILD_VERIFY" }, issues: { done: "completed" } },
      },
      BUILD_VERIFY: {
        type: "agent", prompt: "build verify",
        transitions: { pass: { done: "completed" }, fail: { done: "failed" } },
      },
    },
  };
  const deepAudit = {
    name: "deep-audit-loop", entry: "DEEP_AUDIT", maxTotalSteps: 20,
    steps: {
      DEEP_AUDIT: {
        type: "agent", prompt: "deep audit",
        resultTag: "AI_STEP_RESULT",
        transitions: { clean: { goto: "ADVERSARIAL" }, issues: { goto: "ADVERSARIAL" } },
      },
      ADVERSARIAL: {
        type: "agent", prompt: "adversarial review",
        resultTag: "AI_STEP_RESULT",
        transitions: { clean: { done: "completed" }, issues: { goto: "DRAFT_PLANS" } },
      },
      DRAFT_PLANS: {
        type: "agent", prompt: "draft plans",
        resultTag: "AI_STEP_RESULT",
        transitions: { created: { done: "completed" } },
      },
    },
  };
  return { "plan-execution": planExec, "deep-audit-loop": deepAudit };
}
