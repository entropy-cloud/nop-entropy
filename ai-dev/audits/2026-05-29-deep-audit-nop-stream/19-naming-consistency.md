# 维度19：命名与术语一致性

## 第 1 轮（初审）

### 检查范围说明

检查了 nop-stream 模块中的命名一致性：

- **实体/类命名**：核心类名（StreamExecutionEnvironment, CheckpointCoordinator, PendingCheckpoint 等）在所有引用中一致。
- **字段命名**：数据库列名（snake_case）与 Java 属性名（camelCase）在 checkpoint 相关配置中一致。
- **术语一致性**：整个模块使用统一的术语（checkpoint、savepoint、barrier、operator、task、subtask）。无同义多词问题。
- **错误码前缀**：`NopStreamErrors` 使用 `nop.err.stream.*` 前缀，`NopCepErrors` 使用 `nop.err.cep.*` 前缀，与模块名一致。
- **接口 I 前缀**：已在维度 17 中审计（34 个 Nop 原生接口未使用 I 前缀）。

**结论**：命名基本一致。接口 I 前缀问题已在维度 17 中覆盖。

### 零发现确认

- 实体名在 ORM/BizModel/接口/文档中一致 ✓
- 字段命名 snake_case/camelCase 转换正确 ✓
- 错误码前缀与模块名一致 ✓
- 无同义多词问题 ✓
