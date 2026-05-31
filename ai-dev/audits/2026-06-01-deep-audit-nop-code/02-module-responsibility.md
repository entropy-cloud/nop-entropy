# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] CodeIndexService.java 承担 God Class 职责（1573 行）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **证据片段**:
  ```java
  public class CodeIndexService implements ICodeIndexService {
      private CodeSearchService searchService;
      private CodeGraphService graphService;
      private CodeQueryService queryService;
      // ... 74 处直接 ORM 操作
      // 索引持久化、查询委派、图分析委派、流分析委派四大职责混合
      // 构造器中硬编码 new JavaLanguageAdapter() 等
      // 两个功能重叠的 updateIndexStats 重载方法（行 855 和行 1276）
  ```
- **严重程度**: P2
- **现状**: 同时承担索引持久化、查询委派、图分析委派、流分析委派四大职责。包含 74 处直接 ORM 操作，硬编码语言适配器实例化，两个重叠的 updateIndexStats 方法。
- **风险**: 修改任何一侧功能都需要理解整个 1573 行文件，回归风险高。新增功能倾向继续堆叠在此类中。
- **建议**: 持久化逻辑提取到专门的 Repository/Store 类；语言适配器注册移到 beans.xml IoC 配置。
- **信心水平**: 确定
- **误报排除**: 1573 行、74 处 ORM 操作、4 大职责混合是可量化证据。
- **复核状态**: 未复核

### [维度02-02] ICodeIndexService 接口承担 God Interface（29 个方法，10 个功能域）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java`
- **证据片段**:
  ```java
  public interface ICodeIndexService {
      // Indexing (3), File Queries (5), Symbol Queries (8), 
      // Graph Analysis (7), Dependency Graph (4), Flow Analysis (5),
      // Batch + Incremental + Management
      // 共 29 个方法
  }
  ```
- **严重程度**: P2
- **现状**: 单一接口横跨索引管理、文件查询、符号查询、类型查询、图分析、依赖图、流分析共 10 个功能分区。
- **风险**: 违反接口隔离原则。图分析、流分析等功能应各自拆分为独立接口。
- **建议**: 拆分为 ICodeIndexService（核心索引）+ ICodeGraphService（图分析）+ ICodeFlowService（流分析）等。
- **信心水平**: 确定
- **误报排除**: 29 个方法、10 个功能域是可量化证据。
- **复核状态**: 未复核

### [维度02-03] nop-code-core 中放置了语言特定的 ImportResolver 实现

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/resolver/JavaImportResolver.java`, `PythonImportResolver.java`, `TypeScriptImportResolver.java`
- **证据片段**:
  ```java
  public class JavaImportResolver implements IImportResolver {
      @Override
      public String getLanguage() { return "JAVA"; }
      // Java-specific: "src/main/java/" path mapping
  }
  ```
- **严重程度**: P2
- **现状**: core 模块定位为"语言无关的核心分析逻辑"，但包含了 Java/Python/TypeScript 三种语言的 ImportResolver 实现。这些实现应移入各自的 lang-* 子模块。
- **风险**: core 模块每新增一种语言都要修改，违反开闭原则。
- **建议**: 将语言特定 ImportResolver 移入各自 lang-* 子模块，core 仅保留 IImportResolver 接口。
- **信心水平**: 确定
- **误报排除**: 不是"看起来不优雅"，是可量化的职责越权（core 不应包含语言特定代码）。
- **复核状态**: 未复核

### [维度02-04] CodeIndexService 硬编码跨模块具体类实例化

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:165-205`
- **证据片段**:
  ```java
  public CodeIndexService() {
      this.registry = new LanguageAdapterRegistry();
      this.registry.registerAdapter(new JavaLanguageAdapter());      // lang-java
      this.registry.registerAdapter(new PythonLanguageAdapter());    // lang-python
      this.registry.registerAdapter(new TypeScriptLanguageAdapter()); // lang-typescript
      registerSemanticExtractors(); // new NameSimilarityExtractor() 等 graph 模块类
      registerImportResolvers();    // new JavaImportResolver() 等
  }
  ```
- **严重程度**: P2
- **现状**: 构造器中 `new` 了三个 LanguageAdapter 和三个 SemanticExtractor，绕过了 NopIoC 依赖注入。
- **风险**: 新增语言适配器时必须修改此构造器。service 模块直接耦合 lang-* 和 graph 的具体实现类。
- **建议**: 通过 NopIoC beans.xml 注入 LanguageAdapter 列表和 SemanticExtractor 列表。
- **信心水平**: 确定
- **误报排除**: 绕过 IoC 容器的硬编码实例化是结构性问题。
- **复核状态**: 未复核

### [维度02-05] NopCodeIndexBizModel 承担过多 API 端点

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`
- **证据片段**:
  ```java
  @BizModel("NopCodeIndex")
  public class NopCodeIndexBizModel extends CrudBizModel<NopCodeIndex> {
      // 暴露约 30 个方法：CRUD + 社区检测 + 图分析 + 流检测 + 依赖图...
  }
  ```
- **严重程度**: P3
- **现状**: 作为 NopCodeIndex 实体的 CRUD BizModel，暴露了几乎所有 ICodeIndexService 方法（约 30 个），包括与 Index 实体 CRUD 无关的图分析、流检测等功能。
- **风险**: 按平台约定每个 BizModel 应聚焦对应实体的业务逻辑。
- **建议**: 将图分析、流分析等功能拆分到独立的 BizModel（如 CodeAnalysisBizModel）。
- **信心水平**: 确定
- **误报排除**: 30 个方法中大部分与 Index 实体 CRUD 无关。
- **复核状态**: 未复核

## 零发现区域

- dao 模块职责纯净：11 个实体 stub + 11 个 IBiz 契约 + 1 个 Constants
- meta 模块职责纯净：0 个 Java 文件
- web/app/codegen 模块职责纯净
- _ 前缀生成文件无手写痕迹
- lang-* 模块职责边界清晰
- graph 模块包划分合理

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 02-01 | P2 | CodeIndexService.java (1573行) | God Class：混合四大职责、74处ORM操作 |
| 02-02 | P2 | ICodeIndexService.java (29方法) | God Interface：横跨10个功能域 |
| 02-03 | P2 | core/resolver/ | 语言特定 ImportResolver 误放 core 模块 |
| 02-04 | P2 | CodeIndexService.java:165-205 | 硬编码跨模块具体类实例化，绕过 IoC |
| 02-05 | P3 | NopCodeIndexBizModel.java | 承担过多 API 端点（约30个方法） |
