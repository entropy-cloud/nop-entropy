import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates } from "./helpers.js";
import { mkdtempSync, rmSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

// WI1 — audit 计数落盘 (Plan mdo-step-audit-1). Pins design §5.2 写法 2:
// increment lives in _wfOpen; the maxAuditRounds gate reads the PRE-increment
// workflow.auditRound with `>=`, and the gate stays BEFORE totalSteps++ and
// _wfOpen so an exhausted iteration leaves NO phantom step record, NO extra
// stepCount, and NO step_started event. Subflow children (isSubflow=true) do
// not increment the counter. Legacy run-state without auditRound reads as 0.

function auditFlow({ maxAuditRounds, auditEntry = "AUDIT", entry = "START", pingPongWindow = 20 }) {
  return {
    name: "audit-test",
    maxTotalSteps: 50,
    maxCycleVisits: 20,
    maxAuditRounds,
    auditEntry,
    pingPongWindow,
    entry,
    steps: {
      START: {
        type: "agent",
        prompt: "start",
        resultTag: "AI_STEP_RESULT",
        transitions: { ok: { goto: "AUDIT" } },
      },
      AUDIT: {
        type: "agent",
        prompt: "audit",
        resultTag: "AI_STEP_RESULT",
        transitions: { ok: { goto: "START" } },
      },
    },
  };
}

const OK_AGENT = { text: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>", ok: true };

describe("WI1 audit count persistence (Plan mdo-step-audit-1)", () => {
  it("Case A: maxAuditRounds=2, two audit visits → auditRound===2 persisted", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "audit-a-"));
    try {
      const flow = auditFlow({ maxAuditRounds: 2 });
      const delegates = makeMockDelegates({
        responses: { START: OK_AGENT, AUDIT: OK_AGENT },
        config: { projectRoot: runDir, runDir },
      });
      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();
      assert.equal(result.status, "completed");

      const state = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
      assert.equal(state.auditRound, 2,
        "auditRound must equal the number of audit visits actually executed");
      assert.equal(state.maxAuditRounds, 2,
        "maxAuditRounds snapshot must be persisted for consumers");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("Case B: 写法 2 gate boundary — 3rd audit entry completes WITHOUT bumping round/stepCount/step record", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "audit-b-"));
    try {
      const flow = auditFlow({ maxAuditRounds: 2 });
      const delegates = makeMockDelegates({
        responses: { START: OK_AGENT, AUDIT: OK_AGENT },
        config: { projectRoot: runDir, runDir },
      });
      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();

      // Gate fired on 3rd AUDIT entry with pre-increment round=2 → completed.
      assert.equal(result.status, "completed");

      const state = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
      assert.equal(state.auditRound, 2,
        "gate-stopped 3rd iteration must NOT increment auditRound (写法 2: gate reads pre-increment value with >=)");

      const auditSteps = state.steps.filter((s) => s.name === "AUDIT");
      assert.equal(auditSteps.length, 2,
        "only the 2 actually-executed AUDIT visits should be in workflow.steps; the gate-stopped 3rd must NOT leave a phantom record");

      // Expected trace: 1=START, 2=AUDIT(r1), 3=START, 4=AUDIT(r2), 5=START,
      // then gate fires at AUDIT-visit-3 BEFORE totalSteps++ → stepCount=5.
      assert.equal(result.stepCount, 5,
        "stepCount must NOT be incremented by the gate-stopped iteration");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("Case C: subflow (isSubflow=true) with entry matching auditEntry name does NOT increment auditRound", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "audit-c-"));
    try {
      // Child flow whose entry is named "AUDIT" — same name a parent's
      // auditEntry would have. The isSubflow guard in _wfOpen must block
      // the increment on the child engine.
      const childFlow = {
        name: "audit-child",
        maxTotalSteps: 10,
        maxCycleVisits: 5,
        entry: "AUDIT",
        steps: {
          AUDIT: {
            type: "agent",
            prompt: "child audit step",
            resultTag: "AI_STEP_RESULT",
            transitions: { ok: { done: "completed" } },
          },
        },
      };
      const delegates = makeMockDelegates({
        responses: { AUDIT: OK_AGENT },
        config: { projectRoot: runDir, runDir, isSubflow: true },
      });
      const engine = new FlowEngine(childFlow, delegates);
      const result = await engine.run();
      assert.equal(result.status, "completed");

      const state = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
      assert.equal(state.auditRound, 0,
        "child engine (isSubflow=true) must NOT increment auditRound even when its entry name matches a parent's auditEntry");
      assert.equal(state.maxAuditRounds, 0,
        "maxAuditRounds snapshot for child flow without the field reads as 0");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("Case D: legacy run-state without auditRound field reads as 0 and engine tolerates missing field", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "audit-d-"));
    try {
      // Simulate a legacy run-state.json written by an older engine version
      // that predates the auditRound field. External consumers (monitor,
      // analyze) parse this with the defensive `|| 0` pattern.
      const legacyFile = join(runDir, "run-state-legacy.json");
      const legacy = {
        missionName: "old-mission",
        flowName: "old-flow",
        runId: "legacy-run",
        runDir,
        pid: 99999,
        status: "running",
        startedAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
        endedAt: null,
        currentStep: null,
        steps: [],
        // NOTE: no auditRound / maxAuditRounds field
      };
      writeFileSync(legacyFile, JSON.stringify(legacy, null, 2));

      const loaded = JSON.parse(readFileSync(legacyFile, "utf8"));
      const round = (loaded && loaded.auditRound) || 0;
      const max = (loaded && loaded.maxAuditRounds) || 0;
      assert.equal(round, 0, "legacy state without auditRound must read as 0");
      assert.equal(max, 0, "legacy state without maxAuditRounds must read as 0");

      // Engine-side resilience: simulate a workflow missing the field at
      // init time (e.g. a future resume path loading legacy state) and
      // verify run() does not throw and treats auditRound as 0.
      const flow = auditFlow({ maxAuditRounds: 1 });
      const delegates = makeMockDelegates({
        responses: { START: OK_AGENT, AUDIT: OK_AGENT },
        config: { projectRoot: runDir, runDir },
      });
      const engine = new FlowEngine(flow, delegates);
      const origInit = engine._initWorkflow.bind(engine);
      engine._initWorkflow = function () {
        origInit();
        delete engine.workflow.auditRound;
      };
      const result = await engine.run();
      assert.equal(result.status, "completed",
        "engine must complete normally when workflow.auditRound was missing at init");
      assert.ok(engine.workflow.auditRound >= 1,
        "auditRound must be (re)established on first AUDIT entry via (undefined||0)+1");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });
});
