# C1 共享可变状态与可见性 — nop-wf + nop-task 第 3 轮收口（盲审）

- 审计单元：`nop-wf`（10 jar）+ `nop-task`（10 jar），379 files / 36,107 lines（与前两轮同口径）
- 维度：C1 共享可变状态与可见性第 3 轮收口（task 状态机转移守卫三元矩阵穷尽 / resume 入口与执行期并发转移面 / wf transitToStatus 守卫同族扩展）
- 审计人：C1 子代理第 3 轮（盲审，未读 ai-dev/audits（除本维度第 1、2 轮文件）、ai-dev/plans、ai-dev/bugs、ai-dev/lessons）
- 日期：2026-10-15；分支：concurrency-audit-remediation；以 live code 为准（`_` 前缀生成文件不作发现对象）
- 前序输入：`C1-shared-mutable-state.md`（C1-001~008）+ `C1-round2.md`（C1-009/C1-010 + 终止声明）+ `_tmp/cra-baseline-nop-wf-nop-task.txt`
- 严重程度判级：与前两轮 campaign 口径一致
- 本轮派生依据：campaign 继续条件（第 2 轮新增含 C1-009 P1，必派下一轮）；本轮为收口轮，任务 1-3 全部闭合（见第 2 节），无 C1-011 及之后编号

## 1. 本轮发现

**无新增发现。** 三项收口任务（task 状态机转移守卫三元矩阵穷尽、resume 入口与执行期并发转移面、wf transitToStatus 守卫同族扩展）经独立 grep + 逐行复核全部闭合，未发现第 1、2 轮登记（C1-001~010）之外的新的可立案缺陷。第 2 轮终止声明维持有效，本收口轮为其补足穷尽性证据（三元矩阵与入口矩阵见第 2 节）。

## 2. 排除项/定向核查

以下为收口轮三项任务逐项执行后的结论（均经本轮独立 grep/逐行核查，非沿用前轮结论）。

### 2.1 任务 1：task 状态机转移守卫三元矩阵穷尽（独立复核第 2 轮"6+2 写点矩阵完全一致"清单）

**task 级 taskStatus 运行时写者复核**：全量 grep `setTaskStatus(`（nop-task/nop-wf main 代码，剔除 `_gen/` 与 test）确认运行时写者恰 **6 处** + 加载/构造期 **2 处**，与第 2 轮枚举逐一对应，无第七写点。三元矩阵（终态守卫有无 × 监视器内外 × 落盘路径）穷尽如下：

| # | 写点 | 状态 | 终态守卫 | 监视器 | 落盘路径 | 判定 |
|---|------|------|---------|--------|---------|------|
| ① | `TaskRuntimeImpl.java:102` | KILLED(70) | ✓ `status==SUSPENDED && !isTerminal()` 前置条件（:99） | ✓ `synchronized(state)`（:97） | `saveTaskState()` 监视器内（:103） | 守卫在；判据被"resume 无 ACTIVE 重置"失真 = C1-009 根因一，已立案 |
| ② | `TaskImpl.java:190` | COMPLETED(40) | ✓ `skipTerminalOverwrite`（:187→:266-275） | ✓ `synchronized(taskState)`（:186） | 监视器内（:191） | 闭合 |
| ③ | `TaskImpl.java:206` | FAILED(60) | ✓ 同上（:203） | ✓（:202） | 监视器内（:207） | 闭合 |
| ④ | `TaskImpl.java:241` | KILLED(70) | ✓ 同上（:238） | ✓（:237） | 监视器内（:242） | 闭合 |
| ⑤ | `TaskImpl.java:256` | TIMEOUT(50) | ✓ 同上（:253） | ✓（:252） | 监视器内（:257） | 闭合 |
| ⑥ | `TaskImpl.java:159` | SUSPENDED(10) | **✗ 无** | **✗ 游离** | `saveTaskState()` 游离（:160） | **唯一双缺点** = C1-001（游离）+ C1-009（可覆写 KILLED），已立案 |

