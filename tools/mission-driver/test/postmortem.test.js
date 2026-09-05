// mdo-3 Phase 1 — direct-call unit test for the extracted runPostmortem.
// Verifies: (1) it builds the skeleton, resolves the prompt, dispatches via the
// injected runner, and (2) parses the <POSTMORTEM_FILE>/<MEMORY_UPDATED> return
// tags from the agent text (FSD §3.3.3A / §3.3.3D). The CLI wrapper's behavioural
// equivalence is covered by analyze-run.test.js (which exercises the same code
// path end-to-end via config resolution).
import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { runPostmortem } from "../src/postmortem.mjs";

function tmpRoot() {
  return mkdtempSync(join(tmpdir(), "postmortem-"));
}

// Minimal run dir so buildRunSkeleton has structured artifacts to pre-digest.
function makeRunDir(root, id, { state, events } = {}) {
  const dir = join(root, "_tmp", id);
  mkdirSync(dir, { recursive: true });
  if (state !== null) {
    writeFileSync(join(dir, "run-state.json"), JSON.stringify(state || {}), "utf8");
  }
  if (events !== null) {
    const body = (events || []).map((e) => JSON.stringify(e)).join("\n");
    writeFileSync(join(dir, "events.jsonl"), body, "utf8");
  }
  return dir;
}

describe("runPostmortem (extracted reuse function)", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { try { rmSync(root, { recursive: true, force: true }); } catch {} });

  it("dispatches via runner.runAgent, includes the skeleton, and parses return tags", async () => {
    const targetRunDir = makeRunDir(root, "2026-07-04-100000-mission-driver", {
      state: { missionName: "demo", runId: "2026-07-04-100000-mission-driver", status: "completed", steps: [] },
      events: [{ type: "run_started" }],
    });

    let capturedPrompt = "";
    let capturedStepName = "";
    const runner = {
      async runAgent(stepName, prompt) {
        capturedStepName = stepName;
        capturedPrompt = prompt;
        return {
          text:
            "postmortem written.\n" +
            "<POSTMORTEM_FILE>tools/mission-driver/docs/postmortems/2026-07-04-demo.md</POSTMORTEM_FILE>\n" +
            "<MEMORY_UPDATED>self: 1 added/0 updated; module: skipped</MEMORY_UPDATED>",
          ok: true,
        };
      },
    };

    const res = await runPostmortem({
      projectRoot: root,
      missionsDir: join(root, "missions"),
      targetRunDir,
      targetRunId: "2026-07-04-100000-mission-driver",
      runner,
      opts: { moduleInfo: { moduleName: "demo-mod", moduleMemoryDir: "" } },
    });

    // Dispatched as the analyze-run agent with the skeleton folded into prompt.
    assert.equal(capturedStepName, "analyze-run");
    assert.ok(capturedPrompt.includes("2026-07-04-100000-mission-driver"), "prompt must carry the target run id");
    assert.ok(capturedPrompt.length > 200, "prompt must contain the run-postmortem template body");

    // Return tags parsed (FSD §3.3.3D).
    assert.equal(res.postmortemFile, "tools/mission-driver/docs/postmortems/2026-07-04-demo.md");
    assert.match(res.memoryUpdated, /self: 1 added/);
    assert.ok(res.text.includes("<POSTMORTEM_FILE>"));
  });

  it("returns null tags when the agent omits them (graceful, no throw)", async () => {
    const targetRunDir = makeRunDir(root, "2026-07-04-200000-mission-driver", {
      state: { missionName: "demo", status: "failed", steps: [] },
      events: [],
    });
    const runner = {
      async runAgent() {
        return { text: "agent produced prose with no structured tags", ok: true };
      },
    };
    const res = await runPostmortem({
      projectRoot: root,
      missionsDir: join(root, "missions"),
      targetRunDir,
      targetRunId: "2026-07-04-200000-mission-driver",
      runner,
      opts: {},
    });
    assert.equal(res.postmortemFile, null);
    assert.equal(res.memoryUpdated, null);
    assert.ok(res.text.includes("no structured tags"));
  });

  it("throws when runner.runAgent is missing", async () => {
    const targetRunDir = makeRunDir(root, "2026-07-04-300000-mission-driver");
    await assert.rejects(
      () => runPostmortem({
        projectRoot: root,
        missionsDir: join(root, "missions"),
        targetRunDir,
        targetRunId: "2026-07-04-300000-mission-driver",
        runner: {},
        opts: {},
      }),
      /runAgent is required/,
    );
  });

  it("throws when targetRunDir is missing", async () => {
    const runner = { async runAgent() { return { text: "", ok: true }; } };
    await assert.rejects(
      () => runPostmortem({ projectRoot: root, targetRunDir: null, targetRunId: "x", runner, opts: {} }),
      /targetRunDir is required/,
    );
  });
});
