# nop-message mission WI35 独立收口审计报告

- 审计日期：2026-10-05
- 审计者：独立 fresh-session 子代理（与 WI1-WI34 执行会话不同 task_id）
- 审计对象：`ai-dev/backlog/nop-message-encapsulation-roadmap.md`（v3）全部 35 个工作项（WI1-WI35）、6 个里程碑（M1-M6）、58 条缺陷（MSG-F-01 至 MSG-F-59，其中 MSG-F-51 为平台先例非缺陷）
- 审计方法：roadmap 全文重读 + live 源码逐文件核验 + git log 提交比对 + 4 条门禁命令亲跑 + mission-driver 解析器亲跑

## 总结论：PASS（附 4 项条件，见文末）

35 个工作项中 33 个 PASS，WI33 的偏离成立但其状态 token 违反 roadmap 自身规则（FAIL-mechanics），WI34 退出条件部分未达成（FAIL-partial）。58 条缺陷中 57 条 closed（均有 file:line 或测试证据），1 条 open（F-44，按声明偏离 deferred）。4 条门禁命令全部 exit 0。

---

## 一、工作项逐项核验（35 项）

| WI | roadmap 状态 | 判定 | 一句话结论 |
|----|----|----|----|
| WI1 | done | PASS | 基线 `ai-dev/analysis/2026-10/nop-message-audit-baseline.md` 在位；plan 01 completed；commit 2e7dcd07df |
| WI2 | done | PASS | 矩阵 `ai-dev/analysis/2026-10/nop-message-capability-matrix.md` 在位（151 行含桩豁免行）；独立子代理 audit 注记在 plan 02；commit 08d2d4ac68 + ad1bbfc3a5 |
| WI3 | done | PASS | 11/11 裁定落 `ai-dev/design/nop-message/`（README + message-contract-design + backend-semantics-design 三文件实测存在）；plan 04 completed；commit 8e7ee04358 |
| WI4 | done | PASS | `nop-message-core/src/test/.../contract/AbstractMessageServiceContractTest.java` 实测覆盖 send×3 + 五种返回值 + ConsumeLater + batch arity + suspend/resume/cancel 幂等 + destroy，Local 为首个 extend 者；commit 71d7d69dbb |
| WI5 | done | PASS | `nop-message-spring-comparison.md` 在位，AckMode/RetryTopic/CommonErrorHandler 不采纳记录在案；commit 9b5ebf8f10 |
| M1 | done | PASS | WI1-WI5 全 done，解锁条件满足 |
| WI6 | done | PASS | `MessageCapability`/`MessageOptionsValidator`/`getCapabilities()` 落 nop-api-core；Kafka/Pulsar/Local 的每个 subscribe 与 sendAsync 入口均实测调用 validator；TerminateConsume/PartialCommit 两信号就位 |
| WI7 | done | PASS | seek 起点移入 `KafkaConsumeTask.onPartitionsAssigned`（rebalance listener，:140-234）；seekToMessage 在 `KafkaMessageService.doSubscribe:400-403` 于 consumer 创建前类型化拒绝到达调用方；旧 `seekToBeginHonoredNotStubbed` 全仓零残留，替换为 pollFirst_* 系非 mock 断言（TestKafkaConsumeTask:630-703）；commit 88405f516f |
| WI8 | done | PASS | Pulsar INIT_SEEK→`subscriptionInitialPosition`（:550-557）、ALWAYS_SEEK_TO_END/seekToTime 同步 seek（:501-508）、seekToMessage 类型化拒绝（:292-295）；seek 失败路径关闭 consumer 防泄漏（:527-541，WI8 audit Major-1）；commit b2aa08b195 |
| WI9 | done | PASS | `PulsarHelper.deriveAckTopic:122-130` 对全名 topic 只前缀末段；`IMessageSubscriber.getAckTopic` javadoc 契约同步改写；E2E（PulsarBrokerE2ETest）+ 单元矩阵双证；commit 175d0955dd |
| WI10 | done | PASS | `PulsarConsumeTask.consume/batchConsume` 失败 rethrow（:314/:379），rollback 失败 addSuppressed，退避与 onConsumeFailure 对消息级失败可达；commit b5c3e80f27 |
| WI11 | done | PASS | F-06 progress 回退（rewindFrom:747-757）、F-07 分区收敛提交+未派发回退（commitConverged:713-726 + rewindUndispatched:729-744）、F-08 批终点（:529-535，ConsumeLater⇒全批不提交不变式）、F-58 warn-once 接线；TestKafkaConsumeTask 40 tests 0F；commit a94def93a5 |
| WI12 | done | PASS | `deferAfterBatch:442-454` 兑现 delay（500ms 分片、中断响应），单条与批量两路径均接线（:368/:534）；commit e81391dd01 |
| WI13 | done | PASS | `resolveGroupId:508-520` D7 前缀模型 `groupId.subscribeName`，缺 subscribeName 类型化拒绝；extraProps 先置不可覆盖管理键（:169-174/:203-207）；commit 8f421b0593 |
| WI14 | done | PASS | `transactional`/`transactionTimeout` 带 `@Deprecated` 弃用期保留（MessageSubscribeOptions），validator warn-once 直调（MessageOptionsValidator:60-63）；Pulsar 事务路径删除、主代码零残留（实测 grep）；at-least-once 基线 javadoc 声明（KafkaConsumeTask:769-782、PulsarConsumeTask:474-500）；commit 8a166066df |
| WI15 | done | PASS | sendTimeout→`future.orTimeout` 两后端接线（Kafka:311-314/Pulsar:261-264）；cancelToken 发送前检查→拒绝 future（Kafka:246-253/Pulsar:245-252，`MessageErrors.ERR_SEND_CANCELLED`）；commit 68d5024932 |
| WI16 | done | PASS | `ConsumeFailure(topic,location,message,attempt,throwable)` 全上下文类型 + `onConsumeFailure` default（WARN 留证 + 桥接 deprecated onException）；两后端三调用点全部迁移；commit 540a32d56c + f41cb05045 |
| WI17 | done | PASS | `KafkaConsumerConfig.maxPollIntervalMs`（:54）接入 buildConsumerProps（:226-227）；batchReceiveTimeout 语义在 SPI javadoc 显式声明（「可能与后端拉取/长轮询阻塞时长绑定…」）；F-39 同步抛包装为失败 future（:290-307）；踢出时序按声明归 WI33；commit f16ba9b0e7 |
| WI18 | done | PASS | 三规则族（msg-pre-poll-seek/msg-stub/msg-warn-per-call）实测生效——本次审计亲跑 `--module nop-message-kafka` 与 `--module nop-message` 均 0 violation + 1 invariant-ok 裁决；fixture self-test 与 CI 接线见 plan 18；commit 7342b6325a |
| M2 | done | PASS | WI6-WI18 全 done，解锁条件满足 |
| WI19 | done | PASS | 两后端 `NamedThreadFactory` daemon + 命名 + fan-out 后缀可区分（Kafka:427-445/Pulsar:514-516）；Pulsar 有界 receive（:322/:189-211）使 stop 不依赖 close 解阻；两 service 统一 LifeCycleSupport；commit 6d4314f174 |
| WI20 | done | PASS | Pulsar doStop 重排 subscriptions→producers→client（:157-188）；Kafka 订阅 AtomicBoolean（:557-559）+ `Cancellable.cancel` synchronized 单 winner（nop-commons Cancellable:44-49）；close-after-termination（Kafka:582-599/Pulsar:600-608）；F-30 buildProducer 守卫 + doSubscribe checkIsActive；commit 76ba6de61e |
| WI21 | done | PASS | Pulsar `DeadLetterPolicy` 接线（:461-481，含 2.8 ack 触发语义与 Shared 限定 warn-once）；Kafka 应用级重发布 + 自环守卫（resolveDeadLetterTopic:527-542）；`MessageConsumeFailureBridge`（nop-retry 桥）在位；commit 6c7c4c90ad |
| WI22 | done | PASS | 退避 base/max 可配置（两 config bean）+ `computeBackoffMs` 指数升级 n 从 0 起；`maxConsumeAttempts` 终点两后端落地（Kafka 单条/批量 + Pulsar 单条），批量终点 publish-before-commit 顺序有回退测试钉定；跨重启语义（Pulsar redeliveryCount）在 config javadoc 与 design 3.3.2 声明；commit c4662d0690 |
| WI23 | done | PASS | Local 独立 Subscription 实例 + `remove(this)` 身份取消（:74-81/:187-190）；subscribeName/concurrency/subscriptionType warn-once 处置（:171-185）；ConsumeLater 拦截改延迟重投（:218-219/:250-270）；commit 46b45d1260 |
| M3 | done | PASS | WI19-WI23 全 done，解锁条件满足 |
| WI24 | done | PASS | 头序翻转：两 Helper 上游头先拷、框架元数据后写（KafkaHelper:56-82/PulsarHelper:28-71）；nop-id fill-if-absent 双后端；`MessageValueCodec` 共享编码去重（F-31）；`TopicMessage` 无参构造 + 字段值 equals/hashCode（F-59）；D8 零改名；commit 51aa4443e5 |
| WI25 | done | PASS | Pulsar 成对迭代 `n=min(responses,messages)` + 双向 arity WARN + 尾部 nack/豁免规则（:245-299）；Kafka per-subscribe `max.poll.records` 覆盖（:229-231）+ Pulsar BatchReceivePolicy gate+100ms 兜底（:441-447）；按记录语义落 design 3.10；commit c30c594e39 |
| WI26 | done | PASS | 三接口前缀 `nop.err.message.*`（KafkaErrors:16/PulsarErrors:13/MessageCoreErrors）；五死码接线（Kafka consumer-create:412/send:282,303；Pulsar client:131/producer:231/consumer:493）+ serialization 码删除；`MessageCoreErrors` 非空（ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED 且有调用点）；commit 808b5171b6 |
| WI27 | done | PASS | `SubscriptionMode` 中立枚举加性引入 + `resolveEffectivePulsarType` 单一来源（:371-391）；SINGLE_ACTIVE 钳制 1 warn-once（:329-334）、未配置+N 显式 Shared warn-once（:335-340），原硬失败消除；commit bda6d8e721 |
| WI28 | done | PASS | 四类后端 concurrency 语义矩阵 + 多 JVM 边界落 design 3.11（:128）；`MessageSubscribeOptions.concurrency` javadoc 与实现一致（实测含 SysDao/Kafka 多 JVM 行）；纯文档零行为变更；commit 2e6bce201d |
| WI29 | done | PASS | F-18/F-19/F-35/F-36 物理删除（LEGAL_TOPIC_PATTERN、PulsarConsumeTask.schedule、enableTransaction、maxTotalReceiverQueueSizeAcrossPartitions 全仓 grep 零残留）；F-29 新码 `ERR_PULSAR_SUBSCRIBE_NAME_NOT_CONFIGURED`（:320-323）；F-34 javadoc 修正（KafkaConsumerConfig:13-16 指向 doSubscribe）；F-37 `List.copyOf` 快照 + 空列表 false；F-38 register() 失败取消全部已建订阅；commit dd529cd7a6 |
| M4 | done | PASS | WI24-WI29 全 done，解锁条件满足 |
| WI30 | done | PASS | `MessageMetrics`（sent/consumed/failed Counter + send Timer，micrometer 平台口径）落 nop-message-core；两后端 send/consume 路径接线，null registry=noop；模块图变更显式声明（两后端 pom 新增 nop-message-core，实测在位）；commit 54a3ee09ca |
| WI31 | done | PASS | `nop-message-model` 目录已删除（ls 实证）；codec 保留目录归 FU-1（D10(c) 降级）；`nop-bom/pom.xml` 补登（:693-713 四模块全在）；commit bc59176702 |
| WI32 | done | PASS（附记录性缺口） | 两后端 `XxxMessageServiceContractTest extends AbstractMessageServiceContractTest`（各 @Disabled 14 skip，禁用理由完整）+ 组件级测试；本次实跑 97 tests 0F + 28 skipped。**缺口**：plan 32 Phase 复选框「11 实现覆盖/豁免表落 design」未勾而 Closure Gates 已勾「覆盖/豁免表落档」，且 design 中未见 11 项单一清单（实质散落：matrix:151 桩豁免行 + message-contract-design 后端处置表 + @Disabled 理由）——见发现 D-3；另该 plan 的「独立子 agent closure audit」以 self-audit 代行 |
| WI33 | deferred | FAIL（mechanics）/ N-A（实质） | 实质偏离成立：`KafkaBrokerE2ETest`/`PulsarBrokerE2ETest` 门控脚手架在位（`@EnabledIfSystemProperty` 与 owner doc 属性名一致，默认跳过，实测各 5 skip），符合「不得以跳过态标记 done」。**但状态 token `deferred` 不在合法集 {todo/ready/planned/done}**（roadmap-check.mjs:14,20），该行被解析器静默丢弃——见发现 D-1；commit 4d20c38ef7 |
| WI34 | done | FAIL（partial） | owner doc `docs-for-ai/03-modules/nop-message.md`（86 行八节）+ `INDEX.md:137` 登记均实测在位，F-43 实质收口；**但 WI34 提交（101466b）未触及 `docs-for-ai/04-reference/source-anchors.md`，全 docs-for-ai 无独立锚点前缀（MSG-* 零命中）**，而 plan 34 勾选了「[x] source-anchors 登记」——见发现 D-2；「对齐既有 4 处顺带提及」未见对应 diff（4 处提及本身仍准确，属低危） |
| WI35 | todo | N-A（本轮即其退出条件） | 本报告即 WI35 完成判定载体；审计通过后由 executor 翻 done 并更新当日日志 |

