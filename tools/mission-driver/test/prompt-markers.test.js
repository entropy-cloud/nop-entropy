import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { lintPrompt, lintAllPrompts, buildPromptMarkerMap } from "../src/prompt-check.mjs";

describe("prompt-check — result-tag lint (§1.4-0, §11.8)", () => {
  it("all shipped prompts/*.md pass the linter (no malformed/typo'd tags)", () => {
    const errors = lintAllPrompts();
    assert.deepEqual(errors, [], errors.join("\n"));
  });

  it("catches the mismatched-tag regression (<AIE_STEP_RESULT>done</AI_STEP_RESULT>)", () => {
    const errors = lintPrompt(
      "x.md",
      "example:\n<AIE_STEP_RESULT>done</AI_STEP_RESULT>\n",
      { markers: new Set(["done"]), forEach: false },
    );
    assert.equal(errors.length, 1);
    assert.match(errors[0], /malformed result tag/);
  });

  it("catches a consistently misspelled pair (<AIE_STEP_RESULT>…</AIE_STEP_RESULT>)", () => {
    const errors = lintPrompt(
      "x.md",
      "<AIE_STEP_RESULT>done</AIE_STEP_RESULT>",
      { markers: new Set(["done"]), forEach: false },
    );
    assert.equal(errors.length, 1);
    assert.match(errors[0], /malformed result tag/);
  });

  it("catches an out-of-contract marker value for a non-forEach step", () => {
    const errors = lintPrompt(
      "x.md",
      "<AI_STEP_RESULT>banana</AI_STEP_RESULT>",
      { markers: new Set(["pass", "fail"]), forEach: false },
    );
    assert.equal(errors.length, 1);
    assert.match(errors[0], /not a valid transition\/alias/);
  });

  it("accepts a well-formed in-contract example", () => {
    const errors = lintPrompt(
      "x.md",
      "return `<AI_STEP_RESULT>pass</AI_STEP_RESULT>`",
      { markers: new Set(["pass", "fail"]), forEach: false },
    );
    assert.deepEqual(errors, []);
  });

  it("does NOT enforce value membership for forEach steps (e.g. plan-review approved)", () => {
    const errors = lintPrompt(
      "plan-review.md",
      "<AI_STEP_RESULT>approved</AI_STEP_RESULT>",
      { markers: new Set(["all_complete", "some_failed", "all_failed"]), forEach: true },
    );
    assert.deepEqual(errors, []);
  });

  it("maps prompts to their flow steps (draft-from-roadmap → created/nothing)", () => {
    const map = buildPromptMarkerMap();
    const info = map.get("draft-from-roadmap.md");
    assert.ok(info, "draft-from-roadmap.md must be mapped");
    // WI4: DRAFT_PLANS no longer exposes a `done` exit — the engine's
    // audit-gate decides mission completion, not the AI. Only `created`
    // and `nothing` are valid markers for this step now.
    for (const m of ["created", "nothing"]) {
      assert.ok(info.markers.has(m), `expected marker ${m}`);
    }
    assert.ok(!info.markers.has("done"),
      "done marker must NOT be present after WI4 (engine audit-gate owns mission completion)");
    assert.equal(map.get("plan-review.md")?.forEach, true, "plan-review step is forEach");
  });
});
