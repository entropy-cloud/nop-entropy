import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

// dre-d7 Phase 2 (G3) — script-step in-process wall-clock envelope. Script
// steps run in-process (engine.js _executeScriptStep `await stepDef.run(...)`)
// with NO executor watchdog; a buggy/omitted internal timeout could hang the
// whole engine process. stepDef.timeoutMs wraps stepDef.run in a Promise.race
// against a sleep; on timeout it returns marker "fail" + a transcript reason.
// These tests exercise the engine integration (not the pure executor) using
// the same mock-delegate pattern as core.test.js script steps.

describe("FlowEngine — script step wall-clock envelope (G3)", () => {
  it("timeoutMs + never-resolving run → returns marker 'fail' + timeout reason", async () => {
    const flow = simpleFlow({
      START: {
        type: "script",
        timeoutMs: 50,
        // never resolves — simulates a script with a missing internal timeout
        run: () => new Promise(() => {}),
        transitions: {
          pass: { done: "completed" },
          fail: { done: "failed" },
        },
      },
    });

    const delegates = makeMockDelegates();
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed", "timed-out script step must route via the 'fail' transition");
    assert.equal(result.stepCount, 1);
    // The synthesized timeout text must be recorded for EVALUATE / human review.
    const ctx = engine.context.get("START");
    assert.ok(ctx, "step context must be recorded");
    assert.equal(ctx.marker, "fail");
    assert.match(ctx.text, /script step START exceeded timeoutMs=50/);
  });

  it("timeoutMs + run that resolves AFTER the deadline → still bounded to 'fail' (late resolve ignored)", async () => {
    const flow = simpleFlow({
      START: {
        type: "script",
        timeoutMs: 30,
        // resolves well after the 30ms wall clock — must be preempted
        run: () => new Promise((r) => setTimeout(() => r("phase_late"), 500)),
        transitions: {
          phase_a: { done: "completed" },
          fail: { done: "failed" },
        },
      },
    });

    const delegates = makeMockDelegates();
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed");
    const ctx = engine.context.get("START");
    assert.equal(ctx.marker, "fail");
    assert.match(ctx.text, /exceeded timeoutMs=30/);
  });

  it("timeoutMs + run that resolves BEFORE the deadline → normal marker used (no false timeout)", async () => {
    const flow = simpleFlow({
      START: {
        type: "script",
        timeoutMs: 5000,
        run: () => "phase_a",
        transitions: {
          phase_a: { goto: "A" },
          fail: { done: "failed" },
        },
      },
      A: {
        type: "agent",
        prompt: "do a",
        resultTag: "X",
        transitions: { ok: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({ responses: { A: "<X>ok</X>" } });
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(result.stepCount, 2);
  });

  it("no timeoutMs declared → run awaited directly (backward compatible, no race)", async () => {
    // A run that takes a little time but completes; without timeoutMs the engine
    // must NOT preempt it (legacy behaviour — relies on the script's own internal
    // timeout). Uses an async run returning an object marker form.
    const flow = simpleFlow({
      START: {
        type: "script",
        // deliberately NO timeoutMs
        run: async () => {
          await new Promise((r) => setTimeout(r, 20));
          return { marker: "phase_a", text: "completed normally" };
        },
        transitions: {
          phase_a: { done: "completed" },
          fail: { done: "failed" },
        },
      },
    });

    const delegates = makeMockDelegates();
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    const ctx = engine.context.get("START");
    assert.equal(ctx.marker, "phase_a");
  });

  it("timeoutMs + run that REJECTS before deadline → rejection propagates (not swallowed as timeout)", async () => {
    const flow = simpleFlow({
      START: {
        type: "script",
        timeoutMs: 5000,
        run: () => Promise.reject(new Error("script blew up")),
        transitions: {
          pass: { done: "completed" },
          fail: { done: "failed" },
        },
        onError: { done: "failed" },
      },
    });

    const delegates = makeMockDelegates();
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    // The rejection surfaces as a thrown error caught by the dispatch try/catch
    // → onError.done = "failed" (NOT the synthesized timeout marker). A thrown
    // script error does NOT record step context (engine.js returns via onError
    // before context.set), which distinguishes it from the timeout path. This
    // proves the wall clock does not mask genuine script errors as timeouts,
    // and the runP.catch(()=>{}) NB4 handler does not swallow the propagation.
    assert.equal(result.status, "failed");
    assert.equal(engine.context.get("START"), undefined, "thrown error must not synthesize a timeout context");
  });
});
