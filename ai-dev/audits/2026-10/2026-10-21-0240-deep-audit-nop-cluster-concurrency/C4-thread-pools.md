# C4 线程池与执行器治理 — nop-cluster（WI 4.2）

- 审计维度：C4（线程池与执行器治理）
- 审计对象：nop-cluster 全模块（7 个 src/main/java 源码根：nop-cluster-core / nop-rpc-cluster / nop-cluster-nacos / nop-cluster-sentinel / nop-hazelcast-core / nop-hazelcast-cache / nop-cluster-admin）
- 审计模式：盲审初审（单批次），以 live code 为准，未读任何既有 audit/plan/bug/lesson/backlog 记录
- 日期：2026-10-21

## 审计范围与口径

### owner doc 覆盖声明

`docs-for-ai/` 对 nop-cluster 的线程/执行器/定时器行为**零覆盖**（无任何 owner doc 声明 globalTimer 使用契约、选主轮询线程模型、Nacos 回调线程契约）。因此本维度全部契约以源码与 javadoc 为准；所有「已声明边界豁免」均不适用——不存在可豁免的已声明边界。

### 规模口径

7 个源码根共 74 个主源码文件（不含测试），与 R1 实测口径（74f / 5,577 行）一致。全模块 grep 复核（`new Thread` / `Executors.*` / `commonPool` / `parallelStream` / `supplyAsync` / `ScheduledExecutor` / `synchronized` / `ThreadLocal`）未发现基线 C4a/C4b 之外的线程创建点；数量结论已人工复现。

### rpc-cluster 零自建池事实声明

`nop-rpc-cluster` **零自建池**（无 `new Thread`、无自建 `ThreadPoolExecutor`、无 commonPool 使用）。其对共享池的占用全部通过 `GlobalExecutors.globalTimer()` 注入，共 2 站点：`ClusterRpcProxyFactoryBean.java:43` 与 `ClusterRpcServiceInvoker.java:68`（与 R1 实测一致）。二者均把**裸 globalTimer**（未 `executeOn(...)` 包装）传入 `ClusterRpcClient`，再流入 `PollingRpcClient`——这是本维度 C4-003 的核心占用面。

### 线程池清单表（池 × 参数 × 队列 × 拒绝策略 × 关闭路径 × 判定）

| # | 池/线程 | 创建点 | 参数 | 队列 | 拒绝策略 | 关闭路径 | 判定 |
|---|---|---|---|---|---|---|---|
| 1 | `nop-global-timer`（共享，5 线程上限） | `GlobalExecutors.globalTimer()`（nop-commons，基础设施不重审，只审使用） | daemon，core=4，max=`nop.commons.concurrent.global-timer.maxPoolSize`（默认 5） | JDK `ScheduledThreadPoolExecutor` 无界 DelayedWorkQueue | n/a（无界队列） | JVM 全局单例，无 destroy 钩子；模块侧无关闭责任 | 共享占用面见 C4-003（rpc-cluster 轮询体在 timer 线程执行）；elector 站点已正确用 `executeOn(globalWorker())` 卸载 |
| 2 | `nop-global-worker`（共享，30 线程上限） | `GlobalExecutors.globalWorker()`（nop-commons） | daemon，core=4，max=`...global-worker.maxPoolSize`（默认 30），keepAlive=60s | `TaskQueue`（LinkedBlockingQueue 子类），容量 10000 | `RejectHandler`（`StandardThreadPoolExecutor.java:67` 注入，恒抛 RejectedExecutionException；`ExecutorHelper.java:85-88`） | 同上 | elector 选举体、AutoRegistration 刷新体在此执行；断链风险见 C4-004 |
| 3 | `BindScheduledExecutor` 包装器（elector 站点） | `AbstractPollingLeaderElector.java:19` `globalTimer().executeOn(globalWorker())` | 调度在 timer、执行在 worker | 透传 #1/#2 | 透传 #2（Abort） | 无独立生命周期（包装器无 destroy 调用方） | `isDestroyed()` 不转发 → 守卫失效，见 C4-002 |
| 4 | 选主轮询回路（自 perpetuating 调度链，非池） | `AbstractPollingLeaderElector.doStart`/`scheduleCheck` | 周期=`checkIntervalMs`（默认 2000ms），一次性 schedule 链式自续 | 占用 #3 | — | **无停止句柄**（Future 丢弃，doStop 不取消在途调度） | C4-001（stop 后回路复活）、C4-004（断链无监督） |
| 5 | AutoRegistration 自动刷新链（同上） | `AutoRegistration.java:205-206` `scheduleWithFixedDelay` | 周期=`autoUpdateInterval` | 占用 #1+#2 | 透传 #2 | `stop()` 有 `cancel(false)`（本模块内对称性 OK） | 断链无监督归入 C4-004；停止竞态轻微（见 4.4 备注） |
| 6 | Nacos SDK 内部线程（notify/healthcheck 等） | Nacos client（外部基础设施） | 不重审 | — | — | `NacosNamingService.destroy()` 调 `shutDown()`（对称） | 回调线程占用面（cleanupTasks 内联执行）**C3 已立案，本维度不重复** |
| 7 | Hazelcast 内部线程 | `Hazelcast.newHazelcastInstance`（外部基础设施） | 不重审 | — | — | `HazelcastProvider` @PostConstruct/@PreDestroy 对称（`HazelcastProvider.java:25-44`） | 通过 |
| 8 | nop-cluster-sentinel / nop-cluster-admin | 无线程创建 | — | — | — | — | 通过（`ClusterAdminBizModel.shutdown()` 为空实现，无线程行为） |

