# 维度 09：文档-代码一致性 + 跨模块契约（nop-stream，盲审 R6）

- 审计日期：2026-10-06
- 审计对象：HEAD live code + docs-for-ai/nop-stream 文档族（6 份）+ nop-stream/README.md + nop-stream/quickstart/
- 审计人：独立盲审 subagent（未读 ai-dev/audits|plans|bugs|lessons|logs 任何文件；ai-dev/design/ 仅按 docs 边界规则未直接消费其内容，未以之为证据）
- 方法：逐条可检验声明 → grep/read live code 核对（API 签名、配置键与默认值、错误码、能力边界、文件/测试锚点、命令可执行性）→ 同类漂移深挖第 2 轮。

## 审计范围与方法

**文档面（逐份核对）**：
1. `docs-for-ai/03-modules/nop-stream.md`（owner doc：指标名表 / 事件 / REST / 配置键 / 维护工具 / 凭据 / 健康状态机 / 告警 / 治理 / 运维手册）
2. `docs-for-ai/03-modules/nop-stream-user-guide.md`（DataStream API / XDSL / 连接器指引 / 分布式 / 触发语义映射）
3. `docs-for-ai/03-modules/nop-stream-connectors.md`（SPI 注册中心 / 探测 / 能力矩阵 / 并行 2PC）
4. `docs-for-ai/03-modules/nop-stream-cdc-cookbook.md`（Debezium 机制 / offset / schema / 故障表 / dmlMode）
5. `docs-for-ai/03-modules/nop-stream-migration-guide.md`（三条版本轴 / reshard / 台账 v2 / 反序列化白名单）
6. `docs-for-ai/03-modules/nop-stream-sql.md`（`<sql>` 元素 / 纳入面 / 错误码 / 编程入口）
7. `nop-stream/README.md`、`nop-stream/nop-stream-cep/README.md`、`nop-stream/nop-stream-fraud-example/README.md`、`nop-stream/quickstart/`（README + generate.sh + verify.sh + template/）

