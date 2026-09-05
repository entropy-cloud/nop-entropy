# 竞品产品化综合对比与 P-REQ 清单（roadmap item 5）

> Status: resolved
> Date: 2026-09-01
> Scope: 7 竞品（Flink / Beam / SeaTunnel / Spark Structured Streaming / Kafka Streams / tis / Hazelcast Jet）× 7 产品化维度综合矩阵（plan 1 §2 口径）；`ST-1..10` / `SPS-1..9` / `KS-1..9` 候选合并 + 6 份既有报告补录 → **P-REQ-1..28 最终清单**；items 6—18 scope 修正建议；旧 open 报告 Status 收敛；M1 完备性自查
> Conclusion: 产品化差距面收敛为四组——① 运维可观测（指标标准集/Prometheus/REST/健康状态机，最大空白，12 条 P-REQ 归 item 16）② 裁定型输入（dry-run/K8s/standby/IQ/触发器/checkpoint 版本化等 9 条归 item 6 D-GAP）③ 文档与起步（连接器目录/CDC cookbook/迁移指南/脚手架 4 条归 item 17）④ 状态产品化核对（schema 演进检查/离线 reshard 对照 2 条归 item 11）；tis 报告 6 项采纳建议逐项映射（4 采纳/2 显式拒绝），其中连接器 SPI + OLAP 端扩展超出 items 6—18 语义 → Follow-up 工作项建议已落 roadmap；05-19a 报告收敛 superseded，tis 报告裁定保持 open（超出本 item scope 的未决内容）。
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 5；plan `ai-dev/plans/nop-stream-productization/2026-09-01-0753-3-competitor-synthesis-p-req-list.md`
> Related: `2026-09-01-research-asset-inventory-and-evaluation-framework.md`（评估矩阵与置信度口径）、`2026-09-01-seatunnel-productization-analysis.md`、`2026-09-01-spark-structured-streaming-productization-analysis.md`、`2026-09-01-kafka-streams-productization-analysis.md`（三份 primary 报告）

## Context

- roadmap item 5（Phase R 收口项）：汇总 items 1—4 全部竞品证据 + 既有 Flink/Beam/Hazelcast/tis 对比报告，产出综合矩阵与最终 P-REQ 清单，并给出 items 6—18 的 scope 修正建议（roadmap 自进化入口之一）。
- 评分与置信度口径沿用 plan 1 §2.2（`分数@置信度`；high=源码级、medium=报告间接推断、low=文档/假设推断），不另造标准。
- 竞品源码 spot-check（2026-09-01 live）：`~/sources` 六仓 SHA——flink `b1fe7b40`、beam `898c1a8e`、seatunnel `5dbfb374`、spark `992b0905`、kafka `7434a60c`、tis `ae7e6e24`（前三者与各自报告 metadata 一致；tis 为浅引用不重开分析）。
- nop-stream 现状对照依据：roadmap Current baseline shipped 清单 + 本报告执行时的 live repo 抽查（抽查命令与命中见 §2.6）。

## Phase 1 — 证据汇编与综合对比矩阵

### 1.1 每竞品证据源（primary → 补充）

| 竞品 | Primary 证据（产品化评分来源） | 补充证据 | 综合置信度策略 |
|------|------|------|------|
| SeaTunnel | `2026-09-01-seatunnel-productization-analysis.md`（源码级，SHA `5dbfb374`） | `2026-05-19a`（架构层，已被 primary 吸收） | high（primary 全维度源码级） |
| Spark SS | `2026-09-01-spark-structured-streaming-productization-analysis.md`（源码级，SHA `992b0905`） | — | high |
| Kafka Streams | `2026-09-01-kafka-streams-productization-analysis.md`（源码级，SHA `7434a60c`） | — | high |
| Flink | `nop-stream-flink-comparison-deep-dive.md` + `ai-dev/analysis/nop-stream/01—08`（源码级历史审计） | `2026-07-20-nop-stream-dataflow-api-gap-analysis.md`（API 面）+ 本轮 `~/sources/flink` 只读 spot-check | API/FT/DEPL high；OPS 由 spot-check 升 high；CONN medium（生态在仓库外）；PERF/DOC low（无专项报告） |
| Beam | `2026-05-23-nop-stream-beam-hazelcast-comparison.md`（Beam 章节，源码级 proto/类引用） | 本轮 `~/sources/beam` 只读 spot-check（API 多语言 SDK、CONN 目录计数） | API/CONN high（spot-check）；DEPL/OPS/FT medium（报告）；PERF no-evidence；DOC low |
| Hazelcast Jet | `2026-05-23-nop-stream-beam-hazelcast-comparison.md`（Hazelcast 章节，源码级类引用；**源码未下载**） | 无（plan 1 §2.3：拆分须先补源码，本轮不补） | ≤medium（无源码，未覆盖维度显式 no-evidence） |
| tis | `2026-08/2026-08-14d-tis-vs-nop-data-integration-comparison.md`（v5.1.0 源码级，混合 scope） | — | CONN/DEPL/OPS/API high；FT medium / PERF low / DOC medium（按 08-14d 证据层级如实标注） |

### 1.2 tis 行（roadmap 必答）

- **裁定：保留 tis 行，无排除建议**。tis（v5.1.0，`~/sources/tis@ae7e6e24`）是数据集成平台形态的直接竞品（roadmap item 5 明确列出；plan 1 盘点 #3 direct-competitor），其连接器生态与运营闭环证据对 nop-stream 产品化有直接参照价值，排除缺乏证据支持。
- 评分以 08-14d 报告为 primary（该报告为源码级双路 explore 盘点）：CONN 3@high（~45 端类型 + Jenkins 式插件体系/市场）、DEPL 2@high（执行底座外挂 Flink/K8s/Akka，非自有引擎部署面）、OPS 2@high（WebSocket 实时监控 + 轮询告警 + 告警渠道插件，集成专用 UI）、API 1@high（SQL 驱动建模 + 向导，封装度高但开放性弱）。
- 低置信如实标注：FT 1@medium（容错主要依赖外部 Flink 引擎，TIS 层薄弱——08-14d 判断，非逐类源码核验）、PERF 1@low（无专项证据）、DOC 1@medium（tis.pub 外部文档，仓库内文档证据弱）。
- tis 与 nop 的能力差异中「应用层集群管理 vs 容器编排层」的分层结论（08-14d §C-2）被本报告 P-REQ-15（K8s 裁定）直接消费。

