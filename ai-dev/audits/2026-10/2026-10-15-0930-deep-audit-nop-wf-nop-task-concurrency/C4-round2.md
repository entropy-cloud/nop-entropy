# C4 线程池与执行器治理维度 — nop-wf + nop-task 第 2 轮深挖（盲审）

- 审计单元：`nop-wf`（10 jar）+ `nop-task`（10 jar），合计 379 files / 36,107 行
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15；维度：C4 线程池与执行器治理（第 2 轮深挖）
- 盲审纪律：未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件（前序轮次文件 `C4-thread-pools.md` 除外）；未读取其他维度审计输出。全部结论基于 live code。
- 输入：第 1 轮发现 C4-001~006（已完整读取）；`_tmp/cra-baseline-nop-wf-nop-task.txt` C4/C3 节（两簇均为空，与第 1 轮复核一致）。
- 本轮编号从 **C4-007** 起（第 1 轮最大编号 C4-006 已确认）。

---

## 1. 本轮发现

### [nop-wf-nop-task-C4-007]

- **字段1 编号/标题**：nop-wf-nop-task-C4-007 wf 扫描 job 调度链路零失败韧性且执行体内联于共享调度池——一次 fire 失败即 FAILED 永久停摆（无恢复 API 可达），一次 fire 挂死即 RUNNING 永久占位；C4-003 同特性的 F3 失效面扩展
- **字段2 严重度**：P2（缺省 disabled 边界路径；但启用是"超时升级"特性的唯一预期生产形态，一旦启用，单次瞬态故障即永久静默失效，无自愈、无告警聚合）
- **字段3 缺陷族**：C4 池治理（调度池占用/隔离缺失）+ F3 失效面（装配韧性死面）；交叉引用 C4-003（同特性装配死面）
- **字段4 簇/模块**：nop-wf / nop-wf-scheduler（job.yaml 装配 + WfTaskScanner 执行体）；宿主设施 nop-job-local（LocalJobScheduler/BeanMethodJobInvoker）为本案的放大器而非立案对象
- **字段5 位置（文件:行）**：
  - `nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/job/conf/wf-due-task-scan.job.yaml`（无 timeout 字段；cron `0 0/1 * * * ?`）
  - `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:64-76`（syncGet 逐项阻塞，无任何超时）
  - `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:181-187`（`findAllByQuery` 无 limit，扫描集无界）
  - `nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:310-327`（fire 在 `synchronized(job)` 内内联执行 body）、`:353-358`（err → FAILED 后 `return`，不再 scheduleNext）、`:360-366`（ERROR 结果 → FAILED）、`:220-226`（fireNow 拒绝 FAILED）、`:175-183`（resumeJob 只认 SUSPENDED）
  - `nop-job/nop-job-local/src/main/java/io/nop/job/local/config/LocalJobConfigLoader.java:76-106,223-227`（@PostConstruct 一次性注册，无 reload；@PreDestroy→deactivate）
- **字段6 证据片段**：

  ```java
  // WfTaskScanner.java:96 —— 每个到期步骤在调度线程上同步等待业务动作完成，无超时
  FutureHelper.syncGet(workflowService.invokeActionAsync(request, null, newSchedulerContext()));
  // DaoWorkflowStore.java:181-185 —— 到期集无 limit，扫描时长随积压线性增长
  public List<? extends IWorkflowStepRecord> findDueActivatedSteps() {
      QueryBean query = new QueryBean();
      query.addFilter(FilterBeans.eq(NopWfStepInstance.PROP_NAME_status, ...ACTIVATED));
      query.addFilter(FilterBeans.le(NopWfStepInstance.PROP_NAME_dueTime, ...));
      return stepDao().findAllByQuery(query);
  }
  // LocalJobScheduler.java:353-357 —— 任一 fire 异常即 FAILED 终态且不再重排
  if (err != null) {
      LOG.error("nop.job.execute-failed:jobName={}", job.spec.getJobName(), err);
      job.state.internal = InternalState.FAILED;
      return;                       // ← 无 scheduleNext：cron job 一次失败永久停摆
  }
  // LocalJobScheduler.java:219-221 —— FAILED job 连手动 fireNow 都拒绝
  if (job.state.internal == InternalState.COMPLETED || job.state.internal == InternalState.FAILED)
      return false;
  ```

  执行通道核实：`BeanMethodJobInvoker.invokeAsync`（:57-73）对 void 方法在**调用线程内联反射执行**（`invokeReflective`→`fn.call0`，:113-127/179-189）；`invokeActionAsync`（WorkflowServiceImpl.java:134-144）经 `DefaultWorkflowExecutor`（第 1 轮 W1 已核：调用线程内联）执行 dueAction 业务体。即扫描体的每一步——无界 DB 查询、逐项动作执行——全部发生在 `nopJobScheduledExecutor`（app 全局单例，core=max=4，与 nop-batch `sys-event-batch-consumer` job 共享，全仓 3 个 job.yaml 中 2 个属本单元）的 timer 线程上，且全程持有该 job 的监视器（`synchronized(job)` 包裹 `executeJob`，LocalJobScheduler.java:311-321），期间 `getJobState`/`suspendJob`/`removeJob`/`fireNow` 等管理接口对该 job 全部阻塞。
