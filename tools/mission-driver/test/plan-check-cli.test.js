// O6 — `plan-check.mjs` standalone CLI cross-platform entry guard.
// Mirrors `mission-check-cli.test.js` (WI4) for the sibling check tool.
// Pins design §2.5 / §4.4: the standalone CLI must actually execute the
// inspector on Windows / macOS / Linux. Before the O6 fix the entry guard
//   `import.meta.url === `file://${process.argv[1]}``
// never compared equal on Windows (left side normalizes to `file:///C:/...`,
// right side concatenation produces invalid `file://C:\...`), so the CLI body
// silently no-op'd and `node plan-check.mjs <anything>` exited 0 without
// inspecting the plan.
//
// Five cases asserted here:
//   A. Bad plan path (nonexistent file) → exit 1, stderr mentions ENOENT /
//      read error. NOTE: this case catches the broken guard ON WINDOWS ONLY
//      (on POSIX the broken `` `file://${abs}` `` happens to equal
//      `import.meta.url`, so the guard fires and the body runs anyway). The
//      cross-platform regression anchor is Case E, not Case A.
//   B. Missing CLI arg (`node plan-check.mjs` with no positional) → exit 2 +
//      "Usage:" stderr (pins the `if (!file)` branch at `:146-149`).
//   C. Valid plan (all items checked + Closure evidence) → exit 0 + stdout
//      contains `"passed": true`.
//   D. Failing plan (unchecked items, no closure evidence) → exit 1 + stdout
//      contains `"passed": false` (pins the `inspectPlan` verdict path).
//   E. Cross-platform regression anchor: source-inspection of `plan-check.mjs`
//      asserting the guard uses `pathToFileURL(...)` and NOT the broken
//      template-string concatenation. Fires identically on Windows / macOS /
//      Linux because it inspects source text, not the runtime comparison.
//
// Note on CLI invocation: mirrors `mission-check-cli.test.js` —
// `spawnSync(process.execPath, [PLAN_CHECK, ...args], { encoding: "utf8" })`.
// Using `process.execPath` (not the literal `'node'`) avoids dev-machine Node
// version ambiguity.

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, writeFileSync, rmSync, readFileSync } from "node:fs";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { tmpdir } from "node:os";

const __dirname = dirname(fileURLToPath(import.meta.url));
const PLAN_CHECK = resolve(__dirname, "..", "src", "plan-check.mjs");

// Spawn the standalone plan-check CLI with the given args. Returns
// { code, stdout, stderr }. Mirrors the runCli helper in mission-check-cli.test.js.
function runCli(...args) {
  const res = spawnSync(process.execPath, [PLAN_CHECK, ...args], {
    encoding: "utf8",
    timeout: 15000,
  });
  return { code: res.status ?? 0, stdout: res.stdout || "", stderr: res.stderr || "" };
}

// ── Case A: bad plan path → exit 1 ──────────────────────────────────────────

