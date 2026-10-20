# C3 异步链路正确性 — nop-message（WI 4.1）

## 审计范围与口径

### 单批次 4 源码根覆盖声明

本批次单批次覆盖 nop-message 全部 4 个 main 源码根，逐文件审读（test 源码仅作行为佐证，不作为发现对象；`_` 前缀生成物无）：

- `nop-message/nop-message-core/src/main/java`（7 文件：LocalMessageService、MessageConsumeFailureBridge、MessageMetrics、ChannelBacklogProbe、ReflectionMessageSubscriptionRegistrar、MessageCoreConstants/Errors —— 逐行读）
- `nop-message/nop-message-debezium/src/main/java`（9 文件：DebeziumEngineWrapper、DebeziumMessageSource、DebeziumEventConverter、NopStreamOffsetBackingStore、DebeziumEngineConfig、ChangeEvent/ChangeEventMetadata/DebeziumConfig/DebeziumErrors —— 核心四类逐行读，POJO/常量类扫读）
- `nop-message/nop-message-kafka/src/main/java`（7 文件：KafkaMessageService、KafkaConsumeTask 逐行读；KafkaHelper、KafkaClientConfig/ProducerConfig/ConsumerConfig、KafkaErrors 扫读）
- `nop-message/nop-message-pulsar/src/main/java`（8 文件：PulsarMessageService、PulsarConsumeTask 逐行读；PulsarHelper 逐行读；PulsarClientConfig/ConsumerConfig/ProducerConfig、PulsarErrors 扫读）

平台依赖面按"只审使用方式"口径触及：`FutureHelper`（getResult/syncGet/futureCall/futureRun）、`IMessageSender` 默认方法、`ContextProvider.syncGet`（kernel 内部不再下钻）。

### CL-022 落点确认

WI 2.3 CL-022 显式移交落点确认存在：`KafkaMessageService.java:313` 与 `PulsarMessageService.java:273` 两处 `orTimeout` 站点。取消传播 / 超时后底层任务行为 / 重试交互的三点分析结论见 **C3-007**（处置：fire-and-forget 语义已在两处 javadoc 声明，ghost 任务时长由 client 层兜底参数界定；残留问题降档为 P3）。

### 口径纪律声明（数量人工复现）

- **orTimeout 口径复现**：`grep -rn "orTimeout" --include=*.java`（4 根，main 代码）原始行 **3 行**（含 `KafkaMessageService.java:310` javadoc 注释 1 行）；**实际代码调用点 2 处**（`KafkaMessageService.java:313`、`PulsarMessageService.java:273`）。基线 C3a 节括注「code 11 vs grep 13」与本批人工复现不一致，疑为口径示例或更宽范围统计；本批数量结论以本次复现为准。
- **异步入口清单**（grep CompletionStage/CompletableFuture + 线程创建点，main 代码 7 文件全部入审）：KafkaMessageService/KafkaConsumeTask、PulsarMessageService/PulsarConsumeTask、LocalMessageService、MessageConsumeFailureBridge、NopStreamOffsetBackingStore；线程创建点 5 处（Kafka/Pulsar 每订阅单线程 executor、Local 红投调度器、Debezium 全局 worker、Local 显式 new Thread）。
- **降档口径**：本维度全部发现均为长驻运行期面（消费循环 / 发送路径 / 引擎回调线程 / 红投定时器），无启动期专属站点，启动期降档不适用。
- **盲审纪律**：未读 `ai-dev/audits/**`（本文件除外）、`ai-dev/plans/**`、`ai-dev/bugs/**`、`ai-dev/lessons/**`、`ai-dev/backlog/**`，未查 git log。`ai-dev/design/nop-stream/connector-design.md §5.4`、`backend-semantics-design.md 3.3.2/3.7/3.8.1` 以代码 javadoc 引用形式获知其存在，未打开阅读；凡 javadoc 声明引用设计文档的边界，按"已声明边界"处理并标注出处行号。

---

## 发现

