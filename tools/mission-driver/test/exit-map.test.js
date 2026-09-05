// O7 — `main.js` `EXIT_MAP` exit-code contract regression suite (plan
// 2026-07-22-1223-2). Pins `EXECUTION-PRINCIPLE.md §11` row-by-row: every
// status the operator doc promises an exit code for must actually have one
// in the map, mapped to the documented value.
//
// Before the O7 fix the map was
//   { completed: 0, single_step_done: 0, failed: 1,
//     max_cycles: 2, max_total_steps: 2, max_retries: 2 }
// so `unknown_step` / `unknown_type` / `no_transition` / `invalid_transition`
// (documented exit 1) and `ping_pong` (documented ambiguously as `—`) all
// fell through to `undefined` → Node's default exit 0 (success). Anyone
// scripting the driver (`./tools/mission-driver.sh X && next-step`, or a CI
// gate) treated a flow-definition error or a death-loop as success.
//
// Two assertion blocks:
//   1. Documented-status mapping — for each documented terminal status in
//      EXECUTION-PRINCIPLE.md §11 (`completed`, `failed`, `max_cycles`,
//      `max_total_steps`, `max_retries`, `ping_pong`, `unknown_step`,
//      `unknown_type`, `no_transition`, `invalid_transition`) AND
//      `single_step_done` (documented in single-step.test.js:6 comment),
//      assert `EXIT_MAP[status]` equals the documented exit code.
//      This is the row-by-row contract pin O7 names.
//   2. No-documented-status-maps-to-undefined sweep — iterate the same
//      documented set and assert none is `undefined` in `EXIT_MAP`.
//      Scoped EXPLICITLY to the documented set: the engine emits
//      additional statuses NOT in §11 (notably `skipped` at engine.js
//      `_result(...)` call sites, and dynamic `done` values like
//      `onMaxRetries.done`) which are intentionally NOT in `EXIT_MAP` and
//      therefore intentionally map to `undefined` (exit 0 by Node's
//      default). Asserting the engine's full terminal-status set would be
//      self-contradictory with the `skipped` deferral; the sweep's contract
//      is "every status the doc promises an exit code for actually has one"
//      — the exact gap O7 names. (See plan `Deferred But Adjudicated`.)

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { EXIT_MAP } from "../src/main.js";

// Single source of truth for the documented contract. Each row: status →
// documented exit code per `EXECUTION-PRINCIPLE.md §11` (+ the
// `single_step_done → 0` note in `single-step.test.js:6`). If §11 adds or
// changes a row, this table is the one place to update; both assertion
// blocks below consume it, so the contract stays pinned end-to-end.
const DOCUMENTED = {
  completed: 0,
  single_step_done: 0,
  failed: 1,
  unknown_step: 1,
  unknown_type: 1,
  no_transition: 1,
  invalid_transition: 1,
  max_cycles: 2,
  max_total_steps: 2,
  max_retries: 2,
  ping_pong: 2,  // Phase 1 Decision alternative 1 — loop-guard alignment
};

describe("O7 — EXIT_MAP documented-status mapping (EXECUTION-PRINCIPLE.md §11)", () => {
  for (const [status, expected] of Object.entries(DOCUMENTED)) {
    it(`maps ${status} → exit ${expected}`, () => {
      assert.equal(
        EXIT_MAP[status],
        expected,
        `EXIT_MAP[${JSON.stringify(status)}] must be ${expected} per EXECUTION-PRINCIPLE.md §11 (got ${EXIT_MAP[status]})`,
      );
    });
  }
});

describe("O7 — no documented status maps to undefined (sweep, scoped to §11 set)", () => {
  it("every documented status has an explicit EXIT_MAP entry", () => {
    const unmapped = Object.keys(DOCUMENTED).filter((s) => EXIT_MAP[s] === undefined);
    assert.deepEqual(
      unmapped,
      [],
      `documented statuses missing from EXIT_MAP (would silently exit 0): ${JSON.stringify(unmapped)}. ` +
        `Note: engine statuses NOT in §11 (skipped, dynamic done values) are intentionally unmapped — ` +
        `see plan 2026-07-22-1223-2 Deferred But Adjudicated.`,
    );
  });

  it("DOCUMENTED table covers exactly the 11 §11 statuses (guard against accidental table drift)", () => {
    assert.equal(
      Object.keys(DOCUMENTED).length,
      11,
      "DOCUMENTED must list exactly the 11 statuses named in the plan Exit Criteria " +
        "(completed, single_step_done, failed, unknown_step, unknown_type, no_transition, " +
        "invalid_transition, max_cycles, max_total_steps, max_retries, ping_pong)",
    );
  });
});
