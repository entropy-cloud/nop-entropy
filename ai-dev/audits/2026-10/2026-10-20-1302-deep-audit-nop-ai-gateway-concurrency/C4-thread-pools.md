# C4 线程池与执行器治理 — nop-ai-gateway（WI 3.3）

- 审计维度：C4 线程池与执行器治理
- 审计对象：`nop-ai/nop-ai-gateway/src/main/java`（36 文件 / 6,035 行，单批次全源码根逐文件审读）
- 审计日期：2026-10-20
- 审计员：C4 盲审初审员（未读 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons、ai-dev/backlog；未查 git log）

## 审计范围与口径

### 覆盖声明

单批次全源码根：36 个 `.java` 文件全部审读（含全部 channel/feishu/failover/login/routing 类与接口）。全模块 grep 盘点线程创建点（`new Thread`、`Executors.*`、`ThreadPool`、`commonPool`、`parallelStream`、`ScheduledExecutor`、`Thread.ofPlatform`/virtual、`ForkJoin`、`runAsync/supplyAsync`），逐一核对。

**线程池清单表**（池 × 参数 × 队列 × 拒绝策略 × 关闭路径 × 判定）：

| # | 池 | 创建点 | 参数 | 队列 | 拒绝策略 | 关闭路径 | 判定 |
|---|----|--------|------|------|----------|----------|------|
| P1 | `ownedFanOutExecutor`（ai-channel-fanout-N，daemon） | `ChannelMessageServiceImpl.java:227` | `newFixedThreadPool(max(2, cores))`，自研 `FanOutWorkerThreadFactory`（有命名、daemon=true） | JDK fixed = `LinkedBlockingQueue` **无界** | 默认 AbortPolicy（无界队列下**不可达**＝无背压信号） | `close()` 存在但**全仓无调用方**、bean **未挂 destroy-method** | 不合格（C4-001/002/003/006） |
| — | 平台池（非本模块自有，只审使用方式） | — | `nopLocalMessageService` 消费线程（mode 2 桥消费者运行面）；`DefaultAgentEngine` 自建 cachedThreadPool `nop-ai-agent-exec`（nop-ai-agent）；`FeishuClient` WS 接收线程 + `newSingleThreadScheduledExecutor(feishu-client)`（nop-integration-feishu） | — | — | — | 使用方式见 C4-005；池本体出界不重审 |

模块内 `CompletableFuture.orTimeout` 使用 3 处（ChannelMessageServiceImpl mode-1/mode-2）依赖 JDK 共享 Delayer 单线程调度器——平台设施，非自建调度器。

### 心跳/超时调度器缺席裁定（留痕）

基线 C4b（词边界 heartbeat/idle/ping）命中 `FailoverMetricsImpl` 的 micrometer `requestTimer`/`Timer` —— 属指标计时器**误报命中**，模块内**不存在** `java.util.Timer`/`ScheduledExecutorService`/自建心跳调度点（grep 全根确认：C4b 节实际为 0 个真调度器）。会话心跳续期（heartbeat renewal）位于 nop-ai-agent `DefaultAgentEngine`/`AgentSessionLifecycle`，出界不立案；超时语义全部经 `orTimeout`（JDK 共享 Delayer）实现。**裁定：本模块无自建心跳/超时调度器，缺席本身不立案。**

### 与 C1/C2/C3 已立案条目的边界声明

C1/C2/C3 已立案面（据派单边界）：FailoverStreamFlow（启动窗/监视器投递/终态冲刷）、FeishuConnector 启动窗（`started` 标志）、并发计数（ConcurrencyRegistry / GatewayStreamingLifecycleListener）。本维度条目与它们的失效面区分：

- **C4-002**（fan-out 池饥饿/无界队列）：同站点（`fanOutToListeners`）但失效面是**池容量/队列治理与背压**，非 C3a 已立案的 orTimeout 中断语义正确性（AR-4 面）。
- **C4-004**（connector 生命周期未接线）：FeishuConnector 失效面是**stop 泄漏与停机后重连活性**，非启动窗 `started` 标志竞态。
- **C4-005**（WS 接收线程内联阻塞 I/O）：失效面是**传输线程占用→ACK 延迟→重投/停摆**，非启动窗、非投递正确性。
- **C4-006**（DCL 无 volatile）：失效面是**安全发布**，与 C3a 异步链面不重叠。

---

## 发现

