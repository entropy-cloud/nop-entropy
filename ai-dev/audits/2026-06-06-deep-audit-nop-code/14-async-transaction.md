# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] triggerIncrementalIndex 中文件分析和 I/O 在事务内执行，存在长事务风险

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:664-739`
- **证据片段**:
  ```java
  return withIndexLock(indexId, () -> transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
          ormTemplate.runInSession(session -> {
      // 以下操作全部在事务内执行:
      List<FileFingerprint> previousFingerprints = store.loadFingerprints(indexId);  // DB 读取
      List<IResource> currentResources = collectResourcesFromVfs(vfs, vfsPath);       // VFS 遍历
      for (String changedFile : changedFiles) {
          String sourceCode = resource.readText();                    // 文件 I/O（在事务内!）
          CodeFileAnalysisResult fileResult = fileAnalyzer.analyze(relativePath, sourceCode); // CPU 密集
      }
  })));
  ```
- **严重程度**: P2
- **现状**: triggerIncrementalIndex 整个业务逻辑包裹在一个事务中，包括文件读取和语法分析。相比之下 indexDirectory 将分析放在事务外。
- **风险**: 长事务持有数据库连接和锁，可能导致连接池耗尽、其他请求阻塞、事务超时。
- **建议**: 将文件 I/O 和语法分析移到事务外，参照 indexDirectory 的模式。
- **信心水平**: 高 (85%)
- **误报排除**: indexDirectory 已正确实现事务外分析。
- **复核状态**: 未复核

### [维度14-02] indexLocks 的 ConcurrentHashMap 只在 deleteIndex 中清理

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:114-124`
- **证据片段**:
  ```java
  private final ConcurrentHashMap<String, ReentrantLock> indexLocks = new ConcurrentHashMap<>();
  
  private <T> T withIndexLock(String indexId, Supplier<T> action) {
      ReentrantLock lock = indexLocks.computeIfAbsent(indexId, k -> new ReentrantLock());
      lock.lock();
      try { return action.get(); } finally { lock.unlock(); }
  }
  ```
- **严重程度**: P3
- **现状**: indexLocks 通过 computeIfAbsent 添加锁，但只在 deleteIndex 中通过 remove 清理。
- **风险**: 长期运行且不断创建不重复 indexId 时导致缓慢内存泄漏。典型使用场景中影响极小。
- **建议**: 可增加定时清理或使用后检查是否还有持有者。
- **信心水平**: 高 (80%)
- **误报排除**: 典型场景中 indexId 数量有限。
- **复核状态**: 未复核

### [维度14-03] incrementalStatusMap 在集群环境下不一致

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:44-50`
- **证据片段**:
  ```java
  private final Map<String, IncrementalStatus> incrementalStatusMap = Collections.synchronizedMap(
          new LinkedHashMap<String, IncrementalStatus>(16, 0.75f, true) { ... });
  ```
- **严重程度**: P3
- **现状**: JVM 进程内 LRU 缓存（上限 20），集群中各节点状态不共享。应用重启后丢失。
- **风险**: 多节点部署中状态查询返回过时或空结果。
- **建议**: 对于 admin 使用的临时状态追踪可接受。如需精确持久化应存入数据库。
- **信心水平**: 高 (90%)
- **误报排除**: 与维度07-01是同一问题的不同角度。
- **复核状态**: 未复核
