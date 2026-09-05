import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, readFileSync, existsSync, writeFileSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { FlowEngine } from "../src/engine.js";
import { summarizeArg } from "../src/executor.js";
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
        prompt: "build package {{packageFilter}} in {{projectRoot}}",
        resultTag: "R",
        transitions: { ok: { goto: "TOOL_STEP" } },
      },
      TOOL_STEP: {
        type: "tool",
        command: "pnpm --filter {{packageFilter}} typecheck",
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

    assert.ok(agentPrompt.includes("build package @nop-chaos/test-mod in /tmp/test"));
    assert.ok(!agentPrompt.includes("{{packageFilter}}"));
    assert.ok(toolCommand.includes("pnpm --filter @nop-chaos/test-mod typecheck"));
    assert.ok(!toolCommand.includes("{{packageFilter}}"));
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

describe("FlowEngine — flowVars extraction takes LAST block", () => {
  it("returns the real value when a prompt-example <FLOW_VARS> block precedes the AI output", () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "x",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });
    const engine = new FlowEngine(flow, makeMockDelegates({ responses: {} }));

    const text = [
      "# cmd: opencode run Draft a plan. Example output format:",
      "<AI_STEP_RESULT>created</AI_STEP_RESULT>",
      "<FLOW_VARS>",
      "  <PLAN_FILE>/path/to/plan.md</PLAN_FILE>",
      "</FLOW_VARS>",
      "--- AI real response ---",
      "<AI_STEP_RESULT>created</AI_STEP_RESULT>",
      "<FLOW_VARS>",
      "  <PLAN_FILE>docs/plans/160-real.md</PLAN_FILE>",
      "</FLOW_VARS>",
    ].join("\n");

    const vars = engine._extractFlowVars(text);
    assert.equal(vars.PLAN_FILE, "docs/plans/160-real.md");
  });

  it("returns empty when no <FLOW_VARS> block is present", () => {
    const flow = simpleFlow({
      START: { type: "agent", prompt: "x", resultTag: "R", transitions: { ok: { done: "completed" } } },
    });
    const engine = new FlowEngine(flow, makeMockDelegates({ responses: {} }));
    assert.deepEqual(engine._extractFlowVars("no vars here"), {});
  });
});

describe("FlowEngine — when condition", () => {
  it("runs step normally when when is absent", async () => {
    const flow = simpleFlow({
      START: { type: "agent", prompt: "ok", resultTag: "R", transitions: { ok: { done: "completed" } } },
    });
    const engine = new FlowEngine(flow, makeMockDelegates({ responses: { "START": { text: "<R>ok</R>", ok: true } } }));
    const result = await engine.run("START");
    assert.equal(result.status, "completed");
  });

  it("runs step when condition is true (var present)", async () => {
    const flow = {
      name: "when-test", maxTotalSteps: 10, maxCycleVisits: 5, pingPongWindow: 6, entry: "START",
      steps: {
        START: {
          when: { var: "myVar", present: true },
          type: "agent", prompt: "run", resultTag: "R",
          transitions: { ok: { done: "completed" } },
        },
      },
    };
    const del = makeMockDelegates({ responses: { START: { text: "<R>ok</R>", ok: true } } });
    del.vars.myVar = "configured";
    const engine = new FlowEngine(flow, del);
    const result = await engine.run("START");
    assert.equal(result.status, "completed");
  });

  it("skips step when condition is false (var empty), follows otherwise", async () => {
    const flow = {
      name: "when-skip", maxTotalSteps: 10, maxCycleVisits: 5, pingPongWindow: 6, entry: "START",
      steps: {
        START: {
          when: { var: "myVar", present: true },
          otherwise: { done: "completed" },
          type: "agent", prompt: "should-not-run", resultTag: "R",
          transitions: { ok: { done: "failed" } },
        },
      },
    };
    const del = makeMockDelegates({ responses: {} });
    del.vars.myVar = "";
    const engine = new FlowEngine(flow, del);
    const result = await engine.run("START");
    assert.equal(result.status, "completed");
  });

  it("skips step when condition is false, follows otherwise.goto", async () => {
    const flow = {
      name: "when-goto", maxTotalSteps: 10, maxCycleVisits: 5, pingPongWindow: 6, entry: "FIRST",
      steps: {
        FIRST: {
          when: { var: "x", present: true },
          otherwise: { goto: "FALLBACK" },
          type: "agent", prompt: "should-not-run", resultTag: "R",
          transitions: { ok: { done: "failed" } },
        },
        FALLBACK: {
          type: "agent", prompt: "fallback", resultTag: "R",
          transitions: { ok: { done: "completed" } },
        },
      },
    };
    const del = makeMockDelegates({ responses: { FALLBACK: { text: "<R>ok</R>", ok: true } } });
    del.vars.x = "";
    const engine = new FlowEngine(flow, del);
    const result = await engine.run("FIRST");
    assert.equal(result.status, "completed");
  });

  it("defaults otherwise to done:completed when not specified", async () => {
    const flow = {
      name: "when-default", maxTotalSteps: 10, maxCycleVisits: 5, pingPongWindow: 6, entry: "START",
      steps: {
        START: {
          when: { var: "x", present: true },
          type: "agent", prompt: "should-not-run", resultTag: "R",
          transitions: { ok: { done: "failed" } },
        },
      },
    };
    const del = makeMockDelegates({ responses: {} });
    del.vars.x = "";
    const engine = new FlowEngine(flow, del);
    const result = await engine.run("START");
    assert.equal(result.status, "completed");
  });

  it("supports eq/ne operators in when condition", async () => {
    const flow = {
      name: "when-eq", maxTotalSteps: 10, maxCycleVisits: 5, pingPongWindow: 6, entry: "START",
      steps: {
        START: {
          when: { var: "color", eq: "red" },
          type: "agent", prompt: "run", resultTag: "R",
          transitions: { ok: { done: "completed" } },
        },
      },
    };
    const del = makeMockDelegates({ responses: { START: { text: "<R>ok</R>", ok: true } } });
    del.vars.color = "red";
    const engine = new FlowEngine(flow, del);
    const result = await engine.run("START");
    assert.equal(result.status, "completed");
  });

  it("skips when eq does not match", async () => {
    const flow = {
      name: "when-eq-skip", maxTotalSteps: 10, maxCycleVisits: 5, pingPongWindow: 6, entry: "START",
      steps: {
        START: {
          when: { var: "color", eq: "red" },
          otherwise: { done: "completed" },
          type: "agent", prompt: "should-not-run", resultTag: "R",
          transitions: { ok: { done: "failed" } },
        },
      },
    };
    const del = makeMockDelegates({ responses: {} });
    del.vars.color = "blue";
    const engine = new FlowEngine(flow, del);
    const result = await engine.run("START");
    assert.equal(result.status, "completed");
  });
});

