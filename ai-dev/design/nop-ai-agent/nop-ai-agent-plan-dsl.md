# Nop AI Agent Plan DSL

## 1. 目标

本篇以 `agent-plan.xdef` 为中心说明 Plan DSL。

对应 schema：

- `/nop/schema/ai/agent-plan.xdef`

Plan 是结构化执行控制对象，不是普通备注列表。文档重点是解释：

- `plan` 各节点的语义
- 哪些字段真正驱动执行
- 哪些字段主要用于记录和恢复

这里要特别强调：

- `plan` 不是完整的人类计划文档模板
- `plan` 是 AI 与运行时引擎协作的结构化协议
- 只有运行时能明确理解和校验的部分，才应该进入强约束结构
- 其余说明性内容应允许 AI 自由发挥

## 1.1 Hard Contract 与 Soft Narrative

建议把 plan 中的信息分成两类：

### Hard Contract

运行时能消费、校验、并据此阻止结束的内容：

- plan / phase / task 的状态
- phase / task 结构
- task 依赖关系
- 当前 phase / 当前 task 的推进状态
- success criteria
- 错误记录与解决状态
- completion / validation 类检查项

### Soft Narrative

主要给 AI 和人阅读，不要求运行时完全理解的内容：

- 背景说明
- 决策理由
- 扩展笔记
- 临时分析
- 长段解释文本

当前 `agent-plan.xdef` 中，下面这些字段更偏 hard contract：

- `currentPhase`
- `status`
- `phases/tasks/subTasks`
- `dependsOn`
- `startedAt/completedAt`
- `errors.blocking`
- `errors.resolvedAt`
- `validationChecklist`
- `closure`

下面这些字段更偏 soft narrative：

- `goal`
- `instructions`
- `resultMessage`
- `errorText` / `resolution`
- Markdown-only sections such as `Questions`, `Decisions`, `Additional Notes`

## 2. DSL 入口

根节点：

```xml
<plan title="..." currentPhase="..." currentTaskNo="..." createdAt="..." updatedAt="..."
      reviewedAt="..." status="...">
    <purpose>...</purpose>
    <goal>...</goal>
    <currentBaseline>...</currentBaseline>
    <sources>...</sources>
    <relatedPlans>...</relatedPlans>
    <successCriteria>...</successCriteria>
    <nonGoals>...</nonGoals>
    <scope>...</scope>
    <readFiles>...</readFiles>
    <writtenFiles>...</writtenFiles>
    <phases>...</phases>
    <errors>...</errors>
    <validationChecklist>...</validationChecklist>
    <closure>...</closure>
</plan>
```

这套结构与 AGE Plan Template 保持一致的 authoring direction，但 runtime 只消费其中的 hard-contract 子集。

## 3. 顶层字段语义

### 3.1 `currentPhase`

- 指向当前正在执行的阶段
- 是运行时恢复和进度展示的重要字段

### 3.1.1 `title`

- plan 标题
- 对应 Markdown 的 `# <Plan Title>`

### 3.1.2 `currentTaskNo`

- 指向当前正在推进的任务
- 适合让运行时和 AI 对齐当前焦点任务

### 3.1.3 `reviewedAt`

- 对应 Markdown 的 `Last Reviewed`
- 用于计划审计、回顾和新鲜度判断

### 3.2 `createdAt` / `updatedAt`

- 用于审计、恢复和调试
- 不直接驱动控制流

### 3.3 `status`

- 表示整个 plan 的执行状态
- 与 phase/task 的状态共同组成整体进度视图
- 它是运行时强校验的核心字段之一

### 3.4 `goal`

- Plan 的总目标
- 是阶段和任务分解的上层约束
- 压缩上下文时，通常应优先保留
- 但它本身通常不作为“是否允许结束”的强判定条件

### 3.5 `purpose`

- 对应 Markdown 的 `## Purpose`
- 用于解释这份计划为什么存在

### 3.6 `currentBaseline`

- 对应 Markdown 的 `## Current Baseline`
- 用于记录当前状态的英文基线摘要

### 3.7 `sources` / `relatedPlans`

- 对应 Markdown 顶部的 `Source` / `Related`
- 用于保留来源和关联计划引用

### 3.8 `successCriteria` / `nonGoals`

