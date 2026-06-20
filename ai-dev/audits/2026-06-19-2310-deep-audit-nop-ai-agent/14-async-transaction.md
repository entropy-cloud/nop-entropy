# 维度 14：异步与事务模式（含并发/恢复/锁）

## 检查范围

runtime(/coordination/lock/recovery)、team/scheduler、team/flow、reliability、message、engine 关键类；逐类核验并发集合、锁、CAS、事务边界、资源关闭、cancel/fork/lease-lost 路径。

## 第 1 轮（初审）发现

### [维度14-01] cancelSession 收尾窗口与 worker 最终 save 竞态

- **文件**: `engine/DefaultAgentEngine.java`（doExecute 2283/2289/2321-2326；cancelSession else 2007-2017；resumeSession/restoreSession 同构）
- **证据片段**:
  ```java
  // doExecute inner finally
  runningExecutions.remove(sessionId, handle);                 // 2283 handle 已移除
  session.setStatus(ctx.isLeaseLost() ? failed : ctx.getStatus()); // 2289
  // post-finally（save 之前）
  session.replaceMessages(ctx.getMessages());                  // 2321
  sessionStore.save(session);                                  // 2326 此时才落盘
  // cancelSession else 分支（handle==null，与 worker 并发）
  AgentSession session = sessionStore.get(sessionId);          // 2008 同一缓存引用
  session.setStatus(AgentExecStatus.cancelled);                // 2013 直接改共享对象
  ```
- **严重程度**: P2 → **复核降级 P3**
- **现状**: remove(2283) 到 save(2326) 之间 handle 已从 map 移除但 session 未落盘；并发 cancelSession 在 else 分支拿同一 AgentSession 引用 setStatus(cancelled)。AgentSession.status 非 volatile。
- **复核降级依据**: 窗口仅在 executor 完成后（join 2277 返回）才打开——此刻 cancel 取消的是已完成任务，语义无害；InMemorySessionStore（默认）save 是 no-op，竞态实为"worker setStatus 与 cancel setStatus 谁最后写同一引用"，最坏仅"已成功完成 session 显示 cancelled"（误导性状态），无数据丢失/安全/双执行；DBSessionStore 下 else 不 persist，worker save 写正确终态。
- **信心水平**: 高
- **误报排除**: executor.execute(ctx).join() 同步执行；但 cancelSession 在调用方线程并发，二者有真并发。putIfAbsent 只防同 session 两 execute，不防此窗口。
- **复核状态**: **已复核——降级 P2→P3**（窗口真实但影响被高估：cancel 落 post-completion，仅 in-memory 误导性状态，DB 无不一致）。

### [维度14-02] lease-lost 中断无法打断工具 fan-out 的 allOf().join()（double-execution 窗口）

- **文件**: `engine/ReActAgentExecutor.java:1906-1928,2040`；`engine/DefaultAgentEngine.java:1821-1833`(handleLeaseLost)
- **证据片段**:
  ```java
  // ReActAgentExecutor 工具 fan-out
  CompletableFuture<ToolCallOutput> toolFuture = toolManager.callTool(...).thenApply(...);
  if (toolTimeoutMs > 0) { toolFuture = toolFuture.orTimeout(toolTimeoutMs,...).exceptionally(...); }  // 1909 仅 >0 挂超时
  CompletableFuture.allOf(futuresArray).join();    // 1928 join 不可被 interrupt 打断
  if (ctx.isCancelRequested()) { handleCancellation(...); break; }  // 2040 join 返回后才检查
  // DefaultAgentEngine.handleLeaseLost
  Thread t = handle.thread; if (t != null) { t.interrupt(); }  // 1831 仅设 interrupt 标志
  ```
- **严重程度**: P2
- **现状**: handleLeaseLost 用 t.interrupt() 中断 worker，设计意图是立即中止以防 double-execution。但 CompletableFuture.allOf().join() 按 JDK 契约不可中断（不抛 InterruptedException，interrupt 标志被吞），阻塞到所有 future 完成。toolTimeoutMs<=0（合法 opt-out）且工具挂死时 join 永不返回；默认 toolTimeoutMs=300_000（5min）下 abort 被延迟最多 5 分钟。
- **风险**: 跨进程 double-execution 真实窗口（正是 plan 273 要消除的）。lease 被 TTL 过期/他实例抢占后，接管实例已开始 ReAct，原实例 zombie 仍跑工具写 session/checkpoint。LLM 调用路径（callChatWithTimeout 用 f.get(timeout)）可中断，但工具 fan-out 路径不可。
- **建议**: allOf 之后对 allOf 自身挂 orTimeout（与 lease-lost 联动），或 join 包 Future+轮询 ctx.isCancelRequested()；退一步 ctx.isLeaseLost() 为 true 时让工具 future 立即 complete exceptionally。
- **信心水平**: 中-高
- **误报排除**: toolTimeoutMs<=0 是文档化 opt-out（1900-1905 注释），非死代码；CF.join() 不可中断是 JDK 契约；handleLeaseLost 确只 interrupt 不 cancel 工具 future。
- **复核状态**: **已复核——成立（维持 P2）**。修正：默认 toolTimeoutMs=300_000（:473）有界 5min，"永不返回"仅 opt-out 成立；但默认下 abort 仍被延迟，存在有界 double-execution 窗口。

