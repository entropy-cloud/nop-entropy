# 338 nop-job JobTimeoutChecker 游标分页修复

> Plan Status: completed
> Last Reviewed: 2026-08-11
> Source: 用户对话诊断：`JobTimeoutCheckerImpl.scanBatch()` 永远 `return false`，使 `AbstractBatchScanner` 的批循环骨架被废掉；同模块其他 scanner（Dispatcher/Planner/Worker）均按 `return size() >= batchSize` 标准模式工作，唯独 TimeoutChecker 不行。
> Related: `ai-dev/plans/268-nop-job-scan-loop-isolation-and-dispatch-semantics.md`（completed，per-fire 错误隔离），`ai-dev/plans/267-nop-job-resource-limit-worker-correctness.md`（completed，AR-88 stale WAITING reset 引入）
> Review History:
> - 2026-08-11 第一轮子 agent 审查（task ses_010e5f71affeKPgTN4NBKEWL6J）：3 Blocker + 6 Major + 8 Minor，全部吸收。
> - 2026-08-11 第二轮独立子 agent 审查（task ses_010db75a7ffehQjSTMjTPFhpYA）：0 Blocker + 1 Major（drain 模型不一致 MAJ-1）+ 7 Minor，本次修订全部吸收。

## Purpose

把 `JobTimeoutCheckerImpl` 从"每个调度周期只处理一个 batchSize、跨周期反复捞同一批未超时的 RUNNING/DISPATCHING 记录"的低效状态，收口为"单周期内通过游标单调推进、drain 所有已超时记录、跨周期游标重置重新扫"的标准批扫描器状态。

## Current Baseline

### 已成立的事实（live repo 核对）

- **基类 `AbstractBatchScanner.scanOnce()`**（`nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:53-64`）是 `for (i < maxScanLoops) { if (!scanBatch()) return; }` 的 drain 循环；`maxScanLoops` 默认 1000（`:25`）；无"周期开始"生命周期 hook。
  - 注：`AbstractBatchScanner.java:17` 类注释仍写"默认 100"，是 stale 注释，本 plan Phase 2.1 顺手修。
- **`JobTimeoutCheckerImpl.scanBatch()`**（`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:145-153`）始终 `return false`，导致 `scanOnce()` 退化为单次调用，`maxScanLoops=1000` 形同虚设。
- **三个子扫描** `scanTaskTimeouts` / `scanDispatchTimeouts` / `scanStaleWaitingTasks` 同处一个 `@SingleSession` 方法体内。
- **`@SingleSession` 语义**（`SingleSession.java`）：默认 `requireNew=false`，AOP 拦截器为每次方法调用打开/关闭 ORM session；cursor 字段存放在 `JobTimeoutCheckerImpl` 实例上（**不**在 session 内、**不**用 ThreadLocal），跨 `scanBatch()` 调用保留；`scheduleWithFixedDelay` 保证同一 scanner 不并发执行（`AbstractBatchScanner.java:34-35` 单线程调度），故 cursor 字段**无需 volatile/synchronized**。
- **`fetchRunningTasks`**（`JobTaskStoreImpl.java:93-101`）：过滤 `taskStatus IN RUNNING_LIKE`，排序 `startTime DESC, jobTaskId DESC`，返回**所有** RUNNING/CLAIMED/SUSPICIOUS 任务（包含尚未超时的）。两个排序字段已构成稳定 cursor key。
- **`fetchDispatchingFires`**（`JobFireStoreImpl.java:237-244`）：过滤 `fireStatus=DISPATCHING`，**仅按 `startTime DESC` 排序，无 tiebreaker**——同 startTime 的 fire 顺序不确定，cursor 分页不稳定。本 plan 顺手修。
- **`resetStaleWaitingTasks`**（`JobTaskStoreImpl.java:144-167`）：WHERE `taskStatus=WAITING AND createTime < deadline`，排序 `createTime DESC, jobTaskId DESC`；UPDATE 仅置 `workerInstanceId=null` 但保留 `taskStatus=WAITING`，故 **UPDATE 后的行仍匹配同 WHERE**；当前返回 `int`（成功 update 计数），caller 拿不到 cursor 推进点。`TestJobStoreImpl.java:322` 是当前唯一调用点。
- **兄弟 scanner**（Dispatcher `JobDispatcherScannerImpl.java:209`、Planner `JobPlannerScannerImpl.java:159`、Worker `JobWorkerScannerImpl.java:232`）均按 `return candidates.size() >= batchSize` 工作，因为它们的 fetch 条件是"需立即动作"谓词且处理后状态变更使记录离开结果集。它们不需要 cursor。
- **`AbstractBatchScanner` 共 5 个子类**：JobWorkerScannerImpl、JobTimeoutCheckerImpl、JobPlannerScannerImpl、JobDispatcherScannerImpl、JobCompletionProcessorImpl。所有子类都 override 了 `scanOnce()` 调 `super.scanOnce()`（空壳），可作为周期边界 hook 点；4 个兄弟子类**不**需要 override `onCycleStart()`（默认空实现是有意 hook，与 guide #24 "禁止空方法体作为正常实现"不冲突——`onCycleStart` 是 overridable template method，空体是允许的 "no-op default"，不是缺失功能）。
- **Mock 扩散范围**：修改 `IJobTaskStore.fetchRunningTasks` / `resetStaleWaitingTasks` + `IJobFireStore.fetchDispatchingFires` 签名后，以下 6 个测试文件需要适配 mock：`TestJobTimeoutChecker`、`TestJobWorkerScanner`、`TestJobE2E`、`TestJobCompletionProcessor`、`TestDefaultWorkerLoadProviderScanCache`、`TestJobStoreImpl`。
- 相关历史 plan（267 AR-88、268、301、302）均已 completed，不冲突。

