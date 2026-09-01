# 2 竞品源码获取与产品化分析：SeaTunnel / Spark Structured Streaming / Kafka Streams（roadmap items 2—4）

> Plan Status: completed
> Last Reviewed: 2026-09-01
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` items 2/3/4（Phase R）；评估矩阵输入依赖 `2026-09-01-0753-1-research-asset-inventory-and-evaluation-framework.md`
> Related: 前置 plan `2026-09-01-0753-1`（item 1）；后续 plan `2026-09-01-0753-3`（item 5 汇总本 plan 产出的 P-REQ 候选）
> Mission: nop-stream-productization
> Work Item: roadmap items 2, 3, 4

## Purpose

获取 SeaTunnel、Spark Structured Streaming、Kafka Streams 三个竞品源码（shallow clone 到 `~/sources`），按 plan 1 产出的产品化评估矩阵逐竞品产出产品化分析报告与 P-REQ 候选条目，补齐 M1 里程碑的证据输入。

## Current Baseline

（2026-09-01 live 核对）

- `~/sources/seatunnel`、`~/sources/spark`、`~/sources/kafka` 均不存在（`ls ~/sources` 核实，51 个现有项目中无这三个）
- 既有 `ai-dev/analysis/2026-05-19a-seatunnel-vs-nop-stream-comparison.md`（Status: open）已覆盖 SeaTunnel 架构/功能对比（模块结构、Source/Sink/Transform 抽象等）——item 2 的新报告聚焦产品化维度，架构对比内容引用不重做
- 无任何 Spark Structured Streaming / Kafka Streams 相关分析报告
- **前置依赖**：plan 1（item 1）已完成，产出可用的评估维度矩阵与 per-竞品必答/选答维度清单。本 plan 在 plan 1 closure 前不得进入执行
- nop-stream 侧已有能力（分析对照的基础事实）：见 roadmap Framework / platform reuse 表与 Current baseline 节（FLIP-27 source、CDC、2PC sink、RocksDB、HA/failover、XDSL 编排等）
- clone 需要网络访问；若 clone 失败视为 blocker 上报（不允许以文档/官网资料替代源码分析充当 item 2—4 交付物）

## Goals

- `~/sources` 新增三个竞品源码（`--depth 1` shallow clone；spark 允许仅 streaming 相关子集的 sparse/partial clone，见 Phase 2 Decision）
- 三份产品化分析报告（每竞品一份，落 `ai-dev/analysis/2026-09/`），按评估矩阵逐维度评分并附源码证据；每份报告**记录所分析竞品源码的 commit SHA**（`git -C ~/sources/<repo> rev-parse HEAD` 输出），保证证据链可复现（shallow clone 无法事后 checkout 其他 commit，且上游 HEAD 每日漂移）
- 每份报告产出编号 P-REQ 候选条目（前缀区分：`ST-`/`SPS-`/`KS-`），每条含要求陈述、**可判定的**验收标准（repo-observable 或 process-observable，禁用「完善」「更好」类模糊词）、源码证据指针（竞品仓库内文件路径，锚定报告记录的 SHA）、建议归属工作项（roadmap item 6—18 编号之一，或 `Follow-up`）——供 item 5 汇编为最终 P-REQ 清单

> Bundling 裁定记录：roadmap Stages 表写「per-item plan」，本 plan 将同构的 items 2/3/4（三竞品 clone→矩阵分析→P-REQ 候选，零相互依赖、同一前置 plan 1、同一消费方 plan 3）合并为一个 plan 的三个 Phase。单一结果面 = 「M1 的 items 2—4 证据输入齐备」。部分完成语义：若单一竞品 clone 持续失败（网络/磁盘），已完成 Phase 的交付物保留有效，本 plan 保持 `in progress` 并按 blocker 上报，roadmap items 2/3/4 不写回；不得为凑 closure 静默降级未完成竞品。

## Non-Goals

- nop-stream 侧任何代码/文档改动（P-REQ 候选只进 analysis 报告）
- 其他竞品（flink/beam/tis 已有证据）
- item 5 的综合对比与最终 P-REQ 编号
- 竞品的非流处理部分深挖（spark 非 streaming 模块、kafka broker/storage）

## Scope

### In Scope

- `~/sources/seatunnel|spark|kafka` 三个竞品源码 clone（`--depth 1` shallow 为主；spark 允许 partial clone，见 Phase 2 Decision；写操作仅限这三个新目录）
- `ai-dev/analysis/2026-09/` 三份新报告
- `_tmp/` 下的 clone 过程临时产物（如需）

### Out Of Scope

- `~/sources` 既有 51 个项目的任何变更
- nop-stream 模块、`docs-for-ai/`、`ai-dev/design/` 变更
- P-REQ 最终汇编（item 5）

## Execution Plan

### Phase 1 - SeaTunnel 源码获取与产品化分析（item 2）

Status: completed
Targets: `~/sources/seatunnel`、`ai-dev/analysis/2026-09/2026-09-{DD}-seatunnel-productization-analysis.md`

- Item Types: `Proof | Decision`

- [x] `git clone --depth 1 https://github.com/apache/seatunnel ~/sources/seatunnel`
- [x] 记录所分析源码的 commit SHA（报告 metadata 一节）
- [x] 按 plan 1 矩阵逐维度分析（必答：连接器生态组织方式、CDC 产品化、多引擎适配层 Source/Sink API 抽象、部署形态本地/集群/K8s、监控与运维、配置 DSL 与向导）
- [x] 架构层结论引用 `2026-05-19a-seatunnel-vs-nop-stream-comparison.md`，不重做；两报告结论冲突时显式记录
- [x] 产出 `ST-n` 编号 P-REQ 候选条目（含验收标准 + 源码证据路径 + 建议归属工作项）
- [x] 报告遵循 analysis guide（metadata + 必需章节），Status: resolved（后续工作指向 item 5）

