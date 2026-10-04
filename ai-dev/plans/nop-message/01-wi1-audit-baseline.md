# 01 WI1 nop-message 四模块与 SPI 全量审计基线

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI1）、`missions/nop-message-encapsulation.json`
> Related: 后续 plan 均引用本项产出的审计基线

## Purpose

把 roadmap 中 59 个 `MSG-F-nn` 编号（58 条缺陷 + 1 条平台先例）对照 2026-10-04 live 代码逐条复核，产出可被后续所有工作项引用的审计基线文档 `ai-dev/analysis/2026-10/nop-message-audit-baseline.md`：每条 finding 给出三态裁定（confirmed / already-correct / watch-only），confirmed 逐条给与当日 live 代码一致的 file:line 锚点。本项不含任何代码变更。

## Current Baseline

- `ai-dev/backlog/nop-message-encapsulation-roadmap.md` 已含 59 个 `MSG-F-nn` 编号（MSG-F-01 至 MSG-F-59，其中 MSG-F-51 为平台先例非缺陷），证据列为 2026-10-01 实测的 file:line。
- SPI 位于 `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/message/`（19 个文件）+ 注解 `io/nop/api/core/annotations/message/`（2 个文件），`nop-message` 下有 core / kafka / pulsar / debezium 四个子模块（另有 model / codec 两个空壳模块，属 WI31 处置范围）。
- 实现清单共 13 个命名实现：src/main 9 个非后端实现（LocalMessageService、SysDaoMessageService、LettucePubSubService、MqttServerMessageService、TdMessageService、DBMessageService、DataPlaneMessageServiceAdapter、DelegateMessageService、RemoteTransportWriteBench 的 DelayedMessageService〔benchmark 模块 main〕）+ 2 个 broker 后端（Kafka / Pulsar）+ 2 个 src/test 实现（MockMessageService、PollingJdbcMessageService）。实测口径：`grep -rln "implements IMessageService" --include="*.java"` 在 src/main 命中 11 个文件（含两后端）、src/test 命中 23 个。注意：roadmap 硬约束所列「11 个主源码实现」清单字面构成为 8 main 非后端 + 2 test + 1 benchmark（不含两后端），与 src/main 的 11 是总数相同但成员不同的两个集合；审计文档按实际构成登记，不照抄等式。
- 2026-10-01 之后仓库有其他分支改动，roadmap 中的 file:line 锚点未对照 2026-10-04 live 代码逐条重验过。
- `ai-dev/analysis/2026-10/` 目录已存在，尚无 nop-message 审计基线文档。

## Goals

- 产出 `ai-dev/analysis/2026-10/nop-message-audit-baseline.md`，覆盖全部 59 个 `MSG-F-nn` 编号。
- 每条 finding 给出三态裁定：`confirmed`（缺陷在 live 代码中仍然成立）/ `already-correct`（roadmap 判定有误或已被修复）/ `watch-only`（缺陷形态存在但当前配置或调用路径下不可达，属潜在陷阱）。
- 每条 confirmed / watch-only 给出与 2026-10-04 live 代码一致的 file:line 锚点。
- 文档同时登记：SPI 类清单（`message/` 19 文件 + `annotations/message/` 2 注解）、四模块结构与行数、上述 13 个命名实现清单及其对选项字段的处置现状摘要，作为 WI2 能力矩阵的直接输入。
- 复核中新发现的缺陷（不在 59 个编号内）登记到文档附录，后续按 roadmap Follow-up 规则追加，不在本文档内私开工作项。

## Non-Goals

- 不修改任何产品代码、测试、构建脚本（WI1 为纯 Proof 项）。
- 不做修复方案设计（属 WI3 / 各 Phase B+ plan）。
- 不重写 roadmap 的任何既有工作项定义（roadmap 执行期冻结）。
- 不验证真实 broker 集群行为（属 WI33 gated E2E 范围）。

## Scope

### In Scope

- `nop-kernel/nop-api-core` 的 `io.nop.api.core.message` 包（只读核对）。
- `nop-message/nop-message-core`、`nop-message-kafka`、`nop-message-pulsar`、`nop-message-debezium`（只读核对）。
- `ai-dev/analysis/2026-10/nop-message-audit-baseline.md`（新建）。
- `ai-dev/logs/2026/10-04.md`（新建或追加）。
- `ai-dev/backlog/nop-message-encapsulation-roadmap.md` 的 Work Item Status 块（仅 WI1 状态行 `todo→planned→done`）。

### Out Of Scope

- 任何 `_gen/`、`_*.xml` 生成物。
- `nop-stream`、`nop-rpc-core`、`nop-graphql-message` 等消费方代码（仅作为回归面记录，不改）。
- roadmap 除状态行外的任何内容。

## Execution Plan

### Phase 1 - 锚点重验与三态裁定

Status: completed
Targets: `nop-kernel/nop-api-core`、`nop-message/*`（只读）

- Item Types: `Proof`

- [x] 对照 live 代码逐条重验 58 条缺陷 + 1 条先例的证据锚点（grep -n 定位，逐条记录行号）
- [x] 每条给出三态裁定与一句话依据；裁定与 roadmap 表述不一致的（如已修复、形态变化）单独标注。裁定表直接写入目标文档形成初稿（Phase 2 在其上成文收口，避免中间产物无处落）
- [x] 核对 13 个命名实现的存在性与所在模块，并与 `grep -rln "implements IMessageService"` 实测对照（src/main 11 含两后端、src/test 23）
- [x] 扫描 SPI 包与四模块是否有 roadmap 未覆盖的新缺陷形态（空方法体、吞异常、未读字段），新发现记入附录

