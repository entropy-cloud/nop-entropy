# 文档产品化（roadmap item 17 / P-REQ-22..25 + P-REQ-16 文档化 + D-DRIFT-1 收口）

> Plan Status: completed
> Mission: nop-stream-productization
> Work Item: item 17 文档产品化
> Last Reviewed: 2026-09-03
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 17 + Stage 17；`ai-dev/analysis/2026-09/2026-09-01-competitor-productization-synthesis-and-p-req.md` §2.2（P-REQ-22..25 验收标准，归属 item 17；P-REQ-25 属 getting-started 范畴）；`ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md` §2.1（P-REQ-16 go 裁定 → item 17 文档化交付）、§1.4/§3.4（D-DRIFT-1 RuntimeTopology 修正建议移交 item 17）；`ai-dev/analysis/2026-09/2026-09-01-nop-stream-core-module-audit.md` §2.1 ②（触发语义接线矩阵，item 17 直接消费）；`ai-dev/analysis/2026-09/2026-09-01-nop-stream-rocksdb-flow-fraud-example-audit.md` §2.4（fraud-example 完整度评估 + 脚手架可用性准则表 + 3 入门拓扑缺口清单，D-GAP §3.1 ③ 路由为 P-REQ-25/item 17 输入）
> Related: `2026-09-02-2216-1-observability-ops-productization.md`（item 16，owner doc 运维契约已落，本 plan 补用户面）；`2026-09-03-0617-2-final-acceptance-audit.md`（item 18 最终验收以本 plan 交付物为核验对象，执行顺序在本 plan 之后）

## Purpose

把 nop-stream 的用户文档面从「内部设计文档 + 运维 owner doc」推进到「产品用户文档体系」：用户指南（含触发语义映射表）、连接器目录与能力矩阵、CDC 生产化 cookbook、版本化迁移指南首版、快速起步脚手架——P-REQ-22..25 全部 met，P-REQ-16 文档化交付落地，D-DRIFT-1（RuntimeTopology 概念退役）设计文档修正收口，`docs-for-ai/INDEX.md` 与 source-anchors 同步且 link checker 通过。

## Current Baseline

（2026-09-03 live 核对）

