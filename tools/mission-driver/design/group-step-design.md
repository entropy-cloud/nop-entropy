# Group Step Design

> Status: **implemented** -- Phase 1-3 landed (engine.js `_executeGroupStep`, flow definitions)
> Last Reviewed: 2026-06-10
> Parent: [flow-engine-design.md](./flow-engine-design.md) Section 2.2 Step Types
> Source: Abstraction need from CLOSURE_SCRIPT_CHECK <-> CLOSURE_AUDIT loop

## 1. Motivation

The flow has a recurring "script check -> AI audit -> re-check" loop pattern. When implemented with scattered `goto` jumps:

1. **Loop logic scattered** -- CLOSURE_SCRIPT_CHECK and CLOSURE_AUDIT jump to each other via goto; iteration count controlled by piecemeal maxRetries and visitCounts.
2. **No holistic rate limiting** -- nowhere to say "this check-audit loop runs at most 3 rounds".
3. **Not reusable** -- future scenarios (e.g. BUILD_VERIFY -> FIX_BUILD loop) have similar needs.

Need a **general grouping step** that encapsulates multiple sub-steps into a black box, interacting externally via markers.

## 2. Core Principles

### 2.1 Black-Box Encapsulation

A group is an **opaque step**. From the outside, it's indistinguishable from an agent/tool/script step:

```
External view:
  EXECUTE -> CLOSURE_VERIFY --pass--> BUILD_VERIFY
                      | fail
                      +-> EXECUTE (with remaining items)
```

The group **does not know about external steps**. It interacts via returning markers; the outside maps markers to actions via `transitions`.

### 2.2 Internal Autonomy

Sub-steps inside a group jump to each other via `goto`, but goto targets can only be:

1. **Other sub-step names within the same group**
2. **Special markers** (see 2.3)

### 2.3 Sub-step marker -> action mapping

In sub-step `transitions`, each marker maps to an action:

```typescript
type SubTransition =
  | { goto: string }           // jump to another sub-step in group, or special marker
  | { exit: string }           // exit group, return specified marker
```

Special goto values:

| Value | Meaning |
|-------|---------|
| Sub-step name | Intra-group jump |
| `_retry` | Round failed, round++, restart from group's first sub-step |

`exit` field: directly exit group with specified marker (e.g. `{ exit: "pass" }`).

### 2.4 Rounds and Fallback

- **round**: Increments each time the group's first sub-step is executed (first round round=1).
- **maxRounds**: Group-level config; exceeding triggers `onExhausted`.
- **onExhausted**: Exit marker when rounds exhausted, default `"fail"`.

## 3. DSL Schema

### 3.1 Group Step Definition

```javascript
CLOSURE_VERIFY: {
  type: "group",
  maxRounds: 3,
  onExhausted: "fail",     // optional, default "fail"

  steps: {
    SCRIPT_CHECK: {
      type: "script",
      run: async (delegates) => { /* ... */ },
      transitions: {
        pass:  { exit: "pass" },        // exit group with "pass"
        fail:  { goto: "AI_AUDIT" },     // jump to AI_AUDIT in group
      },
    },

    AI_AUDIT: {
      type: "agent",
      prompt: "...Output <AI_STEP_RESULT>complete</AI_STEP_RESULT> or <AI_STEP_RESULT>incomplete</AI_STEP_RESULT>...",
      transitions: {
        complete:   { goto: "_retry" },  // back to group start, round++
        incomplete: { exit: "fail" },     // exit group with "fail"
      },
      onError: { exit: "fail" },
    },
  },

  // Group external transitions -- same as normal steps
  transitions: {
    pass: { goto: "BUILD_VERIFY" },
    fail: {
      retry: "EXECUTE",
      maxRetries: 5,
      append: { extract: "REMAINING", template: "..." },
    },
  },
  onError: { retry: "EXECUTE", maxRetries: 3 },
},
```

### 3.2 Action Type Summary

