# 11 WI11 Kafka 批量路径回退与提交边界

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI11，deps WI4✓；对应 MSG-F-06/F-07/F-08/F-58）
> Related: `ai-dev/plans/nop-message/06-wi6-capability-validator.md`（PartialCommit 已建立的边界语义）

## Purpose

修复 Kafka 批量消费路径的四个编号缺陷：F-06（批量失败无回退 seek → 整批被跳过）、F-07（无参 commitSync 越过未派发记录 → at-most-once 丢失）、F-08（批内 ConsumeLater 混合 → reply 重复 + 早期 ConsumeLater 永不重投）、F-58（跨分区越界提交陷阱）。

## Current Baseline

- `KafkaConsumeTask.batchConsume()`（WI6 后约 `:282-330`）：成功路径无参 `commitSync()`；`responses` 短于记录时尾部既不 ack 也被提交（F-07）；ConsumeLater 后循环继续、后续记录照常 reply（F-08 前半）；catch 仅 LOG+rollback+throw、无 seek（F-06，position 已前移整批被跳过）。
- PartialCommit 边界语义（WI6）已建立按分区收敛 commitSync(OffsetAndMetadata) 的先例，可复用同构逻辑。
- `KafkaConsumerConfig.enableAutoCommit` 默认 false（手动提交流），但允许 true——届时 Kafka 内部 auto-commit 按自身节奏提交任意 position，本任务的提交边界形同虚设（F-58 陷阱形态）。
- 测试设施：TestKafkaConsumeTask mock 设备 + WI6 边界测试先例。

## Goals

- **F-08（ConsumeLater 停批）**：批量循环遇 ConsumeLater → seek 回该记录 offset 并 **break**（后续记录不 dispatch、不 reply），与 PartialCommit 的「停止处理其后响应」语义一致。措辞裁定（审查 M1）：消除的是 break 点**之后**记录的无效 dispatch/reply 及其确定性重投；break 点之前已 reply 的记录在重投时仍会重复——at-least-once 本质残留，javadoc 显式声明。不变式：ConsumeLater ⇒ 全批不提交（保守但重投完整；审查 Minor-4）。
- **F-07（提交边界收敛）**：成功路径不再无参 commitSync；改为按分区收敛提交「已最终化记录」的 `offset+1` 上界（复用 WI6 写法）。**进程内跳过窗口闭环（审查 M2，采方案 a）**：收敛上界 < 本批尾的分区 seek 回首个未派发 offset（与 F-06 同构；全批完成时上界=position，seek 为 no-op 跳过）→ 未派发记录下轮 poll 立即重投，不留「重启才恢复」的无界窗口。
- **失败路径收敛提交语义（审查 Minor-5）**：成功路径与 PartialCommit 路径的 commitSync(Map) 在 try 内（传播 → 走 F-06 回退，安全）；F-08 break 路径不提交。
- **F-06（失败回退）**：`int progress` 提升至 try 之前、循环首行 `progress = i`（单线程 executor，无需原子包装）；catch 中对 `[progress..n-1]` 记录按分区取最小 offset seek（该分区全部落在已最终化前缀则不 seek，避免进程内重放已处理记录）；rollback 后 rethrow（既有）。responses 为 null（handler 返回 null）从「跳批」变为「重试」——与 Pulsar negativeAcknowledge(messages) 语义一致，javadoc 声明。
- **F-58（auto-commit 陷阱显式化）**：`doSubscribe` 检测 `enableAutoCommit=true` 时 warn-once（MessageOptionsValidator.warnOnce，键 `kafka|auto-commit`），声明手动提交流的边界在该配置下不被 Kafka 内部 auto-commit 尊重；配置本身不强制改写（用户显式选择受尊重）。
- 回归测试逐条覆盖四个编号（见测试清单）。

## Non-Goals

- 不改单条路径（pollAndConsume 已有 seek 回退 + 无参 commitSync 在全批处理完成时语义正确）。
- 不做重试计数/失败终点（WI22）、不改 onMessageBatch arity 契约裁定（WI25——本任务的「responses 短于记录」行为是修复而非契约裁定）。

