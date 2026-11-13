# C2 深挖第 2 轮 — nop-cluster（WI 4.2）

- **审计人**: 独立深挖第 2 轮审计员（合并批 C1/C2/C3/C4/C8）
- **日期**: 2026-10-21
- **状态**: 深挖完成，未复核；与第 1 轮 C2（C2-001…C2-004）编号续接，不重复立案

## 深挖范围与方法

按派发口径，本轮对以下 C2 指定邻接面逐一重审（live code 为准，盲审纪律同第 1 轮）：

1. **AbstractLeaderElector synchronized 选主同步之外的其他站点**：第 1 轮 C2-003 已穷举 5 个写站点（newElection/onRestartElection/onElectionCompleted/onStop/doStop）的锁覆盖缺口。本轮新核验的站点是**锁内回调执行面**：`onElectionCompleted` 在持 `this` 监视器期间调用 `promise.complete(epoch)`——按 CompletableFuture 语义，非 async 依赖（`thenApply/thenAccept/whenComplete` 未指定 executor 注册的 continuation）在**完成线程内同步执行**，即等待者用户代码在 ETHIS 锁内运行。立案为 C2-005。其余 synchronized 方法（onRestartElection 仅 cancel/替换字段）无外部代码调用，负结果。
2. **NacosNamingService:230/238 双站点的锁内操作**：
   - `:230 initInstances`（LSN 锁）→ `updateInstances` → `:238 synchronized(this)`（LSN 重入）→ 锁内操作逐项核验：逐实例 `instance.toString()`（纯内存字符串拼接）、LinkedHashMap 重建、`instanceMap.keySet().removeAll(map.keySet())`（初次为 `Collections.emptyMap().keySet()`，空集 removeAll 不触发 UOE，已核验）、`CollectionHelper.immutableList`（Guava 拷贝）+ `sorted()`（O(n log n)）——**全部纯内存，无 I/O、无用户回调**；LSN 持有时长为 O(n log n) 内存操作量级，与 C2-002 的 BIN 锁内远程 I/O 不同级。cleanup 循环在 LSN 释放后执行（正确设计）。
   - **锁序复核**：请求线程持 BIN（computeIfAbsent）→ LSN（initInstances）→ SINFO（setServiceInstances）；事件线程 LSN → SINFO。SINFO 的三个 synchronized 方法（ServiceInfo:21/:25/:29）均不反向获取 BIN/LSN——锁序图无新边、无环，**维持第 1 轮"无可达死锁"结论**。
3. **filter/chooser 的隐式同步面**：逐文件核验 5 个 filter（Healthy/Route/Specific/Tag/Zone）与 3 个 chooser（ServiceServer/LoadBalance/Master）——除装配期 setter 写入的配置字段外**零实例可变状态、零同步原语、零共享集合**；filter 操作对象为每请求私有副本（ServiceServerChooser:64/66 恒 new ArrayList），`IServiceInstanceFilter.filter` 默认实现为 stream 收集新列表（不 mutate 入参）。**负结果，无立案面**。
4. **基线外锁载体复核**：以 C2a（10 站点）+ C2b（4 站点）为底册全量重走，无第 1 轮未覆盖的新锁载体；`Collections.synchronizedXxx`/`ReentrantLock`/wait-notify 模块内仍为零。

## 新增发现

