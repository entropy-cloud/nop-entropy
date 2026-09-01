# nop-stream 整体设计产品化 gap 分析（D-GAP，roadmap item 6）

> Status: resolved
> Date: 2026-09-01
> Scope: 对照 P-REQ-13..21（9 条裁定型输入）+ README 三项未实现声明（K8s/YARN 编排、HPA、RuntimeTopology）审视 nop-stream 整体设计（16 份设计文档 + live repo），产出 D-GAP 三态裁定清单与下游输入（Phase M 审计重点 / Phase S 场景约束 / item 16 裁剪建议）
> Conclusion: D-GAP 全量裁定落地——go×4（P-REQ-13/14→Follow-up item 20「提交前校验产品化」、16→item 17 文档化、20→item 8 窄增量收敛 manifest 字段 drift）、defer×3（15/K8s、17、21，各附条件与 revisit 触发点）、exclude×5（15/YARN、15/HPA（vision non-goal 必然推论）、15/RuntimeTopology（概念退役）、18 standby、19 IQ）；F-2 升级为 stop-edit-restart 触发建议（item 16 追加告警渠道闭环语义）、F-3 维持建议级；现状修正 2 处 + 设计-实现 drift 2 项（D-DRIFT-1/2，修正建议供 item 17/8 收口）；Phase M/S/16 三方下游输入齐备自包含，S3 裁定不派生维持 S1/S2。
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 6；plan `ai-dev/plans/nop-stream-productization/2026-09-01-0938-1-design-productization-gap-analysis.md`
> Related: `2026-09-01-competitor-productization-synthesis-and-p-req.md`（P-REQ 清单，本报告直接输入）、`2026-09-01-seatunnel/spark-structured-streaming/kafka-streams-productization-analysis.md`（三份 primary）

## Context

- roadmap item 6（Phase D 收口项，critical path）：M1 完备后，对照 P-REQ 清单审视 nop-stream 整体设计，产出 D-GAP 清单（每条 go/defer/exclude 三态裁定 + 依据 + live 证据锚点 + 归属执行项），是 items 7—11 审计重点、item 12 场景约束、item 16 裁剪的正式输入源。
- P-REQ 报告现状抽查（§2.6）结论为 13/15/18/19/21 未满足、14/16/17/20 部分满足；四条部分满足项需本报告补 live 证据后裁定，P-REQ-20 重点核验版本化/校验和 absence。
- 裁定不得与 `ai-dev/design/nop-stream/00-vision.md` 显式 non-goals 冲突；冲突时只能产出 stop-edit-restart 建议。

## Phase 1 — 输入证据核对与 live 基线固化

> 核对时间：2026-09-01；worktree 根；除特别标注外所有命令在仓库根执行，`--glob '!target'` 排除生成物。

### 1.1 P-REQ-13..21 逐条 live 证据表

> 「现状」列为 P-REQ 报告 §2.2 记载值的复核结果：`一致` = 复核后维持原判定；`修正` = 复核发现原判定需精确化（漂移记录）。

