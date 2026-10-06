# nop-message 后端行为语义设计（D3 / D5 / D6 / D7 / D9 / D10 / D11）

**日期**：2026-10-04
**范围**：`nop-message-kafka`、`nop-message-pulsar`、`nop-message-core`（Local）、SPI 返回协议（nop-api-core）、模块图（nop-message-model / nop-message-codec）
**状态**：active
**负责人**：nop-message mission 执行 agent（ZCode session 71fd21c6），2026-10-04，依 roadmap `ai-dev/backlog/nop-message-encapsulation-roadmap.md` 前置裁定表生效。注：D11 为 roadmap v3 新增，Rules 落档清单未列，裁定归入本文档（事务语义属后端行为域）。

---

## 一、设计结论

1. **D3（Kafka seek 起点）**：挂 `ConsumerRebalanceListener.onPartitionsAssigned` 逐次重解析 seek 起点；一次性 seek（首 poll 前或首 poll 后单次执行）被拒绝——它无法表达 ALWAYS_SEEK_TO_END（每次重连都从最新开始）。降级路径：删除 seek API、起点由 `auto.offset.reset` 表达（仅能表达 INIT 系，不能表达 ALWAYS）。
2. **D5（AckMode）**：不引入容器级 ack 模式常量；`onMessage` 返回值协议是 ack 抽象且按消息粒度优。但协议缺两个信号，必须补齐（SPI 收窄式新增，plan-first）：**终止拒绝信号**（「这条是毒消息，不要重投」）与**部分提交信号**（「批量只提交到第 k 条」）。
3. **D6（DLQ 归属）**：三层——SPI 层只加终止拒绝信号与带上下文（topic/message/attempt/throwable）的失败回调；后端原生能力层（Pulsar `deadLetterPolicy`/`maxDeliveries`、Kafka 应用级重发布）；`nop-retry` 桥消费失败回调。SPI 配置不出现 `deadLetterTopic`/`maxRetries` 字段。
4. **D7（身份模型）**：全局 `groupId` 不再静默遮蔽 `subscribeName`；全局配置降级为命名空间前缀，生效消费组身份为 `${groupId}.${subscribeName}`；`subscribeName` 缺失时与 Kafka 现行为一致抛类型化错误（Pulsar 不再生成随机订阅名）。
5. **D9（错误码前缀，WI26 实现态）**：迁移完成——`nop.err.message.kafka.*` / `nop.err.message.pulsar.*` / `nop.err.message.core.*`，常量名零改名；旧前缀全仓零引用（i18n/properties/字面量断言均无）。六码处置：五码接线（Kafka consumer-create / send 失败双路径 [callback+同步 catch]、Pulsar client-create / producer-create / consumer-create[窄包，subscribe 失败与 seek 失败分离]）；`ERR_KAFKA_SERIALIZATION_FAILED` 删除（无自然挂点：encode 委托共享 codec 携带自身错误，单边接线制造跨后端不对称）。`MessageCoreErrors` 填充 `ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED`（日志语句必须引用常量 getErrorCode——防新码沦为 dead-define）。包装惯例：调用点包装（Pulsar client-create 的 wrap 在 doStart 调用处——方法体内包装会被覆写绕过）+ cause 链保留。
6. **D10（空壳模块）**：立即删除 `nop-message-model`（两个空类，无任何依赖方）；`nop-message-codec` 降级为 Follow-up（待 D2 信封契约落地后出现非 String payload 实证需求再议）；`nop-bom/pom.xml` 补登模块条目随 WI31 执行。
7. **D11（事务语义）**：删除 `MessageSubscribeOptions.transactional` 与 `transactionTimeout` 两字段（SPI 破坏性变更，plan-first 承载于 WI14，遵守 message-contract D8 的弃用期规则）。理由：Kafka 侧零处置、Pulsar 侧每消息建事务（粒度错误 + 每消息 broker 往返），保留即违反「不得声明却从不读（或从未正确兑现）」约束。

## 二、背景与动机

两 broker 后端结构对称但行为不对等，且不对等的方式全部静默：Kafka 的 seek 在消费组分配完成前执行（assignment 为空集）形同 no-op；Pulsar 的 ack topic 对全名 topic 做前缀拼接必抛异常，请求-响应通道整体不可用；批量路径的失败边界与提交边界互相穿透。这些不是单点 bug，而是「SPI 声明了字段/语义、后端各自解释」的系统性缺口。本设计把六个行为域的显式承诺落成裁定，凡后端不能兑现的语义必须类型化拒绝而非静默。

## 三、核心设计

### 3.1 D3 seek 起点解析

