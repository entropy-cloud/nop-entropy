# 仓库模块分组

当前仓库是一个根 `pom.xml` 驱动的 Maven 多模块工程。

AI 不需要一开始记住所有模块名，但必须知道应该先去哪个层级找答案。

## 根模块的主要分组

| 分组 | 主要路径 | 作用 |
|------|---------|------|
| 基础内核 | `nop-kernel/` | 代码生成、XLang、核心 API、基础工具。JSON 查询支持面由 `nop-kernel/nop-jpath` 承担（fastjson 兼容 JSONPath `NopJsonPath` + nop-core `JPath` 门面的 SPI 求值桥接） |
| 核心框架 | `nop-core-framework/` | IoC、Config、Boot、Plugin、Security、Log。Plugin 子模块组：`nop-plugin-api`（插件实现者契约 `IPlugin`/`IPluginScope`/`IPluginActivator` + 单层六态 `PluginState` + `plugin.xdef`，零依赖）、`nop-plugin-manager`（双轨加载 + 单激活生命周期编排 + 插件级 coeffect/reconcile + HMR + `HttpPluginResourceResolver` artifact 下载与 SHA256 校验）、`nop-plugin-support`（`AbstractPlugin` jar 轨兼容基类）。使用文档见 `03-modules/nop-plugin.md` |
| 持久化 | `nop-persistence/` | DAO、ORM、DB Migration、DBTool、数据搬迁编排（`nop-orm-migration`：分片扩容 N→M 与同构机房迁移的五状态机编排器——P0 位点钉定/全量委托 nop-batch/增量委托 debezium-cdc→jdbc-2pc/三级校验/写停止关；使用文档 `02-core-guides/data-migration.md`） |
| 服务框架 | `nop-service-framework/` | BizModel、GraphQL、Gateway |
| 典型业务模块 | `nop-auth/`、`nop-job/`、`nop-task/`、`nop-wf/` | 最标准的业务骨架样板 |
| 宿主集成（双宿主） | `nop-spring/`（web-starter/core-starter/proxy/file/delta 等）、`nop-quarkus/`（web/core-starter/file 等） | Spring Boot 与 Quarkus 双宿主适配。nop-host 无 03-modules owner doc（缺口登记，owner doc 建设归 R-G 治理批）；并发契约以 `04-reference/source-anchors.md` 的 `HOST-CC-001` 锚点 + AI 开发记录区 concurrency 设计集 host-startup-readiness-gate-future-exception-termination-upload-close-and-credential-pair-concurrency.md 为准 |
| 可复用业务模块 | `nop-sys/`、`nop-report/`、`nop-rule/`、`nop-batch/`、`nop-dyn/`、`nop-file/`、`nop-credential/`、`nop-retry/`、`nop-tcc/`、`nop-metadata/` | 系统管理、报表、规则、批处理、动态表单、文件、凭证、重试、分布式事务、联邦式元数据。详见 `03-modules/` |
| AI 子系统 | `nop-ai/` | AI 相关的 model/codegen/dao/meta/service/web/app 以及 agent、skills、toolkit、tools。分层：`nop-ai-api`（公开 API 契约，`IChatService`/`ChatOptions` 等；WI14/plan 2446 后为纯能力契约 jar 29 文件——chat/mcp/credential/secure/exceptions/tool，实体 CRUD 生成面 beans/crud 66 文件已删 + 22 实体 xmeta 打 no-api）、`nop-ai-core`（LLM 调用 + `ILlmDialect` Provider 适配）、`nop-ai-agent`（执行引擎）、`nop-ai-toolkit`（工具执行层：抽象契约 + 具体执行器）、`nop-ai-tools`（具体工具实现）。其余模块：`nop-ai-coder`（AI 编程助手）、`nop-ai-maven`（VFS 集成——WI16/plan 2453 已并入 nop-ai-toolkit mavenvfs 包，模块解散）、`nop-ai-dsl-orm`（ORM DSL 集成）、`nop-ai-rag`（RAG 垂直：vectorstore SPI/实现/桥接/classifier 已随 plan 2421 迁入，包名保留 io.nop.ai.core.api.*；embedding 客户端接口留守 nop-ai-core）、`nop-ai-shell`、`nop-ai-gateway`、`nop-ai-codegen`。旧 chat API（`IAiChatService` 等，nop-ai-core）已随 plan 2414（roadmap WI1，2026-10-11）整体删除，`IChatService`（nop-ai-api，bean `nopChatService`）是唯一 chat API；AiCommand/AI 任务（xlib `ai:Execute`）已迁移至 `IChatService` + `ChatRequest`/`ChatResponse` 栈。`nop-ai-agent` 的 `nop-dao`/`nop-message-core` 为 test scope（仅测试需要，不泄漏给下游消费者）；nop-ai 整体定位为**库集合**——宿主由下游应用承担（nop-ai-app 仅为轻量 Quarkus 参考宿主，不默认装配 agent/gateway；WI22/plan 2458 裁定），agent 引擎的装配宿主为引用方应用或独立网关部署；MCP 集成（WI15/plan 2452 后）：`nop-spring-mcp-server`（SpringBoot 启动器 + spring-ai adapter，原 support 已并入）单一模块独立发布周期；`nop-ai-mcp-server` 已解散（AiFileTool 迁入 `nop-ai-tools`，包 `io.nop.ai.tools.mcp`） |
| Runner / CLI | `nop-runner/`、`scripts/` | CLI、runner、命令入口 |
| 集成与运行时外围 | `nop-spring/`、`nop-quarkus/`、`nop-network/`、`nop-integration/` | 宿主集成与运行环境支持 |
| 集群基础设施 | `nop-cluster/` | 选主（`nop-cluster-core` elector：`ILeaderElector`/`AbstractLeaderElector`+轮询监督基类）、服务发现与注册（nacos/hazelcast）、Sentinel 限流、集群 RPC（`nop-rpc-cluster`：`ClusterRpcClient` 按候选实例重试调用）。无 `03-modules` owner doc（缺口登记，owner doc 建设归 R-G 治理批）；并发契约以 `04-reference/source-anchors.md` 的 `CLUSTER-CC-001` 锚点 + AI 开发记录区 concurrency 设计集 cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md 为准 |
| 测试与示例 | `nop-autotest/`、`nop-demo/`、`demo/` | 测试基建、demo、模板 |
| WIP 实验模块 | `nop-code/` | 多语言代码索引与智能分析服务。子模块：`nop-code-core`（通用模型+图数据结构+CodeCallGraph 适配层）、`nop-code-flow`（执行流追踪、变更分析、死代码检测）、`nop-code-lang-java/python/typescript/go/rust/csharp`（语言适配器；go/rust/csharp 2026-09-29 入库）、`nop-code-codegen`（代码生成）、`nop-code-service`（BizModel + CodeIndexService + 业务图分析类）、`nop-code-api`（外部 RPC 接口）、`nop-code-dao/meta/web/app`（标准 Nop 分层）。已加入根 pom.xml modules。 |
| 通用图算法库 | `nop-graph/` | 通用图接口与算法库。子模块：`nop-graph-api`（`IGraph` 接口+`Edge`+结果类型，零外部依赖）、`nop-graph-core`（算法实现：BFS、PageRank、TarjanSCC、ImpactPropagator、LeidenDetector、BetweennessCentrality、GraphExporter 等，依赖 JGraphT+CWTS）。供 `nop-code`、未来可供 `nop-wf`/`nop-task`/`nop-stream` 复用。无 `03-modules` owner doc（缺口声明经 R-G 治理批/plan 2567 登记收口——确有实质并发契约落账，暂维持行内登记形态不单开 owner doc 页）；并发与资源契约以 `04-reference/source-anchors.md` 的 `GRAPH-CC-001`/`LEIDEN-CC-001` 锚点 + AI 开发记录区 concurrency 设计集 duckdb-engine-race-ctas-guard-graph-envelope-and-edge-attrs-contract.md 与 leiden-timeout-envelope-and-code-graph-self-heal-singleflight-guard-contract.md 为准（Betweenness 超时的消费面声明另见 `03-modules/nop-duckdb.md`「并发与资源契约」节；Leiden 超时与社区检测守卫的消费面声明另见 `03-modules/nop-code.md`「图分析并发与资源契约」节）。 |
| 流处理引擎 | `nop-stream/` | 分布式流处理引擎。子模块（与 `nop-stream/pom.xml` `<modules>` 一一对应，共 13 个）：`nop-stream-core`（核心 API、状态后端、算子〔含窗口算子族 `core.operators.windowing`——结构重构 roadmap WI7 自 runtime 归位〕、数据类型、watermark 注入算子 `TimestampsAndWatermarksOperator`；Flink API 兼容层 `DataStreamSource`/`Transformation`/`StreamGraph`/`JobGraph` 位于本模块内部，非独立子模块）、`nop-stream-runtime`（运行时与任务调度、检查点协调器 `runtime.checkpoint` + 共享内核 `checkpoint.core`〔RestoreGates/SavepointMaterializer〕 + 存储实现与 `CheckpointStorages` 工厂 `checkpoint.storage`、跨 JVM wire DTO 零出边叶子 `runtime.protocol`、RPC 控制面 rpc/coordinator/cluster、数据面 transport、launch/ops/maintain/health 运维面；内置 replayable source 基线 `runtime.source`）、`nop-stream-cep`（CEP 复杂事件处理、NFA、SharedBuffer、模式匹配）、`nop-stream-flow`（XDSL StreamModel 声明式编排 + Delta 定制，依赖 core/cep/xdefs）、`nop-stream-format-avro`（Avro 数据格式）、`nop-stream-rocksdb`（RocksDB 增量状态后端）、`nop-stream-connector`（消息源/汇连接器基础、文件 exactly-once sink）、`nop-stream-connector-batch`（批源/汇连接器）、`nop-stream-connector-jdbc`（JDBC 两阶段提交 exactly-once sink）、`nop-stream-connector-debezium`（Debezium CDC source）、`nop-stream-connector-kafka`（Kafka exactly-once 连接器，gated 真 broker E2E）、`nop-stream-sql`（SQL 编译宿主模块）、`nop-stream-fraud-example`（欺诈检测示例应用）。仓库内不存在 `nop-stream-flink` / `nop-stream-checkpoint` 子模块。JMH 性能基准位于 `nop-benchmark/nop-benchmark-stream`（13 基准类，覆盖 codec、RocksDB/Memory 状态、窗口、timer、NFA、SharedBuffer、CheckpointSerDe（读侧 checksum on/off 档）、远程传输写（RemoteResultPartition 锁内发送 + 写/心跳互卡组）、CepOperator 算子层缓冲/drain、InputGate 读取循环、BufferPool 许可、处理时间驱动触发延迟；运行方式见 nop-benchmark README）。注意：CEP 算子与 RocksDB 状态后端的组合当前不可用（SharedBuffer Lockable 不可 JSON 序列化等三层缺陷；审计报告「2026-09-27-deep-audit-nop-stream-quality-r2/06-cep-rocksdb-gap」——按边界规则此处不直接链接 ai-dev 路径），CEP 生产使用限 Memory 后端|
| 开发者工具链 | `nop-tools/` | 开发者/AI 编程工具链聚合分组（plan 2418 归组，Maven 坐标不变）：`nop-rg`（ripgrep 等价内容搜索，JDK 22+ 门控）、`nop-find`（模糊文件查找，JDK 22+ 门控）、`nop-jq`（jq 查询引擎）、`nop-treesitter`（纯 Java Tree-sitter 运行时）、`nop-lint`（YAML 驱动 AST lint）、`nop-refactor`（AI-first 代码修改工具链）、`nop-bytecode`（字节码缺陷发现通道）、`nop-ib`（javac 增量编译/测试选择引擎，AI 开发期快路径，见下行明细）。门控 profile 封装在各组 pom 内部（`java22-modules`，随组搬移不变；JDK 25 全面切换为后继独立工作）。聚合器 parent=nop-entropy，子组 reparent 到 nop-tools，孙子模块不动。各组明细见下方「nop-tools 分组明细」；使用文档见 `03-modules/` 对应模块页。 |
| 进程内分析执行层 | `nop-duckdb/` | 数据文件（CSV/Parquet）与本地库场景的 DuckDB 分析执行层（顶层独立模块组，parent=nop-entropy，单模块无子模块；与 nop-jq 曾有的顶层扁平先例不同——nop-jq 已于 plan 2417 归入 `nop-tools/`，本模块因消费面是数据文件分析管线而非编程工具链，作为顶层扁平例外保留）。`IDuckDbEngine`（连接管理：内存库/文件库/配置三键/单写者与配置指纹约束）+ `DuckDbFiles`（CSV/Parquet/XLSX 读写面）+ `DuckDbSqlTaskStep`（nop-task SQL 步骤编排）。业务库写回一律走 nop-dao/ORM（本模块仅分析计算与文件读写）；持久层用开放文件格式，禁用 `.duckdb` 作长期归档。使用文档：`03-modules/nop-duckdb.md`。消费方：数据文件分析管线、nop-task SQL 编排任务、ORM 第二分析数据源路由。 |
| 报表格式引擎内核 | `nop-format/` | nop-report 的格式内核（tablesaw/office-model/office-doc-model/record-netty 等，CRA 5.4 审计单元主载体，占单元 84% 行数）。无 `03-modules` owner doc——缺口声明经 R-G 治理批（plan 2567）登记收口，修复批 owner doc 锚点为 `03-modules/nop-report.md`；并发与资源契约以 AI 开发记录区 concurrency 设计集 record-decode-bounds-and-format-concurrency.md 为准（record 反序列化 64MB 上界/字体锁纪律/格式注册表并发容器），执行契约句见 `03-modules/nop-report.md`。 |
| 开发调试工具 | `nop-dev-tools/` | XLang 调试器相关模块组（在线调试能力已全部落地，xlang-web-debugger roadmap items 1-23 `done`，2026-10-18）。子模块：`nop-api-debugger`（调试协议接口 11 类，仅依赖 nop-api-core）、`nop-xlang-debugger`（调试引擎 `XLangDebugger` + 会话化 `io.nop.xlang.debugger.session` 包：`SessionDebugManager`/`SessionRoutingExecutor`/租约机制；`DebugExpressionExecutor` 在父包 io.nop.xlang.debugger，依赖 nop-xlang/nop-api-debugger/nop-rpc-simple）、`nop-xlang-debug-web`（`io.nop.xlang.debug.web` 12 类：WS 网关 `DebugWebSocketHandler`/`DebugGatewayRuntime`/`DebugWebServerConfig`、会话绑定 `DebugSessionBinder`、日志管道 `DebugLogPipe`/`GatewayNotifierBridge`、REPL `ReplService`/`ReplAccessGuard` 等，依赖 nop-xlang-debugger/nop-graphql-core）、`nop-maven-shaded-plugin`。同目录 `nop-idea-plugin` 为 Gradle 工程，不参与 Maven 构建。端点适配器面在本组之外的壳模块：`nop-quarkus/nop-quarkus-web` 与 `nop-spring/nop-spring-web-starter`（各含 `XLangDebugWebSocketEndpoint` + `NopXlangDebugReplWebService` twin）。配置键：`nop.xlang.debugger.enabled`（默认 false）/ `nop.xlang.debug-web.enabled`（默认 false），开关矩阵见设计文档 00-overview §3.3；租约/日志/REPL 参数族以 `XLangDebuggerConfigs` 为准（租约参数表见设计文档 02-lease-lifecycle、REPL 限制见 07-repl-security）。端点：WS `/ws/xlang-debugger`（nopDebugToken 握手）+ `/r/nop-xlang-debug/compile|execute`。使用文档见 `02-core-guides/debugging-and-diagnostics.md`「XLang 在线调试」节；锚点 `04-reference/source-anchors.md` DBG-006 起。 |
| 外围与 IDE/演示/E2E 工具 | `nop-dev-tools/`、`nop-frontend-support/`、`nop-demo/`、`demo/`、`nop-entropy-e2e/` | 外围工具与示例面（CRA 6.5 审计单元载体）。`nop-dev-tools`（idea-plugin/调试工具，Gradle 构建隔离）验证通道决定收口于 AI 开发记录区 concurrency 治理集 governance/gradle-channel-decision.md（CL-301：通道② sink 下沉兑现，验证通道收归 Maven 门禁；harness 纳入 CI 为后续建议）；其并发契约以 AI 开发记录区 concurrency 设计集 peripheral-js-engine-lifecycle-rejection-and-idea-plugin-debug-channel-concurrency-contract.md 为准（connector 下沉 `io.nop.xlang.debugger` 后调试通道契约归 nop-xlang-debugger 面）。无 `03-modules` owner doc——缺口声明经 R-G 治理批登记收口。`nop-frontend-support`/`nop-demo`/`demo`/`nop-entropy-e2e` 无 campaign 并发契约落账——缺口声明显式维持（R-G 治理批触发条款登记册附录）。 |

