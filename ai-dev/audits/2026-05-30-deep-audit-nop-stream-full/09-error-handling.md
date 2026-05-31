# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### 模块异常体系

| 异常类 | 父类 | 用途 |
|--------|------|------|
| `StreamRuntimeException` | `NopException` | 模块根异常 |
| `StreamException` | `StreamRuntimeException` | 通用 checked 异常 |
| `CheckpointStorageException` | `StreamException` | Checkpoint 存储专用 |
| `MalformedPatternException` | `StreamRuntimeException` | CEP 模式校验 |

ErrorCode：`NopStreamErrors`（34 个）+ `NopCepErrors`（9 个）

---

### [维度09-01] KeyedStreamImpl 6 处公共 API 方法使用 UnsupportedOperationException 而非 ErrorCode

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/KeyedStreamImpl.java:181,189,204,213,228,237`
- **证据片段**:
```java
// KeyedStreamImpl.java 第 181 行
throw new UnsupportedOperationException("sum(int field) with field != 0 requires Tuple types");
// 第 189 行
throw new UnsupportedOperationException("min(int field) with field != 0 requires Tuple types");
// ... 共 6 处
```
- **严重程度**: P2
- **现状**: KeyedStreamImpl 的 sum/min/max 等面向用户的公共 API 方法在参数校验失败时使用 `UnsupportedOperationException` 而非 `StreamException(ERR_STREAM_UNSUPPORTED).param(...)` 模式。
- **风险**: 用户无法通过结构化 ErrorCode 获取诊断信息，不符合公共 API 层的两档策略要求。
- **建议**: 改为 `throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_METHOD_NAME, "sum").param(ARG_DETAIL, "...")`。
- **信心水平**: 确定
- **误报排除**: 这些是面向用户的公共 API 方法（DataStream 操作），不属于 JDK 标准库或内部实现。
- **复核状态**: 未复核

---

### [维度09-02] CEP 子模块 10+ 处 MalformedPatternException 使用字符串消息而非 ErrorCode

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/Pattern.java:243,248,644,650,666,673`，`NFACompiler.java:189,215,232`，`NFAStateNameHandler.java:58`
- **证据片段**:
```java
// Pattern.java 第 243 行
throw new MalformedPatternException("Only one until condition can be applied.");
// NFACompiler.java 第 189 行
throw new MalformedPatternException("NotFollowedBy is not supported without windowTime...");
```
- **严重程度**: P3
- **现状**: CEP 子模块的 MalformedPatternException 大量使用字符串构造器而非 `NopCepErrors.ERR_CEP_MALFORMED_PATTERN` ErrorCode。消息为英文且语义清晰，作为模块内部实现可接受。
- **风险**: 若 CEP API 被视为跨模块公共接口，则不一致的异常策略会降低可诊断性。
- **建议**: 统一使用 `NopCepErrors.ERR_CEP_MALFORMED_PATTERN` + `.param(ARG_PATTERN_DETAIL, ...)`。
- **信心水平**: 很可能
- **误报排除**: `NopCepErrors` 已定义了 `ERR_CEP_MALFORMED_PATTERN` 但几乎未使用，说明是疏忽而非设计选择。
- **复核状态**: 未复核

---

### 整体评价

nop-stream 错误处理整体水平优秀：
- 完整的模块级异常层次
- 43 个结构化 ErrorCode（NopStreamErrors 34 + NopCepErrors 9）
- .param() 上下文传递广泛且规范
- 无空 catch 块、无 .printStackTrace()、无中文异常消息
- 日志全部使用 SLF4J

---

## 维度复核结论

（待复核）

## 子项复核结论

（待复核）

## 最终保留项

（待复核后填写）
