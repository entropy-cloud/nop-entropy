# nop-stream Independent Audit — Environment Qualification (Stage 5)

> Status: frozen
> Frozen at: HEAD 2026-08-08
> Owner: nop-stream-independent-audit mission (Stage 5)
> Validator: `ai-dev/tools/check-nop-stream-audit-manifest.mjs qualification`
> Source plan: `ai-dev/plans/nop-stream-independent-audit/2026-08-07-2346-1-environment-qualification-gated-evidence.md`

This record freezes the **lane qualification registry** for the nop-stream independent audit: which test environments can produce **reproducible audit evidence**, and how an unavailable required external backend is honestly reported as `blocked` rather than silently skipped. Each lane target (T1–T6) maps to the **frozen** strength vocabulary of `evidence-schema.md` (`unit | in-process | multi-jvm`); this plan introduces **no new lane vocabulary**. Later domain audits (Stages 6–16) may only take evidence inside a lane that is `qualified` here; a `required_lane` whose lane is `blocked` blocks the Stage 23 readiness verdict.

This record produces **no product-correctness conclusion** — only "this lane's tests can actually run, produce the expected positive result, and report `blocked` honestly when a backend is unavailable".

## Lane Summary (frozen T1–T6)

| Lane | `frozen_strength` | `status` | One-line basis |
| --- | --- | --- | --- |
| T1 unit + embedded in-process | `in-process` | `qualified` | `./mvnw test` standard suite PASS (TestJdbcTwoPhaseCommitSinkSkeleton 19/19 embedded H2; TestDebeziumCdcCheckpoint 7/7 mocked). H2 embedded & Debezium mocked — honestly NOT external integration. |
| T2 multi-JVM | `multi-jvm` | `qualified` | TestMiniStreamClusterProcessSpawn 3/3 PASS (real cross-JVM spawn + registration + coordinator boot). Deeper recovery/HA tests have defects → capability findings for Stages 13/14. |
| T3 Kafka data-plane | `in-process` | `blocked` | No Kafka broker provisioned (localhost:9092 closed); gate verified effective (Skipped without flag). |
| T4 Pulsar data-plane | `in-process` | `blocked` | No Pulsar service provisioned (localhost:6650 closed); gate verified effective. |
| T5 PostgreSQL | `in-process` | `blocked` | No `@EnabledIfSystemProperty`-gated PostgreSQL test in repo (JDBC sink tests use embedded H2 → T1). |
| T6 Debezium real-CDC | `in-process` | `blocked` | Only mocked offset-config test exists (→ T1); no real-source Debezium engine integration test. |

**Lane-mapping consequence (Rule S5-3):** because T3–T6 are `blocked`, any domain audit row needing a `qualified` lane at `in-process` strength may ONLY draw on T1 (the sole `qualified` `in-process` lane). Rows needing `multi-jvm` strength may draw on T2. No row may claim an `environment_class` stronger than a `qualified` lane's `frozen_strength`. A `required_lane` whose only candidate lanes are `blocked` forces the row's `disposition` to `blocked` and blocks the Stage 23 `ready` verdict.

## Frozen-Schema Integrity Check (Phase 4)

This Stage-5 plan is an **additive** overlay on the Stage-4 frozen `evidence-schema.md`. The integrity check confirms NO drift to the frozen surface:

- **11 evidence-row fields**: UNCHANGED. The Field Specification table still declares exactly `inventory_id, source_anchor, declared_guarantee, implementation_anchor, runtime_wiring, positive_proof, rejection_proof, environment_class, required_lane, finding_id, disposition` (11). Verified `grep -E '^\| [0-9]+ \|' evidence-schema.md | wc -l` = 11.
- **7-value Disposition Vocabulary**: UNCHANGED. Still exactly `e2e-proved | component-only | unverified | fail-fast | non-goal | residual-risk | blocked` (7). Verified count = 7.
- **Lane strength vocabulary**: UNCHANGED. Still `unit | in-process | multi-jvm` (for `frozen_strength`/`required_lane`) plus `none` (for `environment_class`).
- **What this plan ADDED (not a field/vocabulary change):** a "Stage 5 Supplement" rules section with three rules — S5-1 (gated-evidence), S5-2 (required-lane/blocked-gate), S5-3 (lane-mapping). These bind how later audits consume gated-test results and how `blocked` propagates; they alter neither the 11 fields nor the 7-class vocabulary.

