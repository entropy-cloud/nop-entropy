# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] SymbolDTO 等公开 DTO 放置在 service 模块内而非 api 模块

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/dto/SymbolDTO.java` 等
- **严重程度**: P2
- **现状**: SymbolDTO、FileAnalysisDTO、AnnotationUsageDTO 位于 nop-code-service 模块，但被公开 BizModel 方法返回。外部消费者必须依赖 nop-code-service 而非轻量的 nop-code-api。
- **风险**: 增加模块间耦合。
- **建议**: 将公开 API 返回的 DTO 移至 nop-code-api。
- **信心水平**: 高
- **误报排除**: 这些 DTO 被 GraphQL API 使用，属于跨模块公共契约。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度20-01] | P2 | nop-code-service/api/dto/ | 公开 DTO 放在 service 而非 api 模块 |
