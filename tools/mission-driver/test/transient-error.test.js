import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, readFileSync, existsSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { FlowEngine, isTransientProviderError, isQuotaExhaustion, extractQuotaResetTime, quotaWaitMs } from "../src/engine.js";
import { resolveConfig } from "../src/config.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

// mdr-1 Phase 2 — isTransientProviderError pure classifier.
describe("isTransientProviderError — stderr-signature classification (mdr-1 Phase 2)", () => {
  it("empty output (no stderr) → null (cause unknown, NOT transient)", () => {
    assert.equal(
      isTransientProviderError({ exitCode: 1, stderrTail: "", stepDurMs: 4000, logLen: 0 }),
      null,
    );
    assert.equal(
      isTransientProviderError({ exitCode: 137, stderrTail: null }),
      null,
    );
    assert.equal(isTransientProviderError({}), null);
  });

  it("429 signature in stderr → transient", () => {
    const sig = isTransientProviderError({
      exitCode: 1, stderrTail: "HTTP 429 Too Many Requests", stepDurMs: 3000, logLen: 100,
    });
    assert.ok(sig, "must return a truthy signature");
  });

  it("rate_limit / quota / overloaded signatures → transient", () => {
    assert.ok(isTransientProviderError({ stderrTail: "Error: rate_limit exceeded" }));
    assert.ok(isTransientProviderError({ stderrTail: "quota exhausted for org" }));
    assert.ok(isTransientProviderError({ stderrTail: "server overloaded, retry later" }));
    assert.ok(isTransientProviderError({ stderrTail: "service unavailable" }));
  });

  it("normal failure (stderr without a signature) → null", () => {
    assert.equal(
      isTransientProviderError({ exitCode: 1, stderrTail: "SyntaxError: unexpected token", logLen: 500 }),
      null,
    );
    assert.equal(
      isTransientProviderError({ exitCode: 0, stderrTail: "ENOENT: no such file" }),
      null,
    );
  });

  it("signature match is case-insensitive", () => {
    assert.ok(isTransientProviderError({ stderrTail: "RATE_LIMIT hit" }));
    assert.ok(isTransientProviderError({ stderrTail: "Overloaded" }));
  });

  // mdr-quota — the observed zhipu 5h-quota message must classify as transient
  // (it was previously missed by every signature → misclassified as a genuine
  // failure → consumed onError budget → whole mission failed).
  it("zhipu 5h quota message (Chinese) → transient AND quota exhaustion", () => {
    const msg = "Error: 已达到 5 小时的使用上限。您的限额将在 2026-08-15 01:12:08 重置。";
    assert.ok(isTransientProviderError({ stderrTail: msg }), "must be classified transient");
    assert.ok(isQuotaExhaustion(msg), "must be classified quota exhaustion");
    assert.ok(isQuotaExhaustion("usage limit reached for this project"));
    assert.ok(isQuotaExhaustion("daily limit exceeded"));
    assert.equal(isQuotaExhaustion("HTTP 429 Too Many Requests"), null, "plain 429 is NOT quota exhaustion (generic rate-limit)");
    assert.equal(isQuotaExhaustion("SyntaxError: unexpected token"), null);
    assert.equal(isQuotaExhaustion(""), null);
  });

  // mdr-quota — reset-time extraction.
  it("extractQuotaResetTime parses the announced reset time (local format)", () => {
    const future = new Date(Date.now() + 3_600_000);
    const ts = `${future.getFullYear()}-${String(future.getMonth() + 1).padStart(2, "0")}-${String(future.getDate()).padStart(2, "0")} ${String(future.getHours()).padStart(2, "0")}:${String(future.getMinutes()).padStart(2, "0")}:${String(future.getSeconds()).padStart(2, "0")}`;
    const t = extractQuotaResetTime(`Error: 已达到 5 小时的使用上限。您的限额将在 ${ts} 重置。`);
    assert.ok(Number.isFinite(t), "must extract a numeric epoch");
    assert.ok(t > Date.now(), "must be in the future");
  });

  it("extractQuotaResetTime parses ISO reset times", () => {
    const t = extractQuotaResetTime("usage limit reached — resets at 2099-01-01T00:00:00Z");
    assert.ok(Number.isFinite(t) && t > Date.now(), "ISO reset must parse");
  });

  it("extractQuotaResetTime → null for past/absent/unparseable reset times", () => {
    assert.equal(extractQuotaResetTime("Error: 已达到 5 小时的使用上限。"), null, "no reset marker/time → null");
    assert.equal(extractQuotaResetTime("quota exhausted, no reset info"), null);
    assert.equal(extractQuotaResetTime("resets at 01:12:08"), null, "time-of-day without a date → null");
    assert.equal(extractQuotaResetTime(""), null);
  });

  it("quotaWaitMs: reset-aware vs fallback", () => {
    const future = new Date(Date.now() + 10_000);
    const ts = `${future.getFullYear()}-${String(future.getMonth() + 1).padStart(2, "0")}-${String(future.getDate()).padStart(2, "0")} ${String(future.getHours()).padStart(2, "0")}:${String(future.getMinutes()).padStart(2, "0")}:${String(future.getSeconds()).padStart(2, "0")}`;
    const cfg = { quotaResetBufferMs: 5_000, quotaWaitFallbackMs: 123_456 };
    const w = quotaWaitMs(`您的限额将在 ${ts} 重置。`, cfg);
    assert.ok(w >= 10_000 && w < 30_000, `wait must be ~reset+buffer (got ${w})`);
    assert.equal(quotaWaitMs("quota exhausted, no reset info", cfg), 123_456, "unparseable → fallback");
    assert.equal(quotaWaitMs("", cfg), 123_456);
    assert.equal(quotaWaitMs("usage limit reached", {}), 600_000, "default fallback = 10 min");
  });
});

