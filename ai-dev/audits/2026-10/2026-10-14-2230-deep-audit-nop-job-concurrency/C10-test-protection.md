# nop-job 并发审计 — C10 测试保护力（最后批次）

- 审计单元：`nop-job` 聚合器（12 个 jar 子模块），测试面 = `nop-job/*/src/test/java`（67 个测试类）
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation，HEAD 335e77caec）
- 审计维度：C10 测试保护力——不是"有没有测试"，而是"回归会不会红"：对 Phase 4 复核后的 P1/P2 缺陷逐条做变异推演（补上正确实现后，现有哪个测试会红？）
- 盲审纪律：未读取 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/；输入 = 同目录 C2/C4/C5/C7 维度文件（Phase 4 复核后最终保留项）+ nop-job 各子模块测试代码；cra-index.md 无 nop-job 已知未修复登记（全文件 73 行，零 nop-job 命中）
- 审计日期：2026-10-15

---

## 1. 既有并发测试盘点（nop-job 测试清单 × 保护范围）

### 1.1 驱动形态总述

对全部 67 个测试类做并发原语 grep（`Thread.sleep|CountDownLatch|CompletableFuture|ExecutorService|new Thread|synchronized|Atomic*|await|timeout`）+ 关键类全文精读后，总体结论：

1. **全模块零真实多线程竞争测试**。没有任何测试并发调用同一临界区（无双线程 addJob、无双线程管理 API 对冲、无竞争压力循环）。名为 "Race" / "Concurrency" 的两个测试类（`TestJobFireStoreRace`、`TestJobConcurrency`）实为**单线程顺序模拟**：先 A 后 B 各调一次 `scanOnce` / store 方法，用 DB 乐观锁版本冲突断言竞争语义——这是 DB CAS 语义级保护（有效），不是线程级保护。
2. 真实线程只出现在两类形态：(a) `LocalJobScheduler` 测试经 `GlobalExecutors.globalTimer()` 真实定时触发，测试线程以 latch/deadline 轮询等待；(b) rpcPoll 测试经 manager 内部 `scheduleWithFixedDelay`（pollIntervalMs=1000）真实轮询，测试以 `future.get(timeout)` 等待。
3. **等待原语**：无 Awaitility。三种模式并存：deadline+sleep 轮询（`TestJobCoordinatorScanner.waitForStatus` 15s/200ms、`TestLocalSchedulerResultContract` 2s/20ms、`LocalJobScheduler.awaitXxx` 主代码 5ms 轮询）；latch（`TestLocalJobScheduler.testCancelWhileRunning` 等 3 个）；`future.get(timeout)`（`TestRemoteJobInvoker`）。
4. **异步完成面 100% 即时完成**：worker/rpcPoll 测试的全部 mock invoker/client 返回 `CompletableFuture.completedFuture(...)` 或经 `supplyAsync` 立即返回——**没有任何测试使用"永不完成的 promise"或"延迟 N 秒才完成的 promise"**（唯一例外：`TestRemoteJobInvoker.testHungPollDoesNotBlockOtherPollTasks` 的同步阻塞 mock 与 `testCancelTokenListener` 的永不完成 future，两者目的分别是隔离性与取消回调，非终结性保护）。

### 1.2 盘点清单（按保护对象分组）

| # | 模块 | 测试类（测试数） | 保护范围 | 并发相关形态 |
|---|------|----------------|---------|-------------|
| T1 | nop-job-local | `TestLocalJobScheduler`（13） | LocalJobScheduler 注册/暂停恢复/取消/fireNow/once 语义/cancel-while-running/remove-while-running/update-while-running/deactivate | 真实 globalTimer 触发 + latch + awaitXxx 轮询；invoker 全部为立即完成或 supplyAsync |
| T2 | nop-job-local | `TestLocalSchedulerResultContract`（2） | JobFireResult.nextScheduleTime 契约、病态 cron 置 FAILED（历史审计回归） | 真实触发 + 有界忙等（2s/20ms） |
| T3 | nop-job-local | `TestBeanMethodJobInvoker`（10）、`TestBeanContainerInvokerResolver`、`TestLocalJobConfigLoader` | invoker 反射/参数/错误契约；配置装载 | 纯功能，无线程形态 |
| T4 | nop-job-core | `TestAbstractBatchScanner`（5）、`TestCronExpression`、trigger/calendar 系列 | scanOnce 直调下的 onCycleStart/cursor reset/drainBatch 边界；触发器纯计算 | **startScanning/stopScanning 调度链（BindScheduledExecutor 重排链）零测试** |
| T5 | nop-job-dao | `TestJobStoreImpl`（~20） | store 语义含 `sumReservedCost` 全状态核算（WAITING/CLAIMED/SUSPICIOUS/RUNNING 计入、终态排除、按 worker 隔离）、`testClaimedTaskVisibleToRunningScanViaClaimTime` | 顺序单线程 |
| T6 | nop-job-dao | `TestJobFireStoreRace`（10）、`TestJobFireStateMachine` 等 4 个 | fire 完成/取消/超时的乐观锁版本冲突语义（completeFire 不覆盖 canceled、cancelFire 检测版本失配、failFireWithoutSchedule 版本冲突不抛） | 顺序双操作模拟（有效但非线程级） |
| T7 | nop-job-coordinator | `TestJobConcurrency`（6） | 双 planner/dispatcher/worker 竞争同一 DB 行（乐观锁）、TimeoutChecker 标记、completion 聚合、全管线 | 顺序双实例模拟（**非并行线程**） |
| T8 | nop-job-coordinator | `TestJobTimeoutChecker`（~30，1308 行） | dispatch 超时、worker liveness 链（含 CLAIMED 态两分支）、executionTimeoutMs 优先级、WAITING stale 重置（AR-88）、cursor drain、批内隔离 | 全部 `scanOnce()` 直调，时钟为 `scheduleStore.setCurrentTime` 手工推进（**无真实时间依赖，时序鲁棒**） |
| T9 | nop-job-coordinator | `TestRemoteJobInvoker`（11） | rpcPoll 三段式全部正常 resolve 路径（SUCCESS/FAILURE/CANCELLED/TIMEOUT/NOT_FOUND）、墙钟超时（timeoutSeconds=1 配置态）、cancelToken 即时回调 + 监听器摘除断言、hung poll 隔离 | manager 真实 1s 轮询 + future.get(10s)；**getJobStatus 全部成功返回，无失败注入** |
| T10 | nop-job-coordinator | `TestJobCoordinatorScanner`（~25，含 2 个 rpcPoll E2E） | dispatcher/分派回退/超配守卫/分区边界/completion 推进 + `testE2E_rpcPoll_fullChain`（成功链）、`testE2E_rpcPoll_wallClockTimeout`（配置 timeoutSeconds=1 的墙钟超时链） | DB 集成（localDb）+ 真实轮询 + `waitForStatus` 15s/200ms deadline 轮询 |
| T11 | nop-job-coordinator | `TestDefaultRpcPollTaskClient`（13）、`TestRpcJobInvoker`（14） | RPC 客户端请求契约（header/payload/错误码透传/poll-timeout 注入/ICancelToken 透传） | 纯契约，`await` 封装 5s future.get |
| T12 | nop-job-coordinator | `TestJobE2E`、`TestJobCompletionProcessor`、`TestJobScheduleCounterReconciler`、`TestLeastLoadedStrategy`、`TestWorkerLoad`、`TestDefaultWorkerLoadProviderScanCache`（AR-96）、`TestBlockStrategies`、task builder 系列 | 全链 happy path、completion 语义、reconciler、负载缓存生命周期、block 策略 | 单线程 scanOnce 语义 |
| T13 | nop-job-worker | `TestJobWorkerScanner`（~16，1353 行） | 认领→执行→写回成功/失败、max-concurrency 闸门（>0 态）、resource capacity 三场景（Plan 212）、AR-83/84/85/91/93/94/36/41 回归（CAS 失败跳过 invoker、null cost、null promise、单任务隔离） | `scanOnce()` 直调 + localDb；**invoker 全部 completedFuture/返回 null，无未决 promise** |
| T14 | nop-job-service | `TestNopJobTaskLogBizModel`（~6）、`TestNopJobFireBizModel` 等 4 个、`TestDiagProxy` | reportTaskLog 校验/落库（批=2）、biz 门控 | 纯功能 |
| T15 | nop-job-api / retry-adapter | `TestJobLogReporter`（5）、`TestResourceVector`、`TestNopRetryJobRetryBridge` | fire-and-forget 上报 best-effort 语义（失败不传播） | future.join 短等待 |
| T16 | nop-job-web / codegen / app | `NopJobWebPagesTest`、codegen main | 页面/代码生成 | 无并发面 |

### 1.3 与本次缺陷清单的直接交集