- Kafka：seek 起点解析逻辑挂在 rebalance listener 的 onPartitionsAssigned 回调内，每次分区分配（含首次与再平衡）后对**新分配的分区**执行；INIT 系仅首次生效（按消费组位移状态判断），ALWAYS_SEEK_TO_END 每次分配都 seekToEnd。seekToMessage（按消息 id 定位）Kafka 无对应语义，类型化拒绝（错误码已在位）。
- Pulsar：INIT_SEEK 系经 `SubscriptionInitialPosition`（Earliest/Latest）精确兑现——该初始位仅当订阅光标不存在时生效，与 SeekMode javadoc「第一次从头/尾、以后从上次位置」逐字对应，并与 Kafka 的 committed 门控跨后端对称；ALWAYS_SEEK_TO_END 与 seekToTime 在 consumer 创建后同步 seek（Pulsar 无 rebalance 回调概念，解析于每次经 SPI subscribe 创建 consumer 时；客户端内部瞬断重连不会重新 seek）。seekToMessage 类型化拒绝。**WI33 真机实证**：INIT_SEEK_TO_BEGIN（新订阅名在存量 backlog 上从头收）与 ALWAYS_SEEK_TO_END（跳过订阅前 backlog、只收增量）均经 Testcontainers E2E 验证；「2.8 的 seek 不清空已缓冲 incoming 队列」的微观窗口观测改归 follow-up（时序窗口非确定性，不改用户契约，见 plan 33 Deferred But Adjudicated）。
- 消费任务启动期的 seek 抛错（如 seekToMessage）不得被消费线程吞掉，必须传播到 `subscribe` 调用方。

### 3.2 D5 返回协议与两个新信号

- 五种返回值语义保持（null=ack；CompletionStage=异步完成后 ack；ConsumeLater=延迟重投且 delay 必须被兑现；Acknowledge=ack+reply；其他非 null=ack+reply）。
- 新增终止拒绝信号：消费者可声明「本消息处理失败且不可重投」，后端必须 ack（或转死信路径）而不再重投——这是 D6 失败终点的协议前提。
- 新增部分提交信号：批量消费时消费者可表达「已成功到第 k 条」，后端的提交边界不得越过该位置——这是修复「整批成败或整批丢失」二选一的协议前提。
- 两信号以收窄式新增落入 SPI（default 方法/返回值包装），不改变既有返回值语义。

### 3.3 D6 三层 DLQ 与失败终点

- 第一层（SPI）：带 topic/message/attempt/throwable 的失败回调替换现 `onException(Exception)` 空签名；默认实现按 error-handling 规范记录完整上下文 WARN（留证后丢弃）。回调语义含终止拒绝（3.2 信号）。
- 第二层（后端原生）：Pulsar 接线 `deadLetterPolicy` + `maxDeliveries` 进 consumer 配置；Kafka 无原生 DLQ，提供应用级重发布（消费失败终点时向死信 topic 发布）按需启用。
- 第三层（nop-retry）：`IRetryEngine` 桥消费第一层失败回调；重试策略统一由 nop-retry 承载，禁止消息层自建重试机制。
- 无 DLQ substrate 的后端（Local/Redis/MQTT/TDengine/DB）：仅第一层回调，终止拒绝后消息丢弃（留证），不承担后端 DLQ 义务。
- 实现口径（WI16）：`IMessageConsumer.onConsumeFailure(ConsumeFailure)` 为框架唯一派发点（ConsumeFailure=topic/location/message/attempt/throwable；location 为 `topic-partition@offset` 或 Pulsar messageId 串）；默认实现先 WARN 留证再桥接旧 `onException`（@Deprecated，兼容旧覆写）；派发处防护（用户回调抛异常不杀消费线程）；attempt 为同 location 连续失败计数（成功即清零；跨重启语义归 WI22）。

### 3.3.1 DLQ substrate 实现口径（WI21）