### 里程碑一致性

- M1（WI1-5）、M2（WI6-18）、M3（WI19-23）、M4（WI24-29）：全 done，与其成员 WI 状态一致，无提前翻转。
- M5：`todo` —— 正确。WI33 未 done（deferred），解锁条件不满足，未违规提前翻转。
- M6：`todo` —— 正确（依赖 M5 与 WI35）。
- WI34 状态行的 deps 括注记录了 M5 偏离（「WI33 deferred pending broker infra——owner doc 覆盖面不依赖 broker 执行结果」），偏离已声明且理由成立。

### git log 覆盖面

35 个 WI 全部有对应 commit（WI1 2e7dcd07df、WI2 08d2d4ac68、WI3 8e7ee04358、WI4 71d7d69dbb、WI5 9b5ebf8f10、WI6 3a87df66a0、WI7 88405f516f、WI8 b2aa08b195、WI9 175d0955dd、WI10 b5c3e80f27、WI11 a94def93a5、WI12 e81391dd01、WI13 8f421b0593、WI14 8a166066df、WI15 68d5024932、WI16 540a32d56c、WI17 f16ba9b0e7、WI18 7342b6325a、WI19 6d4314f174、WI20 76ba6de61e、WI21 6c7c4c90ad、WI22 c4662d0690、WI23 46b45d1260、WI24 51aa4443e5、WI25 c30c594e39、WI26 808b5171b6、WI27 bda6d8e721、WI28 2e6bce201d、WI29 dd529cd7a6、WI30 54a3ee09ca、WI31 bc59176702、WI32 9bb8fe5536、WI33 4d20c38ef7、WI34 101466b702）。任务给出的 14 个关键 SHA 全部逐一比对一致。

