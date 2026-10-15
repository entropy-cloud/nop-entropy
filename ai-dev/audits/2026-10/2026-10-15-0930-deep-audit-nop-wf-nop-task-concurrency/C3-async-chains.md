# C3 异步链路正确性 — nop-wf + nop-task 第 1 轮初审（盲审）

- 审计单元：`nop-wf`（10 jar 子模块）+ `nop-task`（10 jar 子模块），379 files / 36,107 lines
- 审计维度：C3 异步链路正确性（异常传播 / 取消与超时 / 上下文传播 / 时序假设 / double-subscribe 幂等 / F1·F2 对照）
- 仓库：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- 盲审纪律：未读取 `ai-dev/audits|plans|bugs|lessons/` 下任何既有文件；仅审当前 live code；`_` 前缀生成文件不作发现对象。
- 审计日期：2026-10-15

---

## 检查范围说明（异步入口清单）

### grep 基线对照

`_tmp/cra-baseline-nop-wf-nop-task.txt` 的 `== C3: async chains ==` 节：nop-wf 侧 **零命中**（grep 模式未覆盖 `CompletionStage` 方法签名声明），nop-task 侧命中 17 个文件。本轮人工全量核对后，实际异步面如下（基线缺口的补充均已纳入下述清单）。

### nop-task 异步入口（全量盘点）

| 入口 | 文件 | 说明 |
|------|------|------|
| step 返回值异步包装 | `nop-task-core/.../TaskStepReturn.java` | `ASYNC/ASYNC_RETURN/thenCompose/thenApply/whenComplete`，`hookFuture` 压平嵌套异步 |
| 线程池提交 | `nop-task-core/.../step/ExecutorTaskStepWrapper.java` | `IThreadPoolExecutor.submit`（`<step executor="...">`） |
| 上下文重投递 | `nop-task-core/.../step/RunOnContextTaskStepWrapper.java` | `context.execute(...)`（`runOnContext="true"`，缺省 false） |
| 定时器 | `nop-task-core/.../step/DelayTaskStep.java`、`utils/TaskStepHelper.timeout(...)`（timer + timeoutGuard 竞速）、`TaskStepHelper.retry` 的 `schedule(action, delay)` | 均落 `TaskFlowManagerImpl.getScheduledExecutor()` → `GlobalExecutors.globalTimer()` |
| 聚合 | `step/GraphTaskStep.java`、`ParallelTaskStep.java`、`AbstractForkTaskStep.java`（fork/fork-n） | `AsyncHelper.waitAsync(promises, joinType)` promise 聚合 |
| 组合遍历 | `step/SequentialTaskStep.java`、`SelectorTaskStep.java`、`LoopTaskStep.java`、`LoopNTaskStep.java` | 异步完成值经 `thenApply/thenCompose` 续延 `execute(stepRt)` |
| 包装层 | `TryTaskStepWrapper`、`BuildOutputTaskStepWrapper`、`TimeoutTaskStepWrapper`、`ThrottleTaskStepWrapper`、`RateLimitTaskStepWrapper`、`SyncTaskStepWrapper`、`RetryTaskStepWrapper`（builder 装配序见 `builder/TaskStepEnhancer.wrap`） |
| task 终态 driver | `impl/TaskImpl.java`（COMPLETED/FAILED/KILLED/TIMEOUT 四 driver，`synchronized(taskState)` 原子序列） |
| 外部事务异步 | `nop-task-ext/.../dao/TransactionalTaskStepWrapper` → `ITransactionTemplate.runInTransactionAsync`（platform 设施，用法正确） |
| 队列 | `nop-task-queue/ITaskQueue.java`（纯接口，无实现，无异步面） |

### nop-wf 异步入口（全量盘点）

| 入口 | 文件 | 说明 |
|------|------|------|
| `*Async` API 面 | `nop-wf-api/.../WorkflowService.java`、`nop-wf-core/.../WorkflowServiceSpi.java`、`service/impl/WorkflowServiceImpl.java` | 8 个 `CompletionStage` 方法，全部经 `DefaultWorkflowExecutor.execute` **同步执行**后以 `FutureHelper.toCompletionStage` 包装为已完成 stage——nop-wf 引擎本体无真实异步执行 |
| 引擎异步形状 | `engine/WorkflowEngineImpl.java` | `runSource` 的 `FutureHelper.tryResolve`（未决 future 透传，见 C3-008）；`WfRuntime.delayExecute`（对象内命令队列延迟执行，见 C3-010） |
| 定时任务 | `nop-wf-scheduler/.../WfTaskScanner.java` | 经 `nop/job/conf/wf-due-task-scan.job.yaml`（cron 缺省 0 0/1 * * * ?）与 `wf-remind-task-scan.job.yaml` 驱动；`FutureHelper.syncGet(workflowService.invokeActionAsync(...))` 阻塞消费 |
| step retry | `WorkflowEngineImpl.runSourceWithRetry` | `Thread.sleep(delay)` 同步阻塞重试（见 C3-007） |
| 消息监听器 | 无（nop-wf/nop-task 各模块 grep 无 MQ listener） | — |

### 已声明契约对照（不立案项）

依据 `docs-for-ai/03-modules/nop-task.md`：
1. **[03-06] parallel/fork/fork-n/graph 只做 promise 聚合、子步骤缺省内联串行**——"parallel 无 executor 不加速"不立案。
2. **first-terminal-wins**（plan 364 [05-01/02-12]）：task 级监视器原子序列已核实（`TaskImpl.driveTask*` 的 `synchronized(taskState)`）；step 级 guard 式守卫的残留窗口见 C3-006。
3. **步骤业务副作用与终态保存无事务原子性、step body 须幂等**——幽灵任务的业务副作用延续（C3-005 的副作用部分）按已声明边界不单独立案，C3-005 仅立"引擎续延仍在幽灵线程写引擎状态/scope"部分。
4. **fork/fork-n 分支共享 stepPath 行、跨进程 DB-resume 不可靠**——已裁定暂缓，不立案；进程内 `DaoTaskStateStore` 条带锁已核实存在（`saveStepState` synchronized(rowLock)）。
5. **suspend 传播、kill 对 SUSPENDED 任务直驱 KILLED、join=ANY_* 挂起检测的最小语义**——均已声明，不立案。

---

## 发现条目