- **Pulsar 原生层（第二层）**：`PulsarConsumerConfig.deadLetterMaxRedeliverCount`（0=off）+ `deadLetterTopic`（null=客户端派生 `<topic>-<sub>-DLQ`）→ `ConsumerBuilder.deadLetterPolicy(...)`。**语义（2.8.0 WI33 真机实证更正取代原静态裁定；4.0.14 plan 36 复核通过，机制不变）**：重投计数在接收侧追踪；消息以 `redeliveryCount >= maxRedeliverCount` 被接收时客户端登记入 DLQ 追踪表，**分流发生在该消息随后被 negative-acknowledge 时**——客户端在 `getRedeliveryMessageIdData` 管道拦截重投请求，改道发布 DLQ 副本并代为 ack 原消息；**plain ack 永不改道**。`redeliverUnacknowledgedMessages` 仅 Shared/Key_Shared 携带计数。**推论**：(a) WI22 失败终点在策略已配置且阈值达位时改走 nack（`PulsarConsumeTask.runFailureEndpoint`，WI33 F-DLQ——终结 ack 会使策略成为静默 no-op，为真机 E2E 抓出的 live defect）；配置 `maxConsumeAttempts == deadLetterMaxRedeliverCount` 使终点首次触发即达分流阈值；(b) 非 Shared 订阅配置 DLQ 时一次性 WARN（`dlq-subscription-type`），效果为 no-op；(c) 真机投递已由 Testcontainers E2E 实证（Shared 订阅 + nack 链路，DLQ topic 收到原文；2.8.0 WI33 与 4.0.14 plan 36 双版本验证）；(d) **4.0 边界（plan 36 字节码裁定 + javadoc 声明）**：`redeliveryCount` 严格大于 `maxRedeliverCount` 的消息（ack 超时/断连重连/两 cap 错配场景）不再投递给 handler、也不分流，弹回 broker 直至某次 nack——保持 `maxConsumeAttempts == deadLetterMaxRedeliverCount` 使 endpoint 在阈值处 nack 即可避开。`redeliverUnacknowledgedMessages` 仅 Shared/Key_Shared 携带计数为 broker 侧行为（4.0 是否放开未单独验证，E2E 均用 Shared）。
- **Kafka 应用级重发布（第二层，按需启用）**：`KafkaConsumerConfig.deadLetterTopic`（null=off）经包私有 `resolveDeadLetterTopic(topic)` 读取（内含自环守卫：dlq==topic 时一次性 WARN 并按 off 处理，防 terminate 无限自环）。`TerminateConsume` 失败终点在 commit 前经 `sendAsync(dlq, apiMessage, null)` + `FutureHelper.syncGet` 同步重发布——DLQ 写 durable 先于提交；发送失败透传 adapted 异常走既有失败路径重投（不加新错误码）；批量路径失败由 WI11 的 progress 回退重投。逐条 sync 发送计入 `max.poll.interval.ms` 预算；DLQ 发送持续失败的消费停摆属 at-least-once 语义，由 WI22 上限治理（当前无界，显式声明）。已知残留：DLQ 已写但后续 `commitSync` 失败 → DLQ 重复（at-least-once，可接受）。Pulsar 的 `TerminateConsume` 不做重发布（分轨裁定：其 DLQ 走原生 deadLetterPolicy 路径——分流在 nack 达阈值时触发（WI33 F-DLQ 更正）；terminate 直接 ack 计数未超限，故进不了 DLQ）。
- **nop-retry 桥（第三层）**：`nop-message-core.MessageConsumeFailureBridge`——「失败事件 → 部署声明的重放入口」的**转发器**，非消息重放机制、零重试策略（policyId 仅部署显式提供时设置，否则引擎默认）。`IRetryEngine` 与重放目标（serviceName/serviceMethod）任一缺失 → WARN（`nop.err.message.bridge.not-configured`）零行为；payload 映射：`ApiRequest.data` = 失败 ApiMessage 原样，topic/location/attempt 经 request headers（`nop-msg-*`），idempotentId=location、groupId=topic；提交失败仅 LOG 不抛（辅助运维通道，不反噬消费）。模块图：`nop-message-core` 新增 `nop-retry-api` 依赖（api-only，无 engine 传递；BOM 登记归 WI31）。部署用法：消费者覆写 `onConsumeFailure` 调 `bridge.submit(failure)`——仅对具备消息重放 service 的部署有意义。
- **存量交互记录（不修，本计划登记）**：Pulsar 2.8 客户端 `doReconsumeLater` 无条件解引用 `deadLetterPolicy.getRetryLetterTopic()`——未配置 DLQ 时 `ConsumeLater` → `reconsumeLaterAsync` 每次触发客户端内部 NPE→ERROR 日志噪音→原地重投（at-least-once 语义不受影响）；配置 `deadLetterMaxRedeliverCount` 后噪音以 topic:null 形态延续。处置归 Pulsar 升级或 retryLetterTopic 语义实现（roadmap Follow-up）。

### 3.3.2 重试上限与退避（WI22）

