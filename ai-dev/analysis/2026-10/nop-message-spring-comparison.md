# nop-message vs spring-kafka / spring-pulsar 对照分析（WI5）

> Status: resolved
> Date: 2026-10-04
> Scope: 消息抽象层设计对照（ack / 错误处理 / 事务 / 容器生命周期 / 可观测性 / 测试支持）
> Conclusion: Spring 两栈在六个维度上均以「容器 + 注解 + 每栈独立构件」为组织单位；nop 的 SPI 公分母策略（一套 `IMessageService` + beans.xml 覆盖）在骨架上优于 Spring 的双栈分裂，但在 ack 之外的五个维度当前落后于 Spring 的工程化程度。对照结论：不采纳 AckMode、@RetryableTopic、CommonErrorHandler 三项 Spring 机制（理由见专项小节）；采纳其思想的有界关闭、失败上下文回调、客户端指标暴露、契约化测试，分别由 WI19 / WI16 / WI30 / WI4+WI33 承载。

## Context

roadmap Sources 段指定 spring-kafka 与 spring-pulsar 为对照基线。目的有二：一是验证「SPI 公分母 + 显式拒绝」策略相对 Spring 双栈的取舍是否成立；二是把 AckMode / RetryTopic / CommonErrorHandler 三项易被后续 agent 重新导入的 Spring 机制的不采纳理由落档。Spring 侧描述为机制级公开事实（不绑定小版本 API 细节）；nop 侧逐点给 WI1/WI2 产出或 roadmap 编号。

## 结构前提：公分母策略 vs 双栈分裂

Spring：spring-kafka 与 spring-pulsar 是两个独立构件——两套模板类（`KafkaTemplate` / `PulsarTemplate`）、两套监听注解（`@KafkaListener` / `@PulsarListener`）、两套容器与配置属性体系，无共同父接口；跨后端切换即重写接入层。

nop：`IMessageService` 仅 2 个抽象方法（sendAsync / subscribe），broker 切换靠 `beans.xml` + `ioc:default="true"` 同 id 覆盖（WI1 基线「结论摘要」）。一套 `MessageSubscribeOptions` + 一套 `IMessageConsumer` 返回值协议跨后端复用。

结论：骨架不重写（roadmap Hard constraint）；Spring 的优势全部在工程化补全（下述六维），不在抽象层形态。

## 维度 ① ack 语义