---

## 二、缺陷逐条闭环核验（58 条）

判定口径：closed = live 代码 + 测试双证或等价强证据；open = 缺陷形态仍在或按声明偏离未执行；ruled-out = 经核验缺陷描述不成立。

### P0 — 静默错误结果

| ID | 判定 | 证据 |
|----|----|----|
| MSG-F-01 | closed | seek 起点解析移入 rebalance listener `KafkaConsumeTask.onPartitionsAssigned:176-234`（INIT 系带 committed-offset 门控 ：195-210，ALWAYS 每次重放 ：193-194，seekToTime 仅一次 ：213-216）；pollFirst_* 三测试（TestKafkaConsumeTask:630-703）以真实 poll 流驱动 |
| MSG-F-02 | closed | seekToMessage 在 `KafkaMessageService.doSubscribe:400-403` 类型化拒绝（ERR_KAFKA_SEEK_NOT_SUPPORTED），先于 consumer 创建、在调用方线程；Pulsar 侧对称（PulsarMessageService:292-295） |
| MSG-F-03 | closed | 空方法体与 TODO 消除：INIT→`subscriptionInitialPosition`（PulsarMessageService:550-557）、ALWAYS/seekToTime 同步 seek（:501-508）、seekToMessage 类型化拒绝（:292-295）；TestPulsarConsumeTask 24 tests 0F |
| MSG-F-04 | closed | `PulsarHelper.deriveAckTopic:122-130` 对 persistent://全名只前缀末段；`IMessageSubscriber.getAckTopic` javadoc 契约要求全名后端 override；Kafka/Pulsar 矩阵 + gated E2E 双证（WI9 plan audit 七项 PASS） |
| MSG-F-05 | closed | `PulsarConsumeTask` consume/batchConsume 失败 rethrow（:314/:379）+ rollback 失败 suppressed（:306-311/:371-376）；runTask 退避（:117-130）与 onConsumeFailure 对消息级失败可达；传播测试断言 onException 原实例 + gap≥backoff 下限（WI10 audit） |
| MSG-F-06 | closed | 批量失败按 progress 指针回退 `rewindFrom`（KafkaConsumeTask:599/:747-757），批不再被跳过 |
| MSG-F-07 | closed | `commitConverged` 分区收敛到最高已定案 record（:713-726）+ `rewindUndispatched` 回退未派发（:729-744）；无参 commitSync 不再越过未派发记录 |
| MSG-F-08 | closed | ConsumeLater 为批终点：停止派发、重 seek、全批不提交（:529-535，不变式见 javadoc :471-477） |
| MSG-F-09 | closed | `deferAfterBatch:442-454` 兑现 getDelay（分片 sleep、中断响应），单条 :368 / 批量 :534 两路径接线；CPU 自旋消除 |
| MSG-F-10 | closed | `KafkaConsumerConfig.maxPollIntervalMs:54` 默认 300000，接入 `buildConsumerProps:226-227`，javadoc 与 max.poll.records 交互自洽 |
| MSG-F-11 | closed | 语义在 SPI javadoc 显式声明（MessageSubscribeOptions.batchReceiveTimeout：「该值的语义映射依后端而定…进入其活性/踢出预算」），design 3.6.1 D-entry 记录被拒方案 |
| MSG-F-12 | closed | D7 前缀模型 `resolveGroupId:508-520`：subscribeName 必填（缺省类型化拒绝），全局 groupId 降级为命名空间前缀，异构订阅不再共享分配域 |
| MSG-F-13 | closed | D11(b) 落地：`transactional` 带 `@Deprecated` 弃用期 + validator warn-once（MessageOptionsValidator:60-63）+ 接口 javadoc 改写；不再是零处置静默 |
| MSG-F-14 | closed | Local `invokeSubscription:218-219` 拦截 ConsumeLater → `scheduleRedelivery:250-270`（fire 时状态重检）；`handleMessageResult` javadoc 声明 ConsumeLater 不可达 |
| MSG-F-15 | closed | subscribe 每次创建独立 Subscription（:187-190）、cancel 按 `remove(this)` 身份移除（:80）、redelivery contains 实例判等（:259）；零 findSubscription 残留 |
| MSG-F-16 | closed | 成对迭代 `n=min(responses,messages)`（:245-246）；双向失配确定行为：多余响应 WARN 丢弃（:288-291）、尾部 nack（:292-299）；早退豁免规则声明 |
| MSG-F-58 | closed | 批路径无参 commitSync 消除（分区收敛边界）；`warnIfAutoCommit:454-460` 对 enable.auto.commit=true warn-once（F-58 的陷阱前提显式化）；单条路径无参提交仅在全部 record 成功后到达（失败在 ：417 throw） |

