# 维度 18：文档-代码一致性

## 审计范围

docs-for-ai/ 中所有涉及 nop-job 的文档。

## 第 1 轮（初审）发现

### [维度18-01] where-things-live.md 遗漏 nop-job-retry-adapter 子模块

- **文件**: `docs-for-ai/01-repo-map/where-things-live.md:65`
- **证据片段**: 列出 10 个子模块，遗漏 retry-adapter。
- **严重程度**: P3
- **现状**: 文档列出 nop-job 的 10 个子模块，实际有 11 个。遗漏了 nop-job-retry-adapter。
- **风险**: 开发者根据文档定位模块时遗漏此子模块。
- **建议**: 在列表中追加 `nop-job/nop-job-retry-adapter/`。
- **信心水平**: 高
- **误报排除**: 可通过实际目录结构验证。
- **复核状态**: 未复核

### [维度18-02] docs-for-ai 中缺少 RpcJobInvoker 和 retry-adapter 的文档描述

- **文件**: docs-for-ai/ (整体)
- **证据片段**: grep 搜索 "RpcJobInvoker" 和 "nop-job-retry-adapter" 返回 0 结果。
- **严重程度**: P2
- **现状**: 两个架构上重要的组件在文档中完全未被记录。
- **风险**: 开发者不了解 RPC 调用器的参数约定和重试桥接器的接入方式。
- **建议**: 在 architecture-principles.md 或新建 runbook 中补充。
- **信心水平**: 高
- **误报排除**: grep 搜索确认零结果。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 18-01 | P3 | where-things-live.md:65 | 遗漏 retry-adapter 子模块 |
| 18-02 | P2 | docs-for-ai/ | 缺少 RpcJobInvoker/retry-adapter 文档 |
