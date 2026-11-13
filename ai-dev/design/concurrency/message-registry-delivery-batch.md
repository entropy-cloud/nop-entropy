# WI 4.1 REGISTRY 生命周期/交付语义束（R4-24，plan 2558）设计裁定

> Status: active
> Last Reviewed: 2026-11-11
> Source: `ai-dev/audits/2026-10/2026-10-20-2210-deep-audit-nop-message-concurrency/`（13/13 原样 fresh-session 取证 @ 2026-11-11）；REGISTRY 契约 = `ai-dev/design/concurrency/registry-lifecycle-contract.md`（plan 2503 终裁）；consumeAwaitTimeout 契约 = 4.1 merge-adjudication §五-6（R2-16 对齐）

## 一、设计结论（十三条裁定 A-M）

1. **裁定 A（MA-01 consumeAwaitTimeout；R1-Major-3/m6 修订）**：三后端消费等待点（KafkaConsumeTask :371/:531、PulsarConsumeTask :262/:347、LocalMessageService :217）加 `consumeAwaitTimeout` 有界包装——API：等待对象须为 `CompletableFuture`（`toCompletableFuture()` 非法形态 typed error，不静默）；超时键落各后端消费配置（`KafkaConsumerConfig`/`PulsarConsumerConfig`/Local 配置，键名 `consumeAwaitTimeoutMs`，缺省对齐各后端 maxPollIntervalMs/ackTimeout 自洽指引并 javadoc 声明）。**超时处置 = 专用分支，不参与 maxConsumeAttempts/poison 预算**（attempt=0 语义：Kafka seek 重投/Pulsar nack/Local 走 dispatchConsumeFailure 留证丢弃+WARN——挂死 handler 无限重投为显式接受的可观测面，DLQ 预算保留给真实 handler 失败）；run 循环活性暴露消 isActive 误报。**StackOverflowError 消误报面已由 2496 C3-009 落地——该腿为回归钉非 red-first**（载体表标注）。护栏 C10-005。

2. **裁定 B（MA-03 + C1-002 REGISTRY 契约落地，同 diff 禁各修一半；R1-Major-2 修订）**：按 `registry-lifecycle-contract.md` 终裁一次性落地——①stop 侧配对释放**单点承担者 = `DebeziumEngineWrapper.stop()`，释放置于 finally（或 closeFailure rethrow 之前）**——终止校验失败亦释放（契约 §五①）；`DebeziumMessageSource.stop()` 不释放（其唯一生产调用方即 wrapper，无双计）②`clearConnector` 收紧条件移除（活绑定 typed 拒绝；绑定代为实现表达）③孤儿写入 typed 拒绝或 WARN 可观测 ④驻留诊断指标 + owner doc 生命周期契约节（强制项）。**TTL/evictIdle 不做（裁定②拒绝）**。软钉#5：`setOffsets` REPLACE 契约维持不改写（TestNopStreamOffsetBackingStore :89-97 零改写），仅加「恢复声明窗」守卫（桶/绑定代级）+ 窗外 typed 拒绝负向断言随批新增。护栏 C10-003 三测试。②`clearConnector` 收紧条件移除（活绑定 typed 拒绝；绑定代为实现表达）③孤儿写入 typed 拒绝或 WARN 可观测 ④驻留诊断指标 + owner doc 生命周期契约节（强制项）。**TTL/evictIdle 不做（裁定②拒绝）**。软钉#5：`setOffsets` REPLACE 契约维持不改写（TestNopStreamOffsetBackingStore :89-97 零改写），仅加「恢复声明窗」守卫（桶/绑定代级）+ 窗外 typed 拒绝负向断言随批新增。护栏 C10-003 三测试。

3. **裁定 C（MA-04 Local 重投 executor 三面；R1-m8 指标面显式化）**：destroy 钩子（shutdownNow + `message-core-defaults.beans.xml` nopLocalMessageService 挂 destroy-method——手工维护资源非生成物，可安全添加）；有界待决队列（每订阅上限对齐 maxConsumeAttempts）；到点投递 handler 转有界工作队列 + pending 深度 gauge（**core 增设可选 IMetrics 注入字段**，对齐 kafka/pulsar 形态，beans 注入）+ delay=0 最小间隔节流。**不采 globalTimer**（WI 2.1 C4-001 在案）。护栏 C10-006。

