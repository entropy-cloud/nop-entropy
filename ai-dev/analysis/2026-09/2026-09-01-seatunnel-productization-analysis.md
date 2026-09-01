# SeaTunnel 产品化分析（roadmap item 2）

> Status: resolved
> Date: 2026-09-01
> Scope: Apache SeaTunnel master @ `5dbfb374f985349aeefde9cf84169aa98b3ac5ca`（`git -C ~/sources/seatunnel rev-parse HEAD`，`--depth 1` shallow clone，clone 于 2026-09-01）× plan 1 评估矩阵 7 维度；nop-stream 产品化 P-REQ 候选（`ST-` 前缀）
> Conclusion: SeaTunnel 在连接器生态（3@high）、部署形态（3@high）、运维监控（3@high）三个维度达到竞品标杆水位（74 个连接器模块 + 9 CDC 方言内嵌 Debezium + Helm/K8s + REST 运维全家桶 + Prometheus/OpenMetrics + Vue3 Web 控制台）；API/DX 2@high（配置声明式产品化程度高但流式算子 API 非其定位）、容错 2@medium、性能 2@high、文档 3@high。产出 10 条 `ST-` P-REQ 候选，建议归属 items 16/17/6/10。后续工作由 item 5 汇编。
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 2；plan `ai-dev/plans/nop-stream-productization/2026-09-01-0753-2-competitor-source-productization-analysis.md` Phase 1；评估矩阵 `ai-dev/analysis/2026-09/2026-09-01-research-asset-inventory-and-evaluation-framework.md` §2

## Context

- roadmap item 2 要求获取 SeaTunnel 源码并按 plan 1 矩阵做**产品化视角**分析；架构/功能对比已由 `ai-dev/analysis/2026-05-19a-seatunnel-vs-nop-stream-comparison.md` 覆盖，本报告**引用不重做**（见 §与既有报告的关系）。
- 本报告所有源码证据锚定上述 commit SHA（shallow clone 无法事后 checkout 其他 commit；上游 HEAD 每日漂移，复现以 SHA 为准）。
- 分析对象为 master HEAD（SeaTunnel 3.x 线），与 05-19a 报告基线（嵌套 checkout `v2.0.4-4366-g51b690461`）存在版本差，差异点在 §与既有报告的关系显式记录。

## 与既有报告的关系（架构结论引用，不重做）

- 架构结论（模块结构、Source/Transform/Sink 三大抽象、Zeta 引擎 Master/Worker、HOCON 管道模型）直接引用 `2026-05-19a-seatunnel-vs-nop-stream-comparison.md` 第一部分，本报告不重复展开。
- 冲突/差异核对（逐项）：
  - 05-19a §2.1「执行引擎选择：Zeta / Flink / Spark」：在 live SHA 下仍成立，但组织方式已演进为**多版本子模块**（`seatunnel-translation/seatunnel-translation-flink/seatunnel-translation-flink-{13,15,20}`、`seatunnel-translation-flink-common`），且 Zeta 是默认且主力引擎（`seatunnel-starter`）。非结论性冲突，属结构演进。
  - 05-19a §2.1「CDC 支持：MongoDB-CDC / MySQL-CDC」：live SHA 下 CDC 已扩为 9 方言（见 §D2.2），05-19a 结论欠完整但不冲突。
  - 未发现方向性冲突（两报告对「SeaTunnel = 连接器生态 + 分布式执行 + exactly-once 的数据集成平台」定位一致）。
- 05-19a 的 Status 收敛（open → resolved + 指向本报告）按 plan 2 Non-Blocking Follow-ups 归属 item 5 汇编时处理。

## 评分总表（plan 1 矩阵 §2.2 口径：分数@置信度）

