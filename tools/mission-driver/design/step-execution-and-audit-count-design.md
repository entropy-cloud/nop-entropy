# Step Execution Modes & Deep-Audit Count Design

**Date**: 2026-07-20
**Scope**: `tools/mission-driver/src/main.js`, `tools/mission-driver/src/engine.js`, `tools/mission-driver/flows/mission-driver.json`, `tools/mission-driver/prompts/draft-from-roadmap.md`, run-state schema
**Status**: proposal (analysis + recommended solution, no code change yet)
**Related**: `mission-driver-flow-design.md`, `flow-engine-design.md`

---

## 0. 问题概述

用户在使用 mission-driver 时遇到三个相互关联的问题：

| # | 问题 | 现象 |
|---|------|------|
| 1 | `--step` 单步执行不可靠 | 指定 `--step DEEP_AUDIT` 后仍会进入固定循环，没有真正"只跑这一步" |
| 2 | 缺少"从某步开始向后跑"的开关 | 是一个 loop，希望能从 DEEP_AUDIT 开始继续往后执行 |
| 3 | deep audit 是否已做过由 AI 自判，不稳定 | AI 混淆 plan 的 closure-audit 与 DEEP_AUDIT，无法稳定进入 deep audit |

三者共同的症状是**"无法可靠地触发并观测 DEEP_AUDIT"**。本文档分析根因并给出方案。

---

## 1. 现状梳理（关键代码位置）

### 1.1 主流程的 audit 循环

`flows/mission-driver.json` 的核心循环（简化）：

```
CHECK ──pass──▶ REVIEW_PLANS ──▶ EXEC_PLANS ──▶ DRAFT_PLANS ──┐
                  ▲                                 │          │
                  │                                 │ nothing  │
                  │                                 ▼          │
                  └────────── DRAFT_PLANS ◀── DEEP_AUDIT       │
                                   ▲                            │
                                   │ done / 达到 maxAuditRounds │
                                   └──── completed ────────────┘
```

- `DRAFT_PLANS` 是一个 `agent` 步骤，AI 自行决定输出 `created` / `nothing` / `done` 三种 marker（`prompts/draft-from-roadmap.md:27-43`）。
- 只有 AI 输出 `nothing` 时，才转入 `DEEP_AUDIT`（`mission-driver.json:65`）。
- `DEEP_AUDIT` 是 `subflow`，跑完 `complete` 后回到 `REVIEW_PLANS`（`mission-driver.json:86-94`）。`REVIEW_PLANS.forEach = draftPlans()`，若 deep-audit-loop 的 `SCAN_NEW_RESULTS` 已把 plan 推到 `active`，则 `draftPlans()` 为空、`REVIEW_PLANS` 兜底直通 `EXEC_PLANS` 执行；若仍有 `draft` plan，则正常评审。2026-07-14 commit `0c763f0` 大迁移曾把这条 transition 无意回退成 `DRAFT_PLANS`，导致 DRAFT_PLANS（只看 roadmap）忽略 audit 创建的 active plan、`nothing` → DEEP_AUDIT 空转，2026-07-21 修回。

### 1.2 `--step` 现有实现

`src/main.js:583-597`：

```js
if (config.entryStep) {
  const step = flow.steps[config.entryStep];
  if (!step) { /* 报错退出 */ }
  console.log(`Step:       ${config.entryStep} (single-step mode)`);
  for (const [, t] of Object.entries(step.transitions || {})) {
    if (t.goto && !t.retry) {
      t.done = "completed";   // 把 goto 改写成 done
      delete t.goto;
    }
  }
}
// ...
const result = await engine.run(config.entryStep);  // 以该步为 entry
```

机制：把指定步的 `transitions` 里所有非 retry 的 `goto` **就地改写**成 `done: "completed"`，然后以该步为入口跑一次。

### 1.3 `maxAuditRounds` 现有实现

`src/engine.js:1278-1347`：

```js
let currentStep = entryOverride || this.flow.entry;
const maxAuditRounds = this.flow.maxAuditRounds ?? 0;     // 3
const auditEntry = this.flow.auditEntry || this.flow.entry; // "DEEP_AUDIT"
// ...
while (totalSteps < maxTotalSteps) {
  const visits = (this.visitCounts.get(currentStep) || 0) + 1;
  this.visitCounts.set(currentStep, visits);
  // ...
  if (maxAuditRounds > 0 && currentStep === auditEntry && visits > maxAuditRounds) {
    return await this._result("completed", totalSteps);   // 达到上限 → 整个 run 结束
  }
  // ...
}
```

