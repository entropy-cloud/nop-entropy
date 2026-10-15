# C2 锁与同步原语 — nop-wf + nop-task 第 1 轮初审（盲审）

审计单元：`nop-wf`（10 jar 子模块）+ `nop-task`（10 jar 子模块），379 files / 36,107 lines。
审计维度：C2 锁与同步原语（锁正确性 / 死锁排查 / 锁性能 / F5 等待点超时对照）。
审计日期：2026-10-15。分支：concurrency-audit-remediation。仅审计 live code，`_` 前缀生成文件不作发现对象。

## 检查范围说明

**锁载体盘点（人工全量核对 + 基线 `_tmp/cra-baseline-nop-wf-nop-task.txt` C2 节双重确认）**：

- **nop-wf 主代码：零 synchronized / 零 ReentrantLock / 零 CHM / 零 volatile**（独立 grep 复核命中全部位于 `src/test`：`MockWorkflowStore`、`TestWorkflowEngineRegression`）。nop-wf 源码中的「lock」为审批流业务语义（`checkApprovalFlowLocked` 的定义锁定），非并发原语，按任务书纪律不对 wf 找幻影锁。nop-wf 的并发正确性完全依赖「`WorkflowImpl` 每次 `getWorkflow`/`newWorkflow` 新建实例 → 单线程封闭」+ DB 乐观锁，见 C2-007。
- **nop-task 锁载体全量清单**（与基线一致，人工核对无遗漏）：
  - `TaskImpl.java:186/202/237/252` — 4 个 `synchronized (taskState)` 终态 driver 块；
  - `TaskRuntimeImpl.java:97` — `cancel()` 中 `synchronized (state)`；
  - `TaskStateBean.java:91` — `synchronized int newRunId()`（监视器 = bean 自身，与上面 taskState 块同一监视器对象）；
  - `TaskFlowModel.java:38/49/73` — 3 个 synchronized 惰性初始化方法（全同步访问，非 DCL，无发现）；
  - `TaskStepRuntimeImpl.java:181` — `synchronized addStepCleanup`；
  - `DaoTaskStateStore.java:357` — `synchronized (rowLock(...))` 64 槽静态条带锁（`ROW_LOCKS` 为 static final、槽位 final、从不替换，无锁对象替换问题）；
  - CHM：`TaskRuntimeImpl.attrs`（:36）、`TaskFlowManagerImpl.globalRateLimiters/globalSemaphores`（:63-64）、`GraphTaskStep.makeResults` 的 STEP_RESULTS CHM（:434）、`TaskExceptionRegistry.reflectiveMissing`（CHM-backed，:53）、`TaskStepRuntimeImpl.isFirstInstantiation` 经 `computeAttributeIfAbsent` 的 `ConcurrentHashMap.newKeySet()`（:174-175）。
- **computeIfAbsent/compute 全部 5 处逐一核对**：`WfModelAnalyzer.java:127`（方法局部 HashMap，模型构建期单线程，安全）；`DefaultWorkflowDOProvider.java:32`（`entity.computeIfAbsent`，平台 DynamicOrmEntity 设施，使用方式无异常）；`TaskRuntimeImpl.java:204`（mapping 仅 new 轻量对象）；`TaskFlowManagerImpl.java:178/199`（mapping 含 LOG.warn，见 C2-008）。无 mapping 内递归取同一 CHM、无持锁重 I/O（除 C2-008 低频日志）。
- **核实后排除的疑似项**（防误报记录）：`ParallelTaskStep.java:63-103` 与 `AbstractForkTaskStep.java:107-143` 的共享 `ArrayList promises`——初看疑似 any-join 下「调度线程 add × 聚合回调线程 iterate」竞态，经逐帧核查：`promises.add` 全部发生在 `AsyncHelper.waitAsync` 注册之前，而聚合 `thenApply` 只能经 wait future 的完成链 HB 之后执行（CompletableFuture 内部 CAS/volatile 提供 happens-before），add 与 iterate 无并发窗口，**排除**；`GraphTaskStep.stepResults` 为 CHM、`errorConsumers`/`stepFutures` 构建后只读，**排除**；`TaskExceptionRegistry.factories/registeredFqcns` 仅构造器内写、运行期只读，**排除**；`WfTransitionToType/WfExecGroupType` 的 static HashMap 在 static initializer 中填充，类初始化 HB 保障，**排除**；`WorkflowEngineImpl.java:893` 的 `Thread.sleep(delay)` 为同步重试退避，次数有界（maxRetryCount+1）、中断响应正确（恢复中断位并传播原始异常），非 F5 无界等待，**排除**。
- **F5 等待点全量清单**：`TaskStepHelper.timeout`（定时器兜底，达标）；`ThrottleTaskStepWrapper:41`/`RateLimitTaskStepWrapper:37` 的 `tryAcquire(1, maxWait)`（有界，超时报 `ERR_TASK_THROTTLE_TIMEOUT`/`ERR_TASK_REQUEST_RATE_EXCEED_LIMIT`，达标）；`DelayTaskStep:47-52`（schedule 有界 + cancel 绑定，达标）；`ExecutorTaskStepWrapper:59-66`（取消兜底 completeExceptionally，达标）；**不达标项**：`TaskStepReturn.sync()`（:185-189）→ `FutureHelper.syncGet` → `IContext.syncGet` 无超时，全部无条件调用点登记于 C2-010。

**严重度分布**：P1 × 1，P2 × 5，P3 × 4。

---

## 发现条目

