# nop-code — Code Indexing & Analysis Module

## 子模块职责

| Submodule | Responsibility |
|-----------|---------------|
| `nop-code-core` | Core models: `CodeSymbol`, `CodeFileAnalysisResult`, `SymbolTable`, `CallGraph`, `EdgeProvenance`. Interfaces: `ICodeFileAnalyzer`, `ILanguageAdapter`, `ISemanticEdgeExtractor`. |
| `nop-code-lang-java` | Java source analyzer using JavaParser + Symbol Solver. Contains hardcoded Spring route extraction (`[legacy]` — to be migrated out of core; the target is pluggable SPI-loaded framework patterns). |
| `nop-code-lang-python` | Python source analyzer. Import resolution via `PythonImportResolver`. |
| `nop-code-lang-typescript` | TypeScript/JavaScript source analyzer. Import resolution via `TypeScriptImportResolver`. |
| `nop-code-lang-go` | Go 语言解析（tree-sitter go blob + GoLanguageAdapter/GoCodeFileAnalyzer：函数/方法/struct/interface/别名/常量符号、嵌入继承、calls、imports） |
| `nop-code-lang-rust` | Rust 语言解析（tree-sitter rust blob + RustScanner + RustLanguageAdapter/RustCodeFileAnalyzer：函数/方法/struct/enum/trait/别名/常量符号、impl IMPLEMENTS 边、trait supertrait EXTENDS 边、calls、use 导入） |
| `nop-code-lang-csharp` | C# 语言解析（tree-sitter c-sharp blob + CSharpScanner + CSharpLanguageAdapter/CSharpCodeFileAnalyzer：类/接口/结构体/record/枚举/方法/属性/字段/常量符号、base_list 按容器分派继承边、calls、using 导入） |
| `nop-code-flow` | Flow detection: execution flows, dead-code detection, change analysis. Interfaces: `IFlowDetector`, `IChangeAnalyzer`, `IDeadCodeDetector`. Contains hardcoded Spring entry-point patterns (`[legacy]` — to be migrated out of core). |
| `nop-code-codegen` | 代码生成层。 |
| `nop-code-dao` | Generated ORM entities and DAO layer (from `nop-code.orm.xml`). |
| `nop-code-service` | Business logic. `CodeIndexService` (implements `ICodeIndexService`): full/incremental indexing, persistence, flow/graph orchestration. |
| `nop-code-web` | Web/API layer. |
| `nop-code-app` | 应用打包层。 |
| `nop-code-api` | API DTOs: `IndexStatsDTO`, `CodeSearchResultDTO`, `DepGraphDTO`, etc. |
| `nop-code-meta` | Metadata: xmeta definitions, ORM model (`nop-code.orm.xml`), i18n resources, dict files. |

> 图分析能力（community detection/Leiden、critical-node scoring、impact analysis、graph diff、dependency cycles）不在本模块，而在**独立的顶层模块 `nop-graph/`**（`nop-graph-api` + `nop-graph-core`），见 `../01-repo-map/module-groups.md`。`nop-code` 的子模块清单中**没有** `nop-code-graph`。`nop-code` 通过 `io.nop.graph.api.IGraph` 抽象消费该库（`CodeCallGraph` 是适配器）。

## 核心 API 清单

### ICodeIndexService (main service interface)

Located in `nop-code-service`. Key methods:

| Method | Type | Description |
|--------|------|-------------|
| `indexDirectory` | Mutation | Full index of a VFS directory |
| `indexFile` | Mutation | Index a single file |
| `triggerIncrementalIndex` | Mutation | Detect changes and re-index modified files |
| `deleteIndex` | Mutation | Delete an index and all associated entities |
| `getFiles`, `getFile`, `getFileSourceCode` | Query | File-level queries |
| `getFileSymbols`, `getFileTypes`, `getFileOutline` | Query | Symbol-level queries |
| `findSymbols`, `findSymbolsPage`, `getSymbolById` | Query | Symbol search |
| `findReferencedBy`, `getSymbolSourceCode` | Query | Reference & source queries |
| `getTypeHierarchy`, `getCallHierarchy` | Query | Hierarchy traversal |
| `detectCommunities`, `getCriticalNodes` | Query | Graph analysis |
| `getImpactAnalysis`, `exportGraph`, `diffGraph` | Query | Impact & diff |
| `getDeps`, `getReverseDeps`, `findCycles` | Query | Dependency analysis |
| `detectFlows`, `listFlows`, `getFlow` | Query/Mutation | Execution flow |
| `analyzeChanges`, `detectDeadCode` | Query | Change & dead code |
| `searchCode` | Query | Full-text/vector/hybrid search (`searchType`: `TEXT`/`VECTOR`/`HYBRID` via `ISearchEngine` — engine path with Lucene when deployed, default in `nop-code-app`; DB-LIKE fallback otherwise; legacy values normalize to engine TEXT; `VECTOR`/`HYBRID` need an `ITextEmbedding` implementation on the classpath for the vector leg) |

> **访问控制（N6.5）**：`ICodeIndexService` 全部公共入口经 `IndexAccessPolicy` SPI 检查（读/写两类检查点，默认 Permissive 零行为变化）；per-index 本地路径白名单（`getAllowedLocalRoot`）与私有仓库凭据解析（`GitCredentialResolver`，env 通道零泄漏）由部署方注入策略对接租户系统。action-auth 资源树不受影响。

### Key BizModels

- **NopCodeIndexBizModel** (`@BizModel("NopCodeIndex")`) — exposes `ICodeIndexService` as GraphQL/REST via `@BizQuery`/`@BizMutation`. Mutations require the `admin` role (`@Auth(roles = "admin")`); reads are authorized through action-auth resource permissions (`NopCodeIndex:query` etc., see `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/auth/nop-code.action-auth.xml`).
- **NopCodeSymbolBizModel** — symbol-specific CRUD and queries.

## API 暴露策略

**nop-code 通过 GraphQL（BizModel）统一暴露 API，不引入 MCP 协议。**

原因：
1. Nop 平台的 BizModel + `IGraphQLEngine` 已是通用 API 暴露层，天然支持所有 AI 客户端（Claude Code、Cursor、Codex、OpenCode 等）通过 GraphQL 查询消费数据。
2. GraphQL 支持嵌套查询（`@BizLoader`），单次请求可获取符号+源码+引用+层级的完整上下文，对 AI 代理的 token 效率优于 MCP 的扁平工具调用。
3. GraphQL 内置权限控制（`@Auth` 注解），MCP 无认证机制。
4. 新增 MCP Server 会引入不必要的协议适配层和运维复杂度。

AI 客户端通过标准 GraphQL 客户端库或 HTTP POST 直接调用 nop-code 的 GraphQL endpoint 即可。所有 38+ BizModel 方法均可通过 GraphQL 查询访问。参见 `02-core-guides/api-and-graphql.md`。

## 架构原则

nop-code 的目标定位是**集群式代码索引服务**（非单机本地工具）。下表中"现状"列说明哪些已落地、哪些是目标：