4. **裁定 D（MA-06 destroy/stop 窗口守卫）**：Pulsar checkIsActive 前置交换（:431/:434）；装载与 clear 共享服务级锁或 doStop 二次排空；Kafka 复制 Pulsar WI8 外层 catch 形态；`producer` 字段 volatile + getProducer 单次局部读。护栏 C10-004 三组压力测试。

5. **裁定 E（MA-07 Kafka reply 预算）**：批量 reply 改 `sendAsync` 收集 future + 批末 waitAll（对齐 Pulsar 形态）+ 消费线程期限化（低 max.block.ms 或独立 producer）+ owner doc 三参数联动声明 + buffer 满溢探针测试（定案 CL-022 docs-bug）。护栏 C10-008。

6. **裁定 F（MA-10 FAILURE_LISTENERS；R1-Blocker-1 修订——取两参条件移除形态，零 API 变更/零跨模块触碰）**：`DebeziumEngineWrapper` FAILURE_LISTENERS 修法取裁定表第二选项——**两参条件移除 `remove(key, listener)`**（拒绝移除他者句柄）+ 注册 REPLACE 检测（覆盖存活的**不同** listener 实例时 WARN 留痕——世代观测面，不改变 replace 生效语义）+ **stop 内 unregister 兜底 = remove-by-name**（wrapper 无法感知外部 owner 句柄——按名清空本 connector 槽位）；外部 API 签名零变更（`nop-stream-connector-debezium` DebeziumCdcSourceFunction :314/:373 调用方零触碰，**R404 零 diff 红线保持**——TestDebeziumFailureListenerGuardR404 三腿回归验证绿）；owner doc「静态表必须有 stop 配对回收」。与 WI 1.2 C3-006 同案双轨本体面。**同文件 MA-09 P3 四子面顺带**（quiesce join stuck WARN 细化/worker 死亡幂等 close/start 失败复位 startRequested/start-after-stop one-shot 契约）。护栏 C10-002。

7. **裁定 G（C1-012 反向钉死#3；R1-m7 形态显式化）**：`KafkaClientConfig`（LinkedHashMap :47-53）与 `DebeziumConfig`（HashMap :349-360）——**getter 返回 `Map.copyOf(field)` 不可变视图**（null 值策略：copyOf NPE——extraProps 写入口跳过 null 值 + WARN）；`addExtraProperty` 保持写 raw 字段（冻结=读侧视图化，无生命周期钩子；序列化不受影响——field 保持 HashMap 可序列化）+「装配期专用」为 **documented-only 契约**（javadoc 显式声明，无状态标志强制）；**强制联动：先改写 TestDebeziumEngineProperties :181/:187/:207/:218 四处活 map setup**；TestDebeziumConfigSerializable 序列化 bless 面同批复核（C5-006 挂靠同文件）。护栏 C10-010。

8. **裁定 H（C3-004 反向钉死#1；R1-Major-4 修订——残差分叉显式声明）**：`PulsarMessageService.sendPulsarAsync`（:252-277）**含 getProducer 在内**全段 try/catch，非 NopException 包装 `ERR_PULSAR_SEND_FAILED` 后 `completeExceptionally`（NopException 透传 + InterruptedException 恢复位，对照 Kafka :288-311 形态）；NOT_ACTIVE 面 Pulsar=failed-future 与 Kafka=getProducer 外置同步抛形成**残差分叉——显式登记于 owner doc**（C3-004 缺陷面=destroy 路径同步抛 NPE，本批修复该面；Kafka F-39 范围不含 getProducer，跨后端统一留后续契约批）。**强制联动：改写 TestPulsarServiceLifecycle.sendAfterDestroyIsTypedRejectedNotNpe（:459-467）断言为 failed-future 形态**。护栏 C10-004 挂靠。

9. **裁定 I（C3-005 Pulsar 批量排序分叉）**：per-message 先 reply 后 ack 对齐（或显式声明排序契约）+ CL-023 整批 nack 重复应答声明 + CL-026 catch 清理 `lastFailureTopic` 一行。护栏 C10-008 挂靠。

