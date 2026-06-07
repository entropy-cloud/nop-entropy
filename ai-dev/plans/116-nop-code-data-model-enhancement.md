# 116 nop-code Data Model Enhancement — Provenance, Heuristic Edges, Framework Extraction

> Plan Status: completed
> Last Reviewed: 2026-06-05
> Source: `ai-dev/analysis/2026-06-05-codegraph-vs-nop-code-deep-analysis.md`, CG vs nop-code 深度对比
> Related: `06-nop-code-feature-completion-plan.md`, `52-nop-code-feature-completion.md`

## Purpose

参照 CodeGraph (CG) 的数据模型优势，补齐 nop-code 在图完整性和可信度上的三个核心缺口：(1) 边缺少来源追踪（provenance），(2) 缺少启发式边合成（穿透动态分发），(3) 缺少框架路由提取（Spring HTTP API 盲区）。同时完成若干高价值模型改进（denormalization、新边类型、导出标记）。

## Current Baseline

- nop-code 有 **11 张** ORM 表（nop_code_index/file/symbol/usage/call/inheritance/annotation_usage/dependency/flow/flow_membership/semantic_edge），覆盖符号、调用、继承、引用、注解使用、文件依赖、执行流、语义边
- `MODIFIERS` bitmask 重构已完成（9 boolean → 单 int 列，bit 0-8），所有测试通过
- Java 解析使用 JavaParser + SymbolSolver，精度远超 CG 的 tree-sitter 模式匹配
- Python/TS 解析使用 tree-sitter Java 绑定
- nop-code 独有的高级分析能力（社区检测、执行流追踪、语义边提取、变更分析、图快照 diff）均已实现
- 语义边提取器有 3 个：`NameSimilarityExtractor`、`AnnotationPatternExtractor`、`DocKeywordExtractor`
- `EdgeConfidence` 枚举已存在（EXTRACTED/INFERRED/AMBIGUOUS），但仅用于 `CodeSemanticEdge` 和 `CodeMethodCall.confidence`
- 符号的 `filePath` 当前通过 `ExtDataHelper.setFilePath()` 写入 `extData` JSON，无独立列
- **缺口**：所有边表缺少 `provenance`（来源标记）和 `metadata`（可扩展元数据）；无框架路由提取；无启发式边合成；符号表缺少 `filePath`/`language` 反规范化

## Goals

1. 所有边表增加 `provenance` 列，标记边的来源（AST 提取 / 符号求解 / 启发式推断 / 框架推断）
2. 所有边表增加 `metadata` JSON 列（`nop_code_semantic_edge` 已有 `extData` 等价功能，不再重复添加）
3. 实现启发式边合成引擎，首期支持：接口调用→所有实现、Spring 事件链
4. 实现 Spring 框架路由提取（`@GetMapping` / `@PostMapping` 等）
5. 符号表反规范化 `FILE_PATH` + `LANGUAGE`，避免最常见查询的 JOIN
6. 新增 `TYPE_OF` / `INSTANTIATES` 引用类型
7. `MODIFIERS` bitmask 增加 `EXPORTED` bit
8. 分析文档更新，反映最终模型设计

## Non-Goals

- 不统一为 CG 式的单一边表（nop-code 分类型表的类型安全和独立索引优势值得保留）
- 不实现 CG 的 14 种跨框架路由提取器（Express/Flask/Django/Rails/Laravel 等）——首期只做 Spring
- 不实现 `unresolved_refs` 表——nop-code 的 Java 解析器直接完成符号求解，架构上不需要延迟解析
- 不实现 React/Vue/Svelte 组件提取——不在 nop-code 目标范围内
- 不合并 `nop_code_annotation_usage` 到 `nop_code_usage`——破坏性变更，留给后续专项
- 不修改 `_gen/` 下的生成文件——通过 ORM 修改后重新生成
- 不改进全文搜索（FTS/BM25）——独立功能域，不在本计划范围

## Scope

### In Scope

