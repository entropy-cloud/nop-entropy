# nop-message 四模块与 SPI 全量审计基线（WI1）

> Status: resolved
> Date: 2026-10-04
> Scope: `nop-kernel/nop-api-core`（SPI）、`nop-message`（core / kafka / pulsar / debezium）
> Conclusion: roadmap 59 个 `MSG-F-nn` 编号逐条对照 2026-10-04 live 代码复核完毕：58 条 confirmed（57 条缺陷 + MSG-F-51 平台先例，均在 live 代码中成立）、1 条 watch-only（MSG-F-58，形态存在但当前路径不可达）、0 条 already-correct。roadmap 的缺陷判定无一条被推翻。另新登记 2 条 roadmap 未覆盖的 watch-only 发现（MSG-NEW-01、MSG-NEW-02）。roadmap 证据锚点（2026-10-01 实测）与 2026-10-04 live 代码存在少量行号漂移，本文档给出的锚点为当日实测值，后续工作项以本文档锚点为准。

## Context

`missions/nop-message-encapsulation.json` 驱动的 roadmap（`ai-dev/backlog/nop-message-encapsulation-roadmap.md`）以 2026-10-01 的 file:line 实测为证据口径，声称 58 条缺陷 + 1 条平台先例。WI1 要求在执行任何修复前建立审计基线：逐条复核三态裁定（confirmed / already-correct / watch-only），confirmed 逐条给当日 live 锚点，作为 WI2 能力矩阵与全部 Phase B+ 工作项的证据输入。

复核方法：通读 SPI 19 文件 + 注解 2 文件 + `nop-message` 四模块 main 源码（4251 行）+ 关键测试与消费方，`grep -n` 逐条固定锚点；对「零引用 / 零调用」类断言做全仓 grep 反证。

## 结论摘要

- **SPI 骨架结论与 roadmap 一致**：`IMessageService` 仅 2 个抽象方法（`sendAsync` / `subscribe`），其余全 `default`；broker 切换靠 `beans.xml` + `ioc:default="true"`。骨架不需重写。
- **全部 58 条缺陷在 live 代码中仍然成立**，无一被修复或误判。两条 watch-only（F-58、F-52 相关的可达性限定）不改变修复优先级。
- **行号漂移**：roadmap 2026-10-01 锚点与 2026-10-04 live 相比普遍偏移 0-20 行（如 KafkaConsumeTask `start()` 由 :87 仍为 :87，但 `seekToPosition` 由 :129 系漂移至 :106-145；`seekToBeginHonoredNotStubbed` 由 :161 漂移至 :154）。本文档锚点为当日实测。
- **新发现 2 条 watch-only**（见附录），不足以改变 roadmap 工作项划分。

## P0 — 静默错误结果 / 数据正确性（16 条）

