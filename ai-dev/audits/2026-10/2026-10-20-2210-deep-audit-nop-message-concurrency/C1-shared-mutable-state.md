# C1 共享可变状态与可见性 — nop-message（WI 4.1）

- 审计维度：C1（共享可变状态与可见性）
- 批次：单批次初审（4 源码根全覆盖，逐文件审读）
- 日期：2026-10-20
- 盲审声明：本条目撰写前未读取 `ai-dev/audits/**`、`ai-dev/plans/**`、`ai-dev/bugs/**`、`ai-dev/lessons/**`、`ai-dev/backlog/**`，未查 git 修复历史。允许面内仅读 `docs-for-ai/**`（owner doc `docs-for-ai/03-modules/nop-message.md`）、源码与 `_tmp/cra-baseline-nop-message.txt`。跨模块引用的 `nop-stream/nop-stream-connector-debezium/DebeziumCdcSourceFunction.java`、`nop-sys/nop-sys-dao/.../SysDaoMessageService.java` 仅作为**静态注册表/内部集合的生产调用点证据**读取，不作为发现对象。

## 审计范围与口径

### 覆盖声明（单批次 4 源码根，手写口径，`_` 前缀文件不在对象内）

| 源码根 | 文件数 | 行数 | 关键对象 |
|---|---|---|---|
| `nop-message/nop-message-core/src/main/java` | 7 | 723 | LocalMessageService（consumers CHM / 惰性 ScheduledExecutor / volatile 标志）、ReflectionMessageSubscriptionRegistrar（subscriptions 注册表）、ChannelBacklogProbe（纯接口，无状态）、MessageConsumeFailureBridge、MessageMetrics（不可变） |
| `nop-message/nop-message-kafka/src/main/java` | 7 | 1,955 | KafkaMessageService（producer 字段 / subscriptions 队列 / initialized volatile）、KafkaConsumeTask（active/suspended volatile + 消费线程封闭字段） |
| `nop-message/nop-message-pulsar/src/main/java` | 7 | 1,513 | PulsarMessageService（client / producers CHM / subscriptions 队列）、PulsarConsumeTask（active volatile + pendingBatch 线程封闭） |
| `nop-message/nop-message-debezium/src/main/java` | 10 | 1,949 | **NopStreamOffsetBackingStore.REGISTRY:59（static 注册表本体）**、DebeziumEngineWrapper（FAILURE_LISTENERS 静态表 + start/stop 状态机）、DebeziumMessageSource（subscriptions CHM） |

**codec 零源登记**：线上编解码入口（`KafkaHelper.encodeValue` / `PulsarHelper.encodeValue` → `MessageValueCodec`）实现位于 `nop-api-core`（SPI 模块），本批次 4 源码根内 codec 相关源文件为 **0**，无 C1 对象。

**基线复现（口径纪律）**：`_tmp/cra-baseline-nop-message.txt` C1a/C1b/C1c 人工逐条复核——
- C1a（static 非 final 字段）：全部命中项为 `static final Logger` / `static final` 常量 / 静态工具方法签名误报，**无非 final 可变 static 字段**；
- C1b（static final 可变集合）：共 6 处。4 处为 `EnumSet` 能力集（Local×2、Kafka×2、Pulsar×1 中对应项），声明后只读，校验器 `MessageOptionsValidator.check` 仅 `contains`（已核实），**非缺陷**（但 `getCapabilities()` 把可变 EnumSet 直接暴露给外部，未发现变更方，登记为 watch，见清单表）；真实可变注册表 **2 处**：`DebeziumEngineWrapper.FAILURE_LISTENERS:44`、`NopStreamOffsetBackingStore.REGISTRY:59`（本文件 C1-001 / C1-002）；
- C1c（ThreadLocal）：4 根 grep 均为空，**0 处**，人工复现一致。

**降档口径声明**：消息消费/投递为长驻运行期面，启动期单线程降档**不适用**于 send/poll/ack 路径；仅对纯 IoC 装配期字段（如 `ReflectionMessageSubscriptionRegistrar.subscriptions`，register 与 @PreDestroy 同为容器生命周期线程）按启动期口径降档接受，已在清单表标注。

### 共享可变状态清单表（字段 × 写者 × 读者 × 同步机制 × 判定）

| 载体 | 字段/对象 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| bean 实例字段 | LocalMessageService.consumers (CHM→COWList) | subscribe 线程 / cancel / clearConsumers / 经 getConsumers 的外部方（SysDao:617） | send 线程、redelivery 定时线程、getBroadcastTopics | CHM + COW | 迭代安全；cancel 裸 get 有 NPE 窗口 → C1-008 |
| bean 实例字段 | Subscription.suspended/cancelled（Local） | suspend/resume/cancel 调用线程 | send 线程、定时线程 | volatile | 正常 |
| bean 实例字段 | LocalMessageService.redeliveryExecutor | 首次 redeliveryScheduler 调用线程；测试 setter | 定时任务线程 | volatile + synchronized DCL | DCL 正确；executor 无关停路径 → CL-001 |
| bean 实例字段 | ReflectionMessageSubscriptionRegistrar.subscriptions | register()（容器启动线程） | destroy()（@PreDestroy） | 无（同容器生命周期线程） | 启动期降档接受 |
| bean 实例字段 | MessageConsumeFailureBridge 5 个配置字段 | IoC 装配 | submit 调用线程 | 装配 HB | 正常 |
| bean 实例字段 | KafkaMessageService.producer | doStart/doStop（生命周期线程） | sendAsync 请求线程 | initialized(volatile) + 裸字段 | 复合守卫双读 → C1-005 |
| bean 实例字段 | KafkaMessageService.initialized / metrics | doStart/doStop / setMetricRegistry | 各请求线程 | volatile | 正常 |
| bean 实例字段 | KafkaMessageService.subscriptions (CLQ) | doSubscribe add / doStop 迭代+clear / KafkaMessageSubscription.cancel remove | doStop | CLQ 原子 + 无复合互斥 | add-after-clear 泄漏 → C1-007 |
| bean 实例字段 | KafkaMessageSubscription.cancelled / suspended | 任意调用线程 | 任意 | AtomicBoolean / volatile | 正常 |
| 线程封闭 | KafkaConsumeTask.suspendedApplied / seekToTimeApplied / lastFailure* / failureAttempts(HashMap) / executor / pollTimeoutMs | 消费线程（runTask 全路径核实） | 消费线程 | 线程封闭 + start 前 volatile active 写建立 HB | 正常（清单核实无跨线程触点） |
| bean 实例字段 | PulsarMessageService.client | doStart/doStop（生命周期线程） | getProducer/newConsumerBuilder（请求线程） | 无 volatile、无统一守卫 | 守卫次序错位 → C1-003；TOCTOU 泄漏 → C1-004 |
| bean 实例字段 | PulsarMessageService.producers / topicSchemas (CHM) | getProducer computeIfAbsent / setTopicSchemas 装配 | send 线程、doStop | CHM 原子，复合操作与 doStop 无互斥 | 单操作正常；销毁窗口 → C1-004 |
| bean 实例字段 | PulsarMessageSubscription.suspended | suspend/resume 调用线程 | isSuspended | volatile | 正常；跨线程 pause 模式分歧 → C1-009 |
| 线程封闭 | PulsarConsumeTask.pendingBatch / failureAttempts / lastFailure* / receiveTimeoutMs | 消费线程（javadoc 显式声明 + 调用点核实） | 消费线程 | 线程封闭 | 正常 |
| static 注册表 | DebeziumEngineWrapper.FAILURE_LISTENERS (CHM) | register/unregister（生产方为 DebeziumCdcSourceFunction run/finally 线程） | notifyEngineFailure（engine worker 线程） | CHM 原子；replace+无条件 remove 复合协议无世代校验 | 丢失更新 → **C1-001 (P1)** |
| static 注册表 | NopStreamOffsetBackingStore.REGISTRY (CHM) | forConnector/configure/ensureBound computeIfAbsent；clearConnector remove（唯一生产调用点 nop-stream first-run） | 各实例 get/set | CHM 原子；生命周期无配对释放 | 无界账本 + 摘除竞态 → C1-002 |
| static 注册表 | 4 处 EnumSet CAPABILITIES/WARN_ONCE | 无（类初始化） | getCapabilities()/校验器 | 事实只读 | 非缺陷；getCapabilities 暴露可变集合，watch |
| 实例 CHM | DebeziumMessageSource.subscriptions | subscribe put（非同步段）/ cancel remove / stop clear | dispatchEvent（engine worker 线程） | CHM 原子；put 与 stop.clear 无互斥 | 订阅被 clear 吞没 → C1-006；cancelReason 可见性 → C1-010 |
| 实例 AtomicBoolean | DebeziumEngineWrapper.running/startRequested/stopped | start/stop（synchronized this）/ engine worker | isRunning/守卫 | AtomicBoolean + synchronized | 原子性正常；无复位路径 → C1-011 |
| 实例 volatile | NopStreamOffsetBackingStore.data/connectorName/started | forConnector/configure/ensureBound/start/stop | get/set/getOffsets（engine worker 与 stream checkpoint 线程共享同一 data map） | volatile 引用 + CHM | 单 key 原子；跨 key 快照撕裂为 at-least-once 良性（重放冗余），登记不计缺陷 |
| ThreadLocal | — | — | — | — | 四根 0 处，无对象 |

