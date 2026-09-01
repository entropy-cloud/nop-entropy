# 1 nop-stream 整体设计产品化 gap 分析（roadmap item 6）

> Plan Status: completed
> Last Reviewed: 2026-09-01
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 6（Phase D，critical path，M1 已解锁，unlocks items 7—11 / M2）；P-REQ 清单 `ai-dev/analysis/2026-09/2026-09-01-competitor-productization-synthesis-and-p-req.md`
> Related: `2026-09-01-0753-3-competitor-synthesis-p-req-list.md`（P-REQ 产出 plan）、`2026-09-01-0938-2-core-module-audit.md` / `2026-09-01-0938-3-runtime-module-audit.md`（本 plan 下游，执行顺序在本 plan 之后）
> Mission: nop-stream-productization
> Work Item: roadmap item 6

## Purpose

对照 P-REQ 清单（重点 P-REQ-13..21 共 9 条裁定型输入）审视 nop-stream 整体设计（16 份设计文档 + live repo），产出 **D-GAP 清单**：每条含 go/defer/exclude 三态裁定 + 依据 + live 证据锚点 + 归属执行项；并对 README 已声明未实现的三项（K8s/YARN 部署编排、HPA、RuntimeTopology）完成正式裁定。D-GAP 是 Phase M 审计重点、Phase S 场景约束、item 16 裁剪的正式输入源（roadmap 自进化入口之二）。

## Current Baseline

（2026-09-01 live 核对）

- M1 done（items 1—5 全部 done + closure audit PASS）：P-REQ-1..28 清单已落地于 `ai-dev/analysis/2026-09/2026-09-01-competitor-productization-synthesis-and-p-req.md`（7 竞品 × 7 维度矩阵 + 28 条 P-REQ，P0×5 / P1×19 / P2×4；归属 item 16×12 / item 6×9 / item 17×4 / item 11×2 / Follow-up×1）
- 本 plan 直接输入 P-REQ-13..21（D-GAP 裁定输入组）：dry-run 连通性验证（13）、凭据加密与配置校验（14）、**K8s/YARN/HPA 三态裁定（15，P0 决策必答）**、触发器语义核对（16，P2）、可插拔异常处理策略（17，P1）、Standby 热备裁定（18，P1，决策型）、Interactive Query 一致性边界裁定（19，P2，决策型）、checkpoint 版本化与校验和（20，P1）、跨版本升级兼容测试基建（21，P1）
- P-REQ 报告的现状抽查结论（2026-09-01）：13/15/18/19/21 未满足；14/16/17/20 部分满足（14：XDef/XDSL 校验链路与 nop-credential 平台侧已有但未接入 stream 作业配置；16：窗口级 Trigger 家族存在；17：平台错误码 fail-fast 存在；20：`ICheckpointStorage` 双实现存在但版本化/校验和**未核验**）——四条部分满足项均需本 plan 补 live 证据后再裁定
- README 声明（`nop-stream/README.md:7`）：LOCAL/DISTRIBUTED 双模式已实现（含独立进程入口与多 JVM 测试）；**尚未实现**：K8s/YARN 集群部署编排、HPA 弹性伸缩；`RuntimeTopology` 类处于概念阶段（0 Java 引用）
- 设计文档体系：`ai-dev/design/nop-stream/` 共 16 份（README 索引 + 00-vision + 01-architecture-baseline + 11 份专题设计 + comparison + component-roadmap）；`00-vision.md` 含显式 non-goals（SQL/Table API、双流 join 等）——**裁定不得与之冲突**；冲突时只能产出 stop-edit-restart 建议
- stop-edit-restart 建议两件已记录待消化（P-REQ 报告 §3.1）：F-2（item 16 语义追加「告警渠道闭环」，P-REQ-12 载体）、F-3（item 15 吸收 P-REQ-21 执行面）
- 前序 plans deferred 检查：0753-1/0753-2 的 deferred 项均已收口；0753-3 的 deferred（Flink/Beam 产品化维度按需补评）归属 Phase M 审计（items 7—11），非本 plan scope

## Goals

