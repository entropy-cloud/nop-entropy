# C6 资源生命周期与泄漏 — nop-message（WI 4.1）

- 审计维度：C6（资源生命周期与泄漏）
- 批次：单批次，覆盖全部 4 源码根
- 审计日期：2026-10-20
- 方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md`（高频缺陷族 F1–F11、C6 维度正文、附录 A 13 字段格式）

## 审计范围与口径

### 单批次 4 源码根覆盖声明

逐文件审读以下 4 个源码根的全部 31 个 main 源码文件（test 源码仅用于行为佐证，不作发现对象）：

| 源码根 | 文件数 | 重点站点 |
|---|---|---|
| `nop-message/nop-message-core/src/main/java` | 7 | LocalMessageService 调度器生命周期（CL-009 线索，立案 C6-001）、Subscription 注销路径、ReflectionMessageSubscriptionRegistrar 注册/注销对称性 |
| `nop-message/nop-message-kafka/src/main/java` | 7 | KafkaProducer/KafkaConsumer 创建与关闭、doSubscribe 异常路径、cancel 释放序 |
| `nop-message/nop-message-pulsar/src/main/java` | 7 | PulsarClient/Producer 缓存/Consumer 创建关闭、doSubscribe 外层 catch、cancel 释放序 |
| `nop-message/nop-message-debezium/src/main/java` | 10 | DebeziumEngineWrapper 启停/一次性面（CL-013 线索，立案 C6-005）、DebeziumMessageSource 订阅生命周期、NopStreamOffsetBackingStore 清理路径 |

平台基础设施只审使用方式不重审实现：`GlobalExecutors.globalWorker()`（nop-kernel/nop-commons `GlobalExecutors.java:85-96`，默认 maxPoolSize=30，见 `CommonConfigs.java:26-27`）、`LifeCycleSupport`、NopIoC `@PreDestroy` 支持（`nop-core-framework/nop-ioc/.../DefaultBeanClassIntrospection.java:70` 确认支持）。跨模块证据仅取调用点行号：`nop-stream/nop-stream-connector-debezium/.../DebeziumCdcSourceFunction.java`（clearConnector/registerFailureListener 的唯一生产调用方）。

### 与已立案条目的边界声明

- **NopStreamOffsetBackingStore**：C1-002（并发面）、C5-001（内存面）已立案；本维度只审其 **close/清理路径面**（→ C6-004），不重复并发/内存结论。
- **Kafka/Pulsar destroy 窗口守卫失效**：C1-003/C1-005 已立案守卫失效面；本维度只审 **资源释放序**（经复核：两侧 doStop 释放序（先订阅后 producer 后 client）、cancel 的"先停线程后 close consumer"序均已配对且被 WI20/WI26 修复覆盖，未发现新的释放序缺陷）。
- **DebeziumEngineWrapper 启停/生命周期一次性面**（CL-013 线索）：本维度立案为 C6 主面（C6-005）；C6-002/C6-004/C6-006 为同文件族的外延失效面。
- **LocalMessageService 调度器生命周期**（CL-009 线索）：本维度立案为 C6 主面（C6-001）。

### 口径纪律声明

1. 基线 `_tmp/cra-baseline-nop-message.txt` 的 C6a（L377–453）/C6b（L454–472）仅用于圈定重点站点；全部数量与判定均由人工逐文件复核得出，未采用基线计数。
2. **降档口径**：本报告全部泄漏/残留形态（C6-001/002/004、CL-015/016/017）**不适用"启动期单线程"降档**——daemon 线程不阻碍 JVM 退出只影响 P0/P1 判级，不构成"不是问题"；"stop 后不可重启"等一次性生命周期面（C6-005）按已声明边界/使用面校准：模块内唯一生产使用方 `DebeziumMessageSource` 每次 `(re)start` 均新建 wrapper（`DebeziumMessageSource.java:143`），使用面与一次性契约一致，故 C6-005 校准为 P3。
3. 并发类判定均给出具体触发时序；写不出必然触发输入的（C6-003）按纪律降 P2 并标注"理论风险，未具体化"。
4. 已声明的边界不算缺陷：Kafka/Pulsar cancel 的"线程卡死超预算后 best-effort WARN'd close"（各自 javadoc 引 backend-semantics-design.md 3.8.1）、Pulsar destroy 窗口 producer 的 adapted error（F-30 javadoc）、PulsarConsumeTask `pendingBatch` 停止后消息留待 redelivery（javadoc 已声明）均不立案。

### 资源获取点配对表（C6 维度输出件）

| # | 资源 | 获取点 | 释放点 | 异常/取消/超时路径 | 判定 |
|---|---|---|---|---|---|
| 1 | KafkaProducer | `KafkaMessageService.doStart` :127 | `doStop` :153–162（close(Duration) + 置 null） | close 异常 WARN 继续；destroy 后 sendAsync 走 `getProducer` 类型化拒绝（:235–241） | 配对 |
| 2 | KafkaConsumer | `doSubscribe` :407（失败 :409–414 类型化拒绝，无泄漏） | `KafkaMessageSubscription.cancel` :596（先等 executor 5s+1s 再 close） | **构造成功后的后续步骤抛错无清理** → C6-003 | 部分配对 |
| 3 | Kafka consume executor | `doSubscribe` :428 | `cancel` :583–594（shutdown→await 5s→shutdownNow→await 1s→中断兜底） | InterruptedException 分支 shutdownNow | 配对 |
| 4 | PulsarClient | `doStart` :139 | `doStop` :189–196 | close 异常 WARN；client=null 后 buildProducer 有 :227–230 守卫 | 配对 |
| 5 | Pulsar Producer（按 topic 缓存） | `getProducer` :218 computeIfAbsent → `buildProducer` :238 | `doStop` :180–187 逐个 close + clear | create 失败即抛（computeIfAbsent 无残留条目）；destroy 窗口已声明（F-30） | 配对 |
| 6 | Pulsar Consumer | `doSubscribe` :501 | `PulsarMessageSubscription.doOnCancel` :615 | **外层 catch :537–551 close+shutdownNow**（WI8 修复） | 配对 |
| 7 | Pulsar consume executor | `doSubscribe` :525 | `doOnCancel` :597–609 | 同 #3 兜底序 | 配对 |
| 8 | Debezium engine | `DebeziumEngineWrapper.start` :151–156 | `stop` :207–212 engine.close() | close 异常 WARN；**不 join、running 过早翻转、一次性面** → C6-005 | 部分配对 |
| 9 | Debezium worker 线程占用（globalWorker） | `start` :159 | engine.run() 返回自然释放 | start 阶段失败窗口 → C6-005(d)；池容量耦合 → CL-018 | 部分配对 |
| 10 | NopStreamOffsetBackingStore.REGISTRY 条目 | `forConnector` :85 / `configure` :109 | 仅外部 API `clearConnector` :101–103；模块内生命周期（DebeziumMessageSource.stop）不调用 | 无 | **不配对** → C6-004 |
| 11 | LocalMessageService redelivery executor | `redeliveryScheduler` :285（懒建） | **无任何释放路径** | RejectedExecutionException 已兜（:263–269），但 executor 本体永不 shutdown | **不配对** → C6-001 |
| 12 | FAILURE_LISTENERS 注册 | `DebeziumEngineWrapper.registerFailureListener` :52–62 | `unregisterFailureListener` :67–71 | API 对称；模块内生产代码无注册点，nop-stream 调用方 finally 注销（DebeziumCdcSourceFunction.java:248/298） | 配对 |
| 13 | Micrometer meters | `MessageMetrics.create` :38–45 | 无显式反注册 | Micrometer 同名去重，service 重启不累积 meter | 配对（按 registry 契约） |
| 14 | ReflectionMessageSubscriptionRegistrar subscriptions | `register` :99–100 | `@PreDestroy destroy` :140–151；register 失败回滚 :57–66 | cancel 异常吞为 best-effort（原错误优先） | 配对 |

## 发现

### [C6-001] LocalMessageService 重投递调度器只建不关，bean 无 destroy 挂钩（CL-009 立案）

- **文件**: `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:274-295`（字段+懒建工厂）、`:250-270`（scheduleRedelivery）；`nop-message/nop-message-core/src/main/resources/_vfs/nop/message/beans/message-core-defaults.beans.xml`（`nopLocalMessageService` bean 无 destroy-method）
- **证据片段**:
  ```java
  // LocalMessageService.java:280-295
  private ScheduledExecutorService redeliveryScheduler() {
      ScheduledExecutorService executor = redeliveryExecutor;
      if (executor == null) {
          synchronized (this) {
              if (redeliveryExecutor == null) {
                  redeliveryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                      Thread t = new Thread(r, "nop-local-message-redelivery");
                      t.setDaemon(true);
                      return t;
                  });
              }
              executor = redeliveryExecutor;
          }
      }
      return executor;
  }
  ```
  全类无 stop/destroy/close 方法；`clearConsumers()`（:54-56）只清 map；beans.xml 中该 bean 既无 `destroy-method` 也无 `@PreDestroy`。
- **严重程度**: P2
- **并发场景**: 消息分发线程（send/invokeSubscription 调用链，首个 ConsumeLater 触发懒创建）与 IoC 容器生命周期线程（销毁时无任何钩子可执行）。
- **触发时序**: T1 任意 send 投递到返回 `ConsumeLater` 的订阅 → `invokeSubscription` :219 → `scheduleRedelivery` :253 → `redeliveryScheduler()` :285 创建单线程 daemon 调度器，此后该线程与实例同寿；T2 容器销毁或调用方 `clearConsumers()` → 无任何路径 shutdown 该 executor，线程滞留。若容器在进程内重建（dev 热载、测试反复建容器），每个新实例再建一条新线程，旧实例线程全部永久滞留。
- **现状**: 调度器"分配必释放"闭环缺失——只建不关（F8 清理不对称），且 bean 装配层无 destroy 挂钩兜底。
- **风险**: daemon + 单线程 + 每实例一条，故无耗尽级后果（因此 P2 而非 P0/P1）；但长驻应用每经历一次容器重建即净增一条永久空闲线程，测试套件中大量实例各自遗留线程，线程名全部同名（`nop-local-message-redelivery`），诊断不可分辨。
- **建议**: 为 LocalMessageService 增加 `destroy()`（shutdownNow + 置空 `redeliveryExecutor`）并在 beans.xml 挂 `destroy-method`；或直接复用平台托管线程池 `GlobalExecutors.globalTimer()`（平台统一生命周期，免自建线程）。
- **失效面枚举**: 模块内全部生命周期管理点逐一——KafkaMessageService（init/destroy + LifeCycleSupport，beans.xml 已挂，**配对**）；PulsarMessageService（同左，**配对**）；ReflectionMessageSubscriptionRegistrar（`@PreDestroy destroy`，NopIoC 确认支持，**配对**）；DebeziumMessageSource（`stop()` 公开 API + 订阅 cancel 自动停，**配对**，另见 C6-002 的失败路径缺口）；LocalMessageService（**唯一未配对**）。
- **信心水平**: 确定
- **误报排除**: 非"低频路径粗粒度锁"或"原语使用"类误报；这是 C6 命题"分配的资源在所有路径被释放"的直接违反。daemon 属性只影响判级（不阻碍 JVM 退出 → 非 P0/P1），按本报告口径声明，泄漏/残留形态不适用启动期单线程降档。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（全类无 stop/destroy/close + beans.xml `nopLocalMessageService` 无 destroy-method 逐点回码确认；懒建 :280-295 与 RejectedExecutionException 兜底 :263-269 在位但 executor 本体永不 shutdown 成立。修复建议中「复用 GlobalExecutors.globalTimer()」一项不采：WI 2.1 C4-001 已立案 globalTimer 死配置+共享定时器阻塞放大（P1），新增消费方会加重已裁定平台病——建议改为可销毁专用 executor 或平台 `IThreadPoolExecutor`；与 C5-003/C8-003 同一 executor 三面同 diff 批次）

### [C6-002] DebeziumMessageSource.subscribe 失败路径残留"幽灵订阅"：异常上抛但注册条目留存且永久不可注销

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:72-80`（先 put 后启动）、`:137-153`（startEngineIfNeeded 失败重抛）、`:155-163`（dispatchEvent 按 keySet 全量投递）
- **证据片段**:
  ```java
  // DebeziumMessageSource.java:72-80
  public ICancellable subscribe(Consumer<ChangeEvent> action) {
      Subscription subscription = new Subscription(action);
      subscriptions.put(action, subscription);          // 先落账
      // 首次订阅时启动引擎
      startEngineIfNeeded();                             // 失败 → NopException 上抛，put 不回滚
      return subscription;
  }
  // :147-152
  } catch (Exception e) {
      LOG.error("Failed to start Debezium engine: {}", config.getName(), e);
      throw new NopException(DebeziumErrors.ERR_DEBEZIUM_ENGINE_START_FAILED)...
  }
  ```
