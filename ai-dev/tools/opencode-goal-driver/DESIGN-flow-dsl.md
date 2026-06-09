# Goal Driver Flow Engine 设计文档

> Status: draft v2
> Last Reviewed: 2026-06-09
> Source: ai-dev/tools/opencode-goal-driver

## 1. 动机

将硬编码的 workflow 重构为声明式 Flow DSL + 通用引擎。核心原则：

1. **引擎不含业务逻辑** — 所有业务语义在 Flow 定义（DSL）中表达，引擎只负责"执行 step → 提取结果 → 查表跳转"
2. **每个 step 是原子单元** — 执行一个固定脚本、或发送一个 AI prompt，返回一个结构化结果
3. **结果驱动跳转** — step 的返回值查 transitions 表决定下一步
4. **统一容错** — 每个 step 有独立的重试/降级配置

## 2. 核心概念

### 2.1 Flow

Flow 是一个**有限状态机**：由若干 Step 组成，Step 之间通过 Transition 连接。

```javascript
const flow = {
  name: "goal-driver",
  entry: "DETECT_START",
  maxTotalSteps: 100,
  maxCycleVisits: 15,
  totalTimeout: 0,             // flow 总执行时限（毫秒），0 = 不限

  // 全局 marker 别名映射（容错：AI 返回非预期值时尝试映射）
  markerAliases: {
    "已创建": "created", "已批准": "approved", "有问题": "issues",
    "完成": "complete", "未完成": "incomplete", "成功": "success",
    "失败": "failed", "修复": "fixed", "待处理": "pending", "干净": "clean",
  },

  steps: { /* ... */ },
};
```

### 2.2 Step

每个 Step 是一个原子工作单元。有三种类型：

| 类型 | 执行方式 | 结果提取 |
|------|---------|---------|
| `script` | 执行 JS 函数 | 返回值直接作为 marker |
| `tool` | 执行 bash 命令 | `exit 0` → marker=`pass`，非零 → marker=`fail` |
| `agent` | spawn `opencode run` 子进程 | 从 AI 输出中提取 `<resultTag>值</resultTag>` |

Step 定义：

```typescript
interface Step {
  name: string;                    // 唯一标识（key in steps map）
  type: "script" | "tool" | "agent";

  // === 执行配置 ===
  run?: (ctx) => string | { marker: string; vars?: Record<string, string> };  // script: 返回 marker 或 marker+vars
  command?: string;                // tool: bash 命令模板（支持 {var} 替换）
  prompt?: string;                 // agent: 内联 AI prompt（短提示词）
  promptFile?: string;             // agent: 从文件加载 prompt（长提示词，引擎运行时读取，不占 AI turn）
  resultTag?: string;              // agent: 从输出中提取此 XML 标签的值作为 marker
  system?: string;                 // agent: system prompt

  // === 超时控制 ===
  timeout?: number;                // 总执行时限（毫秒），硬上限，超时 SIGTERM
  activityTimeout?: number;        // 活动超时（毫秒），无活动输出则判定卡死
  activityProbe?: ProbeSpec;       // 活动探测方案（见 §3.6）
  onActivityTimeout?: {            // 活动超时后的行为
    action: "kill" | "watchdog";
    fallback?: Action;
  };

  // === 结果跳转 ===
  transitions: Record<string, Action>;  // marker → action 映射

  // === 容错配置 ===
  maxRetries?: number;             // retry action 的最大重试次数（默认 3）
  onError?: Action;                // 子进程被 kill / 执行异常时的 fallback action
  onUnknown?: Action;              // marker 提取失败时的 fallback action
  onUnknownMaxRetries?: number;    // marker 值不在 transitions 中时，尝试 session 纠正的最大次数（默认 2）
  onMaxRetries?: Action;           // maxRetries 耗尽时的降级 action
}
```

### 2.3 Action

Action 描述"下一步做什么"：

