import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createMissionDriverFlow } from "../src/flow-loader.js";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates } from "./helpers.js";

// mdr-fix-3: CHECK is a configurable deterministic-state gate. When
// commands.check is configured, a failing-but-auto-fixable check emits the
// `needs_fix` marker, which retries CHECK (maxRetries:2) before the step-level
// onMaxRetries:{done:"failed"} terminates. The unconfigured path keeps the
// OPT-4 guarantee pinned in check-lightweight.test.js: `fail` is terminal with
// no retry. These tests prove the needs_fix retry path against the real built-in
// mission-driver.json and confirm the unconfigured terminal guarantee still
// holds alongside it (no repair death-loop reintroduced).

describe("mdr-fix-3 CHECK configurable gate — flow shape (built-in mission-driver.json)", () => {
  function checkStep() {
    const flow = createMissionDriverFlow({ flowName: "mission-driver" });
    assert.ok(flow.steps && flow.steps.CHECK, "built-in flow must define a CHECK step");
    return flow.steps.CHECK;
  }

  it("CHECK exposes needs_fix → retry CHECK maxRetries:2 (configurable auto-fix path)", () => {
    const step = checkStep();
    assert.deepEqual(
      step.transitions.needs_fix,
      { retry: "CHECK", maxRetries: 2 },
      "needs_fix must retry CHECK up to maxRetries:2",
    );
  });

  it("CHECK.fail stays terminal alongside needs_fix (OPT-4 unconfigured guarantee preserved)", () => {
    const step = checkStep();
    const fail = step.transitions.fail;
    assert.equal(fail.retry, undefined, "fail must NOT retry (unconfigured path stays terminal)");
    assert.equal(fail.goto, undefined, "fail must NOT goto another step");
    assert.equal(fail.done, "failed", "fail must terminate the run as failed");
    assert.deepEqual(
      step.onMaxRetries,
      { done: "failed" },
      "onMaxRetries must stay terminal for the exhausted needs_fix retry path",
    );
  });
});

describe("mdr-fix-3 CHECK configurable gate — engine behavior (needs_fix retry)", () => {
  it("needs_fix once then pass reaches REVIEW_PLANS within maxRetries", async () => {
    const flow = createMissionDriverFlow({ flowName: "mission-driver" });
    // Cap the (by-design infinite) main loop right after REVIEW_PLANS is
    // reached: CHECK(needs_fix) → CHECK(pass) → REVIEW_PLANS = 3 step visits.
    flow.maxTotalSteps = 3;

    let checkCalls = 0;
    let reviewPlansCalls = 0;
    const delegates = makeMockDelegates({
      async runAgent(stepName) {
        if (stepName === "CHECK") {
          checkCalls++;
          // First attempt: check failed but auto-fixable → needs_fix.
          // Retry: fix applied, re-run succeeds → pass.
          return {
            text: checkCalls === 1
              ? "<AI_STEP_RESULT>needs_fix</AI_STEP_RESULT>"
              : "<AI_STEP_RESULT>pass</AI_STEP_RESULT>",
            ok: true,
          };
        }
        if (stepName === "REVIEW_PLANS") {
          reviewPlansCalls++;
          return { text: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>", ok: true };
        }
        return { text: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>", ok: true };
      },
      config: { projectRoot: process.cwd() },
      expressionFuncs: {
        draftPlans: () => ["plan-1.md"],
        activePlans: () => [],
        openAudits: () => [],
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(checkCalls, 2, "CHECK must be invoked twice (needs_fix retry then pass)");
    assert.equal(reviewPlansCalls, 1, "after needs_fix→pass, control must reach REVIEW_PLANS");
    assert.notEqual(result.status, "failed", "run must not end failed at CHECK (pass transition taken)");
  });

  it("needs_fix past maxRetries ends the run failed via onMaxRetries (terminal)", async () => {
    const flow = createMissionDriverFlow({ flowName: "mission-driver" });

    let checkCalls = 0;
    const delegates = makeMockDelegates({
      async runAgent(stepName) {
        if (stepName === "CHECK") {
          checkCalls++;
          return { text: "<AI_STEP_RESULT>needs_fix</AI_STEP_RESULT>", ok: true };
        }
        return { text: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>", ok: true };
      },
      config: { projectRoot: process.cwd() },
      expressionFuncs: {
        draftPlans: () => [],
        activePlans: () => [],
        openAudits: () => [],
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    // maxRetries:2 ⇒ 1 initial + 2 retries = 3 CHECK invocations, then
    // onMaxRetries:{done:"failed"} terminates (no 4th invocation, no death-loop).
    assert.equal(
      checkCalls, 3,
      "CHECK must be invoked exactly 3 times (1 + maxRetries:2) before onMaxRetries",
    );
    assert.equal(
      result.status, "failed",
      "exhausted needs_fix retries must terminate the run as failed",
    );
  });
});