### [nop-wf-nop-task-C2-001]
1. **发现ID**：nop-wf-nop-task-C2-001
2. **严重度**：P1（罕见但可具体化竞态——用户显式 kill 静默丢失，状态机与运维意图背离）
3. **维度/类别**：C2 锁正确性——check-then-act 跨监视器边界（同一状态字段的一处转移绕过了其余全部转移使用的监视器）
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:153-162`（SUSPENDED 转移，无锁）；对照 `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java:97-105`（cancel 判定，持锁）
5. **标题**：任务进入 SUSPENDED 的写路径不经 taskState 监视器，与 cancel() 的 SUSPENDED 判定构成竞态，kill 挂起中的任务可静默丢失
6. **问题描述**：plan 364 [05-01/02-12] 已将 task 级四个终态 driver（COMPLETED/FAILED/KILLED/TIMEOUT）的「判定+写入+落盘」收敛到 `synchronized(taskState)` 原子序列；`TaskRuntimeImpl.cancel()` 对挂起任务的 KILLED 驱动（plan 364 [05-03]）也在同一监视器内读 `status == TASK_STATUS_SUSPENDED` 后决策。但 SUSPENDED 状态本身的写入（`TaskImpl.execute` 的 `thenCompose` 挂起分支）既不加监视器也不落任何 first-terminal-wins 框架，两个并发驱动方对同一 `taskStatus` 字段（plain field，非 volatile）形成无同步的 check-then-act。
7. **证据片段**（TaskImpl.java:153-162）：
```java
return stepReturn.thenCompose((ret, err) -> {
    if (err == null && ret != null && ret.isSuspend()) {
        // SUSPEND 不是终态（check2 P0-2 / plan 349 Phase 1）...
        taskState.setTaskStatus(TaskConstants.TASK_STATUS_SUSPENDED);   // ← 无 synchronized
        taskRt.saveTaskState();                                          // ← 无 synchronized
        return ret;
    }
```
   对照 TaskRuntimeImpl.java:97-105：`synchronized (state) { Integer status = state.getTaskStatus(); if (status != null && status == TASK_STATUS_SUSPENDED && !state.isTerminal()) { ... setTaskStatus(KILLED); saveTaskState(); } }`
8. **触发时序**：T1 = 任务执行线程，mainStep 已返回 suspend、进入 thenCompose 回调、尚未执行 :159；T2 = 运维 kill 线程，进入 `TaskRuntimeImpl.cancel`。交错：① T2 调 `svcCtx.cancel(reason)` 置取消 token（此后无人消费——驱动已返回 suspend）；② T2 进 `synchronized(state)`，读 `taskStatus` 仍为 ACTIVE(30)，`status == SUSPENDED` 不成立 → 跳过 KILLED 驱动直接返回；③ T1 执行 `setTaskStatus(SUSPENDED)` + `saveTaskState()`。终态：任务在 DB 中停留 SUSPENDED，kill 无任何报错、无任何落盘痕迹，面板显示可恢复，resume 后继续执行被运维明确取消的任务。反向交错（T2 先读到 SUSPENDED、T1 后落盘）因两线程操作同一 ITaskState bean，最终 DB 值仍为 KILLED，但 T1 的无锁 `updateEntityDirectly` 与 T2 的写碰撞 ORM version，产生与任务语义无关的乐观锁异常从 thenCompose 冒泡。
9. **影响面**：所有使用 `<suspend>` 且支持运维 kill 的持久化任务（nop-task 挂起恢复是 owner doc 载明的一等契约）；kill 挂起任务是 plan 364 [05-03] 明确支持的主路径操作。
10. **声明边界对照**：nop-task.md:67 载明「`cancel()` 对处于 SUSPENDED（无驱动在飞）的任务直接驱动 KILLED」——「无驱动在飞」是设计前提，但 SUSPENDED 转移的在飞窗口未被任何守卫覆盖，前提在窗口期不成立且不可检测。
11. **F 族对照**：F3（check-then-act 分离——正是 plan 364 在终态 driver 上修掉的同类形态，SUSPENDED 转移是漏网的一处「修实例不修类别」残留）；F9 相关（丢失无日志）。
12. **修复建议**：将 :159-160 收进 `synchronized (taskState)` 并复用同一判定形态（进入监视器后重读状态：若已被 KILLED/TIMEOUT 等终态占据则放弃 SUSPENDED 转移；若保持非终态则写 SUSPENDED + save）；或在 `cancel()` 未能命中时对 ACTIVE→(驱动已返回) 场景补一次终态复核。
13. **置信度**：高（交错窗口为毫秒级但客观存在；字段非 volatile 使可见性进一步无保障；同文件 :186/202/237/252 的注释自证该形态在终态侧已被裁定为必须加锁的缺陷家族）。
14. **复核状态**： 已保留（P1——live code 逐行复核成立：TaskImpl.java:159-160 无锁 SUSPENDED 转移 × TaskRuntimeImpl.java:97-105 监视器内判定，交错与双向后果（kill 静默丢失 / 乐观锁异常冒泡，后者经 saveTaskState:257-260 updateEntityDirectly + DaoTaskStateStore:332-337 version 碰撞注释确证）推演成立；与 C1-001 为同一代码点（C1 复核已保留 P1，跨线程对 TeamTaskFlowOrchestrator orTimeout 线程具体化），本条为 C2 锁正确性视角，cross-ref C1-001，合并裁定阶段归并）。现状：新发现

### [nop-wf-nop-task-C2-002]
1. **发现ID**：nop-wf-nop-task-C2-002
2. **严重度**：P2（JMM 可见性缺陷，触发窗口极窄，后果与 C2-001 同型）
3. **维度/类别**：C2 锁正确性——跨线程共享字段的 safe publication 缺失
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java:52`（`private ITaskState taskState;`）、:132-134（非同步 setter）、:79-81（构造器内 `svcCtx.appendOnCancel(onCancel)` 先行发布）；写入方 `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:99-102`（构造后调 `setTaskState`）
5. **标题**：taskState / metrics 为非 volatile 裸字段，经构造器注册的 onCancel 监听器跨线程读无 happens-before 保障
6. **问题描述**：`TaskRuntimeImpl` 构造器把 `this::cancel` 注册进 `svcCtx` 的取消监听器列表（:79-81），而 `taskState` 在构造完成之后才由 `TaskFlowManagerImpl.newTaskRuntime`/`getTaskRuntime` 经 `setTaskState` 写入。注册动作与 setTaskState 写之间无任何同步关系；取消触发线程（svcCtx.cancel 传播）读 `getTaskState()` 时 JMM 不保证看到该写。:73 注释自证「taskRt可能会被多线程访问」，但字段保护未落实（同类对照：:43-47 的 `cancelToken`/`stepState` 在 `TaskStepRuntimeImpl` 已显式 volatile）。
7. **证据片段**（TaskRuntimeImpl.java:79-81, 94-96, 132-134）：
```java
if (svcCtx != null) {
    svcCtx.appendOnCancel(onCancel);        // 构造器内先发布自身
}
...
ITaskState state = getTaskState();          // 跨线程读（cancel 路径）
if (state == null)
    return;                                  // 陈旧 null → 静默放弃 KILLED 驱动
...
public void setTaskState(ITaskState taskState) {   // 构造后另一时刻写入，非 volatile 非 synchronized
    this.taskState = Guard.notNull(taskState, "taskState");
}
```
8. **触发时序**：T1 执行 `newTaskRuntime`（或 `getTaskRuntime`）：构造 TaskRuntimeImpl（发布 onCancel）→ 尚未执行/尚未全局可见 `setTaskState`；T2 因外层 svcCtx 被取消而进入 `cancel()`：读 `taskState` 命中陈旧 null → :95-96 提前 return。此后任务照常挂起（SUSPENDED），KILLED 驱动永不发生——与 C2-001 同一用户可见后果（kill 静默丢失），但根因是可见性而非交错。
9. **影响面**：所有经 `svcCtx` 传播取消的任务运行时；窗口为构造与 setTaskState 之间，概率极低。
10. **声明边界对照**：无声明；`TaskStepRuntimeImpl` 头注释（plan 364 [05-06]）建立了「跨线程读写必须显式同步/volatile」的不变式，`TaskRuntimeImpl` 自身字段未纳入同一收敛。
11. **F 族对照**：F3 家族（task 级收敛时的遗漏面）；与 C2-001 叠加时后果相同。
12. **修复建议**：`taskState`（及 `metrics`）声明为 `volatile`；或将 onCancel 注册移到 `setTaskState` 之后（需保证 cancel 语义不回退）。
13. **置信度**：中高（数据竞争客观存在；现实硬件上 stale-null 窗口极窄，故按纪律标注「理论风险成分高」，但因可写出具体交错且与 P1 同后果，未降 P3）。
14. **复核状态**： 已降级至 P3（非 volatile 裸字段 +「构造器注册先于 setTaskState 写」的序为代码事实；但独立 grep 证实 svcCtx.cancel 在 nop-task/nop-wf 单元内零外部触发点（仅 TaskRuntimeImpl:88 自身重入），唯一实存跨线程 cancel 消费方（orTimeout 链、父子 runtime 传播）的 HB 均后于 setTaskState——后果交错不可具体化；且 cancel override 体不受 once 门控、逐次执行，重复 kill 可恢复。与 C1-round2 §4 对同一字段的定向核查结论（无可具体化可见性缺口，不立案）自洽。残余：latent 可见性缺口，未来若出现发布先于 setTaskState 的取消驱动即复活）；现状：新发现（cross-ref C1-round2 §4 同面核查）

### [nop-wf-nop-task-C2-003]
1. **发现ID**：nop-wf-nop-task-C2-003
2. **严重度**：P2（守卫非原子——当前代码未能具体化双驱动并发时序，按纪律标注「理论风险，未具体化」）
3. **维度/类别**：C2 锁正确性——非原子的 first-terminal-wins 守卫（F3「修实例不修类别」对照项）
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java:46-52`（succeed）、:55-63（fail）、:189-195（setStepStatus）；对照 task 级 `TaskImpl.java:182-258`（同一守卫语义已收敛到 `synchronized(taskState)`）
5. **标题**：step 级 first-terminal-wins 守卫（succeed/fail/setStepStatus）是 check-then-act 且无任何监视器，与 task 级「监视器内原子序列」的修复标准不对称
6. **问题描述**：owner doc（nop-task.md:57）声明「step 级 `fail()` 与 `succeed()` 对称携带 first-terminal-wins 守卫」；该守卫为 `if (isDone()) return;` 的前置检查 + 多字段写入，字段（`stepStatus`/`exception`/`resultValue`）均为 plain field。task 级同一形态的竞态（plan 364 [05-01] javadoc 自述「kill 线程与完成回调线程可同时通过判定……退化为 last-writer-wins」）已通过 `synchronized(taskState)` 修复；step 级保留了完全相同的形态却没有任何锁。此外 `TaskStepExecution` 的 sync 失败出口（:360-370 catch → `driveStepFailure`）与 async 出口（:316-327 thenCompose → `driveStepFailure`）调用 `fail()/setStepStatus()/saveTerminalStateIfDone()` 时均不持任何监视器，`DaoTaskStateStore.saveStepState` 的条带锁只串行化 DB 写、不串行化 bean 上的守卫判定。
7. **证据片段**（TaskStepStateBean.java:46-52, 189-195）：
```java
public void succeed(Object result, String nextStepId, ITaskRuntime taskRt) {
    // 终态守卫（plan 349 Phase 2）：已终态（如 kill 竞态先到）不得被覆写为 COMPLETED
    if (isDone())            // ← 无锁前置检查
        return;
    setResultValue(result);
    setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED);   // ← 多写非原子
}
...
public void setStepStatus(Integer stepStatus) {
    if (stepStatus != null && isDone() && !stepStatus.equals(this.stepStatus))   // ← check
        return;
    this.stepStatus = stepStatus;                                            // ← act
}
```
8. **触发时序**：未具体化——穷举当前驱动链（`TaskStepHelper.timeout` 的 winner 单链、`ExecutorTaskStepWrapper` 的 body/完成同线程、graph 每节点独立 state bean、fork 分支独立 newStepState）后，未能构造出两个线程同时驱动同一 `TaskStepStateBean` 的可达路径；但守卫的存在本身即声明了「可能存在后到驱动」的语义，一旦未来新增驱动入口（如 kill 直驱 step 终态）即复现 task 级已修的 last-writer-wins。
9. **影响面**：step 终态语义（EXPIRED/KILLED/FAILED/COMPLETED 的先到保留）、resume 重抛一致性（owner doc :57 载明的「resume 重抛的异常与状态标注不符」会在守卫失效时回归）。
10. **声明边界对照**：nop-task.md:57 声明该守卫为实现保障；声明隐含原子性假设，plain field 不满足。
11. **F 族对照**：F3「修实例不修类别」的直接对照点——task 级（C2-001 同文件族）加锁、step 级仅靠非原子守卫。
12. **修复建议**：将 step 终态驱动收敛到与 task 级同型的监视器（如以 stepState bean 为监视器包住 fail/succeed/setStepStatus+save 序列），或在接口 javadoc 明确「守卫非原子、驱动方必须单链」的约束并加守卫测试钉住驱动入口集合。
13. **置信度**：高（非原子性是代码事实；「当前无并发驱动入口」的穷举结论置信中等，故不升 P1、按未具体化留 P2）。
14. **复核状态**： 已保留（P2——succeed/fail/setStepStatus 无锁 check-then-act 逐行核实（:46-52/:55-63/:189-195，全文零 synchronized）；timeout winner 单链与 driveStepFailure 双出口（:327/:369）复核维持「无可具体化双驱动」结论；owner doc :57 声明守卫为实现保障而其非原子，P2 理论档与 C1 侧对同一失效面的 C1-004（已保留 P2）裁定自洽，cross-ref C1-004，合并裁定阶段归并）；现状：新发现

### [nop-wf-nop-task-C2-004]
1. **发现ID**：nop-wf-nop-task-C2-004
2. **严重度**：P2（锁语义不对称——理论风险，未具体化）
3. **维度/类别**：C2 锁正确性——同字段写同步读不同步的不一致锁纪律
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskStepRuntimeImpl.java:181-187`（`synchronized addStepCleanup`）与 :189-195（`runStepCleanups` 迭代+clear 无锁）
5. **标题**：addStepCleanup 加 synchronized 而 runStepCleanups 迭代同一 ArrayList 不加锁，二者锁纪律不一致
6. **问题描述**：`stepCleanups`（plain ArrayList）的写入口 `addStepCleanup` 用 `synchronized(this)` 保护，暗示作者预期存在跨线程调用；但唯一读取+清空点 `runStepCleanups()` 不取同一监视器。若「写端需要同步」的预期成立，则读端存在 CME / 丢失 cleanup 的窗口；若预期不成立（写端实际单线程封闭），synchronized 是无效成本且掩盖了真实约束。类头 javadoc（plan 364 [05-06]）声明所有可变字段写必须发生在创建线程或 future 链同步点——即声明了封闭纪律，但未解释 synchronized 的存在，二者互相矛盾。
7. **证据片段**（TaskStepRuntimeImpl.java:181-195）：
```java
@Override
public synchronized void addStepCleanup(Runnable cleanup) {   // ← 写端同步
    if (cleanup == null)
        return;
    if (stepCleanups == null)
        stepCleanups = new ArrayList<>();
    stepCleanups.add(cleanup);
}

@Override
public void runStepCleanups() {                               // ← 读端不同步
    if (stepCleanups != null) {
        stepCleanups.forEach(this::runCleanup);
        stepCleanups.clear();
    }
}
```
8. **触发时序**：未具体化——在当前主代码中未能找到绕过 future 链 HB 从另一线程调用 `addStepCleanup` 的可达路径（task 级 bean 容器 cleanup 经 `taskRt.addTaskCleanup` 走 `Cancellable`，非本列表）。
9. **影响面**：cleanup 丢失 = bean 容器/资源不 stop（与 `TaskImpl.execute:135-140` 的 `beanContainer::stop` 同类资源）；CME 会从 cleanup 驱动点冒泡。
10. **声明边界对照**：类 javadoc 的访问约束不变式（:24-30）是已声明边界，但与本方法的 synchronized 相互矛盾，边界自身不自洽。
11. **F 族对照**：F3 家族（同一字段两套纪律）。
12. **修复建议**：二选一——删除 `addStepCleanup` 的 synchronized 并在不变式注释中钉死「写仅限创建线程/future 链」；或让 `runStepCleanups` 同步取同一监视器（注意 cleanup 回调持锁运行的重入风险，宜复制后清空再跑）。
13. **置信度**：高（不对称是事实；风险定性为理论）。
14. **复核状态**： 已降级至 P3（锁纪律不对称属实；但独立 grep 证实 addStepCleanup 零生产调用方（仅 ITaskStepRuntime:131 接口声明 + TaskStepRuntimeImpl:181 实现），runStepCleanups 仅 TryTaskStepWrapper:57/80 在 promise 链上调用——live code 不存在并发窗，风险面仅剩 XPL/自定义步骤扩展点（stepRt 经 VAR_TASK_STEP_RT 可达）；与同暴露面的 C2-009（P3）校准一致，不对称本身仍值得低成本收敛故保留为 P3 防御项）；现状：新发现

### [nop-wf-nop-task-C2-005]
1. **发现ID**：nop-wf-nop-task-C2-005
2. **严重度**：P2（边界配置缺陷——合法配置下可达的永久互斥饥饿，需显式共享 key 的非常规但可写出的配置）
3. **维度/类别**：C2 锁正确性——非重入信号量在递归/嵌套获取下的自死锁
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:35-56`；注册表 `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:197-207`（`globalSemaphores` 按 `taskName:key` 共享单一 `DefaultSemaphore` 实例）；错误定义 `nop-task/nop-task-core/src/main/java/io/nop/task/TaskErrors.java:169`
5. **标题**：throttle 全局信号量无属主/重入守卫，嵌套两个同 key throttle 步骤（如递归 call-task 显式共享 keyExpr）按深度消耗 permit 且永不释放，maxConcurrency 耗尽后该 key 永久死锁
6. **问题描述**：`tryAcquire(1, maxWait)` 成功后持 permit 执行 body，body 内若再次到达同 key 的 throttle 步骤（递归 call-task 两层配置同一 `keyExpr`，或 graph/parallel 分支与外层共享 key），内层获取需要新 permit；当嵌套深度 ≥ maxConcurrency 时内层全部 `ERR_TASK_THROTTLE_TIMEOUT` 或（maxWait 足够大时）长阻塞，外层因等待内层而永不 release——经典非重入互斥体自死锁。`getSemaphore` 的注册表按 `taskName:key` 强引用共享（owner doc :33 载明刻意跨租户共享），使该死锁面放大到同名任务的所有实例。
7. **证据片段**（ThrottleTaskStepWrapper.java:41-56）：
```java
if (!semaphore.tryAcquire(1, maxWait)) {
    throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_THROTTLE_TIMEOUT)
            .param(ARG_KEY, key);
}
try {
    TaskStepHelper.checkNotCancelled(stepRt);
    return getTaskStep().execute(stepRt).whenComplete((ret, err) -> {   // ← body 内可再进同 key wrapper
        semaphore.release(1);
    });
} catch (Exception e) {
    semaphore.release(1);
    throw NopException.adapt(e);
}
```
8. **触发时序**：任务 T 的步骤 S1 配 `throttle keyExpr="K" maxConcurrency="2"`；S1 经 `call-task` 递归调用自身，子任务对应步骤配置同一 keyExpr K（或父/子两个步骤显式同 key）。第 1、2 层各持 1 permit，第 3 层 tryAcquire 失败/阻塞；递归深度由输入数据驱动，超过 2 层即触发；已持有的 permit 依赖外层 body 完成，外层等待内层 → 环。global=true 时全平台同名任务共享该 permit 池，全部实例随之不可执行。
9. **影响面**：配置了显式共享 keyExpr 的嵌套/递归任务族；默认 key=stepPath 时父子步骤天然不同 key，不触发。
10. **声明边界对照**：owner doc :33 只声明了「global 限流器/信号量刻意跨租户共享、注册表强引用不驱逐」，未声明嵌套获取语义；无 fail-fast 守卫（构建期/执行期均不检测同 key 重入）。
11. **F 族对照**：死锁排查（C2-3）——本模块唯一可达的锁类死锁形态；F5 相关（maxWait 大时为无界阻塞窗口内的挂死，可检测性仅靠 ERR_TASK_THROTTLE_TIMEOUT 日志）。
12. **修复建议**：文档化「同 key 不得嵌套」约束并在 TaskStepEnhancer/执行期做 key 重入检测（如 stepRt attribute 记录当前线程/执行链已持 key 集，重入即构建期硬失败或即时报错），对称于 owner doc 已有的 `ERR_TASK_STEP_CONFIG_INVALID` 硬失败风格。
13. **置信度**：高（自死锁机理确定；触发依赖用户显式共享 key 的配置，故 P2 而非 P1/P0）。
14. **复核状态**： 已保留（P2——permit 横跨 body 全程（ThrottleTaskStepWrapper:46-48 whenComplete 释放）、global 注册表按 taskName:key 共享（TaskFlowManagerImpl:199）、嵌套同 key 获取机理逐帧核实成立；ERR_TASK_THROTTLE_TIMEOUT 定义于 TaskErrors.java:169 确证。保留意见：标题「永久死锁」措辞偏重——maxWait 为有限 int，实际后果为确定性超时失败或长阻塞，C2-011 已在同族内修正该框架，不影响判级）；现状：新发现

### [nop-wf-nop-task-C2-006]
1. **发现ID**：nop-wf-nop-task-C2-006
2. **严重度**：P2（锁内 I/O 登记——条带锁结构本身为已声明边界，锁内 I/O 代价与挂起时长未被声明覆盖）
3. **维度/类别**：C2 锁性能——持锁 DB I/O / 模型加载 / JSON 序列化
4. **位置**：① `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:357-373`（64 槽条带锁内：findStepEntity SELECT + collectSecretProps 模型加载 + serializeStepStateData JSON 序列化 + save/update 写）；② `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:186-258` 四个终态 driver 持 taskState 监视器调 `taskRt.saveTaskState()` → `DaoTaskStateStore.saveTaskState:159-261`（entity SELECT + collectSecretProps + 多段 JsonTool.serialize + errorStack 提取 + UPDATE）；③ `TaskRuntimeImpl.java:97-105` cancel 路径同链路
5. **标题**：taskState 监视器与行条带锁的临界区内包含 DB 往返、VFS/DB 模型组件加载与 JSON 序列化，锁持有时间由最慢 I/O 决定
6. **问题描述**：`collectSecretProps`（:930-942）每次保存都调 `ResourceComponentManager.instance().loadComponentModel(path)`——缓存命中时廉价，但缓存失效/动态更新窗口内是全量模型解析（可能经 DB 资源源 + XDSL merge），发生在条带锁（①）或 taskState 监视器（②③）内。②③使 kill/cancel 线程在监视器上阻塞的时长 = 完成线程的整个「DB 读+模型加载+序列化+DB 写」序列；①使 64 槽条带锁成为全局 step-save 吞吐上限（≈ 64 / 单次保存时延），且 DB 连接池紧张时同槽行互相拖带。
7. **证据片段**（DaoTaskStateStore.java:357-372）：
```java
synchronized (rowLock(state.getTaskInstanceId(), state.getStepPath())) {
    NopTaskStepInstance entity = findStepEntity(...);          // ← SELECT 持锁
    ...
    Set<String> secretProps = collectSecretProps(              // ← 模型组件加载持锁
            stepRt.getTaskRuntime().getTaskName(), ...);
    copyStepStateToEntity(state, entity, secretProps);         // ← JSON 序列化持锁
    if (isNew)
        stepDao().saveEntityDirectly(entity);                  // ← INSERT/UPDATE 持锁
    else
        stepDao().updateEntityDirectly(entity);
}
```
8. **触发时序**：非竞态类发现，无交错时序；负载时序：DB RTT 10ms 时全局 step-save 吞吐上限 ≈6400/s，RTT 100ms（或模型缓存失效抖动）时 ≈640/s，同槽不同行保存互相排队，观测为与业务无关的状态落盘延迟尖刺；kill 线程可观测地阻塞在 `cancel()` 的监视器入口直至完成线程保存序列结束。
9. **影响面**：持久化模式（defaultSaveState=true）下所有 step 状态保存；运维 kill 的响应延迟。
10. **声明边界对照**：owner doc :69 + 类 javadoc :332-337 已声明「条带 64 槽、跨行同槽过度串行化、低频可接受、跨进程写为已知边界」——条带锁**结构**为已声明边界（不计缺陷）；但声明文本未覆盖「锁内模型组件加载」这一可长尾的 I/O 成分，属声明边界之外的代价项。
11. **F 族对照**：无（纯 C2-4 锁性能登记）。
12. **修复建议**：将 `collectSecretProps` 结果按 (taskName,version) 提到锁外预取（保存前计算，锁内仅消费）；条带锁内收缩为「读前持锁-读后释放-写前重验-version 条件更新」或直接依赖 ORM version 条件更新收敛冲突；taskState 监视器内的 saveTaskState 可改为「监视器内拷贝快照+状态判定，锁外持久化+失败回滚重试」。
13. **置信度**：高（I/O 在锁内为代码事实）。
14. **复核状态**： 已降级至 P3（锁内 I/O 为代码事实（saveStepState :357-373 / saveTaskState :159-261 / collectSecretProps :930-942 均逐一核实），但：① 条带锁 read-copy-write 结构与「低频可接受」为类 javadoc :332-337 + owner doc :69 已声明的设计边界（初审第 10 项已自认「不计缺陷」）；② 监视器内 saveTaskState 是 plan 364「判定+写入+落盘收敛」原子性裁定的组成部分，属设计取舍而非缺陷；未声明残余仅为 collectSecretProps 缓存失效窗口的长尾成分——优化项。与同族「持有时长放大」的 C2-012（P3）校准一致。合并裁定阶段若认定持久化高负载为一等场景可复议回 P2）；现状：新发现

### [nop-wf-nop-task-C2-007]
1. **发现ID**：nop-wf-nop-task-C2-007
2. **严重度**：P3（当前无并发触发路径；防御性/文档性候选）
3. **维度/类别**：C2 锁正确性——非线程安全集合 + check-then-act 标志依赖未声明的实例封闭
4. **位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowImpl.java:41`（`HashMap steps`）、:46（`ArrayDeque commandQueue`）、:51（`boolean executing`）、:80-115（`delayExecute`/`executeNow` 的 check-then-act）
5. **标题**：WorkflowImpl 的命令队列/executing 标志/step 缓存完全非线程安全，正确性依赖「每请求新建实例」这一未在类型上声明的封闭约束
6. **问题描述**：`delayExecute` 的 `if (!executing)` check-then-act 与非线程安全 `ArrayDeque.poll` 在多线程下会产生命令孤儿（T2 读到 executing=true 跳过 drain、其任务在 T1 最后一次 poll 之后入队 → 永不执行）或 deque 结构性损坏；`steps` HashMap 并发 put/iterate 同理。当前唯一生产入口 `WorkflowManagerImpl.getWorkflow`/`newWorkflow`（:76/:87）每次调用都 `new WorkflowImpl(...)`，实例不出请求线程，封闭成立——但该约束仅隐含在 manager 实现里，类上无任何 javadoc 声明，也无断言守卫。
7. **证据片段**（WorkflowImpl.java:80-100）：
```java
public void delayExecute(Runnable task) {
    commandQueue.add(task);
    if (!executing) {              // ← check-then-act，非原子非 volatile
        this.executing = true;
        try {
            runCommandQueue();
        } finally {
            this.executing = false;
        }
    }
}
private void runCommandQueue() {
    do {
        Runnable command = this.commandQueue.poll();   // ← ArrayDeque 非线程安全
```
8. **触发时序**：未具体化（当前全部调用方单线程封闭）。假想触发：同一 WorkflowImpl 实例被两个请求线程共享（如未来引入 per-wfId 实例缓存）→ 双双读到 executing=false → 并发 poll 同一命令（重复执行/丢失/Deque 损坏）。
9. **影响面**：工作流步骤推进命令（`WorkflowEngineImpl` 8 处 `delayExecute`：join 检查、autoTransition 等）的恰好一次执行假设。
10. **声明边界对照**：无声明（与 C2-004 的自洽声明、nop-task.md 的显式边界相比，此处是纯隐式约束）。
11. **F 族对照**：无（无当前可达路径，故 P3）。
12. **修复建议**：在 `WorkflowImpl` 类 javadoc 与 `IWorkflowManager.getWorkflow` 契约处声明「实例线程封闭、禁止缓存共享」；若未来引入实例缓存，改 ConcurrentLinkedQueue + AtomicInteger drain 标志。
13. **置信度**：高。
14. **复核状态**： 已保留（P3——HashMap steps(:41)/ArrayDeque commandQueue(:46)/executing(:51) 与 :80-115 check-then-act 逐行核实；WorkflowManagerImpl:73-87 每次 getWorkflow/newWorkflow 均 new WorkflowImpl 的封闭现状确证；纯隐式约束、无可达路径，防御性候选定位准确）；现状：新发现