机制：用内存里的 `this.visitCounts` 计数，第 `maxAuditRounds+1` 次进入 `DEEP_AUDIT` 时整个 run 以 `completed` 结束。

### 1.4 run-state 现有字段

`src/engine.js:303-323`（`_initWorkflow`）：

```js
this.workflow = {
  missionName, flowName, runId, runDir,
  pid: process.pid,
  status: "running",
  startedAt, updatedAt, endedAt: null,
  currentStep: null,
  steps: [],   // 每步 open/close 时追加/替换一条记录
};
```

**没有**任何 audit 计数字段。`visitCounts` 也只活在内存，从不落盘。

---

## 2. 根因分析

### 2.1 问题 1：`--step` 不可靠 —— 只改写了 `transitions`，漏了 fallback 跳转

`main.js:591-596` 的改写循环只遍历 `step.transitions`。但一个步骤的**出口**实际有四类：

| 出口字段 | 含义 | 是否被改写 |
|----------|------|-----------|
| `transitions[*]` | 正常 marker 驱动的跳转 | ✅ 改写 |
| `onError` | 步骤抛错时的兜底 | ❌ **不改写** |
| `onUnknown` | marker 无法识别且纠正失败的兜底 | ❌ **不改写** |
| `onMaxRetries` | retry 达到上限的兜底 | ❌ **不改写** |

以 `DEEP_AUDIT` 为例（`mission-driver.json:86-94`）：

```json
"DEEP_AUDIT": {
  "type": "subflow",
  "flow": "deep-audit-loop",
  "transitions": {
    "complete": { "goto": "DRAFT_PLANS" },   // ← 被改成 done
    "failed":   { "goto": "DRAFT_PLANS" }    // ← 被改成 done
  },
  "onError": { "goto": "DRAFT_PLANS" }        // ← 没改！出错就逃逸进主循环
}
```

`DRAFT_PLANS` 更严重（`:59-71`）：`onError: { retry }`、`onUnknown: { goto: DEEP_AUDIT }`、`onMaxRetries: { goto: DEEP_AUDIT }` 全都没被改写。一旦 AI 输出一个无法识别的 marker（在审计相关场景里这正是用户反馈的"混淆"高发区），`onUnknown` 就把它送进 `DEEP_AUDIT`，再回到 `DRAFT_PLANS`……单步模式直接退化成完整循环。

**这就是"指定单步却还是固定步骤执行"的直接原因**：改写只覆盖了 happy path，任何异常出口都会让控制流逃出"单步"边界。

附带问题：
- 改写是**对共享 flow 对象的就地 mutation**，靠"每次 run 都是新进程"侥幸避免污染，语义不干净。
- 子流程（subflow）类型的步骤，其内部 `when` 条件（如 `deep-audit-loop` 的 `openAudits().length > 0`）在孤立单步时可能不满足，导致内部步骤被 skip，但这是子流程自己的事，不算 bug。

### 2.2 问题 2：缺少 `--from-step`

`engine.run(entryOverride)` 已经支持任意入口（`engine.js:1278`），但 CLI 层只有两种模式：
- 不传 `--step`：从 `flow.entry`（CHECK）开始，正常循环。
- 传 `--step`：单步（且因 2.1 不可靠）。

**没有"从 X 开始、之后照常循环"的模式**。这个能力其实是 2.1 的对偶：把入口挪到 X，但**不动 transitions**。

一个真实的诉求场景：用户想"今天就跑一次 deep audit，跑完让它自动接着 draft / review / exec"。当前做不到——`--step DEEP_AUDIT` 会跑完就停（如果没出错），而默认入口又得从 CHECK 走起。

### 2.3 问题 3：deep audit 是否做过，靠 AI 自判

链路上的判断点有**两层 AI 自判**，都不稳定：

**第一层：进入 DEEP_AUDIT 的触发**

`DRAFT_PLANS` 的 AI 需要在三种 marker 里选一个（`draft-from-roadmap.md:27-43`）：

- `nothing`：roadmap 跑完、没有 deferred 项 → 进 DEEP_AUDIT
- `done`：mission 完全完成，**包括**"DEEP_AUDIT already ran and found nothing actionable"
- `created`：起草了新 plan

问题在于 prompt 把"DEEP_AUDIT 是否已经跑过且无可执行结论"这个**事实判断**交给了 AI。AI 没有 run state，只能去翻 `docs/audits/` 里的文件去猜。而 `docs/audits/` 里同时存在：
- `plan-execution` 子流程产生的 closure audit（plan 级审计）
- DEEP_AUDIT 产生的 multi / open audit（mission 级审计）

