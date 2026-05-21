# Plan Authoring And Execution Guide

> Status: active workflow guide
> Last Reviewed: 2026-05-21
> Sources: `docs/logs/2026/03-31.md`, `docs/logs/2026/04-03.md`, `docs/logs/2026/04-04.md`, `docs/logs/2026/04-07.md`, `docs/logs/2026/04-08.md`, `docs/logs/2026/04-09.md`, `docs/logs/2026/04-10.md`, `docs/logs/2026/05-04.md`, `docs/logs/2026/05-05.md`

## Goal

这份指南只解决两件事：

- 计划怎么写才不容易缺项。
- 计划执行完后怎么审，才不容易漏 phase 或剩余工作。

`ai-dev/plans/` 是执行文档，不是 ideas dump，也不是 architecture 的替代品。

计划描述**做什么（what）和怎么验证（exit criteria）**，不描述**怎么做（how）**。具体实现方案、类签名属于 `ai-dev/design/` 或源码，不属于 plans。描述期望行为语义的伪代码（算法规格）是允许的，前提是它在回答"应该发生什么"。

## Lessons From History

从历史日志看，最常见的问题主要有 9 类：

1. 没先审 current baseline，直接沿用旧计划或旧 completion note。
2. 一个计划过宽，后面不得不重写或拆分。
3. 只记录最近 landing 的改动，没有回头逐条核对整个 plan。
4. 剩余工作没有明确归属，导致计划看起来完成，实际上还有隐含 debt。
5. 把"最近一个 slice 已 landing"误当成"整份 plan 可关闭"，缺少独立 closure audit。
6. 看到接口、类型、方法名已经出现，就误判对应语义已经完整落地，没有继续核对 live behavior 和 focused tests。
7. 顶部 `Plan Status`、slice `Status`、phase `Exit Criteria`、`Closure Gates` 没有一起同步，导致文本里同时出现"completed"与未勾选 closure checklist 的矛盾状态。
8. **空壳实现（Hollow Implementation）**：接口和类存在，单元测试通过，但组件之间的连线未接通（从入口点到出口点的路径不完整），或内部逻辑用 stub/placeholder/no-op 填充。典型案例：nop-stream 的 CheckpointCoordinator/BarrierAligner/ICheckpointStorage 全部存在且各有单测，但 `StreamExecutionEnvironment.execute()` 从未调用其中任何一个。根因：**组件级验证通过 ≠ 系统级功能可用**。
9. **静默跳过（Silent No-Op）**：缺失的功能用 `continue`、空方法体、或吞掉异常的方式绕过，而非快速失败。单测不检查这些路径，所以不会暴露问题。根因：**缺失功能时应抛异常或返回错误，而非静默忽略**。

所以本指南只保留最少规则，并把它们直接体现在模板里。

## Minimum Rules

