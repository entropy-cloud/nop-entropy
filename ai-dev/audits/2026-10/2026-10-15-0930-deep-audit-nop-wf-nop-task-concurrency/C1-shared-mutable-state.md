# C1 共享可变状态与可见性 — nop-wf + nop-task 第 1 轮初审（盲审）

- 审计单元：`nop-wf`（10 jar 子模块）+ `nop-task`（10 jar 子模块），合计 379 files / 36,107 lines（手写口径）
- 维度：C1 共享可变状态与可见性（F3 修实例不修类别 / F10 并发守卫缺失 定向对照）
- 审计人：C1 子代理（盲审，未读 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件）
- 日期：2026-10-15；分支：concurrency-audit-remediation；以 live code 为准（`_` 前缀生成文件不作发现对象）
- Owner doc 依据：`docs-for-ai/03-modules/nop-wf.md`、`docs-for-ai/03-modules/nop-task.md`
- 严重程度判级：P0=主路径数据竞争/包络内必然耗尽/主路径泄漏/可达死锁；P1=罕见但可具体化的竞态/错误取消路径泄漏/异步异常静默吞掉致状态不一致；P2=理论竞态/边界配置缺陷/并发测试缺失；P3=优化候选

## 检查范围说明

### wf 簇（nop-wf，205 files / 21,065 lines）

逐一深读或结构化扫描的 singleton bean（经 `_vfs/**/beans/*.beans.xml` 注册表核对）：`WorkflowManagerImpl`、`WorkflowEngineImpl`（extends `WfActorAssignSupport`）、`DefaultWorkflowExecutor`、`WorkflowCoordinatorImpl`、`DaoWorkflowStore`（extends `AbstractWorkflowStore`）、`DaoWorkflowModelLoader`、`WfResourceNamespaceHandler`、`DefaultWorkflowDOProvider`、`WfTaskScanner`（nop-wf-scheduler）、`WorkflowServiceImpl`（@BizModel）、`DaoWfActorResolver`、`WorkflowDesignerService`、nop-wf-service 全部 `*BizModel`（10 个，均无实例字段）。
逐文件扫描的运行时对象：`WorkflowImpl`、`WorkflowStepImpl`（final 字段）、`WfRuntime`、`WfActorWithWeight`、`ExecGroupSupport`（纯静态方法）、`TaskStepExecution` 无关面、`WfModelAnalyzer`、`WfExecGroupType`/`WfTransitionToType`（static-init textMap）、`WfGraphDocumentCodec`/`WfDesignerConfigBuilder`（无状态构建器）、`WfAiHelper`（无状态静态 helper）、`WfActorHelper`、`WorkflowRecordBean`/`WorkflowStepRecordBean`/`WorkflowActionRecordBean`、`NopWfInstance.transitToStatus`/`NopWfStepInstance.transitToStatus`。nop-wf-web 仅 resources 无 java。nop-wf-api/nop-wf-meta/nop-wf-codegen/nop-wf-app 各 1-64 文件均为接口/常量/main 类（`NopWfApplication.globalArgs` 为 main 线程专用，排除）。
同步机制盘点结果：wf 簇无 ThreadLocal、无 static 可变集合（枚举 textMap 为 static-init 只读）、无自建线程池（扫描排程外置 nop-job 配置）。跨线程共享状态收敛在两类载体：(1) singleton bean 的 init 期 @Inject 字段（启动后只读）；(2) ORM 实体（`nop_wf_instance`/`nop_wf_step_instance` 均声明 `versionProp="version"` 乐观锁列，见 `nop-wf/model/nop-wf.orm.xml:181,353`）。`WorkflowImpl` 每次 `WorkflowManagerImpl.getWorkflow/newWorkflow` 均新建（`WorkflowManagerImpl.java:76,87`），线程封闭。

### task 簇（nop-task，174 files / 15,042 lines）

逐一深读或结构化扫描的 singleton bean：`TaskFlowManagerImpl`（task-defaults.beans.xml）、`DaoTaskStateStore`（dao beans）、`DefaultTaskStateStore`（INSTANCE 单例）、`TaskExceptionRegistry`、nop-task-ext 5 个 decorator（`TransactionTaskStepDecorator`/`OrmSessionTaskStepDecorator`/`RetryTaskStepDecorator`/`TimeoutTaskStepDecorator`/`RateLimitTaskStepDecorator`，全部无状态构建期工厂）、nop-task-service 4 个 `*BizModel`（仅 `crudWriteDisabled` 工厂方法）、`TaskFlowMetricsImpl`（不可变字段）、`DefaultTaskExecutionQueue`（注册于 task-defaults.beans.xml，实现位于 nop-core 平台设施——只审使用方式：nop-task 侧无直接调用方）。
逐文件扫描的运行时对象：`TaskRuntimeImpl`、`TaskStepRuntimeImpl`、`TaskImpl`、`TaskStateBean`、`TaskStepStateBean`、`AbstractTaskStateCommon`、`TaskStepExecution`、`GraphTaskStep`、`ParallelTaskStep`、`AbstractForkTaskStep`/`ForkNTaskStep`、`ExecutorTaskStepWrapper`、`TimeoutTaskStepWrapper`、`RateLimitTaskStepWrapper`、`ThrottleTaskStepWrapper`、`TryTaskStepWrapper`、`TaskStepHelper`（timeout/retry/withCancellable）、`TaskStepBuilder`/`TaskStepEnhancer`/`TransformCustomStepHelper`（构建期）。nop-task-web/nop-task-meta/nop-task-app 无 main java 或仅 main 类。
同步机制盘点结果：task 簇无 ThreadLocal、无 static 可变集合（`DaoTaskStateStore.ROW_LOCKS` 为 static final 不可变数组 + 条带锁）、无自建线程池（复用 `GlobalExecutors`）。已声明的并发契约（owner doc `nop-task.md`）：全局限流/信号量注册表强引用不驱逐 [05-04]、parallel promise 聚合 [03-06]、CRUD 写保护、first-terminal-wins task 级监视器收敛 [05-01/02-12]、step 级 succeed/fail 对称守卫、`DaoTaskStateStore` 同 stepPath 行条带锁 [05-02]、fork 跨进程 DB-resume 不可靠（暂缓实施）——上述已声明且有守卫的边界均不立案。

## 发现条目

### [nop-wf-nop-task-C1-001] task 级 SUSPENDED 转移游离于 taskState 监视器之外，与 cancel 的 KILLED 驱动构成非原子 check-then-act——kill/超时取消可被静默丢失

