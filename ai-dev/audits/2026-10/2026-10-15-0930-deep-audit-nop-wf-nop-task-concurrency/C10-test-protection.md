# C10 测试保护力维度 — nop-wf + nop-task 并发审计（最后批次，盲审）

- **审计单元**：nop-wf（11 子模块）+ nop-task（11 子模块）
- **仓库根**：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation，Java 25）
- **维度定义**：不是"有没有测试"，而是"回归会不会红"——对 Phase 4 复核后的每条 P1 缺陷做变异推演：假设正确实现已补上又被人回退（变异），现有哪个测试会失败。
- **变异推演输入**：本目录 C1/C2/C3/C4/C5/C7/C8 七个维度文件的「最终保留项」节（13 条 P1 + 2 组高价值 P2 抽验）。
- **盲审纪律**：未读取 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/。cra-index.md 在本仓库不存在（仅有 `_tmp/cra-baseline-*.txt` 基线），「已知未修复」状态直接引自各维度文件最终保留项表内标注。

---

## 1. 既有并发测试盘点

全单元 `*/src/test` 共约 115 个 Java 测试文件。经逐文件筛查（并发原语 grep + javadoc + 断言形态核对），与并发/竞争/资源/大规模相关的测试如下。

### 1.1 真并发测试（起真实多线程竞速，仅 3 个）

| 测试 | 模块 | 保护对象 | 并发形态 | 保护力评估 |
|---|---|---|---|---|
| `TestGraphDrainRace`（nop-task-core，300 轮） | io.nop.task.step | GraphTaskStep drain 误判（plan344 修复） | CyclicBarrier 对齐双线程同时 complete 两个前驱 future；5s 上限 get；统计 falseDrain 计数最后 assertEquals(0) | **强**——javadoc 明确"修复前随机失败（红），修复后全部成功"，是本单元保护力标杆 |
| `TestGraphErrorSuccessRace`（nop-task-core，300 轮） | io.nop.task.step | graph 错误消费×成功分支竞速（plan 364 [05-07] 修复） | 同上 + 错误分支 completeExceptionally；显式断言不出现 ERR_TASK_GRAPH_NO_ACTIVE_STEP | **强**（同上） |
| `TestDaoTaskStateStoreConcurrentSameRow`（nop-task-ext，localDb=true） | io.nop.task.ext.reliability | saveStepState 条带锁（plan 364 [05-02] 修复） | CountDownLatch 齐射 2 线程 × 50 次同行 save；断言零异常 + version==100（无丢更新） | **强**（有真实 DB 乐观锁语义参与） |

**关键事实：这 3 个真并发测试保护的全部是不在本次 P1 清单内的已修复缺陷。对 13 条 P1 缺陷族，多线程竞速测试保护力为 0。**

### 1.2 单线程并发语义测试（测状态机/驱动器契约，不起线程竞速）

| 测试 | 模块 | 契约内容 | 与 P1 清单的关系 |
|---|---|---|---|
| `TestPhase4TerminalAndConcurrency` | nop-task-core | ①sync/async 失败驱动 XPL stack 一致；②driveTaskCompleted/driveTaskKilled 反射交替 20 轮（first-terminal-wins）；③SUSPENDED 任务 cancel 直接驱动 KILLED；④全局闸门注册表同源不驱逐 | ②③与 F1/F2（C1-001/C1-009）同点但**均为单线程串行化**，见 3.1 |
| `TestSuspendContract` | nop-task-core | SUSPEND 契约 8 断言点（挂起态持久化、resume 续跑、retry 包装不吞挂起等） | 覆盖挂起正常路径，无 cancel 交错（见 3.1） |
| `TestAsyncSuspendPropagation` / `TestSuspendSemantics` | nop-task-core | 异步 SUSPEND 经 Sequential/Selector/fork/parallel/graph 透传 | 与 P1 清单无交叉 |
| `TestTaskStepCancelledTerminalDriver` | nop-task-core | step 层 EXPIRED/KILLED driver + save 接线 | 单线程；cancel-check 出口语义 |
| `TestTaskKilledTimeoutResumeE2E` | nop-task-core | KILLED/TIMEOUT cross-restart resume 短路区分（TaskLevelSnapshotTaskStateStore round-trip） | 覆盖"终态后 resume"串行版，不覆盖"resume 期间被 kill"（见 3.1） |
| `TestTaskTerminalStateLifecycle` / `TestTerminalStatePersistence` | nop-task-core | task COMPLETED/FAILED driver + saveTaskState 接线 | 与 F1/F2 相邻 |
| retry 四件套：`TestTaskStepHelperRetrySyncReturn` / `RetryDelayExecutionCount` / `RetrySuspend` / `RetryAttemptPersistOrder` | nop-task-core | retry 同步单次返回、延迟重试每轮恰一次、SUSPEND 不置 COMPLETED、attempt 先落盘再进下一轮 | 全部断言**执行次数/持久顺序**，零断言**执行线程身份**（见 3.7） |
| `TestTaskFlowManager.testThrottle`（`test/throttle-01` 模型） | nop-task-core | fork 4 分支 sleep 1000ms + throttle(maxConcurrency=1, maxWait=10000)，断言总时长 ≥ 2000ms | 本单元**唯一** throttle 运行时行为测试；只锁"限流生效"，不锁嵌套/泄漏/超时（见 3.2、3.9） |
| `TestTaskFlowManagerGlobalStats` / `TestTaskMetricsGuard` | nop-task-core | resetGlobalStats 成对、metrics 判空 | 与 P1 清单无交叉 |
| `TestExecutorTaskStepWrapperAsyncBranch` | nop-task-core | executor wrapper whenComplete 成功/失败分支（NopException 双向），5s 上限 get | 不覆盖 Error 穿透（见 3.13） |
| `TestChildRuntimeCancelPropagation` | nop-task-core | 父 cancel 传播子 runtime（svcCtx=null 路径） | 取消传播面，与 P1 清单无交叉 |
| `TestReliabilityDecorators`（15+ 用例） | nop-task-ext | retry/timeout/rateLimit decorator 经 .task.xml 的运行时行为（计数、分类、honest-fail） | **不含 throttle decorator**；retry 用例仍只断计数（见 3.7） |
| `TestTaskLevelResume` / `TestDbBackedTerminalStateResume` / `TestMainStepEnvelopeResume*` | nop-task-ext | resume 短路 cross-restart E2E | 全部单线程顺序：execute→终态→load→resume；无并发 resume（见 3.14） |

### 1.3 wf 侧测试（全部单线程顺序提交）

| 测试 | 模块 | 内容 | 与 P1 清单的关系 |
|---|---|---|---|
| `TestExecGroupVoteSemantics` | nop-wf-core | ExecGroupSupport.shouldExecGroupComplete/Reject 判定函数（动态代理 + 真实 record/model default 方法） | **纯判定函数**，不含引擎并发完成路径（见 3.3） |
| `TestWorkflowEngineFlow` | nop-wf-core | 启动/迁移/终态/驳回/撤回/非法操作（InMemoryWfStore） | 顺序 |
| `TestWfTaskScanner`（7 用例） | nop-wf-scheduler | due/remind 扫描：happy path、ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS 容忍、getDueAction 单条失败不中断、非 Nop 容忍、remind 失败隔离 | 全部 proxy store + 1~2 条 fixture，单线程；无 LIMIT/收敛/fencing/重复触发面（见 3.8/3.11/3.12） |
| `TestWorkflowEngine` / `TestWorkflowEngineRegression` / `TestWorkflowServiceImpl` | nop-wf-service | execGroup AND/OR/VOTE/SEQ 语义、signal 不误激活 waiting 步骤、action record | 顺序提交 |
| `TestWorkflowExamples`（countersign/vote-sign/parallel-branch/inclusive-branch） | nop-wf-service | 会签全票→transition、split-and→join-and 合流 | **逐个 invokeAction 顺序驱动**——并发完成/并发到达竞态零覆盖（见 3.3/3.4/3.5） |
| `TestDaoWorkflowEngine` / `TestUseApproval*` / `TestBeeflowCase` 等 | nop-wf-service | DAO 引擎 / 审批组件 E2E | 顺序 |

### 1.4 盘点结论

1. **nop-task 侧并发测试基础设施成熟**（fake runtime 体系、snapshot store 体系、竞速模板各一），但成熟设施只被用于已修复缺陷的回归；P1 缺陷所在的 TaskImpl 状态转移、throttle、retry 调度、scanner 均未接入任何竞速模板。
2. **nop-wf 侧并发测试基础设施为零**：全模块 0 个多线程测试，0 个原子竞速构造（grep 全部 Atomic* 均为调用计数器）；`TestWorkflowExamples.testCountersign` 等用顺序动作序列模拟"会签"，对并发完成竞态构成系统性假保护。
3. **nop-wf-core 测试 mock InMemoryWfStore** 顶层为 ConcurrentHashMap（`InMemoryWfStore.java:36-39`），具备构造并发测试的最低可行性；bean 内部 list 字段非线程安全，建议并发测试优先走 DAO store 或加固 mock。

---

## 2. 保护力矩阵（P1 缺陷族 × 现有测试 × 变异结论）

