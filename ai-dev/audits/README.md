# 审计记录

`ai-dev/audits/` 存放代码和设计文档的审计记录。审计使用 `ai-dev/skills/` 下的 prompt 模板驱动。

## 目录命名规范

按日期归档到 `{year}-{month}/` 子目录：

```
ai-dev/audits/
├── YYYY-MM/                             # 每月的审计都放在这里
│   ├── YYYY-MM-DD-HHMM-{type}-{module}/   # 深度审计（含多份报告）
│   │   ├── summary.md                      # 总体发现摘要
│   │   ├── {detail-report}.md              # 分项报告
│   │   └── ...
│   └── YYYY-MM-DD-HHMM-{type}-{module}.md  # 独立审计记录
├── README.md
```

- `{type}`: `deep-audit` / `adversarial-review` / `plan-closure-audit` / `doc-age-audit`
- `{module}`: 被审计的模块名（如 `nop-stream`、`nop-job`）
- `HHMM`: 24 小时制时分（如 `0930`、`1430`），用于区分同一天内的多次执行。历史记录可能只有 `YYYY-MM-DD`（无时分），新记录一律带 `HHMM`。

## 证据产物目录（evidence/）

`ai-dev/audits/evidence/{plan-id}/` 按 plan/milestone 归档修复证据产物（语义对比脚本与输出、baseline 快照、白名单等），与 audit 记录分离——audit 记录描述"发现了什么"，evidence 记录"修复如何被验证"。约定：

- `baseline/`：拆分/重构前的原始文件快照（BASE_REF），首次变更提交前捕获并提交，commit SHA 写入各证据文件头部
- `compare.sh` + `compare.py` + `whitelist.json`：可复现的语义对比工具链（compare.sh 从自身目录执行，`cd "$DIR"` 后以相对路径引用源码）
- `*-semantic-diff.txt`：对比输出（RESULT: 0-diff = 语义等价），提交入库
- 现有证据：`ma4-2-05/`（引擎三文件拆分，plan 2026-08-01-0441-1）

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
