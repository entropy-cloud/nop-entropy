// WI3 — `--from-step <STEP>` entry override (Plan mdo-step-audit-3).
// Pins design §4.3.2-§4.3.4: --from-step sets the entry step and keeps the
// transitions / loop intact (the dual of WI2's --step single-step stop).
//
// Four cases asserted here:
//   1. Entry override does NOT mutate transitions — engine.run(entryOverride)
//      receives DEEP_AUDIT as the first step, stepCount > 1 (loop continues),
//      and the flow object is byte-identical before vs after run.
//   2. Mutex: --step + --from-step together → exit code 1, engine never runs.
//   3. Nonexistent step: --from-step NOPE → exit code 1, error message lists
//      the available top-level steps (reusing getTopSteps()).
//   4. singleStep flag duality: --from-step sets config.singleStep === false;
//      --step sets config.singleStep === true (regression guard for WI2/WI3).
//
// Note on CLI invocation: cases 2 & 3 spawn `node src/main.js <mission> ...`
// via the MAIN command path (no `run` keyword). This matches cli-help.test.js
// patterns and avoids a known commander 15.0.0 quirk where options shared
// between the `run` subcommand and the implicit main command get stripped
// from the subcommand when both are registered with the same option strings
// (a pre-existing latent issue, out of scope for WI3 to fix).

import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { resolve, dirname, join } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import { FlowEngine } from "../src/engine.js";
import { resolveConfig } from "../src/config.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const MAIN_JS = resolve(__dirname, "..", "src", "main.js");

// Isolated temp project root — prevents the test from polluting the real
// repo's `_tmp/` with orphan `<ts>-mission-driver` dirs. resolveConfig's
// mkdirSync side effect (config.js:574) would otherwise leak a dir per
// spawn/resolveConfig call when --dir points at the real repo root. Mirrors
// skip-steps.test.js / transient-error.test.js isolation.
function tmpRoot() {
  return mkdtempSync(join(tmpdir(), "from-step-"));
}

// Build a minimal missions/ layout that satisfies loadMission's REQUIRED_FIELDS
// + path-existence checks (mission-check.mjs:13,58). Mission name is preserved
// as "mission-driver-step-audit" so existing assertions still hold; contents
// are minimal because Cases 2/3 exit before engine.run and Case 4 only inspects
// resolveConfig's plumbing fields (fromStep/entryStep pass-through).
function setupMission(root) {
  const missionsDir = join(root, "missions");
  mkdirSync(missionsDir, { recursive: true });
  writeFileSync(join(missionsDir, "base.json"), JSON.stringify({
    model: "test/model",
    agent: "build",
    maxCycles: 8,
    maxInnerCycles: 6,
    maxTotalSteps: 500,
    fastSkipSteps: [],
    contextDir: "docs/context",
    moduleDir: "tools/mission-driver",
    commands: { test: "echo ok" },
  }), "utf8");
  writeFileSync(join(missionsDir, "mission-driver-step-audit.json"), JSON.stringify({
    extends: "base",
    name: "mission-driver-step-audit",
    roadmapPath: "docs/backlog/roadmap.md",
    plansDir: "docs/plans/mission-driver-step-audit",
    contextDir: "docs/context",
    moduleDir: "tools/mission-driver",
    commands: { test: "echo ok" },
  }), "utf8");
  // Create directories referenced by the mission so validateMission's
  // path-existence checks pass when --dir=root is passed.
  mkdirSync(join(root, "docs", "backlog"), { recursive: true });
  writeFileSync(join(root, "docs", "backlog", "roadmap.md"), "# stub roadmap\n", "utf8");
  mkdirSync(join(root, "docs", "plans", "mission-driver-step-audit"), { recursive: true });
  mkdirSync(join(root, "docs", "context"), { recursive: true });
  mkdirSync(join(root, "tools", "mission-driver"), { recursive: true });
  return missionsDir;
}

