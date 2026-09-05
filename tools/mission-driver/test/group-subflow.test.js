import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow, mockSubFlows } from "./helpers.js";

import { mkdtempSync, writeFileSync, mkdirSync, rmSync } from "node:fs";
import { resolve, join } from "node:path";
import { tmpdir } from "node:os";

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
          type: "agent", prompt: "build {{planFile}} in {{shortName}}",
          transitions: { done: { done: "completed" } },
        },
      },
    });

    let childPrompt = "";
    const flow = simpleFlow({
      START: {
        type: "subflow", flow: "child",
        flowArgs: { planFile: "docs/plans/test.md", shortName: "{{shortName}}" },
        transitions: { complete: { done: "completed" }, failed: { done: "failed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "WORK": (sn, prompt) => { childPrompt = prompt; return { text: "<AI_STEP_RESULT>done</AI_STEP_RESULT>", ok: true }; },
      },
      config: { moduleName: "my-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.vars = { module: "my-mod", shortName: "my-mod", projectRoot: "/tmp/test" };
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    assert.ok(childPrompt.includes("docs/plans/test.md"));
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
        "WORK": "<AI_STEP_RESULT>done</AI_STEP_RESULT>\n<FLOW_VARS>\n  <PLAN_FILE>docs/plans/test.md</PLAN_FILE>\n</FLOW_VARS>",
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
    assert.ok(afterCall.prompt.includes("docs/plans/test.md"));
  }));

  it("forEach: expression function call", withSubflowDir(async (dir) => {
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
        type: "subflow", flow: "child", forEach: "getItems()",
        transitions: {
          all_complete: { done: "completed" },
          some_failed: { done: "failed" },
          all_failed: { done: "failed" },
        },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "WORK": () => ({ text: `<AI_STEP_RESULT>done</AI_STEP_RESULT>`, ok: true }),
      },
      config: { moduleName: "test-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.expressionFuncs = { getItems: () => ["x", "y", "z"] };
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    // Should have called WORK 3 times (one per item)
    const workCalls = delegates.callLog.filter(c => c.stepName === "WORK");
    assert.equal(workCalls.length, 3);
  }));

  it("forEach: expression function returns empty → all_complete", withSubflowDir(async (dir) => {
    writeSubflow(dir, "child", {
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: { type: "agent", prompt: "work", transitions: { done: { done: "completed" } } },
      },
    });

    const flow = simpleFlow({
      START: {
        type: "subflow", flow: "child", forEach: "getItems()",
        transitions: { all_complete: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      config: { moduleName: "test-mod", projectRoot: "/tmp/test", subflowDir: dir },
    });
    delegates.expressionFuncs = { getItems: () => [] };
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    const workCalls = delegates.callLog.filter(c => c.stepName === "WORK");
    assert.equal(workCalls.length, 0);
  }));
});

describe("FlowEngine — universal forEach (non-subflow types)", () => {
  it("agent + forEach: runs agent once per item", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        forEach: "getItems()",
        prompt: "review {{forEachItem}}",
        resultTag: "R",
        transitions: {
          all_complete: { done: "completed" },
          some_failed: { done: "completed" },
          all_failed: { done: "failed" },
        },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        "START": () => ({ text: "<R>approved</R>", ok: true }),
      },
    });
    delegates.expressionFuncs = { getItems: () => ["plan-a", "plan-b", "plan-c"] };

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    const calls = delegates.callLog.filter(c => c.stepName === "START");
    assert.equal(calls.length, 3, "agent should be called 3 times");
    assert.ok(calls[0].prompt.includes("plan-a"), "first call should have plan-a");
    assert.ok(calls[1].prompt.includes("plan-b"), "second call should have plan-b");
  });

  it("agent + forEach: empty list → all_complete", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        forEach: "getItems()",
        prompt: "review {{forEachItem}}",
        resultTag: "R",
        transitions: {
          all_complete: { done: "completed" },
        },
      },
    });

    const delegates = makeMockDelegates({});
    delegates.expressionFuncs = { getItems: () => [] };

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    const calls = delegates.callLog.filter(c => c.stepName === "START");
    assert.equal(calls.length, 0, "agent should NOT be called for empty list");
  });

  it("forEachItem vars are cleaned up after forEach", async () => {
    const flow = {
      name: "cleanup-test", maxTotalSteps: 10, maxCycleVisits: 5, pingPongWindow: 6, entry: "ITER",
      steps: {
        ITER: {
          type: "agent",
          forEach: "getItems()",
          prompt: "work on {{forEachItem}}",
          resultTag: "R",
          transitions: { all_complete: { goto: "AFTER" } },
        },
        AFTER: {
          type: "agent",
          prompt: "forEachItem should be empty: [{{forEachItem}}]",
          resultTag: "R",
          transitions: { ok: { done: "completed" } },
        },
      },
    };

    const delegates = makeMockDelegates({
      responses: {
        "ITER": () => ({ text: "<R>approved</R>", ok: true }),
        "AFTER": () => ({ text: "<R>ok</R>", ok: true }),
      },
    });
    delegates.expressionFuncs = { getItems: () => ["x"] };

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    const afterCall = delegates.callLog.find(c => c.stepName === "AFTER");
    assert.ok(afterCall.prompt.includes("[{{forEachItem}}]"), "forEachItem should NOT be resolved in AFTER");
  });
});

