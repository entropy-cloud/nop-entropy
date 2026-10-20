# C8 背压、流控与过载行为 — nop-message（WI 4.1）

## 审计范围与口径

- **单批次 4 源码根覆盖声明**：本批次一次性覆盖 `nop-message` 全部 4 个源码根——`nop-message-core`（Local 后端 + bridge + registrar）、`nop-message-kafka`、`nop-message-pulsar`、`nop-message-debezium`（`nop-message-codec` 经核实为空壳模块，仅 pom，设计 3.6 已裁定降级 Follow-up，无审计对象）。逐文件审读全部 main 源码（30 个文件，约 6.1k 行）。
- **Phase 2 收官说明**：C8 为 Phase 2 收官维。CL 线索编号自 `nop-message-CL-019` 起续接（CL-001…018 已被其他维度占用）。C10（测试保护力）按调度规则在最后批次，本文件不覆盖。
- **与 C5 已立案条目的边界声明**：C5 拥有内存有界性（无界集合/缓存/物化的 OOM 面）。本维度只审**过载行为语义**：prefetch 窗口的交付语义（暂停后已预取消息仍投递、poll 缓冲在暂停分区上的行为）、满载时阻塞/丢弃/异常的分支选择、重投预算的存在性与闭环、背压信号传播路径。因此 `failureAttempts` 无界增长、receiverQueueSize 预取无界堆积等**内存面**只记为 CL 线索移交 C5，不在本维度立案。
- **口径纪律声明**：`_tmp/cra-baseline-nop-message.txt` C8a/C8b 节的 grep 命中仅为线索圈定；本文件全部数量结论经人工逐点复现（如消费路径 `FutureHelper.getResult` 等待点 = 5 处：Kafka 2 + Pulsar 2 + Local 1，grep 与逐文件审读一致）。
- **生产-消费边界清单**（C8 执行步骤 1 要求）：
  1. Kafka：`consumer.poll(pollTimeoutMs)` → 单条/批量同步派发（消费线程内联）→ reply/ack/commit。prefetch = `max.poll.records`（默认 500，仅上限）；背压 = 停止 poll（堆积在 broker）；suspend = 消费线程状态重断言（WI20 已裁定，不重审）。
  2. Pulsar：`receive(1s)` / `batchReceiveAsync().get(1s)`（future 跨轮复等）→ 同步派发 → ack/nack/reconsumeLater。prefetch = 客户端 `receiverQueueSize`（模块未配置，走客户端默认——内存面归 C5）；suspend = 客户端 `pause()`（设计 3.8.1 裁定线程安全，已预取消息仍会投递——Pulsar 客户端已知语义，登记不立案）。
  3. Local：`send` → 逐订阅**同步**调用（设计 3.9 声明的投递模型）→ `ConsumeLater` 走单线程 `ScheduledExecutorService` 延迟重投（队列无界）。
  4. 发送面 Kafka：单例 producer，`buffer.memory` 32MB + `max.block.ms` 60s + future `orTimeout(sendTimeoutMs=120s)`（超时弃 underlying send 的 fire-and-forget 残留已在 WI15 javadoc 声明）。
  5. 发送面 Pulsar：per-topic on-demand producer（`computeIfAbsent` 惰性创建），builder `sendTimeout(30s)` + future `orTimeout`；`maxPendingMessages`/`blockIfQueueFull` 未配置 → Pulsar 默认 fail-fast（队列满即异常），无静默丢弃。
  6. Debezium：engine 线程 → `dispatchEvent` 逐订阅同步 fan-out（无队列、无执行器隔离；慢订阅者阻塞 binlog 消费 = CDC 场景正确的背压形态，保留不立案；**失败面**见 C8-004）。
  7. 平台设施核查：`QueueOverflowPolicy`/`ISemaphore`/`HighWatermarkSemaphore`/`IRateLimiter` 在本模块无适用点——生产-消费边界全部委托 broker 客户端或同步调用，自建队列仅 Local 延迟队列一处（无界，见 C8-003）。
- **已裁定边界（本维度不再立案，防止重复计数）**：慢 handler 超 `maxPollIntervalMs` 踢出自愈（设计 3.6.1 WI33 真机 E2E 实证）；Kafka DLQ 发布持续失败导致消费停摆「当前无界，显式声明」（设计 3.3.1）；Local 无 WI22 重试上限、TerminateConsume = WARN 后丢弃（设计 3.9）；Pulsar 批量 ConsumeLater 非早退、与 Kafka 批终点语义不同义（设计 3.10）；close-after-termination 病态尾窗（设计 3.8.1）；`ChannelBacklogProbe` 仅持久化后端实现、broker 后端声明不实现（接口 javadoc + 设计）；Kafka `commitSync()` 失败仅记日志 = at-least-once 重投安全方向（`KafkaConsumeTask.java:421-425`）。

## 发现

