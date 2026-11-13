# C3 异步链路正确性 — nop-wf + nop-task 第 2 轮深挖（盲审）

- 审计单元：`nop-wf`（10 jar）+ `nop-task`（10 jar），与第 1 轮同口径（379 files / 36,107 lines）
- 仓库：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- 本轮定位：第 1 轮（C3-001~010）之后的定向深挖——C3-001 失效面扩展（wf 引擎同族"快照读→判定→写"站点）、C3-002 失效面扩展（sync 叠加矩阵）、取消传播链完整性、orTimeout/timeout 幽灵站点全家扫描、IContext 其余异步边界核对。
- 盲审纪律：未读取 `ai-dev/audits|plans|bugs|lessons/` 下除本维度第 1 轮文件（`C3-async-chains.md`，指令列出的必要输入）外的任何文件；未读取其他维度审计文件；仅审当前 live code。
- 审计日期：2026-10-15
- 发现编号接续第 1 轮：C3-011 起。

---

## 1. 本轮发现

### [nop-wf-nop-task-C3-011]
- **字段1 发现ID**：nop-wf-nop-task-C3-011
- **字段2 严重度**：P1
- **字段3 维度/族**：C3-时序假设（wf 流程级自动终结判定的"快照读→判定→写"竞态）；F2 半个成功（两个"步骤成功退出"合成为"流程永不终结"整体失败）。C3-001 同族新站点。
- **字段4 位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1578-1595`（checkEnd 判定）；`:1587`（isAllStepsHistory）；`:1315-1317`（每次 action 后 delayExecute 调度点）；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/store/AbstractWorkflowStore.java:178-180`；`nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:305-307`（步骤集=当前事务内 ORM 集合）
- **字段5 证据片段**：
```java
// WorkflowEngineImpl.checkEnd —— 全 history 才自动终结，读的是本事务快照
if (!wf.isEnded() && wf.isStarted()) {
    boolean bEnd = wfRt.willEnd();
    if (bEnd) { killSteps(wfRt); }
    else if (wf.getStore().isAllStepsHistory(wfRecord)) {   // :1587 快照判定
        bEnd = true;
    }
    if (bEnd) { this.doEndWorkflow(NopWfCoreConstants.WF_STATUS_COMPLETED, wfRt); }
}
// AbstractWorkflowStore.isAllStepsHistory —— getSteps=当前事务 ORM 集合
return getSteps(wfRecord).stream().allMatch(IWorkflowStepRecord::isHistory);
```
- **字段6 触发时序**：任意"以 auto-end 收口"的流程（末段步骤无 TO_END 迁移，靠全 history 自动终结；TO_EMPTY/无迁移均属此类）末段有两个并行活跃步骤 A、B。用户/系统几乎同时对 A、B 提交动作（`@BizMutation` 各自独立事务，WorkflowServiceImpl:132-134）。T1 退出 A（A 行 COMPLETED，`:1292-1306`）→ `delayExecute(checkEnd)`：B 在 T1 事务快照中仍 ACTIVATED（T2 未提交）→ 不终结。T2 对称地看到 A 未提交 → 不终结。两侧写**不同的 step 行**（`nop_wf_step_instance` 各自的 version 列），乐观锁无冲突，双双提交。终态：所有步骤 history、`nop_wf_instance.status` 永远停在 ACTIVATED。
- **字段7 影响**：流程实例永不开闭、业务实体审批状态卡死（bizEntityState 不再推进）。无自动补救出口：`runAutoTransitionsWithLimit` 只遍历 activated 步骤（DefaultWorkflowExecutor:51-59，此时为空）；`WfTaskScanner` 的 due/remind 扫描只查 ACTIVATED 步骤（DaoWorkflowStore:181-194）；对 history 步骤仅 `forHistory`/`forWithdraw` action 可通过 `isForStatus`（WorkflowEngineImpl:1792-1798），未配置时任何 mutation 都无法再触发 checkEnd。人工 `kill` 是唯一闭环。与 C3-001 的区别：无需 exec-group 配置，任何末段并发完成的流程即触发，触发面更宽；滞留形态不同（实例不终结 vs 组下游未创建），不构成重复立案。
- **字段8 根因**：与 C3-001 同根——每请求新建 WorkflowImpl（WorkflowManagerImpl:81-88）+ 事务内快照读 + "先判定后写"无实例级互斥；站点从组完成判定换成了流程终结判定。`nop_wf_instance` 的 version 列只能保护同写一行的冲突，跨行判定无锁可依。
- **字段9 维度族对照**：F2（两个半成功合成整体失败）；非 F1（各事务快照自洽，无撕裂）、非 F9（无异常被吞）。
- **字段10 修复方向**：同 C3-001 修复一并处理——按 wfInstanceId 条带锁串行化 `DefaultWorkflowExecutor.execute`（对齐 `DaoTaskStateStore.rowLock` 模式），或 checkEnd 判定改为写锁内重读兄弟步骤最新已提交状态；跨进程需 wf_instance 行 `SELECT ... FOR UPDATE`。亦可加自愈兜底：检测到"全 history 但未终结"时以新事务重读确认后再终结（与并发 kill 的双写由乐观锁显式裁决）。
- **字段11 误报校准**：非无状态单例误报（竞态对象是 per-request 快照与共享 DB 行）；非低频路径——并行分支末段两步几乎同时完成是审批流常态；判 P1 与 C3-001 同标（永久滞留需人工介入）。
- **字段12 置信度**：高（判定链、`@BizMutation` 事务边界、ORM 集合读路径、无 rescue 出口均逐行核实；未做并发复现）。
- **字段13 备注**：C3-001 字段7 提及的"checkEnd 看不到未迁移组出口"是 exec-group 场景的从属后果；本条立的是**无 exec-group 参与**的独立流程终结竞态。第 2 节第 1 项的 vote-group 修正与 C3-001/011/012 同属一个修复面。
- **复核状态**: 已保留（P1。从严复核属实：checkEnd（:1578-1595）经 isAllStepsHistory（AbstractWorkflowStore:178-180）读 DaoWorkflowStore.getSteps=NopWfInstance.getSteps() 事务内 ORM 集合，竞态双侧各写不同步骤行、双双不写 wf 行故乐观锁无冲突可依；无 rescue 出口逐项核实——findDueActivatedSteps/findRemindActivatedSteps 过滤 status=ACTIVATED（DaoWorkflowStore:181-194 区）、runAutoTransitions 仅遍历 getActivatedSteps（WorkflowEngineImpl:815）、doEndWorkflow 生产调用方恰 checkEnd+kill；TO_END 路径经 killSteps 全行覆盖的天然保护不对称（round3 §2.2-4）佐证本条暴露面唯一且修复后无残留。现状：新发现——历史审计（check2/nop-wf.md、2026-09/06-nop-auth-wf.md 的 G6-22 系）均为此站点的语义/终态问题而非本并发竞态，无重复立项）

