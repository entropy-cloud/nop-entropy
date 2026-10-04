# 09 WI9 Pulsar 请求-响应通道修复

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI9，deps WI4✓；对应 MSG-F-04——P0 在线硬故障）
> Related: roadmap Hard constraint（getAckTopic 派生触及项必答回归：nop-rpc-core / nop-graphql-message）

## Purpose

修复 Pulsar 请求-响应通道全断（MSG-F-04）：`getAckTopic` 对全名 topic（`persistent://tenant/ns/name`）做 `"ack-" + topic` 前缀拼接，`TopicName.get("ack-persistent://...")` 抛 `IllegalArgumentException`，异常被消费循环吞掉 → 请求消息无限重投。修复后 reply 落到合法 topic。

## Current Baseline

- SPI 默认实现 `IMessageSubscriber.getAckTopic`（`IMessageSubscriber.java:17-19`）返回 `"ack-" + topic`——对短名（Kafka/Local）正确；Pulsar 的 topic 来自 `message.getTopicName()` 全名。
- Pulsar 侧 3 个 reply send 调用点（WI6/WI8 后行号重锚：`:157` 批量、`:199` Acknowledge、`:203` 单条非 null）的异常均被 catch 吞掉 → negativeAck 重投（F-04 主症状）。
- WI8 已建立 `newConsumerBuilder` 测试接缝与 subscription cancel 收尾模式；`PulsarHelper` 静态工具类在位。
- 必答回归方（Hard constraint）：nop-rpc-core（MessageRpcClient/Server）与 nop-graphql-message。

## Goals

- **派生逻辑**：`PulsarHelper.deriveAckTopic(String topic)` 静态方法——`persistent://`/`non-persistent://` 开头的全名：保留 domain+namespace，仅对末段加 `ack-` 前缀（`persistent://public/default/orders` → `persistent://public/default/ack-orders`）；短名维持 `ack-<topic>`（2 段形式如 `ns/orders` 对 Pulsar 非法、运行时不可达，仅作为纯字符串契约行）。javadoc 契约措辞为「对合法输入 topic 保证派生合法」。`PulsarMessageService` override `getAckTopic` 委托它。
- **顺带修复 MSG-NEW-03（未登记缺陷，审查发现）**：`buildProducer` 从不调 `.topic(...)`（computeIfAbsent lambda 还丢弃 topic key），真客户端上 `ProducerBuilderImpl.create()` 必抛 `IllegalArgumentException("Topic name must be set")`——即使 ack topic 修好，reply 在真实 broker 上仍发不出去。修复：`buildProducer(topic, schema)` 设置 topic；`getProducer` 未知 topic 走 `computeIfAbsent` 按需创建（schema 缺省 STRING）；`init()` 不再创建无 topic 的 `defaultProducer`（该形态在 2.8 非法），`defaultProducer` 字段与 destroy 分支移除。WI1 审计文档附录登记 MSG-NEW-03。
- **E2E 注入路径（审查 B1）**：测试子类 override `buildProducer` 返回 mocked Producer，并 `setTopicSchemas(Map.of(派生后 ack topic 精确串, Schema.STRING))` 使 `getProducer` 命中 map——key 与真实 deriveAckTopic 输出逐字符一致，派生错误即 NPE 响亮失败（结构性接线断言）；另在 getProducer override 中记录 topic 串，把「落点」变为直接断言（不 mock getAckTopic，仍是真实派生）。
- **Message mock stub 清单**：必须 stub——getTopicName（FQN）、getValue、receive（首条返回消息，第二次起返回可控 latch 且测试先 countDown 再 cancel，防 awaitTermination 白等 5s）、acknowledgeAsync→completedFuture、producer.newMessage→builder、builder.sendAsync→completedFuture(MessageId mock)（null 会在 thenAccept NPE→negativeAck→断言全挂）；可省——getKey/getProperties（null 跳过）、getEventTime/getPublishTime/getSequenceId（primitive 默认 0 无害）。TypedMessageBuilder 仅 value+sendAsync 被调（options 恒 null、reply 非 ApiMessage）。断言用 verify(timeout) 容忍 executor 线程时序。
- **SPI javadoc 契约修复**：`IMessageSubscriber.getAckTopic` javadoc 显式声明「返回的 topic 必须对目标 broker 合法；broker 命名含命名空间/域名的后端必须 override」，不改默认行为（Kafka/Local 语义保持）。
- **端到端用例**（roadmap 完成判定）：service 级测试——subscribe 后消费一条 FQN topic 消息，handler 返回 Acknowledge 与普通非 null 两种形态，断言 reply 经真实 `deriveAckTopic` 派生落点（captured TypedMessageBuilder 的 value 与 producer 目标）且消息被 ack（非 negativeAck）。用 WI8 的 `newConsumerBuilder` 接缝 + `buildProducer` override 注入 mocked producer。
- 必答回归：nop-rpc-core、nop-graphql-message 测试退出 0。