**核对通过的代表性声明（抽样列举，均与 live 一致）**：
- 指标名表全部 24 项（engine/task/operator/io/state + `numLateRecordsDropped` 命名例外）与 `EngineMetrics`/`TaskNodeMetrics`/`MicrometerStreamTaskMetrics`/`ChannelQueueGauges`/`RocksDBMetricsRecorder` 常量逐一相等；`commitQueueDepth`/`commitCallerRuns.total`/`ackSendFailures.total` 存在且语义注释一致。
- 事件类型 10 枚举、负载字段（jobId/type/timestamp/checkpointId/durationMs/sizeBytes/cause）与 `StreamJobEvent` 一致；`LoggingJobEventListener` 前缀 `nop-stream job event:`、`job health transition:`、`nop-stream alert:` 三类日志锚点均在 main 代码。
- REST 面：`/metrics`（TextFormat 0.0.4 / OpenMetrics 协商）、`/jobs` GET/POST、`/jobs/{id}/stop?mode=`（未知 mode 400 BAD_STOP_MODE）、`/jobs/{id}`、`/jobs/{id}/checkpoints`、`/jobs/{id}/threaddump`、TM 侧 503 `REGISTRY_UNAVAILABLE`/`SUBMIT_UNAVAILABLE`/`STOP_UNAVAILABLE`、404 `JOB_NOT_FOUND`、401 `UNAUTHORIZED`、F-09a 前缀守卫（`java./javax./jakarta./jdk./sun./com.sun.` + `[`）与 `Class.forName(name,false,loader)` 均在 `StreamOpsHttpServer`/`OpsJobManager` 实证。
- 配置键与默认值：`StreamOpsConfig`（enabled=false/port=8901/bind=127.0.0.1/metrics.enabled=true/token 非法 bind fail-fast）、`StreamGovernanceConfig`（100/1440/1440/300000）、`AlertService.fromProperties` + `WebhookAlertChannel`（5000/2/200）、`StreamMetricsReporter`（4 键 + ATOMIC_MOVE）、`metrics.properties.template` 存在且含 JMX/PushGateway 备忘。
- `CheckpointConfig` 全部 13 个默认值与 user-guide 表逐项相等；env 默认 parallelism=1、watermark 200ms；`execute` 一次性守卫存在。
- 版本轴：`CheckpointFormatVersions.CURRENT_FORMAT_VERSION=2`、`RocksDBKeyEncoder.KEY_LAYOUT_VERSION=2`（legacy 1）、`SerializerFingerprint.DEFAULT_SCHEMA_VERSION=1`；`ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH`/`_FORMAT_VERSION_UNSUPPORTED`/`ERR_STREAM_CLASS_NOT_ALLOWED`/`ERR_STREAM_STATE_SCHEMA_MISMATCH`/`ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED` 均定义于 `NopStreamErrors`；已废除的 `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED` 在 live code 全仓 grep 为空（与「随门禁移除删除」声明一致）。
- 能力边界：`DataStream.union(DataStream...)` 存在；`getSideOutput` 提取 API + `SideOutputDataStream` 对 map/filter/keyBy/setParallelism 抛 `ERR_STREAM_UNSUPPORTED`；XDSL `<sideOutput>` fail-fast（错误码漂移见 DC-01）；`KeyedStreamImpl.countWindow` 默认接线（PurgingTrigger∘CountTrigger / CountTrigger+CountEvictor）与矩阵一致；assigners 目录含 8 个窗口 assigner + `GlobalWindows.NeverTrigger`；triggers 目录恰 11 文件（含包私有 `ContinuousIntervalTrigger`）；CEP×RocksDB 在 `CepOperator`（`stateBackend.getClass().getName().contains("RocksDB")` → typed 拒绝）实证 README「CEP 限 Memory 后端」。
- 连接器：8 个 SPI 工厂类 + 4 个 `connector-*.beans.xml`（无全局 app 容器泄漏，beans 注释自证）；`StreamConnectorCatalog` API（`of`/`discoverConnectorBeansResources`/`renderListing`/`probe`/`probeConnectivity`）签名一致；一致性声明（FileSource AT_LEAST_ONCE、MessageSourceFunction AT_LEAST_ONCE、Debezium REPLAYABLE、File/JDBC sink TWO_PHASE_COMMIT、BatchConsumer IDEMPOTENT）逐一实证；`copyForSubtask(int)` 基类默认抛 `ERR_STREAM_UNSUPPORTED`；台账 v2 DDL 主键 `(sink_namespace, epoch_id, subtask_id)`（`getLedgerTableDDL`）。
- Debezium：版本 3.7.0.Final（nop-message-debezium/pom.xml）、connectorType 白名单 mysql/postgres（`DebeziumEngineConfig.getConnectorClass` fail-fast）、`record.processing.threads` 钉 1、offset.storage 覆盖拒绝、`cdc-offsets` operator-state 键、`DebeziumConfig` snapshotMode=initial/schemaHistoryStore=jdbc/snapshotMaxThreads=1；`TestDebeziumEngineProperties` 确在 `nop-message/nop-message-debezium`（cookbook 的模块位置标注准确）。
- SQL：`StreamSqlCompiler.compile(SourceLocation,String,Map,String)` 四参签名、九受管类型闭集、`nop.err.eql.dialect-not-support-feature`/`nop.err.eql.invalid-interval-value` 定义于 `OrmEqlErrors`、xdef `<sql sinkBean>` 顶层元素与「唯一内容」注释、`app-sql-compiler.beans.xml` SPI 注册、xpl 六种函数签名与 xdef `xpl-fn:` 声明逐一相等。
- 测试锚点：约 45 个文档引用的测试类/方法名全部存在（含 `TestUnionPipelineE2E`、`TestAdvancedTransforms.sideOutputTransformThrowsWithRuntimeGapMessage`、`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`、`TestDebeziumCdcCheckpoint` 6 方法、`TestDebeziumEngineProperties` 4 方法、`TestS2OfflineReshardE2E.s2OfflineReshard128to256ThenRestoreCompletesExactlyOnce`、`TestEvictorIntegration.testEvictionPersistsToStateOnProductionListStatePath`、gated 开关三个系统属性、`JobCoordinatorMain`/`TaskManagerMain` 确在 test scope、演练端口 8901/8931/8941 与 `StabilityExerciseSupport` 一致、fraud 三份 `.stream.xml` 路径与 S1 四 CEP 链拓扑）。
- 跨模块契约：`IBatchLoaderProvider/IBatchConsumerProvider.setup(IBatchTaskContext)` 两侧签名一致（nop-batch-core ↔ connector-batch）；`IMessageService`（nop-api-core，无 health-check API → message 探测显式 SKIP 的前提成立）；jdbc-2pc upsert 经 nop-dao `UpsertStatements.buildNativeUpsertSqlText`（转义/方言/键守卫下沉，见 DC-05）；`ISqlStreamCompiler`（flow/spi）↔ `StreamSqlCompilerProvider`（sql 模块 bean 注册）↔ `StreamModelDslBuilder.tryGetBeanByType` 缺失时 `nop.err.stream.invalid-arg` 点名模块，三段契约闭合。
- quickstart：`generate.sh`/`verify.sh` 命令与占位符可执行（本审计不跑 mvn，仅静态核对：`mvnw` 坐标、`exec:java` 用法、模板 `Topology1MinimalPipeline` 含 main、`QuickstartSupport.registerCheckpointExecutorFactory/unregister` 存在、生成树清单与 template/ 实际文件一一对应——除 DC-02/DC-03 两处描述漂移）。

**总体结论**：本轮未发现任何「能力边界声明全面失真」级别的问题——历史上最易失真的 union/sideOutput/窗口类型/CEP×后端/并行 2PC/方言白名单边界声明，当前 HEAD 与文档一致或仅存在下述 minor 级漂移。共保留 6 项发现，全部为 low/info 级文档侧修正项，无代码缺陷。

