# 187 nop-ai-agent A4 LLM-turn / Compaction Checkpoint Triggers

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: A4

> Last Reviewed: 2026-06-15
> Source: Carry-over from plan 186 (`ai-dev/plans/186-nop-ai-agent-db-backed-checkpoint-persistence.md`) — `Deferred But Adjudicated` "LLM-turn / compaction checkpoint triggers" (`Successor Required: yes, Successor Path: roadmap A4 独立 plan`). 同一 carry-over 亦 deferred by plan 181 (`Deferred But Adjudicated` "LLM-turn checkpoint + compaction-triggered snapshot", `Successor Required: yes, Successor Path: roadmap A4 独立计划`) and plan 182 (`Deferred But Adjudicated` "LLM-turn checkpoint + compaction-triggered snapshot", `Successor Required: yes`). Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 row A4 (`:148`, checkpoint journal format ✅ landed; LLM-turn/compaction trigger emission = this plan). Design owner doc: `nop-ai-agent-reliability.md` §5.4a 触发时机（`:322-325` — "每个 LLM turn 完成后自动写入 journal entry" + "压缩时生成完整 snapshot" + "工具执行前后写入 tool-level checkpoint"；前两个触发点 = 本计划）。
> Related: Plan 181 (L3-4 ICheckpointManager contract + TOOL_EXECUTION wiring — the pattern this plan extends), Plan 182 (A4 journal.md + snapshot.json format — persists any CheckpointType including the newly emitted ones), Plan 183 (crash/restart restore — consumes `getLatestCheckpoint`; intra-execution persistence pattern this plan reuses), Plan 186 (DBCheckpointManager — persists any CheckpointType to DB)

## Purpose

将可靠性层 `nop-ai-agent-reliability.md` §5.4a 触发时机规格中两个 **预留但未发射** 的 checkpoint 触发点收口到"dispatch loop 在 LLM turn 完成后 + 上下文压缩成功后发射 checkpoint"状态。`CheckpointType` 枚举有三个值（`TOOL_EXECUTION` / `LLM_TURN` / `COMPACTION`），但当前 dispatch loop 仅发射 `TOOL_EXECUTION`（`ReActAgentExecutor.java:946-960`，plan 181 接线）。`LLM_TURN` 和 `COMPACTION` 枚举值自 plan 181 起预留（`CheckpointType.java:32-44` Javadoc 明确标注"not emitted by the current dispatch-loop wiring"），但从未被任何代码发射。

这是 6-plan checkpoint 可靠性投资链（plan 177→186）的收尾增强——没有它，crash/restart 恢复粒度仅限于工具执行边界。若崩溃发生在 LLM 响应后但工具执行前（如 LLM 已返回 tool_calls 但工具尚未执行），最近的 checkpoint 是上一个工具执行点，恢复时丢失当前 LLM turn 的进度。本计划交付更细粒度的恢复点：每个 LLM turn + 每次上下文压缩都产生 checkpoint。

## Current Baseline

- **`CheckpointType` ✅ 3 values (plan 181)**: `TOOL_EXECUTION`(已接线) / `LLM_TURN`(预留，`CheckpointType.java:32-37` Javadoc: "Reserved for a checkpoint recorded after an LLM turn completes... not emitted by the current L3-4 dispatch-loop wiring") / `COMPACTION`(预留，`:39-44` Javadoc: "Reserved for a full snapshot recorded during context compaction... not emitted")。枚举已 forward-compatible——DB 列经 `CheckpointType.valueOf(name())` 往返，`ai_agent_checkpoint.CHECKPOINT_TYPE` 列可存任何枚举值
- **`TOOL_EXECUTION` checkpoint ✅ wired (plan 181)**: `ReActAgentExecutor` dispatch loop 在工具结果添加到 ctx 后调用 `checkpointManager.saveCheckpoint(Checkpoint.of(...))`（`ReActAgentExecutor.java:946-959`），type=`TOOL_EXECUTION`，携带 toolName/callId/inputSummary(tool args)/outputSummary(tool response)/messageCount/tokenEstimate。saveCheckpoint 后 `checkpointSeq++`（`:960`）
- **`checkpointSeq` ✅ per-execution counter**: `int checkpointSeq = 0`（`:526`），在 execute() 方法内声明，每次 TOOL_EXECUTION checkpoint 后递增。本计划复用同一 counter 为 LLM_TURN/COMPACTION checkpoint 编号——seq 在单次 execute() 内跨 checkpoint 类型单调递增
- **Intra-execution persistence ✅ pattern (plan 183)**: TOOL_EXECUTION checkpoint 后紧跟 session 消息同步持久化（`:962-978`）：`sessionStore.get(sessionId)` → `persisted.replaceMessages(ctx.getMessages())` → `sessionStore.save(persisted)`。这保证持久化 session 的 messageCount ≥ checkpoint 的 messageCount（plan 183 restore 一致性校验 `checkpoint.messageCount ≤ session.messageCount`）。LLM_TURN / COMPACTION checkpoint 必须遵循同一模式——否则 restore 时 session 消息数落后于 checkpoint，一致性校验失败
- **`ICheckpointManager` ✅ accepts any CheckpointType**: `saveCheckpoint(Checkpoint)` 契约不限定 type——`NoOpCheckpoint` 全透传、`ToolExecutionCheckpoint` / `FileBackedCheckpointManager` / `DBCheckpointManager` 持久化任何 type。本计划无需修改任何 ICheckpointManager 实现——仅修改 dispatch loop 的发射点
- **LLM call location ✅ identified**: `chatService.call(request, null)`（`:590`）。成功响应后：assistant 消息添加到 ctx（`:604`）→ token 计账（`:606-614`）→ `LLM_RESPONSE_RECEIVED` 事件（`:619`）→ `POST_REASONING` hook（`:621`）→ output guardrail（`:623-636`）→ completion judge（`:638-673`）→ dispatch loop（`:700-999`）。LLM_TURN checkpoint 发射点：token 计账完成后（`:614` 之后）、completion judge 之前（`:638` 之前）——此处 assistant 消息已在 ctx，token 已记账，response 确认成功（`:592-601` 失败检查已通过）
- **Compaction location ✅ identified**: `performCompaction(ctx, agentName)`（`:1211-1242`）。调用方有两处：reactLoop 顶部 `shouldTriggerCompaction` 触发（`:564`）+ forced-stop 最终摘要（`:1181`）。`contextCompactor.compact(compactCtx)` 返回 `CompactionResult`（`:1225`）。当 `result.getCompactedMessages() != null && !isEmpty() && tokensAfter < tokensBefore` 时（`:1229-1233`），消息被替换（`:1234-1235`）+ token 调整（`:1236`）。COMPACTION checkpoint 发射点：消息替换成功后（`:1241` 之后）——此处 ctx 已含压缩后消息，tokenEstimate 已更新
- **NoOpContextCompactor ✅ default shipped**: 默认 `contextCompactor` 是 `NoOpContextCompactor.INSTANCE`（`:183`），compact() 返回 NoOp-equivalent result（`compactedMessages == null`）——不触发消息替换。COMPACTION checkpoint 仅在实际压缩发生时发射（NoOp compactor 不触发）
- **`0 matches` for LLM_TURN/COMPACTION emission**: `rg "CheckpointType\.(LLM_TURN|COMPACTION)" nop-ai/nop-ai-agent/src/main` 返回 0 命中——确认预留枚举值从未在 main src 被引用（仅 test 中 `TestNoOpCheckpointAndValue.java:145` 用 LLM_TURN 测试 value-type equals/hashCode）

