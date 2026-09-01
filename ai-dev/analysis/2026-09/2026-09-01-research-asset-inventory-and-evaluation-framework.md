# 调研资产盘点与产品化评估框架（roadmap item 1）

> Status: resolved
> Date: 2026-09-01
> Scope: `~/sources`（51 个参考项目，只读盘点）× `ai-dev/analysis/` 全递归 nop-stream 相关报告（27 份）× 产品化评估维度矩阵（7 维度）× 缺口清单 × items 2—4 scope 校准
> Conclusion: 资产基线 = 51 个 `~/sources` 项目（4 direct / 9 adjacent / 38 unrelated）+ 27 份 nop-stream 相关报告（含 3 份混合 scope）；产品化 7 维度矩阵（每维度 3—6 检查点 + 0—3 评分锚点 + high/medium/low 置信度）已定义并经 4 个证据源 dry-run 验证可操作；关键缺口 = SeaTunnel/Spark SS/Kafka Streams 产品化维度（seatunnel 嵌套 checkout 已存在于 `~/sources/data-integration/seatunnel`，item 2 可直接复用，是否仍顶层 clone 由其 plan 裁定）；tis 产品化证据已存在（2026-08-14d，源码级）；8 条 stale gap-analysis 行已同步收口。结论由 items 2—5 plans 接手。
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 1；plan `ai-dev/plans/nop-stream-productization/2026-09-01-0753-1-research-asset-inventory-and-evaluation-framework.md`

## Context

- nop-stream 产品化 roadmap Phase R 需要统一的产品化评估框架，避免 items 2—5 各自定义评分口径。
- 前序 mission 遗留 data-quality debt：`ai-dev/analysis/nop-stream/08-gap-analysis.md` 8 条 stale 行（G6/G9/G24/G25/G32/G45/G62/G64），其所属 stage 已 done 但行状态未同步。
- 收录规则：`ai-dev/analysis/` 下 Scope 涉及 nop-stream 或其直接子系统的报告均计入；混合 scope 计入并标注；仅顺带提及（示例/索引级）不计入。

## Phase 1 — 调研资产盘点

### 1.1 `~/sources` 项目盘点（live 计数 51，2026-09-01）

分级锚点（plan 定义）：`direct-competitor`（流/批流数据处理或数据集成引擎）、`adjacent-reference`（调度/任务/连接器/CDC 等共享子问题）、`unrelated`（与流处理产品化无直接关系）。「被引用」列指本报告 §1.2 索引的 27 份 nop-stream 相关报告；括号注明其他 ai-dev 报告的引用情况。

