import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, mkdirSync, rmSync } from "node:fs";
import { resolve, join } from "node:path";
import { tmpdir } from "node:os";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

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

describe("FlowEngine — goal driver integration", () => {
  function mockSubFlows() {
    const planExec = {
      name: "plan-execution", entry: "EXECUTE", maxTotalSteps: 10,
      markerAliases: { success: "pass", ok: "pass", done: "pass", error: "fail", failed: "fail" },
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
          type: "agent", prompt: "closure audit {{PLAN_FILE}}",
          resultTag: "AI_STEP_RESULT",
          transitions: { approved: { goto: "BUILD_VERIFY" }, issues: { done: "completed" } },
          onMaxRetries: { goto: "BUILD_VERIFY" },
        },
        BUILD_VERIFY: {
          type: "agent", prompt: "build verify {{PLAN_FILE}}",
          resultTag: "AI_STEP_RESULT",
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
          transitions: { clean: { done: "completed" }, issues: { retry: "DEEP_AUDIT", maxRetries: 3 } },
        },
      },
    };
    return { "plan-execution": planExec, "deep-audit-loop": deepAudit };
  }

  it("completes full dev loop + audit loop", async () => {
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();
    flow.maxTotalSteps = 80;
    flow.steps.DEEP_AUDIT_LOOP.transitions.clean = { done: "completed" };

    let roadmapCount = 0;
    let deepAuditCount = 0;

    const delegates = makeMockDelegates({
      subFlows: mockSubFlows(),
      responses: {
        HEALTH_CHECK: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",

        ROADMAP_CHECK: () => {
          roadmapCount++;
          return roadmapCount <= 1
            ? { text: "<AI_STEP_RESULT>pending</AI_STEP_RESULT>\n<ROADMAP_ITEMS><item>P1</item></ROADMAP_ITEMS>", ok: true }
            : { text: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>", ok: true };
        },

        PLAN_DRAFT: "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS>\n  <PLAN_FILE>/tmp/_goal-driver-test-plan.md</PLAN_FILE>\n</FLOW_VARS>",

        "EXECUTE": "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
        "CLOSURE_SCRIPT_CHECK": "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
        "CLOSURE_AUDIT": "<AI_STEP_RESULT>approved</AI_STEP_RESULT>",
        "BUILD_VERIFY": "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",

        DEEP_AUDIT: () => {
          deepAuditCount++;
          return deepAuditCount <= 1
            ? { text: "<AI_STEP_RESULT>issues</AI_STEP_RESULT>", ok: true }
            : { text: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>", ok: true };
        },
        ADVERSARIAL: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
        DRAFT_PLANS: "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS><PLAN_FILE>/tmp/_goal-driver-test-plan.md</PLAN_FILE></FLOW_VARS>",
      },
    });

    delegates.config = { moduleName: "test-mod", projectRoot: "/tmp/test" };
    delegates.vars = { module: "test-mod", projectRoot: "/tmp/test" };

    const { mkdirSync, writeFileSync, rmSync } = await import("node:fs");
    rmSync("/tmp/test/ai-dev/plans", { recursive: true, force: true });
    mkdirSync("/tmp/_goal-driver-test", { recursive: true });
    writeFileSync("/tmp/_goal-driver-test-plan.md", "# Test Plan\n\n> **Plan Status**: active");

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(result.stepCount >= 7, `expected >=7 steps, got ${result.stepCount}`);

    assert.ok(delegates.callLog.some(c => c.stepName === "PLAN_DRAFT"), "PLAN_DRAFT should be called");
    assert.ok(delegates.callLog.some(c => c.stepName === "DEEP_AUDIT"), "DEEP_AUDIT should be called");
  });

  it("retries PLAN_DRAFT with feedback when PLAN_FILE does not exist, then proceeds normally", async () => {
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();
    flow.maxTotalSteps = 40;
    flow.steps.DEEP_AUDIT_LOOP.transitions.clean = { done: "completed" };

    let planDraftCalls = 0;
    let roadmapCalls = 0;
    const delegates = makeMockDelegates({
      subFlows: mockSubFlows(),
      responses: {
        HEALTH_CHECK: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
        ROADMAP_CHECK: () => {
          roadmapCalls++;
          return roadmapCalls <= 1
            ? { text: "<AI_STEP_RESULT>pending</AI_STEP_RESULT>\n<ROADMAP_ITEMS><item>P1</item></ROADMAP_ITEMS>", ok: true }
            : { text: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>", ok: true };
        },
        "EXECUTE": "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
        CLOSURE_SCRIPT_CHECK: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
        CLOSURE_AUDIT: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>",
        BUILD_VERIFY: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
        DEEP_AUDIT: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
        ADVERSARIAL: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
        DRAFT_PLANS: "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS><PLAN_FILE>/tmp/_goal-driver-test-plan.md</PLAN_FILE></FLOW_VARS>",
        PLAN_DRAFT: () => {
          planDraftCalls++;
          if (planDraftCalls === 1) {
            return { text: "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS>\n  <PLAN_FILE>ai-dev/plans/YYYY-MM-DD-NNN-slug.md</PLAN_FILE>\n</FLOW_VARS>", ok: true };
          }
          return { text: "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS>\n  <PLAN_FILE>/tmp/_goal-driver-test-plan.md</PLAN_FILE>\n</FLOW_VARS>", ok: true };
        },
      },
    });

    delegates.config = { moduleName: "test-mod", projectRoot: "/tmp/test" };
    delegates.vars = { module: "test-mod", projectRoot: "/tmp/test" };
    const { rmSync, mkdirSync, writeFileSync } = await import("node:fs");
    rmSync("/tmp/test/ai-dev/plans", { recursive: true, force: true });
    mkdirSync("/tmp/_goal-driver-test", { recursive: true });
    writeFileSync("/tmp/_goal-driver-test-plan.md", "# Test Plan\n\n> **Plan Status**: active");

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(planDraftCalls, 2, "PLAN_DRAFT should be called twice (first with placeholder, retry with valid)");
    assert.ok(delegates.callLog.some(c => c.stepName === "ROADMAP_CHECK"),
      "flow should progress past PLAN_DRAFT to ROADMAP_CHECK after valid PLAN_FILE on retry");
  });

  it("handles execute entry via execute-all-active-plans with active plan", async () => {
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();
    flow.maxTotalSteps = 40;
    flow.steps.DEEP_AUDIT_LOOP.transitions.clean = { done: "completed" };

    let scanCalls = 0;

    const delegates = makeMockDelegates({
      subFlows: mockSubFlows(),
      responses: {
        HEALTH_CHECK: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
        ROADMAP_CHECK: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>",
        DEEP_AUDIT: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
        ADVERSARIAL: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
        DRAFT_PLANS: "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS><PLAN_FILE>/tmp/_goal-driver-test-plan.md</PLAN_FILE></FLOW_VARS>",
        "EXECUTE": "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
        CLOSURE_SCRIPT_CHECK: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
        CLOSURE_AUDIT: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>",
        BUILD_VERIFY: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
      },
      async runScript(stepName, stepDef) {
        if (stepName === "EXECUTE_ALL_ACTIVE_PLANS.SCAN_PLANS") {
          scanCalls++;
          return scanCalls === 1
            ? { marker: "ok", vars: { items: '["/tmp/test/ai-dev/plans/active-plan.md"]' }, text: "ok" }
            : { marker: "empty", vars: { items: "[]" }, text: "empty" };
        }
        return undefined;
      },
    });

    delegates.config = { moduleName: "test-mod", projectRoot: "/tmp/test" };
    delegates.vars = { module: "test-mod", projectRoot: "/tmp/test" };

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    const executeCalls = delegates.callLog.filter(c => c.stepName === "EXECUTE");
    assert.ok(executeCalls.length >= 1, `Expected EXECUTE in child subflow, callLog: ${delegates.callLog.map(c => c.stepName).join(", ")}`);
  });

  it("retries HEALTH_CHECK on failure, then proceeds", async () => {
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();
    flow.maxTotalSteps = 40;
    flow.steps.DEEP_AUDIT_LOOP.transitions.clean = { done: "completed" };

    let healthCount = 0;

    const delegates = makeMockDelegates({
      subFlows: mockSubFlows(),
      responses: {
        HEALTH_CHECK: () => {
          healthCount++;
          return healthCount < 2
            ? { text: "<AI_STEP_RESULT>fail</AI_STEP_RESULT>", ok: true }
            : { text: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>", ok: true };
        },
        ROADMAP_CHECK: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>",
        DEEP_AUDIT: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
        ADVERSARIAL: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
        DRAFT_PLANS: "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS><PLAN_FILE>/tmp/_goal-driver-test-plan.md</PLAN_FILE></FLOW_VARS>",
      },
    });

    delegates.config = { moduleName: "test-mod", projectRoot: "/tmp/test" };
    delegates.vars = { module: "test-mod", projectRoot: "/tmp/test" };

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(healthCount, 2, "HEALTH_CHECK should be retried once after first fail");
  });
});

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

  it("at least one step has a done transition (in transitions, onError, or onMaxRetries)", async () => {
    const { createGoalDriverFlow } = await import("../src/flow-loader.js");
    const flow = createGoalDriverFlow();
    const hasDone = Object.values(flow.steps).some(step =>
      Object.values(step.transitions || {}).some(t => t.done) ||
      (step.onError && step.onError.done) ||
      (step.onMaxRetries && step.onMaxRetries.done),
    );
    assert.ok(hasDone, "no step has a done transition");
  });
});

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
        transitions: { done: { goto: "AFTER" }, failed: { done: "failed" } },
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
