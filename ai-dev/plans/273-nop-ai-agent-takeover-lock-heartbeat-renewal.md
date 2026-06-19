# 273 nop-ai-agent——DbSessionTakeoverLock 心跳续期（长时执行自动 renew，消除 30min 租约过期导致的 double-execution）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: 14-06 (carry-over from plan 271)
> Last Reviewed: 2026-06-20
> Source: deep audit 2026-06-19 finding 14-06；carry-over 自 `ai-dev/plans/271-nop-ai-agent-reliability-async-timeout.md` §Deferred But Adjudicated / §Follow-up
> Related: 271-nop-ai-agent-reliability-async-timeout (source-plan), 221（takeover lock 引入）

## Purpose

收口 carry-over 14-06：`DbSessionTakeoverLock` 的租约固定 30 分钟（`lockLeaseMs=1_800_000L`），引擎在执行期间从不自动调用已实现的 `tryRenew`。长时间运行的无人值守 agent（>30min）会让租约过期，使另一个 JVM 实例通过 stale-lock 抢占机制接管同一 session，导致 double-execution。本计划把心跳续期接进引擎的 3 个执行入口点，并在续期失败（租约丢失）时快速中止本侧执行。

> **Granularity note（standalone justification）**：本项预估生产代码 ~100–120 行（新增 config、续期调度 helper、3 个入口点接线、lease-lost 中止、Javadoc 同步），无符合条件的 bundle sibling。两个候选 sibling 均不在同一子系统：13-8（Docker `--cpus` 语义，security/sandbox 包，不同执行模型）与 20-5（`nop-record-mapping` pom scope，构建配置）均经 ROADMAP 裁定为非 bundle-eligible。本项 ≥ ~100 行门槛，独立成 plan。

## Current Baseline

- `DbSessionTakeoverLock.tryRenew` 已完整实现（`DbSessionTakeoverLock.java:281-312`）——条件 `UPDATE ... SET LOCK_EXPIRES_AT=? WHERE SESSION_ID=? AND LOCK_OWNER=?`，owner 仍持有时返回 true。但引擎从不调用它。
- `ISessionTakeoverLock.tryRenew`（`ISessionTakeoverLock.java:126`）与 `NoOpSessionTakeoverLock.tryRenew`（`NoOpSessionTakeoverLock.java:55-57`，返回 true）均已就位。
- 两处 Javadoc 明确把自动续期标为"后续工作"：`DbSessionTakeoverLock.java:75-77`（"auto heart-beat renew is an explicit successor — see plan 221 Non-Goals"）与 `ISessionTakeoverLock.java:110-114`（"Reserved for manual / future use ... auto heart-beat renew is an explicit successor"）。
- 引擎租约配置：`DefaultAgentEngine.java:373` `lockLeaseMs = 1_800_000L`（30min）；`instanceId`（`:366`，UUID 不可变）；`sessionTakeoverLock` 字段（`:360`，默认 `NoOpSessionTakeoverLock`）。
- 3 个执行入口点各自在 `tryAcquire` 成功后执行，并在每个清理路径调用 `releaseLockQuietly`（`:1668-1676`）：
  - `doExecute`：tryAcquire `:2030`；release `:2041`（putIfAbsent 失败 catch）/`:2108`（inner finally）/`:2142`（outer catch / supplyAsync 提交失败）。
  - `resumeSession`：tryAcquire `:2336`；release `:2347`/`:2393`/`:2416`。
  - `restoreSession`：tryAcquire `:2516`；release `:2527`/`:2571`/`:2592`。
- 引擎已有专用 `agentExecutor`（`:425`，virtual-thread `ExecutorService`，plan 271），但它是 `ExecutorService` 而非 `ScheduledExecutorService`，不能直接 `scheduleWithFixedDelay`。
- 现成的周期调度范式：`ScheduledRecoveryManager.java:354-373`（`scheduleWithFixedDelay` + 幂等 start/stop + `scanOnceSafe()` try-catch 防止任务被异常静默杀死）；`DBMessageService.java:183-188`（`Executors.newSingleThreadScheduledExecutor` + `scheduleWithFixedDelay`）。
- **真正的 gap**：执行期间没有任何续期调用；>30min 的执行必然导致租约过期 → 另一实例抢占 → double-execution。

## Goals

