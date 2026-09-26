# deepwiki — nop-entropy 模块百科

由 `.opencode/skills/nop-deepwiki/` 生成与维护的各模块 DeepWiki 产出与对比分析。每个子目录是一个独立 wiki（含自己的 PLAN.md 结构契约与 meta/wiki-state.json 增量指纹）。

## 模块 wiki

| 目录 | 模块 | 形态判定 | 页面集 | 状态 |
|------|------|---------|--------|------|
| `nop-task/` | DSL 任务流编排引擎 | framework-repo + 运行时语义 | 10 页（受限集） | ✅ 全绿（2026-09-26） |
| `nop-batch/` | chunked 批处理框架 | framework-repo（chunk 管线） | 10 页（受限集） | ✅ 全绿（2026-09-26） |
| `nop-jq/` | jq 查询引擎 | consumer-library 变体 | 已按 v3 协议生成后随 plan 361 处置删除；可按需重生成 | — |

## 分析报告

- `analysis/2026-09-26-multitype-gap-analysis.md` — 多类型对比差距分析 v4（Java 四形态 payload 级量化 + COBOL 17 仓探测 + 迭代终止判定）

## 维护

- 全量重生成：按 SKILL.md MODE generate（受限页面集口径见各 PLAN.md）
- 增量更新：SKILL.md MODE update（基于 wiki-state.json 内容哈希指纹）
- 门禁：`node .opencode/skills/nop-deepwiki/scripts/check-wiki.mjs <子目录> --strict --verify-claims 20 --seed 42`
