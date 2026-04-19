# AI 开发日志

`ai-dev/logs/` 用于记录每天的短期开发上下文、关键决策和后续动作。

它不是 source of truth。当前项目的唯一规范性开发文档仍是 `docs-for-ai/`。

## 结构

```text
ai-dev/logs/
├── index.md
├── 2026/
│   └── 04-09.md
└── 2027/
```

**路径约定**：`ai-dev/logs/{year}/{month}-{day}.md`

## 写作规则

1. 一天一个文件。
2. 新条目追加在文件顶部，按倒序记录。
3. 保持简短，优先链接 `docs-for-ai/`、`ai-dev/plans/` 或相关代码路径。
4. 日志只记录短期上下文、关键决策和下一步，不复制 canonical doc 内容。
5. 如果变更已经改变默认规则，先更新 `docs-for-ai/`，再写日志。

## 条目模板

```markdown
# AI 开发日志 — YYYY-MM-DD

### YYYY-MM-DD

- 做了什么。
- 相关路径：`docs-for-ai/...`、`ai-dev/plans/...`、`module/...`
- 关键决策：...
- 下一步：...
```

## 索引（倒序）

### 2026-04

- [04-14](2026/04-14.md) — 仅在 `nop-ai-agent/docs/` 内拆分 Agent 设计草稿，保留原大文档不动，收敛为总览 / 运行时 / 扩展 / 可靠性 / 路线图五篇专题文档
- [04-10](2026/04-10.md) — 复核 `docs-for-ai` 审查意见，修正路由缺口、DTO 规则、CRUD hook / QueryBean 指南，并补充 XLang/XPL 基础文档
- [04-09](2026/04-09.md) — `docs-for-ai` 重构收口、自动维护规则、`ai-dev/logs` 机制建立
