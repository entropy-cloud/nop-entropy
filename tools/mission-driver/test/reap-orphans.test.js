import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { reapStartupOrphans } from "../src/reap-orphans.mjs";
import { registerActiveRun } from "../src/active-run-registry.mjs";
import { mkdtempSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

// ── helpers ───────────────────────────────────────────────────────────────

// A PID that is very unlikely to be a live process; used for "dead" scenarios.
const DEAD_PID = 9_999_899;

function withTempRegistry(fn) {
  return async () => {
    const dir = mkdtempSync(join(tmpdir(), "reaper-reg-"));
    try {
      await fn(dir);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  };
}

// REALISTIC driver cmdline: `node .../src/main.js <missionName>` — contains
// main.js + missionName but NOT runId (runDir is generated internally, never a
// CLI arg). This is what isAliveAndOurs matches on, so mocks must mirror it or
// the test passes for the wrong reason (review C2).
function driverProc(pid, missionName) {
  return {
    pid,
    ppid: 1,
    pgid: 0,
    rss_kb: 100 * 1024,
    name: "node",
    cmd: `node /repo/tools/mission-driver/src/main.js ${missionName}`,
  };
}

// opencode child spawned by runner.js: cmdline has `opencode run` + the
// [MISSION_DRIVER:<runId>] tag. ppid = the driver that spawned it.
function ocProc(pid, ppid, runId, prompt = "do the work") {
  const tag = runId ? `[MISSION_DRIVER:${runId}]` : "[MISSION_DRIVER]";
  return {
    pid,
    ppid,
    pgid: pid,
    rss_kb: 200 * 1024,
    name: "opencode",
    cmd: `opencode run -m model --agent build --dangerously-skip-permissions ${tag} ${prompt}`,
  };
}

// Capture [reaper] stderr lines so we can assert "sparing" vs "killing" without
// depending on the return array alone.
function captureStderr(fn) {
  const chunks = [];
  const orig = process.stderr.write.bind(process.stderr);
  process.stderr.write = (s) => { chunks.push(String(s)); return true; };
  try {
    fn();
  } finally {
    process.stderr.write = orig;
  }
  return chunks.join("");
}

// ── Scenario 1: single run, own opencode is excluded → no kills ───────────

describe("reaper — scenario 1: own run's opencode is never reaped", () => {
  it("excludes the current run's opencode by excludePpid (self-protection)", () => {
    const procs = [
      driverProc(process.pid, "mA"),
      ocProc(50001, process.pid, "runOwn"),
    ];
    const res = reapStartupOrphans(null, process.pid, procs, { ownRunId: "runOwn" });
    assert.equal(res.killed.length, 0, "own opencode must not be reaped");
  });
});

// ── Scenario 2: two concurrent runs → B spares A ──────────────────────────

describe("reaper — scenario 2: spare an active concurrent run", () => {
  it("registry-seeded: spares run-A's opencode when run-B starts", withTempRegistry((dir) => {
    registerActiveRun({ runId: "runA", driverPid: process.pid, missionName: "mA", dir });
    const procs = [
      driverProc(process.pid, "mA"),
      ocProc(50010, process.pid, "runA"),
    ];
    const err = captureStderr(() => {
      // run-B is starting: its own runId is runB, its pid is some other value.
      const res = reapStartupOrphans(null, 777777, procs, { ownRunId: "runB", registryDir: dir });
      assert.equal(res.killed.length, 0, "active concurrent run must be SPARED");
    });
    assert.match(err, /sparing PID 50010 — active concurrent run runA/);
  }));
});

// ── Scenario 3: crashed run, registry seeded, dead driver → reap ──────────

describe("reaper — scenario 3: reap a confirmed-dead run's orphan", () => {
  it("isAliveAndOurs false (driver dead) → reap with 'dead run' reason", withTempRegistry((dir) => {
    registerActiveRun({ runId: "runDead", driverPid: DEAD_PID, missionName: "mDead", dir });
    const procs = [
      driverProc(DEAD_PID, "mDead"),
      ocProc(50020, DEAD_PID, "runDead"),
    ];
    const res = reapStartupOrphans(null, process.pid, procs, { ownRunId: "runOther", registryDir: dir });
    const reaped = res.killed.find((k) => k.pid === 50020);
    assert.ok(reaped, "dead run's opencode must be reaped");
    assert.match(reaped.reason, /dead run runDead/);
  }));
});

// ── Scenario 4: PID reuse → isAliveAndOurs false → reap ───────────────────

describe("reaper — scenario 4: PID reuse (alive pid, cmdline not ours)", () => {
  it("driverPid alive but recycled by an unrelated process → judged dead → reap", withTempRegistry((dir) => {
    // Registry believes driverPid = process.pid belongs to runX, but the live
    // process at that PID is now an unrelated program (PID was recycled).
    registerActiveRun({ runId: "runX", driverPid: process.pid, missionName: "mX", dir });
    const procs = [
      { pid: process.pid, ppid: 1, pgid: 0, rss_kb: 1, name: "other", cmd: "some-unrelated-program --foo" },
      ocProc(50030, process.pid, "runX"),
    ];
    const res = reapStartupOrphans(null, 777777, procs, { ownRunId: "runOther", registryDir: dir });
    const reaped = res.killed.find((k) => k.pid === 50030);
    assert.ok(reaped, "PID-reused run must be reaped, not spared");
    // The unrelated recycled process (process.pid = the test process) is NOT killed.
    assert.ok(!res.killed.some((k) => k.pid === process.pid),
      "the unrelated recycled process must not be killed");
  }));
});

// ── Scenario 5: legacy bare [MISSION_DRIVER] tag → parent fallback ────────

describe("reaper — scenario 5: legacy bare tag, parent alive → spare", () => {
  it("no runId in tag → _parentIsAliveDriver → live driver ancestor → spare", () => {
    const procs = [
      driverProc(process.pid, "mLegacy"),
      ocProc(50040, process.pid, null), // null runId → bare [MISSION_DRIVER]
    ];
    const err = captureStderr(() => {
      const res = reapStartupOrphans(null, 777777, procs, { ownRunId: "runOther" });
      assert.equal(res.killed.length, 0, "legacy-tagged opencode with a live driver must be spared");
    });
    assert.match(err, /sparing PID 50040 — active concurrent run <legacy>/);
  });

  it("legacy bare tag, driver dead → reap", () => {
    const procs = [
      driverProc(DEAD_PID, "mLegacyDead"),
      ocProc(50041, DEAD_PID, null),
    ];
    const res = reapStartupOrphans(null, process.pid, procs, { ownRunId: "runOther" });
    assert.ok(res.killed.some((k) => k.pid === 50041), "legacy opencode with dead driver is reaped");
  });
});

// ── Scenario 6: no registry / registry loss → parent fallback ─────────────

describe("reaper — scenario 6: missing registry entry → parent fallback", () => {
  it("runId tag but no registry entry, parent alive → spare (conservative)", withTempRegistry((dir) => {
    // Registry is EMPTY for runNoReg — simulates registry loss / homedir issue.
    const procs = [
      driverProc(process.pid, "mNoReg"),
      ocProc(50050, process.pid, "runNoReg"),
    ];
    const res = reapStartupOrphans(null, 777777, procs, { ownRunId: "runOther", registryDir: dir });
    assert.equal(res.killed.length, 0, "no registry + live parent → spare (never mis-kill active run)");
  }));

  it("runId tag but no registry entry, parent dead → reap", withTempRegistry((dir) => {
    const procs = [
      driverProc(DEAD_PID, "mOrphan"),
      ocProc(50051, DEAD_PID, "runOrphan"),
    ];
    const res = reapStartupOrphans(null, process.pid, procs, { ownRunId: "runOther", registryDir: dir });
    assert.ok(res.killed.some((k) => k.pid === 50051), "no registry + dead parent → reap the orphan");
  }));
});

// ── CLI / backward-compat: no opts → does not crash ───────────────────────

describe("reaper — CLI mode (no opts) backward compat", () => {
  it("reapStartupOrphans with undefined opts does not throw and spares a live legacy run", () => {
    const procs = [
      driverProc(process.pid, "mCli"),
      ocProc(50060, process.pid, null),
    ];
    // excludePpid = process.pid protects own; the legacy opencode here is a
    // sibling whose driver is alive → must be spared, not killed.
    const res = reapStartupOrphans(null, 777777, procs);
    assert.equal(res.killed.length, 0);
    assert.ok(res.warnings);
  });
});
