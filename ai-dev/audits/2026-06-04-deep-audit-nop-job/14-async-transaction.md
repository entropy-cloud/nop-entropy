# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] Timeout Checker 在与 Completion Processor 竞态时可能发送误报警告

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:267-361`
- **证据片段**:
  ```java
  fireStore.completeFireAndUpdateSchedule(fire, schedule);
  // 无论更新是否成功，都发送告警
  if (alarmHandler != null) {
      JobAlarmEvent event = new JobAlarmEvent(...);
      alarmHandler.onFireTimeout(event);
  }
  ```
- **严重程度**: P3
- **现状**: `completeFireAndUpdateSchedule` 可能因 fire 已终态而静默返回，但告警仍被发送。
- **风险**: 运维人员收到"任务超时"告警但实际任务已成功完成。
- **建议**: 让 `completeFireAndUpdateSchedule` 返回 boolean，仅在更新成功时发送告警。
- **信心水平**: 很可能
- **误报排除**: 告警发送缺少条件守卫是具体的代码缺陷。
- **复核状态**: 未复核

---

### [维度14-02] copyMap 方法名误导——未执行实际复制

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:228-230`
- **证据片段**:
  ```java
  private Map<String, Object> copyMap(Map<String, Object> map) {
      return map == null ? Collections.emptyMap() : map;
  }
  ```
- **严重程度**: P3
- **现状**: 方法名暗示创建防御性副本，但实际返回原始 Map 引用。
- **风险**: 方法名与行为不一致可能在后续维护中引入 bug。
- **建议**: 改为 `return new HashMap<>(map)` 或重命名为 `nullToEmpty`。
- **信心水平**: 确定
- **误报排除**: 方法名明确承诺 `copy` 但未执行复制。
- **复核状态**: 未复核

---

### [维度14-03] Scanner 生命周期中 scheduleWithFixedDelay 与 cancel(false) 的竞态

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:82-97`
- **证据片段**:
  ```java
  public synchronized void stopScanning() {
      running = false;
      if (scanFuture != null) {
          scanFuture.cancel(false);
          scanFuture = null;
      }
  }
  ```
- **严重程度**: P3
- **现状**: `stopScanning()` 返回后可能仍有 1 个 batch 的 scanOnce 在执行中。
- **风险**: shutdown 场景下存在已提交但未完成的 scan 操作。不会导致数据损坏。
- **建议**: 如需严格 shutdown 语义，增加 scan 完成等待机制。当前行为对后台定时任务已属合理。
- **信心水平**: 很可能
- **误报排除**: 指向具体的 shutdown 语义不匹配风险。
- **复核状态**: 未复核
