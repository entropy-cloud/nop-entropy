import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  registerActiveRun,
  touchActiveRun,
  unregisterActiveRun,
  loadActiveRunIndex,
} from "../src/active-run-registry.mjs";
import {
  mkdtempSync,
  mkdirSync,
  rmSync,
  writeFileSync,
  readFileSync,
  readdirSync,
  existsSync,
} from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

// ── helpers ───────────────────────────────────────────────────────────────

function withTempDir(fn) {
  return async () => {
    const dir = mkdtempSync(join(tmpdir(), "registry-"));
    try {
      await fn(dir);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  };
}

// ── registerActiveRun ─────────────────────────────────────────────────────

describe("registerActiveRun — writes <runId>-<driverPid>.json", () => {
  it("creates the registry file with full entry shape", withTempDir((dir) => {
    registerActiveRun({
      runId: "runA", driverPid: 12345, missionName: "missionA",
      projectRoot: "/proj", dir,
    });
    const files = readdirSync(dir);
    assert.equal(files.length, 1);
    assert.equal(files[0], "runA-12345.json");
    const rec = JSON.parse(readFileSync(join(dir, "runA-12345.json"), "utf8"));
    assert.equal(rec.runId, "runA");
    assert.equal(rec.driverPid, 12345);
    assert.equal(rec.missionName, "missionA");
    assert.equal(rec.projectRoot, "/proj");
    assert.ok(rec.startedAt, "startedAt stamped");
    assert.ok(rec.heartbeatTs, "heartbeatTs stamped");
  }));

  it("creates the registry dir if missing (recursive)", withTempDir((dir) => {
    const nested = join(dir, "deep", "active");
    registerActiveRun({ runId: "r", driverPid: 1, missionName: "m", dir: nested });
    assert.ok(existsSync(join(nested, "r-1.json")));
  }));

  it("is a no-op when runId or driverPid is missing", withTempDir((dir) => {
    registerActiveRun({ runId: "", driverPid: 1, missionName: "m", dir });
    registerActiveRun({ runId: "r", driverPid: 0, missionName: "m", dir });
    assert.deepEqual(readdirSync(dir), []);
  }));

  it("driverPid suffix prevents same-runId file collision", withTempDir((dir) => {
    registerActiveRun({ runId: "2026-07-29-0900-mission-driver", driverPid: 111, missionName: "m", dir });
    registerActiveRun({ runId: "2026-07-29-0900-mission-driver", driverPid: 222, missionName: "m", dir });
    const files = readdirSync(dir).sort();
    assert.deepEqual(files, [
      "2026-07-29-0900-mission-driver-111.json",
      "2026-07-29-0900-mission-driver-222.json",
    ]);
  }));

  it("swallows unwritable dir failures (best-effort)", () => {
    // A path whose parent is a file cannot be mkdir'd → must not throw.
    const fileAsParent = mkdtempSync(join(tmpdir(), "reg-block-")) + "/blocker";
    writeFileSync(fileAsParent, "x");
    try {
      assert.doesNotThrow(() =>
        registerActiveRun({ runId: "r", driverPid: 1, missionName: "m", dir: join(fileAsParent, "active") })
      );
    } finally {
      rmSync(fileAsParent, { recursive: true, force: true });
    }
  });
});

// ── touchActiveRun ────────────────────────────────────────────────────────

describe("touchActiveRun — refreshes heartbeatTs", () => {
  it("updates heartbeatTs on an existing entry", withTempDir(async (dir) => {
    registerActiveRun({ runId: "r", driverPid: 7, missionName: "m", dir });
    const before = JSON.parse(readFileSync(join(dir, "r-7.json"), "utf8")).heartbeatTs;
    // wait a tick so the ISO timestamp can differ
    await new Promise((r) => setTimeout(r, 1100));
    touchActiveRun("r", 7, dir);
    const after = JSON.parse(readFileSync(join(dir, "r-7.json"), "utf8"));
    assert.notEqual(after.heartbeatTs, before, "heartbeatTs must advance");
    assert.equal(after.startedAt, before, "startedAt unchanged by touch");
    assert.equal(after.runId, "r");
  }));

  it("is a silent no-op when the entry does not exist", withTempDir((dir) => {
    assert.doesNotThrow(() => touchActiveRun("missing", 99, dir));
    assert.deepEqual(readdirSync(dir), []);
  }));
});

// ── unregisterActiveRun ───────────────────────────────────────────────────

describe("unregisterActiveRun — best-effort, idempotent", () => {
  it("deletes the entry", withTempDir((dir) => {
    registerActiveRun({ runId: "r", driverPid: 5, missionName: "m", dir });
    unregisterActiveRun("r", 5, dir);
    assert.ok(!existsSync(join(dir, "r-5.json")));
  }));

  it("ENOENT is silently ignored (never-registered run)", withTempDir((dir) => {
    assert.doesNotThrow(() => unregisterActiveRun("never", 5, dir));
  }));

  it("idempotent: second call is a silent no-op", withTempDir((dir) => {
    registerActiveRun({ runId: "r", driverPid: 5, missionName: "m", dir });
    unregisterActiveRun("r", 5, dir);
    assert.doesNotThrow(() => unregisterActiveRun("r", 5, dir));
  }));
});

// ── loadActiveRunIndex ────────────────────────────────────────────────────

describe("loadActiveRunIndex — builds runId -> entries map", () => {
  it("groups entries by runId (handles same-runId collisions)", withTempDir((dir) => {
    registerActiveRun({ runId: "shared", driverPid: 111, missionName: "mA", dir });
    registerActiveRun({ runId: "shared", driverPid: 222, missionName: "mB", dir });
    registerActiveRun({ runId: "solo", driverPid: 333, missionName: "mC", dir });
    const idx = loadActiveRunIndex(dir);
    assert.equal(idx.size, 2);
    assert.equal(idx.get("shared").length, 2, "two drivers share runId 'shared'");
    assert.equal(idx.get("solo").length, 1);
    const pids = idx.get("shared").map((e) => e.driverPid).sort();
    assert.deepEqual(pids, [111, 222]);
  }));

  it("skips corrupt / malformed json files", withTempDir((dir) => {
    registerActiveRun({ runId: "good", driverPid: 1, missionName: "m", dir });
    writeFileSync(join(dir, "broken-2.json"), "{ not valid json");
    writeFileSync(join(dir, "no-identity-3.json"), JSON.stringify({ foo: "bar" }));
    const idx = loadActiveRunIndex(dir);
    assert.equal(idx.size, 1, "only the well-formed entry is indexed");
    assert.ok(idx.has("good"));
  }));

  it("returns empty map when the registry dir does not exist", () => {
    const idx = loadActiveRunIndex(join(tmpdir(), "definitely-missing-" + Date.now()));
    assert.equal(idx.size, 0);
  });

  it("returns a Map instance", withTempDir((dir) => {
    assert.ok(loadActiveRunIndex(dir) instanceof Map);
  }));
});