| ID | 裁定 | 2026-10-04 锚点与依据 |
| --- | --- | --- |
| MSG-F-01 | confirmed | `KafkaConsumeTask.java:87-99` `start()` 先 `seekToPosition()` 再 `runTask()`；`:106-145` `seekToPosition()` 依赖 `consumer.assignment()`（`:130,146`），首次 `poll()` 前恒为空集 → 四种 seek 模式与 `seekToTime` 全部 no-op。唯一生效分支 `:115-117` `seekToMessage` 抛错 |
| MSG-F-02 | confirmed | `KafkaConsumeTask.java:89-95` catch → `LOG.error` → `active=false` → `return`；调用方已持有 `isCancelled()==false` 的死订阅（`KafkaMessageService.java:286-291` task.start 后照常返回 subscription） |
| MSG-F-03 | confirmed | `PulsarConsumeTask.java:70-73` 空方法体 + `// TODO: implement seekToPosition`；`PulsarErrors.java:10-26` 无 seek 相关错误码 |
| MSG-F-04 | confirmed | `IMessageSubscriber.java:17-18` `getAckTopic` 拼 `"ack-" + topic`；Pulsar 的 topic 来自 `message.getTopicName()` 全名（`PulsarConsumeTask.java:138,171,175`）；抛错被 `consume()` 的 catch（`:177-181`）与 `batchConsume()` 的 catch（`:146-151`）吞掉 → negativeAck 重投。3 个抛出点即上述 `:138,171,175` |
| MSG-F-05 | confirmed | `PulsarConsumeTask.java:146-151`（batch catch 不 rethrow）、`:177-181`（单条 catch 不 rethrow）→ `runTask` 的 catch（`:86-96`）对消息级失败不可达，`:92` 的 `sleep(consumeErrorBackoffMs)` 退避是死代码，`onException` 永不被调用。Kafka 对比：`KafkaConsumeTask.java:187-194` 异常路径退避可达，两后端行为相反 |
| MSG-F-06 | confirmed | `KafkaConsumeTask.java:309-313` batch catch 无 `consumer.seek()`；单条路径 `:250-253` 有 seek。批量失败后 position 已前移整批且 `commitSync`（`:318`）被 `anyConsumeLater` 之外的成功路径跳过 → 批内消息被跳过 |
| MSG-F-07 | confirmed | `KafkaConsumeTask.java:283-313`：`onMessageBatch` 返回的 responses 少于记录数时循环只处理 `responses.size()` 条（`:288`），尾部记录未 ack；`:306` `context.commit()` 后 `:318` 无参 `commitSync()` 提交全部已 poll 记录 → at-most-once 静默丢失 |
| MSG-F-08 | confirmed | `KafkaConsumeTask.java:289-296`：批内某记录 `ConsumeLater` 后循环继续，后续记录仍发 reply（`:293-295`）；该批因 `anyConsumeLater` 跳过 `commitSync`（`:316-318`），下轮重放 → reply 重复；若后续记录抛错，`:309-313` 无 seek，直接 throw → 早期 ConsumeLater 记录永不重投 |
| MSG-F-09 | confirmed | `KafkaConsumeTask.java:225-231`：`ConsumeLater` 分支 seek 回同 offset（`:228`），`getDelay()` 仅 debug 打印（`:231`）→ 下次 poll 立即重投，100% CPU 自旋。Pulsar 对照：`PulsarConsumeTask.java:169` `reconsumeLater(message, delay)` 原生正确 |
| MSG-F-10 | confirmed | `KafkaConsumerConfig.java:15-110` 无 `max.poll.interval.ms` 字段；`buildConsumerProps`（`KafkaMessageService.java:146-169`）未设置该键；`max.poll.records=500` 默认（`KafkaConsumerConfig.java:34`，注入 `KafkaMessageService.java:160`）；`pollAndConsume` 逐条 `FutureHelper.getResult`（`KafkaConsumeTask.java:217`）无超时 → 慢 handler 触发踢出与 `CommitFailedException`（`:256-258` 被吞） |
| MSG-F-11 | confirmed | `KafkaMessageService.java:278-279` `batchReceiveTimeout` 直连为 `poll(Duration)` 时长（传入 `KafkaConsumeTask.pollTimeoutMs`）；与 `MessageSubscribeOptions.java:34-36` javadoc「不足 batchReceiveCount 时的等待时间」语义不符 |
| MSG-F-12 | confirmed | `KafkaMessageService.java:295-304` `resolveGroupId`：全局 `defaultConsumerConfig.getGroupId()` 优先于 `options.getSubscribeName()` → 同组异构订阅永久分区饥饿；`subscribeName` 非死字段：`KafkaConsumerConfig.groupId` 无默认值（`:20`），仓库无 YAML 设置 → 默认配置下 subscribeName 即消费组 id，缺陷是静默优先级遮蔽。`ERR_GROUP_ID_NOT_CONFIGURED` 在 `:267-271` 兜底 |
| MSG-F-13 | confirmed | `KafkaConsumeTask.java:331-337` `ConsumeContext.commit()/rollback()` 均 no-op（注释自认）；全 `nop-message-kafka` main 源码无任何 `isTransactional` 读取（grep 实测 0 处）→ `transactional=true` 零处置且无 WARN |
| MSG-F-14 | confirmed | `LocalMessageService.java:184-191` `handleMessageResult`：无 `ConsumeLater` 分支，`ret != null` 即 `send(getAckTopic(topic), ret)` → `ConsumeLater` 对象本身被发到 ack topic |
| MSG-F-15 | confirmed | `LocalMessageService.java:105-111` `removeConsumer` 按 consumer 身份移除；`:144-158` `subscribe` 按 consumer 身份去重（`findSubscription :115-120` 用 `==`）→ 同一 consumer 挂到多 topic 后取消其一即全部取消 |
| MSG-F-16 | confirmed | `PulsarConsumeTask.java:129-144`：按 `responses` 迭代，`it.next()` 取消息（`:131`）→ responses 多于消息抛 `NoSuchElementException` → `:146-151` 整批 negativeAck 重放（含已发 reply）；少于消息 → 尾部消息永不 ack |