## 发现

### [C1-001] FAILURE_LISTENERS 注册表 replace+无条件 remove 的丢失更新——引擎终局失败监听被交叠注销吞没

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:44,52-71,79-90`（注册表本体与协议；生产注册/注销对：`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:248,298`，作为触发链证据）
- **证据片段**:
  ```java
  // DebeziumEngineWrapper.java
  private static final Map<String, Consumer<Throwable>> FAILURE_LISTENERS = new ConcurrentHashMap<>();
  public static void registerFailureListener(String connectorName, Consumer<Throwable> listener) {
      ...
      FAILURE_LISTENERS.put(connectorName, listener);          // 61: 无条件替换
  }
  public static void unregisterFailureListener(String connectorName) {
      if (connectorName != null) {
          FAILURE_LISTENERS.remove(connectorName);              // 69: 无条件移除
      }
  }
  ```
- **严重程度**: P1
- **并发场景**: nop-stream 作业 failover/restart 时，同名 connector 的新旧两个 task 的 run() 线程交叠（旧 task 的 `cancel()` 不 join 其 run() 线程，run() 的 finally 异步执行）；外加 Debezium engine 全局 worker 线程在引擎死亡时调用 `notifyEngineFailure`。
- **触发时序**: T1 = 旧 task 的 run 线程（已注册 L1，正被取消，进入 finally 但尚未执行 :298）→ T2 = 新 task 的 run 线程执行 :248 `registerFailureListener(name, L2)`，put 无条件替换 L1 → T1 执行 :298 `unregisterFailureListener(name)`，remove 无条件移除——**把 T2 刚注册的 L2 一并删掉** → T2 的 engine `run()` 抛出（或 CompletionCallback success=false）→ wrapper :168/:234 `notifyEngineFailure` 取到 null → 仅 ERROR 日志 → T2 在 :283-290 的 1s 轮询等待循环永远等不到 countDown，task 保持 RUNNING 而 CDC 流已死——正是该监听机制（R5-CON-03 注释自述）要消灭的失效模式被注销竞态复活。
- **现状**: 注册表以 connector name 单键寻址，`put` 语义为"替换任一前任"，`remove` 无条件移除现任；无世代号/engine 实例标识、无条件移除（`remove(key, listener)` 两参形态）、无 putIfAbsent 保护。API 契约注释要求"owners must deregister"，但同键两个 owner 交叠时注销无归属判定。
- **风险**: 长驻流作业的静默死亡（不抛错、不产出、不失败），运维只能从 ERROR 日志发现；这正是 P1 校准中"异步链异常被静默吞掉导致状态不一致"的形态。
- **建议**: 注册表键升维为 `(connectorName, engine/owner 实例标识)`；或注销改两参条件 `FAILURE_LISTENERS.remove(name, registeredListener)`（持有注册时返回的引用做同一性校验）；注册返回句柄供注销。参照平台 `IResourceLockManager` 的按标识持锁/释放协议。
- **失效面枚举**（F3 同族清扫）: 本模块三个 connector-name 键控注册表逐一登记——(1) `FAILURE_LISTENERS`（本条，缺陷）；(2) `NopStreamOffsetBackingStore.REGISTRY`——remove 型 `clearConnector` 同为无条件移除（关联 C1-002，当前唯一调用方以 clear→forConnector 顺序单线程规避，守卫缺失）；(3) `DebeziumMessageSource.subscriptions`——实例级 CHM，remove 以 consumer 引用为键，单实例内无跨 owner 替换方，**无此竞态**。注册/注销调用方清单：生产仅 `DebeziumCdcSourceFunction.run:248` / `finally:298` 一对；测试 `TestDebeziumEngineWrapperFailureListener`（自注册自注销，无交叠）、`TestDebeziumCdcSourceErrorPropagation:220`（直接 notify，无注册竞态面）。
- **信心水平**: 很可能（交叠前提依赖 nop-stream failover 的任务交叠语义；链路各步均有代码实据）
- **误报排除**: 非"ConcurrentHashMap 原语使用"类误报——CHM 单操作无误，缺陷在 replace + 无条件 remove 的**复合协议**缺世代校验（check-then-act 家族，F10 变体）；也非已声明边界——javadoc 未声明同键交叠语义。
- **复核状态**: 未复核
- 复核状态: 降级(P2)｜现状:新发现（回码证实 :44/:61/:69 与 DebeziumCdcSourceFunction:248/:283-290/:298 触发链；但 **WI 1.2 C3-006 已以同一缺陷立案并复核保留 P2（其 merge-adjudication 明言"本 campaign 唯一登记"，T1–T4 同序）**——按使用面（nop-stream 调用方）/本体面（本条，修复落点）双轨互补登记非重复工单，严重度对齐先案 P2、P1↔P2 分歧留 7.1。触发前提复核收窄：同实例 region-restart 被 runEntered CAS（:227-229，复位 :317 晚于 :298）时序保护，竞态需同 JVM 同名双实例（热重部署/测试矩阵））

### [C1-002] NopStreamOffsetBackingStore.REGISTRY 生命周期：无 stop 侧释放的无界账本 + remove 型 clear 的摘除竞态

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java:59-60,81-90,101-103,105-111,220-232`
- **证据片段**:
  ```java
  private static final ConcurrentHashMap<String, ConcurrentHashMap<ByteBuffer, ByteBuffer>> REGISTRY =
          new ConcurrentHashMap<>();                                  // 59-60
  public static void clearConnector(String connectorName) {
      REGISTRY.remove(connectorName);                                 // 102: 无条件摘除
  }
  private void ensureBound() {
      if (data == null) { ...
          data = REGISTRY.computeIfAbsent(name, k -> new ConcurrentHashMap<>());  // 230
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: JVM 内所有使用过 `NopStreamOffsetBackingStore` 的引擎实例（Debezium engine worker 线程、nop-stream checkpoint 线程）与 first-run 清理线程共享 static REGISTRY；条目建立点三处（`forConnector:85`、`configure:109`、`ensureBound:230`），删除点仅 `clearConnector:101-102`。
- **触发时序**: (a) **无界账本**：无需并发——JVM 生命周期内每个出现过的 connector name 留下一条目；`clearConnector` 本模块零调用，唯一生产调用点是跨模块的 `DebeziumCdcSourceFunction.newFreshOffsetStore`（nop-stream :440-444）且仅 first-run 路径触发。经 restore 路径（`initializeState` 有 checkpoint 分支 :431）或直接使用 `DebeziumMessageSource`（不经过 nop-stream）创建的条目**永不释放**——engine `stop()` / source `stop()` / wrapper `stop()` 均不清 REGISTRY。增长上界 = JVM 生命周期内不同 connector name 基数，单条目数据极小（若干分区的 offset），故 P2 而非 P1。(b) **摘除竞态**：T1 = 活引擎实例（其 `data` volatile 已绑定 map M）持续 `set():170` 写 offset → T2 = `clearConnector(name)` 执行 `REGISTRY.remove` → M 成为孤儿但 T1 照常写入，**写入静默丢失**；后续任何 `forConnector/configure` computeIfAbsent 建新 map，新旧实例从此分裂到两个 map。当前唯一生产调用方以 `clear→forConnector` 顺序在同一线程执行（nop-stream :442-443）规避了自伤，但 API 本身无在用守卫/世代校验，属"守卫缺失、可绕过"。
- **现状**: setOffsets 的 REPLACE 语义（:199 clear+put）若在引擎运行中被调用，也会把引擎新写入回滚为 restore 值；当前调用时序（initializeState 先于 engine start）未触发，列为同族登记。
- **风险**: 长驻 JVM（多作业复用进程）下注册表只增不删（F4 家族）；改名/重建 connector 的部署模式放大基数。摘除竞态一旦被未来调用方（如管理端"重置 offset"功能）触发，表现为 CDC 静默回到种子位（重复读）或 offset 分裂，无报错。
- **建议**: 为 REGISTRY 增加与引擎实例绑定的释放路径（wrapper.stop → 按 (name, store 实例) 引用计数递减，归零才 remove）；`clearConnector` 语义改为条件移除或引入 epoch，文档化"活实例绑定期间摘除"为类型化错误。owner doc `docs-for-ai/03-modules/nop-message.md` 未记载 REGISTRY 生命周期契约，建议补记。
- **失效面枚举**（F3 同族清扫）: 建桶点 ×3——`forConnector:85`（无配对释放）、`configure:109`（同）、`ensureBound:230`（同）；删除点 ×1——`clearConnector:102`（无条件）；`setOffsets:199` clear+put 复合写（时序保护，登记）；对照同文件快照读 `getOffsets:211-218` 弱一致迭代（跨 key 撕裂在 at-least-once 下退化为重放，良性，不计）。nop-message 内无其他 static offset/状态账本。
- **信心水平**: 确定（清理缺失面）/ 很可能（摘除竞态为守卫缺失类，当前调用序未触发）
- **误报排除**: 非"有界声明存在的缓存"——`REGISTRY` 无容量声明、无淘汰；owner doc 亦无规模包络声明可援引。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（三面分层表态成立——本条主面收窄为「摘除竞态/守卫缺失」：无界账本增长事实归 C5-001 内存面、清理调用点缺失归 C6-004、消费方语义后果归 WI 2.5（C3-008 键错位/CL-010 watch/C1-005 同名双引擎 P1），四方无重复工单；唯一生产调用序 clear→forConnector 同线程（DebeziumCdcSourceFunction.java:442-443）回码证实）

### [C1-003] PulsarMessageService.doSubscribe 的 checkIsActive 位于首次 client 解引用之后——宣称的类型化拒绝路径实际 NPE

- **文件**: `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java:404-406,426-434`
- **证据片段**:
  ```java
  protected ConsumerBuilder<?> newConsumerBuilder(Schema<?> schema) {
      return client.newConsumer(schema);                 // 405: client==null → NPE
  }
  ...
  IMessageSubscription doSubscribe(MessageSubscriptionConfig subConfig) {
      ...
      ConsumerBuilder<?> builder = newConsumerBuilder(schema);   // 431: 先解引用
      builder.topic(subConfig.getTopic());
      checkIsActive(); // WI29: destroy-then-subscribe surfaces typed, not NPE (F-30)  // 434: 守卫在后
  ```
- **严重程度**: P2
- **并发场景**: 生命周期线程（doStart/doStop，`doStop:195` 置 `client = null`）与 subscribe 调用线程；包括纯顺序场景（未启动即 subscribe）与交叠场景（stop 进行中 subscribe）。
- **触发时序**: 服务未启动或已 destroy（`client == null`）→ 调用方 `subscribe(...)` → `doSubscribe:431` 先执行 `newConsumerBuilder` → `client.newConsumer(schema)` NPE → :434 的 `checkIsActive()` **永远执行不到**。确定性复现，无需交错。与 :434 行内注释宣称的 "destroy-then-subscribe surfaces typed, not NPE (F-30 subscribe path)" 直接矛盾：守卫在首次解引用之后，等于没有守住该路径。
- **现状**: `buildProducer`（:227）在创建 producer 前有 `client == null` 的类型化守卫（send 路径 OK），subscribe 路径的守卫次序错位是唯一漏点。
- **风险**: 停机窗口/装配错误场景下，调用方收到裸 NPE 而非 `ERR_SERVICE_NOT_ACTIVE` 类型化错误；异常处理按 message-service SPI 契约难以分支处理，且 NPE 丢失"哪个服务未激活"的语义。
- **建议**: 将 `checkIsActive()` 提至 :431 之前（与 Kafka doSubscribe 的"先校验后创建"注释意图对齐——Kafka :397-403 对 seekToMessage 的校验即位于 consumer 构造之前）；或 `newConsumerBuilder` 内做 client null 类型化检查。
- **失效面枚举**（F3 同族清扫）: 三个后端 subscribe 入口逐一登记——(1) Pulsar `doSubscribe:426`：守卫存在但次序错位（本条）；(2) Kafka `doSubscribe:390-435`：**完全没有**生命周期守卫（升级为独立发现 C1-007）；(3) Local `subscribe:163`：无守卫但无外部资源可泄（in-JVM，退化为纯校验问题，不立项）。send 入口：Kafka `getProducer:236`（initialized 守卫，见 C1-005）、Pulsar `buildProducer:227`（守卫在位）、Local 无。
- **信心水平**: 确定
- **误报排除**: 非低频路径粗锁类误报——这是声明过的错误处理契约（F-30/WI29 注释）与实际次序不符，属守卫失效而非优雅性；触发面包括所有"destroy 后 subscribe"与"装配顺序错误"场景。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（:431 newConsumerBuilder 先于 :434 checkIsActive 实测确认，F-30 行内注释与实际次序矛盾成立；buildProducer:227 send 侧守卫在位的对照成立）

### [C1-004] Pulsar producer 缓存的 check-then-act 与 doStop 交叠——销毁后残留孤儿 producer（连接泄漏）

- **文件**: `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java:210-219,221-245,180-196`
- **证据片段**:
  ```java
  Producer<?> buildProducer(String topic, Schema<?> schema) {
      if (client == null) { throw ...ERR_PULSAR_SERVICE_NOT_ACTIVE...; }   // 227: TOCTOU 检查
      ...
      return builder.create();                                             // 238
  }
  Producer<?> getProducer(String topic) {
      Producer<?> producer = producers.get(topic);
      if (producer != null) return producer;
      ...
      return producers.computeIfAbsent(topic, k -> buildProducer(k, schema));  // 218
  }
  // doStop: 180-187 迭代关闭 + producers.clear()；189-195 client.close() + client = null
  ```
- **严重程度**: P2
- **并发场景**: sendAsync 请求线程（getProducer → computeIfAbsent）与生命周期线程（doStop：迭代关闭 producers → clear → client.close → client=null）交叠。
- **触发时序**: T1 sendAsync 线程进入 `buildProducer`，:227 的 `client == null` 检查通过（client 尚非 null）→ T2 doStop 线程完成 :187 `producers.clear()`，执行到 :191 `client.close()` 之前 → T1 :232 `client.newProducer(...)` 在 close 生效前成功（PulsarClientImpl 状态检查先于 close 到达）→ T1 :218 `computeIfAbsent` 在 T2 的 clear **之后**写入 → 该 producer 条目存留于已销毁服务的 map 中，无人再调用 `producer.close()` → broker 连接/producer 句柄泄漏至 JVM 退出。旁注：`allowRestart=true`（LifeCycleSupport 默认）时若服务重启，下一轮 doStop 的迭代会顺带关闭它（自愈窗口）；服务不再重启则永久泄漏。
- **现状**: F-30 注释（:222-226）自认"narrow destroy window ... underlying client error surfaces adapted"，但只声明了**错误面**（client 已关时报类型化错误），未声明**泄漏面**（新建成功但错过 clear 的条目无 close 调用方）。
- **风险**: 频繁重建服务的场景（测试矩阵、热切换 backend）累积孤儿 producer；每个孤儿持有 broker 会话与内存缓冲。
- **建议**: doStop 中 clear 后对 map 做二次排空（clear 前后各迭代一轮），或 getProducer 的 computeIfAbsent 与生命周期共享一把服务级锁；长期看把 producers 纳入 `LifeCycleSupport` 状态机守卫（stop 后拒绝新装载，见 C1-007 建议）。
- **失效面枚举**（F3 同族清扫）: producer/资源缓存同族——(1) Pulsar `producers` CHM + computeIfAbsent（本条，缺陷）；(2) Kafka 单 `producer` 字段：doStop :153-162 有 close，无按 topic 缓存族，同窗口下仅错误面问题（C1-005），**无泄漏窗口**；(3) Local：无 producer 资源；(4) `getProducer` 的 get→computeIfAbsent 复合读本身 benign（computeIfAbsent 内部重查，非缺陷）。
- **信心水平**: 很可能（窗口窄但每一步均有代码实据，可具体化）
- **误报排除**: 非平台基础设施问题——竞态协议全部在本模块代码内；非已声明边界——声明只覆盖错误面。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（TOCTOU 窗口 :227→:232 与 doStop :187 clear→:191 client.close 交叠实测成立；allowRestart 自愈窗口与每事件有界泄漏核verified——P1 校准行「错误路径资源泄漏」虽字面相关，窗口窄+自愈+有界维持 P2）

### [C1-005] KafkaMessageService.getProducer 复合守卫对非 volatile 字段双读——销毁窗口返回 null 致 NPE 顶替类型化错误

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaMessageService.java:95,127,161-163,235-241,254,274,290-307`
- **证据片段**:
  ```java
  KafkaProducer<String, String> producer;                       // 95: 非 volatile
  KafkaProducer<String, String> getProducer() {
      if (!initialized || producer == null) {                   // 236: initialized(volatile) 与 producer 两次独立读
          throw error(KafkaErrors.ERR_KAFKA_PRODUCER_CREATE_FAILED,
                  "KafkaMessageService is not initialized");
      }
      return producer;                                          // 240: 第二次读取
  }
  // doStop: 157 producer.close(...); 161 producer = null; 163 initialized = false
  ```
- **严重程度**: P2
- **并发场景**: sendAsync 请求线程（getProducer + prod.send）与生命周期线程（doStop：close → producer=null → initialized=false）交叠。
- **触发时序**: T1 请求线程执行 :236 —— `initialized`（volatile）读到 true、`producer` 第一次读到非 null，两查通过 → T2 生命周期线程执行 :161 `producer = null` → T1 :240 `return producer` **第二次读取**得到 null → `sendAsync:254` 的 `prod` 为 null → :274 `prod.send(...)` NPE → :290 catch 将 NPE 包成 `ERR_KAFKA_SEND_FAILED`（cause=NPE）——本应命中 :236-239 的"not initialized"类型化错误语义。
- **现状**: init 方向的发布安全成立（doStart 中 `producer = ...` 先于 `initialized = true` volatile 写，读侧 initialized 获取读先行，安全发布 OK）；缺陷仅在 stop 方向的复合检查非原子且字段双读。
- **风险**: 停机窗口内错误码失真（NPE 链路干扰上层按错误码分支的重试/告警逻辑）；无数据损坏，故 P2。
- **建议**: `getProducer` 单次读入局部变量再判空返回（`KafkaProducer p = producer; if (!initialized || p == null) throw ...; return p;`），并可顺势把 `producer` 声明为 volatile 以对称于 `initialized`。
- **失效面枚举**（F3 同族清扫）: 服务单例字段守卫族——(1) Kafka `getProducer`（本条）；(2) Pulsar `getClient():201-203` 裸返回字段、`buildProducer:227` 单点 null-check 后 :232 仍二次解引用 `client`——同款 TOCTOU，但 :239 catch 会把 NPE 包成 `ERR_PULSAR_PRODUCER_CREATE_FAILED`，无 NPE 逃逸（次序问题归 C1-003/C1-004 计）；(3) 两服务 `metrics` volatile 单写单读——正常；(4) Local 无对应物。
- **信心水平**: 确定（交错序列具体）
- **误报排除**: 非"看到共享字段就报线程不安全"——init 方向已按安全发布论证豁免；缺陷精确定位于 stop 方向的 check-then-act 双读。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（:236 双读→doStop:161 置 null→:240 二次读→sendAsync:274 NPE→:290 catch 包成 ERR_KAFKA_SEND_FAILED 全链实测成立；init 方向安全发布论证成立）

### [C1-006] DebeziumMessageSource.subscribe 的 put 位于同步段之外——末订阅者 cancel 触发的 stop().clear() 吞没并发新订阅

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:72-80,168-180,219-229,155-163`
- **证据片段**:
  ```java
  public ICancellable subscribe(Consumer<ChangeEvent> action) {
      Subscription subscription = new Subscription(action);
      subscriptions.put(action, subscription);        // 74: 不在 synchronized 段内
      startEngineIfNeeded();                          // 77: synchronized
      return subscription;
  }
  public synchronized void stop() {
      ...
      subscriptions.clear();                          // 179
  }
  // Subscription.cancel: 222 remove → 226 if (subscriptions.isEmpty()) stop();
  ```
- **严重程度**: P2
- **并发场景**: T1 = 新订阅者线程（subscribe）；T2 = 旧订阅者线程（最后一个旧订阅 `cancel` → 触发 synchronized `stop`）；后续 dispatchEvent 在 engine worker 线程执行。
- **触发时序**: T1 :74 put(sub1) 完成、尚未进入 :77 的同步方法 → T2 :222 remove 自身、:226 读 `isEmpty()`（此刻早于 sub1 的语义被 T2 计入，因为 T2 的读发生在其 synchronized 段之外……准确说 T2 的 isEmpty 读读到 {sub1 已入 map} 之前的旧快照或恰在 put 后：关键交错为 T2 isEmpty 判空成立）→ T2 进入 synchronized `stop()`：engineWrapper.stop、started=false、:179 `clear()` **抹掉 sub1** → T1 进入 synchronized `startEngineIfNeeded`：`started==false` → 新建 wrapper 并启动引擎 → T1 返回 sub1。此后 sub1 已不在分发 map，`dispatchEvent:156` 永远不会调用它——**订阅者静默收不到任何 CDC 事件**；引擎以 0 订阅者空转，直到 sub1.cancel() 命中 isEmpty→stop 兜底，若订阅方不 cancel 则引擎长期空转。
- **现状**: `startEngineIfNeeded` 与 `stop` 各自 synchronized，但 `subscribe` 的 put 段不在任何锁内；`Subscription.cancel` 的 remove→isEmpty→stop 也非原子。
- **风险**: CDC 订阅静默失效（无错误、无日志），属"半个成功"形态：subscribe 返回了活的 ICancellable，语义上未生效。
- **建议**: subscribe 整体纳入与 stop 相同的锁（put + startEngineIfNeeded 同段）；cancel 的 remove→isEmpty→stop 段同样入锁；或 isEmpty 判定改用 `subscriptions.remove(action) 后在锁内复查`。
- **失效面枚举**（F3 同族清扫）: subscribe/stop 交叠族——(1) DebeziumMessageSource（本条）；(2) Kafka `doSubscribe:433 add` vs `doStop:144-151 迭代+clear`（同族，升级为 C1-007，资源后果更重）；(3) Pulsar `doSubscribe:535 add` vs `doStop:171-178`：有 `checkIsActive`（虽次序错位）使 destroy 后 subscribe 先被拒，交叠窗口内 add-after-clear 仍可能但 consumer/executor 会在 :540-549 之外无人清理——与 Kafka 同款残余窗口，登记为 C1-007 的同族兄弟（Pulsar 侧）；(4) Local `subscribe:187-189` vs `clearConsumers:54-56`：COW add 原子，但 cancel 侧有 NPE 窗口（C1-008）。
- **信心水平**: 确定（交错具体；唯一含糊点在 T2 的 isEmpty 读与 T1 put 的先后，两种先后均产出失效终态：先后→本条主时序；后先→sub1 存活但 T2 的 stop 兜底不触发，引擎带旧状态继续）
- **误报排除**: 非纯理论竞态——触发链由公开 API 的文档语义（"最后一个订阅者取消后停止引擎"，:225 注释）自证为设计内路径，两个设计内路径交叠即可达。
- **复核状态**: 未复核
- 复核状态: 保留(P2，并入 C3-003 主登记)｜现状:新发现（与本单元 C3-003 交错 A 同根同站点（DebeziumMessageSource:72-80/:168-180），C3-003 另含交错 B（stop 后引擎复活）为超集；同站点跨维度重复计数，合并裁定应二并一——本条保留为 C1 面证据（cancel 的 remove→isEmpty→stop 非原子细节），修复随 C3-003）

### [C1-007] KafkaMessageService.doSubscribe 无生命周期守卫 + add-after-clear——destroy 交叠窗口订阅（consumer+executor）泄漏

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaMessageService.java:96,143-151,390-435`
- **证据片段**:
  ```java
  private Queue<KafkaMessageSubscription> subscriptions = new ConcurrentLinkedQueue<>();  // 96
  protected void doStop() {
      for (KafkaMessageSubscription subscription : subscriptions) { ... cancel ... }      // 144-150
      subscriptions.clear();                                                              // 151
      ...
  }
  IMessageSubscription doSubscribe(MessageSubscriptionConfig subConfig) {
      // 390-432: 全程无 checkIsActive / isActive 检查
      ...
      subscriptions.add(subscription);                                                    // 433
  ```
- **严重程度**: P2
- **并发场景**: T1 = subscribe 调用线程（doSubscribe：创建 KafkaConsumer → 建单线程 executor → task.start → add）；T2 = 生命周期线程（doStop：迭代 cancel → clear）。
- **触发时序**: T1 doSubscribe 执行到 :428-432（consumer 已建、executor 已建、poll 任务已 start，但 :433 的 add 尚未落地）→ T2 doStop :144 迭代（此刻队列中无 T1 的订阅）→ :151 clear() → T1 :433 add 在 clear 之后落地 → 服务 destroy 完成，而 T1 订阅的 **KafkaConsumer 网络 IO 线程 + 命名消费线程（daemon）存活**，直到订阅方显式 cancel。顺带的确定性场景：destroy 之后调用 subscribe 无任何拒绝（对比 Pulsar 有 checkIsActive），consumer 创建 + group subscribe 成功，仅 send 侧经 getProducer 报类型化错误——消费侧在"已销毁"服务上继续运行。
- **现状**: doStop 注释宣称 "Idempotent via the LifeCycleSupport stop state machine"——stop 侧幂等成立；subscribe 侧完全未纳入状态机。
- **风险**: F8 资源清理不对称（register/add 与 unregister/迭代+clear 无互斥也不拒绝并发装载）；销毁后残留消费组成员会持续 rebalance 干扰同组其他成员。
- **建议**: doSubscribe 入口加 `checkIsActive()`（对齐 Pulsar 的守卫意图并修正其次序问题）；`subscriptions` 的装载与迭代+clear 复合操作共享服务级锁，或 doStop 末尾对 clear 后新增条目补一轮 cancel（二次排空）。
- **失效面枚举**（F3 同族清扫）: 见 C1-006 的失效面——四后端逐一登记；本条与 Pulsar 侧（C1-006 失效面 (3)）构成 add-after-clear 同族双胞胎，Pulsar 因 checkIsActive 在位（尽管错位）窗口更窄。SysDao 包装 Local（生产使用方）无 broker 资源，不入族。
- **信心水平**: 确定
- **误报排除**: 非低频路径粗锁豁免范围——涉及长驻资源（消费组身份、网络线程）而非一次性对象；非已声明边界——注释只声明了 stop 侧幂等。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（doSubscribe:390-435 全程无生命周期守卫实测确认（对照 Pulsar :434 守卫在位虽错位）；:430 task.start→:433 add 与 doStop :144-151 迭代+clear 的 add-after-clear 泄漏链成立）

### [C1-008] LocalMessageService.Subscription.cancel 对 consumers.get(topic) 裸解引用——topic 条目被并发移除时 NPE；内部 map 经 getConsumers 外泄被生产代码变更

- **文件**: `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:52-60,74-81`
- **证据片段**:
  ```java
  public Map<String, List<Subscription>> getConsumers() {   // 58: 内部活引用直接外泄
      return consumers;
  }
  @Override
  public void cancel() {
      cancelled = true;
      ...
      consumers.get(topic).remove(this);                     // 80: 无 null 守卫
  }
  ```
- **严重程度**: P3
- **并发场景**: T1 = 订阅 cancel 线程；T2 = 执行 `clearConsumers()`（公开的契约 close 钩子，测试与 teardown 使用）或经 `getConsumers()` 外泄引用做移除的线程——**生产实证**：`SysDaoMessageService.DurableSubscription.cancel`（nop-sys :617）调用 `localService.getConsumers().remove(state.topic, new CopyOnWriteArrayList<>())` 变更 Local 内部 map。
- **触发时序**: T1 cancel():80 执行 `consumers.get(topic)` → T2 clearConsumers():55 `consumers.clear()`（或 SysDao 条件 remove 恰在 Local 侧其他订阅全部移除后命中）→ T1 get 返回 null → `.remove(this)` NPE，cancel 异常逃逸至调用方（`ReflectionMessageSubscriptionRegistrar.destroy:143-148` 会捕获记 ERROR，但 Kafka/Pulsar 契约测试驱动方与用户代码会收到未预期异常）。
- **现状**: 同族其余订阅实现（Kafka AtomicBoolean、Pulsar Cancellable、Debezium CHM remove）cancel 均有守卫；唯 Local 裸 get。条件 remove 的成功前提是列表为空，与 in-flight cancel 交叠时序上难以同时成立，故降为 teardown 窗口的 P3。
- **风险**: teardown 噪声异常；`getConsumers()` 外泄还构成跨模块隐式契约（SysDao 依赖"remove(topic, 空列表)"语义），Local 内部结构一旦改动即静默破坏 SysDao。
- **建议**: :80 改为 `List<Subscription> list = consumers.get(topic); if (list != null) list.remove(this);`；`getConsumers` 收窄为包私有或返回快照，SysDao 所需的"topic 是否仍有本地订阅"语义改由 Local 提供显式方法。
- **失效面枚举**（F3 同族清扫）: 本类迭代点逐一核对——`invokeMessageListener:200`（get 后判 null，安全）、`getBroadcastTopics/getNonBroadcastTopics:135-153`（keySet 弱一致迭代，安全）、`scheduleRedelivery:256-259`（get 后判 null，安全）——唯 :80 一处裸解引用。`clearConsumers` 与 redelivery 定时任务的交叠由 fire-time 复查（:256-260 判 null return）覆盖，安全。
- **信心水平**: 确定（NPE 路径成立；生产触发概率低故 P3）
- **误报排除**: 非测试专用代码豁免——`clearConsumers` 是主类公开 API（契约测试将其定义为 close 语义），SysDao 变更点在生产源码内。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现（SysDaoMessageService.java:617 生产变更点逐字实测确认（两参 remove 空列表语义）；:80 裸 get NPE 窗口成立，teardown 窗口概率低维持 P3；getConsumers 外泄的跨模块隐式契约面独立成立）

### [C1-009] Pulsar suspend/resume 由调用方线程直呼 consumer.pause/resume——偏离 Kafka WI20 已裁定的消费线程应用模式，暂停非即时生效

- **文件**: `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java:587,627-642`；对照 `KafkaMessageService.java:613-628` 与 `KafkaConsumeTask.java:85-87,248-275`
- **证据片段**:
  ```java
  // PulsarMessageSubscription
  public void suspend() {
      suspended = true;
      consumer.pause();              // 630: 调用方线程直接操作 consumer
  }
  public void resume() {
      consumer.resume();             // 640: 先恢复客户端，后清标志
      suspended = false;
  }
  // 对照 Kafka: suspend() { suspended = true; task.setSuspended(true); }  // 仅记录意图，
  // 消费线程在 runTask 轮首 applySuspendState() 差分应用 + rebalance 重放（WI20 F-26 注释）
  ```
- **严重程度**: P3
- **并发场景**: suspend/resume 调用方线程 vs 消费线程（阻塞在 `receive(1s)` 或 `pendingBatch.get(1s)`）；Kafka 侧对应裁定（:81-84 注释明确 "a cross-thread `consumer.pause()` call would race the poll loop"）。
- **触发时序**（效果时序，非内存竞态）: T1 调用方 suspend():630 `consumer.pause()` 返回（Pulsar 客户端为标志位型调用，无内存错误）→ 消费线程当前 in-flight 的 receive 轮与**已缓冲消息**仍会在 ≤1s 内投递给 handler——`suspend()` 返回后 handler 仍可能被调；resume 方向 :640 先 `consumer.resume()` 后 `suspended=false`，其间 `isSuspended()` 短暂报告 true 而客户端已恢复投递。消费侧无任何 fail-fast 守卫/状态镜像（Kafka 的 `suspendedApplied` 差分 + 轮首重放在 Pulsar 无对应物），调用方无法得知暂停何时真正生效。
- **现状**: 本模块未声明 Pulsar suspend 的生效时延边界（owner doc 无此行）；Kafka 侧同族问题已被 WI20 裁定修复，Pulsar 为 F3 修实例不修类别的残留面。
- **风险**: "suspend 后事件不再投递"的直觉契约在 ≤1s 窗口内不成立；对依赖 suspend 做排空/切换的调用方产生重复消费或时序误判。无内存安全问题，故 P3。
- **建议**: 对齐 Kafka 模式——PulsarConsumeTask 增加 volatile `suspended` 意图标志，消费线程在每轮 receive 前差分应用 pause/resume；或在 owner doc 补记 Pulsar suspend 的最终一致语义。
- **失效面枚举**（F3 同族清扫）: suspend 族全量——(1) Local `Subscription.suspend`：volatile 标志逐派发检查，生效点即检查点，正确；(2) Kafka：裁定模式，正确；(3) Pulsar：本条；(4) DebeziumMessageSource 无 suspend 概念（ICancellable 无 suspend），不入族。
- **信心水平**: 确定（模式差异与效果窗口均有代码实据；Pulsar 客户端内部标志的线程安全性按其实现假设，本条不依赖其出错）
- **误报排除**: 非"原语使用"误报——报告对象是两后端状态转移纪律的不一致与未声明的生效时延，非 pause 调用本身。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现（Pulsar :628-631/:639-642 调用方线程直呼 pause/resume vs Kafka :619-628/:248-275 意图标志+轮首差分应用模式对照实测成立（含 Kafka WI20 F-26 注释先例）；效果时延面非内存竞态，P3 校准正确）

### [C1-010] DebeziumMessageSource.Subscription.cancelReason 非 volatile，getCancelReason 单独读取无 happens-before

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:200-223`
- **证据片段**:
  ```java
  private volatile boolean cancelled = false;
  private String cancelReason;                       // 202: plain 字段
  ...
  public String getCancelReason() {
      return cancelReason;                           // 215-216: 无配套 volatile 读
  }
  public void cancel(String reason) {
      cancelled = true;                              // 220: volatile 写在后
      cancelReason = reason;                         // 221: plain 写
  ```
- **严重程度**: P3
- **并发场景**: cancel 调用线程 vs 任意线程的 `getCancelReason()` 读者。
- **触发时序**: T1 cancel: :220 `cancelled=true`（volatile 写）、:221 `cancelReason=reason`（plain 写，但位于 volatile 写**之后**——写序反置）→ T2 读者不经 `isCancelled()` 直接 `getCancelReason()`：无任何 HB 边，可能读到 null 或旧值；即便先读 `isCancelled()`，由于 T1 的 volatile 写在 cancelReason 写之后，"看到 cancelled==true 即看到 reason"的保障**不成立**（正确顺序应先写 reason 再写 cancelled）。
- **现状**: 单行次序缺陷；当前仓库内读者（nop-stream `DebeziumCdcSourceFunction` 未见 getCancelReason 调用）未受影响。
- **风险**: 未来调用方在错误报告路径读到的取消原因为空，误导排障；属可见性纪律缺口而非功能故障。
- **建议**: :220-221 两行交换写序（reason 先、cancelled 后），或将 cancelReason 声明为 volatile。
- **失效面枚举**（F3 同族清扫）: cancel-reason 同族——(1) 本条；(2) Local `Subscription` 无 reason 字段；(3) Kafka/Pulsar 订阅实现无 reason 字段；(4) `ICancellable` 其他实现（nop-commons Cancellable）为平台基础设施不重审。族内仅此一处。
- **信心水平**: 确定
- **误报排除**: 非理论化——字段为 public 接口 `getCancelReason()` 的返回源，跨线程读者契约明确。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现（:220 volatile 写在 :221 plain 写之前的写序反置实测确认——「见 cancelled==true 即见 reason」不成立的分析正确；全仓 grep 证实本实现 getCancelReason 零读者，风险面纯前瞻，P3）

### [C1-011] DebeziumEngineWrapper 一次性状态机无复位：startRequested 先于可失败构建置位，失败/停止后实例报废且以"already running"误导

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:96-101,135-180,195-215`
- **证据片段**:
  ```java
  private final AtomicBoolean startRequested = new AtomicBoolean(false);   // 100
  private final AtomicBoolean stopped = new AtomicBoolean(false);          // 101
  public synchronized void start() {
      if (running.get() || startRequested.get()) {
          LOG.warn("Debezium engine is already running");                   // 137-138: 静默返回
          return;
      }
      startRequested.set(true);                                             // 143: 先置位
      try {
          ...
          engine = DebeziumEngine.create(JsonByteArray.class) ... .build(); // 148-156: 可失败构建
  ```
- **严重程度**: P3
- **并发场景**: start/stop 均 synchronized(this)——原子性成立；问题是状态机**无复位转移**（startRequested/stopped 永不复位）与置位次序。
- **触发时序**: T1 第一次 start():143 置 startRequested=true → :148-156 `buildProperties`/`build` 抛出 → :175-178 包装 `ERR_DEBEZIUM_ENGINE_START_FAILED` 抛出，但 startRequested 仍为 true → 同一实例任何后续 start() 命中 :136 守卫 → WARN "already running"（实际从未 running）后**静默返回**——实例永久报废且无类型化拒绝。stop() 后同理不可重启。
- **现状**: 用户级恢复被 `DebeziumMessageSource.startEngineIfNeeded:143`（每次新建 wrapper）掩盖；直接使用 wrapper 的嵌入方（类的 public 契约）无恢复路径。:137 的 WARN 文案与实际状态（已请求启动/已停止）不符。
- **风险**: 嵌入方重试逻辑被静默吞掉（不抛错的 return）；`isRunning()` 无法区分"排队未启动/已停止/启动中"。
- **建议**: start 失败 catch 中复位 startRequested；start 对"已 stop"态改抛类型化错误（参照 `LifeCycleSupport` 的 `ERR_SERVICE_NOT_ALLOW_START_AFTER_STOP` 模式）；或直接让 wrapper 复用 `LifeCycleSupport` 状态机（模块内 Kafka/Pulsar 服务已是该模式）。
- **失效面枚举**（F3 同族清扫）: 一次性 AtomicBoolean 状态族——(1) 本条（无复位）；(2) `KafkaMessageSubscription.cancelled`（一次性 cancel 幂等语义，**正确**）；(3) `LifeCycleSupport.status`（CAS + allowRestart 复位回 CREATED，**正确对照**）；(4) `DebeziumMessageSource.started`（volatile + 新建 wrapper 策略，正确）。
- **信心水平**: 确定（失败路径报废为代码直证；"误导"影响为文档/可观测性判断）
- **误报排除**: 非并发原子性误报——报告的是状态机完整性（失败转移缺失）与静默返回语义，非 AtomicBoolean 用法。
- **复核状态**: 未复核
- 复核状态: 保留(P3，并入 C6-005 主登记)｜现状:新发现（:143 置位→:148-156 可失败构建→catch :175-179 不复位 startRequested 实测确认，失败后实例报废+「already running」误导成立；与本单元 C6-005（同文件 DebeziumEngineWrapper 一次性生命周期面，已按使用面校准 P3）同根同站点，合并裁定应并入，修复随 C6-005）

## 维度集外线索（overlay C2/C4/C7/C9）

### nop-message-CL-001（C4 资源生命周期）LocalMessageService 惰性 redelivery 调度器无关停路径

`LocalMessageService.java:274-295`：DCL 正确，但创建的单线程 ScheduledExecutorService（daemon）没有任何 stop/destroy 侧关闭路径——`LocalMessageService` 未实现 `ILifeCycle`，无 destroy-method；`setRedeliveryExecutorForTest:276` 是唯一替换/关停入口（测试专用）。服务实例在 IoC 中为长驻单例时无实际泄漏；但该类为 public、可多次实例化（契约测试即逐测新建），每实例懒创建一个常驻线程且不回收。建议补 destroy 钩子或改用平台 `StandardThreadPoolExecutor`（io.nop.commons.concurrent.executor）统一生命周期管理。

### nop-message-CL-002（C7 有界停止）Kafka ConsumeLater 延迟在消费线程内裸 sleep，wakeup 无效，取消时延受 5s 预算兜底

`KafkaConsumeTask.java:442-454`（deferAfterBatch 无上限分片 sleep，javadoc 自declared "No cap"）+ `KafkaConsumeTask.stop:236-239`（`wakeup()` 对 `Thread.sleep` 无效）+ `KafkaMessageService.cancel:578-601`（先 shutdown+awaitTermination(5s) 才 shutdownNow 强中断）。handler 返回超大 delay 时，消费线程被占住至多整个 delay（interruptible），取消路径的实际时延 = min(delay, 5s)+1s；Pulsar 侧同场景走 broker 侧 `reconsumeLaterAsync`（`PulsarConsumeTask.java:274`）不占线程——两后端停止时延语义不对称。建议 C7 维度评估 delay 上限与 `max.poll.interval.ms` 的联动（javadoc 已承认 kick 风险）。

### nop-message-CL-003（C7/C3 引擎启动可观测性）DebeziumEngineWrapper 借调 globalWorker 无排队可观测性，"排队未跑"与"已死亡"不可区分

`DebeziumEngineWrapper.java:159-173`：`GlobalExecutors.globalWorker().execute(...)` 提交后，`startRequested=true` 而 `running=false`；若 worker 队列饱和排队（或 RejectedExecutionException——该异常会被 :175 catch 包装为类型化启动失败，此路径 OK），引擎迟迟未 run 时 `isRunning()` 返回 false、无任何失败回调（`notifyEngineFailure` 只在 lambda 内触发）。消费方（如 `DebeziumCdcSourceFunction` 的 1s 轮询等待循环）只能依赖外部超时兜底。建议 C7 维度评估：globalWorker 饱和度对 CDC 启动时延的影响与"启动超时"回调缺口。

### nop-message-CL-004（C9 热路径/指标口径）sendPulsarAsync 在取消检查与实际发送之前递增 sent 指标，与 Kafka 口径不一致

`PulsarMessageService.java:252-254`：`metrics.incrementSent()` 位于方法第一行——cancel-token 预检拒绝（:255-262）与 producer 获取失败的发送也计入 `nop.message.pulsar.sent`；Kafka 侧 `KafkaMessageService.java:315` 的 `incrementSent()` 在 dispatch 之后（但同样不含 broker 确认语义）。两后端 sent/duration 指标口径不一致，容量观测与告警阈值不可比。建议 C9 维度统一计数点位（建议统一为"通过预检并提交底层发送"后）。

---
（本文件为 C1 维度单批次初审产出；发现条目均未复核，复核与合并裁定流程见 deep-audit-prompts.md「调度架构」。）

## 维度复核结论（WI 4.1 独立复核，2026-10-20）

复核人（独立子代理，未参与初审/深挖）对 12 条发现逐条回码验证：证据片段行号逐一比对、触发时序逐步推演、严重度对照 `ai-dev/skills/concurrency-resource-audit-prompt.md` 校准表，并对照 `ai-dev/audits/cra-index.md`（WI 1.1–3.4 台账）与 WI 1.2/WI 2.5 历史审计目录做同案查重。跨源证据核验点：`DebeziumCdcSourceFunction.java`（nop-stream-connector-debezium）:227-229/:248/:283-290/:298/:440-444、`SysDaoMessageService.java`（nop-sys-dao）:617、`MessageOptionsValidator.java`（nop-api-core）:83-85（EnumSet 只读 uses 声明的复核）。

### 逐条判定表

| 条目 | 初判 | 判定 | 最终严重度 | 现状标注 | 关键复核依据 |
|---|---|---|---|---|---|
| C1-001 | P1 | **降级（同案并入）** | P2 | 新发现 | 机制回码证实；WI 1.2 C3-006 已以同一缺陷立案（P2 复核保留、「本 campaign 唯一登记」）——使用面/本体面双轨互补非重复工单；触发前提收窄（同实例 region-restart 被 runEntered CAS 时序保护，需同 JVM 同名双实例）；严重度对齐先案，P1↔P2 分歧留 7.1 |
| C1-002 | P2 | 保留（主面收窄） | P2 | 新发现 | 摘除竞态/守卫缺失面成立；「无界账本」子面与 C5-001 重复陈述同一事实，收窄归 C5-001；清理调用点面归 C6-004；消费语义面归 WI 2.5——三面分层+WI 2.5 消费方面边界干净 |
| C1-003 | P2 | 保留 | P2 | 新发现 | :431 先于 :434 实测，F-30 注释矛盾成立 |
| C1-004 | P2 | 保留 | P2 | 新发现 | TOCTOU 泄漏链成立；自愈窗口+每事件有界，P1 不采 |
| C1-005 | P2 | 保留 | P2 | 新发现 | 双读 NPE→错误码失真全链成立 |
| C1-006 | P2 | 保留（并入 C3-003 主登记） | P2 | 新发现 | 与 C3-003 交错 A 同根同站点，C3-003 为超集；合并归一 |
| C1-007 | P2 | 保留 | P2 | 新发现 | 无守卫+add-after-clear 成立 |
| C1-008 | P3 | 保留 | P3 | 新发现 | SysDao:617 生产变更点逐字实测 |
| C1-009 | P3 | 保留 | P3 | 新发现 | 模式分歧+效果时延面成立 |
| C1-010 | P3 | 保留 | P3 | 新发现 | 写序反置成立；全仓零读者 grep 复现 |
| C1-011 | P3 | 保留（并入 C6-005 主登记） | P3 | 新发现 | 与 C6-005 同文件同根一次性状态机面 |
| C1-012 | P2 | 保留 | P2（理论面） | 新发现 | 活引用外泄+checkpoint 放大面实测；运行期写者零命中 grep 复现（addExtraProperty/setExtraProps 主代码零调用），理论自评符合降档口径 |

**判定统计**：保留 11（其中 2 条标注并入主登记）、降级 1（C1-001 P1→P2）、驳回 0。
**最终严重度分布**：P1×0 / **P2×8** / P3×4（初审 P1×1/P2×7/P3×4，唯一变动为 C1-001 降级）。
**现状标注分布**：新发现×12（本单元无前序 CRA 覆盖；nop-stream 侧 R5-CON-02/03 修复在位但不覆盖本批任一条目；WI 1.2 C3-006 为跨单元同案双轨，非本单元前案，不构成「已知未修复」标注）。

### 跨单元对照（cra-index P1 台账逐条比对）

1. **C1-001 ↔ WI 1.2 C3-006（同案双轨）**：WI 1.2 C3-async-chains.md C3-006（P2 复核保留）登记的是同一 replace+无条件 remove 协议缺陷在 `DebeziumCdcSourceFunction` 调用面的表现，其 merge-adjudication 明言「本 campaign 唯一登记」；本条为 `DebeziumEngineWrapper` 注册表本体面（修复落点在 nop-message）。**表态**：双轨互补成立，合并裁定应互挂 cross-ref 并统一严重度（先案 P2 为基线；P1 升档依据=「任务永挂」后果与校准行「罕见但可具体化的竞态」，终裁留 7.1）。另注：WI 1.2 C4-thread-pools 曾以「register/unregister 严格配对」对 FAILURE_LISTENERS 不立项——该结论只对泄漏面成立，与竞态面（其 C3-006）不冲突。
2. **C1-002 三面分层 vs WI 2.5 消费方面**：nop-message 侧 C1（并发面）/C5-001（内存演化面）/C6-004（清理路径面）三层 + WI 2.5 侧消费面（CL-010 REGISTRY 只增不清 watch 候选、C3-008 lag 探测键错位独立缺陷、C1-005 同名双引擎 P1）。**表态**：分层互斥成立、无重复立项；WI 2.5 CL-010 为台账 watch 非工单；本单元 dispatch-log 已预登记本单元为 REGISTRY 本体落点，C1-002 承接正确；唯一收窄建议见判定表（C1-002 账本子面归 C5-001，避免单元内重复计数）。
3. **其余 9 条**（C1-003…010、C1-012）在 WI 1.1–3.4 台账无同站点/同案命中——Kafka/Pulsar/Local 服务与订阅实现为本单元独有审计面；WI 1.2 C3-014/MA-07（Debezium 停止契约）与 C4-004/MA-06（globalWorker 占用）为 wrapper/引擎的 nop-stream 使用面，与本文件 C1-011（本体状态机面）及 CL 线索双轨互补，非重复。

## 最终保留项

- **保留工单 12 条**：C1-001…C1-012 全部成立（零驳回），其中主登记归并 2 条——C1-006→C3-003（C3 卷）、C1-011→C6-005（C6 卷），修复随主登记 diff 实施。
- **同案并入 1 条**：C1-001 与 WI 1.2 C3-006 双轨互挂（使用面/本体面），修复落点本条（`DebeziumEngineWrapper` 注册表协议：键升维 (name, owner-instance) 或两参条件 remove + 注册返回句柄），最终严重度留 7.1 对齐。
- **修复批次建议（同根归批）**：① destroy/stop 窗口守卫失效族——C1-003/C1-004/C1-005/C1-007（subscribe/producer 装载与 doStop 无互斥，修法一致：入口 checkIsActive 前置 + 装载与 clear 共享服务级锁/二次排空）；② REGISTRY 协议族——C1-002（与 C5-001/C6-004 同 diff：stop 侧配对释放 + 条件移除）；③ 配置冻结——C1-012（装配终点 Map.copyOf + 不可变视图）。
