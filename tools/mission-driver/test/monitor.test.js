import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  mkdtempSync,
  rmSync,
  mkdirSync,
  writeFileSync,
  readFileSync,
  appendFileSync,
  utimesSync,
  renameSync,
  existsSync,
  readdirSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import http from "node:http";
import { startMonitor, parseRoadmapMarkdown, __setSpawnerForTest, handleStartDraft } from "../src/monitor.js";

// ── Test helpers ──────────────────────────────────────────────────────────

function fetchJson(urlstr) {
  return new Promise((resolve, reject) => {
    const u = new URL(urlstr);
    const req = http.request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        method: "GET",
        agent: false,
      },
      (res) => {
        let data = "";
        res.on("data", (chunk) => (data += chunk));
        res.on("end", () => {
          try {
            resolve({ status: res.statusCode, headers: res.headers, body: JSON.parse(data) });
          } catch {
            resolve({ status: res.statusCode, headers: res.headers, body: data });
          }
        });
      }
    );
    req.on("error", reject);
    req.end();
  });
}

// POST JSON helper (mdo-4 P2: shared across describe blocks for draft/PUT tests).
function postJson(urlstr, body) {
  return new Promise((resolve, reject) => {
    const u = new URL(urlstr);
    const payload = JSON.stringify(body);
    const req = http.request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        method: "POST",
        headers: { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(payload) },
        agent: false,
      },
      (res) => {
        let data = "";
        res.on("data", (chunk) => (data += chunk));
        res.on("end", () => {
          try {
            resolve({ status: res.statusCode, body: JSON.parse(data) });
          } catch {
            resolve({ status: res.statusCode, body: data });
          }
        });
      }
    );
    req.on("error", reject);
    req.write(payload);
    req.end();
  });
}

function fetchSSE(urlstr, collectMs) {
  return new Promise((resolve) => {
    const u = new URL(urlstr);
    let data = "";
    let resolved = false;
    const finish = () => {
      if (!resolved) {
        resolved = true;
        resolve(data);
      }
    };
    const req = http.request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        method: "GET",
        agent: false,
      },
      (res) => {
        res.on("data", (chunk) => (data += chunk.toString()));
      }
    );
    req.on("error", () => finish());
    req.end();
    setTimeout(() => {
      try {
        req.destroy();
      } catch {}
      finish();
    }, collectMs);
  });
}

function fetchHeaders(urlstr) {
  return new Promise((resolve) => {
    const u = new URL(urlstr);
    const req = http.request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        method: "GET",
        agent: false,
      },
      (res) => {
        const result = { status: res.statusCode, headers: res.headers };
        try {
          req.destroy();
        } catch {}
        resolve(result);
      }
    );
    req.on("error", () => resolve(null));
    req.end();
  });
}

function makeTmpProject() {
  const root = mkdtempSync(join(tmpdir(), "md-mon-"));
  mkdirSync(join(root, "_tmp"), { recursive: true });
  mkdirSync(join(root, "missions"), { recursive: true });
  mkdirSync(join(root, "web"), { recursive: true });
  return root;
}

function makeRun(projectRoot, runId, opts = {}) {
  const runDir = join(projectRoot, "_tmp", runId);
  mkdirSync(runDir, { recursive: true });
  if (opts.state) {
    writeFileSync(join(runDir, "run-state.json"), JSON.stringify(opts.state, null, 2));
  }
  if (opts.events) {
    writeFileSync(
      join(runDir, "events.jsonl"),
      opts.events.map((e) => JSON.stringify(e)).join("\n") + "\n"
    );
  }
  if (opts.logs) {
    for (const [fileName, content] of Object.entries(opts.logs)) {
      writeFileSync(join(runDir, fileName), content);
    }
  }
  if (opts.sysmon) {
    writeFileSync(
      join(runDir, "sys-snapshot.log"),
      opts.sysmon.map((s) => JSON.stringify(s)).join("\n") + "\n"
    );
  }
  return runDir;
}

function makeMission(projectRoot, name, mission) {
  writeFileSync(join(projectRoot, "missions", `${name}.json`), JSON.stringify(mission, null, 2));
}

function baseUrl(monitor) {
  return `http://localhost:${monitor.port}`;
}

// ── Phase 1: Skeleton + lifecycle ─────────────────────────────────────────

