# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] IJobRetryBridge 返回值语义与异步实现不匹配

- **文件**: `nop-job/nop-job-retry-adapter/src/main/java/io/nop/job/retry/adapter/NopRetryJobRetryBridge.java:20-21, 37-65`
- **证据片段**:
  ```java
  // 接口返回 String retryRecordId
  @Override
  public String onFireFailed(JobFireFailedEvent event) {
      task.callAsync(request, null).whenComplete((resp, err) -> { /* 仅日志 */ });
      return null;  // 始终返回 null
  }
  ```
- **严重程度**: P2
- **现状**: IJobRetryBridge.onFireFailed 返回类型为 String（retryRecordId），调用方 JobCompletionProcessorImpl 会用此值更新 fire 记录。但 NopRetryJobRetryBridge 因异步模式始终返回 null，导致 retryRecordId 永远不被填充。
- **风险**: ORM 模型中 retryRecordId 字段有索引但永远为 null，关联关系失效。
- **建议**: 改为同步获取 retryRecordId，或在异步回调中回填。
- **信心水平**: 高
- **误报排除**: 这与维度03-01是同一问题的不同视角（03 从 API 契约角度，20 从跨模块一致性角度）。
- **复核状态**: 未复核

### [维度20-02] rpcBroadcast executor kind 映射到普通 RpcJobInvoker（缺少注释说明）

- **文件**: `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/beans/app-service.beans.xml:9-11`
- **严重程度**: P3
- **现状**: rpcBroadcast 映射到 RpcJobInvoker（与 rpc 相同的类）。广播逻辑由 coordinator 侧 RpcBroadcastTaskBuilder 完成，不需要专门的广播 invoker。
- **建议**: 添加注释说明广播逻辑的分工。
- **信心水平**: 高
- **误报排除**: 架构上合理（task 拆分在 coordinator，每个 task 独立执行用 RpcJobInvoker），但缺少文档说明。
- **复核状态**: 未复核
