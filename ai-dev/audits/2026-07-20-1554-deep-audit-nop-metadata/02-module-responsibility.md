# 维度 02：模块职责与文件边界 — 审计报告

> 初审结果（待复核）

## 发现条目

### [维度02-01] MetaAggregationExecutor.java 达 3468 行，内部含 18 个内部类

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaAggregationExecutor.java`
- **证据**: 一个类承担了 7 种聚合路径的完整实现，内含 18 个内部类（`MemAggAccumulator` + 5 个子类, `CrossDbFieldResolver` 等）。
- **严重程度**: P2
- **现状**: 单一文件违反"一个文件一个主要职责"原则。虽 18 个内部类做了逻辑分组，但 3468 行对可维护性构成实质障碍。
- **风险**: 难以导航、容易引入回归、新开发人员学习成本高。
- **建议**: 将 `MemAggAccumulator` 及其子类、跨库相关类、JOIN 聚合解析类分别抽取到独立文件。
- **信心水平**: 高

### [维度02-02] MetaJoinExecutor.java 达 1012 行

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaJoinExecutor.java`
- **证据**: 处理同库 JOIN 和跨库拼接，约 40 个私有方法。
- **严重程度**: P2
- **现状**: 超过 1000 行，职责边界尚可但已达到拆分阈值。
- **建议**: 将跨库拼接逻辑抽取到 `CrossDbJoinMerger.java`。
- **信心水平**: 中

### 合规确认

| 检查项 | 结果 |
|--------|------|
| 生成目录混入手写代码 | ✅ 无问题 |
| _ 前缀文件被手改 | ✅ 无问题 |
| 模块边界越权 | ✅ 无问题 |
