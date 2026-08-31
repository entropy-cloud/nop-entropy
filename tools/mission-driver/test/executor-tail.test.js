import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { tailLines, buildErrorTail, resolveTimeoutMs } from "../src/executor.js";

describe("executor.js — tailLines (FSD §4.3)", () => {
  let dir;

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), "md-tail-"));
  });

  afterEach(() => {
    try { rmSync(dir, { recursive: true, force: true }); } catch {}
  });

  it("returns last 3 non-comment, non-empty lines", () => {
    const f = join(dir, "child.log");
    const body = [
      "# cmd: node foo",
      "# cwd: /tmp",
      "# started: 2026-06-29T15:51:42",
      "",
      "[INFO] step 1",
      "[INFO] step 2",
      "[ERROR] Tests run: 173, Failures: 29",
      "Now I have the real root cause",
    ].join("\n") + "\n";
    writeFileSync(f, body, "utf8");
    const size = statSync(f).size;

    const out = tailLines(f, size);

    assert.deepEqual(out, [
      "[INFO] step 2",
      "[ERROR] Tests run: 173, Failures: 29",
      "Now I have the real root cause",
    ]);
  });

  it("respects max 3 lines when more available", () => {
    const f = join(dir, "child.log");
    const lines = ["# header"].concat(
      Array.from({ length: 10 }, (_, i) => `line ${i + 1}`)
    );
    writeFileSync(f, lines.join("\n") + "\n", "utf8");
    const size = statSync(f).size;

    const out = tailLines(f, size);

    assert.deepEqual(out, ["line 8", "line 9", "line 10"]);
  });

  it("filters # header comment lines", () => {
    const f = join(dir, "child.log");
    writeFileSync(f, "# cmd: hidden\ntail body only\n", "utf8");
    const size = statSync(f).size;

    const out = tailLines(f, size);

    assert.deepEqual(out, ["tail body only"]);
  });

  it("returns empty array when file does not exist", () => {
    const out = tailLines(join(dir, "missing.log"), 100);
    assert.deepEqual(out, []);
  });

  it("returns empty array when file contains only header comments", () => {
    const f = join(dir, "child.log");
    writeFileSync(f, "# cmd: x\n# cwd: y\n# started: z\n\n", "utf8");
    const size = statSync(f).size;
    const out = tailLines(f, size);
    assert.deepEqual(out, []);
  });

  it("handles files smaller than 2KB — returns all content lines", () => {
    const f = join(dir, "child.log");
    writeFileSync(f, "# h\nshort content\n", "utf8");
    const size = statSync(f).size;
    assert.ok(size < 2048, "precondition: file < 2KB");

    const out = tailLines(f, size);
    assert.deepEqual(out, ["short content"]);
  });

  it("handles large files — reads only tail window and discards truncated first line", () => {
    const f = join(dir, "child.log");
    // 构造 > 2KB 内容，每行可识别
    const header = "# cmd: big\n";
    const fillerLine = "filler-".repeat(20); // ~140 chars each
    const lines = [header].concat(
      Array.from({ length: 30 }, (_, i) => `${fillerLine} ${i + 1}`)
    );
    writeFileSync(f, lines.join("\n") + "\n", "utf8");
    const size = statSync(f).size;
    assert.ok(size > 2048, "precondition: file > 2KB");

    const out = tailLines(f, size);

    // 仅 3 行，全部来自尾部窗口
    assert.equal(out.length, 3);
    // 不应包含 header
    for (const l of out) {
      assert.ok(!l.startsWith("#"), `non-header line: ${l}`);
      assert.match(l, /filler- \d+$/);
    }
  });

  it("truncates combined output to ≤500 chars when lines are long", () => {
    const f = join(dir, "child.log");
    const longLine = "x".repeat(300);
    // 5 行 × 300 chars = 1500 chars, 远超 500
    const body = `# h\n${longLine} 1\n${longLine} 2\n${longLine} 3\n${longLine} 4\n${longLine} 5\n`;
    writeFileSync(f, body, "utf8");
    const size = statSync(f).size;

    const out = tailLines(f, size, 500);

    // 合计 ≤ 500 字符（含 join 分隔符）
    const totalChars = out.join("\n").length;
    assert.ok(totalChars <= 500, `expected ≤500 chars, got ${totalChars}`);
    // 至少 1 行返回
    assert.ok(out.length >= 1);
    // 返回的应是尾部内容
    for (const l of out) {
      assert.match(l, /^x+ \d+$/);
    }
  });

  it("currentSize 0 returns empty array (no content to read)", () => {
    const f = join(dir, "child.log");
    writeFileSync(f, "some content\n", "utf8");
    // currentSize=0 → readLen=0 → 无内容
    const out = tailLines(f, 0);
    assert.deepEqual(out, []);
  });
});

