# 维度 05：生成管线完整性

## 适用范围

本模块**无完整** model→codegen→dao→meta→service→web 链路（无 codegen 子模块、无 _app.orm.xml 聚合）。唯一生成入口是 `precompile/gen-agent-xdsl.xgen`，仅生成 xdsl 模型类（`_gen`）。

## 第 1 轮（初审）发现

**零发现。** 核查结果：

1. **xgen 引用的 xdef 存在且正确**：`gen-agent-xdsl.xgen` 引用 `/nop/schema/ai/agent-plan.xdef` 与 `/nop/schema/ai/agent.xdef`，二者均实际存在于 `nop-kernel/nop-xdefs/.../nop/schema/ai/`。
2. **_gen 产物与 xdef 一致**：`agent.xdef:5 xdef:bean-package="io.nop.ai.agent.model"` → 生成 `model/_gen/_AgentModel.java`（类头 generate-from 标注正确）；`agent-plan.xdef:13 xdef:bean-package="io.nop.ai.agent.plan.model"` → 生成 `plan/model/_gen/_AgentPlanModel.java`，包路径对齐。
3. **pom exec-maven-plugin 触发**：pom.xml:56-60 声明 exec-maven-plugin（继承父 pom precompile 配置，与同级 nop-ai-core/toolkit 一致）。

> 注：维度10 发现了 plan 模型层的孤儿保留类问题（AgentPlanModel 等 5 个），那属于"模型重设计后的残留清理"，不属于生成管线本身正确性问题。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| — | — | — | 本维度零发现（管线基本 N/A，唯一存在的 xdsl 生成正确） |