### [nop-wf-nop-task-C3-001]
- **字段1 发现ID**：nop-wf-nop-task-C3-001
- **字段2 严重度**：P1
- **字段3 维度/族**：C3-时序假设（依赖"先判组完成、后迁移"顺序但无跨请求同步保证）；伴随 F2 半个成功（两请求各自"成功"退出自身步骤，组迁移整体丢失）
- **字段4 位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowManagerImpl.java:81-87`；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1256-1265`（组完成判定→doTransition 时序）；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/ExecGroupSupport.java:27-33`
- **字段5 证据片段**：
```java
// WorkflowManagerImpl:81 —— 每次调用新建 WorkflowImpl，步骤快照随实例固化
public IWorkflow getWorkflow(String wfId) {
    ...
    return new WorkflowImpl(workflowEngine, workflowStore, workflowCoordinator, wfModel, wfRecord);
}
// WorkflowEngineImpl:1257 —— 组完成判定读同组兄弟步骤状态后才 doTransition
boolean groupComplete = isExecGroupComplete(step, wfRt);
if (!groupComplete) { shouldTransition = false; }
// ExecGroupSupport:32 —— AND/SEQ 组：兄弟步骤未全部结束即不完整
return step.getStepsInSameExecGroup(false, false).isEmpty();
```
- **字段6 触发时序**：AND 组（execGroupType=and-group）含步骤 S1、S2，用户 A、B 几乎同时分别对 S1、S2 提交 agree。T1、T2 各自 `getWorkflow(wfId)` 得到**不同** WorkflowImpl，各自加载的步骤快照互不可见对方未提交变更。T1 判组：S2 在 T1 快照中仍 ACTIVATED → `groupComplete=false` → 跳过 doTransition，仅 `doExitStep(S1, COMPLETED)`；T2 对称地跳过迁移、退出 S2。两事务写的是**不同的 step 行**，乐观锁无冲突，双双提交成功。
- **字段7 影响**：组内全部步骤已 COMPLETED（history），但组迁移（下游步骤创建/流程推进）永久丢失；`checkEnd` 经 `wfRt.delayExecute` 执行时同样看不到未迁移的组出口。除非模型另有补偿或有人对同 wf 再触发任一动作（`runAutoTransitionsWithLimit` 只遍历活跃步骤，组内步骤已 history，不再补救），流程**永久滞留**在中间态，审批语义上"两个人都批了却没有产生下一步"。
- **字段8 根因**：组完成判定（读快照）与组迁移（写库）之间无跨请求同步原语；每请求新建 WorkflowImpl 使 `delayExecute/executeNow` 的对象内串行化对"同一工作流实例的并发请求"完全失效（代码注释"如果当前有同一工作流实例相关的任务在执行，则会放入执行队列"描述的是对象级而非实例级语义）。
- **字段9 维度族对照**：F2（两个半成功合成为一个整体失败）；非 F1（无撕裂读，各自快照一致）。
- **字段10 修复方向**：对 wfInstanceId 做进程内条带锁（对齐 `DaoTaskStateStore.rowLock` 模式）串行化 `DefaultWorkflowExecutor.execute`；或组完成判定改为迁移前在写锁内重读兄弟步骤最新已提交状态（re-check under lock）；跨进程需 DB 级 `SELECT ... FOR UPDATE` 于 wf_instance 行。
- **字段11 误报校准**：非无状态单例误报——竞态对象是 per-request 的步骤快照与共享 DB 行；OR_GROUP/VOTE_GROUP 因 OR 语义或 `member == step` 特判，单侧即可判 complete，受影响面主要是 and-group/seq-group（seq 组表现为乱序激活下一序号，`activateNextSeqStep` 可能被双侧各触发一次的相邻变体）。
- **字段12 置信度**：高（判定代码、快照来源、提交路径均已核实；未做并发复现实验）。
- **字段13 备注**：本条同时符合维度集外 C9（状态一致性竞态）形态；已按 C3 主发现立案，不再重复登记 CL。
- **复核状态**: 已保留（P1。复核属实：getWorkflow 每请求新建 WorkflowImpl（WorkflowManagerImpl.java:87）、组完成判定经 ExecGroupSupport.shouldExecGroupComplete:30 读事务内 ORM 步骤集（WorkflowStepImpl:329-340）、写点仅自身步骤行故乐观锁无冲突可依、vote-group 修正成立（isVoteGroupComplete:52 仅计 member==step 与快照已完成者，多数决/一致通过配置并发双投均判不完整）、无补救出口核实（runAutoTransitions:815 仅遍历 ACTIVATED、due/remind 扫描 status=ACTIVATED 过滤）；引文行号 1256-1265 实为 1264-1275，漂移不影响成立性。现状：新发现——历史审计（check2/nop-wf.md、2026-09 各审计）均无此竞态记录，本 campaign 无修复提交）

### [nop-wf-nop-task-C3-002]
- **字段1 发现ID**：nop-wf-nop-task-C3-002
- **字段2 严重度**：P1
- **字段3 维度/族**：C3-取消/超时语义之资源占有 + 自建线程提交；可达死锁（配置依赖，非主路径缺省）
- **字段4 位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:117-141`（wrap 装配序：executor 先、sync 后）；`nop-task/nop-task-core/src/main/java/io/nop/task/step/SyncTaskStepWrapper.java:15-18`；`nop-task/nop-task-core/src/main/java/io/nop/task/step/ExecutorTaskStepWrapper.java:33-52`
- **字段5 证据片段**：
```java
// TaskStepEnhancer.wrap —— sync 包装在 executor 外层：调用线程阻塞等待池内任务
if (!StringHelper.isEmpty(stepModel.getExecutor())) {
    step = new ExecutorTaskStepWrapper(step, stepModel.getExecutor());
}
...
if (stepModel.isSync())
    step = new SyncTaskStepWrapper(step);
// SyncTaskStepWrapper —— 无超时、不检查 cancel token 的阻塞等待
public TaskStepReturn execute(ITaskStepRuntime stepRt) {
    return getTaskStep().execute(stepRt).sync();   // FutureHelper.syncGet → 阻塞
}
```
- **字段6 触发时序**：有界线程池 X（`GlobalExecutors` 命名 executor 或 bean，maxThreads=N）。父步骤 A 配 `executor="X"` 且 `sync="true"`，其 body 内步骤 B 亦配 `executor="X"`。并发提交 N 个 A 实例 → N 个池线程各自执行 A 的 body、各自在 `.sync()` 上阻塞等待自己派生的 B；N 个 B 全部在 X 队列中排队，无空闲线程可执行 → 全体永久互等，池耗尽且无超时出口（`.sync()` 无 timeout 参数，cancel token 不参与唤醒阻塞中的 syncGet）。
- **字段7 影响**：参与互等的所有任务实例永久挂死（终态 driver 永不驱动，任务停留 ACTIVE），池线程全部占用后**同池所有其他任务**亦被殃及；因 syncGet 阻塞不响应取消，kill 也无法解围（token listener 只能 force-complete future，解除不了已入 condition 等待的线程——见 `ExecutorTaskStepWrapper` 取消兜底只 complete `ret`，不中断 future 所在线程的阻塞等待者）。
- **字段8 根因**：`sync=true` 与 `executor` 组合未做构建期互斥/告警；`SyncTaskStepWrapper` 的阻塞等待无超时、无取消响应点。
- **字段9 维度族对照**：F9 不涉及（无吞异常）；属"可达死锁"判级的配置依赖变体。
- **字段10 修复方向**：构建期拒绝 `sync && executor` 组合（对齐 plan 364 [02-06] 对非法可靠性参数的硬失败先例），或 `syncGet` 带超时并接入 cancel token。
- **字段11 误报校准**：非"平台设施自身实现"（装配序与阻塞点是 task 模块自有代码）；判 P1 而非 P0 因需要特定模型配置 + 池饱和两个条件叠加，缺省主路径（无 executor）不触发。
- **字段12 置信度**：中高（装配序与阻塞语义已核实；未复现实际死锁）。
- **字段13 备注**：`TaskStepReturn.sync()` → `ContextProvider.getOrCreateContext().syncGet(future)`（`BaseContext.taskQueue.syncGet`），非 context 主线程时为条件等待阻塞，已核实无内联执行捷径。
- **复核状态**: 已保留（P1。装配序 executor(:118-120) 在内、sync(:159-160) 在外、`.sync()`→FutureHelper.syncGet 无超时无阻塞唤醒点、构建期无 sync+executor 互斥，均属实；两点校正：①字段6 叙述中阻塞者是 sync 步骤的调用线程（饱和场景中为同池父步骤续延所在的池线程），机制不变；②字段7"kill 也无法解围"过强——ExecutorTaskStepWrapper:59-66 取消兜底 complete ret 可唤醒 syncGet 等待者，但平台无任务级 kill/cancel API（round2 §5 已核），无外部取消源时滞留实际永久；与 C4-002 为同点不同视角（池治理面），判定自洽可归并，建议 7.1 合并处置。现状：新发现）

