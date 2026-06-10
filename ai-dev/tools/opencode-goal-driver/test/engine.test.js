import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";

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

    loadSubFlow(name) {
      const sub = overrides.subFlows?.[name];
      if (sub) return sub;
      throw new Error(`Mock subflow not found: ${name}`);
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
        resultTag: "AI_STEP_RESULT",
        transitions: {
          created: { goto: "AUDIT" },
          none: { done: "completed" },
        },
      },
      AUDIT: {
        type: "agent",
        prompt: "audit plan",
        resultTag: "AI_STEP_RESULT",
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
          return { text: "<AI_STEP_RESULT>created</AI_STEP_RESULT>", ok: true };
        },
        AUDIT: (sn) => {
          if (draftCount < 3) {
            return { text: `<AI_STEP_RESULT>issues</AI_STEP_RESULT>\n<ISSUES><item>Issue ${draftCount}</item></ISSUES>`, ok: true };
          }
          return { text: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>", ok: true };
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
  it("substitutes variables in prompts and commands", async () => {
    let agentPrompt = "";
    let toolCommand = "";

    const flow = simpleFlow({
      AGENT_STEP: {
        type: "agent",
        prompt: "build module {{module}} in {{projectRoot}}",
        resultTag: "R",
        transitions: { ok: { goto: "TOOL_STEP" } },
      },
      TOOL_STEP: {
        type: "tool",
        command: "./mvnw -pl {{module}} -C",
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
    assert.ok(!agentPrompt.includes("{{module}}"));
    assert.ok(toolCommand.includes("./mvnw -pl test-mod -C"));
    assert.ok(!toolCommand.includes("{{module}}"));
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
  it("completes full dev loop + audit loop", async () => {
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();
    flow.maxTotalSteps = 80;

    let roadmapCount = 0;
    let planAuditCount = 0;
    let closureCount = 0;
    let deepAuditCount = 0;
    let closureScriptPass = false;

    const delegates = makeMockDelegates({
      responses: {
        DETECT_START: { text: "", ok: true, marker: "roadmap" },
        HEALTH_CHECK: true,
        BUILD_VERIFY: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",

        ROADMAP_CHECK: () => {
          roadmapCount++;
          return roadmapCount <= 1
            ? { text: "<AI_STEP_RESULT>pending</AI_STEP_RESULT>\n<ROADMAP_ITEMS><item>P1</item></ROADMAP_ITEMS>", ok: true }
            : { text: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>", ok: true };
        },

        FIX_BUILD: "<AI_STEP_RESULT>fixed</AI_STEP_RESULT>",

        PLAN_DRAFT: "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS>\n  <PLAN_FILE>ai-dev/plans/test-plan.md</PLAN_FILE>\n</FLOW_VARS>",

        PLAN_AUDIT: () => {
          planAuditCount++;
          return planAuditCount <= 1
            ? { text: "<AI_STEP_RESULT>issues</AI_STEP_RESULT>\n<ISSUES><item>Major: fix X</item></ISSUES>", ok: true }
            : { text: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>", ok: true };
        },

        EXECUTE: "<AI_STEP_RESULT>success</AI_STEP_RESULT>",

        "CLOSURE_VERIFY.AI_AUDIT": () => {
          closureCount++;
          closureScriptPass = true;
          return closureCount === 1
            ? { text: "<AI_STEP_RESULT>incomplete</AI_STEP_RESULT>\n<REMAINING><item>todo</item></REMAINING>", ok: true }
            : { text: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>", ok: true };
        },

        DEEP_AUDIT: () => {
          deepAuditCount++;
          return deepAuditCount <= 1
            ? { text: "<AI_STEP_RESULT>issues</AI_STEP_RESULT>", ok: true }
            : { text: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>", ok: true };
        },

        ADVERSARIAL: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
      },

      async runScript(stepName, stepDef) {
        if (stepName === "CLOSURE_VERIFY.SCRIPT_CHECK") {
          this.callLog.push({ type: "script", stepName });
          return { ok: true, marker: closureScriptPass ? "pass" : "fail", text: closureScriptPass ? "pass" : "fail" };
        }
        return undefined;
      },
    });

    delegates.config = { moduleName: "test-mod", projectRoot: "/tmp/test" };
    delegates.vars = { module: "test-mod", projectRoot: "/tmp/test" };

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(result.stepCount > 10, `expected >10 steps, got ${result.stepCount}`);

    assert.ok(delegates.callLog.some(c => c.stepName === "PLAN_AUDIT"), "PLAN_AUDIT should be called");
    assert.ok(delegates.callLog.some(c => c.stepName === "CLOSURE_VERIFY.SCRIPT_CHECK"), "CLOSURE_VERIFY.SCRIPT_CHECK should be called");
    assert.ok(delegates.callLog.some(c => c.stepName === "CLOSURE_VERIFY.AI_AUDIT"), "CLOSURE_VERIFY.AI_AUDIT should be called");
    assert.ok(delegates.callLog.some(c => c.stepName === "DEEP_AUDIT"), "DEEP_AUDIT should be called");
    assert.ok(delegates.callLog.some(c => c.stepName === "ADVERSARIAL"), "ADVERSARIAL should be called");
  });

  it("handles execute entry: DETECT_START → execute → EXECUTE is called", async () => {
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();

    // After ROADMAP_CHECK returns "complete" and DEEP_AUDIT finds issues,
    // PLAN_DRAFT → PLAN_AUDIT → EXECUTE path will be taken
    let deepAuditCount = 0;

    const delegates = makeMockDelegates({
      responses: {
        HEALTH_CHECK: true,
        EXECUTE: "<AI_STEP_RESULT>success</AI_STEP_RESULT>",
        "CLOSURE_VERIFY.AI_AUDIT": "<AI_STEP_RESULT>complete</AI_STEP_RESULT>",
        BUILD_VERIFY: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
        ROADMAP_CHECK: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>",
        DEEP_AUDIT: () => {
          deepAuditCount++;
          return deepAuditCount === 1
            ? { text: "<AI_STEP_RESULT>issues</AI_STEP_RESULT>", ok: true }
            : { text: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>", ok: true };
        },
        ADVERSARIAL: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
        PLAN_DRAFT: "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS>\n  <PLAN_FILE>ai-dev/plans/test-plan.md</PLAN_FILE>\n</FLOW_VARS>",
        PLAN_AUDIT: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>",
        FIX_BUILD: "<AI_STEP_RESULT>fixed</AI_STEP_RESULT>",
        EXECUTE: "<AI_STEP_RESULT>success</AI_STEP_RESULT>",
      },
      async runScript(stepName) {
        if (stepName === "CLOSURE_VERIFY.SCRIPT_CHECK") return { ok: true, marker: "pass", text: "pass" };
        return undefined;
      },
    });

    delegates.config = { moduleName: "test-mod", projectRoot: "/tmp/test" };
    delegates.vars = { module: "test-mod", projectRoot: "/tmp/test" };

    flow.steps.DETECT_START.run = () => "execute";

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(delegates.callLog.some(c => c.stepName === "EXECUTE"),
      `Expected EXECUTE in callLog: ${delegates.callLog.map(c => c.stepName).join(", ")}`);
  });

  it("handles build failure → fix → retry", async () => {
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();
    flow.maxTotalSteps = 80;

    let buildCount = 0;

    const delegates = makeMockDelegates({
      responses: {
        DETECT_START: { text: "", ok: true, marker: "roadmap" },
        HEALTH_CHECK: () => {
          buildCount++;
          return buildCount === 1 ? { ok: false, logFile: null } : { ok: true, logFile: null };
        },
        FIX_BUILD: "<AI_STEP_RESULT>fixed</AI_STEP_RESULT>",
        ROADMAP_CHECK: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>",
        DEEP_AUDIT: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
        ADVERSARIAL: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
        PLAN_DRAFT: "<AI_STEP_RESULT>none</AI_STEP_RESULT>",
        PLAN_AUDIT: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>",
        EXECUTE: "<AI_STEP_RESULT>success</AI_STEP_RESULT>",
        "CLOSURE_VERIFY.AI_AUDIT": "<AI_STEP_RESULT>complete</AI_STEP_RESULT>",
        BUILD_VERIFY: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
      },
      async runScript(stepName) {
        if (stepName === "CLOSURE_VERIFY.SCRIPT_CHECK") return { ok: true, marker: "pass", text: "pass" };
        return undefined;
      },
    });

    delegates.config = { moduleName: "test-mod", projectRoot: "/tmp/test" };
    delegates.vars = { module: "test-mod", projectRoot: "/tmp/test" };

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(delegates.callLog.some(c => c.stepName === "FIX_BUILD"));
  });
});

// ═══════════════════════════════════════════
// 11.5. Group step
// ═══════════════════════════════════════════

describe("FlowEngine — group step", () => {
  function groupFlow(groupDef) {
    const { transitions: outerTransitions, ...rest } = groupDef;
    return simpleFlow({
      START: {
        type: "group",
        maxRounds: rest.maxRounds || 3,
        onExhausted: rest.onExhausted || "fail",
        steps: rest.steps,
        transitions: outerTransitions || {
          pass: { done: "completed" },
          fail: { done: "failed" },
        },
      },
    });
  }

  it("exits immediately when script check passes (no AI needed)", async () => {
    const flow = groupFlow({
      steps: {
        CHECK: {
          type: "script",
          run: async () => "pass",
          transitions: { pass: { exit: "pass" }, fail: { goto: "FIX" } },
        },
        FIX: {
          type: "agent",
          prompt: "fix it",
          resultTag: "AI_STEP_RESULT",
          transitions: { fixed: { goto: "_retry" }, failed: { exit: "fail" } },
        },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "START.FIX": { text: "<AI_STEP_RESULT>fixed</AI_STEP_RESULT>", ok: true },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(!delegates.callLog.some(c => c.stepName === "START.FIX"),
      "FIX agent should NOT be called when CHECK passes");
  });

  it("calls AI audit when script fails, then re-checks", async () => {
    let scriptCallCount = 0;

    const flow = groupFlow({
      steps: {
        CHECK: {
          type: "script",
          run: async () => {
            scriptCallCount++;
            return scriptCallCount >= 2 ? "pass" : "fail";
          },
          transitions: { pass: { exit: "pass" }, fail: { goto: "FIX" } },
        },
        FIX: {
          type: "agent",
          prompt: "fix it",
          resultTag: "AI_STEP_RESULT",
          transitions: { fixed: { goto: "_retry" }, failed: { exit: "fail" } },
        },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "START.FIX": { text: "<AI_STEP_RESULT>fixed</AI_STEP_RESULT>", ok: true },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(scriptCallCount, 2, "CHECK should be called twice (round 1 fail, round 2 pass)");
    assert.ok(delegates.callLog.some(c => c.stepName === "START.FIX"),
      "FIX agent should be called once");
  });

  it("exits fail when AI audit returns incomplete", async () => {
    const flow = groupFlow({
      steps: {
        CHECK: {
          type: "script",
          run: async () => "fail",
          transitions: { pass: { exit: "pass" }, fail: { goto: "AUDIT" } },
        },
        AUDIT: {
          type: "agent",
          prompt: "audit it",
          resultTag: "AI_STEP_RESULT",
          transitions: { complete: { goto: "_retry" }, incomplete: { exit: "fail" } },
          onError: { exit: "fail" },
        },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "START.AUDIT": { text: "<AI_STEP_RESULT>incomplete</AI_STEP_RESULT>\n<REMAINING><item>todo</item></REMAINING>", ok: true },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed");
  });

  it("exits fail after maxRounds exhausted", async () => {
    let scriptCallCount = 0;

    const flow = groupFlow({
      maxRounds: 2,
      steps: {
        CHECK: {
          type: "script",
          run: async () => { scriptCallCount++; return "fail"; },
          transitions: { pass: { exit: "pass" }, fail: { goto: "FIX" } },
        },
        FIX: {
          type: "agent",
          prompt: "fix it",
          resultTag: "AI_STEP_RESULT",
          transitions: { fixed: { goto: "_retry" }, failed: { exit: "fail" } },
        },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "START.FIX": { text: "<AI_STEP_RESULT>fixed</AI_STEP_RESULT>", ok: true },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed");
    assert.equal(scriptCallCount, 2, "CHECK should be called once per round (2 rounds)");
  });

  it("handles subprocess error in AI sub-step", async () => {
    const flow = groupFlow({
      steps: {
        CHECK: {
          type: "script",
          run: async () => "fail",
          transitions: { pass: { exit: "pass" }, fail: { goto: "AUDIT" } },
        },
        AUDIT: {
          type: "agent",
          prompt: "audit it",
          resultTag: "AI_STEP_RESULT",
          transitions: { complete: { goto: "_retry" }, incomplete: { exit: "fail" } },
          onError: { exit: "fail" },
        },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "START.AUDIT": { text: "killed", ok: false },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed");
  });

  it("propagates sub-step output text on exit", async () => {
    const flow = groupFlow({
      steps: {
        CHECK: {
          type: "script",
          run: async () => "fail",
          transitions: { pass: { exit: "pass" }, fail: { goto: "AUDIT" } },
        },
        AUDIT: {
          type: "agent",
          prompt: "audit it",
          resultTag: "AI_STEP_RESULT",
          transitions: {
            complete: { goto: "_retry" },
            incomplete: { exit: "fail" },
          },
        },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "START.AUDIT": {
          text: "<AI_STEP_RESULT>incomplete</AI_STEP_RESULT>\n<REMAINING><item>X</item></REMAINING>",
          ok: true,
        },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed");
    const groupResult = engine.context.get("START");
    assert.ok(groupResult.text.includes("<REMAINING>"),
      `Group output should contain REMAINING XML, got: ${groupResult.text}`);
  });

  it("uses onExhausted marker when maxRounds reached", async () => {
    const flow = groupFlow({
      maxRounds: 1,
      onExhausted: "timeout",
      steps: {
        CHECK: {
          type: "script",
          run: async () => "fail",
          transitions: { pass: { exit: "pass" }, fail: { goto: "FIX" } },
        },
        FIX: {
          type: "agent",
          prompt: "fix",
          resultTag: "AI_STEP_RESULT",
          transitions: { fixed: { goto: "_retry" } },
        },
      },
      transitions: {
        pass: { done: "completed" },
        timeout: { done: "timeout_exhausted" },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "START.FIX": { text: "<AI_STEP_RESULT>fixed</AI_STEP_RESULT>", ok: true },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "timeout_exhausted");
  });
});

// ═══════════════════════════════════════════
// 12. Subflow step
// ═══════════════════════════════════════════

import { mkdtempSync, writeFileSync, mkdirSync, rmSync } from "node:fs";
import { resolve, join } from "node:path";
import { tmpdir } from "node:os";

function withSubflowDir(fn) {
  return async () => {
    const dir = mkdtempSync(join(tmpdir(), "subflow-test-"));
    mkdirSync(dir, { recursive: true });
    try {
      await fn(dir);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  };
}

function writeSubflow(dir, name, flowDef) {
  writeFileSync(resolve(dir, `${name}.json`), JSON.stringify(flowDef));
}

describe("FlowEngine — subflow step", () => {
  it("runs a child subflow that completes", withSubflowDir(async (dir) => {
    writeSubflow(dir, "child", {
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: { type: "agent", prompt: "work", transitions: { done: { done: "completed" } } },
      },
    });

    const flow = simpleFlow({
      START: {
        type: "subflow", flow: "child",
        transitions: { complete: { done: "completed" }, failed: { done: "failed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { "WORK": "<AI_STEP_RESULT>done</AI_STEP_RESULT>" },
      config: { moduleName: "test-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
  }));

  it("runs a child subflow that fails", withSubflowDir(async (dir) => {
    writeSubflow(dir, "child-fail", {
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: { type: "agent", prompt: "work", transitions: { done: { done: "completed" } } },
      },
    });

    const flow = simpleFlow({
      START: {
        type: "subflow", flow: "child-fail",
        transitions: { complete: { done: "failed" }, failed: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { "WORK": { text: "", ok: false } },
      config: { moduleName: "test-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
  }));

  it("forEach: all items complete", withSubflowDir(async (dir) => {
    writeSubflow(dir, "child", {
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: {
          type: "agent", prompt: "work on {{forEachItem}}",
          transitions: { done: { done: "completed" } },
        },
      },
    });

    const flow = simpleFlow({
      START: {
        type: "subflow", flow: "child", forEach: "items",
        transitions: {
          all_complete: { done: "completed" },
          some_failed: { done: "failed" },
          all_failed: { done: "failed" },
        },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "WORK": (sn, prompt) => {
          const item = prompt.includes("item-a") ? "item-a" : "item-b";
          return { text: `<AI_STEP_RESULT>done</AI_STEP_RESULT>`, ok: true };
        },
      },
      config: { moduleName: "test-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.vars.items = '["item-a","item-b"]';
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
  }));

  it("forEach: some items fail", withSubflowDir(async (dir) => {
    writeSubflow(dir, "child", {
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: {
          type: "agent", prompt: "work on {{forEachItem}}",
          transitions: { done: { done: "completed" } },
        },
      },
    });

    let callCount = 0;
    const flow = simpleFlow({
      START: {
        type: "subflow", flow: "child", forEach: "items",
        transitions: {
          all_complete: { done: "all_ok" },
          some_failed: { done: "completed" },
          all_failed: { done: "all_bad" },
        },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "WORK": () => {
          callCount++;
          return callCount === 1
            ? { text: `<AI_STEP_RESULT>done</AI_STEP_RESULT>`, ok: true }
            : { text: "", ok: false };
        },
      },
      config: { moduleName: "test-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.vars.items = '["item-a","item-b"]';
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
  }));

  it("forEach: all items fail", withSubflowDir(async (dir) => {
    writeSubflow(dir, "child", {
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: {
          type: "agent", prompt: "work on {{forEachItem}}",
          transitions: { done: { done: "completed" } },
        },
      },
    });

    const flow = simpleFlow({
      START: {
        type: "subflow", flow: "child", forEach: "items",
        transitions: {
          all_complete: { done: "all_ok" },
          some_failed: { done: "some_ok" },
          all_failed: { done: "completed" },
        },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "WORK": { text: "", ok: false },
      },
      config: { moduleName: "test-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.vars.items = '["item-a","item-b"]';
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
  }));

  it("forEach: empty list", withSubflowDir(async (dir) => {
    writeSubflow(dir, "child", {
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: { type: "agent", prompt: "work", transitions: { done: { done: "completed" } } },
      },
    });

    const flow = simpleFlow({
      START: {
        type: "subflow", flow: "child", forEach: "items",
        transitions: { all_complete: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      config: { moduleName: "test-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.vars.items = "[]";
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
  }));

  it("resolves flowArgs template variables from parent context", withSubflowDir(async (dir) => {
    writeSubflow(dir, "child", {
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: {
          type: "agent", prompt: "build {{planFile}} in {{module}}",
          transitions: { done: { done: "completed" } },
        },
      },
    });

    let childPrompt = "";
    const flow = simpleFlow({
      START: {
        type: "subflow", flow: "child",
        flowArgs: { planFile: "ai-dev/plans/test.md", module: "{{module}}" },
        transitions: { complete: { done: "completed" }, failed: { done: "failed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "WORK": (sn, prompt) => { childPrompt = prompt; return { text: "<AI_STEP_RESULT>done</AI_STEP_RESULT>", ok: true }; },
      },
      config: { moduleName: "my-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.vars = { module: "my-mod", projectRoot: "/tmp/test" };
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    assert.ok(childPrompt.includes("ai-dev/plans/test.md"));
    assert.ok(childPrompt.includes("my-mod"));
  }));

  it("propagates flowVars from child back to parent", withSubflowDir(async (dir) => {
    writeSubflow(dir, "child", {
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: {
          type: "agent", prompt: "create plan",
          transitions: { done: { done: "completed" } },
        },
      },
    });

    let parentPrompt = "";
    const flow = simpleFlow({
      SUB: {
        type: "subflow", flow: "child",
        transitions: { complete: { goto: "AFTER" }, failed: { done: "failed" } },
      },
      AFTER: {
        type: "agent", prompt: "check {{PLAN_FILE}}",
        transitions: { ok: { done: "completed" } },
      },
    }, "SUB");

    const delegates = makeMockDelegates({
      responses: {
        "WORK": "<AI_STEP_RESULT>done</AI_STEP_RESULT>\n<FLOW_VARS>\n  <PLAN_FILE>ai-dev/plans/test.md</PLAN_FILE>\n</FLOW_VARS>",
        AFTER: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>",
      },
      config: { moduleName: "test-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    // verify AFTER received PLAN_FILE from child's flowVars
    const afterCall = delegates.callLog.find(c => c.stepName === "AFTER");
    assert.ok(afterCall, "AFTER should be called");
    assert.ok(afterCall.prompt.includes("ai-dev/plans/test.md"));
  }));
});

// ═══════════════════════════════════════════
// 13. Flow definition validation
// ═══════════════════════════════════════════

describe("Flow definition — structural validation", () => {
  it("all goto/retry targets reference existing steps", async () => {
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();
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
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();
    assert.ok(flow.steps[flow.entry], `entry "${flow.entry}" not found`);
  });

  it("every step has type and transitions", async () => {
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();
    for (const [name, step] of Object.entries(flow.steps)) {
      assert.ok(step.type, `${name} has no type`);
      assert.ok(step.transitions || step.type === "script",
        `${name} has no transitions`);
    }
  });

  it("at least one step has a done transition", async () => {
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();
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
        resultTag: "AI_STEP_RESULT",
        transitions: {
          created: { goto: "PLAN_AUDIT" },
          none: { done: "completed" },
        },
      },
      PLAN_AUDIT: {
        type: "agent",
        prompt: "audit plan",
        resultTag: "AI_STEP_RESULT",
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
        resultTag: "AI_STEP_RESULT",
        transitions: { success: { done: "completed" } },
      },
    }, "PLAN_DRAFT");

    let draftCount = 0;
    const delegates = makeMockDelegates({
      responses: {
        PLAN_DRAFT: () => {
          draftCount++;
          return { text: "<AI_STEP_RESULT>created</AI_STEP_RESULT>", ok: true };
        },
        PLAN_AUDIT: () => {
          if (draftCount < 2) {
            return { text: "<AI_STEP_RESULT>issues</AI_STEP_RESULT>\n<ISSUES><item severity=\"Major\">fix exit criteria</item></ISSUES>", ok: true };
          }
          return { text: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>", ok: true };
        },
        EXECUTE: "<AI_STEP_RESULT>success</AI_STEP_RESULT>",
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
// 14. Case-insensitive marker alias resolution
// ═══════════════════════════════════════════

describe("FlowEngine — case-insensitive marker aliases", () => {
  it("resolves marker via case-insensitive fallback (CREATED → created)", async () => {
    const flow = simpleFlow({
      PLAN_DRAFT: {
        type: "agent",
        prompt: "draft",
        resultTag: "AI_STEP_RESULT",
        transitions: { created: { goto: "NEXT" }, none: { done: "failed" } },
      },
      NEXT: {
        type: "agent",
        prompt: "next",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "PLAN_DRAFT");

    const delegates = makeMockDelegates({
      responses: {
        PLAN_DRAFT: "<AI_STEP_RESULT>CREATED</AI_STEP_RESULT>",
        NEXT: "<R>ok</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
  });

  it("resolves markers via case-insensitive match", async () => {
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

    const delegates = makeMockDelegates({
      responses: {
        START: "<R>FIXED</R>",
        DONE: "<R>ok</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
  });

  it("falls back to no_transition when alias not found", async () => {    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        onUnknownMaxRetries: 0,
        transitions: { yes: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
        responses: { START: "<R>nonexistent_value</R>" },
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
// 16. StepResult unification: new tests
// ═══════════════════════════════════════════

describe("FlowEngine — StepResult unification", () => {
  it("group step aggregates sub-step vars into StepResult.vars", async () => {
    const flow = simpleFlow({
      START: {
        type: "group",
        maxRounds: 1,
        steps: {
          CHECK: {
            type: "agent",
            prompt: "check",
            resultTag: "R",
            transitions: { pass: { exit: "pass" } },
          },
        },
        transitions: {
          pass: { goto: "AFTER" },
          fail: { done: "failed" },
        },
      },
      AFTER: {
        type: "agent",
        prompt: "use {{X}} and {{Y}}",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "START");

    const delegates = makeMockDelegates({
      responses: {
        "START.CHECK": { text: "<R>pass</R>\n<FLOW_VARS>\n<X>hello</X>\n<Y>world</Y>\n</FLOW_VARS>", ok: true },
        AFTER: (sn, prompt) => {
          assert.ok(prompt.includes("hello"), "AFTER should have X=hello from group vars");
          assert.ok(prompt.includes("world"), "AFTER should have Y=world from group vars");
          return { text: "<R>ok</R>", ok: true };
        },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    const groupResult = engine.context.get("START");
    assert.ok(groupResult.vars, "group result should have vars field");
    assert.equal(groupResult.vars.X, "hello");
    assert.equal(groupResult.vars.Y, "world");
  });

  it("subflow step propagates vars via StepResult.vars (single)", withSubflowDir(async (dir) => {
    writeSubflow(dir, "child", {
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: {
          type: "agent", prompt: "work",
          transitions: { done: { done: "completed" } },
        },
      },
    });

    const flow = simpleFlow({
      SUB: {
        type: "subflow", flow: "child",
        transitions: { complete: { goto: "AFTER" }, failed: { done: "failed" } },
      },
      AFTER: {
        type: "agent", prompt: "check {{PLAN_FILE}}",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "SUB");

    const delegates = makeMockDelegates({
      responses: {
        "WORK": "<AI_STEP_RESULT>done</AI_STEP_RESULT>\n<FLOW_VARS>\n  <PLAN_FILE>my-plan.md</PLAN_FILE>\n</FLOW_VARS>",
        AFTER: (sn, prompt) => {
          assert.ok(prompt.includes("my-plan.md"), "AFTER should have PLAN_FILE from subflow vars");
          return { text: "<R>ok</R>", ok: true };
        },
      },
      config: { moduleName: "test-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    const subResult = engine.context.get("SUB");
    assert.ok(subResult.vars, "subflow result should have vars field");
    assert.equal(subResult.vars.PLAN_FILE, "my-plan.md");
  }));

  it("subflow forEach aggregates vars from all children via StepResult.vars", withSubflowDir(async (dir) => {
    writeSubflow(dir, "child", {
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: {
          type: "agent", prompt: "work on {{forEachItem}}",
          transitions: { done: { done: "completed" } },
        },
      },
    });

    let callIdx = 0;
    const flow = simpleFlow({
      START: {
        type: "subflow", flow: "child", forEach: "items",
        transitions: { all_complete: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "WORK": () => {
          callIdx++;
          return {
            text: `<AI_STEP_RESULT>done</AI_STEP_RESULT>\n<FLOW_VARS>\n  <ITEM_KEY>item-${callIdx}</ITEM_KEY>\n</FLOW_VARS>`,
            ok: true,
          };
        },
      },
      config: { moduleName: "test-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.vars.items = '["a","b"]';
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    const subResult = engine.context.get("START");
    assert.ok(subResult.vars, "forEach subflow result should have vars field");
    assert.equal(subResult.vars.ITEM_KEY, "item-2", "last-write-wins aggregation");
  }));

  it("agent marker correction stays internal (parse agent fallback + correction)", async () => {
    let agentCalls = [];
    let parseCalls = [];

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
        agentCalls.push({ stepName, sessionId });
        if (stepName === "START") return { text: "<R>no</R>", ok: true, sessionId: "ses_abc" };
        if (stepName.includes("correct")) return { text: "<R>yes</R>", ok: true };
        return { text: "<R>no</R>", ok: true };
      },
      async runParseAgent(stepName, prompt) {
        parseCalls.push({ stepName });
        return { text: "<R>unknown</R>", ok: true };
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(parseCalls.length === 0, "parse agent should NOT be called when tag exists");
    assert.ok(agentCalls.some(c => c.stepName.includes("correct")), "correction should happen internally");
    assert.ok(agentCalls.length >= 2, "at least original + correction call");
  });

  it("group step with tool sub-step ok:false does not trigger error path", async () => {
    const flow = simpleFlow({
      START: {
        type: "group",
        maxRounds: 1,
        steps: {
          BUILD: {
            type: "tool",
            command: "exit 1",
            transitions: { pass: { exit: "pass" }, fail: { exit: "fail" } },
          },
        },
        transitions: {
          pass: { done: "completed" },
          fail: { done: "build_failed" },
        },
      },
    }, "START");

    const delegates = makeMockDelegates({
      responses: {
        "START.BUILD": false,
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "build_failed");
    const groupResult = engine.context.get("START");
    assert.equal(groupResult.marker, "fail");
    assert.equal(groupResult.ok, true);
  });
});