### [C8-001] 消费线程对 onMessage 返回的 CompletionStage 无超时阻塞等待——挂死 future 导致订阅僵尸化且 isActive() 误报健康

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaConsumeTask.java:356`（单条）、`:516`（批量）；`nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarConsumeTask.java:333`（单条）、`:248`（批量）；`nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:216`
- **证据片段**:
  ```java
  // KafkaConsumeTask.pollAndConsume (PulsarConsumeTask.consume:333 / batchConsume:248 同形)
  Object response = consumerFn.onMessage(record.topic(), apiMessage, context);
  response = FutureHelper.getResult(response);   // → FutureHelper.syncGet → future.get()，无 timeout
  dispatched = true;
  ```
  `FutureHelper.getResult`（nop-api-core `FutureHelper.java:283-287`）→ `syncGet`（`:158-175`）→ `ContextProvider...syncGet(future)`——整条链**无任何 timeout 参数**；对未完成 future 等价于无限期 `future.get()`。
- **严重程度**: P1
- **并发场景**: 单一消费线程（`nop-msg-kafka-consume-*` / `nop-msg-pulsar-consume-*` daemon 线程）与用户 handler 的异步执行面（用户自管线程池/下游 RPC 客户端）；Local 后端还牵连全局红elivery scheduler 线程（见失效面第 5 项）。
- **触发时序**: T1：handler 返回一个永不完成的 `CompletionStage`（下游 RPC 未配超时、外部系统挂死）。T2：消费线程在 `getResult` 处无限阻塞，run 循环不再迭代。T3（Kafka）：心跳线程按 `max.poll.interval.ms`（默认 300s，设计 3.6.1 WI33 E2E 实证）主动 LeaveGroup，分区移交同组其他成员——**该成员此后永不 poll、永不重新入组**（自愈语义以「handler 最终返回」为前提，永不完成的 future 无此前提）；单成员部署则订阅整体停摆。T4：`KafkaMessageSubscription.isCancelled()==false`、`KafkaConsumeTask.isActive()==true`（`KafkaConsumeTask.java:277-279` 只读 volatile `active`），健康检查视图完全正常——F5「挂死对健康检测不可见」。Pulsar Exclusive 变体：T3' 无 ack → receiver queue 灌满 → 客户端停拉，`ackTimeout` 默认 0 无重投兜底，Exclusive 无接管者 → 订阅冻结。唯一恢复路径是 `cancel()`（`shutdownNow` 中断 `future.get`）。
- **现状**: 五种返回值协议中「CompletionStage = 异步完成后 ack」是契约（`IMessageConsumer.java` javadoc + 设计 3.2），但「异步完成」的等待**无上界**；已声明的只有慢 handler 踢出自愈（以 handler 会返回为前提）。「永不完成的 future → 成员僵尸 + active 误报」未声明、无看门狗、无等待超时选项。
- **风险**: 下游一次无超时调用即把一个订阅变成静默僵尸：多成员组内表现为该成员永久退出消费池（容量静默缩水），单成员部署表现为整条订阅停摆；运维面 `isActive/isCancelled` 全绿，只能靠消费延迟曲线间接发现。 Local 变体（`:216`）额外冻结**全 JVM 所有 topic** 的延迟重投（scheduler 单线程被占）。
- **建议**: 为消费路径的 await 增加有界包装——优先复用平台 `FutureHelper.waitResult(future, timeout)`（已有 timeout 语义）并新增 SPI/配置级 `consumeAwaitTimeout`（超时按消息级失败路径处置：Kafka seek+重投入 WI22 预算、Pulsar nack、Local 留证丢弃），超时值纳入 `maxPollIntervalMs`/ackTimeout 的自洽指引；同时让 run 循环在「单轮派发未返回」期间通过独立的活性时间戳暴露给 `isActive()`（或新增 `isHealthy()`），消除 F5 盲区。
- **失效面枚举**（F3：`FutureHelper.getResult` 在 nop-message 消费路径的全部 5 个调用点逐一登记）: ① `KafkaConsumeTask.java:356`（单条）——本条主证；② `KafkaConsumeTask.java:516`（批量）——同缺陷，且批内第 i 条挂死冻结整批与后续 poll；③ `PulsarConsumeTask.java:333`（单条）——同缺陷，Exclusive 下后果更重（无接管）；④ `PulsarConsumeTask.java:248`（批量）——同缺陷；⑤ `LocalMessageService.java:216`——同缺陷且运行在全局单线程红elivery scheduler 上（放大面见 C8-003）。五处全部同族未修，无一处有超时保护。发送侧 `FutureHelper.syncGet` 调用点（DLQ 发布 `KafkaConsumeTask.java:694`、Pulsar 批 waitAll `:257/:284`、Pulsar ack `:343/:347/:350/:355/:410`）均有 future 级 timeout 或 broker 客户端内部超时兜底，不在本条范围。
- **信心水平**: 机制 = 确定（无 timeout 的 await 是代码事实）；触发前提（业务 handler 返回永不完成的 future）= 很可能（下游客户端未配超时是常见生产形态）。
- **误报排除**: 不属于「慢 handler 踢出自愈」已声明场景——该 E2E 场景的 handler 最终返回并自愈（设计 3.6.1 明文「消费组自愈续投」）；本条是 handler 永不返回时**自愈前提失效**的残留面 + isActive 误报，两部分均无任何声明。也不属于「无状态单例被并发调用」类误报——这是单线程内的等待点上界问题。
- **复核状态**: 未复核
- 复核状态: 降级(P2，同案并入 C3-001 主登记)｜现状:新发现（5 等待点无 timeout 逐点回码确认：KafkaConsumeTask.java:356/:516、PulsarConsumeTask.java:333/:248、LocalMessageService.java:216；`FutureHelper.getResult`→`syncGet`→`ContextProvider.syncGet` 全链无 timeout（FutureHelper.java:158-175/:283-287）、有界替代 `waitResult(future,timeout)` 在位（:262）均实测。**同案命中（本次复核新登记）**：与本单元 C3 卷 C3-001 为同一缺陷——同 5 站点、同根因、同修复（consumeAwaitTimeout），C3-001 已独立复核保留 P2 且失效面为本条超集（多 DLQ 面⑥）；单一缺陷不得跨卷双严重度，按 C1-001 先例降级对齐先案 P2 并入 C3-001 主登记，本条独有增量（F5 活性盲区/isHealthy、Kafka 成员僵尸化细节）并入同 diff；P1↔P2 分歧留 7.1——若升档须两案同批）

### [C8-002] Local 总线消费失败静默丢弃且从不派发第一层 onConsumeFailure 回调——违反设计文档「无 DLQ substrate 后端仅第一层回调」契约

- **文件**: `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:213-235`（`invokeSubscription`）、`:302-310`（`handleMessageResult`）
- **证据片段**:
  ```java
  private void invokeSubscription(Subscription subscription, Object message) {
      ConsumeContext context = new ConsumeContext(subscription.topic);
      try {
          Object ret = FutureHelper.getResult(
                  subscription.consumer.onMessage(subscription.topic, message, context));
          ... // ConsumeLater/Terminate/PartialCommit/handleMessageResult
      } catch (Exception e) {
          LOG.error("nop.message.consumer-failed:topic={},message={}", subscription.topic, message, e);
      }   // ← 吞掉：无重投、无 attempt 计数、无 onConsumeFailure、无 DLQ
  }
  ```
- **严重程度**: P1
- **并发场景**: 发送者线程（Local 的 `send` 是同步逐订阅调用）执行用户 handler；handler 抛出任意异常（下游瞬时不可用、反序列化失败）时与消息生命周期管理的交叠。
- **触发时序**: T1：`send(topic, msg)` 在发送者线程进入 `invokeSubscription`。T2：handler 抛异常（如依赖的 DB 瞬断）。T3：catch 仅记 ERROR 日志即返回——消息对该订阅者**永久丢失**：无重投、无预算、无失败回调；reply 发送失败（`handleMessageResult` 抛出）同样落入此 catch 被吞。T4：后续消息继续投递，失败无任何可观测状态残留（连失败计数都没有）。
- **现状**: 设计 3.2 明文「无 DLQ substrate 的后端（Local/Redis/MQTT/TieredDB）：**仅第一层回调**，终止拒绝后消息丢弃（留证）」——即 Local 的契约底线是至少派发 `onConsumeFailure`。grep 证实 `LocalMessageService` 全文**没有任何 `onConsumeFailure` 调用**（Kafka 2 处、Pulsar 2 处均在）。handler 异常（非 TerminateConsume）被当作隐式 Terminate 处置，绕过了 D5 协议「TerminateConsume(reason) 才是终结信号」的显式性要求。
- **风险**: D11 声明的 at-least-once 基线在 Local 失败路径退化为 at-most-once；三层失败架构（SPI 回调 → 原生 DLQ → nop-retry 桥）在 Local 后端整体断链：`MessageConsumeFailureBridge` 收不到任何事件，部署方声明的重放通道对 Local 完全失效。单测/本地总线场景下失败被静默吞掉，问题直到切到 broker 后端才暴露。
- **建议**: 在 catch 中补齐第一层回调：构造 `ConsumeFailure(topic, location=null, message, attempt, e)` 派发 `config.getConsumer().onConsumeFailure`（对齐 Kafka/Pulsar 的 guarded dispatch 形态，回调抛异常不得杀死发送者线程）；是否加重投（如延迟重投一次）可按设计 3.9「无 broker 重投循环」的立场显式裁决，但「回调断链」应视为对设计 3.2 契约的缺陷修复。
- **失效面枚举**（F3：四后端失败路径闭环逐一登记）: ① Kafka 单条（`KafkaConsumeTask.java:396-418`）——seek+重投+预算+endpoint+回调，闭环 ✓；② Kafka 批量（`:560-606`）——progress 回退+预算+endpoint+回调 ✓；③ Pulsar 单条（`PulsarConsumeTask.java:361-381`）——nack+max(本地,redeliveryCount) 预算+endpoint+回调 ✓；④ Pulsar 批量（`:302-316`）——整批 nack+原生 DLQ（无应用级终点为设计 3.3.2 显式边界）✓；⑤ **Local（本条）——零机制 ✗**；⑥ Debezium（`DebeziumMessageSource.dispatchEvent`）——零机制 ✗（独立立案为 C8-004）。两处同族未修。
- **信心水平**: 确定
- **误报排除**: 设计 3.9 声明的是「重试上限（WI22）不适用 Local」「TerminateConsume = WARN 后丢弃」，均不覆盖 handler **异常**路径的处置；「仅第一层回调」是设计 3.2 对 Local 的明确契约要求，当前实现连第一层都没有——契约与实现的冲突不是已声明边界。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜现状:新发现（catch :232-234 仅 LOG.error、LocalMessageService 全文零 `onConsumeFailure` 调用（grep 独立复现：Kafka :626/:668、Pulsar :418/:461 各 2 处，core 0 处）回码确认；设计文档核验：「无 DLQ substrate 的后端（Local/…）：仅第一层回调」实出自 backend-semantics-design.md **§3.3**（finding 引 §3.2 为出处笔误，不影响实质）+ WI16 派发口径行，§3.9 的 Local 已声明边界仅覆盖 Terminate 丢弃与无 WI22，异常静默丢弃无声明可援引。Local 为生产路径（SysDaoMessageService 承接 nop_sys_event），D11 at-least-once 退化 at-most-once + 三层失败架构断链，P1 维持）

### [C8-003] Local 延迟重投：单线程 scheduler 内联执行用户 handler + 无界延迟队列——全局串行化点与无界账本

- **文件**: `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:250-270`（`scheduleRedelivery`）、`:280-295`（`redeliveryScheduler`）
- **证据片段**:
  ```java
  redeliveryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "nop-local-message-redelivery"); t.setDaemon(true); return t;
  });
  ...
  redeliveryScheduler().schedule(() -> {
      if (subscription.cancelled || subscription.suspended) return;
      ...
      invokeSubscription(subscription, message);   // ← 用户 handler 在 scheduler 线程内联执行
  }, delay, TimeUnit.MILLISECONDS);
  ```
- **严重程度**: P2
- **并发场景**: 全 JVM 唯一的红elivery scheduler 线程 vs 所有订阅的用户 handler（任意耗时/任意阻塞）；发送者线程持续产生新的 `ConsumeLater`。
- **触发时序**: T1：topic-A 订阅者对慢操作返回 `ConsumeLater(5000)`。T2：5s 后 scheduler 线程执行 `invokeSubscription` → handler 内联运行 30s（或经 `getResult` 等待用户 future 更久，交叉引用 C8-001）。T3：期间所有其他 topic 的到期延迟重投全部排队——延迟被兑现的程度取决于**别的订阅者**的 handler 时长，「delay 必须被兑现」契约（D5）实际上不受本订阅者控制。T4：持续生产 + 持续 defer 的负载下，scheduler 的无界 `ScheduledThreadPoolExecutor` 队列随未完成重投消息数线性增长（每项持有消息载荷引用）。
- **现状**: 设计 3.9 声明了「定时器 per-subscription 延迟重投」机制与「重试上限不适用 Local」，**未声明**的是执行拓扑：单一全局线程、用户代码内联执行、无界队列三者的叠加。fire-time 重查（cancelled/suspended/实例在位）已实现且正确。
- **风险**: 一个劣化订阅者的 handler 拖垮全 JVM 延迟重投的时延契约；长时运行下未完成重投账本无界增长（C5 内存面的行为侧表现：无淘汰、无上限、无指标暴露队列深度）。
- **建议**: 复用平台 `io.nop.commons.concurrent.executor`（`ThreadPoolConfig` 队列上限 + `QueueOverflowPolicy`）或按订阅隔离执行器：scheduler 只负责「到点投递信号」，handler 执行转入有界工作队列（满载走声明的 overflow 策略）；同时给延迟队列深度加 Micrometer gauge（对齐 WI30 指标口径）。
- **失效面枚举**: 本模块延迟/异步投递拓扑清单：① Kafka `deferAfterBatch`（`KafkaConsumeTask.java:442-454`）——消费线程自身 sleep，时延只影响本订阅且有 `maxPollIntervalMs` 踢出兜底（已声明），非全局串行点；② Pulsar `reconsumeLater`——broker 侧重投队列，无本地资源占用 ✓；③ Local scheduler（本条）——全局单点 ✗。同族中仅 Local 存在全局串行化点。
- **信心水平**: 确定
- **误报排除**: 不属于「无界声明存在的队列」误报——该 scheduler 队列无任何容量声明与配置入口；也不属于「低频路径粗锁」——延迟重投是 Local 总线的常规路径。设计 3.9 的「不适用 WI22」裁定针对**重投次数预算**，本条针对**执行拓扑与队列上界**，无重叠。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（:285-289 单线程、:253-262 handler 内联、fire-time 重查在位回码确认；delay 兑现契约（设计 3.2「ConsumeLater=延迟重投且 delay 必须被兑现」）受跨订阅 handler 时长侵蚀的论证成立。与 C5-003（堆无界面）/C6-001（生命周期面）为同一 executor 三面，边界干净、一个 diff 批次；本条独有增量=执行拓扑与全局串行化点）

### [C8-004] Debezium dispatchEvent 吞订阅者异常——engine 视为投递成功并推进 offset，CDC 事件对该订阅者永久丢失

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:155-163`
- **证据片段**:
  ```java
  private void dispatchEvent(ChangeEvent event) {
      for (Consumer<ChangeEvent> consumer : subscriptions.keySet()) {
          try {
              consumer.accept(event);
          } catch (Exception e) {
              LOG.error("Error processing CDC event: {}", event, e);   // ← 吞掉 → engine 正常返回 → offset 推进
          }
      }
  }
  ```
