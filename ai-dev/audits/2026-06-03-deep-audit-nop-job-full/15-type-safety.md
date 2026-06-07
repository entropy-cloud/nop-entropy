# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] JobWorkerScannerImpl Integer 自动拆箱导致 NPE 风险

- **文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:205-207, 241-243`
- **证据片段**:
  ```java
  NopJobTask task = taskStore.loadTask(jobTaskId);
  if (task.getTaskStatus() == _NopJobCoreConstants.TASK_STATUS_TIMEOUT
          || task.getTaskStatus() == _NopJobCoreConstants.TASK_STATUS_CANCELED
          || task.getTaskStatus() == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS) {
      return;
  }
  ```
- **严重程度**: P2
- **现状**: `getTaskStatus()` 返回 `Integer`，常量为 `int`。`Integer == int` 触发自动拆箱。若 `getTaskStatus()` 返回 `null` 则抛出 `NullPointerException`。同文件第 263-266 行的 `completeTaskWithFailure` 方法正确地先做了 null 检查。
- **风险**: 如果 task 的 taskStatus 列为 NULL（数据库未设 NOT NULL 或插入时遗漏），Worker 扫描器会因 NPE 中断单条任务处理。后台扫描器有外层 try-catch 保护不会影响其他任务，但该任务会被跳过。
- **建议**: 在第 205 行之前加 `if (task.getTaskStatus() == null) return;` 或使用 `Objects.equals()` 模式。
- **信心水平**: 高
- **误报排除**: 同一文件中存在正确的 null 检查模式（第 263 行），说明开发者知道需要 null 检查，此处是遗漏。
- **复核状态**: 未复核

### [维度15-02] RpcBroadcastTaskBuilder 无类型校验的向下转型

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java:50`
- **证据片段**:
  ```java
  String serviceName = (String) jobParams.get("serviceName");
  ```
- **严重程度**: P3
- **现状**: 从 `Map<String, Object>` 取值后直接 `(String)` 强转。若值不是 String 则抛出 ClassCastException 且错误信息无法帮助定位。同模块的 RpcJobInvoker 对类似场景使用了 `requireString()` 辅助方法（带 instanceof 校验和描述性 NopException）。
- **建议**: 引入与 RpcJobInvoker.requireString() 相同的类型校验逻辑。
- **信心水平**: 高
- **误报排除**: RpcJobInvoker 的 requireString() 说明同模块已有更好的类型校验模式。
- **复核状态**: 未复核

### [维度15-03] JobAlarmEvent 和 JobFireFailedEvent 缺少 @DataBean 注解

- **文件**: `nop-job/nop-job-api/src/main/java/io/nop/job/api/alarm/JobAlarmEvent.java`, `JobFireFailedEvent.java`
- **严重程度**: P3（信息性）
- **现状**: 同模块其他 DTO 类均标注了 @DataBean（JobFireResult、JobInstanceState、JobSpec 等）。这两个事件类作为跨模块传递的事件值对象缺少该注解。
- **建议**: 在两个类上添加 @DataBean 注解保持一致性。
- **信心水平**: 高
- **误报排除**: 同模块其他 6 个 DTO 类均有 @DataBean，这两个遗漏是客观事实。
- **复核状态**: 未复核

## 无问题确认

| 检查项 | 结论 |
|--------|------|
| 原始类型遗漏 | 未发现 |
| @SuppressWarnings("unchecked") | 4 处全部合理（框架 API 限制不可避免） |
| 接口契约泛型精度 | IJobScheduler、IJobInvoker 等泛型参数完整 |
| Object 类型可收窄 | BeanContainer.tryGetBean() 返回 Object 是 IoC 框架约定 |
| 死代码 | RpcBroadcastTaskBuilder.emptyIfNull() 无调用点（信息性） |