describe("FlowEngine — string expression when conditions", () => {
  it("evaluates string expression when condition (true)", async () => {
    const flow = {
      name: "expr-when", maxTotalSteps: 10, maxCycleVisits: 5, pingPongWindow: 6, entry: "START",
      steps: {
        START: {
          when: "myVar != ''",
          type: "agent", prompt: "run", resultTag: "R",
          transitions: { ok: { done: "completed" } },
        },
      },
    };
    const del = makeMockDelegates({ responses: { START: { text: "<R>ok</R>", ok: true } } });
    del.vars.myVar = "configured";
    const engine = new FlowEngine(flow, del);
    const result = await engine.run("START");
    assert.equal(result.status, "completed");
  });

  it("evaluates string expression when condition (false, skips)", async () => {
    const flow = {
      name: "expr-when-skip", maxTotalSteps: 10, maxCycleVisits: 5, pingPongWindow: 6, entry: "START",
      steps: {
        START: {
          when: "myVar != ''",
          otherwise: { done: "completed" },
          type: "agent", prompt: "should-not-run", resultTag: "R",
          transitions: { ok: { done: "failed" } },
        },
      },
    };
    const del = makeMockDelegates({ responses: {} });
    del.vars.myVar = "";
    const engine = new FlowEngine(flow, del);
    const result = await engine.run("START");
    assert.equal(result.status, "completed");
  });

  it("supports function calls in when expressions", async () => {
    const flow = {
      name: "expr-func", maxTotalSteps: 10, maxCycleVisits: 5, pingPongWindow: 6, entry: "START",
      steps: {
        START: {
          when: "myFunc().length > 0",
          otherwise: { done: "completed" },
          type: "agent", prompt: "run", resultTag: "R",
          transitions: { ok: { done: "completed" } },
        },
      },
    };
    const del = makeMockDelegates({ responses: { START: { text: "<R>ok</R>", ok: true } } });
    del.expressionFuncs = { myFunc: () => ["a", "b"] };
    const engine = new FlowEngine(flow, del);
    const result = await engine.run("START");
    assert.equal(result.status, "completed");
  });

  it("skips when function returns empty array", async () => {
    const flow = {
      name: "expr-func-empty", maxTotalSteps: 10, maxCycleVisits: 5, pingPongWindow: 6, entry: "START",
      steps: {
        START: {
          when: "myFunc().length > 0",
          otherwise: { done: "completed" },
          type: "agent", prompt: "should-not-run", resultTag: "R",
          transitions: { ok: { done: "failed" } },
        },
      },
    };
    const del = makeMockDelegates({ responses: {} });
    del.expressionFuncs = { myFunc: () => [] };
    const engine = new FlowEngine(flow, del);
    const result = await engine.run("START");
    assert.equal(result.status, "completed");
  });
});