### 与已立案条目的边界声明

按派发口径，C1+C2 已立案 12 条、C3 已立案 11 条。本维度逐一对照后声明以下**不重复立案**边界：

- **Nacos subscribe 回调线程内联执行 cleanupTasks**（`NacosNamingService.java:268-276`）：失效面=事件线程被业务清理任务占用/失败影响事件分发——属 C3 已立案的「cleanupTasks 事件线程内联」同站点同失效面，本维度不立案。
- **Nacos subscribe 泄漏**（`getServiceInfo` 订阅无对应 unsubscribe 路径，`NacosNamingService.java:186-199`）：C3 已立案，不重复。
- **`instanceCache.computeIfAbsent` / `CachingNamingService` cache loader 在锁内执行网络 I/O**（`NacosNamingService.java:175`、`CachingNamingService.java:37`）：失效面=桶锁/check-then-act 竞态，属 C1/C2 锁面（基线 grep 已列该站点），本维度不重复立案。
- **ClusterRpcClient callAsync 吞异常 / BroadcastRpcClient 半个成功 / 选主 promise（electionPromise）竞态**：C3 已立案，本维度只审其线程模型面（调度链生命周期），不触碰 promise 并发语义。
- **C2-003 已立案写站点锁面**：AbstractPollingLeaderElector 同站点的锁/状态一致性归 C2，本维度只审线程生命周期与调度链治理（C4-001/002/004 均为线程模型面，与锁面失效机制不同）。

### 降档口径

本维度全部发现为**长驻运行期面**（选主回路、注册刷新链、RPC 轮询链均为常驻后台回路），「启动期降档不适用」，无降档条目。

---

## 发现