### 1.3 综合矩阵（7 竞品 × 7 维度，`分数@置信度` 或显式 `no-evidence`）

| 维度 | Flink | Beam | SeaTunnel | Spark SS | Kafka Streams | tis | Hazelcast Jet |
|------|-------|------|-----------|----------|---------------|-----|---------------|
| D1 API/DX | 3@high | 3@high | 2@high | 3@high | 3@high | 1@high | 2@medium |
| D2 连接器生态 | 3@medium | 3@high | 3@high | 2@high | 1@high | 3@high | no-evidence¹ |
| D3 部署形态 | 3@high | 2@medium | 3@high | 3@high | 2@high | 2@high | 2@medium |
| D4 运维监控 | 3@high² | 2@medium | 3@high | 3@high | 2@high | 2@high | no-evidence² |
| D5 容错语义 | 3@high | 2@medium | 2@medium | 2@high | 3@high | 1@medium | 2@medium |
| D6 性能 | 3@low³ | no-evidence³ | 2@high | 3@high | 2@high | 1@low | 2@medium |
| D7 文档 | 3@low⁴ | 2@low⁴ | 3@high | 3@high | 3@high | 1@medium | no-evidence⁴ |

no-evidence 标注（原因）：

1. Hazelcast CONN：05-23 报告未覆盖其连接器（source/sink）生态，`~/sources` 无 hazelcast 源码无法补证——按 plan 规则以 `no-evidence` 显式标注而非猜测评分。
2. Hazelcast OPS：05-23 报告仅覆盖执行/lifecycle/snapshot 机制，未取证 REST/UI/metrics 暴露面；源码未下载。
3. Beam PERF：Beam 本体是可移植编程模型（runner 执行），无自有运行时性能面，性能取决于 runner 选型——维度对该形态不适用，不猜测评分。Flink PERF 3@low：仓库内无基准报告（deep-dive/#13—#20 均为结构/机制审计），「标杆水位」属业界公认认知（Tungsten 级内存/网络栈在 01—07 系列有结构证据但无基准数据）。
4. Hazelcast DOC：05-23 未覆盖文档维度。Beam/Flink DOC low：官方文档站独立于代码仓库（spot-check：`~/sources/beam` 无 website 目录、flink `flink-docs` 模块为构建壳），评分仅凭业界认知。

置信度升级说明（相对 plan 1 §2.3 dry-run）：

- ²Flink OPS 3@low → **3@high**：本轮 spot-check 取得源码级证据——`flink-runtime/src/main/java/org/apache/flink/runtime/rest/`（REST 服务框架 + FileUpload/history/webmonitor 子包）、`flink-metrics/` 8 个 reporter 模块（core/datadog/dropwizard/graphite/influxdb/**prometheus**/jmx/slf4j/statsd）。评分不变（3），置信度升 high。
- Beam CONN/经 spot-check 升 high：`sdks/java/io/` 51 个顶层条目（bigquery/clickhouse/cassandra/amqp/azure/aws2/cdap/csv…，2026-09-01 `ls | wc -l` 计数）；API 经 `sdks/{java,python,go,typescript}` 多语言 SDK 存在性核验。
- Flink DEPL spot-check 佐证：`flink-dist/src/main/flink-bin/{bin,conf,kubernetes-bin,yarn-bin}`。

### 1.4 维度级观察（P-REQ 推导输入）

- **D4 运维监控是 nop-stream 最大空白面**：5/7 竞品 ≥2 分且 3 家 3 分（SeaTunnel/Spark/Flink）；nop-stream live 仅有 `runtime/checkpoint/metrics/CheckpointMetrics{,Snapshot}` 单点（spot-check），无 REST/无 Prometheus/无健康状态机——items 2—4 共 16 条候选中 10 条落 OPS，与矩阵分布一致。
- **D2 连接器生态两极**：集成平台型（SeaTunnel 74 模块、tis ~45 端、Beam 51 io）vs 引擎型（Flink 生态在仓库外、Spark 捆绑窄面、KS 1 by design）。nop-stream 现状（connector/batch/jdbc/debezium 四模块 + 无 SPI 注册中心）更接近「引擎型窄面」，但 nop-stream 的产品化目标含数据集成场景（fraud-example/CDC），目录化与能力矩阵是应有项（ST-1）。
- **D5 容错是 nop-stream 优势项**：region failover + unaligned checkpoint + RocksDB 增量 + 2PC sink（roadmap shipped）不弱于任何竞品（强于 SeaTunnel 2@medium / Spark 整查询重启 / tis 1@medium）——三份新报告的「反向结论」（不设对标 P-REQ）与既有 73 缺口收口记录一致，本清单不再产生 FT 对标条目（仅保留核对型 SPS-5/6/7）。
- **D1 API/DX 路线分化**：声明式（SeaTunnel HOCON/nop-stream XDSL）vs 统一关系模型（Spark DataFrame）vs 库嵌入式（KS DSL）。nop-stream XDSL+DataStream 双入口 + Delta 定位是差异化项（deep-dive §2.9），候选中仅 SPS-8（触发器语义核对）与 KS-4（异常策略）进入清单。
- **tis 的启示不在引擎层而在产品外壳层**（连接器市场/SQL 建模/监控告警闭环/AI Agent），与 7 维度矩阵的 D2/D4 权重分布互相印证。

## Phase 2 — P-REQ 清单

### 2.1 候选合并（零丢失映射表）

三份报告 28 条候选（ST-1..10 / SPS-1..9 / KS-1..9）全部处置，2 组语义重叠合并（源报告自标注同族）：

| 候选 | 处置 | P-REQ |
|------|------|-------|
| ST-1..ST-6, ST-8..ST-10 | 直接采纳（9 条；ST-7 另入合并行，ST 组合计 10 条） | P-REQ-22、13、5、3、6、14、15、11、23（对应见 §2.2） |
| ST-7 + SPS-3 | **合并**（Web 控制台/UI 页签同族；SPS-3 自标注「与 ST-7 同族，item 5 合并裁定」） | P-REQ-9 |
| SPS-1 + KS-2 | **合并**（指标标准集 + 分层模型同族；KS-2 自标注「与 SPS-1 进度指标集合并裁定」） | P-REQ-1 |
| SPS-2、SPS-4..SPS-9 | 直接采纳（7 条） | P-REQ-2、4、26、27、20、16、24 |
| KS-1、KS-3..KS-9 | 直接采纳（8 条） | P-REQ-7、8、17、18、19、10、21、25 |
| tis 报告 6 项采纳建议之 1、5 | 补录新增（无候选编号，见 §2.4） | P-REQ-28、12 |

