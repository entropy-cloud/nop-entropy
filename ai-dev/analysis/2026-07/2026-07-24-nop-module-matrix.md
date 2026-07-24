# nop-entropy 业务模块矩阵全景与定位

> Status: resolved
> Date: 2026-07-24
> Scope: 全平台业务模块（`nop-auth`/`nop-sys`/`nop-wf`/`nop-task`/`nop-job`/`nop-batch`/`nop-report`/`nop-rule`/`nop-dyn`/`nop-file`/`nop-retry`/`nop-tcc`/`nop-metadata`/`nop-stream`/`nop-code`/`nop-graph`/`nop-ai` + 基础设施 `nop-network`/`nop-integration`/`nop-search`/`nop-cluster`/`nop-message`/`nop-plugin`）；模块职责分类 + `*-api` 依赖矩阵 + 模块发现机制 + 7 领域竞品对标
> Conclusion: nop-entropy 的业务模块生态以「统一 model-first 骨架 + 可插拔 `_module` 发现 + 跨模块 `-api` 契约分层」为组织主线。依赖矩阵实证显示：**跨领域被广泛依赖的契约是 HTTP/RPC/集成/调度/认证等基础设施型 `-api`（≥3 外部消费者）**，而 `nop-auth-api`/`nop-sys-api`/`nop-batch-api`/`nop-dyn-api`/`nop-file-api`/`nop-report-api`/`nop-task-api` 等**领域型 `-api` 外部消费者为 0**——它们通过运行期 bean/拦截器/`_module` 发现到达应用，而非 Maven 制品依赖。竞品对标（工作流/调度/流/元数据/AI/规则/报表 7 领域）显示 nop 的差异化不在单模块功能清单的覆盖率，而在「一套 XDSL/Delta 机制统管所有领域 DSL（wf.xdef/batch.xdef/rule 模型/orm 模型）+ model-first 一致骨架 + 可逆计算定制」，这是 Flowable/PowerJob/Flink/DataHub/LangGraph/Drools/JasperReports 等单点竞品不具备的横向一致性。
> Mission: nop-deep-analysis（Work Item A5）
> Superseded By: （本分析为 A7 capstone 提供「业务模块矩阵」层面的参照；若 A7 重新组织模块生态章节，则被替代）

## Context

- **要回答的问题**：nop-entropy 平台的业务模块生态长什么样？各模块职责如何分类、相互依赖关系如何？每个领域相对主流竞品（Flowable/Camunda、PowerJob/XXL-Job、Flink、DataHub/OpenMetadata、LangGraph/Agno、Drools、JasperReports/FineReport）的定位是什么？平台作为整体的差异化在哪里？
- **涉及模块/子系统**：全平台业务模块 + 基础设施模块（`module-groups.md` 全部分组）。
- **约束**：仅模块矩阵与定位——A2 覆盖核心引擎实现、A3 覆盖 codegen/Delta、A4 覆盖 GraphQL/服务/前端。单模块的逐行实现审计不在本分析范围（以 `ai-dev/design/<module>/` 或 `docs-for-ai/03-modules/<module>.md` 为准，引用即可）。
- **来源基线**：`docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/03-modules/`（16 份模块专题）、`docs-for-ai/04-reference/source-anchors.md`（MOD/AUTH/WF/BATCH/REPORT/CODE/SYS/AUDIT/META/RPC/INT 系列）。**18 个 source-anchors 锚点经源码交叉核对全部 PASS**（见 §6 终检）。依赖矩阵由 Python 脚本对全部 `pom.xml` 统计 `*-api` 外部消费者得出，非预设结论。竞品对标优先复用 `ai-dev/analysis/` 既有专题（≥10 份），仅对缺失领域（规则/报表）与主流刷新（Flowable/Camunda、LangGraph）补联网调研。

## 1. 全景模块矩阵（按职责分类）

平台根 `pom.xml` 是一个 Maven 多模块工程。按**职责领域**将业务/基础设施模块分为 6 类。每模块附「定位 + 代表依据」（依据优先级：`03-modules/` 专题 > `module-groups.md` 行 > source-anchor）。

### 1.1 业务模块矩阵总表

