# Dimension 02: Module Responsibility & File Boundary — nop-stream

## 第 1 轮（初审）

### [维度02-01] WindowOperator.java is a 1664-line god class with 10 inner classes

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:1-1664`
- **Evidence snippet**: Contains 5 distinct responsibilities: window processing, state management, 5 namespace-aware state adapter inner classes (~210 lines of copy-paste), window/trigger context objects, and timer data class.
- **Severity**: P2
- **Current state**: The 5 NamespaceAware state wrappers (lines 1229-1437) and 2 KeyedStateStore implementations (lines 1141-1227) could be extracted into a standalone utility class, reducing the file by ~300 lines.
- **Risk**: Maintenance burden; the state adapter pattern is duplicated 5 times with near-identical structure.
- **Recommendation**: Extract state adapters to NamespaceAwareStateAdapters utility class.
- **Confidence**: Certain
- **False positive exclusion**: Not a style preference — 210 lines of near-identical delegation code that could be parameterized.
- **Review status**: Unreviewed

### [维度02-02] Core module contains 1650+ lines of concrete execution code

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/` (34 files)
- **Severity**: P1
- **Current state**: TaskExecutor (439 lines), StreamTaskInvokable (432 lines), GraphExecutionPlan (462 lines), Task (319 lines) are all concrete execution implementations living in core. StreamExecutionEnvironment.execute() directly performs full local execution.
- **Risk**: Core is not actually "core APIs + abstractions" — it's a fully functional standalone streaming runtime. Any module depending on nop-stream-core gets the full execution engine whether it needs it or not.
- **Recommendation**: Medium-term refactoring to extract execution implementations to nop-stream-runtime, keeping core as API + abstractions.
- **Confidence**: Certain
- **False positive exclusion**: The 34 files in core/execution/ include TaskExecutor, InputGate, RecordWriter, etc. — all concrete implementations, not interfaces.
- **Review status**: Unreviewed

### [维度02-03] GraphModelCheckpointExecutor.java is 807-line procedural god-object

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1-807`
- **Severity**: P2
- **Current state**: 20+ static methods handling 4 near-identical execution paths (checkpoint, savepoint, termination). The two executeWithCheckpoint overloads share ~80% of code.
- **Risk**: Any change to the orchestration pattern must be duplicated across 4 paths.
- **Recommendation**: Refactor into separate classes (CheckpointExecutionOrchestrator, StateRestorer, SavepointManager).
- **Confidence**: Certain
- **False positive exclusion**: The copy-paste pattern between the two executeWithCheckpoint overloads is verifiable.
- **Review status**: Unreviewed

### [维度02-04] Four empty placeholder modules add build noise

- **Severity**: P3 (already reported in Dim 01)

### [维度02-05] NFAFactoryCompiler is a 913-line inner class

- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:142-1055`
- **Severity**: P3
- **Current state**: The outer NFACompiler is a 33-line facade. The real work is the 913-line inner class.
- **Recommendation**: Extract to top-level class for readability and testability.
- **Confidence**: Certain
- **False positive exclusion**: Standard SRP concern for a 913-line inner class.
- **Review status**: Unreviewed
