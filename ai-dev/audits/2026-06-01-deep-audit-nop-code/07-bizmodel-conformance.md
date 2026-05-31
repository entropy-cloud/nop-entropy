# 维度 07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] detectFlows() @BizMutation 缺少 @Auth 鉴权注解

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:190-193`
- **证据片段**:
  ```java
  @BizMutation
  public List<ExecutionFlow> detectFlows(@Name("indexId") String indexId) {
      return codeIndexService.detectFlows(indexId);
  }
  ```
- **严重程度**: P2
- **现状**: `detectFlows()` 标记了 `@BizMutation` 但未添加 `@Auth` 注解。同文件中所有其他 `@BizMutation` 方法均带有 `@Auth(roles = "admin")`（triggerFullIndex、triggerIncrementalIndex、indexDirectory、indexFile、deleteIndex）。`detectFlows()` 是唯一缺失鉴权的 mutation。
- **风险**: 此 mutation 触发昂贵计算操作（重建 SymbolTable + CallGraph + 执行流检测 + DB 持久化），未授权用户可调用导致资源被恶意消耗（DoS 向量）和数据库被写入未授权记录。
- **建议**: 为 `detectFlows()` 添加 `@Auth(roles = "admin")` 注解。
- **信心水平**: 确定
- **误报排除**: 同一文件内 6 个 mutation 中 5 个有 @Auth，形成可量化不一致。
- **复核状态**: 未复核

### [维度07-02] NopCodeFileBizModel 的 @BizLoader (symbols/types/outline) 返回空数据

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java:55-91`
- **证据片段**:
  ```java
  @BizLoader(forType = CodeFileAnalysisResult.class)
  public List<CodeSymbol> symbols(@ContextSource CodeFileAnalysisResult file) {
      return file.getSymbols();  // 永远返回空列表
  }

  @BizLoader(forType = CodeFileAnalysisResult.class)
  public List<CodeSymbol> types(@ContextSource CodeFileAnalysisResult file) {
      return file.getSymbols().stream()  // 永远返回空列表
              .filter(s -> s.getKind() == CodeSymbolKind.CLASS
                      || s.getKind() == CodeSymbolKind.INTERFACE
                      || s.getKind() == CodeSymbolKind.ENUM
                      || s.getKind() == CodeSymbolKind.ANNOTATION_TYPE)
              .collect(Collectors.toList());
  }
  ```
- **严重程度**: P2
- **现状**: `entityToFileResult()` 仅填充 filePath、packageName、language、lineCount、sourceCode 五个字段。`CodeFileAnalysisResult.symbols` 由字段初始化器设为 `new ArrayList<>()`，从未被填充。因此 `symbols`、`types`、`outline` 三个 @BizLoader 始终返回空数据。
- **风险**: GraphQL 客户端查询 `NopCodeFile__getByPath { symbols { name } }` 时始终收到空结果。API 契约承诺了字段但实际不返回数据。
- **建议**: @BizLoader 应从数据库实际加载 symbols 数据，或在 entityToFileResult() 中补充加载。
- **信心水平**: 确定
- **误报排除**: @BizLoader forType 声明了字段，客户端可以查询并预期得到数据，但始终为空。
- **复核状态**: 未复核

### [维度07-03] entityToCodeSymbol() 方法在三个类中重复实现

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
      symbol.setKind(entity.getKind() != null ? CodeSymbolKind.valueOf(entity.getKind()) : null);
      // ... 完全相同的 26 行转换逻辑 ...
      symbol.setExtData(entity.getExtData());
      return symbol;
  }
  ```
- **严重程度**: P2
- **现状**: 相同的 `entityToCodeSymbol()` 转换方法在三个 package-private 服务类中各有一份副本（90 行重复代码）。
- **风险**: NopCodeSymbol 新增字段时需同步修改三处。遗漏任何一处会导致数据不一致。
- **建议**: 将转换逻辑提取为共享方法或独立 EntityMapper 工具类。
- **信心水平**: 确定
- **误报排除**: 90 行完全相同的代码复制，30+ 字段每次 schema 变更需改三处。
- **复核状态**: 未复核

### [维度07-04] getIndexStats() 全量加载实体仅用于计数，大索引下存在性能/内存风险

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:470-501`
- **证据片段**:
  ```java
  IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
  QueryBean symbolQuery = new QueryBean();
  symbolQuery.addFilter(FilterBeans.eq("indexId", indexId));
  List<NopCodeSymbol> allSymbols = symbolDao.findAllByQuery(symbolQuery);  // 无 limit，加载全部

  stats.setSymbolCount(allSymbols.size());  // 仅取 size()
  stats.setFileCount(fileDao.findAllByQuery(fileQuery).size());  // 全量加载 file 仅取 size()
  ```
- **严重程度**: P2
- **现状**: `getIndexStats()` 将全部 NopCodeSymbol 和 NopCodeFile 实体加载到内存仅为了取 `.size()`。Nop DAO API 提供了 `countByQuery()` 方法可替代。
- **风险**: 大型项目（数千文件、数万符号）每次查询占用大量 JVM 堆内存。并发调用场景下风险放大。
- **建议**: 至少 `fileDao.findAllByQuery(fileQuery).size()` 应改为 `fileDao.countByQuery(fileQuery)`。symbolCount 可用 SQL GROUP BY 获取 kind 分布。
- **信心水平**: 确定
- **误报排除**: Nop DAO API 明确提供 `countByQuery()`，此处未使用。fileCount 加载含 sourceCode 大文本字段的全量实体仅取 size。
- **复核状态**: 未复核

### [维度07-05] IncrementalStatus 内部类缺少 @DataBean 注解

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:259-314`
- **证据片段**:
  ```java
  @BizQuery
  public IncrementalStatus getIncrementalStatus(@Name("indexId") String indexId) {
      return incrementalStatusMap.get(indexId);
  }

  public static class IncrementalStatus {
      private String indexId;
      private String mode;
      private int fileCount;
      private int symbolCount;
      private boolean completed;
      private String errorMessage;
      // ... 手动 getter/setter ...
  }
  ```
- **严重程度**: P3
- **现状**: `IncrementalStatus` 作为 `@BizQuery` 方法的返回类型，缺少 `@DataBean` 注解。本模块中所有其他 33 个 DTO 均正确标注了 `@DataBean`。
- **风险**: `@DataBean` 在 Nop 平台触发编译期自动生成 GraphQL schema 元数据，缺失可能导致 schema 不完整。与模块内其他 DTO 定义方式不一致。
- **建议**: 提取为独立文件 `IncrementalStatusDTO.java`，添加 `@DataBean` 注解。
- **信心水平**: 确定
- **误报排除**: 模块内 33/34 的 DTO 都有 @DataBean，此为唯一例外。
- **复核状态**: 未复核

## 深挖第 2 轮追加

第 1 轮已完整覆盖全部 11 个 BizModel 和 4 个核心服务类。无新发现。深挖结束。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 07-01 | P2 | NopCodeIndexBizModel.java:190-193 | detectFlows() @BizMutation 缺少 @Auth 鉴权注解 |
| 07-02 | P2 | NopCodeFileBizModel.java:55-91 | @BizLoader symbols/types/outline 始终返回空数据 |
| 07-03 | P2 | CodeIndexService/CodeQueryService/CodeGraphService | entityToCodeSymbol() 在三个类中重复（90行） |
| 07-04 | P2 | CodeIndexService.java:470-501 | getIndexStats() 全量加载实体仅取 size() |
| 07-05 | P3 | NopCodeIndexBizModel.java:259-314 | IncrementalStatus 缺少 @DataBean 注解 |
