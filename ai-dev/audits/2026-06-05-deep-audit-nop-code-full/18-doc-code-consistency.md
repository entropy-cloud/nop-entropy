# 维度 18：文档-代码一致性 — nop-code 模块

## 第 1 轮（初审）

### [维度18-01] nop-code 模块在 docs-for-ai/ 中无特定模块文档

- **文件**: `docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md`
- **严重程度**: P2
- **现状**: nop-code 在平台文档中完全未记录，无 runbook、指南或源锚点。
- **建议**: 添加 nop-code 架构概述：模块层、关键接口、增量索引管线。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度18-02] ICodeIndexService Javadoc 使用中文

- **文件**: `ICodeIndexService.java:12-27`
- **严重程度**: P3
- **建议**: 翻译为双语或英文 Javadoc。
- **复核状态**: 未复核

### [维度18-03] nop-code-api 模块为空 — 与 domain-module-pattern.md 约定矛盾

- **文件**: `nop-code-api/`（空目录）
- **严重程度**: P2（与维度 01-02 重复）
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 18-01 | P2 | docs-for-ai/ | nop-code 无模块文档 |
| 18-02 | P3 | ICodeIndexService.java | Javadoc 中文 |
| 18-03 | P2 | nop-code-api/ | api 模块为空违背约定 |