### [nop-wf-nop-task-C2-008]
1. **发现ID**：nop-wf-nop-task-C2-008
2. **严重度**：P3（优化候选——低频一次性路径的持 bin 锁 I/O）
3. **维度/类别**：C2 锁性能——computeIfAbsent mapping 函数内日志 I/O
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:178-182`（globalRateLimiters）、:199-203（globalSemaphores），mapping 内调 `warnIfOverCap`（:209-215，LOG.warn）
5. **标题**：CHM computeIfAbsent 的 mapping 函数内执行 LOG.warn（appender I/O），同 bin 其他 key 的注册被日志延迟拖带
6. **问题描述**：CHM computeIfAbsent 在 bin 级锁内执行 mapping 函数；`warnIfOverCap` 触发时在锁内写日志（控制台/远端 appender 可能慢）。触发条件为注册表大小达到配置容量阈值且每个注册表仅一次（AtomicBoolean CAS），为低频一次性路径。同 bin 的后续 `getRateLimiter/getSemaphore` 注册（新 taskName:key 首配）会在锁内等待。
7. **证据片段**（TaskFlowManagerImpl.java:178-182, 209-215）：
```java
IRateLimiter limiter = globalRateLimiters.computeIfAbsent(cacheKey, k -> {
    warnIfOverCap(rateLimiterCapWarned, CFG_TASK_MAX_GLOBAL_RATE_LIMITERS.get(),
            globalRateLimiters.size(), "global-rate-limiter");   // ← 锁内 LOG.warn
    return new DefaultRateLimiter(requestPerSecond);
});
```
8. **触发时序**：非竞态发现，无交错时序（性能登记项）。
9. **影响面**：容量阈值告警触发瞬间的全局注册吞吐；量级极小。
10. **声明边界对照**：owner doc :33 声明注册表容量告警契约；未涉及锁内日志。
11. **F 族对照**：无。
12. **修复建议**：将 size 采样移入 mapping、warn 移到 computeIfAbsent 返回后（按「本次是否新建+超阈」条件触发）。
13. **置信度**：高。
14. **复核状态**： 已保留（P3——mapping 函数内 warnIfOverCap→LOG.warn 确证（:178-182/:199-203 lambda 首语句 + warnIfOverCap :209-215）；AtomicBoolean CAS 一次性 + 超阈才触发，低频一次性定位准确）；现状：新发现

### [nop-wf-nop-task-C2-009]
1. **发现ID**：nop-wf-nop-task-C2-009
2. **严重度**：P3（当前无并发调用点；防御性候选）
3. **维度/类别**：C2 锁正确性——非同步惰性初始化
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStateBean.java:146-155`（`getTaskVars` 惰性 new LinkedHashMap + `setTaskVar` put）
5. **标题**：taskVars 惰性初始化无同步，并发首调可各建 Map 互覆（静默丢键）；LinkedHashMap 本身亦非线程安全
6. **问题描述**：`getTaskVars()` 为 `if (taskVars == null) taskVars = new LinkedHashMap<>()` 的非同步惰性初始化。全仓 grep 确认主代码当前无任何 `setTaskVar`/`getVar` 生产调用点（仅接口与实现），该状态容器目前是预留面；一旦 step body（XPL 可经 `$taskRt` 触达）在 parallel/fork executor 分支中并发写 taskVars，除初始化竞态外 LinkedHashMap 并发 put 还有结构损坏风险。
7. **证据片段**（TaskStateBean.java:146-155）：
```java
@Override
public Map<String, Object> getTaskVars() {
    if (taskVars == null)
        taskVars = new LinkedHashMap<>();     // ← 非同步惰性初始化
    return taskVars;
}
@Override
public void setTaskVar(String name, Object value) {
    getTaskVars().put(name, value);           // ← LinkedHashMap 非线程安全
}
```
8. **触发时序**：未具体化（当前无生产写入者）。
9. **影响面**：预留 API 面；对照 `TaskRuntimeImpl` 构造器 :69 对 scope 用 `CollectionHelper.newConcurrentMap` 的既有做法，此处未对齐。
10. **声明边界对照**：无声明。
11. **F 族对照**：F3 候选（新增并发写入者即触发）。
12. **修复建议**：改 `new ConcurrentHashMap<>()` 直接初始化（对齐 :69 惯例），删除惰性分支。
13. **置信度**：高。
14. **复核状态**： 已保留（P3——:146-150 无同步惰性初始化 + :153-155 LinkedHashMap put 确证；零生产调用方与 C1-round3 独立 grep（setTaskVar/getTaskVars 零生产调用）互证；对照 TaskRuntimeImpl:69 CollectionHelper.newConcurrentMap 既有惯例的缺口成立）；现状：新发现