变异问题统一为：**"补上正确实现后若被回退（恢复缺陷形态），现有哪个测试会红？"**

| # | 缺陷族（引用编号） | 缺陷本体严重度 | 现有最近测试 | 变异结论 | 判据 |
|---|---|---|---|---|---|
| F1 | C1-001 + C2-001 + C7-016（SUSPENDED 转移游离监视器 × cancel 竞态，kill 静默丢失） | P1 | `TestPhase4TerminalAndConcurrency.suspendedTask_cancel_drivesKilledWithoutResume` | **假保护**（单线程串行版）；变异后**无测试会红** | 该测试先置 SUSPENDED 再 cancel，中间无并发 SUSPENDED 写者；回退"转移纳入监视器+终态守卫"后，此测试的执行序列逐字节等价于变异后行为 |
| F2 | C1-009（resume 期间 KILLED 被 SUSPENDED 裸覆写，kill 后复活） | P1 | `TestTaskKilledTimeoutResumeE2E`、`TestSuspendContract.suspendPlain_suspendsAndResumes` | **无测试会红** | 两测试均为串行 round-trip：终态后 resume 短路、挂起后 resume 续跑；"resume 执行中 cancel + suspend 分支覆写"无任何测试构造 |
| F3 | C2-005 + C2-011（throttle 非重入信号量嵌套同 key 确定性死锁/超时，零显式配置可达） | P1（C2 判 P2，本清单聚合 P1） | `TestTaskFlowManager.testThrottle`（throttle-01） | **无测试会红** | throttle-01 为 fork 4 平级分支同 key 排队，无嵌套获取；回退重入检测后该测试行为不变 |
| F4 | C3-001（exec-group 并发会签迁移丢失：AND/SEQ 组互判未完成） | P1 | `TestWorkflowExamples.testCountersign`、`TestExecGroupVoteSemantics`、`TestWorkflowServiceImpl` execGroup* | **假保护**；变异后**无测试会红** | 全部顺序提交：第 n 个 agree 提交时第 n-1 个已完全落盘；并发双完成各自快照互判未完成的窗口在顺序执行中不存在；判定函数测试与引擎写路径解耦 |
| F5 | C3-011（末段并行步骤并发完成 → 实例永久滞留 ACTIVATED） | P1 | `TestWorkflowEngineFlow.testFinalActionEndsFlow`、TestWorkflowExamples 各 ended 断言 | **无测试会红** | 顺序场景 checkEnd 单侧总能看到对方已 history；竞态双盲区仅在并发退出时出现，无多线程 wf 测试 |
| F6 | C3-012（and/or-join 激活竞态 + join 实例重复创建） | P1 | `TestWorkflowExamples.testParallelBranch` / `testInclusiveBranch` | **无测试会红** | 顺序到达时 getNextJoinStepRecord 复用判定恒命中已有实例；并发双到达盲区无测试构造 |
| F7 | C3-002 + C4-002（sync=true + executor 同池嵌套线程饥饿死锁） | P1 | 无（测试 task 模型 grep 零 `sync="true"`，`TestFirstClassConfigValidation` 不含 sync×executor 互斥校验） | **无测试会红** | 缺陷形态（有界池饱和全员互等）连构建期校验都没有，更无运行时测试 |
| F8 | C4-001 + C4-008 + C3-013（retry 延迟重试体 + ORM 簿记恒跑共享 globalTimer，负载即全平台定时语义失准） | P1 | retry 四件套、`TestReliabilityDecorators` retry_* | **无测试会红** | 全部断言执行次数/attempt 持久顺序；fake 把 getScheduledExecutor 与 getThreadPoolExecutor 都返回 globalTimer（`TestTaskStepHelperRetryDelayExecutionCount.java:214-219`），线程身份零断言——把重试体改回/改离 globalTimer 均不变红 |
| F9 | C5-001 + C8-001（findDueActivatedSteps 无 LIMIT 全量载入 + 失败步骤 dueTime 不清、毒步骤每轮重入扫描集） | P1 | `TestWfTaskScanner` 7 用例 | **无测试会红** | store 为 proxy，`findDueActivatedSteps` 返回固定 1~2 条 list（`TestWfTaskScanner.java:476-477`），查询形状（LIMIT/filter）零断言；无"失败步骤下轮是否重入"的收敛性测试（现有容错测试只断言"单条失败不中断本批"，恰与缺陷行为兼容） |
| F10 | C5-003 + C8-004（动态 keyExpr 强引用注册表键空间无界；已知未修复，plan 364 [05-04] 声明边界之上） | P1 | `TestPhase4TerminalAndConcurrency.globalGates_strongRefRegistry_sameInstanceAcrossManyKeys` | **假保护（反向钉死）**；变异后**无测试会红** | 该测试断言的恰是缺陷赖以成立的"强引用永不驱逐"语义（"must never be evicted"）；若有人实施"驱逐/容量上限"修复，此测试反而会红——保护力与缺陷同向 |
| F11 | C4-003（wf 扫描 job yaml `invoker.bean=wfTaskScanner` 与实际 bean id=`nopWfTaskScanner` 失配，启用即 100% 失效停摆；已知未修复 2026-09 P2-3.1-001 同案） | P2（清单内 P1 批次） | 无 | **无测试会红** | 全单元无任何测试装配/解析 `wf-due-task-scan.job.yaml`/`wf-remind-task-scan.job.yaml`，无 bean 引用一致性校验测试（nop-job 侧也只测 coordinator 自身 wiring） |
| F12 | C7-001（WfTaskScanner 多节点无 fencing，双实例重复触发、remindCount 丢失更新） | P1 | 无（`TestWfTaskScanner` 无重叠扫描/双 scanner 用例） | **无测试会红** | 单节点顺序扫描是唯一被测形态；两个 scanner 实例并发 scanDueTasks 的构造零存在 |
| F13 | C4-007（wf 扫描 job 零失败韧性：fire 失败即 FAILED 永久停摆 / 挂死即 RUNNING 永久占位；形态 A 与 nop-job-C7-006 跨单元同面） | P2（清单内 P1 批次） | `TestWfTaskScanner` 单条失败隔离 3 用例 | **部分假保护**；job 生命周期面**无测试会红** | 现有测试保护"扫描方法体内单条失败不中断整批"；job 级 fire 失败/挂死→永久 FAILED/RUNNING 属 job 调度层（nop-job 单元），本单元零覆盖且无恢复 API 测试 |
| F14 | C8-006 + C8-013（catch(Exception) 漏 Error：throttle 许可永久泄漏 / executor promise 悬挂任务永驻非终态） | P2（高价值抽验） | `TestExecutorTaskStepWrapperAsyncBranch`（NopException 双向）、`TestTaskFlowManager.testThrottle` | **无测试会红** | 两测试的异常注入均为 NopException/RuntimeException 通道；`ThrottleTaskStepWrapper.java:50-53`、`ExecutorTaskStepWrapper.java:44` 的 catch(Exception) 对 Error 的漏接无任何 Error 注入用例 |
| F15 | C7-014（task 无执行级 fencing，同一 taskInstanceId 并发 resume 双执行；`TaskFlowManagerImpl.getTaskRuntime` 每次新建 TaskRuntimeImpl，监视器随实例对象失效） | P2（高价值抽验） | `TestTaskLevelResume` / `TestTaskKilledTimeoutResumeE2E.resume_inProgressNonTerminal_doesNotShortCircuit_mainStepRuns` | **无测试会红** | 全部串行 resume；"两次并发 getTaskRuntime + 并发 execute → 双跑"无构造；ACTIVE 不短路测试反而证实第二路 resume 会完整重跑 mainStep（缺陷行为的单线程投影） |

**矩阵结论：15 行（13 P1 族 + 2 P2 抽验）× 覆盖测试约 20 个。变异结论分布——会红：0；假保护：4（F1、F4、F10、F13，其中 F10 为反向钉死特例）；无测试：11。**

---

## 3. 缺保护登记发现条目

登记口径：每缺陷/缺陷族一条（同机制共用，引用全部缺陷编号）；严重程度统一 **P2（缺保护）**，与缺陷本体严重度分开计数。

