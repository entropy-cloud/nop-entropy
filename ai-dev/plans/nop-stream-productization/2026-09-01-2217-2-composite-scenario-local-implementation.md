# 2 复合场景单进程落地（roadmap item 13）

> Plan Status: active
> Last Reviewed: 2026-09-01
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

Status: planned
Targets: 场景模块（plan 1 裁定）、`ai-dev/design/nop-stream/composite-scenario-design.md`（消费）

- Item Types: `Fix | Decision | Proof`

- [ ] 执行前置核对：plan 1 completed 且设计文档存在（否则本 plan 置 blocked 并记录）；设计文档中「恢复与检查点测试驱动形态」「连接器参数表达形态」两项决策已裁定（若 plan 1 遗漏，本 phase 现场裁定并回写设计文档，显式记录）
- [ ] 模块放置落地：扩展 fraud-example 或新建模块（pom 依赖面按设计文档裁定：flow/connector-*/runtime/rocksdb 按需）
- [ ] S1 场景资产：XDSL 定义文件 + CDC 驱动形态实现（按设计裁定）+ 2PC JDBC sink 目标准备 + 正确性 happy-path 测试（入口到输出）
- [ ] S2 场景资产：XDSL 定义文件（base + Delta 定制）+ file source/sink 接线 + 聚合正确性测试（含 Delta 生效证明断言）
- [ ] Gap A/B/C 交付面按映射裁定落地（Gap B 的 keyBy/窗口/keyed state 用 DataStream API；UnusualAmount 去 stub；死文件处置）

Exit Criteria:

- [ ] 每个场景有 XDSL 声明式定义文件（repo-observable 路径）且经 flow 编译管线加载（非 Java 拼装旁路）；Delta 定制以 delta 文件形态存在（S2）
- [ ] 每个场景至少一条 E2E 测试从 source 定义到 sink 输出断言完整跑通（**端到端验证**，guide Rule #22）
- [ ] **接线验证**（Rule #23）：Phase 1 引入的新组件（CDC 驱动器/sink 目标/Delta 文件）在 E2E 测试中被执行链真实调用（断言可观察输出证明，非仅类型存在）
- [ ] 新增测试基建无空壳/静默跳过（驱动器未实现路径抛异常而非静默返回；**无静默跳过**，Rule #24）
- [ ] 新增功能逐项有对应测试（**Test-Mandated**，Rule #25；测试清单列明「哪个测试验证哪个新行为」）
- [ ] XDSL 定义不使用 flow fail-fast 声明面（`params`/`maxParallelism`/per-transform parallelism 不匹配等，FL-1/FL-2 约束）
- [ ] `./mvnw test -pl <场景模块> -am` 绿（新资产编译 + 新测试通过）
- [ ] owner-doc 裁定：模块 README/结构说明随新资产同步（若涉及），owner-doc 层面无契约变更则显式 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 验收断言集收口（exactly-once + 恢复语义）+ 缺陷修复

Status: planned
Targets: 场景模块、（条件性）缺陷所在模块

- Item Types: `Fix | Decision | Proof`

- [ ] 设计文档验收断言集逐条映射核对：建立「断言 → 测试方法」映射表，无未覆盖断言（不可测试的断言回写设计文档修正，显式记录）
- [ ] exactly-once 断言落地：S1 JDBC sink 端（事务提交结果无重复无丢失）、S2 文件 sink 端（`FileTwoPhaseCommitSink` 提交产物），断言基于 D-GAP §3.2 约束 ④ 语义
- [ ] 恢复语义断言落地：每场景至少一条 checkpoint → 中断/停止 → 从最新 durable manifest 恢复 → 重放 → 结果一致性测试（LOCAL 进程内路径；kill 进程级路径属 plan 3）
- [ ] rescale 断言落地（S2）：restore-time parallelism rescale 路径结果一致性测试（离线 reshard 工具路径按设计文档矩阵裁定，或路由 plan 3）
- [ ] 场景执行发现的缺陷：就地修复 + focused 测试（验证正确结果）；修复不引入空壳；`_` 前缀生成文件禁改（上移源模型/模板）
- [ ] 大缺陷转 Follow-up（编号顺延、来源标注本 plan、证据锚点）

Exit Criteria:

- [ ] 断言→测试映射表落库（plan 文件或当日 log），设计文档验收断言 100% 有测试归属或显式修正记录
- [ ] 每场景恢复语义测试通过（恢复后重放无丢失无重复——**端到端验证**覆盖快照→恢复→重处理完整路径）
- [ ] **接线验证**（Rule #23）：场景新组件（CDC 驱动器/sink 目标/Delta 文件）被场景执行链在运行时真实调用（测试断言可观察输出证明，非仅类型存在）
- [ ] 全部就地修复配 focused 测试（或 Rule #25 显式豁免附理由）；无 `_` 前缀文件手改
- [ ] 大缺陷全部 Follow-up 化（或显式「无大缺陷」）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全模块绿
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module <场景模块> --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [ ] owner-doc 裁定：受影响 owner doc 已最小同步或显式 `No owner-doc update required`（含理由）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 全部场景（S1/S2）LOCAL 端到端绿（XDSL 定义 → 编译管线 → sink 输出断言）
- [ ] 设计文档验收断言集 100% 映射（或显式修正记录回写设计文档）
- [ ] exactly-once + 恢复断言测试全绿；rescale（S2）断言测试按设计矩阵归属本 plan 的范围全绿（路由 plan 3 的格子有显式路由记录）
- [ ] Gap A/B/C 按 plan 1 映射 100% 落地（或显式记录偏差与裁定理由回写设计文档/roadmap）
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect（发现的缺陷已修复或 Follow-up 化，附证据）
- [ ] 受影响 owner docs 已同步或显式 `No owner-doc update required`
- [ ] `./mvnw compile` / `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] checkstyle 随构建通过（沿 items 7—11 现状基线）
- [ ] hollow scan（场景模块，含新建模块）+ invariants + doc-links 三工具退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] **Anti-Hollow Check**：closure audit 验证场景调用链运行时连通（XDSL 加载 → 算子 → sink），无空方法体/静默跳过/no-op 正常实现
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] roadmap item 13 状态写回（closure audit 通过后）

## Deferred But Adjudicated

（执行时按需填写；允许类型仅 `watch-only residual | optimization candidate | out-of-scope improvement`，逐条附 Why Not Blocking Closure）

## Non-Blocking Follow-ups

- （执行时填写；confirmed live defect 不得出现在这里）

## Closure

Status Note: （closure 时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （closure 时填写）
- Evidence: （closure 时填写）

Follow-up:

- （closure 时填写）