- **严重程度**: P1
- **并发场景**: 订阅调用线程与引擎 worker 线程（`dispatchEvent` 在 globalWorker 上遍历 `subscriptions.keySet()`）；单线程即可触发，无需竞态。
- **触发时序**: T1 `subscribe(A)`：:74 put 落账 → :77 → :144 `engineWrapper.start()` 因 broker 不可达/引擎构建失败抛 NopException → 调用方收到异常，**未拿到返回的 ICancellable**，但 A 的 Subscription 留在 map（cancelled=false）；T2 之后任一次 `subscribe(B)` 成功启动引擎 → `dispatchEvent` :156 遍历 keySet，把每个 CDC 事件同时投递给幽灵 A——被告知"订阅失败"的消费者持续收到事件，且其闭包引用（含业务对象）经 map 强引用永久滞留；连续失败 N 次积累 N 个幽灵条目，仅 `stop()` 可清。
- **现状**: 错误路径"半个成功"（F2/F8）：注册动作在成败判定之前生效，失败时不回滚。
- **风险**: 订阅者语义违约（异常 ≠ 未订阅）叠加错误路径资源滞留；启动期 broker 抖动是常态场景，非罕见路径。
- **建议**: 将 `subscriptions.put` 移到 `startEngineIfNeeded()` 成功之后；或在 catch 中 `subscriptions.remove(action)` 后再重抛。
- **失效面枚举**: "先注册后启动/先建后登记"同族入口逐一——DebeziumMessageSource.subscribe（**未配对**，本条）；KafkaMessageService.subscribe 多并发分支 :371-386（失败即 cancel 已建子订阅，**配对**，WI29/F-37）；KafkaMessageService.doSubscribe（createConsumer 失败 :409-414 配对，构造后缺口见 C6-003）；PulsarMessageService.subscribe :356-371（**配对**）+ doSubscribe 外层 catch :537-551（**配对**，WI8）；ReflectionMessageSubscriptionRegistrar.register :54-66（失败即批量 cancel，**配对**，WI29/F-38）。
- **信心水平**: 确定
- **误报排除**: 不是"无状态单例被并发调用"或"原语使用"误报；这是单线程、确定性可复现的错误路径泄漏 + 投递语义违约，触发输入具体（引擎启动失败的任意一次订阅）。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜现状:新发现（:74 先 put→:77→:147-152 catch 重抛不回滚、dispatchEvent :156 遍历 keySet 逐点回码确认。**子项复核——触发窗收窄**：引擎未启动期间 dispatchEvent 不会被调，幽灵投递须等后续任一 subscribe 成功启动引擎；幽灵条目数受「启动失败重试次数」约束非无界。**生产主路径结构性免疫**：唯一 main 生产调用方 DebeziumCdcSourceFunction 每次 run 新建 source 实例（:220-222/:258），任务失败重启即弃旧实例，幽灵面自愈；受损面=长生命周期直接嵌入方（复用同一 source 反复 subscribe——失败后重试订阅即与幽灵形成同事件双投递）。维持 P1 依据=F2「半个成功」无恢复语义校准行 + 确定性触发；嵌入方暴露面权重与 C3-002 同口径留 7.1）

