# nop-code 架构基线

**日期**：2026-05-02（更新于 2026-06-07）
**范围**：`nop-code` 模块
**状态**：active

---

## 一、设计结论

1. **nop-code-core 只放模型和接口**：通用代码模型、图数据结构、分析接口定义、增量检测、import 解析、语言适配器注册
2. **nop-code-graph 放图算法**：社区检测、入口点评分、影响分析、Hub/Bridge 检测、图导出、图对比
3. **nop-code-flow 放流级分析**：执行流追踪、风险评分变更分析、死代码检测
4. **语言无关的通用代码模型**：SymbolKind / AccessModifier / RelationType 等枚举设计覆盖 Java / Python / TypeScript
5. **算法与语言解耦**：所有分析算法基于 CallGraph / SymbolTable 抽象接口，不依赖任何语言特定的 AST 类型

## 二、模块划分

```
nop-code/
  nop-code-core/                    ← 通用代码模型 + 接口 + 图数据结构 + 增量检测
    model/                          ← 数据模型（CodeSymbol, CodeMethodCall, ...）
    graph/                          ← CallGraph, SymbolTable（纯数据结构）
    analyzer/                       ← 分析接口定义（ICommunityDetector, IFlowDetector, ...）
    incremental/                    ← IncrementalDetector, FingerprintStore
    resolver/                       ← Import 解析器（Java/Python/TypeScript）
    adapter/                        ← LanguageAdapterRegistry

  nop-code-graph/                   ← 图算法（依赖 core）
    CommunityDetector               ← Leiden + LabelPropagation
    EntryPointScorer                ← 入口点/God Node 识别
    ImpactAnalyzer                  ← BFS 影响范围分析
    HubBridgeAnalyzer               ← 度中心性 + 介数中心性
    KnowledgeGapAnalyzer            ← 孤立节点、薄弱社区、未测试热点
    GraphExporter                   ← GraphML / Mermaid / JSON 导出
    GraphDiffer                     ← 图快照对比

  nop-code-flow/                    ← 流级分析（依赖 core + graph）
    FlowDetector                    ← 执行流追踪 + 入口点模式匹配
    ChangeAnalyzer                  ← 风险评分变更分析
    DeadCodeDetector                ← 死代码检测

  nop-code-lang-java/               ← Java 语言适配器（依赖 core）
  nop-code-lang-python/             ← Python 语言适配器（依赖 core）
  nop-code-lang-typescript/         ← TypeScript 语言适配器（依赖 core）
  nop-code-dao/                     ← ORM 持久化
  nop-code-api/                     ← 接口契约
  nop-code-meta/                    ← XMeta + i18n
  nop-code-service/                 ← BizModel 编排（依赖 core + graph + flow + dao + search-api）
  nop-code-web/                     ← 页面视图
  nop-code-app/                     ← 应用启动
  nop-code-codegen/                 ← 代码生成
```

### 模块拆分决策

| 决策 | 选择了什么 | 拒绝了什么 | 理由 |
|------|-----------|-----------|------|
| core 的边界 | 只放模型+接口+数据结构 | 把算法也放 core | core 是所有模块的公共依赖，放入算法会导致 lang-* 和 dao 被迫依赖 JGraphT、Leiden 等算法库 |
| graph 独立模块 | 所有图算法放 graph | 每个算法独立模块 | graph 算法间有强关联（ImpactAnalyzer 依赖 EntryPointScorer 结果），且共享 JGraphT 依赖，拆太细增加依赖管理复杂度 |
| flow 独立模块 | 执行流+变更分析+死代码放 flow | 放 graph 或放 core | flow 分析依赖 graph 算法输出（社区、入口点评分），形成明确的 core←graph←flow 依赖层级 |
| export 放 graph | GraphExporter/GraphDiffer 归 graph | 独立 nop-code-export 模块 | 导出功能体量小（~400 行），且直接操作 CallGraph/SymbolTable，与图算法同依赖 |

### 依赖关系

```
nop-code-core          ← 模型+接口+图数据结构+增量检测（依赖 jgrapht-core 用于图数据结构）
    ↑
nop-code-graph         ← 依赖 core + 算法库（networkanalysis/Leiden, jgrapht 算法）
    ↑
nop-code-flow          ← 依赖 core + graph（用社区/入口点/影响分析的输出）

nop-code-lang-java     ← 依赖 core + javaparser
nop-code-lang-python   ← 依赖 core + tree-sitter-python
nop-code-lang-typescript ← 依赖 core + tree-sitter-typescript

nop-code-dao           ← 依赖 nop-orm
nop-code-meta          ← 依赖 dao
nop-code-service       ← 依赖 core + graph + flow + dao + meta + nop-search-api
nop-code-web           ← 依赖 service + meta
nop-code-app           ← 依赖 web
```

依赖方向严格单向：flow → graph → core，lang-* → core，service → all。

## 三、通用代码模型

### 3.1 CodeSymbolKind（符号类型枚举）

