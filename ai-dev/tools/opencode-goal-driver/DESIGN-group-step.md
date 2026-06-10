# Group Step 设计文档

> Status: **implemented** — 已迁移至 `ai-dev/design/opencode-goal-driver/group-step-design.md`
> Last Reviewed: 2026-06-10
> Source: CLOSURE_SCRIPT_CHECK ↔ CLOSURE_AUDIT 循环的抽象需求
>
> **注意**：本文档保留作为历史参考。权威文档见 [group-step-design.md](../../../design/opencode-goal-driver/group-step-design.md)。

## 1. 动机

当前 flow 中出现了"脚本检查 → AI 审计 → 再脚本检查"的循环模式。用散落的 `goto` 跳转实现时：

1. **循环逻辑分散** — CLOSURE_SCRIPT_CHECK 和 CLOSURE_AUDIT 通过 goto 互相跳转，循环次数由各自的 maxRetries 和 visitCounts 拼凑控制
2. **无法整体限流** — 没有一个地方说"这个检查-审计循环最多跑 3 轮"
3. **无法复用** — 未来其他场景（如 BUILD_VERIFY → FIX_BUILD 循环）也有类似需求

需要一种**通用分组步骤**，将多个子步骤封装为一个黑盒，对外通过 marker 交互。

## 2. 核心原则

### 2.1 黑盒封装

Group 是一个**不透明的步骤**。从外部看，它和 agent/tool/script 步骤没有区别：

```
外部视角：
  EXECUTE → CLOSURE_VERIFY ──pass──→ BUILD_VERIFY
                      │ fail
                      └──→ EXECUTE (with remaining items)
```

Group **不知道外部步骤的存在**。它通过返回 marker 与外部交互，外部通过 `transitions` 映射 marker 到动作。

### 2.2 内部自治

Group 内部的子步骤通过 `goto` 互相跳转，但 goto 的目标只能是：

1. **同一 group 内的其他子步骤名**
2. **特殊标记**（见 2.3）

### 2.3 子步骤的 marker → 动作映射

子步骤的 `transitions` 中，每个 marker 映射到一个 action：

```typescript
type SubTransition =
  | { goto: string }           // 跳到 group 内另一个子步骤，或特殊标记
  | { exit: string }           // 退出 group，返回指定 marker
```

特殊 goto 值：

| 值 | 含义 |
|------|------|
| 子步骤名 | group 内跳转 |
| `_retry` | 本轮失败，round++，从 group 第一个子步骤重新开始 |
| `_exit_fail` | 整个 group 立即以 `fail` marker 退出（不等待 maxRounds） |

`exit` 字段：直接以指定 marker 退出 group（如 `{ exit: "pass" }`）。

### 2.4 轮次与兜底

- **round**：每执行到 group 的第一个子步骤时 round++（第一轮 round=1）
- **maxRounds**：group 级别配置，超过则触发 `onExhausted`
- **onExhausted**：轮次用尽时的退出 marker，默认 `"fail"`

## 3. DSL Schema

### 3.1 Group Step 定义

```javascript
CLOSURE_VERIFY: {
  type: "group",
  maxRounds: 3,
  onExhausted: "fail",     // 可选，默认 "fail"

  steps: {
    SCRIPT_CHECK: {
      type: "script",
      run: async (delegates) => { /* ... */ },
      transitions: {
        pass:  { exit: "pass" },        // 直接以 "pass" 退出 group
        fail:  { goto: "AI_AUDIT" },     // 跳到 group 内 AI_AUDIT
      },
    },

    AI_AUDIT: {
      type: "agent",
      prompt: "...Output <AI_STEP_RESULT>complete</AI_STEP_RESULT> or <AI_STEP_RESULT>incomplete</AI_STEP_RESULT>...",
      transitions: {
        complete:   { goto: "_retry" },  // 回到 group 开头，round++
        incomplete: { exit: "fail" },     // 以 "fail" 退出 group
      },
      onError: { exit: "fail" },
    },
  },

  // Group 外部的 transitions — 和普通步骤一样
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

### 3.2 Action 类型总结

```typescript
// 子步骤 transitions 内的 action：
type SubAction =
  | { goto: string }         // group 内跳转（步骤名 | "_retry"）
  | { exit: string }         // 以指定 marker 退出 group

// Group 外部的 transitions — 和普通步骤完全一致：
type OuterAction = Action   // goto | retry | done（见 DESIGN-flow-dsl.md §2.4）
```

### 3.3 执行语义

```
group.run():
  round = 0
  loop:
    round++
    if round > maxRounds:
      return onExhausted    // 默认 "fail"

    currentSubStep = group.steps 的第一个

    subLoop:
      result = execute(currentSubStep)
      marker = resolveMarker(result, currentSubStep)
      action = currentSubStep.transitions[marker]

      if action.exit:
        return action.exit    // 以指定 marker 退出 group

      if action.goto == "_retry":
        break subLoop        // 跳出子循环，回到外层 loop 开始新一轮

      if action.goto in group.steps:
        currentSubStep = group.steps[action.goto]
        continue subLoop

      // goto 指向不存在的子步骤 → 错误
      return "error"
