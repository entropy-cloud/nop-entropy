# 维度18：文档-代码一致性

## 第 1 轮（初审）

### [维度18-01] docs-for-ai 将 nop-code-api 描述为"外部 RPC 接口"但实际为空壳

- **文件**: `docs-for-ai/01-repo-map/module-groups.md`
- **行号**: L20
- **证据片段**:
  ```
  | WIP 实验模块 | `nop-code/` | ... `nop-code-api`（外部 RPC 接口）... |
  ```
- **严重程度**: P2
- **现状**: 文档将 nop-code-api 描述为"外部 RPC 接口"，但实际无任何 Java 文件。ICodeIndexService 在 nop-code-service 中。
- **风险**: 开发者误解 API 契约位置。
- **建议**: 修正文档描述，注明 api 当前为空壳。
- **信心水平**: 确定
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度18-02] source-anchors.md 无 nop-code 相关锚点

- **文件**: `docs-for-ai/04-reference/source-anchors.md`
- **行号**: 全文件 90 行
- **严重程度**: P3
- **现状**: 无 nop-code 相关条目。
- **建议**: 添加核心锚点（ICodeIndexService、CodeIndexService、ProjectAnalyzer 等）。
- **信心水平**: 很可能
- **误报排除**: WIP 模块可能尚不需要锚点。
- **复核状态**: 未复核

### [维度18-03] 无 nop-code 专属设计文档

- **文件**: `ai-dev/design/` 目录
- **严重程度**: P3
- **现状**: 无 nop-code 相关设计文档。模块涉及多语言解析、图算法、执行流分析等复杂子系统。
- **建议**: 补充 ai-dev/design/nop-code-architecture.md。
- **信心水平**: 很可能
- **误报排除**: WIP 模块设计尚在演进中。
- **复核状态**: 未复核