| 维度 | 评分 | 一句话判定 |
|------|------|-----------|
| D1 API/DX | 2@high | 配置声明式（HOCON 模板/校验/加密/dry-run/option-rules）达产品标杆；但流式算子核心 API（窗口/CEP）缺失——取最低主检查点 |
| D2 连接器生态 | 3@high | 74 连接器模块 + CDC 9 方言 + SPI + 316 份连接器文档 + 78 e2e 模块 |
| D3 部署形态 | 3@high | local/cluster 双 master 类型 + Helm chart + docker/k8s getting-started + 多引擎 starter |
| D4 运维监控 | 3@high | 30+ REST 端点（作业/日志/线程/checkpoint/metrics）+ Prometheus/OpenMetrics + Vue3 Web 控制台 |
| D5 容错语义 | 2@medium | Zeta checkpoint（barrier 对齐）+ 存储插件（hdfs/local-file）+ savepoint/restore；深度弱于 Flink（无 region failover/非对齐） |
| D6 性能 | 2@high | benchmarks 模块 + Zeta tuning-guide 存在；无公开基准数据集 |
| D7 文档 | 3@high | en/zh 双语 + getting-started（local/docker/k8s/recipes）+ 316 连接器文档 + CDC cookbook + FAQ |

## Analysis

### D1 API/DX（必答子项：配置 DSL 与向导）— 2@high

检查点证据：

1. 声明式编排（强项）：HOCON 管道配置（`config/v2.streaming.conf.template`、`config/v2.batch.config.template` 模板开箱）；解析层为 shaded typesafe config（`seatunnel-config/seatunnel-config-shade/src/main/java/org/apache/seatunnel/shade/com/typesafe/config/impl/ConfigParser.java`）。
2. 配置校验与向导（强项）：
   - `OptionRule` 逐连接器选项校验（`seatunnel-api/src/main/java/org/apache/seatunnel/api/configuration/util/OptionRule.java` + `Options.java`）
   - 配置校验 CLI：`seatunnel-core/seatunnel-starter/.../command/SeaTunnelConfValidateCommand.java`
   - dry-run 连通性检查：`ConnectorCheckCommand.java` + `DryRunConnectValidator.java`；dry-run 能力接口化（`seatunnel-api/.../table/factory/SupportSourceDryRunValidation.java`、`SupportSinkDryRunValidation.java`）
   - 连接器选项自描述 REST：`/option-rules`（`rest/servlet/OptionRulesServlet.java`）——向导/UI 生成配置表单的数据源
   - 凭据加密：`/encrypt-config`（`EncryptConfigServlet.java`）
   - AI 辅助：`docs/en/ai-cli/`（AI CLI 工具文档目录）
3. SQL Transform：基于 Calcite 的 SQL 引擎 + UDF（`seatunnel-transforms-v2/src/main/java/.../calcite/CalciteSQLEngine.java`、`calcite/udf/BuiltinFunctions.java`，含向量距离等 20+ 内置函数）
4. 核心 API 完整性（短板，取最低主检查点）：流式原语（窗口/CEP/多流 join 算子 API）缺失——引用 05-19a §2.1（窗口 ❌、CEP ❌、SQL 聚合走批式语义）；SeaTunnel 定位数据集成 ETL 而非通用流计算。
5. 示例：`seatunnel-examples/`（engine/flink/spark 三组示例工程）。

结论：作为**数据集成配置 DSL** 是竞品标杆（模板+校验+dry-run+自描述+加密+AI 辅助全链路）；作为**通用流处理 API** 不完整。综合按矩阵「取最低主检查点」记 2@high。

### D2 连接器生态（必答子项：连接器生态组织方式、CDC 产品化、多引擎适配层 Source/Sink API 抽象）— 3@high

#### D2.1 生态组织方式

- 74 个连接器 Maven 模块（`seatunnel-connectors-v2/` 下 77 个条目除去 pom.xml 与两份 README；覆盖 Kafka/JDBC/ClickHouse/Doris/Iceberg/Paimon/Hudi/Redis/MongoDB/HTTP/Email/Slack/Web3j 等）
- SPI 与装配：连接器以 `factoryIdentifier` 标识（`seatunnel-api/.../table/factory/Factory.java`），`plugin-mapping.properties` 维护「插件名 → artifactId」映射供类加载器定位 jar
- 公共基座：`connector-common`（公共错误码/工具）+ `seatunnel-formats`（序列化格式独立模块：json/csv/parquet/avro/orc…）
- 质量保障：`seatunnel-e2e/seatunnel-connector-v2-e2e/` 下 78 个 e2e 模块（每连接器独立 testcontainers IT，如 `connector-jdbc-e2e-part-7/JdbcPrestoIT.java`）
- 文档：`docs/en/connectors/` 316 份 md（source/sink 分目录 + `common-options/` + `formats/` + `connector-faq.md` + `connector-isolated-dependency.md` 依赖隔离说明）

