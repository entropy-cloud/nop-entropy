import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";
import { mergeSubflowChildren } from "../src/monitor.js";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync, existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

function withDir(fn) {
  return async () => {
    const dir = mkdtempSync(join(tmpdir(), "subflow-iso-"));
    mkdirSync(dir, { recursive: true });
    try { await fn(dir); }
    finally { rmSync(dir, { recursive: true, force: true }); }
  };
}

const childFlow = {
  name: "child", entry: "WORK", maxTotalSteps: 20, steps: {
    WORK: { type: "agent", prompt: "work", transitions: { done: { done: "completed" } } },
  },
};

describe("subflow state isolation — run-state.json not overwritten by child", () => {
  it("child writes to run-state-{subflowId}.json; parent keeps all top-level steps", withDir(async (runDir) => {
    const flow = simpleFlow({
      BEFORE: { type: "agent", prompt: "before", transitions: { ok: { goto: "SUB" } } },
      SUB: { type: "subflow", flow: "child", transitions: { complete: { goto: "AFTER" }, failed: { done: "failed" } } },
      AFTER: { type: "agent", prompt: "after", transitions: { ok: { done: "completed" } } },
    }, "BEFORE");

    const delegates = makeMockDelegates({
      responses: {
        BEFORE: { text: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>", ok: true },
        WORK: { text: "<AI_STEP_RESULT>done</AI_STEP_RESULT>", ok: true },
        AFTER: { text: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>", ok: true },
      },
      config: { projectRoot: runDir, runDir },
    });
    delegates.loadSubFlow = () => childFlow;

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");

    const parent = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
    assert.equal(parent.steps.length, 3,
      "parent run-state.json must retain all 3 top-level steps (BEFORE, SUB, AFTER) — not overwritten by child _initWorkflow");
    const names = parent.steps.map(s => s.name);
    assert.deepEqual(names, ["BEFORE", "SUB", "AFTER"]);

    const subStep = parent.steps[1];
    assert.equal(subStep.type, "subflow", "SUB step record must carry type=subflow");
    assert.ok(Array.isArray(subStep.subflowRuns), "SUB step must carry subflowRuns");
    assert.equal(subStep.subflowRuns.length, 1);

    const subFile = subStep.subflowRuns[0].file;
    assert.ok(subFile && subFile.startsWith("run-state-"), `subflowRun.file should be a child state file, got: ${subFile}`);
    assert.ok(existsSync(join(runDir, subFile)), `child state file ${subFile} must exist on disk`);

    const childState = JSON.parse(readFileSync(join(runDir, subFile), "utf8"));
    assert.ok(childState.steps.length > 0, "child state must contain the WORK step");
    assert.equal(childState.steps[0].name, "WORK");
  }));

  it("forEach subflow: each item gets its own child state file", withDir(async (runDir) => {
    const flow = simpleFlow({
      SUB: {
        type: "subflow", flow: "child", forEach: "items",
        transitions: { all_complete: { done: "completed" }, some_failed: { done: "failed" }, all_failed: { done: "failed" } },
      },
    }, "SUB");

    const delegates = makeMockDelegates({
      responses: { WORK: { text: "<AI_STEP_RESULT>done</AI_STEP_RESULT>", ok: true } },
      config: { projectRoot: runDir, runDir },
    });
    delegates.vars.items = '["item-a","item-b"]';
    delegates.loadSubFlow = () => childFlow;

    const engine = new FlowEngine(flow, delegates);
    await engine.run();

    const parent = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
    const subStep = parent.steps[0];
    assert.equal(subStep.subflowRuns.length, 2, "2 forEach items → 2 subflowRuns");
    const files = subStep.subflowRuns.map(r => r.file);
    assert.notEqual(files[0], files[1], "each item must have a distinct child file");
    assert.equal(subStep.subflowRuns[0].forEachItem, "item-a");
    assert.equal(subStep.subflowRuns[1].forEachItem, "item-b");
    for (const f of files) {
      assert.ok(existsSync(join(runDir, f)), `child file ${f} must exist`);
    }
  }));
});

describe("monitor mergeSubflowChildren — attach child state to step.children", () => {
  it("merges child state files into step.children tree", () => {
    const runDir = mkdtempSync(join(tmpdir(), "merge-"));
    try {
      writeFileSync(join(runDir, "run-state.json"), JSON.stringify({
        missionName: "t", status: "running", currentStep: "X",
        steps: [
          { name: "A", status: "completed" },
          { name: "SUB", status: "completed", type: "subflow", subflowRuns: [
            { forEachIndex: 0, forEachItem: "p1.md", file: "run-state-SUB-1-0.json", status: "completed" },
          ] },
        ],
      }));
      writeFileSync(join(runDir, "run-state-SUB-1-0.json"), JSON.stringify({
        status: "completed", currentStep: null,
        steps: [
          { name: "EXECUTE", status: "completed", durationMs: 5000, sessionId: "ses_x" },
          { name: "BUILD_VERIFY", status: "completed", durationMs: 3000 },
        ],
      }));

      const state = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
      mergeSubflowChildren(runDir, state.steps);

      const subStep = state.steps[1];
      assert.ok(Array.isArray(subStep.children), "SUB step must have children after merge");
      assert.equal(subStep.children.length, 1);
      const child = subStep.children[0];
      assert.equal(child.forEachItem, "p1.md");
      assert.equal(child.status, "completed");
      assert.equal(child.steps.length, 2);
      assert.equal(child.steps[0].name, "EXECUTE");
      assert.equal(child.steps[0].sessionId, "ses_x", "child step sessionId should pass through");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("gracefully handles missing child file (no crash, empty steps)", () => {
    const runDir = mkdtempSync(join(tmpdir(), "merge-miss-"));
    try {
      const steps = [
        { name: "SUB", type: "subflow", subflowRuns: [
          { forEachIndex: 0, forEachItem: null, file: "run-state-GONE.json", status: "completed" },
        ] },
      ];
      mergeSubflowChildren(runDir, steps);
      assert.equal(steps[0].children.length, 1);
      assert.deepEqual(steps[0].children[0].steps, []);
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("ignores non-subflow steps", () => {
    const steps = [{ name: "A", status: "completed" }];
    mergeSubflowChildren("/nonexistent", steps);
    assert.ok(!steps[0].children, "non-subflow step should not get children");
  });

  it("recovers children from disk for aborted step with empty subflowRuns (fix: disappearing steps after reconcile)", () => {
    const runDir = mkdtempSync(join(tmpdir(), "merge-abort-"));
    try {
      writeFileSync(join(runDir, "run-state.json"), JSON.stringify({
        missionName: "t", status: "aborted", currentStep: "SUB",
        steps: [
          { name: "SUB", status: "aborted", visits: 2, type: "subflow", subflowRuns: [] },
        ],
      }));
      writeFileSync(join(runDir, "run-state-SUB-2-0.json"), JSON.stringify({
        status: "completed", currentStep: null,
        steps: [{ name: "EXECUTE", status: "completed", durationMs: 1000 }],
      }));
      writeFileSync(join(runDir, "run-state-SUB-2-1.json"), JSON.stringify({
        status: "aborted", currentStep: "EXECUTE",
        steps: [{ name: "EXECUTE", status: "aborted", durationMs: null }],
      }));

      const state = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
      mergeSubflowChildren(runDir, state.steps);

      const subStep = state.steps[0];
      assert.ok(Array.isArray(subStep.children), "aborted SUB with empty subflowRuns must still recover children from disk");
      assert.equal(subStep.children.length, 2, "both subflow files must be picked up regardless of step status");
      assert.equal(subStep.children[0].status, "completed");
      assert.equal(subStep.children[0].steps[0].name, "EXECUTE");
      assert.equal(subStep.children[1].status, "aborted");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  // Regression for the bug where a forEach subflow with N completed items +
  // 1 in-flight item rendered only the N completed items in the dashboard.
  // Root cause: the disk scan was gated on `subflowRuns.length === 0`, so as
  // soon as ANY prior item completed (and was appended to subflowRuns by
  // _wfAppendSubflowRun on completion), the disk scan was skipped — hiding the
  // in-flight item whose placeholder hadn't been appended yet (engine.js
  // appends on item COMPLETION, not start). The 4th plan's run-state file
  // exists on disk with status:running but is invisible to the dashboard.
  // Fix: always scan disk and take the UNION (subflowRuns entries + disk files
  // whose forEachIndex isn't already in subflowRuns).
  it("shows in-flight forEach child on disk even when subflowRuns is non-empty (running-item visibility)", () => {
    const runDir = mkdtempSync(join(tmpdir(), "merge-inflight-"));
    try {
      writeFileSync(join(runDir, "run-state.json"), JSON.stringify({
        missionName: "t", status: "running", currentStep: "EXEC_PLANS",
        steps: [
          {
            name: "EXEC_PLANS", status: "running", visits: 2, type: "subflow",
            // Engine has appended 3 completed items; the 4th is still running
            // and has NOT been appended yet (WI5 _wfAppendSubflowRun fires on
            // completion, not start).
            subflowRuns: [
              { forEachIndex: 0, forEachItem: "plan-1.md", file: "run-state-EXEC_PLANS-2-0.json", status: "completed" },
              { forEachIndex: 1, forEachItem: "plan-2.md", file: "run-state-EXEC_PLANS-2-1.json", status: "completed" },
              { forEachIndex: 2, forEachItem: "plan-3.md", file: "run-state-EXEC_PLANS-2-2.json", status: "completed" },
            ],
          },
        ],
      }));
      // 3 completed child state files (matching subflowRuns entries)
      for (let i = 0; i < 3; i++) {
        writeFileSync(join(runDir, `run-state-EXEC_PLANS-2-${i}.json`), JSON.stringify({
          status: "completed", currentStep: null,
          steps: [{ name: "EXECUTE", status: "completed", durationMs: 1000 }],
        }));
      }
      // 4th in-flight child state file — exists on disk but NOT in subflowRuns
      writeFileSync(join(runDir, "run-state-EXEC_PLANS-2-3.json"), JSON.stringify({
        status: "running", currentStep: "BUILD_VERIFY",
        steps: [
          { name: "EXECUTE", status: "completed", durationMs: 1000 },
          { name: "BUILD_VERIFY", status: "running", durationMs: null },
        ],
      }));

      const state = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
      mergeSubflowChildren(runDir, state.steps);

      const subStep = state.steps[0];
      assert.ok(Array.isArray(subStep.children), "SUB must have children");
      assert.equal(subStep.children.length, 4,
        "must show all 4 children: 3 from subflowRuns + 1 in-flight from disk (was the bug: only 3 showed)");
      // subflowRuns entries keep their forEachItem; disk-only entry lacks it
      assert.equal(subStep.children[0].forEachItem, "plan-1.md");
      assert.equal(subStep.children[2].forEachItem, "plan-3.md");
      assert.equal(subStep.children[3].forEachItem, null,
        "disk-only child has no forEachItem (engine hasn't persisted it yet)");
      // The in-flight item reflects live status from its disk file
      assert.equal(subStep.children[3].status, "running");
      assert.equal(subStep.children[3].currentStep, "BUILD_VERIFY");
      assert.equal(subStep.children[3].steps.length, 2);
      assert.equal(subStep.children[3].steps[1].status, "running");
      // Indices are sorted (subflowRuns entry order + appended disk entry)
      assert.deepEqual(subStep.children.map((c) => c.forEachIndex), [0, 1, 2, 3]);
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  // Regression for non-forEach subflow (e.g. DEEP_AUDIT) where the engine
  // writes a subflowRuns placeholder with file=null at step start, then the
  // child's run-state-<stepName>-<visits>-0.json is written independently.
  // The old mergeSubflowChildren readSubflowState(null) → null → child got
  // status from placeholder ("running") but EMPTY steps + null currentStep,
  // so the dashboard showed the subflow as running with nothing to expand.
  // Fix: the disk scan MERGES live state into the existing seed entry instead
  // of skipping it (childrenByIndex.has(idx) → continue was the bug).
  it("fills live state from disk when subflowRuns placeholder has file=null (non-forEach DEEP_AUDIT case)", () => {
    const runDir = mkdtempSync(join(tmpdir(), "merge-nullfile-"));
    try {
      writeFileSync(join(runDir, "run-state.json"), JSON.stringify({
        missionName: "t", status: "running", currentStep: "DEEP_AUDIT",
        steps: [
          {
            name: "DEEP_AUDIT", status: "running", visits: 2, type: "subflow",
            // Engine wrote a placeholder at step start: file=null, status=running.
            // The child's own run-state file is written separately and never
            // back-linked into subflowRuns[0].file.
            subflowRuns: [
              { forEachIndex: 0, forEachItem: null, file: null, status: "running" },
            ],
          },
        ],
      }));
      // The child's actual state on disk — full live detail
      writeFileSync(join(runDir, "run-state-DEEP_AUDIT-2-0.json"), JSON.stringify({
        status: "running", currentStep: "OPEN_AUDIT",
        steps: [
          { name: "CHECK_OPEN_AUDITS", status: "skipped", durationMs: 0 },
          { name: "MULTI_AUDIT", status: "completed", durationMs: 438000 },
          { name: "OPEN_AUDIT", status: "running", durationMs: null },
        ],
      }));

      const state = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
      mergeSubflowChildren(runDir, state.steps);

      const subStep = state.steps[0];
      assert.equal(subStep.children.length, 1, "non-forEach subflow has 1 child");
      const child = subStep.children[0];
      // WITHOUT the fix: status was "running" (from placeholder) but steps=[]
      // and currentStep=null (readSubflowState(null) returned null). WITH the
      // fix: disk scan merges live state over the seed.
      assert.equal(child.status, "running");
      assert.equal(child.currentStep, "OPEN_AUDIT",
        "currentStep must come from disk file, not the null-file placeholder");
      assert.equal(child.steps.length, 3,
        "steps must come from disk file, not empty (the bug: expansion showed nothing)");
      assert.equal(child.steps[0].name, "CHECK_OPEN_AUDITS");
      assert.equal(child.steps[0].status, "skipped");
      assert.equal(child.steps[2].name, "OPEN_AUDIT");
      assert.equal(child.steps[2].status, "running");
      // forEachItem stays null (non-forEach subflow has no item path)
      assert.equal(child.forEachItem, null);
      // file gets back-filled from disk so future reads can find it
      assert.equal(child.file, "run-state-DEEP_AUDIT-2-0.json");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  // Regression for O5: when step.visits is absent (synthetic test step,
  // legacy state, or corruption), the narrow `${step.name}-${step.visits}-`
  // disk-scan prefix silently produced "SUB-undefined-" and matched
  // nothing. Combined with a subflowRuns placeholder carrying file=null
  // (the non-forEach start case the refactor targets), the merge silently
  // dropped to empty steps — neither the seed loop (file=null →
  // readSubflowState returns null) nor the disk scan (broken prefix) could
  // recover the live state. Production always writes visits (engine.js
  // _wfOpen increments on entry), so this is latent — but each prior
  // refactor of mergeSubflowChildren has re-tripped the same coupling
  // (open-audit O4). This test pins the broad-prefix fallback so the next
  // refactor cannot silently reintroduce the no-op.
  it("falls back to broad stepName prefix when step.visits is absent (O5: visits-absent + file:null no silent empty steps)", () => {
    const runDir = mkdtempSync(join(tmpdir(), "merge-novisits-"));
    try {
      writeFileSync(join(runDir, "run-state.json"), JSON.stringify({
        missionName: "t", status: "running", currentStep: "SUB",
        steps: [
          {
            name: "SUB", status: "running", type: "subflow",
            // visits deliberately OMITTED — mirrors the :99 synthetic step
            // shape (production engine always writes visits via _wfOpen).
            // Placeholder: file=null + status=running (non-forEach start).
            subflowRuns: [
              { forEachIndex: 0, forEachItem: null, file: null, status: "running" },
            ],
          },
        ],
      }));
      // Real child state on disk — filename encodes visits=1 even though
      // the parent step's visits field is absent. The broad-prefix
      // fallback should still locate it.
      writeFileSync(join(runDir, "run-state-SUB-1-0.json"), JSON.stringify({
        status: "running", currentStep: "WORK",
        steps: [
          { name: "INIT", status: "completed", durationMs: 100 },
          { name: "WORK", status: "running", durationMs: null },
        ],
      }));

      const state = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
      mergeSubflowChildren(runDir, state.steps);

      const subStep = state.steps[0];
      assert.ok(Array.isArray(subStep.children), "SUB must have children even when visits absent");
      assert.equal(subStep.children.length, 1, "non-forEach subflow has 1 child");
      const child = subStep.children[0];
      // WITHOUT the fix: prefix "SUB-undefined-" matched nothing, seed had
      // file=null → child carried placeholder status with EMPTY steps +
      // null currentStep (the silent drop). WITH the fix: broad prefix
      // "SUB-" finds run-state-SUB-1-0.json and merges live state.
      assert.equal(child.status, "running");
      assert.equal(child.currentStep, "WORK",
        "currentStep must come from disk file, not the null-file placeholder");
      assert.equal(child.steps.length, 2,
        "steps must come from disk file, not empty (was 0 without fix — the silent drop)");
      assert.equal(child.steps[0].name, "INIT");
      assert.equal(child.steps[1].status, "running");
      assert.equal(child.forEachItem, null, "forEachItem stays null (non-forEach)");
      assert.equal(child.file, "run-state-SUB-1-0.json", "file back-filled from disk");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });
});

// Regression for the bug where subflow child steps (EXECUTE, BUILD_VERIFY,
// MULTI_AUDIT, etc.) never received sessionId/logFile during execution — only
// after completion. Root cause: the runner's onStepUpdate callback was bound to
// the PARENT engine (main.js:752 config.onStepUpdate), which searches the
// parent's workflow.steps for the stepName. Subflow step names (EXECUTE, etc.)
// aren't in the parent's workflow, so logFile/sessionId updates from onSpawn
// were silently dropped. Fix: _runChildSubflow wraps childDelegates.runAgent to
// inject the child engine's _onAgentStepUpdate via opts.onStepUpdate (runner.js
// prefers opts over config). This test verifies the wiring by providing a mock
// runAgent that fires opts.onStepUpdate BEFORE returning — if the wiring is
// correct, the child's step record has the sessionId; if broken (pre-fix), it's
// null because the mock returns sessionId=null in the result object.
describe("subflow child onStepUpdate routing — live sessionId/logFile during execution", () => {
  it("child step receives sessionId from opts.onStepUpdate (not dropped on parent floor)", withDir(async (runDir) => {
    const flow = simpleFlow({
      SUB: { type: "subflow", flow: "child", transitions: { complete: { done: "completed" } } },
    }, "SUB");

    const delegates = makeMockDelegates({
      config: { projectRoot: runDir, runDir },
    });
    // Custom runAgent that simulates the runner's onSpawn callback: calls
    // opts.onStepUpdate with logFile + sessionId BEFORE returning. The return
    // value deliberately has sessionId=null to prove the live value came from
    // onStepUpdate, not the result object.
    delegates.runAgent = async function(stepName, prompt, system, _sid, _model, opts) {
      this.callLog.push({ type: "agent", stepName });
      if (opts?.onStepUpdate) {
        opts.onStepUpdate({
          stepName,
          logFile: `${runDir}/oc-${stepName}-fake.log`,
          promptFile: `${runDir}/oc-${stepName}-fake.log.prompt`,
          sessionId: `ses_live_${stepName}`,
        });
      }
      return { text: "<AI_STEP_RESULT>done</AI_STEP_RESULT>", ok: true, sessionId: null };
    };
    delegates.loadSubFlow = () => childFlow;

    const engine = new FlowEngine(flow, delegates);
    await engine.run();

    const parent = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
    const subFile = parent.steps[0].subflowRuns[0].file;
    assert.ok(subFile, "subflow run must have a child state file");
    const childState = JSON.parse(readFileSync(join(runDir, subFile), "utf8"));
    const workStep = childState.steps.find(s => s.name === "WORK");
    assert.ok(workStep, "child state must contain the WORK step");
    assert.equal(workStep.sessionId, "ses_live_WORK",
      "child step sessionId must come from onStepUpdate (pre-fix: was null because update routed to parent, which had no WORK step)");
    assert.ok(workStep.logFile && workStep.logFile.includes("oc-WORK-fake.log"),
      "child step logFile must come from onStepUpdate");
  }));
});
