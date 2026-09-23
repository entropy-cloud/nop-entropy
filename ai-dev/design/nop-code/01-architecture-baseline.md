# nop-code 架构基线

**日期**：2026-05-02（更新于 2026-09-23）
**范围**：`nop-code` 模块
**状态**：active

---

## 〇、模块边界校正（2026-09-23）

> 本节校正本基线此前的过期描述，以下为**当前代码的真实边界**，是阅读后续章节的前提。

- **通用图算法在顶层共享模块 `nop-graph/`，不在 `nop-code` 内**：`nop-graph-api`（`IGraph` / `Edge` / `CommunityResult` / `CriticalNodeResult` 等结果类型）+ `nop-graph-core`（`LeidenDetector` / `LabelPropagation` / `BetweennessCentrality` / `Bfs` / `PageRank` / `TarjanSCC` / `ImpactPropagator` / `PathQueryExecutor` / `GraphExporter` / `GraphDiffer` / `TopologicalSort`）。当前消费者是 `nop-code`（core/service），**未来可供** `nop-wf`/`nop-task`/`nop-stream` 复用。**`nop-code` 子模块清单中没有 `nop-code-graph`**（已迁移）。
- **图存储抽象是 `IGraph`，不是 `ICallGraph`/`ISymbolTable`**：`io.nop.graph.api.IGraph`（`getOutEdges`/`getInEdges`）即"屏蔽底层存储形式"的接口，其 javadoc 明确"所有实现返回同一类型，调用者无需感知底层是 InMemory、Neo4j 还是 ORM"。`CallGraph` / `SymbolTable`（`nop-code-core/.../core/graph/`）是**内存具体数据结构**；`CodeCallGraph implements IGraph` 是适配器（当前仅投影 CALLS 边）。
- **nop-code 专属分析器分两处**：`nop-code-core/.../entrypoint/EntryPointScorer`（入口点评分）与 `nop-code-service/.../service/graph/`（`KnowledgeGapAnalyzer`、`NameSimilarityExtractor`/`DocKeywordExtractor`/`AnnotationPatternExtractor`、`InterfaceImplSynthesizer`/`SpringEventSynthesizer`）。
- **`nop-code-flow` 独立模块存在**：`FlowDetector` / `ChangeAnalyzer` / `DeadCodeDetector` 在其中，只依赖 `nop-code-core`（不依赖 `nop-graph`）。

> 保留此校正节的理由：本基线 2026-05-02 初版以 `nop-code-graph` 为模块，该模块后续被迁移为顶层 `nop-graph`；若不校正，读者会按不存在的模块名实施。

## 一、设计结论

1. **nop-code-core 只放模型和数据结构**：通用代码模型、`CallGraph`/`SymbolTable` 内存数据结构、增量检测、import 解析、语言适配器注册
2. **通用图算法在顶层 `nop-graph/`**：社区检测（Leiden/LabelPropagation）、影响传播、Hub/Bridge、BFS、PageRank、TarjanSCC、图导出、图对比。nop-code 专属的入口点评分与知识缺口分析保留在 nop-code（core/service）
3. **nop-code-flow 放流级分析**：执行流追踪、风险评分变更分析、死代码检测（只依赖 core）
4. **语言无关的通用代码模型**：SymbolKind / AccessModifier / RelationType 等枚举设计覆盖 Java / Python / TypeScript
5. **算法基于 `IGraph` 抽象**：所有图算法基于 `io.nop.graph.api.IGraph`（+ `Edge`），不依赖具体存储实现，也不依赖任何语言特定的 AST 类型

## 二、模块划分

