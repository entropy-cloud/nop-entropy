# 2 复合场景单进程落地（roadmap item 13）

> Plan Status: completed
> Last Reviewed: 2026-09-02
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 13（Phase S，critical path，deps: item 12）；stage details「复合场景单进程落地」
> Related: `2026-09-01-2217-1-composite-scenario-design.md`（item 12，前置依赖，其交付物 `ai-dev/design/nop-stream/composite-scenario-design.md` 是本 plan 的输入合同）；`2026-09-01-1457-3-rocksdb-flow-fraud-example-audit.md`（item 11，其 §3.2 路由到 items 12/13 的 fraud-example 改造清单）；`2026-09-01-2217-3-composite-scenario-distributed-verification.md`（item 14，后继，消费本 plan 的场景测试资产）
> Mission: nop-stream-productization
> Work Item: roadmap item 13

## Purpose

在 LOCAL 模式下把 `composite-scenario-design.md` 定义的全部复合场景（S1/S2 及并入的 Gap 交付面）跑通为可运行测试：XDSL 声明式定义优先，每个场景的验收断言集（正确性 + exactly-once + 恢复语义）逐条映射为 focused 测试；就地修复场景执行中发现的缺陷（大缺陷转 Follow-up）；落地 item 11 路由的 fraud-example 改造项。全模块回归绿。本 plan 与 plan 3（item 14）共同构成 M3 解锁条件（13+14 done）。

## Current Baseline

（2026-09-01 live 核对；**执行前置条件**：plan 1（item 12）已 completed 且 `ai-dev/design/nop-stream/composite-scenario-design.md` 落库——本 plan 执行前必须核对存在，不存在则 blocked）

- **场景合同**：设计文档由 plan 1 交付（S1: CDC → CEP → 窗口聚合 → 2PC JDBC sink；S2: file source → keyBy 聚合 + Delta 定制 → exactly-once 文件 sink + rescale；Gap A/B/C 映射裁定 + 模块放置决策 + W-F5 目录化裁定内嵌其中）——本 plan 不重开设计，按合同实施
- **fraud-example 现状**（item 11 报告 §2.4）：pom 仅依赖 `nop-stream-cep`；`FraudDetectionDemo` 直连 NFA 内部 API；`fraud-detection.stream.xml` 死文件；`UserTransactionHistory` 死代码；`UnusualAmountPattern` DEMO STUB（固定 $100 均值）；README 已做事实修复（FX-4）但 Known Limitations 披露死 XDSL 文件待处置
- **flow XDSL 执行面**：`StreamModelDslBuilder`（parse → build → execute）+ testing helpers（`CollectingSinkFunction`/`IntegerSourceFunction`/`TestSourceFunction` 等，`nop-stream-flow/src/test/java/io/nop/stream/flow/testing/`）+ fixtures（`_vfs/nop/stream/test/test-*.stream.xml` 含 Delta 三形态）；item 11 FL-3 先例：XDSL 定义到执行的 E2E 测试模式已验证（timestampsWithoutAssignerExecutesWithoutNpe）
- **flow 声明面约束**（live，item 11 FL-1/FL-2 后）：`params`/`outputType`/`inputType`/`maxParallelism`/非默认 `consistencyCapability`/per-transform parallelism 不匹配 → build 期 fail-fast；场景 XDSL 不得使用（消费属 item 29）
- **S1 组件**：`DebeziumCdcSourceFunction`（connector-debezium）+ `JdbcTwoPhaseCommitSink`/`Builder`（connector-jdbc）；驱动形态按设计文档裁定实施
- **S2 组件**：file source/sink（connector，含 `FileTwoPhaseCommitSink`）；restore-time parallelism rescale + 离线 `MaxParallelismReshardMigration` 路径（`TestMaxParallelismReshardMigrationE2E` 先例）
- **checkpoint/恢复断言基线**：D-GAP §3.2 约束 ④——以「最新 durable epoch manifest 恢复 + exactly-once 结果」为准（manifest 级 checksum 属 item 25，不断言其行为）。**已知集成空白**：checkpoint/恢复测试先例全在 runtime 层且为 Java 拼装管线（`TestE2ECheckpointAndRecovery`/`TestE2EManifestRestoreIdAdvance`/`TestMailboxE2ECheckpoint` 等），「XDSL 场景管线 + checkpoint 开启执行 + manifest 恢复」组合**无先例**（flow main 侧 "restore" 零命中，`StreamModelDslBuilder.build()` 只产出 env）——恢复测试的驱动形态（XDSL 入口直驱 vs runtime harness 组合）是 plan 1 设计文档的关键决策之一，本 plan 消费该裁定执行
- **异常处理边界**：默认 fail-fast + 恢复路径既有 per-path 语义（P-REQ-17 defer）——场景执行发现 poison-record 类需求时记录为 revisit 证据，不私造策略机制
- **验证基建**：JUnit 5 + Nop AutoTest（仓库既有）；LOCAL 模式 = 五层编译管线既有双模式之一（roadmap Current baseline）
- **工具门禁**：`ai-dev/tools/scan-hollow-implementations.mjs`（--severity high 退出码 0 硬门禁）、`check-nop-stream-invariants.mjs`、`check-doc-links.mjs --strict`