| # | 项目 | 一句话定位 | 分级 | 被引用 |
|---|------|-----------|------|--------|
| 1 | flink | Apache Flink 分布式流处理引擎（1.20/master 源码参照） | direct-competitor | 是（deep-dive、nop-stream/01—08、05-22(b)、06-14 等 10+ 份） |
| 2 | beam | Apache Beam 统一批流编程模型 | direct-competitor | 是（2026-05-23） |
| 3 | tis | TIS 数据集成平台（DataX 连接器生态 + Flink 执行底座） | direct-competitor | 是（2026-08-14d 源码级） |
| 4 | data-platform-open | 可视化拖拽式大数据集成平台后端（flow/query/web 多模块） | direct-competitor（边界，见 1.1.1-B1） | 否 |
| 5 | data-integration | 多项目集合：airbyte / meltano / nifi / **seatunnel** | adjacent-reference（边界，见 1.1.1-B2） | seatunnel 子目录是（2026-05-19a、2026-06-14 提及 Zeta） |
| 6 | data-quality | 数据质量工具集合：great-expectations / griffin / OpenRefine | adjacent-reference（数据质量共享子问题） | griffin 是（metadata-survey/2026-07-15 对比） |
| 7 | metadata | 元数据治理集合：amundsen / atlas / datahub / dbt / marquez | adjacent-reference（元数据/血缘共享子问题） | 间接（metadata-survey 系列对比，非 nop-stream 直接对标） |
| 8 | open-cdm | CloudDM 团队数据库管理工具（权限/脱敏/SQL 审计） | adjacent-reference（连接器/数据管理的管理面参照） | 否 |
| 9 | olap | OLAP 引擎集合：ClickHouse / doris / druid / starrocks | adjacent-reference（流处理下游存储/分析；druid 具流摄入） | 否 |
| 10 | PowerJob | 分布式任务调度与计算框架 | adjacent-reference | 否（被 nop-job 系列 analysis 引用，非 nop-stream 报告） |
| 11 | snail-job | 分布式任务调度 | adjacent-reference | 否（同上，2026-05-17/18 系列） |
| 12 | Sundial | 分布式调度框架 | adjacent-reference | 否（nop-job 佐证） |
| 13 | Juggle | 流程编排引擎（任务编排共享子问题） | adjacent-reference | 否（2026-05-18 juggle-vs-nop-task） |
| 14 | ai-data | AI 数据工具集合：DataMind / langchain / llamaindex / pandas-ai | unrelated（AI/LLM 数据分析域） | 否 |
| 15 | amis | 前端低代码框架（JSON 配置渲染） | unrelated（前端 UI） | 否 |
| 16 | ant-design | React UI 组件库 | unrelated（前端 UI） | 否 |
| 17 | bi | BI/报表集合：aj-report / calcite / cube / dataease 等 | unrelated（BI 领域；见 1.1.1-B6） | 否（被 nop-bi 分析引用） |
| 18 | c-shopping-rn | React Native 商城应用 | unrelated（商城示例） | 否 |
| 19 | Chat2DB | AI 数据库客户端 | unrelated（数据库工具/AI） | 否 |
| 20 | cobol | COBOL 现代化/记录文件解析工具集合 | unrelated（记录文件解析域，nop-record 参照） | 否 |
| 21 | complex-controls | 前端复杂控件集合 | unrelated（前端 UI） | 否 |
| 22 | eclipse.jdt.ls | Eclipse JDT Language Server | unrelated（LSP 工具链） | 否 |
| 23 | erp | ERP 应用 | unrelated（plan 锚点示例） | 否 |
| 24 | flowlong | 工作流（审批流）引擎 | unrelated（见 1.1.1-B7） | 否（2026-07-02 flowlong 系列 nop-wf 分析） |
| 25 | flowlong-designer | flowlong 流程设计器 | unrelated（同上） | 否 |
| 26 | formily | 前端表单方案 | unrelated（前端 UI） | 否 |
| 27 | fortress-json | 序列化基础库（Apache） | unrelated（基础序列化，平台工程参照） | 否 |
| 28 | fory | Apache Fory 高性能序列化 | unrelated（同上） | 否 |
| 29 | graal | GraalVM（native image） | unrelated（平台工程参照） | 否 |
| 30 | industrial-hmi-research | 工业 HMI/图形库集合（fabric.js / FUXA / konva / leafer） | unrelated（工业 HMI/前端图形） | 否 |
| 31 | java-language-server | Java LSP 实现 | unrelated（LSP 工具链） | 否 |
| 32 | litemall | 商城应用 | unrelated（plan 锚点示例 mall） | 否 |
| 33 | magic-api | 低代码 API 快速开发框架（DB→HTTP 接口） | unrelated（见 1.1.1-B8） | 否 |
| 34 | maven | Maven 构建系统 | unrelated（构建工具） | 否 |
| 35 | newbee-mall-vue3-app | 商城前端 | unrelated（mall） | 否 |
| 36 | nocobase | 低代码平台 | unrelated（低代码平台域） | 否 |
| 37 | primereact | React UI 组件库 | unrelated（前端 UI） | 否 |
| 38 | query | TanStack Query（前端数据请求） | unrelated（前端） | 否 |
| 39 | react-doctor | React 诊断工具 | unrelated（前端） | 否 |
| 40 | smart-flow | AI 对话/流程客户端工具 | unrelated（AI flow 工具） | 否 |
| 41 | spring-boot | Spring Boot | unrelated（基础框架） | 否 |
| 42 | spring-framework | Spring Framework | unrelated（基础框架） | 否 |
| 43 | tanstack-table | React 表格库 | unrelated（前端 UI） | 否 |
| 44 | tiny-engine | 低代码引擎（前端） | unrelated（低代码/前端） | 否 |
| 45 | ui | 前端 UI 集合 | unrelated（前端 UI） | 否 |
| 46 | ui-predicate | 前端条件过滤组件 | unrelated（前端 UI） | 否 |
| 47 | vant | Vue 移动端 UI 库 | unrelated（前端 UI） | 否 |
| 48 | vtable | 高性能表格组件 | unrelated（前端 UI） | 否 |
| 49 | x-sheet | 前端电子表格 | unrelated（前端 UI） | 否 |
| 50 | xyflow | 流程图/节点图库（React） | unrelated（前端 UI） | 否 |
| 51 | yudao-mall-uniapp | 商城 uniapp 前端 | unrelated（mall） | 否 |