### [C4-001] 选主轮询回路无停止句柄：stop 后回路复活，已停止节点可重新抢主并形成双回路并发选举

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractPollingLeaderElector.java:16-29`；协同证据：`AbstractLeaderElector.java:119-124`（doStop 不触碰调度链）、`nop-kernel/nop-commons/src/main/java/io/nop/commons/service/LifeCycleSupport.java:107-108`（stop 完成后状态复位为 CREATED）
- **证据片段**:
  ```java
  // AbstractPollingLeaderElector.java
  protected void doStart() {
      if (scheduledExecutor == null) {
          scheduledExecutor = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
      }
      scheduledExecutor.schedule(this::checkElection, 0, TimeUnit.MILLISECONDS); // 返回的 Future 被丢弃
  }
  protected void scheduleCheck() {
      IScheduledExecutor executor = this.scheduledExecutor;
      if (executor != null && !executor.isDestroyed())      // 对默认包装器恒为 false（见 C4-002）
          executor.schedule(this::checkElection, getCheckIntervalMs(), TimeUnit.MILLISECONDS); // Future 再次丢弃
  }
  // AbstractLeaderElector.java
  protected void doStop() {
      electionPromise.cancel(false);
      onStop(); listeners.clear();      // 不取消任何在途/待触发的 checkElection 调度
  }
  ```
- **严重程度**: P1
- **并发场景**: `nop-global-worker` 池线程（执行 checkElection 选举体）× 发起 `stop()` 的主线程（IoC @PreDestroy / 管理动作）× `nop-global-timer`（持有待触发的 checkElection 调度项）。
- **触发时序**: 轮询回路常态下每个周期都有一个新的 `checkElection` 调度项挂在 timer 的延迟队列上（窗口宽度≈`checkIntervalMs`，默认 2000ms，**几乎任何时刻 stop 都落在窗口内**）。T1：主线程在 t0 调用 `stop()`，`doStop` 只取消 electionPromise；t0+2s 时 T2：worker 线程执行仍在队列里的 `checkElection`。此时 `LifeCycleSupport.stop()` 已完成且 `isAllowRestart()` 默认 true → 状态复位为 CREATED（`LifeCycleSupport.java:107-108`，ServiceStatus.CREATED(0) < STOPPING(3)）→ `isStopping()` 返回 **false** → T2 走完整选举逻辑：`SysDaoLeaderElector.checkFollower`（`SysDaoLeaderElector.java:142-148`）发现「DB 记录本节点是 leader 但本地是 follower」→ `changeLeader` 写 DB + `onBecomeLeader` + `onElectionCompleted`，随后尾部 `if (!isStopping()) scheduleCheck()` 再次成立 → **回路永久复活**。若进程随后又 `start()`（allowRestart=true 明确支持 stop→start 复用），doStart 再 schedule 一条新链 → 同一实例上**两条并发选举回路**交错执行 DB 抢主。
- **现状**: 停止路径完全没有回收轮询回路：一次性 schedule 的 Future 两处全部丢弃、doStop 不 cancel 延迟队列中的调度项、也不置空 `scheduledExecutor`；`isStopping()` 依赖的 LifeCycle 状态在 stop 完成后即复位，形同虚设。
- **风险**: 正在停机的节点在窗口期内重新写入 lease 行并持续续期 → 集群把流量/任务分区继续指派给濒死节点；进程真正退出后租约要等 `leaseMs`（默认 30s）过期才能 failover；stop→restart 场景双回路并发改同一 lease 行，epoch 抖动、`onBecomeLeader` 监听器被重复回调。对照故障模型：这不是异常路径，而是**每次优雅停机都落入的宽窗口竞态**。
- **建议**: `scheduleCheck()` 保留返回的 `Future`（字段 `volatile Future<?> pendingCheck`），`doStop()` 中 `cancel(false)`；或引入 `volatile boolean stopped` 于 doStart/doStop 置位，`scheduleCheck()` 与 checkElection 尾部双检该标志（不依赖 LifeCycleStatus，因其会被 allowRestart 复位）；同时给 `BindScheduledExecutor` 补 `isDestroyed()` 转发（见 C4-002）。修复点在 `AbstractPollingLeaderElector`/`AbstractLeaderElector` 一处即可覆盖全部子类。
- **失效面枚举**（F3，grep `extends AbstractPollingLeaderElector` 全量）:
  1. `SysDaoLeaderElector`（nop-sys-dao，`SysDaoLeaderElector.java:45-76`）——继承缺陷，checkElection 尾部 `if (!isStopping()) scheduleCheck()`（L73-74）受同窗口影响：**受影响，未修复**
  2. `JdbcLeaderElector`（nop-stream-runtime，`JdbcLeaderElector.java:114-147`）——该子类已在循环头补 `isStopping()` 前置检查（防在途迭代），但**尾部 reschedule 与延迟队列窗口同样未设防**：**受影响（部分缓解），未修复**
  3. `TestAbstractLeaderElector`（测试，不适用）
- **信心水平**: 确定（机制链全部可从源码直接追溯，无推测环节）
- **误报排除**: 不是「低频路径粗粒度锁」类误报——这是线程生命周期/停止句柄缺失；也不是平台基础设施自身问题：`AbstractPollingLeaderElector`/`AbstractLeaderElector` 是 nop-cluster-core 自有代码，停止语义是其自身契约。
- **复核状态**: 未复核
- 复核状态: 降级(P1→P2，同案双轨)｜新发现未修复——复活机制全链逐环回码成立：LifeCycleSupport:26 `allowRestart=true` 缺省、:107-108 stop 完成复位 CREATED、isStopping()=status≥STOPPING(3)（:114-117）、ServiceStatus 序数 CREATED(0)<STOPPING(3)、SysDaoLeaderElector:142-148 checkFollower「DB 记录本节点是 leader 而本地 follower」→changeLeader 抢主、:73-74 尾部 `if(!isStopping()) scheduleCheck()` 无守卫、JdbcLeaderElector:123 头检仅缓解在途迭代/:146-147 尾部同窗。判级受同案计数约束：根因（doStop 不撤销轮询回路）已由 C1-004（P1，复活状态面）主登记、调度句柄面已由 C2-003（P2「pending 调度不可撤销」）立案；本条独立价值=停止后 pending 调度触发**完整重选主**（DB 抢主/双回路）互补面，修复与 C1-004 同 diff（pendingCheck Future + stopped 标志），P1 由 C1-004 承载不重复计数

### [C4-002] BindScheduledExecutor 不转发 isDestroyed()：AbstractPollingLeaderElector.scheduleCheck 的销毁守卫恒为 false（死守卫），被销毁的自定义执行器仍被继续投递

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractPollingLeaderElector.java:25-29`（守卫站点）；证据根：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BindScheduledExecutor.java:25-180`（类全量无 `isDestroyed` 覆写）、`nop-kernel/nop-commons/src/main/java/io/nop/commons/lang/IDestroyable.java:15-16`（default 返回 false）、`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultScheduledExecutor.java:96-99`（真实实现 `executor == null || executor.isShutdown()`）、`DefaultScheduledExecutor.java:100-109`（`checkEnabled` 抛 `ERR_SERVICE_NOT_ACTIVE`）
- **证据片段**:
  ```java
  // AbstractPollingLeaderElector.java:25-29 —— 守卫写法假定 executor 会如实报告销毁状态
  IScheduledExecutor executor = this.scheduledExecutor;
  if (executor != null && !executor.isDestroyed())
      executor.schedule(this::checkElection, getCheckIntervalMs(), TimeUnit.MILLISECONDS);

  // BindScheduledExecutor.java —— 覆写清单仅 getConfig/refreshConfig/executeOn/schedule×2/
  // scheduleAtFixedRate/getName/stats/submit×2/destroy/execute；IDestroyable.isDestroyed() 未覆写，
  // 继承 default 实现恒返回 false
  ```
- **严重程度**: P2
- **并发场景**: 应用关闭/重配置线程 destroy 自定义 `IScheduledExecutor`（典型为 beans.xml 注入的 `DefaultScheduledExecutor`）× globalWorker 线程上正在收尾的 `checkElection`。
- **触发时序**: T1：管理线程调用自定义 `DefaultScheduledExecutor.destroy()`（shutdownNow）。T2：worker 线程执行中的 `checkElection` 到达尾部 `if (!isStopping()) scheduleCheck()`——守卫读到包装器 `isDestroyed()==false`，放行投递；`DefaultScheduledExecutor.checkEnabled()`（`DefaultScheduledExecutor.java:100-105`）抛 `ERR_SERVICE_NOT_ACTIVE`。该异常从 `SysDaoLeaderElector.java:73-74` 的 **try/catch 之外**抛出（catch(Exception) 块已结束），沿 `ExecutorHelper.schedule` 的 catch(Throwable) 被记为通用 `nop.executor.execute-fail` 日志后丢弃 → 当轮之后回路**静默死亡**（与 C4-004 同一终态，但此处由「守卫失效」直接造成）。
- **现状**: `scheduleCheck()` 的销毁守卫对模块默认路径（`executeOn(globalWorker())` 包装器）与自定义 `DefaultScheduledExecutor` 被包装两种形态均失效——包装器不转发销毁状态，守卫永远不会拦住投递。
- **风险**: 销毁执行器本意是让轮询回路有序终止，实际却以裸异常方式打断回路且不留业务可观测痕迹；若未来有站点依赖该守卫做有序下线，将全部落空。
- **建议**: `BindScheduledExecutor` 增加覆写：`public boolean isDestroyed() { return timer.isDestroyed(); }`（nop-commons 侧一行修复，惠及全部使用方）；`AbstractPollingLeaderElector.scheduleCheck()` 捕获 `scheduleCheck` 抛出的异常并记业务错误码（如 `nop.cluster.leader-elector.schedule-fail`），避免回路以通用执行器日志静默终止。
- **失效面枚举**（F3，grep 模块内全部 `isDestroyed()` 消费点 + 包装器使用点）:
  1. `AbstractPollingLeaderElector.scheduleCheck`（本站点）：**受影响，未修复**
  2. `AutoRegistration`（`AutoRegistration.java:205`，同用包装器但不消费 isDestroyed）：**不受影响**
  3. `ClusterRpcProxyFactoryBean.java:43` / `ClusterRpcServiceInvoker.java:68`（裸 globalTimer，无包装器）：**不受影响**
- **信心水平**: 确定
- **误报排除**: 不是「原语使用不是问题」类误报——问题不在 `isDestroyed()` 原语，而在包装器组合（`executeOn`）破坏了销毁状态的传播，属于复合语义缺陷；且该缺陷有可写出的具体触发链与模块内唯一消费点。
- **复核状态**: 未复核
- 复核状态: 降级(P2→P3)｜新发现未修复（机制勘误价值）——BindScheduledExecutor 全类无 isDestroyed 覆写（覆写清单核对成立）、IDestroyable 默认 false、DefaultScheduledExecutor.checkEnabled 在 schedule 调用时即抛 ERR_SERVICE_NOT_ACTIVE（:114-119，并经 ExecutorHelper:176-198 catch(Throwable) 记通用日志）均回码成立。判级校准：守护空转面已由 C2-003（保留 P2）现状节立案且其复核判定明含「scheduleCheck:27 守护空转证实」；原条目「检查的是全局 timer 销毁态」表述不准——实为包装器不转发（本条勘误采纳）；默认路径 underlying globalTimer 为进程生命周期单例永不 destroy（C2-003 同款论证成立），注入**裸** DefaultScheduledExecutor 反而如实转发 isDestroyed（:96-99）——触发需自定义非转发包装器形态装配；后果=destroy 的预期终态+通用日志，无泄漏、无错误选主，P3

### [C4-003] rpc-cluster 把裸 globalTimer（5 线程共享池）交给 PollingRpcClient：轮询任务体在 timer 线程执行，且轮询链无整体截止时间，可长期占用全平台共享定时器

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcClient.java:143-150`（接线站点）、`ClusterRpcProxyFactoryBean.java:42-43` 与 `ClusterRpcServiceInvoker.java:67-68`（裸 globalTimer 注入）；后果载体：`nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/composite/PollingRpcClient.java:94-101`；执行语义证据：`nop-kernel/nop-commons/.../ExecutorHelper.java:176-198`（`DefaultScheduledExecutor.schedule(Callable)` 在 **timer 线程**上直接执行 callable）
- **证据片段**:
  ```java
  // ClusterRpcClient.java —— timer 为裸 globalTimer（未 executeOn 卸载）
  String pollingMethod = RpcHelper.getPollingMethod(request);
  if (pollingMethod != null) {
      int interval = RpcHelper.getPollInterval(request);
      if (interval > 0) {
          service = new PollingRpcClient(service, pollingMethod, timer,
                  interval, RpcHelper.getMaxPollErrorCount(request));
      }
  }
  // PollingRpcClient.java:97 —— 轮询体在 timer 线程执行（ExecutorHelper.schedule 直跑 callable）
  timerFuture = timer.schedule(this, pollInterval, TimeUnit.MILLISECONDS);
  ```
