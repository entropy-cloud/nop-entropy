# 计划审查：31-nop-message-pulsar Implementation Plan — Round 1

> 审查日期：2026-05-20
> 审查角色：独立计划审查者（实现者视角）
> 审查对象：`ai-dev/plans/31-nop-message-pulsar-implementation-plan.md`
> 审查结论：**approve with comments**

---

## 1. 整体评价：Approve with Comments

计划整体方向正确，Phase 划分合理，对审计报告的 17 个发现覆盖充分。Non-Goals 和 Deferred 处理得当，尤其是 SubscriptionType 抽象（发现 16）和 MessageId 返回（发现 17）的延迟决策是正确的架构选择。

**但有以下需要修正/加强的方面：**

- 1 个 blocking issue（pulsar-client 依赖不完整，计划中的 watch-only 分类不够）
- 3 个 significant comments（beans.xml 放置路径错误、缺少 `_module` 文件、ExecutorService shutdown 与 Pulsar 线程模型的兼容性）
- 2 个 minor suggestions（Phase 可合并、测试策略需加强）

---

## 2. 按 Phase 的具体审查意见

### Phase 1 — PulsarHelper 补全与消息转换

**评价：基本合理，Exit Criteria 可行。**

**Comment P1-1：`buildApiMessage` 需要确认 Schema 映射策略**

当前代码中 `Message.getValue()` 返回的是 `Object`（由 `Consumer<Object>` 的 Schema 决定）。计划中提到"提取 value"但没有说明如何处理不同 Schema 类型（BYTES、STRING、JSON、AVRO）到 `ApiMessage` 的映射。

建议在 Exit Criteria 中补充：
- [ ] `buildApiMessage` 对 `Schema.STRING` 的消息能正确将 value 设置到 `ApiMessage.setData()`
- [ ] 对 `Schema.BYTES` 的消息能正确处理（Base64 编码或保留 byte[]）

**Comment P1-2：`encodeValue` 需要注意 Pulsar property 的值约束**

Pulsar message property 值必须是 `String`。`encodeValue` 需要处理 `null` 输入的情况（返回空字符串或跳过该 property），否则 `builder.property(name, null)` 会抛出 `NullPointerException` 或 `IllegalArgumentException`。

建议在 Exit Criteria 中补充：
- [ ] `encodeValue(null)` 不会导致 NPE，且结果不会使 Pulsar `TypedMessageBuilder.property()` 失败

### Phase 2 — PulsarMessageService 生命周期与 IoC 集成

**评价：方向正确，但有 3 个需要修正的点。**

**🔴 Blocking Comment P2-1：beans.xml 放置路径错误**

计划写的是：
> 创建 `src/main/resources/_vfs/nop/message/pulsar/beans/pulsar-defaults.beans.xml`

这是错误的。参考 `nop-message-core` 的实际结构：

```
nop-message-core/src/main/resources/_vfs/
├── nop/autoconfig/nop-message-core.beans          ← 自动发现入口
└── nop/message/beans/message-core-defaults.beans.xml  ← 实际 bean 定义
```

NopIoC 的 bean 发现机制是：
1. `nop/autoconfig/<moduleId>.beans` 文件指向 beans.xml 的路径
2. AppBeanContainerLoader 通过 `_module` 和 autoconfig 路径发现 bean

正确的做法应该是：
1. 创建 `src/main/resources/_vfs/nop/autoconfig/nop-message-pulsar.beans`（内容为一行路径引用）
2. 创建 `src/main/resources/_vfs/nop/message/pulsar/beans/pulsar-defaults.beans.xml`
3. **可能还需要 `src/main/resources/_vfs/nop/message/pulsar/_module` 文件**（参考 ioc-and-config.md 中"启用模块通过 `/<moduleId>/_module` 被发现"的说明）

需要修正计划中的 Exit Criteria：
- [ ] beans.xml 路径遵循 NopIoC autoconfig 约定，能通过 `AppBeanContainerLoader` 自动发现
- [ ] 模块有 `_module` 文件（如需要）