```
nop-code/
  nop-code-core/                    ← 通用代码模型 + 内存图数据结构 + 增量检测 + 入口点评分
    core/model/                     ← 数据模型（CodeSymbol, CodeMethodCall, CodeSemanticEdge, ...）
    core/graph/                     ← CallGraph, SymbolTable（内存具体结构）+ CodeCallGraph（IGraph 适配器）
    core/entrypoint/                ← EntryPointScorer
    core/incremental/               ← IncrementalDetector, FingerprintStore
    core/resolver/                  ← Import 解析器（Java/Python/TypeScript）
    core/adapter/                   ← LanguageAdapterRegistry
    core/semantic/                  ← 语义边模型与提取器接口

  nop-code-flow/                    ← 流级分析（只依赖 core）
    FlowDetector                    ← 执行流追踪 + 入口点模式匹配
    ChangeAnalyzer                  ← 风险评分变更分析
    DeadCodeDetector                ← 死代码检测

  nop-code-service/                 ← BizModel + 服务编排 + 专属图分析器
    service/impl/CodeGraphService   ← 调用 nop-graph 算法
    service/graph/                  ← KnowledgeGapAnalyzer + 语义边提取器/合成器
    service/...BizModel             ← GraphQL API 暴露

  nop-code-lang-java/               ← Java 语言适配器（依赖 core）
  nop-code-lang-python/             ← Python 语言适配器（依赖 core）
  nop-code-lang-typescript/         ← TypeScript 语言适配器（依赖 core）
  nop-code-dao/                     ← ORM 持久化
  nop-code-api/                     ← DTO 与生成的 CRUD API
  nop-code-meta/                    ← XMeta + ORM 模型 + i18n + dict
  nop-code-web/                     ← 页面视图
  nop-code-app/                     ← 应用启动
  nop-code-codegen/                 ← 代码生成

nop-graph/                          ← 顶层共享图算法库（不属 nop-code）
  nop-graph-api/                    ← IGraph + Edge + 结果类型（复合类型：CommunityResult/CriticalNodeResult/...）
  nop-graph-core/                   ← 算法实现（Leiden/LabelPropagation/Betweenness/Bfs/PageRank/TarjanSCC/Impact/Exporter/Differ）
```

### 模块拆分决策

| 决策 | 选择了什么 | 拒绝了什么 | 理由 |
|------|-----------|-----------|------|
| 通用图算法独立为顶层 `nop-graph` | 算法与结果类型放 `nop-graph-api/core` | 放在 nop-code 内（原 `nop-code-graph`） | 图算法设计为跨模块复用（当前 `nop-code` 使用，未来 `nop-wf`/`nop-task`/`nop-stream` 可用）；放 nop-code 内会造成跨模块反向依赖。**已实施迁移** |
| core 的边界 | 只放模型+内存数据结构+适配器 | 把通用算法也放 core | core 是所有模块的公共依赖；`CallGraph`/`SymbolTable` 是内存结构，算法通过 `IGraph` 适配器读取 |
| flow 独立模块 | 执行流+变更分析+死代码放 flow | 放 core | flow 分析自成体系，只依赖 core；当前不依赖 `nop-graph`（入口点模式内聚在 FlowDetector） |
| nop-code 专属分析器留在 service | KnowledgeGapAnalyzer、语义提取器放 `nop-code-service/.../service/graph/` | 放 `nop-graph`（通用库不应含代码领域概念） | 这些分析器依赖 `CodeSymbol`/语义边等代码领域模型，属 nop-code 专属 |
| export 用 `nop-graph` 静态工具 | `GraphExporter`/`GraphDiffer` 在 `nop-graph-core` | 独立模块 | 体量小，且已被 nop-code 复用 |

### 依赖关系

```
nop-graph-api          ← IGraph + Edge + 结果类型（零算法依赖）
    ↑
nop-graph-core         ← 算法实现（依赖 api + JGraphT + CWTS Leiden）

nop-code-core          ← 模型 + 内存图结构 + 增量检测 + 入口点评分（依赖 nop-graph-api）
    ↑
nop-code-flow          ← 只依赖 nop-code-core

nop-code-lang-java     ← 依赖 core + javaparser
nop-code-lang-python   ← 依赖 core + tree-sitter-python
nop-code-lang-typescript ← 依赖 core + tree-sitter-typescript

nop-code-dao           ← 依赖 nop-orm
nop-code-meta          ← 依赖 dao
nop-code-service       ← 依赖 core + flow + dao + meta + nop-graph-core + nop-search-api
nop-code-web           ← 依赖 service + meta
nop-code-app           ← 依赖 web
```

依赖方向严格单向：lang-* → core，flow → core，service → {core, flow, nop-graph-core}。**`nop-code` 不依赖任何语言特有 AST 类型跨层泄漏；通用算法依赖方向是 nop-code → nop-graph，不可反向**。

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

### 4.4 分析能力入口

> **校正**：不存在 `ICommunityDetector` / `IEntryPointScorer` / `IImpactAnalyzer` 接口。当前通用算法是 `nop-graph-core` 的**具体类/静态方法**，nop-code 专属分析器是 `nop-code-core`/`nop-code-service` 的具体类。若未来需要可替换算法，应通过 `IGraph` 输入契约实现，而非另立接口。

