// WI2 — `--step <STEP>` single-step mode (Plan mdo-step-audit-2).
// Pins design §4.3.1: the engine-level `maxSteps = 1` hard cap (set via
// `cfg.singleStep`) replaces the old main.js transition-rewrite hack. Every
// exit class — `transitions[*]`, `onError`, `onUnknown`, `onMaxRetries` — is
// physically capped at exactly one executed step, and the run terminates with
// status `single_step_done` (mapped to exit code 0 in main.js exitMap).
//
// Engine behavior asserted here (the "four exits"):
//   1. transitions正常出口 — agent emits a marker whose transition is a goto.
//   2. onError出口        — agent returns ok=false, triggering onError.goto.
//   3. onUnknown出口      — agent emits a marker not in transitions (and not
//                            aliasable), triggering onUnknown.goto.
//   4. onMaxRetries出口   — step is configured to retry on a marker; the retry
//                            target would re-execute, but the cap fires first.
//
// Immutability invariant: in every case the flow object (steps + transitions +
// onError/onUnknown/onMaxRetries) MUST be byte-identical before vs after run.
// This is the core guarantee the old in-place mutation violated.

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

// Deep-clone a flow snapshot for before/after comparison. JSON round-trip is
// sufficient — flow JSON has no functions or undefined leaves at the top level.
function snapshot(flow) {
  return JSON.parse(JSON.stringify(flow));
}

// Build a single-step-configured delegates and engine pair. singleStep is set
// on `delegates.config` AFTER construction (same pattern skip-steps.test.js
// uses for effectiveSkip) because cfg is read inside run(), not in the ctor.
function makeSingleStepEngine(flow, responses) {
  const delegates = makeMockDelegates({ responses });
  delegates.config.singleStep = true;
  const engine = new FlowEngine(flow, delegates);
  engine.eventsFile = null; // _emitEvent becomes a no-op without eventsFile
  return { engine, delegates };
}