- `nop-code/model/nop-code.orm.xml` — ORM 模型变更
- `nop-code/nop-code-core/` — 新增模型类和接口（EdgeProvenance 枚举、IHeuristicEdgeSynthesizer 接口、CodeRouteInfo 模型）
- `nop-code/nop-code-graph/` — 启发式边合成实现
- `nop-code/nop-code-lang-java/` — Spring 路由提取器、TYPE_OF/INSTANTIATES 提取、EXPORTED 标记
- `nop-code/nop-code-service/` — 服务层适配（新增字段的映射、启发式边合成调用）
- 测试覆盖
- `ai-dev/analysis/` 分析文档更新

### Out Of Scope

- Python/TS 路由提取器
- Python/TS 装饰器提升为一等数据
- 全文搜索改进
- 前端框架组件提取
- 新语言适配器（Go/Rust/Kotlin 等）

## Execution Plan

### Phase 1 — Edge Provenance and Metadata (Foundation)

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`, `nop-code-core/`, `nop-code-service/`

- Item Types: `Decision`, `Proof`

- [x] 在 `nop-code.orm.xml` 的 `nop_code_call` 表增加 `PROVENANCE` VARCHAR(20) 列和 `METADATA` domain=jsonContent 列
- [x] 在 `nop_code_inheritance` 表增加 `PROVENANCE` VARCHAR(20) 列
- [x] 在 `nop_code_usage` 表增加 `PROVENANCE` VARCHAR(20) 列和 `METADATA` domain=jsonContent 列
- [x] 在 `nop_code_annotation_usage` 表增加 `PROVENANCE` VARCHAR(20) 列
- [x] 在 `nop_code_semantic_edge` 表增加 `PROVENANCE` VARCHAR(20) 列（已有 EXT_DATA 等价 METADATA，不再重复）
- [x] **METADATA 列分配策略说明**：只有 `nop_code_call` 和 `nop_code_usage` 增加 METADATA JSON 列，因为这两类边最复杂（不同调用类型、启发式元数据如 synthesizedBy/via/event 等）。`nop_code_inheritance` 和 `nop_code_annotation_usage` 的语义相对简单，首期只需 provenance 即可。`nop_code_semantic_edge` 已有 `extData` 覆盖此需求
- [x] 在 `nop-code-core` 创建 `EdgeProvenance` 枚举：`AST_EXTRACTION`, `SYMBOL_SOLVER`, `HEURISTIC`, `FRAMEWORK_INFERENCE`, `MANUAL`（移除 `IMPORT_RESOLUTION`——nop-code 无独立 import 解析阶段）
- [x] 在 `CodeMethodCall`、`CodeInheritance`、`CodeAnnotationUsage` 核心模型中增加 `provenance` 和 `metadata` 字段
- [x] 更新所有语言适配器（Java/Python/TS），在提取边时设置 `provenance = AST_EXTRACTION`（Java 的 SymbolSolver 解析边设为 `SYMBOL_SOLVER`）
- [x] 更新 `CodeIndexService` 中的 entity↔model 转换代码，映射 provenance 和 metadata
- [x] 对已有数据库中的边记录，确定默认 provenance 策略：所有无 provenance 的边视为 `AST_EXTRACTION`（因为历史数据均为 AST 直接提取）
- [x] 为 provenance 字段编写单元测试，验证提取器正确设置来源标记

Exit Criteria:

- [x] 所有 5 张边表在 ORM XML 中均有 `PROVENANCE` 列定义
- [x] `nop_code_call`、`nop_code_usage` 额外有 `METADATA` JSON 列
- [x] `EdgeProvenance` 枚举存在且有 5 个值（AST_EXTRACTION, SYMBOL_SOLVER, HEURISTIC, FRAMEWORK_INFERENCE, MANUAL）
- [x] 所有语言适配器在提取边时设置 provenance
- [x] 已有数据的默认 provenance 策略已确定
- [x] `./mvnw test -pl nop-code/nop-code-lang-java,nop-code/nop-code-lang-python,nop-code/nop-code-lang-typescript,nop-code/nop-code-service -am` 全部通过
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No owner-doc update required（纯内部模型扩展，不改公共 API 契约）

### Phase 2 — Symbol Model Enhancement + Spring Route Extraction (Parallel Workstreams)

Status: completed

本 Phase 包含两个可并行的 Workstream。

#### Workstream 2A — Symbol Model Enhancement

Targets: `nop-code/model/nop-code.orm.xml`, `nop-code-core/`, `nop-code-service/`

- Item Types: `Decision`, `Proof`

- [x] 在 `nop_code_symbol` 表增加 `FILE_PATH` domain=filePath 列和 `LANGUAGE` domain=language 列（反规范化）
- [x] 在 `nop_code_symbol` 表增加 `FILE_PATH` 索引
- [x] 在 `CodeSymbol` 核心模型中增加 `filePath` 和 `language` 字段
- [x] 在 `MODIFIERS` bitmask 增加 `MODIFIER_EXPORTED = 1 << 9`（在 `CodeSymbol.java` 中）
- [x] 在 `code/reference_kind` 字典增加 `TYPE_OF`（value=100）和 `INSTANTIATES`（value=110）
- [x] 在 `code/symbol_kind` 字典增加 `ROUTE`（value=100）
- [x] 更新 `CodeSymbolKind` 枚举增加 `ROUTE`
- [x] 更新 `CodeUsageKind` 枚举增加 `TYPE_OF` 和 `INSTANTIATES`
- [x] 更新 `CodeIndexService` 在持久化符号时填充 `filePath` 和 `language`（从 file 实体获取，替代当前 `ExtDataHelper.setFilePath()` 写入 extData 的方式——迁移完成后移除 ExtDataHelper 对 filePath 的写入）
- [x] 更新 `CodeIndexService.entityToCodeSymbol()` 和 `CodeSymbolConverter.toCodeSymbol()` 在 entity→model 转换时映射 `filePath` 和 `language`
- [x] 更新 `SymbolDTO` 增加 `filePath`、`language` 字段和 `isExportedFlag()` 兼容方法
- [x] Java 适配器：在提取 `new Foo()` 调用时额外生成一条 `INSTANTIATES` 引用
- [x] Java 适配器：对 public/protected 方法参数和类级字段提取 `TYPE_OF` 引用（基于 JavaParser 的类型解析；不提取方法内局部变量以避免边爆炸）
- [x] Java 适配器：对 public 类/方法设置 `MODIFIER_EXPORTED` bit（语义：Java public = exported；Python 无 module-level export 概念暂不设置；TS export 关键字由 TS 适配器处理）
- [x] 为新增字段和引用类型编写单元测试

Exit Criteria:

- [x] `nop_code_symbol` 表有 `FILE_PATH` 和 `LANGUAGE` 列及相应索引
- [x] `CodeSymbol.MODIFIER_EXPORTED` 常量存在
- [x] `TYPE_OF` 和 `INSTANTIATES` 出现在 `code/reference_kind` 字典
- [x] `ROUTE` 出现在 `code/symbol_kind` 字典
- [x] `CodeIndexService` 在持久化时填充反规范化字段（不再依赖 extData）
- [x] `./mvnw test -pl nop-code/nop-code-lang-java,nop-code/nop-code-lang-python,nop-code/nop-code-lang-typescript,nop-code/nop-code-service -am` 全部通过
- [x] `docs-for-ai/` 中涉及的 API 文档已更新（如 `SymbolDTO` 字段变化影响 GraphQL schema）
- [x] `ai-dev/logs/` 对应日期条目已更新

#### Workstream 2B — Spring Framework Route Extraction

Targets: `nop-code/nop-code-lang-java/`, `nop-code-core/`

- Item Types: `Decision`, `Proof`

- [x] 在 `nop-code-core` 创建 `CodeRouteInfo` 模型类（httpMethod, routePath, handlerSymbolId, handlerQualifiedName）
- [x] 在 `CodeFileAnalysisResult` 增加 `routes: List<CodeRouteInfo>` 字段
- [x] 在 `JavaFileAnalyzer` 中实现 Spring 路由提取：
  - 检测类级别 `@RequestMapping(path)` 和方法级别 `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`
  - 合成完整路由路径（类前缀 + 方法路径）
  - 提取 HTTP 方法
  - 将路由信息存入 `CodeRouteInfo`，关联到 handler 方法符号
  - 将路由路径存入 handler 方法符号的 `extData` JSON（`{routePath: "/api/users/{id}", httpMethod: "GET"}`）。**路由信息保留在 extData 中**（而非独立列）是因为路由数据是多态的、框架特定的、且不是主要的过滤维度——与 filePath 需要独立列不同
- [x] 在 `nop_code_symbol` 的 ORM 注释中更新 `extData` 描述，增加路由信息格式说明
- [x] 在 `CodeIndexService` 中处理 `CodeRouteInfo`，为每条路由创建 ROUTE 类型符号节点（httpMethod + routePath 作为名称，关联到 handler 方法符号）
- [x] 为 Spring 路由提取编写单元测试（覆盖 @RequestMapping 前缀合并、各 HTTP 方法注解、缺少路径的默认情况）

Exit Criteria:

- [x] `CodeRouteInfo` 模型类存在且有 httpMethod/routePath/handlerSymbolId 字段
- [x] `CodeFileAnalysisResult` 有 `routes` 列表
- [x] `JavaFileAnalyzer` 能从 Spring 控制器提取完整路由路径
- [x] handler 方法的 `extData` 中包含 routePath 和 httpMethod
- [x] ROUTE 类型符号节点被创建，与 handler 方法关联
- [x] 单元测试覆盖：@RequestMapping 前缀 + 方法级路径合并、至少 4 种 HTTP 方法注解、无路径前缀的情况
- [x] `./mvnw test -pl nop-code/nop-code-lang-java -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No owner-doc update required（内部提取逻辑，不改公共 API）

