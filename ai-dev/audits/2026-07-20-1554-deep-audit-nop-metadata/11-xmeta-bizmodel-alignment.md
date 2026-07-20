# 维度 11：XMeta 与 BizModel 对齐 — 审计报告

> 初审结果（待复核）

## 审计结论：通过（轻微发现）

XMeta-BizModel 对齐良好。所有核心维度通过或 N/A。

## 发现条目

### [维度11-01] INopMetaLineageEdgeBiz 中 4 个 @BizQuery 方法缺少 IServiceContext context

- **文件**: 
  - `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMetaLineageEdgeBiz.java:37-49`
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java:346-468`
- **证据**:
  ```java
  @BizQuery
  List<String> getUpstream(@Name("metaTableId") String metaTableId);  // 缺少 context
  
  @BizQuery
  List<String> getDownstream(@Name("metaTableId") String metaTableId);  // 缺少 context
  
  @BizQuery
  List<String> getLineagePath(@Name("sourceTableId") String sourceTableId,
                               @Name("targetTableId") String targetTableId);  // 缺少 context
  
  @BizQuery
  List<String> getImpactAnalysis(@Name("metaTableId") String metaTableId,
                                  @Optional @Name("columnName") String columnName);  // 缺少 context
  ```
- **严重程度**: P3
- **现状**: 4 个 @BizQuery 方法缺少 `IServiceContext context` 作为最后一个参数，违反 `ICrudBiz` 契约规范。
- **风险**: 当前功能正确，但未来转换为 I*Biz 跨模块代理时，这些方法不可通过代理调用。当需要数据权限检查时，无法添加 context。
- **建议**: 向接口和实现的所有 4 个方法添加 `IServiceContext context` 作为最后一个参数。
- **信心水平**: 高

## 合规确认

| 检查项 | 结果 |
|--------|------|
| XMeta 字段覆盖 BizModel 方法 | ✅ 全部通过 |
| @BizLoader 有对应 XMeta prop | ✅ N/A（未使用 @BizLoader） |
| 字段类型兼容 | ✅ |
| 权限与语义匹配 | ✅（connectionConfig published=false 已正确应用） |
| dict 覆盖 | ✅ |
| displayName 本地化 | ✅ 中英文完整覆盖 |
| 死字段 | ✅ 无 |
| 未受控暴露 | ✅ |
