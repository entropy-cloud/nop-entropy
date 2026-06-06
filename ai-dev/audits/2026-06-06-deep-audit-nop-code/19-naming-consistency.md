# 维度 19：命名与术语一致性

## 第 1 轮（初审）

### [维度19-01] NopCodeFlow.entryPointId 与 ExecutionFlow.entryPointSymbolId 命名不一致

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1662-1665, 1694-1695`
- **证据片段**:
  ```java
  flowEntity.setEntryPointId(flow.getEntryPointSymbolId());   // 写入
  flow.setEntryPointSymbolId(entity.getEntryPointId());       // 读取
  ```
- **严重程度**: P3
- **现状**: ORM 字段 entryPointId vs 领域模型 entryPointSymbolId。文档已记录映射关系。
- **风险**: 低。已文档化且双向映射一致。但新开发者可能困惑。
- **建议**: 维持现状即可。文档已覆盖。
- **信心水平**: 高
- **误报排除**: 映射在文档中有记录。
- **复核状态**: 未复核

## 合规性检查

| 检查项 | 结果 |
|--------|------|
| 错误码前缀一致性 | 合规（全部 nop.err.code.*）|
| 实体名一致性 | 合规 |
| 字段命名规范 | 合规（DB snake_case, Java camelCase）|
