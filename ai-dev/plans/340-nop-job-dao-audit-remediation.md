# 340 nop-job-dao 审计发现修复 + 设计文档补全

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Source: nop-job-dao 三路独立深度审计（store 层 / FSM helper 层 / entity 生命周期，2026-08-12）；审计发现合并去重后 1×P1 + 9×P2 + ~15×P3
> Related: `ai-dev/plans/338-nop-job-timeout-checker-cursor-pagination.md`（completed，cursor 分页引入源头）、`ai-dev/plans/339-nop-job-dispatch-routing-orthogonalization.md`（completed）、`ai-dev/design/nop-job/*`

## Purpose

把 nop-job-dao 三路审计发现的 1 个 P1（生产可触发的长任务误杀）+ 9 个 P2（游标分页方向反转、计数漂移、超时语义错配、契约分歧等）收口到"代码行为正确 + 设计文档与 live baseline 一致"的状态。H2 测试环境遮蔽问题（空库初始化，非本 plan 能修）移入 successor plan。

## Current Baseline

### 已成立的事实（live repo 核对，2026-08-12 三路审计）

- **状态机/helper 层语义迁移零漂移**：9 处 `_NopJobCoreConstants` 裸比较迁移到 `JobFireStateMachine`/`JobTaskStateMachine` helper，逐一核对 null 处理 / `==` vs `>=` / Integer 拆箱均与迁移前等价。FSM 内部自洽（fire WAITING=0/DISPATCHING=10/RUNNING=20/SUCCESS=30/FAILED=40/TIMEOUT=50/CANCELED=60；task WAITING=0/CLAIMED=10/SUSPICIOUS=15/RUNNING=20/+ 终态 30/40/50/60；schedule DISABLED=0/ENABLED=10/PAUSED=20/COMPLETED=30/ARCHIVED=40）。
- **VERSION 乐观锁挡住了最危险的并发问题**：`tryLockFiresForDispatch` / `tryLockTasksForExecute` / `tryLockSchedulesForPlan` 用 `tryUpdateManyWithVersionCheck` CAS，多实例不会 double-claim/duplicate-execute。
- **生命周期 21 条迁移路径 19 条有测试覆盖**（`TestJobE2E`/`TestJobCoordinatorScanner`/`TestJobConcurrency`/`TestJobTimeoutChecker`/`TestJobWorkerScanner`/`TestJobStoreImpl`/`TestNopJobFireBizModel` 等）。
- **`@SingleSession` + dirty-flush 是完成/取消路径的既定设计**：`JobCompletionProcessorImpl.completeSingleFire:122,199-200` 与 `JobFireStoreImpl.completeFireAndUpdateSchedule:122-142` 均显式注释——`@SingleSession` 下 `requireEntityById` 返回缓存实体，reload 重试无意义，dirty 字段在 `@Transactional` commit 时 flush。**任何"reload + 有界重试"方案都与该设计冲突，不可行**（plan review B2）。

### 真正剩余的 gap（审计发现，按严重度）

**P1（生产风险）**
- **P1-1** `JobTimeoutCheckerImpl.java:446-457`（核对：`:452-453` 的 else 分支 `effectiveTimeoutMs = dispatchTimeoutMs`）：RUNNING task 执行超时在 `schedule.timeoutSeconds` 为空且 `executionTimeoutMs≤0` 时**回退到 `dispatchTimeoutMs`（默认 300000ms）**。未配 per-schedule 超时的长任务（>5min）被"派发超时"误杀。语义错配：`dispatchTimeoutMs` 是"fire 卡在 DISPATCHING"的超时。

