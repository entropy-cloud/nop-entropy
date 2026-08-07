# Stage 14 — Data Plane & Multi-JVM Recovery Evidence

> Status: produced by Stage 14 audit (plan `nop-stream-independent-audit/2026-08-08-0514-1-data-plane-multi-jvm-recovery-audit.md`)
> Domain: manifest a/d/e/g (nop-stream-core transport + nop-stream-runtime transport/execution/multijvm source surface + test lane; SysDao backend lives in nop-sys-dao and is referenced as a note, not a manifest-domain anchor, per scope rule)
> Lane policy: only `in-process` lane (single-JVM data-plane transport over `IMessageService` + wire-codec) or stronger is credited for data-plane transport (record/barrier/watermark/EOS/heartbeat/fencing) claims; `unit` is component-only. `in-process` is the MINIMUM credible lane for data-plane wiring (record must traverse RemoteResultPartition → wire-codec → backend → RemoteInputChannel). The cross-JVM SysDao backend (always-on T1 lane) lifts record/barrier/watermark/fencing/EOS transport to `in-process` over a real persisted backend. Kafka/Pulsar gated external backends (T3/T4) are `blocked` — the gate is effective and Rule S5-1 forbids a skipped gated test from being cited as evidence. True cross-JVM process recovery requires the T2 `multi-jvm` lane: its infrastructure is `qualified` (real process spawn + registration + coordinator boot), but its two deeper capability tests have defects (owned here as `blocked`) and the cross-JVM fencing/zombie residuals absorbed from Stage 13 are `residual-risk` requiring the multi-jvm lane.
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence` (parses `@@EVIDENCE` rows from `*.evidence.md` direct children of this dir)
> All source/test anchors in this file were verified against the live repo on 2026-08-08 (line anchors cross-checked by an explore agent reading each file with the Read tool; test method names confirmed by direct file read; the TestDataPlaneSysDaoBackendE2E path was confirmed to live in nop-sys-dao, not nop-stream-runtime, and the TestUnalignedCheckpointBackpressure package is `checkpoint/` not `transport/`).

## Support / Reject Combination Matrix (frozen by this audit — data plane / multi-JVM recovery)

This matrix adjudicates every supported and rejected data-plane / multi-JVM recovery combination. Each row cites the live source
anchor that implements or rejects it. The matrix changes neither the 11 evidence-row fields nor the 7-value disposition
vocabulary (frozen by Stage 4 `evidence-schema.md`).

### Data-Plane Transport Capability Matrix (entry-to-effect, in-process lane)

| # | Capability | Verdict | Lane | Live anchor (implementing) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| D1 | Record transport across TaskManager boundary | **SUPPORTED** | in-process | `RemoteResultPartition.write():153` → `StreamElementCodec.encode():163` stamps epochId → `messageService.send():165`; consumer `RemoteInputChannel.EnvelopeConsumer.onMessage():352` → decode `:402-406` → `queue.put():406` | EVID-S14-001 |
| D2 | Checkpoint barrier transport | **SUPPORTED** | in-process | `StreamMessageEnvelope.TYPE_CHECKPOINT_BARRIER:33`; encoded inline `StreamElementCodec.encode():65-68`; consumer barrier path `onMessage():402-406` | EVID-S14-002 |
| D3 | Watermark transport | **SUPPORTED** | in-process | `StreamMessageEnvelope.TYPE_WATERMARK:34`; encoded inline `StreamElementCodec.encode():71-73`; consumer `onMessage():402-406` | EVID-S14-003 |
| D4 | Epoch fencing discard (stale-epoch envelope dropped before processing) | **SUPPORTED** | in-process | Producer stamps epochId `RemoteResultPartition.write():163`; consumer `RemoteInputChannel.EnvelopeConsumer.onMessage():368-372` discards when `envelope.getEpochId() != expectedEpochId` BEFORE liveness refresh | EVID-S14-004 |
| D5 | End-of-stream propagation | **SUPPORTED** | in-process | Producer `RemoteResultPartition.close():184-187` sends `TYPE_CONTROL/CONTROL_END_OF_STREAM`; consumer EOS branch `onMessage():382-388` sets `finished`, offers END_OF_STREAM sentinel | EVID-S14-005 |
| D6 | Heartbeat / idle liveness | **SUPPORTED** | in-process | Producer `RemoteResultPartition.sendHeartbeatIfIdle():252-255` emits `CONTROL_HEARTBEAT`; consumer refreshes `lastReceivedTime:379` only AFTER fence passes, heartbeat branch `:389-395` does not enqueue | EVID-S14-006 |
| D7 | Unaligned checkpoint in-flight capture / replay | **SUPPORTED** | in-process | `RemoteInputChannel.captureInFlightData():277` drains local queue (preserves EOS); `injectElements():298` re-injects captured in-flight at front after recovery | EVID-S14-018 |
| D8 | Buffer-pool boundary (cross-JVM bound = IMessageService backend, not buffer pool) | **SUPPORTED (by design G53)** | in-process | `RemoteResultPartition` calls `super(1)` and sends directly via `messageService` (buffer pool intentionally bypassed, RemoteResultPartition.java:50-56); `RemoteInputChannel` uses a dummy ResultPartition with no pool | EVID-S14-019 |

### Wire-Codec Backend Support / Reject Matrix

| # | Backend (codec) | Verdict | Lane | Live anchor (codec) | Backend location | Evidence row |
| --- | --- | --- | --- | --- | --- | --- |
| W1 | Identity (LocalMessageService default) | **SUPPORTED** | in-process | `IdentityWireCodec.java:20` (passthrough); wired `stream-data-plane.beans.xml:76-77` | `LocalMessageService` (nop-message-core) | EVID-S14-007 |
| W2 | SysDao (DB-backed, always-on) | **SUPPORTED** | in-process | `SysDaoWireCodec.java:36` (`ApiRequest{data:map}` :41-48); wired via `DataPlaneMessageServiceAdapter` (`RpcDistributedExecutor.java:270-272`) | `SysDaoMessageService` (nop-sys-dao) | EVID-S14-008 |
| W3 | Kafka (StringSerializer, JSON wire) | **SUPPORTED-but-BLOCKED** | none (gated T3) | `KafkaStringWireCodec.java:42` (`toWire :47` JSON-stringify) | `KafkaMessageService` (nop-message-kafka) — note only, manifest out-of-domain | EVID-S14-009 |
| W4 | Pulsar (Schema.STRING, JSON wire) | **SUPPORTED-but-BLOCKED** | none (gated T4) | `PulsarStringWireCodec.java:39` (`toWire :44-49`) | `PulsarMessageService` (nop-message-pulsar) — note only, manifest out-of-domain | EVID-S14-010 |
| DD | Deployment-descriptor cross-JVM reconstruction (topic/codec deterministic wiring) | **SUPPORTED** | in-process | `TaskDeploymentDescriptor.java:51` (serializable JobGraph/DeploymentPlan/fencingEpoch); coordinator-built `RemoteGraphExecutionPlanBuilder.buildRemoteOnly():81` (`StreamTopicNaming.buildTopic :115`, producer `:117`/consumer `:122`) vs TaskManager-local rebuild `SubtaskPlanBuilder.java:61/72-94` | both sides use the same deterministic `StreamTopicNaming` | EVID-S14-011 |

### Multi-JVM Recovery / Cross-JVM Residual Matrix (T2 lane)

| # | Combination | Verdict | Lane | Live anchor | Evidence row |
| --- | --- | --- | --- | --- | --- |
| M1 | Cross-JVM process spawn + TM registration + coordinator boot (INFRASTRUCTURE) | **SUPPORTED** | multi-jvm | `MiniStreamCluster.java:78` (child-JVM spawn `:137-138`, `spawnJobCoordinator :389`, `logFileFor :310`, H2 `AUTO_SERVER=TRUE :134`); `TestMiniStreamClusterProcessSpawn` 3/3 PASS | EVID-S14-012 |
| M2 | Cross-JVM exactly-once recovery (kill/restart → stale-attempt fencing → recovered sink-result) | **BLOCKED — deeper test defect** | multi-jvm | `TestMultiJvmExactlyOnceRecovery.java:111` reads `logFileFor("coordinator")` (missing `-0`) → `NoSuchFileException`; `MiniStreamCluster.java:404` writes `"coordinator-"+index` | EVID-S14-013 |
| M3 | Cross-JVM HA-fencing takeover (brain-split, coordinator-1 must take over) | **BLOCKED — deeper test defect** | multi-jvm | `TestMultiJvmCoordinatorFailover.java:129` assertion "coordinator-1 must take over" (`epoch1 > 0`) fails | EVID-S14-014 |
| M4 | Embedded-vs-multi-JVM boundary (in-process wiring proven; true process-boundary recovery blocked) | **PARTIALLY SUPPORTED — residual-risk** | multi-jvm | in-process `RpcDistributedExecutor.java:183,270-273` vs true process `MiniStreamCluster`; cross-JVM rebuild contract `TaskDeploymentDescriptor.java:18-49` | EVID-S14-015 |
| M5 | Cross-JVM fencing epoch revalidation (M8-2-P0-1 distributed mutex) | **PARTIALLY SUPPORTED — residual-risk** | multi-jvm | in-process epoch discard proven `RemoteInputChannel.java:368` + stamping `RemoteResultPartition.java:163`; true cross-JVM distributed mutex needs multi-jvm lane | EVID-S14-016 |
| M6 | Cross-JVM zombie task fencing (M8-2-P1-6) | **PARTIALLY SUPPORTED — residual-risk** | multi-jvm | LOCAL zombie mitigation hardened (Stage 13 EVID-S13-013/019); true cross-JVM stale-attempt zombie fencing needs multi-jvm lane | EVID-S14-017 |

Adjudication rules applied (consistent with Stage 4 schema + Stage 5 supplement):
- A supported data-plane transport capability gets an entry-to-effect evidence row with `disposition: e2e-proved` when an in-process
  test traces record/barrier/watermark from `RemoteResultPartition.write()` through wire-codec + backend to `RemoteInputChannel`
  consumption (wiring actually connected), or an honest weaker disposition when only a segment is exercised.
- A capability needing a cross-JVM external backend (Kafka/Pulsar) that is gated-but-blocked gets `disposition: blocked` with the
  unqualified lane named (T3/T4 `@@LANE` block) and a `rerun_condition` — a skipped gated test is NEVER silently upgraded to
  `e2e-proved` (gated-evidence Rule S5-1).
- A capability needing the cross-JVM lane whose deeper test has a defect gets `disposition: blocked` with the defect named and
  cross-ref to Stage 13 EVID-S13-015/016 + Stage 5 T2 `@@LANE` note (honest `blocked`, not silent skip — T2 infrastructure IS qualified).
- A cross-JVM residual absorbed from Stage 13 gets `disposition: residual-risk` with `required_lane: multi-jvm` + successor ownership,
  never silently upgraded to `e2e-proved`.

---

## Evidence Rows

### Phase 1 — Data-Plane Transport Record/Barrier/Watermark Evidence (in-process)

@@EVIDENCE
inventory_id: EVID-S14-001
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteResultPartition.java:153,163,165
declared_guarantee: Record transport across TaskManager boundary — RemoteResultPartition.write encodes the StreamRecord via StreamElementCodec.encode(element, valueType, epochId) (epochId stamped into the envelope at :163) and sends it via messageService.send(topic, envelope) (:165); the consumer RemoteInputChannel.EnvelopeConsumer.onMessage (:352) decodes (:402-406) and enqueues (queue.put :406) the record, so a record traverses producer→wire→backend→consumer end-to-end
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:352,402-406
runtime_wiring: wired
positive_proof: TestRemoteDataExchange#testRemoteInputChannelReceivesRecords
rejection_proof: TestRemoteDataExchange#testRemoteResultPartitionSendsEnvelope
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S14-002
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamMessageEnvelope.java:33
declared_guarantee: Checkpoint barrier transport — StreamMessageEnvelope.TYPE_CHECKPOINT_BARRIER is encoded inline by StreamElementCodec.encode (:65-68, payload = the barrier object, no valueType); RemoteResultPartition.write sends the envelope (:163-165) and the consumer RemoteInputChannel.onMessage (:402-406) decodes it back into a CheckpointBarrier, so a barrier crosses the data plane in-band on the same topic as records
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java:65-68,121-127
runtime_wiring: wired
positive_proof: TestRemoteDataExchange#testRemoteBarrierExchange
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S14-003
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamMessageEnvelope.java:34
declared_guarantee: Watermark transport — StreamMessageEnvelope.TYPE_WATERMARK is encoded inline by StreamElementCodec.encode (:71-73, payload = the Watermark object); RemoteResultPartition.write sends the envelope (:163-165) and the consumer RemoteInputChannel.onMessage (:402-406) decodes it back into a Watermark, so a watermark event propagates across the data plane
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java:71-73,129-134
runtime_wiring: wired
positive_proof: TestRemoteDataExchange#testRemoteWatermarkExchange
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S14-004
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:368-372
declared_guarantee: Epoch fencing discard — EnvelopeConsumer.onMessage discards any envelope whose epochId != expectedEpochId BEFORE refreshing liveness (:368 returns null for a stale-epoch envelope); RemoteResultPartition stamps epochId into every outbound envelope (:163), so a stale-epoch data-plane envelope (from a prior leader/recovery) is dropped not processed. A wrong-epoch heartbeat never reaches the liveness-refresh line, so stale heartbeats do NOT count as producer liveness (fencing invariant)
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteResultPartition.java:163-164
runtime_wiring: wired
positive_proof: TestRemoteDataExchange#testRemoteInputChannelFencingRejectsStaleMessages
rejection_proof: TestRemoteInputChannelHeartbeat#testWrongEpochHeartbeatDoesNotRefreshLiveness
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S14-005
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteResultPartition.java:184-187
declared_guarantee: End-of-stream propagation — RemoteResultPartition.close stops the heartbeat task then sends a TYPE_CONTROL envelope with payload CONTROL_END_OF_STREAM carrying the current epochId (:184-187); the consumer RemoteInputChannel.onMessage EOS branch (:384-388) sets finished=true and offers the END_OF_STREAM sentinel to the queue, so producer completion is signalled across the data plane rather than silently closing
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:382-399
runtime_wiring: wired
positive_proof: TestRemoteDataExchange#testRemoteInputChannelEndOfStream
rejection_proof: TestRemoteInputChannelHeartbeat#testEndOfStreamIsNotHeartbeatTimeout
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S14-006
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteResultPartition.java:252-255
declared_guarantee: Heartbeat / idle liveness — when heartbeat is enabled and no data has flowed for heartbeatIntervalMs, RemoteResultPartition.sendHeartbeatIfIdle emits a TYPE_CONTROL/CONTROL_HEARTBEAT envelope carrying the current epochId (:252-255); the consumer refreshes lastReceivedTime AFTER the fence passes (:379) and the heartbeat branch (:389-395) does NOT enqueue anything, so an idle producer keeps the consumer's liveness clock alive without injecting a fake data element, and a sustained producer failure trips the consumer timeout
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:379,389-395
runtime_wiring: wired
positive_proof: TestRemoteInputChannelHeartbeat#testProducerSendsIdleHeartbeatWhenIdle
rejection_proof: TestRemoteInputChannelHeartbeat#testConsumerTimesOutWhenSilent
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 2 — Wire-Codec Backend Matrix & Deployment-Descriptor Reconstruction

@@EVIDENCE
inventory_id: EVID-S14-007
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/IdentityWireCodec.java:20
declared_guarantee: Identity wire-codec (LocalMessageService default) — IdentityWireCodec.toWire returns the envelope object unchanged (carried by reference in-process); it is the default codec selected by stream-data-plane.beans.xml (:76-77, ioc:default IdentityWireCodec), so the in-process LocalMessageService data plane carries the full StreamMessageEnvelope object graph with zero serialization loss
implementation_anchor: nop-stream/nop-stream-runtime/src/main/resources/_vfs/nop/stream/beans/stream-data-plane.beans.xml:76-77
runtime_wiring: wired
positive_proof: TestRemoteDataExchange#testRemoteInputChannelReceivesRecords
rejection_proof: TestStreamModuleDiscovery
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S14-008
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/SysDaoWireCodec.java:36
declared_guarantee: SysDao wire-codec (DB-backed, always-on) — SysDaoWireCodec.toWire wraps the envelope as ApiRequest{data: envelope-as-JSON-serializable-map} (:41-48 via DataPlaneWireSupport.toWireMap, flattening barrier/watermark control objects) so SysDaoMessageService persists the full envelope body into NopSysEvent.eventData; fromWire reconstructs the envelope from the delivered map/String. The codec references only ApiRequest/JsonTool (no nop-sys-dao import), so nop-stream-runtime stays backend-dep-free while the real DB backend carries record/barrier/watermark/EOS/fencing across a persisted boundary (not in-memory by reference)
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RpcDistributedExecutor.java:270-272
runtime_wiring: wired
positive_proof: TestDataPlaneSysDaoBackendE2E#recordsTraverseNopSysEventTableExactlyOnce
rejection_proof: TestDataPlaneSysDaoBackendE2E#fencingRejectsStaleEpochOverBackend
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S14-009
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/KafkaStringWireCodec.java:42
declared_guarantee: Kafka wire-codec (gated external backend — BLOCKED) — KafkaStringWireCodec.toWire serializes the envelope to a JSON String (:47) matching KafkaMessageService StringSerializer wire format; fromWire reconstructs the envelope from the delivered ApiMessage data String. The transport path is fully implemented on the nop-stream side, but its positive evidence requires a qualified Kafka broker lane. Lane T3 (environment-qualification.md) is `blocked` — no Kafka broker provisioned (localhost:9092 closed); the gate @EnabledIfSystemProperty(nop.stream.test.kafka.enabled) is verified effective (default Skipped). Per gated-evidence Rule S5-1 a skipped gated test is NOT evidence, so this row is `blocked`, NOT e2e-proved. The gated test TestDataPlaneKafkaBackendE2E#recordBarrierWatermarkTraverseKafkaTopic is the rerun target, not current evidence. rerun_condition: provision a Kafka broker and rerun with -Dnop.stream.test.kafka.enabled=true -Dnop.stream.test.kafka.brokers=<host:9092>
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/KafkaStringWireCodec.java:47-49
runtime_wiring: wired
positive_proof: TestDataPlaneKafkaBackendE2E#recordBarrierWatermarkTraverseKafkaTopic
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: blocked
@@END

@@EVIDENCE
inventory_id: EVID-S14-010
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/PulsarStringWireCodec.java:39
declared_guarantee: Pulsar wire-codec (gated external backend — BLOCKED) — PulsarStringWireCodec.toWire serializes the envelope to a JSON String (:44-49) matching PulsarMessageService Schema.STRING wire format; fromWire reconstructs the envelope from the delivered ApiMessage data String. The transport path is fully implemented on the nop-stream side, but its positive evidence requires a qualified Pulsar service lane. Lane T4 (environment-qualification.md) is `blocked` — no Pulsar service provisioned (localhost:6650 closed); the gate @EnabledIfSystemProperty(nop.stream.test.pulsar.enabled) is verified effective (default Skipped). Per gated-evidence Rule S5-1 a skipped gated test is NOT evidence, so this row is `blocked`, NOT e2e-proved. The gated test TestDataPlanePulsarBackendE2E#recordBarrierWatermarkTraversePulsarTopic is the rerun target, not current evidence. rerun_condition: provision a Pulsar service and rerun with -Dnop.stream.test.pulsar.enabled=true -Dnop.stream.test.pulsar.serviceUrl=pulsar://<host:6650>
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/PulsarStringWireCodec.java:44-49
runtime_wiring: wired
positive_proof: TestDataPlanePulsarBackendE2E#recordBarrierWatermarkTraversePulsarTopic
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: blocked
@@END

@@EVIDENCE
inventory_id: EVID-S14-011
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/rpc/TaskDeploymentDescriptor.java:51
declared_guarantee: Deployment-descriptor cross-JVM reconstruction — TaskDeploymentDescriptor (@DataBean Serializable :51) carries the serializable JobGraph (:84), DeploymentPlan (:90), fencingEpoch (:75) and checkpointRestorePath (:99) so a TaskManager JVM rebuilds its own StreamTaskInvokable locally (no live operator reference crosses the JVM, javadoc :18-49). Coordinator-built RemoteGraphExecutionPlanBuilder.buildRemoteOnly (:81) and TaskManager-local rebuild SubtaskPlanBuilder.buildSubtaskInvokable (:78-94) both derive topics via StreamTopicNaming.buildTopic(jobId, edgeId, s, t) (:115), so the two sides produce IDENTICAL topic names (coordinator-side producer :117 / consumer :122; TaskManager-local rebuild reuses the same builder :90-94) and cross-JVM data exchange connects deterministically
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/SubtaskPlanBuilder.java:61,72-94
runtime_wiring: wired
positive_proof: TestJobCoordinatorRemoteDeploy#remoteDeployModeCallsDeployTaskNotReceiveAssignment
rejection_proof: TestJobCoordinatorRemoteDeploy#remoteDeployModeFailsFastWithoutJobGraph
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 3 — True Multi-JVM Recovery Evidence (T2 Lane) & T2 Defect Disposition

@@EVIDENCE
inventory_id: EVID-S14-012
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/MiniStreamCluster.java:78
declared_guarantee: T2 multi-JVM INFRASTRUCTURE (qualified) — MiniStreamCluster spawns real child JVMs via ProcessBuilder using java.class.path (:137) and java.home/bin/java (:138), shares state across JVMs through an H2 file DB with AUTO_SERVER=TRUE (:134), spawns a coordinator JVM via spawnJobCoordinator (:389) writing the process label "coordinator-"+index (:404), and resolves per-node logs via logFileFor (:310). This is genuine cross-JVM process spawn, NOT an in-process simulation. The T2 lane is `qualified` (environment-qualification.md) on this infrastructure: TestMiniStreamClusterProcessSpawn 3/3 PASS proves TaskManagers register in the shared cluster registry (:58), coordinator-0.log is created in the separate coordinator JVM (:70), and a killed tm-0 re-registers after restart (:74-98)
implementation_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMiniStreamClusterProcessSpawn.java:58,70,74-98
runtime_wiring: wired
positive_proof: TestMiniStreamClusterProcessSpawn#twoTaskManagersAndOneCoordinatorStartAndRegister
rejection_proof: none
environment_class: multi-jvm
required_lane: multi-jvm
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S14-013
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmExactlyOnceRecovery.java:111
declared_guarantee: T2 lane deeper defect — TestMultiJvmExactlyOnceRecovery reads cluster.logFileFor("coordinator") at :111 (and :103,:142,:146,:229) but MiniStreamCluster.spawnJobCoordinator keys coordinators as "coordinator-"+index (writes label "coordinator-"+index at MiniStreamCluster.java:404; coordinatorKey returns "coordinator-"+index at :229), so Files.size throws NoSuchFileException on the bare "coordinator" path (the real file is coordinator-0.log). The deeper cross-JVM exactly-once recovery assertion (kill/restart → stale-attempt fencing → recovered sink-result) cannot be evidenced until the log-label mismatch is fixed. Owned by Stage 14 (cross-ref Stage 5 T2 @@LANE defect note; Stage 13 EVID-S13-015). The T2 lane infrastructure itself is qualified (EVID-S14-012), so this is an honest capability-level `blocked`, NOT a silent skip
implementation_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/MiniStreamCluster.java:404
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: multi-jvm
finding_id: none
disposition: blocked
@@END

@@EVIDENCE
inventory_id: EVID-S14-014
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmCoordinatorFailover.java:129
declared_guarantee: T2 lane deeper defect — testBrainSplitFencingBoundary (:106) fails the assertion "coordinator-1 must take over" (assertTrue epoch1 > 0 at :129) exercising the HA lease-table rotation path; the cross-JVM HA-fencing takeover capability cannot be evidenced until the failover-takeover gap is fixed. Owned by Stages 13/14 (cross-ref Stage 5 T2 @@LANE defect note; Stage 13 EVID-S13-016). T2 lane infrastructure is qualified (EVID-S14-012), so this is an honest capability-level `blocked`, NOT a silent skip
implementation_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmCoordinatorFailover.java:106-132
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: multi-jvm
finding_id: none
disposition: blocked
@@END

@@EVIDENCE
inventory_id: EVID-S14-015
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RpcDistributedExecutor.java:183,270-273
declared_guarantee: Embedded-vs-multi-JVM boundary — the in-process distributed executor (RpcDistributedExecutor.startJob :183) wires TaskManagers, the coordinator and the data-plane plan (RemoteGraphExecutionPlanBuilder over DataPlaneMessageServiceAdapter :270-273, buildRemoteOnly :273) within ONE JVM; true process-boundary execution (MiniStreamCluster) replaces this in-JVM wiring with real cross-JVM RPC + shared message backend. The TaskDeploymentDescriptor cross-JVM rebuild contract (javadoc :18-49) is the bridge: it is built in-process here and reconstructed locally on a TaskManager JVM (SubtaskPlanBuilder). In-process wiring + descriptor reconstruction are proven (EVID-S14-011/012), but true process-boundary recovery (kill/restart → fencing → recovered result) is blocked by the two T2 deeper defects (EVID-S14-013/014), so the boundary as a recovery claim is a residual-risk, NOT e2e-proved
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/rpc/TaskDeploymentDescriptor.java:18-49
runtime_wiring: partial
positive_proof: TestRpcDistributedExecutorE2E#fullPipelineRunsOverRpcControlPlane
rejection_proof: none
environment_class: in-process
required_lane: multi-jvm
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S14-016
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:368
declared_guarantee: Cross-JVM fencing epoch revalidation (M8-2-P0-1 distributed mutex — absorbed from Stage 13) — the in-process lane proves data-plane epoch discard: EnvelopeConsumer.onMessage (:368) drops any envelope whose epochId != expectedEpochId and RemoteResultPartition stamps epochId into every outbound envelope (:163). However the underlying cross-JVM control-plane property (a single global fencing epoch rotation under concurrent distributed recovery drivers, i.e. a true distributed mutual-exclusion guarantee) is NOT independently provable in the in-process lane — it requires the multi-jvm lane, whose deeper exactly-once-recovery test has a defect (EVID-S14-013). Final cross-JVM distributed-mutex revalidation is a residual-risk owned by Stage 14 successor work (cross-ref Stage 13 EVID-S13-012 / EVID-S13-017)
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteResultPartition.java:163-164
runtime_wiring: partial
positive_proof: TestFencingEpochUnification#dataPlaneStaleEpochEnvelopeDiscardedCurrentAccepted
rejection_proof: none
environment_class: in-process
required_lane: multi-jvm
finding_id: M8-2-P0-1
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S14-017
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:492-503
declared_guarantee: Cross-JVM zombie task fencing revalidation (M8-2-P1-6 — absorbed from Stage 13) — LOCAL zombie mitigation is hardened: SupervisionLoop.waitForTerminal (:492-503) throws ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT when a task does not terminate within DEFAULT_TERMINAL_WAIT_BUDGET_MS (was silent WARN+return → two producers writing the same ResultPartition). True cross-JVM stale-attempt zombie fencing (rejecting OUTPUT from a zombie producer running in another JVM) is a cross-JVM control-plane property requiring the multi-jvm lane; it is NOT independently provable in-process. Residual-risk owned by Stages 13/14 successor work (cross-ref Stage 13 EVID-S13-013 / EVID-S13-019); the T2 HA-fencing takeover test has a known defect (EVID-S14-014)
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1499-1544
runtime_wiring: partial
positive_proof: TestSupervisionLoopZombieTaskTimeout#waitForTerminal_timesOut_failsLoudWithZombieTimeoutError
rejection_proof: none
environment_class: in-process
required_lane: multi-jvm
finding_id: M8-2-P1-6
disposition: residual-risk
@@END

### Phase 4 — Unaligned In-Flight Capture, Buffer-Pool Boundary & Historical Finding Revalidation

@@EVIDENCE
inventory_id: EVID-S14-018
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:277
declared_guarantee: Unaligned checkpoint in-flight capture / replay — RemoteInputChannel.captureInFlightData(barrierReceived) (:277) drains the local queue into a list (re-placing the END_OF_STREAM sentinel if encountered, :281-285) so in-flight records buffered before the barrier can be checkpointed; injectElements(elements) (:298) re-injects captured in-flight records at the FRONT of the queue after recovery (before any newly delivered upstream records, :313-316), preserving the END_OF_STREAM sentinel (:320-322). This gives the unaligned-checkpoint no-duplicate/no-loss path on the remote channel's local queue
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:298-323
runtime_wiring: wired
positive_proof: TestUnalignedCheckpointBackpressure#testNoDuplicatesRecordsBeforeBarrierNotReplayed
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S14-019
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/transport/TestBufferPoolRemoteExclusion.java:33
declared_guarantee: Buffer-pool boundary (by design G53) — RemoteResultPartition calls super(1) and overrides write() to send directly via IMessageService, intentionally bypassing both the per-partition queue and the per-job IBufferPool (RemoteResultPartition.java:50-56 documents the exclusion); RemoteInputChannel uses a dummy ResultPartition whose queue it never consumes. The cross-JVM producer/consumer bound is therefore the IMessageService backend, NOT the buffer pool. Asserted by TestBufferPoolRemoteExclusion#remoteResultPartitionDoesNotConsumePool (:33) and #remoteInputChannelDummyPartitionHasNoPool (:72)
implementation_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/transport/TestBufferPoolRemoteExclusion.java:72
runtime_wiring: wired
positive_proof: TestBufferPoolRemoteExclusion#remoteResultPartitionDoesNotConsumePool
rejection_proof: TestBufferPoolRemoteExclusion#remoteInputChannelDummyPartitionHasNoPool
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S14-020
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:980-1004
declared_guarantee: Historical P0 finding revalidation (M8-2-P0-1 cross-JVM distributed mutex) — globalRecovery is hardened with fencing-epoch-before-lock ordering (snapshot epochAtEntry at :988, lock recoveryLock at :991) + late-arrival guard (:999-1004) that short-circuits a redundant concurrent rotation with an observable WARN, proven to serialize two concurrent recovery drivers to one epoch rotation in-process (TestJobCoordinatorRecoveryConcurrency). The distributed cross-JVM mutual-exclusion invariant remains a residual-risk: it is NOT independently provable in-process and the T2 exactly-once-recovery test is blocked by a defect (EVID-S14-013). NOT e2e-proved for the cross-JVM claim; in-process serialization IS e2e-proved (Stage 13 EVID-S13-014). Owned by Stage 14 successor work after T2 defect fix
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:988,991,999-1004
runtime_wiring: partial
positive_proof: TestJobCoordinatorRecoveryConcurrency#concurrentGlobalRecovery_serializesToOneRotation
rejection_proof: none
environment_class: in-process
required_lane: multi-jvm
finding_id: M8-2-P0-1
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S14-021
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:492-503
declared_guarantee: Historical P1 finding revalidation (M8-2-P1-6 cross-JVM zombie task fencing) — LOCAL zombie mitigation is hardened (waitForTerminal fail-loud timeout + per-region restart budget, TestSupervisionLoopZombieTaskTimeout). True cross-JVM stale-attempt zombie fencing (a zombie producer in one JVM continuing to emit into a shared data-plane topic after its epoch was superseded by a recovery in another JVM) is a cross-JVM control-plane property requiring the multi-jvm lane; the receiver-side epoch discard (EVID-S14-004) drops stale-epoch envelopes regardless of which JVM produced them, but full distributed-zombie prevention (stopping the zombie process) is NOT provable in-process. Residual-risk owned by Stages 13/14 successor work (cross-ref Stage 13 EVID-S13-013/019)
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:368-372
runtime_wiring: partial
positive_proof: TestSupervisionLoopZombieTaskTimeout#waitForTerminal_timesOut_failsLoudWithZombieTimeoutError
rejection_proof: none
environment_class: in-process
required_lane: multi-jvm
finding_id: M8-2-P1-6
disposition: residual-risk
@@END

---

## Cross-Reference Notes (final disposition of historical P0/P1 findings touched by this audit)

- **M8-2-P0-1** (cross-JVM distributed mutex / globalRecovery serialization): **residual-risk (cross-JVM residual).** The data-plane epoch discard is proven in-process (EVID-S14-004 / EVID-S14-016: `RemoteInputChannel.onMessage:368` drops stale-epoch envelopes; `RemoteResultPartition:163` stamps epochId). The control-plane serialization is proven in-process (Stage 13 EVID-S13-012/014/017; revalidated EVID-S14-020). The distributed cross-JVM mutual-exclusion invariant is NOT independently provable in the in-process lane and the T2 deeper exactly-once-recovery test has a log-label defect (EVID-S14-013). Final cross-JVM revalidation owned by Stage 14 successor remediation plan, after the T2 defect is fixed.
- **M8-2-P1-6** (cross-JVM zombie task fencing): **residual-risk (cross-JVM residual).** LOCAL zombie mitigation hardened (EVID-S14-017/021: `SupervisionLoop.waitForTerminal:492-503` fail-loud). Receiver-side epoch discard (EVID-S14-004) drops stale-epoch output regardless of producer JVM, but full distributed-zombie prevention (stopping the zombie process) requires the multi-jvm lane. The T2 HA-fencing takeover test has a known defect (EVID-S14-014). Owned by Stages 13/14 successor work.

## T2 Lane Defect Disposition (cross-ref Stage 5 T2 record `@@LANE` note)

- **TestMultiJvmExactlyOnceRecovery log-label mismatch** (`:111` reads `logFileFor("coordinator")`; `MiniStreamCluster:404` writes `"coordinator-0"`): `disposition: blocked`, `required_lane: multi-jvm` (EVID-S14-013). The T2 lane is `qualified` at infrastructure level (`TestMiniStreamClusterProcessSpawn` 3/3 PASS, EVID-S14-012), so this is an honest capability-level `blocked`, NOT a silent skip — the deeper cross-JVM exactly-once-recovery evidence cannot be produced until the log-label defect is fixed. Owned by Stage 14; successor remediation plan or T2-defect-fix plan.
- **TestMultiJvmCoordinatorFailover HA-fencing takeover failure** (`:129` "coordinator-1 must take over" assertion fails): `disposition: blocked`, `required_lane: multi-jvm` (EVID-S14-014). Cross-JVM HA-fencing takeover capability gap; T2 infrastructure is qualified so this is an honest `blocked`. Owned by Stages 13/14.

## Kafka/Pulsar Gated-Backend Disposition (cross-ref Stage 5 T3/T4 `@@LANE` blocks)

- **Kafka wire-codec** (`KafkaStringWireCodec:42`): `disposition: blocked`, `required_lane: in-process`, `environment_class: none` (EVID-S14-009). Lane T3 is `blocked` — no Kafka broker provisioned; gate verified effective. The transport path is implemented on the nop-stream side, but per gated-evidence Rule S5-1 a skipped gated test is NOT evidence. rerun_condition: provision a Kafka broker and rerun with the gate flag.
- **Pulsar wire-codec** (`PulsarStringWireCodec:39`): `disposition: blocked`, `required_lane: in-process`, `environment_class: none` (EVID-S14-010). Lane T4 is `blocked` — no Pulsar service provisioned; gate verified effective. Per gated-evidence Rule S5-1 a skipped gated test is NOT evidence. rerun_condition: provision a Pulsar service and rerun with the gate flag.

## Non-Goals honored (not silently dropped)

- Connector source/sink guarantees (exactly-once / cursor / offset / external transaction) = Stages 15/16. This audit verifies the generic data-plane transport, not connector business semantics.
- Checkpoint barrier alignment / state backend / window / CEP semantics = Stages 9/10/11/12. This audit only cross-references M8-2-P0-1 / M8-2-P1-6 live-revalidation results.
- Control-plane RPC / leader election / fencing epoch encoding = Stage 13 (done). This audit absorbs its cross-JVM residual revalidation but does not re-audit the control plane itself.
- Fixing confirmed live defects discovered by this audit = assigned to a successor remediation plan per roadmap rule (none new-discovered here beyond the two known T2 defects EVID-S14-013/014, which were already recorded in the Stage 5 T2 lane note and Stage 13, and are dispositioned `blocked`).

## Coverage Gaps (assigned to successor remediation — NOT confirmed new live defects)

- **Cross-JVM exactly-once recovery** (`TestMultiJvmExactlyOnceRecovery` log-label mismatch EVID-S14-013) and **cross-JVM HA-fencing takeover** (`TestMultiJvmCoordinatorFailover` EVID-S14-014) are `blocked` on the T2 lane deeper-test defects, owned by Stages 13/14 successor work — honestly `blocked`, not silently upgraded.
- **Cross-JVM distributed mutex (M8-2-P0-1)** and **cross-JVM zombie fencing (M8-2-P1-6)** are `residual-risk` requiring the multi-jvm lane after the T2 defects are fixed (EVID-S14-016/017/020/021).
- **Kafka/Pulsar real-backend evidence** requires provisioning a broker (T3/T4 `rerun_condition`) then re-running (EVID-S14-009/010).