describe("WI2 single-step mode (Plan mdo-step-audit-2) — four exits all cap at 1 step", () => {
  it("Case 1 (transitions正常出口): goto marker emitted → single_step_done, goto target NOT reached, flow immutable", async () => {
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        transitions: {
          pass: { goto: "WORK" },   // happy-path goto — old mutation rewrote this
          fail: { done: "failed" },
        },
        onError: { done: "failed" },
      },
      WORK: {
        type: "agent",
        prompt: "work",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "CHECK");

    const before = snapshot(flow);
    const { engine, delegates } = makeSingleStepEngine(flow, {
      // CHECK emits `pass` → transition is goto WORK. Single-step must still
      // stop at CHECK and never invoke WORK.
      CHECK: { text: "<R>pass</R>", ok: true },
      WORK: () => { throw new Error("WORK must NOT be reached in single-step mode"); },
    });

    const result = await engine.run("CHECK");

    assert.equal(result.status, "single_step_done",
      "transitions goto exit must terminate as single_step_done");
    assert.equal(result.stepCount, 1,
      "exactly one step executed (the entry step only)");
    assert.equal(delegates.callLog.filter((c) => c.stepName === "CHECK").length, 1,
      "CHECK agent invoked exactly once");
    assert.equal(delegates.callLog.filter((c) => c.stepName === "WORK").length, 0,
      "WORK (the goto target) must NOT be invoked");
    assert.deepEqual(snapshot(flow), before,
      "flow object (steps + transitions + onError) must be byte-identical before vs after run");
    // WI5 — _finalizeWorkflow maps single_step_done → step-level "completed"
    // (aligns with main.js exitMap exit code 0). Assert no "成功单步却标 failed".
    const checkStep = engine.workflow.steps.find((s) => s.name === "CHECK");
    assert.ok(checkStep, "workflow.steps must contain the CHECK step record");
    assert.equal(checkStep.status, "completed",
      "WI5: single_step_done terminal step record must read 'completed' (not 'failed')");
    assert.equal(engine.workflow.status, "single_step_done",
      "WI5: workflow-level status retains single_step_done (distinguishes from full completion)");
  });

  it("Case 2 (onError出口): ok=false triggers onError.goto → single_step_done, onError target NOT reached, flow immutable", async () => {
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        transitions: { pass: { goto: "WORK" } },
        // onError gotoes a different step — old mutation never touched this,
        // so single-step used to escape into WORK. The hard cap must stop it.
        onError: { goto: "FALLBACK" },
      },
      WORK: {
        type: "agent",
        prompt: "work",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
      FALLBACK: {
        type: "agent",
        prompt: "fallback",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "CHECK");

    const before = snapshot(flow);
    const { engine, delegates } = makeSingleStepEngine(flow, {
      // ok=false triggers the `result.ok` failure path → onError.goto FALLBACK.
      CHECK: { text: "", ok: false },
      FALLBACK: () => { throw new Error("FALLBACK (onError target) must NOT be reached in single-step mode"); },
      WORK: () => { throw new Error("WORK must NOT be reached in single-step mode"); },
    });

    const result = await engine.run("CHECK");

    assert.equal(result.status, "single_step_done",
      "onError exit must terminate as single_step_done");
    assert.equal(result.stepCount, 1,
      "exactly one step executed (the entry step only — onError goto is NOT a second step)");
    assert.equal(delegates.callLog.filter((c) => c.stepName === "CHECK").length, 1,
      "CHECK agent invoked exactly once");
    assert.equal(delegates.callLog.filter((c) => c.stepName === "FALLBACK").length, 0,
      "onError.goto target must NOT be invoked");
    assert.deepEqual(snapshot(flow), before,
      "flow object must be byte-identical before vs after run (no mutation of onError)");
  });

  it("Case 3 (onUnknown出口): unknown marker triggers onUnknown.goto → single_step_done, onUnknown target NOT reached, flow immutable", async () => {
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        // Only `pass` is defined. Any other marker → onUnknown.goto OTHER.
        // This is exactly the DRAFT_PLANS failure mode from design §2.1:
        // an unrecognized marker used to escape single-step into OTHER.
        transitions: { pass: { goto: "WORK" } },
        onUnknown: { goto: "OTHER" },
        onUnknownMaxRetries: 0, // skip correction retry — go straight to onUnknown
      },
      WORK: {
        type: "agent",
        prompt: "work",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
      OTHER: {
        type: "agent",
        prompt: "other",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "CHECK");

    const before = snapshot(flow);
    const { engine, delegates } = makeSingleStepEngine(flow, {
      // Emit a marker that has no transition and no alias → onUnknown path.
      CHECK: { text: "<R>mystery_marker</R>", ok: true },
      OTHER: () => { throw new Error("OTHER (onUnknown target) must NOT be reached in single-step mode"); },
      WORK: () => { throw new Error("WORK must NOT be reached in single-step mode"); },
    });

    const result = await engine.run("CHECK");

    assert.equal(result.status, "single_step_done",
      "onUnknown exit must terminate as single_step_done");
    assert.equal(result.stepCount, 1,
      "exactly one step executed (the entry step only — onUnknown goto is NOT a second step)");
    assert.equal(delegates.callLog.filter((c) => c.stepName === "CHECK").length, 1,
      "CHECK agent invoked exactly once");
    assert.equal(delegates.callLog.filter((c) => c.stepName === "OTHER").length, 0,
      "onUnknown.goto target must NOT be invoked");
    assert.deepEqual(snapshot(flow), before,
      "flow object must be byte-identical before vs after run (no mutation of onUnknown)");
  });

  it("Case 4 (onMaxRetries出口): step configured to retry → single_step_done caps at step 1 BEFORE retry can re-execute, flow immutable", async () => {
    // Engine semantics being pinned here (per plan note "以引擎实际行为为准"):
    // retry fires by setting `currentStep = retryTarget; continue;` which
    // re-evaluates the loop condition. In single-step mode the condition is
    // `totalSteps < 1`; after the first step runs totalSteps=1, so the loop
    // EXITS before the retry target can execute. The onMaxRetries branch is
    // therefore unreachable in single-step mode — the cap fires first.
    // (Transient-retry's visitCounts rollback at engine.js:1587 still cannot
    // re-enter because totalSteps is NOT rolled back, only visitCounts.)
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        transitions: {
          pass: { goto: "WORK" },
          // `issues` triggers a retry to CHECK itself. Old behaviour: this
          // would loop until maxRetries, then fire onMaxRetries. Single-step:
          // never gets a second attempt.
          issues: { retry: "CHECK", maxRetries: 3 },
        },
        onMaxRetries: { done: "failed" },
        onError: { done: "failed" },
      },
      WORK: {
        type: "agent",
        prompt: "work",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "CHECK");

    const before = snapshot(flow);
    const { engine, delegates } = makeSingleStepEngine(flow, {
      // Always emit `issues` so the retry path is taken — but the cap fires
      // before the second attempt, so CHECK runs exactly once.
      CHECK: { text: "<R>issues</R>", ok: true },
      WORK: () => { throw new Error("WORK must NOT be reached in single-step mode"); },
    });

    const result = await engine.run("CHECK");

    assert.equal(result.status, "single_step_done",
      "retry-configured step must still terminate as single_step_done at step 1; onMaxRetries is unreachable under single-step");
    assert.equal(result.stepCount, 1,
      "exactly one step executed — retry does NOT add a second step in single-step mode");
    assert.equal(delegates.callLog.filter((c) => c.stepName === "CHECK").length, 1,
      "CHECK agent invoked exactly once (retry target re-execution is blocked by the cap)");
    assert.deepEqual(snapshot(flow), before,
      "flow object must be byte-identical before vs after run (no mutation of transitions / onMaxRetries)");
  });
});

describe("WI2 single-step mode — regression: without singleStep the same flows run normally", () => {
  it("same flow without singleStep flag reaches the goto target (multi-step)", async () => {
    // Same shape as Case 1 above, but WITHOUT delegates.config.singleStep.
    // Proves maxSteps === Infinity when the flag is absent, so behaviour is
    // byte-identical to the pre-WI2 engine.
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        transitions: { pass: { goto: "WORK" } },
      },
      WORK: {
        type: "agent",
        prompt: "work",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "CHECK");

    const delegates = makeMockDelegates({
      responses: {
        CHECK: { text: "<R>pass</R>", ok: true },
        WORK: { text: "<R>ok</R>", ok: true },
      },
    });
    // NOTE: delegates.config.singleStep is deliberately NOT set.
    assert.equal(delegates.config.singleStep, undefined,
      "makeMockDelegates must not pre-set singleStep (would mask a default-Infinity regression)");

    const engine = new FlowEngine(flow, delegates);
    engine.eventsFile = null;
    const result = await engine.run("CHECK");

    assert.equal(result.status, "completed",
      "without singleStep the flow runs to normal completion");
    assert.equal(result.stepCount, 2,
      "both CHECK and WORK executed (no single-step cap)");
    assert.equal(delegates.callLog.filter((c) => c.stepName === "WORK").length, 1,
      "goto target reached when singleStep is off");
  });
});