- **owner doc 已有运维契约，无用户文档**：`docs-for-ai/03-modules/nop-stream.md`（218 行，item 16 交付）= 模块路由 + 指标名表/REST/健康/告警/治理 + 运维手册；无 DataStream API / XDSL / 连接器使用 / 分布式部署的用户指南内容。`docs-for-ai/03-modules/` 下 nop-stream 相关仅此一页。
- **P-REQ-22..25 现状均为未满足**（P-REQ 报告 §2.6 抽查 + 本轮 `ls docs-for-ai/03-modules/` 复核）：无连接器目录页、无 CDC cookbook、无迁移指南、无快速起步脚手架。
- **INDEX/source-anchors 现状**：`docs-for-ai/INDEX.md` 已有 nop-stream 路由行（:132—:135 共 4 行：owner doc 路由/CEP/checkpoint/运维契约）+ module-groups 段落（:229）；`04-reference/source-anchors.md` STRM-001..045（STRM-038..045 为 item 16 新增）。
- **README 与设计文档 drift 未修**：`nop-stream/README.md:5`（管线叙述含 RuntimeTopology）与 `:7`（「`RuntimeTopology` 类仍处于概念阶段」表述）均残留；`ai-dev/design/nop-stream/00-vision.md` §九、`01-architecture-baseline.md`（:13/:21/:33/:100/:117/:127 等，以 D-GAP §1.4 索引为子集、live 全量扫描为权威）同样保留；D-GAP 已裁定 exclude（概念退役），修正建议显式移交本 item（D-GAP §3.4，不在 item 6 顺手重写）。
- **触发语义证据已备**：item 7 core 审计产出行为级证据——Trigger 家族 11 类（10 个 Trigger/Evictor 类 + `Triggerable`，每类专属单测）+ assigner/countWindow 接线矩阵 8 行（6 个 assigner 映射 + `KeyedStreamImpl.countWindow` 两形态）+ runtime WindowOperator accumulator 集成 + 作业级等价物（`CheckpointConfig` 配置项族——以 live 全量为准，不预设计数 + processing-time timer + DRAIN truncation），报告 §2.1 ② 自包含，可直接作为映射表素材。
- **分布式入口类 scope 事实**：`JobCoordinatorMain`/`TaskManagerMain` 位于 `nop-stream-runtime/src/test/java/.../launch/`（test scope；runtime test-jar 已由 item 14 导出供 gated 测试消费）——产品用户指南引用前须显式裁定消费方式（见 Phase 1 Decision D1b）。
- **连接器 live 清单**（main 代码，本轮 find 复核）：`nop-stream-connector`（FileSource/FileSourceReader、FileTwoPhaseCommitSink、MessageSourceFunction/MessageSinkFunction）、`nop-stream-connector-jdbc`（JdbcTwoPhaseCommitSink + Builder）、`nop-stream-connector-debezium`（DebeziumCdcSourceFunction）、`nop-stream-connector-batch`（BatchLoaderSourceFunction/BatchConsumerSinkFunction）。2PC sink parallelism=1 已证明、P>1 被 `StreamGraphGenerator` 规划期 fail-fast 门禁拒绝（`ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`，STRM-033/036）。
- **示例资产现状**：fraud-example 已承载产品级复合场景（S1 CDC→CEP→窗口→2PC JDBC、S2 文件→keyBy 聚合+Delta→文件 sink + rescale，item 13；分布式矩阵 item 14；演练 item 15）——但它是「高级复合示例」，入门 tier（最小 source→transform→sink）无独立载体。item 11 审计 Gap A/B/C 已由 items 12/13 消化，不重复处理。
- **状态/迁移机制现状**（迁移指南素材）：`StateMigrationRegistry`/`MigratableKeyedState`、离线 reshard `MaxParallelismReshardMigration`（注意拼写 Parallelism；D-GAP 报告 §1.1/§2.5/§3.2 中 `MaxParallelity...` 为笔误，以 live 类名为准）、状态重置 `StreamStateResetTool`（reset-state/reshard 维护入口族，STRM-043）、key-group layout v2、CEP 状态 JSON 持久化 `__java_bytes__` marker（item 13）；无已发布版本、无格式版本递增（P-REQ-21 defer 前提成立）。

## Goals

- P-REQ-22 连接器目录：目录页覆盖现有全部连接器（file/message/jdbc/debezium/batch 桥接），逐项标注 source/sink、exactly-once/at-least-once 语义、并行度支持，语义标注与 live 代码行为核对一致（含如实标注 2PC sink P=1 门禁）。
- P-REQ-23 CDC cookbook：四类操作场景各一节（snapshot→增量切换、offset 恢复与重放、schema 演进边界、故障排查与全新重跑）；至少抽 2 项声称与 debezium 模块 live 行为核对一致并在文中记录核对锚点。
- P-REQ-24 迁移指南首版：覆盖 XDSL 变更与状态格式变更两条轴 + 版本策略声明（无已发布版本背景下的前瞻性政策，P-REQ-21 defer 前提如实记录）。
- P-REQ-25 快速起步脚手架：maven archetype 或等价模板 + 3 个入门示例拓扑（必含 source→transform→sink 最小链路）；存在脚本化验证证明「脚手架生成的工程可 `mvn test` 通过」。
- P-REQ-16 文档化交付（go 裁定）：用户指南含「触发语义映射表」（nop-stream 既有机制 → Spark/SeaTunnel/Flink 竞品概念对照，消费 item 7 证据表）。
- D-DRIFT-1 收口：`nop-stream/README.md` 与 `00-vision.md`/`01-architecture-baseline.md` 中 RuntimeTopology 管线叙述按 D-GAP 裁定修正（移除或改注「概念已退役」）。
- `docs-for-ai/INDEX.md` 路由与 `04-reference/source-anchors.md` 锚点同步；`node ai-dev/tools/check-doc-links.mjs --strict` exit 0。