| 缺陷（族） | 最近邻测试 | 交集判定 |
|-----------|-----------|---------|
| C2-001 / C2-007（LocalJobScheduler 锁范围/占位） | T1 | invoker 形态错位：测试用即时完成/真异步 invoker，从未用"invokeAsync 内长时间同步阻塞"的 invoker（= BeanMethodJobInvoker 实际形态） |
| C4-001 = C5-001 = C7-001（rpcPoll 条目终结族，+C5-005 放大器） | T9、T10 | 覆盖全部"客户端成功"的 resolve 路径 + 配置态墙钟超时；**错误路径（getJobStatus 持续 failedFuture）× 未配置超时的组合零覆盖**；`activeCount()` 虽为测试而设（RpcPollTaskManager.java:130-132 注释"可观测/测试用"）但全测试面零调用 |
| C4-009 / C7-002（worker promise 无超时） | T13 | 全部即时完成 invoker；无未决 promise 场景；`testLongRunningTaskWithoutTimeoutNotKilled`（T8）从 checker 侧钉死"无配置不超时"声明契约 |
| C4-003（scanner 链死） | T4 | `AbstractBatchScanner` 仅 scanOnce 直调测试；startScanning→BindScheduledExecutor 重排链→running 标志真实性零测试 |
| C7-011（CLAIMED 零回收） | T8、T5 | worker 消失路径已保护（`testClaimedTask_reclaimedWhenWorkerGone`）；worker 存活 + 执行超时的肯定性回收零覆盖，且 `testClaimedTask_notMarkedWhenWorkerAlive` 钉住"存活不触碰"的现行行为 |

---

## 2. 保护力矩阵（P1/P2 抽验缺陷 × 现有测试 × 变异结论）

变异问题：**"补上正确实现后，现有哪个测试会红？"** 判定三值：会红 / 假保护（红但红在钉死缺陷行为的断言上，需先改断言）/ 无测试红（= 缺保护）。

| 缺陷 | 修复形态（变异） | 最近邻测试 | 逐测试判定 | 矩阵结论 |
|------|----------------|-----------|-----------|---------|
| **C2-001** P1 | invokeAsync 移出 `synchronized(job)`（锁内完成状态迁移后锁外调 invoker） | T1 全部 13 测试 | `testCancelWhileRunning`/`testRemoveWhileRunning`/`testUpdateWhileRunningPreservesOldSpec`：invoker 为 supplyAsync 立即返回，锁范围变化不改变 latch 时序，5s deadline 下仍绿；其余测试不触及执行期管理 API | **无测试红（缺保护）** |
| **C4-001=C5-001=C7-001** P1（同机制三视角，含 C5-005 放大器） | (a) pollOnce 加失败预算；(b) computeDeadline 纳入全局 executionTimeoutMs；(c) DB 终态回传 complete future | T9（11）、T10（2 个 E2E） | (a) 全部 mock getJobStatus 成功返回，失败预算不触发；(b) 两个 E2E 均即时 SUCCESS 或显式 timeoutSeconds=1，无 timeoutSeconds 的 fullChain 在 (b) 默认 -1（禁用）下不变绿→红都不红；(c) 测试面无 DB 终态联动断言；`activeCount()` 零断言 | **无测试红（缺保护）** |
| **C4-009** P1（=C7-002 同根因） | worker 侧 promise 超时包装（或 in-flight 年龄兜底回收） | T13 全部 | 全部 invoker 即时完成，超时包装不触发；`testMaxConcurrencySkipsWhenAtLimit` 只验证 count 闸门读取 RUNNING 行，不验证 RUNNING 滞留回收 | **无测试红（缺保护）**（checker 侧"默认墙钟兜底"修复形态另受 C10-002 钉死约束，见该条） |
| **C2-007** P2 | `job.running` 占位前移至 invokeAsync 之前 | T1 | `testFireNow`：fireNow 从管理线程调用于 SUSPENDED 态、invoker 立即完成，占位时点变化不影响断言；无自重入 fireNow 场景 | **无测试红（缺保护）** |
| **C4-003** P2 | scanFuture 挂 whenComplete 置 running=false + catch Throwable | T4 | `TestAbstractBatchScanner` 全部直调 `scanOnce()`，从不调 `startScanning()`；Error 注入/running 标志断言不存在 | **无测试红（缺保护）** |
| **C7-011** P2 | 执行超时覆盖 CLAIMED（isRunning 门放开 + 认领时钟兜底） | T8、T5 | `testClaimedTask_reclaimedWhenWorkerGone`：worker 消失路径，修复不动该路径，仍绿；`testClaimedTask_notMarkedWhenWorkerAlive`：task 的 startTime/createTime 均为 null，基于认领时钟的修复无从计时 → 仍绿（**该测试既不保护也不阻断，仅证明"无时钟即不触碰"**） | **无测试红（缺保护）**（肯定性"存活+超时→回收"用例缺失） |

矩阵规模：6 个缺陷/缺陷族 × 6 组最近邻测试 × 6 条变异结论；**会红 = 0，假保护 = 0（P1 面），无测试红 = 6/6**。P1 缺陷（4 条立案、3 个机制）的回归保护力为零——本轮缺陷全部修复后，若不新增测试，`mvnw test` 全绿通过。

---

## 3. 缺保护登记发现条目

### [nop-job-C10-001] rpcPoll 条目终结三失效面（失败预算/全局超时兜底/DB 终态回传）全部无测试——P1 泄漏族修复无回归网

