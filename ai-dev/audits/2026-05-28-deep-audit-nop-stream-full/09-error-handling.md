# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### 模块异常层次结构

| 类 | 类型 | 位置 |
|---|---|---|
| `StreamException` | checked | nop-stream-core |
| `StreamRuntimeException` | unchecked | nop-stream-core (extends NopException) |
| `MalformedPatternException` | unchecked | nop-stream-cep (extends StreamRuntimeException) |

ErrorCode 定义: NopStreamErrors (10个错误码), NopCepErrors (3个错误码)。所有消息均为英文，有专门的测试 TestErrorCodeMessagesEnglish 校验。

### [维度09-01] Exception cause dropped in MemoryKeyedStateBackend.wrapInAccumulator

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:571-573`
- **证据片段**:
  ```java
  } catch (Exception e) {
      throw new StreamException(ERR_STREAM_STATE_ERROR)
              .param(ARG_DETAIL, "Failed to create accumulator: " + accumulatorClass.getName());
      // BUG: cause 'e' is caught but never passed to the StreamException constructor
  }
  ```
- **严重程度**: P1
- **现状**: 捕获的异常 e 被静默丢弃，丢失了根因（InstantiationException、IllegalAccessException 等），使调试显著困难。
- **风险**: 生产环境排查状态创建失败时无法获取根因异常信息。
- **建议**: 改为 `throw new StreamException(ERR_STREAM_STATE_ERROR, e).param(...)`
- **误报排除**: 不是代码风格问题。异常链断裂直接导致生产环境排查困难，是功能性缺陷。同类中几乎所有其他 catch 块都正确传递了 cause。
- **复核状态**: 已保留（独立复核确认：同文件3处正确用法证明是遗漏，StreamException 支持 cause 构造器，修复简单）

## 维度复核结论

- [维度09-01]: **保留 P1** — 独立复核确认异常 e 被静默丢弃，同文件1067/1152/1184行有正确用法
- [维度09-02]: **保留 P2** — 约30处字符串异常确认
- [维度09-03]: **保留 P3** — 3处直接 NopException 确认
- [维度09-04]: **保留 P3** — IllegalArgumentException 确认

### [维度09-02] String-only StreamException without ErrorCode (~30 production sites)

- **文件**: 多个文件（GraphModelCheckpointExecutor 8处, NFA 5处, ChainingOutput 5处, MemoryKeyedStateBackend 3处, 等）
- **严重程度**: P2
- **现状**: 约30处生产代码使用 `new StreamException("message")` 而非 `new StreamException(ERR_XXX).param(...)`，绕过了结构化 ErrorCode 系统。所有消息均为英文，且大多数正确保留了 cause。问题在于与 ErrorCode 模式的不一致性。
- **风险**: 无法通过错误码进行结构化处理和国际化。但功能上不影响运行。
- **建议**: 优先迁移 GraphModelCheckpointExecutor（8处，checkpoint/restore 是跨模块公共 API）、ChainingOutput（5处）和 NFA（5处）。
- **误报排除**: 模块内部实现可使用字符串构造器（两档策略允许），但其中跨模块公共 API 的部分应使用 ErrorCode。
- **复核状态**: 未复核

### [维度09-03] Direct NopException bypasses module exception hierarchy (3 sites in CEP)

- **文件**: `nop-stream-cep/.../ICepPatternGroupModel.java:26`, `CepPatternBuilder.java:60,94`
- **严重程度**: P3
- **现状**: 3处直接 throw new NopException(ERR_CEP_XXX) 而非 StreamRuntimeException。功能上可行（NopException 是父类），但调用者通过 instanceof StreamException/StreamRuntimeException 无法捕获。
- **建议**: 改为使用 StreamRuntimeException 包装。
- **误报排除**: 功能上不会导致运行时错误，但违反了模块异常层次结构约定。
- **复核状态**: 未复核

### [维度09-04] IllegalArgumentException in CheckpointType enum parsing

- **文件**: `nop-stream-core/.../checkpoint/CheckpointType.java:98`
- **证据片段**:
  ```java
  throw new IllegalArgumentException("Unknown CheckpointType name: " + name);
  ```
- **严重程度**: P3
- **现状**: 使用 IllegalArgumentException 而非模块约定的 StreamException(ERR_STREAM_INVALID_ARG)。
- **建议**: 改为 StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, name)
- **误报排除**: 虽然 IllegalArgumentException 是 Java 枚举解析的惯例，但模块已建立 StreamException 约定，应保持一致。
- **复核状态**: 未复核

### 正面观察

| 方面 | 状态 |
|------|------|
| 无吞掉异常（空 catch 块） | PASS |
| 异常链保留（除1处外） | PASS |
| 无中文错误消息 | PASS |
| ErrorCode 消息英文测试 | PASS |
| SLF4J 日志使用 | PASS |
| addSuppressed 异常累积 | PASS |
| NopException.adapt(e) 在 lambda 中使用 | PASS |
