# Goal Driver Flow Engine Design

> Status: v3
> Last Reviewed: 2026-06-09
> Source: ai-dev/tools/opencode-goal-driver

## 1. Motivation

Refactor hardcoded workflow into a declarative Flow DSL + generic engine. Core principles:

1. **Engine has no business logic** — all semantics in flow definition (DSL), engine only does "execute step → extract result → lookup transition"
2. **Each step is atomic** — one script call OR one AI prompt OR one subflow invocation
3. **Result-driven transitions** — step return value is looked up in transitions table to decide next step
4. **Unified fault tolerance** — each step has independent retry/degradation config

## 2. Core Concepts

### 2.1 Flow

A Flow is a **finite state machine** composed of Steps connected by Transitions.

```javascript
const flow = {
  name: "goal-driver",
  entry: "FIX_TESTS",
  maxTotalSteps: 120,
  maxCycleVisits: 20,

  markerAliases: {
    "已创建": "created", "已批准": "approved", "有问题": "issues",
    "无": "none", "完成": "complete", "已完成": "complete",
    "未完成": "incomplete", "成功": "success", "失败": "failed",
    "修复": "fixed", "无错误": "no_errors", "通过": "approved",
    "待处理": "pending", "干净": "clean", "需要": "needed", "不需要": "not_needed",
  },

  steps: { /* ... */ },
};
```

### 2.2 Step

Each Step is an atomic work unit. Four types:

| Type | Execution | Result extraction |
|------|-----------|-------------------|
| `script` | Execute JS function | Return value used directly as marker |
| `tool` | Execute bash command | `exit 0` → marker=`pass`, non-zero → marker=`fail` |
| `agent` | Spawn `opencode run` subprocess | Extract `<resultTag>value</resultTag>` from AI output |
| `subflow` | Spawn child FlowEngine | Child engine returns `complete`/`failed`/`all_complete`/`some_failed`/`all_failed` |

Step definition:

```typescript
interface Step {
  name: string;
  type: "script" | "tool" | "agent" | "subflow";

  // === Execution config ===
  run?: string | ((delegates, args?) => string | { marker: string; vars?: Record<string, any> });
  command?: string;
  prompt?: string;
  promptFile?: string;
  resultTag?: string;
  system?: string;

  // === Variable extraction from output ===
  extractVars?: Record<string, RegExp>;  // Extract structured vars from result.text

  // === Subflow config (type=subflow only) ===
  flow?: string;                         // Sub-flow name (resolved via delegates.loadSubFlow)
  forEach?: string;                      // Context var containing array to iterate
  flowArgs?: Record<string, string>;     // Template-resolved args passed to child
  onItemError?: { stopOnError?: boolean };

  // === Transitions ===
  transitions: Record<string, Action>;

  // === Fault tolerance ===
  maxRetries?: number;
  onError?: Action;
  onUnknown?: Action;
  onUnknownMaxRetries?: number;
  onMaxRetries?: Action;
}
```

#### extractVars

The `extractVars` field allows extracting structured variables from agent step output text using regex patterns. Each key is a variable name, each value is a regex with one capture group.

```javascript
{
  type: "agent",
  prompt: "create a plan",
  resultTag: "PLAN_RESULT",
  extractVars: {
    planFile: "Plan file:\\s*(ai-dev/plans/\\S+\\.md)"
  },
  transitions: { created: { goto: "RUN" } }
}
```

After step execution, extracted vars are available as `{steps.STEP_NAME.vars.varName}` in downstream templates and as `steps.STEP_NAME.vars.varName` via dot-notation traversal.

### 2.3 Action

Action describes "what to do next":

```typescript
type Action =
  | { goto: string }
  | { goto: string; append: AppendSpec }
  | { done: string }
  | { retry: string; maxRetries?: number; append?: AppendSpec }
```

**goto vs retry**:

| | goto | retry |
|---|---|---|
| Target step counting | +1 visit | +1 retry (independent of visit count) |
| Context append | Per append spec | Per append spec, feedback **accumulates** |
| Overflow handling | maxCycleVisits → terminate | maxRetries → onMaxRetries |
| Prompt assembly | Original prompt + append | Original prompt + all accumulated append |

**retry fallback chain**: `transition.maxRetries` → `stepDef.maxRetries` → `3` (default).

### 2.4 Template Variables & Context Model

