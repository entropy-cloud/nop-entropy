# 维度16：测试覆盖与质量

## 第 1 轮（初审）

**审计日期**: 2026-05-18
**审计基线**: `nop-job/` 目录下全部 19 个测试文件，10 个测试文件 > 200 行
**误报校准声明**: AutoTest 快照比对机制为标准测试模式，不对快照文件本身审计。以下仅审计测试代码的逻辑覆盖质量。

**测试文件基线**（按行数降序）：

| 行数 | 文件 |
|------|------|
| 619 | TestJobCoordinatorScanner.java |
| 474 | TestJobTimeoutChecker.java |
| 406 | TestJobConcurrency.java |
| 385 | TestJobCompletionProcessor.java |
| 384 | TestNopJobScheduleBizModel.java |
| 382 | TestNopJobFireBizModel.java |
| 331 | TestJobWorkerScanner.java |
| 327 | TestJobE2E.java |
| 287 | TestRpcJobInvoker.java |
| 233 | TestJobPartitionResolver.java |

---

### F-16-1: `DefaultJobCancelHandler` 零测试覆盖——取消通道完全未验证

**文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultJobCancelHandler.java`
**行号**: L22–L59（全文件 59 行）
**严重程度**: **P1**
**现状**: 全仓库无任何测试文件覆盖此类。该类是超时取消流程中唯一调用 `IJobInvoker.cancelAsync()` 的组件，被 `JobTimeoutCheckerImpl`（L67 注入）依赖。`TestJobCoordinatorScanner` 的 `testTimeoutCheckerInvokesCancelHandler`（L296）虽然在集成流中触发了取消流程，但仅验证了最终状态（task timeout），未验证 `DefaultJobCancelHandler` 自身的三个分支：invoker 为 null 静默返回、cancelAsync 正常完成、cancelAsync 抛异常吞掉。
**风险**: 若 `resolveInvoker` 的 Bean 查找逻辑退化或 `CancelJobExecutionContext` 构造出错，超时取消将静默失败，运行中的任务永远不会被通知取消。这是分布式 job 系统的关键安全通道。
**建议**: 新建 `TestDefaultJobCancelHandler.java`（单元测试，Mock `BeanContainer` 和 `IJobInvoker`），至少覆盖：① invoker 为 null 时不抛异常；② cancelAsync 正常完成；③ cancelAsync 抛异常时仅 log 不传播；④ fire.executorKind 为 null 时 fallback 到 schedule.executorKind。
**误报排除**: 已通过 `grep -r "TestCancelHandler"` 和 `grep -r "DefaultJobCancelHandler.*Test"` 确认无测试文件存在。
**审核状态**: 待确认

**证据**:
```java
// DefaultJobCancelHandler.java:22-46
@Override
public void cancelRunningTask(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
    String executorKind = resolveExecutorKind(schedule, fire);
    IJobInvoker invoker = resolveInvoker(executorKind);
    if (invoker == null) {
        LOG.debug("nop.job.cancel.invoker-not-found:scheduleId={},fireId={},taskId={},executorKind={}",
                schedule.getJobScheduleId(), fire.getJobFireId(), task.getJobTaskId(), executorKind);
        return;
    }
    try {
        CompletionStage<Boolean> promise = invoker.cancelAsync(
            new CancelJobExecutionContext(schedule, fire, task));
        if (promise != null) {
            promise.whenComplete((ret, err) -> {
                if (err != null) {
                    LOG.warn("nop.job.cancel.invoke-failed:scheduleId={},fireId={},taskId={}",
                            schedule.getJobScheduleId(), fire.getJobFireId(), task.getJobTaskId(), err);
                }
            });
        }
    } catch (Exception e) {
        LOG.warn("nop.job.cancel.invoke-failed:scheduleId={},fireId={},taskId={}",
                schedule.getJobScheduleId(), fire.getJobFireId(), task.getJobTaskId(), e);
    }
}

// DefaultJobCancelHandler.java:48-58
private IJobInvoker resolveInvoker(String executorKind) {
    if (executorKind == null || executorKind.isBlank() || !BeanContainer.isInitialized()) {
        return null;
    }
    Object bean = BeanContainer.tryGetBean(INVOKER_PREFIX + executorKind);
    return bean instanceof IJobInvoker ? (IJobInvoker) bean : null;
}