### [nop-wf-nop-task-C2-010]
1. **发现ID**：nop-wf-nop-task-C2-010
2. **严重度**：P3（F5 对照项——等待点无超时，liveness 依赖外层配置，挂死可检测性弱）
3. **维度/类别**：C2/F5——无限期阻塞等待点登记
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/step/SyncTaskStepWrapper.java:17`（无条件 `execute(stepRt).sync()`）；根等待点 `nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java:185-189`（`sync()` → `FutureHelper.syncGet` → `IContext.syncGet`，无 timeout 参数）；同类有条件调用点：`LoopTaskStep.java:153`、`LoopNTaskStep.java:191`、`TaskStepHelper.java:277/292`、`ExecutorTaskStepWrapper.java:38`（前三者有 isDone 前置守卫）、`nop-task/nop-task-ext/src/main/java/io/nop/task/ext/dao/TransactionalTaskStepWrapper.java:30`、`nop-task/nop-task-ext/src/main/java/io/nop/task/ext/orm/OrmSessionTaskStepWrapper.java:26/30`
5. **标题**：sync 包装器的阻塞等待无超时，异步步骤的返回 promise 永不完成时驱动线程无限期挂死，且无中断/挂起可观测手段
6. **问题描述**：`SyncTaskStepWrapper` 由 `TaskStepEnhancer:160` 按配置接入，语义即「同步等待异步步骤」。引擎自身的 liveness 保障（plan 349 取消/超时终结不变量）只在步骤配置了 `<timeout>`/`executor` 取消兜底时成立；未配置且 body 返回的 future 永不完成时（如自定义 step 吞掉回调），sync 点永久阻塞且：无超时参数可调、是否响应中断取决于平台 `IContext.syncGet` 实现（本单元外）、无挂死指标。对照同文件 `syncIfDone`（:165-171）与 Loop/Retry 路径的 `isDone()` 前置守卫——引擎作者在部分调用点有意识地避免了无守卫阻塞，SyncTaskStepWrapper 与两个 ext wrapper 是剩余的无守卫面。
7. **证据片段**（SyncTaskStepWrapper.java:8-18；TaskStepReturn.java:185-189）：
```java
public class SyncTaskStepWrapper extends DelegateTaskStep {
    ...
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        return getTaskStep().execute(stepRt).sync();   // ← 无 isDone 守卫、无超时
    }
}
...
public TaskStepReturn sync() {
    if (future == null)
        return this;
    return FutureHelper.syncGet(future);               // → IContext.syncGet，无 timeout
}
```
8. **触发时序**：非竞态发现（liveness 登记项）；触发条件 = 用户配置 `<sync>` 包装 + 包装体返回不终结的 future 且未配 `<timeout>`。
9. **影响面**：使用 sync 包装的任务驱动线程（GraphQL/调度线程池）；单点挂死随线程池耗尽放大为全局不可服务。
10. **声明边界对照**：无「sync 必须搭配 timeout」的配置期校验（对照 owner doc :158 已有的 timeout 负值构建期硬失败惯例，此处缺对称守卫）。
11. **F 族对照**：F5（等待点超时核查的唯一不达标项；WfTaskScanner 对 `invokeActionAsync` 的 `FutureHelper.syncGet`（WfTaskScanner.java:79）同型，因 wf 动作引擎链路实际总是完成，暂记同族旁证不单独立案）。
12. **修复建议**：TaskStepEnhancer 在装配 SyncTaskStepWrapper 且步骤无 timeout 装饰时构建期告警或硬失败；或 `sync()` 增加 timeout 重载并在 wrapper 接入点强制配置。
13. **置信度**：高（无超时是事实；后果链依赖用户配置组合）。
14. **复核状态**： 已保留（P3——SyncTaskStepWrapper:17 无守卫无超时、TaskStepReturn.sync()→FutureHelper.syncGet 链确证；全部 7 处调用点行号逐一核实精确（LoopTaskStep:153 / LoopNTaskStep:191 / TaskStepHelper:277,292 / ExecutorTaskStepWrapper:38 / TransactionalTaskStepWrapper:30 / OrmSessionTaskStepWrapper:26,30）。勘误：sync() 实际位于 TaskStepReturn.java:208-213，初审位置标注 :185-189 有行号偏移（该区间实为 get()），证据片段内容本身无误，不影响结论）；现状：新发现

---

## 锁序图（文本）

**节点（模块内显式锁）**：
- `L1 = taskState monitor`（TaskStateBean 实例自身；持有者：TaskImpl 四终态 driver :186/202/237/252、TaskRuntimeImpl.cancel :97、TaskStateBean.newRunId :91）
- `L2 = DaoTaskStateStore.ROW_LOCKS[i]`（static final 64 槽条带锁；持有者：saveStepState :357）
- `L3 = CHM bin 锁`（TaskRuntimeImpl.attrs / globalRateLimiters / globalSemaphores 内部锁；持有者：computeIfAbsent/compute 调用点）
- `P = 平台内部锁`（ResourceComponentManager 模型缓存、OrmTemplate 会话、GlobalMeterRegistry——平台设施，仅列边不计环）

**边（A → B = 持 A 时获取 B）**：
1. `L1 → P(DB写+模型加载)`：TaskImpl drivers / TaskRuntimeImpl.cancel 持 L1 调 saveTaskState（taskDao 读/写、collectSecretProps→模型缓存、JsonTool）。（C2-006②③）
2. `L2 → P(DB读写+模型加载)`：saveStepState 持 L2 做 findStepEntity / collectSecretProps / save-update。（C2-006①）
3. `L2 → (L1)*`：saveStepState 持 L2 调 `state.beforeSave(taskRt)` —— beforeSave 是用户可覆写 hook（默认空实现），hook 内理论可达 L1（如经 taskRt.newRunId()）。标 \* 为经扩展点的潜在边。
4. `L1 → L3`、`L2 → L3`：持锁路径上存在 attrs/registry 的 CHM 操作（如 saveTaskState 链路内的 scope 写）→ CHM 内部 bin 锁。CHM bin 锁内不再获取任何模块锁（mapping 函数仅 new + LOG.warn）。
5. **不存在 `L1 → L2` 边**：task 级保存（saveTaskState）不取条带锁；step 级保存（saveStepState）不持 taskState 监视器。两把显式锁之间无直接嵌套。

**环检测**：显式锁子图（L1, L2, L3）的边集为 {L1→L3, L2→L3, (L2→L1)\*}——\* 边经用户 hook 才成立，且即使成立也不构成环（需要 L1→L2 反向边，而该边不存在）。**结论：模块内显式锁无可达死锁环**；唯一锁类死锁形态是 C2-005 的非锁原语自旋（非重入信号量嵌套获取），以及平台锁 P 子图内的序（本单元外）。锁内调用外部代码点两处已登记：`state.beforeSave/afterLoad` hook 在 L2 内调用（C2-006 证据链）、`LOG.warn` 在 L3 bin 锁内调用（C2-008）。

---

## 覆盖度与残留边界

**覆盖度**：
- 锁载体：基线 C2 节（nop-wf 2 处 computeIfAbsent / 0 锁载体；nop-task 7 锁载体 + 3 computeIfAbsent）与独立 grep（synchronized/ReentrantLock/ReadWriteLock/Collections.synchronized/原子类/CHM/volatile/ThreadLocal/CountDownLatch/Semaphore）双向核对一致，无遗漏；nop-task-queue（仅 1 个接口）、nop-task-ext/service/web/meta/api/codegen、nop-wf 除 core 外全部子模块经独立 grep 确认零锁载体。
- 逐字段核对迭代点：`stepCleanups`（C2-004）、`promises`（核实排除）、`stepFutures`/`errorConsumers`（构建后只读，排除）、`globalRateLimiters/globalSemaphores.forEach`（TaskFlowManagerImpl:220/229/237/242 stats/reset——CHM 弱一致遍历，只读快照语义，排除）、`WorkflowImpl.steps`（C2-007 一并覆盖）、`TaskExceptionRegistry`（构造后只读，排除）。
- computeIfAbsent 5 处全核（见检查范围说明）；锁对象替换核查：ROW_LOCKS static final、taskState 监视器对象无二次 setTaskState 路径，排除；DCL 核查：TaskFlowModel 全同步方法无 DCL、WorkflowImpl.getGlobalVars 惰性初始化在实例封闭内，排除。
- F5 等待点：timeout/throttle/rate-limit/delay/executor-cancel 全部达标；不达标面收敛为 `sync()` 无超时族（C2-010）；`WorkflowEngineImpl:893 Thread.sleep` 有界+中断正确，排除。
- 盲审纪律：未读取 ai-dev/audits（除本输出文件写入）、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；全部结论来自 live code + owner doc（docs-for-ai/03-modules/nop-wf.md、nop-task.md）+ 基线 grep。

**残留边界（本轮未立案、移交后续轮次或已声明）**：
1. 跨进程 fork+DB-resume 完整性与跨进程并发写同一 stepPath 行——owner doc :69 已声明为暂缓设计主题，本轮按「已声明边界」不立案（含 DB 乐观锁兜底路径）。
2. 平台设施内部锁语义（`IContext.syncGet` 的中断行为、`DynamicOrmEntity.computeIfAbsent` 的底层 Map 类型、ResourceComponentManager 缓存锁、DefaultSemaphore/DefaultRateLimiter 实现、Cancellable 监听器链）——按误报校准「平台设施自身实现」不审实现，仅在 C2-010 记录使用方后果。
3. nop-wf 的 DB 级并发（同一步骤双操作员并发 agree、scanner 与用户操作竞态）——无模块内同步原语可审，属 C6/C9/状态机维度；本轮仅确认 scanner 对状态竞态有 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` 的 skip 容错（WfTaskScanner.java:88-93）。
4. `ITaskState` 自定义子类（afterLoad/beforeSave hook）在 L2 内的任意行为——潜在 L2→任意 边（锁序图边 3），默认实现为空，用户扩展面风险留待 hook 契约文档化时收敛。
5. 维度集外线索（C6/C9 形态 P0/P1）：本轮未发现达到登记标准的线索——ThrottleTaskStepWrapper 的 permit 释放经 `whenComplete` 对 sync/async/异常三条路径均闭合（TaskStepReturn.whenComplete:273-280 对非异步返回立即执行回调），不构成 C6 资源泄漏线索。

