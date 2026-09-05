import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

const childFlow = {
  name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
    WORK: { type: "agent", prompt: "work on {{forEachItem}}", transitions: { done: { done: "completed" } } },
  },
};

const delay = (ms) => new Promise((r) => setTimeout(r, ms));

// Build an engine whose _runChildSubflow is mocked so we can observe real
// concurrency (in-flight counter) without spawning real child engines.
// failSet = Set of forEachIndex values that should report status "failed".
// delayMs  = number (same delay for every item) OR array of per-item delays.
// Returns a harness object: access h.maxInflight / h.resolvedOrder / h.dispatchLog AFTER run().
function buildHarness({ items, concurrency, failSet = new Set(), stopOnError = true, delayMs = 25 }) {
  const flow = simpleFlow({
    SUB: {
      type: "subflow",
      flow: "child",
      forEach: "items",
      ...(concurrency != null ? { concurrency } : {}),
      ...(stopOnError ? { onItemError: { stopOnError: true } } : {}),
      transitions: {
        all_complete: { done: "completed" },
        some_failed: { done: "some_ok" },
        all_failed: { done: "all_bad" },
      },
    },
  }, "SUB");

  const delegates = makeMockDelegates({});
  delegates.vars.items = JSON.stringify(items);
  delegates.loadSubFlow = () => childFlow;

  const engine = new FlowEngine(flow, delegates);

  let inflight = 0;
  let maxInflight = 0;
  const resolvedOrder = [];
  const dispatchLog = []; // { index, dispatchedAt, endedAt } timestamps (ms since run start)
  const t0 = Date.now();

  engine._runChildSubflow = async (_flowDef, vars) => {
    const i = vars.forEachIndex;
    const dispatchedAt = Date.now() - t0;
    inflight++;
    maxInflight = Math.max(maxInflight, inflight);
    const d = Array.isArray(delayMs) ? (delayMs[i] ?? 0) : delayMs;
    await delay(d);
    inflight--;
    const endedAt = Date.now() - t0;
    const status = failSet.has(i) ? "failed" : "completed";
    resolvedOrder.push(i);
    dispatchLog.push({ index: i, dispatchedAt, endedAt });
    return { childResult: { status }, childFlowVars: {}, subflowFile: `run-state-SUB-1-${i}.json` };
  };

  return {
    engine,
    delegates,
    run: () => engine.run(),
    get maxInflight() { return maxInflight; },
    get resolvedOrder() { return resolvedOrder; },
    get dispatchLog() { return dispatchLog; },
  };
}