| 能力 | 当前实现位置 | 输入 |
|------|-------------|------|
| 社区检测（Leiden + LabelPropagation） | `nop-graph-core` `LeidenDetector` / `LabelPropagation` | `IGraph` + 节点集 |
| 介数中心性（Bridge） | `nop-graph-core` `BetweennessCentrality` | `IGraph` |
| BFS | `nop-graph-core` `Bfs` | `IGraph` + start + maxDepth |
| PageRank / TarjanSCC / 影响传播 | `nop-graph-core` `PageRank` / `TarjanSCC` / `ImpactPropagator` | `IGraph` |
| 图导出 / 图对比 | `nop-graph-core` `GraphExporter` / `GraphDiffer`（静态工具） | 节点/边集合 |
| 入口点评分 | `nop-code-core/.../entrypoint/EntryPointScorer` | `CallGraph` + `SymbolTable` |
| 知识缺口 | `nop-code-service/.../service/graph/KnowledgeGapAnalyzer` | `CallGraph` + 社区结果 |

### 4.4.1 图存储抽象：`IGraph`（2026-09-23 校正）

**结论**：**不需要新增 `ICallGraph`/`ISymbolTable`。** "对外提供接口屏蔽后台存储形式"这一目标**已由现有 `nop-graph-api` 的 `IGraph` 满足**。

```
io.nop.graph.api.IGraph          ← 存储抽象接口（已有）
  ├── getOutEdges(nodeId) → List<Edge>
  └── getInEdges(nodeId) → List<Edge>
        Edge { sourceId, targetId, weight, type, attrs }
```

| 层 | 角色 | 当前实现 |
|----|------|---------|
| 存储抽象 | `IGraph`（`nop-graph-api`） | 接口，javadoc 明示"InMemory/Neo4j/ORM 对调用者透明" |
| 内存具体结构 | `CallGraph` + `SymbolTable`（`nop-code-core`） | 默认简易方案（`HashMap` + BFS） |
| 适配器 | `CodeCallGraph implements IGraph`（`nop-code-core`） | 将 `CallGraph` 投影为 `List<Edge>` |
| 未来数据库实现 | 另一个 `IGraph` 实现（如 `OrmCallGraph`） | 待实现 |

**设计决策**：
- **复用 `IGraph`，不新造接口**。用户目标"内存 CallGraph 作为简易方案、对外接口屏蔽存储形式"已经落地：`CallGraph` 是内存实现，`IGraph` 是抽象边界，切换后端只需换 `IGraph` 实现。
- **领域属性通过 `Edge.attrs` 承载**（`IGraph` javadoc 约定）：`type`（CALLS/INHERITANCE/ANNOTATION/SEMANTIC）、confidence、filePath 等放 `attrs`，算法层不解析。当前 `CodeCallGraph` 只投影 CALLS 边且未填 attrs，是**待增强点**（见 `graph-discovery-and-export-design.md` 前置条件）。
- **`IGraph` 只保证局部遍历**：`getOutEdges`/`getInEdges` 适合深度受限的局部查询下推到数据库。**全局算法（Leiden、介数中心性、PageRank、TarjanSCC）必须整体物化**，无法由递归 CTE 逐点求得——生产后端下这些全局结果应在**索引构建期计算并持久化**（见 §6.2 待做），查询期只读。
- **数据库后端选型未定**：`ltree` / 递归 CTE / Apache AGE 仍是开放决策；参考应用当前是 MySQL/H2，硬绑定 Postgres 扩展会造成可移植性回退。此点登记为待决策项，不在本基线预设结论。

### 4.5 流级分析

**校正**：流级分析接口位于 `nop-code-flow`（`io.nop.code.flow`），**不是** `nop-code-core`；`nop-code-flow` 模块存在且已实现。

```
// 执行流追踪（nop-code-flow）
IFlowDetector.detect(SymbolTable, CallGraph) → List<ExecutionFlow>
IEntryPointPatternProvider.getPatterns() → List<EntryPointPattern>  // 框架模式注册（可插拔）

// 风险评分变更分析（nop-code-flow）
IChangeAnalyzer.analyze(indexId, baseCommitish, targetCommitish) → ChangeAnalysisResult

// 死代码检测（nop-code-flow）
IDeadCodeDetector.detect(CallGraph, SymbolTable, config) → DeadCodeReport

// 图导出 / 图快照对比（nop-graph-core，静态工具）
GraphExporter.export(...) / GraphDiffer.diff(...)
```

