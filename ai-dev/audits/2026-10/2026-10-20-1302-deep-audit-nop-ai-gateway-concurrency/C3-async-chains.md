# C3 异步链路正确性 — nop-ai-gateway（WI 3.3）

## 审计范围与口径

- **覆盖声明**：单批次全源码根逐文件审读 `nop-ai/nop-ai-gateway/src/main/java`（36 文件 / 6,035 行），100% 文件触达。异步入口清单（全根 grep `CompletableFuture|CompletionStage|new Thread|ExecutorService|Flow.Publisher|Flow.Subscriber|orTimeout|whenComplete|thenApply|thenCompose|runAsync|supplyAsync` 兜底，与基线 C3a/C3b 一致）：
  1. `ChatServiceFailoverAdapter`（非流式 `callAsync` 的 CompletableFuture 编排 + `callStream` 直通 FailoverStreamFlow）
  2. `FailoverStreamFlow`（Flow.Publisher/Subscription/Subscriber 自实现，per-attempt 内部订阅）
  3. `AiGatewayFailoverInterceptor`（非流式 `invoke` CompletableFuture 编排；流式经回调/监听器注入）
  4. `GatewayStreamingRetryCallback` / `GatewayStreamingLifecycleListener`（nop-gateway 缓冲层回调面）
  5. `ChannelMessageServiceImpl`（mode-1 fan-out 专用池 + mode-2 sendAsync orTimeout + 桥消费者）
  6. `FeishuConnector`（`IMessageHandler.onMessage` 投递线程 + `execute()` future 回调）
  7. `ChannelLoginScanProcessor` / `ChannelLoginApiBizModel`（`createSessionForUserAsync` 链 + 同步抛 MFA 适配）
  8. `ModelClassRouter` / `DefaultSelectionStrategy` / `RuleBasedSelectionStrategy`（被异步决策链同步调用的选择原语，无自有异步）
- **心跳/超时调度器缺席裁定留痕**：本模块无心跳/stall 检测器；超时仅存在于 channel 派发层（`dispatchTimeoutMs`）。failover future、流式订阅、`agentEngine.execute` future 均无 `orTimeout`/总时限。按任务口径该缺席本身已裁定，不作为独立发现立案；本报告仅将其作为 C3-004 等条目的后果放大因子（悬挂不可自愈）引用，不越界另立"补心跳"条目。
- **与 C1/C2 已立案条目的边界声明**（盲审，仅按任务简报所载标题对齐，未读 C1/C2 正文）：
  - C1-001「FailoverStreamFlow 窗口清理竞态」：本维度复核确认 `FailoverStreamFlow.handleStreamFailure` L438 `state.buffer.clear()` 在 StreamSubscription monitor 之外执行、与下游 `request()`→`flushBuffer` 的 monitor 内 `poll()` 构成同站点竞态（含跨 attempt 内容泄漏后果）——**同站点同失效面，不重复立案，归 C1-001**。
  - C1-002「FeishuConnector 启动窗口」：`started` 标志于 `feishuClient.start` 之后置位（L200-201），启动期入站消息被静默丢弃——归 C1-002，不重复。`getCapabilities()` L159-169 的 check-then-act 懒初始化为幂等构造、无实质后果，不立案。
  - C2-001「取消-重订阅并发计数泄漏」：`cancel()` 与 `startAttemptWithOptions` L288 `attempt = state` 的交错导致新 attempt 脱离 teardown（计数泄漏）——本维度复核确认该交错存在，**归 C2-001，不重复**。
  - C2-002「监视器内投递」：`deliver`/`flushBuffer`/`flushAll`/`handleStreamComplete` 在 `synchronized(this)` 内调用 `subscriber.onNext/onError/onComplete`——归 C2-002，不重复。
- **降档口径**：请求触达面（每请求/每订阅执行路径）；启动期路径不适用本维度降档。
- **误报校准执行**：`ConcurrencyRegistry` 的 compute 原子区实现复核为正确（下溢 fail-fast 语义成立）；`CandidateHealth.isConcurrencySaturated` 用 `>=` 使 acquire 后复查的重选递归必然终止（类内无可用候选时 router 抛 `ERR_AI_MODEL_CLASS_SATURATED`），**"无界递归自旋"假设被证伪**，不立案（残余影响见 C3-005）；`GatewayStreamingLifecycleListener` synchronized + `released` 守卫复核为正确；`ChannelMessageServiceImpl.fanOutToListeners` 的 AR-4 raw-future + worker-interrupt 模式复核为正确。

## 发现

