# 3 竞品综合对比与产品化要求清单 P-REQ（roadmap item 5）

> Plan Status: completed
> Last Reviewed: 2026-09-01
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 5（Phase R 收口，unlocks M1）
> Related: 前置 plans `2026-09-01-0753-1-research-asset-inventory-and-evaluation-framework.md`（item 1，评估矩阵）、`2026-09-01-0753-2-competitor-source-productization-analysis.md`（items 2—4，三份产品化报告 + `ST-`/`SPS-`/`KS-` P-REQ 候选）
> Mission: nop-stream-productization
> Work Item: roadmap item 5

## Purpose

汇总全部竞品证据（新增三份产品化报告 + 既有 Flink/Beam/Hazelcast 对比报告）产出综合对比矩阵与最终 **P-REQ 清单**（编号产品化要求：每条含验收标准 + 来源依据 + 建议归属工作项，映射到 Phase D/M/S items 6—18），并给出对 items 6—18 scope 的修正建议（roadmap 自进化入口之一）。

## Current Baseline

（2026-09-01 live 核对；本 plan 执行时以当时 live 状态为准）

- **前置依赖**：plans 1—2（items 1—4）已完成——`ai-dev/analysis/2026-09/` 下存在评估矩阵报告（含评分锚点与置信度分级）与三份竞品产品化报告（含 `ST-`/`SPS-`/`KS-` 编号候选条目 + commit SHA）。本 plan 在前置 plans closure 前不得进入执行
- 既有可引用证据：`ai-dev/analysis/nop-stream-flink-comparison-deep-dive.md`、`ai-dev/analysis/2026-05-23-nop-stream-beam-hazelcast-comparison.md`（resolved）、`ai-dev/analysis/2026-05-19a-seatunnel-vs-nop-stream-comparison.md`（open，架构层）、`ai-dev/analysis/nop-stream/` 的 01—08 系列（flink 源码审计与 73 缺口收口记录），以及月份子目录 3 份：`ai-dev/analysis/2026-07/2026-07-20-nop-stream-dataflow-api-gap-analysis.md`、`ai-dev/analysis/2026-08/2026-08-06-nop-stream-audit-baseline-and-roadmap-analysis.md`、`ai-dev/analysis/2026-08/2026-08-14d-tis-vs-nop-data-integration-comparison.md`
- tis：`~/sources/tis` 源码存在，且**已有专门对比报告** `ai-dev/analysis/2026-08/2026-08-14d-tis-vs-nop-data-integration-comparison.md`（Status: open，tis v5.1.0 源码级，含 6 项优先级采纳建议：连接器 SPI/插件市场、批流统一管道、SQL 数据流建模、K8s 编排、监控告警闭环、Pipeline AI Agent）——roadmap item 5 明确列出 7 竞品含 tis，tis 行为**必答项**（低置信度评分可接受，引用该报告）
- roadmap items 6—18 为 P-REQ 映射目标；item 5 交付物落 `ai-dev/analysis/`（roadmap stage details 明确）
- roadmap Rules：追加 Follow-up 工作项合法（末尾追加、编号顺延）；调整既有 items 语义/顺序须 stop-edit-restart，本 plan 只记录建议不直接改既有 items（含 tis 排除——若证据支持排除，只能作为 stop-edit-restart 建议记录，不得在本 plan 内裁决排除）

## Goals

- 综合对比报告：roadmap item 5 明确的 **7 竞品**（Flink / Beam / SeaTunnel / Spark SS / Kafka Streams / tis / Hazelcast）× 7 产品化维度的矩阵，每单元格有评分（按 plan 1 的 0—3 锚点）+ 置信度标注（按 plan 1 的 high/medium/low 分级）+ 证据来源引用
- **P-REQ 清单**：汇编三份报告的候选条目 + 既有报告补录，去重合并为最终编号条目（`P-REQ-n`），每条含：要求陈述、验收标准（repo-observable 或 process-observable）、来源依据（竞品证据 + 报告路径）、建议归属 roadmap 工作项（6—18）
- items 6—18 scope 修正建议：显式记录（新维度的缺口 → Follow-up 工作项建议；既有 item 语义调整 → 建议记录，遵循 stop-edit-restart）
- M1 里程碑证据齐备（items 1—5 交付物全部在库）

## Non-Goals

- nop-stream 侧代码/设计文档改动（D-GAP 属 item 6）
- 直接修改 roadmap 既有 items 6—18 的语义或顺序（只产出建议；追加 Follow-up 项除外）
- 新竞品分析（items 2—4 之外的）
- P-REQ 的实施排期（归属各 owning plan）

## Scope

### In Scope

- 新增：`ai-dev/analysis/2026-09/2026-09-{DD}-competitor-productization-synthesis-and-p-req.md`
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（仅 Follow-up 工作项追加 + Last updated，当修正建议成立时）
- 修改：`2026-05-19a-seatunnel-vs-nop-stream-comparison.md` 等 open 状态旧报告的 Status/Superseded By 收敛（若其结论被本报告吸收）

### Out Of Scope

