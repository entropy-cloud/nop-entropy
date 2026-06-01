# Adversarial Review: nop-code — Open Findings

> **日期**: 2026-06-02（第 7 轮对抗性审查）
> **模块**: nop-code
> **审查类型**: 开放式对抗性审查
> **发现来源视角**: 安全面一致性审计 + 异常路径侦探

## 去重确认

已审阅以下历史报告：
- r6 (2026-06-01): AR-87 O(N²) getProjectFilePaths
- r5 (2026-06-01): AR-82 detectFlows @Auth, AR-83 N+1, AR-84-86
- r4 (2026-05-31): AR-77 countByQuery, AR-78 NPE

本轮发现均为新视角切入的新问题。

---

### [AR-88] NopCodeSymbolBizModel 15/17 方法缺少 @Auth 保护

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:49-232`
- **证据片段**:
  ```java
  @BizQuery
  public SymbolDTO getBySymbolId(@Name("id") String id, @Name("indexId") String indexId) {
      // 无 @Auth
  }

  @BizQuery
  public CallHierarchyDTO getCallHierarchy(...) {
      // 无 @Auth
  }

  @BizQuery
  public DeadCodeReport detectDeadCode(@Name("indexId") String indexId) {
      // 无 @Auth — 触发全量分析
  }
  ```
- **严重程度**: P1
- **现状**: `NopCodeSymbolBizModel` 中 17 个公开方法仅有 3 个（`sourceCode`、`showSymbol`、`searchCode`）有 `@Auth` 保护。其余 15 个方法（包括符号查询、类型层次、调用层次、模块摘要、公共 API 面、死代码检测、引用查找、实现查找等）完全无权限控制。
- **风险**: 任何已认证用户（甚至未认证用户，取决于 GraphQL 配置）都可以：查询任意索引的完整符号表、获取类型/调用层次、触发昂贵的死代码分析、枚举公共 API 面。这是一个比 AR-82（仅 `detectFlows` 缺 @Auth）更系统性的安全缺口。
- **建议**: 为所有 `@BizQuery` 添加至少 `@Auth(permissions = "code-query")`。对昂贵操作（`detectDeadCode`）改为 `@BizMutation` + `@Auth(roles = "admin")`。
- **信心水平**: 确定
- **发现来源视角**: 安全面一致性审计

### [AR-89] NopCodeFileBizModel 5 个方法缺少 @Auth 保护

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java:34-91`
- **证据片段**:
  ```java
  @BizQuery
  public CodeFileAnalysisResult getByPath(
          @Name("filePath") String filePath,
          @Name("indexId") String indexId) {
      // 无 @Auth
  }

  @BizLoader(forType = CodeFileAnalysisResult.class)
  public List<CodeSymbol> symbols(@ContextSource CodeFileAnalysisResult file) {
      // 无 @Auth — 暴露所有符号信息
  }
  ```
- **严重程度**: P2
- **现状**: `getByPath`、`findPage_files`、`fileTree` 三个 `@BizQuery` 和 `symbols`、`types`、`outline` 三个 `@BizLoader` 均无 `@Auth`。仅 `sourceCode` 有 `@Auth(permissions = "code-source-read")`。对比 `NopCodeIndexBizModel` 中同类型查询均标注 `@Auth(permissions = "code-query")`，一致性断裂。
- **风险**: 补充了 AR-88 的权限缺口——通过 `NopCodeFileBizModel` 可以绕过 `NopCodeIndexBizModel` 中的权限检查，直接按文件路径获取文件信息和符号列表。
- **建议**: 为 `@BizQuery` 方法添加 `@Auth(permissions = "code-query")`。`@BizLoader` 需评估是否应要求权限（目前 GraphQL selection 机制可能已部分控制，但 `symbols` 暴露了所有符号详细信息）。
- **信心水平**: 确定
- **发现来源视角**: 安全面一致性审计

