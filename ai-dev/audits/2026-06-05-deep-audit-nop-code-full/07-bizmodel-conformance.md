# 维度 07：BizModel 规范遵循 — nop-code 模块

## 第 1 轮（初审）

### [维度07-01] NopCodeIndexBizModel 在 ConcurrentHashMap 中持有易失性内存状态，重启后数据丢失

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:37,335-342`
- **证据片段**:
  ```java
  // Line 37
  private final Map<String, IncrementalStatus> incrementalStatusMap = new java.util.concurrent.ConcurrentHashMap<>();

  // Lines 335-342
  private void evictStatusMap() {
      while (incrementalStatusMap.size() > MAX_STATUS_ENTRIES) {
          String key = incrementalStatusMap.keySet().iterator().next();
          if (key != null) {
              incrementalStatusMap.remove(key);
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: `incrementalStatusMap` 使用 `ConcurrentHashMap` 跟踪索引状态（通过 `getIncrementalStatus` 暴露为 `@BizQuery`），完全在内存中，无持久化机制。以简单 FIFO 方式淘汰条目，硬编码最大限制 20。
- **风险**: (a) 应用重启清除所有状态 — 客户端轮询 `getIncrementalStatus` 将得到 null，无法知道索引作业是否完成。(b) 集群部署中不同节点看到不同状态。(c) FIFO 淘汰策略不确定，可能淘汰正在被轮询的活动条目。
- **建议**: 将状态持久化到数据库（例如在 `NopCodeIndex` ORM 实体上使用 `status` 字段），或使用带有 TTL 的分布式缓存。
- **信心水平**: 确定
- **误报排除**: `getIncrementalStatus` 被注解为 `@BizQuery`，是面向客户端的 API。面向客户端的 API 返回短暂性、未持久化的状态是真实的可靠性问题。
- **复核状态**: 未复核

### [维度07-02] CodeIndexService 使用 `Map<String, Object>` 进行 DAO 字段投影，绕过类型安全

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:530-541`
- **证据片段**:
  ```java
  // Lines 530-541
  QueryBean kindQuery = new QueryBean();
  kindQuery.addFilter(FilterBeans.eq("indexId", indexId));
  kindQuery.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("kind"));
  List<Map<String, Object>> kindResults = symbolDao.selectFieldsByQuery(kindQuery);
  if (!kindResults.isEmpty()) {
      Map<String, Integer> kindCounts = new LinkedHashMap<>();
      for (Map<String, Object> row : kindResults) {
          String kind = row.get("kind") != null ? row.get("kind").toString() : "UNKNOWN";
          kindCounts.merge(kind, 1, Integer::sum);
      }
      stats.setSymbolCounts(kindCounts);
  }
  ```
- **严重程度**: P3
- **现状**: `selectFieldsByQuery` 返回 `List<Map<String, Object>>`，通过字符串键和手动类型转换访问。绕过平台类型安全的 `QueryBean` + 实体模式。
- **风险**: 如果 ORM 模型重命名 `kind` 列，此查询在运行时静默中断（返回 null 而非编译时错误）。
- **建议**: 考虑使用类型化分组查询，或至少将字段名提取为常量。
- **信心水平**: 很可能
- **误报排除**: 代码库中其他地方不存在相同的 `Map<String, Object>` 模式，表明这是局部偏差。
- **复核状态**: 未复核

### [维度07-03] 作为 BizModel 返回类型的内部 DTO 类缺少 `@DataBean`

- **文件**: `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/DeadCodeReport.java:20-53`, `ExecutionFlow.java:38-49`, `ChangeAnalysisResult.java:23-75`
- **证据片段**:
  ```java
  @DataBean
  public class DeadCodeReport {
      private List<DeadCodeEntry> deadSymbols;   // DeadCodeEntry lacks @DataBean
      private DeadCodeStats stats;                // DeadCodeStats lacks @DataBean

      public static class DeadCodeEntry {        // no @DataBean
          private String symbolId;
          private String qualifiedName;
      }
      public static class DeadCodeStats {         // no @DataBean
          private int total;
      }
  }
  ```
- **严重程度**: P3
- **现状**: 多个用作 BizModel 返回类型子对象的 `public static` 内部类缺少 `@DataBean` 注解：`DeadCodeEntry`、`DeadCodeStats`、`FlowStats`、`AffectedSymbol`、`RiskBreakdown`、`RiskSummary`。
- **风险**: Nop 平台在代码生成和序列化路径中使用 `@DataBean` 进行类发现。缺少它可能导致内部类跳过生成步骤或无法正确参与平台数据对象协议。
- **建议**: 为所有 6 个内部类添加 `@DataBean`。
- **信心水平**: 很可能
- **误报排除**: 这些是 `public static` 内部类，用作 `@DataModel` 类中的字段类型。同模块中所有同级 DTO 类一致带有 `@DataBean`。
- **复核状态**: 未复核

### [维度07-04] NopCodeSymbolBizModel.findPage_symbols 使用 6 个 `@Name` 参数而非 `@RequestBean`

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:66-99`
- **证据片段**:
  ```java
  @BizQuery
  @Auth(permissions = "code-query")
  public PageBean<SymbolDTO> findPage_symbols(
          @Name("query") @Optional String query,
          @Name("kinds") @Optional List<String> kinds,
          @Name("packageName") @Optional String packageName,
          @Name("indexId") String indexId,
          @Name("offset") @Optional long offset,
          @Name("limit") @Optional int limit) {
  ```
- **严重程度**: P3
- **现状**: `findPage_symbols` 有 6 个 `@Name` 注解参数。平台约定是少量参数用 `@Name`，多参数用 `@RequestBean`。`searchCode`（也是 6 个参数）也有相同模式。
- **风险**: 添加新参数需要更改方法签名，破坏 GraphQL schema 兼容性。`@RequestBean` 允许以向后兼容方式添加可选字段。
- **建议**: 定义 `SymbolPageRequest` `@DataBean`，使用 `@RequestBean` 注解作为单个参数。
- **信心水平**: 很可能
- **误报排除**: 平台约定明确"用 @Name（少量参数）或 @RequestBean（多参数）"。6 个参数明确达到"多"的阈值。
- **复核状态**: 未复核

### [维度07-05] NopCodeSymbolBizModel 存在未使用的 `NopCodeErrors` 导入

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:27`
- **证据片段**:
  ```java
  // Line 27 - imported but never referenced in the class body
  import io.nop.code.service.NopCodeErrors;
  ```
- **严重程度**: P3
- **现状**: `NopCodeSymbolBizModel` 导入了 `NopCodeErrors` 但从未使用它。
- **风险**: 误导性导入暗示该类应处理但实际未处理的错误情况。
- **建议**: 移除未使用的导入。
- **信心水平**: 确定
- **误报排除**: grep 搜索确认类体中 import 之后无 NopCodeErrors 引用。
- **复核状态**: 未复核

### [维度07-06] NopCodeIndexBizModel.getIncrementalStatus 返回可变内部对象，无防御性复制

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:83-87`
- **证据片段**:
  ```java
  @BizQuery
  @Auth(permissions = "code-query")
  public IncrementalStatus getIncrementalStatus(@Name("indexId") String indexId) {
      return incrementalStatusMap.get(indexId);
  }
  ```
- **严重程度**: P3
- **现状**: 直接从内部 `ConcurrentHashMap` 返回可变 `IncrementalStatus` 对象。
- **风险**: 违反防御性编程原则。如果内部代码在不同线程处理请求时修改 `IncrementalStatus`，存在潜在竞态条件。
- **建议**: 返回前创建防御性副本，或将返回类型设为不可变快照。
- **信心水平**: 有趣的猜测
- **误报排除**: GraphQL 序列化阻止了此特定向量的实际可利用性，但模式仍与防御性编程规范矛盾。
- **复核状态**: 未复核

### [维度07-07] 八个简单 BizModel 类在开括号前缺少空格（代码风格）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeUsageBizModel.java:8-11` 及其他 7 个文件
- **证据片段**:
  ```java
  // 缺少空格的模式（8 个文件）:
  public class NopCodeUsageBizModel extends CrudBizModel<NopCodeUsage> implements INopCodeUsageBiz{
      public NopCodeUsageBizModel(){

  // 正确的模式:
  public class NopCodeSymbolBizModel extends CrudBizModel<NopCodeSymbol> implements INopCodeSymbolBiz {
      public NopCodeSymbolBizModel() {
  ```
- **严重程度**: P3
- **现状**: 8 个简单 BizModel 类中类声明和构造函数的开括号前缺少空格。
- **风险**: 小的风格不一致性，影响可读性和代码审查。无功能影响。
- **建议**: 在开括号前添加空格。
- **信心水平**: 确定
- **误报排除**: 所有 8 个文件显示相同格式偏差，其他 3 个复杂 BizModel 和项目中所有其他 Java 文件一致在 `{` 前使用空格。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 07-01 | P2 | NopCodeIndexBizModel.java:37 | ConcurrentHashMap 持有易失性内存状态 |
| 07-02 | P3 | CodeIndexService.java:530 | Map<String, Object> DAO 字段投影 |
| 07-03 | P3 | DeadCodeReport.java 等 6 个内部类 | 内部 DTO 缺少 @DataBean |
| 07-04 | P3 | NopCodeSymbolBizModel.java:66 | 6 个 @Name 参数应用 @RequestBean |
| 07-05 | P3 | NopCodeSymbolBizModel.java:27 | 未使用的 NopCodeErrors 导入 |
| 07-06 | P3 | NopCodeIndexBizModel.java:83 | 返回可变内部对象无防御性复制 |
| 07-07 | P3 | 8 个简单 BizModel 文件 | 开括号前缺少空格 |
