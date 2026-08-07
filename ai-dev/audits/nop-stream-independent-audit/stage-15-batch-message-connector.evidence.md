# Stage 15 — Batch / Message Connector Capability Evidence

> Status: produced by Stage 15 audit (plan `nop-stream-independent-audit/2026-08-08-0610-1-batch-message-connector-capability-audit.md`)
> Domain: manifest a (public types) + e (4 connector modules main java: connector, connector-batch, connector-jdbc, connector-debezium) + g (test lane); the message backend abstraction `IMessageService` lives in `nop-api-core` (referenced as the contract, not a connector-module anchor), and the real Kafka/Pulsar backend wire-codecs live in `nop-stream-runtime` (transport proven by Stage 14, cross-referenced not re-audited).
> Lane policy: connector source/sink capabilities are credited only at `in-process` lane or stronger (a real operator/SourceContext driving `run()`/`consume()` over an in-process `LocalMessageService`). `unit` is component-only. The real Kafka/Pulsar message backend requires the T3/T4 lanes which are `blocked` in `environment-qualification.md` (no broker provisioned) — per gated-evidence Rule S5-1 a skipped gated test is NEVER cited as evidence, so the real-backend capability is honestly `blocked`, never silently upgraded to `e2e-proved`. The in-process `LocalMessageService` (`ioc:default` bean `streamMessageService`) is the T1 in-process backend and must not be passed off as Kafka/Pulsar.
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence` (parses `@@EVIDENCE` rows from `*.evidence.md` direct children of this dir).
> All source/test anchors in this file were verified against the live repo on 2026-08-08 (line anchors cross-checked by reading each file; test method names confirmed by direct file read and by a green `./mvnw test` run on `nop-stream-connector` + `nop-stream-connector-batch`: 35 + 35 tests, 0 failures, 0 skipped). The partition-aware path (`MessageSourceFunction.getEffectiveTopic()` / partitioned ctor) was confirmed to have ZERO test references across the whole repo — it is honestly marked `unverified`, never `e2e-proved`.

## Support / Reject Combination Matrix (frozen by this audit — batch / message connector)

This matrix adjudicates every supported and rejected batch/message connector source/sink capability. Each row cites the live source anchor that implements or rejects it and the evidence row that freezes the adjudication. The matrix changes neither the 11 evidence-row fields nor the 7-value disposition vocabulary (frozen by Stage 4 `evidence-schema.md`).

### Batch Connector Capability Matrix (entry-to-effect, in-process lane)

| # | Capability | Verdict | Lane | Live anchor (implementing) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| B1 | Bounded batch source (loop `loader.load` → break on empty → `ctx.collect`) | **SUPPORTED** | in-process | `BatchLoaderSourceFunction.run():61-85` (loop `:67-78`, empty break `:69-71`, per-item `ctx.collect` `:76`); loader `AutoCloseable` close in finally `:80-83` | EVID-S15-001 |
| B2 | Replayable source (offset cursor `getCurrentOffset`/`seek`) | **SUPPORTED** | in-process | `BatchLoaderSourceFunction.getCurrentOffset():98-100` / `seek(long):103-105` (currentOffset incremented per collect `:77`) | EVID-S15-002 |
| B3 | Buffered batch sink (buffer→`batchSize`→`flush`→`consumer.consume`) | **SUPPORTED** | in-process | `BatchConsumerSinkFunction.consume():77-89` (buffer `:85-88`); `flush():91-105` (delegate `consumer.consume` `:99`, clear `:100`) | EVID-S15-003 |
| B4 | Batch sink null-boundary reject + robust close (P1-15) | **PARTIALLY SUPPORTED — residual-risk** | in-process | null reject `consume():82-84` (throw `ERR_STREAM_NULL_ARG`); robust close `close():116-145` (flush+capture+suppressed+rethrow `ERR_STREAM_CHAINING_OUTPUT_FLUSH_FAILED`); thread-safety = documented single-thread contract `:39-45` (NOT concurrency-tested) | EVID-S15-004 |
| B5 | StreamConnectors optional-deps isolation (AR-2) | **PARTIALLY SUPPORTED — residual-risk** | unit | `StreamConnectors.java:20-23` lives in connector-**batch** (references `nop-batch-core`); base `nop-stream-connector/pom.xml` declares only `nop-stream-core` (compile) + `nop-message-core`/junit (test) — no `nop-batch-core` | EVID-S15-005 |
| B6 | Batch sink consistency label `IDEMPOTENT` (no EXACTLY_ONCE over-claim) | **SUPPORTED** | in-process | `BatchConsumerSinkFunction.getSinkConsistency():148-150` returns `IDEMPOTENT`; `StreamRequirementValidator` rejects `IDEMPOTENT` for `STRICT_EXACTLY_ONCE` | EVID-S15-006 |

### Message Connector Capability Matrix

| # | Capability | Verdict | Lane | Live anchor (implementing) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| M1 | Partition-aware subscription (`{topic}-{subtaskIndex}`) | **CLAIMED — UNVERIFIED (zero test coverage)** | none | `MessageSourceFunction.getEffectiveTopic():114-119`; partition ctor validation `:95-102`; subscribe `:129`; **no test references this path** (TestMessageSourceFunctionThreadSafety + TestMessageAdapters both use the non-partitioned ctor) | EVID-S15-007 |
| M2 | Collect/type-mismatch capture-and-rethrow (P1-9 FIXED) | **SUPPORTED** | in-process | `MessageSourceFunction` pendingError capture `:151-164` (collect) + `:136-141` (type-mismatch); `run()` rethrow `:176-182` | EVID-S15-008 |
| M3 | Synchronous sink send (per-record `messageService.send`) | **SUPPORTED** | in-process | `MessageSinkFunction.consume():43-45` → `messageService.send(topic, value)` (sync, no buffer/2PC) | EVID-S15-009 |
| M4 | Message source/sink consistency label `AT_LEAST_ONCE` (no over-claim) | **SUPPORTED** | in-process | `MessageSourceFunction.getSourceConsistency():197-199` + `MessageSinkFunction.getSinkConsistency():48-50` both return `AT_LEAST_ONCE` | EVID-S15-010 |
| MB | Real Kafka/Pulsar message backend integration | **BLOCKED — unqualified lane** | none (T3/T4 blocked) | connector binds `IMessageService`; real backend = `nop-stream-runtime` wire-codec SPI (Stage 14 EVID-S14-009/010), gated tests skipped — Rule S5-1 | EVID-S15-011 |

### Resource-Lifecycle / Drainable / Thread-Safety / Historical-Finding Matrix

| # | Capability / Finding | Verdict | Lane | Live anchor | Evidence row |
| --- | --- | --- | --- | --- | --- |
| R1 | Connector resource lifecycle (cancel/close releases subscription/loader/consumer) | **SUPPORTED** | in-process | `MessageSourceFunction.cancel():186-194` (subscription.cancel); `BatchLoaderSourceFunction:80-83` loader close; `BatchConsumerSinkFunction.close():129-139` consumer close | EVID-S15-013 |
| R2 | Drainable source support (`truncateForDrain` stops consuming) | **SUPPORTED** | in-process | `DrainableSource.java:15-16` (`@Internal` SPI); `TestDrainableSourceSupport` exercises the drain contract | EVID-S15-014 |
| R3 | Message source thread-safety (`synchronized(ctx)` around collect) | **SUPPORTED** | in-process | `MessageSourceFunction.onMessage():148` (`synchronized(ctx)`) | EVID-S15-015 |
| H1 | M8-2-P2-12 (LocalSourceCoordinator bare exception, corpus tag `connector`) | **OUT OF SCOPE — non-goal** | none | anchor `LocalSourceCoordinator.java:127,150,267,274` lives in **`nop-stream-core`** (`io/nop/stream/core/source/coordinator/`), NOT in manifest domain e (4 connector modules) → cross-ref deferred plan `2026-08-04-2300-3-contract-drift-config-test-integrity.md` | EVID-S15-012 |

Adjudication rules applied (consistent with Stage 4 schema + Stage 5 supplement):
- A batch/message connector source/sink capability gets an entry-to-effect evidence row with `disposition: e2e-proved` when an in-process test traces the path from `run()`/`consume()` through to `ctx.collect()` / `consumer.consume()` / `messageService.send()` (wiring actually connected over a real `LocalMessageService`), or an honest weaker disposition when only a segment is exercised.
- A capability whose only implementation path has ZERO test references (partition-aware subscription) gets `disposition: unverified` with `positive_proof: manual-trace:...` and `environment_class: none` — it is NEVER silently upgraded to `e2e-proved` (Anti-Hollow Check (a)/(e)).
- A capability needing a real Kafka/Pulsar backend whose T3/T4 lane is `blocked` gets `disposition: blocked` naming the unqualified lane (T3/T4 `@@LANE`) + Rule S5-1 — a skipped gated test is NEVER cited as evidence (Anti-Hollow Check (c)).
- A historical connector finding that is only partially addressed (M7-2-P1-15 happy-path-only) gets `disposition: residual-risk` with the residual (concurrency boundary documented but not tested) named and a non-blocking rationale; it is never silently downgraded to a closed `e2e-proved`.
- A historical finding whose corpus domain tag is `connector` but whose code anchor lives outside the connector modules (M8-2-P2-12 in `nop-stream-core`) is adjudicated `non-goal` for THIS stage and cross-referenced to its deferred remediation plan (Anti-Hollow / Rule #24 — no silent skip of an in-scope-tagged finding).

---

## Evidence Rows

### Phase 1 — Batch Connector Source/Sink Capability Evidence (in-process)

@@EVIDENCE
inventory_id: EVID-S15-001
source_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:61-85
declared_guarantee: Bounded batch source — run() loops loader.load(batchSize, chunkContext) and breaks when the loader returns an empty/null list, emitting each record via ctx.collect(item), so the source terminates on exhaustion (bounded semantics); the loader (AutoCloseable) is closed in a finally block.
implementation_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:67-83
runtime_wiring: wired
positive_proof: TestBatchLoaderSourceFunction#testEmitAllRecords
rejection_proof: TestBatchLoaderSourceFunction#testEmptyLoaderCompletes
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S15-002
source_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:97-105
declared_guarantee: Replayable source — implements ReplayableSourceFunction exposing getCurrentOffset() (returns currentOffset, incremented per ctx.collect at :77) and seek(long) (sets currentOffset), enabling checkpoint snapshot/restore of the read position.
implementation_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:98-105
runtime_wiring: wired
positive_proof: TestBatchLoaderSourceFunction#testReplayableSourceFunctionGetCurrentOffset
rejection_proof: TestBatchLoaderSourceFunction#testSeekSetsOffset
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S15-003
source_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkFunction.java:77-105
declared_guarantee: Buffered batch sink — consume(value) appends to a List buffer and triggers flush() when buffer.size() >= batchSize; flush() delegates consumer.consume(new ArrayList(buffer), chunkContext) and clears the buffer, providing batched downstream delivery.
implementation_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkFunction.java:91-105
runtime_wiring: wired
positive_proof: TestBatchConsumerSinkFunction#testBufferAndFlush
rejection_proof: TestBatchConsumerSinkFunctionFailure#testFlushFailurePropagatesException
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S15-004
source_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkFunction.java:82-84
declared_guarantee: P1-15 partial-addressed — consume() rejects null at the boundary (throws StreamException ERR_STREAM_NULL_ARG at :82-84 instead of buffering it); close() robustly flushes remaining records and on flush failure captures the error, logs it, attempts consumer close, and rethrows as ERR_STREAM_CHAINING_OUTPUT_FLUSH_FAILED with suppressed close errors (:116-145), so data-loss-on-close is surfaced not silent. Residual: thread-safety is a documented single-thread contract (:39-45) that is NOT exercised by any concurrency test.
implementation_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkFunction.java:82-84,116-145
runtime_wiring: wired
positive_proof: TestBatchConsumerSinkFunction#testConsumeNullRejected
rejection_proof: TestBatchConsumerSinkFunctionCloseLogging#testCloseWithFlushFailureThrowsStreamException
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P1-15
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S15-005
source_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/StreamConnectors.java:20-23
declared_guarantee: AR-2 — StreamConnectors (which directly references nop-batch-core types IBatchLoaderProvider/IBatchConsumerProvider) deliberately lives in connector-batch, NOT the base nop-stream-connector module; the base connector pom.xml declares only nop-stream-core (compile) + nop-message-core/junit (test) with NO nop-batch-core dependency, so base connector classes (MessageSourceFunction/MessageSinkFunction) load without nop-batch-core on the classpath and do not throw NoClassDefFoundError at class-load time.
implementation_anchor: nop-stream/nop-stream-connector/pom.xml:34-48
runtime_wiring: wired
positive_proof: manual-trace:nop-stream/nop-stream-connector/pom.xml:34-48
rejection_proof: none
environment_class: unit
required_lane: in-process
finding_id: O7-2-AR-2
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S15-006
source_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkFunction.java:148-150
declared_guarantee: Consistency label adjudication — getSinkConsistency() returns IDEMPOTENT, consistent with batched-flush semantics where the consumer contract is expected to handle retry/idempotency; the label does NOT over-claim EXACTLY_ONCE/2PC, and StreamRequirementValidator.validateConnectorConsistency correctly REJECTS an IDEMPOTENT sink for STRICT_EXACTLY_ONCE.
implementation_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkFunction.java:148-150
runtime_wiring: wired
positive_proof: TestConnectorConsistencyCapability#testBatchConsumerSinkDeclaresIdempotent
rejection_proof: TestConnectorConsistencyCapability#validateConnectorConsistency_batchLoaderPlusIdempotentSink_failsForStrict
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 2 — Message Connector Source/Sink Capability & Backend Limitation Evidence

@@EVIDENCE
inventory_id: EVID-S15-007
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:114-119
declared_guarantee: Partition-aware subscription — when subtaskIndex >= 0, getEffectiveTopic() returns {topic}-{subtaskIndex} for deterministic per-subtask partition consumption without overlap; the ctor validates totalParallelism > 0 and subtaskIndex < totalParallelism (:95-102) and run() subscribes via messageService.subscribe(effectiveTopic, consumer) (:129). GAP: this path has ZERO test references across the whole repo (TestMessageSourceFunctionThreadSafety and TestMessageAdapters both use the non-partitioned ctor), so the partition-aware capability is asserted but not demonstrated.
implementation_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:114-119
runtime_wiring: partial
positive_proof: manual-trace:nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:114-119
rejection_proof: manual-trace:nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:95-102
environment_class: none
required_lane: in-process
finding_id: none
disposition: unverified
@@END

@@EVIDENCE
inventory_id: EVID-S15-008
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:148-182
declared_guarantee: P1-9 FIXED — a collect() exception (and a typeClass type-mismatch) inside onMessage is captured into pendingError (collect capture :151-164; type-mismatch capture :136-141) and rethrown by run() after the loop exits (:176-182), so the source surfaces as FAILED rather than completing as a false-success EOS (preventing the silent data loss of the prior swallow-and-return implementation).
implementation_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:151-164,176-182
runtime_wiring: wired
positive_proof: TestMessageSourceFunctionThreadSafety#testCollectFailureSurfacesFromRun
rejection_proof: TestMessageSourceFunctionThreadSafety#testTypeMismatchSurfacesFromRun
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P1-9
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S15-009
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSinkFunction.java:43-45
declared_guarantee: Synchronous sink send — consume(T) calls messageService.send(topic, value) synchronously per record (no buffer, no async, no 2PC), providing per-record at-least-once delivery to the bound IMessageService backend; null messageService/topic are rejected at construction.
implementation_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSinkFunction.java:43-45
runtime_wiring: wired
positive_proof: TestMessageAdapters#testMessageSinkSendsMessages
rejection_proof: TestMessageAdapters#testNullArgumentsRejected
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S15-010
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:197-199
declared_guarantee: Consistency label adjudication — getSourceConsistency() returns AT_LEAST_ONCE (MessageSourceFunction :197-199) and getSinkConsistency() returns AT_LEAST_ONCE (MessageSinkFunction :48-50); both are consistent with synchronous subscribe/send semantics over IMessageService and neither over-claims EXACTLY_ONCE/2PC.
implementation_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSinkFunction.java:48-50
runtime_wiring: wired
positive_proof: TestMessageAdapters#testMessageSinkSendsMessages
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S15-011
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:129
declared_guarantee: Message backend Kafka/Pulsar real integration — connectors bind the IMessageService abstraction; the real Kafka/Pulsar backend is provided by the nop-stream-runtime wire-codec SPI (KafkaStringWireCodec/PulsarStringWireCodec, transport proven by Stage 14 EVID-S14-009/010), whose gated E2E tests (TestDataPlaneKafkaBackendE2E / TestDataPlanePulsarBackendE2E) require T3/T4 brokers that are NOT provisioned in the audit environment. Per Rule S5-1 the skipped gated tests are NEVER cited as evidence; the in-process LocalMessageService (ioc:default bean streamMessageService) is the T1 backend and must not be passed off as Kafka/Pulsar.
implementation_anchor: none
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: blocked
@@END

### Phase 3 — Historical Finding Revalidation, Resource-Lifecycle & Thread-Safety Evidence

@@EVIDENCE
inventory_id: EVID-S15-012
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:127
declared_guarantee: M8-2-P2-12 scope judgment — the finding-corpus tags this finding domain 'connector' but its code anchor LocalSourceCoordinator.java lives in nop-stream-core (io/nop/stream/core/source/coordinator/), NOT in manifest domain e (the 4 connector modules connector/connector-batch/connector-jdbc/connector-debezium). Therefore it is OUT of Stage 15 connector-audit scope; the corpus 'connector' tag is a classification label, not a code-location marker. The finding is cross-referenced (deferred) to plan 2026-08-04-2300-3-contract-drift-config-test-integrity.md, which the corpus deferred field already names — this row records the in-scope复验 judgment and does not re-audit the runtime coordinator.
implementation_anchor: none
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: M8-2-P2-12
disposition: non-goal
@@END

@@EVIDENCE
inventory_id: EVID-S15-013
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:186-194
declared_guarantee: Connector resource lifecycle — MessageSourceFunction.cancel() sets running=false, counts down the shutdown latch, and cancels the subscription (:186-194), so the subscriber thread terminates and the IMessageService subscription is released; the batch-family parallels are BatchLoaderSourceFunction.java:80-83 (loader AutoCloseable close in finally) and BatchConsumerSinkFunction.java:129-139 (consumer AutoCloseable close in close()).
implementation_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:186-194
runtime_wiring: wired
positive_proof: TestConnectorResourceManagement#testMessageSourceFunctionVolatileSubscription
rejection_proof: TestConnectorResourceManagement#testMessageSourceFunctionCollectExceptionSetsFailedFlag
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S15-014
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/DrainableSource.java:15-16
declared_guarantee: Drainable source support — the @Internal DrainableSource SPI extends SourceFunction with truncateForDrain(), which a source implements to stop further record emission so the stream can be drained for savepoint/redeploy; TestDrainableSourceSupport exercises a DrainableSource whose run loop exits once truncateForDrain() flips a draining flag, proving the drain contract stops consuming.
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/DrainableSource.java:15-16
runtime_wiring: wired
positive_proof: TestDrainableSourceSupport#testDrainableSourceTruncateStopsConsuming
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S15-015
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:148
declared_guarantee: Message source thread-safety — onMessage serializes ctx.collect() inside synchronized(ctx) (:148) so concurrent message deliveries from the subscriber thread do not interleave collect calls against the run() thread, and the captured pendingError coordinates failure surfacing across threads.
implementation_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:148
runtime_wiring: wired
positive_proof: TestMessageSourceFunctionThreadSafety#testConcurrentCollectCallsAreSynchronized
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END
