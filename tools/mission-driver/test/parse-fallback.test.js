import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine, extractTagTolerant } from "../src/engine.js";
import { makeMockDelegates, simpleFlow } from "./helpers.js";

describe("extractTagTolerant — tolerant marker extraction (OPT-2)", () => {
  it("matches the strict form (parity with extractTag)", () => {
    assert.equal(
      extractTagTolerant("<AI_STEP_RESULT>success</AI_STEP_RESULT>", "AI_STEP_RESULT"),
      "success",
    );
  });

  it("matches case-insensitive tag name", () => {
    assert.equal(
      extractTagTolerant("<ai_step_result>success</ai_step_result>", "AI_STEP_RESULT"),
      "success",
    );
    assert.equal(
      extractTagTolerant("<Ai_Step_Result>Pass</Ai_Step_Result>", "AI_STEP_RESULT"),
      "pass",
    );
  });

  it("tolerates whitespace between tag name and angle brackets", () => {
    assert.equal(
      extractTagTolerant("< AI_STEP_RESULT >fail< / AI_STEP_RESULT >", "AI_STEP_RESULT"),
      "fail",
    );
    assert.equal(
      extractTagTolerant("<AI_STEP_RESULT >ok</AI_STEP_RESULT >", "AI_STEP_RESULT"),
      "ok",
    );
  });

  it("extracts a marker wrapped in markdown code fences", () => {
    const text = "Here is the result:\n```\n<AI_STEP_RESULT>pass</AI_STEP_RESULT>\n```\ndone.";
    assert.equal(extractTagTolerant(text, "AI_STEP_RESULT"), "pass");
  });

  it("takes the last match and lowercases/trims the value", () => {
    const text = "<AI_STEP_RESULT>fail</AI_STEP_RESULT> text <AI_STEP_RESULT>  PASS  </AI_STEP_RESULT>";
    assert.equal(extractTagTolerant(text, "AI_STEP_RESULT"), "pass");
  });

  it("returns null when no marker is present", () => {
    assert.equal(extractTagTolerant("no marker here", "AI_STEP_RESULT"), null);
    assert.equal(extractTagTolerant("<AI_STEP_RESULT>unclosed", "AI_STEP_RESULT"), null);
    assert.equal(extractTagTolerant("", "AI_STEP_RESULT"), null);
    assert.equal(extractTagTolerant("<AI_STEP_RESULT>success</AI_STEP_RESULT>", "OTHER_TAG"), null);
  });

  it("escapes regex metacharacters in the tag name", () => {
    // a tagName with a regex metachar should be matched literally, not interpreted
    assert.equal(
      extractTagTolerant("<R.T>ok</R.T>", "R.T"),
      "ok",
    );
  });
});

describe("FlowEngine — OPT-2 tolerant extract short-circuits parse fallback", () => {
  it("does NOT call runParseAgent when strict misses but tolerant hits", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { success: { done: "completed" } },
        onUnknown: { done: "failed" },
      },
    });
    // lowercase tag → strict extractTag misses, tolerant regex hits
    const delegates = makeMockDelegates({
      responses: { START: "<ai_step_result>success</ai_step_result>" },
    });
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(
      delegates.callLog.filter((c) => c.type === "parse").length,
      0,
      "runParseAgent must NOT be called when tolerant extract hits",
    );
  });

  it("tolerant hit still flows through marker alias normalization", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        // only an alias target exists; "ok" must be aliased to "pass" via markerAliases
        markerAliases: undefined,
        transitions: { pass: { done: "completed" } },
        onUnknown: { done: "failed" },
      },
    });
    flow.markerAliases = { ok: "pass" };
    const delegates = makeMockDelegates({
      responses: { START: "< AI_STEP_RESULT >Ok</ AI_STEP_RESULT >" },
    });
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(
      delegates.callLog.filter((c) => c.type === "parse").length,
      0,
      "runParseAgent must NOT be called — tolerant hit + alias normalization",
    );
  });

  it("still calls runParseAgent when both strict and tolerant miss (fallback kept)", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { success: { done: "completed" } },
        onUnknown: { done: "failed" },
      },
    });
    const delegates = makeMockDelegates({
      responses: { START: "the agent returned no marker at all" },
    });
    // Make runParseAgent return a recognizable strict marker so the flow resolves.
    delegates.runParseAgent = async (stepName, prompt, system) => {
      delegates.callLog.push({ type: "parse", stepName });
      return { text: "<AI_STEP_RESULT>success</AI_STEP_RESULT>", ok: true };
    };
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(
      delegates.callLog.some((c) => c.type === "parse"),
      "runParseAgent must be called as the last-resort fallback",
    );
  });
});

describe("FlowEngine — OPT-3 correction routes through runParseAgent", () => {
  it("_runCorrectionAgent calls delegates.runParseAgent and forwards sessionId", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent", prompt: "go", resultTag: "AI_STEP_RESULT",
        transitions: { success: { done: "completed" } },
        onUnknownMaxRetries: 2,
      },
    });
    // Agent emits an invalid marker; runAgent returns a sessionId so we can
    // assert it is forwarded to the correction retry via runParseAgent.
    const delegates = makeMockDelegates({
      responses: {
        START: { text: "<AI_STEP_RESULT>bogus</AI_STEP_RESULT>", ok: true, sessionId: "ses_corr_1" },
      },
    });
    const parseCalls = [];
    delegates.runParseAgent = async (stepName, prompt, system, sessionId) => {
      parseCalls.push({ stepName, sessionId });
      return { text: "<AI_STEP_RESULT>success</AI_STEP_RESULT>", ok: true };
    };
    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(parseCalls.length >= 1, "correction must go through runParseAgent");
    assert.equal(parseCalls[0].sessionId, "ses_corr_1", "sessionId must be forwarded to correction");
  });
});
