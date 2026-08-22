# nop-task 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-task（core/api/ext/dao/service 为主）
- 文件数: 264（src/main/java，其中约 47 个为 `model/_gen` 生成文件，按约定排除在检查与修改建议之外；非生成文件约 217 个）
- 覆盖范围声明:
  - 深读（全文逐行）: task-core 执行主链路与全部 step 实现约 45 个核心文件——`TaskStepExecution`、`TaskImpl`、`TaskRuntimeImpl`、`TaskStepRuntimeImpl`、`TaskFlowManagerImpl`、`SequentialTaskStep`、`GraphTaskStep`、`LoopTaskStep`、`LoopNTaskStep`（stateBean 部分）、`ParallelTaskStep`、`ForkTaskStep`、`AbstractForkTaskStep`、`SelectorTaskStep`、`ChooseTaskStep`、`IfTaskStep`、`SuspendTaskStep`、`DelayTaskStep`、`SleepTaskStep`（结构确认）、`CallTaskStep`、`CallStepTaskStep`、`TryTaskStepWrapper`、`RetryTaskStepWrapper`、`TimeoutTaskStepWrapper`、`ExecutorTaskStepWrapper`、`RunOnContextTaskStepWrapper`、`SyncTaskStepWrapper`、`ThrottleTaskStepWrapper`、`RateLimitTaskStepWrapper`、`ValidatorTaskStepWrapper`、`BuildOutputTaskStepWrapper`、`BeanTaskStep`、`AbstractTaskStep`、`DelegateTaskStep`、`TaskStepHelper`、`TaskStepReturn`、`StepResultBean`、state 包全部 4 文件、builder 包（TaskFlowBuilder/TaskStepBuilder/TaskStepEnhancer/TaskFlowAnalyzer/GraphStepAnalyzer/GraphStepBuilder）、metrics 3 文件、`TaskFlowModel`；
  - 深读 nop-task-dao: `DaoTaskStateStore`、`TaskExceptionRegistry`（接口级确认）；
  - 深读 nop-task-ext 全部 9 个文件（4 个 decorator 深读）；
  - grep 全量扫描（220 个非生成 java 文件）: 空 catch、`new RuntimeException`、`printStackTrace`、`@Inject private`、Spring `@Value`、synchronized、可变 static 集合、`CompletableFuture`/`ExecutorService` 使用点——每处命中均已回读上下文；
  - 抽查: nop-task-service（4 个 BizModel）、nop-task-api（bean/crud 接口）、nop-task-queue、`_vfs` 下 beans.xml（task-defaults/task-ext）、task.xdef 相关注释；
  - 交叉验证: 只读 ext/core 测试 fixture 与测试 store 实现（`StateCapturingTaskStateStore`、`SnapshotResumeTaskStateStore`）用于确认测试盲区；`StringHelper.isEmptyObject`、`Cancellable`、`CompletableFuture.completeExceptionally(null)`（JDK 实测）等平台行为均已实证；
  - 未覆盖: `model/` 下非生成薄委托类仅抽查；`_gen`、`_` 前缀生成文件、测试代码本身不在审计范围。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 3 |
| P1 | 5 |
| P2 | 4 |
| P3 | 2 |

## 发现列表

