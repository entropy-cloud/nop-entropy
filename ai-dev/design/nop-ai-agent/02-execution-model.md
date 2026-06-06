# Nop AI Agent 执行模型

**日期**：2026-06-06
**范围**：Agent Engine Layer 的执行循环和生命周期
**状态**：active

---

## 一、设计结论

1. 采用双循环模型：外层 followUp 循环 + 内层 ReAct 循环，循环粒度为完整消息
2. Hook 统一事件模型，10 个生命周期点，优先级排序
3. Steering 机制填补外部注入消息的空白，是引擎层机制，当前不需要 DSL 支持

## 二、双循环模型

**决策**：采用 pi-agent 风格的双循环，简化为只处理完整消息。

### 外层循环（followUp 循环）

- Agent 完成一次完整执行后，检查是否有排队的后续消息
- 如果有，注入后续消息，启动新的内层循环
- 如果没有，Agent 执行结束

### 内层循环（steering + ReAct 循环）

- 标准 ReAct 循环：LLM 调用 → 解析输出 → 工具执行 → 结果回灌
- 每轮工具执行后检查 steering 队列
- 循环粒度是**完整消息**——每次 LLM 调用返回完整的 assistant message，不是逐 token 处理

**"完整消息"的定义**：一次 LLM 调用返回的完整 assistant message，可能包含文本内容和/或一组工具调用。引擎在收到完整响应后才进入下一阶段（工具执行或结束判定），不在 LLM 流式输出过程中做决策。

**理由**：

- 无人值守场景不需要逐 token 交互
- 完整消息简化了引擎的状态机——不需要管理流式中间状态
- 流式输出仍然可以通过事件发布（TextChunk 事件），但不影响引擎决策

**拒绝了**：逐 token 的流式决策模式。理由是它增加了引擎复杂度（需要管理部分解析状态），而无人值守场景不需要它。

## 三、ReAct 主循环的行为语义

ReAct 主循环的期望行为：

1. 引擎接收配置和请求后，初始化会话、装配 Hook/Skill/Tool、构建 system prompt
2. 进入循环：构建 LLM 请求 → 调用 LLM → 解析输出
3. 如果 LLM 输出包含工具调用 → 执行工具 → 结果写回消息 → 回到第 2 步
4. 如果 LLM 输出不包含工具调用 → 跳出内层循环
5. 循环中任何时刻，如果达到约束上限（maxIterations、token 预算、外部取消、不可恢复错误）→ 中止循环
6. 内层循环中止后，检查 steering 和 followUp 队列
7. 最终发布结果或错误

其中，Hook 在以下语义点被触发：
- Agent 执行开始前和结束后
- 每次 LLM 调用前后
- 每次工具执行前后
- 错误发生时

本节的执行流程与 `nop-ai-agent-react-engine.md` 一致。后者是 ReAct 引擎的详细设计文档。

## 四、Steering 机制

**期望行为**：在 ReAct 循环中，每轮工具执行后检查是否有外部注入的 steering 消息。如果有，注入消息、跳过当前剩余工具、进入下一轮推理。

**决策理由**：现有 Hook 机制是自动化的（按生命周期点触发），缺少人工/外部注入消息的能力。Steering 填补这个空白。

**拒绝了什么**：
- 通过 Hook 注入 steering 消息：Hook 的语义是"增强当前事件"，不是"注入新消息流"。职责不同。
- 通过修改消息历史注入：绕过引擎主循环，难以保证一致性。

**边界条件**：
- Steering 是引擎层机制，当前不需要 DSL 支持（不需要在 `agent.xdef` 中新增元素）
- Steering 消息的来源是外部调用者（API 层），不是 Hook
- 同一轮中，steering 优先于剩余工具执行
- Steering 不影响 Hook 的正常触发

**后续考虑**：如果 steering 证明有价值，未来可以在 `agent.xdef` 的 `constraints` 中增加 steering 相关配置。但这属于 Phase 2+ 范畴。

## 五、Hook 生命周期设计

### 5.1 完整生命周期点

| 生命周期点 | 触发时机 | 可修改内容 |
|-----------|---------|-----------|
| `PRE_CALL` | Agent 开始执行前 | 请求参数、工具列表 |
| `POST_CALL` | Agent 执行完成后 | 最终结果 |
| `PRE_REASONING` | 发起 LLM 调用前 | 输入消息、chatOptions |
| `POST_REASONING` | LLM 响应后 | LLM 输出消息 |
| `REASONING_CHUNK` | LLM 流式输出中间块 | 流式块内容 |
| `PRE_ACTING` | 工具执行前 | 工具调用参数（可 block） |
| `POST_ACTING` | 单个工具执行后 | 工具结果（可修改） |
| `PRE_SUMMARY` | 达到 maxIterations 后，生成摘要前 | 无 |
| `POST_SUMMARY` | 摘要生成后 | 摘要内容 |
| `ON_ERROR` | 发生错误时 | 错误处理策略 |

### 5.2 Hook 设计原则

1. **统一事件分发**：所有 Hook 接收相同的生命周期事件类型，引擎决定事件路由
2. **可修改性由事件本身决定**：部分事件允许修改执行数据，部分只读
3. **优先级排序**：数值越小优先级越高，同优先级按注册顺序
4. **失败传播**：Hook 执行失败时，引擎根据错误类型决定继续还是中止

### 5.3 与 DSL 的关系

`agent.xdef` 中的 `<hooks>` 声明 Hook 配置，引擎在运行时加载并排序。

## 六、执行控制

Agent 执行有**循环控制**和**计算资源控制**：

- **循环控制**：maxIterations 限制 ReAct 循环次数，token 预算限制总消耗，超时限制执行时长。达到上限时 Agent 中止并返回部分结果
- **计算资源控制**：CPU 时间、内存使用、工具调用次数的配额。防止失控 Agent 消耗过多资源
- **followUp 循环同样受这些控制约束**：不会因为持续注入 followUp 消息导致 Agent 永不退出

## 七、错误处理

### 错误分类

| 错误类型 | 处理策略 | 示例 |
|---------|---------|------|
| LLM 调用失败 | 自动重试（可配置次数） | 网络超时、rate limit |
| LLM 返回无效格式 | 重试 + 修正 prompt | XML 解析失败 |
| 工具执行失败 | 记录错误，继续或中止（可配置） | 文件不存在 |
| 工具超时 | 中止该工具，返回超时错误 | 长时间运行的工具 |
| Agent 预算耗尽 | 中止执行，返回部分结果 | token 超限 |
| 外部取消 | 优雅停止，保存当前状态 | 用户取消 |

### 重试策略

参考 solon-ai 的 `ToolRetryInterceptor`：
- 工具执行失败时，根据错误类型决定是否重试
- 重试次数和间隔可配置
- 重试策略通过 Hook/Interceptor 实现，不在引擎主循环中硬编码

详细设计见 `nop-ai-agent-reliability.md`。

---

## 与其他文档的关系

- `01-architecture-baseline.md` — 本篇依托的架构基线
- `04-tool-invocation.md` — 工具调用架构（本篇 ReAct 循环中的工具执行环节）
- `nop-ai-agent-react-engine.md` — ReAct 引擎详细设计（本篇 §3 的展开）
- `nop-ai-agent-hook-skill-engine.md` — Hook/Skill 引擎层详细设计
- `nop-ai-agent-reliability.md` — 可靠性增强层详细设计
