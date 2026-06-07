# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] NopRetryJobRetryBridge.onFireFailed 始终返回 null

- **文件**: `nop-job-retry-adapter/src/main/java/io/nop/job/retry/adapter/NopRetryJobRetryBridge.java:65`
- **证据片段**:
```java
return null; // 总是返回 null
```
调用方（JobCompletionProcessorImpl.java:235-238）：
```java
String retryRecordId = retryBridge.onFireFailed(event);
if (retryRecordId != null) {
    fire.setRetryRecordId(retryRecordId);
    fireStore.updateRetryRecordId(fire.getJobFireId(), retryRecordId);
}
```
- **严重程度**: P2
- **现状**: IJobRetryBridge.onFireFailed() 返回 String，调用方期望 retryRecordId。但 NopRetryJobRetryBridge 因 IRetryTask.callAsync() 异步限制始终返回 null。fire.retryRecordId 永远不会被填充。
- **风险**: 无法从 fire 表追溯到 retry 记录。
- **建议**: 如果 retry 系统提供了同步获取 recordId 的能力，应更新 bridge 实现。或修改接口设计，在异步回调中更新 retryRecordId。
- **信心水平**: 高
- **误报排除**: 测试 TestNopRetryJobRetryBridge:44 显式断言 assertNull，说明开发者有意为之但接受了功能缺陷。
- **复核状态**: 未复核

### 正向确认

- IJobRetryBridge 接口签名与实现一致
- IJobScheduler 和 IJobInvoker 接口稳定，LocalJobScheduler/RpcJobInvoker 正确实现
- NopRetryJobRetryBridge 正确调用 IRetryEngine（来自 nop-retry-api）
