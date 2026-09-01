# Nop Stream Fraud Detection Example

This module demonstrates real-time fraud detection on Nop Stream through TWO entry points:

1. **Composite scenarios (product front door)** — S1/S2 XDSL-declared pipelines executed by the
   streaming engine with checkpointing, exactly-once sinks, restore and offline reshard
   (roadmap item 13, `ai-dev/design/nop-stream/composite-scenario-design.md`):
   - **S1** `fraud-s1-cdc.stream.xml`: replayable CDC source (ChangeEvent shell on the production
     `DebeziumCdcSourceFunction` offset path) → event-time watermarks → decode → keyBy →
     keyed-state per-user history enrichment (`UserHistoryEnricher`, the production-form revival of
     `UserTransactionHistory` and the de-stubbed unusual-amount average) → 4 CEP fraud patterns
     (`<patterns>` declarations) → per-pattern tumbling-window aggregation → 2PC JDBC sink
     (per-chain epoch ledger tables, H2).
   - **S2** `fraud-s2-file.stream.xml` (+ `fraud-s2-file-delta.stream.xml`): bounded directory
     file source with per-file byte cursors in checkpoints → parse → watermarks → keyBy → window
     aggregation → exactly-once `FileTwoPhaseCommitSink` (epoch files + manifest). The delta file
     adds a blacklist filter via `x:extends` (topology-level delta: new operator + edge rewiring).
2. **Engine-direct teaching demo** — `FraudDetectionDemo.main` drives the CEP `NFA`/`SharedBuffer`
   engine API directly with mock data (kept as the engine-internals walkthrough; the product
   narrative is the scenarios above).

## Features

- **4 Fraud Detection Patterns (declared in XDSL `<patterns>`, mirrored by the pattern classes):**
  1. RapidTransactionPattern - Detects 2+ large transactions (>$1000) within 30 seconds
  2. UnusualAmountPattern - Detects transactions >10x user's REAL keyed-state historical average
     (>= 3 prior transactions; the standalone demo class still uses a fixed $100 average)
  3. GeographicAnomalyPattern - Detects transactions from different cities within 1 hour
     (expressed as a pure per-event predicate over the enriched `prevCity` field)
  4. AccountTakeoverPattern - Detects login → password change → withdrawal within 15 minutes

- **End-to-end semantics, all assertion-backed by tests:** exact expected outputs, late-event
  drop, cross-user zero false positives, exactly-once sink outputs (JDBC PK + ledger guard /
  epoch-file + manifest contract), checkpoint → stop → restore → replay recovery on both the
  memory and RocksDB backends, and offline maxParallelism reshard (128 → 256) of a real
  scenario savepoint.

## Project Structure
```
nop-stream-fraud-example/
├── README.md
├── pom.xml
├── src/main/java/io/nop/stream/fraud/
│   ├── FraudDetectionDemo.java          # Engine-direct teaching demo (main)
│   ├── model/
│   │   ├── FraudAlert.java             # Alert model (@DataBean, checkpoint-safe)
│   │   └── TransactionEvent.java       # Event model (@DataBean, checkpoint-safe)
│   ├── pattern/                         # Pattern classes (engine-direct demo + teaching)
│   ├── scenario/                        # Composite-scenario assets (S1/S2)
│   │   ├── EnrichedTransaction.java     # keyed-state-enriched event (avg/prevCity)
│   │   ├── UserHistoryEnricher.java     # KeyedProcessFunction keyed ValueState enrichment
│   │   ├── FraudAlertPatternFunction.java # CEP select bean (per-pattern)
│   │   ├── AlertCountAggregate.java / AlertSummaryRow.java  # S1 window aggregation + row
│   │   ├── TransactionWindowAggregate.java / TxSummaryRow.java  # S2 aggregation + row
│   │   ├── CdcChangeDecoder.java        # ChangeEvent -> TransactionEvent decode bean
│   │   ├── TransactionLineParser.java   # S2 line parse bean
│   │   ├── ReplayableCdcSourceFunction.java  # S1 deterministic CDC source (offset path)
│   │   └── DirectoryFileSourceFunction.java  # S2 bounded file source (byte cursors)
│   ├── state/
│   │   ├── DemoKeyedStateStore.java    # Demo-only state store (see class Javadoc)
│   │   └── UserTransactionHistory.java # keyed-state avg holder (used by the enricher)
│   └── util/
│       └── MockTransactionGenerator.java
├── src/main/resources/_vfs/nop/stream/demo/
│   ├── fraud-s1-cdc.stream.xml          # S1 pipeline (XDSL: patterns/checkpoint/4 chains)
│   ├── fraud-s2-file.stream.xml         # S2 base pipeline (XDSL)
│   └── fraud-s2-file-delta.stream.xml   # S2 delta (x:extends blacklist filter)
└── src/test/java/io/nop/stream/fraud/
    ├── pattern/                          # Pattern unit tests (engine-direct demo)
    └── scenario/                         # Scenario E2E + focused tests (S1/S2)
```

