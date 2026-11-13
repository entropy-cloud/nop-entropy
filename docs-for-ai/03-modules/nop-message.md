# nop-message 消息服务

## 概述

`nop-message` 提供统一的 `IMessageService` SPI，通过 beans.xml + `ioc:default="true"` 覆盖切换 broker 后端（Kafka / Pulsar / Local / SysDao 等）。SPI 骨架极简（1 抽象方法 + default 方法收窄式新增），能力差异通过 `MessageCapability` 声明 + `MessageOptionsValidator` 入口门禁管理。

## 模块图

| 模块 | 职责 |
| --- | --- |
| nop-api-core | SPI 接口 + 选项 bean + `MessageOptionsValidator` + `MessageValueCodec` |
| nop-message-core | Local 后端 + `MessageConsumeFailureBridge` + 契约测试骨架 |
| nop-message-kafka | Kafka 后端（KafkaMessageService + KafkaConsumeTask + Helper + 3 config bean） |
| nop-message-pulsar | Pulsar 后端（PulsarMessageService + PulsarConsumeTask + Helper + 3 config bean） |
| nop-message-debezium | Debezium CDC 集成（ChangeEvent + DebeziumMessageSource） |

## Broker 切换

beans.xml + `ioc:default="true"` 同 id 覆盖。例：切换到 Kafka：

```xml
<bean id="nopKafkaMessageService"
      class="io.nop.message.kafka.KafkaMessageService"
      ioc:default="true"
      init-method="init"
      destroy-method="destroy">
    <property name="config" ref="nopKafkaClientConfig"/>
    <property name="defaultProducerConfig" ref="nopKafkaProducerConfig"/>
    <property name="defaultConsumerConfig" ref="nopKafkaConsumerConfig"/>
</bean>
```

## 能力声明（D1）

每个后端声明 `getCapabilities()` + `MessageOptionsValidator` 在每个 `subscribe`/`sendAsync` 入口强制校验。选项字段三态处置：生效 / 类型化错误 / 一次性 WARN。

| 后端 | 能力集 | warn-once |
| --- | --- | --- |
| Kafka | SEND, SUBSCRIBE, SEND_TIMEOUT, BATCH_CONSUME, SEEK, ACK_REPLY, ENVELOPE_METADATA | DELAYED_SEND |
| Pulsar | SEND, SUBSCRIBE, SEND_TIMEOUT, BATCH_CONSUME, DELAYED_SEND, SEEK, ACK_REPLY, ENVELOPE_METADATA | — |
| Local | SEND, SUBSCRIBE, ACK_REPLY | DELAYED_SEND, SEND_TIMEOUT, BATCH_CONSUME, SEEK |
| SysDao | SEND, SUBSCRIBE, DELAYED_SEND, ACK_REPLY | 其余逐字段 warn-once |

## onMessage 返回值协议（D5）

| 返回值 | 语义 |
| --- | --- |
| null | ack（消息处理成功） |
| Acknowledge(reply) | ack + reply 发到 ack topic |
| 非 null（其他） | ack + reply |
| ConsumeLater(delay) | 延迟重投（delay 必须被兑现） |
| TerminateConsume(reason) | 毒消息终结（不再重投） |
| PartialCommit(k) | 批量前缀提交信号 |

## concurrency 语义（WI28 / design 3.11）

| 后端 | concurrency 语义 | 多 JVM |
| --- | --- | --- |
| Kafka | N 个独立 consumer 同消费组 | 消费组自动分区分摊 |
| Pulsar | SINGLE_ACTIVE 钳制 1；LOAD_BALANCED/ORDERED_BY_KEY N 个 | Shared 由 broker 分发 |
| Local | warn-once 忽略 | 仅单 JVM |
| SysDao | 忽略 | 每 JVM 独立轮询 |

## DLQ 与失败终点（D6 / design 3.3）

三层架构：SPI 失败回调（`onConsumeFailure`）+ 后端原生 DLQ（Pulsar `deadLetterPolicy` / Kafka 应用级重发布）+ nop-retry 桥。SPI 不出现 `deadLetterTopic`/`maxRetries` 字段。