---

## 第 1 轮（初审）

### [DC-01] user-guide：XDSL `<sideOutput>` fail-fast 的错误码声明与 live 不符（ERR_STREAM_UNSUPPORTED vs ERR_STREAM_NOT_IMPLEMENTED）

- **文件+行号**：
  - 文档：`docs-for-ai/03-modules/nop-stream-user-guide.md:73`（能力边界三分口径第 ③ 条）
  - 代码：`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:810-820`（`buildSideOutput` 抛出点）；测试 `nop-stream-flow/src/test/java/io/nop/stream/flow/builder/TestAdvancedTransforms.java:532`
- **证据片段**：
  - 文档声明（L73）：`③ 残余边界：XDSL <sideOutput> 变换与 side-output 视图上的下游 graph 级 transform（map/filter/keyBy 等）仍在 build 期 fail-fast（ERR_STREAM_UNSUPPORTED——side-output 边暂无图级路由；锚点 TestAdvancedTransforms.sideOutputTransformThrowsWithRuntimeGapMessage ...）`
  - 代码事实（AdvancedTransforms.java:810-812）：
    ```java
    private static Object buildSideOutput(StreamSideOutputModel m) {
        throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                .param(ARG_DETAIL, "Stream DSL <sideOutput id='" + m.getId() + "' tag='" + m.getTag()
    ```
  - 该文档引用的测试自身断言（TestAdvancedTransforms.java:532）：`assertEquals("nop.err.stream.not-implemented", ex.getErrorCode().toString());`
- **严重程度**：low
- **现状**：文档把两条路径的错误码合并写成 `ERR_STREAM_UNSUPPORTED`。实际拆分为两条：XDSL `<sideOutput>` 变换 → `nop.err.stream.not-implemented`（`AdvancedTransforms.buildSideOutput`）；side-output 视图上的下游 graph 级 transform（`SideOutputDataStream.map/filter/keyBy/setParallelism`）→ `nop.err.stream.unsupported`（`SideOutputDataStream.unsupported()`，L115-121）。文档声明对后半句正确、对前半句（XDSL 变换）错误。
- **风险**：用户按文档错误码做异常分流/告警匹配（如 catch `nop.err.stream.unsupported`）会在 XDSL `<sideOutput>` 场景漏配；错误码是文档承诺的可编程契约面。
- **建议**：将 L73 括号内错误码改为双值表述：XDSL `<sideOutput>` 变换 → `ERR_STREAM_NOT_IMPLEMENTED`（`nop.err.stream.not-implemented`）；side-output 视图下游 transform → `ERR_STREAM_UNSUPPORTED`。
- **信心水平**：高（文档、代码、测试三方互证，测试断言与文档矛盾是决定性证据）。
- **误报排除**：已确认 `ERR_STREAM_NOT_IMPLEMENTED` 定义于 `NopStreamErrors.java:605-606`（`nop.err.stream.not-implemented`）；非 SpEL 渲染或别名问题——`getErrorCode().toString()` 输出的就是 `nop.err.stream.*` 规范名。
- **复核状态**：未复核

### [DC-02] quickstart README：生成工程 pom 依赖清单声明「三依赖」，模板实为四依赖（新增 nop-xlang）

- **文件+行号**：
  - 文档：`nop-stream/quickstart/README.md:54`（「生成的工程长什么样」树注释）
  - 代码：`nop-stream/quickstart/template/pom.xml:18-43`（`<dependencies>` 四项）
- **证据片段**：
  - 文档声明（L54）：`├── pom.xml    # 三依赖：nop-stream-flow / nop-stream-runtime / junit`
  - 代码事实（template/pom.xml）：`nop-stream-flow`（L20-24）、`nop-stream-runtime`（L26-30）、`nop-xlang`（L31-36，注释「`.stream.xml` 解析（DslModelParser）与 VFS 初始化」）、`org.junit.jupiter:junit-jupiter:6.0.3`（L37-42）。
- **严重程度**：low
- **现状**：模板在文档撰写后追加了 `nop-xlang` 显式依赖（编译期需要 `DslModelParser`/VFS 类），README 的树注释未同步；「junit」与实际坐标 `junit-jupiter` 亦有出入。
- **风险**：误导使用者认为显式声明三依赖即可编译生成工程；若用户自建 pom 漏掉 nop-xlang，`.stream.xml` 解析链在编译/运行期缺类。文档可信度损耗。
- **建议**：更新为「四依赖：nop-stream-flow / nop-stream-runtime / nop-xlang / junit-jupiter」或改为「3 个 nop-stream 构件 + junit-jupiter（另有显式 nop-xlang）」。
- **信心水平**：高（模板 pom 为生成工程的唯一来源，直接比对）。
- **误报排除**：已确认 nop-xlang 不是 nop-stream-flow/nop-stream-runtime 的 compile-scope 传递保证之外被模板冗余声明——模板注释明示其用于「DslModelParser 与 VFS 初始化（传递 nop-core）」，是显式声明而非笔误；故属 README 描述滞后而非模板错误。
- **复核状态**：未复核

