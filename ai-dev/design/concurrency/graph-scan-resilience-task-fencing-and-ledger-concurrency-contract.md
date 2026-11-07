# 图扫描韧性、task 状态 fencing 与账本并发契约（R4-09 批十三工单裁定 A–E）

> Status: active
> Last Reviewed: 2026-11-08（plan 2542 Phase 1 产出；本批唯一设计裁决权威——[plan 2542](../../plans/2542-cra-phase8.49-r4-09-graph-scan-infra.md)「工单→裁定映射与分组登记」以本文第一节为分组权威载体）
> Source: plan 2542（CRA 批次 8.49 R4-09，13 工单全 P2 独立立案——merge-adjudication §2.2 表十三行）Current Baseline 十三工单表+对齐纪律显式条款+Baseline 勘误 1–12；发现事实源 = 1.5 单元审计目录 `ai-dev/audits/2026-10/2026-10-15-0930-deep-audit-nop-wf-nop-task-concurrency/`（C1-006/C3-003/C3-004/C4-003/C4-004/C4-007/C5-005/C5-006/C7-004/C7-007/C7-009/C7-010/C7-014 十三条目 live 亲读 + merge-adjudication §4.3 C10 映射 + §4.4 跨单元对账 + §五 R1-4/R2 批次建议）；行号 = live `62be12c478`（分支 `concurrency-audit-remediation`）2026-11-08 亲读回读值，执行期漂移以 live 为准并登记于 plan 勘误
> 实现锚（live 亲读）：nop-task-core `step/GraphTaskStep`（:202-224 wait 调度循环 / :228-234 enter 直跑循环 / :248 runStep def 无一次性守卫——仅 :255 取消早退与 :263 errorTriggerFired 检查 / :337 stepFuture.complete 级联 / :445-447 initFutures 已完成步骤映射 completedFuture）、`builder/GraphStepAnalyzer`（:46 initDag 校验集=重名/enter 非空/exit 非空/未知步骤/含环五分支，无 enter∩wait 拦截 / :87 normalizeWaitSteps / :88 addDataDepends / :93 normalizeWaitSteps 由 next 自动派生 waitStep :95-97）、`builder/GraphStepBuilder`（:19-22 enter/exit 标记）、`step/ExecutorTaskStepWrapper`（:33 executor.submit 池线程直跑 body 无 context 重投递 / :48 catch(Throwable) 2541 W-06 产物 / :57-58 Error 原样 rethrow / :69-74 onCancel 兜底 / :76 bindCancelToken）、`step/RunOnContextTaskStepWrapper`（:30-33 `stepRt.getContext()` 捕获+`context.execute(() -> body)`——W-03 包裹同构先例）、`impl/TaskFlowManagerImpl`（:131-137 getThreadPoolExecutor——:132 GlobalExecutors 保留名优先 / :134 无 instanceof 强转 / 2541 治理产物 :68-113 注册表+gauge+逐出零触碰锚 / :168-182 getTaskRuntime 每次 new TaskRuntimeImpl :170 无在途标记 / :222-294 getRateLimiter/getSemaphore 注册表面）、`impl/TaskImpl`（:98 resume 终态短路 / :122-130 resume ACTIVE 重置监视器内 saveTaskState :127 / :176-185 SUSPENDED 转移 :184 / :211-218/:227-234/:262-269/:277-284 四终态 driver saveTaskState :216/:232/:267/:282 / :291 skipTerminalOverwrite def——六处 synchronized(taskState) 内调 saveTaskState 亲证）、`step/CallTaskStep`（:90 getTaskRuntime 子实例二次入口 / :102-103 2541 throttle inherited-keys 产物零触碰锚）、`ITaskRuntime`（:45-51 getLocale svcCtx==null 回退 ThreadLocal ContextProvider.currentLocale）、`step/SuspendTaskStep`（:28-43 execute——:36-42 resumeWhen 仅 resume 调用链求值）、`step/LoopTaskStep`（:124-127 toList 物化入 LoopStateBean / :155-157 同步每迭代 incIndex+saveState / :169-171 异步 / :194 maxCount 截断既有半面 / :89-116 LoopStateBean items+index）、`step/ForkTaskStep`（:51-59 toList 物化入 ForkStateBean）；nop-task-dao `store/DaoTaskStateStore`（:132-143 newTaskState / :159 saveTaskState def 无锁直写 / :169 task 级 beforeSave 钩子 / :257-260 saveEntityDirectly/updateEntityDirectly / :339-352 64 槽条带锁 [05-02] / :355-374 saveStepState 条带锁内 :365 调 state.beforeSave / :452/:505 workerId round-trip 无生产赋值点 / :560-599 serializeStepStateData——:574 wrapper 含全量 stateBean、:579-580 判长在物化之后）；nop-wf-core `store/AbstractWorkflowStore`（:32 `protected abstract getSteps` 缝 / :51-55 getLatestStepRecordByName 全集排序 / :58-70 getStepRecords 内存过滤 / :108-133 getJoinWaitStepRecords 全集扫描 / :183-197 getNextJoinStepRecord 全集扫描）、`engine/DefaultWorkflowExecutor`（:52 maxLoops=10_000 / :56 ERR_WF_AUTO_TRANSITION_EXCEED_LIMIT——plan 344 产物零触碰）、`engine/WorkflowEngineImpl`（:917-930 runAutoTransitions——:922 wf.getActivatedSteps() 每轮全集；零触碰锚 runSourceWithRetry/computeRetryDelay 2541 W-12 产物）；`impl/WorkflowImpl`（:232-234 getActivatedSteps 经 getStepRecords(record, false, isActivated)——includeHistory=false 腿亲证）；nop-wf-dao `store/DaoWorkflowStore`（:88-95 withWfLock 2514 MA-02 / :208-249 transfer 查询族 2541 产物 / :251-262 hasOpenedTransaction 2541 W-12 / :265-317 due/remind 分批游标 2514 MA-05 / :323-348 scan lease fencing 2514 MA-23 / :350-383 直发 UPDATE 族 / :494 getSteps 直返 `((NopWfInstance) wfRecord).getSteps()` 全集）、`entity/NopWfInstance`（:36-51 transitToStatus 每迁移 append NopWfStatusHistory）；nop-wf-scheduler `WfTaskScanner`（:179-184/:186-191 scanDueTasks/scanRemindTasks 入口异常外泄面 / :197-233 due 主循环——:207-208 每轮 gauge 重置 / :240-300 scanDueTask——:244 fencing / :274 `FutureHelper.syncGet(workflowService.invokeActionAsync(...))` 单参无 deadline / :306-325 postponeFailedDueTask 退避 / :333-366 remind 主循环 / :407-424 remind 退避 / :132-142 registerScanGauges）；`wf-due-task-scan.job.yaml:8`/`wf-remind-task-scan.job.yaml:8` `invoker.bean: wfTaskScanner`（未修亲证）× `app-scheduler.beans.xml:9` `id="nopWfTaskScanner"`；平台 API 亲证：`IBeanProvider.containsBean(String)`（:19）、`FutureHelper.syncGet` 单参无超时重载（:158，`waitResult(Future,timeout)` :262 仅 java.util.concurrent.Future）、`WfSchedulerConfigs`（scan-batch-size=500/scan-time-budget-ms=30000/scan-lease-ms=120000/node-id——`<=0=禁用`约定 :200 亲证）、`TaskConfigs`（nop.task.* 键持有类）、nop-wf-scheduler pom :26 已依赖 nop-job-local、`nop-task/model/nop-task.orm.xml` :222/:308 workerId 列在位（零 DDL）、`TaskErrors` 码族 `nop.err.task.*`
> Related：零重开边界权威 = [wf-task-scanner-fencing-and-task-wf-concurrency.md](wf-task-scanner-fencing-and-task-wf-concurrency.md)（plan 2514 裁定①–⑦：§3.2 MA-01 监视器收敛锁序声明 / §六 :173 C7-014 分立声明承接指向——本批兑现）；同单元前批权威 = [task-gate-throttle-error-boundary-and-lifecycle-concurrency-contract.md](task-gate-throttle-error-boundary-and-lifecycle-concurrency-contract.md)（plan 2541 裁定 A–F：W-06 Throwable 收宽=W-03 同方法邻接 / 键空间治理=W-05 独立分支 / 扫描治理=W-06/W-09 diff 分组 / retry 封顶=W-08 独立分支）；延迟重试载体裁定 = plan 2498 §3.1.1+MA-04（timer 只触发+载体执行——引用不重建）；调度槽纪律 = plan 2497 §七；历史裁定 = plan 364 [02-06][05-01][05-02] / plan 349 / plan 344（引用不重建）；owner doc = `docs-for-ai/03-modules/nop-task.md` / `docs-for-ai/03-modules/nop-wf.md`

## 一、工单→裁定映射与分组登记（分组权威）

本节为 plan 2542 裁定分组的**唯一权威**。plan Goals 节建议的 A–E 分组经 live 亲读核对后**采纳并落一处入组登记**：W-05 按其站点（TaskFlowManagerImpl.getThreadPoolExecutor 解析链）与缺陷族（解析治理）入裁定 C（图执行/上下文/解析链域）——R1-M1 修订登记（Phase 2 实施项枚举补 W-05 载体），分组与文件命名以本节为准。