**Comment P2-2：`PulsarClientConfig` 已有 `serviceUrl` 字段**

审计报告中指出 `PulsarClientConfig` 已有 `serviceUrl` 和 `enableTransaction` 字段（实际代码确认如此）。计划中的 item "增加 serviceUrl 属性（已存在但需确认注入路径）"表述不够清晰。建议改为：
- [ ] 确认 `PulsarClientConfig.serviceUrl` 通过 beans.xml 正确注入（使用 `@InjectValue` 或 setter）
- [ ] 验证 `PulsarClientConfig` 在无显式配置时有合理默认值或启动时 fail-fast

**Comment P2-3：缺少 `init()`/`destroy()` 生命周期方法在接口上的声明**

NopIoC 支持通过 `ioc:alive-method` 和 `ioc:destroy-method` 在 beans.xml 中声明生命周期方法，或者使用 `@PostConstruct` / `@PreDestroy` 注解。计划没有说明用哪种方式。建议明确：
- 在 beans.xml 中用 `ioc:alive-method="init"` 和 `ioc:destroy-method="destroy"`（参考 `ReflectionMessageSubscriptionRegistrar` 使用 `ioc:delay-method="register"` 的模式）
- 或者在 Java 类上使用 `@PostConstruct` / `@PreDestroy`

### Phase 3 — PulsarConsumeTask 消费循环修复与资源管理

**评价：合理，但有一个关键的线程模型兼容性问题。**

**⚠️ Significant Comment P3-1：ExecutorService shutdown 与 Pulsar Client 线程模型**

计划中将 `Executor` 改为 `ExecutorService` 并在 cancel 时 shutdown。但需要注意：

1. **Pulsar `Consumer.receive()` 是阻塞调用**。当 `ExecutorService.shutdown()` 被调用时，如果消费线程正在 `receive()` 中阻塞，`shutdown()` 不会中断它。需要使用 `shutdownNow()` 或在循环中检查 `Thread.interrupted()`。
2. **`PulsarClient.close()` 会等待内部线程结束**。如果先 shutdown ExecutorService 再关闭 Consumer，可能产生死锁（Consumer 的回调跑在 Pulsar 内部线程上）。

建议修改为：
- [ ] `stop()` 时先设置 `active = false`，再关闭 `Consumer`（这会让阻塞的 `receive()` 抛出 `PulsarClientException.AlreadyClosedException`，从而退出循环）
- [ ] `ExecutorService.shutdown()` 在 `Consumer.close()` 之后调用
- [ ] 添加 `awaitTermination` 带超时的等待，避免无限挂起

**Comment P3-2：退避策略不够具体**

计划提到"异常后 sleep 一段短时间再重试"，但没有指定：
- 初始退避时间
- 最大退避时间
- 是否指数退避

建议 Exit Criteria 补充：
- [ ] 消费异常退避时间可配置，默认 1 秒，最大 30 秒（或使用简单固定间隔）

### Phase 4 — nop-message-core Bug 修复

**评价：完全正确且必要。**

**Comment P4-1：`invokeMessageListener` 异步路径的 fix 需要仔细验证**

审计发现 10 确认了 `handleMessageResult(ret, ...)` 应改为 `handleMessageResult(r, ...)`。但需要注意 `r` 的类型——当 `onMessage` 返回 `CompletionStage<Acknowledge>` 时，`r` 是 `Acknowledge` 对象，这正是我们想要的。

同时，审计发现 11 指出消费循环缺乏异常隔离。当前代码（L168-184）中，`consumer.onMessage()` 如果抛异常，会直接传播到 `for` 循环外。修复方案正确。

建议 Exit Criteria 补充：
- [ ] 异步路径修复后，`CompletionStage<Acknowledge>` 的 Acknowledge 对象能被 `handleMessageResult` 正确识别和处理

### Phase 5 — 测试结构与验证

**评价：基本可行，但需要明确 mock 策略。**

**Comment P5-1：PulsarMessageService beans.xml 加载测试的可行性**