## Goals

- 全部场景（S1/S2）在 LOCAL 模式端到端跑通：从 XDSL 声明定义（文件，非纯 Java 拼装）经编译管线到 sink 输出，每场景至少一条「入口点到最终输出」的 E2E 测试
- 设计文档验收断言集逐条映射为 focused 测试并全绿（断言正确结果而非仅无异常）；exactly-once 断言落在 sink 端可观察输出（无重复无丢失）；恢复语义断言走 checkpoint → 中断 → 恢复 → 重放路径
- Gap A/B/C 按 plan 1 映射裁定落地（含 UnusualAmount 去 stub、`UserTransactionHistory` 复活或裁定删除、死 `fraud-detection.stream.xml` 处置、README 同步）
- 场景执行发现的缺陷就地修复（限单 plan 范围，配 focused 测试）；跨模块/大缺陷转 roadmap Follow-up（编号顺延）
- 模块放置按 plan 1 决策执行（新模块则 pom/包结构遵循 `docs-for-ai/01-repo-map/domain-module-pattern.md`）
- roadmap item 13 写回

## Non-Goals

- 多 JVM / DISTRIBUTED 模式验证（plan 3 / item 14）
- 长时 soak / chaos / backpressure 稳定性演练（item 15）
- flow `params`/`maxParallelism`/per-transform parallelism 消费实现（item 29）；连接器新能力开发（item 19）；提交前校验（item 20）
- checkpoint manifest 字段落地（item 25）与 runtime 协调器结构治理（item 26/27/28）
- core/rocksdb SerDe 重构（items 21/30）
- 既有 868 个测试的行为回归重写（只增不破；发现既有测试错误时按 bug fix 流程处置并记录）

## Scope

### In Scope

- 新增/修改：场景模块（`nop-stream-fraud-example` 扩展或新 demo 模块，按 plan 1 裁定）——XDSL 场景定义文件、场景驱动/断言测试、必要的测试基建（如 S1 事件源驱动器、H2 JDBC sink 目标 DDL）
- 新增/修改：fraud-example 改造项（item 11 §3.2 路由清单，按 plan 1 映射裁定）
- 修复：场景执行发现的缺陷（单 plan 范围内）+ focused 测试
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（Follow-up 追加（如有）+ item 13 closure 写回 + Last updated）
- 修改（条件性）：受影响 owner-doc 最小同步（`ai-dev/design/nop-stream/connector-design.md`/`stream-dsl-design.md` 等，仅当修复/落地改变契约语义时；否则显式 `No owner-doc update required`）
- 只读输入：`composite-scenario-design.md`（合同）、D-GAP §3.2、item 11 报告 §2.4/§3.2

### Out Of Scope

- `nop-stream-runtime` 分布式路径改动（除非 LOCAL 场景缺陷修复所需的最小修复；结构性改动转 Follow-up）
- 16 份既有设计文档结构性重写（D-DRIFT-1 修正属 item 17）
- `docs-for-ai/` 用户指南撰写（item 17；场景 README 只做模块级事实同步）

## Execution Plan

### Phase 1 - 场景资产落地（XDSL 定义 + 测试基建）

Status: completed
Targets: `nop-stream/nop-stream-fraud-example`（D8 裁定：扩展 fraud-example，不新建模块）、`ai-dev/design/nop-stream/composite-scenario-design.md`（消费）

