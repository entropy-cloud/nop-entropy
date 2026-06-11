import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { FlowEngine } from "../src/engine.js";
import { makeMockDelegates, simpleFlow, mockSubFlows } from "./helpers.js";

describe("FlowEngine — goto with append", () => {
  it("appends previous output to target step prompt", async () => {
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check items",
        resultTag: "CHECK_RESULT",
        transitions: {
          pending: { goto: "WORK", append: true },
          done: { done: "completed" },
        },
      },
      WORK: {
        type: "agent",
        prompt: "do work",
        resultTag: "WORK_RESULT",
        transitions: { ok: { done: "completed" } },
      },
    }, "CHECK");

    let workPrompt = "";
    const delegates = makeMockDelegates({
      responses: {
        CHECK: "<CHECK_RESULT>pending</CHECK_RESULT>\n<ITEMS><item>task1</item></ITEMS>",
        WORK: (stepName, prompt) => { workPrompt = prompt; return { text: "<WORK_RESULT>ok</WORK_RESULT>", ok: true }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(workPrompt.includes("do work"));
    assert.ok(workPrompt.includes("<ITEMS>"));
    assert.ok(workPrompt.includes("task1"));
  });

  it("appends using template with extract", async () => {
    const flow = simpleFlow({
      CHECK: {
        type: "agent",
        prompt: "check",
        resultTag: "R",
        transitions: {
          pending: {
            goto: "WORK",
            append: { extract: "ITEMS", template: "Remaining:\n${output}" },
          },
          done: { done: "completed" },
        },
      },
      WORK: {
        type: "agent",
        prompt: "work",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "CHECK");

    let workPrompt = "";
    const delegates = makeMockDelegates({
      responses: {
        CHECK: { text: "<R>pending</R>\n<ITEMS><item>a</item></ITEMS>\n<EXTRA>noise</EXTRA>", ok: true },
        WORK: (sn, prompt) => { workPrompt = prompt; return { text: "<R>ok</R>", ok: true }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(workPrompt.includes("<ITEMS><item>a</item></ITEMS>"));
    assert.ok(!workPrompt.includes("<EXTRA>"));
  });
});

describe("FlowEngine — retry", () => {
  it("retries target step and accumulates append context", async () => {
    let draftCount = 0;
    let draftPrompts = [];

    const flow = simpleFlow({
      DRAFT: {
        type: "agent",
        prompt: "write plan",
        resultTag: "AI_STEP_RESULT",
        transitions: {
          created: { goto: "AUDIT" },
          none: { done: "completed" },
        },
      },
      AUDIT: {
        type: "agent",
        prompt: "audit plan",
        resultTag: "AI_STEP_RESULT",
        transitions: {
          approved: { done: "completed" },
          issues: {
            retry: "DRAFT",
            maxRetries: 3,
            append: { template: "Feedback:\n${output}" },
          },
        },
      },
    }, "DRAFT");

    const delegates = makeMockDelegates({
      responses: {
        DRAFT: (sn, prompt) => {
          draftCount++;
          draftPrompts.push(prompt);
          return { text: "<AI_STEP_RESULT>created</AI_STEP_RESULT>", ok: true };
        },
        AUDIT: (sn) => {
          if (draftCount < 3) {
            return { text: `<AI_STEP_RESULT>issues</AI_STEP_RESULT>\n<ISSUES><item>Issue ${draftCount}</item></ISSUES>`, ok: true };
          }
          return { text: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>", ok: true };
        },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(draftCount, 3);
    assert.ok(!draftPrompts[0].includes("Feedback"));
    assert.ok(draftPrompts[1].includes("Feedback"));
    assert.ok(draftPrompts[1].includes("Issue 1"));
    assert.ok(draftPrompts[2].includes("Feedback"));
    assert.ok(draftPrompts[2].includes("Issue 2"));
    assert.ok(draftPrompts[2].includes("───────────────"));
  });

  it("fires onMaxRetries when retry count exceeded", async () => {
    let draftCount = 0;

    const flow = simpleFlow({
      DRAFT: {
        type: "agent",
        prompt: "draft",
        resultTag: "R",
        transitions: { ok: { goto: "AUDIT" } },
      },
      AUDIT: {
        type: "agent",
        prompt: "audit",
        resultTag: "R",
        maxRetries: 2,
        transitions: {
          approved: { done: "completed" },
          issues: {
            retry: "DRAFT",
            append: true,
          },
        },
        onMaxRetries: { done: "degraded" },
      },
    }, "DRAFT");

    const delegates = makeMockDelegates({
      responses: {
        DRAFT: () => { draftCount++; return { text: "<R>ok</R>", ok: true }; },
        AUDIT: { text: "<R>issues</R>", ok: true },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "degraded");
    assert.ok(draftCount >= 3);
  });
});

describe("FlowEngine — onError (subprocess killed)", () => {
  it("uses onError when agent returns ok=false", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "do",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
        onError: { goto: "FALLBACK" },
      },
      FALLBACK: {
        type: "agent",
        prompt: "fallback",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });

    let callCount = 0;
    const delegates = makeMockDelegates({
      responses: {
        START: () => { callCount++; return { text: "", ok: false }; },
        FALLBACK: "<R>ok</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(result.stepCount, 2);
  });

  it("defaults to failed when no onError defined", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "fail",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: { START: { text: "", ok: false } },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "failed");
  });

  it("onError with retry", async () => {
    let attempt = 0;

    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "flaky",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
        onError: { retry: "START", maxRetries: 3, append: { template: "retry after kill" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        START: () => {
          attempt++;
          if (attempt < 3) return { text: "", ok: false };
          return { text: "<R>ok</R>", ok: true };
        },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(attempt, 3);
  });
});

describe("FlowEngine — case-insensitive marker aliases", () => {
  it("resolves marker via case-insensitive fallback (CREATED → created)", async () => {
    const flow = simpleFlow({
      PLAN_DRAFT: {
        type: "agent",
        prompt: "draft",
        resultTag: "AI_STEP_RESULT",
        transitions: { created: { goto: "NEXT" }, none: { done: "failed" } },
      },
      NEXT: {
        type: "agent",
        prompt: "next",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "PLAN_DRAFT");

    const delegates = makeMockDelegates({
      responses: {
        PLAN_DRAFT: "<AI_STEP_RESULT>CREATED</AI_STEP_RESULT>",
        NEXT: "<R>ok</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
  });

  it("resolves markers via case-insensitive match", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        transitions: {
          fixed: { goto: "DONE" },
          failed: { done: "failed" },
        },
      },
      DONE: {
        type: "agent",
        prompt: "done",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        START: "<R>FIXED</R>",
        DONE: "<R>ok</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
  });

  it("falls back to no_transition when alias not found", async () => {    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        onUnknownMaxRetries: 0,
        transitions: { yes: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
        responses: { START: "<R>nonexistent_value</R>" },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "no_transition");
  });
});

describe("FlowEngine — marker correction retry", () => {
  it("retries with correction prompt when marker not in transitions", async () => {
    let callCount = 0;
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        onUnknownMaxRetries: 2,
        transitions: { yes: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      async runAgent(stepName, prompt, system, sessionId) {
        delegates.callLog.push({ type: "agent", stepName, prompt, system, sessionId });
        callCount++;
        if (stepName.includes("correct")) {
          return { text: "<R>yes</R>", ok: true };
        }
        return { text: "<R>no</R>", ok: true, sessionId: "ses_test123" };
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(callCount >= 2);
    const correctionCalls = delegates.callLog.filter(c => c.stepName.includes("correct"));
    assert.ok(correctionCalls.length >= 1, "should have at least one correction call");
  });

  it("passes sessionId to correction retry", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        onUnknownMaxRetries: 2,
        transitions: { yes: { done: "completed" } },
      },
    });

    let capturedSessionId = null;
    const delegates = makeMockDelegates({
      responses: {
        START: () => ({ text: "<R>no</R>", ok: true, sessionId: "ses_test456" }),
      },
      async runAgent(stepName, prompt, system, sessionId) {
        delegates.callLog.push({ type: "agent", stepName, prompt, system, sessionId });
        if (stepName.includes("correct")) capturedSessionId = sessionId;
        if (stepName.includes("correct")) return { text: "<R>yes</R>", ok: true };
        return { text: "<R>no</R>", ok: true, sessionId: "ses_test456" };
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(capturedSessionId, "ses_test456");
  });

  it("gives up after onUnknownMaxRetries attempts", async () => {
    const flow = simpleFlow({
      START: {
        type: "agent",
        prompt: "go",
        resultTag: "R",
        onUnknownMaxRetries: 1,
        transitions: { yes: { done: "completed" } },
      },
    });

    const delegates = makeMockDelegates({
      responses: {
        START: "<R>garbage</R>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "no_transition");
  });
});

describe("FlowEngine — plan audit retry loop", () => {
  it("retries plan draft when audit finds issues, then proceeds on approval", async () => {
    const flow = simpleFlow({
      PLAN_DRAFT: {
        type: "agent",
        prompt: "draft plan",
        resultTag: "AI_STEP_RESULT",
        transitions: {
          created: { goto: "PLAN_AUDIT" },
          none: { done: "completed" },
        },
      },
      PLAN_AUDIT: {
        type: "agent",
        prompt: "audit plan",
        resultTag: "AI_STEP_RESULT",
        transitions: {
          approved: { goto: "EXECUTE" },
          issues: {
            retry: "PLAN_DRAFT",
            maxRetries: 3,
            append: { template: "Feedback:\n${output}" },
          },
        },
        onMaxRetries: { goto: "EXECUTE", append: { template: "⚠️ degraded:\n${output}" } },
      },
      EXECUTE: {
        type: "agent",
        prompt: "execute plan",
        resultTag: "AI_STEP_RESULT",
        transitions: { success: { done: "completed" } },
      },
    }, "PLAN_DRAFT");

    let draftCount = 0;
    const delegates = makeMockDelegates({
      responses: {
        PLAN_DRAFT: () => {
          draftCount++;
          return { text: "<AI_STEP_RESULT>created</AI_STEP_RESULT>", ok: true };
        },
        PLAN_AUDIT: () => {
          if (draftCount < 2) {
            return { text: "<AI_STEP_RESULT>issues</AI_STEP_RESULT>\n<ISSUES><item severity=\"Major\">fix exit criteria</item></ISSUES>", ok: true };
          }
          return { text: "<AI_STEP_RESULT>approved</AI_STEP_RESULT>", ok: true };
        },
        EXECUTE: "<AI_STEP_RESULT>success</AI_STEP_RESULT>",
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.equal(draftCount, 2);

    const draftCalls = delegates.callLog.filter(c => c.stepName === "PLAN_DRAFT");
    assert.equal(draftCalls.length, 2);
    assert.ok(!draftCalls[0].prompt.includes("Feedback"));
    assert.ok(draftCalls[1].prompt.includes("Feedback"));
    assert.ok(draftCalls[1].prompt.includes("fix exit criteria"));
  });

  it("degrades gracefully when plan audit retries exhausted", async () => {
    const flow = simpleFlow({
      PLAN_DRAFT: {
        type: "agent",
        prompt: "draft",
        resultTag: "R",
        transitions: { ok: { goto: "PLAN_AUDIT" } },
      },
      PLAN_AUDIT: {
        type: "agent",
        prompt: "audit",
        resultTag: "R",
        maxRetries: 2,
        transitions: {
          approved: { goto: "EXECUTE" },
          issues: { retry: "PLAN_DRAFT", append: true },
        },
        onMaxRetries: { goto: "EXECUTE", append: { template: "⚠️ plan audit failed" } },
      },
      EXECUTE: {
        type: "agent",
        prompt: "exec",
        resultTag: "R",
        transitions: { ok: { done: "completed" } },
      },
    }, "PLAN_DRAFT");

    let execPrompt = "";
    const delegates = makeMockDelegates({
      responses: {
        PLAN_DRAFT: "<R>ok</R>",
        PLAN_AUDIT: "<R>issues</R>",
        EXECUTE: (sn, prompt) => { execPrompt = prompt; return { text: "<R>ok</R>", ok: true }; },
      },
    });

    const engine = new FlowEngine(flow, delegates);
    const result = await engine.run();

    assert.equal(result.status, "completed");
    assert.ok(execPrompt.includes("⚠️ plan audit failed"));
  });
});
