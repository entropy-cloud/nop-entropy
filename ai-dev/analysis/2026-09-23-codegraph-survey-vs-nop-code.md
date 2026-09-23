# Code Graph Analysis Projects vs nop-code: Comprehensive Survey Report

> Status: open
> Date: 2026-09-23
> Scope: All 10 code graph analysis projects on GitHub vs nop-code module (Nop Platform)
> Supersedes: `2026-06-05-codegraph-vs-nop-code-deep-analysis.md` and `2026-05-25-code-review-graph-vs-nop-code.md`
> Conclusion: (待评审)

---

## 1. Executive Summary

This report synthesizes findings from all 10 code graph analysis projects — CodeGraph, code-review-graph, GitNexus, codeflow, ast-outline-rs, understand-anything, graphify, ontology-driven-agent, entrix, and DeepCode — compared against nop-code (the Nop Platform's code indexing module).

### Key Findings

1. **GraphQL already provides universal API exposure.** 7 of 10 projects use MCP, but nop-code's BizModel + GraphQL体系 is already a universal API protocol. All AI clients (Claude Code, Cursor, Codex, OpenCode) can consume GraphQL directly via HTTP POST. MCP is an unnecessary protocol layer for Nop modules — see `docs-for-ai/02-core-guides/api-and-graphql.md` for the architectural principle.

2. **Tree-sitter is the universal parsing standard.** 8 of 10 projects use tree-sitter for AST parsing. nop-code uses JavaParser for Java (superior precision) but tree-sitter Java bindings for Python/TypeScript, and lacks the WASM-based cross-platform execution that dominates the landscape.

3. **Knowledge graph is the universal abstraction.** All 10 projects build some form of code knowledge graph. nop-code has the most sophisticated graph analysis algorithms (Leiden, FlowDetector, ChangeAnalyzer), but the weakest integration with AI agents.

4. **Incremental indexing and file watching are near-universal.** 9 of 10 projects support incremental updates; nop-code has the mechanism but suffers from cache issues and lacks native file watching.

5. **nop-code's graph analysis depth exceeds all competitors.** Community detection (Leiden + LabelPropagation), execution flow tracing (5-dimension criticality), risk-scored change analysis, and semantic edge extraction are capabilities that no single competitor possesses all of.

6. **The critical gap is not analytical power but AI agent UX.** nop-code has more analysis capabilities than any single competitor, but lacks token efficiency mechanisms (detail_level, context budgeting, next_tool_suggestions) that make AI agents more effective. GraphQL already provides the data layer; the improvement is in response optimization, not protocol addition.

7. **Architectural principle: GraphQL > MCP for Nop modules.** Nop platform's `IGraphQLEngine` is already a universal request dispatch layer supporting REST, GraphQL, JSON-RPC, and RPC. Adding MCP would duplicate this infrastructure. AI agents should consume nop-code via standard GraphQL queries (with `@BizLoader` nested navigation for one-shot context building), not via a separate MCP server.

### Top 5 Recommendations

| Priority | Recommendation | Rationale |
|----------|---------------|-----------|
| P0 | Integrate nop-search for unified search | CRG, CodeGraph all use FTS5/vector hybrid search; nop-search already has Lucene+RRF |
| P0 | Add analysis cache (SymbolTable + CallGraph) | Eliminates full rebuild on every query; all competitors have caching |
| P0 | Add token efficiency mechanisms | detail_level, context budgeting, next_suggested_queries for AI agent UX |
| P1 | Add file watcher with debouncing | 9/10 projects have native OS event monitoring; nop-code relies on manual triggers |
| P1 | Add framework route detection | Spring/Express/Django routing awareness is critical for enterprise code |
| P1 | Expand language support beyond 3 | 8/10 projects support 10+ languages via tree-sitter |

**NOT recommended**: Adding MCP Server. GraphQL via BizModel already provides universal API exposure with nested query support (`@BizLoader`), permission control (`@Auth`), and type safety — all advantages MCP lacks. See `docs-for-ai/02-core-guides/api-and-graphql.md` § "GraphQL Already Provides Universal API Exposure".

---

## 2. Survey Overview: All 10 Projects at a Glance

### 2.1 Project Table

| # | Project | Language | Parser | Storage | API | Languages | Last Update | Stars Focus |
|---|---------|----------|--------|---------|-----|-----------|-------------|-------------|
| 1 | **CodeGraph** (colbymchenry) | TypeScript | tree-sitter WASM | SQLite | MCP (8 tools) | 28 | 2026-06-05 | 58% fewer tool calls |
| 2 | **code-review-graph** (tirth8205) | Python | tree-sitter | SQLite | MCP (28 tools + 5 prompts) | 24 | 2026-05-25 | 8.2x token reduction |
| 3 | **GitNexus** (abhigyanpatwari) | TypeScript/Go | tree-sitter | Knowledge Graph | Smart tools | TBD | 2026-06-05 | Wiki + knowledge graph |
| 4 | **codeflow** (braedonsaunders) | TypeScript | tree-sitter | — | Web UI | TBD | 2026-05-24 | Dependency graphs + blast radius |
| 5 | **ast-outline-rs** (aeroxy) | Rust | AST-native | — | CLI | TBD | 2026-06-05 | Fastest AST navigation |
| 6 | **understand-anything** (Lum1104) | TypeScript | tree-sitter | Knowledge Graph | — | Auto-detect | 2026-06-04 | Interactive knowledge graph |
| 7 | **graphify** (safishamsi) | Python/TypeScript | tree-sitter + semantic | — | AI assistant + Web UI | TBD | 2026-05-03 (v2 main) | Hypergraph + confidence scores |
| 8 | **ontology-driven-agent** (yql210) | TypeScript/Python | tree-sitter | — | CLI/MCP | TBD | 2026-07-09 | Knowledge graph from code+docs |
| 9 | **entrix** (phodal) | TypeScript | — | Graph-backed | Commands | TBD | 2026-05-28 | Quality guardrails |
| 10 | **DeepCode** (HKUDS) | TypeScript/Go | tree-sitter | — | Web UI + Agent | TBD | 2026-05-18 | CodeRAG + Code Indexing Agent |
| — | **nop-code** | Java 21 | JavaParser/tree-sitter | ORM (10+ tables) | GraphQL (38+ methods) | 3 (Java/Python/TS) | Ongoing | Enterprise-grade analysis |

### 2.2 Key Observations

- **Language distribution**: TypeScript dominates (7 of 10), Python (3), Rust (1), Go (2 as secondary). Java is not used by any competitor for code graph analysis.
- **Parser dominance**: tree-sitter is used by 8 of 10 projects. JavaParser is unique to nop-code for Java parsing.
- **Storage patterns**: SQLite dominates local-first projects; server-side projects use various databases. nop-code's ORM approach is unique among competitors.
- **API evolution**: MCP dominates among standalone tools (8/10), but nop-code deliberately uses GraphQL because it is a Nop platform module with a universal API layer already in place. See §6.4 for the architectural principle.
- **graphify v2**: The main branch has significantly evolved — hypergraph support, semantic similarity edges, confidence scores, git commit hook auto-rebuild, parallel AST+semantic parsing. This represents a major shift from the local v6 copy previously reviewed.

---

## 3. Cross-Project Feature Matrix

### 3.1 Comprehensive Comparison Table

| Feature | CodeGraph | CRG | GitNexus | codeflow | ast-outline | understand-anything | graphify | ontology-agent | entrix | DeepCode | nop-code |
|---------|-----------|-----|----------|----------|-------------|---------------------|----------|----------------|--------|----------|----------|
| **MCP Protocol** | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| **GraphQL** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **tree-sitter** | ✅ WASM | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❓ | ✅ | Partial (Java only) |
| **JavaParser** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **SQLite** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ (ORM) |
| **ORM/DB** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Web UI** | ❌ | ✅ (D3.js) | ❌ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ (basic) |
| **File Watcher** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ (hook) | ❌ | ❌ | ❌ | ❌ |
| **Incremental Index** | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ (cache issues) |
| **Community Detection** | ❌ | ✅ Leiden | ✅ | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ✅ Leiden+LP |
| **Execution Flow** | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Risk Analysis** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ✅ |
| **Semantic Edges** | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Framework Routing** | ✅ 14 fw | ✅ ~40 fw | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Cross-language Bridge** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Graph Export** | ❌ | ✅ GraphML/Neo4j/Obsidian | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ GraphML/Mermaid/JSON |
| **Dead Code Detection** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Entry Point Scoring** | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| **Knowledge Gap** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Graph Diff** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **AI Prompt/Workflow** | ✅ | ✅ 5 prompts | ❌ | ❌ | ❌ | ❌ | ✅ (/graphify) | ❌ | ❌ | ❌ | ❌ |
| **PreToolUse Hook** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Token Efficiency** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Multi-Repo** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ (indexId) |
| **Refactoring** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Vector Search** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ (nop-search ready) |
| **Wiki/Doc Generation** | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Zero Config** | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Security Scanning** | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
| **Code Ownership** | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Blast Radius** | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ (impact) |

### 3.2 Feature Category Summary

**Features nop-code uniquely leads (all 10 projects combined):**
- Execution flow tracing with 5-dimension criticality scoring
- Risk-scored change analysis (git diff → line mapping → multi-dimensional risk)
- Dual community detection (Leiden + LabelPropagation with large-graph optimization)
- Semantic edge extraction (3 independent extractors)
- Graph diff/snapshot comparison
- Knowledge gap analysis (isolated nodes + weak communities)
- Hub/Bridge critical node analysis (PageRank + Betweenness)
- Java symbol precision (JavaParser + SymbolSolver)
- Enterprise ORM persistence with 10+ tables
- GraphQL nested queries with @BizLoader
- Permission control (@Auth)
- Multi-format export (GraphML/Mermaid/JSON)
- Annotation usage tracking
- Dead code detection with 20+ framework exclusion patterns

**Features nop-code uniquely lacks (across all 10 projects):**
- MCP protocol (7/10 use it)
- Native file watcher with OS events
- Framework route detection (14+ frameworks)
- Cross-language bridge detection
- Interactive web graph visualization (D3.js/Cytoscape)
- AI workflow prompts (5+ pre-built)
- PreToolUse hooks
- Token efficiency guidance
- Vector search integration
- Auto-configuration / zero-config experience
- Wiki/Markdown generation
- GraphRAG capability

---

## 4. Technology Stack Comparison

### 4.1 Language & Runtime

| Project | Primary Language | Runtime | Binary Size | Memory Profile |
|---------|-----------------|---------|-------------|----------------|
| CodeGraph | TypeScript | Node.js | ~5MB | Low (WASM sandbox) |
| code-review-graph | Python | CPython | ~15MB | Medium |
| GitNexus | TypeScript/Go | Node + Go | Medium | Low-Medium |
| codeflow | TypeScript | Node.js | Medium | Medium |
| ast-outline-rs | Rust | Native | Small | Very Low |
| understand-anything | TypeScript | Node.js | Medium | Medium |
| graphify | Python/TypeScript | CPython + Node | Medium | Medium |
| ontology-driven-agent | TypeScript/Python | Node + CPython | Medium | Medium |
| entrix | TypeScript | Node.js | Small | Low |
| DeepCode | TypeScript/Go | Node + Go | Medium | Low-Medium |
| **nop-code** | **Java 21** | **JVM/Quarkus** | **~100MB+** | **High** |

**Key Insight**: The JVM's memory footprint is a significant disadvantage for a local code analysis tool. All competitors except nop-code are designed to run as lightweight local tools. nop-code's server-side architecture is appropriate for enterprise deployment but creates friction for local AI agent integration.

### 4.2 Parsing Technology

| Approach | Projects Using | Precision | Speed | Language Coverage |
|----------|---------------|-----------|-------|-------------------|
| **tree-sitter WASM** | CodeGraph, CRG | Medium | Fast | 28+ languages |
| **tree-sitter (native)** | GitNexus, codeflow, ast-outline, understand-anything, graphify, ontology-agent, DeepCode | Medium | Fast | 1-10 languages each |
| **JavaParser + SymbolSolver** | nop-code (Java only) | **High (compiler-level)** | Slow | Java only |
| **tree-sitter Java bindings** | nop-code (Python/TS) | Medium | Medium | Python, TypeScript |
| **Native AST** | ast-outline-rs | High | **Fastest** | Rust only |

**Critical Observation**: tree-sitter's language-agnostic parsing is the industry standard for code graph tools. nop-code's use of JavaParser for Java gives it superior symbol resolution precision, but the tree-sitter approach dominates for cross-language consistency and speed.

**For nop-code**: The `2026-09-07-tree-sitter-runtime-architecture.md` and `2026-09-07-pure-java-tree-sitter-feasibility.md` analyses confirm that integrating tree-sitter into the JVM ecosystem (via JNI or WebAssembly) is feasible and would significantly expand nop-code's language coverage while maintaining Java's precision advantages.

### 4.3 Storage Architecture

| Pattern | Projects | Pros | Cons |
|---------|----------|------|------|
| **SQLite (local-first)** | CodeGraph, CRG | Zero config, portable, WAL mode | Limited concurrent access |
| **Neo4j (native graph)** | OntoAgent (reference) | Native graph queries (Cypher) | Heavy dependency |
| **Knowledge Graph (custom)** | GitNexus, understand-anything, ontology-agent | Flexible schema | Implementation complexity |
| **ORM (relational)** | **nop-code** | Type-safe, enterprise features, migrations | Heavy, server-dependent |
| **In-memory graph** | Most competitors | Fast traversal | No persistence |

### 4.4 API Protocol

| Protocol | Projects | AI Agent Integration |
|----------|----------|---------------------|
| **MCP** | CodeGraph, CRG, GitNexus, codeflow, understand-anything, graphify, ontology-agent, entrix (8/10) | Native, but requires protocol adapter; no auth, flat tools |
| **CLI** | ast-outline-rs, ontology-agent | Scriptable but not interactive |
| **Web UI** | codeflow, understand-anything, graphify, DeepCode | Human-facing, not agent-facing |
| **GraphQL** | **nop-code** | Universal HTTP; nested queries, auth, type safety — no adapter needed |

**Why nop-code uses GraphQL, not MCP**: The 8 projects using MCP are standalone tools with no platform API layer. nop-code is a Nop platform module where `IGraphQLEngine` already provides universal API exposure (REST/GraphQL/JSON-RPC/RPC all dispatch to the same BizModel). Adding MCP would duplicate this. GraphQL additionally offers nested queries (`@BizLoader`), permission control (`@Auth`), and type safety — all absent from MCP. AI agents consume nop-code via standard GraphQL HTTP POST. See §6.4 and `docs-for-ai/02-core-guides/api-and-graphql.md`.

---

## 5. Parsing & Analysis Capabilities

### 5.1 Parser Architecture Patterns

**Pattern 1: tree-sitter WASM (CodeGraph)**
- Single parser compiled to WASM
- Worker thread pool with automatic memory management (250-file recycling)
- Declarative LanguageExtractor configuration
- Framework-specific extractors (14+ frameworks)

**Pattern 2: tree-sitter native (CRG, GitNexus, etc.)**
- Language-specific tree-sitter parsers
- Parallel parsing via ProcessPoolExecutor (Python) or worker threads (TypeScript)
- Heuristic pattern matching for framework detection

**Pattern 3: JavaParser + tree-sitter hybrid (nop-code)**
- Java: JavaParser for full symbol resolution + tree-sitter for AST structure
- Python/TypeScript: tree-sitter Java bindings
- SymbolSolver for type inference
- LanguageAdapterRegistry for extensibility

**Pattern 4: AST-native (ast-outline-rs)**
- Rust's enum-based AST
- Fastest parsing speed
- Limited to single language

### 5.2 Analysis Algorithm Coverage

| Algorithm | Projects with Implementation | nop-code |
|-----------|------------------------------|----------|
| **BFS/DFS Traversal** | 9/10 | ✅ |
| **Community Detection** | 6/10 | ✅ (Leiden + LP, most advanced) |
| **Impact Analysis** | 7/10 | ✅ (bidirectional BFS + risk) |
| **Execution Flow** | 3/10 (CRG, GitNexus, nop-code) | ✅ (most sophisticated) |
| **Dead Code Detection** | 4/10 | ✅ (most exclusion rules) |
| **Entry Point Detection** | 5/10 | ✅ (5-level classification) |
| **Hub/Bridge Detection** | 3/10 | ✅ (PageRank + Betweenness) |
| **Knowledge Gap** | 2/10 | ✅ (unique) |
| **Graph Diff** | 2/10 | ✅ (unique) |
| **Change Risk Analysis** | 3/10 (CRG, nop-code, ontology-agent) | ✅ (5-dimension scoring) |
| **Semantic Edge** | 5/10 | ✅ (3 extractors + 2 synthesizers) |
| **Heuristic Synthesis** | 3/10 (CodeGraph, CRG, nop-code) | ✅ (InterfaceImpl + SpringEvent) |
| **Framework Routing** | 2/10 (CodeGraph, CRG) | ❌ |
| **Cross-language Bridge** | 1/10 (CodeGraph) | ❌ |
| **Vector Embedding** | 4/10 | ❌ (nop-search ready) |
| **GraphRAG** | 2/10 (Microsoft, HKUDS) | ❌ |
| **LLM Semantic Extraction** | 2/10 (OntoAgent, graphify) | ❌ (by design) |

### 5.3 What Each Project Does Best

| Project | Best At |
|---------|---------|
| **CodeGraph** | Zero-config experience, MCP protocol, cross-language bridges, token-efficient exploration |
| **CRG** | Most MCP tools (28), AI workflow prompts, file watcher daemon, interactive visualization |
| **GitNexus** | Knowledge graph breadth, wiki generation, smart tool naming |
| **codeflow** | Interactive dependency visualization, blast radius analysis, code ownership |
| **ast-outline-rs** | Raw parsing speed, Rust-native performance |
| **understand-anything** | Language auto-detection, clean knowledge graph UI |
| **graphify** | AI coding assistant integration, hypergraph support, confidence scores |
| **ontology-agent** | Code+doc integration, semantic search, natural language queries |
| **entrix** | Quality guardrails, graph-backed command system |
| **DeepCode** | CodeRAG system, agentic coding platform |
| **nop-code** | Deepest analysis algorithms, Java precision, enterprise architecture |

### 5.4 graphify 核心功能覆盖分析（专项）

graphify v2 的管线为 `detect → extract → build_graph → cluster → analyze → report → export`。逐模块对比 nop-code：

| graphify 模块 | 功能 | nop-code 对应 | 覆盖 |
|--------------|------|--------------|------|
| `detect.py` | `collect_files(root)` 文件收集 + 语言过滤 | `scan_directory()` / `IProjectAnalyzer` 扫描 | ✅ |
| `extract.py` | tree-sitter 提取 `{nodes, edges}` | `ICodeFileAnalyzer` + `ILanguageAdapter` | ✅ |
| `build.py` | 组装 NetworkX 图 | `SymbolTable` + `CallGraph`（现为 `InMemoryCallGraph`） | ✅ |
| `cluster.py` | 社区检测 | `CommunityDetector`（Leiden + LabelPropagation） | ✅ **更强** |
| `analyze.py` | god nodes / surprises / questions | `CriticalNodeAnalyzer` + `KnowledgeGapAnalyzer` + `FlowDetector` + `ChangeAnalyzer` | ✅ **远更强** |
| `report.py` | 渲染 `GRAPH_REPORT.md` | `GraphExporter`（GraphML/Mermaid/JSON） | ⚠️ 缺 Markdown 报告 |
| `export.py` | Obsidian vault / graph.json / graph.html / graph.svg | `GraphExporter` | ⚠️ 缺 Obsidian / HTML / SVG |
| `ingest.py` | URL 抓取入库（非代码源） | N/A（nop-code 专注代码） | N/A |
| `cache.py` | 语义缓存 | `OrmFingerprintStore`（无语义缓存） | ⚠️ 部分 |
| `security.py` / `validate.py` | 输入校验 | 框架层 `allowedLocalRoot` 等 | ⚠️ 部分 |
| `serve.py` | MCP stdio server | N/A（GraphQL 替代） | N/A by design |
| `watch.py` | 目录变更监控 | ❌ | ❌ 缺失 |
| `hooks.py` | git hook 自动重建 | ❌ | ❌ 缺失 |
| `benchmark.py` | token 效率基准 | ❌ | 非核心 |

**graphify v2 独有能力**：
- **Hypergraph**：`graph.json` 中的 hyperedges（超边连接多个节点）+ HTML 阴影区域
- **Semantic similarity edges**：语义相似度边，surprising connections 中排序更高
- **Confidence scores on INFERRED edges**：推断边带置信度并在报告中显示均值
- **并行 AST+semantic**：`--update` 快速增量
- **git commit hook**：每次提交自动重建图

**nop-code 已覆盖的 graphify 核心功能**：✅ 全部核心管线（detect/extract/build/cluster/analyze/report/export）。且分析深度（社区检测双算法、执行流、风险评分、知识缺口、死代码、关键节点）远超 graphify。

**nop-code 相对 graphify 的差距**：
1. Hypergraph（超边）模型
2. Git hook 自动重建机制
3. 交互式 Web UI（graph.html/graph.svg）
4. Obsidian vault 导出
5. 目录变更监控（watch）
6. 语义相似度边的显式置信度评分展示

**结论**：graphify 的**核心功能 nop-code 已全部覆盖**，差距集中在可视化/导出/自动化触发等外围能力，以及 hypergraph 这一数据结构扩展。

**吸收决策**（详见 `ai-dev/design/nop-code/graph-discovery-and-export-design.md`）：

| v2 增强 | 决策 | 理由 |
|---------|------|------|
| 意外连接（复合惊奇评分） | ✅ 吸收 | nop-code 原列为"远期"，v2 给出具体评分算法 |
| 图谱问题生成 | ✅ 吸收 | 全新能力，为 AI 代理提供探索引导 |
| 图谱 Wiki 导出 | ✅ 吸收 | 扩展 `IGraphExporter`，补足人类/代理可读叙事 |
| 自动重建 | ✅ 吸收（改造为 VCS 事件驱动） | 与集群无状态架构对齐，不做本地 watch daemon |
| 语义相似度边 / 置信度 / graph_diff | ❌ 已覆盖 | 见 `semantic-edge-design.md`、`EdgeConfidence`、`GraphDiffer` |
| Hypergraph | ⏸ 暂缓 | 无明确用例；`flow_membership` 已部分覆盖多节点分组 |
| MCP server / Obsidian 导出 | ❌ 拒绝 | 架构决策（GraphQL 已足够）/ 已有拒绝记录 |

---

## 6. AI Integration Patterns

### 6.1 Protocol Strategy: GraphQL vs MCP

**Architectural Principle (Nop Platform):** GraphQL via BizModel is the universal API exposure protocol. Nop platform's `IGraphQLEngine` already dispatches requests from REST, GraphQL, JSON-RPC, and RPC paths to the same BizModel method. Adding MCP would duplicate this infrastructure.

**Why nop-code does NOT need MCP:**
1. **Universal reach**: GraphQL endpoint accepts any HTTP client — no protocol-specific adapter needed. Claude Code, Cursor, Codex, OpenCode all have standard GraphQL HTTP clients.
2. **Nested queries**: `@BizLoader` enables one-shot context building (symbol + sourceCode + usages + hierarchy in a single GraphQL request). MCP requires multiple tool calls for the same data.
3. **Permission control**: `@Auth` annotations on BizModel methods provide enterprise-grade access control. MCP has no authentication mechanism.
4. **Type safety**: GraphQL schema derived from BizModel types is strongly typed. MCP tools are loosely typed JSON schemas.
5. **Nop ecosystem integration**: BizModel integrates with Nop's Delta system, permission framework, and VFS. MCP would isolate nop-code from the platform.

**How AI agents should consume nop-code:**
- Standard GraphQL POST to `/graphql` endpoint
- Use `@BizLoader` nested navigation for one-shot context
- Use `detailLevel` parameter for token efficiency (see §6.3)
- For agent-friendly query patterns, see `NopCodeSymbolBizModel` with its 17 methods + 2 BizLoaders

**Projects that DO use MCP (7 of 10) — and why they don't have GraphQL:**
- CodeGraph, CRG, GitNexus, codeflow, understand-anything, graphify, ontology-driven-agent, entrix are **independent tools** without a platform-level API framework. They need MCP because they lack a universal protocol layer.
- nop-code is **part of the Nop platform** which already has GraphQL as the universal layer. MCP would be redundant.

### 6.2 AI Agent Tool Patterns

| Pattern | Projects | nop-code equivalent |
|---------|----------|---------------------|
| **MCP Tools** | CodeGraph (8 tools), CRG (28 tools + 5 prompts), GitNexus, codeflow, understand-anything, graphify, ontology-agent, entrix | **Not needed** — GraphQL BizModel serves the same purpose |
| **Nested GraphQL queries** | — | `@BizLoader` on NopCodeSymbolBizModel (usages, sourceCode), NopCodeFileBizModel (symbols, types, sourceCode, outline) |
| **AI Prompt/Workflow** | CRG (5 prompts), graphify (/graphify), CodeGraph (workflow init) | Not implemented — could be added as agent-facing GraphQL queries |
| **Context Builder** | CodeGraph (adaptive output budget) | Not implemented — could add `detailLevel` parameter to queries |
| **Token Efficiency** | CodeGraph (detail_level + next_tool_suggestions) | Not implemented — see §6.3 recommendations |

### 6.3 Token Efficiency Strategies

| Strategy | Projects Using | nop-code |
|----------|---------------|----------|
| detail_level分级 | CodeGraph, CRG, graphify | ❌ (should add as query parameter) |
| next_tool_suggestions | CRG | ❌ (could implement as `suggestedNextQueries` field) |
| Context budgeting | CodeGraph (5 levels) | ❌ |
| Minimal context mode | CRG (~100 tokens) | ❌ |
| File expiration hints | CodeGraph | ❌ |
| Adaptive output | CodeGraph (based on project size) | ❌ |

**Recommendations for nop-code** (all achievable via GraphQL parameters, no MCP needed):
- Add `detailLevel` enum parameter (MINIMAL/STANDARD/DETAILED) to all query BizModels
- Add `suggestedNextQueries` field to responses guiding AI to optimal next queries
- Implement `ContextBuilder` as a service that respects budget constraints
- Add file expiration markers in query results
| Search degradation | CRG (3-level) | ❌ |

**nop-code's @BizLoader already provides the underlying mechanism** for nested queries that reduces round trips, but lacks the explicit token budget management and AI-facing guidance that competitors provide.

### 6.4 Architectural Principle: GraphQL as Universal API for Nop Modules

**Principle**: Nop platform's `IGraphQLEngine` is the universal request dispatch layer. All HTTP entry points (GraphQL POST, REST GET/POST `/r/{opName}`, `/p/{opName}/path`, JSON-RPC `/jsonrpc`, RPC `/px/{svc}/{method}`) converge on the same BizModel method. Therefore:

1. **No Nop module should introduce MCP**. GraphQL already provides:
   - Universal HTTP reach (any client, any language)
   - Nested query support (`@BizLoader`)
   - Type safety (schema derived from BizModel)
   - Permission control (`@Auth`)
   - Delta/customization support (xmeta/xpix)
2. **AI agent integration is achieved via standard GraphQL**, not MCP:
   - Claude Code/Cursor/Codex can all make HTTP POST requests to `/graphql`
   - `@BizLoader` nested navigation provides one-shot context building
   - Response customization via `@DataBean` + field selection
3. **If agent-friendly query patterns are needed**, add them as GraphQL queries/mutations (e.g., `explore` query combining search + hierarchy + impact in one call), not as a separate protocol.

**See `docs-for-ai/02-core-guides/api-and-graphql.md`** for the full architectural principle documentation.

---

## 7. nop-code vs All Projects: Gap Analysis

### 7.1 Critical Gaps (Must Fix)

#### Gap 1: Analysis Cache (Impact: Critical)
- **Status**: Every graph query rebuilds SymbolTable + CallGraph from DB (no caching)
- **Competitor baseline**: CodeGraph has LRU bounded cache; CRG has persistent SQLite graph
- **Impact**: Large projects (10K+ symbols) experience severe latency on every query
- **Solution**: Implement `AnalysisCacheManager` with indexId-based caching of SymbolTable + CallGraph. Invalidate on incremental index events.

#### Gap 2: File Watcher (Impact: High)
- **Status**: nop-code requires manual `triggerIncrementalIndex()` calls
- **Competitor baseline**: CRG has watchdog + 300ms debounce + daemon; CodeGraph has native OS events + 2s debounce
- **Impact**: No real-time code change tracking; stale graph between manual triggers
- **Solution**: Implement `CodeFileWatcher` using Java WatchService with configurable debounce. Auto-trigger incremental indexing on file changes.

#### Gap 3: Search (Impact: High)
- **Status**: DB LIKE queries for search; nop-search module exists but unintegrated
- **Competitor baseline**: CodeGraph uses FTS5 + BM25; CRG uses FTS5 + vector + RRF hybrid
- **Impact**: Slow search on large projects; no vector/semantic search
- **Solution**: Integrate `nop-search` (Lucene + vector + RRF) into `CodeIndexService.searchCode()`. This is straightforward since nop-search already provides the infrastructure.

#### Gap 4: Token Efficiency (Impact: High)
- **Status**: Fixed full responses; no adaptive output
- **Competitor baseline**: CodeGraph has 5-level detail budgets; CRG has minimal context mode (~100 tokens)
- **Impact**: AI agents waste tokens on unnecessary data
- **Solution**: Add `detailLevel` parameter to all query BizModels; implement `ContextBuilder` service with budget-aware output. **Achievable via GraphQL parameters only — no MCP needed.**

#### Gap 5: Framework Route Detection — 由 DSL 驱动，不内置
- **状态**：nop-code 核心不内置任何框架特定路由检测
- **设计原则**：框架适配必须由描述式 DSL 驱动（如 YAML/DSL 描述路由模式 → 处理器映射），硬编码框架模式（Spring @GetMapping 等）不应内置在 nop-code 核心。核心只处理通用 AST 解析和关系抽取。
- **框架适配器**（可选，按需加载）：
  - 通过 `IFrameworkRouteExtractor` 接口定义
  - 具体实现由 DSL 文件驱动（`spring-routes.yaml` 等）
  - 通过 Nop IoC 自动发现
  - Nop 平台自身的框架（如 `nop-wf` 的 BPMN、`nop-task` 的 task flow）由平台内置处理
- **结论**：nop-code 核心不需要维护 14+ 种框架的模式匹配库。DSL 驱动的适配器是可插拔的、可维护的。

### 7.2 Storage Architecture — 接口抽象 + 可插拔后端

**核心原则：`CallGraph` 和 `SymbolTable` 作为内存中的简易实现（`InMemoryCallGraph`），但业务逻辑只依赖 `ICallGraph` / `ISymbolTable` 接口，屏蔽后台存储形式。**

```
业务逻辑层（CodeIndexService 等）
    ↓ 依赖接口
┌─────────────────────────────────────────────┐
│ ICallGraph（接口）                           │
│ ├── InMemoryCallGraph（当前 CallGraph 类）   │
│ │   → HashMap + BFS，简易方案，小项目/开发模式│
│ └── PostgresCallGraph（生产集群模式）         │
│     → PostgreSQL + Apache AGE / CTE 递归      │
│     → 无状态，可水平扩展                       │
├─────────────────────────────────────────────┤
│ ISymbolTable（接口）                         │
│ ├── InMemorySymbolTable（当前 SymbolTable）  │
│ └── PostgresSymbolTable（数据库查询）         │
└─────────────────────────────────────────────┘
```

**设计决策**：
- **InMemoryCallGraph 保留**：当前 `CallGraph.java` 的 `HashMap + BFS` 实现作为默认实现保留不动
- **业务代码只依赖接口**：`CodeIndexService` 等只调用 `ICallGraph.getCallers()`、`ICallGraph.getCallees()`、`ICallGraph.bfs()` 等接口方法
- **切换无成本**：生产环境切换为 `PostgresCallGraph`（使用 `WITH RECURSIVE` CTE 或 Apache AGE 的 `AGE()` 函数）只需换 Spring Bean 实现，不改任何业务逻辑
- **Nop 平台自身的图算法库**（`nop-graph/`）可提供统一接口实现

**与 cluster-indexing + stateless 原则的关系**：
- 索引构建阶段（batch indexing）：可以并行构建 InMemoryCallGraph，然后持久化到数据库
- 查询阶段（stateless）：直接从数据库图索引查询，不依赖内存中的 CallGraph
- InMemoryCallGraph 作为开发模式/小项目的简易方案，生产集群由数据库图索引支撑

### 7.3 Important Gaps (Should Fix)

| Gap | Severity | Solution | Effort |
|-----|----------|----------|--------|
| Search | High | Integrate nop-search (Lucene + vector + RRF) | Low |
| Token Efficiency | High | Add `detailLevel` parameter to GraphQL queries | Low |
| More Languages | Medium | Expand tree-sitter bindings beyond Java/Python/TS | Medium |
| Export Formats | Medium | Add Obsidian/Neo4j export alongside existing GraphML/Mermaid/JSON | Low |
| File Watcher | Medium | Java WatchService + debounce auto-trigger incremental indexing | Medium |
| Interactive Graph UI | Medium | D3.js/Cytoscape.js force-directed visualization | Medium |
| Incremental Dependency Impact | Medium | 2-hop propagation tracking | Medium |
| GraphRAG | Low | Add document/wiki generation from graph communities | Medium |
| AI Workflow Prompts | Medium | Add pre-built prompts for common tasks (review, debug, explore) | Low |

### 7.3 Architecture Gaps

| Gap | Description | Solution |
|-----|-------------|----------|
| Source Code Returns Null | `getFileSourceCode()` and `getSymbolSourceCode()` return null despite data being in DB | Fix `entityToFileResult()` to preserve sourceCode |
| BizLoader indexId Hardcoded | `@BizLoader` falls back to hardcoded "test" indexId | Use `@Name("indexId")` parameter passing or entity field |
| xmeta Files Missing | No `.xmeta.xml` files for any BizModel | Generate xmeta files for all 13 modules |
| Empty Aggregation Roots | NopCodeCall/Inheritance/Usage/etc. have no business methods | Add `findCallers()`, `findCallees()`, `findImplementations()` methods |
| API Design Issues | Super-aggregator pattern (NopCodeIndex has 4 unrelated capabilities) | Refactor: move dependency graph methods to NopCodeDependency, analysis methods to NopCodeSymbol |

---

## 8. Updated Priority Recommendations

> **Architectural constraints applied** (per 2026-09-23 decisions):
> - **No MCP** — GraphQL is the universal API. See §6.4.
> - **No hardcoded framework adapters** — framework detection is DSL/SPI-driven and optional, not built into core.
> - **Storage abstraction** — reuse the existing `io.nop.graph.api.IGraph` (`nop-graph-api`); `CallGraph`/`SymbolTable` are in-memory structures, `CodeCallGraph` is the adapter. Do NOT invent `ICallGraph`/`ISymbolTable`.
> - **Cluster-indexing + stateless** — index build is batch/parallel; query path is stateless and horizontal-scalable.
> - **Leverage DB graph indexes** — PostgreSQL (ltree/Apache AGE) or graph DB for local traversal instead of in-memory Java structures in production; global algorithms must be materialized at index time.

### 8.1 Merged Priority Matrix (from all previous reports + new findings)

#### P0 — Critical, Immediate Action Required

| # | Recommendation | Source | Value | Effort |
|---|---------------|--------|-------|--------|
| 1 | **Enrich `IGraph` edge projection** (`CodeCallGraph` currently only projects CALLS without `Edge.attrs`) | 2026-09-23 decision | Enables typed-edge analysis on the existing abstraction | Low-Medium |
| 2 | **Integrate nop-search for search** | CRG (FTS5+vector+RRF), CodeGraph (FTS5+BM25); nop-search already available | Search performance + semantic search | Low |
| 3 | ~~Fix sourceCode returning null~~ | CRG audit (2026-05-25) | ✅ 已修复 | — |
| 4 | ~~Fix BizLoader indexId hardcoding~~ | CRG audit (2026-05-25) | ✅ 已修复 | — |
| 5 | ~~xmeta files for all BizModels~~ | CRG audit (2026-05-25) | ✅ 已具备 | — |

#### P1 — Important, Near-Term Planning

| # | Recommendation | Source | Value | Effort |
|---|---------------|--------|-------|--------|
| 6 | **Design for cluster-indexing + stateless query path** | 2026-09-23 decision | Horizontal scalability | Medium |
| 7 | **PostgreSQL graph index backend (ltree / Apache AGE)** | 2026-09-23 decision | Production-scale graph traversal | High |
| 8 | **Token efficiency + detailLevel** | CodeGraph, CRG, graphify | AI agent token optimization | Low |
| 9 | **Expand to 10+ languages** | CodeGraph (28), CRG (24) | Code coverage | Medium |
| 10 | **Tree-sitter for Python/TS (unify parsing)** | All competitors | Better cross-language consistency | Medium |
| 11 | **Export to Obsidian/Neo4j** | CRG (GraphML/Neo4j/Obsidian) | External tool integration | Low |
| 12 | **File watcher with OS events** | CodeGraph (FSEvents/inotify), CRG (watchdog) | Real-time code graph updates | Medium |
| 13 | **Vector search integration** | CRG (4 providers), nop-search ready | Semantic search | Medium |

#### P2 — Valuable, Long-Term

| # | Recommendation | Source | Value | Effort |
|---|---------------|--------|-------|--------|
| 14 | **Interactive web graph visualization** | CRG (D3.js), codeflow, understand-anything | User experience | Medium-High |
| 15 | **DSL-driven framework adapter mechanism** | CodeGraph (14 frameworks), CRG (~40 patterns) | Framework awareness without hardcoding | Medium |
| 16 | **Cross-language bridge detection** | CodeGraph (Swift↔ObjC, RN Bridge) | Multi-language project support | High |
| 17 | **GraphRAG capability** | Microsoft GraphRAG, HKUDS DeepCode | Natural language code understanding | High |
| 18 | **LLM semantic extraction** | OntoAgent (LLM-driven), graphify | Business concept discovery | High |
| 19 | **Wiki/Doc generation** | CRG (Markdown wiki), GitNexus (wiki) | Documentation automation | Low |
| 20 | **Evaluation framework** | CRG (5-dimension eval) | Quality assurance | Medium |

### 8.2 Implementation Timeline

```
Phase 1 (P0) — Correctness + API Foundation (Weeks 1-4)
├── 1. (done) Fix sourceCode null bug
├── 2. (done) Fix BizLoader indexId hardcoding
├── 3. (done) xmeta files for all BizModels
├── 4. nop-search integration (searchCode → ISearchEngine)
└── 5. Enrich IGraph edge projection (typed edges + attrs)

Phase 2 (P1) — Storage + Scale (Weeks 5-12)
├── 6. Stateless query path design (remove in-memory full rebuild from query flow)
├── 7. PostgreSQL graph index backend (ltree / Apache AGE) behind IGraph
├── 8. Cluster-indexing batch build path
├── 9. Token efficiency (detailLevel)
├── 10. Tree-sitter unification for Python/TS
└── 11. Language expansion (Go, Rust, C#)

Phase 3 (P2) — Ecosystem (Weeks 13-24)
├── 12. DSL-driven framework adapter mechanism
├── 13. Interactive graph visualization
├── 14. Cross-language bridge detection
├── 15. GraphRAG / LLM semantic edges
├── 16. Wiki/Doc generation
└── 17. Evaluation framework
```

---

## 9. Strategic Recommendations for nop-code

### 9.1 Strategic Positioning

Based on the comprehensive survey, nop-code's strategic positioning should be:

> **"The deepest code analysis engine with enterprise-grade persistence, wrapped in AI-native interfaces."**

This means:
- Keep nop-code's analysis depth (community detection, flow tracing, risk scoring) as its core differentiator
- Do NOT add MCP — GraphQL is already the universal API
- Maintain enterprise persistence (ORM) as the storage backbone
- Do NOT try to be a zero-config local tool — that's CodeGraph's market
- Do NOT try to be a standalone web application — that's codeflow's market
- Be the **cluster-indexing backend analysis engine** that powers both Nop platform and AI coding assistants

### 9.2 Target Architecture (Stateless + DB Graph Index)

```
┌─────────────────────────────────────────────────────────┐
│ Tier 1: GraphQL API Layer (Stateless)                     │
│ ├── IGraphQLEngine unified dispatch                       │
│ ├── @BizLoader nested navigation                          │
│ ├── Permission control (@Auth)                            │
│ └── Token efficiency (detailLevel parameter)              │
├─────────────────────────────────────────────────────────┤
│ Tier 2: Service Layer (Stateless)                         │
│ ├── CodeIndexService                                      │
│ ├── ICallGraph / ISymbolTable interfaces                  │
│ │    ├── InMemoryCallGraph (simple/dev/small projects)    │
│ │    └── PostgresCallGraph (production/cluster)           │
│ └── Analysis orchestration (interface-based)              │
├─────────────────────────────────────────────────────────┤
│ Tier 3: Graph Storage (Database-native)                   │
│ ├── PostgreSQL + ltree / Apache AGE (graph index)         │
│ ├── Recursive CTE for traversal (no in-memory BFS)        │
│ ├── pgvector for embeddings                               │
│ └── nop-search (Lucene + vector + RRF)                    │
├─────────────────────────────────────────────────────────┤
│ Tier 4: Index Build (Batch/Parallel, Cluster)             │
│ ├── Language Adapters (JavaParser + tree-sitter)         │
│ ├── Parallel full/incremental build                       │
│ ├── Fingerprint-based change detection                    │
│ └── Optional DSL-driven framework adapters                │
└─────────────────────────────────────────────────────────┘
```

**Key architectural properties:**
- **Stateless query path**: No in-memory SymbolTable/CallGraph rebuilding on queries. Graph traversal via DB recursive CTE.
- **Cluster-indexing**: Index build is a batch job that can run distributed; each indexId is an isolated unit.
- **Storage abstraction**: `ICallGraph`/`ISymbolTable` interfaces hide the backend. In-memory impl is the simple default; DB impl is production.
- **DB graph index**: PostgreSQL ltree/Apache AGE provides graph traversal primitives, eliminating Java memory overhead.

### 9.3 Competitive Advantages to Preserve

1. **Deepest analysis algorithms**: No competitor has nop-code's combination of community detection, flow tracing, risk analysis, semantic edges, and knowledge gap analysis. This is the moat.

2. **Enterprise architecture**: ORM persistence, permission control, multi-tenancy via indexId, and Nop platform integration are enterprise features that local-first tools cannot match.

3. **Java precision**: JavaParser + SymbolSolver provides compiler-level symbol resolution that tree-sitter alone cannot match. This matters for Java enterprise projects.

4. **GraphQL nested queries**: @BizLoader provides richer query capabilities than MCP's flat tool calls. This is a genuine architectural advantage.

5. **Deterministic analysis**: Unlike competitors that are adding LLM-based semantic extraction (which can hallucinate), nop-code's purely algorithmic analysis is deterministic, testable, and auditable.

6. **Cluster-scale**: Designed for distributed indexing with database-backed graph storage — competitors are local-first single-repo tools.

### 9.4 What NOT to Do

1. **Don't add MCP** — GraphQL via BizModel already provides universal API exposure. See §6.4.
2. **Don't hardcode framework adapters in core** — framework detection must be DSL-driven and optional. Core only handles generic AST + Nop platform itself.
3. **Don't keep in-memory graph as the only implementation** — extract `ICallGraph`/`ISymbolTable` interfaces so DB graph backend can be plugged in for cluster mode.
4. **Don't build a stateful query path** — queries must not rebuild SymbolTable/CallGraph in memory; use DB graph indexes.
5. **Don't replace JavaParser with tree-sitter for Java** — JavaParser's symbol resolution precision is superior.
6. **Don't add LLM-based analysis to core** — stick to deterministic algorithms; LLM integration belongs in nop-ai-agent layer.
7. **Don't try to support all 28 languages at once** — prioritize Java, Python, TypeScript, then Go, Rust.

---

## 10. nop-code's Unique Advantages

### 10.1 Capabilities nop-code Has That No Competitor Has

| Capability | Description | Why It Matters |
|------------|-------------|----------------|
| **Dual community detection** | Leiden + LabelPropagation with large-graph optimization and automatic fallback | More robust community detection than any single algorithm |
| **5-dimension criticality scoring** | fileSpread(0.30) + externalScore(0.20) + securityScore(0.25) + testGap(0.15) + depthScore(0.10) | Unmatched execution flow analysis precision |
| **Risk-scored change analysis** | git diff → line mapping → multi-dimensional risk with security keyword detection | Directly supports CI/CD security gates |
| **Semantic edge extraction** | NameSimilarity + DocKeyword + AnnotationPattern extractors | Finds relationships that structural analysis misses |
| **Heuristic edge synthesis** | InterfaceImplSynthesizer + SpringEventSynthesizer | Infers relationships without explicit references |
| **Knowledge gap analysis** | Isolated nodes + weak communities detection | Identifies documentation/understanding gaps |
| **Graph diff/snapshot** | Set difference between two index states | Tracks architecture evolution over time |
| **Hub + Bridge analysis** | PageRank + Betweenness centrality | Identifies both hotspots and bottlenecks |
| **20+ framework exclusion patterns** | For dead code detection | Far more precise than simple call-graph analysis |
| **Multi-format export** | GraphML, Mermaid, JSON | Interoperability with external tools |

### 10.2 Architecture Advantages

| Advantage | Description |
|-----------|-------------|
| **LanguageAdapterRegistry** | Nop IoC auto-discovery means new languages are added by registering an adapter, not modifying core code |
| **@BizLoader nested queries** | Single GraphQL request can fetch symbol + usages + sourceCode + typeHierarchy in one round trip |
| **Nop ORM transaction management** | ACID compliance for index operations that SQLite cannot guarantee |
| **Nop platform integration** | Access to Nop's VFS, configuration, security, and module ecosystem |
| **Deterministic algorithms** | All analysis is algorithmic and reproducible; no LLM variability |

### 10.3 The Hybrid Advantage

nop-code's hybrid parsing approach (JavaParser for Java + tree-sitter for Python/TS) gives it:
- **Compiler-level Java precision** that no tree-sitter-only tool can match
- **Cross-language consistency** via tree-sitter for non-Java languages
- **A clear migration path** to expand tree-sitter to more languages without losing Java precision

---

## 11. Open Questions

> MCP-related questions are **resolved**: nop-code does NOT add MCP. GraphQL is the universal API (see §6.4, §9.4, and `docs-for-ai/02-core-guides/api-and-graphql.md`).

### 11.1 Technical Questions

- [ ] **Storage abstraction design**: What is the minimal `ICallGraph`/`ISymbolTable` interface that covers all current algorithms (BFS, callers/callees, impact, flow) and can be implemented by both in-memory and DB graph backends?
- [ ] **DB graph backend choice**: PostgreSQL `ltree` + recursive CTE vs Apache AGE (Cypher) vs a dedicated graph DB? What is the migration path from the current `nop_code_call` relational tables?
- [ ] **Stateless query path**: How to eliminate the current `rebuildSymbolTable()` full-load on every query — direct CTE queries, or a materialized graph projection refreshed on index events?
- [ ] **Cluster indexing**: How should index build be partitioned across a cluster? By repo (indexId), by directory, by language? How to coordinate incremental updates?
- [ ] **tree-sitter Java integration**: JNI bindings, WebAssembly in JVM, or both? See `2026-09-07-pure-java-tree-sitter-feasibility.md`.
- [ ] **Search index sync**: When to call `ISearchEngine.addDoc()` (per file / batched)? How to handle deletions and updates?
- [ ] **Framework adapter DSL**: What DSL shape describes route/DI patterns declaratively? (e.g., annotation name + method name pattern → route template). How does it integrate with Nop's XDef/XDSL system?

### 11.2 Strategic Questions

- [ ] **Server vs local tool**: The market favors local-first tools for developer AI integration, but nop-code's natural habitat is cluster/server deployment. Could a local in-memory mode (via `InMemoryCallGraph`) coexist with cluster mode?
- [ ] **How to handle the LLM trend?** Competitors increasingly add LLM-based semantic extraction (OntoAgent, graphify). Should nop-code add this as an optional layer in nop-ai-agent, or stay purely algorithmic in core?
- [ ] **What about GraphRAG?** Microsoft GraphRAG and HKUDS/DeepCode move toward graph-augmented RAG. Should nop-code expose community/graph data to nop-ai-rag?
- [ ] **Output formats**: Which export targets matter most (Obsidian, Neo4j Cypher, HTML visualization)?

### 11.3 Priority Clarification Questions

- [ ] **Should tree-sitter expansion or DB graph backend come first?** The DB backend is the bigger architectural shift; language count is independent value.
- [ ] **What is the minimum stateless query path?** Which of the 38+ BizModel methods are on the AI hot path and must move to DB-native traversal first?
- [ ] **DSL-driven framework adapters**: Is this P2 or should the DSL mechanism be designed alongside the core parser interfaces?

---

## Appendix A: Previous Reports Superseded

This report supersedes:
- `2026-06-05-codegraph-vs-nop-code-deep-analysis.md` — Covers CodeGraph vs nop-code in full detail
- `2026-05-25-code-review-graph-vs-nop-code.md` — Covers CRG vs nop-code in full detail

Both previous reports remain available for historical reference and detailed algorithm comparisons. Their findings are incorporated into this synthesis.

## Appendix B: Source References

| Source | Type | Date |
|--------|------|------|
| CodeGraph GitHub (`colbymchenry/codegraph`) | Project | v0.9.9, last update 2026-06-05 |
| code-review-graph GitHub (`tirth8205/code-review-graph`) | Project | v2.3.3, last update 2026-05-25 |
| GitNexus GitHub (`abhigyanpatwari/GitNexus`) | Project | last update 2026-06-05 |
| codeflow GitHub (`braedonsaunders/codeflow`) | Project | last update 2026-05-24 |
| ast-outline-rs GitHub (`aeroxy/ast-outline`) | Project | last update 2026-06-05 |
| understand-anything GitHub (`Lum1104/Understand-Anything`) | Project | last update 2026-06-04 |
| graphify GitHub (`safishamsi/graphify`) | Project | v2 main branch, last update 2026-05-03 |
| ontology-driven-agent GitHub (`yql210/ontology-driven-agent`) | Project | last update 2026-07-09 |
| entrix GitHub (`phodal/entrix`) | Project | last update 2026-05-28 |
| DeepCode GitHub (`HKUDS/DeepCode`) | Project | last update 2026-05-18 |
| nop-code module source | Internal | Ongoing |
| `ontology-driven-agent-vs-nop-code-index.md` | Previous analysis | 2026-07-10 |
| `2026-09-07-tree-sitter-runtime-architecture.md` | Technical analysis | 2026-09-07 |
| `2026-09-07-pure-java-tree-sitter-feasibility.md` | Technical analysis | 2026-09-07 |
| `2026-09-12a-github-ontology-projects-vs-nop-metadata.md` | Previous analysis | 2026-09-12 |

## Appendix C: MCP Tool List (Complete from Previous Reports)

### CodeGraph MCP Tools (8 tools)

| # | Tool Name | Required Params | Optional Params | Return | Core Logic |
|---|-----------|----------------|-----------------|--------|------------|
| 1 | `codegraph_explore` | `query` (string) | `maxFiles`, `projectPath` | Source by file + blast radius + graph | `findRelevantContext` → RWR → scoring → clustering → adaptive trimming |
| 2 | `codegraph_search` | `query` (string) | `kind`, `limit`, `projectPath` | name, kind, file:line, signature | FTS5 + exact name match |
| 3 | `codegraph_node` | `symbol` (string) | `includeCode`, `file`, `line`, `projectPath` | Symbol details + (optional) body/outline + caller/callee trail | Multiple overload resolution |
| 4 | `codegraph_callers` | `symbol` (string) | `limit`, `projectPath` | Caller list | `getCallers()` along calls/references/imports |
| 5 | `codegraph_callees` | `symbol` (string) | `limit`, `projectPath` | Callee list | `getCallees()` along calls/references/imports |
| 6 | `codegraph_impact` | `symbol` (string) | `depth`, `projectPath` | Impact by file | `getImpactRadius()` reverse BFS |
| 7 | `codegraph_status` | — | `projectPath` | file/node/edge counts, DB size | Direct DB stats |
| 8 | `codegraph_files` | — | `path`, `pattern`, `format`, `maxDepth`, `projectPath` | File tree + language + symbol counts | Query files table |

### CRG MCP Tools (28 tools + 5 prompts)

CRG provides the most comprehensive MCP toolset among all 10 projects:
- 28 individual tools covering all graph operations
- 5 workflow prompts: `review_changes`, `architecture_map`, `debug_issue`, `onboard_developer`, `pre_merge_check`
- stdio/HTTP dual transport
- PreToolUse hooks for Grep/Read interception

### nop-code GraphQL API Summary (38+ BizModel methods)

**NopCodeIndexBizModel** (24 methods): Index management, community detection, graph analysis, impact analysis, flow detection, change analysis, dependency graph
**NopCodeSymbolBizModel** (15 methods + 2 BizLoaders): Symbol lookup, hierarchy, search, references, dead code, implementations, source code
**NopCodeFileBizModel** (3 methods + 4 BizLoaders): File query, file tree, symbol/type/source/outline access

---

*This report synthesizes all available analysis of 10 code graph analysis projects against nop-code, providing a comprehensive foundation for strategic planning and implementation.*