- Item Types: `Fix | Decision | Proof`

- [x] 执行前置核对：plan 1 completed 且设计文档存在（否则本 plan 置 blocked 并记录）；设计文档中「恢复与检查点测试驱动形态」「连接器参数表达形态」两项决策已裁定（若 plan 1 遗漏，本 phase 现场裁定并回写设计文档，显式记录）
- [x] 模块放置落地：扩展 fraud-example（pom 依赖面按设计文档 §3.4.8 D8：+ flow / connector-debezium / connector-jdbc / connector / runtime / rocksdb / h2(test)）
- [x] S1 场景资产：XDSL 定义文件 + CDC 驱动形态实现（按设计裁定）+ 2PC JDBC sink 目标准备 + 正确性 happy-path 测试（入口到输出）
- [x] S2 场景资产：XDSL 定义文件（base + Delta 定制）+ file source/sink 接线 + 聚合正确性测试（含 Delta 生效证明断言）
- [x] Gap A/B/C 交付面按映射裁定落地（Gap B 的 keyBy/窗口/keyed state 用 DataStream API；UnusualAmount 去 stub；死文件处置）

Exit Criteria:

- [x] 每个场景有 XDSL 声明式定义文件（repo-observable 路径）且经 flow 编译管线加载（非 Java 拼装旁路）；Delta 定制以 delta 文件形态存在（S2）
- [x] 每个场景至少一条 E2E 测试从 source 定义到 sink 输出断言完整跑通（**端到端验证**，guide Rule #22）
- [x] **接线验证**（Rule #23）：Phase 1 引入的新组件（CDC 驱动器/sink 目标/Delta 文件）在 E2E 测试中被执行链真实调用（断言可观察输出证明，非仅类型存在）
- [x] 新增测试基建无空壳/静默跳过（驱动器未实现路径抛异常而非静默返回；**无静默跳过**，Rule #24）
- [x] 新增功能逐项有对应测试（**Test-Mandated**，Rule #25；测试清单列明「哪个测试验证哪个新行为」）
- [x] XDSL 定义不使用 flow fail-fast 声明面（`params`/`maxParallelism`/per-transform parallelism 不匹配等，FL-1/FL-2 约束）
- [x] `./mvnw test -pl <场景模块> -am` 绿（新资产编译 + 新测试通过）
- [x] owner-doc 裁定：模块 README 随新资产同步（双入口叙事 + 场景资产结构 + Scenario Notes）；owner-doc 层面无契约变更则显式 `No owner-doc update required`（设计文档 A.0 落地裁定记录为唯一契约层回写）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 验收断言集收口（exactly-once + 恢复语义）+ 缺陷修复

Status: completed
Targets: 场景模块、（条件性）缺陷所在模块（core/cep/connector 就地最小修复）

- Item Types: `Fix | Decision | Proof | Follow-up`

- [x] 设计文档验收断言集逐条映射核对：建立「断言 → 测试方法」映射表（本文件附录 A），无未覆盖断言（不可测试的断言回写设计文档修正，显式记录）
- [x] exactly-once 断言落地：S1 JDBC sink 端（事务提交结果无重复无丢失）、S2 文件 sink 端（`FileTwoPhaseCommitSink` 提交产物），断言基于 D-GAP §3.2 约束 ④ 语义
- [x] 恢复语义断言落地：每场景至少一条 checkpoint → 中断/停止 → 从最新 durable manifest 恢复 → 重放 → 结果一致性测试（LOCAL 进程内路径；kill 进程级路径属 plan 3）
- [x] rescale 断言落地（S2）：restore-time parallelism rescale 路径结果一致性测试（离线 reshard 工具路径按设计文档矩阵裁定，或路由 plan 3）
- [x] 场景执行发现的缺陷：就地修复 + focused 测试（验证正确结果）；修复不引入空壳；`_` 前缀生成文件禁改（上移源模型/模板）
- [x] 大缺陷转 Follow-up（编号顺延、来源标注本 plan、证据锚点）

Exit Criteria:

