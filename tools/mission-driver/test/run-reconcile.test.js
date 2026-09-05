import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  isAliveAndOurs,
  markAborted,
  reconcileStaleRuns,
} from "../src/run-reconcile.mjs";
import { isAlive } from "../src/platform.mjs";
import {
  mkdtempSync,
  mkdirSync,
  rmSync,
  writeFileSync,
  readFileSync,
} from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

// ── Test helpers ──────────────────────────────────────────────────────────

// A PID that is very unlikely to be a live process; used for "dead" scenarios.
const DEAD_PID = 9_999_899;

function withProjectRoot(fn) {
  return async () => {
    const root = mkdtempSync(join(tmpdir(), "reconcile-"));
    try {
      await fn(root);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  };
}

// Write `<root>/_tmp/<runId>/run-state.json` (creating dirs) and return the runDir.
function seedRun(root, runId, state, extraFiles = {}) {
  const runDir = join(root, "_tmp", runId);
  mkdirSync(runDir, { recursive: true });
  writeFileSync(join(runDir, "run-state.json"), JSON.stringify({ runId, ...state }, null, 2));
  for (const [name, content] of Object.entries(extraFiles)) {
    writeFileSync(join(runDir, name), JSON.stringify(content, null, 2));
  }
  return runDir;
}

function readRun(runDir, file = "run-state.json") {
  return JSON.parse(readFileSync(join(runDir, file), "utf8"));
}

// Process snapshot entry whose cmdline satisfies the main.js + identity check.
function ownProc(pid, runId, missionName) {
  return {
    pid,
    ppid: 1,
    pgid: 0,
    rss_kb: 100,
    name: "node",
    cmd: `node main.js --mission ${missionName} ${runId} [MISSION_DRIVER]`,
  };
}

// ── isAliveAndOurs — direct identity logic ────────────────────────────────

describe("isAliveAndOurs — identity + liveness logic", () => {
  it("returns false for a non-numeric / missing pid", () => {
    assert.equal(isAliveAndOurs(null, "r1", "m1", []), false);
    assert.equal(isAliveAndOurs(undefined, "r1", "m1", []), false);
    assert.equal(isAliveAndOurs("123", "r1", "m1", []), false);
  });

  it("returns false when the pid is dead (isAlive false)", () => {
    assert.equal(isAliveAndOurs(DEAD_PID, "r1", "m1", [ownProc(DEAD_PID, "r1", "m1")]), false);
  });

  it("returns true when alive AND cmdline has main.js + runId", () => {
    // process.pid is genuinely alive (this test process).
    const snap = [ownProc(process.pid, "rid-alive", "mAlive")];
    assert.equal(isAliveAndOurs(process.pid, "rid-alive", "mAlive", snap), true);
  });

  it("returns true when alive AND cmdline has main.js + missionName (runId absent)", () => {
    const snap = [ownProc(process.pid, "rid-x", "mAliveName")];
    assert.equal(isAliveAndOurs(process.pid, null, "mAliveName", snap), true);
  });

  it("PID reuse: alive but cmdline lacks main.js/identity → returns false", () => {
    const snap = [{ pid: process.pid, ppid: 1, pgid: 0, rss_kb: 1, name: "x", cmd: "some-unrelated-program --foo bar" }];
    assert.equal(isAliveAndOurs(process.pid, "rid-alive", "mAlive", snap), false);
  });

  it("cmdline missing fallback: alive pid not in snapshot → returns true (conservative, R2)", () => {
    assert.equal(isAliveAndOurs(process.pid, "rid-alive", "mAlive", []), true);
    // entry present but empty cmd also falls back to alive-only
    assert.equal(isAliveAndOurs(process.pid, "rid-alive", "mAlive", [{ pid: process.pid, cmd: "" }]), true);
  });
});

// ── markAborted ───────────────────────────────────────────────────────────

describe("markAborted — flips running→aborted, syncs subflows, idempotent", () => {
  it("flips top-level running→aborted, stamps endedAt/updatedAt/abortReason, and tail running steps", () => {
    const runDir = mkdtempSync(join(tmpdir(), "abort-"));
    try {
      writeFileSync(join(runDir, "run-state.json"), JSON.stringify({
        status: "running",
        runId: "r1",
        updatedAt: "2026-01-01T00:00:00.000Z",
        steps: [
          { name: "A", status: "completed" },
          { name: "B", status: "running" },
        ],
      }));
      markAborted(runDir, "test-abort");

      const s = readRun(runDir);
      assert.equal(s.status, "aborted");
      assert.equal(s.abortReason, "test-abort");
      assert.ok(s.endedAt, "endedAt must be stamped");
      assert.ok(s.updatedAt, "updatedAt must be stamped");
      assert.equal(s.steps[0].status, "completed", "already-completed step untouched");
      assert.equal(s.steps[1].status, "aborted", "running step synced to aborted");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("syncs co-located subflow run-state-*.json files", () => {
    const runDir = mkdtempSync(join(tmpdir(), "abort-sub-"));
    try {
      writeFileSync(join(runDir, "run-state.json"), JSON.stringify({ status: "running", runId: "r1", steps: [] }));
      writeFileSync(join(runDir, "run-state-child1.json"), JSON.stringify({ status: "running", runId: "r1", steps: [{ name: "X", status: "running" }] }));
      writeFileSync(join(runDir, "run-state-child2.json"), JSON.stringify({ status: "completed", runId: "r1", steps: [] }));
      markAborted(runDir, "sub-sync");

      assert.equal(readRun(runDir).status, "aborted");
      assert.equal(readRun(runDir, "run-state-child1.json").status, "aborted", "running subflow synced");
      assert.equal(readRun(runDir, "run-state-child1.json").steps[0].status, "aborted");
      assert.equal(readRun(runDir, "run-state-child2.json").status, "completed", "non-running subflow untouched");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("idempotent: already-aborted file is not rewritten", () => {
    const runDir = mkdtempSync(join(tmpdir(), "abort-idem-"));
    try {
      writeFileSync(join(runDir, "run-state.json"), JSON.stringify({
        status: "aborted", runId: "r1", endedAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z", abortReason: "first", steps: [],
      }));
      markAborted(runDir, "second-call");
      const s = readRun(runDir);
      assert.equal(s.status, "aborted");
      assert.equal(s.abortReason, "first", "must not overwrite on second call (idempotent)");
      assert.equal(s.updatedAt, "2026-01-01T00:00:00.000Z", "updatedAt unchanged");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });
});

// ── reconcileStaleRuns — the 8 scenarios (FSD §4.1/§4.2/§4.3) ─────────────

describe("reconcileStaleRuns — stale-run reconciliation", () => {
  it("1. alive run (pid alive + identity match) is SKIPPED, file unchanged", withProjectRoot(async (root) => {
    const runDir = seedRun(root, "rid-alive", {
      missionName: "mAlive", pid: process.pid, status: "running",
      updatedAt: new Date().toISOString(), steps: [{ name: "S", status: "running" }],
    });
    const before = readRun(runDir);

    const res = reconcileStaleRuns(root, [ownProc(process.pid, "rid-alive", "mAlive")]);

    assert.equal(res.reconciled.length, 0, "active run must NOT be reconciled");
    assert.equal(res.skipped.length, 1);
    assert.equal(res.skipped[0].reason, "process alive");
    assert.deepEqual(readRun(runDir), before, "active run file must be byte-for-byte untouched (safety boundary)");
  }));

  it("2. dead pid → run is reconciled to aborted", withProjectRoot(async (root) => {
    const runDir = seedRun(root, "rid-dead", {
      missionName: "mDead", pid: DEAD_PID, status: "running",
      updatedAt: new Date().toISOString(), steps: [{ name: "S", status: "running" }],
    });
    // pass empty snapshot: pid not alive anyway (isAlive false) → dead.
    const res = reconcileStaleRuns(root, []);

    assert.equal(res.reconciled.length, 1);
    const s = readRun(runDir);
    assert.equal(s.status, "aborted");
    assert.ok(s.abortReason.includes(String(DEAD_PID)), "abortReason references the dead pid");
    assert.equal(s.steps[0].status, "aborted", "running step synced");
  }));

  it("3. PID reuse (alive pid, cmdline not ours) → judged dead → aborted", withProjectRoot(async (root) => {
    const runDir = seedRun(root, "rid-reuse", {
      missionName: "mReuse", pid: process.pid, status: "running",
      updatedAt: new Date().toISOString(), steps: [],
    });
    // process.pid is alive, but the recycled cmdline has nothing to do with us.
    const snap = [{ pid: process.pid, ppid: 1, pgid: 0, rss_kb: 1, name: "x", cmd: "unrelated --recycled" }];
    const res = reconcileStaleRuns(root, snap);

    assert.equal(res.reconciled.length, 1, "PID reused by unrelated process → treated as dead");
    assert.equal(readRun(runDir).status, "aborted");
  }));

  it("4. cmdline unobtainable fallback → alive-only → SKIPPED", withProjectRoot(async (root) => {
    const runDir = seedRun(root, "rid-nocmd", {
      missionName: "mNoCmd", pid: process.pid, status: "running",
      updatedAt: new Date().toISOString(), steps: [],
    });
    // empty snapshot → live pid not found → conservative alive-only (R2).
    const res = reconcileStaleRuns(root, []);
    assert.equal(res.reconciled.length, 0);
    assert.equal(res.skipped.length, 1);
    assert.equal(readRun(runDir).status, "running", "conservative fallback must not abort an alive pid");
  }));

  it("5. no pid, updatedAt < 90min → SKIPPED (cannot prove stale)", withProjectRoot(async (root) => {
    const runDir = seedRun(root, "rid-nopid-fresh", {
      missionName: "mFresh", status: "running",
      updatedAt: new Date().toISOString(), steps: [],
    });
    const res = reconcileStaleRuns(root, []);
    assert.equal(res.reconciled.length, 0);
    assert.equal(res.skipped.length, 1);
    assert.equal(readRun(runDir).status, "running", "recently-updated no-pid run must be left alone");
  }));

  it("6. no pid, updatedAt > 90min → stale fallback → aborted", withProjectRoot(async (root) => {
    const old = new Date(Date.now() - 120 * 60 * 1000).toISOString(); // 2h ago
    const runDir = seedRun(root, "rid-nopid-stale", {
      missionName: "mStale", status: "running", updatedAt: old, steps: [{ name: "S", status: "running" }],
    });
    const res = reconcileStaleRuns(root, []);
    assert.equal(res.reconciled.length, 1);
    const s = readRun(runDir);
    assert.equal(s.status, "aborted");
    assert.ok(/90min/.test(s.abortReason), "abortReason mentions the 90min fallback");
    assert.equal(s.steps[0].status, "aborted");
  }));

  it("7. idempotent: second reconcile is a no-op (already aborted)", withProjectRoot(async (root) => {
    const runDir = seedRun(root, "rid-idem", {
      missionName: "mIdem", pid: DEAD_PID, status: "running",
      updatedAt: new Date().toISOString(), steps: [],
    });
    reconcileStaleRuns(root, []);
    const after1 = readRun(runDir);
    assert.equal(after1.status, "aborted");

    const res2 = reconcileStaleRuns(root, []);
    const after2 = readRun(runDir);
    assert.equal(res2.reconciled.length, 0, "second pass reconciles nothing");
    assert.equal(res2.skipped.length, 0, "non-running run is neither reconciled nor 'skipped-alive'");
    assert.deepEqual(after2, after1, "aborted file must not be rewritten on second pass");
  }));

  it("8. subflow run-state-*.json are synced when the main run is aborted", withProjectRoot(async (root) => {
    const runDir = seedRun(root, "rid-sub", {
      missionName: "mSub", pid: DEAD_PID, status: "running",
      updatedAt: new Date().toISOString(), steps: [],
    }, {
      "run-state-child.json": { status: "running", runId: "rid-sub", steps: [{ name: "X", status: "running" }] },
    });
    const res = reconcileStaleRuns(root, []);
    assert.equal(res.reconciled.length, 1);
    assert.equal(readRun(runDir).status, "aborted");
    const child = readRun(runDir, "run-state-child.json");
    assert.equal(child.status, "aborted", "subflow synced alongside main run");
    assert.equal(child.steps[0].status, "aborted");
  }));

  it("9. coexisting ACTIVE run is never harmed while a dead one is reconciled", withProjectRoot(async (root) => {
    const aliveDir = seedRun(root, "rid-co-alive", {
      missionName: "mCoAlive", pid: process.pid, status: "running",
      updatedAt: new Date().toISOString(), steps: [{ name: "S", status: "running" }],
    });
    const deadDir = seedRun(root, "rid-co-dead", {
      missionName: "mCoDead", pid: DEAD_PID, status: "running",
      updatedAt: new Date().toISOString(), steps: [{ name: "S", status: "running" }],
    });
    const aliveBefore = readRun(aliveDir);

    const res = reconcileStaleRuns(root, [ownProc(process.pid, "rid-co-alive", "mCoAlive")]);

    assert.equal(res.reconciled.length, 1);
    assert.equal(res.reconciled[0].runId, "rid-co-dead");
    assert.equal(readRun(deadDir).status, "aborted", "dead run reconciled");
    assert.deepEqual(readRun(aliveDir), aliveBefore, "coexisting active run untouched — safety boundary holds");
    assert.equal(isAlive(process.pid), true);
  }));
});