汇总：direct-competitor 4 项（#1—#4）、adjacent-reference 9 项（#5—#13）、unrelated 38 项。

#### 1.1.1 边界案例裁定

- **B1 data-platform-open → direct-competitor**：定位为「可视化拖拽式大数据集成平台」，与 tis/SeaTunnel 同属数据集成产品域；虽成熟度与生态规模显著更低，但按「数据集成平台」口径计入 direct，产品化对比价值集中在 UI 编排/部署形态。若按「引擎」严格口径可降为 adjacent，此处取产品域一致性。
- **B2 data-integration → adjacent-reference（按 plan 锚点）但含 direct 级资产**：该目录是多项目聚合目录（airbyte/meltano/nifi/seatunnel），按 plan 分级锚点以聚合目录计 adjacent。**关键发现**：`~/sources/data-integration/seatunnel` 是完整的 SeaTunnel checkout（git describe `v2.0.4-4366-g51b690461`，2026-09-01 live 核实），roadmap「无 seatunnel」的 baseline 仅指顶层 `~/sources/seatunnel` 不存在。item 2 可直接复用该嵌套 checkout，是否仍按 plan 执行顶层 shallow clone 由 item 2 的 plan 裁定（clone 的价值在于固定 commit SHA 可复现性，见 plan 2 的 SHA 落库要求）。
- **B3 olap → adjacent-reference**：OLAP 引擎是流处理的下游存储/分析端（sink 生态与性能维度参照），非直接竞品；druid 具备实时摄入，证据价值最高。
- **B4 open-cdm → adjacent-reference**：CloudDM 实为数据库管理工具（非 CDC 引擎），但数据库访问/审计/脱敏是连接器生态的管理面共享子问题，按 plan 锚点计 adjacent。
- **B5 bi → unrelated**：BI/报表是与流处理产品化不同的领域（被 nop-bi 分析引用），与 nop-stream 7 个产品化维度无直接映射。
- **B6（并入 B5）**
- **B7 flowlong/flowlong-designer → unrelated**：审批流引擎，被 nop-wf 分析引用；工作流不属于流处理的调度/任务/连接器/CDC 共享子问题（与 PowerJob/snail-job 的任务调度不同）。
- **B8 magic-api → unrelated**：低代码 API 框架（DB→HTTP），非数据集成/流处理引擎。

另注：Hazelcast Jet 被 2026-05-23 报告对比但 `~/sources` 无其源码（beam 有），其维度证据置信度上限 medium。

### 1.2 既有分析报告索引（全递归扫描 27 份，2026-09-01 live 核实）

维度缩写：API=API/DX，CONN=连接器生态，DEPL=部署形态，OPS=运维监控，FT=容错语义，PERF=性能，DOC=文档。「混合」= Scope 同时覆盖 nop-stream 与其他模块。

