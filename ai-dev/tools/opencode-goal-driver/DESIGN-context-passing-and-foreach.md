# Context Passing & For-Each Step Design

> Status: **superseded** — 已迁移至 `ai-dev/design/opencode-goal-driver/context-passing-and-foreach-design.md`，并标注 superseded 状态
> Last Reviewed: 2026-06-10
> Reviewers: (round 1 complete — see §8 for review log)
>
> **注意**：本文档保留作为历史参考。权威设计见 [flow-engine-design.md](../../../design/opencode-goal-driver/flow-engine-design.md)。

## 1. Problem Statement

### 1.1 Problem A: No Context Passing Between Steps

PLAN_DRAFT creates a plan file and outputs `<AI_STEP_RESULT>created</AI_STEP_RESULT>`. The plan file path exists only in the agent's text output. When PLAN_AUDIT runs, its prompt says "Review the plan that was just created" — the agent must independently find which plan to review. This is fragile:

1. **Wrong target** — The agent may review an older active plan instead of the one just created
2. **No explicit contract** — There is no structured way for PLAN_DRAFT to tell downstream steps "here is the exact file path"
3. **Repeated across all downstream steps** — EXECUTE, CLOSURE_VERIFY.SCRIPT_CHECK, CLOSURE_VERIFY.AI_AUDIT all independently re-discover the plan path

Current workarounds:
- EXECUTE prompt says "Run check-plan-status.mjs, select the first plan in the Active list"
- CLOSURE_VERIFY.AI_AUDIT prompt says "Read the latest active plan under ai-dev/plans/"
- Both are ambiguous when multiple active plans exist

### 1.2 Problem B: No Iteration Over Collections

DETECT_START detects active plans and returns `"execute"`. The flow then processes ONE plan (the first active one). But there may be multiple active plans for the module. The current design has no mechanism to iterate over a collection and apply a sub-flow to each item.

**Foreach is needed for one concrete scenario today**: when DETECT_START finds multiple active plans, process ALL of them before moving to DEEP_AUDIT. The feature is generalized as an engine primitive because the same pattern (iterate collection → run sub-flow per item) will recur in future flows.

## 2. Design Goals

1. **Context variables** — Steps can output structured key-value pairs that downstream steps reference in prompt templates
2. **For-each step** — A new step type that iterates over a collection and executes a sub-flow for each item
3. **Backward compatible** — Existing flow definitions and tests must continue to work without changes
4. **Generic engine** — These are engine-level features, not goal-driver-specific

## 3. Solution Part A: Flow Variables (contextVars)

### 3.1 Concept

Add a `flowVars` map to the engine. Steps can write to it, and prompt templates reference it via `{variableName}`.

**Relationship to existing `this.context`:**

| Field | Purpose | Key type | Value type | Used by |
|-------|---------|----------|------------|---------|
| `this.context` | Step output history | Step name (string) | `{ ok, marker, text }` | Append/retry logic, evidence |
| `this.flowVars` | Template variable substitution | Variable name (string) | String | `_buildPrompt()`, script steps |

They are separate because they serve different purposes: `context` is for the engine's internal step-to-step data flow; `flowVars` is for template substitution in prompts and commands.

The existing `_templateVar()` method already supports `{var}` substitution from `delegates.vars`. The change is to merge `flowVars` into the same substitution pool, with `flowVars` taking precedence (allows overriding static vars with dynamic ones).

### 3.2 How Steps Output Flow Variables

**Agent steps** — Output XML blocks that the engine extracts:

```
<AI_STEP_RESULT>created</AI_STEP_RESULT>
<FLOW_VARS>
  <PLAN_FILE>ai-dev/plans/2026-06-10-142-my-plan.md</PLAN_FILE>
</FLOW_VARS>
```

The engine extracts `PLAN_FILE` from the `<FLOW_VARS>` block and writes it to `flowVars`.

Tag name `FLOW_VARS` is deliberately specific to avoid collision with generic `<CONTEXT>` or domain-specific XML that agent output may contain.