## P1 — 可靠性与生命周期（31 行：F-17 至 F-40 共 24 条 + F-52 至 F-58 共 7 条）

| ID | 裁定 | 2026-10-04 锚点与依据 |
| --- | --- | --- |
| MSG-F-17 | confirmed | Kafka：`KafkaConsumeTask.java:180` `allowBatchConsume()` 仅作布尔门，批量大小由 `max.poll.records` 决定（`:160` 注入）；Pulsar：`PulsarConsumeTask.java:111` 无参 `batchReceive()`，`batchReceiveTimeout` 在 Pulsar 侧无任何读取（grep 实测） |
| MSG-F-18 | confirmed | `PulsarConsumerConfig.java:16,34-40` 声明 + 访问器；全仓 grep `MaxTotalReceiverQueueSizeAcrossPartitions` 除定义文件外零命中 |
| MSG-F-19 | confirmed | `PulsarClientConfig.java:15,25-31`；唯一读取点 `TestPulsarIoC.java:31`（test）；`PulsarMessageService.newTransaction`（`:161-167`）与 `PulsarConsumeTask.newConsumeContext`（`:190-196`）均不看它 |
| MSG-F-20 | confirmed | `IMessageConsumer.java:41-42` `default void onException(Exception e){}` 空实现，签名只有 Exception，拿不到 topic / offset / messageId |
| MSG-F-21 | confirmed | `KafkaMessageService.java:286-287`、`PulsarMessageService.java:243-244` 均传 `DEFAULT_CONSUME_ERROR_BACKOFF_MS`（各文件内常量 1000），无可配置入口 |
| MSG-F-22 | confirmed | 全 `nop-message` main 源码无 DLQ / 重试上限 / 失败终点实现（grep `deadLetter\|DLQ\|maxRetries\|maxDeliveries` 零命中） |
| MSG-F-23 | confirmed | 全 `nop-message` main 源码无 metrics / lag / health（grep `Meter\|micrometer\|lag\|Health` 零命中） |
| MSG-F-24 | confirmed | Pulsar：`PulsarMessageService.java:242` `Executors.newSingleThreadExecutor()` 无 ThreadFactory → 非 daemon、无名；Kafka：`:281-285` daemon 且命名，但 N 路 fan-out 线程同为 `"kafka-consume-" + groupId` 不可区分 |
| MSG-F-25 | confirmed | `KafkaMessageService.java:330-334` `cancel()` check-then-act（`:330` 判 `cancelled` → `:331` 置位非原子）；`nop-commons/.../Cancellable.java:40-46` `cancel(String)` 同构 |
| MSG-F-26 | confirmed | `KafkaMessageService.java:337` `cancel()` 从调用线程直接 `consumer.close()`，与消费线程 `poll()`（`KafkaConsumeTask.java:209`）竞态；`suspend()` `:364-368` / `resume()` `:374-378` 跨线程调 `pause/resume` |
| MSG-F-27 | confirmed | Kafka `destroy()`（`KafkaMessageService.java:100-122`）先订阅后 producer；Pulsar `destroy()`（`PulsarMessageService.java:90-128`）`:91-108` 先关全部 producer，`:110-116` 后取消订阅 → 关闭期在途发送打已关闭 producer |
| MSG-F-28 | confirmed | `KafkaMessageService.java:141`（producer props 末尾）与 `:168`（consumer props 末尾）`props.putAll(config.getExtraProps())` → 可静默覆盖 `group.id`（绕过 `resolveGroupId`）与全部调优键 |
| MSG-F-29 | confirmed | Pulsar：`PulsarMessageService.java:222-224` 仅在 `subscribeName != null` 时设 subscriptionName → Pulsar 生成随机订阅名，重启丢游标；Kafka：`:267-271` 同样缺失抛 `ERR_GROUP_ID_NOT_CONFIGURED` |
| MSG-F-30 | confirmed | `PulsarMessageService` 无 `initialized` 标志（Kafka 有，`:75`）；`destroy()` 后 `client=null`（`:121-127`）、producers 清空（`:98,103`）→ `sendAsync`（`:182-186`）→ `getProducer`（`:138-150`）返回 null → `producer.newMessage()` NPE |
| MSG-F-31 | confirmed | Kafka：`KafkaMessageService.java:224-233` `toWireValue` 对非 String data JSON 编码；Pulsar：`PulsarHelper.java:99` `builder.value(message.getData())` 直接塞 STRING schema → 非 String 发送期序列化失败 |
| MSG-F-32 | confirmed | `MessageSubscribeOptions.java:39-40` javadoc「exclusive模式下会忽略这个参数」；Pulsar `doSubscribe`（`PulsarMessageService.java:212-247`）不检查 `concurrency` 与 `subscriptionType` 组合 → Exclusive + concurrency>1 时 `builder.subscribe()` 抛错（Pulsar broker 侧拒绝同 subscriptionName 多 Exclusive consumer） |
| MSG-F-33 | confirmed | `KafkaHelper.java:56-61` 先设框架头（topic/partition/offset/eventTime），`:64-69` 再拷贝上游 headers → 上游同名头覆盖框架头；`PulsarHelper.java:30-59` 同序（`:34-53` 框架头，`:56-59` properties 覆盖） |
| MSG-F-34 | confirmed | `KafkaConsumerConfig.java:13-21` javadoc 称 groupId 在 `init()` 校验；实际 `init()`（`KafkaMessageService.java:85-97`）只校验 bootstrapServers，groupId 在 `doSubscribe` `:267-271` 校验 |
| MSG-F-35 | confirmed | `nop-stream/nop-stream-runtime/.../StreamTopicNaming.java:55` 声明 `LEGAL_TOPIC_PATTERN`；全仓 grep 零引用（仅声明行自身） |
| MSG-F-36 | confirmed | `PulsarConsumeTask.java:75-77` `schedule()`；全仓 grep `.schedule()` 零调用方 |
| MSG-F-37 | confirmed | `MultiMessageSubscription.java:20-27` `allMatch` 空列表返回 true → 构造窗口内 `isSuspended`/`isCancelled` 误报；`:13-16` 构造器直接持有外部可变列表（Kafka `KafkaMessageService.java:253-258` 传入 live list），无同步 |
| MSG-F-38 | confirmed | `ReflectionMessageSubscriptionRegistrar.java:47-56` `register()` 循环注册无 try/finally；`:84-88` `registerListener` 中 `subscriber.subscribe(...)` 抛错时已建订阅留在 `this.subscriptions` 但 bean 创建失败，`@PreDestroy destroy()`（`:127-139`）不被调用 → 泄漏 consumer 与线程 |
| MSG-F-39 | confirmed | `KafkaMessageService.java:208` `prod.send(record, callback)`：`KafkaProducer.send` 在 `max.block.ms` 耗尽时同步抛 `TimeoutException`，`sendAsync`（`:182-206`）无 try/catch → 直接抛而非返回失败 `CompletionStage`，违反 `IMessageSender.send` 的 `FutureHelper.syncGet` 契约。现有测试全部 mock producer.send 故不可见 |
| MSG-F-40 | confirmed | `KafkaErrors.java:14` `PREFIX = "nop.message.kafka."`、`PulsarErrors.java:11` 同构；`MessageCoreErrors.java` 为空接口（`nop-message-core`） |
| MSG-F-52 | confirmed | `PulsarConsumeTask.java:123`（batch 每批一次）、`:162`（单条每消息一次）`newConsumeContext()`；`:190-196` 每次 `service.newTransaction(timeout)` 建 broker 事务 → 每消息一次 broker 往返，粒度不一致。`PulsarMessageService.java:161-167` |
| MSG-F-53 | confirmed | Kafka 逐次 WARN：`KafkaMessageService.java:200-205`（delay，每次发送）、`:242-245`（subscriptionType，每次订阅）、`KafkaConsumeTask.java:141-143`（未知 seek mode）；Pulsar main 源码 `LOG.warn` 计数 0（grep 实测）→ 第三态缺失或打爆，无一处 warn-once |
| MSG-F-54 | confirmed | `PulsarConsumeTask.java:102-104` `stop()` 只翻转 `active`；`:155` `receive()` 无超时阻塞、无 wakeup 等价物 → 下一条消息到达前 `stop()` 空操作。Kafka 对照：`KafkaConsumeTask.java:170` `consumer.wakeup()` |
| MSG-F-55 | confirmed | 六个错误码零引用（grep 实测）：`KafkaErrors.java:30,34,42`（`ERR_KAFKA_CONSUMER_CREATE_FAILED` / `ERR_KAFKA_SERIALIZATION_FAILED` / `ERR_KAFKA_SEND_FAILED`）、`PulsarErrors.java:18,21,24`（三个 `ERR_PULSAR_*_CREATE_FAILED`） |
| MSG-F-56 | confirmed | `LocalMessageService.java:144-158` `subscribe` 只读 topic 与 listener，`MessageSubscribeOptions` 的任何字段零读取 |
| MSG-F-57 | confirmed | `PulsarConsumeTask.java:193` `getTransactionTimeout()` 缺省 0；`PulsarMessageService.java:163-164` `if (timeout > 0)` 跳过 `withTransactionTimeout` → 回落 broker 默认，无 WARN |
| MSG-F-58 | watch-only | 单条路径两道阻断在位：`KafkaConsumeTask.java:248` 失败记录 `throw` 跳出循环、`:252` `anyConsumeLater` 守卫跳过 `commitSync` → 跨分区越界提交当前不可达。陷阱：`KafkaConsumerConfig.java:30` 允许 `enableAutoCommit=true`（`KafkaMessageService.java:154-155` 接线为 ENABLE_AUTO_COMMIT_CONFIG），届时 auto-commit 按内部周期提交任意 position，两道阻断同时失效。归 WI11 一并处置 |

