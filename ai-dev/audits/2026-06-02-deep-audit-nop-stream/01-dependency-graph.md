# Dimension 01: Dependency Graph & Module Boundary Audit — nop-stream

## Complete Internal Dependency Graph (Text)

```
nop-stream (parent pom, packaging=pom)
│
├── nop-stream-api [PLACEHOLDER - empty, no src/]
│   └── (no dependencies, no dependents)
│
├── nop-stream-core
│   ├── → nop-commons          (compile)
│   ├── → nop-core             (compile)
│   └── → junit-jupiter        (test)
│   └── [produces test-jar artifact]
│
├── nop-stream-cep
│   ├── → nop-stream-core      (compile)
│   ├── → nop-stream-core      (test-jar, test)
│   └── → junit-jupiter        (test)
│   └── [exec-maven-plugin for codegen]
│
├── nop-stream-connector
│   ├── → nop-stream-core      (compile)
│   ├── → nop-batch-core       (compile, optional)
│   ├── → nop-message-debezium (compile, optional)
│   ├── → nop-message-core     (test)
│   └── → junit-jupiter        (test)
│
├── nop-stream-runtime
│   ├── → nop-stream-core      (compile)
│   ├── → nop-dao              (provided)
│   ├── → nop-stream-core      (test-jar, test)
│   ├── → nop-message-core     (test)
│   ├── → junit-jupiter        (test)
│   ├── → HikariCP             (test)
│   └── → h2                   (test)
│   └── [SPI: ICheckpointExecutorFactory → CheckpointExecutorFactoryImpl]
│   └── [SPI: IDeploymentPlanProvider → DeploymentPlanProviderImpl]
│
├── nop-stream-checkpoint [PLACEHOLDER - empty, no src/]
│   └── (no dependencies)
│
├── nop-stream-flow [PLACEHOLDER - empty, no src/]
│   └── (no dependencies)
│
├── nop-stream-flink [PLACEHOLDER - empty, no src/]
│   └── (no dependencies)
│
└── nop-stream-fraud-example
    ├── → nop-stream-cep       (compile)
    └── → junit-jupiter        (test)
```

## 第 1 轮（初审）

### [维度01-01] Four Empty Placeholder Modules Built and Published as Empty JARs

- **File**: `nop-stream/pom.xml:16-26` and individual pom.xml files at:
  - `nop-stream/nop-stream-api/pom.xml`
  - `nop-stream/nop-stream-flow/pom.xml`
  - `nop-stream/nop-stream-flink/pom.xml`
  - `nop-stream/nop-stream-checkpoint/pom.xml`
- **Evidence snippet** (parent pom listing all modules):
```xml
    <modules>
        <module>nop-stream-api</module>
        <module>nop-stream-core</module>
        <module>nop-stream-checkpoint</module>
        <module>nop-stream-flink</module>
        <module>nop-stream-flow</module>
        <module>nop-stream-cep</module>
        <module>nop-stream-connector</module>
        <module>nop-stream-runtime</module>
        <module>nop-stream-fraud-example</module>
    </modules>
```
- **Evidence snippet** (typical placeholder pom):
```xml
    <artifactId>nop-stream-checkpoint</artifactId>
    <!-- placeholder, planned but not implemented -->
```
- **Severity**: P3
- **Current state**: Four modules (api, checkpoint, flink, flow) are declared in the Maven reactor with no source code, producing empty JARs.
- **Risk**: Each empty module adds ~2-3 seconds of Maven reactor overhead per build. The empty JARs get published to Maven repository, confusing consumers. Documentation in `docs-for-ai/01-repo-map/module-groups.md` lists these as real modules, which misleads readers.
- **Recommendation**: Either remove the placeholder modules from the `<modules>` list in the parent pom, or add README.md in each placeholder directory. Update `docs-for-ai/01-repo-map/module-groups.md` to mark them as "planned/not yet implemented".
- **Confidence**: Certain
- **False positive exclusion**: Has structural consequences: build time cost, published empty artifacts, misleading documentation. Four modules have zero source files, confirmed by glob search.
- **Review status**: Unreviewed

### [维度01-02] nop-stream-api Is Empty and Has No Dependents — Public API Surface Lives in nop-stream-core

- **File**: `nop-stream/nop-stream-api/pom.xml:12-14`
- **Evidence snippet**:
```xml
    <artifactId>nop-stream-api</artifactId>
    <!-- placeholder, planned but not implemented -->
    <!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->
```
- **Severity**: P3
- **Current state**: The api module exists as an empty placeholder. The public API surface (SourceFunction, SinkFunction, DataStream, StreamExecutionEnvironment, etc.) all live in nop-stream-core's package tree.
- **Risk**: Low — streaming engine is a single-vendor internal library. If external consumers appear, they must depend on nop-stream-core which includes all implementation details.
- **Recommendation**: Acceptable as-is for now. When module matures and external consumers appear, consider extracting interfaces to nop-stream-api.
- **Confidence**: Certain
- **False positive exclusion**: nop-stream is a streaming engine, not a standard Nop business module. Finding documents the deviation for future reference.
- **Review status**: Unreviewed

## Compliant Module List

| Sub-module | Boundary Assessment | Notes |
|---|---|---|
| **nop-stream-core** | COMPLIANT | Depends only on nop-commons and nop-core. No dao, no service framework, no runtime. |
| **nop-stream-cep** | COMPLIANT | Depends only on nop-stream-core (compile + test-jar). No reverse dependency. |
| **nop-stream-connector** | COMPLIANT | Depends on nop-stream-core (compile). Optional dependencies are genuinely optional. |
| **nop-stream-runtime** | COMPLIANT | nop-dao correctly scoped as `provided`. SPI files correctly register implementations. |
| **nop-stream-fraud-example** | COMPLIANT | Depends only on nop-stream-cep. |

## Circular Dependency Check

No circular dependencies. All dependency arrows point toward core. Confirmed clean.

## Reverse Dependency Check

Core has no knowledge of any higher module. Confirmed clean.

## Optional Dependency Genuineness Check

Both optional dependencies (nop-batch-core, nop-message-debezium) are genuinely optional.

## Overall Assessment

nop-stream has a **clean and well-structured dependency graph** with no circular dependencies, correct layering direction, and appropriate scope management. Only P3-level findings: empty placeholder modules.
