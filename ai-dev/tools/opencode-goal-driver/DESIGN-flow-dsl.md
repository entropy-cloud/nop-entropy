# Goal Driver Flow Engine 设计文档

> Status: draft
> Last Reviewed: 2026-06-08
> Source: ai-dev/tools/opencode-goal-driver 重构需求

## 1. 动机

当前 `workflow.js` 用硬编码的 if/else + for 循环编排整个工作流。随着需求增长（开发循环、审计循环、计划拟制自动审计、错误恢复），代码变得越来越复杂且难以扩展。

核心问题：

1. **流程逻辑与执行逻辑耦合** — 添加一个步骤需要同时修改 prompts.js 和 workflow.js 的控制流
2. **重复模式未抽象** — "run step → extract marker → branch" 在代码中重复出现 20+ 次
3. **无法表达重试+上下文追加** — 当前只有 execute-loop 支持 remainingXml 追加，其他步骤失败只能跳过或终止
4. **计划拟制缺少审计闭环** — plan guide 明确要求"写初稿 → 子 agent 对抗性审查 → 修复 → 再审查"，但 driver 没有实现这个迭代

目标：用一个声明式的 **Flow DSL** 描述工作流，一个通用的 **Flow Engine** 执行它。

## 2. 核心概念

### 2.1 Flow

一个 Flow 是一个**有限状态机**：由若干 Step 组成，Step 之间通过 Transition 连接。Engine 从入口 Step 开始，按 Transition 规则逐步执行，直到到达终态。

```
┌─────────┐    marker="pass"    ┌─────────┐    marker="pending"   ┌──────────┐
│  BUILD  │ ──────────────────→ │ ROADMAP │ ──────────────────→  │ PLAN     │
│ (tool)  │                     │ (agent) │                       │ (agent)  │
└─────────┘                     └─────────┘                       └──────────┘
   │ marker="fail"                  │ marker="complete"               │
   ↓                                ↓                                ↓
┌─────────┐                    [dev loop done]               ┌──────────┐
│ FIX     │                                                 │ PLAN     │
│ (agent) │                                                 │ AUDIT    │
└─────────┘                                                 │ (agent)  │
                                                            └──────────┘
```

### 2.2 Step

每个 Step 是一个原子工作单元。有三种类型：

| 类型 | 说明 | 输入 | 输出 |
|------|------|------|------|
| `agent` | 启动 opencode 子 agent | prompt（字符串模板） | AI 输出文本，从中提取 marker |
| `tool` | 执行 bash 命令 | command 模板 | exit code (0=pass, 非0=fail) |
| `script` | 执行 JS 函数 | config 对象 | 直接返回 marker 值 |

### 2.3 Marker & Transition

Agent step 的输出中包含 XML 标签（如 `<PLAN_RESULT>created</PLAN_RESULT>`），Engine 提取标签值作为 **marker**。

每个 Step 定义一个 `transitions` 映射：`marker → action`。

### 2.4 Action 类型

```typescript
type Action =
  | { goto: string }                          // 跳转到指定 step
  | { goto: string; append: AppendSpec }      // 跳转并追加上下文到目标 step 的 prompt
  | { done: string }                          // 终止 flow，返回 status
  | { retry: string; maxRetries: number }     // 重试指定 step（带上下文追加）
```

`append` 和 `retry` 的核心区别：
- `append`：目标 step 是首次执行，但它的 prompt 会被修改（拼接当前 step 的输出）
- `retry`：目标 step 已经执行过，重新执行时 prompt 包含之前所有迭代的累积反馈

### 2.5 Context & 上下文追加

Flow 执行过程中维护一个 **context bag**，每个 step 的输出可以存入 context，后续 step 可以引用。

```typescript
type AppendSpec =
  | true                          // 追加上一步的完整输出
  | string                        // 追加指定 context key 的内容
  | { from: string; template: string }  // 用模板格式化指定 context key
```

示例：