### [维度14-03] LLM 超时包装与 agent 执行共用同一 executor（固定池集成→饱和群体伪超时）

- **文件**: `engine/DefaultAgentEngine.java:3124`(resolveExecutor)；`:1875-1895`(agentExecutor)；`engine/ReActAgentExecutor.java:2261-2286`(callChatWithTimeout)
- **证据片段**:
  ```java
  // resolveExecutor
  .timeoutExecutor(getAgentExecutor())        // 3124 与 agent 执行池同一引用
  // 默认 agentExecutor
  agentExecutor = Executors.newCachedThreadPool(...);   // 1888
  // callChatWithTimeout
  CompletableFuture<ChatResponse> f = CompletableFuture.supplyAsync(() -> chatService.call(request,null), timeoutExecutor);  // 2265
  return f.get(llmTimeoutMs, TimeUnit.MILLISECONDS);  // 2268
  // 代码注释自承风险但默认接线仍共用
  // "a fixed pool risks self-deadlock if the ReAct LLM-call timeout wrapper dispatches back to the same saturated pool"  // 1885-1887
  ```
- **严重程度**: P2
- **现状**: callChatWithTimeout 把 chatService.call 经 supplyAsync 投到 timeoutExecutor，3124 赋为 getAgentExecutor()——即 agent 执行池。默认缓存池（无界）不死锁；但 setAgentExecutor（:1903 文档化选项，:1899-1900"fixed-size pool for resource-constrained deployments"）允许换固定池。固定池大小 N 且 N agent 并发（各占线程阻塞 f.get(llmTimeoutMs)），LLM supplyAsync 排队等不到线程→f.get 必超时→所有并发 agent 群体性伪超时。
- **风险**: 资源受限部署（恰是换固定池场景）高并发触发群体伪超时→有效宕机；f.cancel(true) 不中断真正在跑的 chatService.call，provider 故障期累积 zombie LLM 线程。
- **建议**: 为 callChatWithTimeout 配独立有界/缓存 timeoutExecutor，从默认接线消除 footgun；或 setAgentExecutor 检测固定池时 fail-fast 要求独立 timeoutExecutor。
- **信心水平**: 中-高
- **误报排除**: 3124 确为同一引用；默认缓存池不触发，故非"当前错误行为"，定 P2；注释自承风险证明已知但未从默认接线消除。
- **复核状态**: **已复核——成立（维持 P2）**。修正：默认缓存池安全，触发需显式固定池+高并发；风险已被代码注释记录（减轻项）。

### [维度14-04] DBMessageService 异步消费结果回调不在完成线程重置 tenant

- **文件**: `message/DBMessageService.java:562-589`(handleConsumerResult)；`125-127`(currentTenant)
- **证据片段**:
  ```java
  private void handleConsumerResult(String sid, Object result) {
      if (result instanceof CompletionStage) {
          ((CompletionStage) result).whenComplete((r,e) -> {   // 565 回调线程不确定
              ... try { handleConsumerResult(sid, r); } ...     // 577 仍在完成线程
          });
      } else if (result instanceof ConsumeLater) { ... }
      else { markConsumed(sid); }   // 587 用 currentTenant() 注入 tenant WHERE
  }
  private String currentTenant() { return tenantResolver.resolveTenantId(); }  // ThreadLocal backed
  ```