```typescript
type Action =
  | { goto: string }                         // 跳转到指定 step
  | { goto: string; append: AppendSpec }     // 跳转并追加上下文到目标 step 的 prompt
  | { done: string }                         // 终止 flow，返回 status
  | { retry: string; maxRetries?: number; append?: AppendSpec }  // 重试目标 step
```

**goto vs retry 的区别**：

| | goto | retry |
|---|---|---|
| 目标 step 计数 | +1 visit | +1 retry（独立于 visit 计数） |
| 上下文追加 | 按 append spec | 按 append spec，且历史反馈**累积** |
| 超限处理 | maxCycleVisits → 终止 | maxRetries → onMaxRetries |
| prompt 拼接 | 原始 prompt + append | 原始 prompt + 所有历史 append 累积 |

**跳转方向约束**：`goto` 和 `retry` 只能跳转到**同层兄弟 step 或外层 step**。类似嵌套调用的 `break to label`。引擎不强制此约束（当前为单层 flat 结构），但设计上应遵守。

### 2.4 模板变量与上下文模型

引擎维护一个**上下文变量池**，prompt 和 command 中的 `{variable}` 在运行时从中替换：

```typescript
// 上下文变量来源：
// 1. 静态配置（delegates.vars）
{ module }              → config.moduleName (如 "nop-ai-agent")
{ projectRoot }         → config.projectRoot

// 2. 前序 step 的输出结果（自动注入）
{ steps.STEP_NAME.text }     → step 的 AI 输出文本
{ steps.STEP_NAME.marker }   → step 的 marker 值
{ steps.STEP_NAME.ok }       → step 的 ok 值 ("true"/"false")
{ steps.STEP_NAME.logFile }  → step 的日志文件路径

// 3. script step 返回的 vars（自动展平到顶层）
// script 返回 { marker: "execute", vars: { activePlanCount: "3" } }
{ activePlanCount }           → "3"
```

**模板语法**：`{variable}` 支持点分路径（如 `{steps.ROADMAP_CHECK.text}`）。正则为 `\{(\w[\w.]*)\}`，匹配 `a.b.c` 形式。未匹配的变量保留原样（如 `{DATE}` 不在上下文中，保留为 `{DATE}`，AI 自行解释）。

**`promptFile` 与 `prompt` 优先级**：若 step 定义了 `promptFile`，引擎运行时从文件加载内容作为 prompt 基础文本（不占用 AI turn）。若未定义，使用 `prompt` 内联文本。两者都经过模板变量替换。

### 2.5 结果提取与 Marker 别名

**agent step 的结果提取链**：

```
AI 输出 → 查找 <resultTag>标签</resultTag>
       → 未找到 → spawn parse agent 推断
       → 未找到 → 使用 onUnknown action

找到 marker → 查 transitions 表
           → 不匹配 → 尝试 markerAliases 映射
           → 不匹配 → 尝试大小写不敏感匹配
           → 不匹配 → session 纠正（最多 onUnknownMaxRetries 次）
           → 不匹配 → 使用 onUnknown action
```

**markerAliases 是 workflow 级别的配置**，用于容错。AI 可能返回"已创建"而不是"created"，别名映射让引擎理解两者等价。

### 2.6 上下文追加（AppendSpec）

```typescript
type AppendSpec =
  | true                                    // 追加上一步的完整输出
  | string                                  // 追加固定文本
  | { template: string }                    // 用模板格式化输出
  | { extract: string; template: string }   // 从输出中提取 XML 块，再用模板格式化
```

append 文本拼接到**目标 step 的 prompt 尾部**。retry 场景下，每次重试的反馈用分隔线累积追加。

### 2.7 循环抽象

当前引擎为单层 flat 结构。循环通过 `goto` 跳转回之前的 step 实现。

**未来扩展：嵌套循环**

当需要"遍历每个未完成计划，对每个计划执行子步骤"时，需要引入 `foreach` step 类型：