- [x] 断言→测试映射表落库（本文件附录 A），设计文档验收断言 100% 有测试归属或显式修正记录
- [x] 每场景恢复语义测试通过（恢复后重放无丢失无重复——**端到端验证**覆盖快照→恢复→重处理完整路径）
- [x] **接线验证**（Rule #23）：场景新组件（CDC 驱动器/sink 目标/Delta 文件）被场景执行链在运行时真实调用（测试断言可观察输出证明，非仅类型存在）
- [x] 全部就地修复配 focused 测试（或 Rule #25 显式豁免附理由）；无 `_` 前缀文件手改
- [x] 大缺陷全部 Follow-up 化（或显式「无大缺陷」——发现的 6 项引擎缺陷就地修复（bug note `ai-dev/bugs/2026-09/2026-09-02-composite-scenario-uncovered-engine-defects.md`），2PC sink P>1 门禁为既有显式 defer（CONN-01 P1），A2-4 LOCAL P>1 路由 plan 3（设计文档 A.0 裁定 5））
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全模块绿
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module <场景模块> --severity high` 退出码 0
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [x] owner-doc 裁定：`composite-scenario-design.md` 追加 A.0 落地裁定记录（7 项实现级偏差显式记录）；`connector-design.md`/`stream-dsl-design.md` 无契约语义变更——`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部场景（S1/S2）LOCAL 端到端绿（XDSL 定义 → 编译管线 → sink 输出断言）
- [x] 设计文档验收断言集 100% 映射（或显式修正记录回写设计文档）
- [x] exactly-once + 恢复断言测试全绿；rescale（S2）断言测试按设计矩阵归属本 plan 的范围全绿（路由 plan 3 的格子有显式路由记录——设计文档 A.0 裁定 5）
- [x] Gap A/B/C 按 plan 1 映射 100% 落地（或显式记录偏差与裁定理由回写设计文档/roadmap——设计文档 A.0）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect（发现的缺陷已修复或 Follow-up 化，附证据）
- [x] 受影响 owner docs 已同步或显式 `No owner-doc update required`
- [x] `./mvnw compile` / `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] checkstyle 随构建通过（沿 items 7—11 现状基线）
- [x] hollow scan（场景模块，含新建模块）+ invariants + doc-links 三工具退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] **Anti-Hollow Check**：closure audit 验证场景调用链运行时连通（XDSL 加载 → 算子 → sink），无空方法体/静默跳过/no-op 正常实现
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] roadmap item 13 状态写回（closure audit 通过后）

## Deferred But Adjudicated

- **A2-4 LOCAL restore-time parallelism rescale（P>1 + 2PC sink 组合）**
  - Classification: `out-of-scope improvement`（路由 successor）
  - Why Not Blocking Closure: 引擎对 2PC sink 在有效并行度 >1 build 期显式 fail-fast（`ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`，CONN-01 P1 有意 defer，checkpoint-design §6.4.1——已进入 fail-fast 的不可降级硬规则），当前引擎无法与 exactly-once 文件 sink 同表；LOCAL 已覆盖 keyed 状态跨恢复续算（W1 跨运行窗口）+ 双后端恢复 + 离线 reshard 新 maxParallelism 恢复执行（keyed 再路由）
  - Successor Required: yes
  - Successor Path: plan 3（`2026-09-01-2217-3-composite-scenario-distributed-verification.md`，矩阵 C2 必格）
- **有界源末窗（terminator 自身窗口）在 EOS 保持 in-flight、由恢复运行补齐**
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: 引擎既有语义（2PC sink 仅在完成的 checkpoint 上提交；CANCEL 末 checkpoint best-effort 且竞态由 barrier-drop 修复收敛为良性）——不是本 plan 引入的缺陷；恢复测试已证明 in-flight 窗口跨恢复补齐；矩阵 C0/C1（plan 3）覆盖持续运行形态（非有界终止）
  - Successor Required: no

## Non-Blocking Follow-ups

- （无新增 roadmap Follow-up：发现的 6 项引擎缺陷已就地修复（见 `ai-dev/bugs/2026-09/2026-09-02-composite-scenario-uncovered-engine-defects.md`）；A2-4 路由已入 plan 3 范围；2PC P>1 门禁为既有 defer 项非本 plan 发现的新缺陷）

## Closure

Status Note: S1/S2 复合场景以 XDSL 声明式定义在 LOCAL 模式端到端跑通（fraud-example 场景模块扩展，D8）；设计文档验收断言 100% 映射（附录 A；A2-4 LOCAL P>1 显式路由 plan 3）；场景执行发现的 6 项引擎缺陷全部就地修复配回归（bug note）；Gap A/B/C 100% 落地；全模块测试与四工具门禁全绿；Deferred 区 2 条均附 non-blocking 理由。无剩余 plan-owned work。

Completed: 2026-09-02

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，task `ses_fa1ad6cbfffeumKpvD5XpqeGGx`）
- Evidence:
  - A.1—A.4（Phase 1 exit criteria）全 PASS：XDSL 三文件存在且含 checkpoint/patterns 链声明、死文件 0 残留；8 个测试类存在且断言可观察输出（精确期望集/多重集/manifest 契约）；场景主代码类与继承关系 live 核对（UserHistoryEnricher:40/ReplayableCdcSourceFunction:46/DirectoryFileSourceFunction:46-51）；pom 依赖面与 D8 一致。
  - B.5—B.9（Phase 2 exit criteria）全 PASS：附录 A 方法名 12/12 grep 命中（超 8 项抽查基线）；6 项引擎修复 live 锚点核对（JobGraphGenerator topologicalOrder/resolveJobEdgePartitioner、ProcessOperator.open keyed backend、JavaStreamSerializer+CepOperator 装配、setPendingCommits Long 键归一、ResultPartition barrier 丢弃、MemoryStateSerDe JAVA_BYTES_MARKER）；`./mvnw test` 场景 5 类 10/10 绿 BUILD SUCCESS；hollow scan exit 0（0 findings）；invariants exit 0。
  - C. Anti-Hollow PASS：XDSL 20 个 bean 引用与 s1Resolver/s2Resolver 1:1 全解析；scenario 包无 TODO/空 catch（唯一 `continue` 为 CRLF 规范化合法逻辑）；运行时调用链（parseStreamXml→buildEnv→env.execute→sink 可观察产物）经 E2E 证明连通。
  - D.10—D.13 诚实性全 PASS：A2-4 路由记录双落（plan Deferred + 设计文档 A.0 裁定 5）+ 引擎门禁 live 证据（NopStreamErrors:438/StreamGraphGenerator:367）；bug note 6 缺陷与修复一一对应；roadmap item 13 done + Last updated 同步；daily log 三条目齐备。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（勾选完整 + Closure Evidence 写入后复核）。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（30900+ refs / 0 errors）。
  - `./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS（全模块，含 fraud-example 57/57）。
  - 审计附带观察（非 rubric）：交付未提交（待 mission-driver 统一 nop-git-master 收口）；companion 文档先行引用 closure 状态属标准收口顺序，attribution 已按本 session 修正。