private String resolveExecutorKind(NopJobSchedule schedule, NopJobFire fire) {
    String kind = fire.getExecutorKind();
    return kind != null ? kind : schedule.getExecutorKind();
}
```
```bash
# 确认无测试文件
$ grep -r "TestCancelHandler\|DefaultJobCancelHandler" nop-job --include="*.java" | grep -i test
# (无输出)
```

---

### F-16-2: `RpcBroadcastTaskBuilder` 零测试覆盖——广播任务构建逻辑未验证

**文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java`
**行号**: L34–L100（全文件约 100 行）
**严重程度**: **P2**
**现状**: `DefaultJobTaskBuilder` 有独立的 `TestDefaultJobTaskBuilder`（49 行，2 个测试），但 `RpcBroadcastTaskBuilder` 完全没有测试。该类有 4 个 fallback 分支和一个核心循环路径（per-instance task creation with sharding metadata），共 5 条执行路径，全部未覆盖。
**风险**: 广播场景下 `shardingIndex`/`shardingTotal`/`targetHost` 注入错误会导致 RPC 路由到错误节点，且 fallback 逻辑退化时不会报错——静默降级为单任务模式。
**建议**: 新建 `TestRpcBroadcastTaskBuilder.java`，覆盖：① discoveryClient 返回 N 个实例时生成 N 个 task 且 payload 正确；② serviceName 为 null/blank 时 fallback；③ discoveryClient 为 null 时 fallback；④ 实例列表为空时 fallback；⑤ jobParams 为 null 时 fallback。
**误报排除**: 已通过 `grep -r "RpcBroadcastTaskBuilder"` 确认无测试文件。`TestDefaultJobTaskBuilder` 仅测试 `DefaultJobTaskBuilder`，不含 `RpcBroadcastTaskBuilder` 的逻辑。
**审核状态**: 待确认

**证据**:
```java
// RpcBroadcastTaskBuilder.java:34-69
public class RpcBroadcastTaskBuilder implements IJobTaskBuilder {

    private IDiscoveryClient discoveryClient;
    private final IJobTaskBuilder fallback = new DefaultJobTaskBuilder();

    @Inject
    public void setDiscoveryClient(@Nullable IDiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        Map<String, Object> jobParams = fire.getJobParamsSnapshotComponent().get_jsonMap();
        if (jobParams == null) {
            return fallback.buildTasks(fire);
        }

        String serviceName = (String) jobParams.get("serviceName");
        if (serviceName == null || serviceName.isBlank()) {
            return fallback.buildTasks(fire);
        }

        if (discoveryClient == null) {
            return fallback.buildTasks(fire);
        }

        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            return fallback.buildTasks(fire);
        }

        long now = System.currentTimeMillis();
        Map<String, Object> baseJobParamsSnapshot = emptyIfNull(jobParams);

        List<NopJobTask> tasks = new ArrayList<>();
        int total = instances.size();
        for (int i = 0; i < total; i++) {
            // 为每个 instance 创建一个 task，payload 中注入 targetHost/shardingIndex/shardingTotal
            ...
        }
```
```bash
$ grep -r "RpcBroadcastTaskBuilder" nop-job --include="*.java" | grep -i test
# (无输出)
```

---

### F-16-3: `JobWorkerScannerImpl` 异常路径测试不足——invoker 抛未捕获异常场景缺失

**文件**: `nop-job/nop-job-worker/src/test/java/io/nop/job/worker/engine/TestJobWorkerScanner.java`
**行号**: L68–L219（全部 4 个测试方法）
**严重程度**: **P2**
**现状**: `TestJobWorkerScanner` 有 4 个测试，覆盖了成功路径（L68）、返回 `JobFireResult.ERROR` 的显式失败（L112）、并发控制（L156/L186）。但缺少以下异常路径：① `invoker.invokeAsync()` 抛出未包装的 RuntimeException（而非返回 ERROR result）；② `taskStore.tryLockTasksForExecute()` 抛异常（锁失败场景）；③ `invoker.cancelAsync()` 在失败回调中的异常。`testWorkerExecutesTaskFailure` 使用的是 `CompletableFuture.completedFuture(JobFireResult.ERROR(...))`——这测试的是"invoker 返回错误结果"而非"invoker 抛异常"。
**风险**: 生产代码在 L190 的 `try-catch(Exception)` 负责将 invoker 抛出的意外异常转为 TASK_STATUS_FAILED，这条路径未被测试约束。如果该 catch 块的逻辑被误改（如遗漏 `task.setTaskStatus`），任务将永远卡在 RUNNING。
**建议**: 在 `TestJobWorkerScanner` 中增加：① `testWorkerInvokerThrowsException`：invoker 的 `invokeAsync` 抛 RuntimeException，验证 task 状态变为 FAILED 且 errorCode 非空；② `testWorkerLockFailsGracefully`：模拟 tryLockTasksForExecute 返回空列表，验证 scanOnce 不抛异常。
**误报排除**: 已确认 `testWorkerExecutesTaskFailure` 测试的是 `JobFireResult.ERROR` 返回值路径，不是 invoker 抛异常路径，两者是不同代码分支。
**审核状态**: 待确认

