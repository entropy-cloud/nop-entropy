# 维度 14：异步与事务模式

## 审计日期
2026-06-06

## 第 1 轮（初审）

### [维度14-01] indexDirectory 长事务 —— persistInSession 在单一会话中持久化大量实体

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:303-323`
- **严重程度**: P2
- **现状**: persistInSession 在单一会话中保存文件、符号、调用、继承、注解、引用、依赖、语义边——潜在数千实体无中间提交。
- **风险**: 大项目（>1000文件）可能导致内存压力。
- **建议**: 考虑按批拆分会话。BatchQueue flush/evict 已部分缓解。
- **信心水平**: 可能
- **复核状态**: 未复核

### [维度14-02] deleteIndex 无显式事务边界

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:557-590`
- **严重程度**: P2
- **现状**: 删除 10 种实体类型无 txn() 边界。中途失败可能留下孤儿记录。
- **建议**: 包装在 txn() 块中。
- **信心水平**: 可能
- **复核状态**: 未复核

### [维度14-03] indexLocks ConcurrentHashMap 永不收缩

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:306-323`
- **严重程度**: P3
- **现状**: 锁映射随 indexId 增长无清理。
- **建议**: 添加定期清理类似 evictStatusMap 模式。
- **信心水平**: 可能
- **复核状态**: 未复核