合并规则：保留多来源引用（P-REQ-1 同时引用 `ProgressReporter.scala` 与 KS 五级 metrics 包；P-REQ-9 同时引用 SeaTunnel engine-ui 与 Spark streaming-ui）。

### 2.2 P-REQ 总表（最终清单，28 条）

> 字段：要求 / 验收标准（repo- 或 process-observable）/ 来源依据 / 归属 / 优先级（P0 阻塞产品化、P1 应有、P2 增强）/ nop-stream 现状（已满足=live 证据、部分满足=部分证据、未满足=无证据，依据 roadmap shipped 清单 + §2.6 抽查）。

**运维可观测组（归属 item 16，P-REQ-1..12）**

- **P-REQ-1 流式进度指标标准集与分层指标模型**（P0）
  - 要求：定义并暴露流作业标准指标（吞吐输入/处理速率、状态算子行数与时长、watermark、批次/checkpoint 耗时分解），按层级组织（引擎线程/任务/算子/输入输出/状态后端各一组标准指标名）
  - 验收：指标枚举/注册类落码，每层 ≥3 个指标有注册与单测；指标名与语义文档化成表
  - 来源：SPS-1（`sql/core/.../runtime/ProgressReporter.scala:355-394,625-663`）+ KS-2（`streams/.../processor/internals/metrics/{ThreadMetrics,TaskMetrics,ProcessorNodeMetrics,TopicMetrics,RebalanceListenerMetrics}.java`）；`~/sources/spark@992b0905`、`~/sources/kafka@7434a60c`
  - 现状：**未满足**（live 仅 `nop-stream-runtime/.../checkpoint/metrics/CheckpointMetrics{,Snapshot}` 单点，无分层注册体系）
- **P-REQ-2 作业进度事件监听 API**（P1）
  - 要求：流作业生命周期事件（started/progress/terminated 等价物）可注册监听，至少一个内建监听器（日志用）
  - 验收：监听接口 + ≥1 内建实现落码；MiniStreamCluster e2e 或单测断言监听器被回调
  - 来源：SPS-2（`sql/api/.../streaming/StreamingQueryListener.scala`、`runtime/StreamingQueryListenerBus.scala`）；`~/sources/spark@992b0905`
  - 现状：**未满足**
- **P-REQ-3 Prometheus/OpenMetrics 标准暴露**（P0）
  - 要求：核心指标以 Prometheus TextFormat 与 OpenMetrics 格式经 HTTP 暴露
  - 验收：启动配置后 `curl /metrics` 返回 TextFormat 004 内容；指标族覆盖 job/cluster/node 至少三级；有 e2e 或单测断言输出
  - 来源：ST-4（`engine-server/.../rest/servlet/MetricsServlet.java:24-44`、`telemetry/metrics/exports/{Cluster,Job,Node}MetricExports.java`）；`~/sources/seatunnel@5dbfb374`；Flink 侧佐证 `flink-metrics/flink-metrics-prometheus/`（spot-check）
  - 现状：**未满足**（rg "Prometheus" 无命中）
- **P-REQ-4 metrics 配置模板开箱**（P1）
  - 要求：metrics source/sink 配置模板文件随资源目录提供（含 ≥3 类 sink 注释样例）
  - 验收：模板文件存在且覆盖 ≥3 类 sink 样例；文档引用该模板
  - 来源：SPS-4（`conf/metrics.properties.template` 22 项）；`~/sources/spark@992b0905`
  - 现状：**未满足**
- **P-REQ-5 REST 运维 API（作业生命周期 + 诊断）**（P0）
  - 要求：引擎暴露作业提交/停止/列表/详情与线程/系统诊断 REST 端点
  - 验收：runtime 提供 REST 端点并有用例覆盖（submit/stop/running-jobs 至少三类）；接口文档落 owner doc
  - 来源：ST-3（`engine-server/.../rest/RestConstant.java` 30+ 端点、`StopJobServlet.java`、`ThreadDumpServlet.java`）；`~/sources/seatunnel@5dbfb374`；Flink 侧佐证 `flink-runtime/.../rest/`（spot-check）
  - 现状：**未满足**（nop-stream 无 REST/servlet 类）
- **P-REQ-6 checkpoint 运维观测（overview/history + 失败原因）**（P1）
  - 要求：checkpoint 成功率/时长/失败原因可通过运维接口查询并保留历史
  - 验收：overview/history 查询接口与数据结构存在；至少一次 e2e 断言其输出；失败记录含原因字段
  - 来源：ST-5（`CheckpointOverviewServlet.java`、`CheckpointHistoryServlet.java`、`CheckpointMonitorRestService.java`）；`~/sources/seatunnel@5dbfb374`；补录合并：Flink deep-dive「Checkpoint 失败原因追踪（failureCause 字段）」建议（`ai-dev/analysis/nop-stream-flink-comparison-deep-dive.md` §2.5）
  - 现状：**部分满足**（CheckpointMetrics 快照结构存在；历史查询接口与 failureCause 未见）
- **P-REQ-7 流作业逻辑健康状态机**（P0）
  - 要求：引擎侧暴露作业/实例级逻辑健康状态机（含 REBALANCING/PENDING_ERROR 等中间态等价物）与可注册状态监听器
  - 验收：状态枚举 + 迁移合法性表落码且有单测；监听器在 MiniStreamCluster e2e 中被断言调用
  - 来源：KS-1（`streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java:263-272` 七态）；`~/sources/kafka@7434a60c`；KS 核心命题结论：引擎形态取「逻辑健康信号面」
  - 现状：**未满足**（无 HealthState/StateListener 类）
- **P-REQ-8 状态后端专用指标 recorder**（P1）
  - 要求：RocksDB 状态后端暴露内部统计（block cache/memtable/compaction 级）
  - 验收：recorder 类 + 指标注册存在；单测或 e2e 断言非空读数
  - 来源：KS-3（`streams/.../state/internals/metrics/RocksDBMetricsRecorder.java`）；`~/sources/kafka@7434a60c`
  - 现状：**未满足**（nop-stream-rocksdb 无指标 recorder）
