# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] CodeIndexService 职责过重（God Class）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: 全文件（1633行）
- **证据片段**:
  ```java
  // Facade/路由 + ORM 持久化(450行) + 索引编排 + 增量索引(110行) + Flow分析(156行) + 路径校验
  ```
- **严重程度**: P2
- **现状**: 同时承担 Facade 路由、ORM 持久化、增量索引、Flow 分析、路径校验等 6 种职责。
- **风险**: 修改一处可能误触另一处；代码审查困难；测试隔离困难。
- **建议**: 持久化提取为 CodePersistenceService，增量索引提取为 CodeIncrementalIndexer。
- **信心水平**: 确定
- **误报排除**: Facade 模式合理但持久化和编排不属于 facade 层。
- **复核状态**: 未复核

### [维度02-02] CodeIndexService 硬编码依赖 lang-* 模块具体类

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:37-39,201-210`
- **证据片段**:
  ```java
  import io.nop.code.lang.java.JavaImportResolver;
  import io.nop.code.lang.python.PythonImportResolver;
  import io.nop.code.lang.typescript.TypeScriptImportResolver;
  ```
- **严重程度**: P2
- **现状**: 直接 import 并 new 了 lang-* 模块具体类，而非通过 IoC 或 ServiceLoader 发现。
- **风险**: 新增语言适配器需修改 CodeIndexService，违反 OCP。
- **建议**: 通过 BeanContainer 自动发现 IImportResolver 实现。
- **信心水平**: 很可能
- **误报排除**: 项目已通过 LanguageAdapterRegistry 做了语言适配器解耦，但 ImportResolver 未遵循。
- **复核状态**: 未复核

### [维度02-03] CodeIndexService 硬编码依赖 graph.semantic 具体类

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:63-65,193-199`
- **证据片段**:
  ```java
  import io.nop.code.graph.semantic.AnnotationPatternExtractor;
  import io.nop.code.graph.semantic.DocKeywordExtractor;
  import io.nop.code.graph.semantic.NameSimilarityExtractor;
  ```
- **严重程度**: P2
- **现状**: 语义边提取器硬编码发现，而非通过 IoC 自动注册。
- **建议**: 通过 BeanContainer 自动发现 ISemanticEdgeExtractor 实现。
- **信心水平**: 很可能
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度02-04] FlowDetector 依赖 graph 模块 EntryPointScorer

- **文件**: `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java:16`
- **证据片段**:
  ```java
  import io.nop.code.graph.entrypoint.EntryPointScorer;
  ```
- **严重程度**: P3
- **现状**: flow 模块直接依赖 graph 模块的具体类。
- **建议**: 抽象为接口放在 core 模块。
- **信心水平**: 很可能
- **误报排除**: 设计权衡。
- **复核状态**: 未复核

### [维度02-05] CodeGraphService 内嵌 Tarjan SCC 算法（功能错放）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:644-694`
- **证据片段**:
  ```java
  private List<List<String>> tarjanSCC(Map<String, List<String>> adj) { ... }
  private void tarjanDFS(...) { ... }
  ```
- **严重程度**: P2
- **现状**: 图算法放在 service 层而非 graph 模块。
- **建议**: 移至 nop-code-graph 的 CycleDetector 类。
- **信心水平**: 确定
- **误报排除**: 图算法明确属于 graph 模块。
- **复核状态**: 未复核

### [维度02-06] CommunityDetector 含 3 个大型内部类（283 行样板代码）

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:30-317`
- **严重程度**: P3
- **现状**: CommunityDetectionResult、Community、CommunityConfig 三个数据类作为内部类，占文件 32%。
- **建议**: 提取为顶层类。
- **信心水平**: 很可能
- **误报排除**: 内部类是合理 Java 模式，但文件过大。
- **复核状态**: 未复核

### [维度02-07] ProjectAnalyzer 三个重载方法后处理逻辑重复 3 次

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:217-233,303-313,379-390`
- **证据片段**:
  ```java
  // 三个入口方法中完全相同的后处理代码
  int[] callCounts = resolveCalls(fileResults, globalSymbolTable);
  ProjectStats stats = buildStats(...);
  runSemanticExtractors(...);
  ```
- **严重程度**: P3
- **现状**: 后处理代码复制粘贴了 3 次。
- **建议**: 提取 postProcess() 方法。
- **信心水平**: 确定
- **误报排除**: 无。
- **复核状态**: 未复核
