# Cycle 1 / I6 — 循环收口与 Cycle 2 派生（Closure And Next-Cycle Derivation）

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Work Item I6 + Loop Rule；I5 全量验证结果 `ai-dev/audits/nop-stream-invariants/cycle1-I6-input.md`（唯一落点）；I3 派生登记 `ai-dev/audits/nop-stream-invariants/adjudication-table.md` §4（PD-15）与 §1 裁决表；mission `nop-stream-invariant-loop.json`（Cross-Cutting 授权边界）
> Related: 前置 `2026-08-12-1217-6-nop-stream-invariants-cycle1-I5-full-verification.md`（I5，硬串行依赖，须已 `completed`）；后继 `2026-08-12-1217-8-nop-stream-invariants-cycle2-I1-output-contract-gates.md`（本 plan Phase 2 派生的 Cycle 2 / I1）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 1 / I6. 循环收口与下一轮触发判定

## Purpose

收口 Cycle 1 并触发下一轮：确认 I5 全量验证统计（门禁数 / red list / 新族数，唯一落点 `cycle1-I6-input.md`）、裁定 PD-15 输出契约族为正式新族、裁决跨 task side-output 缺口（生产可达的已确认契约缺口；双层处置 = interim fail-fast 预授权分派 Cycle 2 / I4 + 线协议支持 `HG-01` 人工确认门）并登记、依 Loop Rule 派生 Cycle 2（预授权自动追加，回写 roadmap）、登记复触发条件、roadmap Work Item I6 状态流转 `todo`→`planned`→`done`、独立 fresh session closure。本 plan 是纯文档 / 裁决计划：不写代码、不沉淀门禁、不修复缺陷。

## Current Baseline