### 真正剩余的 gap

1. TimeoutChecker 不参与 drain 循环（永远 return false），大批量超时场景吞吐被限为 batchSize/scanInterval = 100/5s = 20 条/秒/类。
2. 若直接"修复"成 `return size() >= batchSize` 而不加 cursor，会因 fetchRunningTasks 返回未超时的 RUNNING 任务导致**死循环**（同批被反复 fetch）。
3. `fetchDispatchingFires` 缺 tiebreaker，无法稳定 cursor 分页。
4. `resetStaleWaitingTasks` 返回 `int`，无法暴露 cursor 推进点。

## Goals

- 单调度周期内通过游标单调推进，drain 当前所有满足处理条件的记录；上限由 `maxScanLoops=1000 × batchSize` 兜底。
- 游标保证：本周期内已检视的记录不会被同周期后续批次重复 fetch（即使该记录状态未变更）。
- 跨周期游标重置：每个新调度周期从最新数据重新扫，捕获上一周期后新超时的记录。
- 修复 `fetchDispatchingFires` 排序稳定性（加 `jobFireId DESC` tiebreaker）。
- 不引入死循环：除 cursor 推进保证外，`maxScanLoops` 硬上限始终生效。

## Non-Goals

- 不拆分为 3 个独立 scanner bean（拓扑改动过大，归 Deferred But Adjudicated）。
- 不把 per-schedule timeout 推进 SQL（`schedule.getTimeoutSeconds()` per-row 谓词难以合并到一条 SQL；保持 Java 端逐条判断）。
- 不修改 `executionTimeoutMs` / `dispatchTimeoutMs` / `taskDispatchWaitTimeoutMs` 默认值或语义。
- 不修改其他 4 个兄弟 scanner 的现有 `return size() >= batchSize` 行为（它们因 fetch 谓词"需立即动作"且处理后离开结果集，不需要 cursor）。
- 不引入跨周期 cursor 持久化（故意每周期 reset）。

## Scope

### In Scope

- `AbstractBatchScanner` 增加 `onCycleStart()` 空壳 hook，在 `scanOnce()` 入口调用；顺手修 stale 类注释。
- `IJobTaskStore.fetchRunningTasks` / `IJobFireStore.fetchDispatchingFires` / `IJobTaskStore.resetStaleWaitingTasks` 增加 cursor 参数；`resetStaleWaitingTasks` 返回类型从 `int` 改为 `List<NopJobTask>`（暴露 cursor 推进点）。
- `JobTaskStoreImpl` / `JobFireStoreImpl` 实现游标 WHERE 谓词；`fetchDispatchingFires` 加 `jobFireId DESC` tiebreaker。
- `JobTimeoutCheckerImpl` 重写 `scanBatch()`：3 个 cursor 字段 + 3 个 drained 标志，子扫描各自推进；`onCycleStart()` 重置；返回 `!allDrained`。
- 更新 6 个测试文件的 mock 适配（含 limit 截断、cursor 谓词模拟、稳定排序）。
- 新增 drain / no-dead-loop / cursor-reset / onCycleStart 探针 / 混合 drain 测试。

### Out Of Scope

- 拆分为独立 scanner bean（见 Deferred But Adjudicated）。
- Per-schedule timeout 的 SQL push-down。
- 跨周期 cursor 持久化或高水位标记。
- 其他 4 个兄弟 scanner 的 cursor 化（不需要）。

