# 维度16：测试覆盖与质量 — 第1轮（初审）

> 审计模块: nop-metadata

## 发现清单

### [维度16-01] 约 19/40 个 BizModel 类缺少任何测试覆盖

- **文件**: `NopMetaCatalogBizModel.java`, `NopMetaEntityBizModel.java`, `NopMetaEntityFieldBizModel.java`, `NopMetaEntityRelationBizModel.java`, `NopMetaEntityUniqueKeyBizModel.java`, `NopMetaGlossaryBizModel.java`, `NopMetaGlossaryTermBizModel.java`, `NopMetaManifestBizModel.java`, `NopMetaOrmModelBizModel.java`, `NopMetaPipelineBizModel.java`, `NopMetaProfilingResultBizModel.java`, `NopMetaReconciliationEntityBizModel.java`, `NopMetaReconciliationResultBizModel.java`, `NopMetaSemanticTypeBizModel.java`, `NopMetaTableDimensionBizModel.java`, `NopMetaTableFilterBizModel.java`, `NopMetaTableMeasureBizModel.java`, `NopMetaDomainBizModel.java`, `NopMetaClassificationBizModel.java`
- **严重程度**: P1
- **现状**: 约 40% 的 BizModel 类（19/40）零测试覆盖，包括关键实体（EntityField、Classification、Domain、ReconciliationEntity）。
- **风险**: 高价值业务流程节点上的未经测试方法在运行时可能静默失败。
- **建议**: 按优先级添加集成测试：TableMeasure → EntityField → Entity → Reconciliation → Classification。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度16-02] 搜索测试使用 Mockito mock 替代真实搜索引擎

- **文件**: `TestNopMetadataSearchIntegration.java:33`, `TestNopMetaSearchService.java:15`, `TestNopMetaIndexBuilder.java`
- **严重程度**: P2
- **现状**: 搜索测试验证 mock 交互（verify），而非真实搜索行为。没有集成测试通过 IGraphQLEngine 调用 NopMetaSearchBizModel。
- **风险**: 搜索引擎 SPI 兼容性、字段映射、查询 DSL 差异不会被捕获。
- **建议**: 添加使用 Lucene/内存搜索引擎的真实集成测试。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度16-03] 聚合测试直接注入 BizModel 而非通过 IGraphQLEngine

- **文件**: `TestAggregationCategoricalAndTemporal.java:71`, `TestAggregationExternalJoinAndPagination.java`, `TestAggregationEntityJoinAndComplex.java`
- **严重程度**: P1
- **现状**: 三个聚合测试文件（~1830 行）直接调用 `nopMetaTableBizModel.queryAggregation(...)` 而非通过 `IGraphQLEngine.newRpcContext()` + `executeRpc()`。
- **风险**: 违反 testing.md 第25-48行明确规则。绕过权限层、ORM 会话管理。回归测试无法检测权限绕过。
- **建议**: 迁移到使用 `IGraphQLEngine.executeRpc`。
- **信心水平**: 确定
- **误报排除**: testing.md 第25-48行明确列为禁止模式。
- **复核状态**: 未复核

### [维度16-04] 核心 BizModel 方法未经端到端测试

- **文件**: `NopMetaModuleBizModel.java:444`, `NopMetaDataContractBizModel.java:103`
- **严重程度**: P2
- **现状**: `releaseModule`, `generateManifest`, `activateContract`, `deprecateContract`, `retireContract` 均无测试。
- **风险**: 模块发布状态转换、清单生成、合同生命周期状态机回归难以检测。
- **建议**: 为这些方法添加集成测试。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度16-05] 聚合引擎 JOIN 处理器测试极其单薄

- **文件**: `TestEntityAggregationProcessor.java:28`, `TestEntityEntityJoinAggregationProcessor.java:28`, `TestExternalAggregationProcessor.java:45`, `TestExternalExternalJoinAggregationProcessor.java:49`, `TestMixedSameDbJoinAggregationProcessor.java:40`
- **严重程度**: P2
- **现状**: 五个处理器测试每个短于 50 行，只测试空路径/平凡值，零错误路径。
- **风险**: 这些测试实质上提供零保护，是虚假的安全网。
- **建议**: 添加实际 JOIN 查询、类型不匹配、空结果集、分页边界的测试，或删除空壳测试。
- **信心水平**: 确定
- **复核状态**: 未复核

## 总结

| # | 严重程度 | 描述 |
|---|---------|------|
| 16-01 | P1 | 19/40 BizModel 零测试 |
| 16-03 | P1 | 聚合测试绕过 IGraphQLEngine |
| 16-02 | P2 | 搜索测试只 mock |
| 16-04 | P2 | 核心方法无端到端测试 |
| 16-05 | P2 | JOIN 处理器测试是空壳 |