- **字段7 触发时序（两种失效形态）**：
  - **形态 A（一次失败→永久停摆）**：①运维启用 `nop.job.wf-due-task-scan.enabled=true`（并修复 C4-003 的 bean 名）。②某次 fire 期间 DB 瞬态抖动，`ormTemplate.runInNewSession` 抛 SQLException → `invokeReflective` 捕获转 failedFuture → `.exceptionally` 记 `nop.job.bean-method-invoke-failed` 并返回 `JobFireResult.ERROR` → `handleResult` 判 `isErrorResult` → 置 FAILED → **不再 scheduleNext**。③该 cron job 永久停摆：`resumeJob` 只处理 SUSPENDED（:175-183 返回 false）、`fireNow` 明确拒绝 FAILED（:219-221）、`LocalJobConfigLoader` 为 @PostConstruct 一次性注册无 reload 机制——**唯一恢复途径是重启应用**。超时升级/提醒能力静默消失，全程仅 2 条 error 日志，无告警聚合。
  - **形态 B（一次挂死→永久占位）**：①同上启用。②某个 dueAction 的业务实现挂死（如调用 nop-task 任务踩中 C4-002 同池嵌套死锁、或外呼无超时的 socket 读）→ `syncGet` 无限等待 → invoker future 永不完成 → `executeJob` 的 `whenComplete` 永不触发 → `handleResult` 永不执行 → 同样**不再重排**，且该 fire 的池线程与 job 监视器永久占用（`cancelRunning` 为空实现，LocalJobScheduler.java:373-376 注释自认无法取消）。job 状态停在 RUNNING，无 watchdog、无日志。
  - 形态 B 依赖 C4-003 修复后才可达；形态 A 的注册期变体（当前 C4-003 未修复时）已经成立：bean 名失配使 `requireBean` 在 invokeAsync 方法体内**同步抛错**（BeanMethodJobInvoker.java:61,199-206）→ `executeJob` catch → FAILED → 首次 cron 触发后永久停摆。