> `IEntryPointPatternProvider` 是可插拔框架模式的**现有 SPI**（非 DSL）；当前内置 Spring provider。核心不硬编码框架的目标应通过把内置 provider 移出 `nop-code-core` 实现（见 `00-vision.md` 约束 9）。

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
| `nop-graph`（顶层，api+core） | ✅ 已实现（`IGraph`/`Edge`/结果类型；Leiden/LabelPropagation/Betweenness/Bfs/PageRank/TarjanSCC/Impact/Exporter/Differ） |
| `nop-code-core` | ✅ 已实现（通用模型、`CallGraph`/`SymbolTable` 内存结构、`CodeCallGraph` 适配器、`EntryPointScorer`、增量检测） |
| `nop-code-flow` | ✅ 已实现（执行流追踪、风险评分变更分析、死代码检测） |
| `nop-code-lang-java` | ✅ 已实现（JavaParser + SymbolSolver，覆盖 Java 17；含 Spring 路由提取`[legacy，待迁出核心]`） |
| `nop-code-lang-python` | ✅ 已实现（tree-sitter-python，符号/继承/装饰器/调用提取） |
| `nop-code-lang-typescript` | ✅ 已实现（tree-sitter-typescript，符号/继承/装饰器提取，**暂无调用图**） |
| `nop-code-api` | ✅ 已实现（生成的 per-entity CRUD API + DTO；服务接口为 `ICodeIndexService`） |
| `nop-code-meta` | ✅ 已实现（xmeta 全套 + ORM 模型 + dict + i18n） |
| `nop-code-service` | ✅ 已实现（全部 GraphQL API；`CodeGraphService` 调用 nop-graph；nop-search 双路径：可注入 `ISearchEngine`，默认无引擎时降级 DB LIKE） |
| 语义边（确定性） | ✅ 已实现（`CodeSemanticEdge` + `ISemanticEdgeExtractor` + 3 提取器 + ORM 表 + BizModel） |
| 启发式调用边合成 | ✅ 已实现（`InterfaceImplSynthesizer` / `SpringEventSynthesizer`，产出 INFERRED `CodeMethodCall`，非语义边） |
| 图存储抽象（`IGraph`） | ✅ 已实现（`IGraph` 接口 + `CodeCallGraph` 适配器） |
| 查询路径无状态 | ❌ 未实现（当前用 `CodeCacheManager` 堆内缓存 + 全量 rebuild） |
| 数据库图后端（`IGraph` 的第二实现） | ⏳ 未定（`ltree`/CTE/AGE 选型开放；参考应用为 MySQL/H2） |

### 6.2 待做

| 功能 | 状态 | 说明 |
|------|------|------|
| 全局算法结果持久化 | ⏳ 近期 | 社区/中心性/入口点评分当前每次查询重算；应索引期物化、查询期只读（无状态化前提） |
| 查询路径无状态化 | ⏳ 中期 | 消除 `CodeCacheManager` 全量 rebuild；本地遍历下推 `IGraph` 后端 |
| 数据库图后端 | ⏳ 待决策 | `IGraph` 的第二实现；需先定生产 DB 与可移植性边界 |
| 框架适配迁出核心 | ⏳ 中期 | 把 `JavaFileAnalyzer` 硬编码 Spring 路由改为 `IEntryPointPatternProvider`/适配器；DSL 为远期选项 |
| 语义边 LLM 集成 | ⏳ 远期 | 依赖 nop-ai；当前确定性提取器只产出 EXTRACTED 边 |
| nop-search 向量/混合 | ⏳ 远期 | 双路径已实现，向量嵌入与 RRF 混合搜索待部署时注入 |
| 集群索引构建 | ⏳ 远期 | 需先定义源码分发、分片、原子发布；当前参考应用是单节点 |
| 增量更新依赖传播 | ⏳ 远期 | 2-hop 传播的额外收益需实际场景验证 |

## 七、与已有设计的关系

| 主题 | 文档 |
|------|------|
| 设计原则、non-goals、约束、不变量 | `00-vision.md` |
| GraphQL 查询 API | `query-api-design.md` |
| nop-search 集成 | `search-integration-design.md` |
| 图分析增强 | `graph-analysis-design.md` |
| 图探索与导出增强（意外连接/问题生成/Wiki/自动重建） | `graph-discovery-and-export-design.md` |
| 流级分析 | `flow-analysis-design.md` |
| 语义边 | `semantic-edge-design.md` |
| 顶层图算法库 `nop-graph` | `docs-for-ai/01-repo-map/module-groups.md`（仓库模块分组） |
