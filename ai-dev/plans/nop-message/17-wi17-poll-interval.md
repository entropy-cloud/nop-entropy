# 17 WI17 max.poll.interval.ms 与 poll 语义修正

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI17，deps WI4✓；对应 MSG-F-10 与 MSG-F-11；F-39 顺带承接 WI15 Deferred）
> Related: `ai-dev/plans/nop-message/12-wi12-kafka-consume-later-delay.md`（deferAfterBatch 的 max.poll 交互）

## Purpose

修复 MSG-F-10（`max.poll.interval.ms` 不可配置、无自洽指引）——踢出本身是 at-least-once 不可消除行为，本 plan 修复的是配置缺失（缓解）与 MSG-F-11（`batchReceiveTimeout` 直连 poll 时长语义不符）：poll 间隔可配置并与处理耗时自洽；batchReceiveTimeout 的 Kafka 语义显式声明；顺带完成 WI15 Deferred 的 F-39 顺带项（`producer.send` 同步抛包装为失败 future）。

## Current Baseline

- `KafkaConsumerConfig` 无 `max.poll.interval.ms` 字段、`buildConsumerProps` 未设置（F-10）；`max.poll.records=500` 默认 + 逐条无超时 `FutureHelper.getResult` → 慢 handler 踢出 + CommitFailedException 被吞。
- `doSubscribe`（WI11 后 `:296-298`）：`batchReceiveTimeout` 直连为 poll 时长（F-11），与 `MessageSubscribeOptions` javadoc「不足 batchReceiveCount 时的等待时间」语义不符。
- F-39（审查 B1 事实修正）：`max.block.ms` 耗尽时 `send(record, callback)` **走 callback**（ApiException 族路由为 onCompletion(error) + FutureFailure）——现有 callback 已转 completeExceptionally。真实同步抛路径是：InterruptException（中断且回调不调）、IllegalStateException（producer 已关闭）、其他未预期 RuntimeException。这三类使 sendAsync 直接抛而非返回失败 future。WI15 的 orTimeout 已兜底悬挂面；本 plan 包装防御其余同步抛。
- WI12 `deferAfterBatch`：delay sleep 在 poll 间隙，javadoc 已声明超 max.poll.interval 时重投只晚不早。

## Goals

- **F-10**：`KafkaConsumerConfig` 新增 `maxPollIntervalMs`（默认 300000 = Kafka 默认）+ `buildConsumerProps` 接线 `MAX_POLL_INTERVAL_MS_CONFIG`；javadoc 自洽指引（须 > 单批处理耗时；与 `max.poll.records`、WI12 的 ConsumeLater delay 的关系声明）。
- **F-11（显式声明形态，roadmap 允许；审查 m2 位置裁定）**：SPI 级 javadoc 写中立句（「该值的语义映射依后端而定，可能与后端拉取/长轮询阻塞时长绑定并进入其活性预算」），Kafka 专属句子放 `KafkaConsumerConfig`。不改运行时行为；解耦登记为优化候选（Follow-up）。design `backend-semantics-design.md` 补一条 D-entry（F-11 处置：显式声明而非解耦，被拒方案=解耦 wiring）。
- **F-39 顺带（WI15 Deferred 承接，按审查 B1 修正触发面）**：`prod.send` 调用包 try/catch Exception → 包装为 `future.completeExceptionally(adapt(e))`（InterruptException 额外恢复中断位）——`producer.send` 的任意同步异常（producer 已关闭/中断/未预期 runtime）不再逃逸 sendAsync；前置校验（未初始化 NopException / 选项校验 / 取消检查）保留既有同步 typed rejection 不变。
- 测试：① `buildConsumerProps` 含配置的 `max.poll.interval.ms`；② 慢 handler（sleep 100ms > pollTimeout 50ms）整批处理 + commitSync 正常、无异常上抛（mock 语义下的自洽冒烟；真踢出时序归 WI33）；③ F-39——`doThrow` 于 `producer.send` → sendAsync 返回异常完成 future 而非抛。

## Non-Goals

- 不做处理耗时自适应调节（记录为优化候选）。
- 真 broker 踢出时序归 WI33。

## Scope

### In Scope

