# Nop AI Agent 收敛路线与实施阶段

## 1. 目标

本篇定义 `nop-ai-agent` 的实施顺序和设计收敛原则，避免项目在第一阶段就扩展成一个过大的系统。

核心问题不是“还可以设计多少能力”，而是“当前阶段什么是必须稳定的”。

## 2. 当前判断

`nop-ai-agent` 目前处于典型的“设计空间很大，但实现基础还不稳”的阶段。

这类阶段最容易出现两个问题：

1. 一次性规划过多高级能力
2. 设计文档过早进入大量实现细节

因此路线图的作用，是把系统收敛到几个清晰阶段，而不是继续横向扩张设计面。

## 3. 收敛原则

### 3.1 先主链路，后增强

先稳定：

- `agent.xdef` 的字段语义
- `agent-plan.xdef` 的字段语义
- `tool.xdef` / `tool-call.xdef` / `call-tools-response.xdef` 的字段语义
- `call-agent.tool.xml` 的现有契约

再做：

- runtime 对 DSL 的解释
- 会话、权限和容错策略
- 多 Agent 编排和平台级增强

### 3.2 先单 Agent，后多 Agent

如果单 Agent runtime 还没有稳定，多 Agent 编排只会放大问题。

### 3.3 先确定性能力，后智能决策能力

优先程序化的部分：

- 参数验证
- 错误分类
- 超时
- 安全限制

再引入 Advisor Agent 决策：

- retry advisor
- compression advisor
- repair advisor

### 3.4 先稳定边界，后细化实现

一篇设计文档更应该固定：

- 对象边界
- 生命周期
- 输入输出契约

而不是先固定大量实现细节。

## 4. Phase 1: 核心闭环

### 4.1 目标

让单 Agent 可以稳定执行一次完整的 `ReAct` 循环。

### 4.2 交付物

- `agent.xdef` 的语义定稿
- `agent-plan.xdef` 的语义定稿
- `tool.xdef` / `tool-call.xdef` / `call-tools.xdef` / `call-tools-response.xdef` 的语义定稿
- `call-agent.tool.xml` 的语义定稿
- runtime 对上述 DSL 的最小解释闭环

### 4.3 验收标准

- 可以仅根据 DSL 文档写出合法的 `.agent.xml`、`plan.xml`、工具调用 XML
- 可以明确区分哪些字段是现有 DSL，哪些只是 runtime 解释
- 可以把一轮 Agent 执行描述为“DSL -> runtime 解释 -> 工具/结果回灌”的闭环
- 不再需要依赖旧总稿理解当前 DSL

### 4.4 不纳入本阶段

- 大量 Java 接口草图
- 未来字段的 DSL 预埋
- 完整多 Agent 编排
- 完整可靠性平台

## 5. Phase 2: 可插拔增强

### 5.1 目标

让 Agent runtime 可以承载更丰富但仍然局部的增强能力。

### 5.2 交付物

- Hook、Skill、Plan/Todo 的运行时语义补充
- `permissions` 与外部权限配置的优先级约定
- Session/快照/压缩回写的存储语义
- `call-agent` 的使用模式说明，但不发明 schema 中不存在的新字段

### 5.3 验收标准

- 能清楚说明 Skill、Hook、Plan、Todo 如何附着在现有 DSL 之上
- 能清楚说明 `permissions`、session、压缩这些非 xdef 结构的 runtime 约束
- 不把运行时假设误写成新的 DSL 字段

## 6. Phase 3: 可靠性增强

### 6.1 目标

让 Agent 在真实场景下更稳定、更可控。

### 6.2 交付物

- 错误分类语义
- 超时预算语义
- 压缩保真规则
- 工具验证和安全限制语义
- 循环检测与回退策略的设计约定

### 6.3 验收标准

- LLM 调用故障可以区分是否自动重试
- 工具调用能在执行前被验证和拦截
- 长对话能触发压缩并继续运行
- 运行时间和工具超时可控

## 7. Phase 4: 平台级能力

### 7.1 目标

支持更复杂的生产级场景和长任务执行。

### 7.2 交付物

- 断路器和模型回退的设计定稿
- 检查点和会话恢复的设计定稿
- 多 Agent 编排与 Nop Flow / Task 的对齐方案

### 7.3 验收标准

- provider 连续故障后系统可自动降级
- 长任务中断后可以恢复
- 多 Agent 任务可以通过 Flow / Task 组织

## 8. 当前最值得固定的设计决策

建议明确固定以下决策，不再反复摇摆：

1. 以现有 `xdef` 和 `.tool.xml` 作为设计入口
2. 文档先定义 DSL 语义，再定义 runtime 解释
3. `call-agent` 以真实 `call-agent.tool.xml` 为准，不在文档里额外发明字段
4. Hook 基于 `agent.xdef` 的事件模式
5. Plan 与 Todo 独立
6. 多 Agent 编排后置

## 9. 当前最值得延期的设计决策

下面这些先不要写成当前 DSL 或当前主体架构：

- `call-agent` 的未来字段扩展
- `async/detached` 的完整行为语义
- 通用 AgentSession 消息队列
- AI repair branch 的分支合并模型
- 多 Agent 图执行的统一抽象

这些内容都依赖实现经验，应在第一轮 runtime 落地后再定。

## 10. 文档维护建议

后续设计文档建议遵守下面规则：

1. 总览文档只讲边界、对象和核心决策
2. 专题文档只讲一个主题
3. 研究过程和框架对照不要混进主设计文档
4. 伪代码只保留最小必要片段
5. 路线图只保留阶段目标、交付物和延期项

## 11. 结论

`nop-ai-agent` 当前最需要的不是再增加新的实现对象，而是稳定一条 DSL-first 的设计顺序：

1. 先定现有 DSL 的语义
2. 再定 runtime 如何解释这些 DSL
3. 再补会话、安全、可靠性等策略层文档
4. 最后才讨论未来 schema 扩展和多 Agent 编排

只要这条顺序不乱，设计文档就不会再次滑回 Java-first。
