# C2 锁与同步原语 — nop-ai-gateway（WI 3.3）

- 审计维度：C2（锁与同步原语）
- 审计对象：`nop-ai/nop-ai-gateway/src/main/java`（36 文件 / 6,035 行，单批次全源码根逐文件审读）
- 审计日期：2026-10-20
- 初审员：C2 维度盲审初审员（单批次）
- 基线：`_tmp/cra-baseline-nop-ai-gateway.txt` C2a（锁载体 14 处）/ C2b（computeIfAbsent/compute 2 处）圈定重点，数量结论按下文人工复现

## 审计范围与口径

**覆盖声明**：本批次对 `nop-ai/nop-ai-gateway/src/main/java` 全部 36 个 Java 文件逐文件完整审读（非抽样）；`channel/`（12f）、`failover/`（16f）、`login/`（5f）、`routing/`（8f，含根包 1f）四个子包全数覆盖。锁载体清单与基线 C2a 一致（FeishuConnector `start/stop` synchronized + `rateLimitLock`；ChannelMessageServiceImpl 两处 `synchronized(this)`；GatewayStreamingLifecycleListener 两 synchronized 方法；FailoverStreamFlow `subscribeLock` + 7 处 `synchronized(this)`；ConcurrencyRegistry 两处 `compute`），基线外新增识别的同步面：ChannelConnectorManager / ChannelLoginApiBizModel 的零锁注册容器、ChannelSessionStoreImpl 的跨 DB check-then-act、FailoverStreamFlow.AttemptCancelToken 自研取消原语、FeishuConnector.getCapabilities 惰性初始化——均已逐一核对。

**锁序图（文本）**（`A → B` = 持 A 获取 B；全部边经代码交叉验证）：

```text
FeishuConnector.this ──(start/stop)──> FeishuClient.this ──> HTTP/WS 网络 I/O（阻塞点）
FeishuConnector.rateLimitLock          ——叶子锁，无嵌套（tryAcquireInbound 唯一访问点，与 this 无交错）
FailoverStreamFlow.subscribeLock       ——叶子锁（仅字段读写）
FailoverStreamFlow.StreamSubscription.this ──> ThresholdBreaker entry monitor（handleStreamComplete→recordSuccess）
                                          ──> 下游 Subscriber 回调（onNext/onError/onComplete，未知外部代码，无界）
ChannelMessageServiceImpl.this ──(ensureBridgeSubscribed)──> IMessageService.subscribe 内部锁（本地总线=CHM bin；DB/Kafka=未知）
                               ──(resolveFanOutExecutor DCL)──> （池构造，无外部锁）
GatewayStreamingLifecycleListener.this ——叶子锁
ConcurrencyRegistry CHM per-bin ——叶子（compute 内轻操作）
```

**无环结论**：全部边单向、无反向边（FeishuClient 回调 `streamListener→handler.onMessage` 不持 client 监视器；LocalMessageService 投递遍历 COW 不持锁；下游 Subscriber 无已知逆边）——本模块无可达死锁环；C2 缺陷集中在**持锁 I/O、锁扩展、取消/转换交错原子性、零锁生命周期容器**四族。

**心跳/超时调度器缺席裁定**：基线 C4b 显示本模块源码无任何心跳/idle/ping 调度器站点（仅 `FailoverMetricsImpl.requestTimer` 指标名词形命中，非调度器）。心跳机制实际位于 `FeishuClient`（nop-integration-feishu，`scheduleHeartbeat`/`redeliveryScheduler`）——**越界，不立案**，仅在 C2-003 作为持锁阻塞点的证据引用。本维度无调度器类发现。

**与 C1 已立案条目的边界声明**：C1 已就 ChannelConnectorManager 零锁容器的**可见性面**（C1-001…008）立案。本维度 C2-004 立案的是同站点的**互斥缺失面**（startAll/stopAll 并发交叠的丢失更新/漏停，interleaving corruption），失效维度不同；C2-005 立案的 ChannelSessionStoreImpl/FeishuConnector 是**跨请求 find-then-create 原子性面**，与 C1 的字段可见性面无重叠。其余发现站点（FailoverStreamFlow、FeishuConnector.start/stop、ChannelMessageServiceImpl、getCapabilities、AttemptCancelToken）与 C1 已知站点边界已逐一核对，无同失效面重复。

**降档口径**：本模块全部发现站点均在请求/消息触达面（流式网关主路径、飞书入站路径），无启动期独占站点，启动期降档不适用；唯 C2-007（getCapabilities）当前无生产消费方（接口 javadoc RESERVED 裁定），按请求触达面口径降档 P3 潜伏登记。

## 发现

