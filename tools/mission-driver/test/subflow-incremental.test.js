import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";
import { mkdtempSync, rmSync, readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";

// draft-robustness WI5 — pins design/draft-robustness-design.md §4.5:
// `_wfAppendSubflowRun` mirrors `_onAgentStepUpdate`'s "find the running
// placeholder + patch + _writeWorkflow" pattern, but additionally matches on
// `visits` (re-entry safety). Every forEach item completion appends the run
// record to the placeholder in the main run-state.json so the file is
// self-contained even when the parent process is killed mid-forEach.
//
// Test harness combines `forEach-concurrency.test.js`'s mock `_runChildSubflow`
// pattern with `audit-count.test.js`'s `mkdtempSync + config.runDir` pattern.

const __dirname = dirname(fileURLToPath(import.meta.url));
const ENGINE_SRC = join(__dirname, "..", "src", "engine.js");

const childFlow = {
  name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
    WORK: { type: "agent", prompt: "work on {{forEachItem}}", transitions: { done: { done: "completed" } } },
  },
};

const delay = (ms) => new Promise((r) => setTimeout(r, ms));

const subStep = (concurrency) => ({
  type: "subflow",
  flow: "child",
  forEach: "items",
  ...(concurrency != null ? { concurrency } : {}),
  onItemError: { stopOnError: true },
  transitions: {
    all_complete: { done: "completed" },
    some_failed: { done: "some_ok" },
    all_failed: { done: "all_bad" },
  },
});

function withDir(fn) {
  return async () => {
    const dir = mkdtempSync(join(tmpdir(), "subflow-inc-"));
    try { await fn(dir); }
    finally { rmSync(dir, { recursive: true, force: true }); }
  };
}