设计原则：每种语言的分析器只使用自己需要的 Kind，忽略其他的。`METHOD` vs `FUNCTION`：Java 没有顶层函数用 METHOD；Python/TS 顶层函数用 FUNCTION，类方法用 METHOD。GraphQL 查询时通过 `kind` 过滤。

| 类别 | Kind | 值 | 覆盖语言 |
|------|------|---|---------|
| 通用 | CLASS | 10 | Java/Python/TS |
| 通用 | INTERFACE | 20 | Java/TS |
| 通用 | ENUM | 30 | Java/TS/Python |
| 通用 | METHOD | 50 | Java/Python/TS |
| 通用 | CONSTRUCTOR | 60 | Java/Python/TS |
| 通用 | FIELD | 70 | Java/Python/TS |
| 通用 | FUNCTION | 55 | Python/TS（顶层函数） |
| 通用 | CONSTANT | 80 | Java/TS/Python |
| 通用 | NAMESPACE | 90 | Java/Python/TS |
| Java 特有 | ANNOTATION_TYPE | 40 | Java |
| TS 特有 | TYPE_ALIAS | 45 | TypeScript |
| TS 特有 | MIXIN | 46 | TypeScript |
| Python 特有 | DECORATOR | 47 | Python |
| 通用辅助 | PARAMETER | 95 | 所有 |
| 通用辅助 | LOCAL_VARIABLE | 96 | 所有 |
| 通用辅助 | TYPE_PARAMETER | 97 | Java/TS |
| 通用辅助 | IMPORT | 98 | 所有 |

### 3.2 CodeAccessModifier（访问修饰符）

| 修饰符 | 值 | Java | Python | TypeScript |
|--------|---|------|--------|------------|
| PUBLIC | 10 | `public` | - | `public` |
| PROTECTED | 20 | `protected` | `_name` | `protected` |
| PRIVATE | 30 | `private` | `__name` | `private` |
| PACKAGE_PRIVATE | 40 | _(default)_ | - | - |
| INTERNAL | 41 | - | - | `internal` |
| NO_MODIFIER | 50 | - | 默认 | 默认 |

### 3.3 CodeSymbol（通用符号模型）

替代 `nop-java-parser` 中的 `SymbolInfo`，标记为 `@DataBean`。语言特有扩展通过 `extData`（JSON）字段承载。

核心字段分组：

| 分组 | 字段 | 说明 |
|------|------|------|
| 标识 | id, kind, name, qualifiedName | 唯一标识 |
| 访问 | accessModifier, deprecated, documentation | 可见性和文档 |
| 位置 | line, column, endLine, endColumn | 源码定位 |
| 层级 | parentId, declaringSymbolId | 嵌套类/内部类 |
| 类型 | superClassName, abstractFlag, finalFlag | CLASS/INTERFACE/ENUM 相关 |
| 方法 | signature, returnType, staticFlag, asyncFlag | METHOD/FUNCTION 相关 |
| 字段 | fieldType, readonlyFlag | FIELD 相关 |
| 扩展 | extData | JSON：Java(synchronized/native/...)、Python(classmethod/...)、TS(arrow/...) |

### 3.4 CodeLanguage（语言枚举）

| 语言 | 代码 | 扩展名 |
|------|------|--------|
| JAVA | "java" | .java |
| PYTHON | "python" | .py |
| TYPESCRIPT | "typescript" | .ts, .tsx |
| JAVASCRIPT | "javascript" | .js, .jsx |

### 3.5 CodeFileAnalysisResult（通用文件分析结果）

标记为 `@DataBean`，包含：filePath / sourceCode / lineCount / language / packageName / imports / symbols / calls / inheritances / annotationUsages。

## 四、核心接口

### 4.1 ICodeFileAnalyzer（文件分析器接口）

每种语言提供一个实现。

| 方法 | 签名 | 说明 |
|------|------|------|
| getLanguage | `() → CodeLanguage` | 支持的语言 |
| analyze | `(filePath, sourceCode) → CodeFileAnalysisResult?` | 分析单个文件，null 表示无法解析 |
| getFileExtensions | `() → List<String>` | 支持的扩展名（如 Java → [".java"], TS → [".ts", ".tsx"]） |

### 4.2 ILanguageAdapter（语言适配器接口）

注册到 `LanguageAdapterRegistry`，提供该语言的分析器和文件匹配规则。

| 方法 | 签名 | 说明 |
|------|------|------|
| getLanguage | `() → CodeLanguage` | |
| getFileAnalyzer | `() → ICodeFileAnalyzer` | |
| getFileExtensions | `() → List<String>` | |
| getExcludePatterns | `() → List<String>` | 排除目录（如 Python → ["__pycache__/", ".venv/"]） |

### 4.3 IProjectAnalyzer（项目分析器接口）

扫描项目目录，自动识别语言，调度对应分析器。

