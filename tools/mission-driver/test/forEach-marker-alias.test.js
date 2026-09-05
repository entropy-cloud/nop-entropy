import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createMissionDriverFlow } from "../src/flow-loader.js";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates } from "./helpers.js";

// mdr-fix-1: REVIEW_PLANS is a forEach agent step. Its per-plan review prompt
// (prompts/plan-review.md) emits a per-item `<AI_STEP_RESULT>approved</AI_STEP_RESULT>`
// marker, but the step's transitions are the AGGREGATE markers
// all_complete / some_failed / all_failed (derived from iterResult.ok counts in
// _executeForEach). Without an alias, the per-item `approved` marker hit no
// transition, fired the correction agent (up to onUnknownMaxRetries=2 parse-model
// calls per draft plan) for zero effect — the aggregate marker is computed solely
// from counts and never from the per-item marker value.
//
// Fix: alias `approved` → `all_complete` in flows/mission-driver.json. _tryAliasMarker
// resolves the per-item marker to a transition-valid value, so resolvedOk flips true
// (engine.js:855) and no correction runs. The alias is inert on every other main-flow
// step: _tryAliasMarker only returns it when transitions[all_complete] exists, and
// CHECK / DRAFT_PLANS / DEEP_AUDIT have no such transition.

describe("mdr-fix-1 — approved marker alias (built-in mission-driver.json)", () => {
  it("markerAliases maps `approved` → `all_complete`", () => {
    const flow = createMissionDriverFlow({ flowName: "mission-driver" });
    assert.equal(
      flow.markerAliases.approved, "all_complete",
      "built-in flow must alias the per-item `approved` marker to the forEach aggregate `all_complete`",
    );
  });

  it("REVIEW_PLANS per-item `approved` resolves to aggregate all_complete with NO correction (direct _executeForEach)", async () => {
    const flow = createMissionDriverFlow({ flowName: "mission-driver" });
    const delegates = makeMockDelegates({
      responses: {
        REVIEW_PLANS: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>",
      },
      expressionFuncs: {
        draftPlans: () => ["plan-1.md"],
        activePlans: () => [],
        openAudits: () => [],
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine._executeForEach("REVIEW_PLANS", flow.steps.REVIEW_PLANS);

    assert.equal(result.marker, "all_complete", "single approved item must aggregate to all_complete");
    assert.equal(result.ok, true);
    assert.equal(
      delegates.callLog.filter((c) => c.type === "parse").length, 0,
      "approved alias must short-circuit before the parse/correction fallback (no wasted model calls)",
    );
  });

  it("real engine run: REVIEW_PLANS aggregate all_complete routes to EXEC_PLANS, no correction invoked", async () => {
    const flow = createMissionDriverFlow({ flowName: "mission-driver" });
    // Start at REVIEW_PLANS and cap steps so the (by-design infinite) main loop
    // terminates right after proving the aggregate marker reaches EXEC_PLANS.
    // maxTotalSteps=3 covers: REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS, then stops.
    flow.entry = "REVIEW_PLANS";
    flow.maxTotalSteps = 3;

    const loadSubFlowCalls = [];
    const stubSubflow = {
      name: "stub", entry: "STUB", maxTotalSteps: 5,
      steps: { STUB: { type: "agent", prompt: "stub", transitions: { done: { done: "completed" } } } },
    };
    const delegates = makeMockDelegates({
      responses: {
        REVIEW_PLANS: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>",
        DRAFT_PLANS: "<AI_STEP_RESULT>nothing</AI_STEP_RESULT>",
      },
      config: { projectRoot: process.cwd() },
      expressionFuncs: {
        draftPlans: () => ["plan-1.md"],
        activePlans: () => [],
        openAudits: () => [],
      },
      loadSubFlow(name) { loadSubFlowCalls.push(name); return stubSubflow; },
    });

    const engine = new FlowEngine(flow, delegates);
    await engine.run();

    // EXEC_PLANS is a subflow; _executeSubflowStep calls loadSubFlow("plan-execution")
    // even when activePlans() resolves empty (the empty-forEach short-circuit happens
    // AFTER the subflow def is loaded), so observing that load proves the aggregate
    // all_complete marker took the REVIEW_PLANS → EXEC_PLANS transition.
    assert.ok(
      loadSubFlowCalls.includes("plan-execution"),
      "REVIEW_PLANS all_complete must route to EXEC_PLANS (loadSubFlow('plan-execution') observed)",
    );
    assert.equal(
      delegates.callLog.filter((c) => c.type === "parse").length, 0,
      "no correction/parse calls anywhere in the run (alias makes approved transition-valid)",
    );
    assert.equal(
      engine.context.get("REVIEW_PLANS").marker, "all_complete",
      "REVIEW_PLANS recorded aggregate marker must be all_complete",
    );
  });
});
