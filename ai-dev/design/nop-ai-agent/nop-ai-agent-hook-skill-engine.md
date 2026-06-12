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

**排序语义**：同一 Hook 点上的多个 Hook 按 `priority` 升序执行（数值小的先执行）。无 `priority` 声明时默认 `0`。相同 `priority` 按注册序执行。

**超时策略**：每个 Hook 执行有 `hookTimeoutSeconds` 限制（默认 30s），超时后记录 `HookTimeout` 警告并继续（不中断 ReAct 循环）。`before_*` 和 `after_*` Hook 超时不阻断执行；`on_error` Hook 超时使用 fallback 默认错误处理。

**失败传播**：`before_*` Hook 失败 → 阻止当前操作（返回 Hook veto）；`after_*` Hook 失败 → 记录但不影响已完成操作；`on_error` Hook 失败 → 使用引擎默认错误处理。

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

## 5a. Hook 类型扩展（MiMoCode 吸收）

除了 §4 定义的 before/after/on_error 事件 Hook，新增两个**允许 ReAct 重入**的 Hook 点：

| Hook 点 | 触发时机 | 允许重入 | 典型用途 |
|---------|---------|---------|---------|
| `before_tool_result_processed` | 工具执行完成、结果写入消息历史之前 | ✅ 可返回 `ReenterResult` | 工具结果修复、结果拦截重试 |
| `after_tool_result_processed` | 工具结果已写入消息历史、下一轮推理之前 | ✅ 可返回 `ReenterResult` | 结果评审、触发子任务 |

**重入语义**：
- Hook 返回 `ReenterResult{message}` → 引擎注入该消息，跳过当前轮剩余步骤，进入下一轮 ReAct
- Hook 返回 `PassResult` → 继续正常流程
- Hook 超时 → 默认 Pass
- 重入次数限制：单个 Hook 点连续重入 N 次（默认 3 次）→ 强制 Pass，防止死循环

**设计理由**：MiMoCode 的 PreStop/PostStop ReAct 重入钩子被证明能有效处理工具结果异常（JSON 截断修复、结果拦截重试）和后处理（结果评审、知识提取）。Nop 现有 Hook 只有事件通知，没有"改变执行流"的能力。两个新 Hook 点给引擎增加了"执行流修正"能力，而不需要拆解 ReAct 循环本身。

## 6. 内部 Agent 化

### 6.1 概念

部分引擎内部能力可以用 Agent 来实现——接口的提供者是引擎，接口的实现者可以是硬编码逻辑，也可以是一个 Agent。

详见 `nop-ai-agent-context-model.md` §6。

### 6.2 与 Hook/Skill 的关系

内部 Agent 化的能力可以通过 Hook 挂接到引擎生命周期上：

- 上下文压缩 → 挂接在 `POST_REASONING` 或独立触发
- 错误修复 → 挂接在 `ON_ERROR`
- 结果评审 → 挂接在 `POST_CALL`

Hook 负责触发时机，内部 Agent 负责具体逻辑。

### 6.3 阶段归属

Phase 1：所有内部能力用硬编码逻辑实现。
Phase 2：逐步将硬编码逻辑替换为 Agent 实现，接口不变。

## 7. 本篇结论

Hook 和 Skill 都属于 Java 引擎扩展层。