### [nop-wf-nop-task-C3-012]
- **字段1 发现ID**：nop-wf-nop-task-C3-012
- **字段2 严重度**：P1
- **字段3 维度/族**：C3-时序假设（and-join 激活判定的快照竞态）+ join 实例重复创建（double-subscribe 族的空间维变体）；F2。C3-001 同族新站点。
- **字段4 位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:491-520`（checkWaitingJoinStep）；`:342-364`（getNextJoinStepRecord 复用判定）+`:357-358`/`:419-421`（激活检查调度点）；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/store/AbstractWorkflowStore.java:108-133`（getJoinWaitStepRecords 过滤 history）与`:183-197`（getNextJoinStepRecord）
- **字段5 证据片段**：
```java
// newStepForActor —— join 复用判定读当前事务快照
IWorkflowStepRecord stepRecord = wf.getStore().getNextJoinStepRecord(
        currentStep.getRecord(), joinGroup, stepModel.getName(), actor);
if (stepRecord != null) { ... return step; }          // :343-363 复用既有 J
// —— 快照里看不到并发事务未提交的 J → 走 :370 newStepRecord 再建一个 J
// checkWaitingJoinStep —— 激活判定同样读快照
for (IWorkflowStepImplementor waitStep : step.getJoinWaitSteps(wfRt)) {
    if (!waitStep.isHistory()) { allWaitFinished = false; break; }   // :504-509
}
```
- **字段6 触发时序**：split S1→A、S1→B；A、B 均迁移至 join 步骤 J（`joinType="and"`）。T1 退出 A → 迁移创建 J1（join 步骤初始 WAITING，`:402-403`）→ `checkWaitingJoinStep(J1)`：快照中非 history 上游 = {B（早已提交，ACTIVATED）} → 不激活。T2 **并发**退出 B → `getNextJoinStepRecord` 在 T2 快照中看不到 T1 未提交的 J1 → **创建第二个实例 J2** → `checkWaitingJoinStep(J2)`：非 history 上游 = {A（T1 未提交，仍 ACTIVATED）} → 不激活。双双提交：A、B=COMPLETED（history），J1、J2 永久 WAITING。顺序到达（T1 提交后 T2 才开始）则正确复用并激活——仅并发窗口触发。
- **字段7 影响**：汇聚点永不激活 → 流程永久滞留（无自动 rescue：`checkWaitingJoinStep` 仅在新上游接入（:358/:421）或 `turnSignalOn`（:752-754→:766-767）时重跑；WAITING 不在 due/remind 扫描范围 DaoWorkflowStore:181-194）；叠加 join 实例重复，后续若人工 `transitTo(J)` 补救会再创建实例（join 步骤 transitTo 后仍 WAITING→此时上游全 history 才激活），待办/迁移口径混乱。人工补救依赖运维精确理解内部状态，非声明契约。
- **字段8 根因**：同 C3-001 族——join 复用判定与激活判定都建立在事务内快照上且无实例级互斥；C3-001 立的是 exec-group（同名步骤多实例组），本条立的是 joinType=and（DAG 汇聚点），机制不同、代码站点不同。
- **字段9 维度族对照**：F2；double-subscribe 邻接（同一逻辑 join 步骤被物化为两个实例）。
- **字段10 修复方向**：同 C3-001/011 的 wfId 条带锁一并解决；或 join 实例创建改幂等（DB 唯一约束 on (wfId, stepName, joinGroup, 活跃态部分索引) + 冲突时重读复用），激活判定在写锁内重读上游。
- **字段11 误报校准**：需要 `joinType="and"` 配置，但并行汇合是工作流主流形态，判 P1 与 C3-001（and-group）同标；非"有界声明结构"——wf.xdef 未声明 join 的并发到达语义。
- **字段12 置信度**：高（复用、创建、激活三条路径逐行核实；`getJoinWaitStepRecords` 先滤 history 再判 `isHistory` 的语义已复核——全 history 时集合为空 → allWaitFinished=true，单事务语义正确，缺陷仅在跨事务快照盲区）。
- **字段13 备注**：signal 激活路径 `checkWaitingStep`（:764-793）对非 join 的 WAITING 步骤同样读快照，但其重跑入口（turnSignalOn）是显式人工/系统事件，天然存在"下一次信号重试"，竞态窗口自愈性强，不单独立案。
- **复核状态**: 已保留（P1，采纳 round3 or-join 影响面扩展。从严复核属实：复用判定（:343 getNextJoinStepRecord，AbstractWorkflowStore:183-197 先滤 history 再按 stepName+joinGroup 匹配）读事务快照、并发双建无阻——nop_wf_step_instance 仅 stepId 主键，无 (wfId,stepName,joinGroup) 唯一约束（orm 全文核实）；激活判定（:491-520 getJoinWaitSteps→isHistory）同读快照；and 初始 WAITING（:402-403）滞留面与 or-join 复用竞态（复用门控 :337 为 getJoinType()!=null 两类型共用；xdef:261-263 契约原文逐字核实；or-join 新实例直接 ACTIVATED 致下游双跑）均成立，扩展结论采纳、同一站点同一修复。现状：新发现）