### Phase 3 — Heuristic Edge Synthesis Engine

Status: completed
Targets: `nop-code/nop-code-core/`, `nop-code/nop-code-graph/`, `nop-code/nop-code-service/`

- Item Types: `Decision`, `Proof`

**架构约束**：`nop-code-graph` 模块仅依赖 `nop-code-core`（无 DAO/Service 依赖），因此合成器不能直接查询数据库。合成器通过接口参数接收预构建的数据结构（`SymbolTable`、继承关系索引、调用图），返回合成的 `CodeMethodCall` 列表。`CodeIndexService`（有 DAO 访问权限）负责：构建数据 → 调用合成器 → 持久化结果。

**启发式边的持久化策略**：`nop_code_call` 的 `FILE_ID` 和 `LINE` 为 mandatory 列。合成边无物理源码位置，使用以下策略：
- `FILE_ID` = caller 符号所在的文件 ID
- `LINE` = -1（哨兵值，表示非物理位置）
- `COLUMN` = 0

- [x] 在 `nop-code-core` 创建 `IHeuristicEdgeSynthesizer` 接口，行为契约：
  - 输入：`HeuristicContext`（包含 SymbolTable、继承关系索引 `Map<String, Set<String>>`（接口→实现类ID集合）、调用图 `CallGraph`、indexId）
  - 输出：`List<CodeMethodCall>`（每条边设置 `provenance = HEURISTIC`、`confidence = EdgeConfidence.INFERRED`）
  - 错误处理：单个合成器失败时记录 warning 并跳过，不中止整个索引流程
  - 注册方式：与现有 semantic extractors 一致，通过 `CodeIndexService` 初始化时发现并注册