| 方法 | 签名 | 说明 |
|------|------|------|
| analyzeProject | `(projectRoot) → ProjectAnalysisResult` | 自动检测语言 |
| analyzeProject | `(projectRoot, languages) → ProjectAnalysisResult` | 指定语言 |
| analyzeIncremental | `(projectRoot, changedFilePaths) → ProjectAnalysisResult` | 增量分析 |

### 4.4 分析算法接口

| 接口 | 方法 | 说明 |
|------|------|------|
| ICommunityDetector | `detect(CallGraph, SymbolTable, CommunityConfig) → CommunityDetectionResult` | 社区检测（Leiden + LabelPropagation） |
| IEntryPointScorer | `score(CallGraph, SymbolTable) → List<EntryPointScore>` | 入口点/God Node 识别 |
| IImpactAnalyzer | `analyze(targetQualifiedName, CallGraph, SymbolTable, maxDepth) → ImpactResult` | BFS 影响范围分析 |

> 注：接口签名为目标架构。`IEntryPointScorer` 和 `IImpactAnalyzer` 的目标签名包含 `reverseCallGraph` 参数，当前实现暂无。

### 4.5 流级分析接口

定义在 `nop-code-core`，不引入新模块。

```
// 执行流追踪
IFlowDetector.detect(SymbolTable, CallGraph) → List<ExecutionFlow>
IEntryPointPatternProvider.getPatterns() → List<EntryPointPattern>  // 框架模式注册

// 风险评分变更分析
IChangeAnalyzer.analyze(indexId, baseCommitish, targetCommitish) → ChangeAnalysisResult

// 死代码检测
IDeadCodeDetector.detect(CallGraph, SymbolTable, config) → DeadCodeReport

// 图导出
IGraphExporter.export(CallGraph, SymbolTable, format) → String

// 图快照对比
GraphDiffer.diff(GraphSnapshot, GraphSnapshot) → GraphDiff
```

## 五、边类型

当前 4 种边类型（calls / inheritances / annotationUsages / fileDependencies），补充 3 种：

| 边类型 | 存储方式 | 用途 |
|--------|---------|------|
| **CONTAINS** | `CodeSymbol.parentId`（已有字段） | 父子关系（类→方法/字段） |
| **TESTED_BY** | `nop_code_usage`（`kind=TESTED_BY`） | 测试关联，覆盖率分析 |
| **REFERENCES** | `nop_code_usage`（`kind=REFERENCES`） | 通用引用，增强影响分析 |

**决策**：不新增独立边表。TESTED_BY 和 REFERENCES 复用 `nop_code_usage.kind` 枚举扩展。

**框架特定边**（如 Spring DI 接口→实现）：通过 `CodeAnnotationUsage` + `IImportResolver` 组合推导，不新增边类型。

> **IMPLEMENTS 为什么不独立为边类型**：接口实现关系已通过 `nop_code_inheritance` 表的 `kind=IMPLEMENTS` 字段区分（`CodeRelationType.IMPLEMENTS`），无需复用 `nop_code_usage` 存储。

## 六、实现状态

### 6.1 模块状态

| 模块 | 状态 |
|------|------|
| `nop-code-core` | ✅ 已实现（通用模型、图数据结构、分析算法） |
| `nop-code-graph` | ✅ 已实现（社区检测 Leiden/LabelPropagation、入口点评分、影响分析、Hub/Bridge、知识缺口、GraphML/Mermaid/JSON 导出、图快照对比） |
| `nop-code-flow` | ✅ 已实现（执行流追踪、风险评分变更分析、死代码检测） |
| `nop-code-lang-java` | ✅ 已实现（JavaParser + SymbolSolver，覆盖 Java 17） |
| `nop-code-lang-python` | ✅ 已实现（tree-sitter-python，符号/继承/装饰器/调用提取） |
| `nop-code-lang-typescript` | ✅ 已实现（tree-sitter-typescript，符号/继承/装饰器提取，暂无调用图） |
| `nop-code-api` | ✅ 已实现（CodeIndexApi 接口 + 5 个通用 API） |
| `nop-code-service` | ✅ 已实现（全部 GraphQL API，nop-search 双路径集成） |

### 6.2 远期待做

| 功能 | 状态 | 说明 |
|------|------|------|
| 语义边 LLM 集成 | ⏳ 远期 | 依赖 nop-ai 模块 |
| nop-search 全功能集成 | ⏳ 远期 | 双路径已实现，向量嵌入和混合搜索待部署时注入 |
| 框架感知解析 | ⏳ 远期 | 入口点模式检测已覆盖主要框架场景 |
| 增量更新依赖传播 | ⏳ 远期 | 2-hop 传播的额外收益需实际场景验证 |

## 七、与已有设计的关系

| 主题 | 文档 |
|------|------|
| 设计原则、non-goals、约束、不变量 | `00-vision.md` |
| GraphQL 查询 API | `query-api-design.md` |
| nop-search 集成 | `search-integration-design.md` |
| 图分析增强 | `graph-analysis-design.md` |
| 流级分析 | `flow-analysis-design.md` |
| 语义边 | `semantic-edge-design.md` |
