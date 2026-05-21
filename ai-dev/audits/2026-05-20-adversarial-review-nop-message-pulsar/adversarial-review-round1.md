# nop-message-pulsar 对抗性审查 — Round 1

> 审查日期：2026-05-20
> 审查范围：nop-message 全部子模块（重点 nop-message-pulsar），以及 nop-api-core 中的消息接口契约
> 审查方法：开放式发现导向，从代码骨架和空实现出发
> 去重：已扫描 `ai-dev/audits/` 和 `ai-dev/analysis/`，无针对 nop-message-pulsar 的已有审计
> 发现来源视角：**新人开发者 + 10x 规模运维者 + IoC 侦探 + 死代码清道夫**（混合使用）

---

## 发现 1：PulsarHelper.buildApiMessage 返回 null — 消息内容全部丢失

**在哪里：** `nop-message-pulsar/.../PulsarHelper.java` L21-31

**是什么：**

```java
public static ApiMessage buildApiMessage(Message message) {
    message.getKey();
    message.getValue();
    message.getMessageId();
    message.getTopicName();
    message.getEventTime();
    message.getProperties();
    message.getPublishTime();
    message.getSequenceId();
    return null;   // ← 所有 getter 结果被丢弃，返回 null
}
```

调用方 `PulsarConsumeTask.consume()` (L137) 和 `batchConsume()` (L93) 将此 null 作为消息体传给 `IMessageConsumer.onMessage()`。消费者收到的消息永远是 null，无法做任何业务处理。

**为什么值得关心：** P0 — 消费端功能完全失效。所有通过 Pulsar 接收的消息都会以 null 传递给业务消费者。

**信心水平：** 确定

---

## 发现 2：PulsarHelper.encodeValue 返回 null — 消息属性全部丢失

**在哪里：** `nop-message-pulsar/.../PulsarHelper.java` L67-69

**是什么：**

```java
static String encodeValue(Object value) {
    return null;
}
```

在 `_buildPulsarMessage` (L51) 中，`encodeValue(entry.getValue())` 将每个 header 值转为 null，然后作为 Pulsar message property 设置。虽然 `builder.property(name, null)` 的行为取决于 Pulsar 客户端实现（可能抛异常或静默丢弃），但无论如何发送端的消息头信息都会丢失。

**为什么值得关心：** 发送 ApiMessage 时，所有 header 信息（trace ID、tenant ID 等分布式追踪和上下文传播数据）都会丢失或导致发送失败。

**信心水平：** 确定

---

## 发现 3：PulsarConsumeTask.active 从未设为 true — 消费循环只执行一次

**在哪里：** `nop-message-pulsar/.../PulsarConsumeTask.java` L42, L67-80, L52-56

**是什么：**

```java
private volatile boolean active = false;  // L42

public void start() {
    executor.execute(() -> {
        seekToPosition();
        this.runTask();
    });
}

private void runTask() {
    do {
        // ... consume or batchConsume
    } while (active);  // active 始终为 false
}
```

`active` 初始值为 false，`start()` 调用 `runTask()` 时没有先设置 `active = true`。循环体执行一次后因 `while (false)` 退出。消费者只处理一条消息/一批消息就永久停止。

**为什么值得关心：** P0 — 消费者无法持续消费消息。每次启动只处理一条消息后退出。生产环境完全不可用。

**信心水平：** 确定

---

## 发现 4：PulsarClient 从未初始化 — 全模块 NPE

**在哪里：** `nop-message-pulsar/.../PulsarMessageService.java` L56

**是什么：**

```java
private PulsarClient client;  // 始终为 null
```

`PulsarMessageService` 没有：
- 构造器初始化 client
- setter/injection 方法注入 client
- `@PostConstruct` / `init()` / `start()` 生命周期方法创建 client
- beans.xml IoC 配置文件