- **P-REQ-9 Web 控制台/流式页签（交付或裁定排除）**（P2）
  - 要求：提供（或显式裁定排除）作业列表/详情 + 速率/延迟时序 + DAG 可视化的 Web 页面
  - 验收：item 16 产出明确裁定（交付最小控制台或排除并记录依据）；若交付含 jobs/overview 两视图
  - 来源：ST-7（`seatunnel-engine/seatunnel-engine-ui/`，Vue3 + AntV X6）+ SPS-3（`sql/core/.../streaming/ui/{StreamingQueryTab,StreamingQueryPage}.scala`）；`~/sources/seatunnel@5dbfb374`、`~/sources/spark@992b0905`
  - 现状：**未满足**（无 Web 控制台；平台 AMIS 体系可作实现载体，属 item 16 裁定范围）
- **P-REQ-10 作业状态重置工具**（P1）
  - 要求：作业/应用级状态重置 CLI（清理本地状态 + 重置输入位点），支持全新重跑
  - 验收：工具类 + 手册章节存在；e2e 演示重置后从起点正确重放
  - 来源：KS-7（`tools/src/main/java/org/apache/kafka/tools/StreamsResetter.java` + `docs/streams/developer-guide/app-reset-tool.md`）；`~/sources/kafka@7434a60c`
  - 现状：**未满足**
- **P-REQ-11 历史作业与日志生命周期治理**（P1）
  - 要求：作业历史保留时长、日志滚动与定期清理可配置
  - 验收：配置项存在且默认值合理；有测试或文档断言清理行为
  - 来源：ST-9（`config/seatunnel.yaml`：`history-job-expire-minutes: 1440`、`logs.scheduled-deletion-enable: true`）；`~/sources/seatunnel@5dbfb374`
  - 现状：**未满足**
- **P-REQ-12 告警与事件外发通道**（P1）
  - 要求：作业失败/恢复事件经可插拔告警渠道外发（webhook/邮件/IM；渠道抽象可复用 nop-integration / nop-message 既有渠道）
  - 验收：AlertChannel 抽象 + ≥2 渠道实现落码；故障注入测试断言事件外发
  - 来源：tis 采纳建议 5（08-14d §C P1-7：告警渠道插件 DingTalk/WeCom/Lark/Email/Http + Flink 作业轮询告警，源码级）；评估矩阵 D4 检查点 3（告警与事件通知）
  - 现状：**未满足**（nop-integration 有 email/sms/飞书渠道但无引擎侧 AlertChannel 框架；nop-stream 内无 alert 类）

**D-GAP 裁定输入组（归属 item 6，P-REQ-13..21）**

- **P-REQ-13 连接器 dry-run 连通性验证**（P1）
  - 要求：提交前连通性/配置校验入口（CLI 或测试模式），不可达时 fail-fast 并给出结构化错误
  - 验收：dry-run 入口类与测试存在；错误配置返回显式错误码而非静默通过
  - 来源：ST-2（`ConnectorCheckCommand.java`、`DryRunConnectValidator.java`、`SupportSourceDryRunValidation.java`）；`~/sources/seatunnel@5dbfb374`
  - 现状：**未满足**
- **P-REQ-14 凭据加密与配置校验入口**（P1）
  - 要求：作业配置支持凭据加密存储与独立校验命令（不启动作业即报错到字段级）
  - 验收：encrypt 工具/接口与 conf-validate 命令存在；缺失必填项输出含选项名的错误
  - 来源：ST-6（`EncryptConfigServlet.java`、`SeaTunnelConfValidateCommand.java`、`OptionRule.java`）；`~/sources/seatunnel@5dbfb374`
  - 现状：**部分满足**（XDef/XDSL 校验链路平台已有；凭据加密 nop-credential 平台侧已有但未接入 stream 作业配置，字段级报错入口未有——增量由 item 6 裁定）
- **P-REQ-15 K8s/YARN 部署编排与 HPA 三态裁定**（P0，决策必答）
  - 要求：README 已声明的 K8s/YARN 未实现项，以 SeaTunnel Helm chart 为参照完成 go/defer/exclude 正式裁定（含 HPA、容器编排层 vs 应用层集群管理分层结论）
  - 验收：item 6 D-GAP 清单含 K8s/HPA 条目且三态裁定 + 依据记录
  - 来源：ST-8（`deploy/kubernetes/seatunnel/` Chart + templates）+ tis 采纳建议 4（08-14d §C P0-2：容器编排层 K8s/YARN/HPA，Nop 应用层集群管理已完备、缺容器编排层）；`~/sources/seatunnel@5dbfb374`
  - 现状：**未满足**（README 声明未实现且未裁定）
- **P-REQ-16 触发器/触发语义一等化核对**（P2）
  - 要求：触发语义（固定间隔/可用即批/一次性等）成为作业 API 一等参数并文档化——核对 nop-stream 连续流模型下触发语义的覆盖面与命名
  - 验收：item 6 裁定 nop-stream trigger 语义覆盖面（含 autocheckpoint 间隔等既有机制映射）；裁定记录含文档化要求
  - 来源：SPS-8（`sql/api/.../execution/streaming/Triggers.scala:57-125` 五态）；`~/sources/spark@992b0905`
  - 现状：**部分满足**（窗口级 Trigger 家族存在：EventTimeTrigger/ContinuousEventTimeTrigger/DeltaTrigger 等；作业级触发语义无——连续流模型 by design，覆盖面待裁定）
- **P-REQ-17 可插拔异常处理策略**（P1）
  - 要求：反序列化/处理/写出异常的策略可声明式选择（跳过并记录 vs 快速失败），默认 fail-fast
  - 验收：策略接口 + ≥2 内建实现 + 每实现一个行为测试；配置键文档化
  - 来源：KS-4（`streams/.../errors/{DeserializationExceptionHandler,LogAndContinueExceptionHandler,LogAndFailExceptionHandler,ProductionExceptionHandler}.java`）；`~/sources/kafka@7434a60c`
  - 现状：**部分满足**（平台错误码体系与 fail-fast 语义存在；异常策略声明式选择未见——item 6 对照裁定）