- D-GAP 报告落地 `ai-dev/analysis/2026-09/2026-09-{DD}-nop-stream-design-productization-gap-analysis.md`：P-REQ-13..21 逐条三态裁定（go/defer/exclude），每条含依据、live 证据锚点（代码/文档路径）、go 项归属执行项（items 7—17 既有项或新 Follow-up 工作项）
- README 三项声明正式裁定：K8s/YARN 部署编排、HPA、RuntimeTopology 各一条三态裁定 + 依据（P-REQ-15 必答；RuntimeTopology 裁定须显式处置「0 引用概念类」）
- 产出 Phase M 审计重点输入：items 7—11 逐模块的重点清单（或显式「无额外重点」结论）
- 产出 Phase S 场景约束输入：item 12 场景设计须遵守的裁定边界 + S3 可选场景是否由 D-GAP 派生的裁定
- 产出 item 16 裁剪输入：P-REQ-1..12 中建议交付/defer/exclude 的初步裁剪清单（正式裁定属 item 16 自身 plan）
- 消化 F-2/F-3：各给出显式结论——「升级为 stop-edit-restart 触发建议（写入 D-GAP 报告并提请建议人执行）」或「维持建议级（理由）」；注意 Follow-up 追加无法承载 item 16/15 语义变更（roadmap Rules），F-2 的采纳路径只能是 stop-edit-restart 素材升级

## Non-Goals

- 修改 nop-stream 产品代码（纯分析/裁定 plan，无代码变更）
- 实施任何 go 裁定项（dry-run 入口、异常策略、checkpoint 版本化等属归属执行项）
- 模块级代码审计（items 7—11）
- 裁决 P-REQ-28 / 连接器生态（Follow-up item 19；tis 报告 Open Question 2「Delta 作为市场替代机制」按 P-REQ 报告 §3.2 裁定留给 item 19）
- 直接修改 roadmap 既有 items 语义/顺序（修正只能以 Follow-up 追加或 stop-edit-restart 素材记录，遵循 roadmap Rules）

## Scope

### In Scope

- 新增：D-GAP 报告（单文件，章节 = 输入证据核对 / 逐条裁定 / 下游输入）
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（仅两类：裁定产生新 Follow-up 工作项时按 Rules 追加到 Work Items 末尾 + Last updated；item 6 状态写回由 closure 后动作完成，写回内容**必须含 D-GAP 报告路径**——下游 items 7—11/12/16 plans 依赖该路径定位本报告）
- 只读：live repo 抽查（nop-stream 各模块、README、设计文档）、P-REQ 报告与三份竞品 primary 报告、`~/sources` 竞品源码（P-REQ 来源证据复核，如 SeaTunnel Helm chart、KS standby/IQ 源码）

### Out Of Scope

- `ai-dev/design/nop-stream/` 既有设计文档的结构性重写（若裁定发现设计文档与 live 行为 drift，记录为裁定依据与 Follow-up 建议，不顺手重写）
- `docs-for-ai/` 变更（item 17 scope）

## Execution Plan

### Phase 1 - 输入证据核对与 live 基线固化

Status: completed
Targets: `ai-dev/analysis/2026-09/`（报告输入证据章节）、live repo 只读抽查

- Item Types: `Proof`

- [x] 按 `ai-dev/analysis/00-analysis-writing-guide.md` 创建报告骨架（metadata 头 + 规范章节），后续 Phase 章节在该骨架内追加
- [x] 逐条复核 P-REQ-13..21 的「现状」结论：对四条部分满足项（14/16/17/20）补 live 证据（源码路径锚点，P-REQ-20 重点核验版本化/校验和 absence）；已核验项逐条做锚点复核（每条至少一个源码/文档锚点与 P-REQ 报告 §2.6 抽查记录交叉核对——§2.6 无对应抽查记录的条目改做 absence/存在性抽查；发现漂移即修正并记录）
- [x] 核对 README 三项声明的 live 事实：K8s/YARN/HPA 相关代码与配置的 absence 证据、`RuntimeTopology` 0 引用事实复核、既有集群发现/选主（nop-cluster discovery + JdbcLeaderElector/SysDaoLeaderElector）与「应用层 vs 容器编排层」分层边界的事实基线
- [x] 对照 16 份设计文档：提取与 P-REQ-13..21 相关的设计承诺/约束/non-goals 逐条索引（含 `00-vision.md` non-goals、`checkpoint-design.md` 协议边界、`connector-design.md` SourceWorkUnit 协议），标记设计文档与 live 行为的任何 drift

Exit Criteria:

- [x] 报告骨架已按 writing guide 创建（metadata 头齐全）
- [x] 报告输入证据章节含 P-REQ-13..21 逐条 live 证据表（每条有代码/文档路径锚点或显式 absence 证据）
- [x] 报告含 README 三项声明的 live 事实核对记录（含 RuntimeTopology 0 引用复核命令与结果）
- [x] 报告含设计文档承诺索引表（逐条标注与 live 行为一致 / drift）
- [x] No owner-doc update required（纯 analysis 产出；设计文档 drift 仅记录，不改设计文档）
- [x] No new test required: 纯证据核验章节，无代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - D-GAP 逐条裁定