- `goal` 是单个总目标
- `successCriteria` 对应 Markdown 的 `## Success Criteria`
- `nonGoals` 对应 Markdown 的 `## Non-Goals`

这样可以避免两个层级都叫 goal：

- `goal` = 总目标
- `successCriteria` = AI 和人可读的成功结果列表

当前建议把 `successCriteria` 视为接近 narrative 的边界字段：

- 它帮助 AI、审阅者和运行时对齐“这份 plan 要收口到什么结果”
- 它属于 hard contract 的边界字段
- 但 runtime 最终的完成判定仍应主要依赖 `exitCriteria`、`checks`、`validationChecklist`

### 3.9 `scope`

- 对应 Markdown 的 `## Scope`
- 分成 `inScope` / `outOfScope`
- 虽然更多偏 narrative，但对 AI 和运行时边界控制都很重要

## 4. 文件记录节点

### 4.1 `readFiles`

- 记录已经读取过且有价值的文件
- 每项包括：`path`、`purpose`、`readAt`

语义：

- 这不是文件系统快照，而是 Agent 认知轨迹的一部分
- 适合在长任务中避免重复探索

### 4.2 `writtenFiles`

- 记录已经写过的文件
- 每项包括：`path`、`summary`、`lastModified`

语义：

- 这不是 VCS 历史，而是 Agent 工作产物记录
- 对恢复和总结有价值

## 5. `phases`

### 5.1 `phase`

每个阶段包含：

- `name`
- `kind`
- `status`
- `startedAt`
- `completedAt`
- `description`
- `targets`
- `exitCriteria`
- `tasks`

语义：

- `phase` 是高层任务分类单位
- 适合表达“大步骤”
- 不应过细；过细会退化成 task 平铺
- `kind` 允许对齐 Markdown 中的 `phase` / `workstream`
- `targets` 对应 Markdown 中的 `Targets`
- `exitCriteria` 对应 Markdown 中的 phase-level checkbox 验收项

### 5.2 `tasks`

每个任务包含：

- `taskNo`
- `title`
- `dependsOn`
- `status`
- `startedAt`
- `completedAt`
- `instructions`
- `resultMessage`
- `checks`
- `subTasks`

语义补充：

- `taskNo` 是任务主键
- `dependsOn` 用于表达依赖，不等于自动调度已经实现
- `instructions` 是任务执行指令正文
- `resultMessage` 是任务完成后的摘要结果
- `checks` 对应 Markdown 的 task-level checkbox

运行时应优先把 `task` 理解为：

- 一个最小可跟踪执行单元
- 一个可以被标记为 `pending / running / completed / failed` 的对象
- 一个可以被依赖和被校验的对象

### 5.3 `subTasks`

- 用于递归分解任务
- 适合在复杂任务内部再细分步骤

推荐约束：

- 控制递归深度
- 不要把简单线性流程全部塞进 `subTasks`

## 6. Errors, Validation, And Closure

### 6.1 `errors`

- 记录值得保留的错误
- 每项包括：`attemptNumber`、`resolvedAt`、`relatedTaskNo`、`blocking`、`errorText`、`resolution`

语义补充：

- `resolvedAt=null` 的错误可以视为未解决错误
- 如果错误被标记为 blocking，则应能阻止 plan 结束

### 6.2 `validationChecklist`

- 对应 Markdown 的 `## Validation Checklist`
- 适合放 plan-level required checks

### 6.3 `closure`

- 对应 Markdown 的 `## Closure`
- 包含 `statusNote`、`auditEvidence`、`followUps`
- 这是结束前的重要结构化出口

Markdown 中的 `Questions`、`Decisions`、`Additional Notes` 仍然建议保留为 authoring guidance，但它们不属于当前 runtime hard contract，因此不进入当前 `agent-plan.xdef`。

## 7. 运行时强校验建议

> **落地状态（W1-1/W1-2/W1-3）**：项 1/4 已通过 `AgentPlanValidator`（加载时 `AgentPlan.init()` → `INeedInit` hook）接入真实加载路径；gate 门控（项 5 的变体）已由 `PlanRunner.checkGate()` 实现。项 2/3（phase.name 唯一、task.taskNo 唯一）已纳入 `AgentPlanValidator` 结构校验。

运行时不需要理解 plan 的全部叙述内容，但应对 hard contract 做强校验。

建议至少校验：