**P2（实质缺陷）**
- **P2-1** 游标分页方向反转（3 处同款 bug，核对行内容一致）：`JobTaskStoreImpl.java:110-111`（`fetchRunningTasks`）、`:173-174`（`resetStaleWaitingTasks`）、`JobFireStoreImpl.java:255-256`（`fetchDispatchingFires`）。`addOrderField(x, false)`（ASC）+ `lt` 游标谓词 → 批次 2 重复批次 1 的行，`drainBatch` 因 `size<batchSize` 提前结束。结果：每个扫描周期只处理最老的 ~batchSize 行。
- **P2-2** `JobScheduleStoreImpl.java:207-210`：`recoveryFireAndAdvanceSchedule` 在 fire 不可恢复时不推进 `nextFireTime` 即 return → 5s 紧重试循环无退避。
- **P2-3** `JobFireStoreImpl.java:291`：`failFireWithoutSchedule` 丢弃 `tryUpdateWithVersionCheck` 返回值 → 版本冲突时 fire 静默留 DISPATCHING，超时检查器无限重入。
- **P2-4** `activeFireCount` 计数漂移：`JobFireStoreImpl.java:139`（complete 的 `fireOnly()` 返回）与 `:205`（cancel 的 `fireOnly()` 返回）处，fire 更新提交但 schedule 版本冲突→计数不递减；`Math.max(0,…)` 防负不自愈，长期累积可阻塞 DISCARD/OVERLAY/RECOVERY。**因 `@SingleSession` 约束，漂移点本身不可有界重试**（见上），需独立会话的对账机制收敛。
- **P2-5** 两个同名 `cancelFire` 副作用分歧：`JobFireStoreImpl.cancelFire:146`（fire+tasks+schedule 计数）vs `JobScheduleStoreImpl.cancelFire:417`（仅 fire）。审计初判 merge 形态更干净，但 plan review B2 发现 public 方法是 `@Transactional(REQUIRES_NEW)`、overlay 循环逐个调它产生 N 个嵌套新事务破坏原子性，最终裁定 **rename**（见 §2.6）。
- **P2-6** 完成路径不可观测：`JobCompletionProcessorImpl.completeSingleFire` 依赖 `@SingleSession` dirty-flush（设计正确），但版本冲突在 commit 时抛 `OptimisticLockException`，被外层 `catch(Exception):110` 吞为通用 `fire-complete-failed` warn，计数漂移不可观测。
- **P2-7** `JobScheduleStoreImpl.java:196-219` recovery 死代码：先 mutate `failedFire`（196-204）再 reload `freshFire` 重设同字段，前者从不持久化，误导维护。
- **P2-8** 无 LIMIT 查询：`JobScheduleStoreImpl.java:363`（`findActiveFires`）、`JobFireStoreImpl.java:306`/`JobTaskStoreImpl.java:116`（`findTasksByFireId`）等，overlay/广播风暴下无界。
- **P2-9** `TriggerSpecHelper.java:46-48`：fixed-rate 首次触发相位锚定错误，`lastFireTime==null` 时 `getLastScheduledTime()` 返回 0（非负），消费方 `PeriodicTrigger` 的 `start<0` fallback 成死代码，首次 fire 锚到 epoch 0、skip 后网格漂移。**根因在消费方对负值的解释**，需联动核对。

**关键元问题**
- **H2 测试环境故障遮蔽 bug**（**非本 plan 范围**）：`ai-dev/logs/2026/08-11.md:121` 记载 `Table "NOP_JOB_SCHEDULE" not found (this database is empty)`——**空库初始化问题**（AutoTest/H2 在单模块隔离运行下 schema 未初始化），**非列缺失/列漂移**。重生成 `_app.orm.xml` 不能修复。本 plan 仅记录诊断，修复移入 successor plan。

## Goals

- P1-1 修复：RUNNING task 执行超时不再回退到 dispatch 超时；未配超时的长任务不被误杀。
- P2-1 修复：3 处游标分页方向改为 DESC；以 mock 级游标推进测试 + 代码核对验证（真 DB 集成测试 gated on successor H2 plan）。
- P2-2 ~ P2-9 修复：recovery 退避、版本冲突可观测、计数漂移经**独立会话对账器**收敛、cancelFire 契约澄清（rename `markFireCanceledOnly`）、完成路径冲突可观测、死代码清理、无界查询加 LIMIT、fixed-rate 相位锚定。
- 设计文档补全：timeout 语义、store 游标分页契约、@SingleSession 约束下的计数对账策略、recovery 语义、cancelFire 单一契约写入 `ai-dev/design/nop-job/` 对应文档；审计记录写入 `ai-dev/audits/2026-08/`。
- H2 遮蔽问题：诊断记录 + 移交 successor plan（不在本 plan 修）。
- 独立子 agent closure audit 通过，证据写入 plan。

## Non-Goals

- **不改 ORM 模型结构**（`model/*.orm.xml`）：死列清理（fire `taskCostCpu/Memory`、task `workerAddress/progress/progressMessage`、fire `retryRecordId` + 索引 `IX_NOP_JOB_FIRE_RETRY`）与新增索引（`startTime` 覆盖索引）属 ORM 模型结构变更，受 Protected Area `plan-first` 约束，移入 `Deferred But Adjudicated`。
- **不修 H2 测试环境**（空库初始化问题）：移入 successor plan。
- **不改 `@SingleSession` 既定设计**：完成/取消路径的 dirty-flush 是 deliberate 设计，本 plan 在其约束内工作（用独立会话对账器收敛漂移，不强制造路径内 reload 重试）。
- 不改 `IJobTaskBuilder`/dispatcher 路由（plan 339 已完成）；不改状态机 helper（审计确认零漂移）；不改 worker invoker / reserved-capacity / 多租户隔离模型。