- **严重程度**: P2
- **并发场景**: `nop-global-timer`（默认 5 线程，全平台共享定时器）× 每个进行中的 polling 型 RPC 请求一条 `PollTask` 链；轮询体的同步前缀（URL 构建、`JSON.stringify(request.getData())` 全量请求体序列化、`LOG.info`）与 `whenComplete` 续排逻辑都在 timer 线程上执行。
- **触发时序**: T1：调用方经 `ClusterRpcClient.callAsync` 发起带 polling 语义的请求（`RpcHelper.getPollingMethod(request)` 命中），远端任务长时间不完成。T2：每过 `pollInterval`，`PollTask.call()` 在 5 选 1 的 timer 线程上执行同步前缀并续排下一轮；若调用方**放弃响应且未触发 cancelToken**（`cancelToken==null` 或不取消），`PollTask.future` 永不完成、`timerFuture` 永续重排 → 该链常驻共享 timer：延迟队列持续累积重排项、每个 tick 反复占用 timer 线程做全量请求体序列化。N 条此类链并存时，全平台定时任务（family 先例：globalTimer 族 WI 1.5 MA-04 / WI 2.1 C4-001 / WI 3.1 MA-04——业务体与簿记共享致定时失准）被持续挤压。
- **现状**: 模块内其他 globalTimer 消费点（选主回路、AutoRegistration）都通过 `executeOn(globalWorker())` 把任务体卸载到 worker 池，唯独 rpc-cluster 的轮询链把任务体留在 timer 线程；且 `PollingRpcClient` 只有 `maxErrorCount` 与 cancelToken 两个出口，**没有整体 deadline**。
- **风险**: 长时运行中每个被放弃/远端挂死的 polling 请求都是一条不可回收的常驻链（内存持有 request/cancelToken/future + timer 队列项），叠加后全局定时精度劣化；无任何超时兜底使恢复只能依赖进程重启。
- **建议**: `ClusterRpcProxyFactoryBean.java:43` / `ClusterRpcServiceInvoker.java:68` 与 `AbstractPollingLeaderElector.java:19` 对齐：`GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker())` 后再传给 `ClusterRpcClient`（一行改动，轮询体即卸载到 worker）；并在 `PollingRpcClient` 增加可选整体超时（对照 `nop.commons.concurrent` 平台设施的 `IRateLimitExecutor`/超时包装），cancelToken 缺失时也强制终止链。
- **失效面枚举**（F3，grep 模块内全部 globalTimer 注入点）:
  1. `ClusterRpcProxyFactoryBean.java:43` → `ClusterRpcClient` → `PollingRpcClient`：**受影响（裸 timer），未修复**
  2. `ClusterRpcServiceInvoker.java:68` → 同上：**受影响（裸 timer），未修复**
  3. `AbstractPollingLeaderElector.java:19`：**已正确卸载（executeOn(globalWorker())），对照项**
  4. `AutoRegistration.java:205`：**已正确卸载，对照项**
  5. `BroadcastRpcClient` / `HttpRpcServiceInvoker`：不使用 timer，**不受影响**