### [C2-001] FailoverStreamFlow 取消与重订阅交错窗口内新 attempt 的并发计数永久泄漏

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:234-298, 323-344, 507-516`
- **证据片段**:
  ```java
  // startAttemptWithOptions：cancelled 检查(235) 与 attempt 装配(288) 之间无原子性
  if (cancelled.get()) { return; }                       // L235
  ...
  int count = adapter.getRegistry().acquire(candidate.getProvider(), candidate.getAccountKey()); // L263
  ...
  AttemptState state = new AttemptState(attemptsStarted, candidate, sunk, new AttemptCancelToken());
  attempt = state;                                       // L288（volatile 写，非原子区）
  ...
  publisher.subscribe(new AttemptSubscriber(state));     // L292
  // onSubscribe 的取消分支只取消上游订阅，不释放已 acquire 的计数：
  if (cancelled.get() || state != attempt) { subscription.cancel(); return; }  // L510-513
  ```
- **严重程度**: P1
- **并发场景**: delegate HTTP 客户端回调线程（attempt1 失败信号 → `handleStreamFailure` → 被动重选重订阅）与调用方取消线程（caller token 回调 / 输出订阅 `cancel()` → `teardownCurrentAttempt`）。
- **触发时序**: T1 在 `handleStreamFailure(state1)` 窗口内路径通过取消检查后到达 L462 `startAttemptWithOptions(null,null)`，通过 L235 `cancelled.get()==false` → 执行候选选择并在 L263 对新候选 `acquire`（计数 +1）。T2 此刻执行 `cancel()`（L208 CAS 成功）→ `teardownCurrentAttempt`：`attempt=state1→null`、`up.cancel()`、`token.cancel`、`releaseAttempt(state1)`（若 T1 已在 L418 释放则 CAS 跳过，无双重释放）。T1 继续：L288 `attempt=state2` 覆盖 null → L292 subscribe → `AttemptSubscriber.onSubscribe` L510 读到 `cancelled==true` → 仅 `subscription.cancel()` 返回。交叠后果：state2 的 acquire 无任何 release 路径（teardown 已跑完、`handleStreamFailure/Complete` 因取消守卫不可达、`released` CAS 无人触发）→ `ConcurrencyRegistry` 该 `(provider, accountKey)` 计数永久 +1，同时 `onConcurrencyRelease` 指标缺失（观测漂移）。
- **现状**: 取消检查（L235）、计数获取（L263）、attempt 装配（L288）三步之间无原子区；`onSubscribe` 的取消分支（L510-513）是唯一能观测到"新 attempt 遇已取消流"的位置，却只取消上游订阅、不回滚计数。
- **风险**: 幽灵计数使 `currentCount` 虚高 → `DefaultSelectionStrategy.isConcurrencySaturated`（`current>=limit`）持续跳过该账号；累计触发 N 次（N=该账号 `concurrencyLimit`）后该账号永久饱和，长时运行网关慢相变容量损失，最终 `ERR_AI_MODEL_CLASS_SATURATED` 误报全池饱和。同类幽灵计数正是 `ConcurrencyRegistry` javadoc L34 明言要在原语层杜绝的形态——原语层已修，编排层仍留此窗口。
- **建议**: 最小修复 = `AttemptSubscriber.onSubscribe` 的取消分支补 `releaseAttempt(state)`（一行覆盖全部三种交错变体：取消落在检查前/装配前/装配后）；结构性修复 = 将"取消复查 + acquire + attempt 装配"移入 `StreamSubscription` 监视器临界区，并让 `teardownCurrentAttempt` 同经该监视器，保证装配与拆除互斥。`released` AtomicBoolean 单次守卫机制可原样复用。
- **失效面枚举**（F3 同族清扫，`startAttemptWithOptions` 全部 4 个进入点 + 对照路径）:
  1. `start()` → L227 首次 attempt：取消先于订阅则 L216 拦截，窗口同在但此时无已 acquire 计数可漏 → 无害；
  2. 超限重选递归 L273：同一窗口，同泄漏面（同键 acquire→放弃）→ **同病未修**；
  3. CACHE_STATE_LOST 原地重发 L443（`handleStreamFailure` inWindow 路径对该分支无二次取消检查）→ 同窗口 → **同病未修**；
  4. 切换类重订阅 L462（本案触发路径）→ **已立案**；
  5. 对照·非流式 `ChatServiceFailoverAdapter.callAsyncWithOptions` L246-248：`whenComplete` 恒 release（取消导致 delegate 异常终止时仍触发）→ 有终止即释放兜底，**无此缺陷**——流式路径缺少等价兜底正是本条根因。
- **信心水平**: 确定（交错序列可完整写出；release 路径穷举确认缺失）
- **误报排除**: 非"看到共享字段报线程不安全"——`attempt` volatile 读本身正确；缺陷在复合操作序列缺原子区，且后果（永久计数泄漏）可具体化到注册表键级别。亦非已声明边界：`ConcurrencyRegistry` javadoc 明确承诺编排层 acquire/release 全路径配对，本发现即该承诺的违反点。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（cra-index WI 1.1–3.2 零同站点）。release 路径穷举回码证实：teardown / handleStreamFailure L418 / handleStreamComplete L470 / onSubscribe 取消分支（L510-513 确认只 cancel 不回滚）/ 同步 catch 五路径中仅 onSubscribe 分支漏释放；4 进入点同病证实；javadoc L34 幽灵计数族引用准确。与 C1-008 同站点合并；WI 3.1 MA-01 同构面（取消传播到 publisher 生命周期）为不同站点不同失效面，不重复立项，修复批次对齐

### [C2-002] StreamSubscription 监视器内调用下游 Subscriber 未知代码——锁扩展与投递/demand 互相卡位

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:353-405, 184-202, 465-496`
- **证据片段**:
  ```java
  private void bufferOrForward(AttemptState state, ChatStreamChunk item) {
      synchronized (this) {                    // L354：进入监视器
          ...
          deliver(state, item);                // L367：临界区内调用
      }
  }
  private void deliver(AttemptState state, ChatStreamChunk item) {
      ...
      subscriber.onNext(item);                 // L378：下游未知代码在锁内执行
  }
  ```
- **严重程度**: P2
- **并发场景**: delegate HTTP 线程（`onNext/onError/onComplete` 信号进入 `bufferOrForward/handleStreamComplete/deliverError`）与下游订阅者按 Reactive Streams 规范允许的任意线程 `request(n)`（`StreamSubscription.request` L192 需同一监视器）。
- **触发时序**: T1（HTTP 线程）在 L354 持监视器 → `deliver` → L378 `subscriber.onNext(item)`——下游实现（网关 SSE 中继/缓冲层）阻塞在其自身背压点（慢客户端写、有界队列 put），监视器被下游任意用户代码持有时长不定（秒~分钟级）。T2 同期从另一线程调用 `request(n)` 补充 demand → L192 `synchronized(this)` 阻塞，直到 T1 的 onNext 返回。交叠后果：投递与 demand 管理被下游未知代码的耗时串行化；若下游 onNext 实现在自己的锁 Lx 内跨线程回调本订阅（或下游内部再进入本 flow），可构成 `StreamSubscription.this → 下游 Lx → StreamSubscription.this` 潜在环（全仓库具体环未定位 → 按口径标注理论风险）。
- **现状**: 下游终态/数据信号共 4 个锁内外部调用点全部在监视器内：`deliver`→onNext（L378）、`flushBuffer`→onNext（L392）、`flushAll`→onNext（L403）、`handleStreamComplete`→onComplete（L481）、`deliverError`→onError（L494）。同时 `handleStreamComplete` 临界区内还调用 `CircuitObservation.recordSuccess`（→ ThresholdBreaker entry monitor，二级嵌套）与 metrics 记录。
- **风险**: 慢下游把锁持有时间放大为下游处理时长；`request()` 与终态处理在锁上排队，锁竞争随负载上升；极端下游实现下存在未定位的死锁环。Reactive Streams 惯例（onX 信号路径不得长时间持锁调用外部代码）被系统性违反。
- **建议**: 临界区只做状态机变更（buffer/demand/forwarded 判定 + 元素摘除），`subscriber.onX` 移出监视器后调用（先在锁内 `poll()` 摘元素、记 `forwarded`，出锁后投递）；或引入单线程执行器串行化信号（对齐平台 `io.nop.commons.concurrent.executor.StandardThreadPoolExecutor`），监视器仅保护状态。
- **失效面枚举**（F3：锁内外部调用点逐一登记）:
  1. `bufferOrForward`→`deliver`→onNext（L378）→ **已立案**；
  2. `request`→`flushBuffer`→onNext（L392）→ **同病未修**；
  3. `handleStreamComplete`→`flushAll`→onNext + `onComplete`（L481）→ **同病未修**；
  4. `deliverError`→`onError`（L494）→ **同病未修**；
  5. 对照 `subscribe()`（L82-83）：`subscriber.onSubscribe`/`start()` 刻意在 `subscribeLock` 外调用——同文件已有正确先例，上述 4 点与其自身规范不一致。
