# 14 Data Plane & Multi-JVM Recovery Audit (nop-stream Independent Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 14); frozen Stage-4 outputs (`source-manifest.md` domains a/d/e/g, `evidence-schema.md`, `finding-corpus.md`, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`); frozen Stage-5 outputs (`environment-qualification.md` — T1 `qualified`/`in-process`, T2 `qualified`/`multi-jvm` infra, T3/T4 `blocked`); frozen Stage-6/9/13 evidence; live repo baseline of `nop-stream-core` + `nop-stream-runtime` transport/execution/multijvm surfaces.
> Mission: nop-stream-independent-audit
> Work Item: 14. Data plane and real multi-JVM recovery audit
> Related: Execution order `{1}` of this DRAFT_PLANS round. Roadmap deps: Stage 4 (evidence schema), Stage 5 (env qualification), Stage 9 (checkpoint audit), Stage 13 (control-plane/HA/fencing) — all `done`. Hard prerequisite for Stage 15 (batch/message connectors) and Stage 16 (JDBC/file/CDC external effects); also feeds Stage 20/22 historical disposition. On **critical path**. Absorbs Stage-13 deferred follow-ups: cross-JVM fencing revalidation (M8-2-P0-1 distributed mutex), cross-JVM zombie task fencing (M8-2-P1-6), T2 deeper defects (log-label mismatch + HA-fencing takeover).

## Purpose

独立验证 nop-stream 的 **data plane** 是否实现其设计目标：真实 **process-boundary**（cross-JVM）的 record / barrier / watermark 传输，以及使用合格环境（qualified environment）的失败恢复。每个被支持的 data-plane 能力必须形成一条可复核的 entry-to-effect evidence row；每个不支持的组合必须有 fail-fast 证明或显式 `blocked` 裁定（按 Stage-5 gated-evidence 规则 S5-1/S5-2）。

本审计验证核心 invariants：(a) record/barrier/watermark 经 wire-codec + `IMessageService` 后端跨 JVM 传输并保持语义（顺序/ fencing / EOS）；(b) 部署描述符（`TaskDeploymentDescriptor`）在 coordinator 侧构建、在 TaskManager 侧确定性重建，两侧 topic/codec 接线一致；(c) 真实进程边界下的 kill/restart 恢复、stale-attempt fencing、recovered sink-result。

本审计**发现**的任何 confirmed live defect 不在本计划内修复，而按 roadmap 规则指派给 active/successor remediation plan。

## Current Baseline

经 2026-08-08 live repo 核对（引用均与 frozen Stage-4 `source-manifest.md` 域 a/d/e/g + 实际源码一致；line anchors 经 explore agent 逐行复核）：

