# 维度 10：XDSL/XLang 用法审查

## 通过检查

- 所有 `x:schema` 引用正确 ✓
- 所有 `x:extends` 用法正确 ✓
- 所有 beans.xml class 路径验证通过 ✓

## 发现

### [10-01] P2/P3 — NopJobTask.xmeta Delta 缺少系统管理字段的只读声明

- **文件**: NopJobTask.xmeta
- **现状**: NopJobTask.xmeta Delta 缺少以下系统管理字段的 `insertable=false` / `updatable=false` 声明：
  - `progress`
  - `progressMessage`
  - `targetHost`
  - `shardingIndex`
  - `shardingTotal`
  
  此外，NopJobFire.xmeta 缺少 `retryRecordId` 的只读声明。
- **影响**: 这些系统管理的字段通过 GraphQL mutation 仍然可以被外部 API 写入，可能导致数据不一致。
- **建议**: 在 xmeta Delta 中为这些字段添加 `insertable=false` / `updatable=false` 声明。
