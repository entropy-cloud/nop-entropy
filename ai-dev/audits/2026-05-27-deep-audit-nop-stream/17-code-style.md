# 维度 17：代码风格与规范

**审计日期**: 2026-05-27

## Import 排序规范

**Nop 平台规范**: `java.*` → `jakarta.*` → third-party (`org.*`, `com.*`) → `io.nop.*`

## 发现

### D17-01: 30+ 个文件 import 顺序不合规
- **严重程度**: P2
- **现状**: 多个文件中 `org.slf4j.Logger` 或 static import 出现在 `io.nop.*` 之后，违反了 third-party → io.nop 的排序规则。
  
  **具体文件列表**（部分）：
  | 文件 | 问题 |
  |------|------|
  | `WindowAggregationOperator.java:6` | `import org.slf4j.Logger` 出现在 `io.nop.*` 之后 |
  | `StreamTaskInvokable.java:14` | 同上 |
  | `TaskExecutor.java:22` | 同上 |
  | `CheckpointBarrierTracker.java:17` | 同上 |
  | `Task.java:15` | 同上 |
  | `InputGate.java:27` | 同上 |
  | `JdbcCheckpointStorage.java:19` | 同上 |
  | `LocalFileCheckpointStorage.java:18` | 同上 |
  | `GraphModelCheckpointExecutor.java:19` | 同上 |
  | `EmbeddedDistributedExecutor.java:14` | 同上 |
  | `WindowOperator.java:26` | `static import Guard.checkArgument` 出现在 `io.nop.*` 之后 |
  | `NFACompiler.java:35` | 同上 |
  | `SharedBuffer.java:29` | `import org.slf4j.Logger` 出现在 `io.nop.*` 之后 |
  | `NopCepErrors.java:11` | `static import ErrorCode.define` 出现在 `io.nop.*` 之后 |
  | `CepRuntimeContext.java:22` | 同上 |
  | `PatternStream.java:21` | 同上 |
  | `PatternStreamBuilder.java:25` | 同上 |

  **共同模式**: 所有文件都是 `io.nop.core` 或 `io.nop.api` 的 import 出现在 `org.slf4j` 之前。这说明可能是编辑器自动 import 时将 `io.nop.*` 排到了 `org.*` 前面。

- **风险**: 不影响编译和运行，但违反项目规范。
- **建议**: 批量运行 IDE 的 "Optimize Imports" 功能。可在后续 Plan 57 (Code Cleanup) 中一并处理。

### D17-02: FraudDetectionDemo import 顺序异常
- **文件**: `nop-stream-fraud-example/.../FraudDetectionDemo.java:13`
- **严重程度**: P3
- **现状**: `import java.math.BigDecimal` 出现在 third-party import 之后（order: 2 → 0）。
- **建议**: 调整 import 顺序。

### D17-03: WatermarkStrategy static import 位置异常
- **文件**: `nop-stream-core/.../eventtime/WatermarkStrategy.java:25`
- **严重程度**: P3
- **现状**: `static import Guard.checkArgument` 出现在 `io.nop.*` import 之后。

### D17-04: 命名规范检查
- **严重程度**: — ✅
- **现状**: 
  - 类名：PascalCase ✅
  - 方法名/变量名：camelCase ✅
  - 常量名：UPPER_SNAKE_CASE ✅
  - 包名：`io.nop.stream.*` ✅

### D17-05: 未使用的 import/变量
- **严重程度**: P3
- **现状**: 未发现明显的未使用 import（通过抽样检查）。_gen 文件的 `@SuppressWarnings("PMD.UnusedLocalVariable")` 是生成代码的正常模式。

### D17-06: 无 FQN 引用问题
- **严重程度**: — ✅
- **现状**: 生产代码中未发现全限定类名引用（`io.nop.xxx.ClassName` 形式的内联使用），仅 `Trigger.java:90` 有一处 Javadoc 中的引用，属于文档。

### D17-07: 无硬编码中文字符串
- **严重程度**: — ✅
- **现状**: 生产代码中未发现中文字符串。`NopCepErrors.java` 中的中文是 ErrorCode 定义中的描述文本，这是规范用法。

## 总结

| 指标 | 数量 |
|------|------|
| Import 顺序违规文件 | ~30 |
| 命名规范违规 | 0 |
| 未使用 import | 0 |
| FQN 引用 | 0 |

主要问题是 import 排序不合规，集中在 `org.slf4j` 和 static import 放在 `io.nop.*` 之后。建议在 Plan 57 中批量修复。