## Scope

### In Scope

- `nop-message-kafka`：KafkaConsumeTask（batchConsume 重构 + javadoc）、KafkaMessageService（doSubscribe warn-once）、TestKafkaConsumeTask（4 个编号各 ≥1 测试）、日志、roadmap WI11 状态行。

### Out Of Scope

- 下游生产消费方 nop-stream-runtime 数据面行为（其 wire codec 不依赖批量边界细节；回归命令覆盖）。

## Execution Plan

### Phase 1 - 批量路径重构与编号测试

Status: completed
Targets: `nop-message/nop-message-kafka`

- Item Types: `Fix`

- [x] batchConsume：ConsumeLater break 语义 + 分区收敛提交 + 失败回退 seek + javadoc
- [x] doSubscribe：enableAutoCommit warn-once（包私有 `warnIfAutoCommit()`，接线 spy 验证；注：读 config bean 路径，extraProps 覆盖 auto-commit 的路径不覆盖——javadoc 声明，审查 Minor-6）
- [x] 测试 4 条（多分区索引敏感断言保持单分区；F-06/F-07 断言按分区顺序无关；F-06/F-08 补 never commitSync(anyMap)；F-58 测试 cancel subscription）：F-06 双分区批量失败→各分区 seek 回首条未最终化记录+无任何 commitSync；F-07 responses 短于记录→commitSync(Map) 仅含已派发上界+未派发分区 seek 回退+无无参 commitSync；F-08 中段 ConsumeLater→seek+后续记录无 reply+无任何 commitSync；F-58 spy 验证 doSubscribe 调用 warnIfAutoCommit（doReturn 注入 consumer + cancel 收尾）
- [x] 既有测试零回归（含 WI6 的 PartialCommit 边界测试）

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-kafka` 退出 0 全绿
- [x] **无静默跳过**：批量失败路径显式 seek 回退，无「跳过继续」分支
- [x] **新功能必有测试**：四个编号各一条命名对应的回归测试
- [x] mission 回归（kafka + core + rpc-core）退出 0
- [x] `ai-dev/logs/` 条目
- [x] No owner-doc update required（行为修复，无新契约；F-58 处置随 owner doc WI34 汇总）

## Closure Gates

- [x] F-06/F-07/F-08/F-58 四编号各有命名测试且全绿
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-kafka -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/11-wi11-kafka-batch-boundary.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 真 broker 批量重投时序 → WI33 gated E2E。

## Closure

Status Note: WI11 经独立子代理 closure audit 八项全 PASS：四编号逐一证实修复（F-06 progress 回退 / F-07 收敛提交+未派发 seek / F-08 batch endpoint / F-58 warn-once 接线）、四条命名测试断言真实（40/40）、与 design §3.2 及 PartialCommit javadoc 语义一致、范围干净。audit 的 Low 级发现（anyConsumeLater 死字段）已在提交前清理；`-am` gate 以「install + 模块直测退出 0」等价形式记录（并行会话占用 orm-eql 链）。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_32dcb767-c0bb-4f4c-9921-0b8ebe1ed623（fresh session，自行重跑测试）
- Evidence:
  - F-06 PASS：progress 指针 + rewindFrom 分区最小 offset seek，无跳过分支
  - F-07 PASS：commitConverged 收敛 + rewindUndispatched（null/≥boundary 判定）+ 无参 commitSync 仅存于单条路径
  - F-08 PASS：seek+return batch endpoint，全批不提交，javadoc 残留声明
  - F-58 PASS：warnIfAutoCommit 接线（kafka|auto-commit 键）
  - 测试 PASS：kafka 40/40、core 17/0fail/1skip
  - 语义一致性 PASS：PartialCommit javadoc 与 design §3.2 逐句吻合
  - git 范围 PASS：6 文件
  - Deferred 分类检查：无新增 deferred
