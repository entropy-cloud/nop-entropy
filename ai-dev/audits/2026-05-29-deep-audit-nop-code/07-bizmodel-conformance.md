# 维度 07：BizModel 规范遵循

**审计日期**: 2026-05-29
**审计范围**: nop-code-service 中 11 个 @BizModel 注解类 + CodeIndexService.java (3004行)

---

## 第 1 轮（初审）

### [维度07-01] CodeIndexService 违反命名约定且构成 God Class（3004行）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: 全文件 1-3004
- **证据片段**:
  ```java
  // 第98行：类声明使用 *Service 后缀
  public class CodeIndexService implements ICodeIndexService {
      // 文件内部包含约 23 个功能分区：
      // 第204行: Entity-to-Model Conversion
      // 第330行: Indexing
      // 第378行: File Queries
      // 第629行: Symbol Queries
      // 第722行: Search
      // 第1078行: Type Queries
      // 第1130行: Hierarchy Queries
      // 第1274行: Index Management
      // 第1406行: Graph Analysis
      // 第1563行: Dependency Graph
      // 第1794行: Incremental Indexing
      // 第1920行: ORM Persistence
      // 第2463行: Flow Analysis
      // 第2896行: Batch File Records
  }
  ```
- **严重程度**: P2
- **现状**: 类名使用 *Service 后缀（违反 service-layer.md 约定），单文件 3004 行包含 15+ 功能分区，被 3 个 BizModel 共同注入使用。
- **风险**: 违反平台命名约定（"不要创建 *Service 类"）；God Class 高认知成本；15+ 个功能分区说明职责过重。
- **建议**: 按功能分区拆分为独立 Processor（如 IndexPersistenceProcessor、GraphAnalysisProcessor、DependencyGraphProcessor 等）；重命名为符合 Nop 习惯的名称。
- **信心水平**: 高
- **误报排除**: 3004 行的 God Class 是真实的维护成本，命名违反平台文档中的明确约定。
- **复核状态**: 未复核

### [维度07-02] NopCodeIndexBizModel.IncrementalStatus 缺少 @DataBean 注解

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:247-302`
- **证据片段**:
  ```java
  // 第75-78行：作为 @BizQuery 返回值
  @BizQuery
  public IncrementalStatus getIncrementalStatus(@Name("indexId") String indexId) {
      return incrementalStatusMap.get(indexId);
  }
  // 第247行：内部类无 @DataBean
  public static class IncrementalStatus {
      private String indexId;
      private String mode;
      private int fileCount;
      private boolean completed;
      // ... getter/setter
  }
  ```
- **严重程度**: P3
- **现状**: IncrementalStatus 作为 @BizQuery 方法返回值暴露给 GraphQL，但未标注 @DataBean。模块内其他 28 个 DTO 均标注了 @DataBean。
- **风险**: GraphQL 序列化引擎依赖 @DataBean 进行类型发现和 schema 生成，缺少注解可能导致字段被忽略或序列化异常。
- **建议**: 添加 @DataBean 注解，或提取为独立 DTO 类。
- **信心水平**: 高
- **误报排除**: @DataBean 在 Nop 平台中有功能性作用（GraphQL schema 生成）。
- **复核状态**: 未复核

### [维度07-03] NopCodeSymbolBizModel 的 @BizLoader 缺少 forType 导致 ContextSource 类型与 BizModel 实体类型不一致

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:96-120`
- **证据片段**:
  ```java
  // 第38-39行：BizModel 实体类型是 NopCodeSymbol
  @BizModel("NopCodeSymbol")
  public class NopCodeSymbolBizModel extends CrudBizModel<NopCodeSymbol> {
      // 第96-108行：@BizLoader 无 forType，@ContextSource 使用 SymbolDTO（非实体）
      @BizLoader
      public List<AnnotationUsageDTO> usages(
              @ContextSource SymbolDTO symbol,
              @Name("indexId") String indexId) {
          // ...
      }
  }
  ```
  对比同模块正确用法：
  ```java
  // NopCodeFileBizModel.java:55
  @BizLoader(forType = CodeFileAnalysisResult.class)
  public List<CodeSymbol> symbols(@ContextSource CodeFileAnalysisResult file) { ... }
  ```
- **严重程度**: P2
- **现状**: 两个 @BizLoader 方法未指定 forType，但 @ContextSource 参数类型为 SymbolDTO（DTO，非实体类型 NopCodeSymbol）。
- **风险**: GraphQL 引擎默认将 loader 注册到 NopCodeSymbol 类型，但方法期望 SymbolDTO，可能导致运行时类型转换失败。
- **建议**: 添加 forType = SymbolDTO.class 使其显式化，或将 @ContextSource 改为 NopCodeSymbol。
- **信心水平**: 中高
- **误报排除**: forType 缺失可能导致运行时类型错误；同模块 NopCodeFileBizModel 的正确用法佐证这是遗漏。
- **复核状态**: 未复核

### [维度07-04] NopCodeIndexBizModel 的 incrementalStatusMap 使用内存态存储，重启即丢失

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:33,45-51,65-69,76-78,104-107`
- **证据片段**:
  ```java
  // 第33行
  private final Map<String, IncrementalStatus> incrementalStatusMap = new ConcurrentHashMap<>();
  // 第45-51行
  incrementalStatusMap.put(indexId, status);
  // 第76-78行
  @BizQuery
  public IncrementalStatus getIncrementalStatus(@Name("indexId") String indexId) {
      return incrementalStatusMap.get(indexId);
  }
  ```
- **严重程度**: P3
- **现状**: incrementalStatusMap 是实例级 ConcurrentHashMap，索引状态仅存于 JVM 内存中，重启后丢失。
- **风险**: 应用重启后状态丢失；多实例部署时状态不共享。
- **建议**: 将状态持久化到 NopCodeIndex 实体扩展字段或专门配置表。
- **信心水平**: 中
- **误报排除**: 内存态状态与持久化数据不一致是真实的运行时行为差异，但当前仅影响查询展示，评为 P3。
- **复核状态**: 未复核

## 清洁项

1. **实体-BizModel-xmeta 三元组完整**: 全部 11 个 BizModel 均有对应 ORM 实体和 xmeta。
2. **继承与构造函数**: 全部正确继承 CrudBizModel<T> 并调用 setEntityName()。
3. **注入规范**: 所有 @Inject 字段均为 protected。
4. **DTO 使用**: 28 个 DTO 均标注 @DataBean，无 Map<String, Object> 反模式。
5. **注解使用**: @BizQuery/@BizMutation 使用正确。
6. **跨模块调用**: 通过 ICodeIndexService 接口调用，而非直接注入实现类。