1. 写计划前先核对 live repo，再写 `Current Baseline`。
2. 一个计划只负责一个明确结果面；过宽就拆成新 plan。
3. 必须有 `Goals` 和 `Non-Goals`，因为历史上真正稳定的计划几乎都靠这两段防止 scope drift。
4. 必须有 plan 级状态、execution-slice 级状态、Closure Gates。
5. 不要求给 `Purpose`、`Scope`、`Risks` 这类说明段落单独标记完成状态。
6. 只有当前 scope 真正完成，且 leftover 已明确移出 debt，才能标 `completed`。
7. 旧 baseline 失效时，显式写 `Outdated Note`、`Supersession Note` 或 `replaced/superseded`，不要让多套 baseline 并存。
8. `completed` 必须来自单独的 closure audit，不要在完成最后一个编码 slice 的同时顺手宣布 plan 关闭。
9. 任何 execution slice 只要还有一项未完成、blocked、或未移出 scope，plan 就不能标 `completed`。
10. 计划中不要放实现代码、类签名或具体实现方案。实现方案放 `ai-dev/design/`，代码放源码。计划只描述目标、步骤和验证标准。例外：描述期望行为语义的伪代码（算法规格、需求描述）可以出现在 Exit Criteria 或 Current Baseline 中，只要它是在回答"应该发生什么"而不是"代码怎么写"。
11. 关闭计划时，必须区分"contract surface 已出现"和"contract semantics 已落地"；前者不能替代后者。
12. 标记 `completed` 前，必须完成一次由独立审阅者或独立子 agent 执行的 closure audit，并把证据写进 plan 或对应 daily log。self-audit 可用于执行中的自查，但不能替代 `completed` 所需的独立 closure audit。
13. **已经进入 `lint`、静态检查脚本、或 CI fail-fast 的固定规则，都是不可降级的硬约束。** 计划里不能把这些规则改写成 advisory、follow-up、或"如有时间再做"的事项。
14. `ai-dev/design/` 下的文档**只记录架构决策和使用契约**：选了什么方案、为什么选、拒绝了哪些替代方案及原因。属于 design 层面的包括：架构约束、模块边界、接口契约（如 Meter 命名、API 命名规范、数据流方向）。**不写代码层面的类签名、方法列表、字段定义、伪代码——源码是代码层面的唯一事实。** 不写历史变迁、不写"Proposed vs Current"对比、不写演进叙事。如果一个 design doc 包含 "Proposed Design" 或 "Current vs Proposed" 章节，说明它还停留在 draft 状态，实施完成后必须重写为最终设计文档。
15. 每个 execution item 都必须能被归类为 `Fix`、`Decision`、`Proof`、或 `Follow-up`。已确认的 live defect 或 contract drift 只能属于 `Fix`，不能降级成 `Follow-up`。
16. 允许 deferred 的是优化项或已裁定为 non-blocking 的 residual，不允许 deferred 的是已确认且仍在 scope 内的 live defect、contract gap、owner-doc drift、以及未满足的硬门禁。
17. 如果某个 Phase 改变了 live baseline、public contract、或 owner behavior，该 Phase 的 Exit Criteria 必须包含相应文档更新项。纯测试拆分、纯工具整理、纯内部重构可以显式写明 `No owner-doc update required`，但不能默默跳过文档裁定。
18. **Checklist 打勾是 closure 的前置条件，不是附带动作。** 执行完一个 item 后必须立即将对应 `- [ ]` 改为 `- [x]`。标记 `Plan Status: completed` 时，文件内不得残留任何未勾选的 in-scope checklist item。如果存在未勾选项，要么完成它，要么显式移入 `Deferred But Adjudicated` 并写清原因。
19. **标记 `completed` 前，必须做一次文本一致性核对。** 至少逐项确认以下五处彼此一致：`Plan Status`、每个 slice 的 `Status`、每个 slice 的 `Exit Criteria`、`Closure Gates`、以及对应 `ai-dev/logs/` 收口记录。任何一处仍显示未完成，都不能把 plan 视为真正关闭。
20. **已标记 `completed` 的历史计划默认视为历史记录，不因后续规范演进、模板变化、或后续代码演化而主动回写。** 只有在用户明确要求、需要修复事实性错误/损坏链接、或当前活跃计划明确且经用户确认以"修订历史计划文本"为交付物时，才允许修改这类计划。对历史计划的新审计发现，默认记录在新的 analysis / active plan / daily log 中，而不是为了追求模板一致性去重写旧计划。
21. **文件命名使用两位数字递增编号**：`NN-<简短描述>.md`，其中 `NN` 为当前最大编号 + 1（如已有 `01`~`19`，则下一个为 `20`）。不使用日期前缀。`00` 保留给本 guide 文件。
22. **端到端验证规则（Anti-Hollow Rule）**：如果计划涉及管线/流程/链条式系统（如流处理引擎、批处理管线、HTTP 请求链），至少一个 Exit Criteria 必须要求**从用户入口点到最终输出**的端到端验证。组件级单测不能替代端到端验证。例如：流处理计划必须有一条测试从 `env.addSource()` 到 `sink` 输出完整跑通；checkpoint 计划必须有一条测试从 barrier 触发到状态恢复到重新处理数据完整走通。
23. **接线验证规则（Wiring Verification Rule）**：如果计划引入了新组件 A 需要与已有组件 B 协作，Exit Criteria 必须包含一条验证：**A 确实被 B（或 B 被 A）在运行时调用**。不能只验证 A 的存在和 B 的存在。验证方式可以是：在端到端测试中添加断言检查 A 的方法确实被调用（如计数器、标志位、或 mock verify），或通过代码审查确认调用链的连通性。
24. **禁止静默跳过规则（No Silent No-Op Rule）**：新代码中不允许出现以下模式作为"正常"实现：
    - 空方法体（`{}`）
    - `continue` 跳过应处理的逻辑分支
    - 吞掉异常不抛出不记录（`catch (Exception e) {}`）
    - 返回 null/0/false 的 placeholder 当作正常返回值
    - `// TODO` 或 `// FIXME` 标记的代码被当作已完成

    如果某个功能确实需要暂缓实现，正确做法是抛出 `UnsupportedOperationException("not yet implemented: ...")` 或返回错误码——让调用方快速失败，而非静默忽略。Exit Criteria 中必须验证：新增的每个公共方法/分支路径在未实现时是显式失败的，不是静默跳过的。