**Extraction fallback**: If the agent doesn't output `<CONTEXT>`, no variables are extracted. Downstream steps that reference `{PLAN_FILE}` will see the literal `{PLAN_FILE}` string in their prompt, making the failure visible.

**Script steps** — Return an object instead of a string:

```javascript
// Legacy (still supported):
run: (delegates) => "execute"

// New format (marker + variables):
run: (delegates) => ({ marker: "execute", vars: { activePlanCount: 3 } })
```

The engine detects the return type. If it's a string, it's the marker (backward compatible). If it's an object with a `marker` field, extract both marker and vars.

**Tool steps** — No variable output (exit code is the only output). This is fine.

**Group steps** — Sub-steps can write to `flowVars`. Variables set inside a group are visible after the group completes.

### 3.3 Variable Precedence

When resolving `{var}` in templates:

```
1. flowVars (dynamic, set by steps at runtime)
2. delegates.vars (static, set at initialization)
3. Keep original `{var}` if not found (visible in prompt for debugging)
```

### 3.4 Required Variables and Validation

Steps can declare `requiredVars` — a list of variable names that **must** exist in `flowVars` after the step executes. The engine validates this and attempts automatic repair.

```javascript
PLAN_DRAFT: {
  type: "agent",
  prompt: "...",
  requiredVars: ["PLAN_FILE"],   // engine checks PLAN_FILE exists after execution
  transitions: { ... },
}
```

**Validation + repair flow:**

```
1. Execute agent step, extract <FLOW_VARS> from output
2. Check: all requiredVars present in flowVars?
   ├─ Yes → continue to transitions
   └─ No  → session continue repair:
       a. Send prompt to same session:
          "The following required variables were not found in your output: PLAN_FILE.
           Please output them now in <FLOW_VARS> format:
           <FLOW_VARS><PLAN_FILE>the plan file path</PLAN_FILE></FLOW_VARS>"
       b. Extract vars from repair response
       c. Re-check requiredVars
       d. Repeat up to maxRetries (default: 2, configurable via requiredVarsMaxRetries)
3. Still missing after retries → step fails (no matching transition), falls to onError
```

**Engine implementation:**

```javascript
async _validateFlowVars(stepName, stepDef, sessionId) {
  const required = stepDef.requiredVars;
  if (!required || required.length === 0) return true;

  const maxRetries = stepDef.requiredVarsMaxRetries ?? 2;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    const missing = required.filter(v => !this.flowVars.has(v));
    if (missing.length === 0) return true;

    if (attempt < maxRetries) {
      this._log(`  ${stepName}: missing vars [${missing.join(", ")}], repair attempt ${attempt + 1}/${maxRetries}`);
      const repairPrompt = [
        `The following required variables were not found in your output: ${missing.join(", ")}.`,
        `Please output them now in <FLOW_VARS> format.`,
        missing.map(v => `<${v}>value of ${v}</${v}>`).join("\n"),
        `Wrap them in <FLOW_VARS>...</FLOW_VARS>.`,
      ].join("\n");

      const repaired = await this._executeAgentStep(
        `${stepName}:fix-vars-${attempt}`, { prompt: repairPrompt }, sessionId,
      );
      if (repaired && repaired.text) {
        const vars = this._extractFlowVars(repaired.text);
        for (const [k, v] of Object.entries(vars)) {
          this.flowVars.set(k, v);
        }
      }
    }
  }

  const stillMissing = required.filter(v => !this.flowVars.has(v));
  this._log(`  ${stepName}: required vars still missing after ${maxRetries} repairs: [${stillMissing.join(", ")}]`);
  return false;
}
```

This is parallel to the existing marker correction retry mechanism (`onUnknownMaxRetries`), but for variable validation instead of marker resolution.

### 3.5 Engine Changes

