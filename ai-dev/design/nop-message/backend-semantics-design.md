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
5. **D9（错误码前缀）**：迁移至 `nop.err.message.kafka.*` / `nop.err.message.pulsar.*`，与平台 `nop.err.[模块].[子域].[错误]` 规范一致；旧前缀无任何 i18n/properties 引用，直接迁移（加性体现在：迁移提交内同步核对零引用，不做双前缀共存期）。
6. **D10（空壳模块）**：立即删除 `nop-message-model`（两个空类，无任何依赖方）；`nop-message-codec` 降级为 Follow-up（待 D2 信封契约落地后出现非 String payload 实证需求再议）；`nop-bom/pom.xml` 补登模块条目随 WI31 执行。
7. **D11（事务语义）**：删除 `MessageSubscribeOptions.transactional` 与 `transactionTimeout` 两字段（SPI 破坏性变更，plan-first 承载于 WI14，遵守 message-contract D8 的弃用期规则）。理由：Kafka 侧零处置、Pulsar 侧每消息建事务（粒度错误 + 每消息 broker 往返），保留即违反「不得声明却从不读（或从未正确兑现）」约束。

## 二、背景与动机

两 broker 后端结构对称但行为不对等，且不对等的方式全部静默：Kafka 的 seek 在消费组分配完成前执行（assignment 为空集）形同 no-op；Pulsar 的 ack topic 对全名 topic 做前缀拼接必抛异常，请求-响应通道整体不可用；批量路径的失败边界与提交边界互相穿透。这些不是单点 bug，而是「SPI 声明了字段/语义、后端各自解释」的系统性缺口。本设计把六个行为域的显式承诺落成裁定，凡后端不能兑现的语义必须类型化拒绝而非静默。

## 三、核心设计

### 3.1 D3 seek 起点解析

- Kafka：seek 起点解析逻辑挂在 rebalance listener 的 onPartitionsAssigned 回调内，每次分区分配（含首次与再平衡）后对**新分配的分区**执行；INIT 系仅首次生效（按消费组位移状态判断），ALWAYS_SEEK_TO_END 每次分配都 seekToEnd。seekToMessage（按消息 id 定位）Kafka 无对应语义，类型化拒绝（错误码已在位）。
- Pulsar：INIT_SEEK 系经 `SubscriptionInitialPosition`（Earliest/Latest）精确兑现——该初始位仅当订阅光标不存在时生效，与 SeekMode javadoc「第一次从头/尾、以后从上次位置」逐字对应，并与 Kafka 的 committed 门控跨后端对称；ALWAYS_SEEK_TO_END 与 seekToTime 在 consumer 创建后同步 seek（Pulsar 无 rebalance 回调概念，解析于每次经 SPI subscribe 创建 consumer 时；客户端内部瞬断重连不会重新 seek）。seekToMessage 类型化拒绝。2.8 的 seek 不清空已缓冲 incoming 队列（真机窗口行为归 gated E2E）。
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
- **F-11/F-10 与 max.poll.interval.ms**：`maxPollIntervalMs` 可配置（默认 300000），自洽指引（须覆盖单批处理耗时 + ConsumeLater defer sleep）。真 broker 踢出时序验证归 WI33（其 charter 扩展含慢 handler 踢出场景）。
- **F-39 事实修正（WI17 审查）**：`max.block.ms` 耗尽时 KafkaProducer.send 走 callback（ApiException 族路由）而非同步抛；真实同步抛为 producer 已关闭/中断/未预期 runtime——已包装为失败 future，前置校验的同步 typed rejection 保持不变。

### 3.7 D11 事务语义

- `transactional` / `transactionTimeout` 字段进入 D8 弃用期（字段保留 YAML 兼容、@Deprecated 标注），**行为已删除**：任何后端读到非默认值即一次性 WARN「deprecated and ignored by D11」并忽略（WI14）。Pulsar 每消息建事务（F-52）与 timeout=0 静默回落（F-57）的实现路径一并移除；Kafka 零处置（F-13）由 warn-once 显式化。
- `ConsumeContext.commit()/rollback()` 为显式 no-op（at-least-once 基线）：消息层 ack 语义由返回值协议与各后端的 ack/提交机制承担，事务不作为消费语义的一部分。
- 未来若引入事务：按 message-contract D1 的 `TRANSACTION` 能力声明逐后端实现（当前无后端声明）。

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