计划提到"PulsarMessageService beans.xml 加载测试：验证 NopIoC 能发现并装配 bean"。这个测试不需要真实的 Pulsar 集群，但需要：
1. `PulsarClientConfig.serviceUrl` 有默认值或测试配置
2. `PulsarMessageService.init()` 不会在无 Pulsar 服务时被立即调用（lazy init）或者测试时 mock 掉 client 创建

需要明确：
- beans.xml 加载测试是否需要启动完整的 NopIoC 容器？如果是，需要继承 `JunitBaseTestCase` 并使用 `@NopTestConfig`
- 或者仅验证 beans.xml 解析成功（轻量级测试）

**Comment P5-2：缺少 PulsarConsumeTask 的单元测试**

Phase 3 修复了消费循环的核心逻辑，但 Phase 5 没有对应的 PulsarConsumeTask 测试。建议增加：
- [ ] PulsarConsumeTask 单元测试：验证 `start()` 设置 `active = true`
- [ ] PulsarConsumeTask 单元测试：验证 `stop()` 设置 `active = false`
- [ ] 使用 mock Consumer 验证消费循环行为

---

## 3. 技术风险提示

### 🔴 Risk 1：pulsar-client-api vs pulsar-client 依赖（Blocking）

**现状确认**：
- `nop-message-pulsar/pom.xml` 只依赖 `pulsar-client-api`
- `nop-dependencies/pom.xml` 只管理了 `pulsar-client-api` 的版本（2.8.0），**没有管理 `pulsar-client`**
- `PulsarClient.create()` 是实现类方法，不在 `pulsar-client-api` 中

计划将此列为 "watch-only residual"（审计发现 13），分类偏低。**这是一个编译期/运行期 blocking issue**：
- 如果模块只被编译但不运行（当前状态），编译可能通过（`pulsar-client-api` 有所有 import 的类和接口）
- 但 `PulsarClient.create()` 是 factory 方法，实际实现在 `pulsar-client` 包中

**建议**：
1. 在 Phase 2 中明确将 `pulsar-client` 依赖添加到 `nop-message-pulsar/pom.xml`
2. 在 `nop-dependencies/pom.xml` 中增加 `pulsar-client` 的版本管理
3. 升级审计发现 13 为 "blocking fix"
4. 版本 2.8.0 较旧（2021 年），考虑升级到 2.10+ 或 3.x

### ⚠️ Risk 2：NopIoC autoconfig 路径约定

nop-message-pulsar 当前没有任何 `_vfs` 资源文件（无 `_module`、无 autoconfig、无 beans.xml）。计划需要在 Phase 2 中创建完整的 autoconfig 链路。如果只创建 beans.xml 但缺少 autoconfig 入口文件，NopIoC 将无法发现这些 bean。

参考 nop-message-core 的完整约定：
```
_vfs/nop/autoconfig/nop-message-core.beans  →  指向 beans.xml 的路径
_vfs/nop/message/beans/message-core-defaults.beans.xml  →  实际 bean 定义
```

### ⚠️ Risk 3：Pulsar Client 线程模型与 ExecutorService 交互

Pulsar Client 内部使用自己的线程池（Netty event loop）。当 `PulsarClient.close()` 被调用时，它会等待所有内部操作完成。如果消费任务运行在自定义 ExecutorService 上，且 `receive()` 正在阻塞，需要确保关闭顺序正确：
1. 设置 `active = false`
2. 关闭 Consumer（打断 `receive()` 阻塞）
3. 关闭 ExecutorService
4. 关闭 PulsarClient

这个顺序需要在 Phase 3 中明确。

### ⚠️ Risk 4：`defaultProducer` 的初始化时机

计划提到"`defaultProducer` 在 `init()` 中创建"，但 `init()` 时不知道会用什么 topic/Schema。`Producer` 绑定到特定 topic。如果没有配置任何 topic schema，`defaultProducer` 应该用什么 Schema 创建？

