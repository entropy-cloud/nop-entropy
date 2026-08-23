# nop-task 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-task
- 文件数: 220（src/main/java，其中 47 个为 `_gen/` 或 `_` 前缀生成文件，按纪律跳过；非生成 173 个）
- 覆盖范围声明: 深读约 70 个非生成文件——`step/` 全部 36 个（Loop/LoopN/Fork/ForkN/Parallel/Sequential/Selector/Choose/If/Graph/Suspend/Sleep/Delay/End/Exit/Call/CallStep/Invoke/InvokeStatic/Bean/Eval/Delegate + 全部 Wrapper）、`state/` 4 个、`impl/` 7 个、`utils/` 3 个、`builder/` 9 个、`ext/` 7 个、`dao/store/` 2 个（DaoTaskStateStore/TaskExceptionRegistry）、`metrics/` 2 个、`exceptions/` 2 个、`reflect/` 1 个、核心接口约 12 个（ITask/ITaskRuntime/ITaskStepRuntime/ITaskStepState/ITaskState/ITaskStateStore/ITaskStepExecution/TaskStepReturn/TaskConstants/ITaskFlowManager 等）、`model/` 抽样 4 个、`service/` 抽样 1 个；另交叉读取 task.xdef（nop-xdefs）、_app.orm.xml、task-defaults.beans.xml/task-ext.beans.xml、RetryPolicy/Cancellable/FutureHelper（nop-commons/nop-api-core）用于验证。模式扫描（@Inject private/Spring 注解/SimpleDateFormat/异常吞噬/bare RuntimeException/beans.xml 注册/ORM 唯一索引）覆盖其余文件。未覆盖区域：`model/_gen/` 47 个生成文件（纪律跳过）、`api/` 12 个与 `dao/entity` 生成式样板接口（抽查确认薄壳）、`service/` 其余 3 个薄壳 BizModel、nop-task-codegen/nop-task-web/nop-task-meta（0 个 Java 实现文件）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 2 |
| P1 | 6 |
| P2 | 6 |
| P3 | 5 |

## 发现列表

### [P0] suspend 步骤在默认配置下抛 NPE：TaskStepExecution 挂起路径调用 metrics.endStep 未判空 meter

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java:247-253`
- **维度**: D1
- **证据**:
```java
ITaskFlowMetrics metrics = parentRt.getTaskRuntime().getMetrics();
Object meter = recordMetrics ? metrics.beginStep(stepRt.getStepPath(), step.getStepType()) : null;