- **严重程度**: P1
- **并发场景**: Debezium embedded engine 的投递线程（`DebeziumEngineWrapper.handleDebeziumEvent` → `changeEventConsumer.accept`，engine 经 `GlobalExecutors.globalWorker()` 驱动）与多订阅者 fan-out；任一订阅者的 handler 异常即触发。
- **触发时序**: T1：binlog 事件 E 到达，`dispatchEvent` 依次投递订阅者 A、B。T2：A 的 handler 抛异常（下游库瞬断），被 catch 吞掉；B 正常处理。T3：`dispatchEvent` 正常返回 → `handleDebeziumEvent` 正常返回 → Debezium engine 按其 at-least-once 契约将 E 的 offset 标记为已投递并推进（engine 的语义是「consumer 回调正常返回 = 已交付」）。T4：E 对 A **永久丢失**——无重投、无 attempt 预算、无 DLQ、无失败回调（Debezium 模块无任何失败会计设施）；重启后也从已提交 offset 之后的位点续读。
- **现状**: 「半个成功」（F2）：多订阅者 fan-out 中部分成功部分失败时，失败者的交付责任无人承担；异常被降级为日志即完成 ack。设计文档（backend-semantics-design.md 范围节）未覆盖 nop-message-debezium，本路径无任何已裁定语义。慢订阅者阻塞 engine 线程是 CDC 的正确背压形态（保留），但失败面零语义。
- **风险**: CDC 订阅者一次瞬断即静默丢单条/一批变更事件，且丢的是**变更流**——下游物化视图/缓存/审计与源库进入未声明的静默漂移，无任何可对账痕迹（仅一条 ERROR 日志）。这是本模块内后果最接近数据损坏的过载/失败行为。
- **建议**: 至少补齐交付语义声明 + 失败留痕：对齐 broker 后端形态，为 Debezium 路径引入 `ConsumeFailure` 回调派发与 per-subscriber attempt 计数；若维持「CDC 无重投 substrate」，则失败时应**抛出异常中止本轮投递**（让 engine 不推进 offset，天然获得 at-least-once 重放，代价是故障订阅者阻塞引擎——需在 javadoc 声明该取舍），或按订阅者提供本地重试预算。禁止维持现状的「吞异常 + 推进 offset」组合。
- **失效面枚举**（F3：本文件全部事件消费入口逐一登记）: ① `subscribe`（`:72-80`）原始订阅——经 `dispatchEvent`，本条覆盖；② `subscribeTable`（`:89-99`）/③ `subscribeOperation`（`:108-114`）/④ `subscribeInserts/Updates/Deletes`（`:119-135`）——均为 `subscribe` 的过滤包装，异常面与本条同源同修；⑤ engine 层 `DebeziumEngineWrapper.notifyEngineFailure`（`:79-90`）——engine 终态失败已有 listener 通知（R5-CON-03 已修）✓，与消息级投递失败（本条）是两个层次。消息级投递失败面 4 个入口全部无保护。
- **信心水平**: 确定（engine「回调正常返回即推进 offset」为 Debezium embedded engine 公开契约；吞异常使其失效是代码事实）
- **误报排除**: 非单线程豁免——降档口径下过载/失败行为语义单线程不豁免；非「已声明边界」——backend-semantics-design.md 的范围节明确不含 nop-message-debezium，无裁定可援引；非 F9 纯错误处理维度——吞异常直接改变投递语义（offset 推进），属并发/交付正确性。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜现状:新发现（dispatchEvent :155-163 吞异常→handleDebeziumEvent 正常返回→engine 推进 offset 链回码确认。**子项复核——主路径豁免面收窄**：nop-stream 主路径的订阅回调自带 capture-and-rethrow（DebeziumCdcSourceFunction.java:258-271 pendingError→任务失败→checkpoint 重启），该路径事件不静默丢（at-least-once 保持）；受损面=直接使用 DebeziumMessageSource 的嵌入方（仓内 main 仅示例树）——与 C3-002 完全同口径。**同屏障两极性同 diff 要求核对成立**：本条（投递极性：屏障不得吞）与 C3-002（存活极性：屏障须有）修复为同一 per-event 屏障 + 毒事件 failure endpoint 设计，禁纯吞（本条面）与整体炸流（C3-002 面）两极，维持 P1 并与 C3-002 判级配对，暴露面权重分歧同留 7.1）

