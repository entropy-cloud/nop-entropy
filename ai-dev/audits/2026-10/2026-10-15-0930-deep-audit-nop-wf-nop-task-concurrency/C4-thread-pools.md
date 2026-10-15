# C4 线程池与执行器治理维度 — nop-wf + nop-task 第 1 轮初审（盲审）

- 审计单元：`nop-wf`（10 jar 子模块）+ `nop-task`（10 jar 子模块），合计 379 files / 36,107 行
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15；维度：C4 线程池与执行器治理
- 盲审纪律：未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件（本文件除外）；全部结论基于当前 live code + `_` 前缀生成文件排除规则。
- Owner doc 依据：`docs-for-ai/03-modules/nop-wf.md`、`docs-for-ai/03-modules/nop-task.md`（已完整阅读）。
- grep 基线：`_tmp/cra-baseline-nop-wf-nop-task.txt` 的 C4/C3/C8 节为起点，已人工全量核对并补充（基线 nop-wf 簇 C4「thread creation and pools」「scheduled」两节为空，本轮 grep 证实 wf 簇 main 源码确无线程池/定时器直接创建）。

---

## 检查范围说明

### wf 簇（nop-wf，10 子模块）

C4 真实使用面经全量 grep（`GlobalExecutors|globalTimer|IThreadPoolExecutor|ThreadPoolConfig|ScheduledExecutor|ExecutorService|newFixedThreadPool|Executors\.`）确认为：

1. **`IWorkflowExecutor` / `DefaultWorkflowExecutor`**（nop-wf-core）——唯一实现，`ioc:default="true"`（wf-core.beans.xml:12-13）。`execute()` 在**调用者线程内联**执行（构造 WorkflowImpl → task.apply → runAutoTransitionsWithLimit），**不涉及任何线程池**。javadoc 声明「同 wfId 并发调用不做互斥，写竞争由 ORM 乐观锁拦截」。
2. **`WorkflowServiceImpl.transferActorsAsync`**（nop-wf-core/service/impl/WorkflowServiceImpl.java:227-249）——对 stepRecords 循环调用 `FutureHelper.syncGet(workflowExecutor.execute(...))`。缺省内联 executor 下 syncGet 即时返回；仅当部署方替换为异步 IWorkflowExecutor bean 时才在 GraphQL 线程上真实阻塞（顺序逐项，有界）。
3. **`WfTaskScanner`**（nop-wf-scheduler）——scanDueTasks/scanRemindTasks 两个公共方法，经 `nop-job` local scheduler 装配：`app-scheduler.beans.xml` import `/nop/job/beans/app-local-scheduler.beans.xml`，两个 job.yaml（`_vfs/nop/job/conf/wf-due-task-scan.job.yaml`、`wf-remind-task-scan.job.yaml`）以 cron `0 0/1 * * * ?` / `0 0/5 * * * ?` 触发，**默认 `enabled=false`**。job 运行线程 = nop-job 专用池 `nopJobScheduledExecutor`（DefaultScheduledExecutor，core=4/max=4，可配 `nop.job.local-scheduler.pool-size`，destroy-method=destroy）。
4. wf 簇其余子模块（nop-wf-api/dao/meta/web/ai/app/service/codegen）grep 零命中——**wf 簇不自建线程池、不直接触碰平台执行器**。

### task 簇（nop-task，10 子模块）

1. **`TaskFlowManagerImpl.getScheduledExecutor()`**（impl/TaskFlowManagerImpl.java:76-81）——字段未注入（task-defaults.beans.xml 未 wire），恒返回 `GlobalExecutors.globalTimer()`。全簇所有调度类步骤共用此平台定时器。
2. **`TaskFlowManagerImpl.getThreadPoolExecutor(beanProvider, executorBean)`**（:83-89）——先查 `GlobalExecutors` 注册表（命中 `nop-global-timer`/`nop-cached-thread-pool`/`nop-global-worker` 三个保留名），未命中回退 `beanProvider.getBean(executorBean)` 强转。
3. **`ExecutorTaskStepWrapper`**（step/ExecutorTaskStepWrapper.java:29-69）——step 显式配置 `executor` 属性时（TaskStepEnhancer.java:118-120）把步骤体提交到解析出的池，配 cancel 兜底。
4. **`TaskStepHelper.timeout`**（utils/TaskStepHelper.java:197-243）——超时守卫 schedule 到 globalTimer；同步/异步/异常三条路径均 `timer.cancel(false)` 清理。
5. **`TaskStepHelper.retry` 延迟重试路径**（:262-284）——`scheduledExecutor.schedule(action, delay)`，**action 即步骤体**，在 globalTimer 线程上执行（发现 C4-001）。
6. **`DelayTaskStep`**（step/DelayTaskStep.java:39-49）——globalTimer 上 schedule 一个 no-op 占位（等待期不占线程），bindCancelToken 支持取消时撤单。安全用法。
7. **`ThrottleTaskStepWrapper` / `RateLimitTaskStepWrapper`**——`semaphore/rateLimiter.tryAcquire(1, maxWait)` 在调用线程上有界阻塞（包装顺序在 executor 之外，发生在提交前）。
8. **`RunOnContextTaskStepWrapper`**——`context.execute(...)` 分发到 IContext 自带执行通道，非直接池引用。
9. **`ParallelTaskStep` / `AbstractForkTaskStep` / `GraphTaskStep`**——零 executor 引用，纯 promise 聚合内联执行；与 owner doc「parallel/fork 只做 promise 聚合，仅显式配置 executor 才真正并行」声明一致（已载明边界，不立案）。
10. **`nopTaskExecutionQueue` bean**（task-defaults.beans.xml:8-15）——DefaultTaskExecutionQueue + 专属 ThreadPoolConfig（config-prefix `nop.task.execution-queue.executor`），全仓库零消费者（发现 C4-005）。
11. **`nop-task-queue` 模块**——仅含空占位接口 `ITaskQueue`（发现 C4-006）。

### 平台设施核对口径（只审使用方式）

`GlobalExecutors`（GlobalExecutors.java:38-96）：globalTimer=DefaultScheduledExecutor（daemon，maxPoolSize 来自 `nop.commons.concurrent.global-timer.maxPoolSize` 缺省 5）；globalWorker=DefaultThreadPoolExecutor（daemon，max 缺省 30）；cachedThreadPool=无界线程（maxPoolSize=-1、queue 0）。`ExecutorHelper.newScheduledExecutor`（ExecutorHelper.java:56-71）实际按 **corePoolSize**（ThreadPoolConfig.java:17 缺省 4）建 ScheduledThreadPoolExecutor 并显式忽略 maxPoolSize（:62-63 注释「ScheduledThreadPoolExecutor的maxPoolSize没有被使用」）。普通池为 StandardThreadPoolExecutor + TaskQueue（容量 10000，池先增长到 max 后强制入队，满后 RejectHandler 同步抛 RejectedExecutionException，无 CallerRuns/Discard）。