### [nop-wf-nop-task-C3-013]
- **字段1 发现ID**：nop-wf-nop-task-C3-013
- **字段2 严重度**：P2
- **字段3 维度/族**：C3-取消/超时语义之资源占有 + 时序假设（业务步骤体在 JVM 全局定时器线程上执行，共享 timer 池饥饿 → 全进程超时/延迟级联失准）；并修正第 1 轮 C3-007 备注对 task 侧"非阻塞退避"的定性。
- **字段4 位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/step/SequentialTaskStep.java:77-96`（delay 完成后 thenApply 续延递归 execute）；`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:262-284`（retry 延迟恢复 `schedule(action, delay)`，:270）；`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:77-79`（→GlobalExecutors.globalTimer）；`nop-kernel/nop-commons/.../executor/GlobalExecutors.java:38-49`；`nop-kernel/nop-commons/.../CommonConfigs.java:22-24`（global-timer.maxPoolSize 缺省 **5**）
- **字段5 证据片段**：
```java
// SequentialTaskStep —— 异步步骤完成后，续延（含下一步全部业务体）在完成线程内联执行
return stepResult.thenApply(result -> {
    ...
    stepRt.setBodyStepIndex(getNextIndex(indexParam, result, stepRt));
    stepRt.saveState();
    return execute(stepRt);        // :94 在完成线程上递归执行后继步骤
});
// TaskStepHelper.retry —— 延迟后整段 body 直接调度到全局 timer 线程
stepRt.getTaskRuntime().getScheduledExecutor()
        .schedule(action, delay, TimeUnit.MILLISECONDS)   // :269-270 action=整个 step body