Conclusion: the frozen evidence-schema surface is byte-for-byte intact in its field/vocabulary definitions; only an additive rules supplement was appended.

## Qualification Record Format (`@@LANE` block)

Each `@@LANE ... @@END` block is a machine-parsable record consumed by the validator's `qualification` subcommand. Flat `key: value` lines (same convention as `@@ENTRY` / `@@EVIDENCE`).

Required fields: `lane_id`, `frozen_strength`, `invoke_command`, `preconditions`, `credential_isolation`, `cleanup`, `timeout`, `artifact_retention`, `owner`, `status`.

- `frozen_strength` MUST be one of `unit | in-process | multi-jvm`.
- `status` MUST be one of `qualified | blocked`.
- When `status: qualified`, `expected_positive_result` is REQUIRED (real surefire / run reference).
- When `status: blocked`, `blocked_reason` AND `rerun_condition` are REQUIRED.
- `note` is an OPTIONAL allowed field (used for honest classification such as embedded/mocked nature).
- When `status: blocked` because no gated test exists in the repo, `invoke_command` MAY be the placeholder `none (no gated test in repo)` — this is a legal non-empty value, not a missing field.

The validator rejects: missing required fields, `frozen_strength` outside the vocabulary, `status` outside `{qualified, blocked}`, a `blocked` row missing `blocked_reason`/`rerun_condition`, a `qualified` row missing `expected_positive_result`, and any unknown field.

## Lane Registry (T1–T6)

### T1 — unit + embedded in-process (always available)