```javascript
// PLAN_AUDIT 发现 issues → retry PLAN_DRAFT，并追加审计反馈
{
  marker: "issues",
  action: { retry: "PLAN_DRAFT", append: { from: "PLAN_AUDIT", template: 
    "\n\n以下是计划审计的反馈，请据此修改计划：\n${output}" } }
}
```

Engine 拼接后的 PLAN_DRAFT prompt：

```
[原始 PLAN_DRAFT prompt]

以下是计划审计的反馈，请据此修改计划：
<AUDIT_RESULT>issues</AUDIT_RESULT>
<ISSUES>
  <item severity="Blocker">Exit Criteria 不可验证：...</item>
  <item severity="Major">引用的文件路径不存在：...</item>
</ISSUES>
```

### 2.6 Evidence

当 step 的 transition 包含 `evidence: true` 时，Engine 自动将 step 的输出记录到指定位置。

对于计划相关的 step，evidence 写入 plan 文件的 `## Draft Audit Evidence` 段落。这样工具脚本可以自动检查：

```bash
node ai-dev/tools/check-plan-audit.mjs <plan-file>   # 检查 draft audit evidence 存在且结果为 approved
node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict  # 已有，检查 closure
```

## 3. Flow DSL Schema

### 3.1 JavaScript 表示

