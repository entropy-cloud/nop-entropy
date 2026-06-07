# 维度 19：命名与术语一致性

## 第 1 轮（初审）

### [维度19-01] JobCoreErrors 中状态标记使用非标准错误码格式

- **文件**: `JobCoreErrors.java:24-37`
- **证据片段**:
```java
ErrorCode ERR_JOB_TIMEOUT = define("JOB_TIMEOUT", "Job task timed out");
ErrorCode ERR_JOB_CANCELED = define("JOB_CANCELED", "Job fire/task canceled");
```
- **严重程度**: P3
- **现状**: 不使用 `nop.err.*` 前缀，而是直接用 `JOB_TIMEOUT` 等大写字符串。注释说明是 "status markers, not thrown exceptions"。
- **风险**: 与其他模块的 nop.err.* 约定不一致，可能误导。
- **建议**: 注释已解释设计意图，可考虑更明确标注 "NOT FOR THROWING"。
- **信心水平**: 高
- **复核状态**: 未复核

### 正向确认

- 实体名在 ORM、BizModel、接口中全链路一致
- 错误码前缀与模块名一致（nop.err.job.*）
- 字段名在数据库列名、Java 属性名、GraphQL 字段名之间一致