### [C6-003] Kafka doSubscribe 构造后无清理守卫——与 Pulsar 同族外层 catch 不对称（F3 兄弟漏修）

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaMessageService.java:405-435`；对照 `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java:494-551`
- **证据片段**:
  ```java
  // KafkaMessageService.doSubscribe:405-434 —— createConsumer 之后无 try/catch
  Consumer<String, String> kConsumer;
  try {
      kConsumer = createConsumer(buildConsumerProps(groupId, options), ...);   // :407
  } catch (Exception e) { ... throw error(ERR_KAFKA_CONSUMER_CREATE_FAILED ...); } // 仅覆盖构造
  java.util.List<String> topics = java.util.Collections.singletonList(subConfig.getTopic());
  KafkaConsumeTask task = newTask(kConsumer, subConfig);                      // :416
  kConsumer.subscribe(topics, task.rebalanceListener());                      // :417 可能抛
  ...
  ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);// :428
  task.bindExecutor(executor, pollTimeout);
  task.start();                                                               // :430
  KafkaMessageSubscription subscription = new KafkaMessageSubscription(...);  // :432
  subscriptions.add(subscription);                                            // :433
  ```
  对照 PulsarMessageService.doSubscribe :537-551：同位置有外层 `catch (Exception e)` 执行 `consumer.close()` + `executor.shutdownNow()`（javadoc 明示 WI8 closure audit Major-1 修复）。
- **严重程度**: P2（理论风险，未具体化——写不出必然抛错的输入，按口径纪律降 P2；KafkaConsumer.subscribe 对全新实例抛错的现实触发面极窄）
- **并发场景**: 订阅调用线程；泄露对象为已构造的 KafkaConsumer（内部 network/heartbeat 线程）与/或单线程 executor。
- **触发时序**: T1 :407 createConsumer 成功（consumer 已持有网络线程）→ :417 `kConsumer.subscribe(...)`（或 :428/:430）抛 RuntimeException → 异常直接冒泡，无任何 catch 关闭 kConsumer/executor；此时 subscription 尚未 :433 入队，`doStop` 的遍历（:144-151）永远够不到它 → 网络线程随 consumer 对象泄漏至 GC 根消失（consumer 被 task 强引用，task 被弃置后可达性终结，但 Kafka 线程为非 daemon 化配置时依赖 close 才退出——未 close 即按 client 默认线程模型滞留至进程退出）。
- **现状**: F8 清理不对称的家族残留：Pulsar 侧已修（WI8），Kafka 侧同构代码未获得同等守卫。
- **风险**: 低概率 × 高粘性——一旦触发，泄漏的 consumer 线程与 group coordinator 会话残留（后续同 groupId 订阅受 rebalance 噪音影响）。
- **建议**: 复制 Pulsar 的外层 catch 形态：consumer/executor 局部变量前置声明，catch 中 close + shutdownNow 并 addSuppressed 后重抛。
- **失效面枚举**: 两后端 subscribe 路径逐一——PulsarMessageService.doSubscribe（**已配对**，:537-551）；KafkaMessageService.doSubscribe（**未配对**，本条）；两处 `subscribe()` 多并发包装层（Kafka :371-386 / Pulsar :356-371 均已配对）；ReflectionMessageSubscriptionRegistrar.register（**已配对**）。
- **信心水平**: 很可能（缺口确定存在；触发概率低）
- **误报排除**: 非"原语使用本身"误报——报的是配对缺失而非 synchronized/KafkaConsumer 用法；亦非已声明边界：Kafka 侧无任何 javadoc 声明放弃该守卫。
- **复核状态**: 未复核
- 复核状态: 保留(P2，理论风险未具体化标注维持)｜现状:新发现（:405-414 try/catch 仅覆盖构造、:417/:428/:430 裸奔段回码确认；Pulsar 侧 :494-495 局部变量前置声明 + :537-551 外层 catch close+shutdownNow 的对照形态实测成立——恰为本条建议的修复形态。纪律项 4 降档（写不出必然抛错输入）适用正确。修复区域与 C1-007（add-after-clear/生命周期守卫面）同段，两单同 diff 协调避免互相遮蔽）

### [C6-004] NopStreamOffsetBackingStore 静态 REGISTRY 的清理路径不在本模块生命周期内：stop 不清条目，独立使用面可 stale-resume（C1-002/C5-001 边界外，本条仅清理路径面）

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java:59-60`（静态 REGISTRY）、`:81-90`（forConnector 写入）、`:101-103`（唯一 remove API）；`nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:168-180`（stop 不调 clearConnector）
- **证据片段**:
  ```java
  // NopStreamOffsetBackingStore.java:59-60, 101-103
  private static final ConcurrentHashMap<String, ConcurrentHashMap<ByteBuffer, ByteBuffer>> REGISTRY =
          new ConcurrentHashMap<>();
  public static void clearConnector(String connectorName) {
      REGISTRY.remove(connectorName);
  }
  // DebeziumMessageSource.stop():168-180 —— 只停引擎/清订阅，无 clearConnector
  public synchronized void stop() {
      if (engineWrapper != null) { ... engineWrapper.stop(); ... engineWrapper = null; }
      started = false;
      subscriptions.clear();
  }
  ```