- **I0–I5 全部 `completed`**（plans `2026-08-12-1217-1..6`）。I5 实测：nop-stream 模块组 2822 tests / 0 failures；门禁 9 类 / 92 tests；mjs pin 0；e2e 6/6 + 7/7；零新失败。
- **`cycle1-I6-input.md` 已落档（唯一落点）**：red list 7/7 修复确认 + RL-3 触发闭合；新族数 = 1（PD-15 在案）；门禁 9 类 / 92 tests；pin 0。
- **PD-15（输出契约族）在案**：不变式陈述「任何 `Output.collect(OutputTag, X)` 调用必须被转发到注册的 side-output 消费者，不得静默丢弃；无注册消费者 → fail-fast」。历史触发证据（I3 登记，pre-fix 行号）：`ChainingOutput.java:84-86`（原始丢弃点）/ `WindowOperator.java:1015-1017` / `StreamTaskInvokable.java:171/:209`。
- **live 复核（2026-08-12 实测）**：`ChainingOutput.collect(OutputTag)`（`nop-stream/nop-stream-core/.../operators/ChainingOutput.java:111`）已转发至注册消费者 + 无消费者 fail-fast（`ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）；`TimestampedCollector.java:97` 纯转发（透传至被包装 output）；`StreamTaskInvokable.registerSideOutputConsumer`（:310-313）→ 共享 consumer map → `ChainingOutput.sideOutputConsumers` 接线已连通。
- **side-output call-site 全量盘点（live 实测 6 个发射点，全部经由 `Output.collect(OutputTag, ...)`）**：
  - in-task 链式路径（发射点 = `Output.collect(OutputTag-typed, ...)` 调用行）：`ProcessOperator.java:111`（ProcessFunction `ctx.output` 发射）/ `:134`（OnTimer `ctx.output` 发射）；`WindowOperator.java:1030`（late-data `sideOutput` 发射）/ `:1860`（ProcessWindowFunction `ctx.output` 发射）；`CepOperator.java:483`（late-data 发射）/ `:777`（PatternProcessFunction `ctx.output` 发射）。声明点（`:110/:133/:1856/:770` 的 `void output(OutputTag, X)`）为间接层，不属发射点。
  - **跨 task 生产可达**：`GraphExecutionPlan.java:454-458` 以 `fanOutWriters` 构造 `StreamTaskInvokable`，`wireOperators` 将 task tail 算子 `setOutput(RecordWriterOutput)`（`StreamTaskInvokable.java:239/:245`，另 :352 `wireTailToRecordWriter`）。多 vertex 部署下，tail 算子（WindowOperator / CepOperator / ProcessOperator）发出的 side-output 经 `RecordWriterOutput.collect(OutputTag)`（:645 空体）/ `BroadcastingRecordWriterOutput.collect(OutputTag)`（:706 空体）**静默丢弃**——**生产可达的已确认契约缺口（silent data-loss，同 RL-7 族）**，非「无调用方」。
- **I6 剩余 gap**：Cycle 1 收口裁定（稳态判定）、PD-15 正式追加 Cycle 2（roadmap 表追加 6 行）、跨 task 缺口处置登记（人工确认执行门）、复触发条件登记、I6 行状态流转、独立 closure。

## Goals

- 确认 Cycle 1 收口统计与 `cycle1-I6-input.md` 一致，且 red list 零悬挂（7/7 + RL-3 闭合）。
- 裁定 PD-15 = 新族（输出契约族），依 Loop Rule「新族强制沉淀」派生 Cycle 2：roadmap Work Item 表追加 Cycle 2 / I1–I6 六行（I1 附触发证据 = 发现 `文件:行` + 不变式陈述）。
- 裁决跨 task side-output 缺口：已确认契约缺口（P1，静默数据丢失，生产可达）→ **必须修**，但修复 = RecordWriter 线协议结构性重构 → 按 mission Cross-Cutting「结构性重构执行前人工确认」，**执行门 = 人工确认**（登记「人工确认待办」，附证据 / 修复方向 / 触发条件 / 保护性覆盖；不静默降级、不假装已修）。
- 裁定 Cycle 2 / I1 门禁范围：不变式 #6 一等门禁 = 全 `Output` 实现类三态分类 + 类级枚举完备性 + call-site 注册表（6 个 call-site 全覆盖，新增 call-site 即红）；跨 task 实例以已知违约 pin 登记（关联人工确认待办，移除 = 人工确认修复落地后）。
- 登记复触发条件；roadmap Work Item I6 状态流转 `todo`→`planned`→`done`。
- 独立 fresh session closure audit，evidence 写入本 plan Closure 段。

## Non-Goals

- **不修复任何代码**：跨 task 线协议结构性变更 = 人工确认门，不在本 plan 亦不在自动修复范围（I4 已修的 in-task 实例不重复处理）。
- **不沉淀新门禁 / 新不变式**（属 Cycle 2 / I1，plan `2026-08-12-1217-8-...`）。
- **不执行 Cycle 2 / I2 审计**（下一 work item，依赖 I1 门禁落地）。
- **不弱化 / 删除 / 豁免既有门禁**（棘轮规则只增不减；本 plan 无门禁改动）。
- **不重跑 I5 全量验证**（I5 已实测，本 plan 仅核对记录一致性）。

## Scope

### In Scope

- 收口统计核对（`cycle1-I6-input.md` ↔ on-disk surefire 报告；red-list.md §5；mjs-pins.json）。
- 稳态判定（有新族 → 非稳态 → 派生 Cycle 2）+ PD-15 新族裁定。
- 跨 task side-output 缺口裁决与处置登记（人工确认待办：证据 = 6 call-site + 接线路径；修复方向；触发条件；保护性覆盖 = pin + call-site 注册表）。
- roadmap 回写：Cycle 2 / I1–I6 六行追加（附触发证据）+ Follow-up Backlog 条目追加。
- 复触发条件登记；roadmap I6 行状态流转；`ai-dev/logs/` 记录。
- 独立 fresh session closure audit + evidence。

### Out Of Scope

- 任何代码 / 门禁 / 不变式变更（属 Cycle 2 / I1）。
- Cycle 2 的执行（I1–I6 各 plan 另行拟制，本 plan 仅派生登记）。
- 对既有 completed 计划（I0–I5）的正文回写（Minimum Rules #20）。

## Execution Plan

### Phase 1 - 收口统计确认与稳态判定

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/cycle1-I6-input.md`、`red-list.md`、`mjs-pins.json`、nop-stream 各模块 surefire 报告（`**/target/surefire-reports/TEST-*.xml`）、`ai-dev/logs/2026/08-12.md`

- Item Types: `Proof`