- **attempt 查询面与计数点**：`ConsumeFailure.getAttempt()` 是唯一查询面。计数自增在已知 location 的失败点（Kafka 单条内层 catch / 批量外层 catch 的 progress 记录；Pulsar 单条 catch），`dispatchConsumeFailure` 只读取（peek）不再自增。批量归因收紧：progress 以 -1 起始——`onMessageBatch` 本身抛出/返回 null 属框架级失败，不 bump 不归属记录 0；仅响应处理期失败按 progress 记录计数。cap 作用域：仅消息级失败（有 location，含处理期 ack-topic 发送失败）；框架级失败（poll/commit）永不计数永不终点。
- **失败终点（上限驱动）**：`maxConsumeAttempts` 计重试次数，终点在第 **cap+1** 次失败触发（`>` 比较符；与 Pulsar 原生 `redeliveryCount >= maxRedeliverCount` 入表条件等值配置时精确衔接——本地 cap 命中的 ack 恰使计数入表、原生分流接管，无双写）。默认 **10**。**默认值取舍（显式裁定）**：有限预算保护暂态故障；超出预算（默认升级退避下约数分钟）的持续故障期间未提交消息被终点丢弃（DLQ off 时）——spring-kafka `DefaultErrorHandler`「有界重试后跳过」同款权衡；零丢失诉求设 `0`（unlimited，WI22 前行为）或配 DLQ。
  - Kafka 单条：DLQ 重发布（WI21 机制）成功 → WARN 留证 + 回调派发（attempt=封顶实际值）+ 清计数清上下文 → 不 seek 继续批内后续、随本轮 commit；**发布失败回退 seek+rethrow 正常失败路径**（无静默丢弃）。
  - Kafka 批量：终点记录**先 publish（可重试部分）成功后 `commitConverged`**（含终点 offset+1，catch 路径显式提交——否则 rebalance/重启后终点复活）；发布失败回退 `rewindFrom(progress)` 正常路径（**不提交**——commit 先行会让发布失败时 boundary 已持久化、回退失去意义，WI22 审计证据测试实测纠出）；成功则 `rewindFrom(progress+1)` + rethrow（尾部仍重投）。commit 丢失时的重启重投为 at-least-once 残留（如实声明）。批量终点轮因 rethrow 会产生**两次** onConsumeFailure 派发（终点级 attempt=cap 值 + run-loop 级 topic=null/attempt=0）——系「rethrow + 清上下文」裁定的必然后果，单条终点无此双重派发。
  - Pulsar 单条：有效 attempt = max(本地计数, `message.getRedeliveryCount()`)；终点 = 永久 ack（原生 policy 在该 ack 时分流）——**不做应用级 DLQ 重发布**（与原生分流双写）；ack 失败 WARN 不抛（broker 未 ack 重投兜底）。**Pulsar 批量不设应用级终点**（整批 nack 无逐条归因；原生 policy 覆盖 Shared 场景）——显式边界。
- **跨重启 attempt 语义（显式声明）**：attempt 为消费任务实例内的同 location 连续失败计数（内存态，重启/rebalance 归零）。Pulsar 的 cap 判定并入 broker redeliveryCount——**跨重启有效性仅 Shared/Key_Shared 订阅成立**（2.8 非 Shared 计数恒 0，退化为本地预算，与 Kafka 同款）；Kafka 无 broker 计数，重启后毒消息重获完整本地预算。不宣称 Kafka 侧跨重启预算。
- **退避（F-21）**：`consumeErrorBackoffMs`（默认 1000）/`consumeErrorBackoffMaxMs`（默认 30000）模块级可配置；循环退避 = `min(base × 2^n, max)`，**n 从 0 起（首错睡 base，与旧常量行为兼容）**；清零条件 = 本轮**成功派发至少一条消息**（空 poll/receive 超时轮中性——否则 Pulsar nack 重投间隙的空轮把升级打回 base，F-21 原样复发）；ConsumeLater 轮算派发成功轮。`computeBackoffMs` 纯函数两后端各留私有静态副本（两后端无共享模块，故意复制）。
- **注入通道**：backoff base 经构造参数（doSubscribe 读 config 传入）；`maxConsumeAttempts`/`consumeErrorBackoffMaxMs` 经 service 包私有访问器读 config，`defaultConsumerConfig == null` 时回落模块默认常量（**非 0**——直构服务保持生产语义；mock 服务 int 默认 0=off 使既有测试自然保持无限重投行为）。
- **既有不对称（记录现状，不本计划修）**：Pulsar 的 ConsumeLater 轮清 per-location 计数而 Kafka 不清——cap 语义下两后端对「延迟重投是否重置预算」回答不同；owner doc（WI34）与 3.3.2 同时声明。

### 3.4 D7 消费组身份模型

- 生效身份 = `${groupId}.${subscribeName}`（全局 groupId 配置时）；仅 subscribeName 时身份 = subscribeName；两者皆缺 = 类型化错误（ERR_GROUP_ID_NOT_CONFIGURED）。
- 该模型保证不同订阅不共享分区分配域（消除同组异构订阅的永久分区饥饿），同时保留全局前缀作为部署级命名空间。
- Pulsar 侧 subscribeName 缺失不再落随机订阅名，与 Kafka 同为类型化错误（消除重启丢游标）。
- Kafka 实现口径（WI13）：前缀分隔符为 `.`（`<groupId>.<subscribeName>`）；subscribeName 缺失（含已配置全局 groupId 时）抛 `ERR_GROUP_ID_NOT_CONFIGURED`。消费方迁移（MessageRpcServer / GraphQL 与 Reflection 注册器 / DataPlane 适配器注入 subscribeName）随 owner doc 给出模板。

