# Adversarial Review: nop-code (2026-06-01)

> **审查类型**: 开放式对抗性审查
> **目标模块**: nop-code（含 13 个子模块）
> **审查日期**: 2026-06-01
> **审查方法**: 代码驱动的开放探索，从并发正确性和资源管理入手。起始视角：事务边界追踪者 + 10x 规模运维者 + 组合爆炸测试者。
> **去重基线**: 已读取 `ai-dev/audits/2026-05-31-adversarial-review-nop-code/`（AR-59 至 AR-76，前次刷新审查）、`ai-dev/audits/2026-05-29-adversarial-review-nop-code/`（AR-01 至 AR-22）、`ai-dev/audits/2026-05-29-deep-audit-nop-code/`（维度 01-21，66 个发现）、`ai-dev/analysis/2026-05-25-code-review-graph-vs-nop-code.md`。

---

## 总评

nop-code 模块在 2026-05-31 审查后经历了针对 `deleteFileRecords` 的修复（现已删除 NopCodeUsage、NopCodeSemanticEdge、NopCodeFlowMembership 等），但核心的 P0 问题（反向 BFS 深度 1、VFS 数据丢失、死代码排除逻辑）仍未修复。

**本次审查发现 3 个新问题**，其中最重要的是 `indexLocks` 的竞态条件（AR-77）：`indexDirectory` 的 `finally` 块在锁仍被其他线程持有时移除锁，导致新的并发调用可创建不同的锁实例，完全绕过互斥保护。这是一个此前未被报告的并发正确性 Bug。

**最值得关注的 3 个方向**：

1. **`indexLocks` 竞态条件破坏互斥**（AR-77）——首次被发现的并发正确性缺陷，可导致同一索引被并发写入
2. **反向 BFS 深度 1 仍未修复**（AR-29/AR-63→AR-78）——连续 4 次审查确认的 P0 Bug
3. **`CommunityDetector.runWithTimeout` 线程泄漏**（AR-79）——超时后 CPU-bound 算法线程继续运行，多次超时后累积僵尸线程

## 本次审查的盲区自评

- **前端 view.xml 和 xmeta 配置**：未覆盖 GraphQL schema 与 BizModel 方法的字段级对齐
- **GraalVM native image**：未验证 reflect-config.json 完整性
- **大型代码库端到端验证**：所有并发和性能问题基于代码推理，未实际压测
- **图算法数学正确性**：Leiden/Louvain 实现未做数学层面验证
- **语义边提取器质量**：AnnotationPatternExtractor、DocKeywordExtractor、NameSimilarityExtractor 的精确度未评估

---

## 已修复问题确认（自 2026-05-31 审查后）

| # | 问题 | 修复方式 |
|---|------|---------|
| AR-60/AR-73 | deleteFileRecords 不删除 NopCodeUsage 和 NopCodeSemanticEdge | `deleteFileRecords` 现在删除 NopCodeCall、NopCodeSymbol、NopCodeUsage、NopCodeDependency、NopCodeAnnotationUsage、NopCodeInheritance、NopCodeSemanticEdge、NopCodeFlowMembership（:1173-1197）✅ |
| AR-11 | CodeIndexService 粗粒度 synchronized 阻塞跨索引 | 改为 `ReentrantLock` + `ConcurrentHashMap<String, ReentrantLock>` 按索引粒度加锁（:98, 273-304）✅ |
| AR-12 | deleteIndex 全量加载后删除 | 改为 `deleteEntitiesPaged` 分页删除（:513-560）✅ |

---

## 第 1 轮发现

### [AR-77] indexLocks 竞态条件：锁移除破坏互斥保护

- **文件**: `nop-code-service/.../impl/CodeIndexService.java:273-304`
- **证据片段**:
  ```java
  // :273 — 创建或获取锁
  ReentrantLock lock = indexLocks.computeIfAbsent(indexId, k -> new ReentrantLock());
  lock.lock();
  try {
      return ormTemplate.runInSession(session -> {
          // ... 索引操作 ...
      });
  } finally {
      indexLocks.remove(indexId, lock);  // :303 — 无条件移除
  }
  ```
- **严重程度**: P1
- **现状**: `indexDirectory` 使用 `ConcurrentHashMap<String, ReentrantLock>` 实现按索引粒度的互斥。但 `finally` 块在锁仍被其他等待线程持有时就移除它。具体竞态序列：
  1. Thread A: `computeIfAbsent("idx1")` → 创建 lock1，`lock1.lock()` 获取
  2. Thread B: `computeIfAbsent("idx1")` → 返回 lock1（同一实例），`lock1.lock()` 阻塞等待
  3. Thread A: 完成工作，`lock1.unlock()`，然后 `indexLocks.remove("idx1", lock1)` 移除 lock1
  4. Thread B: `lock1.lock()` 成功（lock1 仍在内存中，只是从 map 移除）
  5. Thread C: `computeIfAbsent("idx1")` → 创建 **新的** lock2，`lock2.lock()` 立即成功
  6. **Thread B 和 Thread C 同时操作 "idx1"**，互斥被打破
