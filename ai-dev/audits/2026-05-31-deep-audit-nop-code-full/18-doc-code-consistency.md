# 审核维度 18：文档-代码一致性

## 第 1 轮（初审）

### [维度18-01] docs-for-ai/ 中无 nop-code 专属文档

- **严重程度**: P2
- **现状**: docs-for-ai/ 中对 nop-code 的提及仅限于 module-groups.md 中一行描述和几个示例引用。无 API 参考、BizModel 定制指南、语言适配器开发指南。
- **建议**: 新建 nop-code 使用指南文档。
- **复核状态**: 未复核

### [维度18-02] 设计文档状态过时

- **文件**: `ai-dev/design/nop-code/language-agnostic-code-index-design.md:286-296`
- **严重程度**: P3
- **现状**: 6.1 节标注 nop-code-graph 为"不存在⏳规划中"，但实际已有 10+ 个类完整实现。同样 lang-python/lang-typescript 标注为"骨架"但已有完整分析器。
- **建议**: 更新状态为实际完成状态。
- **复核状态**: 未复核