- 加载/构造期 2 处：`DaoTaskStateStore.java:403`（`toTaskStateBean` 单线程构造）+ `DefaultTaskStateStore.java:61`（fresh `newTaskState`，该 store save 为 no-op、构造期即弃）——均构造期单线程，不在失效面。
- ORM 实体侧写面（非独立状态源，与第 2 轮口径一致）：`DaoTaskStateStore.java:137`（fresh 实体 ACTIVE 构造期）、`:173-174`（saveTaskState 中 bean→entity 条件拷贝）。
- `TASK_STATUS_WAITING(20)` 本轮 grep 复核：全仓 main 代码仅常量定义（`TaskConstants.java:99`、`_NopTaskCoreConstants.java:64`），零运行时写者——死表面判定维持。
- grep 噪音排除：`nop-ai/.../PlanExecutionState.java:146` 的 `setTaskStatus(String, AgentExecStatus)` 为 AI agent plan 状态的自有同名方法（不同类型体系，任务号 String、状态枚举），非 `ITaskState` 写者，不在本单元失效面。
- 状态码基线核实（守卫方向判定的前提）：`_NopTaskCoreConstants.java:54-89` SUSPENDED=10/WAITING=20/ACTIVATED=30/COMPLETED=40/EXPIRED(=TIMEOUT)=50/FAILED=60/KILLED=70；`ITaskState.isTerminal()`（`ITaskState.java:20-28`）= {40,70,60,50}。⑥ 的 SUSPENDED→① 的 KILLED 为终态覆写方向，②-⑤ 的 `skipTerminalOverwrite` 对该方向全部拦截（`TaskImpl.java:266-275` 同终态幂等放行逻辑逐行复核）。

**task 级 stepStatus 运行时写者穷尽**（同族成员核对）：grep `setStepStatus(` + `state.succeed/state.fail` 全量确认运行时写者收敛于两处 driver 聚合点，共 6 个调用点，全部经 `TaskStepStateBean` 内部无锁 first-terminal-wins 守卫（`succeed:46-52` / `fail:54-63` / `setStepStatus:189-195`，C1-004 已登记其锁缺失面），无绕过 bean 的裸写者：

| 写点 | 驱动 | 落盘 |
|------|------|------|
| `TaskStepExecution.java:349`（succeed→COMPLETED） | 步骤成功 thenCompose | `saveTerminalStateIfDone`（:356→:426-434，isDone 条件） |
| `TaskStepExecution.java:386-390`（fail + EXPIRED/KILLED） | cancel 分类驱动（driveStepFailure） | `saveTerminalStateIfDone`（:390） |
| `TaskStepExecution.java:399-402`（fail + FAILED） | 普通失败驱动 | `saveTerminalStateIfDone`（:402） |
| `TaskStepHelper.java:301`（retry 同步 succeed） | retry 成功 | 非终态记账（ACTIVE，无落盘） |
| `TaskStepHelper.java:309`（retry fail，非取消类） | retry 失败计数 | `:316-317` retryAttempt 增量落盘 |
| `TaskStepHelper.java:352`（doRetry 异步 succeed） | retry 异步成功 | 非终态记账 |

- 构造/加载期：`DefaultTaskStateStore.java:33,46`、`DaoTaskStateStore.java:273,308`（fresh ACTIVE 构造期）、`DaoTaskStateStore.java:449`（load）、`:498-499`（bean→entity 拷贝，saveStepState 条带锁内）。
- 结论：**写点矩阵 6+2 独立复核成立**；step 级家族成员全部收敛于 C1-004 已登记的"无锁 check-then-act"面，无未登记的第七写点或裸写旁路。

### 2.2 任务 2：resume 全部入口与执行期窗口内的全部并发转移面

**入口矩阵**（全仓 main 代码 grep `getTaskRuntime(` / `new TaskRuntimeImpl(` 穷尽）：

| 入口 | 位置 | recoverMode | 生产调用方 |
|------|------|-------------|-----------|
| API 面 | `ITaskFlowManager.java:27`（2 参 default）→ `TaskFlowManagerImpl.java:121-134`（`new TaskRuntimeImpl(..., true)` 唯一构造点） | true | **仓内唯一**：`CallTaskStep.java:89`（call-task 断点续跑，`task.execute(subRt, outputNames)` :97）；无 scheduler/job 入口（本单元无触发器定义） |
| fresh 面 | `TaskFlowManagerImpl.java:97-106`（`new TaskRuntimeImpl(..., false)`） | false | `TeamTaskFlowOrchestrator.java:587`（nop-ai，`newTaskRuntime(built.task, false, null)`）等——非 resume；其 orTimeout cancel（`:655`）是 C1-001/009 的 T2 驱动侧 |

**执行期窗口内的并发转移面穷尽**（对 resume 执行全程逐面核对）：