- **字段8 影响与爆炸半径**：nop-wf 超时升级/提醒能力在启用形态下的可用性等于"零故障运行"——单次瞬态错误即永久失效且不可运维恢复（除重启）；扫描体与全应用 local job 共享 4 线程池，无界积压扫描可长时间挤压其他 job 的触发线程。
- **字段9 与 owner doc/已声明边界的关系**：owner doc `nop-wf.md` 将超时升级列为核心能力，未声明其调度链路"一次失败即永久停摆且不可恢复"；nop-job 重叠跳过语义（第 1 轮残留边界#4）只覆盖"慢"，不覆盖"失败终止"与"挂死"两种形态。非已载明边界。
- **字段10 修复方向建议**：①host 侧（移交 nop-job 单元）：cron job 的 FAILED 不应终端化——至少对非 onceTask 提供"失败继续按 cron 重排"开关或连续失败阈值；提供 FAILED job 的 resume/fireNow 恢复路径。②使用侧（本单元）：`findDueActivatedSteps`/`findRemindActivatedSteps` 加 limit 分批 + 多轮消化；WfTaskScanner 逐项 syncGet 加时限（超时记 error 跳过该条）；job.yaml 无法声明 timeout 的现状下，考虑将扫描体改为快速入队（把动作执行投递到 worker 池）后即返回。
- **字段11 误报校准复核**：缺省 disabled 与 C4-003 同口径（第 1 轮 P2 先例）；非平台设施自身实现立案——LocalJobScheduler 的 FAILED 终态语义属宿主设计，本案对象是本单元把"无界 body + 逐项内联业务执行 + 无超时"的扫描体装配到该宿主上的使用面选择；触发不需代码缺陷叠加，启用 + 一次瞬态故障即成立。
- **字段12 置信度**：高（fire 生命周期、FAILED 终态、fireNow/resumeJob 拒绝路径、loader 一次性注册、内联执行通道逐行核实）。
- **字段13 关联/交叉引用**：C4-003（同特性装配死面；本条含对其字段7"每分钟重复"表述的修正，见排除项 #1）；C4-002（形态 B 的现实挂死源之一）；nop-job 分布式调度器（coordinator/worker）不在本单元，若生产使用分布式形态本条仅在 local 形态成立。
- **复核状态**：已保留（P2，附 7.1 跨单元归并指令。从严复核属实：WfTaskScanner:96 syncGet 无超时、DaoWorkflowStore.findDueActivatedSteps/findRemindActivatedSteps（:181-194）findAllByQuery 无 limit、handleResult err/ERROR 双路置 FAILED 即 return 不重排、resumeJob 只认 SUSPENDED、fireNow 拒绝 FAILED、LocalJobConfigLoader @PostConstruct（:76）一次性注册无 reload、cancelRunning 空实现、BeanMethodJobInvoker void 方法调用线程内联执行、全仓 3 个 job.yaml 中 2 个属本单元且与 nop-batch sys-event-batch-consumer 共享 core=max=4 池（app-local-scheduler.beans.xml:10-16）——field5 引文的 LocalJobScheduler 行号相对 live 文件有 ≤14 行结构性漂移（resumeJob 实为 :161-174、fireNow 拒绝实为 :218-219、err→FAILED 实为 :352-355、ERROR→FAILED 实为 :358-362、cancelRunning 实为 :390-393），逐条实质证据已另行核实无误，不影响成立性。现状：形态 A（一次失败→FAILED 永久停摆）的宿主机制与 WI 1.4 nop-job-C7-006（P2，open）同面——7.1 须跨单元归并、勿重复修复立项，C7-006 复核备注"nop-wf-scheduler 内置 job 启用面未逐一核实"恰由本条补齐（enabled 缺省 false、启用为超时升级唯一驱动）；形态 B（一次挂死→RUNNING 永久占位，C7-006 只覆盖异常失败不覆盖挂死，nop-job-C7-007 系分布式 AbstractBatchScanner 链非 local 形态）与使用面（无界扫描集/逐项内联无超时/共享池挤压）为本批新发现）

### [nop-wf-nop-task-C4-008]

- **字段1 编号/标题**：nop-wf-nop-task-C4-008 C4-001 失效面扩展：`executor` 隔离配置无法覆盖延迟重试路径——重试体与重试簿记（含持久化 saveState 的 ORM 写）恒定运行在共享 globalTimer 线程上（C3-013 同池面的池治理视角补全）
- **字段2 严重度**：P1（C4-001 同级失效面扩展案：同一共享定时器的必然池耗尽面，本条证明"配 executor 隔离"这一直观缓解无效，爆炸半径不因隔离配置收窄）
- **字段3 缺陷族**：C4 池治理（任务-池隔离错配的失效面枚举补全）；交叉引用 C4-001、C3-013
- **字段4 簇/模块**：nop-task / nop-task-core（包装序 + 回调线程语义）；宿主池 GlobalExecutors.globalTimer
- **字段5 位置（文件:行）**：
  - `nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:114-120`（包装序：retry 在 executor **内侧**）
  - `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:268-284`（延迟重试 schedule + thenCompleteAsync 回调）
  - `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/FutureHelper.java:334-352`（`thenCompleteAsync` 用 `whenComplete`——handler 在完成 future 的线程上执行）
  - `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskStepRuntimeImpl.java:103-105`（saveState → stateStore.saveStepState）
  - `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:355`（持久化模式 saveStepState = ORM 实体写）