```javascript
const flow = {
  name: "goal-driver",

  // 全局循环限制
  maxTotalSteps: 100,      // 任何 flow 执行不超过 100 个 step
  maxCycleVisits: 10,      // 同一个 step 不超过 10 次访问（防无限循环）

  // 入口 step 的 marker 决定从哪里开始
  entry: "DETECT_START",

  steps: {
    DETECT_START: {
      type: "script",
      run: (ctx) => detectStartPhase(ctx.config),
      transitions: {
        execute:  { goto: "HEALTH_CHECK" },
        roadmap:  { goto: "HEALTH_CHECK" },
        plan:     { goto: "HEALTH_CHECK" },
        audit:    { goto: "HEALTH_CHECK" },
      },
    },

    HEALTH_CHECK: {
      type: "tool",
      command: "./mvnw clean install -pl {module} -am -T 1C",
      timeout: 1_800_000,
      transitions: {
        pass: { goto: "ROADMAP_CHECK" },
        fail: { goto: "FIX_BUILD" },
      },
      onError: { goto: "FIX_BUILD" },    // 子进程被 kill
    },

    FIX_BUILD: {
      type: "agent",
      prompt: "修复模块 {module} 的编译错误...",
      resultTag: "HEALTH_STATUS",
      transitions: {
        fixed:  { goto: "ROADMAP_CHECK" },
        failed: { done: "failed" },
      },
      onError: { done: "failed" },
    },

    ROADMAP_CHECK: {
      type: "agent",
      prompt: "请检查模块 {module} 的路线图未实现项...",
      resultTag: "ROADMAP_RESULT",
      transitions: {
        pending:  { goto: "PLAN_DRAFT", append: true },
        complete: { goto: "DEEP_AUDIT" },   // dev loop 退出，进入审计循环
      },
      onError: { goto: "DEEP_AUDIT" },      // roadmap check 失败 → 跳过开发，直接审计
    },

    PLAN_DRAFT: {
      type: "agent",
      prompt: "请为模块 {module} 拟定开发计划...",
      resultTag: "PLAN_RESULT",
      transitions: {
        created: { goto: "PLAN_AUDIT" },
        none:    { goto: "ROADMAP_CHECK" },  // 无需计划，重新检查路线图
      },
      onError: { goto: "ROADMAP_CHECK" },    // plan draft 失败 → 回到路线图检查
    },

    PLAN_AUDIT: {
      type: "agent",
      prompt: "你是一个独立的计划审查者。请审查刚刚创建的计划...",
      resultTag: "AUDIT_RESULT",
      transitions: {
        approved: { goto: "PLAN_AUDIT_EVIDENCE" },
        issues:   { retry: "PLAN_DRAFT", append: {
          from: "PLAN_AUDIT",
          template: "\n\n以下是计划审查的反馈，请据此修改计划：\n${output}"
        }},
      },
      onError: { retry: "PLAN_DRAFT" },      // audit agent 被 kill → 重试 draft
      maxRetries: 3,                          // PLAN_DRAFT↔PLAN_AUDIT 循环最多 3 轮
    },

    PLAN_AUDIT_EVIDENCE: {
      type: "tool",
      command: "node ai-dev/tools/record-plan-audit.mjs {latest_plan}",
      transitions: {
        pass: { goto: "EXECUTE" },
        fail: { retry: "PLAN_DRAFT" },        // 写入失败（不应发生），重试
      },
    },

    EXECUTE: {
      type: "agent",
      prompt: "请执行模块 {module} 的未完成计划...",
      resultTag: "EXECUTE_RESULT",
      transitions: {
        success: { goto: "CLOSURE_AUDIT" },
        failed:  { goto: "CLOSURE_AUDIT" },   // 即使 failed 也做 closure audit 看实际进度
      },
      onError: { goto: "CLOSURE_AUDIT" },
    },

    CLOSURE_AUDIT: {
      type: "agent",
      prompt: "你是一个独立验证者...",
      resultTag: "CLOSURE_RESULT",
      transitions: {
        complete:   { goto: "BUILD_VERIFY" },
        incomplete: { retry: "EXECUTE", append: {
          from: "CLOSURE_AUDIT",
          extract: "REMAINING",  // 从输出中提取 <REMAINING>...</REMAINING> 块
          template: "\n\n以下是未完成项，请继续：\n${extracted}"
        }},
      },
      onError: { retry: "EXECUTE", append: {
        template: "\n\nclosure audit 子进程被 kill，请重新检查计划 Exit Criteria"
      }},
      maxRetries: 5,
    },

    BUILD_VERIFY: {
      type: "tool",
      command: "./mvnw clean install -pl {module} -am -T 1C",
      timeout: 1_800_000,
      transitions: {
        pass: { goto: "ROADMAP_CHECK" },      // dev loop: 回到路线图检查
        fail: { retry: "EXECUTE" },            // 构建失败 → 重新执行修复
      },
      onError: { retry: "EXECUTE" },
    },

    // ── 审计循环 ──
    DEEP_AUDIT: {
      type: "agent",
      prompt: "请对模块 {module} 执行深度审计...",
      resultTag: "AUDIT_RESULT",
      transitions: {
        issues: { goto: "PLAN_DRAFT" },        // 审计发现问题 → 拟定修复计划
        clean:  { goto: "ADVERSARIAL" },
      },
      onError: { goto: "PLAN_DRAFT" },         // 被 kill 视为有问题
    },

    ADVERSARIAL: {
      type: "agent",
      prompt: "请对模块 {module} 执行对抗性审查...",
      resultTag: "ADVERSARIAL_RESULT",
      transitions: {
        issues: { goto: "PLAN_DRAFT" },
        clean:  { done: "completed" },          // 审计循环无问题 → 完成
      },
      onError: { done: "completed" },           // 被 kill 不视为有问题（保守策略）
    },
  },
};
```

### 3.2 Template 变量

所有 prompt 和 command 中可以使用 `{variable}` 模板变量，在运行时从 context 替换：

| 变量 | 来源 |
|------|------|
| `{module}` | config.moduleName |
| `{projectRoot}` | config.projectRoot |
| `{latest_plan}` | Engine 自动检测最新活跃计划文件路径 |
| `{prev_output}` | 上一步的完整输出（当 append=true 时自动可用） |

## 4. Engine 行为

### 4.1 执行循环

```
1. 初始化 context bag，设 entry step 为当前 step
2. while (当前 step 不是终态)：
   a. 检查 step 访问次数 ≤ maxCycleVisits，否则返回 "max_cycles"
   b. 根据 step.type 执行：
      - agent: 拼接 prompt（含 append 的上下文），spawn opencode run，提取 marker
      - tool:  替换 command 模板变量，spawn bash，取 exit code
      - script: 调用 JS 函数，取返回值
   c. 将 step 输出存入 context bag（以 step name 为 key）
   d. 在 transitions 中查找 marker 对应的 action
   e. 执行 action：
      - goto: 设目标 step 为当前 step
      - retry: 增加重试计数，设目标 step 为当前 step，标记需要 append
      - done: 返回最终结果
3. 返回结果
```

