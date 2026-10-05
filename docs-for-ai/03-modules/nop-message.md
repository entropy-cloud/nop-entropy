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

## 事务语义（D11 / design 3.7）

`transactional`/`transactionTimeout` 字段已弃用（warn-once 忽略）。at-least-once 基线。

## Seek 能力边界

Pulsar：INIT_SEEK 系经 `SubscriptionInitialPosition`，ALWAYS_SEEK_TO_END/seekToTime 同步 seek，seekToMessage 类型化拒绝。Kafka：rebalance listener 内 seek 起点（D3），seekToMessage 类型化拒绝。

## 错误码

`nop.err.message.kafka.*` / `nop.err.message.pulsar.*` / `nop.err.message.core.*`。

## 指标（WI30）

Micrometer 口径：`nop.message.{backend}.sent/consumed/failed` + `nop.message.{backend}.send.duration`。通过 `setMetricRegistry(MeterRegistry)` 注入（null = no-op）。

## gated E2E（WI33）

`-Dnop.test.message.e2e.kafka=true` / `-Dnop.test.message.e2e.pulsar=true`（需真实 broker）。
