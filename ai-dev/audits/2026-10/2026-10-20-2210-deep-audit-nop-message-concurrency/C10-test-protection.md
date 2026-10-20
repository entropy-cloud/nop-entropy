# C10 测试保护力 — nop-message（WI 4.1）

- 审计维度：C10（并发正确性的测试保护力）· 最后批次（依赖 C1/C3/C5/C6/C8 最终保留项缺陷清单）
- 日期：2026-10-20
- 方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md` C10 正文（L329–347）+ 附录 A 格式（L351–377）
- 输入缺陷清单：本目录五个维度「最终保留项」共 44 条（C1×12 / C3×10 / C5×6 / C6×7 / C8×9）

## 审计范围与口径

### 测试底座盘点（2026-10-20 live code）

nop-message 全模块测试文件共 27 个（约 5.3k 行）：

| 模块 | 测试文件 | 并发相关内容 |
|---|---|---|
| nop-message-core | TestLocalMessageService、TestLocalMessageServiceAsync、TestMessageConsumeFailureBridge、TestMessageMetrics、contract/AbstractMessageServiceContractTest、contract/LocalMessageServiceContractTest | 多线程原语仅 TestLocalMessageServiceAsync 1 处（无）；合同测试全单线程顺序驱动 |
| nop-message-kafka | TestKafkaConsumeTask（1155 行）、TestKafkaServiceLifecycle、TestKafkaSubscription、TestKafkaMessageServiceSend、TestKafkaHelper、TestKafkaIoC、KafkaBrokerE2ETest（Docker 门控）、KafkaMessageServiceContractTest（**@Disabled**） | 并发原语集中在 TestKafkaSubscription（4 线程并发 cancel 幂等）与 Lifecycle（fan-out 线程命名/关闭时序）——全部是 WI19/WI20 修复的回归钉 |
| nop-message-pulsar | TestPulsarConsumeTask（911 行）、TestPulsarServiceLifecycle、TestPulsarHelper、TestPulsarIoC、PulsarBrokerE2ETest（Docker 门控）、PulsarMessageServiceContractTest（**@Disabled**） | 并发原语集中在 stop 有界退出（F-54）与 4 线程并发 cancel |
| nop-message-debezium | TestDebeziumEngineWrapperFailureListener、TestNopStreamOffsetBackingStore、TestDebeziumEngineProperties、TestDebeziumConfigSerializable、TestChangeEventSemanticView | **零多线程测试**；全部为静态语义/配置断言 |

关键结构性事实（C10 证据基座）：

1. **DebeziumMessageSource 全类零测试**：全仓 grep `DebeziumMessageSource` 在 nop-message test 源码内仅命中 `TestDebeziumEngineWrapperFailureListener.java:28` 的 javadoc 文字。C3-002/C3-003/C6-002/C6-006/C8-004（含 3 条 P1）全部无任何测试触达。
2. **契约测试只对 Local 生效**：`AbstractMessageServiceContractTest` 的 12 个契约维度（五返回值语义/suspend/cancel/destroy 幂等）中，Kafka/Pulsar 两个子类均 `@Disabled`（MockConsumer/MockProducer 无法在 JVM 内互通，见两类头部注释）；Debezium 后端从未接入契约骨架。
3. **E2E 为 Docker 门控 assume-skip**：`KafkaBrokerE2ETest`/`PulsarBrokerE2ETest` 场景集为 restart 恢复、毒消息、DLQ、seek、request-response、慢 handler 踢组——全部是功能语义面，不含任何并发交错/资源泄漏场景。
4. **跨模块触达面**：nop-stream 侧 `TestDebeziumCdcSourceErrorPropagation.java:220` 以直接 `notifyEngineFailure` 驱动（无注册竞态面）；nop-sys 侧 `TestSysDaoMessageService.java:379` 经 `subscription.cancel()` 间接触达 Local 订阅注销。

### 判定口径（变异推演）

- **会红·有保护**：存在启用中的测试，若本条缺陷面被（重）引入或其修复被回退，该测试会失败。
- **假保护**：同站点/同协议存在测试，但无一断言本条缺陷面——缺陷在位时全部保持绿（本轮 44 条缺陷均为 live code，套件绿，故「会红」逻辑上不可能存在于缺陷面本身；判定落到「邻近测试是否构成真保护」）。
- **无测试**：无启用中的测试触达该站点或协议（仅 @Disabled / Docker 门控测试触达亦计无测试）。
- 严重度分布口径注记：五卷逐条判定表合计 P1×4 / P2×26 / P3×14；派发口径 P2×25/P3×15 与逐条合计差 1，属 C8-001（同案并入 C3-001）的计数归属差异。矩阵按 44 行全列、每行标注该条复核后最终严重度，不影响 C10 结论。

---

## 保护力矩阵

| # | 发现 | 最终严重度 | 判定 | 证据（测试文件:line 或检索说明） |
|---|---|---|---|---|
| 1 | C1-001 FAILURE_LISTENERS replace+无条件 remove 竞态 | P2 | 假保护 | TestDebeziumEngineWrapperFailureListener.java:40-89（4 测试全部单线程自注册自注销）；nop-stream TestDebeziumCdcSourceErrorPropagation.java:220 直接 notify 无注册面。变异「回退为无条件 remove」全绿；无同键交叠 register/unregister 测试 |
| 2 | C1-002 REGISTRY 摘除竞态+stop 不释放 | P2 | 假保护 | TestNopStreamOffsetBackingStore.java:101-148 钉跨实例共享/configure 绑定；无并发 clearConnector 测试、无 stop 侧释放断言；@AfterEach:35 手工 clearConnector 反而掩盖配对缺失 |
| 3 | C1-003 Pulsar doSubscribe 守卫次序错位（NPE） | P2 | 假保护 | doSubscribe happy path 被 TestPulsarConsumeTask.preparedService:202-223 大量触达，但全部在 init 后调用；destroy/未启动后 subscribe 的类型化拒绝断言全仓为 0（seekToMessageRejectedSynchronouslyBeforeClientUse:282-293 在 client 解引用前返回，恰好绕开缺陷路径，制造「守卫在位」错觉） |
| 4 | C1-004 Pulsar producer 缓存 TOCTOU 泄漏 | P2 | 假保护 | TestPulsarServiceLifecycle.destroyCancelsSubscriptionsBeforeClosingProducerAndKeepsInFlightSendAlive:160-193 钉 destroy 侧 producer.close（主泄漏路径受保护）；buildProducer×doStop 交叠窗口无任何测试，producers map 销毁后残留无断言 |
| 5 | C1-005 Kafka getProducer 双读 NPE | P2 | 假保护 | TestKafkaMessageServiceSend.sendAsyncFailsBeforeInitWhenNotInitialized:112-115 钉「未初始化→类型化拒绝」（守卫的零防御形态受保护）；双读竞态面无压力测试（destroy 循环中并发 send 断言 cause≠NPE 的测试不存在） |
| 6 | C1-006 subscribe 锁外写（并入 C3-003） | P2 | 无测试 | DebeziumMessageSource 全类零测试（grep 证据见审计范围节 1） |
| 7 | C1-007 Kafka doSubscribe 无守卫+add-after-clear | P2 | 假保护 | TestKafkaServiceLifecycle.destroyCancelsSubscriptionsBeforeClosingProducer:206-236 钉 stop 侧 cancel 序；happy-path subscribe 测试多个（TestKafkaConsumeTask:273-299 等）；destroy-then-subscribe 拒绝与 :430 task.start→:433 add 交叠窗口均无测试 |
| 8 | C1-008 Local cancel 裸 get NPE + getConsumers 外泄 | P3 | 假保护（附反向钉死） | NPE 面：cancel 路径被 TestLocalMessageService:48-83 与 TestSysDaoMessageService:379 反复触达但全部顺序执行，无 clearConsumers×cancel 并发测试。leak 面：TestLocalMessageService.java:70/:117、TestSysDaoMessageService.java:379 依赖外泄活 map（见反向钉死 #4） |
| 9 | C1-009 Pulsar suspend 调用方线程直呼 | P3 | 无测试 | grep `suspend|pause` 在 Pulsar 测试 = 0 命中；契约 suspendStopsDeliveryAndResumeRestoresIt 仅对 Local 运行（Kafka/Pulsar 契约测试 @Disabled）；Kafka 侧对应模式（WI20 F-26）有测试（TestKafkaServiceLifecycle:243-278），Pulsar 兄弟零覆盖 |
| 10 | C1-010 cancelReason 非 volatile 写序反置 | P3 | 无测试 | DebeziumMessageSource 零测试；getCancelReason 全仓零读者（C1 卷复核 grep 复现），无任何测试读该字段 |
| 11 | C1-011 wrapper 一次性状态机（并入 C6-005） | P3 | 无测试 | DebeziumEngineWrapper.start/stop 全仓无启停测试（唯一 wrapper 测试只测静态注册表语义） |
| 12 | C1-012 配置 bean 集合活引用外泄 | P2 | 假保护（附反向钉死） | TestDebeziumEngineProperties.java:181/:187/:207/:218 以 `config.getExtraProperties().put(...)` 活 map 做 setup（反向钉死 #3）；TestDebeziumConfigSerializable:44-71 钉 Serializable+可变 extraProperties 往返；无任何 freeze/不可变断言 |
| 13 | C3-001 onMessage 返回 CompletionStage 无上界等待 | P2 | 无测试 | 五等待点（Kafka:356/:516、Pulsar:333/:248、Local:216）无「永不完成 future」测试；契约 completionStageReturnIsUnwrappedBeforeDispatch:143-158 用 completedFuture。发送侧 orTimeout 有钉（TestKafkaMessageServiceSend:141-168）但消费侧等待上界为零覆盖 |
| 14 | C3-002 Debezium 回调无异常屏障+started 撒谎 | P1 | 无测试 | DebeziumEventConverter 无专项测试（TestChangeEventSemanticView 仅 RowKind 映射矩阵）；handleDebeziumEvent/DebeziumMessageSource.isStarted 全部零测试 |
| 15 | C3-003 DebeziumMessageSource subscribe/stop 交叉 | P2 | 无测试 | DebeziumMessageSource 全类零测试 |
| 16 | C3-004 Pulsar sendAsync 同步异常逸出 | P2 | 假保护（反向钉死） | TestPulsarServiceLifecycle.sendAfterDestroyIsTypedRejectedNotNpe 断言 `assertThrows(NopException)` **同步抛**——钉死缺陷语义（见反向钉死 #1）；Kafka 对照面由 TestKafkaMessageServiceSend.producerSendSyncExceptionBecomesFailedFuture:192-208 钉为 rejected future——分叉被两套绿测试各自合法化 |
| 17 | C3-005 Pulsar 批量 ack/reply 序分叉 | P2 | 假保护 | 批量测试（batchPartialCommitNacksBoundaryRecordAndRemainder:88-149、batchExtraResponsesAreWarnedAndIgnoredWithoutNack:737-779）全部用已完成 future，reply 与 ack 完成顺序、ConsumeLater 批内非早退语义均无断言 |
| 18 | C3-006 Local 红投火发时 suspended 丢弃 | P2 | 无测试 | 契约 suspendStopsDeliveryAndResumeRestoresIt:306-324 只测「suspend 期间新消息不投递」；ConsumeLater 在途红投 × suspend 组合、resume 后恢复语义零测试 |
| 19 | C3-007 orTimeout ghost 残留+零配置无上界 | P3 | 假保护 | sendTimeoutBoundsTheReturnedFuture:141-168 与 producerLevelSendTimeoutUsedWhenOptionAbsent:171-189 钉「已配置」两分支；双缺省 → resolveSendTimeoutMs 返回 0（无上界）面无断言（负向断言形态缺失） |
| 20 | C3-008 Kafka commitSync 失败仅 WARN 吞 | P3 | 无测试 | 全部 commitSync 断言为 happy-path verify（nullResponseCommitsOffset:88-106 等）；无 commitSync 抛异常注入测试；退避重置面（dispatched=true）无测试 |
| 21 | C3-009 异常屏障 Exception-only（Error 逃逸） | P2 | 无测试 | 全模块无任何测试向消费循环/引擎回调注入 Error/Throwable；Exception 面部分覆盖（consumeAttemptCapTriggersFailureEndpoint:332-371、runTaskInvokesOnConsumeFailureWithFullContextAndBacksOff:451-521）恰恰使 Exception-only 屏障看起来有保护 |
| 22 | C3-010 Pulsar 批量 null 响应 NPE | P3 | 无测试 | 两后端均无 onMessageBatch 返回 null 的测试（Kafka 的 IllegalStateException 显式契约同样无钉；batchExtraResponsesIgnoredAndTailRewoundWithArityWarnings:237-270 只测多余响应） |
| 23 | C5-001 REGISTRY 静态账本单调增长 | P2 | 假保护 | TestNopStreamOffsetBackingStore 重度触达 store 且 @AfterEach 手工清理——「清理靠调用方」被测试实践本身正常化；无条目驻留/淘汰断言 |
| 24 | C5-002 FAILURE_LISTENERS stop 不注销 | P3 | 假保护 | 注册表语义有钉（TestDebeziumEngineWrapperFailureListener），stop() 与注册表关联面零测试；「stop 自动注销」修复对现有测试不可见（不会红） |
| 25 | C5-003 Local 未决红投无界+delay=0 自旋 | P2 | 假保护 | 契约 consumeLaterDefersRedeliveryWithoutAckReply:189-221 钉单次红投发生（300ms）；pending 深度/上限/delay=0 自旋全无测试；testRedeliveryScheduleRejectionIsWarnedNotThrown:103-121 钉 F-40 拒绝路径（邻近保护，非本条） |
| 26 | C5-004 Pulsar 消费预取配置面缺位 | P3 | 假保护（反向钉死） | TestPulsarServiceLifecycle.batchReceivePolicyNotWiredWhenUnconfigured **把「未配置时不设 BatchReceivePolicy」钉为契约**（见反向钉死 #2）；receiverQueueSize 全模块 grep=0（配置面缺位连测试对象都不存在） |
| 27 | C5-005 Pulsar 生产端 pending/batching 配置面缺位 | P3 | 无测试 | grep `batchingMaxMessages|maxPendingMessages|receiverQueueSize` 在 Pulsar 测试 = 0 命中；producer builder 装配面零断言 |
| 28 | C5-006 Debezium 引擎队列字节上界缺位 | P3 | 假保护 | TestDebeziumEngineProperties:203-224 钉 offset.storage/schema.history.internal 前缀钉定族（注释明言「回退 prefix sweep 会红」——钉定机制本身有回归保护）；max.queue.size 族不在钉定族、无字节缺省面零测试 |
| 29 | C6-001 Local 红投调度器只建不关 | P2 | 无测试 | 无任何线程泄漏断言（无 @AfterEach 线程数检查）；setRedeliveryExecutorForTest 是唯一生命周期缝（仅 F-40 测试用过一次）；契约测试逐测 `new LocalMessageService()` 各自泄漏线程且无感知 |
| 30 | C6-002 Debezium 幽灵订阅（put-then-start 不回滚） | P1 | 无测试 | DebeziumMessageSource 全类零测试；引擎启动失败路径（startEngineIfNeeded 抛 NopException）无任何测试 |
| 31 | C6-003 Kafka doSubscribe 构造后无清理守卫 | P2 | 假保护 | consumerCreateFailureCarriesTypedCode:302-320 钉构造失败类型化拒绝（被覆盖段受保护）；:417 subscribe/:428 executor/:430 start 裸奔段无故障注入测试 |
| 32 | C6-004 REGISTRY 清理路径不在生命周期内/stale-resume | P2 | 假保护 | TestNopStreamOffsetBackingStore 覆盖 forConnector/configure/stop 全 API 面但从不断言生命周期与 REGISTRY 的关系；同名 restart 续读旧 offset（stale-resume）无测试 |
| 33 | C6-005 wrapper stop 三缺口（不 join/过早翻转/一次性） | P3 | 无测试 | wrapper start/stop/一次性行为零测试（同 #11）；CL-013→C6-005 立案两轮，测试面始终未建 |
| 34 | C6-006 Debezium action 身份作注销键 | P2 | 无测试 | DebeziumMessageSource 零测试；同族 Local 修复（WI23）有钉（testSubscribeIsIndependentPerCallAndCancelDoesNotCrossAffect:48-83）——家族对照测试存在而 Debezium 兄弟缺位，F3 漏修无测试闸门 |
| 35 | C6-007 引擎死亡路径 engine 不 close | P3 | 无测试 | 引擎 run() 抛出→worker catch→无 close 的路径零测试（TestDebeziumCdcSourceErrorPropagation 用 stub source，不经真 wrapper） |
| 36 | C8-001 消费线程无上界等待（并入 C3-001） | P2 | 假保护 | startAndStopToggleActive:1139-1154（Kafka）/testStart_setsActive:34-58（Pulsar）钉 active 标志翻转；「handler 挂死但 isActive 全绿」的健康盲区面无测试 |
| 37 | C8-002 Local 失败静默丢弃无第一层回调 | P1 | 假保护 | testExceptionIsolation（TestLocalMessageServiceAsync:50-64）钉「其他订阅者隔离」（修复后仍绿）；grep `onConsumeFailure` 在 LocalMessageService=0 处、core 测试=0 处——Local 的 onConsumeFailure 契约（设计 §3.3）零断言 |
| 38 | C8-003 Local 单线程 scheduler 内联 handler | P2 | 无测试 | 全部 Local 测试从测试线程单线程驱动 send；调度线程拓扑、跨订阅时延侵蚀、无界队列行为零测试 |
| 39 | C8-004 Debezium dispatchEvent 吞异常推进 offset | P1 | 无测试 | DebeziumMessageSource 零测试；dispatchEvent 的 per-consumer 屏障（C3-002 失效面枚举中被称为「正确形态」处）从未被任何测试执行过 |
| 40 | C8-005 Kafka ConsumeLater 无预算+delay≤0 热旋转 | P2 | 假保护 | consumeLaterSeeksAndSkipsCommit:109-123 钉 seek+skip-commit；consumeLaterDelayHonoredBeforeRedelivery:860-886 钉 delay 下界兑现（200ms）；delay=0 热旋转、连续 defer 无退出机制零测试（两测试手动调 pollAndConsume，热循环结构上不可见） |
| 41 | C8-006 Kafka 批量 reply 逐条同步串行 | P2 | 假保护 | 批量测试的 service.sendAsync 全部 mock 为已完成 future（batchAttemptCapEndpointsFailedRecordAndCommitsBoundary 等）；串行 vs 管道化拓扑、max.poll.interval 预算侵蚀零断言 |
| 42 | C8-007 getProducer computeIfAbsent 锁内阻塞建连 | P2 | 假保护 | pulsarCreationFailuresCarryTypedCodes 钉建连失败的类型化包装；阻塞排队×线程数放大、失败后排队者重试完整建连的故障期行为零测试 |
| 43 | C8-008 过载指标缺口（批失败漏计等四处） | P3 | 无测试 | TestMessageMetrics 仅单测 Counter 类本身；全部指标调用点（批 catch 无 incrementFailed、Pulsar sent 前置、毒消息 endpoint 漏计、Local 全缺）零断言 |
| 44 | C8-009 消费线程承接 max.block.ms 同步阻塞 | P2 | 无测试 | 无 buffer.memory 满溢探针测试（CL-022 建议的探针形态从未落地）；reply/DLQ 路径预算侵蚀零测试 |

**矩阵统计**：会红·有保护 **0** ｜ 假保护 **23** ｜ 无测试 **21**（合计 44）
**按严重度分层**：P1×4 全部无保护（无测试 3：C3-002/C6-002/C8-004；假保护 1：C8-002）；P2×26 中假保护 17/无测试 9；P3×14 中假保护 5/无测试 9。

---

## 反向钉死检查

对 Kafka/Pulsar 消费测试、Debezium offset/REGISTRY 测试、LocalMessageService 测试全量扫描后，命中 **5 处**测试断言/依赖的正是缺陷语义或缺陷赖以存在的 API 形状（修复将使现有测试变红）：

### 反向钉死 #1（硬钉，命中 C3-004）：Pulsar 同步抛被钉为契约
`TestPulsarServiceLifecycle.sendAfterDestroyIsTypedRejectedNotNpe`：
```java
NopException e = assertThrows(NopException.class,
        () -> service.sendAsync("t-after", "m", null));