### [AR-90] detectDeadCode 是 @BizQuery 但触发全量分析，且无权限控制

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:208-211`
- **证据片段**:
  ```java
  @BizQuery
  public DeadCodeReport detectDeadCode(@Name("indexId") String indexId) {
      return codeIndexService.detectDeadCode(indexId);
  }
  ```
  对比 `NopCodeIndexBizModel.java:200-204`:
  ```java
  @BizMutation
  @Auth(roles = "admin")
  public List<ExecutionFlow> detectFlows(@Name("indexId") String indexId) {
      return codeIndexService.detectFlows(indexId);
  }
  ```
- **严重程度**: P1
- **现状**: `detectDeadCode` 在 `CodeIndexService:1335-1347` 中调用 `getOrRebuildSymbolTable()` + `getOrRebuildCallGraph()`，这会重建整个内存中的符号表和调用图（可达 10 万符号 + 50 万边），然后遍历全部符号做死代码分析。但它是 `@BizQuery`（只读语义），且无 `@Auth`。对比功能类似的 `detectFlows` 是 `@BizMutation` + `@Auth(roles = "admin")`。
- **风险**: 任何用户可以反复触发高开销的全量分析，导致内存和 CPU 压力。由于 `getOrRebuildSymbolTable` 有 `MAX_CACHE_SYMBOLS=100000` 的保护，超限时会降级为空缓存，但即便如此，查询本身仍然会加载大量数据。
- **建议**: 改为 `@BizMutation` + `@Auth(roles = "admin")`，与 `detectFlows` 保持一致。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

### [AR-91] FlowDetector.evictOverflow 无同步保护，ConcurrentHashMap.Iterator.remove() 可导致无限循环

- **文件**: `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java:522-530`
- **证据片段**:
  ```java
  private void evictOverflow(Map<String, ?> cache) {
      while (cache.size() > MAX_CACHE_ENTRIES) {
          Iterator<String> it = cache.keySet().iterator();
          if (it.hasNext()) {
              it.next();
              it.remove();
          }
      }
  }
  ```
  调用位置（`detectFlows`，非 synchronized）:
  ```java
  // FlowDetector.java:133-135
  flowCache.put(indexId, flows);
  evictOverflow(flowCache);
  evictOverflow(symbolFilePathCache);
  ```
- **严重程度**: P1
- **现状**: `flowCache` 和 `symbolFilePathCache` 是 `ConcurrentHashMap`（行 66-67），`detectFlows()` 和 `evictOverflow()` 均无 `synchronized`。`ConcurrentHashMap` 的 `Iterator.remove()` 是弱一致性的——在并发场景下不保证成功。如果 `it.remove()` 静默失败（不抛异常但也不删除），`cache.size()` 永远不变，while 循环将无限执行。
- **风险**: 当多个线程同时调用 `detectFlows` 时（例如为不同 indexId 并行检测流），`evictOverflow` 可能进入无限循环，导致线程永久阻塞。在高并发场景下是确定性的 DoS。
- **建议**: 使用原子操作替换 Iterator 模式，例如：
  ```java
  private void evictOverflow(Map<String, ?> cache) {
      while (cache.size() > MAX_CACHE_ENTRIES) {
          String key = cache.keySet().stream().findFirst().orElse(null);
          if (key == null) break;
          cache.remove(key);
      }
  }
  ```
  或者改用 `LinkedHashMap` + 同步块（与 `CodeCacheManager` 的 synchronized 模式一致）。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

### [AR-92] CodeCacheManager 全方法 synchronized 但使用 ConcurrentHashMap — 冗余设计，掩盖真正问题

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java:38-80`
- **证据片段**:
  ```java
  private final Map<String, AnalysisCache> analysisCacheMap = new ConcurrentHashMap<>();

  synchronized SymbolTable getOrRebuildSymbolTable(...) { ... }
  synchronized CallGraph getOrRebuildCallGraph(...) { ... }
  synchronized void invalidateAnalysisCache(...) {
      // ...
      while (analysisCacheMap.size() > MAX_CACHE_ENTRIES) {
          Iterator<String> it = analysisCacheMap.keySet().iterator();
          if (it.hasNext()) {
              it.next();
              it.remove();
          }
      }
  }
  ```