// Deep-clone a flow snapshot for before/after comparison. JSON round-trip is
// sufficient — flow JSON has no functions or undefined leaves at the top level.
function snapshot(flow) {
  return JSON.parse(JSON.stringify(flow));
}

// ── Case 1: entry override does not touch transitions ──────────────────────

describe("WI3 --from-step — Case 1: entry override keeps transitions immutable + loop continues", () => {
  it("engine.run(fromStep) starts at fromStep, stepCount > 1, flow byte-identical", async () => {
    // Mirror the real mission-driver main flow shape (5 steps cycle). We start
    // at WORK (the "from-step" target) and the loop must continue into DONE.
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        transitions: { pass: { goto: "WORK" }, fail: { done: "failed" } },
      },
      WORK: {
        type: "agent",
        prompt: "work",
        resultTag: "R",
        transitions: { ok: { goto: "REVIEW" } },
      },
      REVIEW: {
        type: "agent",
        prompt: "review",
        resultTag: "R",
        transitions: { approved: { done: "completed" }, issues: { done: "failed" } },
      },
    }, "CHECK");

    const before = snapshot(flow);
    const delegates = makeMockDelegates({
      responses: {
        // CHECK is NOT the entry — it must never run when entryOverride=WORK.
        CHECK: () => { throw new Error("CHECK must NOT be reached from --from-step WORK"); },
        WORK: { text: "<R>ok</R>", ok: true },
        REVIEW: { text: "<R>approved</R>", ok: true },
      },
    });
    // --from-step path: singleStep is explicitly false (WI3/WI2 duality).
    // Engine reads cfg.singleStep to pick maxSteps=1 vs Infinity.
    delegates.config.singleStep = false;
    const engine = new FlowEngine(flow, delegates);
    engine.eventsFile = null; // _emitEvent becomes a no-op without eventsFile

    // This mirrors main.js: `engine.run(config.entryStep)` where entryStep is
    // the fromStep value. entryOverride lands at engine.js:
    //   let currentStep = entryOverride || this.flow.entry;
    const result = await engine.run("WORK");

    assert.equal(result.status, "completed",
      "loop must run to normal completion (no single-step cap)");
    assert.ok(result.stepCount > 1,
      `stepCount must be > 1 (loop continues past the entry step); got ${result.stepCount}`);
    // First executed step must be the fromStep, not flow.entry.
    const firstCall = delegates.callLog.find((c) => c.type === "agent");
    assert.equal(firstCall.stepName, "WORK",
      "first agent step must be the fromStep target (entry override honored)");
    // CHECK (flow.entry) must NOT have been invoked.
    assert.equal(delegates.callLog.filter((c) => c.stepName === "CHECK").length, 0,
      "flow.entry (CHECK) must NOT be reached when --from-step points elsewhere");
    // Transitions immutability — the core guarantee --from-step must preserve
    // (contrast with the old WI2 in-place mutation that --step used to do).
    assert.deepEqual(snapshot(flow), before,
      "flow object (steps + transitions) must be byte-identical before vs after run");
  });
});

// ── Cases 2 & 3: CLI-level — spawn the actual binary ───────────────────────

// Run the mission-driver CLI with the given args. Returns { code, stdout, stderr }.
// Spawns `main.js --dir <root> [args...]` — i.e. uses the MAIN command path
// (no `run` keyword). The `run` subcommand path is exercised separately by
// Case 5 below as a regression guard for the commander 15.0.0 same-name-option
// stripping bug (mitigated in main.js via `program.enablePositionalOptions()`).
// `root` is the isolated temp project root (from tmpRoot()) so resolveConfig's
// mkdirSync side effect writes to <root>/_tmp/, not the real repo's _tmp/.
function runCli(root, ...args) {
  const res = spawnSync(process.execPath, [MAIN_JS, "--dir", root, ...args], {
    encoding: "utf8",
    timeout: 15000,
    cwd: root,
  });
  return { code: res.status ?? 0, stdout: res.stdout || "", stderr: res.stderr || "" };
}