- [x] 逐条核对 `cycle1-I6-input.md` 统计与 on-disk surefire 报告一致（I5 closure audit 同法：解析 nop-stream 模块组 TEST-*.xml 汇总 tests/failures/errors；门禁 9 类 / 92 tests 与 `-Dtest='Test*Invariant*'` 报告一致）；mjs pin 0（`mjs-pins.json` pinnedViolations 为空）。**出入处置**：以 surefire 实测为准，差异记录到 `ai-dev/logs/` + 本 plan 裁定记录与 closure evidence；**不回写 `cycle1-I6-input.md`**（I5 落点，Minimum Rules #20 尊重；差异不影响 Cycle 2 派生判定）
- [x] 确认 red list 零悬挂：red-list.md §5 7 条 RL 逐条修复证据在案（commit 号可溯源）+ RL-3 Follow-up Backlog 闭合 ✅；门禁复跑结果引用 I5 实测（本 plan 不重跑）
- [x] 确认新族数 = 1：PD-15 在案（不变式陈述 + 触发证据，历史 pre-fix 行号与 live 行号分列——live 复核以 Phase 1 实测为准：发射点 `ProcessOperator.java:111/:134` / `WindowOperator.java:1030/:1860` / `CepOperator.java:483/:777`；接线点 `ChainingOutput.java:111` / `StreamTaskInvokable.java:310-313`）；I5 零新失败 → 无新增族
- [x] **跨 task 缺口确认（本 phase 复核项）**：重跑 call-site 盘点（main 代码 grep `output\.collect\([a-zA-Z_$][\w$]*[Oo]utputTag` + 排除 `Output` 实现类内部转发调用（如 `TimestampedCollector.java:98`，属 V1/V3 管辖），确认 6 个发射点与接线路径（`GraphExecutionPlan.java:454-458` → `StreamTaskInvokable.java:239/:245/:352` → `RecordWriterOutput`）——作为 Phase 2 裁决的实证输入
- [x] **稳态判定**：有新族（PD-15）+ 跨 task 缺口在案 → 非稳态 → 依 Loop Rule 派生 Cycle 2 / I1；「无新族且 red list 零 → 稳态暂停」分支不适用，复触发条件仍按 §Loop Rule 登记（见 Phase 3）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 核对记录在案：`cycle1-I6-input.md` 全部统计项与 on-disk surefire 一致（无出入或出入已修正记录）
- [x] red list 零悬挂确认记录（7/7 + RL-3 闭合）
- [x] 6 个 call-site 盘点 + 跨 task 接线路径复核记录在案（Phase 2 裁决的实证输入）
- [x] 稳态判定结论记录在案（非稳态 → Cycle 2 派生）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 本 Phase 为纯核对/文档工作，无代码变更 → `No owner-doc update required`（`docs-for-ai/` 不涉及；`ai-dev/` 记录归本 plan 各 Phase）

### Phase 2 - PD-15 裁决与 Cycle 2 派生

Status: completed
Targets: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`（Work Item 表 + Follow-up Backlog）、`ai-dev/audits/nop-stream-invariants/adjudication-table.md`（I6 裁定记录）

- Item Types: `Decision`

- [x] **PD-15 新族裁定**：输出契约族 = 正式新族（I2/I3 登记 + I4 修复基线 + I5 零新增，触发证据在案）→ Loop Rule「新族强制沉淀」成立，派生 Cycle N+1 / I1
- [x] **跨 task side-output 缺口裁决（双层）**：`RecordWriterOutput` / `BroadcastingRecordWriterOutput` 的 `collect(OutputTag)` 空体 = 同族**已确认契约缺口**（生产可达静默数据丢失，影响类同 RL-7 → P1）：
  - **层次 1（interim fail-fast，自动信封内）**：空体 → fail-fast（抛异常，`ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 风格）——类内部行为修复（private 嵌套类，`Output` 接口零变更），同 RL-7 修复先例（I4 自动执行）；Rule #24 明确「功能未实现时快速失败而非静默忽略」→ 按 P1 预授权自动修复。裁定处置 = **预裁决分派 Cycle 2**（经 I3 确认 → I4 执行；I1 门禁以过渡 pin 全绿落地，I4 修复后移除 pin）
  - **层次 2（线协议支持，人工确认门）**：跨 task side-output 的真正支持 = RecordWriter 线协议结构性重构（公共内部机制变更）→ 执行门 = 人工确认（mission Cross-Cutting：结构性重构执行前人工确认，不在自动修复信封内）→ 登记**人工确认待办 `HG-01`**（证据 = 6 发射点 + 接线路径；修复方向；触发条件；Successor = 人工确认后另立 plan）
  - 保护性覆盖：in-task 已 fail-fast（I4 `b20fcd0e1`）+ Cycle 2 / I1 门禁（类级枚举 + call-site 注册表 + 过渡 pin）+ `HG-01` 待办条目——已确认缺口按 Rule #15 归 `Fix` 类，处置 = 分派 + 留痕，非延期、非降级
