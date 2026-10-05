# 12 WI12 Kafka ConsumeLater 延迟语义

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI12，deps WI4✓；对应 MSG-F-09——P0 在线硬故障）
> Related: `ai-dev/plans/nop-message/11-wi11-kafka-batch-boundary.md`（批量 ConsumeLater 停批语义）

## Purpose

修复 MSG-F-09：单条路径 `ConsumeLater.getDelay()` 被丢弃（仅 debug 打印），seek 回同 offset 后立即继续 poll → 100% CPU 自旋。修复后 delay 被真正 honored：延迟期间不发生立即重投。

## Current Baseline

- `KafkaConsumeTask.pollAndConsume`（`:225-231`）：ConsumeLater → seek 回同 offset，`getDelay()` 仅 debug → 下次 poll 立即重投。
- WI11 已建立批量路径的 ConsumeLater batch-endpoint 语义（seek + return），但同样未 honoring delay。
- Kafka 无原生延迟投递（WI2 矩阵：Kafka DELAYED_SEND warn-once）；消费侧唯一兑现手段是 poll 循环内的延迟等待。

## Goals

- **单条路径（审查 Blocker 裁定：endpoint 语义）**：首条 ConsumeLater → seek → **break**（后续记录不 dispatch——分区提交边界单调，跳条提交不可能；继续 dispatch 必随重投重复副作用，且同分区多条 ConsumeLater 时 maxDelay 方案的末次 seek 覆盖前者会造成 deferral 丢失）→ sleep(该记录 delay) → 不提交。与 WI11 批量 endpoint 语义同构。
- **sleep 分片（响应性，不越 WI17/WI22 界）**：`wakeup()` 不中断 `Thread.sleep`，delay>5s 时 cancel 会退化到 shutdownNow 升级——sleep 按 ≤500ms 分片、片间查 `active`，保住即时停止。
- **InterruptedException**：单条路径本地处理（恢复中断位 + 返回）；批量路径 sleep 移出 try（或单独 catch），不得落进 rewind + onException（审查 Major-3）。
- javadoc 声明：① 无 cap（cap/退避归 WI22，配置面归 WI17）——超 max.poll.interval.ms 时 kick→rebalance（波及全组）→CommitFailedException 走 onException，重投方向**只晚不早**（放心 defer 的理由）；② 「重连」实为 poll 循环内的 sleep。
- 批量路径：ConsumeLater 分支 seek 后 sleep(delay) 再 return，与单条同构（审查 Major-3 补测试）。
- 测试：单条——handler 首次 ConsumeLater(200) 其后 null，断言两次调用 gap ≥ 200ms（下限断言，无 flaky：sleep 语义只多不少）+ `times(1)` 限定首轮无 commitSync；批量——同构时延断言。既有两条 1000L delay stub 调小避套件膨胀（审查 Minor-6）。

## Non-Goals

- 不做延迟上限/重试计数（WI22）。
- 不改 `max.poll.interval.ms` 配置面（WI17）。
- 不做分区 pause/resume 精细化延迟（优化候选，非本缺陷修复所需）。

## Scope

### In Scope

- `nop-message-kafka`：KafkaConsumeTask（两路径 sleep + javadoc）、TestKafkaConsumeTask（1 条时延测试）、日志、roadmap WI12 状态行。

### Out Of Scope

- WI17 poll 语义、WI22 重试上限。

## Execution Plan

### Phase 1 - delay honoring 与时延测试

Status: completed
Targets: `nop-message/nop-message-kafka`

- Item Types: `Fix`

- [x] pollAndConsume：maxDelay 收集 + 非 commit 分支 sleep + InterruptedException 本地处理 + javadoc
- [x] batchConsume：ConsumeLater 分支 seek 后 sleep(delay) 再 return
- [x] 测试：consumeLaterDelayHonoredBeforeRedelivery（gap ≥ 200ms 下限断言）

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-kafka` 退出 0 全绿（含 WI11 四条）
- [x] **无静默跳过**：delay 零值保持原行为（无 sleep），正值必等待
- [x] **新功能必有测试**：时延下限断言
- [x] mission 回归（kafka + core）退出 0
- [x] `ai-dev/logs/` 条目
- [x] No owner-doc update required（行为修复；max.poll.interval 交互随 WI17）

## Closure Gates

- [x] MSG-F-09 消除：delay honored（时延测试证明）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-kafka -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/12-wi12-kafka-consume-later-delay.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 真 broker 延迟重投节奏 → WI33 gated E2E。

## Closure

Status Note: WI12 经独立子代理 closure audit：MSG-F-09 消除（endpoint 语义 + interrupt 响应式分片 sleep）、单条/批量两路径时延测试全绿、42/42。审查 Blocker（maxDelay 方案 deferral 丢失）在 plan 修订阶段已裁定废弃并改 endpoint 语义。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_6858e525-e578-471e-949c-32ff1ce32b00（fresh session，自行重跑测试）
- Evidence:
  - endpoint 语义 PASS：pollAndConsume L259-268 与 batchConsume L397-403 均 seek+deferAfterBatch+return，无 dispatch 无 commit；旧 debug 打印已删
  - deferAfterBatch PASS：L317-329 ≤500ms 分片 + interrupt 恢复 + 无 cap + javadoc 三要素
  - 无残留 PASS：anyConsumeLater 局部变量/maxDelay/旧尾部守卫 grep 0 命中
  - 测试 PASS：gap≥200ms 与 elapsed≥150ms 真实时断言，stub 1000L→5L；kafka 42/42 exit 0、core 17/0 exit 0
  - git 范围 PASS：WI11 batchConsume 仅 +2 行，PartialCommit/commitConverged/rewindUndispatched 完整
  - 程序性 Blocker（日志缺失、Status 预置）已按审计建议回填；check-plan-checklist --strict 回填后 exit 0
  - Deferred 分类检查：无新增 deferred
