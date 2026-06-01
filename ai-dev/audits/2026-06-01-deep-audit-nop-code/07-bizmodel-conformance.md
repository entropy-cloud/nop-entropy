# 维度 07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] NopCodeIndexBizModel 使用内存 ConcurrentHashMap 存储增量索引状态

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:37`
- **证据片段**:
  ```java
  private final Map<String, IncrementalStatus> incrementalStatusMap = new java.util.concurrent.ConcurrentHashMap<>();
  ```
- **严重程度**: P2
- **现状**: BizModel 是 NopIoC 单例，incrementalStatusMap 是进程内内存状态。重启丢失，集群不共享。evictStatusMap() 驱逐策略依赖非确定顺序。
- **风险**: 多实例部署时各节点状态不一致；重启后所有增量索引状态丢失。
- **建议**: 将状态持久化到 ORM 实体或使用分布式缓存。
- **信心水平**: 确定
- **误报排除**: ConcurrentHashMap 在单例 Bean 中存储业务状态是明确的状态管理问题。
- **复核状态**: 未复核

### [维度07-02] findCycles 返回 `List<List<String>>` 缺少类型安全 DTO

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:181-188`
- **证据片段**:
  ```java
  @BizQuery
  @Auth(roles = "admin")
  public List<List<String>> findCycles(
          @Name("indexId") String indexId,
          @Name("minSize") @Optional Integer minSize) {
  ```
- **严重程度**: P2
- **现状**: 方法返回裸 `List<List<String>>`，无 DTO 封装。内层 List 语义不明确。
- **风险**: GraphQL schema 无法为列表元素提供字段名和文档；后续增加元数据需破坏性变更。
- **建议**: 引入 CycleDTO，与其他分析方法（CommunityDetectionResultDTO、CriticalNodeResultDTO）保持一致。
- **信心水平**: 确定
- **误报排除**: 同 BizModel 的其他分析方法均使用结构化 DTO，findCycles 是唯一遗漏。
- **复核状态**: 未复核

### [维度07-03] findPage_symbols 静默吞掉无效枚举值

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:75-86`
- **证据片段**:
  ```java
  kindList = kinds.stream()
          .map(k -> {
              try {
                  return Enum.valueOf(CodeSymbolKind.class, k);
              } catch (IllegalArgumentException e) {
                  return null;
              }
          })
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
  ```
- **严重程度**: P2
- **现状**: 无效 kinds 值被静默过滤，用户不会收到错误提示，得到意外的空结果。
- **风险**: 用户误传参数无法感知错误，调试困难。
- **建议**: 对无效枚举值抛出 NopException + ErrorCode。
- **信心水平**: 很可能
- **误报排除**: Nop 平台错误处理规范要求对非法输入明确报错。
- **复核状态**: 未复核

### [维度07-04] NopCodeIndexBizModel 承载 24 个自定义方法，混合多个领域关注点

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:27-343`
- **证据片段**:
  ```java
  // 索引管理（5 methods）+ 统计查询（2）+ 图分析（4）+ 依赖分析（5）+ 流程分析（5）+ 图比较/导出（3）
  // 全部在同一个 BizModel 中
  ```
- **严重程度**: P2
- **现状**: 24 个自定义方法按职责可分为 5-6 个子域，但全部聚合在一个 BizModel 中。
- **风险**: 后续增加分析能力会进一步膨胀。343 行超出单职责合理范围。
- **建议**: 将图分析、依赖分析、流程分析拆分为独立 BizModel。
- **信心水平**: 很可能
- **误报排除**: 24 方法/343 行在单一 BizModel 中是可量量的结构性问题。
- **复核状态**: 未复核

### [维度07-05] @BizQuery 鉴权级别不一致

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:124-276`
- **证据片段**:
  ```java
  @Auth(roles = "admin")  // detectCommunities
  @Auth(permissions = "code-query")  // getDeps
  @Auth(roles = "admin")  // diffGraph
  ```
- **严重程度**: P3
- **现状**: 同一 BizModel 中 15 个方法用 admin 角色，9 个用 code-query 权限，无明显规律。
- **风险**: 鉴权策略不一致导致困惑和可能的越权/过度限制。
- **建议**: 按操作类型和资源敏感度统一划分权限。
- **信心水平**: 很可能
- **误报排除**: 同 BizModel 内 24 个方法缺乏一致的鉴权分层逻辑。
- **复核状态**: 未复核

### [维度07-06] @BizLoader 作用于非实体 DTO 类型

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java:58-96`
- **证据片段**:
  ```java
  @BizLoader(forType = CodeFileAnalysisResult.class)
  public List<CodeSymbol> symbols(@ContextSource CodeFileAnalysisResult file) {
      return file.getSymbols();
  }
  ```
- **严重程度**: P3
- **现状**: BizModel 实体类型是 NopCodeFile，但 @BizLoader 的 forType 指定了 CodeFileAnalysisResult。部分 loader 仅透传 getter。
- **风险**: loader 注册位置不直观，部分增加不必要间接层。
- **建议**: 考虑在 xmeta 中声明 computed prop 或直接让 GraphQL 序列化 DTO 字段。
- **信心水平**: 有趣的猜测
- **误报排除**: Nop 平台支持 forType 全局注册，不是语法错误。
- **复核状态**: 未复核

### [维度07-07] findPage_symbols 使用 6 个 @Name 参数，超出合理阈值

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:66-74`
- **证据片段**:
  ```java
  public PageBean<SymbolDTO> findPage_symbols(
          @Name("query") @Optional String query,
          @Name("kinds") @Optional List<String> kinds,
          @Name("packageName") @Optional String packageName,
          @Name("indexId") String indexId,
          @Name("offset") @Optional long offset,
          @Name("limit") @Optional int limit) {
  ```
- **严重程度**: P3
- **现状**: 6 个独立 @Name 参数，Nop 平台规范建议多参数用 @RequestBean。
- **风险**: 后续增加过滤条件导致参数列表膨胀。
- **建议**: 引入 SymbolPageRequest RequestBean。
- **信心水平**: 确定
- **误报排除**: 6 个参数 + 含类型转换逻辑，拆出 RequestBean 可内聚转换逻辑。
- **复核状态**: 未复核
