# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-stream-cep: nop-xlang declared as compile dependency with zero compile-time Java usage

- **文件**: `nop-stream/nop-stream-cep/pom.xml`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-xlang</artifactId>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: nop-xlang is declared as compile-scope dependency, but no Java source file in the module imports any io.nop.xlang.* class. There are also no XLang resource files (.xpl, .xjs, .xdef) in the module's resource directory (no src/main/resources/ at all).
- **风险**: Adds unnecessary transitive classpath weight for consumers that only need the CEP engine's programmatic API. The XDef schema file lives in nop-xdefs, and CepPatternModel's _gen base classes extend AbstractComponentModel from nop-core -- neither requires nop-xlang at compile time. nop-xlang is only needed at runtime when the XLang model loader parses XML pattern definitions.
- **建议**: Change the dependency to optional, or add a comment in the POM explaining the runtime dependency rationale.
- **误报排除**: Verified zero import io.nop.xlang.* across all 70+ Java files in nop-stream-cep's src/main/java/. The dependency is legitimate for runtime XDef model loading but is over-scoped at compile time.
- **复核状态**: 未复核

### [维度01-02] nop-stream-fraud-example: accesses nop-stream-core internal implementation class SimpleKeyedStateStore

- **文件**: `nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/FraudDetectionDemo.java:29`
- **证据片段**:
  ```java
  import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
  ```
- **严重程度**: P3
- **现状**: FraudDetectionDemo imports SimpleKeyedStateStore from the simple sub-package of nop-stream-core. The simple sub-package conventionally signals internal implementation, not public API. The example reaches two layers down (fraud-example -> cep -> core) without a direct pom dependency on nop-stream-core.
- **风险**: As an example module, it sets a pattern that downstream users may copy. If SimpleKeyedStateStore is refactored or renamed, this example will break.
- **建议**: Either add a test-scope factory helper in nop-stream-cep that provides a simple KeyedStateStore implementation, or inline a minimal equivalent directly in the example module.
- **误报排除**: The io.nop.stream.core.common.state.simple package is clearly an implementation package. The fraud-example has no direct pom dependency on nop-stream-core.
- **复核状态**: 未复核

## 完整依赖图

```
nop-stream (parent aggregator pom)
│
├── nop-stream-api ──── (empty placeholder, no deps)
├── nop-stream-checkpoint ──── (empty placeholder, no deps)
├── nop-stream-flink ──── (empty placeholder, no deps)
├── nop-stream-flow ──── (empty placeholder, no deps)
│
├── nop-stream-core ──► nop-commons
│                   └─► nop-core
│
├── nop-stream-cep ──► nop-stream-core
│                  └─► nop-xlang
│
├── nop-stream-connector ──► nop-stream-core
│                        └─► nop-batch-core
│                        └─► nop-message-core      [optional]
│                        └─► nop-message-debezium   [optional]
│
├── nop-stream-runtime ──► nop-stream-core
│                      └─► nop-dao                [provided]
│                      └─► nop-message-core        [test]
│
└── nop-stream-fraud-example ──► nop-stream-cep
```

## 合规模块清单

| Module | Verdict |
|---|---|
| nop-stream-api | COMPLIANT |
| nop-stream-core | COMPLIANT |
| nop-stream-checkpoint | COMPLIANT |
| nop-stream-flink | COMPLIANT |
| nop-stream-flow | COMPLIANT |
| nop-stream-cep | COMPLIANT (minor P3) |
| nop-stream-connector | COMPLIANT |
| nop-stream-runtime | COMPLIANT |
| nop-stream-fraud-example | COMPLIANT (minor P3) |

无循环依赖。无反向依赖。第三方依赖管理规范。