| # | 路径 | Status | Scope 要点 | 覆盖竞品 × 维度 |
|---|------|--------|-----------|----------------|
| 1 | `ai-dev/analysis/2026-04-02-nop-stream-design-review.md` | obsolete | nop-stream 全子模块设计快照（历史） | 内部审计：代码质量（API/DOC 间接）；结论部分被后续 plan 推翻 |
| 2 | `ai-dev/analysis/2026-04-02-nop-stream-review.md` | 未标注（历史快照） | nop-stream 全子模块综合分析 | 内部审计：设计/实现/性能瓶颈（API/PERF 间接） |
| 3 | `ai-dev/analysis/2026-05-19a-seatunnel-vs-nop-stream-comparison.md` | open | SeaTunnel vs nop-stream（+nop-batch） | SeaTunnel × 架构/功能（API 弱、FT 部分）；**架构/功能视角，产品化维度未覆盖** |
| 4 | `ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md` | resolved | nop-stream 10 子模块重复/废弃代码审计 | 内部审计：代码质量（Phase M 输入） |
| 5 | `ai-dev/analysis/2026-05-22-test-coverage-comparison-flink.md` | open（混合：全项目，重点 nop-stream） | 测试覆盖 vs Flink | Flink × 测试覆盖（DOC/API 间接） |
| 6 | `ai-dev/analysis/2026-05-22b-nop-stream-vs-flink-streaming-test-comparison.md` | open | nop-stream 全模块 vs flink-streaming-java 测试 | Flink × 测试覆盖（DOC 间接） |
| 7 | `ai-dev/analysis/2026-05-23-nop-stream-beam-hazelcast-comparison.md` | resolved | Beam/Hazelcast Jet vs nop-stream 分布式执行/checkpoint/exactly-once/状态调度 | Beam、Hazelcast Jet × FT/DEPL/API（合并评述，未拆分） |
| 8 | `ai-dev/analysis/2026-06-14-nop-stream-barrier-checkpoint-comparison.md` | open（标注 analysis） | Flink/SeaTunnel(Zeta) vs nop-stream barrier/checkpoint 容错场景 | Flink、SeaTunnel(Zeta) × FT |
| 9 | `ai-dev/analysis/2026-06-30-nop-stream-code-audit.md` | 未标注（历史快照） | nop-stream 9 子模块代码深度审计 | 内部审计：代码质量/测试覆盖/端到端 |
| 10 | `ai-dev/analysis/nop-stream-flink-comparison-deep-dive.md` | 未标注（历史） | Flink(master) vs nop-stream 架构对比与低成本设计迁移 | Flink × API/FT/架构（设计迁移视角） |
| 11 | `ai-dev/analysis/checkpoint-module-extraction.md` | 未标注（历史） | nop-stream-checkpoint 模块提取可行性 | 内部：架构/模块划分 |
| 12 | `ai-dev/analysis/distributed-exactly-once-design-amendment.md` | active amendment（历史修订） | nop-stream 分布式 exactly-once 设计补充 | 内部：FT（exactly-once） |
| 13 | `ai-dev/analysis/nop-stream/01-flink-source-audit.md` | open | Flink 1.20.0 六大包源码结构审计 | Flink × API/FT/窗口/CEP/分布式（结构参照） |
| 14 | `ai-dev/analysis/nop-stream/02-nopstream-live-audit.md` | open | nop-stream 现有实现审计（core/runtime/cep/connector/batch/debezium） | 内部：全维度实现基线 |
| 15 | `ai-dev/analysis/nop-stream/03-checkpoint-comparison.md` | resolved | Checkpoint & Barrier 源码级对比 | Flink × FT（源码级） |
| 16 | `ai-dev/analysis/nop-stream/04-state-comparison.md` | 未标注（正文 resolved 级证据） | 状态管理/状态后端源码级对比 | Flink × FT/状态（源码级） |
| 17 | `ai-dev/analysis/nop-stream/05-window-comparison.md` | resolved | 窗口机制/时间模型源码级对比 | Flink × API/FT（源码级） |
| 18 | `ai-dev/analysis/nop-stream/06-cep-comparison.md` | 未标注（正文含 resolved 级证据） | CEP 引擎源码级对比 | Flink × API/FT（源码级；含 roadmap 修正） |
| 19 | `ai-dev/analysis/nop-stream/07-distributed-comparison.md` | resolved | 分布式执行模型源码级对比 | Flink × DEPL/FT（源码级） |
| 20 | `ai-dev/analysis/nop-stream/08-gap-analysis.md` | resolved | 汇总 03—07 全部发现，73 条显式缺口行 | Flink × 全维度 gap 汇总（items 3—7 输入） |
| 21 | `ai-dev/analysis/2026-07/2026-07-20-nop-stream-dataflow-api-gap-analysis.md` | resolved | DataStream API vs Flink 完整性 | Flink × API（源码级，P1 缺口 → plan 305） |
| 22 | `ai-dev/analysis/2026-08/2026-08-06-nop-stream-audit-baseline-and-roadmap-analysis.md` | resolved | nop-stream 10 子模块审计基线 + 审计路线图 | 内部：审计方法论基线（Phase M/D 输入） |
| 23 | `ai-dev/analysis/2026-08/2026-08-14d-tis-vs-nop-data-integration-comparison.md` | open（混合：tis vs nop-stream/batch/job/metadata/ai） | TIS v5.1.0 源码级数据集成能力对比 | tis × CONN/DEPL/OPS/API（源码级）；**tis 产品化证据已存在**（连接器生态与市场/部署/监控/告警/UI，含 6 项采纳建议） |
| 24 | `ai-dev/analysis/metadata-survey/2026-07-15-griffin-vs-nop-stream-comparison.md` | open | Apache Griffin 流批架构 vs nop-stream | Griffin × FT/API（流批统一视角） |
| 25 | `ai-dev/analysis/2026-07/2026-07-17-data-quality-tools-comparison.md` | open（混合：OpenRefine/GE/Griffin/dbt/PandasAI vs Nop 多模块，含 nop-stream） | 数据质量与探索工具源码对比 | Griffin 等 × FT 间接（流批统一/度量模型）、API 间接 |
| 26 | `ai-dev/analysis/2026-07/2026-07-24-nop-module-matrix.md` | resolved（混合：全平台模块矩阵，含 nop-stream 专节 §5.3） | 全平台业务模块矩阵 + 7 领域竞品对标 | Flink × DEPL/API（定位层：嵌入式流处理 vs 独立集群） |
| 27 | `ai-dev/analysis/2026-08/2026-08-23-direct-sql-usage-survey.md` | resolved（混合：全 worktree SQL 调研，含 nop-stream-connector-jdbc/runtime 条目） | 直接 SQL 使用全景调研 | 内部：CONN 局部证据（JDBC 2PC sink、checkpoint 基础设施表裁定） |