// mdr-1 Phase 2 — header-only / extremely short output must NOT trigger the
// runParseAgent LLM fallback (memory L001).
describe("FlowEngine — header-only output short-circuits parse fallback (mdr-1 Phase 2)", () => {
  it("header-only result.text does NOT call runParseAgent", async () => {
    let parseCalls = 0;
    const delegates = makeMockDelegates({
      responses: {
        START: {
          // Only the executor header — no real AI output, no result tag.
          text: "# cmd: opencode run\n# cwd: /repo\n# started: 2026-07-02T11:00:00\n\n",
          ok: true,
        },
      },
      runParseAgent() { parseCalls++; return { text: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>", ok: true }; },
    });
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { pass: { done: "completed" } },
        onUnknown: { done: "failed" },
      },
    });
    const engine = new FlowEngine(flow, delegates);
    const res = await engine.run();
    assert.equal(parseCalls, 0, "runParseAgent must NOT be invoked on header-only output");
    // header-only → no marker → onUnknown → failed
    assert.equal(res.status, "failed");
  });

  it("real (long) output without a tag DOES invoke runParseAgent (regression guard)", async () => {
    let parseCalls = 0;
    const longBody = "I analyzed the plan and finished the work successfully. " + "x".repeat(200);
    const delegates = makeMockDelegates({
      responses: { START: { text: longBody, ok: true } },
      runParseAgent() { parseCalls++; return { text: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>", ok: true }; },
    });
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { pass: { done: "completed" } },
        onUnknown: { done: "failed" },
      },
    });
    const engine = new FlowEngine(flow, delegates);
    await engine.run();
    assert.ok(parseCalls >= 1, "runParseAgent SHOULD be invoked when there is real body to parse");
  });
});

// mdr-1 Phase 3 — independent transient retry path.
// A transient provider error (rate-limit/quota/overload) is retried on its OWN
// budget: it does NOT consume onError.maxRetries, does NOT emit step_failed
// (emits transient_retry), and does NOT trip maxCycleVisits. Exceeding the
// transient hard cap degrades to a real failure.
function readEvents(runDir) {
  const f = join(runDir, "events.jsonl");
  if (!existsSync(f)) return [];
  return readFileSync(f, "utf8").split("\n").filter(Boolean).map((l) => JSON.parse(l));
}

// Build a transient-failure response (empty output + 429 stderr signature).
const transientFail = () => ({ ok: false, text: "", exitCode: 1, stderrTail: "HTTP 429 Too Many Requests, rate_limit exceeded" });

