// pi driver support — config defaults + {agentFile} token + opencode-extras
// guard + findLatestSessionId pi guard.
// (a) resolveConfig applies pi defaults (driverArgs/promptMode/agentFile) when
//     driver=="pi", on all three return points (main / draft / analyze).
// (b) explicit driverArgs/promptMode override the pi defaults.
// (c) opencode regression: driver unset → driverArgs=undefined, promptMode="arg",
//     agentFile=undefined on ALL three return points (runner.js:27 `|| "stdin"`
//     must never silently trigger).
// (d) buildDriverArgs renders the pi template with {agentFile}/{model}, drops
//     --pure/--variant/--dangerously-skip-permissions, leaves no standalone '@'.
// (e) findLatestSessionId short-circuits (null) for driver=="pi".
import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { join, resolve, isAbsolute } from "node:path";
import { tmpdir } from "node:os";
import { resolveConfig } from "../src/config.js";
import { createRunner, findLatestSessionId } from "../src/runner.js";

function tmpRoot() {
  return mkdtempSync(join(tmpdir(), "pi-driver-"));
}

// Self-contained temp base + demo mission (mirrors skip-steps.test.js setup so
// loadMission path validation passes without depending on repo-root missions/).
function setupMission(root, body) {
  const missionsDir = join(root, "missions");
  mkdirSync(missionsDir, { recursive: true });
  writeFileSync(join(missionsDir, "base.json"), JSON.stringify({
    model: "test/model",
    agent: "build",
    maxCycles: 8,
    contextDir: "docs/context",
    moduleDir: "demo-mod",
    commands: { test: "echo ok" },
  }), "utf8");
  for (const d of ["docs/roadmap", "docs/plans/demo", "docs/context", "demo-mod"]) {
    mkdirSync(join(root, d), { recursive: true });
  }
  writeFileSync(join(missionsDir, "demo.json"), JSON.stringify({
    extends: "base",
    name: "demo",
    roadmapPath: "docs/roadmap",
    plansDir: "docs/plans/demo",
    commands: { test: "echo ok" },
    ...body,
  }), "utf8");
  return missionsDir;
}

// Capture spawn args via an injectable execute (no real subprocess).
function makeFakeExecute(calls, runDir) {
  return (config, label, cmd, args, opts) => {
    calls.push({ config, label, cmd, args, opts });
    const fakeLog = join(runDir, `fake-${label}-${calls.length}.log`);
    // empty body → extractSessionId returns null (we test buildDriverArgs, not session)
    writeFileSync(fakeLog, "");
    return Promise.resolve({ ok: true, logFile: fakeLog, pid: 1 });
  };
}

describe("pi driver — resolveConfig defaults (main branch)", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { rmSync(root, { recursive: true, force: true }); });

  it("driver=='pi' with no explicit driverArgs/promptMode → pi defaults + absolute agentFile", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({ dir: root, missionsDir, mission: "demo", driver: "pi" });
    assert.equal(cfg.driver, "pi");
    assert.ok(cfg.driverArgs.includes("-p --model {model}"), `driverArgs got: ${cfg.driverArgs}`);
    assert.ok(cfg.driverArgs.includes("--append-system-prompt @{agentFile}"), "must reference {agentFile}");
    assert.ok(cfg.driverArgs.includes("--tools read,write,edit,bash,grep,find,ls"), "must set tool allowlist");
    assert.equal(cfg.promptMode, "stdin");
    assert.ok(cfg.agentFile, "agentFile must be set for pi");
    assert.ok(isAbsolute(cfg.agentFile), "agentFile must be absolute");
    assert.ok(cfg.agentFile.endsWith(join("agents", "build.pi.md")), `agentFile: ${cfg.agentFile}`);
  });

  it("explicit driverArgs/promptMode override the pi defaults", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({
      dir: root, missionsDir, mission: "demo", driver: "pi",
      driverArgs: "custom {model}", promptMode: "arg",
    });
    assert.equal(cfg.driverArgs, "custom {model}");
    assert.equal(cfg.promptMode, "arg");
  });

  it("driver unset → opencode regression (driverArgs undefined, promptMode 'arg', agentFile undefined)", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({ dir: root, missionsDir, mission: "demo" });
    assert.equal(cfg.driver, "opencode");
    assert.equal(cfg.driverArgs, undefined, "opencode driverArgs must stay undefined");
    assert.equal(cfg.promptMode, "arg", "opencode promptMode must stay 'arg' (never undefined)");
    assert.equal(cfg.agentFile, undefined);
  });
});

describe("pi driver — resolveConfig defaults (draftMission branch)", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { rmSync(root, { recursive: true, force: true }); });

  it("driver=='pi' draft path → pi defaults", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({ dir: root, missionsDir, draftMission: "build something", driver: "pi" });
    assert.equal(cfg.driver, "pi");
    assert.equal(cfg.promptMode, "stdin");
    assert.ok(cfg.driverArgs.includes("{agentFile}"));
    assert.ok(isAbsolute(cfg.agentFile));
  });

  it("driver unset draft path → opencode regression (promptMode 'arg', never undefined)", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({ dir: root, missionsDir, draftMission: "build something" });
    assert.equal(cfg.driver, "opencode");
    assert.equal(cfg.driverArgs, undefined);
    assert.equal(cfg.promptMode, "arg");
    assert.equal(cfg.agentFile, undefined);
  });
});

