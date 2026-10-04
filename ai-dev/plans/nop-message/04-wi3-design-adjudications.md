# 04 WI3 D1-D11 前置裁定落档

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI3，deps WI2+WI5；「前置裁定（Gate D1—D11）」表与 Rules 落档义务）
> Related: `ai-dev/plans/nop-message/02-wi2-capability-matrix.md`（矩阵输入）、`ai-dev/plans/nop-message/03-wi5-spring-comparison.md`（D5/D6/D11 不采纳理由）

## Purpose

把 D1 至 D11 十一条前置裁定按 roadmap Rules 落档到 `ai-dev/design/nop-message/` 下对应设计文档，每条含裁定结论、理由、被拒绝的替代方案、负责人与日期。落档后 Phase B 工作项（WI6 起）解除起草阻塞。本项不含代码变更。

## Current Baseline

- WI2 能力矩阵、WI5 Spring 对照已 done；roadmap 前置裁定表对 D1-D11 各有明确「建议」与完整论证，WI3 的职责是**裁定生效并落档**（design 文档为最终状态表述，不引用 analysis 过程文档）。
- roadmap Rules 落档义务：D1、D2、D4、D8 落 `ai-dev/design/nop-message/message-contract-design.md`；D3、D5、D6、D7、D9、D10 落 `ai-dev/design/nop-message/backend-semantics-design.md`。**D11 为 v3 新增，Rules 清单未列**——本 plan 裁定其落 `backend-semantics-design.md`（事务语义属后端行为域），并在文档中显式登记该归属解释。
- `ai-dev/design/nop-message/` 目录不存在，需新建（含 README.md，按 design guide 的子系统目录规范：AGE owner-doc 模式声明 + Vision/Architecture Baseline 层级说明）。
- design guide 硬约束：设计文档禁止引用 `ai-dev/analysis/` 与 `ai-dev/discussions/`；不得有 Proposed vs Current 对比章节；关键发现须作为文档自身章节呈现。

## Goals

- 新建 `ai-dev/design/nop-message/`：README.md（索引+层级声明）、`message-contract-design.md`（D1/D2/D4/D8）、`backend-semantics-design.md`（D3/D5/D6/D7/D9/D10/D11）。
- 十一条裁定全部按 roadmap 建议采纳（经矩阵与 Spring 对照复核无推翻事由），每条含：结论、结构化理由（自含，引用 live 锚点或机制事实）、被拒绝的替代方案及理由、负责人（本 mission 执行 agent）与日期（2026-10-04）。
- D1 的「逐后端能力声明表」与 D8 的「选项字段加性变更规则」以契约形式写入 message-contract-design.md（自含表格，不引用 WI2 文档路径）。
- D10 的立即删除范围（nop-message-model；nop-message-codec 降级 Follow-up）与 D11 的字段删除范围（transactional + transactionTimeout，plan-first 承载于 WI14）写成明确裁定边界。

## Non-Goals

- 不执行任何裁定对应的代码变更（属各 WI：D1→WI6、D2→WI6/WI24、D3→WI7/WI8、D4→WI27、D5→WI6/WI16/WI25、D6→WI16/WI21、D7→WI13、D8→WI24、D9→WI26、D10→WI31、D11→WI14）。
- 不在文档中写实现方案（类签名/方法体），只写契约与决策。
- 不新建独立 Vision 层文档（本两项文档即首批 Architecture Baseline 级内容；该偏离在 README.md 中对读者显式声明：Vision 层延至 WI34 owner doc 阶段补齐）。

## Scope

### In Scope

- `ai-dev/design/nop-message/README.md`、`ai-dev/design/nop-message/message-contract-design.md`、`ai-dev/design/nop-message/backend-semantics-design.md`（均新建）。
- `ai-dev/design/README.md`（既有 attractor index）：Subsystem Design 表追加 nop-message 行。
- `ai-dev/logs/2026/10-04.md`（追加）。
- roadmap Work Item Status 块 WI3 状态行（todo→planned→done）。

### Out Of Scope

- 全部代码、测试、构建脚本。
- roadmap 除 WI3 状态行外的任何内容。

## Execution Plan

### Phase 1 - 裁定落档

Status: completed
Targets: `ai-dev/design/nop-message/`

- Item Types: `Decision`