### [P0] 持久化状态下 Loop 迭代 2+/Fork 分支 2+ 被 continuation-skip 静默跳过并复用首轮回放结果

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskStepRuntimeImpl.java:127`、`nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:248-259`、`nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java:196-222`
- **维度**: D1（恢复点/状态机）、D8
- **证据**:
```java
// TaskStepRuntimeImpl.newStepRuntime —— loadStepState 无条件调用，未按 taskRt.isRecoverMode() 门控
ITaskStepState newState = stateStore.loadStepState(stepState, stepName, stepType, taskRt);
if (newState != null) {
    newStepRt.setRecoverMode(true);
} else {
    newState = stateStore.newStepState(stepState, stepName, stepType, taskRt);
}
```
```java
// DaoTaskStateStore.loadStepState —— 仅按 taskInstanceId + stepPath 定位，不含 runId/迭代序号
String stepPath = TaskStepHelper.buildStepPath(parentPath, stepName);
NopTaskStepInstance entity = findStepEntity(taskRt.getTaskInstanceId(), stepPath);
```
```java
// TaskStepExecution.executeWithParentRt —— isDone 即跳过 step body，返回缓存结果（不检查 recoverMode）
ITaskStepState stepState = stepRt.getState();
if (stepState != null && stepState.isDone()) {
    if (stepState.isSuccess()) {
        TaskStepReturn cached = stepState.result();
        ...
```
- **现状**: Loop/Fork 的循环体子步骤在每次迭代/每个分支中都通过 `executeWithParentRt → newStepRuntime(同名)` 创建运行时，stepPath 恒为 `/loopStep/leafName`、`/forkStep/forkName/leafName`（Fork body 路径见 `AbstractForkTaskStep.executeFork:89`——所有分支共用 fork 自身名字作为子步骤名）。而 `saveTerminalStateIfDone`（plan 258）会在第 1 次迭代/分支完成时把 COMPLETED 终态行写入 store。第 2 次迭代（或第 2 个 fork 分支）进入 `newStepRuntime` 时 `loadStepState` 命中该行 → `recoverMode=true` + `isDone()=true` → continuation-skip 直接返回第 1 轮的缓存结果，step body 不再执行。runId 字段虽有生成（`newRunId`）但仅用于日志，`DaoTaskStateStore.loadStepState` 的查询条件不含它，无法区分迭代。对照 `TaskRuntimeImpl.newMainStepRuntime:205-208`（loadMainStepState 明确用 `if (recoverMode)` 门控）可见子步骤加载缺少同样的门控。
- **风险**: 使用 DB 持久化 store（`saveState=true`，即接入 `DaoTaskStateStore`——仓库内唯一持久化实现）的任务流中，任何 loop 的第 2+ 次迭代、fork/forkN 的第 2+ 个分支都会静默跳过执行：副作用缺失（漏发消息/漏写数据）、所有迭代/分支复用第 1 轮结果（数据错误）、并行分支互相吞并。另：多个 fork 分支并发 `saveStepState` 对同一 (taskInstanceId, stepPath) 行 upsert，存在丢失更新/行锁竞争。测试未覆盖：`StateCapturingTaskStateStore` 继承 `DefaultTaskStateStore` 未 override `loadStepState`（恒返回 null），所有 composite-loop/fork 测试跑在无 load 命中的 store 上。
- **建议**: 在 `newStepRuntime` 中以 `taskRt.isRecoverMode()` 门控 `loadStepState`（与 `newMainStepRuntime` 对齐）；或让 load 匹配当前 runId/迭代键；并为 Fork body 步骤运行时附加分支序号以区分 stepPath。
- **误报排除**: 已逐环验证：`SequentialTaskStep.execute:57-59` 每轮迭代调用 `executeWithParentRt`；`TaskStepExecution.saveTerminalStateIfDone:382-387` 确认终态落盘；`DefaultTaskStateStore`（内存）不受影响（load 恒 null），故仅持久化配置触发；非 Loop/Fork 场景（Graph/固定 Sequential）步骤路径唯一，行为正确。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 成立，已修复。未采用 recoverMode 硬门控（会破坏既有 resume-by-load 语义及其全部相关测试），改为在 `TaskStepRuntimeImpl.newStepRuntime` 以 task 级 attribute 记录本次执行内已实例化的 stepPath，`loadStepState` 仅对每个 stepPath 的首次实例化生效：loop 迭代 2+/fork 分支 2+（同一执行内同一 stepPath 的再次实例化）一律走 `newStepState` 正常执行，不再命中首轮终态行；重新执行（resume/re-execution 总是构建新 task runtime，attribute 集为空）的 load 语义不变。`DaoTaskStateStore` 未改动（含 fork 并发 add 的原子性由并发集合保证）。测试：`nop-task-core` `TestRepeatedStepPathExecution#loopIteration2_executesInsteadOfReusingIteration1Result`（修复前 loop 第 2 轮迭代被 continuation-skip 静默跳过，sum 停留在 1，结果 'FAIL'）；`TestRepeatedStepPathExecution#forkBranch2_executesInsteadOfReusingBranch1Result`（修复前 fork 第 2 分支复用第 1 分支结果，聚合 2+2=4，'FAIL'）；`TestRepeatedStepPathExecution#uniquePathResume_firstInstantiationStillLoads`（守卫：唯一路径步骤 resume-by-load 跳过语义不变）。

### [P0] retry 延迟重试路径每轮将 step body 执行两次（副作用重复）

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:224-241`
- **维度**: D1（重试/恢复逻辑）
- **证据**:
```java
if (delay > 0) {
    return TaskStepReturn.of(null, stepRt.getTaskRuntime().getScheduledExecutor()
            .schedule(action, delay, TimeUnit.MILLISECONDS).thenApply(result -> {
                try {
                    TaskStepReturn ret = action.call();   // ← 第二次执行 step body
                    if (ret.isAsync()) {
                        if (ret.isDone())
                            return doRetry(result.sync(), null, loc, stepRt, retryPolicy, action);
                    }
                    return (Object) result.thenCompose((v, err) -> doRetry(v, err, loc,
                            stepRt, retryPolicy, action));
```
- **现状**: `schedule(action, delay)` 在延迟后执行一次 step body（run#1，即本意中的重试执行）；随后 `thenApply` 回调里又调用 `action.call()`（run#2）。run#2 的返回值仅用于 `isAsync` 判断，最终结果取自 run#1，run#2 的副作用白做且无任何记录。每次延迟重试轮次 = 2 次 body 执行。
- **风险**: 配置 `retryDelay > 0` 的重试步骤（nop-task-ext 的 retry decorator 一等公民配置，官方 fixture `retry-decorator-delay-success` 即使用 `retry:retryDelay="10"`）在首次失败后的每个延迟重试轮次把业务副作用执行两次（重复扣款/重复发消息/重复写库）。现有测试未捕获：fixture 的 `failOnceThenSucceed` 幂等吸收了双执行。
- **建议**: `schedule` 应提交空任务（纯定时器），在 thenApply 中执行一次 `action.call()`；或去掉回调中的 `action.call()`、直接基于 `result` 判定 async。同时为该路径补一条"每轮执行次数=1"的回归测试。
- **误报排除**: 确认 `IScheduledExecutor.schedule(Callable, delay, unit)` 语义为延迟执行传入任务（非定时器占位）；`action` 为 `() -> getTaskStep().execute(stepRt)`（`RetryTaskStepWrapper:28-29`），两次调用均为真实执行；`thenApply` 参数 `result` 即 run#1 的返回值。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 成立，已修复。删除 thenApply 回调中的重复 `action.call()`，延迟轮次仅由 `schedule(action, delay)` 执行一次；回调改用 `FutureHelper.thenCompleteAsync` 组装（handler 返回 CompletionStage 时自动压平为同步完成值），顺带修复修复前同样存在的 async 延迟重试结果未压平问题（延迟轮 action 返回未完成 async 结果时，外层 future 完成值为 async TaskStepReturn，触发 `TaskStepReturn.of` 的 "asyncReturn result must not be async" Guard 异常）。测试：`nop-task-core` `TestTaskStepHelperRetryDelayExecutionCount#delayRetry_syncSuccess_executesActionExactlyOncePerRound`（修复前 1 次初始失败 + 1 轮延迟重试共执行 step body 3 次，副作用重复）；`TestTaskStepHelperRetryDelayExecutionCount#delayRetry_asyncSuccess_executesActionExactlyOncePerRound`（修复前同样 3 次执行，且 async 结果触发上述 Guard 异常）。

### [P0] ExecutorTaskStepWrapper 异步分支 whenComplete 成败条件写反：成功挂死、失败吞错

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/ExecutorTaskStepWrapper.java:38-44`
- **维度**: D1、D4
- **证据**:
```java
result.whenComplete((data, err) -> {
    if (err != null) {
        ret.complete(data);              // 失败 → 按“成功”正常完成（data 为 null），异常被吞
    } else {
        ret.completeExceptionally(err);  // 成功 → completeExceptionally(null)
    }
});
```
- **现状**: `TaskStepReturn.whenComplete` 的回调参数为标准 `(result, error)` 语义（已核对 `TaskStepReturn.java:271-278`）。两个分支正好接反：子步骤异步失败时 `ret.complete(null)` 把失败伪装成成功（异常丢失）；子步骤异步成功时调用 `ret.completeExceptionally(null)`。经本机 JDK 实测（openjdk 26），`CompletableFuture.completeExceptionally(null)` 抛 NPE，该 NPE 在 whenComplete 回调内被 CompletableFuture 捕获写入被丢弃的依赖 future——`ret` 永不完成。
- **风险**: 任何配置了 `executor` 且子步骤返回未完成异步结果的步骤（如 executor + delay/异步 invoke 组合）：成功路径任务永久挂死（等待一个永不完成的 future）；失败路径错误被静默吞掉、以 null 结果当成功继续流转（数据错误）。同步子步骤（`isDone()` 为 true 走 `ret.complete(result.sync())`）不受影响，掩盖了该缺陷。
- **建议**: 交换两个分支体：`err != null → ret.completeExceptionally(err)`，否则 `ret.complete(data)`。补 executor+异步步骤的成功/失败两用例。
- **误报排除**: JDK 行为已实测验证（成功分支 NPE 被吞、future 永不完成）；参数语义已对照 `TaskStepReturn.whenComplete` 定义；该代码自 `e3ba7cdd5` 起即存在，非近期回归。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 成立，已修复。交换 `ExecutorTaskStepWrapper.execute` 中 whenComplete 回调两个分支体：`err != null → ret.completeExceptionally(err)`，否则 `ret.complete(data)`。测试：`nop-task-core` `TestExecutorTaskStepWrapperAsyncBranch#asyncSubStepSuccess_completesWrapperWithResult`（修复前成功分支 `completeExceptionally(null)` 抛 NPE 且被回调机制吞掉，wrapper future 永不完成，任务挂死）；`TestExecutorTaskStepWrapperAsyncBranch#asyncSubStepFailure_propagatesException`（修复前失败被 `ret.complete(null)` 吞掉，以 null 结果当成功返回）。

### [P1] 步骤 input mandatory 校验条件反转：非空抛异常、空值放行

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java:402-405`
- **维度**: D1、D8
- **证据**:
```java
Object value = expr == null ? parentScope.getValue(name) : expr.invoke(scope);
if (inputConfig.isMandatory() && !StringHelper.isEmptyObject(value))
    throw new NopException(ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY)
            .param(ARG_STEP_PATH, stepRt.getStepPath())
            .param(ARG_INPUT_NAME, name);
```
- **现状**: `StringHelper.isEmptyObject`（已核对 nop-commons 实现）在 null/空串时返回 true，故 `!isEmptyObject(value)` 表示"值非空"。条件实际语义为"mandatory 且值非空 → 抛 mandatory-input-not-allow-empty"，与错误码含义完全相反。同 commit（bf7c0287c）在 `TaskImpl.checkInputs:263` 写的正确版本是 `isMandatory() && StringHelper.isEmptyObject(value)`，可证此处为笔误。
- **风险**: 任务流中任何步骤声明 `<input mandatory="true">` 且传入了合法非空值 → 任务直接失败；真正的空值反而绕过校验。step 级 mandatory 功能完全不可用且反向伤害。
- **建议**: 去掉 `!`，与 `TaskImpl.checkInputs` 对齐；补一条 mandatory 命中/放行的单测。
- **误报排除**: 已核对 `isEmptyObject` 源码语义、`TaskStepEnhancer:67-68` 确认 `mandatory` 直接来自 `TaskInputModel.isMandatory()`（task.xdef `<input mandatory>` 属性），链路可达。

### [P1] next 属性被静默忽略、nextOnError 被同时用作成功跳转目标（跳错分支）

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:81-85`
- **维度**: D1（步骤跳转）、D8
- **证据**:
```java
return new TaskStepExecution(stepModel.getLocation(), stepModel.getName(), inputs, outputs, outputVars,
        stepModel.getFlags(), stepModel.getWhen(), step,
        stepModel.getNextOnError(), stepModel.getNextOnError(),   // ← nextStepName 与 nextStepNameOnError 同传 getNextOnError()
        stepModel.isRecordMetrics(),
        stepModel.getErrorName(), Boolean.TRUE.equals(stepModel.getUseParentScope()));
```
- **现状**: `TaskStepExecution` 构造参数依次为 `nextStepName`（成功路径默认跳转，见 `executeWithParentRt:310-311`）与 `nextStepNameOnError`（失败路径）。此处两个参数都传 `stepModel.getNextOnError()`：`stepModel.getNext()` 在非 graph 模式下经全仓 grep 确认无任何消费点（仅 `TaskFlowAnalyzer.checkStepRef:47` 做存在性校验、graph 模式经 `GraphStepAnalyzer.normalizeWaitSteps:80` 转 wait 边）。后果：(1) 顺序流中 `next="B"` 被静默忽略，实际按文档顺序执行下一步；(2) 配置了 `nextOnError="E"` 的步骤成功时也会跳到 E——以正常数据（非 ErrorBean）进入错误处理分支。
- **风险**: 状态机跳转错误：依赖 `next` 的流程执行顺序与定义不符（校验器还确认过该目标存在，用户以为生效）；使用 `nextOnError` 的流程成功路径误入错误分支，属于静默跳步。官方 fixture `next-step-on-error-failure/v1.task.xml` 把 `next` 与 `nextOnError` 设成同值（`next="errorHandler" nextOnError="errorHandler"`），恰好使两种语义不可区分，测试无法暴露。
- **建议**: 第一个参数改为 `stepModel.getNext()`；补 `next != nextOnError` 的顺序流跳转测试（成功应走 next、失败走 nextOnError）。
- **误报排除**: 已确认 `_TaskStepModel` 同时存在 `next`/`nextOnError` 两个独立属性且 nextOnError 注释只描述错误场景；已 grep 全模块确认 `getNext()` 无其他运行时消费点；该写法自初始 commit 722e21af0 即存在。

### [P1] TimeoutTaskStepWrapper 取消级联失效：`cancellable.append(cancellable)` 自引用

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:180-206`
- **维度**: D3（任务取消竞态）
- **证据**:
```java
Cancellable cancellable = new Cancellable();
Consumer<String> cancel = cancellable::cancel;
if (cancelToken != null)
    cancellable.append(cancellable);      // ← 把自己 append 到自己；应为 cancelToken.appendOnCancel(cancel)
```
- **现状**: `Cancellable.append(ICancellable)` 内部即 `appendOnCancel(task::cancel)`（已核对 nop-commons 源码），此处等于把 cancellable 注册为自己的回调，无任何外部传播作用；正确写法应像同文件 `withCancellable:288-290` 那样 `cancelToken.appendOnCancel(cancel)`。结果：外部取消（task/svc 级 kill）不会触发步骤的 timeout cancellable，而 `TimeoutTaskStepWrapper:21` 已用 `stepRt.setCancelToken(cancellable)` 替换了运行时原 token，超时窗口内步骤体的 `checkNotCancelled` 只看得见这个收不到外部取消的 token。收尾处 `cancelToken.removeOnCancel(cancel)` 移除的是从未注册过的回调（no-op）。附带：`catch (Exception e)` 出口未 `future.cancel(false)`，超时定时器在同步抛错后仍会滞留到触发。
- **风险**: kill/取消任务时，带 timeout 的步骤直到自身超时前对外部取消无响应（步骤体持续执行）；取消语义延迟放大到 timeout 上限。
- **建议**: 改为 `cancelToken.appendOnCancel(cancel);`，并在 catch 出口补 `future.cancel(false)`。
- **误报排除**: 已核对 `Cancellable.append` 实现确认自 append 无传播语义；对照同文件 `withCancellable` 的正确模式；`cancel()` 的 cancelled 守卫使自引用不至无限递归，排除"崩溃"仅保留"失效"定性。

### [P1] `getDumpValue` 对 null 值 NPE：dump=true 的空输入/输出直接崩溃步骤

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:318-325`
- **维度**: D1（NPE）
- **证据**:
```java
public static Object getDumpValue(Object value) {
    if (value instanceof XNode)
        return ((XNode) value).xml();
    if (value.getClass().isAnnotationPresent(DataBean.class)) {   // ← value == null 时 NPE
```
- **现状**: `value instanceof XNode` 对 null 安全返回 false，但下一行 `value.getClass()` 对 null 抛 NPE。调用点 `TaskStepExecution.initInputs:408-411` 与 `initOutputs:425-428` 在 `dump=true` 时对任意值调用，输入缺失（无 expr 且 scope 无值→null）或输出为 null 即触发。
- **风险**: 开启 `dump`（调试特性，恰好在值可疑时使用）遇到 null 值 → 步骤以 NPE 失败，任务中断；与"调试排障"目的相悖。
- **建议**: 方法开头加 `if (value == null) return null;`。
- **误报排除**: 已核对两个调用点均在 dump 分支内直接传值，无 null 过滤；无其他调用者。

### [P1] Loop/Fork/Choose/If 的 stateBean（循环下标/分支决策）未持久化：DB 断点续跑语义缺失

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java:27`、`nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:378-441`、`nop-task/nop-task-core/src/main/java/io/nop/task/step/LoopTaskStep.java:121-128`
- **维度**: D1（恢复点）
- **证据**:
```java
// TaskStepStateBean
private Object stateBean;            // 循环下标/items、分支 case 值都存在这里

// DaoTaskStateStore.copyStepStateToEntity —— 仅序列化 resultValue 到 stateBeanData，stateBean 无对应列/无写入
Object resultValue = state.getResultValue();
if (resultValue != null) {
    ... entity.setStateBeanData(json);

// LoopTaskStep.execute —— stateBean 为 null 即从 index=0 重来
LoopStateBean stateBean = stepRt.getStateBean(LoopStateBean.class);
if (stateBean == null) {
    stateBean = new LoopStateBean();
    ... stateBean.setIndex(0);
```
- **现状**: `DaoTaskStateStore` 的实体↔bean 拷贝只 round-trip `resultValue/stepStatus/bodyStepIndex/exception` 等，`stateBean` 既不写也不读。崩溃恢复（`getTaskRuntime` → recoverMode）后 `getStateBean` 返回 null：循环从 index 0 重新计数（itemsExpr 重算）、Choose/If 重新求值决策、Fork 重新生产 items。`ITaskStateStore.loadMainStepState` 的注释（plan 263）声称 Loop 作为 composite mainStep 可"从已持久化的中间位置续跑"，但 Loop 的位置在 stateBean 而非 bodyStepIndex，实际不成立。
- **风险**: DB 断点续跑对含循环/选择结构的任务不生效：轻则重复遍历（叠加 P0-1 的路径冲突后表现为全跳过、结果错误），重则 items 表达式非幂等（重查数据库）时恢复后处理集合与崩溃前不一致。与模块"断点续跑"的核心承诺直接冲突。
- **建议**: 将 stateBean（JSON 可序列化的 DataBean）纳入持久化（复用 stateBeanData 列或新列），load 时还原；或在文档中明确当前 DB 恢复仅支持 Sequential/Graph。
- **误报排除**: 已通读 `copyStepStateToEntity`/`toStepStateBean` 全部字段映射确认无 stateBean 通路；已核对 NopTaskStepInstance 实体可用列；Choose/If/LoopN/Fork 均依赖 stateBean（各自 execute 已读）。

### [P2] GraphTaskStep 在 whenComplete 回调里 throw：检测异常被吞、图可能无提示挂死

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:243-246`
- **维度**: D4、D1
- **证据**:
```java
if (runningCount.get() == 0 && !future.isDone())
    throw new NopException(ERR_TASK_GRAPH_NO_ACTIVE_STEP)
            .source(this)
            .param(ARG_STEP_PATH, stepRt.getStepPath());
```
- **现状**: 同样的"无可运行步骤"检测在 `execute:200-203`（同步 lambda 内）会正确传播到 promise；但此处位于 `node.getStep().executeAsync(stepRt).whenComplete((v, e) -> {...})` 回调内，抛出的异常进入 whenComplete 返回且被丢弃的依赖 future——异常静默丢失，图级 `future` 永不完成。触发场景：waitError 依赖成功时对应节点永不调度（`buildWaitFuture:128-137` 对成功仅 decrement 不 complete），若图中其余路径都已结束且无人完成 `future`（如错误处理分支是唯一 exit）。
- **风险**: 图结构配置导致死路时得不到 ERR_TASK_GRAPH_NO_ACTIVE_STEP 报错，任务挂死且无诊断信息（异常吞掉）。
- **建议**: 回调内改为 `future.completeExceptionally(...)` 而非 throw。
- **误报排除**: 已核对 whenComplete 回调异常不会传播给外层 promise（CompletableFuture 语义，与前述 P0-3 同机制）；同步路径的 throw 行为正确作对照。

### [P2] stepFailureTimer 复制粘贴错误：step 失败指标被记到 success 标签的 meter 上

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/metrics/TaskFlowMetricsImpl.java:55-57`
- **维度**: D1（可观测性正确性）
- **证据**:
```java
stepSuccessTimer = createTimer(TaskConstants.METER_STEP, taskNameTag, taskVersionTag, statusSuccessTag);
stepFailureTimer = createTimer(TaskConstants.METER_STEP, taskNameTag, taskVersionTag, statusSuccessTag);
//                                                                                          ^^^^^^^^^^^^^^^ 应为 statusFailureTag
```
- **现状**: `stepFailureTimer` 使用了 `statusSuccessTag`，与 `stepSuccessTimer` 名称+标签完全相同——Micrometer 返回同一 meter 实例。`endStep(meter, success=false)`（`TaskStepExecution` 失败路径调用）的耗时全部计入 success 维度；failure 维度的 step meter 永远无数据。task 级两个 timer 正确（对照第 51-52 行）。
- **风险**: 步骤失败率/失败耗时监控完全失真（失败被计为成功），基于该指标的告警失效。
- **建议**: 改为 `statusFailureTag`；补一条断言两个 timer 不为同一实例的测试。
- **误报排除**: 已核对 `endStep:79-82` 确认失败路径确实使用 `stepFailureTimer`；Micrometer 同名同 tag 返回同实例为既定语义。

### [P2] DaoTaskStateStore 两处空 catch 吞序列化异常且无日志

- **文件**: `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:152-157、403-409`
- **维度**: D4
- **证据**:
```java
String json = JsonTool.serialize(resultValue, false);
if (json != null && json.length() <= 4000)
    entity.setRemark(json);
} catch (Exception expected) {
}                                     // ← stateBeanData 处同款空 catch（403-409）
```
- **现状**: task 级 resultValue（remark 列）与 step 级 resultValue（stateBeanData 列）的 JSON 序列化失败被静默吞掉，无任何日志。同文件其他降级路径（`trySerialize:571-574`、`extractErrorStack:513-515`）均有 LOG.warn，且注释多处声称"#24 非静默"，这两处与之相悖。
- **风险**: 恢复关键数据（终态 result）序列化失败时无痕迹，DB 恢复后 result 缺失只能靠行为倒推，排障成本高。
- **建议**: 补 `LOG.warn("nop.task.serialize-state-failed:...", e)`，与同文件其他非致命降级路径对齐。
- **误报排除**: 已核对两处 catch 体确为空且无日志输出；同文件存在带日志的对照实现可证约定。

### [P2] continuation-skip 命中时不重放 outputConfigs：恢复后非 RESULT 导出变量丢失

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java:197-222`
- **维度**: D1（恢复逻辑）、D8
- **证据**:
```java
if (stepState != null && stepState.isDone()) {
    if (stepState.isSuccess()) {
        TaskStepReturn cached = stepState.result();
        if (cached != null) {
            parentScope.setLocalValue(TaskConstants.VAR_RESULT, cached.getResult());
            if (cached.getNextStepName() == null && nextStepName != null)
                cached = TaskStepReturn.RETURN(nextStepName, cached.get());
            return cached;            // ← 未执行 initOutputs，exportAs/toTaskScope 变量未恢复
```
- **现状**: 正常成功路径会经 `initOutputs:305-307` 把输出按 `exportAs`/`toTaskScope` 写回父 scope/任务 scope；continuation-skip 路径只恢复 `RESULT` 一个变量。`TaskStepStateBean.succeed` 仅保存 `resultValue`（单值），outputs 映射未持久化，恢复后无从重放。
- **风险**: DB 恢复（或 P0-1 的误命中）后，依赖 `<output name="x" exportAs="y">` 导出变量的下游步骤读到 null，产生与正常执行不一致的结果。
- **建议**: state 持久化 outputs 映射（或至少在 skip 路径按 outputConfigs 从缓存 result 重建可恢复项），并在文档标注限制。
- **误报排除**: 已核对 `TaskStepStateBean.succeed:41-44` 仅 setResultValue；正常路径 initOutputs 语义如上；两路径行为差异明确。

### [P3] DefaultTaskStateStore.newStepState 将 parentStepPath 记录为祖父路径

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/state/DefaultTaskStateStore.java:44-47`
- **维度**: D1（元数据一致性）
- **证据**:
```java
if (parentState != null) {
    state.setParentStepPath(parentState.getParentStepPath());   // ← 应为 parentState.getStepPath()
    state.setParentRunId(parentState.getRunId());
}
```
- **现状**: 传入的应是父步骤自身的 stepPath，此处取了父的 parentStepPath（祖父路径），隔层丢失一级。对照 `DaoTaskStateStore.newStepState:240-242` 的正确写法 `state.setParentStepPath(parentState.getStepPath())`。
- **风险**: 仅内存 store 使用该字段（不持久化），影响限于运行时元数据/诊断信息错误；stepPath 本身计算正确。
- **建议**: 与 DaoTaskStateStore 对齐改为 `parentState.getStepPath()`。
- **误报排除**: 两个实现的差异已并排核对；DefaultTaskStateStore 无持久化消费方（loadStepState 恒 null）。

### [P3] ParallelTaskStep/AbstractForkTaskStep 聚合时未完成分支被记为 ERR_TASK_CANCELLED 而非实际状态

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/AbstractForkTaskStep.java:117-124`
- **维度**: D8（契约语义）
- **证据**:
```java
if (FutureHelper.isFutureDone(future)) {
    StepResultBean result = StepResultBean.buildFrom(stepName, stepRt.getLocale(), future);
    states.add(String.valueOf(index), result);
} else {
    StepResultBean result = new StepResultBean();
    result.setError(new ErrorBean(TaskErrors.ERR_TASK_CANCELLED.getErrorCode()));
    states.add(String.valueOf(index), result);
}
```
- **现状**: joinType 决定提前返回时（如任一失败即返回 allOf/anyOf 语义），未完成分支统一标注为 `nop.err.task.cancelled`，无论其真实状态（可能仍在运行或被跳过）；且 `buildAggResult` 的 key 用 `String.valueOf(index)`（1-based 字符串）而非步骤名，与 `ParallelTaskStep:84-87` 用 stepName 作 key 的约定不一致。
- **风险**: aggregator 读到误导性错误码（把"未及完成"报告为"被取消"）；两类并行步骤的聚合 map key 风格不一致，聚合器需要按不同 key 规则取数。
- **建议**: 引入专门的"未完成"错误码或标注；统一 key 为 stepName/index 并写入文档。
- **误报排除**: 已对照 `ParallelTaskStep.execute:80-89` 确认 key 约定不一致；`ErrorBean` 直接以 errorCode 字符串构造的语义已核对。

## 维度结论摘要

- D2（资源管理）: 未发现流/连接泄漏；future 类资源均绑定 cancel token（`DelayTaskStep`、`ExecutorTaskStepWrapper`）。`timeout` 同步抛错出口未取消定时器（已并入 P1 取消级联发现的建议）。
- D3（并发）: taskRt scope/attrs 均为并发安全结构；`newStepRuntime(..., concurrent)` 正确传递并发标志。主要问题为取消传播失效（P1）与 Fork 分支同路径并发写行（并入 P0-1）。
- D5（安全）: 未发现运行时字符串动态求值面——所有 `IEvalAction` 均为模型编译期产物；`InvokeStaticTaskStep` 使用编译期 resolved method。无发现。
- D6（性能）: `TaskFlowModel` 经 `ResourceComponentManager` 缓存并 lazy 构建 ITask（synchronized，一次性），无重复加载；`TaskFlowManagerImpl` 的全局限流器/信号量按 key 缓存有上限。无发现。
- D7（平台规范）: 未发现 `@Inject private` 字段注入（仅 setter 注入且 bean 在 `_vfs` beans.xml 显式定义）；未发现 Spring `@Value`、bare `RuntimeException`、`printStackTrace`；错误处理符合两档策略（TaskErrors/NopException + 模块异常 NopTaskCancelledException/NopTaskFailException）。唯一相悖点为 DaoTaskStateStore 两处空 catch（P2）。
- 测试盲区总评: ext 可靠性测试套件的 `StateCapturingTaskStateStore` 不 override `loadStepState`，导致"持久化 load 命中"路径（P0-1/P1 stateBean/P2 skip-outputs）与 `next != nextOnError`、executor+异步、retry 每轮执行次数等场景均无覆盖——建议优先补齐这些用例。