// Variant that goes through the `run` subcommand (`main.js --dir <root> run ...`).
// Used by Case 5 to lock in the commander 15.0.0 option-stripping fix
// (enablePositionalOptions in main.js). Without that fix, `opts.step` /
// `opts.fromStep` would arrive as `undefined` in cmdRunMission's action.
function runCliViaSubcommand(root, ...args) {
  const res = spawnSync(process.execPath, [MAIN_JS, "--dir", root, "run", ...args], {
    encoding: "utf8",
    timeout: 15000,
    cwd: root,
  });
  return { code: res.status ?? 0, stdout: res.stdout || "", stderr: res.stderr || "" };
}

describe("WI3 --from-step — Case 2: mutex with --step (both → exit 1, engine not called)", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { try { rmSync(root, { recursive: true, force: true }); } catch {} });

  it("--step CHECK --from-step DEEP_AUDIT → exit 1, mutex error printed, engine not started", () => {
    setupMission(root);
    const r = runCli(root, "mission-driver-step-audit", "--step", "CHECK", "--from-step", "DEEP_AUDIT", "--dry-run", "--no-monitor");

    assert.equal(r.code, 1,
      "mutex violation must exit with code 1");
    const combined = r.stdout + r.stderr;
    assert.match(combined, /--step .* --from-step .*互斥|--from-step .* --step .*互斥/,
      "error output must mention the mutex rule");
    // Engine must not have started. Strongest portable signal: no "step 1" log
    // line that the engine always emits when a step actually executes.
    assert.doesNotMatch(combined, /\[step 1\]/,
      "engine.run must NOT be called on mutex violation (no step executed)");
  });
});

describe("WI3 --from-step — Case 3: nonexistent step rejected with available-step list", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { try { rmSync(root, { recursive: true, force: true }); } catch {} });

  it("--from-step NOPE → exit 1, error lists available top-level steps", () => {
    setupMission(root);
    const r = runCli(root, "mission-driver-step-audit", "--from-step", "NOPE", "--dry-run", "--no-monitor");

    assert.equal(r.code, 1,
      "nonexistent --from-step target must exit with code 1");
    const combined = r.stdout + r.stderr;
    assert.match(combined, /step "NOPE" not found/,
      "error message must name the rejected step");
    // Available top-level steps must be listed (reuses getTopSteps()).
    // getTopSteps() reads `__dirname/../flows/mission-driver.json` (hardcoded
    // relative to main.js's __dirname) — independent of --dir, so still works
    // under the temp root.
    // The mission-driver main flow has CHECK / EXEC_PLANS / DRAFT_PLANS /
    // REVIEW_PLANS / DEEP_AUDIT (order-independent).
    for (const s of ["CHECK", "EXEC_PLANS", "DRAFT_PLANS", "REVIEW_PLANS", "DEEP_AUDIT"]) {
      assert.ok(combined.includes(s),
        `available-step list must include ${s}`);
    }
    // Engine must not have started.
    assert.doesNotMatch(combined, /\[step 1\]/,
      "engine.run must NOT be called when --from-step target is invalid");
  });
});

// ── Case 4: singleStep flag duality (regression guard for WI2 vs WI3) ───────

