// cline driver support — config defaults + {agentFile} content substitution +
// opencode-extras guard + findLatestSessionId cline guard.
// (a) resolveConfig applies cline defaults (driverArgs/promptMode/agentFile) when
//     driver=="cline", on all three return points (main / draft / analyze).
// (b) explicit driverArgs/promptMode override the cline defaults.
// (c) opencode regression: driver unset → driverArgs=undefined, promptMode="arg",
//     agentFile=undefined (runner.js:27 `|| "stdin"` must never trigger for
//     non-stdin drivers).
// (d) buildDriverArgs renders the cline template with {model}/{agentFile} →
//     persona CONTENT substituted (not the path), drops --pure/--variant/
//     --dangerously-skip-permissions, appends the prompt as a positional arg.
// (e) findLatestSessionId short-circuits (null) for driver=="cline" (and "pi").
import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { join, resolve, isAbsolute } from "node:path";
import { tmpdir } from "node:os";
import { resolveConfig } from "../src/config.js";
import { createRunner, findLatestSessionId } from "../src/runner.js";

function tmpRoot() {
  return mkdtempSync(join(tmpdir(), "cline-driver-"));
}

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

function makeFakeExecute(calls, runDir) {
  return (config, label, cmd, args, opts) => {
    calls.push({ config, label, cmd, args, opts });
    const fakeLog = join(runDir, `fake-${label}-${calls.length}.log`);
    writeFileSync(fakeLog, "");
    return Promise.resolve({ ok: true, logFile: fakeLog, pid: 1 });
  };
}

describe("cline driver — resolveConfig defaults (main branch)", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { rmSync(root, { recursive: true, force: true }); });

  it("driver=='cline' with no explicit driverArgs/promptMode → cline defaults + absolute agentFile", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({ dir: root, missionsDir, mission: "demo", driver: "cline" });
    assert.equal(cfg.driver, "cline");
    assert.ok(cfg.driverArgs.includes("-m {model}"), `driverArgs got: ${cfg.driverArgs}`);
    assert.ok(cfg.driverArgs.includes("--json"), "must use --json (parseable NDJSON)");
    assert.ok(cfg.driverArgs.includes("--yolo"), "must use --yolo (headless single-turn)");
    assert.ok(cfg.driverArgs.includes("--auto-approve true"), "must auto-approve tools");
    // persona (-s) is injected separately by the runner, not baked into the
    // whitespace-split template (content may contain newlines)
    assert.ok(!cfg.driverArgs.includes("-s"), "persona -s is injected post-split, not in template");
    assert.equal(cfg.promptMode, "arg", "cline prompt is a positional arg");
    assert.ok(cfg.agentFile, "agentFile must be set for cline");
    assert.ok(isAbsolute(cfg.agentFile), "agentFile must be absolute");
    assert.ok(cfg.agentFile.endsWith(join("agents", "build.cline.md")), `agentFile: ${cfg.agentFile}`);
  });

  it("explicit driverArgs/promptMode override the cline defaults", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({
      dir: root, missionsDir, mission: "demo", driver: "cline",
      driverArgs: "custom {model}", promptMode: "stdin",
    });
    assert.equal(cfg.driverArgs, "custom {model}");
    assert.equal(cfg.promptMode, "stdin");
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

describe("cline driver — resolveConfig defaults (draft + analyze branches)", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { rmSync(root, { recursive: true, force: true }); });

  it("driver=='cline' draft path → cline defaults (promptMode arg)", () => {
    const missionsDir = setupMission(root);
    const cfg = resolveConfig({ dir: root, missionsDir, draftMission: "build something", driver: "cline" });
    assert.equal(cfg.driver, "cline");
    assert.equal(cfg.promptMode, "arg");
    // cline persona is injected post-split as `-s <content>`, so the whitespace
    // template carries -m/--json but intentionally NOT {agentFile} / -s
    assert.ok(cfg.driverArgs.includes("-m {model}"), `driverArgs: ${cfg.driverArgs}`);
    assert.ok(cfg.driverArgs.includes("--json"), "cline must parse NDJSON");
    assert.ok(!cfg.driverArgs.includes("{agentFile}"), "persona injected separately, not in template");
    assert.ok(isAbsolute(cfg.agentFile), "agentFile must be absolute");
    assert.ok(cfg.agentFile.endsWith(join("agents", "build.cline.md")), `agentFile: ${cfg.agentFile}`);
  });

  it("driver=='cline' analyze path → cline defaults (promptMode arg)", () => {
    const missionsDir = setupMission(root);
    const runStateDir = join(root, "_tmp", "20260802-000002-mission-driver");
    mkdirSync(runStateDir, { recursive: true });
    writeFileSync(join(runStateDir, "run-state.json"), JSON.stringify({ missionName: "demo" }));
    const cfg = resolveConfig({ dir: root, missionsDir, analyzeRun: true, driver: "cline" });
    assert.equal(cfg.driver, "cline");
    assert.equal(cfg.promptMode, "arg");
    assert.ok(cfg.driverArgs.includes("-m {model}"), `driverArgs: ${cfg.driverArgs}`);
    assert.ok(cfg.driverArgs.includes("--json"), "cline must parse NDJSON");
    assert.ok(!cfg.driverArgs.includes("{agentFile}"), "persona injected separately, not in template");
    assert.ok(isAbsolute(cfg.agentFile), "agentFile must be absolute");
    assert.ok(cfg.agentFile.endsWith(join("agents", "build.cline.md")), `agentFile: ${cfg.agentFile}`);
  });
});