- **严重程度**：P2（缺保护登记；宿主缺陷 C4-001=C5-001=C7-001 为 P1）
- **关联缺陷**：nop-job-C4-001、nop-job-C5-001、nop-job-C7-001（同机制三视角归并单立案）、nop-job-C5-005（影响放大器，共用本条测试形态）
- **位置（被测面）**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java:175-207`（pollOnce 错误分支）、`:259-265`（computeDeadline）、`:105-128`（register/cancelEntry）、`:130-132`（activeCount——注释"可观测/测试用"）
- **证据片段**（测试面缺口）：
  - `TestRemoteJobInvoker.java`（全文）：11 个测试的 `MockRpcPollTaskClient.getJobStatus` 一律返回 `CompletableFuture.completedFuture(...)`（:730-734），无一个失败注入；唯一"永不完成"出现在 `testCancelTokenListener_cancelsImmediatelyWithoutPollTick`（:230-255），其目的是取消回调路径而非终结兜底。
  - `TestJobCoordinatorScanner.java:1390-1417`（`testE2E_rpcPoll_wallClockTimeout`）：`schedule.setTimeoutSeconds(1)` 显式配置——测的是 deadline **配置态**，非 deadline=0 缺省态。
  - 全测试面 `activeCount()` 零引用（grep 证实），条目从注册表移除仅作为测试清理副作用，从未被断言。
- **并发场景**：worker 崩溃/网络分区 → getJobStatus 每轮 failedFuture → pollOnce warn-and-return → entry + 周期句柄 + PollEntry 钉住的三实体 JSON 对象图（C5-005）永久驻留。
- **触发时序（=变异场景）**：修复 (a) 失败预算——现有 mock 永不失败，预算逻辑不执行；(b) computeDeadline 纳入全局 executionTimeoutMs——测试要么显式配 timeoutSeconds（deadline 路径不变）、要么默认 -1（行为不变）；(c) DB 终态回传——测试面无"TimeoutChecker 置 TIMEOUT 后 entry 应终结"的组合用例。三条修复路径落地后 `mvnw test -pl nop-job-coordinator` 全绿。
- **现状**：无测试覆盖（错误路径 × 缺省超时的组合为零；注册表清理仅 happy path 副作用）
- **风险**：P1 修复回归（如预算计数 off-by-one、deadline 单位错、回传只清理不 cancel 句柄）无任何红灯；C5-005 的 payload 精简修复同样无驻留体积/条目数断言可依。
- **建议**（测试类名级 + 断言点）：
  1. `TestRpcPollTaskManagerEntryTermination`（新，纯单测，复用 TestRemoteJobInvoker 的 mock 体系）：(i) `testTransientFailuresForever_leaksEntryUnderCurrentContract`——getJobStatus 恒 failedFuture + 无 timeoutSeconds → 断言修复后 `activeCount()==0`（失败预算或 deadline 兜底触发 resolve）；(ii) `testGlobalExecutionTimeoutBoundsDeadline`——不配 timeoutSeconds、注入全局 executionTimeoutMs → 断言条目在墙钟上限内终结；(iii) `testDbTerminalStateResolvesFuture`——mock TimeoutChecker 写 TIMEOUT 后（按选定回传机制）断言 future 完成 ERR_JOB_TIMEOUT 且 `activeCount()==0`。
  2. 每个终结测试末尾统一断言 `activeCount()==0`（把现成的 activeCount() 从"为测试而设"变成事实钩子）。
- **失效面枚举**：rpcPoll 条目终结的全部三条修复面（失败预算/超时兜底/终态联动）+ C5-005 payload 驻留面；happy path resolve（T9 六个终态映射测试）与配置态墙钟超时（`testWallClockTimeout_cancelsRemoteAndCompletesWithTimeout`、`testE2E_rpcPoll_wallClockTimeout`）已有保护，不在缺口内。
- **信心水平**：高（测试面全文核读 + grep 双通道证实零失败注入、零 activeCount 断言）。
- **误报排除**：`testCancelTokenListener` 的 removedListeners==1 断言是监听器泄漏的有效窄保护（予以保留认可），不与本条冲突——本条针对 entry/句柄/registry 终结面，非监听器面。
- **复核状态**：已复核（独立复核子代理 2026-10-15）：**保留**。零失败注入/零 activeCount 断言/E2E 仅配置态超时三项独立 grep + 全文核读证实（TestRemoteJobInvoker 实测 13 测试，计数修正见 R2；证据完备性见 R4）。

### [nop-job-C10-002] worker 侧"永不完成的 promise"场景零测试，且 checker 侧兜底修复形态被声明契约测试钉死——C4-009/C7-002 修复只剩单行通道

- **严重程度**：P2（缺保护登记；宿主缺陷 C4-009 P1（任务清单口径）/P2（C4 文件最终保留项）、C7-002 P2）
- **关联缺陷**：nop-job-C4-009、nop-job-C7-002（同根因异维度对）
- **位置（被测面）**：`nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:297-302`（`var promise = invoker.invokeAsync(ctx); promise.whenComplete(...)` 无超时包装）
- **证据片段**（测试面缺口 + 钉死约束）：
  - `TestJobWorkerScanner.java`（1353 行，16 测试）：全部 invoker 为 `CompletableFuture.completedFuture(...)`（:83、:127、:201、:246、:389-391、:534-536、:610-612、:739-740、:782、:1153-1155 等）或返回 null（:1200-1202，AR-41 已保护 null promise → FAILED）。**零个未决 promise 用例**。
  - 钉死约束：`TestJobTimeoutChecker.java:614-639` `testLongRunningTaskWithoutTimeoutNotKilled`——`executionTimeoutMs=-1` + `dispatchTimeoutMs=5000` 下 RUNNING 10 分钟的任务**必须不被 kill**（plan 340 §2.1 P1-1 声明契约）。该断言与 C4 文件所述"兜底三选一默认全缺"（JobTimeoutCheckerImpl:53 + :489-502）互为表里：**任何"在 checker 侧给无配置任务加默认墙钟兜底"的修复形态都会打红这条声明契约测试**。
- **并发场景**：业务 invoker promise 泄漏（永不完成）→ task 永久 RUNNING → cost 永久计入 `sumReservedCost` → worker 容量账本被侵蚀至零认领（C4-009 后果链）；fire 永久 RUNNING + DISCARD 停摆 + 零可观测（C7-002）。
- **触发时序（=变异场景）**：worker 侧加 promise 超时（如 `promise.orTimeout` / 带超时的副本 future）——现有即时完成 invoker 不触发超时路径，全部用例仍绿；sumReservedCost 回落断言不存在（`TestJobWorkerScanner.testResourceCapacityExhaustion` 只验证占用方向，不验证释放方向）。
- **现状**：无测试覆盖（未决 promise 零用例）；且修复形态被双向约束——checker 侧默认兜底会红 `testLongRunningTaskWithoutTimeoutNotKilled`（声明契约，不可作为修复位），worker 侧无任何测试可供回归。
- **风险**：修复若误选 checker 侧默认兜底形态会与 plan 340 契约冲突（红灯原因错误）；选 worker 侧正确形态则无回归网——超时值单位错、异常完成分支漏写 handleExecutionResult、容量释放遗漏均静默通过。
- **建议**：`TestJobWorkerScannerPromiseTermination`（新）：(i) `testNeverCompletingPromise_timesOutAndFinalizesTask`——invoker 返回 `new CompletableFuture<>()` + 配置 promise 超时（短值）→ 断言 task 终态 TIMEOUT/FAILED、fire 可被 completion 聚合；(ii) `testNeverCompletingPromise_releasesReservedCost`——同场景配置 cost>0 → 断言超时回收后 `taskStore.sumReservedCost(hostId)` 回落为 0（当前该断言在缺陷态下恒红，正是缺口所在）；(iii) `testPromiseTimeoutDisabled_keepsDeclaredNoTimeoutContract`——超时配置为禁用值时不 kill（对齐 plan 340 契约，防止修复越过声明边界）。
- **失效面枚举**：JobWorkerScannerImpl.executeTask 的 promise 终结面（超时包装/兜底回收）+ 容量账本释放面；null promise（AR-41）、CAS 失败跳过（AR-85）、终态写回已有保护，不在缺口内。
- **信心水平**：高。
- **误报排除**：`testNullPromiseTreatedAsError`（AR-41）保护的是 invoker 返回 null 的同步分支，与"已返回但永不完成"的 promise 不同面，不构成部分保护。
- **复核状态**：已复核（独立复核子代理 2026-10-15）：**保留**。钉死面专项核读通过（testLongRunningTaskWithoutTimeoutNotKilled :614-639 + JobTimeoutCheckerImpl:497-501 声明注释）；"任何兜底都会打红"表述按 R5 收窄（大默认值新配置不红该测试但属声明契约违约，工程结论不变）；宿主 C4-009 终判级 P2 见 R3。

### [nop-job-C10-003] C2-001 持锁调外部 invoker：无"同步阻塞型 invoker × 管理 API 响应性"测试——P1 唯一修复（锁范围重构）无行为锚点

- **严重程度**：P2（缺保护登记；宿主缺陷 C2-001 为 P1）
- **关联缺陷**：nop-job-C2-001
- **位置（被测面）**：`nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:302-310`（timer 回调 `synchronized(job)` 内 executeJob）、`:215-222`（fireNow 同形态）、`:313-346`（executeJob 锁内 invokeAsync）
- **证据片段**（测试面缺口）：`TestLocalJobScheduler.java` 中三个"running 态"测试的 invoker 全部为 `CompletableFuture.supplyAsync(...)`（:298、:348、:398）——invokeAsync **立即返回**，业务体在池线程异步跑，per-job 监视器从未被业务体长时间持有。平台唯一内置 invoker `BeanMethodJobInvoker`（同步执行方法体，`BeanMethodJobInvoker.java:113-127`）的执行形态在 TestLocalJobScheduler 中零出现（其自身测试 `TestBeanMethodJobInvoker` 为纯反射契约，不经 scheduler）。
- **并发场景**：同步 invoker 方法体长跑（分钟级）→ getJobState/suspendJob/fireNow/deactivate 全部阻塞至方法体结束；awaitXxx 的锁获取段不受 timeout 约束；ABBA 死锁环可构造。
- **触发时序（=变异场景）**：修复 = 把 invokeAsync 移出 `synchronized(job)`（锁内完成状态迁移后锁外调用）。现有 13 测试的时序断言全部基于 latch/awaitXxx + 5s deadline，锁范围收窄不改变其通过性（逐测试推演见 §2 矩阵行 1）；无任何测试断言"执行期间管理 API 必须在有限时间内返回"或"deactivate 不等业务体"。
- **现状**：无测试覆盖（正确实现与缺陷实现在现有测试下行为不可区分——这正是锁范围缺陷的测试学特征）
- **风险**：C2 文件已标注 C2-001 修复涉及 executeJob 锁范围重构、需与 C2-007 占位前移合并设计并回归":330-332 注释所述已完成 future 同步回调覆盖 running"既有防护——该重构若无锁外行为锚点测试，回调注册窗口重排（先注册回调再赋 running、或锁外 invoke 与并发 removeJob 交错）引入的新竞态将无红灯。
- **建议**：`TestLocalJobSchedulerManagementResponsiveness`（新）：(i) `testSyncInvokerDoesNotBlockManagementApi`——invoker 在 invokeAsync 内 latch 阻塞（模拟 BeanMethodJobInvoker 同步形态）；另一线程 `getJobState`/`getJobDetail` 必须在 ≤500ms 返回（缺陷态下该断言恒超时红，正是缺口）；(ii) `testDeactivateDoesNotWaitForRunningJobBody`——同阻塞 invoker 下 deactivate 在 ≤500ms 返回且 job 体最终自然完成不崩；(iii) `testAwaitTimeoutBoundRespectedDuringExecution`——`awaitIdle(job, 500ms)` 在执行期实际耗时 ≤ 明显上界（1s），锁获取段不计入语义修复的回归锚。
- **失效面枚举**：executeJob 两个调用点（timer :306 / fireNow :221）× 锁范围修复面 + deactivate/awaitXxx 响应性面；注册/移除/暂停恢复的**顺序**语义已由 T1 良好保护，不在缺口内。
- **信心水平**：高。
- **误报排除**：`testCancelWhileRunning` 等三测保护的是"running 态管理操作语义"（取消/移除/更新不中断在跑任务），属有效回归但与锁范围正交——锁范围变化不会使其红，也不能防止锁范围回归。
- **复核状态**：已复核（独立复核子代理 2026-10-15）：**保留**。supplyAsync 位置（:298/:348/:398）、BeanMethodJobInvoker 同步形态、TestBeanMethodJobInvoker 零 scheduler 引用、awaitIdle 锁获取段不受 timeout 约束全部独立核实；建议测试形态可行性经 live API 核实（见可行性抽查 1）。

### [nop-job-C10-004] C2-007 running 占位滞后：fireNow 自重入递归/SOE 逃逸链零测试——修复（占位前移）与 C2-001 合并重构缺双向锚点

- **严重程度**：P2（缺保护登记；宿主缺陷 C2-007 为 P2 高价值）
- **关联缺陷**：nop-job-C2-007（与 C2-001 同段代码，修复须合并设计）
- **位置（被测面）**：`LocalJobScheduler.java:321`（invokeAsync 在前）vs `:334`（`job.running = done` 在后）；`fireNow` 判据 `:216-217`（`if (job.running != null) return false`）
- **证据片段**（测试面缺口）：`TestLocalJobScheduler.testFireNow`（:206-247）从测试线程对 SUSPENDED job 调 fireNow，invoker 立即完成——不存在"invoker 方法体执行期间再次进入 fireNow"的自重入形态；全测试面无任何 invoker 回调 scheduler 自身 API 的用例。
- **并发场景**：同步 invoker 的 job 体（BeanMethodJobInvoker 形态）内自调 `scheduler.fireNow(同一 job)` → 占位未置位 → fireNow 重入 executeJob → 无限递归 → SOE 为 Error 穿透两层 catch(Exception) → `:314` 已置 RUNNING 无恢复路径 → 该 job 周期调度静默永久死亡。
- **触发时序（=变异场景）**：修复 = 占位 `job.running = done` 前移至 invokeAsync 之前。现有测试无自重入场景，占位时点变化在即时完成 invoker 下不可观测（testFireNow 断言的是 fireNow 返回 true + count+1，两种时序下均成立）。
- **现状**：无测试覆盖
- **风险**：占位前移本身引入新窗口（占位已置但 invokeAsync 抛 Exception → 需保证 catch 分支清占位，否则 job 永久假 RUNNING）——该新窗口同样无测试；C2-007 的伴生面 awaitIdle 执行全程误报空闲亦无断言。
- **建议**：`TestLocalJobSchedulerReentrancy`（新）：(i) `testSelfReentrantFireNowDoesNotRecurse`——同步 invoker 方法体内调 `fireNow(同名)`（经 AtomicBoolean 防御真实业务递归深度）→ 断言不抛 SOE、fireNow 返回 false（占位生效）、job 后续周期继续触发（未静默死亡）；(ii) `testRunningPlaceholderSetBeforeInvoke`——invoker 内部读 `getJobState`/`awaitIdle` → 断言执行期即报告 RUNNING（修复后行为）；(iii) `testInvokerThrowsAfterPlaceholder_placeholderCleared`——invokeAsync 同步抛 Exception → 断言 job.running 清空、状态回到可调度（修复引入窗口的守卫）。
- **失效面枚举**：executeJob 占位时点 × fireNow 重入面 + awaitIdle 误报面 + update 运行中守卫面（C2-007 三后果）；fireNow 对 SUSPENDED/COMPLETED 的拒绝语义已有 testFireNow/testCancel 覆盖。
- **信心水平**：高。
- **误报排除**：`testFireNow` 已保护 fireNow 的基本成功路径与 SUSPENDED 态行为，本条仅针对执行期重入面，两者互补。
- **复核状态**：已复核（独立复核子代理 2026-10-15）：**保留**。锚点 :321 vs :334、fireNow 判据 :216-217、:314 置位逐行核实；全测试面无 invoker 回调 scheduler API 用例经 grep 证实。

### [nop-job-C10-005] scanner 调度链生命周期（startScanning/BindScheduledExecutor 重排链/running 标志真实性）零测试——C4-003 链死停摆修复无观测锚点

- **严重程度**：P2（缺保护登记；宿主缺陷 C4-003 为 P2、C4-004 同族）
- **关联缺陷**：nop-job-C4-003（与 C4-004 同族同修法）
- **位置（被测面）**：`nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:58-65`（startScanning）、`:88-103`（scanOnce 仅 catch Exception）、`:67-73`（stopScanning）
- **证据片段**（测试面缺口）：`TestAbstractBatchScanner.java` 全部 5 个测试直调 `scanner.scanOnce()`（:48、:78、:85、:126、:199、:221），从不调用 `startScanning()`/`stopScanning()`；`getExecutor()`（globalTimer().executeOn(globalWorker())，:134-136）在测试面零触及；全 nop-job 测试面无任何对 `running` 标志、`scanFuture` 异常完结传播的断言。
- **并发场景**：scanBatch 抛 Error（NoClassDefFoundError/OOM 包装）或 globalWorker 拒绝 → BindScheduledExecutor 链 completeExceptionally 不再重排 → running 仍 true、6 个 scanner 静默永久停摆、无生命周期级感知。
- **触发时序（=变异场景）**：修复 = scanFuture 挂 whenComplete 置 running=false（+metrics）或 scanOnce 放宽 catch Throwable。现有测试全部单轮 scanOnce 直调，不启动调度链，修复前后行为一致。
- **现状**：无测试覆盖（`startScanning` 在整个测试面零调用——生命周期入口无烟测）
- **风险**：修复本身在 whenComplete 回调中改 running（volatile 写）与 stopScanning（synchronized）的新交错无回归网；"running=false 后 startScanning 可重启"语义（若同修）也无锚点。
- **建议**：`TestBatchScannerSchedulingLifecycle`（新，nop-job-core）：(i) `testStartScanning_runsCyclesAndStopScanningStops`——ProbeScanner + 短 interval 真实启动 → 断言 batchCount 增长 → stop 后不再增长（当前即缺的基础生命周期烟测）；(ii) `testScanBatchThrowsError_chainTerminatesButRunningReflectsTruth`——scanBatch 第 2 轮抛 Error → 断言修复后 `running==false`（或 whenComplete 挂钩生效），且 error 可观测（metrics/日志钩子）；(iii) `testSubmitRejected_runningReflectsTruth`——注入满载 executor 模拟拒绝 → 同上；(iv) `testErrorDoesNotKillChain_whenCatchThrowableFix`——若采 catch Throwable 修法则断言链继续（与 (ii) 按选定修法二选一）。
- **失效面枚举**：6 个 scanner 共享的基类调度链（修复一处全收益）；scanOnce 单轮语义（onCycleStart/cursor/drainBatch）已由 T4 良好保护。
- **信心水平**：高。
- **误报排除**：`testBatchResilience_*`（TestJobTimeoutChecker :461/:505，批内单失败不中止）保护的是 scanOnce 内 catch Exception 层——恰是 C4-003 明言"已处理且感知良好"的主路径，与本条（链级 Throwable/拒绝旁路）不同面。
- **复核状态**：已复核（独立复核子代理 2026-10-15）：**保留**。TestAbstractBatchScanner 5 测试全为 scanOnce 直调（7 处，零 startScanning/stopScanning）逐行核实；BindScheduledExecutor（nop-commons）catch Throwable → completeExceptionally 无重入队机制核实；C4-004 族关联口径见 R6；建议测试形态可行性经 live 代码核实（见可行性抽查 2）。

### [nop-job-C10-006] C7-011 CLAIMED 态肯定性回收（worker 存活 × 执行超时）零测试——现有两测覆盖"worker 消失"与"无时钟不触碰"，修复的正确行为无断言

- **严重程度**：P2（缺保护登记；宿主缺陷 C7-011 为 P2）
- **关联缺陷**：nop-job-C7-011
- **位置（被测面）**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java`（liveness 要求 worker 消失、执行超时被 isRunning 门排除 CLAIMED）
- **证据片段**（测试面现状）：
  - `TestJobTimeoutChecker.java:309-334` `testClaimedTask_reclaimedWhenWorkerGone`：CLAIMED + workerInstance 不在存活集 → SUSPICIOUS → TIMEOUT（**worker 消失路径已有有效保护**）。
  - `TestJobTimeoutChecker.java:337-358` `testClaimedTask_notMarkedWhenWorkerAlive`：CLAIMED + worker 存活 + `timeoutSeconds=60` → 断言**保持 CLAIMED**。注意该 task 的 startTime/createTime 均为 null（`createTask` :979-983 只设 id/fireId/status）——它实际断言的是"无可计时钟则不触碰"，**不是**"有超时配置且时钟超限仍不触碰"的缺陷行为；反之，修复后它也大概率保持绿。
  - `TestJobStoreImpl.java:154-179` `testClaimedTaskVisibleToRunningScanViaClaimTime`：CLAIMED 行对 running 扫描可见（认领时钟已入库）——visibility 已备，回收断言缺失。