#### D2.2 CDC 产品化

- CDC 家族：`connector-cdc/` 伞模块 = `connector-cdc-base` + 9 方言（mysql/mongodb/postgres/oracle/sqlserver/db2/opengauss/tidb/vitess）
- 技术底座：内嵌 Debezium Engine（`connector-cdc/pom.xml:46` `debezium.version=1.9.8.Final`；base 模块含 `io/debezium/connector/base`、heartbeat 等补丁类）
- 开箱能力：`IncrementalSource`（initial snapshot + incremental 流式读取统一入口，`connector-cdc-base/.../base/source/IncrementalSource.java`）、offset 随 checkpoint 持久化
- Schema 演进：一等公民 API（`base/schema/SchemaChangeResolver.java`、`SchemaChangeEventFilter.java`、`SchemaChangeEventType.java`）
- CDC 亦为 factory 层一等概念：`seatunnel-api/.../table/factory/ChangeStreamTableSourceFactory.java`（+ checkpoint/state 配套类）
- 生产文档：`docs/en/connectors/cdc-production-cookbook.md`（生产化 cookbook 专文）

#### D2.3 多引擎适配层（Source/Sink API 抽象）

- 引擎无关 API 层：`seatunnel-api`（Source/Sink/Transform + 类型系统 + Catalog：`api/table/catalog/Catalog.java`）
- 翻译层：`seatunnel-translation/` = base + flink + spark；flink 侧按版本拆分（`seatunnel-translation-flink-{13,15,20}` + `-common`，如 `flink-20/.../sink/FlinkSink.java`、`FlinkCommitter.java`——SeaTunnel Sink 契约到 Flink Sink/Committer 契约的适配）
- 启动器：`seatunnel-core/` = `seatunnel-starter`（Zeta）+ `seatunnel-flink-starter` + `seatunnel-spark-starter` + `seatunnel-core-starter`（公共参数/命令框架）
- 对 nop-stream 的含义：nop-stream 的 FLIP-27 Source / 2PC Sink 抽象已对齐该分层思想（引擎无关 API + 引擎适配），差异在连接器**数量与目录化组织**（见 P-REQ ST-1/ST-2）

### D3 部署形态（必答子项：本地/集群/K8s）— 3@high

- 本地/集群：Zeta 引擎提交目标二态（`seatunnel-core/seatunnel-starter/.../args/ClientCommandArgs.java:68`「support [local, cluster]」，`MasterType.LOCAL/CLUSTER`；local 模式单 JVM 跑完整引擎）
- 集群拓扑：master/worker/client 三角色 hazelcast 配置（`config/hazelcast-master.yaml`、`hazelcast-worker.yaml`、`hazelcast-client.yaml`）+ 分角色 JVM 参数文件（`config/jvm_master_options` 等）
- K8s：Helm chart（`deploy/kubernetes/seatunnel/Chart.yaml` + `values.yaml` + templates：master/worker Deployment、headless Service、ingress、rbac、configmap；配套 `helm-tests/test-chart.sh`）
- 容器化文档：`docs/en/getting-started/docker/`、`docs/en/getting-started/kubernetes/`
- 资源管理：slot 服务（`config/seatunnel.yaml` `slot-service.dynamic-slot: true` 动态 slot）
- 交付物：`seatunnel-dist` 打包模块 + `bin/install-plugin.sh`（按需安装连接器插件）

### D4 运维监控（必答子项：监控与运维）— 3@high

