// mdo-3 Phase 2 — Fast Run / Skip Steps tests (FSD §3.3.2A / §3.3.3A).
// (a) resolveConfig merges skipSteps + fastRun into effectiveSkip correctly.
// (b) engine run() emits step_skipped (reason "skipSteps") and jumps to the
//     step's first non-retry goto when effectiveSkip contains the step.
// (c) a skipped step with no target completes the run.
import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { resolveConfig } from "../src/config.js";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

// --- config merge -----------------------------------------------------------

function tmpRoot() {
  return mkdtempSync(join(tmpdir(), "skip-steps-"));
}

// Build a valid temp mission (paths exist so loadMission's path validation
// passes) that extends a SELF-CONTAINED temp base.json (avoids depending on the
// repo-root missions/base.json, which is unreachable when npm runs the test
// with CWD=tools/mission-driver). The temp base mirrors the real defaults so
// the fastSkipSteps merge behaviour is exercised identically.
function setupMission(root, body) {
  const missionsDir = join(root, "missions");
  mkdirSync(missionsDir, { recursive: true });
  // Self-contained base with the same defaults as the repo's missions/base.json.
  writeFileSync(join(missionsDir, "base.json"), JSON.stringify({
    model: "test/model",
    agent: "build",
    maxCycles: 8,
    maxInnerCycles: 6,
    maxTotalSteps: 500,
    fastSkipSteps: ["DEEP_AUDIT"],
    contextDir: "docs/context",
    moduleDir: "demo-mod",
    commands: { test: "echo ok" },
  }), "utf8");
  // Create the directories the mission references so path validation passes
  // (base contributes contextDir:"docs/context" and moduleDir:"demo-mod", both
  // validated by loadMission).
  mkdirSync(join(root, "docs", "roadmap"), { recursive: true });
  mkdirSync(join(root, "docs", "plans", "demo"), { recursive: true });
  mkdirSync(join(root, "docs", "context"), { recursive: true });
  mkdirSync(join(root, "demo-mod"), { recursive: true });
  const merged = {
    extends: "base",
    name: "demo",
    roadmapPath: "docs/roadmap",
    plansDir: "docs/plans/demo",
    commands: { test: "echo ok" },
    ...body,
  };
  writeFileSync(join(missionsDir, "demo.json"), JSON.stringify(merged), "utf8");
  return missionsDir;
}

describe("resolveConfig — fast/skip merge (mdo-3 Phase 2)", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { try { rmSync(root, { recursive: true, force: true }); } catch {} });

  it("effectiveSkip is empty when neither --fast nor --skip-steps given", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({ dir: root, missionsDir, mission: "demo" });
    assert.ok(cfg.effectiveSkip instanceof Set);
    assert.equal(cfg.effectiveSkip.size, 0);
    assert.deepEqual(cfg.fastSkipSteps, ["DEEP_AUDIT"]);
  });

  it("--fast folds fastSkipSteps into effectiveSkip", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({ dir: root, missionsDir, mission: "demo", fastRun: true });
    assert.ok(cfg.effectiveSkip.has("DEEP_AUDIT"));
    assert.equal(cfg.fastRun, true);
  });

  it("--skip-steps X,Y adds X,Y to effectiveSkip", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({ dir: root, missionsDir, mission: "demo", skipSteps: "ALPHA,BETA" });
    assert.ok(cfg.effectiveSkip.has("ALPHA"));
    assert.ok(cfg.effectiveSkip.has("BETA"));
    assert.equal(cfg.effectiveSkip.has("DEEP_AUDIT"), false, "no --fast → no fastSkipSteps");
  });

  it("--fast + --skip-steps form a union", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({ dir: root, missionsDir, mission: "demo", fastRun: true, skipSteps: "ALPHA" });
    assert.ok(cfg.effectiveSkip.has("DEEP_AUDIT"));
    assert.ok(cfg.effectiveSkip.has("ALPHA"));
  });

  it("mission.fastSkipSteps override replaces the base default", () => {
    const missionsDir = setupMission(root, { fastSkipSteps: ["ONLY_THIS"] });
    const cfg = resolveConfig({ dir: root, missionsDir, mission: "demo", fastRun: true });
    assert.deepEqual(cfg.fastSkipSteps, ["ONLY_THIS"]);
    assert.ok(cfg.effectiveSkip.has("ONLY_THIS"));
    assert.equal(cfg.effectiveSkip.has("DEEP_AUDIT"), false, "base default replaced, not merged");
  });
});