## Non-Goals

- 营销/官网类内容（roadmap item 17 Out of scope）。
- Web 控制台（P-REQ-9 已 defer，revisit 属 item 18）。
- 提交前校验/凭据加密（P-REQ-13/14 → Follow-up item 20，代码交付）。
- 连接器 SPI 注册中心与 OLAP 端连接器（P-REQ-28 → Follow-up item 19）；本 plan 只为**现有**连接器编目，不新增连接器。
- 跨版本升级兼容测试基建（P-REQ-21 已 defer；迁移指南只写政策与现状基线，不建测试基建）。
- K8s/YARN/HPA 部署编排文档（P-REQ-15 已 defer/exclude，D-GAP §2.2；用户指南分布式部署章节只覆盖已实现的 JDBC 集群/独立进程形态）。
- 新引擎功能实现；fraud-example 复合场景的扩展（items 12—15 已收口）。
- 设计文档体系性重写（除 D-DRIFT-1 明确列出的位置外，不动 `ai-dev/design/nop-stream/` 其他内容）。

## Scope

### In Scope

- `docs-for-ai/03-modules/` 下新增 nop-stream 用户文档页族（用户指南、连接器目录、CDC cookbook、迁移指南——具体文件布局由 Phase 1 Decision 裁定）。
- `nop-stream/README.md`、`ai-dev/design/nop-stream/00-vision.md`、`01-architecture-baseline.md` 的 D-DRIFT-1 定点修正。
- 快速起步脚手架载体（archetype 模块或等价模板，Phase 4 Decision 裁定）及其脚本化验证。
- `docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md` 同步。

### Out Of Scope

- `docs-for-ai/02-core-guides/`、`03-runbooks/` 平台级文档（nop-stream 专属内容一律落 03-modules 页族）。
- 连接器代码变更、引擎行为变更。
- `ai-dev/design/nop-stream/` 除 D-DRIFT-1 清单外的任何修改。

## Execution Plan

### Phase 1 - 文档架构裁定 + 用户指南主体（含 P-REQ-16 触发语义映射表）+ D-DRIFT-1 修正

Status: completed
Targets: `docs-for-ai/03-modules/nop-stream*.md`（新页族）、`docs-for-ai/INDEX.md`、`nop-stream/README.md`、`ai-dev/design/nop-stream/00-vision.md`、`ai-dev/design/nop-stream/01-architecture-baseline.md`

- Item Types: `Decision | Fix | Follow-up`

- [x] **Decision D1**：用户文档页族布局裁定（单页 vs `nop-stream-<topic>.md` 多页族；连接器目录/cookbook/迁移指南的文件归属；INDEX 路由行方案），记录于本 plan 或随页族 README
- [x] **Decision D1b**：分布式部署章节对 test-scope 入口类（`JobCoordinatorMain`/`TaskManagerMain`）的引用方式裁定——(a) 文档化 test-jar 消费方式、(b) 裁定「产品级 main-scope 入口」为 Follow-up 候选并在指南中如实标注现状、(c) 接受现状直接引用；三选一留痕于 daily log
- [x] 用户指南主体：DataStream API（入口/常用算子/状态与计时器心智模型）、XDSL 声明式编排（`.stream.xml` + bean/xpl 双函数形态 + Delta 定制，以 S1/S2 与 flow 测试 fixture 为实例锚点）、连接器使用指引（链到 Phase 2 目录页）、分布式部署（集群配置、JDBC 集群注册/选主、与 owner doc 运维手册的分工引用；入口类引用方式按 D1b）
- [x] **Fix D-DRIFT-1**：以 `rg -n "RuntimeTopology" nop-stream/README.md ai-dev/design/nop-stream/` 全量扫描为权威清单（D-GAP §1.4 索引为其子集，README:7 亦在修正范围），逐处移除或改注「概念已退役（2026-09-01 D-GAP 裁定）」；修正后残留命中仅剩带退役注记的表述
- [x] **P-REQ-16 交付**：触发语义映射表落用户指南（既有机制全集——窗口 Trigger 家族/`CheckpointConfig` 配置项族（live 全量）/processing-time timer/DRAIN truncation——到 Spark Triggers/SeaTunnel/Flink 概念的对照；消费 item 7 审计 §2.1 ② 证据，不重新审计）

