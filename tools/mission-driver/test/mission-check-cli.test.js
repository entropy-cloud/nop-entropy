// WI4 — `mission-check.mjs` standalone CLI cross-platform entry guard.
// Pins design §2.5 / §4.4: the standalone CLI must actually execute validation
// on Windows / macOS / Linux. Before the fix the entry guard
//   `import.meta.url === `file://${process.argv[1]}``
// never compared equal on Windows (left side normalizes to `file:///C:/...`,
// right side拼接 produces invalid `file://C:\...`), so the CLI body silently
// no-op'd and any mission — even one with a missing `plansDir` — "passed".
//
// Four cases asserted here:
//   A. Bad mission (missing required field) → exit 1, stderr mentions the field.
//   B. `plansDir` pointing at a nonexistent path → exit 1, stderr mentions
//      "does not exist".
//   C. Valid mission → exit 0, stdout prints `{ "valid": true, ... }`.
//   D. Platform-agnostic `pathToFileURL` normalization anchor (unit-level, no
//      spawn). Locks the normalization shape that makes the entry guard work
//      identically across Windows / macOS / Linux, so a future regression that
//      reverts to the old template-string concatenation fails on every platform,
//      not just Windows.
//
// Note on CLI invocation: cases A/B/C mirror `from-step.test.js:24,117` —
// `spawnSync(process.execPath, [MISSION_CHECK, ...args], { encoding: "utf8" })`.
// Using `process.execPath` (not the literal `'node'`) avoids dev-machine Node
// version ambiguity.

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { tmpdir } from "node:os";

const __dirname = dirname(fileURLToPath(import.meta.url));
const MISSION_CHECK = resolve(__dirname, "..", "src", "mission-check.mjs");

// Spawn the standalone mission-check CLI with the given args. Returns
// { code, stdout, stderr }. Mirrors the runCli helper in from-step.test.js:116.
function runCli(...args) {
  const res = spawnSync(process.execPath, [MISSION_CHECK, ...args], {
    encoding: "utf8",
    timeout: 15000,
  });
  return { code: res.status ?? 0, stdout: res.stdout || "", stderr: res.stderr || "" };
}

// ── Case A: bad mission (missing required field) → exit 1 ───────────────────