- **Spring 做法**：容器级 `ContainerProperties.AckMode` 常量集（RECORD / BATCH / TIME / COUNT / COUNT_TIME / MANUAL / MANUAL_IMMEDIATE），由监听容器按策略提交 offset；MANUAL 系需调用 `Acknowledgment.acknowledge()`。
- **nop 现状**：无容器；`onMessage` 返回值即 ack 协议——null=完成、CompletionStage=异步完成后 ack、ConsumeLater=延迟重投、Acknowledge/其他非 null=ack+reply（WI2 维度 E）。当前缺陷在实现层（F-07/F-08/F-09/F-16），不在协议形态。
- **裁定：不采纳 AckMode（D5 已裁）**。理由见专项小节；补 D5(b') 的终止拒绝与部分提交信号（WI6/WI16/WI25）。

## 维度 ② 错误处理

- **Spring 做法**：`DefaultErrorHandler`（可配 BackOff，seek 回退重投）+ `DeadLetterPublishingRecoverer`（重试耗尽自动发布死信 topic）+ `CommonErrorHandler`（2.8 起统一的容器级错误回调契约）；注解侧 `@RetryableTopic` 以 topic 跳跃实现非阻塞重试。spring-pulsar 侧对应 negativeAck 重投 + 原生 `deadLetterPolicy`。
- **nop 现状**：无 DLQ、无重试上限、退避硬编码且 Pulsar 侧因 F-05 不生效（F-21/F-22）；`IMessageConsumer.onException(Exception)` 空签名拿不到上下文（F-20）。
- **裁定**：D6 三层方案（SPI 终止拒绝信号 + 带上下文失败回调 → 后端原生 DLQ（Pulsar `deadLetterPolicy` 接线，Kafka 应用级重发布）→ `nop-retry` 桥），由 WI16/WI21 承载。**不采纳 @RetryableTopic 与 CommonErrorHandler**，理由见专项小节。

## 维度 ③ 事务

- **Spring 做法**：spring-kafka 提供 `KafkaTransactionManager` 与 `executeInTransaction`，producer 事务 + consumer `read_committed` 组合可达 exactly-once；spring-pulsar 支持事务性 producer/consumer（transaction 提交 ack）。
- **nop 现状**：`transactional` 字段 Kafka 侧零处置（F-13）、Pulsar 侧每消息建事务（F-52）且 timeout=0 静默回落（F-57）；D11 建议 (b)——删除两个字段，显式拒绝当前无后端能完整兑现的能力。
- **裁定**：按 D11。若未来引入事务：以能力声明（WI6 `MessageCapability.TRANSACTION`）+ 类型化错误为门禁逐后端实现，**不移植 Spring 事务管理器抽象**（引入 spring-tx 依赖违背「不引入新依赖」约束，且 nop 的 `ITransactionTemplate` 已是平台内等价物）。

## 维度 ④ 容器生命周期

- **Spring 做法**：监听容器实现 Smart Lifecycle（start/stop 挂应用生命周期）；stop 时经 consumer `wakeup()` 解阻 poll 线程、可配 shutdown timeout；pause/resume 走容器状态机在 poll 循环内生效，避免跨线程直接操作 consumer。
- **nop 现状**：两后端手写线程模型——Pulsar 消费线程不可唤醒（F-54）、cancel 从调用线程直接 `consumer.close()` 与 poll 竞态（F-26）、destroy 顺序两后端相反（F-27）、线程命名/daemon 不一致（F-24）；平台已有 `LifeCycleSupport` 先例（SysDaoMessageService 在用）。
- **裁定（采纳思想、不采纳形态）**：WI19 统一采用 `LifeCycleSupport` + 有界关闭 + `wakeup` 式解阻 + daemon/命名规范；不引入 Spring 的容器/Smart Lifecycle 形态——nop 的订阅是运行时可增删的一等对象（`IMessageSubscription`），不是启动期装配的容器集合。

## 维度 ⑤ 可观测性

- **Spring 做法**：Micrometer 绑定——spring-kafka 自动注册 Kafka 客户端原生 metrics（consumer lag 需另配或 Micrometer Kafka client metrics），`KafkaListenerContainer` 侧有容器级指标；spring-pulsar 同理暴露 client 指标。
- **nop 现状**：零可观测性（F-23）。
- **裁定（采纳思想、按平台口径）**：WI30 按平台既定的 `351-repo-wide-core-metrics` micrometer 组合注册表暴露发送/消费/失败计数与计时；**不新建指标体系**、不移植 Spring 的自动绑定配置面。consumer lag 因 AdminClient 依赖与 broker 不可达失败语义属新增失败类别，按 roadmap 归 FU-2 评估。

## 维度 ⑥ 测试支持

- **Spring 做法**：`@EmbeddedKafka`（进程内 broker）、`KafkaTestUtils`、spring-pulsar 的容器化测试支持；生态惯例是 Testcontainers 起真 broker。
- **nop 现状**：mock 为主，`seekToBeginHonoredNotStubbed` 用 mock 造非空 assignment 把 F-01 认证为正确（F-46）；消息层自身无 E2E（F-44）。
- **裁定（采纳思想）**：WI4 建 broker 无关契约测试骨架（`AbstractMessageServiceContractTest`，Local 首个 extend），WI33 建 gated 真 broker E2E（默认跳过、属性门控）；**不采纳 EmbeddedKafka 形态**——进程内 broker 作为强测试依赖会进入 nop-message 模块依赖图，违背「不引入新依赖」约束，且 gated E2E 已覆盖「mock 不足以验证 rebalance/时序」的核心诉求（roadmap Cross-cutting：以 mock 断言结项视为 hollow）。

## 专项小节：三项不采纳

### A. AckMode（不采纳，D5）

- **机制**：容器级提交策略常量集，按容器配置决定 offset 提交时机。
- **不采纳理由（SPI 结构事实）**：nop 没有「容器」——订阅是运行时对象、提交决策点在 `KafkaConsumeTask`/`PulsarConsumeTask` 的消息分派处，且 `onMessage` 返回值已按**单条消息**表达 ack 意图，粒度细于容器级常量。引入 AckMode 需要先引入容器抽象（改 SPI 骨架，违反 Hard constraint），且 AckMode 无法表达「这条是毒消息不要重投」与「批量只提交到第 k 条」（D5 原论证）。
- **被否决的替代方案**：容器级 AckMode 常量 + MANUAL 回调（D5 选项 (a)）——破坏公分母骨架，且粒度劣化。
- **若强行导入的后果**：SPI 膨胀为多接口矩阵；同一返回值协议与 AckMode 双轨并存，语义冲突。

### B. @RetryableTopic（不采纳，D6 关联）

- **机制**：注解驱动 topic 跳跃重试——重试 N 次即消息流转 N 个 retry-topic，耗尽进 DLT；需要 broker 端自动建 topic 权限与 AdminClient 管理。
- **不采纳理由（SPI 结构事实）**：① 重试状态编码在 topic 拓扑里，nop 的 9 个非 broker 后端（Local/SysDao/Lettuce/MQTT/DB 等）没有 topic 自动创建设施，该机制无法跨公分母落地；② 平台已有 `nop-retry`（`IRetryEngine` + 死信存储）与 `SysDaoMessageService` 的 `IRetryPolicy`，topic 跳跃会制造第三个重试机制（D6 原论证）；③ 重试拓扑对 `nop-stream` 数据面 topic 命名（`StreamTopicNaming`）产生不可见侵入。
- **被否决的替代方案**：SPI 层加 `deadLetterTopic` + `maxRetries` 字段（D6 选项 (a)）——5/9 后端无 DLQ substrate，字段必然沦为「声明了但从不读」。
- **若强行导入的后果**：重试语义分裂为三套（SPI 选项、nop-retry、topic 跳跃），与 MSG-F-53 同型的声明/兑现鸿沟。

### C. CommonErrorHandler（不采纳，D5(b')/D6 关联）

- **机制**：容器级错误处理契约——`handleOne/handleOther` 按异常类型分派，容器把失败记录批量交给 handler，handler 可 seek/重试/发布死信。
- **不采纳理由（SPI 结构事实）**：nop 的错误处理责任已在 SPI 内有落点——`IMessageConsumer.onException`（默认空实现，F-20）+ 后端失败路径。需要的是**给这个落点补上下文与终止语义**（WI16：带 topic/message/attempt/throwable 的失败回调 + D5(b') 终止拒绝信号），而不是再引入一个平行的 handler 接口；Spring 的 handler 形态以「容器持有记录批次」为前提，nop 的批次属于消费任务而非容器。
- **被否决的替代方案**：在 SPI 旁新增 `IMessageErrorHandler` 接口——平行接口与 `onException` 职责重叠，回归面翻倍（13 实现）。
- **若强行导入的后果**：错误处理双轨（onException vs handler），调用方需要二选一，行为不可预期。

## 防重新导入清单

后续 agent 提案出现以下名词时按既定裁定处理，不重开讨论：

| 提案名词 | 既定裁定 | 承载项 |
| --- | --- | --- |
| AckMode / MANUAL ack / Acknowledgment 回调 | 不采纳；返回值协议 + D5(b') 信号 | WI6 / WI16 / WI25 |
| @RetryableTopic / retry-topic 跳跃 | 不采纳；nop-retry 桥 + 后端原生 DLQ | WI21 / WI22 |
| CommonErrorHandler / IMessageErrorHandler 平行接口 | 不采纳；WI16 失败回调替换 onException | WI16 |
| KafkaTransactionManager / spring-tx 移植 | 不采纳；D11(b) 删字段，事务未来按能力声明实现 | WI14 |
| ListenerContainer / Smart Lifecycle 形态 | 不采纳；LifeCycleSupport + 有界关闭 | WI19 / WI20 |
| EmbeddedKafka 常驻测试依赖 | 不采纳；契约骨架 + gated E2E | WI4 / WI33 |

## 与 nop 产出的交叉核对记录

- 维度 ① nop 现状 = WI2 矩阵维度 E；维度 ② nop 现状 = F-20/F-21/F-22；维度 ③ = F-13/F-52/F-57 + D11；维度 ④ = F-24/F-26/F-27/F-54；维度 ⑤ = F-23；维度 ⑥ = F-44/F-46。抽查 3 处与 WI1/WI2 产出一致（F-20 空签名、F-52 每消息建事务、F-46 mock 认证缺陷）。

## Conclusion

- Spring 的组织单位是容器，nop 的组织单位是订阅对象；前者不适合 nop 的 SPI 公分母形态，后者的工程化缺口（有界关闭、失败上下文、指标、真 broker 测试）正是 Phase C/E 各 WI 的内容。
- 被否决的方案：AckMode、@RetryableTopic、CommonErrorHandler、KafkaTransactionManager 移植、容器形态、EmbeddedKafka——理由均落档于专项小节与防重新导入清单。
- 后续工作：WI3 裁定 D5/D6/D11 时引用本文档；WI16/WI19/WI21/WI30/WI33 实现时以本文档为「不采纳」边界依据。

## Open Questions

- [ ] 无

## References

- `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（D5/D6/D11 建议与 Hard constraint）
- `ai-dev/analysis/2026-10/nop-message-audit-baseline.md`（F 编号锚点）
- `ai-dev/analysis/2026-10/nop-message-capability-matrix.md`（返回值协议 as-is）
- spring-kafka / spring-pulsar 官方文档（容器、错误处理、事务、指标机制）