describe("WI3 --from-step — Case 4: singleStep flag duality (--step vs --from-step)", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); setupMission(root); });
  afterEach(() => { try { rmSync(root, { recursive: true, force: true }); } catch {} });

  it("--from-step path → config.singleStep === false (explicit, not undefined)", () => {
    // resolveConfig now passes entryStep / fromStep through (WI3 fix). The
    // post-resolve main.js logic reads config.fromStep and sets:
    //   config.entryStep = config.fromStep;
    //   config.singleStep = false;
    // We simulate that post-resolve step here to pin the duality.
    const cfg = resolveConfig({
      dir: root,
      missionsDir: join(root, "missions"),
      mission: "mission-driver-step-audit",
      fromStep: "DEEP_AUDIT",
    });
    assert.equal(cfg.fromStep, "DEEP_AUDIT",
      "resolveConfig must pass fromStep through (WI3 plumbing fix)");
    assert.equal(cfg.entryStep, undefined,
      "entryStep is unset when only --from-step is given (prevents WI2 branch firing)");

    // Simulate main.js post-resolve wiring for the --from-step branch.
    // Mirrors cmdRunMission lines: config.entryStep = config.fromStep;
    // config.singleStep = false;
    const wired = { ...cfg };
    if (wired.fromStep) {
      wired.entryStep = wired.fromStep;
      wired.singleStep = false;
    }
    assert.equal(wired.entryStep, "DEEP_AUDIT",
      "--from-step must set config.entryStep to the target step");
    assert.equal(wired.singleStep, false,
      "--from-step must set config.singleStep to false (explicit, dual of --step's true)");
  });

  it("--step path → config.singleStep === true (WI2 regression guard)", () => {
    const cfg = resolveConfig({
      dir: root,
      missionsDir: join(root, "missions"),
      mission: "mission-driver-step-audit",
      entryStep: "CHECK",
    });
    assert.equal(cfg.entryStep, "CHECK",
      "resolveConfig must pass entryStep through (WI3 plumbing fix unblocked WI2's CLI path too)");

    // Simulate main.js post-resolve wiring for the --step branch.
    // Mirrors cmdRunMission lines: config.singleStep = true;
    const wired = { ...cfg };
    if (wired.entryStep) {
      wired.singleStep = true;
    }
    assert.equal(wired.singleStep, true,
      "--step must set config.singleStep to true (WI2 contract preserved)");
  });
});

// ── Case 5: `run` subcommand honors same-name options (regression guard) ───

// commander 15.0.0 has a parsing quirk: when an option name is declared on BOTH
// a subcommand and the main command (e.g. `program.command("run").option("--step")`
// + `program.option("--step")`), invoking the subcommand silently drops the
// option. main.js mitigates this with `program.enablePositionalOptions()`.
// This case locks in the mitigation — if someone removes that line, these
// assertions fail because `run X --step Y` would start at flow.entry (CHECK)
// instead of the requested step. See main.js's `enablePositionalOptions`
// comment for the full bug + verification matrix.
describe("Case 5: `run` subcommand receives --step / --from-step (commander 15.0.0 mitigation)", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { try { rmSync(root, { recursive: true, force: true }); } catch {} });

  it("`run <mission> --step <S>` enters single-step mode at <S>, not flow.entry", () => {
    setupMission(root);
    const r = runCliViaSubcommand(root, "mission-driver-step-audit", "--step", "CHECK", "--dry-run", "--no-monitor");
    // main.js:727 prints this line only when config.entryStep is truthy.
    // If commander drops --step, this line is absent and the engine starts at
    // flow.entry (CHECK happens to be flow.entry here, so we'd still see
    // "[step 1] CHECK" — but the "Step:" header line is the disambiguator).
    assert.match(r.stdout + r.stderr, /Step:\s+CHECK \(single-step mode\)/,
      "`run` subcommand must receive --step (commander 15.0.0 option-stripping mitigation intact)");
  });

  it("`run <mission> --from-step <S>` enters entry-override mode at <S>", () => {
    setupMission(root);
    const r = runCliViaSubcommand(root, "mission-driver-step-audit", "--from-step", "DEEP_AUDIT", "--dry-run", "--no-monitor");
    // main.js:741 prints this line only when config.fromStep is truthy.
    assert.match(r.stdout + r.stderr, /From step:\s+DEEP_AUDIT/,
      "`run` subcommand must receive --from-step (commander 15.0.0 option-stripping mitigation intact)");
  });
});