describe("cline driver — buildDriverArgs rendering", () => {
  let root;
  beforeEach(() => { root = tmpRoot(); });
  afterEach(() => { rmSync(root, { recursive: true, force: true }); });

  it("cline config renders -m --json --yolo --auto-approve, -s <persona content> pair, arg prompt, no oc extras", async () => {
    const calls = [];
    const personaPath = resolve(root, "build.cline.persona.md");
    writeFileSync(personaPath, "PERSONA_LINE_1\nPERSONA_LINE_2\n", "utf8");
    const runner = await createRunner({
      dryRun: false,
      driver: "cline",
      model: "deepseek/deepseek-v4-flash",
      agent: "build",
      agentFile: personaPath,
      driverArgs: "-m {model} --json --yolo --auto-approve true",
      promptMode: "arg",
      projectRoot: root,
      runDir: root,
      // extras that must NOT reach cline
      pure: true,
      variant: "nightly",
    }, makeFakeExecute(calls, root));

    await runner.runAgent("CHECK", "do the check", "sys", null);

    assert.equal(calls.length, 1, "exactly one spawn");
    const { cmd, args } = calls[0];
    assert.equal(cmd, "cline", "exe must be cline");
    // ordered expected shape (the runner prepends a [MISSION_DRIVER:<run>] tag
    // to the prompt, so compare the stable prefix and the trailing prompt)
    assert.deepEqual(args.slice(0, 8), [
      "-m", "deepseek/deepseek-v4-flash",
      "--json", "--yolo", "--auto-approve", "true",
      "-s", "PERSONA_LINE_1\nPERSONA_LINE_2",
    ], `args: ${JSON.stringify(args)}`);
    assert.ok(
      args[args.length - 1].endsWith("do the check"),
      `trailing prompt arg: ${JSON.stringify(args[args.length - 1])}`
    );
    // no opencode-specific extras leak into cline
    assert.ok(!args.includes("--pure"), "must not pass --pure to cline");
    assert.ok(!args.includes("--variant"), "must not pass --variant to cline");
    assert.ok(!args.includes("--dangerously-skip-permissions"), "must not pass oc perm flag");
    assert.ok(!args.includes("@"), "no standalone '@' arg");
    // arg mode: prompt appended as the last positional arg (with the runner's
    // [MISSION_DRIVER:<run>] tag prepended to it, so compare the tail)
    assert.ok(
      args[args.length - 1].endsWith("do the check"),
      `prompt is the trailing positional arg: ${JSON.stringify(args[args.length - 1])}`
    );
  });

  it("pi config still gets the @path (not content) — cline content path doesn't leak", async () => {
    const calls = [];
    const personaPath = resolve(root, "build.pi.persona.md");
    writeFileSync(personaPath, "PERSONA_CONTENT\n", "utf8");
    const runner = await createRunner({
      dryRun: false,
      driver: "pi",
      model: "m",
      agent: "build",
      agentFile: personaPath,
      driverArgs: "-p --model {model} --append-system-prompt @{agentFile} --tools read,write",
      promptMode: "stdin",
      projectRoot: root,
      runDir: root,
    }, makeFakeExecute(calls, root));

    await runner.runAgent("CHECK", "do", "sys", null);
    const { args } = calls[0];
    // pi keeps the @path form (its flag reads the file itself), not the content
    assert.ok(args.includes("@" + personaPath), `pi should get @path: ${JSON.stringify(args)}`);
    assert.ok(!args.includes("PERSONA_CONTENT"), "pi must not get file content inline");
  });
});

describe("cline driver — findLatestSessionId & session guard", () => {
  it("returns null for driver=='cline' (and 'pi'), without shelling out", () => {
    assert.equal(findLatestSessionId(process.cwd(), "cline"), null);
    assert.equal(findLatestSessionId(process.cwd(), "pi"), null);
  });
});