1. `currentPhase` 必须存在于 `phases`
2. `phase.name` 必须唯一
3. 同一 `phase` 下 `task.taskNo` 必须唯一
4. `dependsOn` 指向的任务必须存在（悬空依赖 fail-fast）；全局 `dependsOn` DAG 不含环（复用 nop-task `GraphStepAnalyzer`）
5. 未完成依赖的 task 不能被标记为 `completed`
6. `completedAt` 不能早于 `startedAt`
7. phase 标记为 `completed` 时，其下 task 不能还有未完成项
8. plan 标记为 `completed` 时，不能还有未完成 phase/task
9. plan 标记为 `completed` 时，不能还有 `blocking=true` 且未解决的 error
10. plan 标记为 `completed` 时，必要的 validation/check 项必须已通过

这些校验都是运行时能明确判断的内容，因此应作为强约束。

## 8. Completion Blocking 规则

> **落地状态（W1-1）**：gate 门控的阻断结束语义已由 `PlanRunner.checkGate()` 实现——gate `on-fail=block` 时后续阶段被阻断；`on-fail=escalate` 时 status 置 escalated（对应下文"不满足强校验就不能结束 plan"）。

建议明确一条核心规则：

- 不满足强校验，就不能结束 plan

这意味着：

- AI 可以先生成不完整计划
- AI 可以先补 narrative 内容
- 但运行时不允许把 plan/phase/task 置为最终完成状态，除非 hard contract 满足

建议阻断条件至少包括：

- 还有未完成依赖
- 还有未完成 task
- 还有未解决 blocking error
- 还有未通过的 required checks
- phase gate 未放行（`on-fail=block` / `require-explicit-verdict=true` 但无 verdict 时阻断结束）

## 9. 建议补强但不必过度扩展的字段

如果后续要增强 `agent-plan.xdef`，优先考虑补充真正可校验的字段，而不是人类文档模板字段。

比起加入更多 narrative-only 文档段落，更值得考虑的是：

- `blocking="boolean"` on error
- `checks` / `validationChecks`
- `currentTaskNo`
- `planOwner` 或 `executor`

这些字段更接近运行时协作协议。

## 10. Markdown 对齐原则

`agent-plan.xdef` 现在的目标不是表达完整 Markdown plan，而是表达“可由 Markdown 中的 hard-contract 子集与 XML 做部分结构化映射”的最小结构化协议。

建议遵守：

1. Markdown plan 全部使用英文
2. 标题、状态、phase、task、check、closure 都必须有稳定结构
3. narrative 允许 AI 自由发挥，但 hard-contract 字段必须固定
4. XML 与 Markdown 的字段名和章节尽量一一对应

标准 plan 写作规范和 canonical template 见项目 AGE Plan Template，本文档仅定义 Agent Plan DSL 的运行时语义。

## 11. 最小样例

```xml
<plan title="Example Plan" currentPhase="analysis" currentTaskNo="B1" status="pending">
    <purpose>Define a plan that can be converted between Markdown and XML.</purpose>
    <goal>Define AI agent plan semantics</goal>
    <currentBaseline>Current schema supports task trees but needs stronger plan conversion alignment.</currentBaseline>

    <phases>
        <phase name="analysis" kind="phase" status="completed">
            <description>Read current code and identify gaps</description>
            <targets>
                <target id="target-1" path="nop/schema/ai/agent-plan.xdef">Schema file</target>
            </targets>
            <exitCriteria>
                <criterion id="crit-1" completed="true" required="true" blocking="true">Plan schema reviewed</criterion>
            </exitCriteria>
            <tasks>
                <task taskNo="A1" title="Read agent schema" status="completed">
                    <instructions>Read agent.xdef and summarize semantics</instructions>
                    <resultMessage>Schema read and analyzed</resultMessage>
                    <checks>
                        <check id="chk-a1" completed="true" required="true" blocking="true">Schema understanding captured</check>
                    </checks>
                </task>
            </tasks>
        </phase>

        <phase name="implementation" kind="phase" status="running">
            <description>Refine runtime semantics based on existing DSL</description>
            <tasks>
                <task taskNo="B1" title="Define runtime interpretation" dependsOn="A1" status="running">
                    <instructions>Describe how runtime interprets agent and tool DSL</instructions>
                </task>
            </tasks>
        </phase>
    </phases>
</plan>
```