describe("FlowEngine — subflow forEach sliding-window concurrency", () => {
  it("concurrency=1 → fully sequential (backward compatible; never >1 in flight)", async () => {
    const h = buildHarness({ items: ["a", "b", "c", "d"], concurrency: 1 });
    const result = await h.run();
    assert.equal(result.status, "completed");
    assert.equal(h.maxInflight, 1, "concurrency=1 must run strictly one at a time");
  });

  it("concurrency=2, 4 items → sliding-window keeps <=2 in flight; subflowRuns ordered, all run", async () => {
    const h = buildHarness({ items: ["a", "b", "c", "d"], concurrency: 2 });
    const result = await h.run();
    assert.equal(result.status, "completed");
    assert.ok(h.maxInflight <= 2, "concurrency=2 must cap in-flight at 2 (got " + h.maxInflight + ")");
    assert.ok(h.maxInflight >= 1, "some concurrency must be exercised");

    const sub = h.engine.context.get("SUB");
    assert.ok(sub.subflowRuns, "subflow step must record subflowRuns");
    assert.equal(sub.subflowRuns.length, 4, "all 4 items must run");
    // subflowRuns stays in forEachIndex order despite resolve-order collection
    assert.deepEqual(
      sub.subflowRuns.map((r) => r.forEachIndex),
      [0, 1, 2, 3],
    );
    assert.equal(h.resolvedOrder.length, 4);
  });

  it("concurrency=3, 5 items → sliding-window caps at 3; all 5 run; ordered", async () => {
    const h = buildHarness({ items: ["a", "b", "c", "d", "e"], concurrency: 3 });
    const result = await h.run();
    assert.equal(result.status, "completed");
    assert.ok(h.maxInflight <= 3, "must never exceed concurrency=3");
    const sub = h.engine.context.get("SUB");
    assert.equal(sub.subflowRuns.length, 5);
    assert.deepEqual(sub.subflowRuns.map((r) => r.forEachIndex), [0, 1, 2, 3, 4]);
  });

  it("concurrency > items.length → every item dispatches at once (capped by item count)", async () => {
    const h = buildHarness({ items: ["a", "b", "c"], concurrency: 10 });
    const result = await h.run();
    assert.equal(result.status, "completed");
    assert.equal(h.maxInflight, 3, "only 3 items exist; concurrency capped by item count");
    const sub = h.engine.context.get("SUB");
    assert.equal(sub.subflowRuns.length, 3);
  });

  it("onItemError.stopOnError + sliding-window → stops dispatching new items on first failure; in-flight items complete; marker some_failed", async () => {
    const h = buildHarness({
      items: ["a", "b", "c", "d", "e", "f"], concurrency: 2,
      failSet: new Set([0]), stopOnError: true,
    });
    const result = await h.run();
    assert.equal(result.status, "some_ok", "mixed result → some_failed transition");
    const sub = h.engine.context.get("SUB");
    // sliding-window: the failing item's partner (and any item dispatched before the
    // failure is observed) complete; NO further items dispatch after stopOnError fires.
    assert.ok(sub.subflowRuns.length < 6, "must NOT have run all 6 items after stopOnError");
    assert.ok(sub.subflowRuns.length >= 2, "at least the initially-dispatched batch completes");
    assert.equal(sub.marker, "some_failed", "marker must be some_failed");
    assert.ok(h.maxInflight <= 2, "concurrency cap respected even on early stop");
  });

  it("no stopOnError → every item runs even if some items fail", async () => {
    const h = buildHarness({
      items: ["a", "b", "c", "d"], concurrency: 2,
      failSet: new Set([0]), stopOnError: false,
    });
    const result = await h.run();
    assert.equal(result.status, "some_ok");
    const sub = h.engine.context.get("SUB");
    assert.equal(sub.subflowRuns.length, 4, "without stopOnError every item runs");
    assert.equal(sub.marker, "some_failed");
  });

  it("default (no concurrency key) → sequential, same as concurrency=1", async () => {
    const h = buildHarness({ items: ["a", "b", "c"], concurrency: null });
    const result = await h.run();
    assert.equal(result.status, "completed");
    assert.equal(h.maxInflight, 1, "omitting concurrency defaults to sequential");
  });

  it("PROOF: sliding-window overlap — a fast-completing item's successor dispatches BEFORE a slow sibling completes (batch-barrier could not)", async () => {
    // Unbalanced durations: item 1 is the long pole (200ms); the others are 10ms each.
    // Under batch-barrier (concurrency=2) items 2 & 3 cannot start until BOTH items 0 & 1
    // finish (~200ms). Under sliding-window, item 2 dispatches the instant item 0 resolves
    // (~10ms) — long before item 1 ends. That ordering is the batch-vs-sliding discriminator.
    const h = buildHarness({
      items: ["a", "b", "c", "d"], concurrency: 2,
      delayMs: [10, 200, 10, 10],
    });
    const result = await h.run();
    assert.equal(result.status, "completed");

    const log1 = h.dispatchLog.find((e) => e.index === 1); // slow item
    const log2 = h.dispatchLog.find((e) => e.index === 2); // first successor of the fast item 0
    assert.ok(log1 && log2, "dispatch log must contain items 1 and 2");

    // Core sliding-window evidence: item 2 was dispatched before item 1 finished.
    // (Under batch-barrier, item 2's dispatchedAt would be >= item 1's endedAt.)
    assert.ok(
      log2.dispatchedAt < log1.endedAt,
      `item 2 dispatched at ${log2.dispatchedAt}ms must precede item 1 end at ${log1.endedAt}ms (sliding overlap)`,
    );

    // Invariants preserved
    assert.ok(h.maxInflight <= 2, "maxInflight must respect concurrency=2 (got " + h.maxInflight + ")");
    const sub = h.engine.context.get("SUB");
    assert.equal(sub.subflowRuns.length, 4, "all items run");
    assert.deepEqual(sub.subflowRuns.map((r) => r.forEachIndex), [0, 1, 2, 3], "ordered by forEachIndex");
  });
});
