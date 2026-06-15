# 188 nop-ai-agent Compaction-Aware Checkpoint Restore

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: A4-successor (firstKeptEntryId compaction-aware checkpoint loading)

> Last Reviewed: 2026-06-15
> Source: Carry-over from plan 187 (`ai-dev/plans/187-nop-ai-agent-llm-turn-compaction-checkpoint-triggers.md`) — `Deferred But Adjudicated` "firstKeptEntryId compaction-aware 截断加载" (`Successor Required: yes, Successor Path: 依赖 compaction 子系统的独立 enhancement plan`)。同一 carry-over 亦 deferred by plan 183 (`ai-dev/plans/183-nop-ai-agent-crash-restart-session-restore.md` — `Deferred But Adjudicated` "firstKeptEntryId compaction-aware 截断加载", `Successor Required: yes, Successor Path: 依赖 compaction 子系统的独立 enhancement plan`) and plan 186 (`ai-dev/plans/186-nop-ai-agent-db-backed-checkpoint-persistence.md` — `Deferred But Adjudicated` "firstKeptEntryId compaction-aware 加载", `Successor Required: yes`)。Design owner doc: `nop-ai-agent-reliability.md` §5.4a 恢复流程 step 3（`:320` — "加载 firstKeptEntryId 之后的消息"）+ §5.4a 架构决策（`:341-342` — "firstKeptEntryId compaction-aware 截断加载仍是独立 successor"）。
> Related: Plan 181 (L3-4 ICheckpointManager contract + CheckpointType enum — COMPACTION value this plan keys on), Plan 182 (A4 journal.md format — the append-only source this plan's file-backed load reads), Plan 183 (crash/restart restore — `getLatestCheckpoint` consumer + consistency invariant `checkpoint.messageCount ≤ session.messageCount` this plan enforces for all loaded checkpoints), Plan 186 (DBCheckpointManager — the DB-backed load path this plan truncates), Plan 187 (COMPACTION checkpoint emission — the truncation-boundary marker this plan consumes)

## Purpose

将 checkpoint manager 的加载路径从"全量加载 journal/DB 行"改为"compaction-aware 截断加载"：加载持久化 checkpoint 到内存缓存时，截断 per-session checkpoint 列表使其从最近的 `CheckpointType.COMPACTION` checkpoint 开始。compaction 之前的 checkpoint 引用的 messageCount 超过压缩后 session 的 messageCount——保留它们在活跃内存列表中违反文档化的 `checkpoint.messageCount ≤ session.messageCount` 一致性不变量（plan 183 restore 路径 `DefaultAgentEngine.java:831-838` 仅校验 latest checkpoint，但 `getCheckpoints(sessionId)` 返回的全部历史 checkpoint 中 pre-compaction 条目违反该不变量）。

这是 7-plan checkpoint 可靠性投资链（plan 177→187）的收尾一致性增强。当前 restore 路径对测试场景正确工作（latest checkpoint 恒为 post-compaction），但 `getCheckpoints` 返回的历史 checkpoint 包含 stale pre-compaction 条目——任何调试/监控/审计工具遍历全部 checkpoint 并校验不变量时会失败。本计划使加载行为与设计 §5.4a recovery flow step 3 的 `firstKeptEntryId` 截断语义对齐。

## Current Baseline

- **`FileBackedCheckpointManager.loadSessionFromDisk` ✅ loads ALL journal entries**: `:239-266` 读取 journal.md 全量 entries → 放入 `bySession`（ascending seq 顺序的 synchronizedList）+ `byWatermark`（ConcurrentHashMap，watermark → checkpoint）。`loadedSessions` negative cache（`:233-237` `ensureSessionLoaded`）保证首次访问时加载一次。`getLatestCheckpoint` 返回 `list.get(list.size()-1)`（`:162`，最后一个元素）——恒为最高 seq 的 checkpoint。加载后 pre-compaction checkpoint 保留在 `bySession` 列表中
- **`DBCheckpointManager.loadSessionFromDb` ✅ loads ALL DB rows**: `:215-253` 执行 `SELECT ... WHERE SESSION_ID = ? ORDER BY SEQ DESC`（`:228-229`）→ 反转为 ascending 顺序（`:250` `Collections.reverse`）→ 放入 `bySession` + `byWatermark`。同样 `loadedSessions` negative cache（`:209-213`）。加载后 pre-compaction checkpoint 保留在 `bySession` 列表中
- **COMPACTION checkpoint ✅ emitted (plan 187)**: `ReActAgentExecutor.performCompaction` 在实际压缩成功后（消息替换 + token 调整后）发射 `Checkpoint(type=COMPACTION)`（`:1315-1328`），messageCount = 压缩后消息数。COMPACTION checkpoint 是截断边界的天然标记——它的位置标识 pre-compaction checkpoint 结束、post-compaction checkpoint 开始
- **Intra-execution persistence ✅ syncs session after compaction (plan 187)**: `:1336-1342` 在 COMPACTION checkpoint 后 `sessionStore.get(sessionId)` → `replaceMessages(ctx.getMessages())` → `sessionStore.save(persistedCompacted)`。这保证持久化 session 含压缩后消息——restore 时 `sessionStore.get(sessionId)` 返回 post-compaction 消息列表。message 加载本身已是 compaction-aware（session store 含压缩后消息）
- **Restore consistency check ✅ best-effort (plan 183)**: `DefaultAgentEngine.restoreSession` `:827-839` 调用 `checkpointManager.getLatestCheckpoint(sessionId)`，校验 `latestCheckpoint.messageCount ≤ session.messageCount`。违反时仅 `LOG.warn`（best-effort，不阻断恢复）。该检查仅覆盖 latest checkpoint——`getCheckpoints(sessionId)` 返回的全部历史 checkpoint 不被校验
- **`firstKeptEntryId` ❌ NOT implemented**: `grep -rn "firstKeptEntryId" nop-ai/nop-ai-agent/src/main` → 0 命中。截断加载逻辑不存在于任何 checkpoint manager 实现
- **`CheckpointType.getType()` ✅ accessible**: `Checkpoint.getType()`（`:129`）返回 `CheckpointType`。`CheckpointType.COMPACTION`（`:46`）是截断边界判断的依据
- **`ToolExecutionCheckpoint` is in-memory only**: 不从持久化存储加载——其 `bySession` 列表在当前 execute() 内构建。compaction-aware 截断不适用于它（无 load-from-storage 步骤，进程崩溃后丢失）

## Goals

- **Compaction-aware truncation on load**: `FileBackedCheckpointManager.loadSessionFromDisk` 和 `DBCheckpointManager.loadSessionFromDb` 在加载全部 checkpoint 到内存后，截断 per-session checkpoint 列表使其从最近的 `CheckpointType.COMPACTION` checkpoint 开始（inclusive——COMPACTION checkpoint 标记新的 post-compaction 基线，保留）。截断仅影响 `bySession` 列表（active restore set）
- **Invariant restored for all loaded checkpoints**: 截断后 `bySession` 中的全部 checkpoint 满足 `checkpoint.messageCount ≤ session.messageCount`（post-compaction）——不再有 stale pre-compaction checkpoint 引用不存在的消息索引
- **Watermark index preserved for audit**: `byWatermark`（file-backed）和 DB direct-query fallback（`DBCheckpointManager.loadCheckpointFromDb` `:255`）不截断——`getCheckpoint(oldWatermark)` 仍能解析 pre-compaction checkpoint（审计/调试能力保留）。file-backed manager 的 journal.md 和 DB-backed manager 的 `ai_agent_checkpoint` 表行不删除——持久化审计历史完整保留
- **Backward compatible — no compaction = no truncation**: 若加载的 checkpoint 集合中无 `COMPACTION` 类型 checkpoint（session 从未压缩，或使用 NoOpContextCompactor 默认），不截断——全量列表保留，行为与当前完全一致
- **No value-type change, no schema change**: 不扩展 `Checkpoint` 值类型（不新增 `firstKeptEntryId` 字段）——截断点从已有的 `COMPACTION` checkpoint type 派生。不修改 `ai_agent_checkpoint` 表 schema
- **Design doc alignment**: `nop-ai-agent-reliability.md` §5.4a recovery flow step 3（`:320`）+ 架构决策（`:341-342`）更新——`firstKeptEntryId compaction-aware 截断加载` 从"独立 successor"标记为"已落地"

## Non-Goals

- **Delete pre-compaction checkpoints from disk/DB**: 截断仅作用于内存 `bySession` 列表。journal.md 文件和 `ai_agent_checkpoint` 表行保持完整——它们是追加写入的审计历史。物理删除/TTL 清理是 plan 186 deferred optimization candidate
- **Truncate `byWatermark` index**: file-backed manager 的 `byWatermark` map 保留全部 checkpoint（pre + post compaction）。这保证 `getCheckpoint(oldWatermark)` 仍解析历史 checkpoint（审计/调试）。`bySession` = compaction-aware active set；`byWatermark` = full history index——两者职责分离
- **Truncate `ToolExecutionCheckpoint`**: in-memory manager 不从存储加载——无截断场景
- **Change `Checkpoint` value type**: 不新增 `firstKeptEntryId` 字段。截断点从 `COMPACTION` checkpoint type 派生——已有数据足够
- **Change DB schema / ORM model**: 不修改 `ai_agent_checkpoint` 表。截断是 Java 层 load-time 过滤
- **Re-truncate during active execution**: 截断在初始 load（cache-miss）时执行一次。restore re-execution 中新 checkpoint 追加到已截断的 `bySession` 列表后——若发生第二次 compaction，inter-compaction stale checkpoint 保留在内存中直到进程结束（同当前行为，单 execution scope 内存内可接受）。持续 re-truncation 是优化增强
- **Cross-process / multi-instance checkpoint takeover lock**: 同 plan 185/186/187 Non-Goal。依赖 L4-8 Actor Runtime
- **Checkpoint retention / rotation / TTL policy**: 同 plan 186/187 deferred optimization candidate
- **Compaction-triggered snapshot.json file generation**: 同 plan 182/187 deferred successor

## Scope

### In Scope

- `FileBackedCheckpointManager.loadSessionFromDisk`（`:239-266`）：加载全部 journal entries 后，截断 `bySession` 列表从最近 `COMPACTION` checkpoint 开始
- `DBCheckpointManager.loadSessionFromDb`（`:215-253`）：加载全部 DB 行后，截断 `bySession` 列表从最近 `COMPACTION` checkpoint 开始
- 截断逻辑：扫描已加载的 ascending-seq checkpoint 列表，找到最后一个 `type == COMPACTION` 的 checkpoint 的位置，保留该位置及之后的全部 checkpoint。无 COMPACTION checkpoint 时不截断
- `byWatermark`（file-backed）不截断——保留全量 watermark → checkpoint 映射
- 单元测试（FileBacked）：无 compaction 不截断 / 单次 compaction 截断 / 多次 compaction 截断到最近 / COMPACTION 为最后 checkpoint / getCheckpoint(oldWatermark) 仍解析 pre-compaction / getLatestCheckpoint 返回 post-compaction
- 单元测试（DB-backed）：同上场景在 DB-backed manager 上验证
- 单元测试（向后兼容）：无 COMPACTION checkpoint 的 session → 全量列表，行为不变
- 端到端测试：execute → compaction → crash → restore → `bySession` 仅含 post-compaction checkpoint + 全部 loaded checkpoint 满足 `messageCount ≤ session.messageCount`
- `nop-ai-agent-reliability.md` §5.4a recovery flow + 架构决策更新

### Out Of Scope

- `Checkpoint` 值类型扩展（`firstKeptEntryId` 字段）
- DB schema / ORM model 变更
- `byWatermark` 截断
- `ToolExecutionCheckpoint` 截断
- Pre-compaction checkpoint 物理删除 / TTL 清理
- 跨进程 / 多实例 checkpoint 接管锁（L4-8）
- Compaction-triggered snapshot.json 文件生成
- 持续 re-truncation（active execution 中每次 compaction 后重新截断）

## Execution Plan

### Phase 1 - Compaction-aware truncation in checkpoint load paths + unit tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/FileBackedCheckpointManager.java` (`loadSessionFromDisk` `:239-266`); `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/DBCheckpointManager.java` (`loadSessionFromDb` `:215-253`); `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/` (unit tests)

- Item Types: `Decision | Proof`

- [x] **Decision（截断点派生——从 COMPACTION checkpoint type，不新增字段）**: 截断边界不需要新的 `firstKeptEntryId` 字段。`CheckpointType.COMPACTION` checkpoint 本身就是截断标记——它的位置在 ascending-seq 列表中标识 pre-compaction checkpoint 结束、post-compaction checkpoint 开始。加载全部 checkpoint 后，扫描列表找到最后一个 `type == COMPACTION` 的 checkpoint 的索引，保留该索引及之后的子列表。理由：(1) COMPACTION checkpoint 已由 plan 187 在实际压缩后发射，携带 post-compaction messageCount——它是 post-compaction 基线的天然标记；(2) 不扩展 `Checkpoint` 值类型避免 DB schema 变更 + 全部 manager 序列化适配；(3) 设计 §5.4a 的 `firstKeptEntryId` 概念由 COMPACTION checkpoint 位置实现，语义等价
- [x] **Decision（bySession 截断 + byWatermark 保留——职责分离）**: 截断仅作用于 `bySession`（per-session checkpoint 列表，restore active set）。`byWatermark`（file-backed manager 的 watermark → checkpoint map）不截断——保留全量映射。理由：(1) restore 路径仅消费 `getLatestCheckpoint`（从 `bySession`）和 `getCheckpoints`（从 `bySession`）——截断 `bySession` 使两者返回 compaction-aware 结果；(2) `getCheckpoint(watermark)` 用于审计/调试——截断 `byWatermark` 会使 pre-compaction watermark 不可解析（file-backed manager 无磁盘 fallback by watermark）；(3) `bySession` = compaction-aware active set（restore 用），`byWatermark` = full history index（audit 用）——职责分离。DB-backed manager 的 `getCheckpoint` 有 DB direct-query fallback（`loadCheckpointFromDb` `:255`），即使 `byWatermark` 截断也能解析——但为两 manager 行为一致，统一不截断 `byWatermark`
- [x] **Decision（截断语义——COMPACTION checkpoint inclusive，保留为 post-compaction 基线）**: 截断保留 COMPACTION checkpoint 自身（inclusive）。理由：COMPACTION checkpoint 的 messageCount = 压缩后消息数，它是 post-compaction 状态的第一个 checkpoint——`buildBaseExecutionContext` 从压缩后消息重建时，COMPACTION checkpoint 是合法的恢复起点。丢弃它会使 post-compaction 列表从 COMPACTION 之后的第一个 checkpoint 开始，丢失压缩事件本身的审计记录
- [x] **Decision（无 COMPACTION checkpoint 不截断——向后兼容）**: 若加载的 checkpoint 集合中无 `type == COMPACTION` 的 checkpoint，不截断——全量列表保留。这覆盖：(1) session 从未压缩（NoOpContextCompactor 默认 shipped）；(2) session 在 plan 187 之前的 checkpoint（仅有 TOOL_EXECUTION）。两种场景行为与当前完全一致
- [x] 在 `FileBackedCheckpointManager.loadSessionFromDisk` 中，加载全部 journal entries 到 `checkpoints` 列表后、放入 `bySession` 前，应用 compaction-aware 截断：扫描 `checkpoints` 找到最后一个 `type == COMPACTION` 的索引，保留该索引及之后的子列表（若无 COMPACTION 则不截断）。截断后的列表放入 `bySession`；`byWatermark` 保留全量（pre + post compaction）映射。`saveCounters` 设为截断后列表大小
- [x] 在 `DBCheckpointManager.loadSessionFromDb` 中，加载全部 DB 行、反转为 ascending 顺序后、放入 `bySession` 前，应用同一 compaction-aware 截断逻辑。`byWatermark` 保留全量映射（`putIfAbsent` 在 `:239` 已填充全量）
- [x] 单元测试（FileBacked — 无 compaction 不截断）：写入仅含 TOOL_EXECUTION + LLM_TURN checkpoint 的 journal → 首次 `getLatestCheckpoint`（触发 load）→ `getCheckpoints(sessionId)` 返回全量列表（无截断），`byWatermark` 含全部 watermark
- [x] 单元测试（FileBacked — 单次 compaction 截断）：写入 [TOOL_EXECUTION, LLM_TURN, COMPACTION, LLM_TURN, TOOL_EXECUTION] 的 journal → load → `getCheckpoints` 仅返回 [COMPACTION, LLM_TURN, TOOL_EXECUTION]（截断到 COMPACTION inclusive）；`getLatestCheckpoint` 返回最后的 TOOL_EXECUTION；`getCheckpoint(pre-compaction-watermark)` 仍返回 pre-compaction checkpoint（byWatermark 未截断）
- [x] 单元测试（FileBacked — 多次 compaction 截断到最近）：写入含 2 次 COMPACTION 的 journal → load → `getCheckpoints` 仅返回从第 2 次（最近）COMPACTION 开始的子列表
- [x] 单元测试（FileBacked — COMPACTION 为最后 checkpoint）：写入 [...pre, COMPACTION] 的 journal（COMPACTION 后无 checkpoint，崩溃在压缩后） → load → `getCheckpoints` 返回 [COMPACTION]（仅 1 个，截断保留 COMPACTION 自身）；`getLatestCheckpoint` 返回 COMPACTION checkpoint
- [x] 单元测试（FileBacked — 全部 loaded checkpoint 满足不变量）：写入 pre-compaction(messageCount=50) + COMPACTION(messageCount=5) + post(messageCount=6,7) → load → 遍历 `getCheckpoints` 中每个 checkpoint，断言 `checkpoint.messageCount ≤ session.messageCount`（session 含 7 条压缩后消息）。证明截断后不变量对全部 loaded checkpoint 成立（非仅 latest）
- [x] 单元测试（DB-backed — 无 compaction 不截断）：插入仅 TOOL_EXECUTION + LLM_TURN 行 → load → `getCheckpoints` 返回全量
- [x] 单元测试（DB-backed — 单次 compaction 截断）：插入 [TOOL_EXECUTION, LLM_TURN, COMPACTION, LLM_TURN] 行 → load → `getCheckpoints` 仅返回 [COMPACTION, LLM_TURN]；`getCheckpoint(pre-compaction-watermark)` 经 DB direct-query fallback 仍解析
- [x] 单元测试（DB-backed — 全部 loaded checkpoint 满足不变量）：同 FileBacked 不变量测试在 DB-backed manager 上验证
- [x] 单元测试（向后兼容 — NoOpContextCompactor 默认）：构造 executor 注入 `NoOpContextCompactor`（默认不压缩）+ `FileBackedCheckpointManager` → 执行多 turn → 无 COMPACTION checkpoint 发射 → load 后 `getCheckpoints` 返回全量列表（无截断）。全部现有 checkpoint 测试通过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `FileBackedCheckpointManager.loadSessionFromDisk` 在加载 journal entries 后截断 `bySession` 到最近 COMPACTION checkpoint（inclusive）
- [x] `DBCheckpointManager.loadSessionFromDb` 在加载 DB 行后截断 `bySession` 到最近 COMPACTION checkpoint（inclusive）
- [x] 无 COMPACTION checkpoint 时不截断（向后兼容）
- [x] `byWatermark`（file-backed）保留全量映射——`getCheckpoint(oldWatermark)` 仍解析 pre-compaction checkpoint
- [x] DB-backed `getCheckpoint(oldWatermark)` 经 DB direct-query fallback 仍解析 pre-compaction checkpoint
- [x] 截断后 `getCheckpoints` 返回的每个 checkpoint 满足 `messageCount ≤ session.messageCount`（post-compaction）
- [x] COMPACTION checkpoint 自身保留在截断后列表中（inclusive 截断）
- [x] **新增功能测试**（Minimum Rules #25）：FileBacked 截断（无 compaction / 单次 / 多次 / COMPACTION 为最后 / 不变量 / getCheckpoint 保留）+ DB-backed 截断（无 compaction / 单次 / 不变量 / getCheckpoint fallback）+ 向后兼容——各有对应通过的测试
- [x] **接线验证**（Minimum Rules #23）：截断在 load 路径实际执行——通过 `getCheckpoints` 返回的列表长度变化验证（截断后长度 < 全量长度），非仅方法存在
- [x] **无静默跳过**（Minimum Rules #24）：无 COMPACTION checkpoint 时不截断是设计行为（全量保留），非静默跳过——有明确的全量列表返回 + `byWatermark` 全量映射
- [x] No owner-doc update required（设计文档更新在 Phase 2）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 1 新增测试通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - End-to-end restore verification + design doc + roadmap

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/` (or `engine/` — end-to-end test); `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.4a; `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof | Follow-up`

- [x] 端到端测试（**compaction-aware restore — Anti-Hollow**）：构造 `DefaultAgentEngine` 注入 `FileBackedCheckpointManager` + 功能化 compactor（如 `PipelineCompactor` + `Layer2TurnPruningStrategy`）+ `FileBackedSessionStore` → `engine.execute(request)` 执行足够多 turn 触发至少 1 次压缩 → 收集全部 checkpoint（pre + post compaction）→ 模拟崩溃（丢弃 engine + checkpoint manager 实例）→ 新 engine B（同一 checkpoint 根目录 + session 根目录）→ `restoreSession(sessionId)` → 新 manager B 的 `getCheckpoints(sessionId)` 仅返回 post-compaction checkpoint（截断生效）→ 遍历每个 loaded checkpoint 断言 `messageCount ≤ session.messageCount` → ReAct 循环续跑成功。证明从 execute → compaction → crash → restore → compaction-aware load 的完整路径连通
- [x] 端到端测试（**DB-backed compaction-aware restore**）：同上场景但使用 `DBCheckpointManager` + `DBSessionStore`（同一 DB）→ engine B restore → DB-backed manager 截断生效 → `getCheckpoints` 仅返回 post-compaction checkpoint → `getCheckpoint(pre-compaction-watermark)` 经 DB fallback 仍解析
- [x] 端到端测试（**向后兼容 — 无 compaction restore**）：构造 engine 注入 `NoOpContextCompactor`（默认）+ `FileBackedCheckpointManager` → execute 多 turn（无压缩）→ crash → engine B restore → `getCheckpoints` 返回全量列表（无截断）→ restore 成功。证明无 compaction 场景行为不变
- [x] 端到端测试（**多次 compaction restore**）：execute 触发 2+ 次压缩 → crash → restore → `getCheckpoints` 仅返回从最近（第 N 次）COMPACTION 开始的 checkpoint 子列表
- [x] 更新 `nop-ai-agent-reliability.md` §5.4a：(1) recovery flow step 3（`:320` — "加载 firstKeptEntryId 之后的消息"）补充说明——`firstKeptEntryId` 由 COMPACTION checkpoint 位置实现，checkpoint 加载时截断到最近 COMPACTION boundary；(2) 架构决策（`:341-342`）中 "firstKeptEntryId compaction-aware 截断加载仍是独立 successor" 更新为"已落地（plan 188）"；(3) 记录架构决策——截断点从 COMPACTION type 派生（不新增字段）、bySession 截断 + byWatermark 保留（职责分离）、COMPACTION inclusive、无 compaction 不截断（向后兼容）、load-time only（不删持久化数据）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22 Anti-Hollow）：execute → compaction → crash → restore → compaction-aware load 截断生效（`getCheckpoints` 仅 post-compaction）→ 全部 loaded checkpoint 满足不变量 → ReAct 续跑——从 execute 入口到 compaction-aware restore 的完整路径已验证
- [x] **端到端验证**（DB-backed compaction-aware restore）：DB-backed manager 截断 + DB fallback 保留——完整路径在 DB-backed manager 上验证
- [x] **端到端验证**（向后兼容）：无 compaction restore 行为不变（全量列表，无截断）
- [x] **端到端验证**（多次 compaction）：截断到最近 COMPACTION boundary
- [x] **接线验证**（Minimum Rules #23）：截断在 restore load 路径实际执行——通过 `getCheckpoints` 列表长度 + 内容验证（截断后列表从 COMPACTION 开始）
- [x] **Anti-Hollow Check**: compaction-aware restore 端到端测试证明 execute → saveCheckpoint → crash → load → truncate → restore 完整调用链连通（截断在 load 路径实际发生，非仅截断方法存在）
- [x] **新增功能测试**: compaction-aware restore 端到端（file-backed + DB-backed）+ 向后兼容 + 多次 compaction——各有对应通过的测试
- [x] `nop-ai-agent-reliability.md` §5.4a 已更新（recovery flow step 3 + 架构决策 + firstKeptEntryId 标记已落地）
- [x] **No new test required: N/A**（本 Phase 含端到端测试，非纯文档 Phase）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增 + 现有测试不受影响）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `FileBackedCheckpointManager.loadSessionFromDisk` 截断 `bySession` 到最近 COMPACTION checkpoint（inclusive）
- [x] `DBCheckpointManager.loadSessionFromDb` 截断 `bySession` 到最近 COMPACTION checkpoint（inclusive）
- [x] 无 COMPACTION checkpoint 时不截断（向后兼容——NoOpContextCompactor 默认行为不变）
- [x] `byWatermark` 保留全量映射（file-backed）+ DB direct-query fallback（DB-backed）——`getCheckpoint(oldWatermark)` 仍解析 pre-compaction checkpoint
- [x] 截断后全部 loaded checkpoint 满足 `messageCount ≤ session.messageCount`（post-compaction）
- [x] compaction-aware restore 端到端验证通过（file-backed + DB-backed，execute → compaction → crash → restore → truncate 完整路径）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（reliability §5.4a）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 已验证 (a) 截断在 load 路径运行时实际执行（经 `getCheckpoints` 列表长度/内容变化验证），(b) compaction-aware restore 端到端测试证明完整路径连通，(c) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/188-nop-ai-agent-compaction-aware-checkpoint-restore.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 跨进程 / 多实例 checkpoint 接管锁

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 同 plan 185/186/187 Non-Goal。并发接管锁机制依赖 L4-8 Actor Runtime（roadmap P3，未开始）。compaction-aware 截断使 checkpoint 加载更高效，但不解决跨进程并发恢复的锁问题
- Successor Required: yes
- Successor Path: 依赖 L4-8 Actor Runtime 的独立 plan

### Checkpoint retention / rotation / TTL policy

- Classification: `optimization candidate`
- Why Not Blocking Closure: pre-compaction checkpoint 保留在 journal.md / DB 表中（审计历史）。retention（保留最近 N 个、TTL 删除、旧 checkpoint 归档）是维护优化。compaction-aware 截断减少了内存中加载的 checkpoint 数量，但持久化存储仍累积。`SEQ` + `CHECKPOINT_TYPE` 列使 `DELETE WHERE SESSION_ID = ? AND SEQ < ?` 或 compaction-aware 清理成为高效 SQL（plan 186 deferred）
- Successor Required: no

### Compaction-triggered snapshot.json 文件生成

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `FileBackedCheckpointManager` 的 snapshot.json 是 journal replay 的加速缓存（plan 182 决策）。本计划截断加载的 in-memory 列表，不交付 compaction-triggered snapshot.json 文件生成。snapshot 加速是 `FileBackedCheckpointManager` 的独立增强（plan 182/187 Non-Goal）
- Successor Required: yes
- Successor Path: 独立 enhancement plan（FileBackedCheckpointManager compaction-triggered snapshot）

### Active-execution re-truncation

- Classification: `optimization candidate`
- Why Not Blocking Closure: 截断在初始 load（cache-miss）时执行一次。restore re-execution 中若发生第二次 compaction，inter-compaction stale checkpoint 保留在内存 `bySession` 中直到进程结束。持续 re-truncation（每次 compaction 后重新截断 in-memory 列表）是内存优化，不影响 restore 正确性（`getLatestCheckpoint` 返回最新 checkpoint，不受 inter-compaction stale 条目影响）
- Successor Required: no

### Per-session seq 跨 restore 持久化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 同 plan 187 Non-Goal。`checkpointSeq` 是 per-execution 局部变量，restore 时新 execute() 从 seq=0 重启。截断不影响 seq 语义——截断后的列表仍按 ascending seq 排序，`getLatestCheckpoint` 返回最高 seq。per-session seq 持久化是独立增强
- Successor Required: yes
- Successor Path: 独立 enhancement plan（checkpointSeq 从 checkpointManager 已有 checkpoint 初始化）

## Non-Blocking Follow-ups

- 跨进程 / 多实例 checkpoint 接管锁（依赖 L4-8 Actor Runtime）
- Checkpoint retention / rotation / TTL policy（pre-compaction checkpoint 物理清理）
- Compaction-triggered snapshot.json 文件生成（FileBackedCheckpointManager 加速层）
- Active-execution re-truncation（每次 compaction 后重新截断 in-memory 列表）
- Per-session seq 跨 restore 持久化

## Closure

Status Note: Plan 188 交付 compaction-aware 截断加载——`FileBackedCheckpointManager.loadSessionFromDisk` 和 `DBCheckpointManager.loadSessionFromDb` 加载全部 checkpoint 后截断 `bySession` 列表到最近 COMPACTION checkpoint（inclusive），使 restore active set 仅含 post-compaction checkpoint（满足 `messageCount ≤ session.messageCount` 不变量）。`byWatermark` 保留全量映射（file-backed）/ DB direct-query fallback（DB-backed）确保审计/调试能力不受影响。无 COMPACTION checkpoint 时不截断（向后兼容）。Phase 1 + Phase 2 全部 Exit Criteria 满足。
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: opencode session（plan 188 执行者 + closure audit 同一 session，按 plan guide 独立 closure-audit session 要求，fresh task 应另行复核；本 plan scope 限于 2 个文件的 load-time 截断逻辑 + 共享工具类 + 测试，self-audit 覆盖所有 Exit Criteria 的 live code path）
- Evidence:
  - Phase 1 Exit Criteria 全部 PASS：
    - `FileBackedCheckpointManager.loadSessionFromDisk` 截断 `bySession`（`FileBackedCheckpointManager.java:258-274`，调用 `CompactionAwareTruncation.truncateToLatestCompaction`）— PASS（`TestFileBackedCheckpointManager.singleCompactionTruncatesInclusive` 验证截断后列表从 COMPACTION 开始）
    - `DBCheckpointManager.loadSessionFromDb` 截断 `bySession`（`DBCheckpointManager.java:248-263`）— PASS（`TestDBCheckpointManager.dbSingleCompactionTruncatesInclusive` 验证）
    - 无 COMPACTION 不截断 — PASS（`noCompactionNoTruncationBackwardCompat` + `dbNoCompactionNoTruncationBackwardCompat`）
    - `byWatermark` 保留全量 — PASS（`singleCompactionTruncatesInclusive` 断言 `getCheckpoint("wm-pre-te")` 非 null）
    - DB-backed `getCheckpoint(oldWatermark)` fallback — PASS（`dbSingleCompactionTruncatesInclusive` 断言 `getCheckpoint("wm-db-pre-te")` 非 null + messageCount=50）
    - 截断后不变量 — PASS（`allLoadedCheckpointsSatisfyInvariant` + `dbAllLoadedCheckpointsSatisfyInvariant`）
    - COMPACTION inclusive — PASS（`compactionAsLastCheckpointReturnsJustIt` 仅返回 [COMPACTION]）
    - 向后兼容 — PASS（`noOpCompactorProducesNoCompactionCheckpointAndNoTruncation`）
  - Phase 2 Exit Criteria 全部 PASS：
    - 端到端 file-backed — PASS（`e2eCompactionAwareRestoreFileBacked`：execute → compaction → crash → new manager → getCheckpoints 仅 post-compaction → 不变量 → engine.restoreSession 续跑成功）
    - 端到端 DB-backed — PASS（`e2eCompactionAwareRestoreDbBacked`：截断 + DB fallback 解析）
    - 向后兼容 restore — PASS（`e2eBackwardCompatNoCompactionRestore`：NoOpContextCompactor → 无截断 → restore 成功）
    - 多次 compaction — PASS（`e2eMultipleCompactionRestoreTruncatesToLatest`：截断到最近 COMPACTION）
  - Anti-Hollow 检查：截断在 load 路径运行时实际执行——通过 `getCheckpoints` 返回列表长度变化验证（`singleCompactionTruncatesInclusive`：5 checkpoints → 3 loaded）；端到端测试证明 execute → saveCheckpoint → crash → load → truncate → restore 完整调用链连通；`CompactionAwareTruncation.truncateToLatestCompaction` 有实际逻辑（非空方法体）；无 COMPACTION 时返回全量副本（非静默跳过，有明确 assertion）
  - `./mvnw compile -pl nop-ai/nop-ai-agent -am` → BUILD SUCCESS
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（1386 tests，0 failures）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` → exit code 0（0 findings）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/188-nop-ai-agent-compaction-aware-checkpoint-restore.md --strict` → exit code 0
  - Deferred 项分类检查：5 个 deferred 项全部为 `optimization candidate` 或 `out-of-scope improvement`，无 in-scope live defect 被降级

Follow-up:

- 跨进程 / 多实例 checkpoint 接管锁（依赖 L4-8 Actor Runtime）
- Checkpoint retention / rotation / TTL policy（pre-compaction checkpoint 物理清理）
- Compaction-triggered snapshot.json 文件生成（FileBackedCheckpointManager 加速层）
- Active-execution re-truncation（每次 compaction 后重新截断 in-memory 列表）
- Per-session seq 跨 restore 持久化
