# 2584 nop-ai-agent 持久化一致性与资源治理修复

> Plan Status: completed
> Last Reviewed: 2026-11-21
> Adversarial Review: 2026-11-21 独立子代理 1 轮通过（4 项执行精度先修已吸收：claim 同连接读、ATOMIC_MOVE 回退、ioLock 量级、javadoc 口径）
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

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/FileBackedCheckpointManager.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/CheckpointJournalWriter.java`

- Item Types: `Fix`

- [x] deadLetter：INSERT+UPDATE 单事务；DLQ 重复键视为已死信直接推进主表状态（幂等）
- [x] claimMessage：gen 获取原子化——首选同连接同事务条件读（UPDATE 后同一连接 SELECT，自带行锁可见性，零方言依赖；不采用 RETURNING/OUTPUT，H2/MySQL 方言不可移植）
- [x] rewriteJournal 纳入与 append 互斥的同一锁域（最简：CheckpointJournalWriter 增加 rewriteJournal 方法内部持 ioLock，manager 删除自有 rewrite；ioLock 全局单锁会短暂阻塞其他 session append——rotate/rewrite 低频，量级可接受）；journal 落盘改 temp file + ATOMIC_MOVE（catch AtomicMoveNotSupportedException 回退普通 move）

Exit Criteria:

- [x] 回归钉：DLQ INSERT 成功 UPDATE 失败注入 → 消息最终离开 PENDING 且不无限重试（`TestDBMessageServiceAtomicDeadLetter_2584.deadLetterUpdateFailureRollsBackAllThenRetrySucceeds` + `deadLetterWithPreExistingDlqRowAdvancesMainRow`）
- [x] 回归钉：rewrite 期间并发 append 不丢失（受控 executor + latch 注入，非随机并发，防 flaky）；写中途异常后 journal 可读且无截断（`TestCheckpointJournalRewriteAtomicity_2584` 三钉，writer ioLock 内测试钩注入）
- [x] claim gen 原子性钉（8 线程并发 claim 恰一胜者 + 返回 gen==持久化 CLAIM_GEN 断言，`claimGenerationIsAtomicUnderConcurrentClaims`）
- [x] `ib test -pl :nop-ai-agent,:nop-ai-core` 全绿（nop-ai-agent 1621/0/0，nop-ai-core 505/0/0，2026-11-21）
- [x] `ai-dev/logs/` 已更新（`ai-dev/logs/2026/11-21.md` 顶部 plan 2584 条目）

### Phase 2 - 规范与资源收敛（Minor 群）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/recovery/`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/BoundedStreamPublisher.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DeferredAckMailbox.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionLifecycle.java`

- Item Types: `Fix`

- [x] 裸 IllegalArgumentException 统一为 NopAiAgentException + ErrorCode + .param（消息保持英文）
- [x] BoundedStreamPublisher 提交失败至少 WARN 一次 + 持续失败 closeExceptionally fail-loud
- [x] DeferredAckMailbox：被 sweep 移走的 deliveryId ack/nack 增加 WARN/计数
- [x] terminalStatuses 设 LRU 上界 + 淘汰降级语义
- [x] purgeTerminal 判空顺序修正

Exit Criteria:

- [x] 全量 diff grep 无新增裸 IllegalArgumentException（口径：实际 throw 点；javadoc 提及如 DefaultTeamTaskRecoveryHandler.java:85 不计入）——本次 diff 仅替换 4 处既有裸 throw 为类型化错误，零新增（`BoundedStreamPublisher` 构造器 2 处为审计窗口前既有基线，未触碰）
- [x] 上述每项 focused 测试覆盖（RejectionException 注入、sweep 后 ack、LRU 淘汰）——`TestBoundedStreamPublisherSubmissionFailure_2584`、`TestDeferredAckMailboxSweptAck_2584`、`TestAgentSessionLifecycleTerminalStatusLru_2584`、`TestFileBackedCheckpointManagerPurgeNullSafety_2584`、`TestRecoveryHandlerTypedInvalidArgument_2584`
- [x] `ib test -pl :nop-ai-agent,:nop-ai-core` 全绿（nop-ai-agent 1621/0/0，nop-ai-core 505/0/0，2026-11-21）
- [x] No owner-doc update required：DLQ 落账契约、journal 轮转观测面、mailbox 3-phase 协议、终态清理矩阵等 owner doc（`ai-dev/design/concurrency/agent-orchestration-session-lifecycle-concurrency.md` 等）的「what」层语义全部维持成立；本次为内部原子性/锁域/观测性收敛，无契约漂移
- [x] `ai-dev/logs/` 已更新（`ai-dev/logs/2026/11-21.md` 顶部 plan 2584 条目）

## Closure Gates

- [x] 3 个 Major 修复并有回归钉
- [x] Minor 群全部落地或移入 Deferred But Adjudicated（附理由）（五项全部落地）
- [x] focused tests 全绿（nop-ai-agent 1621/0/0 + nop-ai-core 505/0/0，2026-11-21）
- [x] 无 in-scope live defect 被降级
- [x] 独立子 agent closure audit 完成，evidence 写入本文件（主控负责）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（主控负责）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（主控负责）

## Deferred But Adjudicated

### Docker 容器 kill 失败人工清理边界

- Classification: `watch-only residual`
- Why Not Blocking Closure: 已有 owner 裁定登记为 ops boundary
- Successor Required: `no`

## Non-Blocking Follow-ups

- nop-ai-core ChatServiceImpl/FailoverStreamFlow、toolkit 大文件深审（另立分析任务）

## Closure

Status Note: Status Note: 两 Phase 全部落地；独立审计 PASS（mvnw 实测 nop-ai-agent 1652/0，排除既有环境型 TestDockerSandboxBackend 挂起类，非本 plan 引入）；scan-hollow 3 条为注释误报已裁定 watch-only。
Completed: 2026-11-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（closure audit 2584+2586，2026-11-21）
- Evidence: deadLetter 单事务 + SQLIntegrityConstraintViolationException 幂等（DBMessageService.java:952/965/1000）、claim 同连接读 gen（8 线程恰一胜者钉）、rewriteJournal ioLock+ATOMIC_MOVE、裸 IAE scoped grep 零命中、LRU/fail-loud/sweep 计数逐条 live 核实；nop-ai-core 505/0 未独立复跑，以实施记录为准