Exit Criteria:

- [x] 用户指南页存在（路径按 D1 落定）且四主题（DataStream/XDSL/连接器指引/分布式部署）各成章节；每个代码级声称（类名/配置键/文件名）与 live repo 一致（执行时逐个锚点核对）
- [x] 触发语义映射表存在且覆盖：Trigger/Evictor 家族 11 类 + assigner/countWindow 接线矩阵 8 行 + 作业级等价物三组（CheckpointConfig 配置项族/processing-time timer/DRAIN truncation）
- [x] D1b 裁定在档（daily log 可查），分布式部署章节与裁定一致
- [x] D-DRIFT-1 修正完成：全量 rg 扫描清单逐位置修正或加退役注记（含 README:5/:7；逐位置记录于 daily log），残留命中均带退役注记
- [x] No new test required: 纯文档变更（本 Phase 无代码）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 连接器目录与能力矩阵（P-REQ-22）

Status: completed
Targets: `docs-for-ai/03-modules/`（连接器目录页，路径按 D1）

- Item Types: `Proof`

- [x] 能力矩阵逐连接器落表：`FileSource`/`FileSourceReader`（含恢复后字节光标语义）、`FileTwoPhaseCommitSink`、`MessageSourceFunction`/`MessageSinkFunction`（SysDao/Pulsar/Kafka wire 后端）、`JdbcTwoPhaseCommitSink`（+Builder）、`DebeziumCdcSourceFunction`、`BatchLoaderSourceFunction`/`BatchConsumerSinkFunction`（nop-batch 桥接）
- [x] 每项标注：source/sink 方向、交付语义（exactly-once/at-least-once/best-effort 及依据）、并行度支持（2PC sink P=1 + `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED` 规划期门禁必须如实标注）、恢复语义（cursor/offset checkpoint 路径）
- [x] 语义标注与 live 代码逐项核对（不允许从记忆或旧文档抄写；核对锚点记录于 daily log）

Exit Criteria:

- [x] 目录页覆盖现有全部连接器（Current Baseline 清单 10 类组件全数在表，无遗漏）
- [x] 每行语义标注有 live 代码依据；at-least-once 与 exactly-once 的区分与代码实际行为一致（如 message sink 的语义按实现如实标注，宁缺勿错——不确定处标注「语义待证」并转 Non-Blocking Follow-up，禁止臆断）
- [x] P-REQ-22 验收标准逐字满足：目录页存在 + 覆盖 connector/jdbc/debezium/file/batch + 逐项含语义标注
- [x] No new test required: 纯文档变更
- [x] link checker exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - CDC 生产化 cookbook（P-REQ-23）+ 迁移指南首版（P-REQ-24）

Status: completed
Targets: `docs-for-ai/03-modules/`（cookbook 页 + 迁移指南页，路径按 D1）

- Item Types: `Proof`

