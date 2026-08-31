import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, basename } from "node:path";
import { createRunner } from "../src/runner.js";
import { FlowEngine } from "../src/engine.js";
import { boundPromptSize } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

// ── Phase 2: runner passes prompt via STDIN, never as a positional cmdline arg ──

function makeFakeExecute(calls, runDir) {
  return (config, label, cmd, args, opts) => {
    calls.push({ config, label, cmd, args, opts });
    const fakeLog = join(runDir, `fake-${label}-${calls.length}.log`);
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

describe("runner — mdr-3 Phase 2: prompt via stdin", () => {
  it("passes the prompt via opts.stdin, NOT as a positional arg", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-runner-stdin-"));
    try {
      const calls = [];
      const runner = await createRunner(baseConfig(runDir), makeFakeExecute(calls, runDir));

      await runner.runAgent("EXECUTE", "do the work", "sys", "ses_main");

      const opencodeCalls = calls.filter((c) => c.cmd === "opencode");
      assert.equal(opencodeCalls.length, 1);
      const { args, opts } = opencodeCalls[0];

      // stdin carries the marked prompt. The tag now carries the run's identity
      // ([MISSION_DRIVER:<runId>]) for parallel-run safety; runId = basename(runDir).
      const runTag = `[MISSION_DRIVER:${basename(runDir)}]`;
      assert.equal(opts.stdin, `${runTag} do the work`);
      // NO positional prompt arg: the last non-flag value must NOT be the prompt.
      // args = ["run","-m","main-model","--agent","build","--dangerously-skip-permissions","--session","ses_main"]
      assert.ok(!args.includes(`${runTag} do the work`),
        "prompt must not appear as a positional cmdline arg");
      assert.ok(!args.includes("do the work"),
        "raw prompt must not appear as a positional cmdline arg");
      // core flags preserved
      const mIdx = args.indexOf("-m");
      assert.equal(args[mIdx + 1], "main-model");
      assert.ok(args.includes("--dangerously-skip-permissions"));
      const sIdx = args.indexOf("--session");
      assert.equal(args[sIdx + 1], "ses_main");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("passes a >32K prompt entirely via stdin with no oversized positional arg", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-runner-big-"));
    try {
      const calls = [];
      const runner = await createRunner(baseConfig(runDir), makeFakeExecute(calls, runDir));

      // 65466 chars — the exact failure size from the plan baseline (>2x the 32767
      // CreateProcess ceiling). Previously this killed the subprocess before any
      // output; now it must travel entirely through stdin.
      const bigPrompt = "X".repeat(65466);
      await runner.runAgent("EXECUTE", bigPrompt, "sys", null);

      const opencodeCalls = calls.filter((c) => c.cmd === "opencode");
      assert.equal(opencodeCalls.length, 1);
      const { args, opts } = opencodeCalls[0];

      // stdin holds the FULL prompt intact (tag now carries runId).
      const runTag = `[MISSION_DRIVER:${basename(runDir)}]`;
      assert.equal(opts.stdin, `${runTag} ${bigPrompt}`);
      assert.equal(opts.stdin.length, 65466 + `${runTag} `.length);
      // NO arg exceeds the 32K cmdline ceiling
      const oversized = args.filter((a) => typeof a === "string" && a.length > 32000);
      assert.deepEqual(oversized, [], "no positional arg may exceed the 32K cmdline ceiling");
      // no --session when sessionId absent
      assert.ok(!args.includes("--session"));
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("runTool does NOT set stdin (legacy commands keep stdin 'ignore')", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-runner-tool-stdin-"));
    try {
      const calls = [];
      const runner = await createRunner(baseConfig(runDir), makeFakeExecute(calls, runDir));

      await runner.runTool("BUILD", "pnpm build");

      const toolCalls = calls.filter((c) => c.label === "BUILD");
      assert.equal(toolCalls.length, 1);
      assert.equal(toolCalls[0].opts.stdin, undefined,
        "runTool must not opt into stdin — keeps legacy 'ignore' stdio");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });
});

// ── Phase 3: prompt size guard (boundPromptSize) ──

describe("engine — mdr-3 Phase 3: boundPromptSize guard", () => {
  it("leaves prompts under the threshold untouched", () => {
    const p = "short prompt";
    assert.equal(boundPromptSize(p), p);
    // exactly at the threshold is still allowed (<=)
    const atMax = "a".repeat(512);
    assert.equal(boundPromptSize(atMax, { maxBytes: 512 }), atMax);
  });

  it("truncates prompts over the threshold, keeping head and tail + a marker", () => {
    const head = "HEAD-START";
    const tail = "TAIL-END";
    const filler = "F".repeat(30 * 1024); // push well past the 24KB default
    const big = head + filler + tail;
    assert.ok(Buffer.byteLength(big) > 24 * 1024);

    let truncated = null;
    const out = boundPromptSize(big, { onTruncate: (i) => { truncated = i; } });

    // bounded under the threshold
    assert.ok(Buffer.byteLength(out) <= 24 * 1024,
      `bounded prompt (${Buffer.byteLength(out)}B) must be <= 24KB`);
    // head and tail preserved
    assert.ok(out.startsWith("HEAD-START"), "head retained");
    assert.ok(out.endsWith("TAIL-END"), "tail (AI_STEP_RESULT contract) retained");
    // middle filler dropped
    assert.ok(!out.includes(filler), "the bulk of the filler must be dropped");
    // marker present
    assert.match(out, /PROMPT TRUNCATED/);
    // callback fired with byte accounting
    assert.ok(truncated && truncated.originalBytes > truncated.boundedBytes);
    assert.ok(truncated.droppedBytes > 0);
  });

  it("respects custom thresholds (bounds test-stdout append under a small budget)", () => {
    // Simulate a closure-audit append that drags full npm-test stdout into the
    // prompt. With a small budget the guard must still cap it.
    const testStdout = "FAIL src/x.test.js\n" + "line\n".repeat(4000);
    const base = "Execute the plan.\n";
    const prompt = base + testStdout;
    assert.ok(Buffer.byteLength(prompt) > 1000);

    const out = boundPromptSize(prompt, { maxBytes: 1000, keepBytes: 300 });
    assert.ok(Buffer.byteLength(out) <= 1000, "must respect the custom budget");
  });
});

// ── Phase 3: engine applies the guard at _buildPrompt (integration) ──

describe("engine — mdr-3 Phase 3: _buildPrompt applies size guard", () => {
  it("truncates an oversized prompt+append so runAgent never sees >threshold", async () => {
    // A step whose base prompt + a giant append would exceed 24KB.
    const filler = "Z".repeat(30 * 1024);
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: `base instructions ${filler} tail-marker`,
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "START");

    let seenPrompt = "";
    const delegates = makeMockDelegates({
      runAgent: async (stepName, prompt) => {
        seenPrompt = prompt;
        return { text: "<R>ok</R>", ok: true };
      },
    });

    const engine = new FlowEngine(flow, delegates);
    await engine.run();

    assert.ok(Buffer.byteLength(seenPrompt) <= 24 * 1024,
      `prompt seen by runAgent (${Buffer.byteLength(seenPrompt)}B) must be <= 24KB`);
    assert.ok(seenPrompt.startsWith("base instructions"), "head retained");
    assert.ok(seenPrompt.includes("tail-marker"), "tail retained");
    assert.match(seenPrompt, /PROMPT TRUNCATED/);
  });
});