### [C3-001] 终态冲刷受 demand 耗尽约束，剩余缓冲元素被静默丢弃——流以 onComplete"成功"截断

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:371-405, 465-483`
- **证据片段**:
  ```java
  private void deliver(AttemptState state, ChatStreamChunk item) {
      ...
      } else {
          state.buffer.add(item);            // L380：demand==0 时元素进缓冲，无上限
      }
  }
  private void flushAll(AttemptState state) {
      while (!state.buffer.isEmpty() && demand > 0 && !cancelled.get()) {  // L400：受 demand 约束
          ...
      }                                       // 循环退出时缓冲内剩余元素被放弃
  }
  // handleStreamComplete: L480-481
  flushAll(state);
  subscriber.onComplete();                    // 缓冲未清空仍发成功终态
  ```
- **严重程度**: P1
- **并发场景**: 委托 publisher 投递线程（`ChatServiceImpl` 的 SubmissionPublisher submit 线程）与下游订阅者的 `request()` 调用线程；demand 为 StreamSubscription 跨 attempt 共享字段（monitor 保护）。
- **触发时序**: T1 下游以真实背压消费（如每次 `request(1)`），demand 归零；T2 上游 LLM 流持续产出，`deliver` L380 将元素持续堆入 `state.buffer`；T3 委托流正常结束 → `handleStreamComplete` L480 `flushAll` 仅送出 demand 余额内的元素 → **缓冲剩余元素被丢弃** → L481 发出 `onComplete`。下游收到"成功"终态但响应尾部缺失。次级面：窗口内完成（`passed=false`）时若 complete 前元素尚未越窗，complete 后下游按 RS 契约不会再 `request`，而 `flushBuffer` L386 被 `passed` 门控——即使再次 request 也无法排空。
- **现状**: 终态冲刷语义与自身设计声明矛盾——L397 注释明言"终态冲刷……不受窗口状态门控——窗口语义只延迟转发，**不吞数据**"，但 demand 不足时实现确实吞数据且仍报成功。
- **风险**: LLM 流式响应尾部静默截断（用户看到不完整答案且无任何错误信号）；下游若做聚合/落库则半截结果被当成功持久化（F2"半个成功"无恢复语义）。无 in-repo 消费方可证实下游采用受限 demand（`nopChatServiceFailoverAdapter` 为按名 opt-in bean，仓库内无生产消费方），故保留"很可能"而非"确定"。
- **建议**: 终态（complete 路径）不得丢弃缓冲：`handleStreamComplete` 在缓冲非空且 demand 耗尽时应保持终态挂起（记录 pendingComplete），由 `request()` 的 `flushBuffer` 排空后再发 `onComplete`（参考 SubmissionPublisher 的"缓冲 + demand 驱动投递 + 终态最后发"语义）；或将 AttemptSubscriber 对上游的 `request(MAX)` 改为与下游 demand 联动的受控 request，从源头避免欠 demand 积压。
- **失效面枚举**（F3 同族清扫）: ①`handleStreamComplete` L480-481（成功终态丢弃——本条主面）；②`handleStreamFailure` 窗口外路径 L432-433（error 前丢弃——错误路径可辩护为"以错误优先"，登记豁免）；③`deliverError` L490-493（主动清缓冲 + onError——错误路径，豁免）；④非流式路径无对应面（一次性响应，无缓冲窗口）。仅①立案。
- **信心水平**: 很可能（机制确定；触发依赖下游 demand 模式，仓库内无生产消费方可证）
- **误报排除**: 非"有界声明存在的缓存"——`state.buffer` 无任何容量声明；非 C2-002（不在讨论监视器内投递的锁问题，而是 demand 账本与终态信号的语义缺陷）；非规范允许行为——RS 规范仅禁止超 demand 发信号，不许可"持有关键数据却报成功"。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（cra-index WI 1.1–3.2 台账零同站点；同文件 WI 2.3 C3-003 为缓冲层背压断裂——不同失效面，不重复）。三处引文回码全证实：`deliver` L380 demand==0 入缓冲、`flushAll` L400 受 demand 门控、`handleStreamComplete` L480-481 缓冲未清仍发成功终态；L397"不吞数据"自注释与实现矛盾成立。同仓库正确对照先例佐证缺陷成立：`BufferedStreamingPublisher.handleComplete` L328-337 以 `pendingComplete` 挂起终态、由 `request()` 冲空缓冲后补发 onComplete——LOCAL 形态缺同一机制，双实现分叉；次级面（窗口内完成后 `flushBuffer` L386 passed 门控无法排空）核实成立。触发面 = LOCAL 形态 opt-in 装配 + 下游 demand 受限订阅者（在库零消费方可证，初审已如实标注），判级按"半个成功无恢复语义 + 可具体化触发链"维持 P1；与 CL-001（同站点 C5 无界缓冲面）同批修复不重复计数

### [C3-002] AttemptCancelToken.appendOnCancel check-then-act 竞态——取消回调丢失，已取消 attempt 的 HTTP 流不中断（幽灵请求烧到 EOF）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:108-128, 287-292`
- **证据片段**:
  ```java
  @Override
  public void appendOnCancel(Consumer<String> task) {
      if (cancelled.get()) {          // L109：检查
          task.accept(reason);
      } else {
          tasks.add(task);            // L112：登记——与 cancel() 的 CAS+遍历无互斥
      }
  }
  void cancel(String reason) {
      if (cancelled.compareAndSet(false, true)) {
          ...
          for (Consumer<String> task : tasks) { task.accept(reason); }  // L124-126
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 委托 `callStream` 执行线程（在 `startAttemptWithOptions` L287-292 创建 token 后同步进入 `ChatServiceImpl.callStream`，其内部 `eventPublisher.subscribe` 的 `onSubscribe` 调 `cancelToken.appendOnCancelTask(subscription::cancel)` 完成登记）与调用方取消线程（输出 `cancel()` 或 callerToken 监听器 → `teardownCurrentAttempt` → `state.token.cancel(reason)`）。
- **触发时序**: T1 委托线程执行 `appendOnCancel`，L109 读 `cancelled.get()==false` 后尚未执行 L112 `tasks.add`；T2 取消线程执行 `cancel()`：CAS 成功、L124 遍历空列表返回；T1 恢复执行 `tasks.add` → 任务永久滞留列表、永不被执行。后果链：teardown 已释放并发计数（`releaseAttempt`）并 cancel 了内部订阅（`upSub.cancel()` 只切断 FailoverStreamFlow 的接收面），但**委托在 attempt token 上登记的 HTTP 订阅取消回调丢失** → 底层 SSE fetch 不被中断，继续跑到自然 EOF——占用连接与 provider 配额，而本地计数已归零（限流账面与真实在途请求背离）。
- **现状**: 经典 check-then-act（F10 守卫缺失）；`removeOnCancel`（L117-119）存在但与本案无关；`AttemptCancelToken` 为本模块唯一自建 ICancelToken 实现。
- **风险**: 每次命中泄漏一个活跃 HTTP 流（时长 = 剩余流生命周期，LLM 流可达分钟级）；高取消率负载下（用户中断长回答）幽灵请求累积挤占 provider 侧真实并发额度，且 `isOverConcurrencyLimit` 预检失效。
- **建议**: 将"检查 + 登记"合并为原子序列：`tasks.add(task)` 后二次检查 `cancelled.get()`，若已取消则 `task.accept(reason)` 并（可选）从列表移除；或改用 `CopyOnWriteArrayList` 的快照遍历 + `synchronized(tasks)` 互斥（监听器列表本身已是 COW，补一把轻量锁即可）。
- **失效面枚举**（F3 同族清扫）: ①`FailoverStreamFlow.AttemptCancelToken.appendOnCancel`（本条，唯一自建 token）；②`callerToken.appendOnCancel`（L311，平台 ICancelToken 实现，属 nop-api-core 平台设施，按口径只审使用方式——使用方 `registerCallerListener` 仅在 `start()` 单线程调用一次，无同型竞态，豁免）；③`ChatServiceImpl` 内 `appendOnCancelTask` 调用点为 nop-ai-core 模块（越界，只作证据引用）。
- **信心水平**: 确定（竞态窗口真实存在且可写出交错序列）
- **误报排除**: 非"CompletableFuture/FutureTask 原语使用本身"——这是自建取消原语上的复合操作原子性缺失；非 C2-001（后者是 teardown 与 attempt 字段赋值的交错导致计数泄漏，本条是 token 回调登记的丢失，不同失效面）。
- **复核状态**: 未复核
- 复核状态: 降级(P3，并入 C1-003/C2-008 同站点三登记合并工单，站级判级随合并裁定)｜新发现。竞态机制回码证实（L109 check 与 L112 add 对 `cancel()` 的 CAS+遍历无互斥，窗口真实）；**初审表述勘误核实成立（C1 复核输入，本复核独立验证）**：该交错窗口严格先于 `AttemptSubscriber.onSubscribe`——登记点在 `ChatServiceImpl` L205-212（`eventPublisher.subscribe` 的 onSubscribe 内 `appendOnCancelTask`），执行于 `callStream` 返回之前，而 `publisher.subscribe(AttemptSubscriber)` 在其后（FailoverStreamFlow L292）——故 T2 teardown 时 `state.upSub` 必为 null，`up.cancel()` 为 no-op；且 `upSub` 所指为 SubmissionPublisher 订阅（其 cancel 本就不中断 HTTP fetch），`AttemptSubscriber.onSubscribe` 的 cancelled 守卫（L510-513）取消的同样是该订阅——**外部面无任何冗余中止路径，幽灵 fetch 烧到 EOF 后果链强于初审表述**。初审 L78"已 cancel 内部订阅（upSub.cancel()）"一句在该窗口不成立，以本勘误为准；后果由 WI 3.1 MA-01 族承载（跨单元修复批次对齐）

### [C3-003] 非流式路径调用方取消被分类为 TRANSIENT（可切换）——熔断 recordFailure 记账污染 + 重试预算误耗

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/ChatServiceFailoverAdapter.java:246-274, 291-293, 324-333`
- **证据片段**:
  ```java
  stage.whenComplete((resp, err) -> {
      registry.release(...);
      if (err != null) {
          decideNonStreamFailure(future, request, router, cancelToken, attempt, candidate, sunk, null, err, ...);
          // L291-293: ErrorClassification cls = LlmErrorClassifier.classify(error);
  ```
  而 `LlmErrorClassifier.classify`（nop-ai-core）：`java.io.InterruptedIOException` → **TRANSIENT**；未知异常（含 `CancellationException`，无 status 无 timeout 标记）→ 兜底 **TRANSIENT**；`isSwitchable(TRANSIENT)==true` → L325 `CircuitObservation.recordFailure(...)` + L332 以 `attempt+1` 重试。