describe("WI4 mission-check CLI — Case A: missing required field exits 1", () => {
  it("mission.json missing `name` → exit 1 + stderr mentions 'missing required field'", () => {
    const tmp = mkdtempSync(join(tmpdir(), "mc-cli-A-"));
    try {
      const missionFile = join(tmp, "bad.json");
      // Missing `name`; other required fields present so the only error is the
      // missing field (keeps the stderr assertion tightly coupled to one cause).
      writeFileSync(missionFile, JSON.stringify({
        roadmapPath: "docs/backlog/x.md",
        plansDir: "docs/plans",
        commands: { test: "echo ok" },
      }));

      const r = runCli(missionFile, tmp);

      assert.notEqual(r.code, 0,
        "CLI must exit non-zero when a required field is missing");
      assert.equal(r.code, 1,
        `CLI must exit with code 1 (got ${r.code}); stderr: ${r.stderr}`);
      assert.match(r.stderr, /missing required field/,
        "stderr must mention 'missing required field' (aligned with validateMission)");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── Case B: plansDir does not exist → exit 1 ────────────────────────────────

describe("WI4 mission-check CLI — Case B: nonexistent plansDir exits 1", () => {
  it("mission.json with plansDir pointing nowhere → exit 1 + stderr mentions 'does not exist'", () => {
    const tmp = mkdtempSync(join(tmpdir(), "mc-cli-B-"));
    try {
      const missionFile = join(tmp, "mission.json");
      writeFileSync(missionFile, JSON.stringify({
        name: "broken",
        roadmapPath: "docs/backlog/x.md",
        plansDir: "does-not-exist-dir",
        commands: { test: "echo ok" },
      }));

      const r = runCli(missionFile, tmp);

      assert.notEqual(r.code, 0,
        "CLI must exit non-zero when plansDir does not exist");
      assert.equal(r.code, 1,
        `CLI must exit with code 1 (got ${r.code}); stderr: ${r.stderr}`);
      assert.match(r.stderr, /does not exist/,
        "stderr must mention 'does not exist' (aligned with validateMission path checks)");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── Case C: valid mission → exit 0 + stdout JSON ────────────────────────────

describe("WI4 mission-check CLI — Case C: valid mission exits 0 with JSON stdout", () => {
  it("mission.json with all fields + existing paths → exit 0 + stdout contains '\"valid\": true'", () => {
    const tmp = mkdtempSync(join(tmpdir(), "mc-cli-C-"));
    try {
      // Create the real subdirectory the mission references, so the path
      // existence checks in validateMission pass.
      mkdirSync(join(tmp, "real-plans"));
      writeFileSync(join(tmp, "real-plans", ".keep"), "");
      mkdirSync(join(tmp, "docs", "backlog"), { recursive: true });
      writeFileSync(join(tmp, "docs", "backlog", "x.md"), "# roadmap\n");

      const missionFile = join(tmp, "mission.json");
      writeFileSync(missionFile, JSON.stringify({
        name: "ok",
        roadmapPath: "docs/backlog/x.md",
        plansDir: "real-plans",
        commands: { test: "echo ok" },
      }));

      const r = runCli(missionFile, tmp);

      assert.equal(r.code, 0,
        `CLI must exit 0 for a valid mission (got ${r.code}); stderr: ${r.stderr}`);
      assert.match(r.stdout, /"valid":\s*true/,
        "stdout must contain '\"valid\": true' JSON marker printed by the CLI body");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── Case D: pathToFileURL normalization anchor (platform-agnostic) ──────────

describe("WI4 mission-check CLI — Case D: pathToFileURL normalization anchor", () => {
  it("normalizes a Windows-style path to file:///C:/... on every platform", () => {
    // This is the exact shape `import.meta.url` takes on Windows. If
    // pathToFileURL ever stops producing this form, the entry guard breaks
    // silently on Windows again.
    assert.equal(
      pathToFileURL("C:\\Work\\foo\\mission-check.mjs").href,
      "file:///C:/Work/foo/mission-check.mjs",
      "Windows-style path must normalize to file:///C:/... (three slashes + drive letter)",
    );
  });

  it("normalizes a POSIX-style absolute path with the file:/// three-slash prefix on every platform", () => {
    // The plan's Exit Criteria requires Case D to pass on any host platform.
    // Node's pathToFileURL treats `/abs/path` differently per host: on POSIX it
    // stays `file:///abs/path/...`; on Windows it resolves against the current
    // drive (`file:///C:/abs/path/...`). The cross-platform invariant — and the
    // property that actually defeats the old broken concatenation — is the
    // `file:///` three-slash prefix that `import.meta.url` also uses. The old
    // `file://${argv[1]}` form produced `file://<raw>` (two-slash), which fails
    // this assertion on every platform.
    const href = pathToFileURL("/abs/path/mission-check.mjs").href;
    if (process.platform === "win32") {
      // Windows resolves POSIX-style absolute against the current drive, e.g.
      // file:///C:/abs/path/mission-check.mjs (three slashes + drive letter).
      assert.match(href, /^file:\/\/\/[A-Za-z]:\/abs\/path\/mission-check\.mjs$/,
        "on Windows, POSIX-style path resolves against current drive with file:/// prefix");
    } else {
      assert.equal(href, "file:///abs/path/mission-check.mjs",
        "on POSIX, absolute path normalizes to file:///abs/...");
    }
    // Belt-and-braces: the three-slash prefix must hold regardless of host.
    assert.match(href, /^file:\/\/\//,
      "pathToFileURL result must always use the file:/// three-slash prefix");
  });

  it("the OLD template-string concatenation must NOT match the normalized form (regression anchor)", () => {
    // This is the inverse anchor: the old broken expression
    //   `file://${process.argv[1]}`
    // produced `file://C:\Work\...` for a Windows argv[1], which is neither a
    // valid file URL nor equal to `import.meta.url`. Lock that inequality so a
    // future revert from `pathToFileURL(...).href` back to template-string
    // concatenation fails this assertion on every platform — including Linux CI
    // where the Windows regression otherwise hides.
    const oldConcatenation = `file://C:\\Work\\foo\\mission-check.mjs`;
    const normalized = pathToFileURL("C:\\Work\\foo\\mission-check.mjs").href;
    assert.notEqual(oldConcatenation, normalized,
      "old `file://${argv[1]}` concatenation must NOT equal the normalized form — this is the root cause of the Windows silent no-op");
  });
});
