# code-review-graph vs nop-code 深度功能对比与补充分析

> Status: open
> Date: 2026-05-25
> Scope: nop-code 模块功能补全，参考 code-review-graph (CRG) v2.3.3
> Conclusion: （待评审）

## Context

- **目标**：通过对比 [code-review-graph](https://github.com/tirth8205/code-review-graph)（以下简称 CRG）和 nop-code 模块，识别 CRG 已实现但 nop-code 缺失或有提升空间的功能，为 nop-code 后续迭代提供决策参考。
- **CRG 定位**：面向 AI 编码助手（Claude Code/Cursor/Copilot）的持久化代码知识图谱工具，Python 实现，SQLite 存储，MCP 协议暴露。核心卖点：8.2x token 缩减。
- **nop-code 定位**：Nop 平台的多语言代码索引与语义分析服务，Java 实现，ORM 持久化，GraphQL API 暴露。核心价值：为 AI 辅助代码分析提供结构化索引。
- **两者定位相似度**：均构建代码结构图，支持符号提取、调用图、继承图、社区检测、影响分析。CRG 偏重 AI 集成和 token 效率，nop-code 偏重平台化和企业级 API。

---

## 一、功能全景对比

### 1.1 核心构建能力

| 能力 | CRG | nop-code | 差距 |
|------|-----|----------|------|
| **全量构建** | 并行 AST 解析（ProcessPoolExecutor） | 并行批处理（ExecutorService + BatchQueue） | 无显著差距 |
| **增量更新** | git diff → SHA-256 → 受影响文件重解析 → 条件性后处理 | mtime → SHA-256 → 变更集检测 → DB 持久化指纹 | **CRG 更完善**：检测受影响依赖文件（2-hop），条件性后处理（仅变化语言触发对应 resolver） |
| **文件监控** | watchdog 实时监控 + 300ms 防抖 + crg-daemon 多仓库守护 | 无 | **nop-code 缺失** |
| **语言支持** | 24 语言 + Jupyter | Java / Python / TypeScript（3 种） | **nop-code 少很多**，但扩展机制已就绪（ILanguageAdapter） |
| **VCS 支持** | Git + SVN 自动检测 | Git（隐含，无显式 VCS 抽象） | 小差距 |

### 1.2 代码图谱能力

| 能力 | CRG | nop-code | 差距 |
|------|-----|----------|------|
| **AST 解析** | tree-sitter（所有语言统一） | Java: JavaParser+SymbolSolver；Python/TS: tree-sitter | nop-code Java 解析更强（符号解析），但通用性不如 CRG |
| **边类型** | CALLS / IMPORTS_FROM / INHERITS / IMPLEMENTS / CONTAINS / TESTED_BY / DEPENDS_ON / REFERENCES（8 种） | calls / inheritances / annotationUsages / fileDependencies（4 种）。注意：CodeSymbol 已有 parentId/declaringSymbolId 字段存储父子关系，但无独立 CONTAINS 边表 | **nop-code 缺少**：TESTED_BY（测试覆盖关联）、REFERENCES（通用引用）。IMPLEMENTS 可从已有 inheritances 拆分。CONTAINS 是否需独立边表待讨论 |
| **边置信度** | EXTRACTED / INFERRED / AMBIGUOUS 三级 | EXTRACTED / INFERRED 两级 | 小差距，nop-code 设计文档已规划 AMBIGUOUS |
| **跨文件解析** | TS path alias / Dart pubspec / ReScript 跨模块 | Java/Python/TS import resolver | **nop-code 缺少**框架特定解析（Spring DI、Temporal、Kafka 等） |
| **框架感知** | Spring @Autowired/@Inject / Temporal workflow / Kafka @KafkaListener / ~40 种框架装饰器模式 | 无 | **nop-code 完全缺失** |

### 1.3 分析能力

| 能力 | CRG | nop-code | 差距 |
|------|-----|----------|------|
| **执行流追踪** | 入口点检测（~70 种框架模式） → BFS 前向追踪 → 五维关键度评分 → 增量流更新 | **完全缺失** | **核心差距** |
| **风险评分变更分析** | git diff → 行级别映射 → 五维风险评分（flow 参与度 / 社区交叉 / 测试缺口 / 安全敏感 / 调用者数） | 无 | **核心差距** |
| **社区检测** | Leiden + file-based 降级 + 超大社区分裂 + 批量 O(E) 内聚度 + 架构概览 | Leiden + LabelPropagation + 大图优化 + 内聚度 | nop-code 实现**略强**（双算法），但缺少架构概览和超大社区处理 |
| **入口点评分** | 融合在执行流中，~70 种框架模式匹配 | `calleeCount/(callerCount+1)` 公式，5 级分类 | **CRG 远更丰富**：框架装饰器模式库 + 约定名模式库 |
| **影响分析** | BFS blast radius + 变更上下文 | BFS 双向遍历 + 风险等级 | 功能等价，CRG 多了变更关联 |
| **Hub/Bridge 节点** | 度中心性（hub）+ 介数中心性（bridge） | God Node 识别（入口点评分附带） | **nop-code 缺少**介数中心性（bridge node） |
| **知识缺口分析** | 孤立节点 + 薄弱社区 + 未测试热点 + 单文件社区 | 无 | **nop-code 缺失** |
| **意外连接发现** | 跨社区/跨语言/边缘-枢纽/跨测试边界/异常边类型的复合惊奇评分 | 无 | **nop-code 缺失** |
| **死代码检测** | 多维度排除（框架/测试/dunder/构造器/ORM/CDK/类型注解等）+ bare-name 模糊匹配 + 导入图验证 | 无 | **nop-code 缺失** |

### 1.4 搜索能力

| 能力 | CRG | nop-code | 差距 |
|------|-----|----------|------|
| **全文搜索** | SQLite FTS5（Porter stemmer + unicode61） + BM25 排名 | DB LIKE + 内存多因子评分（scoreSymbolNameMatch + scoreFullTextMatch + scoreCombined） | **nop-code 差距大**：无倒排索引/BM25，大项目性能堪忧（但评分逻辑比纯 LIKE 更精细） |
| **语义向量搜索** | 4 Provider（sentence-transformers / Gemini / MiniMax / OpenAI 兼容） | 无 | **nop-code 完全缺失** |
| **混合搜索** | FTS5 + 向量 → RRF 融合 + 查询感知 kind boosting | 无 | **nop-code 完全缺失** |
| **搜索降级** | FTS5+向量 → FTS5 → LIKE 三级降级 | 仅 LIKE | **nop-code 缺失** |

### 1.5 AI 集成能力

| 能力 | CRG | nop-code | 差距 |
|------|-----|----------|------|
| **MCP 服务器** | 28 tools + 5 prompts + stdio/HTTP 双传输 | 无 | **nop-code 缺失**（但 Nop 平台有 GraphQL API 可类比） |
| **工作流 Prompt** | review_changes / architecture_map / debug_issue / onboard_developer / pre_merge_check | 无 | **nop-code 缺失** |
| **Token 效率引导** | minimal context → next_tool_suggestions → detail_level 分级 | 无 | **nop-code 缺失** |
| **PreToolUse Hook** | 拦截 Grep/Read 调用注入图知识 | 无 | **nop-code 缺失** |
| **上下文提示** | 意图推断 + 工具邻接图 + 会话状态追踪 | 无 | **nop-code 缺失** |

### 1.6 可视化与导出

| 能力 | CRG | nop-code | 差距 |
|------|-----|----------|------|
| **交互式图谱** | D3.js 力导向图，4 种模式（full/community/file/auto），搜索高亮，社区着色 | Web 页面（code-browser/call-hierarchy/type-hierarchy/dashboard） | nop-code 有基础 UI，但无交互式图谱可视化 |
| **导出格式** | GraphML / Neo4j Cypher / Obsidian Vault / SVG / Markdown Wiki | 无 | **nop-code 完全缺失** |
| **Wiki 生成** | 按社区生成 Markdown（概览+成员+流+依赖） | 无 | **nop-code 完全缺失** |

### 1.7 其他能力

| 能力 | CRG | nop-code | 差距 |
|------|-----|----------|------|
| **重构工具** | 重命名预览 + 死代码检测 + 社区驱动建议 + dry-run | 无 | **nop-code 完全缺失** |
| **图快照对比** | 快照 → 集合差运算 → 新增/删除/社区变更 | 无 | **nop-code 完全缺失** |
| **Q&A 记忆** | YAML frontmatter + Markdown 持久化 | 无 | **nop-code 完全缺失** |
| **多仓库支持** | 注册中心 + 守护进程 + 跨仓库搜索 | 多索引（indexId） | nop-code 有基础隔离，但无守护进程和跨仓库搜索 |
| **评估框架** | Token 效率 / 影响准确性 / 构建性能 / 流完整性 / 搜索质量（5 维） | 单元测试覆盖 | **nop-code 缺少**系统化评估 |

---

## 二、可补充功能优先级排序

基于 CRG 功能对 nop-code 的价值、实现难度、与 Nop 平台设计理念的契合度进行综合评估。

### P0 — 核心缺失，高价值，建议立即规划

#### 2.1 执行流追踪（Execution Flow Tracing）

**CRG 实现**：
- 入口点检测：3 种策略 OR（无入边根节点 / ~40 种框架装饰器模式 / ~30 种约定名模式）
- BFS 前向追踪：沿 CALLS 边，max_depth=15
- 五维关键度评分：`file_spread(0.30) + external_score(0.20) + security_score(0.25) + test_gap(0.15) + depth_score(0.10)`
- 增量追踪：变更文件 → 受影响流 ID → 删除+重追踪

**nop-code 补充方案**：
- 新增 `ExecutionFlow` 模型（name, entryPoint, depth, criticality, pathNodes）
- 新增 `IFlowDetector` 接口 + `FlowDetector` 实现
- 入口点检测复用 `EntryPointScorer` 的 ENTRY_POINT 分类
- 增量追踪复用 `IncrementalDetector` 的变更集
- 持久化到新表 `nop_code_flow` + `nop_code_flow_membership`
- 通过 `ICodeIndexService` 暴露查询 API

**价值**：理解代码执行路径、安全审计、变更影响范围评估的基础能力。

**实现难度**：中等。BFS 追踪逻辑与 `ImpactAnalyzer` 类似，核心新增是入口点模式库和关键度评分。

---

#### 2.2 风险评分变更分析（Risk-Scored Change Analysis）

**CRG 实现**：
- `parse_git_diff_ranges()` → `{file: [(start, end)]}` 行级别映射
- `map_changes_to_nodes()` → 行范围重叠检测映射到图节点
- `compute_risk_score()` → 五维：flow 参与度(0.25) + 社区交叉(0.15) + 测试缺口(0.30→0.05) + 安全敏感(0.20) + 调用者数/20(0.10)
- `analyze_changes()` → 完整管线

**nop-code 补充方案**：
- 新增 `IChangeAnalyzer` 接口 + `ChangeAnalyzer` 实现
- git diff 解析：可使用 JGit 或调用 git 命令
- 行映射：复用 `CodeSymbol` 的 line/column 信息
- 风险评分：复用 `ImpactAnalyzer` + `EntryPointScorer` + `CommunityDetector` 的输出
- 新增 `ChangeAnalysisResult` DTO

**价值**：代码审查、CI/CD 集成、变更安全评估的核心能力。

**实现难度**：中等。依赖执行流追踪（P0-2.1），风险评分各维度已有基础数据。

---

#### 2.3 搜索增强（Search Enhancement）

**CRG 实现**：
- FTS5 全文索引（Porter stemmer + BM25）
- 4 种向量嵌入 Provider
- RRF 混合搜索 + 查询感知 kind boosting
- 三级降级策略

**nop-code 补充方案**：
- **Phase 1**（立即可做）：集成 Lucene 或使用 Nop 平台的全文搜索能力，替换 DB LIKE
- **Phase 2**（中期）：集成 `nop-ai` 模块的嵌入能力，实现向量搜索
- **Phase 3**：RRF 混合搜索 + kind boosting

**价值**：搜索是 AI 辅助开发最常用的能力，当前 DB LIKE 在大项目上性能不可接受。

**实现难度**：
- Phase 1：低。Lucene 集成成熟。
- Phase 2：中。依赖嵌入模型集成。
- Phase 3：低。RRF 算法简单。

---

### P1 — 重要增强，中期规划

#### 2.4 框架感知解析（Framework-Aware Parsing）

**CRG 实现**：
- Spring DI：@Autowired/@Inject/@Value + Lombok 构造器 → 接口到实现解析
- Temporal：@WorkflowMethod/@ActivityMethod → 工作流图谱
- Kafka：@KafkaListener → 消费者关联
- ~40 种框架装饰器模式（Flask/Django/Spring/Angular/React/Celery/CDK 等）

**nop-code 补充方案**：
- 新增 `IFrameworkResolver` 接口
- Java 框架：利用已有 `CodeAnnotationUsage` 数据，实现 Spring DI 解析器
- Python 框架：装饰器模式匹配
- 框架解析器通过 Nop IoC 自动发现

**价值**：对 Java 企业项目的代码理解至关重要（Spring 是主流），nop-code 已有注解数据可直接利用。

**实现难度**：中低。已有 `CodeAnnotationUsage` 数据，只需添加解析逻辑。

---

#### 2.5 死代码检测（Dead Code Detection）

**CRG 实现**：
- 多维度排除：框架入口点、测试文件、dunder 方法、构造器、@property/@abstractmethod、ORM/Pydantic 基类子类、CDK 构造、类型注解引用
- 检查：CALLS/TESTED_BY/IMPORTS_FROM/REFERENCES/INHERITS 入边（含 bare-name 模糊匹配）
- 导入图 2-hop 验证可信度

**nop-code 补充方案**：
- 新增 `IDeadCodeDetector` 接口 + `DeadCodeDetector` 实现
- 利用现有 `CallGraph`（反向索引）+ `SymbolTable`
- 排除规则可配置（Nop 配置机制）
- 复用 `EntryPointScorer` 的 ENTRY_POINT 分类排除框架入口

**价值**：代码质量评估的核心能力。

**实现难度**：低。算法简单，主要工作是排除规则的定义和调优。

---

#### 2.6 依赖增量更新增强（Incremental Update Enhancement）

**CRG 实现**：
- `find_dependents()`：通过 IMPORTS_FROM/CALLS/INHERITS 边找受影响文件（最多 2-hop，500 文件上限）
- 条件性后处理：仅当相关语言文件变化时运行框架特定解析器

**nop-code 当前**：
- `IncrementalDetector` 只检测直接变更文件，不追踪受影响的依赖文件
- 无条件性后处理概念

**nop-code 补充方案**：
- 增强 `IncrementalDetector`：新增 `findDependentFiles()` 方法，沿依赖图 BFS 查找受影响文件
- 后处理管线：将分析算法（社区检测、流追踪）拆为独立步骤，支持增量触发

**价值**：大项目增量更新的效率关键。

**实现难度**：低。核心逻辑已具备（CallGraph、依赖图），只需串联。

---

#### 2.7 知识缺口与意外连接分析（Knowledge Gap & Surprising Connections）

**CRG 实现**：
- 知识缺口：孤立节点、薄弱社区、未测试热点、单文件社区
- 意外连接：跨社区/跨语言/边缘-枢纽/跨测试边界/异常边类型的复合惊奇评分

**nop-code 补充方案**：
- 新增 `IKnowledgeGapAnalyzer` 和 `ISurprisingConnectionAnalyzer` 接口
- 孤立节点：利用 `CallGraph` 找无连接节点
- 薄弱社区：利用 `CommunityDetector` 的内聚度计算
- 未测试热点：需要新增 TESTED_BY 边类型
- 意外连接：跨社区边检测 + 评分

**价值**：架构健康度评估、代码审查辅助。

**实现难度**：中。需要 TESTED_BY 边支持。

---

### P2 — 有价值增强，长期规划

#### 2.8 多格式导出（Multi-Format Export）

**CRG 支持**：GraphML / Neo4j Cypher / Obsidian Vault / SVG / Markdown Wiki

**nop-code 补充方案**：
- 新增 `IGraphExporter` 接口 + 多种实现
- GraphML 导出：JGraphT 原生支持
- Mermaid 导出：生成 Mermaid 语法文本（比 SVG 更适合 Markdown 集成）
- Wiki 生成：利用 `CommunityDetector` 结果 + `SymbolTable`

**价值**：文档生成、架构可视化、与外部工具集成。

**实现难度**：低。

---

#### 2.9 交互式图谱可视化（Interactive Graph Visualization）

**CRG 实现**：D3.js 力导向图，4 种模式（full/community/file/auto），社区钻入

**nop-code 补充方案**：
- nop-code 已有 Web 前端基础设施（view.xml + page.yaml）
- 方案 A：前端集成 D3.js 或 Cytoscape.js
- 方案 B：生成 Mermaid 图嵌入 Markdown
- 方案 C：后端导出 JSON，前端渲染

**价值**：用户体验提升。

**实现难度**：中高。前端工作量较大。

---

#### 2.10 图快照对比（Graph Diff）

**CRG 实现**：快照 → 集合差运算 → 新增/删除/社区变更节点

**nop-code 补充方案**：
- 新增 `IGraphDiffer` 接口 + `GraphDiffer` 实现
- 利用 Nop 平台的 Delta 思想（可逆计算原理）
- 快照可基于 `ProjectAnalysisResult` 的不可变快照

**价值**：追踪架构演化、代码评审辅助。

**实现难度**：低。算法简单（集合差运算）。

---

#### 2.11 重构工具（Refactoring Tools）

**CRG 实现**：重命名预览 + 安全应用（refactor_id + 10 分钟过期 + dry-run）+ 社区驱动建议

**nop-code 补充方案**：
- 设计文档 `code-index-query-api-design.md` 已规划 P2 级别"代码重写/变换"
- 新增 `IRefactorService` 接口
- 重命名：基于 `SymbolTable` 全局搜索 + 调用点定位
- 安全机制：token 过期 + dry-run + 路径遍历防护

**价值**：从只读索引到可操作的工具，质的变化。

**实现难度**：中。核心是安全机制和文件修改的原子性保证。

---

#### 2.12 Token 效率机制（Token Efficiency）

**CRG 实现**：
- `get_minimal_context`：~100 tokens 获取全局上下文
- `next_tool_suggestions`：引导 AI 选择最优下一步
- `detail_level` 分级：minimal / standard / detailed
- 工具调用限制：≤3 次/轮

**nop-code 补充方案**：
- 在 GraphQL API 层新增 `minimalContext` 查询
- 所有查询 API 新增 `detailLevel` 参数
- 响应中包含 `nextSuggestedQueries`

**价值**：AI 辅助开发场景的核心需求。

**实现难度**：低。主要是 API 设计和响应裁剪。

---

#### 2.13 Hub/Bridge 节点分析

**CRG 实现**：
- Hub：度中心性最高（架构热点）
- Bridge：介数中心性最高（架构瓶颈）

**nop-code 补充方案**：
- Hub：已有 `EntryPointScorer` 可近似（高 calleeCount = hub）
- Bridge：需新增介数中心性计算（JGraphT 有 BetweennessCentrality 实现）

**价值**：架构健康度评估。

**实现难度**：低。JGraphT 直接支持。

---

### P3 — 锦上添花，按需实现

#### 2.14 文件监控守护进程（File Watcher Daemon）

**CRG 实现**：watchdog 实时监控 + 300ms 防抖 + crg-daemon 多仓库守护 + 30 秒健康检查

**nop-code 考虑**：
- Nop 平台已有定时任务和事件机制
- 可实现为 Nop后台任务（@Scheduled 或 Quartz）
- 但 Java 生态中已有 better-files/watchservice 等成熟方案

**价值**：实时性要求高的场景（CI/CD、实时协作）。

**实现难度**：中。

---

#### 2.15 Q&A 记忆持久化（Q&A Memory）

**CRG 实现**：YAML frontmatter + Markdown 持久化

**nop-code 考虑**：
- Nop 平台已有完善的 ORM 和文件存储
- 可直接持久化到 `nop_code_qa_memory` 表
- 更适合企业级场景（搜索、审计、权限控制）

**价值**：AI 会话持续性。

**实现难度**：低。

---

#### 2.16 PreToolUse Hook / 上下文感知提示

**CRG 实现**：
- PreToolUse Hook：拦截 AI 工具调用注入图知识
- 意图推断 + 工具邻接图 + 会话状态追踪

**nop-code 考虑**：
- 这是 MCP 生态特有的功能，nop-code 的定位是平台服务
- 可通过 Nop 的 GraphQL subscription 或 webhook 实现类似能力
- 但优先级较低，更适合作为 AI 集成层的功能

**价值**：AI 集成深度。

**实现难度**：高。需要深度集成 AI 工具链。

---

#### 2.17 评估框架（Evaluation Framework）

**CRG 实现**：Token 效率 / 影响准确性（F1/P/R）/ 构建性能 / 流完整性 / 搜索质量（MRR）

**nop-code 补充方案**：
- 新增 `nop-code-eval` 测试模块
- 基准测试：使用 JMH（Java Microbenchmark Harness）
- 准确性测试：使用已知项目（如 nop-entropy 自身）作为 ground truth

**价值**：质量保证、性能回归检测。

**实现难度**：中。

---

## 三、边类型补充分析

nop-code 当前 4 种边类型（calls/inheritances/annotationUsages/fileDependencies）对比 CRG 的 8 种，建议补充：

| 新边类型 | 优先级 | 用途 | 实现难度 |
|----------|--------|------|---------|
| **CONTAINS** | P0 | 父子关系（类包含方法、文件包含类），支持粒度查询 | 低（AST 解析时已有父子信息） |
| **TESTED_BY** | P1 | 测试关联，支持覆盖率分析和测试缺口检测 | 低（通过命名约定或注解匹配） |
| **REFERENCES** | P1 | 通用引用（字段访问、类型引用等），增强影响分析完整性 | 中（需 AST 级别引用提取） |
| **IMPLEMENTS** | P2 | 接口实现关系（从已有 inheritances 拆分） | 低（已有 extends/implements 区分） |

---

## 四、nop-code 的优势（CRG 没有的）

公平起见，记录 nop-code 已有但 CRG 缺失的优势：

| 能力 | nop-code | CRG |
|------|----------|-----|
| **Java 符号解析** | JavaParser + SymbolSolver，精确解析方法签名、参数类型、返回类型 | tree-sitter 基于模式匹配，无符号求解 |
| **企业级持久化** | ORM 实体 + 多表关系 + 数据库索引 | SQLite 单文件 |
| **GraphQL API** | 类型安全的嵌套查询 | MCP tool 扁平 JSON |
| **Nop 平台集成** | IoC 自动发现、BizModel、权限、Web UI | 独立工具 |
| **多算法回退** | Leiden + LabelPropagation 双算法 | Leiden + file-based |
| **设计可扩展性** | 7 个扩展接口 + 适配器模式 | 硬编码较多 |
| **注解使用追踪** | 完整的 `CodeAnnotationUsage` 模型 | 无专门注解追踪 |

---

## 五、实施路线建议

```
Phase 1 (P0) — 核心能力补齐
├── 2.1 执行流追踪
├── 2.2 风险评分变更分析（依赖 2.1）
├── 2.3 搜索增强 Phase 1（Lucene 集成）
└── 边类型补充：CONTAINS + TESTED_BY

Phase 2 (P1) — 分析能力增强
├── 2.4 框架感知解析（Spring DI）
├── 2.5 死代码检测
├── 2.6 增量更新增强
├── 2.7 知识缺口分析
└── 2.3 搜索增强 Phase 2（向量搜索）

Phase 3 (P2) — 工具化与可视化
├── 2.8 多格式导出
├── 2.10 图快照对比
├── 2.12 Token 效率机制
├── 2.13 Hub/Bridge 节点
└── 2.11 重构工具（设计文档已规划）

Phase 4 (P3) — 生态完善
├── 2.9 交互式图谱可视化
├── 2.14 文件监控守护进程
├── 2.15 Q&A 记忆
├── 2.16 AI Hook 集成
└── 2.17 评估框架
```

---

---

## 六、nop-search 搜索能力评估

### 6.1 nop-search 已有能力

`nop-search` 模块（`nop-search-api` + `nop-search-lucene` + `nop-search-core`）提供了完整的搜索基础设施：

| 能力 | 实现情况 | 与 CRG 对比 |
|------|---------|------------|
| **全文搜索** | Lucene BM25 + StandardQueryParser + 自定义 Analyzer（代码分隔符友好） | 等价于 CRG 的 FTS5 |
| **向量搜索** | Lucene KnnFloatVectorQuery + COSINE 相似度 | 等价于 CRG 的向量搜索 |
| **混合搜索** | RRF（k=60）融合文本+向量 | 等价于 CRG 的混合搜索 |
| **高亮** | Lucene Highlighter，title/content/summary 三字段 | CRG 无 |
| **标签过滤** | matchAllTags=true/false，AND/OR 语义 | CRG 用 kind boosting |
| **高级过滤** | FilterBean → Lucene Query 转换（EQ/NE/GT/LT/AND/OR/NOT） | CRG 无 |
| **向量自动生成** | `autoGenerateEmbedding=true` + `ITextEmbedding` 接口 | 等价于 CRG 的 4 Provider |
| **GraphQL 暴露** | `SearchEngineBizModel` 自动暴露 search/addDoc/removeDocs | CRG 用 MCP tools |
| **索引管理** | addDoc/addDocs/removeDocs/removeTopic/refreshBlocking | CRG 用 SQLite CTE |

### 6.2 结论：nop-search 完全够用

**nop-search 已经覆盖了 CRG 搜索能力的全部核心功能**（全文/向量/混合/RRF），甚至在某些方面更强（FilterBean 高级过滤、GraphQL 集成）。nop-code **不需要自建搜索基础设施**，只需要：

1. **集成 nop-search**：在 `nop-code-service` 的 pom.xml 添加 `nop-search-api` 依赖
2. **实现索引同步**：`CodeIndexService` 在 `saveFileResultInSession` 时同步调用 `ISearchEngine.addDoc`
3. **替换 searchCode 实现**：将当前 DB LIKE 查询改为调用 `ISearchEngine.search`
4. **实现 `ITextEmbedding`**：利用 `nop-ai` 模块或外部 API 提供向量嵌入

**具体集成方案**：

```java
// nop-code-service 新增
@Inject
ISearchEngine searchEngine;  // nop-search 的搜索引擎

// searchCode 方法改造
public List<CodeSearchResultDTO> searchCode(String indexId, String query, ...) {
    SearchRequest req = new SearchRequest();
    req.setTopic("nop-code-" + indexId);  // topic = 索引隔离
    req.setQuery(query);
    req.setSearchType(SearchType.HYBRID);  // 混合搜索
    req.setLimit(limit);
    SearchResponse resp = searchEngine.search(req);
    // 转换 SearchHit → CodeSearchResultDTO
}

// 索引时同步
void saveFileResultInSession(...) {
    // ... 原有 ORM 持久化逻辑 ...
    SearchableDoc doc = new SearchableDoc();
    doc.setId(symbolId);
    doc.setTitle(symbol.getQualifiedName());
    doc.setContent(symbol.getDocumentation() + " " + symbol.getSignature());
    doc.setTagSet(Set.of(symbol.getKind().name(), language));
    doc.setAutoGenerateEmbedding(true);  // 自动生成向量
    searchEngine.addDoc("nop-code-" + indexId, doc);
}
```

### 6.3 nop-search 相比 CRG 的不足（小问题）

| 问题 | 影响 | 解决方案 |
|------|------|---------|
| 无 offset 分页（仅 limit） | 深分页场景受限 | nop-code 可通过多次 limit+游标绕过 |
| 无自定义排序字段 | 无法按文件名/修改时间排序 | BM25 评分排序对代码搜索通常够用 |
| 无分面搜索（faceted） | 无法按 language/kind 聚合统计 | 可通过 DB 聚合查询补充 |
| 无中文分词器 | Java 代码搜索基本无影响 | 如需文档搜索可后续集成 |

---

## 七、nop-code GraphQL API 设计评估

### 7.1 当前架构：7 个聚合根，2 个"超级根"

| 聚合根 | 自定义方法数 | 角色定位 |
|--------|------------|---------|
| **NopCodeIndex** | 15 | 索引管理 + 图分析 + 依赖图 + 增量索引 |
| **NopCodeFile** | 6 | 文件查询 + 文件树 + Loader |
| **NopCodeSymbol** | 13 | 符号查询 + 层级 + 搜索 + 引用 |
| NopCodeDependency | 0 | 纯 CRUD（空壳） |
| NopCodeUsage | 0 | 纯 CRUD（空壳） |
| NopCodeCall | 0 | 纯 CRUD（空壳） |
| NopCodeInheritance | 0 | 纯 CRUD（空壳） |
| NopCodeAnnotationUsage | 0 | 纯 CRUD（空壳） |

### 7.2 存在的设计问题

#### 问题 1：方法归属不合理——"超级聚合根"反模式

`NopCodeIndex` 承载了 4 类不相关的能力（索引管理 + 图分析 + 依赖图 + 增量索引），违反了单一职责。依赖图的 4 个方法（getDeps / getReverseDeps / findCycles / getDepGraph）概念上应属于 `NopCodeDependency`。

**建议**：
- 将依赖图方法迁移到 `NopCodeDependencyBizModel`
- 将图分析方法（detectCommunities / getGraphAnalysis / getImpactAnalysis）迁移到 `NopCodeSymbolBizModel`（因为输入是 symbolId）
- `NopCodeIndex` 只保留索引生命周期管理（triggerFullIndex / triggerIncrementalIndex / indexDirectory / indexFile / deleteIndex / getStats / getIncrementalStatus）

#### 问题 2：5 个关系型聚合根是空壳 CRUD

`NopCodeCall`、`NopCodeInheritance` 等 5 个聚合根没有自定义方法，只有 CrudBizModel 的标准 CRUD。对关系型实体做 CRUD（谁会对 `nop_code_call` 表做 `findPage`？）几乎没有业务价值。

**两条路可选**：
- **路 A**：赋予它们业务意义。例如 `NopCodeCall` 暴露 `findCallers(calleeId)` / `findCallees(callerId)`，`NopCodeInheritance` 暴露 `findImplementations(interfaceId)` / `findSubclasses(classId)`
- **路 B**：降级为内部表，不作为聚合根暴露。通过 `@BizLoader` 在 NopCodeSymbol 上提供关联导航

**推荐路 A**，因为它们是独立的关系实体，且对 AI 消费者有直接查询价值。

#### 问题 3：功能重叠——fileOutline 双重入口

- `NopCodeFileBizModel` 有 `outline` BizLoader
- `NopCodeSymbolBizModel` 有 `fileOutline` Query
- 功能完全相同，消费者不知道该用哪个

**建议**：删除 `NopCodeSymbolBizModel.fileOutline`，统一使用 `NopCodeFile` 的 `outline` Loader。

#### 问题 4：缺少 xmeta 文件

nop-code 模块**完全没有 .xmeta.xml 文件**。这意味着：
- GraphQL 类型定义完全依赖 Java 注解运行时推导，缺少显式 schema
- 无法生成精确的 GraphQL schema 文档
- 无法做 schema 版本管理
- 字段类型定义不够精确（枚举在 Java 中是 String）
- 违反了 Nop 平台的硬性规则："每个 @BizModel 必须对应一个有 xmeta 的实体"

**建议**：为每个聚合根补充 xmeta 文件，定义字段的可见性、类型、关联关系。

#### 问题 5：性能隐患——每次图查询全量重建

`getTypeHierarchy`、`getCallHierarchy`、`detectCommunities`、`getGraphAnalysis`、`getImpactAnalysis` 每次都全量加载所有符号/调用到内存重建 SymbolTable 和 CallGraph。

```java
// CodeIndexService.java
private SymbolTable rebuildSymbolTable(String indexId) {
    List<NopCodeSymbol> entities = symbolDao.findAllByQuery(query); // 全量加载！
}
```

对大型项目（10 万+ 符号），这会造成严重的内存和延迟问题。

**建议**：
- 引入 `AnalysisCache`：按 indexId 缓存 SymbolTable + CallGraph，增量索引时失效
- 或改为 SQL 级别的图遍历（类似 CRG 的 SQLite CTE），避免全量加载

#### 问题 6：源码返回 null

`getFileSourceCode()` 和 `getSymbolSourceCode()` 均返回 null。ORM 表中 `SOURCE_CODE` 列存在且写入了数据，但 `entityToFileResult()` 显式设为 null。这是 AI 消费者的刚需。

**建议**：修复 `entityToFileResult()`，保留 sourceCode 字段。或实现 `ISourceCodeProvider` 从磁盘按需读取。

#### 问题 7：缺少按注解搜索

设计文档规划了 `NopCodeSymbol__findByAnnotation`，但未实现。`nop_code_annotation_usage` 表已存在且有数据，只是 BizModel 未暴露查询入口。

**建议**：在 `NopCodeSymbolBizModel` 新增 `findByAnnotation(annotationName, indexId)` Query。

#### 问题 8：BizLoader 中 indexId 硬编码

```java
// NopCodeSymbolBizModel.java
return codeIndexService.getSymbolUsages(indexId != null ? indexId : "test", ...);
```

`@BizLoader` 无法从 GraphQL 上下文自动获取 indexId，fallback 为硬编码 "test"。这是一个功能性 Bug。

**建议**：利用 `@Name("indexId")` 参数 + 框架上下文传递机制，或在实体中增加 indexId 冗余字段。

#### 问题 9：应返回 Entity 的方法返回了 DTO

Nop 标准模式：BizModel 方法直接返回 ORM Entity，框架通过 xmeta 控制字段暴露，无需 DTO。对于没有对应实体的计算结果（图分析、层级树、依赖图、搜索结果），DTO 是正确的。

当前问题：
- `NopCodeSymbolBizModel.getBySymbolId` 返回 `SymbolDTO` — 背后有 `NopCodeSymbol` 实体，应直接返回 Entity
- `NopCodeSymbolBizModel.findByQualifiedName` 返回 `SymbolDTO` — 同上
- `NopCodeSymbolBizModel.findPage_symbols` 返回 `PageBean<SymbolDTO>` — 应返回 `PageBean<NopCodeSymbol>`
- `NopCodeFileBizModel.getByPath` 返回 core model `CodeFileAnalysisResult` — 应返回 `NopCodeFile` Entity

而 `detectCommunities`（→ `CommunityDetectionResultDTO`）、`getTypeHierarchy`（→ `TypeHierarchyDTO`）等计算结果返回 DTO 是正确的。

**根因**：缺少 xmeta 文件 → 框架无法控制字段裁剪 → 开发者被迫手写 DTO 控制暴露范围。

**建议**：补充 xmeta 后，将应返回 Entity 的方法改为直接返回 Entity，移除冗余的 SymbolDTO。

#### 问题 10：文件 ID 碰撞风险

```java
String fileEntityId = indexId + "_" + Math.abs(file.getFilePath().hashCode());
```

`hashCode()` 碰撞概率在大项目中不可忽略。

**建议**：改用 SHA-256 或 `indexId + ":" + filePath` 作为稳定 ID。

### 7.3 设计文档 vs 实现差距

设计文档 `ai-code-index-graphql-design.md`（1493 行）描述了一个理想的 GraphQL Schema（interface/union 分类型、嵌套导航、泛型解析），但实现采用"统一大表 + 单一 DTO"策略。这是合理的务实选择，但差距记录如下：

| 设计规划 | 实际实现 | 评估 |
|---------|---------|------|
| `NopCodeSymbol` interface + union dispatch | 统一 `SymbolDTO` + kind 字段 | 合理简化 |
| `NopCodeClass/Method/Field` 分类型 | 无 | 可后续通过 xmeta computed prop 实现 |
| 嵌套导航 `class.methods.callers.declaringClass` | 无（只能一级查询） | **核心缺失**，影响 API 表达力 |
| `NopCodeParameter`（参数列表） | extData JSON | 勉强可用 |
| `NopCodeTypeReference`（泛型解析） | returnType/fieldType 字符串 | 可接受 |
| `findByAnnotation` | 未实现 | **应补充** |
| `batchGet` | 未实现 | **应补充** |

### 7.4 对比 CRG 的 API 设计理念

| 维度 | CRG（MCP Tools） | nop-code（GraphQL BizModel） | 评价 |
|------|-----------------|--------------------------|------|
| **API 风格** | 扁平工具调用（28 tools） | 聚合根 + 嵌套查询 | nop-code 更结构化 |
| **参数传递** | JSON 对象 | GraphQL args + QueryBean | nop-code 更类型安全 |
| **字段选择** | 固定返回结构 | GraphQL Selection Set | nop-code 更灵活 |
| **关联导航** | 多次工具调用 | @BizLoader 嵌套 | nop-code 更优 |
| **Token 效率** | detail_level 分级 + next_tool_suggestions | 无 | CRG 更优 |
| **工作流引导** | 5 个预置 Prompt | 无 | CRG 更优 |
| **版本管理** | 无 | xmeta/xpix Delta 定制 | nop-code 更优 |
| **权限控制** | 无 | CrudBizModel 内置 | nop-code 更优 |

### 7.5 改进优先级

| 优先级 | 改进项 | 工作量 |
|--------|-------|--------|
| **P0** | 修复 sourceCode 返回 null | 1h |
| **P0** | 修复 BizLoader indexId 硬编码 "test" | 2h |
| **P0** | 引入分析缓存（避免每次全量重建） | 1d |
| **P1** | 补充 xmeta 文件 | 2d |
| **P1** | 方法归属调整（依赖图→NopCodeDependency） | 1d |
| **P1** | 新增 findByAnnotation / batchGet | 0.5d |
| **P1** | 集成 nop-search 替换 DB LIKE 搜索 | 2d |
| **P2** | 为关系型聚合根添加业务方法 | 1d |
| **P2** | 将应返回 Entity 的方法改为返回 Entity，移除冗余 DTO | 1d（依赖 xmeta 补充） |
| **P2** | 修复文件 ID 碰撞风险 | 2h |
| **P3** | 消除 fileOutline 重复 | 0.5d |

---

## Open Questions

- [ ] 执行流追踪的入口点模式库：CRG 用 Python 字典维护 ~70 种模式，Java 实现如何设计可扩展的模式注册机制？（建议用 Nop IoC 自动发现）
- [ ] 向量搜索集成：nop-search 已有 `ITextEmbedding` 接口，需评估 `nop-ai` 模块是否能提供嵌入实现
- [ ] 全文搜索选型：**已确定使用 nop-search**（Lucene + 向量 + RRF 混合搜索），无需额外选型
- [ ] 框架感知解析的范围：首批支持哪些框架？Spring（必选）、MyBatis、Dubbo？
- [ ] 重构工具的定位：nop-code 是否应该支持代码修改？还是保持只读索引，由上层工具负责修改？
- [ ] BizLoader 中 indexId 传递机制：是通过实体冗余字段、GraphQL context、还是改为独立的 Query 方法？
- [ ] 关系型聚合根（Call/Inheritance/Usage 等）是保留为聚合根添加业务方法，还是降级为内部表？

## 八、审计纠正（2026-05-25）

基于代码逐行审计，纠正本文档中的错误表述：

### 确认的 Bug（文档说法准确）

1. **CallHierarchy ID 不一致**（`CodeIndexService.java:1021`）：`buildCallHierarchy` 用 qualifiedName 查 CallGraph，但 `rebuildCallGraph`（`:211`）用 symbol ID 建边。调用层级查询永远返回空结果。
2. **NopCodeUsage 未填充**（`saveFileResultInSession` 不写 Usage 实体）：`findReferencedBy` 读空表。
3. **Python/TS 适配器未注册**（`:97-102` 仅注册 JavaLanguageAdapter）。
4. **sourceCode 返回 null**（`:160` 显式设 null，但 `:1651` 确实写入了 DB）。
5. **indexId 硬编码 "test"**（`NopCodeSymbolBizModel.java:98,111`）。
6. **每次图查询全量重建**（5 个图方法均调 rebuildSymbolTable + rebuildCallGraph，无缓存）。
7. **File ID 碰撞风险**（`:1636` 用 `Math.abs(hashCode())`）。

### 纠正的表述

1. **搜索能力**：原文说"DB LIKE 查询 + 评分排序"，实际 `searchCode` 包含内存多因子评分（`scoreSymbolNameMatch`、`scoreFullTextMatch`、`scoreCombined`），比纯 LIKE 精细。但结论正确：无倒排索引，大项目性能不可接受。已修正 1.4 节。
2. **CONTAINS 边**：原文说"缺少 CONTAINS（父子关系）"，但 `CodeSymbol` 已有 `parentId`/`declaringSymbolId` 字段存储父子关系，只是没有独立边表。已修正 1.2 节。
3. **analyzeIncremental 忽略 languages 参数**：原文此说法归因错误。`ICodeIndexService` 无 `analyzeIncremental` 方法；`IProjectAnalyzer.analyzeIncremental` 根本 throws `UnsupportedOperationException`。不存在"languages 参数被忽略"的情况。

### 文档未覆盖的重要问题

1. **源码存储浪费**：写入 DB 又丢弃，要么返回已存储的源码，要么改为 `ISourceCodeProvider` 按需读磁盘。
2. **ORM 关系死重**：`NopCodeFile` 定义了对 Symbol/Usage/Call 的 ORM 关系但 BizModel 从未使用。
3. **线程安全**：图分析方法直接调 `daoProvider.daoFor()` 不包装 session。
4. **NopCodeAnalysisBizModel 残留**：`reflect-config.json` 引用和测试文件存在，但源码中没有此类。
5. **安全模型**：`indexId` 无鉴权，任何认证用户可查询任何索引。

---

## References

- CRG 仓库：https://github.com/tirth8205/code-review-graph
- nop-code 设计文档：`ai-dev/design/nop-code/`
- nop-code 查询 API 设计：`ai-dev/design/nop-code/code-index-query-api-design.md`
- nop-code 语义边设计：`ai-dev/design/nop-code/semantic-edge-design.md`
- nop-code 多语言设计：`ai-dev/design/nop-code/language-agnostic-code-index-design.md`
- nop-code 完成计划：`ai-dev/plans/06-nop-code-feature-completion-plan.md`
- nop-code GraphQL 服务计划：`ai-dev/plans/07-nop-code-graphql-service-plan.md`
- Nop GraphQL API 指南：`docs-for-ai/02-core-guides/api-and-graphql.md`
- Nop 服务层指南：`docs-for-ai/02-core-guides/service-layer.md`