// GlobalExecutors —— JVM 全局共享，缺省 5 线程
config.setMaxPoolSize(CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE.get());  // 缺省 5
```
- **字段6 触发时序**：两条入口。(a) **delay 续延**：串行任务含 `<delay>` 步骤（DelayTaskStep 在全局 timer 上调度 no-op，完成后 future 在 timer 线程完成）→ Sequential/Loop 的 `thenApply/thenCompose` 续延在 **timer 线程**上递归 `execute` → delay 之后的全部同步步骤（输入求值、when 条件、业务体、state 落盘）都在 timer 线程执行，直至下一个异步边界。(b) **retry 恢复**：步骤配 `retry` 且 `retryDelay>0`，首次尝试失败 → 重试 body 经 `schedule(action, delay)` 在 timer 线程**整段执行**（注意 `TaskStepEnhancer.wrap:114-120` 装配序 executor 在 retry 外层，配置的 executor 只包住首次尝试，重试体不经业务线程池）。
- **字段7 影响**：N 个并发任务的长耗时/阻塞 body 即可占满 5 线程全局 timer 池 → 全进程所有 step-timeout 计时器（TaskStepHelper:199-204）、DelayTaskStep 完成、retry 调度排队延迟 → **无关任务**的超时阈值失效（TIMEOUT 终态晚到、任务超期挂活）、delay 步骤精度失效，跨任务级联。同时 timer 线程上的 body 无 ThreadLocal 上下文（C3-004 同族，已立案不重复）。
- **字段8 根因**：`FutureHelper.thenCompleteAsync` 的"完成线程内联回调"语义 + 延迟恢复直接 schedule 业务 Callable——延迟与续延之后没有把执行权交还工作线程池；全局 timer 池被设计为计时器专用（缺省 5 线程）却被复用为业务执行线程。
- **字段9 维度族对照**：非 F1/F2/F9；C3 资源占有族（与 C3-007 wf 侧 Thread.sleep 对偶：task 侧退避不占调用线程，但把代价转嫁给共享 timer 池——第 1 轮 C3-007 字段13 将 task 侧标为"非阻塞退避"优良对照，本条修正该认知：退避本身非阻塞，恢复执行阻塞在全局 timer 池上）。
- **字段10 修复方向**：delay 完成回调与 retry 恢复经工作线程池重新投递（global-worker 或步骤配置的 executor），timer 线程只做完成信号；或最低成本：owner doc [03-06] 强声明"delay 后继步骤与 retry body 在共享定时器线程执行、必须短小无阻塞"，并在 xdef 配置校验层给出告警。
- **字段11 误报校准**：判 P2：需 delay/延迟重试配置且 body 耗时不可忽略；纯内存短 body 危害有限。但一旦触发即跨任务全局影响，且 delay/retry 均为一等模型属性，非边界配置。
- **字段12 置信度**：高（续延装配、调度点、池配置逐行核实；DefaultScheduledExecutor 为 ScheduledThreadPoolExecutor 包装，完成线程=timer 线程确定）。
- **字段13 备注**：第 1 轮 C3-004 字段7 已提及"retry 延迟路径续延在 globalTimer 线程"，但仅限上下文传播视角；本条立的是**共享计时池被业务体占用**的资源/活性维度，属新增面。
- **复核状态**: 已保留（P2。thenApply 续延在完成线程递归 execute（SequentialTaskStep:82-96）、retry 延迟恢复整段 body 调度到 globalTimer（TaskStepHelper:268-270）、executor 包装在 retry 外层致重试体不经业务池（TaskStepEnhancer:114-120）、getScheduledExecutor→globalTimer（TaskFlowManagerImpl:76-81）、CommonConfigs:22-24 缺省 5，均属实。两点对账备注：①retry 半边与 C4-001/C4-008（均 P1）同池同站，delay 续延半边为本条相对 C4 的独有面，建议 7.1 合并裁定按 C4-001 族统一处置（存在 C3-P2/C4-P1 同机制判级分歧，归并时需统一口径）；②"缺省 5 线程"为配置缺省值，实际线程数=corePoolSize 缺省 4（ExecutorHelper 对 ScheduledThreadPoolExecutor 忽略 maxPoolSize，C4 证据），不影响结论。现状：新发现）

### [nop-wf-nop-task-C3-014]
- **字段1 发现ID**：nop-wf-nop-task-C3-014
- **字段2 严重度**：P3（理论风险为主，XPL 脚本路径可达）
- **字段3 维度/族**：C3-取消/超时语义（取消传播链断裂：step seam cancelToken 为 null，taskRt.cancel 无 step 侧消费者）
- **字段4 位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java:234`（`stepRt.setCancelToken(getSvcCtx())`——svcCtx=null 时透传 null）；`:85-106`（cancel 仅置自身 token + SUSPENDED 特判）；`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskStepRuntimeImpl.java:43`（volatile token）；`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:71-80`（checkNotCancelled 读 stepRt token）；`nop-task/nop-task-core/src/main/java/io/nop/task/step/ExecutorTaskStepWrapper.java:54-67`（token null 则取消兜底不注册）；`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskGenHelper.java:17,30`（XPL 脚本 `newTaskRuntime(task, ...)` 可传 null svcCtx）
- **字段5 证据片段**：
```java
// TaskRuntimeImpl —— 主 step runtime 的取消令牌直接取 svcCtx
TaskStepRuntimeImpl stepRt = new TaskStepRuntimeImpl(this, stateStore, getEvalScope());
stepRt.setCancelToken(getSvcCtx());        // :234 svcCtx==null → token=null
// cancel(reason)：super.cancel 只置 taskRt 自身 token，step 侧无人读它
public void cancel(String reason) {
    super.cancel(reason);
    if (svcCtx != null) svcCtx.cancel(reason);   // svcCtx==null 时无传播路径
    ... // 仅 SUSPENDED 直驱 KILLED，ACTIVE 任务无 DB 侧收敛
}
```
- **字段6 触发时序**：脚本/嵌入式用法 `taskFlowManager.newTaskRuntime(task, saveState, null)`（TaskGenHelper 生成的 XPL 可达）→ 执行中经 `newChildRuntime`/`ITask.asExecution` 挂到父 cancel token → 父作用域取消 → `taskRt.cancel` → 自身 token 置位，但 stepRt.cancelToken=null → `checkNotCancelled` 恒 false、`SleepTaskStep.waitUntil(stepRt::isCancelled)` 不提前醒、ExecutorTaskStepWrapper 取消兜底不注册 → 全部步骤照常跑完 → `state.succeed` → 任务落 COMPLETED 并 saveTaskState——取消被静默无视且以成功终态覆盖语义。
- **字段7 影响**：该形态下取消语义完全失效（ACTIVE 任务不可取消、以 COMPLETED 收场）。平台主路径不受影响：service 层传入真实 svcCtx（CallTaskStep:83/89 子任务继承父 svcCtx），取消经 svcCtx→stepRt token 全链可达。
- **字段8 根因**：step seam 的取消令牌唯一取自 svcCtx，未回退到 taskRt 自身（`TaskRuntimeImpl extends Cancellable`，自身即合法 ICancelToken）。
- **字段9 维度族对照**：非 F1/F2/F9；C3 取消传播完整性。与 round1 契约项 5（"ACTIVE 任务的取消……由 step driver 收敛"）的隐含前提（token 非空）在 svcCtx=null 时不成立。
- **字段10 修复方向**：`stepRt.setCancelToken(svcCtx != null ? svcCtx : this)`；或编程式 API 强制要求显式 cancel token（Guard 非空）。
- **字段11 误报校准**：P3——平台生产代码无 null svcCtx 调用方，仅 XPL 脚本助手可达（低频 + 需特定用户代码）；但时序可完整写出，不作为纯理论排除。
- **字段12 置信度**：中高（代码路径确定；实际触发依赖用户脚本写法）。
- **字段13 备注**：外部 kill API 缺失本身（nop-task-service 无任何可达运行中 runtime 的 kill/cancel 入口，无内存注册表）经核实为**已声明边界**（取消入口仅 svcCtx/step-timeout/parent-runtime 三条），不立案，见第 2 节第 5 项。
- **复核状态**: 已保留（P3。取消断链全链复核属实：主 stepRt token=getSvcCtx()（TaskRuntimeImpl:233-234 区）、cancel 仅 svcCtx 路径传播+SUSPENDED 特判、volatile token（TaskStepRuntimeImpl:43）、checkNotCancelled 读 stepRt token（TaskStepHelper:71-80）、ExecutorTaskStepWrapper:59 token null 则兜底不注册、newChildRuntime 传 getSvcCtx() 且经 this.appendOnCancel(taskRt::cancel)——父作用域取消→子 taskRt.cancel→子主 stepRt token 仍 null→步骤不感知，链路逐步验证成立。一处精确化：TaskGenHelper 生成的 biz action 实际传 svcCtx（biz 上下文非 null），null 路径需脚本/嵌入式显式传 null——与字段11"仅 XPL 脚本助手可达"的自我校准一致，P3 恰当。现状：新发现）