**Local 面闭合（三层失败架构全后端连通）**：`LocalMessageService.invokeSubscription` 消费失败时在 `nop.message.consumer-failed` WARN 之外派发第一层 `onConsumeFailure`（`dispatchConsumeFailure`，对齐 `KafkaConsumeTask` 形态——回调自身抛异常不影响发送者/重投线程，attempt 固定 0）——`MessageConsumeFailureBridge` 在 Local 后端可收到失败事件，Local 不再是三层架构断链点（无 DLQ substrate 后端仅第一层回调，符合 design 3.3）。

**Local 重投立场（显式裁决，禁止按需猜测）**：异常终结本次投递、**不自动重投**——`ConsumeLater` 返回值是唯一重投路径（per-subscription 延迟队列显式信号）；失败回调/WARN/桥为可见性保底通道，不承担重投。无 broker 的 Local 不引入隐式 broker 重投循环（design §3.9 立场）。

## Debezium 订阅与毒事件 endpoint 契约（D12 §3.3.3）

`nop-message-debezium` 的 `DebeziumMessageSource`（Debezium 嵌入引擎的事件订阅宿主）订阅与交付契约：

- **毒事件两极同禁**：畸形事件/订阅者异常**不得炸流**（存活极性——异常逃逸进 `engine.run()` 即 CDC 流终态死亡、`isStarted` 撒谎），也**不得吞后静默推进 offset**（投递极性——事件对该订阅者永久丢失）。行为终裁 = **(a) 案四步**：跳过（不重抛进引擎线程）+ 计数 + 上报 + `ConsumeFailure` 派发（该事件 at-most-once，可见性保底）。裁定文本 = backend-semantics-design.md §3.3.3（D12，位于 ai-dev/design/nop-message/ 目录，按 docs-for-ai 边界规则不直接链接），本节为其使用知识投影。
- **毒事件 endpoint**：wrapper 屏障（`handleDebeziumEvent`，convert 与 consumer 调用统一纳入）与 `dispatchEvent` 订阅者分发失败分支共用**同一 endpoint**（禁分叉）；观测面 = WARN 键 `nop.message.debezium.poison-event`（topic/location/attempt/total）+ 失败计数 `getPoisonEventCount()`；convert 阶段 attempt 固定 0（M-1 裁定），订阅者粒度 attempt 连续失败计数、成功清零。
- **subscribe 失败回调重载**：`subscribe(action, failureHandler)` 接收 per-subscription 失败回调（毒事件第三通道）；`failureHandler=null` 为显式声明的两通道退化（WARN 键 `nop.message.debezium.poison-event.no-failure-handler` + 计数，不静默）；回调自身抛异常被守卫（`poison-event.failure-callback-throw` WARN），不反噬投递循环。
- **订阅生命周期（MA-05 三面归一 + WI23 注销身份）**：注册移至引擎启动成功之后（启动失败无幽灵订阅残留——异常上抛且无条目留存）；subscribe/cancel/stop 复合操作统一在 source monitor 内；stop 之后 subscribe typed 拒绝（`ERR_DEBEZIUM_ENGINE_START_FAILED` 族，不复活引擎）。订阅表为 **Subscription 身份集**（`Set<Subscription>`）——同一 action 引用重复订阅 = 两条独立订阅，各自 cancel 各自生效，cancel 旧句柄不影响现任订阅（不回退到按 consumer 身份查找）。
- **isStarted 诚实 + 终止可观测**：引擎终态死亡经实例级失败回调翻转 `started` 并可达注册的失败监听（静态 `FAILURE_LISTENERS` 连接器名单槽协议零触碰——实例回调不覆写宿主任务注册）；`isEngineTerminated()` / 停止契约「已终止」语义见 `03-modules/nop-stream-connectors.md`「Debezium 停止契约终止性」节。


## Offset REGISTRY 生命周期契约（plan 2558 / 裁定 B，registry-lifecycle-contract.md 投影）

