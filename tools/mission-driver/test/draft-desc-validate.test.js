import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, readFileSync, writeFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import {
  cmdDraftMission,
  __setRunnerFactoryForTest,
  validateDraftDesc,
} from "../src/main.js";
import { readDraftJob } from "../src/draft-job.mjs";

// ── Helpers ───────────────────────────────────────────────────────────────

function makeTmpProject() {
  const root = mkdtempSync(join(tmpdir(), "md-desc-val-"));
  mkdirSync(join(root, "missions"), { recursive: true });
  return root;
}

function makeFakeRunner() {
  const calls = [];
  const runner = {
    async runAgent(stepName, prompt, system, sessionId) {
      calls.push({ stepName, prompt, system, sessionId });
      return { text: "<AI_STEP_RESULT>created</AI_STEP_RESULT>" };
    },
    async close() {},
  };
  return { runner, calls };
}

/** Capture console.error output during `fn`. Returns the joined string. */
async function captureStderr(fn) {
  const lines = [];
  const prev = console.error;
  console.error = (...args) => lines.push(args.join(" "));
  try {
    await fn();
  } finally {
    console.error = prev;
  }
  return lines.join("\n");
}

// ── Case A — pure function layer ──────────────────────────────────────────

describe("validateDraftDesc (pure function)", () => {
  it('rejects "" and "   " as empty', () => {
    const a = validateDraftDesc("");
    assert.equal(a.ok, false);
    assert.match(a.reason, /empty/i);

    const b = validateDraftDesc("   ");
    assert.equal(b.ok, false);
    assert.match(b.reason, /empty/i);
  });

  it('rejects "d" as too short', () => {
    const v = validateDraftDesc("d");
    assert.equal(v.ok, false);
    assert.match(v.reason, /too short/i);
  });

  it('rejects placeholder words: all 9 regex alternatives + case-insensitivity anchors (TODO / N/A)', () => {
    for (const desc of ["test", "asdf", "foo", "bar", "todo", "xxx", "none", "null", "n/a", "TODO", "N/A"]) {
      const v = validateDraftDesc(desc);
      assert.equal(v.ok, false, `${desc} should be rejected`);
      assert.match(v.reason, /placeholder/i);
    }
  });

  it('accepts "add audit count" as a valid description', () => {
    const v = validateDraftDesc("add audit count");
    assert.equal(v.ok, true);
  });

  it('honours an explicit minLen override (8): "add" too short', () => {
    const v = validateDraftDesc("add", 8);
    assert.equal(v.ok, false);
    assert.match(v.reason, /too short/i);
  });

  it('non-finite minLen ("garbage") falls back to default 4 — "add audit count" passes', () => {
    const v = validateDraftDesc("add audit count", "garbage");
    assert.equal(v.ok, true);
  });

  it('non-finite minLen falls back to default 4 — "ad" (len 2) still blocked', () => {
    const v = validateDraftDesc("ad", "garbage");
    assert.equal(v.ok, false);
    assert.match(v.reason, /too short/i);
  });
});

// ── Case B1 — cmdDraftMission integration (no draftJobDir) ────────────────