```javascript
FOREACH_PLAN: {
  type: "foreach",
  source: "node ai-dev/tools/check-plan-status.mjs --json",  // 返回 JSON 数组
  iteratorVar: "currentPlan",     // 内部变量名（循环体内引用）
  outputVar: "completedPlans",    // 外部变量名（循环结束后写入上下文）
  body: [                         // 子步骤序列
    { name: "EXECUTE_PLAN", type: "agent", prompt: "执行计划 {currentPlan}...", ... },
    { name: "AUDIT_PLAN", type: "agent", prompt: "验证计划 {currentPlan}...", ... },
  ],
  onItemError: { continue: true },    // 单项失败时继续下一项
  transitions: {
    done: { goto: "ROADMAP_CHECK" },
    allFailed: { done: "failed" },
  },
}
```

变量作用域规则：
- `iteratorVar` 是**内部变量**，只在 foreach body 内可见
- `outputVar` 是**外部变量**，循环结束后写入 flow 级别上下文
- 嵌套 foreach 时，内层的 iteratorVar 不能与外层同名（引擎应检测冲突）

**当前状态**：尚未实现 foreach。EXECUTE step 的 prompt 让 AI 自行遍历计划，CLOSURE_AUDIT 检查断点续传。

## 3. 容错设计

### 3.1 错误分类

| 错误类型 | 触发条件 | 处理方式 |
|---------|---------|---------|
| **subprocess killed** | agent step 返回 `ok=false` | 使用 `onError` action |
| **执行异常** | try/catch 捕获到异常 | 使用 `onError` action |
| **总时限超时** | step `timeout` 到达 | SIGTERM → `ok=false` → `onError` |
| **活动超时** | step `activityTimeout` 到达，无有效活动 | `onActivityTimeout`（kill 或 watchdog） |
| **marker 提取失败** | AI 输出中无 resultTag | spawn parse agent → `onUnknown` |
| **marker 不在 transitions** | AI 返回意外值 | alias 映射 → 大小写匹配 → session 纠正 → `onUnknown` |
| **重试次数耗尽** | retry count > maxRetries | 使用 `onMaxRetries` action |
| **循环次数过多** | visit count > maxCycleVisits | 返回 `max_cycles` |
| **总步数过多** | totalSteps > maxTotalSteps | 返回 `max_total_steps` |
| **Flow 总超时** | flow.totalTimeout 到达 | 返回 `total_timeout` |

### 3.2 重试机制

retry action 的重试行为：

```
首次执行 step → marker 命中 retry action
  → 计算 retryKey = "fromStep→targetStep"
  → retryCount++
  → 如果 > maxRetries → 执行 onMaxRetries
  → 否则 → 拼接 append 到目标 step 的 appendBuffer → goto 目标 step
```

**重试时的 prompt 构建**：

```
[原始 prompt]
                              ← 第 1 次 append（如果是第 2+ 次重试）
───────────────               ← 分隔线（第 2+ 次重试时插入）
[第 2 次 append]              ← 新追加的反馈
```

### 3.3 重试提示词

当前设计中，重试时通过 `append` 追加上次反馈到原始 prompt 尾部。

**未来扩展**：可配置 `retryPrefix`，在重试时拼接到 prompt **前方**而非尾部：

```javascript
{
  retryPrefix: "⚠️ 这是第 {retryCount} 次重试。之前的问题是：\n",
  append: { template: "${output}" },
}
```

或通过上下文变量动态构建重试提示词：

```javascript
{
  append: { template: "第 {retryCount}/{maxRetries} 次尝试。上次返回：\n${output}" },
}
```

### 3.4 每个 step 的总失败次数

当前设计中，每条 transition 可以独立配置 `maxRetries`。不存在"step 级别总失败次数"的概念——每条 retry 路径独立计数。

**如果需要"step 总失败次数"**，可以在 step 上增加：

```javascript
{
  maxTotalFailures: 5,  // 此 step 所有 retry 路径的累计失败次数上限
  onTotalFailures: { done: "degraded" },
}
```

**当前状态**：未实现。现有设计通过 `maxCycleVisits`（同一 step 的总访问次数上限）间接控制。

### 3.6 双层超时：总时限 vs 活动超时

每个 step 可配置两层独立的超时：

