# C1 共享可变状态与可见性 — nop-ai-gateway（WI 3.3）

- **审计日期**: 2026-10-20
- **审计员**: C1 维度盲审初审员（单批次）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（F1–F11 缺陷族 + C1 维度正文 + 附录 A 格式）

## 审计范围与口径

### 覆盖声明（单批次全源码根）

`nop-ai/nop-ai-gateway/src/main/java` 全部 **36 个 .java 文件 / 6,035 行**逐文件审读完毕（`wc -l` 复核 6,035 总行数一致），无抽样。覆盖包：`failover/`（13 文件）、`routing/`（9 文件）、`channel/`（11 文件，含 `feishu/`）、`login/`（4 文件）、根包（1 文件）。

### 五类载体盘点结论

| 载体 | 盘点结果 |
|------|---------|
| static 可变字段 | **无**（C1a 基线命中全为 `static final` 常量与 static 方法；C1b 节为空） |
| static 非线程安全工具（SimpleDateFormat/Random） | **无**（grep 证实） |
| ThreadLocal | **无**（C1c 基线节为空，grep 证实——本模块无 ThreadLocal 载体） |
| singleton bean 实例可变字段 | 见下方清单表；全部为 IoC 启动期装配、运行期只读，或带同步守卫 |
| 全局注册表/缓存 | `ConcurrencyRegistry`（CHM+compute 原子，裁定正确）；`ChannelConnectorManager`/`ChannelLoginApiBizModel` 启动装配容器（C1-007） |

### 共享可变状态清单表（字段 × 写者 × 读者 × 同步机制 × 判定）

| 站点 | 写者 | 读者 | 同步机制 | 判定 |
|------|------|------|---------|------|
| `FeishuConnector.started/context/botOpenId`（volatile） | start/stop（synchronized） | onMessage/sendOutbound/onExecutionComplete（Feishu transport 线程 + 引擎回调线程） | volatile + synchronized(start/stop) | 复合顺序缺陷见 C1-002 |
| `FeishuConnector.capabilities`（非 volatile） | getCapabilities 惰性初始化 | getCapabilities（当前无生产消费者，RESERVED SPI） | 无 | C1-006（P3） |
| `FeishuConnector.inboundWindow` + `rateLimitLock` | tryAcquireInbound | 同 | synchronized(rateLimitLock) evict+count+add 原子 | 正确 |
| `FeishuConnector.rateLimitPerMinute`（包私有可变） | 测试专用 | tryAcquireInbound（锁内读） | 写在锁外（仅测试） | 启动/测试专用，生产恒默认值，不立案 |
| `ChannelMessageServiceImpl.dispatchTimeoutMs`（volatile） | setter（启动） | dispatchInbound/fanOut | volatile | 正确 |
| `ChannelMessageServiceImpl.ownedFanOutExecutor`（**非 volatile**） | resolveFanOutExecutor / close | 同 + fanOutToListeners | DCL synchronized(this)，但首检/关闭均锁外 | **C1-004（P2）** |
| `ChannelMessageServiceImpl.subscribedTopics`（CHM keySet）+ `ensureBridgeSubscribed` | dispatchInbound（transport 线程） | 同 | CHM + synchronized(this) 双检（subscribe-before-record 锁内保序） | 正确（已有修复注释） |
| `ChannelMessageServiceImpl.inboundListeners` | subscribeInbound | fanOutToListeners | CopyOnWriteArrayList | 正确 |
| `ChannelConnectorManager.connectors/byType/started`（ArrayList/LinkedHashMap） | setConnectors/register/startAll（装配期） | lookup（请求线程）/stopAll | 无运行时守卫 | C1-007（P3，纯启动装配降档论证） |
| `ChannelLoginApiBizModel.providersByType`（LinkedHashMap） | setChannelBindProviders（装配期） | requireProvider（请求线程） | 无 | C1-007 同族，运行期只读，安全 |
| `AiGatewayFailoverInterceptor`/`ChatServiceFailoverAdapter` 实例字段（strategy/breaker/registry/metrics/retryBudget 等） | IoC setter（启动） | 请求线程 | 无（依赖容器装配先于请求） | 启动发布，低频装配路径，按误报校准不立案 |
| `ConcurrencyRegistry.counts`（CHM<Key,AtomicInteger>） | acquire/release（请求线程） | currentCount | CHM.compute per-key 原子临界区 + 归零收缩 | 正确（javadoc 已论证无幽灵计数） |
| `ModelClassRouter.attempted/extendedCandidates` | doSelect | 同 | per-request 实例，javadoc 明示"单线程 per-call 独占" | 正确（声明契约） |
| `FailoverStreamFlow.StreamSubscription.attempt`（volatile）/`demand`（monitor）/`AttemptState.buffer`（ArrayDeque） | 上游 attempt 线程/取消线程/request 线程 | 同 | volatile + synchronized(this) + released CAS | **L438 clear 脱离 monitor：C1-001（P1）**；resubscribe-after-cancel：C1-008（P3） |
| `FailoverStreamFlow.AttemptCancelToken.cancelled/tasks/reason` | 任意线程 | 同 | AtomicBoolean + CoW list | **appendOnCancel check-then-act：C1-003（P2）** |
| `GatewayStreamingLifecycleListener.acquiredProvider/acquiredAccountKey/released` | onFetchStarted/onStreamTerminated | 同 | synchronized 方法 | 正确 |
| `CircuitObservation` getState 前后对比 | 编排层多线程 | — | 无锁读对比 | **owner doc 已声明"近似观测"契约**（nop-ai-gateway.md 指标表），判为已声明边界，不立案 |
| acquire-后-复查（M-6a，count>limit） | 各编排路径 | — | registry 原子 + 跳过语义 | owner doc B-13"预检语义/短暂超限不承诺硬上限"已声明，不立案 |

### 心跳/超时调度器缺席裁定（留痕）

本模块 `src/main/java` 内**无任何调度器/定时器构造**（无 ScheduledExecutorService/Timer/@Scheduled；基线 C4b 节命中的 `FailoverMetricsImpl.requestTimer` 为 micrometer Timer 指标，非调度器）。Feishu 心跳执行器（`FeishuClient.scheduleHeartbeat`，nop-integration-feishu）与 fan-out 超时调度（`CompletableFuture.orTimeout` 底层 Delayer）均**不在本模块源码根内**，按范围纪律不在本维度立案，移交对应模块侧审计。

### 口径纪律声明