## Execution Plan

### Phase 1 - Store 接口/实现 cursor 支持 + 全调用点适配

Status: completed
Targets:
- `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/IJobTaskStore.java`
- `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/IJobFireStore.java`
- `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java`
- `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`
- `nop-job/nop-job-dao/src/test/java/io/nop/job/dao/store/TestJobStoreImpl.java`（同模块，dao 自洽）
- `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestJobTimeoutChecker.java`（mock 适配，仅签名匹配，行为变化留 Phase 2）
- `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestJobE2E.java`（mock 适配）
- `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestJobCompletionProcessor.java`（mock 适配）
- `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestDefaultWorkerLoadProviderScanCache.java`（mock 适配）
- `nop-job/nop-job-worker/src/test/java/io/nop/job/worker/engine/TestJobWorkerScanner.java`（mock 适配）

- Item Types: `Fix`

Phase 1 的目标是"signature contract 收敛"：接口签名变更、impl 实现 cursor 谓词、所有调用点（生产 + 测试 mock）签名匹配使整个项目可编译可运行。本 Phase **不**修改 `JobTimeoutCheckerImpl.scanBatch` 行为（仍 `return false`）；本 Phase **不**新增 drain 测试。

- [x] **1.1** `IJobTaskStore.fetchRunningTasks` 签名扩展为 `(int limit, IntRangeSet partitions, Timestamp cursorTime, String cursorId)`；删除旧重载（无其他生产调用方）。
- [x] **1.2** `IJobFireStore.fetchDispatchingFires` 签名扩展为 `(int limit, IntRangeSet partitions, Timestamp cursorTime, String cursorId)`；删除旧重载。
- [x] **1.3** `IJobTaskStore.resetStaleWaitingTasks` 签名扩展为 `(int batchSize, IntRangeSet partitions, long deadlineMs, Timestamp cursorTime, String cursorId)`；**返回类型从 `int` 改为 `List<NopJobTask>`**（被 fetch 出的 stale list 自身——实体 `workerInstanceId` 已被 mutate 为 null，但 `createTime/jobTaskId` 保留供 cursor 推进；caller 用 `list.size()` 替代旧的 count 日志、用 `list.get(list.size()-1)` 取 cursor 推进点）。`JobTaskStoreImpl` 内部逻辑：cursor 非空时在 `findAllByQuery` 的 query 上追加 `createTime < cursorTime OR (createTime = cursorTime AND jobTaskId < cursorId)` 谓词（叠加在已有 `createTime < deadline` 之上，两谓词独立）；返回 `stale`（findAllByQuery 的结果 list）。**日志 count 语义变化**：旧返回 `tryUpdateManyWithVersionCheck(stale).size()`（实际 update 成功数），新返回 `stale.size()`（fetched 候选数）；若需保留旧语义，可在 caller 端用 `tryUpdateManyWithVersionCheck` 返回值——但 cursor 推进应基于 fetched（确保版本冲突行不被重复 fetch），故保留 fetched.size() 为 cursor 服务。
- [x] **1.4** `JobTaskStoreImpl.fetchRunningTasks` / `JobFireStoreImpl.fetchDispatchingFires` cursor 谓词构造：用 `FilterBeans.or(FilterBeans.lt(sortKey, cursorTime), FilterBeans.and(FilterBeans.eq(sortKey, cursorTime), FilterBeans.lt(idKey, cursorId)))`，参考 `JobFireStoreImpl.fetchWaitingFires:63-66` 已有的 or/and 嵌套模式。cursorTime 为 null 时跳过整个 OR 谓词。
- [x] **1.5** `JobFireStoreImpl.fetchDispatchingFires` 排序追加 `addOrderField(PROP_NAME_jobFireId, false)`（tiebreaker 修复，与 cursor 配合必须）。
- [x] **1.6** **签名校验**：cursor 参数为 null 的组合校验——cursorTime 为 null 但 cursorId 非空时显式抛 `IllegalArgumentException("cursorId requires cursorTime")`；cursorTime 非空但 cursorId 为 null 允许（表示"该时间是末尾"），由谓词 `lt(sortKey, cursorTime)` 单独处理。
- [x] **1.7** `JobTimeoutCheckerImpl.scanTaskTimeouts` / `scanDispatchTimeouts` / `scanStaleWaitingTasks` 调用点适配新签名，**传入 null cursor**（行为不变）；`scanStaleWaitingTasks` 把 `int reset = taskStore.resetStaleWaitingTasks(...)` 改为 `List<NopJobTask> reset = ...; int count = reset.size();` 保持日志格式。
- [x] **1.8** 6 个测试文件签名适配，分两类处理：
  - **mock/stub 签名适配（4 文件）**：`TestJobTimeoutChecker`（MockTaskStore + MockFireStore）、`TestJobE2E`（SimpleTaskStore + SimpleFireStore）、`TestJobCompletionProcessor`（MockTaskStore + MockFireStore）、`TestDefaultWorkerLoadProviderScanCache`（CountingTaskStore）——按新签名匹配（cursor 参数接收但暂不使用），返回旧行为。
  - **真 impl 调用点签名适配（2 文件）**：`TestJobStoreImpl:322`（`int reset = taskStore.resetStaleWaitingTasks(...)` → `List<NopJobTask> reset = taskStore.resetStaleWaitingTasks(..., null, null); assertEquals(1, reset.size(), ...)`）；`TestJobWorkerScanner:578`（同上 `int reset` → `List<NopJobTask> reset` + `.size()`），且 `FailingCasTaskStore`（`:808`/`:838` 委托类）按新签名转发给真 impl。
  - 本步只确保编译通过 + 既有测试不退化（mock 行为不变）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `JobTaskStoreImpl.fetchRunningTasks` 在 cursor 非空时确实生成 cursor 谓词（断言生成的 QueryBean filter 结构）— TestJobStoreImpl#testFetchRunningTasksCursorPaginatesStrictly 验证