```javascript
// In constructor — add flowVars alongside existing context:
this.context = new Map();       // existing: step output history
this.flowVars = new Map();      // new: template variable substitution

// Updated _buildPrompt():
_buildPrompt(stepName, stepDef) {
  let prompt = stepDef.prompt || "";
  const allVars = { ...(this.delegates.vars || {}), ...Object.fromEntries(this.flowVars) };
  prompt = this._templateVar(prompt, allVars);

  const buf = this.appendBuffers.get(stepName);
  if (buf) {
    prompt += "\n" + buf;
  }
  return prompt;
}

// New extraction method:
_extractFlowVars(text) {
  const m = text.match(/<FLOW_VARS>([\s\S]*?)<\/FLOW_VARS>/);
  if (!m) return {};
  const vars = {};
  const re = /<(\w+)>([^<]*)<\/\1>/g;
  let match;
  while ((match = re.exec(m[1])) !== null) {
    vars[match[1]] = match[2].trim();
  }
  if (Object.keys(vars).length === 0) {
    this._log(`  warning: <FLOW_VARS> block found but no variables extracted`);
  }
  return vars;
}
```

In `run()`, after executing an agent step:

```javascript
// After result is obtained from agent step:
if (result.text) {
  const vars = this._extractFlowVars(result.text);
  for (const [k, v] of Object.entries(vars)) {
    this.flowVars.set(k, v);
  }
}

// Validate required vars (with session-continue repair)
if (stepDef.requiredVars) {
  const valid = await this._validateFlowVars(currentStep, stepDef, this.lastSessionId);
  if (!valid) {
    this._log(`  required vars validation failed`);
    const onError = stepDef.onError || { done: "failed" };
    if (onError.done) return this._result(onError.done, totalSteps);
    if (onError.goto) { currentStep = onError.goto; continue; }
    return this._result("failed", totalSteps);
  }
}
```

### 3.6 Script Step Return Type Change

```javascript
async _executeScriptStep(stepName, stepDef) {
  const ret = await stepDef.run(this.delegates, this.flowVars);
  if (ret && typeof ret === "object" && ret.marker !== undefined) {
    // New format: { marker, vars }
    if (ret.vars) {
      for (const [k, v] of Object.entries(ret.vars)) {
        this.flowVars.set(k, v);
      }
    }
    return { ok: true, marker: ret.marker, text: String(ret.marker) };
  }
  // Legacy format: string marker
  return { ok: true, marker: ret, text: String(ret) };
}
```

The `run()` signature changes from `(delegates)` to `(delegates, flowVars)`. All existing call sites:
- `_executeScriptStep()` — updated directly
- `_executeScriptStepWithOverride()` — delegates to `_executeScriptStep()`, already covered
- `_executeSubStep()` (used by group) — calls `_executeScriptStepWithOverride()`, already covered
- `_executeGroupStep()` internal sub-steps — use `_executeSubStep()`, already covered

All script `run()` functions that ignore the second argument continue to work (JavaScript ignores extra arguments).

### 3.7 Example: PLAN_DRAFT → PLAN_AUDIT with Context

**PLAN_DRAFT prompt** (appended instruction):
```
After creating the plan, output the file path:
<FLOW_VARS>
  <PLAN_FILE>path/to/the/plan/file.md</PLAN_FILE>
</FLOW_VARS>
```

**PLAN_DRAFT step definition** (declares required var):
```javascript
PLAN_DRAFT: {
  type: "agent",
  prompt: "...",
  requiredVars: ["PLAN_FILE"],  // engine validates, auto-repairs if missing
  transitions: {
    created: { goto: "PLAN_AUDIT" },
    none: { goto: "ROADMAP_CHECK" },
  },
}
```

**PLAN_AUDIT prompt** (uses flow variable):
```
You are an independent plan reviewer. Review the plan at:
{PLAN_FILE}

Review dimensions: ...
```

**EXECUTE prompt** (uses same flow variable):
```
Execute the plan at: {PLAN_FILE}

Steps:
1. Read the plan file at {PLAN_FILE}
2. Skip Phases already marked [x] ...
```

**CLOSURE_VERIFY.AI_AUDIT prompt**:
```
Verify whether the plan at {PLAN_FILE} is truly complete.
```

