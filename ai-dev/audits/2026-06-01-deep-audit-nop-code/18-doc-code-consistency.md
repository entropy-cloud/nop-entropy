# 维度18：文档-代码一致性 -- nop-code 模块审计报告

## 第 1 轮（初审）

### [维度18-01] module-groups.md 缺少 nop-code-codegen 子模块描述

- **文件**: `docs-for-ai/01-repo-map/module-groups.md:20`
- **证据片段**:
  ```
  WIP 实验模块 | `nop-code/` | 多语言代码索引与智能分析服务。子模块：`nop-code-core`（通用模型+图数据结构）、`nop-code-graph`（图算法）...
  ```
  未列出 `nop-code-codegen` 子模块。
- **严重程度**: P3
- **现状**: 文档列出了 12 个子模块（core, graph, flow, lang-java, lang-python, lang-typescript, service, api, dao, meta, web, app），但遗漏了 `nop-code-codegen`。
- **风险**: 开发者/AI 可能不知道存在 codegen 子模块。
- **建议**: 补充 `nop-code-codegen`（代码生成入口）。
- **信心水平**: 95%
- **误报排除**: 已确认 nop-code-codegen 子模块实际存在且在根 pom.xml 中声明。
- **复核状态**: 未复核

## 无问题确认

- **module-groups.md 子模块描述与实际目录结构一致**（除遗漏 codegen）。
- **"已加入根 pom.xml modules"与实际一致**。
- **debugging-and-diagnostics.md 中启动路径正确**。
- **引用的文件路径全部有效**。