Engine maintains a **context variable pool**. `{variable}` in prompts and commands are resolved at runtime.

```typescript
// Context variable sources:
// 1. Static config (delegates.vars)
{ module }              → config.moduleName

// 2. Step output results (auto-injected via _buildVars)
{ steps.STEP_NAME.text }      → step output text
{ steps.STEP_NAME.marker }    → step marker value
{ steps.STEP_NAME.ok }        → "true"/"false"
{ steps.STEP_NAME.logFile }   → log file path
{ steps.STEP_NAME.vars.X }    → extracted/script-returned var (dot-notation traversal)

// 3. Script step returned vars (flattened to top level)
// script returns { marker: "has_plans", vars: { activePlanFiles: [...] } }
{ activePlanFiles }            → [...] (array preserved)
```

**Template syntax**: `{variable}` supports dot-notation paths. Regex: `\{(\w[\w.]*)\}`. Unresolved variables are kept as-is (e.g., `{DATE}` → AI interprets).

**`_buildVars` internals**: Stores both flat keys (`steps.NAME.text`) AND nested object (`steps.NAME.vars.X`) so that dot-notation traversal works correctly.

### 2.5 Subflow

A subflow step spawns a child `FlowEngine` instance with a separate flow definition. Two modes:

**forEach mode**: Iterates over an array from context vars. Each item becomes `currentItem` + `planFile` in child vars.

```javascript
PROCESS_PENDING_PLANS: {
  type: "subflow",
  flow: "plan-lifecycle",
  forEach: "activePlanFiles",
  transitions: {
    all_complete: { goto: "NEXT" },
    some_failed:  { goto: "NEXT" },
    all_failed:   { goto: "NEXT" }
  }
}
```

**Single execution mode**: Runs one instance of the sub-flow. Passes `flowArgs` (template-resolved) to child.

```javascript
PLAN_EXECUTE: {
  type: "subflow",
  flow: "plan-lifecycle",
  flowArgs: { planFile: "{steps.PLAN_DRAFT.vars.planFile}" },
  transitions: {
    complete: { goto: "NEXT" },
    failed:   { goto: "NEXT" }
  }
}
```

**Child delegate inheritance**: `_buildChildDelegates` merges parent's `_buildVars()` context + `delegates.vars` + `flowArgs` + `itemContext`. For forEach, `currentItem` and `planFile` are explicitly set from the array item.

**Sub-flow loading**: Dynamic via `delegates.loadSubFlow(name)`. The engine caches results in the caller. No pre-registration required.

**forEach result markers**:
| Condition | Marker |
|-----------|--------|
| All items completed | `all_complete` |
| Some failed, some succeeded | `some_failed` |
| All failed | `all_failed` |
| Empty array | `all_complete` (no work) |

**Single execution result markers**:
| Condition | Marker |
|-----------|--------|
| Child completed | `complete` |
| Child failed | `failed` |

### 2.6 AppendSpec

```typescript
type AppendSpec =
  | true
  | string
  | { template: string }
  | { extract: string; template: string }
```

### 2.7 Result Extraction & Marker Aliases

**agent step extraction chain**:

```
AI output → find <resultTag>tag</resultTag>
         → not found → spawn parse agent
         → not found → use onUnknown action

Found marker → lookup transitions table
           → no match → try markerAliases
           → no match → try case-insensitive
           → no match → session correction (up to onUnknownMaxRetries times)
           → no match → use onUnknown action
```

**markerAliases** is a flow-level config for fault tolerance. AI might return Chinese text like "已创建" instead of "created" — alias mapping lets the engine understand both.

## 3. Fault Tolerance

### 3.1 Error Classification

| Error type | Trigger | Handling |
|-----------|---------|---------|
| **subprocess killed** | agent step `ok=false` | `onError` action |
| **execution exception** | try/catch caught | `onError` action |
| **marker extraction failed** | No resultTag in output | parse agent → `onUnknown` |
| **marker not in transitions** | Unexpected AI value | alias → case-insensitive → correction → `onUnknown` |
| **retry exhausted** | retry count > maxRetries | `onMaxRetries` action |
| **too many cycles** | visit count > maxCycleVisits | Return `max_cycles` |
| **too many steps** | totalSteps > maxTotalSteps | Return `max_total_steps` |

### 3.2 Result Success Markers

