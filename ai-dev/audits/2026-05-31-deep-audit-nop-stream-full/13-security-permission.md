# 维度 13：安全与权限模型

## P2 发现

### [维度13-02] ClassNameValidator 白名单包含 javax.crypto/javax.net
- **文件**: nop-stream-core/.../util/ClassNameValidator.java:15-41
- **严重程度**: P2
- **现状**: 白名单包含 javax.crypto./javax.net. 前缀，Class.forName + newInstance 可能实例化敏感类。
- **建议**: 移除不必要的 javax.crypto./javax.net./javax.sql./java.nio. 前缀。

### [维度13-04] Fencing token 在 INFO 日志中暴露
- **文件**: runtime/JobCoordinator.java:146, TaskManager.java:349
- **严重程度**: P2
- **现状**: JobCoordinator/TaskManager 在 INFO 级别记录 fencing token。JdbcClusterRegistry 正确使用 DEBUG。
- **建议**: 改为 DEBUG 级别或遮蔽 token。

### [维度13-06] restoreFromSavepointPath 未验证用户路径
- **文件**: runtime/GraphModelCheckpointExecutor.java:659-677
- **严重程度**: P2
- **现状**: savepointPath 直接传入 LocalFileCheckpointStorage，未做路径合法性验证。
- **建议**: 验证路径在可配置白名单目录内。

### [维度13-08] Checkpoint JSON 反序列化无完整性校验
- **文件**: runtime/CheckpointSerDe.java:49-110
- **严重程度**: P2
- **现状**: checkpoint 数据反序列化无 HMAC/签名验证。
- **建议**: 添加 HMAC-SHA256 完整性校验。

## 积极发现
- 全部 SQL 参数化查询，无注入风险
- LocalFileCheckpointStorage 路径遍历防护完善
- 无硬编码凭据
