import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, readFileSync, writeFileSync } from "node:fs";
import { join, resolve, dirname } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import {
  cmdDraftMission,
  parseDraftArtifact,
  __setRunnerFactoryForTest,
} from "../src/main.js";

// Test file lives at tools/mission-driver/test/, so the prompts dir is two
// levels up + /prompts. Using fileURLToPath anchors Case E regardless of which
// cwd pnpm chooses (it differs between `node --test test/*.test.js` from the
// package root vs running the file from the repo root).
const __PROMPTS_DIR = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "..",
  "prompts",
);

// ── Helpers ───────────────────────────────────────────────────────────────

function makeTmpProject() {
  const root = mkdtempSync(join(tmpdir(), "md-path-"));
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

/**
 * Run `fn` while capturing process.stderr.write into a string. Restores the
 * original write in a finally so a thrown assertion never leaves stderr
 * hijacked. Returns { result, stderr }.
 */
function withStderrCaptured(fn) {
  const chunks = [];
  const origWrite = process.stderr.write.bind(process.stderr);
  process.stderr.write = (chunk) => {
    chunks.push(typeof chunk === "string" ? chunk : chunk.toString("utf8"));
    return true;
  };
  let result;
  try {
    result = fn();
  } finally {
    process.stderr.write = origWrite;
  }
  return { result, stderr: chunks.join("") };
}

/** Normalize Windows backslashes to forward slashes for cross-platform asserts. */
const norm = (s) => s.replace(/\\/g, "/");

// ── Case A: backlogDir template var injection ─────────────────────────────

describe("draft-path-consistency — Case A: backlogDir injected into prompts", () => {
  let prevFactory = null;

  beforeEach(() => {
    prevFactory = __setRunnerFactoryForTest(null);
  });
  afterEach(() => {
    __setRunnerFactoryForTest(prevFactory);
  });

  it("brief + draft prompts contain no residual {{backlogDir}} and carry resolved absolute path", async () => {
    const root = makeTmpProject();
    const jobDir = join(root, "_tmp", "draft-case-a");
    try {
      const { runner, calls } = makeFakeRunner({
        "mission-brief": "<BRIEF_FILE>" + resolve(root, "docs/backlog") + "/x-brief.md</BRIEF_FILE>",
        "draft-mission": "<AI_STEP_RESULT>created</AI_STEP_RESULT>",
      });
      __setRunnerFactoryForTest(() => runner);

      await cmdDraftMission("add audit count", { dir: root, draftJobDir: jobDir });

      assert.equal(calls.length, 2, "brief + draft = 2 runAgent calls");
      const expectedBacklogDir = norm(resolve(root, "docs/backlog"));
      for (const call of calls) {
        const np = norm(call.prompt);
        assert.ok(
          !np.includes("{{backlogDir}}"),
          `${call.stepName} prompt must not contain residual {{backlogDir}}`,
        );
        assert.ok(
          np.includes(expectedBacklogDir),
          `${call.stepName} prompt must include resolved backlogDir (${expectedBacklogDir})`,
        );
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Case B: projectRoot ≠ repo root — baseline consistency ─────────────────

describe("draft-path-consistency — Case B: backlog + missions share the same projectRoot", () => {
  let prevFactory = null;

  beforeEach(() => {
    prevFactory = __setRunnerFactoryForTest(null);
  });
  afterEach(() => {
    __setRunnerFactoryForTest(prevFactory);
  });

  it("brief and draft prompt paths share the sub-root (no split-brain)", async () => {
    const outer = makeTmpProject();
    const subRoot = join(outer, "sub");
    mkdirSync(subRoot, { recursive: true });
    mkdirSync(join(subRoot, "missions"), { recursive: true });
    mkdirSync(join(subRoot, "_tmp"), { recursive: true });
    const jobDir = join(subRoot, "_tmp", "draft-case-b");
    try {
      const { runner, calls } = makeFakeRunner({
        "mission-brief": "<BRIEF_FILE>" + resolve(subRoot, "docs/backlog") + "/y-brief.md</BRIEF_FILE>",
        "draft-mission": "<AI_STEP_RESULT>created</AI_STEP_RESULT>",
      });
      __setRunnerFactoryForTest(() => runner);

      await cmdDraftMission("add a thing", { dir: subRoot, draftJobDir: jobDir });

      assert.equal(calls.length, 2);
      const normSub = norm(subRoot);
      const normOuter = norm(outer);
      const briefPrompt = norm(calls[0].prompt);
      const draftPrompt = norm(calls[1].prompt);

      // Both prompts' backlog paths resolve under <subRoot>/docs/backlog.
      assert.ok(
        briefPrompt.includes(normSub + "/docs/backlog"),
        "brief backlog path should resolve under subRoot",
      );
      assert.ok(
        draftPrompt.includes(normSub + "/docs/backlog"),
        "draft backlog path should share subRoot",
      );
      assert.ok(
        draftPrompt.includes(normSub + "/missions"),
        "draft missions path should share subRoot",
      );
      // Negative: neither prompt should reference the OUTER root's docs/backlog
      // or missions — that would indicate split-brain resolution back toward
      // the repo root.
      assert.ok(
        !briefPrompt.includes(normOuter + "/docs/backlog"),
        "brief must not leak to outer-root docs/backlog (split-brain guard)",
      );
      assert.ok(
        !draftPrompt.includes(normOuter + "/docs/backlog"),
        "draft must not leak to outer-root docs/backlog (split-brain guard)",
      );
    } finally {
      rmSync(outer, { recursive: true, force: true });
    }
  });
});

// ── Case C: parseDraftArtifact warn (prefix trick /foo/bar vs /foo/barbaz) ──

describe("draft-path-consistency — Case C: warn when file shares prefix with but is not under missionsDir", () => {
  it("warns and still returns out for the /foo/bar vs /foo/barbaz boundary", () => {
    const tmp = mkdtempSync(join(tmpdir(), "md-warn-c-"));
    // Construct a sibling-prefix layout: <tmp>/expected/missions (expected)
    // vs <tmp>/expected/missions-but-also-extended/x.json (real file).
    const expectedMissionsDir = join(tmp, "expected", "missions");
    const trickDir = join(tmp, "expected", "missions-but-also-extended");
    mkdirSync(expectedMissionsDir, { recursive: true });
    mkdirSync(trickDir, { recursive: true });
    const file = join(trickDir, "x.json");
    writeFileSync(file, JSON.stringify({ name: "trick", roadmapPath: "docs/x.md" }));
    try {
      const { result, stderr } = withStderrCaptured(() =>
        parseDraftArtifact(
          `<MISSION_FILE>${norm(file)}</MISSION_FILE>`,
          expectedMissionsDir,
        ),
      );
      assert.match(
        stderr,
        /\[WARN\] mission\.json landed outside expected missionsDir/,
        "warn must fire for /foo/barbaz vs /foo/bar prefix trick",
      );
      assert.equal(
        result.missionFile,
        norm(file),
        "function still returns the file (no throw, no null)",
      );
      assert.equal(result.missionName, "trick");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── Case D: parseDraftArtifact silent when file is under missionsDir ───────

describe("draft-path-consistency — Case D: silent when file is under missionsDir", () => {
  it("does not warn and returns out when file lands under expected missionsDir", () => {
    const tmp = mkdtempSync(join(tmpdir(), "md-warn-d-"));
    const missionsDir = join(tmp, "expected", "missions");
    mkdirSync(missionsDir, { recursive: true });
    const file = join(missionsDir, "x.json");
    writeFileSync(file, JSON.stringify({ name: "x", roadmapPath: "docs/x.md" }));
    try {
      const { result, stderr } = withStderrCaptured(() =>
        parseDraftArtifact(
          `<MISSION_FILE>${norm(file)}</MISSION_FILE>`,
          missionsDir,
        ),
      );
      assert.equal(stderr, "", "no stderr output when file is under missionsDir");
      assert.equal(result.missionFile, norm(file));
      assert.equal(result.missionName, "x");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── Case N1-D: extractBriefPath strips ANSI from <BRIEF_FILE> marker ───────
//
// extractBriefPath is not exported (it's an internal helper used by
// cmdDraftMission), so this is an integration test that asserts the extracted
// briefPath lands in draft-state.json clean — proving the strip-at-extraction
// discipline covers all three extraction sites in main.js.

describe("draft-path-consistency — Case N1-D: extractBriefPath strips ANSI before <BRIEF_FILE> match (mdr-remediate-5 N1)", () => {
  let prevFactory = null;

  beforeEach(() => {
    prevFactory = __setRunnerFactoryForTest(null);
  });
  afterEach(() => {
    __setRunnerFactoryForTest(prevFactory);
  });

  it("ANSI-wrapped <BRIEF_FILE> marker still resolves a clean briefPath in draft-state.json", async () => {
    // Without stripAnsiControl the CSI bytes around/inside the marker defeat
    // the `<BRIEF_FILE>\s*([^\s<]+)` matcher → briefPath would be null (or
    // polluted with raw ESC bytes) in draft-state.json. This is the value-
    // capture failure mode stripAnsiControl exists to neutralize (memory L009;
    // cross-ref test/ansi-and-mixedcase-tag.test.js:108 and Case N1-C above).
    const root = makeTmpProject();
    const jobDir = join(root, "_tmp", "draft-case-n1-d");
    const briefPathRaw = resolve(root, "docs/backlog") + "/ansi-d-brief.md";
    try {
      const { runner } = makeFakeRunner({
        "mission-brief":
          "\x1b[32m<BRIEF_FILE>" + briefPathRaw + "</BRIEF_FILE>\x1b[0m\n<BRIEF_GATE>pass</BRIEF_GATE>",
        "draft-mission":
          "<AI_STEP_RESULT>created</AI_STEP_RESULT>\n<MISSION_FILE></MISSION_FILE>",
      });
      __setRunnerFactoryForTest(() => runner);

      await cmdDraftMission("add audit count", { dir: root, draftJobDir: jobDir });

      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(
        norm(state.briefPath),
        norm(briefPathRaw),
        "briefPath must be the clean resolved path (ANSI stripped before the marker match)",
      );
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Case N1-C: parseDraftArtifact strips ANSI from <MISSION_FILE> marker ───

describe("draft-path-consistency — Case N1-C: parseDraftArtifact strips ANSI before <MISSION_FILE> match (mdr-remediate-3 N1)", () => {
  it("ANSI intermixed in the value capture still resolves missionName/roadmapPath/missionFile", () => {
    // Without stripAnsiControl the CSI bytes (`\x1b[0m`) sit between the path
    // and `</MISSION_FILE>` and pass the `[^<]+` value matcher, so the captured
    // "path" carries raw ESC bytes → readFileSync fails → silent fall-through
    // to the scan branch (missionName/missionFile null). This is the value-
    // capture failure mode stripAnsiControl exists to neutralize (memory L009;
    // cross-ref test/ansi-and-mixedcase-tag.test.js:108).
    const tmp = mkdtempSync(join(tmpdir(), "md-ansi-c-"));
    const missionsDir = join(tmp, "missions");
    mkdirSync(missionsDir, { recursive: true });
    const file = join(missionsDir, "ansi-c.json");
    writeFileSync(file, JSON.stringify({ name: "ansi-c", roadmapPath: "docs/ansi-c.md" }));
    try {
      const result = parseDraftArtifact(
        `\x1b[32m<MISSION_FILE>${norm(file)}\x1b[0m</MISSION_FILE>\x1b[0m`,
        missionsDir,
      );
      assert.equal(result.missionFile, norm(file), "missionFile must resolve to the real path (ANSI stripped)");
      assert.equal(result.missionName, "ansi-c");
      assert.equal(result.roadmapPath, "docs/ansi-c.md");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── Case E: grep anchor — no docs/backlog/ literal in prompt files ─────────

describe("draft-path-consistency — Case E: prompt files have no docs/backlog/ literal", () => {
  it("prompts/mission-brief.md contains no literal docs/backlog/", () => {
    const brief = readFileSync(join(__PROMPTS_DIR, "mission-brief.md"), "utf8");
    assert.equal(brief.match(/docs\/backlog\//g), null, "mission-brief.md must use {{backlogDir}}/");
  });

  it("prompts/mission-draft.md contains no literal docs/backlog/", () => {
    const draft = readFileSync(join(__PROMPTS_DIR, "mission-draft.md"), "utf8");
    assert.equal(draft.match(/docs\/backlog\//g), null, "mission-draft.md must use {{backlogDir}}/");
  });
});
