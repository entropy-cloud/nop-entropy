# 维度02：模块职责与文件边界

**模块**: nop-code
**审计日期**: 2026-05-31

---

## 第 1 轮（初审）

### [维度02-01] entityToCodeSymbol 方法在三处重复实现

- **文件**: `CodeIndexService.java:209-238`, `CodeQueryService.java:35-64`, `CodeGraphService.java:304-333`
- **证据片段**:
  ```java
  private CodeSymbol entityToCodeSymbol(NopCodeSymbol entity) {
      CodeSymbol symbol = new CodeSymbol();
      symbol.setId(entity.getId());
      symbol.setName(entity.getName());
      symbol.setKind(entity.getKind() != null ? CodeSymbolKind.valueOf(entity.getKind()) : null);
      // ... 25+ 行完全一致的属性映射
  }
  ```
- **严重程度**: P2
- **现状**: 三处完全相同的 entity→model 转换逻辑。entityToInheritance 也有类似重复（2 处）。
- **风险**: NopCodeSymbol 字段变更需同步三处，遗漏导致行为不一致。
- **建议**: 提取到 `CodeEntityConverter` 工具类。
- **信心水平**: 95%
- **误报排除**: 三份代码逐行对比完全一致。
- **复核状态**: 未复核

---

### [维度02-02] CodeIndexService 1569行承担过多职责

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **证据片段**: 文件包含 7+ 功能区块，`saveFileResultInSession` 单方法 260 行。
- **严重程度**: P2
- **现状**: 已拆分出 3 个子服务，但自身仍保留 ORM 持久化、增量索引、Flow 分析等多种职责。
- **风险**: 维护成本高。
- **建议**: 拆分为 CodePersistenceService、CodeFlowService 等。
- **信心水平**: 90%
- **误报排除**: 单方法 260 行是可量化的结构性问题。
- **复核状态**: 未复核

---

### [维度02-03] CodeIndexService 构造函数硬编码 9 个具体实现类

- **文件**: `CodeIndexService.java:165-204`
- **证据片段**:
  ```java
  this.registry.registerAdapter(new JavaLanguageAdapter());
  this.registry.registerAdapter(new PythonLanguageAdapter());
  this.registry.registerAdapter(new TypeScriptLanguageAdapter());
  ```
- **严重程度**: P2
- **现状**: 硬编码所有语言适配器、语义提取器、import 解析器。违反 LanguageAdapterRegistry 的 SPI 设计意图。
- **风险**: 新增语言须改 CodeIndexService。
- **建议**: 使用 IoC 注入或 ServiceLoader 发现机制。
- **信心水平**: 90%
- **误报排除**: LanguageAdapterRegistry 的存在证明设计意图是插件化。
- **复核状态**: 未复核

---

### [维度02-04] ICodeIndexService 接口大量返回 core model 而非 DTO

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java:27-174`
- **证据片段**:
  ```java
  CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode);
  List<CodeSymbol> getFileSymbols(String indexId, String filePath);
  List<ExecutionFlow> detectFlows(String indexId);
  ```
- **严重程度**: P2
- **现状**: API 接口直接暴露 core 层模型类型。核心模型变更即 API 变更。
- **风险**: API 契约不稳定。
- **建议**: 对高频方法包装 DTO 返回。
- **信心水平**: 85%
- **误报排除**: ICodeIndexService 是公共 API 接口，直接暴露 core model 的风险是结构性的。
- **复核状态**: 未复核

---

### [维度02-05] CommunityDetector 内嵌 3 个公有内部类 270 行 boilerplate

- **文件**: `nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:21-892`
- **证据片段**: 约 270 行（30%）是内部类的 getter/setter。
- **严重程度**: P3
- **现状**: 3 个公有静态内部类占 30% 纯 boilerplate。
- **建议**: 提取为独立顶层类文件。
- **信心水平**: 95%
- **误报排除**: 可量化（270/892=30%）。
- **复核状态**: 未复核