describe("summarizeArg — keeps prompt out of log header", () => {
  it("passes short single-line args through unchanged", () => {
    assert.equal(summarizeArg("run"), "run");
    assert.equal(summarizeArg("-m"), "-m");
  });

  it("truncates long args and reports total length", () => {
    const longPrompt = "Draft a development plan for package flux-runtime.\nOutput:\n<FLOW_VARS>\n  <PLAN_FILE>docs/plans/x.md</PLAN_FILE>\n</FLOW_VARS>";
    const out = summarizeArg(longPrompt);
    assert.ok(out.length < longPrompt.length, "should be shorter than input");
    assert.ok(out.endsWith(`...(${longPrompt.length} chars)`));
    assert.ok(!out.includes("docs/plans/x.md"), "must not leak placeholder example token");
    assert.ok(!out.includes("\n"), "must be single line");
  });

  it("truncates args that exceed 80 chars even without newlines", () => {
    const arg = "x".repeat(200);
    const out = summarizeArg(arg);
    assert.ok(out.endsWith("...(200 chars)"));
    assert.ok(out.length < 200);
  });
});

describe("FlowEngine — run-state.json separation (Phase 1)", () => {
  it("writes run-state.json with missionName/runId/status/steps into runDir", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-runstate-"));
    try {
      const flow = simpleFlow({
        START: {
          type: "agent", prompt: "go", resultTag: "R",
          transitions: { ok: { goto: "B" } },
        },
        B: {
          type: "agent", prompt: "b", resultTag: "R",
          transitions: { done: { done: "completed" } },
        },
      });

      const delegates = makeMockDelegates({
        responses: { START: "<R>ok</R>", B: "<R>done</R>" },
        config: {
          moduleName: "test-mod", shortName: "test-mod",
          packageFilter: "@nop-chaos/test-mod", projectRoot: runDir,
          runDir, missionName: "test-mission",
        },
      });

      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();
      assert.equal(result.status, "completed");

      const stateFile = join(runDir, "run-state.json");
      assert.ok(existsSync(stateFile), "run-state.json must be produced");
      const state = JSON.parse(readFileSync(stateFile, "utf8"));
      assert.equal(state.missionName, "test-mission");
      assert.equal(state.flowName, "test-flow");
      assert.equal(state.runId, runDir.split(/[\\/]/).pop());
      assert.equal(state.runDir, runDir);
      assert.equal(state.status, "completed");
      assert.ok(Array.isArray(state.steps) && state.steps.length === 2);
      assert.equal(state.steps[0].name, "START");
      assert.equal(state.steps[0].status, "completed");
      assert.equal(state.steps[1].name, "B");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("strips a residual workflow node from the mission JSON on startup", async () => {
    const root = mkdtempSync(join(tmpdir(), "md-migrate-"));
    const missionsDir = join(root, "missions");
    mkdirSync(missionsDir, { recursive: true });
    const missionFile = join(missionsDir, "polluted-mission.json");
    writeFileSync(missionFile, JSON.stringify({
      name: "polluted-mission",
      description: "has stale workflow",
      workflow: { status: "running", steps: [{ name: "OLD" }] },
      otherConfig: true,
    }, null, 2));
    try {
      const runDir = mkdtempSync(join(tmpdir(), "md-migrate-run-"));
      try {
        const flow = simpleFlow({
          START: {
            type: "agent", prompt: "go", resultTag: "R",
            transitions: { ok: { done: "completed" } },
          },
        });
        const delegates = makeMockDelegates({
          responses: { START: "<R>ok</R>" },
          config: {
            moduleName: "test-mod", shortName: "test-mod",
            packageFilter: "@nop-chaos/test-mod", projectRoot: root,
            runDir, missionName: "polluted-mission", missionsDir,
          },
        });
        const engine = new FlowEngine(flow, delegates);
        await engine.run();

        const after = JSON.parse(readFileSync(missionFile, "utf8"));
        assert.equal(after.workflow, undefined, "mission JSON must no longer carry a workflow node");
        assert.equal(after.otherConfig, true, "non-workflow config must be preserved");
        assert.equal(after.name, "polluted-mission");
      } finally {
        rmSync(runDir, { recursive: true, force: true });
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("does not write run-state.json when runDir is absent (no regression)", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });
    const delegates = makeMockDelegates({ responses: { START: "<R>ok</R>" } });
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    assert.equal(engine.delegates.config.runDir, undefined);
  });
});

describe("FlowEngine — events.jsonl stream (Phase 2)", () => {
  function readEvents(runDir) {
    const raw = readFileSync(join(runDir, "events.jsonl"), "utf8");
    return raw.split("\n").filter(Boolean).map((line) => JSON.parse(line));
  }

  it("emits run_started / step_started / step_completed / run_completed into events.jsonl", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-events-"));
    try {
      const flow = simpleFlow({
        START: {
          type: "agent", prompt: "go", resultTag: "R",
          transitions: { ok: { goto: "B" } },
        },
        B: {
          type: "agent", prompt: "b", resultTag: "R",
          transitions: { done: { done: "completed" } },
        },
      });
      const delegates = makeMockDelegates({
        responses: { START: "<R>ok</R>", B: "<R>done</R>" },
        config: {
          moduleName: "test-mod", shortName: "test-mod",
          packageFilter: "@nop-chaos/test-mod", projectRoot: runDir,
          runDir, missionName: "evt-mission",
        },
      });

      const engine = new FlowEngine(flow, delegates);
      await engine.run();

      const events = readEvents(runDir);
      assert.ok(events.length >= 6, `expected >=6 events, got ${events.length}`);

      const types = events.map((e) => e.type);
      assert.ok(types.includes("run_started"), "missing run_started");
      assert.ok(types.includes("run_completed"), "missing run_completed");
      assert.ok(types.filter((t) => t === "step_started").length >= 2, "missing step_started");
      assert.ok(types.filter((t) => t === "step_completed").length >= 2, "missing step_completed");

      // Common fields on every event
      for (const e of events) {
        assert.equal(typeof e.ts, "string");
        assert.equal(e.missionName, "evt-mission");
        assert.equal(e.runId, runDir.split(/[\\/]/).pop());
        assert.equal(e.flowName, "test-flow");
      }

      // run_started business fields
      const started = events.find((e) => e.type === "run_started");
      assert.equal(started.flowName, "test-flow");
      assert.equal(started.runDir, runDir);
      assert.equal(typeof started.maxTotalSteps, "number");
      assert.equal(typeof started.maxCycleVisits, "number");

      // run_completed business fields
      const completed = events.find((e) => e.type === "run_completed");
      assert.equal(completed.status, "completed");
      assert.equal(completed.stepCount, 2);

      // step_completed carries produced array + durationMs
      const stepDone = events.find((e) => e.type === "step_completed" && e.step === "B");
      assert.ok(Array.isArray(stepDone.produced));
      assert.equal(typeof stepDone.durationMs, "number");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("emits transition (via goto) and limit_hit (max_cycles) for branching/limit paths", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-events-limit-"));
    try {
      const flow = simpleFlow({
        A: {
          type: "agent", prompt: "loop", resultTag: "R",
          transitions: { again: { goto: "A" } },
        },
      }, "A");
      flow.maxCycleVisits = 3;

      const delegates = makeMockDelegates({
        responses: { A: "<R>again</R>" },
        config: {
          moduleName: "test-mod", shortName: "test-mod",
          packageFilter: "@nop-chaos/test-mod", projectRoot: runDir,
          runDir, missionName: "limit-mission",
        },
      });

      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();
      assert.equal(result.status, "max_cycles");

      const events = readEvents(runDir);
      const types = events.map((e) => e.type);
      const gotoTrans = events.find((e) => e.type === "transition" && e.via === "goto");
      assert.ok(gotoTrans, "missing transition via:goto");
      assert.equal(gotoTrans.from, "A");
      assert.equal(gotoTrans.to, "A");

      const limit = events.find((e) => e.type === "limit_hit");
      assert.ok(limit, "missing limit_hit");
      assert.equal(limit.limitType, "max_cycles");
      assert.equal(limit.max, 3);
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("does not produce events.jsonl when runDir is absent (no regression)", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });
    const engine = new FlowEngine(flow, makeMockDelegates({ responses: { START: "<R>ok</R>" } }));
    engine._emitEvent("test", { foo: "bar" });
    // No throw, no file — _emitEvent is a guarded no-op without eventsFile.
    assert.equal(engine.eventsFile, undefined);
  });
});