## P2 — 契约与产品化（11 条）

| ID | 裁定 | 2026-10-04 锚点与依据 |
| --- | --- | --- |
| MSG-F-41 | confirmed | `nop-bom/pom.xml`：`:693` nop-message-core、`:700` nop-message-pulsar、`:706` nop-message-debezium 在册；nop-message-kafka / nop-message-model / nop-message-codec 三个条目缺失 |
| MSG-F-42 | confirmed | `nop-message-model/src/main/java/io/nop/message/model/` 仅 `MessageModel.java`、`MessageFieldModel.java` 两个空壳类；`nop-message-codec` 无 `src/` 目录 |
| MSG-F-43 | confirmed | `docs-for-ai/` 中提及 nop-message 的文件恰 4 个：`03-modules/reusable-modules-overview.md`、`03-modules/nop-ai-gateway.md`、`01-repo-map/module-groups.md`、`04-reference/source-anchors.md`，无 owner doc |
| MSG-F-44 | confirmed | `nop-stream/nop-stream-runtime/src/test/.../TestDataPlaneKafkaBackendE2E.java` 与 `TestDataPlanePulsarBackendE2E.java` 是仅有的 broker 级测试，且属 nop-stream 数据面，消息层自身无 E2E |
| MSG-F-45 | confirmed | `MessageSendOptions.sendTimeout` 零消费：grep `getSendTimeout`（MessageSendOptions 上）仅 `MessageRpcClient.java:81` 写入 `options.setSendTimeout(timeout)`，无任何读取方；`cancelToken` 仅 `RpcMessageSender.java:52` 消费 |
| MSG-F-46 | confirmed | `TestKafkaConsumeTask.java:154-168` `seekToBeginHonoredNotStubbed`：`when(consumer.assignment()).thenReturn(assignment)` 用 mock 造出非空 assignment，把 MSG-F-01 认证为正确行为 |
| MSG-F-47 | confirmed | SPI 回归面实测：src/main 命中 11 文件（9 非后端 + 2 后端）、src/test 命中 23 文件。`DelegateMessageService.java` 位于 `nop-api-core`；`DelayedMessageService` 在 `nop-benchmark/nop-benchmark-stream/.../RemoteTransportWriteBench.java:124`（内部类，`static final class ... implements IMessageService`）。注意 roadmap 硬约束的「11 个主源码实现」清单字面混入 2 个 test 实现、不含两后端，与 src/main 的 11 是总数相同但成员不同的集合——审计按实际构成登记 |
| MSG-F-48 | confirmed | Pulsar 设 `nop-id`（`PulsarHelper.java:34`）、`nop-publish-time`（`:48`）、`nop-sequence-id`（`:53`）；Kafka `buildApiMessage`（`KafkaHelper.java:46-73`）不设其中任何一个 → 依赖 `nop-id` 去重的消费者在 Kafka 侧得 null |
| MSG-F-49 | confirmed | `nop-message-kafka/pom.xml` 依赖仅 nop-core + kafka-clients（+slf4j/test）；`nop-message-pulsar/pom.xml` 仅 nop-core + pulsar-client-original → 两后端均不依赖 `nop-message-core`，共享契约测试需改模块图 |
| MSG-F-50 | confirmed | 四义实测：Kafka 组内成员（`concurrency` → N 个 doSubscribe）；Pulsar 同订阅多 consumer（`:196-208`）；Local 完全忽略（`LocalMessageService.java:144-158` 无 concurrency 读取）；SysDao 每 JVM 独立轮询（`SysDaoMessageService.java:58`） |
| MSG-F-59 | confirmed | `TopicMessage.java:15-31` 无 equals/hashCode、仅带参构造器（`@DataBean` 批量载荷）；`MessageSendOptions.java:26` `cancelToken`（`ICancelToken` 接口类型）为 `@DataBean` 字段 → 无人兑现的可写 YAML 键 |