- **信心水平**: 确定（锁内调用外部代码为代码事实；死锁环部分为理论）
- **误报排除**: 不属"原语使用不是问题"校准——synchronized 本身无错，错在临界区边界吞入下游回调；也非低频管理路径（流式网关主路径逐元素执行）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现；4 个锁内外部调用点回码证实（deliver L378 / flushBuffer L392 / flushAll+onComplete L481 / deliverError L494）+ subscribe L82-83 锁外正确先例对照成立；与 C2-009 同族不同监视器（StreamSubscription vs BufferedSubscription），修复同源（临界区收窄），两监视器同批修复

### [C2-003] FeishuConnector.start/stop 在 this 监视器内执行长连接建立/拆除网络 I/O——挂死传染停机路径

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:172-217`
- **证据片段**:
  ```java
  public synchronized void start(ChannelConnectorContext context) {   // L173
      ...
      feishuClient.start(credentials, this);   // L200：监视器内——凭证解析(可能远程
  }                                            // credential store) + getStreamEndpoint HTTP + WS connect
  @Override
  public synchronized void stop() {            // L206
      ...
      feishuClient.stop();                     // L212：监视器内网络拆除
  }
  ```
- **严重程度**: P2
- **并发场景**: 启动/管理线程（`startAll`→`start`）与停机线程（shutdown hook / admin `stopAll`→`stop`）；`onMessage` 回调线程不取该监视器（无锁序交叉，已验证）。
- **触发时序**: T1 管理线程进入 `start()` → L200 `feishuClient.start`（其自身 synchronized）内部执行 `httpApi.getStreamEndpoint`（HTTP，FeishuClient.java:199）与 `transport.connect`（WebSocket 握手，FeishuClient.java:200）——网络黑洞/对端无响应且未显式配置连接超时时阻塞分钟级以上，T1 全程持有 FeishuConnector 监视器。T2 停机线程调用 `stop()` → L206 `synchronized(this)` 无限期阻塞 → 优雅停机挂死；且 `started` 仍为 false，连接语义上未建立，恢复手段仅剩 kill。
- **现状**: 监视器互斥语义本意是防并发重复 start（L174-177 守卫），但把无超时上界的网络 I/O 整体包进临界区；`dispatchTimeoutMs` 类超时机制在本路径不存在（F5：等待点无超时、挂死不可检测）。
- **风险**: 网络抖动/Feishu 端点异常时启动挂死 + 停机挂死双重传染；容器化环境下表现为 liveness 探针失败后强制 kill，干净关闭语义失效。
- **建议**: 监视器内仅做守卫判定与字段赋值（`context`/`botOpenId`/`started`），`feishuClient.start/stop` 移出临界区（以 `AtomicBoolean` CAS 保证单次启停）；或先在锁外完成连接建立、成功后锁内置位 `started`。
- **失效面枚举**（F3：本模块"监视器内外部 I/O"同族全部站点）:
  1. `FeishuConnector.start`→`feishuClient.start`（L200）→ **已立案**；
  2. `FeishuConnector.stop`→`feishuClient.stop`（L212，transport.close 网络拆除）→ **同病未修**；
  3. `ChannelMessageServiceImpl.ensureBridgeSubscribed`→`messageService.subscribe`（DB/Kafka 实现下为网络 I/O）→ 已并入 C2-006(c)；
  4. 对照 `tryAcquireInbound`（L467-474）：锁内纯内存操作 → 无此缺陷；
  5. 模块外同模式：`FeishuClient.start/stop` 自身 synchronized 包网络 I/O（nop-integration-feishu，越界不立案，留痕）。
- **信心水平**: 确定（锁内 I/O 为代码事实；无超时依赖 FeishuClient 的 HttpClient 配置，模块内未配置即默认无界）
- **误报排除**: 不属"低频管理路径粗粒度 synchronized 合理"校准——报的不是粗粒度优雅性，而是临界区内无上界阻塞 I/O 使互斥点退化为挂起点；亦非平台原语内部实现（调用方式在本模块内）。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：投产即回升)｜新发现；锁内网络 I/O 代码事实证实（FeishuClient.start 内 getStreamEndpoint HTTP + transport.connect WS 回码，FeishuClient 自身亦 synchronized 包裹同模式，模块外留痕成立）——但 start/stop 唯一入口为 startAll/stopAll，全仓零生产调用（grep 证实），触发面休眠；按 WI 3.2 零生产接线先例条件化降档，生命周期投产接线即回升 P2

### [C2-004] ChannelConnectorManager 生命周期零互斥——并发 startAll/stopAll 交叠致漏停（连接泄漏）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelConnectorManager.java:42-44, 100-120`
- **证据片段**:
  ```java
  private final List<IChannelConnector> started = new ArrayList<>();   // L44：零锁
  public void startAll(ChannelConnectorContext context) {
      ...
      for (IChannelConnector c : connectors) {
          c.start(context);
          started.add(c);                       // L107
      }
  }
  public void stopAll() {
      for (int i = started.size() - 1; i >= 0; i--) {
          started.get(i).stop();                // L116-117：迭代期间无任何互斥
      }
      started.clear();                          // L119
  }
  ```