### [DC-03] quickstart README：`verify.sh` 的端到端步骤描述缺第 4 步（SQL quickstart，WI23）

- **文件+行号**：
  - 文档：`nop-stream/quickstart/README.md:42-47`（「一键验证（仓库侧）」）
  - 代码：`nop-stream/quickstart/verify.sh:57-63`（step 4）
- **证据片段**：
  - 文档声明（L46）：`# 端到端：检测/补齐本地构件 → 生成到 _tmp/quickstart-verify → mvn test → 3 拓扑全绿`
  - 代码事实（verify.sh）：
    ```bash
    # 4. SQL quickstart（WI23）：.sql 文件 → <sql> 模型 → 编译 → execute → sink
    "$MVNW" -f "$REPO_ROOT/pom.xml" test -pl nop-stream/nop-stream-sql \
        -Dtest='TestStreamSqlQuickstart' -Dsurefire.failIfNoSpecifiedTests=false
    echo "[verify] OK — 脚手架端到端验证通过：生成 → mvn test → 3 拓扑全绿 + SQL quickstart"
    ```
- **严重程度**：low
- **现状**：verify.sh 实际验证面 = 3 拓扑 + 仓库侧 `TestStreamSqlQuickstart`（`nop-stream-sql` 模块，该测试类存在）；README 的流程注释仍是三步口径，也未把 SQL quickstart 计入「全绿」判定面。
- **风险**：使用者对 verify.sh 的覆盖面与失败面产生误判（例如 SQL quickstart 失败时 verify.sh 非零退出，但按 README 理解找不到失败步骤归属）；文档-脚本漂移是同类漂移（DC-02）的第二个实例。
- **建议**：README「一键验证」注释补第 4 步「+ 仓库侧 SQL quickstart（nop-stream-sql TestStreamSqlQuickstart）」。
- **信心水平**：高（脚本输出文本自述「3 拓扑全绿 + SQL quickstart」）。
- **误报排除**：`TestStreamSqlQuickstart.java` 存在于 `nop-stream/nop-stream-sql/src/test/java/io/nop/stream/sql/compile/`（已核），非脚本残留死代码。
- **复核状态**：未复核

### [DC-04] connectors.md 同页自相矛盾：「并行 2PC 能力说明」沿用旧 2 段台账键 `(epoch_id, subtask_id)`，与本页能力矩阵及 live DDL 的 v2 三元键不符

- **文件+行号**：
  - 文档：`docs-for-ai/03-modules/nop-stream-connectors.md:97`（并行 2PC 机制条目）vs 同文件 `:87`（能力矩阵 JdbcTwoPhaseCommitSink 行）；另 `docs-for-ai/03-modules/nop-stream-migration-guide.md:80-84`（v2 三元键权威描述）
  - 代码：`nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:764-776`（`getLedgerTableDDL`）
- **证据片段**：
  - 文档 L97：`**机制**：... JDBC 台账复合键 (epoch_id, subtask_id)、File 输出 .sK 后缀——subtask 身份进入提交键`
  - 文档 L87（同页矩阵）：`commit 幂等（台账三元键 (sink_namespace, epoch_id, subtask_id) 守卫，namespace=部署身份 jobId|vertexId ...）`
  - 代码事实（DDL 主键，L770-776）：`sink_namespace VARCHAR(500) NOT NULL, epoch_id BIGINT NOT NULL, subtask_id INT NOT NULL ... PRIMARY KEY (sink_namespace, epoch_id, subtask_id)`；默认表 `DEFAULT_LEDGER_TABLE = "stream_epoch_ledger_v2"`（L84）。
- **严重程度**：low
- **现状**：L97 是 R5-CON-01 命名空间键改造前的旧表述残留：v2 键中 subtask 身份确实入键，但幂等守卫键是三元组且新增 `sink_namespace` 维度（同库多支路/跨作业防互踩正是靠它）；同页 L87 与 migration-guide 均已是 v2 口径，唯 L97 未同步。
- **风险**：读者据 L97 推断「同库两条 sink 支路仍需各用独立 ledger 表」（该约束已被 v2 明文废弃，见 migration-guide L86），做出错误的多管线共库部署决策。
- **建议**：L97 改为「JDBC 台账三元键 `(sink_namespace, epoch_id, subtask_id)`（namespace=jobId|vertexId）」与 L87 对齐。
- **信心水平**：高（同文档两处互斥 + DDL 直接证据）。
- **误报排除**：已确认 L97 上下文是机制说明而非历史注记（无「历史/v1/已废弃」限定词）；`stream_epoch_ledger`（旧 3 列表）在 main 代码中仅以「不迁移不读」语义出现，不是现行键。
- **复核状态**：未复核