- **字段6 证据片段**：

  ```java
  // TaskStepEnhancer.java:114-120 —— executor 包装在 retry 之外：池线程执行的是"含 retry 循环的整链"
  if (stepModel.getRetry() != null) {
      step = new RetryTaskStepWrapper(step, buildRetryPolicy(stepModel));
  }
  if (!StringHelper.isEmpty(stepModel.getExecutor())) {
      step = new ExecutorTaskStepWrapper(step, stepModel.getExecutor());
  }
  // ExecutorTaskStepWrapper.java:36-40 —— 池线程仅在链返回 async 后注册桥接即释放
  TaskStepReturn result = getTaskStep().execute(stepRt);   // ← retry 循环在此运行
  ...
  result.whenComplete((data, err) -> { ... });              // 池线程此后空闲
  // FutureHelper.java:339 —— 完成回调在完成线程执行：ScheduledFuture 由 globalTimer 线程完成
  future.whenComplete((ret, err) -> { Object ret2 = handler.apply(ret, err); ...
  // TaskStepHelper.java:273-275 —— handler（= globalTimer 线程）内执行 doRetry：计数落盘 + 下一轮判定
  if (err != null)
      return doRetry(null, err, loc, stepRt, retryPolicy, action).getReturnPromise();
  ```

- **字段7 触发时序**：①步骤同时配置 `executor="X"`（任意专属池）与 `<retry retryDelay>0`。②首次尝试失败后，池线程（或调用线程）进入延迟分支：`schedule(action, delay)` 注册到 globalTimer，随后外层链因 async 返回而**释放池线程**——隔离配置到此为止。③delay 到期，**步骤体 action 在 globalTimer 线程上执行**（C4-001 已立案的主体）。④步骤体同步完成（DB/HTTP 调用型步骤的常态）时，`thenCompleteAsync` 的 handler 仍在该 globalTimer 线程上继续执行：`doRetry` → `state.setRetryAttempt` + `stepRt.saveState()`——持久化模式下是 `DaoTaskStateStore.saveStepState` 的 ORM 实体写（含 result/error JSON 序列化），即 **DB I/O 与 JSON 序列化发生在共享定时器线程**；随后下一轮 `getRetryDelay` 计算也在该线程。⑤若策略使后续延迟衰减为 0（如 retryDelay=0 配置或指数退避后归零路径），`retry()` 循环顶部的 `action.call()`（TaskStepHelper.java:289）在 timer 线程**内联执行步骤体**——无需延迟也上 timer。⑥多个此类步骤并发时，globalTimer（实际 4 线程）被业务体+DB 写占满，全平台定时语义（timeout 守卫/delay 步骤/nop-job globalTimer 回退）同 C4-001 时序恶化。
- **字段8 影响与爆炸半径**：与 C4-001 相同的全平台定时器饿死面；新增含义：**部署方按直觉配置 executor 隔离后风险不降级**，池隔离与定时器健康被误判为已治理；saveState 在 timer 线程执行还把 DAO 层的延迟/故障（锁等待、连接池等待）直接引入定时器线程。
- **字段9 与 owner doc/已声明边界的关系**：owner doc 未声明 retry 延迟体的执行线程归属，也未声明 executor 属性的隔离边界仅覆盖"首次尝试"；C3-013 已登记"delay 续延与 retry 恢复在 JVM 全局 timer 池执行业务 body"（异步链视角），本条为 C4 池治理视角的失效面补全（隔离配置无效性 + 簿记 I/O），不重复立案。
- **字段10 修复方向建议**：与 C4-001 的修复合一：延迟到期后经 `GlobalExecutors.globalWorker()` 或步骤显式 executor 执行 action（schedule 仅做定时唤醒），簿记回调随 action 同池迁移；若保留 timer 线程执行，至少把 `saveState` 类 I/O 移出完成回调链。
- **字段11 误报校准复核**：非低频路径（retry-delay 为文档化一等特性）；executor+retry 组合为常见配置意图（隔离），"配置无效"是治理语义缺陷而非纯理论；持久化 saveState=ORM 写已核到 DaoTaskStateStore 实现体；非平台设施自身实现（包装序与回调链均为本单元代码）。
- **字段12 置信度**：高（包装序、池线程释放点、whenComplete 完成线程语义、saveState 实现链逐行核实）。
- **字段13 关联/交叉引用**：C4-001（主体案，本条为其失效面扩展）；C3-013（同池面异步链视角）；C4-002（executor 隔离被误信为已生效的同类认知风险）。
- **复核状态**：已保留（P1。从严复核属实：包装序 retry（TaskStepEnhancer:114-116）在 executor（:118-120）内侧——executor 池线程执行的是含 retry 循环的整链，延迟分支返回 async 后池线程仅注册 whenComplete 桥接即释放（ExecutorTaskStepWrapper:36-47，result 非 done 走 whenComplete 分支后立即 ASYNC 返回）；schedule future 由 globalTimer 线程完成，故 thenCompleteAsync handler（FutureHelper:334-352 whenComplete 语义）在 timer 线程执行 doRetry→setRetryAttempt+saveState（TaskStepHelper:273-275,337-345）→DaoTaskStateStore.saveStepState（:355）ORM 写，"executor 隔离覆盖不了延迟重试路径"证明成立。一处精度瑕疵：字段7⑤"指数退避后归零路径"对内建 RetryPolicy 不可达（RetryPolicy.getRetryDelay:158-186 对正 retryDelay 只增不减、jitter 不归零；retryDelay=0 时无延迟分支、循环留在进入线程=executor 池线程）——⑤为表述过宽，主体主张①-④不依赖该子场景，不降级。对账：与同批 C3-013（复核保留 P2）的 retry 半边同池同站，C3-P2/C4-P1 判级分歧两复核均已声明留 7.1 统一；本复核维持 C4 侧 P1（本条立案对象是本单元包装序+簿记 I/O 上共享定时器的使用侧缺陷）。现状：新发现）