| 原则 | 含义 | 现状 |
|------|------|------|
| **存储形式由接口屏蔽** | `io.nop.graph.api.IGraph` 是存储抽象边界；`CallGraph`/`SymbolTable` 是内存结构，`CodeCallGraph` 是适配器。切换后端只需换 `IGraph` 实现 | ✅ 已落地（`IGraph` 在 `nop-graph-api`） |
| **通用图算法下沉** | 社区检测、中心性、BFS、导出等通用算法在顶层 `nop-graph/`；nop-code 通过 `IGraph` 消费 | ✅ 已落地 |
| **查询路径无状态** | 目标：查询不在 JVM 内存中全量重建图；全局算法结果索引期物化、查询期只读 | ⏳ 目标（当前用 `CodeCacheManager` 堆内缓存 + 全量 rebuild） |
| **利用数据库图能力** | 目标：评估 PostgreSQL 图索引（`ltree`/递归 CTE）或 Apache AGE；需先定生产 DB 与可移植性（参考应用当前 MySQL/H2）。全局算法无法由递归 CTE 求得 | ⏳ 待决策 |
| **框架适配不入核心** | 目标：框架模式经 SPI（`IEntryPointPatternProvider`）可插拔加载，DSL 为远期选项 | ⏳ 目标（当前 `JavaFileAnalyzer`/`FlowDetector` 含硬编码 Spring） |
| **索引构建可分布式** | 目标：索引构建作为批处理可并行/分片；每个 indexId 是独立隔离单元 | ⏳ 目标（当前单节点） |

## 实体关系

```
NopCodeIndex
 ├──< NopCodeFile (indexId)          [cascade delete]
 │     ├──< NopCodeSymbol (fileId)
 │     ├──< NopCodeUsage (fileId)
 │     └──< NopCodeCall (fileId)
 ├──< NopCodeSymbol (indexId)        [cascade delete]
 │     ├──< NopCodeUsage (symbolId)
 │     ├──< NopCodeAnnotationUsage (annotatedSymbolId)
 │     ├──< NopCodeFlow (entryPointId)
 │     ├──< NopCodeFlowMembership (symbolId)
 │     ├──< NopCodeCall (callerId / calleeId)
 │     └──< NopCodeInheritance (subTypeId / superTypeId)
 ├──< NopCodeDependency (indexId)    [cascade delete]
 ├──< NopCodeFlow (indexId)          [cascade delete]
 │     └──< NopCodeFlowMembership (flowId)
 ├──< NopCodeUsage (indexId)         [cascade delete]
 ├──< NopCodeCall (indexId)          [cascade delete]
 ├──< NopCodeInheritance (indexId)   [cascade delete]
 ├──< NopCodeAnnotationUsage (indexId) [cascade delete]
 └──< NopCodeSemanticEdge (indexId)  [cascade delete]
```

Key relationship entities:
- **NopCodeCall** — method call edges (caller → callee)
- **NopCodeDependency** — file-level import dependencies (source → target file path)
- **NopCodeInheritance** — type hierarchy (extends/implements)
- **NopCodeSemanticEdge** — semantic relationships (similar, related, pattern-based)
- **NopCodeFlow / NopCodeFlowMembership** — execution flow tracking with criticality scores
- **NopCodeUsage** — unified reference tracking (CALL, ANNOTATES, EXTENDS, IMPLEMENTS, IMPORTS, READ, WRITE, TYPE_REFERENCE, TYPE_OF, INSTANTIATES, OVERRIDES, TESTED_BY, REFERENCES). Note: `CodeUsageKind` 含全部枚举值，但持久层当前仅派生 CALL / ANNOTATES / EXTENDS / IMPLEMENTS / TESTED_BY（IMPORTS 落在文件级 `NopCodeDependency`）；TYPE_REFERENCE/READ/WRITE/TYPE_OF/INSTANTIATES 尚未由分析器提取。
- **NopCodeAnnotationUsage** — annotation usage on symbols

## 配置项说明

| Config | Description |
|--------|-------------|
| `allowedLocalRoot` | Restricts local filesystem paths that can be indexed. Set via `setAllowedLocalRoot()`. Must be configured to prevent path traversal. |
| `ISearchEngine` | Injectable search backend. When present, symbols are synced for full-text search. Optional. |
| `IFlowDetector` | Injectable execution flow detection strategy. Required for `detectFlows()`. |
| `IChangeAnalyzer` | Injectable change analysis strategy. Requires `IFlowDetector`. Used for git-based change impact. |
| `IDeadCodeDetector` | Injectable dead code detection strategy. Required for `detectDeadCode()`. |
| `IFingerprintStore` | Pluggable fingerprint storage for incremental indexing. Defaults to `OrmFingerprintStore`. |