// mdr-1 Phase 1 — diagnosable errorTail from independently-captured stderr.
// Pure-function proof (no real spawn): simulates the empty-output / header-only
// / real-output branches that the child `close` handler delegates to
// buildErrorTail. Real spawns were flaky under the concurrent `node --test`
// worker pool (Windows STATUS_DLL_INIT_FAILED races), so the logic is extracted
// and tested deterministically — matching the existing executor.js test
// convention (tailLines / detectSuspendJump / emitSuspendEvent are all pure).
describe("buildErrorTail() — stderr synthesis (mdr-1 Phase 1)", () => {
  const HEADER_LOG = "# cmd: opencode run\n# cwd: /repo\n# started: 2026-07-02T11:00:00\n\n";

  it("empty output (header-only log) + non-zero exit → errorTail carries exit code + stderr tail", () => {
    const errorTail = buildErrorTail({
      logContent: HEADER_LOG,
      stderrTail: "fatal: ENOSPC no space left on device",
      exitCode: 2,
    });
    assert.ok(errorTail, "errorTail must be non-empty on empty-output crash");
    assert.match(errorTail, /exit=2/);
    assert.match(errorTail, /ENOSPC/);
  });

  it("stderr with 429 signature → tail preserved for Phase 2 signature match", () => {
    const errorTail = buildErrorTail({
      logContent: HEADER_LOG,
      stderrTail: "Error: 429 Too Many Requests, rate_limit exceeded",
      exitCode: 1,
    });
    assert.match(errorTail, /exit=1/);
    // The rate-limit signature must survive into errorTail so
    // isTransientProviderError can match it (Phase 2 reads stderrTail/errorTail).
    assert.match(errorTail, /429/);
    assert.match(errorTail, /rate_limit/i);
  });

  it("header-only log + no stderr → still synthesizes a diagnosable tail", () => {
    const errorTail = buildErrorTail({
      logContent: HEADER_LOG,
      stderrTail: "",
      exitCode: 137,
    });
    assert.match(errorTail, /exit=137/);
    assert.match(errorTail, /no stderr captured/);
  });

  it("log with real output → errorTail is the log tail, NOT header synthesis", () => {
    const log = HEADER_LOG + "[INFO] building...\nBUILD FAILURE: tests failed\n";
    const errorTail = buildErrorTail({
      logContent: log,
      stderrTail: "some stderr noise",
      exitCode: 1,
    });
    assert.match(errorTail, /BUILD FAILURE: tests failed/);
    assert.doesNotMatch(
      errorTail, /exit=1\]/,
      "must NOT synthesize header form when the log has real content",
    );
  });

  it("timeout always wins over log/stderr synthesis", () => {
    const errorTail = buildErrorTail({
      logContent: HEADER_LOG, stderrTail: "429 rate_limit", exitCode: null, timedOut: true, timeoutMin: 60,
    });
    assert.match(errorTail, /\[TIMEOUT\]/);
    assert.doesNotMatch(errorTail, /exit=/);
  });

  // dre-d7 Phase 1 — L010 residual: tag-absent timeout diagnostic. tag-present
  // timeouts are salvaged by engine.js resolvedOk (extractTag hits the marker
  // after kill) so they never reach buildErrorTail via !ok; this branch serves
  // the genuine no-output hang that previously surfaced as "cause unknown".
  it("timeout + logContent with resultTag → diagnostic shows tag present + log tail + stderr", () => {
    const log = HEADER_LOG + "[INFO] working...\n<AI_STEP_RESULT>pass</AI_STEP_RESULT>\n[INFO] done\n";
    const errorTail = buildErrorTail({
      logContent: log, stderrTail: "some stderr noise", exitCode: null, timedOut: true, timeoutMin: 60,
    });
    assert.match(errorTail, /\[TIMEOUT\] Process killed after 60min/);
    assert.match(errorTail, /resultTag <AI_STEP_RESULT> present: yes/);
    assert.match(errorTail, /last log lines:/);
    assert.match(errorTail, /AI_STEP_RESULT/);
    assert.match(errorTail, /stderr tail:/);
    assert.match(errorTail, /some stderr noise/);
  });

  it("timeout + logContent without tag + cert stderr signature → diagnostic shows tag absent + stderr", () => {
    const log = HEADER_LOG + "[INFO] fetching...\n[INFO] still working\n";
    const errorTail = buildErrorTail({
      logContent: log, stderrTail: "Error: certificate has expired (CERT_EXPIRED)", exitCode: null, timedOut: true, timeoutMin: 45,
    });
    assert.match(errorTail, /\[TIMEOUT\] Process killed after 45min/);
    assert.match(errorTail, /resultTag <AI_STEP_RESULT> present: no/);
    assert.match(errorTail, /last log lines:/);
    assert.match(errorTail, /still working/);
    assert.match(errorTail, /stderr tail:/);
    assert.match(errorTail, /CERT_EXPIRED/);
  });

  it("timeout + empty logContent + empty stderr → still returns a readable diagnostic (not 'cause unknown')", () => {
    const errorTail = buildErrorTail({
      logContent: "", stderrTail: "", exitCode: null, timedOut: true, timeoutMin: 60,
    });
    assert.match(errorTail, /\[TIMEOUT\] Process killed after 60min/);
    assert.match(errorTail, /resultTag <AI_STEP_RESULT> present: no/);
    // must NOT contain the old opaque "cause unknown"
    assert.doesNotMatch(errorTail, /cause unknown/i);
    // no log lines / no stderr → those sections simply omitted
    assert.doesNotMatch(errorTail, /last log lines:/);
    assert.doesNotMatch(errorTail, /stderr tail:/);
  });

  it("timeout + custom resultTag → detects the custom tag presence", () => {
    const log = HEADER_LOG + "<REVIEW_RESULT>clean</REVIEW_RESULT>\n";
    const errorTail = buildErrorTail({
      logContent: log, stderrTail: "", exitCode: null, timedOut: true, timeoutMin: 60, resultTag: "REVIEW_RESULT",
    });
    assert.match(errorTail, /resultTag <REVIEW_RESULT> present: yes/);
  });

  it("exit code defaults to -1 when not provided (header-only path)", () => {
    const errorTail = buildErrorTail({ logContent: HEADER_LOG, stderrTail: "boom" });
    assert.match(errorTail, /exit=-1/);
  });
});

