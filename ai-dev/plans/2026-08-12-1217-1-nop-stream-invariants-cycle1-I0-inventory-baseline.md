# Cycle 1 / I0 — 不变式盘点与基线（Invariant Inventory And Baseline）

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1 发现 1 Blocker（AR-9 误分类为已修复）+ 2 Major + 9 Minor，全部修复；round 2 确认 7 项修复到位、无 Blocker、verdict: approved，仅 1 个 TaskManager 行号 Minor 已顺手修正）
> Source: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Work Item I0；`ai-dev/skills/invariant-loop-audit-prompt.md`；21 轮审计证据（`ai-dev/audits/2026-05-31-adversarial-review-nop-stream-r8` ~ `r16` 等）
> Related: 后续 `2026-08-12-1217-2-nop-stream-invariants-cycle1-I1-first-gates.md`（I1 首批门禁，依赖本 plan 的 catalog 与首批不变式定稿）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 1 / I0. 不变式盘点与基线

## Purpose

把 nop-stream 21 轮审计（5 deep + 16 adversarial r1–r16）+ 23 阶段独立审计中反复复发的已知失败模式族，盘点为**可执行不变式目录**（`ai-dev/audits/nop-stream-invariants/invariant-catalog.md`），并从 live 代码枚举**审计目标集**（Operator 族 / SinkFunction 族 / Checkpoint 机制 / CEP NFA / ClusterRegistry 的全部变更型类/方法）。同时**实测确认基线 = 当前零代码不变式门禁**，并定稿首批 5 条不变式候选族作为 I1 的输入。本 plan 只盘点与定稿，不写门禁、不修缺陷。

## Current Baseline

> 已核对 live repo（2026-08-12）：`ai-dev/tools/`、`ai-dev/audits/`、nop-stream 五个族的代表类源码与审计证据文件。