10. **裁定 J（C3-006 suspended 红投重调度）**：`LocalMessageService` fire-time suspended 改重调度（挂起累积 resume flush 可选形态）+ javadoc 放弃矩阵声明（仅 cancelled 放弃）。与 MA-04 同文件协调。护栏 C10-006。

11. **裁定 K（C8-005 ConsumeLater 预算闭环；R1-Major-5 键位显式化）**：`KafkaConsumerConfig` 新增 `consumeLaterMinDelayMs`（缺省 1000）与 `consumeLaterMaxDefers`（缺省 10）——`Math.max(delayMs, 0L)` → `max(delayMs, consumeLaterMinDelayMs)` + 连续 defer 计数超预算 → WARN 告警 + 退出重投循环（交还 poll 主路径）；Pulsar reconsumeLater 无预算弹跳扩展随单（同键族）；Local 自旋面归 MA-04。护栏 C10-007 参数化矩阵 + MockConsumer 有限轮自旋测试。

12. **裁定 L（C8-007 getProducer 阻塞建连）**：`PulsarMessageService` computeIfAbsent 锁内阻塞建连改占位 + createAsync+超时（失败不缓存负面 → 占位移除重试语义保持，但排队者不再串行放大）。**与 C1-004 同站点异机制，diff 声明互不遮蔽**。护栏 C10-004 建连挂起压力。

13. **裁定 M（钉义务执行序）**：三件钉义务先改后修——①TestDebeziumEngineProperties 四处 setup 先改（G 前）②TestPulsarServiceLifecycle :459-467 断言先改写（H 前）③软钉#5 窗外负向断言先增（B 后守）。2503 零回退钉族（TestDebeziumMessageSource 7 + TestLocalConsumeFailure_C8_002 4 + StopContract/DedicatedCarrier/FailureListener 11 + 2496 Throwable 宽度）全绿门槛。

## 二、载体表（13 工单 + C10 守卫随单）

| 工单 | 载体 | 模块叶 |
|---|---|---|
| MA-01 | `TestConsumeAwaitTimeout`（永不完成 future 超时处置 + Local 挂死 + StackOverflowError 消误报） | kafka/pulsar/core |
| MA-03+C1-002 | `TestOffsetRegistryLifecycle`（stop 配对释放/条件移除活绑定拒绝/孤儿写入/驻留指标 + 软钉#5 窗外负向断言；:89-97 零改写） | debezium |
| MA-04 | `TestLocalRedeliveryBounds`（destroy 钩子/有界队列/depth gauge/节流）+ suspended 重调度（J） | core |
| MA-06 | `TestDestroyWindowGuards`（Pulsar 次序/Kafka volatile+单读/doSubscribe 守卫三组压力） | kafka/pulsar |
| MA-07 | `TestKafkaReplyPipeline`（sendAsync 收集+waitAll 耗时断言 + buffer 探针） | kafka |
| MA-10 | `TestFailureListenerOwnership`（条件移除拒绝他者句柄/REPLACE 不同实例 WARN/stop 兜底 remove-by-name/并发 notify）+ MA-09 P3 四子面钉 | debezium |
| C1-012 | `TestDebeziumConfigFrozen`（4 setup 改写后冻结断言 + 并发写锚 + 序列化 bless 复核） | kafka/debezium |
| C3-004 | `TestPulsarSendAsyncEnvelope`（改写后断言 failed-future 形态） | pulsar |
| C3-005 | `TestPulsarBatchOrdering`（先 reply 后 ack 对齐 + CL-023 声明钉 + CL-026 一行） | pulsar |
| C8-005 | `TestConsumeLaterBudget`（delay∈{0,-5,200} 矩阵 + 有限轮自旋） | kafka/pulsar |
| C8-007 | `TestProducerCreateTimeout`（占位/超时/排队者不串行放大） | pulsar |

## 三、回归面与接缝

- 回归基线：`./mvnw -o test -pl nop-message -am`（四叶 core/kafka/pulsar/debezium；内存形态无需 docker，broker E2E docker 门控跳过）。
- 2503/2496/2498 零回退钉族全绿门槛（见裁定 M③）。
- 硬接缝见取证 seam 矩阵（八文件协调义务，禁各修一半/互不遮蔽声明）。