---

## 维度复核结论（独立复核子代理）

- **复核子代理**：C2 锁与同步原语维度独立复核（与初审者不同角色，逐条验证、保留/降级/驳回，不补充新发现）。
- **复核日期**：2026-10-15。**代码基线**：branch `concurrency-audit-remediation` @ be0cbbb2a3。
- **复核方法**：完整读取 C2-locks-sync.md（C2-001~010）与 C2-round2.md（C2-011~013）；对 13 条发现逐条重读 live code 验证文件/行号、证据真实性、锁序/死锁/竞态推演与判级口径；按纪律定向核对 C1 侧同代码点裁定（C1-shared-mutable-state.md 复核结论节、C1-round2.md §4 排除记录、C1-004）与 cra-index.md 历史台账；独立 grep 验证关键可达性事实（svcCtx.cancel 触发点、addStepCleanup/runStepCleanups 调用方）。

### 逐条判定表（覆盖全部 13 条）

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---------|-------|------|-----------|---------|
| C2-001 | P1 | **保留** | 无锁 SUSPENDED 转移（:159-160）× 监视器内判定（TaskRuntimeImpl:97-105）交错成立，双向后果推演成立且 kill 静默丢失可具体化；与 C1-001 同一代码点（C1 复核已保留 P1），判定自洽 | 新发现（cross-ref C1-001，合并裁定阶段归并） |
| C2-002 | P2 | **降级至 P3** | 非 volatile + 注册先于写为代码事实，但独立 grep 证实 svcCtx.cancel 单元内零外部触发、唯一实存跨线程 cancel 消费方 HB 均后于 setTaskState——后果不可具体化；cancel override 体不受 once 门控，重复 kill 可恢复；与 C1-round2 §4 同面核查结论（不立案）自洽 | 新发现（cross-ref C1-round2 §4） |
| C2-003 | P2 | **保留** | 守卫非原子为代码事实（全文零锁），无可具体化双驱动的穷举结论经 timeout winner 单链 + driveStepFailure 双出口复核维持；P2 理论档与 C1-004（已保留 P2）一致 | 新发现（cross-ref C1-004，合并裁定阶段归并） |
| C2-004 | P2 | **降级至 P3** | 不对称为事实，但独立 grep 证实 addStepCleanup 零生产调用方、runStepCleanups 仅 TryTaskStepWrapper:57/80 在 promise 链上调用——live code 无并发窗，风险面仅剩 XPL/自定义步骤扩展点；与同暴露面的 C2-009（P3）校准一致 | 新发现 |
| C2-005 | P2 | **保留** | permit 横跨 body + global 注册表 taskName:key 共享 + 嵌套同 key 获取机理逐帧核实成立；标题「永久死锁」措辞偏重（maxWait 为有限 int，实为确定性超时/长阻塞），C2-011 已在同族内修正框架，不影响判级 | 新发现 |
| C2-006 | P2 | **降级至 P3** | 锁内 I/O 属实，但条带锁结构与「低频可接受」为类 javadoc :332-337 + owner doc :69 已声明边界（初审已自认不计缺陷），监视器内 saveTaskState 是 plan 364 原子性裁定组成部分；未声明残余仅 collectSecretProps 缓存失效长尾，属优化项；与同族 C2-012（P3）校准一致 | 新发现 |
| C2-007 | P3 | **保留** | HashMap/ArrayDeque/executing 与 check-then-act 逐行核实，WorkflowManagerImpl:73-87 每调用新建实例的封闭现状确证；纯隐式约束防御性候选定位准确 | 新发现 |
| C2-008 | P3 | **保留** | mapping 内 LOG.warn（:178-182/:199-203 + :209-215）确证；AtomicBoolean 一次性 + 超阈才触发，低频定位准确 | 新发现 |
| C2-009 | P3 | **保留** | :146-155 无同步惰性初始化确证；零生产调用方与 C1-round3 独立 grep 互证；对照 TaskRuntimeImpl:69 并发容器惯例缺口成立 | 新发现 |
| C2-010 | P3 | **保留** | 无超时 sync 链与全部 7 处调用点行号逐一核实精确；勘误：sync() 实际位于 TaskStepReturn.java:208-213（初审标注 :185-189 有行号偏移，该区间实为 get()），证据片段内容无误 | 新发现 |
| C2-011 | P2 | **保留** | 两条默认可达嵌套路径逐帧核实成立（CallTaskStep:97 内联 × taskName:key 注册表；CallStepTaskStep:83 同 stepRt × 默认 key=stepPath × lib 同 wrap 管线）；细部修正：main step 相对路径为 `@main`（newMainStepState），初审「path=S」为简化表述，递归实例路径重启 → 同 key 的结论不变 | 新发现 |
| C2-012 | P3 | **保留** | wrap 栈序（Retry :115 内 ← Throttle :137-142 外）+ schedule 退避期间 promise 未完成（TaskStepHelper:268-284）确证；吞吐退化非正确性，P3 恰当 | 新发现 |
| C2-013 | P3 | **保留** | 双重校验缺口确证（Enhancer :130-131/:147-149 有守卫 vs :137-142 无；TaskFlowManagerImpl :183-188 有告警 vs :197-207 无；task.xdef:115 仅 `!int`）；owner doc :158-159 自证同口径惯例存在而 throttle 遗漏；semaphore 半边为 plan 349 Phase 5 修复的对称遗漏面 | 新发现 |

