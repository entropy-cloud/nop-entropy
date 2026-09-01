# Nop Stream Fraud Detection Example

This example demonstrates real-time fraud detection using Nop Stream's CEP (Complex Event Processing) capabilities, driven directly by the CEP engine (`NFA`/`SharedBuffer`).

## Features

- **4 Fraud Detection Patterns:**
  1. RapidTransactionPattern - Detects 2+ large transactions (>$1000) within 30 seconds
  2. UnusualAmountPattern - Detects transactions >10x user's historical average
  3. GeographicAnomalyPattern - Detects transactions from different cities within 1 hour
  4. AccountTakeoverPattern - Detects login → password change → withdrawal within 15 minutes

- **Self-contained** - mock data generation, in-memory demo state, no external systems
- **Simple API** - Clean, intuitive CEP Pattern definitions
- **Comprehensive Tests** - JUnit tests for all patterns

## Project Structure
```
nop-stream-fraud-example/
├── README.md
├── pom.xml
├── src/main/java/io/nop/stream/fraud/
│   ├── FraudDetectionDemo.java          # Main demo class (entry point)
│   ├── model/
│   │   ├── FraudAlert.java             # Alert model
│   │   └── TransactionEvent.java       # Event model
│   ├── pattern/
│   │   ├── AccountTakeoverPattern.java
│   │   ├── GeographicAnomalyPattern.java
│   │   ├── RapidTransactionPattern.java
│   │   └── UnusualAmountPattern.java
│   ├── state/
│   │   ├── DemoKeyedStateStore.java    # Demo-only state store (see class Javadoc)
│   │   └── UserTransactionHistory.java # Keyed-state usage sketch (not wired into the demo)
│   └── util/
│       └── MockTransactionGenerator.java
├── src/main/resources/_vfs/nop/stream/demo/
│   └── fraud-detection.stream.xml       # Unwired XDSL sketch (see Known Limitations)
└── src/test/java/io/nop/stream/fraud/pattern/
    ├── TestAccountTakeoverPattern.java
    ├── TestGeographicAnomalyPattern.java
    ├── TestGeographicAnomalyPatternFix.java
    ├── TestRapidTransactionPattern.java
    └── TestUnusualAmountPattern.java
```

## Quick Start

### Build
```bash
cd nop-stream
mvn clean install -pl nop-stream-fraud-example -am -DskipTests
```

### Run Demo
```bash
cd nop-stream
mvn exec:java -pl nop-stream-fraud-example -Dexec.mainClass="io.nop.stream.fraud.FraudDetectionDemo"
```

The demo prints one section per pattern with the generated events and any `FraudAlert`s. It exits non-zero if the demo itself fails.

### Test
```bash
cd nop-stream
mvn test -pl nop-stream-fraud-example
```

## Fraud Detection Patterns

### 1. RapidTransactionPattern
Detects 2+ large transactions (>$1000) within 30 seconds

**Example:**
- User makes 2 transactions of $2000 within 30 seconds → **ALERT**

### 2. UnusualAmountPattern  
Detects transactions >10x user's historical average

**Example:**
- User's average is $100
- Sudden transaction of $2000 → **ALERT** (>10x average)

Note: in the demo the historical average is a fixed $100 (see the pattern's Javadoc); a real keyed-average pipeline is planned as part of the composite-scenario examples.

### 3. GeographicAnomalyPattern
Detects transactions from different cities within 1 hour

**Example:**
- Transaction in New York, then Los Angeles 1 hour later → **ALERT**

### 4. AccountTakeoverPattern
Detects login → password change → withdrawal within 15 minutes

**Example:**
- Login → Change Password → Withdrawal within 15 minutes → **ALERT**

## Configuration
- **Amount Thresholds**: See pattern constants (e.g., `RAPID_TRANSACTION_AMOUNT_THRESHOLD = 1000`)
- **Time Windows**: Configurable per pattern
- **State**: the demo uses `DemoKeyedStateStore`, a demo-only in-memory store — it is NOT the engine's MemoryStateBackend and is not configurable. See its class Javadoc before copying.

## Architecture
- **CEP Engine**: Uses Nop Stream's CEP implementation directly (`NFA`/`SharedBuffer`)
- **State Management**: demo-only in-memory store (see `DemoKeyedStateStore`)
- **Event Processing**: Synchronous (for demo purposes)
- **No External Dependencies**: No Kafka, Redis, databases

## Known Limitations
- The demo drives the CEP engine API directly; it does not yet show the Java DataStream API or the XDSL declarative entry, and it demonstrates no keyBy/checkpoint/window semantics.
- `src/main/resources/_vfs/nop/stream/demo/fraud-detection.stream.xml` is an unwired sketch (it references partitions the DSL builder does not support yet and is loaded by no code); composite-scenario examples will replace it.
- `UnusualAmountPattern` uses a fixed average instead of per-user keyed state.

## License
Apache License 2.0 (see the repository root license file)