### [nop-wf-nop-task-C3-015]
- **字段1 发现ID**：nop-wf-nop-task-C3-015
- **字段2 严重度**：P3
- **字段3 维度/族**：C3-异常传播/时序假设（并发兄弟子流结束时父流程行乐观锁冲突回滚输家子流的整个结束事务）；F2 半个成功
- **字段4 位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1622-1633`（endSubFlow 在子流结束事务内通知父流程）；`:1127-1148`（notifySubFlowEnd 写父步骤并触发迁移）；`:1597-1620`（doEndWorkflow→saveWfRecord 写父 wf 行）；`nop-wf/nop-wf-dao/.../store/DaoWorkflowStore.java:130-132`；`nop-wf/model/nop-wf.orm.xml:181,254`（nop_wf_instance versionProp 乐观锁）
- **字段5 证据片段**：
```java
// endSubFlow —— 父流程通知与子流自身终态落盘同处一个事务
WfStepReference parentStepRef = getParentStepRef(wf);
if (parentStepRef != null) {
    wf.getCoordinator().endSubFlow(wf.getWfReference(), status, parentStepRef,
            results, wfRt.getSvcCtx());     // :1631 notifySubFlowEnd → 父行读写
}
// doEndWorkflow —— wfRt.saveWfRecord(status) 写 nop_wf_instance（version 乐观锁）
```
- **字段6 触发时序**：父流程两个 flow 步骤各挂子流 A、B，二者结束后的迁移路径均通向父 END。T_A：子流 A 结束 → notify 父 → 父 checkEnd → `doEndWorkflow(COMPLETED)` 写父行（version+1）→ 提交。T_B 与 T_A 在同一乐观锁窗口内并发：子流 B 结束 → `wf.isEnded()` 读 T_B 快照仍 false → `doEndWorkflow` → `saveWfRecord` 命中 version 冲突 → **T_B 整体回滚**，包括子流 B 自身的终态落盘与 endTime。子流 B 停留 ACTIVATED + 全步骤 history。
- **字段7 影响**：子流 B 永久滞留为孤儿实例（无 activated 步骤，due/remind 扫描与 auto-transitions 均不可达，同 C3-011 的无 rescue 分析）；调用方收到的是底层乐观锁异常，与"子流结束失败"无语义关联，易误判为偶发冲突重试了事——但子流已无重试入口。窗口窄（两个子流在同一 version 窗口内结束），故 P3。
- **字段8 根因**：子流终态落盘与跨实例父流程写耦合在同一事务，父行的乐观锁成为子流终态的单点失败源；`doEndWorkflow` 的 `wf.isEnded()` 守卫是快照读，挡不住并发双终结。
- **字段9 维度族对照**：F2（父已终结而子流未终结，两侧状态整体不一致）；非 F1/F9。
- **字段10 修复方向**：父侧终结改幂等原子更新（`UPDATE nop_wf_instance SET status=COMPLETED WHERE wf_id=? AND status<ENDED`，冲突即说明他方已终结，通知方继续自身提交）；或 notifySubFlowEnd 失败时以 REQUIRES_NEW 重试父侧通知，子流自身终态先行独立提交。
- **字段11 误报校准**：P3——错误可见（非静默）、窗口窄、若模型配置了 forHistory/forWithdraw 动作存在人工补救通道；同实例并发 kill vs action 的同写冲突由乐观锁显式裁决（错误可见），不属本条。
- **字段12 置信度**：中（写路径与锁冲突机制核实成立；未复现，实际到达需父模型多子流同窗结束）。
- **字段13 备注**：`WorkflowCoordinatorImpl.endSubFlow:60-72` 对父实例缺失已做跳过保护，但保护不了"父存在但行版本冲突"这一形态。
- **复核状态**: 已保留（P3。机制复核属实：endSubFlow 在子流结束事务内通知父流程（WorkflowEngineImpl:1622-1633）、coordinator 同上下文加载父实例并写父步骤/父行（WorkflowCoordinatorImpl:56-73）、nop_wf_instance versionProp（orm:181,254）、doEndWorkflow 的 wf.isEnded() 为快照读（:1599）挡不住并发双终结、notifySubFlowEnd 的 isHistory() 早退（:1145-1146）覆盖"父已终结后重试"路径。一处校正：字段7"子流已无重试入口/永久滞留孤儿"不成立——T_B 整体回滚使子流末步骤恢复 ACTIVATED（doExitStep 同事务，:1410-1427），due 扫描/人工重试该动作即可恢复（父已终结时 notify 走 isHistory 早退不再冲突），真实代价是动作副作用重放与乐观锁异常误导向；机制成立、后果轻于原文表述，P3 维持。现状：新发现）

---

## 2. 排除项/定向核查

1. **[C3-001 影响面修正——vote-group 高阈值配置同样受影响]** 第 1 轮 C3-001 字段11 称"VOTE_GROUP 因 member == step 特判，单侧即可判 complete"。复核 `ExecGroupSupport.isVoteGroupComplete`（ExecGroupSupport.java:33-72）：`member == step` 特判只把**当前触发步骤自身**计入 completeWeight；其余成员经 `member.isCompleted()` 读**同一请求快照**。当 passWeight/passPercent 高于当前成员单票权重（多数决、一致通过——现实中最高频的会签配置）时，并发投票互相看不到对方未提交的 COMPLETED → 双方都判 groupComplete=false → 组迁移丢失，形态与 and-group 完全一致。该站点与 C3-001 同一代码路径，不另立 ID；**C3-001 字段11 的豁免结论需按此修正**，修复面必须覆盖 vote-group。
2. **[C3-002 叠加矩阵（sync=true 与其他 wrapper）]** 装配序实测（TaskStepEnhancer.wrap:107-165，后包者在外）：`allowFailure > sync > validator > rateLimit > throttle > timeout > runOnContext > executor > retry > output > try > decorator`。逐项：sync+**timeout**=有界——timeout 兜底 promise 在 deadline 异常完成，`SyncTaskStepWrapper.execute` 的 `.sync()`（TaskStepReturn.java:208-213 → FutureHelper.syncGet）以取消异常抛出，阻塞线程被释放，**C3-002 的永久死锁在配置了 timeout 时不成立**（残余为 C3-005 已立案的幽灵 body）；sync+**retry**=有界（maxRetryCount 封顶）但调用线程阻塞横跨全部重试时长（重试恢复点见 C3-013）；sync+**throttle/rateLimit**=获取阶段 maxWait 有界；sync+**executor**=第 1 轮 C3-002（本轮补注其触发前提为"未配 timeout"）。矩阵无新独立立案项。
3. **[orTimeout/timeout 幽灵站点全家扫描]** `orTimeout`/`completeOnTimeout` 在两单元 main 代码**零命中**；超时机制仅 `TaskStepHelper.timeout` 一处（第 1 轮 C3-005 已覆盖，其 timer 在同步完成路径正确取消 :214/:222/:241）。`.schedule(` 全部三站：DelayTaskStep:41（no-op 占位，无幽灵工作体，干净）；TaskStepHelper:199（timeout 计时器，已覆盖）；TaskStepHelper:270（retry 延迟恢复 → 本轮 C3-013）。无新增幽灵站点。
4. **[withCancellable 共享 stepRt 交叉复位排除]** `withCancellable`（TaskStepHelper:357-386）对共享 stepRt 做 `setCancelToken(install)`/完成时 `setCancelToken(restore)`。调用点四家：GraphTaskStep:199（硬编码 autoCancel=true）、ParallelTaskStep:80、ForkTaskStep:67、ForkNTaskStep:47——均在**单次 withCancellable 作用域内**串行创建各分支子 stepRt（TaskStepRuntimeImpl:164 创建时快照继承 token），分支各自持有子 runtime 的 token 引用，install/restore 期间无并发读者，现调用形无交叉复位竞态。排除；若未来出现"同一 stepRt 并发进入两个 withCancellable"的用法需重新审查。
5. **[外部 kill/cancel API 缺失=已声明边界]** nop-task-service/main 代码 grep 无 killTask/cancelTask，无"taskInstanceId→运行中 runtime"内存注册表。任务取消的真实入口仅三条：宿主 svcCtx 取消（TaskRuntimeImpl:79-81）、step-timeout 计时器（per-step token，步骤级语义=已声明）、父任务 runtime（newChildRuntime:167-176）。因此深挖指令中的"部分 step 收到取消、部分继续执行"窗口在当前代码中不存在 svcCtx 级部分取消路径：task 级取消（svcCtx）经共享 token 全量可达所有步骤（协作式检查点密度为已声明边界）；step-timeout 只取消本步骤为设计语义。不立案。
6. **[SEQ 组 activateNextSeqStep 双触发排除]** 同一时刻组内至多一个 ACTIVATED 成员（newSteps:305-314 初始降级 WAITING + `isForStatus`:1792-1793 仅 ACTIVATED 可执行 forActivated action），并发双退出只能命中同一实例行，由 nop_wf_step_instance 行级乐观锁（orm.xml:353,457）显式裁决、错误可见。排除。
7. **[WfTaskScanner 上下文边界]** 独立 `ServiceContextImpl` + `wf-scheduler` 虚拟身份 + `ATTR_WF_SYSTEM_ACTION_TRIGGER` 系统标记（WfTaskScanner:151-156），显式传参不经 ThreadLocal；调度竞态由第 1 轮 C3-009 覆盖。干净。
8. **[IContext 其余异步边界]** executor 提交（C3-004 已立）；timer 线程回调体（C3-013 附带说明，同 C3-004 族不重复立案）；DelayTaskStep 完成→续延线程（同 C3-013 站点）；`ContextProvider.thenOnContext` 设施在本轮新增扫描中仍为零调用方。无新独立立案项。
9. **[runSourceWithRetry/Thread.sleep]** 第 1 轮 C3-007 已立，本轮复核未变。
10. **[kill 与 action 同实例并发写排除]** killSteps（WorkflowEngineImpl:1658-1666）与并发 action 退出同一 step 行：乐观锁 version 冲突显式失败、错误可见（区别于 C3-011/012 的跨行判定无锁可依），不立案。

---

## 3. 终止声明

本轮**有**新发现（C3-011×P1、C3-012×P1、C3-013×P2、C3-014×P3、C3-015×P3），故不适用"未发现新的高价值问题"终止信号。同时，本轮已完成第 1 轮遗留盲区的定向闭合，证据如下：

- **C3-001 族（wf"快照读→判定→写"）站点已穷举**：exec-group 判定（第 1 轮 C3-001）、vote-group 判定（第 2 节第 1 项影响面修正）、流程级 checkEnd 自动终结（C3-011）、and-join 激活与复用（C3-012）、signal 激活（自愈型，排除第 2-6 项邻近分析）、子流结束通知（C3-015）、withdraw/kill 同行写（乐观锁兜底，排除第 2-10 项）。wf 引擎内不再存在未检查的跨请求判定站点。
- **task 侧异步面**按第 1 轮全量入口清单复核完毕，新增仅 C3-013（timer 池占用）与 C3-014（svcCtx=null 取消断链），其余归入矩阵修正与排除项。
- 取消传播、timeout/幽灵、IContext 三条深挖线均已扫描到零残留（第 2 节第 3/5/8 项）。

**结论：本条目所列发现处置完毕后，C3 维度深挖可终止；不建议对 nop-wf/nop-task 再发起第 3 轮 C3 盲挖。**

## 4. 覆盖声明

本轮人工逐行核读（非仅 grep）的代码面：

- **nop-wf**：`WorkflowEngineImpl` 全文（重点 checkEnd/checkWaitingJoinStep/checkWaitingStep/doInvokeAction/doTransition/transitionTo/doEndWorkflow/endSubFlow/killSteps/runSourceWithRetry/isForStatus/canWithdraw）、`ExecGroupSupport` 全文、`WorkflowImpl` 全文、`WorkflowManagerImpl` 全文、`WorkflowCoordinatorImpl` 全文、`AbstractWorkflowStore` 全文、`DaoWorkflowStore` 全文、`WorkflowStepImpl`（组内读取与状态谓词段）、`DefaultWorkflowExecutor` 全文、`WorkflowServiceImpl`（事务注解面）、`WfTaskScanner`（上下文段）、`model/nop-wf.orm.xml`（version 列与状态常量）、`NopWfCoreConstants`（HISTORY_BOUND 语义）。
- **nop-task**：`TaskStepEnhancer` 全文（装配序）、`SyncTaskStepWrapper`/`ThrottleTaskStepWrapper`/`RateLimitTaskStepWrapper`/`RetryTaskStepWrapper`/`TryTaskStepWrapper`/`TimeoutTaskStepWrapper` 全文、`TaskStepHelper` 全文（timeout/retry/doRetry/withCancellable/cancel-reason）、`TaskStepReturn` 全文（sync/thenCompose/ASYNC/hookFuture）、`ExecutorTaskStepWrapper` 全文、`TaskStepExecution` 全文（sync/async 双出口 + driveStepFailure + continuation-skip + saveTerminalStateIfDone）、`TaskRuntimeImpl` 全文（cancel/newChildRuntime/newMainStepRuntime/cleanup）、`TaskStepRuntimeImpl`（token 字段与继承）、`TaskFlowManagerImpl`（executor/timer 获取）、`ParallelTaskStep` 全文、`GraphTaskStep`（execute/runStep/wait 构建段）、`SequentialTaskStep` 全文、`DelayTaskStep` 全文、`SleepTaskStep` 全文、`ITask`（asExecution）、`CallTaskStep`（子 runtime 获取段）、`TaskGenHelper`（脚本入口）。
- **平台设施用法面**：`GlobalExecutors`（globalTimer/cachedThreadPool/globalWorker）、`CommonConfigs`（timer 池缺省 5）、`DefaultScheduledExecutor`（ScheduledThreadPoolExecutor 包装）。
- **grep 定向扫描**：`orTimeout|completeOnTimeout`（零命中）、`.schedule(`（3 站全覆盖）、`withCancellable` 调用点（4+1）、`checkNotCancelled` 全部生产调用点（4）、`newTaskRuntime` 调用方、`killTask|cancelTask`（service 层零命中）、`asExecution` 调用方（零生产调用）。
- **契约核对**：第 1 轮 5 项已声明契约逐条对遇（其中契约 5 的 ACTIVE 取消前提在 svcCtx=null 形态下不成立 → C3-014）；`docs-for-ai/03-modules/nop-task.md` [03-06] 的 executor/并行声明与 C3-013 的"retry 恢复不经配置 executor"形成新的文档缺口（如立案采纳应同步修订 owner doc）。
