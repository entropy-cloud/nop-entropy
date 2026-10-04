# 15 WI15 MessageSendOptions 字段接线

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI15，deps WI4✓；对应 MSG-F-45 与 MSG-F-39）
> Related: `ai-dev/design/nop-message/message-contract-design.md`（D8）、`ai-dev/plans/nop-message/06-wi6-capability-validator.md`

## Purpose

消除「声明了但从不消费」的 `MessageSendOptions.sendTimeout` / `cancelToken`（F-45，全仓零消费）：两 broker 后端接入发送路径（sendTimeout → 返回 future 的超时上界；cancelToken → 发送前取消检查），其余后端 warn-once（对齐 WI2 矩阵提案）。同时以 `orTimeout` 覆盖 F-39 的暴露面。

## Current Baseline

- F-45 实测（WI1/WI2）：`sendTimeout` 唯一写入点 `MessageRpcClient:81`、零读取方；`cancelToken` 仅 `RpcMessageSender:52` 消费。
- `ICancelToken`（实测）：`isCancelled()` / `appendOnCancel(Consumer<String>)` / `removeOnCancel` / `getCancelReason()`。
- 两后端 producer 级配置：`KafkaProducerConfig.sendTimeoutMs=120000`、`PulsarProducerConfig.sendTimeout=30000`（builder 级已传）。
- WI6 validator：sendTimeout/cancelToken 映射注释「wiring adjudicated to WI15」。

## Goals

- **sendTimeout 接入（两 broker）**：`sendAsync` 返回的 future 上加 `orTimeout(sendTimeoutMs)`；options 优先 → producer 级配置回退（`defaultProducerConfig` null 视为无上界）；负值/零不加。
- **cancelToken 接入（两 broker）**：发送前检查——isCancelled → 异常完成 future（`ERR_SEND_CANCELLED`），producer 不被触达。形态依据（审查实证）：`MessageRpcClient.callAsync:84-87` 同链先例，不用同步抛（避免 F-39 反模式）。
- **超时异常契约（审查 Major-3）**：裸 `java.util.concurrent.TimeoutException`（JDK orTimeout 语义，异常完成时原 cause 保留）；fire-and-forget 声明：超时后底层 send 不中止、消息仍可能送达。默认行为变更：producer 默认值为正 → 所有 sendAsync 获得 120s/30s 上界。
- **其余后端**：`SEND_TIMEOUT` 新能力（bundle 两字段）；Kafka/Pulsar 声明；其余 5 后端 WARN_ONCE（warn-once）；TdEngine 空集类型化拒绝（既定）。AGENTS.md Protected Areas 补 `nop-api-core` 行。

## Non-Goals

- 不包装 `producer.send` 同步抛（F-39 完整修复 deferred，见 Deferred）。
- **mid-send 取消（审查 Major-2 裁定）**：不实现——两 broker 客户端均无 per-message abort 能力；入口检查是可兑现形态。

## Scope

### In Scope

- `nop-api-core`：MessageCapability 加 SEND_TIMEOUT、MessageErrors 加 ERR_SEND_CANCELLED、validator 映射。
- 两 broker sendAsync 接线；5 非 broker 后端 WARN_ONCE。
- 测试、design 更新、AGENTS.md、日志、roadmap WI15 状态行。

### Out Of Scope

- F-39 同步抛包装（Deferred，见下）、WI22 重试。

## Execution Plan

### Phase 1 - 接线与测试

Status: completed
Targets: `nop-api-core`、两 broker、5 非 broker 后端

- Item Types: `Fix`

- [x] 枚举 SEND_TIMEOUT（bundle javadoc）+ ERR_SEND_CANCELLED + validator 映射 + CAPABILITIES/WARN_ONCE + design D1 表/3.1 值域 + 3.4 收口 + AGENTS.md Protected Areas
- [x] Kafka/Pulsar sendAsync：cancelToken 前置检查 + orTimeout（Pulsar producers map 测试缝 producersForTest）
- [x] 测试 ①-⑤（Kafka 3：TimeoutException cause/回退/取消不派发；Pulsar 2：取消/超时；validator 1：三路断言）

Exit Criteria:

- [x] `./mvnw test -pl nop-kernel/nop-api-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-message/nop-message-core` 退出 0 全绿
- [x] **无静默跳过**：sendTimeout/cancelToken 每后端 support / warn-once / 类型化拒绝（TdEngine 既定）——无 ignore 残留
- [x] **新功能必有测试**：①-⑤
- [x] mission 回归（kafka + pulsar + core + rpc-core）退出 0
- [x] SPI 回归面：8 修改模块——lettuce/mqtt-server/ai-agent 实跑 exit 0；sys-dao main compile exit 0（test-compile 被并行 mission 坏 import 阻塞，见 Closure）
- [x] `ai-dev/logs/` 条目
- [x] Owner-doc：design D1 表更新（本 plan 内）

## Closure Gates

- [x] F-45 消除：sendTimeout/cancelToken 无零消费字段（grep + 测试双证）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 退出 0（依赖预装 + 模块直测等价路径，已记录）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/15-wi15-send-options-wiring.md --strict` 退出码 0

## Deferred But Adjudicated

### F-39 同步抛包装

- Classification: `optimization candidate`
- Why Not Blocking Closure: F-39 核心危害是「返回永不完成的 future」（本 plan 以 orTimeout 兜底）；同步抛仅在 `max.block.ms`（默认 60s）耗尽的 broker 不可达极端场景触发，且是显式失败非静默。
- Successor Required: `yes`
- Successor Path: 独立小 plan 或 WI17 顺带。

## Non-Blocking Follow-ups

- 无。

## Closure

Status Note: WI15 经独立子代理 closure audit：代码面七项全 PASS（F-45 消除 grep+测试双证、SEND_TIMEOUT 全联动、AGENTS.md 补行、测试全绿）。文本面 3 个 Major（plan 文件 4 份重复、证据占位符、design 3.4 收口缺失）已在关闭前全部处置：去重为单份、回填证据、design 3.4 记录裁定。回归说明：sys-dao test-compile 被并行 mission（plan-2306 commit bc9c444cf3）坏 import 阻塞，main compile exit 0。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_f8261d17-dcbd-4f3d-ac4b-d9fd092de49f（fresh session，自行重跑测试）
- Evidence:
  - sendTimeout PASS：Kafka orTimeout :243-247 + resolveSendTimeoutMs :250-258；Pulsar :188-203 同构（默认 120s/30s）
  - cancelToken PASS：:207-214/:172-179 前置检查，ERR_SEND_CANCELLED，verify producer.send/newMessage never
  - 能力矩阵 PASS：SEND_TIMEOUT bundle 枚举 + validator :43-46 + 两 broker CAPABILITIES + 5 非 broker WARN_ONCE + TdEngine 空集拒绝
  - design/AGENTS PASS：D1 表两行 + 值域 + 3.4 收口（回填）+ Protected Areas 行
  - 测试 PASS：api-core 158/0、kafka 50/0、pulsar 29/0、message-core 与 lettuce+mqtt+ai-agent exit 0
  - sys-dao：main compile exit 0；test-compile exit 1 系并行 mission 坏 import（io.nop.core.unittest.JunitBaseTestCase 不存在），与本改动无关
  - scan-hollow --severity high 三模块 0 发现
  - Deferred 分类检查：F-39 同步抛包装为 optimization candidate（有 successor path），合规
