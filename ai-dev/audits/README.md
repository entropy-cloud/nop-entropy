# 审计记录

`ai-dev/audits/` 存放代码和设计文档的审计记录。审计使用 `ai-dev/skills/` 下的 prompt 模板驱动。

## 目录命名规范

```
ai-dev/audits/
├── YYYY-MM-DD-{type}-{module}/        # 深度审计（含多份报告）
│   ├── summary.md                     # 总体发现摘要
│   ├── {detail-report}.md             # 分项报告
│   └── ...
├── YYYY-MM-DD-{type}-{module}.md      # 独立审计记录
└── README.md
```

- `{type}`: `deep-audit` / `adversarial-review` / `plan-closure-audit` / `doc-age-audit`
- `{module}`: 被审计的模块名（如 `nop-stream`、`nop-job`）

## 与 skills 的关系

审计 prompt 模板定义在 `ai-dev/skills/`：

| Prompt | 审计类型 |
|--------|---------|
| `skills/deep-audit-prompts.md` | 多维度深度审计 |
| `skills/open-ended-adversarial-review-prompt.md` | 开放式对抗审查 |
| `skills/plan-closure-audit-prompt.md` | 计划结项审计 |
| `skills/plan-reviewer-prompt.md` | 计划实施前审计 |
| `skills/age-document-audit-prompt.md` | AGE 文档体系审计（吸引子可发现性、一致性、轨迹完整性、控制有效性、抗漂移能力） |

这些 prompt 是方法论模板，审计记录是执行结果。Prompt 不含业务内容，审计记录包含具体的发现和建议。

## 与 plans 的关系

审计发现如果需要修复，应创建对应的 plan（`ai-dev/plans/`），在 plan 中引用审计记录作为 baseline。

## 写作规则

1. 审计记录必须包含 `summary.md`（或独立文件的摘要段落）。
2. 每条发现标注严重程度（P0/P1/P2/P3）和建议修复方向。
3. 审计记录是证据层，不是规范性文档。规范修复应同步到 `docs-for-ai/` 或 `ai-dev/design/`。
