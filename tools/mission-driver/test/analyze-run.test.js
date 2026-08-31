import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, utimesSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { resolveTargetRun, resolveRunModule, buildRunSkeleton } from "../src/config.js";

// Helpers ---------------------------------------------------------------------

/** Create an empty temp project root. */
function tmpRoot() {
  return mkdtempSync(join(tmpdir(), "analyze-run-"));
}

/** Write a fake mission-driver run dir under <root>/_tmp/<id>-mission-driver. */
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

/** Set a directory's mtime/atime to now + msOffset (ms) for deterministic ordering. */
function setMtime(dir, msOffset) {
  const target = new Date(Date.now() + msOffset);
  utimesSync(dir, target, target);
}

// resolveTargetRun ------------------------------------------------------------

describe("resolveTargetRun", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { try { rmSync(root, { recursive: true, force: true }); } catch {} });

  it("returns {dir:null} when _tmp/ does not exist", () => {
    const r = resolveTargetRun(root, true);
    assert.equal(r.dir, null);
    assert.equal(r.id, null);
    assert.equal(r.isLatest, false);
  });

  it("returns {dir:null} when _tmp/ is empty", () => {
    mkdirSync(join(root, "_tmp"), { recursive: true });
    const r = resolveTargetRun(root, true);
    assert.deepEqual(r, { dir: null, id: null, isLatest: false });
  });

  it("ignores non-*-mission-driver directories", () => {
    makeRunDir(root, "2026-07-01-100000-mission-driver");
    mkdirSync(join(root, "_tmp", "some-other-dir"), { recursive: true });
    const r = resolveTargetRun(root, "some-other");
    assert.equal(r.dir, null, "must not match a non-mission-driver dir");
  });

  it("selects the newest by mtime and flags isLatest when sel===true", () => {
    const old = makeRunDir(root, "2026-07-01-100000-mission-driver");
    const newer = makeRunDir(root, "2026-07-01-200000-mission-driver");
    // Force deterministic mtimes: old in the past, newer more recent.
    setMtime(old, -60000);
    setMtime(newer, -1000);
    const r = resolveTargetRun(root, true);
    assert.equal(r.isLatest, true);
    assert.equal(r.id, "2026-07-01-200000-mission-driver");
    assert.equal(r.dir, newer);
  });

  it("matches exact directory name", () => {
    makeRunDir(root, "2026-07-01-100000-mission-driver");
    makeRunDir(root, "2026-07-01-200000-mission-driver");
    const r = resolveTargetRun(root, "2026-07-01-100000-mission-driver");
    assert.equal(r.id, "2026-07-01-100000-mission-driver");
    assert.equal(r.isLatest, false);
  });

  it("matches by prefix (suffix -mission-driver omitted)", () => {
    makeRunDir(root, "2026-07-01-100000-mission-driver");
    makeRunDir(root, "2026-07-01-200000-mission-driver");
    const r = resolveTargetRun(root, "2026-07-01-100000");
    assert.equal(r.id, "2026-07-01-100000-mission-driver");
  });

  it("matches by contains when exact/prefix fail", () => {
    makeRunDir(root, "2026-07-01-100000-mission-driver");
    makeRunDir(root, "2026-07-01-200000-mission-driver");
    const r = resolveTargetRun(root, "100000");
    assert.equal(r.id, "2026-07-01-100000-mission-driver");
  });

  it("returns {dir:null} when no directory matches the selector", () => {
    makeRunDir(root, "2026-07-01-100000-mission-driver");
    const r = resolveTargetRun(root, "totally-absent");
    assert.deepEqual(r, { dir: null, id: null, isLatest: false });
  });

  it("exact match takes priority over prefix/contains", () => {
    makeRunDir(root, "2026-07-01-100000-mission-driver");
    makeRunDir(root, "2026-07-01-100000"); // contains but not a mission-driver... actually filtered
    // Build a dir whose name is an exact prefix of another to test priority:
    makeRunDir(root, "abc-mission-driver");
    makeRunDir(root, "abcdef-mission-driver");
    const r = resolveTargetRun(root, "abc-mission-driver");
    assert.equal(r.id, "abc-mission-driver", "exact wins over the prefix abc");
  });
});

// buildRunSkeleton ------------------------------------------------------------