**判定计数**：保留 10（C2-001/003/005/007/008/009/010/011/012/013），降级 3（C2-002：P2→P3；C2-004：P2→P3；C2-006：P2→P3），驳回 0。

**与 C1 侧自洽性说明**：C2-001 与 C1-001 为同一代码点的两个维度视角（C1：共享可变状态/状态机；C2：锁正确性/check-then-act），三态判定一致（均保留 P1），合并裁定阶段归并为单一修复项；C1-round3 已载明该修复须与 C1-009（resume 无 ACTIVE 重置）作为同一原子修复设计。C2-003 与 C1-004 为同一失效面（TaskStepStateBean 守卫族）的双维度登记，判定一致（均保留 P2）。

**不确定项（显式列出）**：
1. C2-006 的降级是本轮最接近两可的裁定：若合并裁定阶段认定「持久化模式高负载」为该模块一等场景，可基于「64/RTT 全局吞吐上限 + kill 阻塞」复议回 P2；降级理由的核心依据是锁结构与代价已属声明/设计边界。
2. C2-013 对 `maxConcurrency<=0` 后果的推定依赖 DefaultSemaphore 的信号量定义性语义（0 permit 恒不可获取）；该类为平台设施按口径不审实现，未做实现级验证。
3. C2-011 路径 A 的注册表 key 字面量与初审表述有差（实际为 `taskName:@main` 或 `taskName:@main/S` 形态，非 `taskName:S`）——已核实不影响同 key 碰撞结论，但修复实现若按初审字面量做重入检测需以实际 key 形成为准。