## 平台先例（1 条）

| ID | 裁定 | 说明 |
| --- | --- | --- |
| MSG-F-51 | confirmed（先例，非缺陷） | `nop-kernel/nop-xlang/.../backend/` 的 `EvalBackendCapability` / `EvalBackendRegistry` / `EvalBackendRouter` 先例在位。roadmap 已明确三点不可类比（IoC 构造无注册点、EvalRouter 自身含静默分支、互斥槽位 vs 特性集），仅「声明集合 + 注册期拒绝空集」可迁移，作为 WI6 的落地模板 |

## SPI 与实现清单（WI2 输入）

### SPI 类清单

- `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/message/`：19 文件。核心接口：`IMessageService`（空聚合，extends sender+subscriber）、`IMessageSender`（1 抽象方法 `sendAsync` + 4 default）、`IMessageSubscriber`（1 抽象方法 `subscribe` + `getAckTopic` default）、`IMessageConsumer`（1 抽象方法 `onMessage` + `onMessageBatch`/`onException` default）、`IMessageConsumeContext`（extends IMessageSender + `getCancelToken` default）、`IMessageSubscription`（5 方法）。值类型：`Acknowledge`、`ConsumeLater`、`TopicMessage`、`SeekMode`（4 值）、`SubscriptionType`（4 值）、`MessageSendOptions`（delay/sendTimeout/cancelToken）、`MessageSubscribeOptions`（11 字段）、`MessageSubscriptionConfig`、`MultiMessageSubscription`、`DelegateMessageService`/`DelegateMessageSender`、`IMessageSource`、`ISpecialMessageProcessor`。
- `io/nop/api/core/annotations/message/`：2 注解（`MessageListener`、`MessageSubscription`）。