@@LANE
lane_id: T1-unit-embedded-in-process
frozen_strength: in-process
invoke_command: ./mvnw test -pl nop-stream -am
preconditions: JDK 21 + local reactor build (./mvnw install -pl nop-stream -am -DskipTests to populate classpath); NO external services (no broker, no external DB)
credential_isolation: none (single JVM, embedded H2 in-memory DB)
cleanup: embedded H2 in-memory DB auto-evicted on JVM exit; Mockito mocks GC'd with the JVM
timeout: standard surefire per-JVM timeout (no explicit @EnabledIfSystemProperty gate — runs with the default suite)
artifact_retention: surefire-reports/*.txt + TEST-*.xml per module
owner: nop-stream-independent-audit
status: qualified
expected_positive_result: surefire TestJdbcTwoPhaseCommitSinkSkeleton PASS (Tests run: 19, Failures: 0, Errors: 0, Skipped: 0 — embedded H2 jdbc:h2:mem ...;MODE=MySQL via HikariDataSource, no gate) + TestDebeziumCdcCheckpoint PASS (Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 — mocked WorkerConfig offset-config, no gate). Both run as part of the standard suite (no -D flag required).
note: T1 is the always-available lane. H2 is embedded in-process (jdbc:h2:mem), NOT an externally provisioned DB — it is honestly classified as in-process, NOT冒充 an external PostgreSQL/MySQL integration. The Debezium test is a mocked offset-config unit test (Mockito mock WorkerConfig, MockCdcMessageSource), NOT a real Debezium CDC engine against a real source DB — mocked coverage归 T1, the real-CDC lane is adjudicated separately as T6 (blocked).
@@END

### T2 — multi-JVM (gated, process spawn)

@@LANE
lane_id: T2-multi-jvm
frozen_strength: multi-jvm
invoke_command: ./mvnw test -pl nop-stream/nop-stream-runtime -am -Dnop.stream.test.multi-jvm.enabled=true -Dtest=TestMiniStreamClusterProcessSpawn -Dsurefire.failIfNoSpecifiedTests=false
preconditions: reactor built first (./mvnw install -pl nop-stream -am -DskipTests) so java.class.path is populated; MiniStreamCluster.java:137-138 resolves java.class.path + java.home/bin/java to spawn child JVMs; process spawn allowed (verified: child JVMs spawn, register via shared JDBC registry jdbc:h2:file ...;AUTO_SERVER=TRUE, coordinator boots in a separate JVM); JDK 21
credential_isolation: none (in-process cluster on localhost; shared file-based H2 with AUTO_SERVER for cross-JVM sharing)
cleanup: subprocess termination via ProcessHandle.destroy() (SIGTERM) + temp port/file reclaim under _tmp/mini-stream-cluster/<runId>/ (deleted on shutdown unless -Dnop.stream.test.multi-jvm.preserve-artifacts=true)
timeout: 90s per recovery test (RECOVERY_TIMEOUT_MS), 60s health timeout; spawn test ~2.4s
artifact_retention: surefire-reports/*.txt + TEST-*.xml; per-process logs at _tmp/mini-stream-cluster/<runId>/logs/{tm-0,tm-1,coordinator-0}.log when preserve-artifacts=true
owner: nop-stream-independent-audit
status: qualified
expected_positive_result: surefire TestMiniStreamClusterProcessSpawn PASS (Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.427s — gate nop.stream.test.multi-jvm.enabled=true, offline mode). Proves the multi-JVM lane infrastructure: real child JVMs spawn via ProcessBuilder, TaskManagers register in the shared cluster registry, coordinator-0.log is created in the separate coordinator JVM (cluster.logFileFor("coordinator-0") at TestMiniStreamClusterProcessSpawn.java:70). Gate property matches source TestMultiJvmExactlyOnceRecovery.java:67 (nop.stream.test.multi-jvm.enabled).
note: Lane INFRASTRUCTURE is qualified (cross-JVM process spawn + registration + coordinator boot). Two deeper tests have defects in their capability-level assertions, recorded as findings for downstream capability audits (NOT lane-qualification blockers, since the lane can run and produce positive spawn/registration evidence): (1) TestMultiJvmExactlyOnceRecovery ERRORs at :111 — reads logFileFor("coordinator") but MiniStreamCluster.spawnJobCoordinator(:404) writes label "coordinator-0" → logs/coordinator-0.log, so Files.size throws NoSuchFileException on the bare "coordinator" path (log-label mismatch defect); (2) TestMultiJvmCoordinatorFailover 1/2 — testBrainSplitFencingBoundary(:129) fails "coordinator-1 must take over" (HA failover takeover, a Stage 13 control-plane/HA capability finding). The exactly-once-recovery and HA-fencing capability claims must therefore be evidenced/owned by Stages 13/14, not asserted from this lane qualification alone.
@@END

### T3 — Kafka data-plane (gated, external broker)

@@LANE
lane_id: T3-kafka-dataplane
frozen_strength: in-process
invoke_command: ./mvnw test -pl nop-stream/nop-stream-runtime -am -Dnop.stream.test.kafka.enabled=true -Dnop.stream.test.kafka.brokers=localhost:9092 -Dtest=TestDataPlaneKafkaBackendE2E -Dsurefire.failIfNoSpecifiedTests=false
preconditions: a running Kafka broker reachable at the brokers connection string (default localhost:9092); nop-stream does NOT embed a broker (no testcontainers/embedded-kafka dependency); gate nop.stream.test.kafka.enabled=true REQUIRED (default skipped). Gate verified effective: without the flag the test reports Skipped (1 skipped, BUILD SUCCESS).
credential_isolation: dedicated test Kafka client (no shared production credentials); topic namespaced per-run (topic + nanoTime suffix) to avoid cross-run data leakage
cleanup: backend.destroy() in @AfterEach closes Kafka producer/consumer; per-run topic left on broker (Kafka retains data; mitigated by unique topic suffix)
timeout: 30s per consumer.read (record/watermark/barrier) per TestDataPlaneKafkaBackendE2E.java:98-104
artifact_retention: surefire-reports/*.txt + TEST-*.xml; Kafka producer/consumer logs in surefire stdout
owner: nop-stream-independent-audit
status: blocked
blocked_reason: no Kafka broker provisioned in the audit environment (localhost:9092 connection closed). The gate is effective (test Skipped without -Dnop.stream.test.kafka.enabled=true), so this is an honest blocked — NOT a silent skip — and the gated-evidence rule S5-1 forbids citing a skipped test as evidence.
rerun_condition: provision a Kafka broker (e.g. docker run -p 9092:9092 confluentinc/cp-kafka, or a CI-provided broker) and rerun with -Dnop.stream.test.kafka.enabled=true -Dnop.stream.test.kafka.brokers=<host:9092>; expect recordBarrierWatermarkTraverseKafkaTopic PASS.
@@END

### T4 — Pulsar data-plane (gated, external service)

@@LANE
lane_id: T4-pulsar-dataplane
frozen_strength: in-process
invoke_command: ./mvnw test -pl nop-stream/nop-stream-runtime -am -Dnop.stream.test.pulsar.enabled=true -Dnop.stream.test.pulsar.serviceUrl=pulsar://localhost:6650 -Dtest=TestDataPlanePulsarBackendE2E -Dsurefire.failIfNoSpecifiedTests=false
preconditions: a running Pulsar service reachable at the serviceUrl connection string (default pulsar://localhost:6650); nop-stream does NOT embed Pulsar; gate nop.stream.test.pulsar.enabled=true REQUIRED (default skipped). Gate uses the same @EnabledIfSystemProperty mechanism as T3 (verified at TestDataPlanePulsarBackendE2E.java:49-50).
credential_isolation: dedicated test Pulsar client; topic namespaced per-run (topic + nanoTime suffix)
cleanup: backend.destroy() in @AfterEach closes Pulsar producer/consumer; per-run topic left on broker (Pulsar may retain data; mitigated by unique topic suffix)
timeout: 15s per consumer.read per TestDataPlanePulsarBackendE2E.java:101-109
artifact_retention: surefire-reports/*.txt + TEST-*.xml; Pulsar producer/consumer logs in surefire stdout
owner: nop-stream-independent-audit
status: blocked
blocked_reason: no Pulsar service provisioned in the audit environment (localhost:6650 connection closed). The gate is effective (test Skipped without -Dnop.stream.test.pulsar.enabled=true, same mechanism as T3 verified Skipped), so this is an honest blocked — NOT a silent skip — and the gated-evidence rule S5-1 forbids citing a skipped test as evidence.
rerun_condition: provision a Pulsar service (e.g. docker run -p 6650:6650 apachepulsar/pulsar, or a CI-provided service) and rerun with -Dnop.stream.test.pulsar.enabled=true -Dnop.stream.test.pulsar.serviceUrl=pulsar://<host:6650>; expect recordBarrierWatermarkTraversePulsarTopic PASS.
@@END

### T5 — PostgreSQL (roadmap-listed, no gated test in repo)

@@LANE
lane_id: T5-postgresql
frozen_strength: in-process
invoke_command: none (no gated test in repo)
preconditions: would require a gated PostgreSQL JDBC sink/checkpoint test (e.g. @EnabledIfSystemProperty(nop.stream.test.postgres.enabled=true) + a real PostgreSQL instance); NONE EXISTS in the repo. All JDBC sink tests use embedded H2 (jdbc:h2:mem, classified under T1), not PostgreSQL.
credential_isolation: n/a (no test exists)
cleanup: n/a (no test exists)
timeout: n/a (no test exists)
artifact_retention: n/a (no test exists)
owner: nop-stream-independent-audit
status: blocked
blocked_reason: no @EnabledIfSystemProperty-gated PostgreSQL test exists in the repo. The JDBC two-phase-commit sink tests (TestJdbcTwoPhaseCommitSinkSkeleton) run against embedded H2 in MySQL-compat mode, NOT a real PostgreSQL instance — they are honestly classified under T1, not冒充 as PostgreSQL integration qualification.
rerun_condition: add a gated PostgreSQL sink/checkpoint test (e.g. @EnabledIfSystemProperty(nop.stream.test.postgres.enabled=true) connecting to a real PostgreSQL instance via HikariDataSource), provision a PostgreSQL instance, and rerun.
@@END

### T6 — Debezium real-CDC (roadmap-listed, only mocked test exists)

@@LANE
lane_id: T6-debezium-real-cdc
frozen_strength: in-process
invoke_command: none (no gated test in repo)
preconditions: would require a gated real-source Debezium integration test (e.g. @EnabledIfSystemProperty + a real source DB + the Debezium engine emitting from binlog); NONE EXISTS in the repo. The only Debezium test (TestDebeziumCdcCheckpoint) is a mocked offset-config unit test (Mockito mock WorkerConfig at :215, MockCdcMessageSource), NOT a real Debezium CDC engine against a real source DB — it is honestly classified under T1, not冒充 as real-CDC qualification.
credential_isolation: n/a (no real-source test exists)
cleanup: n/a (no real-source test exists)
timeout: n/a (no real-source test exists)
artifact_retention: n/a (no real-source test exists)
owner: nop-stream-independent-audit
status: blocked
blocked_reason: only a mocked offset-config test exists (TestDebeziumCdcCheckpoint, Mockito mock WorkerConfig + MockCdcMessageSource); no real-source Debezium engine integration test exercises the engine against a real source DB binlog. The mocked test is honestly classified under T1, not冒充 as real-CDC lane qualification.
rerun_condition: add a real-source Debezium integration test (e.g. @EnabledIfSystemProperty + a real MySQL/PostgreSQL source with binlog + the Debezium engine), provision the source DB, and rerun.
@@END