- **Data-plane transport envelope**：`StreamMessageEnvelope`（`nop-stream-core/.../execution/transport/StreamMessageEnvelope.java:27`，`@DataBean implements Serializable`），type 常量 `:32-36`（STREAM_RECORD/CHECKPOINT_BARRIER/WATERMARK/WATERMARK_STATUS/CONTROL）+ control payload `:46-47`（CONTROL_END_OF_STREAM/CONTROL_HEARTBEAT），`epochId` field `:53`。Codec：`StreamElementCodec.encode(...)` `nop-stream-core/.../transport/StreamElementCodec.java:50` / `decode(...)` `:91`（map-fallback reconstruction of barrier/watermark `:148/160/172`）。
- **Wire-codec SPI**：`IDataPlaneWireCodec`（`nop-stream-runtime/.../transport/IDataPlaneWireCodec.java:38`，`toWire(StreamMessageEnvelope):Object` `:46` / `fromWire(Object):StreamMessageEnvelope` `:55`）。实现：`IdentityWireCodec`（`.../IdentityWireCodec.java:20`，passthrough，default for `LocalMessageService`）、`SysDaoWireCodec`（`.../SysDaoWireCodec.java:36`，`ApiRequest{data:map}` `:41-48`）、`KafkaStringWireCodec`（`.../KafkaStringWireCodec.java:42`，`toWire` JSON-stringify `:47`）、`PulsarStringWireCodec`（`.../PulsarStringWireCodec.java:39`，`toWire` `:44-49`）。共享归一化 helper `DataPlaneWireSupport.toWireMap(...)` `.../DataPlaneWireSupport.java:38`（flatten barrier/watermark 为 JSON-serializable map）。
- **`IMessageService` backends**：platform SPI `IMessageService`（`nop-kernel/nop-api-core/.../message/IMessageService.java:13`）。`LocalMessageService`（`nop-message/nop-message-core/.../local/LocalMessageService.java:24`，subscribe `:144`/send `:160`，in-process default）。Cross-JVM backends：`KafkaMessageService`（`nop-message/nop-message-kafka/.../KafkaMessageService.java:58`，StringSerializer wire=JSON）、`PulsarMessageService`（`nop-message/nop-message-pulsar/.../PulsarMessageService.java:47`，Schema.STRING）、`SysDaoMessageService`（`nop-sys/nop-sys-dao/.../SysDaoMessageService.java:53`，持久化到 `NopSysEvent.eventData`）。Test-only cross-JVM backend：`PollingJdbcMessageService`（`nop-stream-runtime/src/test/.../launch/PollingJdbcMessageService.java`，shared JDBC DB backed）。
- **`RemoteResultPartition`（producer，stamps epochId）**：`nop-stream-runtime/.../transport/RemoteResultPartition.java:58`（class），`epochId` field `:66`。`write(StreamElement)` `:153` → `StreamElementCodec.encode(element, valueType, epochId)` `:163` + send envelope `:165`（epochId stamped into every outbound envelope）。EOS stamping on close `:184-187`；heartbeat envelope `:252-255`。
- **`RemoteInputChannel`（consumer，epoch fencing）**：`nop-stream-runtime/.../transport/RemoteInputChannel.java:64`（class），`EnvelopeConsumer.onMessage(...)` inner class `:349`，method `:352`。**Epoch fencing filter**：`:368` — `if (envelope.getEpochId() != expectedEpochId) { LOG.debug("Discarding stale message..."); return null; }`（comment `:364-367` documents Stage 39 single-long-epoch collapse）。Liveness refresh AFTER fence passes `:379`；control-msg branching（EOS/heartbeat）`:382-399`；decode + enqueue `:402-419`。Unaligned checkpoint path：`captureInFlightData` `:277` / `injectElements` `:298`。Subscription in constructor `:156`。
- **`stream-data-plane.beans.xml` IoC**：`nop-stream-runtime/src/main/resources/_vfs/nop/stream/beans/stream-data-plane.beans.xml`，2 个 `ioc:default` bean：`streamMessageService`→`LocalMessageService` `:68-69`、`streamDataPlaneWireCodec`→`IdentityWireCodec` `:76-77`。Header `:3-58` 文档如何 override 为 SysDao/Pulsar/Kafka + matching codec（deployment templates `:36-57`）。Discovery test：`TestStreamModuleDiscovery` asserts both default beans materialize。
- **Distributed execution data-plane wiring**：`RpcDistributedExecutor.startJob(...)` `nop-stream-runtime/.../execution/RpcDistributedExecutor.java:183`。Per-node TaskManager wiring `:199-223`（`new TaskManager(nodeId,...,messageService,clusterRegistry,controlTopic)` `:203`，`tm.updateFencingToken(fencingEpoch)` `:204`，`tm.start()` `:205`，task RPC server `:208-211`，coordinator→task proxy `:214-217`）。**Data-plane wiring** `:270-272`：`new RemoteGraphExecutionPlanBuilder(new DataPlaneMessageServiceAdapter(messageService, dataPlaneWireCodec), new TypeRegistry(), fencingEpoch)` → `buildRemoteOnly(...)` `:273`（comment `:266-269`：adapter 仅作用于 data-plane view，control RPC 保留 raw service）。Codec default `IdentityWireCodec.INSTANCE` `:93`；`setDataPlaneWireCodec(...)` `:120`。`remoteDeployMode` injection `:241-245`。Embedded 等价：`EmbeddedDistributedExecutor` `:204-207`。
- **Deployment-descriptor reconstruction（cross-JVM task rebuild）**：`TaskDeploymentDescriptor`（`nop-stream-runtime/.../rpc/TaskDeploymentDescriptor.java:51`，`@DataBean implements Serializable`），carries serializable `JobGraph` `:84`、`DeploymentPlan` `:90`、`fencingEpoch` `:75`、`checkpointRestorePath` `:99`。Javadoc `:18-49` documents how each TaskManager JVM rebuilds its own `StreamTaskInvokable` locally（no live operator reference crosses the JVM）。Coordinator builds descriptors when `remoteDeployMode` set — `JobCoordinator.java:1597`（deployTask cross-JVM path）`:1646`。`RpcDistributedExecutor.setRemoteDeployMode(boolean)` `:133` → propagates `:241-245`。**Deterministic topic-based wiring**：`RemoteGraphExecutionPlanBuilder.buildRemoteOnly(...)` `.../transport/RemoteGraphExecutionPlanBuilder.java:81`，`StreamTopicNaming.buildTopic(jobId, edgeId, s, t)` `:115`，producer `RemoteResultPartition` wired `:117`、consumer `RemoteInputChannel` wired `:122`。**TaskManager-local rebuild counterpart**：`SubtaskPlanBuilder` `.../transport/SubtaskPlanBuilder.java`（comment `:72`："view would have produced, so cross-JVM data exchange works"，constructor takes `IMessageService` `:61`）。
- **Topic naming（cross-JVM deterministic）**：`StreamTopicNaming` `.../transport/StreamTopicNaming.java:11`（"Provides topic naming convention for cross-TaskManager data exchange"），`buildTopic(jobId, edgeId, sourceSubtask, targetSubtask)`。
- **Buffer pool boundary**：`TestBufferPoolRemoteExclusion` asserts `RemoteResultPartition`/`RemoteInputChannel` intentionally bypass per-job `IBufferPool`（cross-JVM bound is `IMessageService` backend，not buffer pool）。`remoteResultPartitionDoesNotConsumePool` `:33`、`remoteInputChannelDummyPartitionHasNoPool` `:72`。
- **测试语料**（manifest 域 g）：
  - **in-process LocalMessageService**：`TestRemoteDataExchange`（`.../transport/TestRemoteDataExchange.java:31`）— `testRemoteResultPartitionSendsEnvelope` `:52`、`testRemoteInputChannelReceivesRecords` `:111`、**`testRemoteInputChannelFencingRejectsStaleMessages`** `:170`、**`testRemoteBarrierExchange`** `:199`、**`testRemoteWatermarkExchange`** `:223`、`testTopicNaming` `:46`。`TestRemoteInputChannelHeartbeat`（`.../TestRemoteInputChannelHeartbeat.java:28`）— producer idle-heartbeat + consumer timeout：`testProducerSendsIdleHeartbeatWhenIdle` `:57`、`testConsumerTimesOutWhenSilent` `:112`、**`testWrongEpochHeartbeatDoesNotRefreshLiveness`** `:161`（fencing invariant）、`testEndOfStreamIsNotHeartbeatTimeout` `:137`。
  - **always-on DB backend（T1 lane）**：`TestDataPlaneSysDaoBackendE2E`（`nop-sys/nop-sys-dao/src/test/.../TestDataPlaneSysDaoBackendE2E.java:67`，`@NopTestConfig(localDb=true)`，no gate）— `recordsTraverseNopSysEventTableExactlyOnce` `:105`、`barrierAndWatermarkTraverseBackend` `:156`、**`fencingRejectsStaleEpochOverBackend`** `:189`、`endOfStreamPropagatesThroughBackend` `:219`。
  - **gated external backends（T3/T4 blocked）**：`TestDataPlaneKafkaBackendE2E`（`.../TestDataPlaneKafkaBackendE2E.java:46`，gated `@EnabledIfSystemProperty("nop.stream.test.kafka.enabled")` `:44-45`，`KafkaStringWireCodec.INSTANCE` `:66`，`recordBarrierWatermarkTraverseKafkaTopic()` `:77`）。`TestDataPlanePulsarBackendE2E`（`.../TestDataPlanePulsarBackendE2E.java:51`，gated `:49-50`，`recordBarrierWatermarkTraversePulsarTopic()` `:80`）。
  - **codec round-trip unit（always-on）**：`TestSysDaoWireCodec` `:32`、`TestPulsarStringWireCodec` `:30`、`TestKafkaStringWireCodec` `:29`。
  - **unaligned checkpoint data path**：`TestUnalignedCheckpointBackpressure`（`.../TestUnalignedCheckpointBackpressure.java:153`，`testNoDuplicatesRecordsBeforeBarrierNotReplayed`）— exercises `RemoteInputChannel.captureInFlightData/injectElements`。
  - **multi-JVM process（T2 lane）**：`MiniStreamCluster`（`.../multijvm/MiniStreamCluster.java:78`，child-JVM spawn via `java.class.path` `:137-138`，`spawnJobCoordinator(index)` `:389` writes label `"coordinator-"+index` `:404`，`logFileFor(String)` `:310` resolves `<runDir>/logs/<nodeId>.log`，H2 `AUTO_SERVER=TRUE` `:134`）。`TestMiniStreamClusterProcessSpawn`（`.../TestMiniStreamClusterProcessSpawn.java:49`，gated `:48`，**3/3 PASS**，TM registration `:58`，coordinator-0 log-existence `:70`，kill+restart re-registration `:74-98`）。**`TestMultiJvmExactlyOnceRecovery`**（`.../TestMultiJvmExactlyOnceRecovery.java:68`，gated `:67`）— **DEFECT at `:111`**：reads `cluster.logFileFor("coordinator")` (missing `-0` suffix)；`MiniStreamCluster` only ever logs coordinators under `coordinator-{index}`（`:404`/`:228`）→ `Files.size` throws `NoSuchFileException`。`TestMultiJvmCoordinatorFailover`（`.../TestMultiJvmCoordinatorFailover.java:35`，gated `:34`）— `testBrainSplitFencingBoundary()` `:106`（loop `:118-133`，assert `:129` "coordinator-1 must take over"，依赖 HA lease-table rotation）。