因此 `client` 永远为 null。所有依赖 `client` 的操作（`sendAsync` → `getProducer` → `buildProducer` → `client.newProducer()`、`subscribe` → `doSubscribe` → `client.newConsumer()`）都会 NPE。

**为什么值得关心：** P0 — 整个 Pulsar 模块完全不可用。没有任何代码路径能成功执行。

**信心水平：** 确定

---

## 发现 5：topicSchemas 从未初始化 — NPE

**在哪里：** `nop-message-pulsar/.../PulsarMessageService.java` L58

**是什么：**

```java
private Map<String, Schema> topicSchemas;  // 始终为 null
```

`getProducer()` L74 和 `doSubscribe()` L143 都调用 `topicSchemas.get(topic)`，必然 NPE。

**为什么值得关心：** P0 — 即使修复了 client 初始化，发送和消费仍然 NPE。

**信心水平：** 确定

---

## 发现 6：无 IoC 配置 — 无法被 NopIoC 发现和装配

**在哪里：** `nop-message-pulsar/` — 整个模块缺少 beans.xml

**是什么：**

nop-message-pulsar 没有 `src/main/resources/_vfs/nop/message/beans/` 目录，也没有任何 `.beans.xml` 文件。这意味着：
1. `PulsarMessageService` 不会注册为 NopIoC bean
2. `PulsarClientConfig` 的配置属性无处注入
3. 没有任何 bean 定义来创建 `PulsarClient` 实例
4. `ReflectionMessageSubscriptionRegistrar` 的 `ioc:collect-beans` 机制无法发现 Pulsar 消息服务

与之对比，nop-message-core 有完整的 `message-core-defaults.beans.xml` 注册 `nopLocalMessageService` 和 `nopReflectionMessageSubscriptionRegistrar`。

**为什么值得关心：** 缺少 IoC 配置意味着整个 Pulsar 模块无法在 Nop 平台中"即插即用"。即使手动修复了所有代码 bug，也无法通过标准机制集成。

**信心水平：** 确定

---

## 发现 7：Consumer 未配置 topic 和 subscriptionName — subscribe 必然失败

**在哪里：** `nop-message-pulsar/.../PulsarMessageService.java` L142-155

**是什么：**

```java
IMessageSubscription doSubscribe(MessageSubscriptionConfig subConfig) {
    Schema<?> schema = topicSchemas.get(subConfig.getTopic());
    // ...
    ConsumerBuilder<?> builder = client.newConsumer(schema);
    try {
        Consumer<?> consumer = builder.subscribe();  // 没有设置 topic 和 subscriptionName
        // ...
    }
}
```

Pulsar 的 `ConsumerBuilder` 要求至少设置 `topic()` 和 `subscriptionName()` 才能成功调用 `subscribe()`。当前代码直接调用 `builder.subscribe()` 而未配置这些参数，会直接抛异常。

`MessageSubscribeOptions` 中有 `subscribeName` 和 `subscriptionType` 字段，但 `doSubscribe()` 完全忽略了它们。

**为什么值得关心：** 即使修复了 NPE 问题（发现 4、5），消费端仍然无法创建 Consumer。`SeekMode`、`seekToMessage`、`seekToTime` 也全部被忽略（`seekToPosition()` 是空方法体）。

**信心水平：** 确定

---

## 发现 8：Executor 泄漏 — 每个 subscription 创建一个永不关闭的线程

**在哪里：** `nop-message-pulsar/.../PulsarMessageService.java` L157-159

**是什么：**

```java
Executor newConsumeExecutor() {
    return Executors.newSingleThreadExecutor();
}
```

每次 `doSubscribe()` 调用都创建一个新的 `SingleThreadExecutor`。当 subscription 取消时（`PulsarMessageSubscription.doOnCancel`），只关闭了 `Consumer`，没有关闭 `Executor`。线程和 ExecutorService 永远不会被 shutdown。

