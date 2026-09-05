import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import {
  cmdDraftMission,
  extractBriefGate,
  __setRunnerFactoryForTest,
} from "../src/main.js";

// ── Helpers ───────────────────────────────────────────────────────────────

const __dirname_test = dirname(fileURLToPath(import.meta.url));
const PROMPTS_DIR = join(__dirname_test, "..", "prompts");

function makeTmpProject() {
  const root = mkdtempSync(join(tmpdir(), "md-brief-gate-"));
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

/** Capture console.log output during `fn`. Returns the joined string. */
async function captureStdout(fn) {
  const lines = [];
  const prev = console.log;
  console.log = (...args) => lines.push(args.join(" "));
  try {
    await fn();
  } finally {
    console.log = prev;
  }
  return lines.join("\n");
}

// ── Case A — pure function layer ──────────────────────────────────────────

describe("extractBriefGate (pure function, WI2 §4.2.2)", () => {
  it("<BRIEF_GATE>pass</BRIEF_GATE> → { gate: 'pass', reason: null }", () => {
    const r = extractBriefGate("<BRIEF_GATE>pass</BRIEF_GATE>");
    assert.deepEqual(r, { gate: "pass", reason: null });
  });

  it("<BRIEF_GATE>blocked</BRIEF_GATE> + reason → { gate: 'blocked', reason }", () => {
    const r = extractBriefGate(
      "<BRIEF_GATE>blocked</BRIEF_GATE><BRIEF_GATE_REASON>desc too vague</BRIEF_GATE_REASON>",
    );
    assert.deepEqual(r, { gate: "blocked", reason: "desc too vague" });
  });

  it("case-insensitive + tolerates inner whitespace: '<BRIEF_GATE> PASS </BRIEF_GATE>'", () => {
    const r = extractBriefGate("<BRIEF_GATE> PASS </BRIEF_GATE>");
    assert.deepEqual(r, { gate: "pass", reason: null });
  });

  it("no gate marker (only BRIEF_FILE) → { gate: null, reason: null } (backward compat)", () => {
    const r = extractBriefGate("<BRIEF_FILE>docs/backlog/x-brief.md</BRIEF_FILE>");
    assert.deepEqual(r, { gate: null, reason: null });
  });

  it("non-string inputs (undefined / null / 123) → { gate: null, reason: null }", () => {
    assert.deepEqual(extractBriefGate(undefined), { gate: null, reason: null });
    assert.deepEqual(extractBriefGate(null), { gate: null, reason: null });
    assert.deepEqual(extractBriefGate(123), { gate: null, reason: null });
  });

  it("blocked without BRIEF_GATE_REASON → reason null (does not throw)", () => {
    const r = extractBriefGate("<BRIEF_GATE>blocked</BRIEF_GATE>");
    assert.deepEqual(r, { gate: "blocked", reason: null });
  });

  it("invalid gate value ('unknown') → { gate: null, reason: null } (regex only accepts pass|blocked)", () => {
    const r = extractBriefGate("<BRIEF_GATE>unknown</BRIEF_GATE>");
    assert.deepEqual(r, { gate: null, reason: null });
  });

  it("multi-line reason preserved (locks the /s dotall flag): '<BRIEF_GATE_REASON>line1\\nline2</BRIEF_GATE_REASON>'", () => {
    const r = extractBriefGate(
      "<BRIEF_GATE>blocked</BRIEF_GATE><BRIEF_GATE_REASON>line1\nline2</BRIEF_GATE_REASON>",
    );
    assert.deepEqual(r, { gate: "blocked", reason: "line1\nline2" });
  });

  it("whitespace-only reason normalizes to null (never empty string): '<BRIEF_GATE_REASON>   </BRIEF_GATE_REASON>'", () => {
    const r = extractBriefGate(
      "<BRIEF_GATE>pass</BRIEF_GATE><BRIEF_GATE_REASON>   </BRIEF_GATE_REASON>",
    );
    assert.deepEqual(r, { gate: "pass", reason: null });
  });

  // mdr-remediate-3 N1 — ANSI-wrapped marker regression (extractBriefGate now
  // calls stripAnsiControl before matching, mirroring the engine-layer
  // discipline). Real brief-agent output is frequently log-colored; without
  // stripping the strict `[^<]+`-equivalent matchers fail and the gate
  // silently degrades to null (backward-compat Stage 2 runs unconditionally —
  // defeating WI2's gate contract). Cross-ref test/ansi-and-mixedcase-tag.test.js:108.
  it("N1-A: ANSI-wrapped gate+reason markers still parse (\\x1b[32m<BRIEF_GATE>pass...\\x1b[0m)", () => {
    const r = extractBriefGate(
      "\x1b[32m<BRIEF_GATE>pass</BRIEF_GATE>\x1b[0m\x1b[31m<BRIEF_GATE_REASON>blocked reason</BRIEF_GATE_REASON>\x1b[0m",
    );
    assert.deepEqual(r, { gate: "pass", reason: "blocked reason" });
  });

  it("N1-B: ANSI intermixed INSIDE tag characters <BRIEF\\x1b[0m_GATE> still matches after strip", () => {
    // CSI bytes between tag-name characters would break the literal `<BRIEF_GATE>`
    // open-tag match; stripAnsiControl removes them first so the tag reassembles.
    const r = extractBriefGate("<BRIEF\x1b[0m_GATE>pass</BRIEF_GATE>");
    assert.deepEqual(r, { gate: "pass", reason: null });
  });
});

// ── Case B — cmdDraftMission integration, gate=blocked ─────────────────────

describe("cmdDraftMission — brief gate=blocked (WI2)", () => {
  let prevFactory = null;
  let prevExitCode = undefined;

  beforeEach(() => {
    prevFactory = __setRunnerFactoryForTest(null);
    prevExitCode = process.exitCode;
    process.exitCode = undefined;
  });
  afterEach(() => {
    __setRunnerFactoryForTest(prevFactory);
    process.exitCode = prevExitCode;
  });

  it("gate=blocked: Stage 2 NOT run, state.status=blocked, briefGate=blocked, exitCode stays unset", async () => {
    const root = makeTmpProject();
    const jobDir = join(root, "_tmp", "draft-gate-blocked");
    try {
      const { runner, calls } = makeFakeRunner({
        "mission-brief":
          "<BRIEF_FILE>docs/backlog/x-brief.md</BRIEF_FILE>\n<BRIEF_GATE>blocked</BRIEF_GATE>\n<BRIEF_GATE_REASON>desc too vague</BRIEF_GATE_REASON>",
      });
      __setRunnerFactoryForTest(() => runner);

      const stdout = await captureStdout(() =>
        cmdDraftMission("optimize", { dir: root, draftJobDir: jobDir }),
      );

      // Stage 2 not entered: only mission-brief called once.
      assert.equal(calls.length, 1, "Stage 2 (draft-mission) must NOT run on blocked");
      assert.equal(calls[0].stepName, "mission-brief");

      // gate-blocked is a normal workflow outcome — exitCode must stay unset (0).
      assert.equal(process.exitCode, undefined, "exitCode must NOT be set for gate-blocked");

      // draft-state.json reflects blocked.
      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(state.status, "blocked");
      assert.equal(state.phase, "brief_done");
      assert.equal(state.briefGate, "blocked");
      assert.equal(state.briefGateReason, "desc too vague");
      assert.ok(state.endedAt, "endedAt must be present on blocked");
      // Must NOT have written Stage 2 / completed artifacts.
      assert.notEqual(state.phase, "draft");
      assert.notEqual(state.phase, "completed");
      assert.equal(state.missionName, undefined, "must not write missionName");
      assert.equal(state.missionFile, undefined, "must not write missionFile");

      // console.log banner — proves the user-facing gate message went out.
      assert.match(stdout, /\[BRIEF GATE\] blocked/, "stdout must announce blocked gate");
      assert.match(stdout, /desc too vague/, "stdout must include reason");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Case C — cmdDraftMission integration, gate=pass ────────────────────────

describe("cmdDraftMission — brief gate=pass (WI2)", () => {
  let prevFactory = null;

  beforeEach(() => {
    prevFactory = __setRunnerFactoryForTest(null);
  });
  afterEach(() => {
    __setRunnerFactoryForTest(prevFactory);
  });

  it("gate=pass: Stage 2 runs, state.briefGate=pass + phase/status=completed", async () => {
    const root = makeTmpProject();
    const jobDir = join(root, "_tmp", "draft-gate-pass");
    try {
      const { runner, calls } = makeFakeRunner({
        "mission-brief":
          "<BRIEF_FILE>docs/backlog/my-goal-brief.md</BRIEF_FILE>\n<BRIEF_GATE>pass</BRIEF_GATE>",
        "draft-mission":
          "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<MISSION_FILE></MISSION_FILE>",
      });
      __setRunnerFactoryForTest(() => runner);

      await cmdDraftMission("add audit count to dashboard", {
        dir: root,
        draftJobDir: jobDir,
      });

      // Both stages run.
      assert.equal(calls.length, 2, "brief + draft = 2 runAgent calls");
      assert.equal(calls[0].stepName, "mission-brief");
      assert.equal(calls[1].stepName, "draft-mission");

      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(state.briefGate, "pass");
      assert.equal(state.phase, "completed");
      assert.equal(state.status, "completed");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Case D — cmdDraftMission integration, gate=null backward compat ────────

describe("cmdDraftMission — brief gate=null backward compat (WI2 §5.3)", () => {
  let prevFactory = null;

  beforeEach(() => {
    prevFactory = __setRunnerFactoryForTest(null);
  });
  afterEach(() => {
    __setRunnerFactoryForTest(prevFactory);
  });

  it("gate=null (no marker): Stage 2 runs, state.briefGate=null (explicit)", async () => {
    const root = makeTmpProject();
    const jobDir = join(root, "_tmp", "draft-gate-null");
    try {
      const { runner, calls } = makeFakeRunner({
        // Old brief — no <BRIEF_GATE> marker.
        "mission-brief": "<BRIEF_FILE>docs/backlog/legacy-brief.md</BRIEF_FILE>",
        "draft-mission":
          "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<MISSION_FILE></MISSION_FILE>",
      });
      __setRunnerFactoryForTest(() => runner);

      await cmdDraftMission("some legacy goal", { dir: root, draftJobDir: jobDir });

      // Stage 2 still runs — backward compatible.
      assert.equal(calls.length, 2, "brief + draft = 2 runAgent calls");
      assert.equal(calls[0].stepName, "mission-brief");
      assert.equal(calls[1].stepName, "draft-mission");

      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      // Explicit null — distinguishes "Stage 1 ran but AI emitted no marker"
      // from "Stage 1 was skipped".
      assert.equal(state.briefGate, null);
      assert.equal(state.briefGateReason, null);
      assert.equal(state.phase, "completed");
      assert.equal(state.status, "completed");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Case E — --skip-brief path regression ─────────────────────────────────

describe("cmdDraftMission — skipBrief no regression (WI2 §5.3)", () => {
  let prevFactory = null;

  beforeEach(() => {
    prevFactory = __setRunnerFactoryForTest(null);
  });
  afterEach(() => {
    __setRunnerFactoryForTest(prevFactory);
  });

  it("skipBrief=true: Stage 1 skipped, only draft-mission called, briefGate absent or null", async () => {
    const root = makeTmpProject();
    const jobDir = join(root, "_tmp", "draft-gate-skip");
    try {
      const { runner, calls } = makeFakeRunner({
        "draft-mission": "<AI_STEP_RESULT>created</AI_STEP_RESULT>",
      });
      __setRunnerFactoryForTest(() => runner);

      await cmdDraftMission("add audit count", {
        dir: root,
        draftJobDir: jobDir,
        skipBrief: true,
      });

      // Stage 1 entirely skipped — only draft-mission runs.
      assert.equal(calls.length, 1, "skipBrief → single draft runAgent only");
      assert.equal(calls[0].stepName, "draft-mission");

      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(state.status, "completed");
      assert.equal(state.phase, "completed");
      // Stage 1 was skipped so the briefGate patch was never written. Tolerate
      // either "field absent" or "=== null" (implementation-detail dependent).
      assert.ok(
        !("briefGate" in state) || state.briefGate === null,
        "briefGate must be absent or null on skipBrief path",
      );
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Case F — prompt file grep anchor ──────────────────────────────────────

describe("mission-brief.md gate marker contract (WI2 §4.2.1)", () => {
  it("prompts/mission-brief.md contains <BRIEF_GATE> and <BRIEF_GATE_REASON> literals", () => {
    const promptPath = join(PROMPTS_DIR, "mission-brief.md");
    assert.ok(existsSync(promptPath), "mission-brief.md must exist");
    const text = readFileSync(promptPath, "utf8");
    assert.ok(text.match(/<BRIEF_GATE>/g) !== null,
      "mission-brief.md must contain the <BRIEF_GATE> marker literal");
    assert.ok(text.match(/<BRIEF_GATE_REASON>/g) !== null,
      "mission-brief.md must contain the <BRIEF_GATE_REASON> marker literal");
  });
});