- 判级按共享提示词校准；写不出具体 T1/T2 交错的一律降 P2 并标注"理论风险"。
- 已声明边界不计缺陷：CircuitObservation 近似观测、acquire-复查 B-13 预检语义、`DefaultSelectionStrategy` 并发饱和排除（bounded 递归论证：饱和候选被策略跳过，饱和递归有界终止）。
- 会话映射并发插入有 ORM 声明的唯一键 `uk_nop_ai_channel_session_channel` 兜底仲裁（`nop-ai/model/nop-ai.orm.xml:1461`），按"守卫存在"降档处理（C1-005，P3）。
- 盲审纪律遵守：未读 `ai-dev/audits|plans|bugs|lessons|backlog`，未查 git 修复历史。

---

## 发现

### [C1-001] FailoverStreamFlow 窗口内失败清理与 request() 冲刷未同步交错 → 跨 attempt 内容泄漏 / ArrayDeque 数据竞争

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:413-463`（对峙面 `:184-202` `:384-394`）
- **证据片段**:
  ```java
  // handleStreamFailure（上游 attempt 线程执行，L438 不持 monitor）:
  private void handleStreamFailure(AttemptState state, Throwable throwable) {
      boolean inWindow;
      synchronized (this) { inWindow = !state.forwarded; }
      ...
      if (!inWindow) { ... return; }
      // 窗口内失败：丢弃已缓冲元素（Major-1——attempt1 前缀不得进入最终输出），干净重来。
      state.buffer.clear();          // ← L438：唯一一处脱离 StreamSubscription monitor 的 buffer 访问
  ```
  ```java
  // request()（下游消费线程执行，持 monitor）:
  AttemptState current = attempt;              // 失败处理期间仍指向旧 attempt
  if (current != null) { flushBuffer(current); } // flushBuffer 只看 state.passed，不看 attempt 是否已被放弃
  ```
- **严重程度**: P1
- **并发场景**: 上游 attempt 线程（执行 `AttemptSubscriber.onError → handleStreamFailure`）与下游消费线程（`Flow.Subscription.request(n)`，网关 BufferedStreamingPublisher/HTTP 写线程）。
- **触发时序**: T1（上游）进入 `handleStreamFailure`，L414-417 短暂持锁读 `inWindow = !forwarded = true` 后释放锁，进入 `classifyStreamError`（内含 `LlmConfigHelper.loadConfig` 文件 IO，窗口宽）→ T2（下游）此刻调用 `request(5)`：L192 持锁，`attempt` 仍指向旧 state（L288 重赋值未发生），`state.passed == true`（窗口已越、但 demand==0 时元素在 L380 入缓冲，`forwarded` 仍 false——两态可同时成立）→ `flushBuffer` 将 **attempt-1 已缓冲 chunk 全部发给 subscriber** → T1 随后 L438 `clear()` 与 T2 的 `poll()` 无同步并发访问同一 `ArrayDeque` → 交叠后果（二选一）：① attempt-1 前缀在失败决策"重订阅"之后泄漏进最终输出，违反 Major-1 声明的"attempt1 内容不得进入最终输出"透明契约；② ArrayDeque 内部 head/tail 撕裂，`flushBuffer` 的 `poll()` 返回 null → `subscriber.onNext(null)`，下游 NPE。
- **现状**: 窗口内失败的缓冲丢弃（L438）是全文件唯一不持 `synchronized(this)` 的 buffer 访问；`flushBuffer` 以 `passed` 为门控但不校验"该 attempt 是否仍是被认定的 attempt"。
- **风险**: 破坏 owner doc 声明的流式透明边界（§3.5：前端不应感知 attempt 切换）——用户可见两个模型的输出拼接；ArrayDeque 竞态可致 onNext(null) NPE。负载越高（失败窗口与 demand 到达交叠概率越大）触发越频繁。
- **建议**: L438 的 `clear()` 移入 `synchronized(this)` 块；并在 `flushBuffer`/`flushAll` 入口加 `state != attempt` 守卫（与 `AttemptSubscriber.onNext` L521 同款判定），或统一以 monitor 内比较 `attempt` 引用后仅冲刷当前 attempt。
- **失效面枚举（F3）**: `AttemptState.buffer` 全部触点逐一核验——L361 add（bufferOrForward，锁内，OK）；L380 add（deliver，调用链锁内，OK）；L389-393 poll（flushBuffer，调用方 request/bufferOrForward 均锁内，但缺 attempt-identity 守卫，本条修复点）；L400-404 poll（flushAll，调用方 L432/L480 均锁内，OK）；L438 clear（**缺陷站点**）；L492 clear（deliverError，锁内，OK）。同族跨文件：`AiGatewayFailoverInterceptor` 非流式路径无缓冲面，不适用；`GatewayStreamingRetryCallback` 无本地缓冲，不适用。
- **信心水平**: 确定（JMM 层面并发访问无可辩驳；后果分支①②均可具体化）
- **误报排除**: 非"原语使用"误报——ArrayDeque 本身无罪，缺陷是复合操作（清理 vs 冲刷）缺少同一 monitor 归属；也非已声明边界：owner doc 声明的是"缓冲窗口语义"，未声明失败清理路径可与新 demand 交叠。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（cra-index WI 1.1–3.2 台账零同站点）。子项复核全证实：L438 为全文件唯一锁外 buffer 触点；passed=true ∧ forwarded=false 双态可同时成立（demand==0 越窗后 deliver L380 入缓冲）；request()→flushBuffer 无 attempt-identity 守卫，T1/T2 交错完整可达。触达面 = LOCAL 形态 opt-in 装配（nopChatServiceFailoverAdapter beans 全属性就绪、部署按名接线），流式主链 live

### [C1-002] FeishuConnector.start 将投递线程放行置于 started=true 之前 → 启动早期消息被静默丢弃且已 ACK

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:173-203`（对峙面 `:267-271`）
- **证据片段**:
  ```java
  public synchronized void start(ChannelConnectorContext context) {
      if (started) { ... return; }
      ...
      this.context = context;
      this.botOpenId = resolveBotOpenId(context.getConfig());
      ...
      // register this connector as the inbound message handler; FeishuClient
      // delivers parsed FeishuInboundMessage via onMessage / errors via onError
      feishuClient.start(credentials, this);   // ← transport.connect 在此内部完成，读线程已可达 onMessage
      this.started = true;                      // ← L201：投递放行晚于连接建立
  ```
  ```java
  public void onMessage(FeishuInboundMessage message) {
      if (!started) {
          LOG.debug("feishu-connector not started, ignoring inbound message");  // ← debug 级，静默丢弃
          return;
      }
  ```
- **严重程度**: P2
- **并发场景**: T1 = `ChannelConnectorManager.startAll` 启动线程（执行 `FeishuConnector.start`）；T2 = Feishu WebSocket transport 读线程（`FeishuClient` 的 streamListener `onBinary → handler.onMessage`，`nop-integration-feishu/.../FeishuClient.java:339`，连接建立后即可投递）。
- **触发时序**: T1 执行到 `feishuClient.start(credentials, this)`（内部 `transport.connect(...)` 完成，连接上线；此时 `started` 仍为 false）→ T2 读线程收到 Feishu 服务端早到 DATA 帧（断线重连积压/握手后首帧）→ `handler.onMessage(msg)` → `FeishuConnector.onMessage` L268 `!started` → 消息丢弃 → T1 才执行 L201 `started = true` → 交叠后果：该消息**永久丢失**——FeishuClient 在 `handler.onMessage` 返回后按 wire 协议回 ACK（FeishuClient.java:341 起的 P1-6.8-004 逻辑），服务端视为已投递不再重发。
- **现状**: 模块自身在 L297-300 明示 inbound 路径契约"never a silent drop (Minimum Rules #24)"，但启动窗口内的丢弃路径以 debug 日志静默吞掉，且丢弃后仍被上游 ACK。
- **风险**: 启动/重连窗口内的用户消息无痕丢失；与模块声明的 no-silent-drop 契约自相矛盾。触发窗口小（connect 返回到 L201 之间），但重连频繁或积压场景下可复现。
- **建议**: 调整顺序——先 `started = true` 再 `feishuClient.start(...)`（context/botOpenId 已先行赋值，volatile 写序安全）；或 onMessage 未就绪时抛异常/返回失败信号让 FeishuClient 不回 ACK（依赖服务端重投）。
- **失效面枚举（F3）**: `started` 全部触点——`onMessage` L268（**本条缺陷面**）；`sendOutbound` L221（check-then-act：stop 后在途调用继续执行，`feishuClient.sendMessage` 抛错被 deliverSegmented 逐段 catch 记日志，后果=已日志的发送失败，非静默，可接受）；`stop` L207（synchronized 内，OK）；`start` L174（synchronized 内，OK）。同族兄弟：`FeishuClient.started`（nop-integration-feishu，模块外同构形态——其 `transport.connect` 同样先于自身 `started=true`，移交该模块侧 F3 清扫）；`ChannelConnectorManager.startAll`（lifecycle 面无投递语义，不适用）。
- **信心水平**: 确定（FeishuClient.start 源码证实 connect 在 handler 可达前完成；ACK-after-handler 证实丢弃即永久丢失）
- **误报排除**: 非"低频启动路径粗同步合理"——后果是用户消息永久丢失且违反模块自身明文契约（no-silent-drop），非优雅性问题；也非"check-then-act 写不出时序"——T1/T2 交错与 ACK 链路均可具体化。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：投产即回升)｜新发现。机制回码证实（FeishuClient.start 内 handler 登记 + transport.connect 先于外层 started=true；DATA 帧处理后无条件回 ACK，丢弃即永久丢失）——但 startAll/stopAll 全仓零生产调用（grep 证据：`.startAll(`/`.stopAll()` 仅测试树命中，beans.xml 无 init-method/destroy-method 接线），连接器生命周期零接线，触发面休眠；按 WI 3.2 零生产接线先例条件化降档，start 生命周期投产接线即回升 P2