```typescript
// Actions within sub-step transitions:
type SubAction =
  | { goto: string }         // intra-group jump (step name | "_retry")
  | { exit: string }         // exit group with specified marker

// Group external transitions -- identical to normal steps:
type OuterAction = Action   // goto | retry | done (see flow-engine-design.md Section 2.3 Action)
```

### 3.3 Execution Semantics

```
group.run():
  round = 0
  loop:
    round++
    if round > maxRounds:
      return onExhausted    // default "fail"

    currentSubStep = first step in group.steps

    subLoop:
      result = execute(currentSubStep)
      marker = resolveMarker(result, currentSubStep)
      action = currentSubStep.transitions[marker]

      if action.exit:
        return action.exit    // exit group with specified marker

      if action.goto == "_retry":
        break subLoop        // break inner loop, start new round

      if action.goto in group.steps:
        currentSubStep = group.steps[action.goto]
        continue subLoop

      // goto targets non-existent sub-step -> error
      return "error"
```

## 4. Integration with Existing Engine

### 4.1 Engine Changes

In `engine.js`'s main loop, when `step.type === "group"`:

```javascript
if (stepDef.type === "group") {
  const groupMarker = await this._executeGroupStep(currentStep, stepDef);
  // groupMarker is the marker returned by the group (e.g. "pass", "fail")
  // then use stepDef.transitions[groupMarker] to decide external jump
  marker = groupMarker;
}
```

### 4.2 Sub-step Execution

Sub-step execution reuses existing `_executeAgentStep`, `_executeScriptStep`, etc.

