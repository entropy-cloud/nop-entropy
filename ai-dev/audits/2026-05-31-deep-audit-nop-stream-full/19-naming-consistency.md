# 维度 19：命名与术语一致性

## 第 1 轮（初审）

### [维度19-01] NopCepErrors 错误码字符串与常量名语义不匹配

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/NopCepErrors.java:27-29`
- **证据片段**:
  ```java
  ErrorCode ERR_CEP_NOT_CONDITION_DOES_NOT_SUPPORT_GROUP =
          define("nop.err.cep.follow-not-does-support-group", "Not condition does not support complex patterns",
                  ARG_PART_NAME, ARG_FOLLOW_KIND);
  ```
- **严重程度**: P2
- **现状**: 常量名说 "NOT_CONDITION 不支持"，错误码字符串说 "follow-not 支持"，语义方向完全不同。
- **风险**: 运维时根据错误码搜索会得到错误的理解。
- **建议**: 统一错误码字符串为 `nop.err.cep.not-condition-does-not-support-group`。
- **信心水平**: 确定
- **误报排除**: 不是误报——常量名和错误码字符串语义矛盾。
- **复核状态**: 未复核

### [维度19-02] EmbeddedDistributedExecutor 使用裸字符串构造 StreamException

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/EmbeddedDistributedExecutor.java:193-194`
- **证据片段**:
  ```java
  StreamException ex = new StreamException(
          failures.size() + " task(s) failed during distributed execution");
  ```
- **严重程度**: P3
- **现状**: 绕过 NopStreamErrors 错误码体系，同文件第 178 行正确使用了 ERR_STREAM_INVALID_STATE。
- **建议**: 定义专用错误码。
- **信心水平**: 确定
- **误报排除**: 不是误报但影响有限。
- **复核状态**: 未复核

## 合规确认

- 错误码前缀一致：NopStreamErrors 用 nop.err.stream.*，NopCepErrors 用 nop.err.cep.*
- 核心接口命名一致：SourceFunction/SinkFunction/StreamOperator 体系完整
- Window 实体层次清晰