如果应用频繁创建和取消 subscription（例如动态 topic 订阅场景），将积累大量无法回收的线程。

**为什么值得关心：** 资源泄漏。10x 规模下，如果订阅数上百，会累积大量不可回收线程，最终耗尽系统资源。

**信心水平：** 确定

---

## 发现 9：defaultProducer 和 subscriptionConfigs 未初始化/未使用 — 死代码

**在哪里：** `nop-message-pulsar/.../PulsarMessageService.java` L61, L54

**是什么：**

```java
private List<MessageSubscriptionConfig> subscriptionConfigs;  // L54 — 未使用
// ...
private Producer defaultProducer;  // L61 — 未初始化
```

- `subscriptionConfigs` 有声明但从未被读取或设置。
- `defaultProducer` 在 `getProducer()` L78 作为 fallback 返回（当 topic 不在 topicSchemas 中时），但它从未被初始化，返回 null。调用方 `sendAsync()` L102 对 null producer 调用 `newMessage()` 会 NPE。

**为什么值得关心：** `defaultProducer` 为 null 是另一个 NPE 路径。`subscriptionConfigs` 是死代码，暗示设计意图（可能用于配置化订阅）未完成。

**信心水平：** 确定

---

## 发现 10：LocalMessageService.handleMessageResult 异步路径传递 ret 而非 r

**在哪里：** `nop-message-core/.../LocalMessageService.java` L173-180

**是什么：**

```java
if (ret instanceof CompletionStage) {
    ((CompletionStage) ret).whenComplete((r, e) -> {
        if (e != null) {
            LOG.error("...");
        } else {
            handleMessageResult(ret, topic, message, context);  // ← 应该是 r 而不是 ret
        }
    });
}
```

当 `onMessage` 返回 `CompletionStage` 时，`whenComplete` 的 `r` 参数是异步完成后的实际值，但 `handleMessageResult` 接收的是 `ret`（即 CompletionStage 对象本身）。`handleMessageResult` 检查 `ret instanceof Acknowledge`，但 CompletionStage 不可能是 Acknowledge，所以会进入 `else if (ret != null)` 分支，将整个 CompletionStage 对象作为回复消息发送到 ack topic。

这是一个微妙但实际存在的 bug：异步消费者的返回值不会被正确处理。

**为什么值得关心：** 使用异步 `CompletionStage` 返回值的消息消费者无法正确触发回复消息。Acknowledge 类型回复在异步路径下永远无法生效。

**信心水平：** 很可能

---

## 发现 11：LocalMessageService 消费循环内异常未隔离 — 一个消费者异常阻断后续消费者

**在哪里：** `nop-message-core/.../LocalMessageService.java` L168-184

**是什么：**

```java
for (Subscription subscription : subscriptions) {
    // ...
    Object ret = consumer.onMessage(topic, message, context);  // L172
    // ...
}
```

如果某个消费者的 `onMessage` 抛出未检查异常，循环立即中断，后续消费者不会收到消息。在广播（broadcast）topic 场景下，这意味着一个有 bug 的消费者可以阻止其他所有消费者接收消息。

**为什么值得关心：** 广播模式下消费者之间缺乏隔离。一个消费者的 bug 会级联影响其他消费者。Pulsar 模块的 `batchConsume` 有自己的异常处理（L123-127），但 LocalMessageService 没有。

**信心水平：** 很可能

---

## 发现 12：MultiMessageSubscription 取消/暂停/恢复时只抛最后一个异常

**在哪里：** `nop-api-core/.../MultiMessageSubscription.java` L30-41, L44-55, L57-70

**是什么：**

```java
public void cancel() {
    RuntimeException err = null;
    for (IMessageSubscription subscription : subscriptions) {
        try {
            subscription.cancel();
        } catch (RuntimeException e) {
            err = e;  // 只保留最后一个异常
        }
    }
    if (err != null) throw err;
}
```

