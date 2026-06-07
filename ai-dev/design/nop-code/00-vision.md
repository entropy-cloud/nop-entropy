# nop-code 高层设计原则

**日期**：2026-05-02（更新于 2026-06-07）
**范围**：`nop-code` 子系统
**状态**：active

---

## 一、产品定位

nop-code 是 Nop 平台的**多语言代码索引与语义分析服务**，为 AI 辅助代码分析提供结构化索引。

核心使命：将源码解析为语言无关的结构化图模型（CallGraph + SymbolTable），在此之上提供社区检测、影响分析、执行流追踪等图算法能力。AI 层通过 GraphQL 访问，不需要独立的 MCP 服务。

三个不可替代的价值：
1. **语言无关的通用模型**——SymbolKind / AccessModifier / RelationType 覆盖 Java / Python / TypeScript，语言特有语义通过 extData JSON 承载
2. **分层解耦的图算法**——core 只放模型和接口，graph 放算法，flow 放流级分析，形成清晰的 core ← graph ← flow 依赖层级
3. **确定性分析与语义推断的统一**——AST 提取的确定性边与 LLM 辅助的语义边共存于同一模型，通过 confidence 级别区分

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
| 2 | **core 只放模型和接口** | nop-code-core 是所有模块的公共依赖。放入算法会导致 lang-* 和 dao 被迫依赖 JGraphT、Leiden 等算法库 |
| 3 | **算法基于抽象接口** | 所有分析算法基于 CallGraph / SymbolTable 抽象接口，不依赖任何语言特定的 AST 类型 |
| 4 | **查询走 GraphQL** | 所有外部访问通过 BizModel 暴露的 GraphQL API。无 REST 端点、无独立 MCP 服务 |
| 5 | **边类型统一存储** | 不新增独立边表。TESTED_BY 和 REFERENCES 复用 `nop_code_usage.kind` 枚举扩展 |
| 6 | **Nop 平台集成** | 使用 IJdbcTemplate、nop-orm、nop-search-api 等平台基础设施，不绕过平台自建基础设施 |

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

- **保留**：语言无关的通用模型、分层解耦（core / graph / flow）、确定性 + 语义推断统一模型
- **保留（非核心路径）**：LLM 语义边提取——确定性提取器已满足基本需求，LLM 增强为远期能力
- **去除**：IDE 集成、代码生成、运行时分析、MCP 服务、分布式搜索引擎、SVN 支持
- **聚焦**：静态结构索引 + 图算法分析 + GraphQL API 暴露

## 八、设计不变量

以下不变量不可违反：

1. CodeSymbol 的 kind 字段必须是 CodeSymbolKind 枚举，不得使用自由字符串
2. 语言适配器不得将语言特有字段硬编码到通用模型中，必须通过 extData JSON 承载
3. 图算法必须基于 CallGraph + SymbolTable 抽象接口，不得直接操作 ORM 实体
4. 模块依赖方向必须单向：flow → graph → core，不得反向依赖
5. nop-code-core 不得依赖 JGraphT 算法库（只依赖 jgrapht-core 用于图数据结构）
6. 所有 GraphQL API 按聚合根归属，不得创建无对应实体的独立 BizModel
7. 增量分析必须基于 fingerprint 机制，不得跳过变更检测全量重建
8. nop-search 集成必须有降级策略（无搜索引擎时 fallback 到 DB LIKE 查询）

## 九、核心隐喻

nop-code 的运作方式：

1. **语言适配器层**：每种语言提供一个 ICodeFileAnalyzer 实现，将源码解析为语言无关的 CodeFileAnalysisResult
2. **项目分析器**：IProjectAnalyzer 扫描目录、自动识别语言、调度适配器、构建全局 CallGraph + SymbolTable
3. **图算法层**：在 CallGraph + SymbolTable 上运行社区检测、入口点评分、影响分析等算法
4. **流级分析层**：基于图算法的输出，追踪执行流、分析变更风险、检测死代码
5. **服务编排层**：BizModel 聚合所有能力，通过 GraphQL API 对外暴露

### 与 Nop 平台的集成

| 集成点 | 方式 |
|--------|------|
| ORM 持久化 | nop-code-dao（标准 Nop 分层） |
| 搜索引擎 | nop-search-api（仅接口依赖，降级到 DB LIKE） |
| AI 辅助 | nop-ai 模块（LLM 语义边提取，远期） |
| API 暴露 | GraphQL（BizModel + xmeta） |
| IoC 注册 | 语言适配器 + 入口点模式提供者通过 NopIoC 自动发现 |

## 十、拒绝了什么

| 方案 | 拒绝理由 |
|------|---------|
| 把算法也放 core | core 是所有模块的公共依赖，放入算法会导致 lang-* 和 dao 被迫依赖 JGraphT、Leiden 等算法库 |
| 每个图算法独立模块 | graph 算法间有强关联（ImpactAnalyzer 依赖 EntryPointScorer 结果），拆太细增加依赖管理复杂度 |
| 新增独立边表 | TESTED_BY 和 REFERENCES 可复用 `nop_code_usage.kind` 枚举扩展，无需新表 |
| 自建 Lucene 集成 | nop-search 已封装 Lucene BM25 + KNN + RRF，不自建 |
| Elasticsearch | 嵌入式 Lucene 对单机代码索引足够 |
| JGit 替代 ProcessBuilder | git diff 只需简单的行级别解析，不引入 JGit 新依赖 |
| 硬编码框架入口点模式字典 | Nop IoC 自动发现更符合平台理念，且支持用户通过 Delta 扩展 |
| 所有方法放 NopCodeIndex | 违反单一职责，索引级操作归 NopCodeIndex，实体级查询归对应 BizModel |
| 为调用关系单独建 BizModel | 调用关系存储在 `nop_code_usage` 表，调用链查询归 NopCodeSymbolBizModel |
| Token 效率分级 | GraphQL Selection Set 已提供字段级裁剪，额外分级收益不足 |
