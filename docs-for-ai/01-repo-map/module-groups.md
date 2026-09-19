# 仓库模块分组

当前仓库是一个根 `pom.xml` 驱动的 Maven 多模块工程。

AI 不需要一开始记住所有模块名，但必须知道应该先去哪个层级找答案。

## 根模块的主要分组

| 分组 | 主要路径 | 作用 |
|------|---------|------|
| 基础内核 | `nop-kernel/` | 代码生成、XLang、核心 API、基础工具 |
| 核心框架 | `nop-core-framework/` | IoC、Config、Boot、Plugin、Security、Log。Plugin 子模块组：`nop-plugin-api`（插件实现者契约 `IPlugin`/`IPluginScope`/`IPluginActivator` + 单层六态 `PluginState` + `plugin.xdef`，零依赖）、`nop-plugin-manager`（双轨加载 + 单激活生命周期编排 + 插件级 coeffect/reconcile + HMR + `HttpPluginResourceResolver` artifact 下载与 SHA256 校验）、`nop-plugin-support`（`AbstractPlugin` jar 轨兼容基类）。使用文档见 `03-modules/nop-plugin.md` |
| 持久化 | `nop-persistence/` | DAO、ORM、DB Migration、DBTool |
| 服务框架 | `nop-service-framework/` | BizModel、GraphQL、Gateway |
| 典型业务模块 | `nop-auth/`、`nop-job/`、`nop-task/`、`nop-wf/` | 最标准的业务骨架样板 |
| 可复用业务模块 | `nop-sys/`、`nop-report/`、`nop-rule/`、`nop-batch/`、`nop-dyn/`、`nop-file/`、`nop-retry/`、`nop-tcc/`、`nop-metadata/` | 系统管理、报表、规则、批处理、动态表单、文件、重试、分布式事务、联邦式元数据。详见 `03-modules/` |
| AI 子系统 | `nop-ai/` | AI 相关的 model/codegen/dao/meta/service/web/app 以及 agent、skills、toolkit、tools。分层：`nop-ai-api`（公开 API 契约，`IChatService`/`ChatOptions` 等）、`nop-ai-core`（LLM 调用 + `ILlmDialect` Provider 适配）、`nop-ai-agent`（执行引擎）、`nop-ai-toolkit`（工具执行层：抽象契约 + 具体执行器）、`nop-ai-tools`（具体工具实现）。其余模块：`nop-ai-coder`（AI 编程助手）、`nop-ai-maven`（VFS 集成）、`nop-ai-dsl-orm`（ORM DSL 集成）、`nop-ai-rag`、`nop-ai-shell`、`nop-ai-gateway`、`nop-ai-codegen`。废弃 chat API（`IAiChatService` 等，nop-ai-core）已标 `@Deprecated(forRemoval=true)`，迁移目标为 `IChatService`（nop-ai-api，bean `nopChatService`）。`nop-ai-agent` 的 `nop-dao`/`nop-message-core` 为 test scope（仅测试需要，不泄漏给下游消费者）；MCP 集成模块（`nop-ai-mcp-server`、`nop-spring-mcp-server*`）独立发布周期 |
| Runner / CLI | `nop-runner/`、`scripts/` | CLI、runner、命令入口 |
| 集成与运行时外围 | `nop-spring/`、`nop-quarkus/`、`nop-network/`、`nop-integration/` | 宿主集成与运行环境支持 |
| 测试与示例 | `nop-autotest/`、`nop-demo/`、`demo/` | 测试基建、demo、模板 |
| WIP 实验模块 | `nop-code/` | 多语言代码索引与智能分析服务。子模块：`nop-code-core`（通用模型+图数据结构+CodeCallGraph 适配层）、`nop-code-flow`（执行流追踪、变更分析、死代码检测）、`nop-code-lang-java/python/typescript`（语言适配器）、`nop-code-service`（BizModel + CodeIndexService + 业务图分析类）、`nop-code-api`（外部 RPC 接口）、`nop-code-dao/meta/web/app`（标准 Nop 分层）。已加入根 pom.xml modules。 |
| 通用图算法库 | `nop-graph/` | 通用图接口与算法库。子模块：`nop-graph-api`（`IGraph` 接口+`Edge`+结果类型，零外部依赖）、`nop-graph-core`（算法实现：BFS、PageRank、TarjanSCC、ImpactPropagator、LeidenDetector、BetweennessCentrality、GraphExporter 等，依赖 JGraphT+CWTS）。供 `nop-code`、未来可供 `nop-wf`/`nop-task`/`nop-stream` 复用。 |
| 流处理引擎 | `nop-stream/` | 分布式流处理引擎。子模块（与 `nop-stream/pom.xml` `<modules>` 一一对应，共 10 个）：`nop-stream-core`（核心 API、状态后端、算子、数据类型、watermark 注入算子 `TimestampsAndWatermarksOperator`；Flink API 兼容层 `DataStreamSource`/`Transformation`/`StreamGraph`/`JobGraph` 位于本模块内部，非独立子模块）、`nop-stream-runtime`（运行时、检查点协调器、检查点存储抽象、窗口算子、任务调度、RPC 控制面与跨 JVM 数据面 transport）、`nop-stream-cep`（CEP 复杂事件处理、NFA、SharedBuffer、模式匹配）、`nop-stream-flow`（XDSL StreamModel 声明式编排 + Delta 定制，依赖 core/cep/xdefs）、`nop-stream-rocksdb`（RocksDB 增量状态后端）、`nop-stream-connector`（消息源/汇连接器基础、文件 exactly-once sink）、`nop-stream-connector-batch`（批源/汇连接器）、`nop-stream-connector-jdbc`（JDBC 两阶段提交 exactly-once sink）、`nop-stream-connector-debezium`（Debezium CDC source）、`nop-stream-fraud-example`（欺诈检测示例应用）。仓库内不存在 `nop-stream-flink` / `nop-stream-checkpoint` 子模块。 |
| 高性能文件搜索（JDK 22+ 门控） | `nop-rg/` | 纯 JVM 的 ripgrep 等价文件搜索工具（grep 内容搜索 + glob 文件名匹配），开发中。子模块经 JDK ≥ 22 激活 profile 挂入 reactor（同 `nop-utils` `java21-modules` 门控模式，JDK < 22 构建自动跳过）：`nop-rg-bom`（模块版本管理）、`nop-rg-core`（`maven.compiler.release=22`，FFM MemorySegment+Arena I/O、BMH 标量搜索、Glob 匹配；后续 Wave 增加 walk/coordinator/cli/benchmark/vector 子模块）。 |

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