- **P-REQ-18 Standby 热备副本裁定**（P1，决策型）
  - 要求：以 KS StandbyTask 为参照，裁定 nop-stream 是否引入状态热备副本（缩短 failover 状态恢复窗口）
  - 验收：D-GAP 清单含 standby 条目且 go/defer/exclude 三态裁定 + 依据
  - 来源：KS-5（`processor/internals/StandbyTask.java` + changelog 恢复 10 变体）；`~/sources/kafka@7434a60c`
  - 现状：**未满足**（无 standby 实现；region failover + 增量快照已缩短恢复窗口，是否需要热备属裁定内容）
- **P-REQ-19 Interactive Query 一致性边界裁定**（P2，决策型）
  - 要求：若裁定引入状态查询接口，须采用位置边界式一致性（查询拒绝落后数据）；否则显式 exclude
  - 验收：D-GAP 清单含 IQ 条目与三态裁定；若 go，查询 API 带 position/bound 参数并有测试
  - 来源：KS-6（`streams/src/main/java/org/apache/kafka/streams/query/{Position,PositionBound,StateQueryRequest}.java`）；`~/sources/kafka@7434a60c`
  - 现状：**未满足**（无查询接口；需裁定）
- **P-REQ-20 checkpoint 版本化与校验和**（P1）
  - 要求：checkpoint 存储格式带版本管理（升级兼容）与写入校验和/原子性保证
  - 验收：版本管理 + 原子写/校验实现存在且有故障注入测试（部分写后重启可恢复）
  - 来源：SPS-7（`checkpointing/CheckpointVersionManager.scala`、`ChecksumCheckpointFileManager.scala`）；`~/sources/spark@992b0905`；对照 nop-stream `ICheckpointStorage`（LocalFile/JDBC）既有实现裁定增量
  - 现状：**部分满足**（ICheckpointStorage 双实现 shipped（roadmap Stage 43—47 相关）；版本化/校验和未核验——item 6/11 裁定）
- **P-REQ-21 跨版本升级兼容测试基建**（P1）
  - 要求：状态格式/checkpoint 跨版本升级系统测试（旧版本产物 → 新版本恢复），或显式裁定 defer 并说明版本策略
  - 验收：≥1 条升级路径自动化测试存在（或 D-GAP 显式 defer + 版本策略记录）
  - 来源：KS-8（`streams/upgrade-system-tests-0110 … 43` 26 个版本模块）；`~/sources/kafka@7434a60c`；执行可落 item 14/15
  - 现状：**未满足**

**文档与起步组（归属 item 17，P-REQ-22..25）**

- **P-REQ-22 连接器目录与能力矩阵文档**（P1）
  - 要求：连接器目录文档，每连接器标注 source/sink 能力、exactly-once/at-least-once 语义、并行度支持
  - 验收：`docs-for-ai/` 或 nop-stream 文档存在目录页，覆盖现有全部连接器模块（connector/jdbc/debezium/file/batch），逐项含语义标注
  - 来源：ST-1（`docs/en/connectors/` 316 份 + `sink-overview.md`）；`~/sources/seatunnel@5dbfb374`
  - 现状：**未满足**（spot-check：docs-for-ai 无 nop-stream 连接器目录页）
- **P-REQ-23 CDC 生产化 cookbook 文档**（P1）
  - 要求：CDC 使用文档达生产 cookbook 水位（snapshot/增量切换、offset 恢复、schema 演进边界）
  - 验收：文档含四类操作场景各一节；抽 2 项与 debezium 模块实际行为核对一致
  - 来源：ST-10（`docs/en/connectors/cdc-production-cookbook.md`、`SchemaChangeResolver.java`）；`~/sources/seatunnel@5dbfb374`
  - 现状：**未满足**
- **P-REQ-24 版本化迁移指南**（P1）
  - 要求：流作业/状态/checkpoint 版本间行为变更维护迁移指南
  - 验收：迁移指南条目存在，首个版本覆盖 XDSL 与状态格式变更
  - 来源：SPS-9（`docs/streaming/ss-migration-guide.md`）；`~/sources/spark@992b0905`
  - 现状：**未满足**
- **P-REQ-25 快速起步脚手架**（P1）
  - 要求：nop-stream 快速起步工程脚手架（maven archetype 或等价模板 + 3 个入门示例拓扑）
  - 验收：脚手架生成工程可 `mvn test` 通过；示例含 source→transform→sink 最小链路
  - 来源：KS-9（`streams/quickstart/java/src/main/resources/archetype-resources/` Pipe/LineSplit/WordCount）；`~/sources/kafka@7434a60c`
  - 现状：**未满足**（无 archetype；fraud-example 可作示例基础）

**状态产品化核对组（归属 item 11，P-REQ-26..27）**

- **P-REQ-26 状态 schema 演进兼容检查**（P1）
  - 要求：状态存储 schema 变更时提供兼容性检查（不兼容即 fail-fast 并报告差异）
  - 验收：检查器类 + 不兼容场景测试（变更字段类型断言报错）
  - 来源：SPS-5（`state/StateSchemaCompatibilityChecker.scala`、`SchemaHelper.scala`）；`~/sources/spark@992b0905`
  - 现状：**部分满足**（`StateMigrationRegistry`/`MigratableKeyedState`/`StateMigrationFunction` 存在——live spot-check；schema 兼容 fail-fast 检查待 item 11 核对）
- **P-REQ-27 离线状态重分区工具能力对照**（P2，核对型）
  - 要求：核对 nop-stream 既有离线 reshard 工具与 Spark OfflineStateRepartitionRunner 能力面（多分区策略/校验/错误报告），缺口转 D-GAP
  - 验收：item 11 审计报告含对照表（功能逐项 ✓/✗）
  - 来源：SPS-6（`state/OfflineStateRepartitionRunner.scala`）；`~/sources/spark@992b0905`
  - 现状：**部分满足**（离线 reshard 工具已 shipped：roadmap Current baseline + `runtime/checkpoint/reshard/MaxParallelismReshardMigration.java` live 命中；能力面对照未做）

**生态扩展组（Follow-up 建议，P-REQ-28）**