- **审计证据在册（live 文件）**：`ai-dev/audits/2026-05-31-adversarial-review-nop-stream-r8/{summary,01-open-findings}.md`（含 AR-58 resolveKey 方向反转、AR-66 CepOperator watermark 语义）、`r9`~`r13`、`2026-05-31-adversarial-review-nop-stream-r16/{summary,01-open-findings}.md`（AR-1..AR-20+，含 R14-AR-1 `:` 分隔符、R15-AR-1..5 遗留确认表、AR-18 ClusterRegistry 不一致）、`2026-05-31-adversarial-review-nop-stream/{01-open-findings,summary}.md`。R16 summary 自列级联（AR-5+AR-15、AR-1+AR-11、AR-6+AR-7+AR-8）与 P0/P1/P2/P3 分级。
- **R14/R15 证据位置说明（live 实测）**：`ai-dev/audits/` **不存在独立的 r14 / r15 目录**；R14-AR-1 与 R15-AR-1..5 的证据仅存在于 `ai-dev/audits/2026-05-31-adversarial-review-nop-stream-r16/01-open-findings.md` 头部的「前轮已知未修复问题」确认表（该表不是 R15 完整发现集）。Phase 1 盘点时按此位置取数，映射表须注明「R14/R15 全集不可得，以 R16 确认表为证据上限」的覆盖边界。
- **基线 = 零代码不变式门禁（已实测）**：`ai-dev/tools/check-nop-stream-invariants.mjs` 不存在；`ai-dev/audits/nop-stream-invariants/` 目录不存在；唯一相关工具 `ai-dev/tools/check-nop-stream-audit-manifest.mjs`（1304 行）只校验审计证据 schema，不校验代码不变式。nop-stream 各模块无 `@ParameterizedTest` 用法（grep 无结果）。
- **live 代码已含大部分修复（抽查确认，I0 需逐族核实定稿）**：
  - Window 粘合层：`WindowedStreamImpl.java` 4 个 call-site（apply/aggregate/reduce/process，`:186/:201/:216/:231`）均传 `assigner, trigger, evictor, allowedLateness, function, elementType, keySelector, keyClass`；`WindowOperatorFactoryImpl.java` 4 个 `create*Operator` 全参数转发 `WindowOperatorBuilder`；`WindowOperatorBuilder.buildWindowOperator`（`:183-203`）将 windowAssigner/windowSerializer/keySelector/keySerializer/keyClass/windowFn/trigger/allowedLateness/lateDataOutputTag/accClass/stateDesc/mergeFn/evictor/accumulationMode 传入 `WindowOperator` 构造器（R16 AR-2 allowedLateness 死 API 已修）。
  - `WindowOperator.java:644` 已调用 `triggerContext.onMerge(mergedWindows)`（R15-AR-3 修复）；`:652` 调 `mergeWindowContents`。
  - `Lockable.java` release/releaseOrDetach 已 CAS + 负数抛 `StreamRuntimeException`（R16 AR-8 修复）。
  - `CheckpointCoordinator.java` `checkpointSuccessMap` 为 ConcurrentHashMap 且完成时 remove（`:805`），触发 `clear`（`:1179`）（R16 AR-10 修复）；恢复路径 `restoreFromCheckpoint()` `:896-900` 已做单调 ID 推进（`restoredId >= currentCounter` 才 `set(restoredId + 1)`）——**这才是 R16 AR-5（恢复后 ID 倒退）的真实修复点**（`CheckpointIDCounter` 的 AtomicLong 仅是并发机制，I0 Phase 1 按此归因核实）。
  - `JdbcClusterRegistry.java:132-148` renewLease 已使用 per-renewal `leaseTimeoutMs` 计算 `lease_expire_at`；**但 R16 AR-9 的缺陷点（`registerNode()` 写 `lease_expire_at=0L`、`getActiveNodes()` 按 `lease_expire_at > now` 过滤）在 live 代码中仍存在**：`:112-115` INSERT 仍写 `0L`、`:172-174` 仍按 `lease_expire_at > now` 过滤——**live residual 候选（AR-9 未修复），I0 Phase 1 必须实测定稿**（调用方 `TaskManager.java:154/:215` 注册后依赖心跳循环 `renewLease` 才可见，`TaskManager.java:212`）。
  - `InMemoryClusterRegistry.java:68-81` renewLease **仍忽略 `leaseTimeoutMs` 参数**（固定 `leaseTtlMs`，`:90/:98/:114`）——**live residual 候选（R16 AR-18 未确认修复），I0 必须核实并定稿**。
  - `TwoPhaseCommitSinkFunction.java:83` `saveState()` 的 `new TreeMap<>(pendingCommits)` **无 synchronized 块**；`:76-78` `setPendingCommits()` 仍接受任意 Map——**live residual 候选（R16 AR-1/AR-11 修复未完全确认），I0 必须核实**（注：`finishCommit`/`restoreFromEpoch` 内部迭代已 synchronized，`:101/:158`）。
  - **历史 finding 引用的旧类名已迁移（live 实测）**：R8/R13/R14/R15/R16 的 Window 族 finding（如 R15-AR-3、R14-AR-1、R13-AR-5/6、R8-AR-58）引用的 `WindowAggregationOperator` **已不存在**（git `905d6411a` 删除），live 对应类为 `runtime/operators/windowing/WindowOperator` 及其粘合链（`WindowedStreamImpl` → `IWindowOperatorFactory` → `WindowOperatorFactoryImpl` → `WindowOperatorBuilder` → `WindowOperator`）。catalog 引用历史 finding 时须带「旧类名 → live 类名」映射注记，行号按审计快照标注（详见 Phase 3）。
