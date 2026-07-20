# 仓库模块分组

当前仓库是一个根 `pom.xml` 驱动的 Maven 多模块工程。

AI 不需要一开始记住所有模块名，但必须知道应该先去哪个层级找答案。

## 根模块的主要分组

| 分组 | 主要路径 | 作用 |
|------|---------|------|
| 基础内核 | `nop-kernel/` | 代码生成、XLang、核心 API、基础工具 |
| 核心框架 | `nop-core-framework/` | IoC、Config、Boot、Plugin、Security、Log |
| 持久化 | `nop-persistence/` | DAO、ORM、DB Migration、DBTool |
| 服务框架 | `nop-service-framework/` | BizModel、GraphQL、Gateway |
| 典型业务模块 | `nop-auth/`、`nop-job/`、`nop-task/`、`nop-wf/` | 最标准的业务骨架样板 |
| 可复用业务模块 | `nop-sys/`、`nop-report/`、`nop-rule/`、`nop-batch/`、`nop-dyn/`、`nop-file/`、`nop-retry/`、`nop-tcc/`、`nop-metadata/` | 系统管理、报表、规则、批处理、动态表单、文件、重试、分布式事务、联邦式元数据。详见 `03-modules/` |
| AI 子系统 | `nop-ai/` | AI 相关的 model/codegen/dao/service/web/app 以及 agent、skills、toolkit。`nop-ai-agent` 的 `nop-dao`/`nop-message-core` 为 test scope（仅测试需要，不泄漏给下游消费者） |
| Runner / CLI | `nop-runner/`、`scripts/` | CLI、runner、命令入口 |
| 集成与运行时外围 | `nop-spring/`、`nop-quarkus/`、`nop-network/`、`nop-integration/` | 宿主集成与运行环境支持 |
| 测试与示例 | `nop-autotest/`、`nop-demo/`、`demo/` | 测试基建、demo、模板 |
| WIP 实验模块 | `nop-code/` | 多语言代码索引与智能分析服务。子模块：`nop-code-core`（通用模型+图数据结构+CodeCallGraph 适配层）、`nop-code-flow`（执行流追踪、变更分析、死代码检测）、`nop-code-lang-java/python/typescript`（语言适配器）、`nop-code-service`（BizModel + CodeIndexService + 业务图分析类）、`nop-code-api`（外部 RPC 接口）、`nop-code-dao/meta/web/app`（标准 Nop 分层）。已加入根 pom.xml modules。 |
| 通用图算法库 | `nop-graph/` | 通用图接口与算法库。子模块：`nop-graph-api`（`IGraph` 接口+`Edge`+结果类型，零外部依赖）、`nop-graph-core`（算法实现：BFS、PageRank、TarjanSCC、ImpactPropagator、LeidenDetector、BetweennessCentrality、GraphExporter 等，依赖 JGraphT+CWTS）。供 `nop-code`、未来可供 `nop-wf`/`nop-task`/`nop-stream` 复用。 |
| 流处理引擎 | `nop-stream/` | 分布式流处理引擎。子模块：`nop-stream-core`（核心 API、状态后端、算子、数据类型）、`nop-stream-cep`（CEP 复杂事件处理、NFA、模式匹配）、`nop-stream-runtime`（运行时、检查点协调器、窗口算子、任务调度）、`nop-stream-connector`（消息源/汇连接器、批消费适配器）、`nop-stream-checkpoint`（检查点存储抽象）、`nop-stream-flow`（流控）、`nop-stream-flink`（Flink API 兼容层）、`nop-stream-fraud-example`（欺诈检测示例应用）。 |

## 最值得先理解的模块

### 1. 框架主干

- `nop-kernel/`
- `nop-core-framework/`
- `nop-persistence/`
- `nop-service-framework/`

当你要回答“框架默认怎么做”时，通常应该先从这四层找依据。

### 2. 业务骨架样板

- `nop-auth/`
- `nop-job/`
- `nop-task/`
- `nop-wf/`

当你要回答“一个标准业务模块通常如何建模、生成、分层和扩展”时，优先看这几个目录。