- **stop 配对释放单点**：`DebeziumEngineWrapper.stop()` finally 承担 REGISTRY 桶释放 + 失败监听器 remove-by-name 兜底（终止校验失败/closeFailure typed 抛出时亦释放）；`DebeziumMessageSource.stop()` 不释放（无双计）。
- **条件移除 + 绑定代**：`clearConnector` 仅在无活绑定时移除——活绑定 typed 拒绝；桶 holder 携带绑定代与恢复声明窗（任一绑定 start 即封窗），窗外 `setOffsets` typed 拒绝（REPLACE 契约窗内维持）；孤儿写入/读取全数据路径 typed 拒绝（fresh generation 独立空桶，stale-resume 废除）。
- **TTL/evictIdle 被拒**：生命周期面由配对释放+条件移除覆盖（2503 裁定②），不引入 TTL。
- **驻留可观测**：`residencySnapshot()` 驻留诊断指标。
- **静态表纪律**：FAILURE_LISTENERS 类静态表必须有 stop 配对回收——注册 REPLACE 覆盖存活不同实例时 WARN 留痕；条件两参移除拒绝他者句柄。

## 消费等待与生命周期窗口契约（plan 2558 / 裁定 A·D·H·I·K·L）

**consumeAwaitTimeoutMs（裁定 A）**：Kafka/Pulsar 消费等待点对 handler 返回的 future 有界等待（键 `KafkaConsumerConfig.consumeAwaitTimeoutMs` 缺省 60000、`PulsarConsumerConfig.consumeAwaitTimeoutMs` 缺省 30000，javadoc 对齐各后端 maxPollIntervalMs/ackTimeout 自洽指引）。等待对象须 `CompletableFuture`——非 CF 形态 typed `ERR_KAFKA/PULSAR_CONSUME_AWAIT_NOT_ASYNC`，不静默降级为无界等待。超时 = 专用分支（Kafka seek 重投 / Pulsar nack + WARN），**attempt=0 语义：不参与 maxConsumeAttempts/poison 预算**（挂死 handler 无限重投为显式接受的可观测面，DLQ 预算保留给真实 handler 失败）。

**ConsumeLater 预算（裁定 K）**：键 `consumeLaterMinDelayMs`（缺省 1000）/`consumeLaterMaxDefers`（缺省 10，两后端同键族）——请求 delay 取 `max(delay, minDelay)`；连续 defer 超预算 WARN 后退回 poll 主路径（Kafka 跳过 sleep 仍 seek / Pulsar nack 回 broker）；只有 finalizing 轮重置计数。三参数联动声明：`consumeAwaitTimeoutMs` < `maxPollIntervalMs`（Kafka）且 producer `max.block.ms` 低于组踢阈值（MA-07 批 reply 预算）。

**destroy/stop 窗口（裁定 D）**：Pulsar `checkIsActive` 前置于 `newConsumerBuilder`；producer 创建与 doStop 排空共享服务级锁（TOCTOU 关闭，doStop 取消在建占位）；Kafka `producer` volatile + `getProducer` 单次局部读 + doSubscribe 外层 catch（资源排空）。

**send 失败信封与 NOT_ACTIVE 残差分叉（裁定 H，显式登记）**：Pulsar `sendPulsarAsync` 全段（含 getProducer）失败 → `ERR_PULSAR_SEND_FAILED` 信封 failed-future（原始异常保留为 cause，永不同步抛）；Kafka 同段形态镜像 `ERR_KAFKA_SEND_FAILED`。**残差分叉**：NOT_ACTIVE 面 Kafka = getProducer 外置同步抛 typed `ERR_KAFKA_PRODUCER_CREATE_FAILED`；Pulsar = failed-future 信封（cause 链携带 `ERR_PULSAR_SERVICE_NOT_ACTIVE`）——跨后端调用方不可假设两后端 NOT_ACTIVE 同形态（统一留后续契约批）。

**producer 建连（裁定 L，与 C1-004 同站点异机制互不遮蔽）**：Pulsar `getProducer` 占位 + `createAsync` + 有界等待（sendTimeout 下限 1s）；失败不缓存负面（占位自移除重试，排队者共享一次建连不串行放大）。C1-004（destroy 窗口 typed 拒绝）与本机制（blocked-connect 窗口超时）组合：destroy 于挂死建连期间取消占位、等待者 typed 醒来——互不遮蔽。

**Pulsar 批排序契约（裁定 I）**：per-message **reply-first-then-ack** 为注册序契约（非 happens-before 保证）；CL-023：批失败整批 nack，已 reply 消息重投会重复应答（at-least-once 声明成本）；CL-026：批 catch 清理 `lastFailureTopic` 等全部失败上下文字段。

## 消费循环异常屏障契约（Throwable 宽度）