- **审计目标集候选类（live 路径，I0 Phase 2 定稿完整清单）**：
  - Operator 族：`nop-stream-core/.../datastream/WindowedStreamImpl.java`、`core/operators/IWindowOperatorFactory.java`、`runtime/operators/windowing/{WindowOperatorFactoryImpl,WindowOperatorBuilder,WindowOperator}.java`（注：`StreamReduceOperator` 在 `nop-stream-core/operators`，不在 runtime）
  - SinkFunction 族：`core/common/functions/sink/TwoPhaseCommitSinkFunction.java`、`nop-stream-connector-batch/.../BatchConsumerSinkFunction.java`（实测位于 connector-batch 模块，非 connector）等
  - Checkpoint 机制：`core/checkpoint/CheckpointIDCounter.java`、`runtime/checkpoint/{CheckpointCoordinator,PendingCheckpoint,CheckpointPlanBuilder}.java`、`runtime/checkpoint/storage/LocalFileCheckpointStorage.java`
  - CEP NFA：`cep/nfa/{NFA,DeweyNumber}.java`、`cep/nfa/sharedbuffer/{SharedBuffer,Lockable}.java`、`cep/operator/CepOperator.java`
  - ClusterRegistry：`runtime/cluster/{ClusterRegistry,JdbcClusterRegistry,InMemoryClusterRegistry,NodeDiscoveryConsistencyChecker}.java`

## Goals

- 产出 `ai-dev/audits/nop-stream-invariants/invariant-catalog.md`，每条不变式含四要素：**不变式陈述 / 覆盖失败族 / 历史审计证据（finding-ID + 文件:行）/ 检测方法（JUnit / 静态扫描 / ArchUnit）**。
- 从 live 代码枚举五族全部变更型类/方法，产出**审计目标集清单**（作为 I1 表完备性门禁的"表"来源）。
- 实测确认并**记录基线 = 零代码不变式门禁**。
- 定稿**首批 5 条不变式**（覆盖 roadmap 列出的五族候选：① Window 构造参数完备性 ② synchronized 集合迭代点 ③ Checkpoint idCounter 原子性 ④ CEP Lockable/SharedBuffer 释放对称性 ⑤ ClusterRegistry 多实现语义一致性）。
- 对每条不变式覆盖的历史 finding，**核实 live 修复状态**（fixed / residual / 待裁决），residual 必须显式记录为 I2 red list 候选输入。

## Non-Goals

- **不写任何门禁代码/测试/扫描器**（属 I1，`2026-08-12-1217-2-...`）。
- **不修复任何 defect**（属 I4，经 I2 跑门禁 → I3 裁决）。
- **不做对抗探查**（属 I2）。
- **不做 P0/P1/P2 裁决**（属 I3）。
- 不涉及与 `nop-stream-production` / `nop-stream-independent-audit` / `nop-stream-flink-comparison` roadmap 功能建设范围重叠的盘点（本 plan 只盘点不变式类别，不盘点功能缺口）。

## Scope

### In Scope

- 审计证据盘点（R8–R16 及独立审计证据中与五族相关 finding 的索引）。
- 五族审计目标集枚举（live 类/方法清单）。
- 零门禁基线确认与记录。
- 首批 5 条不变式的定稿（含 live 修复状态核实表）。
- 不变式目录文档撰写 + 独立子 agent 共识审查。

### Out Of Scope

- 门禁实现（I1）、red list 生成与对抗探查（I2）、裁决（I3）、修复（I4）、全量验证（I5）、收口（I6）。
- ArchUnit 依赖引入（I1 的 Decision，本 plan 只写检测方法建议）。

## Execution Plan

### Phase 1 - 审计证据盘点与基线核实

Status: completed
Targets: `ai-dev/audits/`（R8–R16 各轮 + 独立审计证据）；`ai-dev/tools/`；nop-stream 五族代表类

