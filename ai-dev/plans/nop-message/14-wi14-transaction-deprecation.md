# 14 WI14 Kafka 与 Pulsar 事务语义显式化（D11）

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI14，deps WI3✓；对应 MSG-F-13/F-52/F-57）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（D11/3.7）、`ai-dev/design/nop-message/message-contract-design.md`（D8 弃用期规则）

## Purpose

按 D11 裁定移除事务语义的「声明而不兑现」形态：`MessageSubscribeOptions.transactional` / `transactionTimeout` 两字段进入**弃用期**（字段保留 YAML 兼容、使用时 warn-once 声明被忽略），全部事务行为路径删除——Pulsar 每消息建事务（F-52）、timeout=0 静默回落（F-57）、Kafka 零处置（F-13）随之消除。`ConsumeContext.commit/rollback` 的 no-op 以 at-least-once 基线在 javadoc 与 design 一并声明。

## Current Baseline

- D11(b) 已裁定：删除两字段的行为（非物理删除字段——按 D8 弃用期执行），未来事务按 D1 `TRANSACTION` 能力声明逐后端实现。
- Pulsar：`PulsarConsumeTask.newConsumeContext`（`:190-196`）每消息 `service.newTransaction`（F-52）；`transactionTimeout=0` 静默回落（F-57）；`ERR_PARTIAL_COMMIT_TRANSACTIONAL`（WI6 引入）在事务删除后成为死分支。
- Kafka：`transactional` 全程零处置（F-13）；validator 现将 TRANSACTION 缺失映射为类型化拒绝（WI6 的 D11 过渡态）。
- `MessageCapability.TRANSACTION` 枚举值在位；两后端中仅 Pulsar 声明 TRANSACTION。

## Goals

- **SPI 弃用期（D8 合规）**：`MessageSubscribeOptions.transactional/transactionTimeout` 的 getter/setter 标 `@Deprecated` + `@deprecated` javadoc（字段保留，YAML 键兼容——@DataBean/IoC/Jackson 均不读 Deprecated 注解，审查 Q4 实证）；`IMessageConsumeContext.sendAsync` 的接口级事务 javadoc 同步改写（审查 Major-2）。
- **Validator（审查 Q2 最小 diff）**：TRANSACTION 能力检查替换为直调 `warnOnce("message-spi", "transactional"/"transactionTimeout", ...)`——任何后端收到即一次性 WARN「deprecated and ignored by D11」，不再能力门禁；类 javadoc 补 D11 覆盖说明。影响面（grep 实证）：主代码读取点仅 validator 与 PulsarConsumeTask 两处；`TestMessageOptionsValidator` 两个断言拒绝的方法须重写为 warn-once 形态。
- **能力声明清理（审查 Major-1）**：Pulsar `CAPABILITIES` 移除 TRANSACTION；其余 5 后端 WARN_ONCE 集合中的 TRANSACTION 死条目顺手清理。
- **Pulsar 行为删除**：`newConsumeContext` 不再建事务（ConsumeContext.transaction 恒 null，保留 Transaction 字段位以最小化 diff？——不，直接删除字段与 `newTransaction`/`sendAsync(txn,...)` 重载与 `ERR_PARTIAL_COMMIT_TRANSACTIONAL` 分支，dead code 不留）；`ackAsync` 走非事务路径。
- **Kafka**：无行为代码（本就零处置），由 validator warn-once 覆盖 F-13。
- **at-least-once 基线声明**：`ConsumeContext.commit/rollback` javadoc + design backend-semantics 3.7 更新（弃用期说明）。
- 测试：validator warn-once 形态（transactional 不再类型化拒绝）；字段 @Deprecated 反射断言；Pulsar `newConsumeContext` 不触 client 事务（mock client 计数）；grep 无 newTransaction 残留。

## Non-Goals

- 不删 `PulsarClientConfig.enableTransaction`（F-19 归 WI29）。
- 不实现任何新事务能力（未来按 D1 能力声明另立项）。

## Scope