### P1 — 可靠性与生命周期

| ID | 判定 | 证据 |
|----|----|----|
| MSG-F-17 | closed | Kafka：per-subscribe `batchReceiveCount>0` 覆盖 max.poll.records（:229-231）；Pulsar：`BatchReceivePolicy.maxNumMessages` gate + 100ms timeout 兜底（:441-447）；不对称性声明于 design 3.10 |
| MSG-F-18 | closed | `PulsarConsumerConfig.maxTotalReceiverQueueSizeAcrossPartitions` 物理删除（文件实测无此字段） |
| MSG-F-19 | closed | `PulsarClientConfig.enableTransaction` 物理删除（仅剩 serviceUrl）；主代码事务引用零残留（实测 grep） |
| MSG-F-20 | closed | `IMessageConsumer.onConsumeFailure(ConsumeFailure)` 全上下文 default（WARN 留证 + 桥接）；`onException` @Deprecated；两后端 dispatchConsumeFailure + runFailureEndpoint 均以全上下文调用（KafkaConsumeTask:614-631/:668-669、PulsarConsumeTask:432-449/:401-402） |
| MSG-F-21 | closed | `consumeErrorBackoffMs/MaxMs` 两 config bean 可配置（PulsarConsumerConfig:37-40、KafkaConsumerConfig:69-72）+ `computeBackoffMs` 指数升级封顶（两 task :326-332/:139-145），硬编码常量消除 |
| MSG-F-22 | closed | 失败终点语义：`maxConsumeAttempts` 终点 + `runFailureEndpoint`（Kafka :643-678 DLQ 重发布优先于提交、Pulsar :391-410 终结 ack + 原生 DLQ 分流）；毒消息不再空转；design 3.3/3.3.2 落档 |
| MSG-F-23 | closed | `MessageMetrics`（sent/consumed/failed + send Timer）按平台 micrometer 口径；两后端发送与消费路径接线（Kafka:80-84/:315-316/:358/:399、Pulsar:81-85/:244/:333/:369）；null registry=noop |
| MSG-F-24 | closed | 两后端消费线程 daemon + `nop-msg-{kafka,pulsar}-consume-<identity>` 命名 + NamedThreadFactory 后缀区分 fan-out（Kafka:427-445、Pulsar:510-516） |
| MSG-F-25 | closed | Kafka 订阅 `AtomicBoolean.compareAndSet`（:578-581）；`Cancellable.cancel` synchronized 单 winner（nop-commons Cancellable:44-49），cancelReason 归因确定 |
| MSG-F-26 | closed | cancel 序：stop→shutdown→awaitTermination(5s+1s)→close-after-termination（Kafka:582-599/Pulsar:586-608）；suspend/resume 由消费线程 applySuspendState 串行化（Kafka:261-275/:288-290），残余病态尾窗 WARN + design 3.8.1 声明 |
| MSG-F-27 | closed | Pulsar doStop 重排为 subscriptions→producers→client（:157-188），与 Kafka 一致 |
| MSG-F-28 | closed | extraProps 最先 putAll（producer :172-174、consumer :205-207），管理键后写不可被覆盖，javadoc 显式声明 |
| MSG-F-29 | closed | Pulsar subscribeName 必填类型化拒绝 `ERR_PULSAR_SUBSCRIBE_NAME_NOT_CONFIGURED`（:320-323），随机订阅名消除 |
| MSG-F-30 | closed | `initialized` 标志 + LifeCycleSupport 状态机幂等（Kafka:97/:128/:163）；Pulsar `buildProducer` client==null 守卫（:217-220）+ doSubscribe `checkIsActive`（:424）；`ERR_PULSAR_SERVICE_NOT_ACTIVE` 类型化 |
| MSG-F-31 | closed | 共享 `MessageValueCodec.encodeValue`：Kafka `toWireValue:336-338`、Pulsar `builder.value(encodeValue(data)):112`，非 String payload 两后端 wire 一致 |
| MSG-F-32 | closed | `resolveEffectivePulsarType`（:371-391）+ SINGLE_ACTIVE 钳制 1 warn-once（:329-334）+ 默认 Shared warn-once（:335-340）；builder.subscribe 硬失败形态消除 |
| MSG-F-33 | closed | 上游头先拷贝、框架元数据后写：KafkaHelper:56-82（bizKey 后移 ：65-67）、PulsarHelper:28-56；同名上游头不可覆盖真实 topic/partition/offset/id |
| MSG-F-34 | closed | `KafkaConsumerConfig` javadoc 修正为「validated non-empty in KafkaMessageService.doSubscribe」（:13-16），漂移消除 |
| MSG-F-35 | closed | `StreamTopicNaming.LEGAL_TOPIC_PATTERN` 全仓 grep 零残留（已删除） |
| MSG-F-36 | closed | `PulsarConsumeTask.schedule()` 已不存在于源文件（全文读取实证） |
| MSG-F-37 | closed | `MultiMessageSubscription` 构造器 `List.copyOf` 不可变快照 + 空列表 isSuspended/isCancelled 返回 false（实测源码）；两后端改 fill-then-construct（Kafka:366-387/Pulsar:341-362，失败路径 cancel+suppressed） |
| MSG-F-38 | closed | `ReflectionMessageSubscriptionRegistrar.register()` catch 中逐个 cancel 已建订阅后 rethrow（:46-57） |
| MSG-F-39 | closed | `prod.send` 同步异常捕获包装为失败 future（Kafka:290-307，含 InterruptException 中断恢复）；`resolveSendTimeoutMs` 兜底或Timeout；契约恢复为 CompletionStage |
| MSG-F-40 | closed | 前缀迁移 `nop.err.message.{kafka,pulsar,core}.*` 实测三文件；旧前缀字面量零残留（plan 26 审计逐条核验 25 处日志 key 豁免）；`MessageCoreErrors` 非空且有调用点（LocalMessageService:267） |
| MSG-F-45 | closed | sendTimeout 两后端接线（orTimeout + config 兜底）；cancelToken 发送前检查两后端接线（拒绝 future，对齐 MessageRpcClient 先例）；「既不消费也不拒绝」形态消除 |
| MSG-F-46 | closed | P0 缺陷现在均有专项测试（本次实跑 Kafka 102 tests/Pulsar 78 tests 全绿，TestKafkaConsumeTask 40 + TestPulsarConsumeTask 24）；误导性旧测试 `seekToBeginHonoredNotStubbed` 全仓零残留，以 pollFirst_* 真实流断言替代 |
| MSG-F-47 | closed | WI32：两后端 contract scaffold extend 骨架（broker-mediated 不可 in-JVM 直通的裁定 + @Disabled 完整理由）+ 组件级 return-value 协议测试；11 实现覆盖/豁免实质散落 matrix:151 与 message-contract-design 后端处置表（**单一清单未聚合**，见发现 D-3） |
| MSG-F-48 | closed | Kafka `buildApiMessage:80-82` fill-if-absent 写 nop-id（派生 location）；Pulsar :49-51 fill-if-absent 写原生 messageId；「Kafka 全不设」消除；Kafka 派生/Pulsar 原生的不对称性已声明 |
| MSG-F-49 | closed | 模块图变更显式声明并落地：两后端 pom 现依赖 `nop-message-core`（实测 pom），契约骨架下沉共享；WI30 plan 登记该变更 |
| MSG-F-50 | closed | design 3.11 四类后端语义矩阵 + 多 JVM/nop-cluster 边界 + `MessageSubscribeOptions.concurrency` javadoc 同步（实测含 SysDao 忽略/Kafka 消费组分摊行） |