排除说明（顺带提及不计入）：`2026-07-25-opendcai-vs-age-vs-mission-driver.md`（nop-stream 仅出现在示例 prompt）、`agent-survey/2026-06-06-agent-memory-compaction-session-deep-dive.md`（仅一处 open-question 提及 nop-stream CEP）、`2026-07/2026-07-26-nop-platform-deep-introduction.md`（capstone，nop-stream 仅 2 处引用 A5 结论）、`2026-05-18-fault-tolerance-deep-dive.md`（nop-job vs snail-job）、`2026-08/2026-08-15-w1-streaming-resubscribe-spike.md`（nop-gateway AI 聊天流式，非 nop-stream 模块）、`metadata-survey/2026-07-15-apache-griffin-deep-analysis.md`（Scope 为 nop-metadata 设计参考；其 nop-stream 对比在 #24）。

计数核对：顶层 12（#1—#12）+ `nop-stream/` 子目录 8（#13—#20）+ 月份子目录 6（#21—#23、#25—#27，位于 `2026-07/`、`2026-08/`）+ 专题子目录 1（#24，`metadata-survey/`）= 27。相对 plan baseline 已知 24 份（12 顶层 + 8 子目录 + 3 月份 + 1 专题），新增 3 份月份子目录报告（#25、#26、#27，均为混合 scope，plan 执行时全递归扫描发现）。