## Quick Start

### Build
```bash
cd nop-stream
mvn clean install -pl nop-stream-fraud-example -am -DskipTests
```

### Run the engine-direct demo
```bash
cd nop-stream
mvn exec:java -pl nop-stream-fraud-example -Dexec.mainClass="io.nop.stream.fraud.FraudDetectionDemo"
```

The demo prints one section per pattern with the generated events and any `FraudAlert`s. It exits non-zero if the demo itself fails.

### Test (scenarios + patterns)
```bash
cd nop-stream
mvn test -pl nop-stream-fraud-example
```

The scenario tests are self-contained: they use in-memory H2 (MODE=MySQL) for the S1 sink, temp
directories for the S2 input/output and checkpoint storage, and deterministic fixtures — no
external systems.

## Scenario Notes

- **S1 sink**: `JdbcTwoPhaseCommitSink` writes `fraud_alerts_summary` (PK: window bounds + user +
  pattern). Each CEP chain uses its own epoch ledger table (the ledger PK `(epoch_id, subtask_id)`
  has no vertex dimension, so chains must not share one ledger table).
- **S1 CDC driving form**: the replayable CDC source only replaces the message ENGINE (through the
  documented `createMessageSource` factory seam); offsets, snapshot/restore and checkpointing run
  on the production `DebeziumCdcSourceFunction` code path. Swapping the bean for an
  engine-backed source is the "change one bean" upgrade to real Debezium.
- **S2 rescale routing**: restore-time parallelism rescale is verified in the distributed plan
  (multi-JVM); the engine fail-fasts 2PC sinks at parallelism > 1 (see
  `checkpoint-design.md` §6.4.1). Local coverage includes keyed-state continuation across
  restore, backend switching and offline maxParallelism reshard.

## Configuration
- **Amount Thresholds**: See pattern constants (e.g., `RAPID_TRANSACTION_AMOUNT_THRESHOLD = 1000`)
- **Time Windows**: Configurable per pattern (XDSL `within`, tumbling window size in the
  assigner/aggregator beans)
- **State**: scenario pipelines use the engine's state backends (memory or RocksDB via checkpoint
  config); the engine-direct demo uses `DemoKeyedStateStore`, a demo-only in-memory store that is
  NOT the engine's MemoryStateBackend (see its class Javadoc before copying)

## Architecture
- **CEP Engine**: scenarios declare patterns in XDSL (`<patterns>` + `<cep>` transforms); the
  engine-direct demo uses the CEP implementation directly (`NFA`/`SharedBuffer`)
- **State Management**: keyed ValueState enrichment + keyed window state, checkpointed through
  the engine's JSON snapshot path (CEP NFA/shared-buffer state travels as Java-serialized
  payloads embedded in the JSON snapshot)
- **Exactly-once sinks**: 2PC JDBC (transactional rows + epoch ledger, idempotent re-commit
  guard) and 2PC file sink (temp + atomic rename + manifest)
- **No External Dependencies**: H2 in-memory, local temp directories

## Known Limitations
- The engine-direct demo (`FraudDetectionDemo`) still uses a fixed $100 average for the
  unusual-amount pattern; the SCENARIO pipeline uses the real per-user keyed-state average
  (`UserHistoryEnricher`).
- A bounded run's final in-flight window rows are committed on the NEXT (recovery) run: 2PC sinks
  commit only on completed checkpoints, and the best-effort final checkpoint of a terminating
  bounded job races natural completion (engine semantics; the recovery tests cover the
  completion).
- 2PC sinks at parallelism > 1 are engine-rejected (deliberate deferral, see
  `checkpoint-design.md` §6.4.1); the distributed plan owns restore-time rescale verification.

## License
Apache License 2.0 (see the repository root license file)