| 裁定 | 工单（finding ID） | 域 | 站点（live 行号） |
|------|-------------------|----|------------------|
| A | W-04（1.5-C4-003；C10-010 宿主）/ W-06（1.5-C4-007；C10-012 宿主）/ W-09（1.5-C7-004） | 扫描 job 韧性域（merge-adjudication §五 R1-4 批次层面兑现——三工单一次设计） | 两 job.yaml / WfTaskScanner（:179-191/:274——diff 分组禁触 2514/2541 产物）/ DaoWorkflowStore 查询族零触碰 |
| B | W-01（1.5-C1-006）/ W-11（1.5-C7-009）/ W-13（1.5-C7-014；C10-014 宿主） | task 状态与 fencing 域（§五 R2 批次层面兑现——三工单一次设计，workerId/租约基建共享） | DaoTaskStateStore / TaskImpl / TaskFlowManagerImpl / CallTaskStep :90 传播面 |
| C | W-02（1.5-C3-003）/ W-03（1.5-C3-004）/ W-05（1.5-C4-004） | 图执行/上下文/解析链域（W-05 入组=R1-M1 登记） | GraphTaskStep / GraphStepAnalyzer / GraphStepBuilder / ExecutorTaskStepWrapper / TaskFlowManagerImpl（getThreadPoolExecutor 独立分支）/ ITaskRuntime javadoc 候选 |
| D | W-07（1.5-C5-005）/ W-08（1.5-C5-006） | 边界与规模域 | LoopTaskStep / ForkTaskStep（DaoTaskStateStore 序列化面零 diff）/ DaoWorkflowStore / AbstractWorkflowStore / WorkflowEngineImpl runAutoTransitions 分支 |
| E | W-10（1.5-C7-007）/ W-12（1.5-C7-010） | 账本与滞留观测域 | owner doc（W-10）/ DaoTaskStateStore gauge 注册（W-12）/ SuspendTaskStep 零触碰 |
| E 附 | 红①–红⑬ 驱动器总表 + 零回退钉全列 + C10 关联面注记 | 测试缝 | 测试树（`*R409` 后缀+类注释归属 CRA 8.49 R4-09） |

**W-10×DaoWorkflowStore 新增方法 diff 分组登记（R1 观察项闭合）**：裁定 E 取 DBA 保留策略声明形态——**DaoWorkflowStore 零新增方法**，该观察项以零触发闭合（若 Phase 2 实施中任何 W-10 相关代码需触 DaoWorkflowStore 写面即越裁，禁选）。

### 实施次序矩阵（组间依赖与共享站点联调）

| 次序 | 组 | 内容 | 依赖/并行关系 |
|------|----|------|--------------|
| 1 | 组 A | W-04 yaml 改名+C10-010 → W-06 异常边界+gauge → W-09 deadline（同站点同簇一次落地） | 首组启动；W-04 先行（一行级低风险+装配钉立即可红转绿） |
| 2 | 组 C | W-02 校验+守卫 → W-03 context 包裹 → W-05 解析链 | 与组 A 可并行（站点零交集）；W-03 依赖 2541 W-06 产物形态（已在位） |
| 3 | 组 B | **锁序评审落款先行**（监视器→条带锁单向论证+beforeSave 禁则成文）→ W-01 条带锁 → W-11 workerId+滞留告警 → W-13 fencing+C10-014 | 组 B 在组 A/C 之后启动非强依赖，而是评审先行纪律；W-11 先于 W-13（workerId 基建先落，fencing 不依赖其列） |
| 4 | 组 D | W-07 引擎层剥离 → W-08 读路径下推（可并行） | W-07 经裁定为引擎层剥离——DaoTaskStateStore 零 diff，原「W-07 在组 B 的 DaoTaskStateStore diff 后」次序约束**消解**；W-08 与组 B/C 无站点交集可并行 |
| 5 | 组 E | W-10 owner doc 声明（随时可落）→ W-12 gauge（DaoTaskStateStore 同文件在组 B diff 之后追加） | 收尾组：W-12 gauge 与组 B 同文件追加，静态守卫形态与锁面无交集，次序仅登记编译面 |

**三共享站点联调矩阵**：

| 共享站点 | 工单（组） | diff 分组纪律 | 联调点 |
|----------|-----------|--------------|--------|
| TaskFlowManagerImpl | W-05（组 C，getThreadPoolExecutor :131-137）× W-13（组 B，在途注册表字段+ITaskFlowManager fence 接口面） | 方法域互斥（:131-137 vs 新增字段/接口方法）——禁触 2541 getRateLimiter/getSemaphore 注册表面与逐出/gauge 语义 | 组 B 先落注册表字段与接口方法、组 C 落 instanceof 分支，同文件编译面一次收敛；closure audit git 过滤逐文件核对 |
| DaoTaskStateStore | W-01/W-11（组 B）× W-12 gauge（组 E）；W-07 零 diff（组 D 消解） | W-01 锁面（:159-261）与 W-11 赋值/告警（:132-143/:146-156）与 W-12 静态 gauge 载体三类互不嵌套；gauge 为类级静态 AtomicLong 载体（WfTaskScanner :71-73 同构） | 组 B 先、组 E 追加；红⑪/红⑫ 断言同文件但独立成腿 |
| WfTaskScanner | W-06/W-09（同组 A） | 单组内一次落地——:274 deadline 与入口异常边界同簇；退避常量/failCounts/dueTime 推进/fencing/gauge 注册逻辑（2514/2541 产物）零改写 | 无跨组联调 |

## 二、既有裁定引用核对（起草期义务——引用不重建）