两类文件都带 `> Audit Status: open` 头（`flow-loader.js` 的 `openAudits()` 正是靠这个正则扫描）。**AI 很容易把 plan 级审计的产物误认成"deep audit 已经做过了"**，从而错误地输出 `done`，整个 run 提前结束，DEEP_AUDIT 根本没进。这正是用户描述的"AI 会混淆 plan 的 audit 和 deep audit，无法进入 deep audit"。

**第二层：`maxAuditRounds` 的退出**

`engine.js:1344` 用内存 `visitCounts` 控制第 N 次进入 DEEP_AUDIT 后 completed。但：
- 计数**不落盘**到 run-state.json，monitor 看不到、事后复盘看不到、AI 也读不到。
- 这个上限只防止"无限 audit"，并不告诉 DRAFT_PLANS"还剩几轮 audit 额度"。

**结果**：进入与退出 DEEP_AUDIT 的两个判断，一个全靠 AI 猜、一个只活在内存。用户感知到的"不稳定"是必然的。

### 2.4 三个问题的耦合点

三个问题看似独立，实际共享同一个缺失物：**run-state 里没有一个权威的、可观测的 "deep audit 已执行次数" 计数**。

- 有了它，问题 3 的进入/退出判断可以从"AI 自判"升级为"引擎据计数决定"。
- 有了它，问题 1/2 的"单步/从某步继续"才能在 DEEP_AUDIT 这个点上做到**可复现**（否则同一个 `--step DEEP_AUDIT` 在不同 audit 计数下行为不同）。
- 有了它，monitor、`--analyze` 复盘、日志才能显示"这是第几轮 audit"。

因此**问题 3 是三个问题的枢纽**，建议优先做。

---

## 3. 设计目标与约束

### 3.1 目标

1. **G1 — 单步执行可靠**：`--step X` 无论 X 的哪条出口触发（含 onError/onUnknown/onMaxRetries），都只跑一步就停。
2. **G2 — 支持从某步继续**：新增 `--from-step X`，以 X 为入口、transitions 不动、之后照常循环。
3. **G3 — audit 计数权威化**：在 run-state.json 中持久化 DEEP_AUDIT 已执行次数；DRAFT_PLANS 的进入/退出判断改为引擎 + 计数驱动，AI 只负责"还有没有 plan 可起草"。
4. **G4 — 可观测**：audit 计数出现在 monitor dashboard、events.jsonl、`--analyze` 复盘里。
5. **G5 — 向后兼容**：旧 run-state.json（无新字段）能被正常读取；不引入破坏性的 flow JSON 变更。

### 3.2 约束 / 不变性

- 引擎核心保持**零 npm 依赖**（`CONTEXT.md` 关键约束）。
- flow JSON 仍是纯声明式状态机；不在 flow 里写命令式逻辑。
- 顶部步骤之间不传参（`mission-driver-flow-design.md` 设计结论 #8）——步骤间数据只通过磁盘文件 + `delegates.vars`。audit 计数走 `delegates.vars` 注入，**不破坏**这条不变性。
- run-state 写入必须保持**原子写**（`_writeWorkflow` 的 tmp+rename 模式，`engine.js:404-413`）。
- Windows / Git Bash 环境。

### 3.3 非目标

- 不重构 deep-audit-loop 子流程内部步骤。
- 不改 marker 协议（`<AI_STEP_RESULT>`）。
- 不引入跨 run 的 audit 计数（每次 run 仍从 0 开始计；见 §6 备选讨论）。

---

## 4. 解决方案

总体三块：**(A) audit 计数持久化** → **(B) DRAFT_PLANS 进入/退出决策引擎化** → **(C) `--step` 修固 + `--from-step` 新增**。按依赖顺序展开。

### 4.1 方案 A：在 run-state 中持久化 audit 计数

#### 4.1.1 新增字段

在 `_initWorkflow`（`engine.js:303-323`）初始化的 workflow 对象中新增两个顶层字段：

```js
this.workflow = {
  // ...现有字段...
  auditRound: 0,                 // 本 run 内 DEEP_AUDIT 已开始执行的次数（从 0 起）
  maxAuditRounds: this.flow.maxAuditRounds ?? 0,   // 上限，便于消费方直接读
};
```

字段语义：
- `auditRound`：每次**进入** `DEEP_AUDIT` 步骤（`_wfOpen` 时且 `name === auditEntry`）就 `+1`。表示"这是第 N 轮 audit（1-based）"。
- 子流程内部步骤（`CHECK_OPEN_AUDITS` 等）的执行**不**额外计数，一轮 audit = 一次 `DEEP_AUDIT` 顶层步骤访问。

> 命名说明：用 `auditRound` 而非 `auditCount`，与 `maxAuditRounds` / 现有 log 文案"audit round"对齐，避免"count vs visits"歧义。

