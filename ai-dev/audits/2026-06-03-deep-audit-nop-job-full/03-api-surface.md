# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] NopRetryJobRetryBridge.onFireFailed 始终返回 null，retryRecordId 关联断裂

- **文件**: `nop-job/nop-job-retry-adapter/src/main/java/io/nop/job/retry/adapter/NopRetryJobRetryBridge.java:31-70`
- **证据片段**:
  ```java
  @Override
  public String onFireFailed(JobFireFailedEvent event) {
      try {
          IRetryTask task = retryEngine.newRetryTask(SERVICE_NAME, SERVICE_METHOD)
                  .withPolicyId(event.getRetryPolicyId())
                  .withIdempotentId(event.getJobFireId());
          task.callAsync(request, null)
                  .whenComplete((resp, err) -> { /* 仅日志 */ });
          return null; // 始终返回 null
      } catch (Exception e) {
          return null;
      }
  }
  ```
- **严重程度**: P2
- **现状**: `IJobRetryBridge.onFireFailed()` 的返回值定义为 `String retryRecordId`，调用方 `JobCompletionProcessorImpl.handleRetryAndAlarm()` 会将此 ID 写入 `NopJobFire.retryRecordId` 字段。由于始终为 null，此关联字段永远不会被填充。
- **风险**: 无法从 Fire 记录反向查找对应的重试记录。原因：callAsync 是异步提交，无法同步获取重试记录 ID。这是接口设计与实现能力之间的设计间隙。
- **建议**: 改为同步获取 retryRecordId，或在异步回调中通过 Store 层回填 retryRecordId。
- **信心水平**: 高
- **误报排除**: 这不是"看起来不优雅"——retryRecordId 是 ORM 模型中明确定义的字段且有索引（IDX_NOP_JOB_FIRE_RETRY），但永远为 null 导致索引和关联关系完全失效。
- **复核状态**: 未复核

### [维度03-02] resolveTriggeredBy 在两个 BizModel 中完全重复

（与维度07-02相同发现，此处不重复详述。参见 07-bizmodel-conformance.md）

## 无问题确认

| 检查项 | 结论 |
|--------|------|
| I*Biz 接口覆盖 | 3 个接口覆盖所有自定义方法，签名完全匹配 |
| Map<String,Object> 使用 | triggerNow 的 overrideParams 是作业调度领域的合理动态结构 |
| 死 API 检查 | 所有 @BizMutation 方法均被 action-auth 配置和使用 |
| xmeta 字段权限 | 引擎控制字段正确标记为不可写 |