- items 6—18 的任何执行
- 既有 roadmap items 语义修改
- 竞品源码重新分析（只消费 items 2—4 产出；对 `~/sources/flink|beam|tis` 既有源码的只读 spot-check 引用属允许，见 Phase 1）

## Execution Plan

### Phase 1 - 证据汇编与综合对比矩阵

Status: completed
Targets: `ai-dev/analysis/2026-09/`（报告 Phase 1 章节）

- Item Types: `Proof | Decision`

- [x] tis 行必答：以 `ai-dev/analysis/2026-08/2026-08-14d-tis-vs-nop-data-integration-comparison.md` 为 primary 证据评分（置信度按其源码级/推断级如实标注 medium/low）；若执行中发现排除 tis 才合理，只能作为 stop-edit-restart 建议**记录**（本 plan 无权裁决排除，roadmap item 5 明确列 tis）
- [x] 汇编 7 竞品 × 7 维度矩阵：新增三报告的评分为 primary（high confidence）；Flink/Beam/Hazelcast/tis 以既有报告 + plan 1 dry-run 结论补评，逐格标注 confidence；旧报告未覆盖的维度（预计集中在文档/运维监控/部署形态）用 `no-evidence` 显式标注而非猜测评分
- [x] 每单元格附证据来源（报告路径；新增三报告的单元格另附竞品源码路径 + SHA；F/B/H 单元格允许只读引用 `~/sources/flink|beam` 既有源码路径作 spot-check 佐证——引用不算「重新分析」，深挖才算）

Exit Criteria:

- [x] 矩阵覆盖 7 竞品 × 7 维度，无空单元格（每单元格要么有评分 + 置信度标注，要么显式标注 `no-evidence` + 原因，二者必居其一）
- [x] 有评分的单元格置信度标注引用 plan 1 矩阵的分级定义，不另造标准
- [x] 新增三报告（SeaTunnel/Spark/KS）的单元格附竞品源码路径 + SHA；Flink/Beam/Hazelcast/tis 单元格的证据引用可回溯（报告路径存在）
- [x] No owner-doc update required（矩阵只进 analysis 报告）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P-REQ 清单汇编

Status: completed
Targets: `ai-dev/analysis/2026-09/`（报告 Phase 2 章节）

- Item Types: `Decision | Proof`

- [x] 合并 `ST-`/`SPS-`/`KS-` 候选，去重（语义重叠条目合并，保留多来源引用）
- [x] 既有报告**补录**（source set 显式枚举，逐报告处置）：`ai-dev/analysis/nop-stream-flink-comparison-deep-dive.md`、`ai-dev/analysis/2026-05-23-nop-stream-beam-hazelcast-comparison.md`、`ai-dev/analysis/2026-05-19a-seatunnel-vs-nop-stream-comparison.md`、`ai-dev/analysis/2026-08/2026-08-14d-tis-vs-nop-data-integration-comparison.md`、`ai-dev/analysis/2026-07/2026-07-20-nop-stream-dataflow-api-gap-analysis.md`、`ai-dev/analysis/2026-08/2026-08-06-nop-stream-audit-baseline-and-roadmap-analysis.md`；每报告处置三选一：`adopted`（→ P-REQ 映射）/ `rejected`（附理由）/ `already-shipped`（现状对照佐证）。其中 **tis 报告的 6 项采纳建议须逐项映射**（→ P-REQ 或显式拒绝 + 理由），不允许报告级 adopted 概括吞掉子项
- [x] 补录排除理由记录：其余 nop-stream 相关报告（bar-checkpoint 对比、05-22 测试对比 ×2、`ai-dev/analysis/nop-stream/` 的 01—08 系列等）不入补录 source set 的理由（内部审计/已收口缺口记录/测试对比，由 items 7—11 与前序 roadmap 消费，非产品化要求来源）写入报告，使零丢失声明可审计
- [x] 为每条 P-REQ 标注：优先级建议（P0 阻塞产品化 / P1 应有 / P2 增强）、验收标准、来源依据、建议归属工作项（6—18 之一或 Follow-up）
- [x] 交叉核对：每条 P-REQ 的归属工作项在 roadmap 中存在且语义匹配；不匹配的进入 Phase 3 修正建议
- [x] nop-stream 现状对照：逐条标注 nop-stream 当前已满足 / 部分满足 / 未满足（依据 roadmap Current baseline 的 shipped 清单 + 必要时 live repo 抽查）

Exit Criteria:

- [x] P-REQ 清单章节存在，全部条目有唯一 `P-REQ-n` 编号、要求陈述、验收标准、来源依据、归属建议、优先级、现状对照
- [x] 候选条目零丢失：三份报告的每个 `ST-`/`SPS-`/`KS-` 条目要么被合并（保留映射）要么被显式拒绝（附理由）
- [x] 补录零丢失：6 份枚举 source 报告均有逐报告处置记录（adopted/rejected/already-shipped），tis 报告 6 项采纳建议逐项有映射
- [x] 每条验收标准可判定（repo-observable 或 process-observable，无「更好」「完善」类模糊词）
- [x] No owner-doc update required（P-REQ 清单只进 analysis 报告）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - scope 修正建议与 M1 收口