### 3.5 D9 错误码前缀

- 全部 `KafkaErrors` / `PulsarErrors` 前缀迁 `nop.err.message.kafka.*` / `nop.err.message.pulsar.*`；`MessageCoreErrors`（空接口）按需填充或随模块处置。
- 六个从未引用的错误码（Kafka 3 个 + Pulsar 3 个）删除或接线（WI26 裁定逐个去向）。
- 错误描述保持英文，`NopException` + `ErrorCode.define` 形态不变。

### 3.6 D10 模块处置

- `nop-message-model`：立即删除（两个空类、零依赖方）。
- `nop-message-codec`：删除或降级为 Follow-up——裁定为降级 Follow-up：保留目录待 D2 契约落地后出现实证需求再实现或删除，期间其空壳状态在 BOM 与文档中显式声明（不注册任何 bean）。
- `nop-bom/pom.xml` 补登缺失条目（nop-message-kafka 必补；model/codec 按上述裁定处理）。

### 3.6.1 F-11 与 F-39 处置（WI17）

- **F-11（batchReceiveTimeout）**：裁定为**显式声明而非解耦**——SPI javadoc 声明语义映射依后端而定，Kafka 侧写明「该值用作 poll 阻塞时长并与 max.poll.interval.ms 相互约束」。被拒方案：解耦 wiring（独立 poll 时长配置）——登记优化候选。
- **F-11/F-10 与 max.poll.interval.ms**：`maxPollIntervalMs` 可配置（默认 300000），自洽指引（须覆盖单批处理耗时 + ConsumeLater defer sleep）。**WI33 真机实证**：慢 handler 踢出时序经 Testcontainers E2E 验证——handler 阻塞超过 `maxPollIntervalMs` 后客户端心跳线程主动 LeaveGroup，单条路径 commit 失败被吞（log only，无显式 seek rewind），该消息因 offset 未提交而按 `auto.offset.reset=earliest` 重投、消费组自愈续投。踢出预算须 > 空闲轮 poll 时长（pollTimeoutMs 默认 1000）。
- **F-39 事实修正（WI17 审查）**：`max.block.ms` 耗尽时 KafkaProducer.send 走 callback（ApiException 族路由）而非同步抛；真实同步抛为 producer 已关闭/中断/未预期 runtime——已包装为失败 future，前置校验的同步 typed rejection 保持不变。

### 3.7 D11 事务语义

- `transactional` / `transactionTimeout` 字段进入 D8 弃用期（字段保留 YAML 兼容、@Deprecated 标注），**行为已删除**：任何后端读到非默认值即一次性 WARN「deprecated and ignored by D11」并忽略（WI14）。Pulsar 每消息建事务（F-52）与 timeout=0 静默回落（F-57）的实现路径一并移除；Kafka 零处置（F-13）由 warn-once 显式化。
- `ConsumeContext.commit()/rollback()` 为显式 no-op（at-least-once 基线）：消息层 ack 语义由返回值协议与各后端的 ack/提交机制承担，事务不作为消费语义的一部分。
- 未来若引入事务：按 message-contract D1 的 `TRANSACTION` 能力声明逐后端实现（当前无后端声明）。

### 3.8 线程模型与有界关闭（WI19）

- **线程命名与 daemon**：两后端消费线程统一经 `NamedThreadFactory(prefix, daemon=true)` 创建。Kafka prefix `nop-msg-kafka-consume-<groupId>`；Pulsar prefix `nop-msg-pulsar-consume-<subscribeName>`（缺失时 fallback topic 名——缺失本身可被接受是 F-29/WI29 的缺陷，非本契约承诺）。factory 的 `-<poolNumber>-<threadNumber>` 后缀（JVM 级计数器）保证 fan-out 与多订阅线程互异；名称断言只允许前缀匹配，不允许断言完整名。禁止无 ThreadFactory 的 `Executors.newSingleThreadExecutor()`。
- **有界停止**：消费循环每轮等待有界（Kafka `poll(pollTimeoutMs)` + wakeup；Pulsar 单条 `receive(1s)`、批量 `batchReceiveAsync().get(1s)`）。`stop()` 只翻转 `active`；退出上界 = 一个等待轮 + 单批处理耗时，**不依赖 `consumer.close()` 抛异常解阻**。接收超时为常量 1000ms（对齐 Kafka poll 默认值），不是 SPI 选项字段——不可再引入「声明了但从不读」的配置键。
- **Pulsar 批量保留模式**：2.8 无 `batchReceive(timeout)` 重载，任务持有同一个 `batchReceiveAsync()` future 跨轮复等；禁止每轮超时后重新发起请求（会在客户端积压无人 ack 的弃请求）。future 异常完成（close/断连）时清空并上抛交 runTask 退避路径；中断时恢复中断标志后按 active 决定退出。stop 时遗留的未完成 future 其消息由 Pulsar close/重连未确认重投语义兜底（生产 stop 仅发生于 cancel 路径，其后必跟 close）。
- **LifeCycleSupport 统一**：两后端 service `extends LifeCycleSupport`，公开 `init()/destroy()`（beans.xml `init-method/destroy-method` 契约不变）一行委托 `start()/stop()`；真实逻辑在 `doStart()/doStop()`。Pulsar 的 `init()` 不再声明 `throws PulsarClientException`（doStart 无受检异常面）——client 创建失败经 `NopException.adapt` 包装为 `NopWrapException` 抛出（全仓无调用方依赖原受检类型）；`PulsarClient` 经 `createClient()` protected seam 注入（对齐 Kafka `createConsumer` 先例）。

