# nop-metadata 设计文档

> Status: draft
> Date: 2026-07-15
> Scope: nop-metadata 模块设计

## 目录结构

| 文档 | 层级 | 说明 |
|------|------|------|
| `00-vision.md` | Vision | 定位、范围、成功标准、non-goals |
| `01-architecture-baseline.md` | Architecture Baseline | 核心对象、模块边界、数据流 |
| `02-gap-analysis.md` | Gap Analysis | 对比五个平台，识别设计缺失和可完善点 |
| `03-version-management.md` | Version Management | 模块级版本管理、Maven 对齐 |
| `04-data-governance.md` | Data Governance | 域定义、字典、数据契约、血缘、质量 |
| `05-metadata-import.md` | Metadata Import | 元数据采集、导入、Manifest、Catalog |
| `06-data-quality-extended.md` | Data Quality Extended | 数据质量扩展、数据剖析、验证执行 |
| `07-ai-integration.md` | AI Integration | AI 集成、GraphQL 自动暴露 |
| `08-reconciliation.md` | Reconciliation | 实体对账、外部知识库集成 |

## 核心设计决策

1. **版本管理粒度**: 模块级别，对齐 Maven 打包/发布
2. **接口暴露方式**: GraphQL 自动暴露，AI 通过 schema 自动学习
3. **数据质量**: 参考 Great Expectations 的 Expectation Suite 模式
4. **元数据导入**: 参考 dbt 的 Manifest 模式
5. **AI 集成**: 通过 GraphQL schema 自动发现功能，AI 自动生成查询
6. **实体对账**: 参考 OpenRefine Reconciliation，支持外部知识库匹配

## 阅读顺序

1. 先读 `00-vision.md` — 确认这是不是你想要的
2. 再读 `01-architecture-baseline.md` — 理解分层和核心契约
3. 然后读 `02-gap-analysis.md` — 对比外部平台，识别需补充的内容
4. 按需阅读其他设计文档

## 状态

- 草案阶段，尚未进入实现
- 触发实现前需先更新 `ai-dev/plans/`
