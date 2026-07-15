# Metadata Platform Survey

> Status: resolved
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 为实现 nop-metadata 调研主流 metadata 平台的设计理念、架构和功能特性
> Conclusion: 五个平台的源码深度分析已完成，关键设计模式已提取到对比分析报告

## 调研范围

| 项目 | 类型 | 技术栈 | 深度分析 |
|------|------|--------|----------|
| DataHub | 数据目录平台 | Java/Python, Kafka, Elasticsearch | [分析](./2026-07-15-datahub-deep-analysis.md) |
| OpenMetadata | AI 上下文层 | Java, MySQL/PostgreSQL, Elasticsearch | [分析](./2026-07-15-openmetadata-deep-analysis.md) |
| Apache Atlas | Hadoop 治理框架 | Java, HBase, Solr | [分析](./2026-07-15-atlas-deep-analysis.md) |
| Amundsen | 数据发现引擎 | Python, Neo4j/Elasticsearch | [分析](./2026-07-15-amundsen-deep-analysis.md) |
| Marquez | 血缘追踪服务 | Java, PostgreSQL, Elasticsearch | [分析](./2026-07-15-marquez-deep-analysis.md) |
| **综合对比** | - | - | [对比](./2026-07-15-metadata-platforms-comparison.md) |

## 核心设计模式提取

### Tier 1: 必须借鉴

| 模式 | 来源 | 描述 |
|------|------|------|
| 声明式实体注册 | DataHub | YAML 驱动的实体/Aspect 定义，单一真实来源 |
| 模板方法 CRUD | OpenMetadata | EntityRepository 13K 行基类 + 子类钩子 |
| 边表关系存储 | OpenMetadata | 关系与属性分离，UUID 键永不过时 |
| 递归 CTE 血缘 | Marquez | 无需图数据库，PostgreSQL 递归查询 |
| Propose-Commit 事件 | DataHub | MCP → MCL 两阶段，支持验证和审计 |

### Tier 2: 建议借鉴

| 模式 | 来源 | 描述 |
|------|------|------|
| 注解驱动索引 | DataHub | PDL 注解 → ES 映射自动生成 |
| 向量搜索 | OpenMetadata | 多块嵌入 + 可插拔正文提取 |
| 组织记忆实体 | OpenMetadata | 记忆作为版本化实体 |
| 数据契约 ODCS | OpenMetadata | 开放标准合规 |
| DNF 授权 | DataHub | 析取范式策略 |

## 对 nop-metadata 的建议

1. **存储**: Nop ORM (JSON 文档 + 关系边表) + Elasticsearch (搜索 + 向量)
2. **模型**: XDef 声明式定义 + 模板方法 CRUD
3. **关系**: 独立边表，UUID 键，整数序号
4. **血缘**: 递归 CTE，支持列级血缘
5. **事件**: Propose-Commit 两阶段
6. **搜索**: 注解驱动索引 + 向量搜索

## 后续工作

- [x] 完成五个平台的深度分析
- [x] 提取可复用的设计模式
- [x] 制定 nop-metadata 设计建议
- [ ] 设计 nop-metadata 核心模型
- [ ] 制定实现计划