- REST 运维面（Jetty 内嵌，`config/seatunnel.yaml` `http.enable-http/port: 8080`；端点清单 `engine-server/.../rest/RestConstant.java`，30+ 端点）：
  - 作业生命周期：`/submit-job`、`/submit-jobs`、`/submit-job/upload`、`/stop-job`、`/stop-jobs`、`/running-jobs(/summary)`、`/finished-jobs`、`/pending-jobs`、`/job-info`、`/update-tags`
  - 诊断：`/thread-dump`、`/running-threads`、`/system-monitoring-information`、`/resource/workers`
  - 日志：`/logs`、`/log`、`/get-all-log-name`、`/loggers`（远端日志检索与级别调整，`rest/service/LogService.java`、`LoggerLevelService.java`）
  - checkpoint 观测：`/jobs/checkpoints`、`/jobs/checkpoints/history`（`CheckpointOverviewServlet.java`、`CheckpointHistoryServlet.java` + `CheckpointMonitorRestService.java`）
  - 实时指标：`/metrics/realtime`
  - 安全：Basic Auth（`rest/filter/BasicAuthFilter.java`，seatunnel.yaml `enable-basic-auth`）
- metrics 标准暴露：`/metrics`（Prometheus TextFormat 004）+ `/openmetrics`（OpenMetrics 100）（`rest/servlet/MetricsServlet.java:24-26,43-44`，`io.prometheus.client`）；指标族 `engine-server/.../telemetry/metrics/exports/`：`ClusterMetricExports`、`JobMetricExports`、`NodeMetricExports`、`JobThreadPoolStatusExports`、`EngineStateStoreMetricExports`；开关 `seatunnel.yaml telemetry.metric.enabled`
- Web 控制台：`seatunnel-engine/seatunnel-engine-ui/`（Vue3 + Naive UI + AntV X6 DAG 图 + echarts，views：`jobs/`、`managers/`、`overview/`；自带 vitest 单测 + cypress e2e）
- 生命周期治理：历史作业过期（`seatunnel.yaml history-job-expire-minutes: 1440`）、日志定时清理（`logs.scheduled-deletion-enable: true`）、执行信息周期打印（`print-execution-info-interval`）
- 链路追踪：`seatunnel-trace/` 模块 + `/trace/task-mapping` REST

### D5 容错语义（选答）— 2@medium

- checkpoint 存储：插件化（`seatunnel-engine/seatunnel-engine-storage/checkpoint-storage-plugins/`：hdfs、local-file；API 层 `checkpoint-storage-api`）；IMap 存储（`imap-storage-plugins/imap-storage-file`）
- 引擎侧：`engine-server/.../checkpoint/CheckpointCoordinator.java` + REST 观测（见 D4）；barrier 对齐 exactly-once 语义引用既有源码级报告 `ai-dev/analysis/2026-06-14-nop-stream-barrier-checkpoint-comparison.md`（SeaTunnel/Zeta 章节）
- savepoint/restore：CLI 一等参数（`ClientCommandArgs.java` 校验 sample/restore/savepoint 组合）
- 评分依据：hazard 面弱于 Flink（无 region 级 failover、无非对齐 checkpoint、控制面 HA 依赖 hazelcast IMap backup-count）→ 2；置信度 medium（部分结论转引 06-14 报告二手证据）

### D6 性能（选答）— 2@high

- 基准设施：`seatunnel-benchmarks/`（BenchmarkBase/BenchmarkPipeline/BenchmarkRunResult + connector sink 基准）
- 调优指南：`docs/en/engines/zeta/tuning-guide.md`
- 缺口：仓库内无公开基准数据/报告 → 未达 3

### D7 文档（选答）— 3@high

- 双语：`docs/en` + `docs/zh` 对称
- 完备链路：`getting-started/`（locally/docker/kubernetes/recipes）→ `introduction/concepts` → `connectors/`（316 份）→ `engines/zeta`（含 tuning）→ `architecture/`（api-design/engine/fault-tolerance/features）→ `faq.md` + `tools/`
- 特色：CDC 生产 cookbook、连接器依赖隔离说明、AI CLI 文档

## 必答维度结论映射（roadmap item 2 stage details 6 项 ↔ 本报告章节）

