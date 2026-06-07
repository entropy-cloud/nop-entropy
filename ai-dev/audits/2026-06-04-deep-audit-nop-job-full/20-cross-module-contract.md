# 维度 20：跨模块契约一致性

## 审计范围

nop-job-api 对外接口、retry-adapter 桥接、RpcJobInvoker 与 nop-rpc 集成。

## 第 1 轮（初审）发现

### [维度20-01] IJobRetryBridge.onFireFailed() 返回值 String 未被使用

- **文件**: `nop-job/nop-job-api/src/main/java/io/nop/job/api/retry/IJobRetryBridge.java:4`
- **证据片段**:
  ```java
  String onFireFailed(JobFireFailedEvent event);
  ```
  两个实现均返回 null，调用方未使用返回值。
- **严重程度**: P3
- **现状**: 接口返回 String，但所有实现返回 null 且调用方不使用返回值。
- **风险**: 接口语义模糊，误导使用者以为返回值有实际意义。
- **建议**: 将返回类型改为 void 或在 Javadoc 中明确说明返回值语义。
- **信心水平**: 高
- **误报排除**: 两个实现和调用方均可验证返回值未使用。
- **复核状态**: 未复核

## 正面评价

- IJobScheduler 接口 11/11 方法被 LocalJobScheduler 正确实现
- NopRetryJobRetryBridge 与 IRetryEngine/IRetryTask 契约完全匹配
- RpcJobInvoker 与 IRpcServiceInvoker 签名完全匹配

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 20-01 | P3 | IJobRetryBridge.java:4 | 返回值 String 未使用 |
