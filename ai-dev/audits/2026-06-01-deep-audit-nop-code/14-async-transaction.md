# 维度14：异步与事务模式 -- nop-code 模块审计报告

## 第 1 轮（初审）

### [维度14-01] 长事务风险：`indexDirectory()` 在单个 ORM session 中执行全量索引

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:229-254,709-768`
- **证据片段**:
  ```java
  return ormTemplate.runInSession(session -> {
      // 扫描全部文件 + 解析 + 持久化 + 关系解析
      int fileCount = indexDirectoryInSession(session, ...);
      return fileCount;
  });
  ```
- **严重程度**: P2
- **现状**: `indexDirectory()` 在 `runInSession` 中执行完整目录扫描 + 文件解析 + 实体持久化 + `resolveQualifiedNamesToIds()`。对于大型项目（数千文件），session 可能长时间持有数据库连接。
- **风险**: 长时间持有连接可能导致连接池耗尽、锁超时等问题。
- **建议**: 将全量索引拆分为多 session（每 N 个文件一个 session），或将 `resolveQualifiedNamesToIds` 移到独立 session。
- **信心水平**: 80%
- **误报排除**: 内部使用了 `BatchQueue`（batch size=500）定期 `flush/evictAll` 控制内存，但事务持续时间仍可能很长。
- **复核状态**: 未复核

### [维度14-02] `updateIndexStats()` 使用 `findAllByQuery().size()` 做全表加载计数

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1304-1324`
- **证据片段**:
  ```java
  List<NopCodeFile> files = fileDao.findAllByQuery(allFilesQuery);
  indexEntity.setFileCount(files.size());
  List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(allSymbolsQuery);
  indexEntity.setSymbolCount(symbols.size());
  ```
- **严重程度**: P2
- **现状**: `updateIndexStats()` 通过加载全部实体再 `.size()` 来计数，而非使用 `COUNT` 聚合。
- **风险**: 大型索引（10 万+ 符号）会加载全部实体到内存仅为了计数，严重浪费资源。
- **建议**: 替换为 `dao.countByQuery()` 或等效的 COUNT 查询。
- **信心水平**: 95%
- **误报排除**: 这是真实的性能问题，不是代码风格问题。
- **复核状态**: 未复核

### [维度14-03] ReentrantLock 无超时设置，锁对象无限增长

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:100,232-253`
- **证据片段**:
  ```java
  private final ConcurrentHashMap<String, ReentrantLock> indexLocks = new ConcurrentHashMap<>();
  ReentrantLock lock = indexLocks.computeIfAbsent(indexId, k -> new ReentrantLock());
  lock.lock(); // 无超时
  ```
- **严重程度**: P3
- **现状**: 锁对象按 indexId 无限增长，无清理机制。`lock()` 无超时设置。
- **风险**: (1) 理论上的死锁风险（虽然当前 finally 正确释放）。(2) 内存中锁对象累积。
- **建议**: 使用 `lock.tryLock(timeout, TimeUnit)` 并定期清理不活跃的锁对象。
- **信心水平**: 70%
- **误报排除**: 当前实现中 finally 块正确释放锁，实际死锁风险低。
- **复核状态**: 未复核

## 无问题确认

- **无 txn() 调用**: 所有数据库操作通过 `ormTemplate.runInSession()` 管理。
- **无资源泄漏**: session 由 ormTemplate 自动管理，ReentrantLock 在 finally 中释放。
- **无明显竞态条件**: `ConcurrentHashMap` 使用 `computeIfAbsent`。
