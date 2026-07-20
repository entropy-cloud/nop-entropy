# 维度 14：异步与事务模式 — 审计报告

> 初审结果（待复核）

## 发现条目

### [维度14-01] dispatchActions 文档/实现语义偏差——并非真正的 post-commit（P2）

- **文件**: `NopMetaQualityCheckpointBizModel.java:160-161, 325-337`
- **说明**: 注释声称"store 提交后才触发（post-commit dispatch）"，但实际使用 `ITransactionTemplate.runWithoutTransaction` 同步执行，不保证 store 事务已提交。如果外层事务回滚，dispatch 已发生无法撤回。
- **严重程度**: P2
- **建议**: 将 dispatch 移到 `txn().afterCommit()` 注册，或更新注释准确反映 at-least-once 语义。

### [维度14-02] upsertExternalTable 存在读取-然后-写入竞态条件（P2）

- **文件**: `NopMetaDataSourceBizModel.java:432-471`
- **说明**: 按 (metaModuleId, tableName) 查询候选行，Java 层按 schema 筛选，然后是否存在分别 save/update。两步骤无法原子完成，并发请求可能导致重复。
- **严重程度**: P2
- **建议**: 添加数据库级唯一约束 UNIQUE(meta_module_id, table_name, schema) 或有数据库端 MERGE/INSERT ON DUPLICATE KEY UPDATE。

### [维度14-03] afterCommit 未使用，事件写入与业务事务耦合（P3）

- **文件**: `NopMetaTableBizModel.java:224-226`, `MetaModelChangedEventPublisher.java:29`
- **说明**: 变更事件与业务数据写入同一事务。首版设计决定，但跨数据源场景时需改用 txn().afterCommit()。
- **严重程度**: P3

### 合规确认

| 检查项 | 结果 |
|--------|------|
| 所有 JDBC 资源正确关闭 | ✅ |
| 无显式异步处理（符合预期） | ✅ |
| 事务传播语义正确 | ✅ |