**证据**:
```java
// TestJobWorkerScanner.java — 全部测试方法列表:
@Test
public void testWorkerExecutesTaskSuccess() { ... }         // L68: 正常执行
@Test
public void testWorkerExecutesTaskFailure() { ... }         // L112: 返回 JobFireResult.ERROR
@Test
public void testMaxConcurrencySkipsWhenAtLimit() { ... }    // L156: 并发上限跳过
@Test
public void testMaxConcurrencyAllowsWhenBelowLimit() { ... } // L186: 并发未满放行
```

对比生产代码 `JobWorkerScannerImpl.java` 的异常处理路径：
```java
// JobWorkerScannerImpl.java:133-158
try {
    IntRangeSet partitions = ...;
    List<NopJobTask> tasks = taskStore.fetchWaitingTasks(batchSize, partitions);
    List<NopJobTask> lockedTasks = taskStore.tryLockTasksForExecute(tasks, ...);
    ...
} catch (Exception e) {
    LOG.error("nop.job.worker.scan-failed", e);
}

// JobWorkerScannerImpl.java:167-169
} catch (NopException e) {
    task.setTaskStatus(TASK_STATUS_FAILED);
    ...
}

// JobWorkerScannerImpl.java:190-200
try {
    CompletionStage<JobFireResult> future = invoker.invokeAsync(...);
    ...
} catch (Exception e) {
    task.setTaskStatus(TASK_STATUS_FAILED);
    ...
}
```

```java
// TestJobWorkerScanner.java:112-153 — 当前"失败"测试使用的是返回值，不是抛异常
@Test
public void testWorkerExecutesTaskFailure() {
    ...
    container.registerBean("nopJobInvoker_test", new IJobInvoker() {
        @Override
        public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
            return CompletableFuture.completedFuture(JobFireResult.ERROR(
                    new ErrorBean("JOB_TEST_ERROR").description("worker failed")
            ));  // ← 返回 ERROR result，不是抛异常
        }
        ...
    });
    ...
}
```

---

### F-16-4: `JobTaskStoreImpl.tryLockTasksForExecute` 无独立单元测试

