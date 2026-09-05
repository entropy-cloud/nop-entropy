// mdr-fix-2 — Per-mission promptsDir config support.
//
// Pins the 3-level prompt resolution chain
//   mission.promptsDir → missions/prompts/ → built-in TOOL_ROOT/prompts/
// for BOTH the main flow (createMissionDriverFlow) and subflows (loadSubFlow),
// plus the mission-check existence validation for the new optional field.
//
// The chain is realized through the existing `projectPromptDirs` array passed
// to `loadPrompt` (flow-loader.js): the first dir that contains the file wins;
// `loadPrompt` then falls back to `TOOL_ROOT/<promptPath>`. main.js builds the
// array as `[config.missionPromptsDir, resolve(config.missionsDir, "prompts")]`
// (filter(Boolean) drops the empty string when unset); loadSubFlow builds the
// same array from `this.config`.
//
// Coverage:
//   A. config.js         — resolveConfig exposes missionPromptsDir (abs / "").
//   B. main-flow chain   — createMissionDriverFlow: mission shadows shared,
//                          per-file fallback to shared, built-in fallback.
//   C. subflow chain     — loadSubFlow: mission shadows shared, shared-only,
//                          falsy-missionsDir guard keeps built-in fallback.
//   D. mission-check     — promptsDir existence-validated (rejected / accepted
//                          / optional).

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, readFileSync } from "node:fs";
import { resolve, join, dirname } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import { createMissionDriverFlow, loadSubFlow } from "../src/flow-loader.js";
import { validateMission } from "../src/mission-check.mjs";
import { resolveConfig } from "../src/config.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const TOOL_ROOT = resolve(__dirname, "..");
// A promptPath that exists under TOOL_ROOT so built-in fallback is provable
// without depending on a specific prompt's body content beyond equality.
const BUILTIN_PROMPT_REL = "prompts/health-check.md";

// ── A. config.js exposes missionPromptsDir ─────────────────────────────────