describe("buildRunSkeleton", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { try { rmSync(root, { recursive: true, force: true }); } catch {} });

  it("produces correct stats, step timeline, and red-flag logFile basenames from full artifacts", () => {
    const logFile = join(root, "_tmp", "2026-07-01-100000-mission-driver", "oc-EXECUTE-123-abcd.log");
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: {
        missionName: "demo-mission",
        runId: "2026-07-01-100000-mission-driver",
        status: "completed",
        startedAt: "2026-07-01T10:00:00.000Z",
        endedAt: "2026-07-01T14:23:00.000Z",
        steps: [
          { name: "CHECK", visits: 1, status: "completed", marker: "pass", durationMs: 48000, logFile: join(root, "_tmp", "2026-07-01-100000-mission-driver", "oc-CHECK-1.log"), produced: [] },
          { name: "EXECUTE", visits: 1, status: "failed", marker: "fail", durationMs: 289000, logFile, produced: [] },
        ],
      },
      events: [
        { type: "run_started" },
        { type: "step_started", step: "CHECK" },
        { type: "transition", from: "CHECK", to: "REVIEW", marker: "pass", via: "goto" },
        { type: "transition", from: "EXECUTE", to: "EXECUTE", marker: "fail", via: "retry" },
        { type: "transition", from: "EXECUTE", to: "EXECUTE", marker: "fail", via: "retry" },
        { type: "step_failed", step: "EXECUTE", visit: 1, marker: "fail", error: "boom" },
        { type: "limit_hit", step: "EXECUTE", limitType: "max_retries", count: 2, max: 1 },
        { type: "step_skipped", step: "CHECK_OPEN", visit: 1, reason: "when false" },
      ],
    });

    const skel = buildRunSkeleton(dir);

    assert.match(skel, /Mission: demo-mission/);
    assert.match(skel, /Run: 2026-07-01-100000-mission-driver/);
    assert.match(skel, /Status: completed/);
    assert.match(skel, /Total top-steps: 2/);
    assert.match(skel, /Wall: ~4h23m/);
    assert.match(skel, /Retries detected: 2/);
    assert.match(skel, /Limit hits: 1/);
    assert.match(skel, /Skipped steps: 1/);
    // step timeline entries
    assert.match(skel, /CHECK.+v1.+pass/);
    assert.match(skel, /EXECUTE.+v1.+fail/);
    // red flag must reference the log basename (not the full path)
    assert.match(skel, /oc-EXECUTE-123-abcd\.log/);
    // limit_hit count is in the summary line; the per-step limit detail shares
    // the same log basename, so it is deduped (basename shown once).
    assert.match(skel, /Limit hits: 1/);
    // skipped step surfaced
    assert.match(skel, /skipped/);
    assert.match(skel, /CHECK_OPEN/);
  });

  it("degrades without throwing when events.jsonl is missing", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: { missionName: "m", runId: "x", status: "completed", steps: [] },
      events: null, // do not write events.jsonl
    });
    const skel = buildRunSkeleton(dir);
    assert.match(skel, /Retries detected: 0/);
    assert.match(skel, /events\.jsonl/); // data completeness note
  });

  it("degrades without throwing when run-state.json is missing", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: null,
      events: [{ type: "step_failed", step: "X", marker: "fail" }],
    });
    const skel = buildRunSkeleton(dir);
    assert.match(skel, /run-state\.json missing/);
    assert.match(skel, /run-state\.json/); // data completeness note
    // events-derived counts still present
    assert.match(skel, /Retries detected: 0/);
  });

  it("degrades without throwing when both files are missing (empty run dir)", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", { state: null, events: null });
    const skel = buildRunSkeleton(dir);
    assert.ok(typeof skel === "string");
    assert.match(skel, /run-state\.json/);
  });

  it("keeps the output compact (<= 8KB)", () => {
    // Build a sizeable run with many steps + events.
    const steps = [];
    const events = [];
    for (let i = 0; i < 40; i++) {
      steps.push({ name: `STEP_${i}`, visits: 1, status: "completed", marker: "pass", durationMs: i * 1000, produced: [] });
      events.push({ type: "step_started", step: `STEP_${i}` });
    }
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: { missionName: "big", runId: "big", status: "completed", startedAt: "2026-07-01T00:00:00Z", endedAt: "2026-07-01T01:00:00Z", steps },
      events,
    });
    const skel = buildRunSkeleton(dir);
    assert.ok(Buffer.byteLength(skel) <= 8 * 1024, `skeleton too large: ${Buffer.byteLength(skel)} bytes`);
  });

  it("reports 'no red flags' for a clean run", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: { missionName: "clean", runId: "clean", status: "completed", steps: [
        { name: "CHECK", visits: 1, status: "completed", marker: "pass", durationMs: 1000, produced: [] },
      ] },
      events: [{ type: "run_started" }, { type: "transition", via: "goto", marker: "pass" }],
    });
    const skel = buildRunSkeleton(dir);
    assert.match(skel, /RED FLAGS: none detected/);
  });

  // bfrv-2 C2a — three new red-flag signal sources (report.json verdict,
  // subflow run-state-*.json glob, step_completed{marker ∈ FAILISH}).

  it("C2a signal #1: report.json with blocked>0 produces a verdict red flag", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      // Top-level state looks clean — no failed step, no step_failed event.
      // Without the report.json signal the skeleton would falsely say "none".
      state: { missionName: "m", runId: "blocked-run", status: "completed", steps: [
        { name: "AGGREGATE", visits: 1, status: "completed", marker: "pass", durationMs: 1000, produced: [] },
      ] },
      events: [{ type: "run_started" }, { type: "transition", via: "goto", marker: "pass" }],
    });
    writeFileSync(
      join(dir, "report.json"),
      JSON.stringify({ runId: "blocked-run", summary: { pass: 0, fail: 0, blocked: 1, noManifest: 0 }, issues: [] }),
      "utf8",
    );
    const skel = buildRunSkeleton(dir);
    assert.doesNotMatch(skel, /RED FLAGS: none detected/);
    assert.match(skel, /report\.json/);
    assert.match(skel, /verdict=blocked/);
    assert.match(skel, /blocked=1/);
  });

  it("C2a signal #1: report.json with fail>0 produces a verdict=failed red flag", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: { missionName: "m", runId: "fail-run", status: "completed", steps: [] },
      events: [],
    });
    writeFileSync(
      join(dir, "report.json"),
      JSON.stringify({ runId: "fail-run", summary: { pass: 0, fail: 2, blocked: 1, noManifest: 0 }, issues: [] }),
      "utf8",
    );
    const skel = buildRunSkeleton(dir);
    assert.match(skel, /verdict=failed/);
    assert.match(skel, /fail=2/);
  });

  it("C2a signal #2: subflow run-state-*.json with a failed step produces a red flag", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      // Top-level state has NO failed steps — the failure is hidden inside the
      // COLLECT_BACKEND subflow state file.
      state: { missionName: "m", runId: "sub-fail", status: "completed", steps: [
        { name: "VERIFY_EACH", visits: 1, status: "completed", marker: "pass", durationMs: 1000, produced: [] },
      ] },
      events: [],
    });
    writeFileSync(
      join(dir, "run-state-COLLECT_BACKEND-1-0.json"),
      JSON.stringify({
        steps: [
          { name: "COLLECT_BACKEND", visits: 1, status: "failed", marker: "failed", logFile: "/tmp/oc-COLLECT_BACKEND-abc.log" },
        ],
      }),
      "utf8",
    );
    const skel = buildRunSkeleton(dir);
    assert.doesNotMatch(skel, /RED FLAGS: none detected/);
    assert.match(skel, /oc-COLLECT_BACKEND-abc\.log/);
    assert.match(skel, /subflow run-state-COLLECT_BACKEND-1-0\.json/);
  });

  it("C2a signal #3: step_completed{marker:'failed'} event produces a red flag", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: { missionName: "m", runId: "evt-fail", status: "completed", steps: [
        { name: "VERIFY_EACH", visits: 1, status: "completed", marker: "pass", durationMs: 1000, produced: [] },
      ] },
      events: [
        { type: "run_started" },
        // engine.js:1655-1663 emits step_completed (NOT step_failed) for subflow steps.
        { type: "step_completed", step: "COLLECT_BACKEND", visit: 1, marker: "failed", logFile: "/tmp/oc-CB-fail.log" },
      ],
    });
    const skel = buildRunSkeleton(dir);
    assert.doesNotMatch(skel, /RED FLAGS: none detected/);
    assert.match(skel, /oc-CB-fail\.log/);
    assert.match(skel, /step_completed marker=failed/);
  });

  it("C2a negative: a fully green run (with report.json all-pass + no subflow failures) still reports 'none'", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: { missionName: "green", runId: "green", status: "completed", steps: [
        { name: "AGGREGATE", visits: 1, status: "completed", marker: "pass", durationMs: 1000, produced: [] },
      ] },
      events: [
        { type: "run_started" },
        { type: "step_completed", step: "CHECK", visit: 1, marker: "pass" },
      ],
    });
    // all-pass report → no verdict red flag
    writeFileSync(
      join(dir, "report.json"),
      JSON.stringify({ runId: "green", summary: { pass: 2, fail: 0, blocked: 0, noManifest: 0 }, issues: [] }),
      "utf8",
    );
    // a subflow state with only passing steps → no subflow red flag
    writeFileSync(
      join(dir, "run-state-COLLECT_UI-1-0.json"),
      JSON.stringify({ steps: [{ name: "COLLECT_UI", visits: 1, status: "completed", marker: "pass", logFile: "/tmp/ok.log" }] }),
      "utf8",
    );
    const skel = buildRunSkeleton(dir);
    assert.match(skel, /RED FLAGS: none detected/);
  });

  // WI5 — audit round progress surfaces in the skeleton so postmortem agents
  // know how many DEEP_AUDIT rounds were executed. Skipped entirely when the
  // flow has no audit concept (maxAuditRounds === 0).
  it("WI5: emits 'Audit rounds: N/M' when state.maxAuditRounds > 0", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: {
        missionName: "m", runId: "audit-run", status: "completed", steps: [],
        auditRound: 2, maxAuditRounds: 3,
      },
      events: [{ type: "run_started" }],
    });
    const skel = buildRunSkeleton(dir);
    assert.match(skel, /Audit rounds: 2\/3/);
  });

  it("WI5: omits the Audit rounds line when maxAuditRounds is 0 (audit-less flow)", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: { missionName: "m", runId: "plain", status: "completed", steps: [] },
      events: [{ type: "run_started" }],
    });
    const skel = buildRunSkeleton(dir);
    assert.doesNotMatch(skel, /Audit rounds:/);
  });

  it("WI5: falls back to 0 for legacy run-state.json missing the fields", () => {
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: {
        missionName: "m", runId: "legacy", status: "completed", steps: [],
        maxAuditRounds: 2,
        // auditRound field intentionally absent (legacy run-state.json pre-WI1)
      },
      events: [{ type: "run_started" }],
    });
    const skel = buildRunSkeleton(dir);
    assert.match(skel, /Audit rounds: 0\/2/);
  });
});