**文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java`
**行号**: L67–L81
**严重程度**: **P2**
**现状**: `tryLockTasksForExecute` 是 worker 端最关键的并发原语，通过 `tryUpdateManyWithVersionCheck` 实现乐观锁。该方法的测试仅出现在 `TestJobConcurrency.testTwoWorkersCompeteForSameTask`（L118–L149）中作为并发集成测试的一部分，没有针对边界条件的独立测试。具体缺失：① 空列表输入（tasks == null）；② 部分锁定成功（列表中某些 task 版本冲突）；③ lockTimeoutMs 为 0 或负数时 `Math.max(lockTimeoutMs, 1L)` 的行为。
**风险**: 乐观锁是多实例 worker 安全运行的基础。若 `tryUpdateManyWithVersionCheck` 返回部分成功的列表而调用方假设全量成功，可能导致任务状态不一致。目前代码在 `JobWorkerScannerImpl` L151 处理 `lockedTasks` 时未校验长度是否等于原始 `tasks` 长度——这个隐式假设没有被测试约束。
**建议**: 在 `TestJobStoreImpl` 中增加：① `testTryLockTasksForExecute_emptyList_returnsEmpty`；② `testTryLockTasksForExecute_versionConflict_returnsPartialList`（通过两次锁定同一 task 模拟）；③ `testTryLockTasksForExecute_lockTimeoutClampedToOne`。
**误报排除**: `TestJobConcurrency` 的 L118–L149 确实间接测试了乐观锁竞争，但那是集成级别的"两人竞争同一任务"场景，不是 `tryLockTasksForExecute` 自身的边界条件单元测试。
**审核状态**: 待确认

**证据**:
```java
// JobTaskStoreImpl.java:67-81
@Override
public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks,
        String workerInstanceId, long lockTimeoutMs) {
    if (tasks == null || tasks.isEmpty()) {
        return List.of();
    }
    java.sql.Timestamp lockTime = new java.sql.Timestamp(
            taskDao().getDbEstimatedClock().getMaxCurrentTimeMillis()
            + Math.max(lockTimeoutMs, 1L));
    for (NopJobTask task : tasks) {
        task.setTaskStatus(TASK_STATUS_CLAIMED);
        task.setWorkerInstanceId(workerInstanceId);
        task.setUpdatedBy("system");
        task.setUpdateTime(lockTime);
    }
    return taskDao().tryUpdateManyWithVersionCheck(tasks);
}
```

对比 `TestJobStoreImpl.java`（仅覆盖 schedule 锁）：
```java
// TestJobStoreImpl.java:46-56 — 仅覆盖了 scheduleStore.tryLockSchedulesForPlan
@Test
public void testFetchAndLockSchedules() {
    ...
    List<NopJobSchedule> locked = scheduleStore.tryLockSchedulesForPlan(
        dueSchedules, "planner-1", 1000);
    assertEquals(1, locked.size());
}
// 无 tryLockTasksForExecute 的独立测试
```

---

### F-16-5: `JobCompletionProcessorImpl` 的 `scanOnce` 顶层 catch 路径未测试

**文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java`
**行号**: L130–L146
**严重程度**: **P3**
**现状**: `TestJobCompletionProcessor` 有 10 个测试方法，质量较好——覆盖了 retry bridge 调用/不调用、retry policy 优先级、alarm 调用/不调用、exception 不阻塞完成（L135/L214）、部分任务未完成时不终结 fire（L234）。但 `scanOnce()` 的顶层 `catch(Exception)` 路径未被测试：如果 `fireStore.fetchRunningFires` 或 `partitionResolver.resolvePartitions` 抛异常，整个 scan 被吞掉仅记日志。
**风险**: 低风险。`scanOnce` 是定时循环调用的，单次失败不影响下次 scan。但如果 `fetchRunningFires` 持续抛异常（如 DB 连接断开），completion processor 将静默停工且无外部告警。
**建议**: 在 `TestJobCompletionProcessor` 中增加 `testScanOnce_exceptionInFetchDoesNotPropagate`：让 `MockFireStore.fetchRunningFires` 抛 RuntimeException，验证 `processor.scanOnce()` 不抛异常。
**误报排除**: 已确认 L214 的 `testAlarm_exceptionDoesNotBlockCompletion` 测试的是 `handleRetryAndAlarm` 内部的 alarm 异常，不是 `scanOnce` 顶层 catch。
**审核状态**: 待确认

**证据**:
```java
// JobCompletionProcessorImpl.java:130-146
void scanOnce() {
    try {
        IntRangeSet partitions = partitionResolver != null
            ? partitionResolver.resolvePartitions() : null;
        List<NopJobFire> fires = fireStore.fetchRunningFires(batchSize, partitions);
        int completedCount = 0;
        for (NopJobFire fire : fires) {
            if (tryCompleteFireAndGetStatus(fire) != null) {
                completedCount++;
            }
        }
        if (completedCount > 0) {
            completionMetrics.onFiresCompleted(completedCount);
        }
    } catch (Exception e) {
        LOG.error("nop.job.completion.scan-failed", e);  // ← 此路径未测试
    }
}
```

```java
// TestJobCompletionProcessor.java — 10 个测试方法列表:
void testRetryBridge_calledWhenFireFailedWithRetryPolicy()       // L61
void testRetryBridge_notCalledWhenNoRetryPolicy()                // L83
void testRetryBridge_usesFirePolicyOverSchedulePolicy()          // L99
void testRetryBridge_notCalledOnTimeout()                         // L118
void testRetryBridge_exceptionDoesNotBlockCompletion()            // L135 ← 内部异常
void testAlarm_calledOnFireFailed()                               // L154
void testAlarm_calledOnFireTimeout()                              // L176
void testAlarm_notCalledOnSuccess()                               // L195
void testAlarm_exceptionDoesNotBlockCompletion()                  // L214 ← 内部异常
void testFireNotCompletedWhenTasksPending()                       // L234
// 无 scanOnce 顶层异常测试
```