| Step type | Success | Failure |
|----------|---------|---------|
| tool | exit 0 → `pass` | exit ≠ 0 → `fail` (normal marker, NOT onError) |
| script | Function returns → return value as marker | Function throws → onError |
| agent | `ok=true` → extract marker from output | `ok=false` (killed) → onError |
| subflow | Child `completed` → `complete`/`all_complete` | Child failed → `failed`/`some_failed`/`all_failed` |

### 3.3 Retry Mechanism

```
Step executes → marker hits retry action
  → compute retryKey = "fromStep→targetStep"
  → retryCount++
  → if > maxRetries → execute onMaxRetries
  → else → append feedback to target step's appendBuffer → goto target step
```

**Prompt assembly on retry**:

```
[Original prompt]
                              ← 1st append (if 2nd+ retry)
──────────────               ← separator (inserted on 2nd+ retry)
[2nd append]                 ← newly appended feedback
```

### 3.4 Commit Error → Agent Fix Pattern

Both main flow and sub-flow use the same pattern for git commit errors:

```
COMMIT (script: gitCommit --no-verify)
  → committed → next step
  → nothing → next step (skip)
  → error → COMMIT_FIX (agent: fix conflicts/lint/secrets using nop-git-master)
              → fixed → next step
              → skipped → next step
              → onError → next step (graceful)
```

This pattern is critical: git hooks (lint, secrets detection) can reject commits. The agent fallback handles these gracefully without terminating the flow.

## 4. Engine Execution Loop

```mermaid
flowchart TD
    START([run entry]) --> INIT["currentStep = entry<br/>totalSteps = 0"]
    INIT --> LOOP{totalSteps<br/>< maxTotalSteps?}

    LOOP -->|no| MAX_STEPS["return max_total_steps"]
    LOOP -->|yes| FIND["stepDef = steps[currentStep]"]

    FIND --> STEP_EXISTS{stepDef<br/>exists?}
    STEP_EXISTS -->|no| UNKNOWN["return unknown_step"]
    STEP_EXISTS -->|yes| VISIT["visitCount++<br/>totalSteps++"]

    VISIT --> CYCLE{visitCount<br/>gt maxCycleVisits?}
    CYCLE -->|yes| MAX_CYCLES["return max_cycles"]
    CYCLE -->|no| EXECUTE["result = executeStep()"]

    EXECUTE --> CATCH{exception?}
    CATCH -->|yes| ON_ERROR["→ onError action"]
    CATCH -->|no| EXTRACT["extractVars(stepDef, result)<br/>context[currentStep] = result"]

    EXTRACT --> OK_CHECK{result.ok == false<br/>AND not tool?}
    OK_CHECK -->|yes| ON_ERROR
    OK_CHECK -->|no| MARKER["marker = resolveMarker()"]

    MARKER --> MARKER_FOUND{marker<br/>found?}
    MARKER_FOUND -->|no| ON_UNKNOWN["→ onUnknown action"]
    MARKER_FOUND -->|yes| TRANS["transition = findTransition()"]

    TRANS --> TRANS_FOUND{transition<br/>found?}
    TRANS_FOUND -->|no| CORRECT["alias → case-insensitive<br/>→ session correction"]
    CORRECT --> TRANS_FOUND

    TRANS_FOUND -->|yes| DISPATCH{transition type?}
    DISPATCH -->|done| DONE(["return status"])
    DISPATCH -->|retry| RETRY["handleRetry() → goto"]
    DISPATCH -->|goto| GOTO["handleGoto() → goto"]

    RETRY --> LOOP
    GOTO --> LOOP

    style START fill:#4a9eff,color:#fff
    style DONE fill:#2d9c2d,color:#fff
    style MAX_STEPS fill:#d32f2f,color:#fff
    style MAX_CYCLES fill:#d32f2f,color:#fff
    style UNKNOWN fill:#d32f2f,color:#fff
```

## 5. File Organization