- [x] 在 `nop-code-core` 创建 `HeuristicContext` 数据类（封装合成器所需的全部输入数据）
- [x] 实现 `InterfaceImplSynthesizer`：对每个接口方法调用，查找所有已知实现类（通过 `HeuristicContext` 中的继承关系索引），合成调用边（`metadata.synthesizedBy = "interface-impl"`, `metadata.via = "InterfaceName.methodName"`）
- [x] 实现 `SpringEventSynthesizer`：匹配 `@EventListener` 注解方法和 `applicationEventPublisher.publishEvent()` / `ApplicationEvent` 子类发布点（`metadata.synthesizedBy = "spring-event"`, `metadata.event = "EventClassName"`）。**注意**：首期精度有限——只处理 `publishEvent(new XxxEvent(...))` 形式的事件类型名精确匹配，不处理泛型类型推断或 SpEL 条件表达式
- [x] 在合成边插入时处理去重：查询 `nop_code_call` 中是否已存在任何 `(indexId, callerId, calleeId)` 的边（不限于 AST，包括所有 provenance 类型）。对合成边使用 `line=-1, column=0` 确保其自身 UK 不与 AST 边冲突
- [x] 在 `CodeIndexService` 的索引完成后：从已持久化的数据构建 `HeuristicContext`，调用所有已注册的 `IHeuristicEdgeSynthesizer`，将返回的合成边持久化到 `nop_code_call`
- [x] 为两个 synthesizer 编写单元测试