### [C2-005] onElectionCompleted 持 ETHIS 监视器期间 promise.complete() 同步派发等待者 continuation：平台用户回调在选主锁内执行，锁扩展至未知代码

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractLeaderElector.java:173-181`（complete 站点）、`:33`（promise 字段）、`:157-159`（等待者挂接面）
- **证据片段**:
  ```java
  protected synchronized void onElectionCompleted(LeaderEpoch epoch) {
      this.leaderEpoch = epoch;
      CompletableFuture<LeaderEpoch> promise = this.electionPromise;
      if(promise.isDone()){ promise = new CompletableFuture<>(); this.electionPromise = promise; }
      promise.complete(epoch);   // 非 async 依赖在此线程（持 ETHIS 中）同步执行
  }
  ```
- **严重程度**: P2
- **并发场景**: globalTimer/worker 线程（checkElection → onElectionCompleted）× 任意通过公开 API `ILeaderElector.whenElectionCompleted()` 挂接 continuation 的平台用户线程所注册的回调（回调体本身在该 worker 线程执行）。
- **触发时序**: T1 worker 线程进入 synchronized onElectionCompleted → 执行 `promise.complete(epoch)` → T2（同一线程，CompletableFuture.postComplete 语义）逐个同步执行等待者以非 async 方式注册的 continuation（用户代码，如"当选后初始化 leader 侧调度器/抢分布式资源"）→ T3 同期另一轮 checkElection 调用 onRestartElection/onElectionCompleted（仅有的两个 synchronized 方法）→ 阻塞在 ETHIS 直至 T2 用户代码返回。若用户 continuation 内部再次进入 elector 的 synchronized 方法并等待选举推进（如调用子类 synchronized 的 restartElection 实现），形成自等待环。
- **现状**: 监听器回调（onBecomeLeader 等四循环）已逐 listener try/catch 且在**锁外**执行（第 1 轮 C3-006 失效面(2) 从线程占用角度登记）；本条立案的是**锁机制面**：promise completion 路径把 continuation 执行放进了 synchronized 临界区，锁持有时长 = 全部等待者 continuation 时长之和，锁纪律与监听器循环的"锁外回调"设计自相矛盾。仓内当前无 whenElectionCompleted 生产调用方（潜在面），但该 API 是 ILeaderElector 公开契约。
- **风险**: 用户回调慢 → 选举轮询停摆（与 C3-006 同症状但机制不同：回调线程内联 vs 锁扩展）；用户回调死锁 → 选主状态机永久冻结（leaderEpoch 停留旧值，后续 onElectionCompleted 全部阻塞）。修复 C1-004/C2-003 的锁纪律时若简单"全站点加 synchronized"而不同时移出 complete()，会**放大**本面。
- **建议**: onElectionCompleted 内先在锁内完成状态转移与字段替换，`promise.complete(epoch)` 移到 synchronized 块外执行（先取局部引用，出锁后 complete）；或统一改用 `promise.completeAsync`/平台执行器派发 continuation。与 C2-003 的锁统一方案、C3-004 的 promise 生命周期方案协同落地。
- **失效面枚举**（F3：模块内全部 synchronized 站点逐一核对"锁内是否调用外部/用户代码"）: ① AbstractLeaderElector.onElectionCompleted（complete → 外部代码，**本案**）；② AbstractLeaderElector.onRestartElection:165（仅 cancel/new 内部操作，干净）；③④ NacosNamingService.getNamingService:67/getNamingMaintainService:82（锁内 createNamingService 为 SDK 构建，重 I/O 已由 C2-004 立案，非用户回调）；⑤⑥ ServiceInfoListener.initInstances:230/updateInstances:238（锁内纯内存重建，本轮负结果，见上文）；⑦⑧⑨ ServiceInfo:21/:25/:29（纯字段读写）；⑩ ServiceInstance.addCleanupTask:289（纯集合操作）。1/10 失守，无遗漏。
- **信心水平**: 确定（CompletableFuture 非 async 依赖在完成线程执行的语义为 JDK 确定行为；交错无需罕见时序，只要有任一等待者存在）
- **误报排除**: 非"原语使用本身是问题"——complete() 单独使用正确，缺陷在 synchronized 临界区与完成通知的复合编排；与 C3-006 失效面(2) 的边界已声明（线程占用面 vs 锁持有面，同站点不同失效层）；仓内无当前调用方仅降低暴露概率，不改变公开 API 契约缺陷的成立（与第 1 轮 C3-004 对 newElection 的判例一致）
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜公开 API 潜在面——whenElectionCompleted 生产调用方为零（全仓 grep 仅测试；JobCoordinator:584 显式禁用）；complete-in-monitor（:180）与 CF 同步派发语义回码成立

## 维度集外线索（overlay）

（本轮 C2 指定深挖面全部为负结果或已并入既有条目，无新增 CL；统一流水编号让渡给 C1/C3/C4/C8 文件）

## 继续判定

本轮新增 1 条（C2-005 P2，公开 API 潜在面、机制确定），无 P0/P1。三个指定深挖方向中：elector 其余站点产出 C2-005 后闭环（10 个 synchronized 站点全量核对完毕）；NacosNamingService:230/238 锁内操作与锁序复核为可复核负结果（无新边、无环、锁内无 I/O/用户代码）；filter/chooser 隐式同步面为零。**判定：终止信号**——无 P0/P1，仅 1 条 P2 且深挖面已穷尽收敛，无需第 3 轮。

## 复核结论（WI 4.2 独立复核员，2026-10-20）

- C2-005 **保留原级 P2**（状态行见条目）；维持与本单元 C3-004 判例一致的"公开 API + 机制确定 + 零调用方仅注记不降级"口径（与 C1-002/C2-001 降档的分界论证见 C2 主文件维度复核结论）。
- 锁序复核（BIN→LSN→SINFO 无环）与 :230/:238 锁内纯内存操作的负结果抽核通过；10 个 synchronized 站点枚举复核成立。
- 终止信号裁定认可：仅 1 条 P2 且深挖面穷尽收敛，无需第 3 轮。