```
ai-dev/tools/opencode-goal-driver/
├── src/
│   ├── main.js                    # CLI entry: parse args, create flow + runner, start engine
│   ├── config.js                  # Config: module directory discovery
│   ├── engine.js                  # Generic FlowEngine (no business logic)
│   ├── scripts.js                 # Business logic functions (checkPendingPlans, gitCommit, etc.)
│   ├── mock-responses.js          # Mock agent responses (test/dry-run mode)
│   ├── opencode-runner.js         # opencode CLI wrapper (real execution + mock mode)
│   ├── process-executor.js        # Low-level process spawn + fd redirect + watchdog
│   ├── goal-driver-flow.json      # Main flow definition (DSL)
│   └── plan-lifecycle-flow.json   # Reusable plan lifecycle sub-flow
├── prompts/                       # Prompt files (loaded at runtime)
│   ├── fix-tests.md
│   ├── roadmap-check.md
│   ├── plan-draft.md
│   ├── plan-audit.md
│   ├── execute-plan.md
│   ├── closure-audit-v2.md
│   ├── needs-deep-audit.md
│   ├── execute-pending-plan.md
│   └── closure-audit.md
├── test/
│   └── engine.test.js             # 70 tests
└── DESIGN-flow-dsl.md             # This file
```

### Responsibility Matrix

| File | Responsibility | Business logic? |
|------|---------------|----------------|
| engine.js | Generic FSM executor | No |
| scripts.js | Business logic functions | **Yes** |
| goal-driver-flow.json | Flow definition | **Yes** |
| plan-lifecycle-flow.json | Sub-flow definition | **Yes** |
| opencode-runner.js | opencode CLI wrapper | No |
| process-executor.js | Process spawn + watchdog | No |
| config.js | Parameter parsing + module discovery | No |
| mock-responses.js | Mock responses for test mode | No |
| main.js | Glue code | No |

## 6. Flow Diagram

### 6.1 Main Flow (goal-driver-flow.json)

```mermaid
flowchart TD
    FIX_TESTS["🔧 FIX_TESTS<br/><i>agent: fix-tests.md</i>"]

    FIX_TESTS -->|fixed| FIX_TESTS_COMMIT["📦 FIX_TESTS_COMMIT<br/><i>script: gitCommit</i>"]
    FIX_TESTS -->|no_errors| CHECK_PENDING_PLANS
    FIX_TESTS -->|failed| FIX_TESTS_RECOVERY["🔧 FIX_TESTS_RECOVERY<br/><i>agent: conservative fix</i>"]
    FIX_TESTS -->|onError| FIX_TESTS

    FIX_TESTS_COMMIT -->|committed| CHECK_PENDING_PLANS
    FIX_TESTS_COMMIT -->|nothing| CHECK_PENDING_PLANS
    FIX_TESTS_COMMIT -->|error| FIX_TESTS_COMMIT_FIX["🤖 FIX_TESTS_COMMIT_FIX<br/><i>agent: nop-git-master</i>"]
    FIX_TESTS_COMMIT_FIX -->|fixed/skipped| CHECK_PENDING_PLANS
    FIX_TESTS_COMMIT_FIX -->|onError| CHECK_PENDING_PLANS

    FIX_TESTS_RECOVERY -->|fixed| CHECK_PENDING_PLANS
    FIX_TESTS_RECOVERY -->|failed| DONE_FAIL["❌ tests_failed"]

    CHECK_PENDING_PLANS["📋 CHECK_PENDING_PLANS<br/><i>script: checkPendingPlans</i>"]
    CHECK_PENDING_PLANS -->|has_plans| PROCESS_PENDING_PLANS
    CHECK_PENDING_PLANS -->|no_plans| ROADMAP_CHECK

    PROCESS_PENDING_PLANS["🔄 PROCESS_PENDING_PLANS<br/><i>subflow: plan-lifecycle × forEach</i>"]
    PROCESS_PENDING_PLANS -->|all_complete/some_failed| ROADMAP_CHECK

    ROADMAP_CHECK["🗺️ ROADMAP_CHECK<br/><i>agent: roadmap-check.md</i>"]
    ROADMAP_CHECK -->|pending| PLAN_DRAFT
    ROADMAP_CHECK -->|complete| NEEDS_DEEP_AUDIT
    ROADMAP_CHECK -->|onError| NEEDS_DEEP_AUDIT

    PLAN_DRAFT["📝 PLAN_DRAFT<br/><i>agent: plan-draft.md<br/>extractVars: planFile</i>"]
    PLAN_DRAFT -->|created| PLAN_EXECUTE
    PLAN_DRAFT -->|none| NEEDS_DEEP_AUDIT

    PLAN_EXECUTE["▶️ PLAN_EXECUTE<br/><i>subflow: plan-lifecycle<br/>flowArgs: planFile</i>"]
    PLAN_EXECUTE -->|complete/failed| NEEDS_DEEP_AUDIT

    NEEDS_DEEP_AUDIT["🔍 NEEDS_DEEP_AUDIT<br/><i>agent: needs-deep-audit.md</i>"]
    NEEDS_DEEP_AUDIT -->|needed| DEEP_AUDIT
    NEEDS_DEEP_AUDIT -->|not_needed| DONE_OK["✅ completed"]
    NEEDS_DEEP_AUDIT -->|onError| DONE_OK

    DEEP_AUDIT["🔬 DEEP_AUDIT<br/><i>agent: deep-audit skill</i>"]
    DEEP_AUDIT -->|issues/clean| ADVERSARIAL
    DEEP_AUDIT -->|onError| ADVERSARIAL

    ADVERSARIAL["⚔️ ADVERSARIAL<br/><i>agent: adversarial-review skill</i>"]
    ADVERSARIAL -->|issues| AUDIT_PLAN_DRAFT
    ADVERSARIAL -->|clean| DONE_OK
    ADVERSARIAL -->|onError| DONE_OK

    AUDIT_PLAN_DRAFT["📝 AUDIT_PLAN_DRAFT<br/><i>agent: inline<br/>extractVars: planFile</i>"]
    AUDIT_PLAN_DRAFT -->|created| AUDIT_PLAN_EXECUTE
    AUDIT_PLAN_DRAFT -->|none| FIX_TESTS

    AUDIT_PLAN_EXECUTE["▶️ AUDIT_PLAN_EXECUTE<br/><i>subflow: plan-lifecycle<br/>flowArgs: planFile</i>"]
    AUDIT_PLAN_EXECUTE -->|complete/failed| FIX_TESTS

    style FIX_TESTS fill:#4a9eff,color:#fff
    style DONE_OK fill:#2d9c2d,color:#fff
    style DONE_FAIL fill:#d32f2f,color:#fff
    style PROCESS_PENDING_PLANS fill:#7c4dff,color:#fff
    style PLAN_EXECUTE fill:#7c4dff,color:#fff
    style AUDIT_PLAN_EXECUTE fill:#7c4dff,color:#fff
```