- **P-REQ-28 连接器 SPI 注册与 OLAP/数仓端扩展**（P1，建议 Follow-up 工作项）
  - 要求：建立连接器 SPI 注册中心（`IStreamSourceFactory`/`IStreamSinkFactory` 等价物，NopIoC 承载）与连接器能力矩阵机制；评估扩展 OLAP/数仓端连接器（ClickHouse/Doris/StarRocks/Hive/Paimon 等）的最小集
  - 验收：SPI 注册接口 + 注册发现测试存在；OLAP 端扩展裁定记录（最小集或分期）落 D-GAP/Follow-up plan
  - 来源：tis 采纳建议 1（08-14d §C P0-1：连接器生态与插件市场，~45 端类型源码级）+ 05-19a §7.2「连接器 SPI（NopIoC 承载）」补录；`~/sources/tis@ae7e6e24`
  - 现状：**部分满足**（connector/batch/jdbc/debezium 模块化存在；SPI 注册中心无——live spot-check `IStreamSourceFactory|IStreamSinkFactory` 零命中；OLAP 端连接器全部缺失）

### 2.3 既有报告补录处置（6 份 source 报告逐报告，零丢失）

| # | 报告 | 处置 | 说明 |
|---|------|------|------|
| 1 | `ai-dev/analysis/nop-stream-flink-comparison-deep-dive.md` | **already-shipped（主体）+ adopted（1 条并入）** | P0/P1/P2/P3 低成本迁移清单（Timer/ProcessingTimeTimer/WindowAggregationOperator 退役/链化检查/PendingCheckpoint/RocksDB/RPC/Kafka 连接器）已由前序 production roadmap Items 14—56 与 73 缺口收口消费（08-gap-analysis 全部 Closed/Excluded，roadmap Current baseline shipped 清单）；「Checkpoint 失败原因追踪（failureCause）」属运维观测增量 → **adopted 并入 P-REQ-6**（保留来源引用） |
| 2 | `ai-dev/analysis/2026-05-23-nop-stream-beam-hazelcast-comparison.md` | **already-shipped（主体）** | P0—P2 设计建议（StreamComponents/SourceWorkUnit/CheckpointParticipant/FlowControl/TerminationModes/WindowingStrategy 等）由前序 roadmap 分布式/HA/failover stages 消费：drain/reconnect、unaligned checkpoint、多并发 checkpoint、FLIP-27 Source（split/enumerator/dynamic split）、2PC sink、DeploymentPlan 背压/队列/流控（deep-dive §2.8）均 shipped；P2「protocol-level observability（MonitoringInfo/ProgressSnapshot）」→ 归入 P-REQ-1 的指标 envelope 语义（不另立条目）；报告自身 Status 已 resolved，无收敛动作 |
| 3 | `ai-dev/analysis/2026-05-19a-seatunnel-vs-nop-stream-comparison.md` | **already-shipped（4/5）+ adopted（1 条并入）** | §7.2 借鉴点处置：Source/Sink 抽象 P0（shipped：FLIP-27 + 2PC）、声明式配置 P1（shipped：XDSL+Delta）、分片读取 P2（shipped：SourceEnumerator）、Schema 自动推断 P2（shipped：ORM 元数据体系）、连接器 SPI P1 → **adopted 并入 P-REQ-28**；4 条 Open Questions 已被后续工作回答/收口（Kafka 通道=MessageSourceFunction 桥接、CDC=debezium 模块 shipped、流批分工=nop-batch 桥接器、连接器规范→P-REQ-28）；**Status 收敛见 §3.2** |
| 4 | `ai-dev/analysis/2026-08/2026-08-14d-tis-vs-nop-data-integration-comparison.md` | **adopted（6 项逐项映射见 §2.4）** | 6 项优先级采纳建议全部逐项处置；报告其余结论（nop-batch/job/metadata/ai 侧）超出 item 5 scope；**Status 裁定见 §3.2** |
| 5 | `ai-dev/analysis/2026-07/2026-07-20-nop-stream-dataflow-api-gap-analysis.md` | **already-shipped** | P1 缺口（ProcessFunction + SideOutput）已由 plan 305 落地消费——live spot-check：`nop-stream-core/.../common/functions/ProcessFunction.java` 存在；ConnectedStreams 等剩余项属既有 non-goal/优先级裁定（报告自身 Status 已 resolved 且指向 plan 305），不产生新 P-REQ |
| 6 | `ai-dev/analysis/2026-08/2026-08-06-nop-stream-audit-baseline-and-roadmap-analysis.md` | **already-shipped（方法论输入）** | 审计语义/capability matrix/证据分级方法论由 roadmap Phase M（items 7—11）与 item 6 直接消费为审计方法输入（非产品化要求来源，不产生 P-REQ）；其列出的 3 份 active remediation plans（2026-08-04-2300-1/2/3）已由前序 production roadmap 收口（roadmap header：Items 14—56 全部 done） |

### 2.4 tis 报告 6 项采纳建议逐项映射（不允许报告级概括吞掉子项）

| tis 建议（08-14d Conclusion 优先级序） | 处置 | 映射 |
|------|------|------|
| ① 连接器 SPI 与 OLAP 端支持（含插件市场） | **adopted（部分）** | 引擎侧 SPI 注册 + OLAP 端扩展 → P-REQ-28（Follow-up 工作项建议，§3.1）；Jenkins 式插件市场/运行期安装机制 → 显式不采纳（Nop 平台以 NopIoC + Delta 定制为扩展机制，05-19a Open Question 2 的替代思路被采纳为「SPI=IoC bean」，市场机制超出引擎产品化 scope） |
| ② 批流统一管道模型 | **rejected（对本 roadmap）** | 理由：平台级数据集成产品形态决策（统一管道实体跨 nop-stream/nop-batch），非 nop-stream 引擎产品化范围；且 2026-08-06 审计基线的 Design Target 已把「SQL/Table API、双流 join」等列为 non-goal——如未来立项应为独立 mission（stop-edit-restart 素材，§3.1 记录） |
| ③ SQL 数据流建模 | **rejected（对本 roadmap）** | 理由：同上——nop-stream 设计定位明确 non-goal 含 SQL/Table API（`ai-dev/design/nop-stream/00-vision.md` 经 2026-08-06 报告引用）；SQL 血缘/元数据能力属 nop-metadata 域 |
| ④ 容器编排层（K8s/YARN 部署 + HPA） | **adopted** | → P-REQ-15（item 6 三态裁定必答；08-14d 的「应用层集群管理已完备、仅缺容器编排层」分层结论作为裁定输入） |
| ⑤ 监控告警闭环 | **adopted** | 监控观测面 → P-REQ-1/3/5/6（候选条目已覆盖）；告警渠道闭环 → P-REQ-12（tis 补录新增，复用 nop-integration/nop-message 渠道抽象） |
| ⑥ Pipeline AI Agent 垂直化 | **rejected（对本 roadmap）** | 理由：AI 垂直产品功能而非引擎产品化要求；nop-ai 有通用 agent 框架，垂直化属 nop-ai 产品线决策（如立项属独立 mission） |