## Phase 2 — 产品化评估维度矩阵与缺口清单

### 2.1 七维度操作化定义

每维度 3—6 个可观察检查点（评分时逐检查点取证，维度分 = 检查点整体水位，取最低主检查点而非平均——防止单点高分掩盖短板）。

#### D1 API/DX（开发体验）

1. 核心 API 完整性：流式原语（map/filter/keyBy/window/join/CEP/process、side output、watermark 传播、async IO 取舍）覆盖度与一致性
2. 声明式编排：是否存在声明式 job 定义（XDSL/SQL/YAML）及其与代码 API 的分工边界
3. 类型与序列化透明度：用户是否须手写 serializer/schema；schema 演进与兼容策略是否产品化
4. 错误与调试体验：编译期/加载期校验、fail-fast 错误码、本地调试（LocalEnvironment 级）能力
5. 脚手架与示例：quickstart 工程、示例仓库、模板生成
6. API 迁移故事：对主流引擎（Flink 等）API 的兼容层或迁移指南

#### D2 连接器生态（Connector Ecosystem）

1. Source/Sink 目录覆盖：官方连接器数量与主流数据源覆盖（关系库/MQ/文件/对象存储/CDC）
2. 契约一致性：统一 Source/Sink API 抽象（FLIP-27/TwoPhaseCommitSink 等价物）与逐连接器能力矩阵（exactly-once/at-least-once 标注）
3. CDC 产品化：offset 管理、schema 演进、initial snapshot 的开箱能力
4. 第三方扩展机制：连接器 SPI/插件机制与开发文档
5. 连接器质量保障：每连接器一致性/回归测试与版本兼容矩阵

#### D3 部署形态（Deployment）

1. 部署模式矩阵：本地嵌入式/单机/集群（多 JVM）/K8s/YARN 覆盖
2. 资源管理与调度：资源申请、slot/容器分配、rescale/autoscale
3. 依赖 footprint：库形态 vs 引擎形态（嵌入应用的体积/隔离/冲突）
4. 部署工具链：启动入口、容器镜像、Helm/Operator、部署文档
5. 多作业/多租户：单集群多作业隔离与生命周期管理

#### D4 运维监控（Ops & Observability）

1. metrics 暴露面：核心指标（吞吐/延迟/checkpoint 时长与失败率/背压/状态大小）+ 标准暴露格式（Prometheus 等）
2. 健康检查与 liveness：进程级/作业级探针、卡死（stall）检测
3. 告警与事件通知：失败/恢复事件外发通道（webhook/metrics 告警）
4. 运维操作接口：启动/停止/savepoint/恢复/rescale 的 CLI/API 与手册
5. 日志与诊断：结构化日志、troubleshooting 指南（症状→根因路径）

#### D5 容错语义（Fault Tolerance）

1. checkpoint 体系：barrier 对齐/非对齐、增量、并发 checkpoint、savepoint
2. 端到端 exactly-once：2PC/事务 sink、source 可重放
3. 故障恢复模型：failover 粒度（region/task/global）、状态恢复正确性、fencing
4. 控制面 HA：coordinator 高可用、leader election、脑裂防护
5. 语义文档化：处理语义承诺（at-least/exactly/effectively-once）与边界在文档中明确

#### D6 性能（Performance）

1. 背压行为：背压传播机制（天然/显式流控）与背压下的降级行为
2. 大状态支持：RocksDB 级状态后端、增量快照、TTL 的性能影响
3. 基准与调优：官方/第三方基准数据、调优参数指南
4. 资源效率：序列化开销、网络传输模型、内存管理
5. 长时稳定性：soak/泄漏防护证据

