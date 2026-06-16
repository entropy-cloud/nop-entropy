import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow, mockSubFlows } from "./helpers.js";

const __dirname = dirname(fileURLToPath(import.meta.url));

function loadFlow(name) {
  const path = resolve(__dirname, "../flows", `${name}.json`);
  return JSON.parse(readFileSync(path, "utf8"));
}

function fullDelegates(subFlows, overrides = {}) {
  const overridesResponses = overrides.responses || {};
  const overridesRunScript = overrides.runScript;
  const mergedResponses = {
    CHECK: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
    ROADMAP: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>",
    DEEP_AUDIT: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
    ADVERSARIAL: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
    DRAFT_PLANS: "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS><PLAN_FILE>/tmp/test-plan.md</PLAN_FILE></FLOW_VARS>",
    "EXECUTE": "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
    CLOSURE_SCRIPT_CHECK: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
    CLOSURE_AUDIT: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>",
    BUILD_VERIFY: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
    ...overridesResponses,
  };
  const delegates = makeMockDelegates({
    subFlows,
    responses: mergedResponses,
    config: { moduleName: "test-mod", projectRoot: "/tmp/test" },
    vars: { module: "test-mod", projectRoot: "/tmp/test", PLAN_FILE: "ai-dev/plans/test-plan.md" },
    loadSubFlow: (name) => subFlows[name],
  });
  delegates.runScript = async (stepName, stepDef) => {
    if (overridesRunScript) {
      const result = await overridesRunScript(stepName, stepDef);
      if (result !== undefined) return result;
    }
    if (stepName === "PLANS.SCAN_PLANS") {
      return "empty";
    }
    return undefined;
  };
  return delegates;
}

function mockScriptStep(flow, stepName, fn) {
  flow.steps[stepName].run = fn;
}

// ═══════════════════════════════════════════════
// 1. Flow definition integrity
// ═══════════════════════════════════════════════
describe("goal-driver flow definition", () => {
  it("loads goal-driver.json without error", () => {
    const flow = loadFlow("goal-driver");
    assert.equal(flow.name, "goal-driver");
    assert.equal(flow.entry, "CHECK");
    assert.ok(flow.steps.CHECK);
  });

  it("loads plan-execution.json without error", () => {
    const flow = loadFlow("plan-execution");
    assert.equal(flow.name, "plan-execution");
    assert.equal(flow.entry, "EXECUTE");
  });

  it("loads deep-audit-loop.json without error", () => {
    const flow = loadFlow("deep-audit-loop");
    assert.equal(flow.name, "deep-audit-loop");
    assert.equal(flow.entry, "DEEP_AUDIT");
  });

  it("all flow files are structurally valid", () => {
    for (const name of ["goal-driver", "plan-execution", "deep-audit-loop"]) {
      const flow = loadFlow(name);
      assert.ok(flow.steps[flow.entry], `${name}: entry "${flow.entry}" not found`);
      for (const [sn, sd] of Object.entries(flow.steps)) {
        assert.ok(sd.type, `${name}/${sn}: missing type`);
        assert.ok(sd.transitions, `${name}/${sn}: missing transitions`);
        for (const [marker, t] of Object.entries(sd.transitions)) {
          if (t.goto) assert.ok(flow.steps[t.goto], `${name}/${sn}: goto "${t.goto}" not found`);
          if (t.retry) assert.ok(flow.steps[t.retry], `${name}/${sn}: retry "${t.retry}" not found`);
        }
      }
    }
  });
});

// ═══════════════════════════════════════════════
// 2. Subflow marker propagation
// ═══════════════════════════════════════════════
describe("goal-driver — subflow marker propagation", () => {
  it("EXECUTE_PLAN returns 'pass' when plan-execution completes", async () => {
    const flow = loadFlow("goal-driver");
    flow.maxTotalSteps = 30;
    flow.steps.AUDIT.transitions.clean = { done: "completed" };

    let scanCalls = 0;
    const d = fullDelegates(mockSubFlows(), {
      runScript: async (stepName, stepDef) => {
        if (stepName === "PLANS.SCAN_PLANS") {
          scanCalls++;
          return scanCalls <= 1
            ? { marker: "ok", vars: { items: '["/tmp/test-plan.md"]' }, text: "ok" }
            : { marker: "empty", vars: { items: "[]" }, text: "empty" };
        }
        return undefined;
      },
    });

    const engine = new FlowEngine(flow, d);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    const steps = d.callLog.filter(c => c.stepName === "EXECUTE");
    assert.ok(steps.length >= 1, "EXECUTE should be called in plan-execution subflow");
  });

  it("AUDIT returns 'clean' when deep-audit-loop completes", async () => {
    const flow = loadFlow("goal-driver");
    flow.maxTotalSteps = 30;
    flow.steps.AUDIT.transitions.clean = { done: "completed" };

    const d = fullDelegates(mockSubFlows(), {
      responses: {
        ROADMAP: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>",
      },
    });

    const engine = new FlowEngine(flow, d);
    const result = await engine.run();
    assert.equal(result.status, "completed");
  });

  it("_result() includes marker in child flow result", async () => {
    const childFlow = simpleFlow({
      WORK: { type: "agent", prompt: "work", transitions: { done: { done: "completed" } } },
    }, "WORK");

    const engine = new FlowEngine(childFlow, {
      ...makeMockDelegates({ responses: { WORK: "<AI_STEP_RESULT>done</AI_STEP_RESULT>" } }),
      config: { moduleName: "test", projectRoot: "/tmp" },
      vars: { module: "test", projectRoot: "/tmp" },
    });
    const result = await engine.run();
    assert.equal(result.status, "completed");
    assert.equal(result.marker, "done");
  });
});

