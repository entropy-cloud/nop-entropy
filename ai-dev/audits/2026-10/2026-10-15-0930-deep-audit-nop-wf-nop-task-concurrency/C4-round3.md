# C4 线程池与执行器治理维度 — nop-wf + nop-task 第 3 轮收口（盲审）

- 审计单元：`nop-wf`（10 jar）+ `nop-task`（10 jar），合计 379 files / 36,107 行
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15；维度：C4 线程池与执行器治理（第 3 轮收口轮，范围：globalTimer 业务体调度站点穷尽 + C4-002 同池嵌套面闭合）
- 盲审纪律：未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件（前序轮次文件 `C4-thread-pools.md`、`C4-round2.md` 除外）；未读取其他维度审计输出。全部结论基于 live code。
- 输入：第 1 轮 C4-001~006、第 2 轮 C4-007~009（均已完整读取）；`_tmp/cra-baseline-nop-wf-nop-task.txt`（两簇 C4 节为空，前轮已核，本轮 grep 复核一致）。
- 本轮为收口轮：按 campaign 继续条件（前轮含 P1 必派下一轮）执行既定收口范围后终止。编号从 C4-010 起——**本轮未产生新编号**（见第 1 节）。

---

## 1. 本轮发现

**无。本轮按收口范围完成两项定向穷尽核查，未发现新的高价值问题，未新立案（C4-010 起编号空缺）。**

两项收口核查的结论先行：

1. **globalTimer 业务体调度站点穷尽（闭合）**：全单元（wf 10 jar + task 10 jar，main 源码 + 非 `_` 生成资源）`schedule/scheduleAtFixedRate/scheduleWithFixedDelay` 调用点合计 **3 处**，全部位于 nop-task-core；nop-wf 全簇 Java 与 XML/yaml 资源**零命中**。`getScheduledExecutor()`（唯一 globalTimer 入口，TaskFlowManagerImpl.java:77-81 恒回退 `GlobalExecutors.globalTimer()`）的全部消费方恰为这 3 处（DelayTaskStep.java:39、TimeoutTaskStepWrapper.java:25、TaskStepHelper.java:269），获取方与调度站点 1:1 闭合，**不存在第 4 类站点**。执行体矩阵：2 处轻量簿记 + 1 处业务体，唯一业务体站点即已立案的 C4-001/C4-008（详见第 2 节 #1）。
2. **C4-002 同池嵌套面闭合（闭合）**：全单元阻塞等待面（`syncGet|.sync()|blockingGet|awaitDone|timed get`）逐站点核对完毕——除 SyncTaskStepWrapper（C4-002 主体）外，其余全部 `sync()` 调用点均有 `isDone()/isFutureDone()` 前置守卫（done future 上 syncGet 立即返回，非阻塞）；仅有的两个无守卫新阻塞点（nop-task-ext 事务/会话 decorator 的 sync 路径）复用与 C4-002 完全相同的触发标志 `stepModel.isSync()`，不构成新触发配置，且其"连接跨异步窗口持有"面为 decorator 两种模式共同的设计语义（owner doc 已声明），降级为登记项。WF 侧 `FutureHelper.syncGet` 两处（W2/W3）均为前轮已裁定的边界/已立案项，无新嵌套形态（详见第 2 节 #2-#5）。

---

## 2. 排除项/定向核查

### #1 globalTimer 调度站点穷举矩阵（收口任务 1，闭合）

全量 grep（`\.schedule\(|scheduleAtFixedRate|scheduleWithFixedDelay` over `nop-wf/ nop-task/` main 源码；`GlobalExecutors\.|globalTimer\(\)` 复核）+ `getScheduledExecutor()` 消费方反查，结果矩阵：