- **并发场景**：worker 认领后在 CLAIMED→RUNNING 前挂死但进程存活 → liveness 不触发（worker 在存活集）、执行超时被 isRunning 门排除 → fire 永久 RUNNING、额度永久占用。
- **触发时序（=变异场景）**：修复 = isRunning 门放开 CLAIMED + 以 claimTime/createTime 为执行超时时钟。`testClaimedTask_notMarkedWhenWorkerAlive` 的 task 无时钟 → 修复后仍绿；`testClaimedTask_reclaimedWhenWorkerGone` 走 liveness 路径不受影响 → 仍绿。**肯定性行为（存活 + 时钟超限 → TIMEOUT）无任何测试声明**。
- **现状**：无测试覆盖（缺陷面 = CLAIMED × worker 存活 × 超时已到）
- **风险**：修复的时钟选择（claimTime vs createTime vs startTime 回退链）无锚点，误选已被 CAS 移交场景否定的时钟会引入误杀而无红灯；`testClaimedTask_notMarkedWhenWorkerAlive` 的注释文案（"must not be touched"）在修复后语义漂移，易被误读为反对修复的护栏。
- **建议**：`TestJobTimeoutChecker` 增补两测：(i) `testClaimedTaskTimedOutWhileWorkerAlive_reclaimedByExecutionTimeout`——CLAIMED + worker 存活 + claimTime 超过 timeoutSeconds → 断言 TIMEOUT（缺陷态恒红=缺口本体）；(ii) `testClaimedTaskWithinTimeout_notTouched`——时钟未超限 → 保持 CLAIMED（防误杀对偶）。同时更新 `testClaimedTask_notMarkedWhenWorkerAlive` 注释，明确其保护面是"无认领时钟不触碰"而非"存活即豁免"。
- **失效面枚举**：isRunning 门 × CLAIMED 一态；RUNNING 态执行超时（`testExecutionTimeoutUsedWhenScheduleHasNoTimeoutSeconds` 等四测）与 liveness 链（`testWorkerLiveness_*` 五测）已有良好保护。
- **信心水平**：高。
- **误报排除**：不将 `testClaimedTask_notMarkedWhenWorkerAlive` 判为"假保护"——经逐字段核对其 task 无任何计时字段，修复的正确实现不会使其红，故归入"肯定性缺失"而非"断言钉死缺陷行为"。
- **复核状态**：已复核（独立复核子代理 2026-10-15）：**保留**。两既有测试逐行核实，createTask :979-983 无计时字段核实——"非假保护、属肯定性缺失"判定成立；`testWorkerLiveness_*` 恰 5 个、RUNNING 执行超时族 4 个核实；认领时钟字段名修正见 R7（实际为 startTime，JobTaskStoreImpl:107-111）。