```typescript
interface Step {
  // ...
  timeout?: number;           // 总执行时限（毫秒），硬上限，超时无条件 SIGTERM
  activityTimeout?: number;   // 活动超时（毫秒），无新活动输出则判定卡死
  activityProbe?: ProbeSpec;  // 活动探测方案（按 step 类型不同）
}
```

**双层逻辑**：

```
进程启动 → 同时启动两个定时器
  ├── 总时限 timer: 到达 → SIGTERM → SIGKILL → result.ok=false
  └── 活动探测 loop: 每隔 probeInterval 检查一次
        ├── 检测到活动 → 重置活动超时计时
        └── 活动超时到达 → 通知引擎（触发 onError 或 graceful kill）
```

**为什么需要两层**：
- `timeout` 是硬上限，防止失控（如 LLM API 无限挂起）
- `activityTimeout` 是软检测，区分"正常慢"和"真正卡死"
- 例：EXECUTE step timeout=3600s，activityTimeout=600s——AI 可能思考 5 分钟才输出（正常），但连续 10 分钟无任何活动则是卡死

**活动探测方案（ProbeSpec）**：

```typescript
type ProbeSpec =
  | { type: "fd-log" }                          // tool step: 检查 fd 重定向日志的 mtime
  | { type: "opencode-log"; sessionHint?: string }  // agent step: 检查 opencode 内部日志
  | { type: "custom"; check: (ctx) => boolean } // script step: 自定义判断
  | { type: "none" }                            // 不探测（默认）
```

| step 类型 | 默认 probe | 探测逻辑 |
|-----------|-----------|---------|
| `tool` | `fd-log` | 检查 fd 重定向日志文件的 `mtime`，`mtime` 变化 = 有输出 = 活跃 |
| `agent` | `opencode-log` | 检查 opencode 内部日志中的 `service=` 行（见 §3.7） |
| `script` | `none` | JS 函数执行在主线程/await，天然可控 |

**活动超时后的行为**：

```typescript
// step 级配置
onActivityTimeout?: {
  action: "kill" | "watchdog";  // kill: 直接杀进程; watchdog: spawn 子 agent 诊断
  fallback?: Action;            // 杀死后走 onError 还是 goto 其他 step
}
```

- `kill`：直接 SIGTERM，结果 `ok=false`，走 `onError`
- `watchdog`：spawn 独立子 agent 智能判断（当前已实现的 watchdog 机制），子 agent 决定是否 kill

### 3.7 opencode 子 agent 活动探测

当 driver spawn 一个 `opencode run` 子进程时，如何判断子 agent 是否真正在工作？

**探测层次**（从简单到复杂）：

#### 层次 1：fd 重定向日志 mtime（快速，但不精确）

```
probe: { type: "fd-log" }
逻辑: stat(logFile).mtime 在最近 activityTimeout 内变化 → 活跃
局限: opencode 的流式输出持续写 fd 日志（大量 bus 心跳），即使 AI 真正卡住，
      mtime 也会变化 → 误判为活跃
适用: tool step（如 mvnw），输出与进程活动强关联
```

#### 层次 2：opencode 内部日志解析（精确，推荐 agent step 使用）

```
probe: { type: "opencode-log" }
逻辑:
  1. 定位日志文件: ls -t ~/.local/share/opencode/log/*.log | head -1
     或 lsof -p <childPid> -Fn 2>/dev/null | grep opencode/log
  2. 读取最后 N 行（如 tail -100）
  3. 过滤出有效活动行（排除 service=bus 心跳噪声）:
     - service=tool.* → AI 在调用工具（活跃）
     - service=permission.* → AI 请求权限（活跃）
     - service=session.prompt.* → AI 新一轮对话（活跃）
     - service=llm.* → LLM 调用中（活跃）
     - service=bus type=message.part.delta → 流式心跳（忽略）
  4. 最后一条有效活动的时间戳距今 > activityTimeout → 卡死
```

**为什么排除 `service=bus`**：
opencode 的流式响应通过 bus 消息传递，每秒产生大量 `message.part.delta`。这些是 LLM 的流式 token，不代表 AI 在"做事"——AI 可能卡在等待 API 响应，但 bus 心跳仍在（重连、ping 等）。