describe("Flow loading priority (project flows override built-in)", () => {
  it("loadSubFlow: project missions/flows/ takes priority over built-in", async () => {
    const tmpDir = mkdtempSync(join(tmpdir(), "flow-priority-"));
    const projectFlowsDir = resolve(tmpDir, "missions", "flows");
    mkdirSync(projectFlowsDir, { recursive: true });

    // Write a custom subflow in project's missions/flows/
    writeFileSync(resolve(projectFlowsDir, "child.json"), JSON.stringify({
      name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
        WORK: { type: "agent", prompt: "PROJECT OVERRIDE: {{forEachItem}}", transitions: { done: { done: "completed" } } },
      },
    }));

    const flow = simpleFlow({
      START: {
        type: "subflow", flow: "child",
        transitions: { complete: { done: "completed" }, failed: { done: "failed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { "WORK": () => ({ text: "<AI_STEP_RESULT>done</AI_STEP_RESULT>", ok: true }) },
      config: { projectRoot: tmpDir, missionsDir: resolve(tmpDir, "missions") },
    });
    delegates.loadSubFlow = (await import("../src/flow-loader.js")).loadSubFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    const workCall = delegates.callLog.find(c => c.stepName === "WORK");
    assert.ok(workCall.prompt.includes("PROJECT OVERRIDE"), "should load from project missions/flows/");

    rmSync(tmpDir, { recursive: true, force: true });
  });

  it("createMissionDriverFlow: loads custom flowName from project dir", async () => {
    const { createMissionDriverFlow } = await import("../src/flow-loader.js");
    const tmpDir = mkdtempSync(join(tmpdir(), "custom-flow-"));
    const projectFlowsDir = resolve(tmpDir, "missions", "flows");
    mkdirSync(projectFlowsDir, { recursive: true });

    writeFileSync(resolve(projectFlowsDir, "my-custom.json"), JSON.stringify({
      name: "my-custom", entry: "START", maxTotalSteps: 5, steps: {
        START: { type: "agent", prompt: "custom", transitions: { ok: { done: "completed" } } },
      },
    }));

    const flow = createMissionDriverFlow({
      flowName: "my-custom",
      projectFlowsDir,
    });
    assert.equal(flow.name, "my-custom");

    rmSync(tmpDir, { recursive: true, force: true });
  });

  it("createMissionDriverFlow: falls back to built-in when project file missing", async () => {
    const { createMissionDriverFlow } = await import("../src/flow-loader.js");
    const tmpDir = mkdtempSync(join(tmpdir(), "fallback-flow-"));

    // No custom file — should load built-in mission-driver.json
    const flow = createMissionDriverFlow({
      flowName: "mission-driver",
      projectFlowsDir: resolve(tmpDir, "missions", "flows"),
    });
    assert.equal(flow.name, "mission-driver");

    rmSync(tmpDir, { recursive: true, force: true });
  });
});