### 3.8.1 关闭顺序与并发幂等（WI20）

- **destroy 顺序（F-27 最终裁定）**：两后端统一为 **subscriptions → producers → client**——关闭期在途发送打到的仍是活 producer；订阅取消内部等待消费线程终止后才返回。
- **close-after-termination（F-26）**：订阅取消路径为 stop → `executor.shutdown()` → `awaitTermination(5s)` →（超时 `shutdownNow()` + 再等 1s）→ 然后 `consumer.close()`。常规路径下 close 与 poll/处理结构性串行（happens-after 任务终止）。**残余窗口**：消费线程卡死在阻塞式 consumer 调用超过 6s（如 broker 不可达的长 poll/commit）时，兜底 close 在 WARN（`cancel-consume-thread-stuck`）后 best-effort 执行——该窗口自 WI20 前的常态收窄为病态尾窗。任务自身（`stop()`）永不 close consumer（WI19 钉定语义）。
- **cancel 幂等（F-25）**：`KafkaMessageSubscription` 用 `AtomicBoolean.compareAndSet` 守卫清理体恰一次；`Cancellable.cancel(String)` 置位收进 `synchronized(this)`——并发 cancel 单一 winner，`cancelReason` 归因确定；回调仍锁外执行，`tryRunCallbacks` 的锁内一次性消费保证不变。
- **suspend/resume（F-26 同族）**：Kafka 侧采用**消费线程状态重断言**——期望态（`volatile suspended`）由订阅线程置位；消费线程每轮循环头对全量 assignment 施 pause/resume（差异驱动），`onPartitionsAssigned` 对新分配分区重放挂起态（覆盖首分配前挂起与 rebalance 后挂起）。拒绝单槽令牌方案（suspend→resume 紧邻会丢 resume 致静默停流）。Pulsar 客户端 API 线程安全，pause/resume 直调保持。任务已停止时期望态不再施放（显式语义）。
- **getProducer 未激活守卫（F-30）**：守卫位于 `buildProducer` 真实实现内（`client == null` → `ERR_PULSAR_SERVICE_NOT_ACTIVE`）——放 `getProducer` 会破坏「override buildProducer 不 init」的测试注入形态。destroy 中途 producers 已 clear 而 client 尚未置空的窄窗内，底层 client 错误经 adapt 面世（非 NPE）。`init/destroy` 重入由 LifeCycleSupport 承担（allowRestart 默认 true）。

### 3.9 Local 后端语义（WI23）

- **投递模型**：in-JVM 广播——`send` 逐订阅同步调用（区别于 Kafka 组内竞争消费与 Pulsar 订阅模式）；同一 listener 重复订阅同一 topic = 两条独立订阅、各自收到一次投递（WI23 裁定：与 broker「每次 subscribe 一个独立订阅」对齐；`ReflectionMessageSubscriptionRegistrar` 同 listener 订多 topic 不受影响，同 topic 重复声明从静默去重变为重复投递——显式订阅的诚实结果）。
- **订阅独立化（F-15）**：每次 subscribe 新建 `Subscription` 实例；cancel 按实例身份移除（`remove(this)`）；scheduleRedelivery 的存在性检查用实例 `contains`。禁令：不得回退到按 consumer 身份查找（[A,B] 取消 B 误删 A 的 F-15 换形态复发）。
- **ConsumeLater**：定时器 per-subscription 延迟重投（非 broker 重投队列）；fire-time 重查 cancelled/suspended/实例在位；ack topic 永不收到 ConsumeLater（F-14，契约测试 `consumeLaterDefersRedeliveryWithoutAckReply` 钉定）。
- **suspend/cancel**：内存标志/注销，无 broker 状态；cancel 后 pending 重投放弃（契约行为非吞错）。
- **选项字段三态表**：SEND/SUBSCRIBE/ACK_REPLY 生效；DELAYED_SEND/SEND_TIMEOUT/BATCH_CONSUME/SEEK warn-once 忽略（Local 无对应传输）；transactional/transactionTimeout warn-once（D11）；subscribeName/concurrency/subscriptionType warn-once 忽略（WI23 残面收口——in-JVM 广播无此概念，对齐 D1 矩阵 Local 行；validator 因 SUBSCRIBE 能力放行、由后端 in-place warn-once 兜底）。
- **无 substrate 面**：DLQ/seek/batch/事务均无；TerminateConsume = WARN 留证后丢弃（`nop.message.local.terminate-consume`，对齐 broker 格式）；重试上限（WI22）不适用 Local（无 broker 重投循环，ConsumeLater 即显式延迟重投）。

