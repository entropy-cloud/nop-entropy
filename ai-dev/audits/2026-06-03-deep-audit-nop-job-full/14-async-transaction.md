# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] JobWorkerScannerImpl.handleExecutionResult 乐观锁失败时静默丢弃任务结果

- **文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:202-259`
- **证据片段**:
  ```java
  boolean updated = taskStore.updateTask(task);
  if (!updated) {
      NopJobTask freshTask = taskStore.loadTask(jobTaskId);
      if (freshTask.getTaskStatus() == TASK_STATUS_TIMEOUT
              || freshTask.getTaskStatus() == TASK_STATUS_CANCELED
              || freshTask.getTaskStatus() == TASK_STATUS_SUSPICIOUS) {
          return;
      }
      // 若 freshTask 仍在 RUNNING/CLAIMED 状态，结果被静默丢弃
      // 没有重试逻辑
  }
  ```
- **严重程度**: P2
- **现状**: 当 updateTask 因乐观锁冲突失败，且冲突原因不是 timeout/cancel/suspicious 时，任务结果被静默丢弃。任务仍留在 CLAIMED/RUNNING 状态，后续会被 timeout checker 超时处理，导致实际成功的执行被记录为超时。
- **风险**: 在多 Worker 或 partition 重叠场景下可能发生，单 Worker 场景概率极低。
- **建议**: 增加重试逻辑或在非终端状态下记录 WARN 日志并设置 task 结果。
- **信心水平**: 高
- **误报排除**: 同文件其他位置（如 completeTaskWithFailure）有正确的 null 检查和状态校验，此处是遗漏。
- **复核状态**: 未复核

### [维度14-02] JobPlannerScannerImpl.copyMap 方法名误导

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:229-231`
- **证据片段**:
  ```java
  private Map<String, Object> copyMap(Map<String, Object> map) {
      return map == null ? Collections.emptyMap() : map;  // 不执行拷贝
  }
  ```
- **严重程度**: P3
- **现状**: 方法名暗示创建独立副本，实际返回同一引用。功能安全（set_jsonValue 会序列化），但命名误导。
- **建议**: 重命名为 `emptyIfNull`。
- **信心水平**: 高
- **误报排除**: 功能上安全（set_jsonValue 会序列化 Map），仅命名问题。
- **复核状态**: 未复核

## 无问题确认

| 检查项 | 结论 |
|--------|------|
| txn() 使用 | 未发现直接调用 |
| @Transactional | Store 层关键操作使用 REQUIRES_NEW，正确划定事务边界 |
| afterCommit | 未发现调用，使用 CrudBizModel 的 afterEntityChange |
| 长事务风险 | cancelFire 事务操作量可控 |
| 异步错误处理 | 各 Scanner/Worker 有完整 try-catch，互不影响 |
| 并发竞态 | 乐观锁 + volatile + synchronized + @SingleSession |
| 资源泄漏 | Scanner Future 正确 cancel，无手动资源 |