- [x] **Cycle 2 / I1 门禁范围裁定**：不变式 #6（输出契约族）门禁 = 全 `Output` 实现类 `collect(OutputTag)` 行为三态（转发 / fail-fast / pin）+ 类级枚举完备性（新增 main `Output` 实现类不入表即红）+ call-site 注册表（6 个发射点全覆盖，新增发射点即红，跨 task 可达点标注 `HG-01` 关联）；跨 task 实例以**过渡 pin** 登记（`mjs-pins.json`，pin 条目含 `key` + 关联 `HG-01`；移除 = Cycle 2 / I4 interim fail-fast 修复落地后，禁静默移除——`HG-01` 线协议支持落地属增强，不阻塞 pin 移除）
- [x] **roadmap 回写**：Work Item 表追加 Cycle 2 / I1–I6 六行（状态 `todo`，依赖列 = 裸标签（`I6` / `I1` / `I2` / `I3` / `I4` / `I5`，与既有表格式一致）；I1 行附触发证据 = PD-15 不变式陈述 + 发现 `文件:行`（live 发射点行号）+ 派生说明「PD-n 先例链」）；Follow-up Backlog 追加「跨 task side-output 线协议结构性变更（人工确认待办 `HG-01`）」条目（含描述、6 发射点证据、修复方向、状态 `pending human confirmation`、Successor = 人工确认后另立 plan）；interim fail-fast 属自动信封，不入 backlog——记入 Cycle 2 / I3 预裁决输入（§I6 裁定节）
- [x] **I6 裁定记录落档**：adjudication-table.md 追加 §I6 裁定节（新族确认、跨 task 缺口双层裁决（interim fail-fast 预授权分派 Cycle 2 / I4 + `HG-01` 人工确认门）、门禁范围、复触发登记摘要）——供 Cycle 2 / I1 与 Cycle 2 / I3 引用

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] PD-15 新族裁定 + 跨 task 缺口双层裁决（interim fail-fast 预授权分派 + `HG-01` 人工确认门）记录在案（adjudication-table.md §I6）
- [x] roadmap 已追加 Cycle 2 / I1–I6 六行，I1 行含触发证据；Follow-up Backlog 已含 `HG-01` 待办条目
- [x] Cycle 2 / I1 门禁范围裁定记录在案（后继 plan `2026-08-12-1217-8-...` 引用）
- [x] 无静默跳过：跨 task 缺口未以「后续再说」/「watch-only」形式降级——已确认契约缺口按 Rule #15 归 `Fix` 类，处置 = interim fail-fast 预授权分派（Cycle 2 / I4）+ `HG-01` 人工确认门 + 过渡 pin + call-site 注册表四重留痕
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 复触发登记与 roadmap 状态流转