// resolveRunModule ------------------------------------------------------------

describe("resolveRunModule", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { try { rmSync(root, { recursive: true, force: true }); } catch {} });

  function writeMission(name, body) {
    const missionsDir = join(root, "missions");
    mkdirSync(missionsDir, { recursive: true });
    writeFileSync(join(missionsDir, `${name}.json`), JSON.stringify(body), "utf8");
    return missionsDir;
  }

  it("resolves moduleName + moduleMemoryDir from mission.json with moduleDir", () => {
    const missionsDir = writeMission("demo-mission", { name: "demo-mission", moduleDir: "demo-mod", roadmapPath: "x", plansDir: "y", commands: { test: "t" } });
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: { missionName: "demo-mission", steps: [] },
      events: [],
    });
    const r = resolveRunModule(root, missionsDir, dir);
    assert.deepEqual(r, {
      moduleName: "demo-mod",
      moduleMemoryDir: join(root, "docs", "memory", "demo-mod"),
    });
  });

  it("falls back to the mission name when moduleDir is empty", () => {
    const missionsDir = writeMission("aas-work", { name: "aas-work", moduleDir: "", roadmapPath: "x", plansDir: "y", commands: { test: "t" } });
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: { missionName: "aas-work", steps: [] },
      events: [],
    });
    const r = resolveRunModule(root, missionsDir, dir);
    assert.equal(r.moduleName, "aas-work");
  });

  it("returns null when mission.json is missing", () => {
    const missionsDir = join(root, "missions");
    mkdirSync(missionsDir, { recursive: true });
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: { missionName: "no-such-mission", steps: [] },
      events: [],
    });
    const r = resolveRunModule(root, missionsDir, dir);
    assert.equal(r, null);
  });

  it("resolves an arbitrary moduleDir to its basename", () => {
    const missionsDir = writeMission("mystery", { name: "mystery", moduleDir: "something", roadmapPath: "x", plansDir: "y", commands: { test: "t" } });
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", {
      state: { missionName: "mystery", steps: [] },
      events: [],
    });
    const r = resolveRunModule(root, missionsDir, dir);
    assert.equal(r.moduleName, "something");
  });

  it("returns null when run-state.json is missing", () => {
    const missionsDir = writeMission("demo-mission", { name: "demo-mission", moduleDir: "demo-mod", roadmapPath: "x", plansDir: "y", commands: { test: "t" } });
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", { state: null, events: [] });
    const r = resolveRunModule(root, missionsDir, dir);
    assert.equal(r, null);
  });

  it("does not throw on an unparseable run-state.json", () => {
    const missionsDir = writeMission("demo-mission", { name: "demo-mission", moduleDir: "demo-mod", roadmapPath: "x", plansDir: "y", commands: { test: "t" } });
    const dir = makeRunDir(root, "2026-07-01-100000-mission-driver", { state: null, events: [] });
    writeFileSync(join(dir, "run-state.json"), "{ this is not json", "utf8");
    const r = resolveRunModule(root, missionsDir, dir);
    assert.equal(r, null);
  });
});