- [x] **Decision D3**：cookbook 第四节主题裁定——P-REQ-23 验收原文点名三类（snapshot→增量切换、offset 恢复、schema 演进边界），「四类各一节」的第四类本 plan 取「故障排查与全新重跑」（依据：ST-10 SeaTunnel CDC cookbook 体裁 + reset-state 工具已落地可写操作步骤）；执行时可依据 live 素材改选，改选记录于 daily log
- [x] cookbook 四节（第四节按 D3）：①首次启动 snapshot→增量切换 ②offset 恢复与重放（checkpoint 恢复路径、`"cdc-offsets"` key round-trip、`NopStreamOffsetBackingStore` 桥接、S1 `ReplayableCdcSourceFunction` 生产 offset 路径）③schema 演进边界（StateMigrationRegistry 能力面 + 不支持面显式声明）④故障排查与全新重跑（reset-state 入口 + 常见故障表）
- [x] cookbook ≥2 项关键声称与 debezium 模块 live 行为核对一致（如 offset round-trip、snapshot/initializeState 语义），核对锚点（测试名/类:行为）写入文中
- [x] 迁移指南首版：XDSL 轴（拓扑定义演进——Delta/x:extends 作为版本间拓扑迁移机制 + bean 引用兼容约束）+ 状态格式轴（key-group layout v2、序列化指纹现状、StateMigrationRegistry、reshard/reset-state 工具）+ 版本策略声明（无已发布版本 → 指南为前瞻性政策；首次格式版本递增时 P-REQ-21 revisit 联动，不承诺未落地机制）

Exit Criteria:

- [x] cookbook 四节齐备，每节含可执行操作步骤或命令（与 owner doc 运维手册不重复、互相引用不矛盾）
- [x] ≥2 项 live 行为核对锚点在文中可见且真实存在（执行时以测试运行或代码引用证实）
- [x] 迁移指南覆盖 XDSL 与状态格式两轴 + 版本策略；指南不描述任何未实现的版本机制（与 D-GAP/defer 记录一致）
- [x] No new test required: 纯文档变更
- [x] link checker exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 快速起步脚手架（P-REQ-25）

Status: completed
Targets: 脚手架载体（Phase 内 Decision 落定，候选：`nop-stream/` 内 maven archetype 模块或等价模板目录 + 生成脚本）；`docs-for-ai/INDEX.md`

- Item Types: `Decision | Proof`

