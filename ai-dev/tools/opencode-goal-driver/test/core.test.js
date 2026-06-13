import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow, mockSubFlows } from "./helpers.js";

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

describe("FlowEngine — ping-pong detection", () => {
  it("detects A↔B ping-pong and returns ping_pong status", async () => {
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
    flow.pingPongWindow = 6;

    const delegates = makeMockDelegates({
      responses: {
        A: "<R>next</R>",
        B: "<R>next</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "ping_pong");
    assert.ok(result.stepCount >= 6, `expected >= 6 steps, got ${result.stepCount}`);
  });

  it("does not trigger on linear revisits", async () => {
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
        transitions: { next: { goto: "C" } },
      },
      C: {
        type: "agent",
        prompt: "c",
        resultTag: "R",
        transitions: { next: { goto: "A" } },
      },
    }, "A");
    flow.pingPongWindow = 6;
    flow.maxTotalSteps = 10;
    flow.maxCycleVisits = 5;

    const delegates = makeMockDelegates({
      responses: {
        A: "<R>next</R>",
        B: "<R>next</R>",
        C: "<R>next</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "max_total_steps");
  });

  it("skips ping-pong when B→A is retry with maxRetries", async () => {
    const flow = simpleFlow({
      DRAFT: {
        type: "agent",
        prompt: "draft",
        resultTag: "R",
        transitions: { created: { goto: "AUDIT" } },
      },
      AUDIT: {
        type: "agent",
        prompt: "audit",
        resultTag: "R",
        transitions: {
          approved: { done: "completed" },
          issues: { retry: "DRAFT", maxRetries: 5 },
        },
      },
    }, "DRAFT");
    flow.pingPongWindow = 6;
    flow.maxTotalSteps = 20;
    flow.maxCycleVisits = 10;

    let auditCount = 0;
    const delegates = makeMockDelegates({
      responses: {
        DRAFT: "<R>created</R>",
        AUDIT: () => {
          auditCount++;
          return { text: `<R>${auditCount < 4 ? "issues" : "approved"}</R>`, ok: true };
        },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(result.stepCount >= 8, `expected >= 8 steps, got ${result.stepCount}`);
  });
});
