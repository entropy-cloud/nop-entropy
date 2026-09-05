import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { FlowEngine, extractTagFuzzy } from "../src/engine.js";
import { roadmapAllDone } from "../src/roadmap-check.mjs";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

// ── extractTagFuzzy: mismatched open/close tag names ──────────────────────
describe("extractTagFuzzy — tolerates mismatched open/close tag names (§1)", () => {
  const valid = ["done", "nothing", "created", "pass", "fail"];

  it("recovers the exact real-world failure: <AIE_STEP_RESULT>done</AI_STEP_RESULT>", () => {
    // Opening tag has the typo (extra E), closing tag is correct — the pair that
    // previously defeated the \1 backreference and hard-failed the mission.
    assert.equal(
      extractTagFuzzy("<AIE_STEP_RESULT>done</AI_STEP_RESULT>", valid),
      "done",
    );
  });

  it("recovers when both tags carry the typo consistently", () => {
    assert.equal(
      extractTagFuzzy("<AIE_STEP_RESULT>done</AIE_STEP_RESULT>", valid),
      "done",
    );
  });

  it("does NOT match HTML-ish lowercase tags (value whitelist + UPPER guard)", () => {
    assert.equal(extractTagFuzzy("<b>done</b>", valid), null);
    assert.equal(extractTagFuzzy("<span>pass</span>", valid), null);
  });

  it("does NOT match when the value is not a known marker", () => {
    assert.equal(extractTagFuzzy("<AIE_STEP_RESULT>banana</AI_STEP_RESULT>", valid), null);
  });

  it("takes the last matching pair", () => {
    assert.equal(
      extractTagFuzzy("<X_STEP_RESULT>pass</Y_STEP_RESULT> then <AIE_STEP_RESULT>fail</AI_STEP_RESULT>", valid),
      "fail",
    );
  });
});

describe("FlowEngine — mismatched result tag recovers to a valid marker (§1)", () => {
  it("<AIE_STEP_RESULT>done</AI_STEP_RESULT> resolves to completed, no parse fallback", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { done: { done: "completed" }, nothing: { done: "failed" } },
        onUnknown: { done: "failed" },
      },
    });
    const delegates = makeMockDelegates({
      responses: { START: "<AIE_STEP_RESULT>done</AI_STEP_RESULT>" },
    });
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(
      delegates.callLog.filter((c) => c.type === "parse").length,
      0,
      "fuzzy extraction must recover the marker without the LLM parse fallback",
    );
  });
});

// ── null-marker soft-landing (§1 root cause C + §6 observability) ──────────
describe("FlowEngine — null marker soft-lands via onMaxRetries instead of hard-failing", () => {
  it("routes to onMaxRetries.goto when marker is unparseable and no onUnknown given", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { done: { done: "completed" } },
        // NO onUnknown; onMaxRetries route must be used for the null-marker case.
        onMaxRetries: { goto: "RESCUE" },
      },
      RESCUE: {
        type: "agent", prompt: "rescue", resultTag: "AI_STEP_RESULT",
        transitions: { done: { done: "completed" } },
      },
    });
    const delegates = makeMockDelegates({
      responses: {
        START: "no marker at all here",
        RESCUE: "<AI_STEP_RESULT>done</AI_STEP_RESULT>",
      },
    });
    // Make parse fallback also miss so START truly yields a null marker.
    delegates.runParseAgent = async () => ({ text: "still nothing", ok: true });
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed", "null-marker START should soft-land to RESCUE, not fail the run");
  });

  it("completed step persists logFile as a bare filename, not an absolute path", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { done: { done: "completed" } },
      },
    });
    const delegates = makeMockDelegates({
      responses: {
        START: {
          text: "<AI_STEP_RESULT>done</AI_STEP_RESULT>",
          ok: true,
          logFile: "C:\\Work\\example-app\\_tmp\\run-x\\oc-START-1782-abc.log",
          sessionId: "ses_ok",
        },
      },
    });
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();
    assert.equal(result.status, "completed");
    const startStep = engine.workflow?.steps?.find((s) => s.name === "START");
    assert.equal(startStep.logFile, "oc-START-1782-abc.log", "must store basename, not the absolute path");
  });

  it("null-marker failed step still records logFile in workflow (§6 observability)", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { done: { done: "completed" } },
        onUnknown: { done: "failed" },
      },
    });
    const delegates = makeMockDelegates({
      responses: { START: { text: "no marker", ok: true, logFile: "/tmp/oc-START-123-abc.log", sessionId: "ses_x" } },
    });
    delegates.runParseAgent = async () => ({ text: "nope", ok: true });
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed");
    const startStep = engine.workflow?.steps?.find((s) => s.name === "START");
    assert.ok(startStep, "START step must be recorded in workflow");
    assert.equal(startStep.logFile, "oc-START-123-abc.log", "failed step must persist its logFile as a bare filename (relative to runDir)");
    assert.equal(startStep.sessionId, "ses_x", "failed step must persist its sessionId");
  });
});

