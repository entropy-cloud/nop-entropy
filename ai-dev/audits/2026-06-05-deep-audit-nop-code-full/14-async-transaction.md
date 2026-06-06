# 维度 14：异步与事务模式 — nop-code 模块

## 第 1 轮（初审）

### [维度14-01] indexDirectory 在单个 ORM session 中执行完整索引 — 长事务风险

- **文件**: `CodeIndexService.java:303-323`
- **证据片段**: `ormTemplate.runInSession` 中执行完整索引持久化，包括所有文件的所有实体创建。
- **严重程度**: P1
- **现状**: 大型项目（数千文件）索引可能在单个 session/事务中持续数分钟。
- **风险**: 长事务持有数据库锁、占用连接池、失败时部分回滚问题。
- **建议**: 将 persistInSession 拆分为多个独立 session。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度14-02] deleteIndex 在单个 session 中批量删除 9 个表 — 长事务风险

- **文件**: `CodeIndexService.java:557-590`
- **严重程度**: P2
- **建议**: 考虑使用数据库级联删除或批量 SQL。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度14-03] CodeCacheManager 静态缓存无基于内存压力的驱逐

- **文件**: `CodeCacheManager.java:27-137`
- **证据片段**: LinkedHashMap（非 LRU），20个索引×100K符号可能达 2-4GB。
- **严重程度**: P2
- **建议**: 考虑使用 Caffeine 缓存或基于内存压力的驱逐策略。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度14-04] IncrementalStatus 驱逐非 LRU 非确定性

- **文件**: `NopCodeIndexBizModel.java:335-342`
- **严重程度**: P3
- **现状**: ConcurrentHashMap 迭代顺序不确定，可能淘汰最近使用的状态。
- **建议**: 使用 ConcurrentLinkedHashMap 实现 LRU。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度14-05] saveReplacingExisting 使用异常作为正常流程控制

- **文件**: `CodeIndexService.java:1450-1480`
- **严重程度**: P2
- **现状**: try-catch OrmException 检测"实体已存在"，批量索引时频繁抛出异常。
- **建议**: 使用 session.get() 先检查或使用 saveOrUpdate。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度14-06] txn() 和 afterCommit() 未使用 — 缓存失效时机问题

- **文件**: 所有 service 文件
- **严重程度**: P3（信息性）
- **现状**: invalidateAnalysisCache 在 runInSession 外调用，事务失败后缓存已被清除。
- **建议**: 考虑使用 txn().afterCommit() 在事务成功后清除缓存。
- **信心水平**: 很可能
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 14-01 | P1 | CodeIndexService.java:303 | 单 session 完整索引长事务 |
| 14-02 | P2 | CodeIndexService.java:557 | 单 session 批量删除长事务 |
| 14-03 | P2 | CodeCacheManager.java | 静态缓存无内存驱逐 |
| 14-04 | P3 | NopCodeIndexBizModel.java:335 | 非确定性缓存淘汰 |
| 14-05 | P2 | CodeIndexService.java:1450 | 异常作为正常流程控制 |
| 14-06 | P3 | CodeIndexService.java | 缓存失效时机问题 |
