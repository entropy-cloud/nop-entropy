# Nop AI Hook And Skill Engine

## 1. 目标

本篇定义 Hook 与 Skill 在 Java 引擎层中的组织方式。

## 2. Hook 引擎

Hook 引擎负责：

- 加载 `agent.xdef` 中的 Hook 配置
- 按规则筛选、排序和触发 Hook
- 处理 before/after/on_error 的失败传播语义

推荐对象：

- `HookRegistry`
- `HookMatcher`
- `HookInvoker`
- `HookExecutionPolicy`

## 3. Skill 引擎

Skill 引擎负责：

- 发现可用 Skill
- 判断当前请求哪些 Skill 生效
- 汇总 instruction、tools、hooks

推荐对象：

- `ISkillProvider`
- `SkillResolver`
- `SkillActivationPolicy`
- `SkillAssemblyResult`

## 4. Hook 与 Skill 的关系

- Skill 负责注入能力集合
- Hook 负责挂接生命周期点

很多 Skill 最终会通过 Hook 生效，但二者仍应分层实现。

## 5. 与 ReAct 引擎的关系

ReAct 引擎负责调用：

- Hook 引擎
- Skill 装配结果

但 ReAct 引擎本身不负责：

- 发现 Skill
- 解析 Skill 激活条件
- 管理 Hook 优先级规则

## 6. 本篇结论

Hook 和 Skill 都属于 Java 引擎扩展层。