| 分类 | 模块 | 一句话定位 | 代表依据 |
|------|------|-----------|----------|
| **基础（身份/系统服务）** | `nop-auth` | 用户认证、RBAC 权限、多租户、SSO/OAuth2；权限/租户是跨模块横切能力（经拦截器/`_module` 到达，非 `-api` 制品） | `03-modules/nop-auth.md`、AUTH-001~003、TNT-001~008 |
| | `nop-sys` | 序列号、数据字典、i18n、Maker-checker 审批、分布式锁、事件队列、字段级审计、业务编码规则 CodeRule | `03-modules/nop-sys.md`、SYS-001、CODE-001~004、AUDIT-001~002 |
| | `nop-file` | 文件上传/下载/管理、Hash 去重、业务对象关联 | `03-modules/nop-file.md`、INT 系列（`nop-integration-api` 消费者） |
| **流程编排** | `nop-wf` | 工作流/BPM 引擎：会签/加签/委托/转办/驳回、子流程、vote-group、`oa.xwf` base 模板沉淀审批共性 | `03-modules/nop-wf.md`、WF-001~006、`ai-dev/design/nop-wf/` |
| | `nop-task` | 通用任务/逻辑流引擎：多步骤编排、信号、重试、超时（`task.xlib` 标签库） | `03-modules/nop-task.md`、XLANG-005（task.xlib） |
| | `nop-job` | 分布式定时任务调度：CRON/固定频率/一次性、协调器(coordinator)/工作者(worker)架构、scheduler.yaml 注册 | `03-modules/nop-job.md`、WF-006（scheduler.yaml）、`ai-dev/design/nop-job/` |
| | `nop-batch` | 批处理引擎：chunk 处理、断点续传、记录级幂等、`batch.xdef` DSL + `batch.xlib` 标签库 | `03-modules/nop-batch.md`、BATCH-001~002 |
| **数据** | `nop-metadata` | 联邦式元数据 / BI 语义层 / 列级血缘 / 质量检查点 cron 调度 / 数据对账（跨数据源逻辑表抽象 + 聚合查询） | `03-modules/nop-metadata.md`、META-001~005、`ai-dev/design/nop-metadata/` |
| | `nop-report` | 报表引擎：XPT 单元格展开模型（批注→模型）、套打、Excel/PDF/DOCX 输出 | `03-modules/nop-report.md`、REPORT-001~003、RPT-001 |
| | `nop-rule` | 规则引擎：决策树 + 决策矩阵、`RuleModel` 运行期索引、版本管理 | `03-modules/nop-rule.md`、MODEL-INIT-003（RuleModel.init）、`ai-dev/design/nop-rule/` |
| | `nop-dyn` | 动态表单/实体：运行时定义业务模型/页面/SQL | `03-modules/nop-dyn.md` |
| **计算** | `nop-stream` | 分布式流处理引擎：核心 API/状态后端/算子、CEP（NFA 模式匹配）、checkpoint 协调、Flink API 兼容层 | `module-groups.md` §流处理引擎、`ai-dev/design/nop-stream/`、`ai-dev/analysis/nop-stream-flink-comparison-deep-dive.md` |
| | `nop-code` | 多语言代码索引与智能分析：CodeCallGraph、语言适配器（Java/Python/TS）、执行流追踪、死代码检测 | `03-modules/nop-code.md`、`module-groups.md` §WIP、`ai-dev/design/nop-code/` |
| | `nop-graph` | 通用图算法库：BFS/PageRank/TarjanSCC/ImpactPropagator/LeidenDetector/BetweennessCentrality（供 nop-code，未来供 wf/task/stream） | `module-groups.md` §通用图算法库、`nop-graph-api`（`IGraph`/`Edge` 零外部依赖） |
| **集成/可靠性** | `nop-integration` | 外部集成适配器：邮件（Java/腾讯）、短信（腾讯/云片）、文件存储（本地/OSS/SFTP）、二维码 | `03-modules/reusable-modules-overview.md`、INT-001~002 |
| | `nop-retry` | 分布式重试引擎：固定间隔/指数退避、命名空间隔离 | `03-modules/nop-retry.md` |
| | `nop-tcc` | TCC 分布式事务协调器：分支事务管理 | `03-modules/nop-tcc.md`、`nop-rpc-api` 消费者 |
| **AI** | `nop-ai` | AI 子系统：LLM Chat、Prompt 管理、Agent 引擎（可靠性/超时/租约续期）、toolkit、RAG、MCP、shell、代码生成（21 子模块） | `03-modules/nop-ai.md`、AISEC-001~003、AIREL-001、`ai-dev/design/nop-ai-agent/`、`ai-dev/analysis/2026-07-23-nop-ai-architecture-governance.md` |

### 1.2 基础设施模块（无独立业务 ORM，按需集成）

| 模块 | 一句话定位 | 代表依据 |
|------|-----------|----------|
| `nop-network` | 网络通信：HTTP（client/server filters）、Netty、RPC、Socket、Vert.x；`nop-rpc-api`/`nop-http-api` 契约归属 | RPC-001~008、GQL-008 |
| `nop-search` | 搜索引擎抽象：Lucene 实现；`nop-search-api` 被 code/metadata/ai 复用 | `nop-search-api` 消费者清单 |
| `nop-cluster` | 集群基础设施：服务发现、负载均衡、限流（Sentinel）、Nacos 集成 | RPC-003~004（ClusterRpcServiceInvoker/Client） |
| `nop-message` | 消息抽象层：Kafka/Pulsar/Debezium 连接器 | `reusable-modules-overview.md` §基础设施 |
| `nop-plugin` | 插件管理：`nop-plugin-api`/`-manager`/`-support` | `nop-plugin-api` 消费者 |

> **说明**：`nop-graph` 无独立 `03-modules/` 专题（是内部算法库而非业务模块），定位取自 `module-groups.md` §通用图算法库 + 其 `*-api` 制品定义。`nop-job`/`nop-task` 无专属 source-anchor 编号，依据取 `03-modules/` 专题 + 既有对比分析（见 §5）。

## 2. 模块依赖关系矩阵（`*-api` 制品实证）

