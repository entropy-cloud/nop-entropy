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