### [C1-003] AttemptCancelToken.appendOnCancel check-then-act：cancel 完成后注册的钩子永久失联（丢失取消传播）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:97-128`（消费面 `:308-317` `:323-334`）
- **证据片段**:
  ```java
  @Override
  public void appendOnCancel(Consumer<String> task) {
      if (cancelled.get()) {          // ← T1 读到 false
          task.accept(reason);
      } else {
          tasks.add(task);            // ← T2 cancel 已完整跑完（遍历时本 task 不在列）→ task 入列后无人再触发
      }
  }

  void cancel(String reason) {
      if (cancelled.compareAndSet(false, true)) {
          this.reason = reason;
          for (Consumer<String> task : tasks) { task.accept(reason); }   // 一次性遍历，无重试/补触发
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: T1 = 注册取消钩子的一方（模块内：`registerCallerListener` L311 在 subscribe 线程挂 caller-token 监听；模块外：delegate `IChatService.callStream` 实现向 per-attempt token 挂 fetch 取消钩子）；T2 = 取消线程（`StreamSubscription.cancel → teardownCurrentAttempt → state.token.cancel` 或 caller token 取消）。
- **触发时序**: T1 执行 L109 `cancelled.get()` 返回 false → T2 执行 `cancel(reason)`：CAS 成功、遍历 `tasks`（此时不含 T1 的 task）结束 → T1 执行 L112 `tasks.add(task)` → 交叠后果：该钩子既未被执行、也永远不会被执行（`cancel` 已消耗掉唯一一次遍历）。模块内后果链：caller-token 监听失联 → 调用方取消无法传播到 attempt → `StreamSubscription.cancelled` 不置位 → 上游 `request(MAX)` 驱动下流式继续拉取、`bufferOrForward` 持续向已放弃的下游投递（demand 耗尽后转入无界缓冲，见 CL-001），直到远端流自然 EOF。
- **现状**: 手写取消令牌的经典"check-then-add"竞态；`removeOnCancel`/`isCancelled` 无问题，仅 append 路径存在丢失窗口。
- **风险**: 取消路径（错误/取消属 P1 判级域）的清理钩子静默失效——HTTP fetch、生命周期监听等挂到 token 上的资源回收动作丢失；窗口为"check 与 add 之间"的纳秒级交错，但长时运行下每次 attempt 订阅都在掷骰子。
- **建议**: append 后二次复核（add 后再查 `cancelled.get()`，为真则自行 `task.accept(reason)` 并从列表移除——CoW list 下安全）；或复用/对齐平台 `io.nop.api.core.util.ICancelToken` 既有语义实现（本类本就是对该接口的手写重复实现）。
- **失效面枚举（F3）**: 模块内 `appendOnCancel` 调用点全量 grep——仅 `FailoverStreamFlow.java:311`（caller-token 监听注册，受本缺陷影响）；per-attempt token 的其余消费者（`delegate.callStream(request, state.token)` L291 的 fetch 钩子挂接）在 nop-ai-core 侧，属同族兄弟移交项。同族对照：`GatewayStreamingLifecycleListener`（synchronized 守卫，正确形态）；平台 `CancelTokenManager`（nop-graphql-core）/`ITaskExecutionState`（nop-core）为平台既有实现参照。模块内无第二个手写 token。
- **信心水平**: 确定（交错序列在 JMM 下必然可达；后果链前半段确定，后半段取决于下游订阅者是否还消费）
- **误报排除**: 非"AtomicBoolean 原语使用"误报——原语本身正确，缺陷在 check-then-act 复合序；非理论不可具体化——T1/T2 三步交错完整写出。
- **复核状态**: 未复核
- 复核状态: 降级(P3)｜新发现；竞态成立。模块内 caller-token 面后果被掩蔽（复核证实：registerCallerListener L226 与 startAttemptWithOptions L238 isCancelled 检查同线程保序——丢任务必蕴含 caller cancel 已完成，L238 必然 deliverError 干净终止，初审"流式继续拉取直到 EOF"后果链对模块内站点不成立）；外部面经 ChatServiceImpl:210 `appendOnCancelTask(subscription::cancel)` 接线证实，其后果（幽灵 fetch 烧到 EOF）由 WI 3.1 MA-01 族承载。与 C2-008、C3-002 同站点三登记——合并为单一工单，站级判级随合并裁定；事实补充：该交错窗口内 upSub 尚未赋值（onSubscribe 未运行），teardown 的 up.cancel 为 no-op——外部面无冗余中止路径，后果链成立且强于初审表述

### [C1-004] ChannelMessageServiceImpl.ownedFanOutExecutor：无 volatile 的 DCL + close() 与 resolve 无共享锁（池复活/不安全发布）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:113-115` `:216-234` `:241-252`
- **证据片段**:
  ```java
  private Executor fanOutExecutor;
  private ExecutorService ownedFanOutExecutor;   // ← 非 volatile

  private Executor resolveFanOutExecutor() {
      Executor injected = this.fanOutExecutor;
      if (injected != null) { return injected; }
      ExecutorService owned = this.ownedFanOutExecutor;   // L221 锁外首检
      if (owned == null) {
          synchronized (this) {
              owned = this.ownedFanOutExecutor;
              if (owned == null) {
                  ...
                  this.ownedFanOutExecutor = owned;        // L229 锁内写
              }
          }
      }
      return owned;
  }

  public void close() {                                     // ← 全程不持 this 锁
      ExecutorService owned = this.ownedFanOutExecutor;
      if (owned != null) { owned.shutdownNow(); ...; this.ownedFanOutExecutor = null; }
  }
  ```
