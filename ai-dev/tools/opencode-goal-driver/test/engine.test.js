import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { FlowEngine } from "../src/engine.js";

const __dirname = dirname(fileURLToPath(import.meta.url));

function loadGoalDriverFlow() {
  return JSON.parse(readFileSync(resolve(__dirname, "../src/goal-driver-flow.json"), "utf8"));
}

function makeMockDelegates(overrides = {}) {
  const responses = overrides.responses || {};
  const scriptResults = overrides.scriptResults || {};
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
  };

  if (hasOverrideRunAgent) {
    const overrideRunAgent = overrides.runAgent;
    const result = { ...base, ...overrides };
    result.callLog = base.callLog;
    return result;
  }

  return { ...base, ...overrides };
}

function simpleFlow(steps, entry = "START") {
  return { name: "test-flow", maxTotalSteps: 50, maxCycleVisits: 20, entry, steps };
}

// ═══════════════════════════════════════════
// 1. Engine Core: linear flow
// ═══════════════════════════════════════════

describe("FlowEngine — linear flow", () => {
  it("executes a simple A → B → done chain", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "step start",
        resultTag: "STATUS",
        transitions: { ok: { goto: "B" } },
      },
      B: {
        type: "agent",
        prompt: "step b",
        resultTag: "RESULT",
        transitions: { done: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        START: "<STATUS>ok</STATUS>",
        B: "<RESULT>done</RESULT>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(result.stepCount, 2);
    assert.equal(delegates.callLog.length, 2);
  });

  it("returns failed when agent step has no matching transition", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "TAG",
        transitions: { yes: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: "<TAG>no</TAG>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "no_transition");
    assert.equal(result.stepCount, 1);
  });
});

// ═══════════════════════════════════════════
// 2. Script steps
// ═══════════════════════════════════════════

