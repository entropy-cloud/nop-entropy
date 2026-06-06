# 维度 12：GraphQL 与 API 层

## 审计日期
2026-06-06

## 第 1 轮（初审）

### [维度12-01] 手动实体到 DTO 转换绕过 GraphQL FieldSelection

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:365-380`
- **证据片段**:
  ```java
  CodeSymbol getSymbolById(String indexId, String symbolId) {
      NopCodeSymbol entity = symbolDao.getEntityById(symbolId);
      return entity != null ? CodeSymbolConverter.toCodeSymbol(entity) : null;
  }
  ```
  CodeSymbolConverter.toCodeSymbol() 总是转换所有字段，不尊重 GraphQL FieldSelection。
- **严重程度**: P2
- **现状**: 即使客户端只请求 name 和 kind，仍然加载并转换全部 25+ 字段。
- **风险**: 大结果集性能浪费。
- **建议**: 考虑在 BizModel 列表/分页方法中添加 FieldSelectionBean 感知，或接受为内部分析工具的合理权衡。
- **信心水平**: 可能
- **误报排除**: 代码分析工具通常需要完整数据。
- **复核状态**: 未复核

### 合规项
- @BizQuery/@BizMutation 到 GraphQL 映射正确
- 分页使用 PageBean<T>（手动 offset/limit）
- 无硬编码 SQL/HQL