### nop-tools 分组明细（8 个子组，plan 2417 归组）

> 归组背景：nop-jq 2026-09-28 自 nop-kernel 迁出的动机是 nop-kernel 保持精简最小化，而非顶层布局偏好；plan 2417 据此将 7 个开发者/AI 编程工具链模块组物理归入 `nop-tools/`（`git mv` 保留历史，外部消费者零影响；立项编号 2417，执行期因编号冲突改号 2418）。

| 子组 | 路径 | 职责 |
|------|------|------|
| 高性能文件搜索（JDK 22+ 门控） | `nop-tools/nop-rg/` | 纯 JVM 的 ripgrep 等价文件搜索工具（grep 内容搜索 + glob 文件名匹配）。子模块经 JDK ≥ 22 激活 profile 挂入 reactor（同 `nop-utils` `java21-modules` 门控模式，JDK < 22 构建自动跳过）：`nop-rg-bom`（模块版本管理）、`nop-rg-core`（FFM 搜索内核,编译级别随根 pom release=25——原 release=22 pin 已由 plan 2427 WI2 收编,reactor 纳入门控保留：`search` BMH 标量搜索、`glob` rg/globset 语义匹配、`io` FFM MemorySegment+Arena 分块 I/O、`walk` 并行文件遍历（gitignore/隐藏过滤、符号链接目录不递归）、`coordinator` 搜索编排/策略选择/行提取）、`nop-rg-cli`（picocli 命令行入口，`--json` 兼容 rg --json、`--jfr` JFR 诊断录制）、`nop-rg-benchmark`（JMH 1.37 基准：标量搜索/glob/端到端/rg 对比；运行方式见模块 README）、`nop-rg-vector`（Vector API SIMD 搜索，optional 依赖；`--vector` 时经 ServiceLoader SPI 发现，孵化模块缺失自动降级标量，classpath 缺失显式报错；启用需 `--add-modules jdk.incubator.vector`）。默认尊重 .gitignore（注意：rg 仅在 git 仓库内应用 .gitignore，nop-rg 无条件应用）。无 `03-modules` owner doc——缺口声明经 R-G 治理批（plan 2567）登记收口；并发与资源契约以 AI 开发记录区 concurrency 设计集 tools-parallel-batch.md 为准（R4-30/plan 2564 修复批：patternBytes 编码、FFM TOCTOU 边界声明、SearchOutcome 部分成功语义、jarOpener 单开）。 |
| 模糊文件查找（JDK 22+ 门控） | `nop-tools/nop-find/` | 纯 JVM 模糊文件查找 CLI（对标 VSCode Ctrl+P / fzf file mode / zcode `@` 面板）：给定 query 对文件名与路径做三级打分（前缀 < 子串 < 子序列）取最优，返回 top-K 排序路径；不读文件内容（内容搜索归 `nop-rg`）。子模块经 JDK ≥ 22 激活 profile 挂入 reactor（同 `nop-rg` `java22-modules` 门控模式，JDK < 22 构建自动跳过）：`nop-find-bom`（模块版本管理）、`nop-find-core`（传递依赖 nop-rg-core 的 FFM 面,编译级别随根 pom release=25——原 release=22 pin 已由 plan 2427 WI2 收编：`score` 三级打分器、`topk` 二分插入 top-K 收集器、`coordinator` 遍历→并行打分→归并编排、复用 `nop-rg-core` 的 `ParallelFileWalker`/`GlobMatcher`）、`nop-find-cli`（picocli 入口 `NopFindMain`，`-n/-s/-g/--json/--no-ignore/--threads`，退出码 0/1/2 对齐 nop-rg）。入口：独立 `nop-find` CLI，或 `nop-cli find` 透传子命令。用法与差异表见 `nop-tools/nop-find/nop-find-cli/README.md`。无 `03-modules` owner doc——缺口声明经 R-G 治理批（plan 2567）登记收口；并发契约随 AI 开发记录区 concurrency 设计集 tools-parallel-batch.md（R4-30/plan 2564，与 nop-rg 共享 FFM/遍历面）。 |
| jq 查询引擎 | `nop-tools/nop-jq/` | 纯 jq 1.7.1 查询引擎（2026-09-28 自 nop-kernel 迁出以保持 nop-kernel 精简；plan 2417 归入 nop-tools）。`JqEngine.compile(expr).apply(root)`，官方 jq 1.7.1 测试套件 430/430 为语义基线。消费方：`nop-ai-toolkit` 的 `jq` 工具（`JqToolExecutor`）。JSONPath 求值不在本模块——见 `nop-kernel/nop-jpath`。无 `03-modules` owner doc——缺口声明经 R-G 治理批（plan 2567）登记收口；CRA 6.2 口径 jq 执行链四重守卫齐全为正面结论，无在案并发缺陷（消费方 `JqToolExecutor` 契约见 `03-modules/nop-refactor.md` 落盘与并发契约节）。 |
| 纯 Java Tree-sitter 运行时 | `nop-tools/nop-treesitter/` | Tree-sitter 的纯 Java 再实现（parser/lexer/subtree arena/query/cursor/external scanner VM；无 JNI/FFM/native 库加载），200+ 上游语法以二进制 blob 加载。消费方：`nop-lint`（AST 匹配内核底座）、`nop-code-lang-*`（多语言代码索引适配）。使用文档：`03-modules/nop-treesitter.md`。 |
| AST 规则引擎（在建） | `nop-tools/nop-lint/` | YAML 驱动的 AST lint 系统，构建于 nop-treesitter 纯 Java 运行时之上：ast-grep 风格源码 pattern 匹配 + XLang xscript 动态检查 + Nop 平台规则库。子模块：`nop-lint-core`（pattern 编译器与匹配内核、规则 DSL 加载、执行档位）、`nop-lint-java`（Java 语言适配与 Java 规则）、`nop-lint-nop`（Nop 平台规则库）、`nop-lint-js`（TS/TSX 语言适配与 tsc 类型桥，2026-09-22 入库）、`nop-lint-maven-plugin`（CheckMojo 构建 check goal，2026-09-24 入库）、`nop-lint-graphql`（NopLintBizModel lint 查询面，2026-09-25 入库）；cli 尚未建。设计权威与 work items 动态状态由平台自身的 AI 开发记录区维护（design/nop-lint/ 13 篇 + backlog 单文件 roadmap，此处按边界规则不直接链接）。无独立 owner doc 页（`03-modules/` 无对应条目）：lint 面实现契约的承载面 = `04-reference/source-anchors.md` 锚点行 `LINT-CC-001`（执行器全局槽卸载/写面互斥/注册表原子）与 `TSCBRIDGE-CC-001`（tsc 桥接协议与代际栅栏），及 `03-modules/nop-treesitter.md`「语言实例身份与并发契约」、`03-modules/nop-refactor.md`「落盘与并发契约」的相邻承载节。R-G 治理批（plan 2567）裁定维持此行内登记形态（不单开 owner doc 页）；R3-08/plan 2531 修复批收敛面（W-02 Route-B volatile/W-03 .tmp 互斥/W-08 写盘守卫）已并入上述锚点与承载节。 |
| 代码修改工具链（在建） | `nop-tools/nop-refactor/` | AI-first 代码修改工具链（读 nop-code / 查 nop-lint / 改 nop-refactor 三件套的"改"面）：批量 pattern 改写（P0 codemod，复用 nop-lint transform 规则与 fix 应用机制）+ 阶梯式语义 rename（P1）。子模块：`nop-refactor-core`（operation 操作框架：check/plan/apply/verify 四段契约，apply/verify 经 nop-lint fix 机制单点应用；RefactorResult self-verification 载荷、verification 计算面、语言无关 symbol SPI、CLI 批处理形态）、`nop-refactor-java`（Java 符号解析适配：内嵌声明索引 + 绑定过滤引用搜索，消费 nop-java-parser 与 nop-lint-java ScopeAnalyzer 公开 API）、`nop-refactor-graphql`（Refactor__ 契约面）。设计权威与 work items 动态状态由平台自身的 AI 开发记录区维护（design/nop-refactor/ + backlog 单文件 roadmap，此处按边界规则不直接链接）。 |
| 字节码分析通道 | `nop-tools/nop-bytecode/` | 编译产物（Maven reactor 输出目录与 jar 产物）之上的字节码层缺陷发现通道，与源码通道 nop-lint 零依赖并行、并行不替代（缺口面归属见 `../../nop-tools/nop-bytecode/docs/gap-ledger.md`）。四层架构：采集层 `io.nop.bytecode.collect`（class 目录/jar → 工件清单 className/major/sha256，缺失/损坏响亮失败）、内核层 `io.nop.bytecode.kernel`（方法内指令级 CFG + 槽位精确抽象解释）、分析器层 `io.nop.bytecode.analysis.nullflow`（三值 nullness lattice 沿 CFG 传播）、通道层 `io.nop.bytecode.cli`（发现流 console/JSON 渲染）。发现流 CLI 为 report-only：退出码 `0`=运行完成（有无 findings 均 0）、`2`=失败（usage 错误/缺失输入/损坏产物/分析异常），fail-fast 不输出部分报告；升 hard gate 须独立 plan。规则资产独立于 nop-lint（`nullflow/` 命名空间）；autofix/风格/注释依赖面不入本通道。使用文档：`03-modules/nop-bytecode.md`。 |
| 增量编译/测试引擎 | `nop-tools/nop-ib/` | AI 开发期快路径增量引擎（替代日常 `mvn build`/`mvn test` 反馈回路，不替代构建系统：CI/发布/生成/全量冷构建仍走 Maven）。编译走 nop-javac 文件式编译 + 字节码依赖闭包；测试走模块图粗筛 + 类图细选，首版委托 Surefire 执行。依赖方向单向：nop-ib → nop-javac + nop-bytecode；不新增 -api、不依赖 nop-code-service、不入发布型模块运行时 classpath（IB-G1 裁定）。正确性硬门：零假绿（`partial`/`wideImpact`/`fallback` 显式出现在 JSON）、禁止 intra-project jar 进 fast-path classpath、写产物与测试共用 `.mvn/build.lock`（锁超时退出码 75 对齐 mvnq）、模型过期即全量、`--max-tests` 默认拒绝。命令面：`status/plan/compile/test/check/explain/model/serve/cache/bench`（统一 `--json`）。设计权威：`../../ai-dev/design/nop-incremental-build/05-nop-ib-engine-design.md`；roadmap：`../../ai-dev/backlog/nop-ib-roadmap.md`。无 `03-modules` owner doc（Wave 0 登记，行内形态）。 |

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
| `nop-report/` | 报表引擎（Excel/PDF/DOCX；图表渲染经 `nop-chart-report` opt-in，见 `02-core-guides/chart-static-render.md`；Word 文档展开/图片绑定/统一出口见 `02-core-guides/word-document-model.md`，PDF 渲染器在 nop-report-pdf 的 `io.nop.report.pdf.doc` 包） |
| `nop-format/nop-canvas2d` | 纯 Java2D Canvas2D 语义实现（图表静态渲染的绘图底座） |
| `nop-format/nop-chart-echarts` | ECharts 静态渲染引擎（GraalJS 执行官方 dist + Java2D shim） |
| `nop-format/nop-chart-export` | 图表导出壳契约 + ChartModel→ECharts option 适配器（JFreeChart 已退役） |
| `nop-format/nop-office-model` | Office 共享叶子模型（OfficeFont/Word 四类样式/边框/颜色/对齐枚举；Excel 与 Word 共用） |
| `nop-format/nop-office-doc-model` | Word 文档结构模型（OfficeDocModel 八类块/行内/页面设置/assets/styles 容器；owner doc 见 `02-core-guides/word-document-model.md`） |
| `nop-format/nop-ooxml/nop-ooxml-docx` | docx 包读写（解析/生成/round-trip；模板标注解析） |
| `nop-report/nop-chart-report` | 可选报表图表接线（IChartImageProvider 实现，opt-in；缺席静默跳过） |
| `nop-rule/` | 规则引擎（决策树/决策矩阵） |
| `nop-batch/` | 批处理引擎（chunk 处理、断点续传） |
| `nop-dyn/` | 动态表单/实体（运行时定义） |
| `nop-file/` | 文件管理（上传/下载/去重）。`nop-file-app` 为休眠态装配壳（不是可部署 HTTP 服务），复活路径见模块内注记。并发契约见 `04-reference/source-anchors.md` 的 `FILE-CC-001` 锚点 + `03-modules/nop-file.md`「文件去重延迟清理并发契约」节 |
| `nop-datav/` | 数据可视化（dashboard/screen/alert/ChatBI/导出交付）。owner doc `03-modules/nop-datav.md` 已建（最小面——并发契约为主，完整功能文档建设归 R-G 治理批）；并发契约（导出终态 fencing/发布版本行锁/告警互斥/并发限额/调度副作用 afterCommit 后置/启动延迟重试/查询行上界）详见 `03-modules/nop-datav.md` + `04-reference/source-anchors.md` 的 `DATAV-CC-001` 锚点 |
| `nop-credential/` | 凭证管理（存储/取用授权/OAuth 凭证流程）。`nop-credential-app` 为休眠态装配壳（不是可部署 HTTP 服务），复活路径见模块内注记 |
| `nop-retry/` | 分布式重试引擎 |
| `nop-tcc/` | TCC 分布式事务 |
| `nop-metadata/` | 联邦式元数据 / BI 语义层 / 血缘 / 质量 / 对账（跨数据源逻辑表抽象 + 聚合查询 + 自动血缘 + 质量检查点 cron 调度 + 数据对账） |