### [nop-job-C10-007] C2-004 raced 分支 allowUpdate 契约：仅顺序重复注册受保护，并发双注册窗口零测试

- **严重程度**：P2（缺保护登记；宿主缺陷 C2-004 为 P2，C2-003 同分支共用本条测试形态）
- **关联缺陷**：nop-job-C2-004、nop-job-C2-003（同分支家族）
- **位置（被测面）**：`LocalJobScheduler.java:88-93`（入口 existing 分支校验）、`:110-120`（raced 分支无校验直接 update / 静默 return）
- **证据片段**：`TestLocalJobScheduler.testAddDuplicateJobThrowsAlreadyExists`（:470-476）——顺序两次 `addJob(spec, false)` 断言第二次抛 `nop.err.job.already-exists`。该测试只经过入口 `jobs.get` 命中路径（existing 分支）；raced 分支（putIfAbsent 返回非 null）需要**两线程毫秒级窗口并发首注册**才能到达，测试面无双线程注册用例。
- **并发场景**：T1/T2 并发 `addJob("x", specA/specB, false)` 双双通过入口检查 → 后完成 putIfAbsent 的一方走 raced 分支 → 静默改写对方 spec（C2-004）或在并发 remove 下静默丢注册（C2-003）。
- **触发时序（=变异场景）**：修复 = raced 分支补 `if (!allowUpdate) throw ERR_JOB_ALREADY_EXISTS` / 复检失败 fall-through 重建。现有测试不构造 putIfAbsent 竞争窗口，修复前后唯一可见差异（并发期抛异常 vs 静默覆盖）无任何测试可区分。
- **现状**：无测试覆盖（并发窗口）；顺序契约有保护。
- **风险**：修复改动 raced 分支控制流（含 fall-through 重试），回归（如重试不重挂调度、异常吞掉）无红灯。
- **建议**：`TestLocalJobSchedulerConcurrentRegistration`（新）：(i) `testConcurrentFirstRegistration_allowUpdateFalse_atMostOneWinner`——双线程 CountDownLatch 同步起跑各 `addJob(spec, false)` → 断言恰好一方成功、另一方抛 already-exists（循环 N 次提高窗口命中；注入可控 putIfAbsent 时序的包内可见 seam 更佳——若不动产线代码则以 50 次循环概率性覆盖并在注释声明）；(ii) `testAddDuringConcurrentRemove_neverSilentlyDropped`——add+remove 交错循环后断言 `getJobNames()` 与注册调用结果一致（C2-003 面）。
- **失效面枚举**：raced 分支一条路径（existing/null 分支契约与调度行为已由 T1 保护）。
- **信心水平**：中高（并发窗口测试存在概率性；建议配合 seam 注入，见上）。
- **误报排除**：`testAddDuplicateJobThrowsAlreadyExists` 是有效顺序契约回归，修复不得使其红——建议的并发测试以它为基线叠加而非替代。
- **复核状态**：已复核（独立复核子代理 2026-10-15）：**保留**。顺序契约测试（:471-476）仅经 existing 分支核实；raced 分支 :110-120 无 allowUpdate 校验、jobs 为 ConcurrentHashMap（:48）核实——并发窗口零用例成立；概率性测试形态的自我声明（信心水平中高）与复核一致。

### [nop-job-C10-008] C5-002 reportTaskLog 批量端点无上限校验：功能测试全绿面与上限修复零交集

- **严重程度**：P2（缺保护登记；宿主缺陷 C5-002 为 P2）
- **关联缺陷**：nop-job-C5-002
- **位置（被测面）**：`nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobTaskLogBizModel.java:72-86`（reportTaskLog 循环无条目数/单条体积上限）
- **证据片段**：`TestNopJobTaskLogBizModel.java`——`testReportTaskLog_savesBatch`（批=2，:53-76）、空批（:130-132）、校验失败三态（:111-126）。全部用例 batch ≤ 2；无任何"超限拒绝"或"单条超长拒绝"用例；上限修复（如 `nop.job.log.report-max-batch-size`）新增的拒绝分支不触发任何现有断言。
- **并发场景**：恶意/缺陷 worker 单请求提交 N 万条目 → 全量物化 + 单事务逐条双实体加载 → 请求线程堆峰值与长事务（P2 边界缺陷）。
- **触发时序（=变异场景）**：修复加批上限与单条长度校验——现有 batch=2 用例远低于任何合理限值，全部保持绿；限值配置的默认值选择、超限错误码均无测试声明。
- **现状**：无测试覆盖（上限面）
- **风险**：修复限值误配（如默认 1 导致正常批量上报被拒）将直接打断 worker 日志上报而现有测试不可感知（batch=2 恰好是能暴露"默认限值 < 2"事故的最小用例，但不存在反向断言）。
- **建议**：`TestNopJobTaskLogBizModel` 增补：(i) `testReportTaskLog_overBatchLimit_rejected`——注入限值配置（如 3）+ 提交 4 条 → 断言 NopException 且逐条未落库；(ii) `testReportTaskLog_defaultLimitAllowsNormalBatch`——默认限值下 batch=2 仍成功（守护默认值不误伤，直接复用现有成功用例数据）；(iii) `testReportTaskLog_oversizedSingleEntry_rejected`——单条 logMessage 超长 → NopException。
- **失效面枚举**：reportTaskLog 入口的上限校验分支；校验/落库/redundant 列语义已有保护。
- **信心水平**：高。
- **误报排除**：无（缺口直白）。
- **复核状态**：已复核（独立复核子代理 2026-10-15）：**保留**。全部功能用例 batch ≤ 2（批=2 用例、空批、校验三态）与 live reportTaskLog（:72-86 无上限循环）核实——上限修复零断言面成立。

### [nop-job-C10-009] 时序敏感测试命中：两处"固定 sleep 窗口 + 缺席断言"存在 CI 负载下假通过（superficially-pass）面

- **严重程度**：P2（时序敏感测试缺陷，非宿主缺陷缺保护；按步骤 3 审查命中登记）
- **位置与证据片段**：
  1. `nop-job-local/src/test/java/io/nop/job/local/TestLocalJobScheduler.java:190-195`（`testUpdateWhileSuspendedStaysSuspended`）：
     ```java
     sched.addJob(newSpec("test-update-suspended", 10, true), true);
     assertEquals(JobState.SUSPENDED, ...);
     Thread.sleep(200);
     assertEquals(baseline, invoker.invokeCount.get(), "no new fires while suspended ...");
     ```
     修复验证逻辑：SUSPENDED 态热更新不得重排程。job 的 repeatInterval=10ms——若回归（update 重排 SUSPENDED job），10ms 间隔在 200ms 窗口内应产生 ~20 次 fire，断言会红，方向正确；**但**在 CI 高负载下调度线程饿 200ms 时回归形态也可能 0 次 fire → 假通过。窗口与负载的对抗使该回归保护强度不可控。
  2. `TestLocalJobScheduler.java:139-143` 与 `:160-164`（`testOnceJobFiresExactlyOnce` 两分支）：`awaitState/awaitFireCount` 后 `Thread.sleep(300)` + `assertEquals(1, invokeCount)` 缺席断言。once 语义由状态机构造性保证（COMPLETED 后 scheduleNext 不再排程），sleep 窗口仅为哨兵——假通过风险低于 (1)，但同样属"固定窗口证否"形态。
  3. （正面参照）`TestJobCoordinatorScanner.waitForStatus`（:1444-1459）：deadline(15s)+poll(200ms)+终值 assert 三段式—— nop-job 测试面等待模式的正确范本；`TestLocalSchedulerResultContract:118-126` 的有界忙等（2s/20ms）次之。