| roadmap item 2 必答项 | 本报告章节 | 结论摘要 |
|----------------------|-----------|---------|
| 1. 连接器生态组织方式 | §D2.1 | 74 模块 + factoryIdentifier/plugin-mapping SPI + formats/common 基座 + 每连接器 e2e + 316 文档目录化 |
| 2. CDC 产品化 | §D2.2 | Debezium 内嵌 + 9 方言 + IncrementalSource 开箱 + schema 演进一等 API + 生产 cookbook |
| 3. 多引擎适配层 Source/Sink API 抽象 | §D2.3 | 引擎无关 api 层 + translation 按版本适配层（flink-13/15/20）+ 三 starter |
| 4. 部署形态本地/集群/K8s | §D3 | local/cluster MasterType + hazelcast 三角色 + Helm chart + docker/k8s 文档 |
| 5. 监控与运维 | §D4 | 30+ REST 端点 + Prometheus/OpenMetrics + Web 控制台 + 日志/历史生命周期治理 |
| 6. 配置 DSL 与向导 | §D1 | HOCON 模板 + OptionRule 校验 + dry-run + option-rules 自描述 + 凭据加密 + AI CLI |

## P-REQ 候选条目（ST-，供 item 5 汇编）

> 字段：要求陈述 / 可判定验收标准 / 源码证据指针（相对 `~/sources/seatunnel`，锚定 SHA `5dbfb374`）/ 建议归属（roadmap item 6—18 或 Follow-up）

- **ST-1 连接器目录与能力矩阵文档**
  - 要求：nop-stream 提供连接器目录文档，每连接器标注 source/sink 能力、exactly-once/at-least-once 语义、并行度支持
  - 验收：`docs-for-ai/` 或 nop-stream 文档存在连接器目录页，覆盖现有全部连接器模块（connector/jdbc/debezium/file 等），逐项含语义标注
  - 证据：`docs/en/connectors/`（316 份 md、source/sink 分目录）、`docs/en/connectors/sink-overview.md`
  - 归属：item 17（文档产品化）
- **ST-2 连接器 dry-run 连通性验证**
  - 要求：提供提交前连通性/配置校验入口（CLI 或测试模式），连接不可达时 fail-fast 并给出结构化错误
  - 验收：repo 内存在 dry-run 入口类与对应测试；对错误配置返回显式错误码而非静默通过
  - 证据：`seatunnel-core/seatunnel-starter/.../command/ConnectorCheckCommand.java`、`DryRunConnectValidator.java`、`seatunnel-api/.../factory/SupportSourceDryRunValidation.java`
  - 归属：item 6（D-GAP 裁定后落 10/16 执行）
- **ST-3 REST 运维 API（作业生命周期 + 诊断）**
  - 要求：引擎暴露作业提交/停止/列表/详情与线程/系统诊断 REST 接口
  - 验收：nop-stream runtime 提供 REST 端点并有用例覆盖（submit/stop/running-jobs 至少三类）；接口文档落 owner doc
  - 证据：`engine-server/.../rest/RestConstant.java`（30+ 端点）、`rest/servlet/StopJobServlet.java`、`ThreadDumpServlet.java`
  - 归属：item 16
- **ST-4 Prometheus/OpenMetrics 标准暴露**
  - 要求：核心指标（吞吐/延迟/checkpoint 时长与失败/状态大小/背压）以 Prometheus 文本与 OpenMetrics 格式暴露
  - 验收：可 `curl /metrics` 得到 Prometheus TextFormat 004 内容；指标族覆盖 job/cluster/node 至少三级
  - 证据：`rest/servlet/MetricsServlet.java:24-44`、`telemetry/metrics/exports/{Cluster,Job,Node}MetricExports.java`
  - 归属：item 16
- **ST-5 checkpoint 运维观测**
  - 要求：checkpoint 概览（成功率/时长/失败原因）可通过运维接口查询并保留历史
  - 验收：存在 checkpoint overview/history 查询接口与对应数据结构；至少一次 e2e 断言其输出
  - 证据：`rest/servlet/CheckpointOverviewServlet.java`、`CheckpointHistoryServlet.java`、`rest/service/CheckpointMonitorRestService.java`
  - 归属：item 16
- **ST-6 凭据加密与配置校验入口**
  - 要求：作业配置支持凭据加密存储与独立校验命令（不启动作业即可报错到字段级）
  - 验收：存在 encrypt 工具/接口与 conf-validate 命令；对缺失必填项输出含选项名的错误
  - 证据：`rest/servlet/EncryptConfigServlet.java`、`SeaTunnelConfValidateCommand.java`、`api/configuration/util/OptionRule.java`
  - 归属：item 6（XDef 校验已有一半，加密与字段级报错为增量裁定项）
