# 1 调研资产盘点与研究框架（roadmap item 1）

> Plan Status: completed
> Last Reviewed: 2026-09-01
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 1（Phase R，critical path，unlocks items 2—4）；前序 mission 遗留 data-quality debt（`ai-dev/backlog/nop-stream-production-roadmap.md` 记录的 8 条 stale gap-analysis 行）
> Related: `2026-09-01-0753-2-competitor-source-productization-analysis.md`（其 scope 由本 plan 产出校准）、`2026-09-01-0753-3-competitor-synthesis-p-req-list.md`
> Mission: nop-stream-productization
> Work Item: roadmap item 1

## Purpose

为 Phase R 竞品调研建立可复用的地基：盘点已有调研资产（`~/sources` 源码 + `ai-dev/analysis/` 报告）、定义产品化评估维度矩阵（含评分标准）、识别未覆盖的竞品与分析维度（缺口清单，校准 items 2—4 scope）；同时收口前序 mission 记录的 8 条 stale gap-analysis 行。

## Current Baseline

（2026-09-01 live 核对）

- `~/sources` 已有 51 个参考项目，含 `flink`、`beam`、`tis`；**无** `seatunnel`、`spark`、`kafka`（`ls ~/sources` 核实）
- `ai-dev/analysis/` 下与 nop-stream 相关的已有报告（**必须全递归扫描**：`ai-dev/analysis/` 下所有子目录，含 `nop-stream/`、`{year}-{month}/` 月份子目录、`*-survey/` 专题子目录及其嵌套目录；收录规则：Scope 涉及 nop-stream 或其直接子系统的报告均计入，混合 scope（如 tis 报告同时覆盖 nop-batch/nop-job）计入并标注混合。2026-09-01 已核实至少 24 份）：
  - 顶层 12 份：`2026-04-02-nop-stream-design-review.md`、`2026-04-02-nop-stream-review.md`、`2026-05-19a-seatunnel-vs-nop-stream-comparison.md`（Status: open，架构/功能对比视角，非产品化视角）、`2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-05-22-test-coverage-comparison-flink.md`、`2026-05-22b-nop-stream-vs-flink-streaming-test-comparison.md`、`2026-05-23-nop-stream-beam-hazelcast-comparison.md`、`2026-06-14-nop-stream-barrier-checkpoint-comparison.md`、`2026-06-30-nop-stream-code-audit.md`、`nop-stream-flink-comparison-deep-dive.md`、`checkpoint-module-extraction.md`、`distributed-exactly-once-design-amendment.md`
  - 子目录 `nop-stream/` 8 份（`01-flink-source-audit.md` … `08-gap-analysis.md`）
  - 月份子目录 3 份：`ai-dev/analysis/2026-07/2026-07-20-nop-stream-dataflow-api-gap-analysis.md`（DataStream API vs Flink，API/DX 维度直接证据）、`ai-dev/analysis/2026-08/2026-08-06-nop-stream-audit-baseline-and-roadmap-analysis.md`（全 10 模块审计基线）、`ai-dev/analysis/2026-08/2026-08-14d-tis-vs-nop-data-integration-comparison.md`（tis 源码级对比，含连接器生态/部署/监控维度的采纳建议，Status: open）
  - 专题子目录 1 份：`ai-dev/analysis/metadata-survey/2026-07-15-griffin-vs-nop-stream-comparison.md`（Griffin 流批架构对比，Status: open）
- 尚无「产品化评估维度矩阵」：roadmap item 1 列出的 7 个维度（API/DX、连接器生态、部署形态、运维监控、容错语义、性能、文档）没有评分标准与操作化定义
- 尚无覆盖 Spark Structured Streaming / Kafka Streams 的任何分析报告
- **前序 mission re-triggered deferred（confirmed doc drift）**：`ai-dev/analysis/nop-stream/08-gap-analysis.md` 中 8 条行（G6/G9/G24/G25/G32/G45/G62/G64）状态滞后——其所属 stage 在前序 roadmap 中已 done，但 gap-analysis 行状态仍停留在 `deferred (Phase X)` / `可选项` / 空值（前序 roadmap Follow-up Backlog 显式记录为 pre-existing data-quality debt，「可在未来 gap-analysis 同步计划中统一清理」）
- 新 analysis 报告须落在 `ai-dev/analysis/2026-09/`（按 `ai-dev/analysis/00-analysis-writing-guide.md` File Naming Rule）