**有效活动指标定义**：

| 日志 pattern | 含义 | 是否有效活动 |
|-------------|------|------------|
| `service=tool` | AI 调用了 bash/read/write 等工具 | 是 |
| `service=permission` | AI 请求了权限许可 | 是 |
| `service=session.prompt` | AI 发起了新一轮对话循环 | 是 |
| `service=llm` | 发起了 LLM API 调用 | 是 |
| `service=snapshot` | 保存了状态快照 | 否（后台自动） |
| `service=bus type=message.part.delta` | 流式 token 传输 | 否（心跳噪声） |

#### 层次 3：watchdog 子 agent（智能诊断，兜底）

当层次 2 检测到卡死时，可以 spawn 一个独立的 watchdog 子 agent 来做更智能的判断：

```
当前已实现：executor.js 的 spawnWatchdogAgent()
  - spawn 独立 opencode run 子进程
  - 子 agent 读取内部日志、检查进程状态
  - 决策：kill / no-action
  - 结果写入 <WATCHDOG_RESULT> 标签
```

**未来优化**：watchdog 应能读取 fd 重定向日志的最后部分（子 agent 的实际输出内容），结合内部日志的 silence 做综合判断。

### 3.8 Step 执行超时与引擎超时的关系

引擎层面有两个安全网：

```
flow.maxTotalSteps  →  总步数上限（防止无限循环）
flow.maxCycleVisits →  单步循环上限（防止 goto 环）

step.timeout        →  单步总执行时限（硬上限）
step.activityTimeout →  单步活动超时（软检测）
```

引擎在 `run()` 循环中等待 step 完成。如果 step 配置了 `timeout`，executor 层面保证在 timeout 内返回（要么正常完成，要么被 kill）。引擎不需要自己维护 step 级别的定时器。

**引擎级别的总超时**（尚未实现，预留）：

```typescript
interface Flow {
  // ...
  totalTimeout?: number;  // 整个 flow 的总执行时限（毫秒）
}
```

当 flow 总执行时间超过 `totalTimeout`，引擎在下一个 step 开始前检查并终止，返回 `total_timeout`。

### 3.5 结果是否标记成功

| step 类型 | 成功判定 | 失败判定 |
|----------|---------|---------|
| tool | exit code = 0 → `pass` | exit code ≠ 0 → `fail`（正常 marker，不触发 onError） |
| script | 函数正常返回 → 返回值作为 marker | 函数抛异常 → 触发 onError |
| agent | `ok=true` → 从输出提取 marker | `ok=false`（进程被 kill）→ 触发 onError |

**关键区别**：tool step 的 `fail` 是**正常业务 marker**，不是错误。agent step 的 `ok=false` 才是真正的执行错误。

## 4. 引擎执行循环

```
function run(entry):
  currentStep = entry || flow.entry
  flowStartTime = Date.now()

  while totalSteps < maxTotalSteps:
    // === 引擎级总超时检查 ===
    if flow.totalTimeout > 0 AND (Date.now() - flowStartTime) > flow.totalTimeout:
      return "total_timeout"

    stepDef = flow.steps[currentStep]
    if !stepDef → return "unknown_step"

    visitCount[currentStep]++
    if visitCount > maxCycleVisits → return "max_cycles"
    totalSteps++

    try:
      // executor 内部处理 timeout + activityTimeout
      result = executeStep(currentStep, stepDef)
    catch:
      → execute onError action

    context[currentStep] = result

    if result.ok == false AND step is not tool:
      → execute onError action

    marker = resolveMarker(result, stepDef)
    if !marker:
      → execute onUnknown action

    transition = findTransition(stepDef, marker)
    // 尝试: 直接匹配 → alias 映射 → 大小写 → session 纠正

    if transition.done → return transition.done
    if transition.retry → handleRetry(transition) → goto target
    if transition.goto → handleGoto(transition) → goto target
```