describe("O6 plan-check CLI — Case A: nonexistent plan path exits 1", () => {
  it("plan-check.mjs <nonexistent.md> → exit 1 + stderr mentions ENOENT/read error", () => {
    // This is the auditor's exact repro. NOTE: on POSIX the broken guard
    // `` `file://${abs}` `` happens to equal `import.meta.url` for an absolute
    // script path, so this case only catches the broken guard ON WINDOWS. The
    // cross-platform regression anchor is Case E.
    const tmp = mkdtempSync(join(tmpdir(), "pc-cli-A-"));
    try {
      const missingPlan = join(tmp, "definitely-nonexistent-plan.md");

      const r = runCli(missingPlan);

      assert.notEqual(r.code, 0,
        "CLI must exit non-zero when the plan path does not exist");
      assert.equal(r.code, 1,
        `CLI must exit with code 1 (got ${r.code}); stderr: ${r.stderr}`);
      assert.match(r.stderr, /ENOENT|no such file or directory|readFile/i,
        "stderr must mention an ENOENT / read error (the CLI body ran readFileSync and threw)");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── Case B: missing CLI arg → exit 2 + Usage ────────────────────────────────

describe("O6 plan-check CLI — Case B: missing positional arg exits 2 with Usage", () => {
  it("plan-check.mjs (no positional) → exit 2 + stderr contains 'Usage:'", () => {
    // Pins the `if (!file)` branch at plan-check.mjs:146-149. This case also
    // depends on Phase 1 on Windows (the `if (!file)` branch lives inside the
    // guard block — if the guard never fires, the Usage branch is unreachable).
    const r = runCli();

    assert.equal(r.code, 2,
      `CLI must exit 2 when no plan path is given (got ${r.code}); stderr: ${r.stderr}`);
    assert.match(r.stderr, /Usage:/,
      "stderr must contain 'Usage:' (pins the no-arg branch)");
  });
});

// ── Case C: valid plan → exit 0 + stdout JSON `"passed": true` ──────────────

describe("O6 plan-check CLI — Case C: valid plan exits 0 with passing JSON", () => {
  it("completed plan with all items checked + Closure evidence → exit 0 + '\"passed\": true'", () => {
    const tmp = mkdtempSync(join(tmpdir(), "pc-cli-C-"));
    try {
      const planFile = join(tmp, "ok.md");
      writeFileSync(planFile, `# ok plan

> Plan Status: completed
> Last Reviewed: 2026-07-22

### Phase 1 - done

Status: completed

Exit Criteria:

- [x] task one
- [x] task two

## Closure

Status Note: shipped

Closure Audit Evidence:

- all tests green
- typecheck clean
`);

      const r = runCli(planFile);

      assert.equal(r.code, 0,
        `CLI must exit 0 for a valid completed plan (got ${r.code}); stderr: ${r.stderr}`);
      assert.match(r.stdout, /"passed":\s*true/,
        "stdout must contain '\"passed\": true' JSON marker printed by the CLI body");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── Case D: failing plan → exit 1 + stdout JSON `"passed": false` ───────────

describe("O6 plan-check CLI — Case D: failing plan exits 1 with failing JSON", () => {
  it("active plan with unchecked items + no closure evidence → exit 1 + '\"passed\": false'", () => {
    const tmp = mkdtempSync(join(tmpdir(), "pc-cli-D-"));
    try {
      const planFile = join(tmp, "failing.md");
      writeFileSync(planFile, `# failing plan

> Plan Status: active

### Phase 1 - build

Status: planned

Exit Criteria:

- [ ] task one
- [ ] task two
`);

      const r = runCli(planFile);

      assert.equal(r.code, 1,
        `CLI must exit 1 for a plan with unchecked items (got ${r.code}); stderr: ${r.stderr}`);
      assert.match(r.stdout, /"passed":\s*false/,
        "stdout must contain '\"passed\": false' JSON marker (pins the inspectPlan verdict path through the CLI)");
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── Case E: cross-platform regression anchor (source inspection) ────────────

describe("O6 plan-check CLI — Case E: guard uses pathToFileURL (cross-platform anchor)", () => {
  it("plan-check.mjs source uses `import.meta.url === pathToFileURL(...)` for the CLI guard", () => {
    // This is the cross-platform regression anchor: it inspects the SOURCE
    // TEXT of plan-check.mjs, so it fires identically on Windows / macOS /
    // Linux (unlike Case A, which only catches the broken guard on Windows).
    // The broken form `` `file://${process.argv[1]}` `` produces a match for
    // the negative regex and no match for the positive regex.
    const src = readFileSync(PLAN_CHECK, "utf8");

    assert.match(src, /import\.meta\.url\s*===\s*pathToFileURL\(/,
      "guard must use the pathToFileURL(...) form (mirrors mission-check.mjs:107, WI4 / design §2.5)");
    assert.doesNotMatch(src, /import\.meta\.url\s*===\s*`file:\/\/\$\{/,
      "guard must NOT use the broken template-string concatenation `file://${...}` (O6 defect class, design §2.5 缺陷 4)");
  });
});