## Goals

- 产出一份调研资产盘点报告：`~/sources` 51 个项目按与 nop-stream 产品化的相关性分级（直接竞品/相邻参考/无关），已有分析报告逐份索引（路径、Scope、Status、覆盖维度）
- 产出产品化评估维度矩阵：7 维度 × 每维度操作化定义 + 评分标准（含分级锚点），可直接作为 items 2—4 报告与 item 5 综合矩阵的统一评分工具
- 产出缺口清单：未下载竞品 × 未覆盖维度的显式列表，并据此给出 items 2—4 的 scope 校准结论（每竞品必答维度 vs 选答维度）
- 收口 8 条 stale gap-analysis 行：逐条对照 live repo 证据同步为终态（Closed/Excluded/permanently deferred + 证据指针）

## Non-Goals

- 下载任何新竞品源码（items 2—4 所有）
- 撰写竞品对比结论或 P-REQ 条目（item 5 所有）
- 修改 nop-stream 产品代码或 `docs-for-ai/`（本 plan 无代码变更）
- 重写 08-gap-analysis 的正文结构（只同步 8 条行状态 + 必要的汇总计数）

## Scope

### In Scope

- 新增：`ai-dev/analysis/2026-09/2026-09-{DD}-research-asset-inventory-and-evaluation-framework.md`（盘点 + 矩阵 + 缺口清单，单文件三章节）
- 修改：`ai-dev/analysis/nop-stream/08-gap-analysis.md`（仅 8 条 stale 行的状态同步与汇总计数修正）
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（仅当 scope 校准结论要求追加 Follow-up 工作项时，按 Rules 追加到 Work Items 末尾并更新 Last updated）
- 只读盘点：`~/sources`（禁止写操作）

### Out Of Scope

- `~/sources` 任何写操作；items 2—4 的 clone
- 竞品分析报告本体
- gap-analysis 正文重写、其他行的改动

## Execution Plan

### Phase 1 - 调研资产盘点

Status: completed
Targets: `ai-dev/analysis/2026-09/`（报告 Phase 1 章节）

- Item Types: `Proof`

- [x] 全递归盘点 `ai-dev/analysis/`（所有层级子目录，含 `nop-stream/`、`{year}-{month}/`、`*-survey/` 及嵌套目录）全部 nop-stream 相关报告（收录规则见 Current Baseline）：路径、Scope、Status、已覆盖的竞品 × 维度；已知至少 24 份（见 Current Baseline），盘点结果以递归扫描为准而非以本 plan 列举为准
- [x] 盘点 `~/sources` 全部项目（当前 51 个，以执行时 live 计数为准）：名称、一句话定位、与 nop-stream 产品化的相关性分级、是否已被既有报告引用。分级锚点：`direct-competitor`（流/批流数据处理或数据集成引擎：flink、beam、tis 等）、`adjacent-reference`（调度/任务/连接器/CDC 等共享子问题的项目：PowerJob、open-cdm、data-integration 等）、`unrelated`（与流处理产品化无直接关系：erp、mall、react UI 等）；边界案例在报告中显式标注理由
- [x] 盘点结论写入报告（含上述两张表）

Exit Criteria:

- [x] 报告含 `~/sources` 盘点表，全部项目逐一列出且每项有相关性分级（边界案例有理由标注）
- [x] 报告含既有报告索引表，全递归扫描所得全部 nop-stream 相关报告（≥24 份，含月份子目录 3 份与专题子目录 1 份）逐一列出且每份有 Status 与覆盖维度标注（混合 scope 报告有标注）
- [x] 索引表中对 `ai-dev/analysis/2026-05-19a-seatunnel-vs-nop-stream-comparison.md` 显式标注「架构/功能视角，产品化维度未覆盖」；对 `ai-dev/analysis/2026-08/2026-08-14d-tis-vs-nop-data-integration-comparison.md` 显式标注「tis 产品化证据已存在」
- [x] No owner-doc update required（仅产出 `ai-dev/analysis/` 报告，不改 `docs-for-ai/` / `ai-dev/design/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 产品化评估维度矩阵与缺口清单

Status: completed
Targets: `ai-dev/analysis/2026-09/`（报告 Phase 2 章节）

- Item Types: `Decision | Proof`

- [x] 定义 7 维度（API/DX、连接器生态、部署形态、运维监控、容错语义、性能、文档）的操作化定义：每维度 3—6 个可观察检查点（例：运维监控 → metrics 暴露面 / 健康检查 / 告警钩子 / 运维操作 CLI-API）
- [x] 定义统一评分标准：0—3 分级锚点（0=缺失 / 1=最小可用 / 2=产品级 / 3=竞品标杆），每锚点一句判定语；同时定义证据置信度分级（high=源码级证据 / medium=报告间接推断 / low=文档或假设推断），供 items 2—5 统一标注评分的置信度
- [x] 用矩阵对既有证据（flink deep-dive、beam-hazelcast、05-19a seatunnel、2026-08-14d tis）做一次 dry-run 试评，验证检查点可操作（试评结果标注 confidence，正式评分属 items 2—5）
- [x] 产出缺口清单：竞品 × 维度的未覆盖单元格列表（seatunnel/spark/kafka 未下载；产品化维度全面未覆盖；tis 已有 `2026-08-14d` 部分证据，其余维度如实标注）
- [x] 产出 items 2—4 scope 校准结论：每竞品必答维度（来自 roadmap stage details）+ 选答维度

Exit Criteria:

- [x] 报告含评估矩阵章节：7 维度均有操作化定义、评分锚点与置信度分级定义
- [x] 报告含 dry-run 试评表（≥4 个既有证据源被试评，含 tis），无「无法评分」的维度级放弃
- [x] 报告含缺口清单章节：未覆盖竞品 × 维度单元格显式列出
- [x] 报告含 scope 校准结论：items 2—4 每竞品的必答/选答维度清单
- [x] No owner-doc update required（矩阵与清单只进 analysis 报告）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - gap-analysis stale 行收口

Status: completed
Targets: `ai-dev/analysis/nop-stream/08-gap-analysis.md`

- Item Types: `Fix`

- [x] 逐条核对 G6/G9/G24/G25/G32/G45/G62/G64：对照 live repo（代码路径 + 前序 roadmap 对应 stage 的完成证据）确定每条真实终态
- [x] 将 8 条行同步为终态（✅ Closed / permanently deferred / adjudicated），每条附证据指针（plan 路径或代码路径）
- [x] 同步正文汇总处的滞后引用（2026-09-01 已知位置，以执行时 live 行号为准）：Conclusion 总数（`:6` 「87 total active gaps」、`:12` 「87 条有效缺口」及去重算式）、Executive Summary 优先级计数表（`:16—23` 一带）、P1/P2/P3 小节标题计数（`:63`、`:92`、`:128`，其中 `:92` 的「31 total: 16 closed, 15 open」与 `:206` 的「43 个 P2」相对 `:20` 是**本 plan 之前已存在的自相矛盾 drift，本次一并裁定修正**）、P1/P2 叙述计数（`:196`、`:206`）、deferred 要点列表（`:203` 一带，仍列 G6/G24/G25 为 deferred）、Plan Mapping 表 deferred 行（`:227` 一带，仍列 G42/G43/G66/G67 等已 Closed 项）
- [x] 若任何一条经 live 核对发现并非已完成（stage 声称 done 但 live 证据缺失），不得改写状态，转为 roadmap Follow-up 工作项追加并注明来源（核对结论：8 条全部有 live 证据，无需追加）

Exit Criteria:

- [x] 8 条目标 ID（G6/G9/G24/G25/G32/G45/G62/G64）在表格行与正文汇总处均无滞后状态标记残留：`rg -n 'deferred \(Phase|Item 12b|可选项' ai-dev/analysis/nop-stream/08-gap-analysis.md` 的命中仅为终态语境（如「曾 deferred，已 Closed」），8 行均有终态标注 + 证据指针
- [x] 上述枚举的全部汇总计数位置（Conclusion 总数、Exec Summary 表、P1/P2/P3 小节标题、P1/P2 叙述、deferred 要点、Plan Mapping 表）逐处与行状态一致，`:92`/`:206` vs `:20` 的既有计数矛盾已裁定修正（修正值有逐行统计支撑）
- [x] 若产生 Follow-up 工作项：roadmap Work Items 末尾已追加（编号顺延、状态 todo、标注来源本 plan）（不适用：无未完成项，逐条核对均有 live 证据）
- [x] No owner-doc update required（08-gap-analysis 属 `ai-dev/analysis/`，非 owner doc）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本 plan 为纯文档/调研计划（仅改 `ai-dev/` 下文件），`./mvnw` 构建/测试条目按 guide 纯文档计划规则省略。No new test required: 纯调研/文档变更，无代码行为变更。

- [x] 盘点报告存在于 `ai-dev/analysis/2026-09/`，遵循 `ai-dev/analysis/00-analysis-writing-guide.md`（metadata + 必需章节），Status 标注 resolved（结论被 items 2—5 接手）或 open（若遗留 open questions）
- [x] 评估矩阵可被第三方直接使用（items 2—4 计划执行者无需再定义评分标准与置信度分级）
- [x] 8 条 stale gap-analysis 行全部收口且正文汇总处无滞后引用残留
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 前置，guide Minimum Rule #26）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] roadmap item 1 状态写回（closure audit 通过后）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- `2026-05-19a-seatunnel-vs-nop-stream-comparison.md` 的 Status: open 收敛（其结论被 item 2 产品化报告引用后可标 resolved）— 归属 item 2 执行 plan 裁定，Why Not Blocking: analysis 为决策参考非 source of truth，open 状态不阻塞任何 roadmap item

## Closure

Status Note: 纯文档/调研计划三 Phase 全部完成：盘点报告（51 项目分级 + 27 份报告索引 + 边界裁定）与评估框架（7 维度检查点 + 0—3 锚点 + 三级置信度 + 4 源 dry-run + 缺口清单 + items 2—4 scope 校准）落地 `ai-dev/analysis/2026-09/`；08-gap-analysis 8 条 stale 行收口且汇总计数逐行修正；独立 closure audit 全项 PASS。剩余工作显式归属 items 2—5 plans，无 plan-owned 遗留。
Completed: 2026-09-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，task id `ses_fa5a304eaffeQX11bixtA5omOv`）
- Evidence:
  - 报告存在性与格式（metadata/Context/Analysis/Conclusion/References，Status: resolved）：PASS
  - Phase 1 三条 Exit Criteria：PASS（51/51 项目逐一核对 1:1；索引 27 份含月份 3 + 专题 1，5 份混合 scope 标注；05-19a「架构/功能视角，产品化维度未覆盖」与 2026-08-14d「tis 产品化证据已存在」标注均在位）
  - Phase 2 四条 Exit Criteria：PASS（7 维度检查点 6/5/5/5/5/5/5 均在 3—6；锚点与置信度定义完整；dry-run 28 单元格全部 score@confidence、含 tis、无放弃维度；缺口清单 + scope 校准在位）
  - Phase 3 四条 Exit Criteria：PASS（8 行终态 + 证据指针，spot-check 5 项 live 证据全部成立：`RedistributionMode.java:12-14`、`JdbcLeaderElector.java`、`RocksDBIncrementalSnapshotStrategy.java`、`state-management-design.md:54` §2.4、5 份 plan 文件存在；`rg` 14 处命中全部终态语境；独立行数统计 P0=3/P1=24/P2=31/P3=10 与 37 closed/31 active/5 Doc 一致；Plan Mapping 12b→0 active、Deferred 行仅 G5/G34/G37-G39）
  - Logs：PASS（09-01.md 两条执行记录）
  - Follow-up 诚实性：PASS（仅 05-19a 收敛项，归属 item 2）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（2655 files, 0 errors）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（本节填写后复跑确认）
  - Anti-Hollow 检查：不适用（纯文档计划，无代码变更、无组件连线）；Deferred 项分类检查：无 in-scope live defect 被降级
- Final verdict: **AUDIT PASS**（全项）

Follow-up:

- no remaining plan-owned work（05-19a Status 收敛已显式移交 item 2 plan，见 Non-Blocking Follow-ups）