Exit Criteria:

- [x] `~/sources/seatunnel` 存在且为有效 git 仓库（`git -C ~/sources/seatunnel rev-parse HEAD` 成功）
- [x] 报告存在且 7 个评估维度全部有评分（每维度附置信度标注，按 plan 1 分级）+ 源码证据引用（引用落到竞品仓库内具体文件路径），并记录 commit SHA
- [x] plan 1 校准结论中该竞品的**必答维度逐项有结论**（以 plan 1 校准结论为准且必须 ⊇ roadmap item 2 列出的 6 项，逐一可对应到报告章节/条目）
- [x] P-REQ 候选章节存在，条目均有 `ST-` 编号、要求陈述、可判定验收标准、证据指针、建议归属（item 6—18 编号或 Follow-up）
- [x] 报告显式引用 2026-05-19a 报告并声明架构对比不重做
- [x] No owner-doc update required（纯 analysis 产出）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Spark Structured Streaming 源码获取与产品化分析（item 3）

Status: completed
Targets: `~/sources/spark`、`ai-dev/analysis/2026-09/2026-09-{DD}-spark-structured-streaming-productization-analysis.md`

- Item Types: `Proof | Decision`

- [x] Decision: clone 策略裁定——完整 `--depth 1` vs partial clone（`--filter=blob:none` + sparse-checkout 限 streaming 相关子模块），以磁盘占用与可分析性为准，裁定结论写入报告附录（roadmap item 3 允许「仅 streaming 相关子集」）。sparse 候选目录起始集：`sql/core`（streaming 执行主力）、`sql/api`、`sql/catalyst`、`core`（调度）、`streaming`（旧 DStream 仅参照）、`docs/`（文档维度证据）、`external/`（连接器生态证据）；7 个评估维度的证据路径（含 AQE 跨 `sql/core` 执行与 `core` 调度）所需目录必须在裁定中逐项确认覆盖，避免过度剪枝丢失证据路径
- [x] 执行选定策略 clone 到 `~/sources/spark`，记录 commit SHA
- [x] 按 plan 1 矩阵逐维度分析（必答：micro-batch vs continuous 双模式取舍、adaptive query execution、状态存储与 checkpoint 产品化、Structured Streaming API 设计、运维/监控集成）
- [x] 产出 `SPS-n` 编号 P-REQ 候选条目
- [x] 报告遵循 analysis guide，Status: resolved

Exit Criteria:

- [x] `~/sources/spark` 存在且为有效 git 仓库，streaming 相关子模块（`sql/core` streaming 源码等）内容可读
- [x] clone 策略 Decision 已记录（含理由 + 必答维度目录覆盖确认）
- [x] 报告存在且 7 维度全部有评分（每维度附置信度标注，按 plan 1 分级）+ 源码证据引用，并记录 commit SHA
- [x] plan 1 校准结论中该竞品的必答维度逐项有结论（以 plan 1 校准结论为准且必须 ⊇ roadmap item 3 列出的 5 项，逐一可对应到报告章节/条目）
- [x] P-REQ 候选章节存在，条目均有 `SPS-` 编号、要求陈述、可判定验收标准、证据指针、建议归属（item 6—18 编号或 Follow-up）
- [x] No owner-doc update required（纯 analysis 产出）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Kafka Streams 源码获取与产品化分析（item 4）

Status: completed
Targets: `~/sources/kafka`、`ai-dev/analysis/2026-09/2026-09-{DD}-kafka-streams-productization-analysis.md`

- Item Types: `Proof | Decision`

- [x] `git clone --depth 1 https://github.com/apache/kafka ~/sources/kafka`（分析范围以 `streams/` 子模块为主），记录 commit SHA
- [x] 按 plan 1 矩阵逐维度分析（必答：库形态 vs 引擎形态的运维差异、事务性 exactly-once 集成、状态存储 RocksDB 内嵌、interactive query、liveness/健康暴露）
- [x] 产出 `KS-n` 编号 P-REQ 候选条目
- [x] 报告遵循 analysis guide，Status: resolved

Exit Criteria:

- [x] `~/sources/kafka` 存在且为有效 git 仓库，`streams/` 子模块内容可读
- [x] 报告存在且 7 维度全部有评分（每维度附置信度标注，按 plan 1 分级）+ 源码证据引用，并记录 commit SHA
- [x] plan 1 校准结论中该竞品的必答维度逐项有结论（以 plan 1 校准结论为准且必须 ⊇ roadmap item 4 列出的 5 项，逐一可对应到报告章节/条目）
- [x] P-REQ 候选章节存在，条目均有 `KS-` 编号、要求陈述、可判定验收标准、证据指针、建议归属（item 6—18 编号或 Follow-up）
- [x] No owner-doc update required（纯 analysis 产出）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 纯调研计划（写 `~/sources` 新目录 + `ai-dev/analysis/`），`./mvnw` 构建测试条目按 guide 纯文档计划规则省略。No new test required: 无代码变更。

- [x] 三个竞品 clone 均存在且有效（三个 `git rev-parse HEAD` 均成功）
- [x] 三份报告均存在、均按 7 维度矩阵评分、均有编号 P-REQ 候选章节、均记录 commit SHA
- [x] 每份报告的 P-REQ 候选可被 item 5 直接汇编（编号无冲突、字段齐全、验收标准可判定）
- [x] 每竞品必答维度（roadmap items 2/3/4 stage details 所列）逐项有结论
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 前置，guide Minimum Rule #26）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] roadmap items 2/3/4 状态写回（closure audit 通过后）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 2026-05-19a seatunnel 报告的 Status 收敛（resolved + Superseded By 指向新产品化报告）随 Phase 1 一并处理则更优；若未处理，归属 item 5 汇编时收敛。Why Not Blocking: analysis 为决策参考，open 状态不阻塞 M1

## Closure

Status Note: 三 Phase 全部完成：三个竞品 shallow clone 落地 `~/sources`（seatunnel@5dbfb374、spark@992b0905、kafka@7434a60c，SHA 均落报告 metadata）+ 三份产品化分析报告落 `ai-dev/analysis/2026-09/`（各 7 维度分数@置信度评分 + 源码证据 + 必答维度映射 + 28 条 P-REQ 候选：ST-1..10 / SPS-1..9 / KS-1..9，字段齐全可直接供 item 5 汇编）。独立 closure audit 全项 AUDIT PASS（含 ~50 处证据路径 spot-check），审计发现 1 Major（kafka 报告升级测试模块计数 34→26、`-010`→`-0110`）+ 4 Minor（78 e2e / 28 AQE 文件 / 22 metrics 项 / 12 IQ Query 类型计数修正）已全部修复并复核。剩余工作显式归属 item 5（plan 2026-09-01-0753-3），无 plan-owned 遗留。
Completed: 2026-09-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，task id `ses_fa5820c40ffe6B6eG4QfQkkC0j`）
- Evidence:
  - Clones & SHA 链：三个 `git rev-parse HEAD` 与三报告 metadata SHA 逐一匹配 PASS（5dbfb374/992b0905/7434a60c，体积 109M/315M/116M 与报告声明一致）
  - Phase 1 七条 Exit Criteria：PASS（7 维度评分表↔正文一致；必答 6 项映射核对 roadmap item 2 deliverables 6/6；ST-1..10 字段齐全无模糊词；05-19a 引用 + 2 条差异记录 + 无冲突声明在位）
  - Phase 2 七条 Exit Criteria：PASS（附录 A 完整 clone 裁定含 7 维度目录覆盖表；必答 5 项映射 5/5；SPS-1..9 齐全）
  - Phase 3 六条 Exit Criteria：PASS（必答 5 项映射 5/5；KS-1..9 齐全；CONN 1@high 附 by-design 语境标注）
  - 证据 spot-check ~50 处全部核实（抽样含 `RestConstant.java` 65 端点、`MicroBatchExecution.scala:651-659` AQE 禁用（SPARK-53941）、`Triggers.scala:57-125` 五态、`KafkaStreams.java:264-272` 七态、`StreamsConfig.java:421` exactly_once_v2、CDC 9 方言 + debezium 1.9.8.Final、Helm chart、MetricsServlet 双格式、74 连接器/316 文档/10 changelog 变体精确计数等）；无捏造结构
  - 跨报告 P-REQ 编号：OK（ST/SPS/KS 前缀无冲突无重复）
  - Non-Goals：PASS（git status 仅 plan/日志/三报告变更，nop-stream/docs-for-ai/ai-dev/design 零改动）
  - 审计发现与修复：Major F1（kafka 升级测试模块 34→26、`upgrade-system-tests-010` 不存在→`-0110`）+ Minor F2—F5（seatunnel 79→78 e2e、spark 30→28 AQE 文件、23→22 metrics 项、19→12 IQ Query 类型）——已全部修正于三报告与 09-01 日志，修复后 grep 复核零残留
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（auditor 实跑 0 errors；修复后由执行者复跑确认）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（本节填写后复跑确认）
  - Anti-Hollow 检查：不适用（纯调研计划，无代码变更、无组件连线）；Deferred 项分类检查：无 in-scope live defect 被降级
- Final verdict: **AUDIT PASS**（全项，附 F1 修复条件已满足）

Follow-up:

- no remaining plan-owned work（28 条 P-REQ 候选已显式移交 item 5 plan `2026-09-01-0753-3`；05-19a Status 收敛归属 item 5，见 Non-Blocking Follow-ups）