## Scope

### In Scope

- 审计记录 + 设计文档补全（timeout / store 契约 / @SingleSession 计数对账 / recovery / cancelFire rename 契约）。
- P1-1、P2-1 ~ P2-9 代码修复 + 测试（含新增独立会话计数对账器）。
- 低风险纯代码 P3（`findTasksByFireId` 去重、`enforceAttribution` 空值语义、`startTimeOrNow` 命名、recovery 冗余 mutation 随 P2-7 一并清理）。
- H2 遮蔽问题诊断记录（不修）。
- 全量回归（mock 测试）+ 独立子 agent closure audit。

### Out Of Scope

- ORM 模型结构变更（死列删除、索引增删）→ 后续 plan。
- H2 测试环境修复（空库初始化）→ successor plan 340 的 sibling。
- 状态机 helper 重构、worker invoker / reserved-capacity / 多租户隔离模型。
- CANCELED 触发重试/告警的路由调整（helper 审计标 P3，预存在）。

## Execution Plan

### Phase 1 - 设计文档补全 + 审计记录

Status: completed
Targets: `ai-dev/audits/2026-08/340-nop-job-dao-deep-audit/`、`ai-dev/design/nop-job/timeout-and-recovery-design.md`（新增）、`ai-dev/design/nop-job/store-contract-design.md`（新增）、`docs-for-ai/03-modules/nop-job.md`（补 timeout/counter 段）

- Item Types: `Decision`、`Follow-up`

Phase 1 先把"正确行为应该是什么 + 关键设计裁定"落到 design 文档，作为 Phase 2 代码修复的规格依据。

- [x] **1.1** 写审计记录：`ai-dev/audits/2026-08/340-nop-job-dao-deep-audit/summary.md` + `store-layer.md` + `fsm-helpers.md` + `entity-lifecycle.md`，含合并去重后的完整 findings 表（ID/Sev/File:line/Description/Evidence/Fix）与 H2 遮蔽元问题。
- [x] **1.2**（Decision）`ai-dev/design/nop-job/timeout-and-recovery-design.md` 新增，含三处关键裁定：
  - **三层超时语义边界**：`dispatchTimeoutMs`（fire 卡在 DISPATCHING）/ `executionTimeoutMs`（task 执行墙钟）/ `schedule.timeoutSeconds`（per-schedule 覆盖）；
  - **裁定：默认无执行超时**——`executionTimeoutMs≤0` 且 `schedule.timeoutSeconds` 为空时 RUNNING task 不超时，依赖 worker-liveness SUSPICIOUS→TIMEOUT 兜底（不再回退 dispatchTimeoutMs）；
  - **recovery 退避**：不可恢复 fire 仍推进 nextFireTime（消除 5s 紧循环）；`failFireWithoutSchedule` 版本冲突须可观测（warn）；
  - **@SingleSession 约束下的计数对账**：解释为何完成/取消路径内 reload 重试无意义（缓存实体），改由**独立会话的计数对账器**（`JobScheduleCounterReconciler`，non-`@SingleSession`）周期性按 live fire 计数重算 `activeFireCount` 收敛漂移。
  - **对账器规格**：扫描间隔复用 planner 的 `scan-interval-ms`（避免新引入线程/配置）；算法为按 schedule 聚合 `count(fireStatus<terminal)` 单次查询、与 `schedule.activeFireCount` 比对、mismatch 则 `tryUpdateWithVersionCheck` 修正；**写回风暴防护**——单次扫描每 schedule 至多一次写回，遇版本冲突不重试（warn + 留待下个周期），避免热 schedule 上的写竞争；**deviation 说明**：现有 5 个 scanner 均 `@SingleSession`（`AbstractBatchScanner:24` 约定），对账器是刻意的例外，因其在独立会话里重算需要读 live（非缓存）计数。