// ═══════════════════════════════════════════════
// 3. Flow path coverage
// ═══════════════════════════════════════════════
describe("goal-driver — path coverage", () => {
  it("execute path: CHECK → PLANS → ROADMAP → complete → AUDIT → clean → done", async () => {
    const flow = loadFlow("goal-driver");
    flow.maxTotalSteps = 20;
    flow.steps.AUDIT.transitions.clean = { done: "completed" };

    let scanCalls = 0;
    const d = fullDelegates(mockSubFlows(), {
      responses: {
        ROADMAP: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>",
        DEEP_AUDIT: "<AI_STEP_RESULT>clean</AI_STEP_RESULT>",
      },
      runScript: async (stepName, stepDef) => {
        if (stepName === "PLANS.SCAN_PLANS") {
          scanCalls++;
          return scanCalls <= 1
            ? { marker: "ok", vars: { items: '["/tmp/test-plan.md"]' }, text: "ok" }
            : { marker: "empty", vars: { items: "[]" }, text: "empty" };
        }
        return undefined;
      },
    });
    const engine = new FlowEngine(flow, d);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    assert.equal(scanCalls, 1);
  });

  it("CHECK fails 3 times → done failed", async () => {
    const flow = loadFlow("goal-driver");
    flow.maxTotalSteps = 10;

    const d = fullDelegates(mockSubFlows(), {
      responses: { CHECK: "<AI_STEP_RESULT>fail</AI_STEP_RESULT>" },
    });
    const engine = new FlowEngine(flow, d);
    const result = await engine.run();
    assert.equal(result.status, "failed");
    const healthCalls = d.callLog.filter(c => c.stepName === "CHECK");
    assert.equal(healthCalls.length, 4); // 1 initial + 3 retries = 4
  });

  it("DRAFT(created) → PLANS → done (audit is internal to DRAFT)", async () => {
    const flow = loadFlow("goal-driver");
    flow.maxTotalSteps = 20;
    flow.steps.AUDIT.transitions.clean = { done: "completed" };
    let rmCalls = 0;

    const d = fullDelegates(mockSubFlows(), {
      responses: {
        ROADMAP: () => {
          rmCalls++;
          return rmCalls <= 1
            ? { text: "<AI_STEP_RESULT>pending</AI_STEP_RESULT>", ok: true }
            : { text: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>", ok: true };
        },
        DRAFT: "<AI_STEP_RESULT>created</AI_STEP_RESULT>",
      },
    });
    const engine = new FlowEngine(flow, d);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    // DRAFT should go straight to PLANS, no PLAN_AUDIT step
    assert.ok(!d.callLog.some(c => c.stepName === "PLAN_AUDIT"), "PLAN_AUDIT step should not exist");
  });

  it("AUDIT triggers cycle via PLANS → ROADMAP → ... → max_total_steps", async () => {
    const flow = loadFlow("goal-driver");
    flow.maxTotalSteps = 10;

    const d = fullDelegates(mockSubFlows(), {
      responses: {
        ROADMAP: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>",
      },
    });
    const engine = new FlowEngine(flow, d);
    const result = await engine.run();
    // cycles: PLANS → ROADMAP → AUDIT until maxTotalSteps
    assert.equal(result.status, "max_total_steps");
  });
});

// ═══════════════════════════════════════════════
// 4. Deep-audit-loop internal loop
// ═══════════════════════════════════════════════
describe("goal-driver — deep-audit-loop internal loop", () => {
  it("DEEP_AUDIT(issues) → ADVERSARIAL(issues) → DRAFT_PLANS → completed", async () => {
    const flow = loadFlow("deep-audit-loop");
    flow.maxTotalSteps = 30;

    let deepAuditCalls = 0;
    let draftCalls = 0;

    const d = fullDelegates(mockSubFlows(), {
      responses: {
        DEEP_AUDIT: () => {
          deepAuditCalls++;
          return { text: "<AI_STEP_RESULT>issues</AI_STEP_RESULT>", ok: true };
        },
        ADVERSARIAL: "<AI_STEP_RESULT>issues</AI_STEP_RESULT>",
        DRAFT_PLANS: () => {
          draftCalls++;
          return { text: "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<FLOW_VARS><PLAN_FILE>/tmp/test-plan.md</PLAN_FILE></FLOW_VARS>", ok: true };
        },
      },
    });
    const engine = new FlowEngine(flow, d);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    assert.equal(deepAuditCalls, 1, "should call DEEP_AUDIT once");
    assert.equal(draftCalls, 1, "should draft plans once via DRAFT_PLANS");
  });
});

// ═══════════════════════════════════════════════
// 5. Engine produces marker in result
// ═══════════════════════════════════════════════
describe("goal-driver — step result marker integrity", () => {
  it("agent step marker is preserved in engine result", async () => {
    const flow = simpleFlow({
      STEP: { type: "agent", prompt: "test", transitions: { ok: { done: "completed" } } },
    }, "STEP");

    const engine = new FlowEngine(flow, makeMockDelegates({
      responses: { STEP: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>" },
    }));
    const result = await engine.run();
    assert.equal(result.status, "completed");
    assert.equal(result.marker, "ok");
  });

  it("subflow step result has child's marker", async () => {
    const flow = simpleFlow({
      CHILD: { type: "subflow", flow: "child", transitions: { ok: { done: "completed" } } },
    }, "CHILD");
    const engine = new FlowEngine(flow, makeMockDelegates({
      responses: { WORK: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>" },
      subFlows: {
        child: {
          name: "child", entry: "WORK", steps: {
            WORK: { type: "agent", prompt: "work", transitions: { ok: { done: "completed" } } },
          },
        },
      },
    }));
    const result = await engine.run();
    assert.equal(result.status, "completed");
    assert.equal(result.marker, "ok");
  });
});
