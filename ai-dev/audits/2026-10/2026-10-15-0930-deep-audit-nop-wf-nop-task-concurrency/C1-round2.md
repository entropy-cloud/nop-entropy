# C1 共享可变状态与可见性 — nop-wf + nop-task 第 2 轮深挖（盲审）

- 审计单元：`nop-wf`（10 jar）+ `nop-task`（10 jar），379 files / 36,107 lines（与第 1 轮同口径）
- 维度：C1 共享可变状态与可见性第 2 轮深挖（C1-001 失效面扩展复核 / 发布安全专项 / `++` 与 long-double 专项 / wf 变量容器跨请求共享面 / builder-POJO 归属抽验）
- 审计人：C1 子代理第 2 轮（盲审，未读 ai-dev/audits（除本维度第 1 轮文件）、ai-dev/plans、ai-dev/bugs、ai-dev/lessons）
- 日期：2026-10-15；分支：concurrency-audit-remediation；以 live code 为准（`_` 前缀生成文件不作发现对象）
- 前序输入：`C1-shared-mutable-state.md`（C1-001~008）+ `_tmp/cra-baseline-nop-wf-nop-task.txt`
- 严重程度判级：与第 1 轮 campaign 口径一致

## 1. 本轮发现

### [nop-wf-nop-task-C1-009] resume 执行期间 cancel 驱动的 KILLED 可被后续 SUSPENDED 转移覆写——显式 kill 后任务复活且可再次 resume

