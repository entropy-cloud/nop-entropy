import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

// OPT-1 (Phase 2): the opencode sessionId that executed a step must be persisted
// into the run-state.json step record (and the step_completed/step_failed event
// payloads) so each step can be replayed via `opencode export <sessionId>`.
// These tests pin the engine-side persistence: the completed and failed close
// points thread `result.sessionId` into the `_wfClose` record, and non-agent
// steps record sessionId=null (no session to replay).

describe("OPT-1 sessionId persistence — engine record", () => {
  it("completed agent step record carries sessionId from the agent run", async () => {
    const flow = simpleFlow({
      WORK: {
        type: "agent",
        prompt: "do work",
        resultTag: "AI_STEP_RESULT",
        transitions: { ok: { done: "completed" } },
      },
    }, "WORK");

    const delegates = makeMockDelegates({
      async runAgent() {
        return { text: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>", ok: true, sessionId: "ses_engine_completed_123" };
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");

    const rec = engine.workflow.steps.find((s) => s.name === "WORK" && s.status === "completed");
    assert.ok(rec, "completed WORK record must exist");
    assert.equal(rec.sessionId, "ses_engine_completed_123");
  });

  it("failed agent step (ok=false) record carries sessionId", async () => {
    const flow = simpleFlow({
      WORK: {
        type: "agent",
        prompt: "do work",
        resultTag: "AI_STEP_RESULT",
        transitions: { ok: { done: "completed" } },
      },
    }, "WORK");

    const delegates = makeMockDelegates({
      async runAgent() {
        return { text: "", ok: false, sessionId: "ses_engine_failed_456" };
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "failed");

    const rec = engine.workflow.steps.find((s) => s.name === "WORK" && s.status === "failed");
    assert.ok(rec, "failed WORK record must exist");
    assert.equal(rec.sessionId, "ses_engine_failed_456");
  });

  it("non-agent (tool) step record has sessionId null", async () => {
    const flow = simpleFlow({
      WORK: {
        type: "tool",
        command: "echo hi",
        transitions: { pass: { done: "completed" } },
      },
    }, "WORK");

    const delegates = makeMockDelegates({});
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");

    const rec = engine.workflow.steps.find((s) => s.name === "WORK");
    assert.ok(rec);
    assert.equal(rec.sessionId, null);
  });

  it("step_completed event payload carries sessionId", async () => {
    const flow = simpleFlow({
      WORK: {
        type: "agent",
        prompt: "do work",
        resultTag: "AI_STEP_RESULT",
        transitions: { ok: { done: "completed" } },
      },
    }, "WORK");

    const emitted = [];
    const delegates = makeMockDelegates({
      async runAgent() {
        return { text: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>", ok: true, sessionId: "ses_event_789" };
      },
    });
    // capture emitted events by stubbing the engine's emitter via a runDir-less
    // engine: _emitEvent is a no-op when eventsFile is null, so instead spy on
    // the record (already covered above). Here we verify via the record path
    // that the value flows through completedRec.sessionId.
    const engine = new FlowEngine(flow, delegates);
    // Force an events target so _emitEvent actually records. Use a spy by
    // overriding _emitEvent on the instance.
    engine._emitEvent = (type, data) => emitted.push({ type, ...data });
    await engine.run();

    const completed = emitted.find((e) => e.type === "step_completed");
    assert.ok(completed, "step_completed must be emitted");
    assert.equal(completed.sessionId, "ses_event_789");
  });
});