**CLOSURE_VERIFY.SCRIPT_CHECK** (accesses flowVars):
```javascript
run: async (delegates, flowVars) => {
  const planFile = flowVars.get("PLAN_FILE") ?? throw new Error("PLAN_FILE not set in flowVars");
  execSync(`node ai-dev/tools/check-plan-checklist.mjs ${planFile} --strict`);
  return "pass";
}
```

**Important**: Script steps that depend on flow variables must guard against missing values. `flowVars.get("VAR")` returns `undefined` when the variable is not set — unlike prompt templates where `{VAR}` appears literally for visibility. Always use `?? throw new Error(...)` or an explicit null check in script steps.

### 3.8 Variable Scope and Lifecycle

Variables are **flow-scoped** (not step-scoped). Once set, they persist until:
- Overwritten by another step
- The flow ends

This is intentional: the plan file path set by PLAN_DRAFT should be available to all downstream steps.

If a foreach step runs the same sub-flow multiple times, each iteration can overwrite the same variables. This is correct behavior — each iteration works on a different plan.

### 3.9 Registered Variable Names

Per the naming convention in DESIGN-flow-dsl.md §3.2, new variables use `UPPER_SNAKE_CASE`:

| Variable | Source | Description |
|----------|--------|-------------|
| `{PLAN_FILE}` | PLAN_DRAFT output | Path to the created plan file |
| `{ACTIVE_PLANS}` | DETECT_START output | JSON array of active plan file paths |

## 4. Solution Part B: For-Each Step

### 4.1 Concept

A `foreach` step iterates over a collection and executes a sub-flow for each item. From the outside, it looks like a regular step that returns a marker.

**Concrete use case**: DETECT_START finds multiple active plans → FOREACH_PLAN processes each one → DEEP_AUDIT.

### 4.2 DSL Schema

```typescript
type ForEachStep = {
  type: "foreach",

  // Collection source — a function returning string[]
  list: (delegates: object, flowVars: Map) => string[] | Promise<string[]>,

  // Flow variable name for current item (set before each iteration)
  itemVar: string,

  // Sub-flow definition (same schema as a top-level Flow)
  body: {
    entry: string,
    steps: Record<string, StepDef>,
    maxTotalSteps?: number,
    maxCycleVisits?: number,
  },

  // Completion detection: body result.status must equal this (default: "completed")
  successStatus?: string,

  // Maximum number of items to process (default: 20, bounds worst-case execution)
  maxItems?: number,

  // Whether to continue iterating when an item fails (default: false)
  continueOnFail?: boolean,

  // Markers returned to outer flow
  onComplete: string,           // all items succeeded
  onItemFail: string,           // an item failed, continueOnFail=false

  // Outer transitions (same as any step)
  transitions: Record<string, Action>,
  onError?: Action,
}
```

**Design decision: No `onPartial` marker.** When `continueOnFail=true`, if some items fail, `onItemFail` is used. This keeps the API simple. The foreach step's `text` field contains the count (`"2/5 items failed"`) for diagnostic logging, but the marker is either `onComplete` (all succeeded) or `onItemFail` (any failed).

### 4.3 Engine Implementation

