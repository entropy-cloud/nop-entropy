# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] indexDirectory 锁存在竞态条件，可导致同一 indexId 并发索引

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:98,273-304`
- **证据片段**:
  ```java
  private final ConcurrentHashMap<String, ReentrantLock> indexLocks = new ConcurrentHashMap<>();
  
  public int indexDirectory(String indexId, String vfsPath, String filePattern) {
      ReentrantLock lock = indexLocks.computeIfAbsent(indexId, k -> new ReentrantLock());
      lock.lock();
      try {
          return ormTemplate.runInSession(session -> { /* ... */ });
      } finally {
          indexLocks.remove(indexId, lock);   // <-- 竞态：移除后等待线程与新到达线程使用不同锁
      }
  }
  ```
- **严重程度**: P2
- **现状**: finally 块中 `indexLocks.remove(indexId, lock)` 在释放锁之前从 map 移除。等待同一锁的线程 B（持有旧 lock）和新到达的线程 C（获得新 lock）可同时进入临界区。
- **风险**: 两个线程并发写入同一索引数据，可能导致重复实体、主键冲突或数据不一致。
- **建议**: 不在 finally 中移除锁，改用定期清理或保持锁不移除（ConcurrentHashMap 的内存开销极小）。
- **信心水平**: 确定
- **误报排除**: 竞态条件可通过多线程测试复现。
- **复核状态**: 未复核

### [维度14-02] indexDirectory 将重计算包裹在 DB Session 内，长事务/长连接风险

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:276-284`
- **证据片段**:
  ```java
  return ormTemplate.runInSession(session -> {
      ensureIndexEntity(indexId, vfsPath, session);     // DB 操作
      ProjectAnalysisResult result = analyzer.analyzeProject(localFile.toPath());  // 纯计算，可能耗时数分钟
      persistInSession(indexId, vfsPath, result, session);
      return result.getFileResults().size();
  });
  ```
- **严重程度**: P2
- **现状**: `analyzer.analyzeProject()` 是纯计算（遍历文件+解析源码），可能耗时数分钟，但整个计算在 runInSession 内执行，DB 连接被持续占用。
- **风险**: 高并发索引请求可耗尽连接池，阻塞其他 DB 访问。对比 triggerIncrementalIndex 正确地将分析放在 Session 外。
- **建议**: 将 `analyzer.analyzeProject()` 移到 Session 外，仅在持久化阶段打开 Session。
- **信心水平**: 确定
- **误报排除**: 可量化——大型项目分析耗时数分钟，期间占用 DB 连接。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 14-01 | P2 | CodeIndexService.java:98,273-304 | indexDirectory 锁竞态条件（remove 后新线程获新锁） |
| 14-02 | P2 | CodeIndexService.java:276-284 | 重计算在 DB Session 内执行，长连接风险 |