### [nop-wf-nop-task-C4-009]

- **字段1 编号/标题**：nop-wf-nop-task-C4-009 取消/超时不能撤回已排程的延迟重试体——与 DelayTaskStep 的撤单语义不对称，超时/kill 后挂起的重试体仍会在 globalTimer 上完整执行一次
- **字段2 严重度**：P3（状态面已被 first-terminal-wins 守卫兜住；残留影响为 post-终态副作用 + 定时器线程占用，依赖已声明的幂等边界缓解）
- **字段3 缺陷族**：C4 池治理（共享定时器上的不可撤回工作项）；伴生取消语义不对称
- **字段4 簇/模块**：nop-task / nop-task-core
- **字段5 位置（文件:行）**：
  - `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:268-270`（schedule 未绑定 cancel token）
  - `nop-task/nop-task-core/src/main/java/io/nop/task/step/DelayTaskStep.java:41-47`（同文件族内对称正例：bindCancelToken 撤单）
  - `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:249`（checkNotCancelled 仅在循环顶部，已排程 action 不经过）
- **字段6 证据片段**：

  ```java
  // TaskStepHelper.java:268-270 —— 延迟重试的 ScheduledFuture 未绑定 cancel token：取消时无法撤单
  return TaskStepReturn.of(null, FutureHelper.thenCompleteAsync(
          stepRt.getTaskRuntime().getScheduledExecutor()
                  .schedule(action, delay, TimeUnit.MILLISECONDS), ...);
  // DelayTaskStep.java:41-47 —— 同引擎内 delay 步骤的标准做法：取消即撤回占位定时器
  CompletableFuture<?> future = scheduledExecutor.schedule(() -> null, delay, TimeUnit.MILLISECONDS);
  ICancelToken cancelToken = stepRt.getCancelToken();
  FutureHelper.bindCancelToken(cancelToken, future);   // ← retry 路径缺这一步
  ```

  取消检查点核实：`checkNotCancelled` 仅存在于 TaskStepExecution seam 入口、retry 循环顶部（:249）与 Sleep/Throttle/RateLimit 步骤；重试体内层链（try/output/raw step）不再过 seam 检查，故已排程 action 触发时无任何取消短路。