## Non-Goals

- 不改 Kafka/Local 的 ack topic 派生（短名语义已正确）。
- 不修 reply 异常被吞的通用面（F-05 归 WI10）。
- 不实现 reply topic 的自动创建/订阅端（消费方自行订阅 ack topic，与现状一致）。

## Scope

### In Scope

- `nop-message-pulsar`：PulsarHelper（deriveAckTopic）、PulsarMessageService（override）、TestPulsarHelper（派生矩阵）、TestPulsarConsumeTask（E2E 用例）。
- `nop-kernel/nop-api-core`：IMessageSubscriber javadoc。
- design `backend-semantics-design.md`（ack topic 契约一行）、日志、roadmap WI9 状态行。

### Out Of Scope

- WI10 异常传播、WI24 信封对称性。
- `AGENTS.md` Protected Areas 表补 `nop-api-core` 行（roadmap :211 硬性附带项，随 Phase B SPI 触发项最后一个 WI27 一并补，避免多次改表）。

## Execution Plan

### Phase 1 - 派生修复与 E2E 用例

Status: completed
Targets: `nop-message/nop-message-pulsar`、`nop-kernel/nop-api-core`

- Item Types: `Fix`

- [x] PulsarHelper.deriveAckTopic + PulsarMessageService.getAckTopic override
- [x] MSG-NEW-03：buildProducer(topic, schema) 设 topic；getProducer 未知 topic 按需创建；defaultProducer 字段移除；WI1 审计文档附录登记
- [x] IMessageSubscriber.getAckTopic javadoc 契约声明
- [x] TestPulsarHelper：FQN（persistent/non-persistent）/短名/TopicName.get 可解析性矩阵（2 段 `ns/orders` 运行时不可达，按 Goals 记为纯字符串契约边界，不设用例）
- [x] E2E 用例：Acknowledge 形态（reply 落 FQN ack topic + consumer.acknowledgeAsync 被调）+ 非 null reply 形态；断言全程无 negativeAcknowledge
- [x] 必答回归：`./mvnw test -pl nop-network/nop-rpc/nop-rpc-core,nop-service-framework/nop-graphql/nop-graphql-message` 退出 0

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-pulsar` 退出 0 全绿
- [x] **端到端验证**：subscribe→receive→dispatch→reply→ack 全链在测试中走通（非仅单元函数断言）
- [x] **接线验证**：E2E 用例使用真实 deriveAckTopic（非 mock service.getAckTopic）
- [x] `ai-dev/logs/` 条目
- [x] Owner-doc：design 3.1/契约行同步（ack topic 派生规则）

## Closure Gates

- [x] MSG-F-04 主症状消除：全名 topic 不再产生非法 ack topic（单元矩阵 + E2E 双证）
- [x] 必答回归方（rpc-core/graphql-message）退出 0
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/09-wi9-pulsar-ack-topic.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 真 broker 上 reply topic 首次发送时的自动创建行为 → WI33 gated E2E。

## Closure

Status Note: WI9 经独立子代理 closure audit 七项全 PASS：F-04 主症状消除（单元矩阵 + E2E 双证）、MSG-NEW-03 顺带修复完整有登记、必答回归 rpc-core 30/0 实跑、Anti-Hollow 通过。3 个 Minor 均为文本级（矩阵措辞已在本收口改写、Phase Status 翻转、owner-doc 目标文件名漂移知悉），不阻塞。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_d7ce42a0-1233-4401-8bde-d1f3c9508e60（fresh session，自行重跑测试）
- Evidence:
  - F-04 修复 PASS：deriveAckTopic :109-117 + getAckTopic override + SPI javadoc 契约
  - MSG-NEW-03 PASS：buildProducer(topic, schema) + 按需创建 + defaultProducer 移除无残留
  - design PASS：contract-design §3.2.1 + 审计附录 MSG-NEW-03 行
  - 测试 PASS：pulsar 25/25（Helper 10 + ConsumeTask 11 + IoC 4）；E2E 四要素（value/producedTopics/acknowledgeAsync/never negativeAck）齐备
  - 必答回归 PASS：rpc-core 30/30 实跑；graphql-message 编译级（无 src/test）
  - git 范围 PASS；Anti-Hollow scan 0 发现
  - Deferred 分类检查：无新增 deferred- [x] WI9 Pulsar 请求-响应通道修复

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI9，deps WI4✓；对应 MSG-F-04——P0 在线硬故障）
> Related: roadmap Hard constraint（getAckTopic 派生触及项必答回归：nop-rpc-core / nop-graphql-message）

## Purpose

修复 Pulsar 请求-响应通道全断（MSG-F-04）：`getAckTopic` 对全名 topic（`persistent://tenant/ns/name`）做 `"ack-" + topic` 前缀拼接，`TopicName.get("ack-persistent://...")` 抛 `IllegalArgumentException`，异常被消费循环吞掉 → 请求消息无限重投。修复后 reply 落到合法 topic。