- **严重程度**: P2
- **并发场景**: 请求线程（发起 `callAsync`）与调用方取消触发线程（token 取消可来自任意线程）；异常分类与记账发生在 delegate stage 的完成线程上的 `whenComplete` 回调内。
- **触发时序**: T1 调用方在 HTTP 在途时 `cancelToken.cancel()`；T2 delegate（`ChatServiceImpl`/HTTP 客户端）响应取消使 stage 异常完成，异常形态为中断/取消族（`InterruptedIOException` 被分类器**显式**映射 TRANSIENT；`CancellationException` 走兜底 TRANSIENT）→ `isSwitchable` 放行 → 对健康候选 `breaker.recordFailure` + `metrics.onRequestFailure` + 预算 -1 → `callAsyncAttempt(attempt+1)` 下一次循环入口 `isCancelled()` 检查才干净退出。javadoc L287 声称"取消路径不经过本方法"（L421 同），但该假设无任何运行时守卫强制。
- **现状**: 取消与传输失败在非流式失败决策中未区分；流式路径有 `cancelled.get()` 短路（`FailoverStreamFlow` L529/538）而非流式无对应守卫。
- **风险**: 用户主动放弃长回答属高频行为——同一账号反复被取消会在 `ThresholdBreaker` 上累积失败计数直至 OPEN，把健康账号打入冷却、触发无谓跨账号切换；失败指标失真误导容量/健康度运维。
- **建议**: `whenComplete` 的 `err != null` 分支入口处先判 `cancelToken != null && cancelToken.isCancelled()`（或 cause 链含 `CancellationException`）→ 直接 `future.completeExceptionally(err)`，不记账、不 recordFailure、不耗预算；或将"取消"提升为独立 `ErrorClassification`，与 nop-ai-core `LlmErrorClassifier` 一并补取消分支。
- **失效面枚举**（F3 同族清扫）: ①`ChatServiceFailoverAdapter.callAsyncWithOptions` L246-274（本条主面）；②`AiGatewayFailoverInterceptor.invokeNonStreamingAttempt` L284-310 + `decideNonStreamFailure` L328-330（W7 同款孪生，同缺陷，同修）；③`GatewayStreamingRetryCallback.retry` L57-79——若 nop-gateway 缓冲层把客户端断连以异常传入 `retry()`，`classifyStreamError` 同样落到 TRANSIENT → L78 `recordFailure` 同样污染（是否可达取决于缓冲层是否以取消调用 retry，该层在 nop-gateway 模块，本批次越界未证，登记为待证兄弟）；④流式本地路径 `FailoverStreamFlow`——取消被 `cancelled` 短路，豁免。
- **信心水平**: 确定（分类器对取消形态异常的映射为在库代码实证）
- **误报排除**: 非"错误处理两档策略"范畴（不在审 error-handling，审的是取消被误归类后的并发状态污染）；非规范豁免——"取消路径不经过本方法"仅为注释假设而非 fail-fast 守卫，对照 F10 属守卫缺失。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（cra-index 零同站点；WI 3.1 MA-08 超时取消族为 nop-ai-agent 工具执行取消传播面——不同模块不同失效面，非同案）。分类器映射回码证实：`LlmErrorClassifier` L98-101 `InterruptedIOException` 显式 → TRANSIENT、无 status/timeout 标记异常经兜底 → TRANSIENT；既有两层守卫（`callAsyncAttempt` L195 / 流式 `cancelled` 短路）均为 attempt 边界检查，在途取消必穿至 `decideNonStreamFailure`——初审时序成立；javadoc L287/L421"取消路径不经过本方法"仅为注释假设（F10 守卫缺失定性准确）。面②孪生证实（interceptor `decideNonStreamFailure` L328-330 `classifyStreamError` 对无 status 取消形态同落 TRANSIENT）；面③收窄注记：缓冲层客户端断连走 `BufferedSubscription.cancel()`（不经 `retry()`），同族污染面仅在"客户端断连被 HTTP 客户端以上游 onError 呈现"时可达，维持待证标注不单独立案

