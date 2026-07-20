# 维度 17：代码风格与规范 — 审计报告

> 初审结果（待复核）

## 发现条目

### [维度17-01] 多个文件缺少标准版权头（P2）

- **文件**: `NopMetaDataSourceBizModel.java`, `NopMetaTableBizModel.java`, `CheckpointActionDispatcher.java` 等
- **说明**: 旧文件缺少 Nop 平台标准版权头，新文件（如 NopMetadataErrors.java）包含版权头，存在不一致。
- **严重程度**: P2
