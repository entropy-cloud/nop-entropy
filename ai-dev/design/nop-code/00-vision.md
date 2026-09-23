# nop-code 高层设计原则

**日期**：2026-05-02（更新于 2026-09-23）
**范围**：`nop-code` 子系统
**状态**：active

---

## 一、产品定位

nop-code 是 Nop 平台的**集群式多语言代码索引与语义分析服务**，为 AI 辅助代码分析提供结构化索引。

核心使命：将源码解析为语言无关的结构化图模型（`CallGraph` + `SymbolTable`，通过 `IGraph` 抽象暴露），在此之上提供社区检测、影响分析、执行流追踪等图算法能力。AI 层通过 GraphQL 访问，不需要独立的 MCP 服务。

三个不可替代的价值：
1. **语言无关的通用模型**——SymbolKind / AccessModifier / RelationType 覆盖 Java / Python / TypeScript，语言特有语义通过 extData JSON 承载
2. **通用图算法复用 + 领域分析内聚**——通用图算法在顶层共享模块 `nop-graph/`（`IGraph` + 算法），nop-code 专属分析在 core/service，流级分析独立在 `nop-code-flow`
3. **确定性分析与语义推断的统一**——AST 提取的确定性边与 LLM 辅助的语义边共存于同一模型，通过 confidence 级别区分

### 架构定位（2026-09-23 增补）

nop-code 的目标部署形态是**集群式索引服务**，不是单机本地工具。以下方向中，存储抽象已落地，其余为**目标架构**（当前实现以括号内说明为准）：

- **存储抽象（已落地）**：`io.nop.graph.api.IGraph`（`getOutEdges`/`getInEdges` + `Edge`）即"屏蔽底层存储"的接口；`CallGraph`/`SymbolTable` 是内存具体结构，`CodeCallGraph` 是 `IGraph` 适配器。切换后端只需换 `IGraph` 实现，业务逻辑无感。
- **查询路径无状态（目标）**：查询不应在 JVM 内存中全量重建图；生产环境本地遍历下推 `IGraph` 后端，全局算法结果（社区/中心性/入口点评分）在索引期物化、查询期只读。（当前：`CodeCacheManager` 堆内缓存 + 全量 rebuild，待迁移）
- **集群索引（目标）**：索引构建作为批处理，可并行/分片；需先定义源码分发、原子发布与一致性模型。（当前：单节点参考应用，无集群编排）
- **利用数据库图能力（待决策）**：生产环境可评估 PostgreSQL 图索引（`ltree`/递归 CTE）或 Apache AGE；但需先确定生产 DB 与可移植性边界（参考应用当前为 MySQL/H2）。**全局算法无法由递归 CTE 求得**，只能局部遍历下推。

## 二、成功标准

1. 支持 Java / Python / TypeScript 三种语言的完整符号提取（符号、调用关系、继承关系、注解使用）
2. 图分析算法覆盖社区检测（Leiden + LabelPropagation）、入口点评分、影响分析、Hub/Bridge 检测、知识缺口分析
3. 流级分析覆盖执行流追踪、风险评分变更分析、死代码检测
4. 语义边可确定性提取（名称相似度、文档关键词、注解模式），LLM 增强提取通过 nop-ai 集成
5. 所有能力通过 GraphQL API 暴露，AI 层无需了解内部模块结构
6. 语言适配器与核心模型完全解耦——新增一种语言只需实现 ICodeFileAnalyzer + ILanguageAdapter，不修改核心代码

## 三、不可违反的约束