## Current Baseline

- SPI 默认实现 `IMessageSubscriber.getAckTopic`（`IMessageSubscriber.java:17-19`）返回 `"ack-" + topic`——对短名（Kafka/Local）正确；Pulsar 的 topic 来自 `message.getTopicName()` 全名。
- Pulsar 侧 3 个 reply send 调用点（WI6/WI8 后行号重锚：`:157` 批量、`:199` Acknowledge、`:203` 单条非 null）的异常均被 catch 吞掉 → negativeAck 重投（F-04 主症状）。
- WI8 已建立 `newConsumerBuilder` 测试接缝与 subscription cancel 收尾模式；`PulsarHelper` 静态工具类在位。
- 必答回归方（Hard constraint）：nop-rpc-core（MessageRpcClient/Server）与 nop-graphql-message。

## Goals

- **派生逻辑**：`PulsarHelper.deriveAckTopic(String topic)` 静态方法——`persistent://`/`non-persistent://` 开头的全名：保留 domain+namespace，仅对末段加 `ack-` 前缀（`persistent://public/default/orders` → `persistent://public/default/ack-orders`）；短名维持 `ack-<topic>`（2 段形式如 `ns/orders` 对 Pulsar 非法、运行时不可达，仅作为纯字符串契约行）。javadoc 契约措辞为「对合法输入 topic 保证派生合法」。`PulsarMessageService` override `getAckTopic` 委托它。
- **顺带修复 MSG-NEW-03（未登记缺陷，审查发现）**：`buildProducer` 从不调 `.topic(...)`（computeIfAbsent lambda 还丢弃 topic key），真客户端上 `ProducerBuilderImpl.create()` 必抛 `IllegalArgumentException("Topic name must be set")`——即使 ack topic 修好，reply 在真实 broker 上仍发不出去。修复：`buildProducer(topic, schema)` 设置 topic；`getProducer` 未知 topic 走 `computeIfAbsent` 按需创建（schema 缺省 STRING）；`init()` 不再创建无 topic 的 `defaultProducer`（该形态在 2.8 非法），`defaultProducer` 字段与 destroy 分支移除。WI1 审计文档附录登记 MSG-NEW-03。
- **E2E 注入路径（审查 B1）**：测试子类 override `buildProducer` 返回 mocked Producer，并 `setTopicSchemas(Map.of(派生后 ack topic 精确串, Schema.STRING))` 使 `getProducer` 命中 map——key 与真实 deriveAckTopic 输出逐字符一致，派生错误即 NPE 响亮失败（结构性接线断言）；另在 getProducer override 中记录 topic 串，把「落点」变为直接断言（不 mock getAckTopic，仍是真实派生）。
- **Message mock stub 清单**：必须 stub——getTopicName（FQN）、getValue、receive（首条返回消息，第二次起返回可控 latch 且测试先 countDown 再 cancel，防 awaitTermination 白等 5s）、acknowledgeAsync→completedFuture、producer.newMessage→builder、builder.sendAsync→completedFuture(MessageId mock)（null 会在 thenAccept NPE→negativeAck→断言全挂）；可省——getKey/getProperties（null 跳过）、getEventTime/getPublishTime/getSequenceId（primitive 默认 0 无害）。TypedMessageBuilder 仅 value+sendAsync 被调（options 恒 null、reply 非 ApiMessage）。断言用 verify(timeout) 容忍 executor 线程时序。
- **SPI javadoc 契约修复**：`IMessageSubscriber.getAckTopic` javadoc 显式声明「返回的 topic 必须对目标 broker 合法；broker 命名含命名空间/域名的后端必须 override」，不改默认行为（Kafka/Local 语义保持）。
- **端到端用例**（roadmap 完成判定）：service 级测试——subscribe 后消费一条 FQN topic 消息，handler 返回 Acknowledge 与普通非 null 两种形态，断言 reply 经真实 `deriveAckTopic` 派生落点（captured TypedMessageBuilder 的 value 与 producer 目标）且消息被 ack（非 negativeAck）。用 WI8 的 `newConsumerBuilder` 接缝 + `buildProducer` override 注入 mocked producer。
- 必答回归：nop-rpc-core、nop-graphql-message 测试退出 0。