try {
    TaskStepReturn stepResult = step.execute(stepRt);
    if (stepResult.isSuspend()) {
        metrics.endStep(meter, false);
        return stepResult;
    }
```
配合 `metrics/TaskFlowMetricsImpl.java:76-78`：
```java
public void endStep(Object meter, boolean success) {
    ((Timer.Sample) meter).stop(success ? stepSuccessTimer : stepFailureTimer);
}
```
- **现状**: 步骤级 `recordMetrics` 缺省为 false（`task.xdef:134` 中 `recordMetrics="!boolean=false"`），此时 `meter == null`；而 fresh 执行路径的 runtime metrics 恒为 `TaskFlowMetricsImpl`（`TaskFlowManagerImpl.prepareTaskRuntime` 在 `newTaskRuntime` 中无条件 setMetrics）。步骤返回 SUSPEND 时第 252 行直接 `metrics.endStep(null, false)`，`((Timer.Sample) null).stop(...)` 抛 NPE。同文件其他出口均已判空（第 257-258 行 `if (meter != null)`、第 330-331 行 `if (meter != null)`），唯独 suspend 出漏。
- **风险**: 任何带 suspend 步骤的任务在首次（非 recoverMode）执行、默认 metrics 配置下，挂起动作变成 NPE；NPE 被 catch(Exception) 捕获后走 FAILED driver 重抛，挂起语义完全失效。全仓库 nop-task 测试无 suspend 用例（已 grep src/test 验证），缺陷不会被现有测试拦截。
- **建议**: 第 252 行改为 `if (meter != null) metrics.endStep(meter, false);`，与第 257/330 行对齐；补 suspend 步骤的回归测试。
- **误报排除**: 已读 `TaskStepEnhancer.enhancedTaskStep`（recordMetrics 取自 stepModel）、`TaskFlowManagerImpl.newTaskRuntime/prepareTaskRuntime`（metrics 恒为 TaskFlowMetricsImpl）、`EmptyTaskFlowMetrics`（无操作但仅 resume 路径默认）、`TaskFlowMetricsImpl.endStep`（强转后直接调用 stop）、`FutureHelper.isFutureDone(null)==true`（SUSPEND 非异步会进入该分支）、task.xdef 步骤级默认值，确认触发链完整。

### [P0] 任务挂起（SUSPEND）被当作成功完成：TaskImpl 将挂起返回驱动为 COMPLETED 并持久化

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:150-162`
- **维度**: D1
- **证据**:
```java
return stepReturn.thenCompose((ret, err) -> {
    taskRt.runCleanup();
    if (metrics != null)
        metrics.endTask(meter, err != null);
    if (err == null) {
        // plan 259 设计裁定 1: task 终态 COMPLETED driver —— mainStep 成功 → task 进入 COMPLETED + 捕获 result。
        driveTaskCompleted(taskRt, taskState, ret);
        return ret;
    }
```
以及 `driveTaskCompleted`（170-174 行）：
```java
private void driveTaskCompleted(ITaskRuntime taskRt, ITaskState taskState, TaskStepReturn ret) {
    taskState.result(ret);
    taskState.setTaskStatus(TaskConstants.TASK_STATUS_COMPLETED);
    taskRt.saveTaskState();
}
```
- **现状**: mainStep 返回 `TaskStepReturn.SUSPEND`（SequentialTaskStep 同步透传子步骤挂起）时，`thenCompose` 对非异步结果立即调用 fn：err==null 走 `driveTaskCompleted`，把任务状态写成 COMPLETED 并 `saveTaskState()` 持久化；同时 `taskRt.runCleanup()` 会停止任务级 bean 容器。代码没有对 `ret.isSuspend()` 的任何分支。而 `TaskConstants.TASK_STATUS_SUSPENDED = 20` 明确定义了挂起状态但从未被使用。
- **风险**: 挂起的任务实例在 DB 中被标记为 COMPLETED（数据错误），后续经 `getTaskRuntime` + `execute` 恢复时命中 `taskRt.isRecoverMode() && taskState.isTerminal()` 短路（第 98-103 行）返回缓存 null 结果——挂起恢复语义端到端断裂；即使不持久化（saveState=false），内存中 taskState 也已是 COMPLETED，误导调用方。
- **建议**: `thenCompose` 内增加 `if (ret.isSuspend()) { taskState.setTaskStatus(TASK_STATUS_SUSPENDED); taskRt.saveTaskState(); return ret; }`（不 runCleanup 或仅做部分清理），与 STEP_NAME_SUSPEND 注释声明的历史状态恢复语义对齐。
- **误报排除**: 已读 `TaskStepReturn.thenCompose`（非异步直接 apply，SUSPEND 必进 fn）、`SequentialTaskStep.execute`（同步挂起直接 return SUSPEND）、`TaskStateBean.result/setTaskStatus`、`DaoTaskStateStore.saveTaskState`（status 直接落库）、`TaskConstants`（存在未用的 TASK_STATUS_SUSPENDED=20，佐证预期行为）、task.xdef 头注释（"可以从任意步骤中断并恢复执行"），确认无任何中间层拦截 SUSPEND。

### [P1] TaskImpl 异常出口 metrics.endTask 未判空：recordMetrics=false 且任务失败时 NPE 吞掉原始异常

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:129-147`
- **维度**: D1/D4
- **证据**:
```java
ITaskFlowMetrics metrics = recordMetrics ? taskRt.getMetrics() : null;
Object meter = metrics == null ? null : metrics.beginTask();
try {
    ...
    stepReturn = mainStep.execute(stepRt);
} catch (Exception e) {
    taskRt.runCleanup();
    metrics.endTask(meter, false);      // ← metrics 可能为 null
    driveTaskTerminal(taskRt, taskState, e);
    throw NopException.adapt(e);
}
```
- **现状**: 任务级 recordMetrics 缺省 true，但配置为 false 时 `metrics == null`；mainStep 抛异常后第 144 行 `metrics.endTask(...)` 抛 NPE。对比异步出口（第 151-153 行）有 `if (metrics != null)` 判空，同步 catch 出口漏判。
- **风险**: 任务失败时传播的是 NPE 而非真实业务异常（异常信息丢失），且 NPE 发生在 `driveTaskTerminal` 之前导致任务终态（FAILED/KILLED/TIMEOUT）不落库，task 实例停留在 ACTIVE。
- **建议**: 第 144 行改为 `if (metrics != null) metrics.endTask(meter, false);`。
- **误报排除**: 已确认 task.xdef 任务级 `recordMetrics="!boolean=true"` 可配置为 false（TaskFlowBuilder.buildTask 透传 `taskFlowModel.isRecordMetrics()`），`TaskFlowMetricsImpl.endTask` 同样强转调用 stop；异步出口的判空写法证明此处为遗漏而非设计。

### [P1] BuildOutputTaskStepWrapper 将 SUSPEND 转换为普通返回：挂起信号被吞、输出表达式被提前求值

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/BuildOutputTaskStepWrapper.java:32-53`
- **维度**: D1
- **证据**:
```java
return getTaskStep().execute(stepRt).thenApply(res -> {
    ...
    Map<String, Object> result = res.getOutputs() != null ? new LinkedHashMap<>(res.getOutputs())
            : new LinkedHashMap<>();
    outputExprs.forEach((name, expr) -> {
        if (stepRt.needOutput(name)) {
            ...
            result.put(name, expr.invoke(stepRt));
        }
    });
    return TaskStepReturn.RETURN(res.getNextStepName(), result);
});
```
配合 `TaskStepReturn.thenApply`（263-266 行）：`if (!isAsync()) { return fn.apply(this); }`。
- **现状**: SUSPEND 是非异步单例（`nextStepName="@suspend"`, outputs=null）。步骤声明了输出（除单 RESULT 无表达式情形，见 `TaskStepEnhancer.addOutput`）时，`thenApply` 的 fn 对 SUSPEND 立即执行：输出表达式在挂起点被求值（副作用提前发生），返回值变成 `RETURN("@suspend", result)`——一个 `isSuspend()==false`（非单例）的普通返回。
- **风险**: 上层 `TaskStepExecution.executeWithParentRt` 的 `stepResult.isSuspend()` 判定失效，流程不挂起继续走 succeed/COMPLETED，且返回值携带 nextStepName="@suspend"；SequentialTaskStep 的 `getNextIndex` 查不到 "@suspend" 抛 `ERR_TASK_UNKNOWN_NEXT_STEP`。声明输出变量的挂起步骤在运行期直接报错。
- **建议**: fn 开头增加 `if (res.isSuspend()) return res;`；或 `addOutput` 对 suspend 类型步骤跳过包装。
- **误报排除**: 已读 `TaskStepEnhancer.wrap/addOutput`（包装顺序：BuildOutput 在 Try 之外、Retry 之内，SUSPEND 必经此层）、`TaskStepReturn.SUSPEND/RETURN/thenApply` 实现、task.xdef（suspend 步骤 `xdef:ref="TaskStepModel"` 继承 input/output 定义，配置合法）、`SequentialTaskStep.getNextIndex`，链路完整。

### [P1] GraphTaskStep 错误路径使 onError 边（waitErrorSteps/nextOnError）永远不可达

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:224-250`
- **维度**: D1/D8
- **证据**:
```java
node.getStep().executeAsync(stepRt).whenComplete((v, e) -> {
    runningCount.decrementAndGet();

    if (e != null) {
        cancellable.cancel();
        future.completeExceptionally(e);
    } else {
        ...
        stepFuture.complete(null);
```
而 `buildWaitFuture`（128-137 行）对 waitError 边的预期是：
```java
for (String waitError : this.waitErrorSteps) {
    CompletableFuture<?> future = allFutures.get(waitError);
    future.whenComplete((result, err) -> {
        waitingCount.decrementAndGet();
        if (err != null) {
            if (waitingCount.get() <= 0)
                ret.complete(null);
        }
    });
}
```
- **现状**: 任何节点出错时 runStep 直接 `future.completeExceptionally(e)`（整个图失败）并 cancel 全图，且**失败节点的 stepFuture 从未 completeExceptionally**。error-join 依赖 stepFuture 异常完成才会触发，因此永远不会触发。
- **风险**: `GraphStepAnalyzer.normalizeWaitSteps/addInputDepend` 与 task.xdef 的 `nextOnError`/`waitErrorSteps`/`STEP_RESULTS.x.error` 数据依赖分析明确支持错误分支，但运行期错误分支是死特性：配置了错误边的图在首错时整体失败，行为与模型契约不符。
- **建议**: 错误路径先 `stepFuture.completeExceptionally(e)`（让 waitSuccess 短路失败、waitError 正常推进），仅当不存在等待该错误的节点或全图无 exit 可达时才 `future.completeExceptionally`；或至少在分析期拒绝同时存在错误边与 fail-fast 语义的配置。
- **误报排除**: 已读 `GraphStepAnalyzer`（waitErrorSteps 来源：nextOnError 归一化 + STEP_RESULTS.x.error 输入依赖分析）、`GraphStepBuilder`、`buildWaitFuture` 全部三个循环、`execute` 的 waitFuture 消费逻辑，确认没有任何路径会 completeExceptionally 失败节点的 stepFuture。

### [P1] fork/parallel 分支共享同一 stepPath：DaoTaskStateStore 并发 find→save/update 竞态写同一行

- **文件**: `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:265-291`
- **维度**: D3
- **证据**:
```java
public void saveStepState(ITaskStepRuntime stepRt) {
    ITaskStepState state = stepRt.getState();
    NopTaskStepInstance entity = findStepEntity(state.getTaskInstanceId(), state.getStepPath());
    boolean isNew = entity == null;
    if (isNew) {
        entity = stepDao().newEntity();
        ...
    }
    ...
    stepDao().updateEntityDirectly(entity);
}

protected NopTaskStepInstance findStepEntity(String taskInstanceId, String stepPath) {
    ...
    return stepDao().findFirstByQuery(query);
}
```
配合 `AbstractForkTaskStep.java:87-90`（所有分支用同一 stepName 建子 runtime）：
```java
protected TaskStepReturn executeFork(ITaskStepRuntime parentRt, Object varValue, int index) {
    IEvalScope parentScope = parentRt.getEvalScope();
    ITaskStepRuntime stepRt = parentRt.newStepRuntime(stepName, step.getStepType(),
            null, true, step.isConcurrent());
```
- **现状**: fork 的所有分支 stepPath 相同（`TaskStepRuntimeImpl.isFirstInstantiation` 的注释亦自认"fork/forkN 分支 2+ 共用同一 stepPath"）。ACTIVE 创建是顺序的（注册循环单线程），但各分支完成时的 `saveTerminalStateIfDone`→`saveStepState` 在不同完成线程上并发执行 find→update，对同一行做丢失更新式覆盖；`nop_task_step_instance` 表无 (taskInstanceId, stepPath) 唯一索引（已核对 `_app.orm.xml`，全文件 0 个 unique/index），并发 insert 也会产生重复行使 findFirstByQuery 结果不确定。
- **风险**: DB 持久化 + fork/parallel + 分支异步完成时，共享行的终态是"随机分支"的状态；跨进程恢复时 first-instantiation 分支加载该行，可能被错误跳过（读到别的分支 COMPLETED）或错误重抛（读到别的分支 FAILED），并伴随多线程共用 IEntityDao session 的线程安全风险。
- **建议**: stepPath 纳入分支维度（如 `forkName[index]` 或使用 runId）；或为 (taskInstanceId, stepPath, runId) 建唯一索引并以 runId 参与查询；短期至少在 saveStepState 上按 stepPath 加锁串行化。
- **误报排除**: 已读 `TaskStepRuntimeImpl.newStepRuntime/isFirstInstantiation`（确认分支 stepPath 相同）、`AbstractForkTaskStep.executeFork/buildForkStep`（setStepName(stepModel.getName()) 单名）、`ForkTaskStep/ForkNTaskStep.execute`（分支 future 并发完成）、`_app.orm.xml` NopTaskStepInstance 实体（仅 STEP_INSTANCE_ID 主键，无唯一索引）、`TaskStepExecution.saveTerminalStateIfDone`（完成线程回调内调用），确认并发写同一行成立。

### [P1] 恢复路径 continuation-skip 不重新导出输出变量：DB 恢复后已声明的输出丢失

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java:196-222`
- **维度**: D1
- **证据**:
```java
ITaskStepState stepState = stepRt.getState();
if (stepState != null && stepState.isDone()) {
    if (stepState.isSuccess()) {
        TaskStepReturn cached = stepState.result();
        ...
        if (cached != null) {
            parentScope.setLocalValue(TaskConstants.VAR_RESULT, cached.getResult());
            if (cached.getNextStepName() == null && nextStepName != null)
                cached = TaskStepReturn.RETURN(nextStepName, cached.get());
            return cached;
        }
```
而正常路径第 305-307 行会执行 `initOutputs(ret, stepRt, parentScope)` 导出 outputConfigs。
- **现状**: `TaskStepStateBean.result()`（73-77 行）只包装 `RETURN_RESULT(resultValue)`；`DaoTaskStateStore` 也只持久化 resultValue（stateBeanData 列）。continuation-skip 只恢复 VAR_RESULT，不调用 initOutputs，也不恢复原始 nextStepName（仅用静态 nextStepName 补）。
- **风险**: 崩溃/跨进程恢复后，已 COMPLETED 步骤被跳过时，其声明的输出变量（exportAs/toTaskScope）不在 parentScope 中，下游步骤引用这些变量得到 null——恢复执行的下游数据错误。
- **建议**: 持久化 outputs（如 stateBeanData 存完整 outputs map），或 continuation-skip 时基于 resultValue 重建并调用 initOutputs；nextStepName 一并持久化。
- **误报排除**: 已读 `TaskStepStateBean.result/succeed`、`DaoTaskStateStore.toStepStateBean/copyStepStateToEntity`（无 outputs 字段/列）、`TaskStepExecution.initOutputs`（唯一导出点）、`AbstractTaskStateCommon`（仅 resultValue），确认无其他恢复通路。

### [P1] 循环/条件步骤的 stateBean 未持久化：跨进程恢复从头上重跑循环（重复副作用）

- **文件**: `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:345-354`
- **维度**: D1
- **证据**:
`DaoTaskStateStore.toStepStateBean` 只恢复 resultValue/retryAttempt/tagSet 等，无 stateBean：
```java
// resultValue 从 stateBeanData 反序列化（JSON）
String data = entity.getStateBeanData();
if (!StringHelper.isEmpty(data)) {
    try {
        state.setResultValue(JsonTool.parse(data));
```
而循环位置保存在内存 stateBean（`LoopTaskStep.java:121-128`）：
```java
LoopStateBean stateBean = stepRt.getStateBean(LoopStateBean.class);
if (stateBean == null) {
    stateBean = new LoopStateBean();
    List<Object> items = CollectionHelper.toList(itemsExpr.invoke(stepRt));
    stateBean.setItems(items);
    stateBean.setIndex(0);
    stepRt.setStateBean(stateBean);
}
```
- **现状**: `ITaskStepState.stateBean`（LoopStateBean 的 items/index、ForkStateBean 的 items、ForkN 的 count、Choose 的 caseValue、If 的条件值、Suspend 的 first 标记）从不写入 DB；`copyStepStateToEntity` 也只序列化 resultValue。`TaskStepStateBean.stateBean` 无持久化通路。
- **风险**: DB 恢复（recoverMode）时 LoopTaskStep 的 stateBean 为 null，从 index=0 重新求值并重跑：迭代 0 的 body 因持久化 COMPLETED 行被 continuation-skip 跳过一次，但迭代 1+ 会用全新 state 再次真实执行——崩溃前已完成迭代的业务副作用（写库/发消息等）被重复执行。Choose/If 条件重求值还可能在数据变化后走不同分支。
- **建议**: 将 stateBean（或至少 loop index/items、choose 决策）序列化持久化（stateBeanData 列语义本应如此），afterLoad 时恢复；或在文档明确当前恢复粒度仅到"步骤级"而非"迭代级"。
- **误报排除**: 已读 `DaoTaskStateStore.toStepStateBean/copyStepStateToEntity` 全部字段映射、`TaskStepStateBean`（stateBean 无 @Column 映射）、`LoopTaskStep/LoopNTaskStep/ForkTaskStep/ForkNTaskStep/ChooseTaskStep/IfTaskStep/SuspendTaskStep` 的 stateBean 使用方式、`TaskStepRuntimeImpl.newStepRuntime`（load 后 stateBean 为 null），确认无恢复机制。

### [P2] persistVars 全链路死代码：xdef 声明的持久化变量特性未实现

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java:183-184`
- **维度**: D8
- **证据**:
```java
ITaskStepRuntime stepRt = parentRt.newStepRuntime(stepName, step.getStepType(),
        step.getPersistVars(), useParentScope, step.isConcurrent());
```
`AbstractTaskStep.java:60-66` 的 persistVars 字段无任何 builder 赋值；xdef（task.xdef:28）声明的属性名是 `persisVars`（拼写缺 t），解析到 `_TaskExecutableModel._persisVars`，全仓库 grep `getPersisVars` 在非生成代码中 0 个消费者。
- **现状**: 两套字段互不相干：模型侧 `_persisVars` 解析后无人读取；运行时侧 `AbstractTaskStep.persistVars` 无人 set（`TaskStepBuilder.initAbstractStep` 不设置）。`TaskStepRuntimeImpl.persistVars` 也仅存储无消费。
- **风险**: task.xdef 注释承诺"标记为 persist 的变量会自动保存，支持中断后恢复执行"，实际完全未接线——契约漂移，使用者会误信变量级持久化存在（与上一条 stateBean 未持久化问题叠加）。
- **建议**: 要么实现（saveState 时按 persistVars 序列化 scope 变量到 stateBean），要么删除 xdef 属性与运行时死代码并在文档修正承诺。
- **误报排除**: 已 grep 全仓库 `persistVars|getPersisVars` 全部命中点（仅接口定义、setter、透传，无读取方），并核对 `_TaskExecutableModel` 生成代码与 `TaskStepBuilder.initAbstractStep`。

### [P2] TaskRuntimeImpl.newChildRuntime 取消传播自引用：父任务取消不传播到子任务运行时（svcCtx 为 null 时）

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java:150-156`
- **维度**: D3/D1
- **证据**:
```java
public ITaskRuntime newChildRuntime(ITask task, boolean saveState) {
    ITaskRuntime taskRt = taskManager.newTaskRuntime(task, saveState, getSvcCtx());
    Consumer<String> onCancel = this::cancel;
    this.appendOnCancel(onCancel);
    taskRt.addTaskCleanup(() -> removeOnCancel(onCancel));
    return taskRt;
}
```
对照 `ITask.asExecution`（ITask.java:36-40）的正确模式：
```java
Consumer<String> onCancel = taskRt::cancel;
cancelToken.appendOnCancel(onCancel);
taskRt.addTaskCleanup(() -> cancelToken.removeOnCancel(onCancel));
```
- **现状**: `this.appendOnCancel(this::cancel)` 把父 runtime 自己的 cancel 方法注册到自己的监听器上（自引用，Cancellable.cancel 先置位再回调，重入直接返回，无死循环但无效果）；本意显然是 `taskRt::cancel`。父→子传播目前仅靠共享 svcCtx 间接生效（子 runtime 构造器 `svcCtx.appendOnCancel(child::cancel)`）。
- **风险**: `newTaskRuntime(task, saveState, null)`（svcCtx=null，如仓库内 TeamTaskFlowOrchestrator 即传 null）时，CallTaskStep 子任务在父任务 cancel 后继续运行，无法中止——资源泄漏/超时后子任务失控。
- **建议**: 改为 `Consumer<String> onCancel = taskRt::cancel; this.appendOnCancel(onCancel); taskRt.addTaskCleanup(() -> this.removeOnCancel(onCancel));`
- **误报排除**: 已读 `Cancellable`（确认自引用回调为无操作）、`TaskRuntimeImpl` 构造器（svcCtx 非空时的间接传播路径）、`ITask.asExecution`（同型代码的正确写法）、`CallTaskStep.execute`（newChildRuntime 调用方）、仓库内 `newTaskRuntime(..., null)` 调用点。

### [P2] SequentialTaskStep 异步回调缺少 isSuspend 处理：异步挂起被当作 "@suspend" 跳转抛错

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/SequentialTaskStep.java:77-91`
- **维度**: D1
- **证据**:
```java
int indexParam = index;
return stepResult.thenApply(result -> {
    if (result.isEnd()) {
        stepRt.setBodyStepIndex(steps.size());
        return result;
    } else if (result.isExit()) {
        stepRt.setBodyStepIndex(steps.size());
        return TaskStepReturn.RETURN(result.getOutputs());
    } else {
        stepRt.setBodyStepIndex(getNextIndex(indexParam, result, stepRt));
        stepRt.saveState();
        return execute(stepRt);
    }
});
```
`getNextIndex`（96-105 行）对未知 nextStepName 抛 `ERR_TASK_UNKNOWN_NEXT_STEP`。
- **现状**: 同步路径 62-63 行处理了 suspend；异步路径没有。异步完成值为 SUSPEND 时（如 suspend 步骤被 executor/runOnContext 包装，ExecutorTaskStepWrapper 会把 SUSPEND 装进 future），`getNextIndex` 查 `stepIndex.get("@suspend")` 得 null 抛错，且 bodyStepIndex 已推进失败前未回滚（异常发生）。
- **风险**: 挂起 + 异步包装（executor/runOnContext/嵌套异步 sequential）组合下，任务以 ERR_TASK_UNKNOWN_NEXT_STEP 失败而非挂起。
- **建议**: thenApply 回调开头增加 `if (result.isSuspend()) return result;`。
- **误报排除**: 已读 `ExecutorTaskStepWrapper`（SUSPEND 经 whenComplete 进入 ret future，`isFutureDone(null)==true` 走 complete 分支）、`TaskStepExecution.thenCompose`（异步挂起返回 SUSPEND 作为完成值）、`SequentialTaskStep` 同步/异步两分支、`TaskConstants.STEP_NAME_SUSPEND="@suspend"`，确认可达。

### [P2] SelectorTaskStep 异步回调缺少 isSuspend 处理：异步挂起被静默跳过并推进下一候选

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/SelectorTaskStep.java:103-110`
- **维度**: D1
- **证据**:
```java
} else {
    if (indexParam + 1 >= steps.size() || result.isResultTruthy())
        return result;

    stepRt.setBodyStepIndex(indexParam + 1);
    stepRt.saveState();
    return execute(stepRt);
}
```
- **现状**: 同步路径 61-62 行处理 suspend；异步路径的 else 分支对 SUSPEND（isResultTruthy()==false，outputs 为 null）走 `setBodyStepIndex(indexParam + 1)` 继续执行下一候选步骤——挂起被当作"该候选返回 falsy"静默跳过。
- **风险**: selector 候选步骤异步挂起时流程继续执行后续候选（并保存了推进后的 bodyStepIndex），语义错误且难排查。
- **建议**: else 分支前增加 `if (result.isSuspend()) return result;`。
- **误报排除**: 已读该文件同步/异步两分支与 `isResultTruthy` 实现（SUSPEND outputs=null → false），结合上条验证的异步 SUSPEND 可达性。

### [P2] TaskStepHelper.retry 对 SUSPEND 返回调用 state.succeed：重试包装的挂起步骤状态被标 COMPLETED

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:256-266`
- **维度**: D1
- **证据**:
```java
try {
    TaskStepReturn result = action.call();
    if (result.isAsync()) {
        ...
    }
    state.succeed(result.getResult(), result.getNextStepName(), stepRt.getTaskRuntime());
    return result;
} catch (Exception e) {
    state.fail(e, stepRt.getTaskRuntime());
}
```
- **现状**: 同步非异步返回一律 `state.succeed(...)`，未区分 SUSPEND（`succeed` 直接把 stepStatus 置 COMPLETED，见 `TaskStepStateBean.succeed:41-44`）。挂起步骤配置 retry 时（`TaskStepEnhancer.wrap:107-109` 会包装），挂起动作把步骤状态写成 COMPLETED。
- **风险**: 步骤状态机错误（挂起≠完成）；虽然 suspend 出口随后返回不再 save，但内存态 status 已 COMPLETED，后续任何 saveState（父步骤推进等场景）会把错误终态落库，恢复时被 continuation-skip 误跳过。
- **建议**: `state.succeed(...)` 前增加 `if (result.isSuspend()) return result;`。
- **误报排除**: 已读 `retry` 全流程、`TaskStepStateBean.succeed/isDone`、`TaskStepExecution` suspend 提前返回路径（确认 succeed 不被外层二次纠正）、`TaskStepEnhancer.wrap` 的 retry 包装顺序。

### [P2] 恢复路径 runtime 不初始化 metrics：TaskFlowManagerImpl.getTaskRuntime 未调用 prepareTaskRuntime

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:111-121`
- **维度**: D8
- **证据**:
```java
public ITaskRuntime getTaskRuntime(String taskInstanceId, IServiceContext svcCtx, IEvalScope scope) {
    ITaskStateStore stateStore = requirePersistStateState();
    TaskRuntimeImpl taskRt = new TaskRuntimeImpl(this, stateStore, svcCtx, scope, true);
    ITaskState taskState = stateStore.loadTaskState(taskInstanceId, taskRt);
    ...
}
```
对比 `newTaskRuntime`（88-97 行）中 `prepareTaskRuntime(taskRt, task)` 设置 `TaskFlowMetricsImpl`。
- **现状**: resume 路径构造的 runtime metrics 保持字段默认 `EmptyTaskFlowMetrics.INSTANCE`，恢复执行的任务不记录任何 task/step 指标。
- **风险**: 可观测性不对称：同一任务首次执行有指标、恢复执行无指标；监控口径失真。无数据破坏。
- **建议**: `getTaskRuntime` 中同样 `taskRt.setMetrics(new TaskFlowMetricsImpl(...))`（可从 load 到的 taskState 取 taskName/version）。
- **误报排除**: 已读 `TaskRuntimeImpl` 字段默认值、`newTaskRuntime/prepareTaskRuntime`、`TaskFlowMetricsImpl/EmptyTaskFlowMetrics`，确认无其他赋值点。

### [P3] resetGlobalStats 只重置信号量不重置限流器

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:198-203`
- **维度**: D8
- **证据**:
```java
public void resetGlobalStats() {
    globalSemaphores.forEachEntry((k, v) -> {
        v.resetStats();
    });
}
```
- **现状**: 与之成对的 `getGlobalSemaphoreStats/getGlobalRateLimiterStats` 都暴露两类统计，但 reset 只覆盖 `globalSemaphores`，遗漏 `globalRateLimiters`。
- **风险**: 运维调用重置后限流器统计未清零，监控数据不一致。无功能破坏。
- **建议**: 补充 `globalRateLimiters.forEachEntry((k, v) -> v.resetStats())`（若 IRateLimiter 提供 resetStats）。
- **误报排除**: 已读该文件全部统计方法与两个 LocalCache 定义。

### [P3] 全局 RateLimiter/Semaphore 首配置固化：后续速率/并发参数变化被忽略

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:162-178`
- **维度**: D1
- **证据**:
```java
public IRateLimiter getRateLimiter(ITaskRuntime taskRt, String key, double requestPerSecond, boolean global) {
    if (global) {
        return globalRateLimiters.computeIfAbsent(taskRt.getTaskName() + ":" + key,
                k -> new DefaultRateLimiter(requestPerSecond));
    }
```
- **现状**: 同 key（taskName+key）第二次以不同 requestPerSecond/maxPermits 调用时直接返回缓存实例，新参数被静默忽略（同任务模型内配置一致时无影响；keyExpr 动态相同 key、模型版本变更限流参数时会命中）。
- **风险**: 参数调优不生效，且无告警；属边界配置问题。
- **建议**: 缓存命中时校验参数一致，不一致记 warn 或重建。
- **误报排除**: 已读 `getRateLimiter/getSemaphore` 实现与 LocalCache 配置，确认无参数校验。

### [P3] TaskFlowAnalyzer.forEachStep 对 if/then/else 非递归遍历：if 分支孙级步骤漏 normalize/校验

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskFlowAnalyzer.java:97-104`
- **维度**: D1
- **证据**:
```java
} else if (stepModel instanceof IfTaskStepModel) {
    IfTaskStepModel ifModel = (IfTaskStepModel) stepModel;
    if (ifModel.getThen() != null) {
        action.accept(ifModel.getThen());
    }
    if (ifModel.getElse() != null) {
        action.accept(ifModel.getElse());
    }
} else if (stepModel instanceof ChooseTaskStepModel) {
    ...forEachStep(subStep, action); // choose 是递归的
```
- **现状**: then/else 是 `TaskStepsModel` 子类（`_IfThenTaskStepModel extends TaskStepsModel`），但只用 `action.accept` 处理一层，其内部再嵌套 if/choose/sequential 的孙级步骤不会被 `normalize`/`forceUseParentScope`/`checkStepRef` 覆盖；ChooseTaskStepModel 分支则正确使用递归 `forEachStep`。
- **风险**: 深层嵌套分支的输入模型未 normalize（defaultValue/valueExpr 归一化）与引用校验遗漏，构建期错误延后到运行期。
- **建议**: if 分支改用 `forEachStep(ifModel.getThen(), action)` 递归。
- **误报排除**: 已读 `forEachStep` 全部分支、`_IfThenTaskStepModel/_TaskChooseCaseModel` 继承关系、`TaskStepModel.normalize`（仅处理 inputs 一层）。

### [P3] DaoTaskStateStore 未在任何 beans.xml 注册：开箱使用 saveState/resume 即抛 ERR_TASK_NO_PERSIST_STATE_STORE

- **文件**: `nop-task/nop-task-dao/src/main/resources/_vfs/nop/task/beans/_dao.beans.xml`（空文件）
- **维度**: D7
- **证据**:
`_dao.beans.xml` 内容为空 beans 壳；全仓库 grep `DaoTaskStateStore` 在任何 `*.beans.xml`（排除 target/_dump）中无命中；而 `TaskFlowManagerImpl`（nop-task-core task-defaults.beans.xml 注册）依赖注入：
```java
@Inject
public void setTaskStateStore(@Nullable ITaskStateStore taskStateStore) { ... }

private ITaskStateStore requirePersistStateState() {
    if (this.taskStateStore == null)
        throw new NopException(ERR_TASK_NO_PERSIST_STATE_STORE);
```
- **现状**: 平台仓库内没有任何地方把 `DaoTaskStateStore` 注册为 `ITaskStateStore` bean；nop-task-dao 提供了完整实现与实体，但装配留白。
- **风险**: 应用调用 `newTaskRuntime(task, true, ...)` 或 `getTaskRuntime(...)` 时直接抛 ERR_TASK_NO_PERSIST_STATE_STORE；plan 252-266 全部持久化/恢复能力在默认装配下不可达，属于装配缺口而非代码缺陷（应用可自行注册）。
- **建议**: 在 nop-task-dao 的 beans 中带条件注册 DaoTaskStateStore（类似 task-ext 的 on-class condition 模式），或在文档明确需要应用注册。
- **误报排除**: 已 grep 全仓库 beans.xml 与 Java 引用（仅 nop-task-ext 测试与 pom 注释命中），确认无注册点；`@Inject` 为 setter 注入且可空，注入方式本身合规。

### [P3] ParallelTaskStep 聚合静默丢弃未完成分支（与 fork 族占位行为不对称）

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/ParallelTaskStep.java:80-89`
- **维度**: D8
- **证据**:
```java
for (CompletionStage<TaskStepReturn> future : promises) {
    String stepName = steps.get(index++).getStepName();
    if (FutureHelper.isFutureDone(future)) {
        StepResultBean result = StepResultBean.buildFrom(stepName, stepRt.getLocale(), future);
        states.add(result.getStepName(), result);
    }
}
```
- **现状**: joinType 为非 ALL（如 ANY）时未完成的分支在聚合结果中直接缺席；对比 `AbstractForkTaskStep.buildAggResult`（117-124 行）会为未完成分支补 `ERR_TASK_CANCELLED` 的 StepResultBean 占位。
- **风险**: 聚合方/aggregator 无法区分"分支被取消"与"分支不存在"，MultiStepResultBean 的 size/sum 口径在不同 joinType 下不一致。无数据破坏。
- **建议**: 对齐 fork 族行为，未完成分支补 cancelled 占位。
- **误报排除**: 已读 `ParallelTaskStep.execute` 全文与 `AbstractForkTaskStep.buildAggResult`、`MultiStepResultBean` 聚合口径。

## 补充说明（非缺陷，供后续审计参考）

- `RetryPolicy.setMaxRetryDelay`（nop-commons，nop-task 经 `TaskStepEnhancer.buildRetryPolicy` 调用）的 Guard 校验的是错误的变量 `retryDelay` 而非 `maxRetryDelay`，且 `withRetryDelay` 误调 `setMaxRetryDelay`——属 nop-kernel/nop-commons 文件，超出本次 nop-task 审计范围，仅提示交叉影响。
- `TaskGenHelper` 以字符串拼接把 taskName/taskPath 拼入生成脚本源码，taskName 来自受信模型资源（xdef model-name 约束），注入风险可忽略。
- `TaskExceptionRegistry` 的 `Class.forName` 反射构造以注册表（factories map）为白名单门禁，持久化数据中的任意 FQCN 无法触发任意类实例化，无反序列化漏洞。