- **Stage 13 deferred → 本计划吸收**：cross-JVM fencing epoch revalidation（M8-2-P0-1 distributed mutex，`required_lane: multi-jvm`）、cross-JVM zombie task fencing（M8-2-P1-6，`required_lane: multi-jvm`）、T2 lane deeper defect（log-label mismatch `TestMultiJvmExactlyOnceRecovery:111`、HA-fencing takeover `TestMultiJvmCoordinatorFailover:106/129`）。
- **真实 gap**：(1) 没有覆盖"record/barrier/watermark 经真实 process-boundary 传输"的成套 evidence row（in-process LocalMessageService 有测试但 cross-JVM 真实后端 SysDao only always-on，Kafka/Pulsar gated-blocked）；(2) deployment-descriptor 在 coordinator 构建 vs TaskManager 重建的接线一致性（topic/codec deterministic）缺端到端 evidence row；(3) 真实 multi-JVM recovery（process kill/restart → stale-attempt fencing → recovered sink-result）的端到端 evidence row 受 T2 两个 deeper defect 阻塞（须标 `blocked` disposition）；(4) cross-JVM fencing/zombie residual（Stage 13 deferred）的最终 cross-JVM 复验缺 evidence row。

## Goals

- 产出一份 **data-plane 支持/拒绝能力矩阵**（record transport、barrier transport、watermark transport、EOS propagation、heartbeat/liveness、epoch fencing discard、unaligned in-flight capture、deployment-descriptor cross-JVM rebuild），每能力一条 evidence row，`environment_class` 按 frozen lane 词表裁定（in-process LocalMessageService + SysDao → `in-process`；真实 Kafka/Pulsar → 据 T3/T4 `blocked` 标 `blocked`；真实 multi-JVM process → 据 T2 `qualified` infra 标 `multi-jvm` 或 `blocked`）。
- 为**每条 in-process data-plane 能力**产出 entry-to-effect evidence row：`positive_proof` 为真实 in-process 实跑测试名（`ClassName#method`），验证 record/barrier/watermark 从 `RemoteResultPartition.write()` 经 wire-codec + backend 到 `RemoteInputChannel` 消费完整走通（接线验证）。
- 产出 **wire-codec backend 矩阵** evidence row：Identity（LocalMessageService）、SysDao（DB，always-on）、Kafka（gated）、Pulsar（gated）各一行，`disposition` 据 lane 资格裁定（SysDao `e2e-proved`/`in-process`；Kafka/Pulsar `blocked` + cross-ref Stage 5 T3/T4 `@@LANE` block）。
- 产出 **deployment-descriptor cross-JVM reconstruction** evidence row：`TaskDeploymentDescriptor` 序列化 round-trip + coordinator-built vs TaskManager-local-rebuild 的 topic/codec 接线一致性（`RemoteGraphExecutionPlanBuilder.buildRemoteOnly` vs `SubtaskPlanBuilder` deterministic topic wiring），`positive_proof` 引用 in-process 实跑或 manual-trace。
- 产出 **true multi-JVM recovery** evidence row（T2 lane）：`TestMiniStreamClusterProcessSpawn` 3/3 证明 cross-JVM process spawn + registration + coordinator boot（`environment_class: multi-jvm`，`disposition: e2e-proved` for infrastructure）。
- 对 **T2 lane 两个 deeper defect**（`TestMultiJvmExactlyOnceRecovery` log-label mismatch、`TestMultiJvmCoordinatorFailover` HA-fencing takeover）产出 `disposition: blocked` evidence row，`required_lane: multi-jvm`，cross-ref Stage 5 T2 `@@LANE` defect note + Stage 13 EVID-S13-015/016。
- 对**关键历史 P0/P1 finding** 做 live 复验标注：M8-2-P0-1（cross-JVM distributed mutex）、M8-2-P1-6（cross-JVM zombie）——据 live 行为标 `finding_id` + `disposition`（cross-JVM residual 标 `residual-risk` + successor ownership）。
- 对 **Kafka/Pulsar gated-but-blocked** 能力产出 `disposition: blocked` evidence row，cross-ref Stage 5 T3/T4 `@@LANE` block + `rerun_condition`。
- 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验通过且非空过；corpus finding_id 交叉标注合法。