`suspend()` 和 `resume()` 同样如此。如果多个 subscription 取消失败，只有最后一个异常被抛出，之前的异常被静默丢弃。

**为什么值得关心：** 部分失败场景下的错误信息丢失。调用方无法得知哪些 subscription 成功取消了。建议使用 suppressed exceptions 或收集所有异常。

**信心水平：** 确定

---

## 发现 13：nop-message-pulsar 依赖 pulsar-client-api 而非 pulsar-client — 功能受限

**在哪里：** `nop-message-pulsar/pom.xml` L21-23

**是什么：**

```xml
<dependency>
    <groupId>org.apache.pulsar</groupId>
    <artifactId>pulsar-client-api</artifactId>
</dependency>
```

`pulsar-client-api` 只包含接口和类型定义，不包含 `PulsarClient` 的实现。`PulsarMessageService` 使用了 `PulsarClient.create()`（虽然未实现，但代码中引用了 `client.newProducer()` 等需要实现类的方法）。实际运行时需要 `pulsar-client`（完整客户端）或 `pulsar-client-original` 依赖。

**为什么值得关心：** 即使修复了所有初始化问题，运行时可能因为缺少实现类而出现 ClassNotFoundException 或 NoClassDefFoundError。

**信心水平：** 很可能（取决于 Nop 父 POM 的依赖管理配置——如果父 POM 已通过 `pulsar-client-all` 或类似方式引入了完整实现，则此问题不成立）

---

## 发现 14：三个子模块是完全空壳 — 无源码无功能

**在哪里：**
- `nop-message-kafka/` — 只有 pom.xml，无 src/ 目录
- `nop-message-codec/` — 只有 pom.xml，无源码
- `nop-message-model/` — 有两个完全空的 Java 类（MessageFieldModel、MessageModel）

**是什么：** 这些模块在 parent pom.xml 中声明为子模块，但没有任何实际代码。`nop-message-model` 的两个类是空壳，没有任何字段或方法。

**为什么值得关心：**
- 如果下游模块依赖 `nop-message-kafka`，编译时不会有错误提示，运行时则找不到实现。
- 空壳模块增加了维护者的认知负担（"这个模块实现了吗？"）
- `nop-message-codec` 暗示消息编解码抽象是设计意图的一部分，但完全没有落地

**信心水平：** 确定

---

## 发现 15：PulsarProducerConfig 和 PulsarConsumerConfig 是空类

**在哪里：**
- `nop-message-pulsar/.../PulsarProducerConfig.java` — 完全空类
- `nop-message-pulsar/.../PulsarConsumerConfig.java` — 完全空类

**是什么：** 两个 Config 类被声明为字段（`PulsarMessageService` L50-52）但没有任何配置属性。

Pulsar Producer/Consumer 有大量可配置项（batching、batchSize、maxPendingMessages、ackTimeout、negativeAckRedeliveryDelay 等），当前全部使用默认值且无法通过配置调整。

**为什么值得关心：** 生产环境调优完全不可能。没有配置意味着无法调整批处理大小、确认超时、重试策略等关键参数。

**信心水平：** 确定

---

## 发现 16：SubscriptionType 枚举直接暴露 Pulsar 概念到 API 层

**在哪里：** `nop-api-core/.../SubscriptionType.java` L1-46

**是什么：**

```java
/**
 * 与Pulsar消息队列的SubscriptionType相对应
 */
public enum SubscriptionType {
    Exclusive, Shared, Failover, Key_Shared
}
```

`SubscriptionType` 位于 `nop-api-core`（平台通用 API 层），但注释明确说"与 Pulsar 相对应"，枚举值完全复制了 Pulsar 的订阅类型。如果将来引入 Kafka（`nop-message-kafka`），Kafka 的 consumer group 模型与 Pulsar 的订阅类型不完全映射（Kafka 没有 Failover 或 Key_Shared 的直接对应）。