## Anti-Slacking Rule

计划可以延期优化工作，但不能延期"这个 in-scope 项目到底是不是 closure 必需项"的裁定。

写法要求：

- 每个 in-scope 项在计划关闭前，必须落到且只落到以下一种状态：`landed`、`adjudicated as residual-risk-only / watch-only`、`moved to explicit successor ownership`、或 `removed from scope through a recorded scope change`。
- 不允许用 `optional`、`if time permits`、`consider`、`maybe`、`nice to have`、`as needed` 这类模糊词替代状态裁定。
- 已确认的 live defect、contract drift、owner-doc drift、或硬门禁失败项，不能放进 non-blocking follow-up。
- 可以延期的项，必须写清 `Why Not Blocking Closure`，说明为什么它不影响当前 supported baseline 成立。

### Non-Degradable Items

以下事项不能在 plan 中降级成 advisory 或 non-blocking：

- `lint` / 静态检查脚本 / CI fail-fast 中已经固定的仓库规则
- 已确认的 live defect
- 已确认的 public-contract drift
- 已确认的 owner-doc 与 live baseline 不一致
- 为已落地行为提供的必要 focused verification

### Allowed Deferred Classifications

只有以下类型可以进入 deferred / non-blocking 区域：

- `watch-only residual`
- `optimization candidate`
- `out-of-scope improvement`

它们都必须附带明确的 non-blocking 理由；没有理由的 deferred 项按未完成处理。

## Required Status Markers

### Plan-Level Status

每个 plan 顶部必须有：

- `> Plan Status: proposed | planned | in progress | partially completed | completed | superseded | replaced | deferred | cancelled`
- `> Last Reviewed: YYYY-MM-DD`
- `> Source: <<说明>>`

说明：

- `proposed` 适合已经成型但还未进入正式执行的计划。
- `superseded` / `replaced` 适合历史计划或已被新计划接管的计划。
- `deferred` 适合明确延后、不作为当前 active queue 的计划。

### Execution-Slice Status

每个 execution slice 都必须有自己的状态。slice 可以是顺序 `Phase`，也可以是并行 `Workstream`。

- `planned`
- `in progress`
- `completed`
- `blocked`
- `cancelled`

推荐做法：

- 顺序执行的计划：用 `Phase`。
- 可并行或按主题拆开的计划：用 `Workstream`。
- `## Phase Status` / `## Workstream Status` 总表是可选的，不是强制的；真正强制的是每个 slice 本身要写 `Status: ...`。

### Checklist Status

执行和验收项统一用 checkbox：

- 未完成：`[ ]`
- 已完成：`[x]`

## Template

下面这个模板就是默认格式。`<<说明>>` 保留为占位提示，写 plan 时直接替换。

历史核对后的结论：

- `Goals` / `Non-Goals` 应保留，它们在近期高质量计划里几乎是稳定项。
- `Phase Status` 总表不应强制，因为历史上很多好计划只有 slice 内状态，没有总表。
- `Phase` 与 `Workstream` 都应允许，取决于任务是顺序还是并行。
- `Closure Gates`、`Deferred But Adjudicated`、`Non-Blocking Follow-ups` 用来防止把真实问题伪装成"后续再说"。