describe("pi driver — resolveConfig defaults (analyzeRun branch)", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { rmSync(root, { recursive: true, force: true }); });

  it("driver=='pi' analyze path → pi defaults", () => {
    const missionsDir = setupMission(root);
    // analyzeRun resolves the newest _tmp/*-mission-driver/ run-state.json
    const runStateDir = join(root, "_tmp", "20260802-000000-mission-driver");
    mkdirSync(runStateDir, { recursive: true });
    writeFileSync(join(runStateDir, "run-state.json"), JSON.stringify({ missionName: "demo" }));
    const cfg = resolveConfig({ dir: root, missionsDir, analyzeRun: true, driver: "pi" });
    assert.equal(cfg.driver, "pi");
    assert.equal(cfg.promptMode, "stdin");
    assert.ok(cfg.driverArgs.includes("{agentFile}"));
    assert.ok(isAbsolute(cfg.agentFile));
  });

  it("driver unset analyze path → opencode regression (promptMode 'arg')", () => {
    const missionsDir = setupMission(root);
    const runStateDir = join(root, "_tmp", "20260802-000001-mission-driver");
    mkdirSync(runStateDir, { recursive: true });
    writeFileSync(join(runStateDir, "run-state.json"), JSON.stringify({ missionName: "demo" }));
    const cfg = resolveConfig({ dir: root, missionsDir, analyzeRun: true });
    assert.equal(cfg.driver, "opencode");
    assert.equal(cfg.driverArgs, undefined);
    assert.equal(cfg.promptMode, "arg");
    assert.equal(cfg.agentFile, undefined);
  });
});

describe("pi driver — buildDriverArgs rendering", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { rmSync(root, { recursive: true, force: true }); });

  it("pi config renders pi -p ... --append-system-prompt @<abs> --tools, no oc extras, no standalone @", async () => {
    const calls = [];
    const runner = await createRunner({
      dryRun: false,
      driver: "pi",
      model: "google/gemini-2.5-pro",
      agent: "build",
      agentFile: resolve(root, "fake-persona.md"),
      driverArgs: "-p --model {model} --append-system-prompt @{agentFile} --tools read,write,edit,bash,grep,find,ls",
      promptMode: "stdin",
      projectRoot: root,
      runDir: root,
    }, makeFakeExecute(calls, root));

    await runner.runAgent("CHECK", "do the check", "sys", null);

    assert.equal(calls.length, 1, "exactly one spawn");
    const { cmd, args, opts } = calls[0];
    assert.equal(cmd, "pi", "exe must be pi");
    assert.deepEqual(args, [
      "-p", "--model", "google/gemini-2.5-pro",
      "--append-system-prompt", "@" + resolve(root, "fake-persona.md"),
      "--tools", "read,write,edit,bash,grep,find,ls",
    ], `args: ${JSON.stringify(args)}`);
    // no opencode-specific extras
    assert.ok(!args.includes("--pure"), "must not pass --pure to pi");
    assert.ok(!args.includes("--variant"), "must not pass --variant to pi");
    assert.ok(!args.includes("--dangerously-skip-permissions"), "must not pass oc perm flag");
    assert.ok(!args.includes("@"), "no standalone '@' arg (agentFile substituted)");
    // stdin pipe used (promptMode stdin): opts.stdin carries the marked prompt
    assert.ok(typeof opts.stdin === "string" && opts.stdin.includes("do the check"), "stdin must carry the prompt");
  });

  it("opencode config still passes --dangerously-skip-permissions and uses arg mode (regression)", async () => {
    const calls = [];
    const runner = await createRunner({
      dryRun: false,
      driver: "opencode",
      model: "m",
      agent: "build",
      // driverArgs undefined → DEFAULT_DRIVER_ARGS (opencode template)
      projectRoot: root,
      runDir: root,
      promptMode: "arg",
    }, makeFakeExecute(calls, root));

    await runner.runAgent("CHECK", "short prompt", "sys", null);
    const { cmd, args, opts } = calls[0];
    assert.equal(cmd, "opencode");
    assert.ok(args.includes("--dangerously-skip-permissions"), "oc keeps its perm flag");
    assert.equal(opts.stdin, undefined, "oc arg mode must NOT pipe stdin");
    assert.ok(args.some(a => typeof a === "string" && a.includes("short prompt")), "oc arg mode appends prompt as positional");
  });
});

describe("pi driver — findLatestSessionId guard", () => {
  it("driver=='pi' → returns null without invoking opencode session list", () => {
    const root = tmpRoot();
    try {
      // Guard short-circuits before execSync; even if opencode is absent this
      // returns null deterministically (no subprocess, no throw).
      const sid = findLatestSessionId(root, "pi");
      assert.equal(sid, null);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});