### P2 — 契约与产品化

| ID | 判定 | 证据 |
|----|----|----|
| MSG-F-41 | closed | `nop-bom/pom.xml:693-713` 四模块（core/kafka/pulsar/debezium）全在；model/codec 按 D10 处置（删除/降级 FU-1），不再需要 BOM 条目 |
| MSG-F-42 | closed | `nop-message-model` 模块删除（目录级实证，plan 31 全仓零引用 grep）；codec 保留为 FU-1（D10(c) 降级，晋级条件在 Follow-up Backlog 登记） |
| MSG-F-43 | closed | owner doc `docs-for-ai/03-modules/nop-message.md` 新建（八节：broker 切换/能力声明/返回值协议/concurrency/DLQ/事务/seek/错误码/指标/gated E2E）；`INDEX.md:137` 登记；doc-links 0 error（本次亲跑复核）。注：WI34 退出条件中的 source-anchors 锚点前缀缺口归 WI34（发现 D-2），不影响 F-43 缺陷本身的收口 |
| MSG-F-44 | **open（deferred）** | 消息层 gated E2E 脚手架在位且默认跳过（`KafkaBrokerE2ETest:18-19`/`PulsarBrokerE2ETest:18-19`，门控属性与 owner doc 一致），但真实 broker 五场景（重启恢复/毒消息/DLQ/seek/请求-响应）未执行——按声明偏离 deferred，符合「不得以跳过态标记 done」。M5/M6 相应保持 todo |
| MSG-F-51 | N-A（先例） | 平台先例非缺陷（roadmap 自身标注）；模板引用的 `EvalBackendCapability/EvalBackendRegistry` 在 nop-xlang 实测存在；其 (a)(c) 两点不可迁移理由已吸收进 D1 裁定（validator 挂入口而非注册点） |
| MSG-F-52 | closed | D11(b)：Pulsar 事务路径整体删除，`newConsumeContext` 无事务（:455-459），主代码 transactional/newTransaction/enableTransaction 零残留（实测 grep）；每消息建事务形态不复存在 |
| MSG-F-53 | closed | `MessageOptionsValidator.warnOnce` 每 JVM 每 backend+field 一次（本次实测测试输出仅打印一次 option-warn-once）；热路径三处（delay/subscriptionType/seek）改 warn-once；Pulsar 模块静默路径补齐 validator 调用；门禁族 msg-warn-per-call 实测 0 violation |
| MSG-F-54 | closed | Pulsar 有界 receive：单条 `receive(timeout):322`、批量 `receiveBatchBounded` 保留 pending future 的 timeout 轮询（:189-211）；stop 后一个 timeout 轮内有界退出，不依赖 close 解阻（:147-161 javadoc） |
| MSG-F-55 | closed | 五码接线：`ERR_KAFKA_CONSUMER_CREATE_FAILED`（:412）、`ERR_KAFKA_SEND_FAILED`（:282/:303）、`ERR_PULSAR_CLIENT_CREATE_FAILED`（:131）、`ERR_PULSAR_PRODUCER_CREATE_FAILED`（:231）、`ERR_PULSAR_CONSUMER_CREATE_FAILED`（:493）；`ERR_KAFKA_SERIALIZATION_FAILED` 删除（codec 化后无此失败点）；零死码 |
| MSG-F-56 | closed | Local `subscribe` 对 subscribeName/concurrency/subscriptionType 逐字段 warn-once（:171-185）；D1 矩阵 Local 行（message-contract-design）与实现一致；「完全不读」消除 |
| MSG-F-57 | closed | `transactionTimeout` 随 D11 弃用 + warn-once 忽略，`if (timeout > 0)` 静默缺省路径不复存在（主代码零残留） |
| MSG-F-59 | closed | `TopicMessage` 去 final + 无参构造 + 字段值 equals/hashCode（:47-58，ApiMessage 引用等值残差已声明）；`cancelToken` 经 WI15 接线消除「无人兑现的可写 YAML 键」（message-contract-design 3.3 记录） |