### IMessageService 实现清单（实测 2026-10-04）

`grep -rln "implements IMessageService" --include="*.java"`：src/main 11 文件，src/test 23 文件。

| # | 实现 | 模块 | 位置 |
| --- | --- | --- | --- |
| 1 | LocalMessageService | nop-message-core | `io/nop/message/core/local/LocalMessageService.java` |
| 2 | SysDaoMessageService | nop-sys-dao | `io/nop/sys/dao/message/SysDaoMessageService.java:58` |
| 3 | LettucePubSubService | nop-nosql-lettuce | — |
| 4 | MqttServerMessageService | nop-vertx-mqtt-server | — |
| 5 | TdMessageService | nop-orm-tdengine | — |
| 6 | DBMessageService | nop-orm | — |
| 7 | DataPlaneMessageServiceAdapter | nop-stream-runtime | — |
| 8 | DelegateMessageService | nop-api-core（Protected Area） | `io/nop/api/core/message/DelegateMessageService.java` |
| 9 | DelayedMessageService（bench） | nop-benchmark-stream | `RemoteTransportWriteBench.java:124` 内部类 |
| 10 | KafkaMessageService | nop-message-kafka | `io/nop/message/kafka/KafkaMessageService.java` |
| 11 | PulsarMessageService | nop-message-pulsar | `io/nop/message/pulsar/PulsarMessageService.java` |
| 12 | MockMessageService | src/test | — |
| 13 | PollingJdbcMessageService | src/test（nop-stream-runtime） | — |