Status: completed
Targets: `ai-dev/analysis/2026-09/`（报告裁定章节）

- Item Types: `Decision`

- [x] P-REQ-13..21 逐条裁定 go/defer/exclude：每条含裁定值、依据（Phase 1 证据 + 竞品来源依据）、go 项归属（items 7—17 既有工作项或建议新 Follow-up）、defer 项含 defer 条件与 revisit 触发点
- [x] P-REQ-15 三项子裁定：K8s/YARN 部署编排、HPA、RuntimeTopology 各自三态裁定 + 依据（消费 tis 08-14d「应用层集群管理已完备、仅缺容器编排层」分层结论与 SeaTunnel Helm chart 参照，见 P-REQ-15 来源）
- [x] 裁定一致性检查：全部裁定与 `00-vision.md` non-goals 及 16 份设计文档承诺无冲突；冲突项只能输出 stop-edit-restart 建议（记录素材，不改 roadmap 既有 items）
- [x] F-2/F-3 消化裁定：各输出「升级为 stop-edit-restart 触发建议（写入报告 + 提请建议人执行）」或「维持建议级（理由）」的显式结论；F-2 结论即 P-REQ-12 归属处置的唯一记录点（Phase 2 其余条目不再单独重复裁定 P-REQ-12 归属）
- [x] 若任何 P-REQ-1..11 条目经整体设计视角需要归属修正，输出裁剪建议（正式裁定留给归属 item 自身 plan / stop-edit-restart 素材；与 F-2 重叠的条目引用 F-2 结论，不重复记录）；无修正项时显式记录「经扫描无归属修正建议」

Exit Criteria:

- [x] 报告裁定章节含 9 条 P-REQ × 三态裁定表 + 3 条 README 声明子裁定，无空缺单元格
- [x] 每条 go 裁定有明确归属执行项；每条 defer 有 defer 条件与 revisit 触发点；每条 exclude 有竞品/设计依据引用
- [x] 裁定一致性检查结论落地（含与 non-goals 冲突项的处理输出，若无冲突则显式记录）
- [x] F-2/F-3 消化结论落地（两方向均显式，无「待定」）
- [x] No owner-doc update required（同 Phase 1）
- [x] No new test required: 纯裁定章节，无代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 下游输入输出与 roadmap 写回

Status: completed
Targets: `ai-dev/analysis/2026-09/`（报告下游输入章节）、`ai-dev/backlog/nop-stream-productization-roadmap.md`

- Item Types: `Decision | Proof | Follow-up`

- [x] 产出 items 7—11 逐模块审计重点清单（消费本 plan 裁定中 go/核对型条目；无额外重点的模块显式写「无额外重点，按既有审计模式执行」）
- [x] 产出 item 12 场景约束输入：场景设计须遵守的裁定边界清单 + S3 可选场景派生裁定（派生自 D-GAP 的具体条目，或显式「S3 不派生，维持 S1/S2」）
- [x] 产出 item 16 裁剪输入：P-REQ-1..12 初步裁剪建议表（交付建议/defer/exclude + 一句依据；正式裁定归 item 16 plan）
- [x] 裁定产生新 Follow-up 工作项时：按 roadmap Rules 追加到 Work Items 末尾（编号顺延 item 19 之后、状态 todo、来源标注本 plan）并更新头部 Last updated
- [x] 报告 Status 定稿（resolved 或 open+遗留问题清单）并遵循 `ai-dev/analysis/00-analysis-writing-guide.md` 章节规范

Exit Criteria:

- [x] 报告下游输入章节含三张清单（Phase M 逐模块重点、Phase S 约束 + S3 裁定、item 16 裁剪建议），items 7—11 五个模块逐一覆盖
- [x] roadmap 若有变更：新 Follow-up 条目已追加且 Last updated 已更新；若无变更：报告中显式记录「无 roadmap 修正需要」
- [x] D-GAP 报告自包含：裁定表可独立阅读（无需回读 P-REQ 报告即可理解每条裁定的要求、现状与依据），供 items 7—11 审计 plans 与 item 12/16 plans 落地时直接引用
- [x] No owner-doc update required
- [x] No new test required: 纯裁定/清单产出，无代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本 plan 为纯文档/裁定计划（仅改 `ai-dev/` 下文件），`./mvnw` 构建/测试条目按 guide 纯文档计划规则省略。No new test required: 纯调研/裁定变更，无代码行为变更。