```md
# NN <<计划标题>>

> Plan Status: planned
> Last Reviewed: YYYY-MM-DD
> Source: <<关联 architecture / analysis / logs>>
> Related: <<相关计划，可选>>

## Purpose

<<这份计划要把什么收口到什么状态>>

## Current Baseline

- <<当前已经成立的事实>>
- <<已完成但旧文档/旧计划可能还没同步的事实>>
- <<真正剩余的 gap>>

## Goals

- <<这份计划要达成的结果>>
- <<这份计划要达成的结果>>

## Non-Goals

- <<明确不在本计划内的方向>>
- <<明确不在本计划内的方向>>

## Scope

### In Scope

- <<说明>>
- <<说明>>

### Out Of Scope

- <<说明>>
- <<说明>>

## Execution Plan

<<顺序任务用 Phase；并行任务用 Workstream。二选一即可，不要求同时使用。>>

### Phase 1 - <<名称>>

Status: planned
Targets: `<<文件/模块/文档>>`

- Item Types: `Fix | Decision | Proof | Follow-up`

- [ ] <<执行项>>
- [ ] <<执行项>>

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] <<完成判定>>
- [ ] <<验证点>>
- [ ] **端到端验证**（如适用）：从用户入口点到最终输出的完整路径已验证（见 Minimum Rules #22）
- [ ] **接线验证**（如适用）：新组件与已有组件的运行时调用连通性已验证（见 Minimum Rules #23）
- [ ] **无静默跳过**（如适用）：新增方法/分支在未实现时抛出异常而非静默返回（见 Minimum Rules #24）
- [ ] <<若该 Phase 改变 live baseline：相关 `ai-dev/design/` / `docs-for-ai/` 已更新；否则明确写 `No owner-doc update required`>>
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - <<名称>>

Status: planned
Targets: `<<文件/模块/文档>>`

- Item Types: `Fix | Decision | Proof | Follow-up`

- [ ] <<执行项>>
- [ ] <<执行项>>

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] <<完成判定>>
- [ ] <<验证点>>
- [ ] **端到端验证**（如适用）：从用户入口点到最终输出的完整路径已验证（见 Minimum Rules #22）
- [ ] **接线验证**（如适用）：新组件与已有组件的运行时调用连通性已验证（见 Minimum Rules #23）
- [ ] **无静默跳过**（如适用）：新增方法/分支在未实现时抛出异常而非静默返回（见 Minimum Rules #24）
- [ ] <<若该 Phase 改变 live baseline：相关 `ai-dev/design/` / `docs-for-ai/` 已更新；否则明确写 `No owner-doc update required`>>
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Workstream 1 - <<名称>>

Status: planned
Targets: `<<文件/模块/文档>>`

- Item Types: `Fix | Decision | Proof | Follow-up`

- [ ] <<执行项>>
- [ ] <<执行项>>

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] <<完成判定>>
- [ ] <<验证点>>
- [ ] **端到端验证**（如适用）：从用户入口点到最终输出的完整路径已验证（见 Minimum Rules #22）
- [ ] **接线验证**（如适用）：新组件与已有组件的运行时调用连通性已验证（见 Minimum Rules #23）
- [ ] **无静默跳过**（如适用）：新增方法/分支在未实现时抛出异常而非静默返回（见 Minimum Rules #24）
- [ ] <<若该 Workstream 改变 live baseline：相关 `ai-dev/design/` / `docs-for-ai/` 已更新；否则明确写 `No owner-doc update required`>>
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。
>
> **纯文档计划**：如果计划不涉及任何代码变更（仅修改 `docs/` 或 `ai-dev/` 下的文件），`./mvnw test`、`./mvnw lint` 等构建验证条目可以直接从 Closure Gates 中删除，不需要执行。

- [ ] <<所有 in-scope confirmed live defects 已修复>>
- [ ] <<所有 in-scope confirmed contract drifts 已收敛>>
- [ ] <<行为/契约结果已达成>>
- [ ] <<必要 focused verification 已完成>>
- [ ] <<不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift>>
- [ ] <<受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required>>
- [ ] <<独立子 agent / 独立审阅者 closure-audit 已完成并记录证据>>
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）组件间调用链在运行时确实连通（不只是类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` (或 `-pl` 指定模块)
- [ ] `./mvnw test` (或 `-pl` 指定模块)
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### <<条目名称>>

- Classification: `watch-only residual | optimization candidate | out-of-scope improvement`
- Why Not Blocking Closure: <<一条明确理由>>
- Successor Required: `yes | no`
- Successor Path: <<如需要则填写 plan path>>

## Non-Blocking Follow-ups

- <<不影响当前 contract closure 的治理项或优化项>>
- <<不影响当前 contract closure 的治理项或优化项>>

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>

## Optional Sections