- 文件:行号：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:154-161`（游离写者）；`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java:94-105`（监视器内 KILLED 驱动）
- 证据片段（TaskImpl.java:153-161）：

```java
        return stepReturn.thenCompose((ret, err) -> {
            if (err == null && ret != null && ret.isSuspend()) {
                // SUSPEND 不是终态（check2 P0-2 / plan 349 Phase 1）...
                taskState.setTaskStatus(TaskConstants.TASK_STATUS_SUSPENDED);
                taskRt.saveTaskState();
                return ret;
            }
```

  证据片段（TaskRuntimeImpl.java:97-105）：

```java
        synchronized (state) {
            Integer status = state.getTaskStatus();
            if (status != null && status == TaskConstants.TASK_STATUS_SUSPENDED && !state.isTerminal()) {
                state.exception(new io.nop.api.core.exceptions.NopException(
                        io.nop.task.TaskErrors.ERR_TASK_CANCELLED));
                state.setTaskStatus(TaskConstants.TASK_STATUS_KILLED);
                saveTaskState();
            }
        }
```

- 严重程度：P1（罕见但可具体化的竞态；后果为 kill/overall-timeout 语义违约 + 僵尸任务可被 resume）
- 并发场景：任务执行线程在 mainStep 返回 SUSPEND 后进入 thenCompose 挂起分支的同一时间窗，另一线程（整体超时定时器线程或请求取消线程）对同一 `TaskRuntimeImpl` 调用 `cancel(reason)`。
- 触发时序（已具体化，跨线程对真实存在）：
  - T1（任务执行线程）：`mainStep.execute` 返回 suspend 结果，进入 `thenCompose`，尚未执行 `taskState.setTaskStatus(SUSPENDED)`；
  - T2（orTimeout 调度线程，实际调用方为 `nop-ai/nop-ai-agent/.../TeamTaskFlowOrchestrator.java:655` 的 `taskRt.cancel(CANCEL_REASON_TIMEOUT)`，或经 `svcCtx.appendOnCancel` 传播的请求取消线程）：进入 `synchronized(state)`，读取 `state.getTaskStatus()` 得 **ACTIVE(30)**（T1 未写），条件 `status == SUSPENDED` 不成立——仅设置 cancel token 即返回，**不驱动 KILLED**；
  - T1：`setTaskStatus(SUSPENDED)` + `saveTaskState()` 落盘；
  - 终态：DB 中任务停留 SUSPENDED。T2 侧（AI team flow 场景）已按 honest-failure 报告整体失败并认定"underlying graph cancelled"（AR-3 语义），但 nop-task 任务并未 KILLED，后续 resume-when 触发仍可继续执行——正是 AR-3 要阻止的 zombie 工作。
- 现状：plan 364 [05-01/02-12] 已把 task 级四个终态 driver（COMPLETED/FAILED/KILLED/TIMEOUT）的"判定+写入+落盘"收敛到 `taskState` 监视器内原子序列（`TaskImpl.java:186-192,202-208,237-243,252-258`），但 **SUSPENDED 转移是第五个 taskStatus 写者，未纳入同一监视器**；`cancel()` 对"SUSPENDED 判定→KILLED 写入"的守卫依赖读取时刻该写者已可见，二者间无 happens-before。
- 风险：kill/overall-timeout 在窗口内被静默吞掉（无日志、无错误——cancel() 正常返回）；运维/AI 编排层认为任务已终结，任务实际仍挂起且可恢复执行，产生语义违约与不可预期的业务副作用复现窗口。
- 建议：把 TaskImpl.java:159-160 的 SUSPENDED 转移纳入 `synchronized (taskState)`（与四个终态 driver 同一监视器），并在 `cancel()` 的监视器块内对"刚转入 SUSPENDED 但尚在保存中"的状态同样驱动 KILLED；同时为"cancel 时读到非 SUSPENDED 即放弃"的路径补一条 debug 日志留痕。
- 失效面枚举（F3，全量 grep `setTaskStatus(` 运行时写者共 6 处）：①`TaskRuntimeImpl.java:102`（KILLED，已在监视器内）；②`TaskImpl.java:190`（COMPLETED，监视器内）；③`TaskImpl.java:206`（FAILED，监视器内）；④`TaskImpl.java:241`（KILLED，监视器内）；⑤`TaskImpl.java:256`（TIMEOUT，监视器内）；⑥**`TaskImpl.java:159`（SUSPENDED，未在监视器内——本发现唯一失效点）**。加载期写者（`DaoTaskStateStore.java:403`、`DefaultTaskStateStore.java:61`）为构造期单线程，不在失效面。修复必须覆盖 ⑥ 且不得回归 ①-⑤ 的既有监视器语义。
- 信心水平：高（跨线程对真实存在：TeamTaskFlowOrchestrator orTimeout 线程 vs 任务执行线程；交错窗口窄但每次整体超时逼近挂起点时都可能命中）
- 误报排除：非"无状态单例被并发调用"（taskState 是每 runtime 实例的共享可变对象）；非 synchronized/CHM 原语本身问题（恰是守卫未覆盖全部写者）。
- 复核状态： 已保留（P1——TaskImpl.java:159 游离写点、TaskRuntimeImpl.java:97-105 监视器守卫、TeamTaskFlowOrchestrator.java:655 跨线程驱动逐行确证；交错时序成立，kill 静默丢失后果具体）

### [nop-wf-nop-task-C1-002] wf 同一 step 实例并发 action 的"状态检查→执行→落盘"非原子——业务副作用先于乐观锁保护执行，双击/并发提交可重复执行 action source

- 文件:行号：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1210-1227`（invokeAction 前置校验）、`1292-1307`（doExitStep 在业务执行之后）
- 证据片段（WorkflowEngineImpl.java:1216-1226 与 1309-1318 节选）：

```java
        WfActionModel actionModel = requireActionModel(wfRt, actionName);
        ErrorCode errorCode = checkAllowedAction(actionModel, step, wfRt);   // 读 step 状态（ACTIVATED）
        if (errorCode != null)
            throw wfRt.newError(errorCode).param(ARG_ACTION_NAME, actionModel.getName());
        checkActionAuth(wfModel, wfRt);
        initArgs(actionModel, args, actionName, wfRt);
        return doInvokeAction(actionModel, step, wfRt);                      // 业务 source 在此执行
        // ... 之后才 doExitStep(step, status, wfRt) → saveStepRecord（version 列在此才参与乐观锁）
```

- 严重程度：P2（可具体化的并发窗口，但落库完整性由 ORM version 列兜底、业务幂等由平台惯例委托给 step 实现；无数据结构损坏）
- 并发场景：同一用户双击提交，或同一 step 实例（同 stepId）的两个并发请求各持独立 ORM 会话与独立 `WorkflowImpl` 实例（每次 `getWorkflow` 新建），同时对同一 ACTIVATED 步骤执行 action。
- 触发时序：T1/T2 同时 `getWorkflow(wfId)` → 各自加载 stepRecord（status=ACTIVATED，各自会话副本）→ 各自通过 `checkAllowedAction` → 各自执行 `actionModel.getSource()`（业务 XPL 副作用执行两次）→ 各自 `doExitStep` → `saveStepRecord` 落盘：后到者被 `nop_wf_step_instance.version` 乐观锁拒绝（`ERR_ORM_UPDATE_ENTITY_NOT_FOUND`），但其业务副作用已发生且不回滚（无事务内版本预检）。
- 现状：`NopWfStepInstance.transitToStatus`（`nop-wf-dao/.../NopWfStepInstance.java:33-44`）有 history 单调性 fail-fast 守卫，但守卫作用于会话内内存副本，跨会话竞态仅由 DB version 列在保存点收敛；引擎层无"保存前重读状态"或 action 记录唯一约束。WfTaskScanner 侧对同类竞态已有显式容错（`WfTaskScanner.java:97-110` 按 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` skip 并注释"并发步骤状态变迁竞态"），说明该竞态形态被引擎认知，但用户 action 路径未做等价防护或声明。
- 风险：action source 的业务副作用（发通知、写业务表、外部调用）重复执行；第二个请求收到裸 ORM 错误（错误码未映射为 wf 语义）。会签/vote 场景中每 actor 有独立 step 实例，正常多投票不受影响——失效面收敛为"同 stepId 并发提交"。
- 建议：在 `doInvokeAction` 业务执行前于当前事务内对 step 行做条件更新（`UPDATE ... SET status=EXECUTING WHERE stepId=? AND status=ACTIVATED`）式占位，或把 actionRecord 插入提前并加 (stepId, actionName) 唯一约束；至少在 owner doc `nop-wf.md` 显式声明"action source 必须幂等，跨会话并发同 step 提交由乐观锁兜底"的边界。
- 失效面枚举（F3，全仓 `transitToStatus(` 调用 17 处，均在 per-request 引擎流内）：终态方向守卫在 `NopWfInstance.transitToStatus`/`NopWfStepInstance.transitToStatus`/两个 RecordBean 共 4 处实现一致；跨会话保护统一缺位于保存点——同族面 = `WorkflowEngineImpl` 全部 action 出口（doInvokeAction、kill、suspend/resume、turnSignalOn）+ `WfTaskScanner.scanDueTasksInCurrentSession`（后者已有容错）。修复若采用占位更新需覆盖全部 17 个调用点的所属驱动路径。
- 信心水平：中高（竞态窗口真实；实际命中率取决于前端防重与部署形态）
- 误报排除：非"无状态单例被并发调用"（问题在共享 DB 行状态）；会签多 actor（每 actor 独立 step 实例）不在失效面内。
- 复核状态： 已保留（P2——invokeAction :1210 前置校验→doInvokeAction :1229 runXpl→doExitStep 后置落盘的非原子性确证；失效面陈述按 C1-010 修订：守卫一致仅 step 级 2 处实现，非 4 处）

### [nop-wf-nop-task-C1-003] WorkflowImpl 的 executing 标志与 commandQueue 无任何同步原语，与类注释声明的"同实例延迟执行"跨线程语义不匹配（当前线程封闭，理论风险）

- 文件:行号：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowImpl.java:41-51,79-100,102-115`
- 证据片段：

```java
    private final Map<String, WorkflowStepImpl> steps = new HashMap<>();
    private final Deque<Runnable> commandQueue = new ArrayDeque<>();
    /**
     * 是否正在执行过程中。如果是，则待做的工作放到continuations中等待后续执行
     */
    private boolean executing = false;

    public void delayExecute(Runnable task) {
        commandQueue.add(task);
        if (!executing) { this.executing = true; try { runCommandQueue(); } finally { this.executing = false; } }
    }
```

- 严重程度：P2（理论风险，未具体化——当前 live code 中未找到跨线程共享同一 WorkflowImpl 的路径）
- 并发场景：若同一 `WorkflowImpl` 被两个线程访问（该类自带 `steps` 缓存、`globalVars` 懒加载缓存，结构上"邀请"被缓存复用），T1/T2 同时进入 `delayExecute`：两者都可能读到 `executing==false` 并同时 `runCommandQueue()`（ArrayDeque 并发 poll 结构性损坏），或 T2 的 add 发生在 T1 poll 判空之后导致命令滞留队列直到下一次 delayExecute。
- 触发时序：无法在当前 live code 中具体化——`WorkflowManagerImpl.getWorkflow/newWorkflow` 每次 new 实例（`WorkflowManagerImpl.java:76,87`），`DefaultWorkflowExecutor.execute`、`WorkflowCoordinatorImpl`、`WfTaskScanner` 均各自新建；无全局 wf 实例注册表。故按盲审纪律降 P2 并标注理论风险。
- 现状：`executing` 非 volatile、`commandQueue` 为非线程安全 ArrayDeque、无 synchronized；类注释以"同一工作流实例相关的任务"暗示跨请求互斥语义，但实现仅支持单线程重入（`executeNow` 的 `if (this.executing) return task.get()` 同样只对重入正确）。
- 风险：未来任何"按 wfId 缓存 WorkflowImpl"的优化（steps/globalVars 缓存的存在使其看似可行）会直接落入数据竞争；当前风险为设计约束未声明，后续开发者无从得知线程封闭前提。
- 建议：在类 javadoc 显式声明线程封闭约束（每实例单请求），或按最小改动将 `executing` 改 volatile + `commandQueue` 改 ConcurrentLinkedDeque 并以 CAS 抢占执行权。
- 失效面枚举（F3）：同族面 = `delayExecute`/`executeNow`/`runCommandQueue` 三个方法 + `steps`(HashMap)/`globalVars`/`outputVars` 三处懒加载字段（`WorkflowImpl.java:41,43-44,297-311`）——同一实例上的全部非同步可变状态，任一跨线程共享路径出现即全部失效；当前全部依赖同一未声明前提（实例线程封闭）。
- 信心水平：中（设计缺陷判定高置信；live 可达性低置信——未找到共享路径）
- 误报排除：不按"见共享字段即报"立案——明确标注当前不可达；`WfRuntime` 全部字段为 per-request 创建（`WorkflowEngineImpl.newWfRuntime` 每调用新建），不在本条范围。
- 复核状态： 已保留（P2——executing/commandQueue 无同步与每调用新建实例（WorkflowManagerImpl.java:76/87）均确证；live API 面 + 线程封闭前提未声明，P2 理论竞态档自洽）

### [nop-wf-nop-task-C1-004] TaskStepStateBean 的 succeed/fail/setStepStatus first-terminal-wins 守卫为无锁 check-then-act——与 task 级监视器收敛不对称，存在 resultValue/终态写交错窗口

- 文件:行号：`nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java:46-63,186-195`
- 证据片段：

```java
    public void succeed(Object result, String nextStepId, ITaskRuntime taskRt) {
        // 终态守卫（plan 349 Phase 2）：已终态（如 kill 竞态先到）不得被覆写为 COMPLETED
        if (isDone())
            return;
        setResultValue(result);
        setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED);
    }
    ...
    public void setStepStatus(Integer stepStatus) {
        if (stepStatus != null && isDone() && !stepStatus.equals(this.stepStatus))
            return;
        this.stepStatus = stepStatus;
    }
```

- 严重程度：P2（理论风险为主：同一步骤 runtime 的状态写实际单线程；残余窗口见触发时序；并发测试缺失）
- 并发场景：步骤完成回调线程（thenCompose/whenComplete）调用 `succeed()` 的同时，另一驱动路径（如 kill/timeout 经 `driveStepFailure`→`fail()`+`setStepStatus(KILLED/EXPIRED)`）操作同一 `TaskStepStateBean`。
- 触发时序：T1 `succeed()`：`isDone()`=false 通过 → 执行 `setResultValue(result)`；T2 `setStepStatus(KILLED)`：`isDone()` 仍 false（T1 尚未写 COMPLETED）→ 写入 KILLED；T1 `setStepStatus(COMPLETED)`：isDone=true 且不同 → 被守卫拦截。终态 KILLED 正确保留（守卫兜底有效），但 bean 已携带 resultValue——若此刻任一线程触发 `saveTerminalStateIfDone`，落库行是"KILLED + resultValue 已填"。反向交错（T2 先 KILLED 落盘，T1 succeed 后到）被 `isDone()` 正确拦截。task 级同构竞态已由 plan 364 [05-01] 以监视器收敛消除，step 级无等价处理。
- 现状：三个方法均无同步；owner doc `nop-task.md` 声明"step 级 fail() 与 succeed() 对称携带 first-terminal-wins 守卫"，但守卫是无锁 check-then-act，其正确性依赖"每个 stepRt 的 state 写者单线程"这一未声明前提（类 javadoc 未写，对照 `TaskStepRuntimeImpl.java:24-30` 已声明的访问约束不变式，stepState 被标为 volatile 供"定时器/异步回调线程"读——读侧跨线程已声明，写侧互斥未声明）。
- 风险：终态语义本身由 setStepStatus 内部二次守卫保住（无 last-writer-wins 数据损坏）；残余为 KILLED/EXPIRED 行携带 resultValue 的混合状态、以及 `fail()` 写 exception 与 `succeed()` 写 resultValue 的自由交错——对 resume 重抛诊断（`TaskStepExecution.java:252-267` 消费 exception）可能产生"状态 KILLED、errorBeanData 为空但 resultValue 非空"的困惑性快照。
- 建议：与 task 级对齐——把 succeed/fail/setStepStatus 收敛到 stepState 监视器（或在 `TaskStepExecution` 两处终态 driver 外统一加锁），并在 `TaskStepRuntimeImpl` 类头不变式注释中补写"state 写者单线程"前提。
- 失效面枚举（F3，全量 grep）：写者面 = `TaskStepExecution.java:349`（succeed）、`:386/:399`（fail）、`:387/:400`（setStepStatus EXPIRED/KILLED/FAILED）、`TaskStepHelper.java:301/352`（retry 路径 succeed）、`:309`（retry fail）、`TaskStepStateBean.setStepStatus` 内部（终态守卫）——共 8 个调用点，全部无锁；同族兄弟无一处已收敛，修复需整族覆盖（retry 路径在 `TaskStepHelper.retry` 的 do/while 内与 delay-schedule 回调线程都可能触达）。
- 信心水平：中（无锁 check-then-act 事实高置信；实际跨线程写对在 live 代码中未逐路径证实——kill 驱动经 cancel token 通常由同一线程收口，故整体按 P2 登记）
- 误报排除：不报"setStepStatus 守卫本身线程不安全"——其内部守卫在单写者前提下正确；不报 fork 分支共享 stepPath 行（owner doc 已裁定边界，条带锁已声明）。
- 复核状态： 已保留（P2——succeed/fail/setStepStatus 无锁 check-then-act 与 8 个写点（TaskStepExecution:349/386-389/399-400、TaskStepHelper:301/309/352）确证；初审已自认跨线程写对未逐路径证实，P2 理论档自洽）

### [nop-wf-nop-task-C1-005] WfTaskScanner remindCount 读-改-写无守卫，扫描入口无重叠/多节点互斥——提醒计数丢失更新，dueAction 双触发仅靠引擎状态码容错

- 文件:行号：`nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:122-143`（remind 路径）、`78-120`（due 路径）
- 证据片段：

```java
    void scanRemindTasksInCurrentSession() {
        for (IWorkflowStepRecord stepRecord : workflowStore.findRemindActivatedSteps()) {
            try {
                ...
                Integer remindCount = stepRecord.getRemindCount();
                if (remindCount == null) { remindCount = 0; }
                stepRecord.setRemindCount(remindCount + 1);        // 会话内 read-modify-write
                stepRecord.setRemindTime(null);
                workflowStore.saveStepRecord(stepRecord);
```

- 严重程度：P2（多节点/重叠排程部署下必然发生丢失更新；单节点默认不可达——排程配置外置于 nop-job 部署侧，本单元内无触发器定义）
- 并发场景：两节点（或同节点重叠触发）同时执行 `scanRemindTasks()`，对同一 remind 到期步骤各持会话副本 read-modify-write `remindCount`。
- 触发时序：T1/T2 同时 `findRemindActivatedSteps()` 命中同一 stepRecord（remindCount=3）→ 各自写回 4 → 后到者乐观锁失败（version 冲突）或先到者覆盖后丢失一次计数（取决于两保存的事务时序，丢失更新与 ORM 异常两种结局都可能）。
- 现状：`nopWfTaskScanner` 为普通 singleton（`app-scheduler.beans.xml:8-12`），无分布式锁/DB 占位/shedlock 类防护；due 路径的竞态已有显式容错（`WfTaskScanner.java:97-110` 按 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` skip，注释"并发步骤状态变迁竞态"——该容错使 dueAction 双触发中的一个失败被静默吞掉，语义可接受），remind 路径的 remindCount 无等价处理也无声明。
- 风险：提醒计数少计（提醒统计失真）；`nop_wf_step_instance.version` 冲突异常落入 catch-all（`WfTaskScanner.java:136-141`）打 error 日志——日志噪音。无数据损坏、无死锁。
- 建议：remindCount 改用 DB 原子自增（`UPDATE ... SET remindCount = remindCount + 1`）或在 owner doc 声明"scanner 假定单实例排程"的部署边界；若声明边界，建议在 README/runbook 同步多节点部署时的 scanner 独占要求。
- 失效面枚举（F3）：同族面 = `WfTaskScanner` 全部读-改-写实体字段——`scanRemindTasksInCurrentSession` 的 remindCount/remindTime（本发现）与 `scanDueTasksInCurrentSession` 间接经 invokeAction 的引擎状态列（已有容错）；同模块无其他 RMW 面。
- 信心水平：高（RMW 事实确定；触发条件依赖部署形态，故 P2）
- 误报排除：单节点 cron 场景不可达，不按 P1 报；`ormTemplate.runInNewSession` 的会话隔离本身正确。
- 复核状态： 已保留（P2——remindCount RMW（WfTaskScanner.java:128-135）、普通 singleton 注册（app-scheduler.beans.xml）、due 路径竞态容错（:97-110）均确证；多节点/重叠排程前置成立）

### [nop-wf-nop-task-C1-006] DaoTaskStateStore.saveTaskState 不在条带锁保护内，与 saveStepState 不对称——跨 runtime 并发终态保存仅由 DB 乐观锁收敛，失败以裸 ORM 错误码冒泡

- 文件:行号：`nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:158-261`（saveTaskState 无锁）、`:339-374`（saveStepState 条带锁）
- 证据片段：

```java
    @Override
    public void saveTaskState(ITaskRuntime taskRt) {
        ITaskState state = taskRt.getTaskState();
        NopTaskInstance entity = taskDao().getEntityById(state.getTaskInstanceId());
        boolean isNew = entity == null;
        if (isNew) { entity = taskDao().newEntity(); entity.setTaskInstanceId(state.getTaskInstanceId()); }
        state.beforeSave(taskRt);
        ...
        if (isNew) taskDao().saveEntityDirectly(entity);
        else       taskDao().updateEntityDirectly(entity);      // version 列条件更新，无进程内串行化
    }
```

- 严重程度：P2（正确性由 DB version 列兜底、不产生数据损坏；残余为错误语义未映射 + 内存态/DB 态漂移窗口）
- 并发场景：同一 taskInstanceId 被两个并发 runtime 驱动（重复 resume：两次 `getTaskRuntime` 各建 runtime/各 load taskState bean；或 resume 与 cancel 跨请求并发），两个 runtime 各自 `synchronized(各自的 taskState)`（监视器不跨实例）后并发 `saveTaskState`。
- 触发时序：T1（resume 执行线程）driveTaskCompleted：监视器内 `saveTaskState` version N→N+1 落盘 COMPLETED；T2（kill 线程）driveTaskKilled：自己 bean 仍是 ACTIVE，skipTerminalOverwrite 放行 → `saveTaskState` 以 version N 条件更新 → ORM 抛 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND` → 异常从 `TaskRuntimeImpl.cancel`（或 resume 调用方）以裸 ORM 错误冒泡，而非 first-terminal-wins 的幂等静默/明确任务语义错误码。
- 现状：plan 364 [05-02] 条带锁注释明确只覆盖"同 (taskInstanceId, stepPath) 行的进程内 read-copy-write"，task 级行无对应串行化；`skipTerminalOverwrite`（`TaskImpl.java:266-275`）只在同一 bean 内生效，跨 bean 无效。DB 层 version 列保证最终一致（不损坏），但失败路径的错误码、以及 T2 内存 bean 与 DB 的永久漂移（T2 的 bean 停留在 ACTIVE 记账）均未处理。
- 风险：调用方（AI 编排、运维 kill API）收到与任务语义无关的 ORM 错误；T2 runtime 后续操作基于漂移的内存状态。无双写损坏（version 列）、无死锁（锁序单一：taskState 监视器与条带锁不嵌套——saveTaskState 在监视器内调用且不取条带锁）。
- 建议：saveTaskState 复用条带锁模式（按 taskInstanceId 加锁串行化进程内双 driver），或在捕获 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND` 后重读 DB 状态、按 first-terminal-wins 静默收敛并告警；同时为该路径定义任务语义错误码（对齐 error-handling 两档策略）。
- 失效面枚举（F3）：同族面 = `DaoTaskStateStore` 全部实体直写点——`saveTaskState` 的 `:258/:260`（本发现）、`saveStepState` 的 `:370/:372`（已有条带锁）；`DefaultTaskStateStore` 为 no-op 不在失效面。任务级条带锁若引入，需与 [05-02] 的 64 槽实现共享锁序审计（当前无嵌套获取点，安全）。
- 信心水平：高（不对称事实确定；双 runtime 并发在 resume-when 自动触发+人工 kill 叠加时可自然发生）
- 误报排除：不报 fork 分支同 stepPath 行覆写（owner doc 已裁定为 DB 断点续跑完整性主题）；不报 PK 冲突双插入（taskInstanceId 主键保护）。
- 复核状态： 已保留（P2——saveTaskState（DaoTaskStateStore.java:158-261）无条带锁 vs saveStepState（:354-374）条带锁的不对称确证；owner doc nop-task.md 仅声明 step 行条带锁，task 行缺口与其声明一致）

### [nop-wf-nop-task-C1-007] TaskStepRuntimeImpl.addStepCleanup（synchronized）与 runStepCleanups（非同步 drain 同一 ArrayList）锁语义不对称，且 addStepCleanup 生产代码零调用

- 文件:行号：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskStepRuntimeImpl.java:53,180-195`
- 证据片段：

```java
    private List<Runnable> stepCleanups;

    @Override
    public synchronized void addStepCleanup(Runnable cleanup) {
        if (cleanup == null) return;
        if (stepCleanups == null) stepCleanups = new ArrayList<>();
        stepCleanups.add(cleanup);
    }

    @Override
    public void runStepCleanups() {
        if (stepCleanups != null) {
            stepCleanups.forEach(this::runCleanup);
            stepCleanups.clear();
        }
    }
```

- 严重程度：P2（理论风险，未具体化——全仓 grep 确认 `addStepCleanup` 无任何生产调用方，仅 6 个测试 fake 实现；竞态在当前 live code 不可达）
- 并发场景（假设性）：未来步骤实现经异步回调线程调用 `addStepCleanup` 注册清理（synchronized 写），与此同时完成路径线程在 `TryTaskStepWrapper.java:57/:80` 调 `runStepCleanups()`（非同步读/forEach/clear 同一 ArrayList）→ 数据竞争（ConcurrentModificationException / 丢失清理 / 损坏的数组结构）。
- 触发时序：无法在当前 live code 具体化（无生产注册者），按盲审纪律降 P2 标注理论风险。
- 现状：写侧 synchronized、读/drain 侧完全无同步——混合同步语义比全无同步更具误导性（暗示存在跨线程契约，实际无法成立）；类头不变式注释（`:24-30`，plan 364 [05-06]）列举的可变字段中不含 stepCleanups 的跨线程约定。
- 风险：API 表面（`ITaskStepRuntime.addStepCleanup`）是公开扩展点，任何 step 实现者按"synchronized 签名暗示线程安全"使用即踩坑；当前无生产损害。
- 建议：runStepCleanups 同步化或将 stepCleanups 改为 `ConcurrentLinkedQueue` + drain 全清；若确认该 API 为死表面，在接口 javadoc 声明单线程契约。
- 失效面枚举（F3）：同族面 = `stepCleanups` 单字段 + `ITaskStepRuntime.java:131/:133` 两个接口方法 + 唯一实现 `TaskStepRuntimeImpl`——失效面仅此一处（无同族兄弟类）；对照同文件 `addTaskCleanup`（`TaskRuntimeImpl.cleanups`，基于 `Cancellable` 并发原语，`TaskRuntimeImpl.java:56,266-275`）已是并发安全实现，两套 cleanup 机制防护水平不一致。
- 信心水平：高（不对称与零调用事实均 grep 确证）
- 误报排除：测试 fake 中的实现不作为发现对象；`TaskRuntimeImpl.cleanups` 路径并发安全，不立案。
- 复核状态： 已保留（P2——addStepCleanup 生产零调用（仅接口声明+实现）、runStepCleanups 生产消费侧在用（TryTaskStepWrapper.java:57/80）、锁语义不对称均 grep 确证；边缘观察：类头不变式注释 :27 实际已列名 stepCleanups，发现现状句欠精确，不影响判级）

### [nop-wf-nop-task-C1-008] TaskStateBean.taskVars 懒初始化 LinkedHashMap 且 setTaskVar 无同步——当前零生产调用方的防御性缺口（未来 executor 并行分支调用即成 HashMap 结构性竞态）

- 文件:行号：`nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStateBean.java:145-155`
- 证据片段：

```java
    @Override
    public Map<String, Object> getTaskVars() {
        if (taskVars == null)
            taskVars = new LinkedHashMap<>();       // 非线程安全容器 + 非原子懒初始化
        return taskVars;
    }

    @Override
    public void setTaskVar(String name, Object value) {
        getTaskVars().put(name, value);             // 并发 put 可致 LinkedHashMap 树化/扩容损坏
    }
```

- 严重程度：P3（优化候选/防御性加固——全仓 grep 确认 `setTaskVar`/`getTaskVars()` 在生产代码零调用（接口+实现自带），当前不可达）
- 并发场景（假设性）：配置了 `executor` 的 `<parallel>`/`fork` 分支（真实并行，owner doc [03-06] 声明的唯一真并行路径）中步骤体调用 `taskRt.setTaskVar(...)`：T1/T2 并发 `getTaskVars()` 懒初始化 → 各自 new LinkedHashMap，一方覆盖另一方；或并发 put 触发扩容 → 死循环/丢条目（经典 HashMap 并发缺陷）。
- 触发时序：无法在当前 live code 具体化（无生产调用方），按纪律标注理论风险并降 P3。
- 现状：taskState bean 的其他高并发字段已分别设防——`newRunId()` synchronized（`TaskStateBean.java:90-93`）、终态字段由 C1-004 守卫；唯独 taskVars 容器无防护且选择了非并发容器。
- 风险：作为 `ITaskState` 公开接口的一部分，未来接入（如 job 调度回写 vars、断点恢复 taskVars 持久化——owner doc 已注明"taskVars 无对应列，维持暂缓裁定"）时会直接暴露竞态。
- 建议：改 `new ConcurrentHashMap<>()`（或懒初始化移入 synchronized）；与 taskVars 持久化暂缓裁定联动处理。
- 失效面枚举（F3）：同族面 = `TaskStateBean.taskVars` 单字段 + `ITaskRuntime.java:91-105` 四个 default 方法（getTaskVar/setTaskVar/getTaskVars/removeTaskVar）——仅此一个实现类（`DefaultTaskStateStore` 复用同一 TaskStateBean），无其他同族兄弟。
- 信心水平：高（不可达判定经全仓 grep 确证；缺陷形态为教科书级确定性）
- 误报排除：严格区分"缺陷"与"未接线表面"——因生产零调用降 P3，不占 P2 序列。
- 复核状态： 已保留（P3——getTaskVars/setTaskVar 生产零调用（仅接口自委托）与懒初始化 LinkedHashMap 均确证；P3 校准与 C1-003/C1-007 的区分理由成立）

## 共享可变状态清单表

### wf 簇

| # | 载体 | 位置 | 谁写/谁读 | happens-before 机制 | 判定 |
|---|------|------|-----------|---------------------|------|
| W1 | singleton bean init 字段 | WorkflowManagerImpl（store/engine/coordinator）、WorkflowEngineImpl+父类 WfActorAssignSupport（resolver/userDelegateService）、DefaultWorkflowExecutor、WorkflowServiceImpl、WfTaskScanner（5 个 @Inject）、DaoWorkflowStore、DaoWfActorResolver、WorkflowDesignerService | 容器启动写；请求期只读 | NopIoC 容器初始化完成后发布 bean（安全发布） | 无缺陷（init 期注入，运行期只读） |
| W2 | WorkflowImpl 实例字段（steps HashMap、globalVars/outputVars 懒加载、commandQueue、executing） | WorkflowImpl.java:41-51 | 每次请求 new 实例，单请求线程写读 | 实例线程封闭（经 WorkflowManagerImpl 每调用新建） | C1-003（理论风险 P2：封闭前提未声明） |
| W3 | WfRuntime 实例字段（scope/currentStep/selectedActors 等 14 个可变字段） | WfRuntime.java:40-92 | 每次 action 调用新建；仅当前请求线程 | 线程封闭 | 无缺陷 |
| W4 | ORM 实体行状态（nop_wf_instance/nop_wf_step_instance/nop_wf_work 等，含 signals KV、status、version） | nop-wf/model/nop-wf.orm.xml:181,353,761 | 各请求会话各自 load/copy 写 | DB version 列乐观锁（保存点收敛）+ transitToStatus 会话内单调守卫 | C1-002（P2：副作用先于保护执行）|
| W5 | 枚举反查表 textMap | WfExecGroupType.java:42-48、WfTransitionToType.java:42-48 | static-init 写； thereafter 只读 | 类初始化（JLS 12.4） | 无缺陷 |
| W6 | NopWfApplication.globalArgs（static 可变） | nop-wf-app/.../NopWfApplication.java:22 | main 线程写读 | 线程封闭（main） | 无缺陷（误报排除） |
| W7 | WfTaskScanner 对 stepRecord 的 remindCount/remindTime RMW | WfTaskScanner.java:128-135 | 扫描触发器线程写；DB 读回 | 无进程内/跨节点同步；依赖 DB version 列（部分兜底） | C1-005（P2） |

### task 簇

| # | 载体 | 位置 | 谁写/谁读 | happens-before 机制 | 判定 |
|---|------|------|-----------|---------------------|------|
| T1 | 全局限流器/信号量注册表（strong-ref） | TaskFlowManagerImpl.java:63-66,175-207 | 任意执行线程 computeIfAbsent 写、acquire/release 读写 | ConcurrentHashMap computeIfAbsent 原子性 + AtomicBoolean 告警幂等 + 容量阈值 WARN | 已声明边界（plan 364 [05-04]，强引用不驱逐+告警）——不立案 |
| T2 | taskRt attrs（任务级属性表，含 rate-limit/semaphore 非全局键、first-instantiated-step-paths） | TaskRuntimeImpl.java:36,179-205；TaskStepRuntimeImpl.java:172-178 | 执行线程 + 并行分支线程 | ConcurrentHashMap（attrs）/CHM.newKeySet | 无缺陷 |
| T3 | taskState bean（taskStatus/exception/resultValue/nextRunId/taskVars） | TaskStateBean.java | 任务执行线程 + cancel/超时线程 | taskState 监视器（四个终态 driver + newRunId）；**SUSPENDED 写者游离** | C1-001（P1）；taskVars 见 C1-008（P3） |
| T4 | stepState bean（stepStatus/exception/resultValue/stateBean/outputs） | TaskStepStateBean.java:46-63,186-195,218-223 | 完成回调/失败驱动线程 | 无锁 check-then-act（内部终态守卫兜底）；stepState 引用 volatile | C1-004（P2） |
| T5 | stepRt 可变字段（cancelToken volatile、outputNames/enabledFlags/persistVars/exception 非volatile） | TaskStepRuntimeImpl.java:38-54 | 创建线程或 future 链 HB（类头 :24-30 已声明不变式） | volatile（cancelToken/stepState）+ future-chain happens-before + addStepCleanup synchronized（读侧未同步） | C1-007（P2 理论）；其余按已声明不变式不立案 |
| T6 | DB 行条带锁 + step/task 实体行 | DaoTaskStateStore.java:339-374（step）、:158-261（task） | 各 runtime 的状态保存 | 条带锁（step 行，plan 364 [05-02] 已声明）；task 行仅 DB version 列 | step 不立案（已声明）；task 见 C1-006（P2） |
| T7 | TaskExceptionRegistry（factories LinkedHashMap 构造期填充 + reflectiveMissing CHM 懒缓存） | TaskExceptionRegistry.java:43-53 | 构造期写；运行期多线程读/并发标记 missing | 构造期安全发布 + CHM | 无缺陷 |
| T8 | GraphTaskStep 运行期容器（stepResults CHM、stepFutures HashMap、runningCount AtomicInteger） | GraphTaskStep.java:182-245,429-453 | 图驱动线程 + whenComplete 回调线程 | CHM + CompletableFuture 完成链 HB + AtomicInteger + 级联先于减计数不变式（plan 364 [05-07] 已声明） | 无缺陷（已声明且有守卫） |
| T9 | DefaultTaskStateStore.INSTANCE（static 单例） | DefaultTaskStateStore.java:18 | init 期类加载写； thereafter 无状态读 | 类初始化 | 无缺陷 |
| T10 | NopTaskApplication.globalArgs（static 可变） | nop-task-app/.../NopTaskApplication.java:16 | main 线程 | 线程封闭 | 无缺陷（误报排除） |
| T11 | Long/double 非原子读写、`++` 复合递增 | 全簇 grep：唯一复合递增为 `TaskStateBean.nextRunId++`（synchronized 方法内，:90-93）与 `runningCount`（AtomicInteger）、`waitingCount`（AtomicInteger） | — | synchronized / CAS | 无缺陷（无裸 long/double 共享字段） |
| T12 | 发布安全（构造器启动线程/注册监听器） | 全簇 grep：`TaskRuntimeImpl` 构造器内 `svcCtx.appendOnCancel(onCancel)`（:79-81）注册到请求上下文——构造中向外发布 self 引用，但 onCancel 回调仅在 cancel 时触发且字段届时已初始化完成（容器/工厂同步构造） | — | 构造完成后经 runtime 引用返回 | 无缺陷（边界观察：构造期注册回调模式，无提前泄漏证据） |

## 覆盖度与残留边界

- 覆盖声明：wf 簇与 task 簇全部 20 个 jar 子模块的手写 main java（379 files）经"beans.xml 注册表核对 + 全量字段 grep + 重点类逐行深读"覆盖；C1 五类载体（singleton bean 实例字段、static 可变字段、全局注册表/缓存、ThreadLocal、集合/参数逃逸隐式共享）逐一盘点。两簇 ThreadLocal 命中数为 0（无需评估池化线程 remove 问题）；static 可变字段仅 3 处（2 个 app main 的 globalArgs + 枚举 textMap），均排除；`_gen/`、`_*.java` 生成物未审（含全部 ORM 生成实体的 PROP_NAME_TO_ID static HashMap——静态 init 只读，形态安全）。
- grep 基线对照：`_tmp/cra-baseline-nop-wf-nop-task.txt` 的 C1 三节（static non-final / static final collections / ThreadLocal）逐项复核，基线条目中非 import/非方法签名的真实字段命中均已在上表收口。
- 已声明不立案清单（owner doc `nop-task.md` 载明且有守卫/裁定）：全局限流/信号量注册表强引用不驱逐 [05-04]；parallel/fork promise 聚合（默认内联串行）[03-06]；CRUD 写保护（xmeta updatable=false + ERR_TASK_CRUD_WRITE_DISABLED）；fork/fork-n 跨进程 DB-resume 不可靠（暂缓实施）；步骤副作用与终态保存无事务原子性（幂等要求）；同 stepPath 行条带锁 [05-02]；graph 级联先于减计数 [05-07]。
- owner doc 漂移登记（不立案）：①`nop-wf.md` 子模块表仅列 4/10（缺 api/ai/app/codegen/meta/scheduler 六行，本审实际覆盖 10 个）；②`nop-wf.md` 对 wf 引擎并发语义零声明（对比 nop-task.md 的 7 项并发契约）——C1-002 的"action 双执行窗口"无 owner 边界可对照，建议补写；③WfTaskScanner 的排程触发器定义外置（nop-job 部署配置），本单元内无法核实重叠触发配置，C1-005 的触发条件判定受此限制。
- 残留边界（本维度未展开，移交对应维度）：①`TaskRuntimeImpl.cancel` 与 `TaskImpl` 终态 driver 的锁序与状态机完备性深审（C2 锁与协议维度）；②条带锁 64 槽的锁竞争与公平性（C2/C9）；③ORM 会话级实体缓存跨请求的隔离性（平台基础设施，仅使用方视角）；④`DefaultTaskExecutionQueue`（nop-core 设施）在 nop-task 侧当前零调用，其自身实现不在本单元范围；⑤C6/C9 集外线索评估：`ThrottleTaskStepWrapper` 信号量许可在 body promise 永不完成且未配 timeout 时被活持有（持有者线程仍存活，属挂死非泄漏，且以用户误配为前提）——P2 形态，按 overlay 规则（P2/P3 不登记）不立 CL 条目；`WfTaskScanner.scanDueTasks` 的 `FutureHelper.syncGet` 阻塞调度线程——P3 形态，不登记。
- 维度集外线索：无（本轮 C1 视角未发现达 P0/P1 门槛的 C6/C9 形态线索）。

## 维度复核结论（独立复核子代理）

- 复核人：C1 独立复核子代理（与初审不同角色；对全部 10 条逐条重读 live code 验证文件/行号/证据/触发时序/判级口径，以"被审计方可用什么证据反驳"为标准从严裁断）
- 复核日期：2026-10-15；分支：concurrency-audit-remediation；HEAD `be0cbbb2a3`；以 live code 为准（`_` 前缀生成文件不作发现对象）
- 复核方法与输入：完整读取 C1-shared-mutable-state.md / C1-round2.md / C1-round3.md；逐条重验关键文件（TaskImpl、TaskRuntimeImpl、TaskStateBean、TaskStepStateBean、TaskStepExecution、TaskStepHelper、SuspendTaskStep、TaskStepRuntimeImpl、TaskFlowManagerImpl、DaoTaskStateStore、DefaultTaskStateStore、ITaskState、WorkflowEngineImpl（invokeAction/doInvokeAction/start/suspend/resume/kill/checkEnd/doEndWorkflow 段）、WorkflowImpl、WorkflowManagerImpl、WfRuntime.saveWfRecord、NopWfInstance、NopWfStepInstance、WorkflowRecordBean、WorkflowStepRecordBean、WfTaskScanner、app-scheduler.beans.xml、IWorkflow.isEnded、TeamTaskFlowOrchestrator:595-665）；独立 grep 复核（`setTaskStatus` 6 运行时写点、`TASK_STATUS_ACTIVE` 2 构造期写点、`transitToStatus` 15 调用+2 声明、`addStepCleanup`/`runStepCleanups`/`setTaskVar`/`getTaskVars` 零生产调用）。P1×2（C1-001/C1-009）按复核指令从严验证：写点矩阵、监视器内外、终态守卫缺失、orTimeout 驱动线程（TeamTaskFlowOrchestrator.java:655 逐字确证）、SuspendTaskStep 无 token 检查、resume 无 ACTIVE 重置穷尽核对。
- 现状标注依据：git log 自绿色基线 `b3a9c76bed` 起 `nop-task/`、`nop-wf/` 零提交（工作区仅本审计目录与 plan 2465 未跟踪文件）；cra-index.md 报告清单仅含 WI 1.1–1.4（nop-stream/nop-job 单元），本单元未入台账；同 campaign WI 1.1–1.4 审计目录 grep 无本单元相关发现（nop-job C7-006 仅将 nop-wf-scheduler 列为部署影响面，与本案无关）。code 注释中 plan 364 [05-0x] 修复已含于基线——本批 10 条均为该批修复之后的残余面首次登记，故全部标注「新发现」，无「已知未修复」/「已修复」项。

### 逐条判定表

| 编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|------|--------|------|-----------|---------|
| C1-001 | P1 | 保留 | SUSPENDED 写者游离（TaskImpl.java:159 无监视器无守卫）× cancel 监视器内 status==SUSPENDED 判据（TaskRuntimeImpl.java:99）× 跨线程对确证（TeamTaskFlowOrchestrator.java:655 orTimeout 线程），交错成立且 kill 静默丢失（resume 换新 runtime 丢弃旧 token）后果具体 | 新发现 |
| C1-002 | P2 | 保留 | check（:1210 前置校验）→执行（:1229 runXpl source）→落盘（doExitStep 后置）非原子确证；nop_wf_step_instance version 列（orm.xml:353）兜底、副作用不回滚；失效面"4 处实现一致"陈述按 C1-010 修订后仍保留（核心竞态为跨会话形态，修订不影响判级） | 新发现 |
| C1-003 | P2 | 保留 | executing/commandQueue 零同步（WorkflowImpl.java:46-51,80-115）与实例每调用新建（WorkflowManagerImpl.java:76/87）确证；live API 面 + 线程封闭前提未声明，P2 理论竞态档自洽 | 新发现 |
| C1-004 | P2 | 保留 | succeed/fail/setStepStatus 无锁 check-then-act（TaskStepStateBean.java:46-63,188-195）与全部 8 写点确证；初审已自认跨线程写对未逐路径证实（两 driver 实为同一 promise 链的两分支），P2 理论档自洽 | 新发现 |
| C1-005 | P2 | 保留 | remindCount RMW（WfTaskScanner.java:128-135）、普通 singleton 注册（app-scheduler.beans.xml 无互斥防护）、due 路径已有竞态容错（:97-110）对照确证；多节点/重叠排程前置成立 | 新发现 |
| C1-006 | P2 | 保留 | saveTaskState（DaoTaskStateStore.java:158-261）无条带锁 vs saveStepState（:354-374）条带锁不对称确证；owner doc nop-task.md 仅声明 step 行条带锁，task 行缺口与声明一致，非豁免边界内 | 新发现 |
| C1-007 | P2 | 保留 | addStepCleanup 生产零调用（仅接口+实现）、runStepCleanups 生产消费侧在用（TryTaskStepWrapper.java:57/80）、synchronized 写 vs 非同步 drain 不对称均确证；消费侧 live 意味着竞态仅需生产者出现（公开扩展点正常使用即触达），与 C1-008 的双侧 dormant 有别，维持 P2 | 新发现 |
| C1-008 | P3 | 保留 | getTaskVars/setTaskVar 生产零调用（仅接口自委托）+ 懒初始化 LinkedHashMap 确证；P3 校准自洽 | 新发现 |
| C1-009 | P1 | 保留 | 从严复核通过：前提 A（resume 无 ACTIVE 重置，`TASK_STATUS_ACTIVE` 运行时写者仅 2 处 fresh 构造期）+ suspend 分支裸覆写（无终态守卫无监视器）+ SuspendTaskStep.execute 不检查 token（覆写时序 err==null 成立）+ owner doc nop-task.md cancel-KILLED 声明前提（"SUSPENDED（无驱动在飞）"）被 resume 路径事实违反，全链条确证 | 新发现 |
| C1-010 | P2 | **降级至 P3** | 守卫不对称（wf 级 2/4 实现零守卫）与对 C1-002"4 处一致"的事实修订均成立并采纳；但场景 A 时序有误——kill 运行于 executeNow 内（executing=true），doEndWorkflow(KILLED) 经 delayExecute 排队、在 AFTER_KILL listener 之后执行，listener 运行时记录仍为 ACTIVATED 而非"已 KILLED"；KILLED 落定后 wf 级三个非终态 driver 入口守卫（start :177-180 / suspend :646-650 / resume :671-672）读同一内存记录全部拦截，live code 无会话内终态回退驱动路径；场景 B 归 C1-002 族。零守卫实际暴露面=防御纵深缺失，对称 C1-008 校准降 P3 | 新发现（对 C1-002 的事实修订经复核采纳，具约束力） |

### 不确定项（显式列出）

1. C1-007 现状句称类头不变式注释"列举的可变字段中不含 stepCleanups 的跨线程约定"——实际 `TaskStepRuntimeImpl.java:27` 已列名 stepCleanups（纳入"写必须发生在 step 创建线程或经 future 链同步点"的一般约束）；发现的实质（synchronized 签名暗示跨线程安全、与未同步 drain 矛盾）不受影响，判级不变。
2. C1-009"窗口=整个 resume 执行期"表述偏宽：有效窗为最后一个 cancel-token 检查点（TaskStepExecution.java:271）至下一挂起转移完成；若 T1 到达步骤边界检查则取消经 `NopTaskCancelledException`→`driveTaskKilled` 幂等放行正确收敛。多级挂起任务（审批/编排常态）下窗口仍自然命中，P1 维持。
3. C1-010 场景 A 唯一残留的间接路径：listener 经 `delayExecute` 排队的命令在 doEndWorkflow(KILLED) 之后 drain，但当前无任何 live driver 会经该路径写非终态 wf 状态（saveWfRecord 6 调用点穷尽核对）——若未来新增 wf 级驱动，零守卫即暴露，此为降 P3 而非驳回的原因。

### 修复耦合提示（移交修复阶段）

- C1-001 与 C1-009 为同一对写者（TaskImpl.java:159 SUSPENDED × TaskRuntimeImpl.cancel）的两种相反交错，必须作为**同一原子修复**处理：SUSPENDED 转移纳入 `synchronized(taskState)` + 终态前置检查 + resume 非终态确认后显式重置 ACTIVE 落盘 + （可选）`TaskStateBean.setTaskStatus` 补 task 级终态拦截；修复不得回归四个终态 driver（TaskImpl.java:186-192/202-208/237-243/252-258）的既有监视器语义，并连带裁定 round3 §2.4-1 的"挂起任务被 timeout-cancel 记 KILLED 而非 TIMEOUT"分类问题。
- C1-010 降级后仍约束 C1-002 的修复覆盖面：若采用占位条件更新方案，wf 级须覆盖 `saveWfRecord` 全部 6 个调用点，不能只处理 step 级。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|------|-------------|---------|-----------|------|
| C1-001 | P1 | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:154-161` | task 级 SUSPENDED 转移游离于 taskState 监视器外，与 cancel 的 KILLED 驱动构成非原子 check-then-act——kill/超时取消在交错窗内被静默丢失 | 新发现 |
| C1-002 | P2 | `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1210-1229` | wf 同 step 实例并发 action 的"状态检查→执行→落盘"非原子——业务副作用先于乐观锁保护执行，双击/并发提交可重复执行 action source（失效面按 C1-010 修订） | 新发现 |
| C1-003 | P2 | `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowImpl.java:41-115` | executing/commandQueue 无同步原语，"同实例延迟执行"的线程封闭前提未声明（当前每调用新建实例，理论风险） | 新发现 |
| C1-004 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java:46-63,188-195` | step 级 first-terminal-wins 守卫为无锁 check-then-act——残余 KILLED/EXPIRED 行携带 resultValue 的混合状态窗口（跨线程写对未在 live code 证实） | 新发现 |
| C1-005 | P2 | `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:122-143` | remindCount 读-改-写无守卫、扫描入口无重叠/多节点互斥——提醒计数丢失更新，dueAction 双触发仅靠引擎状态码容错 | 新发现 |
| C1-006 | P2 | `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:158-261` | saveTaskState 不在条带锁保护内（与 saveStepState 不对称）——跨 runtime 并发终态保存仅 DB 乐观锁收敛，失败以裸 ORM 错误码冒泡且内存态漂移 | 新发现 |
| C1-007 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskStepRuntimeImpl.java:53,180-195` | addStepCleanup（synchronized）与 runStepCleanups（非同步 drain 同一 ArrayList）锁语义不对称，且 addStepCleanup 生产零调用（消费侧 live，公开扩展点） | 新发现 |
| C1-008 | P3 | `nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStateBean.java:145-155` | taskVars 懒初始化 LinkedHashMap 且 setTaskVar 无同步——零生产调用方的防御性缺口 | 新发现 |
| C1-009 | P1 | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:154-161`（+ `TaskRuntimeImpl.java:94-105`、`TaskStateBean.java:100-103`） | resume 执行期间 cancel 驱动的 KILLED 可被后续 SUSPENDED 裸覆写（resume 无 ACTIVE 重置 + suspend 分支无终态守卫）——显式 kill/超时后任务复活且可再次 resume | 新发现 |
| C1-010 | P3（降级） | `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/entity/NopWfInstance.java:36-51`（+ `WorkflowRecordBean.java:115-117`） | wf 级 transitToStatus 零回退守卫（2/4 实现零守卫，修订 C1-002"4 处一致"陈述）——live code 无会话内终态回退驱动路径，属防御纵深缺失 | 新发现（含经复核采纳的 C1-002 事实修订） |

**保留/降级/驳回计数**：保留 9（C1-001/002/003/004/005/006/007/008/009 维持原判级）、降级 1（C1-010：P2→P3）、驳回 0。最终保留项合计 10 条：P1×2（C1-001/009）、P2×6（C1-002/003/004/005/006/007）、P3×2（C1-008/010）。

**需子项复核的高风险项**：C1-001 + C1-009（同一对写者的两种相反交错，修复方案耦合——监视器纳入、终态守卫、resume ACTIVE 重置必须一并设计并回归四个既有终态 driver 语义，修复若引入新同步结构须由 C2 维度复核锁序）；其余条目无子项复核需求。
