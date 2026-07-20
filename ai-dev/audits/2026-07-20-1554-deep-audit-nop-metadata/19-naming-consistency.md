# 维度 19：命名与术语一致性 — 审计报告

> 初审结果（待复核）

## 发现条目

### [维度19-01] ErrorCode 前缀不一致（P2）

- **说明**: NopMetadataErrors.java 使用 `nop.err.metadata.*` 标准格式，但 100+ 个内联 ErrorCode 使用 `metadata.*`（缺少 nop.err. 前缀）。
- **严重程度**: P2

### [维度19-02] _NopMetadataCoreConstants vs NopMetadataConstants 常量重复/混淆（P2）

- **说明**: nop-metadata-core 有活跃的 `_NopMetadataCoreConstants`，nop-metadata-service 有空存根 `NopMetadataConstants.java`，nop-metadata-dao 有 `NopMetadataDaoConstants.java`。命名和位置不统一。
- **严重程度**: P2