### [nop-wf-nop-task-C3-003]
- **字段1 发现ID**：nop-wf-nop-task-C3-003
- **字段2 严重度**：P2
- **字段3 维度/族**：C3-时序假设 / double-subscribe 幂等（图节点双调度）；F2 邻接（body 双跑=业务副作用重复）
- **字段4 位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:194-244`（wait 调度循环 + enter 直跑循环）；`nop-task/nop-task-core/src/main/java/io/nop/task/builder/GraphStepAnalyzer.java:64-92`（校验集）；`nop-task/nop-task-core/src/main/java/io/nop/task/builder/GraphStepBuilder.java:19-22`
- **字段5 证据片段**：
```java
// GraphTaskStep.execute —— 两处独立调用 runStep，无"已执行"去重
for (GraphStepNode node : nodes) {
    if (node.getWaitCount() <= 0) continue;
    ... waitFuture.whenComplete((ret, err) -> { ... runStep(node, ...); });
}
...
for (GraphStepNode node : nodes) {
    if (!node.isEnter()) continue;
    runStep(node, stepRt, cancellable, future, stepFutures, ...);   // enter 直跑
}
// GraphStepAnalyzer 只校验：重名/enter非空/exit非空/未知步骤/含环；未校验 enter ∩ waitSteps
```
- **字段6 触发时序**：模型把步骤 X 同时列入 `enterSteps` 且让某步骤 `next="X"`（或显式 `waitSteps` 含 X 的依赖）——`GraphStepAnalyzer.normalizeWaitSteps:70-73` 会由 `next` **自动**给 X 追加 waitStep，校验不拦截。执行期：enter 循环先同步 `runStep(X)`（body 执行、`stepFuture.complete(null)` 级联）；其后 X 的 waitFuture 完成再次 `runStep(X)` → `executeAsync` 二次执行 body，`stepResults.put(X, result)` 覆写首次结果（`isFirstInstantiation` 第二次为 false，走全新 state，continuation-skip 不拦）。
- **字段7 影响**：X 的业务副作用执行两次（外部写、DB 写、消息发送等）；图级结果取后到完成值，`STEP_RESULTS.x` 首值被覆写；若 X 是 exit 节点，首次完成已终结全图，二次执行成为无主幽灵工作。
- **字段8 根因**：构建期校验缺口（enter 与 wait 语义互斥未建模）；`runStep` 无按节点一次性调度守卫。
- **字段9 维度族对照**：F2 邻接；F3 不涉及。
- **字段10 修复方向**：`GraphStepAnalyzer` 增加 `enterSteps ∩ (waitSteps∪waitErrorSteps) ≠ ∅` 硬失败；或 `runStep` 入口检查 `stepFuture.isDone()` 直接跳过二次调度。
- **字段11 误报校准**：非缺陷误报候选——正常模型不会这样配，但 `next` 自动派生 wait 使"看似冗余实则有害"的配置门槛很低，属边界配置缺陷（P2 判级依据）。
- **字段12 置信度**：高（代码路径确定，双执行无任何守卫）。
- **字段13 备注**：二次执行的 `newStepRuntime` 因 `isFirstInstantiation=false` 用全新 state，不会误触 continuation-skip——即引擎对"同 stepPath 二次执行"毫无防线。
- **复核状态**: 已保留（P2。wait 循环（GraphTaskStep:202-224）与 enter 直跑循环（:228-234）双调度、runStep 无一次性守卫（:248-350 仅检查取消与 errorTriggerFired）、normalizeWaitSteps:93-103 由 next 自动派生 waitStep、校验集（initDag:46-91）无 enter∩wait 拦截，均逐行属实；一处弱化：exit 节点首次完成后 cancellable.cancel(:343) 使二次 runStep 于 :255 早退，仅剩并发窗口残留，非 exit 节点的双执行与结果覆写则无条件成立，不影响 P2。现状：新发现）

### [nop-wf-nop-task-C3-004]
- **字段1 发现ID**：nop-wf-nop-task-C3-004
- **字段2 严重度**：P2
- **字段3 维度/族**：C3-上下文传播（异步边界未传递 IContext/ThreadLocal 依赖）
- **字段4 位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/step/ExecutorTaskStepWrapper.java:33-36`；`nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:126-128`；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:140`（`runOnContext="!boolean=false"`）
- **字段5 证据片段**：
```java
// ExecutorTaskStepWrapper —— 池线程直跑 body，无 context 重投递
CompletableFuture<?> future = executor.submit(() -> {
    try {
        TaskStepReturn result = getTaskStep().execute(stepRt);
// RunOnContextTaskStepWrapper —— 显式重投递仅当 runOnContext=true
context.execute(() -> { TaskStepReturn result = getTaskStep().execute(stepRt); ... });
```
- **字段6 触发时序**：步骤配 `executor="pool"`（真实并行，owner doc [03-06] 唯一指定方式）但未配 `runOnContext="true"`（xdef 缺省 false）→ body 在池线程执行，`BaseContextProvider` ThreadLocal 为空/为其他请求上下文。body 内任何经 `ContextProvider.currentContext()` / `currentLocale()` / `currentUser()` 取值的代码（XLib 函数、日志脱敏、租户过滤钩子等）读到 null 或**串号到其他线程残留的上下文**。
- **字段7 影响**：并行步骤体内隐式依赖 ThreadLocal 的能力静默失效或串号；与 `DelayTaskStep`/`retry` 延迟路径（续延在 globalTimer 线程）同类。`ITaskRuntime.getLocale()` 在 `svcCtx==null`（编程式 `newTaskRuntime(task,false,null)`）时回退 `ContextProvider.currentLocale()`，异步线程上必错（`ITaskRuntime.java:46-50`）。
- **字段8 根因**：引擎把"是否重投递上下文"交给逐步骤 opt-in 开关，而 `executor`（唯一并行手段）的常用配置路径未提示该耦合； captured context 实际就存在 `TaskRuntimeImpl.context`（构造时已捕获，`TaskRuntimeImpl.java:77`），`thenOnContext` 设施（`TaskStepReturn.thenOnContext:287`）全仓**零调用方**。
- **字段9 维度族对照**：非 F1/F2/F9；C3-4 上下文传播主缺陷。
- **字段10 修复方向**：`ExecutorTaskStepWrapper.submit` 内默认以 `stepRt.getContext().execute(...)`（或 `ContextProvider.thenOnContext`）包裹 body，保留 `runOnContext=false` 作为显式退出；至少在 owner doc [03-06] 补"context 需 runOnContext=true"的强提示。
- **字段11 误报校准**：runOnContext 机制在 xdef 有文档（"指定本步骤中的操作都使用IContext.runOnContext来执行"），属**部分声明**——判 P2 而非 P1；平台业务层主模式是显式传 `IServiceContext`（`stepRt.getSvcCtx()`），纯显式传参的 step body 不受影响。
- **字段12 置信度**：中（机制确定；实际损害面取决于用户 step 体内对 ThreadLocal 的依赖程度）。
- **字段13 备注**：理论风险可具体化到代码路径（currentContext 读点），非纯推测。
- **复核状态**: 已保留（P2。ExecutorTaskStepWrapper 池线程直跑 body 无上下文重投递、RunOnContextTaskStepWrapper 仅 runOnContext=true 生效且 xdef 缺省 false（task.xdef:140）、ITaskRuntime.getLocale 在 svcCtx==null 时回退 ThreadLocal currentLocale（实际 :44-51）、thenOnContext 生产代码零调用方（grep 核实仅定义与测试），均属实；xdef 已文档化 runOnContext 的"部分声明"降档校准成立。现状：新发现）

### [nop-wf-nop-task-C3-005]
- **字段1 发现ID**：nop-wf-nop-task-C3-005
- **字段2 严重度**：P2
- **字段3 维度/族**：C3-取消/超时语义（orTimeout 幽灵任务；超时后资源/状态收尾）；F1/F2 边界对照见字段9
- **字段4 位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:184-244`（timeout 竞速）；`nop-task/nop-task-core/src/main/java/io/nop/task/step/TimeoutTaskStepWrapper.java:20-29`；`nop-task/nop-task-core/src/main/java/io/nop/task/step/BuildOutputTaskStepWrapper.java:32-58`
- **字段5 证据片段**：
```java
// TaskStepHelper.timeout —— 竞速只保证"返回值在超时时刻终结"，不停止被包装步骤
Future<?> timer = executor.schedule(() -> {
    cancellable.cancel(ICancellable.CANCEL_REASON_TIMEOUT);
    timeoutGuard.completeExceptionally(NopTaskCancelledException.forReason(...));
    return null;
}, timeout, TimeUnit.MILLISECONDS);
...
result.getReturnPromise().whenComplete((v, e) -> {   // 幽灵完成后仍会走到这里（winner 已 done，no-op）
    if (e != null) winner.completeExceptionally(e); else winner.complete(v);
});
// BuildOutputTaskStepWrapper.thenApply —— 超时后幽灵线程仍执行 output 求值与 scope 写
stepRt.setValue(TaskConstants.VAR_STEP_RESULT, res.getOutputs());
```
- **字段6 触发时序**：步骤配 `timeout=100`、body 为 executor 异步步骤实际耗时 10s。t=100ms：timer 触发 → winner 以 TIMEOUT 取消异常终结 → 外层 `driveStepFailure` 走 EXPIRED/KILLED driver、task seam 驱动 TIMEOUT 终态并落盘，**主流程已收尾**。t=10s：幽灵 body 完成 → `cancellable.cancel(TIMEOUT)` 已把内层 token 置取消，但若 body 不检查 token，其返回 promise 照常完成 → 内层包装链（BuildOutput 的 `thenApply`、retry 的 `doRetry`）在幽灵线程继续执行：`stepRt.setValue` 写 scope、retry 成功路径调 `state.succeed`（被 isDone 守卫 no-op）与 `saveState`（写 DB 行）。
- **字段7 影响**：超时报告后仍有一个存活线程持续消耗 CPU/连接并写引擎 scope 与状态行；与并行兄弟分支（parallel join=ANY 场景）构成对同一 eval scope 的双线程并发访问（scope 的 `concurrent` 缺省 false=普通 HashMap，见 task.xdef:137）。终态本身受 first-terminal-wins 保护不被覆写。
- **字段8 根因**：取消语义是协作式（token 检查点），引擎对不响应 token 的 body 无中断/隔离手段；竞速 winner 与被竞速 promise 之间无生命周期绑定。
- **字段9 维度族对照**：业务副作用延续部分 = 已声明边界（副作用与终态无原子性，step body 须幂等），不重复立案；本条立"幽灵续延写引擎状态/scope"与"无中断响应点"部分。非 F1（无活引用撕裂——scope 写为最后写者胜）。
- **字段10 修复方向**：timeout 触发时对 executor 提交的底层 `Future` 施加 `cancel(true)`（`FutureHelper.bindCancelToken` 已有该能力，`ExecutorTaskStepWrapper:67` 仅绑定了 ret 而非中断语义传导到 body 内层 promise 链）；或在文档强化"timeout 不终止 body"契约。
- **字段11 误报校准**：无中断是协作式取消的常见设计；判 P2 因损害为资源占用 + 窄窗 scope 竞态，终态一致性已由守卫兜住。
- **字段12 置信度**：高（竞速与续延路径逐行核实）。
- **字段13 备注**：`withCancellable` 完成时的 `cancellable.cancel(CANCEL_REASON_KILL)`（TaskStepHelper:376）同样只置 token，不停止在飞 body——全引擎取消语义一致地无中断点。
- **复核状态**: 已保留（P2。timeout 竞速（TaskStepHelper:184-244，timer:199-204、幽灵 promise whenComplete:228-233）、幽灵续延写 scope（BuildOutputTaskStepWrapper:38-40 stepRt.setValue 在幽灵完成线程执行）、withCancellable 完成时 cancel（:376 区）与全引擎协作式取消无中断点，均逐行属实；终态受 first-terminal-wins 保护、损害限资源占用+窄窗 scope 竞态的判级校准成立。现状：新发现）

### [nop-wf-nop-task-C3-006]
- **字段1 发现ID**：nop-wf-nop-task-C3-006
- **字段2 严重度**：P2
- **字段3 维度/族**：C3-时序假设（step 级终态 driver 并发收敛）；plan 364 [05-01] 已修机制的残留窗口
- **字段4 位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java:46-63`（succeed/fail guard）、`:189-194`（setStepStatus）；对照 `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:186-192`（task 级 `synchronized(taskState)`）；`step/TaskStepExecution.java:380-407`（driveStepFailure 无监视器）
- **字段5 证据片段**：
```java
// TaskStepStateBean —— guard 是 check-then-act，非原子
public void succeed(Object result, String nextStepId, ITaskRuntime taskRt) {
    if (isDone()) return;                 // (1) 检查
    setResultValue(result);
    setStepStatus(TASK_STEP_STATUS_COMPLETED);  // (2) 写入，setStepStatus 内部再查一次
}
public void fail(Throwable exception, ITaskRuntime taskRt) {
    if (isDone()) return;
    exception(exception);                 // 不经 setStepStatus，无二次校验
}
```
- **字段6 触发时序**：step-timeout timer 线程与正常完成回调线程并发。T1（成功回调）`succeed()`：isDone=false 通过；T2（超时 driver，`driveStepFailure` cancel 分支）`fail()`：isDone=false 通过 → T2 `exception(e)` 写入；T1 `setStepStatus(COMPLETED)`（isDone 仍 false）→ COMPLETED 胜出；T2 `setStepStatus(EXPIRED)`：isDone=true 且 EXPIRED≠COMPLETED → 被忽略。终态收敛正确，但 **COMPLETED 行携带了 exception**。
- **字段7 影响**：`copyStepStateToEntity` 按 `state.exception()!=null` 持久化 errCode/errMsg/errorBeanData → DB 中出现 status=COMPLETED 且 errorBeanData 非空的步骤行；resume 读侧 `isSuccess()` 命中后仅用缓存 result，不消费 exception——功能无损，但诊断口径自相矛盾（展示层/审计读数会呈现"已成功但有错误"）。
- **字段8 根因**：plan 364 [05-01/02-12] 为 task 级 driver 选了监视器原子序列、为 step 级选了非原子 guard；`fail()` 的 `exception()` 写不在任何校验之后。
- **字段9 维度族对照**：非 F9（未吞异常）；属竞态残留（可给出具体时序但后果限于诊断字段不一致，故按纪律不降 P3、不足 P1）。
- **字段10 修复方向**：step 级对齐 task 级——`TaskStepExecution.driveStepFailure` 与成功路径收敛到 `synchronized (stepState)` 原子序列；或 `fail()` 在写入前于同一锁内复查 isDone。
- **字段11 误报校准**：非"有界声明结构"误报——窗口真实存在；但触发需同一步骤的完成与取消几乎同刻，属罕见竞态。
- **字段12 置信度**：中高。
- **字段13 备注**：`TaskImpl.skipTerminalOverwrite`（task 级）在监视器内复查，无此问题——两层级修复方式不对称是本条的可指认偏差。
- **复核状态**: 已保留（P2。succeed/fail check-then-act 非原子（TaskStepStateBean:47-63，fail 的 exception() 写无二次校验）、setStepStatus 守卫（:186-194）、driveStepFailure 无监视器（TaskStepExecution:378-407）与 task 级 synchronized(taskState)+监视器内 skipTerminalOverwrite（TaskImpl:186-192）的层级不对称，均逐行属实；"COMPLETED 行携带 exception"的交错时序成立，后果限诊断字段一致性的判级恰当。现状：新发现——plan 364 [05-01] 修的是 task 级监视器，step 级残留此前无记录）

### [nop-wf-nop-task-C3-007]
- **字段1 发现ID**：nop-wf-nop-task-C3-007
- **字段2 严重度**：P2
- **字段3 维度/族**：C3-取消/超时语义（无取消响应点的同步等待）+ 资源占有
- **字段4 位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:884-912`（runSourceWithRetry）；`:915-929`（computeRetryDelay）
- **字段5 证据片段**：
```java
long delay = computeRetryDelay(retry, attempt);
if (delay > 0) {
    try {
        Thread.sleep(delay);            // 请求线程原语级阻塞
    } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw e;                         // 中断放弃重试（可取），但引擎无任何 cancel 源
    }
}
// computeRetryDelay —— 仅 exponentialDelay 分支应用 maxRetryDelay 封顶
long delay = retry.getRetryDelay();
if (delay <= 0) return 0;
if (retry.isExponentialDelay()) { ... Math.min(delay, maxRetryDelay); }
return delay;                            // 固定 delay：无封顶路径
```
- **字段6 触发时序**：wf 步骤配置 `<retry maxRetryCount="10" retryDelay="60000"/>`（固定间隔），source 持续失败 → 调用线程在 `WorkflowServiceImpl.invokeActionAsync`（实为同步）内累计阻塞约 10 分钟；该调用处于 `@BizMutation` 事务内 → DB 连接与行锁全程持有，期间 wf 实例行/步骤行对其他请求不可见提交。
- **字段7 影响**：长事务 + 连接占用（多次重试叠加可达分钟级）；重试期间不可取消（wf 引擎无 cancel token 概念，kill 只能等 sleep 自然结束且仅在下一轮检查生效）；`maxRetryDelay` 对固定 delay 不生效，模型无法表达"封顶"意图。
- **字段8 根因**：wf 引擎全同步设计下用 `Thread.sleep` 实现退避；`maxRetryDelay` 语义错误地只挂接在 exponential 分支。
- **字段9 维度族对照**：非 F9（中断路径正确传播原异常）；资源占有类。
- **字段10 修复方向**：`computeRetryDelay` 对固定 delay 同样应用 `maxRetryDelay` 封顶（对齐 task 侧 `IRetryPolicy.getRetryDelay` 语义）；长期看 wf 步骤重试应经 `scheduledExecutor` 异步退避或文档声明"retryDelay 须为小值"。
- **字段11 误报校准**：判 P2 非 P1：需要模型显式配置大 delay；缺省 retryDelay=0 时不 sleep。
- **字段12 置信度**：高（语义与调用链已核实）。
- **字段13 备注**：对照 task 引擎同能力（`TaskStepHelper.retry:268-284`）用的是 `scheduledExecutor.schedule` 非阻塞退避——同仓两种实现并存，wf 侧为弱者。
- **复核状态**: 已保留（P2。Thread.sleep（实际 :893）位于 @BizMutation 请求事务内、computeRetryDelay（实际 :922-933）仅 exponentialDelay 分支应用 maxRetryDelay 封顶、task 侧 RetryPolicy.getRetryDelay（RetryPolicy.java:158-185）对非指数延迟同样封顶（下限=tryDelay），均属实；引文行号 884-912/915-929 有约 8 行漂移，不影响成立性。现状：新发现）

### [nop-wf-nop-task-C3-008]
- **字段1 发现ID**：nop-wf-nop-task-C3-008
- **字段2 严重度**：P3
- **字段3 维度/族**：C3-异常传播/异步形状（未决 CompletionStage 作为值透传）
- **字段4 位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:866-868`（runSource）；`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/FutureHelper.java:177-193`（tryResolve）
- **字段5 证据片段**：
```java
protected Object runSource(WfStepModel stepModel, IWfRuntime wfRt) {
    return FutureHelper.tryResolve(runXpl(stepModel.getSource(), wfRt));
}
// FutureHelper.tryResolve —— 未决 future 原样返回（作为"值"）
if (future.getClass() == CompletableFuture.class) {
    CompletableFuture<?> promise = (CompletableFuture<?>) future;
    if (promise.isDone()) { return getFromFuture(promise); }
}
return value;   // 未决 CompletionStage 透传
```
- **字段6 触发时序**：用户在 wf 步骤 `<source>` 中返回未完成的 `CompletableFuture`（XPL 可调用返回 future 的 bean 方法）→ tryResolve 不等待，future 对象本身作为 action 返回值流入 `doInvokeAction` 的 result → 存入步骤输出/流程变量/待办展示，下游读到的永远是对象引用而非业务值；该 future 若最终异常完成，异常**无人消费**（F9 形态）。
- **字段7 影响**：值泄漏为不可序列化对象（JSON 序列化报错或输出空）、异步异常静默丢失；仅影响把 wf 步骤当异步用的高阶用法。
- **字段8 根因**：wf 引擎无异步语义但未对 source 返回值做 `CompletionStage` 拒绝校验（对照 task 侧 `TaskStepReturn.RETURN_RESULT` 的 `Guard.checkArgument(!(result instanceof CompletionStage), "not allow async result")`）。
- **字段9 维度族对照**：F9 邻接（未决 future 的异常无消费者）。
- **字段10 修复方向**：`runSource` 对 `CompletionStage` 返回值做 `Guard` 硬失败（对齐 task 侧先例），或文档声明 wf source 禁止返回 future。
- **字段11 误报校准**：非主路径；wf xdef 未声明异步支持，属边界形状缺口。
- **字段12 置信度**：中（透传路径确定；触发依赖用户 source 写法）。
- **字段13 备注**：task 侧同类场景已由 `TaskStepReturn` 全面压平（`hookFuture`），两者形成能力差，易误导跨引擎迁移的用户。
- **复核状态**: 已保留（P3。runSource（:866-868）→FutureHelper.tryResolve 对未决 CompletableFuture 原样透传（tryResolve 尾部 return value 已核），属实；wf xdef 未声明异步支持、边界形状缺口的 P3 校准成立。现状：新发现）

### [nop-wf-nop-task-C3-009]
- **字段1 发现ID**：nop-wf-nop-task-C3-009
- **字段2 严重度**：P3
- **字段3 维度/族**：C3-定时任务（扫描调度的批处理时序与并发触发）
- **字段4 位置**：`nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:64-120`；`nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/job/conf/wf-due-task-scan.job.yaml:1-12`
- **字段5 证据片段**：
```java
public void scanDueTasks() {
    ormTemplate.runInNewSession(session -> {     // 整批一个 session/事务
        scanDueTasksInCurrentSession();
        return null;
    });
}
...
FutureHelper.syncGet(workflowService.invokeActionAsync(request, null, newSchedulerContext()));
// 批内逐条串行执行 dueAction（任意业务逻辑），单条失败 catch 后继续（隔离正确）
```
- **字段6 触发时序**：cron 缺省每分钟触发；批次内某 dueAction 耗时长（业务逻辑 + 阻塞 syncGet）→ 单事务横跨整批持续数分钟；若执行时长超过 cron 间隔且调度器为并发触发策略，下一轮扫描与当前轮并发读同一批 due 步骤。
- **字段7 影响**：长事务持有连接；双触发时同一 due 步骤可能被两次 invokeAction——第二次被引擎 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` 拒绝并走 `skip-due-task-race` 分支（WfTaskScanner:97-103 已显式处理该竞态），状态一致性有引擎守卫兜底，残余影响为重复执行一次被拒动作的前置读放大与日志噪音。
- **字段8 根因**：批处理粒度=单 session 全批；任务定义未声明 disallow-concurrent（job.yaml 无互斥配置项消费迹象）。
- **字段9 维度族对照**：非 F1/F2/F9；双触发幂等性已由既有守卫声明覆盖。
- **字段10 修复方向**：dueAction 执行移出扫描事务（每条独立 runInNewSession），或 job 层声明互斥触发。
- **字段11 误报校准**：低频路径 + 守卫兜底，判 P3。
- **字段12 置信度**：中。
- **字段13 备注**：`scanRemindTasksInCurrentSession` 的提醒计数更新失败不落计数、下轮重试（:136-141 注释明确），幂等语义完好。
- **复核状态**: 已保留（P3。整批单 session（scanDueTasks:64-71 runInNewSession 包裹全批）、批内逐条 FutureHelper.syncGet 串行、ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS 的 skip-due-task-race 守卫在（:97-103 区）、job.yaml 无互斥配置且 enabled 缺省 false、cron 缺省每分钟，均属实；双触发由引擎守卫兜底、残余影响有限的 P3 校准成立。现状：新发现）

### [nop-wf-nop-task-C3-010]
- **字段1 发现ID**：nop-wf-nop-task-C3-010
- **字段2 严重度**：P3
- **字段3 维度/族**：C3-时序假设（延迟执行队列的实例归属契约与实现不符——文档性偏差）
- **字段4 位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WfRuntime.java:100-110`（javadoc）；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowImpl.java:80-110`（delayExecute/executeNow/commandQueue）；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowManagerImpl.java:81-87`
- **字段5 证据片段**：
```java
// WfRuntime.delayExecute javadoc：
/** 如果当前有同一工作流实例相关的任务在执行，则会放入执行队列，延迟执行 */
// WorkflowImpl —— executing/commandQueue 为普通实例字段（ArrayDeque/boolean），
// 串行化范围是"本 WorkflowImpl 对象"，而 getWorkflow(wfId) 每次调用都 new WorkflowImpl
private final Deque<Runnable> commandQueue = new ArrayDeque<>();
private boolean executing = false;
```
- **字段6 触发时序**：并发请求 R1、R2 同时操作同一 wfId → 各持独立 WorkflowImpl → R1 的 `executing=true` 对 R2 不可见 → 两请求的延迟命令（`checkEnd`、`checkWaitingJoinStep` 等）各自独立执行，"同一工作流实例的任务串行"承诺失效；单请求对象内语义（队列+drain+finally 复位）经逐行核验正确，无同对象跨线程共享路径（每请求独享实例），故不构成 C1/C2 数据竞争。
- **字段7 影响**：无直接缺陷后果（跨请求一致性由 C3-001 所述 DB 层保障/缺失）；风险在于后续开发者依据该 javadoc 认为"实例级已串行化"而省略外部同步——C3-001 即为该误解的现实化。
- **字段8 根因**：注释契约按 wfInstanceId 表述，实现按对象实例表述，两者在"每请求新建实例"的事实下不等价。
- **字段9 维度族对照**：不涉及 F1/F2/F9。
- **字段10 修复方向**：修正 javadoc 为对象级语义并指向 C3-001 的实例级互斥缺口；或真实引入按 wfId 的条带锁使契约成立。
- **字段11 误报校准**：明确不按 P1/P2 竞态立案——写不出同对象跨线程时序（实例不共享）。
- **字段12 置信度**：高（实例创建路径与字段修饰符已核实）。
- **字段13 备注**：附随小项：`ITaskRuntime.getLocale()`（`ITaskRuntime.java:46-50`）在 `svcCtx==null` 时回退 ThreadLocal `ContextProvider.currentLocale()`，异步续延线程上取错 locale——并入本条备注不单独立案。
- **复核状态**: 已保留（P3。javadoc 实例级表述（WfRuntime:100-104）、WorkflowImpl 对象级实现（commandQueue:46/executing:51/delayExecute:80-90）、WorkflowManagerImpl:87 每请求新建实例的事实链，均属实；"写不出同对象跨线程时序、按文档性偏差立案"的自我限定诚实，P3 恰当。现状：新发现）

---

## 维度集外线索

（本轮 C3 审计中未发现可具体化到 P0/P1 的 C6/C9 形态新线索；C3-001 兼具 C9 形态但已按 C3 主发现立案，依 overlay 规则不重复登记。）

---

## 覆盖度与残留边界

**已覆盖（人工全量核对，非仅 grep 基线）**：
- nop-task：`TaskStepReturn`、`TaskStepHelper`（timeout/retry/withCancellable/cancel-reason 全路径）、`GraphTaskStep`（含 buildWaitFuture 三类等待、runStep 四条完成路径、completeGraphIfDrained 计数不变式、error-handoff）、`ParallelTaskStep`、`AbstractForkTaskStep`+`ForkTaskStep`+`ForkNTaskStep`、`SequentialTaskStep`、`SelectorTaskStep`、`LoopTaskStep`、`LoopNTaskStep`、`ExecutorTaskStepWrapper`、`RunOnContextTaskStepWrapper`、`DelayTaskStep`、`TimeoutTaskStepWrapper`、`ThrottleTaskStepWrapper`、`RateLimitTaskStepWrapper`、`SyncTaskStepWrapper`、`RetryTaskStepWrapper`、`TryTaskStepWrapper`、`BuildOutputTaskStepWrapper`、`TaskStepExecution`（sync/async 双出口 + driveStepFailure + continuation-skip）、`TaskImpl`（四终态 driver）、`TaskStepRuntimeImpl`（访问约束不变式）、`TaskRuntimeImpl`（cancel/scope）、`TaskFlowManagerImpl`、`DaoTaskStateStore`（条带锁 saveStepState）、`GraphStepBuilder/GraphStepAnalyzer/TaskStepEnhancer`（装配序与校验集）、`TransactionalTaskStepWrapper` + `TransactionTemplateImpl.runInTransactionAsync`（用法面）。
- nop-wf：`WorkflowServiceImpl`（8 个 Async 方法全读）、`DefaultWorkflowExecutor`（含 runAutoTransitionsWithLimit 上限防护）、`WorkflowEngineImpl`（invokeAction/doInvokeAction/handleError/runSource/runSourceWithRetry/exec-group 判定）、`WorkflowImpl`（delayExecute/executeNow/reload）、`WorkflowManagerImpl`（实例创建策略）、`WorkflowCoordinatorImpl`（子流通知）、`WfTaskScanner` + job.yaml 调度配置、`WorkflowService/IWorkflowExecutor` 接口面。
- 平台设施用法面：`FutureHelper`（syncGet/thenCompleteAsync 语义=完成线程内联回调、bindCancelToken/bindResult/isFutureDone）、`AsyncHelper.waitAsync`（joinType 全分支）、`ContextProvider.thenOnContext`（零调用方核实）、`BaseContext.syncGet`（阻塞语义）、`IThreadPoolExecutor/StandardThreadPoolExecutor`（无内建上下文包装核实）、`task.xdef` 关键属性缺省值（runOnContext=false、concurrent=false、autoCancelUnfinished=true）。

**F1/F2/F9 定向对照结论**：F1（异步快照撕裂）——`buildAggResult` 的 done 快照仅在 join=ANY_* 提前返回时读未决分支并补占位（已声明最小语义），join=ALL 全决后读取，无撕裂；`stepResults` 为 ConcurrentHashMap。F2（半个成功）——C3-001 为真实实例；步骤副作用 vs 终态窗口为已声明边界。F9（吞异常）——`thenCompleteAsync` 回调内异常显式 `completeExceptionally`（FutureHelper:347-349）、`completeGraphIfDrained` 对 whenComplete 内 throw 的丢弃风险已由 plan 364 [05-07] 注释与实现收敛、`DaoTaskStateStore` 序列化失败路径全部 warn 可见；未发现新的静默吞异常点。

**残留边界（本轮未立案/未深挖）**：
1. `WorkflowManagerImpl` 每请求新建 WorkflowImpl 带来的 DB 重复加载成本（性能，非本维度）。
2. `SyncTaskStepWrapper` 阻塞等待与 `runOnContext` 单上下文串行注释（"多个runOnContext会串行执行"）的叠加行为未构造完整场景。
3. nop-task fork 分支共享 stepPath 行的跨进程 resume 完整性——owner doc 已裁定暂缓。
4. `GlobalExecutors`/`DefaultSemaphore`/`DefaultRateLimiter` 自身实现——按纪律属平台设施本体，不在本单元。
5. `runAutoTransitionsWithLimit` 的 10,000 次上限内单事务写放大（DoS 面，属 C6 资源类且未达 P0/P1 具体化标准）。
6. wf 侧 `ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER` 防御分支（WfTaskScanner:104-110）已在注释中自我声明为竞态残余，无需重复立案。

---

## 维度复核结论（独立复核子代理）

- 复核子代理：C3 维度独立复核 subagent（与初审者不同角色，仅验证/保留/降级/驳回，不补充新发现）
- 复核日期：2026-10-15
- 复核输入：`C3-async-chains.md`（C3-001~010）、`C3-round2.md`（C3-011~015）、`C3-round3.md`（无新增 + 终止声明 + C3-012 or-join 扩展），按序完整读取
- 复核方法：逐条重读 live code 验证快照读点/判定逻辑/写点/乐观锁失效原因/异步时序与异常传播推演；历史对账仅限授权范围（`check/`、`check2/`、2026-05~09 相关审计目录结论节、cra-index.md、git log）

### 逐条判定表

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---------|-------|------|-----------|---------|
| C3-001 | P1 | **保留** | 快照读点（ExecGroupSupport:30→WorkflowStepImpl:329-340 读事务内 ORM 步骤集）、写点（各自步骤行，跨行无锁）、乐观锁失效原因、无补救出口全部复核成立；vote-group 修正（isVoteGroupComplete:52 仅计自身+快照已完成）亦成立；引文行号 1256-1265 实为 1264-1275（漂移不影响） | 新发现 |
| C3-002 | P1 | **保留** | 装配序（executor 内/sync 外）、`.sync()`→syncGet 无超时阻塞、构建期无互斥均属实；校正两点：阻塞者是 sync 步骤的调用线程、"kill 无法解围"过强（取消兜底 complete ret 可唤醒等待者，但平台无 kill API 故实际永久）；与 C4-002 同点不同视角、判定自洽可归并 | 新发现 |
| C3-003 | P2 | **保留** | 双调度循环、runStep 无一次性守卫、next 自动派生 wait、校验集缺口逐行属实；exit 节点幽灵说有 cancellable.cancel 早退缓解（仅剩并发窗），非 exit 节点双执行无条件成立，P2 不变 | 新发现 |
| C3-004 | P2 | **保留** | 池线程直跑无重投递、runOnContext 缺省 false、getLocale 回退 ThreadLocal、thenOnContext 零生产调用方全部属实；"部分声明"P2 校准恰当 | 新发现 |
| C3-005 | P2 | **保留** | 竞速结构、幽灵续延写 scope、全引擎无中断点逐行属实；终态受保护、损害限资源占用+窄窗 scope 竞态的判级成立 | 新发现 |
| C3-006 | P2 | **保留** | succeed/fail check-then-act、driveStepFailure 无监视器、task 级监视器的不对称全部属实；COMPLETED 携 exception 的交错时序成立、后果限诊断字段，P2 恰当 | 新发现 |
| C3-007 | P2 | **保留** | Thread.sleep 在 @BizMutation 事务内、maxRetryDelay 仅 exponential 分支封顶、task 侧 RetryPolicy 两分支均封顶（对照声明属实）；引文行号约 8 行漂移不影响 | 新发现 |
| C3-008 | P3 | **保留** | tryResolve 对未决 future 原样透传（尾部 return value）属实，边界形状缺口 P3 恰当 | 新发现 |
| C3-009 | P3 | **保留** | 整批单 session、批内 syncGet、skip-due-task-race 守卫、job.yaml 无互斥且 enabled 缺省 false 均属实；守卫兜底下 P3 恰当 | 新发现 |
| C3-010 | P3 | **保留** | javadoc 实例级表述 vs 对象级实现 + 每请求新建实例的事实链属实；文档性偏差立案口径诚实恰当 | 新发现 |
| C3-011 | P1 | **保留** | checkEnd→isAllStepsHistory 读事务内 ORM 集合、竞态双侧各写不同步骤行双双不写 wf 行（乐观锁无冲突可依）、无 rescue 出口（due/remind/auto-transitions 均 ACTIVATED 过滤、doEndWorkflow 生产调用方恰 2 处）、TO_END 路径 killSteps 同行保护的不对称佐证，从严复核全部成立 | 新发现 |
| C3-012 | P1 | **保留（采纳 or-join 扩展）** | 复用判定读快照 + 无 (wfId,stepName,joinGroup) 唯一约束（orm 仅 stepId 主键）→并发双建无阻、激活判定读快照、and 滞留/or-join 契约违反（复用门控 joinType!=null 两类型共用、xdef:261-263 原文逐字核实）全部成立 | 新发现 |
| C3-013 | P2 | **保留** | delay 续延递归 execute 在 timer 线程、retry body 调度 globalTimer、executor 包不住重试体均属实；对账：retry 半边与 C4-001/C4-008（P1）同池同站、delay 续延半边为本条独有面——C3-P2/C4-P1 同机制判级分歧留待 7.1 合并统一；"缺省 5 线程"为配置值（实际 core=4），不影响结论 | 新发现 |
| C3-014 | P3 | **保留** | svcCtx=null 取消断链全链（setCancelToken(getSvcCtx())→checkNotCancelled 恒 false→Executor 兜底不注册→newChildRuntime 传 null）逐步验证成立；TaskGenHelper 生成代码实际传 svcCtx，null 需显式脚本用法，与原判级自我校准一致 | 新发现 |
| C3-015 | P3 | **保留（一处后果校正）** | 子流结束事务与父行写耦合、乐观锁冲突整体回滚机制属实；但字段7"无重试入口/永久孤儿"不成立——整体回滚使子流末步骤恢复 ACTIVATED，重试动作即恢复（父已终结后 notify 走 isHistory 早退），真实代价是副作用重放+异常误导向；机制成立，P3 维持 | 新发现 |

### 判定计数

- 保留：15 / 15（其中 P1×4、P2×6、P3×5）；降级：0；驳回：0
- 影响面修正采纳 2 处（round2 vote-group 并入 C3-001、round3 or-join 并入 C3-012），均经 live code 独立重验成立

### 不确定项（显式列出）

1. **C3-002 字段6/7 叙述精度**：阻塞线程归属与"kill 无法解围"表述有偏差（见判定表），死锁机制本身（同池嵌套 join + 无超时 + 无取消源 = 永久滞留）复核成立；实际触发需模型配置 sync+executor 同池嵌套且池饱和，未做并发复现实验（与初审相同）。
2. **C3-013 判级分歧**：与 C4-001/C4-008 同机制的判级存在 C3-P2/C4-P1 分歧，本复核不代裁，留 7.1 合并裁定；本条 delay 续延半边为 C3 独有，P2 自洽。
3. **C3-015 恢复路径的实证边界**：回滚恢复 ACTIVATED 后的重试恢复链为代码推演（doExitStep 同事务 + isHistory 早退），未做 DB 级复现；不影响机制成立。
4. **C3-001/011/012 未做并发复现实验**（与初审一致）：判定链为逐行静态推演，快照语义依赖 READ_COMMITTED 级隔离 + ORM 事务集合，已在代码层核实（DaoWorkflowStore.getSteps→NopWfInstance.getSteps()）。
5. **历史对账范围限定**：现状标注基于授权范围内的历史记录（check/、check2/、2026-05~09 相关目录结论节、cra-index.md、git log）；不排除更早未归档口头结论，但台账口径内 15 条均为首次立案。

### 现状标注总口径

- 15 条全部标注「新发现」：cra-index.md 台账与本 campaign git log 中 nop-wf/nop-task 零修复提交（唯一修复 commit 8dcfa4cd0c 属 nop-stream）；历史审计中与本 15 条同站点的记录（check2/nop-wf.md joinGroupExpr 持久化、check/nop-task.md timeout cancellable 自引用、2026-09/06-nop-auth-wf.md G6-22 系）均为**不同缺陷**且已修复/另案，与本轮发现无重复立项。
- 相邻历史修复佐证：2026-08-23 timeout 取消传播修复、2026-08-28 joinGroupExpr 落库修复解释了当前代码中的相关注释，但不构成本轮 15 条中任何一条的修复。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|------|------------|---------|-----------|------|
| C3-001 | P1 | nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java（:1264-1275）；ExecGroupSupport.java（:20-31）；WorkflowManagerImpl.java（:81-87） | and/seq/vote 组会签并发完成时各自快照判组不完整、各自退出自身步骤行，组迁移永久丢失且无自动补救 | 新发现 |
| C3-002 | P1 | nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java（:118-120,:159-160）；step/SyncTaskStepWrapper.java（:17）；step/ExecutorTaskStepWrapper.java（:33-52） | sync=true+executor（同池嵌套）组合无构建期互斥，有界池饱和即全员永久互等（无超时、无 kill 入口）；与 C4-002 同点（池治理视角） | 新发现 |
| C3-003 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java（:202-234,:248-350）；builder/GraphStepAnalyzer.java（:93-103） | 图步骤同时入 enterSteps 又被 next/waitSteps 依赖时被双调度执行，body 副作用双跑、结果覆写，无一次性守卫与构建期校验 | 新发现 |
| C3-004 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/step/ExecutorTaskStepWrapper.java（:33-36）；ITaskRuntime.java（:44-51） | executor 并行步骤体在线程池直跑无上下文重投递，ThreadLocal 依赖静默失效或串号（runOnContext 缺省 false，thenOnContext 零调用方） | 新发现 |
| C3-005 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java（:184-244）；step/BuildOutputTaskStepWrapper.java（:32-58） | step 超时竞速只终结返回值不停止 body，幽灵线程继续写 scope/状态行，与并行分支构成窄窗共享 scope 竞态 | 新发现 |
| C3-006 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java（:47-63,:186-194）；step/TaskStepExecution.java（:378-407） | step 级终态守卫 check-then-act 非原子（与 task 级监视器不对称），完成/取消同刻交错产生 COMPLETED 行携带 exception 的诊断矛盾 | 新发现 |
| C3-007 | P2 | nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java（:876-905,:922-933） | wf 步骤重试用 Thread.sleep 阻塞请求事务（连接/行锁全程持有、不可取消），maxRetryDelay 对固定 delay 不封顶 | 新发现 |
| C3-008 | P3 | nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java（:866-868） | wf source 返回未决 CompletionStage 被原样透传为值，异步异常无消费者（对照 task 侧已全面压平） | 新发现 |
| C3-009 | P3 | nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java（:64-120） | due 扫描整批单事务横跨任意业务动作，长事务持连接；双触发由引擎状态守卫兜底，残余为读放大与日志噪音 | 新发现 |
| C3-010 | P3 | nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WfRuntime.java（:100-104）；impl/WorkflowImpl.java（:46-51,:80-90） | delayExecute javadoc 按"工作流实例"表述、实现为每请求独享对象级队列，契约与实现不等价（C3-001 的认知根源） | 新发现 |
| C3-011 | P1 | nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java（:1578-1595）；store/AbstractWorkflowStore.java（:178-180） | 末段并行步骤并发完成时自动终结判定双侧看不到对方未提交退出，实例永久滞留 ACTIVATED 且无任何 rescue 出口 | 新发现 |
| C3-012 | P1 | nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java（:337-364,:491-520）；store/AbstractWorkflowStore.java（:108-133,:183-197） | join 步骤并发到达时复用判定快照盲区致双实例（and 永久 WAITING 滞留；or 违反 xdef 契约下游双跑），修复面须覆盖 and+or | 新发现 |
| C3-013 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/step/SequentialTaskStep.java（:82-96）；utils/TaskStepHelper.java（:262-284） | delay 续延与 retry 恢复体在共享 globalTimer（缺省 4-5 线程）上执行业务体，长 body 占满即全进程超时/延迟级联失准；retry 半边与 C4-001/C4-008 同站 | 新发现 |
| C3-014 | P3 | nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java（:233-234,:85-106） | svcCtx=null 时主 stepRt 取消令牌为 null，取消传播链断裂：任务不可取消且以 COMPLETED 收场（脚本/嵌入式用法可达） | 新发现 |
| C3-015 | P3 | nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java（:1597-1633）；impl/WorkflowCoordinatorImpl.java（:56-73） | 并发兄弟子流结束时父流程行乐观锁冲突回滚输家子流的整个结束事务（副作用重放+异常误导向；复核校正：整体回滚保留 ACTIVATED 重试入口，非永久孤儿） | 新发现 |

### 需子项复核的高风险项清单

无。四个 P1（C3-001/002/011/012）均已由本复核直接从严验证（快照读点、判定逻辑、写点、乐观锁失效原因逐项核实），无需再发起子项复核。移交 7.1 合并裁定的两项交叉（非复核缺口）：C3-002↔C4-002 同点归并、C3-013 retry 半边↔C4-001/C4-008 同池面归并（含 C3-P2/C4-P1 判级统一）。