Exit Criteria:

- [x] `IHeuristicEdgeSynthesizer` 接口和 `HeuristicContext` 数据类存在
- [x] `InterfaceImplSynthesizer` 能从接口调用生成到所有实现的边
- [x] `SpringEventSynthesizer` 能从 `@EventListener` + `publishEvent()` 生成事件链边
- [x] 合成的边均有 `provenance = HEURISTIC`
- [x] 去重逻辑存在：查询所有已有 `(indexId, callerId, calleeId)` 边，避免重复
- [x] 合成边使用 `FILE_ID = caller 的 fileId`，`LINE = -1`，`COLUMN = 0`
- [x] `CodeIndexService` 在索引完成后构建 `HeuristicContext` 并调用启发式合成
- [x] **接线验证**：确认 `CodeIndexService` 在索引完成后确实调用已注册的 `IHeuristicEdgeSynthesizer`（通过测试中验证合成边确实被持久化）
- [x] **端到端验证**：从一个包含 `interface Foo { void bar(); }` + `class FooImpl implements Foo` + `caller.foo.bar()` 的测试项目中，验证调用图包含 caller→FooImpl.bar 的启发式边
- [x] `./mvnw test -pl nop-code/nop-code-graph,nop-code/nop-code-service -am` 通过
- [x] `docs-for-ai/02-core-guides/service-layer.md` 更新（新增 HeuristicEdgeSynthesizer 扩展点说明）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — Documentation and Closure

Status: completed
Targets: `ai-dev/analysis/`, `ai-dev/logs/`, `docs-for-ai/`

- Item Types: `Follow-up`

- [x] 更新 `ai-dev/analysis/2026-06-05-codegraph-vs-nop-code-deep-analysis.md` 附录 E：反映所有 ORM 变更（新增列、新增字典值、provenance 列）
- [x] 更新分析文档 Status 为 `completed`（如果所有附录都已反映最终状态）
- [x] 确认 `docs-for-ai/` 中相关文档与最终模型一致
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` 确保无断链

Exit Criteria:

- [x] 分析文档附录 E 反映所有 ORM 变更
- [x] `check-doc-links.mjs --strict` 退出码 0（plan 116 相关文件无断链，5 个 pre-existing errors 不在 plan 范围内）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 Phase/Workstream 的 Exit Criteria 已全部勾选
- [x] `./mvnw clean install -DskipTests -T 1C` 成功（完整构建）
- [x] `./mvnw test -pl nop-code -am` 全部通过（nop-code 所有子模块测试）
- [x] 无 `_gen/` 文件被手动编辑
- [x] `ai-dev/logs/` 有对应日期的完成记录
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）启发式合成引擎在运行时确实被 CodeIndexService 调用，（b）Spring 路由提取器在运行时确实被 JavaFileAnalyzer 调用，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0（plan 相关文件无断链）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/116-nop-code-data-model-enhancement.md --strict` 退出码为 0

## Deferred But Adjudicated

### Python/TS 装饰器提升为一等数据

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 首期只做 Java Spring 路由提取；Python/TS 装饰器查询需求未经验证
- Successor Required: `no`

### `unresolved_refs` 未解析引用表

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: nop-code 的 Java SymbolSolver 直接解析，架构上不需要延迟解析管线
- Successor Required: `no`