- **信心水平**: 很可能（线程占用与无截止链机制确定；实际后果量级取决于 polling 型 RPC 的使用频率，本仓库内为 opt-in 路径）
- **误报排除**: 不是「有界声明的池不报」——问题不在池参数，而在共享池的**任务体放置决策**（timer 线程跑业务体）与**链生命周期无界**，与平台族先例（globalTimer 占用面）同型；也不是 nop-rpc-core 单方问题——裸 timer 的接线决策发生在本模块 `ClusterRpcClient`/两个 FactoryBean。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（live 面）——两注入点 `if (timer == null) timer = GlobalExecutors.globalTimer()` 裸注入（ClusterRpcProxyFactoryBean.init/ClusterRpcServiceInvoker.init 回码成立）、ExecutorHelper:176-198 `DefaultScheduledExecutor.schedule(Callable)` 在 timer 线程直跑 callable、PollTask 无整体 deadline（仅 maxErrorCount/cancelToken 两出口，schedule() 前置 `future.isDone()` 检查是唯一自停条件）均回码成立。opt-in 面证实：polling 仅在 request 属性 `PROP_POLLING_METHOD` 非空时激活（RpcHelper:43-45），非缺省主路径。跨单元 globalTimer 占用族单列：先例 WI 1.5 MA-04/WI 2.1 C4-001/WI 2.3 C8-001 判 P1 系缺省主路径阻塞/钉死全池；本条为 opt-in 路径 + 单链每 tick 小代价 + 放弃型泄漏，P2 恰当，修复时与平台族隔离方案统一裁决