### 3.10 批量消费契约（WI25）

- **onMessageBatch 为按记录语义（D5 b-prime 裁定落档）**：返回 List 与记录一一对应——第 i 个响应裁定第 i 条记录（null=ack、Acknowledge/非 null=ack+reply、ConsumeLater=延迟重投、TerminateConsume=终结、PartialCommit=前缀提交信号）；**非全批成败**（handler 抛异常走既有失败路径：Kafka rewind / Pulsar 整批 nack）。
- **arity 确定行为（F-16 消除）**：成对迭代 `n = min(responses, messages)`。responses 多于 messages → 多余响应 WARN 忽略（`batch-arity-extra-responses`，逐次；extras 中的信号类型不扫描——无记录可施加即无语义）；messages 多于 responses → 尾部 at-least-once 兜底（Pulsar：逐条 negativeAcknowledge；Kafka：rewindUndispatched 重投）+ WARN（`batch-arity-unhandled-tail`）。**豁免规则**：`PartialCommit`/`ConsumeLater` 早退分支是显式契约信号，自带边界/终点处理并豁免 arity WARN（Kafka 早退 return；Pulsar PartialCommit return 且不得改 break 共享尾部处理）。WARN 一律 post-loop 计算。既有差异登记：Pulsar 批量的 ConsumeLater 非早退（reconsumeLater 后继续循环），与 Kafka 的批终点语义不同义。
- **batchReceiveCount 接线（F-17）**：Kafka = `max.poll.records` 的 per-subscribe 覆盖（优先级链 extraProps < managed keys < per-subscribe option；纯上限，无延迟效应）；Pulsar = `BatchReceivePolicy(maxNumMessages=count, maxNumBytes/timeout=客户端默认保底)`——**gate + 100ms 兜底**（maxNumMessages-only 会让低吞吐 topic 攒满 count 才完成，延迟无界——审查 B-1 实测裁定）。两后端口径不对称（Kafka=cap / Pulsar=gate+兜底）为显式登记。`batchReceiveTimeout` 的 Pulsar policy 接线不做（WI17 F-11 裁定保持）。
- **11 主源码实现评估**：`onMessageBatch` 仅 SPI 默认实现（恒等长响应、永不触发 arity 分支）+ 两个 broker ConsumeTask 调用点，无其他覆写——本契约零波及。

### 3.11 concurrency 语义与多 JVM 部署边界（WI28）

- **四类后端 concurrency 语义矩阵**：

| 后端 | concurrency 语义 | 多 JVM 行为 |
| --- | --- | --- |
| Kafka | N 个独立 consumer 同消费组（分区级并行；N > 分区数时超出者空闲） | 消费组自动分区分摊（concurrency×JVM 数 ≤ 分区数为最优） |
| Pulsar | SINGLE_ACTIVE 钳制 1（WI27）；LOAD_BALANCED/ORDERED_BY_KEY N 个同订阅名 consumer（broker 分发）；均未配置+并发>1 → Shared（WI27） | Shared/Key_Shared 由 broker 跨 JVM 分发；Exclusive 全局仅 1 活跃 |
| Local | warn-once 忽略（in-JVM 广播到全部订阅者，无并行概念） | 仅单 JVM——广播不跨网络 |
| SysDao | 忽略（DB 轮询模型无多 consumer 概念） | **每 JVM 独立轮询**（天然多活，由 DB 行锁/状态机去重） |