- **字段7 触发时序**：①步骤配置 `timeout=10s` + `<retry retryDelay=60s>`。②首次尝试 5s 处失败，进入延迟分支，`schedule(action, 60s)` 挂上 globalTimer。③10s 时 timeout 守卫触发（同样在 globalTimer 线程，TaskStepHelper.java:199-204），winner 异常完成，步骤被 driver 置 EXPIRED 终态。④60s 时挂起的 action 照常触发，**完整执行一次步骤体**（业务副作用照常发生）；完成后 handler→`doRetry` 走 success 分支→`state.succeed`——被 TaskStepStateBean 的 first-terminal-wins 守卫早退（TaskStepStateBean.java:46-49，isDone 即 return），状态不被覆写；err 分支的 `fail` 同理（:55-63）。⑤kill 场景同型：取消时挂起重试体不撤单、到期照跑一次。
- **字段8 影响与爆炸半径**：步骤终态已 EXPIRED/KILLED 后仍发生一次完整业务副作用（超时被认为"已结束"的动作在数秒至数分钟后才实际执行）；globalTimer 上多滞留一个不可撤回的工作项（与 C4-001 池压力同向）。无状态损坏路径（已核守卫）。
- **字段9 与 owner doc/已声明边界的关系**：owner doc 已声明"取消为协作式、cancel(false) 非中断、步骤体必须幂等"——覆盖**运行中**的体；但"已排程未运行的体在取消后必然执行一次"超出该声明的字面范围（此处本可零成本撤单，DelayTaskStep 即如此实现），属声明外的不对称缺口而非已载明豁免。
- **字段10 修复方向建议**：对齐 DelayTaskStep 语义：`Future<?> f = executor.schedule(...)` 后 `FutureHelper.bindCancelToken(cancelToken, f)`；action 触发后首行补 `checkNotCancelled(stepRt)` 作为第二道闸。
- **字段11 误报校准复核**：窗口条件（失败后进入延迟分支且在 delay 内取消/超时）为真实可达时序而非竞态猜写；状态面损坏已被守卫排除，故不拔高到 P2；幂等声明使副作用后果降档，P3 恰当。
- **字段12 置信度**：高（撤单缺失、检查点分布、first-terminal-wins 守卫逐行核实）。
- **字段13 关联/交叉引用**：C4-001/C4-008（同一共享定时器上的工作项治理）；plan 349 Phase 4 取消/超时终结不变量（本条为其"终结后不再有新副作用发生"语义的残留缺口）。
- **复核状态**：已保留（P3。从严复核属实：延迟分支的 ScheduledFuture 未绑定 cancel token（TaskStepHelper:268-270）vs DelayTaskStep:46-47 `bindCancelToken` 对称正例；checkNotCancelled 全 step 分布 grep 核实仅 TaskStepExecution seam（:271）、retry 循环顶部（:249）与 Sleep/Throttle/RateLimit 三步骤——已排程 action 触发时无取消短路；TaskStepStateBean succeed/fail first-terminal-wins 守卫（`if (isDone()) return`）核实，状态面无损坏路径。窗口真实可达、后果被守卫+幂等声明兜住的 P3 校准恰当。现状：新发现）

---

## 2. 排除项/定向核查

