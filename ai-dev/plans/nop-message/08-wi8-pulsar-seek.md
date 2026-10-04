# 08 WI8 Pulsar seek 实现与类型化拒绝

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI8，deps WI3✓ WI4✓；对应 MSG-F-03）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（D3 裁定）、`ai-dev/plans/nop-message/06-wi6-capability-validator.md`（SEEK 能力声明 withheld）

## Purpose

消除 `PulsarConsumeTask.seekToPosition()` 空方法体与 TODO（MSG-F-03）：按 D3 裁定实现 `SeekMode` → Pulsar 原生 seek 的映射，`seekToMessage` 类型化拒绝；`seekToMessage` 拒绝在 subscribe 同步路径抛出（对齐 WI7 的 Kafka 形态）；Pulsar 能力声明补 SEEK。

## Current Baseline

- WI6 时 Pulsar `CAPABILITIES` 有意 withheld SEEK（声明不得跑在行为前），代码注释与日志已登记，design D1 表已加注。
- `PulsarConsumeTask.seekToPosition()`（`:70-73`）空方法体 + TODO；`start()` 在 executor 线程调用它（异常会静默杀死 runnable 且 `active` 仍为 true——F-02 的 Pulsar 切面）。
- Pulsar 2.8 client API（实测 javap）：`Consumer.seek(MessageId)`、`Consumer.seek(long timestamp)` 同步方法，`MessageId.earliest` / `MessageId.latest` 常量在位。
- D3 对 Pulsar 的裁定：SeekMode 映射到原生 seek，在 **consumer 创建路径**执行（Pulsar 无 rebalance 回调概念，等价每次重连创建 consumer 时生效，天然满足 ALWAYS 语义）；seekToMessage 类型化拒绝。

## Goals

- **INIT 系用 `SubscriptionInitialPosition` 精确兑现**（审查裁定，优于创建后 seek）：INIT_SEEK_TO_BEGIN → `builder.subscriptionInitialPosition(Earliest)`、INIT_SEEK_TO_END → `builder.subscriptionInitialPosition(Latest)`——Pulsar 的 initial position 仅在订阅光标不存在时生效，精确对应 SeekMode javadoc「第一次从头/尾，以后从上次位置」，并与 Kafka 的 committed 门控跨后端对称；无 buffered-message 竞态。
- **ALWAYS_SEEK_TO_END 与 seekToTime 用创建后同步 seek**：`builder.subscribe()` 返回后（已连接，seek 为同步方法）调 `consumer.seek(MessageId.latest)` / `consumer.seek(timestamp)`；seek 失败 → 关闭已建 consumer/executor 后抛 `NopException.adapt`（错误路径资源清理，对齐 WI7 Kafka 教训）。null/DEFAULT 无任何 seek。
- `seekToMessage != null` → 类型化拒绝 `nop.err.message.seek-message-not-supported`（新增于 MessageErrors，按 D9 新码用新前缀；WI26 存量迁移不涉及本码）。
- `PulsarMessageService.subscribe` 顶部同步校验 seekToMessage（client 使用前，覆盖 concurrency>1 循环）。
- javadoc 声明两个边界：INIT/ALWAYS 的「重连」实为「每次经 SPI subscribe 创建 consumer」（Pulsar 客户端内部瞬断重连不会重新 seek）；2.8 的 seek 不清空已缓冲 incoming 队列（subscribe 返回到 seek 之间 broker 已推送的消息仍会先投递，窗口极小，真机行为归 WI33）。
- `CAPABILITIES` 补 `SEEK`（兑现 WI6 withheld 声明），design D1 表同步去掉括注。
- 测试：mode 映射矩阵（mock Consumer verify seek 调用）、seekToMessage 同步拒绝（client 为 null 的 service 即可，校验在其之前）、CAPABILITIES 含 SEEK、hollow 扫描不再命中空方法体。

## Non-Goals

- 不修 F-02/F-05 的完整异常传播面（runTask catch 语义归 WI10）。
- 不动 getAckTopic（WI9）。
- 不做 INIT「光标存在性」判断（Pulsar 2.8 client 无光标查询 API；INIT 与 ALWAYS 在 Pulsar 均解析于订阅创建时，与 D3 裁定文字一致——该简化在 javadoc 显式声明）。

## Scope

### In Scope

