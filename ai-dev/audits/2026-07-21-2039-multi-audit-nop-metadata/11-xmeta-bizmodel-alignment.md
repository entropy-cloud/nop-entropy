# 维度11：XMeta 与 BizModel 对齐 — 第1轮（初审）

> 审计模块: nop-metadata

## 发现清单

### [维度11-01] NopMetaSearchBizModel 缺少必需的 xmeta（伪 BizModel 违规）

- **文件**: `NopMetaSearchBizModel.java:28`，对应的 `NopMetaSearch.xmeta` 不存在
- **严重程度**: P0
- **现状**: `NopMetaSearchBizModel` 声明了 `@BizModel("NopMetaSearch")` 并公开了两个 API 方法，但对应的 xmeta 不存在。40 个 `@BizModel` 中唯此无 xmeta。
- **风险**: 前端 AMIS 页面通过 `/graphql` 调用将失败。只有直接 `/r/` REST 调用能工作。
- **建议**: 创建 `NopMetaSearch.xmeta` 文件，定义 GraphQL 类型。
- **信心水平**: 确定
- **误报排除**: service-layer.md 第208-214行明确禁止此模式。
- **复核状态**: 未复核

### [维度11-02] DTO 返回方法在 NopMetaTable.xmeta 中无 schema 关联

- **文件**: `NopMetaTableBizModel.java:262-279` 等，`_NopMetaTable.xmeta`
- **严重程度**: P2
- **现状**: 7 个自定义方法返回 `@DataBean` DTO，但 NopMetaTable GraphQL 类型无法对 DTO 字段做 selection 控制。
- **建议**: 为高频 DTO 返回方法在 xmeta 中补充 schema 引用。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度11-03] 全模块零 @BizLoader 使用

- **证据**: `grep -r "BizLoader" nop-metadata` 返回 0 结果
- **严重程度**: P3
- **现状**: 计算/派生数据全部以独立 `@BizQuery` action 暴露，未使用平台关联字段懒加载能力。
- **建议**: 将 `resolveTableFields`、`getUpstream`/`getDownstream` 等迁移为 `@BizLoader`。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度11-04] 7 个 CrudBizModel 子类无 I*Biz 接口

- **文件**: `NopMetaQualityCheckpointBizModel.java:65`, `NopMetaTableDimensionBizModel.java:43`, `NopMetaReconciliationConfigBizModel.java:60`, `NopMetaReconciliationResultBizModel.java:47`, `NopMetaQualityScoreBizModel.java:38`, `NopMetaTableFilterBizModel.java:44`, `NopMetaModelChangedEventBizModel.java:26`
- **严重程度**: P2
- **现状**: 至少 7 个 BizModel 没有实现对应的 I*Biz 接口，无法通过 `BizProxyFactoryBean` 跨模块注入。
- **建议**: 为所有 CrudBizModel 子类创建对应的 I*Biz 接口。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度11-05] connectionConfig 的 sortable 属性未覆盖

- **文件**: `NopMetaDataSource.xmeta:14`
- **严重程度**: P1
- **现状**: retention 层设 `published="false"`, `queryable="false"` 但未覆盖 `sortable`，继承生成层的 `sortable="true"`。
- **风险**: `queryable="false"` 与 `sortable="true"` 并存，存在排序侧信道泄露的极低风险。
- **建议**: 补上 `sortable="false"`。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度11-06] dict _label 字段 i18n 被显式设为 null

- **文件**: `_nop-metadata.i18n.yaml:449-475`
- **严重程度**: P3
- **现状**: `transformType_label`, `lineageSource_label` 等被设为 `null`，英文 UI 中列头显示中文。
- **建议**: 删除或改为对应英文显示名。
- **信心水平**: 很可能
- **复核状态**: 未复核

## 总结

| # | 严重程度 | 描述 |
|---|---------|------|
| 11-01 | P0 | NopMetaSearch 无 xmeta |
| 11-05 | P1 | connectionConfig sortable 未覆盖 |
| 11-02 | P2 | DTO 返回无 schema 关联 |
| 11-04 | P2 | 7 个 BizModel 无 I*Biz 接口 |
| 11-03 | P3 | 零 @BizLoader |
| 11-06 | P3 | _label i18n 设为 null |