## 5. 文件组织

```
ai-dev/tools/opencode-goal-driver/
├── src/
│   ├── main.js              # CLI 入口：解析参数，创建 flow + runner，启动引擎
│   ├── config.js            # 配置解析：模块目录发现（支持嵌套模块如 nop-ai/nop-ai-agent）
│   ├── engine.js            # 通用 Flow Engine（不含业务逻辑）
│   ├── runner.js            # opencode/tool 执行封装（真实执行 + mock 模式）
│   ├── executor.js          # 底层进程管理（spawn + fd 重定向 + watchdog）
│   └── flow-goal-driver.js  # Goal Driver Flow 定义（纯数据 DSL + 业务 prompt 引用）
├── prompts/                       # prompt 文件（引擎运行时加载，不占 AI turn）
│   ├── fix-tests.md              # 智能修复测试提示词
│   ├── execute-pending-plan.md   # 执行待完成计划提示词
│   ├── roadmap-check.md          # 路线图检查提示词
│   ├── plan-draft.md             # 计划拟制提示词
│   ├── plan-audit.md             # 计划审查提示词
│   ├── execute-plan.md           # 计划执行提示词
│   ├── closure-audit.md          # 闭环验证提示词
│   ├── closure-audit-v2.md       # 闭环验证提示词（v2）
│   └── needs-deep-audit.md       # 智能判断是否需要深度审计
├── test/
│   └── engine.test.js       # 引擎单元测试（62 个测试，涵盖容错/边界/集成）
└── DESIGN-flow-dsl.md       # 本文件
```

### 职责划分

| 文件 | 职责 | 是否含业务逻辑 |
|------|------|-------------|
| engine.js | 通用 FSM 执行器 | 否 |
| runner.js | 执行封装（真实/mock） | 否 |
| executor.js | 进程 spawn + watchdog | 否 |
| config.js | 参数解析 + 模块目录发现 | 否 |
| flow-goal-driver.js | Flow 定义 + prompt | **是**（所有业务语义在此） |
| main.js | 胶水代码 | 否 |

## 6. 错误恢复场景

### 6.1 测试修复失败 → 恢复

```
FIX_TESTS(failed) → FIX_TESTS_RECOVERY(fixed) → CHECK_PENDING_PLANS
FIX_TESTS_RECOVERY(failed) → [tests_failed]
```

第一次修复失败时进入恢复步骤，使用更保守的策略。恢复也失败则终止。

### 6.2 待完成计划循环

```
CHECK_PENDING_PLANS(has_plans) → EXECUTE_PENDING_PLAN → VERIFY_PENDING_PLAN
  → complete → COMMIT_PENDING_PLAN → CHECK_PENDING_PLANS（检查是否有更多）
  → incomplete → retry EXECUTE_PENDING_PLAN（最多 3 次）
```

脚本检测待完成计划，逐个执行验证提交，直到无待完成计划。

### 6.3 计划审计反复失败 → 降级执行

```
PLAN_DRAFT → PLAN_AUDIT(issues) → retry PLAN_DRAFT
PLAN_DRAFT → PLAN_AUDIT(issues) → retry PLAN_DRAFT
PLAN_DRAFT → PLAN_AUDIT(issues) → maxRetries exceeded
→ onMaxRetries: { goto: EXECUTE_PLAN, append: "⚠️ 以下问题需要注意：..." }
```

降级而非终止——不完美的计划也比没有计划好。

### 6.4 计划完成后自动提交

```
EXECUTE_PLAN → PLAN_CLOSURE(complete) → PLAN_COMMIT → NEEDS_DEEP_AUDIT
```

计划闭环验证通过后，脚本自动 git commit，然后进入深度审计判断。

### 6.5 审计发现 → 二次计划循环 → 回到起点

```
DEEP_AUDIT → ADVERSARIAL(issues) → AUDIT_PLAN_DRAFT → AUDIT_PLAN_AUDIT
  → AUDIT_EXECUTE → AUDIT_CLOSURE → AUDIT_COMMIT → FIX_TESTS（回到起点）
```