### [C8-005] Kafka ConsumeLater 无预算闭环且 delay≤0 退化为热旋转——head-of-line 阻塞全部分区

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaConsumeTask.java:360-369`（单条）、`:529-535`（批量）、`:442-454`（`deferAfterBatch`）
- **证据片段**:
  ```java
  if (response instanceof ConsumeLater) {
      consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
      deferAfterBatch(((ConsumeLater) response).getDelay());   // delay<=0 → sleep(0) → 立即重 poll 同一条
      return dispatched;
  }
  ...
  private void deferAfterBatch(long delayMs) {
      long remaining = Math.max(delayMs, 0L);   // 负 delay 归零，零即无等待
  ```
- **严重程度**: P2
- **并发场景**: 单一消费线程的自我循环（seek 回同一条 + poll 立即返回 Kafka 客户端已预取缓冲中的同一批记录）；无第二线程参与，但后果波及同 assignment 全部分区。
- **触发时序**: T1：handler 对某条记录返回 `ConsumeLater(0)`（「稍后尽快重试」是合法协议用法，D5 未给 delay 下限）。T2：消费线程 seek 回该 offset，`deferAfterBatch(0)` 零睡眠。T3：poll 从本地已预取缓冲立即返回同一条 → handler 再返回 `ConsumeLater(0)` → 无限热旋转：100% CPU 烧在单线程上、无任何网络等待稀释。T4：期间该 assignment 的**其他分区全部停摆**（单线程 head-of-line）；`ConsumeLater` 轮被算作「派发成功轮」（设计 3.3.2 L62），退避不升级；WI22 预算不计数（`failureAttempts` 只在异常路径 bump）——无任何机制使其退出。
- **现状**: 「delay 必须被兑现」已声明（D5），`deferAfterBatch` 的 javadoc 也声明了「delay 超过 poll interval 预算会被踢组」；**未声明**的是 delay=0/负值无下限、无尝试预算、无指标区分（`consumed` 计数在旋转中飙升但无法与正常吞吐区分）。
- **风险**: 一个配置不当的 handler（总是 defer 0）即可把订阅变成 CPU 占用器并冻结所有分区，且无自动退出、无告警信号；运行时长无上界。
- **建议**: 给 `ConsumeLater` 路径加最小生效延迟下限（如 `max(delay, pollTimeoutMs)` 或独立配置键，把「尽快」解释为「下一轮」而非「自旋」）并将连续 defer 轮次计入可观测指标；是否纳入 WI22 预算需设计裁定（ConsumeLater 非失败信号，直接并入会改变协议语义——至少应 WARN 一次以上连续零延迟 defer）。
- **失效面枚举**（F3：ConsumeLater 实现路径全部登记）: ① Kafka 单条（本条）——seek+sleep(delay)，delay≤0 时热旋转 ✗；② Kafka 批量（`:529-535`）——同为批终点 + `deferAfterBatch`，同缺陷 ✗；③ Pulsar 单条（`PulsarConsumeTask.java:336-338`）——`reconsumeLater(message, delay)` 走 broker 延迟重投，有网络往返与 `negativeAckRedeliveryDelay` 兜底，无纯自旋 ✓；④ Pulsar 批量（`:272-275`）——`reconsumeLaterAsync`，同上 ✓；⑤ Local（`LocalMessageService.java:218-219`）——scheduler 延迟重投，delay=0 也有一次线程调度 hop，无纯自旋（但预算缺失见 C8-003）。热旋转面仅 Kafka 两处。
- **信心水平**: 确定（机制）；触发前提（handler 持续返回 ConsumeLater(0)）= 很可能（协议未禁止）
- **误报排除**: 不以「handler 写得不对」豁免——SPI 协议公开允许任意 delay≥0，框架必须为协议内输入提供有界行为；非「已声明」——已声明的只有 delay 兑现与 poll-interval 踢组，均不覆盖零延迟自旋。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（:360-369 seek+deferAfterBatch(delay)、:442-454 Math.max(delay,0) 零值无等待、WI22 不计数（failureAttempts 仅异常路径 bump :406）、`dispatched=true` 使退避重置（:298-300）逐点回码确认。**精度注记**：seek 后 fetch 缓冲对该分区重定位，下一轮 poll 实际发起重新拉取——「本地预取缓冲立即返回」表述偏宽，「无网络等待稀释」在远端 broker 下减弱；但实质主张（无预算闭环/无退出机制/HOL 冻结同 assignment 全分区/CPU 高频空转）全部成立，P2 维持。失效面 ⑤（Local「无纯自旋」✓）判定偏宽：Local 的 schedule(0) 即刻触发→再 ConsumeLater(0) 构成调度线程连续执行热循环，该面已在 C5-003 立案，本条 ⑤ 应改引 C5-003 而非 ✓——非漏立案，仅对照标注修正。round 2 扩展（Pulsar reconsumeLater 无预算弹跳随本条计，不另编号）核验成立）

### [C8-006] Kafka 批量路径逐条同步发送 ack-topic reply 占用 poll 预算——与 Pulsar 批量管道化发送不对称

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaConsumeTask.java:536-541`（批量 `ctx.send`）、`:383-391`（单条 `context.send`）；对照 `nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarConsumeTask.java:271-282`（批量 sendAsync 管道化）
- **证据片段**:
  ```java
  // Kafka batchConsume：每条记录同步等 reply 发送完成
  } else if (response instanceof Acknowledge) {
      ctx.send(service.getAckTopic(record.topic()),          // IMessageSender 默认实现 = syncGet(sendAsync)
              ((Acknowledge) response).getReplyMessage());   // ← 阻塞至 broker ack 或 sendTimeoutMs(默认120s)
  } else if (response != null) {
      ctx.send(service.getAckTopic(record.topic()), response);
  }
  // 对照 Pulsar batchConsume：reply future 入队，循环结束后一次 waitAll
  futures.add(context.sendAsync(getAckTopic(message.getTopicName()), response));
  ```
- **严重程度**: P2
- **并发场景**: 消费线程（poll 预算持有者）× Kafka producer 的 broker 往返；批量模式下每条记录的 reply 串行占用同一线程。
- **触发时序**: T1：批量订阅命中一批 500 条记录，handler 对每条返回 reply。T2：消费线程对每条执行 `ctx.send` = `syncGet`，逐条等待 broker ack——健康网络下每条 1-5ms（producer batching 部分摊薄），一批约 0.5-2.5s，可接受。T3：broker 退化（RTT 升至 100ms）→ 单批 reply 串行耗时 50s；RTT 恶化或个别 reply 超时（`orTimeout(sendTimeoutMs=120s)`）→ 单条最多吃掉 120s，批在 `max.poll.interval.ms=300s` 内无法完成 → LeaveGroup → rewind 重投 → 下一轮同样超时 → 持续 rebalance 风暴（每轮 300s 一次，波及全组）。
- **现状**: 设计 3.3.1 只对 **DLQ 同步发布**声明了「计入 max.poll.interval.ms 预算」；ack-topic reply 的逐条同步语义无声明。设计 3.10 已登记 Pulsar 批 ConsumeLater 非早退差异，但**未登记** reply 发送拓扑差异（Kafka 串行 syncGet vs Pulsar 管道 waitAll）。
- **风险**: 请求-响应场景（ACK_REPLY 能力的主用途）在 broker 轻度退化时从「变慢」直接跳变到「组级 rebalance 风暴」，退化阈值由批大小 × RTT 决定，运维无法从配置面预见。
- **建议**: Kafka 批量路径对齐 Pulsar：reply 改 `sendAsync` 收集 future，批末 `FutureHelper.waitAll` 一次等待（复用 `KafkaMessageService.sendAsync` 已有的 orTimeout 界）；等待总量仍受 `maxPollIntervalMs` 约束但由 N 倍串行降为 max(单条) 级。
- **失效面枚举**（F3：消费路径 reply 发送形态 4 处逐一登记）: ① Kafka 单条 `:385/:389`——同步 send，单条仅一次 RTT，可接受（与 Pulsar 单条 `:349/:353` 形态对称）✓；② **Kafka 批量 `:537/:540`——逐条串行 ✗（本条）**；③ Pulsar 批量 `:277`——管道化 ✓；④ Pulsar 单条——同步 send，同 ① ✓。不对称仅 Kafka 批量一处。
- **信心水平**: 确定
- **误报排除**: 非「原语使用本身」误报——缺陷在复合行为（逐条阻塞 × poll 预算 × 批大小放大）；DLQ 同步发送已在设计 3.3.1 声明并有 WI22 预算治理，本条只针对 reply 路径，无重复计数。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（批量 :536-541 两分支逐条同步 ctx.send vs Pulsar :277 futures 管道化+:284 waitAll 对照回码确认；单条路径两侧对称可接受的失效面枚举成立；退化阈值（批大小×RTT vs max.poll.interval.ms=300s）推演合理。修复与 C8-009 收敛同一「reply 管道化/期限化」改点，同 diff 批次）

### [C8-007] Pulsar 发送面 getProducer 在 computeIfAbsent 内联阻塞式远端建连——broker 故障时同 topic 发送全体串行排队

- **文件**: `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java:210-219`（`getProducer`）、`:221-245`（`buildProducer`）
- **证据片段**:
  ```java
  Producer<?> getProducer(String topic) {
      Producer<?> producer = producers.get(topic);
      if (producer != null) return producer;
      Schema<?> schema = topicSchemas.getOrDefault(topic, Schema.STRING);
      return producers.computeIfAbsent(topic, k -> buildProducer(k, schema));
      // buildProducer → client.newProducer(schema).topic(topic)...create()  ← 阻塞远端调用
  }
  ```
- **严重程度**: P2
- **并发场景**: 多个业务线程 + 消费线程（ack reply 走 `sendPulsarAsync` → `getProducer(ackTopic)`）对同一未建 producer 的 topic 并发首次发送；`ConcurrentHashMap` bin 锁串行化所有竞争者。
- **触发时序**: T1：broker 网络劣化/不可达期间，线程 A 首次向 topic-X 发送，进入 `computeIfAbsent` 的 mapping 函数 → `builder.create()` 发起阻塞式建连（Pulsar 默认 operationTimeout 30s/次，连接期可远超此值），**期间持有该 bin 的锁**。T2：线程 B、C 及消费线程（正在为 reply/DLQ 建 ack-topic producer）对同一 topic 的 `computeIfAbsent` 全部阻塞在锁上排队，无超时、无取消。T3：create 最终失败 → 异常经 `ERR_PULSAR_PRODUCER_CREATE_FAILED` 包装抛出，排队的下一个线程**重新发起完整的建连尝试**——故障期内同 topic 发送按「30s 级阻塞 × 线程数」串行放大，而非快速失败。
- **现状**: 过载/故障时发送面行为 = 无界排队（线程堆积）而非声明语义的快速失败；对照 Kafka 侧发送面（共享 producer 预建，`send` 失败经 callback 在 max.block.ms/sendTimeout 内返回失败 future）有明确上界。`computeIfAbsent` 内执行阻塞 I/O 是平台并发语境下的已知反模式。
- **风险**: broker 故障期间发送方线程池被逐个挂住（每个最长一个建连超时 × 队列深度），上游 RPC/请求线程随之耗尽——故障域从「消息不可用」扩大到「应用线程池耗尽」；消费线程为 reply 建 producer 时同样被挂，poll 预算被侵蚀。
- **建议**: mapping 函数只做「占位 + 异步/带超时创建」：用平台 `IPool`/`IAsyncCache` 承载 producer 生命周期，或先 `putIfAbsent` 一个创建中的 `CompletableFuture<Producer>`，失败时移除占位并让所有等待者共享同一次失败（快速失败且不放大）；最低限度在 `buildProducer` 上应用 Pulsar `createAsync` + 有界超时。
- **失效面枚举**（F3：本文件惰性创建/获取资源面逐一登记）: ① `getProducer`/`buildProducer`（本条）✗ 阻塞在锁内；② `doSubscribe` 的 `builder.subscribe()`（`:501`）——调用者线程同步建 consumer，但**无锁包裹**、失败有清理回滚（`:537-551`），形态正确 ✓；③ Kafka 对照面 `createConsumer`（`KafkaMessageService.java:407`）——同步创建但无共享锁串行化，失败 typed 拒绝 ✓；④ `NopStreamOffsetBackingStore` 的 `REGISTRY.computeIfAbsent`（`:85/:109/:230`）——mapping 仅 new 本地容器，无 I/O ✓。违规仅 ① 一处。
- **信心水平**: 确定
- **误报排除**: 非「computeIfAbsent 原语使用」的一般化报警——定位在 mapping 函数内含**无界阻塞 I/O + 故障期重试放大**的具体行为；非低频冷路径——首次发送/ack-reply topic 建连是常规路径，broker 故障是本维度声明的过载场景。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（:210-219 get→computeIfAbsent(buildProducer)、buildProducer :238 builder.create() 阻塞远端调用、失败不缓存→排队者重试完整建连（T3 放大）逐点回码确认；对照面 ②③④（doSubscribe 无锁有回滚/Kafka createConsumer 无共享锁/REGISTRY mapping 无 I/O）核验成立，违规仅 ① 一处确认。与 C1-004（同站点 destroy 窗口 TOCTOU 泄漏面）同站点异机制（过载串行化 vs 生命周期竞态），非重复）

### [C8-008] 过载可观测缺口：无积压/重投/终点落地指标，且 failed 计数漏记批量失败、sent 计数含被拒发送

- **文件**: `nop-message/nop-message-core/src/main/java/io/nop/message/core/MessageMetrics.java:34-47`；`nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaConsumeTask.java:560-606`（批 catch 无 incrementFailed）；`nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarConsumeTask.java:302-316`（同）；`nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java:253-255`（incrementSent 先于取消拒绝检查）
- **证据片段**:
  ```java
  // MessageMetrics：仅 sent/consumed/failed/send.duration 四个口径
  Counter failed = Counter.builder("nop.message." + backend + ".failed")
          .description("Message consume failures").register(registry);
  // KafkaConsumeTask.batchConsume catch：整批失败无任何 metrics 调用
  } catch (Exception e) {
      LOG.error("nop.err.message.pulsar.batch-consume-fail", e);  // (Kafka :560 同形)
      pulsarConsumer.negativeAcknowledge(messages);
  ```
- **严重程度**: P3
- **并发场景**: 过载/退化期间监控面与实际行为的偏差；不涉及线程交错。
- **触发时序**: T1：批量订阅持续失败（broker 退化）。T2：`failed` 计数不动（两条批量路径的 catch 均未调用 `incrementFailed`），`consumed` 却在每轮 `dispatched=true` 后照常增长——监控视图「消费正常」。T3：Pulsar 侧带 cancelToken 的被拒发送也计入 `sent`（`sendPulsarAsync` 的 `incrementSent` 在取消检查**之前**执行，`:254` vs `:255`）。T4：WI22 endpoint 的毒消息终结、DLQ 落地次数、延迟重投队列深度均无任何指标——本维度全部 P1/P2 过载场景在监控上不可见或失真。
- **现状**: C8 执行步骤 4 要求「积压深度、拒绝数有指标」；现况仅四个基础计数，且两处口径漂移。owner doc「指标（WI30）」一节声明的就是这四个口径——口径声明与行为的偏差（批失败漏计、被拒计入 sent）未声明。
- **风险**: 过载场景（本维度的发现全集）缺乏告警信号面：重投风暴、毒消息终结、DLQ 落地、reply 串行化退化都只能靠日志事后检索。
- **建议**: ① 批量 catch 补 `incrementFailed`（Kafka/Pulsar 两处）；② Pulsar `incrementSent` 移至取消检查之后；③ 增补 `redelivery`、`endpoint.terminated`、`dlq.published` 计数与（可选）延迟队列深度 gauge——命名对齐 `nop.message.{backend}.*` 既有口径，owner doc WI30 节同步扩表。
- **失效面枚举**（F3：metrics 调用点全清单）: ① Kafka 单条成功/失败计数（`:358/:399`）✓；② Kafka 批量——仅成功侧隐含（`dispatched`），失败侧漏计 ✗；③ Pulsar 单条（`:334/:370`）✓；④ Pulsar 批量失败漏计 ✗；⑤ Pulsar sent 前置漂移 ✗；⑥ Local——`MessageMetrics` 未接入 Local 后端（Local 无 setMetricRegistry，四口径全缺）——一并登记。四处漂移/缺失。
- **信心水平**: 确定
- **误报排除**: 非纯错误处理/监控风格问题——C8 步骤 4 将「过载可观测」列为维度内要求，且计数漂移会**掩盖本维度 P1/P2 的发生**（批量失败视图为绿）。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现（四口径上限（MessageMetrics.create :34-47）、Kafka 批 catch :560-606 无 incrementFailed、Pulsar 批 catch :302-316 无 incrementFailed、Pulsar incrementSent :254 先于取消检查 :255、Local 全无 metrics（LocalMessageService 无 metrics 字段）逐点回码确认；round 2 扩展（Pulsar 单条毒消息 endpoint :366-368 return 先于 :370 incrementFailed——最高信号价值事件漏计）核验成立，随本条修复一并处理）

## 维度集外线索（overlay C2/C4/C7/C9）

### [nop-message-CL-019]（C1）DebeziumMessageSource 订阅/取消竞态：最后一个 cancel 的 stop() 会 clear 并发新订阅；engine 启动失败残留已 put 的订阅

`DebeziumMessageSource.java:72-80, 137-153, 168-180, 219-229`。时序：T1 线程 A cancel 最后一个订阅 → `subscriptions.remove` 后 `isEmpty()==true` → 调 `stop()`（synchronized）→ `subscriptions.clear()`；T2 线程 B 恰在 A 的 remove 与 isEmpty 检查之间 `subscribe()` put 了新订阅 → 被 A 的 clear 抹掉 → B 拿到看似有效的 `ICancellable` 但 `dispatchEvent` 永远不会投递给它（随后 B 的 `startEngineIfNeeded` 重启了 engine，却无人订阅）。次要面：`subscribe()` 先 `subscriptions.put(action, subscription)` 再 `startEngineIfNeeded()`，engine 启动失败抛 NopException 时已 put 的订阅残留在 map 中（下次启动后会被投递给一个调用方未成功持有的订阅者）。`subscribe` 未同步、`stop/cancel` 的 check-then-act 非原子。建议：subscribe/cancel 统一在 `this` 锁内做「增删 + 空判定 + 启停」复合操作，或改用引用计数。

### [nop-message-CL-020]（C1）LocalMessageService.Subscription.cancel 对 consumers.get(topic) 无 null 防护

`LocalMessageService.java:74-81`。`cancel()` 直接 `consumers.get(topic).remove(this)`：`clearConsumers()`（`:54-56`，public 测试/管理入口）清空 map 后调用任意存量订阅的 `cancel()` 即 NPE；与并发 `computeIfAbsent` 移除（无此路径，但 clearConsumers 可从任意线程调用）交叠时同样成立。建议 `List<Subscription> l = consumers.get(topic); if (l != null) l.remove(this);`（对齐 broker 侧 cancel 的幂等容忍形态）。

### [nop-message-CL-021]（C5，移交）maxConsumeAttempts=0（无限重投）时 failureAttempts 每失败位置永久累积

`KafkaConsumeTask.java:94`（key = `topic-partition@offset`，高基数）、`PulsarConsumeTask.java:68`（key = messageId）。consume-thread confined 无并发问题（C1 免立案）；但 `cap=0` 时 `failureAttempts.remove` 仅在成功/endpoint 路径执行，endpoint 永不触发 → 每个曾失败过的 location 常驻一个 HashMap entry，长时运行下随失败位置数线性增长。内存面归 C5 立案口径，本条仅圈定位置。

### [nop-message-CL-022]（C4）设计 3.6.1 F-39 裁定与 kafka-clients 实际行为不符：max.block.ms 耗尽为同步抛 BufferExhaustedException 而非「走 callback」

`KafkaMessageService.java:290-298` 注释与 `backend-semantics-design.md` 3.6.1 均断言「max.block.ms exhaustion 走 callback（ApiException 族路由）而非同步抛」。kafka-clients（含 4.x）`KafkaProducer.send` 在 accumulator 满时经 `RecordAccumulator.append(maxTimeToBlockMs)` 同步抛 `BufferExhaustedException`（TimeoutException 子类）。两路径在本模块均收敛为失败 future（`:290-307` 的 catch 覆盖），用户可见行为仅差「调用线程是否先阻塞 max.block.ms(60s)」——但该差异恰好是 C8-006 预算侵蚀分析的输入。建议按 `docs-for-ai` docs-bug 流程复核 F-39 裁定的事实基础（用 kafka-clients 4.3.1 写一个 buffer 满探针测试即可定案）。

### [nop-message-CL-023]（C3）Pulsar 批量 catch 的整批 negativeAcknowledge 与已提交 ack future 的在途竞争——重复应答放大

`PulsarConsumeTask.java:236-316`。T1：批内第 i 条 reply `sendAsync`/ack future 已 add 入 `futures` 但尚未 `waitAll`（waitAll 在循环后 `:284`）；T2：第 i+1 条 `getResult` 抛出 → catch `negativeAcknowledge(messages)` 整批 nack。已在途的 ack 与 nack 到达 broker 的顺序不定：nack 先到 → 已应答过的前缀（reply 已发出）整批重投 → 每条重投再发一次 reply。整批 nack 本身是设计 3.10 显式裁定（「Pulsar 整批 nack」），本条仅登记**未声明的残留**：已应答前缀的重复应答放大（放大系数 = 失败点前的批前缀长度），与 Kafka 批量的 progress 精确 rewind（只回退未 finalize 部分）不对称。at-least-once 内合法，建议在设计 3.10 的既有差异段补一句声明即可。

### [nop-message-CL-024]（C3，移交消费线程上下文专题）Local scheduleRedelivery 跨线程未传递 IContext

`LocalMessageService.java:250-270`。`ConsumeLater` 红elivery 从发送者线程 hop 到 `nop-local-message-redelivery` 线程执行 handler，平台 IContext（ThreadLocal 传播）未显式携带 → 到期重投的 handler 读到的调用上下文为空/错误租户。注意 broker 两后端的消费线程同样无上下文（这是消费模型的整体既定形态），本条只标注 Local 特有的**发送者→scheduler 异步边界**丢上下文；是否需要传递应归入 C3 消费线程上下文专题统一裁定，避免单点修造成后端间语义分叉。

## 维度复核结论（WI 4.1 独立复核，2026-10-20）

复核人（独立子代理，未参与初审/深挖，与 C1/C3 复核人互异）对 C8 全部 9 条（第 1 轮 8 条 + 第 2 轮 1 条）逐条回码验证：`KafkaConsumeTask.java` 全文、`PulsarConsumeTask.java` 全文、`LocalMessageService.java` 全文、`PulsarMessageService.java:170-552`、`KafkaMessageService.java:120-519`、`DebeziumMessageSource.java` 全文、`KafkaProducerConfig/KafkaConsumerConfig` 全文逐点比对；跨源核验 `FutureHelper.java`（nop-api-core :158-175/:262/:283-287）、`DebeziumCdcSourceFunction.java:240-300`、设计文档 `backend-semantics-design.md` §3.2/§3.3/§3.3.1/§3.9/§3.10；严重度对照 `ai-dev/skills/concurrency-resource-audit-prompt.md` 校准表，并对照 `ai-dev/audits/cra-index.md`（WI 1.1–3.4 台账）与 WI 1.2/WI 2.5/WI 3.1 历史审计做同案查重。

### 逐条判定表

| 条目 | 初判 | 判定 | 最终严重度 | 现状标注 | 关键复核依据 |
|---|---|---|---|---|---|
| C8-001 | P1 | **降级（同案并入 C3-001 主登记）** | P2 | 新发现 | 5 等待点无 timeout 回码；与本单元 C3-001 同缺陷同站点（C3-001 已独立复核保留 P2、失效面为超集）——单一缺陷不跨卷双严重度，对齐先案 P2，本条 F5/isActive 增量并入同 diff；P1↔P2 分歧留 7.1 |
| C8-002 | P1 | 保留 | P1 | 新发现 | catch 吞+零 onConsumeFailure（grep 复现）+设计 §3.3「仅第一层回调」契约核验（出处笔误 §3.2→§3.3 不影响实质）；§3.9 无豁免声明；P1 维持 |
| C8-003 | P2 | 保留 | P2 | 新发现 | 单线程+内联 handler+无界队列回码；与 C5-003/C6-001 同体三面边界干净 |
| C8-004 | P1 | 保留 | P1 | 新发现 | 吞异常→offset 推进回码；子项复核收窄（nop-stream 主路径被 R5-CON-02 capture 自愈，受损面=直接嵌入方，与 C3-002 同口径）；同屏障两极性同 diff 要求核对成立，判级与 C3-002 配对，分歧同留 7.1 |
| C8-005 | P2 | 保留 | P2 | 新发现 | 无预算闭环+零延迟热循环+HOL 冻结回码（seek 触发重新拉取的表述精度注记不影响实质）；失效面 ⑤ 对照标注修正（Local 自旋面归 C5-003）；round2 Pulsar 扩展吸收核验成立 |
| C8-006 | P2 | 保留 | P2 | 新发现 | 批量逐条同步 reply vs Pulsar 管道化对照回码；与 C8-009 同改点 |
| C8-007 | P2 | 保留 | P2 | 新发现 | computeIfAbsent 锁内阻塞建连+失败重试放大回码；与 C1-004 同站点异机制非重复 |
| C8-008 | P3 | 保留 | P3 | 新发现 | 四处漂移/缺失逐点回码；round2 毒消息 endpoint 漏计扩展核验成立 |
| C8-009 | P2 | 保留 | P2 | 新发现 | max.block.ms 同步阻塞独立于 CL-022 分歧成立（阻塞先于抛出/回调发生）；消费线程调用点 ④ 处逐一核验；与 C8-006 根因不同非重复 |

**判定统计**：保留 8（其中 1 条标注并入 C3-001 主登记）、降级 1（C8-001 P1→P2）、驳回 0。
**最终严重度分布**：P1×2（C8-002/C8-004）/ P2×6 / P3×1（初审 P1×3/P2×5/P3×1，唯一变动为 C8-001 降级并入）。
**现状标注分布**：新发现×9（本单元无前序 CRA 覆盖；WI 3.1 MA-11 为同契约异实现家族关联非前案；WI 1.2 R5-CON-02/03 为已交付机制，C8-004 为其投递极性新失效面；CL-022 为 docs-bug 线索输入非前案）。

### 跨单元对照（cra-index P1 台账逐条比对）

1. **同单元 C8-001↔C3-001 同案命中（本次复核新登记，移交合并裁定）**：两卷对同一缺陷（onMessage 返回 CompletionStage 无上界等待，5 站点：Kafka:356/:516、Pulsar:333/:248、Local:216）各自立案——C3-001 P2（已复核保留）、C8-001 P1。**表态**：同根同站点同修复，按「防止同一缺陷多维度重复计数」规则归并——C3-001 为主登记（先裁+失效面超集），C8-001 降级 P2 并入，其 F5 活性盲区/成员僵尸化增量并入同 diff；严重度 P1↔P2 分歧显式留 7.1（若升档，两案同批）。修复契约与 WI 3.1 MA-11（DBMessageService 同 SPI 同病）对齐的移交结论（C3 卷对照 2）随之适用于合并后的单一工单。
2. **C3-002↔C8-004 同屏障两极性**：核对成立——同一 dispatchEvent 屏障的存活极性（C3-002：无屏障炸流）与投递极性（C8-004：屏障吞异常静默丢）判级相反且均保留，修复须同 diff（per-event 屏障+毒事件 failure endpoint，两极同禁）；两案 P1 均附「nop-stream 主路径被 R5-CON-02 capture 自愈、受损面=直接嵌入方」的同口径注记，7.1 应配对裁定。
3. **WI 1.2 Debezium 同族**：C4-004/MA-06（globalWorker 占用）=本卷 CL-010 面已按 overlay 不重复立案，正确；C3-014/MA-07（停止契约）与 C6 卷 C6-005 双轨互补（handler 挂死使 close 无法静默的扩展面已并入 C6-005，见 C8-round2 扩展节核验）。
4. **其余各条**（C8-002/003/005/006/007/008/009）在 WI 1.1–3.4 台账无同站点/同案命中；WI 1.6 C8-001（扫描器停摆）/WI 2.1 C3-001（周期任务绝缘层）为同名异站点，无关联。

## 最终保留项

- **保留工单 8 条 + 同案并入 1 条**：P1×2（C8-002、C8-004）进入 Phase 8 批量修复面；P2×6/P3×1 按 P2 裁决台账流程处置；C8-001 随 C3-001 主登记（P2）处置。
- **同 diff 修复批建议**：① 消费等待上界族——C3-001（吸收 C8-001 的 F5 增量）+ WI 3.1 MA-11 契约对齐；② Debezium 屏障族——C8-004 + C3-002（+ C3-009 Debezium 面）：毒事件 endpoint 统一设计；③ Kafka reply 预算族——C8-006 + C8-009（管道化+期限化+三参数联动声明）；④ Local 总线族——C8-002（补第一层回调）+ C8-003/C5-003/C6-001（executor 治理三面）；⑤ C8-005（三后端 ConsumeLater 最小延迟下限+defer 计数，Pulsar/Local 面随批）、C8-007（占位+异步创建）、C8-008（四处指标补齐）各自独立小 diff。