### [C3-004] 终态回调/信号回调内的失败决策链无非 NopException 异常护栏——非流式 future 永不完成、流式订阅失去终态信号（永久悬挂）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/ChatServiceFailoverAdapter.java:246-274`、`failover/FailoverStreamFlow.java:251-257, 413-463`、`failover/AiGatewayFailoverInterceptor.java:284-310`
- **证据片段**:
  ```java
  // ChatServiceFailoverAdapter L246-254（whenComplete 回调体无 try/catch 护栏）
  stage.whenComplete((resp, err) -> {
      registry.release(...);
      if (err != null) {
          decideNonStreamFailure(future, ...);   // 内部 selectNextWithProbe/重选/分类均裸奔
      } else if ...
  });
  // FailoverStreamFlow L251-257（只捕 NopException）
  try {
      candidate = adapter.selectNextWithProbe(router, active, request);
  } catch (NopException e) {
      deliverError(e);
      return;
  }                                            // 非 NopException 的 RuntimeException 直接逃逸
  ```
- **严重程度**: P2
- **并发场景**: delegate stage 完成线程（非流式 whenComplete）与委托 publisher 投递线程（流式 `AttemptSubscriber.onError` → `handleStreamFailure` → `startAttemptWithOptions`）；逃逸异常的落点为被丢弃的从属 stage（非流式）或 publisher 投递机制（流式）。
- **触发时序**: T1 某 attempt 以可切换错误失败；T2 完成线程进入 `decideNonStreamFailure`/`handleStreamFailure` 重选路径，途经 `ModelClassRouter.doSelect` → `strategy.select`（`RuleBasedSelectionStrategy` 执行 XLang 规则）或 `probeBrokenCandidates` → `LlmConfigHelper.resolveProviderChainCandidates` → `ResourceComponentManager.loadComponentModel`（VFS 模型解析）；若抛出**非 NopException** 的 RuntimeException → 非流式：异常被 whenComplete 机制吞入无人消费的从属 stage，`future` 永不完成 → 调用方请求永久悬挂；流式：异常从 `onError` 逃逸进委托 publisher 投递机制，订阅者**收不到任何终态信号** → SSE 响应永久悬挂。模块无心跳/总时限兜底（缺席裁定），悬挂不可自愈。
- **现状**: 仓库自身已实证过同族失效——`ChatServiceFailoverAdapter` L297-299 注释记录"release 下溢异常被 whenComplete 吞掉、future 永不完成"（OBS-02 已修单一抛点），但未建立回调体通用护栏；`FailoverMetricsImpl.record`（L142-148）对指标面做了全吞护栏，恰说明决策面缺同等防护。
- **风险**: 配置/规则类运行时错误从"单请求报错"放大为"请求面批量悬挂"且无错误日志可查（whenComplete 丢弃的异常默认不可见）；故障排查只能靠线程 dump。
- **建议**: 三个回调体统一加护栏：`try { 决策链 } catch (Throwable t) { future.completeExceptionally(t) / deliverError(t); }`；`FailoverStreamFlow.startAttemptWithOptions` 的 catch 放宽到 `Throwable`（饱和语义由错误码分支保留）。建议同时评估把重选决策移出完成线程（提交到平台 `IThreadPoolExecutor`），兼收"回调线程不执行重逻辑"之效。
- **失效面枚举**（F3 同族清扫）: ①`ChatServiceFailoverAdapter.callAsyncWithOptions.whenComplete`（本条）；②`AiGatewayFailoverInterceptor.invokeNonStreamingAttempt.whenComplete` L284-310（孪生）；③`FailoverStreamFlow.startAttemptWithOptions`（经 `handleStreamFailure`/`start` 两个调用面，`start` 面异常直接传给 subscribe 调用方可接受，`onError` 面即悬挂面）；④`GatewayStreamingRetryCallback.retry` L87-92 已捕 NopException 返回 null，非 Nop 异常会传给缓冲层（该层在越界模块，行为未知，登记待证）。⑤`ChannelMessageServiceImpl.fanOutToListeners`/`dispatchInbound` 的 whenComplete 均有 whenComplete 内捕获 + 日志，豁免。
- **信心水平**: 很可能（悬挂机制确定；逃逸异常源为规则执行/VFS 解析类 RuntimeException，未能指认单一高概率触发器）
- **误报排除**: 非 F9"吞异常致状态不一致"的重复立案——本条立案面是**终态信号丢失（链悬挂）**，非状态机不一致；非心跳缺席裁定本身——根因是异常逃离决策链而非缺调度器。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现。三回调体无护栏回码证实（adapter `whenComplete` L246-274 决策链裸奔 / `FailoverStreamFlow.startAttemptWithOptions` 仅捕 `NopException` L254 / interceptor `whenComplete` L284-310 孪生）；future 永不完成机制成立——whenComplete 回调内抛出落入无人消费的从属 stage，`future` 本体无人 complete；OBS-02 只修 release 单抛点未立通用护栏的现状标注准确（L297-299 注释在库实证）。面④（GatewayStreamingRetryCallback 非 Nop 异常）已由 C3-round2 闭合——缓冲层 `handleError` L303-310 对 retry 抛 Throwable 有 catch-all + terminate fail-loud；同族跨模块邻接（WI 1.7 C6-001 `catch Exception` Error 逃逸、WI 3.2 MA-01 同步抛出 future 永不完成）为不同站点不同模块，不重复立项，修复批次对齐（回调体 Throwable 护栏统一形态）