- [x] P-REQ-13..21 共 9 条 + README 声明 3 项全部有三态裁定且依据可追溯（live 证据锚点或竞品来源引用）
- [x] P-REQ-15（P0 决策必答项）三项子裁定完整，无空缺
- [x] Phase M（items 7—11）/ Phase S（item 12）/ item 16 三方下游输入齐备且自包含
- [x] 裁定与 `00-vision.md` non-goals 及设计文档承诺的一致性检查完成（冲突项有 stop-edit-restart 素材输出或显式无冲突记录）
- [x] F-2/F-3 消化结论显式落地
- [x] 不存在被静默降级的必答裁定项（P0/P1 决策型条目不得无裁定留空）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] roadmap item 6 状态写回（closure audit 通过后）

## Deferred But Adjudicated

（无——本 plan 为裁定型 plan，所有 in-scope 裁定项必须在 Closure 前落到三态之一，不允许 deferred 逃逸；defer 只能作为某条 P-REQ 的裁定值本身，且须附 defer 条件）

## Non-Blocking Follow-ups

（执行中按需追加：裁定过程中发现但不属于 P-REQ-13..21 / README 三项的优化类观察项，逐条附 Why Not Blocking）

## Closure

Status Note: 纯裁定型 plan 三 Phase 全部完成：D-GAP 报告（P-REQ-13..21 九条三态裁定 + README 三项子裁定 + 一致性检查 + F-2/F-3 消化 + Phase M/S/16 三方下游输入）落库且 Status resolved；Follow-up item 20 已按 roadmap Rules 追加；无 in-scope 裁定项留空，无 live defect/contract drift 被降级（两项设计 drift 均有处置路径）。
Completed: 2026-09-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，task `ses_fa535e4eeffehOuSbEvfm8PM1A`）
- Audit Session: `ses_fa535e4eeffehOuSbEvfm8PM1A`
- Evidence:
  - Phase 1—3 Exit Criteria 逐条 PASS（metadata 头齐全；§1.1 九行证据表含锚点/显式 absence；§1.2 RuntimeTopology 零引用命令审计者重跑 exit 1 确认；§1.4 逐文档 一致/drift 标注；§2.1 无空缺单元格、go 均有归属、defer 均有条件+revisit、exclude 均有依据；§2.2 四子裁定齐；§2.3 显式一致性结论；§2.4 F-2/F-3 均显式无待定；§3.1 五模块全覆盖（item 9 显式无额外重点）；§3.2 六约束 + S3 显式裁定；§3.3 恰 12 行；roadmap item 20 追加 + Last updated 同步）
  - 证据锚点抽查 8/8 PASS（EpochManifest 无 checksum/stateFormatVersion；StateSegmentDescriptor :49-50 双字段；LocalFileCheckpointStorage `.tmp`:53 + ATOMIC_MOVE :100/:403/:411/:500；JobCoordinator STANDBY :163/:436-469 + G24/G25 五处 :565/:768/:822/:883/:971；triggers 目录 10 类 + Triggerable；stream.xdef 存在；ExceptionHandler 族零命中；SeaTunnel Chart.yaml 与 KS StandbyTask.java 存在）+ 对抗性复核（README:7 原文、checkpoint-design :201/:203 精确、16 份设计文档计数、dry-run/IQ/upgrade/yaml absence 重跑零命中、SerializerFingerprint :36 恒 1、§1.2「18 文件全 hPa 假阳性」逐文件核实）
  - Closure Gates 实质项 PASS（HPA 排除引用的 non-goal 在 00-vision.md §四 :51 实存；P-REQ-15/18/20/21 无未裁定；无静默降级——Deferred But Adjudicated 按设计为空，D-DRIFT-1/2 均有处置路径）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（2663 文件 / 30618 引用 / 0 错误）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（见下方验证记录）
  - Anti-Hollow 不适用（纯文档/裁定 plan，无代码变更、无新组件接线；结论语义由审计者锚点级复核代替）
  - 审计发现 3 Minor 均已处置（Triggerable 路径补全修正于报告 §1.1；其余两项即本 closure 动作本身）
- 复核命令记录（closure 时执行）：`node ai-dev/tools/check-doc-links.mjs --strict` → exit 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-productization/2026-09-01-0938-1-design-productization-gap-analysis.md --strict` → exit 0

Follow-up:

- no remaining plan-owned work（裁定型 plan 全量落地；F-2 stop-edit-restart 建议与 D-DRIFT-1/2 设计文档修正建议为提请/移交项，归属 mission owner / items 17/8，见报告 §2.4/§3.4；Follow-up item 20 已入 roadmap 调度）