- `## Problem`
- `## Root Cause`
- `## Risks And Rollback`
- `## Outdated Note`
- `## Supersession Note`
- `## Documentation Follow-Up`
```

## How To Use The Template

### Before & After Drafting: 迭代对抗性审查

**Plan 不是写完就定稿的。** Plan 的拟制本身就是一个迭代改进的过程：写初稿 → 子 agent 审查 → 修改 → 再审查，直到所有维度的问题都被解决。

#### 为什么必须用子 agent 反复审查

历史（nop-stream checkpoint）表明：
- 设计文档描述了"要什么"但没有描述"怎么接"，导致实现者造出空壳
- Plan 格式看似正确但引用了不存在的文件路径
- Exit Criteria 看似可验证但实际无法在仓库中观察
- Phase 划分看似合理但存在未声明的依赖关系

作者自己审查自己的 plan 会产生盲区。必须用独立子 agent（不同 task_id）反复对抗性审查。

#### 审查维度（不限于此）

子 agent 的审查不应局限于某一类问题。以下是历史中出现过的问题类型，但子 agent 应自由发现任何维度的问题：

1. **想象性分析（必须）**：想象自己按 plan 执行实现，在每个步骤问"我真的知道该怎么写代码吗？"——找出设计文档到可执行代码之间的所有断层。不限于新建操作，修改、重构、删除同样需要想象性分析。
2. **Plan 格式与完整性**：是否遵循本文档定义的模板？必填字段是否齐全？Phase/Slice 状态是否一致？
3. **Plan 内容合理性**：Goals/Non-Goals 边界是否清晰？Phase 划分是否合理？Exit Criteria 是否真的 repo-observable？依赖关系是否正确声明？
4. **Plan 引用准确性**：引用的文件路径在仓库中是否存在？引用的代码位置（类名、方法名、行号）是否与实际一致？`Current Baseline` 中描述的现状是否与 live repo 匹配？
5. **任意其他问题**：子 agent 认为可能导致 plan 执行失败或产出空洞实现的任何问题。

#### 想象性分析的 prompt 模板

**这是每次审查的必做环节，不是可选步骤。** 以下是给子 agent 的 prompt 模板：

```
你是一个有经验的工程师。现在给你一份 plan 草稿、相关的设计文档、和现有的代码。
你的任务是：想象你要严格执行这份 plan 来实现功能。

对 plan 中的每个 Phase/Slice：
1. 想象你正在执行这个步骤。你要改哪个文件？改什么？改成什么样？
2. 你有足够的信息写出正确的代码吗？还是需要自己猜测或发明？
3. 这个步骤依赖的前置条件（上游 slice 的产出、设计文档的某个规格）是否明确存在？
4. 这个步骤完成后，后续步骤能接得上吗？产出物是什么？下一个步骤怎么消费它？
5. 如果执行过程中出了问题（编译失败、测试不通过、行为不符预期），plan 中有指引吗？

同时检查：
- plan 引用的文件路径在仓库中是否存在？
- plan 中描述的代码现状（类名、方法名、字段名）是否与实际代码一致？
- Exit Criteria 是否真的可以在仓库中观察和验证？

输出格式：
- 对每个发现的问题：具体位置、问题描述、如果忽略会导致什么后果、严重程度（Blocker/Major/Minor）
- 最后给出总结：这份 plan 是否可以直接执行？如果不能，哪些必须先修？
```

#### 迭代流程

```
写初稿
  ↓
子 agent 对抗性审查（包含想象性分析 + 任意维度检查）
  ↓
作者修复发现的问题
  ↓
子 agent 再次审查（可以换一个不同的 task_id）
  ↓
重复直到：没有 Blocker，作者和子 agent 对 plan 的可执行性达成共识
  ↓