### 4.2 Retry 机制

`retry` action 与 `goto` 的区别：

| 行为 | goto | retry |
|------|------|-------|
| 目标 step 计数 | +1 visit | +1 retry（独立计数） |
| 上下文追加 | 按 append spec | 按 append spec |
| 超限处理 | maxCycleVisits → "max_cycles" | maxRetries → onError or "max_retries" |
| prompt 拼接 | 原始 prompt + append | 原始 prompt + 所有历史 append 累积 |

Retry 的 prompt 拼接示例（PLAN_DRAFT 被 retry 3 次）：

```
第 1 次执行 PLAN_DRAFT：
  [原始 prompt]

第 2 次执行（第 1 次审计反馈）：
  [原始 prompt]
  以下是计划审查的反馈，请据此修改计划：
  <AUDIT_RESULT>issues</AUDIT_RESULT>...

第 3 次执行（第 1+2 次审计反馈）：
  [原始 prompt]
  以下是第 1 轮审查反馈：...
  ───────────────
  以下是第 2 轮审查反馈：...
```

**为什么不是 resume 同一个 opencode session？**

`opencode run` 是 one-shot 模式：接收 prompt，返回输出。不支持对话式交互。

retry 的效果等价于"把之前的对话历史拼接到新 prompt 中"。对于计划拟制这种迭代场景，AI agent 能看到所有历史反馈，效果接近真实的多轮对话。

如果未来 opencode 支持 `--session resume` 模式，Engine 可以切换到真正的 session resume 而不改变 DSL 定义。

### 4.3 onError 处理

每个 step 可以定义 `onError`，当：
- agent step 的子进程被 kill（ok === false）
- tool step 执行异常

onError 的值是一个 action（goto / retry / done），默认行为是 `{ done: "failed" }`。

### 4.4 marker 提取失败

当 agent step 的输出中找不到 resultTag 对应的 XML 标签时，Engine 会：
1. spawn 一个轻量 "parse" agent，让其阅读 AI 输出并推断 marker 值
2. 如果 parse agent 也无法提取，使用 step 的 `onUnknown` action，默认 `{ done: "failed" }`

### 4.5 全局安全网

- `maxTotalSteps`: 防止因 DSL 定义错误导致的无限跳转
- `maxCycleVisits`: 防止同一个 step 被无限访问（如 goto 循环）
- 每个 step 的 `maxRetries`: 防止 retry 循环无限迭代

## 5. 计划拟制的自动审计闭环

### 5.1 流程

```
PLAN_DRAFT ──created──→ PLAN_AUDIT ──approved──→ PLAN_AUDIT_EVIDENCE ──→ EXECUTE
     ↑                      │
     │                      │ issues
     └──── retry ───────────┘
```

### 5.2 与 plan guide 的关系

Plan guide (ai-dev/plans/00-plan-authoring-and-execution-guide.md §322-393) 明确要求：

> Plan 不是写完就定稿的。写初稿 → 子 agent 审查 → 修改 → 再审查，直到所有维度的问题都被解决。

Engine 的 PLAN_DRAFT → PLAN_AUDIT → retry 循环**自动执行**了这个迭代流程。

### 5.3 PLAN_AUDIT 的 prompt 要点

PLAN_AUDIT step 的 prompt 应包含：

1. **引用 plan guide 的审查维度**：想象性分析、格式完整性、内容合理性、引用准确性
2. **要求结构化输出**：每条发现包含 severity (Blocker/Major/Minor)
3. **明确的通过标准**：无 Blocker 且无 Major 时才算 approved

### 5.4 Draft Audit Evidence

当 PLAN_AUDIT 返回 `approved` 时，PLAN_AUDIT_EVIDENCE step 执行：

```bash
node ai-dev/tools/record-plan-audit.mjs <plan-file>
```