---

### F-16-6: `JobTimeoutCheckerImpl` 的 worker 恢复后 suspicious 状态不回退行为未验证

**文件**: `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestJobTimeoutChecker.java`
**行号**: L146–L239（worker liveness 相关测试）
**严重程度**: **P3**
**现状**: `TestJobTimeoutChecker` 有 9 个测试，覆盖了 dispatch timeout 标记/不标记/告警/统计/禁用（L59–L131）和 worker liveness 标记 suspicious→timeout 转换（L146–L239）。但缺少一个重要场景：worker 被标记为 suspicious 后重新上线（aliveWorkerIds 再次包含该 worker），任务状态是否会从 SUSPICIOUS 回退到 RUNNING？从生产代码 L207–L224 看，`tryMarkSuspiciousIfWorkerGone` 没有 "回退" 逻辑——一旦标记为 suspicious，即使 worker 恢复，也不会回退。这个行为没有被测试显式验证。
**风险**: 低风险。suspicious 只是中间状态，不影响最终超时判定。但如果 operator 依赖 suspicious 做告警，可能在 worker 短暂重启后产生误告。
**建议**: 增加 `testSuspiciousTask_notRevertedWhenWorkerComesBack`：创建一个 suspicious task，将 worker 放回 aliveWorkerIds，调用 scanOnce，验证 task 仍为 SUSPICIOUS。
**误报排除**: `testSuspiciousToTimeout_conversion`（L192）测试的是 suspicious→timeout 的推进，不是 worker 恢复后的行为。
**审核状态**: 待确认

**证据**:
```java
// TestJobTimeoutChecker.java — worker liveness 相关测试:
@Test
void testWorkerLiveness_marksSuspiciousThenTimeoutWhenWorkerGone() { ... } // L146
@Test
void testSuspiciousToTimeout_conversion() { ... }                          // L192
@Test
void testExistingTaskTimeout_unchanged() { ... }                           // L240
```

```java
// JobTimeoutCheckerImpl.java:207-224 — 无 "suspicious 回退" 逻辑
private void tryMarkSuspiciousIfWorkerGone(NopJobTask task, Set<String> aliveWorkerIds) {
    if (task.getWorkerInstanceId() == null) return;
    if (aliveWorkerIds.contains(task.getWorkerInstanceId())) return;
    if (task.getSuspiciousSince() == null) {
        task.setSuspiciousSince(new Timestamp(currentTime));
        task.setTaskStatus(TASK_STATUS_SUSPICIOUS);
        taskStore.updateTask(task);
    }
    // 无 else 分支处理 "worker 恢复后 suspicious 标记回退"
}
```

---

### F-16-7: 批处理（batchSize > 1）场景在 coordinator 全部 Scanner 中未被测试

**文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java`、`JobDispatcherScannerImpl.java`、`JobCompletionProcessorImpl.java`
**行号**: 各 Scanner 的 `scanOnce` 方法
**严重程度**: **P3**
**现状**: 所有 coordinator 和 worker 的集成测试（`TestJobCoordinatorScanner`、`TestJobWorkerScanner`、`TestJobE2E`）在准备数据时都只创建 1 条 schedule/fire/task，batchSize 虽然设为 10 或 100，但实际 fetch 永远只返回 1 条记录。没有测试验证当 fetch 返回多条记录时，for 循环中的状态更新是否正确批量完成。
**风险**: 低风险。循环逻辑简单，单条和多条在逻辑上等价。但如果未来添加"中途 break"或"部分失败后继续"逻辑，缺乏 batch 测试可能导致回归。
**建议**: 在 `TestJobCoordinatorScanner` 中增加 `testBatchCompletion_multipleFires`：创建 3 个 fire，每个 fire 下有已完成的 task，验证一次 scanOnce 完成 3 个 fire 的状态更新。
**误报排除**: `TestJobConcurrency` 的 `testTwoPlannersCompeteForSameSchedule`（L63）虽然涉及多个 planner，但每个 planner 只处理 1 条 schedule，不是批处理场景。
**审核状态**: 待确认

**证据**:
```java
// JobPlannerScannerImpl.java — batch fetch
List<NopJobSchedule> due = scheduleStore.fetchDueSchedules(batchSize, partitions);