1. **2514 §六 C7-014 分立声明兑现核对**：2514 设计文档 §六（:173）明文 R4-09 承接「C7-014 分立声明」就绪注记——本裁定 B 以「与 C1-009/MA-01 分立裁定零重开」落款兑现（merge-adjudication §3-B：C7-014=无执行级互斥，C1-009=SUSPENDED 写者无守卫——根因不同）。本批立 C7-014 本体（执行级 fencing），2514 裁定② 监视器收敛产物零重开零弱化；C1-009 本体（写者守卫）已由 2514 修复，本批不触碰其面。
2. **saveTaskState 链「修 MA-01 时不得回归」核对**：C1-006 cross-ref MA-01 同链约束——W-01 条带锁引入后 `TaskImpl` 六处 `synchronized(taskState)` 内调 `taskRt.saveTaskState`（:127/:184/:216/:232/:267/:282 亲证）产生「监视器→条带锁」嵌套序。锁序评审见裁定 B §4.1：当前无反向获取点（rowLock 临界区内仅 DAO helper+state.beforeSave 钩子），单向序成立；2514 §3.2 锁序声明（task taskState 监视器独立锁域）零弱化。
3. **MA-09 gauge 半面引用**：TaskFlowManagerImpl registry-size 两枚 gauge（:103-113，2514 产物）零触碰；MA-09 残余（stats 生产消费/分页聚合）归 G16→R-G，本批 W-06/W-12 gauge 为相邻增量非其本体。
4. **2541 W-06 Throwable×W-03 包裹相互作用推演落款**：2541 裁定 B 已落 ExecutorTaskStepWrapper.submit catch(Throwable)+Error rethrow+onCancel 兜底+bindCancelToken（:48-76 亲证）——W-03 context 重投递包裹为**同 submit 方法内叠加非替换**：包裹位于 try 块内首行，catch(Throwable) 边界随之外扩覆盖包裹开销，Error rethrow 语义不变（Error 从 body 穿透 context.execute → catch → 原样 rethrow），onCancel/bindCancelToken 作用于 ret/future 与 body 线程无关零弱化。逐点推演见裁定 C §5.2。
5. **W-05×2541 键空间治理独立分支落款**：getThreadPoolExecutor（:131-137）为独立解析分支，getRateLimiter/getSemaphore 注册表/逐出/frozen WARN/重入检测（2541 产物）零触碰零交叉——diff 分组禁交叉。
6. **W-06/W-09×2514/2541 扫描产物 diff 分组落款**：分批游标（:210-232/:344-365）/时间预算（:199-214） /failCounts 退避常量（:57-65）/fencing（:244/:377）/remind 退避（:407-424）/due-scan gauge（:132-142）零改写零重开——W-06(a) 异常边界与 W-09 deadline 为新增面。C4-007 审计「扫描集无界」半面已由 2514 MA-05 收敛（plan 勘误 3），本批不重复收敛。
7. **W-08×2541 W-12 retry 域独立分支落款**：WorkflowEngineImpl runAutoTransitions（:917-930）读路径为独立分支；runSourceWithRetry/computeRetryDelay/hasOpenedTransaction（2541 产物）零触碰；DaoWorkflowStore due/remind/transfer 查询族与 fencing 直发 UPDATE 族（2514/2541 产物）零改写——W-08 下推仅新增 getSteps 族分支。
8. **2498 MA-04 载体裁定对齐落款（W-09 如采异步形态的引用边界）**：本裁定 W-09 采**带超时等待分支**（非异步投递）——2498「timer 只触发+载体执行」载体契约**以不触发方式守约**（不重建第二套载体、不修改既有载体语义）；异步投递候选归 Non-Blocking Follow-up（plan 已登记），实施期如翻案须先补 2498 载体符合性核验。
9. **2497 §七调度槽纪律落款**：W-06/W-09 deadline 分支**不新增任何定时器面**；W-10 归档裁定为文档声明形态（无 job）；W-12 裁定为 gauge 形态（无轮询 job）——四工单均以「不触发」守约；若实施期确需新增定时器/job 面，须先补 2497 §七符合性核验（禁静默扩权）。
10. **364/349/344 产物零弱化落款**：364 [05-01] TaskImpl 终态 driver first-terminal-wins=**W-01 收敛语义的语义基准非删除**（skipTerminalOverwrite :291 逐字保留——本裁定在其上补跨 bean 对 DB 真值的收敛点）；364 [05-02] DaoTaskStateStore 条带锁=**W-01 复用形态基准非重写**（64 槽注释与实现零重写仅扩展 task 行锁入口）；364 [02-06] 校验链=W-02 同码族新分支落点邻接零回退；349 取消兜底（ExecutorTaskStepWrapper onCancel :69-74）=W-03 包裹的叠加基座零弱化；344 DefaultWorkflowExecutor 10k 上限（:52/:56）=W-08 立案基线对照零触碰。
11. **跨单元 nop-job-C7-006 承接登记**：C4-007 形态 A 宿主机制（FAILED 终态/resumeJob 只认 SUSPENDED/fireNow 拒绝 FAILED/cancelRunning 空实现）与 C4-003 字段10 后半（LocalJobConfigLoader 注册期 bean 存在性预校验）全部归 nop-job 单元工单 nop-job-C7-006（WI 1.4 open）——本批禁触 nop-job 全模块本体；C10-012 形态 A 保护归属该工单测试面（交叉引用登记不代做）。
12. **同案对齐落款（C4-003×P2-3.1-001）**：P2-3.1-001（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.1-wf-deep-audit.md` :46，AU-01≡SM-10）与本批 1.5-C4-003 同缺陷——**本批为唯一修复执行方**（merge-adjudication §4.4-5 一处修复语义）；台账载体=cra-index G15 束行（UP1：**唯一台账载体**，历史 audit 报告原文零回写）；修复后 Phase 4 落互引关闭注记。
13. **条件申报①②③④核对**：①（ORM 新列）预设零触发——W-11 心跳列否决（裁定 B §4.2 以 workerId 节点身份+读路径守卫替代，`nop-task/model/nop-task.orm.xml` :222/:308 workerId 列在位亲证零 DDL）、W-10 归档表否决（裁定 E §7.1 文档形态）；②（xdef 触碰）预设零触发——W-02 校验在 GraphStepAnalyzer Java 侧、W-03 无 opt-out 键、W-05/W-07/W-08/W-12 均零 xdef 面；③（平台载体上提）禁选——W-09 超时等待用 CompletionStage 既有 API、W-05 GlobalExecutors 只读、W-13 CHM 平台原语使用面；④（`_` 生成物/pom）零触发——两 job.yaml 为手工源配置（`_vfs` 资源根形态，plan 勘误 11），ITaskFlowManager/ITaskStore 接口新增=普通源码签名变更零申报（2541 R1-M2 先例）。

## 三、裁定 A：扫描 job 韧性域（W-04/W-06/W-09）

### 3.1 W-04 yaml invoker.bean 改名唯一分支（C10-010 宿主）

**唯一结论 = 两 job.yaml `invoker.bean` 改为 `nopWfTaskScanner`（各一行），装配测试 C10-010 同 diff。**

- `wf-due-task-scan.job.yaml:8` 与 `wf-remind-task-scan.job.yaml:8` `invoker.bean: wfTaskScanner` → `nopWfTaskScanner`（`method` 字段不变——scanDueTasks/scanRemindTasks 反射存在亲证）。两 yaml 为手工源配置（非生成物），普通源资源编辑零申报。
- **同案对齐一处修复语义落款**：本批为唯一修复执行方；修复后 Phase 4 在 cra-index P2-G15 束行落「1.5-C4-003 与 2026-09 P2-3.1-001 互引关闭」注记（唯一台账载体——UP1）；历史 audit 报告原文零回写。
- **LocalJobConfigLoader 注册期预校验=nop-job-local 宿主本体**——跨单元承接到 nop-job-C7-006 工单（对齐纪律第五条），本批零触碰；C4-003 字段10 前半（yaml 改名）本批兑现、后半（预校验）登记承接。
- **C10-010 测试设计（`TestWfScanJobWiringR409`）**：装配断言三腿——(i) 类路径加载两 job.yaml 解析 `invoker.bean`/`invoker.method`；(ii) 断言 `invoker.bean` 值存在于 `app-scheduler.beans.xml` bean id 集（解析 xml 提取 id 集）；(iii) 断言 `WfTaskScanner` 类存在 `invoker.method` 同名 public 方法（反射）。**当前即红（确定性）**——`wfTaskScanner` 全仓零命中（audit 字段12 亲证）。nop-wf-scheduler pom 已依赖 nop-job-local（:26 亲证——UP2 pom 义务提前兑现）：测试可经 nop-job-local 类库形态解析 yaml 模型，**禁造宿主面断言**（fire 生命周期/FAILED 终态语义断言禁选——归属 nop-job 单元）。

### 3.2 W-06(a) scanner 入口异常边界收敛唯一形态

**唯一结论 = scanDueTasks/scanRemindTasks 两入口 catch(Exception) 收敛：ERROR 留痕 + 轮失败 gauge 增量 + 正常返回（fire 以成功收尾，下一轮 cron 自补偿）。Error 不拦截（直传宿主）。**

- 形态：两 public 入口（:179-184/:186-191）try 包裹 `ormTemplate.runInNewSession(...)` 全体，`catch (Exception e)` → `LOG.error("nop.wf.scheduler.scan-round-failed:half=due|remind", e)` + 新增静态 `AtomicLong SCAN_ROUND_FAILURES` 增量 + gauge `nop.wf.scheduler.scan-round-failures` 注册（:132-142 registerScanGauges 同构扩展——静态守卫一次注册形态复用）。**不 rethrow**——fire 正常完成，消除「单轮瞬态故障→fire ERROR→FAILED 永久停摆」的使用面通道。
- **Error 不拦截的裁定**：catch(Exception) 非 catch(Throwable)——JVM 级 Error 直传宿主由其失败路径呈现，2541 catch(Throwable) 纪律的场景前提（promise 必然终结义务）在此不成立（job fire 线程无 promise 契约）；Error 转业务成功=致命降级禁选。宿主对 Exception 之外的失败终态语义=形态 A，归 nop-job-C7-006（零触碰）。
- **无状态残留契约落款**：failCounts/cursor/scanned 均为方法局部变量（:203-205/:340-342 亲证），due 半区两枚 gauge 每轮入口重置（:207-208 亲证）——单轮失败不携带状态残留、可再次触发由既有每轮重置结构性保证；C10-012 断言消费该契约（见裁定 E 附红⑥）。
- 外泄面枚举（收敛对象）：findDueActivatedSteps/findRemindActivatedSteps DB 异常、releaseScanLease finally 段异常、runInNewSession 会话级异常——三者当前均穿透入口至 fire 层；单条级失败已由 scanDueTask/scanRemindTask 条级 catch 隔离（R1-01 绝缘层，零回退）。

### 3.3 W-06(b)×W-09 逐项 deadline 唯一分支（同簇一次设计）

**唯一结论 = 带超时等待分支：:274 `FutureHelper.syncGet(...)` 改为对返回 CompletionStage 的超时等待（`toCompletableFuture().get(deadlineMs, TimeUnit.MILLISECONDS)`），超时记 error 跳条+退避留痕；拒绝异步投递分支。**

- **配置键**：`nop.wf.scheduler.action-deadline-ms`（WfSchedulerConfigs 新增 `CFG_WF_ACTION_DEADLINE_MS`），缺省 **60000**；`< 0` 装配期 fail-fast（init() :116-123 既有校验链同位新分支——`ERR_WF_INVALID_SCHEDULER_CONFIG` 同码）；`== 0` 禁用（沿 scan-time-budget-ms `<=0 → Long.MAX_VALUE` 既有约定 :200 亲证——禁用为长动作部署的显式逃生门）。
- **异常形态适配**（Phase 2 精确契约）：`get()` 抛 `ExecutionException` 时解包 cause 后按既有分类链走（:275-289 NopException race-skip/allowCallByUser 分支逐字保留）；抛 `TimeoutException` 时走 deadline 跳条：`LOG.warn("nop.wf.scheduler.due-action-deadline-exceeded:wfId={},stepId={},deadlineMs={}")` + 经 postponeFailedDueTask 退避（:306-325 既有毒步骤收敛路径复用——超时条目退出本轮扫描集，恒挂死动作按 60s×2^n 封顶 1h 退避）。**诚实登记**：deadline 不终止底层 action 执行（宿主无 cancel 设施——cancelRunning 空实现=形态 A 宿主面）；僵尸动作残留以「每挂死条目至多一个、fire 正常收尾、退避退出扫描集」为收敛声明。
- **熔断留痕落款**：单轮熔断=既有 scan-time-budget-ms 批间检查（:211-214 零改写）；deadline 跳条留痕=deadline-exceeded WARN+postpone 退避 ERROR/WARN 链（:308-321 既有）+DUE_SCAN_FAILURES 计数（postponeFailedDueTask :324 既有增量）。不新增熔断状态机（无状态残留契约约束——新增跨轮状态即破坏 §3.2 契约）。
- **拒绝异步投递分支的理由**：(i) 快速入队+worker 池投递需新建载体池（sizing/有界队列/context 传播/失败回调接线回 postpone 路径）=调度器模块内新子系统，波及面与本批最小 diff 原则冲突；(ii) 共享池挤压面不由「换一个共享池」闭合，专用池=new 设施面（宿主 bean 域）；(iii) 2498 载体对齐义务仅在采异步时触发（引用不重建）——不采则零载体依赖；(iv) C7-006 墙钟批次（R4-32）为扫描通道设计域承接方，调度通道重构归其批次域。异步投递候选归 Non-Blocking Follow-up（plan 已登记）。
- **池挤压评估落款（配置指引形态）**：deadline 使单条挂死对 nopJobScheduledExecutor（core=max=4 全 local job 共享）的占用上界为 action-deadline-ms（缺省 60s）——4 条并发挂死的最坏冻结窗从「永久」收敛为「≤deadline」；池 sizing 指引（增大 pool-size 或错峰 cron）+deadline 键构成运维缓解组合，落 owner doc（E5）。专用池代码缓解否决（宿主 bean 面触碰+新设施）。

### 3.4 裁定 A 约束落款

2514 分批/fencing/退避常量与 2541 remind 退避/gauge/WARN 校验产物 git diff 零触碰（语义口径边界：「判据、常量、既有分支逻辑零改写，新增边界/新增键/新增 gauge 埋点行允许」——2541 裁定 E R1-M3 同款口径）；形态 A 宿主面零断言；红④/红⑥/红⑨ 见裁定 E 附。

## 四、裁定 B：task 状态与 fencing 域（W-01/W-11/W-13）

### 4.1 W-01 saveTaskState 串行化唯一分支（含锁序评审落款）

**唯一结论 = 串行化分支：saveTaskState 复用 [05-02] 条带锁（按 taskInstanceId 单键取锁，共享既有 64 槽 ROW_LOCKS 数组），锁内以 fresh-read 终态守卫承载 first-terminal-wins（对 DB 真值判定），残余版本冲突 typed 映射。拒绝纯捕获收敛分支。**

- **锁入口形态**：新增 `rowLock(taskInstanceId)`（对既有 `ROW_LOCKS` 数组按 taskInstanceId 哈希取槽——`rowLock(taskInstanceId, stepPath)` 同数组复用 [05-02]，task 行锁与 step 行锁同槽共享为过度串行化可接受面，与 [05-02] 注释同构扩展非重写）；saveTaskState（:159-261）方法体全程置于该锁内（getEntityById→beforeSave→copy→save/update 同临界区，与 saveStepState :357 形态逐字对称）。
- **锁内 fresh-read 终态守卫（first-terminal-wins 的判定点）**：临界区内 getEntityById 取得 fresh entity 后、beforeSave/copy 之前：若 `entity.status` 为终态（isTerminalTaskStatus :642-647）且 ≠ 本 bean 待写状态 → DB 先到终态赢单：将 DB 终态收敛进内存 bean（status/exception 对齐——输家内存 bean 永久漂移 ACTIVE 的缺陷面在此闭合）+ `LOG.warn("nop.task.task-state-terminal-converged:taskInstanceId={},dbStatus={},ignoredStatus={}")` + 跳写返回。同态重复驱动幂等放行（skipTerminalOverwrite :294-296 同语义）。
- **残余冲突 typed 映射**：跨进程（owner 已声明暂缓边界）或同槽外残余窗口的 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND` 在 saveTaskState 出口捕获并重抛 `ERR_TASK_STATE_SAVE_CONFLICT`（TaskErrors 新码 `nop.err.task.state-save-conflict`，param taskInstanceId）——裸 ORM 错误码冒泡面闭合，error-handling 两档 compliant（框架公共 API 路径 typed 码）。跨进程 resume 完整性本体不实施（Non-Goal，禁顺带）。
- **锁序评审落款（监视器→条带锁单向论证——与 2514 裁定②联动，引用不重建）**：
  - 正向序证据：saveTaskState 的全部生产调用点位于 `synchronized(taskState)` 临界区内（TaskImpl 六处 :127/:184/:216/:232/:267/:282 亲证）——加锁后获取序恒为 monitor(taskState) → rowLock，单向无环；
  - **反向序排除证据**：rowLock 临界区内调用面 = taskDao helper（getEntityById/saveEntityDirectly/updateEntityDirectly）+ `state.beforeSave(taskRt)`（:169）——零监视器获取点；DaoWorkflowStore withWfLock 与本锁为独立锁域（2514 §3.1 锁序声明沿用）；
  - **beforeSave 钩子扩展点禁则（UP4——本节核心）**：task 级 `beforeSave` 用户钩子自此运行于 monitor→rowLock 嵌套内（step 级 saveStepState :365 已有同构先例——锁内调 state.beforeSave）。**禁则条款：`ITaskState.beforeSave` 实现禁止回调 taskRt/taskState 的同步方法（含任何触发 saveTaskState/saveStepState 的路径）——回调即「rowLock 内取监视器」反向序，ABBA 风险真实**。禁则以 owner doc 契约句+E5 落账（`docs-for-ai/03-modules/nop-task.md` :62-73 挂起与恢复节扩展），closure audit 以「rowLock 临界区零新增调用面」为核对锚。