describe("mdr-fix-2 config.js — missionPromptsDir resolution", () => {
  it("sets missionPromptsDir (absolute) when mission.promptsDir is set", () => {
    const root = mkdtempSync(join(tmpdir(), "mdr-fix2-cfg-set-"));
    try {
      mkdirSync(join(root, "missions"), { recursive: true });
      mkdirSync(join(root, "custom-prompts"), { recursive: true });
      mkdirSync(join(root, "docs", "backlog"), { recursive: true });
      mkdirSync(join(root, "docs", "plans"), { recursive: true });
      writeFileSync(join(root, "docs", "backlog", "x.md"), "# roadmap\n");
      writeFileSync(join(root, "missions", "foo.json"), JSON.stringify({
        name: "foo",
        roadmapPath: "docs/backlog/x.md",
        plansDir: "docs/plans",
        promptsDir: "custom-prompts",
        commands: { test: "echo ok" },
      }));

      const cfg = resolveConfig({ dir: root, mission: "foo" });

      assert.equal(cfg.missionPromptsDir, resolve(root, "custom-prompts"),
        "missionPromptsDir must be the absolute resolved path");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("sets missionPromptsDir to '' when promptsDir is unset", () => {
    const root = mkdtempSync(join(tmpdir(), "mdr-fix2-cfg-unset-"));
    try {
      mkdirSync(join(root, "missions"), { recursive: true });
      mkdirSync(join(root, "docs", "backlog"), { recursive: true });
      mkdirSync(join(root, "docs", "plans"), { recursive: true });
      writeFileSync(join(root, "docs", "backlog", "x.md"), "# roadmap\n");
      writeFileSync(join(root, "missions", "foo.json"), JSON.stringify({
        name: "foo",
        roadmapPath: "docs/backlog/x.md",
        plansDir: "docs/plans",
        commands: { test: "echo ok" },
      }));

      const cfg = resolveConfig({ dir: root, mission: "foo" });

      assert.equal(cfg.missionPromptsDir, "",
        "missionPromptsDir must be empty string when promptsDir is unset");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── B. Main-flow chain (createMissionDriverFlow) ───────────────────────────

describe("mdr-fix-2 createMissionDriverFlow — prompt resolution chain", () => {
  it("mission-level dir shadows shared dir", () => {
    const tmp = mkdtempSync(join(tmpdir(), "mdr-fix2-main-shadow-"));
    try {
      const flowsDir = resolve(tmp, "missions", "flows");
      const missionPrompts = resolve(tmp, "missions", "custom-prompts");
      const sharedPrompts = resolve(tmp, "missions", "prompts");
      mkdirSync(flowsDir, { recursive: true });
      mkdirSync(missionPrompts, { recursive: true });
      mkdirSync(sharedPrompts, { recursive: true });

      writeFileSync(resolve(flowsDir, "t.json"), JSON.stringify({
        name: "t", entry: "S", maxTotalSteps: 5, steps: {
          S: { type: "agent", promptPath: "p.md", transitions: { ok: { done: "completed" } } },
        },
      }));
      writeFileSync(resolve(missionPrompts, "p.md"), "MISSION");
      writeFileSync(resolve(sharedPrompts, "p.md"), "SHARED");

      const flow = createMissionDriverFlow({
        flowName: "t",
        projectFlowsDir: flowsDir,
        // Mirrors main.js: [config.missionPromptsDir, shared].filter(Boolean)
        projectPromptDirs: [missionPrompts, sharedPrompts],
      });

      assert.equal(flow.steps.S.prompt, "MISSION",
        "mission-level dir must win over shared dir");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("per-file fallback: shared dir used when mission-level dir lacks the file", () => {
    const tmp = mkdtempSync(join(tmpdir(), "mdr-fix2-main-fallback-shared-"));
    try {
      const flowsDir = resolve(tmp, "missions", "flows");
      const missionPrompts = resolve(tmp, "missions", "custom-prompts");
      const sharedPrompts = resolve(tmp, "missions", "prompts");
      mkdirSync(flowsDir, { recursive: true });
      mkdirSync(missionPrompts, { recursive: true });
      mkdirSync(sharedPrompts, { recursive: true });

      writeFileSync(resolve(flowsDir, "t.json"), JSON.stringify({
        name: "t", entry: "S", maxTotalSteps: 5, steps: {
          S: { type: "agent", promptPath: "p.md", transitions: { ok: { done: "completed" } } },
        },
      }));
      // mission-level dir exists but does NOT contain p.md; shared does.
      writeFileSync(resolve(sharedPrompts, "p.md"), "SHARED");

      const flow = createMissionDriverFlow({
        flowName: "t",
        projectFlowsDir: flowsDir,
        projectPromptDirs: [missionPrompts, sharedPrompts],
      });

      assert.equal(flow.steps.S.prompt, "SHARED",
        "must fall through to shared dir when the file is absent from mission dir");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("built-in fallback: loads from TOOL_ROOT when absent from all project dirs", () => {
    const tmp = mkdtempSync(join(tmpdir(), "mdr-fix2-main-fallback-builtin-"));
    try {
      const flowsDir = resolve(tmp, "missions", "flows");
      const emptyDir = resolve(tmp, "missions", "custom-prompts");
      mkdirSync(flowsDir, { recursive: true });
      mkdirSync(emptyDir, { recursive: true });

      // promptPath points at a real built-in prompt; project dir lacks it.
      writeFileSync(resolve(flowsDir, "t.json"), JSON.stringify({
        name: "t", entry: "S", maxTotalSteps: 5, steps: {
          S: { type: "agent", promptPath: BUILTIN_PROMPT_REL, transitions: { ok: { done: "completed" } } },
        },
      }));

      const flow = createMissionDriverFlow({
        flowName: "t",
        projectFlowsDir: flowsDir,
        projectPromptDirs: [emptyDir],
      });

      const expected = readFileSync(resolve(TOOL_ROOT, BUILTIN_PROMPT_REL), "utf8");
      assert.equal(flow.steps.S.prompt, expected,
        "must fall back to built-in TOOL_ROOT/<promptPath> when no project dir has the file");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── C. Subflow chain (loadSubFlow) ─────────────────────────────────────────
//
// loadSubFlow is called by the engine as `this.delegates.loadSubFlow(name)`,
// so `this` is the delegates object and `this.config` is the resolved config.
// We bind it explicitly via loadSubFlow.call(delegates, name).

describe("mdr-fix-2 loadSubFlow — prompt resolution chain", () => {
  it("mission-level dir shadows shared dir", () => {
    const tmp = mkdtempSync(join(tmpdir(), "mdr-fix2-sub-shadow-"));
    try {
      const missionsDir = resolve(tmp, "missions");
      const flowsDir = resolve(missionsDir, "flows");
      const missionPrompts = resolve(tmp, "custom-prompts");
      const sharedPrompts = resolve(missionsDir, "prompts");
      mkdirSync(flowsDir, { recursive: true });
      mkdirSync(missionPrompts, { recursive: true });
      mkdirSync(sharedPrompts, { recursive: true });

      writeFileSync(resolve(flowsDir, "child.json"), JSON.stringify({
        name: "child", entry: "WORK", maxTotalSteps: 5, steps: {
          WORK: { type: "agent", promptPath: "p.md", transitions: { ok: { done: "completed" } } },
        },
      }));
      writeFileSync(resolve(missionPrompts, "p.md"), "MISSION");
      writeFileSync(resolve(sharedPrompts, "p.md"), "SHARED");

      const delegates = { config: { missionsDir, missionPromptsDir: missionPrompts } };
      const flow = loadSubFlow.call(delegates, "child");

      assert.equal(flow.steps.WORK.prompt, "MISSION",
        "subflow prompt must resolve from mission-level dir first");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("shared dir used when missionPromptsDir is unset (empty string)", () => {
    const tmp = mkdtempSync(join(tmpdir(), "mdr-fix2-sub-shared-"));
    try {
      const missionsDir = resolve(tmp, "missions");
      const flowsDir = resolve(missionsDir, "flows");
      const sharedPrompts = resolve(missionsDir, "prompts");
      mkdirSync(flowsDir, { recursive: true });
      mkdirSync(sharedPrompts, { recursive: true });

      writeFileSync(resolve(flowsDir, "child.json"), JSON.stringify({
        name: "child", entry: "WORK", maxTotalSteps: 5, steps: {
          WORK: { type: "agent", promptPath: "p.md", transitions: { ok: { done: "completed" } } },
        },
      }));
      writeFileSync(resolve(sharedPrompts, "p.md"), "SHARED");

      // missionPromptsDir: "" mirrors config.js when promptsDir is unset.
      const delegates = { config: { missionsDir, missionPromptsDir: "" } };
      const flow = loadSubFlow.call(delegates, "child");

      assert.equal(flow.steps.WORK.prompt, "SHARED",
        "subflow must use shared missions/prompts/ when missionPromptsDir is unset");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("preserves falsy-missionsDir guard: built-in fallback when both unset", () => {
    const tmp = mkdtempSync(join(tmpdir(), "mdr-fix2-sub-guard-"));
    try {
      // missionsDir intentionally NOT set; subflowDir lets findFlowFile locate
      // the child flow without it, isolating the prompt-chain behavior.
      const subflowDir = resolve(tmp, "myflows");
      mkdirSync(subflowDir, { recursive: true });
      writeFileSync(resolve(subflowDir, "child.json"), JSON.stringify({
        name: "child", entry: "WORK", maxTotalSteps: 5, steps: {
          WORK: { type: "agent", promptPath: BUILTIN_PROMPT_REL, transitions: { ok: { done: "completed" } } },
        },
      }));

      const delegates = { config: { subflowDir } };
      const flow = loadSubFlow.call(delegates, "child");

      const expected = readFileSync(resolve(TOOL_ROOT, BUILTIN_PROMPT_REL), "utf8");
      assert.equal(flow.steps.WORK.prompt, expected,
        "when missionsDir + missionPromptsDir are both falsy, projectPromptDirs must be [] → built-in fallback");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── D. mission-check existence validation ──────────────────────────────────

describe("mdr-fix-2 mission-check — promptsDir existence validation", () => {
  it("rejects a promptsDir pointing at a nonexistent path", () => {
    const root = mkdtempSync(join(tmpdir(), "mdr-fix2-check-bad-"));
    try {
      mkdirSync(join(root, "docs", "backlog"), { recursive: true });
      mkdirSync(join(root, "docs", "plans"), { recursive: true });
      writeFileSync(join(root, "docs", "backlog", "x.md"), "# roadmap\n");

      const mission = {
        name: "x",
        roadmapPath: "docs/backlog/x.md",
        plansDir: "docs/plans",
        promptsDir: "does-not-exist-dir",
        commands: { test: "echo ok" },
      };

      const { valid, errors } = validateMission(mission, root);

      assert.equal(valid, false, "bad promptsDir must make the mission invalid");
      assert.ok(
        errors.some((e) => e.includes("promptsDir") && e.includes("does not exist")),
        `errors must mention promptsDir does not exist; got: ${JSON.stringify(errors)}`,
      );
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("accepts a promptsDir that exists on disk", () => {
    const root = mkdtempSync(join(tmpdir(), "mdr-fix2-check-good-"));
    try {
      mkdirSync(join(root, "docs", "backlog"), { recursive: true });
      mkdirSync(join(root, "docs", "plans"), { recursive: true });
      mkdirSync(join(root, "real-prompts"), { recursive: true });
      writeFileSync(join(root, "docs", "backlog", "x.md"), "# roadmap\n");

      const mission = {
        name: "x",
        roadmapPath: "docs/backlog/x.md",
        plansDir: "docs/plans",
        promptsDir: "real-prompts",
        commands: { test: "echo ok" },
      };

      const { valid, errors } = validateMission(mission, root);

      assert.equal(valid, true, `existing promptsDir must be valid; errors: ${JSON.stringify(errors)}`);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("does not require promptsDir (optional field, backward compatible)", () => {
    const root = mkdtempSync(join(tmpdir(), "mdr-fix2-check-opt-"));
    try {
      mkdirSync(join(root, "docs", "backlog"), { recursive: true });
      mkdirSync(join(root, "docs", "plans"), { recursive: true });
      writeFileSync(join(root, "docs", "backlog", "x.md"), "# roadmap\n");

      const mission = {
        name: "x",
        roadmapPath: "docs/backlog/x.md",
        plansDir: "docs/plans",
        commands: { test: "echo ok" },
      };

      const { valid } = validateMission(mission, root);

      assert.equal(valid, true, "mission without promptsDir must still be valid");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});