### [nop-wf-nop-task-C10-001] TaskImpl SUSPENDED/KILLED 状态机竞态族零竞速覆盖，既有测试为竞态的单线程串行化（假保护）
- **缺陷编号**：C1-001 + C2-001 + C7-016 + C1-009（同点两族，修复面耦合，合并登记）
- **缺陷位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:154-161`（`taskState.setTaskStatus(TASK_STATUS_SUSPENDED); taskRt.saveTaskState();` 游离于监视器外、无终态守卫）；对照 `TaskRuntimeImpl.java:94-105`（cancel 侧 `synchronized(state)` 内 SUSPENDED→KILLED）
- **现有最近测试**：`nop-task/nop-task-core/src/test/java/io/nop/task/impl/TestPhase4TerminalAndConcurrency.java:155-170`（`suspendedTask_cancel_drivesKilledWithoutResume`）、`:120-150`（`terminalDrivers_alternatingOrder`）、`TestTaskKilledTimeoutResumeE2E.java` 全部、`TestSuspendContract.java:89-106`
- **证据片段**：`TestPhase4TerminalAndConcurrency.java:158-164` 先 `state.setTaskStatus(TASK_STATUS_SUSPENDED)` 再 `taskRt.cancel("ops-kill")`——两个写者在同一线程串行；`terminalDrivers_alternatingOrder` 只交替 driveCompleted/driveKilled，从不触及 SUSPENDED 写分支
- **严重程度**：P2（缺保护）
- **并发场景**：①ACTIVE 任务挂起返回值到达任意线程写 SUSPENDED 的同时 cancel 线程读 status==ACTIVE 走纯 token 路径 → kill 静默丢失（C1-001/C2-001/C7-016）；②resume 执行中 cancel 驱动 KILLED 后，suspend 分支随后裸写 SUSPENDED → 任务复活（C1-009）
- **触发时序（=变异场景）**：回退修复（SUSPENDED 转移纳入 `synchronized(taskState)` + 终态前置检查 + resume ACTIVE 重置）后，T1 在 suspend 回调线程执行 `setTaskStatus(SUSPENDED)` 前，T2 cancel 完成 KILLED 驱动并落盘；T1 随后裸写 SUSPENDED 再落盘——四个既有测试无一起线程，全部通过
- **现状**：假保护。`suspendedTask_cancel_drivesKilledWithoutResume` 锁定的是 plan 364 [05-03] 已修复的串行版行为（挂起后 kill 必达 KILLED），对并发写者交错的缺陷本体零约束
- **风险**：修复回退/重构（如把 thenCompose 回调移出监视器）后无红灯；该族缺陷的失败模式是"运维 kill 成功返回但任务复活"，安全语义级
- **建议**：新增 `TestTaskSuspendCancelRace`：(a) 真双线程竞速——线程 A 经真实 `TaskImpl.execute` 挂起路径写 SUSPENDED，线程 B 同时 `taskRt.cancel`，CyclicBarrier 齐射 × 200 轮，断言终态只允许 {SUSPENDED(未被 cancel 命中) 或 KILLED(终态)}，禁止"SUSPENDED 且 exception 已设取消"与"KILLED 后回退 SUSPENDED"；(b) 直接驱动变体——按 TestGraphDrainRace 模板，反射交替 `driveTaskSuspended`(修复后入口)/`driveTaskKilled` × 20 轮断言 first-terminal-wins 覆盖 SUSPENDED 分支
- **失效面枚举**：TaskImpl 挂起回调；TaskRuntimeImpl.cancel；resume 入口（TaskFlowManagerImpl.getTaskRuntime + TaskImpl.execute isTerminal 短路）；四个既有终态 driver 回归（修复引入新同步结构时）
- **信心水平**：高（写点、测试序列均已逐行核对）
- **误报排除**：`terminalDrivers_alternatingOrder` 对 COMPLETED×KILLED 有保护力，但那两条路径本就在监视器内、非本缺陷写点；本条不否认其价值
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-002] throttle 非重入嵌套死锁（零显式配置可达）无嵌套获取测试
- **缺陷编号**：C2-005 + C2-011
- **缺陷位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:41-56`；`TaskFlowManagerImpl.java:197-207`；默认 key=stepPath 使 `CallTaskStep.java:97`（global×自递归 call-task）与 `CallStepTaskStep.java:83`（call-step 同 stepRt）零配置可达
- **现有最近测试**：`TestTaskFlowManager.java:169-174`（testThrottle + `test/throttle-01/v1.task.xml`：fork 4 平级分支同 key 排队）
- **证据片段**：throttle-01 模型内 sleep 步骤无 call-task/call-step 子层；maxWait=10000 恒不超时
- **严重程度**：P2（缺保护）
- **并发场景**：持 permit 的 body 内嵌套获取同 key 信号量（self 递归或 call-step），按深度耗 permit，确定性 `ERR_TASK_THROTTLE_TIMEOUT` 或长阻塞
- **触发时序（=变异场景）**：加入重入检测修复后回退——嵌套获取测试将确定性超时红；现无该测试，故无红灯
- **现状**：无测试。唯一 throttle 行为测试只覆盖平级排队（限流生效性）
- **风险**：修复回退不可见；且默认 key=stepPath 的零配置失败路径（C2-011）连显式配置都不需要，最易被无感回归
- **建议**：新增 `TestThrottleNestedAcquisition`：(a) task 模型 `test/throttle-nested`：throttle 步骤 body 内 call-step 引用同 stepLib 步骤（同 stepRt），maxWait=500，断言确定性超时错误码（锁缺陷现状）或修复后重入放行语义（按修复裁定改断言）；(b) 单元级：构造包装栈两层同 key ThrottleTaskStepWrapper，内层 execute 断言不依赖外层释放
- **失效面枚举**：ThrottleTaskStepWrapper.execute；CallTaskStep/CallStepTaskStep 包装序（TaskStepEnhancer.wrap）；default key=stepPath 语义
- **信心水平**：高
- **误报排除**：testThrottle 的 ≥2000ms 断言确证了信号量跨 fork 分支互斥生效，本条仅指嵌套面无保护
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-003] wf exec-group 并发会签完成迁移丢失：全 wf 侧零多线程测试，会签用例为顺序假保护
- **缺陷编号**：C3-001
- **缺陷位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1264-1275`（各完成者独立快照判组）；`ExecGroupSupport.java:20-31`；`WorkflowManagerImpl.java:81-87`
- **现有最近测试**：`nop-wf/nop-wf-service/src/test/java/io/nop/wf/service/TestWorkflowExamples.java:225-251`（testCountersign：`actionAs` 逐个顺序提交）、`TestExecGroupVoteSemantics.java`（纯判定函数）
- **证据片段**：testCountersign 第 3 个 agree 前，前 2 个 agree 已完全落盘（`assertFalse(wf.isEnded())` 逐步断言）；`TestExecGroupVoteSemantics.java:83-177` 只喂固定 fixture 给 `shouldExecGroupComplete`
- **严重程度**：P2（缺保护）
- **并发场景**：AND/SEQ 组两成员并发完成，各自快照互判"组未完成"，双双重试后组迁移永久丢失，流程滞留
- **触发时序（=变异场景）**：加入组级原子迁移修复后回退——并发双 agree 测试将出现双 transition 或无 transition；现无该测试
- **现状**：假保护（顺序会签用例证明"组语义在无并发时正确"）
- **风险**：会签/串签是审批流核心卖点场景；并发迁移丢失为静默数据级故障
- **建议**：新增 `TestExecGroupConcurrentCompletion`（nop-wf-core，InMemoryWfStore 顶层已 ConcurrentHashMap）：countersign 模型 3 成员，2~3 线程 CyclicBarrier 齐射 invokeAction("review-agree") × 100 轮，断言每轮 exactly-once transition（confirm 步骤恰激活一次、组内步骤无双 exit、无滞留 ACTIVATED）
- **失效面枚举**：WorkflowEngineImpl.doAction→exec-group 判定与 doTransition；WorkflowManagerImpl；ExecGroupSupport（skipExecGroupMembers/activateNextSeqStep）；DAO 乐观锁交互（TestDaoWorkflowEngine 同步加并发变体）
- **信心水平**：高
- **误报排除**：TestExecGroupVoteSemantics 对判定函数的阈值语义（passWeight/passPercent/排除 CANCELLED）有真实保护，本条仅指引擎并发写路径
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-004] wf 流程级自动终结竞态（末段并行步骤并发完成实例滞留 ACTIVATED）无并发终结测试
- **缺陷编号**：C3-011
- **缺陷位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1578-1595`（checkEnd 双侧判定）；`store/AbstractWorkflowStore.java:178-180`
- **现有最近测试**：`TestWorkflowEngineFlow.java`（testFinalActionEndsFlow 等，顺序）、TestWorkflowExamples 各 `assertTrue(wf.isEnded())`
- **严重程度**：P2（缺保护）
- **并发场景**：流程最后两个并行步骤并发完成，两侧 checkEnd 的 isAllStepsHistory 均读不到对方未提交的退出 → 双双放弃终结，实例永久 ACTIVATED 且无 rescue
- **触发时序（=变异场景）**：加入终结原子化/补偿修复后回退——并发末段完成测试将出现 isEnded()==false 滞留；现无该测试
- **现状**：无测试（顺序终结名场景有保护）
- **风险**：滞留实例无自动出口，与 C5-001 扫描不收敛叠加后成为永久脏数据
- **建议**：新增 `TestWorkflowAutoEndRace`：parallel-branch 模型末段两步骤，双线程齐射完成动作 × 100 轮，断言 `wf.isEnded()` 每轮为真且终态恰为 COMPLETED 一次（doEndWorkflow 幂等由 `wf.isEnded()` 守卫，双终结可检测）
- **失效面枚举**：checkEnd/isAllStepsHistory；doEndWorkflow；AbstractWorkflowStore 步骤退出可见性
- **信心水平**：高
- **误报排除**：无
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-005] wf and/or-join 并发激活竞态 + join 实例重复创建：join 用例全部顺序到达
- **缺陷编号**：C3-012
- **缺陷位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:337-364`（getNextJoinStepRecord 复用判定快照盲区）、`:491-520`（checkWaitingJoinStep）；`AbstractWorkflowStore.java:108-133,183-197`
- **现有最近测试**：`TestWorkflowExamples.testParallelBranch:367-390`、`testInclusiveBranch`（逐个 agree 顺序到达 join）
- **严重程度**：P2（缺保护）
- **并发场景**：两上游步骤并发完成并发进入 join 创建：and-join 双实例（一支永久 WAITING）、or-join 双实例下游双跑（违反 xdef 契约）
- **触发时序（=变异场景）**：加入 join 创建原子化修复后回退——并发到达测试将出现 join 步骤实例数 > 1；现无该测试
- **现状**：无测试
- **风险**：join 实例重复直接导致下游业务动作双跑（副作用级）
- **建议**：新增 `TestJoinConcurrentArrival`：parallel-branch 模型，双线程齐射两个上游 agree × 100 轮，断言 join 步骤（按 stepName+joinGroup 查询）实例数恒 == 1、merge 下游步骤执行次数恒 == 1；or-join 模型同构断言
- **失效面枚举**：enterStep join 分支；getNextJoinStepRecord；checkWaitingJoinStep；`wf.getStore()` 实现双份（InMemory/DAO）
- **信心水平**：高
- **误报排除**：顺序 join 测试保护"单到达合流语义"，保留
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-006] sync=true + executor 同池嵌套线程饥饿死锁：测试模型零 sync 用法，无构建期互斥校验测试
- **缺陷编号**：C3-002 + C4-002
- **缺陷位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:118-120,159-160`（executor 包装先于 sync 包装入栈，sync 的阻塞 `sync()` 等待点在 `TaskStepReturn.java:208-213`）；`step/SyncTaskStepWrapper.java:17`；`step/ExecutorTaskStepWrapper.java:33-52`
- **现有最近测试**：无（`nop-task/*/src/test/resources` grep 零 `sync="true"`；`TestFirstClassConfigValidation` 矩阵不含 sync×executor）
- **严重程度**：P2（缺保护）
- **并发场景**：sync=true 且 executor 指向同一线程池的父/子步骤嵌套，有界池饱和后父在池线程上阻塞等子、子永远排不上队，全员永久互等（无超时无 kill 入口）
- **触发时序（=变异场景）**：加入构建期互斥（sync+executor 同池硬失败）或池隔离修复后回退——配置校验测试或小池（pool-size=1）死锁测试将红；现两者皆无
- **现状**：无测试
- **风险**：死锁形态为请求线程池整体冻结，故障爆炸半径最大
- **建议**：新增 (a) `TestSyncExecutorMutualExclusion`（nop-task-core builder 层）：sync=true + executor 配置的模型构建断言抛 ERR_TASK_STEP_CONFIG_INVALID（若修复采构建期互斥）；(b) `TestSyncExecutorNestingDeadlock`：pool-size=1 专属 executor + 两层嵌套模型，5s 上限 get 断言可完成或确定性失败（锁运行时行为）
- **失效面枚举**：TaskStepEnhancer.wrap 包装序；SyncTaskStepWrapper.sync() 等待点；ExecutorTaskStepWrapper 派发；task.xdef 校验
- **信心水平**：高
- **误报排除**：`TestSuspendContract` 用 globalWorker executor 但无 sync=true，不受影响
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-007] retry 延迟重试体恒跑共享 globalTimer：测试只断执行次数，线程身份零断言
- **缺陷编号**：C4-001 + C4-008 + C3-013（同池面聚合）
- **缺陷位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:268-270`（`getScheduledExecutor().schedule(action, delay, ...)` 直接调度业务体）；`step/RetryTaskStepWrapper.java:27-30`；`TaskFlowManagerImpl.java:76-81`
- **现有最近测试**：`TestTaskStepHelperRetryDelayExecutionCount.java:52-109`（断 executeCount==2）、`TestReliabilityDecorators` retry_*（断计数与分类）
- **证据片段**：`TestTaskStepHelperRetryDelayExecutionCount.java:214-219` fake 把 `getScheduledExecutor` 与 `getThreadPoolExecutor` 都返回 `GlobalExecutors.globalTimer()`——测试自身复刻了缺陷调度拓扑，且断言集合中无"执行线程 ∈ 隔离池"项
- **严重程度**：P2（缺保护）
- **并发场景**：负载上升时延迟重试的业务体 + saveState ORM 写占满 4 线程共享 globalTimer，全平台 delay/timeout/心跳定时语义静默失准
- **触发时序（=变异场景）**：加入"重试体改投隔离执行器（executor 配置生效于延迟路径）"修复后回退——现无测试断言线程来源，全部绿
- **现状**：无测试（执行次数契约保护良好，执行位置契约零保护）
- **风险**：平台级定时失准是跨模块静默故障；修复最易在重构中无声退化
- **建议**：新增 `TestRetryDelayExecutorIsolation`：fake 的 getScheduledExecutor 返回探针调度器（记录 schedule 的 executor 身份）、getThreadPoolExecutor 返回标记线程池；断言延迟轮次 action 实际执行线程属隔离池（修复后语义）或至少 schedule 载体非 globalTimer 单例（可断言 `Thread.currentThread().getName()` 前缀/`GlobalExecutors.globalTimer()` 引用不等）
- **失效面枚举**：TaskStepHelper.retry 延迟分支；RetryTaskStepWrapper；TaskFlowManagerImpl.getScheduledExecutor 解析链（C4-004 同点）；ExecutorTaskStepWrapper（隔离配置面）
- **信心水平**：高
- **误报排除**：retry 四件套对"每轮恰一次""attempt 先落盘"的保护真实有效，本条仅指线程身份面
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-008] 到期扫描无 LIMIT 全量载入 + 毒步骤不收敛：扫描测试用 proxy store，查询形状与收敛性零断言
- **缺陷编号**：C5-001 + C8-001（同点）
- **缺陷位置**：`nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:181-186`（findAllByQuery 无 LIMIT）；`nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:78-118`（失败路径不清 dueTime）
- **现有最近测试**：`TestWfTaskScanner.java`（storeProxy 固定 list，`:476-477`）；DaoWorkflowStore 的 findDueActivatedSteps 查询本体在测试中零调用（InMemoryWfStore/MockWorkflowStore 均返回空 list）
- **严重程度**：P2（缺保护）
- **并发场景**：dueAction 持续失败的步骤 dueTime 永不清除 → 每分钟重入扫描集；积压增长后单轮全量载入单会话，扫描时长与连接占用无界放大
- **触发时序（=变异场景）**：加入 LIMIT 分批或 dueTime 清除/退避修复后回退——现无测试断言查询带 limit、也无测试断言失败记录退出扫描集，全部绿
- **现状**：无测试。现有容错测试（单条失败不中断整批）锁定的恰是"失败后无事发生"的缺陷兼容行为
- **风险**：与 C4-007 停摆叠加构成"要么不跑要么全量跑"的双坏形态
- **建议**：新增 (a) `TestFindDueActivatedStepsQueryShape`（localDb）：插入 N 条 ACTIVATED+due 步骤，捕获 QueryBean 断言含 limit 且 == 声明批次；(b) `TestScanDuePoisonStepConvergence`：注入持续失败的 dueAction 记录 + 正常记录，跑 3 轮 scanDueTasks，断言失败记录第 2 轮起不再出现（修复后语义）或至少断言其 dueTime/重试计数被推进
- **失效面枚举**：DaoWorkflowStore.findDueActivatedSteps/findRemindActivatedSteps；WfTaskScanner.scanDueTasks 失败路径；IWorkflowStore 接口契约（LIMIT 语义进接口需迁移计划）
- **信心水平**：高
- **误报排除**：`testScanDueTasksContinuesAfter*` 3 用例保护批内隔离，保留；本条仅指查询形状与跨轮收敛
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-009] 动态 keyExpr 限流注册表键空间无界：既有回归测试反向钉死"永不驱逐"缺陷语义
- **缺陷编号**：C5-003 + C8-004（同点；已知未修复，plan 364 [05-04] 声明边界之上的残留面）
- **缺陷位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:197-215`（强引用 CHM + computeIfAbsent，key 含 taskName+keyExpr 求值结果）
- **现有最近测试**：`TestPhase4TerminalAndConcurrency.globalGates_strongRefRegistry_sameInstanceAcrossManyKeys:175-193`（断言"must never be evicted"）
- **严重程度**：P2（缺保护，且为**反向钉死**：现有测试会把"引入驱逐/上限"的正确修复打红）
- **并发场景**：keyExpr 含调用方输入直通时键空间随请求基数无界增长，进程生命周期持久驻留
- **触发时序（=变异场景）**：无任何测试断言键空间规模上界/告警后行为——修复（容量上限 + 超限策略）落地前无保护，落地时需同步改写 05-04 回归测试
- **现状**：假保护（同向钉死）。测试锁定的不变量（permit 池不因驱逐分裂）与缺陷不变量（键空间无界）共享同一实现选择
- **风险**：修复者若不读审计记录，会因 05-04 测试红灯而误判修复破坏回归、放弃修复
- **建议**：随修复一并改造：`globalGates_strongRefRegistry` 拆分为两条——(a) 保留"同 key 同实例"断言（防分裂语义）；(b) 新增 `TestGlobalGateRegistryBound`：动态 key 注入 cap+100 个，断言注册表 size ≤ cap 且超限行为（拒绝/降级/驱逐）符合裁定语义
- **失效面枚举**：TaskFlowManagerImpl.getSemaphore/getRateLimiter；warnIfOverCap 告警；C5-010 stats 拷贝；C7-017 告警抑制
- **信心水平**：高
- **误报排除**：本条不否定 05-04 修复（强引用解决的是缓存驱逐分裂），仅指出其对未来修复的反向约束未被文档化
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-010] wf 扫描 job yaml invoker.bean 失配（启用即 100% 停摆）：无任何配置装配测试
- **缺陷编号**：C4-003（已知未修复，2026-09 P2-3.1-001 同案）
- **缺陷位置**：`nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/job/conf/wf-due-task-scan.job.yaml:8`、`wf-remind-task-scan.job.yaml:8`（`bean: wfTaskScanner`）对照 `app-scheduler.beans.xml:9`（`<bean id="nopWfTaskScanner">`）
- **现有最近测试**：无。全仓库测试无 wf job yaml 装载/bean 解析用例
- **严重程度**：P2（缺保护）
- **并发场景**：job 触发即 ERR_JOB_BEAN_NOT_FOUND → FAILED 永久停摆（与并发正交的配置性 100% 失效，扫描并发面全部失效的前置门）
- **触发时序（=变异场景）**：修复 bean 名后回退——现无测试引用该 yaml，无红灯
- **现状**：无测试；且该缺陷属"已知未修复"，无保护意味着修复验证也只能靠手工
- **风险**：双 job 缺省 enabled=false 掩盖失效，一旦生产启用即全灭
- **建议**：新增 `TestWfScanJobWiring`（nop-wf-scheduler）：装载两个 .job.yaml，断言 `invoker.bean` 能在 BeanContainer 解析为 `WfTaskScanner` 类型 bean 且 method 存在（反射断言 `scanDueTasks`/`scanRemindTasks`）——单测形态即可在 CI 锁死失配回归
- **失效面枚举**：两个 job yaml；app-scheduler.beans.xml bean id；未来 bean 重命名
- **信心水平**：高
- **误报排除**：nop-job 侧 TestJobDispatcherContainerWiring 测的是 coordinator 通用 wiring，不覆盖 wf 业务 bean 名
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-011] WfTaskScanner 多节点/重叠扫描无 fencing：无双实例重复触发测试
- **缺陷编号**：C7-001（cross-ref C1-005 remindCount）
- **缺陷位置**：`nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:78-143`
- **现有最近测试**：无重叠扫描用例
- **严重程度**：P2（缺保护）
- **并发场景**：两节点（或同节点两实例）同刻扫描，dueAction 双跑（action source 副作用先于引擎状态守卫执行）、remindCount 丢失更新
- **触发时序（=变异场景）**：加入 fencing（owner 租约/扫描互斥）修复后回退——双 scanner 并发测试将出现重复触发计数 > 1；现无该测试
- **现状**：无测试
- **风险**：dueAction 双跑是用户可见副作用双执行（如重复发送通知）
- **建议**：新增 `TestWfTaskScannerOverlapFencing`：两个 WfTaskScanner 实例共享同一记录集，CountDownLatch 齐射 scanDueTasks × 50 轮，断言 dueAction 触发次数恒 == 1、remindCount 终值恒 == 轮数（无丢失更新）；修复采用租约语义时先断言第二个 scanner 让空
- **失效面枚举**：scanDueTasks/scanRemindTasks 入口；saveStepRecord；引擎状态守卫（ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS 容忍路径为现役兜底，修复不得破坏 testScanDueTasksIgnoresCurrentStepStatusRace）
- **信心水平**：高
- **误报排除**：单条失败隔离测试与本条正交
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-012] wf 扫描 job 零失败韧性：批内隔离有保护，job 级 FAILED/RUNNING 永久停摆零覆盖
- **缺陷编号**：C4-007（形态 A 跨单元同面 nop-job-C7-006，已知未修复；形态 B/使用面新发现）
- **缺陷位置**：`WfTaskScanner.java:96`；`nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:181-194`
- **现有最近测试**：`TestWfTaskScanner` 3 个 ContinuesAfter* 用例（保护方法体内单条失败隔离）
- **严重程度**：P2（缺保护，部分假保护：批内韧性测试可能给出"扫描链路有韧性"的错觉）
- **并发场景**：job fire 一次失败即 FAILED 永久停摆、一次挂死即 RUNNING 永久占位（无恢复 API），扫描级并发治理全部失效
- **触发时序（=变异场景）**：加入恢复/重试韧性修复后回退——本单元无 job 生命周期测试会红（形态 A 的保护归属 nop-job 单元测试面）
- **现状**：无本单元内测试；跨单元依赖 nop-job-C7-006 的修复与测试
- **风险**：与 C10-010 失配叠加：修好 bean 名后首次业务异常即永久停摆，故障从 100% 变为"首错即灭"
- **建议**：(a) 本单元新增 `TestWfScanJobResilienceContract`：以 job fire 失败注入（mock ormTemplate 抛错）断言扫描器可再次触发（无状态残留）；(b) 形态 A 的 FAILED 恢复语义登记为 nop-job 单元测试义务，本条留交叉引用
- **失效面枚举**：WfTaskScanner 异常外泄面；job 框架 fire 生命周期（nop-job）；DaoWorkflowStore 扫描查询
- **信心水平**：中高（形态 A 归属跨单元，本单元判定以"无保护"计）
- **误报排除**：ContinuesAfter* 用例真实有效，仅覆盖面不同层
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-013] catch(Exception) 漏 Error：许可泄漏与 promise 悬挂无 Error 注入测试
- **缺陷编号**：C8-006 + C8-013（同族）
- **缺陷位置**：`ThrottleTaskStepWrapper.java:36-53`（`catch (Exception e)` + body 同步抛 Error 时 whenComplete 未注册 → 许可泄漏）；`ExecutorTaskStepWrapper.java:44`、`RunOnContextTaskStepWrapper.java:29-36`（回调 catch(Exception) 漏 Error → ret/future 无终结路径）
- **现有最近测试**：`TestExecutorTaskStepWrapperAsyncBranch.java:82-111`（NopException 失败传播）、`TestTaskFlowManager.testThrottle`（无错误注入）
- **严重程度**：P2（缺保护）
- **并发场景**：body 抛 Error（OOM/AssertionError/栈溢出）→ 许可永久泄漏累计后该 key 永久全拒；executor 派发回调 Error → promise 悬挂、任务永驻非终态、清理与终态 driver 全跳过
- **触发时序（=变异场景）**：将 catch(Exception) 改为 catch(Throwable)（或 finally 归还）修复后回退——注入 Error 的测试将红；现无任何 Error 注入用例
- **现状**：无测试
- **风险**：泄漏与悬挂均为累积性静默故障（C8-013 实例 A 有平台层断连佐证）
- **建议**：新增 `TestStepWrapperThrowableBoundary`：(a) throttle：body 抛 `new AssertionError("boom")`，断言 execute 后 `semaphore.getStats().available==maxConcurrency`（或 tryAcquire 仍成功）；(b) executor/runOnContext：body 抛 Error，5s 上限断言返回 promise 必然终结且异常为该 Error
- **失效面枚举**：ThrottleTaskStepWrapper；ExecutorTaskStepWrapper；RunOnContextTaskStepWrapper；RetryTaskStepWrapper 内层 catch(Exception)（`:296-298` 同形态）
- **信心水平**：高
- **误报排除**：NopException 双向传播测试保护非 Error 通道，保留
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