### [DC-05] CDC cookbook + connectors 矩阵：键控 upsert 方言边界声明窄于实现（漏 MariaDB；H2 测试方言未提）

- **文件+行号**：
  - 文档：`docs-for-ai/03-modules/nop-stream-cdc-cookbook.md:90`；`docs-for-ai/03-modules/nop-stream-connectors.md:87`（dmlMode 列）
  - 代码：`nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:1058-1077`（`buildKeyedUpsertSql`）；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/UpsertStatements.java:66-78`（`resolveUpsertStyle`）
- **证据片段**：
  - 文档（cookbook L90）：`键控模式必配 keyColumns；方言 upsert：MySQL ON DUPLICATE KEY UPDATE、PostgreSQL ON CONFLICT DO UPDATE（其余方言键控模式类型化拒绝）。`
  - 代码事实（sink 拒绝消息，L1073-1076）：`dialect '" + dialect.getName() + "' has no keyed upsert support in the built-in sync scope (supported: mysql/mariadb, postgresql; h2 for tests)`
  - nop-dao 原语（UpsertStatements L70-76）：`postgresql|pg → POSTGRESQL；mysql|mariadb → MYSQL；h2 → H2`（H2 生成 `MERGE INTO ... KEY (...)`）。
- **严重程度**：low（能力边界声明失真方向为「少报」，非「虚报」）
- **现状**：实现按方言名前缀接受 `mariadb`（走 MySQL `ON DUPLICATE KEY UPDATE` 同款语句）与 `h2`（测试方言），拒绝其余；文档「其余方言键控模式类型化拒绝」对 MariaDB 不成立（MariaDB 不会被拒绝）。跨模块契约（jdbc-connector ↔ nop-dao `UpsertStatements`）本身一致——转义、方言解析、键守卫均已下沉 nop-dao，sink 仅叠加 built-in sync scope 白名单。
- **风险**：MariaDB 用户按文档误判「键控 upsert 不可用」而放弃 `jdbc-2pc` dmlMode（保守方向的功能放弃）；文档作为唯一权威边界表出现缺口。
- **建议**：两处改为「MySQL/MariaDB `ON DUPLICATE KEY UPDATE`、PostgreSQL `ON CONFLICT DO UPDATE`（H2 为测试方言；其余方言键控模式类型化拒绝）」。
- **信心水平**：高（拒绝消息文本与 nop-dao 原语双方自证；connector 的 javadoc 明言「MySQL/MariaDB and PostgreSQL ... H2 is recognized for tests」）。
- **误报排除**：已确认这不是「计划支持」而是已生效路径——`UpsertStatements.buildNativeUpsertSqlText` 对 MYSQL style 生成 `VALUES(col)` 更新集、对空更新集生成首键自赋值形态，均为实现分支而非注释；H2 分支有 `TestJdbcTwoPhaseCommitSinkDml`（H2 真库）在用。
- **复核状态**：未复核

---

## 深挖第 2 轮追加

第 2 轮基于第 1 轮发现的漂移类型（错误码名 / 依赖清单 / 机制键名 / 方言白名单 / 属性名）做同类全面扫描：

- 错误码名全面核对：六份文档引用的全部 `nop.err.*` 码（invalid-state / unsupported / not-implemented / config-error / 2pc-sink-parallelism-change-unsupported / state-schema-mismatch / class-not-allowed / checkpoint-checksum-mismatch / checkpoint-format-version-unsupported / bean-not-found / connector-param-unknown / connector-param-required / connectivity-check-failed / connectivity-not-supported / invalid-arg / ref-unknown / eql.dialect-not-support-feature / eql.invalid-interval-value / debezium 族 3 码）逐一与 `NopStreamErrors`/`OrmEqlErrors`/`DebeziumErrors` 定义比对——除 DC-01 一处外全部一致。
- 已废除声明核对：`ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED` 全仓 grep 为空，与「已随门禁移除删除」一致；`stream_epoch_ledger`（旧表）仅存于「不迁移不读」叙述。
- 属性/配置键名核对：`nop.stream.ops.*`、`nop.stream.metrics.log.*`、`nop.stream.alert.*`、`nop.stream.ops.governance.*`、`nop.stream.state.deserialize.allowed-prefixes`、`nop-stream.checkpoint.storage.dir`、gated 开关三系统属性、launch 参数（nodeId/jobId/jdbcUrl/topicNamespace/checkpointBaseDir/expectedNodeIds/pipelineFactoryClass/opsHttpPort/opsHttpBind/alertWebhookUrl）全部与 `StreamOpsConfig`/`StreamMetricsReporter`/`AlertService`/`StreamDeserializationFilter`/`GraphModelCheckpointExecutor`/`ClusterLaunchConfig`/`JobCoordinatorMain` 键名一致。
- 机制属性名核对（本轮新增发现）：

### [DC-06] user-guide：Delta 机制属性名笔误——`x:support-extends="true"` 在全仓不存在，实际为 xdef 上的 `xdef:support-extends="true"`

- **文件+行号**：
  - 文档：`docs-for-ai/03-modules/nop-stream-user-guide.md:143`（Delta 定制节首句）
  - 代码：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:24`