### [C4-001] nopChannelMessageService 未挂 destroy-method：owned fan-out 池的 close() 无任何调用方，正常停机必然泄漏孤儿池

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:113-115, 241-252`；`nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml:108-126`
- **证据片段**:
  ```java
  // ChannelMessageServiceImpl.java:113-115
  private Executor fanOutExecutor;
  private ExecutorService ownedFanOutExecutor;
  // ...
  // ChannelMessageServiceImpl.java:241-252 —— close() 是唯一的池关闭路径
  public void close() {
      ExecutorService owned = this.ownedFanOutExecutor;
      if (owned != null) {
          owned.shutdownNow();
          try {
              owned.awaitTermination(2, TimeUnit.SECONDS);
          ...
  ```
  ```xml
  <!-- ai-gateway-defaults.beans.xml:108-111 —— 无 destroy-method 属性；
       且全文未注入 fanOutExecutor 属性（setter 存在但无任何 wiring） -->
  <bean id="nopChannelMessageService"
        class="io.nop.ai.gateway.channel.ChannelMessageServiceImpl"
        ioc:type="io.nop.integration.api.channel.IChannelMessageService"
        ioc:default="true">
  ```
- **严重程度**: P1
- **并发场景**: NopIoC 单例 bean 生命周期 × 懒建工作池。mode-1 `dispatchInbound` 与 mode-2 `bridgeConsumer`（经 `LocalMessageService` 消费线程）首次触达 `resolveFanOutExecutor()` 时懒建池；此后池线程常驻（daemon）。
- **触发时序**: T1：部署含任何 inbound 触达（mode-2 部署含 `nop-message-core` 时桥消费者必然触达）→ `resolveFanOutExecutor()` 在 `ChannelMessageServiceImpl.java:227` 懒建 fixed pool；T2：容器 stop，NopIoC 按 beans.xml 销毁单例——`close()` 不在任何 destroy 钩子里（grep 全仓：`close()` 无 main 代码调用方，beans.xml 无 `destroy-method`）→ `shutdownNow`/`awaitTermination` 永不执行；交叠后果：池（`max(2, cores)` 条 daemon 线程 + 无界队列中的在途 InboundChannelMessage）作为孤儿池存活至 JVM 退出。
- **现状**: CL-002 线索核实**成立**：池的关闭路径只有代码内 `close()`，而 `close()` 在生产 wiring 中不可达（bean 无 destroy-method、无 init 钩子、无其他调用方）；`fanOutExecutor` 注入口全仓无使用者，因此 owned 池是唯一生产路径。
- **风险**: 标准单容器 JVM 下泄漏有界（一个池、daemon 线程、JVM 退出兜底，不挂起退出）；但容器重建/热重装配场景每重建泄漏一个池且在途消息闭包滞留队列不被冲刷；与 bean 生命周期不对称违反 F8（register 与 release 未成对——池生命周期挂靠 bean，bean 销毁不联动池销毁）。
- **建议**: beans.xml 为 `nopChannelMessageService` 补 `destroy-method="close"`（或 Nop 等价 `ioc:destroy` 约定）；同时评估注入平台 `IThreadPoolExecutor`（`io.nop.commons.concurrent.executor`，含 ThreadPoolConfig 的拒绝/队列治理）替代自建 JDK 池，由容器统一管生命周期。
- **失效面枚举**（F3 同族清扫）: ① mode-1 `dispatchInbound` 直连 fan-out（`:329-336`）——同一懒建池，同缺陷；② mode-2 `bridgeConsumer.onMessage → fanOutToListeners`（`:150-158`）——同一懒建池，同缺陷；③ mode-2 `sendAsync` 发布路径（`:349-357`）——不使用该池，**豁免**；④ `close()` 本体（`:241-252`）——死代码即缺陷本体；⑤ `setFanOutExecutor` 注入口（`:206-209`）——全仓无 wiring，**无人走"注入方 owns lifecycle"分支**，不构成豁免。
- **信心水平**: 确定
- **误报排除**: 不属于"有界声明的池"误报（缺陷不在参数而在关闭路径缺失）；不属于低频启动路径粗锁误报（这是资源治理缺陷而非优雅性）；基线 CL-002 线索明示要求核实，非臆测。
- **复核状态**: 未复核
- 复核状态: 降级(P2，条件化：fan-out 路径投产接线即回升 P1)｜新发现（= 本单元 C1 overlay CL-002 线索正式立案，同站点互证）。三项事实回码/grep 全证实：`close()` 为唯一池关闭路径（L241-252）、beans.xml 无 destroy-method 且无 fanOutExecutor property wiring（L108-126）、全仓无其他调用方；但 owned 池为懒建且生产零触达——`dispatchInbound` 全仓非测试零调用、桥消费者仅由 `dispatchInbound` mode-2 订阅（`ensureBridgeSubscribed` 唯一调用点在 dispatchInbound 内）、live 面 `sendToUser → sendOutbound` 不经池——**孤儿池当前无可泄漏之物**；按 WI 3.2 零生产接线先例条件化降档（C1 复核对同站点 C1-004/CL-002 的处置对齐：彼 P2→P3，本条 P1→P2，相对序保持）；修复（destroy-method + 平台 IThreadPoolExecutor）与 C1-004/C2-006/CL-002 单工单

### [C4-002] fan-out 池无界队列 + 超时中断仅对协作型 listener 有效：非协作挂死 listener 永久占满小容量池，派发截止契约整体失效且无背压信号

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:226-228, 371-445`
- **证据片段**:
  ```java
  // :226-228 —— 无界队列 fixed 池，容量下限 2
  owned = Executors.newFixedThreadPool(
          Math.max(2, Runtime.getRuntime().availableProcessors()), factory);
  // :384-390 —— 代码自认：释放线程依赖 listener 协作
  // "so the worker thread is captured inside the runnable ... A cooperative
  //  listener (blocking calls throw InterruptedException) releases the pool
  //  thread."
  // :433-436 —— 超时路径唯一手段是 interrupt
  Thread worker = workerRef.get();
  if (worker != null) {
      worker.interrupt();
  }
  ```
- **严重程度**: P1
- **并发场景**: 传输/消息消费线程（`dispatchInbound` 调用方或 `LocalMessageService` 消费线程）向 `ai-channel-fanout-N` fixed 池提交 per-listener 任务；超时管理线程（JDK Delayer）触发 `orTimeout` 回调。
- **触发时序**: T1：消息 A 派发给 listener L，L 在池线程内阻塞于**不响应中断的调用**（典型：老式 JDBC socket read、`Object.wait` 外的原生阻塞、无 soTimeout 的普通 `Socket` read——线程 interrupt 不唤醒）→ 任务永不返回；T2：+`dispatchTimeoutMs`（默认 30s）`orTimeout` 触发 → `cancelled.set(true)` + `raw.cancel(true)` + `worker.interrupt()`（`:425-436`）——对非协作阻塞**全部无效**，仅日志"timed out — task cancelled"；T3：重复 T1 若干次使 `max(2, cores)` 个池线程全部被永久占用；交叠后果：后续所有 `fanOutToListeners` 任务在 `LinkedBlockingQueue`（无界）中无限排队——`dispatchInbound` 不阻塞、不报错、无拒绝信号，派发实际停摆且队列随 inbound 到达率无界增长（F7 OOM 候选 + F5"挂死对健康检测不可见"：超时日志打点后系统再无该 listener 的失败可见性）。
- **现状**: I3 R-2-1 的截止契约（javadoc `:96-103` 声称 hanging listener "can no longer block the transport thread"）对**传输线程**成立，但对池本身不成立：超时机制只保证"报告"，不保证"线程释放"，而代码注释（`:384-390`）自认释放依赖 listener 协作——非协作 listener 是真实存在的 listener 形态（本仓 listener 由外部部署注册，无协作性约束）。
- **风险**: 负载叠加挂死 listener 时 fan-out 通道整体静默瘫痪（消息只在超时日志里出现，之后石沉大海）；持续 inbound 下无界队列滞留 `InboundChannelMessage`（含 rawPayload 字节数组）与闭包引用，长时运行内存无界增长。
- **建议**: 复用平台基础设施：`IThreadPoolExecutor`/`ThreadPoolConfig`（有界队列 + `QueueOverflowPolicy` 显式拒绝策略）替代 `Executors.newFixedThreadPool`；对 listener 执行加**强拆机制**（平台 `CycleSynchronizer`/`ThreadHelper` 或按任务持有 future 做有界池+caller 侧计量），并在连续超时后对持续挂死的 listener 做熔断/摘除而非无限重派。
- **失效面枚举**（F3 同族清扫）: ① mode-1 `fanOutToListeners`（`:371-445`）——缺陷本体；② mode-2 桥消费者复用同一 `fanOutToListeners`（`:150-158` → `:371`）——同缺陷（同一代码路径，无双胞胎修漏面）；③ mode-2 `sendFuture.orTimeout`（`:349-357`）——等待在调用方线程是非阻塞 whenComplete，**豁免**（无池占用问题）；④ failover 域 `FailoverStreamFlow` 窗口（bufferOrForward）——无池、懒检查，出 C4 范围（C1/C3 已立案面）。
- **信心水平**: 确定（失效链完整；"非协作 listener 存在"由 API 公开性保证——`subscribeInbound` 接受任意外部实现）
- **误报排除**: 不是"看到共享字段就报线程不安全"；有具体三步交错时序；不是对 `synchronized`/`FutureTask` 原语本身的报告；也不是"有界声明存在"的误报——**队列无界**恰是缺陷点，容量公式（CPU 核数）用于 I/O 型 listener 无依据且下限 2 极易击穿。
- **复核状态**: 未复核
- 复核状态: 降级(P2，条件化：fan-out 路径投产接线即回升 P1)｜新发现。三步失效链回码全证实：`newFixedThreadPool(max(2,CPU))` 无界 LinkedBlockingQueue（JDK 语义）、L384-390 注释自认线程释放依赖 listener 协作、L433-436 interrupt 对非协作阻塞无效；I3 R-2-1 契约（javadoc L96-103）与实现差距定性准确。触发面与 C4-001 同为 fan-out 休眠面，条件化降档；live 判级与 WI 3.1 MA-11（单线程 poller 内联消费致全模块冻结，P1）对齐。两处 orTimeout 站点（L351/L418）属 WI 2.3 CL-022 移交面（JDK Delayer 共享调度器），修复对齐不重复立项

### [C4-003] close() 关闭路径不完整且与派发存在竞态：未退订 bridge 消费者导致停机后"换装"复活池；在途派发撞上 shutdownNow 抛未处理 RejectedExecutionException

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:216-234, 241-252, 477-491`
- **证据片段**:
  ```java
  // :244-250 —— shutdownNow + 置 null（可被后续派发重建）
  owned.shutdownNow();
  ...
  this.ownedFanOutExecutor = null;
  // :488-489 —— 订阅后从不退订：close() 不清理 subscribedTopics/messageService 订阅
  messageService.subscribe(topic, bridgeConsumer);
  subscribedTopics.add(topic);
  ```
- **严重程度**: P2
- **并发场景**: 容器停机线程（执行 `close()`）× 传输/消费线程（执行 `dispatchInbound → fanOutToListeners`）× `LocalMessageService` 派发线程（停机后仍向未退订的 `bridgeConsumer` 投递）。
- **触发时序**: **(a) 停机后复活**：T1：mode-2 部署中 `ensureBridgeSubscribed` 已把 `bridgeConsumer` 订阅到 `channel.inbound.{type}`（`:488`）；T2：容器 stop 执行 `close()`——池关闭、字段置 null，但**不调用 `messageService.unsubscribe`**（该方法在整个类中不存在订阅撤销路径）；T3：`LocalMessageService`（平台单例，生命周期独立于本 bean）随后投递消息 → `bridgeConsumer.onMessage → fanOutToListeners → resolveFanOutExecutor()`——`ownedFanOutExecutor` 已为 null → **重建一个全新池**（`:221-232`）：已"关闭"的 bean 重新长出活跃线程池，且新池同样无人关闭。**(b) 停机中竞态**：T1'：派发线程在 `:372` 已取到旧池引用 E1；T2'：停机线程 `close()` 执行 `shutdownNow(E1)`；T3'：派发线程 `runAsync(..., E1)` → AbortPolicy 抛 `RejectedExecutionException`，`fanOutToListeners`/`dispatchInbound` 均未捕获 → 异常沿调用面穿透到传输/消费线程。
- **现状**: close() 只做了"关池"一半：订阅撤销缺失（F8 清理不对称）+ 关闭窗口内对提交行为无防御。
- **风险**: 停机后僵尸消费者 + 复活池在后台继续 fan-out（对已销毁 bean 的 listener 列表派发）；停机竞态下异常外泄可能打断外部消息总线的消费循环。
- **建议**: `close()` 内先遍历 `subscribedTopics` 逐 topic `messageService.unsubscribe`（或平台等价 API）再关池；`fanOutToListeners` 对提交做 `rejectedExecution` 防御（catch 后 fail-loud 日志而非穿透）；修复 C4-001 的 destroy-method 后此路径成为常态执行路径，两项必须同批修。
- **失效面枚举**（F3 同族清扫）: ① `close()`×`resolveFanOutExecutor` 竞态（`:216-234` vs `:241-252`）——缺陷本体；② `close()`×`bridgeConsumer` 未退订（`:150-158` + `:477-491`）——同条目并列面；③ `ensureBridgeSubscribed` 的 synchronized 首订阅（`:484-490`）——已有守卫，**豁免**；④ mode-1 无订阅面（无 messageService），不适用；⑤ `inboundListeners`（CopyOnWriteArrayList）只有 add 无 remove（`:307-311`）——listener 无撤销 API，同族不对称但当前无生产注册方，降级记录于本条目风险段不单独立案。
- **信心水平**: 确定（复活链） / 确定（REE 竞态链）
- **误报排除**: 非原语使用误报；两条链均有具体 T1/T2/T3 序列；非"有界声明"豁免对象——close() 的 javadoc（`:236-239`）自述承担释放职责但实现不完整。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：fan-out 路径投产接线即回升 P2)｜新发现；与 C1-004/C2-006/CL-002 同站点族单工单（volatile + close 同锁 + destroy-method + 退订 同 diff）。两链回码均证实：(a) 复活链——close 置 null（L250）可被 `resolveFanOutExecutor` 重建、全类无退订 API；(b) REE 竞态链——派发线程 L372 取引用与 close `shutdownNow` 交错，`runAsync` 同步抛 REE 无捕获。两链触发路径全部经 `dispatchInbound`/桥投递休眠面（grep 证据同 C4-001），条件化降档与 C1 复核对 C2-005/C1-004 的处置（P2→P3 条件化）对齐

### [C4-004] ChannelConnectorManager 声明统一生命周期但从未接线：startAll/stopAll 全仓无生产调用方、bean 无 init/destroy 钩子——停机时 FeishuConnector.stop()/feishuClient.stop() 永不执行，重连调度器在停机后继续活性重连

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelConnectorManager.java:100-120`；`nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml:39-45`；`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:205-217`
- **证据片段**:
  ```java
  // ChannelConnectorManager.java:100/115 —— 生命周期入口存在
  public void startAll(ChannelConnectorContext context) { ... }
  public void stopAll() { for (int i = started.size() - 1; i >= 0; i--) started.get(i).stop(); ... }
  // beans.xml:39-41 —— 无 init-method / destroy-method
  <bean id="nopChannelConnectorManager"
        class="io.nop.ai.gateway.channel.ChannelConnectorManager">
  ```
- **严重程度**: P1
- **并发场景**: NopIoC 容器生命周期 × `FeishuClient` 内部单线程调度器（`feishu-client`）× WS 长连接线程。连接与调度器均为 daemon，但 `scheduleReconnect` 逻辑在 stop 缺席时持续运行。
- **触发时序**: T1：部署方按 javadoc 声明的契约驱动 `startAll`（当前审计树 main 代码内无调用方——启动侧本身也未被容器接线，特征状态一并登记）；T2：`FeishuConnector.start → feishuClient.start` 建立 Stream 长连接 + `feishu-client` 单线程调度器（重连循环就绪）；T3：容器 stop——`nopChannelConnectorManager` 无 destroy-method、全仓无 `stopAll()` 生产调用（grep 审计树 main 代码：仅 nop-orm-migration 自身的同名方法）→ `FeishuConnector.stop()`（`FeishuConnector.java:205-217`）永不执行 → `feishuClient.stop()` 永不执行；交叠后果：WS 断开后重连任务仍被调度、`httpApi.getStreamEndpoint`（HTTP）在已停止的应用进程内继续发起、连接重建后 `handler.onMessage`（已停止 connector 的 `started=false` 仅挡住派发但连接与调度器仍在）——停机后活性错误行为 + 每轮容器重建累积一组孤儿长连接/调度器线程。
- **现状**: 生命周期 owner（manager）的 start/stop 与 IoC 容器 init/destroy 双向断链（F8：连接的 open 由 start 承担、close 无任何到达路径）；`stopAll` 的 LIFO 逆序与失败即抛语义（javadoc `:27-30`）全是死代码。
- **风险**: 只要 connector 启动面被任何部署接通，停机即泄漏 WS 连接 + 调度器且产生停机后外呼（对 Feishu 端点持续重连）；应用语义上"已停止"与"仍在重连"矛盾，属资源泄漏与停机活性双失效。
- **建议**: beans.xml 为 `nopChannelConnectorManager` 补 init（调用 startAll 需上下文——可引入平台 Lifecycle/Startable 约定或 `ioc:init-method` 配套工厂）与 `destroy-method="stopAll"`；过渡方案最低限度先挂 destroy-method 保证停机侧闭环。
- **失效面枚举**（F3 同族清扫）: ① `nopChannelConnectorManager` bean——缺陷本体；② `nopFeishuConnector` bean（`feishu-defaults.beans.xml` 导入）——被 manager collect，其 stop 只经 stopAll 到达，同缺陷传递面；③ `nopChannelMessageService.close()`——已在 C4-001 立案（独立条目，不重复计数）；④ `FeishuClient` 调度器/重连本体——nop-integration-feishu 所有，出界，登记 CL-009 线索；⑤ `IChannelConnector` 其他实现——审计树内仅 FeishuConnector 一个实现，无遗漏兄弟。
- **信心水平**: 确定（接线缺失为 grep 全树实证）；对"启动侧缺席属 WIP 还是缺陷"标注**很可能**（feature 进行中形态），但停机侧断链独立于启动侧进度成立
- **误报排除**: 非低频启动路径优雅性报告——是停机资源治理缺失；非"有界声明"对象——javadoc 明文声明 Unified lifecycle 责任（`:27-30`）而接线不存在，属"守卫缺失、文档未声明豁免"型缺陷。
- **复核状态**: 未复核
- 复核状态: 降级(P2，条件化：连接器生命周期投产接线即回升 P1)｜新发现。接线缺失 grep 实证维持（`startAll`/`stopAll` 全仓非测试零调用——仅测试树 + nop-orm-migration 同名词异类；beans.xml L39-45 无 init-method/destroy-method）；javadoc 声明 Unified lifecycle 而接线不存在的"守卫缺失"定性准确。**严重度校准**：停机断链逻辑独立于启动侧成立（初审已标注），但启动侧同样零接线 → 当前生产无活跃 WS 连接/重连调度器，**无可泄漏之物**——触发面休眠；按 C1 复核对同面先例（C1-002 P2→P3、C2-003 P2→P3 条件化）与本单元相对序（C4-004 live 面 P1 > C1-002 live 面 P2），条件化降档至 P2，投产接线即回升 P1

### [C4-005] FeishuConnector.onMessage 在 WS 接收线程内联执行 DB 查询/写 + 引擎同步阶段 + 同步 HTTP 回复，ACK 被动延迟：慢 DB → 全通道停摆与服务端重投递风暴

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:267-367`（内联点 `:313, :321, :345`；WS 线程同步回复 `:303, :355, :359`）；证据佐证 `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:330-359`
- **证据片段**:
  ```java
  // FeishuConnector.java:313-325 —— WS 线程上同步 DB 读 + DB 写
  session = sessionStore.findByChannel(CHANNEL_TYPE, chatId);
  ...
  sessionStore.updateLastActive(CHANNEL_TYPE, chatId);
  // :345 —— 引擎 execute 的同步阶段（session get-or-create/markInputSeen/save，
  //  见 DefaultAgentEngine.java:761-793）同样在调用线程执行
  future = context.getAgentEngine().execute(request);
  // FeishuClient.java:339-353 —— handler.onMessage 返回后才回 ACK
  handler.onMessage(msg);
  ... transport.send(FeishuPbCodec.encode(ack));
  // 该注释明示后果：缺失时"服务端视为未确认而重复投递/反复断连"（P1-6.8-004）
  ```
- **严重程度**: P1
- **并发场景**: Feishu Stream WS 接收线程（JDK WebSocket 顺序投递，单线程串行）× 业务 DB（ChannelSessionStoreImpl 的 OrmTemplate 会话）× agent 引擎同步装配阶段 × 同步 HTTP（`feishuClient.sendMessage`）。
- **触发时序**: T1：消息 A 到达 WS 接收线程 → `onMessage` 内联执行 `findByChannel`（DB 查询，`:313`）+ `updateLastActive`（DB 写，`:321`）+ `execute()` 同步阶段（模型加载、session get-or-create、`markInputSeen` + `sessionStore.save` DB 写）→ DB 抖动使该串耗时 30s+；T2：期间 WS 后续帧 B、C 因接收线程被占用而无法被读取处理（JDK WebSocket listener 顺序回调）→ A 的 ACK 延迟 30s+；T3：Feishu 服务端 ACK 超时 → 按 FeishuClient 代码注释（`:341-345`）判定未确认而**重复投递 A、反复断连**——重投递放大 onMessage 执行量，进一步拖长接收线程占用，形成正反馈；交叠后果：整通道 inbound 停摆 + 重投递风暴 + 幂等层（dedup key）成为唯一防线但仍消耗 DB 写（每次重投递都走 `markInputSeen`+`save`）。
- **现状**: 传输线程上零任务隔离：DB I/O、引擎同步装配、限流拒绝路径的同步 HTTP 回复（`:303` `deliverSegmented`）、同步抛错路径的同步 HTTP 回复（`:355/:359`）全部内联在接收回调里；限流窗 `tryAcquireInbound` 虽轻但也在同一锁内（无害）。
- **风险**: 主 inbound 路径的可用性被最慢的同步环节钳制；DB/引擎抖动直接转化为对上游的协议层违约（ACK 延迟/断连）；重投递与限流窗口相互作用（重投递占用窗口额度）可能把正常用户消息挤出限流配额。
- **建议**: 在 `onMessage` 入口做线程交接（复用平台 `IThreadPoolExecutor` 或模块自有 fan-out 池的姊妹池）后再执行 sessionStore/引擎调用；ACK 时机问题需与 FeishuClient 协调（接收线程只做解析+交接+ACK）。注意修复时保持 dedup/幂等语义（交接后 `markInputSeen` 时点不变）。
- **失效面枚举**（F3 同族清扫）: ① `onMessage` 主路径（`:267-367`）——缺陷本体；② 限流拒绝路径 `deliverSegmented`（`:303`）——WS 线程同步 HTTP，同缺陷面；③ 同步抛错路径 `sendReply`（`:355, :359`）——WS 线程同步 HTTP，同缺陷面；④ `onExecutionComplete → sendReply → deliverSegmented`（`:376-416, :425-452`）——运行于 `nop-ai-agent-exec` 池线程（cachedThreadPool，弹性扩容）故无停摆风险，但仍是"池线程内同步网络 I/O"，降级登记不单独立案；⑤ `sendOutbound → deliverSegmented`（`:220-262`）——同步接口契约（调用方线程，含 HTTP worker），接口设计使然，**豁免**（IChannelConnector 契约面，非本维度缺陷）。
- **信心水平**: 确定（内联阻塞点与 ACK 时序均为代码实证）；"服务端重投递"引用 FeishuClient 注释断言（跨系统行为，很可能）
- **误报排除**: 与 C1/C2/C3 已立案的 FeishuConnector 启动窗（`started` 标志）、监视器投递、终态冲刷失效面不同——本条失效面是传输线程占用→ACK 延迟→重投递；非"无状态单例并发调用"误报；非低频路径——这是 channel 主 inbound 路径。
- **复核状态**: 未复核
- 复核状态: 降级(P2，条件化：连接器投产接线即回升 P1)｜新发现。内联点逐行回码证实：`findByChannel` L313、`updateLastActive` L321、engine.execute 同步阶段 L345、WS 线程同步 HTTP 回复 L303/L355/L359；ACK-after-handler 时序经 FeishuClient `onBinary` 回码证实（DATA 帧处理 → handler.onMessage → 之后才 send ACK，P1-6.8-004 注释在库）——慢 handler → ACK 延迟 → 服务端重投递的失效链成立。live 判级与 WI 3.1 MA-11（同族"单线程 poller 内联消费"P1）对齐；触发面随连接器生命周期休眠（`startAll` 零生产接线，C1 复核 grep 证据沿用），条件化降档。与 WI 2.1 CL-021（raw JDK/Netty 载体绕过设施，feishu 移交面）邻接不重复：彼为传输载体治理面（FeishuClient 自建线程），本条为 handler 侧使用面，修复同批对齐

### [C4-006] resolveFanOutExecutor 的双重检查锁缺少 volatile：ownedFanOutExecutor 非安全发布（JMM 数据竞争，理论风险）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:115, 216-234`
- **证据片段**:
  ```java
  // :115 —— 非 volatile 字段承载 DCL
  private ExecutorService ownedFanOutExecutor;
  // :221 —— 无锁 fast-path 读（与 :229 synchronized 内的普通写之间无 happens-before 边）
  ExecutorService owned = this.ownedFanOutExecutor;
  if (owned == null) {
      synchronized (this) { ... this.ownedFanOutExecutor = owned; }
  }
  ```
- **严重程度**: P2
- **并发场景**: 并发首次派发：多个传输/消费线程同时执行 `resolveFanOutExecutor()` 的无锁 fast-path 读（`:221`）与 synchronized 慢路径写（`:229`）。
- **触发时序**: T1：线程 A 进入 synchronized 块，执行 `Executors.newFixedThreadPool` 构造（构造器内部多次普通字段写）后普通写 `this.ownedFanOutExecutor = owned`；T2：线程 B 恰在 A 写之后、尚未获得锁时执行 `:221` 无锁读——因字段非 volatile 且读侧无同步，JMM 下允许观察到**非 null 引用但对象字段未完全可见**（数据竞争，无未定义行为但行为实现相关）；交叠后果：B 以可能不完整的池对象执行 `runAsync` 提交，理论上可观察到 `ThreadPoolExecutor` 内部状态默认值（如 ctl/handler）导致 NPE 或提交行为异常。触发概率极低（需毫秒级指令重排窗口 + 特定硬件/编译器重排），写不出必然复现的确定性序列。
- **现状**: 经典 DCL-without-volatile 形态；锁内二次检查保证了单次创建（无重复建池），缺陷仅在发布语义。
- **风险**: 理论上的半初始化对象使用；实际风险集中在首次派发并发窗口， manifestations 罕见且难诊断（偶发首次提交 NPE）。
- **建议**: `ownedFanOutExecutor` 声明为 `volatile`（一行修复）；或改用平台惯例的 holder/`IAtomicReference` 方案。
- **失效面枚举**（F3 同族清扫）: ① `resolveFanOutExecutor` DCL（`:216-234`）——缺陷本体；② `ensureBridgeSubscribed` 的 DCL（`:477-491`）——`subscribedTopics` 为 `ConcurrentHashMap.newKeySet()`（`contains/add` 自身线程安全 + 锁内串行化），且共享状态是线程安全容器而非裸字段，**豁免**（无安全发布问题）；③ `FeishuConnector.capabilities` 懒建（`:158-170`）——无锁竞争写但产物为幂等同值对象且全 final 语义 setter（`ChannelCapabilities` 普通字段非 final——存在同族弱化），影响仅为重复构造/读撕裂，登记**watch-only**，不立案；④ 其余懒建点全模块 grep 无更多 DCL。
- **信心水平**: 确定（DCL 缺 volatile 事实确定；危害表现=理论风险）
- **误报排除**: 按"写不出具体时序的降级 P2"规则**主动降级并标注理论风险**；非对 ConcurrentHashMap/原语的误报（对象是普通 `ExecutorService` 裸字段）；非低频路径豁免对象——fast-path 在每条消息派发上执行。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：fan-out 路径投产接线即回升 P2)｜新发现；与 C1-004(a)/C2-006(a) 同站点 DCL 单工单。缺 volatile 回码证实（L115 非 volatile 字段承载 L221 无锁读 × L229 锁内普通写）；实害缓解勘误沿用 C1 复核（ThreadPoolExecutor 关键字段 final，半初始化观测面进一步受限）；触发路径经 dispatchInbound 休眠面，条件化降档与 C1 复核同站点处置（P2→P3 条件化）完全对齐

## 维度集外线索（overlay C5/C9）

### nop-ai-gateway-CL-009（C5 定时任务线索，锚点在 nop-integration-feishu，经本维度 stopAll 断链发现）

`FeishuClient` 的 `newSingleThreadScheduledExecutor(new DaemonThreadFactory("feishu-client"))`（`FeishuClient.java:101`）单线程调度器同时承载重连循环：`getStreamEndpoint`（同步 HTTP，`FeishuClient.java:302`）直接在调度线程内执行（`connect()` 的 reconnect 分支）。单线程语义下一次 `getStreamEndpoint` 网络悬挂（无超时证据需核实 httpApi 实现）将阻塞同线程上的后续重连/其他调度任务，且 `scheduleReconnect` 无限重试链在 `stop()` 后才可终止——而 `stop()` 经 C4-004 断链在生产不可达。C5 维度立案时建议核查：① 调度线程任务的周期与时长关系（任务慢于周期时的堆积策略）；② stop 后调度器 shutdown 路径；③ getStreamEndpoint 是否有 HTTP 超时。

### nop-ai-gateway-CL-010（C9 长时运行无界增长线索，锚点 ChannelMessageServiceImpl）

`subscribedTopics`（`ChannelMessageServiceImpl.java:135`）只增不删（`ensureBridgeSubscribed` 仅 add，`close()` 不清理且全类无退订 API）——F4 无界账本面：topic 集合随 channelType 种类线性增长（有界于 channel 类型数，短期无害），但配套的 `messageService` 侧订阅（`LocalMessageService` 消费者表）按 bean 生命周期只增不减；叠加 `inboundListeners`（`:265`，CopyOnWriteArrayList）同样只有 `subscribeInbound` 无注销 API——在长时运行 + 容器重装配场景下，消费者与 listener 登记表单向累积，且滞留的 `bridgeConsumer` 引用把已销毁 bean 的 listener 列表钉在平台单例消息服务里（GC 不可达）。C9 维度建议核实：`LocalMessageService` 消费者表是否随容器重建累积、滞留消息闭包的驻留时长。

## 维度复核结论（WI 3.3 独立复核，合并批 C3+C4）

- **复核日期**: 2026-10-20
- **复核人**: C3+C4 维度独立复核员（与初审/深挖/C1+C2 复核员相互独立）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` 校准口径；7 条发现证据行逐条回码、时序论证逐步核验、三态判定 + 现状标注；降档口径核对（休眠面按 WI 3.2 零生产接线先例条件化降档，逐条附 grep 证据——本次复核独立重跑 grep 并与 C1 复核证据互证）；与 `ai-dev/audits/cra-index.md` WI 1.1–3.2 台账逐条查重。

### 台账查重结论

C4 维度 7 条与 cra-index WI 1.1–3.2 台账**零同站点重复立项**。WI 2.3（nop-service-framework-hotpath，含 nop-gateway）对本模块站点的唯一登记为 CL-022 移交面（`ChannelMessageServiceImpl` L351/L418 orTimeout → JDK Delayer 共享调度器，移交 nop-ai/nop-message）——C4-002 引用的 orTimeout 属该移交面核对项，非缺陷重复登记；渠道栈三站点（ChannelMessageServiceImpl/FeishuConnector/ChannelConnectorManager）在 WI 1.1–3.2 无任何缺陷登记。

### 逐条判定表（C4-001…007）

| ID | 初判 | 判定 | 终判 | 核心依据 |
|---|---|---|---|---|
| C4-001 | P1 | 降级 | P2（条件化） | close 唯一关闭路径 + beans.xml 无 destroy-method + 无 fanOutExecutor wiring 三项证实；但 owned 池懒建且生产零触达（dispatchInbound/桥投递全零生产调用、sendToUser 不经池）——孤儿池当前无可泄漏之物；零生产接线先例条件化降档（与 CL-002/C1-004 单工单） |
| C4-002 | P1 | 降级 | P2（条件化） | 三步失效链回码全证实（无界队列/注释自认协作依赖/interrupt 对非协作无效）；触发面与 C4-001 同休眠面；live 判级与 WI 3.1 MA-11 同族 P1 对齐 |
| C4-003 | P2 | 降级 | P3（条件化） | 复活链 + REE 竞态链回码证实；触发路径全经休眠面；与 C1-004/C2-006/CL-002 单工单（处置与 C1 复核 P2→P3 条件化对齐） |
| C4-004 | P1 | 降级 | P2（条件化） | 接线缺失 grep 实证维持；停机断链独立成立，但启动侧同零接线 → 当前无活跃连接/调度器可泄漏，触发面休眠；live 面 P1 > C1-002 live 面 P2 的相对序保持 → P2 |
| C4-005 | P1 | 降级 | P2（条件化） | 内联三阻塞点 + ACK-after-handler 逐行回码证实；live 判级与 WI 3.1 MA-11 同族 P1 对齐；触发面随生命周期休眠；与 WI 2.1 CL-021 邻接不重复 |
| C4-006 | P2 | 降级 | P3（条件化） | DCL 缺 volatile 回码证实；TPE final 字段缓解实害（C1 复核勘误沿用）；与 C1-004(a)/C2-006(a) 同站点单工单（处置完全对齐） |
| C4-007 | P3 | 保留 | P3 | workerRef 捕获 + interrupt 路径回码证实；注入口双层休眠（无 beans.xml wiring + fan-out 面零触达），P3 恰当 |

### 判定统计（C4 维度）

- **保留×1**：C4-007（P3）
- **降级×6**：C4-001/C4-002/C4-004/C4-005（P1→P2 条件化，零生产接线）；C4-003/C4-006（P2→P3 条件化，同站点族对齐）
- **驳回×0**（7 条无证据缺陷；全部站点与引文行号经回码核实，未发现初审夸大机制本身——降档全部源于触发面休眠而非机制不成立）
- 终判严重度分布：P2×4（条件化）/ P3×3（条件化×2 + 保留×1）；P1×0（全部条件化回升挂起）

### 现状标注分布（C4 维度）

- 渠道栈休眠（fan-out 面零生产接线：dispatchInbound/桥投递零生产调用）：C4-001、C4-002、C4-003、C4-006、C4-007（双层休眠）
- 渠道栈休眠（连接器生命周期零接线：startAll/stopAll 零生产调用）：C4-004、C4-005
- live 面（对照留痕）：本维度无 live 站点——owned 池唯一"消费"面 sendToUser → sendOutbound 不经池；failover 域（C3 维度）为全部 live 面

### 降档口径核对（grep 证据留痕）

1. `dispatchInbound`/`subscribeInbound` 全仓非测试零调用（grep 命中仅 nop-ai-gateway 测试树、nop-datav MockChannelMessageService 异类实现）——fan-out/桥订阅/注入执行器各面休眠 → C4-001/002/003/006/007 条件化降档。
2. `startAll`/`stopAll` 全仓非测试零调用（仅测试树 + nop-orm-migration OrmMigrationOrchestrator/IncrementalJobManager 同名词异类）——连接器生命周期休眠 → C4-004/005 条件化降档。
3. `sendToUser`（nop-datav NotificationSender 经 IChannelMessageService 唯一 live 消费面）不经 fan-out 池（sendOutbound 直连 connector）——owned 池生产零创建，C4-001"孤儿池"当前无可泄漏之物（降档核心理由）。
4. 请求触达面不适用启动期降档——本维度 7 条无一位于 failover 请求面，降档全部为休眠面条件化，口径一致。

### 跨维指针核对（同案命中清单）

1. **C4-001 = 单元内 CL-002 同站点**（C1 overlay 线索正式立案，互证成立——destroy-method 缺失两文件各自独立核实一致）。
2. **C4-003 = C1-004 = C2-006 + CL-002 同站点族**：单工单合并修复（volatile + close 同锁 + destroy-method + 退订同 diff）；C4-006 = C1-004(a)/C2-006(a) DCL 面（并入同工单）。
3. **WI 2.1 CL-021**（12+ raw 载体绕过设施，feishu 移交面）↔ C4-005/本文件 CL-009：载体治理面（FeishuClient 自建 WS 线程 + 单线程调度器）归 CL-021 移交轮，本条为 handler 使用面——邻接不重复，修复同批对齐。
4. **WI 2.3 CL-022**（orTimeout → JDK Delayer 共享面移交）↔ C4-002 引用的 L351/L418 两站点：移交面核对项，非缺陷重复。
5. **WI 3.1 MA-11**（DBMessageService 单线程 poller 内联消费 P1）↔ C4-005：同族异模块（nop-ai-core vs nop-ai-gateway channel），判级口径对齐（live 均 P1），无站点重复。
6. **WI 3.1 MA-04**（spawn 编排池泄漏 P1，per-invocation 池无 close）↔ C4-001：同族异形态（懒建单例池 vs per-invocation 池），批量修复时对齐"池生命周期挂靠 bean"治理模式。
7. C1 复核跨维指针第 10 条（WI 2.3 CL-022 归 C4/C9 核对）在本节闭合。

### 高风险条目子项复核说明

- **C4-005（P1→P2 条件化）**：初审失效链的每个环节逐一回码——(a) 内联点行号全部命中（L313/L321/L345/L303/L355/L359）；(b) ACK 时序：`FeishuClient.onBinary` 对 DATA 帧先 `handler.onMessage(msg)` 后 `transport.send(ack)`，ACK 延迟 = handler 时长的被动传导成立；(c) 重投递放大引用 FeishuClient 在库注释（P1-6.8-004），初审已如实标注"跨系统行为，很可能"；(d) 失效面⑤（sendOutbound 接口契约）豁免恰当。降档仅因触发面休眠（startAll 零接线），机制本身零修正——投产接线即回升 P1。
- **C4-004（P1→P2 条件化）**：初审自带的"启动侧缺席属 WIP 还是缺陷"不确定性在本复核收敛为处置口径——启动/停机双侧零接线为同一 grep 事实，停机断链的"独立于启动侧成立"逻辑正确但当前无观测后果（无活跃连接）；降档不改变修复指令（最低限度先挂 destroy-method）。
- **C4-002（P1→P2 条件化）**：失效链三步中初审的两处关键自证（L384-390 注释、L433-436 interrupt）均逐字核实；对"非协作 listener 真实存在"的论证（API 公开性，`subscribeInbound` 接受任意外部实现）接受；无背压信号（无界队列下 AbortPolicy 不可达）的 JDK 语义判断正确。

## 最终保留项（C4 维度）

| 工单面 | 条目 | 终判 | 修复方向 | 合并指令 |
|---|---|---|---|---|
| 孤儿池（生命周期挂靠断链） | C4-001 | P2 条件化 | beans.xml 补 `destroy-method="close"`；评估平台 IThreadPoolExecutor 替代自建池 | 与 C1-004/C2-006/CL-002 单工单（投产接线即回升 P1） |
| 无界队列 + 非协作挂死池耗尽 | C4-002 | P2 条件化 | 有界队列 + QueueOverflowPolicy 显式拒绝策略 + listener 连续超时熔断/摘除 | 同上工单池治理面（回升 P1）；orTimeout 站点随 WI 2.3 CL-022 移交面对齐 |
| close 不完整（复活池 + REE） | C4-003 | P3 条件化 | close 先退订 subscribedTopics 再关池；runAsync 提交加 REE 防御 | 同上工单同 diff（回升 P2） |
| 连接器生命周期断链 | C4-004 | P2 条件化 | 最低限度挂 destroy-method="stopAll"；启动侧按平台 Lifecycle/Startable 约定接线 | 独立（投产接线即回升 P1） |
| WS 接收线程内联阻塞（ACK 延迟） | C4-005 | P2 条件化 | onMessage 入口线程交接（平台 IThreadPoolExecutor）后再执行 sessionStore/引擎；ACK 时机与 FeishuClient 协调，保持 dedup 语义 | 独立（回升 P1）；与 WI 2.1 CL-021 feishu 载体治理面、WI 3.1 MA-11 同批对齐 |
| DCL 缺 volatile | C4-006 | P3 条件化 | `ownedFanOutExecutor` 声明 volatile（一行） | 并入 C4-001 工单同 diff（回升 P2） |
| 注入执行器无契约校验 | C4-007 | P3 | setFanOutExecutor javadoc 补执行器契约；评估注入口径类型改 IThreadPoolExecutor | 独立（7.1 裁决修/deferred） |
