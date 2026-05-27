# 维度 09：错误处理与错误码

**审计日期**: 2026-05-27

## 异常层次

```
NopException (nop-api-core)
  └── StreamRuntimeException (nop-stream-core)
        └── StreamException (nop-stream-core)
```

- `StreamRuntimeException` 正确继承 `NopException`
- `StreamException` 正确继承 `StreamRuntimeException`

## 发现

### D09-01: 生产代码中 124 处 StreamException 使用硬编码字符串，无 ErrorCode
- **文件**: 分布在 51 个生产源文件中
- **严重程度**: P1
- **现状**: 所有 `StreamException` 都使用 `new StreamException("hardcoded message")` 形式，没有一处使用 `ErrorCode`。仅 `NopCepErrors` 中定义了 3 个 `ErrorCode`（且正确使用了 `NopException`）。
- **风险**: 
  - 无法通过错误码进行国际化
  - 无法通过错误码进行程序化处理
  - 不符合 Nop 平台规范（应使用 `ErrorCode` + `.param()`）
- **建议**: 创建 `NopStreamErrors` 错误码定义类，将高频异常（如参数校验、状态不一致）改为 ErrorCode 模式。示例：
  ```java
  public interface NopStreamErrors {
      ErrorCode ERR_STREAM_NULL_ARG = define("nop.err.stream.null-arg", "参数 {argName} 不能为 null", ARG_ARG_NAME);
      ErrorCode ERR_STREAM_INVALID_STATE = define("nop.err.stream.invalid-state", "状态异常: {detail}", ARG_DETAIL);
  }
  ```

### D09-02: 生产代码中存在 1 处 RuntimeException
- **文件**: `nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java:936`
- **严重程度**: P1
- **现状**: `throw new RuntimeException("Failed to create accumulator for ReducingState", e)`
- **风险**: 未使用平台异常体系，上层无法统一处理。
- **建议**: 改为 `throw new StreamException("Failed to create accumulator for ReducingState", e)` 或使用 `NopException.adapt(e)`。

### D09-03: ChainingOutput 中 5 处 StreamRuntimeException 包装器丢失上下文
- **文件**: `nop-stream-core/.../operators/ChainingOutput.java:40,54,63,77,86`
- **严重程度**: P2
- **现状**: 所有 catch 块都用 `new StreamRuntimeException("Error forwarding ...", e)` 包装原始异常。虽然保留了 cause，但丢失了原始异常的类型信息。
- **风险**: 如果调用方需要区分不同类型的异常，无法做到。
- **建议**: 使用 `NopException.adapt(e)` 或 `throw (StreamRuntimeException) new StreamRuntimeException(...).initCause(e)`。

### D09-04: NFA.java 使用 StreamRuntimeException 而非 StreamException
- **文件**: `nop-stream-cep/.../nfa/NFA.java:788,832`
- **严重程度**: P2
- **现状**: 使用 `StreamRuntimeException`，其他模块统一使用 `StreamException`。
- **风险**: 异常层次不一致。`StreamException` 是检查型异常（checked exception 的语义），而 `StreamRuntimeException` 是运行时异常。混用会导致调用方需要同时 catch 两种。
- **建议**: 统一使用 `StreamException` 或 `NopException.adapt(e)`。

### D09-05: NFA.java 硬编码错误消息 "Failure happened in filter function."
- **文件**: `nop-stream-cep/.../nfa/NFA.java:788,832`
- **严重程度**: P2
- **现状**: 错误消息为固定的英文字符串，无法区分不同的 filter 调用点。
- **风险**: 调试时无法确定是哪个 filter 函数出错。
- **建议**: 至少在消息中包含 condition 或 state 名称。

### D09-06: GraphModelCheckpointExecutor 中多处 LOG.warn 后不 rethrow
- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:322,338,470,504`
- **严重程度**: P2
- **现状**: 
  - L322: `catch (Exception e) { LOG.warn("Failed to trigger terminal savepoint", e); }` — 不抛出
  - L338: `catch (Exception e) { LOG.warn("Failed to stop source invokable", e); }` — 不抛出
  - L470: `catch (Exception e) { LOG.warn("Failed to inject checkpoint barrier", e); }` — 不抛出
  - L504: `catch (Exception e) { LOG.warn("Failed to trigger final checkpoint", e); }` — 不抛出
- **风险**: 检查点操作静默失败可能导致数据丢失，而运维人员可能不会注意到 warn 级别日志。
- **建议**: 对于关键操作（如 terminal savepoint、final checkpoint），应至少升级为 `LOG.error`，并考虑设置错误标志使上层能感知。或者改为 rethrow。

### D09-07: NFA 构造方法中 3 处 StreamRuntimeException 硬编码消息
- **文件**: `nop-stream-cep/.../nfa/NFA.java:147,160,173`
- **严重程度**: P2
- **现状**: NFA 构造方法中参数校验使用 `StreamRuntimeException`，消息为硬编码英文。
- **建议**: 改用 `Guard.notNull()` / `Guard.checkArgument()` 或 ErrorCode。

### D09-08: SkipToElementStrategy 使用 StreamRuntimeException
- **文件**: `nop-stream-cep/.../nfa/aftermatch/SkipToElementStrategy.java:59,79`
- **严重程度**: P2
- **现状**: 同 NFA，混用 StreamRuntimeException。
- **建议**: 统一使用 StreamException 或 ErrorCode。

### D09-09: CepOperator 正确使用 NopException.adapt()
- **文件**: `nop-stream-cep/.../operator/CepOperator.java:220,351,385`
- **严重程度**: —
- **现状**: ✅ 正确使用 `NopException.adapt(e)` 包装 checked exception。
- **结论**: 合规

### D09-10: NopCepErrors 正确使用 ErrorCode 模式
- **文件**: `nop-stream-cep/.../NopCepErrors.java`
- **严重程度**: —
- **现状**: ✅ 3 个 ErrorCode 定义，使用 `define()` + 参数 + `.param()` 模式。
- **结论**: 合规，但只有 3 个错误码，与 124 处硬编码字符串形成对比。

### D09-11: 测试代码中的 RuntimeException 使用合理
- **文件**: 多个测试文件
- **严重程度**: —
- **现状**: 测试代码中使用 `throw new RuntimeException(e)` 或 `throw new RuntimeException("test failure")` 是合理的，因为测试框架不强制检查异常。
- **结论**: 合规

## 统计

| 指标 | 数量 |
|------|------|
| 生产代码 StreamException (硬编码字符串) | 124 |
| 生产代码 RuntimeException | 1 |
| 生产代码 StreamRuntimeException (硬编码) | 8 |
| 生产代码 NopException.adapt() | 6 |
| 生产代码 ErrorCode 定义 | 3 |
| 使用 ErrorCode 的异常抛出 | 3 |

## 优先修复建议

1. **P1**: 将 `MemoryKeyedStateBackend.java:936` 的 `RuntimeException` 改为 `StreamException`
2. **P1**: 创建 `NopStreamErrors` 错误码定义类，逐步迁移高频异常
3. **P2**: 统一 NFA/ChainingOutput 中的异常类型为 `StreamException`