Follow-up:

- no remaining plan-owned work
- A2-4 LOCAL P>1 restore-rescale → plan 3（item 14，矩阵 C2 必格，随并行 2PC successor 能力消费）
- 有界源末窗 EOS in-flight 补齐语义 → watch-only（Deferred 裁定 2；持续运行形态由 plan 3 C0/C1 覆盖）

## 附录 A — 验收断言 → 测试映射表（设计文档 §3.1.4 / §3.2.4 全量）

| 断言 | 测试（模块 `nop-stream-fraud-example` src/test） | 结果 |
|------|------|------|
| A1-1 乱序不变性 | `TestS1CdcPipelineE2E#s1PipelineProducesExactExpectedAlertRows`（bob 历史乱序到达 4s→3s→5s，均值/窗口归属不变；期望集精确相等） | 绿 |
| A1-2 迟到丢弃 | 同上（heidi 2500@5s 最后到达，CEP 可触发但 W0 已关——无输出行；负向断言显式） | 绿 |
| A1-3 CEP 精确匹配/跨用户零误报 | 同上（4 模式各恰一组 alert；gina 干扰序列零行；frank/carol/heidi 零行）+ `TestS1PatternDeclarations`（4 模式 NFA 级精确匹配/拒绝） | 绿 |
| A1-4 keyed state 富化生效 | 同上（carol 均值 500 控制组无 alert——与固定 $100 stub 可区分）+ `TestS1PatternDeclarations#unusualPatternUsesEnrichedAverageGate` | 绿 |
| A1-5 无重复无丢失 + ledger | `TestS1CdcPipelineE2E#s1ExactlyOnceLedgerAndPrimaryKeyShapeHolds`（H2 PK + 恰 4 行 + 4 ledger 表非空） | 绿 |
| A1-6 中断重放语义 | `TestS1CdcRecoveryE2E#s1RestoreReplaysFromCheckpointAndCommitsExactlyOnce`（run1 W0 片段 → 恢复 → 重放余量 → 终表=全量期望集无重复；bob 行依赖恢复的 keyed 历史——hollow restore 可检测） | 绿 |
| A1-7 checkpoint 语义锚点 | 同上（恢复前 durable epoch manifest 存在断言 + run2 ledger epoch 严格递增断言；不断言 manifest 校验行为） | 绿 |
| A2-1 聚合正确性 | `TestS2FileAggregationE2E#s2XdslBaseAggregatesExactlyAndSinkIsExactlyOnce`（行集精确多重集相等，含文件内乱序行） | 绿 |
| A2-2 Delta 生效证明 | `TestS2FileAggregationE2E#s2DeltaBlacklistRemovesExactlyTheBlacklistedRows`（delta 输出 = base 输出 − 黑名单行；delta-unique 负向断言） | 绿 |
| A2-3 入口等价 | `TestS2FileAggregationE2E#s2JavaVariantMatchesXdslOutput`（DataStream API 对照变体输出 == XDSL 输出 == 期望集） | 绿 |
| A2-4 restore-rescale 一致性 | LOCAL P>1 形态路由 plan 3（设计文档 A.0 裁定 5：引擎 2PC sink P>1 fail-fast 硬门禁）；LOCAL 侧面覆盖：`TestS2RecoveryAndRescaleE2E`（keyed 窗口状态跨恢复续算/双后端）+ `TestS2OfflineReshardE2E`（新 maxParallelism 下 keyed 再路由恢复） | 路由记录在案 + 侧面绿 |
| A2-5 离线 reshard | `TestS2OfflineReshardE2E#s2OfflineReshard128to256ThenRestoreCompletesExactlyOnce`（真实场景 savepoint 128→256 迁移 + key 守恒 + 迁移后恢复执行 + 输出=全量期望集（含 cursor 保持不重读）） | 绿 |
| A2-6 状态后端切换变体 | `TestS2RecoveryAndRescaleE2E#s2Recovery_memoryBackend` / `#s2Recovery_rocksdbBackend`（同一恢复断言集，memory 与 RocksDB 后端） | 绿 |
| A2-7 文件 sink exactly-once | `TestS2FileAggregationE2E#s2XdslBaseAggregatesExactlyAndSinkIsExactlyOnce` + `TestS2RecoveryAndRescaleE2E.assertMergedExactlyOnce`（epoch 文件行多重集恰一次 + manifest key 集 == epoch 文件集 + 无 .tmp 残留；口径=全部已关闭窗口，terminator 末窗 in-flight 由恢复补齐——Deferred 裁定 2） | 绿 |