### 2.5 补录排除理由（其余 nop-stream 相关报告不入 source set 的裁定，零丢失声明可审计）

- `2026-06-14-nop-stream-barrier-checkpoint-comparison.md`：FT 机制技术对比（barrier/checkpoint 源码级），其结论已由 73 缺口收口与 ST 报告 D5 引用消费——是 FT 证据源而非产品化要求来源。
- `2026-05-22-test-coverage-comparison-flink.md`、`2026-05-22b-nop-stream-vs-flink-streaming-test-comparison.md`：测试覆盖对比，属内部测试质量证据，由 items 7—11 模块审计消费。
- `ai-dev/analysis/nop-stream/` 01—08 系列：Flink 源码审计 + 73 缺口显式收口记录，已由前序 production roadmap（Items 14—56，全 done）消费完毕；其 gap 行是历史执行记录非新要求。
- `2026-04-02-nop-stream-design-review.md`、`2026-04-02-nop-stream-review.md`、`2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-06-30-nop-stream-code-audit.md`：内部设计/代码审计（Phase M 输入，roadmap items 7—11 引用），非产品化要求来源。
- `checkpoint-module-extraction.md`、`distributed-exactly-once-design-amendment.md`：模块划分可行性/设计修订文档（design 侧历史），已被 16 份设计文档体系吸收。
- `metadata-survey/2026-07-15-griffin-vs-nop-stream-comparison.md`、`2026-07/2026-07-17-data-quality-tools-comparison.md`：数据质量/流批统一视角对比，Scope 混合且主结论服务 nop-metadata/数据质量域，流处理产品化维度证据为间接。
- `2026-07/2026-07-24-nop-module-matrix.md`、`2026-08/2026-08-23-direct-sql-usage-survey.md`：平台模块矩阵/SQL 使用调研（nop-stream 仅局部条目），定位层/局部佐证，非产品化要求来源。

### 2.6 交叉核对与 live 抽查记录

- **归属核对**：P-REQ-1..12 ↔ item 16「可观测性与运维产品化（metrics 暴露收敛、健康检查、运维操作手册）」语义匹配（P-REQ-12 告警通道超出 item 16 字面语义 → §3.1 语义调整建议）；P-REQ-13..21 ↔ item 6「对照 P-REQ 清单产出 D-GAP + README 已声明项裁定」匹配；P-REQ-22..25 ↔ item 17「用户指南/连接器目录/文档体系」匹配（P-REQ-25 脚手架属 getting-started 范畴）；P-REQ-26..27 ↔ item 11「rocksdb 状态后端健壮性审计」匹配；P-REQ-28 无既有归属 → Follow-up（§3.1）。
- **live 抽查**（2026-09-01，worktree 根）：`find nop-stream -name "ProcessFunction.java"` 命中 core（P-REQ 现状依据：API 缺口已收口）；`rg "Prometheus"` 零命中；`find -name "*Servlet*|*Rest*"` 零命中；`rg "HealthState|StateListener"` / `"ExceptionHandler"` / `"-iname *alert*|*encrypt*|*standby*|*reset*"` 主代码零命中；`rg "IStreamSourceFactory|IStreamSinkFactory"` 零命中；`ls docs-for-ai/03-modules/` 无 stream 连接器目录页；metrics 仅 `runtime/checkpoint/metrics/`；`StateMigrationRegistry` 与 `checkpoint/reshard/MaxParallelismReshardMigration` 命中（P-REQ-26/27 部分满足依据）。
- **验收标准可判定性**：28 条验收标准全部为 repo-observable（类/文件/测试/文档存在性 + 行为断言）或 process-observable（item 6/16/11 产出裁定记录），无「更好/完善」类模糊词。

## Phase 3 — items 6—18 scope 修正建议与 M1 收口

### 3.1 scope 修正建议（roadmap 自进化入口之一）

**成立的修正（已按 roadmap Rules 落地：Follow-up 工作项追加 + Last updated）：**

- **F-1（P-REQ-28 载体）**：新增 Follow-up 工作项「连接器生态产品化：SPI 注册中心 + 能力矩阵 + OLAP/数仓端连接器最小集裁定」（编号顺延至 19，`todo`，来源标注本 plan/ tis 建议① + 05-19a §7.2）。理由：连接器生态是 7 维度矩阵中 nop-stream 与集成平台型竞品的最大差距（D2），且 tis/SeaTunnel 双源证据；既有 items 6—18 无任何 item 覆盖「新增连接器/SPI 注册」语义（item 10 是既有连接器审计、item 17 是目录文档）。
- **F-2（P-REQ-12 载体）**：item 16 语义调整建议——现文本「metrics 暴露收敛、健康检查、运维操作手册」未覆盖「告警与事件外发通道」。**建议**（stop-edit-restart 素材，本 plan 不直接改）：item 16 追加「告警渠道闭环（AlertChannel，复用 nop-integration/nop-message 渠道抽象）」。过渡期 P-REQ-12 归属建议记为 item 16（语义调整建议成立后正式生效）。
- **F-3（P-REQ-21 执行面）**：item 15「稳定性与性能演练」建议吸收跨版本升级兼容测试的执行面（KS-8 原报告即建议「item 6 裁定 → 执行可落 14/15」）。**建议**级记录，不直接改。

**显式不采纳进本 roadmap 的 tis 建议（裁定记录，防止后续重复评估）：**

- tis ②批流统一管道、③SQL 数据流建模、⑥Pipeline AI Agent：见 §2.4 拒绝理由。均为平台级/产品线级决策而非 nop-stream 引擎产品化范围；SQL/Table API 更是设计 non-goal。如未来重启，应以独立 mission 立项（stop-edit-restart），不应改写本 roadmap 既有 items。

**无需修正的确认**：items 7—11（模块审计）、12—15（复合场景/分布式/演练）、17（文档）、18（最终验收）语义与 P-REQ 映射无冲突；item 6/16 的 P-REQ 输入量（9 条 + 12 条）在单 plan 承载范围内（各为裁定/裁剪型为主）。