| # | 约束 | 含义 |
|---|------|------|
| 1 | **语言无关的通用模型** | CodeSymbol / CodeMethodCall / CodeFileAnalysisResult 等核心模型不包含任何语言特有字段。语言特有扩展通过 `extData`（JSON）承载 |
| 2 | **core 只放模型和数据结构** | nop-code-core 是所有模块的公共依赖。通用图算法不放 core（放顶层 `nop-graph`）；core 只依赖 `nop-graph-api` 的 `IGraph` 抽象 |
| 3 | **算法基于 `IGraph` 抽象** | 所有图算法基于 `io.nop.graph.api.IGraph`（+ `Edge`），不依赖具体存储实现，也不依赖任何语言特定的 AST 类型 |
| 4 | **查询走 GraphQL** | 所有外部访问通过 BizModel 暴露的 GraphQL API；不新建自有 REST 端点、不建独立 MCP 服务。Nop 标准 BizModel 的 `/r/`、`/jsonrpc` 等适配通道仍属 GraphQL 引擎分发，不算"自有 REST" |
| 5 | **边类型统一存储** | 不新增独立边表。TESTED_BY 和 REFERENCES 复用 `nop_code_usage.kind` 枚举扩展（`kind` 枚举当前尚未含这两项，属待扩展） |
| 6 | **Nop 平台集成** | 使用 IJdbcTemplate、nop-orm、nop-search-api 等平台基础设施，不绕过平台自建基础设施 |
| 7 | **存储形式由接口屏蔽（已落地）** | `IGraph` 是存储抽象边界。`CallGraph`/`SymbolTable` 是内存简易实现，`CodeCallGraph` 是适配器；数据库后端是 `IGraph` 的另一实现。业务逻辑只依赖 `IGraph`，切换存储不改业务代码 |
| 8 | **查询路径无状态（目标）** | 目标：查询不在 JVM 内存中全量重建图。当前 `CodeCacheManager` 堆内缓存 + 全量 rebuild 违反此约束，属待迁移项。全局算法结果需索引期物化 |
| 9 | **框架适配不入核心（目标）** | 目标：核心不硬编码框架语义，框架模式通过 `IEntryPointPatternProvider` 等 SPI 可插拔加载，DSL 为远期选项。当前 `JavaFileAnalyzer`/`FlowDetector`/`DeadCodeDetector` 存在硬编码 Spring 逻辑，属待迁出项 |

## 四、显式 Non-Goals

本系统**不做**以下事情：

| Non-Goal | 理由 |
|----------|------|
| IDE 集成（Language Server Protocol） | nop-code 定位为服务端索引，IDE 集成由专用 LSP 服务负责 |
| 代码生成 / 代码重构 | 只读索引服务，代码修改由上层工具（IDE/CI）负责 |
| 运行时分析 / 性能剖析 | 聚焦静态结构分析，运行时行为由 APM 工具负责 |
| MCP 独立服务层 | AI 层通过 GraphQL 访问已足够，额外协议转换无收益 |
| Elasticsearch 全文搜索 | 嵌入式 Lucene（通过 nop-search）对单机代码索引足够，无需分布式搜索引擎 |
| 交互式图谱可视化 | 通过 GraphML / Mermaid 导出满足静态可视化需求，交互式可视化由前端工具负责 |
| 多 VCS 支持（SVN 等） | Nop 项目均为 Git，通过 ProcessBuilder 调用 git 命令 |

## 五、设计收敛路径

设计按以下顺序收敛，不可逆序：

1. **先定义通用代码模型**（CodeSymbol / CodeMethodCall / CodeFileAnalysisResult / 枚举体系）
2. **再定义核心接口**（ICodeFileAnalyzer / ILanguageAdapter / IProjectAnalyzer / 分析算法接口）
3. **再实现语言适配器**（Java → Python → TypeScript，验证模型通用性）
4. **再实现图算法**（社区检测 → 入口点评分 → 影响分析 → Hub/Bridge → 知识缺口）
5. **再实现流级分析**（执行流追踪 → 变更分析 → 死代码检测）
6. **最后补语义边、nop-search 集成、图导出等上层能力**

只要这条顺序不乱，设计就不会滑入"先写算法再补模型"的陷阱。

## 六、必须由人决策的决策点

以下决策不可由 AI 自行发明，必须经过显式确认：

1. 新增语言的适配器是否实现（当前 Java ✅ / Python ✅ / TypeScript ✅，其他语言需人工评估）
2. 新增边类型的决策（当前 4+3 种，边类型影响存储模型和算法行为）
3. LLM 语义边提取的成本预算和触发策略（依赖 nop-ai 模块，涉及 API 调用成本）
4. 定位变更（从"代码索引与语义分析服务"改为其他定位）
5. ID 生成策略变更（当前有碰撞风险，改用 SHA-256 等方案需确认）

## 七、核心取舍

- **保留**：语言无关的通用模型、通用图算法下沉 `nop-graph`、确定性 + 语义推断统一模型
- **保留（非核心路径）**：LLM 语义边提取——确定性提取器已满足基本需求，LLM 增强为远期能力
- **去除**：IDE 集成、代码生成、运行时分析、MCP 服务、分布式搜索引擎、SVN 支持
- **聚焦**：静态结构索引 + 图算法分析 + GraphQL API 暴露

## 八、设计不变量

以下不变量不可违反：