**闭环统计：57 closed / 1 open（F-44，deferred 偏离）/ 0 ruled-out / 1 N-A 先例（F-51，不计入 58）**

---

## 三、门禁抽验结果（4 条全部亲跑）

| 命令 | 结果 | 判定 |
|----|----|----|
| `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` | exit 0。Kafka 102 tests 0F（19 skipped = 5 gated E2E + 14 contract scaffold）；Pulsar 78 tests 0F（19 skipped 同口径）。声明的两处 HEAD 存量失败（nop-sys-dao 编译 / nop-orm AOP codegen）均不在本 reactor 依赖链上，未复现 | PASS |
| `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` | exit 0，扫描 30 个 java 文件，critical/high/medium/low 全 0 | PASS |
| `node ai-dev/tools/check-doc-links.mjs --strict` | exit 0，0 error / 5 warning（全部位于无关的 `ai-dev/plans/2320-orm-sharding-strategy.md` 与 `nop-bytecode/06-resource-leak-v1.md` 存量，本 mission 相关文件零断链） | PASS |
| `node ai-dev/tools/check-silent-wrong-result.mjs --module nop-message-kafka`（另加跑 `--module nop-message`） | 两跑均 exit 0；8 规则族全 0 violation；1 处 `invariant-ok` 裁决（KafkaConsumeTask:394 计数键，裁决指向 `18-wi18-gate-rules.md`） | PASS |