建议：
- 使用 `Schema.STRING` 作为默认 Schema
- 或者在 `getProducer()` 中 lazily 创建（当前已有 `computeIfAbsent` 逻辑，但 defaultProducer 的创建时机需要明确）

---

## 4. 测试策略可行性评估

### PulsarHelper 单元测试 ✅ 可行

不依赖外部服务，使用 mock 的 `Message` 对象即可。Pulsar 的 `Message` 是接口，可以轻松 mock。

### LocalMessageService 异步路径和异常隔离测试 ✅ 可行

已有 `TestLocalMessageService` 作为参考模板。纯 Java 测试，无外部依赖。

### PulsarMessageService beans.xml 加载测试 ⚠️ 需要额外准备

需要：
1. 创建测试用 beans.xml（mock 掉 PulsarClient 创建）
2. 或使用 `@NopTestConfig(testBeansFile = ...)` 指定测试 bean 配置
3. 可能需要 `nop-message-pulsar` 的测试 scope 依赖中添加测试框架

**不需要 embedded Pulsar**。本次计划的最小可用目标不需要真正的 Pulsar 集群。所有发送/消费路径的测试都可以通过 mock 完成。如果将来需要集成测试，可以使用 Testcontainers + Pulsar 容器。

### PulsarConsumeTask 测试 ⚠️ 计划中缺失

Phase 5 没有包含 PulsarConsumeTask 的测试，但 Phase 3 对其做了重要修改。建议补充。

---

## 5. Phase 合并/拆分建议

### 建议：Phase 1 和 Phase 2 可部分并行

Phase 1（PulsarHelper）和 Phase 2（生命周期与 IoC）之间没有硬依赖。PulsarHelper 是纯静态方法，不依赖 PulsarClient 的初始化。可以在 Phase 2 完成配置骨架的同时，并行实现 PulsarHelper。

但这不是一个 blocking 建议——串行执行也完全可以。

### 建议：Phase 5 拆分测试为"随 Phase 测试"和"集成验证"

当前 Phase 5 把所有测试放在最后，这意味着：
- Phase 1-4 的代码在 Phase 5 之前没有测试验证
- 如果 Phase 1-4 引入了回归，要到 Phase 5 才发现

建议改为：
- **每个 Phase 的 Exit Criteria 中包含对应的单元测试**（PulsarHelper 测试随 Phase 1，ConsumeTask 测试随 Phase 3）
- **Phase 5 聚焦于集成验证**（beans.xml 加载、端到端 mock 发送/消费流程）

---

## 6. 综合建议（按优先级排序）

| # | 优先级 | 建议 | 影响 Phase |
|---|--------|------|-----------|
| 1 | 🔴 Blocking | 升级审计发现 13 为 blocking fix，在 Phase 2 中添加 `pulsar-client` 完整依赖 | Phase 2 |
| 2 | 🔴 Blocking | 修正 beans.xml 放置路径，遵循 autoconfig 约定（含 `_module` 和 autoconfig 入口） | Phase 2 |
| 3 | ⚠️ Significant | 明确 `init()`/`destroy()` 生命周期方法在 beans.xml 中的声明方式 | Phase 2 |
| 4 | ⚠️ Significant | 修正 Consumer/ExecutorService/PulsarClient 的关闭顺序 | Phase 3 |
| 5 | ⚠️ Significant | 补充 PulsarConsumeTask 单元测试 | Phase 5（或 Phase 3 内） |
| 6 | 💡 Minor | 明确 `defaultProducer` 的 Schema 策略 | Phase 2 |
| 7 | 💡 Minor | 明确 `encodeValue(null)` 的处理策略 | Phase 1 |
| 8 | 💡 Minor | 退避策略的具体参数 | Phase 3 |
| 9 | 💡 Minor | 考虑每 Phase 内建测试，Phase 5 只做集成验证 | 结构 |

---

## 7. 审查结论

**Approve with Comments**。计划方向正确，覆盖全面，但需要在实施前解决 2 个 blocking issue（pulsar-client 依赖和 autoconfig 路径约定）和 3 个 significant comments。建议修正后进行 Round 2 快速确认。