## Dict Definitions

Dictionaries come from two sources (11 total under `nop-code-meta/src/main/resources/_vfs/dict/code/`):

**8 dicts defined in `nop-code/model/nop-code.orm.xml`** (materialized to `_vfs/dict/code/*.dict.yaml` by codegen):

- `code/symbol_kind` — CLASS, INTERFACE, ENUM, ANNOTATION_TYPE, METHOD, FUNCTION, FIELD, ROUTE, etc.
- `code/access_modifier` — PUBLIC, PROTECTED, PRIVATE, PACKAGE_PRIVATE, INTERNAL
- `code/reference_kind` — READ, WRITE, CALL, TYPE_REFERENCE, EXTENDS, IMPLEMENTS, ANNOTATES, IMPORTS, OVERRIDES, TYPE_OF, INSTANTIATES
- `code/index_status` — CREATED, INDEXING, READY, ERROR, COMPLETED, DETECTED
- `code/language` — JAVA, PYTHON, TYPESCRIPT, JAVASCRIPT, GO, RUST, CSHARP
- `code/call_type` — CONSTRUCTOR (+ free-text return types)
- `code/relation_type` — EXTENDS, IMPLEMENTS
- `code/semantic_relation_type` — SEMANTICALLY_SIMILAR_TO, CONCEPTUALLY_RELATED_TO, SOLVES_SAME_PROBLEM, etc.

**3 dicts defined only as standalone yaml** in `_vfs/dict/code/` (no `<dict>` definition in orm.xml and no `dict=` column reference; present as dictionary resources only):

- `code/call_direction`
- `code/hierarchy_direction`
- `code/provenance` — AST_EXTRACTION, SYMBOL_SOLVER, HEURISTIC, FRAMEWORK_INFERENCE, MANUAL

## Field Name Mapping (CodeIndexService)

The `ExecutionFlow` domain model and `NopCodeFlow` ORM entity use different field names:

| ExecutionFlow (domain) | NopCodeFlow (entity) | Notes |
|------------------------|---------------------|-------|
| `criticality` | `overallScore` | Criticality score stored as overall score |
| `entryPointSymbolId` | `entryPointId` | Entry point FK uses shorter name |

See `entityToExecutionFlow()` and `persistFlows()` in `CodeIndexService` for the bidirectional mapping.

## NopCodeUsage Unique Key Evaluation

Unique key: `uk_usage_unique(indexId, symbolId, fileId, kind, line, column)`.

The `column` column is nullable (`mandatory` not set, defaults to nullable). In standard SQL, NULL ≠ NULL, so two rows with identical values but both having `column = NULL` would **not** violate the unique constraint. In practice, the Java analyzer always sets `column` from `expr.getRange()`, which may be absent for some AST nodes. To guarantee deduplication, application-level dedup is applied via deterministic ID generation (SHA-256 hash of indexId + kind + callerId + fileId + line).

## 变更分析 / 增量索引契约（P1-6.7-009/010/011，plan 2401）