| # | 站点 | 获取方 | 调度体内容 | 体类型 | 撤单/清理 | 对账 | 判定 |
|---|------|--------|-----------|--------|----------|------|------|
| S1 | `DelayTaskStep.java:41-43` | `getScheduledExecutor()`（:39） | `() -> { return null; }`——纯占位，无任何业务动作 | 轻量簿记 | `bindCancelToken(cancelToken, future)`（:47）取消即撤单 | 第 1 轮 T6（通过）；C3-013 的"delay 续延"面 | 通过（本维度正例：无业务体、撤单对称） |
| S2 | `TaskStepHelper.java:199`（经 `TimeoutTaskStepWrapper.java:25` 获取） | `getScheduledExecutor()` | `cancellable.cancel(reason)` → 内建消费者仅 `completeExceptionally` / `future.cancel(false)`（第 2 轮排除项 #2 已核：Cancellable.java:52-60,113-118） | 轻量簿记 | 同步/异步/异常三路径 `timer.cancel(false)`（:240-242） | 第 1 轮 T4（通过）；第 2 轮排除项 #2 | 通过 |
| S3 | `TaskStepHelper.java:268-270`（retry 延迟分支） | `getScheduledExecutor()` | `action` = 被包装步骤的完整 execute 体（RetryTaskStepWrapper.java:28-30）+ 完成回调链上 `doRetry`→`saveState` ORM 写 | **业务体（body + ORM 写）** | 无撤单绑定（C4-009 已立案） | **C4-001/C4-008（P1 主体+失效面扩展）**；C3-013 同池面 | 已立案，维持 |

无第 4 类站点的反向证据：`getScheduledExecutor()` 在 `ITaskRuntime.java:136` 声明、`TaskRuntimeImpl.java:208-209` 单点委托 `taskManager.getScheduledExecutor()`、`TaskFlowManagerImpl.java:77-81` 单点回退 globalTimer——调用链两端闭合，除 S1/S2/S3 外无其他消费方；`scheduleAtFixedRate/scheduleWithFixedDelay` 两簇零命中（无周期调度面）；两簇 main 源码零直接建池（`new Timer|new ScheduledThreadPoolExecutor|newScheduledThreadPool|ExecutorHelper.|DefaultThreadPoolExecutor|DefaultScheduledExecutor` 零命中，与第 1 轮"wf 簇不自建池"结论一致并扩展至 task 簇）；两簇非生成资源（XML/yaml/xlib）中调度相关引用仅 wf-scheduler 的既有 job.yaml + `nopWfTaskScanner` bean（C4-003/C4-007 已立案）与 nop-task 测试资源注释，无新调度面。**收口任务 1 闭合：唯一业务体站点 S3 已被 C4-001/C4-008 覆盖，与 C3-013 对账无遗漏。**

### #2 全单元阻塞等待面逐站点核对（收口任务 2 主体，闭合）

全量 grep（`syncGet|trySyncGet|\.sync\(\)|blockingGet|awaitDone|timed get`）两簇 main 源码，逐站点分类：

**A 组：done 守卫型（已验证非阻塞，排除）**——以下每个站点的 syncGet/sync 均被 `isDone()`/`isFutureDone()` 前置守卫，done future 上 `FutureHelper.syncGet` 立即返回，不产生阻塞窗口：

| 站点 | 守卫 |
|------|------|
| `TaskStepReturn.java:72-73`（ASYNC 工厂） | `FutureHelper.isFutureDone(future)` |
| `TaskStepReturn.java:276-277`、`:291-292`（TaskStepHelper.retry 回调） | `result.isAsync() && result.isDone()` / `result.isDone()` |
| `LoopTaskStep.java:152-153`、`LoopNTaskStep.java:190-191` | `stepResult.isDone()` |
| `ExecutorTaskStepWrapper.java:37-38` | `result.isDone()`（池线程仅桥接 done 结果，非阻塞） |
| `ParallelTaskStep.java:95-96`、`AbstractForkTaskStep.java:125-126`（`StepResultBean.buildFrom` 内含无守卫 syncGet，但调用前有守卫） | `FutureHelper.isFutureDone(future)`，未完成分支走占位 ErrorBean（:100-102/:128-132） |
| `GraphTaskStep.java:311` | `buildFromResult`（同步变体，无 future 等待） |

**B 组：无守卫阻塞点全集**——`SyncTaskStepWrapper.java:17`（C4-002 主体）、`TransactionalTaskStepWrapper.java:30`、`OrmSessionTaskStepWrapper.java:26/:30`、`WorkflowServiceImpl.java:230`（W2）、`WfTaskScanner.java:96`（C4-007）、`TaskStepReturn.syncGetResult/syncGetOutputs`（:173-180，调用方全在本单元外：nop-cli 命令线程——同步 CLI 设计使然；nop-ai-agent 编排器——单元外）。其中 W2（缺省内联 executor 下即时返回，第 1 轮边界 #7 维持）、WfTaskScanner（C4-007 已立案）复核无新形态；**wf 簇内不存在"异步回调内部再 syncGet"的嵌套形态**（W2 的 syncGet 在 GraphQL 调用线程顶层，W3 在 job 调度线程顶层，均非 promise 回调内）。

