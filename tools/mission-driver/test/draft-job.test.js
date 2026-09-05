import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  mkdtempSync,
  rmSync,
  mkdirSync,
  writeFileSync,
  readFileSync,
  utimesSync,
  existsSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  startDraftJob,
  readDraftJob,
  listDraftJobs,
  __setSpawnerForTest,
} from "../src/draft-job.mjs";

// ── Helpers ───────────────────────────────────────────────────────────────

function makeTmpProject() {
  const root = mkdtempSync(join(tmpdir(), "md-draft-"));
  mkdirSync(join(root, "_tmp"), { recursive: true });
  mkdirSync(join(root, "missions"), { recursive: true });
  return root;
}

/** Write a missions/<name>.json with optional roadmapPath. */
function makeMission(root, name, extra = {}) {
  writeFileSync(
    join(root, "missions", `${name}.json`),
    JSON.stringify({ name, roadmapPath: `docs/${name}.md`, ...extra }, null, 2),
  );
}

// ── startDraftJob (with injected fake spawner) ────────────────────────────

describe("draft-job — startDraftJob (injectable spawner)", () => {
  let lastSpawn = null;
  let prevSpawner = null;

  beforeEach(() => {
    lastSpawn = null;
    prevSpawner = __setSpawnerForTest((cmd, args, opts) => {
      lastSpawn = { cmd, args, opts };
      return { unref() {}, pid: 4242 };
    });
  });
  afterEach(() => {
    __setSpawnerForTest(prevSpawner);
  });

  it("creates jobDir + writes a running draft-state.json, returns {jobId, pid}", () => {
    const root = makeTmpProject();
    try {
      const { jobId, pid, jobDir } = startDraftJob({
        projectRoot: root,
        desc: "a test mission",
      });
      assert.ok(jobId.startsWith("draft-"), `jobId prefix: ${jobId}`);
      assert.ok(jobId.endsWith("-mission-draft"), `jobId suffix: ${jobId}`);
      assert.equal(pid, 4242);
      assert.ok(jobDir.endsWith(jobId), "jobDir basename === jobId");

      // jobDir created
      const stateFile = join(jobDir, "draft-state.json");
      const state = JSON.parse(readFileSync(stateFile, "utf8"));
      assert.equal(state.status, "running");
      assert.equal(state.desc, "a test mission");
      // mdo-4 P2: default phase is now "brief" (two-stage); skipBrief collapses
      // to the legacy "draft" single stage.
      assert.equal(state.phase, "brief");
      assert.equal(state.flowHint, null, "no flowHint by default");
      assert.equal(state.targetFile, null, "no targetFile by default");
      assert.ok(state.startedAt, "startedAt present");

      // spawn safety: process.execPath + args array + shell:false (FSD §8 R2)
      assert.ok(lastSpawn, "spawn was invoked");
      assert.equal(lastSpawn.cmd, process.execPath);
      assert.equal(lastSpawn.opts.shell, false, "shell must be false");
      assert.ok(Array.isArray(lastSpawn.args), "args is an array");
      assert.ok(lastSpawn.args.some((a) => typeof a === "string" && a.endsWith("main.js")), "main.js in args");
      assert.ok(lastSpawn.args.includes("draft"), "draft subcommand in args");
      assert.ok(lastSpawn.args.includes("a test mission"), "desc in args as single argv");
      assert.ok(lastSpawn.args.includes("--draft-job-dir"), "--draft-job-dir flag in args");
      assert.ok(lastSpawn.args.includes(jobDir), "jobDir passed to child");
      // mdo-4 P2: no optional flags appended when their values are absent
      assert.ok(!lastSpawn.args.includes("--flow-hint"), "no --flow-hint when flowHint absent");
      assert.ok(!lastSpawn.args.includes("--skip-brief"), "no --skip-brief when skipBrief absent");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("mdo-4 P2: appends --flow-hint / --target-file to spawn argv when provided", () => {
    const root = makeTmpProject();
    try {
      const { jobDir } = startDraftJob({
        projectRoot: root,
        desc: "with flow",
        flowHint: "integration-test",
        targetFile: "docs/backlog/x.md",
      });
      assert.ok(lastSpawn, "spawn was invoked");
      assert.ok(lastSpawn.args.includes("--flow-hint"), "--flow-hint flag in args");
      assert.ok(lastSpawn.args.includes("integration-test"), "flowHint value in args");
      assert.ok(lastSpawn.args.includes("--target-file"), "--target-file flag in args");
      assert.ok(lastSpawn.args.includes("docs/backlog/x.md"), "targetFile value in args");
      // state carries the selections too
      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(state.flowHint, "integration-test");
      assert.equal(state.targetFile, "docs/backlog/x.md");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("mdo-4 P2: skipBrief collapses phase to draft + appends --skip-brief", () => {
    const root = makeTmpProject();
    try {
      const { jobDir } = startDraftJob({
        projectRoot: root,
        desc: "skip brief",
        skipBrief: true,
      });
      const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
      assert.equal(state.phase, "draft", "skipBrief → single draft stage");
      assert.ok(lastSpawn.args.includes("--skip-brief"), "--skip-brief flag in args");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("honours an explicit draftJobDir (test/override path)", () => {
    const root = makeTmpProject();
    try {
      const fixed = join(root, "_tmp", "draft-fixed-mission-draft");
      const { jobId, jobDir } = startDraftJob({
        projectRoot: root,
        desc: "x",
        draftJobDir: fixed,
      });
      assert.equal(jobDir, fixed);
      assert.ok(existsSync(fixed), "fixed jobDir created");
      // jobId is generated regardless; jobDir is what the child writes into
      assert.ok(jobId.startsWith("draft-"));
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── readDraftJob ──────────────────────────────────────────────────────────

describe("draft-job — readDraftJob", () => {
  it("reads state + draft.log tail (mission-draft.log is the real artifact)", () => {
    const root = makeTmpProject();
    try {
      const { jobId, jobDir } = startDraftJob({ projectRoot: root, desc: "d" });
      // Simulate cmdDraftMission completing the job: overwrite state + write log.
      writeFileSync(
        join(jobDir, "draft-state.json"),
        JSON.stringify({
          jobId,
          status: "completed",
          missionName: "my-mission",
          roadmapPath: "docs/my.md",
          missionFile: "missions/my-mission.json",
        }),
      );
      const lines = Array.from({ length: 250 }, (_, i) => `log line ${i}`);
      writeFileSync(join(jobDir, "mission-draft.log"), lines.join("\n") + "\n");

      const { state, logTail, jobId: readJobId } = readDraftJob(root, jobId);
      assert.ok(state, "state parsed");
      assert.equal(state.status, "completed");
      assert.equal(state.missionName, "my-mission");
      assert.ok(logTail, "log tail present");
      const tailLines = logTail.split("\n");
      // tail capped at 200 lines
      assert.ok(tailLines.length <= 201, `tail length: ${tailLines.length}`);
      assert.ok(logTail.includes("log line 249"), "tail includes last line");
      assert.equal(readJobId, jobId);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("returns null state when jobId not found", () => {
    const root = makeTmpProject();
    try {
      const { state, logTail } = readDraftJob(root, "draft-no-such-mission-draft");
      assert.equal(state, null);
      assert.equal(logTail, null);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("rejects path-traversal jobIds (basename-clean)", () => {
    const root = makeTmpProject();
    try {
      const { state } = readDraftJob(root, "../../../etc/passwd");
      assert.equal(state, null);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── listDraftJobs ─────────────────────────────────────────────────────────

describe("draft-job — listDraftJobs", () => {
  it("lists _tmp/draft-* dirs newest-first, default limit 9", () => {
    const root = makeTmpProject();
    try {
      // create 3 draft dirs with distinct mtimes
      const dirs = ["draft-a-mission-draft", "draft-b-mission-draft", "draft-c-mission-draft"];
      for (const d of dirs) {
        const dir = join(root, "_tmp", d);
        mkdirSync(dir, { recursive: true });
        writeFileSync(
          join(dir, "draft-state.json"),
          JSON.stringify({ status: "completed", startedAt: "2026-07-04T00:00:00Z", desc: d }),
        );
      }
      // bump mtime of b → newest, c → middle, a → oldest
      const base = Date.now() / 1000;
      utimesSync(join(root, "_tmp", dirs[0]), base - 200, base - 200);
      utimesSync(join(root, "_tmp", dirs[1]), base, base); // newest
      utimesSync(join(root, "_tmp", dirs[2]), base - 100, base - 100);

      const { jobs } = listDraftJobs(root);
      assert.equal(jobs.length, 3);
      // newest first
      assert.equal(jobs[0].jobId, dirs[1]);
      assert.equal(jobs[1].jobId, dirs[2]);
      assert.equal(jobs[2].jobId, dirs[0]);
      assert.equal(jobs[0].status, "completed");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("respects limit and ignores non-draft dirs", () => {
    const root = makeTmpProject();
    try {
      for (let i = 0; i < 3; i++) {
        mkdirSync(join(root, "_tmp", `draft-${i}-mission-draft`), { recursive: true });
      }
      // a run dir (not a draft) and a stray file must be ignored
      mkdirSync(join(root, "_tmp", "2026-07-04-000000-mission-driver"), { recursive: true });
      writeFileSync(join(root, "_tmp", "stray.txt"), "x");
      const { jobs } = listDraftJobs(root, 2);
      assert.equal(jobs.length, 2, "limit applied");
      assert.ok(jobs.every((j) => j.jobId.startsWith("draft-")), "only draft-* dirs");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Product parsing fallback (cmdDraftMission helper semantics) ────────────
//
// parseDraftArtifact is a private helper in main.js; we exercise its spec via
// readDraftJob by simulating what cmdDraftMission writes. The spec (FSD §3.1.3):
//   - <MISSION_FILE> tag wins when present + readable
//   - else newest missions/*.json with roadmapPath
//   - else nulls (completed but unresolved)

describe("draft-job — product resolution contract (via state file)", () => {
  it("completed state carries missionName/roadmapPath/missionFile when resolved", () => {
    const root = makeTmpProject();
    try {
      makeMission(root, "resolved-mission");
      const { jobId, jobDir } = startDraftJob({ projectRoot: root, desc: "d" });
      // Simulate a successful draft that resolved the artifact
      writeFileSync(
        join(jobDir, "draft-state.json"),
        JSON.stringify({
          status: "completed",
          missionName: "resolved-mission",
          roadmapPath: "docs/resolved-mission.md",
          missionFile: join(root, "missions", "resolved-mission.json"),
        }),
      );
      const { state } = readDraftJob(root, jobId);
      assert.equal(state.missionName, "resolved-mission");
      assert.equal(state.roadmapPath, "docs/resolved-mission.md");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("completed state may carry null missionName (unresolved boundary, FSD §3.1.3)", () => {
    const root = makeTmpProject();
    try {
      const { jobId, jobDir } = startDraftJob({ projectRoot: root, desc: "d" });
      writeFileSync(
        join(jobDir, "draft-state.json"),
        JSON.stringify({ status: "completed", missionName: null, roadmapPath: null, missionFile: null }),
      );
      const { state } = readDraftJob(root, jobId);
      assert.equal(state.status, "completed");
      assert.equal(state.missionName, null);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});