1. **cancel 驱动（token + SUSPENDED→KILLED）**：窗口 = resume 加载 bean（状态保持 SUSPENDED，`DaoTaskStateStore.toTaskStateBean:402-403` 原样带入，全仓确认无 ACTIVE 重置写点）→ 步骤体执行 → 下一挂起点/完成。T2 cancel 在窗口内：token 对同步 XPL 体无效（仅步骤边界 `TaskStepHelper.checkNotCancelled:71-80` 生效）→ 监视器内 KILLED（`TaskRuntimeImpl.java:97-105`）→ T1 后到挂起分支裸覆写（`TaskImpl.java:159`）——即 C1-009 全时序，已立案，本轮逐行复核成立。
2. **双重 resume（两次 `getTaskRuntime` 并行同 taskInstanceId）**：两个 runtime 各持独立 bean（均 SUSPENDED），全量并行重跑 mainStep；引擎层无互斥/状态认领/行占位，收敛仅在保存点 version 列——根因面已由 C1-006（saveTaskState 无条带锁 + 裸 ORM 错误 + 内存/DB 漂移）承载，业务副作用双执行与 C1-002（wf 同 step 双提交）同构同族。**不新增立案**，登记为 C1-002/C1-006 的 resume 场景实例化，修复覆盖面须包含该入口。
3. **resume 短路判定窗口**：`TaskImpl.execute:98` 的 `isRecoverMode() && isTerminal()` 基于 load 快照；kill 若在 load 前落库 → 短路重抛 `ERR_TASK_ALREADY_KILLED/TIMEOUT/FAILED`（`:104-116`，synthesizeResumeException 区分）✓；kill 在 load 后 execute 前落库 → resume 继续（bean 仍 SUSPENDED）→ 归入面 1 的 C1-009 交错。无独立缺口。
4. **取消传播**：CallTaskStep resume 分支子 runtime 经共享 `svcCtx`（`:89` 传 `taskRt.getSvcCtx()`）获得 `svcCtx.appendOnCancel(onCancel)`（`TaskRuntimeImpl` 构造器 :79-81）→ 父 cancel → svcCtx.cancel → 子 cancel ✓（与 `newChildRuntime:167-176` 显式 appendOnCancel 机制不同但等效）。非立案。
5. **resume 期延迟/超时续体**：`DelayTaskStep.java:41-47` 经 `FutureHelper.bindCancelToken(cancelToken, future)` 接线取消 ✓；retry 延迟续体（`TaskStepHelper.retry:268-284`）回调内经 doRetry→checkNotCancelled 收敛 ✓；`TimeoutTaskStepWrapper`→`TaskStepHelper.timeout:184-244` 竞速双守卫（第 2 轮排除项 11 维持）✓。全部经 future 链 HB，无可见性缺口。
6. **wf 侧 resume 对照**（`WorkflowEngineImpl.resume:668-684`）：入口守卫 `!wf.isSuspended() → return`（:671-672，幂等 no-op）+ `saveWfRecord(ACTIVATED)`（:682）——resume 仅状态翻转 + 事件触发，无 task 侧那样的长执行期窗口，**不存在 wf 版 C1-009 形态**；其跨会话竞态由 C1-010 场景 B 承载。

**结论**：resume 面无新增立案；C1-009 的修复建议(2)（resume 确认非终态后显式重置 ACTIVE 并落盘）经本轮入口矩阵核实具备完整落点（唯一入口 `TaskFlowManagerImpl.getTaskRuntime`，修复单点可达）。

### 2.3 任务 3：wf transitToStatus 守卫同族扩展——step 级 fail-fast 实现对全部 step 状态写者的覆盖核对

**写者全覆盖核实**：全量 grep `transitToStatus(`（nop-wf main）= 15 处运行时调用 + 2 处接口声明，与第 2 轮 C1-010 F3 口径一致。其中 step 级 14 处（`WorkflowEngineImpl.java:310,400,403,405,407,409,512,789,836,1017,1373,1414,1431,1716`）**全部**经 `record.transitToStatus(...)`，而 `IWorkflowStepRecord` 仅有两个实现——`NopWfStepInstance.java:33-44` 与 `WorkflowStepRecordBean.java:127-138`，二者 history 回退 fail-fast 守卫**逐字一致**（`cur >= WF_STEP_STATUS_HISTORY_BOUND(=COMPLETED,40) && status < 40 → throw ERR_WF_INVALID_STEP_STATUS_TRANSITION`）。**全覆盖成立，无裸 setStatus 旁路**：