```javascript
async _executeForeachStep(stepName, stepDef) {
  const items = await stepDef.list(this.delegates, this.flowVars);
  const successStatus = stepDef.successStatus || "completed";
  const maxItems = stepDef.maxItems ?? 20;

  if (!items || items.length === 0) {
    this._log(`  foreach ${stepName}: empty list → ${stepDef.onComplete}`);
    return { ok: true, marker: stepDef.onComplete || "done", text: "empty" };
  }

  const bounded = items.slice(0, maxItems);
  if (bounded.length < items.length) {
    this._log(`  foreach ${stepName}: truncated ${items.length} → ${bounded.length} items (maxItems=${maxItems})`);
  }
  this._log(`  foreach ${stepName}: ${bounded.length} items`);
  let failCount = 0;

  for (let i = 0; i < bounded.length; i++) {
    const item = bounded[i];
    this._log(`  foreach ${stepName} [${i + 1}/${items.length}]: ${item}`);

    this.flowVars.set(stepDef.itemVar, item);

    const subEngine = new FlowEngine(stepDef.body, this.delegates);
    subEngine.flowVars = this.flowVars;

    const result = await subEngine.run();

    if (result.status !== successStatus) {
      failCount++;
      this._log(`  foreach ${stepName} [${i + 1}/${items.length}]: FAILED (${result.status})`);
      if (!stepDef.continueOnFail) {
        return {
          ok: true,
          marker: stepDef.onItemFail || "fail",
          text: `item ${item} failed: ${result.status}`,
        };
      }
    } else {
      this._log(`  foreach ${stepName} [${i + 1}/${items.length}]: OK`);
    }
  }

  const marker = failCount > 0
    ? (stepDef.onItemFail || "fail")
    : (stepDef.onComplete || "done");
  return {
    ok: true,
    marker,
    text: `${items.length - failCount}/${items.length} items succeeded`,
  };
}
```

**Key design decisions:**

1. **Sub-engine approach** — Each iteration creates a fresh `FlowEngine`. Clean visit counts, retry counts, and appendBuffers per iteration.

2. **Shared flowVars** — `flowVars` is shared by reference. The foreach step sets `itemVar` before each iteration. Body steps can read and write flowVars. Changes persist across iterations and are visible after foreach completes.

3. **Isolated engine state** — `visitCounts`, `retryCounts`, `appendBuffers`, `markerCorrectionCounts` are NOT shared. Each iteration starts clean.

4. **Completion detection** — Simple `result.status === successStatus` comparison. The body flow terminates via `done` actions, and the final `done` value becomes `result.status`. Default success status is `"completed"`.

### 4.4 Integration into Main Loop

In `engine.js` `run()`:

```javascript
} else if (stepDef.type === "foreach") {
  result = await this._executeForeachStep(currentStep, stepDef);
}
```

### 4.5 Foreach Step Interaction with Outer Flow

**Retry**: The outer flow can retry the foreach step. When retried, `list()` is called again, producing a fresh collection. This means if item processing creates new items, they'll be picked up on retry.

**Nested foreach**: The body is a standard flow and can contain `foreach` steps. No special handling needed.

**Step counting**: Each body iteration's step count contributes to the outer flow's `totalSteps` via the sub-engine's `stepCount` in its result. **Wait — actually the outer engine doesn't increment `totalSteps` for body-internal steps.** The foreach step itself counts as 1 step in the outer flow. The body's internal steps are tracked by the sub-engine's own `totalSteps`. This means the outer `maxTotalSteps` doesn't prevent runaway foreach loops.

**Mitigation**: The body has its own `maxTotalSteps`. Set it conservatively for foreach bodies. Also, the foreach step's `list()` returns a bounded collection (the script controls the list).

## 5. Goal Driver Flow Restructured

### 5.1 Current Flow (Problem)

```
DETECT_START → "execute" → HEALTH_CHECK → ... → EXECUTE (picks "first active plan")
                                         only processes 1 plan
```

### 5.2 Restructured Flow

```
DETECT_START
  ├─ "resume_plans" → FOREACH_PLAN → DEEP_AUDIT → ADVERSARIAL → [completed]
  ├─ "roadmap"      → HEALTH_CHECK → ROADMAP_CHECK → PLAN_DRAFT → PLAN_AUDIT → ...
  └─ "audit"        → HEALTH_CHECK → ... → DEEP_AUDIT → ADVERSARIAL → [completed]
```

Key changes:

1. **DETECT_START** returns `{ marker: "resume_plans", vars: { ACTIVE_PLANS: "json-array" } }` when active plans exist
2. **FOREACH_PLAN** iterates over active plans, running audit→execute→closure→build for each
3. **PLAN_DRAFT** outputs `{ PLAN_FILE }` into flowVars
4. **PLAN_AUDIT, EXECUTE, CLOSURE_VERIFY** reference `{PLAN_FILE}` from flowVars