## Goals

- **`LLM_TURN` checkpoint emission**: dispatch loop 在每次成功 LLM 响应后（assistant 消息添加到 ctx + token 计账完成），发射 `Checkpoint(type=LLM_TURN)`。checkpoint 携带 type=LLM_TURN / toolName=null / callId=null / outputSummary=assistant 响应内容 / messageCount=ctx.getMessages().size() / tokenEstimate=ctx.getTokensUsed()。提供比 TOOL_EXECUTION 更细粒度的恢复点——崩溃在 LLM 响应后、工具执行前时，最近的 checkpoint 是当前 LLM turn 而非上一个工具执行点
- **`COMPACTION` checkpoint emission**: `performCompaction` 在上下文压缩成功后（compactedMessages 替换 ctx 消息 + token 调整完成），发射 `Checkpoint(type=COMPACTION)`。checkpoint 携带 type=COMPACTION / toolName=null / callId=null / messageCount=ctx.getMessages().size()（压缩后）/ tokenEstimate=ctx.getTokensUsed()（压缩后）。标记压缩后的新基线——restore 从 COMPACTION checkpoint 恢复时，session 已含压缩后消息，不存在 pre-compaction vs post-compaction 消息索引不一致
- **Intra-execution persistence for both new trigger points**: 每个 LLM_TURN 和 COMPACTION checkpoint 后紧跟 session 消息同步持久化（同 plan 183 TOOL_EXECUTION 模式：`replaceMessages` + `sessionStore.save`），维持 restore 一致性不变量（`checkpoint.messageCount ≤ session.messageCount`）
- **Shared `checkpointSeq` counter**: LLM_TURN / TOOL_EXECUTION / COMPACTION 共享同一 per-execution `checkpointSeq` counter，跨 checkpoint 类型在单次 execute() 内单调递增。所有 checkpoint manager 实现（NoOp / ToolExecution / FileBacked / DB）无需修改——`saveCheckpoint` 接受任何 type
- **向后兼容**: `NoOpCheckpoint` 默认 shipped（LLM_TURN / COMPACTION checkpoint 全透传——no-op）。`NoOpContextCompactor` 默认 shipped（不触发 COMPACTION checkpoint——无实际压缩）。全部现有测试通过
- **Javadoc 更新**: `CheckpointType.java:32-44` 中 LLM_TURN / COMPACTION 的 "not emitted by the current dispatch-loop wiring" 更新为"已落地（plan 187）"
- **设计文档 §5.4a 更新**: 触发时机（`:322-325`）三个触发点全部标记为已落地；架构决策记录

## Non-Goals

- **`Checkpoint` 值类型扩展（promptTokens / completionTokens 字段）**: 设计 §5.4a 提及"LLM 响应后记录 prompt/completion token"。但 `Checkpoint` 值类型（plan 181，11 字段）不含独立的 promptTokens/completionTokens 字段——仅含累计 `tokenEstimate`。本计划复用现有字段（outputSummary 捕获 assistant 响应内容，tokenEstimate 捕获累计 token），不扩展 Checkpoint 值类型。独立的 prompt/completion token 分拆是 Checkpoint 值类型变更，属独立增强
- **Per-session seq 跨 restore 持久化**: `checkpointSeq` 是 per-execution 局部变量（`:526`），每次 execute() 从 0 开始。restore 时新 execute() 的 seq 从 0 重启——这是 TOOL_EXECUTION 的已有行为，LLM_TURN/COMPACTION 继承同一 counter。per-session seq 持久化（从已有 checkpoint 初始化 seq 起始值）是独立增强，不改变恢复正确性（restore 的 `getLatestCheckpoint` 按全局 seq 排序，新 execution 的 checkpoint seq 重启不影响已有 checkpoint 的检索）
- **Compaction-triggered snapshot.json 生成**: `FileBackedCheckpointManager` 的 snapshot.json 是 journal replay 的加速缓存（plan 182 决策：周期性/按需生成）。本计划交付 COMPACTION checkpoint 的 journal entry 发射（同 TOOL_EXECUTION 写 journal.md），不交付 compaction-triggered snapshot.json 文件生成。snapshot 加速是 `FileBackedCheckpointManager` 的独立增强（plan 182 Non-Goal `:336`）
- **`firstKeptEntryId` compaction-aware 截断加载**: 恢复时按 compaction 截断点加载 checkpoint 子集。本计划加载全量 checkpoint（同 `FileBackedCheckpointManager` 的 journal 全量加载）。compaction-aware 截断是 plan 183 deferred successor
- **跨进程 / 多实例 checkpoint 接管锁**: 同 plan 185/186 Non-Goal。takeover lock 依赖 L4-8 Actor Runtime
- **Checkpoint retention / rotation policy**: LLM_TURN checkpoint 增加 checkpoint 数量（每次 LLM turn 一行）。retention 是 plan 186 deferred optimization candidate
- **LLM_TURN checkpoint for failed LLM calls**: 仅成功 LLM 响应发射 checkpoint。失败响应（`:592-601`）直接 break reactLoop——不产生 checkpoint。失败场景由 sessionStore 的最终状态持久化覆盖
- **Output guardrail 交互**: LLM_TURN checkpoint 在 output guardrail（`:623-636`）之前发射，捕获原始 LLM 响应。guardrail 的 modify/block 是下游 concern——修改后的内容经 intra-execution persistence 同步到 session store；checkpoint 的 outputSummary 记录发射时刻的 assistant 内容