审计发现问题的完整闭环：拟制修复计划 → 审核 → 执行 → 验证 → 提交 → 回到第一步。

## 7. 工具脚本

### 已有

| 脚本 | 用途 |
|------|------|
| `check-plan-status.mjs` | 扫描 `ai-dev/plans/` 目录，解析每个计划的 `Plan Status`，输出活跃计划列表 |
| `check-plan-checklist.mjs` | 检查计划文件的 Exit Criteria checklist |

### 计划中

| 脚本 | 用途 |
|------|------|
| `record-plan-audit.mjs` | 在计划文件中写入 Draft Audit Evidence 段落 |
| `check-plan-audit.mjs` | 检查 Draft Audit Evidence 是否存在且结果为 approved |

## 8. 模块兼容性

引擎通过 `config.js` 的 `findModuleDir()` 自动发现模块目录：

- 顶层模块（如 `nop-stream`）：`{projectRoot}/nop-stream/`
- 嵌套模块（如 `nop-ai-agent`）：`{projectRoot}/nop-ai/nop-ai-agent/`（搜索一级子目录）
- 手动覆盖：`--module-dir nop-ai/nop-ai-agent`

Maven `-pl` 使用 artifactId（如 `nop-ai-agent`），Maven reactor 能解析嵌套模块。

## 9. Goal Driver Flow 图

```
 ┌──────────────────────────────────────────────────────────────┐
 │ 1. FIX_TESTS ──no_errors──→ 2. CHECK_PENDING_PLANS          │
 │    │                           │                              │
 │    │ failed                    │ has_plans ──→ EXECUTE_       │
 │    ↓                           │              PENDING_PLAN    │
 │    FIX_TESTS_RECOVERY          │                   ↓          │
 │    │                           │           VERIFY_PENDING     │
 │    │ fixed → CHECK_...        │               ↓              │
 │    │ failed → [tests_failed]  │         COMMIT_PENDING_PLAN  │
 │                                │               ↓              │
 │                                ←─── (loop to CHECK_PENDING)  │
 │                                │                              │
 │                                │ no_plans                     │
 │                                ↓                              │
 │              3. ROADMAP_CHECK ──complete──→ 7. NEEDS_DEEP_   │
 │                 │                           AUDIT             │
 │                 │ pending                     │               │
 │                 ↓                             │ not_needed     │
 │            4. PLAN_DRAFT ←───────────────────┤ → [completed] │
 │                 │                             │               │
 │                 │ created                     │ needed        │
 │                 ↓                             ↓               │
 │            PLAN_AUDIT ──approved──→ 5.   8. DEEP_AUDIT       │
 │                 │                      EXECUTE_PLAN     │     │
 │                 │ issues → retry           │           │     │
 │                 │ onMaxRetries→EXECUTE      ↓           │     │
 │                                           6. PLAN_    9.     │
 │                                           CLOSURE  ADVERSARIAL│
 │                                              │       │  │    │
 │                                              │       │  │    │
 │                                              ↓       │  │    │
 │                                         PLAN_COMMIT   │  │    │
 │                                              │       │  │    │
 │                                              → NEEDS_│  │    │
 │                                               DEEP_  │  │    │
 │                                               AUDIT  │  │    │
 │                                                      │  │    │
 │                                          issues ────┘  │    │
 │                                          clean → [completed]│
 │                                                             │
 │              10. AUDIT_PLAN_DRAFT ←────────────────────────┘
 │                      │
 │                      │ created
 │                      ↓
 │              11. AUDIT_PLAN_AUDIT ──approved──→ 12. AUDIT_EXECUTE
 │                      │                              │
 │                      │ issues → retry               ↓
 │                      │ onMaxRetries→AUDIT_EXECUTE  13. AUDIT_CLOSURE
 │                                                     │
 │                                                     ↓
 │                                                14. AUDIT_COMMIT
 │                                                     │
 │                                                     ↓
 │                                                → 1. FIX_TESTS (loop)
 └──────────────────────────────────────────────────────────────┘
 ```