## Non-Goals

- Connector-specific source/sink 保证（exactly-once / cursor / offset / external transaction）——属 Stages 15/16（本计划只验证通用 data-plane transport，不验证 connector 业务语义）。
- Checkpoint barrier 对齐 / state backend / window / CEP 语义——属 Stages 9/10/11/12（本计划只在 `finding_id` 交叉中标注相关 finding 的 live 复验结果）。
- Control-plane RPC / leader election / fencing epoch 编码——属 Stage 13（已 done；本计划吸收其 cross-JVM residual 复验，但不重审 control-plane 本身）。
- 修复本审计发现的 confirmed live defect（按 roadmap 规则指派 remediation plan）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-14-data-plane-multi-jvm-recovery.evidence.md`（domain evidence rows，manifest 域 a/d/e/g 范围内的 transport/execution/multijvm source anchor + test lane）。**文件名必须是 `*.evidence.md` 且为 audit dir 直系子文件。**
- 支持/拒绝能力矩阵文本（写入证据文件头部，仅矩阵/判据不改 frozen 字段/词表）。

### Out Of Scope

- 修复 confirmed live defect（指派 remediation plan）。
- Connector source/sink 业务保证（Stages 15/16）。
- Checkpoint/state backend/window/CEP 语义（Stages 9/10/11/12）。
- Control-plane/HA/fencing 本身（Stage 13，已 done）。
- 修改 frozen evidence-row 11 字段定义或 7 分类词表。

## Execution Plan

### Phase 1 - Data-Plane Transport Record/Barrier/Watermark Evidence (in-process)

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-14-data-plane-multi-jvm-recovery.evidence.md`

- Item Types: `Proof`

- [x] 产出 record transport evidence row：`source_anchor` 指向 `RemoteResultPartition.write():153`（stamps epochId `:163/165`）+ `RemoteInputChannel.EnvelopeConsumer.onMessage():352`（decode+enqueue `:402-419`）；`positive_proof` 引用 `TestRemoteDataExchange#testRemoteInputChannelReceivesRecords`；`runtime_wiring: wired`。
- [x] 产出 barrier transport evidence row：`source_anchor` 指向 `StreamMessageEnvelope` barrier type `:33` + `RemoteResultPartition.write()` + `RemoteInputChannel` barrier path；`positive_proof` 引用 `TestRemoteDataExchange#testRemoteBarrierExchange`。
- [x] 产出 watermark transport evidence row：`source_anchor` 指向 `StreamMessageEnvelope` watermark type `:34` + transport path；`positive_proof` 引用 `TestRemoteDataExchange#testRemoteWatermarkExchange`。
- [x] 产出 epoch fencing discard evidence row：`source_anchor` 指向 `RemoteInputChannel.EnvelopeConsumer.onMessage():368`（discard wrong-epoch envelope）+ `RemoteResultPartition` stamping `:163`；`positive_proof` 引用 `TestRemoteDataExchange#testRemoteInputChannelFencingRejectsStaleMessages`；`rejection_proof` 引用 `TestRemoteInputChannelHeartbeat#testWrongEpochHeartbeatDoesNotRefreshLiveness`。
- [x] 产出 EOS propagation evidence row：`source_anchor` 指向 `RemoteResultPartition` EOS stamping on close `:184-187` + `RemoteInputChannel` EOS branching `:382-399`；`positive_proof` 引用 `TestRemoteDataExchange#testRemoteInputChannelEndOfStream`。
- [x] 产出 heartbeat/liveness evidence row：`source_anchor` 指向 `RemoteResultPartition` heartbeat envelope `:252-255` + `RemoteInputChannel` liveness refresh `:379`；`positive_proof` 引用 `TestRemoteInputChannelHeartbeat#testProducerSendsIdleHeartbeatWhenIdle` + `#testConsumerTimesOutWhenSilent`。
- [x] 每条 row 标注 `required_lane`（record/barrier/watermark/fencing/EOS/heartbeat 最低 `in-process`）与 `finding_id`（交叉 corpus，如 `none`）。

Exit Criteria:

- [x] ≥6 条 in-process data-plane transport evidence row（record/barrier/watermark/fencing/EOS/heartbeat），格式经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验 exit 0，且校验器实际解析到行（非 "0 evidence rows yet" 空过）
- [x] **端到端验证（Rule #22）**：至少一条 transport row 的 `positive_proof` 是真实 in-process 实跑测试名（`ClassName#method`），`environment_class >= in-process`，`disposition: e2e-proved`；不得用 component/unit 测试充数
- [x] **接线验证（Rule #23）**：transport row 的 `runtime_wiring` 据 in-process 实跑裁定（`RemoteResultPartition.write()` → wire-codec → backend → `RemoteInputChannel` 消费确实连通），非仅方法存在
- [x] **无静默跳过（Rule #24）**：任一 transport 环节无法在 in-process 实跑的，row `disposition` 标 `unverified`
- [x] `No owner-doc update required`（证据文件是审计产出；不改 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Wire-Codec Backend Matrix & Deployment-Descriptor Reconstruction Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-14-data-plane-multi-jvm-recovery.evidence.md`

- Item Types: `Proof | Decision`

- [x] 产出 Identity codec evidence row（LocalMessageService default）：`source_anchor` 指向 `IdentityWireCodec.java:20` + `stream-data-plane.beans.xml:76`；`positive_proof` 引用 Phase 1 in-process 测试；`environment_class: in-process`；`required_lane: in-process`；`disposition: e2e-proved`。
- [x] 产出 SysDao codec evidence row（DB backend，always-on）：`source_anchor` 指向 `SysDaoWireCodec.java:36`（nop-stream-side anchor）+ `DataPlaneMessageServiceAdapter`（`RpcDistributedExecutor.java:270`）；backend `SysDaoMessageService`（nop-sys-dao，manifest 域外）作 note 不作 source_anchor；`positive_proof` 引用 `TestDataPlaneSysDaoBackendE2E#recordsTraverseNopSysEventTableExactlyOnce` + `#barrierAndWatermarkTraverseBackend` + `#fencingRejectsStaleEpochOverBackend` + `#endOfStreamPropagatesThroughBackend`；`environment_class: in-process`；`required_lane: in-process`；`disposition: e2e-proved`。
- [x] 产出 Kafka codec evidence row（gated external backend）：`source_anchor` 指向 `KafkaStringWireCodec.java:42`（nop-stream-side anchor；backend `KafkaMessageService` 在 nop-message-kafka，作 note 不作 source_anchor，保持 manifest 域内）；`positive_proof` 引用 `TestDataPlaneKafkaBackendE2E#recordBarrierWatermarkTraverseKafkaTopic`；`disposition: blocked`；`required_lane: in-process`（data-plane transport 语义仅需 in-process lane 强度；非 control-plane claim）；`environment_class: none`（gated-skip，gated-evidence 规则 S5-1：skipped gated test 不是 evidence）；cross-ref Stage 5 T3 `@@LANE` block + `rerun_condition`。
- [x] 产出 Pulsar codec evidence row（gated external backend）：`source_anchor` 指向 `PulsarStringWireCodec.java:39`（nop-stream-side anchor；backend `PulsarMessageService` 作 note）；`positive_proof` 引用 `TestDataPlanePulsarBackendE2E#recordBarrierWatermarkTraversePulsarTopic`；`disposition: blocked`；`required_lane: in-process`；`environment_class: none`；cross-ref Stage 5 T4 `@@LANE` block + `rerun_condition`。
- [x] 产出 deployment-descriptor cross-JVM reconstruction evidence row：`source_anchor` 指向 `TaskDeploymentDescriptor.java:51`（serializable JobGraph/DeploymentPlan/fencingEpoch）+ `RemoteGraphExecutionPlanBuilder.buildRemoteOnly():81`（`StreamTopicNaming.buildTopic` `:115`，producer `:117`/consumer `:122`）+ `SubtaskPlanBuilder.java:61/72`（TaskManager-local rebuild counterpart）；`positive_proof` 引用 in-process remote-deploy 测试（如 `TestJobCoordinatorRemoteDeploy`）或 `manual-trace:` 验证 coordinator-built vs TaskManager-rebuilt topic/codec 接线一致；`environment_class: in-process`；`required_lane: in-process`；`disposition: e2e-proved`（if 真实 in-process remote-deploy 测试存在并实跑）或 `component-only`（若仅 manual-trace 证明 deterministic wiring 一致而无端到端实跑）。
- [x] 冻结 **wire-codec backend 支持/拒绝矩阵**文本（写入证据文件头部）：Identity（SUPPORTED, in-process）、SysDao（SUPPORTED, in-process）、Kafka（SUPPORTED-but-BLOCKED, gated T3）、Pulsar（SUPPORTED-but-BLOCKED, gated T4）。

Exit Criteria:

- [x] ≥4 条 wire-codec backend evidence row（Identity/SysDao/Kafka/Pulsar）+ ≥1 条 deployment-descriptor reconstruction row，格式校验 exit 0
- [x] **端到端验证（Rule #22）**：SysDao row 的 `positive_proof` 引用 always-on in-process 实跑测试（`TestDataPlaneSysDaoBackendE2E` 4 个方法），`environment_class >= in-process`，`disposition: e2e-proved`
- [x] **无静默跳过（Rule #24）**：Kafka/Pulsar gated-backend 不得被静默当作 `e2e-proved`——须显式标 `blocked` + cross-ref Stage 5 T3/T4 `@@LANE` block + `rerun_condition`（gated-evidence 规则 S5-1：skipped gated test 不是 evidence）
- [x] **接线验证（Rule #23）**：deployment-descriptor row 的 `runtime_wiring` 据 in-process 实跑或 manual-trace 裁定（`RemoteGraphExecutionPlanBuilder` vs `SubtaskPlanBuilder` deterministic topic wiring 确实一致），非仅类型存在
- [x] wire-codec 支持/拒绝矩阵在证据文件头部有显式文本
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - True Multi-JVM Recovery Evidence (T2 Lane) & T2 Defect Disposition

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-14-data-plane-multi-jvm-recovery.evidence.md`

- Item Types: `Proof | Decision`

- [x] 产出 T2 multi-JVM infrastructure evidence row：`source_anchor` 指向 `MiniStreamCluster.java:78`（child-JVM spawn `:137-138/410`，`spawnJobCoordinator` `:389`，`logFileFor` `:310`，H2 AUTO_SERVER `:134`）；`positive_proof` 引用 `TestMiniStreamClusterProcessSpawn`（3/3 PASS，TM registration `:58`，coordinator-0 log `:70`，kill+restart `:74-98`）；`environment_class: multi-jvm`，`required_lane: multi-jvm`，`disposition: e2e-proved`（for infrastructure：real cross-JVM spawn + registration + coordinator boot）。
- [x] 产出 T2 `TestMultiJvmExactlyOnceRecovery` defect evidence row：`source_anchor` 指向 `TestMultiJvmExactlyOnceRecovery.java:111`（`cluster.logFileFor("coordinator")` missing `-0` suffix）+ `MiniStreamCluster.java:404`（writes `"coordinator-"+index`）；`disposition: blocked`；`required_lane: multi-jvm`；cross-ref Stage 5 T2 `@@LANE` defect note + Stage 13 EVID-S13-015；注明 "log-label mismatch prevents cross-JVM exactly-once recovery evidence; owned by Stage 14"。
- [x] 产出 T2 `TestMultiJvmCoordinatorFailover` defect evidence row：`source_anchor` 指向 `TestMultiJvmCoordinatorFailover.java:106`（`testBrainSplitFencingBoundary`，assert `:129` "coordinator-1 must take over"）；`disposition: blocked`；`required_lane: multi-jvm`；cross-ref Stage 5 T2 `@@LANE` defect note + Stage 13 EVID-S13-016；注明 "HA-fencing takeover capability gap; owned by Stages 13/14"。
- [x] 产出 embedded-vs-multi-JVM boundary evidence row：`source_anchor` 指向 `RpcDistributedExecutor.java:183`（in-process wiring）vs `MiniStreamCluster`（true process boundary）+ `TaskDeploymentDescriptor.java:18-49`（cross-JVM rebuild contract）；`environment_class: in-process`；`required_lane: multi-jvm`；`disposition: residual-risk`——注明 in-process 测试证明 wiring 但 true process-boundary recovery 受 T2 defect 阻塞。
- [x] 产出 cross-JVM fencing residual evidence row（M8-2-P0-1 absorbed from Stage 13）：`source_anchor` 指向 `RemoteInputChannel.java:368`（single-long-epoch discard）+ `RemoteResultPartition.java:163`（stamp）；`disposition: residual-risk`；`required_lane: multi-jvm`；注明 in-process lane 证明 epoch discard，true cross-JVM distributed mutex 受 T2 defect 阻塞；cross-ref Stage 13 EVID-S13-012。
- [x] 产出 cross-JVM zombie fencing residual evidence row（M8-2-P1-6 absorbed from Stage 13）：`disposition: residual-risk`；`required_lane: multi-jvm`；cross-ref Stage 13 EVID-S13-013/019。

Exit Criteria:

- [x] ≥1 条 T2 multi-JVM infrastructure `e2e-proved` row + ≥2 条 T2 defect `blocked` row + ≥1 条 embedded-vs-multi-JVM boundary row + ≥2 条 cross-JVM residual row，格式校验 exit 0
- [x] **端到端验证（Rule #22）**：T2 infrastructure row 的 `positive_proof` 引用真实 multi-JVM 实跑测试（`TestMiniStreamClusterProcessSpawn` 3/3 PASS，real child JVM spawn），`environment_class: multi-jvm`，`disposition: e2e-proved`（仅 for infrastructure capability）
- [x] **无静默跳过（Rule #24）**：T2 deeper defect 不得被静默当作 `qualified`/`e2e-proved`——须显式标 `blocked` + cross-ref Stage 5 T2 defect note（Stage 13 EVID-S13-015/016）；cross-JVM residual 不得被静默当作 `e2e-proved`——须标 `residual-risk` + `required_lane: multi-jvm` + successor ownership
- [x] **required-lane/blocked-gate（Rule S5-2）**：每条 `blocked` row name the unqualified lane（cross-ref `@@LANE` block）；每条 `residual-risk` cross-JVM row 注明 `required_lane: multi-jvm`
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）；finding_id 交叉标注合法
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Unaligned In-Flight Capture & Historical Finding Revalidation

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-14-data-plane-multi-jvm-recovery.evidence.md`

- Item Types: `Proof | Decision`