- **严重程度**: P2
- **并发场景**: 停机线程（shutdown hook `stopAll`）与重启/管理流程线程（`startAll`）；`register/lookup/getConnectors` 同容器零锁（登记入失效面）。
- **触发时序**: T1 停机线程执行 `stopAll`：读 `started.size()=2`（[A,B]），开始逆序迭代并 stop B。T2 管理线程并发执行 `startAll(ctx)`：`c.start(C)` 完成 → L107 `started.add(C)`（list 变 [A,B,C]，ArrayList 扩容写与 T1 的 `get`/`size` 读并发）。T1 循环按迭代开始时的 i=1,0 继续 stop B、A 后退出 → L119 `started.clear()` 把 C 一并抹除。交叠后果：C 已 start 但从未 stop——其下 Feishu 长连接（`FeishuClient` 心跳调度器、WS 连接）在"stopAll 完成"之后仍存活，优雅停机语义静默破坏；若时序更差，T1 `get(i)` 与 T2 `add` 触发 ArrayList 并发修改可抛 `IndexOutOfBoundsException`/脏读使 stopAll 中断，剩余连接全部漏停。
- **现状**: 该类全部 5 个公开方法（`setConnectors/register/lookup/getConnectors/startAll/stopAll`）对三个可变容器零同步；`getConnectors()` 返回活列表的 unmodifiable 视图，外部迭代与 register 写并发即 CME 面。
- **风险**: 停机期连接/心跳线程泄漏（daemon 缓解 JVM 退出但容器内 reload 场景连接真实残留）；管理操作中途异常使 `started` 与实际启动集永久失配（后续 stopAll 漏停或重复 stop→`FeishuConnector.stop` 幂等可吸收，但失配不可自愈）。
- **建议**: `startAll/stopAll/register` 加 `synchronized` 互斥（管理路径粗锁符合本仓库校准）；`stopAll` 先在临界区内快照 `new ArrayList<>(started)` 并 `clear()`，锁外逐个 stop（异常不阻断其余连接的停止 + 失败连接重新登记）；`getConnectors()` 返回防御性拷贝。
- **失效面枚举**（F3：同容器全部方法逐一登记；与 C1 可见性条目互斥不重复）:
  1. `startAll`↔`stopAll` 交叠（L105-119）→ **已立案**（互斥缺失面）；
  2. `register`/`setConnectors` 与 `lookup` 并发：LinkedHashMap put/get 交叠读出不一致——注册仅限启动期（IoC collect-beans），低频 → 不立案，留痕；
  3. `getConnectors()` 活视图外泄（L91-93）→ 与 1 同根（零互斥），修复 1 时一并快照化；
  4. 同族兄弟站点 `ChannelLoginApiBizModel.providersByType`（LinkedHashMap，同零锁形态，`loginByScanAsync` 请求路径读）——同 C1 可见性口径，启动期写后只读 → 不立案，留痕；
  5. C1 已立案条目（可见性面）→ 本条不覆盖、不重复。
- **信心水平**: 确定（交叠序列可完整写出；概率受生命周期操作低频约束）
- **误报排除**: 不属"低频管理路径粗粒度 synchronized 合理"——该校准豁免的是"有锁但粗"，此处是"应锁而零锁"且后果为资源漏停；与 C1 的边界见范围节声明。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：投产即回升)｜新发现；交叠时序代码事实证实（started ArrayList 零互斥 + stopAll 尾部 clear 误删并发 add）——但 startAll/stopAll 全仓零生产调用（grep 证据：非测试代码仅 nop-orm-migration OrmMigrationOrchestrator.stopAll 同名词异类），交叠场景当前不可达；条件化降档，生命周期投产接线即回升 P2；lookup 面 live（nop-datav sendToUser）与 C1-007 边界维持