- **风险**: 同一索引被并发写入，可能导致数据损坏（重复实体、不一致的关联关系、`saveReplacingExisting` 冲突）。
- **建议**: 方案 A：不移除锁，接受 `indexLocks` 的少量内存开销。方案 B：使用引用计数或在所有等待线程完成后才移除。方案 A 最简单可靠。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者（审查 `indexDirectory` 的新 ReentrantLock 替换 synchronized 时发现）

### [AR-78] bfsCollect 反向遍历仍然只走深度 1——连续第 4 次审查确认

- **文件**: `nop-code-service/.../impl/CodeGraphService.java:594-611, 628-645`
- **证据片段**:
  ```java
  // :594-611 — 构建反向邻接表：key=targetFilePath
  private Map<String, List<DepEdgeDTO>> buildReverseAdjacency(String indexId) {
      // ...
      for (NopCodeDependency dep : deps) {
          DepEdgeDTO edge = new DepEdgeDTO();
          edge.setSource(dep.getSourceFilePath());
          edge.setTarget(dep.getTargetFilePath());
          adj.computeIfAbsent(dep.getTargetFilePath(), k -> new ArrayList<>()).add(edge);
      }
      return adj;
  }

  // :628-645 — BFS 遍历：始终跟随 edge.getTarget()
  private void bfsCollect(String start, Map<String, List<DepEdgeDTO>> adj, int maxDepth,
                          Set<String> visited, List<DepEdgeDTO> result) {
      // ...
      for (DepEdgeDTO edge : edges) {
          result.add(edge);
          if (!visited.contains(edge.getTarget())) {      // ← 反向邻接表时，getTarget() 返回当前节点本身
              visited.add(edge.getTarget());
              queue.add(new BfsNode(edge.getTarget(), current.depth() + 1));
          }
      }
  }
  ```
- **严重程度**: P0
- **现状**: 与 AR-29→AR-63 同一问题，第 4 次确认。`bfsCollect` 对正向和反向邻接表都使用 `edge.getTarget()` 做队列推进。在反向邻接表中，key 是 `targetFilePath`，所以 `edge.getTarget()` 返回的是当前节点本身（已被 visited），而不是上游依赖者 `edge.getSource()`。结果：反向依赖查询（`getReverseDeps`）永远只返回直接依赖者，depth 参数无效。
- **风险**: 反向依赖分析对大型代码库完全失效。影响分析、变更风险评估等下游功能。
- **建议**: `bfsCollect` 需要知道当前是正向还是反向遍历。在反向模式下，应跟随 `edge.getSource()` 而非 `edge.getTarget()`。可以添加 `Function<DepEdgeDTO, String> nextNodeExtractor` 参数。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

### [AR-79] CommunityDetector.runWithTimeout 超时后线程泄漏

- **文件**: `nop-code-graph/.../community/CommunityDetector.java:741-749`
- **证据片段**:
  ```java
  private static <T> T runWithTimeout(Callable<T> task, long timeoutMs) throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
          Future<T> future = executor.submit(task);
          return future.get(timeoutMs, TimeUnit.MILLISECONDS);
      } finally {
          executor.shutdownNow();
      }
  }
  ```
- **严重程度**: P2
- **现状**: `shutdownNow()` 仅设置线程中断标志并取消等待中的任务，但**不等待线程实际终止**。Leiden 算法（来自 CWTS library）是 CPU-bound 的纯计算任务，几乎不可能检查 `Thread.interrupted()`。超时后线程继续运行直至完成。如果社区检测被反复调用且经常超时，僵尸线程会累积。
- **风险**: (1) 线程泄漏：每个超时的调用留下一个僵尸线程。(2) CPU 浪费：已超时的计算继续消耗 CPU。(3) 多个僵尸线程同时运行可能导致 OOME。
- **建议**: 方案 A：超时后 `awaitTermination(1, TimeUnit.SECONDS)` 并强制处理。方案 B：使用 `Future.cancel(true)` 并在算法内部增加中断检查（需要修改或包装 Leiden 库调用）。方案 C：不使用超时，改为在外层做整体超时控制。
- **信心水平**: 很可能（基于 Leiden 算法实现的合理推测，未验证 CWTS library 内部是否有中断检查）
- **发现来源视角**: 10x 规模运维者

### [AR-80] getIndexStats 全量加载符号和文件到内存——频繁调用的 OOME 风险

- **文件**: `nop-code-service/.../impl/CodeIndexService.java:472-502`
- **证据片段**:
  ```java
  public IndexStatsDTO getIndexStats(String indexId) {
      // :479-482 — 全量加载所有符号
      IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
      QueryBean symbolQuery = new QueryBean();
      symbolQuery.addFilter(FilterBeans.eq("indexId", indexId));
      List<NopCodeSymbol> allSymbols = symbolDao.findAllByQuery(symbolQuery);

      // :488-491 — 全量加载所有文件
      IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
      QueryBean fileQuery = new QueryBean();
      fileQuery.addFilter(FilterBeans.eq("indexId", indexId));
      stats.setFileCount(fileDao.findAllByQuery(fileQuery).size());

      // :493-499 — 遍历所有符号做 kind 分组计数
      for (NopCodeSymbol s : allSymbols) {
          String kind = s.getKind();
          kindCounts.merge(kind != null ? kind : "UNKNOWN", 1, Integer::sum);
      }
  }
  ```