- **严重程度**: P3
- **现状**: 同步路径 tenant 正确（poller 线程 ThreadLocal 已设）。但 CompletionStage 分支 whenComplete 回调运行在"完成该 stage 的线程"（线程池复用），ThreadLocal tenant 不可预测（null 或残留别的任务 tenant）。markConsumed 按 currentTenant() 决定是否加 AND TENANT_ID=?。
- **风险**: null tenant→SQL 无 tenant WHERE，但 SID 全局唯一 UUID，UPDATE 仍命中正确行（无害）；错误 tenant→WHERE SID=? AND TENANT_ID=<wrong> 命中 0 行→消息停 CLAIMED→stale-claim sweep（5min）重置重投，at-least-once 不丢数据，但租户隔离不变量削弱、产生延迟重投噪声。
- **建议**: pollTopic claim 成功后捕获 tenant 传入 handleConsumerResult，whenComplete 内显式 ThreadLocalTenantResolver.set/clear（与 MemberFanOutDispatcher.dispatch 254-268 对称）。
- **信心水平**: 中
- **误报排除**: ThreadLocalTenantResolver 静态 ThreadLocal 不跨线程；whenComplete 回调线程不可预测；SID 全局唯一使 null tenant 情形无害，仅错误 tenant 产生延迟重投，故 P3。
- **复核状态**: 未复核

### [维度14-05] ScheduledRecoveryManager 运行期可变配置字段非 volatile

- **文件**: `runtime/recovery/ScheduledRecoveryManager.java:145/157/171/181`(字段)；`:231-237/260-266/320-326/287-294`(setter)；`:417/427/445/457`(scanOnce 读取)
- **证据片段**:
  ```java
  private IOrphanRecoveryHandler orphanRecoveryHandler = NoOpOrphanRecoveryHandler.noOp();  // 145 非 volatile
  private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;                                    // 181 非 volatile
  public void setTimeoutSeconds(long timeoutSeconds) { ... this.timeoutSeconds = timeoutSeconds; }  // 293 无同步
  // scanOnce 在 scheduledExecutor 线程读
  long timeoutThresholdMillis = now - timeoutSeconds * 1000L;   // 417
  ```
- **严重程度**: P3
- **现状**: start()（synchronized）启动周期扫描后扫描在独立 scheduledExecutor 线程。timeoutSeconds、3 handler 字段非 volatile，setter 无同步。start 后热更新配置，扫描线程可能长期看不到新值（JMM 不保证非 volatile 写对其他线程可见），long 还可能读到撕裂值。
- **风险**: 热更新"看起来生效"但扫描线程用旧阈值/旧 handler，运维误判。实际部署通常 start 前配置（设计预期），触发概率低。
- **建议**: 4 字段标 volatile，或 setter 加 synchronized 与 start/stop 同锁。DBMessageService 的 pollIntervalMs/maxBatch/staleClaimTimeoutMs/sweepIntervalMs 同模式。
- **信心水平**: 中
- **误报排除**: start/stop synchronized 且 scheduleHandle volatile（生命周期正确），问题仅限 4 业务配置字段；设计预期 start 前配置，故 P3。
- **复核状态**: 未复核

## 已核验正确（未报告）

DbSessionTakeoverLock/DbDaemonCoordinator 的 INSERT+条件 UPDATE CAS（含 portable isDuplicateKey）；claimTask/completeTask/reclaimTask 的 affected-row-count CAS；DeferredAckMailbox 单锁全守护；InMemoryActorRuntime per-actor 单线程消费循环+steering ConcurrentLinkedQueue+volatile；AgentActor synchronizedList 快照；ThresholdBreaker/SessionGoalTracker per-entry synchronized；TeamTaskSchedulerDaemon per-team scan-lease try/finally release；DBMessageService try-with-resources+stale-claim sweep；所有 DB 写 raw JDBC auto-commit（无长事务含 LLM 调用）。无 P0/P1 级硬红线或当前错误行为（除已在 13-01 报告的 tenant 泄漏）。

## 维度复核结论

[维度14-01] 独立复核：**降级 P2→P3**（cancel 落 post-completion，影响仅误导性状态）。[维度14-02] 独立复核：**成立 P2**（修正：默认有界 5min，opt-out 才无界）。[维度14-03] 独立复核：**成立 P2**（默认安全，触发需固定池）。[维度14-04/05] 复核未发现反证，保留 P3。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 14-01 | P3 | engine/DefaultAgentEngine.java | cancel 收尾窗口竞态（已降级，影响仅误导性状态） |
| 14-02 | P2 | engine/ReActAgentExecutor.java | lease-lost 中断无法打断工具 fan-out join（double-execution 窗口） |
| 14-03 | P2 | engine/DefaultAgentEngine.java | LLM 超时与 agent 执行共用池（固定池→群体伪超时） |
| 14-04 | P3 | message/DBMessageService.java | 异步消费回调完成线程 tenant 丢失 |
| 14-05 | P3 | runtime/recovery/ScheduledRecoveryManager.java | 业务配置字段非 volatile |