### 2.6 nop-metadata 快速概览

`nop-metadata/` 是 Nop 平台的元数据中心，承担五类职责：

- **元数据目录（Catalog）**：跨 JDBC 数据源 / SQL 视图 / ORM 实体的统一实体抽象（`NopMetaEntity`，entityKind=EXTERNAL/SQL_VIEW/PHYSICAL，plan 2261 实体归一）；`syncExternalTables` 自动从外部库同步物理表结构为 EXTERNAL 实体行。
- **BI 语义层（Semantic Layer）**：在逻辑表之上定义 Measure / Dimension / Join / Filter，通过 `queryAggregation` / `queryJoinData` / `queryData` 提供 EQL/GraphQL 查询入口；跨库 JOIN 支持同库原生 SQL + 跨库应用层拼接。
- **血缘追踪（Lineage）**：从 SQL AST 自动抽取表级 + 列级 + 指标级血缘（`SqlColumnLineageExtractor` / `SqlSourceEntityExtractor`）；支持上下游追溯与影响分析。
- **数据质量（Quality）**：质量规则 + 检查点批量执行 + 自动评分；`MetaQualityCheckpointScheduler` 提供 cron 调度（BeanMethodJobInvoker 复用既有编排链）；支持 webhook / notify 动作分发执行摘要。
- **数据对账（Reconciliation）**：配置驱动（columnName + matchStrategy）的双向数据比对，支持精确/模糊匹配。