- **ST-7 Web 控制台（作业管理 + 拓扑可视化）**
  - 要求：提供（或裁剪裁定排除）Web 控制台：作业列表/详情/提交 + DAG 可视化
  - 验收：item 16 产出明确裁定（交付最小控制台 或 排除并记录依据）；若交付则含 jobs/overview 两视图
  - 证据：`seatunnel-engine/seatunnel-engine-ui/`（Vue3 + X6 + views jobs/managers/overview + cypress e2e）
  - 归属：item 16（裁剪范围由 D-GAP 决定）
- **ST-8 K8s Helm 部署裁定输入**
  - 要求：README 已声明 K8s 未实现的项，以 SeaTunnel Helm chart 为参照完成 go/defer/exclude 正式裁定
  - 验收：item 6 的 D-GAP 清单含 K8s 条目且三态裁定 + 依据记录
  - 证据：`deploy/kubernetes/seatunnel/`（Chart.yaml/values.yaml/templates 9 件）+ `docs/en/getting-started/kubernetes/`
  - 归属：item 6
- **ST-9 历史作业与日志生命周期治理**
  - 要求：完成作业历史保留时长、日志滚动与定期清理的可配置治理
  - 验收：配置项存在且默认值合理（如 history-job-expire-minutes）；有测试或文档断言清理行为
  - 证据：`config/seatunnel.yaml`（history-job-expire-minutes: 1440、logs.scheduled-deletion-enable: true）
  - 归属：item 16
- **ST-10 CDC 生产化文档（cookbook 级）**
  - 要求：CDC 使用文档达生产 cookbook 水位（snapshot/增量切换、offset 恢复、schema 演进边界）
  - 验收：文档含上述四类操作场景各一节；与 debezium 模块实际行为一致（抽 2 项与代码核对）
  - 证据：`docs/en/connectors/cdc-production-cookbook.md`、`connector-cdc-base/.../schema/SchemaChangeResolver.java`
  - 归属：item 17

## Conclusion

- SeaTunnel 产品化强项集中在「连接器生态组织 + 部署形态 + 运维监控」三维度（均 3@high）：其组织方式（SPI+目录化文档+每连接器 e2e）、CDC 产品化（Debezium 内嵌+9 方言+schema 演进 API+cookbook）、REST/Prometheus/Web 控制台三件套是 nop-stream 当前最直接的差距面（nop-stream 侧现状：MiniStreamCluster/HA/2PC 等 engine 能力已备，运维暴露面与连接器目录化未产品化）。
- 容错（2@medium）与性能（2@high）非其标杆维度（Flink 才是），nop-stream 已有的 region failover/unaligned checkpoint/RocksDB 增量快照不弱于 SeaTunnel——**不产生对标 P-REQ**。
- 被否决的方案：逐连接器深挖 74 个模块（否决原因：矩阵粒度为维度级组织方式，非单连接器实现；证据已足）；对标 SeaTunnel HOCON 引入第二套配置体系（否决原因：nop-stream XDSL/XDef 已有等价且更强的声明式+校验链路，仅需补 ST-6 增量）。
- 后续工作：本报告 10 条 ST- 候选由 item 5（plan `2026-09-01-0753-3`）汇编为最终 P-REQ 清单。

## References

- `ai-dev/analysis/2026-09/2026-09-01-research-asset-inventory-and-evaluation-framework.md`（评估矩阵与置信度口径）
- `ai-dev/analysis/2026-05-19a-seatunnel-vs-nop-stream-comparison.md`（架构/功能对比，被引用）
- `ai-dev/analysis/2026-06-14-nop-stream-barrier-checkpoint-comparison.md`（Zeta checkpoint 容错证据，被引用）
- `ai-dev/backlog/nop-stream-productization-roadmap.md`（item 2/16/17/6）
- 源码：`~/sources/seatunnel@5dbfb374f985349aeefde9cf84169aa98b3ac5ca`（正文内相对路径）