#### 4.1.2 计数时机

在 `_wfOpen`（`engine.js:325-350`）里加一小段，**只在主流程**计（子流程的 `_wfOpen` 不触发，因为子流程的 `auditEntry` 是其自己的 entry，不等于主流程的 `DEEP_AUDIT`；用 `cfg.isSubflow !== true && name === auditEntry` 双重判断）：

```js
_wfOpen(name, visits) {
  // ...现有逻辑...
  if (!this._isSubflow && name === (this.flow.auditEntry || this.flow.entry)) {
    this.workflow.auditRound = (this.workflow.auditRound || 0) + 1;
  }
  // ...writeWorkflow...
}
```

注意：`auditRound` 应在 `_wfOpen`（步骤开始时）就递增并落盘，而不是 `_wfClose`。这样即使 audit 中途崩溃，run-state 也如实反映"第 N 轮 audit 进行中"，与 `status: running` 语义一致，便于 `run-reconcile` 与复盘。

#### 4.1.3 与现有内存计数的关系

`engine.js:1344` 的 `visitCounts` 闸门**保留不变**，但判断依据改为同时读 `this.workflow.auditRound`：

```js
// 进入循环前 / 进入 DEEP_AUDIT 时
if (maxAuditRounds > 0 && currentStep === auditEntry) {
  const round = this.workflow.auditRound || 0;   // 已在 _wfOpen 里递增过
  if (round > maxAuditRounds) {
    return await this._result("completed", totalSteps);
  }
}
```

> 注意顺序：`_wfOpen` 在 `engine.js:1351` 被调用，而 maxAuditRounds 闸门在 `:1344`（在 `_wfOpen` 之前）。需要把闸门挪到 `_wfOpen` 之后，或让闸门读"递增前的值"。实现细节见 §5.1，此处只定语义。

#### 4.1.4 注入给 prompt

在 `main.js` 构建 `delegates.vars` 时（`:540-575`），加两个变量：

```js
vars: {
  // ...现有...
  auditRound: "",        // 占位，engine 运行时按当前轮次回填
  maxAuditRounds: g.maxAuditRounds ?? (flow.maxAuditRounds ?? 0),
}
```

但 `delegates.vars` 是一次构建、全程只读的快照，而 `auditRound` 每轮变化。两种做法（推荐 §4.1.4-B）：

**A. 静态注入（简单但弱）**：在 vars 里放 `maxAuditRounds` 即可，`auditRound` 不进 prompt（因为进入 DEEP_AUDIT 的判断已经引擎化，AI 不需要知道当前轮次）。

**B. 动态回填（推荐）**：让 `draft-from-roadmap.md` 不再判断"audit 是否做过"，只判断"有没有 plan 可起草"。audit 相关决策完全交给引擎。这样 prompt 根本不需要 `auditRound` 变量——**把判断从 prompt 里拿出去**，比把计数喂给 prompt 更稳定（详见 §4.2）。

> 结论：采用 B。`delegates.vars` 只新增 `maxAuditRounds`（用于 prompt 末尾的"你还剩 N 轮 audit 额度"提示性文字，非决策依据）。`auditRound` 只存在于 run-state + events，不进 prompt 决策链。

### 4.2 方案 B：DRAFT_PLANS 进入/退出决策引擎化

这是消除"AI 混淆"的核心。把 DRAFT_PLANS 的职责**收窄**为只回答一个问题：

> **"现在有没有 plan 要起草？"** —— 有 → `created`；没有 → `nothing`。

把"mission 是否完成（含 audit 已无可执行结论）"这个判断**从 AI 手里拿走**，交给引擎按 `auditRound` 与"是否有 active/draft plan"、"是否有 open audit"综合决定。

#### 4.2.1 prompt 改动（`prompts/draft-from-roadmap.md`）

删除当前的 `done` 分支语义。新版 prompt 只保留两种 marker：

```
- 有 plan 可起草（roadmap 还有项、或有 deferred 项、或 audit 产生了新 issue）→ created
- 否则 → nothing
```

明确告诉 AI：**不要**自行判断 mission 是否完成、不要判断 audit 是否做过；那由引擎决定。同时给一句反面提示，直接点名混淆源：

> 注意：`docs/audits/` 里可能存在 plan 级 closure audit 的产物。你不要去判断它们。是否进入 deep audit 由引擎按轮次计数决定，与你能看到的 audit 文件无关。

这把 AI 从"事实判断者"降级为"起草执行者"，混淆问题从根上消除。

#### 4.2.2 flow JSON 改动（`flows/mission-driver.json`）

`DRAFT_PLANS.transitions` 调整：

