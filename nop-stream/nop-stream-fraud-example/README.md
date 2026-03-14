# Nop Stream Fraud Detection Example

This example demonstrates real-time fraud detection using Nop Stream's CEP (Complex Event Processing) capabilities.

## Features

- **4 Fraud Detection Patterns:**
  1. RapidTransactionPattern - Detects 2+ large transactions (>$1000) within 30 seconds
  2. UnusualAmountPattern - Detects transactions >10x user's historical average
  3. GeographicAnomalyPattern - Detects transactions from different cities within 1 hour
  4. AccountTakeoverPattern - Detects login → password change → withdrawal within 15 minutes

- **MemoryStateBackend** - In-memory state management for user transaction history
- **Simple API** - Clean, intuitive CEP Pattern definitions
- **Comprehensive Tests** - JUnit tests for all patterns

## Project Structure
```
nop-stream-fraud-example/
├── src/main/java/io/nop/stream/fraud/
│   ├── FraudDetectionDemo.java          # Main demo class
│   ├── model/
│   │   ├── FraudAlert.java             # Alert model
│   │   └── TransactionEvent.java       # Event model
│   └── pattern/
│       ├── AccountTakeoverPattern.java
│       ├── GeographicAnomalyPattern.java
│       ├── RapidTransactionPattern.java
│       └── UnusualAmountPattern.java
└── src/test/java/io/nop/stream/fraud/pattern/
    ├── TestAccountTakeoverPattern.java
    ├── TestGeographicAnomalyPattern.java
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
- **State Backend**: MemoryStateBackend (configurable)

- **Test Coverage**: Comprehensive unit tests for all patterns
- **Code Style**: Follow Nop Platform conventions
- **Java Version**: 17+

## Architecture
- **CEP Engine**: Uses Nop Stream's CEP implementation
- **State Management**: In-memory with ValueState
- **Event Processing**: Synchronous (for demo purposes)
- **No External Dependencies**: No Kafka, Redis, databases

## Future Enhancements
- Integration with actual streaming infrastructure
- Distributed processing support- Persistent state management
- Real-time monitoring dash- metrics
- More sophisticated patterns

## License
Apache License 2.0 (see LICENSE file)

## Contributing
Contributions welcome! Please read CONTRIBUT.md for guidelines.
