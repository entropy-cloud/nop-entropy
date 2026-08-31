import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { mkdtempSync, rmSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import {
  VAR_PROVENANCE,
  EXPECTED_VARS,
  extractVarsKeysFromMainJs,
  buildInjectionMap,
  listPrompts,
  listMemoryStores,
} from "../src/context-map.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const TOOL_ROOT = resolve(__dirname, "..");
// listMemoryStores scans {projectRoot}/docs/memory — that lives at the repo
// root, not under the tool dir. self memory resolves via an absolute constant
// inside context-map.mjs, so it works regardless of projectRoot.
const REPO_ROOT = resolve(TOOL_ROOT, "..", "..");
const MAIN_JS = resolve(TOOL_ROOT, "src", "main.js");

// The Context Explorer runs against the real tool layout (flows/, prompts/,
// memory/ live under the repo), so most assertions exercise the live files
// rather than a fabricated tmpdir. The buildInjectionMap/listPrompts calls
// pass TOOL_ROOT as projectRoot so the docs/memory/<MODULE> scan resolves.

describe("context-map — VAR_PROVENANCE drift hard-gate (FSD §7.4 residual risk #2)", () => {
  it("every EXPECTED_VARS key has a VAR_PROVENANCE entry", () => {
    const missing = EXPECTED_VARS.filter((k) => !VAR_PROVENANCE[k]);
    assert.deepEqual(missing, [], `EXPECTED_VARS not in VAR_PROVENANCE: ${missing.join(", ")}`);
  });

  it("every main.js delegates.vars key is registered in EXPECTED_VARS", () => {
    const extracted = extractVarsKeysFromMainJs(MAIN_JS);
    assert.ok(extracted.length >= 20, `extraction returned too few keys (${extracted.length}); regex may be broken`);
    const missing = extracted.filter((k) => !EXPECTED_VARS.includes(k));
    assert.deepEqual(missing, [], `main.js vars not in EXPECTED_VARS: ${missing.join(", ")}`);
  });

  it("PLAN_FILE is flagged runtime=true (forEach item, not statically resolvable)", () => {
    assert.equal(VAR_PROVENANCE.PLAN_FILE.runtime, true);
    // TIMESTAMP + runDir are the other two runtime vars.
    assert.equal(VAR_PROVENANCE.TIMESTAMP.runtime, true);
    assert.equal(VAR_PROVENANCE.runDir.runtime, true);
  });

  it("static vars are flagged runtime=false", () => {
    assert.equal(VAR_PROVENANCE.missionName.runtime, false);
    assert.equal(VAR_PROVENANCE.roadmapPath.runtime, false);
    assert.equal(VAR_PROVENANCE.selfMemoryIndex.runtime, false);
  });
});

describe("context-map — buildInjectionMap(mission-driver)", () => {
  const map = buildInjectionMap("mission-driver", TOOL_ROOT);

  it("returns the mission-driver flow with its steps", () => {
    assert.equal(map.flowName, "mission-driver");
    assert.equal(map.notFound, undefined);
    assert.ok(map.steps.length >= 4, `expected ≥4 steps, got ${map.steps.length}`);
  });

  it("CHECK step surfaces health-check.md with missionName + contextDir vars", () => {
    const check = map.steps.find((s) => s.name === "CHECK");
    assert.ok(check, "CHECK step present");
    assert.ok(check.promptPath.endsWith("health-check.md"));
    assert.equal(check.isEntry, true);
    const varNames = check.promptVars.map((v) => v.name);
    assert.ok(varNames.includes("missionName"), `CHECK vars: ${varNames.join(",")}`);
    assert.ok(varNames.includes("contextDir"), `CHECK vars: ${varNames.join(",")}`);
  });

  it("DRAFT_PLANS step surfaces draft-from-roadmap.md with roadmapPath var", () => {
    const draft = map.steps.find((s) => s.name === "DRAFT_PLANS");
    assert.ok(draft, "DRAFT_PLANS step present");
    assert.ok(draft.promptPath.endsWith("draft-from-roadmap.md"));
    const varNames = draft.promptVars.map((v) => v.name);
    assert.ok(varNames.includes("roadmapPath"), `DRAFT_PLANS vars: ${varNames.join(",")}`);
  });

  it("marks provenance source for known vars and (unknown) for contextual ones", () => {
    const check = map.steps.find((s) => s.name === "CHECK");
    const missionNameVar = check.promptVars.find((v) => v.name === "missionName");
    assert.ok(missionNameVar.source.includes("missionName"));
    assert.equal(missionNameVar.runtime, false);
  });
});

describe("context-map — listPrompts", () => {
  const { prompts } = listPrompts(TOOL_ROOT);
  it("returns the prompt library (≥12 prompts, includes execute)", () => {

    assert.ok(prompts.length >= 12, `expected ≥12 prompts, got ${prompts.length}`);
    const execute = prompts.find((p) => p.name === "execute");
    assert.ok(execute, "execute prompt present");
    assert.ok(execute.summary.length > 0, "execute has a summary");
  });

  it("execute prompt usedBy includes plan-execution flow EXECUTE step", () => {
    const execute = prompts.find((p) => p.name === "execute");
    assert.ok(execute, "execute prompt present");
    const used = execute.usedBy.find((u) => u.flow === "plan-execution" && u.step === "EXECUTE");
    assert.ok(used, `execute usedBy missing plan-execution/EXECUTE: ${JSON.stringify(execute.usedBy)}`);
  });

  it("every prompt exposes its {{var}} list", () => {
    const health = prompts.find((p) => p.name === "health-check");
    assert.ok(health.vars.includes("missionName"));
  });
});

describe("context-map — listMemoryStores", () => {
  it("returns the self store + dynamically-discovered module stores", () => {
    const root = mkdtempSync(join(tmpdir(), "md-mem-"));
    try {
      mkdirSync(join(root, "docs", "memory", "mod-a"), { recursive: true });
      writeFileSync(join(root, "docs", "memory", "mod-a", "_index.md"), "---\nlesson_count: 2\nupdated: 2026-07-14\n---\n# mod-a\n");
      const { stores } = listMemoryStores(root);
      const self = stores.find((s) => s.store === "self");
      assert.ok(self, "self store present");
      assert.ok(self.exists, "self store exists");
      assert.ok(self.files.find((f) => f.name === "_index.md"), "self store has _index.md");
      const modA = stores.find((s) => s.store === "mod-a");
      assert.ok(modA, "dynamically-discovered module store present");
      assert.ok(modA.exists, "module store exists");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("surfaces _index.md frontmatter lessonCount for the self store", () => {
    const { stores } = listMemoryStores(REPO_ROOT);
    const self = stores.find((s) => s.store === "self");
    assert.ok(self.indexSummary, "self store parsed frontmatter");
    assert.ok(typeof self.indexSummary.lessonCount === "number");
  });
});

describe("context-map — buildInjectionMap subflow recursion (fix #1)", () => {
  const map = buildInjectionMap("mission-driver", TOOL_ROOT);

  it("EXEC_PLANS is a subflow with plan-execution's steps nested", () => {
    const exec = map.steps.find((s) => s.name === "EXEC_PLANS");
    assert.ok(exec, "EXEC_PLANS step present");
    assert.equal(exec.type, "subflow");
    assert.equal(exec.subflowName, "plan-execution");
    assert.ok(exec.substeps && exec.substeps.length > 0, "EXEC_PLANS has nested substeps");
    // plan-execution's entry is EXECUTE; it should appear in the nested steps.
    const nestedExecute = exec.substeps.find((s) => s.name === "EXECUTE");
    assert.ok(nestedExecute, "nested plan-execution/EXECUTE visible");
    assert.ok(nestedExecute.promptPath.endsWith("execute.md"));
  });

  it("DEEP_AUDIT is a subflow with deep-audit-loop's steps nested", () => {
    const da = map.steps.find((s) => s.name === "DEEP_AUDIT");
    assert.ok(da, "DEEP_AUDIT step present");
    assert.equal(da.type, "subflow");
    assert.equal(da.subflowName, "deep-audit-loop");
    assert.ok(da.substeps && da.substeps.length > 0, "DEEP_AUDIT has nested substeps");
    const nested = da.substeps.find((s) => s.name === "MULTI_AUDIT");
    assert.ok(nested, "nested deep-audit-loop/MULTI_AUDIT visible");
  });

  it("nested substeps keep prompt vars (closure-audit surfaces PLAN_FILE)", () => {
    const exec = map.steps.find((s) => s.name === "EXEC_PLANS");
    const closure = exec.substeps.find((s) => s.name === "CLOSURE_AUDIT");
    assert.ok(closure, "nested CLOSURE_AUDIT visible");
    const varNames = closure.promptVars.map((v) => v.name);
    assert.ok(varNames.includes("PLAN_FILE"), `nested CLOSURE_AUDIT vars: ${varNames.join(",")}`);
  });

  it("non-subflow steps do not carry subflowName/substeps", () => {
    const check = map.steps.find((s) => s.name === "CHECK");
    assert.ok(check);
    assert.equal(check.subflowName, undefined);
    assert.equal(check.substeps, undefined);
  });

  it("returns top-level transition edges (state-machine) — fix #graph", () => {
    assert.ok(Array.isArray(map.edges), "map.edges is an array");
    assert.ok(map.edges.length > 0, `expected transitions, got ${map.edges.length}`);
    // CHECK pass → REVIEW_PLANS (happy-path goto edge).
    const checkPass = map.edges.find((e) => e.from === "CHECK" && e.marker === "pass");
    assert.ok(checkPass, "CHECK/pass edge present");
    assert.equal(checkPass.to, "REVIEW_PLANS");
    assert.equal(checkPass.terminal, undefined);
    // CHECK fail → done:failed (terminal edge with synthetic done: target).
    const checkFail = map.edges.find((e) => e.from === "CHECK" && e.marker === "fail");
    assert.ok(checkFail, "CHECK/fail edge present");
    assert.equal(checkFail.terminal, true);
    assert.equal(checkFail.to.startsWith("done:"), true);
    // EXEC_PLANS onError → DRAFT_PLANS (dashed error edge).
    const execErr = map.edges.find((e) => e.from === "EXEC_PLANS" && e.marker === "error");
    assert.ok(execErr, "EXEC_PLANS/onError edge present");
    assert.equal(execErr.dashed, true);
    assert.equal(execErr.to, "DRAFT_PLANS");
  });

  it("handles a missing subflow gracefully (subflowMissing flag)", () => {
    // Fabricate a flow JSON in a tmp project that references a nonexistent
    // subflow, and confirm the step is flagged rather than crashing.
    const tmpRoot = mkdtempSync(join(tmpdir(), "ctx-subflow-"));
    try {
      // flowSearchDirs looks in {projectRoot}/missions/flows then the tool dir.
      const flowsDir = join(tmpRoot, "missions", "flows");
      mkdirSync(flowsDir, { recursive: true });
      writeFileSync(
        join(flowsDir, "host.json"),
        JSON.stringify({
          name: "host",
          entry: "PARENT",
          steps: { PARENT: { type: "subflow", flow: "no-such-subflow" } },
        }),
      );
      const m = buildInjectionMap("host", tmpRoot);
      const parent = m.steps.find((s) => s.name === "PARENT");
      assert.ok(parent.subflowName === "no-such-subflow");
      assert.equal(parent.subflowMissing, true);
      assert.deepEqual(parent.substeps, []);
    } finally {
      rmSync(tmpRoot, { recursive: true, force: true });
    }
  });
});