- **并发场景**：全部为真实 globalTimer 异步触发下的"事件应不发生"类断言。
- **触发时序（=变异场景）**：若 C2-005（SUSPENDED 更新重排）或 once 语义回归，上述测试在空闲机器上会红（有效），在饱和 CI 上可能绿（假通过）——保护力随环境波动。
- **现状**：方向正确但窗口不可靠的哨兵断言 ×2 处（TestLocalJobScheduler 内 sleep 共 4 处，其余 2 处 :142/:163 同类）。
- **风险**：回归漏报（假通过）；反之 200/300ms 窗口在极端负载下偶发假红（次风险，缺席断言本身不受慢启动影响，假红概率低）。
- **建议**：将"应不发生"断言改为事件驱动计数守卫：(i) 用 invoker 计数 + 明确延长观察窗（如 1s）并在断言消息中携带实际计数；(ii) 或以 scheduler 内部可注入的 clock/executor seam 消除真实时间依赖（LocalJobScheduler 的 executor 已是构造参数——测试可注入手动 tick 的 fake IScheduledExecutor，将 (1) 变成确定性断言）；(iii) 最低成本：保持现结构，仅把 200ms 提到 1s 并注释窗口依据（trade-off：测试时长 +0.8s）。
- **失效面枚举**：`Thread.sleep` 在 nop-job 测试面共 5 处（grep 证实）：TestLocalJobScheduler :142/:163/:193、TestLocalSchedulerResultContract :122（有界忙等，可接受）、TestJobCoordinatorScanner :1448（deadline 轮询组成，可接受）——命中 2 处缺席断言形态（:193 为主要、:142/:163 为哨兵形态同族）。
- **信心水平**：高（形态判定）；中（实际 flake 概率未做运行时统计）。
- **误报排除**：`awaitFireCount/awaitIdle/awaitState`（LocalJobScheduler :405-466）为 deadline+5ms 轮询 + 终值断言，deadline 有界，不属于命中形态（其锁获取段不受 timeout 约束的问题已在 C2-001 登记并在 C10-003 建议中覆盖，不重复计数）。
- **复核状态**：已复核（独立复核子代理 2026-10-15）：**保留**。Thread.sleep 全测试面恰 5 处的枚举经独立 grep 精确复核；:193 主命中（10ms 间隔 × 200ms 窗口 × 缺席断言）与 :142/:163 哨兵形态逐行核实；实际 flake 率未运行时统计（维持形态高/概率中的信心水平声明，见不确定项 1）。

---

## 4. 时序敏感/资源泄漏/大规模测试审查

### 4.1 时序敏感测试审查（步骤 3）

| 审查项 | 结论 |
|-------|------|
| Thread.sleep 同步的测试 | 命中 2 处缺席断言形态（→ C10-009）；其余 3 处为有界忙等/deadline 轮询组成，可接受。全测试面无长 sleep（≥1s）硬等待。 |
| 未等待异步结果就断言的测试 | **零命中**。异步测试统一经 latch.countDown 确认进入、`future.get(timeout)`、或 deadline 轮询后断言；`TestRemoteJobInvoker`/`TestDefaultRpcPollTaskClient` 的 `await()` 封装超时即抛（非静默）。 |
| 测试自己 catch 掉竞争（superficially-pass） | 零显式 catch 竞争；但存在**结构性 superficially-pass**：(a) "竞争"类测试全部单线程顺序模拟（TestJobConcurrency/TestJobFireStoreRace——这是声明过的 DB 语义模拟，不算缺陷但须在覆盖声明中明示不构成线程级保护）；(b) 异步面 100% 即时完成 mock 使"慢/挂"形态系统性不可达（→ C10-001/C10-002 缺口根源）；(c) sleep 窗口缺席断言的负载敏感假通过（→ C10-009）。 |
| 真实时钟依赖 | TestJobTimeoutChecker 用 `scheduleStore.setCurrentTime` 手工推进时钟——全部 ~30 测试零真实时间依赖，时序鲁棒性最佳，值得作为其他测试的修法范本。 |

**时序敏感命中计数：2 处（C10-009 登记）+ 1 项结构性命中（即时完成 mock 形态，并入 C10-001/C10-002）。**

### 4.2 资源泄漏测试审查（步骤 4）

| 审查项 | 结论 |
|-------|------|
| 泄漏检测钩子 | **模块级缺失**。`RpcPollTaskManager.activeCount()`（:130-132）注释明示"可观测/测试用"，但全测试面零调用——现成的钩子未被使用。无 weak-reference 存活探针、无 after-test 注册表规模断言、无线程数/句柄数守卫（无 thread-leak 设施）。 |
| 唯一既有泄漏断言 | `TestRemoteJobInvoker.testCancelTokenListener_cancelsImmediatelyWithoutPollTick:254`——`assertEquals(1, token.removedListeners)`（终结后 onCancel 监听器必须摘除）。这是全模块唯一的泄漏守卫，面积极窄（监听器级），有效应保留。 |
| P1 级缺陷的建议测试形态 | 已并入各条目建议节：C4-001 族 → `activeCount()==0` 终结断言 + 周期句柄 cancel 断言（可经 mock executor 的 scheduleWithFixedDelay 返回句柄核验 cancel 调用）；C4-009 → `sumReservedCost` 回落断言（释放方向，当前只测占用方向）；C5-005 → PollEntry 驻留体积断言（弱引用探针或条目内 payload 引用置空断言，修复 PR 附带最小复现——C5 文件复核节已同向建议）。 |

### 4.3 大规模测试审查（步骤 5）

| 审查项 | 结论 |
|-------|------|
| 声明包络 | owner doc 声明批量包络：batch-size=100 × maxScanLoops=1000（scanner drain 上限 10 万）、worker max-concurrency=0（不限）、poll-interval=5000ms/poll-threads=2。 |
| 边界测试现状 | drain/分页边界有**小规模参数化模拟**：TestJobTimeoutChecker `testDrainAllTimedOutTasksInOneCycle`（25 task/batchSize）、`testDrainMixesTaskTimeoutAndDispatchTimeoutAndStaleWaiting`（25+15+10 混合 drain、cursor 序列断言）、`testCursorResetBetweenCycles`、TestAbstractBatchScanner `testDrainBatchFullBatchWithNullSortKeyOnLastRow...`——跨批边界（25>10 触发 3 fetch）覆盖有效。 |
| 缺口 | (a) 满批边界值本身（size==batchSize 恰好触续扫）在 worker 侧仅由 `candidates.size() >= batchSize` 隐式经过，无显式边界用例（低风险）；(b) C5-004 的 max-concurrency=0 默认旁路态无专测（现有 max-concurrency 两测均为 >0 态——0=不限是声明语义，缺的是"backlog drain 单周期上限=1000×100 的行为可见性"，按声明边界不强制立案，登记为观察项）；(c) C5-001 的万级条目累积无测试（由 C10-001 的小规模终结断言替代——泄漏是否存在用 1 个 entry 的终结性即可判，无需万级压测）。 |
| 判定 | 大规模包络以小规模参数化模拟为主体的策略**基本成立**（drain/cursor/混合批次有代表性用例），无 P1 级缺口；观察项 (b) 随 C10-002 的容量释放断言一并覆盖。 |

---

## 5. 统计与覆盖声明

### 5.1 统计

- **缺保护登记条数**：9（C10-001 ~ C10-009，全部 P2；按缺陷/缺陷族聚合，引用宿主缺陷编号：C10-001→C4-001/C5-001/C7-001+C5-005，C10-002→C4-009/C7-002，C10-003→C2-001，C10-004→C2-007，C10-005→C4-003（含 C4-004 同族），C10-006→C7-011，C10-007→C2-004/C2-003，C10-008→C5-002，C10-009→时序敏感测试本体）
- **保护力矩阵规模**：6 行（P1×3 机制 + P2 抽验×3）× 变异结论；**会红 = 0 / 假保护 = 0 / 无测试红 = 6**。Phase 4 全部 P1 缺陷（C2-001、C4-001 族、C4-009）在现测试面下回归保护力为零。
- **时序敏感测试命中数**：2 处缺席断言形态（C10-009）+ 1 项结构性命中（即时完成 mock 系统性排除"慢/挂"形态，并入 C10-001/C10-002 描述）。
- **资源泄漏钩子**：全模块 1 处窄断言（监听器摘除）；现成 `activeCount()` 钩子零使用。
- **既有测试面总量**：67 个测试类（其中与本审计缺陷直接相关 ~10 类 / ~110 测试方法精读）。

### 5.2 覆盖声明与正面结论（防止修复阶段误删有效保护）

以下既有保护经核验**有效且须保留**：rpcPoll 六种终态映射（T9）、配置态墙钟超时（T9 + T10 E2E）、cancelToken 即时回调与监听器摘除（T9）、hung poll 隔离（T9）、DB 乐观锁竞争语义（T6/T7 顺序模拟）、sumReservedCost 全状态核算（T5）、CLAIMED worker-消失回收链（T8）、null promise/CAS 失败/单任务隔离（T13）、WAITING stale 重置（AR-88，T8）、once 语义与 SUSPENDED 热更新（T1）、手动时钟驱动的 TimeoutChecker 全家（T8，零真实时间依赖）。

### 5.3 覆盖边界

- 本维度为静态审计：未运行任何测试验证时序断言的实际 flake 率（C10-009 信心水平中档的原因）；建议修复 PR 落地时以 `-pl nop-job-local -am` 实测一次。
- 变异推演基于 Phase 4 复核文件所述修复方向（失败预算/deadline 兜底/DB 回传/占位前移/锁外 invoke/链感知/CLAIMED 门放开/批上限）；若最终修复采别的形态，对应条目的测试建议仍适用（断言的是行为不变量，非实现形态）。
- 未对 P3 条目（C2-005/C2-006/C2-008/C4-006/007/008/010/011/C5-003/C7 系 P3）逐条推演——按任务口径仅登记"发现现成测试保护或时序敏感命中"者；核读中未发现 P3 条目存在现成保护或时序命中（C4-006 关闭路径 awaitIdleScan 若实施可与 C10-005 的生命周期测试类共用设施，特此注记）。
- cra-index.md 无 nop-job 已知未修复登记项，本批 9 条均为全新登记。

---

## 维度复核结论（独立复核子代理）

