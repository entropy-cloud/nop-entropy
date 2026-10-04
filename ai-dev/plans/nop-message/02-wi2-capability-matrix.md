# 02 WI2 SPI 字段乘后端能力矩阵

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI2，deps WI1）、`ai-dev/analysis/2026-10/nop-message-audit-baseline.md`
> Related: WI3（D1-D11 裁定以本矩阵为输入）、WI6（校验器按矩阵落格）
> Note: roadmap WI2 状态行原文写「D1 至 D10」，系 v3 新增 D11 前的遗留表述（roadmap 冻结不改工作项定义）；本 plan 按 D1-D11 执行

## Purpose

产出 `ai-dev/analysis/2026-10/nop-message-capability-matrix.md`：13 个 `IMessageService` 命名实现 × SPI 全部选项字段/枚举值/返回值语义/信封元数据的处置矩阵。每格两行：as-is 现状（带 file:line 锚点）+ 提案目标态（Hard constraint 允许的三态之一：support / typed-reject / warn-once）。矩阵作为 WI3 的 D1 至 D11 裁定与 WI6 `MessageOptionsValidator` 的输入。本项不含代码变更。

## Current Baseline

- WI1 审计基线已落档（`ai-dev/analysis/2026-10/nop-message-audit-baseline.md`），59 个 `MSG-F-nn` 裁定完毕：57 缺陷 confirmed + F-58 watch-only + F-51 先例。
- 独立研究（含对全部 13 实现的源码核查）已完成：`MessageSubscribeOptions` 实为 **10 字段**（subscribeName/transactional/transactionTimeout/batchReceiveCount/batchReceiveTimeout/concurrency/subscriptionType/seekMode/seekToMessage/seekToTime），`MessageSendOptions` 3 字段；各实现的 as-is 处置已逐字段带锚点采集（如 SysDao 仅 subscribeName+delay 生效、DBMessageService 识别 ConsumeLater 但忽略 delay、Kafka seekMode 系 no-op 但代码形态为 support 等）。
- as-is 中大量格子的现状是「ignore」——按 roadmap Hard constraint 该形态不是合法终态，矩阵必须为每个 ignore 格提出三态提案。
- 当前 SPI 无能力声明机制（D1 待裁），本矩阵就是该裁定的逐后端落表底稿（roadmap D1：「该裁定须在 WI2 的矩阵中逐后端落表，不得留空」）。

## Goals

- 矩阵覆盖五个维度 × 13 实现：① `MessageSendOptions` 3 字段；② `MessageSubscribeOptions` 10 字段；③ `SeekMode` 4 枚举值；④ `SubscriptionType` 4 枚举值；⑤ `onMessage` 五种返回值语义（null / CompletionStage / ConsumeLater / Acknowledge / 其他非 null）；另加第六维：信封元数据（消息 id / topic / eventTime / key / headers 往返）。
- 每格：as-is 锚点 + 提案目标态（support / typed-reject / warn-once 三选一，ignore 不得出现在提案列）。
- 每个实现的提案附一句话语义定位（如「进程内广播总线」「DB 轮询队列」「装饰器透传」），并给出该实现建议声明的能力集草案（D1 输入）。
- test 实现（Mock / PollingJdbc）与 benchmark 桩（Delayed）单列并标注「test/bench 桩，不适用生产三态门禁」，豁免理由显式记录。

## Non-Goals

- 不落 D1-D10 的最终裁定（属 WI3，本矩阵只供输入；提案列是建议非裁定）。
- 不修改任何产品代码或 SPI。
- 不设计 `MessageOptionsValidator` 的实现（属 WI6）。
- 不覆盖 nop-message-debezium（非 `IMessageService` 实现，是 `IMessageSource` 数据源）。

## Scope

### In Scope

- `ai-dev/analysis/2026-10/nop-message-capability-matrix.md`（新建）。
- `ai-dev/logs/2026/10-04.md`（追加）。
- roadmap Work Item Status 块 WI2 状态行（planned→done）。

### Out Of Scope

- 全部代码、测试、构建脚本。
- roadmap 除 WI2 状态行外的任何内容。

## Execution Plan

### Phase 1 - 矩阵成文