### [C3-001] onMessage 返回的 CompletionStage 在消费/发送线程上被无限期同步等待——全模块唯一无上界等待点

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaConsumeTask.java:355-356`（同族 `:516`）；`nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarConsumeTask.java:332-333`（同族 `:248`）；`nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:216-217`
- **证据片段**:
  ```java
  // KafkaConsumeTask.pollAndConsume（Pulsar 两处同型）
  Object response = consumerFn.onMessage(record.topic(), apiMessage, context);
  response = FutureHelper.getResult(response);   // 无超时上界
  // LocalMessageService.invokeSubscription —— 阻塞的是发送方线程
  Object ret = FutureHelper.getResult(
          subscription.consumer.onMessage(subscription.topic, message, context));
  ```
- **严重程度**: P2
- **并发场景**: 单一消费线程（Kafka/Pulsar 每订阅单线程 executor）或本地总线发送方线程 vs 用户 onMessage 返回的任意 CompletionStage。该模块对 `stop()`/`wakeup()`/receive 超时的有界停止设计（PulsarConsumeTask DEFAULT_RECEIVE_TIMEOUT_MS=1000 的 MSG-F-54 机制、Kafka pollTimeoutMs+wakeup 机制）全部只覆盖 receive/poll 等待点，不覆盖 getResult。
- **触发时序**: T1 消费线程执行 `onMessage` L355，用户代码返回一个永不完成的 CompletionStage（如自建 future 忘记 complete、或返回绑定了已死依赖的 sendAsync）→ T1 阻塞在 L356 `getResult` 无限等待；T2 停机线程执行 `stop()`（wakeup 只作用于 `poll()`，对 getResult 无效）→ `cancel()` 的 5s+1s 预算耗尽，WARN `consume-thread-stuck` 后 `consumer.close()` 与仍停留在 getResult 的线程形成关闭竞态（未来某刻 future 完成时线程恢复，对已关闭 client 的后续 ack/commit 调用抛异常进入已声明的降级路径）。Local 总线更差：`Subscription.cancel()`（LocalMessageService.java:74-81）只设 volatile 标志，**无任何线程可中断**，发送方线程永久挂死且无恢复手段。
- **现状**: 异步响应等待点没有超时/取消兜底；有界停止声明（PulsarConsumeTask.java:152-158 javadoc「observes active within one timeout round」）对该等待点不成立。
- **风险**: 单个订阅者 bug 即永久占用消费线程（Kafka 侧伴随 max.poll.interval.ms 踢组 → 他实例接管 → 本线程仍在处理 → 重复副作用）；停机时产生误导性 stuck WARN 与关闭竞态窗口。Local 总线场景发送方线程（可能是 HTTP worker）被单个订阅者拖死。
- **建议**: 为 getResult 等待点增加可配置上界（复用 `MessageSubscribeOptions` 现有超时字段族或新增 consumeAwaitTimeout），超时按失败路径处理（Kafka seek 重投 / Pulsar nack，at-least-once 不破坏）；Local 总线对订阅者响应等待加硬上界。
- **失效面枚举**: ① KafkaConsumeTask.pollAndConsume:356 —— 未修复；② KafkaConsumeTask.batchConsume:516 —— 未修复；③ PulsarConsumeTask.consume:333 —— 未修复；④ PulsarConsumeTask.batchConsume:248 —— 未修复；⑤ LocalMessageService.invokeSubscription:216 —— 未修复（最重，无中断源）；⑥ KafkaConsumeTask.publishToDeadLetterIfConfigured:694 `syncGet(sendAsync)` —— 消费线程上但受 producer delivery.timeout（默认 120s）间接兜底，降档观察。
- **信心水平**: 确定（等待点无上界是代码事实；触发依赖用户代码行为）
- **误报排除**: 非"原语使用即问题"——FutureHelper 本身正确；问题在于本模块把用户返回的 future 当作必然完成的输入，而同文件对其他等待点均做了有界化（MSG-F-54），构成同一模块内标准不一致，非低频启动路径。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（5 等待点+DLQ 面⑥逐点回码确认——Kafka:355-356/:516、Pulsar:332-333/:248、Local:216-217、Kafka:694 syncGet；有界停止设计（MSG-F-54/wakeup/pollTimeout）不覆盖 getResult 的论证成立；Local cancel 仅 volatile 标志无中断源为最重面）

### [C3-002] Debezium 事件回调线程无异常屏障：单个事件转换异常终止整个 CDC 流，且 DebeziumMessageSource 未接故障可见性机制

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:185-190`；`nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEventConverter.java:33-70,112-115`；`nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:137-153`
- **证据片段**:
  ```java
  // DebeziumEngineWrapper —— notifying 回调，无 try/catch
  private void handleDebeziumEvent(io.debezium.engine.ChangeEvent<byte[], byte[]> event) {
      ChangeEvent changeEvent = DebeziumEventConverter.convert(event);  // 可抛
      if (changeEvent != null) {
          changeEventConsumer.accept(changeEvent);  // dispatchEvent 有屏障，此处直接调用
      }
  }
  // DebeziumEventConverter.convert —— 可抛点：parseNonStrict 非对象 JSON → L41 CCE；
  // event.key()==null → parseKey L113 new String(null) NPE；数值宽度 CCE 隐患（L84 注释自证）
  ```
- **严重程度**: P1
- **并发场景**: Debezium 引擎 worker 线程（`GlobalExecutors.globalWorker().execute`，DebeziumEngineWrapper.java:159）串行处理事件流；异常从 notifying 回调逃逸 → `engine.run()` 抛出 → worker catch（L164-168）→ `notifyEngineFailure` → 引擎终态死亡。
- **触发时序**: T1 引擎线程执行 `handleDebeziumEvent` L186，某事件 value 为非法/非对象 JSON（或 key 为 null、数值宽度落 Integer）→ `convert` 抛出 → T1 同线程异常上抛出 `engine.run()` → worker finally `running=false`；T2 任意线程查询 `DebeziumMessageSource.isStarted()` 返回 **true**（started 标志只反映 start 调用，不感知引擎死亡）→ CDC 流永久停止、offset 停止推进、`DebeziumMessageSource` 自身既未注册 `registerFailureListener`（全仓生产接线仅 nop-stream 侧 DebeziumCdcSourceFunction:248/298），也无重启路径 → 对直接使用 DebeziumMessageSource 的部署，死亡仅存在于 ERROR 日志。
- **现状**: 同文件内异常处理不对称——`dispatchEvent`（DebeziumMessageSource.java:155-163）对订阅者异常有 per-consumer try/catch 屏障，`handleDebeziumEvent` 对转换异常无任何屏障；R5-CON-03 建立的终态可见性机制（FAILURE_LISTENERS）未在本模块对外入口 DebeziumMessageSource 上接线。
- **风险**: 长时运行中一次坏事件（畸形 payload、连接器升级导致格式漂移）使 CDC 静默断流；下游状态与 source 声明状态不一致（isStarted=true vs 引擎已死），触发 F5「静默死亡对健康检测不可见」的变体。
- **建议**: `handleDebeziumEvent` 内为 convert 与 consumer 调用加 per-event try/catch（坏事件跳过 + WARN 计数，毒事件走与消费后端同型的 failure endpoint）；DebeziumMessageSource 启动时注册 failure listener 并将终态死亡反映到 started 标志或健康探针。
- **失效面枚举**: ① DebeziumEngineWrapper.handleDebeziumEvent → convert 路径 —— 无屏障（本条主体）；② DebeziumMessageSource.dispatchEvent:155-163 —— 已有 per-consumer 屏障（同族正确形态，佐证不对称）；③ DebeziumCdcSourceFunction（nop-stream，集外）—— 已注册 listener（248/298 成对），但引擎死亡对它是一次性通知，无重启；④ 两个生产 notify 点（wrapper L168/L234）—— 已接线，非缺陷。
- **信心水平**: 确定（逃逸路径与状态不一致是代码事实）；触发概率标注为低-中（依赖畸形事件出现）
- **误报排除**: 不是"引擎错误处理已存在"的误报——worker catch 只保证线程不崩，不保证流存活与 owner 可见；也不属于 error-handling 维度——本条报的是异步链状态不一致（started vs 实际死亡），非错误码规范。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜现状:新发现（handleDebeziumEvent:185-190 无屏障、convert 三抛点（:41 CCE/:113 NPE/:59 ts_ms 裸转换，扩展面一并回码确认）、started 撒谎（isStarted 不感知引擎死亡）与 DebeziumMessageSource 零 listener 接线全部实测成立。严重度注记：nop-stream 主路径（DebeziumCdcSourceFunction:248 已注册）引擎死亡可见、后果为任务失败重启，P1 最高危面=直接使用 DebeziumMessageSource 的部署（仓内 main 代码仅 nop-stream-fraud-example 示例）；另与同单元 C8 卷「dispatchEvent 吞异常推进 offset」构成同一屏障的存活/投递两极性判定，修复须以毒事件 endpoint 调和——留合并裁定）