- **拒绝纯捕获收敛分支（(b)）的理由**：catch-and-converge 只作用于版本冲突已发生之后——跨 bean 字段级覆写（后写者 fresh-read 到新 version 后盲拷贝陈旧 bean，不经任何冲突路径）无法被异常收敛点覆盖，正常路径仍 last-writer-wins；且 first-terminal-wins 判定后置到异常路径使语义判定与真实 DB 状态脱钩。串行化使判定点前移到锁内 fresh read，一次覆盖冲突窗与覆写窗两窗。
- **既有锚零弱化**：skipTerminalOverwrite（364 [05-01]）语义保留非删除——同 bean 内守卫逐字保留，本裁定补的是其跨 bean 不可达面；364 [05-02] 条带锁形态扩展非重写；2514 裁定② 监视器收敛零触碰。

### 4.2 W-11 workerId 节点身份赋值 + 滞留 ACTIVE 只读告警唯一形态

**唯一结论 = workerId 写入节点身份（newTaskState 单点赋值）+ loadTaskState 读路径滞留守卫（WARN+计数 gauge，不自动恢复）。零 DDL、零 job、零心跳列。**

- **节点身份形态**：`TaskConfigs` 新键 `nop.task.node-id`（缺省 null → 运行期解析为主机名+8 位随机后缀——WfTaskScanner.resolveNodeId :160-177 同构形态，装配期/首用一次生成）；DaoTaskStateStore 实例字段惰性解析。**写入点=newTaskState 单点**（:132-143 内 `entity.setWorkerId(nodeId)`——task 行创建即属主赋值）；step 行 workerId 维持 round-trip（:452/:505 零改动——最小 diff，task 行属主已满足滞留识别需要）。workerId 列在位亲证（orm :222/:308）——零 DDL，**条件申报①零触发**（心跳列否决：滞留判定以「workerId≠当前节点身份」为确定性信号，重启后节点身份必然变化，无需心跳时间戳）。
- **滞留识别形态（读路径守卫）**：loadTaskState（:146-156）afterLoad 后守卫：`status==ACTIVE && workerId != null && !workerId.equals(当前节点身份)` → `LOG.warn("nop.task.stale-active-task-loaded:taskInstanceId={},workerId={},currentNode={}")` + 静态 `AtomicLong STALE_ACTIVE_LOADED` 增量 + gauge `nop.task.stale-active-loaded` 注册（WfTaskScanner 静态守卫形态同构）。**只读不自动恢复**——守卫仅留痕，恢复决策留给显式 resume（与 owner doc「跨进程 DB 断点续跑完整性主题暂缓」裁定兼容——本批只闭合其检测盲区未声明部分，不重立暂缓裁定）。
- **拒绝 job 扫描形态的理由**：nop-task 全模块无 scheduler 依赖与 job 面（audit C7-010 字段6 亲证）——引入 nop-job-local 依赖+新 job.yaml=设施扩展；读路径守卫在唯一恢复入口（getTaskRuntime→loadTaskState）上必然触达滞留行，覆盖 resume 真实路径；全量滞留仪表（不依赖有人 resume）归 R-G 观测轮候选。
- **拒绝心跳列的理由**：条件申报①预设零触发约束下，心跳列引入=DDL 申报链；节点身份随机后缀使「workerId 匹配」已构成属主活性判定（同进程内属主必匹配、崩溃重启后必不匹配），心跳列增益仅在「同进程僵死」场景——该场景由 W-13 进程内 fence 与超时机制覆盖，不重复建设。

### 4.3 W-13 执行级 fencing 唯一分支（C10-014 宿主）

**唯一结论 = 进程内在途注册表：TaskFlowManagerImpl 实例 `ConcurrentHashMap<String, Boolean>` per-taskInstanceId fence，claim 点=TaskImpl.execute 入口（resume 终态短路判定之后），并发第二者于 claim 点 typed 快速失败——输家异常点位写死为 claim 处。拒绝 DB 条件 UPDATE 分支。**

- **接口面**：`ITaskFlowManager`（nop-task-core，实现方全仓唯一=TaskFlowManagerImpl 亲证）新增两方法：`boolean tryBeginExecution(String taskInstanceId)`（putIfAbsent 语义，false=在途）与 `void endExecution(String taskInstanceId)`（remove 语义）——普通源码签名变更零申报（单实现方，编译面闭合）。注册表为 TaskFlowManagerImpl 实例字段（进程生命周期=注册表生命周期，无跨重启残留面）。
- **claim 点契约（UP3 写死）**：`TaskImpl.execute`（:91）在 resume 终态短路块（:98-117）**之后**、监视器块（:122）之前：`if (!taskManager.tryBeginExecution(taskInstanceId)) throw new NopException(ERR_TASK_INSTANCE_ALREADY_RUNNING).param(ARG_TASK_INSTANCE_ID, ...)`（TaskErrors 新码 `nop.err.task.instance-already-running`）。claim 放置于短路之后使纯终态短路读（无业务执行）不被 fence 拒绝；fresh 任务（newTaskRuntime）同链经过 claim（taskInstanceId 唯一无碰撞，统一不变量）。
- **移除点契约（async 生命周期精确覆盖）**：`execute()` 方法返回≠执行完成——thenCompose 续延（:166-199）在返回之后运行。移除双机制：(i) 同步异常路径——catch 块（:155-164）内 driveTaskTerminal 前后 `endExecution`；(ii) 异步路径——对返回 stage 附加 `whenComplete((v, e) -> taskManager.endExecution(taskInstanceId))`（成功/异常/SUSPENDED 完成全路径覆盖——挂起任务的下次 resume 为新 claim，fence 窗口=单次 execute 生命周期）。**顺序 resume 不误伤推演**：先路 execute 无论以终态、SUSPENDED、异常乃至 promise 泄漏退出，出口均触发移除——后续顺序 resume（含 owner 声明的崩溃后人工恢复、`TestTaskKilledTimeoutResumeE2E.resume_inProgressNonTerminal_doesNotShortCircuit_mainStepRuns` 的顺序重跑语义）重新 claim 成功，fence 只拦并发窗，不拦顺序恢复。
- **拒绝 DB 条件 UPDATE 抢占分支的理由**：(i) status 列新增进程外写者=2514 裁定② 监视器收敛的第二写者面（监视器收敛语义零重开边界冲突）；(ii) 条件谓词无法分辨 CREATED/排队 ACTIVE 与执行中 ACTIVE（TaskConstants 状态集亲证——SUSPENDED 前置只拦挂起态 re-enter，拦不住 fresh-ACTIVE 双执行主缺陷面），谓词加 workerId 联动则向跨进程 fencing 半 claim（owner 已声明暂缓域，禁半越界）；(iii) 误占推演复杂度（崩溃残留 claim 行阻塞合法恢复）与 plan Risks 节双向退化警告直接冲突。进程内可达性为本缺陷主面（audit 字段3「进程内即可达，无需多节点」），跨进程面归暂缓裁定域。
- **C1-009 分立裁定零重开落款**：本裁定不触 SUSPENDED 写者守卫面（2514 裁定② 已收敛——监视器+终态前置检查零弱化）；W-13 fence 与监视器为正交两层（claim 拦并发 re-enter 于入口，监视器保写者原子于出口），零锁序交互（fence 为无锁 CHM 操作）。
- **C10-014 测试设计（`TestConcurrentResumeSingleExecutionR409`）**：suspend-plain 模型挂起 → 双线程 latch 对齐于 claim 点前 → 并发 `getTaskRuntime(id)`+`execute` ×50 轮 → ExecutionCounterBean 断言 mainStep 执行恒==1/轮 + 输家路确定性 `ERR_TASK_INSTANCE_ALREADY_RUNNING` 断言 + **顺序 resume 对照腿**（先路退出后第二次 resume 成功——防「fencing 拒绝合法恢复」伪绿反向）。概率腿（竞速窗未命中轮）诚实登记，以 latch 对齐构造保证确定性命中（50 轮竞速模板=C10-test-protection :358 TestGraphDrainRace 300 轮形态基线）。