#### D7 文档（Documentation）

1. 用户指南完备链路：getting started → 概念 → 连接器目录 → 部署 → 调优
2. API 参考/Javadoc 质量
3. 运维手册/FAQ/troubleshooting
4. 版本化：升级/迁移指南、兼容性声明、changelog
5. 示例仓库与多语言文档

### 2.2 统一评分标准（0—3）与置信度分级

| 分值 | 锚点 | 判定语 |
|------|------|--------|
| 0 | 缺失 | 该维度无可用的产品能力（无实现或纯空壳，调用即失败） |
| 1 | 最小可用 | 核心路径可跑通，但无产品包装：无文档/无指标暴露/依赖手工步骤与专家知识 |
| 2 | 产品级 | 能力完整 + 文档 + 运维接口齐备，可用于生产环境，但有明显粗糙处（覆盖缺口或文档深度不足） |
| 3 | 竞品标杆 | 达到 Flink/SeaTunnel 等头部产品在该维度的公认水准：生态丰富、工具链成熟、有大规模生产验证 |

| 置信度 | 定义 | 标注规则 |
|--------|------|---------|
| high | 源码级证据 | 读到实现/测试/配置代码，可给出 `文件:行号` 或类名 |
| medium | 报告间接推断 | 引用既有对比报告的源码级结论（二手） |
| low | 文档或假设推断 | 仅凭官方文档、README 或业界一般认知 |

评分书写格式：`分数@置信度`（例：`3@high`）。每个被评单元格必须同时给出分数与置信度；items 2—5 报告与综合矩阵沿用本节口径，不得另行定义。

### 2.3 Dry-run 试评（4 个既有证据源）

目的：验证检查点可操作（可取证、可打分），**正式评分属 items 2—5**；下表分数为框架验证性试评，非终值。

| 维度 | flink（#10/#13—#20/#21） | beam + hazelcast（#7，合并试评） | seatunnel（#3/#8） | tis（#23） |
|------|------|------|------|------|
| API/DX | 3@high | 2@medium | 2@medium | 1@high（SQL 驱动建模 + 向导，封装度高但开放性弱） |
| CONN | 3@medium | 2@low | 3@high（connectors-v2 100+，源码结构核实） | 3@high（DataX 插件生态 + 市场） |
| DEPL | 3@medium | 2@medium（runner 可移植模型） | 2@medium（Zeta 引擎，架构视角） | 2@high（Flink/K8s/Akka 底座） |
| OPS | 3@low（语料无专项，标杆认知） | 1@low | 2@low（**产品化维度未覆盖**——item 2 必答） | 2@high（集成专用 UI/监控/告警） |
| FT | 3@high | 2@medium | 2@medium（Zeta checkpoint 有 #8 证据） | 1@medium（容错主要依赖 Flink 引擎，TIS 层薄弱） |
| PERF | 3@low（无基准报告） | 2@low | 2@low | 1@low（无专项证据） |
| DOC | 3@low（无专项报告） | 2@low | 2@low | 1@medium |

Dry-run 结论：7 维度 × 4 证据源全部可给出 `分数@置信度`，无「无法评分」的维度级放弃；低置信度单元格即 §2.4 缺口。beam 与 hazelcast 本次合并试评（证据源即合并报告），item 5 综合矩阵如需拆分须先补 hazelcast 证据（源码未下载）。

### 2.4 缺口清单（竞品 × 维度未覆盖单元格）