## 12. 运行时解释边界

> **落地状态（W1-1/W1-2/W1-3）**：下面四项中，前两项已建立运行时——加载时校验（`AgentPlanValidator` 经 `INeedInit` 接入）+ DAG 依赖拓扑调度/就绪计算（`PlanScheduler` + `PlanDagBuilder`）。Gate 门控（`PlanRunner`）已建立运行时库。自动恢复引擎（Replan）仍为后续。

Plan DSL 本身只表达结构化协作协议，不自动等于：

- 已有完整任务调度器
- ~~已有依赖拓扑执行器~~ → **已建立**（W1-3：`PlanDagBuilder` 环检测 + `PlanScheduler` 就绪计算 + `AgentPlanValidator` 加载时校验）
- 已有自动恢复引擎
- 已有完整人类计划文档模板

因此设计文档需要明确区分：

- `xdef` 已能表达什么
- 当前 runtime 真正实现了什么

## 13. 本篇结论

Plan 设计的第一主角应该是 `agent-plan.xdef`。

文档的重点不是再解释“为什么需要 plan”，而是说明：

- `goal/phases/tasks/subTasks` 分别承担什么角色
- 哪些节点用于驱动执行
- 哪些节点用于记录认知轨迹和恢复信息
- 哪些部分是 hard contract
- 哪些部分允许 AI 自由发挥

一句话：

- `plan` 不是为了完整表达 repo 级计划文档
- `plan` 是为了让 AI 能用英文写、让 Markdown/XML 可互转、让运行时能判、并在不满足强校验时明确阻止结束

---

## 14. 外部调研驱动的增量设计（2026-08-01：Gate 门控 / Trigger Rule / DAG / Replan）

> 来源：agent-survey 46 份外部项目分析（jcode DAG / codewhale Gate / archon Trigger / spec-kit 阶段回退 / browser-use replan nudge）。nop 已超越各外部实现，本节按 nop 风格（XDEF 声明式 + 结构化运行时）吸收其**语义增量**。

### 14.1 Gate 门控（阶段验收点）

> **落地状态：已实现（W1-1）**。`agent-plan.xdef` `<phase>` 已含 `<gate>` 子元素（属性 `on-fail`/`max-retries`/`require-explicit-verdict`/`verdict` + `criteria` 子节点复用 `AgentPlanCriterion`）；运行时 `PlanRunner.checkGate()` 实现完整判定语义（`io.nop.ai.agent.plan.runtime.PlanRunner`），判定分支全覆盖测试（`TestPlanRunnerGateSemantics`）。

AgentPlanPhase 边界增加 Gate 定义（对标 codewhale gates.rs + spec-kit gate + jcode is_gate 三合一）：

```xml
<phase name="P1" title="重构接口">
    <gate on-fail="retry|block|escalate" max-retries="3" require-explicit-verdict="true" verdict="true">
        <!-- 验收标准：全通过才进入下一阶段 -->
        <criteria>
            <criterion id="..." completed="true" required="true" blocking="true">测试通过且接口变更已应用</criterion>
        </criteria>
    </gate>
</phase>
```

- `on-fail`（枚举 `GateOnFail`）：retry（回到本阶段）/ block（阻塞后续）/ escalate（升级人工，对应 AgentExecStatus.escalated）；默认 block
- `max-retries`：retry 模式下的最大重试次数（首次尝试不计入，即 max-retries=2 允许首次 + 2 次重试 = 3 次尝试）；耗尽后按 escalate 处理
- `require-explicit-verdict`：防"自动通过"（外部 codewhale 的教训）；为 true 时须有 `verdict=true` 记录，否则 gate 显式阻止（非静默放行）
- `verdict`：gate 级显式裁决字段（由非执行者来源设置），配合 `require-explicit-verdict` 使用
- 运行时：`PlanRunner.checkGate(phase, attempt)` 遍历 criterion 判定（结构化，非 grep），返回 `GateCheckResult`（PASSED/RETRY/RETRY_EXHAUSTED/BLOCKED/ESCALATED/EXPLICIT_VERDICT_REQUIRED）
- 判定语义：criterion satisfied = `completed==true`；gate pass = 所有 `required=true` criterion satisfied；unsatisfied `blocking=true` → 硬失败（不论 required）

### 14.2 Trigger Rule（节点依赖语义）