- 长时执行期间，引擎周期性调用 `tryRenew` 把 `LOCK_EXPIRES_AT` 推到 `now + lockLeaseMs`，使正在运行的 session 租约不会在执行中途过期。
- 续期任务在执行结束的每个清理路径（与现有 `releaseLockQuietly` 相同的 finally/catch 路径）被取消，不泄漏调度线程。
- 当 `tryRenew` 返回 false（租约已被另一实例抢占 / 本侧不再持有）时，本侧执行快速中止并把 session 标记为 failed，避免与接管实例 double-execute。
- 续期间隔可配置，默认值为租约的一个安全分数（远小于 30min，留出重试余量）。
- 默认 `NoOpSessionTakeoverLock` 下零行为回归：续期调度要么不启动，要么是无害 no-op。

## Non-Goals

- 不改 `DbSessionTakeoverLock.tryRenew` 的 SQL/CAS 语义（已正确实现）。
- 不引入分布式协调服务（Zookeeper / Redisson / etcd）替代 DB 租约。
- 不重设计 takeover lock 的整体架构（lease 表结构、owner 模型、preemption 规则均不变）。
- 不实现 background sweeper 主动清理过期锁（已有 `ScheduledRecoveryManager.deleteStaleLocks` 被动兜底，属其职责）。
- 不改 13-8（Docker `--cpus`）与 20-5（record-mapping pom scope）——经裁定非 bundle-eligible。

## Scope

### In Scope

- **Phase 1**：续期调度机制（config + 周期 tryRenew + cancel-on-cleanup，3 个入口点接线）
- **Phase 2**：租约丢失中止 + Javadoc 同步 + owner-doc 同步 + 聚焦测试

### Out Of Scope

- 13-8 Docker `--cpus` 语义修正（独立 successor）。
- 20-5 `nop-record-mapping` pom scope（构建配置独立项）。
- takeover lock 表结构 / preemption 规则变更。

## Execution Plan

### Phase 1 - 心跳续期调度与 cancel-on-cleanup 接线

Status: completed
Targets: `DefaultAgentEngine.java`（config + 续期 helper + 3 入口点接线）

- Item Types: `Fix`