`nop-ai` 分层速查：

| 模块 | 职责 |
|------|------|
| `nop-ai-api` | 公开 API 契约：`IChatService`、`ChatOptions`、`ChatMessage`（Agent Engine 直接消费的类型） |
| `nop-ai-core` | LLM 调用实现（`ChatServiceImpl` + `ILlmDialect` Provider 适配）、token 估算基线、错误码。**废弃 chat API（`IAiChatService` 等）保留于此，勿在新代码使用** |
| `nop-ai-agent` | Agent DSL + 执行引擎（`DefaultAgentEngine`/`ReActAgentExecutor`）。经 `IChatService` 调 LLM，经 `ITokenEstimator` bridge 消费 token 估算；可靠性机制直接复用 `io.nop.ai.core.reliability` 包（`ThresholdBreaker`/`LlmErrorClassifier`/`ProviderFailoverChain`/`StandardRetryPolicy` 等），并直接使用 core 的 `model`/`agent`/`dialect` 公共模型类型（2026-09-14 按 live import 修正） |
| `nop-ai-toolkit` | 工具执行层：19 个具体 `*Executor`（ReadFile/Bash/Http/GraphqlQuery/ApplyDelta/Patch/Skill 等，`io.nop.ai.toolkit.tools`）+ `LocalToolFileSystem` + `ToolManagerImpl`/`DefaultToolExecutorProvider`；抽象面为 `IToolExecutor`/`IToolManager` 与工具 DSL（tool.xdef） |
| `nop-ai-tools` / `nop-ai-skills` | 具体工具实现 / skill 引擎。DSL 文档工具契约与实现（`IDslTool`/`DslToolImpl`，`io.nop.ai.tools.xdsl`）在模块内部（M5-P1 裁定 A：自 nop-ai-coder 下沉，解除 tools→coder 分层倒置；DSL 转换经 nop-converter，不连带 coder 重量依赖链） |
| `nop-ai-gateway` | AI 网关，承载三块能力：LLM failover（多 Provider 路由/转换 + 透明账号切换）+ channel 消息网关（`IChannelConnector`/`FeishuConnector`/`ChannelMessageServiceImpl`/`ChannelSessionStoreImpl`）+ 扫码登录编排（`ChannelLoginApiBizModel`/`ChannelLoginScanProcessor`）。依赖边：gateway → `nop-ai-agent`/`nop-ai-dao`/`nop-integration-api`/`nop-integration-feishu`（channel）+ `nop-biz-auth-core`/`nop-auth-api`（login）；`nop-auth-service` 与消息总线为部署侧可选装配（详见 `docs-for-ai/03-modules/nop-ai-gateway.md`） |
| `nop-ai-rag` | RAG 检索增强 |
| `nop-ai-shell` | Shell 沙箱执行环境 |
| `nop-ai-coder` | AI 编程助手 |
| `nop-ai-maven` | Maven/VFS 集成（模块名历史沿用，核心为 VFS 职责） |
| `nop-ai-dsl-orm` | ORM 与 DSL 模型集成 |
| `nop-ai-dao`/`nop-ai-meta`/`nop-ai-service`/`nop-ai-web`/`nop-ai-app` | 标准 Nop 分层（ORM 实体/元数据/BizModel/页面/启动应用） |

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