- **严重程度**: P2
- **并发场景**: 多条 transport 入站线程并发首次调用 `dispatchInbound → fanOutToListeners → resolveFanOutExecutor`（mode 1 默认部署的真实热路径）；以及生命周期线程调用 `close()`。
- **触发时序**: ①（不安全发布）T1 在 L223 锁内创建池并 L229 赋值；T2 此刻恰在 L221 锁外读到非 null 引用——写侧在锁内、读侧无锁且字段非 volatile，两条路径间**无 happens-before** → T2 可能见到内部状态未完全发布的 `ExecutorService`（JMM 语义下合法）。②（池复活）T1 resolve：L221 读 null → 进入 L223 锁排队；T2 close：L242 读 null → 直接返回；T1 获锁创建池并赋值 → 交叠后果：close 完成后新建的池无人再能关闭（close 已跑完），泄漏常驻 daemon 线程池。
- **现状**: DCL 形态完整但漏掉 volatile；`close()` 与 `resolveFanOutExecutor` 无共同互斥体。`beans.xml`（`ai-gateway-defaults.beans.xml:108-124`）未挂 destroy-method，生产路径 close 目前无人调用，②暂为潜伏面（测试/手工 close 即触发）。
- **风险**: ①为教科书级 unsafe publication，特定 JIT/屏障组合下首次并发派发拿到半初始化执行器（拒绝执行/状态错乱）；②使 close 语义失效，容器重启场景线程池泄漏。
- **建议**: `ownedFanOutExecutor` 加 `volatile`；`close()` 纳入同一 `synchronized(this)` 并在锁内重检；顺带在 beans.xml 评估挂 `destroy-method="close"`（资源生命周期归 C6 维度，见 CL-002）。
- **失效面枚举（F3）**: 本类双检站点共 2 处——`subscribedTopics` 双检（CHM keySet + 锁内 subscribe-before-record，CHM 写提供发布安全，正确）；`ownedFanOutExecutor` 双检（**缺陷站点**）。同族兄弟（声明移交）：javadoc 自述对齐的 `TeamTaskFlowOrchestrator.ownedSpawnExecutor`（nop-ai-agent，模块外，需在该模块 F3 清扫核对是否同样缺 volatile）；`FailoverStreamFlow`/`GatewayStreamingLifecycleListener` 无惰性池构造，不适用。
- **信心水平**: 确定（②确定；①在 JMM 模型下确定，具体硬件表象依赖实现——按方法论仍为成立的数据竞争）
- **误报排除**: 非"低频路径粗同步合理"——resolve 是每条入站消息的 mode-1 热路径；非"有界声明存在"豁免——问题不在队列上限而在发布安全。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：投产即回升)｜新发现。DCL 缺 volatile + close/resolve 无互斥两形态回码证实（并注：ThreadPoolExecutor 关键字段 final，unsafe publication 实害进一步受限）；但初审"mode-1 热路径"定性需修正——dispatchInbound 全仓零生产调用（fan-out 路径仅测试接线），close() 零调用且 beans.xml 无 destroy-method（CL-002 证实），双触发面均休眠；按零生产接线先例条件化降档，fan-out 路径投产接线即回升 P2

