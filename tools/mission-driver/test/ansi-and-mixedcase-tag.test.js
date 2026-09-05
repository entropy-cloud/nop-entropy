import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { extractTag, extractTagTolerant, extractTagFuzzy, stripAnsiControl } from "../src/engine.js";

// ── mdr-2 Phase 1: stripAnsiControl pure function ─────────────────────────
describe("stripAnsiControl — strips ANSI CSI/OSC/ESC + stray C0 controls", () => {
  it("removes a simple SGR color sequence (ESC[31m...ESC[0m)", () => {
    const raw = "\x1b[31mERROR\x1b[0m done";
    assert.equal(stripAnsiControl(raw), "ERROR done");
  });

  it("removes multi-param CSI (ESC[1;31m) and cursor moves (ESC[2K)", () => {
    const raw = "\x1b[1;31m<AI_STEP_RESULT>done</AI_STEP_RESULT>\x1b[0m\x1b[2K";
    assert.equal(stripAnsiControl(raw), "<AI_STEP_RESULT>done</AI_STEP_RESULT>");
  });

  it("removes OSC sequences terminated by BEL", () => {
    const raw = "\x1b]0;window-title\x07visible";
    assert.equal(stripAnsiControl(raw), "visible");
  });

  it("removes OSC sequences terminated by ST (ESC \\)", () => {
    const raw = "a\x1b]2;title\x1b\\b";
    assert.equal(stripAnsiControl(raw), "ab");
  });

  it("preserves meaningful whitespace (\\t \\n \\r)", () => {
    assert.equal(stripAnsiControl("a\tb\nc\rd"), "a\tb\nc\rd");
  });

  it("removes stray C0 controls (NUL/VT/FF/etc.) except \\t \\n \\r", () => {
    assert.equal(stripAnsiControl("a\x00b\x0bc\x0cd"), "abcd");
  });

  it("is a no-op on already-clean text (idempotent)", () => {
    const clean = "<AI_STEP_RESULT>pass</AI_STEP_RESULT>";
    assert.equal(stripAnsiControl(clean), clean);
    assert.equal(stripAnsiControl(stripAnsiControl(clean)), clean);
  });

  it("returns empty string for null/undefined/empty input", () => {
    assert.equal(stripAnsiControl(null), "");
    assert.equal(stripAnsiControl(undefined), "");
    assert.equal(stripAnsiControl(""), "");
  });
});

// ── mdr-2 Phase 1: extractTagFuzzy now accepts mixed-case tag names ───────
describe("extractTagFuzzy — case-insensitive tag name (§Ai_ variant)", () => {
  const valid = ["done", "nothing", "created", "pass", "fail"];

  it("recovers mixed-case open tag typo: <Ai_STEP_RESULT>done</Ai_STEP_RESULT>", () => {
    // The LLM-recovery chain has been observed to emit a lowercase-i variant
    // that the OLD `[A-Z]`-only char class silently dropped (memory L009, SEV1).
    assert.equal(
      extractTagFuzzy("<Ai_STEP_RESULT>done</Ai_STEP_RESULT>", valid),
      "done",
    );
  });

  it("recovers all-lowercase tag names: <ai_step_result>pass</ai_step_result>", () => {
    assert.equal(
      extractTagFuzzy("<ai_step_result>pass</ai_step_result>", valid),
      "pass",
    );
  });

  it("recovers mixed-case with mismatched open/close: <Ai_STEP_RESULT>done</AI_STEP_RESULT>", () => {
    assert.equal(
      extractTagFuzzy("<Ai_STEP_RESULT>done</AI_STEP_RESULT>", valid),
      "done",
    );
  });

  // Regression guard: widening [A-Z] -> [A-Za-z] must NOT break the existing
  // all-uppercase typo variants (locked by tag-recovery-reconcile.test.js too).
  it("STILL recovers all-uppercase typo: <AIE_STEP_RESULT>done</AI_STEP_RESULT>", () => {
    assert.equal(
      extractTagFuzzy("<AIE_STEP_RESULT>done</AI_STEP_RESULT>", valid),
      "done",
    );
  });

  // HTML guard preserved: length>=5 + value whitelist still reject short tags.
  it("STILL does NOT match HTML-ish lowercase short tags", () => {
    assert.equal(extractTagFuzzy("<b>done</b>", valid), null);
    assert.equal(extractTagFuzzy("<span>pass</span>", valid), null);
    assert.equal(extractTagFuzzy("<code>fail</code>", valid), null);
  });
});

// ── mdr-2 Phase 1: ANSI pollution must not defeat strict/tolerant extraction ─
describe("strict/tolerant extraction — robust after ANSI stripping", () => {
  it("strict extractTag recovers `done` from ANSI-wrapped tag (after strip)", () => {
    const raw = "work done\n\x1b[32m<AI_STEP_RESULT>done</AI_STEP_RESULT>\x1b[0m";
    const cleaned = stripAnsiControl(raw);
    assert.equal(extractTag(cleaned, "AI_STEP_RESULT"), "done");
  });

  it("tolerant extract recovers `pass` from ANSI-wrapped + whitespacey tag (after strip)", () => {
    const raw = "\x1b[1;36m< AI_STEP_RESULT >pass< /AI_STEP_RESULT >\x1b[0m\x1b[0m";
    const cleaned = stripAnsiControl(raw);
    assert.equal(extractTagTolerant(cleaned, "AI_STEP_RESULT"), "pass");
  });

  it("without stripping, ANSI inside the value capture breaks strict extract (motivation proof)", () => {
    // ANSI bytes between the value and the closing tag break the `[^<]+` matcher —
    // this is the failure mode stripAnsiControl exists to neutralize. CSI bytes
    // contain no `<`, so they pass through `[^<]+` and pollute the captured value.
    const polluted = "<AI_STEP_RESULT>\x1b[32mdone\x1b[0m</AI_STEP_RESULT>";
    const strictRaw = extractTag(polluted, "AI_STEP_RESULT");
    // Raw extraction yields a value polluted by the CSI bytes (not clean `done`).
    assert.notEqual(strictRaw, "done");
    // After stripping, extraction is clean.
    assert.equal(extractTag(stripAnsiControl(polluted), "AI_STEP_RESULT"), "done");
  });
});