### 4.4 裁定 B 约束落款

2514 裁定② 监视器收敛/364 [05-01] skipTerminalOverwrite/[05-02] 条带锁实现与注释零重写仅扩展；红①/红⑪/红⑬ 见裁定 E 附；锁序评审清单（monitor→rowLock 单向+beforeSave 禁则）为本组实施前置 gate（组 B 首项）。

## 五、裁定 C：图执行/上下文/解析链域（W-02/W-03/W-05）

### 5.1 W-02 enter∩wait 双调度唯一分支（校验+守卫叠加）

**唯一结论 = 叠加分支：(a) GraphStepAnalyzer 构建期硬失败为主（模型误配 fail-fast）；(b) runStep 入口一次性守卫为运行期兜底（并发窗闭合）。拒绝二选一单腿。**

- **(a) 构建期校验**：`initDag`（:46-91）在 addDataDepends（:88，数据依赖派生 wait 注入点）之后新增校验：逐步骤取有效等待集 `waitSteps ∪ waitErrorSteps`，与 `enterSteps` 求交，非空即抛 `ERR_TASK_GRAPH_ENTER_WAIT_OVERLAP`（TaskErrors 新码 `nop.err.task.graph-enter-wait-overlap`，param graphStepName+overlapStepNames——与 initDag 既有四码 ERR_TASK_DUPLICATE_STEP_IN_GRAPH/NO_ENTER/NO_EXIT/UNKNOWN 同族同位新分支，364 [02-06] 校验链零回退）。校验位次在 addDataDepends 后使 next 自动派生（normalizeWaitSteps :95-97）与数据依赖派生（addInputDepend :147/:157）两类注入均被覆盖——误配门槛极低面（audit 字段11）双通道闭合。**升级注记义务**：存量图模型存在 enter∩wait 重叠者升级后装配即 fail-fast，须先修模型再升级（owner doc E5，沿 2514 sync×executor 先例形态）。
- **(b) 运行期守卫**：`runStep`（:248）入口 stepFuture 取得后（:253）新增 `if (stepFuture.isDone()) { LOG.debug("nop.task.graph-step-reschedule-skipped:stepPath={},stepName={}", ...); return; }`——同节点二次调度跳过（含 exit 节点并发窗——首次完成后 cancellable.cancel 早退仅是单线程序防护，并发窗由本守卫闭合，复核弱化口径落款）。不级联不计数（首次执行已完成 bookkeeping）。
- **叠加而非二选一的理由**：audit 字段8 根因双列（「构建期校验缺口；runStep 无一次性调度守卫」）——两根各闭一窗：误配是模型 bug，静默跳过会隐藏配置错误（构建期硬失败使其在部署面可见）；并发窗是运行期事件，构建期校验不可达。守卫单行成本叠加无交互冲突。单腿守卫（无校验）否决理由=静默吞配置错误；单腿校验（无守卫）否决理由=exit 并发窗与未来调度路径新增无防线。
- **resume 语义边界落款**：跨 execute 调用的重调度不受守卫影响——initFutures（:445-447）对新 execute 将已完成步骤映射 completedFuture，续跑跳过由 continuation-skip 既有机制承载（守卫 isDone 判定同值，语义一致）；挂起节点的重新调度走 SUSPEND 路径（:301-308 不完成 stepFuture），守卫不误伤。

### 5.2 W-03 context 重投递缺省包裹唯一分支

**唯一结论 = 缺省重投递：submit 内 try 块首行以 stepRt 已捕获 context 包裹 body（`stepRt.getContext().execute(() -> ...)`——RunOnContextTaskStepWrapper :30-33 同构先例），无 opt-out 键。**

- **包裹形态**：`ExecutorTaskStepWrapper.execute`（:29-78）在 submit lambda 内、既有 try 块首行将 `getTaskStep().execute(stepRt)` 包为 `stepRt.getContext().execute(() -> ...)`（context 在 lambda 外捕获——同构先例亲证）。
- **与 2541 W-06 catch(Throwable) 相互作用推演落款（R1 义务）**：(i) 包裹位次=try 内首行——catch(Throwable) 边界随包裹自然外扩，context.execute 的绑定开销与 body 异常全部处于既有 catch 覆盖内；(ii) Error rethrow 语义不变——body 抛 Error 穿透 context.execute → catch(Throwable) → `completeExceptionally` + 原样 `throw (Error) t`（:56-58 逐字保留）；(iii) onCancel 兜底（:69-74）与 bindCancelToken（:76）作用于 ret/future，与 body 执行线程无关零弱化；(iv) promise 必然终结不变量（5s 断言口径，2541 裁定 B §4.4）零回退——包裹不引入新的悬挂路径（context.execute 同步绑定形态，非异步派发——RunOnContextTaskStepWrapper 注释「多个步骤 runOnContext 串行执行」语义印证其绑定即执行）。
- **显式退出语义落款**：`runOnContext=true` 既有路径保留（RunOnContextTaskStepWrapper 零改动）——与缺省包裹叠加为同 context 双绑定，幂等无害；**无 opt-out 配置键**（零 xdef 面——条件申报②零触发）：绑定 runtime 捕获 context 为严格更正确行为，无合法「需要空 context」场景；依赖旧「null/串号」行为的边缘模型以升级注记承接（行为变更面 E5 句）。
- **ITaskRuntime javadoc 候选落款**：getLocale（:45-51）javadoc 补句——executor 并行步骤体经缺省 context 重投递后，svcCtx==null 时 ThreadLocal 回退读取的是 runtime 构造时捕获的 context（修复前为池线程残留/null）；E5 同步。
- **拒绝 owner-doc-only 分支（文档强指引）的理由**：ThreadLocal 依赖静默失效/串号为数据正确性缺陷（非可用性缺陷），文档指引不闭合缺陷本体；捕获 context 已实存（TaskRuntimeImpl 构造时捕获），包裹为纯新增绑定零数据面风险。
- **红③ 对照腿（伪绿防线）**：runOnContext=true 路径修复前后均正确——其修复后仍绿证明缺陷面在缺省路径（对照腿防「包裹把所有路径都改坏」的过度修复伪绿）。

### 5.3 W-05 解析链治理唯一形态

**唯一结论 = getThreadPoolExecutor 解析链三件套：错类型 instanceof+typed 拒绝、保留名遮蔽 WARN（containsBean 判存在）、owner doc 保留名清单。GlobalExecutors 本体零触碰。**

- **错类型腿**：:134 `(IThreadPoolExecutor) beanProvider.getBean(executorBean)` 改为先取 Object、`instanceof IThreadPoolExecutor` 不成立即抛 `ERR_TASK_EXECUTOR_BEAN_INVALID`（TaskErrors 新码 `nop.err.task.executor-bean-invalid`，param executorBean+actualType+stepPath 语义位）——裸 CCE 无上下文面闭合；缺 bean 路径既有 fail-fast（ERR_IOC_BEAN_NOT_FOUND）零改动（audit 字段11 校准保留）。
- **遮蔽腿**：:132 GlobalExecutors 命中（保留名/注册表名）且 `beanProvider.containsBean(executorBean)` 为真（IBeanProvider.containsBean :19 亲证——零实例化判存在）→ `LOG.warn("nop.task.executor-reserved-name-shadowing:executorBean={},containerBeanExists=true; IoC bean is shadowed by GlobalExecutors reserved name")`。WARN 一次性语义不引入（不做 per-name 去重状态——新动态名无界增长面，与 2514 键空间治理同构矛盾；getThreadPoolExecutor 非逐调用高频路径，per-call WARN 量级可接受）。
- **owner doc 保留名清单（E5）**：三保留名 `nop-global-timer`/`nop-cached-thread-pool`/`nop-global-worker` 登记为 executor 属性不可占用名+类型契约句（executor bean 须实现 IThreadPoolExecutor）。
- **零触碰落款**：GlobalExecutors（平台设施只读引用）、getRateLimiter/getSemaphore 注册表面与逐出/frozen WARN/gauge（2541 产物）——getThreadPoolExecutor 为独立方法域 diff 分组禁交叉。

### 5.4 裁定 C 约束落款

GraphTaskStep 图收敛判据（completeGraphIfDrained :363-368 与级联先于减计数不变式 :333-336 注释）零触碰——守卫为入口早退不触计数路径；TestGraphDrainRace 族零回退；红②/红③/红⑤ 见裁定 E 附。

## 六、裁定 D：边界与规模域（W-07/W-08）

### 6.1 W-07 items 物化治理唯一分支（引擎层剥离）

**唯一结论 = 持久化剥离分支（引擎层实现）：items 移出 LoopStateBean/ForkStateBean 持久化面——stateBean 仅持 index（loop）/itemCount（fork），items 经 stepRt attribute 进程内持有；跨进程 resume 经 itemsExpr 重算。DaoTaskStateStore 序列化面零 diff。**