function readState(runDir) {
  try { return JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8")); }
  catch { return null; }
}

// Parse the visit number out of the engine's _subflowId format
// `${stepName}-${visit}-${i}`. Splitting on the last two hyphens survives
// hyphen-bearing stepNames (last segment is always i, second-to-last is visit).
function parseVisit(subflowId) {
  const parts = String(subflowId || "").split("-");
  if (parts.length < 3) return null;
  return Number(parts[parts.length - 2]);
}

describe("draft-robustness WI5 — subflowRuns incremental persistence", () => {
  it("Case A: concurrency=1, snapshot taken at item 2 entry shows the prior 2 items already on disk", withDir(async (runDir) => {
    // Concrete value of WI5: a parent killed at item 2's start leaves the
    // main run-state.json with items 0 and 1 — consumers (analyze / git show)
    // can read subflow progress without falling back to per-file scans.
    const flow = simpleFlow({ SUB: subStep(1) }, "SUB");
    const delegates = makeMockDelegates({});
    delegates.vars.items = JSON.stringify(["a", "b", "c"]);
    delegates.loadSubFlow = () => childFlow;
    delegates.config = { projectRoot: runDir, runDir };
    const engine = new FlowEngine(flow, delegates);

    let snapshot = null;
    engine._runChildSubflow = async (_flowDef, vars) => {
      const i = vars.forEachIndex;
      if (i === 2) {
        // Synchronous read on entry: items 0 and 1 must already be persisted.
        snapshot = readState(runDir);
      }
      return { childResult: { status: "completed" }, childFlowVars: {}, subflowFile: `run-state-SUB-1-${i}.json` };
    };

    const result = await engine.run();
    assert.equal(result.status, "completed");
    assert.ok(snapshot, "snapshot must have been captured at item 2 entry");

    const snapStep = snapshot.steps.find((s) => s.name === "SUB");
    assert.equal(snapStep.status, "running",
      "placeholder must still be 'running' — forEach has not returned yet");
    assert.ok(Array.isArray(snapStep.subflowRuns), "placeholder must carry subflowRuns array");
    assert.equal(snapStep.subflowRuns.length, 2,
      "items 0 and 1 must already be incrementally persisted by item 2 entry");
    assert.deepEqual(
      snapStep.subflowRuns.map((r) => r.forEachIndex),
      [0, 1],
      "concurrency=1 is serial, so on-disk order is forEachIndex order",
    );
    assert.deepEqual(
      snapStep.subflowRuns.map((r) => r.status),
      ["completed", "completed"],
    );

    const finalState = readState(runDir);
    const finalStep = finalState.steps.find((s) => s.name === "SUB");
    assert.equal(finalStep.status, "completed", "_wfClose must have replaced the placeholder");
    assert.equal(finalStep.subflowRuns.length, 3, "_wfClose final coverage writes all 3 items");
    assert.deepEqual(
      finalStep.subflowRuns.map((r) => r.forEachIndex),
      [0, 1, 2],
      "final subflowRuns must be forEachIndex-ordered (locked: no regression)",
    );
  }));

  it("Case B: concurrency=2 sliding-window, snapshot taken via deterministic latch shows items 0+1 persisted (mdr-remediate-4 H9)", withDir(async (runDir) => {
    // H9: replaces the original delays=[10,10,200] wall-clock pattern with a
    // deterministic Promise-park latch. Items 0 and 1 are parked on
    // manually-controlled Promises; the test releases both before item 2's
    // snapshot. When a slot frees (item 0 or 1 recordResult fires) the
    // dispatcher starts item 2; item 2 then awaits one microtask yield so the
    // dispatcher's .then() chains for BOTH prior items have drained — at
    // which point items 0 and 1 are provably persisted on disk. The snapshot
    // invariant `subflowRuns.length === 2` becomes order-based, not
    // timing-based.
    //
    // Trace (concurrency=2, items=[a,b,c]):
    //   t0: items 0,1 dispatched → mocks park on park0 / park1 (slots full)
    //   t1: test resolves park0 and park1
    //   microtask chain: item0.mock resolves → dispatcher0.then runs
    //     recordResult(0) → dispatch starts item 2 (slot freed).
    //   microtask: dispatcher1.then runs recordResult(1) → dispatch no-op.
    //   microtask: item2.mock (which awaited Promise.resolve()) continues →
    //     snapshot reads both items 0 and 1 persisted.
    const flow = simpleFlow({ SUB: subStep(2) }, "SUB");
    const delegates = makeMockDelegates({});
    delegates.vars.items = JSON.stringify(["a", "b", "c"]);
    delegates.loadSubFlow = () => childFlow;
    delegates.config = { projectRoot: runDir, runDir };
    const engine = new FlowEngine(flow, delegates);

    let snapshot = null;
    let resolveItem0 = null;
    let resolveItem1 = null;
    const park0 = new Promise((r) => { resolveItem0 = r; });
    const park1 = new Promise((r) => { resolveItem1 = r; });

    const makeChildResult = (i) => ({
      childResult: { status: "completed" },
      childFlowVars: {},
      subflowFile: `run-state-SUB-1-${i}.json`,
    });

    engine._runChildSubflow = async (_flowDef, vars) => {
      const i = vars.forEachIndex;
      if (i === 0) { await park0; return makeChildResult(0); }
      if (i === 1) { await park1; return makeChildResult(1); }
      // i === 2: at mock entry, at least one prior item has been recorded
      // (otherwise no slot would have freed for item 2 to start). One
      // microtask yield lets the OTHER prior item's recordResult also drain
      // before we snapshot — making the assertion deterministic.
      await Promise.resolve();
      snapshot = readState(runDir);
      return makeChildResult(2);
    };

    const runPromise = engine.run();
    // Yield so the dispatcher fills both concurrency slots (items 0 and 1
    // park) before we release them. Two awaits cover the dispatcher's setup
    // microtask chain.
    await Promise.resolve();
    await Promise.resolve();

    // Release both parked items. Their recordResult chains fire in microtasks
    // (in some order); the second one's chain frees a slot for item 2 to
    // dispatch, and item 2's mock then yields once and snapshots.
    resolveItem0();
    resolveItem1();
    const result = await runPromise;

    assert.equal(result.status, "completed");
    assert.ok(snapshot, "snapshot must have been captured via the deterministic latch (not a wall-clock delay)");

    const snapStep = snapshot.steps.find((s) => s.name === "SUB");
    assert.equal(snapStep.subflowRuns.length, 2,
      "items 0 and 1 must be incrementally persisted before item 2 snapshots (deterministic latch)");
    const indices = snapStep.subflowRuns.map((r) => r.forEachIndex).sort((a, b) => a - b);
    assert.deepEqual(indices, [0, 1],
      "the two resolved items must be forEachIndex {0, 1} (resolve order may vary)");

    const finalState = readState(runDir);
    const finalStep = finalState.steps.find((s) => s.name === "SUB");
    assert.equal(finalStep.subflowRuns.length, 3);
    assert.deepEqual(
      finalStep.subflowRuns.map((r) => r.forEachIndex),
      [0, 1, 2],
      "_wfClose sorts + covers with forEachIndex order (no resolve-order leakage)",
    );
  }));

  it("Case C: multi-visit re-entry does not cross-contaminate (visits match is required)", withDir(async (runDir) => {
    // Pins the `visits` guard in _wfAppendSubflowRun: when SUB is re-entered
    // (visit 2), the visit-1 placeholder has been replaced by _wfClose with
    // status:completed, and the visit-2 placeholder is status:running with
    // subflowRuns:[]. A stepName-only match (like _onAgentStepUpdate) would
    // find the visit-1 entry first (most recent by name) and append into it,
    // corrupting the closed record. The visits guard prevents this.
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "loop or end",
        resultTag: "R",
        transitions: {
          loop: { goto: "SUB" },
          end: { done: "completed" },
        },
      },
      SUB: {
        type: "subflow",
        flow: "child",
        forEach: "items",
        transitions: {
          all_complete: { goto: "START" },
          some_failed: { goto: "START" },
          all_failed: { done: "failed" },
        },
      },
    }, "START");

    const delegates = makeMockDelegates({});
    delegates.vars.items = JSON.stringify(["a", "b"]);
    delegates.loadSubFlow = () => childFlow;
    delegates.config = { projectRoot: runDir, runDir };

    let startCalls = 0;
    delegates.runAgent = (stepName) => {
      if (stepName === "START") {
        startCalls++;
        // First two START visits route to SUB (so SUB is entered twice);
        // third START visit ends the flow.
        const marker = startCalls <= 2 ? "loop" : "end";
        return Promise.resolve({ text: `<R>${marker}</R>`, ok: true });
      }
      return Promise.resolve({ text: "<R>done</R>", ok: true });
    };

    const engine = new FlowEngine(flow, delegates);

    let snapshotAtVisit2Item0 = null;
    let snapshotAtVisit2Item1 = null;
    engine._runChildSubflow = async (_flowDef, vars) => {
      const i = vars.forEachIndex;
      const visit = parseVisit(vars._subflowId);
      if (visit === 2 && i === 0) {
        // At visit-2 item-0 entry: visit-1 must be closed (completed with 2
        // runs), visit-2 placeholder must be running with 0 runs.
        snapshotAtVisit2Item0 = readState(runDir);
      }
      if (visit === 2 && i === 1) {
        // At visit-2 item-1 entry: visit-2 item-0 must be incrementally persisted.
        snapshotAtVisit2Item1 = readState(runDir);
      }
      return { childResult: { status: "completed" }, childFlowVars: {}, subflowFile: `run-state-SUB-${visit}-${i}.json` };
    };

    const result = await engine.run();
    assert.equal(result.status, "completed");
    assert.equal(startCalls, 3, "START must be entered three times (loop, loop, end)");
    assert.ok(snapshotAtVisit2Item0, "snapshot at visit-2 item-0 entry must have been captured");
    assert.ok(snapshotAtVisit2Item1, "snapshot at visit-2 item-1 entry must have been captured");

    // Visit 1 closed by _wfClose before visit 2 opens; visit 2 placeholder running with no runs yet.
    const v1AtStart = snapshotAtVisit2Item0.steps.filter((s) => s.name === "SUB" && s.visits === 1);
    assert.equal(v1AtStart.length, 1, "exactly one SUB visit-1 record");
    assert.equal(v1AtStart[0].status, "completed",
      "visit-1 must be closed (_wfClose replaced the placeholder) before visit-2 opens");
    assert.equal(v1AtStart[0].subflowRuns.length, 2,
      "visit-1's _wfClose record carries the final 2-item subflowRuns");

    const v2AtStart = snapshotAtVisit2Item0.steps.filter((s) => s.name === "SUB" && s.visits === 2);
    assert.equal(v2AtStart.length, 1, "exactly one SUB visit-2 placeholder");
    assert.equal(v2AtStart[0].status, "running");
    assert.equal(v2AtStart[0].subflowRuns.length, 0,
      "visit-2 placeholder has not pushed any item yet at item-0 entry");

    // After visit-2 item-0 completes, the visit-2 placeholder has 1 run.
    const v2AfterItem0 = snapshotAtVisit2Item1.steps.filter((s) => s.name === "SUB" && s.visits === 2);
    assert.equal(v2AfterItem0.length, 1);
    assert.equal(v2AfterItem0[0].status, "running", "placeholder still running — forEach has not returned");
    assert.equal(v2AfterItem0[0].subflowRuns.length, 1,
      "visit-2 item-0 must have been incrementally appended to the visit-2 placeholder");

    // The visit-1 closed record must NOT have been mutated by visit-2 writes.
    const v1AfterItem0 = snapshotAtVisit2Item1.steps.filter((s) => s.name === "SUB" && s.visits === 1);
    assert.equal(v1AfterItem0[0].subflowRuns.length, 2,
      "visit-1's closed subflowRuns must remain at 2 — visits guard prevents cross-contamination");
  }));

  it("Case D: final coverage semantics not regressed — resolve order [1,2,0] still yields forEachIndex [0,1,2]", withDir(async (runDir) => {
    // Item 0 is the slow pole (100ms); items 1 and 2 resolve first (10ms each)
    // so the incremental writes happen in resolve order [1, 2, 0]. _wfClose's
    // final coverage must sort + replace, yielding forEachIndex order. This
    // locks the contract: incremental persistence cannot leak resolve-order
    // into the final on-disk state.
    const flow = simpleFlow({ SUB: subStep(2) }, "SUB");
    const delegates = makeMockDelegates({});
    delegates.vars.items = JSON.stringify(["a", "b", "c"]);
    delegates.loadSubFlow = () => childFlow;
    delegates.config = { projectRoot: runDir, runDir };
    const engine = new FlowEngine(flow, delegates);

    engine._runChildSubflow = async (_flowDef, vars) => {
      const i = vars.forEachIndex;
      const d = [100, 10, 10][i] ?? 10;
      await delay(d);
      return { childResult: { status: "completed" }, childFlowVars: {}, subflowFile: `run-state-SUB-1-${i}.json` };
    };

    const result = await engine.run();
    assert.equal(result.status, "completed");

    const state = readState(runDir);
    const step = state.steps.find((s) => s.name === "SUB");
    assert.equal(step.subflowRuns.length, 3);
    assert.deepEqual(
      step.subflowRuns.map((r) => r.forEachIndex),
      [0, 1, 2],
      "final subflowRuns must be strictly forEachIndex-ordered regardless of resolve order",
    );
  }));

  it("Case E: _wfAppendSubflowRun is a silent no-op when no matching placeholder exists", () => {
    // Defensive: a missing match must not throw or mutate state. Mirrors
    // _onAgentStepUpdate's silent break-on-no-match.
    const flow = simpleFlow({ START: { type: "agent", prompt: "x", transitions: { ok: { done: "completed" } } } }, "START");
    const engine = new FlowEngine(flow, makeMockDelegates({}));
    engine.workflow = {
      steps: [{ name: "OTHER", visits: 1, status: "running", subflowRuns: [] }],
      updatedAt: "fixed",
    };
    const before = JSON.stringify(engine.workflow);

    assert.doesNotThrow(() => {
      engine._wfAppendSubflowRun("MISSING", 1, { forEachIndex: 0, status: "completed" });
    });
    assert.equal(JSON.stringify(engine.workflow), before,
      "workflow.steps must be unchanged when no name+visits+running placeholder matches");
  });

  it("Case F: grep anchor — _wfAppendSubflowRun appears at least 4 times in engine.js (def + 3 call sites: forEach x2 + non-forEach x1)", () => {
    const src = readFileSync(ENGINE_SRC, "utf8");
    const matches = src.match(/_wfAppendSubflowRun/g) || [];
    assert.ok(
      matches.length >= 4,
      `expected >= 4 occurrences of _wfAppendSubflowRun (1 def + 3 call sites after mdr-remediate-4 H2), got ${matches.length}`,
    );
  });

  it("Case G: non-forEach single-child subflow writes a pre-run running placeholder (mdr-remediate-4 H2)", withDir(async (runDir) => {
    // Locks the H2 fix in the non-forEach branch of _executeSubflowStep:
    // BEFORE awaiting _runChildSubflow, the engine now calls _wfAppendSubflowRun
    // with status:"running". A SIGKILL mid-child leaves run-state.json with
    // subflowRuns:[{status:"running",...}] instead of [] (crash invariant).
    // After the child returns, _wfClose replaces the placeholder with the
    // terminal record — no leftover running entry, no duplicate (end-state
    // invariant). Uses a manually-controlled Promise latch (not setTimeout) so
    // the snapshot is provably mid-flight, not timed.
    const flow = simpleFlow({
      SUB: {
        type: "subflow",
        flow: "child",
        transitions: {
          complete: { done: "completed" },
          failed: { done: "failed" },
        },
      },
    }, "SUB");

    const delegates = makeMockDelegates({});
    delegates.loadSubFlow = () => childFlow;
    delegates.config = { projectRoot: runDir, runDir };
    const engine = new FlowEngine(flow, delegates);

    let snapshot = null;
    let releaseChild = null;
    let signalEntered = null;
    const childGate = new Promise((r) => { releaseChild = r; });
    const enteredGate = new Promise((r) => { signalEntered = r; });
    engine._runChildSubflow = async () => {
      // Synchronous read on entry: parent has already pushed the running
      // placeholder via _wfAppendSubflowRun BEFORE awaiting this mock.
      snapshot = readState(runDir);
      signalEntered();
      await childGate;
      return { childResult: { status: "completed" }, childFlowVars: {}, subflowFile: "run-state-SUB-1-0.json" };
    };

    const runPromise = engine.run();
    // Wait until the mock has been entered and snapshotted (deterministic — no
    // wall-clock delay). The engine's setup awaits chain through microtasks
    // until control reaches _executeSubflowStep's pre-run _wfAppendSubflowRun.
    await enteredGate;

    // Crash invariant: on-disk state has the pre-run running placeholder.
    assert.ok(snapshot, "snapshot must have been captured while the child was pending");
    const snapStep = snapshot.steps.find((s) => s.name === "SUB");
    assert.equal(snapStep.status, "running",
      "placeholder must still be 'running' — child has not returned yet");
    assert.ok(Array.isArray(snapStep.subflowRuns), "placeholder must carry subflowRuns array");
    assert.equal(snapStep.subflowRuns.length, 1,
      "non-forEach pre-run placeholder must be on disk as a single running entry (H2 crash invariant: NOT [])");
    assert.equal(snapStep.subflowRuns[0].status, "running");
    assert.equal(snapStep.subflowRuns[0].forEachIndex, 0,
      "non-forEach child uses forEachIndex 0 (mirrors the single-child _subflowId `${stepName}-${visit}-0`)");

    // Release the child → end-state invariant.
    releaseChild();
    const result = await runPromise;
    assert.equal(result.status, "completed");

    const finalState = readState(runDir);
    const finalStep = finalState.steps.find((s) => s.name === "SUB");
    assert.equal(finalStep.status, "completed", "_wfClose replaced the running placeholder");
    assert.equal(finalStep.subflowRuns.length, 1,
      "exactly ONE terminal-state entry — no leftover running placeholder, no duplicate");
    assert.equal(finalStep.subflowRuns[0].status, "completed",
      "terminal status matches the mock child's result");
    assert.equal(finalStep.subflowRuns[0].forEachIndex, 0);
  }));

  it("Case H: _wfAppendSubflowRun no-op safety also covers the non-forEach call site's running-placeholder shape (mdr-remediate-4 H2)", () => {
    // Mirrors Case E's coverage but locks the no-op contract for the exact
    // run-record shape the new non-forEach call site uses ({status:"running"}).
    // Case E uses {status:"completed"} (the forEach shape); this sibling
    // assertion defends against a future refactor that special-cases one
    // record shape but misses the other.
    const flow = simpleFlow({ START: { type: "agent", prompt: "x", transitions: { ok: { done: "completed" } } } }, "START");
    const engine = new FlowEngine(flow, makeMockDelegates({}));
    engine.workflow = {
      steps: [{ name: "OTHER", visits: 1, status: "running", subflowRuns: [] }],
      updatedAt: "fixed",
    };
    const before = JSON.stringify(engine.workflow);

    assert.doesNotThrow(() => {
      engine._wfAppendSubflowRun("MISSING", 1, { forEachIndex: 0, forEachItem: null, file: null, status: "running" });
    });
    assert.equal(JSON.stringify(engine.workflow), before,
      "workflow.steps must be unchanged when no name+visits+running placeholder matches (non-forEach call site shape)");
  });
});