### [C2-005] 会话映射 find-then-create 跨请求无互斥——并发首聊产生双引擎会话 + 唯一键冲突被 WARN 吞掉

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:310-325, 404-411`；`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelSessionStoreImpl.java:59-98`
- **证据片段**:
  ```java
  // FeishuConnector.onMessage（事件线程 A/B 可并发进入同一 chatId）
  session = sessionStore.findByChannel(CHANNEL_TYPE, chatId);   // L313：check
  ...
  boolean isNewSession = existingSessionId == null;             // L318
  ...
  future = context.getAgentEngine().execute(request);           // L345：act（秒级 LLM 窗口）
  // onExecutionComplete 回调：
  if (isNewSession && result.getSessionId() != null) {
      sessionStore.saveMapping(...);                            // L407：第二个写入撞唯一键
  ```
- **严重程度**: P2
- **并发场景**: Feishu Stream 两条事件线程为**同一 chatId** 的两条消息（用户首聊连发）并发进入 `onMessage`；会话映射表尚无该 chat 行。
- **触发时序**: T1/T2 分别处理消息 m1/m2：都执行 L313 `findByChannel` → 均返回 null（映射未落库）→ 均 `isNewSession=true` → 均以 `existingSessionId=null` 调 `engine.execute` → 引擎并行创建会话 S1、S2（LLM 执行秒级窗口，TOCTOU 窗口极宽）。T1 回调先 `saveMapping(S1)` 成功；T2 回调 `saveMapping(S2)` 违反 `uk_nop_ai_channel_session_channel`（nop-ai.orm.xml:1461-1464 unique(channelType,channelId)）→ 异常被 L408-410 catch 后仅 LOG.warn。交叠后果：一次对话分裂为两个并行引擎会话；m2 的回复基于 S2 生成，但持久化映射=S1，后续消息全部路由 S1——上下文静默错位 + S2 孤儿会话 + 唯一键冲突告警噪音。
- **现状**: check（findByChannel）与 act（execute→saveMapping）之间以一次 LLM 往返为窗口，无 per-key 互斥、无 insert-if-absent 语义、无冲突后复查；平台已有 `io.nop.commons.concurrent.lock.IResourceLockManager` 未用。
- **风险**: 首聊高频连发场景（真实用户行为）下会话上下文错位；WARN 吞冲突使错位不可见（F9 静默吞异常面）；孤儿会话在引擎侧累积。
- **建议**: store 层提供原子 `saveMappingIfAbsent`（依赖唯一键 upsert/insert-on-duplicate + 冲突时回读现存映射并返回实际 sessionId），或编排层以 `IResourceLockManager` 按 `(channelType, chatId)` 加锁覆盖 find→execute→save 全程（后者锁跨 LLM 调用不推荐）；至少将唯一键冲突从 WARN 升级为可观测事件并回读映射以对齐后续路由。
- **失效面枚举**（F3：IChannelConnector 契约的全部实现路径）:
  1. `FeishuConnector.onMessage`/`onExecutionComplete`（L313/L407）→ **已立案**；
  2. `IChannelConnector` 其余实现（dingtalk/wecom/webhook/api）：当前仓库内无其他实现类（接口 javadoc 列为扩展点）→ 新实现将按同契约复现，修复应落在 `IChannelSessionStore` 契约层；
  3. `updateLastActive`（L108-118）：load+modify+update 单 OrmSession，非首聊路径无 check-then-create 面 → 无此缺陷；
  4. 消息级重投去重（idempotencyKey，L336）：只防同一 message_id 重投，不防不同消息并发首聊 → 不构成本条的对冲。
- **信心水平**: 很可能（交错与唯一键行为均实证；实际发生率取决于用户连发行为与引擎创建会话的时点）
- **误报排除**: 非无状态单例并发调用（缺陷在跨请求共享的 DB 行生命周期）；非已声明边界——接口 javadoc 明确"miss → sendMessage → saveMapping"流程，未声明并发首聊语义，且表设计（唯一键）表明单一映射是意图契约。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：投产即回升)｜新发现；find→execute→saveMapping 无互斥（LLM 秒级窗口）+ 唯一键冲突 WARN 吞掉回码证实；与 C1-005/C6-004 同链三视角（C6-004 P1 为链主登记、含非并发场景 A——提示 C6 复核员场景 A 同受连接器零接线休眠面约束）；onMessage 触发面随生命周期休眠；条件化降档，投产接线即回升；唯一键实际位于 nop-ai/model/nop-ai.orm.xml:1462-1465（引文 1461-1464 微偏勘误）

### [C2-006] ChannelMessageServiceImpl 复用单一 this 监视器承载三种职责——DCL 缺 volatile、close 与惰性创建竞态、监视器内外部订阅 I/O

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:113-115, 216-252, 477-491`
- **证据片段**:
  ```java
  private ExecutorService ownedFanOutExecutor;          // L115：非 volatile
  ...
  ExecutorService owned = this.ownedFanOutExecutor;     // L221：无同步读
  if (owned == null) {
      synchronized (this) {                             // L223：DCL 缺 volatile
          ...
          this.ownedFanOutExecutor = owned;             // L229
      }
  }
  public void close() {                                 // L241：与创建路径无互斥
      ExecutorService owned = this.ownedFanOutExecutor; // L242
      if (owned != null) { ... shutdownNow(); ... this.ownedFanOutExecutor = null; } // L250
  }
  ```
- **严重程度**: P2
- **并发场景**: (a) 两个传输线程并发首派发（`dispatchInbound`/bridge 回调）走 `resolveFanOutExecutor` DCL；(b) IoC stop 线程 `close()` 与迟到的首派发线程；(c) mode-2 首订阅线程（持 `this` 调 `messageService.subscribe`）与依赖 `this` 快路径的其他派发线程。
- **触发时序**: (a) T1 L221 读 null → 进监视器创建；T3 稍后 L221 无同步读——`ownedFanOutExecutor` 非 volatile，理论可见非完全发布实例（ThreadPoolExecutor 关键字段 final 缓解实际损害 → 按口径标注理论风险，未具体化）。(b) T1 在 L221 读 null 后、L229 赋值前，T2 执行 `close()`：L242 读 null → no-op 返回；T1 随后完成创建——池在 close 之后才诞生，`shutdownNow` 永不执行，且 IoC stop 后新派发仍会复活该池（生命周期违约）。(c) T1 mode-2 首派发 `ensureBridgeSubscribed`（L484 synchronized）持 `this` 调 `messageService.subscribe`——注入 DB/Kafka 实现时为网络 I/O 且不受 `dispatchTimeoutMs` 约束（超时只包住其后的 `sendAsync` 等待，L349-351）→ T2 并发首派发的 fan-out 线程在 L223 阻塞于同一监视器，阻塞时长 = subscribe 时长（broker 挂死时无界）。
- **现状**: 单一 `this` 监视器同时承担惰性执行器创建、桥订阅串行化两职责，且 `close()`（生命周期入口）游离在互斥之外；`dispatchTimeoutMs` 的有界性承诺（类 javadoc L96-103 "never block the transport thread indefinitely"）不覆盖 subscribe 段。
- **风险**: (a) 理论性不安全发布；(b) 关停后线程池复活/永不关闭（daemon 线程缓解但不消除）；(c) 慢消息总线实现下首派发窗口冻结 inbound 派发路径，违背类内已声明的有界派送契约（F5：subscribe 等待点无超时、不可检测）。
- **建议**: `ownedFanOutExecutor` 加 `volatile`；(b) `close()` 与创建路径同锁（或 `AtomicReference` CAS 置空 + 创建侧 CAS 失败即放弃创建）；(c) `subscribe` 移出监视器（用 `subscribedTopics` CAS `add` 返回值替代 synchronized 双检——`newKeySet().add` 本身原子，subscribe-before-record 顺序以"先 subscribe 后 add"保持，重复 subscribe 由幂等容忍或按订阅对象记录）。
- **失效面枚举**（F3：本监视器全部临界区/共享字段逐一登记）:
  1. `resolveFanOutExecutor` DCL（L221-233）→ **已立案**(a)；
  2. `close()`（L241-252）与创建路径竞态 → **已立案**(b)；
  3. `ensureBridgeSubscribed` 持锁外部 I/O（L484-490）→ **已立案**(c)；
  4. 对照 `subscribedTopics`（L135 CHM keySet）与 `inboundListeners`（L265 COW）：读写路径全安全 → 无缺陷；
  5. `dispatchTimeoutMs` volatile（L105）→ 无缺陷。
- **信心水平**: (b)(c) 确定，(a) 理论风险未具体化（按口径降档合并）
- **误报排除**: 不属"有界声明存在的池"校准——问题不在池容量而在生命周期互斥与发布语义；(a) 部分如实标注理论风险而非夸大为实际故障。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：投产即回升)｜新发现；三面代码事实回码证实——(a) DCL 缺 volatile（TPE 关键字段 final 缓解实害，初审已如实标注理论风险）、(b) close/创建无互斥、(c) ensureBridgeSubscribed 锁内 subscribe；但 dispatchInbound 与 close() 全仓零生产调用（grep 证实），三面触发路径均休眠（(c) 仅经 dispatchInbound 可达）；条件化降档，fan-out 路径投产接线即回升 P2；与 C1-004/CL-002 单工单合并