### [C3-005] acquire 后复查超限的重选路径把"未真正使用的候选"永久计入 attempted 集——单请求内该候选不可再达

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/ChatServiceFailoverAdapter.java:206-217`、`failover/FailoverStreamFlow.java:261-274`、`failover/AiGatewayFailoverInterceptor.java:252-263`、`routing/ModelClassRouter.java:155`
- **证据片段**:
  ```java
  int count = registry.acquire(candidate.getProvider(), candidate.getAccountKey());
  if (isOverConcurrencyLimit(candidate, count)) {
      registry.release(...);
      return callAsyncAttempt(request, router, cancelToken, attempt);  // 重选
  }
  // ModelClassRouter.doSelect L155: attempted.add(candidate);  ← 选中即入集，与是否真正发起无关
  ```
- **严重程度**: P3
- **并发场景**: 同一模型类下多请求并发选择；`ModelClassRouter` 为 per-execution 实例，`attempted` 集是单请求内跨多次重选共享的状态。
- **触发时序**: T1 请求 R 选中候选 A（`attempted.add(A)`，此刻 `currentCount(A)==limit-1` 健康）；T2 并发请求抢先 acquire 使计数到 limit；T1 acquire 得 count==limit+1 > limit → release → 重选 → `DefaultSelectionStrategy` L37 跳过 attempted 中的 A（即使此刻 T2 已释放、A 已空闲）→ 转投其他候选或（单候选类）直接饱和 fail-loud。此后本请求的被动路径永久不再回到 A。
- **现状**: 注释"超限 → release + 视为饱和跳过 → 重选"声明了跳过语义，但未声明"该候选在本请求后续失败重选中也不可回收"这一副作用。
- **风险**: 高争用下可用候选被过早排除 → 更早的 `ERR_AI_MODEL_CLASS_SATURATED` fail-loud 与切换失衡；不影响计数正确性（acquire/release 配对完整），纯活性/均衡问题。
- **建议**: 超限重选路径在 release 后将候选从 `router.getAttempted()` 移除（需 `ModelClassRouter` 提供 remove 通道），或 `selectNextWithProbe` 的探活遍历允许 revisiting"因超限跳过"的候选。
- **失效面枚举**（F3 同族清扫）: ①`ChatServiceFailoverAdapter.callAsyncAttempt` L211-217；②`FailoverStreamFlow.startAttemptWithOptions` L268-274（重入时 `active=false` 走被动路径，同污染）；③`AiGatewayFailoverInterceptor.invokeNonStreamingAttempt` L257-263。三处同修。
- **信心水平**: 确定（机制与序列可完整复现）
- **误报排除**: 非"无界递归"——`CandidateHealth.isConcurrencySaturated` 用 `>=` 判饱和，重选后候选即被健康视图排除，递归必然终止（已验证 `DefaultSelectionStrategy` 全跳过 → router 抛饱和）；非已声明的硬上限预检语义（B-13 声明的是"不承诺硬上限"，未声明 attempted 不可回收）。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现。机制回码证实：`ModelClassRouter.doSelect` L155 选中即 `attempted.add`（与是否真正发起无关）、`DefaultSelectionStrategy` L37 跳过 attempted、三处超限重选路径（adapter L216 / FailoverStreamFlow L273 / interceptor L262）均无 remove 通道——单请求内不可回收成立；递归终止复核维持（`isConcurrencySaturated` `>=` 语义使重选必然收敛）。纯活性/均衡面、计数配对完整，P3 判级恰当

### [C3-006] FeishuConnector 同 chat 并发首条消息 → 双引擎会话创建，saveMapping 唯一键败者映射丢失（会话上下文分叉）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:310-345, 366, 404-411`；佐证 `nop-ai/model/nop-ai.orm.xml:1462-1465`
- **证据片段**:
  ```java
  ChannelSession session = null;
  try { session = sessionStore.findByChannel(CHANNEL_TYPE, chatId); } catch ...
  String existingSessionId = session != null ? session.getSessionId() : null;
  boolean isNewSession = existingSessionId == null;
  ...
  future = context.getAgentEngine().execute(request);   // miss → 引擎新建会话
  // onExecutionComplete L405-410:
  if (isNewSession && result.getSessionId() != null) {
      try { sessionStore.saveMapping(CHANNEL_TYPE, chatId, result.getSessionId(), agentName); }
      catch (Exception e) { LOG.warn("... saveMapping failed ...", e); }   // 败者仅告警
  }
  ```