- [x] **1.3**（Decision）`ai-dev/design/nop-job/store-contract-design.md` 新增，含四处裁定：
  - **游标分页契约**：DESC 排序 + `lt` 游标谓词 + `drainBatch` 推进规则（附"ASC+lt 会重复批次 1"反例）；
  - **cancelFire 契约（裁定 rename 非 merge）**：`JobScheduleStoreImpl.cancelFire(fire, time):417`（私有，仅 fire）**改名 `markFireCanceledOnly`** 消除与 `IJobFireStore.cancelFire(jobFireId)`（public，fire+tasks+schedule 计数原子）的名称碰撞。**拒绝 merge**：public 方法是 `@Transactional(REQUIRES_NEW)`，overlay 循环逐个调它产生 N 个嵌套新事务破坏原子性，且 overlay"聚合一次 schedule 更新"更高效（详见 `store-contract-design.md` §2 的 tx 边界论证）。overlay/manual 调用点（`overlayFireAndAdvanceSchedule:124-134`、`insertManualFire:255-263`）改调 `markFireCanceledOnly`；归因 error code 经方法名区分（markFireCanceledOnly=OVERLAID，public cancelFire=CANCELED）；
  - **无界查询防御性 LIMIT**：`findActiveFires` `setLimit(500)`、`findTasksByFireId` `setLimit(1000)`，超限日志；
  - **`enforceAttribution` 空值语义裁定**：`enforceAttribution && workerInstanceId==null` 视为配置错误显式 throw（不静默跳过归因）。
- [x] **1.4** `docs-for-ai/03-modules/nop-job.md` 补 "超时与计数" 段：用户可见行为（长任务默认不超时、计数漂移由对账器自愈、cancelFire 原子契约）。**counter-drift 内容只进 `store-contract-design.md`，不进 `retry-integration-design.md`**（后者仅管 retryPolicyId↔nop-retry 桥）。

Exit Criteria:

- [x] 审计记录 4 个文件已写；以下 3 条抽样核对 file:line 与 live 代码匹配：P1-1 `JobTimeoutCheckerImpl.java:452-453`、P2-1 `JobTaskStoreImpl.java:110-111`、P2-3 `JobFireStoreImpl.java:291`
- [x] `timeout-and-recovery-design.md` 含三层超时边界裁定 + 默认无执行超时 Decision + @SingleSession 对账器 rationale + 对账器规格（间隔/算法/风暴防护/deviation）
- [x] `store-contract-design.md` 含游标分页 DESC 契约 + cancelFire **rename**（`markFireCanceledOnly`）裁定 + LIMIT 约定 + enforceAttribution throw 裁定
- [x] `nop-job.md` 超时与计数段已写
- [x] **No `./mvnw` 适用**（纯文档 phase）
- [x] `ai-dev/logs/2026/08-12.md` 已更新

### Phase 2 - 代码修复

Status: completed
Targets: `JobTimeoutCheckerImpl.java`、`JobTaskStoreImpl.java`、`JobFireStoreImpl.java`、`JobScheduleStoreImpl.java`、`JobCompletionProcessorImpl.java`、`TriggerSpecHelper.java`、`PeriodicTrigger.java`、新增 `JobScheduleCounterReconciler.java`、对应测试类

- Item Types: `Fix`、`Decision`

- [x] **2.1**（Fix, P1-1）`JobTimeoutCheckerImpl.java:452-453`：删除 else 分支 `effectiveTimeoutMs = dispatchTimeoutMs`，改为 `else { return; }`（不超时）。新语义：`timeoutSeconds>0` 用之；否则 `executionTimeoutMs>0` 用之；否则不超时。附测试 `testLongRunningTaskWithoutTimeoutNotKilled`：未配超时、startTime 远早于 now 的 RUNNING task 不被 `tryMarkTimeout` 标记。**实测 PASS**。
- [x] **2.2**（Fix, P2-1）3 处游标分页方向修正：`JobTaskStoreImpl.java:110-111`、`:173-174`、`JobFireStoreImpl.java:255-256` 的 `addOrderField(x, false)` → `addOrderField(x, true)`（DESC）。**验证**：store 层方向改动由代码核对（3 处 `true`）+ `store-contract-design.md` §1 "ASC+lt 重复批次 1" 反例证明；scanner 侧 cursor 推进由既有 mock 测试 `testNoDeadLoopOnNonTimedOutRunningTasks`/`testCursorResetBetweenCycles` 覆盖（二者实测 PASS，31/31 绿）；真 DB 集成验证 `testFetchRunningTasksCursorPaginatesStrictly` gated on H2 successor plan。
- [x] **2.3**（Fix, P2-2）`JobScheduleStoreImpl.java:207-210`：不可恢复分支调用 `updateScheduleWithRetry(schedule, () -> schedule.setNextFireTime(nextFireTime), "recovery-skip")` 后再 return。验证：代码核对 + 既有 recovery 测试不回归。
- [x] **2.4**（Fix, P2-3）`JobFireStoreImpl.java:291`：检查 `tryUpdateWithVersionCheck` 返回值，false 时 `LOG.warn("nop.job.fire-finalize-conflict:…")`。验证：代码核对。
- [x] **2.5**（Fix, P2-4, Decision）`activeFireCount` 漂移收敛：
  (a) `JobFireStoreImpl.java:139`/`:205` 的 `fireOnly()` 返回点 warn 已存在（保持）；
  (b) **新增 `JobScheduleCounterReconciler`**（独立 scanner，**non-`@SingleSession`**，复用 planner `scan-interval-ms`）：扫 ENABLED 且 `activeFireCount>0` schedule，`countByQuery` 重算，mismatch 则 `tryUpdateWithVersionCheck` 修正，版本冲突不重试（warn+下周期）。beans.xml 注册 + `JobCoordinator.doStart()` 调 `startScanning()`（接线见 EC）。**测试**：`TestJobScheduleCounterReconciler` — 常量 sanity PASS + 收敛用例 `@Disabled`（H2 blocked，证据 `08-11.md:121`，activates on successor）。