### [C3-003] DebeziumMessageSource.subscribe 与 stop 交叉：锁外 map 写 + 锁内 started 检查 → stop 后僵尸引擎复活 / 订阅静默丢失

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:72-80,137-153,168-180`
- **证据片段**:
  ```java
  public ICancellable subscribe(Consumer<ChangeEvent> action) {   // 非 synchronized
      Subscription subscription = new Subscription(action);
      subscriptions.put(action, subscription);                    // L74 锁外写
      startEngineIfNeeded();                                      // L77 锁内判 started
      ...
  private synchronized void startEngineIfNeeded() {
      if (started) { return; }                                    // L138
      engineWrapper = new DebeziumEngineWrapper(...); ... started = true;
  public synchronized void stop() {
      ... engineWrapper = null; started = false; subscriptions.clear();  // L176-179
  ```
- **严重程度**: P2
- **并发场景**: 订阅线程 vs 停机线程（如应用关闭钩子并发于业务线程订阅）。
- **触发时序**: 交错 B：T1 stop 线程执行 L168-179（clear + started=false + wrapper=null）→ T1 stop 返回，调用方认为源已停止 → T2 订阅线程 L74 put（此刻锁空闲，可发生于 stop 任意阶段前后）→ T2 L77 进锁见 started==false → 新建 wrapper 并 start → **stop() 语义违约：已 stop 的 source 又出现运行中的引擎**（binlog 连接被重新建立），需再次 stop 才能释放。交错 A：T2 put → T1 clear（订阅被清）→ T2 startEngine → 引擎运行但订阅表为空，该订阅者永远收不到事件（返回的 ICancellable 静默失效）。
- **现状**: 订阅注册的写操作在生命周期锁外执行，started 检查与注册不在同一临界区，"先订阅后启动"的时序假设无同步保证。
- **风险**: 停机路径上引擎复活导致 binlog 连接/全局 worker 任务泄漏；或订阅者静默收不到数据（F2「半个成功」形态：订阅 API 返回成功但实际未生效）。
- **建议**: 将 `subscriptions.put` 纳入 `startEngineIfNeeded` 同一把锁（或 subscribe 全程 synchronized），并在 stop 后对 subscribe 抛 typed 拒绝（ERR_DEBEZIUM_ENGINE_START_FAILED 族）而非复活引擎。
- **失效面枚举**: ① DebeziumMessageSource.subscribe/stop —— 未修复（本条主体）；② LocalMessageService.subscribe/clearConsumers —— 同型面（public clearConsumers 与 subscribe 并发，computeIfAbsent+CopyOnWriteArrayList 使结果退化为订阅丢失而非引擎复活，且 clearConsumers 为测试辅助面，标注观察）；③ Kafka/Pulsar subscribe vs doStop —— 两者经 LifeCycleSupport 状态机且 subscription 先入队后启动，doStop 遍历清理，无此交错（对照排除）。
- **信心水平**: 确定
- **误报排除**: 非低频路径豁免——teardown 与订阅并发是真实停机窗口；且后果是生命周期违约而非良性竞态。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（交错 A/B 回码确认；本条为 DebeziumMessageSource subscribe/stop 竞态站点的主登记（吸收 C1-006 的交错 A），同站点另见 C6 卷 put-then-start 面——合并裁定三面归一）

### [C3-004] Pulsar sendAsync 缺少同步异常包装——Kafka F-39 修复未清扫到 Pulsar 兄弟（F3）

- **文件**: `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java:252-293`（对照 `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaMessageService.java:273-307`）
- **证据片段**:
  ```java
  // KafkaMessageService —— F-39 已修：同步异常路由进 future
  try { prod.send(record, callback); }
  catch (Exception e) {
      // F-39: synchronous send failures ... must not escape sendAsync —
      // the contract is a CompletionStage.   → future.completeExceptionally(...)
  }
  // PulsarMessageService.sendPulsarAsync —— 无 try/catch：
  Producer<?> producer = getProducer(topic);        // 可同步抛 NopException(ERR_PULSAR_SERVICE_NOT_ACTIVE)
  TypedMessageBuilder<?> builder = producer.newMessage();  // producer 已关闭 → ProducerClosedException 同步抛
  PulsarHelper.buildPulsarMessage(builder, message, options); // 编码失败同步抛
  CompletableFuture<Void> future = builder.sendAsync().thenAccept(ret -> {});
  ```
- **严重程度**: P2
- **并发场景**: 调用 sendAsync 的业务线程 vs doStop 线程（destroy 使 producers.clear() + client.close()）。
- **触发时序**: T1 业务线程进入 `sendAsync` → `sendPulsarAsync` L263 `getProducer(topic)` 命中 doStop 前已建 producer（map 尚未 clear）→ T2 doStop 执行 `producer.close()` + `producers.clear()`（L180-187）→ T1 继续 L264 `producer.newMessage()`，Pulsar 客户端对已关闭 producer 同步抛 ProducerClosedException → 异步契约违约：异常以 **throw** 形式逸出 `sendAsync`，而 Kafka 同场景路由为 completeExceptionally 的 rejected future。`getProducer`→`buildProducer` 的 `client==null` NopException（L227-230）同样同步逸出。
- **现状**: 同一接口契约（`CompletionStage<Void> sendAsync`）在两个后端对"同步失败"的呈现不一致；Kafka 侧注释明言契约是 CompletionStage，Pulsar 侧无等价防护。
- **风险**: 以 rejected-future 语义编写的调用方（如 MessageRpcClient.callAsync 先例、`FutureHelper` 链）在 Pulsar 后端遇到裸异常，错误处理分支未覆盖 → 线程上未捕获异常或误判为框架 bug；destroy 并发窗口内失败形态不确定。
- **建议**: 为 `sendPulsarAsync` 的 getProducer/newMessage/buildPulsarMessage/sendAsync 段加 try/catch，将非 NopException 包装为 `ERR_PULSAR_SEND_FAILED` 后 `completeExceptionally`（逐字对照 Kafka F-39 形态，含 InterruptedException 恢复位的对应处理）。
- **失效面枚举**: ① PulsarMessageService.sendPulsarAsync —— 未修复（本条主体）；② KafkaMessageService.sendAsync:273-307 —— 已修复（F-39 先例）；③ Kafka getProducer:235-241 —— 同步抛仍在（与 Pulsar ② 同型，Kafka 的 F-39 只包住 send 段；两后端的"未初始化"同步抛为既有一致行为，标注观察不入修）；④ ConsumeContext.sendAsync（两后端，KafkaConsumeTask:771 / PulsarConsumeTask:501）—— 纯转发，继承各自服务缺陷面。
- **信心水平**: 确定（ProducerClosedException 同步抛为 Pulsar 客户端既定行为；具体异常类型随版本可能为异步失败，届时降档）
- **误报排除**: 非错误处理风格问题——这是同一 API 在两后端对调用线程的可见行为分叉（throw vs future rejection），属异步组合契约违约。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（sendPulsarAsync:252-277 全程无 try/catch vs Kafka F-39 :290-307 对照实测成立；版本无关的确定性同步逸出面=client==null 的 NopException（buildProducer:227-230，destroy 完成后 getProducer→computeIfAbsent 必达），ProducerClosedException 时序面随客户端版本（finding 已自qualify）——维持 P2）

### [C3-005] Pulsar 批量消费链 ack/reply 排序与单条路径、Kafka 后端不一致；ConsumeLater 批内语义两后端分叉

- **文件**: `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarConsumeTask.java:271-284`（对照同文件单条路径 `:348-355`、Kafka 批量 `KafkaConsumeTask.java:536-546`）
- **证据片段**:
  ```java
  // PulsarConsumeTask.batchConsume —— reply 与 ack 并发推进，L284 才统一等待
  } else {
      futures.add(context.sendAsync(getAckTopic(message.getTopicName()), response)); // reply
      futures.add(ackAsync(context, message));                                       // ack
  }
  ...
  FutureHelper.syncGet(FutureHelper.waitAll(futures));   // L284
  // 对照：单条路径 L349-350 —— reply 先阻塞成功，再 ack（正确序）
  // 对照：Kafka 批量路径 L537 —— ctx.send 阻塞 reply 先行，commit 在边界段之后（正确序）
  ```
- **严重程度**: P2
- **并发场景**: 单消费线程内 reply future 与 ack future 的完成顺序竞态（两者由 Pulsar 客户端不同连接/回调完成）。
- **触发时序**: T1 消费线程为消息 i 提交 reply+ack 两 future → T1' ack 先于 reply 在 broker 侧完成（cursor 前移）→ T2 L284 waitAll 返回时 i 的 reply 失败（broker 抖动/ack topic 异常）→ 进 catch L302-306 对全批 `negativeAcknowledge(messages)` → 若该 nack 请求因连接中断丢失且未配置 ackTimeout，已 ack 的消息 i 不再重投 → **reply 永久丢失**；nack 生效时已成功 reply 的 [0..i-1] 消息被重复投递重复 reply（at-least-once 残留——Kafka 侧在 L474-475 有同型残留声明，Pulsar 侧无对应声明）。
- **现状**: 三处同族实现中 Pulsar 批量是唯一"ack 不等待 reply 达成"的形态；恢复语义依赖 catch 的全批 nack + nack 对已 ack 消息的生效性（后者依赖客户端版本行为，本审计未实测，标注为理论尾部）。
- **风险**: ack-reply RPC 场景下回复偶发丢失且无重投保证；恢复路径副作用重复面扩大；同一 `onMessageBatch` 契约跨后端可移植性受损。
- **建议**: 批量路径改为 per-message「先 syncGet(reply) 再 ack」对齐单条路径与 Kafka（吞吐换正确序），或至少将全批 nack 恢复残留补入 javadoc 声明。
- **失效面枚举**: ① PulsarConsumeTask.batchConsume reply/ack 并发 —— 未修复（本条主体）；② PulsarConsumeTask.consume:348-355 —— 序正确（对照）；③ KafkaConsumeTask.pollAndConsume:383-392 + batchConsume:536-546 —— reply 阻塞先行、commit 后置，序正确（对照）；④ **ConsumeLater 批内语义分叉**：Kafka batchConsume:529-535 视 ConsumeLater 为批端点（其后不再派发、整批不提交），Pulsar batchConsume:272-275 仅 reconsumeLater 单条、后续消息照常派发并 ack——同一 API 两后端行为不同，未在任何 javadoc 声明为后端差异，一并清扫。
- **信心水平**: 很可能（排序竞态与分叉是代码事实；「reply 永久丢失」尾部依赖 nack 失效这一未验证前提）
- **误报排除**: 非 at-least-once 一般性豁免——问题不是"会有重复"，而是同族三处的 ack/reply 先后序不一致 + 契约语义未声明的后端分叉。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（批量 reply/ack 并发推进 :277-278/:284 vs 单条 :349-355 与 Kafka :536-546 序对照实测成立；ConsumeLater 分叉（Kafka :529-535 批端点 vs Pulsar :272-275 单条 reconsumeLater 后照常派发+ack）实测成立；「reply 永久丢失」尾部依赖 nack-on-acked 失效的未验证前提已由 finding 自标注，round2 单路径 nack 同族扩展一并确认）

### [C3-006] LocalMessageService 红投定时器火发时 suspended 即永久丢弃延迟消息——挂起语义吞掉 ConsumeLater 红投

- **文件**: `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:250-270`
- **证据片段**:
  ```java
  redeliveryScheduler().schedule(() -> {
      if (subscription.cancelled || subscription.suspended)
          return;                                    // suspended 与 cancelled 同等丢弃
      List<Subscription> subscriptions = consumers.get(subscription.topic);
      if (subscriptions == null || !subscriptions.contains(subscription))
          return;
      invokeSubscription(subscription, message);
  }, delay, TimeUnit.MILLISECONDS);
  ```
- **严重程度**: P2
- **并发场景**: 红投调度器单线程（`nop-local-message-redelivery` daemon）vs 运维线程执行 suspend/resume。
- **触发时序**: T1 订阅者 onMessage 返回 `ConsumeLater(60s)` → L253 调度延迟红投 → T2 运维在窗口内 `subscription.suspend()`（维护/发布暂停）→ T3 定时器 T+60s 火发见 `suspended==true` → return，消息对象被丢弃且**不重新调度** → T4 resume 后无任何机制恢复该消息。javadoc（L245-248）只把 cancelled 列为契约性放弃（"Abandoning on cancellation is contract behavior"），suspended 的丢弃未声明。
- **现状**: suspend 被实现为"暂停 + 顺带吞掉在途延迟红投"，与 broker 后端对照（Kafka/Pulsar 中 suspend 只延迟消费、消息始终在 broker 侧保留）语义不一致。
- **风险**: 本地总线无持久化底座，被吞红投即静默丢失；ConsumeLater 的延迟语义（"稍后重试"）在 suspend 场景退化为"丢弃"。
- **建议**: 火发时 suspended 改为按剩余/固定延迟重新调度（或挂起期间累积、resume 时 flush），并在 javadoc 声明放弃矩阵。
- **失效面枚举**: ① scheduleRedelivery 火发 suspended 分支 —— 未修复（本条主体）；② scheduleRedelivery 火发 cancelled 分支 —— 已声明（javadoc L247-248），非缺陷；③ scheduleRedelivery RejectedExecutionException 分支 —— 已处理（F-40，L263-269），非缺陷；④ broker 后端 ConsumeLater × suspend 交互 —— 消息留存于 broker，无此丢失面（对照排除）。
- **信心水平**: 确定
- **误报排除**: 非已声明边界——javadoc 明确只豁免 cancelled；suspended 丢弃无声明且与三后端中其余两端的 suspend 语义相反。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（:254 suspended 与 cancelled 同等丢弃、不重调度实测确认；javadoc :246-248 契约句仅声明「Abandoning on cancellation is contract behavior」、suspended 丢弃未声明的判定成立——注：javadoc 事实句提到 suspended flags 会复查，但契约性放弃声明只覆盖 cancelled，finding 表述准确；与 broker 后端 suspend 语义反向对照成立）

### [C3-007] CL-022 移交落点处置：orTimeout 站点 ghost 任务有界、语义已声明；残留为无配置兜底上界与指标口径

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaMessageService.java:308-317,320-328`；`nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java:267-287`
- **证据片段**:
  ```java
  // KafkaMessageService.java:308-313 —— 语义声明与超时施加
  // WI15 (F-45): upper bound on the returned future. Timeout leaves the underlying
  // send running (fire-and-forget) — the message may still be delivered.
  long sendTimeoutMs = resolveSendTimeoutMs(options);
  if (sendTimeoutMs > 0) {
      future.orTimeout(sendTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
  }
  // resolveSendTimeoutMs 尾部：return 0L; // no configured bound
  ```
- **严重程度**: P3
- **并发场景**: 发送调用线程 vs KafkaProducer/Pulsar 客户端内部 IO 线程（ghost 发送任务）。
- **触发时序**: T1 调用线程 sendAsync 后阻塞等待 future → T2 orTimeout 在 sendTimeoutMs 后以 TimeoutException 完成_future，但底层 producer send 继续重试 → T3 底层发送最终成功 → 调用方按失败重试 → 重复投递。三点分析：取消传播——cancelToken 仅在派发前检查（两处 "pre-send cancellation check" 注释即声明了作用域），orTimeout 超时后底层任务既不被取消也不受 token 影响，ghost 时长由 client 层兜底（Kafka delivery.timeout.ms 默认 120s / Pulsar sendTimeout 默认 30s）；超时后行为——已声明 fire-and-forget；重试交互——超时≠失败、超时后成功即重复，由模块级 at-least-once 基线（D11）覆盖。
- **现状**: 语义链闭合且已声明，CL-022 的三个问题点均落回已声明边界或 client 兜底，不构成独立缺陷；残留两点：① `resolveSendTimeoutMs` 在 `defaultProducerConfig==null` 时返回 0（无上界），orTimeout 不施加，返回 future 的完成时限完全依赖 client 隐式默认——声明缺失；② `metrics.incrementSent` 计数点是"提交"而非"结果"（Pulsar L254 甚至在 cancel-check 之前、rejected future 也计数），Kafka L315 在 dispatch 后——sent 指标含超时/取消/失败的发送。
- **风险**: 无配置兜底上界使"无界 future"可能传导到上层（如被消费线程 syncGet 的 DLQ 发布，见 C3-001 失效面⑥）；指标口径偏差影响告警可信度。
- **建议**: javadoc 补"未配置时依赖 client delivery.timeout/sendTimeout 兜底"的显式声明；指标拆分 attempted/succeeded 或更名。
- **失效面枚举**: ① KafkaMessageService:313 —— 语义已声明，残留①②；② PulsarMessageService:273 —— 同上（额外具备 builder 层 sendTimeout 第二道兜底，L236）；③ grep 口径内的第三行（KafkaMessageService:310）为 javadoc，非站点。全仓 4 根内无其他 orTimeout 调用点。
- **信心水平**: 确定
- **误报排除**: 本条按 CL-022 移交要求作出"不构成 P1/P2 缺陷"的显式裁定而非回避——依据是校准规则 6（已声明边界）与模块级 at-least-once 基线，非泛化豁免。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现——WI 2.3 CL-022 移交落点闭环确认（独立 grep 复现：主代码 orTimeout 调用点恰 2 处 kafka:313/pulsar:273 + javadoc 1 行 :310，与「口径纪律声明」一致）；resolveSendTimeoutMs:327 零配置上界与 Pulsar :254 计数点位实测确认；三点裁定（取消传播/orTimeout 后行为/重试交互）落回已声明边界与 client 兜底成立，P3 残留处置正确

### [C3-008] Kafka 成功路径 commitSync 失败仅 WARN 吞掉：方向安全但无升级路径，且 dispatched=true 抑制退避

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaConsumeTask.java:421-426`
- **证据片段**:
  ```java
  try {
      consumer.commitSync();
  } catch (Exception e) {
      LOG.error("nop.err.message.kafka.commit-fail", e);   // 吞掉，无计数/无退避
  }
  return dispatched;   // dispatched=true → runTask 重置 consecutiveErrors
  ```
- **严重程度**: P3
- **并发场景**: 单消费线程顺序路径；影响对象是 broker 端 commit 通道（如 group 状态异常、协调器不可达的持续性故障）。
- **触发时序**: T1 每轮 pollAndConsume 成功派发后 L422 commitSync 持续抛（协调器故障）→ T2 每轮仅 ERROR 日志，`dispatched=true` 使 runTask L298-299 重置退避计数 → 长时运行中 offset 提交持续失败，无指标、无 backoff、无 failure callback 通知（dispatchConsumeFailure 不被触发），仅刷日志。
- **现状**: 吞异常方向安全（不提交 → 重启/rebalance 后重投，at-least-once 保持，对照 F9 判定状态一致），但与 WI22 建立的"错误轮次升级退避 + onConsumeFailure 可见"框架不对齐——同一消费循环内另两类错误（poll/commit 失败上抛路径）都有退避与回调，此路径两者皆无。
- **风险**: 长时运行中提交通道静默退化：重投窗口无界扩大（依赖 rebalance/重启才暴露），排障仅剩日志 grep。
- **建议**: commit 失败计入 consecutiveErrors（return false 或上抛交由 runTask 既有 backoff/failure dispatch），并增失败计数指标（复用 MessageMetrics.failed 语义或新增）。
- **失效面枚举**: ① pollAndConsume 尾部 commitSync:421-425 —— 未修复（本条主体）；② batchConsume 的 commitConverged:546（成功路径，异常上抛走 rewind）—— 已有上抛语义，非缺陷；③ batchConsume 失败端点补提交:584-588 —— 已 WARN 声明为 at-least-once 残留（javadoc 引 backend-semantics-design.md 3.3.2），非缺陷；④ Pulsar 侧无对应 commitSync（ack 即提交，失败经 waitAll 上抛）—— 对照排除。
- **信心水平**: 确定
- **误报排除**: 非一般性"吞异常"误报——吞的方向安全不丢数据，报的是长时运行可见性与同循环内错误处理框架的不一致，定 P3 与此匹配。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现（:421-426 commitSync 吞异常仅日志 + dispatched=true 使 runTask :298-300 重置退避实测确认；与同循环 poll/批量 commit 上抛路径的退避+failure 回调不对称成立；F9 方向安全（at-least-once 保持）判定成立）

---

## 维度集外线索（overlay C2/C4/C7/C9）

### [nop-message-CL-005] [C9] NopStreamOffsetBackingStore.REGISTRY 静态账本仅 clearConnector 一条清理路径且无人保证调用

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java:59,81-90,101-103`
- 静态 `ConcurrentHashMap<String, ConcurrentHashMap<ByteBuffer,ByteBuffer>> REGISTRY` 以 connector 名为键持有 offset map；`forConnector`（L85 computeIfAbsent）创建条目，唯一移除入口 `clearConnector`（L101-103）的调用方在本模块之外（javadoc 称由 nop-stream `DebeziumCdcSourceFunction.initializeState` 的 first-run 路径调用）。直接使用 `DebeziumMessageSource`/`DebeziumEngineWrapper` 的部署（nop-message 自身入口）没有任何路径触发清理。条目体积小（offset 字节），但键空间随部署重启/改名无界增长——F4 家族（长时运行 JVM 内缓慢累积 + 残留旧 offset 被同名 connector 复用的语义风险已由 AR-03 的 fail-fast 部分缓解）。建议 C9 复核时确认 nop-stream 侧 clearConnector 调用的覆盖完备性（改名、失败重启、非 stream 入口三类场景）。

### [nop-message-CL-006] [C4] Debezium 引擎长驻占用全局共享 worker 池

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:159`
- `engine.run()` 是直到 close 才返回的长驻阻塞任务，被直接提交到 `GlobalExecutors.globalWorker()` 共享池。每个运行中的 CDC connector 永久占一个 worker 位；connector 数量受全局池容量约束且与其他全局任务的隔离性依赖全局池的配置（该池的容量/拒绝策略属平台设施，本模块未做任何用量评估或专用隔离）。C4 复核建议：确认 globalWorker 的容量语义，评估"多 connector 部署耗尽全局 worker → 阻塞无关全局任务"的队列溢出面。

### [nop-message-CL-007] [C4] Kafka/Pulsar doStop 串行 cancel 每订阅最长 6 秒，N 订阅停机时间线性放大且无总预算

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaMessageService.java:143-151,578-601`；`nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java:167-178,596-620`
- 两后端 `doStop` 对 subscriptions 队列逐个 `cancel()`，每个 cancel 内含 `awaitTermination(5s)` + 失败再 `awaitTermination(1s)` 的串行预算；Kafka 侧随后 producer.close 还要再等 sendTimeoutMs。`concurrency>1` 的扇出订阅（每个成员都是独立 subscription）按 N 倍放大。最坏停机时长 ≈ 6s×订阅数 + sendTimeout，无总预算或并行化。方向安全（顺序停机避免资源竞态，注释声明 F-27 排序是有意的），C4 复核时按"停机超时被上层 kill -9 截断 → producer.close 不执行"的面评估即可。

### [nop-message-CL-008] [C4] LocalMessageService 无生命周期：红投调度器线程每实例一个、永不关闭，service 丢弃后在途红投仍会触发

- **文件**: `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:280-295`
- `redeliveryScheduler()` 懒建单线程 daemon 调度器，无 stop/destroy 方法、`redeliveryExecutor` 无关闭路径；bean 销毁（IoC destroy）后该线程存续，已调度的红投任务仍会在丢弃的 service 实例上执行 `invokeSubscription`（引用链经调度队列保住整个 service 与订阅者对象图）。
- 对照组：Kafka/Pulsar 每订阅 executor 均有 cancel 关闭路径（shutdownNow + consumer.close），本类是 4 根中唯一无线程生命周期的运行期资源持有者。daemon 属性保证 JVM 退出无碍，但应用内热替换/丢弃 LocalMessageService 的场景留下不可回收的线程与僵尸回调。
- 影响半径有限：调度器每实例至多一个线程、队列为延迟红投在途量（有界）；主要问题是生命周期不对称（F8 家族）而非无界增长。
- C4 复核建议：补 destroy 方法（shutdownNow + RejectedExecutionException 路径已有 F-40 处理可复用，见 LocalMessageService.java:263-269），或将调度器托管到平台执行器设施（io.nop.commons.concurrent.executor）统一治理。

---

## 维度复核结论（WI 4.1 独立复核，2026-10-20）

复核人（独立子代理，未参与初审/深挖）对 10 条发现逐条回码验证：全部等待点/orTimeout 站点/异常屏障/ack-rely 序对照点逐一比对行号，触发时序逐步推演，严重度对照 `ai-dev/skills/concurrency-resource-audit-prompt.md` 校准表；跨源核验点：`KafkaConsumeTask.java`（:301-317/:355-356/:421-426/:509-512/:516/:529-546/:689-695）、`PulsarConsumeTask.java`（:107-132/:237-306/:319-382/:482-489）、`PulsarMessageService.java`（:227-230/:252-277/:628-642）、`KafkaMessageService.java`（:273-317/:390-435）、`LocalMessageService.java`（:74-81/:213-234/:250-270）、`DebeziumEngineWrapper.java`（:159-190）。

### 逐条判定表

| 条目 | 初判 | 判定 | 最终严重度 | 现状标注 | 关键复核依据 |
|---|---|---|---|---|---|
| C3-001 | P2 | 保留 | P2 | 新发现 | 5 等待点+DLQ 面⑥逐点实测；有界停止设计不覆盖 getResult 成立 |
| C3-002 | P1 | 保留 | P1 | 新发现 | 无屏障+三抛点+started 撒谎+零 listener 接线实测；严重度注记见跨单元对照 3 |
| C3-003 | P2 | 保留（本站点主登记，吸收 C1-006） | P2 | 新发现 | 交错 A/B 实测；C1 卷 C1-006 为其交错 A 子面，C6 卷另有 put-then-start 面——合并归一 |
| C3-004 | P2 | 保留 | P2 | 新发现 | 无 try/catch vs F-39 对照；版本无关面=client==null 同步逸出 |
| C3-005 | P2 | 保留 | P2 | 新发现 | 三处同族 ack/reply 序对照+ConsumeLater 后端分叉实测 |
| C3-006 | P2 | 保留 | P2 | 新发现 | suspended 丢弃未声明+javadoc 契约句仅覆盖 cancelled |
| C3-007 | P3 | 保留 | P3 | 新发现（WI 2.3 CL-022 落点闭环） | grep 复现 2 调用点+1 javadoc 行；三点裁定成立 |
| C3-008 | P3 | 保留 | P3 | 新发现 | commitSync 吞+退避重置实测 |
| C3-009 | P2 | 保留 | P2 | 新发现 | 7 屏障+grep Throwable=0 复现；族内吸收注记见跨单元对照 4 |
| C3-010 | P3 | 保留 | P3 | 新发现 | null NPE vs Kafka 显式契约对照实测 |

**判定统计**：保留 10、降级 0、驳回 0。
**最终严重度分布**：P1×1 / P2×6 / P3×3（与初审一致，无校准变动）。
**现状标注分布**：新发现×10（本单元无前序 CRA 覆盖；WI 2.3 CL-022 为移交输入非前案；WI 1.2 R5-CON-02/03 为已交付机制，C3-002/C3-009 是其新失效面，非「已知未修复」）。

### 跨单元对照（cra-index 与历史审计目录）

1. **WI 2.3 CL-022 落点闭环确认**：C3-007 为纯移交轮。移交内容（「JDK Delayer orTimeout 11 站点共享面」→ nop-ai/nop-message）在本模块的落点经独立 grep 复现为主代码恰 2 处（kafka:313/pulsar:273），处置以 javadoc 已声明 fire-and-forget + client 层兜底（delivery.timeout.ms/sendTimeout）闭合三点疑问，残留两点（零配置无上界、sent 指标计数点位）降 P3。**移交闭环成立，无未落点。**
2. **WI 3.1 MA-11 同契约异实现**：`DBMessageService`（nop-ai-agent:79，implements IMessageService）与本模块四实现同一 SPI 契约。C3-001（onMessage 返回的 CompletionStage 无上界等待）与 MA-11（DBMessageService 单线程 poller 内联消费/call-agent join 冻结）为同契约的两种独立实现缺陷——家族关联、非重复立项；建议 C3-001 的 consumeAwaitTimeout 修复与 MA-11 修复批次对齐契约语义（超时上界成为 IMessageService 消费面约定）。
3. **WI 1.2 Debezium 同族**：① C4-004/MA-06（引擎常驻 globalWorker，P1）= 本单元 CL-006 同面——overlay 线索不重复立案，正确；② C3-014/MA-07（停止契约无终结保证，P1）为 wrapper 生命周期的 nop-stream 使用面，本单元 C1-011/C6-005 为本体面——双轨互补；③ C3-002/C3-009 的 notifyEngineFailure 缺口是 R5-CON-03 机制的**新失效面**（转换异常面 + Error 面宽度），非重复。**C3-002 严重度注记**：nop-stream 主路径引擎死亡已可见（listener→任务失败重启），P1 的最高危面是直接使用 `DebeziumMessageSource` 的部署（仓内 main 代码仅 nop-stream-fraud-example 示例接线）；维持 P1 的依据是校准行「异步链异常被静默吞掉导致状态不一致」（isStarted=true vs 引擎已死）与 R5-CON-03 自证的后果等级（CDC 静默断流）；若 7.1 按「生产 main 接线零命中」加权，P2 为降档落点——分歧显式留 7.1。
4. **C3-009 族内判级**：WI 2.1 C3-001（周期任务平台异常绝缘层，P1，29 生产站点）/ WI 1.7 C6-001（Error 逃逸，P1）为同族先例。本条维持 P2：修复归属统一绝缘层批次（族内吸收、避免同族双 P1 计数），僵尸 isActive 面与 Error 死亡绕过 notifyEngineFailure 面为模块独有增量；若 7.1 统一族内判级，本条随 WI 2.1 C3-001 升格。
5. **同单元 C8 卷极性对照（留合并裁定）**：本卷 C3-002 失效面枚举引用 dispatchEvent 的 per-consumer 屏障为「正确形态」，C8 卷以「dispatchEvent 吞异常推进 offset」立案 P1——同一屏障的存活极性（防断流）与投递极性（静默丢事件）判定相反。**表态**：两案均保留、修复同一 diff——per-event 屏障须配毒事件 failure endpoint（跳过+计数+上报），既不纯吞（C8 面）也不整体炸流（C3-002 面）。

## 最终保留项

- **保留工单 10 条**（零驳回、零降级）：P1×1（C3-002）进入 Phase 8 批量修复面；P2×6/P3×3 按 P2 裁决台账流程处置。
- **同 diff 修复批建议**：① Debezium 屏障族——C3-002 + C3-009（Debezium 面）+ C8 卷 dispatch 面（毒事件 endpoint 设计统一）；② DebeziumMessageSource subscribe/stop 锁序——C3-003（吸收 C1-006 与 C6 卷 put-then-start 面）；③ 消费等待上界与指标口径——C3-001 + C3-007 残留（consumeAwaitTimeout + sent 计数点位统一，与 WI 3.1 MA-11 契约对齐）；④ C3-004（Pulsar sendAsync try/catch 对齐 F-39 形态）、C3-005（批量 ack/reply 排序对齐单条路径+ConsumeLater 语义声明）、C3-006（suspended 红投重调度+javadoc 放弃矩阵）各自独立小 diff。