- **严重程度**: P3
- **并发场景**: Feishu Stream 客户端投递线程可并发调用 `onMessage`（L133 javadoc 自证存在并发 `onMessage`）；`findByChannel`/`saveMapping` 为同步 DB 操作，两线程间无串行化。
- **触发时序**: T1、T2 同一 chatId 的两条消息并发到达（首条消息尚未完成执行）→ 两线程 `findByChannel` 均返回 null → 均 `isNewSession=true` → 引擎创建会话 S1、S2 → 各自回复用户（重复回答）→ L404-411 两线程 `saveMapping`；`uk_nop_ai_channel_session_channel (channelType, channelId)` 唯一键使后写者抛约束冲突、被 L408-410 捕获仅告警。
- **现状**: `idempotencyKey`（L336）仅去重**同一消息**的 Stream 重投（引擎 duplicate-input 契约），不串行化同 chat 的**不同消息**；映射行的唯一性由 DB 约束兜底（不会脏双行），但"哪个会话存活"由写入顺序偶然决定。
- **风险**: 会话上下文分叉——败者会话的对话历史永久脱离后续路由（`findByChannel` 只能命中单行）；用户视角为 bot 短暂"失忆"。有界、低频（仅首条消息竞态窗口），不涉计数/资源泄漏。
- **建议**: 对 `findByChannel` miss → execute → saveMapping 的序列按 chatId 加进程内互斥（`ConcurrentHashMap<String, Object>` per-chat 锁或 `IResourceLockManager`）；或 saveMapping 改 upsert 并在唯一键冲突时复用已存 sessionId 终止重复会话。
- **失效面枚举**（F3 同族清扫）: ①`FeishuConnector.onMessage`/`onExecutionComplete`（本条，唯一 IChannelConnector 实现——`ChannelConnectorManager.register` 拒绝同型重复，无第二 connector 兄弟）；②同构模式在 `IChannelSessionStore` 其他调用方：仓库内无（仅 FeishuConnector 消费）。
- **信心水平**: 确定（交错序列与约束冲突后果均为在库实证；发生频率低）
- **误报排除**: 非"并发 onMessage 本身"（ratelimit 锁 L467-474 已正确串行化窗口操作）；非 F4 无界账本（唯一键保证单行）；后果有界故按可具体化竞态的低危档 P3。
- **复核状态**: 未复核
- 复核状态: 保留(P3，并入 C6-004 同链工单不独立计数)｜新发现；与 C1-005/C2-005/C6-004 同链三视角（C6-004 为链主登记）。交错序列与唯一键约束回码证实（`findByChannel`→execute→`saveMapping` 无互斥；`uk_nop_ai_channel_session_channel` 位于 nop-ai.orm.xml L1462-1465，初审引 L1462-1465 准确）；触发面随连接器生命周期休眠（`startAll` 零生产接线，C1 复核 grep 证据沿用）——已 P3 维持原判，休眠标注随工单携带；修复与 C6-004（upsert + 同 chat 串行化）同 diff

## 维度集外线索（overlay C5/C9）

### [nop-ai-gateway-CL-005] (C5) FailoverStreamFlow 越窗后缓冲无上界，慢下游按全响应体累积

`FailoverStreamFlow.java:380, 516`。`AttemptSubscriber.onSubscribe` 对上游 `request(Long.MAX_VALUE)` 无背压传导；越窗后 `deliver` 在 demand==0 时无条件 `state.buffer.add(item)`，`AttemptState.buffer` 为无容量 `ArrayDeque`，无任何上限/淘汰。窗口 N=10/T=1000ms 仅约束首段。单请求内存上界 = 完整 LLM 响应体 × 慢下游并发流数。属 C5 内存有界性维度立案候选（降档口径：请求触达面成立——每请求、慢消费者即触发累积）。

### [nop-ai-gateway-CL-006] (C5) mode-1 fan-out 固定池 + 无界队列，非协作挂死监听器的幽灵任务按派发累积

`ChannelMessageServiceImpl.java:227-228, 393-417`。`newFixedThreadPool(max(2, CPU))` 自带无界队列；30s 超时后 worker 被 interrupt，但代码注释自认（L388-390）非协作监听器可无视中断继续占用池线程——每次派发一个永不释放的任务，池耗尽后后续任务在无界队列中排队，内存与派发延迟无界增长。`orTimeout`/interrupt 只解决"协作"面；建议超配套：按监听器粒度的熔断（连续超时后临时摘除该监听器）或队列水位监控。C5/C7 立案候选。

### [nop-ai-gateway-CL-007] (C9/F8) callerToken 取消监听器只注册不移除，长寿命 token 上线性累积

`FailoverStreamFlow.java:308-317`。`registerCallerListener` 调 `callerToken.appendOnCancel(...)` 后流结束时无对应 `removeOnCancel`（F8 register/unregister 不对称）。请求级 token 随请求 GC 无影响；若部署将长寿命 token（会话/连接级）传入 `callStream`，每个流式请求向同一 token 的任务列表（COW 列表，遍历成本随长度线性增长）追加常驻监听器，取消触发时还要遍历执行全部陈旧监听器。C9 热路径/资源对称性线索。

### [nop-ai-gateway-CL-008] (C9) 完成线程/回调线程执行同步重 I/O：重选决策、DB 会话操作、分段回复发送

三处：①`ChatServiceFailoverAdapter`/`AiGatewayFailoverInterceptor` 的 whenComplete 回调内在完成线程同步执行选择、VFS 模型解析、下一 attempt 的同步预检（`checkRateLimit`/`loadConfig`）——若 delegate HTTP 客户端以事件循环线程完成 stage，重试决策阻塞会拖慢复用同一线程的其他在途请求（量化浪费：一次首次模型解析可达数十 ms）；②`FeishuConnector.onMessage` 在 Feishu SDK 投递线程执行 DB `findByChannel`/`updateLastActive`（`ChannelSessionStoreImpl` 同步 ORM）；③`onExecutionComplete` 在引擎完成线程同步执行 `deliverSegmented`（每段一次阻塞 HTTP 发送，长回复可达 N 段串行）。均为回调中阻塞的 C9 性能线索，正确性风险已在 C3-004 覆盖。

---
*初审员：C3 维度盲审（单批次）。方法论：ai-dev/skills/concurrency-resource-audit-prompt.md L26-118/L171-196/L351-377。基线：_tmp/cra-baseline-nop-ai-gateway.txt C3a/C3b。发现计数：P0=0，P1=1，P2=3，P3=2；CL 线索=4（CL-005…CL-008）。*

## 维度复核结论（WI 3.3 独立复核，合并批 C3+C4）