describe("FlowEngine — transient retry independence (mdr-1 Phase 3)", () => {
  it("(a) transient failures retry on own budget — do NOT consume onError.maxRetries and do NOT emit step_failed", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-trans-a-"));
    try {
      // Step fails transiently twice, then succeeds.
      let calls = 0;
      const delegates = makeMockDelegates({
        config: {
          moduleName: "test-mod", shortName: "test-mod", packageFilter: "x",
          projectRoot: runDir, runDir, missionName: "t",
          // generous transient budget; tiny backoff so the test is fast.
          transient: { enabled: true, maxRetries: 3, backoffBaseMs: 1, backoffCapMs: 2 },
        },
        responses: {
          START: () => { calls++; return calls <= 2 ? transientFail() : { text: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>", ok: true }; },
        },
      });
      const flow = simpleFlow({
        START: {
          type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
          // onError has a TIGHT budget (1). If transient retries consumed it,
          // the 2nd transient failure would exhaust onError and force failure.
          onError: { retry: "START", maxRetries: 1 },
          transitions: { pass: { done: "completed" } },
        },
      });
      const engine = new FlowEngine(flow, delegates);
      const res = await engine.run();

      assert.equal(res.status, "completed", "must recover via transient retry, not die on onError");

      const events = readEvents(runDir);
      assert.equal(events.filter((e) => e.type === "transient_retry").length, 2, "two transient_retry events");
      assert.equal(events.filter((e) => e.type === "step_failed").length, 0, "step_failed must NOT be emitted for transient retries");

      // onError budget untouched: the onError retry key is "START→START".
      assert.equal(engine.retryCounts.get("START→START") || 0, 0, "onError.maxRetries must NOT be consumed by transient retries");
      // Cycle invisibility: 3 executions but only 1 recorded visit (transient
      // retries roll back the visit increment so maxCycleVisits can't trip).
      assert.equal(engine.visitCounts.get("START"), 1, "transient retries must be invisible to maxCycleVisits");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("(b) exceeding the transient budget degrades to a real failure (step_failed + onError)", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-trans-b-"));
    try {
      const delegates = makeMockDelegates({
        config: {
          moduleName: "test-mod", shortName: "test-mod", packageFilter: "x",
          projectRoot: runDir, runDir, missionName: "t",
          transient: { enabled: true, maxRetries: 2, backoffBaseMs: 1, backoffCapMs: 2 },
        },
        responses: { START: transientFail }, // always transient-fails
      });
      const flow = simpleFlow({
        START: {
          type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
          onError: { done: "failed" },
          transitions: { pass: { done: "completed" } },
        },
      });
      const engine = new FlowEngine(flow, delegates);
      const res = await engine.run();

      assert.equal(res.status, "failed", "must degrade to failed once the transient budget is exhausted");
      const events = readEvents(runDir);
      assert.equal(events.filter((e) => e.type === "transient_retry").length, 2, "exactly maxRetries transient_retry events before degradation");
      assert.ok(events.filter((e) => e.type === "step_failed").length >= 1, "step_failed MUST be emitted once transient budget is exhausted");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("(c.1) _transientConfig hard defaults when config.transient is absent", () => {
    const engine = new FlowEngine(simpleFlow({ START: { type: "agent", prompt: "x", transitions: { pass: { done: "completed" } } } }), makeMockDelegates({}));
    const t = engine._transientConfig();
    assert.equal(t.enabled, true);
    assert.equal(t.maxRetries, 6);
    assert.equal(t.backoffBaseMs, 5_000);
    assert.equal(t.backoffCapMs, 120_000);
    assert.equal(t.quotaWaitFallbackMs, 600_000, "quota fallback defaults to 10 min");
    assert.equal(t.quotaResetBufferMs, 60_000, "quota buffer defaults to 1 min");
    assert.equal(t.quotaMaxWaitMs, 0, "quota max wait defaults to unlimited");
  });

  it("(c.2) _transientConfig honors an override (incl. enabled:false and partial fields)", () => {
    const full = new FlowEngine(
      simpleFlow({ START: { type: "agent", prompt: "x", transitions: { pass: { done: "completed" } } } }),
      makeMockDelegates({ config: { transient: { enabled: true, maxRetries: 9, backoffBaseMs: 1_000, backoffCapMs: 30_000 } } }),
    );
    const t = full._transientConfig();
    assert.equal(t.maxRetries, 9);
    assert.equal(t.backoffBaseMs, 1_000);
    assert.equal(t.backoffCapMs, 30_000);

    const off = new FlowEngine(
      simpleFlow({ START: { type: "agent", prompt: "x", transitions: { pass: { done: "completed" } } } }),
      makeMockDelegates({ config: { transient: { enabled: false } } }),
    );
    assert.equal(off._transientConfig().enabled, false, "enabled:false must propagate (disables the transient path)");
  });

  // mdr-quota — quota/usage-limit exhaustion: wait-until-reset (or fallback),
  // unlimited retries, NO budget consumption, NO step_failed, NO visit trips.
  describe("FlowEngine — quota exhaustion wait-and-retry (mdr-quota)", () => {
    function fmtLocal(d) {
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}:${String(d.getSeconds()).padStart(2, "0")}`;
    }
    // zhipu-style message with the reset announced `inMs` from call time.
    const zhipuQuotaFail = (inMs) => () => ({
      ok: false, text: "", exitCode: 1,
      stderrTail: `Error: 已达到 5 小时的使用上限。您的限额将在 ${fmtLocal(new Date(Date.now() + inMs))} 重置。`,
    });

    it("(q.1) quota exhaustion waits for the reset then retries — unlimited, budgets untouched", async () => {
      const runDir = mkdtempSync(join(tmpdir(), "md-quota-a-"));
      try {
        let calls = 0;
        const delegates = makeMockDelegates({
          config: {
            moduleName: "test-mod", shortName: "test-mod", packageFilter: "x",
            projectRoot: runDir, runDir, missionName: "t",
            // transient budget is ZERO — only the quota path can save us.
            transient: { enabled: true, maxRetries: 0, backoffBaseMs: 1, backoffCapMs: 2, quotaResetBufferMs: 1, quotaWaitFallbackMs: 10 },
          },
          responses: {
            START: () => { calls++; return calls === 1 ? zhipuQuotaFail(80)() : { text: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>", ok: true }; },
          },
        });
        const flow = simpleFlow({
          START: {
            type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
            onError: { done: "failed" },
            transitions: { pass: { done: "completed" } },
          },
        });
        const engine = new FlowEngine(flow, delegates);
        const res = await engine.run();

        assert.equal(res.status, "completed", "must wait out the quota and complete");
        assert.equal(calls, 2, "one quota wait, then success");
        const events = readEvents(runDir);
        assert.equal(events.filter((e) => e.type === "quota_wait").length, 1, "one quota_wait event");
        assert.equal(events.filter((e) => e.type === "transient_retry").length, 0, "transient budget NOT used");
        assert.equal(events.filter((e) => e.type === "step_failed").length, 0, "no step_failed");
        assert.equal(engine.retryCounts.get("START→START") || 0, 0, "onError budget NOT consumed");
        assert.equal(engine.transientCounts.get("START") || 0, 0, "transient counter untouched");
        assert.equal(engine.visitCounts.get("START"), 1, "quota waits invisible to maxCycleVisits");
      } finally {
        rmSync(runDir, { recursive: true, force: true });
      }
    });

    it("(q.2) quota exhaustion without a parseable reset time falls back to the fixed wait", async () => {
      const runDir = mkdtempSync(join(tmpdir(), "md-quota-b-"));
      try {
        let calls = 0;
        const delegates = makeMockDelegates({
          config: {
            moduleName: "test-mod", shortName: "test-mod", packageFilter: "x",
            projectRoot: runDir, runDir, missionName: "t",
            transient: { enabled: true, maxRetries: 0, backoffBaseMs: 1, backoffCapMs: 2, quotaWaitFallbackMs: 15, quotaResetBufferMs: 1 },
          },
          responses: {
            START: () => { calls++; return calls === 1 ? { ok: false, text: "", exitCode: 1, stderrTail: "Error: 已达到 5 小时的使用上限。" } : { text: "<AI_STEP_RESULT>pass</AI_STEP_RESULT>", ok: true }; },
          },
        });
        const flow = simpleFlow({
          START: {
            type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
            onError: { done: "failed" },
            transitions: { pass: { done: "completed" } },
          },
        });
        const engine = new FlowEngine(flow, delegates);
        const res = await engine.run();
        assert.equal(res.status, "completed", "must recover via the fallback quota wait");
        const events = readEvents(runDir);
        const qw = events.filter((e) => e.type === "quota_wait");
        assert.equal(qw.length, 1, "one quota_wait event");
        assert.equal(qw[0].waitMs, 15, "fallback wait used when reset time unparseable");
      } finally {
        rmSync(runDir, { recursive: true, force: true });
      }
    });

    it("(q.3) quotaMaxWaitMs caps total quota waiting — exceeding it degrades to a real failure", async () => {
      const runDir = mkdtempSync(join(tmpdir(), "md-quota-c-"));
      try {
        const delegates = makeMockDelegates({
          config: {
            moduleName: "test-mod", shortName: "test-mod", packageFilter: "x",
            projectRoot: runDir, runDir, missionName: "t",
            transient: { enabled: true, maxRetries: 0, backoffBaseMs: 1, backoffCapMs: 2, quotaWaitFallbackMs: 10, quotaResetBufferMs: 1, quotaMaxWaitMs: 5 },
          },
          responses: { START: { ok: false, text: "", exitCode: 1, stderrTail: "Error: 已达到 5 小时的使用上限。" } },
        });
        const flow = simpleFlow({
          START: {
            type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
            onError: { done: "failed" },
            transitions: { pass: { done: "completed" } },
          },
        });
        const engine = new FlowEngine(flow, delegates);
        const res = await engine.run();
        assert.equal(res.status, "failed", "quota wait cap exceeded → real failure");
        const events = readEvents(runDir);
        assert.equal(events.filter((e) => e.type === "quota_wait").length, 0, "no quota_wait when the cap rejects the wait");
        assert.ok(events.filter((e) => e.type === "step_failed").length >= 1, "degraded to step_failed");
      } finally {
        rmSync(runDir, { recursive: true, force: true });
      }
    });
  });
});

describe("config.js — transient.* resolution (mdr-1 Phase 3)", () => {
  // Shared helper: stand up a minimal valid mission (roadmapPath + plansDir
  // must exist on disk; commands.test is required by validateMission).
  function setupMission(root, missionName, missionFields = {}) {
    const missionsDir = join(root, "missions");
    mkdirSync(missionsDir, { recursive: true });
    mkdirSync(join(root, "docs", "backlog"), { recursive: true });
    mkdirSync(join(root, "docs", "plans", "x"), { recursive: true });
    writeFileSync(join(root, "docs", "backlog", "x.md"), "# roadmap\n");
    writeFileSync(join(missionsDir, `${missionName}.json`), JSON.stringify({
      name: missionName,
      roadmapPath: "docs/backlog/x.md",
      plansDir: "docs/plans/x",
      commands: { test: "echo ok" },
      ...missionFields,
    }));
  }

  it("(c.3) resolveConfig populates transient defaults from a mission without transient block", () => {
    const root = mkdtempSync(join(tmpdir(), "md-cfg-def-"));
    try {
      setupMission(root, "trans-cfg-default");
      const cfg = resolveConfig({ dir: root, mission: "trans-cfg-default" });
      assert.equal(cfg.transient.enabled, true);
      assert.equal(cfg.transient.maxRetries, 6);
      assert.equal(cfg.transient.backoffBaseMs, 5_000);
      assert.equal(cfg.transient.backoffCapMs, 120_000);
      assert.equal(cfg.transient.quotaWaitFallbackMs, 600_000, "quota fallback defaults to 10 min");
      assert.equal(cfg.transient.quotaResetBufferMs, 60_000, "quota buffer defaults to 1 min");
      assert.equal(cfg.transient.quotaMaxWaitMs, 0, "quota max wait defaults to unlimited");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("(c.4) resolveConfig honors mission.transient overrides", () => {
    const root = mkdtempSync(join(tmpdir(), "md-cfg-ovr-"));
    try {
      setupMission(root, "trans-cfg-override", {
        transient: { maxRetries: 4, backoffBaseMs: 2_000, backoffCapMs: 60_000, quotaWaitFallbackMs: 300_000, quotaResetBufferMs: 30_000, quotaMaxWaitMs: 3_600_000 },
      });
      const cfg = resolveConfig({ dir: root, mission: "trans-cfg-override" });
      assert.equal(cfg.transient.maxRetries, 4);
      assert.equal(cfg.transient.backoffBaseMs, 2_000);
      assert.equal(cfg.transient.backoffCapMs, 60_000);
      assert.equal(cfg.transient.quotaWaitFallbackMs, 300_000);
      assert.equal(cfg.transient.quotaResetBufferMs, 30_000);
      assert.equal(cfg.transient.quotaMaxWaitMs, 3_600_000);
      assert.equal(cfg.transient.enabled, true, "enabled defaults true when not specified");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("(c.5) resolveConfig honors mission.transient.enabled:false", () => {
    const root = mkdtempSync(join(tmpdir(), "md-cfg-off-"));
    try {
      setupMission(root, "trans-cfg-off", { transient: { enabled: false } });
      const cfg = resolveConfig({ dir: root, mission: "trans-cfg-off" });
      assert.equal(cfg.transient.enabled, false);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});