Status: completed
Targets: `ai-dev/analysis/2026-10/nop-message-capability-matrix.md`

- Item Types: `Proof`

- [x] 按六维 × 13 实现写出矩阵：as-is 列（锚点来自 WI1 基线与本研究采集）+ 提案目标态列（三态，无 ignore）
- [x] SeekMode / SubscriptionType 两维按枚举值逐值展开（如 Pulsar Exclusive+concurrency>1 的硬失败格、Kafka 四值全部无原生映射）
- [x] 信封元数据维按 id/topic/eventTime/key/headers 逐项标注两后端 + SysDao 差异（F-48/F-31/F-33 的矩阵化）
- [x] 每实现一行语义定位 + 建议能力集草案（D1 输入）；test/bench 桩豁免显式记录
- [x] 与 WI1 基线交叉引用：矩阵中每条 as-is 缺陷格标注对应 MSG-F 编号，无编号的新发现登记附录
- [x] 13 个实现的「语义定位 + 建议能力集草案」逐个落表（D1「逐后端落表不得留空」）；TdMessageService 的空集（全 typed-reject）为合法值但须显式声明；test/bench 桩写明豁免

Exit Criteria:

- [x] 矩阵 6 维全覆盖，13 实现 × 全部格子无空格（n/a 仅限 test/bench 桩与不可达路径，且逐格注明原因）
- [x] 提案列无 ignore；每格三态之一且与该实现的语义定位自洽（复检分层抽样 ≥8 格：六维各至少 1 格且必含 LocalMessageService 行与一个 broker 行）
- [x] as-is 锚点分层抽查 ≥6 格与 live 代码一致（broker 两后端、Local/SysDao/DB 非 broker、test 桩各类至少 1 格）
- [x] 13 个实现能力集草案无一遗漏（空集/豁免均显式声明）
- [x] No owner-doc update required（analysis 交付物；owner doc 属 WI34）
- [x] `ai-dev/logs/2026/10-04.md` 追加条目
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 不新增断链

## Closure Gates

- [x] 矩阵文档已产出、六维全覆盖、提案列无 ignore
- [x] as-is 锚点独立 audit 抽查通过
- [x] 无产品代码变更（git 仅含本 plan 声明路径及本 plan 文件）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/02-wi2-capability-matrix.md --strict` 退出码 0
- [x] 纯文档计划：构建验证不适用

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 矩阵成文过程中若发现 roadmap 58 编号之外的新缺陷 → 附录登记，按 roadmap Follow-up 规则处理，不阻塞本 plan。

## Closure

Status Note: WI2 为纯 Proof 项，交付物（能力矩阵）已产出并经独立子代理 closure audit：13 实现全覆盖、六维无遗漏、提案列无 ignore、锚点抽查 6/6 + 附加 3 格全部命中、能力集草案无遗漏。audit 的 1 个 Major（维度 F 裸 n-a 无因）与 2 个可修 Minor（图例裸行号规则）已在关闭前修正；Minor-3（scope 措辞）按意图达成；Minor-4（Td 裸 UnsupportedOperationException）移交 WI3 裁定 typed-reject 错误形态时统一处理。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_dc13ccbd-be1e-4596-9183-c032ffde6e79（fresh session）
- Evidence:
  - 覆盖 PASS：13 实现与 grep 实测吻合（其余 grep 命中均为测试内部 fixture，不在口径内）
  - 提案列 ignore 检查 PASS：34 处 ignore 全部位于 as-is 侧
  - 锚点抽查 PASS：6/6（Kafka delay warn / Pulsar reconsumeLater / SysDao scheduleTime / DB ConsumeLater 回 PENDING / Local 不读 options / Kafka subscriptionType warn）+ 附加 3 格
  - 能力集草案 PASS：13 实现无遗漏，Td 空集与桩豁免显式
  - 无代码变更 PASS：git diff 确认仅 roadmap WI2 状态行 + plan 02 + 矩阵 + 日志
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（本 mission 文件 0 断链）
  - Anti-Hollow PASS：矩阵逐格有锚点与机制描述
  - Deferred 分类检查：无 deferred；2 条 Open Questions 留给 WI3 裁定，属裁定输入非缺陷降级
