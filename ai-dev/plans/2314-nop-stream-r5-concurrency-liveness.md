# 2314 nop-stream R5：并发活性（CC-01/02/06/07）

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: ai-dev/audits/2026-10/2026-10-05-0523-deep-audit-nop-stream-quality-r5/04-concurrency-resource.md（CC-01/02/06/07 + 维度复核）；summary.md 修复归属 2314
> Related: 2313（已完成）

## Purpose

闭合 R5 审计的四个并发活性缺陷：failJob 不 cancel 已分配任务（孤儿任务+2PC 悬挂+运维堵死）、activateAsLeader 半激活 leader、WebhookAlertChannel 投递线程一次异常永久死亡、OpsJobManager governanceSweep 周期任务无异常防护。

## Current Baseline

- **CC-01（P1）**：`JobCoordinator.failJob`（:775）置 FAILED/active=false 但不 fan-out cancelTask（对称路径 stop()/terminateCancel 都走 `cancelAllAssignedTasks`），running 保持 true——孤儿任务 + 运维 remove/submit 堵死。
- **CC-02（P1）**：`activateAsLeader` 先置 `active=true` 再 rebuild；rebuild 失败留下半激活 leader（active=true 零 assignment），elector 吞异常 + 同 epoch 守卫短路重试 → 永久悬挂。
- **CC-06（P2）**：`WebhookAlertChannel.drainLoop` 无 per-event containment——单个投递异常杀死唯一投递线程。
- **CC-07（P3）**：`OpsJobManager.governanceSweep`（scheduleAtFixedRate）无 per-run 异常防护。
- 测试基线：2313 收口后 core/rocksdb/flow/runtime 全绿。

## Goals

- failJob 终态化完整：cancel fan-out + running=false。
- leadership-grant rebuild 失败可重试（active 回滚 false）。
- webhook 投递线程事件级 containment。
- governanceSweep 单次异常不停摆。

## Non-Goals

- CC-03/04/05/08/09/10/11/12（已修复或既有裁定长尾）；CC-11 已在 2312 KR-7 族同步修复。

## Scope

### In Scope

- runtime：`JobCoordinator`（failJob/activateAsLeader）、`WebhookAlertChannel`（drainLoop containment）、`OpsJobManager`（governanceSweep containment + sweepOnce 拆分）、相关测试。

### Out Of Scope

- alert 通道语义变更；elector 注入骨架。

## Execution Plan

### Phase 1 - CC-01 + CC-02：协调器终态与激活回滚

Status: completed
Targets: `runtime/.../coordinator/JobCoordinator.java`

- Item Types: `Fix`

- [x] failJob：置终态后 `running=false` + `cancelAllAssignedTasks("job failed")`（best-effort，不阻塞终态迁移）
- [x] activateAsLeader：`active=true` 移入锁内（rotation 后、prepareAssignments 前）；rebuild 异常 catch 回滚 `active=false` 后 rethrow
- [x] 回归钉子：TestFailJobCancelsAssignedTasks + coordinator 全家回归

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 回归钉子回退必红验证记录进日志（cc01_RED=1；restore 脚本曾半写入致假绿，修正后复验，见日志偏差①）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 全绿（1244/0F）
- [x] No owner-doc update required（内部活性修复）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - CC-06 + CC-07：周期/投递线程 containment

Status: completed
Targets: `runtime/.../alert/WebhookAlertChannel.java`、`runtime/.../ops/OpsJobManager.java`

- Item Types: `Fix`

- [x] drainLoop：per-event try/catch（InterruptedException 保持中断退出；其余异常 WARN 后继续）
- [x] governanceSweep 拆分 sweepOnce + 外层 containment（返回 0 + WARN）
- [x] 回归钉子：TestGovernanceSweepContainment

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 全绿
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects（CC-01/02/06/07）已修复
- [x] 所有 in-scope confirmed contract drifts 已收敛
- [x] 行为/契约结果已达成（终态完整取消、激活可重试、线程 containment）
- [x] 必要 focused verification 已完成（CC-01 变异回退必红 + coordinator 全家回归）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：failJob 的 cancel 走与 stop() 相同的 `cancelAllAssignedTasks`（测试断言计数==assignment 数）；containment catch 非 no-op（WARN + 继续）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 通过（1244/0F）
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2314-nop-stream-r5-concurrency-liveness.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-core --severity high` 退出码为 0

## Deferred But Adjudicated

（无——CC-11 已在 2312 处理，其余 CC 项为既有裁定长尾）

## Non-Blocking Follow-ups

- CC-02 的专设"半激活重试"E2E（需 elector 注入骨架）作为后续测试增强候选。

## Closure

Status Note: 四缺陷修复落地（failJob 终态取消+部署窗口关闭、activateAsLeader 激活回滚、drainLoop/sweep containment），coordinator 全家 131 + runtime 全量 1244 全绿，CC-01 变异回退必红验证。独立 closure audit APPROVE。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure auditor（general-purpose 子代理，2026-10-05）
- Evidence:
  - CC-01：TestFailJobCancelsAssignedTasks（cancel 计数==assignment、running=false、assignTasks 再部署被拒）+ 回退探针 cc01_RED=1
  - CC-02：activateAsLeader 激活点移入锁内 + catch 回滚（代码路径审计）；TestJobCoordinatorRestartStrategy/RecoveryRobustness/TerminationMatrix 全绿
  - CC-06/07：drainLoop/sweepOnce containment + TestGovernanceSweepContainment
  - 全量：_tmp/plan2314-full.log runtime 1244/0F BUILD SUCCESS
  - `check-plan-checklist.mjs --strict` 退出码 0；`scan-hollow-implementations.mjs` 退出码 0
  - Deferred 诚实性：无 in-scope 降级
- Follow-up: 半激活重试 E2E（见 Non-Blocking Follow-ups）
