# nop-deep-analysis Plans Index

> 本目录是 mission `nop-deep-analysis` 的 plansDir。
> 引擎扫描本目录（递归）下状态为 `draft` / `active` 的 `.md` 计划。
> 本 `00-` 前缀文件会被引擎跳过，仅作索引说明。

## Roadmap

- `ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md`

## Plan 编写约定

- 计划文件命名：`A<n>-<short-slug>.md`（与 roadmap 工作项 A1–A7 对应）
- 每个计划产出一份分析文档到 `ai-dev/analysis/2026-07/`，命名 `2026-07-XX-<slug>.md`（遵循 `ai-dev/analysis/00-analysis-writing-guide.md`）
- 分析文档须含 Status / Date / Scope / Conclusion 元数据，并附联网调研来源链接
- 质量门：closure audit（结论经源码交叉核对），非 mvn 全量构建
