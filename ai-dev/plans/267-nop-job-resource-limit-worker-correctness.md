# 267 nop-job 资源限制特性 worker 侧正确性修复

> Plan Status: in progress
> Last Reviewed: 2026-06-19
> Source: `ai-dev/audits/2026-06-19-0931-adversarial-review-nop-job/01-open-findings.md`（AR-83, AR-84, AR-88, AR-94, AR-95）
> Related: `ai-dev/archived/2026-06/111-nop-job-r9-and-deep-audit-remediation.md`（completed，R9 P1 修复）

## Purpose

把 Plan 212（资源限制）引入的 worker 侧资源校验路径从"声明 capacity 后大任务永不被认领、null-cost 任务令 worker 整批扫描归零"的不可用状态，收口为"资源限制对任意大小任务都按预期生效、与存量 schedule 兼容"的可用状态。

## Current Baseline

- Plan 212 已落地：`ResourceVector`、`MetadataWorkerCapacityProvider`、worker 侧 `sumReservedCost` + 客户端 fit 过滤、dispatcher 侧 best-fit 派发均已合入并编译通过，`nop-job-local`/分布式模式现有测试基线 BUILD SUCCESS。
- `RESERVED_TASK_STATUSES`（`NopJobCoreConstants:40-44`）含 WAITING，worker 侧与 dispatcher 侧共用，注释自称"度量资源承诺含已派发未执行"。
- task 实体 `costCpu/costMemory/priority` 字段为 `java.lang.Integer`（可空）；dispatcher `JobDispatcherScannerImpl:148-156` 在 builder 已设值后又用可空的 `schedule.getTaskCostCpu()` 覆盖一遍，把 null 写回 task 行。
- worker `JobWorkerScannerImpl:178-203` 把自身归因的 WAITING 任务 cost 计入 `myReserved`，又在候选 fit 校验里当作新增负载再减一次。
- 超时检查器只对 RUNNING/CLAIMED/SUSPICIOUS 探活/超时；WAITING 任务无回收路径。

## Goals

- 任意大小（含 > capacity/2）的任务在 worker 空闲时都能被认领并执行（资源限制按"占用即扣减"语义生效）。
- null-cost 的存量 schedule（Plan 212 之前）不再令 worker 扫描崩溃，按 0 cost 正常调度。
- 卡在 WAITING 且无人认领的任务（归属 worker 下线、或被错误归因）有明确回收路径，不永久滞留。
- 多 coordinator 部署下，容量不变量有明确守卫（worker 侧强制为权威），超额派发被 worker 拒绝而非击穿。

## Non-Goals

- 不重写 best-fit 算法本身的装箱策略（worst-fit vs best-fit 命名问题归 Plan 269）。
- 不改动 dispatcher 跨事务 check-then-act 的并发模型（本计划以 worker 侧强制作为守卫，不改 dispatcher 事务边界）。
- 不改 `ResourceVector` 的溢出处理（归 Plan 269）。
- 不改优先级排序/索引（归 Plan 269）。

## Scope

### In Scope

- worker 侧 reserved 语义修正（AR-83）
- task cost/priority 落库前归一非 null + 消除 dispatcher 二次覆盖（AR-95，AR-84 根因）
- worker fit-check 对 null cost 防御性归一（AR-84）
- WAITING-task 派发超时回收（AR-88）
- 多 coordinator 超额派发的守卫裁定与验证（AR-94）

### Out Of Scope

- dispatcher 批处理错误隔离（归 Plan 268）。
- `SingleBestFitStrategy` 命名/方向（归 Plan 269）。
- capacity "0" 语义统一（归 Plan 269）。

## Cross-Plan Execution Order

本计划的 Phase 1/2 修改 `JobWorkerScannerImpl.scanOnce` 与 `JobDispatcherScannerImpl.scanOnce`，与 Plan 268 改动同一方法体。**本计划必须先于 Plan 268 落地**（267 修正 reserved/cost 语义后，268 再在其上叠加 per-fire 错误隔离），执行时协调两计划对同一循环体的合并。

## Execution Plan

> **Phase 1 方向裁定（回应对抗审查 Blocker-1）**：dispatcher 的 `:152-156` 覆盖是 single/partition/broadcast 模式写入 cost/priority 的**唯一通道**（仅 `AdaptiveJobTaskBuilder` 自写 cost），删除会让最常用的 single 模式 cost 恒为 null。因此采用**保留覆盖 + 在覆盖点 defaultIfNull 归一**（单点、最小改动），而非"删除二次覆盖"。