// ── terminal reconciliation (§1.4-4) ──────────────────────────────────────
describe("roadmapAllDone — helper", () => {
  it("true only when every work item is done", () => {
    assert.equal(roadmapAllDone("## 阶段状态\n\n- 1. A：`done`\n- 2. B：`done`\n"), true);
    assert.equal(roadmapAllDone("## 阶段状态\n\n- 1. A：`done`\n- 2. B：`todo`\n"), false);
    assert.equal(roadmapAllDone("## 阶段状态\n\n(no items)\n"), false);
  });
});

describe("FlowEngine — terminal reconciliation downgrades false failure (§1.4-4)", () => {
  it("failed → completed when roadmap done, no active/draft plans, no open audits", async () => {
    const dir = mkdtempSync(join(tmpdir(), "md-reconcile-"));
    try {
      const roadmapAbs = join(dir, "roadmap.md");
      writeFileSync(roadmapAbs, "## 阶段状态\n\n- 1. A：`done`\n- 2. B：`done`\n");

      const flow = {
        name: "mission-driver", entry: "START", maxTotalSteps: 50, maxCycleVisits: 20,
        reconcileOnTerminal: true,
        steps: {
          START: {
            type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
            transitions: { done: { done: "completed" } },
            onUnknown: { done: "failed" },
          },
        },
      };
      const delegates = makeMockDelegates({
        responses: { START: "no marker → forces failed terminal" },
        vars: { projectRoot: dir, roadmapPath: "roadmap.md" },
        expressionFuncs: { activePlans: () => [], draftPlans: () => [], openAudits: () => [] },
      });
      delegates.runParseAgent = async () => ({ text: "still nothing", ok: true });

      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();
      assert.equal(result.status, "completed", "reconciliation must downgrade the spurious failure");
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it("does NOT downgrade when an active plan still exists", async () => {
    const dir = mkdtempSync(join(tmpdir(), "md-reconcile-"));
    try {
      writeFileSync(join(dir, "roadmap.md"), "## 阶段状态\n\n- 1. A：`done`\n");
      const flow = {
        name: "mission-driver", entry: "START", maxTotalSteps: 50, maxCycleVisits: 20,
        reconcileOnTerminal: true,
        steps: {
          START: {
            type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
            transitions: { done: { done: "completed" } },
            onUnknown: { done: "failed" },
          },
        },
      };
      const delegates = makeMockDelegates({
        responses: { START: "no marker" },
        vars: { projectRoot: dir, roadmapPath: "roadmap.md" },
        expressionFuncs: { activePlans: () => ["p1.md"], draftPlans: () => [], openAudits: () => [] },
      });
      delegates.runParseAgent = async () => ({ text: "nothing", ok: true });
      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();
      assert.equal(result.status, "failed", "must NOT mask a real failure while a plan is still active");
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it("subflow-style flow (no reconcileOnTerminal) is never reconciled", async () => {
    const dir = mkdtempSync(join(tmpdir(), "md-reconcile-"));
    try {
      writeFileSync(join(dir, "roadmap.md"), "## 阶段状态\n\n- 1. A：`done`\n");
      const flow = {
        name: "plan-execution", entry: "START", maxTotalSteps: 50, maxCycleVisits: 20,
        // no reconcileOnTerminal
        steps: {
          START: {
            type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
            transitions: { done: { done: "completed" } },
            onUnknown: { done: "failed" },
          },
        },
      };
      const delegates = makeMockDelegates({
        responses: { START: "no marker" },
        vars: { projectRoot: dir, roadmapPath: "roadmap.md" },
        expressionFuncs: { activePlans: () => [], draftPlans: () => [], openAudits: () => [] },
      });
      delegates.runParseAgent = async () => ({ text: "nothing", ok: true });
      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();
      assert.equal(result.status, "failed", "subflows must keep their real failure status");
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });
});
