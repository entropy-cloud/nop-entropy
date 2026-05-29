# 维度14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] indexDirectory 使用 synchronized 导致全局串行化

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L333
- **证据片段**:
  ```java
  public synchronized int indexDirectory(String indexId, String vfsPath, String filePattern) {
  ```
- **严重程度**: P1
- **现状**: synchronized 锁的是 CodeIndexService 单例，所有 indexId 共享同一把锁。getOrRebuildSymbolTable、getOrRebuildCallGraph、invalidateAnalysisCache 也使用 synchronized。
- **风险**: 索引项目 A 时，项目 B 的查询和索引操作也被阻塞。indexDirectory 可能耗时数分钟。
- **建议**: 替换为按 indexId 粒度的锁（ConcurrentHashMap<String, ReentrantLock>）。
- **信心水平**: 确定
- **误报排除**: synchronized 在单项目场景下不会暴露问题，但在多项目并发场景下是严重瓶颈。
- **复核状态**: 未复核

### [维度14-02] deleteIndex 是超长事务

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L1321-1406
- **证据片段**:
  ```java
  ormTemplate.runInSession(session -> {
      // 1. 删除 NopCodeUsage + flush + evictAll
      // 2. 删除 NopCodeFlowMembership + flush + evictAll
      // ... 共 11 步级联删除
  });
  ```
- **严重程度**: P1
- **现状**: 一个 runInSession 中执行 11 步级联删除（加载全量→批量删除→flush→evict），大索引可能涉及数十万条记录。
- **风险**: 长事务导致事务日志膨胀和锁竞争。内存中加载全量待删除数据。中途失败导致数据不一致。
- **建议**: 使用数据库级联删除或按表分步删除。对大表使用 DELETE FROM table WHERE indexId=? 而非先 SELECT 再 DELETE。
- **信心水平**: 确定
- **误报排除**: 可能是刻意的性能优化（避免 ORM 级联的逐条删除），但当前实现也有严重问题。
- **复核状态**: 未复核

### [维度14-03] analysisCacheMap 无界增长——内存泄漏

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L109
- **证据片段**:
  ```java
  private final Map<String, AnalysisCache> analysisCacheMap = new java.util.concurrent.ConcurrentHashMap<>();
  ```
- **严重程度**: P1
- **现状**: 缓存 SymbolTable + CallGraph，无最大容量、无 TTL、无过期策略。仅在显式调用 invalidateAnalysisCache 时按 indexId 清理。
- **风险**: 长期运行的服务实例中缓存持续增长。单个大项目 SymbolTable 可能数十万符号。OOM 风险。
- **建议**: 使用 Caffeine/Guava Cache 替代，设置最大容量和过期策略。
- **信心水平**: 确定
- **误报排除**: 如果仅作为离线批处理工具（非长期运行服务），问题不严重。但 app 模块集成了 Quarkus，暗示是长期运行的服务。
- **复核状态**: 未复核

### [维度14-04] incrementalStatusMap 同样无界增长

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`
- **行号**: L33
- **证据片段**:
  ```java
  private final Map<String, IncrementalStatus> incrementalStatusMap = new java.util.concurrent.ConcurrentHashMap<>();
  ```
- **严重程度**: P3
- **现状**: 与 analysisCacheMap 类似，但 IncrementalStatus 轻量，影响较小。
- **建议**: 使用有界缓存。
- **信心水平**: 确定
- **误报排除**: 轻度内存泄漏，优先级低于 analysisCacheMap。
- **复核状态**: 未复核

### [维度14-05] findReferencedBy 存在 N+1 查询模式

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L1000-1024
- **证据片段**:
  ```java
  return usages.stream().map(usage -> {
      // ...
      NopCodeFile file = fileDao.getEntityById(usage.getFileId()); // N+1!
      NopCodeSymbol enclosing = symbolDao.getEntityById(usage.getEnclosingSymbolId()); // N+1!
  ```
- **严重程度**: P2
- **现状**: 对每条 Usage 记录执行 1-2 次单独的 getEntityById 调用。
- **风险**: limit=50 时可能产生 150+ 次数据库查询。
- **建议**: 批量获取 fileId 和 symbolId，使用 findAllByQuery(FilterBeans.in(...))。
- **信心水平**: 确定
- **误报排除**: getEntityById 可能命中 session 缓存，但在跨 session 场景下会触发 SQL。
- **复核状态**: 未复核

### [维度14-06] getOrRebuildSymbolTable/CallGraph 全量加载

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L271-298
- **证据片段**:
  ```java
  List<NopCodeSymbol> entities = symbolDao.findAllByQuery(query); // 全量加载
  List<NopCodeCall> callEntities = callDao.findAllByQuery(query); // 全量加载
  ```
- **严重程度**: P2
- **现状**: 将指定 indexId 的所有 Symbol/Call 一次性加载到内存构建缓存。大项目可能有 5 万+ Symbol 和 10 万+ Call。
- **风险**: 多个大项目并发分析时可能 OOM。
- **建议**: 考虑分页加载或流式处理。为缓存设置最大容量限制。
- **信心水平**: 很可能
- **误报排除**: 图算法（社区检测、调用层次）需要全量数据，当前方案是必要的。问题在于缓存无界增长。
- **复核状态**: 未复核