- **LoopTaskStep 形态**：LoopStateBean 移除 `items` 字段（保留 index :95——旧持久化行 wrapper 内残留 items 键经 getStateBean Map 转换自然忽略，向后兼容零迁移）；execute（:120-190）items 解析改为：`stepRt` attribute（同一次 execute 调用与同进程 suspend/resume 窗口内存活）→ 缺失时 `itemsExpr.invoke(stepRt)` 重算。每迭代 saveState（:155-157/:169-171）持久化 wrapper 的 stateBean 仅含 index——**单迭代序列化量 O(1)，累计 O(N²) 序列化量闭合**；3× 瞬态峰值（先全量物化后判 4000 的序列化面）随 wrapper 缩小消失。
- **ForkTaskStep 形态**：ForkStateBean 的 items 字段替换为 `itemCount`（resume 去重标记语义保留——stateBean 非空即已产出）；items 同形态 attribute 持有+重算。
- **serializeStepStateData 零 diff 落款**：store 层 wrapper（:571-577）与 4000 降级链（:580-594）逐字保留——剥离在引擎层完成后 wrapper 自然缩小，DaoTaskStateStore 本工单零 diff（组 D 与组 B 的 DaoTaskStateStore 共享站点约束消解，见第一节次序矩阵）。
- **resume 语义评估落款（义务）**：跨进程 resume 的 loop/fork 以 itemsExpr 重算重建集合——**itemsExpr 非幂等（随机源/外部读）时恢复集合与原集合不一致=断点续跑语义漂移**，以 owner doc 声明承接（「loop/fork items 表达式须为确定性/纯函数以支持跨进程断点续跑」——与既有跨进程 resume 暂缓主题同域声明，非新暂缓裁定）；进程内 suspend/resume 与单请求内迭代不受影响。**诚实登记**：items 进程内驻留 O(N) 保留（index 寻址迭代必须持有集合——缺陷面的 O(N²) 序列化与 3× 瞬态峰值两项闭合，驻留项为循环语义固有并以 Follow-up 游标候选登记）。
- **拒绝游标分支（(b)）的理由**：Iterable 单向游标不支持 index 寻址（varName 取值 :141 与 bodyStepIndex 断点续跑按 index 随机访问）且 resume 无法在一次性 Iterable 上跳位——游标化与持久化剥离仍须组合，非独立分支。
- **拒绝 bean 白名单分支（(c)）的理由**：per-bean 序列化注解需逐 bean 维护且对未来新增大字段 state bean 静默漏防；store 层通用剥离需 store 感知具体 bean 字段名（分层破坏）——引擎层剥离一处闭合两 bean 且结构性防新 bean 复发。
- **maxCount 零触碰落款**：:31/:40/:194 迭代截断既有半面零改动（items 物化面不受其约束——C5-005 立案本体）；C7-013/C5-008/C2-006 同路径顺带评审记录零本体（随主 diff 评审不改码）。

### 6.2 W-08 读路径下推唯一分支

**唯一结论 = 下推范围唯一清单：DaoWorkflowStore override 五读方法族（缝=AbstractWorkflowStore.getSteps protected abstract :32——IWorkflowStore 签名零变更、两测试桩天然存活[UP5]），谓词不可翻译路径保持全集并登记。**

- **下推腿（五项）**：(i) `getLatestStepRecordByName`——DaoWorkflowStore override 改 `stepDao` 查询 `eq(wfId)+eq(stepName)+orderBy(createTime desc)+limit(1)` 全下推（:51-55 全集排序消除）；(ii) `getStepRecords(wfRecord, includeHistory=false, ...)`——override 对 includeHistory=false 腿补 SQL 预滤 `lt(status, WF_STEP_STATUS_HISTORY_BOUND)`（isHistory 为 status 派生 default 方法亲证——语义等价子集缩减；WorkflowImpl.getActivatedSteps :232-234 经该腿调用亲证，runAutoTransitions :922 每轮全集读放大主热点由此闭合）；(iii) `getStepRecordsByName`——`eq(stepName)`（+history 腿同 (ii)）下推；(iv) `getJoinWaitStepRecords`——`in(stepNames)+lt(status, HISTORY_BOUND)` 预滤、joinGroup 判定内存（joinGroupGetter 为函数参数不可翻译）；(v) `getNextJoinStepRecord`——`eq(stepName)+lt(status, HISTORY_BOUND)` 预滤、joinGroup 内存。
- **保持全集路径登记（语义边界）**：`getStepRecords(includeHistory=true, filter=非空)`——任意 `Predicate` 不可翻译为 SQL，保持 getSteps 全集（该路径为「要全部历史+自定义过滤」的低频显式请求）；filter 为常见谓词时的进一步识别下推归 R-G 优化候选（Non-Blocking Follow-up 已登记告警增强同域）。
- **接口面落款（UP5）**：AbstractWorkflowStore.getSteps protected abstract 签名零变更；被 override 的方法均为 IWorkflowStore 既有签名（default 重载投影需求不存在——本批无签名新增）；两个既有 `@Override` 测试桩（nop-wf-core InMemoryWfStore / nop-wf-service MockWorkflowStore——2541 R1-M2 亲证登记）实现自有读方法，编译面天然零触碰（Phase 2 核对项：两桩 git diff 为空亲证）。
- **单 wf 步骤数治理落款（与 W-10 联动）**：10k 轮/请求上限（plan 344，:52/:56）为零触碰基线；跨请求累计无上界面以 owner doc 包络声明+保留策略句（裁定 E §7.1）联动承接——本批不做步骤数告警仪表（Follow-up 候选）。
- **既有查询族零改写点名**：buildDueScanQuery/buildRemindScanQuery/buildScanQuery/buildTransferScanQuery/tryClaimScanLease/releaseScanLease/postpone 族直发 UPDATE（2514/2541 产物）零触碰；**TestWfScanQueryShape（2514 C10-008）点名零改写**——红⑧ 查询形状腿为 getSteps 族新增断言非既有腿改写。

### 6.3 裁定 D 约束落款

新配置键零枚（裁定 D 无新键）；红⑦/红⑧ 见裁定 E 附；`TestDaoTaskStateStoreRoundTrip` 族为 W-07 回归门（旧格式行解析兼容腿在其覆盖域——零改写）。

## 七、裁定 E：账本与滞留观测域（W-10/W-12）

### 7.1 W-10 账本保留唯一分支（DBA 策略声明形态）

**唯一结论 = owner doc DBA 侧保留策略声明形态：`docs-for-ai/03-modules/nop-wf.md` 补保留策略节（五张 nop_wf_* + 两张 nop_task_* 表按终态时间戳的保留期归属 DBA 侧执行；两段式建议=先备份后清理；白名单终态集建议）。红⑩=结构钉诚实留痕。**

- **拒绝内置归档 job 分支的理由**：(i) 真归档（先归档后删除两段式）需要归档表=DDL——触发条件申报①，预设零触发约束下禁选；纯 DELETE 无归档=不可逆数据破坏面内置平台（KILLED 复查场景/保留期误配的破坏性风险 plan Risks 节自认）；(ii) 归档 job 缺省关闭形态对缺陷面（无保留路径）的收敛=交付一个默认不可用的破坏性工具，运维价值低于声明形态；(iii) 审计字段2 自身定性「已声明设计方向的运维缺口」——声明形态与其定性对齐。内置归档 job 候选归 R-G 优化轮（Non-Blocking Follow-up 已登记）。
- **DaoWorkflowStore 零新增方法落款（R1 观察项闭合）**：文档形态零代码——第一节分组登记节该观察项以零触发闭合。
- **键归属落款**：零新键（保留期属 DBA 侧策略参数，不进 `nop.wf.*` 键空间）。
- **与 W-08 联动落款**：单 wf 步骤数包络声明与保留策略句同节成文（账本膨胀两轴：跨实例累计+Wf 实例内步骤数）。
- **红⑩ 形态落款**：结构钉（E5 保留策略句落账核对）——2537 红⑥ 形态诚实留痕禁伪绿（不造无本体红的行为断言）。

### 7.2 W-12 SUSPENDED 滞留监控唯一分支（gauge 指标形态）

**唯一结论 = DaoTaskStateStore 注册 DB 聚合 gauge 两枚（`nop.task.suspended-tasks`，tag kind=all|stale——TaskFlowManagerImpl registry-size 单名+tag 先例），stale 阈值键 `nop.task.suspended.stale-ms` 缺省 86400000。拒绝 resume-when 轮询 job 分支。**

- **gauge 形态**：静态 AtomicBoolean 守卫一次注册（WfTaskScanner :132-142 同构）；Gauge.function 按指标 scrape 时机执行索引聚合查询（`count(status=SUSPENDED)` / `count(status=SUSPENDED and updateTime < now-stale-ms)`——status+updateTime 既有列零 DDL）；查询失败 `LOG.warn`（60s 周期化——CAP_WARN_INTERVAL_MS 形态复用）+ gauge 返回 NaN（诚实缺席，非静默 0）。
- **键归属**：`nop.task.suspended.stale-ms` 落 TaskConfigs（nop-task-core 既有键持有类——2541 裁定 D 先例）。
- **拒绝轮询 job 分支的理由**：(i) nop-task 无 scheduler 依赖与 job 面（新增=nop-job-local 设施扩展+新 job.yaml+wiring 纪律链）；(ii) 自动 resume 触发面触碰「等人工」声明语义边界（resume-when 自动续跑改变挂起语义本体——owner doc 声明语义零触碰约束）；(iii) 2497 §七 timer 只触发约束需新建载体通道合规链。gauge 形态零语义触碰、红⑫ 可行为钉（行注入→值断言）。
- **「等人工」声明语义零触碰落款**：gauge 为纯观测面——滞留 SUSPENDED 的 resume 仍唯一经显式调用（SuspendTaskStep :36-42 resumeWhen 求值语义零改动）。

### 7.3 裁定 E 约束落款

NopWfInstance.transitToStatus/DaoWorkflowStore 写入口族/DaoTaskStateStore newTaskState+saveStepState 幂等 upsert 面（账本写侧）零触碰——W-10 文档形态与 W-12 gauge 均为观测/声明面新增；红⑩/红⑫ 见裁定 E 附。

## 八、裁定 E 附：红钉总表、零回退钉全列、C10 关联面注记

### 8.1 红①–红⑬ 逐钉形态（确定性标注/构造路径/伪绿防线/闩带上界 ≤30s）