- **严重程度**: P3
- **现状**: 所有三个方法都是 `synchronized`，意味着 `ConcurrentHashMap` 的并发特性完全无用——实际上退化为一个普通 `HashMap`。这本身不是 bug（因为 synchronized 保证了安全），但：
  1. `ConcurrentHashMap` 相比 `HashMap` 有额外的内存和性能开销
  2. 代码给读者一种"并发安全"的假象，但安全实际上来自 `synchronized` 而非 `ConcurrentHashMap`
  3. 与 `FlowDetector`（使用了相同的 Iterator.remove 模式但 **没有** synchronized）形成鲜明对比——FlowDetector 的同样代码就是真正的 bug（AR-91）
- **风险**: 设计不一致性。如果未来有人移除 `synchronized`（认为 ConcurrentHashMap 已经足够），会立即引入 AR-91 同样的 bug。
- **建议**: 二选一：(a) 改用 `LinkedHashMap` + 保持 `synchronized`，(b) 去掉 `synchronized`，改用 `ConcurrentHashMap` 的原子操作方法。当前模式是两者混合，应统一。
- **信心水平**: 确定
- **发现来源视角**: 代码考古学家

### [AR-93] NopCodeFlowMembership 删除使用嵌套属性过滤，依赖 ORM 引擎隐式 JOIN

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:479`
- **证据片段**:
  ```java
  deleteEntitiesPaged(session, NopCodeFlowMembership.class, "flow.indexId", indexId);
  ```
  `NopCodeFlowMembership` ORM 定义中 **没有 `indexId` 列**，只有 `flowId`（外键指向 `NopCodeFlow`）。
- **严重程度**: P2
- **现状**: `NopCodeFlowMembership` 实体没有 `indexId` 字段。过滤条件 `"flow.indexId"` 通过 `NopCodeFlowMembership.flow -> NopCodeFlow.indexId` 的关联路径进行嵌套属性过滤。这需要 ORM 查询引擎将 `flow.indexId` 翻译成 SQL JOIN。如果 ORM 引擎不支持嵌套属性路径过滤（或未来行为变更），此删除操作会静默失败，留下孤儿记录。
- **风险**: 删除索引时 `NopCodeFlowMembership` 记录可能不被清理。长期积累的孤儿数据影响查询性能和数据一致性。
- **建议**: 改为显式两步删除：先查出符合条件的 `flowId` 集合，再按 `flowId` 删除 membership。或者利用 ORM 的级联删除（`NopCodeFlow` → `NopCodeFlowMembership` 已定义 `cascadeDelete`），在删除 `NopCodeFlow` 实体时让 ORM 自动级联。
- **信心水平**: 很可能（取决于 Nop ORM 引擎对嵌套属性过滤的支持程度）
- **发现来源视角**: 异常路径侦探

---

## 总评

本轮审查从安全面一致性切入，发现 `NopCodeSymbolBizModel` 和 `NopCodeFileBizModel` 的权限覆盖存在系统性缺口（AR-88、AR-89），远超之前 AR-82 发现的单个方法缺 `@Auth`。这意味着整个符号查询子系统的 20+ 个 GraphQL 端点实际上无权限保护。`detectDeadCode` 的分类错误（AR-90，`@BizQuery` 而非 `@BizMutation`）放大了这个风险。

在追踪异常路径时发现了 `FlowDetector.evictOverflow` 的潜在无限循环（AR-91），这是一个在并发负载下会触发的真实 bug。`CodeCacheManager` 的冗余同步设计（AR-92）与 `FlowDetector` 形成对比，说明这两个类由不同的人/时间编写，缺乏统一的设计约定。

## 本次审查的盲区自评

1. **代码生成模板**：未审查 `nop-code-codegen` 模块的模板正确性
2. **多语言适配器**：`nop-code-lang-python` 和 `nop-code-lang-typescript` 的解析器质量未深入
3. **xmeta / GraphQL schema 层**：未验证 xmeta 定义是否与 BizModel 方法签名精确对齐
4. **事务边界**：`ormTemplate.runInSession` 中的大批量操作是否在单个事务中执行，事务超时风险未评估
5. **IoC 配置**：未检查 `beans.xml` 中 `CodeIndexService` 及其依赖的注入配置是否正确

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 3    | @Auth 系统性缺失（AR-88）、detectDeadCode 分类错误（AR-90）、无限循环（AR-91） |
| P2      | 2    | @Auth 文件层面缺失（AR-89）、嵌套属性删除（AR-93） |
| P3      | 1    | 冗余并发设计（AR-92） |
