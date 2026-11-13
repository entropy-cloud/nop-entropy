# C6 资源生命周期与泄漏 — nop-ai-gateway（WI 3.3）

- 审计批次：2026-10-20-1302-deep-audit-nop-ai-gateway-concurrency（C6 维度盲审初审，单批次）
- 审计员：C6 维度子代理（盲审：未读 ai-dev/audits/**、ai-dev/plans/**、ai-dev/bugs/**、ai-dev/lessons/**、ai-dev/backlog/**；未用 git log 查修复历史）
- 方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md` F1–F11 缺陷族画像 + C6 维度正文 + 附录 A 十三字段格式

## 审计范围与口径

### 覆盖声明（单批次全源码根，逐文件审读）

- 范围：`nop-ai/nop-ai-gateway/src/main/java` 全部 36 个文件 / 6,035 行，本轮逐一读毕（channel 12、failover 15、routing 8、login 5、根 1——含纯 DTO/常量文件的通读）。
- 资源获取点配对总表（C6 执行步骤 1/5 产出，口径：获取点 → 释放点 → 配对判定）：

| 获取点 | 释放点 | 配对判定 |
|---|---|---|
| `ConcurrencyRegistry.acquire` ×5（ChatServiceFailoverAdapter:207/300、AiGatewayFailoverInterceptor:253、FailoverStreamFlow:263、GatewayStreamingLifecycleListener:44） | 对应 release（whenComplete 全终止路径/同步抛路径/超限复查路径/teardown/lifecycle-listener） | 配对完整度归 C4「并发计数泄漏」已立案面（见边界声明 3）；本表仅留痕 |
| `ownedFanOutExecutor`（ChannelMessageServiceImpl:227 懒建 fixed pool） | `close():244 shutdownNow + awaitTermination(2s)` | 有释放方法但生产 beans 未接线（归 C4-004 面）；close/dispatch 竞态见 C6-003 |
| bridgeConsumer 骨干订阅（`messageService.subscribe`，ChannelMessageServiceImpl:488） | **无 unsubscribe 路径** | 见 C6-003 机理段；`subscribedTopics` 键空间 = channelType 枚举（有界），add-only 本身非泄漏 |
| `inboundListeners`（subscribeInbound add-only，:309） | 无 remove | SPI `IChannelMessageService` 未声明 unsubscribe——add-only 即完整契约，监听者为 singleton bean（进程生命周期），**已声明边界，非缺陷** |
| callerToken `appendOnCancel`（FailoverStreamFlow:311） | 无 removeOnCancel | 流终态后 listener 留存于 per-request token 上，token 随请求回收——有界、终态后触发为无害 no-op（released 守卫），不立案 |
| 会话映射写（`IChannelSessionStore.saveMapping`） | 无删除/TTL API | 见 C6-004 |
| ORM 会话（ormTemplate.runInSession） | 平台 OrmSession 托管关闭 | 配对由 nop-orm 基础设施保证，不在本模块重审 |
| Closeable/AutoCloseable 层级 | — | 基线 C6b 节为空 + 本轮复核确认：模块内**无任何** Closeable 实现；HTTP 客户端（nop-http client bean 带 destroy-method="stop"）与 FeishuClient（heartbeat/transport/scheduler）生命周期在 nop-http / nop-integration-feishu，不在本根 |

### 心跳/超时调度器缺席裁定

基线 C4b 节在本根内无任何 `ScheduledExecutorService`/`Timer`/`schedule*` 命中（仅 micrometer `Timer` 指标记录，非调度器）。FeishuClient 的单线程心跳调度器（`feishu-client` DaemonThreadFactory）位于 `nop-integration-feishu`，**超出本批次源码根**——裁定：本模块内无心跳/超时调度器立案对象；`_tmp/cra-baseline-nop-ai-gateway.txt` C6a/C6b 节圈定的重点（5 个 acquire 点 + 空 closeable 层级）已全部人工复核。

### 降档口径

本维度全部发现为泄漏/残留/清理不对称形态，按盲审任务要求**一律不适用启动期单线程降档**，按常驻并发口径判级。

### 与 C1–C4 已立案条目的边界声明

1. **停机零调用（C4-004）**：生产装配（`ai-gateway-defaults.beans.xml:42/117` 及合并产物）确认 `nopChannelConnectorManager` 无 `init-method="startAll"`/`destroy-method="stopAll"`、`$DEFAULT$nopChannelMessageService` 无 `destroy-method="close"`。该「装配未接线」面归 C4-004，本报告不立案；C6-001/002/003 的判级已将「close/stopAll 当前无生产调用方」计入可达性。
2. **孤儿池（C4-001）**：close 之后延迟建池复活、close 未解除桥订阅导致池复活——同失效面不重复立案，仅在 C6-003 机理段指认为佐证。
3. **并发计数泄漏（C4 已立案面）**：本轮对 5 个 acquire 点逐一复核配对，确认 `FailoverStreamFlow.startAttemptWithOptions` 存在 cancel 竞态孤儿获取窗口（L235 `cancelled.get()` 检查通过 → L263 acquire → L288 `attempt = state` 之前 cancel() 触发 teardown 读到旧 attempt/null → 新 attempt 的 acquire 永不释放；此后 AttemptSubscriber 的 onSubscribe/onNext/onError/onComplete 全部被 `cancelled` 守卫拦截，无任何路径走到 releaseAttempt）。**按盲审边界（同失效面不重复）不立案**，留待合并裁定与 C4 已立案条目对照去重。
4. **传输线程内联（C4-005）**：`FeishuConnector.deliverSegmented` 在传输回调线程同步调用 `feishuClient.sendMessage` 属该面，不立案。

---

## 发现

### [C6-001] ChannelConnectorManager startAll 部分失败无回滚、stopAll 无逐连接器隔离

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelConnectorManager.java:100-120`
- **证据片段**:
  ```java
  public void startAll(ChannelConnectorContext context) {
      ...
      for (IChannelConnector c : connectors) {
          c.start(context);      // 第 k 个抛异常 → 前 k-1 个保持 started，无回滚
          started.add(c);
      }
  }
  public void stopAll() {
      for (int i = started.size() - 1; i >= 0; i--) {
          started.get(i).stop(); // 第 i 个抛异常 → 更早启动的 0..i-1 全部不再 stop
      }
      started.clear();           // 异常时不可达，部分 stop 后重入 stopAll 会重复 stop
  }
  ```
- **严重程度**: P2
- **并发场景**: 单线程生命周期管理调用（容器启动/停机/热重配）与各连接器内部线程（Feishu Stream 接收线程、心跳调度线程）交叠；本质是多资源成组获取/释放的编排缺陷（F8 清理不对称 + F2 半个成功），非数据竞争。
- **触发时序**: T1 `startAll` 依声明序启动 connector#1（`FeishuConnector.start` 成功：`feishuClient.start` 已建立 Stream 长连接 + 心跳调度，`started=true`）→ T1 启动 connector#2 时 `feishuClient.start` 因凭证缺失 fail-fast 抛 `NopFeishuException`（`resolveCredentials` 第 4 档空凭证契约，FeishuConnector.java:779）→ 异常向上传播，**connector#1 无任何路径被 stop**：长连接、心跳任务、已注册的 IMessageHandler 全部存活，管理器失去对「已启动集合」失败语义的收口。stop 面：T2 `stopAll` 中 connector#i.stop() 抛异常 → 逆序循环中断，更早启动的连接器不释放，且 `started.clear()` 跳过（重入 stopAll 会对已停连接器二次 stop，当前实现靠 `started` 标志幂等兜底，属隐性依赖）。
- **现状**: startAll 只有「全成功 / 抛异常」两态，部分成功状态对调用方不可见且不可回滚；stopAll 无逐连接器异常隔离（对照同模块 `ChannelMessageServiceImpl.close()` 的单资源隔离写法）。
- **风险**: 多连接器装配（`IChannelConnector` javadoc 明列 dingtalk/wecom/webhook 扩展方向）下，一个渠道配置错误会使其余已启动渠道的连接/心跳资源在失败启动后长期滞留；stop 面上，一个渠道 stop 抛错会阻止其余渠道的优雅释放，资源滞留至进程退出（daemon 线程不阻断退出但连接/订阅句柄在存活期内持续占用）。当前单连接器（feishu）装配下为结构性潜伏。
- **建议**: `startAll` 失败时对 `started` 已收录子集逆序执行补偿 stop（逐个 try/catch 聚合抑制异常后重抛原异常）；`stopAll` 对每个 `stop()` 独立 try/catch、收集失败清单、循环完毕后统一 `started.clear()` 并 fail-loud 记录（参照 `ChannelMessageServiceImpl.close` 的隔离与 `NopException` 两档错误策略）。
- **失效面枚举**: 同族成组生命周期方法全量清单——(a) `ChannelConnectorManager.startAll/stopAll`（本条，缺陷面）；(b) `FeishuConnector.start/stop`（幂等标志 + stop 全捕获不抛，配对面本身健壮，无缺陷）；(c) `ChannelMessageServiceImpl.close()`（单资源 + awaitTermination 中断处理，隔离良好，无缺陷）；(d) `ChannelLoginApiBizModel.registerProvider/setChannelBindProviders`（纯注册无生命周期语义，N/A）；(e) `FailoverStreamFlow` 取消路径 teardown（有 per-attempt released 守卫，归 C4 面）；(f) 平台侧 `DaoResourceNamespaceHandler register/unregister`（bean 带 destroy-method，模块外）。仅 (a) 立案。
- **信心水平**: 确定（缺陷结构可静态证实；触发依赖 ≥2 连接器装配或抛错型 stop 实现——当前 feishu 单装配下潜伏）
- **误报排除**: 不属「低频启动/管理路径粗粒度同步合理」的误报类——问题不是锁粒度而是成组获取的回滚缺失；javadoc 只声明「stopAll 逆序 LIFO 释放」，未声明部分失败语义，非已声明边界。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：连接器生命周期投产接线即回升 P2)｜新发现（cra-index WI 1.1–3.2 台账零同站点）。机制回码证实：startAll L105-108 逐 connector start 无补偿收录、stopAll L116-119 逆序循环无逐连接器隔离且 started.clear()（L119）异常时不可达；触发面 = startAll/stopAll 被调用——全仓非测试零调用（grep 独立复现：仅测试树 + nop-orm-migration 同名词异类；beans.xml L39-45 无 init-method/destroy-method）；双附加休眠：当前仅 feishu 单连接器装配 + FeishuConnector.stop 内部全捕获不抛（L211-215），stop 面异常路径当前不可达。泄漏/残留形态不适用启动期降档，降档依据为渠道栈休眠面条件化（与 C2-004 同面 P2→P3 处置对齐）；跨单元与 WI 2.5 MA-10（startFallbackChains 部分启动无补偿族）同族，修复批次对齐不重复

### [C6-002] FeishuConnector.stop() 不排空在途 agent 执行，停止后回调仍对已停客户端发送并写库

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:206-217, 343-366, 376-416, 439-452`
- **证据片段**:
  ```java
  // onMessage：future 引用在注册回调后被丢弃，连接器不持有在途执行句柄
  future = context.getAgentEngine().execute(request);
  ...
  future.whenComplete((result, error) -> onExecutionComplete(result, error, replyTarget, isNewSession, agentName));

  // stop()：仅置标志 + 停客户端，无在途执行排空/等待
  public synchronized void stop() {
      if (!started) { return; }
      started = false;
      try { feishuClient.stop(); } catch (Exception e) {
          LOG.debug("feishu-connector stop ignored feishuClient error", e);
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 传输接收线程（onMessage → engine.execute 发起）+ 引擎执行线程池（future 完成回调 onExecutionComplete）与停机线程（stop()）三方交叠；`started` 为 volatile 但只控制入口，不覆盖在途回调。
- **触发时序**: T1 传输线程处理消息 → `execute(request)` 返回在途 future（引擎执行中，持 LLM 上行连接与会话状态）→ T2 运维/容器调用 `stop()`：`feishuClient.stop()` 置 handler=null、关 transport、停心跳调度（FeishuClient.java:200-229）→ T1' 引擎回调线程进入 `onExecutionComplete` → `sendReply` → `deliverSegmented` → `feishuClient.sendMessage` 抛 "client not started"（FeishuClient.java:239-241）→ 每个分段一条 LOG.error，**用户回复永久丢失**；若 `isNewSession`，`saveMapping`（:407）在连接器停止后仍向 DB 写入新会话映射（超出连接器生命周期之外的副作用）。
- **现状**: stop() 与在途执行之间无任何协调原语：无 in-flight 注册表、无 cancel 传播、无有界 drain 等待；stop 后回调仅靠 per-segment try/catch 兜住异常（fail-loud 日志），语义上属「静默丢单」而非「排空或拒绝」。
- **风险**: 停机/重配窗口内所有在途对话的回复丢失（用户侧表现为 bot 已读不回）；错误日志量与在途消息量成正比；停止后 DB 写入使「连接器停止 = 资源静止」的停机语义不成立。在途 agent 执行本身（LLM 连接、会话）由引擎持有，连接器无从释放——排空语义缺口使停机时资源回收责任悬空。
- **建议**: start 期以 `CopyOnWriteArrayList<CompletableFuture<?>>`（或平台 `ICancelToken` 链）登记在途 future，`stop()` 逐个 cancel（附 reason）并以有界 await 等待终态（对齐 `ChannelMessageServiceImpl.close` 的 `awaitTermination(2s)` 姿态）；`onExecutionComplete` 入口检查 `started`，停止后改为显式放弃并计数（fail-loud 指标而非 per-segment error 刷屏）。
- **失效面枚举**: 模块内全部「异步发起后回调」入口清单——(a) `FeishuConnector.onMessage → execute → whenComplete`（本条，无取消契约）；(b) `ChatServiceFailoverAdapter.callAsync` 非 流式 attempt（每次 attempt 前查 `cancelToken.isCancelled()`，whenComplete 全终止路径 release——有取消契约，无缺陷）；(c) `FailoverStreamFlow`（cancel → teardownCurrentAttempt 完整 teardown，归 C4 面）；(d) `GatewayStreamingLifecycleListener`（终态释放归监听器宿主契约，归 C4 监视器投递面）；(e) `ChannelMessageServiceImpl` mode-1 fan-out 任务（shutdownNow + interrupt 兜底）。仅 (a) 立案；(b) 为 (a) 的同仓对照组，证明取消契约在本模块有既成惯例。
- **信心水平**: 确定
- **误报排除**: 不属「优雅停机粗粒度即可」的误报类——后果是用户可见的回复丢失与停止后 DB 写，非纯日志噪音；`started` 检查只挡新消息不挡在途回调，守卫缺口真实存在。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：连接器生命周期投产接线即回升 P2)｜新发现。机制逐点回码证实：stop() L206-217 仅置标志 + 停客户端、无在途登记/取消/排空；onMessage L366 future 引用注册回调后丢弃；onExecutionComplete L376-416 全程无 started 复查（停止后 saveMapping L405-411 仍写库、deliverSegmented L444-451 per-segment catch→LOG.error——回复丢失 + 停止后 DB 写属实）；FeishuClient.sendMessage L239-241 "client not started" 守卫实证。触发面双层休眠：stop 仅经 stopAll/manual 可达（startAll/stopAll 零生产接线）且连接器未 start 即无在途执行。与 C4-004/C4-005 同生命周期面单批修复；与 WI 3.1 MA-05（超时取消契约 F3 族）同治理形态跨单元批次对齐不重复

### [C6-003] ChannelMessageServiceImpl close 与派发竞态：RejectedExecutionException 逃逸中断剩余 listener 派发；close 无 closed 态守卫

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:216-234, 241-252, 371-393, 477-491`
- **证据片段**:
  ```java
  private void fanOutToListeners(InboundChannelMessage message) {
      Executor executor = resolveFanOutExecutor();          // T1: 取得 E
      for (IInboundMessageListener listener : inboundListeners) {
          ...
          CompletableFuture<Void> raw = CompletableFuture.runAsync(() -> { ... }, executor); // T1: E 已 shutdown → REE 同步抛出
          ...
      }
  }
  public void close() {                                     // T2
      ExecutorService owned = this.ownedFanOutExecutor;
      if (owned != null) { owned.shutdownNow(); ... this.ownedFanOutExecutor = null; }
  }
  ```
- **严重程度**: P2
- **并发场景**: 传输/骨干消费线程（`dispatchInbound` → `fanOutToListeners`，mode-2 下为 bridgeConsumer 回调线程）与停机线程（`close()`）交叠；`ownedFanOutExecutor` 的懒建（双重检查锁）与关闭之间无 closed 标志或公共锁。
- **触发时序**: 主时序（拒绝逃逸）：T1 派发线程执行 `fanOutToListeners` L372，`resolveFanOutExecutor()` 返回池 E → T2 停机线程 `close()` L242 读到同一 E，`shutdownNow(E)` → T1 循环内 L393 `runAsync(..., E)` 抛 `RejectedExecutionException`（同步、无捕获）→ 传播出 for 循环：**剩余未派发的 listener 被整体跳过（半个派发）**，异常沿 `dispatchInbound` → `bridgeConsumer.onMessage`（mode-2）或传输连接器回调继续外抛，落在传输层线程上未收口。子时序（复活，归 C4-001 面不计本条）：T2 `close()` 读 `ownedFanOutExecutor==null` 先于 T1 赋值 → close 空转，T1 新建池永不关闭；close 置 null 后任何迟到派发经 `resolveFanOutExecutor` 懒建新池（无 closed 拒绝），且 `close()` 不解除 bridgeConsumer 的骨干订阅（`messageService.subscribe` 无反向调用，:477-491），mode-2 下停机后到达的消息必然触发复活路径。
- **现状**: close 与派发共享的唯一同步点是懒建双重检查锁，关闭侧不参与；runAsync 的拒绝异常无 try/catch 收口；「关闭后拒绝新派发」语义完全缺失。
- **风险**: 任何按 javadoc 契约接线 close（「released by {@link #close()}」，对齐 `TeamTaskFlowOrchestrator.ownedSpawnExecutor` 惯例）的部署，在停机/重配与消息派发交叠时出现未捕获异常外抛 + 剩余 listener 静默跳过（对 mode-2 的外部多消费者语义为「部分消费」）；复活子时序产生永不关闭的孤儿池（该后果面归 C4-001）。当前生产装配未接线 close（C4-004 面），现实可达性集中于测试/手工嵌入/未来接线修复后。
- **建议**: 引入 `volatile boolean closed` 并在 `resolveFanOutExecutor` 与 `close` 间共享判定（closed 后直接拒绝派发并 fail-loud 计数）；`runAsync` 包 try/catch(`RejectedExecutionException`) 记日志后 continue 剩余 listener；close 时尽力解除桥订阅或至少置 bridge 失效标志（`IMessageService` 若无 unsubscribe 能力，则以 closed 标志在 `bridgeConsumer.onMessage` 入口短路）。
- **失效面枚举**: 同族「执行器获取-使用-关闭」站点全量清单——(a) `ChannelMessageServiceImpl.ownedFanOutExecutor`（本条）；(b) `FeishuClient` scheduler（模块外，stop 内 shutdownNow 且 synchronized，配对良好）；(c) 平台 `StandardThreadPoolExecutor`/`IThreadPoolExecutor`（nop-kernel 基础设施带生命周期管理，本模块未用——建议项佐证）；(d) `FailoverStreamFlow` 无自建线程（N/A）。仅 (a) 立案。
- **信心水平**: 确定（交错序列可静态构造）；现实可达性依赖 close 被调用（当前装配未接线，见 C4-004 边界）
- **误报排除**: 不属「CHM/原语本身使用」误报类——缺陷在 close/dispatch 复合操作的守卫缺失；close 的 javadoc 明文声明释放契约（「a lazily-created owned daemon pool … released by {@link #close()}」），竞态发生在契约路径内，非测试专用简化模型。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：fan-out 路径投产接线即回升 P2)｜新发现（同单元已立案站点重复登记——并入工单不独立计数）。与 C4-003 同站点同失效面：主时序（REE 逃逸）= C4-003(b) 停机中竞态（runAsync L393 提交与 close L242-250 shutdownNow 交错、无捕获穿透成立）；子时序（复活/无 closed 守卫）= C4-003(a)（初审已自认归 C4 面不计）；独有增量仅"REE 中断剩余 listener 派发（半个派发）"细节，不构成独立缺陷面。触发面 = close() × dispatchInbound/桥投递——全仓非测试零调用（grep 独立复现）。并入 C4-003/C1-004/C2-006/CL-002 同站点族单工单（终判随 C4-003 对齐）；同 diff 修复：closed 标志 + REE catch + 退订

### [C6-004] 会话映射「半个成功」：新引擎会话已创建而 saveMapping 失败/竞争落败 → 引擎会话孤儿化，无补偿无清理

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:310-325, 404-411`；`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelSessionStoreImpl.java:69-98`
- **证据片段**:
  ```java
  // FeishuConnector.onMessage：findByChannel 失败被降级为 miss（isNewSession=true）
  try {
      session = sessionStore.findByChannel(CHANNEL_TYPE, chatId);
  } catch (Exception e) {
      LOG.warn("feishu-connector sessionStore.findByChannel failed for chat {}", chatId, e); // error ≡ miss
  }
  boolean isNewSession = existingSessionId == null;   // → 引擎将新建 session

  // onExecutionComplete：saveMapping 失败仅 warn，新会话就地遗弃
  if (isNewSession && result.getSessionId() != null) {
      try {
          sessionStore.saveMapping(CHANNEL_TYPE, chatId, result.getSessionId(), agentName);
      } catch (Exception e) {
          LOG.warn("feishu-connector saveMapping failed for chat {}", chatId, e);   // 无补偿
      }
  }

  // ChannelSessionStoreImpl.saveMapping：盲目 newEntity + insert，无 upsert
  NopAiChannelSession entity = dao().newEntity(); ... dao().saveEntity(entity);
  ```
- **严重程度**: P1
- **并发场景**: 传输接收线程（消息 B 的 `findByChannel`）与引擎回调线程（消息 A 完成后的 `saveMapping`）无同步交叠；另含无需并发的单线程失败形态（DB 抖动）。映射表 `uk_nop_ai_channel_session_channel(channelType, channelId)`（nop-ai/model/nop-ai.orm.xml:1466-1470）在库端仲裁唯一性，代码层无 upsert/冲突识别。
- **触发时序**: 场景 A（单线程，无需竞态）：T1 `findByChannel` 因 DB 抖动抛异常 → catch 后 `session=null` → `isNewSession=true` → 引擎新建 session-S2 并以此完成回复 → `saveMapping(S2)` 撞唯一键抛异常 → warn 后遗弃；映射仍指旧 S1，**S2 的会话上下文成为引擎侧孤儿状态**，本条消息的对话上下文在后续轮次不可达。场景 B（并发，用户在处理期间追发消息的常规行为）：T1 消息 A `execute` 在途（尚未 saveMapping）→ T2 消息 B `findByChannel` 返回 null → B 同样 `isNewSession` 并新建 session-SB → T1' A 完成先 `saveMapping(SA)` 胜出 → T2' B 完成 `saveMapping(SB)` 撞唯一键 → warn 遗弃 SB；SB 的回复已送达但会话不被映射，后续消息回退到 SA，上下文断裂。
- **现状**: 「创建引擎会话（上游分配资源）→ 持久化映射（登记句柄）」是两步非原子操作：第二步失败（DB 异常或唯一键竞争落败）仅有 warn 日志，既不回滚第一步（无删除 API），也不把冲突识别为可恢复的「改映射」路径（saveMapping 恒 insert，无按 uk 的 update 分支）；且 `findByChannel` 的 error 被等同为 miss，主动制造新会话分配。
- **风险**: 每次触发遗弃一个引擎会话（其会话记录/历史在引擎存储中成为不可达残留——模块内 `IChannelSessionStore` 无 delete/TTL，无任何回收路径，长时运行下单调累积，对照 F2 半个成功 + F4 只增不删复合）；用户侧表现为追问消息后的对话上下文丢失/跳变（会话连续性违约）。触发链无需罕见时序：追发消息在 agent 执行秒级延迟下是常规用户行为，DB 抖动路径则完全确定。
- **建议**: (1) `ChannelSessionStoreImpl.saveMapping` 改 upsert：按 uk 先查，命中则 `updateEntity` 换绑 sessionId（或捕获唯一键冲突分类后转 update），使竞争落败方无害收敛而非遗弃；(2) `FeishuConnector.onMessage` 区分 miss 与 error——`findByChannel` 抛异常时不得降级 `isNewSession=true`（本轮直接错误回复或短暂跳过），对照 ConcurrencyRegistry「不把缺陷伪装成正常状态」的裁定姿态；(3) 映射换绑/失败时对被遗弃引擎会话登记 fail-loud 指标（复用 `IFailoverMetrics` 模式），引擎侧回收能力属 nop-ai-agent 批次（超出本根，只留线索）。
- **失效面枚举**: 会话映射生命周期全部调用点清单——(a) `FeishuConnector.onMessage/onExecutionComplete` 的 findByChannel→execute→saveMapping 链（本条，缺陷面；未来 dingtalk/wecom/webhook 连接器按 IChannelConnector 模式复制该链时同族暴露）；(b) `FeishuConnector.onMessage` 的 `updateLastActive` 调用（:319-325，try/catch debug 降级——仅刷新时间戳，失败无残留，无缺陷）；(c) `ChannelSessionStoreImpl.saveMapping/updateLastActive/findByChannel`（存储原语：updateLastActive miss 抛错 fail-fast 正确；saveMapping 缺 upsert 即 (a) 的根因载体之一）；(d) `ChannelLoginScanProcessor`（ISessionBootstrap 会话为另一机制，challenge 存上游 MfaChallengeStore，模块内无生命周期管理点，N/A）。立案面 = (a)+(c) 同根一体。
- **信心水平**: 确定（代码结构 + ORM 唯一键静态可证；引擎侧残留的最终驻留形态在 nop-ai-agent，本条按「模块内无回收路径」口径立案）
- **误报排除**: 不是「findByChannel miss → null 是显式语义」的误报类——miss 语义本身正确，缺陷有二：error 被等同为 miss（守卫缺失、文档未声明该降级），以及两步操作无恢复语义；也不属 C4 已立案七面（窗口清理/计数泄漏/监视器投递/终态冲刷/孤儿池/传输线程内联/停机零调用均非本条失效面）。
- **复核状态**: 未复核
- 复核状态: 降级(P2，条件化：连接器投产接线即回升 P1)｜新发现（cra-index 零同站点；链主地位维持）。五子项逐一复核：(1) error≡miss 降级实证（L311-318 catch 后 session 保持 null → isNewSession=true）；(2) saveMapping 恒 insert 无 upsert 实证（ChannelSessionStoreImpl L67-96 盲目 newEntity+saveEntity）；(3) 库侧 UK 仲裁实证——uk_nop_ai_channel_session_channel 位于 nop-ai/model/nop-ai.orm.xml L1462-1466（初审引 1466-1470 微偏，随 C1 复核勘误收敛），UK 存在同时闭合 C7 维度 CL-015"未核实"悬念：并发双插入由库端仲裁，败者走本条场景 B warn 遗弃路径，无影子行；(4) 遗弃无补偿实证（L405-411 内层 catch 仅 warn 不重抛；顶层 catch(Throwable) L412 不改变遗弃语义；IChannelSessionStore 无 delete/TTL API——F2+F4 复合成立）；(5) 触发面休眠核实成立（C2 复核提示独立确认：onMessage 仅经 feishuClient handler 投递可达，连接器未 start 即无消息——场景 A 非并发 DB 抖动形态同受零接线约束）。泄漏/残留形态不适用启动期降档；降档依据为渠道栈休眠面条件化，按 C4-005 先例（onMessage 面 P1→P2 条件化）保持链内相对序（链主 P2 > 兄弟 C1-005/C2-005/C3-006 均 P3）；与 WI 3.1 MA-03（引擎侧 F4 session put-only 族）邻接不重复——线索关系维持，修复同批对齐

---

## 维度集外线索（overlay C5/C9）

### [nop-ai-gateway-CL-011] (C9) 流式反向转换每 chunk 双重 JSON 序列化往返

`AiDialectBackendMessageConverter.toFrontendStreamChunk`（failover/AiDialectBackendMessageConverter.java:99-107）对流式**每个 chunk**执行 `JsonTool.serialize(backendDelta, false)`（Map→JSON 字符串）→ `parseStreamChunk`（JSON→ChatStreamChunk）→ `buildStreamChunk`（→前端 Map）三次转换 + 两棵中间树；`onStreamElement` 是网关热路径（每个 SSE 元素一次，单响应数十至数百 chunk），量化口径：每响应 ~3N 次转换/2N 次中间分配（N=chunk 数）。建议：dialect 增加免序列化的 Map↔Chunk 直转通道，或至少按 backend dialect 复用已解析结构。基准缺口：建议 JMH 基准形态 = `toFrontendStreamChunk(per-chunk map) × 100 chunk` 对照直转实现。

### [nop-ai-gateway-CL-012] (C5) FailoverStreamFlow 越窗后下游零需求时缓冲无上界

`FailoverStreamFlow.deliver`（failover/FailoverStreamFlow.java:371-382）：`passed=true` 后元素在 `demand==0` 时无条件 `state.buffer.add(item)`，窗口 N/T 上限只作用于越窗前（bufferOrForward:359-363）；而内部订阅对上游 `request(Long.MAX_VALUE)`（:516）——下游（SSE 写出方）停滞时整个响应的剩余 chunk 在单个 attempt 缓冲内无界累积，仅受单响应总量约束。长 LLM 输出 + 慢客户端组合下为内存峰值放大点。建议：越窗后缓冲设上限（超限停发上游 request 或降级断流），或改用有界队列 + 平台 `QueueOverflowPolicy`。

### [nop-ai-gateway-CL-013] (C9) 规则选择策略每次 select 全量物化输入 + 新建 RuleRuntime

`RuleBasedSelectionStrategy.select`（routing/RuleBasedSelectionStrategy.java:140-143, 180-223）每次调用 `ruleManager.newRuleRuntime()` 并重建 candidates/health 两个全量 Map 结构（每候选 ~6 项装箱）；调用频率 = 每请求 × 每 attempt（含饱和重选递归路径）。仅当部署选择规则策略时生效，属可量化分配放大候选（候选池规模小，收益有限）。建议：健康视图投影复用 `CandidateHealthProvider` 快照、input 模板按候选池惰性构建。基准缺口：`select(pool=N) × attempt` 微基准。

---

## 维度复核结论（WI 3.3 独立复核，合并批 C6+C7）

- **复核日期**: 2026-10-20
- **复核人**: C6+C7 维度独立复核员（与初审/深挖/C1+C2/C3+C4 复核员相互独立）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` 校准口径；C6-001…005 全部 5 条证据行逐条回码、时序论证逐步核验、三态判定 + 现状标注；降档口径核对（泄漏/残留/长时运行形态不适用启动期降档；渠道栈休眠面条件化降档——本次复核独立重跑 grep 并与 C1/C2/C4 复核证据互证，逐条确认触发面确实依赖渠道栈接线）；与 `ai-dev/audits/cra-index.md` WI 1.1–3.2 台账逐条查重。

### 台账查重结论（C6 维度）

C6 维度 5 条与 cra-index WI 1.1–3.2 台账零同站点重复立项（台账尚无 nop-ai-gateway 条目）。跨单元同族（非重复，批次对齐）：C6-001↔WI 2.5 MA-10（startFallbackChains 部分启动无补偿族）；C6-002↔WI 3.1 MA-05（超时取消契约 F3 族）；C6-004↔WI 3.1 MA-03（引擎侧 F4 session put-only 族——初审线索关系维持）。

### 逐条判定表（C6-001…005）

| ID | 初判 | 判定 | 终判 | 核心依据 |
|---|---|---|---|---|
| C6-001 | P2 | 降级 | P3（条件化：投产接线即回升 P2） | 成组回滚缺失/stop 隔离缺失回码证实；触发面 = startAll/stopAll 零生产接线（grep 独立复现）；双附加休眠（单连接器装配 + FeishuConnector.stop 全捕获不抛）；与 C2-004 同面处置对齐 |
| C6-002 | P2 | 降级 | P3（条件化：投产接线即回升 P2） | stop 无排空/回调无 started 复查/停止后 DB 写逐点回码证实；触发面双层休眠（生命周期零接线 → 无在途执行）；与 C4-004/005 同生命周期面 |
| C6-003 | P2 | 降级 | P3（条件化：投产接线即回升 P2；并入工单） | 与 C4-003 同站点同失效面（REE 竞态 + close 守卫缺失两链重合，独有增量仅"半个派发"细节）；并入 C4-003/C1-004/C2-006/CL-002 单工单，终判随 C4-003 |
| C6-004 | P1 | 降级 | P2（条件化：投产接线即回升 P1） | 五子项全证实（error≡miss/upsert 缺失/UK 仲裁/遗弃无补偿/休眠面含场景 A）；链主地位维持；按 C4-005 先例降一级保持链内相对序 |
| C6-005 | P3 | 保留 | P3 | loginByScan publicAccess 端点 live（不依赖连接器接线，bean 已注册）；TTL 兜底有界，P3 恰当 |

### 判定统计（C6 维度）

- **保留×1**：C6-005（P3，live 面）
- **降级×4**：C6-004（P1→P2 条件化）、C6-001/C6-002（P2→P3 条件化，连接器生命周期面）、C6-003（P2→P3 条件化，fan-out 面 + 并入同站点族工单）
- **驳回×0**（5 条机制全部回码成立，降档全部源于触发面休眠而非机制不成立）
- 终判严重度分布：P2×1（条件化）/ P3×4（条件化×3 + live 保留×1）；P1×0（条件化回升挂起）

### 现状标注分布（C6 维度）

- 新发现：全部 5 条（cra-index 零同站点）
- 渠道栈休眠（连接器生命周期零接线：startAll/stopAll 零生产调用）：C6-001、C6-002、C6-004
- 渠道栈休眠（fan-out 面零生产接线：dispatchInbound/桥投递零生产调用）：C6-003（+ 并入同站点族工单）
- live 面：C6-005（publicAccess 登录编排端点）

### 跨维指针核对（同案命中清单，C6 维度）

1. **C6-003 = C4-003（+C1-004/C2-006/CL-002）同站点重复登记**——并入单工单，终判随 C4-003（P3 条件化）。
2. **C6-004 链主 = C1-005/C2-005/C3-006 同链四视角**——初审已声明，复核确认链主覆盖面最全（非并发场景 A + 并发场景 B + upsert 修复落点），合并裁定不重复计数；C1/C2 复核提示的"场景 A 同受零接线休眠面约束"经独立确认成立（降级主依据之一）。
3. **C6-004 ↔ C7 维度 CL-015**：CL-015 的"UK 是否存在未核实"悬念由本复核闭合——UK 存在（nop-ai.orm.xml L1462-1466），并发双插入由库端仲裁、无影子行，CL-015 独立缺陷面不成立（其机制即 C6-004 场景 B），随 C6-004 工单处置。
4. **C6-002 ↔ C4-005**：同生命周期面不同失效面（停机排空 vs 传输内联），同批修复不重复。
5. **C2-001 acquire 泄漏长程后果指针**：初审边界声明（对 5 个 acquire 点只留痕、计数泄漏面归 C4）经复核成立，无越界重复。

### 高风险条目子项复核说明

- **C6-004（链主，P1→P2 条件化）**：五子项复核见条目状态行——(1)–(5) 全部回码证实；特别核实两点：onExecutionComplete 顶层 catch(Throwable)（L412）不改变 saveMapping 内层 catch 后的遗弃语义（L405-411 先内层捕获、不重抛）；引擎侧孤儿残留的最终驻留在 nop-ai-agent（MA-03 承载），本条按"模块内无回收路径"立案口径成立。降级不改变修复指令（upsert + error/miss 区分 + fail-loud 指标），投产接线即回升 P1。
- **C6-001（P2→P3 条件化）**：子项复核确认 stopAll 隔离缺陷为三重休眠（零接线 × 单连接器 × stop 不抛）——初审"结构性潜伏"定性准确，触发依赖描述如实；机制层面零修正。

## 最终保留项（C6 维度）

| 工单面 | 条目 | 终判 | 修复方向 | 合并指令 |
|---|---|---|---|---|
| 会话映射半个成功（链主） | C6-004 | P2 条件化 | saveMapping 改 upsert（UK 命中换绑）；findByChannel 区分 miss/error；遗弃登记 fail-loud 指标 | 链主工单（吸收 C1-005/C2-005/C3-006；CL-015 随案关闭）；与 WI 3.1 MA-03 引擎侧回收批次对齐；投产接线即回升 P1 |
| 登录会话引导存量 | C6-005 | P3 | 编排补终态指标 + owner doc 声明回收责任 = auth 侧 TTL；loginByScan 加频控 | 独立（7.1 裁决修/deferred）；与 WI 3.1 MA-03 邻接对齐 |
| 成组生命周期编排 | C6-001 | P3 条件化 | startAll 失败逆序补偿 stop；stopAll 逐连接器 try/catch 隔离 + 统一 clear | 独立（回升 P2）；与 C4-004 destroy-method 同批、与 WI 2.5 MA-10 部分启动族对齐 |
| 停机在途排空 | C6-002 | P3 条件化 | 在途 future 登记 + stop 有界 drain + onExecutionComplete started 复查 | 独立（回升 P2）；与 C4-004/C4-005 同批 |
| close/派发竞态 | C6-003 | P3 条件化 | closed 标志 + REE catch + 退订 | 并入 C4-003/C1-004/C2-006/CL-002 单工单同 diff（回升 P2） |