> **落地状态：已实现（W1-2）**。`agent-plan.xdef` `<task>` 已含 `triggerRule` 属性（枚举 `io.nop.ai.agent.plan.model.TriggerRule`）；运行时 `PlanScheduler.getReadyTasks(plan)` 按 trigger + 全局 DAG 拓扑计算就绪任务集（`io.nop.ai.agent.plan.runtime.PlanScheduler`），4 种 trigger 各有测试（`TestPlanScheduler`）。

任务/阶段依赖增加 trigger 类型（对标 archon 的 4 种）：

| trigger | 语义 | 场景 |
|---------|------|------|
| `all_success` | 所有依赖成功才就绪 | 默认 |
| `one_success` | 任一依赖成功即就绪 | 容错备选 |
| `none_failed_min_one_success` | 至少一成功且无失败 | 部分成功继续 |
| `all_done` | 所有依赖结束（无论成败） | 容错汇聚 |

运行时：`PlanScheduler.getReadyTasks(plan)` 按 trigger 计算就绪条件（拓扑序 + 就绪检查），返回当前可执行任务列表。

### 14.3 DAG 任务依赖（跨 phase 图结构）

> **落地状态：已实现（W1-3）**。`dependsOn` → nop-task `Dag`/`GraphStepAnalyzer` 环检测桥已建立（`io.nop.ai.agent.plan.runtime.PlanDagBuilder`，复用 nop-task 真实 `GraphStepAnalyzer.analyze()`，同 `TeamTaskGraphBuilder` 模式）；加载时校验已接入（`AgentPlanValidator` 经 `AgentPlan.init()` → `INeedInit` hook 自动调用，含环检测 + 悬空依赖 + taskNo 全局唯一 + currentPhase 存在 + phase.name 唯一）；DAG 全局扁平化裁定（跨 phase + subTasks 递归计入）已文档化并实现。

AgentPlanTaskModel 增加 `dependsOn` 列表（集合内引用），由 nop-task GraphTaskStep 承载执行（复用，非自建）：

- 环检测：nop-task `GraphStepAnalyzer.analyze()`（复用真实实现，含 `containsLoop()`）；桥接：`PlanDagBuilder.buildDag(plan)` 递归扁平化所有 phase 的 task 及 subTasks → `GraphTaskStepModel` → `GraphStepAnalyzer.analyze()`
- **DAG 作用域裁定**：全局扁平化——所有 phase 的 task（含递归 subTasks）为一个图；跨 phase / 跨 subTask 依赖有效；taskNo 全局唯一
- 加载时校验：`AgentPlanValidator.validate(plan)` 经 `AgentPlan.init()`（`INeedInit`）在 xdef 加载路径自动调用——环/悬空/重复 taskNo 均 fail-fast（非静默接受）
- 就绪计算：`PlanScheduler` 按 trigger rule + 依赖状态计算 ready 集合

### 14.4 Replan（停滞检测 → 重规划）

> **落地状态：部分实现（W1-4 首切）**。停滞信号集 + 决策契约 + 幂等机制 + ESCALATE/CONTINUE 运行时已落地（`io.nop.ai.agent.plan.runtime`：`PlanExecutor` 消费 `PlanRunner`/`PlanScheduler` 驱动状态机并记录 `AgentPlanError`；`StagnationDetector` 产出结构化停滞事件；`PlanReplanner` 产出幂等 `ReplanDecision`）。`ROLLBACK_PHASE`/`SPLIT_TASK` 决策契约已定义，运行时实现延后 successor（未实现时抛 `UnsupportedOperationException`，非静默跳过）。

**前置架构事实**：截至 W1-4 首切，`PlanRunner.checkGate` 与 `PlanScheduler.getReadyTasks` 均为无状态查询，**无任何代码消费它们驱动 phase/task 状态推进**。Replanner 没有宿主可挂载。故 W1-4 首切必须先建立宿主（host 裁定见 §14.5），再在其上挂停滞检测与 replanner。

#### 14.4.1 停滞输入信号集

plan/phase/task 级"无进展"的可观测信号。与 ReAct 级 `SessionGoalTracker` STUCK 区分——后者作用单 session 的 tool-call 重复签名，整 session escalate-and-abort，**不作用于 plan phase/task**，不并入本信号集。