附加解析器实测：`parseRoadmapMarkdown` 对本 roadmap 解析得 **34 工作项 + 6 里程碑（40 条目），而非 35+6**——WI33 状态行因非法 token 被静默丢弃（发现 D-1）。roadmap v3 头部「实测 35 工作项全部识别」的断言在 WI33 翻 deferred 后已失效。

---

## 四、审计发现（按严重度）

### D-1（Major，机械合规性）：WI33 状态 token `deferred` 违反 mission-driver 契约，状态行被解析器静默丢弃

- `tools/mission-driver/src/roadmap-check.mjs:14,20` 的合法状态集为 `{todo, ready, planned, done}`，`BULLET_RE` 不匹配 `deferred` → WI33 行整行丢弃，解析结果 34+6=40 ≠ 35+6=41。
- 这正是 roadmap 自身 Rules 警告的 BULLET_RE 静默丢弃陷阱：「token 只允许 todo/ready/planned/done；blocked 与 deferred 等语义写入括注」「改任何状态行后必须重跑 parseRoadmapMarkdown 核对条目数仍为 35」。WI33 翻转时未执行该自检。
- **影响**：mission-driver 进度分母错误（34 非 35），monitor/flow 终局对账将漏计 WI33。
- **整改**：将 WI33 行 token 改为 `todo`（或 `planned`），deferred 语义移入尾部括注（例如「`todo`（deferred pending broker infra…）」），改后重跑解析器确认 35+6。

### D-2（Major，退出条件缺口）：WI34 的 source-anchors 登记与独立锚点前缀未落地，plan 勾选与事实不符