- [x] **2.6**（Fix, P2-5, Decision）`cancelFire` 契约澄清（**rename 非 merge**）：`JobScheduleStoreImpl.cancelFire(fire, time):428` 改名 `markFireCanceledOnly(fire, time)`，2 处调用点（`overlayFireAndAdvanceSchedule:129`、`insertManualFire:262`）已更新。**拒绝 merge** 详见 `store-contract-design.md` §2（REQUIRES_NEW 嵌套事务破坏原子性）。验证：grep 无残留私有 `cancelFire(activeFire` 调用。
- [x] **2.7**（Fix, P2-6）`JobCompletionProcessorImpl:110`：保留 `@SingleSession` dirty-flush；catch warn 补 `scheduleId` 使 commit 期冲突可关联（不重构 catch 匹配异常类——`tryUpdateWithVersionCheck` 返回 boolean 不抛、commit 期冲突异常类型不可靠识别）。PRIMARY 收敛机制为 2.5 对账器。验证：代码核对。
- [x] **2.8**（Fix, P2-7）`JobScheduleStoreImpl.java:196-204`：删除 `failedFire` 字段设置死代码，仅保留 `freshFire` reload + 更新，加注释。验证：代码核对。
- [x] **2.9**（Fix, P2-8）`JobScheduleStoreImpl.findActiveFires:363`（`setLimit(500)`，overlay drain，超限 warn）。**`findTasksByFireId`（两处）不加 LIMIT**——completion/cancel 需全部 task，详见 `store-contract-design.md` §3。验证：代码核对。
- [x] **2.10**（Fix, P2-9）fixed-rate 相位锚定：`TriggerSpecHelper.java:46-48` 的 `getLastScheduledTime()` 在 `lastFireTime==null` 时返回 `-1`；消费方 `PeriodicTrigger.java:51` 改 `if (start <= 0)` + minScheduleTime/now 锚定（原 `start < 0` 分支对 minScheduleTime==0 fall-through 到 epoch 0）。验证：代码核对 + `CronTrigger`/`HandleMisfireTrigger` 消费方 `-1` 安全性已核对（二者均 `<= 0` 或 `< afterTime` 兜底）。
- [x] **2.11**（Fix, 低风险 P3）`startTimeOrNow` 改名 `computeDispatchElapsedMs` + 去 `updateTime` fallback（代码核对）；`enforceAttribution && workerInstanceId==null` 显式 throw `ERR_JOB_WORKER_INSTANCE_ID_REQUIRED`（新增错误码，代码核对）。**`findTasksByFireId` 去重 DEFERRED**：跨 store 注入风险 > 5 行方法价值，移入 Non-Blocking Follow-ups（两处均用 `daoProvider.daoFor(NopJobTask.class)`，divergence 风险已注释）。

Exit Criteria:

- [x] P1-1：`testLongRunningTaskWithoutTimeoutNotKilled` 实测 PASS；`JobTimeoutCheckerImpl.java:452-453` 不再出现 `dispatchTimeoutMs` 回退
- [x] P2-1：3 处 `addOrderField` 第二参为 `true`（代码核对）；scanner 侧 cursor 推进 mock 测试 PASS；store 侧真 DB 验证 gated on H2 successor
- [x] P2-2：不可恢复 recovery 推进 nextFireTime（代码核对 `updateScheduleWithRetry(...,"recovery-skip")`）；既有 recovery 测试不回归
- [x] P2-3：`failFireWithoutSchedule` 版本冲突 warn（代码核对）
- [x] P2-4：`JobScheduleCounterReconciler` 新增 + beans.xml 注册 + JobCoordinator 接线；收敛用例 @Disabled（H2 gated）+ 常量 sanity PASS；fireOnly warn 含 scheduleId
- [x] P2-5：`markFireCanceledOnly` 改名完成（grep 无残留私有 cancelFire 调用）
- [x] P2-6：完成路径 catch warn 含 scheduleId（代码核对）
- [x] P2-7：`JobScheduleStoreImpl.java:196-204` 死代码已删（代码核对）
- [x] P2-8：`findActiveFires` `setLimit(500)` + 超限 warn（代码核对）
- [x] P2-9：fixed-rate 相位锚定（TriggerSpecHelper -1 + PeriodicTrigger `<=0`，代码核对）
- [x] **接线验证**：`JobScheduleCounterReconciler` 在 `JobCoordinator.doStart():49` 调 `startScanning()`、`app-engine.beans.xml:65` 注册 bean（代码追踪）
- [x] **无静默跳过**：版本冲突分支均 `LOG.warn`（2.4/2.5/2.7）；不可恢复 recovery 推进 nextFireTime（2.3，非裸 return）；对账器 mismatch 显式修正（2.5，非空壳）
- [x] `./mvnw compile`（`-pl nop-job/nop-job-core,nop-job/nop-job-dao,nop-job/nop-job-coordinator,nop-job/nop-job-worker -am`）BUILD SUCCESS；mock 测试套件全绿（TestJobTimeoutChecker 31/31 含新增 P1-1、TestJobCompletionProcessor 22/22、TestJobE2E 4/4、TestJobDispatcherScannerRouting 8/8、TestBlockStrategies 6/6、TestJobDispatcherContainerWiring 1 PASS+1 @Disabled、TestJobScheduleCounterReconciler 1 PASS+1 @Disabled）
- [x] `ai-dev/design/nop-job/` 对应文档与实现一致（store-contract-design.md §2 已改 cancelFire 为 rename、§3 已改 LIMIT 裁定）
- [x] `ai-dev/logs/2026/08-12.md` 已更新

### Phase 3 - 回归 + closure audit

Status: completed
Targets: 全量回归（mock）、`source-anchors.md`、plan 自身

- Item Types: `Proof`、`Follow-up`

- [x] **3.1**（Follow-up）H2 遮蔽问题诊断记录：已写入 `ai-dev/logs/2026/08-12.md`（根因空库初始化，证据 `08-11.md:121`，移 successor plan，不在本 plan 修）。
- [x] **3.2**（Proof）全量回归：`./mvnw test -pl nop-job/nop-job-coordinator`——mock/focused 全绿（含新增 P1-1 用例 + 对账器 sanity）；真 DB 集成测试 TestJobCoordinatorScanner(21)/TestJobConcurrency(6) 维持 pre-existing H2 失败（21+6=27，与 baseline 一致，grep 确认全 42S04，数量未恶化）。
- [x] **3.3** 文档一致性核对：Phase 1 design 文档与 Phase 2 实现逐条一致（store-contract-design.md §2 cancelFire rename、§3 LIMIT 修订均与实现同步）；`source-anchors.md` 核对——`JobScheduleCounterReconciler` 是内部 scanner 非扩展点，无需登记锚点（0 Reconciler 条目，正确 no-op）。
- [x] **3.4**（Proof）独立子 agent closure audit（fresh session，task ses_009b31e66ffeCSxTdOfj9zJ7xP），证据写入本 plan `Closure` 段。
- [x] **3.5** 运行 `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/340-*.md --strict`（exit 0，warnings only 为未 closure 项预期）、`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high`（exit 0，0 critical/high）、`node ai-dev/tools/check-doc-links.mjs --strict`（exit 0，no errors），退出码均 0。

Exit Criteria:

- [x] H2 遮蔽问题诊断记录已写，指向 successor plan
- [x] mock/focused 全量回归通过；真 DB 集成测试的 pre-existing H2 失败状态未恶化（27 errors，全 42S04，与 baseline 一致）
- [x] 文档与 live baseline 一致
- [x] `check-plan-checklist`/`scan-hollow`/`check-doc-links` 退出码 0
- [x] closure audit 证据已写入 `## Closure`（含每条 Exit Criterion/Gate 的 PASS/FAIL + evidence）
- [x] `ai-dev/logs/2026/08-12.md` 收口记录与 Plan Status/Phase Status/Closure Gates 文本一致

## Closure Gates

> 关闭条件：本 section 与每个 Phase 的 Exit Criteria 全部勾选后，才能将 `Plan Status` 改为 `completed`。