进入执行阶段
```

关键规则：
- **想象性分析是每轮审查的必做环节**，不是只在第一轮做
- **审查维度不限**——子 agent 可以发现任何类型的问题
- **Blocker 必须在进入执行之前解决**——包括设计文档不够详细、plan 引用路径错误、Exit Criteria 不可验证等
- **修改后必须重新审查**——修复问题可能引入新问题

### When Drafting

1. 先写 `Current Baseline`，再写 phase。
2. 如果 `Out Of Scope` 写不清，说明 plan 还太宽。
3. 如果 `Goals` / `Non-Goals` 写不清，说明边界还不够硬。
4. 如果 slice 写不出 `Exit Criteria`，说明它还不够可执行。
5. 如果你正在规划的是一个完整 feature，先问自己这份 plan 是否真的能把 feature 收口；如果答案是否定的，再考虑拆成 successor plans，而不是一开始就把 feature 切碎。
6. `Exit Criteria` 尽量写成 repo-observable 结果——在仓库里能直接看到、能验证的东西，而不是模糊的抽象语义。不同性质的计划，"可观测"的含义不同：
   - **代码变更**：具体 API 签名、具体行为描述、对应的单元测试用例及其预期结果。
   - **文档改进**：具体文件路径、章节标题、内容与 live repo 中代码实际行为的一致性。
   - **重构治理**：具体文件行数阈值、迁移完成的文件清单、`./mvnw compile && ./mvnw test` 全过。
     判断标准：读完这条 Exit Criteria 后，任何人都能在仓库里找到对应的文件、代码或文档，明确判断它是否成立。
7. 如果计划要处理重构热点或大文件治理，先基于 live repo audit 写清当前超大文件清单、目标阈值，以及 closure 时将使用的复核命令；不要只引用旧日志或旧计划里的行数结论。
8. 为每个 execution item 标记类型：`Fix`、`Decision`、`Proof`、`Follow-up`。如果一个项已经被确认为 live defect 或 contract drift，就不能写成 `Follow-up`。
9. 如果某个 Phase 改了代码或行为，该 Phase 的 exit criteria 必须列出需要更新的 `ai-dev/design/`、`docs-for-ai/` 或 `ai-dev/logs/` 条目；如果不需要 owner-doc 更新，也要显式写出 `No owner-doc update required`。文档更新不是全局收尾工作，而是 Phase 内的工作。`ai-dev/design/` 下的文档只写最终设计状态（见 Minimum Rules 第 14 条）。
10. 如果你正在规划的是一个完整 feature，先问自己这份 plan 是否真的能把 feature 收口；如果答案是否定的，再考虑拆成 successor plans，而不是一开始就把 feature 切碎。
11. **管线/流程/链条式系统的 Exit Criteria 必须包含端到端验证项**（见 Minimum Rules #22）。问自己：如果这个 phase 完成，用户能否从入口点开始、经过所有新增/修改的组件、到达出口点，完整地使用这个功能？如果答案是否定的，说明还有未接通的线。

### When Executing

1. 开始某个 slice 时，把它改成 `in progress`。
2. slice 完成后，把该 slice 的 `Status` 改成 `completed`，并勾掉对应 checklist。
3. 非执行性的说明段落不用打完成状态。
4. 如果只完成了类型/接口/方法壳，而语义或测试还没对齐，不要把 slice 标成 `completed`；这类情况通常应保持 `in progress` 或改成 `partially completed` 的 plan-level 状态。
5. 如果某个项决定延期，先把它移到 `Deferred But Adjudicated` 或 `Non-Blocking Follow-ups`，并写清 `Why Not Blocking Closure`；不能只在 execution list 里放着不勾选。
6. 执行当前 active plan 时，如果读到的是已 `completed` 的旧计划，默认把它当作历史证据而不是待修正文档；除非用户明确要求，否则不要顺手按新模板回写历史计划。
7. **禁止写空壳代码**（见 Minimum Rules #24）。如果某个功能确实需要暂缓，抛出 `UnsupportedOperationException`，不要写空方法体或 `continue` 跳过。空壳代码会让后续开发者误以为功能已实现，增加调试成本。

### When Closing The Plan

关闭前必须做 9 件事：

1. 从头重读整份 plan，不只看最近 landing 的部分。
2. 逐条核对每个 slice 的 `Exit Criteria`。
3. 逐条核对 `Closure Gates`。
4. 逐项核对文本一致性：`Plan Status`、每个 slice 的 `Status`、每个 slice 的 `Exit Criteria`、`Closure Gates`、`ai-dev/logs/` 收口记录必须彼此一致，不能保留"顶部已 completed、内部仍未勾选"的状态。
5. 把剩余工作写进 `Follow-up`，明确 successor plan 或明确无剩余 debt。
6. 明确区分"接口存在"与"行为完成"（对代码变更计划：至少抽查一轮 live code path 和 focused tests；对纯文档计划：抽查文档内容与 live repo 代码的一致性），确认实现语义真的满足 exit criteria。
7. 由独立审阅者或独立子 agent 做 closure-audit，并在 plan 或对应 daily log 中记录证据。这里的独立子 agent 指为 closure audit 单独启动的 fresh session，而不是复用实现阶段的同一 task session 继续自查。
8. 逐条检查 deferred / follow-up 项是否真的 non-blocking，确认没有把 in-scope live defect、contract drift、或硬门禁失败项偷偷改写成"后续再做"。
9. **Anti-Hollow 检查**：closure audit 必须验证（a）新增组件被已有组件在运行时确实调用（不只是 import 存在），（b）端到端路径从入口点到出口点完整连通，（c）无空方法体/静默跳过/no-op 作为正常实现。验证方法：读代码追踪调用链（从 `execute()` 到算子到 sink），或运行端到端测试并检查关键断言。

如果这些事没做完，就不要把 `Plan Status` 改成 `completed`。

### Closure Audit Rule

把 plan 改成 `completed` 前，必须把"执行"与"收口审计"当成两件事。

最低要求：

1. 关闭动作必须发生在一次明确的 closure-audit pass 中，而不是某个实现 slice 的顺手附带动作。
2. closure audit 要回看 live repo，而不是只看旧 completion note、旧 checklist、或最近一次提交说明。
3. closure audit 必须由独立审阅者或独立子 agent 执行；实现者自己的 self-audit 不能单独作为 `completed` 的依据。
4. 每个 `Phase` / `Workstream` 都必须已经是 `completed`，否则 plan 不能关闭。
5. 如果某个 slice 的工作不再属于本 plan，先把它显式移到 successor plan 或标注取消原因，再关闭本 plan。
6. `Closure Gates` 中的未完成项只能保留在 plan 仍未关闭时；若计划关闭，这些项也必须完成或被移出当前 scope。
7. closure audit 必须抽查"关键行为是否真的被实现"，不能只因为接口、类型、方法名、或注释已经存在就判定完成。
8. 如果 closure audit 发现 only-partial landing，必须把 plan 改成 `partially completed` 或 `in progress`，而不是勉强保留 `completed`。
9. closure audit 必须检查 deferred / follow-up 项的分类是否诚实；已确认的 live defect、contract drift、owner-doc drift、或硬门禁失败项不能留在 non-blocking 区域。
10. **closure audit 必须做 Anti-Hollow 检查**：验证组件间调用链在运行时确实连通（不只是类型系统），验证无空方法体/静默跳过/no-op 作为正常实现。具体操作：从用户入口点追踪代码到出口点，确认每个新增组件都在这条链上被调用。

推荐 closure-audit 证据来源：

- live code or docs paths that satisfy each slice exit criterion
- focused verification results or a clearly cited already-green workspace baseline
- daily log entry recording the closure pass and any final doc-sync work
- independent reviewer / subagent findings with task id or cited review note that explicitly check for plan/doc drift and interface-vs-semantics mismatch
- explicit justification for each deferred item that remained non-blocking at closure
- **anti-hollow evidence**：端到端测试路径和结果，或代码追踪证明调用链连通的截图/日志

实操上可以把 closure audit 理解为一轮独立复核：

- 不是"我刚做完最后一项，所以应该没问题"
- 而是"我现在重新核对整份计划，确认没有剩余 plan-owned work"

一个常见误区：

- "接口已经有了，所以这一 phase 应该算完成"

正确做法：

- 继续核对该接口是否真的被调用、是否满足文档语义、是否有 focused tests 证明行为成立；否则最多算 partial landing。

另一个常见误区：

- "单元测试通过了，所以功能应该没问题"

正确做法：

- 单元测试只验证组件级行为。如果组件之间的连线没有接通（如 CheckpointCoordinator 从未被 execute() 调用），组件级单测全部通过也不能证明系统功能可用。必须额外验证端到端路径的连通性。

## Practical Rule

计划不需要写得很长，但必须一眼看清 3 件事：

- 当前 baseline 是什么。
- phase 到哪一步了。
- 剩余工作归谁。

补充判断：

- 如果读者看完 plan 仍然不知道"这个 feature 什么时候算真正可用"，说明计划可能被切得过碎。
- 如果计划中的多个 slice 只有全部完成后 feature 才第一次成立，那么默认应把它们放在同一个 owner plan 下，直到 live repo 证据证明需要拆分。