### 3.2 旧报告 Status 收敛裁定

- **`2026-05-19a-seatunnel-vs-nop-stream-comparison.md`：收敛**。Status `open` → `superseded`，`Superseded By` 指向本报告；其架构/功能结论（第一部分架构总览、六部分实现路径）的**产品化维度后续**由 `2026-09-01-seatunnel-productization-analysis.md`（live SHA 源码级）接管，借鉴点处置（§2.3 #3）与本报告 Phase 2 吸收完毕；4 条 Open Questions 均已收口（§2.3 #3）。Conclusion 字段由 TBD 补写终态。
- **`2026-08-14d-tis-vs-nop-data-integration-comparison.md`：保持 `open`（显式裁定，非默认）**。理由：① 其 Scope 覆盖 nop-batch/nop-job/nop-metadata/nop-ai 侧结论（元数据治理/调度/血缘/ChatBI 对比），这些超出 item 5（nop-stream 产品化）scope 且未被任何后续报告吸收；② 3 条 Open Questions 未决（Flink-SQL 生成策略、Delta 作为连接器市场替代机制、微前端 vs AMIS 分发），其中第 2 条与本报告 P-REQ-28 的 SPI 形态裁定直接相关，留待 Follow-up item 19 执行时收敛。本报告已吸收的部分（6 项采纳建议逐项映射 §2.4 + 矩阵 tis 行 §1.2）在两报告间建立了显式引用。

### 3.3 M1 完备性自查（items 1—5 交付物逐项）

| Item | 交付物（路径） | 完成证据 |
|------|------|------|
| 1 | `ai-dev/analysis/2026-09/2026-09-01-research-asset-inventory-and-evaluation-framework.md` | Status: resolved；roadmap item 1 `done`（plan 0753-1 completed 2026-09-01，closure audit PASS）；8 条 stale gap 行收口（08-gap-analysis.md） |
| 2 | `ai-dev/analysis/2026-09/2026-09-01-seatunnel-productization-analysis.md` | Status: resolved；roadmap item 2 `done`（plan 0753-2 Phase 1，closure audit PASS）；`~/sources/seatunnel@5dbfb374` live 复核一致 |
| 3 | `ai-dev/analysis/2026-09/2026-09-01-spark-structured-streaming-productization-analysis.md` | Status: resolved；roadmap item 3 `done`（plan 0753-2 Phase 2，closure audit PASS）；`~/sources/spark@992b0905` live 复核一致 |
| 4 | `ai-dev/analysis/2026-09/2026-09-01-kafka-streams-productization-analysis.md` | Status: resolved；roadmap item 4 `done`（plan 0753-2 Phase 3，closure audit PASS）；`~/sources/kafka@7434a60c` live 复核一致 |
| 5 | 本报告（综合矩阵 + P-REQ-1..28 + 修正建议 + M1 自查） | 本 plan 0753-3 closure 见 plan 文件；tis 证据 `2026-08-14d` 已消费（§1.2/§2.4） |

M1（竞品调研完备）解锁条件 1—5 全部满足 → 本报告 + plan closure 后 M1 成立，item 6（D-GAP）可启动，其对照输入即本报告 P-REQ 清单。

## Conclusion

- 综合矩阵覆盖 7 竞品 × 7 维度全 49 格：45 格有评分（`分数@置信度`，口径为 plan 1 §2.2），4 格显式 `no-evidence`（Hazelcast CONN/OPS/DOC、Beam PERF 维度不适用）；Flink OPS 经 spot-check 从 3@low 升 3@high。
- 最终 P-REQ 清单 28 条：26 条来自三份新报告候选（28 候选 − 2 合并），2 条 tis 补录新增（告警通道、连接器 SPI/OLAP）；优先级 P0 5 条（P-REQ-1/3/5/7/15——运维可观测核心 4 + K8s 裁定必答 1）、P1 19 条、P2 4 条（P-REQ-9/16/19/27）；全部条目含唯一编号、要求、可判定验收标准、来源依据（报告路径 + 竞品源码路径 + SHA）、归属建议、现状对照。
- 候选零丢失：ST-1..10 / SPS-1..9 / KS-1..9 共 28 条全部映射（§2.1）；补录零丢失：6 份 source 报告逐报告三态处置（§2.3），tis 6 项逐项映射（§2.4），排除理由显式记录（§2.5）。
- 修正建议：F-1 已落 roadmap（Follow-up item 19 追加 + Last updated）；F-2/F-3 为 stop-edit-restart 建议记录；tis ②③⑥ 显式拒绝并记录理由。
- 被否决的方案：为 Hazelcast 补开源码分析（否决原因：plan Out Of Scope 禁止新竞品分析，05-23 证据 + no-evidence 标注已满足矩阵完备性要求）；将 P-REQ 直接排期实施（否决原因：plan Non-Goals——排期属各 owning plan）；在本 plan 内裁决排除 tis（否决原因：roadmap item 5 明确列 tis，排除只能作为 stop-edit-restart 建议——而证据支持保留，无排除建议）。
- 后续工作：item 6（D-GAP，对照本清单 P-REQ-13..21 + 15 的裁定必答项）与 Follow-up item 19（连接器生态）由 mission 按调度规则取用。

## References

- `ai-dev/backlog/nop-stream-productization-roadmap.md`（item 5 / items 6—18 / Rules / Current baseline）
- `ai-dev/plans/nop-stream-productization/2026-09-01-0753-3-competitor-synthesis-p-req-list.md`
- `ai-dev/analysis/2026-09/2026-09-01-research-asset-inventory-and-evaluation-framework.md`（矩阵与置信度口径 §2.2、dry-run §2.3、缺口清单 §2.4）
- `ai-dev/analysis/2026-09/2026-09-01-seatunnel-productization-analysis.md`、`2026-09-01-spark-structured-streaming-productization-analysis.md`、`2026-09-01-kafka-streams-productization-analysis.md`（三份 primary + ST/SPS/KS 候选）
- 补录 source 六份（§2.3 表列路径）
- `ai-dev/analysis/nop-stream/08-gap-analysis.md`（73 缺口收口记录）
- 源码 spot-check：`~/sources/flink@b1fe7b40`、`~/sources/beam@898c1a8e`、`~/sources/tis@ae7e6e24`（只读引用；seatunnel/spark/kafka SHA 见各报告 metadata）