// --- engine skip behavior ---------------------------------------------------

describe("FlowEngine — skipSteps (mdo-3 Phase 2)", () => {
  it("skips a step in effectiveSkip, emits step_skipped, jumps to first goto", async () => {
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        transitions: {
          pending: { goto: "WORK" },   // first non-retry goto target
          done: { done: "completed" },
        },
      },
      WORK: {
        type: "agent",
        prompt: "work",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "CHECK");

    const events = [];
    const delegates = makeMockDelegates({
      responses: {
        // CHECK is skipped → never invoked. WORK runs once.
        CHECK: () => { throw new Error("CHECK must be skipped, not executed"); },
        WORK: { text: "<R>ok</R>", ok: true },
      },
    });
    // Capture emitted events by wrapping _emitEvent.
    const engine = new FlowEngine(flow, delegates);
    engine.eventsFile = null; // _emitEvent is a no-op without eventsFile
    const origEmit = engine._emitEvent.bind(engine);
    engine._emitEvent = (type, data) => { events.push({ type, ...(data || {}) }); return origEmit(type, data); };
    // Put CHECK in the skip set.
    delegates.config.effectiveSkip = new Set(["CHECK"]);

    const result = await engine.run();

    assert.equal(result.status, "completed");
    const skipped = events.filter((e) => e.type === "step_skipped");
    assert.equal(skipped.length, 1, "exactly one step_skipped event");
    assert.equal(skipped[0].step, "CHECK");
    assert.equal(skipped[0].reason, "skipSteps");
    // WORK must have run (the goto target), proving the jump happened.
    const workAgentCalls = delegates.callLog.filter((c) => c.stepName === "WORK");
    assert.equal(workAgentCalls.length, 1, "WORK executed once after skip-jump");
    // CHECK must NOT have been dispatched.
    assert.equal(delegates.callLog.filter((c) => c.stepName === "CHECK").length, 0);
  });

  it("skips a step with no target → run completes", async () => {
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        // Only a done transition (no goto) → firstNonRetryTarget falls to
        // {done:"completed"} default.
        transitions: { pass: { done: "completed" } },
      },
    }, "CHECK");

    const events = [];
    const delegates = makeMockDelegates({
      responses: {
        CHECK: () => { throw new Error("CHECK must be skipped"); },
      },
    });
    const engine = new FlowEngine(flow, delegates);
    engine.eventsFile = null;
    const origEmit = engine._emitEvent.bind(engine);
    engine._emitEvent = (type, data) => { events.push({ type, ...(data || {}) }); return origEmit(type, data); };
    delegates.config.effectiveSkip = new Set(["CHECK"]);

    const result = await engine.run();
    assert.equal(result.status, "completed", "skipped terminal step with no goto → completed");
    const skipped = events.filter((e) => e.type === "step_skipped");
    assert.equal(skipped.length, 1);
    assert.equal(delegates.callLog.filter((c) => c.stepName === "CHECK").length, 0);
  });

  it("respects otherwise.goto when the skipped step has no transition goto", async () => {
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        transitions: { fail: { retry: "CHECK", maxRetries: 2 } }, // only retry → no goto here
        otherwise: { goto: "FALLBACK" },
      },
      FALLBACK: {
        type: "agent",
        prompt: "fallback",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "CHECK");

    const delegates = makeMockDelegates({
      responses: {
        CHECK: () => { throw new Error("CHECK must be skipped"); },
        FALLBACK: { text: "<R>ok</R>", ok: true },
      },
    });
    const engine = new FlowEngine(flow, delegates);
    engine.eventsFile = null;
    delegates.config.effectiveSkip = new Set(["CHECK"]);

    const result = await engine.run();
    assert.equal(result.status, "completed");
    assert.equal(delegates.callLog.filter((c) => c.stepName === "FALLBACK").length, 1, "otherwise.goto honored on skip");
  });
});
