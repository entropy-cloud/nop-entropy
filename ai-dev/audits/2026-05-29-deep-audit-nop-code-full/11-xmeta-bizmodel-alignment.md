# 维度11：XMeta 与 BizModel 对齐

## 第 1 轮（初审）

**结论**: xmeta 与 BizModel 对齐合理，无问题。

**检查范围**: nop-code-meta 下的 xmeta 文件和 nop-code-service 下的 BizModel 类。

**关键发现**:
- nop-code 的 BizModel 大量使用自定义 @BizQuery/@BizMutation 方法返回 DTO（如 SymbolDTO、CommunityDetectionResultDTO），而非通过 xmeta 的 prop 控制返回字段
- xmeta 主要用于：CRUD 元数据定义、自定义查询参数的 queryable 元数据、限制敏感字段（如 sourceCode published=false）
- 这是合理的设计——这些 API 返回的是分析结果 DTO 而非 ORM 实体字段

**复核状态**: 无问题