1. **对第 1 轮 C4-003 字段7 的表述修正（不改前轮文件）**：bean 名失配的实际失效形态是"**首次 cron 触发即同步抛错（requireBean 在 invokeAsync 方法体内 throw）→ handleResult 置 FAILED → 永久停摆**"，而非"每分钟重复失败"。功能结论（启用即失效）不变，但噪音比描述的低、且更隐蔽（仅 1 条 `nop.job.execute-failed` error 日志）。依据：BeanMethodJobInvoker.java:57-73（requireBean :61 在 future 构造前抛出）+ LocalJobScheduler.java:275-279（executeJob catch）+ :353-358（FAILED 不重排）。该修正同时是 C4-007 形态 A 的注册期变体。
2. **timeout 守卫回调链在 globalTimer 线程上的消费者核实（通过）**：timeout 触发时 `cancellable.cancel` 在 timer 线程同步执行全部 onCancel 消费者（Cancellable.java:52-60,113-118）；内建消费者均为快操作——ExecutorTaskStepWrapper 的 `ret.completeExceptionally`（:60-63）、`FutureHelper.bindCancelToken` 的 `future.cancel(false)`（FutureHelper.java:518-526）；回调异常被捕获记日志不中断其他消费者。无重活上 timer 的内建路径，不立案。
3. **sync + runOnContext 活性（通过）**：BaseContext 为降级实现——`runOnContext` 在无等待处理线程时由调用者内联 flush（BaseContext.java:228-243），`syncGet` 为事件循环式等待（ContextTaskQueue.syncGet：等待线程自己排空队列），sync+runOnContext 组合不自锁、无孤儿队列。
4. **sync+executor 嵌套矩阵补充（C4-002 修复建议增补）**：包装序（TaskStepEnhancer.wrap，内→外：try→output→retry→**executor**→runOnContext→**timeout**→throttle→rateLimit→validator→**sync**→allowFailure）决定 `timeout` 在 sync 内侧——配置 step timeout 可把 C4-002 的永久死锁降级为有限超时错误（winner 被 timeoutGuard 有界化，TaskStepHelper.timeout:227-243）。该模型级缓解存在但无任何文档提示，建议并入 C4-002 修复方向③（文档警示）一并交付。throttle/rateLimit 在 executor 外侧：tryAcquire 发生在提交前的调用线程（有界等待，第 1 轮 T7 边界维持）。
5. **F8 关闭路径专项（两簇核对，无新案）**：`nopWfTaskScanner` bean 无 destroy-method 且不持有池（app-scheduler.beans.xml:9-13，注入对象均无线程资源）——通过；`LocalJobConfigLoader` @PreDestroy→deactivate 对称（第 1 轮已核）——但 `cancelRunning` 为空实现（LocalJobScheduler.java:373-376），在跑 fire 不被 deactivate 中断，仅靠 `nopJobScheduledExecutor` 的 destroy-method=destroy 兜底；`DefaultScheduledExecutor.destroy=shutdownNow`（DefaultScheduledExecutor.java:88-93）**无 awaitTermination**，且该池 threadDaemon=false（ThreadPoolConfig.java:40 缺省）——若扫描体卡在不响应中断的 JDBC/socket 读上，JVM 退出会被延迟。链式条件（需形态 B 挂死 + 停机时点重合），并入 C4-007 视角登记，不独立立案。task 侧：`TaskFlowManagerImpl` 无生命周期钩子但自身不持有池（nopTaskFlowManager bean 无 destroy 需求）；唯一自有池 T9 为死 bean 未启动（C4-005）。
6. **延迟续延的进程本地性（边界登记，非新案）**：globalTimer 上的 delay 占位与 retry 延迟体在停机时随 daemon 线程丢弃，无 drain/re-arm；持久化任务的跨重启恢复依赖 DaoTaskStateStore + 手动 resume（恢复时点语义属 C1/C3 维度裁定面）。第 1 轮残留边界#2（平台进程级模型）已覆盖，本轮不重复立案。
7. **可观测性校准（对第 1 轮 C4-001 字段3/字段7 伴生表述的回填，不改前轮文件）**：globalTimer 建池时 name="nop-global-timer" 且 `enableMetrics` 缺省 **true**（ThreadPoolConfig.java:44；GlobalExecutors.java:42-48）→ `ExecutorHelper.registerMetrics`（ExecutorHelper.java:201-205）绑定 micrometer `ExecutorServiceMetrics`——**池级队列深度/活跃线程度量实际存在**。C4-001 的观测缺口应精确表述为"任务级归因缺失"（业务体与守卫任务在 timer 线程上不可区分、无按任务类型的积压归因），而非"无池积压度量"。
8. **线程命名矩阵（登记，不立案）**：命名格式为 `prefix-poolNumber-threadNumber`（NamedThreadFactory.java:82，如 `nop-global-timer-1-1`；第 1 轮表格中 `nop-global-timer-N` 写法少了 poolNumber 段，前缀归因结论不受影响）。`nop-job-local-scheduler-*` 前缀同时承载 wf-due/remind 扫描与 nop-batch 事件消费的 job 体——不同业务体共享线程名，线程转储归因需依赖栈帧（与 C4-007 的共享池面互为印证）。`nop-task-execution-queue-*` 为死 bean（C4-005）。
9. **job.yaml 同面其他配置错误核查（无新案）**：cronExpr 非法在注册期 fail-fast（addJob→TriggerBuilder.buildTrigger 抛错；loader `failOnConfigError=true` 时应用启动失败，响亮）；bean/方法名错误同为触发期失败（ERR_JOB_METHOD_NOT_FOUND 与 C4-003 同走 FAILED 终态，见排除项#1）；loader 按路径字典序装载并以 registered 集合防重名（LocalJobConfigLoader.java:156-157,188-191）；`GlobalExecutors.register` 在两簇 main 源码零调用——C4-004 的遮蔽面无新触发点。
10. **wf 簇直接调度引用复核（通过）**：对 nop-wf 全部 main 源码 grep `\.schedule(|ScheduledExecutor|globalTimer|GlobalExecutors` 零命中——第 1 轮"wf 簇不自建线程池、不直接触碰平台执行器"结论维持。

