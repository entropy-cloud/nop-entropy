# C3 异步链路正确性 — nop-cluster（WI 4.2）

- 审计执行时间：2026-10-21 02:40 批次（盲审第 1 轮初审，单维度 C3）
- 审计对象：`nop-cluster/` 7 源码根（nop-cluster-core、nop-rpc-cluster、nop-cluster-nacos、nop-cluster-sentinel、nop-hazelcast-core、nop-hazelcast-cache、nop-cluster-admin），主代码 61 f / 测试 13 f
- 方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md` 共享前缀 + C3 正文（L171–196）+ 附录 A 格式（L351–377）
- 基线：`_tmp/cra-baseline-nop-cluster.txt` C3a（异步链）/C3b（发现·回调面）节，仅用于圈定重点；数量与站点结论均按 live code 人工复现

## 审计范围与口径

### owner doc 零覆盖声明

本批次为盲审：未读取 `ai-dev/audits/**`（本文件除外）、`ai-dev/plans/**`、`ai-dev/bugs/**`、`ai-dev/lessons/**`、`ai-dev/backlog/**`，未查 git log。`docs-for-ai/` 中无 nop-cluster 的 owner doc（零覆盖），因此全部契约判断以源码内 javadoc 与接口注释为准（关键契约：`IServerChooser.chooseFromCandidates` "如果列表非空，则此对象不应该为null"、`IServerChooser.getServers` "每次都返回一个新的List，返回的结果列表允许被修改"、`ILeaderElector.whenElectionCompleted` "每一次发生leader选举都会返回一个新的CompletionStage对象"、`Cancellable` "清理函数的执行异常会被自动捕获并忽略"）。

### 与 C1/C2 已立案条目的边界声明

C1+C2 已立案 12 条（站点摘要：Nacos 事件线程原地改写、选主复活竞态、订阅快照无版本守护、cleanupTasks、cleanupTasks 无锁迭代、computeIfAbsent 锁内远程调用、选主写站点锁覆盖等）。本维度按"同站点不同失效面可立案，同失效面不重复"裁定：

- **NacosNamingService.getServiceInfo/getServiceInfoListener**：已立案的"订阅快照无版本守护"（事件先于 init 快照到达、无版本比较）与本文件 C3-005（异常路径退订不对称导致订阅泄漏）是**同站点不同失效面**：前者是时序/版本面，后者是 register/unregister 配对（F8）面。已立案的"Nacos 事件线程原地改写"（copyFromInstance 原地改共享 ServiceInstance）与 C3-006（cleanupTasks 在事件分发线程上同步执行导致事件线程阻塞）也是同站点不同失效面：前者是数据写面，后者是回调执行模型/阻塞面。
- **AbstractLeaderElector**：已立案的"选主复活竞态"（onElectionCompleted isDone→换新 promise 的复活语义）与"选主写站点锁覆盖"（leaderEpoch/promise 写站点的锁覆盖面）分别覆盖复活与写可见性；本文件 C3-004 只立**等待者侧** `whenElectionCompleted()` 读取-attach 时序面（等待者取得旧 promise 后被 doStop/onRestartElection 原地 cancel → 虚假取消/立即异常），不重复复活面与锁覆盖面。`newElection()` 无同步替换问题已并入 C3-004（全仓零调用方，标注潜在面）。
- **cleanupTasks 列表本身**的并发（addCleanupTask/getCleanupTasks）已被 C1/C2 立案，本文件不重复；C3-006 只审其执行位置（Nacos 通知线程内联执行）。
- **computeIfAbsent 锁内远程调用**（CachingNamingService:37 / NacosNamingService:175 同步路径）已被立案；本文件 CL-009 只立**异步 miss 路径无并发去重**的雪崩面（CachingNamingService:46–54），属 C9 线索，不重复锁面。

### 降档口径

本维度审计面均为长驻运行期面（RPC 调用链、服务发现监听、选主轮询、Sentinel 流控挂钩、autoUpdate 定时刷新），启动期降档不适用；但启动期 wiring 缺陷（HazelcastProvider）按惯例列为 overlay 线索（CL-010）而非 C3 发现。

### 异步入口清单（C3 §1 盘点结论）

| 入口 | 形态 | 消费方式 | 判定 |
|---|---|---|---|
| ClusterRpcClient.callAsync:60–94 | getServersAsync→thenCompose→手写 retry 递归（whenComplete 内 re-invoke） | 手工 CompletableFuture 转交调用方 | **C3-001/C3-003** |
| ClusterRpcClient.call:96–126 | 同步 retry 循环 | 直接返回/抛出 | 契约面（C3-003 兄弟面） |
| BroadcastRpcClient.callAsync:52–90 | getServersAsync→thenCompose→N 路 fanout→waitAll/waitAnySuccess→thenApply | 返回聚合 future | **C3-002** |
| ClusterRpcServiceInvoker.invokeAsync:97–106 / HttpRpcServiceInvoker.invokeAsync:83–93 | 同步前置校验 + FutureHelper.reject → callAsync | 返回 future | 无缺陷（reject 路径完整） |
| IDiscoveryClient.getInstancesAsync 默认实现 | FutureHelper.futureCall（**同步内联执行**，无真实异步跳变） | 返回已解析 future | 无缺陷（异常已捕获，不落 commonPool） |
| NacosNamingService.ServiceInfoListener.onEvent:214–220 | Nacos 事件分发线程回调 | 内联执行 | **C3-005/C3-006** |
| NacosConfigService getConfigInfo Listener:149–159 | Nacos 配置推送线程回调（getExecutor 内联） | 内联执行 | **C3-011** |
| AbstractLeaderElector electionPromise / listener 循环:149–225 | 定时器线程完成 promise + 顺序回调 | promise 消费者 + CopyOnWriteArrayList 遍历 | **C3-004** |
| AbstractPollingLeaderElector schedule→checkElection→scheduleCheck:16–31 | 单次调度自续链 | 调度链契约靠子类自觉 | **CL-011** |
| SentinelFlowControlRunner.runAsync:44–100 | task future→whenComplete 挂 Entry.exit | 返回 whenComplete 结果 | **C3-007/C3-008** |
| AutoRegistration start/refreshRegistration/stop:189–229 | globalTimer scheduleWithFixedDelay | try/catch 全包 | **C3-010** |
| HazelcastCache getAsync/putAsync 等:161–186 | Hazelcast IMap 异步委托 | 直通 | 无缺陷（异常自然传播） |

## 发现

### [C3-001] ClusterRpcClient.callAsync 重试回调内同步异常被吞，返回 future 永不完成，调用方永久挂起

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcClient.java:69–93`（配合 `http/HttpRpcClientInstanceProvider.java:33–37`）
- **证据片段**:
  ```java
  CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
  IntConsumer fn = new IntConsumer() {
      public void accept(int retryTimes) {
          ServiceInstance instance = serverChooser.chooseFromCandidates(instances, request);
          getRpcClient(instance, request).callAsync(serviceMethod, request, cancelToken)
              .whenComplete((ret, err) -> {
                  if (err == null) { future.complete(ret); }
                  else {
                      if (retryTimes >= retryCount || ... ) { future.completeExceptionally(err); }
                      else {
                          if (instances.size() > 1) { instances.remove(instance); }
                          this.accept(retryTimes + 1);   // ← 此处同步抛出异常时无人捕获
                      }
                  }
              });   // whenComplete 的返回 stage 被丢弃
      }
  };
  fn.accept(0);
  return future;
  ```
- **严重程度**: P1
- **并发场景**: HTTP worker 线程发起 callAsync → 完成服务发现后由"完成上一跳 RPC 的线程"（HTTP 客户端 IO 线程）执行 whenComplete 回调并在其上同步发起下一跳重试。
- **触发时序**: T1 在 IO 线程完成第 0 跳且 err≠null、可重试（err 为 NopConnectException、retryCount≥1、instances.size()≥2）→ T1 进入 else 分支 :83–85，移除故障实例后调 `this.accept(1)` → T2（同一 IO 线程，重试体内）执行 :73 `chooseFromCandidates`：若为 MasterServerChooser 且此刻 leaderId 为空/leader 不在候选列表则返回 null（见 C3-003）→ :74 `getRpcClient(null, request)` → `clientProvider.getRpcClientInstance(null)` 在 HttpRpcClientInstanceProvider:35 解引用 `instance.getAddr()` 抛 NPE → 该 NPE 只被**丢弃的 whenComplete 返回 stage** 捕获 → `future`（调用方唯一持有的引用）永远未 complete/completeExceptionally → 调用方 syncGet/allOf 无限期等待。`chooseFromCandidates`/`getRpcClientInstance` 抛任意 RuntimeException 时同理。
- **现状**: 重试体内全部同步调用（chooseFromCandidates、clientProvider.getRpcClientInstance、callAsync 的同步抛出部分）无 try/catch，异常无法到达 `future`；首跳 `fn.accept(0)` 在 thenCompose lambda 内执行会被 CompletableFuture 机制捕获，**唯独重试跳的异常丢失**。
- **风险**: 对应 C3-007 的 Sentinel entry 泄漏（entry.exit 挂在永不完成的 future 上）；上层若有 per-call 超时则表现为海量超时 + 每次泄漏一个在途底层连接与 Sentinel entry；若无超时则工作线程/调用方永久挂起，负载上升必然放大（重试路径本身就是网络异常路径）。
- **建议**: 将 `accept` 重试体包 try/catch，catch 内 `future.completeExceptionally(NopException.adapt(e))`；或把重试改写为 `FutureHelper.futureCall`/递归 `whenCompleteAsync` + `handle` 链，保证任何路径都终结 `future`；`chooseFromCandidates` 返回 null 时显式转 `ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE` 而非放任 NPE。
- **失效面枚举**: 全模块"回调内重操作 + whenComplete 返回值被丢弃"的兄弟站点逐一核对——(1) ClusterRpcClient.callAsync:74–88（本条，唯一重试递归站点）；(2) BroadcastRpcClient.callAsync:78–84（thenApply 回调仅调 cancellable.cancel/syncGet，Cancellable 内部吞异常，不丢终结信号）；(3) SentinelFlowControlRunner.runAsync:71–81（whenComplete 返回值**被返回**，异常可见，但 exit 可能不执行→另立 C3-007）；(4) FutureHelper.waitAnySuccess:465–475（nop-api-core 基础设施，回调仅 complete/completeExceptionally，不可抛）；(5) AbstractLeaderElector 4 个 listener 循环:183–218（逐 listener try/catch，不丢）。同族仅 (1) 失守。
- **信心水平**: 确定
- **误报排除**: 非原语误用报错——这里不是"whenComplete 用法"本身，而是"手工转交 future 的完成路径未闭合"；也非误报校准中的"无状态单例被并发调用"。触发序列依赖 MasterServerChooser 返回 null 或 provider 抛错，二者均有明确定义（C3-003、NPE 证据行）。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现未修复（live 默认装配路径复核证实：rpc-cluster-defaults.beans.xml AbstractRpcProxyFactoryBean→ClusterRpcProxyFactoryBean，service-mesh 缺省关闭时全部 @RpcClient 代理经此链；首跳异常被 CF 捕获 vs 重试跳异常丢失的分野回码成立；getRpcClientInstance 首解引用 NPE 证实）

### [C3-002] BroadcastRpcClient waitAll 广播部分失败无聚合、不中止兄弟调用、成功响应被整体丢弃（半个成功）

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/BroadcastRpcClient.java:65–90`
- **证据片段**:
  ```java
  Cancellable cancellable = new Cancellable();
  if (cancelToken != null) { cancelToken.appendOnCancel(cancellable::cancel); }
  for (ServiceInstance server : servers) {
      futures.add(clientProvider.getRpcClientInstance(server)
              .callAsync(serviceMethod, request, cancellable));
  }
  if (waitAll) {
      return FutureHelper.waitAll(futures).thenApply(r -> buildResponse(futures));
  } else {
      return FutureHelper.waitAnySuccess(futures).thenApply(r -> { cancellable.cancel(); return (ApiResponse<?>) r; });
  }
  protected ApiResponse<?> buildResponse(List<CompletionStage<ApiResponse<?>>> futures) {
      return FutureHelper.syncGet(futures.get(0));   // 仅取第一台
  }
  ```
- **严重程度**: P1
- **并发场景**: 单调用方线程扇出 N 路并发 RPC（各自在不同 IO 线程完成），FutureHelper.waitAll=CompletableFuture.allOf 语义（任一失败则整体异常）。
- **触发时序**: T1 向 3 实例广播一个写操作 → T2 实例 1 返回业务错误（非 NopConnectException）→ allOf 在实例 2/3 也完成后整体异常完成 → T3 调用方只收到一个异常：无法得知哪些实例已成功应用写操作（部分成功状态不可见，无 per-server 结果/错误聚合），且 `cancellable` 在该路径**从不触发**（仅外层 cancelToken 或 waitAnySuccess 成功路径触发），失败的广播无法中止其余实例上仍在执行的请求；即使全部成功，`buildResponse` 也只返回 `futures.get(0)` 一台的响应，其余 N-1 台的响应被静默丢弃。
- **现状**: 广播语义 = "全部成功才成功，失败即整体失败但不给部分失败明细、不中止剩余执行、成功也只看第一台"。
- **风险**: 广播典型用途（配置下发/缓存失效/状态刷新等写操作）遭遇单实例故障时必然产生"半个成功"且不可恢复判定（F2）；运维无法从异常判断哪些节点需要补偿；长尾实例让调用方陪跑到最慢响应。
- **建议**: waitAll 失败路径聚合 per-server 结果（复用 `FutureHelper.waitAll` + 逐 future `handle` 收集 ApiResponse/Throwable，返回携带实例级明细的聚合异常或部分结果对象）；失败路径调用 `cancellable.cancel()` 中止兄弟调用；`buildResponse` 明确广播响应语义（首台响应还是应聚合）并写入 javadoc。
- **失效面枚举**: 模块内 fan-out/聚合站点完整清单：(1) BroadcastRpcClient.callServersAsync:70–85（本条）；(2) ClusterRpcClient.callAsync:64–93（串行 fail-over，逐跳结果可见，异常逐跳 completeExceptionally——无此缺陷，重试异常面归 C3-001）；(3) FutureHelper.waitAll/waitAnySuccess/waitAny（nop-api-core 基础设施，仅审使用方式：本条即 waitAnySuccess 之外的 waitAll 使用面）；(4) NacosNamingService.updateInstances:268–276 cleanupTasks 扇出（逐任务 try/catch warn，无聚合但有逐项日志——非 RPC 扇出，不立项）。同族仅 (1) 失守。
- **信心水平**: 确定
- **误报排除**: 不是"广播必须全部成功"这一已声明边界——代码与 javadoc 均未声明该限制，且无部分失败明细使该语义不可运维；也不属于"低频路径粗粒度同步合理"的校准项：广播是运行期主路径而非启动/管理路径。
- **复核状态**: 未复核
- 复核状态: 降级(P2，携回升条款)｜休眠面——BroadcastRpcProxyFactoryBean 全仓 grep 零接线（无任何 beans/应用配置实例化，复核独立复现）；waitAll=allOf 语义与 buildResponse 仅取 get(0)、失败路径 cancellable 从不触发均回码成立；任一部署声明 broadcast proxy 即回升 P1

### [C3-003] MasterServerChooser.chooseFromCandidates 违反契约返回 null，异步链下游零 null 防护

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/chooser/MasterServerChooser.java:38–49`（对照契约 `chooser/IServerChooser.java:24–31`）
- **证据片段**:
  ```java
  // IServerChooser.java:29  @return 从备选列表中选择的实例。如果列表非空，则此对象不应该为null
  @Override
  public ServiceInstance chooseFromCandidates(List<ServiceInstance> instances, R request) {
      String leaderId = leaderObserver.getLeaderId();
      if (StringHelper.isEmpty(leaderId)) return null;      // 列表非空仍返回 null
      for (ServiceInstance instance : instances) {
          if (instance.getInstanceId().equals(leaderId)) return instance;
      }
      return null;                                          // leader 不在候选列表
  }
  ```
- **严重程度**: P2
- **并发场景**: 选主轮询线程（AbstractPollingLeaderElector→checkElection，globalTimer worker）异步维护 leaderEpoch；HTTP worker 线程在 RPC 选择实例时读取。leader 视图为最终一致，读取方可随时落在"无主/leader 未注册"窗口。
- **触发时序**: T1 请求线程在 leader 选举完成前（或 leader 实例尚未出现在发现列表时）调用 ClusterRpcClient.callAsync → `getServersAsync` 返回非空候选 → :73 `chooseFromCandidates` 因 `getLeaderId()==null` 返回 null → :74 `getRpcClient(null,...)` → HttpRpcClientInstanceProvider:35 NPE。同步路径 `call`:105 同 NPE，裸 NPE 掩盖"无可用主节点"的真实错误码；重试路径则触发 C3-001 的永久挂起；`IServerChooser.chooseServerAsync:20–22` 的其他调用方拿到的 future 值为 null，下游解引用即 NPE。
- **现状**: 接口 javadoc 明言"列表非空则不得返回 null"，MasterServerChooser 是唯一违反者（LoadBalanceServerChooser 仅在空列表时返回 null，与调用方 :65 的空检查配对后一致）。
- **风险**: 所有依赖"候选非空⇒结果非空"假设的异步/同步链在选主窗口期退化为裸 NPE 或挂起，且异常不含 serviceName 等上下文，排障成本高。
- **建议**: 二选一并声明：(a) MasterServerChooser 在无主时抛 `ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE`（带 ARG_SERVICE_NAME）保持非 null 契约；(b) 修改接口契约 javadoc 允许 null，并给 ClusterRpcClient.call/callAsync 与 chooseServerAsync 补 null→错误码转换。优先 (a)，改动面最小。
- **失效面枚举**: `chooseFromCandidates` 全部实现/近似实现：(1) MasterServerChooser:39–48（失守）；(2) LoadBalanceServerChooser:37–45（空列表返回 null——调用方 ClusterRpcClient:65、BroadcastRpcClient:61 均先做空检查，配对成立；但直接调 `chooseServerAsync` 的第三方调用方未有空检查保护，属契约边界内）；(3) MasterFirstLoadBalance.chooseMaster:47–55 返回 null 但其 choose():38–43 有 fallback 兜底（无缺陷）；(4) ClusterRpcClient.call:98–107 / callAsync:64–93 两个消费面 + IServerChooser.chooseServerAsync:20 默认方法消费面（均无 null 防护，同族共修点）。
- **信心水平**: 确定（NPE 链为代码级可证；"选举窗口期"触发前提由 lease 机制必然提供）
- **误报排除**: 不是"看到共享字段就报线程不安全"——本条是契约违反 + 下游异步链假设破裂（C3 §5 时序假设），与 C1/C2 已立案的选主竞态条目失效面不同（本条审的是消费端契约，不是选主写端）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（IServerChooser:29 契约原文与 MasterServerChooser:44-54 双 null 返回点回码成立；live 面——经缺省 chooser 装配可达，且为 C3-001 挂起链的现实触发源之一）

### [C3-004] electionPromise 等待者侧时序：原地 cancel 旧 promise 导致等待者虚假取消；重启后 promise 未重建；newElection 无同步替换（潜在）

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractLeaderElector.java:33,120–124,156–181,213–225`
- **证据片段**:
  ```java
  protected volatile CompletableFuture<LeaderEpoch> electionPromise = new CompletableFuture<>();
  public CompletionStage<LeaderEpoch> whenElectionCompleted() { return electionPromise; }   // 无同步读
  protected void newElection() { this.electionPromise = new CompletableFuture<>(); }        // 无 synchronized
  protected synchronized void onRestartElection() {
      this.leaderEpoch = null;
      CompletableFuture<?> promise = this.electionPromise;
      if (promise != null) promise.cancel(false);          // 原地 cancel
      this.electionPromise = new CompletableFuture<>();
  }
  ```
- **严重程度**: P2
- **并发场景**: globalTimer 轮询线程执行 checkElection→onRestartElection/onElectionCompleted；业务线程调用 `whenElectionCompleted()` 挂接选举完成回调；shutdown 线程执行 doStop。
- **触发时序**: T1 业务线程执行 :158 读取字段得到 promise P1，尚未挂接回调 → T2 轮询线程因租约丢失进入 onRestartElection :169 `P1.cancel(false)` 并替换为 P2 → T1 在已取消的 P1 上挂接 continuation → 立即收到 CancellationException，误判"选举失败"，而选举实际在 P2 上继续并可能成功——等待者永远看不到 P2（`whenElectionCompleted` 已返回）。另一时序：doStop:121 cancel 后生命周期重启（LifeCycleSupport start→stop→start），doStart 不重建 promise，重启后到下一次 onElectionCompleted 之间所有等待者拿到的是已 cancel 的 P1，立即异常而非等待。第三时序（潜在）：:161 `newElection()` 不持锁替换 promise，与 synchronized 的 onElectionCompleted 交错可使新 promise 永无人 complete。
- **现状**: `whenElectionCompleted()` 单次 volatile 读 + promise 原地 cancel/替换，接口 javadoc 承诺"每次发生 leader 选举都会返回一个新的 CompletionStage 对象"，但调用方无法原子地获得"当前选举"的 promise 并安全挂接。
- **风险**: 平台用户按 ILeaderElector API 等待选举结果时得到虚假失败（CancellationException≠选举失败），触发上层误降级/误重启；仓内当前无调用方（nop-stream JobCoordinator 已注释禁用该用法），属 API 契约面缺陷，但 protected `newElection()` 是子类可触达面。
- **建议**: 参考已立案的复活面修复统一收敛 promise 生命周期：`whenElectionCompleted()` 改为 synchronized 读；cancel 前先替换字段（cancel 不再作用于任何可被读取到的引用）；doStart 时重建 promise；`newElection()` 加 synchronized 与 onRestartElection/onElectionCompleted 互斥。
- **失效面枚举**: promise 生命周期站点全清单：(1) 字段初始化 :33（初始 promise 无主，同面）；(2) whenElectionCompleted :157–159（本条）；(3) newElection :161–163（无同步，仓内零调用方——潜在面，并入本条）；(4) doStop :120–124 与 onStop :222–224（两处 cancel，同面重复）；(5) onRestartElection :165–171（本条触发源）；(6) onElectionCompleted :173–181（复活面——**C1/C2 已立案，不重复**）。同族等待者挂接点仅 whenElectionCompleted 一处。
- **信心水平**: 很可能（交错序列代码级确定；仓内暂无生产调用方，故非当前主路径）
- **误报排除**: 非误报校准中"FutureTask/原语使用本身"——缺陷在复合时序（读取与 cancel/替换无原子性）；已显式声明与 C1/C2"选主复活竞态""选主写站点锁覆盖"的边界：本条只立等待者挂接面。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜公开 API 契约面——whenElectionCompleted 全仓生产调用方为零（grep 仅测试；JobCoordinator:584 javadoc 显式禁用为激活触发器），现状标注成立；whenElectionCompleted:158 无同步读、onRestartElection:169 原地 cancel、doStart 不重建 promise 均回码成立

### [C3-005] getServiceInfo 异常路径只回滚 NacosException：非 NacosException 逃逸导致订阅泄漏 + 监听器重复累积

- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:178–199`
- **证据片段**:
  ```java
  boolean subscribed = false;
  try {
      namingService.subscribe(serviceName, groupName, listener);
      subscribed = true;
      List<Instance> instances = getNamingService().getAllInstances(serviceName);
      listener.initInstances(instances);
      return serviceInfo;
  } catch (NacosException e) {              // 只捕 NacosException
      if (subscribed) listener.unsubscribe();
      throw newError(e);
  }
  ```
- **严重程度**: P2
- **并发场景**: 多个业务线程首次并发触发某服务的 `getInstances`（instanceCache.computeIfAbsent 未命中者串行）；Nacos 客户端事件分发线程可能已开始向新 listener 投递事件。
- **触发时序**: T1 线程进入 getServiceInfo，:188 subscribe 成功（Nacos 侧已注册 listener）→ :191 `getAllInstances` 抛出**非 NacosException**（典型：Nacos 客户端 shutdown/重建期的 IllegalStateException、updateInstances 内部 RuntimeException——如 initInstances 路径 ServiceInstance.compareTo:164 对异常数据抛 NPE）→ catch 不匹配 → 异常直接上抛 → computeIfAbsent 未放入缓存 → T2 下一次 getInstances 重新走 getServiceInfo：**再 subscribe 一个新 ServiceInfoListener** → 旧 listener 永远残留在 Nacos 客户端订阅表中并持续接收事件（其 onEvent 继续更新孤儿 ServiceInfo）。周期性故障（如客户端滚动重启）下监听器线性累积。
- **现状**: subscribe/unsubscribe 配对仅在 NacosException 分支成立，F8（注册/注销不对称）在异常分类不备时破缺。
- **风险**: 长时运行中每次"subscribe 后非 NacosException 失败"净增一个永久监听器：Nacos 客户端事件分发负载与内存缓涨，且重复事件处理放大 C3-006 的事件线程阻塞面。
- **建议**: catch 改为 `catch (Exception e)`（或 try/finally + subscribed 标志回滚）后再适配异常；`updateInstances` 内部对单实例数据异常做隔离，避免一条坏数据使整个初始化失败。
- **失效面枚举**: 模块内 subscribe/unsubscribe 与 register/unregister 配对点全清单：(1) NacosNamingService.getServiceInfo:186–198（本条，异常分类不备）；(2) NacosNamingService.ServiceInfoListener.unsubscribe:222–228（配对执行点，本身无缺陷）；(3) NacosConfigService.getConfigInfo:161–166（remover 闭包经 ConfigInfo 交付调用方注销；stop() 后 configService==null 时 remover 静默 no-op——因 shutdown 后监听器随之消亡，无泄漏，登记为边界）；(4) AutoRegistration.start:202/stop:227（register/unregister 配对，失败路径见 C3-010，量级 P3）。
- **信心水平**: 很可能（配对破缺为代码级确定；具体非 NacosException 触发源依赖 Nacos 客户端运行时状态）
- **误报排除**: 与已立案"订阅快照无版本守护"（init 与事件先后时序面）同站点不同失效面，已在上文边界声明；也非"异常处理风格"问题——后果是资源（订阅）泄漏与无界累积，非错误码规范问题。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（catch NacosException-only :194 回码成立；RuntimeException 触发源现实存在——copyFromInstance/compareTo 对坏数据可抛 NPE；round2 扩展的 NacosConfigService:148-168 同根站点证实并入同案修复）

### [C3-006] cleanupTasks 在 Nacos 事件分发线程上同步执行：慢清理任务阻塞全平台服务发现事件

- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:235–277`（执行点 :268–276）
- **证据片段**:
  ```java
  void updateInstances(List<Instance> instances) {          // 由 onEvent 在 Nacos 通知线程调用
      ...
      synchronized (this) { ...; this.instanceMap = map; serviceInfo.setServiceInstances(...); }
      for (ServiceInstance serviceInstance : removed) {
          for (Runnable task : serviceInstance.getCleanupTasks()) {
              try { task.run(); }                            // 用户清理代码在事件线程内联执行
              catch (Exception e) { LOG.warn("nop.err.cluster.nacos.clean-up-fail", e); }
          }
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: Nacos 客户端事件分发线程（按事件类型共享的单/少量通知线程）执行本模块注册的 EventListener；业务模块通过 `ServiceInstance.addCleanupTask` 注册的清理逻辑（连接关闭、会话注销等）在该线程上运行。
- **触发时序**: T1 事件分发线程进入 updateInstances，完成同步块（服务列表已更新）→ T2 在 :271 执行某实例的 cleanupTask，该任务阻塞（如关闭带 30s drain 超时的连接池）→ T3 同一分发队列上后续**所有服务**的 InstancesChangeEvent 全部排队等待 ≥ 阻塞时长 → 期间其他服务实例上下线的处理延迟，`getInstances` 返回陈旧视图；阻塞对任何健康检测不可见（不抛异常、不产出，F5 形态）。
- **现状**: 清理逻辑执行位置选在共享通知线程上，无执行器隔离、无超时控制；逐任务 try/catch 只防了异常不防阻塞。
- **风险**: 一个实例的下线清理故障放大为全平台服务发现视图陈旧；与轮询 RPC 链（ClusterRpcClient 每请求经 getServersAsync）叠加后，所有集群调用在阻塞窗口内基于过期实例表选择目标。
- **建议**: 将 cleanupTasks 派发到平台执行器（`io.nop.commons.concurrent.executor` 的 IThreadPoolExecutor/GlobalExecutors.globalWorker，或直接 AsyncJoinType 语义的异步执行），事件线程只入队；为单任务执行加超时上限；javadoc 声明 cleanupTask 的执行线程契约。
- **失效面枚举**: "回调在关键共享线程上内联执行"同族站点：(1) NacosNamingService.updateInstances 清理循环 :268–276（本条）；(2) AbstractLeaderElector listener 循环 :183–218 与 promise.complete 触发的依赖回调（定时器线程内联，慢 listener 延迟选举轮询——同一失效层，随本条同修：listener 派发亦建议异步化/限时）；(3) NacosConfigService Listener.receiveConfigInfo:156–158（配置推送线程内联，独立触发面较窄，归 C3-011 详述）；(4) ServiceInfoListener.onEvent 主体 :214–220（仅同步块+上述清理，同步块本身必须内联——非缺陷）。(1)(2)(3) 立项，(4) 保留。
- **信心水平**: 很可能（阻塞传播链为代码级确定；Nacos 客户端通知线程池规模为其内部实现，按最保守单线程评估）
- **误报排除**: 与已立案"Nacos 事件线程原地改写"（数据写面）和"cleanupTasks 列表并发面"（C1/C2）失效面不同，本条是执行模型/阻塞面；也不是"用户回调慢是用户自己的问题"——把不可信用户代码内联进平台共享线程是平台的调度决策。
- **复核状态**: 未复核
- 复核状态: 保留(P2，条件化标注)｜执行模型决策为代码事实（:268-276 内联 task.run 回码成立）；阻塞面激活依赖 addCleanupTask 接线（当前全仓零调用方）——与 C1-002/C2-001 同批条件化，接线即实际化；事件线程阻塞传播链与 F5 形态论证成立

### [C3-007] SentinelFlowControlRunner.runAsync 的 Entry.exit 唯一挂接在 task future 完成事件上：future 永不完成则 entry 永久泄漏

- **文件**: `nop-cluster/nop-cluster-sentinel/src/main/java/io/nop/cluster/sentinel/SentinelFlowControlRunner.java:63–81`
- **证据片段**:
  ```java
  serviceEntry = SphU.asyncEntry(entry.getResource(), resourceType, entryType, 1, args);
  CompletionStage<T> ret = task.get();
  proceedCalled = true;
  return ret.whenComplete((r, e) -> {
      if (e != null) { if (serviceEntryArg != null) Tracer.traceEntry(e, serviceEntryArg); }
      if (serviceEntryArg != null) { serviceEntryArg.exit(1, args); }   // exit 仅在此发生
  });
  ```
- **严重程度**: P2
- **并发场景**: 业务线程进入 runAsync 创建 Sentinel entry 后立即返回；exit 推迟到完成 task future 的任意线程（HTTP IO 线程等）上执行。
- **触发时序**: T1 runAsync 创建 entry 并返回 `ret.whenComplete(...)` → T2 task future 因底层缺陷永不完成（现实触发源：C3-001 ClusterRpcClient 挂起链、或上层未设超时的慢调用）→ whenComplete 回调永不执行 → `serviceEntry.exit` 永不发生 → Sentinel 资源/并发计数（clusterNode 的线程数、pass 指标）单侧泄漏 → 长时运行中泄漏累积抬高并发统计 → 流控/熔断规则在真实负载远低于阈值时误触发。与 C3-007 伴生：runAsync 也未对 `ret` 挂任何 orTimeout/超时兜底。
- **现状**: entry 生命周期完全绑定 task future 的终结事件，无"不完成"防御；成功路径 exit 在异步线程执行、init 线程的 ContextUtil.exit 已在 finally 同步完成（P1-4.4-C3 已修的 context 泄漏不覆盖本面）。
- **风险**: 每次底层挂起净漏一个 entry；Sentinel 统计虚高导致误限流是生产可见故障；且该泄漏与 C3-001 同源——修 C3-001 可消除主触发源，但 runAsync 自身对"永不完成的 future"仍无防御。
- **建议**: 为 `ret` 挂 `orTimeout`/平台超时装饰（或要求 IFlowControlRunner 契约上 task future 必须有界并在 javadoc 声明），保证 exit 最终执行；exit 异常单独捕获，避免 exit 抛错扭曲 task 的完成结果（当前 exit 抛错会使 whenComplete 结果 stage 异常完成，调用方看到假失败）。
- **失效面枚举**: Entry exit 配对站点全清单：(1) runAsync 成功路径 :71–81（本条）；(2) runAsync BlockException/同步异常路径 :87–93 finally（proceedCalled 守卫，配对成立）；(3) 同步版 run() :155–176 finally（配对成立）；(4) ContextUtil.enter/exit :47/:98（P1-4.4-C3 已修，无条件配对成立）。同族仅 (1) 失守。模块内 IFlowControlRunner 实现仅 SentinelFlowControlRunner 一个（grep 证实）。
- **信心水平**: 很可能（泄漏链代码级确定；"误限流"后果依赖 Sentinel 统计口径与规则配置）
- **误报排除**: 非"asyncEntry 后 exit 放异步回调"这一官方模式本身的问题——官方模式假设 future 必然终结；本条针对"永不终结"的未定义行为缺防御。也不是错误处理维度（不在 C3 范围的 ErrorCode 面不涉及）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（sentinel 装配时 live；exit 唯一挂接点 :71-81 回码成立、无 orTimeout 兜底证实；主触发源 C3-001 同源，修 C3-001 后本条仍独立成立——runAsync 自身对永不完成 future 无防御）

### [C3-008] SentinelFlowControlRunner.runAsync 同步抛出逃逸：task supplier 同步异常以裸异常抛出而非失败 future

- **文件**: `nop-cluster/nop-cluster-sentinel/src/main/java/io/nop/cluster/sentinel/SentinelFlowControlRunner.java:60–99`
- **证据片段**:
  ```java
  try {
      serviceEntry = SphU.asyncEntry(...);
      CompletionStage<T> ret = task.get();      // ← 同步抛 RuntimeException 时
      proceedCalled = true;
      return ret.whenComplete(...);
  } catch (BlockException ex) { ... }
  finally {
      if (!proceedCalled) { if (serviceEntry != null) serviceEntry.exit(1, args); }
      ContextUtil.exit();
  }                                             // 无 catch (Exception)：非 BlockException 直接逃逸
  ```
- **严重程度**: P3
- **并发场景**: 单线程同步段，无并发参与；问题纯为异步签名（`CompletionStage<T>` 返回）内的同步异常边界。
- **触发时序**: T1 调用方 `flowControlRunner.runAsync(entry, task)` → T2 `task.get()`（或 asyncEntry 内部非 BlockException 路径）抛 IllegalArgumentException → 异常穿过 finally（entry 已正确 exit、context 已正确退出）后**直接从 runAsync 抛出** → 契约上期望 CompletionStage 的调用方：若在 lambda/completion 链中调用则异常类型与位置突变；若 `runner.runAsync(...).whenComplete(...)` 链式书写则 NPE/链断。对照 BlockException 路径返回 `FutureHelper.reject(...)` 的既有约定，非 BlockException 的失败未归一为失败 future。
- **现状**: 返回 CompletionStage 的方法存在同步异常逃逸路径，两类失败（Block/非 Block）传播形态不一致。
- **风险**: 上层组合子调用点行为不可预期；日志/监控按"调用方异常"而非"流控包装失败"归类，掩盖真实故障面。量级低（supplier 同步抛错罕见），故 P3。
- **建议**: `catch (Exception e)` 分支中（finally exit 之后语义等价）返回 `FutureHelper.reject(NopException.adapt(e))`，与 BlockException 路径对齐；或 javadoc 显式声明同步抛出契约。
- **失效面枚举**: 模块内"返回 CompletionStage 的公共方法"同步逃逸排查：(1) SentinelFlowControlRunner.runAsync（本条）；(2) ClusterRpcClient.callAsync :64–93（thenCompose lambda 内同步抛 newNoAvailableServerError 被 CompletableFuture 机制捕获为失败 future——无逃逸）；(3) BroadcastRpcClient.callAsync :53–56（同 (2)，servers 为空在 callServersAsync 内走 reject——无逃逸）；(4) ClusterRpcServiceInvoker.invokeAsync:100–102 / HttpRpcServiceInvoker.invokeAsync:85–92（getRpcService 前置失败会同步抛——HttpRpcServiceInvoker.getRpcService:67 `getBaseUrl` 抛 IllegalArgumentException 即同步逃逸，同族失守点，随本条同修）；(5) NacosNamingService/CachingNamingService 异步方法（futureCall 已捕获 Throwable——无逃逸）。
- **信心水平**: 确定
- **误报排除**: 非 error-handling 维度问题（不涉及 ErrorCode 体系选型），是异步签名契约的同步异常边界，属 C3 §2"同步抛出逃逸"定向检查项。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现未修复（catch BlockException-only :87-93 回码成立，finally 配对完整；同步版 run() 的 catch(Exception) 对照证实不对称；失效面(4) HttpRpcServiceInvoker 同族逃逸点采纳）

### [C3-009] 返回 future 与 cancelToken 无反向联动：调用方取消返回 future 不中止在途请求

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcClient.java:69–93`；`nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/BroadcastRpcClient.java:65–86`
- **证据片段**:
  ```java
  CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
  ...                                                     // future 与 cancelToken 之间无任何挂接
  fn.accept(0);
  return future;                                          // future.cancel() 仅改变 future 自身状态
  ```
- **严重程度**: P3
- **并发场景**: 调用方线程取消返回的 future（CompletableFuture.cancel(true)），底层 HTTP 请求仍在 IO 线程执行。
- **触发时序**: T1 调用方对返回 future 调 `cancel(true)` → future 进入 cancelled 态 → T2 底层 callAsync 继续（其持有的 cancelToken 从未被触发）→ 请求打满全流程，响应到达后 :77 `future.complete(ret)` 对 cancelled future 静默无效 → 幽灵请求完整执行（写操作已打到对端）。BroadcastRpcClient 有外层 cancelToken→cancellable→兄弟调用的**正向**联动（:66–68），但 future→token 的**反向**联动两侧均缺失。
- **现状**: 取消传播单向（入参 token → 底层），返回句柄的取消不闭环（C3 §3 取消传播检查项）。
- **风险**: 依赖 future.cancel 语义的上层（如超时管理器取消慢调用）会误以为已中止请求；广播/重试场景下放大为多台实例的幽灵写。因仓内调用方惯例是传 cancelToken 而非 cancel future，实际暴露面有限，故 P3。
- **建议**: 构造 future 时挂接反向传播（如 `future.whenComplete` 不够，应使用可传播取消的封装：在返回前 `future.whenCancelled? `——平台无该 API，可用 `cancelToken.appendOnCancel(reason -> future.completeExceptionally(new CancellationException(reason)))` + 返回侧包装一个 `future.thenApply` 副本并在副本 cancel 时触发 token），或至少 javadoc 声明"取消须通过 cancelToken"。
- **失效面枚举**: 手工创建返回 future 的站点全清单：(1) ClusterRpcClient.callAsync:69（本条）；(2) BroadcastRpcClient.callAsync（聚合 future 为 waitAll/waitAnySuccess 结果，无手工 future，但反向联动同缺——本条合并登记）；(3) FutureHelper.waitAnySuccess:462（基础设施内部 future，无取消需求）；(4) ResolvedPromise 系（立即完成，无取消窗口）。同族仅 (1)(2) 失守。
- **信心水平**: 确定（行为代码级确定；实际危害取决于调用方取消惯例）
- **误报排除**: 不是"CompletableFuture.cancel 本身"的原语问题，而是取消传播链断裂的复合语义；也非误报校准豁免项（生产 RPC 主路径，非测试代码）。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现未修复（future:69 手工创建与 cancelToken 零联动回码成立；BroadcastRpcClient 正向联动/反向缺失的对称性论证成立）

### [C3-010] AutoRegistration.stop 与定时 refreshRegistration 竞态：cancel(false) 不中断在途刷新，instance 字段跨线程非同步读写

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/AutoRegistration.java:189–229`
- **证据片段**:
  ```java
  autoUpdateTimerFuture = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker())
          .scheduleWithFixedDelay(this::refreshRegistration, ...);      // :205–206
  protected void refreshRegistration() {
      if (instance != null) {                                            // :211 check
          try { namingService.updateInstance(instance); }                // :213 use——instance 可能为 null
          catch (Exception e) { LOG.warn(...); }
      }
  }
  public void stop() {
      if (autoUpdateTimerFuture != null) autoUpdateTimerFuture.cancel(false);   // 不中断在途任务
      ...
      namingService.unregisterInstance(instance);                        // :227
      instance = null;                                                   // :228 非 volatile
  }
  ```
- **严重程度**: P3
- **并发场景**: globalTimer worker 线程周期执行 refreshRegistration；应用关闭线程执行 stop()。
- **触发时序**: T1 timer 线程执行 refreshRegistration 通过 :211 非空检查、尚未执行 :213 → T2 关闭线程在 stop() 中 :228 置 `instance = null` → T1 :213 `updateInstance(null)` 抛 NPE → 被 :214 catch 降为 warn（无害但掩盖时序缺陷）。第二时序：T2 先 :227 unregisterInstance，T1 随后 :213 updateInstance——对已注销实例发起元数据更新，结果依赖 NamingService 实现（Nacos maintain 服务对不存在实例的行为未定义），可能在注销后复活实例元数据或报错。
- **现状**: `instance` 字段非 volatile、check-then-use 非原子，stop 的 cancel(false) 不保证与在途任务互斥。
- **风险**: 关闭路径偶发 warn 噪音或注销后残留元数据；因后果轻且被 catch 包裹，降档 P3（理论风险成分：第二时序后果未具体化到实测行为）。
- **建议**: refreshRegistration 改为拷贝引用 `ServiceInstance inst = this.instance;` 后判空使用；stop 中先 `instance = null` 再 cancel（或用 volatile + 停止标志），并在 unregister 后不再接受刷新。
- **失效面枚举**: 模块内"定时器周期任务 + 关闭路径"配对站点：(1) AutoRegistration refreshRegistration/stop（本条）；(2) AbstractPollingLeaderElector scheduleCheck / AbstractLeaderElector.doStop（停机经 isStopping 检查——子类 JdbcLeaderElector:123 已有 while(!isStopping()) 守卫，基类无强制，见 CL-011）；(3) NacosNamingService.destroy / ServiceInfoListener（destroy 后 unsubscribe 走 getNamingService() 重建实例——见边界注：destroy 置 null 后若残余事件线程调 unsubscribe :224 `getNamingService()` 会**重新创建**一个新 Nacos 客户端只为注销——低概率资源复活面，登记 watch，随 (1) 同修为先空检查）。同族 (1) 立项，(2)(3) 登记观察。
- **信心水平**: 很可能（竞态窗口代码级确定；第二时序对端行为未实测）
- **误报排除**: 非误报校准中"启动/管理路径低频粗粒度同步合理"——这是关闭期与运行期任务的交叠面，且修复成本极低；后果虽轻但属真实数据竞争而非风格问题。
- **复核状态**: 未复核
- 复核状态: 保留(P3，并入 C1-007 主登记)｜与 C1-007 同站点同失效面（refreshRegistration vs stop 竞态）重复立案——合并为单案计数，修复随 C1-007 同 diff；本条 P3 判级为合并后采纳级；失效面(3) 的 destroy 后 unsubscribe 重建客户端 watch 面复核确认成立（并入 C1-005 修复批核对）

### [C3-011] NacosConfigService 配置监听器在推送线程内联执行业务回调且无异常隔离：更新静默丢失风险

- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosConfigService.java:146–170`
- **证据片段**:
  ```java
  Listener nacosListener = new Listener() {
      public Executor getExecutor() { return command -> command.run(); }   // 内联：推送线程执行
      public void receiveConfigInfo(String configInfo) {
          listener.onUpdateConfig(configInfo);                              // 无 try/catch
      }
  };
  String content = configService.getConfigAndSignListener(dataId, group, timeout, nacosListener);
  ```
- **严重程度**: P3
- **并发场景**: Nacos 客户端配置推送线程执行 receiveConfigInfo；IConfigUpdateListener（配置热更新链路）在其上同步执行。
- **触发时序**: T1 推送线程回调 receiveConfigInfo → T2 `listener.onUpdateConfig` 抛异常（配置内容非法触发解析失败等）→ 异常上抛给 Nacos 客户端调度层（其自行吞没/记日志）→ 本次配置更新对平台侧**静默丢失**：监听器仍在注册表中，无重试、无状态标记、平台不感知失败（F9：吞异常后状态不一致——配置源停留旧值而服务端已是新值）。慢 onUpdateConfig 则阻塞同推送队列的后续配置项（与 C3-006 同层但触发频率低）。
- **现状**: 包装层既不隔离异常（无降级/重试/告警钩子）也不隔离执行线程，完全委托 Nacos 客户端默认行为。
- **风险**: 配置热更新"偶发丢一次且无痕迹"；改动配置后个别节点不生效的排查无锚点。因 onUpdateConfig 实现多为幂等重载且可经后续推送自愈，降 P3。
- **建议**: receiveConfigInfo 包 try/catch：失败记 error 级日志（带 dataId）并至少标脏待下次校验重拉；getExecutor 提供受控执行器或 javadoc 声明回调线程契约（禁阻塞）；可选失败计数暴露给运维接口。
- **失效面枚举**: "注册到外部系统的回调包装"全清单：(1) NacosConfigService Listener :149–159（本条）；(2) NacosNamingService.ServiceInfoListener.onEvent :214–220（异常同样委托 Nacos 客户端吞没，但其后随 cleanup 面已立项 C3-006/泄漏面 C3-005，事件模型有重推自愈性更强，不单独立项）；(3) ILeaderElectionListener 系列（AbstractLeaderElector 已逐 listener try/catch——无此缺陷，模块内最佳实践参照）；(4) cleanupTasks 逐任务 try/catch warn（有隔离、无告警升级——已在 C3-006 失效面内）。同族 (1) 立项，(2)(4) 登记观察。
- **信心水平**: 很可能（异常路径无隔离为代码级确定；"Nacos 客户端吞没回调异常"基于其调度框架通用行为，未逐版本核实）
- **误报排除**: 不属于 error-handling 维度（不涉及错误码/异常类型选型），是异步回调的异常传播终点问题（C3 §2"回调中抛出的异常是否会杀死调度线程或被吞"定向检查项）。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现未修复（getExecutor 内联 :152-154、receiveConfigInfo 无隔离 :156-158 回码成立；AbstractLeaderElector 逐 listener try/catch 最佳实践对照成立）

## 维度集外线索（overlay C5/C6/C7/C9）

（编号续接 nop-cluster-CL-007；CL-001…006 已被占用）

### [nop-cluster-CL-007]（C9）RouteServiceInstanceFilter 在 removeIf 内逐实例解析 NPM 版本约束

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/chooser/filter/RouteServiceInstanceFilter.java:56–67`
- `Requirement.buildNPM(svcRoute)` 对同一请求的每个候选实例重复执行（N 次解析同一约束字符串），位于每次 RPC 选址热路径；解析结果与实例无关，应提升到 removeIf 外解析一次。另外循环体内 `LOG.info` 逐实例打印也是热路径 IO 放大点。建议：解析一次 + 日志降 debug/采样。

### [nop-cluster-CL-008]（C9）每请求每跳新建 HttpRpcService/CancellableRpcClient/PollingRpcClient 实例

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/http/HttpRpcClientInstanceProvider.java:33–37`；`nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcClient.java:137–152`
- 每次调用为每个目标实例 `new HttpRpcService(httpClient, new DefaultRpcUrlBuilder(baseUrl))`，重试时再各包一层 Cancellable/Polling 装饰器——热路径分配抖动与 GC 压力；HttpRpcService 本身无状态（client 共享），可按 instance 缓存（instanceId→IRpcService，注意随实例下线清理，可与 cleanupTasks 机制联动）。

### [nop-cluster-CL-009]（C9）CachingNamingService 异步 miss 路径无并发去重，缓存过期瞬间穿透风暴

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/CachingNamingService.java:46–54`
- 同步路径 `getInstances` 走 `cache.computeIfAbsent`（单飞语义，锁面已由 C1/C2 立案）；异步路径 `getInstancesAsync` 是"check-then-async-load-then-put"：TTL 过期瞬间 N 个并发请求全部 miss 并各自发起远端实例查询（下游 NacosNamingService 无缓存层时逐次 remote getAllInstances），存在雪崩放大。建议复用 `IAsyncCache`/singleflight 式合并（如 `cache.computeIfAbsentAsync` 语义或按 key 合并的加载 future 表）。边界声明：与已立案 computeIfAbsent 锁内远程调用不同站点不同失效面（本条为无锁去重缺失，非性能外的问题也包括 miss 时 `cache.get` 返回已过期对象与否取决于 LocalCache 过期策略——需复核）。

### [nop-cluster-CL-010]（C6）HazelcastProvider 生命周期与可见性：@PostConstruct 前 @BeanMethod 可返回 null，字段非 volatile

- **文件**: `nop-hazelcast/nop-hazelcast-core/src/main/java/io/nop/hazelcast/core/HazelcastProvider.java:17–41`
- `getInstance()`（@BeanMethod）直接返回 `hazelcast` 字段：若其他 bean 的初始化先于本 bean 的 init() 触发 bean 方法调用则拿到 null（无守卫）；`hazelcast`/`configPath` 字段非 volatile、init/destroy 无同步，跨线程可见性依赖 IoC 容器单线程 init 假设。属资源生命周期/启动 wiring 面（C6/C1），非 C3 异步面，故列线索。

### [nop-cluster-CL-011]（C7）AbstractPollingLeaderElector 自续链契约无基类守护：checkElection 异常即断链

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractPollingLeaderElector.java:16–31`
- `doStart` 仅单次 `schedule(this::checkElection, 0)`，后续轮询完全依赖子类在 checkElection 内"自行调用 scheduleCheck"。基类未用模板方法（finally 中重调度）或装饰守护：子类实现若在 catch 之后的清理逻辑中再抛（如现有子类 catch 块内 `onException`/`onBecomeFollower`/`onRestartElection` 任一抛出）或捕获范围遗漏 Error，轮询即静默停摆且无任何告警（选举监控死亡对健康检测不可见，F5 形态）。现仓两个子类（nop-stream JdbcLeaderElector、nop-sys SysDaoLeaderElector，仓外根）均显式守约，属契约无守护的潜在面。建议：基类将重调度收敛为 `schedule(() -> { try { checkElection(); } finally { scheduleCheck(); } })` 或至少 wrap Throwable 记 error + onException。同族：AbstractLeaderElector listener 循环 :183–218 已逐 listener try/catch（守护成立，参照实现）。

## 维度复核结论

- **复核人**: CRA WI 4.2 独立复核员（合并批 C1+C2+C3）
- **复核日期**: 2026-10-20
- **方法**: 同 C1 文件（逐条回码 + 台账查重 + 降档口径核对）

| 发现 | 初判 | 复核判定 | 关键复核证据 |
|------|------|---------|-------------|
| C3-001 | P1 | **保留 P1** | 重试递归异常丢失机制回码成立（首跳 thenCompose 捕获 vs 重试跳 whenComplete 返回 stage 被丢弃）；live 默认装配路径证实（AbstractRpcProxyFactoryBean 缺省 parent ClusterRpcProxyFactoryBean）；与 WI 1.6 C8-012/WI 3.2 C1-001 为同族异站点关联（future 永不完成族），非重复 |
| C3-002 | P1 | **降级 P2（条件化回升）** | 机制回码成立（waitAll=allOf、buildResponse 仅 get(0)、失败路径 cancellable 不触发）；但 BroadcastRpcProxyFactoryBean 全仓零接线→休眠面条件化降档，接线即回升 P1 |
| C3-003 | P2 | **保留 P2** | 契约原文（IServerChooser:29"列表非空不应为 null"）与双 null 返回点回码成立；live 面 + C3-001 触发源 |
| C3-004 | P2 | **保留 P2** | 等待者挂接面三时序回码成立；生产调用方为零的现状标注证实（P2 判级已按潜在面校准，维持） |
| C3-005 | P2 | **保留 P2** | 异常分类不备回码成立；round2 同根扩展（NacosConfigService 配置监听器注册后取值失败无清理）证实并入，第 1 轮失效面(3) 结论修正采纳 |
| C3-006 | P2 | **保留 P2（条件化标注）** | 内联执行模型回码成立；阻塞面激活依赖 addCleanupTask 接线（当前零调用方），随 C1-002 批次条件化；事件线程共享阻塞传播链成立 |
| C3-007 | P2 | **保留 P2** | exit 唯一挂接在 whenComplete 回码成立；对永不完成 future 无防御独立成立 |
| C3-008 | P3 | **保留 P3** | 同步逃逸面回码成立；P3 判级恰当 |
| C3-009 | P3 | **保留 P3** | 反向取消联动缺失回码成立；仓内调用惯例使暴露面有限的降档理由成立 |
| C3-010 | P3 | **保留 P3（并入 C1-007）** | 与 C1-007 同站点同失效面，合并去重不重复计数；P3 为合并采纳级 |
| C3-011 | P3 | **保留 P3** | 内联回调无隔离回码成立；自愈性论证（幂等重载+后续推送）支持 P3 |
| C3-012（R2） | P3 | **保留 P3** | 发射中断面回码成立；"理论风险，未具体化"标注恰当（同步抛出点依赖模块外实现），符合共享前缀第 4 条口径 |

**降档口径核对**: C3-002 为本文件唯一降档，依据为 BroadcastRpcProxyFactoryBean 全仓零接线（休眠面，附 grep 证据）；C3-001 的载体 ClusterRpcClient 经缺省 beans 装配为 live 路径，不适用休眠降档；C3-006 阻塞面激活条件与 C1-002 同批标注。

**CL-024 移交面漂移复查（闭环输入）**: 本维度复核确认 C3 十二条中**无 per-client/topic 池同族发现**；rpc-cluster 零自建池（复核独立 grep 复现：仅 globalTimer 共享 2 站点 ClusterRpcServiceInvoker:68 / ClusterRpcProxyFactoryBean:43 + 每调用 new HttpRpcService 包装对象）。CL-008（及 C1 文件 CL-003 同站点重复登记）属 C9 每调用分配面，非池累计形态——WI 2.1 CL-024 按"无同族"预期闭环成立。

## 最终保留项

| 最终严重度 | 条目 |
|-----------|------|
| P1×1 | C3-001（重试异常吞没挂死，live 默认路径） |
| P2×6 | C3-002（广播半个成功，条件化 P2）、C3-003（MasterServerChooser null 违约）、C3-004（promise 等待者面）、C3-005（订阅泄漏，含 round2 扩展）、C3-006（事件线程内联执行，条件化标注）、C3-007（Sentinel entry 泄漏） |
| P3×5 | C3-008（同步逃逸）、C3-009（反向取消缺失）、C3-010（并入 C1-007）、C3-011（配置回调无隔离）、C3-012（发射中断，理论风险） |

统计：保留原级 10 / 降级 1 / 并入去重 1（C3-010→C1-007）/ 驳回 0。CL 线索（CL-007~011）复核确认成立；CL-008 与 C1 文件 CL-003 同站点重复，随修复批合并。