---

## 执行器清单表（使用点 × 池 × 隔离 × 关闭 × 判定）

| # | 使用点 | 位置 | 池 | 队列有界性 | 拒绝策略与被拒去向 | 线程命名 | 隔离 | 关闭路径（F8） | 判定 |
|---|--------|------|----|-----------|-------------------|---------|------|---------------|------|
| W1 | DefaultWorkflowExecutor.execute | wf-core/engine/DefaultWorkflowExecutor.java:32-49 | 无（调用线程内联） | N/A | N/A | N/A | 依赖 ORM 乐观锁 | N/A | 通过（声明与实现一致，见覆盖度） |
| W2 | WorkflowServiceImpl.transferActorsAsync syncGet | wf-core/service/impl/WorkflowServiceImpl.java:227-249 | 经 W1（内联） | N/A | N/A | N/A | 逐项顺序 | N/A | 通过（自定义异步 executor 时为有界顺序阻塞，边界记录） |
| W3 | WfTaskScanner.scanDueTasks/scanRemindTasks | wf-scheduler/WfTaskScanner.java:64-76 | nop-job `nopJobScheduledExecutor`（DefaultScheduledExecutor core=4/max=4，daemon 由配置默认 false？——beans 未设 threadDaemon，ThreadPoolConfig 默认 false） | ScheduledThreadPoolExecutor 无界内部队列 | 关闭后 REE 同步抛出 | `nop-job-local-scheduler-N` | wf 扫描独占一个 job 池 | LocalJobConfigLoader @PreDestroy → scheduler.deactivate；executor destroy-method=destroy，对称 | **失效**：job.yaml invoker bean 名失配，启用即每次 fire 失败（C4-003） |
| W4 | wf-due/remind-task-scan.job.yaml 装配 | wf-scheduler resources _vfs/nop/job/conf/*.job.yaml:7-9 | 同 W3 | — | — | — | — | — | **失效**（C4-003，F3） |
| T1 | TaskFlowManagerImpl.getScheduledExecutor | task-core/impl/TaskFlowManagerImpl.java:76-81 | GlobalExecutors.globalTimer（ScheduledThreadPoolExecutor，实际 core=4，daemon） | DelayedWorkQueue 无界 | 关闭后 REE | `nop-global-timer-N` | **全平台共享，无隔离声明** | 进程级 @GlobalInstance 单例，无模块级关闭（平台模型） | 部分缺陷：retry 延迟路径把步骤体压入此池（C4-001）；容量旋钮无效为平台实现（残留边界） |
| T2 | TaskFlowManagerImpl.getThreadPoolExecutor | task-core/impl/TaskFlowManagerImpl.java:83-89 | registry 三保留名优先，否则 IoC bean | 视解析结果 | 视解析结果 | 视解析结果 | 无隔离/嵌套守卫 | 注册表池无关闭调用（进程级） | **边界缺陷**：注册表名遮蔽 + 无类型检查强转（C4-004） |
| T3 | ExecutorTaskStepWrapper.submit | task-core/step/ExecutorTaskStepWrapper.java:29-69 | T2 解析结果（典型 nop-global-worker 4/30 或自定义 4/20/10000） | TaskQueue 10000（先扩到 max 再入队） | Abort：REE 同步抛回步骤执行路径，响亮失败 | 池名前缀 | 同池可重入嵌套无守卫 | 取消经 bindCancelToken future.cancel(false)（非中断） | **缺陷**：sync+同池嵌套无治理（C4-002）；非中断取消为已声明幂等边界的配套，不立案 |
| T4 | TaskStepHelper.timeout 守卫 | task-core/utils/TaskStepHelper.java:197-243 | T1 globalTimer | 无界 | 同 T1 | 同 T1 | 共享 | 同步/异步/异常三路径均 timer.cancel(false)，清理完整 | 通过（清理对称，F8 正例） |
| T5 | TaskStepHelper.retry 延迟执行 | task-core/utils/TaskStepHelper.java:262-284 | T1 globalTimer | 无界 | 同 T1 | 同 T1 | 共享 | 异常经 completeExceptionally + LOG.error（ExecutorHelper 封装，F9 不吞） | **缺陷**：步骤体跑在共享定时器线程（C4-001） |
| T6 | DelayTaskStep | task-core/step/DelayTaskStep.java:39-49 | T1 globalTimer | 无界 | 同 T1 | 同 T1 | 共享 | no-op 占位 + bindCancelToken 撤单 | 通过 |
| T7 | Throttle/RateLimit wrapper | task-core/step/ThrottleTaskStepWrapper.java:44-46、RateLimitTaskStepWrapper.java:33-35 | 无池；调用线程信号量/令牌有界等待（maxWait） | N/A | 超限抛 ERR_TASK_THROTTLE_TIMEOUT/ERR_TASK_REQUEST_RATE_EXCEED_LIMIT | N/A | global 时跨任务共享（owner doc 已载明） | release 在 whenComplete/catch 双路径对称 | 通过（有界阻塞，P3 级观测提示见覆盖度） |
| T8 | RunOnContextTaskStepWrapper | task-core/step/RunOnContextTaskStepWrapper.java:20-33 | IContext 分发通道 | 视 context 实现 | 视实现 | 视实现 | 上下文串行化（javadoc 已声明） | 随 context 生命周期 | 通过 |
| T9 | nopTaskExecutionQueue bean | task-core resources _vfs/nop/task/beans/task-defaults.beans.xml:8-15 | 自建 DefaultThreadPoolExecutor（4/20/10000，daemon=false） | TaskQueue 10000 | Abort | `nop-task-execution-queue-N` | 独立 | doStart/doStop 对称（DefaultTaskExecutionQueue.java:136-156） | **死面**：零消费者（C4-005，F3） |
| T10 | ParallelTaskStep/AbstractForkTaskStep/GraphTaskStep | task-core/step/*.java | 无池（promise 聚合内联） | N/A | N/A | N/A | 内联串行 | N/A | 通过（owner doc 已载明边界） |

---

## 发现条目

### [nop-wf-nop-task-C4-001]

- **字段1 编号/标题**：nop-wf-nop-task-C4-001 retry 延迟重试把步骤体直接调度到全平台共享 globalTimer 线程上执行（错池使用）
- **字段2 严重度**：P1（重试路径 + 负载上升必然池耗尽，且爆炸半径为全平台定时器）
- **字段3 缺陷族**：C4 池治理（任务-池隔离错配）；伴生 F8 观测缺失（定时器积压无度量）
- **字段4 簇/模块**：nop-task / nop-task-core（使用侧）；宿主池为平台 GlobalExecutors.globalTimer
- **字段5 位置（文件:行）**：
  - `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:262-284`（延迟重试路径）
  - `nop-task/nop-task-core/src/main/java/io/nop/task/step/RetryTaskStepWrapper.java:27-30`（action = 步骤体）
  - `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:76-81`（scheduledExecutor 恒为 globalTimer）
- **字段6 证据片段**：

  ```java
  // TaskStepHelper.java:268-270 —— action（= 步骤体）被 schedule 到共享定时器，延迟后在其线程上执行
  return TaskStepReturn.of(null, FutureHelper.thenCompleteAsync(
          stepRt.getTaskRuntime().getScheduledExecutor()
                  .schedule(action, delay, TimeUnit.MILLISECONDS),
          (TaskStepReturn result, Throwable err) -> {
  // RetryTaskStepWrapper.java:28-29 —— action 的实体就是被包装步骤的 execute
  return TaskStepHelper.retry(getLocation(), stepRt, retryPolicy,
          () -> getTaskStep().execute(stepRt));
  // TaskFlowManagerImpl.java:77-81 —— 未注入自定义实现，恒回退平台共享定时器
  public IScheduledExecutor getScheduledExecutor() {
      if (scheduledExecutor == null)
          return GlobalExecutors.globalTimer();
      return scheduledExecutor;
  }
  ```

- **字段7 触发时序**：①任务模型 step S 配置 `<retry>`（maxRetryCount>0 且 retryDelay>0，owner doc 载明为一等增强），步骤体 B 含慢操作（如 invoke 外部服务 30s、rule:Execute 大数据量计算）。②首轮执行失败，进入 TaskStepHelper.retry 延迟分支，`schedule(action, 30s)` 注册到 globalTimer。③30s 后 B 在 globalTimer 线程上运行整个业务时长（DB/HTTP 阻塞），期间重试判定 handler（`thenCompleteAsync` 回调，FutureHelper.java:334-352 在完成线程执行）也占同一 timer 线程。④当 ≥4 个此类长体重试并发（globalTimer 实际线程数 = corePoolSize 缺省 4，见 ExecutorHelper.java:65），4 个 timer 线程全部被业务体占死。⑤此时全平台所有依赖 globalTimer 的调度——其他任务的超时守卫（TaskStepHelper.java:199-204）、`<delay>` 步骤（DelayTaskStep.java:41-43）乃至 nop-job coordinator 的 globalTimer 回退路径——全部排队延迟，步骤超时兜底在超时后数十秒才触发，`<delay>` 漂移；负载继续上升则单调恶化，且**无拒绝、无报错、无池积压度量**，静默降级。
- **字段8 影响与爆炸半径**：不限于 nop-task——globalTimer 是进程级共享设施；task 重试体可使其饿死并拖垮全平台定时语义（超时守卫失效意味着 C4 依赖的「超时终结不变量」在实际负载下不再有界）。
- **字段9 与 owner doc/已声明边界的关系**：owner doc `nop-task.md` 载明 retry/timeout 为每步一等增强，未声明「重试体将在共享定时器线程执行」；并行语义声明只约束 parallel/fork，与本发现正交。非已声明边界。
- **字段10 修复方向建议**：延迟到期后经 `GlobalExecutors.globalWorker()` 或步骤显式 executor 再执行 action（schedule 仅做定时唤醒）；或为 retry 体提供独立执行池并在 `ITaskRuntime.getScheduledExecutor` 之外新增 `getRetryExecutor` 语义；同时为 globalTimer 增加队列深度度量。
- **字段11 误报校准复核**：非低频路径（retry-delay 为文档化一等特性）；非有界声明池（共享定时器容量固定 4）；非平台设施自身实现缺陷（使用侧错池是本案对象；平台侧「maxPoolSize 旋钮无效」另列残留边界移交平台单元）；非测试代码。
- **字段12 置信度**：高（代码路径逐行核实，schedule 的 runnable 即步骤体无歧义；实际线程数=corePoolSize=4 已从 ExecutorHelper/ThreadPoolConfig 双向验证）。
- **字段13 关联/交叉引用**：与 C1 维度（TaskStepHelper 状态）、C3 维度异步链共享文件；影响面陈述依赖平台单元对 globalTimer 的实现审计结论。
- **复核状态**：已保留（P1。从严复核属实：延迟分支 `schedule(action, delay)`（TaskStepHelper:268-270，引文与 live 一致）的 action 即 RetryTaskStepWrapper:28-30 的被包装步骤体；nopTaskFlowManager bean 未装配 scheduledExecutor（task-defaults.beans.xml:5-6 无该 property）故恒回退 globalTimer（TaskFlowManagerImpl:76-81）；globalTimer 实际线程数=corePoolSize 缺省 4（ThreadPoolConfig.java:17 + ExecutorHelper.newScheduledExecutor:62-65 显式忽略 maxPoolSize + DefaultScheduledExecutor.init:80）核实；共享面核实——timeout 守卫（TaskStepHelper:199-204）、delay 步骤（DelayTaskStep:41-43）、nop-job RpcPollTaskManager:172 与 AbstractBatchScanner:135 的 globalTimer 回退/定时均同池，DelayedWorkQueue 无界无拒绝=静默降级成立。现状：新发现——WI 1.1–1.4 无同站点登记（WI 1.4 nop-job 仅立案其自身 fallback/scanner 面），2026-09-25 nop-task 质量审计无此记录，历史修复 e6010dc6eb 只改延迟重试双执行缺陷未改池归属，无相关修复提交）

### [nop-wf-nop-task-C4-002]

- **字段1 编号/标题**：nop-wf-nop-task-C4-002 `sync=true` + `executor` 同池嵌套无任何治理守卫——有界池可被纯模型配置置入永久饥饿死锁（C3-002 死锁候选的池治理切面）
- **字段2 严重度**：P1（负载上升必然池耗尽；配置可达死锁，非缺省主路径故不评 P0）
- **字段3 缺陷族**：C4 池治理（隔离缺失/嵌套可重入无守卫）；交叉引用 C3-002
- **字段4 簇/模块**：nop-task / nop-task-core
- **字段5 位置（文件:行）**：
  - `nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:118-120`（executor 包装）、`:159-160`（sync 包装在 executor **之外**）
  - `nop-task/nop-task-core/src/main/java/io/nop/task/step/SyncTaskStepWrapper.java:17-19`（阻塞 sync）
  - `nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java:207-211`（sync → FutureHelper.syncGet 阻塞）
  - `nop-task/nop-task-core/src/main/java/io/nop/task/step/ExecutorTaskStepWrapper.java:29-33`（按步解析 executorBean 并提交，无嵌套检测）
- **字段6 证据片段**：

  ```java
  // TaskStepEnhancer.java:118-120 —— executor 包装（靠内层）
  if (!StringHelper.isEmpty(stepModel.getExecutor())) {
      step = new ExecutorTaskStepWrapper(step, stepModel.getExecutor());
  }
  // TaskStepEnhancer.java:159-160 —— sync 包装（靠外层）→ 调用线程阻塞等待池上结果
  if (stepModel.isSync())
      step = new SyncTaskStepWrapper(step);
  // SyncTaskStepWrapper.java:17-19
  public TaskStepReturn execute(ITaskStepRuntime stepRt) {
      return getTaskStep().execute(stepRt).sync();   // FutureHelper.syncGet：无时限阻塞
  }
  // ExecutorTaskStepWrapper.java:30-33 —— 每步独立解析同名 executorBean，无同池重入检测
  IThreadPoolExecutor executor = stepRt.getTaskRuntime().getThreadPoolExecutor(executorBean);
  CompletableFuture<TaskStepReturn> ret = new CompletableFuture<>();
  CompletableFuture<?> future = executor.submit(() -> { ... });
  ```

- **字段7 触发时序**：①模型配置两级嵌套步骤均声明 `executor="nop-global-worker"`（或任一有界自定义池）且 `sync="true"`。②外层步骤被提交到池 E（如经 parallel 父步骤的子步骤 executor 配置），E 的 max=30（`nop.commons.concurrent.global-worker.maxPoolSize` 缺省 30）个线程各运行一个外层体。③每个外层体执行到内层 `sync+executor=E` 步骤时，SyncTaskStepWrapper 在 E 线程上 syncGet 阻塞，等待自己刚刚提交到 E 队列的内层任务。④内层任务在 TaskQueue（容量 10000，ExecutorHelper.java:85-88）中排队——**队列永远不满 → 不触发 Abort 拒绝、无任何错误**；30 个 E 线程全部阻塞在 syncGet，内层任务永无线程可用。⑤永久死锁：30 个线程 + 队列中的任务互等，无超时、无日志、无池耗尽告警；若 E 为自定义 4/20 池则 20 个并发嵌套即死锁同型。
- **字段8 影响与爆炸半径**：E=`nop-global-worker` 时为**平台级**工作线程池整体挂死（其他模块经 globalWorker 分发的任务全部饿死）；自定义池时为该业务池挂死且上游 syncGet 调用线程（含 GraphQL 线程）连锁耗尽。
- **字段9 与 owner doc/已声明边界的关系**：owner doc 只声明「子步骤显式配置 executor 才真正并行」（并行加速语义），**未声明** sync+executor 组合的同池嵌套风险，也无任何文档警示 executor bean 的隔离要求——不属于已载明契约豁免。
- **字段10 修复方向建议**：①构建期（TaskStepEnhancer）或运行期（ExecutorTaskStepWrapper 提交前）检测「当前线程已在同一 executorBean 上执行」并 fail-fast（`ERR_TASK_EXECUTOR_SELF_NESTING`）；②或对 sync+executor 组合强制要求 syncGet 带时限；③文档在 executor 属性处声明「不得与 sync 同用于可能同池嵌套的层级」。
- **字段11 误报校准复核**：触发仅需声明式模型配置，无需代码缺陷叠加；队列 10000 + TaskQueue.force 语义使拒绝安全网必然失效（这正是池治理视角的核心：有界队列在饥饿死锁场景不产生拒绝信号）；非测试代码。理论异议「需要 ≥max 个并发嵌套」——该条件即“负载上升必然耗尽”定义本身，且死锁一旦形成不可自愈。
- **字段12 置信度**：高（包装顺序、阻塞点、队列永不拒绝均已逐行核实）。
- **字段13 关联/交叉引用**：C3-002（同一配置面在异步链维度的立案）；本条为 C4 池治理切面独立成案。
- **复核状态**：已保留（P1，与 C3-002 保留 P1 同向裁定。从严复核属实：包装序 executor（TaskStepEnhancer:118-120）在内、sync（:159-160）在外、SyncTaskStepWrapper:17→TaskStepReturn.sync():208-212→FutureHelper.syncGet 无超时阻塞、ExecutorTaskStepWrapper:30-33 同池重入无任何检测；TaskQueue offer 先扩到 max 再入队（StandardThreadPoolExecutor/TaskQueue:69-83 grow-then-queue 语义）核实——饱和嵌套时内层任务滞留 10000 容量队列永不触发拒绝，死锁无自愈出口成立。对账：与 C3-002 为同点不同视角，两复核同向保留 P1 自洽，7.1 归并处置。现状：新发现——同批 C3-002 已并行立案（同一审计批次非历史），无更早同点记录）

### [nop-wf-nop-task-C4-003]

- **字段1 编号/标题**：nop-wf-nop-task-C4-003 wf 到期/提醒扫描 job.yaml 的 invoker bean 名与实际 bean id 失配——扫描调度装配为失效面（F3），启用即每次触发失败
- **字段2 严重度**：P2（缺省 disabled 的边界路径；但一旦按配置启用，功能 100% 失效且无自愈）
- **字段3 缺陷族**：F3 失效面（配置装配死面）；伴生可观测性（有错误日志但语义是“bean 不存在”而非“名字配错”）
- **字段4 簇/模块**：nop-wf / nop-wf-scheduler（装配）；nop-job 为宿主设施
- **字段5 位置（文件:行）**：
  - `nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/job/conf/wf-due-task-scan.job.yaml:7-9`
  - `nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/job/conf/wf-remind-task-scan.job.yaml:7-9`
  - `nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/wf/beans/app-scheduler.beans.xml:9-13`（实际 bean id）
  - `nop-job/nop-job-local/src/main/java/io/nop/job/local/executor/BeanMethodJobInvoker.java:199-206`（触发期抛错点）
- **字段6 证据片段**：

  ```yaml
  # wf-due-task-scan.job.yaml:7-9（wf-remind-task-scan.job.yaml 同）
  invoker:
    bean: wfTaskScanner      # ← 全仓库不存在此 bean id
    method: scanDueTasks
  ```
  ```xml
  <!-- app-scheduler.beans.xml:9-13 —— 实际注册名是 nopWfTaskScanner -->
  <bean id="nopWfTaskScanner" class="io.nop.wf.scheduler.WfTaskScanner">
  ```
  ```java
  // BeanMethodJobInvoker.java:199-205 —— 每次 fire 都走这里失败
  private Object requireBean(String beanName) {
      Object bean = BeanContainer.tryGetBean(beanName);
      if (bean == null) {
          throw new NopException(ERR_JOB_BEAN_NOT_FOUND).param(ARG_BEAN_NAME, beanName);
      }
      return bean;
  }
  ```

- **字段7 触发时序**：①运维按配置开启 `nop.job.wf-due-task-scan.enabled=true`（超时升级功能的唯一驱动路径）。②job 注册成功（invoker 类型 `beanMethod` 经 `nopJobInvoker_beanMethod` 解析通过，注册期不校验业务 bean 名）。③每分钟 cron 触发 → `requireBean("wfTaskScanner")` → tryGetBean 返回 null → 抛 ERR_JOB_BEAN_NOT_FOUND → `invokeAsync.exceptionally` 记 `nop.job.bean-method-invoke-failed` error 日志并返回 JobFireResult.ERROR。④扫描永不执行，步骤 dueAction/超时升级、提醒监听全部静默失效（仅错误日志可见）；每分钟重复，无 fail-fast、无告警聚合。
- **字段8 影响与爆炸半径**：nop-wf 的“超时升级”与“提醒”能力在启用配置后完全不可用；由于 job 注册期不校验，缺陷可长期潜伏。
- **字段9 与 owner doc/已声明边界的关系**：owner doc `nop-wf.md` 列出「超时升级」为核心能力，未声明该能力依赖手动启用且装配已损坏；不属已载明边界。
- **字段10 修复方向建议**：job.yaml 的 `invoker.bean` 改为 `nopWfTaskScanner`；并在 LocalJobConfigLoader 注册期对 beanMethod invoker 预校验目标 bean 存在性（fail-fast 前移）。
- **字段11 误报校准复核**：非平台设施自身实现（job.yaml 属本单元装配面）；`BeanContainerInvokerResolver` 的 `nopJobInvoker_` 前缀只作用于 invoker 类型名（"beanMethod"），不作用于业务 bean 名——已排除“前缀拼接兜底”的可能误判；双 yaml 同错排除单点手误。
- **字段12 置信度**：高（全仓库 grep `wfTaskScanner` 仅命中两个 job.yaml；bean id 唯一）。
- **字段13 关联/交叉引用**：F3 失效面枚举主项；与 WfTaskScanner 本身的异常隔离逻辑（scanDueTasksInCurrentSession 的 per-item catch，质量良好）正交。
- **复核状态**：已保留（P2。从严复核属实：两 job.yaml:8 `invoker.bean: wfTaskScanner` 与实际 bean id=`nopWfTaskScanner`（app-scheduler.beans.xml:10）失配，全仓库 grep `wfTaskScanner` 仅命中两 yaml；requireBean（BeanMethodJobInvoker:199-206）tryGetBean null 即抛 ERR_JOB_BEAN_NOT_FOUND，且在 invokeAsync 方法体 :61 future 构造前同步抛出；`nopJobInvoker_` 前缀（BeanContainerInvokerResolver:12）只作用于 invoker 类型名不作用于业务 bean 名——"前缀兜底"误判已排除属实；注册期不校验业务 bean 名成立。判级与历史立案同级，无升级/降级事由。现状：已知未修复——同发现已于 2026-09-22 code-review-full 立案（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.1-wf-deep-audit.md` P2-3.1-001/AU-01/SM-10，P2 同级），job.yaml 至今未改，无修复提交；初审盲审未读历史审计，编号对齐由本复核补记）

### [nop-wf-nop-task-C4-004]

- **字段1 编号/标题**：nop-wf-nop-task-C4-004 executor bean 解析链存在注册表名遮蔽与无类型检查强转——错误池/错误类型的失败方式是静默错池或无上下文 CCE
- **字段2 严重度**：P2（边界配置缺陷/可观测性缺陷；非主路径必然）
- **字段3 缺陷族**：C4 池治理（解析治理）；伴生 F3 边缘（保留名占用面未声明）
- **字段4 簇/模块**：nop-task / nop-task-core（解析在 task-core，注册表在平台 nop-commons）
- **字段5 位置（文件:行）**：
  - `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:83-89`
  - `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:51-62`
- **字段6 证据片段**：

  ```java
  // TaskFlowManagerImpl.java:84-89
  public IThreadPoolExecutor getThreadPoolExecutor(IBeanProvider beanProvider, String executorBean) {
      IThreadPoolExecutor executor = GlobalExecutors.getExecutor(executorBean);
      if (executor == null)
          executor = (IThreadPoolExecutor) beanProvider.getBean(executorBean);  // 无 instanceof 检查
      return executor;
  }
  // GlobalExecutors.java:51-59 —— 三个保留名优先于任何同名 IoC bean
  public static IThreadPoolExecutor getExecutor(String name) {
      IThreadPoolExecutor executor = g_executors.get(name);
      if (executor == null) {
          if (NOP_GLOBAL_TIMER.equals(name)) return globalTimer();
          if (NOP_CACHED_THREAD_POOL.equals(name)) return cachedThreadPool();
          if (NOP_GLOBAL_WORKER.equals(name)) return globalWorker();
      }
      return executor;
  }
  ```

- **字段7 触发时序（两种配置错误的失败方式）**：①**遮蔽**：部署方定义了名为 `nop-global-worker`（或 `nop-global-timer`/`nop-cached-thread-pool`）的定制 IThreadPoolExecutor bean，意图让 task 步骤用专属池——getExecutor 先命中平台注册表/保留名回退，定制 bean 永不可达，步骤体实际跑在平台共享池上，且线程名（`nop-global-worker-N`）与意图一致，线程转储诊断完全无法发现错池。②**错类型**：executorBean 指向一个存在但非 IThreadPoolExecutor 的 bean → getBean 成功返回后 `(IThreadPoolExecutor)` 强转在步骤首次执行时抛裸 ClassCastException，无 bean 名/模型位置的错误上下文（相比缺 bean 时 getBean 抛的 ERR_IOC_BEAN_NOT_FOUND 有明确上下文）。
- **字段8 影响与爆炸半径**：错误配置的失败不响亮：错池是静默的（隔离意图失效，与 C4-002 的同池嵌套风险叠加），错类型是迟到的、低质的。
- **字段9 与 owner doc/已声明边界的关系**：owner doc 未声明这三个保留名是 executor 属性的不可占用名，也未声明 executor bean 的类型契约；非已载明豁免。
- **字段10 修复方向建议**：解析链加 instanceof 检查并抛带 executorBean/实际类型/模型位置的 NopException；对保留名命中时若容器存在同名 bean 则 WARN 提示遮蔽；文档登记保留名清单。
- **字段11 误报校准复核**：解析代码属本单元（TaskFlowManagerImpl），非平台设施内部；缺 bean 路径已有 fail-fast（getBean 抛错）故未把“缺 bean”立案。
- **字段12 置信度**：高（解析顺序与强转逐行核实）。
- **字段13 关联/交叉引用**：C4-002（错池静默放大嵌套死锁不可见性）；平台 GlobalExecutors 内部实现不在本案范围。
- **复核状态**：已保留（P2。从严复核属实：GlobalExecutors.getExecutor（:51-62）注册表 g_executors 命中优先、未命中再走三保留名回退——部署方同名 IoC bean 被遮蔽不可达且线程名与意图一致（线程转储无法发现错池）；TaskFlowManagerImpl:87 `(IThreadPoolExecutor) beanProvider.getBean(executorBean)` 无 instanceof 检查，错类型在步骤首次执行时抛裸 CCE 无 bean 名/模型位置上下文；缺 bean 路径已有 fail-fast（ERR_IOC_BEAN_NOT_FOUND）故未重复立案的校准属实。解析代码属本单元（TaskFlowManagerImpl）非平台内部的定性成立。现状：新发现——历史审计与本 campaign WI 1.1–1.4 均无此解析链记录）

### [nop-wf-nop-task-C4-005]

- **字段1 编号/标题**：nop-wf-nop-task-C4-005 `nopTaskExecutionQueue` bean 为声明态死面——专属 ThreadPoolConfig 随每应用装配但全仓库零消费者（F3）
- **字段2 严重度**：P3（无运行时危害的清理候选；属 F3 失效面枚举必填项）
- **字段3 缺陷族**：F3 失效面（死配置）
- **字段4 簇/模块**：nop-task / nop-task-core（beans 装配）
- **字段5 位置（文件:行）**：
  - `nop-task/nop-task-core/src/main/resources/_vfs/nop/task/beans/task-defaults.beans.xml:8-15`
  - `nop-kernel/nop-core/src/main/java/io/nop/core/execution/DefaultTaskExecutionQueue.java:136-156`（doStart/doStop）
- **字段6 证据片段**：

  ```xml
  <!-- task-defaults.beans.xml:8-15 —— 每个引入 nop-task-core 的应用都会实例化 -->
  <bean id="nopTaskExecutionQueue" class="io.nop.core.execution.DefaultTaskExecutionQueue">
      <property name="threadPoolConfig">
          <bean class="io.nop.commons.concurrent.executor.ThreadPoolConfig"
                ioc:config-prefix="nop.task.execution-queue.executor">
              <property name="name" value="nop-task-execution-queue"/>
          </bean>
      </property>
  </bean>
  ```
  ```java
  // DefaultTaskExecutionQueue.java:140-147 —— 池只在 start() 后创建；全仓库无任何 start/addTaskIfAbsent 调用方
  if (executor == null) {
      if (threadPoolConfig == null) {
          this.executor = GlobalExecutors.globalWorker();
      } else {
          ownExecutor = true;
          this.executor = DefaultThreadPoolExecutor.newExecutor(threadPoolConfig);
      }
  }
  ```

- **字段7 触发时序**：非并发缺陷。全仓库 grep（Java+XML，含 `_dump` 排除）确认 `nopTaskExecutionQueue`/`ITaskExecutionQueue` 除 bean 声明与 nop-core 自身实现/测试外零引用：无注入点、无 getBean 调用、无 start() 调用。bean 以“永不启动的空壳”形态存在于每个 task 应用上下文中；`nop.task.execution-queue.executor.*` 配置面无任何效果。
- **字段8 影响与爆炸半径**：无直接资源占用（LifeCycleSupport 未 start 则不建池、不建线程）；危害为误导性配置面与维护噪音——若未来有人在 start 前调用 `addTaskIfAbsent`，`executor.execute` 处 NPE（DefaultTaskExecutionQueue.java:206，queueTask 无 active 校验）。
- **字段9 与 owner doc/已声明边界的关系**：owner doc 子模块表与功能清单均未提及任务执行队列；非声明契约。
- **字段10 修复方向建议**：删除该 bean 或接上真实消费者（如把 task 异步执行统一收敛到该队列获得 stats/取消能力）；若保留，在 queueTask 前置 active 校验。
- **字段11 误报校准复核**：确认按“配置有界（4/20/10000）+ 关闭对称（doStart/doStop 配对 destroy）”本可判通过；立案依据是零消费即 F3 失效面，而非池参数问题。
- **字段12 置信度**：高。
- **字段13 关联/交叉引用**：C4-006（同簇另一 F3 死面）。
- **复核状态**：已保留（P3。从严复核属实：task-defaults.beans.xml:8-15 每应用装配、全仓库 Java+XML grep `nopTaskExecutionQueue|ITaskExecutionQueue` 仅命中 bean 声明与 nop-core 自身实现、无注入点/getBean/start 调用方；nop-ioc 容器无 ILifeCycle bean 自动启动机制（grep `instanceof ILifeCycle` 零命中）——未 start 不建池不建线程的"无直接资源占用"定性成立。死面登记 P3 恰当。现状：新发现）

### [nop-wf-nop-task-C4-006]

- **字段1 编号/标题**：nop-wf-nop-task-C4-006 `nop-task-queue` 子模块仅含空占位接口 ITaskQueue——零实现、零使用、零语义的模块级失效面（F3）
- **字段2 严重度**：P3（清理候选；F3 枚举完整性）
- **字段3 缺陷族**：F3 失效面（死接口/死模块）
- **字段4 簇/模块**：nop-task / nop-task-queue
- **字段5 位置（文件:行）**：`nop-task/nop-task-queue/src/main/java/io/nop/task/queue/ITaskQueue.java:1-5`（该模块 main 源码唯一文件）
- **字段6 证据片段**：

  ```java
  // ITaskQueue.java 全文（该子模块唯一 main 源文件）
  package io.nop.task.queue;

  public interface ITaskQueue {
      void enqueueTask();
  }
  ```

- **字段7 触发时序**：非并发缺陷。全仓库 grep `ITaskQueue` 仅命中本文件：无实现类、无注入、无方法调用。模块 jar 被聚合 pom 打包发布但承载零功能。
- **字段8 影响与爆炸半径**：无运行时影响；构建/发布面与文档导航面噪音（未来审计/开发会反复误入排查）。
- **字段9 与 owner doc/已声明边界的关系**：owner doc 子模块表中未列 `nop-task-queue`（列了 queue 支持以外的 8 个子模块），与其“无功能”现状一致，但模块本体仍是声明外的残留物。
- **字段10 修复方向建议**：删除模块或落地其声明语义（如对接 C4-005 的 DefaultTaskExecutionQueue）。
- **字段11 误报校准复核**：确认非测试代码、非平台设施；纯失效面登记。
- **字段12 置信度**：高。
- **字段13 关联/交叉引用**：C4-005。
- **复核状态**：已保留（P3。从严复核属实：ITaskQueue.java 全文 5 行空占位接口、为 nop-task-queue 模块 main 唯一源文件（find 核实）、全仓库 grep `ITaskQueue` 仅命中本文件零实现零注入零调用。模块级失效面登记 P3 恰当。现状：新发现）

---

## 覆盖度与残留边界

### 覆盖声明

- **wf 簇**：对 10 个子模块 main 源码做了全量执行器/线程/定时器 grep（GlobalExecutors、globalTimer、IThreadPoolExecutor、ThreadPoolConfig、ScheduledExecutor、ExecutorService、Executors.、FutureHelper、syncGet、schedule、Timer），逐命中文件精读；`_gen/` 与 `_` 前缀生成物排除。wf 簇确认零自建池、零直接平台池引用；三个真实使用点（W1/W2/W3）全部核到实现级。
- **task 簇**：同样全量 grep 后精读全部 11 个使用点（T1-T10）及宿主平台类（GlobalExecutors/DefaultScheduledExecutor/ExecutorHelper/ThreadPoolConfig/TaskQueue/StandardThreadPoolExecutor/DefaultThreadPoolExecutor/NamedThreadFactory/FutureHelper 关键方法），池参数、队列、拒绝、命名、取消、清理逐项核对。
- **专项核对结果（通过项）**：
  1. **IWorkflowExecutor 声明与实现一致性**：javadoc 声明「同 wfId 并发调用不做互斥……写竞争由 ORM 乐观锁拦截」；运行时守卫核实——`nop-wf/model/nop-wf.orm.xml:181`（nop_wf_instance）与 `:353`（nop_wf_step_instance）均声明 `versionProp="version"`，乐观锁背书真实存在；DefaultWorkflowExecutor 确无互斥，与声明一致。**判定：声明即契约且已被实现背书，不立案。**
  2. **TaskStepHelper.timeout 守卫清理**：同步完成/同步抛错/异步竞速三路径均 `timer.cancel(false)`，无定时器泄漏路径（F8 正例）。
  3. **WfTaskScanner 异常隔离（F9）**：per-item 双层 catch（NopException 特判竞态跳过 + Exception 兜底 LOG.error），扫描批不因单条失败中断；findDueActivatedSteps 级异常经 BeanMethodJobInvoker 包装为 ERROR 并保持后续调度——无吞异常致死调度停止的路径。
  4. **ExecutorTaskStepWrapper 取消兜底**：onCancel 强制终结返回 promise + bindCancelToken 双保险，removeOnCancel 在 whenComplete 对称注销。
  5. **ExecutorHelper.submit/schedule 异常与上下文**：任务体异常 LOG.error + completeExceptionally（不吞），finally detachContext。
- **残留边界（不立案，登记备查）**：
  1. **平台容量旋钮无效（移交平台审计单元）**：`GlobalExecutors.globalTimer()` 设置 `maxPoolSize=nop.commons.concurrent.global-timer.maxPoolSize`（缺省 5），但 `ExecutorHelper.newScheduledExecutor`（ExecutorHelper.java:62-65）按 corePoolSize（缺省 4）建池并显式注释 maxPoolSize 未被使用——该配置项对 globalTimer 实际无效。属平台设施自身实现（误报校准豁免），本单元仅作为 C4-001 影响评估依据（全局定时器实际 4 线程且无有效扩容旋钮）。
  2. **GlobalExecutors 三池无运行时关闭**：globalTimer/globalWorker/cachedThreadPool 为 @GlobalInstance 进程级单例，daemon 线程，无模块 stop 钩子——平台生命周期模型，非两模块缺陷（F8 视角下两模块无自有池需要对称关闭；nop-task 唯一自有池 T9 为死 bean 未启动）。
  3. **cachedThreadPool 无界线程**（maxPoolSize=-1、queue 0）：平台提供的声明式无界设施；task 模型 executor 属性若显式引用需自担风险，本单元未发现在仓模型引用（`_dump` 外无 task.xml 使用 executor 指向它）。
  4. **WfTaskScanner 再入语义**：扫描自身无再入守卫，依赖 nop-job `job.running`/WAITING 状态跳过重叠火花（LocalJobScheduler.java:305-311、330-341）——扫描超 1 分钟时后续 cron 火花被跳过（不重叠、不排队），为宿主设施已实现语义，登记为声明边界。
  5. **Throttle/RateLimit maxWait 阻塞**：调用线程有界阻塞（超限响亮抛错）；在池线程上下文中大量等待会占用池线程，但受 maxWait 与信号量容量双重有界，P3 观测提示不立案。
  6. **cancel(false) 非中断**：ExecutorTaskStepWrapper 取消后池内步骤体继续运行至自然完成——owner doc 已载明「步骤业务副作用与终态保存之间无事务原子性，步骤体必须幂等」，属已声明边界配套语义，不立案。
  7. **WorkflowServiceImpl.syncGet**：缺省内联 executor 下无真实阻塞；替换为异步 executor 时的顺序阻塞是有界的（逐项 await）。未构成 P2 以上形态，登记边界。
- **维度集外线索**：本轮 C4 阅读面（执行器装配、步骤包装链、job 装配、平台池封装调用侧）内**未发现** C6（资源泄漏）/C9（热路径）形态的 P0/P1 级线索，`### [维度集外线索-CL-0NN]` 条目为空；C6/C9 结论以对应维度审计为准。
- **F3 失效面枚举（禁留空）**：C4-003（wf 扫描 job 装配死面）、C4-005（nopTaskExecutionQueue 死 bean）、C4-006（ITaskQueue 死接口）——共 3 项，非空。

---

## 维度复核结论（独立复核子代理）

- 复核子代理：C4 维度独立复核（与初审不同角色、不同任务），复核日期 2026-10-15。
- 仓库：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）。
- 复核输入：`C4-thread-pools.md`（C4-001~006）、`C4-round2.md`（C4-007~009）、`C4-round3.md`（0 新增 + 终止声明）全部完整读取；逐条发现重新读 live code 验证文件/行号/证据片段/池治理与生命周期推演/判级口径。三个 P1（C4-001/002/008）与 bean 名装配（C4-003）从严验证。
- 对账输入：同批 C3 复核结论（C3-async-chains.md 复核节：C3-002 保留 P1、C3-013 保留 P2 并声明 retry 半边与 C4-001/C4-008 同池同站）；同 campaign WI 1.4 nop-job 审计（nop-job-C7-006/C7-007）；历史审计现状检索（2026-09-22 code-review-full 3.1-wf-deep-audit、2026-09-25 nop-task-quality、check/check2、security-audit WF-03）；git log 修复提交核对。
- 判定纪律：对每条以"被审计方能用什么证据反驳"校准，反驳不了的才保留；未补充任何新发现。

### 逐条判定表（覆盖全部 9 条，含 round 文件条目）

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---------|-------|------|-----------|---------|
| C4-001 | P1 | **保留** | schedule(action)=步骤体上 globalTimer 全链核实，实际 4 线程共享定时器、DelayedWorkQueue 无界无拒绝静默降级、timeout/delay/nop-job 回退同池爆炸半径成立 | 新发现 |
| C4-002 | P1 | **保留** | executor 内/sync 外包装序+无超时 syncGet+TaskQueue 先扩后队永不拒绝=饱和同池嵌套永久死锁；与 C3-002 保留 P1 同向自洽 | 新发现（与同批 C3-002 同点，7.1 归并） |
| C4-003 | P2 | **保留** | bean 名失配、触发期同步抛 ERR_JOB_BEAN_NOT_FOUND→FAILED 全核实，判级与历史立案同级 | 已知未修复（2026-09-22 code-review-full P2-3.1-001/AU-01/SM-10） |
| C4-004 | P2 | **保留** | 保留名遮蔽序+无 instanceof 强转逐行核实，P2 校准恰当 | 新发现 |
| C4-005 | P3 | **保留** | 零消费者+nop-ioc 无 ILifeCycle 自动启动（未 start 不建池）核实，死面登记 | 新发现 |
| C4-006 | P3 | **保留** | 5 行空接口、模块唯一 main 源文件、全仓零引用核实 | 新发现 |
| C4-007 | P2 | **保留** | 全部机制核实（field5 行号 ≤14 行漂移不影响成立）；形态 A 宿主机制=nop-job-C7-006 须 7.1 跨单元归并，形态 B 与使用面为本批新增 | 形态 A 已知未修复（WI 1.4 nop-job-C7-006，P2 open）；形态 B/使用面新发现 |
| C4-008 | P1 | **保留** | executor 包不住重试体+池线程释放点+saveState ORM 写上 globalTimer 全链核实；字段7⑤"延迟归零"子场景对内建 RetryPolicy 不可达，系表述瑕疵不动摇主体 | 新发现（与同批 C3-013 retry 半边同池同站，7.1 归并+判级统一） |
| C4-009 | P3 | **保留** | 撤单缺失 vs DelayTaskStep 对称正例、checkNotCancelled 分布、first-terminal-wins 守卫全核实 | 新发现 |

**计数：保留 9 / 降级 0 / 驳回 0。**

### 不确定项（显式列出）

1. **C4-002 "永久"死锁的前提**：以未配置 step timeout 为前提——timeout 包装位于 sync 内侧（TaskStepEnhancer.wrap :133-135 在 :159-160 之前）可解围（round2 排除项 #4 已自曝）；该模型级缓解未文档化且非缺省，不改变 P1 判定。
2. **C4-008 字段7⑤ 子场景不可达**：内建 `RetryPolicy.getRetryDelay`（RetryPolicy.java:158-186）对正 retryDelay 单调不减（指数/线性均增长、jitter 不归零），"指数退避后归零"路径不存在；retryDelay=0 时无延迟分支、循环留在进入线程（executor 池线程），隔离反而有效——⑤表述过宽，主体主张（延迟体+簿记恒上 globalTimer、executor 隔离无效）不依赖该子场景，已在条目复核状态行勘误。
3. **C4-007 field5 行号漂移**：LocalJobScheduler 引文行号相对 live 文件有 ≤14 行结构性偏移（resumeJob 实为 :161-174、fireNow 拒绝实为 :218-219、err→FAILED 实为 :352-355、ERROR→FAILED 实为 :358-362、cancelRunning 实为 :390-393），逐条实质证据已另行核实无误。
4. **C3-P2 vs C4-P1 判级分歧**：C3-013（P2）与 C4-001/C4-008（P1）为同池面不同视角的判级分歧，C3 复核与本复核均已声明留 7.1 合并统一——本复核维持 C4 侧 P1 的理由：C4-001/008 立案对象是本单元使用侧错池（步骤体+簿记 I/O 上共享定时器），非平台设施自身实现；C3-013 的 P2 自我限定于 delay 续延半边。非复核缺口，属 7.1 合并裁定事项。

### 与 C3 侧对账结论（自洽声明）

- C4-002（本复核保留 P1）与 C3-002（C3 复核保留 P1）同点不同视角，两复核同向裁定，自洽；7.1 归并为单案处置。
- C4-001/C4-008（本复核保留 P1）与 C3-013（C3 复核保留 P2）同池同站，判级分歧已双向声明留 7.1；本复核 C4 侧判定不因对账降级。

### 需子项复核的高风险项

**无。** 三个 P1（C4-001/002/008）均已由本复核直接从严验证（调度站点、包装序、阻塞原语、池队列 grow-then-queue 语义、saveState 持久化链逐项核实），无需再发起子项复核。移交 7.1 的归并事项（非复核缺口）：①C3-002↔C4-002 同点归并；②C3-013 retry 半边↔C4-001/C4-008 同池面归并并统一 P1/P2 判级；③C4-007 形态 A↔nop-job-C7-006 跨单元归并（含 C4-003 与 2026-09 P2-3.1-001 的同案对齐）。

---

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|-----|------------|---------|-----------|------|
| C4-001 | P1 | nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java（:268-270）；step/RetryTaskStepWrapper.java（:27-30）；impl/TaskFlowManagerImpl.java（:76-81） | retry 延迟重试把步骤体直接调度到全平台共享 globalTimer（实际 4 线程）上执行，负载上升即定时器饿死、全平台定时语义静默失准 | 新发现 |
| C4-002 | P1 | nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java（:118-120,:159-160）；step/SyncTaskStepWrapper.java（:17）；step/ExecutorTaskStepWrapper.java（:29-33） | sync=true+executor 同池嵌套无治理守卫，有界池饱和即全员永久互等（队列永不拒绝、无超时无告警）；与 C3-002 同点归并 | 新发现 |
| C4-003 | P2 | nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/job/conf/wf-due-task-scan.job.yaml、wf-remind-task-scan.job.yaml（:8） | invoker.bean=wfTaskScanner 与实际 bean id=nopWfTaskScanner 失配，启用即每次触发 ERR_JOB_BEAN_NOT_FOUND→FAILED 永久停摆 | 已知未修复（2026-09 P2-3.1-001/SM-10） |
| C4-004 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java（:83-89） | executor bean 解析链保留名遮蔽+无类型检查强转，错误配置静默错池或迟到 CCE | 新发现 |
| C4-005 | P3 | nop-task/nop-task-core/src/main/resources/_vfs/nop/task/beans/task-defaults.beans.xml（:8-15） | nopTaskExecutionQueue bean 零消费者的声明态死面（专属 ThreadPoolConfig 无效果） | 新发现 |
| C4-006 | P3 | nop-task/nop-task-queue/src/main/java/io/nop/task/queue/ITaskQueue.java（:1-5） | nop-task-queue 模块仅含 5 行空占位接口，零实现零使用 | 新发现 |
| C4-007 | P2 | nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java（:96）；nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java（:181-194） | wf 扫描 job 链路一次失败即 FAILED 永久停摆（无恢复 API）、一次挂死即 RUNNING 永久占位；扫描集无界+逐项内联无超时 | 形态 A 已知未修复（nop-job-C7-006）；形态 B/使用面新发现 |
| C4-008 | P1 | nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java（:114-120）；utils/TaskStepHelper.java（:268-284） | executor 隔离配置覆盖不了延迟重试路径：重试体+saveState 簿记 ORM 写恒定运行在共享 globalTimer 线程；与 C3-013 retry 半边同池同站归并 | 新发现 |
| C4-009 | P3 | nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java（:268-270,:249） | 取消/超时不能撤回已排程的延迟重试体（缺 bindCancelToken，与 DelayTaskStep 不对称），终态后仍完整执行一次副作用 | 新发现 |
