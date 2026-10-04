# 10 WI10 Pulsar 异常传播修复

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI10，deps WI4✓；对应 MSG-F-05）
> Related: `ai-dev/plans/nop-message/08-wi8-pulsar-seek.md`（Pulsar 测试设施）

## Purpose

修复 MSG-F-05：`consume()` / `batchConsume()` 的 catch 不 rethrow → `runTask` 的退避与 `onException` 对消息级失败不可达 → 毒消息退化为无退避重投自旋（与 Kafka 行为相反）。按完成判定「改为 rethrow 或走统一失败回调」——采用 **rethrow**（Pulsar 侧逐消息 negativeAck/rollback 保留，异常向上传播给 runTask 的退避 + onException 处理，与 Kafka 形态对齐）。

## Current Baseline

- `consume()`（`:208-211`）：catch → `fail()`（LOG + negativeAcknowledge(messageId)）→ `context.rollback()` → 吞掉。
- `batchConsume()`（`:166-170`）：catch → LOG → `negativeAcknowledge(messages)` → `rollback()` → 吞掉。
- `runTask`（`:83-96`）：catch → LOG → `onException(e)` → `sleep(consumeErrorBackoffMs)`——仅对 poll/框架级异常可达。
- WI9 后测试设施：TestPulsarConsumeTask 11 用例、receive 阻塞 answer 模式、TestPulsarService 接缝。

## Goals

- `consume()` catch：保留 `fail()`（negativeAck 留证）；`rollback()` 用 inner try/catch + `e.addSuppressed`（事务模式下 rollback 的 runtime 异常不得遮蔽原异常——审查 Major-1）；末尾 `throw e`。
- `batchConsume()` catch：同构（negativeAcknowledge(messages) + suppressed rollback + `throw e`）。
- 语义声明（javadoc）：negativeAck 已触发 broker 重投（2.8 客户端本地调度、不抛 checked 异常——javap 实测）；rethrow 使退避与失败回调可达，两后端行为一致；副作用：`ERR_PARTIAL_COMMIT_TRANSACTIONAL`（WI6 批量路径）改后新达 onException+退避，属期望行为变化。
- 测试：① 单元级——`consume()` 对 handler 抛错 assertThrows 且 negativeAcknowledge(messageId) 被调；② runTask 级——**必须 `start()` 启动**（`schedule()` 下 `do..while(active)` 一轮即停，审查 Major-2），receive 第 1 次返回消息、第 2 次记录 nanoTime 后阻塞在 latch；handler doThrow 一次；断言 `onException` 被触发（ArgumentCaptor + assertSame 原异常实例）且两次 receive 间隔 ≥ backoff（50ms 下限断言，CI 稳定）；finally stop + countDown + cancel。

## Non-Goals

- 不改 `fail()` 的留证形态（完整失败回调归 WI16）。
- 不改 Kafka 侧（其异常路径本就可达）。
- 不动 stop/唤醒语义（F-54 归 WI19）。

## Scope

### In Scope

- `nop-message-pulsar`：PulsarConsumeTask（两处 catch + javadoc）、TestPulsarConsumeTask（新 2 用例）、日志、roadmap WI10 状态行。

### Out Of Scope

- WI16 失败回调、WI19 唤醒。

## Execution Plan

### Phase 1 - rethrow 与传播测试

Status: completed
Targets: `nop-message/nop-message-pulsar`

- Item Types: `Fix`

- [x] consume()/batchConsume() catch：suppressed rollback + 末尾 rethrow + javadoc 语义声明（含 PARTIAL_COMMIT 行为变化）
- [x] 测试 ①②：assertThrows + negativeAck 验证；runTask（start() 启动）存活 + onException assertSame + 退避时延下限

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-pulsar` 退出 0 全绿
- [x] **无静默跳过**：消息级失败路径不再吞异常（代码评审 + 测试双证）
- [x] **新功能必有测试**：退避与 onException 可达性有专门断言
- [x] mission 回归（pulsar + core）退出 0
- [x] `ai-dev/logs/` 条目
- [x] No owner-doc update required（行为对齐 design D5/两后端一致性，无新契约）

## Closure Gates

- [x] MSG-F-05 消除：runTask 退避与 onException 对消息级失败可达（测试证明）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/10-wi10-pulsar-exception-propagation.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 真 broker negativeAck 重投时序 → WI33 gated E2E。

## Closure

Status Note: WI10 经独立子代理 closure audit 全 PASS：MSG-F-05 消除（rethrow + suppressed rollback + negativeAck 留证）、传播测试有真实牙齿（onException 原实例 + gap ≥ backoff 下限）、27/27 + core 零回归、范围干净。audit 的 2 个 Minor（一处恒真断言被后续 gap 断言覆盖、assertSame 语义等价的文本偏差）与 3 个 Info 不阻塞。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_43f93dc6-5ee4-4ea1-962a-d839b26b28ea（fresh session，自行重跑测试）
- Evidence:
  - MSG-F-05 消除 PASS：consume() :216-227 / batchConsume() :166-178 均负ack留证+suppressed rollback+throw e
  - runTask 可达性 PASS：代码追踪 :83-88 onException + sleep 确实可达
  - 测试 PASS：pulsar 27/27（ConsumeTask 13）、core 17/0fail/1skip
  - git 范围 PASS：恰 5 文件
  - Deferred 分类检查：无新增 deferred