### In Scope

- `nop-api-core`：MessageSubscribeOptions（@Deprecated）、MessageOptionsValidator（warn-once 替换）、MessageErrors（删 ERR_PARTIAL_COMMIT_TRANSACTIONAL）、MessageCapability（TRANSACTION 保留但无后端声明）。
- `nop-message-pulsar`：PulsarMessageService/PulsarConsumeTask 事务路径删除、TestPulsarConsumeTask 调整。
- `nop-message-kafka`：如有 transactional 相关过渡态测试则调整。
- design backend-semantics 3.7、日志、roadmap WI14 状态行。

### Out Of Scope

- WI29（enableTransaction 死配置）、WI26（存量错误码迁移）。

## Execution Plan

### Phase 1 - 弃用期与行为删除

Status: completed
Targets: `nop-api-core`、`nop-message-pulsar`

- Item Types: `Fix`

- [x] 字段 @Deprecated + validator warn-once 替换（重写 TestMessageOptionsValidator 两方法，断言 no-throw；WARNED 全局态勿跨用例断言次数）+ ERR_PARTIAL_COMMIT_TRANSACTIONAL 删除
- [x] IMessageConsumeContext javadoc 改写 + Pulsar CAPABILITIES 移除 TRANSACTION + 5 后端 WARN_ONCE 死条目清理
- [x] 行号重锚：newConsumeContext 实为 :234-240（Baseline :190-196 过期）
- [x] Pulsar 事务路径删除（newTransaction / sendAsync(txn,..) / ConsumeContext.transaction）
- [x] at-least-once 基线 javadoc + design 3.7 更新
- [x] 测试：validator warn-once、@Deprecated 反射、Pulsar 消费路径零事务调用、grep 无残留

Exit Criteria:

- [x] `./mvnw test -pl nop-kernel/nop-api-core,nop-message/nop-message-pulsar` 退出 0 全绿
- [x] **无静默跳过**：transactional=true 的行为=一次性 WARN + 忽略（第三态），无静默
- [x] **新功能必有测试**：warn-once 形态 + 弃用注解断言
- [x] mission 回归（pulsar + kafka + core + rpc-core）退出 0
- [x] `ai-dev/logs/` 条目
- [x] Owner-doc：design 3.7 弃用期更新（本 plan 内）

## Closure Gates

- [x] F-13/F-52/F-57 消除（grep + 测试双证）
- [x] D8 弃用期合规（字段保留 + warn-once）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/14-wi14-transaction-deprecation.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 真 broker 事务行为不再存在，无 E2E 需求。

## Closure

Status Note: WI14 经独立子代理 closure audit 七项全 PASS：SPI 弃用期（@Deprecated×6 + 接口 javadoc 改写）、validator warn-once 直调、Pulsar 删除面全仓 grep 0 残留、at-least-once 基线声明、测试全绿（api-core 157 / pulsar 27 / kafka 47 / core 17 / rpc-core 0 失败）、范围干净。2 个 Minor（两后端注释过期、mock 计数测试以 grep+等价证据替代）已在收口时处置。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_42ea09cc-0599-4df5-87f3-6d8d874b49f5（fresh session，自行重跑测试与工具）
- Evidence:
  - SPI 弃用期 PASS：MessageSubscribeOptions 字段+访问器 @Deprecated×6；IMessageConsumeContext javadoc at-least-once
  - Validator PASS：warnOnce("message-spi",...) 直调，非 capability 门禁；类 javadoc D11 说明
  - Pulsar 删除面 PASS：main+test grep 0 命中；CAPABILITIES/WARN_ONCE/MessageErrors 清理确认
  - at-least-once PASS：ConsumeContext 显式 no-op + design 3.7 弃用期表述
  - 测试 PASS：api-core 157 / pulsar 27 / kafka 47 / core 17 / rpc-core 0 失败；validator 两拒绝断言重写 + 反射断言
  - git 范围 PASS：13 文件
  - Deferred 分类检查：无新增 deferred