describe("cmdDraftMission — validation blocks Stage 1 (no draftJobDir)", () => {
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

  it('rejects "d": runAgent NOT called, exitCode === 1, stderr has reason + hint', async () => {
    const root = makeTmpProject();
    try {
      const { runner, calls } = makeFakeRunner();
      __setRunnerFactoryForTest(() => runner);

      const stderr = await captureStderr(() =>
        cmdDraftMission("d", { dir: root }),
      );

      assert.equal(calls.length, 0, "Stage 1 (mission-brief) must not run");
      assert.equal(process.exitCode, 1, "exitCode must be 1");
      assert.match(stderr, /\[DRAFT VALIDATION\]/, "stderr has validation tag");
      assert.match(stderr, /too short/i, "stderr has reason");
      assert.match(stderr, /Hint:/, "stderr has hint");

      // mdr-remediate-3 A1 sibling assertion: direct CLI path (no
      // --draft-job-dir) never writes any draft-state.json — writeDraftState
      // self-guards via `if (!stateFile) return;`. Walks the whole tmpdir
      // tree to be robust against any future sub-path change.
      const seen = [];
      const walk = (dir) => {
        try {
          for (const ent of readdirSync(dir, { withFileTypes: true })) {
            const p = join(dir, ent.name);
            if (ent.isDirectory()) walk(p);
            else if (ent.name === "draft-state.json") seen.push(p);
          }
        } catch { /* ignore */ }
      };
      walk(root);
      assert.equal(seen.length, 0,
        "direct CLI path (no --draft-job-dir) must not write any draft-state.json");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Case B2 — cmdDraftMission integration (draftJobDir path) ──────────────

describe("cmdDraftMission — validation writes terminal failed/rejected state (draftJobDir)", () => {
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

  it('rejects "d" with draftJobDir set: writes {status:"failed", phase:"rejected", error, desc:"d"} (mdr-remediate-3 A1)', async () => {
    const root = makeTmpProject();
    // Use a realistic `_tmp/draft-*-mission-draft` layout so readDraftJob's
    // resolver (draft-job.mjs:114-120) can locate the job — mirrors what
    // startDraftJob produces in production.
    const jobId = "draft-test-reject-mission-draft";
    const jobDir = join(root, "_tmp", jobId);
    mkdirSync(jobDir, { recursive: true });
    try {
      // Pre-populate the initial running state, mirroring startDraftJob's
      // shape (draft-job.mjs:74-86). This lets us verify writeDraftState's
      // merge semantics preserve `desc` across the failed-state patch.
      writeFileSync(
        join(jobDir, "draft-state.json"),
        JSON.stringify({
          jobId,
          status: "running",
          startedAt: new Date().toISOString(),
          desc: "d",
          phase: "brief",
          flowHint: null,
          targetFile: null,
        }, null, 2),
      );

      const { runner, calls } = makeFakeRunner();
      __setRunnerFactoryForTest(() => runner);

      const stderr = await captureStderr(() =>
        cmdDraftMission("d", { dir: root, draftJobDir: jobDir }),
      );

      // Pre-existing invariants (from old B2): Stage 1 not run, exit 1,
      // stderr carries the validation tag.
      assert.equal(calls.length, 0, "Stage 1 (mission-brief) must not run");
      assert.equal(process.exitCode, 1, "exitCode must be 1");
      assert.match(stderr, /\[DRAFT VALIDATION\]/, "stderr has validation tag");

      // mdr-remediate-3 A1 NEW behavior: resulting draft-state.json carries
      // a terminal failed/rejected state with the rejection reason and the
      // preserved desc (was: "no running re-affirm"; now: explicit failed
      // terminal state).
      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(state.status, "failed",
        "status must be failed (terminal) — A1 stuck-running fix");
      assert.equal(state.phase, "rejected",
        "phase must be rejected (pre-Stage-1 input rejection, distinct from brief/draft runtime failures)");
      assert.match(state.error, /too short|placeholder/,
        "error must carry the rejection reason");
      assert.equal(state.desc, "d",
        "desc must be preserved by writeDraftState merge (startDraftJob wrote it before spawn)");
      assert.ok(state.endedAt, "endedAt must be set (terminal timestamp)");

      // WI1 file-pollution goal intact: only draft-state.json in the jobDir
      // (no `*-brief.md`, `*-roadmap.md`, mission `.json` artifacts).
      const entries = readdirSync(jobDir).sort();
      assert.deepEqual(entries, ["draft-state.json"],
        "no junk brief/roadmap/mission.json artifacts on reject");

      // readDraftJob consumer path returns the full state with `error`
      // (verifies the monitor's detail-view read can surface the failure).
      const { state: readState } = readDraftJob(root, jobId);
      assert.ok(readState, "readDraftJob must return the state object");
      assert.equal(readState.status, "failed");
      assert.equal(readState.phase, "rejected");
      assert.match(readState.error, /too short|placeholder/);
      assert.equal(readState.desc, "d");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects "test" (placeholder) with draftJobDir set: error carries "placeholder" reason (mdr-remediate-3 A1)', async () => {
    // Locks in that ALL WI1 rejection reasons (not just "too short") trigger
    // the failed/rejected terminal state — defends against a future refactor
    // that special-cases one rejection branch but misses another.
    const root = makeTmpProject();
    const jobId = "draft-test-placeholder-mission-draft";
    const jobDir = join(root, "_tmp", jobId);
    mkdirSync(jobDir, { recursive: true });
    try {
      writeFileSync(
        join(jobDir, "draft-state.json"),
        JSON.stringify({
          jobId,
          status: "running",
          startedAt: new Date().toISOString(),
          desc: "test",
          phase: "brief",
          flowHint: null,
          targetFile: null,
        }, null, 2),
      );

      const { runner, calls } = makeFakeRunner();
      __setRunnerFactoryForTest(() => runner);

      await captureStderr(() =>
        cmdDraftMission("test", { dir: root, draftJobDir: jobDir }),
      );

      assert.equal(calls.length, 0, "Stage 1 (mission-brief) must not run");
      assert.equal(process.exitCode, 1, "exitCode must be 1");

      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(state.status, "failed");
      assert.equal(state.phase, "rejected");
      assert.match(state.error, /placeholder/i,
        "placeholder rejection reason must surface in the error field");
      assert.equal(state.desc, "test",
        "desc preserved across merge even for placeholder rejection");
      assert.ok(state.endedAt);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Case C — cmdDraftMission legit path no regression ─────────────────────

describe("cmdDraftMission — valid description still reaches Stage 2", () => {
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

  it('"add audit count" passes validation and reaches draft agent (skipBrief)', async () => {
    const root = makeTmpProject();
    try {
      const { runner, calls } = makeFakeRunner();
      __setRunnerFactoryForTest(() => runner);

      await cmdDraftMission("add audit count", {
        dir: root,
        skipBrief: true,
      });

      assert.ok(calls.length >= 1, "runAgent must be called (Stage 2 draft)");
      assert.equal(calls[0].stepName, "draft-mission");
      assert.notEqual(process.exitCode, 1, "exitCode must NOT be 1 on valid desc");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Case D — base.json `draft.minDescLength` wire-up (mdr-remediate-4 H3) ──
//
// Pins the integration read path at main.js:344-367 (JSON.parse →
// baseConfig?.draft?.minDescLength → validateDraftDesc(desc, N)) that the WI1
// plan's ticked exit criterion claims but, prior to mdr-remediate-4, was NOT
// actually verified by any test (every existing test left the temp project
// without a real base.json, so the catch{} fell through to {} → default 4).

describe("cmdDraftMission — base.json draft.minDescLength wire-up (H3)", () => {
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

  it('D1: base.json { draft: { minDescLength: 8 } } actually takes effect — "add xy" (len 6) rejected though it passes default-4 (distinguishing test)', async () => {
    const root = makeTmpProject();
    try {
      writeFileSync(
        join(root, "missions", "base.json"),
        JSON.stringify({ draft: { minDescLength: 8 } }),
      );

      // Distinguishing guard: with default-4, "add xy" (len 6) would PASS.
      // A broken wire-up (e.g. `baseConfig?.minDescLength` forgetting `.draft.`,
      // or `baseConfig?.draft?.min_desc_length` snake-case typo) silently falls
      // back to default 4 and this desc would slip through — surfacing the
      // regression here instead of in production.
      assert.equal(validateDraftDesc("add xy", 4).ok, true,
        "guard: 'add xy' (len 6) passes default-4 — distinguishing assertion is meaningful");

      const { runner, calls } = makeFakeRunner();
      __setRunnerFactoryForTest(() => runner);

      const stderr = await captureStderr(() =>
        cmdDraftMission("add xy", { dir: root }),
      );

      assert.equal(calls.length, 0, "Stage 1 (mission-brief) must not run — validation must block at threshold 8");
      assert.equal(process.exitCode, 1, "exitCode must be 1 (rejected at threshold 8)");
      assert.match(stderr, /\[DRAFT VALIDATION\]/, "stderr has validation tag");
      assert.match(stderr, /too short/i, "stderr has 'too short' reason (proves configured threshold 8 took effect, not default 4)");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it('D2: base.json { draft: { minDescLength: "garbage" } } (string) falls back to default 4 — "add" (len 3) rejected, "add x" (len 5) accepted', async () => {
    const root = makeTmpProject();
    try {
      writeFileSync(
        join(root, "missions", "base.json"),
        JSON.stringify({ draft: { minDescLength: "garbage" } }),
      );

      // Sub-case A: len-3 desc rejected under default-4 fallback.
      const { runner, calls } = makeFakeRunner();
      __setRunnerFactoryForTest(() => runner);
      const stderr = await captureStderr(() =>
        cmdDraftMission("add", { dir: root }),
      );
      assert.equal(calls.length, 0, "Stage 1 must not run for 'add' (len 3 < default 4)");
      assert.equal(process.exitCode, 1, "exitCode must be 1");
      assert.match(stderr, /\[DRAFT VALIDATION\]/);
      assert.match(stderr, /too short/i);

      // Reset exitCode between sub-cases so the acceptance sub-case starts clean.
      process.exitCode = undefined;

      // Sub-case B: len-5 desc accepted (5 ≥ default 4). skipBrief collapses
      // to Stage 2 so the runner is actually invoked — proves no rejection.
      await cmdDraftMission("add x", { dir: root, skipBrief: true });
      assert.ok(calls.length >= 1, "Stage 2 must run for 'add x' (len 5 ≥ default 4)");
      assert.equal(calls[calls.length - 1].stepName, "draft-mission");
      assert.notEqual(process.exitCode, 1, "exitCode must NOT be 1 on accepted desc");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it('D3: base.json { draft: { minDescLength: null } } falls back to default 4 — "add" (len 3) rejected, "add x" (len 5) accepted', async () => {
    const root = makeTmpProject();
    try {
      writeFileSync(
        join(root, "missions", "base.json"),
        JSON.stringify({ draft: { minDescLength: null } }),
      );

      // Sub-case A: len-3 desc rejected under default-4 fallback.
      const { runner, calls } = makeFakeRunner();
      __setRunnerFactoryForTest(() => runner);
      const stderr = await captureStderr(() =>
        cmdDraftMission("add", { dir: root }),
      );
      assert.equal(calls.length, 0, "Stage 1 must not run for 'add' (len 3 < default 4)");
      assert.equal(process.exitCode, 1, "exitCode must be 1");
      assert.match(stderr, /\[DRAFT VALIDATION\]/);
      assert.match(stderr, /too short/i);

      process.exitCode = undefined;

      // Sub-case B: len-5 desc accepted.
      await cmdDraftMission("add x", { dir: root, skipBrief: true });
      assert.ok(calls.length >= 1, "Stage 2 must run for 'add x' (len 5 ≥ default 4)");
      assert.equal(calls[calls.length - 1].stepName, "draft-mission");
      assert.notEqual(process.exitCode, 1, "exitCode must NOT be 1 on accepted desc");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});