```json
"DRAFT_PLANS": {
  "type": "agent",
  "promptPath": "prompts/draft-from-roadmap.md",
  "transitions": {
    "created": { "goto": "REVIEW_PLANS" },
    "nothing": { "goto": "DEEP_AUDIT" }
  },
  "onError":  { "retry": "DRAFT_PLANS", "maxRetries": 3 },
  "onUnknown": { "goto": "DEEP_AUDIT" },
  "onMaxRetries": { "done": "failed" }
}
```

变化：
- 删掉 `"done": { "done": "completed" }` 这条**正常**出口（AI 不再能单方面宣布完成）。
- `onMaxRetries` 改为 `done: failed`（重试耗尽 = 失败，而不是逃进 audit）。

#### 4.2.3 引擎侧的"完成"判断（新增一个轻量 gate）

DRAFT_PLANS 输出 `nothing` → 本来直接 goto `DEEP_AUDIT`。现在在引擎里加一个 **audit-gate**：当 `currentStep === DRAFT_PLANS && marker === nothing` 时，先判断：

```
if (auditRound >= maxAuditRounds && openAudits().length === 0 && activePlans().length === 0):
    → 整个 run 以 completed 结束（不再进 DEEP_AUDIT）
else:
    → 正常 goto DEEP_AUDIT
```

这个 gate 的实现位置有两种选择：

| 选项 | 做法 | 评价 |
|------|------|------|
| **B-1 flow JSON 内联条件** | 在 `DRAFT_PLANS.transitions.nothing` 里写 `"when": "auditRound >= maxAuditRounds && openAudits().length === 0"`，命中则 `done`，否则 `goto DEEP_AUDIT` | 需要给 flow JSON 的 transition 加 `when` 支持（目前只有 step 级 `when`）。改动面大但通用。 |
| **B-2 引擎硬编码 gate（推荐）** | 在 `engine.js` 的 transition 解析处，对 `flow.auditEntry` 配对的主流程步骤，加一段"audit 配额耗尽 → completed"的短路 | 改动集中在 engine 一处；语义由 `maxAuditRounds` + `auditEntry` 两个已有字段驱动，无新 flow 语法。 |

**推荐 B-2**：保持 flow JSON 纯声明、零新语法。gate 的判定函数复用 `flow-loader.js` 已有的 `openAudits()` / `activePlans()` 表达式函数（`flow-loader.js:81-94`），不引入新依赖。

#### 4.2.4 决策真值表（实施后）

| activePlans | openAudits | auditRound vs max | DRAFT_PLANS 输出 | 引擎动作 |
|-------------|------------|-------------------|------------------|----------|
| 有 | * | * | `created` | goto REVIEW_PLANS |
| 无 | * | < max | `nothing` | goto DEEP_AUDIT（跑一轮 audit） |
| 无 | 无 | ≥ max | `nothing` | **run completed**（额度用完且干净） |
| 无 | 有（audit 留了 issue） | ≥ max | `nothing` | 仍 goto DEEP_AUDIT（有 open issue 没消化，不能停） |

最后一行很关键：哪怕 audit 轮次用完，只要还有 `open` 状态的 audit 文件没转化成 plan，就不允许 completed。这防止"audit 发现问题但额度用光导致问题被丢弃"。这种情况应让 DEEP_AUDIT 子流程把 open audit 转成 plan 草稿（`draft-from-audit.md` 已有此能力），下一轮 DRAFT_PLANS 自然 `created`。

### 4.3 方案 C：`--step` 修固 + `--from-step` 新增

#### 4.3.1 废除 transition 改写，改用引擎级 `maxSteps = 1`

`main.js:583-597` 的就地 mutation **整段删除**。改成在 config 上设一个 `singleStep: true` 标志，传给 engine。engine 在 `run()` 顶部：

```js
const maxSteps = cfg.singleStep ? 1 : Infinity;
// ...
while (totalSteps < maxTotalSteps && totalSteps < maxSteps) { ... }
```

并在 `run()` 末尾：若因 `totalSteps >= maxSteps` 退出，返回 status `single_step_done`（区别于 `completed`，便于 monitor / 日志识别）。

这样无论哪条出口（`transitions` / `onError` / `onUnknown` / `onMaxRetries` / retry），都**物理上**只跑一步。G1 达成，且 flow 对象不再被 mutation。

> 兼容：`single_step_done` 在 `main.js:617` 的 exitMap 里映射到 exit code 0（视同成功完成）。

#### 4.3.2 新增 `--from-step <STEP>`

`main.js` run 子命令新增 option：

```js
.option("--from-step <step>", "从指定 step 开始执行，之后照常循环（不改变 transitions）")
```