| 红 | 工单 | 形态与确定性 | 构造路径 | 伪绿防线 | 闩带 |
|----|------|-------------|---------|---------|------|
| ① | W-01 | 行为红（确定性——双 runtime latch 对齐交错构造） | 同 taskInstanceId 两 runtime 双线程：T1 经终态 driver 完成写（version N+1）放行 latch → T2 陈旧快照写；断言调用方无裸 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND` 冒泡+终态语义正确+输家内存态收敛（fresh-read 守卫后 T2 bean status==DB status） | 守卫对 DB 真值断言（防「锁存在但交错仍覆写」退化）+364 [05-01] 语义对照零弱化 | 30s |
| ② | W-02 | 行为红（确定性——pre-fix count==2 双跑） | 模型 X 同列 enterSteps 且 next→X：(i) 装配拒绝腿——构建断言 ERR_TASK_GRAPH_ENTER_WAIT_OVERLAP；(ii) 守卫腿——程序化构造绕过分析器校验后执行，ExecutionCounterBean 断言 body 恒==1 | 守卫腿独立计数断言（防「校验存在但双跑仍发生」）；TestGraphDrainRace 族回归门 | 30s |
| ③ | W-03 | 行为红（确定性——pre-fix null/串号） | 编程式 `newTaskRuntime(task,false,null)`+executor 步骤 body 内读 `currentLocale()`/context 值断言等于注入值；对照腿 runOnContext=true 修复前后均绿 | 对照腿防过度修复伪绿（包裹把全部路径改坏即对照腿红）；TestCraR408ErrorBoundaryLifecycle 零回退 | 30s |
| ④ | W-04 | 行为红（确定性——=C10-010 装配钉本体） | 两 yaml 解析 invoker.bean→断言存在于 app-scheduler.beans.xml bean id 集+method 反射存在；pre-fix wfTaskScanner 零命中即红 | 装配断言消费真实解析链（pom nop-job-local :26 依赖在位——UP2 勘误登记 pom 亲证完成）；禁造宿主面断言 | 同步 |
| ⑤ | W-05 | 行为红（确定性——(i) pre-fix 裸 CCE；(ii) pre-fix 零 WARN） | (i) 错类型腿：executorBean 指向非 IThreadPoolExecutor bean → typed ERR_TASK_EXECUTOR_BEAN_INVALID 含 bean 名断言；(ii) 遮蔽腿：同名保留名 IoC bean 存在 → WARN appender 捕获存在性断言 | 缺 bean 路径对照腿（ERR_IOC_BEAN_NOT_FOUND 既有 fail-fast 不受扰动）；getRateLimiter/getSemaphore 注册表回归零触碰 | 30s/同步 |
| ⑥ | W-06 | 行为红（使用面确定性；形态 A 宿主面禁造） | =C10-012 韧性契约：注入 scanDueTasksInCurrentSession 体失败 → 断言 fire 正常收尾+无状态残留（gauges 每轮重置语义）+可再次触发+scan-round-failures gauge 增量；挂死腿与红⑨ 同载体拆腿禁重复计数 | 形态 A（FAILED 恢复语义）断言禁造——交叉引用登记 nop-job-C7-006 测试义务；ContinuesAfter* 三用例零改写 | 30s |
| ⑦ | W-07 | 行为红（确定性——体积/结构断言） | 大 items 注入 → 断言 saveState 持久化 wrapper 结构不含全量 items（stateBeanData JSON 解析断言 stateBean 仅 index/itemCount）+每迭代 wrapper 长度恒定断言（O(N²) 消除） | resume 重算腿（幂等 itemsExpr 桩重算==原集合断言——防「剥离存在但 resume 数据损坏」）；TestDaoTaskStateStoreRoundTrip 族旧格式兼容零回退 | 30s |
| ⑧ | W-08 | 行为红（确定性——pre-fix 全集载入） | (i) 查询形状腿：getLatestStepRecordByName/getJoinWaitStepRecords 族 QueryBean 含 orderBy+limit/SQL 谓词断言（对齐 TestWfScanQueryShape 形态新增 getSteps 族腿非改写）；(ii) 载入行数腿：注入 N 行含历史 → includeHistory=false 腿断言载入行数==非历史行数 | includeHistory=true+filter 腿对照（保持全集语义非静默截断）；due/remind/transfer 既有查询形状腿回归 | 30s |
| ⑨ | W-09 | 行为红（latch 30s 上界确定性构造） | dueAction never-complete stub → 断言扫描轮在 action-deadline-ms 内返回+该条跳过+postpone 退避写入+deadline-exceeded WARN 留痕；禁用腿（deadline-ms=0 → 无超时语义保留对照） | 轮完成时延断言（防「超时存在但扫描仍挂死」）；退避链既有 ERROR/WARN 零改写 | 30s |
| ⑩ | W-10 | 结构钉（文档声明形态——2537 红⑥ 诚实留痕禁伪绿） | E5 保留策略句落账核对（nop-wf.md 保留节存在+两段式/白名单建议在文）；零行为断言 | 结构钉诚实登记（不造无本体红） | n/a |
| ⑪ | W-11 | 行为红（确定性——pre-fix workerId null+零告警） | (i) 赋值腿：newTaskState 注入 → 断言 task 行 workerId==nodeId；(ii) 滞留腿：ACTIVE 行注入（workerId=foreign-node 模拟崩溃残留）→ loadTaskState → WARN 存在性+stale-active-loaded gauge 值断言 | 同节点腿对照（workerId==self 不告警——防误报）；不自动恢复断言（守卫后 status 仍 ACTIVE） | 30s |
| ⑫ | W-12 | 行为红（确定性——pre-fix 零指标） | SUSPENDED 行注入（updateTime 新旧两行）→ gauge 断言 all==2/stale 按阈值分界计数；阈值键行为钉（stale-ms 边界两侧注入） | scrape 失败腿（NaN 断言——防静默 0）；「等人工」语义对照（gauge 注册零 resume 触发） | 30s |
| ⑬ | W-13 | 行为红（50 轮竞速；概率腿未命中诚实登记） | =C10-014 本体：suspend-plain 挂起 → 双线程 latch 对齐并发 getTaskRuntime+execute ×50 轮 → ExecutionCounterBean mainStep 恒==1+输家 ERR_TASK_INSTANCE_ALREADY_RUNNING 确定性断言+顺序 resume 对照腿（先路退出后 resume 成功） | 双向伪绿防线：执行计数（防「fencing 存在但双跑仍发生」）+顺序对照腿（防「fencing 拒绝合法恢复」）；`TestTaskKilledTimeoutResumeE2E.resume_inProgressNonTerminal` 顺序语义零改写论证（claim 点在短路后+出口移除——顺序 resume 不误伤） | 30s |

概率腿行为红未命中时诚实登记以结构钉+变异腿承载（2539 总册先例）；全部钉落 `_tmp/red-green-ledger-2542.md` 总册+逐 trial 日志存证（原始日志保留纪律）。

### 8.2 零回退钉全列

- **勘误 8 双签名（新钉比对基线）**：nop-task solo **334 tests 0F 0E 0S** / nop-wf solo **256 tests 0F 0E 5S**（与 2541 收口签名逐字一致——签名维持+新钉比对）。
- **R211 钉族（2514）**：`TestPhase4TerminalAndConcurrency`（[05-03]/[05-04]+2514 改写后形态——W-01/W-13 零回退基座）/`TestWfTaskScanner` 全列（含 ContinuesAfter* 三用例——C10-012 批内隔离保护零改写）/`TestWfTaskScannerFencing`/`TestFirstClassConfigValidation`。
- **R408 钉族（2541）**：`TestCraR408ScannerRemindBackoff`（W-06/W-09 零回退）/`TestCraR408ErrorBoundaryLifecycle`（W-03 同文件零回退）。
- **查询形状钉**：`TestWfScanQueryShape`（2514 C10-008）**点名零改写**（W-08 查询族修法新增 getSteps 族腿）。
- **364/349/2498/344 钉**：`TestTaskStepHelperRetryDelayOffload_C4_001`（2498 载体钉）/364 校验链钉族+条带锁钉/349 取消兜底钉族/344 10k 上限（DefaultWorkflowExecutor 零触碰）。
- **既有行为锚**：`TestGraphDrainRace`/`TestGraphErrorSuccessRace`/`TestGraphOnErrorEdge`/`TestGraphStepNodeDefensiveCopy`（W-02 回归基座）/`TestSuspendSemantics`/`TestAsyncSuspendPropagation`/`TestChildRuntimeCancelPropagation`/`TestExecutorTaskStepWrapperAsyncBranch`（W-03/W-12 回归基座）/`TestDaoTaskStateStoreRoundTrip` 族（W-07 旧格式兼容回归门）。
- **零改写核对义务（非零回退清单成员但须逐个核对）**：`TestTaskKilledTimeoutResumeE2E`（顺序 resume 语义保持——claim 点在短路后+出口移除论证）/`TestSuspendContract`/`TestPlan349Fixes`/`TestSuspendSemantics`（getTaskRuntime 顺序 execute 消费形态——fence 不触）。

### 8.3 C10 关联面注记表

| C10 | 宿主 | 本批处置 |
|-----|------|---------|
| C10-010 | W-04（同 diff 绑定，禁拆分） | 裁定 A §3.1 兑现：装配三腿（yaml 解析/bean id 集/method 反射）——当前即红确定性 |
| C10-012 | W-06（同 diff 绑定，禁拆分） | 裁定 A §3.2+E 附红⑥ 兑现：韧性契约（使用面无状态残留可再触发+轮失败 gauge）——形态 A 宿主面断言禁造（登记 nop-job-C7-006 交叉引用） |
| C10-014 | W-13（同 diff 绑定，禁拆分） | 裁定 B §4.3 兑现：50 轮竞速+执行恒==1+输家 typed 断言+顺序对照腿——概率腿未命中诚实登记 |
| C10-001/003/004/005/006/008/009/011 | 2514 已兑现 | 零改写（含 C10-008 TestWfScanQueryShape 点名） |
| C10-002/013 | 2541 已兑现 | 零改写（`TestFirstClassConfigValidation`/`TestCraR408ErrorBoundaryLifecycle` 零触碰） |
| C10-007 | plan 2498 已兑现 | 零触碰 |

## 九、E5 落点清单（owner doc 逐处——Phase 3 统一落账）

`docs-for-ai/03-modules/nop-task.md`：

1. :38 并行语义节——W-03 context 缺省重投递句（executor 步骤体绑定 runtime 捕获 context+行为变更升级注记+runOnContext 幂等说明）；
2. :62-73 挂起与恢复节——W-01 saveTaskState 收敛句（条带锁扩展+fresh-read 终态守卫+beforeSave 钩子禁则）+W-11 workerId 属主句（nop.task.node-id 键+stale-active 读路径告警声明——不自动恢复）+W-13 fencing 句（并发 resume 单执行保证+输家 typed 异常+顺序 resume 不受影响）+W-12 滞留监控句（suspended-tasks gauge 两枚+nop.task.suspended.stale-ms 键）；
3. :164 区校验链邻接——W-05 解析链句（错类型 typed 拒绝+保留名清单+遮蔽 WARN）+W-02 enter∩wait 构建期校验句（升级注记）；
4. loop/fork 节（按实际）——W-07 剥离句（itemsExpr 确定性声明——跨进程 resume 重建语义）；
5. :291-305 源码锚点表刷新。

`docs-for-ai/03-modules/nop-wf.md`：:56-76 并发与扫描契约节——W-04 修复注记（bean 名对齐+启用指引）+W-06 韧性句（入口异常边界+scan-round-failures gauge）+W-09 deadline 句（nop.wf.scheduler.action-deadline-ms 键+禁用语义+池 sizing 指引）+W-08 读路径下推句+**W-10 保留策略节（新增——DBA 侧保留期归属+两段式/白名单建议+单 wf 步骤数包络声明）**。

`docs-for-ai/02-core-guides/workflow-configuration.md`：按需（dueAction 节 deadline 键句）。

`docs-for-ai/04-reference/source-anchors.md`：按实际刷新（归 Phase 3 统一落账）。

## 十、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| W-06/W-09 扫描体异步投递（快速入队+worker 池） | 新建载体池=调度器模块新子系统（sizing/队列界/context/失败回调接线）；共享池挤压面不由换池闭合；2498 载体义务仅采异步时触发；调度通道重构归 C7-006/R4-32 域——deadline 分支最小 diff 直闭挂死面 |
| W-06 入口 catch(Throwable) | job fire 线程无 promise 终结义务（2541 catch(Throwable) 纪律场景前提不成立）；Error 转业务成功=致命降级禁选——Error 直传宿主呈现 |
| W-06 跨轮熔断状态机 | 新增跨轮可变状态即破坏「无状态残留可再次触发」契约；既有 scan-time-budget-ms+退避链已承载收敛 |
| W-06/W-09/W-10/W-12 新增定时器/job 面（未经 2497 §七核验） | 调度槽纪律 timer 只触发硬约束——本批四处均以「不触发」守约落款 |
| W-10 内置归档 job（先归档后删除） | 归档表=DDL 触发条件申报①（预设零触发）；纯 DELETE=不可逆破坏面内置平台（KILLED 复查/保留期误配）；缺省关闭形态交付默认不可用工具——声明形态与审计「运维缺口」定性对齐 |
| W-10 新增 `nop.wf.*` 保留期键 | 保留期属 DBA 侧策略参数非平台配置（文档形态零键） |
| W-12 resume-when 轮询 job | 自动 resume 触「等人工」声明语义边界；nop-task 无 scheduler 依赖（设施扩展）；2497 §七合规链 |
| W-12 心跳/滞留扫描 job（W-11） | 同上无 scheduler 依赖；读路径守卫在唯一恢复入口必然触达滞留行 |
| W-11 独立心跳/租约列 | 条件申报①预设零触发；workerId 节点身份随机后缀已构成属主活性判定；同进程僵死由 W-13 fence+超时覆盖 |
| W-01 纯捕获乐观锁收敛（无锁 (b) 分支） | 字段级覆写（fresh-read 后盲拷贝）不经版本冲突路径，异常收敛点覆盖不到；first-terminal-wins 判定后置异常路径与 DB 真值脱钩——串行化使判定点前移锁内 fresh read |
| W-01 新建独立锁数组 | [05-02] 64 槽形态扩展即基准（同数组复用+同构注释），双数组=两套锁纪律无增益 |
| W-13 DB 条件 UPDATE 抢占 | status 列进程外第二写者破坏 2514 裁定② 监视器收敛；谓词无法分辨排队 ACTIVE 与执行中 ACTIVE（双向退化：误伤合法/漏挡主缺陷面）；向跨进程半 claim 越暂缓裁定域 |
| W-13 fence claim 点=getTaskRuntime | 只读检查型 getTaskRuntime 调用（不 execute）被误拒+注册表泄漏面（无移除触发点）——claim 点=execute 入口使 fence 窗口精确等于执行窗口 |
| W-02 单腿运行期守卫（无构建期校验） | 误配是模型 bug——静默跳过隐藏配置错误；部署面可见性（fail-fast）为校验腿独立价值 |
| W-02 单腿构建期校验（无守卫） | exit 节点并发窗与未来调度路径新增无运行期防线（audit 根因双列） |
| W-02 新 xdef 属性/opt-out 键（W-03 同） | 条件申报②预设零触发；Java 侧校验/缺省包裹零模型面变更即可闭合 |
| W-03 owner-doc-only（文档强指引） | ThreadLocal 失效/串号为数据正确性缺陷，文档不闭合本体；捕获 context 实存，包裹零数据面风险 |
| W-05 保留名 WARN per-name 去重状态 | 新动态名无界增长面（与 2514 键空间治理同构矛盾——2541 R2 Minor 同理由） |
| W-05 instanceof 前置到 GlobalExecutors 本体 | 平台设施触碰（条件申报③禁选）——解析链使用面落码即闭合 |
| W-07 游标消费分支 | index 寻址（varName/bodyStepIndex 断点）与 resume 跳位在一次性 Iterable 上不可达——非独立分支 |
| W-07 LoopStateBean 序列化白名单注解 | per-bean 注解逐个维护+未来大字段 state bean 静默漏防；store 层通用剥离需感知 bean 字段（分层破坏）——引擎层剥离一处闭合且结构防复发 |
| W-08 任意 Predicate 翻译下推 | 谓词不可静态翻译——保持全集路径显式登记（静默截断比全集载入更危险，2514 §五同理由） |
| W-08 新签名/接口投影 | 零需求（override 既有签名即可——UP5 缝点设计使两桩天然存活），新增签名=无谓波及面 |
| W-12/W-06 gauge 挂 DB 全量统计扩展 | MA-09/C8-015 全量观测治理归 R-G——本批两枚为最小落地不扩 stats 面（2541 裁定 E 同口径） |

## 十一、实施留给 Phase 2 的注意事项

1. **组 B 锁序评审是硬前置 gate**：W-01 落码前锁序评审清单（monitor→rowLock 单向论证+beforeSave 禁则）须先成文于组 B 首个 commit 的注释与本设计文档核对锚；rowLock 临界区（:159-261 包锁后）零新增调用面——closure audit 以此为 git 过滤核对锚。
2. **W-13 移除点双机制禁拆分**：claim（TaskImpl.execute 短路后）+同步 catch 移除+返回 stage whenComplete 移除三件同 diff；漏任一移除路径即产生 fence 泄漏（后续合法 resume 误拒）——C10-014 顺序 resume 对照腿为该退化的直接检出器，禁删对照腿。
3. **W-03 包裹位次必须 try 内首行**：包裹若置于 try 外，context.execute 自身异常即穿透 catch(Throwable) 复刻悬挂面——2541 W-06 产物零弱化核对（Error rethrow/onCancel/bindCancelToken 三点 git diff 逐行核对）。
4. **WfTaskScanner diff 分组纪律**：W-06/W-09 新增面（入口 catch+deadline+新 gauge+新键）与 2514/2541 产物（分批游标/时间预算/退避常量/fencing/remind 退避/gauge 注册逻辑）零改写——语义口径边界（「判据/常量/既有分支逻辑零改写，新增行允许」）closure audit 逐文件核对。
5. **W-08 下推后语义等价抽查**：orderBy(createTime desc) 与内存流排序的一致性（同 createTime 并列时序）+isHistory/status 谓词派生核对（IWorkflowStepRecord default 方法）须在红⑧ 查询形状腿内断言；两测试桩（InMemoryWfStore/MockWorkflowStore）git diff 为空亲证。
6. **W-07 旧格式兼容**：旧持久化行 wrapper 内 items 键经 Map 转换自然忽略（LoopStateBean 字段移除后）——TestDaoTaskStateStoreRoundTrip 族旧格式腿零回退核对；ForkStateBean.items→itemCount 字段替换的 resume 去重语义在红⑦ resume 腿断言。
7. **新码/新键/新 gauge 一次成文**：四枚 TaskErrors 新码（graph-enter-wait-overlap/instance-already-running/state-save-conflict/executor-bean-invalid）+三枚新键（nop.task.node-id/nop.task.suspended.stale-ms/nop.wf.scheduler.action-deadline-ms）+三枚新 gauge（scan-round-failures/stale-active-loaded/suspended-tasks）与既有键常量归属纪律（TaskConfigs/WfSchedulerConfigs）同批落地，键常量禁散落。
8. **同 diff 绑定兑现核对**：C10-010/012/014 与本体同 diff（禁拆分禁提前红空壳）；`*R409` 后缀+类注释归属 CRA 8.49 R4-09；同号防混（8.49-W-01≠8.48-W-01 等—— cra-index 三键核对）。
9. **TestTaskKilledTimeoutResumeE2E 零改写论证留痕**：其顺序 resume 用例在 W-13 落地后必须保持绿（claim 点设计保序）——若实测红即 fencing 误伤合法恢复的缺陷回归（禁止改测试迁就实现），回退 fencing 分支并重审 claim/移除点。
10. **组 D 与组 B 的次序消解登记**：W-07 引擎层剥离使 DaoTaskStateStore 零 diff——若实施中发现任何 W-07 需求触 store 层即越裁（禁选），回退为引擎层方案或四件链申报。