describe("FlowEngine — script step", () => {
  it("executes script step and uses returned marker", async () => {
    const flow = simpleFlow({
      START: {
        type: "script",
        run: () => "phase_a",
        transitions: {
          phase_a: { goto: "A" },
          phase_b: { done: "completed" },
        },
      },
      A: {
        type: "agent",
        prompt: "do a",
        resultTag: "X",
        transitions: { ok: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { A: "<X>ok</X>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(result.stepCount, 2);
  });
});

// ═══════════════════════════════════════════
// 3. Tool steps
// ═══════════════════════════════════════════

describe("FlowEngine — tool step", () => {
  it("uses 'pass' marker on exit code 0", async () => {
    const flow = simpleFlow({
      BUILD: {
        type: "tool",
        command: "echo ok",
        transitions: { pass: { done: "completed" }, fail: { done: "failed" } },
      },
    }, "BUILD");

    const delegates = makeMockDelegates({
      responses: { BUILD: true },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
  });

  it("uses 'fail' marker on non-zero exit", async () => {
    const flow = simpleFlow({
      BUILD: {
        type: "tool",
        command: "exit 1",
        transitions: { pass: { done: "completed" }, fail: { goto: "FIX" } },
      },
      FIX: {
        type: "agent",
        prompt: "fix",
        resultTag: "STATUS",
        transitions: { fixed: { done: "completed" } },
      },
    }, "BUILD");

    const delegates = makeMockDelegates({
      responses: { BUILD: false, FIX: "<STATUS>fixed</STATUS>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(result.stepCount, 2);
  });
});

// ═══════════════════════════════════════════
// 4. Goto with append
// ═══════════════════════════════════════════

describe("FlowEngine — goto with append", () => {
  it("appends previous output to target step prompt", async () => {
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check items",
        resultTag: "CHECK_RESULT",
        transitions: {
          pending: { goto: "WORK", append: true },
          done: { done: "completed" },
        },
      },
      WORK: {
        type: "agent",
        prompt: "do work",
        resultTag: "WORK_RESULT",
        transitions: { ok: { done: "completed" } },
      },
    }, "CHECK");

    let workPrompt = "";
    const delegates = makeMockDelegates({
      responses: {
        CHECK: "<CHECK_RESULT>pending</CHECK_RESULT>\n<ITEMS><item>task1</item></ITEMS>",
        WORK: (stepName, prompt) => { workPrompt = prompt; return { text: "<WORK_RESULT>ok</WORK_RESULT>", ok: true }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(workPrompt.includes("do work"));
    assert.ok(workPrompt.includes("<ITEMS>"));
    assert.ok(workPrompt.includes("task1"));
  });

  it("appends using template with extract", async () => {
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        transitions: {
          pending: {
            goto: "WORK",
            append: { extract: "ITEMS", template: "Remaining:\n${output}" },
          },
          done: { done: "completed" },
        },
      },
      WORK: {
        type: "agent",
        prompt: "work",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "CHECK");

    let workPrompt = "";
    const delegates = makeMockDelegates({
      responses: {
        CHECK: { text: "<R>pending</R>\n<ITEMS><item>a</item></ITEMS>\n<EXTRA>noise</EXTRA>", ok: true },
        WORK: (sn, prompt) => { workPrompt = prompt; return { text: "<R>ok</R>", ok: true }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(workPrompt.includes("<ITEMS><item>a</item></ITEMS>"));
    assert.ok(!workPrompt.includes("<EXTRA>"));
  });
});

// ═══════════════════════════════════════════
// 5. Retry mechanism
// ═══════════════════════════════════════════

describe("FlowEngine — retry", () => {
  it("retries target step and accumulates append context", async () => {
    let draftCount = 0;
    let draftPrompts = [];

    const flow = simpleFlow({
      DRAFT: {
        type: "agent",
        prompt: "write plan",
        resultTag: "PLAN_RESULT",
        transitions: {
          created: { goto: "AUDIT" },
          none: { done: "completed" },
        },
      },
      AUDIT: {
        type: "agent",
        prompt: "audit plan",
        resultTag: "AUDIT_RESULT",
        transitions: {
          approved: { done: "completed" },
          issues: {
            retry: "DRAFT",
            maxRetries: 3,
            append: { template: "Feedback:\n${output}" },
          },
        },
      },
    }, "DRAFT");

    const delegates = makeMockDelegates({
      responses: {
        DRAFT: (sn, prompt) => {
          draftCount++;
          draftPrompts.push(prompt);
          return { text: "<PLAN_RESULT>created</PLAN_RESULT>", ok: true };
        },
        AUDIT: (sn) => {
          if (draftCount < 3) {
            return { text: `<AUDIT_RESULT>issues</AUDIT_RESULT>\n<ISSUES><item>Issue ${draftCount}</item></ISSUES>`, ok: true };
          }
          return { text: "<AUDIT_RESULT>approved</AUDIT_RESULT>", ok: true };
        },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(draftCount, 3);
    assert.ok(!draftPrompts[0].includes("Feedback"));
    assert.ok(draftPrompts[1].includes("Feedback"));
    assert.ok(draftPrompts[1].includes("Issue 1"));
    assert.ok(draftPrompts[2].includes("Feedback"));
    assert.ok(draftPrompts[2].includes("Issue 2"));
    assert.ok(draftPrompts[2].includes("───────────────"));
  });

  it("fires onMaxRetries when retry count exceeded", async () => {
    let draftCount = 0;

    const flow = simpleFlow({
      DRAFT: {
        type: "agent",
        prompt: "draft",
        resultTag: "R",
        transitions: { ok: { goto: "AUDIT" } },
      },
      AUDIT: {
        type: "agent",
        prompt: "audit",
        resultTag: "R",
        maxRetries: 2,
        transitions: {
          approved: { done: "completed" },
          issues: {
            retry: "DRAFT",
            append: true,
          },
        },
        onMaxRetries: { done: "degraded" },
      },
    }, "DRAFT");

    const delegates = makeMockDelegates({
      responses: {
        DRAFT: () => { draftCount++; return { text: "<R>ok</R>", ok: true }; },
        AUDIT: { text: "<R>issues</R>", ok: true },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "degraded");
    assert.ok(draftCount >= 3);
  });
});

// ═══════════════════════════════════════════
// 6. Error handling: subprocess killed
// ═══════════════════════════════════════════

describe("FlowEngine — onError (subprocess killed)", () => {
  it("uses onError when agent returns ok=false", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "do",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
        onError: { goto: "FALLBACK" },
      },
      FALLBACK: {
        type: "agent",
        prompt: "fallback",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });

    let callCount = 0;
    const delegates = makeMockDelegates({
      responses: {
        START: () => { callCount++; return { text: "", ok: false }; },
        FALLBACK: "<R>ok</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(result.stepCount, 2);
  });

  it("defaults to failed when no onError defined", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "fail",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: { text: "", ok: false } },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed");
  });

  it("onError with retry", async () => {
    let attempt = 0;

    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "flaky",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
        onError: { retry: "START", maxRetries: 3, append: { template: "retry after kill" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        START: () => {
          attempt++;
          if (attempt < 3) return { text: "", ok: false };
          return { text: "<R>ok</R>", ok: true };
        },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(attempt, 3);
  });
});

// ═══════════════════════════════════════════
// 7. Safety nets
// ═══════════════════════════════════════════

describe("FlowEngine — safety nets", () => {
  it("returns max_cycles when step visited too many times", async () => {
    const flow = simpleFlow({
      A: {
        type: "agent",
        prompt: "loop",
        resultTag: "R",
        transitions: { again: { goto: "A" } },
      },
    }, "A");
    flow.maxCycleVisits = 5;

    const delegates = makeMockDelegates({
      responses: { A: "<R>again</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "max_cycles");
    assert.ok(result.stepCount >= 5);
  });

  it("returns max_total_steps when total steps exceeded", async () => {
    const flow = simpleFlow({
      A: {
        type: "agent",
        prompt: "a",
        resultTag: "R",
        transitions: { next: { goto: "B" } },
      },
      B: {
        type: "agent",
        prompt: "b",
        resultTag: "R",
        transitions: { next: { goto: "A" } },
      },
    }, "A");
    flow.maxTotalSteps = 6;

    const delegates = makeMockDelegates({
      responses: { A: "<R>next</R>", B: "<R>next</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "max_total_steps");
    assert.equal(result.stepCount, 6);
  });

  it("returns unknown_step for invalid goto", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: { ok: { goto: "NONEXISTENT" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: "<R>ok</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "unknown_step");
  });
});

// ═══════════════════════════════════════════
// 8. Template variable substitution
// ═══════════════════════════════════════════

describe("FlowEngine — template vars", () => {
  it("substitutes {module} and {projectRoot} in prompts and commands", async () => {
    let agentPrompt = "";
    let toolCommand = "";

    const flow = simpleFlow({
      AGENT_STEP: {
        type: "agent",
        prompt: "build module {module} in {projectRoot}",
        resultTag: "R",
        transitions: { ok: { goto: "TOOL_STEP" } },
      },
      TOOL_STEP: {
        type: "tool",
        command: "./mvnw -pl {module} -C",
        transitions: { pass: { done: "completed" } },
      },
    }, "AGENT_STEP");

    const delegates = makeMockDelegates({
      responses: {
        AGENT_STEP: (sn, prompt) => { agentPrompt = prompt; return { text: "<R>ok</R>", ok: true }; },
        TOOL_STEP: (sn, command) => { toolCommand = command; return { ok: true, logFile: null }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    await engine.run();

    assert.ok(agentPrompt.includes("build module test-mod in /tmp/test"));
    assert.ok(!agentPrompt.includes("{module}"));
    assert.ok(toolCommand.includes("./mvnw -pl test-mod -C"));
    assert.ok(!toolCommand.includes("{module}"));
  });
});

// ═══════════════════════════════════════════
// 9. Marker extraction fallback
// ═══════════════════════════════════════════

describe("FlowEngine — marker extraction", () => {
  it("extracts last occurrence of XML tag", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: { a: { done: "completed" }, b: { done: "failed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: "<R>a</R> some text <R>b</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed");
  });

  it("calls runParseAgent when marker not found, then uses onUnknown", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
        onUnknown: { done: "marker_not_found" },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: "no tags here" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "marker_not_found");
    assert.ok(delegates.callLog.some(c => c.type === "parse"));
  });
});

// ═══════════════════════════════════════════
// 10. Context and history
// ═══════════════════════════════════════════

describe("FlowEngine — context tracking", () => {
  it("stores step outputs in context map", async () => {
    const flow = simpleFlow({
      A: {
        type: "agent",
        prompt: "a",
        resultTag: "R",
        transitions: { ok: { goto: "B" } },
      },
      B: {
        type: "agent",
        prompt: "b",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "A");

    const delegates = makeMockDelegates({
      responses: {
        A: { text: "<R>ok</R> output A", ok: true },
        B: { text: "<R>ok</R> output B", ok: true },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    await engine.run();

    assert.equal(engine.context.get("A").text, "<R>ok</R> output A");
    assert.equal(engine.context.get("B").text, "<R>ok</R> output B");
    assert.ok(engine.logEntries.length >= 4);
  });
});

// ═══════════════════════════════════════════
// 11. Full goal driver flow (integration)
// ═══════════════════════════════════════════

describe("FlowEngine — goal driver integration", () => {
  it("completes full cycle: fix-tests → pending plans → roadmap → plan → execute → closure → needs-deep-audit → loop", async () => {
    const flow = loadGoalDriverFlow();
    flow.maxTotalSteps = 120;

    let roadmapCount = 0;
    let planAuditCount = 0;
    let closureCount = 0;

    const delegates = makeMockDelegates({
      responses: {
        FIX_TESTS: "<TEST_RESULT>no_errors</TEST_RESULT>",

        ROADMAP_CHECK: () => {
          roadmapCount++;
          return roadmapCount <= 1
            ? { text: "<ROADMAP_RESULT>pending</ROADMAP_RESULT>\n<ROADMAP_ITEMS><item>P1</item></ROADMAP_ITEMS>", ok: true }
            : { text: "<ROADMAP_RESULT>complete</ROADMAP_RESULT>", ok: true };
        },

        PLAN_DRAFT: "<PLAN_RESULT>created</PLAN_RESULT>",

        PLAN_AUDIT: () => {
          planAuditCount++;
          return planAuditCount <= 1
            ? { text: "<AUDIT_RESULT>issues</AUDIT_RESULT>\n<ISSUES><item>Major: fix X</item></ISSUES>", ok: true }
            : { text: "<AUDIT_RESULT>approved</AUDIT_RESULT>", ok: true };
        },

        EXECUTE_PLAN: "<EXECUTE_RESULT>success</EXECUTE_RESULT>",

        PLAN_CLOSURE: () => {
          closureCount++;
          return closureCount === 1
            ? { text: "<CLOSURE_RESULT>incomplete</CLOSURE_RESULT>\n<REMAINING><item>todo</item></REMAINING>", ok: true }
            : { text: "<CLOSURE_RESULT>complete</CLOSURE_RESULT>", ok: true };
        },

        NEEDS_DEEP_AUDIT: "<DEEP_AUDIT_NEEDED>not_needed</DEEP_AUDIT_NEEDED>",
      },
    });

    delegates.config = { moduleName: "test-mod", projectRoot: "/tmp/test" };
    delegates.vars = { module: "test-mod", projectRoot: "/tmp/test" };
    delegates.scripts = {
      checkPendingPlans: () => "no_plans",
      gitCommit: () => "committed",
    };

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(result.stepCount > 8, `expected >8 steps, got ${result.stepCount}`);

    assert.ok(delegates.callLog.some(c => c.stepName === "PLAN_AUDIT"), "PLAN_AUDIT should be called");
    assert.ok(delegates.callLog.some(c => c.stepName === "PLAN_CLOSURE"), "PLAN_CLOSURE should be called");
    assert.ok(delegates.callLog.some(c => c.stepName === "NEEDS_DEEP_AUDIT"), "NEEDS_DEEP_AUDIT should be called");
  });

  it("executes pending plans before roadmap check", async () => {
    const flow = loadGoalDriverFlow();

    let checkPendingCallCount = 0;

    const delegates = makeMockDelegates({
      responses: {
        FIX_TESTS: "<TEST_RESULT>no_errors</TEST_RESULT>",
        EXECUTE_PENDING_PLAN: "<PLAN_EXEC_RESULT>success</PLAN_EXEC_RESULT>",
        VERIFY_PENDING_PLAN: "<VERIFY_RESULT>complete</VERIFY_RESULT>",
        ROADMAP_CHECK: "<ROADMAP_RESULT>complete</ROADMAP_RESULT>",
        NEEDS_DEEP_AUDIT: "<DEEP_AUDIT_NEEDED>not_needed</DEEP_AUDIT_NEEDED>",
      },
    });

    delegates.config = { moduleName: "test-mod", projectRoot: "/tmp/test" };
    delegates.vars = { module: "test-mod", projectRoot: "/tmp/test" };
    delegates.scripts = {
      checkPendingPlans: () => {
        checkPendingCallCount++;
        if (checkPendingCallCount <= 1) {
          return { marker: "has_plans", vars: { activePlanCount: "1" } };
        }
        return "no_plans";
      },
      gitCommit: () => "committed",
    };

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(delegates.callLog.some(c => c.stepName === "EXECUTE_PENDING_PLAN"),
      `Expected EXECUTE_PENDING_PLAN: ${delegates.callLog.map(c => c.stepName).join(", ")}`);
    assert.ok(delegates.callLog.some(c => c.stepName === "ROADMAP_CHECK"));
  });

  it("handles adversarial issues → audit plan cycle → loop back to FIX_TESTS", async () => {
    const flow = loadGoalDriverFlow();

    let adversarialCount = 0;
    let fixTestsCount = 0;

    const delegates = {
      config: { moduleName: "test-mod", projectRoot: "/tmp/test" },
      vars: { module: "test-mod", projectRoot: "/tmp/test" },
      scripts: {
        checkPendingPlans: () => "no_plans",
        gitCommit: () => "committed",
      },
      callLog: [],
      async runAgent(stepName, prompt, system, sessionId) {
        delegates.callLog.push({ type: "agent", stepName, prompt, system, sessionId });
        if (stepName === "FIX_TESTS" || stepName.startsWith("FIX_TESTS:")) {
          fixTestsCount++;
          return { text: "<TEST_RESULT>no_errors</TEST_RESULT>", ok: true };
        }
        if (stepName === "ROADMAP_CHECK") return { text: "<ROADMAP_RESULT>complete</ROADMAP_RESULT>", ok: true };
        if (stepName === "NEEDS_DEEP_AUDIT") return { text: "<DEEP_AUDIT_NEEDED>needed</DEEP_AUDIT_NEEDED>", ok: true };
        if (stepName === "DEEP_AUDIT") return { text: "<AUDIT_RESULT>clean</AUDIT_RESULT>", ok: true };
        if (stepName === "ADVERSARIAL") {
          adversarialCount++;
          return adversarialCount <= 1
            ? { text: "<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>", ok: true }
            : { text: "<ADVERSARIAL_RESULT>clean</ADVERSARIAL_RESULT>", ok: true };
        }
        if (stepName === "AUDIT_PLAN_DRAFT") return { text: "<PLAN_RESULT>created</PLAN_RESULT>", ok: true };
        if (stepName === "AUDIT_PLAN_AUDIT") return { text: "<AUDIT_RESULT>approved</AUDIT_RESULT>", ok: true };
        if (stepName === "AUDIT_EXECUTE") return { text: "<EXECUTE_RESULT>success</EXECUTE_RESULT>", ok: true };
        if (stepName === "AUDIT_CLOSURE") return { text: "<CLOSURE_RESULT>complete</CLOSURE_RESULT>", ok: true };
        return { text: "##MOCK_OK", ok: true };
      },
      async runTool(stepName, command, opts) {
        delegates.callLog.push({ type: "tool", stepName, command, opts });
        return { ok: true, logFile: null };
      },
      async runParseAgent(stepName, prompt) {
        delegates.callLog.push({ type: "parse", stepName, prompt });
        return { text: "<X>ok</X>", ok: true };
      },
    };

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(delegates.callLog.some(c => c.stepName === "AUDIT_PLAN_DRAFT"));
    assert.ok(delegates.callLog.some(c => c.stepName === "AUDIT_EXECUTE"));
    assert.ok(fixTestsCount >= 2, `fix-tests should be called >= 2 times (loop), got ${fixTestsCount}`);
  });
});

// ═══════════════════════════════════════════
// 12. Flow definition validation
// ═══════════════════════════════════════════

describe("Flow definition — structural validation", () => {
  it("all goto/retry targets reference existing steps", async () => {
    const flow = loadGoalDriverFlow();
    const stepNames = new Set(Object.keys(flow.steps));

    for (const [name, step] of Object.entries(flow.steps)) {
      for (const [marker, transition] of Object.entries(step.transitions || {})) {
        if (transition.goto) {
          assert.ok(stepNames.has(transition.goto),
            `${name} → ${marker}: goto "${transition.goto}" does not exist`);
        }
        if (transition.retry) {
          assert.ok(stepNames.has(transition.retry),
            `${name} → ${marker}: retry "${transition.retry}" does not exist`);
        }
      }
      if (step.onError) {
        if (step.onError.goto) {
          assert.ok(stepNames.has(step.onError.goto),
            `${name}.onError: goto "${step.onError.goto}" does not exist`);
        }
        if (step.onError.retry) {
          assert.ok(stepNames.has(step.onError.retry),
            `${name}.onError: retry "${step.onError.retry}" does not exist`);
        }
      }
    }
  });

  it("entry step exists", async () => {
    const flow = loadGoalDriverFlow();
    assert.ok(flow.steps[flow.entry], `entry "${flow.entry}" not found`);
  });

  it("every step has type and transitions", async () => {
    const flow = loadGoalDriverFlow();
    for (const [name, step] of Object.entries(flow.steps)) {
      assert.ok(step.type, `${name} has no type`);
      if (step.type === "agent") {
        assert.ok(step.resultTag, `${name} (agent) has no resultTag`);
      }
      assert.ok(step.transitions || step.type === "script",
        `${name} has no transitions`);
    }
  });

  it("at least one step has a done transition", async () => {
    const flow = loadGoalDriverFlow();
    const hasDone = Object.values(flow.steps).some(step =>
      Object.values(step.transitions || {}).some(t => t.done) ||
      (step.onError && step.onError.done),
    );
    assert.ok(hasDone, "no step has a done transition");
  });
});

// ═══════════════════════════════════════════
// 13. Plan audit retry loop
// ═══════════════════════════════════════════

describe("FlowEngine — plan audit retry loop", () => {
  it("retries plan draft when audit finds issues, then proceeds on approval", async () => {
    const flow = simpleFlow({
      PLAN_DRAFT: {
        type: "agent",
        prompt: "draft plan",
        resultTag: "PLAN_RESULT",
        transitions: {
          created: { goto: "PLAN_AUDIT" },
          none: { done: "completed" },
        },
      },
      PLAN_AUDIT: {
        type: "agent",
        prompt: "audit plan",
        resultTag: "AUDIT_RESULT",
        transitions: {
          approved: { goto: "EXECUTE" },
          issues: {
            retry: "PLAN_DRAFT",
            maxRetries: 3,
            append: { template: "Feedback:\n${output}" },
          },
        },
        onMaxRetries: { goto: "EXECUTE", append: { template: "⚠️ degraded:\n${output}" } },
      },
      EXECUTE: {
        type: "agent",
        prompt: "execute plan",
        resultTag: "EXECUTE_RESULT",
        transitions: { success: { done: "completed" } },
      },
    }, "PLAN_DRAFT");

    let draftCount = 0;
    const delegates = makeMockDelegates({
      responses: {
        PLAN_DRAFT: () => {
          draftCount++;
          return { text: "<PLAN_RESULT>created</PLAN_RESULT>", ok: true };
        },
        PLAN_AUDIT: () => {
          if (draftCount < 2) {
            return { text: "<AUDIT_RESULT>issues</AUDIT_RESULT>\n<ISSUES><item severity=\"Major\">fix exit criteria</item></ISSUES>", ok: true };
          }
          return { text: "<AUDIT_RESULT>approved</AUDIT_RESULT>", ok: true };
        },
        EXECUTE: "<EXECUTE_RESULT>success</EXECUTE_RESULT>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(draftCount, 2);

    const draftCalls = delegates.callLog.filter(c => c.stepName === "PLAN_DRAFT");
    assert.equal(draftCalls.length, 2);
    assert.ok(!draftCalls[0].prompt.includes("Feedback"));
    assert.ok(draftCalls[1].prompt.includes("Feedback"));
    assert.ok(draftCalls[1].prompt.includes("fix exit criteria"));
  });

  it("degrades gracefully when plan audit retries exhausted", async () => {
    const flow = simpleFlow({
      PLAN_DRAFT: {
        type: "agent",
        prompt: "draft",
        resultTag: "R",
        transitions: { ok: { goto: "PLAN_AUDIT" } },
      },
      PLAN_AUDIT: {
        type: "agent",
        prompt: "audit",
        resultTag: "R",
        maxRetries: 2,
        transitions: {
          approved: { goto: "EXECUTE" },
          issues: { retry: "PLAN_DRAFT", append: true },
        },
        onMaxRetries: { goto: "EXECUTE", append: { template: "⚠️ plan audit failed" } },
      },
      EXECUTE: {
        type: "agent",
        prompt: "exec",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "PLAN_DRAFT");

    let execPrompt = "";
    const delegates = makeMockDelegates({
      responses: {
        PLAN_DRAFT: "<R>ok</R>",
        PLAN_AUDIT: "<R>issues</R>",
        EXECUTE: (sn, prompt) => { execPrompt = prompt; return { text: "<R>ok</R>", ok: true }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(execPrompt.includes("⚠️ plan audit failed"));
  });
});

// ═══════════════════════════════════════════
// 14. Chinese marker alias resolution
// ═══════════════════════════════════════════

describe("FlowEngine — Chinese marker aliases", () => {
  it("resolves Chinese marker 已创建 to created", async () => {
    const flow = simpleFlow({
      PLAN_DRAFT: {
        type: "agent",
        prompt: "draft",
        resultTag: "PLAN_RESULT",
        transitions: { created: { goto: "NEXT" }, none: { done: "failed" } },
      },
      NEXT: {
        type: "agent",
        prompt: "next",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "PLAN_DRAFT");
    flow.markerAliases = { "已创建": "created" };

    const delegates = makeMockDelegates({
      responses: {
        PLAN_DRAFT: "<PLAN_RESULT>已创建</PLAN_RESULT>",
        NEXT: "<R>ok</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
  });

  it("resolves multiple Chinese aliases", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: {
          fixed: { goto: "DONE" },
          failed: { done: "failed" },
        },
      },
      DONE: {
        type: "agent",
        prompt: "done",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });
    flow.markerAliases = { "修复": "fixed", "失败": "failed" };

    const delegates = makeMockDelegates({
      responses: {
        START: "<R>修复</R>",
        DONE: "<R>ok</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
  });

  it("falls back to no_transition when alias not found", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        onUnknownMaxRetries: 0,
        transitions: { yes: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: "<R>不存在的值</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "no_transition");
  });
});

// ═══════════════════════════════════════════
// 15. Marker correction retry with session continue
// ═══════════════════════════════════════════

describe("FlowEngine — marker correction retry", () => {
  it("retries with correction prompt when marker not in transitions", async () => {
    let callCount = 0;
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        onUnknownMaxRetries: 2,
        transitions: { yes: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      async runAgent(stepName, prompt, system, sessionId) {
        delegates.callLog.push({ type: "agent", stepName, prompt, system, sessionId });
        callCount++;
        if (stepName.includes("correct")) {
          return { text: "<R>yes</R>", ok: true };
        }
        return { text: "<R>no</R>", ok: true, sessionId: "ses_test123" };
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(callCount >= 2);
    const correctionCalls = delegates.callLog.filter(c => c.stepName.includes("correct"));
    assert.ok(correctionCalls.length >= 1, "should have at least one correction call");
  });

  it("passes sessionId to correction retry", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        onUnknownMaxRetries: 2,
        transitions: { yes: { done: "completed" } },
      },
    });

    let capturedSessionId = null;
    const delegates = makeMockDelegates({
      responses: {
        START: () => ({ text: "<R>no</R>", ok: true, sessionId: "ses_test456" }),
      },
      async runAgent(stepName, prompt, system, sessionId) {
        delegates.callLog.push({ type: "agent", stepName, prompt, system, sessionId });
        if (stepName.includes("correct")) capturedSessionId = sessionId;
        if (stepName.includes("correct")) return { text: "<R>yes</R>", ok: true };
        return { text: "<R>no</R>", ok: true, sessionId: "ses_test456" };
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(capturedSessionId, "ses_test456");
  });

  it("gives up after onUnknownMaxRetries attempts", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        onUnknownMaxRetries: 1,
        transitions: { yes: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        START: "<R>garbage</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "no_transition");
  });
});

// ═══════════════════════════════════════════
// 16. Comprehensive fault tolerance
// ═══════════════════════════════════════════

describe("FlowEngine — fault tolerance: error recovery chains", () => {
  it("recovers from error via onError goto → continue normally", async () => {
    let attempt = 0;
    const flow = simpleFlow({
      FLAKY: {
        type: "agent",
        prompt: "try",
        resultTag: "R",
        transitions: { ok: { goto: "NEXT" } },
        onError: { goto: "RECOVER" },
      },
      RECOVER: {
        type: "script",
        run: () => "recovered",
        transitions: { recovered: { goto: "NEXT" } },
      },
      NEXT: {
        type: "agent",
        prompt: "final",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "FLAKY");

    const delegates = makeMockDelegates({
      responses: {
        FLAKY: () => { attempt++; return attempt === 1 ? { text: "", ok: false } : { text: "<R>ok</R>", ok: true }; },
        NEXT: "<R>ok</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(engine.context.has("RECOVER"));
    assert.equal(engine.context.get("RECOVER").marker, "recovered");
    assert.ok(delegates.callLog.some(c => c.stepName === "NEXT"));
  });

  it("onError with done terminates immediately", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
        onError: { done: "subprocess_died" },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: { text: "crash log", ok: false } },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "subprocess_died");
    assert.equal(result.stepCount, 1);
  });

  it("onError retry exhausts then uses onMaxRetries fallback", async () => {
    let attempt = 0;
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "always-fail",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
        onError: { retry: "START", maxRetries: 2, append: { template: "retry context" } },
        onMaxRetries: { goto: "FALLBACK" },
      },
      FALLBACK: {
        type: "agent",
        prompt: "fallback",
        resultTag: "R",
        transitions: { ok: { done: "degraded" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        START: () => { attempt++; return { text: "", ok: false }; },
        FALLBACK: "<R>ok</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "degraded");
    assert.ok(attempt >= 3, `expected >=3 attempts, got ${attempt}`);
  });

  it("onError append carries error context to recovery step", async () => {
    let recoveryPrompt = "";
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
        onError: { goto: "RECOVER", append: { template: "Error log:\n${output}" } },
      },
      RECOVER: {
        type: "agent",
        prompt: "recover",
        resultTag: "R",
        transitions: { ok: { done: "recovered" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        START: { text: "OOM: heap space", ok: false },
        RECOVER: (sn, prompt) => { recoveryPrompt = prompt; return { text: "<R>ok</R>", ok: true }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "recovered");
    assert.ok(recoveryPrompt.includes("OOM: heap space"));
  });
});

describe("FlowEngine — fault tolerance: script step errors", () => {
  it("script throwing exception triggers onError", async () => {
    const flow = simpleFlow({
      START: {
        type: "script",
        run: () => { throw new Error("disk full"); },
        transitions: { ok: { done: "completed" } },
        onError: { done: "script_error" },
      },
    });

    const engine = new FlowEngine(flow, makeMockDelegates());
    const result = await engine.run();

    assert.equal(result.status, "script_error");
  });

  it("script returning object with vars injects into context", async () => {
    let receivedCount = "";
    const flow = simpleFlow({
      START: {
        type: "script",
        run: () => ({ marker: "has_plans", vars: { planCount: "3", planNames: "A,B" } }),
        transitions: { has_plans: { goto: "NEXT" }, no_plans: { done: "completed" } },
      },
      NEXT: {
        type: "agent",
        prompt: "process {planCount} plans: {planNames}",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        NEXT: (sn, prompt) => { receivedCount = prompt; return { text: "<R>ok</R>", ok: true }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(receivedCount.includes("process 3 plans: A,B"));
  });

  it("script returning plain string marker works without vars", async () => {
    const flow = simpleFlow({
      START: {
        type: "script",
        run: () => "proceed",
        transitions: { proceed: { done: "completed" } },
      },
    });

    const engine = new FlowEngine(flow, makeMockDelegates());
    const result = await engine.run();

    assert.equal(result.status, "completed");
  });
});

describe("FlowEngine — fault tolerance: tool step edge cases", () => {
  it("tool fail is a normal marker, not an error", async () => {
    const flow = simpleFlow({
      BUILD: {
        type: "tool",
        command: "make",
        transitions: {
          pass: { done: "completed" },
          fail: { goto: "FIX" },
        },
      },
      FIX: {
        type: "agent",
        prompt: "fix build",
        resultTag: "R",
        transitions: { fixed: { done: "fixed" } },
      },
    }, "BUILD");

    const delegates = makeMockDelegates({
      responses: { BUILD: false, FIX: "<R>fixed</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "fixed");
    assert.equal(result.stepCount, 2);
  });

  it("tool fail with retry loops back correctly", async () => {
    let buildAttempts = 0;
    const flow = simpleFlow({
      BUILD: {
        type: "tool",
        command: "make",
        transitions: {
          pass: { done: "completed" },
          fail: { retry: "BUILD", maxRetries: 3 },
        },
        onMaxRetries: { done: "build_failed" },
      },
    }, "BUILD");

    const delegates = makeMockDelegates({
      responses: {
        BUILD: () => { buildAttempts++; return { ok: buildAttempts >= 3, logFile: null }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(buildAttempts, 3);
  });

  it("tool step timeout is passed to runTool", async () => {
    let capturedOpts = {};
    const flow = simpleFlow({
      BUILD: {
        type: "tool",
        command: "make",
        timeout: 300_000,
        transitions: { pass: { done: "completed" }, fail: { done: "failed" } },
      },
    }, "BUILD");

    const delegates = makeMockDelegates({
      responses: { BUILD: true },
      async runTool(stepName, command, opts) {
        delegates.callLog.push({ type: "tool", stepName, command, opts });
        capturedOpts = opts;
        return { ok: true, logFile: null };
      },
    });

    const engine = new FlowEngine(flow, delegates);
    await engine.run();

    assert.equal(capturedOpts.timeout, 300_000);
  });
});

describe("FlowEngine — fault tolerance: mixed error recovery", () => {
  it("agent fails → onError goto → tool fails → retry → agent succeeds", async () => {
    let agentAttempt = 0;
    let toolAttempt = 0;

    const flow = simpleFlow({
      AGENT: {
        type: "agent",
        prompt: "start",
        resultTag: "R",
        transitions: { ok: { goto: "TOOL" } },
        onError: { goto: "FALLBACK_AGENT" },
      },
      FALLBACK_AGENT: {
        type: "agent",
        prompt: "fallback",
        resultTag: "R",
        transitions: { ok: { goto: "TOOL" } },
      },
      TOOL: {
        type: "tool",
        command: "build",
        transitions: {
          pass: { done: "completed" },
          fail: { retry: "TOOL", maxRetries: 3 },
        },
        onMaxRetries: { done: "tool_failed" },
      },
    }, "AGENT");

    const delegates = makeMockDelegates({
      responses: {
        AGENT: () => { agentAttempt++; return agentAttempt === 1 ? { text: "", ok: false } : { text: "<R>ok</R>", ok: true }; },
        FALLBACK_AGENT: "<R>ok</R>",
        TOOL: () => { toolAttempt++; return { ok: toolAttempt >= 2, logFile: null }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(agentAttempt >= 1);
    assert.ok(toolAttempt >= 2);
  });

  it("cascading retries with different maxRetries", async () => {
    let innerCount = 0;

    const flow = simpleFlow({
      OUTER: {
        type: "agent",
        prompt: "outer",
        resultTag: "R",
        transitions: {
          proceed: { goto: "INNER" },
          retry_outer: { retry: "OUTER", maxRetries: 2 },
        },
      },
      INNER: {
        type: "agent",
        prompt: "inner",
        resultTag: "R",
        transitions: {
          ok: { done: "completed" },
          fail: { retry: "INNER", maxRetries: 1 },
        },
        onMaxRetries: { goto: "OUTER", append: true },
      },
    }, "OUTER");

    const delegates = makeMockDelegates({
      responses: {
        OUTER: () => ({ text: "<R>proceed</R>", ok: true }),
        INNER: () => {
          innerCount++;
          if (innerCount <= 2) return { text: "<R>fail</R>", ok: true };
          return { text: "<R>ok</R>", ok: true };
        },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(innerCount >= 3);
  });
});

describe("FlowEngine — fault tolerance: context preservation under errors", () => {
  it("context survives across error recovery", async () => {
    const flow = simpleFlow({
      A: {
        type: "agent",
        prompt: "step a",
        resultTag: "R",
        transitions: { ok: { goto: "B" } },
      },
      B: {
        type: "agent",
        prompt: "step b, prev={steps.A.marker}",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
        onError: { retry: "B", maxRetries: 2 },
      },
    }, "A");

    let bAttempt = 0;
    const delegates = makeMockDelegates({
      responses: {
        A: { text: "<R>ok</R>\n<DETAIL>plan alpha</DETAIL>", ok: true },
        B: (sn, prompt) => {
          bAttempt++;
          if (bAttempt === 1) return { text: "", ok: false };
          return { text: "<R>ok</R>", ok: true };
        },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(engine.context.has("A"));
    assert.equal(engine.context.get("A").marker, "ok");
  });

  it("multiple step results accessible via {steps.X.marker}", async () => {
    let cPrompt = "";
    const flow = simpleFlow({
      A: {
        type: "agent",
        prompt: "a",
        resultTag: "R",
        transitions: { alpha: { goto: "B" } },
      },
      B: {
        type: "agent",
        prompt: "b",
        resultTag: "R",
        transitions: { beta: { goto: "C" } },
      },
      C: {
        type: "agent",
        prompt: "prev_a={steps.A.marker} prev_b={steps.B.marker}",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "A");

    const delegates = makeMockDelegates({
      responses: {
        A: { text: "<R>alpha</R>", ok: true },
        B: { text: "<R>beta</R>", ok: true },
        C: (sn, prompt) => { cPrompt = prompt; return { text: "<R>ok</R>", ok: true }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(cPrompt.includes("prev_a=alpha"));
    assert.ok(cPrompt.includes("prev_b=beta"));
  });
});

describe("FlowEngine — fault tolerance: edge cases", () => {
  it("empty transitions object causes no_transition", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: {},
        onUnknownMaxRetries: 0,
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: "<R>anything</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "no_transition");
  });

  it("step with only done transitions terminates correctly", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: { success: { done: "ok" }, failure: { done: "failed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: "<R>success</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "ok");
    assert.equal(result.stepCount, 1);
  });

  it("unknown step type returns unknown_type", async () => {
    const flow = simpleFlow({
      START: {
        type: "invalid_type",
        transitions: { ok: { done: "completed" } },
      },
    });

    const engine = new FlowEngine(flow, makeMockDelegates());
    const result = await engine.run();

    assert.equal(result.status, "unknown_type");
  });

  it("entry override works for mid-flow restart", async () => {
    const flow = simpleFlow({
      SKIP_ME: {
        type: "agent",
        prompt: "should not run",
        resultTag: "R",
        transitions: { ok: { goto: "TARGET" } },
      },
      TARGET: {
        type: "agent",
        prompt: "target",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "SKIP_ME");

    const delegates = makeMockDelegates({
      responses: { TARGET: "<R>ok</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run("TARGET");

    assert.equal(result.status, "completed");
    assert.ok(!delegates.callLog.some(c => c.stepName === "SKIP_ME"));
  });

  it("result history accumulates across all steps", async () => {
    const flow = simpleFlow({
      A: {
        type: "agent",
        prompt: "a",
        resultTag: "R",
        transitions: { ok: { goto: "B" } },
      },
      B: {
        type: "agent",
        prompt: "b",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "A");

    const delegates = makeMockDelegates({
      responses: {
        A: "<R>ok</R>",
        B: "<R>ok</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.ok(result.history.length >= 4);
    assert.ok(result.history.some(h => h.includes("[step")));
    assert.ok(result.elapsed);
  });

  it("flow with single step that immediately done", async () => {
    const flow = simpleFlow({
      START: {
        type: "script",
        run: () => "done",
        transitions: { done: { done: "completed" } },
      },
    });

    const engine = new FlowEngine(flow, makeMockDelegates());
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(result.stepCount, 1);
  });
});

describe("FlowEngine — fault tolerance: retry accumulation edge cases", () => {
  it("retry append accumulates with separators on 3rd+ attempt", async () => {
    let prompts = [];
    const flow = simpleFlow({
      DRAFT: {
        type: "agent",
        prompt: "draft",
        resultTag: "R",
        transitions: { ok: { goto: "CHECK" } },
      },
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        maxRetries: 5,
        transitions: {
          ok: { done: "completed" },
          bad: { retry: "DRAFT", append: { template: "Issue ${output}" } },
        },
      },
    }, "DRAFT");

    let draftCount = 0;
    const delegates = makeMockDelegates({
      responses: {
        DRAFT: (sn, prompt) => {
          draftCount++;
          prompts.push(prompt);
          return { text: `<R>ok</R> draft${draftCount}`, ok: true };
        },
        CHECK: () => {
          return draftCount < 4
            ? { text: `<R>bad</R>\n<ISSUES>issue${draftCount}</ISSUES>`, ok: true }
            : { text: "<R>ok</R>", ok: true };
        },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(draftCount, 4);
    assert.ok(prompts[1].includes("Issue"));
    assert.ok(!prompts[1].includes("───────────────"));
    assert.ok(prompts[2].includes("───────────────"));
    assert.ok(prompts[3].includes("───────────────"));
  });

  it("onMaxRetries defaults to done: max_retries when not specified", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: { retry: { retry: "START", maxRetries: 1 } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: "<R>retry</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "max_retries");
  });
});

describe("FlowEngine — fault tolerance: delegate customization", () => {
  it("custom buildCorrectionPrompt is used", async () => {
    let correctionPrompt = "";
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        onUnknownMaxRetries: 2,
        transitions: { yes: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: "<R>no</R>" },
      buildCorrectionPrompt: (tag, value, validValues) => {
        correctionPrompt = `CUSTOM: ${tag}=${value}, expected=${validValues}`;
        return correctionPrompt + "\nOnly output <R>value</R>.";
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.ok(correctionPrompt.includes("CUSTOM:"));
    assert.ok(correctionPrompt.includes("no"));
    assert.ok(correctionPrompt.includes("yes"));
  });

  it("custom buildParsePrompt is used when marker not found", async () => {
    let parsePromptText = "";
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
        onUnknown: { done: "marker_not_found" },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: "no tags at all" },
      buildParsePrompt: (tag, validValues, text) => {
        parsePromptText = `PARSE: find <${tag}> in text, valid=${validValues}`;
        return parsePromptText;
      },
    });

    const engine = new FlowEngine(flow, delegates);
    await engine.run();

    assert.ok(parsePromptText.includes("PARSE:"));
    assert.ok(parsePromptText.includes("R"));
  });

  it("delegates without runParseAgent skips parse step", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
        onUnknown: { done: "no_marker" },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: "no tags here" },
    });
    delete delegates.runParseAgent;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "no_marker");
  });
});

describe("FlowEngine — fault tolerance: promptFile loading", () => {
  it("promptFile not found uses empty string with warning", async () => {
    let receivedPrompt = "";
    const flow = simpleFlow({
      START: {
        type: "agent",
        promptFile: "nonexistent/file.md",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        START: (sn, prompt) => { receivedPrompt = prompt; return { text: "<R>ok</R>", ok: true }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(receivedPrompt, "");
  });

  it("promptFile takes precedence over inline prompt", async () => {
    let receivedPrompt = "";
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "inline prompt",
        promptFile: "nonexistent.md",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        START: (sn, prompt) => { receivedPrompt = prompt; return { text: "<R>ok</R>", ok: true }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    await engine.run();

    assert.ok(!receivedPrompt.includes("inline prompt"));
  });
});

describe("FlowEngine — fault tolerance: flow-level totalTimeout", () => {
  it("totalTimeout=0 allows unlimited execution (up to maxTotalSteps)", async () => {
    const flow = simpleFlow({
      A: {
        type: "agent",
        prompt: "a",
        resultTag: "R",
        transitions: { next: { goto: "B" } },
      },
      B: {
        type: "agent",
        prompt: "b",
        resultTag: "R",
        transitions: { next: { goto: "A" } },
      },
    }, "A");
    flow.maxTotalSteps = 10;
    flow.totalTimeout = 0;

    const delegates = makeMockDelegates({
      responses: { A: "<R>next</R>", B: "<R>next</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "max_total_steps");
    assert.equal(result.stepCount, 10);
  });
});