### #3 nop-task-ext 事务/会话 decorator sync 路径（第 1/2 轮未盘点的第 2、3 个无守卫阻塞点——降级登记，不立案）

第 1 轮 T1-T10 盘点未含 nop-task-ext 的两个 wrapper。本轮补全核对：

- **结构位置**：decorator 经 `TaskStepEnhancer.wrap:108`（decorateStep 最先 = 最内层）进入，位于 executor 包装（:118-120）**内侧**——即配置 `executor` 时，事务/会话 sync 阻塞发生在**池线程**上（链序：池线程执行 try→output→retry→**decorator(sync)**→raw）。
- **触发标志同源**：`TransactionTaskStepDecorator.java:28` 与 `OrmSessionTaskStepDecorator.java:24` 均传 `stepModel.isSync()` 作为 sync 标志——与 `SyncTaskStepWrapper`（`TaskStepEnhancer.wrap:159-160`，同一标志）**共用同一触发配置**。sync=true 时两个阻塞点同时安装，阻塞时长由同一 inner 链决定：**不存在"经 ext wrapper 阻塞而 SyncTaskStepWrapper 不阻塞"的配置**，任何同池嵌套死锁拓扑均已被 C4-002 的触发面覆盖，无新触发路径。
- **连接跨窗持有面的归属裁定**：`runInTransactionAsync`（sync=false 路径，TransactionTemplateImpl.java:185-224）同样跨异步窗口持有事务直至 promise 完成后 commit/rollback（:195-222）——"DB 事务/连接跨异步窗口（含 retry-delay 长等待、同池排队等待）持有"是 decorator **两种模式的共同设计语义**，非 sync=true 新增面；owner doc `nop-task.md:69` 已显式声明"使用 `TransactionTaskStepDecorator` 并接受该窗口"。该面是否构成连接池耗尽风险属 C1/C6/事务语义裁定域，本维度不立案、不移交立案（声明已覆盖）。
- **登记价值**：若 C4-002 后续修复（如 syncGet 带时限或同池嵌套 fail-fast），修复点应覆盖 TransactionalTaskStepWrapper:30 与 OrmSessionTaskStepWrapper:26/:30 两个同族阻塞点（仅修 SyncTaskStepWrapper 不完整）——作为修复方向补全并入 C4-002 字段10 的交付物，非独立缺陷。

### #4 WF 侧 FutureHelper.syncGet 嵌套复核（收口任务 2 指定样例，闭合）

`WorkflowServiceImpl.transferActorsAsync`（:227-249）：`syncGet(workflowExecutor.execute(...))` 在 GraphQL 调用线程逐项等待；缺省 `DefaultWorkflowExecutor` 为调用线程内联执行（第 1 轮 W1/W2 已核），syncGet 即时返回；部署方替换异步 executor 时为有界顺序阻塞（第 1 轮边界 #7 维持，非同池嵌套——被等待体不在调用线程所在池上，除非部署方将 IWorkflowExecutor 绑定到调用方所属池，该自定义形态属部署域无代码可达路径）。`WfTaskScanner.java:96`：被等待的 `invokeActionAsync` 经 BeanMethodJobInvoker 在**同一 job 调度线程内联执行**（第 2 轮已核：:113-127/179-189），syncGet 实际不跨线程等待（其风险面已由 C4-007 全覆盖）。两处均无"有界池线程阻塞等待同池任务"形态。

### #5 "submit 后同线程阻塞"形态反查（闭合）

ExecutorTaskStepWrapper 提交后立即返回 ASYNC（不阻塞提交线程）；三个 schedule 站点均返回 future 不阻塞；`FutureHelper.bindCancelToken`/`whenComplete` 桥接均非阻塞。单元内**不存在**"向有界池提交任务后在同一线程 syncGet 等待该池任务"的第四种同池嵌套变体——同池嵌套的唯一入口是有界池线程链上的无守卫阻塞点（B 组前 3 项），已全部闭合（#2/#3）。

### #6 前轮结论对账（维持项清单）