该工具在 plan 文件中写入：

```markdown
## Draft Audit Evidence

- Auditor: automated plan-audit sub-agent (flow engine)
- Date: 2026-06-08
- Result: approved
- Iterations: 2 (初稿 + 1 轮修复)
- Findings resolved:
  - [Major] Exit Criteria 不可验证 → 已改为具体文件路径和断言
  - [Minor] 引用路径拼写错误 → 已修正
```

### 5.5 工具检查

新增工具 `check-plan-audit.mjs`：

```bash
node ai-dev/tools/check-plan-audit.mjs <plan-file>
# 退出码 0 = draft audit evidence 存在且结果为 approved
# 退出码 1 = 缺少 draft audit evidence 或结果不是 approved
```

已有的工具保持不变：

```bash
node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict
# 检查 closure audit evidence 和所有 checklist
```

## 6. 完整 Flow 定义：Goal Driver

```
┌──────────────────────────────────────────────────────────────────┐
│                        Goal Driver Flow                          │
│                                                                  │
│  DETECT_START ──→ HEALTH_CHECK ──pass──→ ROADMAP_CHECK          │
│                       │ fail                  │ pending           │
│                       ↓                       │ complete           │
│                    FIX_BUILD                  ↓                   │
│                    fixed──┘            ┌─────────────┐            │
│                    failed──→ [failed]  │ PLAN_DRAFT  │←──┐        │
│                                       └──────┬──────┘   │        │
│                                         created          │        │
│                                              ↓           │        │
│                                       ┌─────────────┐    │        │
│                                  ┌──→ │ PLAN_AUDIT   │    │        │
│                                  │    └──────┬──────┘    │        │
│                                  │ issues     │approved  │        │
│                                  │            ↓           │        │
│                                  │    PLAN_AUDIT_EVIDENCE │        │
│                                  │            │           │        │
│                                  │            ↓           │        │
│                                  │    ┌─────────────┐    │        │
│                                  │    │   EXECUTE    │←──┐│        │
│                                  │    └──────┬──────┘   ││        │
│                                  │           │           ││        │
│                                  │           ↓           ││        │
│                                  │    ┌─────────────┐    ││        │
│                                  │    │CLOSURE_AUDIT │───┘│        │
│                                  │    └──────┬──────┘incomplete    │
│                                  │           │complete             │
│                                  │           ↓                     │
│                                  │    BUILD_VERIFY                 │
│                                  │    pass ──→ ROADMAP_CHECK      │
│                                  │    fail ──→ retry EXECUTE      │
│                                  │                                │
│                                  └── retry PLAN_DRAFT (maxRetries=3)│
│                                                                   │
│  ─── 审计循环 (从 ROADMAP_CHECK complete 进入) ───                │
│                                                                   │
│  DEEP_AUDIT ──issues──→ PLAN_DRAFT (同上)                        │
│     │clean                                                        │
│     ↓                                                             │
│  ADVERSARIAL ──issues──→ PLAN_DRAFT                               │
│     │clean                                                        │
│     ↓                                                             │
│  [completed]                                                      │
└──────────────────────────────────────────────────────────────────┘
```

## 7. 错误恢复场景

### 7.1 场景一：计划拟制失败（subprocess killed）

```
PLAN_DRAFT → subprocess 被 watchdog kill (ok=false)
         → onError: { goto: "ROADMAP_CHECK" }
         → ROADMAP_CHECK 重新评估路线图
         → 如果仍有 pending 项 → 重新进入 PLAN_DRAFT
```

**设计决策**：Plan draft 被 kill 时不直接 retry，而是先回到 ROADMAP_CHECK 重新评估。原因是：plan draft 可能因为计划过大/过复杂导致超时，重新评估路线图可能拆分出更小的计划。

### 7.2 场景二：计划审计反复失败

```
PLAN_DRAFT ──→ PLAN_AUDIT (issues) ──retry──→ PLAN_DRAFT
                                           ──→ PLAN_AUDIT (issues) ──retry──→ PLAN_DRAFT
                                           ──→ PLAN_AUDIT (issues) ──maxRetries exceeded──→ ?
```