### 5.3 DETECT_START Updated

```javascript
DETECT_START: {
  type: "script",
  run: (delegates, flowVars) => {
    const config = delegates.config;
    const output = execSync("node ai-dev/tools/check-plan-status.mjs --json", {
      cwd: config.projectRoot, encoding: "utf8", timeout: 30_000,
    });
    const status = JSON.parse(output);
    if (status.activePlans && status.activePlans.length > 0) {
      return {
        marker: "resume_plans",
        vars: { ACTIVE_PLANS: JSON.stringify(status.activePlans) },
      };
    }
    // ... existing roadmap/plan/audit detection logic
    return marker; // string, backward compatible
  },
  transitions: {
    resume_plans: { goto: "FOREACH_PLAN" },
    roadmap: { goto: "HEALTH_CHECK" },
    plan: { goto: "HEALTH_CHECK" },
    audit: { goto: "HEALTH_CHECK" },
  },
},
```

### 5.4 FOREACH_PLAN Step

```javascript
FOREACH_PLAN: {
  type: "foreach",
  list: (delegates, flowVars) => JSON.parse(flowVars.get("ACTIVE_PLANS")),
  itemVar: "PLAN_FILE",
  successStatus: "completed",
  continueOnFail: true,
  onComplete: "all_done",
  onItemFail: "some_failed",
  body: {
    entry: "PLAN_AUDIT",
    maxTotalSteps: 40,
    maxCycleVisits: 10,
    steps: {
      PLAN_AUDIT: {
        type: "agent",
        prompt: `Review the plan at {PLAN_FILE} ...`,
        transitions: {
          approved: { goto: "EXECUTE" },
          issues: { retry: "PLAN_AUDIT", maxRetries: 2, append: { template: "..." } },
        },
        onMaxRetries: { goto: "EXECUTE" },
      },
      EXECUTE: {
        type: "agent",
        prompt: `Execute the plan at {PLAN_FILE} ...`,
        transitions: {
          success: { goto: "CLOSURE_VERIFY" },
          failed: { goto: "CLOSURE_VERIFY" },
        },
      },
      CLOSURE_VERIFY: { /* group step, references {PLAN_FILE} */ },
      BUILD_VERIFY: {
        type: "agent",
        prompt: `Verify build after executing {PLAN_FILE} ...`,
        transitions: {
          pass: { done: "completed" },
          fail: { done: "build_failed" },
        },
      },
    },
  },
  transitions: {
    all_done: { goto: "DEEP_AUDIT" },
    some_failed: { goto: "ROADMAP_CHECK" },
  },
},
```

## 6. Mock Test Matrix for Foreach

| Scenario | Items | Body result per item | Expected marker |
|----------|-------|---------------------|-----------------|
| Empty list | `[]` | N/A | `onComplete` |
| Single item, succeeds | `["plan1"]` | `"completed"` | `onComplete` |
| Single item, fails | `["plan1"]` | `"build_failed"` | `onItemFail` (stops) |
| 3 items, all succeed | `["p1","p2","p3"]` | all `"completed"` | `onComplete` |
| 3 items, item 2 fails, stop | `["p1","p2","p3"]` | `"completed"`, `"failed"`, — | `onItemFail` (stops at item 2) |
| 3 items, item 2 fails, continue | `["p1","p2","p3"]` | `"completed"`, `"failed"`, `"completed"` | `onItemFail` (1/3 failed) |
| Body retries internally | `["plan1"]` | audit fails twice, passes third time → `"completed"` | `onComplete` |

## 7. Implementation Plan

### Phase 1: Flow Variables (Engine-Level)

