# 维度 18：文档-代码一致性

## 第 1 轮（初审）

### [维度18-01] component-roadmap.md 引用不存在的类名 ExecutionPlan

- **文件**: `ai-dev/design/nop-stream/component-roadmap.md:28,140`
- **严重程度**: P2
- **现状**: 文档描述"Transformation → StreamGraph → JobGraph → ExecutionPlan"，但实际类名是 GraphExecutionPlan。
- **建议**: 统一修改为 GraphExecutionPlan。

### [维度18-02] architecture.md 五层管线包含不存在的 RuntimeTopology 层

- **文件**: `ai-dev/design/nop-stream/architecture.md:16,130-148`
- **严重程度**: P2
- **现状**: 文档声明五层管线含 RuntimeTopology，但代码中不存在此类。实际是四层实现。
- **建议**: 标注 RuntimeTopology 为"规划中"或调整为四层描述。

### [维度18-03] docs-for-ai/ 缺少 nop-stream 核心开发指南

- **文件**: `docs-for-ai/` 目录
- **严重程度**: P2
- **现状**: docs-for-ai/ 几乎没有 nop-stream 指南。INDEX.md 无路由条目，module-groups.md 未列出 nop-stream。
- **建议**: 在 module-groups.md 增加 nop-stream 分组条目。

### [维度18-04] nop-stream-cep README 过于简陋

- **文件**: `nop-stream-cep/README.md`
- **严重程度**: P3
- **现状**: 仅一行"从flink-cep项目剥离代码"，未反映当前完整功能。

### [维度18-05] nop-stream-api POM 注释与 architecture.md 依赖方向描述冲突

- **文件**: `nop-stream-api/pom.xml` vs `architecture.md`
- **严重程度**: P2
- **现状**: architecture.md 描述 api 模块为独立层，但实际 api 为空壳（接口在 core 中）。