### 6.2 Plan Lifecycle Sub-flow (plan-lifecycle-flow.json)

```mermaid
flowchart TD
    CHECK_STATUS["📋 CHECK_STATUS<br/><i>script: readPlanStatus</i>"]
    CHECK_STATUS -->|draft/active| AUDIT
    CHECK_STATUS -->|reviewed| EXECUTE
    CHECK_STATUS -->|completed| DONE["✅ completed"]

    AUDIT["🔍 AUDIT<br/><i>agent: plan-audit.md</i>"]
    AUDIT -->|approved| SET_REVIEWED
    AUDIT -->|issues| AUDIT_RETRY["🔄 retry AUDIT<br/><i>max 3, append feedback</i>"]
    AUDIT_RETRY --> AUDIT
    AUDIT_RETRY -->|onMaxRetries| SET_REVIEWED
    AUDIT -->|onError| AUDIT_RETRY2["🔄 retry AUDIT<br/><i>max 2</i>"]
    AUDIT_RETRY2 --> AUDIT
    AUDIT_RETRY2 -->|onMaxRetries| SET_REVIEWED

    SET_REVIEWED["✏️ SET_REVIEWED<br/><i>script: setPlanStatus → reviewed</i>"]
    SET_REVIEWED -->|ok/error| EXECUTE

    EXECUTE["▶️ EXECUTE<br/><i>agent: execute-plan.md</i>"]
    EXECUTE -->|success| CLOSURE
    EXECUTE -->|failed| CLOSURE
    EXECUTE -->|onError| CLOSURE

    CLOSURE["🔎 CLOSURE<br/><i>agent: closure-audit-v2.md</i>"]
    CLOSURE -->|complete| SET_COMPLETED
    CLOSURE -->|incomplete| EXECUTE_RETRY["🔄 retry EXECUTE<br/><i>max 5, append REMAINING block</i>"]
    EXECUTE_RETRY --> EXECUTE
    EXECUTE_RETRY -->|onMaxRetries| SET_COMPLETED
    CLOSURE -->|onError| EXECUTE_RETRY2["🔄 retry EXECUTE<br/><i>max 3</i>"]
    EXECUTE_RETRY2 --> EXECUTE
    EXECUTE_RETRY2 -->|onMaxRetries| SET_COMPLETED

    SET_COMPLETED["✏️ SET_COMPLETED<br/><i>script: setPlanStatus → completed</i>"]
    SET_COMPLETED -->|ok/error| COMMIT

    COMMIT["📦 COMMIT<br/><i>script: gitCommit</i>"]
    COMMIT -->|committed| UPDATE_ROADMAP
    COMMIT -->|nothing| UPDATE_ROADMAP
    COMMIT -->|error| COMMIT_FIX

    COMMIT_FIX["🤖 COMMIT_FIX<br/><i>agent: nop-git-master</i>"]
    COMMIT_FIX -->|fixed| UPDATE_ROADMAP
    COMMIT_FIX -->|skipped| UPDATE_ROADMAP
    COMMIT_FIX -->|onError| UPDATE_ROADMAP

    UPDATE_ROADMAP["🗺️ UPDATE_ROADMAP<br/><i>script: updateRoadmap</i>"]
    UPDATE_ROADMAP -->|updated/skipped/error| DONE

    style CHECK_STATUS fill:#4a9eff,color:#fff
    style DONE fill:#2d9c2d,color:#fff
    style COMMIT_FIX fill:#ff9800,color:#fff
```