### 2.1 方法论

**以 `*-api` Maven 制品作为跨模块依赖契约的唯一实证来源**：`*-api` 是模块对外暴露的稳定接口/消息 bean 契约；模块内部子模块（`-dao`/`-service`/`-web`/`-core`）之间的依赖不进 `*-api`，不计入跨模块契约。用 Python 脚本扫描全部 `pom.xml`（排除 `target/`、`-api` 自身 pom、`nop-bom`），统计每个 `*-api` 被**多少个外部 top-level 模块**消费。

**可操作定义（由数据分布推导，非预设）**：
- **被广泛依赖** = 外部 top-level 消费者 ≥ 3（基础设施工具型契约）
- **领域契约** = 外部消费者 1~2（多为模块内分层 + 少量跨模块）
- **独立/叶子契约** = 外部消费者 0（模块经运行期机制而非 Maven 制品到达应用）

### 2.2 依赖矩阵（按入站消费者数降序）

| `*-api` 制品 | 外部 top-level 消费者数 | 消费者 | 分类 |
|--------------|:---:|--------|------|
| `nop-http-api` | 9 | nop-ai, nop-auth, nop-core-framework, nop-metadata, nop-network, nop-quarkus, nop-service-framework, nop-spring, tests | **被广泛依赖（基础）** |
| `nop-rpc-api` | 7 | nop-cluster, nop-kernel, nop-network, nop-persistence, nop-service-framework, nop-tcc, tests | **被广泛依赖（基础）** |
| `nop-search-api` | 4 | nop-ai, nop-code, nop-metadata, nop-search | **被广泛依赖（计算）** |
| `nop-biz-auth-api` | 4 | nop-auth, nop-frontend-support, nop-service-framework, tests | **被广泛依赖（基础）** |
| `nop-integration-api` | 3 | nop-file, nop-integration, tests | **被广泛依赖（集成）** |
| `nop-job-api` | 3 | nop-job, nop-metadata, tests | **被广泛依赖（编排：metadata 用它做质量检查点 cron）** |
| `nop-ai-api` | 2 | nop-ai, nop-wf（nop-wf-ai） | 领域契约（wf↔ai 跨） |
| `nop-wf-api` | 2 | nop-wf, tests | 领域契约 |
| `nop-graph-api` | 2 | nop-code, nop-graph | 领域契约（code 复用图算法） |
| `nop-tcc-api` | 2 | nop-tcc, tests | 领域契约 |
| `nop-rule-api` | 2 | nop-rule, tests | 领域契约 |
| `nop-code-api` | 1 | nop-code | 领域契约（模块内） |
| `nop-stream-api` | 1 | tests | 领域契约 |
| `nop-metadata-api` | 1 | nop-metadata | 领域契约 |
| `nop-retry-api` | 1 | nop-retry | 领域契约 |
| `nop-plugin-api` | 1 | nop-core-framework | 领域契约 |
| `nop-auth-api` | 0 | —（无外部消费者） | **独立/叶子** |
| `nop-sys-api` | 0 | — | **独立/叶子** |
| `nop-batch-api` | 0 | — | **独立/叶子** |
| `nop-dyn-api` | 0 | — | **独立/叶子** |
| `nop-file-api` | 0 | — | **独立/叶子** |
| `nop-oauth-api` | 0 | — | **独立/叶子** |
| `nop-report-api` | 0 | — | **独立/叶子** |
| `nop-task-api` | 0 | — | **独立/叶子** |

**关键发现（证据驱动，印证了 plan 的初查假设）**：
1. **基础设施型契约横切全平台**：`nop-http-api`(9) 与 `nop-rpc-api`(7) 是真正的「平台脊梁」，被几乎所有上层模块消费——它们定义了 HTTP/RPC 的统一抽象（RPC-001 `IRpcService.callAsync`、GQL-008 五入口统一分发）。
2. **`nop-auth-api`/`nop-sys-api` 外部消费者为 0**：认证与系统服务**不通过 Maven `-api` 制品被消费**，而是通过运行期机制到达应用——`nop-auth` 的 `AuthFilterConfig`/`AuthHttpServerFilter`（AUTH-001~003）作为 HTTP server filter、`nop-sys` 的 `OrmEntityChangeLogInterceptor`（AUDIT-001）作为 ORM 拦截器、`SysSequenceGenerator`（CODE-002）作为 bean，经 `_module` 发现与 `beans.xml` 装配注入。这是 nop 的设计选择：**横切能力走运行期织入而非编译期依赖**。
3. **`nop-job-api` 被 `nop-metadata` 消费**：印证 META-005——`MetaQualityCheckpointScheduler` 复用 nop-job 的 cron 调度（`BeanMethodJobInvoker`）做质量检查点定时执行。这是「编排模块被数据模块复用」的实证。
4. **`nop-graph-api` 被 `nop-code` 消费**：印证图算法库作为代码索引的底座（CodeCallGraph 复用 PageRank/SCC 等算法）。

### 2.3 依赖关系图（mermaid）