```

## 4. 与现有 Engine 的集成

### 4.1 Engine 改动

在 `engine.js` 的主循环中，`step.type === "group"` 时：

```javascript
if (stepDef.type === "group") {
  const groupMarker = await this._executeGroupStep(currentStep, stepDef);
  // groupMarker 是 group 返回的 marker（如 "pass", "fail"）
  // 然后用 stepDef.transitions[groupMarker] 决定外部跳转
  marker = groupMarker;
}
```

### 4.2 子步骤执行

子步骤的执行复用现有的 `_executeAgentStep`、`_executeScriptStep` 等方法。

关键差异：
- 子步骤**不需要** append buffer（group 内部不维护 append 上下文）
- 子步骤**不需要** visitCount / maxCycleVisits 检查（由 group 的 maxRounds 控制）
- 子步骤的 onError 直接映射到 `{ exit: "fail" }` 或 `{ goto: "_retry" }`

### 4.3 日志

```
[12:00:01] [step 15] CLOSURE_VERIFY (round 1/3)
[12:00:01]   [sub] SCRIPT_CHECK → marker: fail
[12:00:01]   [sub] AI_AUDIT → marker: complete
[12:00:05] [step 15] CLOSURE_VERIFY (round 2/3)
[12:00:05]   [sub] SCRIPT_CHECK → marker: pass
[12:00:05]   group exit: pass
[12:00:05]   marker: pass → goto BUILD_VERIFY
```

## 5. CLOSURE_VERIFY 的完整定义

### 5.1 替换前后

**替换前**（散落的 goto 跳转）：

```
EXECUTE → CLOSURE_SCRIPT_CHECK ──pass──→ BUILD_VERIFY
              │ fail
              ↓
          CLOSURE_AUDIT ──complete──→ CLOSURE_SCRIPT_CHECK  (goto 回去)
              │ incomplete
              ↓
          EXECUTE (retry)
```

**替换后**（group 封装）：

```
EXECUTE → CLOSURE_VERIFY ──pass──→ BUILD_VERIFY
              │ fail (含 exit:fail 和 incomplete 两种情况)
              ↓
          EXECUTE (retry, 带 remaining items)
```

### 5.2 Group 定义

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
          execSync("node ai-dev/tools/check-plan-checklist.mjs --active-only --quiet --strict", {
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
      prompt: `You are an independent verifier — you did NOT participate in plan execution. ...`,
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
        template: "\n\nThe following items remain unfinished — continue:\n${output}",
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

### 5.3 行为说明

| 场景 | 流程 | Group 返回 marker |
|------|------|-------------------|
| 脚本检查通过 | SCRIPT_CHECK(pass) → exit | `"pass"` |
| 脚本失败，AI 修完，再检查通过 | SCRIPT_CHECK(fail) → AI_AUDIT(complete) → round 2 → SCRIPT_CHECK(pass) → exit | `"pass"` |
| AI 说做不完 | SCRIPT_CHECK(fail) → AI_AUDIT(incomplete) → exit | `"fail"` |
| 3 轮脚本都没通过 | round 1→2→3 都 fail → onExhausted | `"fail"` |
| AI 子进程被 kill | AI_AUDIT onError → exit | `"fail"` |

## 6. 未来复用场景

### 6.1 BUILD_VERIFY + FIX_BUILD 循环

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

### 6.2 PLAN_DRAFT + PLAN_AUDIT 循环

```javascript
PLAN_DRAFT_LOOP: {
  type: "group",
  maxRounds: 3,
  onExhausted: "approved",  // 降级：3 轮还没 approved 就带着问题前进
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

## 7. 实现计划

### Phase 1: Engine 扩展

1. 在 `engine.js` 中添加 `_executeGroupStep(stepName, stepDef)` 方法
2. 主循环中识别 `type: "group"`，委托给 `_executeGroupStep`
3. 处理 `exit`、`_retry` 特殊标记
4. Group 内子步骤的日志用 `[sub]` 前缀区分

### Phase 2: Flow 重构

1. 将 CLOSURE_SCRIPT_CHECK + CLOSURE_AUDIT 合并为 CLOSURE_VERIFY group
2. 删除顶层的 CLOSURE_SCRIPT_CHECK 和 CLOSURE_AUDIT 步骤
3. EXECUTE 的 transitions 改为 `success → CLOSURE_VERIFY`、`failed → CLOSURE_VERIFY`

### Phase 3: 测试

1. 添加 group step 的 mock 测试：
   - SCRIPT_CHECK 直接 pass（不触发 AI）
   - SCRIPT_CHECK fail → AI_AUDIT complete → round 2 SCRIPT_CHECK pass
   - 3 轮都 fail → onExhausted
   - AI_AUDIT incomplete → 立即 exit fail
2. 验证现有 34 个测试不回归

### Phase 4: DESIGN-flow-dsl.md 更新

1. §2.2 Step 类型表增加 `group`
2. §4.1 执行循环增加 group 分支
3. §6 完整 Flow 图更新