C4-001/002/003/004/005/006/007/008/009 全部维持前轮立案结论与严重度；第 2 轮排除项 #1-#10 的核对结论（C4-003 失效形态修正、timeout 回调链、sync+runOnContext 活性、包装序矩阵、F8 关闭路径、延迟续延进程本地性、可观测性校准、线程命名矩阵、job.yaml 配置面、wf 簇零直接调度引用）经本轮相关 grep 交叉复核无一被推翻。第 1 轮残留边界 #1-#7 维持（含"globalTimer 实际 4 线程"的 C4-001 影响评估依据）。

**F3 失效面枚举（禁留空）**：沿用前轮 4 项，本轮无新增、无撤销——C4-003（wf 扫描 job 装配死面）、C4-005（nopTaskExecutionQueue 死 bean）、C4-006（ITaskQueue 死接口）、C4-007（扫描链路一次失败/挂死即永久停摆的韧性失效面）。本轮 #1 矩阵反向证实调度面无第 4 个 F3 候选。

---

## 3. 终止声明

第 3 轮收口按指令完成既定范围：①globalTimer 业务体调度站点穷尽——3 站点全数定位并矩阵化（2 簿记 + 1 业务体），获取方与站点 1:1 闭合，唯一业务体站点已被 C4-001/C4-008 立案，与 C3-013 对账无遗漏，无第 4 类站点；②C4-002 同池嵌套面闭合——全单元阻塞等待面逐站点核对，其余阻塞点或为 done 守卫非阻塞、或为已立案/已声明边界、或与 C4-002 共用同一触发标志（无新触发路径），WF 侧无嵌套形态。

**未发现新的高价值问题。C4 维度深挖终止。**

C4 维度最终账目：C4-001（P1）、C4-002（P1）、C4-008（P1，扩展案）、C4-003/004/007（P2）、C4-005/006/009（P3），共 9 项；无 C4-010 及以后编号。后续若代码变更引入新调度站点或新阻塞原语，按新审计任务重启，不在本 campaign 延续。

## 4. 覆盖声明

- **本轮新增阅读面**：全单元 `schedule*/getScheduledExecutor/GlobalExecutors/globalTimer/建池原语` 三组全量 grep 及命中文件精读（DelayTaskStep 全文、TaskStepHelper.java:240-310、TimeoutTaskStepWrapper 定位行、TaskFlowManagerImpl 定位行、ITaskRuntime/TaskRuntimeImpl 定位行）；全单元 `syncGet|.sync()|blockingGet|awaitDone|timed get` 全量 grep 及全部命中站点上下文精读（TaskStepReturn.java:55-124、StepResultBean 全文、ParallelTaskStep.java:60-125、AbstractForkTaskStep.java:90-156、LoopTaskStep.java:95-170、LoopNTaskStep.java:160-205、ExecutorTaskStepWrapper 全文）；TaskStepEnhancer.wrap 全文（包装序与 decorator 位置复核）；nop-task-ext 两个 decorator 与两个 wrapper 全文 + task-ext.beans.xml 装配；TransactionTemplateImpl.java:150-264（sync/async 事务路径）；owner doc `nop-task.md` decorator/事务相关条目；两簇非生成资源调度引用 grep（XML/yaml/xlib）。
- **对账完成**：与 C3-013 对账（S3 即其池治理面，无遗漏站点）；与 C4-001/008 对账（S3 已立案，无重复）；与 C4-002 对账（ext sync 站点同触发闭合，修复面补全建议并入其字段10）；与前两轮全部 9 项发现、W1-W4/T1-T10 使用点清单、残留边界 #1-#7、第 2 轮排除项 #1-#10 对账——全部维持，无推翻。
- **边界声明**：GlobalExecutors/ExecutorHelper 平台内部实现、nop-job-local 宿主语义、ITransactionTemplate 事务语义按误报校准口径仅作裁定依据引用（立案对象均为本单元使用面）；nop-task-ext 的"事务跨异步窗口持有连接"面属 C1/C6/事务维度裁定域（owner doc 已声明"接受该窗口"，本维度仅登记）；C6/C9 面以对应维度审计为准。
- **方法声明**：本轮全部结论来自对 live code 的穷举 grep + 命中站点逐行精读；"无第 4 类站点""无其他阻塞形态"为反向证据闭合（接口消费方反查 + 全量 grep 零剩余命中），非抽样推断。