### [C1-005] 会话映射 check-then-act：并发首条消息双引擎会话，唯一键仲裁后败者会话成孤儿

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:310-366`（`ChannelSessionStoreImpl.java:60-98`）
- **证据片段**:
  ```java
  session = sessionStore.findByChannel(CHANNEL_TYPE, chatId);        // T1、T2 均读得 null
  String existingSessionId = session != null ? session.getSessionId() : null;
  boolean isNewSession = existingSessionId == null;                  // 双方都判 true
  ...
  future = context.getAgentEngine().execute(request);                // 双方各自创建新引擎会话
  ...
  if (isNewSession && result.getSessionId() != null) {
      sessionStore.saveMapping(CHANNEL_TYPE, chatId, result.getSessionId(), agentName);  // 盲插
  }
  ```
- **严重程度**: P3
- **并发场景**: 同一 chat 的两条入站消息经 Feishu transport 读线程/引擎回调线程并发进入 `onMessage`（`tryAcquireInbound` 的 javadoc L133 明示"concurrent onMessage invocations"是设计内形态）；后续写方在 `onExecutionComplete` 的 future 回调线程。
- **触发时序**: T1、T2 先后执行 `findByChannel` 均得 null（新 chat 首两条消息并发）→ 双方均以 `existingSessionId=null` 调 `engine.execute`，引擎创建两个独立会话 → 两边回调各自 `saveMapping` → 交叠后果：DB 唯一键 `uk_nop_ai_channel_session_channel`（`nop-ai/model/nop-ai.orm.xml:1461`）使后到者插入抛约束冲突，被 L406-410 的 catch 记 WARN 吞掉；败者引擎会话永久无映射（孤儿会话），后续消息全部绑定胜者会话。
- **现状**: 数据面由 ORM 声明的唯一键兜底（不产生脏双行），但"一个信道会话唯一映射一个引擎会话"的语义注释在并发首消息窗口内不成立（实际创建了两个引擎会话，一个失联）。
- **风险**: 会话上下文割裂——并发首问的两个用户消息得到两个互不知晓的 agent 会话，其一后续不可达；伴随一条约束冲突 WARN。无数据损坏、无泄漏放大（孤儿会话由引擎侧生命周期管理），故 P3。
- **建议**: `saveMapping` 改 upsert（按 `(channelType, channelId)` 冲突转 UPDATE），并在 FeishuConnector 侧对同 chat 的首会话创建做串行化（按 chatId 粒度锁）或接受现态并在 javadoc 声明"并发首消息允许会话分裂"。
- **失效面枚举（F3）**: `saveMapping` 生产调用点全量 grep——仅 `FeishuConnector.onExecutionComplete:407` 一处（无同族漏修）；`updateLastActive`（ChannelSessionStoreImpl:101）miss 即抛，但无删除路径故无 miss 竞态，OK；`ChannelLoginScanProcessor` 的 bind 反查为只读+显式失败，不适用。其他 IChannelConnector 实现仅 FeishuConnector 一个（grep 证实），无第二连接器站点。
- **信心水平**: 很可能（交错确定可达；"孤儿会话不可达"依赖引擎无按 chatId 的会话恢复语义——以当前 store 契约为准）
- **误报排除**: 未按 P2 报"线程不安全"——DB 唯一键是已声明的仲裁守卫，缺陷面收窄为"检查窗口内语义分裂 + 盲插 WARN"，符合"区分缺陷与已声明边界"要求。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现；与 C2-005（并发面）/C6-004（链主登记 P1，含非并发 DB 抖动形态）同链三视角，合并裁定不重复计数；onMessage 触发面随连接器生命周期休眠（零 start 接线）。勘误：唯一键实际位于 `nop-ai/model/nop-ai.orm.xml:1462-1465`（本条引 1461、C2-005 引 1461-1464 微偏，C6-004 引 1466-1470 同偏）

### [C1-006] FeishuConnector.getCapabilities 惰性初始化无同步无非 volatile（unsafe publication；RESERVED SPI 当前无消费者）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:117` `:157-170`
- **证据片段**:
  ```java
  private ChannelCapabilities capabilities;      // 非 volatile

  public ChannelCapabilities getCapabilities() {
      if (capabilities == null) {                // check-then-act 无锁
          capabilities = new ChannelCapabilities();
          capabilities.setSupportsMarkdown(true);   // 先发布引用后填字段——读方可见半初始化对象
          ...
      }
      return capabilities;
  }
  ```
- **严重程度**: P3（降档：当前无生产触发面）
- **并发场景**: 潜在多线程能力探测调用方；当前仓库生产代码对 `getCapabilities()` 的消费为**零**（grep 全仓证实；接口 javadoc 明示 RESERVED，plan 2026-09-14-1937-2 Option B 裁定）。
- **触发时序**: 理论风险，未具体化为现实调用链（无生产写者/读者并存）——若未来接入消费者：T1 首调进入初始化分支，L160 先赋引用、L161-167 后填字段；T2 并发读 `capabilities != null` → 可见 maxMessageLength=0 等默认值（unsafe publication）或重复初始化。
- **现状**: 双重缺陷形态（非原子 check-then-act + 非安全发布）但 SPI 处于显式 RESERVED 状态，无运行时触达。
- **风险**: 未来接入 capability 驱动的降级/权限矩阵时，首次并发探测可能读到 maxMessageLength=0（若下游据此分段，理论可致 `splitIntoSegments` 死循环）——当前仅为潜伏面。
- **建议**: `capabilities` 声明为 `volatile` 并改为 final 字段饿汉初始化（值静态已知，无需惰性）；或按 RESERVED 契约在接入首个消费者时同步修复并加测试。
- **失效面枚举（F3）**: `IChannelConnector` 实现全量 grep——本模块仅 `FeishuConnector` 一个实现，无同族兄弟；`ChannelCapabilities` 的其余 setter 调用点均在测试/本方法内。
- **信心水平**: 确定（缺陷形态确定；触发面当前不存在，故 P3）
- **误报排除**: 未按"看到共享字段就报线程不安全"处理——已核实 RESERVED 裁定并据实降档，登记目的在于未来接入时的回归锚点。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现；零生产消费方 grep 证实（`IChannelConnector.getCapabilities` 全仓仅接口声明 + javadoc 引用；DBMessageService.getCapabilities 为异接口同名词，非消费者）；RESERVED SPI 潜伏登记维持，与 C2-007 同站点合并（C2-007 时序区分更完整）

### [C1-007] ChannelConnectorManager / ChannelLoginApiBizModel 装配容器无启动后守卫（F10 防御性缺口；生产路径判定安全）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelConnectorManager.java:42-44` `:50-79`（`login/ChannelLoginApiBizModel.java:82` `:120-144`）
- **证据片段**:
  ```java
  private final List<IChannelConnector> connectors = new ArrayList<>();
  private final Map<String, IChannelConnector> byType = new LinkedHashMap<>();
  private final List<IChannelConnector> started = new ArrayList<>();
  ...
  public void register(IChannelConnector connector) {   // public，无 started/state 守卫
      ...
      connectors.add(connector);      // 与请求线程 lookup(byType.get) 并发 = LinkedHashMap 结构破坏
      byType.put(type, connector);
  }
  ```