| 信号 | 语义 | 触发条件 | 载荷 |
|------|------|----------|------|
| `GATE_EXHAUSTED` | 阶段门控重试预算耗尽 | `GateCheckResult.Outcome == RETRY_EXHAUSTED`（结构性判定，非计数推断） | 目标 phase + attempt |
| `TASK_STALLED` | 任务连续 N 调度周期无状态推进 | 非终结态 task 连续 `staleTaskCycles` 周期 status 不变，或连续失败重试 | taskNo + 连续周期/失败计数 |
| `REPEATED_ERRORS` | 同一 task 累积未解决错误达阈值 | 同一 `relatedTaskNo` 的未解决（`resolvedAt==null`）`AgentPlanError` 数 ≥ `maxErrorsPerTask` | taskNo + 未解决错误计数 |

阈值（`staleTaskCycles`/`maxErrorsPerTask`）可配置，留生产调参空间，默认值定义于运行时。`GATE_EXHAUSTED` 无阈值——它复用 gate 自身的 `max-retries` 耗尽判定（§14.1），不重复配置。

#### 14.4.2 决策契约

停滞事件 → 重规划决策。决策空间（枚举契约）：

| 决策 | 语义 | 首切落地状态 |
|------|------|--------------|
| `CONTINUE` | 无停滞信号，继续推进状态机 | ✅ wired |
| `ESCALATE` | 停滞达阈值，升级（plan/phase status 置 `escalated`） | ✅ wired |
| `ROLLBACK_PHASE` | 回退到前置阶段（task 状态重置） | 契约定义，运行时延后 successor |
| `SPLIT_TASK` | 拆分/合并任务（DAG 动态增删节点） | 契约定义，运行时延后 successor |
| `ABORT` | 终止 plan | 契约定义，保留决策槽位 |

决策载荷：决策类型 + 目标 phase/task + 触发信号类型 + 理由文本。

**确定性边界**：决策是输入停滞状态的纯函数——给定相同信号集 + 计数 + 目标 identity，产出相同决策。映射规则固定且无歧义：`GATE_EXHAUSTED`/`TASK_STALLED`/`REPEATED_ERRORS` → `ESCALATE`；无停滞信号 → `CONTINUE`。`ROLLBACK_PHASE`/`SPLIT_TASK` 的触发条件由 successor 定义（首切不产出；首切内调用即快速失败，非静默跳过）。

#### 14.4.3 状态突变语义 + freeze 裁定

`AgentPlan extends AbstractComponentModel`：所有 setter 经 `checkAllowChange()`，frozen 后抛异常；`freeze(cascade)` 深冻结集合。xdef 加载的模板是**冻结不可变声明**。

**裁定**：host 运行在 **mutable runtime execution-state**（叠加在冻结模板之上）。`ResourceComponentManager` 加载时对模板执行 `freeze(true)`（级联深冻结），且 `cloneInstance()` 为浅拷贝（共享已冻结的子模型），故 host 不直接突变加载的模板对象。所有运行时状态突变——task status 推进、`AgentPlanError`（或等价运行时错误记录）写入、phase 推进、未来 ROLLBACK/SPLIT 的 DAG 增删——作用于 host 持有的 mutable execution state（`PlanExecutionState`），它以可变覆盖层（mutable overlay）镜像 task/phase 的运行时 status 与错误记录。**冻结 xdef 模板永不被突变**，只作为只读声明被读取（gate 定义、DAG 结构、trigger rule）。这使 `checkAllowChange()` 语义保持完整（模板常冻结），同时允许执行器驱动状态机。

`ROLLBACK_PHASE` 语义（successor）：重置目标 phase 的 task status（completed→pending）+ phase status 回退。`SPLIT_TASK` 语义（successor）：向运行时副本 DAG 插入/移除 task 节点。两者首切不实现，但突变目标裁定为运行时副本（非模板），为 successor 锁定方向。

#### 14.4.4 幂等 + checkpoint 交互

**幂等机制**：相同输入停滞状态 → 相同 `ReplanDecision`。输入状态 hash 由结构性/序数字段组成（信号类型、目标 phase/task identity、计数）。**时间类信号（`encounteredAt`/`startedAt` 等 wall-clock）排除在 hash 之外**，仅序数计数参与——保证相同可观测停滞在不同时刻求值得相同决策。