注：roadmap 硬约束的「11 个主源码实现」清单 = 上表 1-9 + 12-13（不含 10/11 两后端，另列入 Kafka / Pulsar），是历史形成的口径混合；SPI 变更回归面按上表 13 个命名实现 + 测试侧实现全量执行。

### 四模块结构与行数（main）

| 模块 | main 行数 | 核心类 |
| --- | --- | --- |
| nop-message-core | ~1250（含 Local/Registrar/常量） | LocalMessageService、ReflectionMessageSubscriptionRegistrar |
| nop-message-kafka | 1188 | KafkaMessageService、KafkaConsumeTask、KafkaHelper、3 config、KafkaErrors |
| nop-message-pulsar | 813 | PulsarMessageService、PulsarConsumeTask、PulsarHelper、3 config、PulsarErrors |
| nop-message-debezium | 2227 | DebeziumMessageSource、engine 三件套、NopStreamOffsetBackingStore |

另有空壳模块 nop-message-model（2 个空类）、nop-message-codec（无 src），属 WI31 处置。

### 选项字段处置现状摘要（WI2 矩阵的行）

- `MessageSubscribeOptions` 11 字段 × 三后端（Kafka / Pulsar / Local）：逐字段处置现状为「生效 / 静默忽略 / 逐次 WARN / 无处置」混杂——Kafka 侧 `subscriptionType` 逐次 WARN、`seekMode` 系全部 no-op（F-01）、`transactional` 零处置（F-13）；Pulsar 侧 seek 系空方法体（F-03）、`batchReceiveTimeout`/`batchReceiveCount` 零读取（F-17）、`transactionTimeout=0` 静默回落（F-57）；Local 侧全部零读取（F-56）。详见 F-01/F-03/F-05/F-13/F-17/F-32/F-56/F-57 各行。
- `MessageSendOptions` 3 字段：`delay` Kafka WARN+忽略 / Pulsar 生效（`deliverAfter`）；`sendTimeout` 全后端零消费（F-45）；`cancelToken` 仅 RpcMessageSender 消费（F-45）。
- `onMessage` 五种返回值：null / CompletionStage / ConsumeLater / Acknowledge / 其他非 null——两后端 + Local 的分派实现各有一个编号级缺陷（F-09 / F-14 / F-16），见 P0 表。