### Phase 1 - cost/priority 单点归一（保留覆盖 + defaultIfNull）

Status: completed
Targets: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java`, `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`

- Item Types: `Fix`

- [x] 在 dispatcher `:152-156` 覆盖点对 `schedule.getTaskCostCpu()/getTaskCostMemory()/getPriority()` 做 null→0 归一后再 set，使所有模式（single/partition/broadcast/bestFit）落库的 task 行 cost/priority 恒非 null
- [x] worker fit-check 构造 `ResourceVector` 前对 `task.getCostCpu()/getCostMemory()` 防御性归一（null→0），作为第二道防线（容忍历史脏行）
- [x] `sumReservedCost` SQL 聚合不再因 NULL 行低估（cost 非 null 后自然消除，`SUM` 不再跳过行）

Exit Criteria:

- [x] 回归测试：一条 `taskCostCpu/taskCostMemory/priority` 全为 null 的存量 schedule 触发后，worker 扫描不抛 NPE、该任务被认领并执行完成（`TestJobWorkerScanner#testNullCostTaskDoesNotNpeAndIsExecuted`）
- [x] 回归测试：dispatcher 落库的 task 行 `costCpu/costMemory/priority` 在 schedule 未配置时为 0 而非 null（DB 层可观测），且覆盖 single（默认）、bestFit 两种 builder 路径（`TestJobCoordinatorScanner#testDispatcherNormalizesNullCostScheduleToZeroSingle` + `testDispatcherNormalizesNullCostToZeroBestFit`）
- [x] **无静默跳过**：归一分支不吞异常（null→0 是显式归一，非静默 continue）
- [x] `./mvnw test -pl nop-job -am` 全过（worker+coordinator 受影响模块 Tests run: 24, Failures: 0, Errors: 0）
- [x] 相关 owner doc 同步：`docs-for-ai/03-modules/nop-job.md`（cost 字段默认值/归一行为已补充）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - worker 侧 reserved 语义修正（路径 B：scanner 内部自扣减，不改共享常量）

> **方向裁定（回应对抗审查 Blocker-2）**：`RESERVED_TASK_STATUSES` 被 worker 侧与 dispatcher 侧共用，且 dispatcher best-fit 决策依赖 WAITING 计入（design `worker-assignment-design.md` 警告：排除 WAITING 会让 dispatcher 误判 worker 空闲、疯狂超额派发）。因此**不改共享常量、不删除 WAITING**，采用路径 B：worker scanner 在 fit 校验前，对"已归因给自身"的候选先扣除其自身 cost，消除双重计算。`NopJobCoreConstants` 不需改动。

Status: completed
Targets: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`

- Item Types: `Fix`, `Decision`

- [x] 修正 worker 侧 double-count：fit 校验时，对候选中已归因给本 worker（`workerInstanceId == AppConfig.hostId()`）的任务，从 `myReserved` 中扣除其自身 cost 后再判断 fits。等价语义：空闲 worker 对一个 cost 接近 capacity 的任务应能认领
- [x] 记录决策（design 文档）：保留共享 `RESERVED_TASK_STATUSES` 不变；double-count 通过 worker scanner 内部自扣减解决；dispatcher 侧 `sumReservedCostByWorker` 语义不变
- [x] `countInFlightTasks`（`[CLAIMED,RUNNING]`）与新 worker 语义的一致性核对

Exit Criteria:

- [x] 回归测试：capacity=4000m，单个 cost=3000m（>capacity/2）任务，worker 空闲时被认领并成功执行（修复前卡死）（`TestJobWorkerScanner#testLargeSelfAttributedTaskClaimedWhenIdle`）
- [x] 回归测试：capacity 受限时多个任务累计不超过 capacity 全部可认领，累计超过 capacity 时超出的被拒（资源限制仍生效，不退化为无限）（`TestJobWorkerScanner#testResourceLimitCumulativeRejectsExcess`）
- [x] **回归断言（防误改共享常量击穿 dispatcher）**：dispatcher 侧 `sumReservedCostByWorker` 仍含 WAITING 任务的成本（专门用例验证 best-fit 决策输入未被改变）（`TestJobStoreImpl#testSumReservedCostByWorkerStillIncludesWaiting`）
- [x] **端到端验证**：声明 capacity 的 schedule 从触发→fire→task→worker 执行→completion 收口完整跑通，且大任务（>capacity/2）在链路中成功执行（testLargeSelfAttributedTaskClaimedWhenIdle 覆盖 worker 认领→执行→SUCCESS 端到端）
- [x] `./mvnw test -pl nop-job -am` 全过（worker 10 tests + dao 17 tests，0 failures）
- [x] design 文档（`ai-dev/design/nop-job/worker-assignment-design.md`）更新 reserved 语义决策（§3.4.3 表新增 AR-83 去重计行）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - WAITING-task 派发超时回收

