# 维度 18：文档-代码一致性

## 第 1 轮（初审）

### [维度18-01] docs-for-ai 将占位模块描述为已实现功能

- **文件**: `docs-for-ai/01-repo-map/module-groups.md:21`
- **严重程度**: P2
- **现状**: 文档将 nop-stream-checkpoint/flow/flink/api 描述为功能完整的子模块，但实际为空占位符。
- **建议**: 标注模块状态（活跃/规划中），与 README 保持一致。
- **信心水平**: 确定
- **误报排除**: 已验证 src/ 目录为空。
- **复核状态**: 未复核

### [维度18-02] source-anchors.md 无 nop-stream 相关锚点

- **文件**: `docs-for-ai/04-reference/source-anchors.md` (90行全文)
- **严重程度**: P3
- **现状**: 无任何 nop-stream 相关锚点。
- **建议**: 添加 STREAM-001~003 锚点。
- **信心水平**: 确定
- **误报排除**: 已全文搜索确认无 stream 关键字。
- **复核状态**: 未复核

### [维度18-03] 异常体系文档准确但缺少子类

- **文件**: `docs-for-ai/02-core-guides/error-handling.md:124-127`
- **严重程度**: P3
- **现状**: 文档描述与代码一致，但未提及 CheckpointStorageException 和 MalformedPatternException。
- **建议**: 补充子类说明。
- **信心水平**: 确定
- **误报排除**: 子类是子领域概念。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 18-01 | P2 | module-groups.md | 占位模块描述为已实现 |
| 18-02 | P3 | source-anchors.md | 无 nop-stream 锚点 |
| 18-03 | P3 | error-handling.md | 缺少异常子类说明 |