- [x] **Decision D2**：脚手架形态裁定——(a) maven archetype（packaging=maven-archetype，`archetype:generate` 流）或 (b) 等价模板目录 + 复制/替换生成脚本；裁定依据（构建复杂度、可验证性、与 reactor 关系）与被拒方案理由记录于 daily log。裁定须对照 item 11 审计 §2.4 的脚手架可用性准则表（从零跑通/依赖配置透明/README 质量/**展示产品正门（DataStream 与 XDSL 两扇入口）与核心语义**——item 11 审计时最后一项全缺，脚手架拓扑不得只走 Java API 单门）并记录结论
- [x] 3 个入门示例拓扑落脚手架：①最小链路 source→transform→sink ②keyBy + 窗口聚合 + keyed state ③CEP 或 CDC 简单模式（与 fraud-example 高级场景分层，不复制；③的二选一选择记录于 daily log）。拓扑集与 item 11 审计 Gap A/B/C 缺口框架的关系（入门 tier 独立于已由 items 12/13 消化的高级场景资产）在 D2 裁定中一并记录
- [x] 脚本化验证：repo 内可复现的验证步骤（gated 测试或脚本 + 执行记录），证明「生成的工程在本地仓库就绪后 `mvn test` 通过且 3 拓扑测试全绿」；前置条件（如 `./mvnw install -pl nop-stream -am -DskipTests`）显式文档化
- [x] 快速起步章节落用户指南或脚手架自带 README，并从 INDEX 可路由到

Exit Criteria:

- [x] P-REQ-25 验收标准逐字满足：脚手架存在（archetype 或等价模板）+ 生成工程 `mvn test` 通过（脚本化验证 + 执行记录在档）+ 示例含 source→transform→sink 最小链路（共 3 拓扑）
- [x] **端到端验证**：从「用户获取脚手架 → 生成工程 → 运行测试 → 看到 3 个拓扑跑通」的完整路径已按脚本执行并留档（输出摘要记录于 daily log）
- [x] **无静默跳过**：脚手架生成脚本/验证脚本对失败步骤显式非零退出，无吞错
- [x] 若 D2 引入新 maven 模块：`./mvnw test -pl <模块> -am` 绿，且模块加入 nop-stream reactor 不破坏 `./mvnw test -pl nop-stream -am -T 1C`；若纯模板目录则明确记录「无新模块」
- [x] owner-doc 更新：`docs-for-ai/INDEX.md` 路由行 + 快速起步指引；若新增稳定入口类/脚本则补 source-anchors 锚点，否则记录「No anchor required: 纯资源模板」裁定
- [x] 新增脚本/生成逻辑有最小测试或脚本化自检（Test-Mandated Feature Rule；验证脚本本身即测试载体时记录裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - INDEX/source-anchors 全量同步与门禁收口

Status: completed
Targets: `docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md`、`docs-for-ai/03-modules/nop-stream.md`（模块路由表补文档族行，如需）

- Item Types: `Follow-up`

- [x] INDEX.md By Task/By Code Location 路由行覆盖新页族（用户指南/连接器目录/cookbook/迁移指南/快速起步各有可达路由）
- [x] source-anchors 补齐本 plan 新增的可引用锚点（如脚手架入口、文档族关键页不需要锚点则记录裁定）
- [x] 全量门禁：link checker + （若 Phase 4 引入代码）`./mvnw test -pl nop-stream -am -T 1C` + hollow/invariants 工具按仓库惯例复跑

Exit Criteria:

- [x] 从 `docs-for-ai/INDEX.md` 出发可在 ≤2 跳内到达本 plan 全部新文档页与脚手架入口
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module <affected> --severity high` exit 0（如引入代码）
- [x] No new test required: 纯文档同步 Phase（测试/工具门禁条目属 Phase 4 交付物的复验）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本 plan 以文档交付为主 + 一个脚手架载体（可能含代码）。

- [x] P-REQ-22/23/24/25 验收标准逐条满足（repo-observable，逐条对照 P-REQ 报告 §2.2 原文）
- [x] P-REQ-16 文档化交付落地（触发语义映射表存在且内容可溯源到 item 7 证据表）
- [x] D-DRIFT-1 全部位置修正完成（D-GAP §1.4 清单逐项核销）
- [x] 所有文档级声称与 live repo 一致（抽查无「描述不存在的行为」条目；不确定语义按 Phase 2 规则显式标注而非臆断）
- [x] INDEX/source-anchors 同步完成，link checker exit 0
- [x] （如引入代码）`./mvnw test -pl nop-stream -am -T 1C` 全绿 + hollow scan exit 0
- [x] 不存在被静默降级的 in-scope 项（未完成项要么完成、要么移入 Deferred But Adjudicated 并写明理由）
- [x] 独立子 agent closure-audit 已完成并记录证据（含文档-代码一致性抽查）

## Deferred But Adjudicated

（起草时无；执行中出现的候选项按 Anti-Slacking Rule 落状态后登记于此）

执行中出现的非本 plan 范围事项处置记录：

- **wiring-registry 9 处 stale pin（既有缺陷，就地修复）**：item 16 指标提交移动 StreamTaskInvokable/GraphModelCheckpointExecutor 行号未 re-pin 注册表，致 `check-nop-stream-invariants.mjs` exit 1（git stash 对照确认与本 plan 无关）。按工具指引机械 re-pin（9 调用点逐一 grep 核实仍存在，语义零变更）→ exit 0。Classification: 一次性同步修复（非 defer）。
- **「产品级 main-scope 启动入口」（D1b 选项 b 的 Follow-up 候选）**：Classification: out-of-scope improvement。Why Not Blocking Closure: 用户指南已如实标注 test-scope 现状与 test-jar 消费方式，不影响本文档交付物成立；Successor Required: no（候选登记，供后续 roadmap 裁定）。

## Non-Blocking Follow-ups

- 连接器目录中如出现「语义待证」标注（Phase 2 规则），逐条记录于此并路由（候选归属 item 19/20 或独立 Follow-up），不臆断语义充数。（执行结果：无「语义待证」条目——message source/sink 语义为后端承载的如实标注（非不确定），每行均有 live 代码/测试锚点；无需路由）

## Closure

Status Note: 文档产品化全量交付——P-REQ-22..25 验收逐条满足（连接器目录 10 组件全表 / CDC cookbook 四节含 live 锚点 / 迁移指南两轴+版本政策 / 脚手架 3 拓扑端到端 mvn test 全绿留档）、P-REQ-16 触发语义映射表落地、D-DRIFT-1 九位置核销、INDEX/source-anchors 同步、全工具门禁绿；唯一代码载体（脚手架）为 reactor 外模板目录，reactor 测试全绿不受影响。既有缺陷（wiring registry stale pin）已就地修复而非 defer。
Completed: 2026-09-03

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，research-only）task `ses_f9b7c0d2dffeD4F4S8PTH3aGRq`
- Audit Session: ses_f9b7c0d2dffeD4F4S8PTH3aGRq
- Evidence:
  - 每条 Exit Criterion 验证结果：**全 PASS**——Phase 1（用户指南 5 章节齐备；触发语义表与 live 类 100% 一致、12 个测试锚点全部存在；接线矩阵 8 行与 live `KeyedStreamImpl:172,177` 精确一致；CheckpointConfig 字段/默认值逐项匹配；D-DRIFT-1 残留 6 命中全带退役注记、零未注记；D1b test-scope 如实标注）；Phase 2（10/10 组件全表；抽查 `NopStreamErrors.java:439` 错误码 / `DebeziumCdcSourceFunction.java:62,199` REPLAYABLE+cdc-offsets / `BatchConsumerSinkFunction.java:151` IDEMPOTENT 全命中；message 语义后端承载如实标注）；Phase 3（四节齐备；锚点 `testSnapshotRestoreRoundTrip:127`/`testCdcCheckpointKillRecoverNoDuplicates:231`/`TestStateMigrationEndToEnd` 4 方法/`TestStreamStateResetTool` 3 方法全部存在；迁移指南显式声明 manifest 级 stateFormatVersion 未落地）；Phase 4（3 拓扑形态正确；reactor 10 模块不含 quickstart；`_tmp/quickstart-verify` surefire 报告 3 测试 0F/0E 执行证据在档；脚本 `set -euo pipefail` 无静默跳过）；Phase 5（INDEX 5 路由行 ≤1 跳；STRM-046 在档；三工具门禁 exit 0）
  - 每条 Closure Gate 验证结果：PASS（依据上述 + Deferred 空、Non-Blocking Follow-up 无待证条目、daily log 五 Phase 全记录）
  - Anti-Hollow 检查（文档-代码一致性抽查）：5/5 PASS + 追加 7 项 cited test 与 state key 全部存在，无「描述不存在的行为」条目
  - 工具退出码：`check-doc-links --strict` exit 0（2691 文件 0 error）；`scan-hollow --module nop-stream` exit 0；`scan-hollow --module nop-stream/quickstart` exit 0；`check-nop-stream-invariants` exit 0（9 处 stale pin re-pin 后）；`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS
  - Deferred 项分类检查：无 in-scope live defect 被降级（wiring registry 为就地修复；main-scope 入口为如实标注后的 out-of-scope improvement 候选）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码：见下（本 Closure 写入后执行）

Follow-up:

- 「产品级 main-scope 启动入口」候选（D1b 选项 b 附带登记，见 Deferred But Adjudicated）
- closure audit Note（非缺陷）：plan Current Baseline 的「11 类」口径（10 文件+Triggerable）与 live 具体类计数（8 trigger+3 evictor+Triggerable=12 具体）表述差异——用户指南表本身穷尽且正确，无用户面影响
- 除上述外 no remaining plan-owned work