**maxRetries 耗尽时的处理**：当 PLAN_AUDIT 的 retry 达到 maxRetries(3) 时，Engine 使用 `onMaxRetries` action。默认行为是 `{ done: "max_retries" }`。

但更合理的处理是：**降级执行**。即使计划质量未通过审计，也比完全没有计划好：

```javascript
PLAN_AUDIT: {
  ...
  maxRetries: 3,
  onMaxRetries: { 
    goto: "EXECUTE",
    append: { 
      template: "\n\n⚠️ 计划未通过自动审计（已尝试 {retryCount} 轮），但以下问题需要执行中注意：\n${output}" 
    }
  },
}
```

### 7.3 场景三：执行中构建失败

```
EXECUTE → CLOSURE_AUDIT(complete) → BUILD_VERIFY(fail)
                                         │
                                         └─ retry: "EXECUTE"
                                            append: { from: "BUILD_VERIFY",
                                              template: "构建失败，请修复编译错误。日志: ${logFile}" }
```

**注意**：BUILD_VERIFY 是 tool step，它没有 AI 输出。它的 context 包含 `{ ok: false, logFile: "/path/to/log" }`。append 模板引用 `${logFile}` 时从 context 中取值。

### 7.4 场景四：Closure audit 反复 incomplete

```
EXECUTE → CLOSURE_AUDIT(incomplete) → retry EXECUTE (with REMAINING)
         → EXECUTE → CLOSURE_AUDIT(incomplete) → retry EXECUTE
         → ... (maxRetries=5) → onMaxRetries
```

maxRetries 耗尽后：继续到 BUILD_VERIFY（如果构建通过则进入下一轮审计循环，审计会发现剩余问题）。

```javascript
CLOSURE_AUDIT: {
  ...
  maxRetries: 5,
  onMaxRetries: { goto: "BUILD_VERIFY" },  // 不终止，让后续审计循环处理剩余问题
}
```

### 7.5 场景五：起点检测发现未完成计划

```
DETECT_START → "execute" → HEALTH_CHECK → EXECUTE → CLOSURE_AUDIT → BUILD_VERIFY
                                                                           │
                                                        (dev loop: ROADMAP_CHECK)
```

当 DETECT_START 检测到未完成计划时，流程进入 HEALTH_CHECK → ... → BUILD_VERIFY → ROADMAP_CHECK。也就是：先执行完已有计划，再检查路线图是否有新工作。

### 7.6 场景六：marker 提取失败

```
PLAN_DRAFT → AI 输出中没有 <PLAN_RESULT> 标签
          → Engine spawn parse agent
          → parse agent 推断出 "created"
          → 正常进入 PLAN_AUDIT
```

如果 parse agent 也推断不出：

```
PLAN_DRAFT → AI 输出无标签 → parse agent 也失败 → onUnknown: { retry: "PLAN_DRAFT" }
```

## 8. 文件组织

```
ai-dev/tools/opencode-goal-driver/
├── src/
│   ├── main.js              # CLI 入口（不变）
│   ├── config.js            # 配置解析（不变）
│   ├── executor.js          # 进程执行器（不变）
│   ├── runner.js            # opencode runStep 封装（简化）
│   ├── engine.js            # [新增] 通用 Flow Engine
│   ├── flow-goal-driver.js  # [新增] Goal Driver Flow 定义（DSL）
│   └── prompts.js           # [重构] 仅保留 prompt 模板（去掉 transitions）
```

### 8.1 engine.js — 通用 Flow Engine

```javascript
export class FlowEngine {
  constructor(flowDef, config, runner) { ... }
  
  async run() {
    // 主循环：execute step → extract marker → find transition → execute action
    // 返回 { status, stepCount, elapsed, history }
  }
  
  async _executeStep(step) { ... }
  async _executeAgentStep(step) { ... }
  async _executeToolStep(step) { ... }
  async _executeScriptStep(step) { ... }
  _resolveTransition(step, marker) { ... }
  _buildPrompt(step, appendContext) { ... }
  _recordEvidence(step, output) { ... }
}
```

