# CodeGraph vs nop-code 深度对比分析

> Status: open
> Date: 2026-06-05
> Scope: nop-code 模块架构评估，参考 CodeGraph (`@colbymchenry/codegraph`) v0.9.9
> Conclusion: （待评审）

## Context

- **目标**：对比 [CodeGraph](https://github.com/colbymchenry/codegraph)（以下简称 CG）与 nop-entropy 的 `nop-code` 模块，分析两者的架构差异、设计取舍、功能互补，为 nop-code 后续迭代提供决策参考。
- **CG 定位**：面向 AI 编码助手的本地代码知识图谱工具，TypeScript 实现，SQLite 存储，MCP 协议暴露。核心卖点：58% 更少工具调用、100% 本地运行。已获社区广泛采用（支持 Claude Code、Cursor、Codex CLI、opencode 等 8 种 AI 代理）。
- **nop-code 定位**：Nop 平台的多语言代码索引与语义分析服务，Java 实现，ORM 持久化，GraphQL API 暴露。核心价值：为 AI 辅助代码分析提供企业级结构化索引。
- **注意**：此前已有 `2026-05-25-code-review-graph-vs-nop-code.md` 对比 CRG（code-review-graph，另一个 Python 项目）。CodeGraph 与 CRG 是**不同的项目**，技术栈、架构和侧重点均不同。

---

## 一、项目概览对比

| 维度 | CodeGraph (CG) | nop-code |
|------|---------------|----------|
| **语言** | TypeScript (Node.js) | Java 21 |
| **存储** | SQLite (WAL 模式, 本地文件) | 关系型数据库 (Nop ORM) |
| **API 协议** | MCP (Model Context Protocol) | GraphQL (BizModel) |
| **解析引擎** | tree-sitter WASM (28 语言) | Java: JavaParser+SymbolSolver; Python/TS: tree-sitter Java 绑定 |
| **运行模式** | CLI + MCP Server (stdio/daemon/proxy) | Quarkus Web 应用 |
| **目标用户** | AI 编码助手 (Claude/Cursor/...) | 企业级代码分析平台 + AI 集成 |
| **部署** | npm 全局安装, 零配置 | Maven 构建, 需数据库和应用服务器 |
| **语言支持** | 28 种 (TS/JS/Python/Go/Rust/Java/C#/PHP/Ruby/C/C++/ObjC/Swift/Kotlin/Dart/Svelte/Vue/Liquid/Pascal/Lua/Luau/Scala...) | 3 种 (Java/Python/TypeScript) |
| **规模** | ~50 源文件, 单包 | 13 子模块, ~150+ 源文件 |
| **开源协议** | MIT | Nop 平台的一部分 |

---

## 二、架构深度对比

### 2.1 整体架构

**CodeGraph 架构**（4 层流水线）：

```
Source Files
    ↓ git ls-files / filesystem walk
    ↓ batch read (10 parallel)
Worker Thread (tree-sitter WASM parse)
    ↓ ExtractionResult: nodes[], edges[], unresolvedRefs[]
    ↓
SQLite (.codegraph/codegraph.db, WAL mode)
    ↓
Reference Resolver (framework → import → name matching)
    ↓ + callback synthesis (heuristic edges)
    ↓
Knowledge Graph (nodes + edges + FTS5)
    ↓
MCP Server (stdio / daemon / proxy)
    ↓
AI Assistant tools: explore, search, node, callers, callees, impact
```

**nop-code 架构**（6 层企业应用）：

```
Source Files
    ↓ VFS / filesystem scan
Language Adapter (JavaParser / tree-sitter-Java-binding)
    ↓ CodeFileAnalysisResult
    ↓
Nop ORM 持久化 (10 张数据库表)
    ↓
Graph/Flow 分析引擎 (社区检测/影响分析/流追踪)
    ↓
CodeIndexService (业务编排)
    ↓
BizModel (GraphQL API)
    ↓
Quarkus Web 应用
```

**核心差异**：
- CG 是**单进程本地工具**，数据流线性且无状态（除了 SQLite）
- nop-code 是**多租户企业服务**，有完整的 ORM 层、会话管理、权限控制
- CG 的解析在 **Worker Thread** 中异步执行（避免阻塞主线程），nop-code 使用 **BatchQueue** 批处理

### 2.2 解析层对比

| 维度 | CodeGraph | nop-code |
|------|-----------|----------|
| **解析器** | tree-sitter WASM（统一） | JavaParser（Java）+ tree-sitter Java 绑定（Python/TS） |
| **语言扩展** | `LanguageExtractor` 接口 — 声明式 AST 类型映射 + hook 函数 | `ILanguageAdapter` + `ICodeFileAnalyzer` 接口 — Visitor 模式 |
| **并行策略** | Worker Thread 池，每 250 文件回收（WASM 内存不缩） | ExecutorService + BatchQueue |
| **Java 符号解析** | tree-sitter 模式匹配（无类型求解） | JavaParser + SymbolSolver（精确解析方法签名、参数类型） |
| **错误恢复** | 10s 基础超时 + 10s/100KB + WASM OOM 重试 + 注释剥离降级 | 无特殊错误恢复 |
| **框架提取** | Svelte/Vue/Liquid/MyBatis XML/DFM 专用提取器 | 无 |
| **新增语言成本** | 低（声明式配置 + 共享 TreeSitterExtractor） | 中（需实现完整的 ICodeFileAnalyzer） |

**关键洞察**：
- CG 的 `LanguageExtractor` 是**策略模式** — 每种语言只定义 AST 节点类型映射和 hook，通用 `TreeSitterExtractor` 负责调度
- nop-code 的 Java 解析**远比 CG 精确** — JavaParser + SymbolSolver 能解析方法调用的全限定名，CG 只能做模式匹配
- nop-code 的 Python/TS 解析用 **tree-sitter Java 绑定**（bonede），CG 用 **tree-sitter WASM**（web-tree-sitter），后者更轻量且跨平台

### 2.3 图模型对比

#### 节点类型

| 节点类型 | CodeGraph | nop-code |
|----------|-----------|----------|
| class/struct/interface/trait/protocol | ✅ | ✅ (CLASS/INTERFACE) |
| enum/enum_member | ✅ | ✅ (ENUM/CONSTANT) |
| function/method/constructor | ✅ | ✅ (METHOD/FUNCTION/CONSTRUCTOR) |
| field/property/variable/constant | ✅ | ✅ (FIELD/CONSTANT/LOCAL_VARIABLE) |
| namespace/module | ✅ | ✅ (NAMESPACE) |
| type_alias | ✅ | ✅ (TYPE_ALIAS) |
| parameter/type_parameter | ✅ | ✅ (PARAMETER/TYPE_PARAMETER) |
| import/export | ✅ | ✅ (IMPORT) |
| route | ✅ (框架路由) | ❌ |
| component | ✅ (React/Vue/Svelte) | ❌ |
| annotation_type | ❌ | ✅ (ANNOTATION_TYPE) |
| mixin/decorator | ❌ | ✅ (MIXIN/DECORATOR) |

**CG 独有**：`route`（14 种 Web 框架路由识别）、`component`（前端组件）
**nop-code 独有**：`annotation_type`（Java 注解类型）、`mixin`（Python mixin）、`decorator`（Python 装饰器声明）

#### 边类型

| 边类型 | CodeGraph | nop-code |
|--------|-----------|----------|
| contains (父子) | ✅ (显式边) | ⚠️ parentId 字段 (非独立边表) |
| calls (调用) | ✅ | ✅ (CodeMethodCall) |
| imports (导入) | ✅ | ✅ (CodeFileDependency + IMPORTS usage) |
| extends (继承) | ✅ | ✅ (CodeInheritance.EXTENDS) |
| implements (实现) | ✅ | ✅ (CodeInheritance.IMPLEMENTS) |
| references (引用) | ✅ | ✅ (nop_code_usage 表, kind=TYPE_REFERENCE/CALL 等 9 种) |
| type_of (类型) | ✅ | ❌ |
| returns (返回类型) | ✅ | ❌ (returnType 字符串字段) |
| instantiates (实例化) | ✅ | ❌ |
| overrides (重写) | ✅ | ✅ (CodeUsage.OVERRIDES) |
| decorates (装饰) | ✅ | ❌ (annotationUsage 部分覆盖) |
| exports (导出) | ✅ | ❌ |
| tested_by | ❌ | ❌ |
| semantic_edge | ❌ | ✅ (AI 发现的语义关系) |
| flow_membership | ❌ | ✅ (执行流关联) |

**CG 边类型更丰富**（12 种 vs nop-code 的 ~8 种），尤其在**类型关系**（type_of/returns/instantiates）和**前端领域**（exports/decorates）上覆盖更全。

**nop-code 独有**：`semantic_edge`（语义相似度、概念关联、跨语言对应）、`flow_membership`（执行流参与）

#### 边置信度

| 置信度 | CodeGraph | nop-code |
|--------|-----------|----------|
| provenance | `'tree-sitter'` / `'scip'` / `'heuristic'` | `EXTRACTED` / `INFERRED` / `AMBIGUOUS` |
| 来源追踪 | ✅ (metadata.synthesizedBy) | ❌ |

两者概念等价，但 CG 的 `provenance` 更精细 — 不仅标记是否推断，还标记推断的具体方法（如 `swift-objc-bridge`、`rn-event-channel`），方便消费者判断可信度。

### 2.4 存储层对比

| 维度 | CodeGraph | nop-code |
|------|-----------|----------|
| **数据库** | SQLite (WAL, 本地文件) | 关系型数据库 (MySQL/PostgreSQL/H2) |
| **核心表** | 4 张 (nodes/edges/files/unresolved_refs) + 1 FTS + 1 metadata | 10 张（由 `nop-code.orm.xml` 定义：index/file/symbol/call/inheritance/usage/annotation_usage/dependency/flow/flow_membership/semantic_edge） |
| **全文搜索** | FTS5 虚拟表 + BM25 | DB LIKE + 内存评分 (待集成 nop-search) |
| **索引策略** | SQLite 内置索引 + FTS5 | ORM 实体索引 |
| **锁机制** | WAL 模式 (读写不阻塞) + FileLock | 数据库事务 |
| **增量检测** | content_hash + (size, mtime) stat 预过滤 | SHA-256 fingerprint + OrmFingerprintStore |

**关键差异**：
- CG 的 4 表设计**极简** — 所有边类型存在同一张 `edges` 表中通过 `kind` 区分
- nop-code 的**分表设计**更企业化 — 不同边类型独立存储，支持独立索引和查询优化
- CG 的 FTS5 全文搜索**开箱即用**，nop-code 目前只有 DB LIKE（但 nop-search 模块已具备 Lucene+向量+RRF 能力）

### 2.5 引用解析对比

**CodeGraph 解析管线**（5 层）：

```
1. Built-in 过滤 (跳过标准库)
2. knownNames 快速排除
3. JVM import 精确解析 (Java/Kotlin 全限定名)
4. Framework-specific 解析 (14+ 框架路由解析器)
5. Import-based 解析 (import → 源文件映射 + re-export 链追踪)
6. Name matching (名称/全限定名/大小写模糊匹配)
7. Callback synthesis (启发式动态分派边: 事件发射器/React 渲染/接口→实现)
```

**nop-code 解析**：
- Java：JavaSymbolSolver 在解析时直接完成符号解析（编译器级别精度）
- Python/TS：树遍历时提取调用关系，无独立后处理解析阶段
- 无框架特定解析（Spring DI、NestJS 路由等）
- 无启发式动态分派边合成

**CG 远强于 nop-code 的领域**：
- **框架感知**：14 种 Web 框架路由解析（Django/Flask/FastAPI/Express/NestJS/Laravel/Spring/Rails/Gin/...) — CG 能将 URL 模式链接到处理函数
- **跨语言桥接**：Swift↔ObjC、React Native Bridge、Expo Modules、Fabric — CG 合成跨语言调用边
- **动态分派**：回调合成器为事件发射器、React 渲染链、接口→实现创建启发式边

**nop-code 远强于 CG 的领域**：
- **Java 符号解析精度**：JavaParser + SymbolSolver 达到编译器级别，远超 tree-sitter 的模式匹配
- **语义边发现**：`ISemanticEdgeExtractor` 支持名称相似度、注解模式、文档关键词等多维度语义关联
- **可扩展性**：`LanguageAdapterRegistry` + Nop IoC 自动发现，新增语言只需注册适配器

---

## 三、分析能力对比

### 3.1 图遍历与查询

| 能力 | CodeGraph | nop-code |
|------|-----------|----------|
| **BFS/DFS 遍历** | ✅ (GraphTraverser) | ✅ (CallGraph BFS) |
| **调用者/被调用者** | ✅ | ✅ |
| **影响分析** | ✅ (反向 BFS, 排除 contains) | ✅ (双向 BFS + 风险等级评估) |
| **最短路径** | ✅ (findPath) | ❌ |
| **类型层次** | ✅ (extends/implements 上下遍历) | ✅ (getTypeHierarchy) |
| **循环依赖检测** | ✅ (findCircularDependencies) | ✅ (findCycles) |
| **死代码检测** | ✅ (零入边节点) | ✅ (DeadCodeDetector, 多维度排除) |
| **文件依赖图** | ✅ | ✅ |
| **子图提取** | ✅ (getFilteredSubgraph) | ❌ |

### 3.2 高级分析

| 能力 | CodeGraph | nop-code |
|------|-----------|----------|
| **社区检测** | ❌ (无) | ✅ (Leiden + LabelPropagation 双算法) |
| **入口点评分** | 隐含 (calleeCount 判断) | ✅ (EntryPointScorer, 5 级分类) |
| **关键节点分析** | ❌ | ✅ (Hub: 度中心性, Bridge: 介数中心性) |
| **知识缺口分析** | ❌ | ✅ (孤立节点 + 薄弱社区检测) |
| **执行流追踪** | ❌ | ✅ (FlowDetector, BFS 前向追踪 + 关键度评分) |
| **风险变更分析** | ❌ | ✅ (ChangeAnalyzer, git diff → 行级映射 → 多维风险评分) |
| **语义边提取** | ❌ | ✅ (名称相似度/注解模式/文档关键词) |
| **图快照对比** | ❌ | ✅ (GraphDiffer, 集合差运算) |
| **图导出** | ❌ (无原生导出) | ✅ (GraphML/Mermaid/JSON) |
| **知识缺口分析** | ❌ | ✅ (KnowledgeGapAnalyzer) |

**这是 nop-code 相对 CG 最大的优势领域** — nop-code 有 6 种 CG 完全没有的高级分析能力。

### 3.3 搜索能力

| 能力 | CodeGraph | nop-code |
|------|-----------|----------|
| **全文搜索** | ✅ FTS5 + BM25 + Porter stemmer | ⚠️ DB LIKE + 内存评分 (待集成 nop-search) |
| **模糊搜索** | ✅ (大小写不敏感 + 名称匹配) | ✅ (内存多因子评分) |
| **向量搜索** | ❌ | ❌ (nop-search 已支持，待集成) |
| **混合搜索** | ❌ | ❌ (nop-search 已支持 RRF，待集成) |
| **按符号类型过滤** | ✅ (--kind 参数) | ✅ (kind 过滤) |
| **搜索建议** | ❌ | ❌ |

**CG 当前搜索能力强于 nop-code**（FTS5 vs DB LIKE），但 nop-code 集成 nop-search 后将反超。

---

## 四、AI 集成能力对比

### 4.1 MCP vs GraphQL

| 维度 | CodeGraph (MCP) | nop-code (GraphQL) |
|------|-----------------|---------------------|
| **协议** | MCP (Model Context Protocol) | GraphQL |
| **工具数** | 8 个 (explore/search/node/callers/callees/impact/status/files) | ~30+ BizQuery/BizMutation 方法 |
| **传输** | stdio / daemon socket proxy | HTTP WebSocket |
| **认证** | 无 | @Auth 注解权限控制 |
| **嵌套查询** | 不支持 | @BizLoader 嵌套导航 |
| **实时性** | Daemon 模式共享连接 | 每次请求独立会话 |
| **多代理支持** | 原生支持 (8 种 AI 代理自动配置) | 需客户端自行集成 |

### 4.2 AI 友好性

| 能力 | CodeGraph | nop-code |
|------|-----------|----------|
| **一站式探索** | ✅ `codegraph_explore` 一个调用返回相关源码 + 关系图 + 影响范围 | ✅ `@BizLoader` 嵌套导航，单次 GraphQL 请求可获取符号+源码+引用+层级 |
| **上下文构建** | ✅ `ContextBuilder` 自适应预算，按项目规模调整输出 | ❌ 无上下文构建机制 |
| **工作流 Prompt** | ✅ MCP initialize 自动下发使用引导 | ❌ |
| **Token 效率引导** | ✅ detail_level 分级 + 文件过期提示 | ❌ |
| **自动安装配置** | ✅ `codegraph install` 一键配置 8 种 AI 代理 | ❌ |
| **文件过期提示** | ✅ 编辑中文件在结果中标记为过期 | ❌ |

**nop-code 的 GraphQL `@BizLoader` 机制已支持一站式查询** — `SymbolDTO` 上挂载了 `usages`、`sourceCode` 等 BizLoader，`CodeFileAnalysisResult` 上挂载了 `symbols`、`types`、`sourceCode`、`outline` 等 BizLoader。AI 代理可以在单次 GraphQL 请求中嵌套选择所有关联信息，无需多次往返。`getCallHierarchy`、`getTypeHierarchy` 等也是单次调用返回完整结果树。

CG 在 AI 集成方面的实际优势在于：**MCP 协议原生支持**（AI 代理自动识别工具）、**自适应输出预算**（按项目规模调整响应大小）、**工作流 Prompt**（自动引导 AI 使用策略）。nop-code 需要补充的是 MCP 适配层，而非多次查询问题。

---

## 五、增量更新与同步对比

| 维度 | CodeGraph | nop-code |
|------|-----------|----------|
| **文件监控** | ✅ 原生 OS 事件 (FSEvents/inotify/ReadDirectoryChangesW) | ❌ |
| **防抖策略** | ✅ 2s 可配置 (CODEGRAPH_WATCH_DEBOUNCE_MS) | ❌ |
| **连接时追赶** | ✅ MCP 重连时自动同步离线期间的变更 | ❌ |
| **增量检测** | ✅ stat(size,mtime) 预过滤 + content_hash 确认 | ✅ SHA-256 fingerprint + OrmFingerprintStore |
| **依赖影响追踪** | ❌ (只重索引变更文件) | ❌ (IncrementalDetector 只检直接变更) |
| **多进程安全** | ✅ FileLock + 进程内互斥 | ✅ 数据库事务 |

**CG 的自动同步能力远超 nop-code** — 文件监控 + 防抖 + 连接时追赶构成了完整的"代码变更→图更新"闭环。

---

## 六、跨语言与框架感知

### 6.1 框架路由识别

**CG 支持 14 种 Web 框架**：

| 框架 | 路由模式 | nop-code |
|------|---------|----------|
| Django | `path()`, `re_path()`, `url()`, `include()` | ❌ |
| Flask | `@app.route()`, blueprint | ❌ |
| FastAPI | `@app.get/post/put/delete()` | ❌ |
| Express | `app.get()`, `router.post()` | ❌ |
| NestJS | `@Controller` + `@Get/@Post`, GraphQL `@Resolver` | ❌ |
| Laravel | `Route::get()`, `Route::resource()` | ❌ |
| Rails | `get '/x', to: 'users#index'` | ❌ |
| Spring | `@GetMapping`, `@PostMapping` | ❌ |
| Gin/chi/gorilla/mux | `r.GET()`, `router.HandleFunc()` | ❌ |
| Axum/actix/Rocket | `.route("/x", get(handler))` | ❌ |
| ASP.NET | `[HttpGet("/x")]` | ❌ |
| Vapor | `app.get("x", use: handler)` | ❌ |
| React Router / SvelteKit | Route 组件节点 | ❌ |
| Drupal | `*.routing.yml`, `hook_*` 实现 | ❌ |

**nop-code 完全缺失框架路由识别能力。** 这是一个重要差距 — 对 Spring Boot 项目的代码理解至关重要。

### 6.2 跨语言桥接

**CG 独有的跨语言桥接能力**：

| 桥接 | 示例 | nop-code |
|------|------|----------|
| Swift → ObjC | Swift 调用 ObjC selector | ❌ |
| ObjC → Swift | ObjC 调用 @objc Swift 方法 | ❌ |
| RN Legacy Bridge | JS `NativeModules.X.fn()` → ObjC/Java | ❌ |
| RN TurboModules | JS Native spec → native impl | ❌ |
| RN Native→JS Events | `sendEventWithName` → JS listener | ❌ |
| Expo Modules | `requireNativeModule('X')` → native | ❌ |
| Fabric/Paper Views | JSX `<MyView>` → native impl | ❌ |

nop-code 有 `CROSS_LANGUAGE_PEER` 语义边类型，但无自动发现机制。

---

## 七、设计理念差异

### 7.1 核心设计哲学

| 维度 | CodeGraph | nop-code |
|------|-----------|----------|
| **核心原则** | 零配置、100% 本地、AI 优先 | 可扩展、平台化、企业级 |
| **抽象层次** | 扁平化（4 表、8 工具） | 分层化（13 模块、10+ 表、30+ API） |
| **可扩展性** | 声明式 LanguageExtractor + Framework resolver | ILanguageAdapter + IoC 自动发现 |
| **数据模型** | 统一 nodes/edges (kind 区分) | 分表存储 (类型安全但复杂) |
| **运行时依赖** | 零 (自包含 Node 运行时) | 重 (Quarkus + 数据库 + ORM) |
| **配置** | 零配置 (自动检测一切) | Nop 平台配置体系 |
| **输出预算** | 自适应 (按项目规模调整响应大小) | 固定 (全量返回) |

### 7.2 架构模式对比

| 模式 | CodeGraph | nop-code |
|------|-----------|----------|
| **解析扩展** | Strategy: `LanguageExtractor` 配置对象 | Strategy: `ILanguageAdapter` 接口 |
| **图遍历** | 通用 `GraphTraverser` + 配置化 | 专用方法 (getCallHierarchy/getImpactAnalysis) |
| **边来源** | `provenance` 字段 (tree-sitter/heuristic/scip) | `EdgeConfidence` 枚举 (EXTRACTED/INFERRED/AMBIGUOUS) |
| **缓存** | LRU bounded (防止 OOM) | 无图分析缓存 (每次全量重建) |
| **并发控制** | FileLock + 进程内 mutex | 数据库事务 |
| **Worker 管理** | 250 文件回收 + 超时保护 + OOM 重试 | BatchQueue 批处理 |

### 7.3 设计取舍总结

| 选择 | CodeGraph 取 | nop-code 取 | 谁更合理 |
|------|-------------|------------|---------|
| **存储模型** | 统一大表 (灵活) | 分类型表 (严格) | 取决于场景：CG 的统一模型更简洁，nop-code 的分表更适合复杂查询 |
| **Java 解析** | tree-sitter (快但粗) | JavaParser (慢但精) | nop-code 更合理（Java 是核心语言） |
| **API 协议** | MCP (AI 原生) | GraphQL (通用) | 各有优势：MCP 对 AI 代理友好，GraphQL 对企业应用友好 |
| **配置** | 零配置 | 声明式配置 | CG 更合理（开发者体验优先） |
| **运行时** | 自包含 | 需应用服务器 | CG 更合理（本地工具不应依赖服务器） |

---

## 八、nop-code 的优势（CG 没有的）

公平起见，记录 nop-code 已有但 CG 缺失的优势：

| 能力 | nop-code | CodeGraph |
|------|----------|-----------|
| **Java 精确符号解析** | JavaParser + SymbolSolver，编译器级精度 | tree-sitter 模式匹配，无类型求解 |
| **社区检测** | Leiden + LabelPropagation 双算法，大图优化 | ❌ |
| **执行流追踪** | FlowDetector + 五维关键度评分 | ❌ |
| **风险评分变更分析** | git diff → 行级映射 → 多维风险评分 | ❌ |
| **语义边发现** | 名称相似度/注解模式/文档关键词 | ❌ |
| **关键节点分析** | Hub(度中心性) + Bridge(介数中心性) | ❌ |
| **图快照对比** | GraphDiffer | ❌ |
| **多格式导出** | GraphML/Mermaid/JSON | ❌ |
| **注解使用追踪** | CodeAnnotationUsage 独立模型 | ❌ |
| **企业级持久化** | ORM + 多表 + 数据库索引 | SQLite 单文件 |
| **权限控制** | @Auth 注解 | 无认证机制 |
| **Web UI** | code-browser/call-hierarchy/type-hierarchy/dashboard | CLI only |
| **GraphQL 嵌套查询** | @BizLoader 关联导航 | 扁平工具调用 |
| **设计可扩展性** | 7 个扩展接口 + Nop IoC 自动发现 | 声明式但较固定 |

---

## 九、可借鉴功能优先级排序

基于 CG 功能对 nop-code 的价值、实现难度、与 Nop 平台设计理念的契合度进行综合评估。

### P0 — 核心缺失，高价值

| # | 功能 | CG 实现 | nop-code 补充方案 | 价值 | 难度 |
|---|------|---------|------------------|------|------|
| 1 | **框架路由识别** | 14 种 Web 框架路由提取器 | 新增 `IFrameworkRouteExtractor` 接口，从 Spring `@GetMapping` 等注解提取路由→处理器映射 | 高 (Java 企业项目核心) | 中 |
| 2 | **MCP Server 适配** | MCP stdio/daemon/proxy 三模式 | 新增 `nop-code-mcp` 模块，将 GraphQL API 适配为 MCP tools | 高 (AI 代理直接集成) | 中 |
| 3 | **一站式上下文构建** | `codegraph_explore` 一个调用返回完整上下文 | nop-code 的 `@BizLoader` 已支持 GraphQL 嵌套选择（符号+源码+引用+层级一次获取），与 CG 等价。可进一步新增 `explore` 便捷查询整合更多维度 | 中 (AI token 效率) | 低 |
| 4 | **搜索增强** | FTS5 + BM25 | 集成 nop-search (Lucene + 向量 + RRF)，替换 DB LIKE | 高 (大项目性能) | 低 |
| 5 | **分析缓存** | LRU bounded 缓存 | 新增 `AnalysisCache`，按 indexId 缓存 SymbolTable + CallGraph | 高 (性能) | 低 |

### P1 — 重要增强

| # | 功能 | CG 实现 | nop-code 补充方案 | 价值 | 难度 |
|---|------|---------|------------------|------|------|
| 6 | **边类型丰富化** | 12 种 (type_of/returns/instantiates/exports) | 新增 type_of/returns/instantiates 边类型 | 中 (影响分析完整性) | 中 |
| 7 | **启发式边合成** | callback synthesis (动态分派/事件链) | 新增 `IHeuristicEdgeSynthesizer` 接口 | 中 (覆盖 tree-sitter 无法捕获的关系) | 中 |
| 8 | **自适应输出预算** | 按项目规模调整响应大小 | 在 GraphQL 响应中增加分页/截断/字段选择 | 中 (AI token 效率) | 低 |
| 9 | **文件监控** | 原生 OS 事件 + 防抖 + 追赶同步 | 可用 Nop 定时任务或 Java WatchService | 中 (实时性) | 中 |
| 10 | **更多语言支持** | 28 种 (Go/Rust/C#/PHP/Ruby/C/C++/ObjC/Swift/Kotlin/Dart/...) | 基于 tree-sitter Java 绑定扩展 `ILanguageAdapter` | 中 (覆盖面) | 中 |

### P2 — 有价值增强

| # | 功能 | CG 实现 | nop-code 补充方案 | 价值 | 难度 |
|---|------|---------|------------------|------|------|
| 11 | **跨语言桥接** | Swift↔ObjC/RN Bridge/Expo | 利用 CROSS_LANGUAGE_PEER 语义边 + 语言特定桥接发现器 | 低 (非核心场景) | 高 |
| 12 | **零配置体验** | 自动检测一切 | VFS + 自动语言检测 + 合理默认值 | 中 (开发者体验) | 低 |
| 13 | **Token 效率引导** | detail_level + next_tool_suggestions | 在 GraphQL 响应中增加 `suggestedNextQueries` | 中 (AI 体验) | 低 |

---

## 十、互补融合建议

两个项目有极强的互补性。以下是可能的融合方向：

### 方案 A：CG 作为 nop-code 的前端（推荐）

```
AI Agent (Claude/Cursor/...)
    ↓ MCP protocol
CodeGraph MCP Server (轻量适配层)
    ↓ HTTP/GraphQL
nop-code Service (企业级分析引擎)
```

- CG 负责与 AI 代理的集成（MCP 协议、工具引导、token 优化）
- nop-code 负责深度分析（社区检测、执行流、风险评分、语义边）
- CG 的 `codegraph_explore` 可以在内部调用 nop-code 的 GraphQL API

### 方案 B：nop-code 借鉴 CG 的模式

- 将 CG 的声明式 `LanguageExtractor` 模式移植到 Java（替代手写 Visitor）
- 将 CG 的框架路由提取器移植到 nop-code 的 `ILanguageAdapter` 扩展
- 将 CG 的 MCP Server 模式作为 `nop-code-mcp` 新模块

### 方案 C：独立共存

- CG 用于本地开发场景（AI 编码助手增强）
- nop-code 用于企业 CI/CD 场景（代码审查、架构治理、变更风险评估）
- 通过共享的图模型标准（如 GraphML）实现数据互通

---

## 十一、总结

### 核心差异一句话

**CodeGraph = 面向 AI 代理的轻量级代码知识图谱**（零配置、MCP 原生、28 语言、跨语言桥接）
**nop-code = 面向企业的深度代码分析平台**（Java 精确解析、社区检测、执行流、风险评分、GraphQL API）

### 互补关系

| CG 强 | nop-code 强 |
|--------|-------------|
| AI 代理集成 (MCP) | 企业级架构 (GraphQL/IoC/ORM) |
| 语言覆盖面 (28) | Java 解析精度 (SymbolSolver) |
| 框架路由识别 (14) | 高级图分析 (社区/流/风险) |
| 跨语言桥接 | 语义边发现 |
| 自动同步 (文件监控) | Web UI + 可视化 |
| 零配置体验 | 权限控制 + 多租户 |
| Token 效率 | 图导出 + 快照对比 |

### 建议的下一步

1. **短期（P0）**：集成 nop-search、引入分析缓存、新增 MCP 适配层
2. **中期（P1）**：Spring 路由识别、启发式边合成、更多语言支持
3. **长期（P2）**：零配置体验优化、跨语言桥接、自适应输出

---

## Open Questions

- [ ] MCP 适配层是否应作为 nop-code 的子模块，还是独立项目？
- [ ] 是否应直接引入 CG 作为 nop-code 的本地开发模式（方案 A）？
- [ ] Spring 框架路由识别的范围：@GetMapping/@PostMapping 是最小集，是否包含 @Scheduled/@EventListener 等入口点？
- [ ] 声明式 LanguageExtractor 模式是否适合 Java 生态？JavaParser 的 Visitor 模式已足够成熟
- [ ] nop-code 的图分析能力（社区检测/执行流）是否应通过 MCP 暴露给 AI 代理？

---

## 附录 A：事实核查纠正记录

基于源码逐行审计，以下是对正文中原始表述的纠正：

### A.1 已纠正的错误

| # | 原始表述 | 纠正为 | 依据 |
|---|---------|--------|------|
| 1 | nop-code "需多次 GraphQL 查询" | `@BizLoader` 嵌套导航，单次 GraphQL 请求可获取符号+源码+引用+层级 | `NopCodeSymbolBizModel` 的 `@BizLoader(forType = SymbolDTO.class)` 提供 `usages`、`sourceCode` 关联字段 |
| 2 | nop-code 核心模型无 `CodeUsage.java` | 核心模型确实无此文件，但 ORM 层**有** `nop_code_usage` 表和 `NopCodeUsage` 实体（由 `nop-code.orm.xml` 生成）。`CodeUsageKind` 枚举 9 种引用类型。`findReferencedBy` 查的可能是注解使用或 usage 表（取决于 service 层实现） | `nop-code.orm.xml` 第 454-522 行定义了 `NopCodeUsage` 实体，含 9 列 + 4 个关系 |
| 3 | nop-code "references 存在但未填充" | ORM 层有 `nop_code_usage` 表（kind 含 `REFERENCES`=READ 的引用），但 `CodeFileAnalysisResult` 核心模型无 `usages` 字段，需确认 service 层是否写入 | `ICodeIndexService.getSymbolUsages()` 返回 `List<CodeAnnotationUsage>`，说明当前查的是注解使用表 |

### A.2 事实确认

| # | 语句 | 确认结果 |
|---|------|---------|
| 1 | CG 有 8 个 MCP 工具 | ✅ `getStaticTools()` 返回 8 个：explore, search, node, callers, callees, impact, status, files。小于 500 文件的项目只暴露 3 个核心工具 (explore, search, node) |
| 2 | nop-code 有 Leiden + LabelPropagation 双算法 | ✅ `CommunityDetector`：默认 Leiden（CWTS 库），失败或超大图回退到 LabelPropagation（JGraphT） |
| 3 | nop-code 有介数中心性 | ✅ `CriticalNodeAnalyzer.computeBridgeNodes()` 使用 JGraphT `BetweennessCentrality` |
| 4 | CG 的 `handleExplore` 使用 Personalized PageRank | ✅ `computeGraphRelevance()` — Random-Walk-with-Restart，alpha=0.25，25 次迭代 |
| 5 | nop-code 的 `FlowDetector` 有五维关键度评分 | ✅ 权重：fileSpread(0.30) + externalScore(0.20) + securityScore(0.25) + testGap(0.15) + depthScore(0.10) |
| 6 | nop-code 的 `EdgeConfidence` 有 AMBIGUOUS | ✅ 三级：EXTRACTED(10), INFERRED(20), AMBIGUOUS(30) |

---

## 附录 B：算法逐项对比

### B.1 图遍历算法

#### BFS 遍历

| 维度 | CodeGraph `traverseBFS` | nop-code `CallGraph` + Service 层 |
|------|------------------------|----------------------------------|
| **实现方式** | `GraphTraverser.traverseBFS()` — 通用配置化 BFS，支持任意方向/边类型过滤/节点类型过滤/深度限制/数量限制 | 无独立通用 BFS。各算法内联 BFS（`ImpactAnalyzer.traceImpact`、`FlowDetector.traceForward`） |
| **队列结构** | `Queue<TraversalStep>` — `{node, edge, depth}` | `Queue<String[]>` — `{nodeId, depthAsString}` |
| **访问控制** | `visited: Set<string>` 防环 | `visited: Set<String>` 防环 |
| **边优先级排序** | `contains`(0) > `calls`(1) > others(2) | 无优先级排序 |
| **批量获取** | `getNodesByIds()` 批量获取邻居节点 | `symbolTable.getById()` 逐个查找 |
| **限制** | `maxDepth`, `limit`, `nodeKinds`, `edgeKinds` 全可配 | `maxDepth`, `maxNodes` |
| **返回** | `Subgraph` (nodes Map + edges[] + roots[]) | 各自返回 DTO（`ImpactedSymbol[]` / `ExecutionFlow` 等） |

#### DFS 遍历

| 维度 | CodeGraph | nop-code |
|------|-----------|----------|
| **实现** | `traverseDFS()` → `dfsRecursive()` 递归 | `findCircularDependencies` — DFS 环检测（在 `GraphQueryManager` 中） |
| **递归栈跟踪** | 不需要（通用遍历用 visited 集合） | 是，跟踪 recursion stack 用于环检测 |

#### 最短路径

| 维度 | CodeGraph `findPath` | nop-code |
|------|---------------------|----------|
| **算法** | BFS，每队列条目跟踪完整路径 | ❌ 无实现 |
| **返回** | `Array<{node, edge}> \| null` | — |

#### 影响分析

| 维度 | CodeGraph `getImpactRadius` | nop-code `ImpactAnalyzer.analyzeImpact` |
|------|---------------------------|---------------------------------------|
| **方向** | 单向：反向（incoming，排除 `contains`） | 双向：upstream（callers）+ downstream（callees） |
| **容器感知** | 容器节点（class/interface 等）递归进入子节点 | 无 |
| **边界** | `maxDepth` (默认 3) | `maxDepth` (默认 3) + `maxNodes` (默认 100) |
| **风险评估** | 无 | 有：CRITICAL(>50 或 depth>5) / HIGH(>20 或 depth>3) / MEDIUM(>5) / LOW |
| **返回** | `Subgraph` | `ImpactResult` {targetSymbolId, upstream[], downstream[], riskLevel} |

#### 调用者/被调用者

| 维度 | CodeGraph | nop-code |
|------|-----------|----------|
| **边类型** | `['calls', 'references', 'imports']` | `CallGraph.getCallers/getCallees` — 仅 calls |
| **递归深度** | `maxDepth` 参数 | `maxDepth` 参数 |
| **返回** | `Array<{node, edge}>` | `CallHierarchyDTO` 递归树结构 |

#### 类型层次

| 维度 | CodeGraph `getTypeHierarchy` | nop-code `getTypeHierarchy` |
|------|----------------------------|---------------------------|
| **边类型** | outgoing `extends`/`implements`（向上）+ incoming（向下） | `CodeInheritance` 表，按 `EXTENDS`/`IMPLEMENTS` 过滤 |
| **方向** | 双向（ancestors + descendants） | 可配置：`both`/`up`/`down` |
| **返回** | `Subgraph` | `TypeHierarchyDTO` 递归树 {symbol, superTypes[], subTypes[]} |

### B.2 高级分析算法

#### 社区检测

| 维度 | CodeGraph | nop-code `CommunityDetector` |
|------|-----------|---------------------------|
| **主算法** | ❌ 无 | Leiden (CWTS `nl.cwts.networkanalysis`) |
| **回退算法** | — | LabelPropagation (JGraphT) |
| **参数** | — | resolution=0.1, randomness=0.01, maxIterations=10, minCommunitySize=2 |
| **大图优化** | — | 阈值 10,000 节点：过滤 degree<2 节点，减少迭代(3次)，可选超时(60s) |
| **超大社区分裂** | — | 社区>25% 总节点 → 递归 LabelPropagation 分裂，最深 3 层 |
| **内聚度** | — | `internalEdges / (internalEdges + externalEdges)` |
| **模度** | — | `leidenAlgorithm.calcQuality(network, clustering)` |
| **结果** | — | `CommunityDetectionResult` {communities[], totalSymbols, totalCommunities, averageCohesion, modularity, algorithmUsed, processingTimeMs} |

#### 入口点评分

| 维度 | CodeGraph | nop-code `EntryPointScorer` |
|------|-----------|---------------------------|
| **公式** | 无专用评分器。`handleExplore` 用 RWR (Personalized PageRank, alpha=0.25) 排名 | `score = calleeCount / (callerCount + 1)` |
| **分类** | — | 5 级：ENTRY_POINT(score≥2 && callers≤3) / UTILITY(callers≥5 && callees≤3) / MIDDLEWARE / LEAF(callees=0) / ISOLATED(both=0) |
| **过滤** | — | 仅 METHOD 和 CONSTRUCTOR |
| **返回** | — | `List<EntryPointScore>` {symbolId, score, callerCount, calleeCount, entryPointType} |

#### 关键节点分析

| 维度 | CodeGraph | nop-code `CriticalNodeAnalyzer` |
|------|-----------|-------------------------------|
| **Hub 检测** | ❌ | 度中心性（inDegree + outDegree），取 topN |
| **Bridge 检测** | ❌ | JGraphT `BetweennessCentrality`（不归一化），取 topN |
| **返回** | — | `CriticalNodeResult` {hubNodes[], bridgeNodes[], totalNodes, topN} |

#### 知识缺口分析

| 维度 | CodeGraph | nop-code `KnowledgeGapAnalyzer` |
|------|-----------|-------------------------------|
| **孤立节点** | `findDeadCode` — 零入边（排除 `contains`），跳过 exported 符号 | 检查 `getCallers()` 和 `getCallees()` 均为空 |
| **薄弱社区** | ❌ | 内聚度 < 0.1（可配置） |
| **返回** | `Node[]` | `KnowledgeGapResult` {isolatedSymbols[], weakCommunities[]} |

### B.3 执行流与变更分析

#### 执行流追踪

| 维度 | CodeGraph | nop-code `FlowDetector` |
|------|-----------|----------------------|
| **入口点发现** | ❌ 无 | 三策略 OR：① EntryPointScorer 高分方法 ② 注解模式（12 种 Spring 注解）③ 名称模式（regex 匹配 main/handle*/process* 等） |
| **前向追踪** | — | BFS 沿 callees，maxDepth=15 |
| **外部包剪枝** | — | 14 个前缀（java./javax./jakarta./org.springframework. 等） |
| **关键度评分** | — | 五维加权：fileSpread×0.30 + external×0.20 + security×0.25 + testGap×0.15 + depth×0.10 |
| **安全关键词** | — | 30+ 个（auth/login/encrypt/password/...）regex 匹配 |
| **测试文件检测** | — | 8 种模式（/test/, Test.java, .spec.ts 等） |
| **缓存** | — | ConcurrentHashMap，最大 20 条 |
| **返回** | — | `ExecutionFlow` {id, name, entryPoint, depth, criticality, pathNodeIds[], stats{fileCount, symbolCount, maxDepth}} |

#### 死代码检测

| 维度 | CodeGraph `findDeadCode` | nop-code `DeadCodeDetector` |
|------|-------------------------|---------------------------|
| **检测条件** | 零入边（排除 `contains`），跳过 exported 符号 | `getCallers()` 为空 |
| **排除规则** | 跳过 exported | 多维度排除：构造器/抽象方法/框架注解(20种)/测试符号/ORM 子类/Python dunder(38种)/装饰器方法(5种)/约定入口("main") |
| **置信度** | 无分级 | 0.60(可能动态), 0.70(非 private 字段), 0.75(非 private 非 static 方法), 0.95(private 或 static) |
| **分类** | — | dead(≥0.9) / suspicious(≥0.5) |
| **返回** | `Node[]` | `DeadCodeReport` {deadSymbols[], suspiciousSymbols[], stats{total, dead, suspicious}} |

#### 变更风险分析

| 维度 | CodeGraph | nop-code `ChangeAnalyzer` |
|------|-----------|--------------------------|
| **输入** | ❌ 无 | git diff baseline..target --unified=0 |
| **行级映射** | — | 解析 hunk header `@@ -a,b +c,d @@`，映射到符号行范围 |
| **风险评分** | — | flowParticipation(≤0.25) + communityCrossing(≤0.15) + testCoverageGap(0.05-0.30) + securitySensitivity(0.20) + callerCount(≤0.10) |
| **风险分级** | — | HIGH(≥0.50) / MEDIUM(≥0.25) / LOW |
| **安全关键词** | — | 31 个（auth/login/password/credential/token/...） |
| **Git ref 校验** | — | `^[a-zA-Z0-9._/\-~]{1,256}$` |
| **超时** | — | 30 秒进程超时 |
| **返回** | — | `ChangeAnalysisResult` {changedFiles[], affectedSymbols[], riskSummary{high,medium,low}, suggestedActions[]} |

### B.4 上下文构建与探索

#### 上下文构建（CG `ContextBuilder` vs nop-code 无对应物）

| 维度 | CodeGraph `ContextBuilder.findRelevantContext` | nop-code |
|------|----------------------------------------------|----------|
| **查询解析** | 6 种 regex 提取符号名（CamelCase/snake_case/SCREAMING_SNAKE/ALL_CAPS/dot.notation/lowercase），~130 词停用表 | `searchCode` 接收纯字符串 |
| **搜索管线** | 精确名匹配 → 定义前缀匹配（含词干变体）→ FTS5 多词搜索（含多词 boost）→ 多词共现重排序 → CamelCase 边界 LIKE → 复合词匹配 | DB LIKE + 内存多因子评分 |
| **排序调整** | 测试文件降权(×0.3) → 核心目录加分(+25) → 最终排序 | — |
| **图扩展** | 类型层次展开（2 轮）→ BFS 遍历入口点 | — |
| **预算控制** | maxNodes(20)/searchLimit(3)/traversalDepth(1)/minScore(0.3) | 固定 limit 参数 |
| **文件多样性** | 单文件上限 20%，非生产文件上限 15% | — |
| **置信度评估** | high/low 置信度标记 | — |

#### 探索引擎（CG `handleExplore` vs nop-code `getCallHierarchy`/`searchCode` 组合）

| 维度 | CodeGraph `handleExplore` | nop-code |
|------|--------------------------|----------|
| **搜索参数** | searchLimit=8, traversalDepth=3, maxNodes=200, minScore=0.2 | searchCode 固定 limit |
| **种子注入** | 查询分词(≤16)→解析为可调用定义→按重载和 co-named 容器筛选 | — |
| **文件评分** | namedSeed(+50) / entry(+10) / connected(+3) / else(+1) | — |
| **图相关度** | Random-Walk-with-Restart (alpha=0.25, 25 iterations)，保留 score≥6% 的文件 | — |
| **测试文件排除** | 硬排除（除非查询包含 test 关键词） | 无 |
| **自适应预算** | 5 级：{<150: 13K/4文件/3.8K每文件} → {≥15000: 24K/8文件/7K每文件} | 固定 |
| **多态折叠** | 同类 ≥3 个兄弟时，非主干折叠为签名 | — |
| **聚类** | 按重要性(10/9/6/3/2/1)排序→密度→分数→span，每文件有预算上限 | — |
| **调用路径** | `buildCallPathsSection` — DFS 在 calls 边上，MAX_HOPS=6，保留≥3节点链，最多 3 条 | `getCallHierarchy` 返回递归树 |
| **爆炸半径** | `buildBlastRadiusSection` — ROOT_CAP=5，FILE_CAP=4，含测试覆盖 | `getImpactAnalysis` 返回 upstream+downstream 列表 |
| **硬上限** | min(1.5×maxOutputChars, 25000) chars | 无 |

### B.5 语义边提取

| 提取器 | 算法 | 阈值 | 复杂度 | 返回 |
|--------|------|------|--------|------|
| `NameSimilarityExtractor` | camelCase/snake_case→词集→Jaccard | ≥0.7 | O(N²) 上限 5000 符号 | `SEMANTICALLY_SIMILAR_TO` 无向边 |
| `AnnotationPatternExtractor` | 共享注解→O(N²) 配对 | ≥2 符号共享（排除 Override/Deprecated 等 4 种公共注解） | O(N²) | `CONCEPTUALLY_RELATED_TO` 无向边，固定 confidence=0.8 |
| `DocKeywordExtractor` | 文档分词(去停用词)→Jaccard | ≥2 关键词，≥0.5 重叠 | O(N²) 上限 5000 | `CONCEPTUALLY_RELATED_TO` 无向边 |

CG 无对应能力。

---

## 附录 C：外部可调用 API 逐项对比

### C.1 CodeGraph MCP Tools (8 个)

| # | 工具名 | 必需参数 | 可选参数 | 返回内容 | 核心逻辑 |
|---|--------|---------|---------|---------|---------|
| 1 | `codegraph_explore` | `query` (string) | `maxFiles` (number, 默认 12), `projectPath` (string) | 源码按文件分组 + 爆炸半径 + 关系图 + 自适应预算 | `findRelevantContext` → RWR 排名 → 文件评分 → 聚类 → 自适应裁剪 |
| 2 | `codegraph_search` | `query` (string) | `kind` (enum: function/method/class/interface/type/variable/route/component), `limit` (number, 默认 10), `projectPath` | name, kind, file:line, signature | FTS5 搜索 + 精确名匹配 |
| 3 | `codegraph_node` | `symbol` (string) | `includeCode` (boolean, 默认 false), `file` (string), `line` (number), `projectPath` | 符号详情 + (可选)body/outline + caller/callee trail | 多重载解析：BODY_BUDGET=12000 chars, HARD_CAP=16 definitions |
| 4 | `codegraph_callers` | `symbol` (string) | `limit` (number, 默认 20), `projectPath` | 调用者列表 | `getCallers()` 沿 calls/references/imports 边 |
| 5 | `codegraph_callees` | `symbol` (string) | `limit` (number, 默认 20), `projectPath` | 被调用者列表 | `getCallees()` 沿 calls/references/imports 边 |
| 6 | `codegraph_impact` | `symbol` (string) | `depth` (number, 默认 2, 范围 1-10), `projectPath` | 影响范围按文件分组 | `getImpactRadius()` 反向 BFS |
| 7 | `codegraph_status` | — | `projectPath` | file/node/edge 计数, DB 大小, journal 模式, pending 文件 | 直接查 DB 统计 |
| 8 | `codegraph_files` | — | `path`, `pattern` (glob), `format` (tree/flat/grouped), `includeMetadata` (默认 true), `maxDepth`, `projectPath` | 文件树 + 语言 + 符号计数 | 查 files 表 |

### C.2 nop-code GraphQL API (按 BizModel 分组)

#### NopCodeIndexBizModel (24 个方法)

| # | 方法名 | 类型 | 参数 | 返回类型 | 核心逻辑 |
|---|--------|------|------|---------|---------|
| 1 | `triggerFullIndex` | Mutation | indexId, projectPath | String | `codeIndexService.indexDirectory()` |
| 2 | `triggerIncrementalIndex` | Mutation | indexId, projectPath, manifestPath | int | `codeIndexService.triggerIncrementalIndex()` |
| 3 | `indexDirectory` | Mutation | indexId, directoryPath, filePattern? | int | `codeIndexService.indexDirectory()` |
| 4 | `indexFile` | Mutation | indexId, filePath, sourceCode | FileAnalysisDTO | `codeIndexService.indexFile()` |
| 5 | `deleteIndex` | Mutation | indexId | boolean | `codeIndexService.deleteIndex()` |
| 6 | `getStats` | Query | indexId | IndexStatsDTO | `codeIndexService.getIndexStats()` |
| 7 | `getIncrementalStatus` | Query | indexId | IncrementalStatus | 内存 ConcurrentHashMap 查询 |
| 8 | `detectCommunities` | Query | indexId | CommunityDetectionResultDTO | `CommunityDetector.detectCommunities()` |
| 9 | `getGraphAnalysis` | Query | indexId, topN? | GraphAnalysisResultDTO | `CriticalNodeAnalyzer.analyze()` + 凝聚度统计 |
| 10 | `getImpactAnalysis` | Query | indexId, symbolId, depth? | ImpactResultDTO | `ImpactAnalyzer.analyzeImpact()` |
| 11 | `getCriticalNodes` | Query | indexId, topN? | CriticalNodeResultDTO | `CriticalNodeAnalyzer.analyze()` |
| 12 | `getKnowledgeGaps` | Query | indexId | KnowledgeGapResultDTO | `KnowledgeGapAnalyzer.analyze()` |
| 13 | `exportGraph` | Query | indexId, format, communityView? | String | `GraphExporter.export()` → GraphML/Mermaid/JSON |
| 14 | `diffGraph` | Query | baselineIndexId, targetIndexId | GraphDiffDTO | `GraphDiffer.diff()` |
| 15 | `getDeps` | Query | indexId, filePath, depth? | DepGraphDTO | 文件依赖正向遍历 |
| 16 | `getReverseDeps` | Query | indexId, filePath, depth?, limit? | DepGraphDTO | 文件依赖反向遍历 |
| 17 | `findCycles` | Query | indexId, minSize? | List<List<String>> | 循环依赖检测 |
| 18 | `getDepGraph` | Query | indexId, includeExternal? | DepGraphDTO | 全局依赖图 |
| 19 | `detectFlows` | Mutation | indexId | List<ExecutionFlow> | `FlowDetector.detectFlows()` |
| 20 | `listFlows` | Query | indexId | List<ExecutionFlow> | `FlowDetector.listFlows()` |
| 21 | `getFlow` | Query | indexId, flowId | ExecutionFlow | `FlowDetector.getFlow()` |
| 22 | `getAffectedFlows` | Query | indexId, changedFilePaths | List<ExecutionFlow> | `FlowDetector.getAffectedFlows()` |
| 23 | `analyzeChanges` | Query | indexId, baselineCommitish, targetCommitish | ChangeAnalysisResult | `ChangeAnalyzer.analyzeChanges()` |
| 24 | `findDependentFiles` | Query | indexId, filePath | List<String> | 受影响文件列表 |

#### NopCodeSymbolBizModel (15 个方法 + 2 个 BizLoader)

| # | 方法名 | 类型 | 参数 | 返回类型 | 核心逻辑 |
|---|--------|------|------|---------|---------|
| 1 | `getBySymbolId` | Query | id, indexId | SymbolDTO | `codeIndexService.getSymbolById()` |
| 2 | `findByQualifiedName` | Query | qualifiedName, indexId | SymbolDTO | `codeIndexService.findSymbolByQualifiedName()` |
| 3 | `findPage_symbols` | Query | query?, kinds?, packageName?, indexId, offset?, limit? | PageBean<SymbolDTO> | `codeIndexService.findSymbolsPage()` |
| 4 | `getTypeHierarchy` | Query | qualifiedName, indexId, direction, maxDepth | TypeHierarchyDTO | `codeIndexService.getTypeHierarchy()` |
| 5 | `getCallHierarchy` | Query | qualifiedName, indexId, direction, maxDepth | CallHierarchyDTO | `codeIndexService.getCallHierarchy()` |
| 6 | `fileOutline` | Query | indexId, filePath | FileOutlineDTO | `codeIndexService.getFileOutline()` |
| 7 | `searchCode` | Query | indexId, query, searchType?, language?, filePattern?, limit? | List<CodeSearchResultDTO> | `codeIndexService.searchCode()` |
| 8 | `findReferencedBy` | Query | indexId, qualifiedName, kind?, limit? | List<ReferenceDTO> | `codeIndexService.findReferencedBy()` |
| 9 | `detectDeadCode` | Mutation | indexId | DeadCodeReport | `codeIndexService.detectDeadCode()` |
| 10 | `findByAnnotation` | Query | indexId, annotationName | List<SymbolDTO> | `codeIndexService.findByAnnotation()` |
| 11 | `findImplementations` | Query | indexId, qualifiedName, directOnly?, maxDepth? | List<SymbolDTO> | `codeIndexService.findImplementations()` |
| 12 | `showSymbol` | Query | indexId, qualifiedName, includeBody? | SymbolSourceDTO | `codeIndexService.showSymbolSource()` |
| 13 | `moduleDigest` | Query | indexId, dirPath, includePrivate? | List<ModuleDigestDTO> | `codeIndexService.getModuleDigest()` |
| 14 | `publicSurface` | Query | indexId, dirPath | List<PublicAPIDTO> | `codeIndexService.getPublicSurface()` |
| 15 | `batchGetOutlines` | Query | qualifiedNames, indexId | List<TypeOutlineDTO> | `codeIndexService.batchGetTypeOutlines()` |
| 16 | `usages` | BizLoader | SymbolDTO 上下文, indexId?, limit? | List<AnnotationUsageDTO> | `codeIndexService.getSymbolUsages()` |
| 17 | `sourceCode` | BizLoader | SymbolDTO 上下文, indexId?, linesBefore?, linesAfter? | String | `codeIndexService.getSymbolSourceCode()` |

#### NopCodeFileBizModel (3 个方法 + 4 个 BizLoader)

| # | 方法名 | 类型 | 参数 | 返回类型 |
|---|--------|------|------|---------|
| 1 | `getByPath` | Query | filePath, indexId | CodeFileAnalysisResult |
| 2 | `findPage_files` | Query | indexId, packageName?, offset?, limit? | PageBean<CodeFileAnalysisResult> |
| 3 | `fileTree` | Query | indexId | List<FileTreeNode> |
| 4 | `symbols` | BizLoader | CodeFileAnalysisResult 上下文 | List<CodeSymbol> |
| 5 | `types` | BizLoader | CodeFileAnalysisResult 上下文 | List<CodeSymbol> |
| 6 | `sourceCode` | BizLoader | CodeFileAnalysisResult 上下文 | String |
| 7 | `outline` | BizLoader | CodeFileAnalysisResult 上下文 | FileOutlineDTO |

---

## 附录 D：核心数据结构逐字段对比

### D.1 节点/符号模型

| 字段 | CodeGraph `Node` | nop-code `CodeSymbol` | 差异 |
|------|-----------------|----------------------|------|
| id | ✅ string (hash of file+qualifiedName+kind+line) | ✅ String | 生成方式不同 |
| kind | ✅ NodeKind (22 种字符串枚举) | ✅ CodeSymbolKind (18 种整数枚举) | CG 多 route/component/struct/protocol/property/export/enum_member；nop-code 多 ANNOTATION_TYPE/MIXIN/DECORATOR/LOCAL_VARIABLE |
| name | ✅ string | ✅ String | — |
| qualifiedName | ✅ string (e.g. `MyClass::myMethod`) | ✅ String | CG 用 `::` 分隔，nop-code 用 `.` |
| filePath | ✅ string | ❌ (存在 extData JSON 中) | nop-code 无独立字段 |
| language | ✅ Language (29 种) | ✅ CodeLanguage (4 种) | CG 覆盖远广 |
| startLine | ✅ number (1-indexed) | ✅ int | — |
| endLine | ✅ number (1-indexed) | ✅ int | — |
| startColumn | ✅ number (0-indexed) | ✅ int column | — |
| endColumn | ✅ number (0-indexed) | ✅ int endColumn | — |
| docstring | ✅ string? | ✅ String documentation | — |
| signature | ✅ string? | ✅ String | — |
| visibility | ✅ `'public'|'private'|'protected'|'internal'` | ✅ CodeAccessModifier (6 种：PUBLIC/PROTECTED/PRIVATE/PACKAGE_PRIVATE/INTERNAL/NO_MODIFIER) | nop-code 更细（多了 PACKAGE_PRIVATE, NO_MODIFIER） |
| isExported | ✅ boolean? | ❌ | — |
| isAsync | ✅ boolean? | ✅ boolean asyncFlag | — |
| isStatic | ✅ boolean? | ✅ boolean staticFlag | — |
| isAbstract | ✅ boolean? | ✅ boolean abstractFlag | — |
| decorators | ✅ string[]? | ❌ (存在 extData JSON 中) | — |
| typeParameters | ✅ string[]? | ❌ | — |
| updatedAt | ✅ number | ❌ | — |
| deprecated | ❌ | ✅ boolean | — |
| parentId | ❌ (通过 contains 边) | ✅ String | nop-code 显式字段，CG 用边 |
| declaringSymbolId | ❌ | ✅ String | — |
| superClassName | ❌ | ✅ String | — |
| finalFlag | ❌ | ✅ boolean | — |
| returnType | ❌ (通过 returns 边) | ✅ String | — |
| fieldType | ❌ (通过 type_of 边) | ✅ String | — |
| readonlyFlag | ❌ | ✅ boolean | — |
| extData | ❌ | ✅ String (JSON) | nop-code 扩展字段 |
| rawReturnType | ❌ | ✅ String | — |
| rawFieldType | ❌ | ✅ String | — |

### D.2 边模型

| 字段 | CodeGraph `Edge` | nop-code `CodeMethodCall` (示例) | 差异 |
|------|-----------------|--------------------------------|------|
| source | ✅ string (node ID) | ✅ String callerId | — |
| target | ✅ string (node ID) | ✅ String calleeId | — |
| kind | ✅ EdgeKind (12 种字符串枚举) | ❌ (每种边独立表) | CG 统一表，nop-code 分表 |
| metadata | ✅ Record<string, unknown>? | ❌ | CG 支持任意元数据 |
| line | ✅ number? | ✅ int | — |
| column | ✅ number? | ✅ int | — |
| provenance | ✅ `'tree-sitter'|'scip'|'heuristic'` | ❌ | CG 标记来源 |
| calleeQualifiedName | ❌ | ✅ String | nop-code 冗余存储 |
| methodName | ❌ | ✅ String | — |
| argumentTypes | ❌ | ✅ String | Java 特有 |
| callType | ❌ | ✅ String | — |
| context | ❌ | ✅ String | — |
| confidence | ❌ | ✅ EdgeConfidence | nop-code 标记置信度 |

### D.3 文件模型

| 字段 | CodeGraph `FileRecord` | nop-code `CodeFileAnalysisResult` | 差异 |
|------|----------------------|--------------------------------|------|
| path | ✅ string | ✅ String filePath | — |
| contentHash | ✅ string | ❌ (增量检测在 OrmFingerprintStore) | — |
| language | ✅ Language | ✅ CodeLanguage | — |
| size | ✅ number | ❌ | — |
| modifiedAt | ✅ number | ❌ | — |
| indexedAt | ✅ number | ❌ | — |
| nodeCount | ✅ number | ❌ | — |
| errors | ✅ ExtractionError[]? | ❌ | — |
| sourceCode | ❌ | ✅ String | nop-code 存源码 |
| lineCount | ❌ | ✅ int | — |
| packageName | ❌ | ✅ String | — |
| imports | ❌ | ✅ List<String> | — |
| symbols | ❌ | ✅ List<CodeSymbol> | nop-code 内联 |
| calls | ❌ | ✅ List<CodeMethodCall> | nop-code 内联 |
| inheritances | ❌ | ✅ List<CodeInheritance> | nop-code 内联 |
| annotationUsages | ❌ | ✅ List<CodeAnnotationUsage> | nop-code 内联 |
| semanticEdges | ❌ | ✅ List<CodeSemanticEdge> | nop-code 内联 |

### D.4 关键返回结构对比

#### 影响分析

| 字段 | CG `codegraph_impact` 返回 | nop-code `ImpactResultDTO` |
|------|--------------------------|---------------------------|
| 目标符号 | 在文本中隐含 | `targetSymbolId` + `targetQualifiedName` |
| 上游影响 | 在 Subgraph 中（按文件分组） | `upstream: List<ImpactedSymbolDTO>` |
| 下游影响 | 无（只做反向） | `downstream: List<ImpactedSymbolDTO>` |
| 风险评估 | 无 | `riskLevel: String` (LOW/MEDIUM/HIGH/CRITICAL) |
| 深度信息 | 在节点中间接提供 | `depth: int` per symbol |
| 文件路径 | 从 Node.filePath 获取 | `filePath: String` per symbol |

#### 调用层级

| 字段 | CG `codegraph_callers/callees` 返回 | nop-code `CallHierarchyDTO` |
|------|-------------------------------------|---------------------------|
| 结构 | 扁平列表 `{node, edge}[]` | 递归树 `{symbol, callees[], callers[]}` |
| 符号信息 | 完整 Node 对象 | `SymbolInfoDTO` (name, qualifiedName, kind, accessModifier) |
| 边信息 | 完整 Edge 对象 | 无 |
| 递归深度 | `limit` 控制总数 | `maxDepth` 参数 |

#### 搜索

| 字段 | CG `codegraph_search` 返回 | nop-code `CodeSearchResultDTO` |
|------|--------------------------|-------------------------------|
| 匹配符号名 | Node.name | `matchedSymbolName` |
| 全限定名 | Node.qualifiedName | `matchedQualifiedName` |
| 文件位置 | Node.filePath:Node.startLine | `filePath` + `line` |
| 匹配类型 | 隐含（FTS/精确） | `matchType: String` |
| 分数 | score (0-1, BM25) | `score: double` (内存评分) |
| 上下文 | — | `context: String` |
| 签名 | Node.signature | — |
| 高亮 | `highlights: string[]` | — |

---

## 附录 E：数据库 Schema 逐表逐列对比

### E.1 整体结构

| 维度 | CodeGraph (SQLite) | nop-code (ORM → 关系型数据库) |
|------|-------------------|--------------------------|
| **表数量** | 4 张核心表 + 1 FTS 虚拟表 + 1 元数据表 | 10 张业务表 |
| **设计思路** | 统一宽表：`nodes`(所有符号) + `edges`(所有关系) | 分类型窄表：每类关系独立表 |
| **ID 生成** | `nodes.id` = hash(file+qualifiedName+kind+line) | `codeId` 域，VARCHAR(36)，由 `tagSet="seq"` 生成 |
| **索引隔离** | 无（单项目 SQLite） | `indexId` 列全局存在，支持多索引多租户 |
| **逻辑删除** | 无 | `NopCodeSemanticEdge` 有 `delFlag` 逻辑删除 |
| **审计字段** | `updated_at` (INTEGER 时间戳) | 部分表有 `createdBy/updatedBy/createTime/updateTime` |

### E.2 符号/节点表

#### CodeGraph `nodes` 表 vs nop-code `nop_code_symbol` 表

| # | CG 列名 | 类型 | nop-code 列名 | 类型 | 差异说明 |
|---|---------|------|--------------|------|---------|
| 1 | `id` | TEXT PK | `ID` | VARCHAR(36) PK | CG 哈希生成，nop-code seq 生成 |
| 2 | `kind` | TEXT NOT NULL | `KIND` | VARCHAR(20) | CG 22 种字符串枚举；nop-code 18 种 + dict `code/symbol_kind` |
| 3 | `name` | TEXT NOT NULL | `NAME` | VARCHAR(200) | — |
| 4 | `qualified_name` | TEXT NOT NULL | `QUALIFIED_NAME` | VARCHAR(500) | CG 用 `::` 分隔符 |
| 5 | `file_path` | TEXT NOT NULL | — | — | nop-code 通过 `FILE_ID` 关联 `nop_code_file` |
| 6 | `language` | TEXT NOT NULL | — | — | nop-code 在 `nop_code_file` 表上存语言 |
| 7 | `start_line` | INT NOT NULL | `LINE` | INT | — |
| 8 | `end_line` | INT NOT NULL | `END_LINE` | INT | — |
| 9 | `start_column` | INT NOT NULL | `COLUMN` | INT | — |
| 10 | `end_column` | INT NOT NULL | `END_COLUMN` | INT | — |
| 11 | `docstring` | TEXT | `DOCUMENTATION` | VARCHAR(4000) | — |
| 12 | `signature` | TEXT | `SIGNATURE` | VARCHAR(2000) | — |
| 13 | `visibility` | TEXT | `ACCESS_MODIFIER` | VARCHAR(20) | CG 4 值，nop-code 6 值 + dict |
| 14 | `is_exported` | INT DEFAULT 0 | — | — | nop-code 无此概念 |
| 15 | `is_async` | INT DEFAULT 0 | `ASYNC_FLAG` | BOOLEAN | — |
| 16 | `is_static` | INT DEFAULT 0 | `IS_STATIC` | BOOLEAN | — |
| 17 | `is_abstract` | INT DEFAULT 0 | `IS_ABSTRACT` | BOOLEAN | — |
| 18 | `decorators` | TEXT (JSON array) | — | — | nop-code 存在 `extData` JSON 中 |
| 19 | `type_parameters` | TEXT (JSON array) | — | — | nop-code 存在 `extData` JSON 中 |
| 20 | `updated_at` | INT NOT NULL | — | — | — |
| 21 | — | — | `INDEX_ID` | VARCHAR(36) NOT NULL | 多租户索引隔离 |
| 22 | — | — | `FILE_ID` | VARCHAR(36) NOT NULL | FK → nop_code_file |
| 23 | — | — | `DEPRECATED` | BOOLEAN | — |
| 24 | — | — | `USAGE_COUNT` | INT | 使用次数统计 |
| 25 | — | — | `PARENT_ID` | VARCHAR(36) | FK → nop_code_symbol (self) |
| 26 | — | — | `DECLARING_SYMBOL_ID` | VARCHAR(36) | FK → nop_code_symbol (self) |
| 27 | — | — | `SUPER_CLASS_NAME` | VARCHAR(500) | 冗余存储，避免 join |
| 28 | — | — | `IS_FINAL` | BOOLEAN | — |
| 29 | — | — | `RETURN_TYPE` | VARCHAR(500) | CG 用 `returns` 边 |
| 30 | — | — | `IS_SYNCHRONIZED` | BOOLEAN | — |
| 31 | — | — | `IS_NATIVE` | BOOLEAN | — |
| 32 | — | — | `FIELD_TYPE` | VARCHAR(500) | CG 用 `type_of` 边 |
| 33 | — | — | `IS_VOLATILE` | BOOLEAN | — |
| 34 | — | — | `IS_TRANSIENT` | BOOLEAN | — |
| 35 | — | — | `EXT_DATA` | VARCHAR(4096) JSON | 扩展字段，存 typeParameters/decorators/parameters/exceptions 等 |
| 36 | — | — | `READONLY_FLAG` | BOOLEAN | — |
| 37 | — | — | `RAW_RETURN_TYPE` | VARCHAR(500) | — |
| 38 | — | — | `RAW_FIELD_TYPE` | VARCHAR(500) | — |

**Plan 116 后续更新 (2026-06-05)**：

以下变更已通过 Plan 116 落地，上述对比表中的对应行应理解为最新状态：

| 变更 | 说明 |
|------|------|
| `is_exported` → `MODIFIERS` bit 9 | nop-code 不再缺少 export 概念：`MODIFIERS` bit 9 (1<<9) = EXPORTED |
| `FILE_PATH` | `nop_code_symbol` 新增 `FILE_PATH` 反规范化列（从 `nop_code_file` 冗余） |
| `LANGUAGE` | `nop_code_symbol` 新增 `LANGUAGE` 反规范化列 |
| 所有边表新增 `PROVENANCE` | `nop_code_call`, `nop_code_usage`, `nop_code_inheritance`, `nop_code_annotation_usage`, `nop_code_semantic_edge` 均新增 `PROVENANCE` VARCHAR(20) |
| `nop_code_call`/`nop_code_usage` 新增 `METADATA` | JSON 列，存储启发式合成元数据 |
| `code/symbol_kind` 新增 `ROUTE` | value=100，HTTP 路由端点 |
| `code/reference_kind` 新增 `TYPE_OF`/`INSTANTIATES` | value=100/110 |
| `EdgeProvenance` 枚举 | `AST_EXTRACTION`, `SYMBOL_SOLVER`, `HEURISTIC`, `FRAMEWORK_INFERENCE`, `MANUAL` |
| 启发式边合成引擎 | `InterfaceImplSynthesizer` + `SpringEventSynthesizer` |
| Spring 路由提取 | `@RequestMapping`/`@GetMapping` 等注解解析 |

**索引对比**：

| CG 索引 | nop-code 索引 |
|---------|-------------|
| `idx_nodes_kind` (kind) | `ix_nop_code_symbol_index_id_kind` (indexId, kind) |
| `idx_nodes_name` (name) | `ix_nop_code_symbol_index_id_name` (indexId, name) |
| `idx_nodes_qualified_name` (qualified_name) | `ix_nop_code_symbol_index_id_qualified_name` (indexId, qualifiedName) |
| `idx_nodes_file_path` (file_path) | `ix_nop_code_symbol_file_id` (fileId) |
| `idx_nodes_language` (language) | — |
| `idx_nodes_file_line` (file_path, start_line) | — |
| `idx_nodes_lower_name` (lower(name)) | — (FTS5 覆盖) |
| FTS5 `nodes_fts` | — |
| — | `ix_nop_code_symbol_index_id_declaring_symbol_id` (indexId, declaringSymbolId) |
| — | `ix_nop_code_symbol_parent_id` (parentId) |

**关键差异**：
- nop-code 所有索引都带 `indexId` 前缀（多租户设计）
- CG 有 FTS5 全文索引 + `lower(name)` 大小写不敏感索引
- nop-code 符号表有 38 列（含 Java 特有：synchronized/native/volatile/transient），CG 只有 20 列但更通用
- nop-code 的 `EXT_DATA` JSON 字段是核心扩展机制，存放各种语言特有信息（typeParameters、decorators、parameters、exceptions 等）

**⚠️ nop-code 符号表设计问题 — boolean 列膨胀**：

`nop_code_symbol` 有 9 个独立 boolean 标记列（IS_ABSTRACT/IS_FINAL/IS_STATIC/IS_SYNCHRONIZED/IS_NATIVE/IS_VOLATILE/IS_TRANSIENT/ASYNC_FLAG/READONLY_FLAG），应合并为：
- **方案 A**：单个 `MODIFIERS` int 列（bitmask，同 `java.lang.reflect.Modifier`），减少列数
- **方案 B**：移入已有的 `EXT_DATA` JSON 字段（`{"modifiers":["abstract","static","synchronized"]}`），随语言灵活扩展

当前设计的问题：
- Python/TS 符号有 ~5 列永远为 FALSE（synchronized/native/volatile/transient 毫无意义）
- 新增语言特有修饰符（Kotlin `suspend`/`open`/`data`，Python `@classmethod`）必须加列
- CG 的 Node 模型用 4 个 optional boolean（isAsync/isStatic/isAbstract/isExported）+ `decorators[]` + `typeParameters[]` 解决了同样问题，更简洁

### E.3 边/关系表

#### CodeGraph `edges` 表 vs nop-code 分类型边表

**CG 统一边表**：

| # | 列名 | 类型 | 说明 |
|---|------|------|------|
| 1 | `id` | INTEGER PK AUTOINCREMENT | 自增 |
| 2 | `source` | TEXT NOT NULL FK→nodes | 源节点 |
| 3 | `target` | TEXT NOT NULL FK→nodes | 目标节点 |
| 4 | `kind` | TEXT NOT NULL | 12 种 EdgeKind |
| 5 | `metadata` | TEXT (JSON) | 任意元数据 |
| 6 | `line` | INTEGER | 源码行号 |
| 7 | `col` | INTEGER | 源码列号 |
| 8 | `provenance` | TEXT DEFAULT NULL | 'tree-sitter'/'scip'/'heuristic' |

索引：`idx_edges_kind`, `idx_edges_source_kind`, `idx_edges_target_kind`, `idx_edges_provenance`

**nop-code 分类型边表（4 张）**：

##### nop_code_call（方法调用）— 9 列

| # | 列名 | 类型 | 说明 |
|---|------|------|------|
| 1 | `ID` | VARCHAR(36) PK | — |
| 2 | `INDEX_ID` | VARCHAR(36) NOT NULL | 索引隔离 |
| 3 | `CALLER_ID` | VARCHAR(36) NOT NULL FK→symbol | 调用方 |
| 4 | `CALLEE_ID` | VARCHAR(36) NOT NULL FK→symbol | 被调用方 |
| 5 | `FILE_ID` | VARCHAR(36) NOT NULL FK→file | 所在文件 |
| 6 | `LINE` | INT NOT NULL | — |
| 7 | `COLUMN` | INT | — |
| 8 | `CALL_TYPE` | VARCHAR(20) | 调用类型 |
| 9 | `CONTEXT` | VARCHAR(2000) | 调用上下文 |

UK: `(indexId, callerId, calleeId, line, column)`
索引: `callerId`, `calleeId`, `fileId`, `indexId`

##### nop_code_inheritance（继承关系）— 5 列

| # | 列名 | 类型 | 说明 |
|---|------|------|------|
| 1 | `ID` | VARCHAR(36) PK | — |
| 2 | `INDEX_ID` | VARCHAR(36) NOT NULL | — |
| 3 | `SUB_TYPE_ID` | VARCHAR(36) NOT NULL FK→symbol | 子类型 |
| 4 | `SUPER_TYPE_ID` | VARCHAR(36) NOT NULL FK→symbol | 父类型 |
| 5 | `RELATION_TYPE` | VARCHAR(20) NOT NULL | EXTENDS / IMPLEMENTS |

UK: `(indexId, subTypeId, superTypeId, relationType)`

##### nop_code_usage（符号引用）— 9 列

| # | 列名 | 类型 | 说明 |
|---|------|------|------|
| 1 | `ID` | VARCHAR(36) PK | — |
| 2 | `INDEX_ID` | VARCHAR(36) NOT NULL | — |
| 3 | `SYMBOL_ID` | VARCHAR(36) NOT NULL FK→symbol | 被引用符号 |
| 4 | `FILE_ID` | VARCHAR(36) NOT NULL FK→file | 引用所在文件 |
| 5 | `KIND` | VARCHAR(20) NOT NULL | dict `code/reference_kind`：READ/WRITE/CALL/TYPE_REFERENCE/EXTENDS/IMPLEMENTS/ANNOTATES/IMPORTS/OVERRIDES |
| 6 | `LINE` | INT NOT NULL | — |
| 7 | `COLUMN` | INT | — |
| 8 | `ENCLOSING_SYMBOL_ID` | VARCHAR(36) FK→symbol | 引用所在的符号 |
| 9 | `CONTEXT` | VARCHAR(1000) | 引用上下文 |

UK: `(indexId, symbolId, fileId, kind, line, column)`

##### nop_code_annotation_usage（注解使用）— 7 列

| # | 列名 | 类型 | 说明 |
|---|------|------|------|
| 1 | `ID` | VARCHAR(36) PK | — |
| 2 | `INDEX_ID` | VARCHAR(36) NOT NULL | — |
| 3 | `ANNOTATION_TYPE_ID` | VARCHAR(36) NOT NULL FK→symbol | 注解类型符号 |
| 4 | `ANNOTATED_SYMBOL_ID` | VARCHAR(36) FK→symbol | 被注解符号 |
| 5 | `LINE` | INT | — |
| 6 | `COLUMN` | INT | — |
| 7 | `ATTRIBUTES` | VARCHAR(4096) JSON | 注解属性 {name:value,...} |

UK: `(indexId, annotationTypeId, annotatedSymbolId)`

**nop-code 独有边表（CG 无对应）**：

##### nop_code_dependency（文件依赖）— 6 列

| # | 列名 | 类型 | 说明 |
|---|------|------|------|
| 1 | `ID` | VARCHAR(36) PK | — |
| 2 | `INDEX_ID` | VARCHAR(36) NOT NULL | — |
| 3 | `SOURCE_FILE_PATH` | VARCHAR(500) NOT NULL | 源文件 |
| 4 | `TARGET_FILE_PATH` | VARCHAR(500) | 目标文件 |
| 5 | `IMPORT_STATEMENT` | VARCHAR(500) | 导入语句原文 |
| 6 | `RESOLVED` | BOOLEAN | 是否已解析 |

##### nop_code_flow（执行流）— 18 列

| # | 列名 | 类型 | 说明 |
|---|------|------|------|
| 1 | `ID` | VARCHAR(36) PK | — |
| 2 | `INDEX_ID` | VARCHAR(36) NOT NULL | — |
| 3 | `NAME` | VARCHAR(200) | 流名称 |
| 4 | `ENTRY_POINT_ID` | VARCHAR(36) FK→symbol | 入口符号 |
| 5 | `ENTRY_POINT_QUALIFIED_NAME` | VARCHAR(500) | 冗余存储 |
| 6 | `DEPTH` | INT | 最大追踪深度 |
| 7 | `SYMBOL_COUNT` | INT | 参与符号数 |
| 8 | `FILE_SPREAD` | DOUBLE | 文件扩散评分 |
| 9 | `EXTERNAL_SCORE` | DOUBLE | 外部调用评分 |
| 10 | `SECURITY_SCORE` | DOUBLE | 安全敏感评分 |
| 11 | `TEST_GAP` | DOUBLE | 测试覆盖缺口 |
| 12 | `DEPTH_SCORE` | DOUBLE | 深度复杂度评分 |
| 13 | `OVERALL_SCORE` | DOUBLE | 综合关键度评分 |
| 14 | `STATUS` | VARCHAR(20) | dict `code/index_status` |
| 15-18 | `CREATED_TIME`/`UPDATE_TIME`/`CREATED_BY`/`UPDATED_BY` | TIMESTAMP/VARCHAR | 审计字段 |

##### nop_code_flow_membership（执行流-符号关联）— 9 列

| # | 列名 | 类型 | 说明 |
|---|------|------|------|
| 1 | `ID` | VARCHAR(36) PK | — |
| 2 | `FLOW_ID` | VARCHAR(36) NOT NULL FK→flow | — |
| 3 | `SYMBOL_ID` | VARCHAR(36) NOT NULL FK→symbol | — |
| 4 | `DEPTH` | INT | 符号在流中的深度 |
| 5 | `IS_ENTRY` | BOOLEAN | 是否入口点 |
| 6-9 | 审计字段 | — | — |

UK: `(flowId, symbolId)`

##### nop_code_semantic_edge（语义边）— 14 列

| # | 列名 | 类型 | 说明 |
|---|------|------|------|
| 1 | `ID` | VARCHAR(36) PK | — |
| 2 | `INDEX_ID` | VARCHAR(36) NOT NULL | — |
| 3 | `SOURCE_SYMBOL_ID` | VARCHAR(36) NOT NULL FK→symbol | — |
| 4 | `TARGET_SYMBOL_ID` | VARCHAR(36) NOT NULL FK→symbol | — |
| 5 | `DIRECTED` | BOOLEAN | 有向/无向 |
| 6 | `RELATION_TYPE` | VARCHAR(40) NOT NULL | SEMANTICALLY_SIMILAR_TO 等 8 种 |
| 7 | `CONFIDENCE` | INT NOT NULL | EdgeConfidence (10/20/30) |
| 8 | `CONFIDENCE_SCORE` | DOUBLE | 0.0-1.0 分数 |
| 9 | `RATIONALE` | VARCHAR(500) | 关系原因描述 |
| 10 | `EXTRACTOR_ID` | VARCHAR(40) | 提取器标识 |
| 11 | `EXT_DATA` | VARCHAR(4096) JSON | 扩展数据 |
| 12-13 | `CREATED_BY`/`CREATE_TIME` | — | 审计字段 |
| 14 | `DEL_FLAG` | TINYINT | 逻辑删除 |

UK: `(indexId, sourceSymbolId, targetSymbolId, relationType)`

### E.4 文件表

| # | CG `files` 列 | 类型 | nop-code `nop_code_file` 列 | 类型 | 差异 |
|---|---------------|------|---------------------------|------|------|
| 1 | `path` | TEXT PK | `FILE_PATH` | VARCHAR(500) | nop-code 非 PK，UK(indexId, filePath) |
| 2 | `content_hash` | TEXT NOT NULL | `FILE_HASH` | VARCHAR(64) | — |
| 3 | `language` | TEXT NOT NULL | `LANGUAGE` | VARCHAR(20) | — |
| 4 | `size` | INT NOT NULL | `FILE_SIZE` | BIGINT | — |
| 5 | `modified_at` | INT NOT NULL | `LAST_MODIFIED` | BIGINT | — |
| 6 | `indexed_at` | INT NOT NULL | — | — | — |
| 7 | `node_count` | INT DEFAULT 0 | — | — | — |
| 8 | `errors` | TEXT JSON | — | — | — |
| 9 | — | — | `ID` | VARCHAR(36) PK | — |
| 10 | — | — | `INDEX_ID` | VARCHAR(36) NOT NULL | 索引隔离 |
| 11 | — | — | `PACKAGE_NAME` | VARCHAR(200) | — |
| 12 | — | — | `LINE_COUNT` | INT | — |
| 13 | — | — | `IMPORTS` | VARCHAR(8192) JSON | JSON 数组 |
| 14 | — | — | `SOURCE_CODE` | CLOB | 存储完整源码 |

### E.5 未解析引用表（CG 独有）

| # | CG `unresolved_refs` 列 | 类型 | nop-code 对应 |
|---|------------------------|------|-------------|
| 1 | `id` | INTEGER PK | ❌ 无对应表 |
| 2 | `from_node_id` | TEXT FK→nodes | — |
| 3 | `reference_name` | TEXT NOT NULL | — |
| 4 | `reference_kind` | TEXT NOT NULL | — |
| 5 | `line` | INT NOT NULL | — |
| 6 | `col` | INT NOT NULL | — |
| 7 | `candidates` | TEXT JSON | — |
| 8 | `file_path` | TEXT | — |
| 9 | `language` | TEXT | — |

nop-code 无此概念 — Java 通过 SymbolSolver 在解析时直接完成符号解析，不需要后处理解析阶段。Python/TS 的调用关系在 AST 遍历时直接提取。

### E.6 元数据/索引管理表

| # | CG `project_metadata` | nop-code `nop_code_index` |
|---|----------------------|--------------------------|
| 结构 | key-value 单行 | 完整索引实体（9 列 + 9 个 to-many 关系） |
| 列 | `key` TEXT PK, `value` TEXT, `updated_at` INT | `ID`, `NAME`, `ROOT_PATH`, `LANGUAGE`, `SYMBOL_COUNT`, `FILE_COUNT`, `STATUS`(6 种状态), `LAST_INDEXED`, `INDEX_VERSION` |
| 状态管理 | 无 | 有完整的索引生命周期状态机（CREATED→INDEXING→READY/ERROR→COMPLETED） |

### E.7 索引管理表（nop-code 独有）

| 表名 | 列数 | 作用 |
|------|------|------|
| `nop_code_index` | 9 | 索引元数据（名称、根路径、语言、符号/文件计数、状态、版本） |

### E.8 ORM 关系图（nop-code 独有）

nop-code 的 ORM 模型定义了丰富的实体间关系，CG 无此层：

```
NopCodeIndex (1) ──→ (N) NopCodeFile       (cascadeDelete)
               ──→ (N) NopCodeSymbol      (cascadeDelete)
               ──→ (N) NopCodeCall        (cascadeDelete)
               ──→ (N) NopCodeInheritance  (cascadeDelete)
               ──→ (N) NopCodeUsage       (cascadeDelete)
               ──→ (N) NopCodeDependency   (cascadeDelete)
               ──→ (N) NopCodeAnnotationUsage (cascadeDelete)
               ──→ (N) NopCodeFlow        (cascadeDelete)
               ──→ (N) NopCodeSemanticEdge (逻辑删除)

NopCodeFile (1) ──→ (N) NopCodeSymbol  (by fileId)
            (1) ──→ (N) NopCodeUsage   (by fileId)
            (1) ──→ (N) NopCodeCall    (by fileId)

NopCodeSymbol (1) ──→ (N) NopCodeSymbol   (children, by parentId, self-ref)
              (1) ──→ (N) NopCodeSymbol   (members, by declaringSymbolId, self-ref)
              (1) ──→ (N) NopCodeUsage    (usages, by symbolId)
              (1) ──→ (N) NopCodeCall     (callees, by callerId)
              (1) ──→ (N) NopCodeCall     (callers, by calleeId)
              (1) ──→ (N) NopCodeInheritance (superTypes, by subTypeId)
              (1) ──→ (N) NopCodeInheritance (subTypes, by superTypeId)
              (1) ──→ (N) NopCodeAnnotationUsage (annotations, by annotatedSymbolId)
              (1) ──→ (N) NopCodeFlow    (entryFlows, by entryPointId)
              (1) ──→ (N) NopCodeFlowMembership (flowMemberships, by symbolId)

NopCodeFlow (1) ──→ (N) NopCodeFlowMembership (cascadeDelete)
           (1) ──→ (1) NopCodeSymbol (entryPoint)

NopCodeSemanticEdge (1) ──→ (1) NopCodeSymbol (sourceSymbol)
                   (1) ──→ (1) NopCodeSymbol (targetSymbol)
```

CG 无 ORM 关系层 — 所有关系通过 SQL JOIN 和应用层代码维护。

### E.9 域（Domain）定义对比

**nop-code 定义了 10 个可复用域**：

| 域名 | 精度 | SQL 类型 | 用途 |
|------|------|---------|------|
| `codeId` | 36 | VARCHAR | 所有 ID 字段 |
| `filePath` | 500 | VARCHAR | 文件路径 |
| `qualifiedName` | 500 | VARCHAR | 全限定名、返回类型、字段类型 |
| `symbolName` | 200 | VARCHAR | 符号名称 |
| `packageName` | 200 | VARCHAR | 包名 |
| `signature` | 2000 | VARCHAR | 方法签名 |
| `documentation` | 4000 | VARCHAR | 文档注释 |
| `language` | 20 | VARCHAR | 编程语言 |
| `lineNumber` | — | INTEGER | 行号 |
| `columnNumber` | — | INTEGER | 列号 |
| `jsonContent` | 4096 | VARCHAR(JSON) | extData/attributes |
| `jsonImport` | 8192 | VARCHAR(JSON) | imports 列表 |

CG 无域定义概念 — 列类型直接使用 SQLite 原生类型（TEXT/INTEGER）。

### E.10 字典（Dict）定义对比

**nop-code 定义了 6 个枚举字典**：

| 字典名 | 值类型 | 选项数 | 用途 |
|--------|--------|--------|------|
| `code/symbol_kind` | string | 17 | 符号类型 |
| `code/access_modifier` | string | 6 | 访问修饰符 |
| `code/reference_kind` | string | 9 | 引用类型 |
| `code/index_status` | string | 6 | 索引状态 |
| `code/language` | string | 4 | 编程语言 |
| `code/relation_type` | string | 2 | 继承关系类型 |

CG 无字典概念 — 枚举值作为字符串直接存入 `kind` 列。

---

## References

- CodeGraph 仓库：https://github.com/colbymchenry/codegraph
- CodeGraph 文档：https://colbymchenry.github.io/codegraph/
- nop-code 源码：`nop-code/` 模块
- nop-code 设计文档：`ai-dev/design/nop-code/`
- CRG 对比分析：`ai-dev/analysis/2026-05-25-code-review-graph-vs-nop-code.md`
- Nop GraphQL API 指南：`docs-for-ai/02-core-guides/api-and-graphql.md`