- roadmap WI34 完成判定明文要求「登记 `docs-for-ai/04-reference/source-anchors.md` 并分配独立锚点 ID 前缀」。WI34 提交（101466b）实际只含 4 个文件（roadmap/plan/owner doc/INDEX.md），source-anchors.md 零改动；全 docs-for-ai grep `MSG-` 锚点零命中。
- plan 34 勾选了「[x] source-anchors 登记」——与事实不符。
- **整改**（二选一，由 executor 裁定）：(a) 补登 source-anchors 条目（为 `docs-for-ai/03-modules/nop-message.md` 的关键规则分配如 `MSG-001..` 前缀锚点）；(b) 如实回退 plan 34 该复选框并在 roadmap WI34 括注记录偏离。按本审计立场，(a) 为首选——工作量小且为 roadmap 明文退出条件。
- 次要：F-43 证据列的「对齐既有 4 处顺带提及」未见对应 diff（4 处提及内容本身仍准确，风险低，可随 (a) 一并补齐）。

### D-3（Minor，记录一致性）：WI32 plan 复选框状态自相矛盾，11 实现覆盖/豁免单一清单未在 design 聚合

- plan 32 Phase 1 的「[ ] 11 实现覆盖/豁免表落 design」未勾，而 Closure Gates 勾了「[x] 覆盖/豁免表落档」且 Status Note 声称「11 实现覆盖/豁免表落 design」。实质内容散落三处（`nop-message-capability-matrix.md:151` 桩豁免行、`message-contract-design.md` 后端处置表、两 scaffold 的 @Disabled 理由），未见单一 11 行清单。
- 该 plan 的「独立子 agent closure audit」由 self-audit 代行（plan 执行指南要求独立审计；mission 以 WI35 总审计兜底，但 plan 32 自身标注不准确）。
- **整改**：补一份 11 行清单（或如实改 plan 32 措辞与复选框），并修正「独立子 agent audit」标注为 self-audit + WI35 兜底。

### D-4（Info，已合规确认）：声明的三项偏离全部核实成立

- WI33 deferred：脚手架在位、门控属性一致、默认跳过，未以跳过态标 done（实质合规，仅 token 机械违规即 D-1）。
- nop-sys-dao 编译失败 / nop-orm AOP codegen 失败：两模块均不在 `nop-message-kafka,pulsar -am` 依赖链上，本次 Maven 门禁未受影响，HEAD 工作区的无关改动（nop-orm plan-2318 等）与 nop-message 零交集。偏离声明与实测一致。

---

## 五、Hard constraint 抽查

- 「不得静默违约」：五类选项字段在 Kafka/Pulsar/Local 三后端均有显式三态（validator 能力集 + WARN_ONCE 集实测）；生产代码无空方法体 TODO（scan-hollow 0 findings 亲证）。
- 「SPI 收窄式新增」：`IMessageService` 仍仅 `subscribe`/`sendAsync` 抽象 + default 新增（getCapabilities）；`IMessageConsumer.onMessage` 抽象未变。
- 「不引入 AckMode」「DLQ 不进 SPI 配置」「broker 切换机制不变」「不引入新依赖」「错误两层策略」「复用平台指标口径」：逐项与 live 代码一致（DeadLetterPolicy 在 PulsarConsumerConfig 而非 SPI；beans.xml 覆盖机制未动；`ERR_*` 全走 `ErrorCode.define`；metrics 走 micrometer `nop.message.{backend}.*`）。
- 「行为变更必答回归方」：nop-rpc-core（WI9 plan 记录 30/0 实跑）与 nop-graphql-message 回归证据在对应 plan 中登记。
- 「外部行为不验证不宣称」：WI33 gated 测试默认跳过、M5 未翻转、PulsarConsumerConfig javadoc 明示「Real-broker diversion behavior is verified by the WI33 gated E2E」——未发现以 mock 断言宣称真实 broker 行为的违规。

## 六、最终结论

**PASS（附条件）。** 本 mission 的产品实质——SPI 骨架保留 + 能力声明门禁 + 58 条缺陷中 57 条闭环 + 四条门禁全绿——已达成且证据充分。WI35 的退出条件（逐项核验 + 逐条缺陷结论 + 验收报告）由本报告满足。

翻转为 done 前须完成的条件（按优先级）：

1. **C-1（阻塞 WI35 翻 done）**：修复 D-1——WI33 状态 token 合规化（deferred 移入括注），重跑 `parseRoadmapMarkdown` 确认 35 工作项 + 6 里程碑。
2. **C-2（阻塞 WI35 翻 done）**：处置 D-2——补登 source-anchors 锚点前缀（首选）或如实回退 plan 34 勾选并记录偏离。
3. **C-3（不阻塞，随 C-1/C-2 顺带）**：D-3 记录一致性整改（11 实现清单聚合或 plan 32 措辞如实化）。
4. **C-4（mission 后续）**：F-44 保持 open；WI33 在 broker 基础设施就位后执行 gated E2E，届时翻转 M5/M6。
5. executor 依据本报告翻转 WI35 done 时，按日志规范更新当日 `ai-dev/logs/` 条目。

---

*本报告由独立审计会话产出，未修改任何产品代码与 roadmap 状态。所有 file:line 以 2026-10-05 工作区 HEAD（101466b702）实测为准。*
