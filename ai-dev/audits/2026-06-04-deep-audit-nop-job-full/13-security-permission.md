# 维度 13：安全与权限模型

## 审计范围

5 个权限相关文件 + 3 个 BizModel 类 + RPC 调用安全性。

## 第 1 轮（初审）发现

### [维度13-01] data-auth.xml 配置为空，未实现数据权限

- **文件**: `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/auth/nop-job.data-auth.xml:1-5`
- **证据片段**:
  ```xml
  <?xml version="1.0" encoding="UTF-8" ?>
  <data-auth x:schema="/nop/schema/data-auth.xdef" xmlns:x="/nop/schema/xdsl.xdef">
      <objs/>
  </data-auth>
  ```
- **严重程度**: P3
- **现状**: 数据权限文件为空。任何有权访问查询的用户都可以看到所有记录，没有按 namespaceId、groupId 或组织进行数据隔离。
- **风险**: 多租户/多组织部署场景下，不同 namespace/group 的作业数据无法隔离。
- **建议**: 根据业务需要，至少为 NopJobSchedule 和 NopJobFire 添加基于 `namespaceId` 的数据权限规则。
- **信心水平**: 中
- **误报排除**: 如果仅用于单租户场景，空 data-auth 可能是合理的。但作为通用调度模块，应提供按 namespace 隔离的示例。
- **复核状态**: 未复核

### [维度13-02] RpcJobInvoker 使用用户可控的 jobParams 构造 RPC 调用目标

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/executor/RpcJobInvoker.java:31-64`
- **证据片段**:
  ```java
  String serviceName = requireString(jobParams, "serviceName");
  String serviceMethod = getString(jobParams, "serviceMethod", "invokeJob");
  return rpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, null);
  ```
- **严重程度**: P3
- **现状**: `serviceName` 和 `serviceMethod` 来自用户可配置的 `jobParams`，有 mutation 权限的用户可设置任意服务名。但受 Nop RPC 框架约束和权限控制保护。
- **风险**: 获得 mutation 权限的攻击者可修改 jobParams 中的 serviceName 指向任意服务。但需先获得权限，且 RPC 框架有认证授权。
- **建议**: 可在 BizModel 保存逻辑中校验 serviceName 是否在允许的服务白名单内。
- **信心水平**: 中
- **误报排除**: RPC 作业调用的 serviceName 本身就是用户可配置的，限制过严会丧失灵活性。
- **复核状态**: 未复核

## 正面评价

1. **无 SQL 注入风险**: 全部使用 QueryBean 参数化查询
2. **action-auth 权限覆盖完整**: 保留文件用细粒度权限替代了生成的粗粒度权限
3. **方法级权限通过 action-auth FNPT 资源实现**: 标准 Nop 平台模式

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 13-01 | P3 | nop-job.data-auth.xml | 空数据权限，无 namespace 隔离 |
| 13-02 | P3 | RpcJobInvoker.java | 用户可控 serviceName |