## 附录 — 新发现（roadmap 未覆盖）

| ID | 级别 | 发现 | 锚点 | 建议 |
| --- | --- | --- | --- | --- |
| MSG-NEW-01 | watch-only | `PulsarConsumeTask.runTask` 对 `config.getOptions()` 无 null 守卫即调 `allowBatchConsume()`；Kafka 侧有守卫（`KafkaConsumeTask.java:180`）。当前不可达（两后端 `subscribe()` 均对 null options 兜底 new），但任何绕过 `subscribe` 直接构造 task 的新代码会 NPE | `PulsarConsumeTask.java:82` | WI29 或相关 plan 顺手加守卫 |
| MSG-NEW-02 | watch-only | `KafkaMessageService.getProducer` 未初始化时抛 `ERR_KAFKA_PRODUCER_CREATE_FAILED`，错误码语义是「创建失败」而非「未初始化」，与 Pulsar 侧同场景（直接 NPE，F-30）均属错误报告失真 | `KafkaMessageService.java:173-178` | WI26 错误码清理时一并裁定 |
| MSG-NEW-03 | confirmed（WI9 顺带修复） | `PulsarMessageService.buildProducer` 从不设置 topic（computeIfAbsent lambda 丢弃 topic key），Pulsar 2.8 真客户端 `ProducerBuilderImpl.create()` 必抛 IllegalArgumentException——即使 F-04 修复，reply 在真实 broker 上仍发不出去。roadmap 审查轮未发现，WI9 执行期对抗审查发现并顺带修复（buildProducer(topic, schema) + getProducer 按需创建 + defaultProducer 字段移除） | `PulsarMessageService.java`（WI9 前 :162-174） | 已由 WI9 修复 |

两条均为 watch-only，不阻塞、不新增工作项；按 roadmap Rules 在相关 WI 的 plan 中顺手处置，处置结果回写本文档。

## Conclusion

- 三态裁定汇总（59 编号口径）：58 confirmed（57 缺陷 + F-51 先例）+ 1 watch-only（F-58）+ 0 already-correct；另有 2 条 roadmap 未覆盖的新登记 watch-only（附录 MSG-NEW-01、MSG-NEW-02）。roadmap 判定无推翻，Phase A 后续工作项可直接引用本文档锚点。
- 被否决的处理方式：无（本项为纯复核，未引入方案取舍）。
- 后续工作：WI2 依据「选项字段处置现状摘要」展开能力矩阵；WI7-WI17 修复以本文档 P0/P1 表为锚点依据。

## Open Questions

- [ ] 无（复核过程产生的两个 watch-only 已在附录登记）

## References

- `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（59 编号的原始判定与 D1-D11 裁定建议）
- `missions/nop-message-encapsulation.json`（mission 配置与验证命令）
- `docs-for-ai/02-core-guides/error-handling.md`（错误码前缀基线）