- `nop-message-pulsar`：PulsarConsumeTask、PulsarMessageService、TestPulsarConsumeTask、（新增）TestPulsarSubscribeValidation 或并入现有测试文件；`nop-api-core` MessageErrors 加一个错误码。
- `ai-dev/design/nop-message/message-contract-design.md`（D1 表 SEEK 括注移除）、`ai-dev/design/nop-message/backend-semantics-design.md`（3.1 Pulsar 行加注：INIT 经 SubscriptionInitialPosition 精确兑现）、日志、roadmap WI8 状态行。

### Out Of Scope

- F-05/F-54（WI10/WI19）、信封与 ack topic（WI24/WI9）。

## Execution Plan

### Phase 1 - seek 实现与同步拒绝

Status: completed
Targets: `nop-message/nop-message-pulsar`、`nop-kernel/nop-api-core`

- Item Types: `Fix`

- [x] PulsarMessageService.subscribe 顶部 seekToMessage 同步校验；doSubscribe：INIT 系 `subscriptionInitialPosition`、ALWAYS/seekToTime 创建后同步 seek、失败路径 close consumer + shutdown executor 后抛 adapt
- [x] doSubscribe 的 `client.newConsumer(schema)` 抽为 protected `newConsumerBuilder(schema)`（测试接缝，对齐 Kafka createConsumer 先例）；删除 PulsarConsumeTask.seekToPosition 空方法体与 TODO（INIT/ALWAYS 逻辑移入 service，task 不再持有 seek 职责）
- [x] MessageErrors 新增 ERR_SEEK_MESSAGE_NOT_SUPPORTED；CAPABILITIES 补 SEEK；design D1 表括注移除
- [x] 测试：mode 矩阵（INIT→initialPosition 断言、ALWAYS→seek(MessageId.latest)、DEFAULT+seekToTime→seek(timestamp)）经 newConsumerBuilder 接缝注入 mocked ConsumerBuilder/Consumer（测试收尾 subscription.cancel() 防 receive()==null 热循环）、seekToMessage 同步拒绝（client=null 即可）、getCapabilities 含 SEEK

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-pulsar,nop-kernel/nop-api-core` 退出 0
- [x] **无静默跳过**：TODO 与空方法体消除，全部模式显式处置（未知枚举值 fallback 语义显式声明）
- [x] **接线验证**：seek 调用发生在 subscribe 同步路径（测试断言 service 级行为而非仅 task 方法存在）
- [x] mission 回归（pulsar + core + rpc-core）退出 0
- [x] `ai-dev/logs/` 条目
- [x] Owner-doc 同步：design 3.1 与 D1 表在本 plan 内修订（见 Scope）

## Closure Gates

- [x] MSG-F-03 消除：空方法体与 TODO 删除（hollow 扫描 + grep 双证）
- [x] D3 映射与 D1 SEEK 声明落地
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/08-wi8-pulsar-seek.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 真 broker seek 行为（光标重置实际效果、缓冲队列投递窗口）→ WI33 gated E2E。
- 错误码对称性：seekToMessage 拒绝两后端当前用两个不同名码（Kafka 旧前缀 / Pulsar 走 MessageErrors 新前缀）→ WI26 存量迁移后统一裁定命名与归属。

## Closure

Status Note: WI8 经独立子代理 closure audit：MSG-F-03 消除、D3 映射、SEEK 声明、design 同步、20/20 全绿；audit 抓获 1 个 Major（seek 失败路径泄漏已建 Consumer——catch 只 shutdown executor 未 close consumer）已修复（consumer 提到 try 外，close 失败以 suppressed 附加），复跑全绿后关闭。Low 级（日志计数 3→9 已改；DEFAULT 无 seek 的反向断言备案）不阻塞。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_94c76f6b-a33c-4ffb-837e-72ffbf980257（fresh session，自行重跑测试与工具）
- Evidence:
  - MSG-F-03 消除 PASS：seekToPosition/TODO 全仓 grep 0 命中；scan-hollow 0 发现
  - D3 落地 PASS：applyInitialPosition Earliest/Latest、ALWAYS/seekToTime 同步 seek、seekToMessage subscribe 顶部拒绝
  - 错误码/能力声明/design 同步 PASS
  - 测试 PASS：pulsar 20/20（Major-1 修复后复跑）、core 17/0fail/1skip
  - git 范围 PASS
  - Major-1 修复证据：PulsarMessageService doSubscribe catch 中 consumer.close()（suppressed 附加）+ shutdownNow；pulsar 20/20 复跑退出 0
  - Deferred 分类检查：无新增 deferred；WI33 归属既有
