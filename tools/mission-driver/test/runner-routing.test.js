import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createRunner } from "../src/runner.js";

// OPT-3: a fake execute that records the spawn args (no real opencode process).
// createRunner accepts an injectable execute (default = real executor), which lets
// us assert model routing + sessionId forwarding deterministically.
function makeFakeExecute(calls, runDir) {
  return (config, label, cmd, args, opts) => {
    calls.push({ config, label, cmd, args, opts });
    const fakeLog = join(runDir, `fake-${label}-${calls.length}.log`);
    // realRun reads the log file and extracts a sessionId from it; embedding one
    // avoids the execSync("opencode session list") fallback path.
    writeFileSync(fakeLog, JSON.stringify({ session_id: "ses_runner_mock" }));
    return Promise.resolve({ ok: true, logFile: fakeLog, pid: 1 });
  };
}

function baseConfig(runDir, extra = {}) {
  return {
    dryRun: false,
    model: "main-model",
    agent: "build",
    projectRoot: runDir,
    runDir,
    ...extra,
  };
}

describe("runner — OPT-3 parseModel routing", () => {
  it("runParseAgent invokes opencode with config.parseModel and forwards --session", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-runner-route-"));
    try {
      const calls = [];
      const runner = await createRunner(baseConfig(runDir, { parseModel: "cheap-model" }), makeFakeExecute(calls, runDir));

      await runner.runParseAgent("parse-AI_STEP_RESULT", "infer marker", "sys", "ses_xyz");

      const opencodeCalls = calls.filter((c) => c.cmd === "opencode");
      assert.equal(opencodeCalls.length, 1);
      const args = opencodeCalls[0].args;
      const mIdx = args.indexOf("-m");
      assert.ok(mIdx >= 0, "-m flag present");
      assert.equal(args[mIdx + 1], "cheap-model", "runParseAgent must use parseModel");
      const sIdx = args.indexOf("--session");
      assert.ok(sIdx >= 0, "--session flag present");
      assert.equal(args[sIdx + 1], "ses_xyz", "sessionId must be forwarded for session continuation");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("runParseAgent falls back to config.model when parseModel is unset", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-runner-nomodel-"));
    try {
      const calls = [];
      const runner = await createRunner(baseConfig(runDir), makeFakeExecute(calls, runDir));

      await runner.runParseAgent("parse-X", "infer", "sys", null);

      const opencodeCalls = calls.filter((c) => c.cmd === "opencode");
      assert.equal(opencodeCalls.length, 1);
      const args = opencodeCalls[0].args;
      const mIdx = args.indexOf("-m");
      assert.equal(args[mIdx + 1], "main-model", "must fall back to config.model");
      assert.ok(!args.includes("--session"), "no --session when sessionId is absent");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("runAgent (main path) still uses config.model even when parseModel is set", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-runner-main-"));
    try {
      const calls = [];
      const runner = await createRunner(baseConfig(runDir, { parseModel: "cheap-model" }), makeFakeExecute(calls, runDir));

      await runner.runAgent("EXECUTE", "do work", "sys", "ses_main");

      const opencodeCalls = calls.filter((c) => c.cmd === "opencode");
      assert.equal(opencodeCalls.length, 1);
      const args = opencodeCalls[0].args;
      const mIdx = args.indexOf("-m");
      assert.equal(args[mIdx + 1], "main-model", "runAgent must keep using config.model");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("runTool routes through the same injectable execute (no regression)", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-runner-tool-"));
    try {
      const calls = [];
      const runner = await createRunner(baseConfig(runDir, { parseModel: "cheap-model" }), makeFakeExecute(calls, runDir));

      await runner.runTool("BUILD", "echo ok");

      const toolCalls = calls.filter((c) => c.label === "BUILD");
      assert.equal(toolCalls.length, 1);
      assert.equal(toolCalls[0].cmd, "echo");
      assert.deepEqual(toolCalls[0].args, ["ok"]);
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });
});

// OPT-6: `--pure` optional routing. config.pure (default false) must leave the
// spawn args untouched (behavior unchanged); when true, `--pure` must appear AND
// all other args (-m/--agent/--dangerously-skip-permissions/--session) must be
// fully preserved — so the only delta is the extra flag.
describe("runner — OPT-6 --pure routing", () => {
  it("default (pure=false) args do NOT contain --pure", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-runner-pure-off-"));
    try {
      const calls = [];
      const runner = await createRunner(baseConfig(runDir), makeFakeExecute(calls, runDir));

      await runner.runAgent("EXECUTE", "do work", "sys", "ses_main");

      const opencodeCalls = calls.filter((c) => c.cmd === "opencode");
      assert.equal(opencodeCalls.length, 1);
      const args = opencodeCalls[0].args;
      assert.equal(args[0], "run", "first arg still 'run'");
      assert.ok(!args.includes("--pure"), "default must NOT inject --pure");
      // core args preserved
      const mIdx = args.indexOf("-m");
      assert.equal(args[mIdx + 1], "main-model");
      assert.ok(args.includes("--agent"));
      assert.ok(args.includes("--dangerously-skip-permissions"));
      const sIdx = args.indexOf("--session");
      assert.equal(args[sIdx + 1], "ses_main");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("config.pure=true injects --pure right after 'run' and preserves all other args", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-runner-pure-on-"));
    try {
      const calls = [];
      const runner = await createRunner(baseConfig(runDir, { pure: true }), makeFakeExecute(calls, runDir));

      await runner.runAgent("EXECUTE", "do work", "sys", "ses_main");

      const opencodeCalls = calls.filter((c) => c.cmd === "opencode");
      assert.equal(opencodeCalls.length, 1);
      const args = opencodeCalls[0].args;
      assert.equal(args[0], "run", "first arg still 'run'");
      assert.equal(args[1], "--pure", "--pure injected right after run");
      const pureCount = args.filter((a) => a === "--pure").length;
      assert.equal(pureCount, 1, "exactly one --pure");
      // core args preserved
      const mIdx = args.indexOf("-m");
      assert.equal(args[mIdx + 1], "main-model");
      assert.ok(args.includes("--agent"));
      assert.equal(args[args.indexOf("--agent") + 1], "build");
      assert.ok(args.includes("--dangerously-skip-permissions"));
      const sIdx = args.indexOf("--session");
      assert.equal(args[sIdx + 1], "ses_main");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("config.pure=true without sessionId still injects --pure and omits --session", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-runner-pure-nosess-"));
    try {
      const calls = [];
      const runner = await createRunner(baseConfig(runDir, { pure: true }), makeFakeExecute(calls, runDir));

      await runner.runAgent("EXECUTE", "do work", "sys", null);

      const opencodeCalls = calls.filter((c) => c.cmd === "opencode");
      assert.equal(opencodeCalls.length, 1);
      const args = opencodeCalls[0].args;
      assert.equal(args[1], "--pure", "--pure injected");
      assert.ok(!args.includes("--session"), "no --session when sessionId absent");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });
});