### [nop-wf-nop-task-C10-014] task 并发 resume 无执行级 fencing：resume 测试全部串行，双执行零覆盖
- **缺陷编号**：C7-014
- **缺陷位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:121-134`（每次 getTaskRuntime 新建 TaskRuntimeImpl，监视器随实例对象失效）
- **现有最近测试**：`TestTaskLevelResume`、`TestTaskKilledTimeoutResumeE2E.resume_inProgressNonTerminal_doesNotShortCircuit_mainStepRuns:231-241`（后者恰证明第二路 resume 会完整重跑 mainStep）
- **严重程度**：P2（缺保护）
- **并发场景**：同一 taskInstanceId 两路并发 getTaskRuntime + execute（双击/重试/多入口），非终态行无互斥 → 业务体双跑 + 状态行互覆
- **触发时序（=变异场景）**：加入执行级 fencing（DB 租约/owner 检查/进程内 in-flight 表）修复后回退——并发 resume 测试将出现执行计数 2；现无该测试
- **现状**：无测试
- **风险**：业务副作用双执行；且 C7-009 指出 workerId 从不赋值，fencing 基础缺失使多节点面同样洞开
- **建议**：新增 `TestConcurrentResumeSingleExecution`：suspend-plain 模型挂起后，双线程并发 `getTaskRuntime(id)` + `execute` × 50 轮，ExecutionCounterBean 断言 mainStep/业务 bean 执行次数恒 == 1；修复采用租约时附加断言输家路收到确定性 fencing 异常（非静默成功）
- **失效面枚举**：TaskFlowManagerImpl.getTaskRuntime；TaskImpl.execute 终态短路（修复不得破坏 TestTaskLevelResume 串行契约）；DaoTaskStateStore（租约落点）
- **信心水平**：高
- **误报排除**：终态短路测试（KILLED/TIMEOUT/FAILED resume 重抛）保护另一机制，保留
- **复核状态**：已复核——保留（独立复核子代理 2026-10-15）

---

## 4. 时序敏感 / 资源泄漏 / 大规模测试审查

### 4.1 时序敏感测试审查（命中 5 处）

| # | 位置 | 形态 | 判定 |
|---|---|---|---|
| T1 | `StepStateTestHelper.asyncSuccess:43`（nop-task-ext） | `CompletableFuture.supplyAsync` 内 `Thread.sleep(delayMs)` 强制异步未决路径；测试线程侧经 `syncGetOutputs` 正确等待 | **可接受**——sleep 用于制造"尚未完成"窗口而非同步时序；负载下早完成会走等价 already-done 快捷路径，计数断言不受影响。无红灯风险 |
| T2 | `TestSuspendContract.execute:60-71`（nop-task-core） | javadoc 明确记录历史 flaky："此前未等待就断言 isSuspend()，负载下 promise 未完成时 nextStepName=null 判 false——时序 flaky"，已用 `ret.sync()` 修复 | **正面样本**（本单元唯一已修复的"未等待异步就断言"缺陷；同时提示 async 路径断言需全量排查该形态——当前其余 async 测试均有上限 get，未见同类残留） |
| T3 | `TestExecutorTaskStepWrapperAsyncBranch:53,115`、retry 四件套（10ms 定时器延迟） | 固定短延迟制造异步窗口 + `get(5s)` 上限等待 | **形态正确**——有上限等待而非裸断言；超时即失败不悬挂 |
| T4 | `TestTaskFlowManager.testThrottle:169-174` | 墙钟下界断言（`>= 2000ms`）验证限流串行化 | **可接受但单向**——下界断言不会 flaky；但无上界断言，maxWait 语义、排队公平性、以及"4 分支×1000ms 恰好 4000ms 完成"的上界漂移（如 globalTimer 饥饿 C4-001 发作时该测试会拖长但不红——除非超 maxWait=10000 抛超时）不可检测。注：若 globalTimer 饥饿真发生，maxWait=10000 超时会令该测试红，构成 C4-001 的一点间接边际保护（弱、非确定） |
| T5 | `TestPhase4TerminalAndConcurrency.suspendedTask_cancel_drivesKilledWithoutResume` | 竞态的单线程串行化（superficially-pass 形态）：测试自身正确，但对并发缺陷本体零约束 | **假保护**，已登记 C10-001 |

补充核查：全单元测试代码 Thread.sleep 仅 T1 一处（grep 证实）；无"测试自己 catch 掉竞争后照常通过"形态——`TestGraphDrainRace` 的 falseDrain 计数聚合最终 `assertEquals(0, falseDrain)` 不吞失败，是正确的聚合竞速形态。

### 4.2 资源泄漏测试审查

- **结论：零泄漏检测钩子。** grep 全测试源：无 permit 归还断言（`availablePermits`/SemaphoreStats 零引用）、无线程池活跃度/liveness 断言（`getActiveCount` 零引用）、无 promise 未终结检测、无 `@AfterEach` 泄漏核对（仅 snapshot store 重置类 setUp/tearDown）。
- C8-006（throttle 许可泄漏）、C8-013（promise 悬挂）、C8-014（僵尸 body 占许可）三族完全无检测面；C8-015（扫描零指标）意味着运行期也无间接观测渠道。
- `TestPhase4TerminalAndConcurrency.globalGates_strongRefRegistry` 只测注册表实例同源性，不测 permit 记账。
- 建议的最小钩子形态：测试基类 `@AfterEach` 断言本轮用到的 global 信号量 `getStats()` 回满——单测形态即可覆盖 C8-006/C8-014 回归。

### 4.3 大规模测试审查

- **结论：零规模包络测试。** 全部数据面 fixture 规模：WfTaskScanner 用例 1~2 条记录；DaoTaskStateStoreConcurrentSameRow 2 线程 × 50 次（竞速放大器，非规模）；其余单任务单流程。
- C5-001（无 LIMIT）、C5-003（键空间无界）、C8-001（批量无上限）、C5-002（转办无上限）均无"小规模参数化模拟包络"测试（例如 3 条记录 + limit=2 断言分批正确、cap=3 + 5 个动态 key 断言超限行为）。当前任何 LIMIT/容量修复落地后，除 C10-009 所述反向钉死测试外无任何测试可验证其行为。
- 竞速放大器（300 轮 ×2 个 graph 测试）证明该团队掌握"以重复轮次补偿小规模"的技术路线，未推广到 P1 缺陷面。

---

## 5. 统计与覆盖声明

### 5.1 统计

| 项 | 数值 |
|---|---|
| 缺保护登记条目 | **14 条**（C10-001 ~ C10-014，全部 P2 缺保护） |
| 覆盖 P1 缺陷族 | 13/13（其中 4 族带假保护标注：F1/F4/F10/F13） |
| P2 抽验族 | 2/2（C8-006+C8-013、C7-014，均无保护） |
| 保护力矩阵规模 | 15 行 × 相关测试约 20 个 |
| 变异结论分布 | 会红 **0**；假保护 **4**；无测试 **11** |
| 既有真并发测试 | 3 个（GraphDrainRace / GraphErrorSuccessRace / ConcurrentSameRow），保护对象全部不在 P1 清单内 |
| 时序敏感命中 | 5 处（1 正面样本、2 可接受、1 单向墙钟、1 假保护） |
| 资源泄漏钩子 | 0 |
| 规模包络测试 | 0 |

### 5.2 覆盖声明

- 本维度逐行核对的测试：task-core 15 个、task-ext 4 个（+3 helper）、wf-core 2 个、wf-scheduler 1 个（7 用例）、wf-service 8 个（抽查 join/execGroup 相关用例全文）。其余测试（codegen/web/designer/ai）经 grep 判定与并发面无交叉，未逐行核对。
- 变异推演基于 Phase 4 复核后缺陷清单的最终保留项描述 + 本仓库生产代码现状逐点核对（TaskImpl:154-161、TaskRuntimeImpl:94-105、ThrottleTaskStepWrapper、TaskStepEnhancer.wrap、TaskStepHelper.retry、ExecutorTaskStepWrapper、RunOnContextTaskStepWrapper、DaoWorkflowStore:181-186、WfTaskScanner、WorkflowEngineImpl 三处、CallTaskStep/CallStepTaskStep、两个 job yaml、TaskFlowManagerImpl.getTaskRuntime/getSemaphore 均已直接读源确认）。
- 局限：(a) 变异推演为静态推演，未实际执行"回退-跑测试"实验；(b) nop-wf-service DAO 路径测试（TestDaoWorkflowEngine 等）在 localDb 下的隐藏并发行为未实测；(c) C10-012 形态 A 的保护归属在 nop-job 单元，本单元按"无保护"保守计入。
- 与修复阶段的接口：C10-001/C10-003/C10-004/C10-005/C10-011/C10-014 建议的并发测试均以本单元既有 300 轮竞速模板（TestGraphDrainRace 形态）为基线；C10-009 修复落地时必须同步改写 `globalGates_strongRefRegistry` 回归测试，否则正确修复会被误判为回归。

---

## 维度复核结论（独立复核子代理）

- 复核人：C10 独立复核子代理（与初审不同角色；职责为逐条验证、保留/降级/驳回，不补充新发现）
- 复核日期：2026-10-15；分支：concurrency-audit-remediation；HEAD `be0cbbb2a3`；以 live code 为准（`_` 前缀生成文件不作发现对象）
- 复核方法与输入：完整读取本文件；对照 C1/C2/C3/C4/C5/C7/C8 七个维度文件「最终保留项」节核对全部引用编号与判级；独立 grep `nop-wf/*/src/test` + `nop-task/*/src/test`（124 个测试文件）复核零命中/假保护声明；逐行重读抽查测试（TestPhase4TerminalAndConcurrency、TestTaskFlowManager.testThrottle + throttle-01 模型、TestWorkflowExamples.testCountersign、TestTaskStepHelperRetryDelayExecutionCount、TestWfTaskScanner 全文、TestTaskKilledTimeoutResumeE2E、TestSuspendContract、TestExecutorTaskStepWrapperAsyncBranch、TestFirstClassConfigValidation、TestExecGroupVoteSemantics、TestGraphDrainRace）；逐点重读抽查生产代码（TaskImpl.java:153-162、TaskRuntimeImpl.java:92-107、ThrottleTaskStepWrapper.java:36-66、ExecutorTaskStepWrapper.java:29-68、RunOnContextTaskStepWrapper.java:26-38、TaskStepHelper.java:262-284、TaskFlowManagerImpl.java:121-134/197-215、DaoWorkflowStore.java:180-194、WfTaskScanner.java:65-118、WorkflowEngineImpl.java:335-366/1262-1278/1576-1596、AbstractWorkflowStore.java:108-133/176-182、TaskStepEnhancer.java:114-160、TaskStepReturn.java:208-213、CallTaskStep.java:97、CallStepTaskStep.java:83、两个 job yaml、app-scheduler.beans.xml:9、InMemoryWfStore.java:36-40）。禁读 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 纪律已遵守（plan/bug/lesson 文件零读取；plan 364 [05-0x] 状态均取自各维度文件表内标注与本仓库测试 javadoc）。
- 现状标注依据：git log 确认分支对 `nop-wf/`、`nop-task/` 最新代码提交均为审计前的基础设施批次（WI5/WI9/WI10），工作区改动仅本审计目录；缺保护登记现状按对应缺陷本体现状继承。

### 抽查结果（按复核要点）

**要点 1——零命中/假保护声明抽查（实抽 8 条，超出要求的 5 条）**：

1. **C10-001（假保护）**：证实。`TestPhase4TerminalAndConcurrency.suspendedTask_cancel_drivesKilledWithoutResume`（:155-170）先 `state.setTaskStatus(TASK_STATUS_SUSPENDED)`（:161）再 `taskRt.cancel("ops-kill")`（:164），同线程串行、不经 TaskImpl 挂起写分支；`terminalDrivers_alternatingOrder`（:119-150）只反射交替 driveCompleted/driveKilled，从不触及 SUSPENDED 写点。回退"SUSPENDED 转移纳入监视器+终态守卫"后该测试执行序列不变、仍绿。全单元并发命名测试 grep 独立证实仅 4 个（Phase4/GraphDrainRace/GraphErrorSuccessRace/ConcurrentSameRow），与 §1.1 一致。
2. **C10-002（无嵌套测试）**：证实。testThrottle（TestTaskFlowManager.java:168-174）只断 `end-begin >= 2000`；throttle-01 模型为 fork 4 平级 sleep 分支同 key 排队，无 call-task/call-step 子层。嵌套获取回归无红灯。
3. **C10-003（顺序假保护）**：证实。testCountersign（TestWorkflowExamples.java:221-251）逐个 `actionAs` 顺序提交且逐步 `assertFalse(wf.isEnded())`——第 n 个 agree 提交时前 n-1 个已完全落盘；TestExecGroupVoteSemantics 仅喂固定 fixture 给 `shouldExecGroupComplete` 纯判定函数。wf 侧 5 个含并发类名的测试文件逐一核验：Atomic* 全为调用计数器/捕获引用，零 Thread/CountDownLatch/CyclicBarrier/start() 竞速构造，"nop-wf 侧 0 多线程测试"成立。
4. **C10-007（线程身份零断言）**：证实。fake 代理（TestTaskStepHelperRetryDelayExecutionCount.java:213-216）对 `getScheduledExecutor` 与 `getThreadPoolExecutor` 均返回 `GlobalExecutors.globalTimer()`——测试自身复刻缺陷调度拓扑；全部断言为 executeCount==2，无任何执行线程来源断言。
5. **C10-008（查询形状/收敛性零断言）**：证实。storeProxy（TestWfTaskScanner.java:476-477，行号精确命中）对 `findDueActivatedSteps` 返回固定 list，无 QueryBean/LIMIT 捕获与断言；ContinuesAfter* 用例只断"单条失败不中断本批"，与"失败步骤下轮重入扫描集"的缺陷行为兼容。
6. **C10-009（反向钉死）**：证实。`globalGates_strongRefRegistry_sameInstanceAcrossManyKeys`（:174-193）断言字面为 "must never be evicted"；生产侧 `warnIfOverCap` 日志原文 "strong-ref (never evicted, plan 364 [05-04])"（TaskFlowManagerImpl.java:210-212）——测试锁定的恰是缺陷赖以成立的强引用不驱逐语义，实施驱逐/上限修复会打红该测试。
7. **C10-010（零命中）**：证实。全仓库 nop-wf/nop-task 测试源 grep `wf-due-task-scan|wf-remind-task-scan|wfTaskScanner|nopWfTaskScanner` 零命中；失配本体独立复核成立（两个 job yaml `invoker.bean: wfTaskScanner` vs `app-scheduler.beans.xml:9` `<bean id="nopWfTaskScanner">`，行号精确命中）。
8. **C10-014（resume 全串行）**：证实。`resume_inProgressNonTerminal_doesNotShortCircuit_mainStepRuns`（TestTaskKilledTimeoutResumeE2E.java:229-241）串行构造 ACTIVE 快照→getTaskRuntime→execute；两个 resume 测试文件 grep Thread/CountDownLatch/CyclicBarrier/synchronized 均为 0 命中。"两次并发 resume 双跑"无任何测试构造。

**要点 2——「会红 0」矩阵行复核（实抽 4 行，超出要求的 3 行）**：F1（串行 suspend-cancel 测试对监视器/终态守卫回退不敏感，见上 1）、F9（proxy store 固定 list 对 LIMIT/退避修复回退无感，见上 5）、F11（零测试引用 job yaml，修复 bean 名后回退无红灯，见上 7）、F15（resume 全串行，fencing 回退无红灯，见上 8）——四行变异结论**无测试会红**全部独立成立，未发现任何被漏计的"会红"行。

**要点 3——缺陷编号引用一致性**：14 条登记引用的 24 个缺陷编号逐一对照各维度文件「最终保留项」表——全部存在、编号与同点归并关系一致、无已驳回编号被引用。族判级核对：C1-001/C2-001/C1-009/C3-001/C3-002/C3-011/C3-012/C4-001/C4-002/C4-008/C5-001/C5-003/C7-001 共 13 个 P1 全部入矩阵（F1/F2/F4~F10/F12）；C7-016 为 P3 但标注"同点 C1-001/C2-001 均 P1，留 7.1 统一"，C10-001 将其并入同点族恰当；C3-013/C8-001/C8-004 为 P2 但在维度文件中即标注与 P1 同池/同点对账（7.1），聚合入族有据。**一处计数口径瑕疵（不构成降级）**：矩阵"13 条 P1 缺陷族"中 F3（C2-005+C2-011 均 P2）、F11（C4-003 P2）、F13（C4-007 P2）三行无 P1 成员——F3 已在行内透明标注"C2 判 P2，本清单聚合 P1"，F11/F13 标注"清单内 P1 批次"；准确表述应为"13 个 P1 缺陷 ID 全覆盖 + 3 个高价值 P2 同批族"。另 §5.2 覆盖声明中"变异推演输入……13 条 P1"同此口径。登记条目的严重程度（全部 P2 缺保护）与缺陷本体判级分开计数，口径正确。

**要点 4——建议测试形态可行性抽查（2 条）**：

1. **C10-010 `TestWfScanJobWiring`**：可落为单元测试。nop-wf-scheduler pom 已依赖 `nop-job-local`（pom.xml:26），job yaml 在 main classpath（`_vfs/nop/job/conf/`），测试基类 BaseTestCase 体系现成（TestWfTaskScanner 即用）；断言 `invoker.bean` 可解析 + 反射断言 method 存在不依赖 IoC 容器全量装配，CI 可锁死失配回归。可行。
2. **C10-001 竞速测试**：可落为单元测试。同模块已有成熟模板 TestGraphDrainRace（300 轮 ITERATIONS + CyclicBarrier + falseDrain 聚合计数，逐行确认）；TaskRuntimeImpl/TaskStateBean/FakeStepRt 直构基础设施现成（TestPhase4TerminalAndConcurrency 即直构 TaskRuntimeImpl）。变体 (b) 所反射的 `driveTaskSuspended` 为"修复后入口"——建议文本已自行注明该前提，属修复耦合而非可行性缺陷。可行。
   附核：C10-004 建议中"doEndWorkflow 幂等由 `wf.isEnded()` 守卫"与 live code 一致（checkEnd 入口 `if (!wf.isEnded() && wf.isStarted())`，WorkflowEngineImpl.java:1581），"双终结可检测"的断言设计成立。

**要点 5——时序敏感 5 处命中复核**：

| # | 复核结果 |
|---|---|
| T1 | 证实。`StepStateTestHelper.asyncSuccess:43` 为全单元唯一 Thread.sleep（grep 全部 wf/task 测试源恰 1 处命中，与补充核查陈述一致）；sleep 用于制造未决窗口，判定"可接受"成立 |
| T2 | 证实。TestSuspendContract execute()（:58-70）javadoc 明文记录历史 flaky 根因与 `ret.sync()` 修复，"正面样本"判定成立 |
| T3 | 证实。`AWAIT_TIMEOUT_MILLS=5000`（:53）+ `syncGet` 有上限 get（:112-119）；retry 四件套 10ms 定时器延迟同形态，"形态正确"成立 |
| T4 | 证实。testThrottle 仅有 `>= 2000` 下界断言、无上界；"单向墙钟"判定成立，globalTimer 饥饿经 maxWait=10000 超时间接致红的边际保护推断与测试配置相符 |
| T5 | 证实。即 C10-001 已核的假保护形态，"假保护"判定成立 |

5 处判定全部维持；§4.1 补充核查"无吞失败竞速形态"经 TestGraphDrainRace `assertEquals(0, falseDrain)` 聚合断言确认。

### 不精确点（显式列出，均不构成降级/驳回）

1. §4.2"无 permit 归还断言（availablePermits/SemaphoreStats 零引用）"：`SemaphoreStats` 实有引用——TestTaskFlowManagerGlobalStats.java:52,60 用其 getAcquireCount 断言 resetGlobalStats 语义；但确无任何 permit 归还/泄漏断言（availablePermits/getActiveCount 全单元零命中）。"零泄漏检测钩子"的实质结论成立，仅括号内"SemaphoreStats 零引用"表述过宽。
2. §1"全单元 */src/test 共约 115 个 Java 测试文件"：实际计数 124（find src/test -name '*.java' 排除 target）。"约"字口径下偏差可接受，不影响任何盘点结论（并发相关测试清单逐一核验无误）。
3. §2 矩阵"13 P1 族"计数口径见要点 3——3 行无 P1 成员（F3/F11/F13），行内已透明标注，建议统计口径改为"13 个 P1 缺陷 ID 全覆盖（10 个纯 P1 族 + 3 个高价值 P2 同批族）"。
4. §1.2 表中 `TestTaskKilledTimeoutResumeE2E` 实际包路径为 `io/nop/task/state/`（模块标注 nop-task-core 正确，仅审计文件未写全路径，无引用错误）。

### 逐条判定表

| 编号 | 登记严重度 | 判定 | 一句话理由 | 现状标注（继承缺陷本体） |
|------|-----------|------|-----------|------------------------|
| C10-001 | P2 | 保留 | 假保护实证（串行化测试逐行核对）+ 缺陷写点（TaskImpl.java:153-162 游离 thenCompose 回调、无监视器无终态守卫）与 cancel 监视器侧（TaskRuntimeImpl.java:98-105）独立确认；四既有测试无一竞速 | 新发现（C1-001/C2-001/C1-009/C7-016） |
| C10-002 | P2 | 保留 | throttle-01 模型无嵌套获取证实；缺陷面（ThrottleTaskStepWrapper.java:41-56 非重入 tryAcquire + 默认 key=stepPath）与零配置可达路径（CallTaskStep.java:97/CallStepTaskStep.java:83 同 stepRt）独立确认 | 新发现（C2-005/C2-011） |
| C10-003 | P2 | 保留 | 顺序会签假保护 + wf 侧零多线程测试独立 grep 证实；缺陷面（WorkflowEngineImpl.java:1264-1275 各完成者独立快照判组）独立确认 | 新发现（C3-001） |
| C10-004 | P2 | 保留 | 顺序终断测试不覆盖并发双盲区；checkEnd 双侧 isAllStepsHistory 判定（WorkflowEngineImpl.java:1578-1595 + AbstractWorkflowStore.java:178-180）独立确认 | 新发现（C3-011） |
| C10-005 | P2 | 保留 | join 用例全顺序到达证实；getNextJoinStepRecord 复用判定快照盲区（WorkflowEngineImpl.java:337-364）独立确认 | 新发现（C3-012） |
| C10-006 | P2 | 保留 | 测试资源 grep 零 `sync=`、TestFirstClassConfigValidation 零 sync/executor 校验均独立证实；包装序（TaskStepEnhancer.java:117-119 executor 内层 × :159-160 sync 外层）与阻塞等点（TaskStepReturn.java:208-213）独立确认 | 新发现（C3-002/C4-002） |
| C10-007 | P2 | 保留 | fake 双执行器均返回 globalTimer（:213-216）+ 断言集零线程身份项逐行核对；缺陷面（TaskStepHelper.java:268-270 业务体直投 getScheduledExecutor）独立确认 | 新发现（C4-001/C4-008/C3-013） |
| C10-008 | P2 | 保留 | proxy store 固定 list（:476-477）+ 查询形状/收敛零断言逐行核对；缺陷面（DaoWorkflowStore.java:181-186 无 LIMIT + WfTaskScanner 失败路径仅 LOG 不清 dueTime）独立确认 | 新发现（C5-001/C8-001） |
| C10-009 | P2 | 保留 | 反向钉死实证（测试断言"must never be evicted"× 生产日志同义）；强引用注册表（TaskFlowManagerImpl.java:198-201）与 warn-once（:204-212 compareAndSet）独立确认 | 已知未修复（C5-003，plan 364 [05-04] 声明边界）/ C8-004 新发现（同点残留面） |
| C10-010 | P2 | 保留 | 零命中 grep 证实 + 失配本体（yaml `bean: wfTaskScanner` × beans.xml:9 `id="nopWfTaskScanner"`）行号精确复核 | 已知未修复（C4-003，2026-09 P2-3.1-001 同案） |
| C10-011 | P2 | 保留 | 无重叠扫描/双 scanner 用例独立证实；缺陷面（WfTaskScanner.java:78-143 无 fencing）与 C1-005 cross-ref（remindCount RMW）一致 | 新发现（C7-001，cross-ref C1-005） |
| C10-012 | P2 | 保留 | ContinuesAfter* 仅批内隔离逐行核对 + 本单元零 job 生命周期测试证实；scanDueTasks 整体经 runInNewSession 单入口（:65-69）无恢复 API 与登记描述一致 | 形态 A 已知未修复（C4-007，nop-job-C7-006）/ 形态 B 新发现 |
| C10-013 | P2 | 保留 | Error 注入用例零存在证实；catch(Exception) 漏 Error 三写点（ThrottleTaskStepWrapper.java:50-53 / ExecutorTaskStepWrapper.java:44 / RunOnContextTaskStepWrapper.java:29-31）逐行独立确认 | 新发现（C8-006/C8-013） |
| C10-014 | P2 | 保留 | resume 全串行 + 零线程构造 grep 证实；getTaskRuntime 每调用新建 TaskRuntimeImpl（TaskFlowManagerImpl.java:122-123）监视器随实例失效独立确认 | 新发现（C7-014） |

**复核计数**：保留 14 / 降级 0 / 驳回 0。全部 14 条缺保护登记成立，缺保护维度结论（会红 0 / 假保护 4 / 无测试 11）经独立验证维持。

## 最终保留项

（14 条缺保护登记全部保留；严重程度统一 P2（缺保护），现状继承对应缺陷本体。）

| 编号 | 严重程度 | 缺陷编号 | 一句话摘要 | 现状 |
|------|---------|---------|-----------|------|
| C10-001 | P2（缺保护） | C1-001 + C2-001 + C7-016 + C1-009 | TaskImpl SUSPENDED/KILLED 状态机竞态族零竞速覆盖，既有测试为竞态的单线程串行化（假保护） | 新发现 |
| C10-002 | P2（缺保护） | C2-005 + C2-011 | throttle 非重入嵌套死锁（零显式配置可达）无嵌套获取测试 | 新发现 |
| C10-003 | P2（缺保护） | C3-001 | wf exec-group 并发会签完成迁移丢失：全 wf 侧零多线程测试，会签用例为顺序假保护 | 新发现 |
| C10-004 | P2（缺保护） | C3-011 | wf 流程级自动终结竞态（末段并行步骤并发完成实例滞留 ACTIVATED）无并发终结测试 | 新发现 |
| C10-005 | P2（缺保护） | C3-012 | wf and/or-join 并发激活竞态 + join 实例重复创建：join 用例全部顺序到达 | 新发现 |
| C10-006 | P2（缺保护） | C3-002 + C4-002 | sync=true + executor 同池嵌套线程饥饿死锁：测试模型零 sync 用法，无构建期互斥校验测试 | 新发现 |
| C10-007 | P2（缺保护） | C4-001 + C4-008 + C3-013 | retry 延迟重试体恒跑共享 globalTimer：测试只断执行次数，线程身份零断言 | 新发现 |
| C10-008 | P2（缺保护） | C5-001 + C8-001 | 到期扫描无 LIMIT 全量载入 + 毒步骤不收敛：扫描测试用 proxy store，查询形状与收敛性零断言 | 新发现 |
| C10-009 | P2（缺保护，反向钉死） | C5-003 + C8-004 | 动态 keyExpr 限流注册表键空间无界：既有回归测试反向钉死"永不驱逐"缺陷语义 | 已知未修复（plan 364 [05-04] 声明边界之上的残留面） |
| C10-010 | P2（缺保护） | C4-003 | wf 扫描 job yaml invoker.bean 失配（启用即 100% 停摆）：无任何配置装配测试 | 已知未修复（2026-09 P2-3.1-001 同案） |
| C10-011 | P2（缺保护） | C7-001（cross-ref C1-005） | WfTaskScanner 多节点/重叠扫描无 fencing：无双实例重复触发测试 | 新发现 |
| C10-012 | P2（缺保护） | C4-007 | wf 扫描 job 零失败韧性：批内隔离有保护，job 级 FAILED/RUNNING 永久停摆零覆盖 | 形态 A 已知未修复（nop-job-C7-006）；形态 B 新发现 |
| C10-013 | P2（缺保护） | C8-006 + C8-013 | catch(Exception) 漏 Error：许可泄漏与 promise 悬挂无 Error 注入测试 | 新发现 |
| C10-014 | P2（缺保护） | C7-014 | task 并发 resume 无执行级 fencing：resume 测试全部串行，双执行零覆盖 | 新发现 |