- **严重程度**: P2
- **现状**: `getIndexStats` 对 50 万符号的索引全量加载所有符号实体和所有文件实体到内存，仅为了计数和按 kind 分组。这比 AR-75 的 `resolveQualifiedNamesToIds` 更危险，因为 `getIndexStats` 是 UI 常规操作（每次用户查看索引状态都会调用），而 AR-75 只在索引时调用一次。
- **风险**: 大型索引的统计页面会导致 OOME。即使不 OOME，大量对象也会增加 GC 压力。
- **建议**: 使用 SQL `SELECT kind, COUNT(*) FROM nop_code_symbol WHERE indexId=? GROUP BY kind` 替代全量加载。文件计数同样使用 `SELECT COUNT(*)` 。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者（追踪频繁调用路径时发现）

### [AR-81] getTypeHierarchy 全量加载继承记录——大型继承体系 OOME 风险

- **文件**: `nop-code-service/.../impl/CodeGraphService.java:185-192`
- **证据片段**:
  ```java
  IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
  QueryBean inhQuery = new QueryBean();
  inhQuery.addFilter(FilterBeans.eq("indexId", indexId));
  List<CodeInheritance> allInheritances = inhDao.findAllByQuery(inhQuery).stream()
          .map(this::entityToInheritance)
          .collect(Collectors.toList());
  ```
- **严重程度**: P2
- **现状**: 每次 `getTypeHierarchy` 调用都全量加载该索引的所有继承记录。虽然继承记录数通常少于符号数（每个类 1-3 条继承记录），但对于有 5 万个类的项目，仍有 5-15 万条记录全部加载到内存。与 AR-75 的模式相同，但在层级查询场景下。
- **风险**: 大型项目的类型层级查询可能 OOME 或极慢。每次查询都是全表扫描。
- **建议**: 改为按需查询：先查当前类的继承关系，递归时再查下一层。或使用 `subTypeId` / `superTypeId` 的索引做定向查询（已有 `idx_inheritance_sub` 和 `idx_inheritance_super` 索引）。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

## 已知未修复问题确认

以下问题在前次审查中已报告，经本次验证**仍然存在**：

| # | 问题 | 状态 | 本次补充 |
|---|------|------|---------|
| AR-29/AR-63→AR-78 | bfsCollect 反向深度始终为 1 | **仍存在** | 详细根因分析见 AR-78 |
| AR-47/AR-64 | VFS 路径跳过语义边和 resolveQualifiedNamesToIds | **仍存在** | `indexDirectory` :286-299 VFS 路径仅调 `saveFileResultInSession` |
| AR-48/AR-68 | DeadCodeDetector 排除逻辑全死代码 | **仍存在** | 未在本轮重新验证，前次确认 05-31 |
| AR-75 | resolveQualifiedNamesToIds 全量加载 | **仍存在** | :817, :830 仍为 `findAllByQuery` 无分页 |
| AR-76 | CodeCacheManager 超限降级为空 | **仍存在** | 未在本轮重新验证 |
| AR-02 | TSTree 原生对象未关闭 | **仍存在** | 未在本轮重新验证 |
| AR-33 | Python 嵌套函数/类不可见 | **仍存在** | 未在本轮重新验证 |
| AR-65 | language 硬编码 "Java" | **仍存在** | `persistInSession` :768, `ensureIndexEntity` :849 |
| AR-67 | FlowDetector 硬编码 ".java" | **仍存在** | 未在本轮重新验证 |
| AR-69 | Tarjan 递归 StackOverflow | **仍存在** | `tarjanDFS` :692-718 仍为递归 DFS |
| AR-59 | entityToCodeSymbol 三重复制 | **仍存在** | CodeIndexService:209-238, CodeGraphService:304-333, CodeQueryService:35-64 |
| AR-62 | ensureSubServices 竞态条件 | **仍存在** | :110-116 无同步 |

---

## 严重程度分布表

| 严重程度 | 独立发现数 | 编号 |
|---------|-----------|------|
| P0      | 1         | AR-78（已知未修复，本轮重新确认） |
| P1      | 1         | AR-77 |
| P2      | 4         | AR-79, AR-80, AR-81 + AR-75（已知未修复） |
| P3      | 0         | — |

> 本轮新增独立发现 3 个（AR-77, AR-79, AR-80/AR-81），已修复 2 个（AR-60/AR-73, AR-11/AR-12），已知未修复确认 12 个。

*审查结束。如需深挖某个方向，可追加第 2 轮。*