Status: completed
Targets: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`、`ai-dev/logs/2026/08-12.md`

- Item Types: `Decision | Follow-up`

- [x] **复触发条件登记**（roadmap §Loop Rule / §I6 Phase Details）：Cycle 2 触发原因 = 新族沉淀（PD-15）+ 跨 task 缺口人工确认门；继续登记三选一复触发（① CI 任一不变式门禁变红；② nop-stream 核心类结构变更（新增/重命名 Operator/SinkFunction/Checkpoint 机制/Output 实现类）；③ 周期复探）；人工确认待办的触发 = 人工批准跨 task 线协议变更
- [x] roadmap Work Item I6 行状态流转 `todo`→`planned`（本 plan 激活时）→`done`（closure audit 通过后，不得提前）
- [x] `ai-dev/logs/2026/08-12.md` 顶部追加 I6 条目（统计确认 + 裁定摘要 + Cycle 2 派生 + 复触发登记）
- [x] 文本一致性核对：Plan Status、Phase Status、Exit Criteria、Closure Gates、roadmap 行、daily log 六处一致后方可进入 closure

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 复触发条件登记在案（含 Cycle 2 触发原因 + 人工确认待办触发）
- [x] roadmap I6 行已流转（`planned`；`done` 在 closure 后由独立审计确认）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 文本一致性核对完成（无「顶部已 completed、内部未勾选」矛盾态）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。
>
> **纯文档计划**：本 plan 不涉及任何代码变更（仅修改 `ai-dev/` 下文档），`./mvnw test`、`./mvnw lint` 等构建验证条目已按 guide 删除；保留文档与 checklist 门禁。

- [x] 收口统计确认（与 `cycle1-I6-input.md` 一致）且 red list 零悬挂（7/7 + RL-3 闭合）
- [x] PD-15 新族裁定完成 → Cycle 2 派生在案（roadmap 六行 + 触发证据）
- [x] 跨 task 缺口处置登记在案（= 已确认契约缺口，归 Fix 类；interim fail-fast 预授权分派 Cycle 2 / I4；线协议支持 = `HG-01` 人工确认门；四重留痕，未降级）
- [x] 复触发条件登记在案
- [x] 无 in-scope live defect / contract drift 被静默降级到 deferred / follow-up（跨 task 缺口 = 已确认 + 必须修；interim fail-fast 预授权分派 = 显式 successor ownership，`HG-01` 线协议 = 人工确认执行门登记；均非延期，保护性覆盖在案）
- [x] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [x] **Anti-Hollow Check**：本 plan 纯文档，无代码接线面；closure audit 验证（a）roadmap 追加行与 I5 实测记录一致，（b）跨 task 缺口裁决与 live 代码（6 发射点 + 接线路径）一致，（c）无「记录在案但实际未发生」的空壳断言
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### 层次 1 — 跨 task interim fail-fast 修复（`RecordWriterOutput` / `BroadcastingRecordWriterOutput` 空体 → fail-fast）

- Classification: `Fix`（已确认契约缺口，P1，静默数据丢失，生产可达）——已确认 live defect，**必须修**，不属 deferral
- Why Not Blocking Closure: 本项**不延期**——I6 预裁决其处于 P1 自动修复预授权信封内（类内部行为修复：private 嵌套类、`Output` 接口零变更，同 RL-7 先例；Rule #24 强制「未实现时快速失败而非静默忽略」），分派 Cycle 2 / I3 确认 → I4 执行（显式 successor ownership）。Cycle 1 关闭前无需落地：修复需在 I1 门禁落地后执行（门禁以过渡 pin 全绿，I4 修复后移除 pin），顺序由 Cycle 2 依赖链保证。
- Successor Required: `yes`
- Successor Path: Cycle 2 / I3（裁决确认）→ Cycle 2 / I4（修复 + 注册表分类更新 + pin 移除）

### 层次 2 — 跨 task side-output 线协议结构性变更（人工确认待办 `HG-01`）

- Classification: `Fix`（已确认契约缺口，执行门 = 人工确认）——**非** watch-only residual；结构性重构，mission 授权边界
- Why Not Blocking Closure: ① 修复 = RecordWriter 线协议结构性重构（公共内部机制变更），mission Cross-Cutting 明确「结构性重构执行前人工确认」→ 执行门未过，AI 自动执行信封不含此项；② 裁决仍是「必须修」——处置 = 登记 `HG-01` 人工确认待办（证据 + 修复方向 + 触发条件 + Successor），非关闭、非延期；③ 保护性覆盖四重：in-task 路径已 fail-fast（I4 `b20fcd0e1`）+ Cycle 2 / I1 门禁（类级枚举 + call-site 注册表，新增实现类 / 新增发射点 / 行为漂移即红）+ 过渡 pin（`mjs-pins.json`，移除 = interim fail-fast 落地，与 `HG-01` 解耦）+ Follow-up Backlog 待办条目；④ 本 loop 的闭环不因人工确认门悬挂——人工批准即触发后继 plan。
- Successor Required: `yes`
- Successor Path: 人工确认后另立 plan（跨 task 侧输出线协议设计 + 实现 + E2E）；触发条件 = 人工批准 + 跨 task side-output 需求出现（或 CI 门禁红暴露新实例）

## Non-Blocking Follow-ups

- Cycle 2 / I1–I6 各 phase 的执行 plan 另行拟制（本 plan 只派生登记，不代执行）。
- ArchUnit 架构约束门禁（I1 deferred 裁定延续：`optimization candidate`，引入涉及依赖变更，触发时另立 Decision）。

## Closure

Status Note: Cycle 1 收口统计确认（与 `cycle1-I6-input.md` 一致：2822 tests / 0 failures；门禁 9 类 / 92 tests；pin 0；red list 零悬挂 7/7 + RL-3 闭合）、PD-15 新族裁定（输出契约族 = 正式新族）、跨 task 缺口双层裁决（interim fail-fast 预授权分派 Cycle 2 / I4 + `HG-01` 人工确认门）、Cycle 2 派生（roadmap 六行 + 触发证据）、复触发登记、I6 行流转（`done` 由独立 closure audit 确认后置位）、独立 closure audit 完成
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子 agent（general）
- Audit Session: `ses_00a2716bcffe7BRHlw5B5m0lkY`
- Evidence:
  - Phase 1 Exit Criteria：全 PASS——(1) 3 个 Phase checklist + Exit Criteria 全部 `[x]`、Phase Status 全部 `completed`；(2) 统计与 `cycle1-I6-input.md` 一致（on-disk surefire 实测 2822/0/0/10，逐模块吻合 core 1418 / runtime 804 / cep 320 / rocksdb 83 / connector 35 / connector-jdbc 32 / connector-batch 35 / connector-debezium 19 / flow 51 / fraud-example 25；9 门禁类 = 8+12+10+9+10+4+11+21+7 = 92 tests；`mjs-pins.json` pinnedViolations 空）；(3) red list 零悬挂（commit `fcc71fc05`/`58255014b`/`b20fcd0e1` git log 可溯源；RL-3 backlog ✅）；(4) 6 发射点 live 行号全部命中（ProcessOperator:111/:134、WindowOperator:1030/:1860、CepOperator:483/:777）；(5) 跨 task 接线路径 live 全部命中（ChainingOutput.java:111 转发+fail-fast、StreamTaskInvokable.java:310-312 registerSideOutputConsumer、:239/:245 setOutput(RecordWriterOutput/BroadcastingRecordWriterOutput)、:348-352 wireTailToRecordWriter、:645/:706 空体、GraphExecutionPlan.java:454-458）。
  - Phase 2 Exit Criteria：全 PASS——roadmap Work Item 表 Cycle 2 / I1–I6 六行（I1 附 PD-15 触发证据 + 发现 `文件:行` + PD-n 先例链；依赖裸标签；全部 `todo`）；Follow-up Backlog `HG-01` 条目（`pending human confirmation` + 6 发射点证据 + 修复方向 + Successor）；adjudication-table.md §6 裁定节（6.1 新族 / 6.2 双层裁决 / 6.3 门禁范围 / 6.4 复触发）。
  - Phase 3 Exit Criteria：全 PASS——§Loop Rule + §I6 Phase Details 复触发登记在案（Cycle 2 触发原因 = 新族沉淀 PD-15 + `HG-01` 人工确认门；三选一复触发；人工确认待办触发 = 人工批准线协议变更）；`ai-dev/logs/2026/08-12.md` 顶部 1217-7 条目在案；roadmap I6 行 `planned`（`done` 本 closure 确认后置位）；无「顶部 completed、内部未勾选」矛盾态。
  - Closure Gates：9/9 PASS——收口统计 + 零悬挂；PD-15 裁定 + Cycle 2 派生；跨 task 缺口 Fix 类处置 + 四重留痕未降级；复触发登记；无静默降级；独立 audit 完成（本段）；Anti-Hollow 通过；`check-plan-checklist.mjs --strict` 退出码 0；`check-doc-links.mjs --strict` 退出码 0。
  - Anti-Hollow 检查结果：纯文档计划无代码接线面；(a) roadmap 追加行（Cycle 2 六行 + I1 触发证据行号）与 live 代码行号一致（auditor 实测）；(b) 跨 task 缺口裁决与 live 代码（6 发射点 + GraphExecutionPlan/StreamTaskInvokable 接线）一致；(c) 无空壳断言——Cycle 2 行均为 `todo`（未谎报已做）、HG-01 为 `pending human confirmation`（未谎报已修）、过渡 pin 描述为 Cycle 2 / I1 设计项（`mjs-pins.json` 当前空，一致）。
  - Deferred 项分类检查：跨 task 缺口 = 已确认契约缺口（Fix 类，Rule #15），interim fail-fast 预授权分派 Cycle 2 / I4（显式 successor ownership）、`HG-01` 线协议 = 人工确认执行门登记；均未降级为普通 follow-up / watch-only。

Follow-up:

- Cycle 2 / I1（plan `2026-08-12-1217-8-...`）为直接 successor：沉淀 PD-15 输出契约族门禁（三态分类 + 类级枚举完备性 + call-site 注册表 + 过渡 pin 2 条登记）
- no other remaining plan-owned work
