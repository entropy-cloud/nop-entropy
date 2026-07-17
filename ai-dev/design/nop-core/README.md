# nop-core Design

## Structure

| Document | Layer | Purpose |
|----------|-------|---------|
| `00-record-support-design.md` | Architecture Baseline | Java record 类型在 ReflectionManager 中的支持方案 |
| `01-union-schema-validation.md` | Architecture Baseline | union schema 运行时校验与 subtype 路由契约 |
| `02-table-validator-design.md` | Data Quality | 表格数据集的三层验证模型（row/stat/table） |

## Reading Order

1. `00-record-support-design.md` — record 支持设计（当前唯一文档）
2. `01-union-schema-validation.md` — union schema 运行时校验设计
3. `02-table-validator-design.md` — 表格数据集三层验证模型

## Convention

本目录按 AGE (Attractor-Guided Engineering) owner-doc 模式组织。
