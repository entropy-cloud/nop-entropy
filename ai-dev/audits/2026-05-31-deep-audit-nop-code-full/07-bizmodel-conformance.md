# 审核维度 07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] detectFlows 标注为 @BizQuery 但实际执行数据库持久化

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:190`
- **证据片段**:
  ```java
  // NopCodeIndexBizModel.java:190
  @BizQuery                          // 标注为查询，但下游会写入DB
  public List<ExecutionFlow> detectFlows(@Name("indexId") String indexId) {
      return codeIndexService.detectFlows(indexId);
  }
  
  // CodeIndexService.java:1278-1294
  public List<ExecutionFlow> detectFlows(String indexId) {
      ...
      persistFlows(indexId, flows);   // 写入 NopCodeFlow + NopCodeFlowMembership 表
      return flows;
  }
  ```
- **严重程度**: P2
- **现状**: detectFlows 使用 @BizQuery 注解，但调用链中 CodeIndexService.detectFlows() 会删除旧 Flow 记录再写入新的 Flow 和 Membership 记录。
- **风险**: (1) GraphQL 协议层面 Query 操作不应有副作用。(2) Nop 平台事务管理对 @BizQuery/@BizMutation 可能有不同策略。(3) 客户端可能认为 Query 安全可重试。
- **建议**: 将 detectFlows 改为 @BizMutation。
- **信心水平**: 95%
- **误报排除**: 已确认 persistFlows 方法执行 batchDelete + save 操作。
- **复核状态**: 未复核

### [维度07-02] IncrementalStatus 内部类作为 BizModel 返回类型但缺少 @DataBean 注解

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:259-314`
- **证据片段**:
  ```java
  // NopCodeIndexBizModel.java:83-86 — 返回类型使用 IncrementalStatus
  @BizQuery
  public IncrementalStatus getIncrementalStatus(@Name("indexId") String indexId) {
      return incrementalStatusMap.get(indexId);
  }
  
  // NopCodeIndexBizModel.java:259-265 — 内部类定义，无 @DataBean
  public static class IncrementalStatus {
      private String indexId;
      private String mode;
      ...
  ```
- **严重程度**: P3
- **现状**: IncrementalStatus 是公共静态内部类，作为 BizModel 方法返回类型。同模块所有其他 33 个 DTO 均标注 @DataBean。
- **风险**: @DataBean 是 Nop 平台 GraphQL 序列化标记注解。缺少可能导致 GraphQL Schema 生成时无法识别该类型。
- **建议**: 提取为独立顶级类到 io.nop.code.service.api.dto 包并添加 @DataBean。
- **信心水平**: 90%
- **误报排除**: 已确认 33 个其他 DTO 均含 @DataBean，此为唯一缺失。
- **复核状态**: 未复核

### [维度07-03] entityToCodeSymbol 方法在三个服务类中完全重复（DRY 违反）

- **文件**:
  1. `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:209-238`
  2. `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:35-64`
  3. `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:304-333`
- **证据片段**:
  ```java
  private CodeSymbol entityToCodeSymbol(NopCodeSymbol entity) {
      CodeSymbol symbol = new CodeSymbol();
      symbol.setId(entity.getId());
      symbol.setName(entity.getName());
      // ... 30行完全相同的映射逻辑，出现在三个文件中
  }
  ```
- **严重程度**: P2
- **现状**: entityToCodeSymbol 在三个类中各有一份完全相同的拷贝（每份约 30 行）。entityToInheritance 也在两个类中重复。
- **风险**: 维护成本高，新增字段需同步修改三处。行为不一致风险。
- **建议**: 提取到 CodeCacheManager 或新建 CodeEntityConverter 工具类。
- **信心水平**: 98%
- **误报排除**: 已确认三处代码逐行比对完全一致。
- **复核状态**: 未复核

### [维度07-04] findReferencedBy 存在 N+1 查询模式

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:579-626`
- **证据片段**:
  ```java
  // CodeQueryService.java:601-625
  return usages.stream().map(usage -> {
      ...
      if (usage.getFileId() != null) {
          NopCodeFile file = fileDao.getEntityById(usage.getFileId()); // N+1
      }
      if (usage.getEnclosingSymbolId() != null) {
          NopCodeSymbol enclosing = symbolDao.getEntityById(usage.getEnclosingSymbolId()); // N+1
      }
      ...
  }).collect(Collectors.toList());
  ```
- **严重程度**: P2
- **现状**: 对每条 usage 分别查询关联的 File 和 Symbol 实体。默认 limit=50 时最坏 1+50*2=101 次查询。
- **风险**: 大型索引上查询高频引用符号时性能显著下降。
- **建议**: 预先批量加载所有需要的实体到 Map 缓存中。
- **信心水平**: 90%
- **误报排除**: ORM Session 的 first-level cache 可能缓解。但此代码不在 ormTemplate.runInSession 内。
- **复核状态**: 未复核

### [维度07-05] findImplementations 加载索引内全部符号到内存

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:666-739`
- **行号**: 特别是第 684-692 行
- **证据片段**:
  ```java
  Map<String, String> idToQn = new HashMap<>();
  QueryBean allSymForInhQuery = new QueryBean();
  allSymForInhQuery.addFilter(FilterBeans.eq("indexId", indexId));
  allSymForInhQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);  // MAX=10000
  for (NopCodeSymbol sym : symDaoForInh.findAllByQuery(allSymForInhQuery)) {
      if (sym.getQualifiedName() != null) {
          idToQn.put(sym.getId(), sym.getQualifiedName());
      }
  }
  ```
- **严重程度**: P2
- **现状**: 为解析 superTypeId→qualifiedName 加载整个索引全部符号（最多 10000 条）到内存。
- **风险**: 大型索引下显著内存和查询开销。频繁调用无缓存机制。
- **建议**: 利用已有的 CodeCacheManager.getOrRebuildSymbolTable() 缓存。
- **信心水平**: 85%
- **误报排除**: CodeGraphService 已使用 cacheManager，但 CodeQueryService 未利用。
- **复核状态**: 未复核

### [维度07-06] ensureSubServices() 线程安全缺陷

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:110-116`
- **证据片段**:
  ```java
  // 字段声明，非 volatile
  private CodeSearchService searchService;
  private CodeGraphService graphService;
  private CodeQueryService queryService;
  
  private void ensureSubServices() {
      if (searchService == null && daoProvider != null) {   // check
          searchService = new CodeSearchService(...);         // create
          graphService = new CodeGraphService(...);
          queryService = new CodeQueryService(...);
      }
  }
  ```
- **严重程度**: P3
- **现状**: 三个字段非 volatile 也无同步保护。多线程并发调用时可能重复创建实例。
- **风险**: 实际生产环境发生概率低（通常启动时单线程初始化）。但在集群或异步场景下可能暴露。
- **建议**: 标记 volatile 或加 synchronized。
- **信心水平**: 80%
- **误报排除**: 如果 NopIoC 在初始化阶段完成首次调用则竞态不成立。
- **复核状态**: 未复核