- **严重程度**: P3
- **并发场景**: 写方 = IoC 装配线程（`setConnectors` 经 `<ioc:collect-beans>`，容器启动期单线程）；读方 = 请求线程（`ChannelMessageServiceImpl.sendToUser → lookup` / `ChannelLoginApiBizModel.requireProvider`）。`register` 为 public API，javadoc 自述"may also be registered programmatically"。
- **触发时序**: 理论风险，未具体化生产触发链（现仓库无运行期 register 调用点，全量 grep 证实仅装配/测试）——若运行期调用：T1 请求线程 `byType.get` 遍历桶；T2 `register → byType.put` 触发再哈希 → 读方可见环状结构/丢条目。
- **现状**: 生产形态 = 装配期单线程写 + 运行期只读，按"纯启动装配站点"逐站点论证**判定安全**；缺口仅在 API 面缺少 F10 型 fail-fast 守卫（started 后 register 直接抛错）。
- **风险**: 未来接入管理端点（热注册渠道）时直接踩中非并发容器；当前无生产后果。
- **建议**: `register`/`setConnectors` 加启动完成守卫（`if (startedAll) throw new NopException(...)`），或容器换 `ConcurrentHashMap`（保序需求用键集快照）；`started` 列表同步加守卫。
- **失效面枚举（F3）**: 同族容器全量清单——`ChannelConnectorManager.connectors/byType/started`（3 字段，本条）；`ChannelLoginApiBizModel.providersByType`（同构 LinkedHashMap，同判定）；`ChannelMessageServiceImpl.inboundListeners`（CoW，OK）；`subscribedTopics`（CHM，OK）；`ConcurrencyRegistry.counts`（CHM，OK）。无第五个同族站点。
- **信心水平**: 确定（缺陷形态确定，生产触发面不存在）
- **误报排除**: 未按 P2 上报——遵守降档口径"仅纯启动装配站点逐站点论证"，两站点均已论证为装配期单线程写。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现；lookup 触达面 live（nop-datav NotificationSender 经 IChannelMessageService.sendToUser → ChannelConnectorManager.lookup，grep 证实唯一生产消费者）；register/setConnectors 零运行期调用（装配期单线程写），"装配期写 + 运行期只读安全、缺 F10 守卫"判定维持