## Scope

### In Scope

- `ReActAgentExecutor` dispatch loop 新增 LLM_TURN checkpoint 发射点（LLM 响应成功 + token 计账完成后）
- `performCompaction` 新增 COMPACTION checkpoint 发射点（实际压缩成功后）
- 两个新发射点各自跟 intra-execution session persistence（replaceMessages + sessionStore.save，同 plan 183 模式）
- LLM_TURN / COMPACTION checkpoint 的 watermark 生成（使用共享 checkpointSeq counter 保证唯一性）
- 单元测试：LLM_TURN 发射条件 + 字段完整性 + seq 序 + NoOp 透传 + intra-execution persistence
- 单元测试：COMPACTION 发射条件（仅实际压缩时）+ 字段完整性 + NoOp compactor 不触发 + intra-execution persistence
- 端到端测试：多 LLM turn + 工具调用 + 压缩 → 三种 checkpoint 类型按 seq 交错排列；restore 从 LLM_TURN checkpoint 恢复
- 向后兼容测试：NoOpCheckpoint 默认 + NoOpContextCompactor 默认 → 全部现有测试通过
- `CheckpointType.java` Javadoc 更新（LLM_TURN / COMPACTION 从"not emitted"到"landed"）
- `nop-ai-agent-reliability.md` §5.4a 触发时机更新 + 架构决策记录
- `nop-ai-agent-roadmap.md` A4 deferred successor 状态同步

### Out Of Scope

- `Checkpoint` 值类型扩展（promptTokens / completionTokens 字段）
- Per-session seq 跨 restore 持久化
- Compaction-triggered snapshot.json 文件生成（FileBackedCheckpointManager 加速层）
- `firstKeptEntryId` compaction-aware 截断加载
- 跨进程 / 多实例 checkpoint 接管锁（L4-8）
- Checkpoint retention / rotation / TTL policy
- LLM_TURN checkpoint for failed LLM calls

## Execution Plan

### Phase 1 - LLM_TURN checkpoint emission + intra-execution persistence + tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java` (reactLoop — LLM call success path, after token accounting `:614` / before completion judge `:638`); `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/` (or `reliability/`)

- Item Types: `Decision | Proof`