- 复核子代理：C10 维度独立复核（与初审不同代理），日期 2026-10-15
- 复核基线：仓库 `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation，HEAD 335e77caec，工作树仅含本审计目录未跟踪文件）live test/main 代码逐条重验
- 复核方法：9 条登记逐条独立 grep/精读对应测试目录核对零命中声明；钉死修复形态声明逐行读测试验证；缺陷编号引用与 C1/C2/C4/C5/C7 各文件最终保留项逐条比对；建议测试形态按 live 代码结构抽查 2 条可行性；时序敏感 2 处命中逐处复核；未读 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/
- 复核输入：本文件（初审全文）+ 各维度文件最终保留项节 + `nop-job/*/src/test/java` 与 `*/src/main/java` 对应源码

### 抽查结果（零命中/假保护声明独立验证）

| 抽查对象（条目） | 独立验证手段 | 结果 |
|----------------|-------------|------|
| C10-001 零失败注入声明 | grep + 全文读 `TestRemoteJobInvoker.java`（742 行，实测 13 个 @Test）：`MockRpcPollTaskClient.getJobStatus`（:730-734）恒 `completedFuture`，`failedFuture` 仅出现在 `startJob`（:723-724）与状态豆 error 字段，getJobStatus 零失败注入；`grep -rn activeCount nop-job/*/src/test` 零命中（exit=1）；`testE2E_rpcPoll_wallClockTimeout` 实测 :1390 起、`setTimeoutSeconds(1)` 在 :1393，`testE2E_rpcPoll_fullChain`（:1347）全文无 timeoutSeconds 设置（本文件 setTimeoutSeconds 仅 :828/:871/:1393） | **证实** |
| C10-002 零未决 promise + 钉死面 | grep `TestJobWorkerScanner.java`（1353 行）：全部 invoker 为 `completedFuture`（31 处）或 `return null`（:1202，testNullPromiseTreatedAsError），零 `new CompletableFuture<>()`/`supplyAsync`/`orTimeout`；`sumReservedCost` 两处断言（:259、:455）均为占用方向等值断言，无释放方向断言；`testLongRunningTaskWithoutTimeoutNotKilled` 实测 :614-639：`executionTimeoutMs=-1` + `dispatchTimeoutMs=5000` + startTime 10 分钟前 → 断言保持 RUNNING，注释逐字引用 plan 340 §2.1 (P1-1)；live `JobTimeoutCheckerImpl:53`（executionTimeoutMs 默认 -1）与 :489-502（无配置即 return 不兜底 + 声明注释）核实 | **证实**（钉死面表述有一处过宽，见精确性备注 R5） |
| C10-003/C10-004 锁面/重入面零测试 | grep + 全文读 `TestLocalJobScheduler.java`（488 行，实测 14 个 @Test）：supplyAsync 恰在 :298/:348/:398（三个 running 态测试）；`testFireNow` invoker 仅计数 + completedFuture，全测试面无 invoker 回调 scheduler API 用例（grep fireNow/scheduler. 仅命测试自身持有 scheduler 的文件）；`TestBeanMethodJobInvoker.java` 零 scheduler 引用；live `LocalJobScheduler` 锚点 :321（invokeAsync）/:334（job.running=done）/:216-217（fireNow 判据）/:302-310（timer 回调锁内 executeJob）逐行核实；管理 API（getJobState :151/getJobDetail :64/awaitIdle :429/deactivate :232）全部 synchronized(job) 或锁获取轮询，awaitIdle 锁获取段不受 timeout 约束核实 | **证实** |
| C10-005 生命周期零测试 | `TestAbstractBatchScanner.java` 实测 5 个 @Test、7 处 `scanOnce()` 直调（:48/:78/:85/:126/:134/:199/:221），零 `startScanning`/`stopScanning` 调用；live `AbstractBatchScanner.getExecutor()` 恰在 :134-136（globalTimer().executeOn(globalWorker())）；`BindScheduledExecutor.java`（nop-commons）:101-103/:109-110 catch Throwable → `completeExceptionally` 无重入队——"链死"机制与登记描述一致 | **证实** |
| C10-006 肯定性回收缺失 | `testClaimedTask_reclaimedWhenWorkerGone`（:309-334，worker 不在存活集 → SUSPICIOUS → TIMEOUT）与 `testClaimedTask_notMarkedWhenWorkerAlive`（:337-358，保持 CLAIMED）逐行核实；`createTask` 恰在 :979-983 只设 id/fireId/status——该测试 task 的 startTime/claim 时钟均为 null，setTimeoutSeconds(60) 配置下仍无可计时钟，"非假保护、属肯定性缺失"判定成立；`TestJobStoreImpl:154-179` 可见性测试核实（认领时钟实为 startTime，`JobTaskStoreImpl:107-111` 认领时写入）；`testWorkerLiveness_*` 恰 5 个（:200/:227/:255/:284/:438）、RUNNING 执行超时族 4 个（:591/:642/:666 + :614）核实 | **证实** |
| C10-007 顺序契约 vs 并发窗口 | `testAddDuplicateJobThrowsAlreadyExists` 实测 :471-476：顺序两次 addJob，第二次抛 already-exists——只经入口 `jobs.get` existing 分支；live `LocalJobScheduler:110-120` raced 分支无 allowUpdate 校验、:113-114 并发 remove 下静默 return 核实；`jobs` 为 `ConcurrentHashMap`（:48），putIfAbsent 竞争窗口真实存在且测试面无双线程注册用例 | **证实** |
| C10-008 上限面零交集 | `TestNopJobTaskLogBizModel.java` 实测：batchInsert 用例批=2（`List.of(e1,e2)`，:69）、校验失败三态（:112-126）、空批（:131-132），全部 batch ≤ 2，无任何超限/超长用例；live `NopJobTaskLogBizModel.reportTaskLog`（:72-86）循环 validate+saveEntry 无条目数/单条体积上限核实 | **证实** |
| C10-009 时序敏感 2 处 | grep 全 nop-job 测试面 `Thread.sleep` 恰 5 处：TestLocalSchedulerResultContract:122（2s/20ms 有界忙等）、TestJobCoordinatorScanner:1448（deadline 轮询组成）、TestLocalJobScheduler:142/:163（sleep(300) 哨兵）/:193（sleep(200) 缺席断言）；`testUpdateWhileSuspendedStaysSuspended` 的 repeatInterval=10ms × 200ms 窗口 × `assertEquals(baseline, invokeCount)` 缺席断言形态逐行核实——回归时 10ms 间隔应产生 ~20 次 fire（方向正确），CI 饥饿下可能 0 次（假通过面真实存在） | **证实** |

抽查覆盖：9/9 条全部独立验证（超出任务要求的 4 条下限）；钉死修复形态声明（C10-002）按指令专项核读通过。

### 逐条判定表

| 发现编号 | 宿主缺陷终判级（对照维度文件最终保留项） | 判定 | 一句话理由 | 现状标注（缺保护登记继承宿主） |
|---------|-----------------------------------|------|-----------|------------------------------|
| C10-001 | C4-001 P1 = C5-001 P1 = C7-001 P1（三视角归并）；C5-005 P2 | 保留 | 三条修复面（失败预算/全局 deadline/DB 回传）在测试面均无红灯面：零 getJobStatus 失败注入、零 activeCount 断言、E2E 仅配置态超时——live 锚点与测试缺口全部实证 | 新发现 |
| C10-002 | C4-009 P2（C4 最终保留项）；C7-002 P2（同根因异维度对） | 保留 | 未决 promise 零用例 + sumReservedCost 仅占用方向 + checker 侧修复受 plan 340 声明契约测试与 :489-502 声明注释双向约束——钉死面成立（表述过宽处见 R5），worker 侧唯一修复通道确无回归网 | 新发现 |
| C10-003 | C2-001 P1 | 保留 | 三个 running 态测试 invoker 全为 supplyAsync（:298/:348/:398 逐行核实），BeanMethodJobInvoker 同步形态在 scheduler 测试零出现；锁范围修复在现有 13/14 测试下行为不可区分——缺保护登记的测试学特征成立 | 新发现 |
| C10-004 | C2-007 P2 | 保留 | 全测试面无自重入 fireNow/invoker 回调 scheduler 用例；:321 vs :334 占位滞后锚点精确；修复（占位前移）在即时完成 invoker 下不可观测 | 新发现 |
| C10-005 | C4-003 P2（C4-004 同 F9 族） | 保留 | startScanning/stopScanning 在整个测试面零调用（逐文件核实），BindScheduledExecutor Throwable→completeExceptionally 无重排机制核实——生命周期入口连烟测都没有 | 新发现 |
| C10-006 | C7-011 P2 | 保留 | 两既有测试分别覆盖"worker 消失"与"无时钟不触碰"，肯定性"存活+时钟超限→TIMEOUT"零断言；notMarkedWhenWorkerAlive 的 task 无计时字段（createTask :979-983）——"非假保护、属肯定性缺失"判定经逐字段核对成立 | 新发现 |
| C10-007 | C2-004 P2、C2-003 P2（同分支家族） | 保留 | 顺序重复注册有保护（:471-476），raced 分支需双线程 putIfAbsent 毫秒级窗口——测试面零并发注册用例，修复前后唯一可见差异无测试可区分；信心水平"中高"与概率性测试形态的自我声明一致 | 新发现 |
| C10-008 | C5-002 P2 | 保留 | 全部功能用例 batch ≤ 2，上限修复的拒绝分支与默认限值选择零断言——"batch=2 是能暴露默认限值误伤事故的最小用例但无反向断言"风险论证成立 | 新发现 |
| C10-009 | （时序敏感测试本体，非宿主缺保护） | 保留 | 两处"固定 sleep 窗口 + 缺席断言"形态逐行核实，方向正确但窗口负载敏感；5 处 sleep 的三分类（缺席断言 2 / 有界忙等 1 / deadline 轮询组成 1 + 哨兵并计）与 grep 结果精确一致 | 新发现 |

**复核计数**：保留 9、降级 0、驳回 0——全部登记的零命中声明、钉死面、缺陷编号引用、时序命中均独立验证成立。

### 精确性备注（不改判级，修复阶段引用时须按此修正）

1. **R1 测试类总数**：本文件头部"67 个测试类"经 `find` 实测为 **61**（api 2 / codegen 1 / coordinator 20 / core 12 / dao 7 / local 5 / retry-adapter 1 / service 7 / web 2 / worker 4）；不影响任何条目的缺口判定。
2. **R2 测试方法计数**：§1.2 T1"TestLocalJobScheduler（13）"实为 **14**；T9 与 C10-001"TestRemoteJobInvoker（11）"实为 **13**；T13"~16"实为 **18**；T8"~30"实测 33（~口径可接受）。计数偏差不改变"零失败注入/零未决 promise/零生命周期调用"等定性结论（均逐行核实）。
3. **R3 C4-009 判级口径**：C4 文件最终保留项为 **P2**；本文件 §2 矩阵行 3 与 §5.1 将 C4-009 记作 P1（"任务清单口径"）与终裁不一致——C10-002 严重程度行的双口径声明（"P1（任务清单口径）/P2（C4 文件最终保留项）"）为准，矩阵行与 §5.1 的 P1 标签应按 C4 终裁 **P2** 读取；宿主判级不影响缺保护登记本身（P2 缺保护登记不依赖宿主判级）。
4. **R4 C10-001 证据完备性**：C10-001 证据节仅点名 `testCancelTokenListener` 一个非即时完成例外，未提 `testHungPollDoesNotBlockOtherPollTasks` 的同步阻塞 mock（§1.1 第 4 点已完整披露）；不构成缺口判定的遗漏。
5. **R5 C10-002 钉死面表述过宽**："任何 checker 侧默认墙钟兜底都会打红 testLongRunningTaskWithoutTimeoutNotKilled"在字面意义上过宽——若修复引入**新配置项**且默认值大于该测试任务年龄（10 分钟），该测试不会红。准确的钉死面是：**(a)** 任何兜底默认值 ≤ 10 分钟（即有实际保护力的默认值）必红该测试；**(b)** `JobTimeoutCheckerImpl:497-501` 声明注释已把"无配置任务的回收 = worker 存活链"定为设计契约（plan 340 §2.1 P1-1），checker 侧默认兜底属**声明契约违约**，须先改契约测试与注释——工程结论（checker 侧不可作修复位、只剩 worker 侧单行通道）不变。
6. **R6 C10-005 族关联口径**：C10-005 将 C4-004 记为"同族同修法"——F9（周期链 Error 死亡）族层面成立，但 C4 文件的归并声明是 C4-004↔C4-012（whenComplete 吞没族）；C4-003 的修复面（scanFuture 挂 whenComplete）与 C4-004 的修复面（pollOnce 包装）修法同形但分属两处代码，引用时以 C4 文件归并声明为准，不影响本条测试缺口判定。
7. **R7 认领时钟字段名**：C10-006 建议中"claimTime"字段实际不存在——认领时钟落库为 `startTime`（`JobTaskStoreImpl:107-111` 认领时写入，TestJobStoreImpl:171 断言其非空）；建议测试的时钟选择应表述为"startTime（认领时刻）"，回退链中已有 startTime 项，工程含义不变。

### 建议测试形态可行性抽查（2 条）

1. **C10-003 `TestLocalJobSchedulerManagementResponsiveness`**：**可落为单元测试**。`LocalJobScheduler` 构造器 `(IScheduledExecutor, Function<String,IJobInvoker>)` 已开放 invoker 注入（TestLocalJobScheduler 全部测试即用此形态），latch 阻塞 invoker 可直接构造 BeanMethodJobInvoker 同步形态；getJobState/getJobDetail/awaitIdle/deactivate 均为 public 且取同一 job 监视器，awaitIdle 锁获取段不受 timeout 约束已核（:429-446 synchronized 内检查）——(i)(ii)(iii) 三个断言点均可实现。
2. **C10-005 `TestBatchScannerSchedulingLifecycle`**：**可落为单元测试**。`startScanning/stopScanning` 为 public synchronized，`scanIntervalMs` 为 protected 字段（TestAbstractBatchScanner 与 AbstractBatchScanner 同包 io.nop.job.core，可直接注入短间隔或覆写 getExecutor 注入手动 executor）；(i) 生命周期烟测、(ii) Error 注入 + running 标志断言、(iii) 满载 executor 拒绝注入均无需改动产品代码。

### 不确定项（显式列出）

1. **C10-009 实际 flake 率**：静态审计未运行测试统计假通过/假红概率（初审已声明，维持"信心水平：形态高/概率中"）；建议修复 PR 落地时以 `-pl nop-job-local -am` 实测。
2. **C10-007 概率性覆盖**：不注入 seam 的双线程循环测试对毫秒级窗口的命中率未实测（初审自我声明为概率性，建议配合包内 seam；`jobs` 为 ConcurrentHashMap，包内可见 seam 若不动产品代码仅能经子类覆写间接构造，实际可行性留给修复阶段）。

## 最终保留项

| 编号 | 严重程度 | 被测面（缺口位置） | 一句话摘要 | 现状 |
|-----|---------|------------------|-----------|------|
| C10-001 | P2（宿主 C4-001=C5-001=C7-001 P1） | nop-job-coordinator 测试面（RpcPollTaskManager 终结面 :105-132/:175-207/:259-265） | rpcPoll 条目终结三修复面（失败预算/全局超时兜底/DB 终态回传）零测试：getJobStatus 零失败注入、activeCount() 零断言、E2E 仅配置态超时——P1 泄漏族修复无回归网（含 C5-005 驻留面） | 新发现 |
| C10-002 | P2（宿主 C4-009/C7-002 P2） | nop-job-worker 测试面（JobWorkerScannerImpl :297-302）+ TestJobTimeoutChecker:614-639 钉死约束 | worker 侧"永不完成的 promise"零用例、sumReservedCost 仅占用方向断言；checker 侧默认兜底被 plan 340 声明契约测试与 :497-501 声明注释钉死——C4-009/C7-002 修复只剩 worker 侧单行通道且无回归网 | 新发现 |
| C10-003 | P2（宿主 C2-001 P1） | nop-job-local 测试面（LocalJobScheduler :302-310/:215-222/:313-346） | 无"同步阻塞型 invoker × 管理 API 响应性"测试：三个 running 态测试 invoker 全为 supplyAsync，锁范围重构（P1 唯一修复）无锁外行为锚点 | 新发现 |
| C10-004 | P2（宿主 C2-007 P2） | nop-job-local 测试面（LocalJobScheduler :321 vs :334、:216-217） | fireNow 自重入递归/SOE 逃逸链零测试；占位前移修复及其引入的新窗口（占位已置但 invokeAsync 抛异常）均无双向锚点 | 新发现 |
| C10-005 | P2（宿主 C4-003 P2、C4-004 同 F9 族） | nop-job-core 测试面（AbstractBatchScanner :58-103/:134-136） | scanner 调度链生命周期（startScanning/stopScanning/running 真实性）零测试——生命周期入口无烟测，链死停摆修复无观测锚点 | 新发现 |
| C10-006 | P2（宿主 C7-011 P2） | nop-job-coordinator 测试面（JobTimeoutCheckerImpl isRunning 门 × CLAIMED） | CLAIMED 态肯定性回收（worker 存活 × 执行超时）零断言：两既有测试仅覆盖"worker 消失"与"无认领时钟不触碰"，修复的时钟选择无锚点 | 新发现 |
| C10-007 | P2（宿主 C2-004/C2-003 P2） | nop-job-local 测试面（LocalJobScheduler :110-120 raced 分支） | raced 分支 allowUpdate 契约仅顺序重复注册受保护（:471-476），并发双注册窗口零测试；并发注册/交错增删测试为概率性形态 | 新发现 |
| C10-008 | P2（宿主 C5-002 P2） | nop-job-service 测试面（NopJobTaskLogBizModel :72-86） | reportTaskLog 上限校验分支零测试：全部用例 batch ≤ 2，超限拒绝/默认限值不误伤/单条超长三面无断言 | 新发现 |
| C10-009 | P2（时序敏感测试本体） | nop-job-local 测试面（TestLocalJobScheduler :193 主、:142/:163 哨兵） | 两处"固定 sleep 窗口 + 缺席断言"在 CI 负载下存在假通过面（回归漏报）；方向正确但保护强度随环境波动 | 新发现 |

**复核后总量**：保留 9（全部 P2）、降级 0、驳回 0；保护力矩阵 6 组推演经抽查维持（会红=0/假保护=0/无测试红=6，其中 C4-009 行判级按 R3 备注以 P2 口径读取）。现状全部为"新发现"（继承各宿主缺陷维度的现状标注）。
