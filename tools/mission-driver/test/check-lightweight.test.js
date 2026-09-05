import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createMissionDriverFlow } from "../src/flow-loader.js";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates } from "./helpers.js";

// OPT-4 (Phase 1): CHECK was demoted to a lightweight git-status gate.
// Strategy A+C: CHECK no longer runs the full build/test/repair loop, and its
// fail/onError transitions must be terminal (done:"failed") — never retry-CHECK,
// which previously allowed up to 3× the (formerly heavy) CHECK to re-run as a
// repair death-loop. These tests pin that contract against the real built-in
// mission-driver.json so a regression (re-adding `retry: CHECK`) is caught.

describe("OPT-4 CHECK lightweight — flow transitions (built-in mission-driver.json)", () => {
  function checkStep() {
    const flow = createMissionDriverFlow({ flowName: "mission-driver" });
    assert.ok(flow.steps && flow.steps.CHECK, "built-in flow must define a CHECK step");
    return flow.steps.CHECK;
  }

  it("CHECK.pass → REVIEW_PLANS (unchanged, sanity)", () => {
    const step = checkStep();
    assert.deepEqual(step.transitions.pass, { goto: "REVIEW_PLANS" });
  });

  it("CHECK.fail is terminal done:failed (no retry-CHECK death-loop)", () => {
    const step = checkStep();
    const fail = step.transitions.fail;
    assert.equal(fail.retry, undefined, "fail must NOT retry-CHECK");
    assert.equal(fail.goto, undefined, "fail must NOT goto another step");
    assert.equal(fail.done, "failed", "fail must terminate the run as failed");
  });

  it("CHECK.onError is terminal done:failed (no retry-CHECK on subprocess error)", () => {
    const step = checkStep();
    const onError = step.onError;
    assert.ok(onError, "CHECK must define onError");
    assert.equal(onError.retry, undefined, "onError must NOT retry-CHECK");
    assert.equal(onError.goto, undefined, "onError must NOT goto another step");
    assert.equal(onError.done, "failed", "onError must terminate the run as failed");
  });

  it("CHECK.onMaxRetries is terminal done:failed (consistent with fail/onError)", () => {
    const step = checkStep();
    assert.deepEqual(step.onMaxRetries, { done: "failed" });
  });
});

// Behavioral proof: run a real engine with the built-in flow and confirm a
// failing CHECK aborts the mission exactly once (CHECK is invoked a single
// time, run ends "failed"), rather than retrying CHECK up to maxRetries.
describe("OPT-4 CHECK lightweight — engine behavior (fail aborts, no retry)", () => {
  it("CHECK marker=fail ends the run as 'failed' with CHECK invoked exactly once", async () => {
    const flow = createMissionDriverFlow({ flowName: "mission-driver" });
    let checkCalls = 0;

    const delegates = makeMockDelegates({
      async runAgent(stepName) {
        if (stepName === "CHECK") {
          checkCalls++;
          return { text: "<AI_STEP_RESULT>fail</AI_STEP_RESULT>", ok: true };
        }
        return { text: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>", ok: true };
      },
      config: { projectRoot: process.cwd() },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed", "CHECK fail must terminate the run as failed");
    assert.equal(checkCalls, 1, "CHECK must NOT be retried after a fail marker");
  });

  it("CHECK subprocess error (ok=false) ends the run as 'failed' without retry", async () => {
    const flow = createMissionDriverFlow({ flowName: "mission-driver" });
    let checkCalls = 0;

    const delegates = makeMockDelegates({
      async runAgent(stepName) {
        if (stepName === "CHECK") {
          checkCalls++;
          return { text: "", ok: false };
        }
        return { text: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>", ok: true };
      },
      config: { projectRoot: process.cwd() },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed", "CHECK subprocess error must terminate as failed");
    assert.equal(checkCalls, 1, "CHECK must NOT be retried after a subprocess error");
  });
});