处理逻辑（与 `--step` 互斥，同时传则报错）：

```js
if (opts.fromStep) {
  const step = flow.steps[opts.fromStep];
  if (!step) { /* 报错 + 列出可用 step，复用 list-steps 逻辑 */ }
  config.entryStep = opts.fromStep;   // 只设入口，不动 transitions
  config.singleStep = false;
}
```

`engine.run(config.entryStep)` 已经支持入口覆盖（`engine.js:1278`），无需引擎改动。G2 达成。

#### 4.3.3 安全边界：哪些 step 允许作为 `--from-step`

设计结论 #8（`mission-driver-flow-design.md`）已声明顶部步骤间不传参、各自从磁盘读数据，因此理论上任何顶部步骤都能作为入口。但仍建议在 CLI 层做**白名单提示**（不强制拦截）：

| Step | 作为 `--from-step` 起点 | 说明 |
|------|------------------------|------|
| `CHECK` | 等价默认入口 | 无意义但无害 |
| `REVIEW_PLANS` | ✅ 安全 | `draftPlans()` 为空时 forEach 短路，自然往下走 |
| `EXEC_PLANS` | ✅ 安全 | `activePlans()` 为空时短路 |
| `DRAFT_PLANS` | ✅ 安全 | 进入审计循环的典型起点 |
| `DEEP_AUDIT` | ✅ 安全 | 子流程自包含；audit 计数会正确 `+1` |

配合方案 A，`--from-step DEEP_AUDIT` 时 `auditRound` 从 0 开始计（新 run），第一轮就是 audit——正好满足用户"今天就跑一次 deep audit 然后接着循环"的诉求。

#### 4.3.4 CLI 互斥与校验

- `--step` 与 `--from-step` 互斥；同时给 → 报错退出。
- `--step` / `--from-step` 指向不存在的 step → 报错并打印 `list-steps` 输出（复用 `getTopSteps()`，`main.js:68-72`）。
- `--step` / `--from-step` 指向**子流程内部**步骤（如 `MULTI_AUDIT`）→ 拒绝，提示只接受主流程 step（`getTopSteps` 只读 `mission-driver.json` 的 `steps`，天然满足）。

### 4.4 可观测性增强（方案 D，顺带）

1. **events.jsonl**：`_emitEvent("step_started", ...)`（`engine.js:1352-1358`）的 payload 新增 `auditRound` 字段（当步骤是 auditEntry 时）。
2. **run-state.json**：§4.1.1 的两个新字段已覆盖。
3. **monitor dashboard**：`GET /api/runs/:id` 直接多返回 `auditRound` / `maxAuditRounds`；前端在 RunDetail 顶部展示 "Deep Audit: 2 / 3"。
4. **`--analyze` 复盘**：postmortem prompt 注入 `auditRound`，让复盘 agent 知道跑了几轮 audit。
5. **日志**：`engine.js:1350` 的 `_log` 行在 audit 步骤时追加 `(audit round N/M)`。

---

## 5. 实施影响与风险

### 5.1 代码改动清单（仅评估，不实施）

| 文件 | 改动 | 风险 |
|------|------|------|
| `src/engine.js` `_initWorkflow` (:303) | 新增 `auditRound` / `maxAuditRounds` 字段 | 低；纯加字段，旧 reader 不受影响 |
| `src/engine.js` `_wfOpen` (:325) | 进入 auditEntry 时 `auditRound++`（仅主流程） | 中；需正确区分主/子流程，靠 `cfg.isSubflow` |
| `src/engine.js` `run()` (:1344) | maxAuditRounds 闸门改读 `workflow.auditRound`；调整与 `_wfOpen` 的先后 | 中；顺序错误会导致 off-by-one（见 §5.2） |
| `src/engine.js` transition 解析处 | 新增 DRAFT_PLANS `nothing` → audit-gate（§4.2.3 B-2） | 中；新增一段特判，需测试覆盖真值表四行 |
| `src/engine.js` `run()` (:1288) | 新增 `singleStep` / `maxSteps` 闸门 | 低；加一个循环上界 |
| `src/main.js` (:583-597) | 删 transition mutation；改设 `singleStep` flag | 低；删代码 + 一行赋值 |
| `src/main.js` (:706+) | 新增 `--from-step` option + 互斥校验 | 低 |
| `src/main.js` delegates.vars (:540) | 新增 `maxAuditRounds`（提示用） | 低 |
| `flows/mission-driver.json` (:59-71) | DRAFT_PLANS 删 `done` 出口、调 `onMaxRetries` | 中；改变了 mission 的退出条件，需回归测试 |
| `prompts/draft-from-roadmap.md` | 删 `done` 分支、加"不要判断 audit"提示 | 中；prompt 改动需人工跑一遍验证 AI 不再输出 `done` |
| `test/skip-steps.test.js` 等现有测试 | 确认 `singleStep` 新机制不破坏 `--fast` 语义 | 低 |
| 新增测试 | `audit-count.test.js`（计数落盘）、`single-step.test.js`（四类出口都只跑一步）、`from-step.test.js`（入口覆盖不动 transitions） | — |

