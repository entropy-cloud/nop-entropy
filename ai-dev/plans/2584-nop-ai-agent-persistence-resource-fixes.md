# 2584 nop-ai-agent 持久化一致性与资源治理修复

> Plan Status: draft
> Last Reviewed: 2026-11-21
> Source: `ai-dev/analysis/2026-11/2026-11-21-monthly-new-code-deep-audit.md`（nop-ai Major/Minor 群）
> Related: `ai-dev/design/`（nop-ai-agent 子系统，若有 owner design 则随 Phase 同步）

## Purpose

修复 DBMessageService 死信/claim 的持久化原子性缺陷与 checkpoint journal 一致性缺陷，清理资源残留面。

## Current Baseline

- `DBMessageService.java:889-947`：deadLetter 的 DLQ INSERT 与主表 UPDATE 各自独立提交；INSERT 成功 UPDATE 失败后，毒消息每次 poll 重试且违反 DLQ 主键（AiAgentMessageTable.java:98）永久失败，重投上限失效。
- `DBMessageService.java:694-725`：claim 条件 UPDATE 与 gen 读取跨连接非原子（TOCTOU），与 5min stale sweep 重叠时 fencing 语义错位。
- `FileBackedCheckpointManager.java:342-363`：rewriteJournal 持 `rotateLock` 而 writer append 持 `ioLock`，互不覆盖 → rewrite 丢失并发 append；`Files.writeString` 截断式写非原子，中途崩溃损坏整个 journal。
- Minor：DefaultOrphanRecoveryHandler/DefaultSessionTimeoutHandler 裸 IllegalArgumentException；BoundedStreamPublisher 吞 RejectedExecutionException 无日志；DeferredAckMailbox sweep 后 ack 静默 false；AgentSessionLifecycle terminalStatuses 无界；FileBackedCheckpointManager.purgeTerminal 先 remove 后判 null。

## Goals

- deadLetter 原子化且 DLQ 写入幂等，毒消息上限真实生效。
- claim 的 gen 获取原子化；journal 并发一致 + 原子落盘。
- 错误处理规范与静默失败面收敛。

## Non-Goals

- 不改消息表 DDL 兼容性（不强制迁移已有库）；不动 sandbox 安全面（已验证良好）；不做 agent engine 行为变更。

## Scope

### In Scope

- nop-ai/nop-ai-agent：message/DBMessageService、reliability/FileBackedCheckpointManager + CheckpointJournalWriter、runtime/recovery 两 handler、session/lifecycle；nop-ai-core：BoundedStreamPublisher

### Out Of Scope

- nop-ai-core ChatServiceImpl/FailoverStreamFlow、toolkit ToolManagerImpl/DeltaVirtualFileSystem（审计 follow-up，另立分析）；Docker 容器残留 ops 边界（已裁定 watch-only）。

## Execution Plan

### Phase 1 - 持久化原子性（3 Major）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/FileBackedCheckpointManager.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/CheckpointJournalWriter.java`

- Item Types: `Fix`

- [ ] deadLetter：INSERT+UPDATE 单事务；DLQ 重复键视为已死信直接推进主表状态（幂等）
- [ ] claimMessage：gen 获取原子化（单语句 RETURNING/OUTPUT 或同事务条件读）
- [ ] rewriteJournal 纳入与 append 互斥的同一锁域；journal 落盘改 temp file + ATOMIC_MOVE

Exit Criteria:

- [ ] 回归钉：DLQ INSERT 成功 UPDATE 失败注入 → 消息最终离开 PENDING 且不无限重试
- [ ] 回归钉：rewrite 期间并发 append 不丢失（并发测试）；写中途异常后 journal 可读且无截断
- [ ] claim gen 原子性钉（sweep 竞争场景或单语句断言）
- [ ] `ib test -pl :nop-ai-agent,:nop-ai-core` 全绿
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - 规范与资源收敛（Minor 群）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/recovery/`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/BoundedStreamPublisher.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DeferredAckMailbox.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionLifecycle.java`

- Item Types: `Fix`

- [ ] 裸 IllegalArgumentException 统一为 NopAiAgentException + ErrorCode + .param（消息保持英文）
- [ ] BoundedStreamPublisher 提交失败至少 WARN 一次 + 持续失败 closeExceptionally fail-loud
- [ ] DeferredAckMailbox：被 sweep 移走的 deliveryId ack/nack 增加 WARN/计数
- [ ] terminalStatuses 设 LRU 上界 + 淘汰降级语义
- [ ] purgeTerminal 判空顺序修正

Exit Criteria:

- [ ] 全量 diff grep 无新增裸 IllegalArgumentException（recovery 路径）
- [ ] 上述每项 focused 测试覆盖（RejectionException 注入、sweep 后 ack、LRU 淘汰）
- [ ] `ib test -pl :nop-ai-agent,:nop-ai-core` 全绿
- [ ] owner doc 若登记过相关行为语义则同步，否则显式 No owner-doc update required
- [ ] `ai-dev/logs/` 已更新

## Closure Gates

- [ ] 3 个 Major 修复并有回归钉
- [ ] Minor 群全部落地或移入 Deferred But Adjudicated（附理由）
- [ ] focused tests 全绿
- [ ] 无 in-scope live defect 被降级
- [ ] 独立子 agent closure audit 完成，evidence 写入本文件
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0

## Deferred But Adjudicated

### Docker 容器 kill 失败人工清理边界

- Classification: `watch-only residual`
- Why Not Blocking Closure: 已有 owner 裁定登记为 ops boundary
- Successor Required: `no`

## Non-Blocking Follow-ups

- nop-ai-core ChatServiceImpl/FailoverStreamFlow、toolkit 大文件深审（另立分析任务）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