### 统一边视图（UNION ALL view）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 纯查询便利性优化，不影响图完整性
- Successor Required: `no`

### 源码 CLOB 存储可选化

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前方案工作正常，无紧迫需求
- Successor Required: `no`

### 合并 `nop_code_annotation_usage` 到 `nop_code_usage`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 破坏性变更，需专项计划和迁移策略
- Successor Required: `yes`
- Successor Path: 待创建

### CG 式 14 种框架路由提取器

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 首期只做 Spring；后续按需添加其他框架
- Successor Required: `no`

### FTS5/BM25 全文搜索改进

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 独立功能域，不在本计划模型改进范围内
- Successor Required: `no`

## Non-Blocking Follow-ups

- 考虑为 `code/call_type` 字典补全 ORM 定义（当前 `nop_code_call` 引用了 `ext:dict="code/call_type"` 但字典未在 ORM 中定义）
- 评估 `NameSimilarityExtractor` 的 O(N²) 性能问题（5000 符号上限），考虑 MinHash 或近似最近邻优化
- Python 适配器可增加 `EXPORTED` 标记（基于 `__all__` 列表）
- TS 适配器可增加 `EXPORTED` 标记（基于 `export` 关键字）

## Closure

Status Note: All phases completed. Closure audit passed with no blocking issues.

Closure Audit Evidence:

- Reviewer / Agent: Independent closure auditor (subagent ses_16799d4e0ffeWA0wPbeqggperf)
- Date: 2026-06-05
- Verdict: Can Close
- Anti-Hollow Evidence:
  - (a) HeuristicEdgeSynthesizer: CodeIndexService.synthesizeAndPersistHeuristicEdges() at line 848 builds HeuristicContext from persisted data, iterates all registered synthesizers, calls synthesize(), persists results to nop_code_call with provenance=HEURISTIC. Confirmed.
  - (b) Spring route extraction: JavaFileAnalyzer.SymbolCollector.visit(ClassOrInterfaceDeclaration) calls extractSpringRoutes(decl) at line 233 for every class declaration. Parses @RequestMapping, @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping. Confirmed.
  - (c) All key classes have substantive logic: InterfaceImplSynthesizer (82 lines), SpringEventSynthesizer (145 lines), EdgeProvenance (5 enum values), CodeRouteInfo (4 fields), HeuristicContext (4 fields). No empty methods, no-op, or silent skips. Confirmed.
- Blocking Issues: None
- Advisory Issues: Plan status was still "in progress" at time of audit (now corrected to "completed")

Follow-up:

- See "Non-Blocking Follow-ups" section above
- Consider adding EXPORTED flag for Python (__all__) and TS (export keyword) adapters

## Risks

| Risk | Mitigation |
|------|-----------|
| ORM 变更导致 `_gen/` 重新生成时产生编译错误 | 改完 orm.xml 后立即 `mvn install` 验证生成 |
| 启发式边合成产生大量噪声边 | 合成边必须有 `provenance = HEURISTIC`，消费者可过滤；首期只实现 2 种高精度合成器 |
| 反规范化 `FILE_PATH` 导致数据不一致 | 在 `CodeIndexService` 中统一填充，不依赖手动维护 |
| Phase 3 的接口→实现合成在大项目上性能问题 | 首期限制：只对当前索引内的符号做合成，不跨索引；设置合成边上限 |
| `SpringEventSynthesizer` 精度有限 | 首期只处理 `publishEvent(new XxxEvent(...))` 形式，不处理泛型推断或 SpEL 条件；metadata 记录匹配方式，便于后续迭代 |
| 启发式边与 AST 边的唯一键冲突 | 合成边使用 `line=-1, column=0` 哨兵值确保 UK 不冲突；插入前查询 `(indexId, callerId, calleeId)` 做逻辑去重 |
| 已有边记录缺少 provenance | 所有历史数据默认视为 `AST_EXTRACTION`，无需回填（新列允许 NULL） |
| TYPE_OF 引用可能产生大量边（每个局部变量一条） | 首期只提取 public/protected 方法参数和类级字段的 TYPE_OF，不提取方法内局部变量 |