- **`analyzeChanges` 的工作目录**：`NopCodeIndex.rootPath` 以 vfs 形态（`file:/abs/path`）持久化；`analyzeChanges` 在交给 `ChangeAnalyzer` 之前剥去 `file:` 前缀（Windows 下并还原 `C:/...`），使 git 进程以本地绝对路径为工作目录。git 失败不再静默折叠为「无变更」——工作目录不存在/非目录、非零退出、IOException 均使 `ChangeAnalysisResult.isTruncated()==true`。
- **增量索引失败文件语义**：`triggerIncrementalIndex` 先对全部 changed 文件做分析、再删除并写回；无法分析的文件（无 analyzer / 资源缺失 / 分析抛异常 / 分析返回 null）不删除旧记录、不写新指纹（旧 `NopCodeFile` 指纹行保留），因此下一次增量会重新检出并重试。返回值为**成功持久化**的文件数；失败以 WARN 可观测。
- **文件记录删除的索引作用域**：`deleteFileRecords` 删除 `NopCodeDependency` 时同时按 `indexId` 过滤——同一仓库/文件路径被多个索引覆盖时，删除某一索引不会波及另一索引的依赖边（call/symbol/usage 经 `generateFileId(indexId, path)` 生成 ID，天然具备索引作用域）。
- **失败文件与本轮重分析文件的关系**：N3.1-s 依赖边快照/重绑作用域限于「成功重分析文件 ∪ 已删除文件」；失败文件的符号与边保留原状，不纳入重建集合。

## 图分析并发与资源契约

本节是 nop-code 图指标物化与自愈、社区检测守卫、Leiden 超时消费面的规范声明（图分析算法本体在独立顶层模块 `nop-graph/`，见节首说明）。裁定权威 = AI 开发记录区 concurrency 设计集 leiden-timeout-envelope-and-code-graph-self-heal-singleflight-guard-contract.md（按文档边界规则此处不直接链接）；实现锚点见 `../04-reference/source-anchors.md` 的 `CODEGRAPH-CC-001` 与 `LEIDEN-CC-001`（LeidenDetector 本体在 nop-graph，无独立 owner doc——缺口声明随 R-G 治理，形态同 `nop-duckdb.md` Betweenness 段）。

**社区检测节点规模守卫（skip-and-degrade）**：`CodeGraphService.runCommunityDetection` 本体内守卫，常量 `MAX_NODES_FOR_COMMUNITY_DETECTION = 10000`（包内可见**单源**——`GraphMetricMaterializer` 引用同一常量，禁第二常量漂移源；编译期常量非配置键，配置化为候选未实施）。节点数超限时 WARN 并返回**空 CommunityResult**——与 <2 节点短路分支同形（空社区集、totalCommunities=0、"LEIDEN" 字面量），detectCommunities 兜底、getKnowledgeGaps、exportGraph 社区视图、diffGraph target 侧 4 个调用面一处封死。`GraphMetricMaterializer.detectCommunities` 同守卫：超限返回 null → COMMUNITY/COMMUNITY_INFO 行缺失、GRAPH_SUMMARY 无算法字段，读路径经既有 assemble→null→内存兜底链降级；守卫只降级社区族，BETWEENNESS/PAGE_RANK/HUB/ENTRY_POINT 族不受影响（BETWEENNESS 另有同族 10⁴ 守卫）。`diffGraph` 双侧对称早退：baseline 或 target 任一侧符号表超限 → WARN + 空 `GraphDiffDTO`（空 diff 形态：DTO 集合字段为未初始化 null，语义即「无 diff 上报」）。**生产行为声明**：超限图不再计算 Leiden（此前照常计算、60s 超时仅兜挂死不兜内存峰值），现行为空结果 + WARN 可观测；消费方补救路径 = 缩减节点集。