- [x] 写 `README.md`：AGE owner-doc 模式声明、两篇文档的层级归属（Architecture Baseline）、阅读顺序
- [x] 写 `message-contract-design.md`：D1（MessageCapability 声明 + 入口 validator 门禁 + 13 实现逐后端能力表）、D2（最小信封契约字段集 + 往返保真规则）、D4（中立枚举加性引入 + 映射 + 弃用）、D8（@DataBean 选项字段加性变更 + 弃用期 + 迁移说明）
- [x] 写 `backend-semantics-design.md`：D3（rebalance listener 逐次重解析 + 降级路径）、D5（不引入 AckMode + 终止拒绝/部分提交信号）、D6（三层 DLQ + 失败回调）、D7（groupId 前缀身份模型）、D9（nop.err.message.* 前缀迁移）、D10（删 nop-message-model；codec 降级 Follow-up）、D11（删 transactional/transactionTimeout 两字段）
- [x] 每条裁定标注负责人与日期；D11 与 D10 的 plan-first 承载项写明（WI14/WI31）
- [x] `ai-dev/design/README.md` 的 Subsystem Design 表登记 nop-message 子系统目录
- [x] 自含性检查：两篇文档不出现 `ai-dev/analysis/` 引用；关键依据（如 Kafka assignment 空集、Pulsar TopicName 校验）以文档自身文字表述

Exit Criteria:

- [x] 十一条裁定各在指定文档中出现且含结论/理由/拒绝方案/负责人/日期五要素
- [x] 两文档无 `ai-dev/analysis/`、`ai-dev/discussions/` 引用、无 Proposed vs Current 章节（design guide 约束）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 不新增断链
- [x] `ai-dev/logs/2026/10-04.md` 追加条目
- [x] `node --input-type=module -e "import {parseRoadmapMarkdown} from './tools/mission-driver/src/roadmap-check.mjs'; import {readFileSync} from 'node:fs'; const p=parseRoadmapMarkdown(readFileSync('ai-dev/backlog/nop-message-encapsulation-roadmap.md','utf8')).phases; console.log(p.filter(x=>!x.isMilestone).length, p.filter(x=>x.isMilestone).length)" 输出 `35 6`（roadmap Rules 硬性复跑要求）
- [x] No owner-doc update required（design 文档本身即产出；docs-for-ai 属 WI34）

## Closure Gates

- [x] 三文件已产出，11/11 裁定落档且五要素齐全
- [x] design guide 约束（引用禁令、无演进叙事）独立 audit 通过
- [x] 无产品代码变更（mission 范围口径）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/04-wi3-design-adjudications.md --strict` 退出码 0
- [x] 纯文档计划：构建验证不适用

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 若后续 WI 执行中发现裁定与实现约束冲突 → 修订裁定并记录新日期，不阻塞本 plan。

## Closure

Status Note: WI3 为 Decision 项，三文件（README + 两篇 design）已产出，11/11 裁定落档。独立子代理 closure audit 发现 1 个 Major（D9 缺「被拒绝的替代方案」条目）与 2 个 Minor（D5 条目软引用 analysis 文档、1 个 checkbox 状态陈旧），均已在关闭前修正；其余 10 条裁定五要素齐全、抽查 D3/D5/D7/D11 忠实于 roadmap 建议。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_25192fa7-c5fb-4979-a5d5-7a9193e2a34f（fresh session）
- Evidence:
  - 三文件存在且分层正确 PASS；D11 归属解释在文档头部显式登记
  - 五要素核对：11/11（D9 缺口已补：拒绝保留旧前缀 + 违反平台规范 + 无 i18n/properties 引用）
  - roadmap 一致性 PASS：D1(b)/D2(b)/D3(a)/D4(b)/D5(b')/D6(b)/D7(b)/D8(b)/D9(b)/D10(b)/D11(b)，抽查 4 条忠实
  - design guide 约束 PASS：grep 零命中 analysis/discussions 引用、无 Proposed vs Current、无实现代码块（D5 软引用已删）
  - design/README.md Subsystem Design 表已登记（:49）
  - 无产品代码变更 PASS（mission 范围口径）
  - `parseRoadmapMarkdown` 输出 35 6；`check-doc-links.mjs --strict` 全仓 0 断链
  - Deferred 分类检查：无 deferred