// JobDispatcherScannerImpl.java — batch fetch
List<NopJobFire> fires = fireStore.fetchWaitingFires(batchSize, partitions);

// JobCompletionProcessorImpl.java — batch fetch
List<NopJobFire> fires = fireStore.fetchRunningFires(batchSize, partitions);
```
```bash
$ grep -rn "test.*[Bb]atch\|batchSize.*[0-9]" nop-job --include="*.java" | grep -i test
# (无输出——所有测试使用的都是单条记录场景)
```

```java
// TestJobCoordinatorScanner.java:93-94 — batchSize 设为 10 但数据只有 1 条
worker.setBatchSize(10);
worker.setAssignedPartitions("1");

// TestJobWorkerScanner.java:93-94 — 同样模式
worker.setBatchSize(10);
worker.setAssignedPartitions("1");
```

---

### F-16-8: `NopJobTaskBizModel` 缺少独立测试文件

**文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobTaskBizModel.java`
**行号**: L1–L15（全文件）
**严重程度**: **P3**
**现状**: 该 BizModel 当前仅继承 `CrudBizModel` 无自定义方法，确实没有独特的业务逻辑需要测试。但同类级别的 `TestNopJobScheduleBizModel`（384 行）和 `TestNopJobFireBizModel`（382 行）都有完整的测试，包括状态机转换和错误路径验证。
**风险**: 当前无风险。如果未来为 `NopJobTaskBizModel` 添加自定义方法（如批量取消、重试），将缺少测试基础。
**建议**: 当前可接受。若后续添加自定义方法，必须同步添加测试。可作为代码审查 checklist 的守卫项。
**误报排除**: `NopJobTaskBizModel` 当前确实只有构造函数，没有自定义业务方法，因此"缺少测试"的严重程度较低，不构成 P1/P2。
**审核状态**: 待确认

**证据**:
```java
// NopJobTaskBizModel.java:1-15 (全文件)
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.job.biz.INopJobTaskBiz;
import io.nop.job.dao.entity.NopJobTask;

@BizModel("NopJobTask")
public class NopJobTaskBizModel extends CrudBizModel<NopJobTask>
        implements INopJobTaskBiz {
    public NopJobTaskBizModel(){
        setEntityName(NopJobTask.class.getName());
    }
}
```
```bash
$ find nop-job -name "TestNopJobTaskBizModel.java"
# (无输出)
```

---

## 审计总结

### 发现统计

| 编号 | 严重程度 | 组件 | 问题 |
|------|----------|------|------|
| F-16-1 | **P1** | `DefaultJobCancelHandler` | 零测试覆盖，取消通道未验证 |
| F-16-2 | **P2** | `RpcBroadcastTaskBuilder` | 零测试覆盖，5 条执行路径未验证 |
| F-16-3 | **P2** | `TestJobWorkerScanner` | 异常路径不足，invoker 抛异常未测试 |
| F-16-4 | **P2** | `JobTaskStoreImpl.tryLockTasksForExecute` | 无独立单元测试，边界条件未覆盖 |
| F-16-5 | **P3** | `TestJobCompletionProcessor` | scanOnce 顶层 catch 路径未测试 |
| F-16-6 | **P3** | `TestJobTimeoutChecker` | worker 恢复后 suspicious 不回退行为未验证 |
| F-16-7 | **P3** | 全部 coordinator Scanner | 批处理场景（batchSize > 1）未测试 |
| F-16-8 | **P3** | `NopJobTaskBizModel` | 缺少独立测试文件（当前无自定义逻辑） |

### 优先级行动项

1. **P1 — 立即修复**: 为 `DefaultJobCancelHandler` 添加单元测试（F-16-1）。这是任务取消的唯一通道，零覆盖不可接受。
2. **P2 — 短期修复**: 为 `RpcBroadcastTaskBuilder` 添加单元测试（F-16-2）、为 `TestJobWorkerScanner` 添加 invoker 异常路径测试（F-16-3）、为 `tryLockTasksForExecute` 添加独立边界条件测试（F-16-4）。

### 积极发现（不需要行动，仅供记录）