Exit Criteria:

- [x] 全部 59 个 `MSG-F-nn` 编号在目标文档初稿中各有一行裁定结论，无遗漏编号
- [x] confirmed / watch-only 逐条带 file:line，抽查任意 5 条锚点与 live 代码一致
- [x] 13 个命名实现全部出现在 `grep -rln "implements IMessageService"` 的 src/main（11 文件）或 src/test（23 文件）命中结果中，模块归属无误
- [x] **无静默跳过**：不适用（纯 Proof 项，无新代码）
- [x] No owner-doc update required（本项不改 live baseline；analysis 文档自身即交付物）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 文档产出与 roadmap 状态收敛

Status: completed
Targets: `ai-dev/analysis/2026-10/nop-message-audit-baseline.md`

- Item Types: `Proof`

- [x] 按 analysis writing guide 写出文档（Status: resolved、Date、Scope、Conclusion）。文件名 `nop-message-audit-baseline.md` 按 roadmap WI1 硬性指定，豁免 analysis guide 的 YYYY-MM-DD 前缀规则（同目录 `coverage-baseline-*.md` 已有先例）
- [x] 文档结构：结论摘要 → P0/P1/P2 分级裁定表 → already-correct 事实表 → SPI 与实现清单 → 附录（新发现）
- [x] roadmap Work Item Status 中 WI1 状态行随本 plan 通过 draft review 先翻为 `planned`，closure audit 通过后翻为 `done`；状态行尾部括注保持单层非嵌套（BULLET_RE 陷阱）
- [x] `node --input-type=module -e "import {parseRoadmapMarkdown} from './tools/mission-driver/src/roadmap-check.mjs'; import {readFileSync} from 'node:fs'; const p=parseRoadmapMarkdown(readFileSync('ai-dev/backlog/nop-message-encapsulation-roadmap.md','utf8')).phases; console.log(p.filter(x=>!x.isMilestone).length, p.filter(x=>x.isMilestone).length)"` 输出 `35 6`
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 不新增本文件相关断链（存量断链基线见 roadmap Verification baseline）
- [x] 本 plan 独立 closure audit 完成并写入 Closure 段

Exit Criteria:

- [x] 审计文档通过 `check-doc-links.mjs` 不新增断链
- [x] roadmap 解析条目数 35 + 6 不变
- [x] `ai-dev/logs/2026/10-04.md` 记录本次产出与验证结果
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/01-wi1-audit-baseline.md --strict` 退出码 0

## Closure Gates

- [x] 审计文档已产出且覆盖 59/59 编号，三态裁定齐全
- [x] confirmed 锚点与 2026-10-04 live 代码一致（独立 audit 抽查通过）
- [x] 无任何产品代码变更（`git status` 仅含本 plan 声明的文档路径及本 plan 文件自身）
- [x] 不存在被静默降级的 in-scope live defect（新发现按附录登记并指向 roadmap Follow-up 规则）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/01-wi1-audit-baseline.md --strict` 退出码 0
- [x] 纯文档计划：构建验证不适用（无代码变更）；`node ai-dev/tools/check-doc-links.mjs --strict` 存量错误数不增加

## Deferred But Adjudicated

无。所有 in-scope 项均为 Proof 产出，无延期空间。

## Non-Blocking Follow-ups

- 附录中新登记的缺陷形态（若有）→ 按 roadmap Follow-up Backlog 规则取号，不阻塞本 plan。

## Closure

Status Note: WI1 为纯 Proof 项，交付物（审计基线文档）已产出并经独立子代理 closure audit：59/59 编号覆盖、11 个锚点独立抽查命中、13 实现清单与 grep 实测吻合、无产品代码变更。audit 发现的 1 个 Major（F-55 PulsarErrors 锚点方向写反）与 4 个 Minor（P1 计数、结论口径、F-58 接线行号、Phase 1 未勾项）已在关闭前全部修正。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_50f8dc39-5c6a-4bad-9b5c-d3256574e73a（fresh session，非实现会话）
- Evidence:
  - 覆盖完整性 PASS：audit 文档与 roadmap 的 MSG-F 编号集 diff 为空（59/59，F-01…F-59 连续）
  - 锚点抽查 PASS：11 个锚点（F-01/04/09/16/38/45/58 + NEW-01/02 + F-46 + F-55）grep -n 独立重验命中；F-55 行号错误已修正为 :18,:21,:24
  - 实现清单 PASS：`grep -rln "implements IMessageService"` src/main 11 文件 / src/test 23 文件，13 个命名实现全部定位且模块归属无误
  - 无代码变更 PASS：git diff 确认 mission 改动仅 roadmap 1 行 + 本 plan + analysis 文档 + 日志
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/01-wi1-audit-baseline.md --strict` 退出码 0
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（无错误，未新增断链）
  - `parseRoadmapMarkdown` 输出 35 6 不变
  - Anti-Hollow PASS：裁定表每行有锚点+机制描述，非空壳
  - Deferred 项分类检查：无 deferred；附录 2 条 watch-only（MSG-NEW-01/02）为新发现登记，非降级
