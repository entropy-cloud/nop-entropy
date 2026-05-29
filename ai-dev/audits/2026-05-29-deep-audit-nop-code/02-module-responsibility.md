# 维度 02：模块职责与文件边界

**审计日期**: 2026-05-29

## 第 1 轮（初审）

### [维度02-01] CodeIndexService.java 严重违反单一职责原则（God Object, 3003行）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1-3003`
- **证据片段**:
  ```java
  public class CodeIndexService implements ICodeIndexService {
      // 索引管理: indexDirectory, deleteIndex, triggerIncrementalIndex
      // 文件查询: getFiles, getFile, getFileOutline, getFileTree
      // 符号查询: getSymbolById, findSymbol, searchCode
      // 类型/调用层次: getTypeHierarchy, getCallHierarchy
      // 图分析: detectCommunities, getGraphAnalysis, getImpactAnalysis, ...
      // 依赖图: getDeps, findCycles, getDepGraph + Tarjan算法
      // 流程分析: detectFlows, detectDeadCode
      // ORM持久化: persistInSession, saveFileResultInSession (259行)
      // 批量管理: batchSaveFileRecords, batchLoadFileRecords
  }
  ```
- **严重程度**: P2
- **现状**: 3003行，109个方法，跨越8+个不相关职责域。被3个BizModel共同注入。
- **风险**: 难以理解和维护；功能变更可能意外影响其他功能；merge 冲突概率高。
- **建议**: 按职责域拆分为独立 Processor 或 Service。
- **信心水平**: 95%
- **误报排除**: 109个方法明确跨越8+个不相关职责域。
- **复核状态**: 未复核

### [维度02-02] 语言特定 ImportResolver 误放于 core 模块

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/resolver/JavaImportResolver.java`, `PythonImportResolver.java`, `TypeScriptImportResolver.java`
- **证据片段**:
  ```java
  public class JavaImportResolver implements IImportResolver {
      public String getLanguage() { return "JAVA"; }
      String candidatePath = "src/main/java/" + qualifiedName.replace('.', '/') + ".java";
  }
  ```
- **严重程度**: P3
- **现状**: 语言特定实现放在 core 而非各自 lang-* 模块，core 模块应是语言无关的。
- **风险**: 添加新语言需修改 core 模块，违反 OCP。
- **建议**: 迁移到对应 lang-* 模块。
- **信心水平**: 85%
- **误报排除**: ImportResolver 逻辑简单依赖少，但按模块职责应归属 lang-* 层。
- **复核状态**: 未复核

### [维度02-03] api 模块空壳，接口/DTO 全在 service

- **文件**: `nop-code/nop-code-api/` (仅 pom.xml), `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/` (ICodeIndexService + 35 DTO)
- **严重程度**: P2（与维度01-01同一问题的不同视角）
- **现状**: api 模块完全空壳，接口和DTO全部放在 service 模块中。
- **风险**: 其他模块需要引用接口时必须依赖整个 service 模块。
- **建议**: 将接口和DTO迁至 api 模块，或删除空壳 api 模块。
- **信心水平**: 80%
- **误报排除**: 单体应用可不需要 api 层分离，但空壳模块增加构建复杂度。
- **复核状态**: 未复核

### [维度02-04] CodeIndexService 直接内联 Tarjan SCC 算法（图算法逻辑不应在 service 层）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1742-1792`
- **证据片段**:
  ```java
  private List<List<String>> tarjanSCC(Map<String, List<String>> adj) { ... }
  private void tarjanDFS(String v, Map<String, List<String>> adj, ...) { ... }
  ```
- **严重程度**: P2
- **现状**: Tarjan 算法是纯图论算法，应在 nop-code-graph 模块而非 service 层。
- **风险**: 图算法逻辑散落在两处，service 层承担不应有的算法职责。
- **建议**: 移至 nop-code-graph 模块（如 CycleDetector 类）。
- **信心水平**: 90%
- **误报排除**: 无。Tarjan 算法是纯图论算法，明确属于 graph 模块。
- **复核状态**: 未复核

### [维度02-05] saveFileResultInSession 包含 259 行重复性 ORM 映射代码

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2039-2297`
- **证据片段**:
  ```java
  private void saveFileResultInSession(String indexId, CodeFileAnalysisResult file, IOrmSession session) {
      // 2044-2063: File 实体映射
      // 2065-2131: Symbol 实体映射 + SearchEngine 同步
      // 2133-2150: Call 实体映射
      // 2152-2162: Inheritance 实体映射
      // 2164-2176: AnnotationUsage 实体映射
      // 2178-2238: Usage(CALL/ANNOTATES/EXTENDS/TESTED_BY) 生成
      // 2264-2296: Dependency 生成
  }
  ```
- **严重程度**: P2
- **现状**: 单方法 259 行，9种不同实体的 ORM 映射逻辑混合。
- **风险**: 新增字段时需修改此方法；难以独立测试。
- **建议**: 提取独立 CodePersistenceHelper，每种实体一个 toXxxEntity() 方法。
- **信心水平**: 92%
- **误报排除**: 无。259行纯映射代码是明确的职责混合。
- **复核状态**: 未复核

### [维度02-06] FlowDetector 硬编码 Java/Spring 特定框架常量

- **文件**: `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java:28-67`
- **证据片段**:
  ```java
  private static final Set<String> EXTERNAL_PREFIXES = Set.of(
      "java.", "javax.", "jakarta.", "org.springframework.", ...);
  private static final Set<String> SPRING_ENTRY_ANNOTATIONS = Set.of(
      "org.springframework.web.bind.annotation.RequestMapping", ...);
  ```
- **严重程度**: P3
- **现状**: flow 模块应是语言无关的，但硬编码了 Java/Spring 常量。已有 IEntryPointPatternProvider 扩展点但未使用。
- **风险**: 对 Python/TypeScript 项目需修改 flow 模块代码。
- **建议**: 将常量移到 DefaultEntryPointPatternProvider 或配置文件中。
- **信心水平**: 80%
- **误报排除**: 扩展点设计已存在但未完全使用。
- **复核状态**: 未复核