### [C2-007] FeishuConnector.getCapabilities 惰性初始化无锁无 volatile——不安全发布（潜伏 SPI 缺陷）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:115-119, 157-170`
- **证据片段**:
  ```java
  private ChannelCapabilities capabilities;      // L117：非 volatile
  ...
  public ChannelCapabilities getCapabilities() { // L158
      if (capabilities == null) {                // L159：check-then-act，无锁
          capabilities = new ChannelCapabilities();
          capabilities.setSupportsMarkdown(true);
          ...
          capabilities.setRateLimitPerMinute(rateLimitPerMinute);   // L167
      }
      return capabilities;                       // L170：可能发布半初始化对象
  }
  ```
- **严重程度**: P3（当前无生产消费方，潜伏登记）
- **并发场景**: 潜在——任意两个线程并发首调 `getCapabilities()`（当前仓库内无生产调用点，接口 javadoc `IChannelConnector.java:44-58` 明示 RESERVED/NO production consumer）。
- **触发时序**: T1 通过 L159 check 后正在执行 set 系列；T2 同时通过 check（`capabilities` 仍 null）→ 双实例创建（良性）；更重交错：T1 完成赋值但字段写未对 T2 可见（无 volatile/fence）→ T2 于 L170 读得非 null 引用但对象字段处于默认值（`supportsGroupChat=false`、`maxMessageLength=0`、`rateLimitPerMinute=0`）。交叠后果（一旦有消费方）：能力误判为"全不支持/长度 0"。
- **现状**: 教科书式无同步惰性初始化；同一对象在 `start()` 后只读，理想修法是初始化提前或 final。
- **风险**: 当前零触达面，无运行时风险；SPI 激活后升级为消费方可见的能力错判。
- **建议**: 在 `start()` 末尾（已持 `this`）预构建 `capabilities` 并赋 final 字段；或字段改 `private volatile ChannelCapabilities capabilities` + 静态工厂构建后单次赋值。
- **失效面枚举**（F3）：本类唯一惰性初始化字段即为 `capabilities`；`context/botOpenId/started` 均 volatile 且在 `start()` 单点写入 → 无同族兄弟；其余 connector 实现当前不存在。
- **信心水平**: 确定（模式缺陷确定；后果依赖未来消费方）
- **误报排除**: 不按"低频路径"豁免——报的是模式缺陷并按请求触达面口径如实降档 P3；双实例良性分支已在时序中区分，未夸大。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现；零生产消费方 grep 证实（IChannelConnector.getCapabilities 全仓仅接口声明 + javadoc 引用）；与 C1-006 同站点，本条时序区分（双实例良性分支 / 半初始化分支）更完整，合并裁定的主登记

### [C2-008] AttemptCancelToken.appendOnCancel 与 cancel 的 check-then-add 竞态——取消任务可被静默丢失（现接线被双重守卫掩蔽）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:97-128, 308-317`
- **证据片段**:
  ```java
  public void appendOnCancel(Consumer<String> task) {
      if (cancelled.get()) {            // L109：check
          task.accept(reason);
      } else {
          tasks.add(task);              // L112：与 cancel() 的 iterate 无原子性
      }
  }
  void cancel(String reason) {
      if (cancelled.compareAndSet(false, true)) {
          this.reason = reason;
          for (Consumer<String> task : tasks) { task.accept(reason); }   // L124-126
      }
  }
  ```
- **严重程度**: P3（模块内现存调用点后果被掩蔽；原语级缺陷）
- **并发场景**: 注册线程（`registerCallerListener` 或外部 delegate 对 attempt token 注册取消钩子）与取消线程（`teardownCurrentAttempt`→`token.cancel`）。
- **触发时序**: T1 在 L109 读 `cancelled==false` → T2 `cancel()` CAS 成功并迭代 `tasks`（T1 的任务尚未加入）→ T1 L112 `tasks.add(task)`——任务挂在一个已取消且永不再次迭代的 token 上，静默丢失。模块内唯一注册点 `registerCallerListener`（L308-317）的丢失后果被掩蔽：注册先于首 attempt，丢失即意味着取消发生在首 attempt 之前，后续 `startAttemptWithOptions` L238 `isCancelled` 检查使 flow 走 `deliverError` 干净终止，无需该监听器。
- **现状**: 原语把"检查-注册"与"CAS-迭代"留在无原子区；丢失面依赖调用方注册时序自律。attempt token 还会交给模块外 delegate（`callStream(request, state.token)`）注册 HTTP 断连钩子——若 delegate 注册与 teardown 并发，断 HTTP 钩子可丢（缓解：teardown 顺序先 `up.cancel()` 已提供独立中止路径）。
- **风险**: 当前模块内无实质后果；未来调用方（或 delegate 实现）在 token 生命周期中途注册关键清理任务时，取消清理静默丢失 → F8 资源清理不对称族。
- **建议**: 注册改经同步区（synchronized + 双检 cancelled）或在 CAS 失败后二次检查 `cancelled` 补跑任务（`if (cancelled.get()) task.accept(reason)` 兜底），与平台 `ICancelToken` 既有实现语义对齐。
- **失效面枚举**（F3）：`appendOnCancel` 全部调用点——1. `registerCallerListener` L311（模块内唯一，后果被 L238 前置检查掩蔽）→ 已核对；2. attempt token 的模块外注册（nop-ai-core/nop-ai-agent delegate 内部，越界只留痕）；3. `removeOnCancel`（L117-119）当前模块内零调用 → 无症状。
- **信心水平**: 确定（竞态存在确定；后果掩蔽判定确定）
- **误报排除**: 如实按"后果被守卫掩蔽"降档 P3，未按通用最佳实践报 P1；CopyOnWriteArrayList 本身的使用不在指控范围，指控的是其外的复合时序。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现；与 C1-003/C3-002 同站点三登记；本条"模块内现存调用点后果被双重守卫掩蔽"经回码证实并精确化（registerCallerListener L226 → startAttemptWithOptions L238 同线程保序：丢任务必蕴含 cancel 已完成 → L238 必然 deliverError 干净终止）；外部面经 ChatServiceImpl:210 appendOnCancelTask 接线证实，后果由 WI 3.1 MA-01 族承载；三登记合并为单一工单，本条判级口径为最精确

