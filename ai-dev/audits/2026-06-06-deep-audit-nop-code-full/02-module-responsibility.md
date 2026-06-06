# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] CodeIndexService.java 是 God Class（1954 行，承担 7+ 项职责）

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **证据片段**: 文件包含索引管理、ORM 持久化（约 350 行逐字段构造 entity）、查询委托（20+ 透传方法）、图分析委托、安全路径校验、缓存管理、内部类 MappedPathResource 等。
- **严重程度**: P2
- **现状**: 单个文件承担 7+ 项职责，直接导入 11 个 DAO entity 类并逐字段手工构造 ORM entity。`saveFileResultInSession`（1058-1367 行）和 `persistInSession`（752-813 行）之间有重复的 semantic edge 持久化代码。
- **风险**: 修改任何一项职责可能影响其他功能；无法独立测试；新人难以理解。
- **建议**: (1) ORM entity 构造逻辑提取为 EntityMapper 工具类 (2) ICodeIndexService 拆分为 2-3 个聚焦接口 (3) 消除重复的 semantic edge 持久化代码。
- **信心水平**: 确定
- **误报排除**: 1954 行的手写文件远超合理阈值（500 行），且职责耦合是结构性的（ORM 构造与业务逻辑混合）。
- **复核状态**: 未复核

### [维度02-02] CodeIndexService 硬编码具体语言实现类，违反开闭原则

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:49-51, 75-79`
- **证据片段**:
  ```java
  import io.nop.code.lang.java.JavaImportResolver;
  import io.nop.code.lang.python.PythonImportResolver;
  import io.nop.code.lang.typescript.TypeScriptImportResolver;
  ```
  构造函数中硬编码 `new JavaImportResolver()` 等。
- **严重程度**: P2
- **现状**: ImportResolver 的注册在 CodeIndexService 构造函数中硬编码完成，而非通过 IoC 自动发现。虽然存在 `LanguageAdapterRegistry` 和 `IImportResolver` 接口抽象。
- **风险**: 每新增语言分析器都需改动 CodeIndexService。
- **建议**: 通过 `@Inject` + IoC 自动收集 `IImportResolver` 实现（与 ILanguageAdapter 的注册方式一致）。
- **信心水平**: 确定
- **误报排除**: 这不是"看起来不优雅"的问题。模块已有 IoC 自动发现机制（LanguageAdapterRegistry），但 ImportResolver 没有使用它。
- **复核状态**: 未复核

### [维度02-03] _NopCodeDaoConstants.java 生成文件被手工编辑

- **文件**: `nop-code-dao/src/main/java/io/nop/code/dao/_NopCodeDaoConstants.java`（286 行）
- **严重程度**: P2
- **现状**: 文件以 `_` 前缀标识为生成文件，但 git 历史显示 5 次手工提交（字典修复、P3 修复等）。文件内容为手工编写的字典常量，本应从 `model/*.orm.xml` 中的 dict 定义自动生成。
- **风险**: codegen 重新运行时可能覆盖手工内容，或 model 与 dao 常量不一致。
- **建议**: 将字典定义补全到 `model/nop-code.orm.xml` 中的 `<dict>` 节点，确保 codegen 模板能自动生成。
- **信心水平**: 确定
- **误报排除**: `_` 前缀是 AGENTS.md 中明确的生成文件标识，手工编辑是硬性规则违反。
- **复核状态**: 未复核

### [维度02-04] _app.orm.xml 和 _service.beans.xml 生成文件被手工编辑

- **文件**:
  - `nop-code-dao/src/main/resources/_vfs/nop/code/orm/_app.orm.xml`
  - `nop-code-service/src/main/resources/_vfs/nop/code/beans/_service.beans.xml`
- **严重程度**: P3
- **现状**: 两个 `_` 前缀文件有 3 次手工提交记录（"P3收口"、"ORM字典修复"等）。
- **风险**: codegen 重新运行可能覆盖修改。
- **建议**: 将需要保留的修改通过 Delta 机制或修改源模型实现。
- **信心水平**: 确定
- **误报排除**: 同维度02-03，`_` 前缀文件不允许手工修改。
- **复核状态**: 未复核

### [维度02-05] ProjectAnalyzer.java 包含大段重复代码

- **文件**: `nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java`
- **严重程度**: P3
- **现状**: 两个几乎相同的 `analyzeProject` 方法（行 124-202 和 204-285），唯一区别是后者多了 `onBatchComplete` 调用。3 个 `analyzeIncremental` 重载变体也有结构重复。
- **风险**: 修改核心分析流程需同步多处。
- **建议**: 用 nullable `BatchCallback` 参数统一两个 `analyzeProject` 重载。
- **信心水平**: 确定
- **误报排除**: 代码重复是可量化的维护成本（同一逻辑存在 2 份）。
- **复核状态**: 未复核

### [维度02-06] JavaFileAnalyzer 内嵌 Spring 路由提取逻辑

- **文件**: `nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:835-924`
- **严重程度**: P3
- **现状**: 约 90 行 Spring MVC 路由提取逻辑硬编码在语言分析器中。
- **风险**: 扩展其他框架需改动核心分析器。
- **建议**: 定义 `IRouteExtractor` 接口，将 Spring 路由提取逻辑提取为独立实现。
- **信心水平**: 很可能
- **误报排除**: 语言分析器与特定 Web 框架的耦合是真实的设计问题。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度02-01] | P2 | CodeIndexService.java | God Class（1954 行，7+ 职责） |
| [维度02-02] | P2 | CodeIndexService.java | 硬编码语言实现类违反开闭原则 |
| [维度02-03] | P2 | _NopCodeDaoConstants.java | 生成文件被手工编辑 |
| [维度02-04] | P3 | _app.orm.xml, _service.beans.xml | 生成文件被手工编辑 |
| [维度02-05] | P3 | ProjectAnalyzer.java | 重复代码 |
| [维度02-06] | P3 | JavaFileAnalyzer.java | Spring 路由逻辑内嵌 |