```mermaid
graph TD
    subgraph 基础["基础（身份/系统服务）"]
        AUTH[nop-auth]
        SYS[nop-sys]
        FILE[nop-file]
    end
    subgraph 编排["流程编排"]
        WF[nop-wf]
        TASK[nop-task]
        JOB[nop-job]
        BATCH[nop-batch]
    end
    subgraph 数据["数据"]
        META[nop-metadata]
        REPORT[nop-report]
        RULE[nop-rule]
        DYN[nop-dyn]
    end
    subgraph 计算["计算"]
        STREAM[nop-stream]
        CODE[nop-code]
        GRAPH[nop-graph]
    end
    subgraph AI["AI"]
        AI[nop-ai]
    end

    HTTP[nop-http-api<br/>9 消费者]
    RPC[nop-rpc-api<br/>7 消费者]
    BIZAUTH[nop-biz-auth-api<br/>4 消费者]
    SEARCH[nop-search-api<br/>4 消费者]
    INTEG[nop-integration-api<br/>3 消费者]
    JOBAPI[nop-job-api<br/>3 消费者]
    AIAPI[nop-ai-api<br/>2 消费者]
    GRAPHAPI[nop-graph-api<br/>2 消费者]

    AUTH -.运行期filter/拦截器.-> 全局["全平台（运行期 _module 发现）"]
    SYS -.运行期bean/拦截器.-> 全局

    AUTH --> BIZAUTH
    META --> JOBAPI
    META --> SEARCH
    META --> HTTP
    CODE --> GRAPHAPI
    CODE --> SEARCH
    AI --> AIAPI
    AI --> SEARCH
    AI --> HTTP
    WF --> AIAPI
    FILE --> INTEG

    classDef widely fill:#cfe,stroke:#3a3
    classDef domain fill:#eef,stroke:#33a
    class HTTP,RPC,BIZAUTH,SEARCH,INTEG,JOBAPI widely
    class AIAPI,GRAPHAPI domain
```

> 图例：实线 = Maven `-api` 编译期依赖；虚线 = 运行期机制（filter/拦截器/`_module` 发现），无编译期制品依赖。绿色 = 被广泛依赖（≥3）；蓝色 = 领域契约（1~2）。

## 3. 模块发现与可插拔机制（MOD-001~005）

平台的「可插拔」建立在统一的模块发现机制上（与单点竞品「引入 jar 即用」不同，nop 把模块发现也纳入可逆计算体系）：

| 锚点 | 机制 | 核对结果 |
|------|------|----------|
| MOD-001 | `ModuleManager` 全局单例；`discover()` 扫描 `*/*/_module`（`ModuleManager.java:74` `findAll("*/*/_module")`）；`getEnabledModules()`（`:165`）供所有消费者遍历 | PASS（源码核对） |
| MOD-002 | `ModuleModel` 对应 `app.module.yaml` 的 Java bean | PASS（`_module` 资源读取 `ModuleManager.java:104`） |
| MOD-003 | `ModuleNamespaceHandler` 实现 `module:` VFS 名字空间，按模块隔离查找资源 | PASS（`ModuleNamespaceHandler.java:17`） |
| MOD-004 | `ResourceHelper.getModuleId()/getModuleName()` 转换方法 | （引用 source-anchor） |
| MOD-005 | `CoreConfigs` 定义 `CFG_MODULE_ENABLED_MODULE_NAMES`（`:184`）/`CFG_MODULE_DISABLED_MODULE_NAMES`（`:187`） | PASS（源码核对） |

**设计含义**：任何业务模块只要提供 `/_module` 标记 + 遵循标准分层（`-api`/`-dao`/`-meta`/`-service`/`-web`/`-app`），即可被 `discover()` 纳入并按配置启用/禁用。这是「model-first 统一骨架」在模块级的体现——所有业务模块共享同一套生成模板（`nop/templates/orm-web`）与同一套发现/装配规则，而非各自独立的集成方式。

## 4. 平台模块生态的差异化定位

把单点竞品放在一起看，nop 的差异化**不在某单一模块的功能清单覆盖率**，而在三个横向一致性：

1. **一套 XDSL/Delta 机制统管所有领域 DSL**：工作流（`wf.xdef` + `oa.xwf`）、批处理（`batch.xdef` + `.batch.xml`）、规则（`RuleModel`）、ORM（`orm.xdef`）、报表（XPT）、任务（`task.xlib`）都是平台 XDSL 体系下的一个 schema，共享 `x:extends`/`x:override`/`x:post-extends` 合并语义（EXT-002 `XDslExtender`）。竞品（Flowable 的 BPMN XML、PowerJob 的自定义配置、Drools 的 DRL）各自有独立模型语言，无法跨领域复用合并/定制机制。

2. **model-first 统一骨架**：每个业务模块都从 `model/*.orm.xml` 出发，经同一套 codegen 模板（`nop/templates/orm-web`，GEN-001~009）生成 `-meta`/`-web`/`-app` 分层，自动产出 GraphQL API + AMIS/Flux 管理页面。竞品的「模块」通常是独立产品（DataHub/OpenMetadata 是独立部署的服务，JasperReports 是独立库），与平台骨架无一致性约束。