- Item Types: `Proof`
- [x] 盘点 R8–R16 各轮 `01-open-findings.md` / `summary.md` 中与五族相关的全部 finding（含 R8 AR-58/AR-66、R13 AR-5/AR-6/AR-7/AR-8、R14-AR-1、R15-AR-1..5、R16 AR-1..AR-20+），建立 finding-ID → 失败族 映射表。**注意：独立 r14/r15 目录不存在，R14-AR-1 / R15-AR-1..5 从 `ai-dev/audits/2026-05-31-adversarial-review-nop-stream-r16/01-open-findings.md` 头部确认表取数，映射表注明覆盖边界**
- [x] 实测确认零代码不变式门禁（`check-nop-stream-invariants.mjs` 不存在、`nop-stream-invariants/` 不存在、无 `@ParameterizedTest` 用法），记录到 catalog 基线段
- [x] 逐一核实五族代表类的 live 修复状态，fixed / residual 分类，residual 附 `文件:行` 证据。**必核清单（含已知疑似 residual 与疑似修复）**：`JdbcClusterRegistry.registerNode()`（:112-115 写 `lease_expire_at=0L`）与 `getActiveNodes()`（:172-174 按 `lease_expire_at > now` 过滤）——AR-9 缺陷点；`InMemoryClusterRegistry.renewLease`（:68-81 忽略 `leaseTimeoutMs`）——AR-18；`TwoPhaseCommitSinkFunction.saveState`（:83 无锁 copy）与 `setPendingCommits`（:76-78 接受任意 Map）——AR-1/AR-11；`CheckpointCoordinator.restoreFromCheckpoint`（:896-900 单调 ID 推进）——AR-5 真实修复点

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] finding-ID → 失败族映射表存在（repo-observable：catalog 中可读）
- [x] 零门禁基线确认记录存在（`check-nop-stream-invariants.mjs` 不存在已实测）
- [x] 五族 live 修复状态表存在，residual 每条含 `文件:行` 证据
- [x] No owner-doc update required（本 phase 只盘点，不改变行为契约；`ai-dev/logs/` 除外）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 审计目标集枚举（live 代码）

Status: completed
Targets: nop-stream-core / nop-stream-runtime / nop-stream-cep / nop-stream-connector 的变更型类与方法

- Item Types: `Proof`
- [x] 枚举 Operator 族变更型类/方法（含 Window 粘合层 4 call-site、`IWindowOperatorFactory` 接口 4 方法、`WindowOperatorBuilder` 公共方法、`WindowOperator` 变更方法）
- [x] 枚举 SinkFunction 族（TwoPhaseCommitSinkFunction 全部 public mutating 方法、BatchConsumerSinkFunction 等兄弟）
- [x] 枚举 Checkpoint 机制（CheckpointCoordinator 全部变更方法、CheckpointIDCounter、PendingCheckpoint、LocalFileCheckpointStorage、CheckpointPlanBuilder）
- [x] 枚举 CEP NFA 族（NFA、CepOperator、DeweyNumber、Lockable、SharedBuffer 的变更型方法）
- [x] 枚举 ClusterRegistry 族（ClusterRegistry 接口全部方法 + Jdbc/InMemory 两实现 + NodeDiscoveryConsistencyChecker）
- [x] 目标集清单写入 catalog（供 I1 表完备性门禁作"表"来源）
- [x] **「变更型方法」判定标准定稿**：public/protected 且会改变对象内部状态的方法（含显式状态更新、集合/Map 写入、计数器递增、状态注册/清除）；getter/只读查询方法、toString/equals/hashCode 不入表（作为 I1 表完备性门禁的分类器语义）

Exit Criteria:

