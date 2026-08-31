import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates } from "./helpers.js";
import { createMissionDriverFlow } from "../src/flow-loader.js";
import { _scanOpenAuditsList, _isMissionLevelAudit } from "../src/flow-loader.js";
import { mkdtempSync, rmSync, readFileSync, writeFileSync, existsSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

// WI4 — DRAFT_PLANS audit-gate (Plan mdo-step-audit-4). Pins design §4.2.3 B-2
// and the §4.2.4 truth table (four rows) plus three boundary regressions:
//
//   Row 1 (activePlans non-empty, marker=created) → goto REVIEW_PLANS
//                                                      (gate does not engage)
//   Row 2 (auditRound < max, marker=nothing)        → goto DEEP_AUDIT
//                                                      (gate not yet eligible)
//   Row 3 (auditRound >= max, nothing, no plans,
//          no open audits)                          → run completed via gate
//   Row 4 (auditRound >= max, nothing,
//          open audits non-empty)                   → goto DEEP_AUDIT
//                                                      (issues must not be dropped)
//
// Boundary regressions:
//   E — flow without `auditEntry` → gate never engages (zero-intrusion guard).
//   F — legacy `done` marker after WI4 removal → onUnknown → goto DEEP_AUDIT
//       (AI cannot unilaterally complete the mission; legacy alias gone).
//
// The flow under test mirrors the real mission-driver shape: DRAFT_PLANS
// nothing → DEEP_AUDIT subflow → DRAFT_PLANS cycle. The DEEP_AUDIT step is
// mocked as a plain agent step so we can deterministically drive `auditRound`
// without spawning the deep-audit-loop subflow.

function gateFlow({ maxAuditRounds, auditEntry = "DEEP_AUDIT", withAuditEntry = true, pingPongWindow = 20 }) {
  const flow = {
    name: "draft-plans-gate-test",
    maxTotalSteps: 60,
    maxCycleVisits: 20,
    maxAuditRounds,
    pingPongWindow,
    entry: "DRAFT_PLANS",
    steps: {
      DRAFT_PLANS: {
        type: "agent",
        prompt: "draft",
        resultTag: "AI_STEP_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          created: { goto: "REVIEW_PLANS" },
          nothing: { goto: "DEEP_AUDIT" },
        },
        onError: { retry: "DRAFT_PLANS", maxRetries: 3 },
        onUnknown: { goto: "DEEP_AUDIT" },
        onMaxRetries: { done: "failed" },
      },
      REVIEW_PLANS: {
        type: "agent",
        prompt: "review",
        resultTag: "AI_STEP_RESULT",
        transitions: { approved: { done: "completed" } },
      },
      DEEP_AUDIT: {
        type: "agent",
        prompt: "deep-audit",
        resultTag: "AI_STEP_RESULT",
        transitions: { complete: { goto: "DRAFT_PLANS" } },
      },
    },
  };
  if (withAuditEntry) flow.auditEntry = auditEntry;
  return flow;
}

const NOTHING = { text: "<AI_STEP_RESULT>nothing</AI_STEP_RESULT>", ok: true };
const CREATED = { text: "<AI_STEP_RESULT>created</AI_STEP_RESULT>", ok: true };
const APPROVED = { text: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>", ok: true };
const AUDIT_COMPLETE = { text: "<AI_STEP_RESULT>complete</AI_STEP_RESULT>", ok: true };

// Read events.jsonl from runDir and return parsed event objects.
function readEvents(runDir) {
  const f = join(runDir, "events.jsonl");
  if (!existsSync(f)) return [];
  return readFileSync(f, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l));
}