- **严重程度**: P2
- **并发场景**: 引擎 worker 线程（offset 写入 `data`）与 source 生命周期线程（stop）；静态 REGISTRY 为 JVM 级单例账本。
- **触发时序**: T1 独立使用 `DebeziumMessageSource`（本模块公开 API，不经 nop-stream source function）：subscribe → `forConnector` :85 写入 REGISTRY → 引擎经反射实例化第二实例 `configure` :109 绑定同一 map → `stop()` 只停引擎，条目（连接器名 → 最终 offsets）JVM 终身滞留；T2 同名连接器再次 subscribe → `forConnector` 命中旧条目，引擎从上一轮 stale offset **静默续传**（AR-03 语义）。唯一生产清理点在 nop-stream 侧首跑路径（DebeziumCdcSourceFunction.java:440-444 `newFreshOffsetStore`），独立使用面无人清理。
- **现状**: put/remove 配对缺口：模块提供 remove API 但自身生命周期从不调用；无界性取决于连接器名基数（固定名为有界残留，动态名/多租户名为慢速无界，F4 变体）。
- **风险**: 内存残留为每条目一份最终 offset（小）；实质风险是 stale-resume 语义——独立使用方重启订阅后从旧位点继续，恰好漏掉间隔期变更且无告警。
- **建议**: `DebeziumMessageSource.stop()` 增加 `NopStreamOffsetBackingStore.clearConnector(config.getName())`；若担心破坏"重启续传"期望，则在类 javadoc 显式声明"独立使用须自行 clearConnector"的边界契约（当前无任何声明）。
- **失效面枚举**: nop-message 全部静态注册表——NopStreamOffsetBackingStore.REGISTRY（**remove 存在但生命周期不调，本条**）；DebeziumEngineWrapper.FAILURE_LISTENERS（register/unregister 对称，模块内生产代码零注册点，nop-stream 调用方 finally 注销 DebeziumCdcSourceFunction.java:248/:298，**配对**）；LocalMessageService.consumers（残留面另报 CL-015）。
- **信心水平**: 确定（残留面）/ 很可能（stale-resume 面——取决于独立使用是否真实存在）
- **误报排除**: 非 C5-001（内存面）重复计数——本条主张的是"清理调用点缺失 + 语义后果"，与 C5 的容量/增长分析、C1 的并发分析互斥；亦非已声明边界：`clearConnector` javadoc 仅声明了 stream 侧首跑语义，对独立 stop 路径无声明。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（DebeziumMessageSource.stop :168-180 无 clearConnector、nop-stream :442 唯一清理点、forConnector :85 computeIfAbsent 命中旧条目→stale-resume 链逐点回码确认。三面分层 C6 侧成立：本条=清理调用点缺失+stale-resume 语义面，与 C5-001（内存演化）同根同主修复，**同 diff 落地**；stop-clear 会改变「同 JVM 同名重启续传」期望的契约取舍须一次性裁决（见 C5 卷复核对照 1）。仓内 main 独立使用面=DebeziumCdcSourceFunction（自身有清理序）+示例树，stale-resume 受损面为外部嵌入方，「很可能」自评维持）