3. **可逆计算定制跨模块扩展**：通过 Delta（`DeltaResourceStore` EXT-003/VFS-001 的 tenant→deltaLayers→base 分层），应用可在不修改模块源码的前提下，覆盖任意模块的 `beans.xml`/`*.xmeta`/`*.action-auth.xml`/页面/DSL。这是 `_` 前缀生成物不可手改约束的正面——所有定制回流到 Delta 层，升级时自动合并。

## 5. 逐领域竞品对标

> 每领域结构：**竞品做什么 / nop 模块做什么 / 差异点**。优先复用 `ai-dev/analysis/` 既有结论（标注引用）；缺失领域补联网调研（附 URL + 访问日期 2026-07-24）。

### 5.1 工作流：Flowable / Camunda vs nop-wf

- **竞品做什么**：Flowable 与 Camunda 均基于 **BPMN 2.0 标准**，是成熟的企业级流程引擎。Camunda 更成熟、代码量更大、G2 评分第一；Flowable 是 fork、更用户友好、对 CMMN/DMN 支持更多。二者核心是「BPMN 模型 → 引擎解释执行」，模型语言是外部标准（BPMN XML）。（来源：[Camunda vs Flowable G2](https://www.g2.com/compare/camunda-vs-flowable-platform)、[Medium: Camunda and Flowable](https://medium.com/version-1/camunda-and-flowable-process-and-workflow-automation-platforms-bf4fae4f00ed)，访问 2026-07-24）
- **nop-wf 做什么**：工作流是平台 XDSL 体系的一个 schema（`wf.xdef` WF-003），`oa.xwf` base 模板（WF-001）把审批共性沉淀为可继承的 base XDSL；`oa.xlib`/`wf-actor.xlib`/`wf-vote.xlib`（WF-002/004）把领域规则外置为标签库；`WorkflowEngineImpl`（WF-004）+ `IWorkflowStore`（WF-005）提供引擎与 store 扩展；`WfTaskScanner`（WF-006）复用 nop-job 做定时扫描。
- **差异点**：**复用** `2026-07-02-flowlong-warmflow-vs-nop-wf-comparison.md`（resolved）结论——nop-wf 在引擎核心能力（节点/路由/会签/驳回/转办/子流程/审批人解析）上基本覆盖 FlowLong/Warm-Flow，且在 **XDSL 可逆计算、状态机严谨性、信号机制、业务实体绑定**上明显领先。相对 Flowable/Camunda：nop-wf **不追求 BPMN 2.0 标准合规**（无标准 BPMN 模型互交换诉求），而是把工作流 DSL 纳入平台统一 Delta/定制体系——流程定义可被应用的 Delta 层覆盖、`oa.xwf` 可被业务流程 `x:extends`。代价：缺乏 BPMN 标准生态（设计师工具、互交换）；收益：与平台 model-first 骨架、权限、租户、表单一体化。

### 5.2 调度：XXL-Job / PowerJob / SnailJob vs nop-job / nop-task

- **竞品做什么**：XXL-Job（轻量中心化调度）、PowerJob（分布式、支持 MapReduce/工作流图/秒级调度）、SnailJob（分布式重试+调度）。PowerJob 功能最全（DAG 工作流、容器调度、秒级）。
- **nop-job / nop-task 做什么**：`nop-job` 是 coordinator/worker 架构的分布式调度（CRON/固定频率/一次性），通过 `scheduler.yaml`（WF-006）注册任务；`nop-task` 是轻量逻辑流编排引擎（`task.xlib` XLANG-005）。`nop-job-api` 被 `nop-metadata` 消费做检查点 cron（实证）。
- **差异点**：**复用** `2026-05-17-snail-job-vs-nop-job-comparison.md`、`2026-05-18a/b-powerjob-vs-nop-job-*.md`（识别 nop-job 容错/功能 gap）、`2026-05-18-juggle-vs-nop-task-comparison.md`（Juggle 接口编排 vs nop-task）结论——nop-job 在**功能广度上不及 PowerJob**（缺 DAG 工作流图、容器调度），但调度能力与平台深度集成（复用 nop-core-framework 的 IoC/配置、`scheduler.yaml` 走 `@cfg:` value-resolver、与 nop-wf/nop-metadata 共享编排链）。差异化：调度不是独立产品，而是平台编排能力的一部分（`nop-job-api` 作为契约被其他模块复用）。

### 5.3 流处理：Flink vs nop-stream

- **竞品做什么**：Apache Flink 是成熟的分布式流处理引擎，状态后端、checkpoint、窗口、CEP、exactly-once、大规模集群。
- **nop-stream 做什么**：核心 API/状态后端/算子、CEP（NFA 模式匹配）、runtime（checkpoint 协调器、窗口算子）、connector、`nop-stream-flink`（Flink API 兼容层）。
- **差异点**：**复用** `nop-stream-flink-comparison-deep-dive.md`（2026-07-20）+ `2026-06-14-nop-stream-barrier-checkpoint-comparison.md` + `2026-07-20-nop-stream-dataflow-api-gap-analysis.md` 结论——nop-stream 是「从 Flink 低成本迁移设计」的轻量实现，**不追求 Flink 的大规模生产级集群能力**，定位为平台内的嵌入式流处理（与 ORM/EQL/IoC 同进程）。`nop-stream-flink` 兼容层说明设计意图是「API 对齐 Flink、实现自研」。差异化：流处理纳入平台统一资源/IoC 体系，而非独立部署的集群组件。

### 5.4 元数据 / BI：DataHub / OpenMetadata vs nop-metadata

- **竞品做什么**：DataHub（数据目录、aspect 模型、Kafka/ES）、OpenMetadata（AI 上下文层、模板方法 CRUD 基类）、Atlas/Amundsen/Marquez（血缘/发现）；dbt（转换）、Great Expectations/Griffin（质量）。**复用** `metadata-survey/`（resolved，5 平台 + dbt/GE/Griffin 深度分析）结论——这些是**独立部署的元数据中心**，各有自己的存储/服务/ ingestion 框架。
- **nop-metadata 做什么**：联邦式元数据——跨 JDBC/SQL 视图/ORM 实体的统一逻辑表（`NopMetaTable`）、BI 语义层（Measure/Dimension/Join）、SQL AST 列级血缘（`SqlColumnLineageExtractor` META-004）、质量规则 + 检查点 cron（META-005，复用 nop-job）、数据对账。
- **差异点**：**复用** `2026-07-15-nop-orm-model-management-and-bi-metadata-analysis.md`（指出元数据目录/血缘/质量/语义层差距）+ `2026-07-15-superset-vs-nop-bi-analysis.md`（Nop 非 BI 平台，但 EQL 成熟，缺交互式可视化层）。**注意时序**：前者写于 nop-metadata 模块成型前，部分「差距」已被 nop-metadata 模块补齐（META-001~005 实证）。差异化：nop-metadata 的元数据**直接基于平台 ORM 模型**（entity 即一等元数据来源），而非外部 ingestion 抓取——`MetaTableReferenceResolver`（META-002）按 tableType 分派到平台 `IOrmSessionFactory` 注册的实体。这是 model-first 在元数据领域的延伸。

### 5.5 AI 编排：LangGraph / Agno vs nop-ai

- **竞品做什么**：LangGraph 是图驱动、stateful 的 low-level agent orchestration runtime（node=计算步骤、edge=路由、共享 state 对象、checkpointer 持久化）（来源：[LangGraph docs](https://docs.langchain.com/oss/python/langgraph/overview)、[langchain.com/langgraph](https://www.langchain.com/langgraph)，访问 2026-07-24）。Agno 是通用 Agent SDK + AgentOS 生产部署平台（**复用** `agent-survey/2026-06-13-agno-vs-goal-driver-vs-nop-agent-survey.md`）。
- **nop-ai 做什么**：21 子模块覆盖 LLM Chat、Agent 引擎（可靠性/超时/租约续期 AIREL-001、路径安全 AISEC-001~003）、toolkit、RAG、MCP server、shell、代码生成。`nop-ai-api` 被 `nop-wf-ai` 消费（wf↔ai 跨模块，实证）。
- **差异点**：**复用** `agent-survey/`（40+ 份，含 agentscope/solon-ai/spring-ai-alibaba/mimo-code/omnigent）+ `2026-06-06-nop-ai-agent-architecture-comparison.md` + `2026-07-23-nop-ai-architecture-governance.md` 结论——相对 LangGraph（Python、图 DSL）、Agno（Python SDK+平台），nop-ai 的差异化是**与 Java 平台深度集成**：Agent 引擎复用 nop-core-framework 的 IoC/配置/事务、`nop-ai-api` 作为契约被 nop-wf 等业务模块消费、XDSL 驱动 Prompt/skill 配置。可靠性锚点（AIREL-001：三入口专用 executor、LLM/工具 wall-clock 超时、租约续期防 double-execution）是生产级 Java agent 的工程化体现。

### 5.6 规则：Drools vs nop-rule（新增调研）

- **竞品做什么**：Drools 是重量级 BRMS（Business Rule Management System），forward+backward chaining 推理、Phreak 高性能算法、决策表、DRL/KIE 模型、复杂事件处理。（来源：[Drools docs](https://docs.drools.org/8.38.0.Final/drools-docs/docs-website/drools/rule-engine/index.html)、[Baeldung Drools](https://www.baeldung.com/drools)，访问 2026-07-24）
- **nop-rule 做什么**：决策树 + 决策矩阵、`RuleModel` 运行期索引构建（MODEL-INIT-003 `RuleModel.init()`）、版本管理、`nop-rule-api` 契约。
- **差异点**：nop-rule **故意轻量**——不做 Drools 式的 RETE/Phreak 推理引擎，而是把规则作为平台 XDSL 模型（决策树/矩阵是结构化数据，非推理程序）。差异化：规则定义可走 Delta 定制、与 ORM/GraphQL/表单一体化（规则结果可直接绑定业务实体），适合「业务人员可配置的决策逻辑」场景，不适合「海量事实的复杂推理」场景。这是明确的**功能取舍而非差距**。

### 5.7 报表：JasperReports / FineReport vs nop-report（新增调研）

- **竞品做什么**：JasperReports 是成熟的开源 Java 报表引擎（JRXML 模型、JasperStudio 设计器、PDF/HTML/Excel 导出）；FineReport 是拖拽设计器、可视化优先的商业报表。（来源：[FineReport vs JasperReports](https://www.fanruan.com/en/blog/best-java-reporting-tool)、[Baeldung Java reporting tools](https://www.baeldung.com/java-reporting-tools-comparison)，访问 2026-07-24）
- **nop-report 做什么**：XPT 单元格展开模型（批注→模型 `ExcelToXptModelTransformer`，REPORT-002）、套打机制（`ExcelImage.print=false`，REPORT-003）、Excel/PDF/HTML/DOCX 多渲染器共享展开逻辑（REPORT-001）。
- **差异点**：nop-report 的 XPT 模型**用 Excel 批注驱动**（设计师就是 Excel 本身，批注即模型指令 `*=^ds!field`），而非独立 JRXML/设计器。差异化：报表模板是平台资源（经 VFS/Delta 管理，可被应用 Delta 覆盖），数据源直接来自 ORM/EQL，与 GraphQL/API 一体化。代价：缺独立可视化设计器（依赖 Excel）；收益：模板即代码、可 Delta 定制、无设计器/运行时二分。

## 6. 准确性终检（事实性论断 ↔ 源码/文档核对）

| 论断 | 证据 | 结果 |
|------|------|------|
| `ModuleManager.discover()` 扫描 `*/*/_module` | `ModuleManager.java:70` `discover()`、`:74` `findAll("*/*/_module")` | PASS |
| `getEnabledModules()` 供消费者遍历 | `ModuleManager.java:165` | PASS |
| `module:` VFS 名字空间 | `ModuleNamespaceHandler.java:17` | PASS |
| 模块可独立启用/禁用 | `CoreConfigs.java:184/187` `CFG_MODULE_ENABLED/DISABLED_MODULE_NAMES` | PASS |
| 工作流 base 模板 `oa.xwf` 存在 | `nop-wf/nop-wf-core/.../nop/wf/base/oa.xwf` | PASS |
| `WorkflowEngineImpl` vote-group/doReject | `WorkflowEngineImpl.java:1115` `doInvokeAction`、`:1137` `doReject` | PASS |
| batch DSL schema 存在 | `nop/schema/task/batch.xdef` | PASS |
| 元数据聚合执行器存在 | `MetaAggregationExecutor.java`（META-001） | PASS |
| 列级血缘抽取器存在 | `SqlColumnLineageExtractor.java`（META-004） | PASS |
| 认证配置类存在 | `AuthFilterConfig.java`（AUTH-002） | PASS |
| CodeRule 契约 `generate` 入口 | `ICodeRuleGenerator.java:11`（CODE-001） | PASS |
| 序列号引擎存在 | `SysSequenceGenerator.java`（CODE-002） | PASS |
| sys-event 可靠性实现 | `NonBroadcastEventProcessor.java`（SYS-001） | PASS |
| 字段级审计拦截器 | `OrmEntityChangeLogInterceptor.java`（AUDIT-001） | PASS |
| 报表引擎入口 | `IReportEngine.java:31/37`（REPORT-001） | PASS |
| RPC 核心接口 | `IRpcService.java:33` `callAsync`（RPC-001） | PASS |
| 短信外发接口 | `ISmsSender.java`（INT-001） | PASS |
| GraphQL operation 分隔符 | `GraphQLConstants.java:96` `OBJ_ACTION_SEPARATOR="__"`（GQL-008） | PASS |

**依赖矩阵计数核对**：`*-api` 入站消费者数由 Python 脚本对全部 `pom.xml`（排除 self pom + `nop-bom`）统计，结论与 plan 初查假设一致（`nop-auth-api`/`nop-sys-api` 外部消费者为 0，被广泛依赖的是 `nop-http-api`/`nop-rpc-api` 等）。

## Conclusion

- nop-entropy 的业务模块生态**不是一组独立产品的集合**，而是一套以「统一 model-first 骨架 + 可插拔 `_module` 发现 + 跨模块 `-api` 契约分层 + XDSL/Delta 统一定制」为组织主线的**一致性模块矩阵**。
- 依赖矩阵实证：**基础设施型 `-api`（http/rpc/search/biz-auth/integration/job）横切全平台**；**领域型 `-api` 多为模块内分层**；**`nop-auth-api`/`nop-sys-api` 等横切能力经运行期 filter/拦截器/`_module` 到达应用而非 Maven 制品**——这是 nop 的设计选择（横切能力走运行期织入）。
- 竞品对标（7 领域）：nop 的差异化**不在单模块功能覆盖率**——在多数领域（调度/流/规则）它甚至**故意轻量/功能取舍**；差异化在三个横向一致性：**一套 XDSL/Delta 统管所有领域 DSL**、**model-first 统一骨架**、**可逆计算定制跨模块扩展**。这是单点竞品（Flowable/PowerJob/Flink/DataHub/LangGraph/Drools/JasperReports）不具备的。
- 被否决的定位叙述：「nop 是工作流/调度/流/元数据/AI 的全能平台」——**不准确**。准确叙述是：nop 提供这些领域的**平台原生实现**（与骨架一体化、可 Delta 定制），在需要与平台深度集成时优于独立竞品；在需要单领域极致能力（大规模 Flink 集群、Drools 海量推理、PowerJob DAG）时应集成外部专项系统。
- 后续工作：本分析为 A7 capstone 提供「业务模块矩阵」层面参照。A7 汇总 A1–A6 决定是否将模块矩阵迁移到 `docs-for-ai/`。

## Open Questions

- [ ] `nop-graph` 缺独立 `03-modules/` 专题（当前是内部算法库），是否值得补一份专题文档待 A7 综合评估。
- [ ] `nop-job`/`nop-task` 无专属 source-anchor 编号（MOD/AUTH/WF/BATCH 等系列未覆盖），定位依赖 `03-modules/` + 既有对比分析；是否补 anchor 待文档治理决策。
- [ ] `nop-stream-api`/`nop-batch-api`/`nop-dyn-api`/`nop-file-api`/`nop-report-api`/`nop-task-api` 外部消费者为 0：确认这是「领域自包含」设计而非「契约未被复用」的疏漏（初步判断为前者，这些模块经 `-web` 制品整体集成而非 `-api` 被他人消费）。
- [ ] `2026-07-15-nop-orm-model-management-and-bi-metadata-analysis.md` 的「元数据/血缘/质量差距」结论已被 nop-metadata 模块（META-001~005）部分补齐，该历史分析是否需补「现状更新」注记（属独立文档维护任务，不在本 plan 修复）。

## References

### 平台内部（source-anchors + 模块文档）
- `docs-for-ai/01-repo-map/module-groups.md` — 仓库模块分组
- `docs-for-ai/03-modules/reusable-modules-overview.md` — 可复用业务模块总览
- `docs-for-ai/03-modules/nop-{auth,sys,wf,task,job,batch,report,rule,dyn,file,retry,tcc,metadata,code,ai}.md` — 各模块专题
- `docs-for-ai/04-reference/source-anchors.md` — MOD-001~005 / AUTH-001~003 / WF-001~006 / BATCH-001~002 / REPORT-001~003 / CODE-001~004 / SYS-001 / AUDIT-001~002 / META-001~005 / RPC-001~008 / INT-001~002 / GQL-008 / TNT-001~008
- `ai-dev/analysis/2026-07/2026-07-24-nop-theory-foundation.md`（A1）、`2026-07-24-nop-core-engine-deep-dive.md`（A2）、`2026-07-24-nop-model-driven-and-codegen.md`（A3）、`2026-07-24-nop-graphql-service-frontend.md`（A4）— 上游分析

### 既有专题对比（复用）
- 工作流：`ai-dev/analysis/2026-07-02-flowlong-warmflow-vs-nop-wf-comparison.md`、`2026-07-02-flowlong-warmflow-dingflow-json-format-comparison.md`、`2026-06-27-nop-wf-example-files-audit.md`
- 调度/任务：`ai-dev/analysis/2026-05-17-snail-job-vs-nop-job-comparison.md`、`2026-05-18a-powerjob-vs-nop-job-features.md`、`2026-05-18b-powerjob-vs-nop-job-fault-tolerance.md`、`2026-05-18-juggle-vs-nop-task-comparison.md`
- 流：`ai-dev/analysis/nop-stream-flink-comparison-deep-dive.md`、`2026-06-14-nop-stream-barrier-checkpoint-comparison.md`、`2026-07-20-nop-stream-dataflow-api-gap-analysis.md`
- 元数据/BI：`ai-dev/analysis/metadata-survey/`（README + DataHub/OpenMetadata/Atlas/Amundsen/Marquez/dbt/GE/Griffin 深度分析）、`2026-07-15-superset-vs-nop-bi-analysis.md`、`2026-07-15-nop-orm-model-management-and-bi-metadata-analysis.md`、`2026-07-17-data-quality-tools-comparison.md`
- AI：`ai-dev/analysis/agent-survey/`（40+ 份）、`2026-06-06-nop-ai-agent-architecture-comparison.md`、`agent-survey/2026-06-13-agno-vs-goal-driver-vs-nop-agent-survey.md`、`2026-07-23-nop-ai-architecture-governance.md`
- 代码索引：`ai-dev/analysis/2026-05-25-code-review-graph-vs-nop-code.md`、`2026-06-05-codegraph-vs-nop-code-deep-analysis.md`

### 外部联网调研（访问日期 2026-07-24）
- 工作流：https://www.g2.com/compare/camunda-vs-flowable-platform ；https://medium.com/version-1/camunda-and-flowable-process-and-workflow-automation-platforms-bf4fae4f00ed
- AI 编排：https://docs.langchain.com/oss/python/langgraph/overview ；https://www.langchain.com/langgraph
- 规则：https://docs.drools.org/8.38.0.Final/drools-docs/docs-website/drools/rule-engine/index.html ；https://www.baeldung.com/drools
- 报表：https://www.fanruan.com/en/blog/best-java-reporting-tool ；https://www.baeldung.com/java-reporting-tools-comparison
