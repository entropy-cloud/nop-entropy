# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] indexDirectory 在单个事务+锁中执行全量分析和持久化，长事务风险

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:294-311`
- **证据片段**:
  ```java
  return withIndexLock(indexId, () -> {
      ProjectAnalysisResult result = analyzer.analyzeProject(/* CPU-intensive */);
      return transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
              ormTemplate.runInSession(session -> {
                  persistInSession(indexId, resolvedPath, finalResult, session);  /* DB-intensive */
              }));
  });
  ```
- **严重程度**: P1
- **现状**: indexDirectory 在获取 indexLock 后：(1) 先进行 CPU 密集的代码分析（可能数分钟），(2) 然后开启事务执行大量 DB 写入。triggerIncrementalIndex 和 deleteIndex 也有类似问题。
- **风险**: 长事务持有数据库连接和锁期间阻塞其他操作。indexLock 在分析期间已被持有但无 DB 操作。
- **建议**: 将 analyzeProject 移到 indexLock/事务之外。考虑将 persistInSession 拆分为多个小事务。
- **信心水平**: 高
- **误报排除**: 大型项目（数千文件）的持久化可能需要数十秒，这是可量化的长事务风险。
- **复核状态**: 未复核

### [维度14-02] persistFlows 先删后插在单一事务中，删除无上限保障

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1654-1709`
- **严重程度**: P3
- **现状**: persistFlows 在单一事务中分批删除所有现有 flows（每批 500），然后插入最多 5000 个新 flows。
- **风险**: 如果历史 flows 积累很多，删除阶段会很长。MAX_FLOWS_PER_INDEX=5000 的硬限制可能导致数据丢失。
- **建议**: 考虑将删除和插入拆分为两个独立事务，或使用"标记-清理"模式。
- **信心水平**: 高
- **误报排除**: 只有 admin 角色可调用，flow 数量通常远小于 5000。
- **复核状态**: 未复核

### [维度14-03] indexLocks ConcurrentHashMap 无上限增长

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:114, 562`
- **严重程度**: P3
- **现状**: indexLocks 在 deleteIndex() 时清理，但大量 indexId 不删除时会积累。
- **风险**: 轻微内存泄漏。实际影响较小（每个锁约 24-48 bytes）。
- **建议**: 使用带 LRU 淘汰的 Map。
- **信心水平**: 中
- **误报排除**: deleteIndex() 会清理，实际 indexId 数量有限。
- **复核状态**: 未复核

## 深挖第 2 轮追加

### [维度14-04] deleteIndex 未使用 withIndexLock 保护，与 indexDirectory 存在竞态条件

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:528-563`
- **严重程度**: P2
- **现状**: deleteIndex 没有使用 withIndexLock 包裹，而 indexDirectory、indexFile、triggerIncrementalIndex 都使用 withIndexLock。deleteIndex 正在删除数据时，另一个线程的 indexDirectory 可以获取同一个 indexId 的 lock 并开始写入。
- **风险**: 数据不一致——删除完所有数据后，indexDirectory 的事务尚未提交。
- **建议**: 将 deleteIndex 包裹在 withIndexLock 中。
- **信心水平**: 高
- **误报排除**: withIndexLock 已在其他方法中正确使用，deleteIndex 的遗漏是可验证的。
- **复核状态**: 复核保留

### [维度14-05] triggerIncrementalIndex 将文件分析和指纹计算包含在事务内

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:654-739`
- **严重程度**: P2
- **现状**: 与 indexDirectory 不同（analyzeProject 在事务外执行），triggerIncrementalIndex 将 VFS 遍历、变更检测、文件分析和持久化全部包含在一个事务中。
- **风险**: 文件系统 I/O 和 CPU 密集型操作在事务内执行，长事务风险比维度14-01 更严重。
- **建议**: 将文件分析和变更检测移到事务外，仅在事务内执行持久化。
- **信心水平**: 高
- **误报排除**: 这比维度14-01 更严重——indexDirectory 至少把分析放在事务外。
- **复核状态**: 复核保留

## 维度复核结论

| 编号 | 判定 | 理由 |
|------|------|------|
| [维度14-01] | **保留** P1 | persistInSession 在单个事务中执行大量 DB 写入 |
| [维度14-02] | **保留** P3 | 先删后插单一事务，但仅 admin 可调用 |
| [维度14-03] | **保留** P3 | 结合 indexId 无格式验证有内存增长风险 |
| [维度14-04] | **新增保留** P2 | deleteIndex 缺少 withIndexLock 保护 |
| [维度14-05] | **新增保留** P2 | triggerIncrementalIndex 事务包含文件 I/O |

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度14-01] | P1 | CodeIndexService.java | 长事务风险（分析+持久化在同一事务+锁中） |
| [维度14-04] | P2 | CodeIndexService.java | deleteIndex 缺少 withIndexLock 竞态条件 |
| [维度14-05] | P2 | CodeIndexService.java | triggerIncrementalIndex 事务包含文件 I/O |
| [维度14-02] | P3 | CodeIndexService.java | persistFlows 先删后插单一事务 |
| [维度14-03] | P3 | CodeIndexService.java | indexLocks 无上限增长 |