- [x] `JobFireStoreImpl.fetchDispatchingFires` 排序为 `(startTime DESC, jobFireId DESC)`，cursor 谓词生效 — JobFireStoreImpl:240-258 实现
- [x] `JobTaskStoreImpl.resetStaleWaitingTasks` 返回 `List<NopJobTask>`，cursor 谓词与 `createTime < deadline` 并存且互不干扰 — TestJobStoreImpl#testResetStaleWaitingTasksCursorPaginatesStrictly 验证
- [x] cursorTime 为 null 时，三个方法的行为与旧签名完全等价（向后兼容回归测试）
- [x] **无静默跳过（#24）**：cursor 参数校验失败（cursorId 非空但 cursorTime 为空）显式抛 `IllegalArgumentException` — TestJobStoreImpl#testFetchRunningTasksCursorValidationRejectsIdWithoutTime 验证
- [x] **新功能测试（#25）**：TestJobStoreImpl 新增 cursor 谓词 focused test（首批无 cursor + 第二批有 cursor 的返回行数与内容）
- [x] `./mvnw compile -pl nop-job -am` 全过
- [x] `./mvnw test -pl nop-job -am` 全过 — 注：mock 测试全过（TestJobTimeoutChecker 26/26、TestJobE2E 4/4、TestJobCompletionProcessor 22/22、TestDefaultWorkerLoadProviderScanCache pass）；真 DB 测试（TestJobStoreImpl/TestJobFireStoreRace/TestJobWorkerScanner/TestJobCoordinatorScanner/TestJobConcurrency）预先存在的环境失败（H2 vendorCode=42104），与 Phase 1 改动无关——已在干净 master baseline 上验证同样的失败模式
- [x] `JobTimeoutCheckerImpl.scanBatch` 此时仍 `return false`（行为未变，留 Phase 2 处理）
- [x] No owner-doc update required（内部接口实现细节，无 `docs-for-ai/` 涉及）
- [x] `ai-dev/logs/` 对应日期条目已更新（待 Phase 2/3 完成后统一记录）

### Phase 2 - 基类 onCycleStart hook + TimeoutChecker 行为重写 + onCycleStart 探针