- 直接 setter 排除：grep `setStatus(`/`setStepStatus(` 于 wf main——step record 侧仅 transitToStatus 内部调用（`WorkflowStepRecordBean.java:137`、`NopWfStepInstance.java:43`）；`DaoWorkflowStore.java:90` 的 `setStatus(WF_STATUS_CREATED)` 为 `newWfRecord`（:82-94）fresh 构造期（会话内单线程新建，step 侧 `newStepRecord:97-111` 则不设 status，首态经 transitToStatus 写入，cur==null 时守卫放行——构造期语义正确）。
- 引擎级驱动入口守卫逐点核对（会话副本层 + 记录层双防护）：`:310` SEQ_GROUP `s.isActivated()` 前置；`:400-410` fresh record（null 首态）；`:512` join-wait 全上游 history 判定 + 调用方 isWaiting；`:789` checkWaitingStep isWaiting + 信号 + 子流程状态判定；`:836` runStepAutoTransition `!step.isActivated() → false`（:1009）+ EXECUTED 防重执行源守卫；`:1017` moveStepToWaiting `!step.isHistory()`（:1015）；`:1373` joinType==and 门控；`:1414` doExitStep `!step.isHistory()`（:1411）；`:1431` enterWaiting `!step.isHistory()`（:1430）；`:1716` activateSeqStep 方法体无守卫，但唯一调用方 `ExecGroupSupport.activateNextSeqStep:155` 以 `member.isWaiting()` 过滤（WAITING=10 为前向激活）——全部闭合。
- **反向核对（守卫不阻断合法引擎流）**：驳回/撤回对已终态步骤的"重做"需求由**新建实例模式**规避而非状态回退——`doRejectStep:1378-1387` 对 history 目标经 `newStepForActor` 新建实例；`doWithdraw:1389-1408` 对 history next-step fail-fast `ERR_WF_WITHDRAW_ACTION_IS_NOT_ALLOWED`（:1392-1393）+ 对非 history 经 `doExitStep(WITHDRAWN=100)`（≥40 前向，`_NopWfCoreConstants.java:64` 数值核实）。fail-fast 守卫无合法流阻断。
- wf 级（对照面）：`WfRuntime.saveWfRecord:317-323` = 唯一 wf 级驱动（`transitToStatus` + store 落盘，零守卫零监视器），6 调用点本轮逐一复核：`initCreateStatus:148`（CREATED，`status==null||<=CREATED` 条件）、`saveStarted:470`（ACTIVATED）、`suspend:659`、`resume:682`、`doEndWorkflow:1615`（← `kill:732` 与 `checkEnd:1592`，doEndWorkflow 自带会话内 `wf.isEnded() → return` 重检 :1599-1600）→ 零守卫实现 `NopWfInstance.java:36-51` / `WorkflowRecordBean.java:115-117` = C1-010 已立案，维持原判，无新同族成员。

**结论**：C1-010 的同族扩展核实完毕——step 级两处 fail-fast 实现已覆盖全部 14 个 step 状态写者；守卫缺口精确收敛于 wf 级 2 处实现（C1-010 原判），修复面无需扩大。

### 2.4 收口轮残余边界观察（不立案，移交对应维度）

1. **挂起任务被 timeout-cancel 记 KILLED 而非 TIMEOUT（确定性语义面，非竞态）**：`TaskRuntimeImpl.cancel:97-105` 对 SUSPENDED 任务无条件写 KILLED(70) + `ERR_TASK_CANCELLED`，不读 cancel reason——整体超时驱动（`TeamTaskFlowOrchestrator.java:655` 传 `CANCEL_REASON_TIMEOUT`）命中挂起任务时，状态码分类与 ACTIVE 任务的 reason 分类路径（`TaskImpl.driveTaskTerminal:218-229` 按 reason 分流 TIMEOUT/KILLED）不一致。该行为由注释声明的简化设计（"直接驱动 KILLED 终态并落盘"）覆盖，非并发缺陷，归状态机语义维度；若按 C1-009 建议(3) 为 `setTaskStatus` 补终态拦截，建议同批裁定此分类。
2. **双重 resume 面**（2.2 面 2）：根因已由 C1-002/C1-006 承载，仅登记场景归属；其"业务副作用双执行"语义面若需独立边界声明，归 owner doc 漂移登记（同第 1 轮 `nop-wf.md` 零并发声明的漂移项）。