## Non-Goals

- 不改 Kafka/Local 的 ack topic 派生（短名语义已正确）。
- 不修 reply 异常被吞的通用面（F-05 归 WI10）。
- 不实现 reply topic 的自动创建/订阅端（消费方自行订阅 ack topic，与现状一致）。

## Scope

### In Scope

- `nop-message-pulsar`：PulsarHelper（deriveAckTopic）、PulsarMessageService（override）、TestPulsarHelper（派生矩阵）、TestPulsarConsumeTask（E2E 用例）。
- `nop-kernel/nop-api-core`：IMessageSubscriber javadoc。
- design `backend-semantics-design.md`（ack topic 契约一行）、日志、roadmap WI9 状态行。

### Out Of Scope

- WI10 异常传播、WI24 信封对称性。
- `AGENTS.md` Protected Areas 表补 `nop-api-core` 行（roadmap :211 硬性附带项，随 Phase B SPI 触发项最后一个 WI27 一并补，避免多次改表）。

## Execution Plan

### Phase 1 - 派生修复与 E2E 用例

Status: completed
Targets: `nop-message/nop-message-pulsar`、`nop-kernel/nop-api-core`

- Item Types: `Fix`

- [x] PulsarHelper.deriveAckTopic + PulsarMessageService.getAckTopic override
- [x] MSG-NEW-03：buildProducer(topic, schema) 设 topic；getProducer 未知 topic 按需创建；defaultProducer 字段移除；WI1 审计文档附录登记
- [x] IMessageSubscriber.getAckTopic javadoc 契约声明
- [x] TestPulsarHelper：FQN（persistent/non-persistent）/短名/TopicName.get 可解析性矩阵（2 段 `ns/orders` 运行时不可达，按 Goals 记为纯字符串契约边界，不设用例）
- [x] E2E 用例：Acknowledge 形态（reply 落 FQN ack topic + consumer.acknowledgeAsync 被调）+ 非 null reply 形态；断言全程无 negativeAcknowledge
- [x] 必答回归：`./mvnw test -pl nop-network/nop-rpc/nop-rpc-core,nop-service-framework/nop-graphql/nop-graphql-message` 退出 0

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-pulsar` 退出 0 全绿
- [x] **端到端验证**：subscribe→receive→dispatch→reply→ack 全链在测试中走通（非仅单元函数断言）
- [x] **接线验证**：E2E 用例使用真实 deriveAckTopic（非 mock service.getAckTopic）
- [x] `ai-dev/logs/` 条目
- [x] Owner-doc：design 3.1/契约行同步（ack topic 派生规则）

## Closure Gates

- [x] MSG-F-04 主症状消除：全名 topic 不再产生非法 ack topic（单元矩阵 + E2E 双证）
- [x] 必答回归方（rpc-core/graphql-message）退出 0
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/09-wi9-pulsar-ack-topic.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 真 broker 上 reply topic 首次发送时的自动创建行为 → WI33 gated E2E。

## Closure

Status Note: WI9 经独立子代理 closure audit 七项全 PASS：F-04 主症状消除（单元矩阵 + E2E 双证）、MSG-NEW-03 顺带修复完整有登记、必答回归 rpc-core 30/0 实跑、Anti-Hollow 通过。3 个 Minor 均为文本级（矩阵措辞已在本收口改写、Phase Status 翻转、owner-doc 目标文件名漂移知悉），不阻塞。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_d7ce42a0-1233-4401-8bde-d1f3c9508e60（fresh session，自行重跑测试）
- Evidence:
  - F-04 修复 PASS：deriveAckTopic :109-117 + getAckTopic override + SPI javadoc 契约
  - MSG-NEW-03 PASS：buildProducer(topic, schema) + 按需创建 + defaultProducer 移除无残留
  - design PASS：contract-design §3.2.1 + 审计附录 MSG-NEW-03 行
  - 测试 PASS：pulsar 25/25（Helper 10 + ConsumeTask 11 + IoC 4）；E2E 四要素（value/producedTopics/acknowledgeAsync/never negativeAck）齐备
  - 必答回归 PASS：rpc-core 30/30 实跑；graphql-message 编译级（无 src/test）
  - git 范围 PASS；Anti-Hollow scan 0 发现
  - Deferred 分类检查：无新增 deferred