- 文件:行号：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:154-161`（suspend 分支无终态守卫）；`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java:97-105`（cancel 的 SUSPENDED→KILLED 驱动）；`nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStateBean.java:100-103`（setTaskStatus 裸赋值，无终态拦截——对比 `TaskStepStateBean.java:189-195` step 级有终态拦截）
- 证据片段（TaskImpl.java:153-162）：

```java
        return stepReturn.thenCompose((ret, err) -> {
            if (err == null && ret != null && ret.isSuspend()) {
                // SUSPEND 不是终态（check2 P0-2 / plan 349 Phase 1）...
                taskState.setTaskStatus(TaskConstants.TASK_STATUS_SUSPENDED);   // 无 isTerminal 检查、无 synchronized
                taskRt.saveTaskState();
                return ret;
            }
```

  证据片段（TaskStateBean.java:100-103）：

```java
    @Override
    public void setTaskStatus(Integer taskStatus) {
        this.taskStatus = taskStatus;      // 裸写：终态 KILLED 可被非终态 SUSPENDED 直接覆写
    }
```

- 严重程度：P1（罕见但可具体化的竞态；窗口为整个 resume 执行期，远宽于 C1-001 的微观交错；后果为 kill 语义违约 + 僵尸任务复活可再 resume）
- 并发场景：任务已 SUSPENDED → resume（`getTaskRuntime` 加载 bean，status 保持 SUSPENDED）后长耗时步骤体执行中；kill/overall-timeout 线程对同一 runtime 调用 `cancel`。
- 触发时序（已具体化，跨线程对真实存在——同 C1-001 的 TeamTaskFlowOrchestrator orTimeout 线程 / 请求取消线程 vs resume 执行线程）：
  - 前提 A（根因一）：resume 路径无 ACTIVE 重置写点——`DaoTaskStateStore.loadTaskState→toTaskStateBean`（`DaoTaskStateStore.java:402-403`）原样带入 SUSPENDED，全仓 grep 确认运行时 `TASK_STATUS_ACTIVE` 写者仅 `DefaultTaskStateStore.java:61` / `DaoTaskStateStore.java:137`（均为 fresh newTaskState 构造期）。resume 执行全程 bean.status 仍为 SUSPENDED；
  - T2（cancel 线程）：`super.cancel` 设 token（步骤体内长时间同步 XPL 不检查 token，仅步骤边界 `TaskStepExecution` 的 `checkNotCancelled` 生效——token 此时打不断 T1）→ 进入 `synchronized(state)`：读到 status==SUSPENDED 且 !isTerminal → 写 exception + `setTaskStatus(KILLED)` + `saveTaskState`（`TaskRuntimeImpl.java:99-104`）。该守卫的注释声明其针对"SUSPENDED（挂起、无驱动在飞）"，但前提 A 使**执行中**的任务同样命中此分支——cancel 的"ACTIVE 任务走 token 收敛"设计意图失效；
  - T1（resume 执行线程）：步骤体完成/到达下一挂起判定（suspend 返回不依赖 token，业务模型多次挂起是常态——多级审批/多阶段编排）→ thenCompose suspend 分支：`err==null && ret.isSuspend()` 成立 → **裸写 `setTaskStatus(SUSPENDED)` 覆写 T2 刚写入的 KILLED**（无终态守卫、不在监视器内）→ `saveTaskState()` 落盘 SUSPENDED；
  - 终态：DB 与内存均回写 SUSPENDED。T2 侧（AI 编排）已按 honest-failure 报告整体失败并认定图已取消，任务实际复活；后续 resume-when 触发将创建全新 runtime（fresh cancel token）继续执行——正是取消语义要阻止的 zombie 工作。
  - 反向守卫核对：若 T1 此后不再挂起而是正常完成，`driveTaskCompleted` 的 `skipTerminalOverwrite`（`TaskImpl.java:266-275`）会正确拦截 COMPLETED 覆写 KILLED——**六个 taskStatus 写点中唯 suspend 分支缺终态守卫**，失效面精确收敛。
- 现状：plan 364 [05-01] 把四个终态 driver 收敛到监视器 + skipTerminalOverwrite，但 (a) SUSPENDED 转移（第 1 轮 C1-001 已登记其游离）在此进一步暴露"无终态守卫"属性——C1-001 修复若仅"纳入监视器"而保留 suspend 语义，本条窗口（T2 先 KILLED、T1 后 SUSPENDED 的交错）仍需独立的 isTerminal 前置检查；(b) resume 无 ACTIVE 重置写点使 cancel 的状态判据失真。
- 风险：显式 kill / overall-timeout 后任务停留 SUSPENDED 且可恢复执行；运维/AI 编排认为任务已终结；审计面 kill 记录与任务状态自相矛盾。T2 的 `saveTaskState` 在无请求会话线程上落库的成功性属平台 ORM 设施行为（C1-006 已登记该错误面），但**本条失效不依赖 T2 落库成功**——即便 T2 落库失败，内存 bean 已 KILLED，T1 覆写后仍以 SUSPENDED 落盘。
- 建议：(1) TaskImpl suspend 分支前置终态检查（`isTerminal()` 时 warn + 直接返回/保持终态，对称 `TaskStepStateBean.succeed` 的 first-terminal-wins）并纳入 `synchronized(taskState)`（与 C1-001 修复合并为同一原子序列）；(2) resume 路径（`getTaskRuntime` recoverMode 或 `TaskImpl.execute` recover 分支）在确认非终态后显式重置 `TASK_STATUS_ACTIVE` 并落盘，使 cancel 守卫的状态判据恢复设计意图；(3) 为 TaskStateBean.setTaskStatus 补充 task 级"终态不可回退非终态"拦截（对称 step 级 `setStepStatus` 的既有守卫）。
- 失效面枚举（F3，全量 grep 复核）：taskStatus 运行时写者共 6 处（独立 grep 与第 1 轮一致，无第七写点）——①`TaskRuntimeImpl.java:102`（本条 T2 写点，守卫在但判据被前提 A 失真）；②`TaskImpl.java:190` COMPLETED；③`:206` FAILED；④`:241` KILLED；⑤`:256` TIMEOUT（②-⑤均在监视器+skipTerminalOverwrite 内，不受本条影响）；⑥**`TaskImpl.java:159` SUSPENDED（本条 T1 写点，双重缺守卫：无监视器 + 无终态拦截）**。加载期写者 `DaoTaskStateStore.java:403` / `DefaultTaskStateStore.java:61` 为构造期单线程，不在失效面。修复必须同时覆盖 ⑥ 与 resume 重置，且不得回归 ①-⑤。
- 信心水平：高（resume 无 ACTIVE 重置写点与 suspend 分支无终态守卫均为 grep + 逐行确证；三条件叠加——resume+cancel 并发+再次挂起——在多级挂起任务模型与 AI 编排 kill 场景下自然发生）
- 误报排除：非"无状态单例被并发调用"；非 synchronized/CHM 原语本身问题（恰是守卫缺失与状态判据失真）；正常完成路径由 skipTerminalOverwrite 正确兜底，不在失效面内。
- 复核状态： 已保留（P1——从严复核通过：前提 A 经全量 grep 确证（TASK_STATUS_ACTIVE 运行时写者仅 DaoTaskStateStore:137/DefaultTaskStateStore:61 两处 fresh 构造期）、SuspendTaskStep.execute 不检查 cancel token（覆写路径 err==null 成立）、TaskStateBean.setTaskStatus 裸赋值确证；owner doc nop-task.md 对 cancel-KILLED 声明的"SUSPENDED（无驱动在飞）"前提被 resume 路径事实违反。保留意见：'窗口=整个 resume 执行期'表述偏宽——有效窗为最后一个 cancel-token 检查点（TaskStepExecution:271）至下一挂起转移完成，多级挂起任务模型下仍自然命中，不改判级）

### [nop-wf-nop-task-C1-010] wf 实例级状态转移四实现守卫不一致——NopWfInstance/WorkflowRecordBean 的 transitToStatus 零进程内守卫，终态回退静默通过（第 1 轮 C1-002"4 处实现一致"陈述不成立）

- 文件:行号：`nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/entity/NopWfInstance.java:36-51`（零守卫）；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/store/beans/WorkflowRecordBean.java:115-117`（零守卫）；对照 `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/entity/NopWfStepInstance.java:33-44` 与 `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/store/beans/WorkflowStepRecordBean.java:127-136`（均有 history 回退 fail-fast）
- 证据片段（NopWfInstance.java:36-51，对照 NopWfStepInstance.java:33-41 的守卫）：

```java
    // wf 实例级：任何状态转移无条件接受，仅记录 history
    public void transitToStatus(int status) {
        Integer prevStatus = getStatus();
        setStatus(status);
        if (prevStatus != null && prevStatus != status) {
            NopWfStatusHistory history = new NopWfStatusHistory();
            ...
            getStatusHistories().add(history);
        }
    }

    // step 实例级（对照）：history 态回退非 history 态 fail-fast
    public void transitToStatus(int status) {
        Integer cur = getStatus();
        if (cur != null && cur >= WF_STEP_STATUS_HISTORY_BOUND && status < WF_STEP_STATUS_HISTORY_BOUND)
            throw new NopException(ERR_WF_INVALID_STEP_STATUS_TRANSITION)...
    }
```

- 严重程度：P2（会话内回退路径需用户配置事件监听器方可具体化；跨会话竞态由 DB version 列兜底、无数据损坏；但第 1 轮 C1-002 的失效面陈述"终态方向守卫在 4 处实现一致"经独立复核为**事实错误**——仅 step 级 2 处实现有守卫，wf 级 2 处为零守卫，直接影响 C1-002 修复方案的覆盖面评估）
- 并发场景 A（会话内，可配置触发）：引擎 kill 流（`WorkflowEngineImpl.kill:713-733`）经 `markEnd` + `delayExecute(doEndWorkflow(KILLED))`（`WfRuntime.saveWfRecord:319` → `NopWfInstance.transitToStatus(70)`）后，同请求内 `triggerEvent(EVENT_AFTER_KILL)` 执行用户模型配置的 listener；listener 调用 `runAutoTransitions`/激活类 API 时，引擎可对已 KILLED 的流程再次驱动 `saveWfRecord(WF_STATUS_ACTIVATED(30))`（`:470`）——wf 级零守卫下 KILLED→ACTIVATED 静默通过并落库（终态复活）；同场景 step 级会被 `ERR_WF_INVALID_STEP_STATUS_TRANSITION` fail-fast 拦截。状态码序核实：`_NopWfCoreConstants.java` WF_STATUS CREATED=0/SUSPENDED=10/ACTIVATED=30/COMPLETED=40(HISTORY_BOUND=40)/KILLED=70，KILLED→ACTIVATED 为纯回退。
- 并发场景 B（跨会话，C1-002 同族）：T1 resume（入口守卫 `!wf.isSuspended()` 在 T1 会话副本上通过）与 T2 kill（入口守卫 `wf.isEnded()` 在 T2 会话副本上通过）并发同一 wfId——两个守卫读取各自新鲜加载的 SUSPENDED 副本均放行，双写 ACTIVATED/KILLED，后到者 version 冲突裸 ORM 错误。此为第 1 轮 C1-002 已登记形态的 wf 级实例化；本条新增的事实是：**wf 级不存在 step 级那样的会话内二次兜底守卫**，第 1 轮对该竞态"终态方向守卫兜底有效"的评估对 wf 级不成立。
- 现状：`WfRuntime.saveWfRecord`（`WfRuntime.java:317-323`）是 wf 级唯一转移驱动（调用点：start `:147-148`、激活 `:470`、suspend `:659`、resume `:682`、kill `:732`、complete `:1592`），入口检查-执行-落盘非原子，与 step 级同构；差异仅在实现层守卫。`WorkflowRecordBean.transitToStatus` 为非 DAO 模式实现，同样零守卫。
- 风险：会话内终态复活（需监听器配置，边界触发）；跨会话竞态下错误语义与 C1-002 相同（裸 ORM 错误码）；`getStatusHistories().add(history)` 在零守卫下会为回退转移追加"KILLED→ACTIVATED"history 行，审计数据固化该异常转移。
- 建议：为 `NopWfInstance.transitToStatus` / `WorkflowRecordBean.transitToStatus` 补齐与 step 级对称的 `WF_STATUS_HISTORY_BOUND` 回退 fail-fast（对齐 `NopWfStepInstance.java:38-43` 实现）；同时修订第 1 轮 C1-002 失效面陈述——其修复若采用"占位条件更新"方案，必须覆盖 wf 级 `saveWfRecord` 全部 6 个调用点，不能只按"4 处守卫一致"的前提只处理 step 级。
- 失效面枚举（F3，全量 grep 复核）：`transitToStatus(` 文本命中 17 处 = 15 处运行时调用（14 处 step 级：`WorkflowEngineImpl.java:310,400,403,405,407,409,512,789,836,1017,1373,1414,1431,1716` + `WfRuntime.java:319` wf 级 1 处）+ 2 处接口声明（`IWorkflowRecord.java:34` / `IWorkflowStepRecord.java:46`）——第 1 轮"17 处调用"口径含接口声明，独立复核实际调用 15 处。实现面 4 处中 2 处零守卫（本条），2 处 fail-fast。
- 信心水平：高（守卫不一致为逐行确证；场景 A 的可达性依赖用户 listener 配置，故按纪律 P2）
- 误报排除：不按"跨会话 version 冲突"重复立案（C1-002 已登记该根因）；不报 step 级两实现（守卫在且语义正确）；`NopWfInstance.transitToStatus` 的 history 追加行为本身正确，问题仅在缺回退拦截。
- 复核状态： 已降级至 P3（守卫不对称事实（NopWfInstance:36-51/WorkflowRecordBean:115-117 零守卫 vs step 级两实现 fail-fast）与对 C1-002"4 处实现一致"的事实修订经独立复核均成立、予以采纳；但场景 A 的具体时序有误：kill 运行于 WorkflowImpl.executeNow 内（executing=true），doEndWorkflow(KILLED) 经 delayExecute 排队，实际在 EVENT_AFTER_KILL listener 之后才执行——listener 运行时记录仍为 ACTIVATED，并非"已 KILLED"；KILLED 落定后 wf 级仅有的三个非终态 driver 入口守卫（start:178-180 ERR_WF_ALREADY_STARTED / suspend:646-650 ERR_WF_NOT_ALLOW_SUSPEND / resume:671-672 幂等 return）读取同一内存记录全部拦截，live code 不存在可具体化的会话内终态回退驱动路径；场景 B 归 C1-002 族（version 列兜底，orm.xml:181/353 确证）。零守卫的实际暴露面=防御纵深缺失，对称 C1-008 校准降 P3；对 C1-002 修复覆盖面的修订（wf 级 saveWfRecord 6 调用点）仍具约束力）

## 2. 排除项/定向核查

以下为第 2 轮深挖指令逐项执行后的排除结论（均经独立 grep/逐行核查，非沿用第 1 轮结论）：

1. **taskStatus 写点矩阵复核（C1-001 失效面扩展）**：全量 grep `setTaskStatus(` 确认运行时写者恰 6 处 + 加载期 2 处，与第 1 轮枚举完全一致，无第七写点。`DaoTaskStateStore.java:137`（`entity.setStatus(ACTIVE)`，fresh 构造期）与 `:173-174`（saveTaskState 中 bean→entity 拷贝）为 ORM 实体侧写面，非独立状态源，不构成新写点。`TASK_STATUS_WAITING(20)` 全仓无运行时写者（死表面）。resume 无 ACTIVE 重置写点——该发现已升级为 C1-009 根因一，不单独立案。
2. **suspend/resume/cancel/kill 守卫一致性矩阵**：task 级 4 终态 driver（监视器+skipTerminalOverwrite）✓；cancel 的 SUSPENDED→KILLED 驱动（监视器+status 判据）✓（判据失真见 C1-009）；SUSPENDED 写者双缺（C1-001/C1-009）；正常完成/失败路径在 KILLED 先到时由 `skipTerminalOverwrite` 正确拦截（逐行核对 `TaskImpl.java:266-275` 同状态幂等放行逻辑）✓。wf 级矩阵见 C1-010。
3. **发布安全专项**：两簇 main 代码 `new Thread(` / `Executors.new` / `ThreadLocal` 命中数 **0**。构造器 this 逃逸仅 3 处形态：①`TaskRuntimeImpl` 构造器内 `svcCtx.appendOnCancel(this::cancel)`（`:79-81`）——构造中注册监听器，但回调仅 cancel 时触发，早到取消（taskState 尚为 null）经 `:95-96` null 检查静默返回，cancel token 已由 `super.cancel` 设置、步骤驱动收敛为 KILLED，无提前泄漏损害（第 1 轮 T12 边界观察维持，不立案）；②`WfResourceNamespaceHandler.init` `@PostConstruct` 注册 `VirtualFileSystem`（`:34-37`）——注入完成后注册，容器单线程 init 后发布，安全；③`WorkflowManagerImpl.init` `@PostConstruct` `new WorkflowCoordinatorImpl(this)`（`:55-59`）——同上。`WfModel.init`/`TaskFlowModel.init` 的 `analyze(this)` 为模型加载期单线程。
4. **TaskRuntimeImpl.taskState/metrics/enabledFlags 非 volatile 字段的跨线程可见性**：定向核查唯一活跨线程 cancel 方——`TeamTaskFlowOrchestrator.java:655`（nop-ai）orTimeout 链：`task.execute(taskRt)`（:609，setTaskState 之后）→ `.asyncOutputs().thenApply().orTimeout()` 的 Delayer 任务由请求线程提交，JUC 提交 HB 覆盖 setTaskState 写入 → Delayer 线程 `exceptionally` 回调读 taskState 可见 ✓。`svcCtx.cancel` 传播路径：全仓 grep 确认除 `TaskRuntimeImpl.java:88` 自身与平台 `DelegateServiceContext` 委托外无触发方，不可达 ✓。父子 runtime（`newChildRuntime:167-176`）：子 runtime 完全初始化后才 `appendOnCancel`，父 Cancellable 监视器锁提供 HB ✓。结论：无可具体化的可见性缺口，不立案。文档不对称观察（不立案）：`TaskStepRuntimeImpl` 有类头访问约束不变式注释（`:24-30`），`TaskRuntimeImpl` 对 taskState/metrics/enabledFlags 无等价声明——建议修复 C1-001/C1-009 时顺带补写。
5. **long/double 非原子读写**：两簇非 final 实例 long/double 字段仅 `CallTaskStep.taskVersion` / `CallStepTaskStep.libVersion`——构建期 set、运行期只读的 final 语义配置（实际上未标 final，归属构建期写点，安全发布后无运行期写者）。无裸共享 long/double。
6. **`++` 复合递增全量核对**：`TaskStateBean.nextRunId++`（synchronized 方法内，`:90-93`）✓；GraphTaskStep `runningCount`/`waitingCount`（AtomicInteger）✓；`LoopTaskStep.java:114` / `LoopNTaskStep.java:145-146` LoopStateBean.index/current（串行迭代驱动线程）✓；`SelectorTaskStep.java:52,73`（局部）✓；`AbstractForkTaskStep.buildAggResult:124` / `MultiStepResultBean.count:96`（thenApply 回调内局部）✓；wf 侧 `WorkflowEngineImpl.java:302`（execOrder 局部）/`:900`（attempt 局部）/`WorkflowServiceImpl.java:240`（successCount 局部）✓；`IWorkflowStepRecord.incExecCount`（接口 default RMW，唯一调用点 `WorkflowEngineImpl.java:884` 每请求会话内副本）——与 C1-002 同族（会话内单线程 + 落库 version 收敛），不另立案，登记为 C1-002 修复覆盖面同族点。`ChooseTaskStepModel.java:30` i++ 为模型构建期局部。
7. **可变对象共享引用/容器**：`GraphTaskStep` 复核——`stepFutures`（HashMap）在 `initFutures` 发布前填充、回调仅读（HB 经 whenComplete 注册链）；`errorConsumers`（HashSet）发布前填充只读；`stepResults` 为 CHM（`makeResults:429-438`），并发 `put` 安全 ✓（第 1 轮 T8 判定维持）。`ParallelTaskStep`/`AbstractForkTaskStep` 聚合容器为回调内局部 `MultiStepResultBean`（LinkedHashMap 单线程）✓。`MultiStepResultBean.results` 的 `add` 唯二调用点均在单线程聚合回调内 ✓。
8. **builder/POJO 构建期写点归属抽验**：`TaskFlowModel` 三个懒构建（`getTask:38-43`/`getTaskStepLib:49-54`/`getBeanContainerTemplate:73-81`）在**全局缓存的共享模型对象**上全部 synchronized 且字段无裸读路径（grep 确认 setter 仅模型加载期调用）——懒构建竞争安全 ✓。`WfModel.dag` 由 `init()`（模型加载期）一次构建，运行期 `getDag()` 只读 ✓。step wrapper 5 个（Timeout/RateLimit/Throttle/Retry/Try）与 `ExecutorTaskStepWrapper` 全 final 配置字段 ✓。`TaskFlowBuilder`/`TaskStepLibBuilder` 经 `newTaskFlowBuilder()` 每调用新建（`TaskFlowManagerImpl.java:247-253`）✓。task-service 4 个 + wf-service BizModel 无实例字段 ✓（第 1 轮判定维持）。
9. **wf 变量容器跨请求共享面**：`DaoWorkflowStore.getGlobalVars/getOutputVars`（`:242-250`）返回 `new KvTableVarSet(entity.getGlobalVars())` 会话实体 KV 包装——实体归属请求会话，会话内单线程 ✓。`WorkflowImpl.globalVars/outputVars` 懒加载缓存为 C1-003 已立案载体（本轮复核含 `reload():65-72` 对 wfRecord/steps/commandQueue 的同族写点，同一线程封闭前提覆盖）✓。`WfRuntime` 14 个可变字段 per-request 新建（`WorkflowEngineImpl.newWfRuntime`）复核 ✓；`WorkflowStepImpl` 3 字段全 final 复核 ✓。
10. **WfTaskScanner 共享实体**：`reminderListeners` init 期 @Inject 一次赋值、运行期只读遍历（`:57-62,126`）✓；remindCount RMW 与扫描互斥缺失维持 C1-005 原判；NopWfInstance/NopWfStepInstance 的内存态 vs DB 态归属：每请求独立 ORM 会话各持副本，跨会话收敛点统一在保存点 version 列（C1-002/C1-010 已覆盖），无额外共享载体。
11. **ExecutorTaskStepWrapper / TaskStepHelper.timeout 取消接线**：`if (!ret.isDone()) ret.completeExceptionally(...)` 的 check-then-act 由 CompletableFuture 原子完成语义兜底（done 后 complete 为 no-op）✓；`appendOnCancel` 在 token 已取消态下立即回调的路径（`Cancellable.tryRunCallbacks`）与 `ret.whenComplete` 注销时序闭环 ✓；`timeout` 竞速双守卫（timeoutGuard + winner）无窗口 ✓。`Cancellable` 本体（nop-commons 平台设施）cancel/appendOnCancel 全 synchronized + CAS 判定，超出单元范围不审。
12. **transitToStatus 计数口径**：第 1 轮"17 处调用"实为 15 处运行时调用 + 2 处接口声明的文本命中合计（见 C1-010 F3），第 1 轮 C1-002 的失效面族描述仍成立，但"4 处实现一致"须按 C1-010 修订。

## 3. 终止声明

第 2 轮深挖指令的 5 项盲区（C1-001 写点矩阵扩展复核、suspend/resume/cancel/kill 守卫矩阵、发布安全专项、`++`/long-double/可变共享引用专项、wf 变量容器与 Scanner 共享实体、builder/POJO 归属抽验）已全部执行完毕。除 C1-009（P1）与 C1-010（P2）外，其余定向核查全部收口为排除项（12 项，见第 2 节），无未穷尽的载体族、无留空的失效面。

**未发现新的高价值问题。C1 维度深挖终止。**

（终止限定说明：本终止声明针对 C1 共享可变状态与可见性维度在本审计单元内的第 2 轮深挖；C1-009/C1-010 的修复如引入新的同步结构，其锁序与协议完备性归 C2 锁与协议维度复核。）

## 4. 覆盖声明

- **序号接续**：本轮新增 C1-009、C1-010，接续第 1 轮 C1-001~008；C1 维度累计 10 项（P1×2 = C1-001、C1-009；P2×7 = C1-002/003/004/005/006/007/010；P3×1 = C1-008）。
- **深挖指令对照**：指令 5 项盲区 → 执行记录见第 2 节排除项 1-12；C1-001 失效面经独立 grep 复核维持"6 运行时写点"枚举并扩展出 C1-009 的交错结局；发布安全专项为本轮新增盘点面（第 1 轮仅 T12 边界观察），结论维持无立案但补齐了逐点证据。
- **方法**：独立 grep（`setTaskStatus`/`transitToStatus`/`synchronized`/`++`/`+=`/long-double 字段/`new Thread`/`ThreadLocal`/构造器 this 逃逸/`appendOnCancel`/`MultiStepResultBean`/`incExecCount`）+ 重点类逐行深读（TaskImpl、TaskRuntimeImpl、TaskFlowManagerImpl、TaskStepRuntimeImpl、TaskStepStateBean、TaskStateBean、DaoTaskStateStore、ParallelTaskStep、AbstractForkTaskStep、GraphTaskStep、ExecutorTaskStepWrapper、TaskStepHelper、WfTaskScanner、WorkflowManagerImpl、WorkflowImpl、WorkflowCoordinatorImpl、TaskFlowModel、WfModel、WfResourceNamespaceHandler、Cancellable、四 transitToStatus 实现）+ 跨单元触发链取证（`TeamTaskFlowOrchestrator.java:600-680` 只读引用，不作审计对象）。
- **误报校准执行**：无状态单例并发调用（BizModel/wrapper/store singleton）、锁与 CHM 原语本身（Cancellable/CF/CHM/条带锁）、低频路径粗粒度锁（TaskFlowModel synchronized 懒构建）、有界声明结构（64 槽条带锁）、平台设施自身实现（Cancellable/IEvalScope/ORM 会话/ResourceComponentManager 模型缓存）均按口径排除。
- **盲审纪律遵守**：本轮仅读取第 1 轮 C1-shared-mutable-state.md 一个审计目录文件；未读取 ai-dev/plans、ai-dev/bugs、ai-dev/lessons 及其他维度审计文件；未修改第 1 轮文件。
- **残留边界（移交对应维度）**：①TaskRuntimeImpl 类头无访问约束不变式注释的文档缺口（随 C1-001/C1-009 修复补写，不单独立案）；②resume 无 ACTIVE 重置的**非并发**语义面（状态面板显示 SUSPENDED 而任务实际执行中）——纯状态机展示问题，归状态机/业务语义维度；③T2 cancel 线程上 `saveTaskState` 的 ORM 会话归属与成功性——平台 ORM 设施行为（C1-006 已登记错误面），归平台基础设施审计；④C1-010 场景 A 的 listener 配置可达性需结合 wf 模型使用规约（owner doc `nop-wf.md` 对引擎并发语义零声明的漂移登记维持第 1 轮结论）。