### 5.2 关键时序陷阱：`_wfOpen` vs maxAuditRounds 闸门

`engine.js:1344` 的闸门当前在 `_wfOpen`（:1351）**之前**。若按 §4.1.3 直接读 `workflow.auditRound`，会因为"还没递增"而 off-by-one。

两种正确写法（任选其一）：

- **写法 1**：把 `auditRound++` 挪到闸门**之前**（即 `_wfOpen` 之前先递增），闸门判 `auditRound > maxAuditRounds`。
- **写法 2（推荐）**：保持 `_wfOpen` 递增不变，闸门读"递增前值"：`const round = (name === auditEntry) ? (workflow.auditRound) : 0; if (round >= maxAuditRounds) completed;` 然后 `_wfOpen` 才 `++`。

写法 2 把"是否允许进入"和"已进入计数"分到两处，语义更清晰，且与现有 `visitCounts` 闸门（也是先判后 ++）风格一致。

### 5.3 向后兼容

- **旧 run-state.json**：无 `auditRound` 字段 → 代码用 `?? 0` 兜底，读出来是 0，行为等同"本 run 还没跑过 audit"，符合直觉。
- **旧 flow JSON（无 `maxAuditRounds`）**：`?? 0` → audit-gate 不启用，DRAFT_PLANS `nothing` 永远 goto DEEP_AUDIT（退化为旧行为，但旧 prompt 仍会输出 `done`，所以也不会死循环）。建议 flow 升级与 prompt 升级**同批**发布。
- **`--step` 语义变化**：从"transition 改写"变为"硬一步停"。对外行为更符合用户预期（用户本来就要"只跑一步"），不算破坏性。在 CHANGELOG / README 里说明即可。

### 5.4 残留风险与对策

| 风险 | 对策 |
|------|------|
| AI 在新 prompt 下仍偶尔输出 `done`（旧习惯） | DRAFT_PLANS 的 `markerAliases` 里**移除** `"done": "complete"` 别名（`mission-driver.json:23`），并在 `transitions` 里不定义 `done` → 触发 `onUnknown` → 纠正 agent → 仍不行则 `onMaxRetries: failed`。硬性逼 AI 只用 `created`/`nothing`。 |
| audit-gate 的 B-2 硬编码特判污染引擎通用性 | 把判定收敛到一个命名函数 `_shouldCompleteOnAuditQuota(currentStep, marker)`，仅当 `flow.auditEntry` 存在时生效；无 `auditEntry` 的 flow 完全不经过这段。保持引擎对"无 audit 概念的 flow"零侵入。 |
| `--from-step DEEP_AUDIT` 在没有任何 open audit 文件时跑空 | 子流程 `deep-audit-loop` 已有 `otherwise` 兜底（`MULTI_AUDIT` / `OPEN_AUDIT`），且会执行 multi/open audit prompt（若配置了）。若用户没配 `prompts.multiAudit`，子流程可能秒退——属预期，提示用户配置即可。 |
| 子流程 `deep-audit-loop` 内部的 `CHECK_OPEN_AUDITS` 会把 plan 级 closure audit 也算进 `openAudits()` | 这是 §2.3 混淆问题的**另一侧**。建议顺带细化 `flow-loader.js:81-94` 的 `_scanOpenAuditsList`：只扫描文件名匹配 `*multi-audit*` / `*open-audit*` 的文件，或读取 `> Audit Type:` 头区分类型。**本方案不强制做这一步**（方案 B 已把决策权从 AI 拿走，即使扫描宽泛，引擎也能据 `auditRound` 正确退出），但列为推荐后续改进。 |

---

## 6. 备选方案与为何不选

### 6.1 备选 1：把 audit 计数做成跨 run 持久（写在 `docs/audits/` 或独立 manifest）

想法：用一个 `docs/audits/_audit-round.json` 记录"这个 mission 历史上跑过几轮 audit"，跨 run 累计。

**不选**：mission-driver 的语义是"每个 run 是一个自包含的循环"（`maxAuditRounds` 本就是 per-run 的 3 轮）。跨 run 累计会让"再跑一次 mission"永远秒退（额度历史性耗尽），与用户对"重新开始"的预期冲突。如需跨 run 观测，用 `--analyze` 复盘更合适。