**Leiden 超时包络（消费面契约）**：Leiden 超时由 `LeidenConfig.timeoutMs` 承载（nop-graph-api 既有字段，缺省 30000ms，无新增配置键）；本模块生产调用显式 `setTimeoutMs(60000)`（内存兜底计算与 materializer 同值）。`timeoutMs <= 0` = 同步直跑（调用方显式传参面）。**语义边界**：底层 CWTS Leiden 计算不可中断，超时仅为放弃等待 + 既有 LabelPropagation 降级——放弃后计算在后台一次性 daemon 工作线程（`nop-graph-leiden-` 前缀命名，与 Betweenness 设施同纪律、独立实例不共享）继续至自然结束（不阻塞 JVM 退出、无线程池驻留、按名可辨识），放弃后计算自身的失败不再上报。超时路径 WARN（`nop.graph.leiden-timeout` 结构化标签，含放弃等待时长与节点规模）。**降级结果的可观测标签维持既有行为**：`algorithmUsed` 恒为 "LEIDEN" 字面量（detect 出口无条件以该字面量重建结果）——「超时降级后仍报 LEIDEN」为登记在案的既有行为，标签透传如需变更属可观测行为面，须先评估 GraphQL DTO 与 GRAPH_SUMMARY 持久值消费方。两处显式修正点（相对旧实现的语义收窄）：任务体 Error（如 OOM）不再被吞入降级路径而是原样逃逸；等待线程被中断时恢复中断标志 + 取消任务 + 上抛（终点仍为既有降级）。包络范围仅 `findClustering`（图转换与 cohesion 计算在调用线程）。

**图指标物化单飞 + 失败退避（materialize 收口门）**：`GraphMetricMaterializer.materialize` 是全部自愈/触发面的**唯一收口**——6 个触发点全部经该入口，门在此一处封死、各触发点调用面零改动。单飞形态 = per-index `tryLock` 非阻塞试探 + skip-and-degrade：同索引物化进行中时，后到触发**立即返回**（结构化 WARN `nop.code.graph-metrics-skip:indexId=...,reason=in-progress`，非静默跳过）——赢家正在计算的正是输家想要的结果，输家读路径经既有内存兜底/陈旧返回降级；非阻塞试探结构上无等待环，`ShardBuildCoordinator` 事务内调用兼容（其直接 new 装配形态不受影响），`triggerFullIndex` 物化成功语义零变化。失败退避：门内计算体异常出体时记录 per-index 冷却截止时刻并原样重抛（调用方 catch 链不变，首因可观测不变）；冷却窗缺省 **30000ms**（实例字段 + 包内可见 setter——materializer 为 direct-new 装配非 IoC bean；与 `nop.code.rebuild.debounce-millis` 家族同值非同源），窗内后续触发跳过（WARN `reason=cooldown-active`）不重试计算体；**冷却只作用于失败路径**——计算体成功即清除该索引失败记录，正常重建永不被历史失败窗口压制。

**6 触发点与逐点降级语义**：detectCommunities、getGraphAnalysis、ensureMaterializedForGraphMetrics（getSurprisingConnections / getExplorationQuestions / exportGraphWiki 三入口共用）、getCriticalNodes、`materializeGraphMetrics`（biz 面 `triggerFullIndex` 直通）、ShardBuildCoordinator 发布路径——全经 materialize 收口。门竞争失败（skip）后的消费面降级：detectCommunities / getGraphAnalysis / getCriticalNodes → 物化行缺失 → assemble null → 内存计算兜底（受上述规模守卫约束）；ensureMaterializedForGraphMetrics → 读空/陈旧行走三个分析器既有降级流（与物化失败同链）。

**Residual（显式声明，非静默降级）**：

- **row5 有界陈旧性（triggerFullIndex 面）**：`triggerFullIndex` → `materializeGraphMetrics` 在门竞争下 skip 时，本次触发的重算不执行——陈旧指标保留至下一次索引变更（invalidate 删行后下一次读自愈重算）；与既有失败吞没语义同 blast radius。
- **row6 最终一致收敛界（shard 发布面）**：`ShardBuildCoordinator` 发布路径（completeShard → finalizeIfAllCompleted → materialize）在门被占用时 skip：发布事务原子性不变（status 翻转照常提交），「published ⇒ fully scored」在该竞争窗口退化为**最终一致**——收敛上界 = 在途物化的剩余时长；在途物化失败时 = 失败冷却窗 + 下一次读触发。发布侧 skip 可观测（`reason=in-progress` WARN）。ShardBuildCoordinator 内既有代码注释（published 即 fully scored）零改动，本条为准。