- `nop-message-kafka`：KafkaConsumerConfig、KafkaMessageService（props + F-39 包装）、TestKafkaConsumeTask/TestKafkaMessageServiceSend、日志、roadmap WI17 状态行。
- `nop-api-core`：MessageSubscribeOptions.batchReceiveTimeout javadoc。

### Out Of Scope

- WI22/WI33。

## Execution Plan

### Phase 1 - 配置面与 F-39

Status: completed
Targets: `nop-message-kafka`、`nop-api-core`

- Item Types: `Fix`

- [x] maxPollIntervalMs 字段 + props 接线 + 双侧 javadoc（含 WI12 交互）
- [x] maxPollIntervalMs 字段 + props 接线 + 双侧 javadoc（含 WI12 交互）
- [x] F-39：prod.send try/catch（InterruptException 恢复中断位）→ completeExceptionally
- [x] 测试 ①②③（③用 IllegalStateException 措辞为「任意同步异常包装」，不声称复现 max.block.ms）

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-kafka` 退出 0 全绿
- [x] **无静默跳过**：`producer.send` 的同步异常不再逃逸 sendAsync（包装为异常完成 future）；前置校验（未初始化/选项校验/取消检查）保留既有同步 typed rejection
- [x] **新功能必有测试**：①②③
- [x] mission 回归（kafka + core + rpc-core）退出 0
- [x] `ai-dev/logs/` 条目
- [x] Owner-doc：design backend-semantics 补 F-11 D-entry + F-39 事实修正（本 plan 内）

## Closure Gates

- [x] F-10 配置面（可配置+javadoc 自洽指引）+ F-11 显式声明 + F-39 包装落地（测试证明）；踢出时序验证归 WI33（需为其括注扩展 charter——慢 handler 踢出场景）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-kafka -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/17-wi17-poll-interval.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 真 broker 慢 handler 踢出时序 → WI33（roadmap WI33 状态行更新时顺带把该场景写入其括注 charter）。
- batchReceiveTimeout 解耦（poll 时长独立配置）→ 优化候选。

## Closure

Status Note: F-10/F-11/F-39 均已在 live 代码落地并经独立审计复核：maxPollIntervalMs 配置面（默认 300000、管理键接线、自洽 javadoc 含 max.poll.records 与 WI12 deferAfterBatch 交互）；batchReceiveTimeout SPI 显式声明 + design 3.6.1 D-entry（被拒方案已记录；Kafka 侧句子已按审计意见补进 KafkaConsumerConfig）；producer.send 同步异常包装为失败 future（中断恢复已按审计修正为覆盖 Kafka InterruptException；前置校验 typed rejection 不变）。踢出时序验证归 WI33。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_6f417a37-4272-4975-854b-fac79a830d58（fresh session，自行重跑测试）
- Evidence:
  - F-10 PASS：KafkaConsumerConfig L47-54/L112-118；buildConsumerProps L192-193（管理键）；maxPollIntervalIsConfigurableAndManaged（extraProps 覆盖尝试下断言 450000）
  - F-11 PASS：MessageSubscribeOptions L41-46 中立声明；design 3.6.1 D-entry（含被拒方案与 F-39 事实修正）；Kafka 侧句子已补 KafkaConsumerConfig（审计 #4 处置）
  - F-39 PASS（审计修正后）：try/catch → completeExceptionally(adapt)；InterruptException 恢复中断位覆盖 org.apache.kafka.common.errors.InterruptException（javap 证实 extends KafkaException，原 instanceof InterruptedException 为死分支——审计 MAJOR-1 已修复）；producerSendSyncExceptionBecomesFailedFuture cause=IllegalStateException（adapt 透传）
  - 测试 PASS：kafka 54/54、message-core 17(1 skip)、rpc-core 30 全绿（等价 -am：api-core/message-core 先 install 成功）
  - check-plan-checklist --strict 退出码 0（L52 重复项已删、本段已回填）
  - Anti-Hollow：sendAsync→prod.send→callback/future 链路经 mock 测试验证连通；无空方法体/静默跳过
  - Deferred 检查：真 broker 踢出时序→WI33、解耦→优化候选，无 in-scope live defect 被降级

Follow-up:

- 真 broker 慢 handler 踢出时序 → WI33（charter 扩展含该场景）
- batchReceiveTimeout 解耦（poll 时长独立配置）→ 优化候选