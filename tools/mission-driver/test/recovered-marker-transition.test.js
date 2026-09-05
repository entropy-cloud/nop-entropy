import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

// mdr-2 Phase 2 — a recovered (transition-VALID) marker must NOT route to
// onError even when the subprocess exited non-zero (result.ok === false). The
// marker is the authoritative step outcome; ok is flipped true so the normal
// marker transition is taken. An invalid/null marker keeps the original ok so
// real failures still route to onError (memory L003, count=3).
describe("FlowEngine — mdr-2 Phase 2: recovered valid marker → ok=true transition", () => {

  it("strict miss + fuzzy recovers a TRANSITION-VALID marker (result.ok===false) → step result ok flips true, no parse fallback", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { done: { done: "completed" } },
        onError: { done: "failed" },
      },
    });
    const delegates = makeMockDelegates({
      responses: {
        // Tag-name typo <AIE_STEP_RESULT> defeats strict AND tolerant (both key
        // off the exact / case-insensitive tag name), but fuzzy recovers `done`
        // via the value whitelist. result.ok===false simulates a non-zero exit
        // that still emitted recoverable output — the exact Phase 2 scenario.
        START: { text: "<AIE_STEP_RESULT>done</AI_STEP_RESULT>", ok: false },
      },
    });
    const engine = new FlowEngine(flow, delegates);

    const stepResult = await engine._executeAgentStep("START", flow.steps.START, null);
    assert.equal(stepResult.marker, "done", "fuzzy must recover the done marker");
    assert.equal(stepResult.ok, true, "transition-valid recovered marker must flip ok=true (Phase 2)");
    assert.equal(
      delegates.callLog.filter((c) => c.type === "parse").length, 0,
      "fuzzy recovery must not invoke the LLM parse fallback",
    );
  });

  it("recovered TRANSITION-VALID marker (result.ok===false) takes the normal transition to `completed`, NOT onError", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { done: { done: "completed" } },
        onError: { done: "failed" },
      },
    });
    const delegates = makeMockDelegates({
      responses: {
        START: { text: "<AIE_STEP_RESULT>done</AI_STEP_RESULT>", ok: false },
      },
    });
    const engine = new FlowEngine(flow, delegates);
    const runResult = await engine.run();
    assert.equal(
      runResult.status, "completed",
      "recovered valid marker must take the normal marker transition (completed), not onError (failed)",
    );
  });

  it("recovered INVALID marker (result.ok===false) → ok stays result.ok=false → onError", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { done: { done: "completed" } },
        // Disable the correction agent so the invalid marker stays as-is and
        // Phase 2's transition-validity guard is what decides the ok binding.
        onUnknownMaxRetries: 0,
        onError: { done: "failed" },
      },
    });
    const delegates = makeMockDelegates({
      responses: {
        // Exact tag, but value `maybe` is NOT a transition. strict extracts an
        // invalid marker; Phase 2 must NOT relax — ok keeps result.ok=false.
        START: { text: "<AI_STEP_RESULT>maybe</AI_STEP_RESULT>", ok: false },
      },
    });
    const engine = new FlowEngine(flow, delegates);

    const stepResult = await engine._executeAgentStep("START", flow.steps.START, null);
    assert.equal(stepResult.marker, "maybe", "strict extracts the (invalid) marker unchanged");
    assert.equal(stepResult.ok, false, "invalid marker must keep ok=result.ok (no relaxation)");

    const runResult = await engine.run();
    assert.equal(runResult.status, "failed", "invalid marker must route to onError, not a normal transition");
  });

  it("null marker (no recovery, result.ok===false) → ok stays result.ok=false → onError", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { done: { done: "completed" } },
        onError: { done: "failed" },
      },
    });
    const delegates = makeMockDelegates({
      responses: { START: { text: "no marker anywhere here", ok: false } },
    });
    // parse fallback also misses → truly null marker.
    delegates.runParseAgent = async () => ({ text: "still nothing", ok: true });
    const engine = new FlowEngine(flow, delegates);

    const stepResult = await engine._executeAgentStep("START", flow.steps.START, null);
    assert.equal(stepResult.marker, null, "no marker recovered");
    assert.equal(stepResult.ok, false, "null marker must keep ok=result.ok (onError)");

    const runResult = await engine.run();
    assert.equal(runResult.status, "failed", "null marker must route to onError");
  });
});