- [x] in-scope P1（P1-1）已修复并有 focused test
- [x] in-scope 全部 P2（P2-1 ~ P2-9）已修复（P2-1 store 侧方向 + 对账器收敛的集成验证 gated on H2 successor，本 plan 以 mock + 代码核对 + design-doc 反例证明为接受标准）
- [x] in-scope 低风险 P3（2.11）已落地（startTimeOrNow 改名 + enforceAttribution throw）或显式移入 Deferred（findTasksByFireId 去重）
- [x] 设计文档已补全（timeout / store 契约 / @SingleSession 对账 / recovery / cancelFire rename）并与实现一致
- [x] H2 遮蔽问题已诊断记录并指向 successor plan（不在本 plan 修）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] owner docs（`nop-job.md`、`ai-dev/design/nop-job/*`）已同步
- [x] 独立子 agent closure audit 已完成并记录证据（task ses_009b31e66ffeCSxTdOfj9zJ7xP，PASS-WITH-NOTES，2 个文本一致性 note 已修）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`JobScheduleCounterReconciler` 在 `JobCoordinator.doStart:56-58` 确被 `startScanning` + beans.xml 注册（非空壳）、（b）版本冲突分支均 `LOG.warn`（2.4/2.5/2.7）、（c）不可恢复 recovery 推进 nextFireTime（2.3）
- [x] `./mvnw compile`（`-pl nop-job/nop-job-core,nop-job/nop-job-dao,nop-job/nop-job-coordinator,nop-job/nop-job-worker -am`）BUILD SUCCESS
- [x] `./mvnw test`（受影响模块 mock/focused 全绿：75 tests 0 failures 2 skipped；真 DB 集成测试 pre-existing H2 失败除外——27 errors 全 42S04，与 baseline 一致未恶化）
- [x] checkstyle / 代码规范检查通过（父 pom maven-checkstyle-plugin 被注释、非活跃门禁；按 AGENTS.md 实际规范人工核对通过）

## Deferred But Adjudicated

### ORM 模型结构清理（死列 + 索引）

- Classification: `out-of-scope improvement`（受 Protected Area `plan-first` 约束）
- 涉及：fire `taskCostCpu/taskCostMemory`/`retryRecordId` + 索引 `IX_NOP_JOB_FIRE_RETRY`；task `workerAddress`/`progress`/`progressMessage`；新增 `startTime` 覆盖索引（`fetchRunningTasks`/`fetchDispatchingFires` filesort 优化）。
- Why Not Blocking Closure: 死列不影响正确性（仅占用存储）；索引缺失仅影响性能（filesort，当前数据量可接受）。两者均需改 `model/*.orm.xml`，受 Protected Area 约束。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/`（后续 NN-nop-job-orm-model-cleanup plan）

### H2 测试环境修复（空库初始化）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `ai-dev/logs/2026/08-11.md:121` 证实为 AutoTest/H2 在单模块隔离运行下的 schema 初始化问题（`this database is empty`），非列漂移；与本 plan 改动无关，已在 master 基线复现。P2-1 正确性由本 plan mock 测试 + 代码核对覆盖，不依赖真 DB 集成。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/`（后续 NN-nop-job-h2-test-init plan，research-first 定位 init skip 点）

### 空 list `resolveFinalStatus` 语义

- Classification: `watch-only residual`
- Why Not Blocking Closure: `resolveFinalStatus(空list)=SUCCESS` 在生产由 `completeSingleFire:128-130` 的 `tasks.isEmpty()` 早返回保护，不会误用；仅 API 复用风险。
- Successor Required: `no`

### 多租户隔离模型文档化

- Classification: `watch-only residual`
- Why Not Blocking Closure: 仅靠 partition 隔离是既有设计决策，无安全缺陷；本 plan 在 `store-contract-design.md` 注明该假设。
- Successor Required: `no`

### CANCELED fire 触发重试/告警的路由

- Classification: `watch-only residual`
- Why Not Blocking Closure: helper 审计标 P3，预存在（2026-07-31 verified），非本审计主线；当前调用面影响有限。
- Successor Required: `no`

### recovery 扫描周期级退避

- Classification: `optimization candidate`
- Why Not Blocking Closure: P2-2 修复了 per-schedule 紧循环（nextFireTime 不推进）；scanner 5s 周期本身无 jitter 是全局调度特性，与本次缺陷正交。
- Successor Required: `no`

## Non-Blocking Follow-ups

- `JobFireStateMachine.isWaiting/isRunning/isFailed`、`JobTaskStateMachine.isWaiting/isFailed/isCanceled`、`canRerun` 无生产调用方（closed predicate API，可保留或清理）。
- `TriggerSpecHelper` 补直接单元测试（cron/once/fixed-rate/fixed-delay、null 字段、pause-calendar parse）。
- 实体重构（`fire.transitionTo(s)` 封装 FSM）——encapsulation 改进，非缺陷。

## Optional Sections

### Risks And Rollback