assertEquals(PulsarErrors.ERR_PULSAR_SERVICE_NOT_ACTIVE.getErrorCode(), e.getErrorCode());
```
断言的是 sendAsync **同步抛出**——恰是 C3-004 认定的契约违约面（getProducer 的 client==null NopException 同步逸出；对照 Kafka F-39 形态应为 rejected future，且已被 `TestKafkaMessageServiceSend.producerSendSyncExceptionBecomesFailedFuture:192-208` 钉死）。Pulsar 代码自身在 cancel-token 路径的注释（PulsarMessageService.java:255-262 "never a synchronous throw"）已承认正确形态，两套绿测试把同一接口的分叉行为各自合法化。**修复 C3-004 必须同步改写此测试**，评审时须防「为保测试绿而放弃修复」。

### 反向钉死 #2（硬钉，命中 C5-004）：配置缺位被钉为契约
`TestPulsarServiceLifecycle.batchReceivePolicyNotWiredWhenUnconfigured`：
```java
verify(service.builder, never()).batchReceivePolicy(org.mockito.ArgumentMatchers.any());
```
把「batchReceiveCount=0 时不设默认 BatchReceivePolicy」断言为**期望行为**——正是 C5-004 立案的缺陷面（批路径缺省时批大小完全由客户端默认 policy 决定、无模块配置面）。C5-004 建议的修复（缺省时显式设置 BatchReceivePolicy）会使该测试变红。

### 反向钉死 #3（硬钉，命中 C1-012）：活 map 被测试当 setup 通道
`TestDebeziumEngineProperties.java:181/:187/:207/:218` 共 4 处以 `config.getExtraProperties().put(key, value)` 直接向外泄活引用写入做测试装配。C1-012 建议的修复选项「getter 返回不可变视图 / 装配终点 Map.copyOf」会使这 4 个测试全部变红（前缀钉定测试与 record.processing.threads 钉定测试均依赖活 map 可写）。另 `TestDebeziumConfigSerializable:44-71` 钉死「DebeziumConfig 可序列化且 extraProperties 可变往返」——即 C1-012 的 checkpoint 序列化放大面被测试 blessing 为设计特性。

### 反向钉死 #4（硬钉，命中 C1-008 外泄面）：SysDao 与 Local 测试依赖内部 map 外泄协议
`SysDaoMessageService.java:617`（生产代码）`localService.getConsumers().remove(state.topic, new CopyOnWriteArrayList<>())` 的行为被 `TestSysDaoMessageService.testCancelLastSubscriptionRemovesTopicFromPollSet`（:373-389，断言空 topic 从轮询集合移除）钉住；`TestLocalMessageService.testSubscribeIsIndependentPerCallAndCancelDoesNotCrossAffect:69-73` 与 `testRedeliveryScheduleRejectionIsWarnedNotThrown:114-117` 也直接读写 `service.getConsumers()` 活 map。C1-008 建议的修复（getConsumers 收窄为包私有/返回快照）将同时破坏生产代码与 3 个测试——跨模块隐式契约已被测试固化为公共 API。

### 反向钉死 #5（软钉/已声明契约，命中 C1-002 同族子面）：setOffsets REPLACE 语义被钉
`TestNopStreamOffsetBackingStore.testNopStreamOffsetBackingStoreHelperGetSetOffsets:90-98` 以 R5-CON-03 注释（"nop checkpoint is the single source of truth... replace semantics"）把 `setOffsets` 的 clear+put REPLACE 语义钉为正确行为。C1-002 失效面枚举已登记「REPLACE 若在引擎运行中被调用会回滚新写入」为同族面——若并发修复选择把 setOffsets 改为条件合并/世代化语义，该测试变红。因 REPLACE 有明确设计依据（checkpoint 单一事实源），本条定性为**软钉**：修复须一次性裁决「REPLACE 契约 vs 运行期写保护」，不得两案各修一半（同 C5 卷复核对照 1 的同 diff 纪律）。

**反向钉死命中统计**：硬钉 4 处（C3-004 / C5-004 / C1-012 / C1-008 外泄面）+ 软钉 1 处（C1-002 setOffsets 子面）。C1-001 的 `testUnregisteredListenerIsNotInvoked` 与 `TestDebeziumCdcSourceErrorPropagation` 属相邻语义钉（unregister 生效、notify 触达），与竞态面正交，不计为钉死；`TestLocalMessageServiceAsync.testExceptionIsolation`、`consumeLaterDelayHonoredBeforeRedelivery` 等经验证与推荐修复兼容（修复后仍绿），属假保护而非反向钉死。

---

## 缺保护登记

> 登记口径：按同案保护面合并（一条登记覆盖同站点/同协议的多条发现）；严重度默认 P2（维度正文口径），对被保护本体为 P3 的登记条目降为 P3 并在条目内注记（盲用 P2 会造成 P3 缺陷的保护缺口反而高于 P1 的倒挂）。与缺陷本体分开计数。

### [C10-001] DebeziumMessageSource 全类零测试——3 条 P1 无保护的主空洞

- **文件**: `nop-message/nop-message-debezium/src/test/java/io/nop/message/debezium/`（目录内无 DebeziumMessageSource 任何测试；全仓 grep `DebeziumMessageSource` 于 test 源码仅命中 TestDebeziumEngineWrapperFailureListener.java:28 javadoc）
- **证据片段**:
  ```text
  $ grep -rln "DebeziumMessageSource" nop-message/nop-message-debezium/src/test
  nop-message-debezium/src/test/java/io/nop/message/debezium/engine/TestDebeziumEngineWrapperFailureListener.java
  （唯一命中为该文件 javadoc 注释第 28 行的文字引用，非代码触达）
  ```
- **严重程度**: P2（被保护本体含 P1×3：C6-002、C3-002、C8-004——若 7.1 对 P1 缺保护升档，本条为首选升档对象）
- **保护场景**: 订阅线程、引擎 worker 线程（globalWorker）、stop/cancel 调用线程的全部交错；覆盖发现 C6-002（幽灵订阅 P1）、C3-002（转换异常炸流+started 撒谎 P1）、C8-004（吞异常推进 offset P1）、C3-003（subscribe/stop 交叉 P2，吸收 C1-006）、C6-006（action 键注销 P2）
- **缺保护暴露**: 上述 5 条发现的任何回归/修复回退均无测试可红；本次审计认定的模块内后果最接近数据损坏的两条 P1（C8-004 静默丢变更流、C3-002 CDC 静默断流）处在完全无测试区域
- **现状**: 该类是本模块对嵌入方的公开入口（public API），有 5 条保留发现落在其上，测试为零；nop-stream 侧唯一生产调用方有 TestDebeziumCdcSourceErrorPropagation，但用 stub source 绕过了全部真实逻辑
- **风险**: 修复批次的任何 diff（屏障族/锁序族/注销身份族）没有回归闸门；C3-002 与 C8-004 的「同屏障两极性」修复（毒事件 endpoint）是本审计最精细的行为设计，恰恰无任何测试锚定两极语义
- **建议**: 新建 TestDebeziumMessageSource：(1) subscribe 后引擎启动失败 → 断言 subscriptions 不含幽灵条目（C6-002）；(2) convert 抛非法 JSON → 断言引擎不断流且坏事件进毒事件 endpoint 计数（C3-002/C8-004 两极）；(3) 并发 subscribe×stop 交错（ExecutorService+ barrier，N 轮压力）→ 断言无订阅静默丢失、stop 后无引擎复活（C3-003）；(4) 同一方法引用双订阅后 cancel 旧句柄 → 断言现任订阅不受影响（C6-006，复制 TestLocalMessageService.testSubscribeIsIndependentPerCallAndCancelDoesNotCrossAffect 形态）
- **失效面枚举**（同案保护面清单）: C6-002 / C3-002 / C8-004 / C3-003（含 C1-006）/ C6-006 全部无保护；对照组 LocalMessageService 同族语义（WI23 注销身份、异常隔离）有钉——家族内测试形态可直接复制
- **信心水平**: 确定
- **误报排除**: 非「测试在集成层覆盖」——nop-stream 侧测试用 stub source（functionWith 覆写 createMessageSource），DebeziumMessageSource 真实代码在全部测试中零执行
- **复核状态**: 未复核（C10 单轮维度）

### [C10-002] DebeziumEngineWrapper 启停/通知协议面零测试——竞态与生命周期缺口无保护

- **文件**: `nop-message/nop-message-debezium/src/test/java/io/nop/message/debezium/engine/TestDebeziumEngineWrapperFailureListener.java:32-90`（唯一 wrapper 测试，仅静态注册表语义）
- **证据片段**:
  ```java
  // 全部 4 个测试均为"注册→notify→断言收到/不收到"的静态序列：
  DebeziumEngineWrapper.registerFailureListener("wl-test", listener);
  DebeziumEngineWrapper.notifyEngineFailure("wl-test", failure);   // 直接调用，不经引擎
  ```
- **严重程度**: P2（本体 C1-001 P2 / C5-002 P3 / C6-005+C1-011 P3 / C6-007 P3 / C3-009 Debezium 面 P2）
- **保护场景**: 注册/注销线程交叠（C1-001 竞态）；start 失败/stop/restart 状态机（C6-005、C1-011）；引擎死亡路径回收（C6-007）；stop 与注册表关系（C5-002）；Error 穿透 notifyEngineFailure 缺口（C3-009 面④）
- **缺保护暴露**: register→unregister 交叠吞 listener（长驻流静默死亡）无测试；startRequested 不复位、stop 不 join、isRunning 过早翻转、死亡路径 engine 不 close 全部零覆盖
- **现状**: R5-CON-03 机制（failure listener）本体是该模块近年唯一新增的并发防御设施，其测试仅验证「通知能到达」，未验证「通知不丢失」
- **风险**: C1-001 的修复（键升维/条件 remove）与 C6-005/C6-007 的修复（quiesce/join/死亡即回收）同落本类，无测试保护下极易互相遮蔽（改 stop 时破坏注册协议而无感知）
- **建议**: (1) 竞态测试：两线程循环 register/unregister 同名 listener + 并发 notify N 轮，断言终局失败至少被一个现任 listener 收到（C1-001）；(2) 用嵌入式 DebeziumEngine（或抽 worker lambda 为可注入 Runnable）驱动 start 失败→复位、stop→quiesce、run 抛 Error→listener 必达（C6-005/C6-007/C3-009）
- **失效面枚举**: TestDebeziumEngineWrapperFailureListener 4 测试全单线程；nop-stream TestDebeziumCdcSourceErrorPropagation:220 直接 notify（绕过注册协议）；TestNopStreamOffsetBackingStore 与 wrapper 无关——同文件族测试面 = 本条范围全集
- **信心水平**: 确定
- **误报排除**: 非「通知机制已有测试即有保护」——现有测试在缺陷在位时全绿（变异：回退为无条件 remove，全绿），构成假保护的典型形态
- **复核状态**: 未复核（C10 单轮维度）

### [C10-003] NopStreamOffsetBackingStore REGISTRY 生命周期与竞态面无保护

- **文件**: `nop-message/nop-message-debezium/src/test/java/io/nop/message/debezium/engine/TestNopStreamOffsetBackingStore.java:33-37,101-148`
- **证据片段**:
  ```java
  @AfterEach
  void cleanup() {
      NopStreamOffsetBackingStore.clearConnector(CONNECTOR);   // 手工清理——配对缺失被测试惯例掩盖
      NopStreamOffsetBackingStore.clearConnector("_default_");
  }
  ```
- **严重程度**: P2（本体 C1-002/C5-001/C6-004 均 P2）
- **保护场景**: clearConnector 与活引擎 set() 的并发（C1-002 摘除竞态）；stop/生命周期与 REGISTRY 条目释放（C5-001/C6-004）；同名 connector 重启的 stale-resume（C6-004 语义面）
- **缺保护暴露**: 7 个测试全部顺序执行且靠 @AfterEach 手工 clearConnector 收尾——「清理不在生命周期内」这一被立案事实在测试代码中以惯例形式复现；无一条断言「stop 后条目应被释放」或「clear 时活实例写入不得静默丢失」
- **现状**: store 的 SPI 往返/共享/REPLACE/fail-fast（AR-03）语义钉得扎实（含 testConfigureWithoutNameFailsFast 等 3 条 AR-03 回归钉），唯独生命周期与并发面空缺
- **风险**: C5-001+C6-004 同 diff 修复（stop 侧配对清理）与 C1-002 条件移除修复无回归闸门；stale-resume 契约取舍（stop-clear vs 续传）若被未来 diff 无意改变，无测试告警
- **建议**: (1) 生命周期测试：forConnector→store.stop()（或 DebeziumMessageSource.stop()，随 C10-001 测试）→ 断言 REGISTRY 条目按修复后契约释放或显式声明保留；(2) 竞态测试：线程 A 持实例持续 setOffsets，线程 B 循环 clearConnector/forConnector，断言 A 的写入要么全部可见要么收到类型化错误（C1-002）；(3) stale-resume 意向测试：同名 forConnector 二次绑定后断言 offset 来源（新种子 vs 旧条目）按最终契约
- **失效面枚举**: C1-002（竞态面）/ C5-001（无界面）/ C6-004（清理路径+stale-resume 面）全部无保护；testConnectorNameRegistrySharesDataAcrossInstances:102-113 与 testConfigureWithWorkerConfigBindsToRegistry:131-148 钉住共享语义（修复不得破坏，属约束而非保护）
- **信心水平**: 确定
- **误报排除**: 非「store 已有 7 个测试即有保护」——7 个测试全部绕开本三条发现的面；@AfterEach 手工清理恰是 C6-004 所立案「清理靠外部调用方」的测试内实证
- **复核状态**: 未复核（C10 单轮维度）

### [C10-004] Kafka/Pulsar destroy 窗口守卫族无交错测试

- **文件**: `nop-message/nop-message-kafka/src/test/java/io/nop/message/kafka/TestKafkaServiceLifecycle.java:206-236`、`nop-message/nop-message-pulsar/src/test/java/io/nop/message/pulsar/TestPulsarServiceLifecycle.java:160-193`（现有 destroy 测试全部为顺序序）
- **证据片段**:
  ```java
  // 现有 destroy 测试形态：先订阅→再 destroy→再断言顺序（无交叠）
  service.init();
  io.nop.api.core.message.IMessageSubscription subscription = subscribeOne(service, "t-order", "sub-order");
  service.destroy();                       // ← subscribe 与 destroy 无并发
  org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(...);
  ```
- **严重程度**: P2（本体 C1-003/C1-004/C1-005/C1-007/C6-003/C8-007 均 P2）
- **保护场景**: sendAsync/subscribe 调用线程 × doStop 线程交叠：C1-004（producer TOCTOU 泄漏）、C1-005（双读 NPE）、C1-007（add-after-clear 泄漏）、C1-003（守卫次序 NPE）、C6-003（构造后裸奔段）、C8-007（computeIfAbsent 阻塞建连）
- **缺保护暴露**: 全模块唯一的多线程测试是「4 线程并发 cancel 同一订阅」（TestKafkaSubscription:120-148 / TestPulsarServiceLifecycle:103-125）——保护的是 cancel 幂等（WI20/F-25 已修面）；destroy×send/subscribe 方向的交错测试为零
- **现状**: 6 条发现全部依赖交错窗口，而测试底座从未构造过该方向的压力
- **风险**: C1 卷修复批次①（入口 checkIsActive 前置 + 装载与 clear 共享锁/二次排空）落地后，若实现有并发缺陷（如锁粒度引入新死锁），无测试可检
- **建议**: 每后端一组压力测试：(1) N 线程循环 sendAsync × 主线程循环 init/destroy → 断言 future cause 永不为 NPE、destroy 完成后 producers map 无新增条目且全部已 close（C1-004/C1-005）；(2) N 线程循环 subscribe×destroy → 断言无泄漏 consumer（close 计数==订阅计数）且 destroy 后 subscribe 收类型化拒绝（C1-007/C1-003/C6-003）；(3) broker mock 建连挂起时多线程首发同 topic → 断言有界返回（C8-007）
- **失效面枚举**: Kafka 侧 destroy 顺序面（destroyCancelsSubscriptionsBeforeClosingProducer）与 Pulsar 侧同型测试在位（F-27 钉，非缺陷面）；concurrent cancel 幂等面在位（F-25 钉）；其余 6 发现面全部无保护——同族清单见保护力矩阵 #3/#4/#5/#7/#31/#42
- **信心水平**: 确定
- **误报排除**: 非「Lifecycle 测试已覆盖 destroy」——现有 destroy 测试验证的是顺序与调用序，无一在交叠窗口下运行；MockConsumer/MockProducer 支持所需的并发 stub（TestKafkaSubscription 已示范形态）
- **复核状态**: 未复核（C10 单轮维度）

### [C10-005] 消费循环挂死/Error 僵尸面无保护（C3-001+C8-001、C3-009）

- **文件**: `nop-message/nop-message-kafka/src/test/java/io/nop/message/kafka/TestKafkaConsumeTask.java:1139-1154`、`nop-message/nop-message-pulsar/src/test/java/io/nop/message/pulsar/TestPulsarConsumeTask.java:34-85`
- **证据片段**:
  ```java
  // 现有"活性"测试只翻转标志，从不注入挂死/死亡 handler：
  when(consumer.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());
  task.start();
  Thread.sleep(200);
  assertTrue(task.isActive());      // ← handler 从未阻塞/抛 Error，isActive 必然为 true
  ```
- **严重程度**: P2（本体 C3-001+C8-001 P2（P1↔P2 分歧留 7.1）、C3-009 P2（族内 P1 先例留 7.1）——升档则本条随升）
- **保护场景**: handler 返回永不完成 CompletionStage（C3-001/C8-001 五等待点）；handler/codec 抛 Error 穿透 Exception-only 屏障（C3-009 七屏障）；挂死/死亡后 isActive 健康视图（C8-001 F5 面）
- **缺保护暴露**: 五等待点的无上界等待、Error 僵尸化、isActive 误报三个面全部无测试；现有 return-value 协议测试（17+ 个）全部用同步返回值，恰好遮蔽「CompletionStage 可能永不完成」这一协议内输入
- **现状**: 有界停止（MSG-F-54）有钉（stopExitsBoundedlyWithoutCloseWhenNoMessageArrives:589-613 验证 receive 轮内退出），但该机制对 getResult 等待点无效——即测试保护的对象（receive 等待）与缺陷对象（getResult 等待）是同一循环内两个不同等待点，形成「有界停止已被测试证明」的假象
- **风险**: consumeAwaitTimeout 修复（C3-001 主登记）落地后无回归闸门；catch Throwable 改造（C3-009）同样无锚定
- **建议**: (1) handler 返回 new CompletableFuture<>()（永不完成）→ 断言 cancel() 在 5s+1s 预算内返回、executor 终止（C3-001）；修复后追加强断言：消费循环在 consumeAwaitTimeout 后按失败路径 seek/nack；(2) handler 抛 StackOverflowError → 断言 isActive 变 false 或 onConsumeFailure 触发（C3-009）；(3) Local 变体：挂死订阅者 × 后续 send → 断言发送方线程不被永久占用（修复后语义）
- **失效面枚举**: Kafka:356/:516、Pulsar:333/:248、Local:216 五等待点全部无测试；C3-009 七屏障（Kafka:304/Pulsar:118/Local:232/wrapper:164/dispatchEvent:159/回调守卫/bridge:111）全部无 Error 注入测试；对照组 DLQ syncGet 面（Kafka:694）有间接覆盖（deadLetterPublishFailure 路径测试）
- **信心水平**: 确定
- **误报排除**: 非「startAndStopToggleActive 已覆盖活性」——该测试的 handler 立即返回，验证的是标志位赋值而非挂死/死亡下的健康视图
- **复核状态**: 未复核（C10 单轮维度）

### [C10-006] Local 后端失败闭环与生命周期面无保护（C8-002、C6-001、C8-003、C3-006 等）

- **文件**: `nop-message/nop-message-core/src/test/java/io/nop/message/core/`（TestLocalMessageService/TestLocalMessageServiceAsync/LocalMessageServiceContractTest 全集）
- **证据片段**:
  ```java
  // 契约与单测全部单线程顺序驱动：
  service.subscribe("isolation-test", (t, m, c) -> { throw new RuntimeException(...); });
  service.subscribe("isolation-test", (t, m, c) -> { count.incrementAndGet(); return null; });
  service.send("isolation-test", "test-message");          // 发送者=测试线程，无调度拓扑覆盖
  assertEquals(1, count.get());                            // 只断言隔离，不断言失败回调
  ```
- **严重程度**: P2（本体 C8-002 P1——升档候选；C6-001/C8-003/C3-006/C5-003 行为面 P2；C1-008 NPE 面 P3）
- **保护场景**: handler 异常后的第一层 onConsumeFailure 派发（C8-002，设计 §3.3 契约）；红投调度器生命周期（C6-001）；调度线程拓扑/跨订阅时延（C8-003）；suspended×在途红投（C3-006）；pending 无界（C5-003 行为面）；clearConsumers×cancel NPE（C1-008）
- **缺保护暴露**: Local 是唯一通过契约测试的后端，但契约骨架没有「失败派发」维度——Kafka/Pulsar 的 onConsumeFailure 有测试（TestKafkaConsumeTask:1056-1089 等），Local 契约底线（仅第一层回调）反而零断言；grep `onConsumeFailure` 在 core 测试 = 0 命中
- **现状**: Local 后端为生产路径（SysDaoMessageService 承接 nop_sys_event），其 P1 级失败断链（C8-002）在「唯一有契约测试的后端」上无保护
- **风险**: C8-002 修复（补第一层回调）落地后无回归闸门；C6-001 destroy 钩子、C3-006 红投重调度修复同此
- **建议**: (1) 契约骨架新增 dimension「失败派发」：handler 抛异常 → 断言 onConsumeFailure 被调用且发送方线程存活（Local/Kafka/Pulsar 同一测试三跑，Local 现状会红=预期红，修复后转绿）；(2) 生命周期测试：触发红投后断言线程清理（随 C6-001 destroy 钩子）；(3) 组合测试：ConsumeLater(300) + suspend → 断言 resume 后消息按修复后契约恢复（C3-006）；(4) clearConsumers 与 cancel 并发压力（C1-008）
- **失效面枚举**: C8-002 / C6-001 / C8-003 / C3-006 / C5-003 行为面 / C1-008 NPE 面全部无保护；对照组 Kafka/Pulsar 的 onConsumeFailure/backoff/预算测试形态在位（可复制）；反向钉死关联：本条 (4) 的修复若采用「getConsumers 收窄」方案将触雷反向钉死 #4
- **信心水平**: 确定
- **误报排除**: 非「契约测试已覆盖 Local」——12 个契约维度全为成功路径语义；testExceptionIsolation 验证隔离而非失败上报，修复后仍绿（假保护）
- **复核状态**: 未复核（C10 单轮维度）

### [C10-007] ConsumeLater 预算闭环面无保护（C8-005 及三后端扩展）

- **文件**: `nop-message/nop-message-kafka/src/test/java/io/nop/message/kafka/TestKafkaConsumeTask.java:860-921`
- **证据片段**:
  ```java
  // 既有测试只验证"正 delay 被兑现"，从不构造 delay=0：
  long delayMs = 200L;
  ...
  assertTrue(gapMs >= delayMs, "redelivery must wait for the requested delay");
  // 且两测试均手动调用 task.pollAndConsume()——run 循环自旋结构上不可见
  ```
- **严重程度**: P2（本体 C8-005 P2；Pulsar reconsumeLater 无预算扩展与 Local 自旋面随批）
- **保护场景**: ConsumeLater(0)/负 delay 的热旋转（Kafka seek+deferAfterBatch(0)）；连续 defer 无预算/无退出；Pulsar reconsumeLater(0) broker 弹跳；Local schedule(0) 调度线程自旋（C5-003 放大形态）
- **缺保护暴露**: 消费线程 CPU 占用器+同 assignment 全分区冻结（C8-005 主张）无任何测试；三后端的最小生效延迟下限修复无锚
- **现状**: delay 兑现有下界钉（200ms）、批内 delay 兑现有钉（150ms）——恰好的测试形态（验证 delay≥请求值）对 delay=0 输入结构性失明（gap≥0 恒真）
- **风险**: C8-005 修复（最小延迟下限+defer 计数）若引入回归（正常 defer 被过度延迟），consumeLaterDelayHonoredBeforeRedelivery 能拦住下界方向，但拦不住「下限本身未被施加」方向
- **建议**: (1) handler 恒返回 ConsumeLater(0) → 以 MockConsumer 驱动 runTaskForTest 有限轮，断言轮间存在最小等待或 defer 计数告警（C8-005）；(2) 参数化 delay∈{0,-5,200} 的兑现矩阵；(3) Pulsar/Local 同型测试随批
- **失效面枚举**: Kafka 单条 :360-369 / 批量 :529-535 / deferAfterBatch :442-454 全部无 delay≤0 测试；Pulsar :272-275/:336-338 无预算弹跳无测试；Local :218-219 schedule(0) 无测试（契约测试仅 300ms 单例）
- **信心水平**: 确定
- **误报排除**: 非「delay 兑现测试已存在」——其断言（gap≥delay）对 delay=0 为恒真断言，无判别力
- **复核状态**: 未复核（C10 单轮维度）

### [C10-008] reply 预算与 metrics 口径面无保护（C8-006/C8-009/C8-008）

- **文件**: `nop-message/nop-message-kafka/src/test/java/io/nop/message/kafka/TestKafkaConsumeTask.java:374-461`（批量测试，service.sendAsync 全 mock 已完成 future）；`nop-message/nop-message-core/src/test/java/io/nop/message/core/TestMessageMetrics.java:17-28`（仅单测 Counter 类）
- **证据片段**:
  ```java
  // TestMessageMetrics 只验证计数器类自身，从不验证调用点：
  metrics.incrementSent(); metrics.incrementSent();
  assertEquals(2, metrics.sentCount());      // 谁调用、在哪个时点调用——零覆盖
  ```
- **严重程度**: P2（本体 C8-006/C8-009 P2、C8-008 P3——混合条，按最高本体 P2 计，注记 C8-008 面为 P3）
- **保护场景**: Kafka 批量 reply 逐条串行 vs Pulsar 管道化（C8-006）；消费线程承接 max.block.ms 60s 阻塞（C8-009）；批失败 incrementFailed 漏计、Pulsar sent 前置计数、毒消息 endpoint 漏计、Local 全缺（C8-008 四漂移）
- **缺保护暴露**: reply 拓扑、预算侵蚀、四处指标漂移全部零断言；C8-008 的本质是「过载在监控上不可见」——其测试缺位意味着**缺陷自身就是不可见性**，修复后也无锚
- **现状**: 指标是本模块唯一被 owner doc（WI30）声明口径的观测面，却是全模块测试密度最低的面（Counter 类 1 个单测，调用点 0 个断言）
- **风险**: C8-006+C8-009 同批修复（reply 管道化/期限化）改变消费循环时序行为，无测试锁两边语义；指标补齐后口径回归无闸
- **建议**: (1) metrics 调用点测试：批量 handler 一条失败 → 断言 failedCount==1（Kafka/Pulsar 现状会红=预期红）；Pulsar cancel 拒绝 → 断言 sentCount==0（现状会红）；(2) reply 管道化测试：批 3 条 reply、sendAsync 返回延迟 future → 断言总耗时≈max(单条) 而非求和（C8-006，修复后可写）；(3) max.block.ms 探针（复用 CL-022 建议形态：小 bufferMemory+同步 send 阻塞 → 断言有界返回）
- **失效面枚举**: C8-008 四漂移点（Kafka 批 catch/Pulsar 批 catch/Pulsar sent :254/Local 全缺）+ Pulsar 毒消息 endpoint :366-370 扩展面全部无测试；TestMessageMetrics:17-28 为唯一相关测试且不触调用点
- **信心水平**: 确定
- **误报排除**: 非「指标测试属 C9 观测维度不归 C10」——本条登记的是「P1/P2 过载缺陷的发生在监控与测试双面不可见」的保护力空洞，正是 C10 步骤 4（资源/过载测试形态）的命题
- **复核状态**: 未复核（C10 单轮维度）

### [C10-009] Pulsar 预取/缓冲配置面无保护且缺位被反向钉死（C5-004/C5-005）

- **文件**: `nop-message/nop-message-pulsar/src/test/java/io/nop/message/pulsar/TestPulsarServiceLifecycle.java:317-331`（batchReceivePolicyNotWiredWhenUnconfigured）
- **证据片段**:
  ```java
  verify(service.builder, never()).batchReceivePolicy(
          org.mockito.ArgumentMatchers.any());   // ← 把"缺省不设 policy"钉为期望行为
  ```
- **严重程度**: P3（本体 C5-004/C5-005 均 P3）
- **保护场景**: receiverQueueSize 装配面（C5-004，字段不存在）；批路径缺省 BatchReceivePolicy（C5-004，被反向钉死 #2 钉死为「不设」）；maxPendingMessages/blockIfQueueFull 装配面（C5-005，零测试对象）
- **缺保护暴露**: 配置面缺位类发现的「修复」是新增装配——新增对现有测试不可见（除反向钉死 #2 会红），即 C5-004/C5-005 修复的回归保护完全依赖新写测试
- **现状**: batchReceivePolicyWiredWithClientDefaultFloor:303-316 钉了「已配置」分支（含 100ms latency floor，审计 B-1 回归钉）——配置面一半有钉一半钉死在缺陷语义上
- **风险**: 修复 diff 漏掉 builder 装配或映射错键（如 receiverQueueSize 写到错误 builder 方法）时无测试可红
- **建议**: 随 C5-004/C5-005 修复同步新增：(1) batchReceiveCount=0 时断言 BatchReceivePolicy 以文档化缺省被设置（**改写反向钉死 #2 测试**）；(2) receiverQueueSize/maxPendingMessages/blockIfQueueFull 的 builder 装配 captor 断言（形态复制 batchReceivePolicyWiredWithClientDefaultFloor）
- **失效面枚举**: C5-004 消费端两面（receiverQueueSize 无测试对象 / batchReceivePolicy 缺省被钉死）+ C5-005 生产端两面（maxPendingMessages/blockIfQueueFull 无测试对象）全部无保护；已配置分支有钉（batchReceivePolicyWiredWithClientDefaultFloor，非本条范围）
- **信心水平**: 确定
- **误报排除**: 非「配置类字段无需测试」——本仓库既有惯例是配置装配必带 captor 断言（maxPollRecords/batchReceivePolicy 已配置分支/deadLetterPolicy 均有钉），缺位本身偏离仓库自身标准
- **复核状态**: 未复核（C10 单轮维度）

### [C10-010] 配置冻结面无保护且活 map 被测试反向依赖（C1-012）

- **文件**: `nop-message/nop-message-debezium/src/test/java/io/nop/message/debezium/TestDebeziumEngineProperties.java:181,187,207,218`
- **证据片段**:
  ```java
  DebeziumConfig ok = newConfig("mysql");
  ok.getExtraProperties().put("record.processing.threads", "1");   // 测试以活 map 做 setup
  ```
- **严重程度**: P2（本体 C1-012 P2 理论面）
- **保护场景**: 装配终点快照/冻结（C1-012 主修复）；getter 不可变视图（C1-012 修复选项之一，被 4 处测试 setup 阻断）；checkpoint 序列化面（TestDebeziumConfigSerializable 将可变+Serializable blessing 为契约）
- **缺保护暴露**: 「装配后写者缺位」是 C1-012 从理论转实的前提，而该前提（无 in-repo 写者）正被测试代码以活 map 写入方式部分打破——测试自身就是 extraProperties 的运行期写者（虽在装配期）
- **现状**: 钉定语义测试（prefix sweep、processing threads）扎实且注释明言回归意图；冻结/不可变语义零测试
- **风险**: C1-012 修复若选「getter 不可变视图」方案将同时破坏 4 个既有测试——无预先登记会导致修复 diff 与测试冲突在 review 阶段才暴露
- **建议**: (1) 修复选型时先改造 4 处 setup 为 `config.setExtraProperties(new HashMap<>(Map.of(...)))`（不动 getter 契约）；(2) 新增冻结测试：doStart/buildProperties 后 getExtraProps() 断言 UnsupportedOperationException（按最终选型）；(3) 并发写压力：装配后一线程 put × 一线程 buildProperties 循环 → 断言无 CME（修复后语义锚）
- **失效面枚举**: KafkaClientConfig.extraProps 面（extraPropsCannotOverrideManagedKeys:963-984 钉合并语义，无冻结断言）/ DebeziumConfig.extraProperties 面（本条）/ Pulsar 面（豁免，无集合字段）——同族登记与 C1-012 失效面枚举一一对应
- **信心水平**: 确定
- **误报排除**: 非「理论面缺陷无需测试」——C1-012 的风险陈述（未来配置刷新即转实）恰恰需要冻结测试作为结构保证而非依赖写者纪律
- **复核状态**: 未复核（C10 单轮维度）

### [C10-011] 消费失败可见性/契约完备面无保护（C3-008/C3-010/C1-010/C3-007 残留）

- **文件**: `nop-message/nop-message-kafka/src/test/java/io/nop/message/kafka/TestKafkaConsumeTask.java:88-106`（commitSync 仅 happy-path verify）；`nop-message/nop-message-pulsar/src/test/java/io/nop/message/pulsar/TestPulsarConsumeTask.java`（无 null 响应测试）
- **证据片段**:
  ```java
  // commitSync 只被 verify 调用发生，从未被注入失败：
  task.pollAndConsume();
  verify(consumer).commitSync();       // 无 doThrow(consumer.commitSync()) 任意变体
  ```
- **严重程度**: P3（本体 C3-008 P3 / C3-010 P3 / C1-010 P3 / C3-007 残留 P3）
- **保护场景**: 单路径 commitSync 持续失败的退避/回调缺失（C3-008）；onMessageBatch null 响应的显式契约（C3-010，Kafka 侧 IllegalStateException 同样无钉）；cancelReason 可见性（C1-010，零读者）；resolveSendTimeoutMs 双缺省无上界（C3-007 残留①）
- **缺保护暴露**: 四面全部无测试；C3-010 的修复（对齐 Kafka 显式 IllegalStateException）在 Pulsar 侧无锚、Kafka 侧现状行为也无钉——两侧都靠「恰好没有测试」维持
- **现状**: 这些是五卷中的 P3 尾部，但共同特征是**契约完备性**——不钉住的契约在重构中最易静默漂移（C3-010 风险陈述原话：「未来若 :239 之前的代码结构变化，行为无保护测试锚定」）
- **风险**: 低；但修复成本同样低（均为单断言级测试），性价比最高的补保护批次
- **建议**: (1) doThrow(commitSync) 注入 → 断言现状（WARN、dispatched=true 不退避）或修复后契约（计入退避）按最终裁定；(2) Pulsar batch null 响应 → 断言整批 nack+类型化诊断（对齐 Kafka 显式契约）；(3) Kafka batch null → 钉 IllegalStateException 现状；(4) cancel()/getCancelReason() 可见性测试（修复写序后断言读 cancelled==true 必见 reason）
- **失效面枚举**: C3-008 单路径 :421-426 无失败注入；批路径 :546/:584-588 有部分对照（rewind 上抛被 batchFailureRewindsEachPartitionToFirstIncompleteRecord 间接触达）；C3-010 两后端 null 契约均无钉；C1-010 无读者（前瞻性）；C3-007 双缺省分支无断言
- **信心水平**: 确定
- **误报排除**: 非「P3 不值得登记」——按合并口径并入单条登记以控制工单数，避免 P3 尾部碎片化
- **复核状态**: 未复核（C10 单轮维度）

### [C10-012] Pulsar suspend 生效语义无保护（C1-009）

- **文件**: `nop-message/nop-message-pulsar/src/test/java/io/nop/message/pulsar/`（grep `suspend|pause` 全零命中）
- **证据片段**:
  ```text
  $ grep -rn "suspend\|pause" nop-message-pulsar/src/test --include=*.java
  （零命中）
  ```
- **严重程度**: P3（本体 C1-009 P3）
- **保护场景**: Pulsar suspend/resume 的调用方线程直呼 consumer.pause/resume 与 Kafka 意图标志+轮首差分应用的模式分歧（C1-009）；suspend 返回后 ≤1s 窗口内 handler 仍被调的效果时延
- **缺保护暴露**: Pulsar 后端的 suspend 在任何测试中从未被调用；契约测试的 suspend 维度因 @Disabled 不运行；Kafka 侧同族（WI20 F-26）有 4 个测试钉住正确模式——F3 家族内单侧有钉、单侧裸奔
- **现状**: 若 Pulsar 侧被「修实例不修类别」式重构（照抄 Kafka 代码）改动，无测试区分两模式；若被反向退化为直呼模式亦无感知
- **风险**: 低（P3 效果时延面）；但作为 WI20 已裁定模式的 F3 残留面，测试缺位使该残留无法被常规回归发现
- **建议**: 复制 TestKafkaServiceLifecycle.suspendStateAppliedByConsumeThreadAndReversedOnResume:243-278 形态到 Pulsar（按 C1-009 最终裁定的模式）：断言 pause/resume 由消费线程差分应用、suspend 返回后 in-flight 投递窗口有声明边界
- **失效面枚举**: Local suspend（契约测试在位，volatile 标志逐派发检查）✓；Kafka（TestKafkaServiceLifecycle:243-278 + TestKafkaSubscription:66-86 + onPartitionsAssignedReappliesPauseWhenSuspended:339-355，3 钉）✓；Pulsar（零）✗；Debezium（无 suspend 概念，不入族）
- **信心水平**: 确定
- **误报排除**: 非「Pulsar 契约测试 @Disabled 已声明」——@Disabled 声明的是 mock 架构限制，不是 suspend 维度的豁免；TestPulsarConsumeTask 已证明 receive 驱动形态可行
- **复核状态**: 未复核（C10 单轮维度）

**缺保护登记统计**：12 条（P2×9：C10-001…C10-008、C10-010；P3×3：C10-009、C10-011、C10-012）。C10-001 为首选升档候选（被保护本体含 P1×3）。

---

## 系统性观察

1. **测试保护力与缺陷分布倒挂：Debezium 卷是零保护区，且恰好压着全部 3 条 P1 中的 2 条最重后果**。nop-message-debezium 的 11 条保留发现（C1×4 + C3/C5/C6/C8 的 Debezium 面）中，DebeziumMessageSource 全类零测试、DebeziumEngineWrapper 仅静态注册表语义有 4 个单线程测试。C8-004（吞异常推进 offset，最接近数据损坏）、C3-002（CDC 静默断流）、C6-002（幽灵订阅）三条 P1 全部无保护；而 C3-002/C8-004 的「同屏障两极性」修复（毒事件 endpoint）是本审计最精细的行为设计，将无任何测试锚定。根因结构性存在：契约测试骨架（AbstractMessageServiceContractTest）从未接入 Debezium 后端，nop-stream 侧唯一测试用 stub source 绕过全部真实代码。

2. **并发测试全部是「调用方纪律」回归钉，零交错测试；唯一的多线程测试形态（并发 cancel）保护的是已修面**。27 个测试文件中多线程原语仅出现在 6 个文件，且全部服务于 WI19/WI20 修复的回归钉（cancel 幂等、线程命名、关闭顺序、有界停止）。C1 卷 8 条竞态/TOCTOU 发现（destroy×send、destroy×subscribe、register×unregister、clear×set）无一有保护——测试底座从未构造过缺陷所在方向的交错。更隐蔽的是「假保护」形态：TestKafkaConsumeTask 手动调 pollAndConsume() 的测试风格使热旋转（C8-005）、挂死等待（C3-001）等 run 循环行为结构性不可见；`consumeLaterDelayHonoredBeforeRedelivery` 的 `gapMs >= delayMs` 断言对 delay=0 恒真。修复批次落地后若无交错测试补充，同族回归必然复发（F10 家族在本仓库的复发史即证据）。

3. **反向钉死密度高（5 处），两处分叉语义被成对测试各自合法化**。不止无保护：`sendAfterDestroyIsTypedRejectedNotNpe`（Pulsar 同步抛）与 `producerSendSyncExceptionBecomesFailedFuture`（Kafka rejected future）把 C3-004 的契约分叉分别钉为各自后端的「正确行为」；`batchReceivePolicyNotWiredWhenUnconfigured` 把 C5-004 的配置缺位钉为契约；TestDebeziumEngineProperties 4 处以活 map 做 setup 使 C1-012 的冻结方案与测试冲突；SysDao/Local 测试把 C1-008 的内部 map 外泄协议固化为跨模块公共 API。修复这些发现的 diff 必须先改测试，且评审需显式防「为保测试绿而放弃/削弱修复」——建议 WI 4.1 修复 plan 将反向钉死 #1–#4 列为对应工单的强制联动改动。

4. **（补充）过载面「双不可见」**：C8-008 立案的正是「过载在监控上不可见」，而该面同时无任何测试——缺陷本体与测试保护在同一个点上双重缺位，使过载类 P1/P2（C8-005/006/009）在发生、告警、回归检测三个环节均无信号。指标调用点测试（C10-008 建议 ①）是全模块性价比最高的单个补保护动作（两个断言即可让现状转红、锚定修复）。

---

## 终止判定

C10 为单轮维度（调度规则：最后批次，消费前序缺陷清单，无深挖轮次），本轮即收口。44 条最终保留发现的保护力矩阵、反向钉死检查（5 处命中）与缺保护登记（12 条）一次交付完毕；无新增产品行为缺陷（本维度产出均为测试面工单），无需第二轮。

（本文件为 C10 维度单批次产出；所有 file:line 以 2026-10-20 live code 为准，相对路径根为仓库根。）

---

## C10 复核结论

- 复核人：C10 维度独立复核员（WI 4.1 C10 复核批次，2026-10-20）
- 复核方法：44 行矩阵 × 五维度「最终保留项」逐条对账；17 行矩阵抽查回测测试源码（假保护 9 行 + 无测试 8 行，抽中行全覆盖两判定形态各 ≥3 的要求）；反向钉死 5 处逐处核对测试断言原文与生产代码锚点；12 条缺保护登记逐条格式/编号检查 + 44 行覆盖闭合映射；全部行号按 2026-10-20 live code（nop-entropy-concurrency-audit worktree，HEAD 1407fc426e）重验。

### 1. 矩阵完整性对账——差异 0

- 44 行与五卷最终保留项一一对应：C1×12 / C3×10 / C5×6 / C6×7 / C8×9，无遗漏、无重复、无幽灵行。合并关系注记一致（C1-006→C3-003、C1-011→C6-005 在 C1 卷最终保留项与本矩阵 #6/#11 双侧登记）。
- 严重度逐卷核对全部吻合：C1 P2×8/P3×4；C3 P1×1/P2×6/P3×3；C5 P2×2/P3×4；C6 P1×1/P2×4/P3×2；C8 P1×2/P2×6/P3×1（C8-001 计 P2，与 C8-round2「降级 P1→P2 并入 C3-001」裁定及 C8 卷最终严重度分布 P1×2/P2×6/P3×1 一致）。合计 P1×4/P2×26/P3×14 ✓。
- 判定统计逐行复核：假保护 23 / 无测试 21 / 会红 0 ✓；分层统计（P1×4＝无测试 3+假保护 1；P2×26＝假 17/无 9；P3×14＝假 5/无 9）逐行验证无误。
- 「派发口径 P2×25/P3×15 差 1 属 C8-001 计数归属」注记成立：C8-001 经 round2 降级并入，归属差异已由 C8 卷显式裁定，不影响本矩阵。

### 2. 抽查结果——判定误判 0，修正项 4（均不改判定）

**假保护抽查 9 行，全部维持**：

| 行 | 缺陷 | 抽查验证 |
|---|---|---|
| #1 | C1-001 | TestDebeziumEngineWrapperFailureListener 4 测试（:40-89）确全为单线程 register→notify→assert 序列，无同键交叠面 ✓ |
| #2 | C1-002 | TestNopStreamOffsetBackingStore 7 测试全顺序执行，@AfterEach:33-37 手工 clearConnector 复现 ✓；:102/:131 共享/绑定语义钉在位 ✓ |
| #16 | C3-004 | TestPulsarServiceLifecycle.sendAfterDestroyIsTypedRejectedNotNpe（:459-467）确为同步 assertThrows(NopException)；对照 Kafka producerSendSyncExceptionBecomesFailedFuture:192-208 钉 rejected future（注释 "never a synchronous throw"）✓ |
| #19 | C3-007 | sendTimeoutBoundsTheReturnedFuture:141 / producerLevelSendTimeoutUsedWhenOptionAbsent:171 两分支在位；全文件仅此两处设 sendTimeout，双缺省无断言 ✓ |
| #26 | C5-004 | batchReceivePolicyNotWiredWhenUnconfigured `verify(never())` 原文复现 ✓ |
| #28 | C5-006 | 前缀钉定测试 :203-224 在位（schemaHistory/offsetStorage 两个 FailsLoud）；grep max.queue.size 于 debezium 测试 = 0 ✓ |
| #36 | C8-001 | startAndStopToggleActive（TestKafkaConsumeTask:1139-1154）确为 mock handler 立即返回 + Thread.sleep(200) + isActive 标志断言，无挂死/健康盲区面 ✓ |
| #37 | C8-002 | testExceptionIsolation（TestLocalMessageServiceAsync:50-64）只断言隔离计数；grep onConsumeFailure 于 LocalMessageService 主代码 = 0、core 测试 = 0 ✓ |
| #40 | C8-005 | consumeLaterDelayHonoredBeforeRedelivery:860-886 确为 200ms + `gapMs >= delayMs` 下界断言（对 delay=0 恒真）+ 手动 pollAndConsume×2（run 循环自旋结构性不可见）✓ |

**无测试抽查 8 行，全部维持**：#6/#15/#30/#39（DebeziumMessageSource 全类零测试：grep 于 nop-message test 源码仅命中 TestDebeziumEngineWrapperFailureListener javadoc 文字）、#9（grep `suspend|pause` 于 Pulsar 测试 = 0；Kafka 对照钉 suspendStateAppliedByConsumeThreadAndReversedOnResume:243 在位）、#13（五等待点 Kafka:356/:516、Pulsar:333/:248、Local:216 逐一在主代码命中；测试内 3 处 `new CompletableFuture<>()` 全为发送侧且均被测试完成，消费侧「永不完成 future」测试确为 0）、#43（TestMessageMetrics 35 行 2 测试仅测 Counter 类自身）、#44（grep buffer.memory 于 Kafka 测试 = 0）。

**「调用方纪律回归钉」形态判定核实成立**：全模块多线程原语按真实交错语义仅存在于 6 个文件且全部为 WI19/WI20 回归钉（TestKafkaSubscription:120 4 线程 cancel、TestKafkaServiceLifecycle:110 线程命名 + :129 关闭时序、TestPulsarServiceLifecycle:138 并发 cancel、TestPulsarConsumeTask:589 有界停止、两 ConsumeTask 的 executor 驱动）。raw grep 另命中 TestLocalMessageService（dead executor 传参）与 AbstractMessageServiceContractTest（未使用的 CountDownLatch import），均不构成多线程原语，不影响「6 个文件」结论。

**修正项 4 处（证据精度，不改判定）**：

1. **TestPulsarServiceLifecycle 引用行号系统性过期**：矩阵 #4（destroyCancels…:160-193→实际 :167-202）、C10-004（:206-236→Kafka 侧 :206 正确；Pulsar 并发 cancel :103-125→实际 :138-164）、C10-009（batchReceivePolicyWiredWithClientDefaultFloor:303-316→实际 :416-439；batchReceivePolicyNotWiredWhenUnconfigured:317-331→实际 :442-456）。漂移与 WI25 时代文件状态吻合（WI27/WI29 两次提交移动行号），方法名与语义断言全部无误；违反本文件「以 2026-10-20 live code 为准」声明，修复工单引用时须以本节行号为准。
2. C10-001/C10-002 证据：javadoc 引用实际在 TestDebeziumEngineWrapperFailureListener.java:26（审计写 :28）；4 测试范围 :40-89 正确。
3. 矩阵 #21：runTaskInvokesOnConsumeFailureWithFullContextAndBacksOff:451-521 位于 **TestPulsarConsumeTask**（行号正确，缺文件限定，易误读为 Kafka 上下文；consumeAttemptCapTriggersFailureEndpoint:332-371 确在 TestKafkaConsumeTask）。
4. 系统性观察 1「nop-message-debezium 的 11 条保留发现（C1×4+…）」计数与任何自然切片不吻合（Debezium 类面发现按矩阵去并入约 13 条；C1 前缀 Debezium 面 6 条）——定性结论（零保护区压 3 条 P1 中的 2 条）不受影响，建议后续引用时改为列清单或重数。

### 3. 反向钉死核对——5 处全部成立，方向无误

- **#1（C3-004）**：断言原文与 ：459-467 逐字一致；「修复须同步改写此测试」方向正确（对齐 rejected future 必使 assertThrows 红）。PulsarMessageService.java:255-262 注释锚点在位（实际 :256-257，在引用范围内）。双后端分叉被两套绿测试各自合法化的判定成立。
- **#2（C5-004）**：`verify(service.builder, never()).batchReceivePolicy(any())` 原文一致（实际 :442-456）；「缺省显式设 policy 使其红」成立。
- **#3（C1-012）**：TestDebeziumEngineProperties :181/:187/:207/:218 四处 `getExtraProperties().put` 逐行命中；TestDebeziumConfigSerializable 序列化往返含 extraProperties 可变断言（:23-71）。「getter 不可变视图方案将破坏 4 测试」成立。
- **#4（C1-008 外泄面）**：SysDaoMessageService.java:617 原文逐字命中；TestSysDaoMessageService.testCancelLastSubscriptionRemovesTopicFromPollSet:374 在位；TestLocalMessageService :70/:117 读写活 map 在位。「跨模块隐式契约被测试固化」成立。
- **#5（C1-002 子面，软钉）**：R5 CON-03 注释与 `replace semantics: only p3 remains` 断言在 TestNopStreamOffsetBackingStore:89-97（审计引 :90-98）；「REPLACE 有设计依据故软钉、修复须一次性裁决」定性合理。
- 未计为钉死的两处（testUnregisteredListenerIsNotInvoked、TestDebeziumCdcSourceErrorPropagation:220）复核同意：均为相邻语义钉，与竞态面正交。

### 4. 缺保护登记复核——12 条全部保留，覆盖缺口 1 项（Major）

- 编号 C10-001…C10-012 连续；12 条均为统一 12 字段结构，硬性字段「失效面枚举」逐条齐备；严重度统计（P2×9/P3×3）与统计行一致；C10-001 升档候选注记与登记口径的防倒挂规则一致。格式适配（以「保护场景/缺保护暴露」替换附录 A 的「并发场景/触发时序」）属测试面工单的合理变形，建议在登记口径中补一句显式声明。
- 抽查 6 条（C10-001/002/003/005/006/012）：证据全部复现（grep 零命中类、@AfterEach 收尾、startAndStopToggleActive 形态、suspend 零命中、onConsumeFailure 零命中），无误报。
- **覆盖闭合缺口（本次复核唯一 Major 发现）**：44 行中 41 行被登记承接，**C3-004（#16，P2）、C3-005（#17，P2）、C5-006（#28，P3）三条在 12 条登记中零提及**（全文件仅出现于矩阵行）。三案均为假保护，按本文件口径属「缺保护」：
  - C3-004 的保护缺口实质由反向钉死 #1 承接（修复联动改测试已在观察 3 列为强制联动），处置路径存在但登记面未闭环——应在 C10-004 失效面枚举补挂或在登记口径节加注；
  - C3-005（批内 ack/reply 完成顺序、ConsumeLater 非早退语义无断言）与 C5-006（max.queue.size 族+字节缺省面零测试）为真实无承接的保护缺口——应分别并入 C10-008（reply 面）与 C10-010/C10-011（配置/契约面），或新增 C10-013 收口。
- **最终保留缺保护登记数：12 条维持（P2×9/P3×3）**，外加 1 项覆盖闭合处置要求（上述三行的承接补挂，不新增独立严重度，随对应登记条目处置）。

### 5. 判定统计

| 项 | 维持 | 修正 | 驳回 |
|---|---|---|---|
| 矩阵行（44） | 44 | 0（4 处行号/文件限定修正，不改判定） | 0 |
| 反向钉死（5） | 5 | 0（1 处锚点行号微偏 :26/:28，1 处 :256-257 在引用范围内） | 0 |
| 缺保护登记（12） | 12 | 1 项覆盖闭合处置（C3-004/C3-005/C5-006 补挂，随母条目） | 0 |

### 6. 口径注记

本文件「判定口径」节将「会红」操作化为「缺陷（重）引入/修复回退时测试会红」，与 C10 正文「补上正确实现后现有哪个测试会红」的变异方向相反。该重定义使反向钉死条目归入「假保护」而非「会红·有保护」——语义上更准确（钉死缺陷语义的测试不构成保护），且文件内已显式声明、并以独立的反向钉死节补偿了正变异方向，两方向合计后信息无丢失。复核认可此口径，登记为口径注记（不影响任何判定）。

**复核收口**：C10-test-protection.md 整体判定成立——矩阵对账零差异、抽查零误判、反向钉死 5/5 核实、登记 12/12 保留；随本节生效 4 处证据行号修正与 1 项覆盖闭合处置（C3-004/C3-005/C5-006 补挂至 C10-004/C10-008/C10-010 或 C10-011 的失效面枚举）。C10 维度维持单轮收口结论。