describe("Monitor — skeleton + lifecycle (Phase 1)", () => {
  it("starts on OS-assigned port (port=0) and serves placeholder index", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({
        projectRoot: root,
        runDir: root,
        port: 0,
        webDir: join(root, "web"),
      });
      try {
        assert.ok(monitor.port > 0);
        assert.equal(typeof monitor.close, "function");

        const res = await fetchJson(`${baseUrl(monitor)}/`);
        assert.equal(res.status, 200);
        assert.ok(res.headers["content-type"].includes("text/html"));
        assert.ok(res.body.includes("Monitor server is running"));
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("serves web/index.html when it exists", async () => {
    const root = makeTmpProject();
    try {
      writeFileSync(join(root, "web", "index.html"), "<!DOCTYPE html><html><body>Real UI</body></html>");
      const monitor = await startMonitor({
        projectRoot: root,
        runDir: root,
        port: 0,
        webDir: join(root, "web"),
      });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/`);
        assert.equal(res.status, 200);
        assert.ok(res.body.includes("Real UI"));
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("SPA fallback: deep-link /runs/:runId serves index.html on page refresh", async () => {
    const root = makeTmpProject();
    try {
      writeFileSync(join(root, "web", "index.html"), "<!DOCTYPE html><html><body>Real UI</body></html>");
      const monitor = await startMonitor({
        projectRoot: root,
        runDir: root,
        port: 0,
        webDir: join(root, "web"),
      });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/runs/2026-06-30-175402-mission-driver`);
        assert.equal(res.status, 200);
        assert.ok(res.body.includes("Real UI"), "SPA route should serve index.html");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("SPA fallback: missing asset with extension still 404s", async () => {
    const root = makeTmpProject();
    try {
      writeFileSync(join(root, "web", "index.html"), "<!DOCTYPE html><html><body>Real UI</body></html>");
      const monitor = await startMonitor({
        projectRoot: root,
        runDir: root,
        port: 0,
        webDir: join(root, "web"),
      });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/assets/missing.js`);
        assert.equal(res.status, 404);
        assert.equal(res.body.error, "not found");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("returns 404 for unknown API routes", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({
        projectRoot: root,
        runDir: root,
        port: 0,
        webDir: join(root, "web"),
      });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/nonexistent`);
        assert.equal(res.status, 404);
        assert.equal(res.body.error, "not found");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("resolves /api/runs route and returns empty runs array for empty project", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({
        projectRoot: root,
        runDir: root,
        port: 0,
        webDir: join(root, "web"),
      });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs`);
        assert.equal(res.status, 200);
        assert.deepEqual(res.body.runs, []);
        assert.equal(res.body.total, 0);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("resolves /api/runs/:runId route — 404 for non-existent run", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({
        projectRoot: root,
        runDir: root,
        port: 0,
        webDir: join(root, "web"),
      });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/nonexistent-run`);
        assert.equal(res.status, 404);
        assert.equal(res.body.error, "run not found");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("retries +1 on port conflict (EADDRINUSE)", async () => {
    const root = makeTmpProject();
    try {
      const m1 = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const m2 = await startMonitor({
          projectRoot: root,
          port: m1.port,
          webDir: join(root, "web"),
        });
        try {
          if (m2.port !== m1.port) {
            // EADDRINUSE was raised → retry logic validated
            assert.ok(m2.port > m1.port, `expected m2.port > ${m1.port}, got ${m2.port}`);
            assert.ok(m2.port <= m1.port + 5, `expected m2.port <= ${m1.port + 5}, got ${m2.port}`);
          } else {
            // Windows may allow same-process servers to share a port (no EADDRINUSE).
            // Verify the second monitor is at least functional.
            const res = await fetchJson(`${baseUrl(m2)}/api/runs`);
            assert.equal(res.status, 200);
          }
        } finally {
          await m2.close();
        }
      } finally {
        await m1.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("close() cleans up socket — connections fail after close", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({
        projectRoot: root,
        runDir: root,
        port: 0,
        webDir: join(root, "web"),
      });
      const port = monitor.port;
      await monitor.close();

      await assert.rejects(
        () => fetchJson(`http://localhost:${port}/api/runs`),
        (err) => err.code === "ECONNREFUSED" || err.code === "ECONNRESET"
      );
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Phase 2: REST endpoints ───────────────────────────────────────────────

describe("Monitor — REST endpoints (Phase 2)", () => {
  it("GET /api/runs returns runs sorted by name descending", async () => {
    const root = makeTmpProject();
    try {
      makeRun(root, "2026-06-29-100000-mission-driver", {
        state: {
          missionName: "mission-a",
          runId: "2026-06-29-100000-mission-driver",
          flowName: "flow-a",
          status: "completed",
          startedAt: "2026-06-29T10:00:00Z",
          updatedAt: "2026-06-29T11:00:00Z",
          endedAt: "2026-06-29T11:00:00Z",
          currentStep: null,
          steps: [{ name: "STEP1" }, { name: "STEP2" }],
        },
      });
      makeRun(root, "2026-06-28-090000-mission-driver", {
        state: {
          missionName: "mission-b",
          runId: "2026-06-28-090000-mission-driver",
          flowName: "flow-b",
          status: "running",
          startedAt: "2026-06-28T09:00:00Z",
          steps: [{ name: "WORK" }],
        },
      });

      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs`);
        assert.equal(res.status, 200);
        assert.equal(res.body.runs.length, 2);
        assert.equal(res.body.runs[0].runId, "2026-06-29-100000-mission-driver");
        assert.equal(res.body.runs[0].missionName, "mission-a");
        assert.equal(res.body.runs[0].flowName, "flow-a");
        assert.equal(res.body.runs[0].status, "completed");
        assert.equal(res.body.runs[0].stepCount, 2);
        assert.equal(res.body.runs[1].runId, "2026-06-28-090000-mission-driver");
        assert.equal(res.body.runs[1].missionName, "mission-b");
        assert.equal(res.body.runs[1].flowName, "flow-b");
        assert.equal(res.body.runs[1].status, "running");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs respects limit query", async () => {
    const root = makeTmpProject();
    try {
      for (let i = 0; i < 5; i++) {
        makeRun(root, `2026-06-2${9 - i}-10000${i}-mission-driver`, {
          state: { missionName: "m", runId: `r${i}`, status: "completed", steps: [] },
        });
      }
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs?limit=2`);
        assert.equal(res.body.runs.length, 2);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs supports offset paging with total/hasMore (§4)", async () => {
    const root = makeTmpProject();
    try {
      for (let i = 0; i < 5; i++) {
        makeRun(root, `2026-06-2${9 - i}-10000${i}-mission-driver`, {
          state: { missionName: "m", runId: `r${i}`, status: "completed", steps: [] },
        });
      }
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const p1 = await fetchJson(`${baseUrl(monitor)}/api/runs?limit=2&offset=0`);
        assert.equal(p1.body.total, 5);
        assert.equal(p1.body.runs.length, 2);
        assert.equal(p1.body.hasMore, true);

        const p3 = await fetchJson(`${baseUrl(monitor)}/api/runs?limit=2&offset=4`);
        assert.equal(p3.body.runs.length, 1);
        assert.equal(p3.body.hasMore, false);

        // No overlap between page 1 and page 2 (reverse-sorted, stable slice).
        const p2 = await fetchJson(`${baseUrl(monitor)}/api/runs?limit=2&offset=2`);
        const ids1 = new Set(p1.body.runs.map((r) => r.runId));
        assert.ok(p2.body.runs.every((r) => !ids1.has(r.runId)));
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs synthesizes from events.jsonl when run-state.json missing (BR-6 backward compat)", async () => {
    const root = makeTmpProject();
    try {
      makeRun(root, "2026-06-20-old-run-mission-driver", {
        events: [
          { type: "run_started", ts: "2026-06-20T08:00:00Z", missionName: "old-mission", runId: "2026-06-20-old-run-mission-driver", flowName: "events-flow" },
          { type: "run_completed", ts: "2026-06-20T10:00:00Z", missionName: "old-mission", runId: "2026-06-20-old-run-mission-driver", status: "completed" },
        ],
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs`);
        assert.equal(res.body.runs.length, 1);
        assert.equal(res.body.runs[0].missionName, "old-mission");
        assert.equal(res.body.runs[0].flowName, "events-flow");
        assert.equal(res.body.runs[0].status, "completed");
        assert.equal(res.body.runs[0].startedAt, "2026-06-20T08:00:00Z");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs falls back to mission config flowName for legacy run-state.json (R1)", async () => {
    const root = makeTmpProject();
    try {
      // Legacy run-state.json WITHOUT flowName — should fall back to mission config.
      makeRun(root, "2026-06-19-legacy-run-mission-driver", {
        state: {
          missionName: "legacy-mission",
          runId: "2026-06-19-legacy-run-mission-driver",
          status: "completed",
          startedAt: "2026-06-19T08:00:00Z",
          steps: [{ name: "STEP1" }],
        },
      });
      makeMission(root, "legacy-mission", {
        name: "legacy-mission",
        flowName: "legacy-flow",
        roadmapPath: "docs/backlog/legacy.md",
      });
      // A run whose mission config also lacks flowName — should stay null (no error).
      makeRun(root, "2026-06-18-no-flow-mission-driver", {
        state: {
          missionName: "no-flow-mission",
          runId: "2026-06-18-no-flow-mission-driver",
          status: "completed",
          steps: [],
        },
      });
      makeMission(root, "no-flow-mission", { name: "no-flow-mission" });

      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs`);
        assert.equal(res.body.runs.length, 2);
        const legacy = res.body.runs.find((r) => r.runId === "2026-06-19-legacy-run-mission-driver");
        const noFlow = res.body.runs.find((r) => r.runId === "2026-06-18-no-flow-mission-driver");
        assert.equal(legacy.flowName, "legacy-flow");
        assert.equal(noFlow.flowName, null);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs/:runId returns run detail with events and stepLogs", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-100000-mission-driver";
      makeRun(root, runId, {
        state: {
          missionName: "test-mission",
          runId,
          status: "running",
          startedAt: "2026-06-29T10:00:00Z",
          currentStep: "WORK",
          steps: [{ name: "CHECK", status: "completed", marker: "pass" }],
        },
        events: [
          { type: "run_started", ts: "2026-06-29T10:00:00Z", missionName: "test-mission", runId },
          { type: "step_started", ts: "2026-06-29T10:00:01Z", missionName: "test-mission", runId, step: "CHECK" },
          { type: "step_completed", ts: "2026-06-29T10:00:05Z", missionName: "test-mission", runId, step: "CHECK", marker: "pass" },
        ],
        logs: {
          "oc-CHECK-1782000000000-abc123.log": "# cmd: test\ntest output line 1\ntest output line 2\n",
        },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}`);
        assert.equal(res.status, 200);
        assert.equal(res.body.run.missionName, "test-mission");
        assert.equal(res.body.run.status, "running");
        assert.ok(Array.isArray(res.body.events));
        assert.equal(res.body.events.length, 3);
        assert.ok(Array.isArray(res.body.stepLogs));
        assert.equal(res.body.stepLogs.length, 1);
        assert.equal(res.body.stepLogs[0].step, "CHECK");
        assert.ok(res.body.stepLogs[0].fileName.startsWith("oc-CHECK-"));
        assert.ok(res.body.stepLogs[0].sizeBytes > 0);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs/:runId degrades gracefully with no state/events (only logs)", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-empty-mission-driver";
      makeRun(root, runId, {
        logs: { "oc-WORK-123-abc.log": "some output\n" },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}`);
        assert.equal(res.status, 200);
        assert.ok(res.body.run);
        assert.equal(res.body.run.status, "unknown");
        assert.deepEqual(res.body.events, []);
        assert.equal(res.body.stepLogs.length, 1);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs/:runId/logs/:step returns tail lines", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-log-mission-driver";
      const lines = Array.from({ length: 100 }, (_, i) => `line ${i}`);
      makeRun(root, runId, {
        logs: { "oc-CHECK-1782000000000-xyz.log": lines.join("\n") + "\n" },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}/logs/CHECK?tail=10`);
        assert.equal(res.status, 200);
        assert.equal(res.body.step, "CHECK");
        assert.ok(res.body.fileName.startsWith("oc-CHECK-"));
        assert.ok(res.body.lines.length <= 11);
        assert.ok(res.body.truncated);
        const lastLine = res.body.lines[res.body.lines.length - 1];
        assert.ok(lastLine.includes("line 99") || lastLine === "");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("logs/:step accepts an ABSOLUTE logFile in ?file= (basename) and falls back to prefix search (§2)", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-absfile-mission-driver";
      makeRun(root, runId, {
        logs: { "oc-CHECK-1782000000000-xyz.log": "alpha\nbeta\ngamma\n" },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        // Frontend historically sent run-state's absolute logFile. Server must
        // basename it and still serve the file (previously → 404).
        const abs = join(root, "_tmp", runId, "oc-CHECK-1782000000000-xyz.log");
        const res = await fetchJson(
          `${baseUrl(monitor)}/api/runs/${runId}/logs/CHECK?file=${encodeURIComponent(abs)}`,
        );
        assert.equal(res.status, 200);
        assert.equal(res.body.fileName, "oc-CHECK-1782000000000-xyz.log");
        assert.ok(res.body.lines.join("\n").includes("beta"));

        // A bare step name in ?file= (no such oc- file) must NOT 404 — it should
        // fall through to the step-name prefix search.
        const res2 = await fetchJson(
          `${baseUrl(monitor)}/api/runs/${runId}/logs/CHECK?file=CHECK`,
        );
        assert.equal(res2.status, 200);
        assert.ok(res2.body.fileName.startsWith("oc-CHECK-"));
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs/:runId/logs/:step returns 404 for missing step log", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-nolog-mission-driver";
      makeRun(root, runId, {});
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}/logs/NOSUCHSTEP`);
        assert.equal(res.status, 404);
        assert.equal(res.body.error, "log not found");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs/:runId/sysmon returns parsed snapshots", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-sys-mission-driver";
      makeRun(root, runId, {
        sysmon: [
          {
            ts: "2026-06-29T10:00:00Z",
            label: "START",
            vm: { free_GB: 11.9 },
            totalRSS_GB: 19.1,
            cohorts: { opencode: { rss_mb: 1911.5, count: 4 } },
            processCount: 337,
          },
          {
            ts: "2026-06-29T10:05:00Z",
            label: "heartbeat:oc-CHECK",
            vm: { free_GB: 10.5 },
            totalRSS_GB: 20.2,
            cohorts: { opencode: { rss_mb: 2100.0, count: 5 } },
            processCount: 350,
          },
        ],
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}/sysmon`);
        assert.equal(res.status, 200);
        assert.equal(res.body.snapshots.length, 2);
        assert.equal(res.body.snapshots[0].ts, "2026-06-29T10:00:00Z");
        assert.equal(res.body.snapshots[0].label, "START");
        assert.equal(res.body.snapshots[0].freeGB, 11.9);
        assert.equal(res.body.snapshots[0].totalRSS_GB, 19.1);
        assert.equal(res.body.snapshots[0].opencodeRSS_MB, 1911.5);
        assert.equal(res.body.snapshots[0].processCount, 337);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs/:runId/sysmon returns empty when no sys-snapshot.log", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-nosys-mission-driver";
      makeRun(root, runId, {});
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}/sysmon`);
        assert.equal(res.status, 200);
        assert.deepEqual(res.body, { snapshots: [] });
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/configs lists mission configs with lastRunStatus", async () => {
    const root = makeTmpProject();
    try {
      makeMission(root, "mission-a", {
        name: "mission-a",
        description: "Test mission A",
        roadmapPath: "docs/roadmap-a.md",
        moduleDir: "demo-mod",
        plansDir: "docs/plans",
        commands: { test: "npm test" },
      });
      makeRun(root, "2026-06-29-100000-mission-driver", {
        state: { missionName: "mission-a", runId: "2026-06-29-100000-mission-driver", status: "running", steps: [] },
      });

      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/configs`);
        assert.equal(res.status, 200);
        assert.equal(res.body.configs.length, 1);
        assert.equal(res.body.configs[0].name, "mission-a");
        assert.equal(res.body.configs[0].description, "Test mission A");
        assert.equal(res.body.configs[0].roadmapPath, "docs/roadmap-a.md");
        assert.equal(res.body.configs[0].moduleDir, "demo-mod");
        assert.equal(res.body.configs[0].lastRunStatus, "running");
        assert.equal(res.body.configs[0].lastRunId, "2026-06-29-100000-mission-driver");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/configs supports offset paging with total/hasMore (§3.4)", async () => {
    const root = makeTmpProject();
    try {
      // Create 5 missions (c5 created first → oldest mtime, c1 last → newest mtime).
      for (const n of ["c5", "c4", "c3", "c2", "c1"]) {
        makeMission(root, n, {
          name: n,
          description: `mission ${n}`,
          roadmapPath: `docs/${n}.md`,
        });
      }
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        // Default page: limit=9 → all 5 returned, hasMore false.
        const def = await fetchJson(`${baseUrl(monitor)}/api/configs`);
        assert.equal(def.status, 200);
        assert.equal(def.body.total, 5);
        assert.equal(def.body.configs.length, 5);
        assert.equal(def.body.limit, 9);
        assert.equal(def.body.offset, 0);
        assert.equal(def.body.hasMore, false);
        // Back-compat: configs array still present (current page slice).
        assert.ok(Array.isArray(def.body.configs));
        // Sorted by mtime descending (newest first): c1 last written → first.
        assert.deepEqual(
          def.body.configs.map((c) => c.name),
          ["c1", "c2", "c3", "c4", "c5"]
        );

        // Page 1: limit=2 offset=0.
        const p1 = await fetchJson(`${baseUrl(monitor)}/api/configs?limit=2&offset=0`);
        assert.equal(p1.body.total, 5);
        assert.equal(p1.body.configs.length, 2);
        assert.equal(p1.body.hasMore, true);
        assert.deepEqual(
          p1.body.configs.map((c) => c.name),
          ["c1", "c2"]
        );

        // Last page: limit=2 offset=4 → 1 item, hasMore false.
        const p3 = await fetchJson(`${baseUrl(monitor)}/api/configs?limit=2&offset=4`);
        assert.equal(p3.body.configs.length, 1);
        assert.equal(p3.body.hasMore, false);
        assert.deepEqual(
          p3.body.configs.map((c) => c.name),
          ["c5"]
        );

        // No overlap between page 1 and page 2.
        const p2 = await fetchJson(`${baseUrl(monitor)}/api/configs?limit=2&offset=2`);
        const ids1 = new Set(p1.body.configs.map((c) => c.name));
        assert.ok(p2.body.configs.every((c) => !ids1.has(c.name)));
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/configs ranks missions by mtime descending (newest first)", async () => {
    const root = makeTmpProject();
    try {
      makeMission(root, "alpha", { name: "alpha", roadmapPath: "docs/alpha.md" });
      makeMission(root, "beta", { name: "beta", roadmapPath: "docs/beta.md" });
      makeMission(root, "gamma", { name: "gamma", roadmapPath: "docs/gamma.md" });
      // Force distinct mtimes (rapid writes can share the same ms on Windows,
      // making the descending sort a no-op). gamma newest → ranks first.
      const now = Date.now() / 1000;
      utimesSync(join(root, "missions", "alpha.json"), now - 20, now - 20);
      utimesSync(join(root, "missions", "beta.json"), now - 10, now - 10);
      utimesSync(join(root, "missions", "gamma.json"), now, now);
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/configs`);
        assert.deepEqual(
          res.body.configs.map((c) => c.name),
          ["gamma", "beta", "alpha"]
        );
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/configs/:name/roadmap parses the 阶段状态 block (FIX-3 new contract)", async () => {
    const root = makeTmpProject();
    try {
      // Live roadmap shape: only the "## 阶段状态" block carries status. Other
      // ## sections (目的/状态值/框架复用) must NOT be treated as phases.
      const roadmapContent = [
        "# Roadmap Title",
        "",
        "## 目的",
        "",
        "Some purpose prose, not a phase.",
        "",
        "- [x] a stray checkbox that must be ignored",
        "",
        "## 阶段状态",
        "",
        "> **这是唯一的动态状态块。**",
        "",
        "- 1. 事件层改造（mission-driver 引擎侧）：`done`",
        "- 2. Monitor Server（Node 内置 http + SSE）：`done`",
        "- 3. 监控页基础（单 HTML + Alpine.js）：`todo`",
        "- ★ **里程碑：Web 监控能力就绪**（达成 1-4 解锁）：未达成",
        "- 4. 监控页增强（xterm.js + ECharts）：`planned`",
        "",
        "## 状态值",
        "",
        "| 状态 | 含义 |",
        "",
        "## 框架/平台复用",
        "",
        "irrelevant table",
      ].join("\n");
      mkdirSync(join(root, "docs"), { recursive: true });
      writeFileSync(join(root, "docs", "roadmap-a.md"), roadmapContent);

      makeMission(root, "mission-a", {
        name: "mission-a",
        description: "test",
        roadmapPath: "docs/roadmap-a.md",
        plansDir: "docs/plans",
        commands: { test: "npm test" },
      });

      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/configs/mission-a/roadmap`);
        assert.equal(res.status, 200);
        assert.equal(res.body.roadmapPath, "docs/roadmap-a.md");
        // 4 work items + 1 milestone = 5 phases; non-phase headings excluded.
        assert.equal(res.body.phases.length, 5);

        const p1 = res.body.phases.find((p) => p.name.includes("事件层改造"));
        assert.ok(p1, "phase 1 should exist");
        assert.equal(p1.status, "done");
        assert.equal(p1.isMilestone, false);
        assert.equal(p1.seq, 1);
        // New structure: no legacy items/doneCount/totalCount fields.
        assert.equal(p1.totalCount, undefined);
        assert.equal(p1.doneCount, undefined);

        const p3 = res.body.phases.find((p) => p.name.includes("监控页基础"));
        assert.ok(p3);
        assert.equal(p3.status, "todo");

        const p4 = res.body.phases.find((p) => p.name.includes("监控页增强"));
        assert.ok(p4);
        assert.equal(p4.status, "planned");

        const ms = res.body.phases.find((p) => p.isMilestone);
        assert.ok(ms, "milestone should exist");
        assert.equal(ms.status, "not-done");
        assert.equal(ms.seq, null);

        // overallProgress = done work items / total work items = 2/4 = 0.5
        // (milestone excluded from denominator)
        assert.equal(res.body.overallProgress, 0.5);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/configs/:name/roadmap returns empty when mission not found", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/configs/nonexistent/roadmap`);
        assert.equal(res.status, 200);
        assert.equal(res.body.roadmapPath, null);
        assert.deepEqual(res.body.phases, []);
        assert.equal(res.body.overallProgress, 0);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Phase 3: SSE ──────────────────────────────────────────────────────────

describe("Monitor — SSE endpoint (Phase 3)", () => {
  it("sets correct SSE response headers", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-sse-mission-driver";
      makeRun(root, runId, {
        state: { missionName: "test", runId, status: "running", startedAt: "2026-06-29T10:00:00Z", steps: [] },
        events: [{ type: "run_started", ts: "2026-06-29T10:00:00Z", missionName: "test", runId }],
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const result = await fetchHeaders(`${baseUrl(monitor)}/api/runs/${runId}/events`);
        assert.equal(result.status, 200);
        assert.equal(result.headers["content-type"], "text/event-stream");
        assert.ok(result.headers["cache-control"].includes("no-cache"));
        assert.equal(result.headers["x-accel-buffering"], "no");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("sends snapshot event first, then replays history events in SSE frame format", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-sse2-mission-driver";
      makeRun(root, runId, {
        state: {
          missionName: "sse-test",
          runId,
          status: "running",
          startedAt: "2026-06-29T10:00:00Z",
          currentStep: "WORK",
          steps: [],
        },
        events: [
          { type: "run_started", ts: "2026-06-29T10:00:00Z", missionName: "sse-test", runId },
          { type: "step_started", ts: "2026-06-29T10:00:01Z", missionName: "sse-test", runId, step: "WORK" },
        ],
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const data = await fetchSSE(`${baseUrl(monitor)}/api/runs/${runId}/events`, 600);

        assert.ok(data.includes("event: snapshot\ndata: "), "snapshot event should be present");
        assert.ok(data.includes('"missionName":"sse-test"'), "snapshot data should contain run state");

        assert.ok(
          data.match(/event: run_started\ndata: \{.*\}\n\n/),
          "run_started should be in SSE frame format"
        );
        assert.ok(
          data.match(/event: step_started\ndata: \{.*\}\n\n/),
          "step_started should be in SSE frame format"
        );

        const snapshotIdx = data.indexOf("event: snapshot");
        const runStartedIdx = data.indexOf("event: run_started");
        assert.ok(snapshotIdx < runStartedIdx, "snapshot must precede history events");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("tail-polls new events appended after connect", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-tail-mission-driver";
      const runDir = makeRun(root, runId, {
        state: { missionName: "tail-test", runId, status: "running", startedAt: "2026-06-29T10:00:00Z", steps: [] },
        events: [
          { type: "run_started", ts: "2026-06-29T10:00:00Z", missionName: "tail-test", runId },
        ],
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const data = await new Promise((resolve) => {
          const u = new URL(`${baseUrl(monitor)}/api/runs/${runId}/events`);
          let collected = "";
          const req = http.request(
            { hostname: u.hostname, port: u.port, path: u.pathname, method: "GET", agent: false },
            (res) => {
              res.on("data", (chunk) => (collected += chunk.toString()));
            }
          );
          req.on("error", () => {});

          setTimeout(() => {
            appendFileSync(
              join(runDir, "events.jsonl"),
              JSON.stringify({
                type: "step_completed",
                ts: "2026-06-29T10:00:10Z",
                missionName: "tail-test",
                runId,
                step: "WORK",
                marker: "pass",
              }) + "\n"
            );
          }, 400);

          setTimeout(() => {
            try {
              req.destroy();
            } catch {}
            resolve(collected);
          }, 1600);
          req.end();
        });

        assert.ok(data.includes("event: snapshot"), "should have snapshot");
        assert.ok(data.includes("event: run_started"), "should have replayed run_started");
        assert.ok(data.includes("event: step_completed"), "should have tail-polled step_completed");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("sends state_update when run-state.json mtime changes", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-stateupd-mission-driver";
      const runDir = makeRun(root, runId, {
        state: { missionName: "su-test", runId, status: "running", startedAt: "2026-06-29T10:00:00Z", steps: [] },
        events: [{ type: "run_started", ts: "2026-06-29T10:00:00Z", missionName: "su-test", runId }],
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const data = await new Promise((resolve) => {
          const u = new URL(`${baseUrl(monitor)}/api/runs/${runId}/events`);
          let collected = "";
          const req = http.request(
            { hostname: u.hostname, port: u.port, path: u.pathname, method: "GET", agent: false },
            (res) => {
              res.on("data", (chunk) => (collected += chunk.toString()));
            }
          );
          req.on("error", () => {});

          setTimeout(() => {
            const updated = {
              missionName: "su-test",
              runId,
              status: "completed",
              startedAt: "2026-06-29T10:00:00Z",
              steps: [{ name: "WORK", status: "completed" }],
            };
            const tmpFile = join(runDir, "run-state.json.tmp");
            writeFileSync(tmpFile, JSON.stringify(updated, null, 2));
            renameSync(tmpFile, join(runDir, "run-state.json"));
            const future = new Date(Date.now() + 5000);
            utimesSync(join(runDir, "run-state.json"), future, future);
          }, 400);

          setTimeout(() => {
            try {
              req.destroy();
            } catch {}
            resolve(collected);
          }, 1600);
          req.end();
        });

        assert.ok(data.includes("event: state_update"), "should have state_update event after mtime change");
        assert.ok(data.includes('"status":"completed"'), "state_update should carry new status");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("sends error event for non-existent run", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const data = await fetchSSE(`${baseUrl(monitor)}/api/runs/nonexistent/events`, 400);
        assert.ok(data.includes("event: error"), "should have error event");
        assert.ok(data.includes("run not found"), "error message should mention run not found");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("handles events.jsonl with corrupt JSON lines gracefully", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-29-corrupt-mission-driver";
      const runDir = makeRun(root, runId, {
        state: { missionName: "corrupt-test", runId, status: "running", startedAt: "2026-06-29T10:00:00Z", steps: [] },
      });
      writeFileSync(
        join(runDir, "events.jsonl"),
        [
          JSON.stringify({ type: "run_started", ts: "2026-06-29T10:00:00Z", missionName: "corrupt-test", runId }),
          "THIS IS NOT VALID JSON {{{",
          JSON.stringify({ type: "step_started", ts: "2026-06-29T10:00:01Z", missionName: "corrupt-test", runId, step: "WORK" }),
        ].join("\n") + "\n"
      );

      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const data = await fetchSSE(`${baseUrl(monitor)}/api/runs/${runId}/events`, 600);
        assert.ok(data.includes("event: run_started"), "valid event before corrupt line should be replayed");
        assert.ok(data.includes("event: step_started"), "valid event after corrupt line should be replayed");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Phase 4: Detail page fixes (FIX-1 ~ FIX-4) ───────────────────────────

describe("Monitor — detail page fixes (FIX-1~4)", () => {
  it("FIX-1: GET /api/runs/:runId returns config with roadmapPath/plansDir", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-30-fix1-mission-driver";
      makeMission(root, "mission-fix1", {
        name: "mission-fix1",
        description: "fix1 mission",
        roadmapPath: "docs/backlog/roadmap-fix1.md",
        plansDir: "docs/plans/test/mission-fix1",
        moduleDir: "tools/mission-driver",
        flowName: "mission-driver",
        commands: { test: "npm --prefix tools/mission-driver test", build: "npm run build" },
        commitFormat: "feat: x",
        workflow: { status: "running" }, // must NOT leak into config
      });
      makeRun(root, runId, {
        state: {
          missionName: "mission-fix1",
          runId,
          status: "running",
          startedAt: "2026-06-30T10:00:00Z",
          steps: [],
        },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}`);
        assert.equal(res.status, 200);
        assert.ok(res.body.config, "config should be present");
        assert.equal(res.body.config.roadmapPath, "docs/backlog/roadmap-fix1.md");
        assert.equal(res.body.config.plansDir, "docs/plans/test/mission-fix1");
        assert.equal(res.body.config.moduleDir, "tools/mission-driver");
        assert.equal(res.body.config.commands.test, "npm --prefix tools/mission-driver test");
        assert.equal(res.body.config.commands.build, "npm run build");
        assert.equal(res.body.config.commitFormat, "feat: x");
        // runtime `workflow` must not leak (security: whitelist only)
        assert.equal(res.body.config.workflow, undefined);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("FIX-1: GET /api/runs/:runId returns config=null when missionName missing", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-30-fix1b-mission-driver";
      makeRun(root, runId, {
        state: { missionName: null, runId, status: "running", steps: [] },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}`);
        assert.equal(res.status, 200);
        assert.equal(res.body.config, null);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("FIX-2: GET /api/configs/:name/plans returns plan list with status (draft/active/completed)", async () => {
    const root = makeTmpProject();
    try {
      const plansDir = join(root, "docs", "plans", "t", "mission-fix2");
      mkdirSync(plansDir, { recursive: true });
      writeFileSync(join(plansDir, "2026-06-30-001-completed.md"), "> Plan Status: completed\n\n## Phase\n- [x] done\n");
      writeFileSync(join(plansDir, "2026-06-30-002-active.md"), "> Status: active\n\n## Phase\n- [ ] pending\n");
      writeFileSync(join(plansDir, "2026-06-30-003-draft.md"), "> **Plan Status**: *draft*\n\n## Phase\n");
      writeFileSync(join(plansDir, "2026-06-30-004-unknown.md"), "# No status line here\n");
      makeMission(root, "mission-fix2", {
        name: "mission-fix2",
        plansDir: "docs/plans/t/mission-fix2",
      });

      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/configs/mission-fix2/plans`);
        assert.equal(res.status, 200);
        assert.equal(res.body.plansDir, "docs/plans/t/mission-fix2");
        assert.equal(res.body.plans.length, 4);
        const byName = {};
        for (const p of res.body.plans) byName[p.fileName] = p;
        assert.equal(byName["2026-06-30-001-completed.md"].status, "completed");
        assert.equal(byName["2026-06-30-002-active.md"].status, "active");
        assert.equal(byName["2026-06-30-003-draft.md"].status, "draft");
        assert.equal(byName["2026-06-30-004-unknown.md"].status, "unknown");
        // each entry carries size + mtime
        for (const p of res.body.plans) {
          assert.ok(typeof p.sizeBytes === "number" && p.sizeBytes >= 0);
          assert.ok(typeof p.lastModified === "number");
        }
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("FIX-2: GET /api/configs/:name/plans skips 00- prefixed index files", async () => {
    const root = makeTmpProject();
    try {
      const plansDir = join(root, "docs", "plans", "t", "mission-fix2b");
      mkdirSync(plansDir, { recursive: true });
      writeFileSync(join(plansDir, "00-plan-authoring-and-execution-guide.md"), "> Plan Status: completed\n");
      writeFileSync(join(plansDir, "2026-06-30-001-real.md"), "> Plan Status: active\n");
      // non-md files also excluded
      writeFileSync(join(plansDir, "notes.txt"), "ignore me");
      makeMission(root, "mission-fix2b", {
        name: "mission-fix2b",
        plansDir: "docs/plans/t/mission-fix2b",
      });

      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/configs/mission-fix2b/plans`);
        assert.equal(res.status, 200);
        assert.equal(res.body.plans.length, 1);
        assert.equal(res.body.plans[0].fileName, "2026-06-30-001-real.md");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("FIX-2: GET /api/configs/:name/plans returns empty when plansDir missing", async () => {
    const root = makeTmpProject();
    try {
      makeMission(root, "mission-fix2c", {
        name: "mission-fix2c",
        plansDir: "docs/plans/does/not/exist",
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/configs/mission-fix2c/plans`);
        assert.equal(res.status, 200);
        assert.deepEqual(res.body.plans, []);
        // plansDir still reported (the configured path), so UI can show it
        assert.equal(res.body.plansDir, "docs/plans/does/not/exist");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("FIX-2: GET /api/configs/:name/plans returns empty when mission missing (graceful)", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/configs/nonexistent/plans`);
        assert.equal(res.status, 200);
        assert.deepEqual(res.body.plans, []);
        assert.equal(res.body.plansDir, null);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("FIX-3: parseRoadmapMarkdown only parses 阶段状态 work items (with trailing 括注)", () => {
    // Mirrors live demo-mission-roadmap.md work item with a trailing parenthetical
    // ("（批次 1...）") which must not break status parsing.
    const content = [
      "# demo-mod 技术债务",
      "",
      "## 目的",
      "",
      "prose",
      "",
      "## 阶段状态",
      "",
      "- 1. 安全凭据外部化（独立 plan，并行轨）：`done`",
      "- 2. 生产代码污染清理（P-01/P-02）：`done`",
      "- 3. pom 治理（M-01~M-06）：`done`",
      "- 4. 重复 / Dead Code / 质量治理：`planned`（批次 1 机械清理已完成，见下方基线）",
      "",
      "## 状态值",
      "",
      "ignored",
    ].join("\n");
    const { phases, overallProgress } = parseRoadmapMarkdown(content);
    assert.equal(phases.length, 4);
    assert.equal(phases[0].name, "安全凭据外部化（独立 plan，并行轨）");
    assert.equal(phases[0].status, "done");
    assert.equal(phases[0].seq, 1);
    assert.equal(phases[0].isMilestone, false);
    // trailing 括注 tolerated → status still `planned`
    const p4 = phases.find((p) => p.seq === 4);
    assert.ok(p4);
    assert.equal(p4.status, "planned");
    assert.equal(p4.name, "重复 / Dead Code / 质量治理");
    // 3 done / 4 items = 0.75
    assert.equal(overallProgress, 0.75);
  });

  it("FIX-3: parseRoadmapMarkdown recognizes milestones (已达成→done) and excludes from overallProgress", () => {
    // Live line from mission-monitoring-dashboard-roadmap.md:25 (已达成, no backticks)
    const content = [
      "## 阶段状态",
      "",
      "- 1. 事件层改造（引擎侧）：`done`",
      "- 2. Monitor Server：`done`",
      "- 3. 监控页基础：`done`",
      "- 4. 监控页增强：`done`",
      "- ★ **里程碑：Web 监控能力就绪**（达成 1-4 解锁）：已达成",
      "- 5. 控制台心跳日志尾部增强（独立轨）：`done`",
    ].join("\n");
    const { phases, overallProgress } = parseRoadmapMarkdown(content);
    const ms = phases.find((p) => p.isMilestone);
    assert.ok(ms, "milestone must be recognized");
    assert.equal(ms.name, "★ Web 监控能力就绪");
    assert.equal(ms.status, "done"); // 已达成 normalized to done
    assert.equal(ms.seq, null);
    // 5 work items (milestone excluded) all done → 1
    assert.equal(overallProgress, 1);
  });

  it("FIX-3: parseRoadmapMarkdown also matches `done` milestone token (backticked, live demo-mission)", () => {
    const content = [
      "## 阶段状态",
      "",
      "- 2. 生产代码污染清理：`done`",
      "- 3. pom 治理：`done`",
      "- ★ **里程碑：demo-mod 工具就绪**（达成 2+3 解锁）：`done`",
    ].join("\n");
    const { phases, overallProgress } = parseRoadmapMarkdown(content);
    const ms = phases.find((p) => p.isMilestone);
    assert.ok(ms);
    assert.equal(ms.status, "done");
    assert.equal(overallProgress, 1); // 2/2 work items
  });

  it("FIX-3: parseRoadmapMarkdown returns empty when no 阶段状态 block", () => {
    const content = [
      "# Roadmap",
      "",
      "## 目的",
      "",
      "prose",
      "",
      "## 阶段",
      "",
      "| # | 阶段 |",
    ].join("\n");
    const result = parseRoadmapMarkdown(content);
    assert.deepEqual(result.phases, []);
    assert.equal(result.overallProgress, 0);
  });

  it("guide format: parseRoadmapMarkdown parses ## Work Item Status markdown table", () => {
    const content = [
      "# Roadmap — Example",
      "",
      "## Purpose",
      "",
      "prose",
      "",
      "## Work Item Status",
      "",
      "| Work Item | Status | Owner Doc / Source | Dependencies | Reuse |",
      "| --------- | ------ | ------------------ | ------------ | ----- |",
      "| M1/WI1 基线测量（全量套件总耗时） | done | brief §范围 | — | `node --test` |",
      "| M1/WI2 单文件耗时排名 | done | brief §范围 | WI1 | `time` |",
      "| M1/WI3 组合运行耗时对比 | ready | brief §范围 | WI1 | — |",
      "| M1/WI4 根因分析 | todo | brief §范围 | WI2, WI3 | — |",
      "",
      "## Milestones",
      "",
      "### M1 慢测试诊断",
      "",
      "- **WI1 基线测量** — 运行全量套件",
      "",
      "## Framework / Platform Reuse",
      "",
      "irrelevant",
    ].join("\n");
    const { phases, overallProgress } = parseRoadmapMarkdown(content);
    // 4 work items from the table; milestone section ignored (no status).
    assert.equal(phases.length, 4);
    assert.equal(phases.every((p) => !p.isMilestone), true);

    const p1 = phases.find((p) => p.name.includes("基线测量"));
    assert.ok(p1);
    assert.equal(p1.status, "done");
    assert.equal(p1.seq, null);

    const p3 = phases.find((p) => p.name.includes("组合运行"));
    assert.ok(p3);
    assert.equal(p3.status, "ready");

    const p4 = phases.find((p) => p.name.includes("根因分析"));
    assert.ok(p4);
    assert.equal(p4.status, "todo");

    // 2 done / 4 items = 0.5
    assert.equal(overallProgress, 0.5);
  });

  it("guide format: parseRoadmapMarkdown parses ## Work Item Status bullet list", () => {
    const content = [
      "## Work Item Status",
      "",
      "### Milestone 1 — Core",
      "",
      "- 基线测量: `done`",
      "- 排名分析: `todo`",
      "",
      "### Milestone 2 — Enhancements",
      "",
      "- 增强功能: `ready`",
      "",
      "## Framework Reuse",
      "",
      "ignored",
    ].join("\n");
    const { phases, overallProgress } = parseRoadmapMarkdown(content);
    assert.equal(phases.length, 3);
    assert.equal(phases[0].name, "基线测量");
    assert.equal(phases[0].status, "done");
    assert.equal(phases[1].name, "排名分析");
    assert.equal(phases[1].status, "todo");
    assert.equal(phases[2].name, "增强功能");
    assert.equal(phases[2].status, "ready");
    // 1 done / 3 items = 0.33 (rounded to 2 decimals)
    assert.equal(overallProgress, 0.33);
  });

  it("FIX-4: GET /api/runs/:runId/logs/:step returns absolute filePath", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-30-fix4-mission-driver";
      makeRun(root, runId, {
        logs: { "oc-CHECK-1783000000000-abc.log": "line1\nline2\n" },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}/logs/CHECK?tail=10`);
        assert.equal(res.status, 200);
        assert.ok(res.body.filePath, "filePath should be present");
        // absolute path ending with the log file name, inside the run dir
        assert.ok(res.body.filePath.endsWith("oc-CHECK-1783000000000-abc.log"));
        assert.ok(
          res.body.filePath.includes("_tmp") || res.body.filePath.includes(runId),
          "filePath should locate the file within the run dir"
        );
        // fileName still present (backward compat)
        assert.equal(res.body.fileName, "oc-CHECK-1783000000000-abc.log");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Phase 2 (OPT-1): sessionId passthrough + backward compat ───────────────

describe("Monitor — OPT-1 sessionId passthrough (Phase 2)", () => {
  it("GET /api/runs/:runId returns run.steps[] with sessionId when present in run-state.json", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-30-opt1-mission-driver";
      makeRun(root, runId, {
        state: {
          missionName: "opt1-mission",
          runId,
          status: "running",
          startedAt: "2026-06-30T10:00:00Z",
          currentStep: "WORK",
          steps: [
            { name: "CHECK", status: "completed", marker: "pass", sessionId: "ses_check_abc" },
            { name: "WORK", status: "completed", marker: "ok", sessionId: "ses_work_def" },
          ],
        },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}`);
        assert.equal(res.status, 200);
        assert.ok(Array.isArray(res.body.run.steps));
        assert.equal(res.body.run.steps.length, 2);
        assert.equal(res.body.run.steps[0].sessionId, "ses_check_abc");
        assert.equal(res.body.run.steps[1].sessionId, "ses_work_def");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs/:runId hides sessionId (undefined) for legacy run-state.json without it", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-20-legacy-mission-driver";
      // Historical run-state.json: steps[] predate OPT-1, no sessionId field.
      makeRun(root, runId, {
        state: {
          missionName: "legacy-mission",
          runId,
          status: "completed",
          startedAt: "2026-06-20T08:00:00Z",
          endedAt: "2026-06-20T10:00:00Z",
          steps: [
            { name: "CHECK", status: "completed", marker: "pass" },
            { name: "WORK", status: "failed", marker: "fail" },
          ],
        },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}`);
        assert.equal(res.status, 200);
        assert.ok(Array.isArray(res.body.run.steps));
        assert.equal(res.body.run.steps.length, 2);
        // Must not error; sessionId simply absent (frontend hides the entry).
        assert.equal(res.body.run.steps[0].sessionId, undefined);
        assert.equal(res.body.run.steps[1].sessionId, undefined);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs/:runId mixes sessionId presence per step without error", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-30-mixed-mission-driver";
      makeRun(root, runId, {
        state: {
          missionName: "mixed-mission",
          runId,
          status: "running",
          startedAt: "2026-06-30T10:00:00Z",
          steps: [
            { name: "CHECK", status: "completed", marker: "pass", sessionId: "ses_mix_1" },
            { name: "EXEC_PLANS", status: "completed", marker: "all_complete" }, // subflow, no session
            { name: "WORK", status: "completed", marker: "ok", sessionId: "ses_mix_2" },
          ],
        },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}`);
        assert.equal(res.status, 200);
        assert.equal(res.body.run.steps[0].sessionId, "ses_mix_1");
        assert.equal(res.body.run.steps[1].sessionId, undefined);
        assert.equal(res.body.run.steps[2].sessionId, "ses_mix_2");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// OPT-7: suspended flag passthrough (Phase 3). monitor.js transparently returns
// run.steps[] from run-state.json, so steps carrying `suspended:true` (set by
// the engine's onSuspend handler) flow through unchanged; legacy run-state.json
// without the field must not error and the frontend hides the entry.
describe("Monitor — OPT-7 suspended passthrough (Phase 3)", () => {
  it("GET /api/runs/:runId returns run.steps[] with suspended:true when present", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-30-opt7-mission-driver";
      makeRun(root, runId, {
        state: {
          missionName: "opt7-mission",
          runId,
          status: "running",
          startedAt: "2026-06-30T10:00:00Z",
          currentStep: "EXECUTE",
          steps: [
            { name: "CHECK", status: "completed", marker: "pass" },
            { name: "EXECUTE", status: "running", suspended: true, suspendGapMs: 3720000 },
          ],
        },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}`);
        assert.equal(res.status, 200);
        assert.ok(Array.isArray(res.body.run.steps));
        assert.equal(res.body.run.steps.length, 2);
        assert.equal(res.body.run.steps[0].suspended, undefined);
        assert.equal(res.body.run.steps[1].suspended, true);
        assert.equal(res.body.run.steps[1].suspendGapMs, 3720000);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs/:runId hides suspended (undefined) for legacy run-state.json without it", async () => {
    const root = makeTmpProject();
    try {
      const runId = "2026-06-20-legacy7-mission-driver";
      // Historical run-state.json: steps[] predate OPT-7, no suspended field.
      makeRun(root, runId, {
        state: {
          missionName: "legacy7-mission",
          runId,
          status: "completed",
          startedAt: "2026-06-20T08:00:00Z",
          endedAt: "2026-06-20T10:00:00Z",
          steps: [
            { name: "CHECK", status: "completed", marker: "pass" },
            { name: "WORK", status: "failed", marker: "fail" },
          ],
        },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs/${runId}`);
        assert.equal(res.status, 200);
        assert.ok(Array.isArray(res.body.run.steps));
        assert.equal(res.body.run.steps.length, 2);
        // Must not error; suspended simply absent (frontend hides the badge).
        assert.equal(res.body.run.steps[0].suspended, undefined);
        assert.equal(res.body.run.steps[1].suspended, undefined);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Roadmap item 4: Monitor Server adaptation (prod hosts dist/, --dev flag) ─

describe("Monitor — static hosting prod/dev adaptation (roadmap item 4)", () => {
  it("prod mode (webDir set): serves dist/index.html and static assets with MIME", async () => {
    const root = makeTmpProject();
    try {
      const distDir = join(root, "web", "dist");
      mkdirSync(join(distDir, "assets"), { recursive: true });
      writeFileSync(join(distDir, "index.html"), "<!DOCTYPE html><html><body>Vue UI</body></html>");
      writeFileSync(join(distDir, "assets", "index-abc.js"), "console.log('js')");
      writeFileSync(join(distDir, "assets", "style.css"), "body{color:#fff}");

      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: distDir });
      try {
        const indexRes = await fetchJson(`${baseUrl(monitor)}/`);
        assert.equal(indexRes.status, 200);
        assert.ok(indexRes.headers["content-type"].includes("text/html"));
        assert.ok(indexRes.body.includes("Vue UI"));

        const jsRes = await fetchJson(`${baseUrl(monitor)}/assets/index-abc.js`);
        assert.equal(jsRes.status, 200);
        assert.ok(jsRes.headers["content-type"].includes("application/javascript"));
        assert.ok(jsRes.body.includes("console.log"));

        const cssRes = await fetchJson(`${baseUrl(monitor)}/assets/style.css`);
        assert.equal(cssRes.status, 200);
        assert.ok(cssRes.headers["content-type"].includes("text/css"));
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("prod mode (webDir set) but dist missing: GET / degrades to placeholder, API still works", async () => {
    const root = makeTmpProject();
    try {
      const distDir = join(root, "web", "dist"); // intentionally NOT created
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: distDir });
      try {
        const indexRes = await fetchJson(`${baseUrl(monitor)}/`);
        assert.equal(indexRes.status, 200);
        assert.ok(indexRes.headers["content-type"].includes("text/html"));
        // Placeholder text (API-only mode), not a real UI.
        assert.ok(indexRes.body.includes("API-only mode"));

        // Missing asset in prod → 404.
        const assetRes = await fetchJson(`${baseUrl(monitor)}/assets/missing.js`);
        assert.equal(assetRes.status, 404);

        // API still functional.
        const runsRes = await fetchJson(`${baseUrl(monitor)}/api/runs`);
        assert.equal(runsRes.status, 200);
        assert.deepEqual(runsRes.body.runs, []);
        assert.equal(runsRes.body.total, 0);
        assert.equal(runsRes.body.hasMore, false);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("dev mode (webDir=null): GET / returns JSON hint, static GET returns hint, API/SSE unaffected", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: null });
      try {
        const indexRes = await fetchJson(`${baseUrl(monitor)}/`);
        assert.equal(indexRes.status, 200);
        assert.ok(indexRes.headers["content-type"].includes("application/json"));
        assert.ok(indexRes.body.error.includes("dev mode"));

        // Other static GET also returns the hint (not 404, not a file read).
        const assetRes = await fetchJson(`${baseUrl(monitor)}/assets/whatever.js`);
        assert.equal(assetRes.status, 200);
        assert.ok(assetRes.body.error.includes("dev mode"));

        // API routes unaffected.
        const runsRes = await fetchJson(`${baseUrl(monitor)}/api/runs`);
        assert.equal(runsRes.status, 200);
        assert.deepEqual(runsRes.body.runs, []);
        assert.equal(runsRes.body.total, 0);
        assert.equal(runsRes.body.hasMore, false);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── Phase 5 (abo-it-5): scenario / node-detail / scenario-config endpoints ──

describe("Monitor — POST /api/runs (itp2-5 / FSD §6)", () => {
  // Stubbed spawner so no real `node main.js` engine process is launched.
  let lastSpawn = null;
  let prevSpawner = null;

  beforeEach(() => {
    lastSpawn = null;
    prevSpawner = __setSpawnerForTest((cmd, args, opts) => {
      lastSpawn = { cmd, args, opts };
      return { unref() {} };
    });
  });
  afterEach(() => {
    __setSpawnerForTest(prevSpawner);
  });

  // POST helper (the global fetchJson is GET-only).
  function postJson(urlstr, body) {
    return new Promise((resolve, reject) => {
      const u = new URL(urlstr);
      const payload = JSON.stringify(body);
      const req = http.request(
        {
          hostname: u.hostname,
          port: u.port,
          path: u.pathname + u.search,
          method: "POST",
          headers: { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(payload) },
          agent: false,
        },
        (res) => {
          let data = "";
          res.on("data", (chunk) => (data += chunk));
          res.on("end", () => {
            try {
              resolve({ status: res.statusCode, body: JSON.parse(data) });
            } catch {
              resolve({ status: res.statusCode, body: data });
            }
          });
        }
      );
      req.on("error", reject);
      req.write(payload);
      req.end();
    });
  }

  function writeRunnableMission(root, name) {
    writeFileSync(
      join(root, "missions", `${name}.json`),
      JSON.stringify({
        name,
        roadmapPath: "docs/roadmaps/x.md",
        flowName: "integration-test",
        targets: [{ key: "STATIC-1", summary: "static", verifyKind: "ui" }],
      }),
    );
  }

  it("POST /api/runs launches a whitelisted mission + writes input-targets.json", async () => {
    const root = makeTmpProject();
    try {
      writeRunnableMission(root, "integration-test-v2");
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await postJson(`${baseUrl(monitor)}/api/runs`, {
          missionName: "integration-test-v2",
          targets: [{ key: "PROJ-4023" }, { key: "PROJ-4019" }],
        });
        assert.equal(res.status, 200);
        assert.equal(res.body.missionName, "integration-test-v2");
        assert.ok(res.body.runId, "runId returned");

        // runDir created under _tmp/ and input-targets.json persisted.
        const runDir = join(root, "_tmp", res.body.runId);
        assert.ok(existsSync(runDir), "runDir created");
        const written = JSON.parse(readFileSync(join(runDir, "input-targets.json"), "utf8"));
        assert.equal(written.targets.length, 2);
        assert.equal(written.targets[0].key, "PROJ-4023");

        // spawn safety: process.execPath + args array + shell:false (FSD §8 R2).
        assert.ok(lastSpawn, "spawn was invoked");
        assert.equal(lastSpawn.cmd, process.execPath);
        assert.equal(lastSpawn.opts.shell, false, "shell must be false");
        assert.ok(Array.isArray(lastSpawn.args), "args must be an array");
        assert.ok(lastSpawn.args.some((a) => typeof a === "string" && a.endsWith("main.js")), "main.js in args");
        assert.ok(lastSpawn.args.includes("integration-test-v2"), "sanitised mission name in args");
        assert.ok(lastSpawn.args.includes("--dir"), "--dir flag in args");
        assert.ok(lastSpawn.args.includes(root), "projectRoot passed as --dir value");
        assert.ok(lastSpawn.args.includes("--run-dir"), "--run-dir flag in args");
        assert.ok(lastSpawn.args.includes(res.body.runId), "runDir basename passed as --run-dir value");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("POST /api/runs omits input-targets.json when no targets supplied (CLI default)", async () => {
    const root = makeTmpProject();
    try {
      writeRunnableMission(root, "integration-test-v2");
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await postJson(`${baseUrl(monitor)}/api/runs`, { missionName: "integration-test-v2" });
        assert.equal(res.status, 200);
        const runDir = join(root, "_tmp", res.body.runId);
        assert.ok(existsSync(runDir));
        assert.ok(!existsSync(join(runDir, "input-targets.json")), "no override file when targets absent");
        assert.ok(lastSpawn, "spawn still invoked for the default run");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("POST /api/runs rejects an unknown mission (readMissionConfig null → 400)", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await postJson(`${baseUrl(monitor)}/api/runs`, { missionName: "does-not-exist" });
        assert.equal(res.status, 400);
        assert.equal(res.body.error, "unknown or non-runnable mission");
        assert.equal(lastSpawn, null, "no spawn on rejection");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("POST /api/runs rejects a non-runnable mission (no roadmapPath, e.g. base) → 400", async () => {
    const root = makeTmpProject();
    try {
      // base.json lacks roadmapPath — readMissionConfig returns non-null, but the
      // roadmapPath gate must still reject it (the blocking issue from draft review).
      writeFileSync(
        join(root, "missions", "base.json"),
        JSON.stringify({ name: "base", model: "x" }),
      );
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await postJson(`${baseUrl(monitor)}/api/runs`, { missionName: "base" });
        assert.equal(res.status, 400);
        assert.equal(res.body.error, "unknown or non-runnable mission");
        assert.equal(lastSpawn, null);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("POST /api/runs rejects malformed targets (non-array / missing key) → 400", async () => {
    const root = makeTmpProject();
    try {
      writeRunnableMission(root, "integration-test-v2");
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const r1 = await postJson(`${baseUrl(monitor)}/api/runs`, {
          missionName: "integration-test-v2",
          targets: "not-an-array",
        });
        assert.equal(r1.status, 400);
        assert.match(r1.body.error, /targets/i);

        const r2 = await postJson(`${baseUrl(monitor)}/api/runs`, {
          missionName: "integration-test-v2",
          targets: [{ noKeyOrScenario: true }],
        });
        assert.equal(r2.status, 400);
        assert.match(r2.body.error, /key or scenario/i);
        assert.equal(lastSpawn, null, "no spawn on validation failure");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("POST /api/runs rejects a non-object body → 400", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await postJson(`${baseUrl(monitor)}/api/runs`, [1, 2, 3]);
        assert.equal(res.status, 400);
        assert.match(res.body.error, /JSON object/i);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

describe("Monitor — stale-run reconciliation wiring (sr-2)", () => {
  // A pid virtually guaranteed absent from the process table (mirrors
  // run-reconcile.test.js DEAD_PID). isAliveAndOurs(DEAD_PID,...) is therefore
  // false — the run is judged stale. The test process cmdline lacks
  // main.js/runId so even a recycled pid would not be mis-judged as ours.
  const DEAD_PID = 9_999_899;

  function fetchDelete(urlstr) {
    return new Promise((resolve, reject) => {
      const u = new URL(urlstr);
      const req = http.request(
        {
          hostname: u.hostname,
          port: u.port,
          path: u.pathname + u.search,
          method: "DELETE",
          agent: false,
        },
        (res) => {
          let data = "";
          res.on("data", (chunk) => (data += chunk));
          res.on("end", () => {
            try {
              resolve({ status: res.statusCode, body: JSON.parse(data) });
            } catch {
              resolve({ status: res.statusCode, body: data });
            }
          });
        }
      );
      req.on("error", reject);
      req.end();
    });
  }

  it("startup sweep reconciles a ghost running run to aborted", async () => {
    const root = makeTmpProject();
    try {
      // Ghost created BEFORE the monitor starts → the startup
      // reconcileStaleRuns(projectRoot) sweep (FSD §3.1.4 G3) must catch it.
      makeRun(root, "2026-07-03-sr2-startup-mission-driver", {
        state: {
          runId: "2026-07-03-sr2-startup",
          missionName: "sr2-startup",
          flowName: "mission-driver",
          status: "running",
          pid: DEAD_PID,
          startedAt: "2026-07-03T10:00:00.000Z",
          updatedAt: "2026-07-03T10:00:00.000Z",
        },
      });
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/runs`);
        assert.equal(res.status, 200);
        assert.equal(res.body.runs.length, 1);
        assert.equal(res.body.runs[0].status, "aborted");
        assert.ok(res.body.runs[0].endedAt, "endedAt stamped by startup reconciliation");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/runs lazily reconciles a ghost and unlocks DELETE", async () => {
    const root = makeTmpProject();
    try {
      // Monitor starts on an empty project (nothing to sweep), THEN the ghost is
      // planted — so the only thing that can heal it is the lazy per-request
      // check inside /api/runs (FSD §4.2 R3), not the startup sweep.
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const runId = "2026-07-03-sr2-lazy";
        const dirName = `${runId}-mission-driver`;
        makeRun(root, dirName, {
          state: {
            runId,
            missionName: "sr2-lazy",
            flowName: "mission-driver",
            status: "running",
            pid: DEAD_PID,
            startedAt: "2026-07-03T10:00:00.000Z",
            updatedAt: "2026-07-03T10:00:00.000Z",
          },
        });

        // Before any reconciliation, DELETE is blocked by the running guard.
        const blocked = await fetchDelete(`${baseUrl(monitor)}/api/runs/${dirName}`);
        assert.equal(blocked.status, 409);

        // The GET /api/runs request triggers lazy reconciliation → disk rewritten.
        const list = await fetchJson(`${baseUrl(monitor)}/api/runs`);
        assert.equal(list.status, 200);
        const row = list.body.runs.find((r) => r.runId === runId);
        assert.ok(row, "ghost run appears in the list");
        assert.equal(row.status, "aborted");
        assert.ok(row.endedAt, "endedAt stamped by lazy reconciliation");

        // abortReason is written to disk (the list summary omits it).
        const onDisk = JSON.parse(
          readFileSync(join(root, "_tmp", dirName, "run-state.json"), "utf8")
        );
        assert.equal(onDisk.status, "aborted");
        assert.ok(onDisk.abortReason, "abortReason written to disk");

        // GET /api/runs/:id also reflects the reconciled state + abortReason.
        const detail = await fetchJson(`${baseUrl(monitor)}/api/runs/${dirName}`);
        assert.equal(detail.status, 200);
        assert.equal(detail.body.run.status, "aborted");
        assert.ok(detail.body.run.abortReason, "abortReason surfaced in run detail");

        // After reconciliation, DELETE is no longer blocked by the running guard.
        const del = await fetchDelete(`${baseUrl(monitor)}/api/runs/${dirName}`);
        assert.equal(del.status, 200);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── mdo-2 Phase 2: Mission Draft endpoints (POST/GET /api/missions/draft) ──

describe("Monitor — Mission Draft endpoints (mdo-2 / FSD §3.1A-C)", () => {
  // Stubbed spawner so no real `node main.js draft` child is launched. The
  // monitor reuses its __setSpawnerForTest seam (startDraftJob calls the same
  // module-level spawner).
  let lastSpawn = null;
  let prevSpawner = null;

  beforeEach(() => {
    lastSpawn = null;
    prevSpawner = __setSpawnerForTest((cmd, args, opts) => {
      lastSpawn = { cmd, args, opts };
      return { unref() {}, pid: 7700 };
    });
  });
  afterEach(() => {
    __setSpawnerForTest(prevSpawner);
  });

  function postJson(urlstr, body) {
    return new Promise((resolve, reject) => {
      const u = new URL(urlstr);
      const payload = JSON.stringify(body);
      const req = http.request(
        {
          hostname: u.hostname,
          port: u.port,
          path: u.pathname + u.search,
          method: "POST",
          headers: { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(payload) },
          agent: false,
        },
        (res) => {
          let data = "";
          res.on("data", (chunk) => (data += chunk));
          res.on("end", () => {
            try {
              resolve({ status: res.statusCode, body: JSON.parse(data) });
            } catch {
              resolve({ status: res.statusCode, body: data });
            }
          });
        }
      );
      req.on("error", reject);
      req.write(payload);
      req.end();
    });
  }

  it("POST /api/missions/draft starts a draft job (jobId returned, spawn shell:false)", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await postJson(`${baseUrl(monitor)}/api/missions/draft`, {
          desc: "build a fraud detection mission",
        });
        assert.equal(res.status, 200);
        assert.ok(res.body.jobId, "jobId returned");
        assert.ok(res.body.jobId.startsWith("draft-"));

        // jobDir created under _tmp with a running draft-state.json
        const jobDir = join(root, "_tmp", res.body.jobId);
        assert.ok(existsSync(jobDir), "jobDir created");
        const state = JSON.parse(readFileSync(join(jobDir, "draft-state.json"), "utf8"));
        assert.equal(state.status, "running");
        assert.equal(state.desc, "build a fraud detection mission");

        // spawn safety
        assert.ok(lastSpawn, "spawn invoked");
        assert.equal(lastSpawn.cmd, process.execPath);
        assert.equal(lastSpawn.opts.shell, false);
        assert.ok(lastSpawn.args.includes("draft"), "draft subcommand");
        assert.ok(lastSpawn.args.includes("build a fraud detection mission"), "desc as single argv");
        assert.ok(lastSpawn.args.includes("--draft-job-dir"));
        assert.ok(lastSpawn.args.includes(jobDir), "jobDir passed to child");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/missions/draft/:jobId returns state + logTail", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const start = await postJson(`${baseUrl(monitor)}/api/missions/draft`, { desc: "make mission" });
        const jobId = start.body.jobId;
        // Simulate completion: overwrite state + write a log.
        const jobDir = join(root, "_tmp", jobId);
        writeFileSync(
          join(jobDir, "draft-state.json"),
          JSON.stringify({
            status: "completed",
            missionName: "fraud-detection",
            roadmapPath: "docs/fraud.md",
          }),
        );
        writeFileSync(join(jobDir, "mission-draft.log"), "generating...\ndone\n");

        const res = await fetchJson(`${baseUrl(monitor)}/api/missions/draft/${jobId}`);
        assert.equal(res.status, 200);
        assert.equal(res.body.state.status, "completed");
        assert.equal(res.body.state.missionName, "fraud-detection");
        assert.ok(res.body.logTail.includes("done"));
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/missions/draft lists recent draft jobs (default 9)", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        // Start 2 draft jobs
        await postJson(`${baseUrl(monitor)}/api/missions/draft`, { desc: "make mission one" });
        await postJson(`${baseUrl(monitor)}/api/missions/draft`, { desc: "make mission two" });
        const res = await fetchJson(`${baseUrl(monitor)}/api/missions/draft`);
        assert.equal(res.status, 200);
        assert.equal(res.body.jobs.length, 2);
        assert.ok(res.body.jobs.every((j) => j.jobId.startsWith("draft-")));
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("POST /api/missions/draft rejects empty desc → 400", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await postJson(`${baseUrl(monitor)}/api/missions/draft`, { desc: "   " });
        assert.equal(res.status, 400);
        assert.match(res.body.error, /desc/i);
        assert.equal(lastSpawn, null, "no spawn on validation failure");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("POST /api/missions/draft rejects >2KB desc → 400", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const tooLong = "x".repeat(2049);
        const res = await postJson(`${baseUrl(monitor)}/api/missions/draft`, { desc: tooLong });
        assert.equal(res.status, 400);
        assert.match(res.body.error, /2\d{3}|bytes/i);
        assert.equal(lastSpawn, null);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/missions/draft/:jobId rejects path traversal (jobId with ..) → 400", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/missions/draft/..%2f..%2fetc%2fpasswd`);
        assert.equal(res.status, 400);
        assert.match(res.body.error, /jobId/i);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/missions/draft/:jobId returns 404 for unknown jobId", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/missions/draft/draft-nope-mission-draft`);
        assert.equal(res.status, 404);
        assert.match(res.body.error, /not found/i);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── mdo-4 P2: extended draft body + Flow dropdown + Browse endpoints ──────

describe("Monitor — P2 draft body extension + flows + browse (mdo-4)", () => {
  let lastSpawn = null;
  let prevSpawner = null;

  beforeEach(() => {
    lastSpawn = null;
    prevSpawner = __setSpawnerForTest((cmd, args, opts) => {
      lastSpawn = { cmd, args, opts };
      return { unref() {}, pid: 8800 };
    });
  });
  afterEach(() => {
    __setSpawnerForTest(prevSpawner);
  });

  it("POST /api/missions/draft passes flowHint/targetFile/skipBrief to spawn argv", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await postJson(`${baseUrl(monitor)}/api/missions/draft`, {
          desc: "build it",
          flowHint: "mission-driver",
          targetFile: "docs/backlog/x.md",
          skipBrief: true,
        });
        assert.equal(res.status, 200);
        assert.ok(lastSpawn, "spawn invoked");
        assert.ok(lastSpawn.args.includes("--flow-hint"), "--flow-hint in argv");
        assert.ok(lastSpawn.args.includes("mission-driver"), "flowHint value in argv");
        assert.ok(lastSpawn.args.includes("--target-file"), "--target-file in argv");
        assert.ok(lastSpawn.args.includes("docs/backlog/x.md"), "targetFile value in argv");
        assert.ok(lastSpawn.args.includes("--skip-brief"), "--skip-brief in argv");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("POST /api/missions/draft rejects path-traversal targetFile → 400", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await postJson(`${baseUrl(monitor)}/api/missions/draft`, {
          desc: "build feature",
          targetFile: "../../etc/passwd",
        });
        assert.equal(res.status, 400);
        assert.match(res.body.error, /projectRoot/i);
        assert.equal(lastSpawn, null, "no spawn on validation failure");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("POST /api/missions/draft rejects illegal flowHint chars → 400", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await postJson(`${baseUrl(monitor)}/api/missions/draft`, {
          desc: "build feature",
          flowHint: "bad flow;rm -rf",
        });
        assert.equal(res.status, 400);
        assert.match(res.body.error, /flowHint/i);
        assert.equal(lastSpawn, null);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/flows returns top-level flows, excludes subflows, no kind tag", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/flows`);
        assert.equal(res.status, 200);
        assert.ok(Array.isArray(res.body.flows), "flows is an array");
        assert.ok(res.body.flows.length > 0, "non-empty (tool flows scanned)");
        const byName = Object.fromEntries(res.body.flows.map((f) => [f.name, f]));
        // Top-level flows present.
        assert.ok(byName["mission-driver"], "mission-driver present");
        assert.ok(byName["mission-driver"].stepCount > 0, "stepCount > 0");
        assert.ok(byName["mission-driver"].entry, "entry populated");
        // No kind tag (a flow defines itself; no dev/test label).
        assert.equal(byName["mission-driver"].kind, undefined, "kind removed");
        // Subflows (referenced via subflow steps) are filtered out — they are
        // not valid top-level mission flows.
        for (const sub of ["plan-execution", "deep-audit-loop"]) {
          assert.equal(byName[sub], undefined, `${sub} (subflow) excluded`);
        }
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/browse returns project entries (skips _tmp/node_modules/hidden)", async () => {
    const root = makeTmpProject();
    try {
      // seed some entries in the temp project root
      mkdirSync(join(root, "src"), { recursive: true });
      mkdirSync(join(root, "node_modules"), { recursive: true });
      writeFileSync(join(root, "README.md"), "# hi");
      writeFileSync(join(root, ".hidden"), "x");

      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/browse`);
        assert.equal(res.status, 200);
        const names = res.body.entries.map((e) => e.name);
        assert.ok(names.includes("src"), "src listed");
        assert.ok(names.includes("README.md"), "README.md listed");
        assert.ok(!names.includes("_tmp"), "_tmp skipped");
        assert.ok(!names.includes("node_modules"), "node_modules skipped");
        assert.ok(!names.includes(".hidden"), "hidden file skipped");
        // entries carry project-relative paths
        const src = res.body.entries.find((e) => e.name === "src");
        assert.equal(src.isDir, true);
        assert.ok(src.path, "path field populated");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/browse?prefix=tools drills into a subdirectory", async () => {
    const root = makeTmpProject();
    try {
      mkdirSync(join(root, "tools"), { recursive: true });
      writeFileSync(join(root, "tools", "build.js"), "x");

      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/browse?prefix=tools`);
        assert.equal(res.status, 200);
        const names = res.body.entries.map((e) => e.name);
        assert.ok(names.includes("build.js"), "build.js listed under tools/");
        const entry = res.body.entries.find((e) => e.name === "build.js");
        assert.equal(entry.path, "tools/build.js", "project-relative path");
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET /api/browse?prefix=../../ rejects path traversal → 400", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/browse?prefix=../../`);
        assert.equal(res.status, 400);
        assert.match(res.body.error, /projectRoot/i);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── mdr-remediate-2 N2: monitor-side pre-validation (handleStartDraft) ─────

describe("Monitor — handleStartDraft pre-validation (mdr-remediate-2 N2)", () => {
  // Mirror the __setSpawnerForTest setup at the mdo-2 draft test block. A
  // spawn counter captures how many times the spawner was invoked so N2
  // tests can assert "0 spawns on validation failure" and N2-D can assert
  // "exactly 1 spawn on valid desc". handleStartDraft is called directly
  // (no HTTP) so the test isolates the pre-validation gate from the server.
  let spawnCount = 0;
  let prevSpawner = null;

  beforeEach(() => {
    spawnCount = 0;
    prevSpawner = __setSpawnerForTest(() => {
      spawnCount += 1;
      return { unref() {}, pid: 9900 };
    });
  });
  afterEach(() => {
    __setSpawnerForTest(prevSpawner);
  });

  function makeTmpRoot() {
    const root = mkdtempSync(join(tmpdir(), "md-n2-"));
    mkdirSync(join(root, "_tmp"), { recursive: true });
    mkdirSync(join(root, "missions"), { recursive: true });
    return root;
  }

  function draftDirs(root) {
    try {
      return readdirSync(join(root, "_tmp")).filter((f) => f.startsWith("draft-"));
    } catch {
      return [];
    }
  }

  it("N2-A: rejects too-short desc → 400, no spawn, no jobDir", () => {
    const root = makeTmpRoot();
    try {
      const res = handleStartDraft(root, { desc: "d" });
      assert.equal(res.status, 400);
      assert.match(res.error, /too short/i);
      assert.equal(spawnCount, 0, "spawner not invoked on validation failure");
      assert.equal(draftDirs(root).length, 0, "no jobDir created under _tmp/draft-*");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("N2-B: rejects placeholder desc → 400, no spawn", () => {
    const root = makeTmpRoot();
    try {
      const res = handleStartDraft(root, { desc: "test" });
      assert.equal(res.status, 400);
      assert.match(res.error, /placeholder/i);
      assert.equal(spawnCount, 0, "spawner not invoked on placeholder rejection");
      assert.equal(draftDirs(root).length, 0, "no jobDir created");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("N2-C: honors base.json draft.minDescLength override → 400, no spawn", () => {
    const root = makeTmpRoot();
    try {
      writeFileSync(
        join(root, "missions", "base.json"),
        JSON.stringify({ draft: { minDescLength: 8 } }),
      );
      // "add xy" has length 6 — passes default threshold (4) but < 8.
      const res = handleStartDraft(root, { desc: "add xy" });
      assert.equal(res.status, 400);
      assert.match(res.error, /too short/i);
      assert.equal(spawnCount, 0, "spawner not invoked when configured threshold rejects");
      assert.equal(draftDirs(root).length, 0, "no jobDir created");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("N2-D: valid desc proceeds → jobId/pid returned, spawner invoked once", () => {
    const root = makeTmpRoot();
    try {
      const res = handleStartDraft(root, { desc: "add audit count to dashboard" });
      assert.ok(res.jobId, "jobId returned");
      assert.ok(res.jobId.startsWith("draft-"), "jobId has draft- prefix");
      assert.equal(res.pid, 9900, "pid from fake spawner");
      assert.equal(spawnCount, 1, "spawner invoked exactly once for valid desc");
      assert.equal(draftDirs(root).length, 1, "jobDir created");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

// ── P6 Context Explorer: memory GET/PUT endpoints (Phase 3) ────────────────

describe("Monitor — memory file GET/PUT (P6 Phase 3 / FSD §3.5.2 panel 2)", () => {
  // PUT helper (the global fetchJson is GET-only).
  function putJson(urlstr, body) {
    return new Promise((resolve, reject) => {
      const u = new URL(urlstr);
      const payload = JSON.stringify(body);
      const req = http.request(
        {
          hostname: u.hostname,
          port: u.port,
          path: u.pathname + u.search,
          method: "PUT",
          headers: { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(payload) },
          agent: false,
        },
        (res) => {
          let data = "";
          res.on("data", (chunk) => (data += chunk));
          res.on("end", () => {
            try {
              resolve({ status: res.statusCode, body: JSON.parse(data) });
            } catch {
              resolve({ status: res.statusCode, body: data });
            }
          });
        }
      );
      req.on("error", reject);
      req.write(payload);
      req.end();
    });
  }

  it("GET /api/memory/self/_index.md returns the raw memory file", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/memory/self/_index.md`);
        assert.equal(res.status, 200);
        assert.ok(res.body.content.includes("Top rules") || res.body.content.length > 0);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("PUT then GET roundtrip writes content atomically (demo-mod store in tmpdir)", async () => {
    const root = makeTmpProject();
    // Fabricate an isolated demo-mod memory store under the tmp project root so the
    // PUT never touches the real docs/memory/demo-mod files.
    mkdirSync(join(root, "docs", "memory", "demo-mod"), { recursive: true });
    writeFileSync(join(root, "docs", "memory", "demo-mod", "_index.md"), "old content\n");
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const putRes = await putJson(`${baseUrl(monitor)}/api/memory/demo-mod/_index.md`, {
          content: "---\nlesson_count: 1\n---\n# new content\n",
        });
        assert.equal(putRes.status, 200);
        assert.equal(putRes.body.ok, true);
        // No running mission in this tmp project → no warning.
        assert.equal(putRes.body.warning, undefined);

        const getRes = await fetchJson(`${baseUrl(monitor)}/api/memory/demo-mod/_index.md`);
        assert.equal(getRes.status, 200);
        assert.ok(getRes.body.content.includes("new content"));
        // Original was overwritten (atomic rename), not appended.
        assert.ok(!getRes.body.content.includes("old content"));
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("PUT store=../../ rejects path traversal → 404 (unknown store)", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await putJson(`${baseUrl(monitor)}/api/memory/..%2F..%2F/_index.md`, {
          content: "x",
        });
        // The encoded slashes survive decodeURIComponent as literal chars, so
        // the store param is "../../" which is not in the whitelist → 404.
        assert.equal(res.status, 404);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET/PUT file=foo.md (non-whitelist) → 400", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const getRes = await fetchJson(`${baseUrl(monitor)}/api/memory/self/foo.md`);
        assert.equal(getRes.status, 400);
        assert.match(getRes.body.error, /whitelist/i);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("GET store=unknown → 404", async () => {
    const root = makeTmpProject();
    try {
      const monitor = await startMonitor({ projectRoot: root, port: 0, webDir: join(root, "web") });
      try {
        const res = await fetchJson(`${baseUrl(monitor)}/api/memory/unknown/_index.md`);
        assert.equal(res.status, 404);
        assert.match(res.body.error, /store/i);
      } finally {
        await monitor.close();
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});