### [C6-005] DebeziumEngineWrapper stop 语义三缺口：不 join、isRunning 过早翻转、一次性生命周期无 fail-fast（CL-013 立案主面）

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:135-143`（start 守卫）、`:159-173`（worker 异步执行）、`:195-215`（stop）
- **证据片段**:
  ```java
  // stop():195-215
  public synchronized void stop() {
      if (!startRequested.get() || stopped.getAndSet(true)) {
          return;                                  // start-after-stop 永久静默 no-op
      }
      ...
      if (engine != null) { engine.close(); }      // 不等待 engine.run() 返回
      ...
      running.set(false);                          // run() 尚未返回即翻转
  }
  // start():135-143 —— startRequested/stopped 永不复位，wrapper 一次性
  if (running.get() || startRequested.get()) {
      LOG.warn("Debezium engine is already running");
      return;
  }
  ```
- **严重程度**: P3（按口径校准：模块内唯一生产使用方 DebeziumMessageSource 每次 (re)start 新建 wrapper（:143），使用面与一次性契约一致；缺口只影响直接持有 wrapper 的外部用户）
- **并发场景**: 调用 stop 的生命周期线程与 globalWorker 上执行 run() 的引擎 worker 线程。
- **触发时序**: (a)(b) T1 `stop()` 返回（:214 已置 running=false）→ T2 worker 线程仍在 `handleDebeziumEvent` 分发当前批事件 → 观察者看到 `isRunning()==false` 却仍收到 `changeEventConsumer` 回调（source 侧因已清订阅而 no-op，直接使用者则真实收到）。(c) T1 start→stop→再 start → :136 命中 startRequested → warn + return，无类型化拒绝，调用方误以为引擎已启动。(d) T1 start 中引擎构建成功但 :159 `execute` 抛 RejectedExecutionException → 外层 catch 重抛，但 startRequested 已置位且 engine 无机会 close——wrapper 毒化为不可用（stop 可补救）。
- **现状**: start/stop 对称性停留在"触发关闭"层面，缺"等待静默"（quiesce）与"一次性契约声明"；对照 Kafka/Pulsar cancel 均 await 终止后才 close（已配对）。
- **风险**: 一次性面内无资源泄漏（engine.close 已调用、worker 会自然退出），属契约与可观测性缺口；外部使用方据此写的 drain 逻辑会在收尾窗口漏事件或误判。
- **建议**: start 记录 worker Future（或 CountDownLatch），stop() close 后有限时 join；running 翻转只交给 worker finally；start-after-stop 抛类型化错误或在 javadoc 显式声明 one-shot 契约。
- **失效面枚举**: stop/join 语义同族——KafkaMessageSubscription.cancel（await 5s+1s 后 close，**配对**）；PulsarMessageSubscription.doOnCancel（同构，**配对**）；DebeziumEngineWrapper.stop（**未配对**，本条）；DebeziumMessageSource.stop（委托 wrapper，同一缺口）。一次性面同族——LifeCycleSupport 状态机（stop 后 start 抛状态错，**有守卫**）；DebeziumEngineWrapper（warn+return，**无 fail-fast**，本条）。
- **信心水平**: 确定（行为面）/ 有趣的猜测（外部用户实际受损面）
- **误报排除**: 非已声明边界——R5 CON-02 只声明了"async 启动窗口不早退"，未声明 stop 不 join 与 one-shot；亦非"原语使用"误报（engine.close 的语义由本类的对外契约决定）。
- **复核状态**: 未复核
- 复核状态: 保留(P3，并入 C1-011 主登记)｜现状:新发现（(a)(b) stop :195-215 不 join/running.set(false) :214 过早翻转、(c) startRequested/stopped 永不复位 start-after-stop warn+return、(d) :143 置位→:175 catch 不回收 engine 的毒化窗，全部回码确认。C1-011（同文件一次性状态机无复位+start 失败砖化实例）并入本条为主登记成立——同根同站点，修复范围吸收其「start 失败 catch 复位 startRequested」子项；使用面校准（唯一生产方每次新建 wrapper :143）实测成立，P3 维持；WI 1.2 C3-014/MA-07 为 nop-stream 使用面双轨非重复）

### [C6-006] DebeziumMessageSource 以 action 身份作注册/注销键：cancel 命中同键兄弟注册并停掉活跃订阅的引擎（MSG-F-15 同族未修面）

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:32`（action 键 map）、`:72-74`（put 覆盖语义）、`:219-229`（cancel 按 action remove + 自动 stop）
- **证据片段**:
  ```java
  // :32  private final Map<Consumer<ChangeEvent>, Subscription> subscriptions = new ConcurrentHashMap<>();
  // :219-228
  public void cancel(String reason) {
      cancelled = true;
      cancelReason = reason;
      subscriptions.remove(action);            // 按 action 身份删——删掉的是同键"现任"条目
      LOG.debug("Subscription cancelled: {}", reason);
      if (subscriptions.isEmpty()) {
          stop();                              // 引擎在活跃订阅者脚下停止
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 两次 subscribe 传同一方法引用（`this::handle`）即触发，无需并发；取消线程与引擎 worker 投递线程存在窗口交叠。
- **触发时序**: T1 `source.subscribe(this::handle)` 两次 → 同一 Consumer 实例为键，第二次 put **覆盖**第一次（map 仅一条，返回的 S1 成为孤儿句柄，其 cancelled=false 且无法再注销）；T2 持 S1 的一方 `cancel()` → :222 按 action 键删除的是 S2 的注册 → map 空 → :226-228 `stop()` 停引擎 → S2 持有方 `isCancelled()==false` 却永久收不到事件，引擎在其未被告知的情况下停止。
- **现状**: 注册/注销身份错位（C6 步骤 5 注册表对称性的释放方向缺陷）；与 LocalMessageService WI23（MSG-F-15）修掉的同族缺陷同形，该处未同步。
- **风险**: 语义违约（幽灵活跃订阅 + 引擎误停）；触发条件收敛（同引用重复订阅在单例 listener 场景常见），确定性复现。
- **建议**: 按 WI23 同法改为按 Subscription 自身身份注销（`subscriptions.values().remove(this)`），或 map 改为 `Map<Subscription, ...>`；重复 action 订阅时可 WARN 声明覆盖语义。
- **失效面枚举**: 注销身份同族逐一——LocalMessageService.Subscription.cancel（:76-80，WI23 已改 Subscription 身份，**已修**）；KafkaMessageService/KafkaMessageSubscription（每 subscribe 独立对象入 ConcurrentLinkedQueue，cancel :600 remove(this)，**无身份键问题**）；PulsarMessageSubscription（Cancellable 基类 + remove(this) :619，**无问题**）；DebeziumMessageSource.Subscription（**action 身份，未修，本条**）。
- **信心水平**: 确定
- **误报排除**: 非"无状态 bean 并发调用"误报；触发不依赖竞态，单线程顺序调用即确定性复现；归属 C6 因其为资源释放路径的注销目标错位，与 CL-014（守卫不可达）相区分。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（:32 action 键 map、:74 put 覆盖语义、:219-229 按 action remove+isEmpty→stop 全链回码确认；S1 孤儿句柄（cancelled=false 不可注销）与 S2 脚下停引擎时序推演成立。设计 §3.9 WI23 明文禁令「不得回退到按 consumer 身份查找」——本条为该已裁定家族原则在 DebeziumMessageSource 的未修残留，修复随 WI23 同法（`subscriptions.values().remove(this)` 或 Map<Subscription,·>）；Local（:76-80 注释）/Kafka/Pulsar 已修对照实测成立。触发条件收敛（同方法引用双订阅）维持 P2）

## 维度集外线索（overlay C2/C4/C7/C9）

### [CL-014] Pulsar doSubscribe 的 checkIsActive 位于 newConsumerBuilder 之后——F-30 守卫在其目标场景不可达（C9 守卫/契约面）

`PulsarMessageService.doSubscribe` :431 先执行 `newConsumerBuilder(schema)`（:404-406 直接 `client.newConsumer(schema)`），:434 才 `checkIsActive()`。doStop :195 将 `client` 置 null，因此 destroy 后（或从未 init）订阅 → :431 NPE，:434 的类型化守卫（javadoc 明示"destroy-then-subscribe surfaces typed, not NPE (F-30)"）恰恰在其要防的场景不可达。同文件 `buildProducer` :227-230 的同款守卫位置正确（先判后用），构成同族对照。builder 阶段不建立真实连接，无资源泄漏，故不占 C6 编号；建议交换 :431/:434 顺序。

### [CL-015] LocalMessageService.consumers map 的 topic 条目只增不减 + cancel/clearConsumers 竞窗 NPE（C5/C7 残留面）

`subscribe` :187 `computeIfAbsent` 建 list，`cancel` :80 只 `remove(this)` 从不清空键 → 每个曾订阅过的 topic 永久留一个空 `CopyOnWriteArrayList`，`getBroadcastTopics`/`getNonBroadcastTopics` 持续遍历这些残留；topic 动态生成的应用下慢速无界（C5）。次要看点（主面归 C1 维度）：`clearConsumers()` :55 与 `cancel` :80 并发时 `consumers.get(topic)` 返回 null → NPE 从 cancel 逃逸（cancelled 标志已先置位，无状态破坏，但异常语义违约）。建议 remove 后 list 为空则 `consumers.remove(topic, list)`。

### [CL-016] LocalMessageService 重投递调度队列无上界（C5 无界面）

`scheduleRedelivery` :250-270 对每个 ConsumeLater 无条件 schedule：delay 仅 `Math.max(delayMs, 0)` 无上限，队列深度无界，每任务强引用 `message` + `subscription`。持续返回 ConsumeLater 且延迟长的订阅 + 高消息率 → 单线程调度器 DelayedQueue 按消息量单调增长（与 C6-001 同一 executor 的内存面）。建议：单订阅 pending 重投递去重（同订阅同消息仅保留最新），或为队列深度/延迟设上限并 WARN。

### [CL-017] PulsarConsumeTask.failureAttempts 在 maxConsumeAttempts=0（声明受支持）下只增不删（C5 无界面）

`PulsarConsumeTask` :68 的 HashMap 以 messageId 为键；`isPoisoned` :439-442 在 cap=0 时恒 false → `runFailureEndpoint` 永不触发 → 移除仅剩成功路径 :358。负 ack 后消息持续轮转，每条曾失败过的消息永久留一键 → 长跑无界。F3 同族枚举：`KafkaConsumeTask` :94 同构 map，但失败路径 seek-back（:416）将重试收敛于同一 offset 键，增长有界（每卡点位一条，成功/endpoint 即删）——同模式不同结局，修复时应一并核对。建议 cap=0 时为 map 设上限或改用 LRU。

### [CL-018] Debezium engine.run() 长期独占共享 globalWorker 线程，引擎数量与池容量无耦合（C4 线程池占用面）

`DebeziumEngineWrapper.start` :159 将 `engine.run()`（阻塞至连接器停止，天级时长）提交给 `GlobalExecutors.globalWorker()`——平台共享池，默认 maxPoolSize=30（`GlobalExecutors.java:85-96`、`CommonConfigs.java:26-27`）。每个 CDC 连接器整个生命周期占一条 worker 线程；多连接器部署逼近 30 时，平台其他 globalWorker 任务（跨模块共用）排队饥饿，且无任何预警/容量耦合。stop 后线程释放，非泄漏，属占用/容量面，故不入 C6 编号。建议：引擎改用专用单线程 executor（同 Kafka/Pulsar consume 线程的 NamedThreadFactory 惯例），或至少在启动时对"引擎数 vs 池容量"做一次校验告警。

## 维度复核结论（WI 4.1 独立复核，2026-10-20）

复核人（独立子代理，未参与初审/深挖，与 C1/C3 复核人互异）对 C6 全部 7 条（第 1 轮 6 条 + 第 2 轮 1 条）逐条回码验证：`LocalMessageService.java` 全文、`DebeziumMessageSource.java` 全文、`DebeziumEngineWrapper.java` 全文、`NopStreamOffsetBackingStore.java` 全文、`KafkaMessageService.java:120-519`、`PulsarMessageService.java:170-552`、`message-core-defaults.beans.xml` 全文逐点比对；跨源核验 `DebeziumCdcSourceFunction.java:220-298/:395-444`、`GlobalExecutors`/`CommonConfigs` 引用行；配置面与调用面主张以 grep 独立复现；严重度对照 `ai-dev/skills/concurrency-resource-audit-prompt.md` 校准表，并对照 `ai-dev/audits/cra-index.md`（WI 1.1–3.4 台账）与 WI 1.2/WI 2.5 历史审计做同案查重。

### 逐条判定表

| 条目 | 初判 | 判定 | 最终严重度 | 现状标注 | 关键复核依据 |
|---|---|---|---|---|---|
| C6-001 | P2 | 保留 | P2 | 新发现 | 只建不关+beans 无 destroy-method 回码；修复建议的 globalTimer 项不采（WI 2.1 C4-001 已立案共享定时器病），改专用可销毁 executor |
| C6-002 | P1 | 保留 | P1 | 新发现 | put-then-start 不回滚成立；子项复核收窄触发窗（幽灵投递须后续启动成功；条目数受失败重试约束）+主路径结构性免疫（source 每任务新建）——P1 依 F2 半个成功校准行维持，嵌入方暴露面权重与 C3-002 同口径留 7.1 |
| C6-003 | P2 | 保留 | P2 | 新发现（理论风险，未具体化标注维持） | 构造后无守卫段 :417/:428/:430 + Pulsar :494-551 对照形态回码；降档口径适用正确 |
| C6-004 | P2 | 保留 | P2 | 新发现 | stop 无 clearConnector+stale-resume 链回码；三面分层 C6 侧成立，与 C5-001 同 diff |
| C6-005 | P3 | 保留（吸收 C1-011 主登记） | P3 | 新发现 | 三缺口+(d) 毒化窗回码；C1-011 并入成立，修复吸收其 start 失败复位子项 |
| C6-006 | P2 | 保留 | P2 | 新发现 | action 键注销错位+WI23 同族未修成立；设计 §3.9 禁令强化立项依据 |
| C6-007 | P3 | 保留 | P3 | 新发现 | engine.close 全类唯一调用点在 stop（grep 复现）、worker catch 无回收回码；「很可能」信心自评恰当（残余面依赖第三方 run() 自清理边界） |

**判定统计**：保留 7（其中 1 条为主登记归并：C6-005 吸收 C1-011）、降级 0、驳回 0。
**最终严重度分布**：P1×1 / P2×4 / P3×2（与初审+深挖合计一致，无校准变动）。
**现状标注分布**：新发现×7（本单元无前序 CRA 覆盖；WI 1.2 MA-06/MA-07 为 wrapper 的 nop-stream 使用面双轨，不构成前案；WI20/WI26/WI29/WI8 既有修复在位且未覆盖本批任一条目）。

### 跨单元对照（cra-index P1 台账逐条比对）

1. **REGISTRY 三面分层表态（C6 侧）**：成立——C6-004 承接「清理调用点缺失 + stale-resume 语义」面，与 C1-002（并发摘除竞态）、C5-001（内存演化）、WI 2.5 消费面互斥无重复；C5-001/C6-004 同根同主修复，须同 diff（契约取舍一次裁决，见 C5 卷对照 1）。
2. **C1-011→C6-005 归并核对**：成立。C1-011（startRequested 先置位于可失败构建、失败后实例砖化、误导性 WARN）与 C6-005(c)（start-after-stop 静默 no-op）为同文件同根的一次性状态机面，C6-005 为主登记、修复范围扩入「start 失败复位 startRequested」；WI 1.2 C3-014/MA-07（停止契约，nop-stream 使用面）双轨互补，非重复。
3. **C6-002 与同单元 C3-003/CL-019 同站点三面**：C3-003（竞态交错 A/B，已裁 P2 主登记，吸收 C1-006）+ C6-002（顺序错误路径 put-then-start，P1）+ CL-019（C8 卷 overlay 同点登记）——三面机制互异（竞态 vs 确定性错误路径 vs 订阅/取消竞窗），边界成立；修复同站点归一个 diff 批次，合并裁定三面归一时保留 C6-002 的独立 P1 判级与触发窗收窄注记。
4. **其余各条**在 WI 1.1–3.4 台账无同站点/同案命中：Local executor 生命周期、Kafka doSubscribe 守卫缺口、action 键注销、引擎死亡回收均为本单元独有面（WI 1.5 MA-04/WI 2.1 C4-001 为 globalTimer 设施面——本卷 C6-001 修复建议已据此修正）。

## 最终保留项

- **保留工单 7 条**（零驳回、零降级）：P1×1（C6-002）进入 Phase 8 批量修复面；P2×4/P3×2 按 P2 裁决台账流程处置。
- **同 diff 修复批建议**：① REGISTRY 生命周期族——C6-004 + C5-001（+ C1-002 条件移除）；② DebeziumMessageSource subscribe 站点族——C6-002 + C3-003（+ CL-019）：put 移至启动成功后 + subscribe/cancel 锁内复合化一次落地；③ DebeziumEngineWrapper 生命周期族——C6-005（吸收 C1-011）+ C6-007：quiesce/join + 死亡路径 close + start 失败复位同批；④ Local executor 族——C6-001 + C5-003 + C8-003；⑤ C6-003（Kafka doSubscribe 外层 catch，与 C1-007 同段协调）、C6-006（WI23 同法注销身份）各自独立小 diff。