| P-REQ | 要求摘要 | 现状 | live 证据锚点（存在 / 显式 absence） | 竞品来源参照（复核） |
|-------|---------|------|------|------|
| 13 连接器 dry-run | 提交前连通性/配置校验入口 | 未满足，一致 | absence：`rg -il "dryrun\|dry-run\|DryRun\|ConnectorCheck" nop-stream/`（排除 target）零命中；nop-stream 无 CLI/命令模块。设计文档无 dry-run 承诺（connector-design.md 仅 2PC preCommit 校验与 fencing 校验提及） | SeaTunnel `ConnectorCheckCommand.java` / `DryRunConnectValidator.java`（ST-2，`~/sources/seatunnel@5dbfb374`） |
| 14 凭据加密与配置校验 | 凭据加密存储 + 独立校验命令（字段级报错） | 部分满足，一致（证据锚点如下） | 存在：XDef/XDSL 校验链路 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`（+ `pattern.xdef`、`resource-spec.xdef`，模型解析期字段级校验）；平台凭据设施 `nop-credential-{api,app,codegen,dao,kms-vault,meta,service,web}` 九模块 live。absence：nop-stream 与 nop-credential 零集成（`rg -il credential nop-stream/` 唯一命中为 `fraud-example/.../GeographicAnomalyPattern.java:26` Javadoc 域描述词汇）；无 conf-validate 等价命令/工具类 | SeaTunnel `EncryptConfigServlet.java` / `SeaTunnelConfValidateCommand.java` / `OptionRule.java`（ST-6） |
| 15 K8s/YARN/HPA 三态裁定 | README 声明项正式裁定（P0 决策必答） | 未满足，一致 | 见 §1.2 README 三项声明核对 | SeaTunnel Helm chart `~/sources/seatunnel/deploy/kubernetes/seatunnel/`（Chart.yaml/templates/values.yaml + helm-tests，live 复核存在）；tis 08-14d §C-2 分层结论 |
| 16 触发语义一等化核对 | 触发语义成为作业 API 一等参数并文档化 | 部分满足，一致（覆盖面精化） | 存在：窗口级 Trigger 家族 11 类——`nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/{Trigger,TriggerResult,EventTimeTrigger,ProcessingTimeTrigger,CountTrigger,DeltaTrigger,PurgingTrigger,ContinuousEventTimeTrigger,ContinuousProcessingTimeTrigger,ProcessingTimeoutTrigger}.java` + `nop-stream-core/src/main/java/io/nop/stream/core/operators/Triggerable.java`；作业级等价机制 `core/checkpoint/CheckpointConfig.java`（`checkpointInterval`/`minPause`/`maxConcurrentCheckpoints`/`barrierAlignmentTimeout` 等 11 配置项）。absence：Spark Trigger 等价的「作业级触发模式参数」不存在——连续流（record-at-a-time）模型 by design | Spark `Triggers.scala:57-125` 五态（SPS-8） |
| 17 可插拔异常处理策略 | 反序列化/处理/写出异常策略声明式选择，默认 fail-fast | 部分满足，一致（证据锚点如下） | 存在：异常类层级 `core/exceptions/{StreamException,StreamRuntimeException}.java`、`core/checkpoint/storage/CheckpointStorageException.java`、`cep/pattern/MalformedPatternException.java`（fail-fast 语义）；per-path skip-vs-fail 分工已裁定并文档化——checkpoint-design.md P1-09-01（ChannelState 恢复 best-effort 跳过 + `LOG.warn` 留证；state schema 漂移 fail-fast `ERR_STREAM_STATE_SCHEMA_MISMATCH`）。absence：`rg -l "ExceptionHandler\|LogAndContinue\|LogAndFail" nop-stream/` 零命中——作业级声明式策略选择不存在 | Kafka Streams `errors/{DeserializationExceptionHandler,LogAndContinueExceptionHandler,LogAndFailExceptionHandler,ProductionExceptionHandler}.java`（KS-4，`~/sources/kafka@7434a60c`） |
| 18 Standby 热备裁定 | 是否引入状态热备副本（缩短 failover 恢复窗口） | 未满足 → **修正**：需区分两层语义 | absence（task 级状态热备，KS StandbyTask 等价物）：无 standby task/changelog 复制概念，`failover-design.md` 全文以 global/region epoch recovery 为恢复模型。**存在（coordinator 级 HA 热备，P-REQ 报告未记录的补充事实）**：`runtime/coordinator/JobCoordinator.java` HA 模式 STANDBY（:163 注释、:436—469 启动注册、:565/:768/:822/:883/:971 G24/G25 五处「standby coordinator must never …」fencing 门禁）+ `TestJobCoordinatorStandbyStateMachine.java` 等测试。两层语义不同：控制面选主热备 ≠ 数据面状态热备 | Kafka Streams `processor/internals/StandbyTask.java` + changelog 恢复 10 变体（KS-5，`~/sources/kafka` `streams/.../internals/StandbyTask.java` live 复核存在） |
| 19 Interactive Query 一致性边界 | 状态查询接口（位置边界式一致性）或显式 exclude | 未满足，一致 | absence：`rg -l "StateQuery\|PositionBound\|InteractiveQuery" nop-stream/` 零命中；无状态查询 API | Kafka Streams `streams/src/main/java/org/apache/kafka/streams/query/{Position,PositionBound,Query,KeyQuery,MultiVersionedKeyQuery…}.java`（KS-6，live 复核存在） |
| 20 checkpoint 版本化与校验和 | 存储格式版本管理 + 写入校验和/原子性 | 部分满足 → **精化**（四分项核验） | ① 原子写 **landed**：`runtime/checkpoint/storage/LocalFileCheckpointStorage.java`（`.tmp` 后缀 :53 + `Files.move(…ATOMIC_MOVE)` :100/:403/:411/:500 四类产物）；`JdbcCheckpointStorage.java` 单事务原子 upsert（:642/:674/:701）。② segment 级 checksum+版本 **landed**（增量路径）：`core/checkpoint/StateSegmentDescriptor.java`（`checksum` :49 + `schemaVersion` :50 字段）+ checkpoint-design.md §2.6 Stage 31（`checksum=contentHash (SHA-256)`、`schemaVersion=1`），填充路径 `rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java`（SHA-256/contentHash 命中）。③ **manifest 级 checksum 与 stateFormatVersion absent**：`core/checkpoint/EpochManifest.java` 字段表（:24—37：epochId/jobId/pipelineId/timestamp/checkpointType/state/taskSnapshots/streamModelFingerprint/segments/sourceEnumeratorSnapshots）无 checksum、无 stateFormatVersion——而 checkpoint-design.md §2.6 字段表（:201 `stateFormatVersion`、:203 `checksum` manifest 完整性校验）承诺两者 → **设计-实现 drift**（见 §1.4）。④ SerializerFingerprint.schemaVersion 存在但恒 `DEFAULT_SCHEMA_VERSION=1`（`SerializerFingerprint.java:36-48`；design :1103 自述为预留分支）。⑤ 故障注入测试：存储实现级测试存在（`runtime/checkpoint/storage/Test{LocalFileCheckpointStorage,JdbcCheckpointStorage,EpochManifestPersistence,CheckpointSerDe*}.java`），但「部分写后重启可恢复」torn-write 注入用例未见 | Spark `CheckpointVersionManager.scala` / `ChecksumCheckpointFileManager.scala`（SPS-7） |
| 21 跨版本升级兼容测试基建 | 旧版本产物 → 新版本恢复的系统测试（或 defer + 版本策略） | 未满足，一致 | absence：`find nop-stream -iname "*upgrade*"` 零命中（无 upgrade-system-tests 等价物）；现有 `TestStateMigration` / `TestRocksDBStateMigration` / `TestStateMigrationEndToEnd` / `reshard/TestMaxParallelityReshardMigrationE2E` 均为**同版本**状态迁移机制测试，非跨版本兼容。前置事实：schemaVersion 恒 1、无已发布版本序列 | Kafka Streams `upgrade-system-tests-0110…` 26 版本模块（KS-8） |

### 1.2 README 三项声明的 live 事实核对

README 声明（`nop-stream/README.md:7`）：「尚未实现的是 K8s/YARN 集群部署编排与 HPA 弹性伸缩；`RuntimeTopology` 类仍处于概念阶段（0 Java 引用），不代表分布式能力缺失。」

| 声明项 | live 事实核对（命令与结果） | 结论 |
|--------|------|------|
| K8s/YARN 部署编排 absence | `rg -il "kubernetes\|yarn\|hpa\|horizontalpodautoscaler" nop-stream/`（排除 target）主代码命中 18 文件**全部为假阳性**——`hPa` 局部变量（`core/execution/PartitionRouter.java`、`HashPartitionRouter.java`、`cep/RichPattern{FlatSelect,Select}Function.java` 等，逐一 `rg -io` 复核仅 `hPa` 大小写不敏感命中）；仓库内无 Helm chart / deployment yaml（`find nop-stream -name "*.yaml" -o -name "*.yml"` 排除 target 零命中） | 声明属实：容器编排层代码与资产零存在 |
| HPA 弹性伸缩 absence | 同上（无 autoscaler/HPA 任何关联代码）；另见 §2.2 P-REQ-15 子裁定（vision non-goal「在线/自动 reshard」相关性） | 声明属实 |
| RuntimeTopology 0 引用 | 复核命令：`rg -l "RuntimeTopology" --type java nop-stream/` → **零命中（exit 1）**；全部命中为文档（`ai-dev/**`、`nop-stream/README.md`、design docs）。对照：README §快速开始管线描述「五层执行管线（…DeploymentPlan → RuntimeTopology）」与 design（`01-architecture-baseline.md:13,21,33,100,117,127` 将其列为第 6 阶段与 runtime 模块职责）与 live 不一致 → **设计-实现 drift**（见 §1.4） | 声明属实：0 Java 引用复核成立 |
| 应用层 vs 容器编排层分层事实基线 | 应用层集群管理 live 完备：`runtime/cluster/{JdbcClusterRegistry,JdbcLeaderElector,StreamNodeAutoRegistration,ClusterRegistry,InMemoryClusterRegistry}.java`；平台侧 `nop-sys/nop-sys-dao/.../elector/SysDaoLeaderElector.java`（含 `TestJobCoordinatorWithSysDaoLeaderElector` / `TestStreamNodeAutoRegistrationWithSysDaoNamingService` 集成测试）；`nop-cluster-core/.../discovery/{IDiscoveryClient,FilteredDiscoveryClient,ServiceInstance…}`；独立进程入口 `runtime/test/.../launch/{JobCoordinatorMain,TaskManagerMain}.java` + 多 JVM 测试基建 `runtime/test/.../multijvm/MiniStreamCluster.java` | tis 08-14d §C-2「应用层集群管理已完备、仅缺容器编排层」分层结论经 live 复核成立 |

### 1.3 竞品来源证据复核

- SeaTunnel Helm chart：`~/sources/seatunnel/deploy/kubernetes/seatunnel/`（Chart.yaml、values.yaml、templates/、conf/ + 同级 helm-tests/）——live 存在，P-REQ-15 K8s 参照依据成立。
- Kafka Streams standby/IQ：`~/sources/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/StandbyTask.java` 与 `.../streams/query/{Position,PositionBound,Query…}.java`——live 存在，P-REQ-18/19 参照依据成立。
- 三份 primary 报告（SeaTunnel/Spark SS/KS）与 P-REQ 报告 §1.1 的 SHA 链引用一致，本报告直接引用不重开分析。

### 1.4 设计文档承诺索引（与 P-REQ-13..21 相关，逐条标注与 live 行为一致 / drift）

> 范围：16 份设计文档（`ai-dev/design/nop-stream/`，`ls *.md | wc -l` = 16）。仅索引与本 plan 裁定相关的承诺/约束/non-goals。

| 设计文档 | 相关承诺/约束 | 与 live 一致性 |
|---------|--------------|----------------|
| `00-vision.md` | §四 non-goals：双流 Join、SQL API、PB 级大规模并行、复制 Flink Runtime 结构、**在线/自动 reshard（运行时自动重分片）**、异步算子；§三约束 7「最小控制面」；§八不变量 1—15 | 一致（non-goals 均未被 live 违反；「在线/自动 reshard」与 HPA 子裁定直接相关，见 §2.2） |
| `01-architecture-baseline.md` | :13 六阶段管线 `…DeploymentPlan → RuntimeTopology`；:21/:33 runtime 模块职责含「RuntimeTopology」；:127 RuntimeTopology 定位「运行时实例视图：attempt、心跳、通道状态、checkpoint 进度。可重建，不允许反向生成状态路径或分区规则」 | **drift**：RuntimeTopology 0 Java 引用（§1.2），管线实际止于 DeploymentPlan；运行时实例视图职能由 `ClusterRegistry`/`RuntimeNode`/liveness 承担 |
| `checkpoint-design.md` | §2.6 manifest 字段表 :201 `stateFormatVersion`、:203 `checksum`（manifest 完整性校验）；§2.6 segments（Stage 31，checksum=contentHash SHA-256 + schemaVersion=1）；:389 P1-09-01 skip-vs-fail-fast 分工；:1002 指纹比对 + 快速失败策略（拒绝 Flink 四态兼容模型）；:1103 schemaVersion 恒 1 预留说明；§10 可观测性契约 | **部分 drift**：segments 承诺一致（StateSegmentDescriptor live）；**manifest 级 `stateFormatVersion`/`checksum` 未落地**（EpochManifest 字段缺失）；schemaVersion 恒 1 与 :1103 自述一致 |
| `connector-design.md` | §4 Split-based Source 协议（FLIP-27 风格：核心契约/动态 split/Assignment Recovery）；§5 连接器汇总；无 dry-run/连通性校验承诺 | 一致（无未兑现承诺；P-REQ-13 属新增能力候选，非契约缺口） |
| `failover-design.md` | Targeted/region failover 裁定体系；§2.3 唯一恢复入口 globalRecovery()；§七 region 与 global epoch recovery 关系 | 一致（无 standby/changelog 概念——P-REQ-18 裁定的设计基线） |
| `window-design.md` | 窗口四要素（WindowAssigner + Trigger + Evictor + WindowFunction）、WindowingStrategy | 一致（Trigger 家族 live 11 类，见 §1.1 P-REQ-16） |
| `state-management-design.md` | StateShard 路由、序列化策略、State Segment | 一致 |
| `stream-dsl-design.md` | 三入口合一（XDSL/Java API/Delta）→ 同一 StreamModel；XDef 合同 | 一致（`stream.xdef` live，P-REQ-14 校验链路证据） |
| `core-design.md` / `graph-model-design.md` / `mailbox-design.md` / `time-model-design.md` / `cep-design.md` / `comparison.md` / `component-roadmap.md` / `README.md` | 与 P-REQ-13..21 无直接承诺关联（模块职责/管线转换/时间模型/CEP/对比/组件路线） | 一致（未发现 drift；README.md §1.1 模块表与 live 模块清单一致，含 connector-jdbc/rocksdb 等实际模块） |

**Drift 汇总（2 项，均为「设计承诺未落地」而非「live 违反设计」）**：

1. **D-DRIFT-1（RuntimeTopology）**：00-vision §九 / 01-architecture-baseline :13/:21/:33/:100/:117/:127 / README:5 将 RuntimeTopology 表述为管线第 6 阶段与 runtime 职责；live 0 Java 引用。处置：P-REQ-15 子裁定（§2.2）+ 设计文档修正建议（不在本 plan 顺手重写，见 §3.4）。
2. **D-DRIFT-2（EpochManifest 字段）**：checkpoint-design §2.6 承诺 manifest 级 `stateFormatVersion` + `checksum`；live `EpochManifest.java` 两者均缺。处置：P-REQ-20 裁定（§2.2）。

## Phase 2 — D-GAP 逐条裁定

> 三态：`go`（采纳，含归属执行项）/ `defer`（附 defer 条件与 revisit 触发点）/ `exclude`（附竞品/设计依据与重启路径）。每条裁定自包含：要求（复述）+ 现状（§1.1 锚点）+ 裁定 + 依据 + 归属。

### 2.1 P-REQ-13..21 逐条三态裁定表

| P-REQ | 裁定 | 依据（Phase 1 证据 + 竞品来源） | 归属执行项 / defer 条件与 revisit 触发点 |
|-------|------|------|------|
| **13 连接器 dry-run 连通性验证**（P1） | **go** | nop-stream 零覆盖且设计无承诺（§1.1，属新增能力而非契约缺口）；SeaTunnel ST-2 源码级参照（`ConnectorCheckCommand`/`DryRunConnectValidator`）；与既有 XDef 模型结构校验（`stream.xdef`）互补——模型结构错误已字段级报错，缺的是连接器连通性/凭据可达性的提交前校验；「提交前 fail-fast」是 D1 API/DX 产品化应有项 | **新 Follow-up 工作项 20**（与 P-REQ-14 合并为「作业提交前校验产品化」单一工作项，见 §3.3） |
| **14 凭据加密与配置校验入口**（P1） | **go** | 平台侧 `nop-credential` 九模块（含 kms-vault）live 而 nop-stream 零集成（§1.1）——属平台设施接入增量非新建；XDef 字段级校验链路已有（结构错误已到选项级），缺独立 conf-validate 命令（不启动作业即报错）与凭据加密存储接入；SeaTunnel ST-6 参照（`EncryptConfigServlet`/`SeaTunnelConfValidateCommand`/`OptionRule`） | **新 Follow-up 工作项 20**（同上，单一载体） |
| **15 K8s/YARN 部署编排与 HPA**（P0，决策必答） | 三项子裁定，见 §2.2（K8s：defer；YARN：exclude；HPA：exclude） | 见 §2.2 | 见 §2.2 |
| **16 触发/触发语义一等化核对**（P2） | **go**（文档化交付） | 覆盖面核对结论（§1.1）：作业级触发参数（Spark `Triggers.scala` 五态等价物）不存在且**不需要**——连续流（record-at-a-time）模型 by design，micro-batch 触发节奏参数对该模型不适用，非缺口；既有触发语义全集 = 窗口级 Trigger 家族 11 类 + `CheckpointConfig` 11 配置项（checkpointInterval/minPause/…）+ processing-time timer + DRAIN truncation；剩余缺口仅在「文档化」（用户视角无映射表）；与 vision 无冲突（vision 从未承诺作业级触发参数） | **item 17**（文档产品化）：用户指南补「触发语义映射表」（既有机制 → 竞品概念对照），供 Spark/SeaTunnel 用户迁移参照 |
| **17 可插拔异常处理策略**（P1） | **defer** | 现状非无主空白：per-path skip-vs-fail 语义已显式裁定并文档化（checkpoint-design P1-09-01：恢复路径 best-effort 跳过 + `LOG.warn` vs schema 漂移 fail-fast `ERR_STREAM_STATE_SCHEMA_MISMATCH`），默认 fail-fast 与平台错误处理两级策略一致；缺的是「作业级声明式策略选择」（`ExceptionHandler` 等价物零命中，§1.1）；KS-4 参照主要服务于库形态宿主集成场景 | defer 条件：Phase S 复合场景（items 13/14）出现真实声明式 skip-vs-fail 需求（如 CDC poison record、CEP 匹配异常隔离）时升级为 go 并立 Follow-up；revisit 触发点 = item 14 closure audit / item 18 最终验收前复核 |
| **18 Standby 热备副本**（P1，决策型） | **exclude** | ① 恢复窗口缩短已有三重 shipped 机制：region failover（缩小恢复面）+ RocksDB 增量快照（只传 SST）+ unaligned checkpoint（降低 barrier 传播延迟）② standby task 需 changelog 复制通道，nop-stream 为快照制无 changelog 概念（failover-design 全文以 epoch recovery 为恢复模型，§1.4），引入属架构级新增，与 vision §三约束 7「最小控制面」张力大 ③ 控制面热备已有等价物：`JobCoordinator` HA STANDBY（G24/G25 五处 fencing 门禁 live，§1.1 修正记录）——KS standby 服务的库形态约束（无 per-job coordinator）对 nop-stream 不成立 ④ 无 demonstrated user need。重启路径：item 15 演练若测得状态恢复窗口超出 SLO，可凭数据重启本裁定 | 无（exclude；重启路径如左） |
| **19 Interactive Query 一致性边界**（P2，决策型） | **exclude** | ① IQ 是 KS 库形态核心卖点（外部工具直读本地状态，`query/` 包 live 复核存在），nop-stream 引擎形态下状态不在用户查询路径上，查询需求由平台数据侧承担（GraphQL/DB）② 无状态查询 API 亦无消费者（§1.1 零命中）③ position 边界式一致性约束（`Position`/`PositionBound`）随本裁定记录为未来重启的准入前提 | 无（exclude；重启前提如左） |
| **20 checkpoint 版本化与校验和**（P1） | **go**（窄增量） | 四分项核验（§1.1）：原子写 landed（双存储实现）+ segment 级 checksum/schemaVersion landed（增量路径）+ SerializerFingerprint.schemaVersion 恒 1（设计自述预留）——缺 **manifest 级 `stateFormatVersion`/`checksum`**（checkpoint-design §2.6 :201/:203 承诺而 `EpochManifest.java` 缺失，即 D-DRIFT-2）与 **torn-write 故障注入测试**；属「设计承诺未落地」收敛而非新需求；Spark SPS-7 参照（`CheckpointVersionManager`/`ChecksumCheckpointFileManager`） | **item 8**（runtime 审计，plan 已 active）：审计重点清单含本项（§3.1）——先核对设计意图定方向（补字段落地 vs 修正设计字段表消除 drift），字段+读写+测试若属就地修复范围则 item 8 内完成，否则 item 8 裁定转 Follow-up；torn-write 故障注入测试补齐同归 |
| **21 跨版本升级兼容测试基建**（P1） | **defer** | ① 当前 schemaVersion 恒 1、无已发布版本序列（§1.1）——跨版本升级测试基建先行**无测试对象**（旧版本产物不存在）② KS 26 个 upgrade-system-tests 模块是十年发布史积累 ③ P-REQ-20 的 go 裁定（版本字段落地）是它的结构前置 | defer 条件：nop-stream 首个对外发布版本出现、或首次状态/checkpoint 格式破坏性变更（版本字段首次递增）时建立升级路径自动化测试；revisit 触发点 = 首次格式版本递增 PR / 首个外部部署版本 / item 18 验收前复核 |

### 2.2 P-REQ-15 三项子裁定（README 声明正式裁定，P0 必答）

| 子项 | 裁定 | 依据 |
|------|------|------|
| **K8s 集群部署编排** | **defer** | ① 分层事实（§1.2 live 基线）：应用层集群管理完备（发现/选主/注册/独立进程入口/多 JVM 测试），K8s 编排属**打包交付层**增量（SeaTunnel Helm chart 参照：JobCoordinatorMain/TaskManagerMain 容器化 + JDBC 后端前置 + chart/values），非引擎能力缺口——README 明示「不代表分布式能力缺失」② 成本可控但当前无容器化部署的用户需求证据（roadmap Phase S 演练以 `MiniStreamCluster` 多 JVM 为验证基线，不依赖 K8s）③ defer 期间 README 声明继续成立（诚实声明，非漂移）。defer 条件：出现第一个要求容器化部署的试用/生产用户，或 item 15 演练需要 K8s 环境时升级为 go 并立 Follow-up；revisit 触发点 = item 18 最终验收前复核一次 |
| **YARN 部署编排** | **exclude** | ① YARN 属 Hadoop 世代部署面；Nop 平台技术栈（IJdbcTemplate/IMessageService/GraphQL、云原生方向）无 Hadoop 亲缘，目标用户重叠度极低 ② 二进制打包/节点标签/队列模型的维护成本无对应收益 ③ SeaTunnel/Spark 保留 YARN 源于存量用户基数，nop-stream 无存量负担。无重启路径诉求（如未来 Hadoop 生态用户出现，按 roadmap Rules 以 Follow-up 新工作项立项，不修改本裁定） |
| **HPA 弹性伸缩** | **exclude** | ① **直接约束**：00-vision §四显式 non-goal「在线/自动 reshard（运行时自动重分片）」——有状态流任务的 HPA 扩缩必然触发运行时重分片，exclude 是该 non-goal 的必然推论（若未来 go HPA 必须先走 vision §六决策点流程修订 non-goal，即 stop-edit-restart）② 状态局部性 + fencing + key-group 路由使弹性伸缩语义复杂：KS 库形态内建无 autoscaler；Flink 的 Reactive Mode/Autoscaler 为独立项目且依赖 rescale 协议（nop-stream 仅支持 restore 时 rescale + 显式离线 reshard，§1.4 vision 表）③ 既有容量变更路径（restore 时 parallelism rescale + `MaxParallelismReshardMigration` 离线工具）已覆盖需求。重启路径：vision non-goal 修订后重启裁定 |
| **RuntimeTopology 概念类** | **exclude**（概念退役） | ① 0 Java 引用复核成立（§1.2：`rg -l "RuntimeTopology" --type java nop-stream/` exit 1）② 运行时实例视图职能（attempt/心跳/通道状态/checkpoint 进度）已由 `ClusterRegistry`/`RuntimeNode`/liveness + DeploymentPlan 承担 ③ 设计定位本就是「可重建视图、不允许反向生成语义」（01-architecture-baseline :127）——非语义载体，无已实现消费者，保留概念仅产生持续设计-实现 drift（D-DRIFT-1）④ README 已诚实声明概念阶段。附带动作：设计文档修正建议（00-vision §九/01-architecture-baseline 管线描述/README 管线叙述移除或改注「概念已退役」）记录于 §3.4，由 item 17 文档产品化收口（本 plan Out Of Scope 不顺手重写） |

### 2.3 裁定一致性检查（non-goals 与设计承诺）

- **vs 00-vision non-goals**：9 条裁定 + 3 条子裁定均不与 non-goals 冲突。特别核对：HPA exclude 是 non-goal「在线/自动 reshard」的必然推论（非冲突，是执行）；K8s defer/YARN exclude/RuntimeTopology exclude 均不在 non-goals 清单内且无承诺冲突；P-REQ-16 的「作业级触发参数不需要」结论与 vision 连续流定位一致（vision 从未承诺 micro-batch 触发参数）。
- **vs 设计承诺**：P-REQ-20 go 直接收敛 D-DRIFT-2（checkpoint-design §2.6 承诺）；P-REQ-17 defer 与 checkpoint-design P1-09-01 已裁定语义一致；P-REQ-18 exclude 与 failover-design 恢复模型一致；P-REQ-13/14 为新增能力（无设计承诺冲突）。
- **表面张力处置**：RuntimeTopology exclude 与 00-vision §九/01-architecture-baseline 管线叙述存在表述张力——该表述本身即 D-DRIFT-1（设计文档描述与 live 不符），裁定不与 **non-goals** 冲突（RuntimeTopology 不在 non-goals 清单），处置为 §3.4 设计文档修正建议，无需 stop-edit-restart。
- **结论：无裁定需要 stop-edit-restart 才能成立**（唯一 stop-edit-restart 输出为 F-2 升级建议，属 roadmap item 16 语义调整建议，见 §2.4）。

### 2.4 F-2 / F-3 消化裁定

| 项 | 内容 | 消化结论 |
|----|------|---------|
| **F-2** | item 16 语义追加「告警渠道闭环（AlertChannel，复用 nop-integration/nop-message 渠道抽象）」（P-REQ-12 载体，P1） | **升级为 stop-edit-restart 触发建议（本节即唯一记录点，提请建议人/mission owner 执行）**。理由：P-REQ-12 为 P1（tis 源码级证据：DingTalk/WeCom/Lark/Email/Http 渠道插件 + 作业轮询告警），若 item 16 语义不调整则该 P1 在 roadmap 无承载；roadmap Rules 明确 Follow-up 追加无法承载既有 item 语义变更——stop-edit-restart 是唯一合规路径。执行内容：item 16 文本追加「告警渠道闭环（AlertChannel，复用 nop-integration/nop-message 渠道抽象）」；过渡期（stop-edit-restart 执行前）P-REQ-12 归属维持 item 16（P-REQ 报告 §2.6 已记载该过渡安排）。本条即 P-REQ-12 归属处置的唯一记录点，其余条目不再重复裁定 |
| **F-3** | item 15「稳定性与性能演练」吸收 P-REQ-21 执行面 | **维持建议级**。理由：P-REQ-21 经 §2.1 裁定为 defer（无已发布版本、无测试对象——KS-8 原文即「item 6 裁定 → 执行可落 14/15」的前置条件不满足），item 15 现阶段吸收其执行面无实际对象；待 defer revisit 触发（首次格式版本递增/首个发布版本）时再评估执行面归属（届时可落 item 15 或独立 Follow-up）。建议保留为历史素材，不升级 |

### 2.5 P-REQ-1..11 归属修正扫描（item 16 裁剪输入的前置）

- **结论：经扫描无归属修正建议**。逐条核对：P-REQ-1..8、10、11 ↔ item 16「可观测性与运维产品化」语义匹配；P-REQ-9（Web 控制台，P2）↔ item 16 裁定范围（P-REQ 自身验收即允许「交付或显式裁定排除」）；P-REQ-12 ↔ 过渡期归属 item 16，正式承载依赖 F-2 stop-edit-restart 执行（引用 §2.4 结论，不重复裁定）。
- 补充观察（非归属修正，供 item 16 plan 参考）：P-REQ-10 作业状态重置工具与既有离线 reshard 工具（`runtime/checkpoint/reshard/MaxParallelityReshardMigration.java`）职能邻接，item 16 设计时可考虑 CLI 工具入口收敛（重置/reshard/savepoint 同一入口族），避免两套运维工具体系。

## Phase 3 — 下游输入输出与 roadmap 写回

### 3.1 Phase M 审计重点清单（items 7—11 逐模块）

> 消费本报告裁定中 go/核对型条目；各 item plan 起草/执行时直接引用本节。

| Item（模块） | 审计重点增量（D-GAP 派生） | 派生来源 |
|--------------|---------------------------|---------|
| **7 core** | ① `SerializerFingerprint.schemaVersion` 恒 1 预留分支的 core 侧核对（类型定义与指纹传播路径，与 item 8 联动）；② 窗口 Trigger 家族 11 类行为一致性审计需产出「可直接引用为 item 17 触发语义映射表的行为级证据」；③ core 侧 per-path skip-vs-fail 语义实现一致性（`ChannelState.fromSerializableForm` 在 core 的 best-effort 跳过 + `LOG.warn` 路径，为 P-REQ-17 defer 裁定提供实现级锚点） | P-REQ-20/16/17 |
| **8 runtime** | ① **P-REQ-20 窄增量（go 裁定归属）**：D-DRIFT-2 方向裁定（补 manifest `stateFormatVersion`/`checksum` 字段落地 vs 修正 checkpoint-design §2.6 字段表）→ 就地修复（字段+读写+测试）或转 Follow-up；原子写复核（`LocalFileCheckpointStorage` ATOMIC_MOVE 四类产物 + `JdbcCheckpointStorage` 原子 upsert）；torn-write 故障注入测试（部分写后重启可恢复）补齐；② `JobCoordinator` STANDBY G24/G25 五处 fencing 门禁的行为级验证覆盖（K8s defer 裁定所依赖的「应用层完备」基线 freshness 复核） | P-REQ-20 / P-REQ-15 |
| **9 cep** | **无额外重点，按既有审计模式执行**（D-GAP 裁定条目不触及 CEP 子系统；`MalformedPatternException` fail-fast 路径属既有异常层级审计范围） | — |
| **10 connectors** | ① P-REQ-13/14 go 裁定的接口落点预核：Source/Sink 契约（SourceWorkUnit/FLIP-27 协议、`SinkFunction`/`TwoPhaseCommitSinkFunction`）上「连通性校验钩子」的存在性与最小侵入点——审计需输出**候选钩子清单**（供 Follow-up item 20 plan 直接消费），非仅缺陷清单；② 各连接器配置 bean 的 XDef/字段校验覆盖现状（是否全部走 `stream.xdef` 校验链路） | P-REQ-13/14 |
| **11 rocksdb/flow/fraud-example** | ① `RocksDBIncrementalSnapshotStrategy` segment `checksum`/`schemaVersion` 实际填充核验（每个增量 segment 是否都带有效 SHA-256 contentHash——P-REQ-20「segment 级 landed」结论的端到端真实性）；② flow 模块 XDef 校验链路对连接器配置段的字段级校验完备性；③ fraud-example 作为快速起步脚手架（P-REQ-25，item 17 输入）的完整度评估：现有示例 + 3 个入门拓扑缺口清单 | P-REQ-20/14/25 |

### 3.2 Phase S 场景约束输入（item 12 场景设计必须遵守的裁定边界）

1. **P-REQ-17 defer 边界**：场景不得依赖「声明式异常策略选择」——异常处理按既有 per-path 语义（默认 fail-fast + 恢复路径 P1-09-01 best-effort 跳过）；场景执行中若发现 poison-record 类需求，记录为 P-REQ-17 revisit 证据（升级 go 的触发材料），不得在场景内私造策略机制。
2. **vision non-goal「在线/自动 reshard」**：S2 的 rescale 验证必须走 restore 时 parallelism rescale 或显式离线 reshard 工具（`MaxParallelityReshardMigration`）路径，不得设计运行时自动重分片场景。
3. **P-REQ-18/19 exclude 边界**：场景不得包含 standby 热备组件或状态查询接口。
4. **P-REQ-20 边界**：checkpoint 验证断言以「最新 durable epoch manifest 恢复 + exactly-once 结果」为准（既有语义）；manifest 级 checksum 字段落地前不得断言 manifest 完整性校验行为。
5. **P-REQ-15 defer（K8s）边界**：分布式验证矩阵以 `MiniStreamCluster` 真实多 JVM 为基线（roadmap Cross-cutting concerns 既定规则），不引入 K8s 依赖。
6. **语义不降级**（vision §三约束 4/§八不变量 7）：S1 声明 STRICT_EXACTLY_ONCE 的前提 = CDC source 可重放（offset checkpoint）+ 2PC JDBC sink 严格提交；S2 文件 sink 走 `FileTwoPhaseCommitSink`。

**S3 派生裁定：S3 不派生，维持 S1/S2**。理由：D-GAP 的 go 项中 P-REQ-13/14（提交前工具）、P-REQ-16（文档）、P-REQ-20（存储增量）均不构成新运行场景；defer/exclude 项无场景验证面；S1（CDC → CEP → 窗口聚合 → 2PC JDBC sink）+ S2（文件 source → keyBy 聚合 + Delta 定制 → 文件 sink + rescale）已覆盖 D-GAP 相关运行语义（checkpoint 恢复 / exactly-once / rescale 边界）。可选增强：Follow-up item 20 落地后，把「dry-run 校验 S1 拓扑」作为 S1 的提交前步骤（非独立场景）。

### 3.3 item 16 裁剪输入（P-REQ-1..12 初步裁剪建议表）

> 初步建议仅供 item 16 自身 plan 起草参考，**正式裁定属 item 16 plan**。

| P-REQ | 优先级 | 初步建议 | 一句依据 |
|-------|--------|---------|---------|
| 1 指标标准集与分层模型 | P0 | 交付（go） | P0 核心：无分层注册体系则 P-REQ-3/4/8 全部失去数据源 |
| 2 作业进度事件监听 API | P1 | 交付（go） | 低成本高杠杆：监听接口 + 日志内建实现即可满足验收 |
| 3 Prometheus/OpenMetrics 暴露 | P0 | 交付（go） | D4 最大空白；优先探测复用平台既有 metrics/prometheus 设施（roadmap「优先复用平台设施」） |
| 4 metrics 配置模板开箱 | P1 | 交付（go） | 随 P-REQ-3 顺手交付（模板文件成本低，Spark 22 项参照） |
| 5 REST 运维 API | P0 | 交付（go，建议分期：submit/stop/running-jobs 生命周期端点优先，thread-dump 诊断端点后置） | P0；SeaTunnel 30+ 端点是上限参照非下限 |
| 6 checkpoint 运维观测 | P1 | 交付（go） | `CheckpointMetrics` 单点已存在，增量是 overview/history 查询 + failureCause 字段（Flink deep-dive 并入项） |
| 7 流作业逻辑健康状态机 | P0 | 交付（go） | KS 报告核心命题：引擎形态取「逻辑健康信号面」（七态参照按 nop-stream 语义裁剪） |
| 8 RocksDB 指标 recorder | P1 | 交付（go） | 依赖 P-REQ-1 注册体系先行；KS-3 源码级参照 |
| 9 Web 控制台/流式页签 | P2 | **defer**（条件：REST + 指标落地后按用户反馈裁定） | 验收标准自身允许「交付或显式裁定排除」；defer 保留 AMIS 低成本实现路径，比 exclude 保守 |
| 10 作业状态重置工具 | P1 | 交付（go；与离线 reshard 工具做入口收敛设计，见 §2.5 观察） | KS-7 参照；StreamsResetter 语义映射 = 清本地状态 + 重置输入位点 |
| 11 历史作业与日志生命周期治理 | P1 | 交付（go） | 配置项级交付（保留时长/滚动/定期清理），成本低（SeaTunnel `history-job-expire-minutes` 参照） |
| 12 告警与事件外发通道 | P1 | 交付（go；正式归属依赖 F-2 stop-edit-restart 执行——过渡期归属 item 16） | P1 且无其他承载；复用 nop-integration/nop-message 渠道抽象（tis 源码级参照） |

### 3.4 roadmap 修正记录

- **Follow-up item 20 追加**（P-REQ-13/14 go 裁定载体，按 roadmap Rules 追加到 Work Items 末尾、编号顺延 item 19 之后、状态 todo、来源标注本 plan）：「作业提交前校验产品化：连接器 dry-run 连通性验证（SourceWorkUnit/Sink 契约校验钩子，消费 item 10 审计的候选钩子清单）+ 凭据加密接入（nop-credential，含 kms-vault）+ conf-validate 独立校验命令（不启动作业即字段级报错）」。头部 Last updated 已同步。
- **设计文档修正建议**（不属 roadmap 工作项，供 item 17 文档产品化收口，本 plan Out Of Scope 不顺手重写）：① D-DRIFT-1——`00-vision.md` §九 / `01-architecture-baseline.md` :13/:21/:33/:100/:117/:127 / `nop-stream/README.md` 管线叙述中 RuntimeTopology 移除或改注「概念已退役（2026-09-01 D-GAP 裁定）」；② D-DRIFT-2——`checkpoint-design.md` §2.6 manifest 字段表随 item 8 的 P-REQ-20 方向裁定同步（补字段落地则文档维持，修设计则改字段表）。
- **其余无 roadmap 修正需要**（显式记录）：K8s defer / YARN、HPA、RuntimeTopology、standby、IQ 的 exclude 均不修改既有 items——README 声明在 defer/exclude 裁定下继续成立（诚实声明）；F-2 为 stop-edit-restart 建议（§2.4），本 plan 不直接改 item 16。

## Conclusion

- **D-GAP 裁定全量落地**：P-REQ-13..21 九条 + README 三项声明（K8s/YARN 编排、HPA、RuntimeTopology）全部完成三态裁定且依据可追溯（live 证据锚点 §1 + 竞品源码复核 §1.3）。逐条：P-REQ-13 **go**、14 **go**（→ Follow-up item 20）、15 混合（K8s **defer** / YARN **exclude** / HPA **exclude** / RuntimeTopology **exclude**（概念退役））、16 **go**（文档化 → item 17）、17 **defer**、18 **exclude**、19 **exclude**、20 **go**（窄增量 → item 8，收敛 D-DRIFT-2）、21 **defer**。
- **现状修正 2 处**（P-REQ 报告记载值精化，§1.1）：P-REQ-18 补充 coordinator 级 HA STANDBY 存在事实（与 task 级状态热备区分）；P-REQ-20 拆四分项（原子写/segment 级 checksum landed，manifest 级字段 absent，torn-write 注入测试未见）。**设计-实现 drift 2 项**（D-DRIFT-1 RuntimeTopology 管线叙述、D-DRIFT-2 EpochManifest 字段）记录并派生修正建议。
- **F-2/F-3 消化**（§2.4）：F-2 **升级为 stop-edit-restart 触发建议**（提请 mission owner 执行：item 16 追加「告警渠道闭环」语义；过渡期 P-REQ-12 归属 item 16）；F-3 **维持建议级**（P-REQ-21 defer 后无吸收对象）。一致性检查：全部裁定与 00-vision non-goals 及设计承诺无冲突（HPA exclude 是 non-goal「在线/自动 reshard」的必然推论）。
- **下游输入齐备且自包含**：§3.1 Phase M 逐模块重点（items 7/8/10/11 有增量重点，item 9 显式「无额外重点」）；§3.2 Phase S 约束 6 条 + S3 不派生裁定；§3.3 item 16 裁剪建议 12 条（go×10 / defer×1 / 依赖 F-2×1）。
- **被否决的方案**：为 HPA 引入运行时 rescale 协议（否决原因：vision 显式 non-goal + 竞品实践依赖项目级独立 rescale 协议体系）；在本 plan 内直接重写设计文档消除 drift（否决原因：plan Out Of Scope 禁止结构性重写，只能记录裁定依据与修正建议）；将 P-REQ-13/14 归属 item 17（否决原因：item 17 是文档产品化，dry-run/凭据接入是代码交付，语义不匹配——故立 Follow-up item 20）。
- **后续工作**：items 7—11 审计 plans 与 item 12/16 plans 落地时直接引用本报告（自包含，无需回读 P-REQ 报告）；F-2 stop-edit-restart 提请建议人执行；roadmap Follow-up item 20 已落库待调度。

## References

- `ai-dev/backlog/nop-stream-productization-roadmap.md`（item 6 / items 7—18 / Rules）
- `ai-dev/analysis/2026-09/2026-09-01-competitor-productization-synthesis-and-p-req.md`（P-REQ-13..21 定义与 §2.6 抽查记录）
- `ai-dev/design/nop-stream/`（16 份设计文档）
- `nop-stream/README.md`
- 竞品源码复核：`~/sources/seatunnel@5dbfb374`（deploy/kubernetes/seatunnel）、`~/sources/kafka@7434a60c`（StandbyTask、query 包）