### 数量结论（人工复现，口径纪律第 10 条）

全量发现 **8 条**：P0×0、P1×1（C2-001）、P2×5（C2-002…006）、P3×2（C2-007…008）。负验证（审读后确认无缺陷并留痕）：ConcurrencyRegistry compute 原子区（轻操作/无 I/O/无递归/归零收缩互斥完备）、rateLimitLock 互斥与有界性、GatewayStreamingLifecycleListener 全 synchronized 覆盖、CircuitObservation 近似观测（已声明契约边界，不立案）、inboundListeners/subscribedTopics 并发容器用法、`FeishuConnector.this` 与 `rateLimitLock` 无嵌套（锁序图无环）。基线 C2b 两处 `compute` 均为良性用法。

## 维度集外线索（overlay C5/C9）

### [nop-ai-gateway-CL-003] FailoverStreamFlow 越窗后 deliver 零 demand 无界缓冲（C5/C8）

`FailoverStreamFlow.java:153, 353-382, 516`：`AttemptSubscriber.onSubscribe` 对上游 `request(Long.MAX_VALUE)`（L516），越窗后 `deliver` 在 `demand<=0` 时无条件 `state.buffer.add(item)`（L379-381），`flushBuffer/flushAll` 仅在 `demand>0` 时排水（L389, L400）——下游长时间不补充 demand 的长流（慢 SSE 客户端挂起）下，ArrayDeque 以 HTTP 流到达速率无界增长，直至流自然结束或取消。窗口期有 `getBufferSize()` 上界（L359），越窗后该上界消失。建议评估越窗后缓冲水位上界 + 越界策略（对齐平台 `QueueOverflowPolicy`），或在零 demand 持续时向上游 `upSub.request` 降档为按需。归 C5（无界集合）/C8（队列缓冲）维度立案。

### [nop-ai-gateway-CL-004] fan-out 固定池无界队列 + 每任务占用至 30s——持续过载下积压无界（C9/C4a）

`ChannelMessageServiceImpl.java:226-228, 371-445`：owned fan-out 池为 `newFixedThreadPool(max(2, CPU))`（无界 LinkedBlockingQueue），每个入站消息对每个 listener 提交一个任务，单任务最长占用 `dispatchTimeoutMs`（默认 30s，超时仅中断不回收已排队任务）。持续入站速率 > 池消化速率（如 2 线程 × 慢 listener）时，队列积压随时间线性无界增长（内存 + 派发延迟膨胀），且超时中断只作用于已开始执行的任务，排队任务在超时后才可能被逐个判定超时。建议评估有界队列 + `QueueOverflowPolicy`（fail-loud 或降级），或按积压深度熔断派发。归 C9（长时运行退化）/C4a（线程池配置）维度立案。复核注：该线索的触发路径 dispatchInbound 同样全仓零生产调用（与 C2-006 同休眠面），建议 C9/C4 侧按同一先例条件化校准。

---

## 维度复核结论（WI 3.3 独立复核，合并批 C1+C2）

- **复核日期**: 2026-10-20
- **复核人**: C1+C2 维度独立复核员（与初审/深挖/其他维度复核员相互独立）
- **方法**: 9 条发现的证据行逐条回码、锁序图逐边核验、时序论证逐步核验、严重度三态判定 + 现状标注；降档口径核对（请求触达面不适用启动期降档；休眠面按 WI 3.2 零生产接线先例条件化降档，grep 证据见下）；与 cra-index WI 1.1–3.2 台账逐条查重。

### 逐条判定表（C2-001…009）

| ID | 初判 | 判定 | 终判 | 核心依据 |
|---|---|---|---|---|
| C2-001 | P1 | 保留 | P1 | release 五路径穷举回码，onSubscribe 取消分支确认不回滚计数；4 进入点同病；取消路径资源泄漏、触发链明确 |
| C2-002 | P2 | 保留 | P2 | 4 个锁内外部调用点 + 锁外正确先例对照回码证实；流式主链逐元素执行 |
| C2-003 | P2 | 降级 | P3（条件化） | 锁内 I/O 证实；start/stop 唯一入口 startAll/stopAll 零生产接线 → 休眠 |
| C2-004 | P2 | 降级 | P3（条件化） | 交叠时序证实；startAll/stopAll 零生产调用 → 交叠不可达 |
| C2-005 | P2 | 降级 | P3（条件化） | 无互斥 + WARN 吞冲突证实；同链 C6-004 链主；onMessage 面随生命周期休眠 |
| C2-006 | P2 | 降级 | P3（条件化） | 三面证实；dispatchInbound/close 零生产调用 → 全休眠 |
| C2-007 | P3 | 保留 | P3 | 零消费 grep 证实；与 C1-006 合并（本条为主登记） |
| C2-008 | P3 | 保留 | P3 | 掩蔽判定精确化证实（L238 同线程保序）；与 C1-003/C3-002 三登记合并 |
| C2-009 | P2 | 保留 | P2 | BufferedSubscription 监视器内 retry 全链 + HTTP 发起回码证实；网关形态生产真实（WI 2.3 C3-003 已裁定缓冲层为现实路径） |

### 判定统计（C2 维度）

- **保留×5**：C2-001（P1）、C2-002/C2-009（P2）、C2-007/C2-008（P3）
- **降级×4**：C2-003/C2-004/C2-005/C2-006（全部 P2→P3 条件化，零生产接线）
- **驳回×0**

### 现状标注分布（C2 维度）

- 流式主链 live（LOCAL 形态 opt-in 装配）：C2-001、C2-002、C2-008（外部面）
- 网关形态 live：C2-009
- 渠道栈休眠（连接器生命周期 / fan-out 零接线）：C2-003、C2-004、C2-005、C2-006
- RESERVED 零消费：C2-007

### 降档口径核对（grep 证据留痕）