1. Add `this.flowVars = new Map()` to FlowEngine constructor
2. Add `_extractFlowVars(text)` method
3. Update `_buildPrompt()` to merge flowVars into template substitution
4. Add `requiredVars` + `requiredVarsMaxRetries` validation with session-continue repair
5. Update `_executeScriptStep()` to handle object returns `{ marker, vars }`
6. Update `_executeScriptStep()` to pass `flowVars` as second arg to `run()`
7. Update `_executeGroupStep()` to pass flowVars through `_executeSubStep()` for script sub-steps
8. Tests: context extraction, template substitution, script return format, requiredVars validation, requiredVars repair retry

### Phase 2: Goal Driver Flow Uses Flow Variables

1. Update PLAN_DRAFT prompt to output `<FLOW_VARS><PLAN_FILE>...</PLAN_FILE></FLOW_VARS>`
2. Add `requiredVars: ["PLAN_FILE"]` to PLAN_DRAFT step definition
3. Update PLAN_AUDIT prompt to reference `{PLAN_FILE}`
4. Update EXECUTE prompt to reference `{PLAN_FILE}`
5. Update CLOSURE_VERIFY.AI_AUDIT prompt to reference `{PLAN_FILE}`
6. Update CLOSURE_VERIFY.SCRIPT_CHECK to read `flowVars.get("PLAN_FILE")`
7. Tests: verify flow variables propagate through the pipeline

### Phase 3: ForEach Step (Engine-Level)

1. Add `_executeForeachStep()` method to FlowEngine
2. Add `type === "foreach"` branch in main loop
3. Tests: all 7 scenarios in §6

### Phase 4: Goal Driver Flow Uses ForEach

1. Add `--json` output mode to check-plan-status.mjs
2. Update DETECT_START to return `{ marker, vars: { ACTIVE_PLANS } }`
3. Add FOREACH_PLAN step to goal-driver flow
4. Wire DETECT_START → resume_plans → FOREACH_PLAN → DEEP_AUDIT
5. Tests: integration test with mock plan list

## 8. Review Log

### Round 1 (2026-06-10)

**Reviewer**: Independent sub-agent

**Issues Found**:
1. ~~[Major] Document contradicts itself on foreach necessity~~ → Fixed: §1.2 now states clearly that foreach solves one concrete scenario and is generalized for reuse
2. ~~[Major] Sub-flow completion detection code was broken~~ → Fixed: §4.3 uses simple `successStatus` comparison, §4.2 removed `onPartial` complexity
3. ~~[Major] `contextVars` naming collision with existing `this.context`~~ → Fixed: Renamed to `flowVars`, §3.1 explicitly documents the distinction
4. ~~[Major] Agent context extraction has no fallback~~ → Fixed: §3.2 documents fallback behavior (unresolved variables appear as literal `{VAR}` in prompt)
5. ~~[Major] Script step `run()` signature change not clearly flagged~~ → Fixed: §3.5 enumerates all call sites
6. ~~[Minor] `source` field inconsistency~~ → Fixed: Removed, `list` is always a function
7. ~~[Minor] §4.6 flow diagram incorrect~~ → Fixed: §5 presents correct restructured flow
8. ~~[Minor] No foreach retry discussion~~ → Fixed: §4.5 discusses retry interaction
9. ~~[Minor] `onPartial` semantics ambiguous~~ → Fixed: Removed `onPartial`, simplified to two markers
10. ~~[Minor] Missing variable registration~~ → Fixed: §3.8 registers new variables

### Round 2 (2026-06-10)

**Reviewer**: Independent sub-agent (different from Round 1)

**All 10 Round 1 issues confirmed resolved.**

**New Issues Found**:
1. ~~[Major] Script step `flowVars.get()` fallback differs from template substitution fallback~~ → Fixed: §3.6 added guard pattern with `?? throw new Error(...)` and explicit warning
2. ~~[Minor] Foreach step counting allows unbounded total execution~~ → Fixed: §4.2 added `maxItems` field (default: 20), §4.3 enforces it with `items.slice(0, maxItems)`
3. ~~[Minor] `_extractFlowVars` regex cannot handle values containing `<`~~ → Fixed: §3.4 added warning log when `<CONTEXT>` block exists but yields zero variables

**Verdict**: All issues resolved.