## 3. 终止声明

第 3 轮收口任务全部执行完毕：任务 1（task 状态机转移守卫三元矩阵穷尽，含第 2 轮"6+2 写点矩阵"清单的独立复核——**复核成立，无第七写点**）；任务 2（resume 入口矩阵 1+1 收敛、执行期 6 类并发转移面逐面闭合）；任务 3（wf step 级 fail-fast 守卫对全部 14 个 step 状态写者全覆盖核实 + 守卫不阻断合法流反向核对）。三项任务均未产生新的可立案缺陷，第 1 轮 C1-001~008 与第 2 轮 C1-009/010 的登记面经独立复核全部维持原判，失效面无遗漏成员。

**未发现新的高价值问题。C1 维度深挖终止。**

（终止限定说明：本终止声明针对 C1 共享可变状态与可见性维度在本审计单元内的收口轮；C1-009/C1-010 修复若引入新同步结构，其锁序与协议完备性归 C2 锁与协议维度复核；2.4 节两项残余观察分别移交状态机语义维度与 owner doc 维护项，不构成 C1 维度重开条件。）

## 4. 覆盖声明

- **序号接续**：本轮新增发现 **0 项**（无 C1-011）；C1 维度累计 10 项 = 第 1 轮 C1-001~008 + 第 2 轮 C1-009/010（P1×2 = C1-001、C1-009；P2×7 = C1-002/003/004/005/006/007/010；P3×1 = C1-008）。
- **收口指令对照**：指令 3 项任务 → 执行记录见第 2 节 2.1（三元矩阵表 + step 级写点表）、2.2（入口矩阵 + 6 面穷尽）、2.3（14 写点全覆盖 + 反向核对）；2.4 为执行中产生的残余观察（不立案，显式列出防丢失）。
- **方法**：独立 grep（`setTaskStatus(`/`setStepStatus(`/`transitToStatus(`/`setStatus(`/`TASK_STATUS_WAITING`/`getTaskRuntime(`/`new TaskRuntimeImpl(`/`resume`/`WF_STEP_STATUS_HISTORY_BOUND`/`state.succeed|fail`，全部全仓 main 代码口径）+ 逐行深读（TaskImpl、TaskRuntimeImpl、TaskFlowManagerImpl、TaskStateBean、TaskStepStateBean、AbstractTaskStateCommon、DefaultTaskStateStore、DaoTaskStateStore 全文、TaskStepExecution 全文、TaskStepHelper 全文、TaskStepRuntimeImpl、CallTaskStep、SuspendTaskStep、DelayTaskStep、TaskConstants、ITaskState、_NopWfCoreConstants、NopWfCoreConstants、NopWfStepInstance、NopWfInstance 摘要、WorkflowStepRecordBean、WorkflowRecordBean 摘要、DaoWorkflowStore 摘要、WorkflowEngineImpl 转移驱动段 :130-244/:292-424/:636-762/:1085-1148/:1330-1454/:1578-1666/:1708-1725、WfRuntime.saveWfRecord、ExecGroupSupport.activateNextSeqStep）+ 跨单元触发链只读核对（TeamTaskFlowOrchestrator :587/:608/:655）。
- **误报校准执行**：无状态单例并发调用（BizModel CRUD、DefaultTaskStateStore no-op store）、构造期单线程写者（load/toTaskStateBean/newTaskState/newWfRecord/newStepRecord）、有界声明结构（64 槽条带锁）、平台设施自身实现（Cancellable/CHM/future 链 HB/ORM version 列）、grep 同名方法噪音（PlanExecutionState.setTaskStatus）、已声明设计简化（cancel 驱动 KILLED 硬编码——转 2.4 残余观察）均按口径排除。
- **盲审纪律遵守**：本轮仅读取第 1 轮 C1-shared-mutable-state.md 与第 2 轮 C1-round2.md 两个审计目录文件；未读取 ai-dev/plans、ai-dev/bugs、ai-dev/lessons 及其他维度审计文件；未修改前轮文件。
- **独立性声明**：第 2 轮"写点矩阵 6+2 完全一致"结论经本轮独立 grep（非沿用）复核成立；第 2 轮终止声明经本轮三项收口任务补足穷尽性证据后维持。
