import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";
import { mkdtempSync, rmSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

// Plan 1 Phase 1 — Proof: _initWorkflow() must persist `pid: process.pid` at
// the top level of run-state.json so reconciliation (Phase 2) can decide
// liveness by PID survival. Uses the same mkdtempSync runDir pattern as
// subflow-state-isolation.test.js / sessionid-persist.test.js.

describe("run-state.json persists main process pid (Plan 1 Phase 1)", () => {
  it("_initWorkflow writes a top-level pid equal to process.pid", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "rs-pid-"));
    try {
      const flow = simpleFlow({
        WORK: {
          type: "agent",
          prompt: "do work",
          resultTag: "AI_STEP_RESULT",
          transitions: { ok: { done: "completed" } },
        },
      }, "WORK");

      const delegates = makeMockDelegates({
        responses: { WORK: { text: "<AI_STEP_RESULT>ok</AI_STEP_RESULT>", ok: true } },
        config: { projectRoot: runDir, runDir },
      });

      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();
      assert.equal(result.status, "completed");

      const state = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
      assert.equal(typeof state.pid, "number",
        "run-state.json must carry a numeric top-level pid field");
      assert.equal(state.pid, process.pid,
        "top-level pid must equal the process that ran the engine (process.pid)");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });
});
