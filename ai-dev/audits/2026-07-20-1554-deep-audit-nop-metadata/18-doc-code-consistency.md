# 维度 18：文档-代码一致性 — 审计报告

> 初审结果（待复核）

## 发现条目

### [维度18-01] docs-for-ai 声称 ErrorCode 已集中但大量仍内联（P2）

- **文件**: `docs-for-ai/03-modules/nop-metadata.md`
- **说明**: 文档简写句"ErrorCode 已集中到 NopMetadataErrors.java"具有误导性。实际 NopMetadataErrors.java 仅含 8 个 ErrorCode，20-30 个仍内联在 BizModel 中。
- **严重程度**: P2

### [维度18-02] ai-dev/design/nop-metadata/README.md 仍标记为"草稿"（P3）

- **说明**: 顶级设计文档 status=draft，日期 2026-07-15。代码已大规模实现，设计状态应更新。
- **严重程度**: P3