- **P1-1 行为变更**：原本被 dispatchTimeoutMs 在 5min 误杀的未配超时长任务，修复后不再被超时检查器终止（改由 worker-liveness 兜底）。回滚：恢复 `JobTimeoutCheckerImpl.java:452-453` 的 else 分支。若部署上确需"默认 5min 执行超时"，应显式配 `executionTimeoutMs` 而非隐式复用 dispatch 超时。
- **P2-5 cancelFire rename**：改动 overlay/manual 调用点（2.6）的私有方法名，若漏改某调用点会编译失败（快速暴露，无双计风险）。回滚：`git revert` 2.6 单 commit；调用点清单见 2.6（`overlayFireAndAdvanceSchedule` + `insertManualFire`）。
- **P2-4 对账器**：新增周期扫描组件，若对账逻辑写错会反复改写 activeFireCount。缓解：对账器先按 mismatch 计数 + warn 灰度，mismatch 阈值校验后再写回；附 focused test 覆盖收敛与不误改。
- **@SingleSession 不变**：本 plan 不动该注解，完成/取消路径 dirty-flush 契约保持，无回归风险。

## Closure

Status Note: nop-job-dao 三路审计的 1×P1 + 9×P2 全部修复，设计文档补全并与实现一致，新增独立会话计数对账器收敛 @SingleSession 路径的计数漂移。H2 遮蔽问题诊断记录并移 successor plan。两轮对抗 review（B1/B2/B3 解决）+ 独立 closure audit PASS-WITH-NOTES（2 个文本一致性 note 已修）。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task ses_009b31e66ffeCSxTdOfj9zJ7xP）
- Verdict: **PASS-WITH-NOTES**（16 项 criteria 全 PASS；2 个非阻塞文本一致性 note 已修复）
- Evidence:
  - P1-1 PASS：`JobTimeoutCheckerImpl.java:452-458` else→return；`testLongRunningTaskWithoutTimeoutNotKilled` 实测 PASS。
  - P2-1 PASS：3 处 `addOrderField(x, true)`（`JobTaskStoreImpl:119-120`/`:182-183`、`JobFireStoreImpl:255-256`）；scanner 侧 mock 测试覆盖。
  - P2-2 PASS：`JobScheduleStoreImpl:211-213` `updateScheduleWithRetry(...,"recovery-skip")`。
  - P2-3 PASS：`JobFireStoreImpl:294-296` 版本冲突 warn。
  - P2-4 PASS：`JobScheduleCounterReconciler.java` extends AbstractBatchScanner、scanBatch 无 @SingleSession、countByQuery+tryUpdateWithVersionCheck 单次写回；beans.xml:66-67 注册 + JobCoordinator:56-58 接线。
  - P2-5 PASS：`markFireCanceledOnly` rename（`JobScheduleStoreImpl:433`）+ 2 调用点更新；grep 0 残留私有 cancelFire 调用。
  - P2-6 PASS：`JobCompletionProcessorImpl:115-116` warn 含 scheduleId。
  - P2-7 PASS：recovery 死代码已删（`JobScheduleStoreImpl:201-203` 仅 freshFire）。
  - P2-8 PASS：`findActiveFires setLimit(500)` + warn；`findTasksByFireId` 两处无 LIMIT。
  - P2-9 PASS：`TriggerSpecHelper:51` 返回 -1；`PeriodicTrigger:52` `<=0` 锚定。
  - 2.11 PASS：`computeDispatchElapsedMs` 改名；`ERR_JOB_WORKER_INSTANCE_ID_REQUIRED` throw。
  - Anti-Hollow PASS：对账器真实接线 + `LOG.info` 修正；版本冲突分支均 warn；recovery 推进 nextFireTime。
  - Phase 3 PASS：75 mock tests 0 failures 2 skipped；H2-blocked 27 errors 全 42S04 与 baseline 一致；3 工具 exit 0。
  - Notes（已修）：(A) audit `store-layer.md` P2-1 行号刷新至修复后位置；(B) Phase 1 EC / Closure Gate / audit summary+store-layer 的 "cancelFire merge" 措辞统一为 "rename"。
  - `check-plan-checklist` exit 0；`scan-hollow` exit 0（0 critical/high）；`check-doc-links` exit 0。

Follow-up:

- H2 测试环境修复（空库初始化）→ successor plan（research-first 定位 init skip 点）。
- ORM 模型结构清理（死列 + 索引）→ successor plan（Protected Area plan-first）。
- `findTasksByFireId` 跨 store 去重（Non-Blocking，两处均用 daoProvider.daoFor，divergence 风险已注释）。
- `TriggerSpecHelper` 补直接单元测试（cron/once/fixed-rate/fixed-delay、null 字段、pause-calendar parse）。