全部消费/引擎异常屏障为 **Throwable 宽度**——Kafka/Pulsar `runTask` 循环、`LocalMessageService.invokeSubscription`、Debezium 引擎 worker 体（`DebeziumEngineWrapper.runEngineWorker`）与 per-subscriber 分发（`DebeziumMessageSource.dispatchEvent`）、`dispatchConsumeFailure`/failure-endpoint 的 `onConsumeFailure` 回调守卫、`MessageConsumeFailureBridge.submit` 与 failure-listener 守卫：

- **Exception 分支**：维持既有语义（退避重试 / 失败上报 / 延迟投递），消费循环继续；
- **Error 分支**：与 Exception 走同一失败上报路径（`onConsumeFailure` / `notifyEngineFailure`），随后 Kafka/Pulsar 消费任务置 `active=false` 诚实退出——订阅不进入僵尸态，`isActive()` 不再误报存活。`ConsumeFailure` 仅承载 `Exception`（nop-api-core 契约）：非 Exception 的 Throwable 以 `IllegalStateException` 包装上报，原 Error 为 cause；
- **Debezium 终局通知**：引擎 worker 体在 Error 死亡分支同样触发 `notifyEngineFailure`——注册于 `FAILURE_LISTENERS` 的终局失败通知不因 Error 逃逸被绕过（nop-stream 侧等待 latch 的 task 依赖该通知）。

## 事务语义（D11 / design 3.7）

`transactional`/`transactionTimeout` 字段已弃用（warn-once 忽略）。at-least-once 基线。

## Seek 能力边界

Pulsar：INIT_SEEK 系经 `SubscriptionInitialPosition`，ALWAYS_SEEK_TO_END/seekToTime 同步 seek，seekToMessage 类型化拒绝。Kafka：rebalance listener 内 seek 起点（D3），seekToMessage 类型化拒绝。

## 错误码

`nop.err.message.kafka.*` / `nop.err.message.pulsar.*` / `nop.err.message.core.*`。

## 指标（WI30）

Micrometer 口径：`nop.message.{backend}.sent/consumed/failed` + `nop.message.{backend}.send.duration`。通过 `setMetricRegistry(MeterRegistry)` 注入（null = no-op）。

## 真实 broker E2E（WI33）

`KafkaBrokerE2ETest` / `PulsarBrokerE2ETest` 经 **Testcontainers 自起真实 broker**（`apache/kafka:4.3.1` KRaft + kafka-clients 4.3.1、`apachepulsar/pulsar:4.0.14` standalone + pulsar-client 4.0.14——Pulsar 跟随 4.0 LTS 补丁线，Kafka 跟随支持线最新），门控语义：

- **主门**：Docker daemon 可达即执行（`DockerClientFactory.isDockerAvailable()` assume——不可达时可见 SKIP，不伪造通过）。
- **退出口**：`-Dnop.test.message.e2e.kafka=false` / `-Dnop.test.message.e2e.pulsar=false` 强制跳过（无 Docker 预算的 CI 场景）。
- **镜像仓库前缀**：`-Dnop.test.message.e2e.registry=<prefix>`，默认 `docker.m.daocloud.io/`（Docker Hub 直连超时的环境）；可直连 Hub 的环境设为空串。
- **Kafka 注意**：服务以 `KafkaConsumerConfig` 显式配置时才落 `auto.offset.reset` 等托管键；不配置则按 kafka-clients 默认（`latest`——订阅首次分配前发送的消息会被跳过）。E2E 显式传 `new KafkaConsumerConfig()`（默认 `earliest`）。
- **Pulsar 注意**：重投类场景必须显式调小 `negativeAckRedeliveryDelay`（旧客户端默认 60s；跨版本一律显式配置）并使 `maxConsumeAttempts == deadLetterMaxRedeliverCount`（分流在 nack 时触发，见 design 3.3.1）。`redeliveryCount` 严格超过 `deadLetterMaxRedeliverCount` 的消息不再投递给 handler（4.0 客户端行为）——两 cap 相等可避开。

场景矩阵（11 测试）：Kafka 六场景（重启恢复/毒消息/DLQ/seek/请求-响应/慢 handler `maxPollIntervalMs` 踢出自愈）+ Pulsar 五场景（重启恢复/毒消息/DLQ/真 seek（INIT_SEEK_TO_BEGIN + ALWAYS_SEEK_TO_END）/请求-响应）。
