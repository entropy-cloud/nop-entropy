# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] NopJobConfigs 和 NopJobConstants 为空壳接口

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/NopJobConfigs.java` 和 `NopJobConstants.java`
- **证据片段**:
  ```java
  public interface NopJobConfigs {
  }
  ```
- **严重程度**: P3
- **现状**: 两个接口为空声明体。实际配置键通过 `@InjectValue` 分散在各 Scanner 实现中。
- **风险**: 开发者查找配置项时无法从集中位置获得参考。
- **建议**: 将分散的 `@InjectValue` 配置键集中注册，或删除空壳接口。
- **信心水平**: 确定
- **误报排除**: 文件确实为空。
- **复核状态**: 未复核

---

### [维度20-02] IJobRetryBridge 契约返回值在 NopRetryJobRetryBridge 中被忽略

- **文件**: `nop-job/nop-job-retry-adapter/src/main/java/io/nop/job/retry/adapter/NopRetryJobRetryBridge.java:65`
- **证据片段**:
  ```java
  task.callAsync(request, null)
      .whenComplete((resp, err) -> { /* logging only */ });
  return null;  // 总是返回 null
  ```
  对比调用方 `JobCompletionProcessorImpl.java:235-238`：
  ```java
  String retryRecordId = retryBridge.onFireFailed(event);
  if (retryRecordId != null) {
      fire.setRetryRecordId(retryRecordId);
  }
  ```
- **严重程度**: P2
- **现状**: `NopRetryJobRetryBridge` 总是返回 null，导致 `fire.setRetryRecordId` 永远不会执行。
- **风险**: `retryRecordId` 字段永远为空，ORM 中为此字段建的索引 `IX_NOP_JOB_FIRE_RETRY` 浪费存储空间。
- **建议**: 在 `callAsync` 回调中从 response 提取 retryRecordId 并回填，或修改接口为异步回调模式。
- **信心水平**: 很可能
- **误报排除**: `return null` 是硬编码，调用方明确依赖非 null 返回值。
- **复核状态**: 未复核