1. CodeSymbol 的 kind 字段必须是 CodeSymbolKind 枚举，不得使用自由字符串
2. 语言适配器不得将语言特有字段硬编码到通用模型中，必须通过 extData JSON 承载
3. 图算法必须基于 `io.nop.graph.api.IGraph` 抽象接口，不得直接依赖具体内存实现或 ORM 实体
4. 通用图算法归顶层 `nop-graph`；`nop-code` 单向依赖 `nop-graph`，不得反向；`nop-code-flow` 只依赖 `nop-code-core`
5. `nop-code-core` 不得依赖 JGraphT/Leiden 算法库；它只依赖 `nop-graph-api` 的 `IGraph` 抽象（算法实现依赖在 `nop-graph-core`）
6. 所有 GraphQL API 按聚合根归属，不得创建无对应实体的独立 BizModel
7. 增量分析必须基于 fingerprint 机制，不得跳过变更检测全量重建
8. nop-search 集成必须有降级策略（无搜索引擎时 fallback 到 DB LIKE 查询）
9. （目标）查询路径不在 JVM 内存中全量重建图；全局算法结果索引期物化、查询期只读。当前实现违反，属待迁移
10. （目标）核心不硬编码框架语义；框架模式通过 SPI（`IEntryPointPatternProvider`）可插拔加载。当前 Spring 硬编码属待迁出

## 九、核心隐喻

nop-code 的运作方式：

1. **语言适配器层**：每种语言提供一个 ICodeFileAnalyzer 实现，将源码解析为语言无关的 CodeFileAnalysisResult
2. **项目分析器**：IProjectAnalyzer 扫描目录、自动识别语言、调度适配器、构建全局 `CallGraph` + `SymbolTable`（内存结构），并通过 `CodeCallGraph` 适配为 `IGraph`
3. **图算法层**：通用算法（社区检测、中心性、BFS 等）在顶层 `nop-graph` 中基于 `IGraph` 运行；nop-code 专属分析器（入口点评分、知识缺口）在 core/service
4. **流级分析层**：`nop-code-flow` 基于符号表/调用图输出，追踪执行流、分析变更风险、检测死代码
5. **服务编排层**：BizModel 聚合所有能力，通过 GraphQL API 对外暴露

### 与 Nop 平台的集成

| 集成点 | 方式 |
|--------|------|
| ORM 持久化 | nop-code-dao（标准 Nop 分层） |
| 搜索引擎 | nop-search-api（仅接口依赖，降级到 DB LIKE） |
| AI 辅助 | nop-ai 模块（LLM 语义边提取，远期） |
| API 暴露 | GraphQL（BizModel + xmeta） |
| IoC 注册 | 语言适配器通过 NopIoC 自动发现；入口点模式提供者的 SPI 注册为**目标**（当前 `FlowDetector` 内置硬编码 Spring provider） |

## 十、拒绝了什么

| 方案 | 拒绝理由 |
|------|---------|
| 把通用图算法放 nop-code 内（原 `nop-code-graph`） | 图算法设计为跨模块复用（当前 `nop-code` 使用，未来 `nop-wf`/`nop-task`/`nop-stream` 可用）；放 nop-code 内会造成跨模块反向依赖。**已迁移到顶层 `nop-graph`** |
| 每个图算法独立模块 | 图算法间共享 `nop-graph-core` 依赖与结果类型，拆太细增加依赖管理复杂度 |
| 新增独立边表 | TESTED_BY 和 REFERENCES 可复用 `nop_code_usage.kind` 枚举扩展，无需新表 |
| 自建 Lucene 集成 | nop-search 已封装 Lucene BM25 + KNN + RRF，不自建 |
| Elasticsearch | 嵌入式 Lucene 对单机代码索引足够 |
| JGit 替代 ProcessBuilder | git diff 只需简单的行级别解析，不引入 JGit 新依赖 |
| 硬编码框架入口点模式字典 | 应通过 `IEntryPointPatternProvider` SPI 可插拔加载，支持用户 Delta 扩展；当前内置 Spring provider 属待迁出 |
| 所有方法放 NopCodeIndex | 违反单一职责，索引级操作归 NopCodeIndex，实体级查询归对应 BizModel |
| 为调用关系单独建 BizModel | 调用关系存储在 `nop_code_usage` 表，调用链查询归 NopCodeSymbolBizModel |
| Token 效率分级 | GraphQL Selection Set 已提供字段级裁剪，额外分级收益不足 |
| 引入 MCP 服务层 | GraphQL 已是通用 API 暴露协议，新增 MCP 是冗余协议层（2026-09-23 决策） |
| 新造 `ICallGraph`/`ISymbolTable` 接口 | `nop-graph-api` 的 `IGraph` 已提供"屏蔽底层存储"的抽象；`CallGraph`/`SymbolTable` 是内存实现，`CodeCallGraph` 是适配器。新造接口会造成平台双图接口（2026-09-23 校正） |
| Hypergraph（超边） | 无明确用例；`nop_code_flow_membership` 已表达"多符号同属一个执行流"。暂缓，需真实场景验证（见 `graph-discovery-and-export-design.md`） |