### [C4-004] 选主/注册两条自续调度链均为「单点断链、无监督」：worker 池拒绝或 Error 一律导致回路永久静默死亡，无人感知、无人重启

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractPollingLeaderElector.java:22,28`（丢弃 schedule 返回值）+ `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/AutoRegistration.java:204-218`（刷新链）；断链机制证据：`nop-kernel/nop-commons/.../BindScheduledExecutor.java:88-111`（fixedDelay 链任务体异常/重排）与 `BindScheduledExecutor.java:144-154`（一次性 schedule 执行腿 rejection 分支：仅 `completeExceptionally` + 通用日志）、`nop-kernel/nop-commons/.../ExecutorHelper.java:85-88`（globalWorker 有界队列 10000 + `RejectHandler` 恒抛 RejectedExecutionException）
- **证据片段**:
  ```java
  // AbstractPollingLeaderElector.java:22 —— 一次性链：返回的 future（异常载体）被丢弃
  scheduledExecutor.schedule(this::checkElection, 0, TimeUnit.MILLISECONDS);
  // BindScheduledExecutor.java（一次性 schedule 的执行腿）
  try {
      executor.execute(() -> { ... });          // worker 池拒绝时走 catch
  } catch (Throwable e) {
      future.completeExceptionally(e);          // 无重排 → 链死亡；调用方未持有 future
  }
  // AutoRegistration.java:210-218 —— refreshRegistration 只捕 Exception，
  // 链级断链（rejection/Error）发生在它之外，catch 不可达
  } catch (Exception e) {
      LOG.warn("nop.err.cluster.refresh-registration-fail:instance={}", instance, e);
  }
  ```
- **严重程度**: P2
- **并发场景**: `nop-global-worker`（core=4/max=30/queue=10000/Abort）饱和或任务抛 Error（OOM、NoClassDefFoundError 等）时，与两条常驻链（选主轮询、注册刷新）的下一跳投递竞争。
- **触发时序**: 以选主链为例：T1：worker 线程执行完第 N 轮 `checkElection`，尾部 `scheduleCheck()` 调 `BindScheduledExecutor.schedule` → timer 到期后尝试 `executor.execute(下一轮)`；若此刻 globalWorker 队列满（10000）且 30 线程忙 → `RejectedExecutionException` → `catch (Throwable)` → `completeExceptionally` → **无下一跳**。返回的 future 在 `scheduleCheck` 调用点被丢弃（`scheduleCheck` 自身不返回/不观察 future），无任何告警、无重试。终态：选主静默停止，当前租约 30s 后过期，集群进入无主状态且**没有一次业务错误码日志**（仅通用 `nop.err.thread-pool.execute-fail`，无 elector 上下文）。AutoRegistration 链同构：rejection 或 `refreshRegistration` 抛 Error 时刷新永久停止，注册元数据（weight/tags/enabled）静默变陈旧。
- **现状**: 两条链的自续机制依赖「上一跳成功执行完毕后由任务体内代码续排」，链的存活没有任何监督者（无 watchdog、无 future 观察、无重排失败重试）；与 C4-001 是同一机制的两面——该链既会在不该活时复活（stop 无句柄），也会在不该死时死掉（断链无监督）。
- **风险**: 长时运行必然暴露：任何一次 globalWorker 瞬时饱和（全平台共享池，饱和场景现实存在）或一次 Error 即造成集群失主/元数据陈旧，且故障不可见（F9：静默吞掉导致状态不一致）；恢复完全依赖人工重启。
- **建议**: `AbstractPollingLeaderElector.scheduleCheck()` 改为返回并集中观察 `Future`（`whenComplete` 中对非取消异常记 `nop.cluster.leader-elector.loop-broken` 并按退避重排一次）；`AutoRegistration` 同样观察 `autoUpdateTimerFuture`（其 `scheduleWithFixedDelay` 语义下链死亡即 future 异常完成，可直接挂 `whenComplete` 告警）；长期方向是把两条链迁到平台已有的 `CycleSynchronizer`/带监督的调度设施，避免手写自续链。
- **失效面枚举**（F3，全模块自续/定时链清单）:
  1. `AbstractPollingLeaderElector` 轮询链（一次性 schedule 自续）：**受影响（rejection/Error 断链无监督），未修复**
  2. `AutoRegistration` 刷新链（scheduleWithFixedDelay）：**受影响（同机制；task 内 catch(Exception) 不覆盖链级失败），未修复**
  3. `PollingRpcClient` 轮询链（C4-003 站点）：`handleError`/catch(Throwable) 内有 `schedule()` 续排兜底，链级断链面已被其自身 catch 覆盖大半，仅 worker/timer 层 rejection 仍可断链：**部分受影响**
  4. Nacos SDK 内部重连/心跳链：外部基础设施，不重审
- **信心水平**: 确定（断链时序可完整写出；触发条件为可现实的池饱和/Error）
- **误报排除**: 不是「对平台基础设施重审」——基础设施只提供原语，缺陷在于 nop-cluster 两条链对原语的组合方式（自续、无句柄、无观察者）是其自身线程治理决策；也不是「有界池+Abort 即合理」的豁免场景——问题不在拒绝策略本身，而在被拒后链的生命周期无人接管。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（live 面）——断链机制逐环回码成立：BindScheduledExecutor 一次性 schedule 执行腿 rejection→`completeExceptionally` 无重排（:152-154，future 被 scheduleCheck 丢弃）、scheduleWithFixedDelay 链 task 抛 Throwable 即断链无续排（:101-105 return 先于 :107 重排）、AutoRegistration.refreshRegistration 仅 catch(Exception)（:214-216，链级 rejection/Error 不可达该 catch）。判级校准：跨单元自续链断链族单列（WI 2.1 C3-001 平台绝缘层 29 站点、WI 1.6 C8-001 P1、WI 4.1 C8-002）——本条为消费端新站点，修复可搭 WI 2.1 C3-001 绝缘层统一方案；后果校准：选主链死亡经**他节点** elector 可自愈（租约 30s 过期后他人抢主），AutoRegistration 链死亡仅元数据陈旧（Nacos 心跳独立于刷新链、注册不消失），未达 P1 门槛，P2 恰当

### 4.4 备注（不立案，登记为证据）

- `AutoRegistration.stop()`（`AutoRegistration.java:220-229`）`cancel(false)` 不中断在途 `refreshRegistration`，理论上与 `unregisterInstance` 存在先注后更的窄竞态；Nacos 侧 `updateInstance` 对已注销实例报错且注销注册为 ephemeral 最终一致，后果可自愈，不构成立案级缺陷。
- `NacosNamingService.destroy()` 与 `AutoRegistration.stop()` 的 @PreDestroy 顺序由 IoC 容器依赖序决定（AutoRegistration @Inject INamingService），未见反序证据，不立案。

---

## 维度集外线索（overlay C5/C6/C7/C9）

### [nop-cluster-CL-012] rpc-cluster 热路径每请求重建服务对象与全量日志（C9 性能/分配面）

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/http/HttpRpcServiceInvoker.java:66-74`（每请求 `new HttpRpcService` + `new DefaultRpcUrlBuilder`）、`ClusterRpcClient.java:137-152`（每调用 `new CancellableRpcClient`/`new PollingRpcClient`）、`nop-rpc-http/.../HttpRpcService.java:46`（每请求 `LOG.info("nop.http.request:url={}")`）。
- 线索：请求级对象构造本身无并发缺陷，但热路径分配 + info 级日志在高 QPS 下为可量化浪费；且每次 `getRpcClient` 新建包装链使跨请求复用/指标聚合失效。建议复核阶段按 C9 口径评估缓存化（按 instance 缓存 IRpcService）与日志降级。**信心：很可能**。

