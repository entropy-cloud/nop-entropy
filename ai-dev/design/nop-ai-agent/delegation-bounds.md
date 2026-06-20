# Delegation Bounds

> Status: active design doc
> Source: plan 278 (AR-05), `ai-dev/plans/278-nop-ai-agent-engine-resource-lifecycle-recovery-delegation-bounds.md`

## Decision

`call-agent` 委派链有可配置的深度上限，防止自引用 agent 和 A↔B 互引导致栈溢出。深度经 `AgentMessageRequest.metadata` 的专用 key 传递（非经 `AgentToolExecuteContext` 跨 agent 边界——后者在每次 ReAct 迭代从零重建）。

## MAX_DELEGATION_DEPTH

- 默认值：4。使用 `>=` 检查，允许最多 3 层 LLM-initiated 委派（depth 1/2/3）。
- 可配置：`CallAgentExecutor.setMaxDelegationDepth(int)`（必须 >= 1）。
- Team-flow 委派（`SpawnMemberAgentTaskStep` / `MemberAgentTaskStep`）不经 `CallAgentExecutor`——它们有独立的 DAG 环检测，此限制不影响 team-flow 链。

## 传播通道

深度经 `AgentMessageRequest.metadata` 中的专用 key `__nopAiAgent.delegationDepth`（Integer）传递：

1. `CallAgentExecutor.doExecuteAsync`：从 `AgentToolExecuteContext.getDelegationDepth()` 读父深度，childDepth = parent + 1；检查 `childDepth >= max` 则拒绝。
2. `buildPropagationMetadata(constraint, childDepth)`：始终返回含 depth 的 metadata map（即使无 constraint 也含 depth），经 sync（`engine.execute`）和 async（`CallAgentRequestPayload`）两条路径传播。
3. `DefaultAgentEngine.doExecute`：经 `extractDelegationDepth(request)` 从 metadata 提取 depth，设入 `AgentExecutionContext.setDelegationDepth()`。
4. `ReActAgentExecutor`：构建 `AgentToolExecuteContext` 后调 `setDelegationDepth(ctx.getDelegationDepth())`，供子 `CallAgentExecutor` 回读。

不使用 `AgentToolExecuteContext` 作为跨 agent 传播通道——它在每次 ReAct 迭代从零重建，且 engine 边界构建的是 `AgentExecutionContext`。

## 拒绝行为

超深时返回 `AiToolCallResult.errorResult`（含 "delegation depth limit reached" 描述），不静默返回 null/空、不抛 StackOverflowError。拒绝发生在 `engine.execute` 之前——不创建子 session/actor/lock（无孤儿资源）。