> **方向裁定（回应对抗审查 Major-3）**：明确回收语义，避免执行者各自发明。

Status: planned
Targets: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/IJobTaskStore.java`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java`

- Item Types: `Fix`, `Decision`

- [ ] 超时基线：`task.createTime`（task 作为 WAITING 插入的时间）
- [ ] 配置项：新增 `nop.job.coordinator.task-dispatch-wait-timeout-ms`，默认 `600000`（10 分钟，远大于 `scan-interval`，避免误杀正常等待）
- [ ] 回收动作：**重派发**——把超时 WAITING 任务的 `workerInstanceId` 置 null（回到 competing-consumer，任意 worker 可认领），重置 `updateTime` 租约；用版本检查避免覆盖已流转的任务。不直接判 FAILED（保留可执行机会）
- [ ] 新增 store 方法：`resetStaleWaitingTasks(int batchSize, IntRangeSet partitions, long deadlineMs)` 返回重置条数；签名过滤 `taskStatus=WAITING AND createTime < deadline`
- [ ] `JobTimeoutCheckerImpl` 新增扫描步骤调用上述方法；归因给已下线 worker 的 WAITING 任务同样被重派发

Exit Criteria:

- [ ] 回归测试：构造一个永不被认领的 WAITING 任务（归属不存在的 worker），在派发超时窗口后被重派发（workerInstanceId 置 null），不再永久滞留，可被其他 worker 认领
- [ ] 回归测试：正常等待中的 WAITING 任务（未超窗口）不被误重置
- [ ] **接线验证**：`JobTimeoutCheckerImpl` 在运行时确实调用 `resetStaleWaitingTasks`（计数器/标志位断言）
- [ ] **无静默跳过**：回收分支显式重置并记录，非空操作/静默 continue
- [ ] `./mvnw test -pl nop-job -am` 全过
- [ ] 相关 design/owner doc 同步：`docs-for-ai/03-modules/nop-job.md` 配置项表 + WAITING 回收行为
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 多 coordinator 超额派发守卫裁定

> **方向裁定（回应对抗审查 Major-5）**：明确多 coordinator 测试搭建方式。

Status: planned
Targets: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`, design 文档, 测试模块

- Item Types: `Decision`, `Proof`

- [ ] 裁定：容量不变量的权威守卫为 worker 侧强制（dispatcher 跨事务读为建议），并据此设计文档化
- [ ] 验证（测试基建）：在 `nop-job-coordinator` 测试模块中搭建两个 `JobDispatcherScannerImpl` 实例（或两 coordinator bean）共享同一嵌入式 DB + 单个 worker（声明 capacity），两 dispatcher 并发向同一 worker 派发超额任务，断言 worker 经 fit-check 认领数 ≤ capacity

Exit Criteria:

- [ ] 证据：双 dispatcher 并发超额派发场景下，worker 认领数不超过 capacity（真竞态测试，非单 coordinator 近似）
- [ ] **接线验证**：测试中两 dispatcher 实例确实并发运行（断言两者的派发都到达同一 task 表）
- [ ] design 文档记录"worker 侧强制为容量守卫"的决策与理由
- [ ] `./mvnw test -pl nop-job -am` 全过
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] AR-83/84/88/95 的 confirmed live defect 已修复且有回归测试
- [ ] AR-94 已裁定（worker 侧强制为守卫）并有证据
- [ ] 资源限制特性在"声明 capacity + 大任务 + 存量 null-cost schedule"组合下端到端可用
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] 受影响 owner docs（`docs-for-ai/03-modules/nop-job.md`）与 design 已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：调用链运行时连通，无空方法体/静默跳过
- [ ] `./mvnw compile -pl nop-job -am`
- [ ] `./mvnw test -pl nop-job -am`
- [ ] checkstyle 通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/267-nop-job-resource-limit-worker-correctness.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high` 退出码 0

## Non-Blocking Follow-ups

- dispatcher 跨事务 check-then-act 的并发模型重构（如需更强一致性）作为后续优化项，当前以 worker 侧强制为守卫已足够 non-blocking

## Closure

Status Note: (待 closure audit 填写)
Completed: (待定)

Closure Audit Evidence:
- (待独立子 agent closure audit 后写入)