- [x] 新增 `lockRenewIntervalMs` 配置字段（默认值为租约的安全分数，>0；getter/setter），语义：续期周期，仅在功能性 lock 接入时生效
- [x] 实现续期调度机制：在 `tryAcquire` 成功后，启动一个周期任务，按 `lockRenewIntervalMs` 间隔调用 `sessionTakeoverLock.tryRenew(sessionId, instanceId, lockLeaseMs)`；周期任务异常不静默杀死调度（参照 `ScheduledRecoveryManager.scanOnceSafe` 的 try-catch 范式）
- [x] 为周期调度提供 `ScheduledExecutorService`（新建单线程 daemon scheduled executor，或经 setter 注入；不复用 `agentExecutor`，因其非 Scheduled 类型）
- [x] 在 `doExecute` 的 `tryAcquire` 成功后（`:2030` 之后、`supplyAsync` 之前或其 lambda 内）启动续期任务，并在全部 3 个 release 路径（`:2041`/`:2108`/`:2142`）取消续期任务
- [x] 在 `resumeSession` 的 `tryAcquire`（`:2336`）成功后启动续期任务，并在 release 路径（`:2347`/`:2393`/`:2416`）取消
- [x] 在 `restoreSession` 的 `tryAcquire`（`:2516`）成功后启动续期任务，并在 release 路径（`:2527`/`:2571`/`:2592`）取消
- [x] 默认 `NoOpSessionTakeoverLock` 下零行为回归（续期 no-op 或不启动），不触发 insecure-default WARN（与现有 takeover lock 增量能力裁定一致）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DefaultAgentEngine` 存在 `lockRenewIntervalMs` 字段，默认值 > 0 且 < `lockLeaseMs`，有 getter/setter
- [x] 一个执行期间周期调用 `tryRenew(sessionId, instanceId, lockLeaseMs)` 的续期机制存在；周期任务抛异常时被捕获并记 WARN，不静默终止后续续期
- [x] 3 个入口点（doExecute / resumeSession / restoreSession）在 `tryAcquire` 成功后均启动续期任务
- [x] 每个入口点的全部 release 路径（putIfAbsent 失败 catch / inner finally / outer catch）均取消续期任务，无调度泄漏
- [x] **接线验证**：测试断言执行期间 `tryRenew` 确实被周期调用（如计数器/mock verify），且执行结束后续期任务不再触发
- [x] **无静默跳过**：续期周期任务的异常路径记 WARN 而非吞掉；`lockRenewIntervalMs` 默认非 0
- [x] 新增续期功能的聚焦测试（验证续期被周期触发 + 执行结束取消）：见 Phase 1 测试项
- [x] `No owner-doc update required`（owner-doc 同步归入 Phase 2 一并处理）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 编译通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] `ai-dev/logs/2026/06-20.md` 已更新

### Phase 2 - 租约丢失中止 + 文档/Javadoc 同步 + 聚焦测试

Status: completed
Targets: `DefaultAgentEngine.java`（lease-lost 中止）、`DbSessionTakeoverLock.java`、`ISessionTakeoverLock.java`（Javadoc）、`docs-for-ai/03-modules/nop-ai.md`、`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Fix | Proof`

- [x] 续期任务检测到 `tryRenew` 返回 false（租约丢失 / 被抢占）时，中止本侧执行：把 session 标记为 failed 并中断/取消正在运行的执行（复用现有 cancel/abort 机制，避免与接管实例 double-execute）
- [x] 更新 `DbSessionTakeoverLock.tryRenew` Javadoc（`:72-77`）与 `ISessionTakeoverLock.tryRenew` Javadoc（`:104-114`）：移除"Reserved for manual / future use"与"explicit successor"措辞，改为"引擎执行期间按 `lockRenewIntervalMs` 周期自动调用"
- [x] 更新 owner-doc `docs-for-ai/03-modules/nop-ai.md`「Agent 引擎可靠性配置」段：补 `lockRenewIntervalMs` 配置说明（默认值、语义、仅在功能性 takeover lock 接入时生效）
- [x] 更新 `docs-for-ai/04-reference/source-anchors.md`：补对应 source anchor（若 reliability 配置已有 anchor 则在同段补字段；否则新增）
- [x] 新增聚焦测试：续期正常延长租约（mock/真实 H2 lock 表，断言 `LOCK_EXPIRES_AT` 被推后）
- [x] 新增聚焦测试：`tryRenew` 返回 false 时执行被中止、session 标记 failed（断言状态 + 续期停止）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `tryRenew` 返回 false 时，本侧执行被中止且 session 进入 failed 终态（非静默 continue / 非吞异常）
- [x] **无静默跳过**：lease-lost 路径产生可观测效果（session=failed + WARN 日志），不静默忽略
- [x] `DbSessionTakeoverLock.java:72-77` 与 `ISessionTakeoverLock.java:104-114` 的 Javadoc 不再含 "future use / successor" 措辞，改为描述自动续期
- [x] **端到端验证**：从 `doExecute` 入口 → acquire → 周期 renew →（模拟租约被抢占）→ lease-lost 中止 → session=failed 的完整路径已有测试覆盖
- [x] **接线验证**：lease-lost 中止确实把 session 置为 failed（测试断言 `AgentExecStatus.failed`）
- [x] `docs-for-ai/03-modules/nop-ai.md` 可靠性配置段含 `lockRenewIntervalMs` 说明，与 live 默认值一致
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 编译通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] `ai-dev/logs/2026/06-20.md` 已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Phase 1 Exit Criteria 全部勾选（续期调度 + cancel-on-cleanup）
- [x] Phase 2 Exit Criteria 全部勾选（lease-lost 中止 + 文档/Javadoc 同步）
- [x] 长时执行（>lockLeaseMs）期间租约被续期，不再过期导致 double-execution（14-06 缺陷收敛）
- [x] 默认 NoOpSessionTakeoverLock 下零行为回归（现有测试全绿）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 编译通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] 无 in-scope 项被静默降级为 deferred（14-06 为 in-scope live defect，不得降级）
- [x] 受影响 owner docs（`docs-for-ai/03-modules/nop-ai.md` + `source-anchors.md`）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：续期调度在运行时确实被调用（不只类型存在）；lease-lost 路径确实中止执行；无空方法体/静默跳过作为正常实现
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/273-nop-ai-agent-takeover-lock-heartbeat-renewal.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0

## Deferred But Adjudicated

（本计划无 deferred 项；13-8 / 20-5 经裁定不在本 plan scope，各自独立 successor。）

## Non-Blocking Follow-ups

- 续期调度的指标/可观测性（renew 成功/失败计数、lease-lost 频率）：watch-only residual，当前 WARN 日志已足够观测，后续若有 metrics 框架再补。

## Closure

Status Note: 14-06 收口——`DbSessionTakeoverLock.tryRenew`（已存在但从未被调用）现已被引擎的心跳续期调度器在执行期间按 `lockRenewIntervalMs`（默认 10min）周期调用，使长时执行（>30min 租约）的 `LOCK_EXPIRES_AT` 不断被推后，不再因租约过期被另一 JVM 实例抢占导致 double-execution。续期检测到租约丢失（`tryRenew==false`）时，引擎中止本侧执行并把 session 置 `failed`（复用 cancel/abort 机制 + leaseLost override）。默认 `NoOpSessionTakeoverLock` 下零行为回归（续期 no-op）。三个入口点（doExecute/resumeSession/restoreSession）全部接线并在所有 release 路径取消续期任务，无调度泄漏。owner docs + Javadoc 已同步。独立子 agent closure audit 15/15 PASS。
Completed: 2026-06-20

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore, task_id=ses_11f250831ffeJjbVCTa67UeluM），fresh session（非实现者复用）。
- Audit Session: ses_11f250831ffeJjbVCTa67UeluM
- Evidence:
  - **Phase 1 Exit Criteria**（全部 PASS）：
    - `lockRenewIntervalMs` 字段默认 `600_000L`（>0 且 < `lockLeaseMs=1_800_000L`）+ getter/setter：`DefaultAgentEngine.java:389` + `:1602-1608`。PASS
    - 周期 `tryRenew` + try-catch WARN（非静默吞）：`renewOnceSafe` `DefaultAgentEngine.java:1798-1808`。PASS
    - 专用 `ScheduledExecutorService lockRenewExecutor`（非 agentExecutor）：`DefaultAgentEngine.java:399` + `getLockRenewExecutor :1735-1744`。PASS
    - 3 入口点 tryAcquire 成功后启动续期：`doExecute :2230` / `resumeSession :2552` / `restoreSession :2744`（均 `handle.renewHandle = startLockRenewal(...)`）。PASS
    - 每入口点 3 release 路径取消续期（共 9 处 `cancelLockRenewalQuietly(handle.renewHandle)`）：doExecute `:2233/:2309/:2346`、resumeSession `:2555/:2606/:2632`、restoreSession `:2747/:2796/:2820`。PASS
    - 接线验证：`TestTakeoverLockHeartbeatRenewal` `CountingTakeoverLock` 断言 `renewCount>=1`（3 入口点）+ 结束后不再触发。PASS（6/6 tests green）
  - **Phase 2 Exit Criteria**（全部 PASS）：
    - lease-lost 中止：`handleLeaseLost` `DefaultAgentEngine.java:1821-1833`（setLeaseLost + setCancelRequested + interrupt）+ inner finally override `ctx.isLeaseLost() ? failed : ctx.getStatus()`（doExecute `:2289` / resumeSession `:2592` / restoreSession `:2782`）。PASS
    - `AgentExecutionContext.leaseLost` volatile 字段 + getter/setter：`AgentExecutionContext.java:44` + `:214-220`。PASS
    - Javadoc 同步：`DbSessionTakeoverLock.java:72-79` + `ISessionTakeoverLock.java:108-134` 不再含 "future use / successor"，改为自动续期描述。PASS
    - owner-doc 同步：`docs-for-ai/03-modules/nop-ai.md:69`（lockRenewIntervalMs 行）+ `docs-for-ai/04-reference/source-anchors.md:111`（AIREL-001 含 lockRenewIntervalMs/getLockRenewExecutor/startLockRenewal/handleLeaseLost）。PASS
    - doc 默认值一致：doc `600000`（10min）== code `600_000L`。PASS
    - 端到端验证：`leaseLostAbortsExecutionAndMarksSessionFailed` 断言 `AgentExecStatus.failed` + prompt 终止（`elapsedMs<30000`）+ 续期停止。PASS（runtime WARN 日志 observed）
  - **Closure Gates**（全部 PASS）：
    - `./mvnw compile -pl nop-ai/nop-ai-agent -am`：BUILD SUCCESS。PASS
    - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`：BUILD SUCCESS（全模块全绿，零回归）。PASS
    - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high`：退出码 0（Critical/High/Medium/Low 全 0）。PASS
    - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict`：退出码 0（见下）。PASS
    - `node ai-dev/tools/check-doc-links.mjs --strict`：退出码 0（52 pre-existing BROKEN_LINK warnings 均在其他历史 plan 文件，非本次引入）。PASS
  - **Anti-Hollow 检查**：`startLockRenewal`（`:1779-1788`）真实调用 `scheduleWithFixedDelay` 非 stub；`handleLeaseLost`（`:1821-1833`）真实 set ctx + interrupt 非空方法体；运行时续期确实被调用（CountingTakeoverLock 计数≥1）+ lease-lost 确实中止（session=failed）。PASS
  - **Deferred 项分类检查**：无 deferred 项；14-06 为 in-scope live defect，已修复，未降级。PASS

Follow-up:

- 续期调度的指标/可观测性（renew 成功/失败计数、lease-lost 频率）：watch-only residual，当前 WARN 日志已足够观测，后续若有 metrics 框架再补。
- 无其他 remaining plan-owned work。