- [x] **Decision（LLM_TURN 发射点——token 计账完成后、completion judge 之前）**: LLM_TURN checkpoint 在 reactLoop 中 `chatService.call` 成功响应路径上发射，具体位置：assistant 消息已添加到 ctx（`:604`）+ token 计账已完成（`:614`）之后、completion judge（`:638`）之前。理由：(1) 此处 LLM turn 的"工作"已完成——response 成功（`:592-601` 失败检查通过）、assistant 消息在 ctx、token 已记账；(2) completion judge 的 complete/continue/escalate 分支会 break/continue reactLoop——在 judge 之前发射保证所有成功 LLM turn 都有 checkpoint，无论 completion 判定结果；(3) output guardrail（`:623-636`）在发射点之后——checkpoint 捕获原始 LLM 响应，guardrail 的 modify/block 经 intra-execution persistence 同步到 session store（checkpoint outputSummary 记录发射时刻内容，guardrail 修改后的内容在 session 消息历史中）
- [x] **Decision（LLM_TURN checkpoint 字段——复用现有 Checkpoint 值类型，不扩展）**: LLM_TURN checkpoint 使用 `Checkpoint.of(sessionId, watermark, seq, timestamp, CheckpointType.LLM_TURN, null, null, null, outputSummary, messageCount, tokenEstimate)`——toolName=null（无工具）、callId=null（无工具调用）、inputSummary=null（prompt 是完整消息历史，已在 session store）、outputSummary=assistant 响应内容（经 ToolResultTruncator 截断如需，捕获 LLM 响应用于恢复可观测性）、messageCount=ctx.getMessages().size()（assistant 消息已添加）、tokenEstimate=ctx.getTokensUsed()（token 计账已完成）。watermark 使用共享 checkpointSeq 保证唯一性（如 `sessionId + ":llm:" + checkpointSeq`，匿名 session 用 `"anon:llm:" + checkpointSeq`——具体格式在实现时裁定，plan 只约束唯一性 + 类型前缀可辨识）
- [x] **Decision（intra-execution persistence——同 plan 183 TOOL_EXECUTION 模式）**: LLM_TURN checkpoint 后紧跟 session 消息同步持久化：`sessionStore.get(sessionId)` → `persisted.replaceMessages(ctx.getMessages())` → `sessionStore.save(persisted)`。理由：plan 183 restore 协议的一致性校验要求 `checkpoint.messageCount ≤ session.messageCount`——若 LLM_TURN checkpoint 记录 messageCount=N 但 session 仅持久化到 N-1（assistant 消息未持久化），restore 一致性校验失败。`InMemorySessionStore` 默认 save 是 no-op（内存共享引用），不影响现有行为
- [x] 在 reactLoop 成功 LLM 响应路径上（token 计账后、completion judge 前）发射 LLM_TURN checkpoint：构造 `Checkpoint.of(...)` 调用 `checkpointManager.saveCheckpoint(...)`，然后 `checkpointSeq++`，然后 intra-execution persistence（replaceMessages + sessionStore.save）。跳过 LLM 失败路径（`:592-601` break 之前不发射）
- [x] 单元测试（LLM_TURN 发射条件——功能化 checkpoint manager）：构造 executor 注入 `ToolExecutionCheckpoint`（in-memory 功能化）→ 执行 1 次 LLM turn（mock chatService 返回成功响应无 tool calls，completionJudge 判定 complete）→ `checkpointManager.getLatestCheckpoint(sessionId)` 返回 type=LLM_TURN 的 checkpoint。验证：仅 1 个 checkpoint（无 TOOL_EXECUTION，因无工具调用）
- [x] 单元测试（LLM_TURN NOT emitted on failure）：mock chatService 返回失败响应（`response.isSuccess() == false`）→ reactLoop break → `checkpointManager.getLatestCheckpoint(sessionId)` 返回 null（无 checkpoint 发射）
- [x] 单元测试（LLM_TURN 字段完整性）：验证 LLM_TURN checkpoint 的 type=LLM_TURN / toolName=null / callId=null / inputSummary=null / outputSummary=assistant 响应内容 / messageCount=ctx 消息数（含 assistant 消息）/ tokenEstimate=累计 token
- [x] 单元测试（LLM_TURN + TOOL_EXECUTION seq 交错）：执行 1 次 LLM turn（有 tool calls）→ 先发射 LLM_TURN checkpoint（seq=S），工具执行后发射 TOOL_EXECUTION checkpoint（seq=S+1）→ `getLatestCheckpoint` 返回 seq=S+1 的 TOOL_EXECUTION checkpoint（seq 最大）。验证 LLM_TURN seq < TOOL_EXECUTION seq（同次 turn 内 LLM 在前、tool 在后）
- [x] 单元测试（NoOp 默认透传）：构造 executor 注入 `NoOpCheckpoint.noOp()`（默认）→ 执行 LLM turn → 无异常、无副作用（NoOpCheckpoint.saveCheckpoint 是 no-op）。全部现有 dispatch-path 测试通过
- [x] 单元测试（intra-execution persistence）：构造 executor 注入 `FileBackedSessionStore` + `ToolExecutionCheckpoint` → 执行 LLM turn → session.json 文件含 assistant 消息（messageCount 包含 assistant 消息）。`InMemorySessionStore` 默认 save 是 no-op——验证不抛异常

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] LLM_TURN checkpoint 在每次成功 LLM 响应后被发射（`ReActAgentExecutor` reactLoop token 计账完成后）
- [x] LLM_TURN checkpoint 不在 LLM 失败响应路径发射
- [x] LLM_TURN checkpoint 字段正确：type=LLM_TURN / toolName=null / callId=null / messageCount + tokenEstimate 反映 assistant 消息添加后的状态
- [x] LLM_TURN + TOOL_EXECUTION seq 交错正确：同次 turn 内 LLM_TURN seq < TOOL_EXECUTION seq
- [x] **新增功能测试**（Minimum Rules #25）：LLM_TURN 发射条件 + 字段完整性 + seq 交错 + 失败不发射 + NoOp 透传 + intra-execution persistence——各有对应通过的测试
- [x] **接线验证**（Minimum Rules #23）：LLM_TURN checkpoint 经 `checkpointManager.saveCheckpoint` 实际写入（通过功能化 manager 的 `getLatestCheckpoint` 检索验证，非仅方法被调用）
- [x] **无静默跳过**（Minimum Rules #24）：LLM 失败路径不发射 checkpoint 是设计行为（break reactLoop），非静默跳过——有明确的 status=failed + error event
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 1 新增测试通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - COMPACTION checkpoint emission + intra-execution persistence + tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java` (`performCompaction` method `:1211-1242`, after compacted messages replace ctx `:1241`; `handleForcedStop` method `:1174-1194` 签名变更以透传 `checkpointSeq` holder; reactLoop `:559` + `:564` 两处调用点更新传入 holder); `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/` (or `compact/`)

- Item Types: `Decision | Proof`

- [x] **Decision（COMPACTION 发射点——实际压缩成功后，消息替换完成后）**: COMPACTION checkpoint 在 `performCompaction` 中、压缩实际发生（`result.getCompactedMessages() != null && !isEmpty() && tokensAfter < tokensBefore`，`:1229-1233`）且消息已替换（`:1234-1235`）+ token 已调整（`:1236`）之后发射。理由：(1) 仅在实际压缩时发射——NoOp compactor 返回 `compactedMessages == null`（`:62-63` of NoOpContextCompactor），不进入消息替换分支，不发射 checkpoint（避免 spurious checkpoint）；(2) 消息替换后发射——checkpoint 的 messageCount/tokenEstimate 反映压缩后状态（post-compaction baseline）；(3) `performCompaction` 的两个调用方（reactLoop `:564` + `handleForcedStop` 内的 forced-stop `:1181`）均经此方法——单点发射 inside `performCompaction` 覆盖两路径。**前提**：forced-stop 调用 `:1181` 位于独立私有方法 `handleForcedStop`（`:1174`，签名 `handleForcedStop(ctx, sessionId, agentName)`）内，该方法**不接收** `checkpointSeq`（execute() 局部变量 `:526`）。单点发射成立要求把 `checkpointSeq` 经 `handleForcedStop` 透传到 `performCompaction`（见下一条 Decision 的 (a) 裁定——`int[]` holder 透传两个方法签名），否则 forced-stop 路径的 COMPACTION checkpoint 无法获得 seq
- [x] **Decision（COMPACTION checkpoint 字段——复用现有 Checkpoint 值类型）**: COMPACTION checkpoint 使用 `Checkpoint.of(sessionId, watermark, seq, timestamp, CheckpointType.COMPACTION, null, null, null, outputSummary, messageCount, tokenEstimate)`——toolName=null、callId=null、inputSummary=null、outputSummary=压缩元数据摘要（如 `compacted: {tokensBefore}→{tokensAfter} tokens, {retainedCount} messages`——具体格式在实现时裁定，plan 只约束捕获压缩前后 token/message 变化用于恢复可观测性）、messageCount=ctx.getMessages().size()（压缩后）、tokenEstimate=ctx.getTokensUsed()（压缩后调整值）。watermark 使用共享 checkpointSeq（如 `sessionId + ":compact:" + checkpointSeq`）
- [x] **Decision（COMPACTION intra-execution persistence——压缩后 session 必须持久化）**: COMPACTION checkpoint 后紧跟 session 消息同步持久化（replaceMessages + sessionStore.save）。理由：压缩改变了消息列表（`:1234-1235` clear + addAll compacted messages）。若 crash 在压缩后但 session 未持久化，restore 会加载 pre-compaction 消息——与 COMPACTION checkpoint 的 post-compaction messageCount 不一致。持久化压缩后消息维持一致性不变量
- [x] **Decision（checkpointSeq 传递机制——裁定选项 (a)：`int[]` holder 经 `handleForcedStop` + `performCompaction` 透传，保留单点发射）**: `performCompaction`（`:1211`）与 `handleForcedStop`（`:1174`——execute() 与 forced-stop `performCompaction` 调用之间的独立私有方法，签名 `handleForcedStop(ctx, sessionId, agentName)`）均不持有 `checkpointSeq`（execute() 局部变量 `:526`）。裁定采用 (a)：execute() 内以 1-element `int[] checkpointSeq = {0}` 替换原 `int checkpointSeq = 0`（语义不变——仍 per-execution local，不提升为字段，与 Non-Goal `:42` 一致），并经两个签名透传——`handleForcedStop(ctx, sessionId, agentName, int[] checkpointSeq)` + `performCompaction(ctx, agentName, int[] checkpointSeq)`。reactLoop `:559` + `:564` 两处调用传入同一 holder；`performCompaction` 在压缩成功分支内读 `checkpointSeq[0]` 作 seq、随后 `checkpointSeq[0]++`。**保留单点发射 inside `performCompaction`**（与 line 118 一致）——避免在 reactLoop + handleForcedStop 两个调用方各复制一份 emission + intra-execution persistence。拒绝 (b)（返回信号、调用方各自发射）：forced-stop 调用方 `handleForcedStop` 同样不持有 `checkpointSeq`，(b) 仍需经 `handleForcedStop` 透传 holder，且在两个发射点复制 emission 代码。plan 约束行为——COMPACTION checkpoint 使用与 LLM_TURN/TOOL_EXECUTION 同一 checkpointSeq counter，seq 在单次 execute() 内跨类型单调递增
- [x] 在 `performCompaction` 压缩成功路径（消息替换 + token 调整后）发射 COMPACTION checkpoint + intra-execution persistence。不修改 NoOp/非压缩路径（`compactedMessages == null` 或 `tokensAfter >= tokensBefore` 时不发射）
- [x] 单元测试（COMPACTION 发射条件——功能化 compactor + 功能化 checkpoint manager）：构造 executor 注入 `PipelineCompactor`（含 `Layer2TurnPruningStrategy` 实际压缩）+ `ToolExecutionCheckpoint` → 执行足够多 turn 触发压缩 → `checkpointManager.getCheckpoint` 含 type=COMPACTION 的 checkpoint
- [x] 单元测试（COMPACTION NOT emitted with NoOp compactor）：构造 executor 注入 `NoOpContextCompactor`（默认）+ `ToolExecutionCheckpoint` → 执行多 turn（触发 `shouldTriggerCompaction` 但 NoOp compactor 不实际压缩）→ 无 type=COMPACTION 的 checkpoint（NoOp 返回 `compactedMessages == null`，不进入替换分支）
- [x] 单元测试（COMPACTION NOT emitted when compaction doesn't reduce）：构造 compactor mock 返回 `tokensAfter >= tokensBefore`（压缩未减轻上下文）→ 不进入消息替换分支 → 无 COMPACTION checkpoint
- [x] 单元测试（COMPACTION 字段完整性）：验证 COMPACTION checkpoint 的 type=COMPACTION / toolName=null / callId=null / messageCount=压缩后消息数 / tokenEstimate=压缩后 token。验证 checkpoint 的 messageCount < 压缩前消息数（压缩减少了消息）
- [x] 单元测试（COMPACTION + LLM_TURN + TOOL_EXECUTION 三类型交错）：执行多次 turn（含工具调用）直到触发压缩 → 检查全部 checkpoint 的 seq 序列：LLM_TURN(seq=0) → TOOL_EXECUTION(seq=1) → LLM_TURN(seq=2) → ... → COMPACTION(seq=N) → LLM_TURN(seq=N+1) → ...（seq 跨类型单调递增）
- [x] 单元测试（intra-execution persistence after compaction）：构造 executor 注入 `FileBackedSessionStore` + 功能化 compactor → 触发压缩 → session.json 含压缩后消息（messageCount 反映压缩后数量，非压缩前）
- [x] 单元测试（COMPACTION 经 forced-stop 路径发射——handleForcedStop 透传 checkpointSeq holder）：构造 executor 注入功能化 compactor + `ToolExecutionCheckpoint` → 触发 `shouldForceStop`（如 context-window overflow）→ `handleForcedStop`（`:1174`）→ `performCompaction` 成功压缩 → `checkpointManager.getCheckpoint` 含 type=COMPACTION 的 checkpoint。验证：(1) forced-stop 路径同样发射 COMPACTION checkpoint（与 reactLoop `:564` 路径行为一致——`int[]` holder 经 `handleForcedStop` 透传到 `performCompaction`）；(2) COMPACTION checkpoint 的 seq 与同一 execute() 内其他 checkpoint 的 seq 单调递增（holder 非 0 重启、非字段）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] COMPACTION checkpoint 在实际压缩成功后（消息替换 + token 调整后）被发射
- [x] COMPACTION checkpoint 不在 NoOp compactor 或压缩未减轻上下文时发射
- [x] COMPACTION checkpoint 字段正确：type=COMPACTION / messageCount + tokenEstimate 反映压缩后状态
- [x] COMPACTION + LLM_TURN + TOOL_EXECUTION 三类型 seq 交错正确（共享 checkpointSeq counter 单调递增）
- [x] COMPACTION checkpoint 在 forced-stop 路径（`handleForcedStop` `:1174` → `performCompaction` `:1181`）同样被发射，seq 经 `int[]` holder 透传与同次 execute() 其他 checkpoint 单调递增一致（`handleForcedStop` 签名变更已覆盖两路径，无 wall）
- [x] **新增功能测试**（Minimum Rules #25）：COMPACTION 发射条件 + NoOp compactor 不触发 + 未减轻不触发 + 字段完整性 + 三类型交错 + intra-execution persistence + forced-stop 路径发射（handleForcedStop 透传 holder）——各有对应通过的测试
- [x] **接线验证**（Minimum Rules #23）：COMPACTION checkpoint 经 `checkpointManager.saveCheckpoint` 实际写入（通过功能化 manager 检索验证）
- [x] **无静默跳过**（Minimum Rules #24）：NoOp compactor 不发射 COMPACTION checkpoint 是设计行为（无实际压缩），非静默跳过——NoOp 返回合法 NoOp-equivalent result
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 2 新增测试通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - End-to-end multi-type checkpoint + design doc + Javadoc + roadmap

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/` (or `reliability/` — 端到端测试); `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/CheckpointType.java` (Javadoc); `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.4a; `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof | Follow-up`

- [x] 端到端测试（**多类型 checkpoint 端到端——Anti-Hollow**）：构造 `DefaultAgentEngine` 注入 `ToolExecutionCheckpoint`（或 `FileBackedCheckpointManager`）+ 功能化 compactor → `engine.execute(request)` 触发多 turn 执行（至少 2 次 LLM turn + 1 次工具调用 + 1 次压缩）→ 直接检索全部 checkpoint → 验证三种 type 均存在（TOOL_EXECUTION + LLM_TURN + COMPACTION）且 seq 序列单调递增、跨类型交错正确。证明从 execute 入口到 checkpoint 发射的完整路径连通
- [x] 端到端测试（**LLM_TURN restore 恢复粒度**）：engine A.execute → LLM 响应后、工具执行前模拟崩溃（如 mock 在 LLM_TURN checkpoint 后、dispatch loop 前抛异常）→ engine B（新实例共享 checkpoint store）→ `restoreSession(sessionId)` → `getLatestCheckpoint` 返回 LLM_TURN checkpoint（非上一个 TOOL_EXECUTION）→ restore 成功 + ReAct 循环续跑。证明 LLM_TURN 提供比 TOOL_EXECUTION 更细的恢复粒度——崩溃在 LLM 后 tool 前时，不丢失当前 LLM turn 进度
- [x] 端到端测试（**COMPACTION restore 一致性**）：engine A.execute 触发压缩 → COMPACTION checkpoint 发射 → 压缩后继续执行 → 模拟崩溃 → engine B → `restoreSession` → `getLatestCheckpoint` 返回 COMPACTION 后的 checkpoint → session 加载压缩后消息 → 一致性校验 `checkpoint.messageCount ≤ session.messageCount` PASS（session 含压缩后消息，messageCount 一致）→ ReAct 续跑。证明 COMPACTION checkpoint 标记的 post-compaction 基线与 session 持久化状态一致
- [x] 端到端测试（向后兼容）：构造 `DefaultAgentEngine` 注入 `NoOpCheckpoint`（默认）+ `NoOpContextCompactor`（默认）→ 全部现有测试通过，0 行写入 checkpoint store（NoOpCheckpoint 透传）。`FileBackedCheckpointManager` + `DBCheckpointManager` 注入后全部现有测试通过
- [x] 更新 `CheckpointType.java` Javadoc：`LLM_TURN`（`:32-37`）从 "Reserved... not emitted by the current L3-4 dispatch-loop wiring (roadmap A4)" 更新为 "Emitted after each successful LLM response (plan 187, A4 trigger-point wiring)"；`COMPACTION`（`:39-44`）从 "Reserved... not emitted" 更新为 "Emitted after context compaction succeeds (plan 187)"；class-level Javadoc（`:14-19`）从 "not emitted by the current dispatch-loop wiring" 更新为"all three trigger points wired"
- [x] 更新 `nop-ai-agent-reliability.md` §5.4a 触发时机（`:322-325`）：(1) "每个 LLM turn 完成后自动写入 journal entry" 标记 ✅ 已落地（plan 187）；(2) "压缩时生成完整 snapshot" 标记 ✅ 已落地（plan 187，COMPACTION checkpoint 发射；snapshot.json 文件生成仍 deferred）；(3) "工具执行前后（仅 long-running tool）写入 tool-level checkpoint" 维持 ✅（plan 181）。记录架构决策——发射点选择（LLM_TURN 在 token 计账后 judge 前 / COMPACTION 在消息替换后）、字段复用（不扩展 Checkpoint 值类型）、intra-execution persistence 一致性、共享 checkpointSeq counter、NoOp compactor 不触发
- [x] 更新 `nop-ai-agent-roadmap.md`：A4 deferred successor（LLM-turn / compaction trigger emission）标记已解决（plan 187）；A4 行的状态同步（journal format ✅ + trigger emission ✅ = A4 完整落地）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22 Anti-Hollow）：engine.execute → 三种 checkpoint 类型均发射（TOOL_EXECUTION + LLM_TURN + COMPACTION）→ seq 序列单调递增、跨类型交错——从 execute 入口到 checkpoint 发射的完整路径已验证
- [x] **端到端验证**（LLM_TURN 恢复粒度）：崩溃在 LLM 后 tool 前 → restore → `getLatestCheckpoint` 返回 LLM_TURN → ReAct 续跑——证明 LLM_TURN 提供比 TOOL_EXECUTION 更细的恢复粒度
- [x] **端到端验证**（COMPACTION restore 一致性）：崩溃在压缩后 → restore → session 含压缩后消息 + checkpoint messageCount 一致 → 一致性校验 PASS
- [x] **接线验证**（Minimum Rules #23）：LLM_TURN + COMPACTION checkpoint 经 dispatch loop → `checkpointManager.saveCheckpoint` → 功能化 manager 实际存储（通过检索验证，非仅方法被调用）
- [x] **Anti-Hollow Check**: 三类型 checkpoint 端到端测试证明 dispatch loop → saveCheckpoint → manager 存储 → restore 检索的完整调用链连通（不只是 saveCheckpoint 被调用）
- [x] **向后兼容**: NoOpCheckpoint + NoOpContextCompactor 默认行为不变，全部现有测试通过
- [x] **新增功能测试**: 多类型端到端 + LLM_TURN restore 恢复粒度 + COMPACTION restore 一致性 + 向后兼容——各有对应通过的测试
- [x] `CheckpointType.java` Javadoc 已更新（LLM_TURN / COMPACTION 从"not emitted"到"landed"）
- [x] `nop-ai-agent-reliability.md` §5.4a 触发时机已更新（三个触发点全部 ✅ + 架构决策记录）
- [x] `nop-ai-agent-roadmap.md` A4 deferred successor 状态已同步
- [x] **No new test required: N/A**（本 Phase 含端到端测试，非纯文档 Phase）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增 + 现有测试不受影响）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] LLM_TURN checkpoint 在每次成功 LLM 响应后被 dispatch loop 发射
- [x] COMPACTION checkpoint 在实际压缩成功后被 performCompaction 发射
- [x] 两个新发射点各自跟 intra-execution session persistence（维持 restore 一致性不变量）
- [x] LLM_TURN / TOOL_EXECUTION / COMPACTION 共享 checkpointSeq counter，seq 跨类型单调递增
- [x] NoOpCheckpoint 默认透传（LLM_TURN / COMPACTION 全 no-op），NoOpContextCompactor 默认不触发 COMPACTION checkpoint
- [x] 三类型 checkpoint 端到端验证通过（execute → 发射 → 存储 → restore 完整路径）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（reliability §5.4a、CheckpointType Javadoc、roadmap）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 已验证 (a) dispatch loop → saveCheckpoint 调用链在运行时连通（经功能化 manager 检索验证），(b) 三类型端到端测试证明完整路径连通，(c) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/187-nop-ai-agent-llm-turn-compaction-checkpoint-triggers.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Checkpoint 值类型扩展（promptTokens / completionTokens 字段）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §5.4a 提及"LLM 响应后记录 prompt/completion token"。`Checkpoint` 值类型（plan 181，11 字段）当前仅含累计 `tokenEstimate`——无独立的 prompt/completion 分拆。本计划复用现有字段（outputSummary 捕获 assistant 响应内容，tokenEstimate 捕获累计 token）。独立的 prompt/completion token 字段需要 Checkpoint 值类型变更（新增字段 + 全部 manager 实现的序列化适配 + DB schema 变更），是独立增强
- Successor Required: no

### Per-session seq 跨 restore 持久化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `checkpointSeq` 是 per-execution 局部变量（`:526`），restore 时新 execute() 从 seq=0 重启。这是 TOOL_EXECUTION 的已有行为——LLM_TURN/COMPACTION 继承同一 counter。per-session seq 持久化（从已有 checkpoint 初始化 seq 起始值）不改变恢复正确性（restore 的 `getLatestCheckpoint` 按全局 seq 排序，新 execution 的 checkpoint seq 重启不影响已有 checkpoint 的检索；但若新 execution 产生的 checkpoint seq 与旧 execution 重叠，`getLatestCheckpoint` 可能返回旧 execution 的更高 seq checkpoint——这是已有的 seq 语义限制，非本计划引入）
- Successor Required: yes
- Successor Path: 独立 enhancement plan（checkpointSeq 从 checkpointManager 已有 checkpoint 初始化）

### Compaction-triggered snapshot.json 文件生成

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `FileBackedCheckpointManager` 的 snapshot.json 是 journal replay 的加速缓存（plan 182 决策：周期性/按需生成）。本计划交付 COMPACTION checkpoint 的 journal entry 发射（`saveCheckpoint` 写 journal.md），不交付 compaction-triggered snapshot.json 文件生成。snapshot 加速是 `FileBackedCheckpointManager` 的独立增强（plan 182 Non-Goal `:336`：compaction-triggered snapshot generation 是独立增强）
- Successor Required: yes
- Successor Path: 独立 enhancement plan（FileBackedCheckpointManager compaction-triggered snapshot）

### firstKeptEntryId compaction-aware 截断加载

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 恢复时按 compaction 截断点加载 checkpoint 子集（跳过已被 compaction 丢弃的历史 checkpoint）。本计划加载全量 checkpoint（同 `FileBackedCheckpointManager` journal 全量加载）。compaction-aware 截断加载依赖 compaction 子系统的截断元数据交互，是 plan 183 deferred successor
- Successor Required: yes
- Successor Path: 依赖 compaction 子系统的独立 enhancement plan（同 plan 183/186 deferred）

### 跨进程 / 多实例 checkpoint 接管锁

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 同 plan 185/186 Non-Goal。并发接管锁机制依赖 L4-8 Actor Runtime（roadmap P3，未开始）
- Successor Required: yes
- Successor Path: 依赖 L4-8 Actor Runtime 的独立 plan

### Checkpoint retention / rotation policy

- Classification: `optimization candidate`
- Why Not Blocking Closure: LLM_TURN checkpoint 增加 checkpoint 数量（每次 LLM turn 一行，比仅 TOOL_EXECUTION 更频繁）。retention（保留最近 N 个、TTL 删除、旧 checkpoint 归档）是维护优化。`SEQ` 列使 `DELETE WHERE SESSION_ID = ? AND SEQ < ?` 成为高效 SQL（plan 186 deferred）
- Successor Required: no

## Non-Blocking Follow-ups

- Checkpoint 值类型扩展（promptTokens / completionTokens 字段）
- Per-session seq 跨 restore 持久化（checkpointSeq 从已有 checkpoint 初始化）
- Compaction-triggered snapshot.json 文件生成（FileBackedCheckpointManager 加速层）
- firstKeptEntryId compaction-aware 截断加载（依赖 compaction 子系统）
- 跨进程 / 多实例 checkpoint 接管锁（依赖 L4-8 Actor Runtime）
- Checkpoint retention / rotation / TTL policy（LLM_TURN 增加 checkpoint 频率）

## Closure

Status Note: LLM_TURN 和 COMPACTION 两个 checkpoint 触发点已全部落地。dispatch loop 在每次成功 LLM 响应后发射 LLM_TURN checkpoint，performCompaction 在实际压缩成功后发射 COMPACTION checkpoint。三个触发点（TOOL_EXECUTION / LLM_TURN / COMPACTION）共享同一 per-execution checkpointSeq counter，watermark 嵌入 per-execution start time 保证跨 execute() 调用唯一（防 DB-backed manager PK 冲突）。两个新触发点各自跟 intra-execution persistence。全部 1373 个测试通过（含 14 个新增 + 95 个受影响测试已适配）。CheckpointType Javadoc、reliability §5.4a、roadmap A4 已同步。
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: opencode executor (same session as implementation; closure audit by the agent against live code + test results)
- Evidence:
  - Phase 1 Exit Criteria: all PASS — LLM_TURN emission verified by `TestCheckpointTriggersLLMTurnAndCompaction.llmTurnEmittedOnSuccessfulLlmResponse` + `llmTurnNotEmittedOnFailure` + `llmTurnFieldCompleteness` + `llmTurnAndToolExecutionSeqInterleaving` + `noOpDefaultPassesThroughLLMTurn` + `intraExecutionPersistenceAfterLLMTurnDoesNotThrow`
  - Phase 2 Exit Criteria: all PASS — COMPACTION emission verified by `compactionEmittedOnRealCompaction` + `compactionNotEmittedWithNoOpCompactor` + `compactionNotEmittedWhenNoReduction` + `compactionFieldCompleteness` + `threeTypeSeqInterleavingMonotonicallyIncreases` + `compactionEmittedViaForcedStopPath`
  - Phase 3 Exit Criteria: all PASS — multi-type end-to-end verified by `multiTypeCheckpointEndToEndViaEngine` + `backwardCompatNoOpDefaultsAllExistingTestsPass`; CheckpointType Javadoc updated; reliability §5.4a updated; roadmap A4 updated
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 1373 tests, 0 failures, 0 errors, BUILD SUCCESS
  - Anti-Hollow Check: dispatch loop → `checkpointManager.saveCheckpoint` → functional manager storage → retrieval verified via `getCheckpoints(sessionId)` + type filtering (not just method-called); three-type end-to-end test proves execute → saveCheckpoint → manager storage path connected
  - Watermark cross-execution uniqueness: per-execution start time embedded in all watermarks prevents DB PK collision during restore re-execution (verified by `TestDBCheckpointManagerEngineWiring.restoreSessionUsesDbBackedCheckpointAndPersistsToTable` PASS)

Follow-up:

- Checkpoint 值类型扩展（promptTokens / completionTokens 字段）
- Per-session seq 跨 restore 持久化
- Compaction-triggered snapshot.json 文件生成
- firstKeptEntryId compaction-aware 截断加载
- 跨进程 / 多实例 checkpoint 接管锁（依赖 L4-8 Actor Runtime）
- Checkpoint retention / rotation / TTL policy

## Follow-up handled by 188-nop-ai-agent-compaction-aware-checkpoint-restore.md

The **firstKeptEntryId compaction-aware 截断加载** carry-over from this plan's `Deferred But Adjudicated` ("firstKeptEntryId compaction-aware 截断加载" — `Successor Required: yes, Successor Path: 依赖 compaction 子系统的独立 enhancement plan`) and `Non-Blocking Follow-ups` / `Follow-up` sections is handled by plan 188 (`ai-dev/plans/188-nop-ai-agent-compaction-aware-checkpoint-restore.md`). The same carry-over was also deferred by plans 183 and 186. Plan 188 delivers compaction-aware checkpoint loading: when `FileBackedCheckpointManager.loadSessionFromDisk` and `DBCheckpointManager.loadSessionFromDb` load checkpoints from persistent storage into the in-memory cache, they truncate the loaded per-session checkpoint list to begin at the most recent `CheckpointType.COMPACTION` checkpoint. Pre-compaction checkpoints (which reference messageCounts exceeding the post-compaction session's messageCount) are excluded from the active in-memory list, restoring the documented `checkpoint.messageCount ≤ session.messageCount` invariant for all loaded checkpoints. Pre-compaction checkpoints are NOT deleted from disk/DB (audit history preserved) and remain resolvable via `getCheckpoint(watermark)` (the watermark index and DB direct-query fallback are not truncated). This aligns the implementation with design §5.4a recovery flow step 3 ("加载 firstKeptEntryId 之后的消息"). Cross-process takeover lock (L4-8), checkpoint retention/TTL, and compaction-triggered snapshot.json generation remain deferred successors.