- **nop-cluster 部署边界（owner doc 输入）**：多 JVM 部署时 Kafka 消费组自动分摊（无需 nop-cluster 协调）；Pulsar Shared 由 broker 原生分发；SysDao 每 JVM 独立轮询 + DB 状态机去重（不依赖 nop-cluster 选主）；Local 仅限单 JVM 或作为默认实现不可用于集群。广播 topic（`broadcast:` 前缀）在 Local 模型下仅达本 JVM 订阅者；跨 JVM 广播须使用 SysDao broadcast 或 broker 后端。
- **与 WI23/WI25/WI27 的关系**：Local warn-once（WI23/F-56）、Pulsar 钳制/映射/默认 Shared（WI27/F-32）、Kafka N consumer 行为不变（WI27）——本节为集中声明而非新行为。

### 3.12 真实 broker E2E 门控（WI33）

- **门控语义**（属性名三处一致声明：本节、owner doc `docs-for-ai/03-modules/nop-message.md`、两测试类 javadoc）：主门 = Docker daemon 可达（`DockerClientFactory.isDockerAvailable()`，不可达时 assume 可见 SKIP，不伪造通过）；退出口 = `-Dnop.test.message.e2e.kafka=false` / `-Dnop.test.message.e2e.pulsar=false` 强制跳过；镜像前缀 = `-Dnop.test.message.e2e.registry`（默认 `docker.m.daocloud.io/`，直连 Hub 环境设空串）。broker 镜像（plan 36 起）：`apache/kafka:4.3.1`（KRaft）+ kafka-clients 4.3.1、`apachepulsar/pulsar:4.0.14`（standalone）+ pulsar-client 4.0.14（历史：WI33 曾在 3.7.0/3.0.11 + 2.8.0 客户端上首证，plan 36 整体对齐最新 LTS/支持线——Pulsar 跟随 4.0 LTS 补丁线（active support 至 2026-10-21，security 至 2027-10-21），Kafka 跟随支持线最新补丁；客户端升级零 API 漂移，E2E 双栈全绿）。版本经 `testcontainers-bom` 2.0.5 单点（先例：nop-stream-connector-debezium）。
- **平台依赖裁定（WI33 执行期发现）**：quarkus-bom 导入把 `pulsar-common`/`pulsar-transaction-common`/`pulsar-client-admin-api` 管到 4.0.9，与 `pulsar-client-original` 2.8.0 混版在构造真实 PulsarClient 时即 `NoSuchMethodError`——已在 nop-dependencies 显式对齐至 `${pulsar.version}`。真实 client 构造路径只有 WI33 E2E 覆盖，mock 单测无法暴露此类混版缺陷。

## 四、拒绝了什么

- **D9 拒绝 (a) 保留 `nop.message.kafka.*` / `nop.message.pulsar.*` 前缀**：违反平台 `nop.err.[模块].[子域].[错误]` 规范（error-handling.md）；实测旧前缀无任何 i18n / properties 引用，迁移无兼容成本，双前缀共存期无收益。

- **D3 拒绝一次性 seek（首 poll 前或首 poll 后单次）**：表达不了 ALWAYS_SEEK_TO_END 的「每次重连」语义；现有实现注释自认只执行一次。
- **D3 拒绝删除 seek API 为主案**：`auto.offset.reset` 表达不了 ALWAYS；仅作降级路径保留。
- **D5 拒绝 AckMode 常量集**：需要先引入容器抽象（改骨架），且无法表达毒消息终止与部分提交；nop 的提交决策点在消费任务的消息分派处而非容器，返回值协议已按单条消息表达 ack 意图。
- **D6 拒绝 SPI 层 deadLetterTopic+maxRetries 字段**：5/9 后端无 DLQ substrate，字段必沦为「声明了但从不读」；且会制造第三个重试机制（nop-retry、SysDao IRetryPolicy 之外）。
- **D7 拒绝 (a) 去掉全局 groupId 与 (c) 维持现状**：(a) 丢失部署级命名空间；(c) 全局优先遮蔽 subscribeName 造成永久分区饥饿，是可用性风险。
- **D10 拒绝保留 nop-message-model 待实现**：两个空类无任何依赖方，保留即违反「禁止空壳」约束。
- **D11 拒绝 (a) 保留字段 + Kafka 抛错/Pulsar 修粒度**：两字段无任何后端能完整兑现，保留即继续违反自身 Hard constraint；(c) 仅修 Pulsar 粒度留下 Kafka 零处置的静默违约。

## 五、与已有设计的关系

- `message-contract-design.md`：D1 能力门禁决定本文档各「类型化拒绝」的挂接点；D8 弃用期规则约束 D11 的字段删除执行方式。
- Spring 对照（spring-kafka/spring-pulsar）中 AckMode、@RetryableTopic、CommonErrorHandler 的不采纳结论与本文档 D5/D6 一致且互为佐证。
- 错误码规范见 `docs-for-ai/02-core-guides/error-handling.md`；broker 切换机制（beans.xml + ioc:default）不变。