### 6.3 Commit Error → Agent Fix Pattern

```mermaid
flowchart LR
    COMMIT["📦 COMMIT<br/><i>gitCommit --no-verify</i>"]
    COMMIT -->|committed| NEXT["→ next step"]
    COMMIT -->|nothing| NEXT
    COMMIT -->|error| FIX["🤖 COMMIT_FIX<br/><i>agent fallback</i>"]
    FIX -->|fixed| NEXT
    FIX -->|skipped| NEXT
    FIX -->|onError| NEXT

    style COMMIT fill:#4a9eff,color:#fff
    style FIX fill:#ff9800,color:#fff
    style NEXT fill:#2d9c2d,color:#fff
```

### 6.4 Error Recovery Scenarios

**Test fix failed → recovery**:
```mermaid
flowchart LR
    A[FIX_TESTS] -->|failed| B[FIX_TESTS_RECOVERY]
    B -->|fixed| C[CHECK_PENDING_PLANS]
    B -->|failed| D[❌ tests_failed]
```

**Pending plans loop (subflow)**:
```mermaid
flowchart LR
    A[CHECK_PENDING_PLANS] -->|has_plans| B["PROCESS_PENDING_PLANS<br/>(subflow forEach)"]
    B -->|all_complete| C[ROADMAP_CHECK]
```

**Plan audit retry exhaustion → degraded execution**:
```mermaid
flowchart LR
    A[AUDIT] -->|issues ×3| A
    A -.->|onMaxRetries| B[SET_REVIEWED] --> C[EXECUTE]
```

**Audit findings → plan → back to FIX_TESTS**:
```mermaid
flowchart LR
    A[ADVERSARIAL] -->|issues| B[AUDIT_PLAN_DRAFT] --> C["AUDIT_PLAN_EXECUTE<br/>(subflow)"] --> D[FIX_TESTS]
```

## 7. Script Functions

| Function | File | Returns |
|----------|------|---------|
| `checkPendingPlans(delegates)` | scripts.js | `{ marker: "has_plans"/"no_plans", vars: { activePlanFiles: [...] } }` |
| `readPlanStatus(delegates)` | scripts.js | `{ marker: status, vars: { planFile, planStatus } }` |
| `setPlanStatus(delegates, args)` | scripts.js | `{ marker: "ok"/"error" }` — validates Plan Status field exists |
| `updateRoadmap(delegates)` | scripts.js | `{ marker: "updated"/"skipped"/"error" }` |
| `gitCommit(delegates, args)` | scripts.js | `{ marker: "committed"/"nothing"/"error" }` |

## 8. Module Compatibility

Engine uses `config.js` `findModuleDir()` for module directory discovery:

- Top-level module (e.g., `nop-stream`): `{projectRoot}/nop-stream/`
- Nested module (e.g., `nop-ai-agent`): `{projectRoot}/nop-ai/nop-ai-agent/` (searches one level of subdirectories)
- Manual override: `--module-dir nop-ai/nop-ai-agent`

Maven `-pl` uses artifactId (e.g., `nop-ai-agent`), Maven reactor resolves nested modules.