**Rule #25 新功能测试清单**（新行为 → 验证测试）：
- `UserHistoryEnricher`（keyed ValueState 均值/prevCity 富化）→ A1-4 两条 + `TestS1CdcRecoveryE2E`（跨恢复 keyed 续算）
- `FraudAlertPatternFunction`（确定性 select）→ A1-3
- `AlertCountAggregate`/`TransactionWindowAggregate`（窗口聚合 + 窗口界恢复）→ A1-5/A2-1
- `ReplayableCdcSourceFunction`（offset checkpoint/恢复/有界完成/AR-03/未命名 fail-fast）→ `TestReplayableCdcSourceFunction`（5 用例）+ A1-6
- `DirectoryFileSourceFunction`（字节 cursor/恢复续读/rescale 空态守卫/缺目录 fail-fast/REPLAYABLE/坏状态 fail-fast/并发防重）→ `TestDirectoryFileSourceFunction`（8 用例）+ A2-x
- `CdcChangeDecoder`/`TransactionLineParser`（fail-fast 解码）→ E2E 内经全量事件验证 + malformed 路径抛异常（Rule #24）
- XDSL 场景文件（S1 4 链拓扑/checkpoint/patterns 链接语义）→ `TestS1CdcPipelineE2E#s1PipelineIsXdslDrivenNotJavaAssembled` + `TestS1PatternDeclarations`
- 引擎修复（6 项）→ 场景 E2E 全量 + 既有 1516 core/359 cep 等回归绿（bug note Protection 节）