- **证据片段**：
  - 文档声明（L143）：```.stream.xml` 支持平台标准 Delta 机制（`x:support-extends="true"`）：```
  - 代码事实（stream.xdef:20-24）：
    ```xml
    <stream x:schema="/nop/schema/xdef.xdef" ...
            xdef:name="StreamModel" xdef:bean-package="io.nop.stream.flow.model"
            xdef:model-name-prop="name" xdef:model-version-prop="version"
            xdef:support-extends="true"
    ```
  - 全仓 grep `x:support-extends`（排除 target/docs/ai-dev）：0 命中；实例文件使用的是 `x:schema` + `x:extends`（如 `fraud-s2-file-delta.stream.xml:8`）。
- **严重程度**：low
- **现状**：声明「模型支持 Delta」的开关属性位于 xdef 模式文件上、名为 `xdef:support-extends`；`.stream.xml` 实例侧使用 `x:extends` 继承。文档把前缀写错为 `x:`，且未说明该属性属于 xdef 合同而非用户模型文件。
- **风险**：用户在自有 `.stream.xml` 里照抄 `x:support-extends="true"` 期望开启 Delta——xdef 校验对未知属性的行为可能直接拒绝或静默忽略属性，用户得不到预期 Delta 能力；文档作为 XDSL 契约面的权威表述失真。
- **建议**：改为「`stream.xdef` 声明 `xdef:support-extends="true"`，实例经 `x:extends="..."` 或 `_delta` 分层继承」。
- **信心水平**：高（xdef 原文 + 全仓零命中双向证据）。
- **误报排除**：已确认这不是「平台同时接受两种前缀」——XLang 对 xdef 根属性只识别 `xdef:` 前缀名（xdef.xdef 自身同款写法）；`x:support-extends` 在任何 `.xdef/.java/.xml` 生产文件中均无出现，非别名。
- **复核状态**：未复核

**第 2 轮其余扫尾结果（无新发现）**：
- 类名/方法名锚点批量核对（`GlobalBeanFunctionResolver`/`InMemoryBeanFunctionResolver`/`BeanContainerFunctionResolver.of(IBeanContainer)`/`CheckpointCoordinator.setCheckpointHistoryMaxEntries`/`JobCoordinator.addJobEventListener|addHealthListener`/`StorageJobIds.sanitizeJobId`/`StreamStateResetTool.reset(4 参)`/`StreamConfValidateCommand.run(args,resolver)`/`StreamConfValidator.validateConnector`/`WatermarkStrategyWithIdleness`/`SourceContext.markAsTemporarilyIdle`/`StreamingRuntimeContext.getKeyedStateStore`/`KeyedStateStore` 状态五类型/`StateTtlConfig`/`WindowOperatorFactoryImpl` 固定 FQCN 反射/`AlertService.fromProperties`/`UpsertStatements`/`MiniStreamCluster` 端口注释）全部存在且签名一致。
- quickstart 生成树与 `template/` 实际 12 个文件一一对应；`Topology1MinimalPipeline` 含 `main`（README 第 3 步 `exec:java` 可用）；`QuickstartSupport.registerCheckpointExecutorFactory/unregisterCheckpointExecutorFactory` 与 owner doc 描述一致。
- XDSL transform 类型清单（source/timestampsAndWatermarks/map/flatMap/filter/keyBy/window/aggregate/reduce/process/join/union/cep/sink/custom + `<joins>`/`<patterns>` 注册表）与 `stream.xdef` 定义逐一对应；`process`/`aggregate`/`cep` 仅 bean 形态与 builder 行为一致。
- README 模块表 11 行 ↔ `nop-stream/pom.xml` `<modules>` 11 项一一对应；模块依赖描述（flow 依赖 core+cep+nop-xdefs；cep 依赖 core）与 pom 相符；五阶段管线口径与 xdef 头注释及 `GraphExecutionPlan` 定位说明一致。

---

## 最终保留项（表格索引）

| ID | 文档 | 位置 | 一句话发现 | 严重程度 | 类型 |
|---|---|---|---|---|---|
| DC-01 | nop-stream-user-guide.md | L73 | XDSL `<sideOutput>` fail-fast 错误码写为 `ERR_STREAM_UNSUPPORTED`，live 为 `ERR_STREAM_NOT_IMPLEMENTED`（其引用的测试断言后者） | low | 错误码漂移 |
| DC-02 | nop-stream/quickstart/README.md | L54 | 生成 pom「三依赖」实为四依赖（+nop-xlang；junit→junit-jupiter） | low | 描述滞后 |
| DC-03 | nop-stream/quickstart/README.md | L42-47 | verify.sh 步骤描述缺第 4 步（SQL quickstart / WI23） | low | 描述滞后 |
| DC-04 | nop-stream-connectors.md | L97（vs L87） | 同页台账键旧口径 `(epoch_id, subtask_id)` 与 v2 三元键 `(sink_namespace, epoch_id, subtask_id)`（live DDL）互斥 | low | 过时残留/同页矛盾 |
| DC-05 | nop-stream-cdc-cookbook.md L90；nop-stream-connectors.md L87 | — | 键控 upsert 方言边界窄于实现：MariaDB 实际受支持（MYSQL style）、H2 为测试方言，文档写「其余方言类型化拒绝」 | low | 能力边界少报 |
| DC-06 | nop-stream-user-guide.md | L143 | Delta 开关属性名笔误 `x:support-extends="true"`（全仓不存在），实际为 stream.xdef 的 `xdef:support-extends="true"` | low | 属性名错误 |

**严重程度分布**：high 0 / medium 0 / low 6。无代码缺陷；全部为文档侧修正项。
**总体评价**：nop-stream 文档族当前与 HEAD 的一致性处于高位——owner doc 的指标名表（24 项）、REST/配置键/默认值（约 30 键）、错误码引用（19 码）、测试锚点（约 45 个类/方法）经全量核对仅 1 处错误码漂移；跨模块契约（batch / message / dao-upsert / sql SPI）两侧签名与语义闭合。历史「能力边界声明全面失真」的问题模式在当前 HEAD 未复现（union/sideOutput/窗口类型/自定义 partitioner（FORWARD|HASH 之外显式拒绝）/CEP×RocksDB/2PC 并行/方言白名单逐项验证均准确，唯 DC-05 为少报型小缺口）。

---

## 维度复核结论

> 复核人：独立审计复核员（与初审者无关）。方法：逐条亲自打开被引文档行号与代码行号，核对「文档声明 vs 代码事实 vs 测试断言」三方；反证检查初审是否误读文档语义（另一版本轴/另一形态）。历史仅消费 R5 summary.md。未运行 mvn，未修改除本节外的任何内容。

| 发现 | 判定 | 现状归属 | 复核要点 |
|---|---|---|---|
| DC-01 | 保留 | R6 新发现：位于 R5 DC-1（user-guide.md:73 能力边界失真，plan 2317 文档批次）已修复后的三分口径文本上，属 2317 修复文本的残留新漂移，非 R5 条目重复 | 三方亲证：文档 L73 确将两条路径合并写为 `ERR_STREAM_UNSUPPORTED`；`AdvancedTransforms.buildSideOutput`（L810-820）实抛 `ERR_STREAM_NOT_IMPLEMENTED`；**文档自引锚点测试** `TestAdvancedTransforms.sideOutputTransformThrowsWithRuntimeGapMessage` L532 断言 `nop.err.stream.not-implemented`，与文档矛盾成立；后半句路径正确——`SideOutputDataStream.unsupported()`（L104-158）对 map/filter/keyBy/setParallelism 等 9 个操作实抛 `ERR_STREAM_UNSUPPORTED`；`ERR_STREAM_NOT_IMPLEMENTED`=`nop.err.stream.not-implemented` 定义于 `NopStreamErrors.java:605-606`。初审两路径拆分理解正确，无误读 |
| DC-02 | 保留 | R6 新发现（quickstart 不在 R5 审计对象清单 core/runtime/cep/flow/rocksdb/connector*/fraud-example/sql 内） | README.md:54 原文「三依赖：nop-stream-flow / nop-stream-runtime / junit」逐字确认；template/pom.xml `<dependencies>` 实为四项：flow（L20-24）/runtime（L26-30）/nop-xlang（L31-36，注释自证用于 DslModelParser+VFS，显式声明非冗余）/`org.junit.jupiter:junit-jupiter:6.0.3`（L37-42）。「junit」与 `junit-jupiter` 坐标出入亦确认。无误读 |
| DC-03 | 保留 | R6 新发现（同上，quickstart 不在 R5 范围） | README L42-47 流程注释为纯三步口径（「3 拓扑全绿」），未提 SQL quickstart；verify.sh 实有 step 4（L54-57：`-pl nop-stream/nop-stream-sql -Dtest='TestStreamSqlQuickstart'`），终态输出 L59 自述「3 拓扑全绿 + SQL quickstart」；`TestStreamSqlQuickstart.java` 实存于 `nop-stream-sql/src/test/java/io/nop/stream/sql/compile/`。初审所引行号 57-63 与实际块（54-60）略有偏移，不影响实质。无误读 |
| DC-04 | 保留 | R5-CON-01（plan 368 Phase 4 命名空间键改造）的文档同步残留，R6 首次立案 | connectors.md:97 原文「JDBC 台账复合键 (epoch_id, subtask_id)」逐字确认，且其上下文（L93-100）为现在时机制说明，无任何历史/v1 限定词；同文件 L87 矩阵行已是 v2 三元键口径；migration-guide.md:80-84 权威描述 + L86 明文「旧『同库多链 2PC sink 须各用独立 ledger 表』约束已被 v2 命名空间列取代」印证初审风险推断；live DDL（`getLedgerTableDDL` L764-779）`PRIMARY KEY (sink_namespace, epoch_id, subtask_id)`、`DEFAULT_LEDGER_TABLE="stream_epoch_ledger_v2"`（L84）均确认。同页互斥成立，无误读 |
| DC-05 | 保留 | R6 新发现（R5 summary 未见对应条目；R5 CON 族修复不涉及 upsert 方言文档面） | cookbook L90「MySQL/PostgreSQL（其余方言键控模式类型化拒绝）」与 connectors.md L87 dmlMode 列「MySQL/PG」逐字确认；sink 拒绝消息（L1058-1061）自述 `supported: mysql/mariadb, postgresql; h2 for tests`；nop-dao `UpsertStatements.resolveUpsertStyle`（L65-80）`mysql\|mariadb→MYSQL`、`h2→H2` 前缀匹配确认。**反证补强**：`mariadb.dialect.xml` 为 nop-dao 注册方言（driver=org.mariadb.jdbc.Driver），mariadb 分支是已生效路径而非死代码；sink javadoc（L1044-1050）明言「MySQL/MariaDB and PostgreSQL … H2 is recognized for tests」。少报方向判定正确，low 恰当。无误读 |
| DC-06 | 保留 | R6 新发现（R5 summary 未见对应条目） | user-guide L143 原文 `x:support-extends="true"` 逐字确认；stream.xdef:24 实为 `xdef:support-extends="true"`；XLang 运行时仅识别 `xdef:` 前缀——`XDefKeys.DEFAULT = new XDefKeys("xdef")`（XDefKeys.java:21）→ `SUPPORT_EXTENDS="xdef:support-extends"` 且入 `ATTR_NAMES`；全仓 grep `x:support-extends`（.java/.xml/.xdef 生产文件）0 命中，唯一出现即文档该行自身（初审报告引文除外）；实例侧确认用 `x:schema` + `x:extends`（fraud-s2-file-delta.stream.xml:7）。初审「非别名/双前缀」排除成立。无误读 |

**复核统计**：6 复核 / 6 保留 / 0 降级 / 0 驳回。初审严重程度分布（low 6）维持不变；无一处发现存在文档误读或语义错置。两处引用行号微小偏移（DC-03 verify.sh 实为 L54-60、DC-05 方法起点实为 L1052）不构成降级理由。

## 最终保留项（复核后）

| 编号 | 复核后严重程度 | 文件路径 | 一句话摘要 | 现状归属 |
|---|---|---|---|---|
| DC-01 | low | docs-for-ai/03-modules/nop-stream-user-guide.md | L73 能力边界第③条把 XDSL `<sideOutput>` 变换的错误码写为 `ERR_STREAM_UNSUPPORTED`，live 为 `ERR_STREAM_NOT_IMPLEMENTED`（自引测试断言即为后者）；仅 side-output 视图下游 transform 才是 `ERR_STREAM_UNSUPPORTED` | R6 新发现（2317 修复文本上的残留漂移） |
| DC-02 | low | nop-stream/quickstart/README.md | L54 树注释声明生成 pom「三依赖」，模板实为四依赖（追加 nop-xlang；junit 应为 junit-jupiter） | R6 新发现 |
| DC-03 | low | nop-stream/quickstart/README.md | L42-47 verify.sh 步骤描述缺第 4 步（仓库侧 SQL quickstart：nop-stream-sql `TestStreamSqlQuickstart`），「全绿」判定面未计入 | R6 新发现 |
| DC-04 | low | docs-for-ai/03-modules/nop-stream-connectors.md | L97 并行 2PC 机制沿用旧 2 段台账键 `(epoch_id, subtask_id)`，与同页 L87 及 live DDL 的 v2 三元键 `(sink_namespace, epoch_id, subtask_id)` 互斥 | R5-CON-01 修复的文档同步残留 |
| DC-05 | low | docs-for-ai/03-modules/nop-stream-cdc-cookbook.md；docs-for-ai/03-modules/nop-stream-connectors.md | 键控 upsert 方言边界少报：MariaDB 实际受支持（MYSQL style，mariadb.dialect.xml 注册方言）、H2 为测试方言，文档写「其余方言类型化拒绝」 | R6 新发现 |
| DC-06 | low | docs-for-ai/03-modules/nop-stream-user-guide.md | L143 Delta 开关属性名笔误 `x:support-extends="true"`（全仓生产文件 0 命中），实际为 stream.xdef 根上的 `xdef:support-extends="true"`，实例继承走 `x:extends` | R6 新发现 |