- **`TestJobCompletionProcessor`** 质量较高：10 个测试方法覆盖了 retry bridge、alarm、异常不阻塞完成、部分完成不终结 fire 等场景，且使用 Mock 隔离。其中 `testRetryBridge_exceptionDoesNotBlockCompletion`（L135）和 `testAlarm_exceptionDoesNotBlockCompletion`（L214）是错误路径测试的良好范例。
- **`TestJobTimeoutChecker`** 覆盖较全面：9 个测试方法覆盖了 dispatch timeout（5 个角度）和 worker liveness suspicious→timeout 转换。
- **`TestJobConcurrency`** 是高质量的集成测试：验证了 planner/dispatcher/worker 三层乐观锁竞争，包含两人竞争同一 schedule、同一 fire、同一 task 的场景。
- **`TestNopJobScheduleBizModel`** 和 **`TestNopJobFireBizModel`** 都有充分的错误路径测试（大量 `assertThrows`），包括非法状态转换、已归档操作拒绝等。
- **`TestJobPartitionResolver`** 有 10 个测试方法，覆盖了 null/empty instances、static partition 优先、集群分区、stabilization window 阻塞/恢复等边界条件。
- **`TestJobE2E`** 提供了端到端集成覆盖，验证了从 planner→dispatcher→worker→completion 的完整流程。
- **`TestBlockStrategies`** 覆盖了 discard/overlay/recovery 三种阻塞策略。

## 深挖第 2 轮追加

### [16-02] P3 — BizModel 和 Store 测试中硬编码状态常量，未引用 `_NopJobCoreConstants`

**文件 A**：`nop-job/nop-job-service/src/test/java/io/nop/job/service/entity/TestNopJobScheduleBizModel.java` 多处

**文件 B**：`nop-job/nop-job-service/src/test/java/io/nop/job/service/entity/TestNopJobFireBizModel.java` 多处

**证据**：
```java
// TestNopJobScheduleBizModel.java 中使用硬编码数字
schedule.setScheduleStatus(1);  // 应使用 SCHEDULE_STATUS_ENABLED
schedule.setScheduleStatus(2);  // 应使用 SCHEDULE_STATUS_DISABLED

// TestNopJobFireBizModel.java 中类似模式
fire.setFireStatus(2);  // 应使用 FIRE_STATUS_EXECUTING
fire.setFireStatus(3);  // 应使用 FIRE_STATUS_COMPLETED
```

**严重程度**：P3

**现状**：多个测试文件中直接使用硬编码数字设置状态字段值，而非引用 `_NopJobCoreConstants` 中定义的常量。同样的问题也存在于 Store 实现类中。

**风险**：状态码含义变化时测试不会同步更新，降低测试的可信度。新开发者无法从测试代码直接理解状态值的含义。

**建议**：将测试中的硬编码状态值替换为 `_NopJobCoreConstants` 常量引用。这是代码质量改进，不影响测试正确性。

**误报排除**：这是代码质量问题而非测试覆盖缺失。当前硬编码值与常量定义一致，测试逻辑正确，仅影响可维护性。

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| F-16-1 | DefaultJobCancelHandler 零测试覆盖 | 保留 P1 | 源码确认 4 个分支（invoker==null 静默返回、正常完成、异常吞掉、executorKind fallback）全部未测试，是超时取消唯一通道。 |
| F-16-2 | RpcBroadcastTaskBuilder 零测试覆盖 | 降级 P3 | 无测试确认，但 4 个 fallback 到 `DefaultJobTaskBuilder`（已有测试），最差降级为单任务，生产影响有限。 |
| F-16-3 | JobWorkerScannerImpl 异常路径测试不足 | 保留 P2 | L190 try-catch(Exception) 路径未被测试约束。现有 `testWorkerExecutesTaskFailure` 测的是返回 ERROR 而非抛异常，两者是不同分支。 |
| F-16-4 | JobTaskStoreImpl.tryLockTasksForExecute 无独立单元测试 | 降级 P3 | `TestJobConcurrency` L118-149 已有集成级覆盖。缺边界条件测试有价值但乐观锁有数据库级保障。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| F-16-1 | P1 | `DefaultJobCancelHandler.java` | 零测试覆盖，超时取消唯一通道 4 个分支全部未测试 |
| F-16-2 | P3 | `RpcBroadcastTaskBuilder.java` | 零测试覆盖，有 DefaultJobTaskBuilder fallback 兜底 |
| F-16-3 | P2 | `JobWorkerScannerImpl.java` | 异常路径测试不足，try-catch(Exception) 分支未覆盖 |
| F-16-4 | P3 | `JobTaskStoreImpl.java` | tryLockTasksForExecute 无独立单元测试，有集成级覆盖 |
