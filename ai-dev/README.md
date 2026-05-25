# ai-dev — nop-entropy 平台开发文档

> **受众**：开发 nop-entropy 平台本身的 AI 和开发者。如果你在使用 Nop 构建业务应用，请参考 `docs-for-ai/`。

## 定位

本目录包含 nop-entropy 平台开发过程中的设计、计划和记录。

| | `docs-for-ai/` | `ai-dev/`（本目录） |
|---|---|---|
| **面向** | 使用 Nop 构建业务应用 | 开发 Nop 平台本身 |
| **读者** | 业务应用开发者 / AI | 平台开发者 / AI |

**规范性 vs 过程记录**：`design/`（架构决策、使用契约）是规范性的 source of truth；`logs/`、`bugs/`、`plans/` 是开发过程记录。

## 目录结构

| 目录 | 用途 | 入口文件 |
|------|------|---------|
| `plans/` | 执行计划（含 status、exit criteria） | `00-plan-authoring-and-execution-guide.md` |
| `logs/` | 每日开发上下文、决策记录 | `00-log-writing-guide.md` |
| `design/` | 架构决策 + 使用契约 + 需求规格 | `00-design-writing-guide.md` |
| `analysis/` | AI 调研、对比、评估 | `00-analysis-writing-guide.md` |
| `discussions/` | 人与 AI 多轮对话，澄清模糊需求 | `00-discussion-writing-guide.md` |
| `bugs/` | 复杂 bug 的修复记录 | `00-bug-fix-note-writing-guide.md` |
| `audits/` | 代码和设计审计记录 | `README.md` |
| `lessons/` | 经验教训索引 | `README.md` |
| `skills/` | 可复用的审计/review prompt 模板 | — |

## 约定

- 所有 AI 开发计划必须写在 `plans/` 下，禁止写入 `docs/plans/`。
- 每个子目录的 `00-*-guide.md` 或 `README.md` 是该目录的入口和规范。
- 按时间组织的内容（logs、bugs）采用逆序排列（最新在前）。