- **复核日期**: 2026-10-20
- **复核人**: C3+C4 维度独立复核员（与初审/深挖/C1+C2 复核员相互独立）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` 校准口径；8 条发现证据行逐条回码、时序论证逐步核验、三态判定（保留/降级/驳回）+ 现状标注；降档口径核对（请求触达面不适用启动期降档；休眠面按 WI 3.2 零生产接线先例条件化降档，逐条附 grep 证据）；与 `ai-dev/audits/cra-index.md` WI 1.1–3.2 台账逐条查重（含 P1 汇总、CL 台账、2.3 节网关登记面）。

### 台账查重结论

C3 维度 8 条与 cra-index WI 1.1–3.2 台账**零同站点重复立项**。三处需区分的邻接面：①WI 2.3 C3-003（P1，BufferedStreamingPublisher 背压断裂）与本维 C3-007 同文件异失效面（背压 vs 降级绕过窗口丢弃）——补充登记非重复，同文件同批修复；②WI 3.1 MA-01（nop-ai-core callStream 取消只传上游/publisher 永不关闭）承载 C3-002 外部面后果——跨单元单工单对齐；③WI 3.1 MA-08（超时取消传播断链，nop-ai-agent 工具执行面）与 C3-008（GatewayStreamingRetryCallback 属性漂移）不同模块不同失效面，仅取消/超时治理轴向邻接，**非同案**。

### 逐条判定表（C3-001…008）

| ID | 初判 | 判定 | 终判 | 核心依据 |
|---|---|---|---|---|
| C3-001 | P1 | 保留 | P1 | 三处引文回码全证实（L380/L400/L481）；L397"不吞数据"自注释矛盾成立；同仓库正确对照先例（BufferedStreamingPublisher `pendingComplete` L328-337）佐证 LOCAL 形态缺终态挂起机制；触发依赖下游 demand 受限（初审已如实标注"很可能"），按"半个成功无恢复语义+可具体化触发链"维持 P1 |
| C3-002 | P2 | 降级 | P3（并入三登记工单） | 竞态窗口回码证实；**upSub 勘误独立验证成立**（登记点 ChatServiceImpl L205-212 先于 callStream 返回、严格先于 AttemptSubscriber.onSubscribe——teardown 时 upSub 必为 null，up.cancel 为 no-op；且 upSub 所指 SubmissionPublisher 订阅的 cancel 本就不中断 HTTP fetch）——外部面无冗余中止路径，后果链强于初审表述；后果由 WI 3.1 MA-01 族承载；与 C1-003/C2-008 合并单工单 |
| C3-003 | P2 | 保留 | P2 | 分类器映射回码证实（InterruptedIOException 显式 TRANSIENT L98-101 + 未知异常兜底 TRANSIENT）；在途取消穿透两层 attempt 边界守卫成立；面②孪生证实；面③收窄注记（缓冲层 cancel 不经 retry，待证面不立案）；live 面（LOCAL opt-in + W7 非流式） |
| C3-004 | P2 | 保留 | P2 | 三回调体无护栏回码证实；future 永不完成机制成立（异常吞入无人消费的从属 stage）；面④已由缓冲层 catch-all 闭合（C3-round2 核对成立）；跨模块同族邻接（1.7 C6-001、3.2 MA-01）不重复立项 |
| C3-005 | P3 | 保留 | P3 | doSelect L155 选中即入集 + DefaultSelectionStrategy L37 跳过 + 三重选路径无 remove 通道回码证实；纯活性/均衡面 P3 恰当 |
| C3-006 | P3 | 保留 | P3（并入 C6-004 链工单） | 交错与唯一键约束证实；触发面随连接器生命周期休眠（零 start 接线，C1 复核 grep 证据沿用）——已 P3 维持，annotate 不降档 |
| C3-007 | P2 | 保留 | P2（子项修正） | 核心链回码证实：降级响应经 StreamingProcessor L173-176 onNext+onComplete 直达缓冲层 → handleItem 窗口未过与前缀同缓冲 → handleComplete flushAll 交付 + terminate(null) 成功终态——Major-1 丢弃契约（L301 仅 handleError 重试路径）被确定性绕过；**后果② recordSuccess 记账反转驳回**（见子项复核）；后果①③维持 |
| C3-008 | P3 | 保留 | P3（证据增强） | sinkCandidate(L95)→metrics(L96-100)→buildHttpRequest(L101) null 判定代码序证实；并证实初审未指出的**即时记账漂移**：retry 中止路径上 switch/resubscribe 指标已先记到从未收到请求的 next；后果有界 P3 维持 |

### 判定统计（C3 维度）

- **保留×7**：C3-001（P1）、C3-003/C3-004/C3-007（P2，C3-007 含子项修正）、C3-005/C3-006/C3-008（P3）
- **降级×1**：C3-002（P2→P3，同站点三登记合并吸收，非证据缺陷）
- **驳回×0**（C3-007 的 recordSuccess 子后果驳回为条目内子项修正，非整条驳回）
- 终判严重度分布：P1×1 / P2×3 / P3×3（合并吸收 1）

### 现状标注分布（C3 维度）

- 流式主链 live（LOCAL 形态 opt-in 装配 / W7 网关形态）：C3-001、C3-003、C3-004、C3-005、C3-007、C3-008
- 渠道栈休眠（连接器生命周期零接线）：C3-006
- 合并工单（不独立计数）：C3-002（三登记）、C3-006（C6-004 链）

### 降档口径核对（grep 证据留痕）

1. **请求触达面不适用启动期降档**：C3-001…005/C3-007/C3-008 全部位于 failover 请求面（adapter opt-in by-name 装配、interceptor gateway.xml 挂载契约），维持原判——与 C1 复核"流式主链 live"裁定及 WI 2.3 已裁定"缓冲流式层为 AI 路由现实路径"一致。
2. **休眠面条件化**：C3-006 触发面 = `FeishuConnector.onMessage`，随 `startAll` 零生产接线休眠（grep `.startAll(` 全仓仅测试树，C1 复核证据沿用）——已 P3，仅标注不降档。
3. C3-002 降级非休眠面驱动（其外部面 live），系三登记合并 + 后果由 WI 3.1 MA-01 族承载的**合并吸收**降档。

### 跨维指针核对

1. **C3-002 = C1-003 = C2-008 同站点三登记**：合并为单一工单（C1 复核裁定沿用）；本复核对 C3-002 证据**表态：同意合并，且独立验证并确认 upSub 勘误**——初审"已 cancel 内部订阅"表述在该窗口不成立，以降级状态行所载勘误为准；站级判级随合并裁定（三登记终判 P3 对齐）。
2. **C3-001 ↔ CL-001**（同站点异失效面：终态截断 vs 无界缓冲）：不重复计数，同文件同批修复。
3. **C3-006 = C1-005 = C2-005 = C6-004 同链**：C6-004 链主，本条随链工单。
4. **WI 3.1 MA-01 族 ↔ C3-002**：外部面后果承载方，跨单元修复对齐（MA-01 侧不得假设 AttemptCancelToken 原语无竞态——C1 复核结论沿用）。
5. **WI 2.3 C3-003 ↔ C3-007**：同文件（BufferedStreamingPublisher/StreamingProcessor 链）异失效面，同批修复，零重复立项。
6. **WI 2.1 CL-021**（raw 载体绕过设施，feishu 移交面）：C3 维度无同案命中。

### 高风险条目子项复核说明

- **C3-007（P2 保留，子项修正）**：后果②（recordSuccess 记账反转）**驳回**——`proceedOnStreamComplete` 在 nop-gateway main 代码中唯一调用点为 `StreamingProcessor` L188，位于 mapped publisher 的 `onComplete` lambda 内、由**源**（HTTP 事件流）正常完成驱动；降级路径中 mapped `onError`（L170-183）将 onNext+onComplete **直接发给缓冲层订阅者**，不经过该 lambda，故 `interceptor.onStreamComplete` → `recordSuccess`（interceptor L457-465）在降级路径与窗口外面**均不可达**——初审触发链"内链 onComplete → proceedOnStreamComplete → recordSuccess"混淆了两个 onComplete（订阅者回调 vs 源完成回调），证据链断裂。后果①（attempt-1 前缀 + 降级 wrapper 以成功终态交付，违反缓冲层 Major-1 丢弃契约）与③（与本地形态 `deliverError` 清缓冲+onError 语义分裂）逐行回码维持；P2 判级不受子项修正影响（用户可见脏输出 + 伪装成功为核心后果）。
- **C3-002（降级 P3 并入）**：upSub 勘误的独立验证路径——`ChatServiceImpl.callStream` L202-233：`eventPublisher.subscribe(匿名订阅者)` 于 L205 **同步**执行（onSubscribe 内 L210 登记 `appendOnCancelTask(subscription::cancel)`），先于 L233 返回 publisher，更先于 FailoverStreamFlow L292 `publisher.subscribe(AttemptSubscriber)`；故竞态窗口 [L109 check, L112 add] 内 `state.upSub` 恒为 null。补充：即使 upSub 已赋值，其 `cancel()` 与 `AttemptSubscriber.onSubscribe` 守卫（L510-513）取消的均是 SubmissionPublisher 侧订阅，均不中断底层 HTTP fetch——初审"upSub.cancel() 只切断 FailoverStreamFlow 的接收面"的方向正确，"该窗口已 cancel"的时态错误予以勘误。
- **C3-001（P1 保留）**：flushAll（L399-405）无 passed 门控 + `handleStreamComplete`（L465-483）无终态挂起——与同 campaign 已登记的 `BufferedStreamingPublisher.handleComplete` L328-337 `pendingComplete` 正确实现形成同仓库双实现分叉，佐证 LOCAL 形态缺陷成立且修复方向明确（对齐 pendingComplete 语义）。

## 最终保留项（C3 维度）

| 工单面 | 条目 | 终判 | 修复方向 | 合并指令 |
|---|---|---|---|---|
| 流式终态冲刷截断（半个成功） | C3-001 | P1 | `handleStreamComplete` 缓冲非空且 demand 耗尽时挂起终态（对齐 BufferedStreamingPublisher `pendingComplete` 语义），由 request() 排空后补发 onComplete | 与 CL-001、C1-001 同文件同批 |
| 取消误分类记账污染（非流式） | C3-003 | P2 | `decideNonStreamFailure` err 分支入口先判 `cancelToken.isCancelled()`/cause 链 CancellationException → completeExceptionally 不记账；或将"取消"提升为独立 ErrorClassification（nop-ai-core 分类器一并补） | 与面②（interceptor 孪生）同 diff |
| 终态回调异常护栏缺失（悬挂） | C3-004 | P2 | 三回调体统一 try/catch(Throwable)：completeExceptionally/deliverError 兜底；catch NopException 放宽 | 与 interceptor 孪生同 diff；跨模块护栏形态对齐 1.7 C6-001 批次 |
| 超限重选 attempted 不可回收 | C3-005 | P3 | 超限 release 后从 attempted 移除（router 加 remove 通道）或探活遍历允许 revisit | 独立（7.1 裁决修/deferred） |
| 同 chat 首消息双会话 | C3-006 | P3 | saveMapping upsert + findByChannel miss→execute→saveMapping 按 chatId 串行化 | 并入 C6-004 链工单 |
| 降级路径绕过窗口丢弃（脏输出+伪装成功） | C3-007 | P2 | 拦截器 onError 降级分支感知缓冲层接管态改 rethrow，或缓冲层 handleComplete 前丢弃未越窗缓冲（对齐 Major-1）；~~recordSuccess 守卫~~（子后果已驳回，不需记账守卫） | 与 WI 2.3 C3-003 同文件同批 |
| retry 属性/指标先行漂移 | C3-008 | P3 | buildHttpRequest 前置 config 校验（null → 不 sinkCandidate 不记账）；或 config==null 回滚属性+指标 | 独立（7.1 裁决修/deferred） |
| AttemptCancelToken check-then-act（三登记） | C3-002 | P3 并入 | appendOnCancel 改 add 后二次复核（或对齐平台 ICancelToken 实现） | 并入 C1-003/C2-008 单工单（站级判级随合并裁定） |