- [x] 五族目标集清单完整写入 catalog，每条含 live `类路径:方法` 引用
- [x] 目标集清单可通过 `grep` 在仓库中逐条复核（类/方法名与 live 代码一致）
- [x] No owner-doc update required（只盘点）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 首批不变式定稿与目录撰写

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/invariant-catalog.md`

- Item Types: `Proof | Decision`
- [x] 按模板撰写 `invariant-catalog.md`：Header（基线声明 + 目标集引用）→ 每条不变式含「陈述 / 覆盖失败族 / 历史审计证据 finding-ID + 文件:行 / 检测方法（JUnit 参数化 / 静态扫描 / ArchUnit）」
- [x] **历史证据引用策略**：finding 引用的旧类名（如已删除的 `WindowAggregationOperator`）须带「旧类名 → live 类名」（`WindowOperator`）映射注记；历史行号按审计快照标注（`audit snapshot`），不得伪装成 live 行号；catalog 同时登记每条 finding 对应的 live 修复状态
- [x] 首批 5 条不变式定稿：① Window 粘合层构造参数完备性（round-trip 全部 WindowedStreamImpl 字段）② `Collections.synchronizedMap/List` 字段迭代点必须在 synchronized 块内 ③ CheckpointIDCounter 更新原子性 ④ CEP SharedBuffer/Lockable 释放对称性（over-release 必须 fail-fast）⑤ ClusterRegistry 多实现语义一致性（registerNode 可见性 + renewLease per-renewal timeout）
- [x] 每条不变式登记其 live 修复状态（fixed / residual）与 I2 关注点
- [x] 检测方法一栏为每条选定落地形式（本轮默认 JUnit `@ParameterizedTest` 或 mjs 静态扫描；ArchUnit 标注为可选待后续 Decision）

Exit Criteria:

- [x] `invariant-catalog.md` 存在，首批 5 条不变式齐全且四要素完整（repo-observable）
- [x] 每条不变式覆盖 ≥1 条历史 finding（finding-ID 在审计证据文件中可查）
- [x] 每条不变式登记 live 修复状态；residual 项显式标记为 I2 red list 候选
- [x] 覆盖 `docs-for-ai/` 或 `ai-dev/` 文档变更 → `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 独立共识审查与定稿

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/invariant-catalog.md`；`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`

- Item Types: `Decision | Follow-up`
- [x] 由独立子 agent（fresh session，禁改文件）按 invariant-loop 审查维度（① 合规 ② 内部一致性 ③ 事实基础——live 实测 ④ 门禁/Loop 纪律）审查 catalog 与目标集清单
- [x] 修复审查发现的 Blocker/Major 问题，必要时复审（review-only，每轮 fresh session）
- [x] **roadmap 状态流转时机（按 invariant-loop skill 步骤 5）**：本 plan 通过独立草案审查（本 plan 转 `active`）时，roadmap Work Item I0 `todo` → `planned`；closure audit 通过时 `planned` → `done`（由本 plan 的 Closure 流程确认并记录）

Exit Criteria:

- [x] 独立子 agent 审查记录存在（含审查结论与问题清单）
- [x] 全部 Blocker 已解决；审查结论为可执行
- [x] roadmap I0 状态流转记录存在（`todo` → `planned`）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `invariant-catalog.md` 存在且首批 5 条不变式四要素完整
- [x] 五族审计目标集清单完整且与 live 代码逐条可复核
- [x] 零门禁基线已实测确认并记录
- [x] 每条不变式的 live 修复状态已核实（含 AR-9 registerNode/getActiveNodes 缺陷点、AR-5 真实修复点 `CheckpointCoordinator:896-900` 归因）；residual 已显式标记为 I2 输入（未被静默降级）
- [x] 独立子 agent 共识审查通过且记录在案
- [x] roadmap Work Item I0 已按状态机流转（本 plan 转 active 时 `planned`；closure audit 通过后 `done`）
- [x] **Anti-Hollow Check**：catalog 的每条不变式均引用真实存在的 finding-ID；历史证据带旧类名→live 类名映射注记（无指向已删除类的裸引用）；无空壳条目
- [x] 受影响 owner docs 已同步（本 plan 为纯文档产出，无代码变更；涉及 `docs-for-ai/` 或 `ai-dev/` 文件已过 link check）
- [x] 独立子 agent closure-audit 已完成并记录证据（`ai-dev/logs/`）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [x] 文档 link check：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 本 plan 为纯文档计划（无代码变更）：`./mvnw compile/test` 构建门禁不适用，已按 guide「纯文档计划」删除；`scan-hollow-implementations.mjs` 亦不适用

## Deferred But Adjudicated

### 首次引入 ArchUnit 检测方法

- Classification: `optimization candidate`
- Why Not Blocking Closure: I0 只定稿不变式陈述与检测方法建议；ArchUnit 依赖引入是后续 Decision（当前 nop-stream pom 未配置 ArchUnit），不影响 catalog 的盘点价值。
- Successor Required: `no`（触发时另立 Decision）

## Non-Blocking Follow-ups

- 历史 finding 中非五族、但同属"同族复发"模式的类别（如 R13 AR-9 死锁类、R16 AR-19/AR-20 资源管理类）在 I2 对抗探查中评估是否升格为新不变式族。
- 各轮的「修复确认表」（如 R16 头部 R15-AR-1..5 遗留表）作为 I2 门禁命中的对照来源。

## Closure

Status Note: 纯文档盘点计划。五族 finding 映射表、零门禁基线实测、live 修复状态核实（4 residual 显式入 I2 red list）、五族目标集清单、「变更型方法」判定标准、首批 5 条不变式四要素全部落地于 `ai-dev/audits/nop-stream-invariants/invariant-catalog.md`；2 轮独立共识审查（revised → approved）+ 1 轮独立 closure audit（CLOSED）通过。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立 subagent（fresh session）
- Audit Session: consensus review round 1 `ses_00ba633a9ffevLkcrOgYlpkxFU`（verdict: revised，1 Major + 4 Minor）；round 2 `ses_00b9f1b97ffeBwBx6sEc2OMT5g`（verdict: approved，零 Blocker）；closure audit `ses_00b9dc485ffe3QTxOeZLRKOaDB`（verdict: CLOSED，零 FAIL）
- Evidence:
  - Phase 1-4 Exit Criteria 逐条 PASS（closure audit 复核：映射表 69 条 finding 全可查；零门禁基线 `ls`/`rg` 实测；目标集抽查 5+ 类方法行号逐一命中；5 条不变式四要素齐全；residual 5 条含 文件:行 显式标记 I2 red list；catalog 零 link issue）
  - Closure Gates 逐条 PASS（closure audit）：catalog 存在且四要素完整；目标集 live 可复核；零门禁基线实测；AR-9（`JdbcClusterRegistry.java:115` 写 0L / `:174` 过滤 `>now`）、AR-5（`CheckpointCoordinator.java:896-900` 单调推进）live 核实；Anti-Hollow 20+ finding-ID 真实存在 + 旧类名→live 类名映射注记存在；roadmap I0 `todo`→`planned`→`done` 流转完成
  - `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0（closure 完成后实测）
  - `node ai-dev/tools/check-doc-links.mjs --strict`：全仓 20 个 **pre-existing** 错误全部位于非本 plan 文件（其他 mission roadmap 前向引用、跨仓 nop-chaos-flux 先例引用、`docs-for-ai/INDEX.md:217` 等）；本 plan 产出 `invariant-catalog.md` 零 issue、plan 文件 2 处可解析引用已修（`ai-dev/audits/` 前缀），剩余 6 处为 active-plan 降级 warning（`check-nop-stream-invariants.mjs` 前向引用属 I1 交付物 + 省略包前缀类路径简写）——与 0615-2 credential plan 同款裁定「no new broken links from this plan's files」
  - Anti-Hollow 检查：catalog 每条不变式覆盖 ≥1 真实 finding-ID（审计证据文件可查）；无空壳条目；纯文档计划 `scan-hollow-implementations.mjs` 与构建门禁不适用（已按 guide 删除）
  - Deferred 项分类检查：ArchUnit 为 `optimization candidate`（不阻塞盘点价值）；AR-9/AR-18/AR-1/AR-11 四条 live residual 未 defer，显式入 §6 I2 red list——无 in-scope live defect 被降级
- 独立子 agent closure-audit 记录：见上方 Audit Session 三 session + 当日日志 `ai-dev/logs/2026/08-12.md`

Follow-up:

- no remaining plan-owned work（I0 产出全部移交 I1 `2026-08-12-1217-2-...`；未覆盖类别见 Non-Blocking Follow-ups）