**F3 失效面枚举（禁留空）**：第 1 轮 C4-003（wf 扫描 job 装配死面）、C4-005（nopTaskExecutionQueue 死 bean）、C4-006（ITaskQueue 死接口）+ 本轮 C4-007（扫描链路一次失败/挂死即永久停摆的韧性失效面，含 C4-003 失效形态修正）——共 4 项，非空。

---

## 3. 终止声明

第 2 轮深挖按追加指令完成全部盲区（C4-001/002 失效面扩展、包装序矩阵、WfTaskScanner 调度装配专项、关闭路径 F8 专项、线程命名与可观测性矩阵）。新发现 3 项（C4-007 P2、C4-008 P1 扩展案、C4-009 P3），排除项 10 条全部给出证据级核对结论。C4-001/C4-002 主体、C4-003~006 维持第 1 轮结论不变。**在当前代码面内，C4 维度未再发现新的 P1/P2 级未覆盖池治理问题——C4 维度深挖终止。**

## 4. 覆盖声明

- **本轮新增阅读面**：TaskStepEnhancer 包装序全文、TaskStepHelper（retry/timeout/withCancellable 全文）、ExecutorTaskStepWrapper/TimeoutTaskStepWrapper/RetryTaskStepWrapper/SyncTaskStepWrapper/ThrottleTaskStepWrapper/RunOnContextTaskStepWrapper/DelayTaskStep 全文、TaskFlowManagerImpl 全文、TaskStepRuntimeImpl.saveState、TaskStepStateBean.succeed/fail、DefaultTaskStateStore、DaoTaskStateStore（头部+saveStepState 定位）、TaskFlowManagerImpl beans 装配（task-defaults.beans.xml）、WfTaskScanner 全文、DaoWorkflowStore.findDue/RemindActivatedSteps、LocalJobScheduler 全文、LocalJobConfigLoader 全文、BeanMethodJobInvoker 全文、BeanContainerInvokerResolver 装配（app-local-scheduler.beans.xml）、JobFireResult 语义、BaseContext.syncGet/runOnContext、ContextTaskQueue 全文、FutureHelper（syncGet/thenCompleteAsync/bindCancelToken）、Cancellable 全文、GlobalExecutors（建池/注册）、DefaultScheduledExecutor 全文、ExecutorHelper（建池/metrics/命名）、ThreadPoolConfig 缺省值、NamedThreadFactory、checkNotCancelled 全 step 分布 grep。
- **对账完成**：与 C3-013 对账——同池面（task 业务体上 globalTimer）已按池治理视角补全为 C4-008，未重复立案；与第 1 轮全部 10 个使用点（W1-W4/T1-T10）与 7 条残留边界对账——本轮修正 2 处表述（C4-003 失效形态、C4-001 观测缺口措辞），其余维持。
- **边界声明**：nop-job-local（LocalJobScheduler/BeanMethodJobInvoker/JobFireResult）与 GlobalExecutors 平台内部实现按误报校准口径仅作宿主语义引用，立案对象均为本单元使用面（job.yaml 装配、扫描体实现、包装序）；nop-job 分布式形态、DaoTaskStateStore 恢复语义、C6/C9 面以对应维度审计为准。