### [nop-cluster-CL-013] PartitionResolver 双 volatile 非原子对更新，TTL 判定可见中间态（C1 线索）

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/PartitionResolver.java:49-50,117-118`。
- 线索：`cachedPartitions` 与 `lastResolveTime` 为两个独立 volatile，写序为「先 cache 后 time」；并发读者可见（新 cache, 旧 time）→ 下一个读者误判 TTL 已过而提前再解析，或（旧 cache, 新 time）窗口取决于写交错。后果限于多一次 `getInstances` 解析（幂等），无正确性破坏；理论风险，未具体化到损害，按 C1 口径登记不立案。**信心：确定（机制）/ P3（后果）**。

### [nop-cluster-CL-014] 常驻链失败只有执行器通用日志，缺业务上下文观测点（C9 可观测性线索）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BindScheduledExecutor.java:102-103,148-149`（统一 `LOG.error("nop.err.thread-pool.execute-fail")`）× 消费方 `AbstractPollingLeaderElector`/`AutoRegistration`。
- 线索：与 C4-004 直接相关——链死亡时唯一痕迹是不带 elector/registration 上下文的通用错误码，运维无法区分「哪条业务链死亡」。建议 error-code 层为常驻链提供带业务标识的失败上报。**信心：确定**。

### [nop-cluster-CL-015] ClusterRpcClient.retryCount 非 volatile 且支持运行期热更新（C5 线索）

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcClient.java:45,55-57`、`ClusterRpcProxyFactoryBean.java:52-60`（`setRetryCount` 注释明示「动态更新」）。
- 线索：配置刷新线程写 `retryCount`（普通 int），RPC 完成回调线程并发读——JMM 下无同步关系，实际后果仅为读到旧值（int 无撕裂），语义可接受；按 C5 口径登记，若热更新契约要求立即可见则需 volatile。**信心：确定（机制）/ P3（后果）**。

---

## 维度复核结论（WI 4.2 独立复核员，2026-10-20）

- **方法**：逐条回码（elector 基类/子类、LifeCycleSupport、BindScheduledExecutor、DefaultScheduledExecutor、ExecutorHelper、AutoRegistration、ClusterRpcClient、两个 FactoryBean/Invoker、PollingRpcClient、RpcHelper）+ 台账查重（cra-index WI 1.1–4.1 全量 P1 台账 + CL 台账）+ 同单元 C1/C2/C3 已立案边界核对 + 降档口径核对。

### 逐条判定表

| 发现 | 初判 | 复核判定 | 关键复核证据 |
|------|------|---------|-------------|
| C4-001 | P1 | **降级 P2（同案双轨，P1 由 C1-004 承载）** | 复活链逐环回码成立（LifeCycleSupport:26/107-108/114-117、SysDaoLeaderElector:142-148/:73-74、JdbcLeaderElector:123/:146-147，见条目状态行）；但根因已由 C1-004（P1，复活状态面，复核保留）+ C2-003（P2，含「pending 调度不可撤销」与「stop 无在途 checkElection 撤销手段」明文）主登记——维持独立 P1 即同站点同根三重计数。本条增量面（停止后 pending 调度触发完整重选主/DB 抢主/restart 双回路）并入 C1-004 修复 diff 的回归面 |
| C4-002 | P2 | **降级 P3（守护空转面并入 C2-003，本条保留机制勘误）** | 全链回码成立（无 isDestroyed 覆写/IDestroyable 默认 false/checkEnabled schedule 时抛）；降档依据：① 该面已在 C2-003（保留 P2）立案并复核证实；② 默认路径 underlying globalTimer 进程内永不 destroy、裸 DefaultScheduledExecutor 注入如实转发（:96-99）——触发需自定义非转发包装器；③ 后果=destroy 预期终态+通用日志，非缺陷性终态 |
| C4-003 | P2 | **保留 P2** | 裸 timer 注入（init 兜底分支）、timer 线程直跑 callable（ExecutorHelper:176-198）、无整体 deadline（PollTask 仅 isDone 前置检查）回码成立；opt-in 证实（PROP_POLLING_METHOD request 属性，RpcHelper:43-45）；与 globalTimer 族 P1 先例（缺省主路径阻塞）分档成立 |
| C4-004 | P2 | **保留 P2** | 断链机制回码成立（执行腿 rejection completeExceptionally 无重排 :152-154；fixedDelay task Throwable 断链 :101-105；refreshRegistration catch(Exception) 不覆盖链级失败）；跨单元族关联单列（WI 2.1 C3-001 绝缘层/WI 1.6 C8-001）；后果校准：他节点选主自愈 + 注册不因刷新链死亡消失，未达 P1 |
| C4-005（R2） | P3 | **保留 P3** | 两步获取无回滚回码成立（AutoRegistration:202 → :205-206，②失败无 unregister）；启动期极端窗口理论风险标注符合共享前缀第 4 条口径；feature 门控（nop.cluster.registration.enabled）现状标注采纳 |

### 降档口径核对

- 本维度 5 条全部为**长驻运行期面**，无一适用启动期降档（原文件「降档口径」节声明复核维持）。
- C4-001/C4-002 的降级**非**休眠面降档，而是**同案计数约束**：两面的主登记分别在 C1-004（P1）与 C2-003（P2），复核已确认保留——本维度不再重复计级。elector 装配本身为 config-gated（`nop.cluster.leader-elector.sys-dao-elector.enabled` 条件装配，app-dao.beans.xml:26-29）与 stream HA 模式（JobCoordinatorMain:247-270，haEnabled 门控）——启用面内 stop 复活**必现**（非零接线，不适用条件化降档）。
- C4-005 为 feature-gated（`nop.cluster.registration.enabled`，rpc-cluster-defaults.beans.xml:8-9；grpc 侧同款 nopGrpcAutoRegistration）+ 启动期极端触发窗，初判已 P3，维持。

### 同案查重（台账 + 本单元）

- **globalTimer 族**（WI 1.5 MA-04 / WI 2.1 C4-001 / WI 2.3 C8-001 / WI 3.1 C4-011）：C4-003 为消费端 opt-in 新站点，单列不重复；修复归属与平台族隔离方案统一裁决。
- **自续链断链族**（WI 2.1 C3-001 29 站点绝缘层 / WI 1.6 C8-001 / WI 4.1 C8-002）：C4-004 两条链为新消费端站点，单列；建议随 WI 2.1 C3-001 平台方案同批。
- **CL-024（WI 2.1 移交「per-client/topic 池线性累计→nop-cluster」）**：已知输入称漂移复查闭环（rpc-cluster 无同族池形态）——复核独立证实：rpc-cluster 零自建池/线程，getRpcClient 每请求构造包装对象（无池无线程累计），闭环结论一致。
- **WI 2.3 C8-005 令牌桶族 / WI 3.4 C3-005 错误以成功交付族**：本维度无同案（nop-cluster 无自建限流器实现；C4/C8 各条失败均显式上抛，无成功伪装形态）。

### 高风险条目子项复核说明（C4-001 逐子项）

1. **调度链 Future 丢弃**：doStart:22 与 scheduleCheck:28 两处返回值丢弃，回码成立；常态下每个周期恰有一个 pending check 挂 timer 延迟队列，窗口宽=checkIntervalMs（缺省 2000ms；stream HA 形态 500ms），「任何时刻 stop 均落窗内」成立。
2. **stop 后守卫失效**：isStopping()=status≥3（LifeCycleSupport:114-117）而 stop 完成即复位 CREATED（:107-108，allowRestart 缺省 true :26）→ 守卫恒 false，回码成立；原条目对 ServiceStatus 序数的引用准确。
3. **完整重选主**：onStop 清 leaderEpoch（AbstractLeaderElector:221）→ 复活的 checkElection 走 follower 分支 → checkFollower:142-148 `leader.getLeaderId().equals(getHostId())` → changeLeader 写 DB+onBecomeLeader+onElectionCompleted，回码成立；doStop/onStop **不删 DB lease 行**（复核补充：lease 仅靠过期/他人抢主），复活节点在停机窗口内可重写 lease 行——后果成立。
4. **restart 双回路**：allowRestart=true 支持重 start，doStart 再 schedule 一条新链与复活链并存——成立（结构性，非竞态）。
5. **同族子类**：SysDaoLeaderElector 全窗受影响；JdbcLeaderElector 头检（:118-123 注释即为此前修复 CC/plan 遗产）仅防「在途迭代期间 stop 的自接管」，对「stop 完成后 pending 触发」同窗失效——「部分缓解」表述准确。

## 最终保留项

| 最终严重度 | 条目 |
|-----------|------|
| P2×3 | C4-003（裸 globalTimer 轮询链无截止，live/opt-in）、C4-004（自续链断链无监督，live）、C4-001（stop 复活调度链面，双轨 P2——修复绑 C1-004 同 diff，P1 由 C1-004 承载） |
| P3×2 | C4-002（isDestroyed 不转发，机制勘误+并入 C2-003 面）、C4-005（start 两步无回滚，feature-gated） |

统计：保留原级 3 / 降级 2（C4-001 P1→P2 同案双轨、C4-002 P2→P3 同案并入）/ 驳回 0。**本维度不新增 P1**——单元 P1 维持 4 条（C1-001/C1-003/C1-004/C3-001），与合并批已知输入一致；C4-001 的 DB 抢主后果应作为 C1-004 修复的回归断言面（停机后断言无 lease 行重写、无尾部 reschedule）。CL-012/013/014/015 线索复核确认登记成立，其中 CL-014 与 C4-004 修复同批（业务标识失败上报）。