| 竞品 | 状态 | 未覆盖 / 低置信单元格 | 归属 |
|------|------|----------------------|------|
| seatunnel | 顶层无独立 clone；**嵌套完整 checkout 已存在**（§1.1.1-B2） | OPS/DEPL/DOC 产品包装（#3 为架构/功能视角）、CONN 产品化组织细节、PERF | item 2（必答 OPS/CONN/DEPL/API-DSL，选答 FT/PERF/DOC） |
| spark | 未下载 | 全部 7 维度 | item 3 |
| kafka streams | 未下载 | 全部 7 维度 | item 4 |
| flink | 已下载 | OPS（3@low）、DOC（3@low）、PERF（3@low）——产品化视角的运营侧证据无专项报告 | item 5 汇总时沿用 low 或由 items 2—4 顺带补证 |
| beam / hazelcast | beam 已下载；hazelcast 未下载 | beam OPS/DOC（low）；hazelcast 全维度（证据仅 #7 合并报告，≤medium） | item 5（如需拆分须补源码，否则合并标注） |
| tis | 已下载 | FT（1@medium）、PERF（1@low）低置信 | item 5（以 #23 既有证据为主，不重开源码分析） |

### 2.5 items 2—4 scope 校准结论

依据 roadmap Stage details（item 2/3/4 Deliverables）+ 本报告缺口清单：

| 竞品 | 必答维度（源自 roadmap stage details，须产出场 judgments） | 选答维度（有证据则评，无则标 low 并注明） |
|------|------|------|
| SeaTunnel（item 2） | CONN（生态组织/CDC 产品化）、API/DX（配置 DSL 与向导）、DEPL（本地/集群/K8s）、OPS（监控与运维） | FT（Zeta 已有 #8 证据，仅补产品化包装）、PERF、DOC |
| Spark SS（item 3） | API/DX（Structured Streaming API）、FT（micro-batch/continuous 双模式、状态存储与 checkpoint 产品化）、PERF（adaptive query execution）、DEPL | CONN（Spark 生态 source/sink）、OPS、DOC |
| Kafka Streams（item 4） | DEPL（库形态 vs 引擎形态——核心命题）、OPS（liveness/健康暴露、运维模型倒推）、FT（事务性 exactly-once、interactive query 的状态一致性） | API/DX、CONN（Kafka 生态绑定）、PERF、DOC |

共用规则（对 items 2—5 强制）：每单元格 `分数@置信度`（§2.2 口径）；源码级结论必须落到类/文件；items 2—4 报告中的「借鉴点 → P-REQ 候选」条目沿用 plan 2 定义的 `ST-`/`SPS-`/`KS-` 前缀。

## Conclusion

- 资产基线：51 个 `~/sources` 项目分级完成（4 direct / 9 adjacent / 38 unrelated，8 个边界案例显式裁定）；27 份 nop-stream 相关报告全递归索引完成（较 plan baseline 24 份新增 3 份混合 scope 报告）。
- 评估框架：7 维度 × 3—6 检查点 + 0—3 评分锚点 + 三级置信度已定义并 dry-run 验证（4 证据源 × 7 维度无不可评项），items 2—5 直接复用。
- 关键发现：`~/sources/data-integration/seatunnel` 已有完整 SeaTunnel checkout（v2.0.4+），item 2 的 clone 步骤可裁定为「复用嵌套 checkout + 记录 commit SHA」或「仍顶层 clone 固定 SHA」（由 plan 2 执行时裁定，不改变其 exit criteria）；tis 产品化证据已存在（#23，源码级），item 5 汇总直接消费。
- 8 条 stale gap-analysis 行已按 live repo 证据同步收口（详见 plan 1 Phase 3 与 `ai-dev/logs/2026/09-01.md`）。
- 被否决的方案：按「报告文件名含 nop-stream」机械收录（否决原因：遗漏混合 scope 报告如 #23/#25—#27，且会漏掉月份/专题子目录）；将 flink OPS/DOC/PERF 标 high（否决原因：无专项报告支撑，违反置信度定义）。
- 后续工作：items 2—4（plan `2026-09-01-0753-2`）、item 5（plan `2026-09-01-0753-3`）接手。

## References

- `ai-dev/backlog/nop-stream-productization-roadmap.md`
- `ai-dev/plans/nop-stream-productization/2026-09-01-0753-1-research-asset-inventory-and-evaluation-framework.md`
- `ai-dev/analysis/00-analysis-writing-guide.md`
- 索引表所列 27 份报告（见 §1.2）