### [C1-008] FailoverStreamFlow 重订阅与取消竞态：cancelled 检查通过后仍发起一次新 fetch（onSubscribe 守卫兜底，残余为 callStream 前置开销）——理论风险

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:234-298`（对峙面 `:204-211` `:323-334`）
- **证据片段**:
  ```java
  private void startAttemptWithOptions(ModelClassCandidate fixedCandidate, ChatOptions fixedOptions) {
      if (cancelled.get()) { return; }              // L235：T1 在此读到 false
      if (callerToken != null && callerToken.isCancelled()) { ... }
      ...
      attempt = state;                              // L288：晚于 T2 的 teardown
      try {
          Flow.Publisher<ChatStreamChunk> publisher =
                  adapter.getDelegate().callStream(attemptRequest(sunk), state.token);  // 取消完成后仍发起
          publisher.subscribe(new AttemptSubscriber(state));   // onSubscribe 内 cancelled=true → 立即 cancel
      } catch (Throwable t) { handleStreamFailure(state, t); }
  ```
- **严重程度**: P2（按纪律标注：理论风险，未具体化到用户可见后果——降档记录）
- **并发场景**: T1 = 上游 attempt 线程（窗口内失败 → `handleStreamFailure → startAttemptWithOptions` 重订阅）；T2 = 取消线程（输出侧 `cancel()` 或 caller-token 监听触发 `teardownCurrentAttempt`）。
- **触发时序**: T1 通过 L235 `cancelled.get() == false` → T2 执行 `cancel()`：CAS 置位、`teardownCurrentAttempt` 对旧 state 收尾（release CAS 防双释放，正确）→ T1 继续：select/acquire、L288 `attempt = 新 state`、`delegate.callStream` 发起新 fetch → 交叠后果：新 attempt 的 `AttemptSubscriber.onSubscribe` L510 检测 `cancelled.get()` 为 true → `subscription.cancel()` 兜底取消；残余后果 = callStream 调用至 onSubscribe 之间的一次性连接/映射开销落在已取消订阅上，且 `teardownCurrentAttempt` 不会再看到该新 attempt（attempt 引用在 teardown 之后才赋值，其释放依赖 onSubscribe 路径 cancel）。
- **现状**: 三层守卫（L235/L238 检查、onSubscribe L510 检查、released CAS）使后果收敛于短暂数据面开销；检查与赋值之间存在无法闭合的 TOCTOU 窗口。
- **风险**: 取消路径多付一次候选选择 + 一次 delegate 调用开销；若某 delegate 实现在 `callStream()` 内同步建立 HTTP 连接（实现相关），则该连接存活至 onSubscribe 取消。无计数泄漏（新 attempt 的 release 由 onSubscribe cancel 路径经 teardown… 实际经由 subscriber.cancel 后由 delegate 侧终止回调触发 releaseAttempt，或 subscription 已 cancelled 时 bufferedOrForward 全短路——计数面终态一致）。
- **建议**: 在 L288 `attempt = state` 前后加二次 `cancelled.get()` 复核（为真则立即 releaseAttempt(state) 并 return），闭合窗口；或将 `cancelled` 检查纳入 StreamSubscription monitor 与 teardown 互斥。
- **失效面枚举（F3）**: 同族"每 attempt 前取消检查"站点——本处 L235/L238（流式）；`ChatServiceFailoverAdapter.callAsyncAttempt:195`（非流式，每次递归前检查，同窗口存在但因 whenComplete 链同步完成、无第三方 teardown 线程，竞争面不存在，OK）；`AiGatewayFailoverInterceptor.invokeNonStreamingAttempt`（无 token 检查点，其终止由 whenComplete 链驱动，OK）。
- **信心水平**: 很可能（交错确定可达；用户可见后果未具体化，依纪律降档）
- **误报排除**: 未按"看到共享字段就报"处理——已枚举三层既有守卫并量化残余后果；亦未因守卫存在而漏报（窗口内开销与计数归属仍值得闭合）。
- **复核状态**: 未复核
- 复核状态: 降级(P3)｜新发现；与 C2-001 同站点同根（L235 检查 × L263 acquire × L288 装配 TOCTOU 窗口），计数泄漏这一实质后果由 C2-001（P1）主登记吸收，本条残余面 = 一次性 callStream 前置开销 + onSubscribe 即时取消；并入 C2-001 工单修复（onSubscribe 取消分支补 releaseAttempt 一行同时闭合两条），不独立计数

---

## 维度集外线索（overlay C5/C9）

### [nop-ai-gateway-CL-001] FailoverStreamFlow 越窗后 demand==0 路径无界缓冲（上游 request(MAX) + 慢下游 → OOM 面）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:371-405` `:516`
- **线索**: 上游侧 `subscription.request(Long.MAX_VALUE)`（L516，窗口期保持 demand 的 spike 裁定）使上游永不背压；下游 `deliver()` 在 `demand == 0` 时将元素无限压入 `AttemptState.buffer`（L380 `state.buffer.add(item)`，ArrayDeque 无界）。越窗（`passed == true`）后缓冲门控（`buffer.size() < bufferSize && 时间窗`）**不再生效**——N/T 上限只约束窗口期。慢消费订阅者（HTTP 写阻塞的 SSE 客户端）+ 长流式响应 → buffer 随 token 流无界增长，声明负载包络内即可 OOM。属 C5（内存）/C8（队列缓冲）维度的无界集合族（F7），建议该维度以"demand==0 时的缓冲上限或对上游 request 降档"为核查点复核。
- **置信**: 很可能（机制确定；OOM 取决于下游真实 demand 模型，即 BufferedStreamingPublisher/本地订阅者的请求策略）

### [nop-ai-gateway-CL-002] nopChannelMessageService bean 未挂 destroy-method：ownedFanOutExecutor 生命周期闭环悬空

- **文件**: `nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml:108-124`（对照 `ChannelMessageServiceImpl.java:241-252`）
- **线索**: `ChannelMessageServiceImpl` 提供 `close()` 释放自建 fan-out 线程池，但 `nopChannelMessageService` bean 定义无 `destroy-method="close"`，全模块亦无其他调用点（grep 证实）——生产部署下 owned 池创建后永不关闭（daemon 线程不阻 JVM 退出，但容器热重启/多容器场景线程池与线程常驻泄漏）。属 C6（资源生命周期）/F8（清理不对称：resolve 创建有路径、释放无挂点）。与 C1-004 的 close/resolve 竞态修复联动处理。
- **置信**: 确定（wiring 与调用面已核实）

—— 本维度无其他集外线索。ThreadLocal、static 可变工具、全局缓存 double-checked 惰性初始化（模型缓存类）在本模块源码根内均不存在（grep 留痕见"审计范围与口径"）。

---

## 维度复核结论（WI 3.3 独立复核，合并批 C1+C2）

- **复核日期**: 2026-10-20
- **复核人**: C1+C2 维度独立复核员（与初审/深挖/其他维度复核员相互独立）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` 校准口径；19 条发现的证据行逐条回码、时序论证逐步核验、严重度三态判定（保留/降级/驳回）+ 现状标注；降档口径核对（请求触达面不适用启动期降档；休眠面按 WI 3.2 零生产接线先例条件化降档，逐条附 grep 证据）；与 `ai-dev/audits/cra-index.md` WI 1.1–3.2 台账（含 P1 汇总 79 行）逐条查重。

### 台账查重结论

nop-ai-gateway 为 CRA campaign 首个被审单元（WI 3.1 覆盖 nop-ai-core/nop-ai-agent、WI 3.2 覆盖 nop-ai-toolkit-tools-shell，均不含本模块）。19 条发现与 cra-index P1 台账**零同站点重复立项**。跨单元关系见下方"跨维指针核对"。

### 逐条判定表（C1-001…010）

| ID | 初判 | 判定 | 终判 | 核心依据 |
|---|---|---|---|---|
| C1-001 | P1 | 保留 | P1 | L438 唯一锁外 buffer 触点、passed∧¬forwarded 双态并存（demand==0 越窗 L380）、flushBuffer 无 attempt-identity 守卫——三项子项回码全证实，时序完整可达 |
| C1-002 | P2 | 降级 | P3（条件化） | 机制证实（FeishuClient ACK-after-handler 回码）；但 startAll/stopAll 全仓零生产调用（grep 仅测试树）→ 触发面休眠，投产接线即回升 |
| C1-003 | P2 | 降级 | P3 | 竞态成立；模块内面被 L238 同线程前置检查掩蔽（初审后果链不成立）；外部面后果由 WI 3.1 MA-01 族承载；与 C2-008/C3-002 三登记合并 |
| C1-004 | P2 | 降级 | P3（条件化） | 缺陷形态证实；dispatchInbound/close 全仓零生产调用 + 无 destroy-method → 双面休眠；初审"mode-1 热路径"定性修正 |
| C1-005 | P3 | 保留 | P3 | 与 C2-005/C6-004 同链三视角（C6-004 链主 P1）；orm 唯一键实际 L1462-1465（三处引文微偏勘误）；触发面随生命周期休眠（已 P3，annotate） |
| C1-006 | P3 | 保留 | P3 | 零生产消费方 grep 证实（DBMessageService.getCapabilities 为异接口同名词） |
| C1-007 | P3 | 保留 | P3 | lookup 面 live（nop-datav NotificationSender.sendToUser 唯一生产消费者）；register 零运行期调用 |
| C1-008 | P2 | 降级 | P3 | 与 C2-001 同站点同根，实质后果由 C2-001（P1）主登记吸收；并入同工单不独立计数 |
| C1-009 | P2 | 降级 | P3 | "模块内无显式同步边"事实成立，但 JMM 传递性覆盖该缺口（属性写程序序先于 HTTP 提交，响应投递必然经客户端内部同步移交，读因果后于提交）——具体失效交错需以 nop-http 内部数据竞争为前提，初审未能写出；降格为隐式契约脆弱性（防御性加固候选） |
| C1-010 | P2 | 保留 | P2 | 三代码事实回码证实（adapter 无守卫 / interceptor 守卫在场 / provider null 兜底）；标准装配以非 optional ref 注入不受影响（beans.xml 证实）；装配漂移触发 + 静默全局失效 + 一行守卫，维持 P2（与 C1-007 判级差异：后果为防护整体失效而非结构性潜伏，且 D8 契约已在守卫消息明文声明该失效模式） |

### 判定统计（C1 维度）

- **保留×5**：C1-001（P1）、C1-010（P2）、C1-005/C1-006/C1-007（P3）
- **降级×5**：C1-002、C1-004（P2→P3 条件化，零生产接线）；C1-003、C1-008、C1-009（P2→P3 校准降级）
- **驳回×0**（19 条中无一伪造证据行；全部站点与引文行号真实，三处 ORM 行号微偏已勘误）

### 现状标注分布（C1 维度）

- 流式主链 live（LOCAL 形态 opt-in 装配）：C1-001、C1-003（外部面）、C1-008
- 装配漂移条件面 live：C1-010
- 网关形态 live：C1-009（降档后为防御性加固候选）
- 渠道栈休眠（连接器生命周期零接线）：C1-002、C1-004、C1-005
- RESERVED 零消费：C1-006
- 混合（lookup live / register 装配期）：C1-007

### 降档口径核对（grep 证据留痕）

1. `startAll`/`stopAll` 全仓非测试代码零调用（grep `.startAll(|.stopAll()` 全仓仅测试树 + nop-orm-migration OrmMigrationOrchestrator.stopAll 同名词异类）；beans.xml 无 init-method/destroy-method 接线 → 连接器生命周期休眠 → C1-002 条件化降档。
2. `dispatchInbound` 全仓非测试零调用（唯一调用契约方 FeishuConnector 直连 engine.execute 不经此路径）→ fan-out/桥订阅面休眠 → C1-004 条件化降档。
3. `close()`（ChannelMessageServiceImpl）全仓零调用 → C1-004(b) 面休眠（与 C6 文件对 close 接线的标注一致）。
4. 请求触达面（FailoverStreamFlow 族、gateway 形态）不适用启动期降档，维持原判——WI 2.3 已裁定缓冲流式层为 AI 路由现实路径。

### 跨维指针核对

1. **C1-003 = C2-008 = C3-002 同站点**（AttemptCancelToken.appendOnCancel）：三维度登记合并为单一工单；本复核确认 C2-008 的掩蔽论证（L238 同线程保序）成立、C3-002 的外部面后果链成立（并指出其"upSub.cancel 已切断接收面"一句在该窗口不准——upSub 尚未赋值，up.cancel 为 no-op，后果链反而更强）；外部消费者 ChatServiceImpl:210 已回码证实。
2. **C1-005 = C2-005 = C6-004 同链**（find→execute→saveMapping）：链级以 C6-004（P1，含非并发场景 A）为主登记；提示 C6 复核员：C6-004 场景 A 亦依赖连接器已启动，同样受零接线休眠面约束，建议同先例校准。
3. **C1-008 = C2-001 同站点同根**：C2-001 主登记 P1，C1-008 并入。
4. **C1-004 = C2-006(a)(b) + CL-002 同站点**：单工单合并修复（volatile + close 同锁 + destroy-method）。
5. **C1-006 = C2-007 同站点**：合并，C2-007 时序为主登记。
6. **C1-007 ↔ C2-004 同容器异失效面**（可见性 vs 生命周期互斥）：边界成立，不重复计数。
7. **WI 3.1 MA-01 同构面**（FailoverStreamFlow vs ChatServiceImpl callStream 取消传播）：站点与失效面不同（本地 registry 计数配对 vs 远端 publisher 生命周期），不构成重复立项；C2-001 修复（onSubscribe 补 release）与 MA-01 修复（取消终止信号）应同批对齐，且 MA-01 侧不得假设 AttemptCancelToken 原语无竞态。
8. **WI 2.1 CL-024**（per-client/topic 池线性累计→nop-cluster）：本维度无同案命中——本模块 owned fan-out 池为单例生命周期问题（C1-004/C2-006/CL-002 族），非 per-client 累计，仅池治理轴向邻接。
9. **F4 无界账本族**：本维度无直接立案；族内线索为本文件 CL-001/CL-002、C2 文件 CL-003/CL-004（归 C5/C8/C9/C4 处置）与 C6-004 孤儿会话累积面。
10. **WI 2.3 CL-022**（orTimeout→JDK Delayer 共享面移交 nop-ai/nop-message）：本模块站点为 ChannelMessageServiceImpl L351/L418 两处 orTimeout，归 C4/C9 移交面核对，C1/C2 不立案。

### 高风险条目子项复核说明

- **C1-001（P1）**：逐子项核验——(a) handleStreamFailure L414-417 读 inWindow 后即释放监视器，L438 clear 前存在 releaseAttempt + metrics 两个非同步调用点，窗口真实；(b) `passed=true ∧ forwarded=false` 可同时成立（bufferOrForward 越窗后 demand==0 路径 deliver L380 入缓冲，forwarded 恒 false），初审关键前提证实；(c) flushBuffer（L384-394）仅以 passed 门控、无 `state != attempt` 守卫，而 AttemptSubscriber.onNext L521 有同款守卫——同文件守卫不一致佐证；(d) 后果①（attempt-1 前缀泄漏，违反 Major-1）与后果②（ArrayDeque poll/clear 撕裂 → onNext(null)）均无需额外假设。修复方向（clear 移入监视器 + 冲刷加 attempt-identity 守卫）采纳。
- **C1-010（P2）**：F3 四站点逐一回码（adapter L371-374 无守卫／interceptor L541-553 守卫在场／CandidateHealthProvider L26-29 null 兜底／ModelClassRouter 构造器 L74 传递面）；beans.xml `nopChatServiceFailoverAdapter` 以非 optional ref 注入 breaker/registry/metrics 证实标准装配不受影响；触发面收窄为装配漂移/手工构造/未来重构，静默全局失效模式维持 P2。

## 最终保留项（C1 维度）

| 工单面 | 条目 | 终判 | 修复方向 | 合并指令 |
|---|---|---|---|---|
| 窗口内失败清理与 request 冲刷竞态 | C1-001 | P1 | L438 clear 移入 `synchronized(this)`；flushBuffer/flushAll 加 `state != attempt` 守卫 | 与 C2-001 同文件同批修复 |
| 装配守卫不对称 | C1-010 | P2 | adapter routerFor 复制 D8 fail-fast（一行）；评估 CandidateHealthProvider null 兜底收口 | 独立 |
| 同站潜伏/休眠面 | C1-005 | P3 | saveMapping upsert + 同 chat 首会话串行化 | 并入 C6-004 链工单 |
| 同站潜伏/休眠面 | C1-006 | P3 | capabilities 改 volatile + 饿汉初始化（SPI 接入时回升） | 与 C2-007 合并 |
| 同站潜伏/休眠面 | C1-007 | P3 | register/setConnectors 加启动完成守卫 | 独立 |
| 降级保留 | C1-002 | P3 条件化 | started=true 先于 feishuClient.start（投产接线即回升 P2） | 独立 |
| 降级保留 | C1-003 | P3 | appendOnCancel add 后二次复核（或对齐平台 ICancelToken 实现） | 与 C2-008/C3-002 单工单 |
| 降级保留 | C1-004 | P3 条件化 | volatile + close 同锁 + destroy-method（投产接线即回升 P2） | 与 C2-006/CL-002 单工单 |
| 降级保留 | C1-008 | P3 | 随 C2-001 修复闭合（onSubscribe 补 releaseAttempt） | 并入 C2-001 |
| 降级保留 | C1-009 | P3 | per-attempt 属性快照（不可变对象安全发布）或显式 volatile 栅栏——防御性加固候选 | 独立（7.1 裁决修/deferred） |