### 8.2 flow-goal-driver.js — Flow 定义

纯数据，描述步骤、提示词、转换规则。不含执行逻辑。

### 8.3 prompts.js — 简化

只导出 prompt 模板和 resultTag/markerValues 定义，不再包含 transitions。

### 8.4 workflow.js — 删除

被 engine.js + flow-goal-driver.js 替代。

## 9. 新增工具

### 9.1 record-plan-audit.mjs

在 plan 文件中写入 Draft Audit Evidence 段落。

```bash
node ai-dev/tools/record-plan-audit.mjs <plan-file> [--result approved|issues] [--findings "..."]
```

### 9.2 check-plan-audit.mjs

检查 plan 文件中是否存在 Draft Audit Evidence 且结果为 approved。

```bash
node ai-dev/tools/check-plan-audit.mjs <plan-file>
# 退出码 0 = 通过
# 退出码 1 = 未通过
```

## 10. Mock 测试场景

Engine 的 dry-run 模式下，每个 step 的 mock 行为：

| Step | 第 1 次 mock | 第 2 次 mock | 第 3+ 次 mock |
|------|-------------|-------------|--------------|
| HEALTH_CHECK | pass | pass | pass |
| FIX_BUILD | fixed | - | - |
| ROADMAP_CHECK | pending | complete | complete |
| PLAN_DRAFT | created | created | created |
| PLAN_AUDIT | issues | approved | approved |
| PLAN_AUDIT_EVIDENCE | pass | pass | pass |
| EXECUTE | success | success | success |
| CLOSURE_AUDIT | incomplete | complete | complete |
| BUILD_VERIFY | pass | pass | pass |
| DEEP_AUDIT | issues | clean | clean |
| ADVERSARIAL | clean | clean | clean |

这个 mock 矩阵产生的 dry-run 流程：

```
DETECT_START(roadmap)
→ HEALTH_CHECK(pass)
→ ROADMAP_CHECK(pending)
→ PLAN_DRAFT(created)
→ PLAN_AUDIT(issues) ──retry──→ PLAN_DRAFT'(created)
→ PLAN_AUDIT(approved)
→ PLAN_AUDIT_EVIDENCE(pass)
→ EXECUTE(success)
→ CLOSURE_AUDIT(incomplete) ──retry──→ EXECUTE'(success)
→ CLOSURE_AUDIT(complete)
→ BUILD_VERIFY(pass)
→ ROADMAP_CHECK(complete)  [dev loop 退出]
→ DEEP_AUDIT(issues)
→ PLAN_DRAFT(created)      [审计循环发现的问题]
→ PLAN_AUDIT(approved)     [mock 矩阵：第 3+ 次 approved]
→ PLAN_AUDIT_EVIDENCE(pass)
→ EXECUTE(success)
→ CLOSURE_AUDIT(complete)
→ BUILD_VERIFY(pass)
→ ROADMAP_CHECK(complete)
→ DEEP_AUDIT(clean)
→ ADVERSARIAL(clean)
→ done: completed
```

## 11. 实施计划

### Phase 1: Engine + Flow DSL 核心

1. 实现 `engine.js`：FlowEngine 类
2. 实现 `flow-goal-driver.js`：Goal Driver Flow 定义
3. 重构 `prompts.js`：只保留 prompt 模板
4. 更新 `runner.js`：简化 mock，适配 engine
5. 更新 `main.js`：用 engine 替代旧 workflow.js
6. dry-run 测试通过

### Phase 2: Plan Audit 闭环

1. 实现 `record-plan-audit.mjs` 工具
2. 实现 `check-plan-audit.mjs` 工具
3. 完善 PLAN_AUDIT prompt（引用 plan guide 审查维度）
4. 端到端测试：plan draft → audit → evidence 记录 → 工具检查

### Phase 3: 清理

1. 删除旧 `workflow.js`
2. 更新 `run-goal-driver-go.sh` 和相关文档
3. 干跑（dry-run）+ 真实模块测试