describe("WI4 DRAFT_PLANS audit-gate (Plan mdo-step-audit-4)", () => {
  it("Case A (truth-table row 2): auditRound < max, nothing → goto DEEP_AUDIT; gate not yet eligible", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-gate-a-"));
    try {
      const flow = gateFlow({ maxAuditRounds: 3 });
      const delegates = makeMockDelegates({
        responses: {
          DRAFT_PLANS: NOTHING,
          DEEP_AUDIT: AUDIT_COMPLETE,
          REVIEW_PLANS: APPROVED,
        },
        config: { projectRoot: runDir, runDir },
        expressionFuncs: {
          activePlans: () => [],
          draftPlans: () => [],
          openAudits: () => [],
        },
      });
      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();

      // With maxAuditRounds=3 and always-nothing DRAFT_PLANS, the loop runs:
      //   D(nothing)→A(r1)→D→A(r2)→D→A(r3)→D→ [gate fires on 4th D nothing].
      // Case A asserts the FIRST D nothing does NOT short-circuit (it goes to
      // DEEP_AUDIT). The gate eventually fires — that is Case B below; here we
      // only verify that the first nothing transitioned normally to DEEP_AUDIT.
      const events = readEvents(runDir);
      const firstNothingTransition = events.find(
        (e) => e.type === "transition" && e.from === "DRAFT_PLANS" && e.marker === "nothing",
      );
      assert.ok(firstNothingTransition, "first DRAFT_PLANS nothing must produce a transition event");
      assert.equal(firstNothingTransition.to, "DEEP_AUDIT",
        "first nothing (auditRound < max) must goto DEEP_AUDIT, not be short-circuited");
      assert.notEqual(firstNothingTransition.via, "audit_gate",
        "first nothing must NOT be short-circuited by the audit-gate");
      // Sanity: the run must eventually finish (gate fires or step budget runs out).
      assert.ok(["completed", "max_total_steps", "max_cycles"].includes(result.status),
        `unexpected terminal status: ${result.status}`);
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("Case B (mdc-1 clean short-circuit): auditRound >= 1, nothing, no plans/audits → completed via gate BEFORE maxAuditRounds", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-gate-b-"));
    try {
      const flow = gateFlow({ maxAuditRounds: 3 });
      const delegates = makeMockDelegates({
        responses: {
          DRAFT_PLANS: NOTHING,
          DEEP_AUDIT: AUDIT_COMPLETE,
        },
        config: { projectRoot: runDir, runDir },
        expressionFuncs: {
          activePlans: () => [],
          draftPlans: () => [],
          openAudits: () => [],
        },
      });
      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();

      // Trace (maxAuditRounds=3, auditEntry=DEEP_AUDIT) — mdc-1 semantics:
      //   step1 D(nothing) → gate sees round=0 < 1 → goto A (audit at least once)
      //   step2 A(r1) → goto D
      //   step3 D(nothing) → gate sees round=1 >= 1 && clean → completed via gate
      // The run must NOT burn the remaining 2 audit rounds once the tree is clean.
      assert.equal(result.status, "completed",
        "run must complete via audit-gate once audited >=1 round with no plans/audits remaining");

      const events = readEvents(runDir);
      const gateEvents = events.filter((e) => e.type === "transition" && e.via === "audit_gate");
      assert.equal(gateEvents.length, 1,
        "exactly one audit_gate transition event must be emitted (the gate-fired completion)");
      const ge = gateEvents[0];
      assert.equal(ge.from, "DRAFT_PLANS");
      assert.equal(ge.marker, "nothing");
      assert.equal(ge.to, null, "gate transition has no destination (mission completes)");

      // run-state.json must reflect the EARLY exit: auditRound === 1, not 3.
      const state = JSON.parse(readFileSync(join(runDir, "run-state.json"), "utf8"));
      assert.equal(state.auditRound, 1,
        "clean short-circuit must complete after the FIRST audit round, not run to maxAuditRounds");
      assert.equal(state.maxAuditRounds, 3);
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("Case C (truth-table row 4): auditRound === max, but openAudits non-empty → still goto DEEP_AUDIT", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-gate-c-"));
    try {
      const flow = gateFlow({ maxAuditRounds: 1 });
      const delegates = makeMockDelegates({
        responses: {
          DRAFT_PLANS: NOTHING,
          DEEP_AUDIT: AUDIT_COMPLETE,
        },
        config: { projectRoot: runDir, runDir },
        expressionFuncs: {
          activePlans: () => [],
          draftPlans: () => [],
          // Non-empty open audits — gate must NOT short-circuit even when quota is exhausted.
          openAudits: () => [join(runDir, "fake-open-audit.md")],
        },
      });
      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();

      // With maxAuditRounds=1, the WI1 maxAuditRounds gate at DEEP_AUDIT entry
      // terminates the run after the FIRST completed audit round (when about to
      // enter audit round 2). But the WI4 audit-gate (which fires when LEAVING
      // DRAFT_PLANS toward DEEP_AUDIT) must NOT have fired before that — i.e.
      // every DRAFT_PLANS nothing transitioned normally to DEEP_AUDIT, never to
      // "audit_gate".
      const events = readEvents(runDir);
      const gateEvents = events.filter((e) => e.type === "transition" && e.via === "audit_gate");
      assert.equal(gateEvents.length, 0,
        "audit-gate must NOT fire when openAudits is non-empty, even if quota is exhausted");

      const draftTransitions = events.filter(
        (e) => e.type === "transition" && e.from === "DRAFT_PLANS" && e.marker === "nothing",
      );
      assert.ok(draftTransitions.length >= 1,
        "DRAFT_PLANS nothing transitions must still occur (going to DEEP_AUDIT, not gated)");
      for (const t of draftTransitions) {
        assert.equal(t.to, "DEEP_AUDIT",
          "each DRAFT_PLANS nothing must goto DEEP_AUDIT when openAudits is non-empty");
      }
      // The WI1 maxAuditRounds gate at DEEP_AUDIT entry is the eventual completion
      // mechanism here; the WI4 audit-gate at DRAFT_PLANS never short-circuits.
      assert.equal(result.status, "completed",
        "run still completes via the WI1 DEEP_AUDIT entry gate (maxAuditRounds cap), not the WI4 DRAFT_PLANS gate");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("Case D (truth-table row 1): marker=created → goto REVIEW_PLANS (gate does not engage)", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-gate-d-"));
    try {
      const flow = gateFlow({ maxAuditRounds: 1 });
      const delegates = makeMockDelegates({
        responses: {
          DRAFT_PLANS: CREATED,
          REVIEW_PLANS: APPROVED,
          DEEP_AUDIT: AUDIT_COMPLETE,
        },
        config: { projectRoot: runDir, runDir },
        expressionFuncs: {
          // activePlans non-empty — row 1 of the truth table.
          activePlans: () => [join(runDir, "fake-active-plan.md")],
          draftPlans: () => [],
          openAudits: () => [],
        },
      });
      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();

      assert.equal(result.status, "completed",
        "run completes via REVIEW_PLANS approved → done:completed");

      const events = readEvents(runDir);
      const createdTransition = events.find(
        (e) => e.type === "transition" && e.from === "DRAFT_PLANS" && e.marker === "created",
      );
      assert.ok(createdTransition, "DRAFT_PLANS created must produce a transition event");
      assert.equal(createdTransition.to, "REVIEW_PLANS",
        "created must goto REVIEW_PLANS, never be intercepted by the gate");
      assert.notEqual(createdTransition.via, "audit_gate");

      const gateEvents = events.filter((e) => e.type === "transition" && e.via === "audit_gate");
      assert.equal(gateEvents.length, 0,
        "audit-gate must never fire on marker=created (gate is nothing-only)");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("Case E (zero-intrusion): flow without auditEntry → gate never engages, behavior identical to pre-WI4", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-gate-e-"));
    try {
      const flow = gateFlow({ maxAuditRounds: 1, withAuditEntry: false });
      // Bump maxCycleVisits-based termination is not deterministic enough here;
      // instead we make REVIEW_PLANS terminate the run after one cycle by having
      // DRAFT_PLANS first emit nothing (→ DEEP_AUDIT → DRAFT_PLANS), then on the
      // second DRAFT_PLANS visit emit created (→ REVIEW_PLANS → completed).
      let draftCallCount = 0;
      const delegates = makeMockDelegates({
        responses: {
          DRAFT_PLANS: () => {
            draftCallCount++;
            return draftCallCount === 1
              ? NOTHING
              : { text: "<AI_STEP_RESULT>created</AI_STEP_RESULT>", ok: true };
          },
          REVIEW_PLANS: APPROVED,
          DEEP_AUDIT: AUDIT_COMPLETE,
        },
        config: { projectRoot: runDir, runDir },
        expressionFuncs: {
          activePlans: () => [],
          draftPlans: () => [],
          openAudits: () => [],
        },
      });
      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();

      // Without auditEntry, the WI4 audit-gate is bypassed entirely. The flow
      // must behave as before WI4: DRAFT_PLANS nothing → DEEP_AUDIT (regardless
      // of auditRound), and on the next cycle DRAFT_PLANS created → REVIEW_PLANS
      // → done:completed.
      assert.equal(result.status, "completed");

      const events = readEvents(runDir);
      const gateEvents = events.filter((e) => e.type === "transition" && e.via === "audit_gate");
      assert.equal(gateEvents.length, 0,
        "audit-gate must NEVER fire when flow has no auditEntry (zero-intrusion guarantee)");

      // Verify _shouldCompleteOnAuditQuota directly returns false without auditEntry.
      const engine2 = new FlowEngine(flow, delegates);
      assert.equal(engine2._shouldCompleteOnAuditQuota("DRAFT_PLANS", "nothing", { goto: "DEEP_AUDIT" }), false,
        "_shouldCompleteOnAuditQuota must return false when flow has no auditEntry");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("Case F (legacy `done` marker): markerAliases[done] removed, transitions[done] removed → onUnknown → goto DEEP_AUDIT", async () => {
    const runDir = mkdtempSync(join(tmpdir(), "md-gate-f-"));
    try {
      // First, structural verification against the real mission-driver.json
      // loaded through the production flow loader (also serves as the Phase 1
      // "createMissionDriverFlow loads without error" check from Phase 1's
      // exit criteria).
      const realFlow = createMissionDriverFlow({ flowName: "mission-driver" });
      assert.ok(!("done" in (realFlow.markerAliases || {})),
        "Phase 1: markerAliases must no longer contain the 'done' alias");
      assert.ok(!("done" in (realFlow.steps.DRAFT_PLANS.transitions || {})),
        "Phase 1: DRAFT_PLANS.transitions must no longer contain a 'done' exit");

      // Behavioral verification: use a simplified flow that mirrors the WI4
      // DRAFT_PLANS shape (no plan-execution subflow / forEach) so we can drive
      // the marker deterministically without hitting maxCycleVisits from the
      // un-mocked subflows in the real flow.
      const flow = gateFlow({ maxAuditRounds: 3 });
      let draftCallCount = 0;
      const delegates = makeMockDelegates({
        responses: {
          // First visit emits the legacy `done` marker (simulating an AI that
          // has not internalized the WI4 prompt change); second visit emits
          // `created` so the run can complete via REVIEW_PLANS → done:completed.
          DRAFT_PLANS: () => {
            draftCallCount++;
            return draftCallCount === 1
              ? { text: "<AI_STEP_RESULT>done</AI_STEP_RESULT>", ok: true }
              : CREATED;
          },
          REVIEW_PLANS: APPROVED,
          DEEP_AUDIT: AUDIT_COMPLETE,
        },
        config: { projectRoot: runDir, runDir },
        expressionFuncs: {
          activePlans: () => [],
          draftPlans: () => [],
          openAudits: () => [],
        },
      });
      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();

      // The legacy `done` marker must NOT complete the mission unilaterally.
      // With markerAliases[done] removed and transitions[done] removed, the
      // engine falls through to onUnknown → goto DEEP_AUDIT (WI4 design §5.4).
      assert.equal(result.status, "completed",
        "run still completes eventually (second DRAFT_PLANS created → REVIEW_PLANS → done:completed)");

      const events = readEvents(runDir);
      const gateEvents = events.filter((e) => e.type === "transition" && e.via === "audit_gate");
      assert.equal(gateEvents.length, 0,
        "audit-gate must NOT fire on the legacy done marker — onUnknown takes it to DEEP_AUDIT instead");

      // The onUnknown.goto path does not emit a transition event; verify
      // behavior via callLog instead: the agent call sequence must be
      //   DRAFT_PLANS(legacy done) → DEEP_AUDIT → DRAFT_PLANS(created) → REVIEW_PLANS
      // i.e. the legacy `done` marker must be followed by a DEEP_AUDIT call,
      // NOT by a terminal return (the AI cannot unilaterally complete).
      const agentCalls = delegates.callLog
        .filter((c) => c.type === "agent")
        .map((c) => c.stepName);
      const firstDraftIdx = agentCalls.indexOf("DRAFT_PLANS");
      assert.ok(firstDraftIdx !== -1, "DRAFT_PLANS must be invoked at least once");
      assert.ok(firstDraftIdx + 1 < agentCalls.length,
        "DRAFT_PLANS(legacy done) must be followed by another step (not terminal)");
      assert.equal(agentCalls[firstDraftIdx + 1], "DEEP_AUDIT",
        "legacy `done` marker must route to DEEP_AUDIT via onUnknown — the AI cannot complete unilaterally");

      // No `done: "completed"` terminal event sourced from the legacy marker.
      const runCompleted = events.find((e) => e.type === "run_completed");
      assert.ok(runCompleted, "run_completed event must exist");
      assert.equal(runCompleted.status, "completed");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });

  it("Case G (NF1 residual / O2): real mission-driver flow pins all 3 DEEP_AUDIT edges — complete→REVIEW_PLANS regression fix + failed/onError→DRAFT_PLANS siblings", () => {
    // Structural assertion against the REAL flow (not gateFlow()). The gateFlow()
    // helper deliberately routes DEEP_AUDIT complete → DRAFT_PLANS (the OPPOSITE
    // of the real flow) as a simplified fixture for the audit-gate loop; mirroring
    // it here would assert the wrong destination. The real flow is the correct pin.
    //
    // This test exists because the 0755 multi-audit NF1 found no test asserted the
    // DEEP_AUDIT → complete destination specifically. The edge is an intentional
    // regression fix (open-audit O2): 2026-07-14 commit 0c763f0 inadvertently
    // reverted complete → DRAFT_PLANS, which caused DRAFT_PLANS (which only reads
    // the roadmap) to ignore active plans the audit created → nothing → DEEP_AUDIT
    // spin; 2026-07-21 fixed it back. See
    // design/step-execution-and-audit-count-design.md:43 for the full rationale.
    // Pinning all three edges prevents a silent regression in any direction.
    const realFlow = createMissionDriverFlow({ flowName: "mission-driver" });
    const deepAudit = realFlow.steps.DEEP_AUDIT;

    assert.ok(deepAudit, "DEEP_AUDIT step must exist in the real mission-driver flow");
    assert.equal(deepAudit.type, "subflow",
      "DEEP_AUDIT must be a subflow step");
    assert.equal(deepAudit.flow, "deep-audit-loop",
      "DEEP_AUDIT must invoke the deep-audit-loop subflow");

    // The regression-fix edge: complete MUST go to REVIEW_PLANS (not DRAFT_PLANS).
    // Reverting this reintroduces the nothing → DEEP_AUDIT spin bug.
    assert.ok(deepAudit.transitions && deepAudit.transitions.complete,
      "DEEP_AUDIT.transitions.complete must exist");
    assert.equal(deepAudit.transitions.complete.goto, "REVIEW_PLANS",
      "DEEP_AUDIT complete → REVIEW_PLANS (intentional regression fix, design/step-execution-and-audit-count-design.md:43; do NOT revert to DRAFT_PLANS)");

    // Sibling edges: failed and onError route back to DRAFT_PLANS for re-drafting.
    assert.ok(deepAudit.transitions.failed,
      "DEEP_AUDIT.transitions.failed must exist");
    assert.equal(deepAudit.transitions.failed.goto, "DRAFT_PLANS",
      "DEEP_AUDIT failed → DRAFT_PLANS (re-draft after audit failure)");
    assert.ok(deepAudit.onError,
      "DEEP_AUDIT.onError must exist");
    assert.equal(deepAudit.onError.goto, "DRAFT_PLANS",
      "DEEP_AUDIT onError → DRAFT_PLANS (re-draft after subflow error)");
  });
});

// WI4 Phase 5 — audit-type filter on `_scanOpenAuditsList` (design §5.4).
// Verifies the helper logic that classifies audit files as mission-level vs
// plan-level, plus an end-to-end scan that mixes both kinds in one dir.
describe("WI4 Phase 5 — _scanOpenAuditsList audit-type filter", () => {
  it("classifies files by `> Audit Type:` header (mission vs plan)", () => {
    // Header wins over filename. Mission-level types include but are not
    // limited to the two declared by the deep-audit-loop prompts.
    const cases = [
      // [filename, content, expectedMissionLevel]
      ["2026-07-20-multi-audit-x.md", "> Audit Status: open\n> Audit Type: multi-dimensional\n", true],
      ["2026-07-20-open-audit-x.md", "> Audit Status: open\n> Audit Type: open-ended\n", true],
      ["2026-07-20-future-audit.md", "> Audit Status: open\n> Audit Type: security\n", true],
      ["2026-07-20-closure-audit-x.md", "> Audit Status: open\n> Audit Type: closure\n", false],
      ["2026-07-20-plan-audit-x.md", "> Audit Status: open\n> Audit Type: plan\n", false],
    ];
    for (const [name, content, expected] of cases) {
      assert.equal(
        _isMissionLevelAudit(name, content),
        expected,
        `classification wrong for ${name}`,
      );
    }
  });

  it("falls back to filename pattern when `> Audit Type:` header is missing", () => {
    // Legacy / convention-violating audit files without the header fall back
    // to a filename heuristic. Files with neither signal default to mission-
    // level (include) so we never silently drop an open audit.
    const cases = [
      // [filename, content, expectedMissionLevel]
      ["2026-07-20-multi-audit-x.md", "> Audit Status: open\n", true],
      ["2026-07-20-open-audit-x.md", "> Audit Status: open\n", true],
      ["2026-07-20-closure-audit-x.md", "> Audit Status: open\n", false],
      ["2026-07-20-plan-audit-x.md", "> Audit Status: open\n", false],
      ["2026-07-20-legacy-audit.md", "> Audit Status: open\n", true],
    ];
    for (const [name, content, expected] of cases) {
      assert.equal(
        _isMissionLevelAudit(name, content),
        expected,
        `fallback classification wrong for ${name}`,
      );
    }
  });

  it("end-to-end: _scanOpenAuditsList counts only mission-level open audits", () => {
    const dir = mkdtempSync(join(tmpdir(), "md-audit-filter-"));
    try {
      // Mission-level open audits (counted)
      writeFileSync(join(dir, "2026-07-20-1000-multi-audit-a.md"),
        "> Audit Status: open\n> Audit Type: multi-dimensional\n");
      writeFileSync(join(dir, "2026-07-20-1000-open-audit-b.md"),
        "> Audit Status: open\n> Audit Type: open-ended\n");
      // Plan-level closure audit (filtered out)
      writeFileSync(join(dir, "2026-07-20-1000-closure-audit-c.md"),
        "> Audit Status: open\n> Audit Type: closure\n");
      // Mission-level audit already closed (NOT counted — status filter)
      writeFileSync(join(dir, "2026-07-20-1000-multi-audit-d.md"),
        "> Audit Status: closed\n> Audit Type: multi-dimensional\n");
      // Plan-level closure audit closed (NOT counted)
      writeFileSync(join(dir, "2026-07-20-1000-closure-audit-e.md"),
        "> Audit Status: closed\n> Audit Type: closure\n");

      const result = _scanOpenAuditsList(dir);
      assert.equal(result.length, 2,
        "only the 2 mission-level OPEN audits must be counted; closure-audit / closed files excluded");
      // Filenames returned (basename only check, paths vary by OS)
      const bases = result.map((p) => p.replace(/^(.*[\\/])/, ""));
      assert.ok(bases.includes("2026-07-20-1000-multi-audit-a.md"));
      assert.ok(bases.includes("2026-07-20-1000-open-audit-b.md"));
      assert.ok(!bases.some((b) => b.includes("closure")));
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it("end-to-end: gate fires when only plan-level closure audits remain 'open'", async () => {
    // Verify the integration: with auditRound === maxAuditRounds and a plan-
    // level closure audit file in docs/audits/, the gate MUST still short-
    // circuit to completed (because openAudits() correctly returns []).
    const runDir = mkdtempSync(join(tmpdir(), "md-gate-planaudit-"));
    try {
      // Use a real audits dir for the expressionFuncs to scan so the filter
      // is exercised end-to-end.
      const auditsDir = join(runDir, "docs", "audits");
      mkdirSync(auditsDir, { recursive: true });
      writeFileSync(join(auditsDir, "2026-07-20-1000-closure-audit-x.md"),
        "> Audit Status: open\n> Audit Type: closure\n");

      // Wire openAudits() to a real flow-loader scan against the temp dir.
      const flow = gateFlow({ maxAuditRounds: 1 });
      const delegates = makeMockDelegates({
        responses: {
          DRAFT_PLANS: NOTHING,
          DEEP_AUDIT: AUDIT_COMPLETE,
        },
        config: { projectRoot: runDir, runDir },
      });
      // Override expressionFuncs to scan the temp auditsDir directly.
      delegates.expressionFuncs = {
        activePlans: () => [],
        draftPlans: () => [],
        openAudits: () => _scanOpenAuditsList(auditsDir),
      };

      const engine = new FlowEngine(flow, delegates);
      const result = await engine.run();

      // Even with the closure-audit file present, openAudits() returns [] due
      // to the WI4 Phase 5 filter. So the audit-gate fires on schedule when
      // auditRound reaches maxAuditRounds.
      assert.equal(result.status, "completed",
        "gate must short-circuit to completed when only plan-level closure audits remain");

      const events = readEvents(runDir);
      const gateEvents = events.filter((e) => e.type === "transition" && e.via === "audit_gate");
      assert.equal(gateEvents.length, 1,
        "audit-gate must fire exactly once when openAudits() is empty (closure audits filtered out)");
    } finally {
      rmSync(runDir, { recursive: true, force: true });
    }
  });
});