Status: completed
Targets: `ai-dev/analysis/2026-09/`（报告 Phase 3 章节）、`ai-dev/backlog/nop-stream-productization-roadmap.md`、旧报告 Status 收敛

- Item Types: `Follow-up | Decision`

- [x] 产出 items 6—18 scope 修正建议（新增缺口 → 建议 Follow-up 工作项文本；既有 item 语义调整 → 建议文本，不直接改）
- [x] 对成立的修正：按 roadmap Rules 追加 Follow-up 工作项到 Work Items 末尾（编号顺延、todo、标注来源本 plan），更新 roadmap Last updated
- [x] 收敛被吸收结论的旧 open 报告：`2026-05-19a` 必须收敛（Status → resolved/superseded + 指向）；`2026-08-14d` tis 报告吸收后裁定——若其 P-REQ 相关结论被本报告完全吸收则标 resolved/superseded + 指向，若仍有超出 item 5 scope 的未决内容（nop-batch/job/metadata 侧结论、Open Questions）则保持 open 并在报告中记录保持理由（不允许无裁定的默认保持）
- [x] M1 完备性自查：items 1—5 交付物逐项列出（路径 + 完成证据）

Exit Criteria:

- [x] 报告含修正建议章节；已成立的建议以 Follow-up 工作项落 roadmap（rg 可见新增条目）或明确记录「无需修正」
- [x] `2026-05-19a-seatunnel-vs-nop-stream-comparison.md` Status 不再是 open（resolved/superseded + 指向新报告）；`2026-08-14d` tis 报告有显式 Status 裁定（收敛或记录保持理由）
- [x] 报告含 M1 自查清单，items 1—5 交付物路径全部存在
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] No owner-doc update required（修正建议只进 analysis 报告与 roadmap Follow-up 项）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 纯调研/文档计划，`./mvnw` 构建测试条目按 guide 纯文档计划规则省略。No new test required: 无代码变更。

- [x] 综合报告存在且三章节齐备（矩阵 / P-REQ 清单 / 修正建议 + M1 自查）
- [x] P-REQ 清单可被 item 6 直接消费（D-GAP 分析以它为对照输入）
- [x] 候选条目零丢失（ST/SPS/KS 全量处置）且补录 source 报告零丢失（6 份逐报告处置）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 前置，guide Minimum Rule #26）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] roadmap item 5 状态写回（closure audit 通过后）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 既有 Flink/Beam 对比报告若存在产品化维度缺口，可在 Phase M 审计时按需补评——Why Not Blocking: 本 plan 矩阵已以 low-confidence 补评并标注，正式补评不阻塞 M1

## Closure

Status Note: 纯调研/文档计划（item 5 Phase R 收口）。三 Phase 全 completed：综合报告（7×7 矩阵 45 评分格 + 4 no-evidence 格、P-REQ-1..28 零丢失清单、修正建议 + M1 自查）落 `ai-dev/analysis/2026-09/`；Follow-up item 19 落 roadmap；05-19a 收敛 superseded、tis 报告显式裁定保持 open。M1 证据齐备（items 1—5 交付物全在库），item 6 可启动。
Completed: 2026-09-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，task `ses_fa56dadccffecEazHBGZCJCd2R`）
- Evidence:
  - 8/8 审计项全 PASS：①交付物三章节齐备（43,686 bytes）②矩阵 7×7 全覆盖、4 no-evidence 格均有编号理由（¹—⁴）③零丢失——28 候选 ID 逐一 grep 命中（ST-1..10/SPS-1..9/KS-1..9）、6 份 source 报告 6 行处置、tis 6 项逐项映射（无报告级概括）、P-REQ-1..28 唯一编号且每条含要求/验收/来源/优先级/现状④05-19a superseded + Superseded By 指向成立、tis 保持 open 有 §3.2 显式理由裁定⑤roadmap item 19（todo + 来源标注）与 Last updated 刷新⑥M1 五项交付物路径 `ls` 全存在⑦验收标准抽查 5 条（P-REQ-1/9/15/23/27）均可判定、无模糊词⑧三 Phase Status: completed 且 checklist 全 `[x]`
  - 审计发现 3 处非阻塞 prose 计数偏差（Conclusion「5 格 no-evidence」→4、「直接采纳 10 条」→9+1 合并、P1/P2 计数 20/3→19/4）——已全部修正于报告与日志（本轮）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（2659 files, 0 errors）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（见下方验证记录）
  - 纯文档计划：mvn/scan-hollow 按纯文档规则省略（无代码变更）；Anti-Hollow 不适用（无新增组件/代码路径）
  - Deferred 项分类检查：Non-Blocking Follow-ups 仅 1 项（Flink/Beam 产品化维度正式补评，Why Not Blocking 已注明）；无 in-scope live defect 降级
  - roadmap item 5 写回：closure audit PASS 后 `planned → done`（Work Items block）+ Last updated 同步

Follow-up:

- no remaining plan-owned work（F-2/F-3 为 stop-edit-restart 建议记录于报告 §3.1，归 mission 层裁决；Follow-up item 19 已落 roadmap 参与调度）