- [x] 产出 unaligned in-flight capture evidence row：`source_anchor` 指向 `RemoteInputChannel.captureInFlightData():277` + `injectElements():298`；`positive_proof` 引用 `TestUnalignedCheckpointBackpressure#testNoDuplicatesRecordsBeforeBarrierNotReplayed`；`environment_class: in-process`；`required_lane: in-process`；`disposition: e2e-proved`。
- [x] 产出 buffer-pool boundary evidence row：`source_anchor` 指向 `TestBufferPoolRemoteExclusion`（`RemoteResultPartition`/`RemoteInputChannel` bypass per-job `IBufferPool`）；`environment_class: in-process`；`required_lane: in-process`；`disposition: e2e-proved`——注明 cross-JVM bound is `IMessageService` backend not buffer pool。
- [x] 对关键历史 P0/P1 finding 做 live 复验标注 evidence row（至少 M8-2-P0-1 cross-JVM distributed mutex、M8-2-P1-6 cross-JVM zombie）——据 live 行为标 `finding_id` + `disposition`（cross-JVM residual 标 `residual-risk` + successor ownership）。
- [x] 全 evidence 文件回归校验 + corpus 交叉标注核对。
- [x] 冻结 **data-plane 支持/拒绝矩阵**文本（写入证据文件头部）：record/barrier/watermark/fencing/EOS/heartbeat（SUPPORTED, in-process）、unaligned in-flight（SUPPORTED, in-process）、true multi-JVM recovery（PARTIALLY SUPPORTED — T2 infra qualified, deeper recovery/HA defect → blocked）。

Exit Criteria:

- [x] ≥2 条 unaligned/buffer-pool evidence row + ≥2 条 historical finding revalidation evidence row，格式校验 exit 0
- [x] **无静默跳过（Rule #24）**：cross-JVM residual 不得被静默当作 `e2e-proved`
- [x] data-plane 支持/拒绝矩阵在证据文件头部有显式文本
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）；finding_id 全部合法
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **审计计划（无生产代码变更）**：本计划产出为 evidence rows + 矩阵文本，不改 nop-stream 生产代码。`./mvnw test`/`compile` 不强制；改为以 evidence 校验器退出码 + in-process 实跑证据引用为 closure 依据。但若审计中发现 confirmed live defect，按 roadmap 规则指派 remediation plan（不在本计划内修复）。

- [x] data-plane transport 能力（record/barrier/watermark/fencing/EOS/heartbeat）各有 evidence row（in-process lane 实跑或如实标注缺覆盖）
- [x] wire-codec backend 矩阵（Identity/SysDao/Kafka/Pulsar）各有 evidence row，gated-blocked 显式标 `blocked` + cross-ref T3/T4
- [x] deployment-descriptor cross-JVM reconstruction 有 evidence row（coordinator-built vs TaskManager-rebuilt 接线一致性）
- [x] T2 multi-JVM infrastructure `e2e-proved` + 两个 deeper defect `blocked` + cross-ref Stage 5 T2 record
- [x] cross-JVM fencing/zombie residual（absorbed from Stage 13）有 live 复验 evidence row，标 `residual-risk` + `required_lane: multi-jvm` + successor ownership
- [x] 支持/拒绝矩阵显式成文
- [x] 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且**非空过**
- [x] 不存在被静默降级到 deferred 的 in-scope 审计项（T2 defect 标 `blocked`；cross-JVM residual 标 `residual-risk`；gated-backend 标 `blocked`——均为合法终态）
- [x] 审计发现的任何 confirmed live defect 已指派 active/successor remediation plan
- [x] `No owner-doc update required`（不改 `docs-for-ai/`）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）in-process transport row 的 `positive_proof` 确为实跑测试名（非 component/unit 充数），（b）`runtime_wiring=wired` 确经接线验证，（c）T2 defect 无静默放行（标 `blocked`），（d）gated-backend 无静默放行（标 `blocked` + gated-evidence 规则 S5-1），（e）cross-JVM residual 无静默当作 `e2e-proved`

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。预期场景：T2 lane 的 deeper capability test（`TestMultiJvmExactlyOnceRecovery` / `TestMultiJvmCoordinatorFailover`）有已知 defect——此类 row 应标 `disposition: blocked` + cross-ref T2 `@@LANE` defect + `blocked_reason`，而非 deferred，因为 `blocked` 是本计划合法终态并由 blocked-gate 规则承担后果。Kafka/Pulsar gated-backend 应标 `blocked` + cross-ref T3/T4。cross-JVM fencing/zombie residual 应标 `residual-risk` + successor ownership，非 deferred。）

## Non-Blocking Follow-ups

- T2 lane deeper defect 修复（`TestMultiJvmExactlyOnceRecovery` log-label、`TestMultiJvmCoordinatorFailover` HA-fencing takeover）→ independent remediation plan 或 connector/data-plane successor。
- cross-JVM fencing epoch final revalidation（M8-2-P0-1 distributed mutex，true cross-JVM mutex proof）→ remediation plan after T2 defect fixed。
- cross-JVM zombie task fencing（M8-2-P1-6）→ remediation plan after T2 defect fixed。
- Kafka/Pulsar real-backend evidence → provision broker（T3/T4 `rerun_condition`）then re-run。

## Closure