详细使用文档：`03-modules/nop-metadata.md`。

### 3. AI 专属子系统

- `nop-ai/`

当任务直接涉及 AI agent、tool、skill、RAG、shell、MCP、AI service 时，再深入 `nop-ai/`。

`nop-ai` 分层速查：

| 模块 | 职责 |
|------|------|
| `nop-ai-api` | 公开 API 契约：`IChatService`、`ChatOptions`、`ChatMessage`（Agent Engine 直接消费的类型）。WI14/plan 2446 后为纯能力契约 jar（29 文件），实体 CRUD 生成面已删 |
| `nop-ai-core` | LLM 调用实现（`ChatServiceImpl` + `ILlmDialect` Provider 适配）、错误码。旧 chat API（`IAiChatService` 等）已删除（plan 2414），勿再引用 ；`core/file` = 文件操作契约域（P2-MA1-012 双抽象收口中，plan 2429 死面已删、IFileOperator→IToolFileSystem 收口立 successor）、`core/response` = LLM 输出结构化解析平台能力（plan 2429 留守裁定：prompt 求值引擎内在能力 + dsl-orm→dyn-service 生成链消费） |

| `nop-ai-agent` | Agent DSL + 执行引擎（`DefaultAgentEngine`/`ReActAgentExecutor`）。经 `IChatService` 调 LLM，经 `ITokenEstimator` bridge 消费 token 估算；可靠性机制直接复用 `io.nop.ai.core.reliability` 包（`ThresholdBreaker`/`LlmErrorClassifier`/`ProviderFailoverChain`/`StandardRetryPolicy` 等），并直接使用 core 的 `model`/`agent`/`dialect` 公共模型类型（2026-09-14 按 live import 修正） |
| `nop-ai-toolkit` | 工具执行层：19 个具体 `*Executor`（ReadFile/Bash/Http/GraphqlQuery/ApplyDelta/Patch/Skill 等，`io.nop.ai.toolkit.tools`）+ `LocalToolFileSystem` + `ToolManagerImpl`/`DefaultToolExecutorProvider`；抽象面为 `IToolExecutor`/`IToolManager` 与工具 DSL（tool.xdef） |
| `nop-ai-tools` | 具体工具实现 + DSL 文档工具契约与实现（`IDslTool`/`DslToolImpl`，`io.nop.ai.tools.xdsl`——M5-P1 裁定 A：自 nop-ai-coder 下沉；DSL 转换经 nop-converter）+ MCP 工具适配（`io.nop.ai.tools.mcp.AiFileTool`，WI15/plan 2452 自解散的 nop-ai-mcp-server 迁入，VFS bean 注册随迁）。原 `nop-ai-skills` 聚合已解散（WI17/plan 2454：code-analyzer 升 nop-ai 直属，deepwiki/translate prompt 资源迁 nop-ai-core VFS） |
| `nop-ai-gateway` | AI 网关，承载三块能力：LLM failover（多 Provider 路由/转换 + 透明账号切换）+ channel 消息网关（`IChannelConnector`/`FeishuConnector`/`ChannelMessageServiceImpl`/`ChannelSessionStoreImpl`）+ 扫码登录编排（`ChannelLoginApiBizModel`/`ChannelLoginScanProcessor`）。依赖边：gateway → `nop-ai-agent`/`nop-ai-dao`/`nop-integration-api`/`nop-integration-feishu`（channel）+ `nop-biz-auth-core`/`nop-auth-api`（login）；`nop-auth-service` 与消息总线为部署侧可选装配（详见 `docs-for-ai/03-modules/nop-ai-gateway.md`） |
| `nop-ai-rag` | RAG 检索增强 |
| `nop-ai-shell` | Shell 沙箱执行环境 |
| `nop-ai-coder` | AI 编程助手 |
| `nop-ai-maven` | WI16/plan 2453 已解散：mavenvfs 能力迁入 `nop-ai-toolkit`（`io.nop.ai.toolkit.mavenvfs`），模块删除 |
| `nop-ai-dsl-orm` | ORM 与 DSL 模型集成 |
| `nop-ai-dao`/`nop-ai-meta`/`nop-ai-service`/`nop-ai-web`/`nop-ai-app` | 标准 Nop 分层（ORM 实体/元数据/BizModel/页面/启动应用） |

## 常见任务应该先看哪里

| 任务 | 优先路径 |
|------|---------|
| 理解代码生成链路 | `nop-kernel/`、业务模块下的 `*-codegen/` |
| 理解 ORM / DAO / 迁移 | `nop-persistence/` |
| 理解 BizModel / GraphQL | `nop-service-framework/` |
| 找标准业务实现参考 | `nop-auth/`、`nop-job/`、`nop-task/`、`nop-wf/` |
| 找 CLI 或生成入口 | `nop-runner/`、`tools/install-cli.sh`、`scripts/nop-cli.cmd` |
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
