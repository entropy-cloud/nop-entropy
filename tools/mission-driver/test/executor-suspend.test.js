import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, readFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { detectSuspendJump, emitSuspendEvent } from "../src/executor.js";

// OPT-7: wall-clock jump detection. setInterval freezes during OS sleep; on
// wake the gap between beats overshoots the expected interval. detectSuspendJump
// is a pure function — fully testable without timers.
describe("executor.js — detectSuspendJump (OPT-7)", () => {
  const interval = 5 * 60_000; // LIVENESS_CHECK_MS

  it("returns null for a normal heartbeat gap (≈ interval)", () => {
    const last = 1_000_000;
    const now = last + interval + 5_000; // 5s jitter past the interval
    assert.equal(detectSuspendJump(now, last, interval), null);
  });

  it("returns null when the gap overshoots by less than the threshold", () => {
    const last = 1_000_000;
    // overshoot exactly at the threshold (10min) is NOT a suspend (strict >)
    const now = last + interval + 10 * 60_000;
    assert.equal(detectSuspendJump(now, last, interval), null);
  });

  it("detects a suspend when the gap overshoots the interval beyond the threshold", () => {
    const last = 1_000_000;
    const gapMs = interval + 62 * 60_000; // 62min past the interval → system slept ~67min
    const now = last + gapMs;
    const jump = detectSuspendJump(now, last, interval);
    assert.ok(jump, "must return a jump object");
    assert.equal(jump.gapMs, gapMs);
    assert.equal(jump.overshootMs, gapMs - interval);
  });

  it("respects a custom threshold", () => {
    const last = 1_000_000;
    const now = last + interval + 2 * 60_000; // 2min overshoot
    // default 10min threshold → not a suspend
    assert.equal(detectSuspendJump(now, last, interval), null);
    // custom 1min threshold → suspend
    const jump = detectSuspendJump(now, last, interval, 60_000);
    assert.ok(jump);
    assert.equal(jump.overshootMs, 2 * 60_000);
  });

  it("ignores backward clock skew (NTP correction, gap < 0)", () => {
    const last = 1_000_000;
    const now = last - 5_000; // clock moved backward
    assert.equal(detectSuspendJump(now, last, interval), null);
  });

  it("returns null for non-finite inputs", () => {
    assert.equal(detectSuspendJump(NaN, 1, interval), null);
    assert.equal(detectSuspendJump(1, NaN, interval), null);
    assert.equal(detectSuspendJump(1, 1, NaN), null);
    assert.equal(detectSuspendJump(undefined, 1, interval), null);
  });

  it("returns null for a gap exactly equal to the interval (no overshoot)", () => {
    const last = 1_000_000;
    const now = last + interval; // perfect cadence
    assert.equal(detectSuspendJump(now, last, interval), null);
  });
});

// OPT-7: emitSuspendEvent writes a `suspended` line to events.jsonl (the monitor
// SSE stream) and notifies the engine via config.onSuspend. Isolated → no timers.
describe("executor.js — emitSuspendEvent (OPT-7)", () => {
  let dir;

  function makeCtx() {
    dir = mkdtempSync(join(tmpdir(), "md-suspend-emit-"));
    return dir;
  }

  it("appends a suspended event to events.jsonl and calls config.onSuspend", () => {
    const runDir = makeCtx();
    try {
      const received = [];
      const config = {
        runDir,
        missionName: "suspend-mission",
        onSuspend: (p) => received.push(p),
      };
      const jump = { gapMs: 62 * 60_000, overshootMs: 57 * 60_000 };

      emitSuspendEvent(config, "EXECUTE", 4242, jump);

      const eventsFile = join(runDir, "events.jsonl");
      assert.ok(existsSync(eventsFile), "events.jsonl must be created");
      const lines = readFileSync(eventsFile, "utf8").trim().split("\n");
      assert.equal(lines.length, 1);
      const ev = JSON.parse(lines[0]);
      assert.equal(ev.type, "suspended");
      assert.equal(ev.label, "EXECUTE");
      assert.equal(ev.pid, 4242);
      assert.equal(ev.missionName, "suspend-mission");
      assert.equal(ev.gapMs, jump.gapMs);
      assert.equal(ev.overshootMs, jump.overshootMs);
      assert.equal(ev.runId, runDir.split(/[\\/]/).pop());
      assert.ok(ev.ts, "ts must be present");
      // onSuspend forwarded with the same payload
      assert.equal(received.length, 1);
      assert.equal(received[0].type, "suspended");
      assert.equal(received[0].gapMs, jump.gapMs);
    } finally {
      try { rmSync(dir, { recursive: true, force: true }); } catch {}
    }
  });

  it("still calls onSuspend when runDir is absent (no file write, but engine is notified)", () => {
    const received = [];
    const config = { runDir: null, missionName: "x", onSuspend: (p) => received.push(p) };
    const jump = { gapMs: 1000, overshootMs: 500 };

    emitSuspendEvent(config, "STEP", 1, jump);

    assert.equal(received.length, 1);
    assert.equal(received[0].label, "STEP");
  });

  it("does not throw when onSuspend is absent (optional callback)", () => {
    const runDir = makeCtx();
    try {
      const config = { runDir, missionName: "y" };
      assert.doesNotThrow(() => emitSuspendEvent(config, "S", 9, { gapMs: 1, overshootMs: 1 }));
      // event still written
      const lines = readFileSync(join(runDir, "events.jsonl"), "utf8").trim().split("\n");
      assert.equal(lines.length, 1);
    } finally {
      try { rmSync(dir, { recursive: true, force: true }); } catch {}
    }
  });

  it("tolerates a throwing onSuspend (swallowed, event still written)", () => {
    const runDir = makeCtx();
    try {
      const config = {
        runDir,
        missionName: "z",
        onSuspend: () => { throw new Error("boom"); },
      };
      assert.doesNotThrow(() => emitSuspendEvent(config, "S", 1, { gapMs: 1, overshootMs: 1 }));
      const lines = readFileSync(join(runDir, "events.jsonl"), "utf8").trim().split("\n");
      assert.equal(lines.length, 1);
    } finally {
      try { rmSync(dir, { recursive: true, force: true }); } catch {}
    }
  });
});
