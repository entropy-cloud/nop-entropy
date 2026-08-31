import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, readFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { cmdDraftMission, __setRunnerFactoryForTest } from "../src/main.js";

// ── Helpers ───────────────────────────────────────────────────────────────

function makeTmpProject() {
  const root = mkdtempSync(join(tmpdir(), "md-brief-"));
  mkdirSync(join(root, "_tmp"), { recursive: true });
  mkdirSync(join(root, "missions"), { recursive: true });
  return root;
}

/**
 * Build a fake runner that records every runAgent call and returns canned text.
 * `responses` maps stepName → output text (or a function(stepName, prompt) → text).
 */
function makeFakeRunner(responses) {
  const calls = [];
  const runner = {
    async runAgent(stepName, prompt, system, sessionId) {
      calls.push({ stepName, prompt, system, sessionId });
      const r = responses[stepName];
      const text = typeof r === "function" ? r(stepName, prompt) : r;
      return { text: text ?? "" };
    },
    async close() {},
  };
  return { runner, calls };
}

// ── Two-stage draft (brief → draft) ────────────────────────────────────────

describe("cmdDraftMission — two-stage brief→draft (mdo-4 P2)", () => {
  let prevFactory = null;

  beforeEach(() => {
    prevFactory = __setRunnerFactoryForTest(null);
  });
  afterEach(() => {
    __setRunnerFactoryForTest(prevFactory);
  });

  it("runs brief then draft serially; briefPath parsed + injected into draft prompt", async () => {
    const root = makeTmpProject();
    const jobDir = join(root, "_tmp", "draft-test-mission-draft");
    try {
      const { runner, calls } = makeFakeRunner({
        "mission-brief": "<BRIEF_FILE>docs/backlog/my-goal-brief.md</BRIEF_FILE>",
        "draft-mission": "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<MISSION_FILE></MISSION_FILE>",
      });
      __setRunnerFactoryForTest(() => runner);

      await cmdDraftMission("build a cool thing", {
        dir: root,
        draftJobDir: jobDir,
      });

      // Two runAgent calls: brief first, then draft
      assert.equal(calls.length, 2, "brief + draft = 2 runAgent calls");
      assert.equal(calls[0].stepName, "mission-brief");
      assert.equal(calls[1].stepName, "draft-mission");

      // draft prompt contains the resolved briefPath (template var injected)
      assert.match(calls[1].prompt, /docs\/backlog\/my-goal-brief\.md/, "draft prompt has briefPath");

      // draft-state.json reflects the phase progression + completed
      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(state.status, "completed");
      assert.equal(state.phase, "completed");
      assert.equal(state.briefPath, "docs/backlog/my-goal-brief.md");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("brief failure aborts before draft (no second runAgent, state=failed)", async () => {
    const root = makeTmpProject();
    const jobDir = join(root, "_tmp", "draft-fail-mission-draft");
    try {
      const { runner, calls } = makeFakeRunner({
        "mission-brief"() {
          throw new Error("brief agent crashed");
        },
      });
      __setRunnerFactoryForTest(() => runner);

      await assert.rejects(
        () => cmdDraftMission("something", { dir: root, draftJobDir: jobDir }),
        /brief agent crashed/,
      );

      // Only the brief call was attempted; draft never ran
      assert.equal(calls.length, 1);
      assert.equal(calls[0].stepName, "mission-brief");

      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(state.status, "failed");
      assert.equal(state.phase, "brief");
      assert.match(state.error, /brief agent crashed/);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("flowHint is injected into both brief and draft prompts", async () => {
    const root = makeTmpProject();
    const jobDir = join(root, "_tmp", "draft-flow-mission-draft");
    try {
      const { runner, calls } = makeFakeRunner({
        "mission-brief": "<BRIEF_FILE>docs/backlog/x-brief.md</BRIEF_FILE>",
        "draft-mission": "<AI_STEP_RESULT>created</AI_STEP_RESULT>",
      });
      __setRunnerFactoryForTest(() => runner);

      await cmdDraftMission("goal", {
        dir: root,
        draftJobDir: jobDir,
        flowHint: "integration-test",
      });

      // Both prompts should carry the flowHint template var resolved value
      assert.match(calls[0].prompt, /integration-test/, "brief prompt has flowHint");
      assert.match(calls[1].prompt, /integration-test/, "draft prompt has flowHint");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── skipBrief backward compatibility ───────────────────────────────────────

describe("cmdDraftMission — skipBrief single-stage (backward compat)", () => {
  let prevFactory = null;

  beforeEach(() => {
    prevFactory = __setRunnerFactoryForTest(null);
  });
  afterEach(() => {
    __setRunnerFactoryForTest(prevFactory);
  });

  it("skipBrief=true skips brief; only one runAgent (draft)", async () => {
    const root = makeTmpProject();
    const jobDir = join(root, "_tmp", "draft-skip-mission-draft");
    try {
      const { runner, calls } = makeFakeRunner({
        "draft-mission": "<AI_STEP_RESULT>created</AI_STEP_RESULT>",
      });
      __setRunnerFactoryForTest(() => runner);

      await cmdDraftMission("legacy single stage", {
        dir: root,
        draftJobDir: jobDir,
        skipBrief: true,
      });

      assert.equal(calls.length, 1, "skipBrief → single draft runAgent only");
      assert.equal(calls[0].stepName, "draft-mission");
      // briefPath template var resolves to empty; no *-brief.md path injected
      assert.ok(!calls[0].prompt.includes("-brief.md"), "no brief file path in draft prompt");

      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(state.status, "completed");
      assert.equal(state.phase, "completed");
      assert.equal(state.briefPath, null);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("skipBrief draft failure writes failed state with phase=draft", async () => {
    const root = makeTmpProject();
    const jobDir = join(root, "_tmp", "draft-skipfail-mission-draft");
    try {
      const { runner, calls } = makeFakeRunner({
        "draft-mission"() {
          throw new Error("draft boom");
        },
      });
      __setRunnerFactoryForTest(() => runner);

      await assert.rejects(
        () => cmdDraftMission("fail draft", { dir: root, draftJobDir: jobDir, skipBrief: true }),
        /draft boom/,
      );

      assert.equal(calls.length, 1);
      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(state.status, "failed");
      assert.equal(state.phase, "draft");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});