1. `startAll`/`stopAll` 全仓非测试代码零调用（唯一异类命中为 nop-orm-migration OrmMigrationOrchestrator.stopAll，同名词异类）；beans.xml 无 init-method/destroy-method 接线 → C2-003/C2-004 条件化降档。
2. `dispatchInbound` 全仓非测试零调用；`ChannelMessageServiceImpl.close()` 全仓零调用且无 destroy-method → C2-006 三面全休眠、C2-005 的 onMessage 面休眠（连接器未启动则无入站消息）。
3. 请求触达面（C2-001/C2-002/C2-009 及 C2-008 外部面）不适用启动期降档，维持原判。

### 跨维指针核对

1. **C2-008 = C1-003 = C3-002 同站点**：合并为单一工单（原语修复一处覆盖三登记）；C2-008 掩蔽论证最精确；C3-002 外部面后果链成立（其"upSub.cancel 已切断接收面"一句在该窗口不准——upSub 尚未赋值，up.cancel 为 no-op）；外部消费者 ChatServiceImpl:210 回码证实。
2. **C2-005 = C1-005 = C6-004 同链**：链级以 C6-004（P1）为主登记；本条为并发面；链级终判随 C6 复核对齐。
3. **C2-001 = C1-008 同站点同根**：C2-001 主登记，C1-008 并入（onSubscribe 取消分支补 releaseAttempt 一行闭合两条）。
4. **C2-006(a)(b) = C1-004 + CL-002 同站点**：单工单合并。
5. **C2-002 = C2-009 同族不同监视器**：修复同源（临界区收窄），两监视器同批；C2-009 与 WI 2.3 C3-003（BufferedStreamingPublisher 越窗无界缓冲）同文件不同失效面，修复批次协调不重复计数。
6. **C2-004 ↔ C1-007 同容器异失效面**（互斥缺失 vs 可见性）：边界成立不重复。
7. **WI 3.1 MA-01 同构面**：FailoverStreamFlow（本地形态）vs ChatServiceImpl callStream（远端形态）取消传播轴同构，但站点与失效面不同（本地 registry 计数配对 vs 远端 publisher 生命周期/聚合计），零重复立项；修复批次对齐。
8. **WI 2.1 CL-024**（per-client/topic 池线性累计）：无同案命中（本模块 owned 池为单例生命周期问题，非 per-client 累计）。
9. **F4 无界账本族**：C2 维度无直接立案；关联线索 CL-003（越窗无界缓冲）/CL-004（fan-out 无界队列，本次复核已补注休眠面）归 C5/C8/C9/C4 处置。
10. **锁序图复核**：第 1、2 轮锁序图逐边回码无发现矛盾；新增边（BufferedSubscription → breaker/listener/registry/HTTP）方向一致性核实成立，无环结论维持。

### 高风险条目子项复核说明

- **C2-001（P1）**：release 路径穷举逐条核验——teardownCurrentAttempt（L323-334，仅释放 teardown 时刻的 `attempt`）、handleStreamFailure L418、handleStreamComplete L470、同步 catch（L293-297，仅覆盖 callStream/subscribe 同步抛出）、onSubscribe 取消分支（L510-513 确认仅 `subscription.cancel(); return;`）——确认"取消落在 L235 检查后、L288 装配前"交错下 state2 的 acquire 无任何回滚路径；ConcurrencyRegistry 无 TTL/清理、下溢守卫不触发（无 release 即无下溢异常）→ 静默永久 +1，饱和误判链成立。修复建议（onSubscribe 取消分支补 `releaseAttempt(state)`）覆盖全部交错变体，采纳；结构性修复（取消复查+acquire+装配纳入监视器）作为同批选项。
- **C2-009（P2）**：监视器边界逐段回码——AttemptSubscriber.onError L391-400 持 BufferedSubscription 监视器调 handleError（L298-326），retry() 内 classifyStreamError（loadConfig→VFS）/recordFailure/selectNextWithProbe（探活→VFS）/sinkCandidate（loadConfig×2+resolveApiKey）/buildHttpRequest（loadConfig+toBackendRequest 全量 JSON）逐项与 GatewayStreamingRetryCallback L53-142 对号；重订阅分支 handleError 内 startAttempt（fetchServerEventFlow + subscribe）同锁发起 HTTP 连接核实。冻结时长量级为估算（依赖 VFS 缓存与凭证实现）如实标注，维持 P2。

## 最终保留项（C2 维度）

| 工单面 | 条目 | 终判 | 修复方向 | 合并指令 |
|---|---|---|---|---|
| 取消×重订阅计数泄漏 | C2-001 | P1 | onSubscribe 取消分支补 releaseAttempt（一行）；结构性：取消复查+acquire+装配纳入监视器 | 与 C1-008 并单；与 WI 3.1 MA-01 批次对齐 |
| StreamSubscription 监视器内投递 | C2-002 | P2 | 临界区只做状态机变更，subscriber.onX 移出监视器 | 与 C2-009 同批 |
| BufferedSubscription 监视器内重试全链 | C2-009 | P2 | retry/startAttempt 移出监视器（attempt 引用交换守卫现成）；retry 内配置解析合并 | 与 C2-002 同批；与 WI 2.3 C3-003 同文件协调 |
| 同站潜伏/休眠面 | C2-007 | P3 | capabilities 预构建或 volatile（SPI 接入时回升） | 与 C1-006 合并 |
| 同站潜伏/休眠面 | C2-008 | P3 | appendOnCancel add 后二次复核 cancelled 补跑 | 与 C1-003/C3-002 单工单 |
| 降级保留 | C2-003 | P3 条件化 | feishuClient.start/stop 移出临界区 + AtomicBoolean 单次启停（投产回升 P2） | 独立 |
| 降级保留 | C2-004 | P3 条件化 | startAll/stopAll synchronized + 快照停止（投产回升 P2） | 独立（与 C1-007 守卫同批） |
| 降级保留 | C2-005 | P3 条件化 | saveMappingIfAbsent/upsert + 冲突升可观测（随 C6-004 链；投产回升） | 并入 C6-004 链工单 |
| 降级保留 | C2-006 | P3 条件化 | volatile + close 同锁 + subscribe 移出监视器（投产回升 P2） | 与 C1-004/CL-002 单工单 |