### 2.5 可复用业务模块

应用项目中优先使用这些模块，不要重复造轮子。详细文档在 `03-modules/`：

| 模块 | 用途 |
|------|------|
| `nop-sys/` | 序列号、数据字典、国际化、Maker-Checker 审批、分布式锁、事件队列 |
| `nop-report/` | 报表引擎（Excel/PDF/DOCX） |
| `nop-rule/` | 规则引擎（决策树/决策矩阵） |
| `nop-batch/` | 批处理引擎（chunk 处理、断点续传） |
| `nop-dyn/` | 动态表单/实体（运行时定义） |
| `nop-file/` | 文件管理（上传/下载/去重） |
| `nop-retry/` | 分布式重试引擎 |
| `nop-tcc/` | TCC 分布式事务 |
| `nop-metadata/` | 联邦式元数据 / BI 语义层 / 血缘 / 质量 / 对账（跨数据源逻辑表抽象 + 聚合查询 + 自动血缘 + 质量检查点 cron 调度 + 数据对账） |

### 2.6 nop-metadata 快速概览

`nop-metadata/` 是 Nop 平台的元数据中心，承担五类职责：

- **元数据目录（Catalog）**：跨 JDBC 数据源 / SQL 视图 / ORM 实体的统一逻辑表抽象（`NopMetaTable`）；`syncExternalTables` 自动从外部库同步物理表结构。
- **BI 语义层（Semantic Layer）**：在逻辑表之上定义 Measure / Dimension / Join / Filter，通过 `queryAggregation` / `queryJoinData` / `queryTableData` 提供 EQL/GraphQL 查询入口；跨库 JOIN 支持同库原生 SQL + 跨库应用层拼接。
- **血缘追踪（Lineage）**：从 SQL AST 自动抽取表级 + 列级 + 指标级血缘（`SqlColumnLineageExtractor` / `SqlSourceTableExtractor`）；支持上下游追溯与影响分析。
- **数据质量（Quality）**：质量规则 + 检查点批量执行 + 自动评分；`MetaQualityCheckpointScheduler` 提供 cron 调度（BeanMethodJobInvoker 复用既有编排链）；支持 webhook / notify 动作分发执行摘要。
- **数据对账（Reconciliation）**：配置驱动（columnName + matchStrategy）的双向数据比对，支持精确/模糊匹配。

详细使用文档：`03-modules/nop-metadata.md`。

### 3. AI 专属子系统

- `nop-ai/`

当任务直接涉及 AI agent、tool、skill、RAG、shell、MCP、AI service 时，再深入 `nop-ai/`。

## 常见任务应该先看哪里

| 任务 | 优先路径 |
|------|---------|
| 理解代码生成链路 | `nop-kernel/`、业务模块下的 `*-codegen/` |
| 理解 ORM / DAO / 迁移 | `nop-persistence/` |
| 理解 BizModel / GraphQL | `nop-service-framework/` |
| 找标准业务实现参考 | `nop-auth/`、`nop-job/`、`nop-task/`、`nop-wf/` |
| 找 CLI 或生成入口 | `nop-runner/`、`scripts/nop-cli.cmd` |
| 找测试基类与快照机制 | `nop-autotest/` |
| 找可运行示例 | `nop-demo/`、`demo/` |
| 流处理引擎开发/修改 | `nop-stream/` |
| CEP 模式匹配开发 | `nop-stream/nop-stream-cep/` |
| 检查点/状态管理 | `nop-stream/nop-stream-runtime/`、`nop-stream/nop-stream-core/` |
| 选择可复用模块（报表/规则/批处理/文件/锁等） | `03-modules/reusable-modules-overview.md` |

## 与文档配套的阅读顺序

1. 先看本页建立模块分组。
2. 再看 `domain-module-pattern.md` 理解业务模块骨架。
3. 最后看 `where-things-live.md` 快速定位具体文件。

## 相关文档

- `./domain-module-pattern.md`
- `./where-things-live.md`
- `../02-core-guides/model-first-development.md`