**checkpoint 交互裁定**：replan 决策**不入 reliability checkpoint 系统**。理由：

1. reliability checkpoint（`reliability/` 全模块）捕获 ReAct-loop 级执行状态（tool call、message 序列），plan-level replan 是更高层关注，混入会越界。
2. plan-level replan 作用于内存 `AgentPlan` 运行时副本，其持久化随 plan 模型自身持久化（plan-owned durability），而非 reliability checkpoint。
3. 幂等性由结构保证（纯函数 + 确定性输入 hash），崩溃/恢复时从持久化 plan 状态重放相同停滞状态 → 重现相同决策，不依赖 checkpoint。

故 reliability checkpoint 保持 ReAct-loop 级职责，不承载 plan-level 决策。replan 决策若需审计留痕，落 plan 模型自身的 `errors`/`closure`（plan-owned）。

#### 14.4.5 与 §13.3 / W2-3 三级失败升级的边界

replan `ESCALATE`（W1-4）与 §13.3 三级失败升级（W2-3）**互补不重叠**：

- **W2-3**（`max_aegis_rejections`/`stale_task_max_retries`/`max_dispatch_retries`）：单次 task attempt 内的失败升级（质量/停滞/基础设施失败，dispatch 层）。作用**单次 attempt 执行**。
- **W1-4 replan ESCALATE**：多次 attempt/cycle 累积后 plan/phase/task 级停滞（gate 耗尽、task 不推进）。作用**attempt 之上**。
- **关系**：W2-3 的失败信号是 W1-4 `REPEATED_ERRORS` 信号的聚合输入源；W1-4 不重实现单 attempt 重试（那是 W2-3 / L3 职责）。

#### 14.4.6 与 security 否认层 `DenialSuggestedStep.REPLAN` 的区分

`DenialSuggestedStep.REPLAN`（security 否认层）是 ReAct-loop 级单步否认恢复建议（一次 tool-call step 建议），与 plan/phase/task 级 replanning **无关**。命名偶合，作用层不同（denial 步内恢复 vs plan 级停滞重规划），此处明示区分避免命名/语义混淆。

### 14.5 与 nop-task 的边界 + host-runtime 裁定

**host-runtime 裁定（W1-4）**：三候选——(i) 自建最小 plan 执行器消费 `PlanRunner.checkGate`+`PlanScheduler.getReadyTasks`；(ii) §14.5 nop-task 迁移；(iii) 挂载现有 ReAct/引擎循环。

- **(iii) 塌缩为 (i)**：现有引擎无 phase-transition hook，ReAct 循环作用单 session tool-call，无法驱动 plan phase/task 状态机。"挂载 ReAct 循环"实际等于无法推进 phase，故 (iii) 不成立。
- **(ii) 被排除**：nop-task 迁移本身是 Out-Of-Scope successor（见 plan Deferred But Adjudicated）。host 裁定 gate 要求所选 host 使 Phase 2 单计划可关闭；nop-task 迁移违反此约束，故禁止选 (ii) 作为首切 host。
- **裁定 (i)**：自建最小自包含 plan 执行器（`PlanExecutor`）作为 replanner 宿主。它消费 `PlanScheduler.getReadyTasks` 驱动 task、`PlanRunner.checkGate` 驱动 phase、写入 `AgentPlanError`——这是停滞检测的真实输入源。执行器经外部 `TaskRunner` 回调推进单 task（不依赖完整 LLM/agent 引擎，无 phase hook 需求），在进程内驱动状态机，可独立测试，使今日"产出无人消费"的 `PlanRunner`/`PlanScheduler` 有了真实消费者。

**DSL/nop-task 边界（方向，迁移为 successor）**：

- 声明层（本 DSL）：AgentPlan + Gate + Trigger + dependsOn —— XDEF 静态定义
- 执行层（未来 nop-task 复用）：GraphTaskStep 调度 DAG + ChooseTaskStep 路由 gate 结果 + RetryTaskStepWrapper 重试
- 职责分离：DSL 描述"计划是什么"，nop-task 执行"计划怎么跑"（与 mission-driver 移植设计一致）。首切 (i) 的最小执行器是 nop-task 迁移前的过渡形态——成功落地 replanner 后，迁移到 nop-task 是纯执行层 successor（不动 DSL 声明层，不动 replanner 决策契约）。