// dre-d7 Phase 2 (G2) — per-step configurable timeout resolution. Pure function
// (no spawn): the deadline check lives inside a 5min setInterval in execute(),
// which makes a real-spawn timeout test impractical under the concurrent test
// pool. The override/default logic is extracted into resolveTimeoutMs and tested
// here; execute() consumes the resolved value for both the deadline and the
// buildErrorTail timeoutMin diagnostic.
describe("resolveTimeoutMs() — per-step timeout override (G2)", () => {
  const BASE = 60 * 60_000;

  it("positive finite timeoutMs overrides BASE_TIMEOUT_MS", () => {
    assert.equal(resolveTimeoutMs({ timeoutMs: 5_000 }), 5_000);
    assert.equal(resolveTimeoutMs({ timeoutMs: 1 }), 1);
    assert.equal(resolveTimeoutMs({ timeoutMs: 90 * 60_000 }), 90 * 60_000);
  });

  it("absent timeoutMs falls back to BASE_TIMEOUT_MS (backward compatible)", () => {
    assert.equal(resolveTimeoutMs({}), BASE);
    assert.equal(resolveTimeoutMs(), BASE);
  });

  it("zero, negative, and non-numeric timeoutMs fall back to BASE_TIMEOUT_MS", () => {
    assert.equal(resolveTimeoutMs({ timeoutMs: 0 }), BASE);
    assert.equal(resolveTimeoutMs({ timeoutMs: -100 }), BASE);
    assert.equal(resolveTimeoutMs({ timeoutMs: "5000" }), BASE);
    assert.equal(resolveTimeoutMs({ timeoutMs: NaN }), BASE);
    assert.equal(resolveTimeoutMs({ timeoutMs: Infinity }), BASE);
    assert.equal(resolveTimeoutMs({ timeoutMs: null }), BASE);
  });
});