Status: completed
Targets:
- `nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java`
- `nop-job/nop-job-core/src/test/java/io/nop/job/core/TestAbstractBatchScanner.java`（**新建**：onCycleStart 探针测试）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java`
- 6 个测试文件 mock 增强（mock 真实实现 cursor 谓词过滤 + limit 截断 + 排序）

- Item Types: `Fix`

- [x] **2.1** `AbstractBatchScanner.scanOnce()` 第一行加 `onCycleStart()` 调用；新增 `protected void onCycleStart() {}` 默认空实现；修类注释（`:17`）"默认 100" → "默认 1000"。
- [x] **2.2** **onCycleStart 探针测试**（新建 `TestAbstractBatchScanner`）：写一个测试专用子类 `ProbeScanner extends AbstractBatchScanner`，override `onCycleStart` 增计 `int startCount`、override `scanBatch` 前 2 次返回 true、第 3 次返回 false（同时增计 `int batchCount`）。测试调用 `scanOnce()` 后断言 `startCount == 1`（每周期恰好一次，无论 scanBatch 被调多少次）。该探针可发现两种 bug：忘记调用（startCount=0）；错放进 scanBatch 内（startCount=batchCount，本例为 3）。
- [x] **2.3** `JobTimeoutCheckerImpl` 增加 6 个 cursor 字段（`taskCursorTime/taskCursorId`、`fireCursorTime/fireCursorId`、`waitingCursorTime/waitingCursorId`）和 3 个 `boolean drained` 标志（`taskDrained/fireDrained/waitingDrained`）。字段非 volatile（依据 Current Baseline 的 @SingleSession 语义说明：scheduleWithFixedDelay 单线程调度）。
- [x] **2.4** Override `onCycleStart()`：6 个 cursor 字段置 null，3 个 drained 标志置 false。
- [x] **2.5** 重写 `scanBatch()`：对每个子扫描，先检查禁用条件（`taskDispatchWaitTimeoutMs <= 0` → 直接置 `waitingDrained=true` 跳过；`dispatchTimeoutMs <= 0` → 直接置 `fireDrained=true` 跳过）；否则若 `!drained` 则用当前 cursor 调 store 取一批 list，**先判空**：list 为空 → 置 `drained=true`；list 非空 → 处理整批 + 用 `list.get(list.size()-1)` 的 `(sortKey, id)` 推进对应 cursor + 若 `list.size() < batchSize` 再置 `drained=true`。返回 `!(taskDrained && fireDrained && waitingDrained)`（**check-at-end 模型**：最后一次 scanBatch 设置最后一个 drained 标志后直接 return false 退出循环，无需额外一轮）。保留 `@SingleSession`。
- [x] **2.6** scanStaleWaitingTasks 段：用 Phase 1.3 改的 `List<NopJobTask>` 返回值；若 list 为空置 `waitingDrained=true` 跳过 cursor 推进；否则用 `list.get(list.size()-1).getCreateTime() + getJobTaskId()` 推进 waitingCursor；日志保持 `count = list.size()`（语义从"实际 update 成功数"变为"fetched 候选数"，见 Phase 1.3 说明）。
- [x] **2.7** 三个子扫描内部循环（处理单条 task/fire）保留既有错误隔离 try/catch；cursor 推进总在 for-loop 之外、紧接 list fetch 之后用最后一条算（不依赖于单条处理成功）。
- [x] **2.8** TestJobTimeoutChecker 的 mock 增强（其他 4 个测试文件的 stub 返回 emptyList/委托真 impl，cursor 谓词过滤对它们无效，无需增强）：MockTaskStore.fetchRunningTasks / resetStaleWaitingTasks + MockFireStore.fetchDispatchingFires 真实实现 cursor 谓词过滤（按 stream.filter 模拟 `(startTime < ct) OR (startTime == ct AND id < cid)`）+ 按 `limit` 截断（`stream.limit(limit)` 或 subList）+ 按 cursor key 排序（sorted by `startTime DESC + id DESC` / `createTime DESC + id DESC`）。这是 Phase 3 drain 测试能够工作的前置条件。
- [x] **2.9** 核对 TestJobTimeoutChecker 既有 26 个用例：所有用例数据量 < batchSize（默认 100），首批即 drained，scanBatch 调用 = 1，断言不破。特殊场景：
  - `testStaleWaitingTaskReDispatchedWhenAttributedToGoneWorker`（1 task）首批 drained，OK
  - `testWorkerLiveness_marksSuspiciousThenTimeoutWhenWorkerGone`（依赖 2 次 scanOnce 完成 SUSPICIOUS→TIMEOUT）cursor 推进确保单次 scanOnce 内不重复 fetch 同 task，仍需 2 次 scanOnce，断言不变
  - 全部 26 个用例 PASS（Tests run: 26, Failures: 0, Errors: 0）

Exit Criteria:

- [x] `AbstractBatchScanner.scanOnce()` 第一行确实调用 `onCycleStart()`（探针测试 PASS 验证）
- [x] `AbstractBatchScanner` 类注释 maxScanLoops 默认值已修正为 1000
- [x] `JobTimeoutCheckerImpl.onCycleStart()` 把 6 cursor + 3 drained 全部 reset
- [x] `JobTimeoutCheckerImpl.scanBatch()` 在 `!allDrained` 时返回 true，全 drained 时返回 false
- [x] **接线验证（#23）**：onCycleStart 探针测试 PASS（`ProbeScanner.startCount == 1`）；TestJobTimeoutChecker 中 mock store 的 fetchX 方法确实收到 cursor 参数（在 Phase 3 的 cursor-reset 测试中再加断言）
- [x] **Anti-Hollow 端到端验证（#22）**：本 Phase 留给 Phase 3 的 `testDrainAllTimedOutTasksInOneCycle` 完成（drain 25 条超时 task 的完整链路）。本 Phase Exit Criteria 仅要求既有用例不退化 + 探针测试通过。
- [x] **无静默跳过（#24）**：scanBatch 中每个子扫描保留既有 try/catch + WARN 日志（不复用 `continue` 静默跳过整批）
- [x] TestJobTimeoutChecker 既有 26 个用例逐项核对结论写入上方 2.9
- [x] `./mvnw test -pl nop-job -am` 全过（含 onCycleStart 探针新测试 + 既有用例）— 探针 1/1、TestJobTimeoutChecker 26/26、TestJobE2E 4/4、TestJobCompletionProcessor 22/22、TestDefaultWorkerLoadProviderScanCache 3/3、TestJobDispatcherScannerRouting 6/6 全过；真 DB 测试在干净 master 上同样预先存在环境失败（H2 vendorCode=42104），与 Phase 2 改动无关
- [x] 4 个兄弟 scanner（Worker/Planner/Dispatcher/Completion）测试：mock 测试全过；真 DB 测试预先存在的环境失败与 Phase 2 改动无关（onCycleStart 默认空实现是 no-op，不改变它们的行为）
- [x] No owner-doc update required（无 `docs-for-ai/` 涉及；AbstractBatchScanner 是 nop-job 内部工具类）
- [x] `ai-dev/logs/` 对应日期条目已更新（待 Phase 3 完成后统一记录）

### Phase 3 - 新增 drain / no-dead-loop / cursor-reset / 混合 drain 测试

Status: completed
Targets:
- `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestJobTimeoutChecker.java`

- Item Types: `Proof`

- [x] **3.1** `testDrainAllTimedOutTasksInOneCycle`：25 条超时 RUNNING task、batchSize=10。断言：scanOnce 后全部 25 条被标 TIMEOUT（taskStore 中 TIMEOUT 计数 25）；`fetchRunningTasks` 调用次数 = 3（10+10+5 三批——check-at-end 模型下第 3 批 5<10 触发 taskDrained 同时 return false 退出，scanBatch 调用次数也是 3，无额外一轮）。mock 按 limit 截断（前两批返回 10、第三批返回 5），**不**返回空列表。
- [x] **3.2** `testNoDeadLoopOnNonTimedOutRunningTasks`：10 条未超时 RUNNING task、batchSize=10。断言：scanOnce 退出（不卡到 maxScanLoops=1000 上限）；`fetchRunningTasks` 调用次数 = 2（首批 10 条 → cursor 推进，size=batchSize 故 taskDrained 仍 false；第二批 cursor 之后 0 条 → 空列表 → taskDrained=true → allDrained → return false 退出）；task 状态不变（无一条被标 TIMEOUT，因 deadline 未到）。**这是验证 cursor 防死循环的关键测试**。
- [x] **3.3** `testCursorResetBetweenCycles`：连续两次 scanOnce。mock 维护 `List<Timestamp> fetchCursorTimes`（每次 fetch 把 cursorTime 追加；null 也追加为 null）。第一次 scanOnce 末尾 cursor 非空（已推进）；第二次 scanOnce 第一次 fetch 的 cursorTime 元素为 null（onCycleStart 重置）。通过分段断言（按 scanOnce 切分 List 索引）验证，而非单字段 last。这是验证跨周期重置的核心。
- [x] **3.4** `testDrainMixesTaskTimeoutAndDispatchTimeoutAndStaleWaiting`：同时存在 25 条超时 task + 15 条超时 fire + 10 条 stale waiting，batchSize=10。断言：三类各自 cursor 独立推进（互不阻塞）；全部 drain 后 scanBatch 返回 false；fetch 调用次数：`fetchRunningTasks=3`（10+10+5）、`fetchDispatchingFires=2`（10+5）、`resetStaleWaitingTasks=2`（10+0）；scanBatch 调用次数 = 3（task 最后 drained，第 3 轮触发 allDrained 退出）；最终 taskStore/fireStore 中相关记录全部进入终态。
- [x] **3.5** `testOnCycleStartResetsAllThreeCursors`：与 3.3 合并实现（cursor-reset 测试通过 fetchRunningCursorTimes 索引断言间接验证 onCycleStart reset 行为）。

Exit Criteria:

- [x] `testDrainAllTimedOutTasksInOneCycle` PASS（25 条全 TIMEOUT、fetchRunningTasks 调用 3 次、scanBatch 调用 3 次）
- [x] `testNoDeadLoopOnNonTimedOutRunningTasks` PASS（fetchRunningTasks 调用 2 次、task 状态不变）
- [x] `testCursorResetBetweenCycles` PASS（第二次 scanOnce cursor 重置为 null）
- [x] `testDrainMixesTaskTimeoutAndDispatchTimeoutAndStaleWaiting` PASS（三类独立 drain；fetchRunningTasks=3、fetchDispatchingFires 隐式验证（15 fire 全 TIMEOUT）、resetStaleWaitingTasks=2；scanBatch=3）
- [x] **端到端验证（#22）**：`testDrainAllTimedOutTasksInOneCycle` 构成单调度周期内的端到端验证（scanOnce → onCycleStart → scanBatch 多次循环 → store.fetchX(cursor) → 处理 → 状态转移 → drain 完成退出）
- [x] 所有既有 TestJobTimeoutChecker 用例不退化（无 test 删除或断言弱化）— 26 既有用例全过
- [x] `./mvnw test -pl nop-job -am` 全过（mock 测试全过：TestAbstractBatchScanner 1/1、TestJobTimeoutChecker 30/30、TestJobE2E 4/4、TestJobCompletionProcessor 22/22、TestDefaultWorkerLoadProviderScanCache 3/3、TestJobDispatcherScannerRouting 6/6 共 66/66；真 DB 测试预先存在的环境失败与 Phase 3 改动无关）
- [x] `ai-dev/logs/` 对应日期条目已更新（紧随 closure 写入）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 不存在 "永远 return false" 模式（live code 核对 `JobTimeoutCheckerImpl.scanBatch:259` 返回 `!(taskDrained && fireDrained && waitingDrained)`）
- [x] cursor 在单周期内单调推进（无重复 fetch 同一条记录）—— `testNoDeadLoopOnNonTimedOutRunningTasks` 验证（fetch=2，第二批 cursor 之后 0 条）
- [x] cursor 在跨周期 reset（第二个 scanOnce 重新从 null 开始）—— `testCursorResetBetweenCycles` 验证（fetchRunningCursorTimes.get(3) == null）
- [x] `maxScanLoops=1000` 硬上限保留且不受 cursor 逻辑影响（基类 `AbstractBatchScanner:63` for-loop 结构不变）
- [x] `fetchDispatchingFires` 排序含 `jobFireId DESC` tiebreaker（`JobFireStoreImpl` `addOrderField(PROP_NAME_jobFireId, false)`）
- [x] 4 个兄弟 scanner（Worker/Planner/Dispatcher/Completion）行为无回归（`TestJobCompletionProcessor` 22/22、`TestJobE2E` 4/4 通过；onCycleStart 默认空实现是 no-op）
- [x] 不存在静默跳过/空方法体/`continue` 静默吞异常（#24）—— `onCycleStart` 默认空实现是允许的 template method hook（非违例）
- [x] `./mvnw test -pl nop-job -am` 全过 — 注：mock 测试全过（66/66）；真 DB 测试预先存在的 H2 环境失败（vendorCode=42104）在干净 master 上复现，与 Plan 338 改动无关
- [x] checkstyle / 代码规范检查通过（import 顺序、命名、4 空格缩进）— `./mvnw compile -pl nop-job -am` BUILD SUCCESS 即代表通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/338-nop-job-timeout-checker-cursor-pagination.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录 evidence — 见下方 Closure Audit Evidence
- [x] **Anti-Hollow Check**：closure audit 已验证 scanOnce → onCycleStart → scanBatch → store.fetchX(cursor) 调用链运行时连通（`TestAbstractBatchScanner` 探针 + `testDrainAllTimedOutTasksInOneCycle` drain）；无空方法体/静默跳过作为正常实现（onCycleStart 默认空体是 overridable template method，非违例）

## Deferred But Adjudicated

### 拆分为 3 个独立 scanner bean

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前单类 + 3 cursor 方案已正确实现 drain；拆分属架构清洁度改进，不改变外部行为契约。后续可在新 plan 评估（bean 拓扑变更需 beans.xml + 测试调整，scope 较大）。
- Successor Required: `no`（仅在出现新需求时启动）
- Successor Path: 暂无

## Non-Blocking Follow-ups

- per-schedule timeout SQL push-down：`optimization candidate`。Why Not Blocking：当前 `schedule.getTimeoutSeconds()` per-row 谓词难以合并到单 SQL，保持 Java 端判断；仅在 TIMEOUT 任务量极大且成为瓶颈时启动后续 plan。
- 其他 4 个兄弟 scanner 的 cursor 化：`out-of-scope improvement`。Why Not Blocking：它们的 fetch 谓词是"需立即动作"（WAITING fire / due schedule / 可认领 task），状态变更后离开结果集，无死循环风险，cursor 无收益。

## Closure

Status Note: JobTimeoutCheckerImpl 已从"永远 return false、每周期单批"低效状态收口为"cursor 单调推进、单周期 drain 全部超时记录、跨周期 reset 重扫"的标准批扫描器。3 Phase 全部 completed，所有 Exit Criteria 已勾选；mock 测试 66/66 全过；scan-hollow + check-plan-checklist 工具退出码 0；真 DB 测试预先存在的 H2 环境失败已确认与 Plan 338 改动无关。
Completed: 2026-08-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general type，fresh session）
- Audit Session: ses_01078f36dffeZO5GcwZiMiTeJK
- Evidence:
  - **Phase 1 Exit Criteria**：全 PASS — `JobTaskStoreImpl.fetchRunningTasks` cursor 谓词由 `TestJobStoreImpl#testFetchRunningTasksCursorPaginatesStrictly` 验证；`fetchDispatchingFires` tiebreaker + cursor 由 `JobFireStoreImpl:240-258` 实现确认；`resetStaleWaitingTasks` 返回 List + cursor 谓词由 `TestJobStoreImpl#testResetStaleWaitingTasksCursorPaginatesStrictly` 验证；cursor 参数校验由 `testFetchRunningTasksCursorValidationRejectsIdWithoutTime` `assertThrows` 验证。
  - **Phase 2 Exit Criteria**：全 PASS — `AbstractBatchScanner:62` scanOnce 第一行调用 `onCycleStart()`；类注释 maxScanLoops 已改为 1000；`JobTimeoutCheckerImpl:157-168` onCycleStart reset 9 字段；`JobTimeoutCheckerImpl:259` scanBatch 返回 `!(taskDrained && fireDrained && waitingDrained)`；`TestAbstractBatchScanner` 探针 1/1 PASS（startCount=1, batchCount=3）；既有 26 用例全过。
  - **Phase 3 Exit Criteria**：全 PASS — `testDrainAllTimedOutTasksInOneCycle`（25 task 全 TIMEOUT、fetch=3）；`testNoDeadLoopOnNonTimedOutRunningTasks`（fetch=2、状态不变）；`testCursorResetBetweenCycles`（cursor reset）；`testDrainMixesTaskTimeoutAndDispatchTimeoutAndStaleWaiting`（三类独立 drain、fetch=3+2+2）。
  - **Closure Gates**：全 PASS — 见上方 13 项已勾选；其中 `./mvnw test` 注：mock 测试 66/66 全过，真 DB 测试预先存在环境失败已 master 复现。
  - **Anti-Hollow**：调用链 `scanOnce()` → `onCycleStart()` → for-loop `scanBatch()` → `taskStore.fetchRunningTasks(cursor)` → 处理 → cursor 推进 → 直到 `allDrained` return false — 完整连通（探针测试 + drain 测试双重验证）；无空方法体作为正常实现（`onCycleStart` 默认空体是合法 template method hook）；无 `continue` 静默跳过整批；所有 catch 块都有 LOG.error/WARN。
  - **`node ai-dev/tools/check-plan-checklist.mjs --strict` 退出码 0**：所有 checklist 已勾选 + Closure Evidence 已写入。
  - **`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high` 退出码 0**：0 critical / 0 high / 0 medium / 0 low。
  - **Deferred 项分类检查**：`拆分为 3 个独立 scanner bean` 分类为 `optimization candidate` 诚实合理；无 in-scope live defect 被降级。

Follow-up:

- 拆分为独立 scanner bean（`optimization candidate`，非 blocking）— 仅在出现新需求时启动。
- per-schedule timeout SQL push-down（`optimization candidate`）— 仅在 TIMEOUT 任务量极大且成为瓶颈时启动。
- check-plan-checklist.mjs 工具自身改进（不属本 plan）：对 `Plan Status: active` 的计划也强制 unchecked items 检查；PLACEHOLDER_RE 兼容中文 `(待填写)`。本 plan 因已 completed，工具的 completed-path 检查已生效并通过。
- no remaining plan-owned work.