`MessageSubscribeOptions` 使用此枚举作为字段类型（L43），意味着所有消息实现都必须理解 Pulsar 的订阅模型。

**为什么值得关心：** API 层的抽象泄漏。通用消息接口不应绑定特定消息中间件的概念。这会增加 Kafka/RocketMQ 等其他实现的适配成本。`Key_Shared` 这个命名风格（下划线）也与 Java 枚举惯例不一致。

**信心水平：** 有趣的猜测（当前只有 Pulsar 一个实现，但 API 层的设计应该前瞻性考虑多实现场景）

---

## 发现 17：PulsarMessageService.sendAsync 丢弃 Pulsar 返回的 MessageId

**在哪里：** `nop-message-pulsar/.../PulsarMessageService.java` L107-108

**是什么：**

```java
return builder.sendAsync().thenAccept(ret -> {
    // ret 是 MessageId，被完全丢弃
});
```

`sendAsync()` 返回的 `MessageId` 是 Pulsar 消息的唯一标识，在消息追踪、去重、调试时非常重要。当前实现将其完全丢弃，`sendAsync` 的调用者无法获得发送后的消息 ID。

同时，`IMessageSender.sendAsync` 的返回类型是 `CompletionStage<Void>`，接口设计层面就不支持返回消息 ID。

**为什么值得关心：** 缺少消息追踪能力。在生产环境中排查消息丢失或重复时，没有发送端的 message ID 会非常困难。

**信心水平：** 很可能

---

## 总评

nop-message-pulsar 模块处于**早期骨架/占位符**状态，与 nop-stream 模块类似但程度更严重：它不是一个"有 bug 的可用实现"，而是一个**完全不可运行的代码骨架**。具体来说：

1. **最值得关注的 3 个方向：**
   - **模块完成度**：PulsarMessageService 缺少 PulsarClient 初始化、生命周期管理、IoC 配置、Consumer/Producer 配置。当前代码如果被引用，100% NPE。这不是"有 bug"，而是"未完成"。需要补全：client 创建 → beans.xml IoC 注册 → config 注入 → lifecycle 方法 → Consumer/Producer 配置传递。
   - **API 层与实现层的耦合**：SubscriptionType 枚举将 Pulsar 概念直接暴露到 nop-api-core，为未来多消息中间件实现（Kafka、RocketMQ）埋下适配障碍。MessageSubscribeOptions 中的 Pulsar 特有字段（subscriptionType、seekMode）也是如此。
   - **core 模块的异步处理 bug**：LocalMessageService.handleMessageResult 的异步路径错误（发现 10）是一个真实的运行时 bug，会影响到所有使用异步消费者的场景。

2. **模块间一致性**：nop-message-core 的 LocalMessageService 是一个完整且基本可用的实现（除了异步路径 bug），但 Pulsar 模块与它的差距不是"细节差异"而是"完成度差异"。如果团队期望 Pulsar 模块可用，需要先确定最小可用功能的范围。

---

## 本次审查的盲区自评

1. **Nop 父 POM 的依赖管理**：未检查父 POM 是否已通过 `pulsar-client-all` 引入了完整 Pulsar 客户端实现。如果已引入，发现 13 不成立。
2. **Pulsar 客户端 API 的精确行为**：未验证 `builder.property(name, null)` 和 `builder.subscribe()`（无 topic）的确切异常类型和消息。
3. **nop-message-debezium 模块**：虽然已读取代码，但未做深入审查。该模块看起来比 Pulsar 模块完整，但未验证其与 nop-message-core 的集成路径。
4. **运行时依赖关系**：未检查是否有其他模块（nop-sys、nop-spring等）引用了 nop-message-pulsar 并假设它能工作。
5. **NopIoC 自动发现机制**：未验证如果添加了 beans.xml，NopIoC 的 classpath 扫描是否能自动发现 nop-message-pulsar 中的 bean 定义。