Status Note: Stage 14 data-plane & multi-JVM recovery audit completed. Produced `ai-dev/audits/nop-stream-independent-audit/stage-14-data-plane-multi-jvm-recovery.evidence.md` (21 evidence rows EVID-S14-001..021 + header matrices D1–D8 / W1–W4+DD / M1–M6). All in-process data-plane transport capabilities (record/barrier/watermark/fencing/EOS/heartbeat) e2e-proved on real in-process tests; SysDao DB-backed codec e2e-proved always-on; Kafka/Pulsar honestly `blocked` (T3/T4 gated, Rule S5-1); T2 infrastructure e2e-proved; two T2 deeper defects honestly `blocked`; cross-JVM fencing/zombie residuals honestly `residual-risk`. Validator `check-nop-stream-audit-manifest.mjs evidence --strict` EXIT=0, 21 rows parsed (non-empty).
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (fresh session, task_id ses_021d3904dffe7pev0upj4HMijd) — **VERDICT: APPROVED**, no blocking defects. Anti-Hollow checks (a–g) ALL PASS via live-source Read; every test method name and source anchor opened and confirmed real; no dishonest classification, fabricated test, or stale anchor found. Non-blocking observations: (1) EVID-S14-018 proxy exercises the invariant at generic InputGate/InputChannel level (RemoteInputChannel override provides it on its local queue — defensible); (2) TestMiniStreamClusterProcessSpawn is gated (intended T2 pattern, honestly classified multi-jvm); (3) a 22nd `@@EVIDENCE` token is prose in the validator header — validator correctly parses 21 real rows.
- Evidence (per Exit Criterion / Closure Gate + validator exit code + Anti-Hollow):
  - Phase 1 (≥6 in-process transport rows): SATISFIED — 6 rows EVID-S14-001..006 (record/barrier/watermark/fencing/EOS/heartbeat), all `disposition: e2e-proved`, `environment_class: in-process`, `required_lane: in-process`, `runtime_wiring: wired`. Rule #22: every transport row `positive_proof` is a real in-process test name (`TestRemoteDataExchange#testRemoteInputChannelReceivesRecords` etc.), confirmed by explore-agent line-read. Rule #23: `runtime_wiring: wired` adjudicated from `RemoteResultPartition.write()→:163 stamp→:165 send` to `RemoteInputChannel.onMessage():352→:402-406 enqueue` (full path connected). Rule #24: no transport segment left silently unverified.
  - Phase 2 (≥4 wire-codec + ≥1 deployment-descriptor): SATISFIED — EVID-S14-007 Identity / 008 SysDao (both `e2e-proved`) / 009 Kafka / 010 Pulsar (both `blocked`, `environment_class: none`) / 011 deployment-descriptor (`e2e-proved`, `TestJobCoordinatorRemoteDeploy#remoteDeployModeCallsDeployTaskNotReceiveAssignment`). Rule #22: SysDao row cites always-on in-process `TestDataPlaneSysDaoBackendE2E` (4 methods, @NopTestConfig(localDb=true), no gate). Rule #24: Kafka/Pulsar NOT silently `e2e-proved` — explicitly `blocked` + cross-ref T3/T4 @@LANE + rerun_condition + Rule S5-1 note. Rule #23: descriptor row `runtime_wiring: wired` per `RemoteGraphExecutionPlanBuilder.buildRemoteOnly():81` (StreamTopicNaming.buildTopic :115) vs `SubtaskPlanBuilder:61/72-94` deterministic topic wiring. Wire-codec support/reject matrix text frozen in evidence file header (W1–W4+DD).
  - Phase 3 (≥1 T2 infra e2e-proved + ≥2 T2 defect blocked + ≥1 boundary + ≥2 cross-JVM residual): SATISFIED — EVID-S14-012 T2 infra `e2e-proved` (`environment_class: multi-jvm`, `TestMiniStreamClusterProcessSpawn` 3/3 PASS) / 013 exactly-once defect `blocked` / 014 coordinator-failover defect `blocked` / 015 boundary `residual-risk` / 016 M8-2-P0-1 fencing `residual-risk` / 017 M8-2-P1-6 zombie `residual-risk`. Rule #22: T2 infra row cites real multi-jvm实跑. Rule #24: T2 defects NOT silently `qualified`/`e2e-proved` — `blocked` + cross-ref T2 @@LANE + Stage 13 EVID-S13-015/016. Rule S5-2: every `blocked` row names unqualified lane; every cross-JVM `residual-risk` row sets `required_lane: multi-jvm` + successor ownership.
  - Phase 4 (≥2 unaligned/buffer-pool + ≥2 historical revalidation): SATISFIED — EVID-S14-018 unaligned in-flight capture `e2e-proved` / 019 buffer-pool boundary `e2e-proved` / 020 M8-2-P0-1 revalidation `residual-risk` / 021 M8-2-P1-6 revalidation `residual-risk`. Data-plane support/reject matrix text frozen in evidence file header (D1–D8 + M1–M6). Rule #24: cross-JVM residuals NOT silently `e2e-proved`.
  - Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` EXIT=0, parses 21 EVID-S14 rows (non-empty; dispositions: 12 e2e-proved / 4 blocked / 5 residual-risk). finding_id cross-refs (M8-2-P0-1, M8-2-P1-6) all match registered corpus IDs.
  - Anti-Hollow Check: (a) in-process transport row `positive_proof` confirmed real test names (explore-agent line-read, not component/unit padding); (b) `runtime_wiring=wired` per live path; (c) T2 defects marked `blocked` (no silent pass); (d) gated-backends marked `blocked` + Rule S5-1 (no silent pass); (e) cross-JVM residuals marked `residual-risk` (never silently `e2e-proved`).
  - Plan is audit-only (no nop-stream production code change); only artifact is a new `.evidence.md` doc under `ai-dev/audits/`. Per the plan's own Closure Gates, `./mvnw` is not mandatory; closure basis = evidence validator exit code + in-process实跑 evidence references. No new confirmed live defect beyond the two known T2 defects (EVID-S14-013/014) already recorded in Stage 5 T2 + Stage 13.

Follow-up:

- T2 lane deeper defect fix (`TestMultiJvmExactlyOnceRecovery` log-label `:111`; `TestMultiJvmCoordinatorFailover` HA-fencing `:129`) → independent remediation plan (then EVID-S14-013/014 can be re-adjudicated `e2e-proved`).
- Cross-JVM fencing epoch final revalidation (M8-2-P0-1 distributed mutex) → remediation plan after T2 defect fixed.
- Cross-JVM zombie task fencing (M8-2-P1-6) → remediation plan after T2 defect fixed.
- Kafka/Pulsar real-backend evidence → provision broker (T3/T4 `rerun_condition`) then re-run.