---

## 最终保留项

（含保留 + 降级项，共 13 条；无驳回项。文件路径均为仓库相对路径。）

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|------|------------|---------|-----------|------|
| C2-001 | P1 | nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:159-160（对照 TaskRuntimeImpl.java:97-105） | SUSPENDED 转移游离于 taskState 监视器外，与 cancel 的 KILLED 驱动构成跨监视器 check-then-act——kill 挂起中的任务可静默丢失（cross-ref C1-001 同点归并） | 新发现 |
| C2-002 | P3（降级） | nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java:52,79-81,132-134 | taskState/metrics 非 volatile 且 onCancel 注册先于 setTaskState 写——latent 可见性缺口，live code 无可具体化触发 | 新发现 |
| C2-003 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java:46-63,189-195 | step 级 first-terminal-wins 守卫为无锁 check-then-act，与 task 级监视器收敛不对称（cross-ref C1-004 同面归并） | 新发现 |
| C2-004 | P3（降级） | nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskStepRuntimeImpl.java:181-195 | addStepCleanup 加锁而 runStepCleanups 无锁的纪律不一致；写端零生产调用方，风险面仅剩扩展点 | 新发现 |
| C2-005 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:41-56（注册表 TaskFlowManagerImpl.java:197-207） | throttle 非重入信号量嵌套同 key 获取按深度耗 permit，显式共享 key 配置下确定性超时/长阻塞 | 新发现 |
| C2-006 | P3（降级） | nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:357-373（及 TaskImpl 终态 driver 持监视器 saveTaskState 链） | 条带锁/taskState 监视器临界区内含 DB 往返与模型组件加载，锁持有时间由最慢 I/O 决定（主体属已声明设计边界，残余为优化项） | 新发现 |
| C2-007 | P3 | nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowImpl.java:41,46,51,80-115 | 命令队列/executing 标志/step 缓存完全非线程安全，正确性依赖未声明的「每请求新建实例」封闭约束 | 新发现 |
| C2-008 | P3 | nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:178-182,199-203,209-215 | CHM computeIfAbsent mapping 内 LOG.warn，同 bin 注册被日志 I/O 拖带（低频一次性） | 新发现 |
| C2-009 | P3 | nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStateBean.java:146-155 | taskVars 无同步惰性初始化 + LinkedHashMap 非线程安全，预留 API 面未对齐并发容器惯例 | 新发现 |
| C2-010 | P3 | nop-task/nop-task-core/src/main/java/io/nop/task/step/SyncTaskStepWrapper.java:17（根等待点 TaskStepReturn.java:208-213） | sync 包装器阻塞等待无超时无守卫，永不完成的 future 使驱动线程无限期挂死（勘误：初审 sync() 行号 :185-189 应为 :208-213） | 新发现 |
| C2-011 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/step/CallTaskStep.java:97、CallStepTaskStep.java:83（× ThrottleTaskStepWrapper.java:58、TaskFlowManagerImpl.java:199,204-206） | 零显式共享 key 即可达的 throttle 嵌套获取：global×自递归 call-task 与 call-step 同 stepRt 两条默认配置确定性失败路径（C2-005 失效面修正） | 新发现 |
| C2-012 | P3 | nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:114-142,159-160（× TaskStepHelper.java:268-284） | wrap 栈序使 throttle permit 横跨整个 retry 链（含退避与 timeout 预算），global 闸门额度被单实例重试蚕食 | 新发现 |
| C2-013 | P3 | nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:137-142（对照 :130-135,:147-153）、TaskFlowManagerImpl.java:197-207（对照 :183-188）、nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:115 | throttle 闸门双重校验缺口：maxConcurrency<=0 无构建期硬失败（运行期确定性超时）+ 同 key maxPermits 首配置静默固化无告警（rate limiter 同形态已修） | 新发现 |

**最终严重程度分布**：P1 × 1（C2-001），P2 × 3（C2-003/005/011），P3 × 9（C2-002/004/006/007/008/009/010/012/013）。

**需子项复核的高风险项**：
1. **C2-001**（连带 C1-001/C1-009 同点修复）：修复须将 SUSPENDED 转移纳入 `synchronized(taskState)` + 终态前置检查，并与 C1-009 的 resume ACTIVE 重置作为同一原子修复设计，回归四个既有终态 driver 语义；若修复引入新同步结构，其锁序须按本维度锁序图复核。
2. **C2-005 + C2-011 + C2-013（throttle 闸门族）**：三者修复耦合（执行期重入检测 / 参数校验 / 固化告警须一次性设计），且修复不得无意改变 C2-012 登记的栈序语义（permit 覆盖 retry 链）——建议作为单一修复项整体裁决。