Key differences:
- Sub-steps **do not need** append buffer (group doesn't maintain append context internally).
- Sub-steps **do not need** visitCount / maxCycleVisits checks (controlled by group's maxRounds).
- Sub-step onError maps directly to `{ exit: "fail" }` or `{ goto: "_retry" }`.

### 4.3 Logging

```
[12:00:01] [step 15] CLOSURE_VERIFY (round 1/3)
[12:00:01]   [sub] SCRIPT_CHECK -> marker: fail
[12:00:01]   [sub] AI_AUDIT -> marker: complete
[12:00:05] [step 15] CLOSURE_VERIFY (round 2/3)
[12:00:05]   [sub] SCRIPT_CHECK -> marker: pass
[12:00:05]   group exit: pass
[12:00:05]   marker: pass -> goto BUILD_VERIFY
```

## 5. Full CLOSURE_VERIFY Definition Example

### 5.1 Before and After

**Before** (scattered goto jumps):

```
EXECUTE -> CLOSURE_SCRIPT_CHECK --pass--> BUILD_VERIFY
               | fail
               v
           CLOSURE_AUDIT --complete--> CLOSURE_SCRIPT_CHECK  (goto back)
               | incomplete
               v
           EXECUTE (retry)
```

**After** (group encapsulation):

```
EXECUTE -> CLOSURE_VERIFY --pass--> BUILD_VERIFY
               | fail (includes exit:fail and incomplete cases)
               v
           EXECUTE (retry, with remaining items)
```

### 5.2 Group Definition

```javascript
CLOSURE_VERIFY: {
  type: "group",
  maxRounds: 3,
  onExhausted: "fail",

  steps: {
    SCRIPT_CHECK: {
      type: "script",
      run: async (delegates) => {
        const { execSync } = await import("node:child_process");
        try {
          execSync("node plan-check.mjs --active-only --quiet --strict", {
            cwd: delegates.config.projectRoot,
            encoding: "utf8",
            timeout: 30_000,
          });
          return "pass";
        } catch {
          return "fail";
        }
      },
      transitions: {
        pass: { exit: "pass" },
        fail: { goto: "AI_AUDIT" },
      },
    },

    AI_AUDIT: {
      type: "agent",
      prompt: `You are an independent verifier -- you did NOT participate in plan execution. ...`,
      transitions: {
        complete:   { goto: "_retry" },
        incomplete: { exit: "fail" },
      },
      onError: { exit: "fail" },
    },
  },

  transitions: {
    pass: { goto: "BUILD_VERIFY" },
    fail: {
      retry: "EXECUTE",
      maxRetries: 5,
      append: {
        extract: "REMAINING",
        template: "\n\nThe following items remain unfinished -- continue:\n${output}",
      },
    },
  },
  onError: {
    retry: "EXECUTE",
    maxRetries: 3,
    append: { template: "\n\nClosure audit subprocess was killed. Re-check the plan Exit Criteria." },
  },
  onMaxRetries: { goto: "BUILD_VERIFY" },
},
```

### 5.3 Behavior Matrix

| Scenario | Flow | Group Returns Marker |
|----------|------|---------------------|
| Script check passes | SCRIPT_CHECK(pass) -> exit | `"pass"` |
| Script fails, AI fixes, re-check passes | SCRIPT_CHECK(fail) -> AI_AUDIT(complete) -> round 2 -> SCRIPT_CHECK(pass) -> exit | `"pass"` |
| AI says cannot complete | SCRIPT_CHECK(fail) -> AI_AUDIT(incomplete) -> exit | `"fail"` |
| 3 rounds all fail script check | round 1->2->3 all fail -> onExhausted | `"fail"` |
| AI subprocess killed | AI_AUDIT onError -> exit | `"fail"` |

## 6. Future Reuse Scenarios

### 6.1 BUILD_VERIFY + FIX_BUILD Loop

```javascript
BUILD_FIX_LOOP: {
  type: "group",
  maxRounds: 3,
  onExhausted: "fail",
  steps: {
    BUILD: {
      type: "tool",
      command: "./mvnw clean install -pl {module} -am -T 1C",
      transitions: {
        pass: { exit: "pass" },
        fail: { goto: "FIX" },
      },
    },
    FIX: {
      type: "agent",
      prompt: "Fix build errors for module {module}...",
      transitions: {
        fixed:   { goto: "_retry" },
        failed:  { exit: "fail" },
      },
      onError: { exit: "fail" },
    },
  },
  transitions: {
    pass: { goto: "ROADMAP_CHECK" },
    fail: { goto: "PLAN_DRAFT" },
  },
},
```

### 6.2 PLAN_DRAFT + PLAN_AUDIT Loop

> **Deprecated (v5)**: This group-step approach modeled draft and audit as engine-level loops. From v5, draft and audit are merged into a single `DRAFT_PLANS` agent step -- the review loop completes within the agent step via independent session sub-agent, no longer needing engine-level group/retry orchestration. Retained below as group-step modeling reference.

```javascript
PLAN_DRAFT_LOOP: {
  type: "group",
  maxRounds: 3,
  onExhausted: "approved",  // degraded: after 3 rounds without approved, proceed with issues
  steps: {
    DRAFT: {
      type: "agent",
      prompt: "...",
      transitions: {
        created: { goto: "AUDIT" },
        none:    { exit: "none" },
      },
    },
    AUDIT: {
      type: "agent",
      prompt: "...",
      transitions: {
        approved: { exit: "approved" },
        issues:   { goto: "_retry" },
      },
    },
  },
  transitions: {
    approved: { goto: "EXECUTE" },
    none:     { goto: "ROADMAP_CHECK" },
  },
},
```

## 7. Implementation Status

**All phases completed.**

| Phase | Status | Evidence |
|-------|--------|----------|
| Phase 1: Engine extension | Done | `engine.js` `_executeGroupStep()` |
| Phase 2: Flow definitions | Done | Group steps in `flows/mission-driver.json` (EXEC_PLANS, REVIEW_PLANS) |
| Phase 3: Tests | Done | `test/group-subflow.test.js` group step tests |
| Phase 4: Documentation | Done | `flow-engine-design.md` Section 2.2 Step Types includes group |