### 6.2 备选 2：让 DRAFT_PLANS prompt 读 run-state.json 自己判断

想法：把 `auditRound` 注入 prompt，让 AI 据此输出 `done`。

**不选**：这正是当前架构的不稳定根源——把状态判断委托给 AI。即使喂了准确计数，AI 仍可能在"还有 open audit 但没转化成 plan"这种边界情况下误判。引擎判断比 prompt 判断可靠一个量级，且可测试。方案 B 把 AI 从判断链里移除，是更优解。

### 6.3 备选 3：给 flow JSON 的 transition 加 `when` 支持（方案 B-1）

想法：通用化，`"nothing": { "when": "...", "done": "completed", "otherwise": { "goto": "DEEP_AUDIT" } }`。

**不选（本期）**：能力强但改动面大（要改 expression 求值时机、transition schema、测试），且当前只有这一处用例。先用 B-2 硬编码 gate 解决问题，等出现第二个同类用例再升级到 B-1。记入后续 backlog。

### 6.4 备选 4：`--step` 用"运行后强制 done"而非"限制步数"

想法：不改循环上界，而是在任何 transition 解析后，只要 `singleStep` 就把结果强制改成 done。

**不选**：与 `maxSteps=1` 等价但更绕（要在 transition 分支里各插一道），且 retry 语义不好处理（单步模式下还允不允许 retry？）。`maxSteps` 上界最直白、最无歧义。

---

## 7. 验证策略（实施后）

1. **单元测试**
   - `audit-count.test.js`：跑一个 mock flow，进 2 次 DEEP_AUDIT，断言 `run-state.json.auditRound === 2`。
   - `single-step.test.js`：对 CHECK / DRAFT_PLANS / DEEP_AUDIT 分别 `--step`，强制触发 `onError` / `onUnknown`，断言 `stepCount === 1`。
   - `from-step.test.js`：`--from-step DEEP_AUDANS`，断言 transitions 未被改写、循环继续到 DRAFT_PLANS。
   - 决策真值表（§4.2.4）四行各一个用例。
2. **dry-run 集成**：`node src/main.js demo --from-step DEEP_AUDIT --dry-run --no-monitor` 验证编排正确。
3. **人工回归**：用真实 model 跑一次完整 demo mission，确认：
   - DRAFT_PLANS 不再输出 `done`（看 events.jsonl）。
   - audit 在 `maxAuditRounds` 用完且无 open audit 时 completed。
   - monitor dashboard 显示 `auditRound / maxAuditRounds`。
4. **旧状态兼容**：拿一个现有 run-state.json（无新字段）喂给 monitor，确认不报错。

---

## 8. 实施顺序建议

按依赖与价值排序，每步可独立合入：

1. **Step 1（方案 A）**：audit 计数落盘 + monitor 展示。低风险、立即提升可观测性，且是后续步骤的地基。
2. **Step 2（方案 C 前半）**：废除 transition mutation，改 `maxSteps=1`。修固 `--step`，独立可验证。
3. **Step 3（方案 C 后半）**：新增 `--from-step`。依赖 Step 2 的 singleStep 区分逻辑。
4. **Step 4（方案 B）**：DRAFT_PLANS 引擎化决策 + prompt 改造。依赖 Step 1 的 `auditRound`。风险最高，放最后并配齐测试。
5. **Step 5（方案 D）**：可观测性收尾（events / 日志 / `--analyze`）。

每步完成后按 `AGENTS.md` 要求更新 `docs/logs/{year}/{month}-{day}.md`。

---

## 9. 一页摘要

- **问题 1 根因**：`--step` 只改写 `transitions`，漏了 `onError/onUnknown/onMaxRetries`，异常出口让单步逃逸成完整循环。
- **问题 2 缺失**：引擎本就支持入口覆盖，只是 CLI 没暴露"不改 transitions"的入口。
- **问题 3 根因**：DRAFT_PLANS 让 AI 自判"audit 是否做过"，AI 分不清 plan 级与 mission 级 audit；且 `maxAuditRounds` 计数只在内存、不落盘。
- **枢纽**：三个问题共用同一缺失物——run-state 里的权威 audit 计数。
- **方案**：(A) `auditRound` 落盘 → (B) DRAFT_PLANS 决策引擎化（删 `done` 出口、加 audit-gate）→ (C) `--step` 改 `maxSteps=1`、新增 `--from-step` → (D) 可观测性。
- **优先级**：A → C前 → C后 → B → D。问题 3 是枢纽，但 A 是它和 1/2 共同的地基，先做 A。
