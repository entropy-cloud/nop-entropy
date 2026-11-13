# job 调度账本自愈正确性、边界上限与保留回收并发契约（CRA 8.47 R4-07 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约；独立评审记录见「评审记录」节）
> Last Reviewed: 2026-11-07
> Source: plan 2540（[2540-cra-phase8.47-r4-07-job-scheduler-ledger-b.md](../../plans/2540-cra-phase8.47-r4-07-job-scheduler-ledger-b.md)，CRA Phase 8.47 R4-07 1.4 调度账本束 b G13 后半，10 工单全 P2；头部 Draft Review R1 修订 **M-1+m-1~m-8 = 裁定输入硬约束**——M-1 勘误 6 重写版（git 亲证漂移登记）、m-1 merge-adjudication :83/:178/:179 行锚订正、m-2 roadmap 行漂移登记、m-3 loadedTaskIds 处置口径、m-4 task_log 无状态列纯 logTime TTL、m-5 W-03/W-10 同文件 diff 分组与实施次序联调矩阵、m-6 看门狗守 2497 §七 调度槽纪律、m-7 组 B 为 plan 内部同域分组、m-8 MJ-1 修订义务覆盖 ReentrancyR406 :249-257 区全部 FAILED 自愈相关断言腿含 sleep 后腿——逐项吸收落款见「Draft Review R1 修订吸收登记」节）；发现事实源 = `ai-dev/audits/2026-10/2026-10-14-2230-deep-audit-nop-job-concurrency/`（[merge-adjudication.md](../../audits/2026-10/2026-10-14-2230-deep-audit-nop-job-concurrency/merge-adjudication.md) §2.2 独立案表十行[C5-002/C5-004/C7-003~C7-007/C7-009/C7-010/C7-011] + §2.3-4 worker 容量束联合推演 + §4.4 C10 映射 :178[m-1 订正]/:179 + §4.5 跨单元关联 + §五 批次建议 4/5/6）；批次定义 = batch-proposal §二.4 R4-07 行（:122——批次归属权威，m-7 落款）+ §一 E1–E6
> Owners: 裁定 A（SUSPICIOUS/liveness 域：W-03 复查+顺延+stale 上限+空集语义+计数 metric+namingService 桩 seam）；裁定 B（reconciler 窗口域：W-05 翻页轮转+limit-hit warn+javadoc 一致性核对）；裁定 C（触发器失败隔离域：W-08 终态化/PAUSED+计数升级+保存校验+错误码归属）；裁定 D（planner fence 域：W-09 post-fence 值匹配复查+放弃语义+seam 口径）；裁定 E（本地终态语义域：W-06 恢复 vs 声明二选一定夺+BF-1 硬边界+MJ-1 联动）；裁定 F（scanner stall 域：W-07 观察型看门狗+2496 零弱化+修复面收窄基线）；裁定 G（边界上限域：W-01 批上限+单条校验+错误码归属+loadedTaskIds 处置；W-02 三候选组合+联合推演）；裁定 H（保留族与回收域：W-04 R01 七款落款+W-10 墙钟扩展+javadoc 修正+per-status gauge）+**裁定 H 附**（红①–红⑩ 驱动器总表+零回退钉全列+C10 注记表+W-03/W-10 同文件联调矩阵[m-5 补锚]）。**裁定编号 = Phase 1 起草指令分组（与 plan Goals 预设 A–H+H 附同构）；分组权威 = 本文「工单→裁定映射与分组登记」节。**
> 文件命名裁定：`job-scheduler-ledger-selfheal-capacity-and-retention-concurrency-contract.md`——与 plan Phase 1 Targets 拟名逐字一致，零调整登记（无 2538 式改名义务）。
> 行号锚 = live HEAD `bd7432c618`（工作仓 `nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`）亲读值；nop-job 产品面对 plan 起草/执行基线 `80aac19cb3` **零 diff git 亲证**（唯一交界 commit = `bd7432c618` docs(plan) 提交[plan+roadmap 两文件，零产品代码]）——plan 工单表行号锚直接承接。局部子锚与 plan 行文存在 ±1~10 行同区域漂移，精确亲读值集中登记于「plan 输入勘正」节（勘误 6 重写版漂移纪律的兑现落点）；执行前如再漂移以 live 为准并登记于 plan 勘误。与 plan 文本不一致的 live 核证事实，裁定以本文为准。

本文是 plan 2540 全部 10 工单修复的唯一设计裁决权威。每条裁定给出：决策（唯一预设默认分支）、理由（并发裁定含触发时序 T1/T2 论证）、被否决的备选、零触碰边界。三范式/既有裁定结论引用不复制——SSOT 在既有设计文档。全批**零 nop-job-api diff**（错误码预设：复用 `ERR_JOB_LOG_INVALID_ENTRY`+`ARG_TASK_LOG_ID`（W-01 单条面，live `JobApiErrors` :122 亲证）；新增码全部落 **JobCoreErrors**（nop-job-core，非 api 叶——W-01 批上限码 + W-08 配置类错误码，plan PA 段预设「JobCoreErrors 模块内新增」口径，条件申报①零触发落款）、零 orm（W-04 索引预设零触发——条件申报③不触发落款）、零 `_` 生成物本体（W-04 清理载体新增 bean 定义=既有 `app-dao.beans.xml` +1 行——条件申报②批内申报落款）、零平台设施本体（nop-kernel/nop-commons 载体族只读引用——条件申报④禁选落款）。W-06 声明形态裁断使全批唯一「零产品 diff 修复面」工单以文档钉承载（裁定 E）。

## 既有裁定引用核对（引用不重建——软依赖 8.46 已解除 + 同单元前批 2496/2513/2539 已收口 + 对齐纪律「既有裁定引用条款」授权）

2539 裁定 A–G（[job-scheduler-ledger-observation-raced-branch-and-executor-envelope-concurrency-contract.md](job-scheduler-ledger-observation-raced-branch-and-executor-envelope-concurrency-contract.md)）为本批头号引用权威；2513 裁定①–③（[job-rpcpoll-termination-and-invoker-lock.md](job-rpcpoll-termination-and-invoker-lock.md)）为可引用既有裁定；2496 绝缘契约/2497 §七 载体契约/2498 §3.1.1 判定标准/2509 §3.6 出锁投递范式为引用边界；[ledger-retention-policy.md](ledger-retention-policy.md) 为 W-04 族级 SSOT（§3.6 明文 R4-07 按同构行对齐）。本批修复不得重开其体裁，修复 diff 不得触碰其已落地语义与钉内断言。**2539 Phase 4 就绪注记三指向兑现核对**（计划义务成文）：①C10-006/008 宿主承接指向→W-10/W-01 验收前置同 diff 在本批兑现（裁定 H 附 C10 注记表）；②W-10@2539 worker 容量束联调边界传导→本批裁定 G 联合推演记录消费（2539 裁定 G.2 决策 4(iii) 前瞻落款的兑现面）；③C7-004 R01 SSOT §3.6 指向→本批裁定 H W-04 七款对齐落地。**BF-1 判定落款 + MJ-1 联动定夺见裁定 E**（声明形态→MJ-1 钉零改写；BF-1 硬边界 ：500-502/:361 零触碰落款同节）。

| 既有裁定/产物 | 本批引用面 | 引用边界与零回退红线 |
|---|---|---|
| 2539 裁定 A–G 产物（R406 九钉+owner doc 八处落账） | 同文件跨批基线（LocalJobScheduler 六面+第七面 W-06；AbstractBatchScanner W-05 CAS+守卫+stall 面 W-07；BeanMethodJobInvoker catch 宽度零回退） | **红线 1（2539 产物零重开零回退）**：LocalJobScheduler 六面（getJobDetail 持锁/addJob raced/ctx 快照/startJobUnlocked catch 收宽/settleExecution/abandonAfterInterleave）+两阶段四产物（prepareJobLocked :385-392→startJobUnlocked :399-431→settleExecution :437-456→abandonAfterInterleave :464-471，占位先行时序）零改写零回退；**W-06 为 2539 预声明承接面**（:416-419 注释「self-heal belongs to C7-006/R4-07」live 亲证——本批兑现=声明面落账，非语义重开）；W-07 stall 面加于 W-05 守卫与 2496 绝缘语义**之外**（失败语义不变证明见裁定 F）；MJ-1 联动例外仅经裁定 E 专项通道（本裁定=零改写声明，例外未触发） |
| 2513 裁定①（rpcPoll 终结四通道） | W-03/W-10 同文件基线（JobTimeoutCheckerImpl 内 `resolveFromAuthority` 回传通道+`markSuspiciousAsTimeout` :485/`tryMarkTimeout` :562 两处「裁定①(c)」回传写点 live 亲证） | **红线 2（rpcPoll 五面零触碰）**：失败预算/deadline 兜底/DB 回传（resolveFromAuthority）/future 兜底+PollEntry 白名单+gauge 五面零改写零重开；W-03/W-10 修复触 scanTaskTimeouts/resolveAliveWorkerIds/tryMarkSuspicious/tryMarkTimeout 面，**不触 rpcPoll 回传通道本体**（TIMEOUT 写点的 `notifyPollManagerTaskTimeout` 调用在新旧路径均保持） |
| 2496 绝缘契约+存活指标族（owner doc §六） | W-07 站点产物面（AbstractBatchScanner :63-68 W-05 CAS/:70-76 六字段指标族/:78-99 startScanning 绝缘入口/:126-162 scanOnce）；W-02 看门狗产物面（JobWorkerScannerImpl in-flight 登记区） | **红线 3（绝缘产物+指标族零弱化）**：Throwable 边界（:154-158）/onScanCycleFailed/计数器（:144-146）+startScanning 拒绝真实化（:90-98）+MA-06 observe-only 看门狗（:425-434 in-flight 登记区）零改写零弱化；**勘误 3 收窄口径**：W-07 修复面=挂起检测非异常路径——存活指标族六字段+getters（:70-76/:224-251 区[F-m1 订正——类尾部 getters 区]）为修复面收窄基线（非重建对象）；`TestAbstractBatchScanner` 9 用例+`TestScannerInFlightGuardR406` 零回退 |
| 2497 §七 载体契约（owner doc §七） | W-04 清理载体周期链 + W-07 看门狗载体（m-6 补锚） | 调度槽纪律 =「timer 只承载触发与轻量内存检查，任务体 hop worker」——W-04 清理体（DB 删除 IO）必须 `timer.executeOn(worker)` hop 形态（SSOT §3.3 形态 A 约束同源）；W-07 看门狗体为毫秒级内存检查（volatile 读+日志）——§3.1.1 豁免判据 (a)(b)(d) 满足，载体形态随裁定 F 落款 |
| 2498 §3.1.1 专用载体判定标准 | W-02 对齐基准（批行增补明文） | **红线 4（判定标准对齐落款）**：判定标准（独立容量预算+关闭归属+不经 globalTimer 执行任务体+豁免判据）引用不重建；裁定 G 适用结论=W-02 **零载体创建**（软上限为 JVM 内计数闸门非载体）——worker 执行体仍经 `globalTimer().executeOn(globalWorker())` 共享（C 类合格准绳形态维持）；2498 落地面（DebeziumEngineWrapper 等）零触碰 |
| 2509 §3.6 出锁投递范式 | W-09 放弃分支语义参照 | `insertFireAndAdvanceSchedule` 放弃分支的「显式 WARN+语义显式化」形态按 §3.6 出锁复检范式投影（2539 abandonAfterInterleave javadoc 已引用同范式——live :400 注释亲证）；dispatch 先例 `insertTasksAndMarkFireDispatching`（:103-121 requireEntityById+DISPATCHING 复查）为形态投影源，零触碰 |
| ledger-retention-policy SSOT（§3.1 七款/§3.3 形态菜单/§3.4 传导表/§3.6 家族声明） | W-04 全部设计面（批行 exit criteria 增补） | **红线 5（R01 七款零参数发明）**：三键缺省值=SSOT §3.1（30d/1h/1000）；形态 A 机器速率写入面必须；生命周期钩子生产接线+回归钉四件套（接线断言防空壳——1.6-C7-005 反例，按号防混）；锚列逐表落名禁隐式选列；参数修订只走 SSOT 一处；orm 零触碰默认（§四.4）；落地后 §3.4 增补 nop-job 三表成员行（Phase 2/3 义务）；形态先例=R2-12 tcc 行（[retry-tcc-cas-compensation-and-ledger-hygiene.md](retry-tcc-cas-compensation-and-ledger-hygiene.md) §3.8） |
| plan 340 checker 契约钉（`TestJobTimeoutChecker` :615 `testLongRunningTaskWithoutTimeoutNotKilled` live 亲证） | W-03/W-10 回归门 + W-08 §2.7 注释 | **红线 6（plan 340 双面零触碰）**：(a) checker 侧 RUNNING 兜底契约钉（未配超时长任务不误杀）零回退——W-10 CLAIMED 墙钟回收仅作用已配超时形态（兼容论证落款裁定 H）；(b) completion `scanBatch` catch 区 plan 340 §2.7 注释（:111-114 live 亲证）与既有 catch 结构零弱化——W-08 隔离面为新增终态化/计数层，WARN 通道保留（裁定 C） |
| 2512 三联裁定模式（环境抖动处置） | E3 验证通道纪律 | stash 基线同红/solo 绿/复跑全绿三联留痕纪律沿用（2513 勘误 5/2538 勘误 7/2539 勘误 5 形态链）；coordinator 2S 容器/门控面（勘误 7 基线在案——E3 噪声源登记） |
| 2537/2538/2539 红绿判据纪律 | 裁定 H 附 | orTimeout 禁令/闩带 ≤30s 且收尾必放闸/`_tmp/` 红绿总册（命名 red-green-ledger-2540——Phase 2 创建，沿 2539 总册先例）+逐 trial 原始日志存证/`*R407` 后缀同号防混/结构钉+变异探针诚实留痕禁伪绿/概率腿未命中诚实登记条款 |

**既有裁定零重建声明**：本批零既有裁定文档 diff（唯一例外=ledger-retention-policy §3.4 成员行增补——W-04 落地义务，SSOT 明文授权的传导面非修订）；2513/2496/2497/2498/2509/2539 裁定结论如需修订走各自设计文档修订，不回写本批。

## plan 输入勘正（live 亲读核证 @ `bd7432c618`，裁定以本节为准）

| # | plan 行文 | live 事实（file:line @ `bd7432c618`） | 对裁定的影响 |
|---|---|---|---|
| K1 | NopJobTaskLogBizModel 锚区「:71-87 reportTaskLog/:86-96 validate/:98 区 saveEntry」 | 精确亲读（134 行）：`reportTaskLog` :73-86（@BizMutation :72/loadedTaskIds 只写不读 :77-82/逐条 saveEntry :83）；`validate` :88-98（三校验）；`saveEntry` :100-112；`fillRedundantColumns` :114-133（loadTask :116/loadFire :122/容忍缺失 catch :129-132）；`ERR_JOB_LOG_INVALID_ENTRY` import :27 | 裁定 G 的 W-01 上限校验落点与红① 钉内引用以精确值为准；fillRedundantColumns 容忍缺失路径 = W-04 三表独立谓词删除的「悬挂容忍」在位佐证 |
| K2 | 「NopJobFireBizModel :80-84 delete/NopJobTaskBizModel :25-28 delete」 | `NopJobFireBizModel.delete` :81-85（@Override :80）；`NopJobTaskBizModel.delete` :24-28；两处码=**JobApiErrors**（nop-job-api）`ERR_JOB_FIRE_DELETE_NOT_ALLOWED` :64（经 NopJobErrors extends JobApiErrors 继承引用——live import :35 亲证） | W-04 delete 共存口径以精确值为准；错误码归属核对（条件申报①）两码均在 api 叶=只读引用零触发 |
| K3 | JobScheduleStoreImpl 锚区「tryLockSchedulesForPlan :68-90/insertFireAndAdvanceSchedule :92-101/hasWaitingFire :360-374」 | 精确亲读（483 行）：`tryLockSchedulesForPlan` :66-82（@Transactional REQUIRES_NEW :66-67/max 时钟 :75/lockTime :76/逐实体写 lockDeadline :78-80/版本 CAS :81——**plan :68-90 区间尾段含 advanceScheduleAfterSkip :84-88 属过包含**）；`insertFireAndAdvanceSchedule` :90-114（:94 hasWaitingFire/:101 saveEntityDirectly/计数推进 :103-113）；`hasWaitingFire` :360-370（精确匹配 scheduledFireTime :364）；`findActiveFires` :372-386（limit-hit warn :381-384——`nop.job.query-limit-hit` 先例） | 裁定 D 的 post-fence 复查插入点（:101 之前）与红⑨ 钉锚以精确值为准；`hasWaitingFire` :364 精确匹配=去重防线缺口锚亲证 |
| K4 | JobTaskStoreImpl「认领 javadoc :88-96」 | javadoc block 实际 :88-99（「**或执行超时配置**」矛盾声明在 :90；check2 [P2-1] 段 :92-98）；`tryLockTasksForExecute` :102-114（CLAIMED+workerInstanceId+startTime :109-112——R7 时钟锚）；`fetchRunningTasks` RUNNING_LIKE+startTime 非空过滤 :117-141（注释 :123-127）；`countInFlightTasks` IN_FLIGHT 集 :158-163；`resetStaleWaitingTasks` 仅 WAITING :184-221（:189） | 裁定 H 的 javadoc 修正文案覆盖 :88-99 全块；W-10 墙钟时钟口径（startTime=claim 时刻）锚亲证 |
| K5 | JobTimeoutCheckerImpl 锚区「scanTaskTimeouts :278-294」 | 精确亲读（572 行）：`scanTaskTimeouts` 方法 :255-295（任务循环 :280-294/statusBefore :282/新标跳过 :286-288/tryMarkTimeout :290）；`resolveAliveWorkerIds` :297-324（namingService null→null :298-306/instances null/empty→null :310-312/**非空全 unhealthy→空集** :313-319/异常→null :320-323）；`tryMarkSuspiciousIfWorkerGone` :326-343（worker 在 alive 集→return :333-335——无顺延无复查亲证）；`markSuspiciousAsTimeout` :457-488（cancelRunningTask :461-471/duration=startTime 口径 :476-477/裁定①(c) 回传 :485）；`tryMarkTimeout` :490-563（SUSPICIOUS 分支 :497-499/**!isRunning 门 :502-504**/startTime null 早退 :506-509/超时配置三选一 :521-534[plan 340 §2.1 注释 :528-533]/deadline 判定 :536-540/裁定①(c) 回传 :562） | 裁定 A 复查插入点（:290 之前/:497-499 分支内两形态取其一——落款 scanTaskTimeouts 循环级）与裁定 W-10 门扩展点（:502-504）以精确值为准 |
| K6 | JobCompletionProcessorImpl「catch :109-116」 | 精确亲读（389 行）：`scanBatch` :97-124（fetchRunningFires :99/**catch :110-117[plan 340 §2.7 注释 :111-114]**/drain 信号 :123 `fires.size() >= batchSize`）；`completeSingleFire` :128 起（@SingleSession :127/REQUIRES_NEW :126）；scheduleEnabled nextFireTime 段 :193-202（fixed-delay :199-200）；`calculateFixedDelayNextFireTime` :339-349；`toTriggerSpec` :351-353 | 裁定 C 隔离层插入点（catch 区分流）以精确值为准；:123 drain 信号+无 cursor 重捞形态 = 批外新发现 BF-4（窗口饱和观察面）锚 |
| K7 | JobScheduleCounterReconciler「fetchSchedulesToReconcile :73-84」 | 精确亲读（129 行）：`scanBatch` :61-72（恒 false :71）；`fetchSchedulesToReconcile` :74-82（ENABLED+activeFireCount>0 :76-78/limit batchSize :79/jobScheduleId desc 无翻页 :80）；类 javadoc「最终一致」:31-32；batch-size setter :49-52；reconcileSchedule check2 [P3-1] 注释 :95-98 | 裁定 B 翻页改造点与 javadoc E5 落点以精确值为准 |
| K8 | JobWorkerScannerImpl「setter :163-165」 | `setMaxConcurrency` :163-166（@InjectValue :163）；count ceiling :183-192（仅 >0 :184）；resource check :194-203；drain 信号 :271；`executeTask` :274 起（永久滞留 CLAIMED 注释 :275-277；invokeAsync :341）；2496 看门狗 in-flight 登记区：`inFlightInvokeStartMillis` map + `scheduleInvokeWatchdog` + oldest 计算 :425-434（**该 map 的 size=JVM 在途 invoke 数——裁定 G 软上限复用载体**）；`handleExecutionResult` :439 起[F-m2 订正]（isConcurrentlyFinalized 早退 :443-445[F-m2 订正]——SUSPICIOUS 阶段即弃真实结果亲证） | 裁定 G 软上限复用载体（2496 registry）与 W-02×W-10 联合推演口径以精确值为准；BF-3（SUSPICIOUS 阶段丢弃语义）为裁定 A 残余面声明的机制基础 |

**核证范围声明**：16 站点文件全量亲读（service 3/dao 4/coordinator 4/core 3[含 NopJobCoreConstants 只读锚]/worker 1/local 2）；`_NopJobCoreConstants` 状态常量（WAITING=0/CLAIMED=10/SUSPICIOUS=15/RUNNING=20/TIMEOUT=50；FIRE WAITING=0~CANCELED=60，isTerminal ≥SUCCESS=30）/`JobTaskStateMachine`（isRunning 仅 RUNNING :62-64/isConcurrentlyFinalized 含 SUSPICIOUS :149-155/isFinished 含 SUSPICIOUS :113-127——**SUSPICIOUS 为 finished 但 resolvable-pending**）/`JobFireStateMachine`（ACTIVE=WAITING+DISPATCHING+RUNNING :26-30/isTerminal ≥30 :53-55）/`nop-job.orm.xml` UK :323-325/`NopJobScheduleBizModel`（enable/resume 试构建先例 :63-68/:102-107+recalculateNextFireTime :222-228+PAUSED 写点 :96）关联面亲读；HEAD `bd7432c618` 对 plan 基线 `80aac19cb3` 零产品 diff git 亲证。

## 工单→裁定映射与分组登记

| 工单（finding ID；判级） | 裁定 | 承载节 | plan 组 | 起草指令对照 |
|---|---|---|---|---|
| W-03（1.4-C7-003；P2） | A.1 | 复查+顺延唯一分支+stale 上限口径+空集语义+计数 metric+红③ seam | 组 A（coordinator 自愈正确性域） | 指令-A 同点位 |
| W-10（1.4-C7-011；P2——C10-006 宿主；与 W-03 同文件） | A.2 + H.2 | 同文件交界登记+联调矩阵[m-5]（A.2）；墙钟扩展+javadoc+gauge+plan 340 兼容（H.2） | 组 A W-03 面/组 C W-10 面（跨组同文件） | 指令-A/H 同点位 |
| W-05（1.4-C7-005；P2） | B | 翻页轮转唯一分支+limit-hit warn+淘汰取舍+javadoc 核对+红⑤ | 组 A | 指令-B 同点位 |
| W-08（1.4-C7-009；P2） | C | 终态化/PAUSED 分流唯一分支+错误码归属+计数升级+保存校验+FIXED_DELAY 处置+W-05 交互注记+红⑧ | 组 A | 指令-C 同点位 |
| W-09（1.4-C7-010；P2） | D | post-fence 值匹配复查唯一分支+放弃语义+红⑨ seam+C7-008 零代做+租约声明 | 组 A | 指令-D 同点位 |
| W-06（1.4-C7-006；P2） | E | 恢复 vs 声明二选一定夺（**声明形态**）+MJ-1 零改写+BF-1 硬边界+红⑥ 结构钉 | 组 B（本地终态与扫描 stall 域） | 指令-E 同点位 |
| W-07（1.4-C7-007；P2） | F | 观察型看门狗唯一分支+误报防线+2496 零弱化+收窄基线+红⑦ | 组 B | 指令-F 同点位 |
  - **判定谓词 conflations 声明[F-m4 评审补强]**：`lastScanEndTimeMillis < lastScanStartTimeMillis` 在「每轮抛异常但链持续推进」的链上恒真（异常闭合路径只写 failureTime 不写 endTime——2496 零触碰面不可加写）——看门狗侧补 `lastObservedScanCycleCount` 比较（scanCycleCount 推进即非 stall——零产品 diff 兼容），防「持续失败但活着」的链被持续误报为 stall。
| W-01（1.4-C5-002；P2——C10-008 宿主） | G.1 | 批上限键+单条校验+错误码归属+loadedTaskIds 处置[m-3]+C10-008 三面设计 | 组 C（容量账本与保留族域） | 指令-G 同点位 |
| W-02（1.4-C5-004；P2） | G.2 | 三候选组合唯一分支+联合推演记录+2498 对齐落款+红② | 组 C | 指令-G 同点位 |
| W-04（1.4-C7-004；P2——R01 三表保留族） | H.1 | R01 七款落款+锚列落名[m-4]+delete 共存+红④ 四件套 | 组 C | 指令-H 同点位 |
| 红①–红⑩ 总表+零回退钉全列+C10 注记+同文件联调矩阵 | H 附 | 驱动器总表 | 全批 | 指令-H 附 |

**分组确认声明**：plan Goals 预设「裁定 A–H+H 附按主题域分组，最终分组权威在设计文档映射表」——本文映射与 plan Goals/工单表/Phase 2 组 A/B/C 预设**逐项一致，零调整登记**。**组归属勘定（m-7 落款）**：plan 内部「组 A/B/C」为**同域实施分组**（Phase 2 实施次序与 focused 验证通道分组），**批次归属权威=batch-proposal §二.4 R4-07 行**（:122 单一批次行）——组 B/C 标签不构成第二批次，cra-index 收敛注以 finding ID 单批承载。跨组同文件交界：`JobTimeoutCheckerImpl` 承载 W-03（组 A）+W-10（组 C）——**同文件 diff 分组与实施次序联调矩阵见裁定 H 附第 4 节[m-5 补锚]**；`NopJobTaskLogBizModel` 承载 W-01 本体+W-04 邻接面（reportTaskLog 为 task_log 唯一写入路径——W-04 清理谓词不触写路径，零逻辑交界，diff 分组登记禁顺带）；`JobFireStoreImpl` 承载 W-08 支撑面（fetchRunningFires 零 diff——隔离层在 completion 侧）+W-09 先例对照面（:103-121 零触碰）——单文件双引用面零 diff 登记。W-08 与 W-09 同链绑定（dao 共享面同组实施）；W-01 与 W-02 同域绑定（裁定 G 同节）；W-04 与 W-10 同组绑定（裁定 H 同节）。

## Draft Review R1 修订吸收登记（M-1+m-1~m-8 逐项落款）

| 修订 | 内容 | 承载节 |
|---|---|---|
| **M-1** | 勘误 6 重写版（git 亲证修正）：审计基线 `335e77caec`→live 漂移按交界 commit 集逐文件核证（16 文件=11 零漂移+5 真实漂移[2496 1 件+2513 1 件+2539 3 件]）——本文 K1–K8 精确锚为其兑现落点 | 「plan 输入勘正」节 K1–K8 |
| m-1 | merge-adjudication 行锚订正：§2.3-4 :83/§4.4 :178[m-1 订正]（C10-006→C7-011 CLAIMED 肯定性回收断言+startTime 时钟）/§4.4 :179（C10-008→C5-002 上限三面） | 「既有裁定引用核对」节+裁定 H 附 C10 注记表 |
| m-2 | roadmap 8.47 行起草基线 :189、执行期已漂移至 :218 区——Phase 4 定位以 live 为准 | 零触碰边界总表（文档面行） |
| m-3 | loadedTaskIds 处置口径=**删除**（死代码移除）；「按 taskId 缓存 fillRedundantColumns 结果」使用候选归 plan Non-Blocking Follow-ups（优化轮，本批不扩面） | 裁定 G.1 决策 4 |
| m-4 | task_log **无状态列**（saveEntry :100-112 全字段亲证零 status 写点）——终态谓词不适用，**纯 logTime TTL** | 裁定 H.1 决策 2（锚列表） |
| m-5 | W-03/W-10 同文件（JobTimeoutCheckerImpl）diff 分组与实施次序联调矩阵补锚 | 裁定 H 附第 4 节 |
| m-6 | W-07 看门狗守 2497 §七 调度槽纪律（看门狗体=毫秒级内存检查，豁免判据声明） | 裁定 F 决策 2 |
| m-7 | 组 B 为 plan 内部同域分组——批次归属权威=batch-proposal R4-07 行 | 「工单→裁定映射与分组登记」节分组确认声明 |
| m-8 | MJ-1 钉修订义务覆盖 ReentrancyR406 :249-257 区全部 FAILED 自愈相关断言腿（含 sleep 后腿）——裁定 E 采恢复形态时的修订清单范围口径 | 裁定 E 决策 3（MJ-1 零改写声明的范围前提） |

## 裁定 A SUSPICIOUS/liveness 域（W-03）

### A.1 W-03：SUSPICIOUS 复查顺延+空集禁用本轮+liveness 观测计数（1.4-C7-003；←C7-long-running :159-195）

**决策**：**「(a) markSuspiciousAsTimeout 触达前复查本轮存活集，worker 已恢复则顺延本轮（SUSPICIOUS stale 上限兜底）+ (b) resolveAliveWorkerIds 对『非空但全 unhealthy』返回 null（与既有 null 禁用分支对齐）+ (c) 顺延/强转双向计数 metric」唯一分支**：

1. **复查形态（顺延分支）**：`scanTaskTimeouts`（:255-295）任务循环内，对进入本轮时已为 SUSPICIOUS 的 task（`statusBefore` 判据 :282 已在手），于 `tryMarkTimeout`（:290）触达 `markSuspiciousAsTimeout` 之前复查**本轮**已解析的 `aliveWorkerIds`（:278 每轮一次解析复用——零新增发现服务查询）：`task.getWorkerInstanceId() ∈ aliveWorkerIds` → **顺延本轮**（跳过强转，SUSPICIOUS 保持）；不在 alive 集 → 既有强转路径原样；（aliveWorkerIds==null 时无 alive 集可复查——按决策 2 不变式为准[F-M1 评审勘正]：本轮不复查不强转，既有 SUSPICIOUS 行仅 stale 超限强转）。**顺延受 stale 上限约束**：新配置键 `nop.job.coordinator.timeout.suspicious-stale-ms`（缺省 60000，≤0 装配期 fail-fast）——`now − task.updateTime > suspicious-stale-ms` 时**即便 worker 存活也强制转 TIMEOUT**（stale 强转计数留痕）。stale 锚=updateTime（SUSPICIOUS 行写入后无后续写点——worker 迟到结果被 isConcurrentlyFinalized 拒绝（JobWorkerScannerImpl :439-441 亲证）、强转前无 cancel 写——updateTime≈SUSPICIOUS 标记时刻，账龄事实列语义成立）。
2. **空集语义唯一分支（返回 null）**：`resolveAliveWorkerIds`（:297-324）对「instances 非空但全部 unhealthy」（:313-319 现返回空集）改为**返回 null**——与既有三处 null 分支（:298-306 namingService 未接线/:310-312 列表空/:320-323 异常）语义对齐：null=「发现服务本轮不可信，禁用 liveness 判定」。判定链不变式：null → 不做 tryMarkSuspiciousIfWorkerGone（:283-285 既有门）→ 无新标 SUSPICIOUS → 已有 SUSPICIOUS 行走 stale 上限兜底（顺延分支的 null 侧语义=本轮不复查不强转，**仅 stale 超限强转除外**——禁用轮不产生新误杀也不无限滞留）。
3. **计数 metric（观测面）**：checker 新增两个 AtomicLong 计数（镜像 2496 计数器家族形态）：`suspiciousDeferredCount`（顺延次数）+`suspiciousStaleForcedCount`（stale 强转次数）+getter 两枚——零平台 metric 注册接线（owner doc §五 观测行 E5 承载，与 2513 gauge activeCount 同形先例）。
4. **两轮确认窗语义保持论证（防矫枉过正——真实 worker 消失仍两轮回收）**：真实消失时序=轮 N worker 不在 alive 集 → SUSPICIOUS 标记；轮 N+1 worker 仍不在 → 复查不命中 → **强转路径原样**（两轮窗=1 scan-interval 语义不变，plan 340 侧回收契约零回退）。误杀时序闭合见理由段。stale 上限只作用于「worker 恢复但任务滞留 SUSPICIOUS」的第三态——该态下任务真实结果已被 SUSPICIOUS fence 丢弃（BF-3），滞留无收益，上限 60s（12 轮）为 fire 聚合与 retry 通道的收敛上界。

**理由（触发时序论证）**：缺陷时序——T1（checker 线程，轮 N）注册中心瞬态抖动（GC 停顿/心跳闪断/全 unhealthy 空集放大），`tryMarkSuspiciousIfWorkerGone` 把健康 worker 的在途 task 置 SUSPICIOUS；轮 N+1（默认仅 5s 后）worker 已恢复——`tryMarkSuspiciousIfWorkerGone` 命中 alive 集 return（:333-335，无恢复路径），棘轮检查（:286-288 statusBefore=SUSPICIOUS）放行 → `markSuspiciousAsTimeout` 无条件强转 + `cancelRunningTask`（:461-471）杀掉真实在途执行 + task 置 TIMEOUT → worker 真实完成的结果被丢弃/取消，fire 聚合失败 → failFireCount++ → retryPolicy 重跑 = 已成功工作重复执行。空集放大形态：T1′ 注册中心整体抖动一轮（全部 unhealthy）→ 空集非 null → 集群全部在途判「worker 全灭」。post-fix：T1″ 轮 N+1 复查命中 alive 集 → 顺延（cancel 不发生、强转不发生）；若恢复后执行真已完成，其结果虽仍被 fence 丢弃（owner doc 声明的 SUSPICIOUS 单向 fencing 语义零改写——见被否决备选 1），fire 经 stale 上限（60s）收敛 TIMEOUT → retry 通道按既有语义走——**误杀窗从「5s 内无条件终局+cancel」收窄为「瞬时抖动零终局；持续滞留 60s 有界终局」**，两轮确认窗对真实消失保持。

**红③ 钉形态（namingService 桩 seam——检出力定夺）**：`INamingService` 桩（测试内手工 stub——namingService 为 @Inject @Nullable 字段 :87-90 区，测试注入缝=既有装配形态非生产改动）——三腿：腿 (i) **棘轮复查腿**（当前即红[确定性——桩驱动]）：桩先返回「非空且不含目标 worker[含至少一健康他实例——F-m5 两相构造稳定化：修复后空集→null 不再产生标记]」（标记 SUSPICIOUS）→ 恢复返回含该 worker 的集合 → 断言 task 保持 SUSPICIOUS 非 TIMEOUT（pre-fix：强转=红）；腿 (ii) **空集禁用腿**（当前即红）：桩返回单元素列表 unhealthy=true → 断言本轮零新标 SUSPICIOUS（pre-fix：空集判死=红）；腿 (iii) **stale 上限腿**：恢复后推进时钟越 stale 上限 → 断言强转 TIMEOUT+stale 计数递增（防顺延无限滞留——修复面完整性腿）。`TestJobTimeoutChecker` 33 用例（含 plan 340 钉 :615）零回退；闩带 ≤10s。

**被否决的备选**：
- **SUSPICIOUS→RUNNING 恢复迁移**（worker 恢复时把 SUSPICIOUS 改回 RUNNING，重新开放 fence）：把「误杀面」换成「假活面」——注册缓存滞后/健康误报场景下 revert 后 RUNNING 行无人执行且退出 liveness 链管辖（alive 集含其 worker 时不再标记），仅剩超时配置兜底——未配超时=无限滞留 RUNNING（较 SUSPICIOUS 有界滞留更劣）；且 SUSPICIOUS 单向 fencing 为 owner doc 明文声明的设计取舍（「有意保留的中间态」），双向化=重开已声明契约。不采。
- **仅顺延无 stale 上限**：worker 恢复且执行已死（标记后进程真退出但 identity 残留注册缓存）场景下 SUSPICIOUS 无限滞留——fire 永不聚合、activeFireCount 永占（与 W-10 同族滞留面）。不采。
- **空集改「视同全 alive」**：语义反转荒谬（不健康实例反而抑制回收——真实全灭场景回收链失效）。不采。
- **每任务独立 resolveAliveWorkerIds 复查**（强转前逐 task 查询）：N 次发现服务查询/轮——发现服务抖动时放大为查询风暴；每轮一次解析复用已闭合复查语义。不采。

**零触碰边界**：`tryMarkSuspiciousIfWorkerGone`（:326-343）本体零 diff（SUSPICIOUS 标记语义与 fence 单向性零改写）；`markSuspiciousAsTimeout` 本体零 diff（复查在其**调用之前**于 scanTaskTimeouts 层分流）；2513 红线 2 五面零触碰（`resolveFromAuthority` 回传通道 :485/:562 写点保持）；plan 340 钉（:615）零回退；`TestJobTimeoutChecker` 33 用例零改写（红③ 为新测试类/新 @Nested 追加）；与 W-10 同文件 diff 分组（裁定 H 附第 4 节矩阵）。

### A.2 W-03×W-10 同文件交界登记（scanTaskTimeouts 调用链）

W-03 触及 :255-295 循环级（复查分流+顺延分支）+ :297-324（空集返回 null）+ 计数字段；W-10 触及 :490-563 方法级（!isRunning 门 :502-504 扩展）+ gauge 字段。**子区域不相交、共享测试文件**（`TestJobTimeoutChecker` 两腿追加）——修复互不覆盖（merge-adjudication §2.2 :65 C7-003 行「误杀 vs 不回收方向相反」独立案自洽）；详细联调矩阵归裁定 H 附第 4 节。

## 裁定 B reconciler 窗口域（W-05）

**决策**：**「cursor 翻页轮转唯一分支（`jobScheduleId < cursor` 递减窗口+欠批回卷）+ limit-hit warn 对齐先例 + updateTime 淘汰不采」唯一分支**：

1. **翻页形态**：`fetchSchedulesToReconcile`（:74-82）增加实例字段 cursor（`private String reconcileCursor`，初始 null=从头）：查询追加 `jobScheduleId < reconcileCursor`（cursor 非 null 时），排序维持 `jobScheduleId desc` + limit=batchSize；**回卷规则**：结果数 < batchSize（本方向窗口已尽）→ cursor 置 null（下轮从最高 id 重新开始——同时天然拾取窗口外新高 id schedule）；结果数 == batchSize → cursor=本批最小 jobScheduleId（下轮继续递减）。窗口覆盖收敛界=ceil(活跃 schedule 数/batchSize) 个周期——饥饿面闭合（窗口外漂移行至多 N/batch 个周期内入窗）。
2. **limit-hit warn（对齐先例）**：结果数 == batchSize 时 `LOG.warn("nop.job.query-limit-hit:method=fetchSchedulesToReconcile,limit={}", batchSize)`——文案与 `findActiveFires` 先例（JobScheduleStoreImpl :381-384 `nop.job.query-limit-hit`）同键同形（防护不对称消除）。
3. **类 javadoc「最终一致」核对结论（E5 联动）**：javadoc :31-32「对账器以扫描间隔为节拍最终一致」在 >batch-size 场景 pre-fix 不成立（审计复核亲证）；post-fix 翻页轮转使命题对**全部**活跃 schedule 成立——javadoc 补一句轮转收敛界声明（E5 owner doc :380「fire 计数自愈」句同步）——**声明与实现一致性由本修闭合，非撤销声明**。
4. **scanBatch 恒 false 维持**：每周期一批的 non-drain 语义零改写（刻意的 non-@SingleSession 例外与写回风暴防护 :30-32 注释零触碰）。

**理由（触发时序论证）**：缺陷时序——活跃 ENABLED 且 activeFireCount>0 的 schedule 数 > batchSize(100) 时，固定 desc 排序每轮取同一批最高 id；窗口外 schedule 的 activeFireCount 虚高漂移（并发版本冲突累积——completion 路径 @SingleSession 约束下无法路径内重试）永不入窗 → `shouldDiscard`（activeFireCount>0 判据）把真实空闲 schedule 永久判 DISCARD → **作业静默停摆**（reconciler 自身宣称要收敛的状态对其不可达——自愈机制不可达缺陷）。post-fix：窗口每轮递减推进、欠批回卷——漂移行在 ceil(N/batch) 周期内必入窗，对账收敛恢复全量有效；limit-hit warn 让「满窗」可观测（运维容量信号）。

**红⑤ 钉形态（确定性构造）**：batch-size 配置调小（`nop.job.coordinator.reconciler.batch-size` 配置键在位 :49-52——测试注入 batch=2）+ 构造 3 个活跃 schedule（id 递增）+ 窗口外最低 id schedule 注入计数漂移（activeFireCount=1 实际 0）→ pre-fix：跨多轮扫描漂移行计数永不收敛（当前即红[确定性——配置驱动]）；post-fix：翻页轮转 ≤2 轮内漂移行入窗且 activeFireCount 收敛为 actual + limit-hit warn 留痕断言；`TestJobScheduleCounterReconciler` 既有用例（1S 门控面基线）零回退；闩带 ≤10s。

**被否决的备选**：
- **updateTime 淘汰近期已对账项**（候选 c）：淘汰判据需附加查询过滤且 updateTime 被对账器自身写回刷新（自清洗语义混乱）；窗口边界不确定（淘汰集大小不可控）；翻页轮转已闭合可达性——淘汰策略零增量收益。不采。
- **仅 limit-hit warn 不翻页**（候选 b 单独）：可观测性改善但饥饿面本体不动（窗口外永不入窗）——非收敛修复。不采（warn 作为翻页的配套保留）。
- **升序翻页+双 cursor**：改排序方向需动既有 orderField 语义且双 cursor 状态机复杂；递减+欠批回卷单 cursor 等价覆盖。不采。
- **drain 循环改造**（scanBatch 单周期内翻完全部窗口）：单周期全量对账=DB 读放大+写回风暴防护语义破坏（:30-32 注释声明的单次写回纪律）；跨周期轮转天然节流。不采。

**零触碰边界**：`reconcileSchedule`/`countActiveFires` 本体零 diff（check2 [P3-1] 注释 :95-98 零触碰）；scanBatch 恒 false 语义零改写；batch-size/scan-interval 既有键零触碰（cursor 为新增实例字段非配置键——轮转状态不外暴）；`TestJobScheduleCounterReconciler` 既有用例零改写（红⑤ 为新测试类追加）。

## 裁定 C 触发器失败隔离域（W-08）

**决策**：**「配置类错误白名单分流：completion 侧终态化 fire（FAILED+新错误码）+ planner 侧 schedule 置 PAUSED——非配置类异常维持既有 WARN/ERROR 环 + 连续失败计数升级 + 保存路径试构建校验」组合唯一分支**：（终态化 FAILED 时已记账的 successFireCount/failFireCount 按 FAILED 重分类[F-m7 补明]）

1. **配置类错误判定口径（白名单，非全异常宽度）**：可识别为「调度配置数据损坏」的异常集 = `NopException` 且错误码 ∈ {`ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL`/`ERR_JOB_TRIGGER_LOOP_COUNT_EXCEED_LIMIT`/`ERR_JOB_TRIGGER_INVALID_ANCHOR_CONFIG`（JobCoreErrors :36/:39/:42 live 亲证）+ 新增 `ERR_JOB_TRIGGER_CONFIG_INVALID`（JobCoreErrors 新增——承载坏 pauseCalendarSpec JSON 等不可精确归类到三既有码的配置损坏面）}。**全 Throwable 宽度不采**（DB 瞬态故障终态化 fire=假死面——被否决备选 1）。判定落点=`TriggerSpecHelper.toTriggerSpec`（:22-36，parsePauseCalendars :81-86 坏 JSON 抛错点）与 `TriggerBuilder.buildTrigger`（:29-72，坏 cron :33/坏 calendar :57 抛错点）的调用层 catch。
2. **completion 侧终态化**：`completeSingleFire` 的触发器计算段（`calculateFixedDelayNextFireTime` :339-349 → toTriggerSpec）抛出配置类错误时，**不再任其逃逸到 scanBatch catch**（:110-117）——在 `completeSingleFire` 内捕获配置类错误：fire 终态化 FAILED + errorCode=`ERR_JOB_TRIGGER_CONFIG_INVALID` + ERROR 日志（终态写路径复用既有 fire 状态推进形态，schedule 计数同步 `activeFireCount` 减 1 对齐 `completeSingleFire` 既有尾段语义）→ `fetchRunningFires`（:73-81 RUNNING 过滤）不再重捞 → 5s 重试环阻断。**FIXED_DELAY 首暴露路径处置**：坏 spec 穿过 planning（planner 对 FIXED_DELAY 短路 :218-221）首次暴露即在完成路径——终态化后 schedule.nextFireTime 保持 null（计算失败不写下一轮）→ FIXED_DELAY schedule 零日志静默死亡形态消除为「FAILED fire+错误码+ERROR 日志」显式死亡。
3. **planner 侧 PAUSED**：`planSchedule`（:156-215）的 `calculateNextFireTime`（:217-229）抛配置类错误时，catch 分流：**schedule 置 PAUSED**（SCHEDULE_STATUS_PAUSED=20，写路径与 completion 既有 SCHEDULE_STATUS_COMPLETED 直写先例 :195 同形——checker/completion 直写状态为模块既有形态，非 BizModel 状态机越权）+ ERROR 日志（含错误码+scheduleId）→ `fetchDueSchedules`（ENABLED 过滤）不再触达 → 60s 隐式节流环阻断、「该 schedule 从此不再产出 fire」的静默停摆升格为显式 PAUSED+告警。PAUSED 语义=用户可恢复（resumeSchedule 既有通道，试构建校验通过方可 resume——(c) 项闭环）。
4. **非配置类异常+计数升级（候选 b）**：既有 catch 结构零弱化（completion :110-117 WARN 保留/plan 340 §2.7 注释零触碰；planner :128-133 ERROR 保留）——新增 completion 扫描器级连续失败计数（`consecutiveCompleteFailCount`，成功即清零——镜像 2496 计数器家族形态+getter），超阈值（新配置键 `nop.job.coordinator.completion.fail-escalate-threshold`，缺省 10，≤0 禁用升级——观测性旋钮非 fail-fast 面）升级 ERROR+计数 getter 暴露。**边界声明**：配置类错误已分流终态化，计数升级只承载非配置持久失败（DB 长故障等）—— graduated observability 非新失败语义。
5. **保存/update 路径试构建校验（候选 c）**：`NopJobScheduleBizModel` save/update 路径新增触发器试构建（`TriggerSpecHelper.toTriggerSpec` + 触发链构建——对齐 enable/resume 先例 `recalculateNextFireTime` :222-228 的试算形态，覆盖 cron 解析+anchor 校验+calendar 构建 :29-72 全失败面）——配置损坏在入口被 typed 拒绝（`ERR_JOB_TRIGGER_CONFIG_INVALID`），不再流入 DB。**落点登记**：`NopJobScheduleBizModel` 为**第 17 个 main 文件**（plan 站点清单外——批外新发现 BF-1 登记+零触碰边界总表增补行；save/update 为 CrudBizModel 继承方法，校验挂接其覆写入口，diff 面=校验方法+两处覆写挂点）。
6. **与 W-05 停摆交互注记**：stuck fire 永久滞留 RUNNING → activeFireCount 恒>0 → DISCARD 卡死（与 W-05 窗口饥饿同流）——本修终态化清空 stuck fire 源头，W-05 翻页保证既有漂移行入窗对账——两修正交闭环（隔离防新增滞留、翻页保存量收敛）；隔离后 reconciler 窗口语义无新交互（终态化走 completion 既有计数推进段，非新写点）。

**理由（触发时序论证）**：缺陷时序——T1（completion 线程）坏 pauseCalendarSpec JSON 在 `parsePauseCalendars`（:81-86）抛 NopException → `completeSingleFire`（REQUIRES_NEW :126-127）回滚 → fire 未终态化 → `fetchRunningFires` 每 5s 重捞 → 无限重试环（17,280 WARN/天/fire）+事务/DB 读消耗 → activeFireCount 恒>0 → DISCARD 永久卡死。T2（planner 线程）坏 cron 配置 → `tryLockSchedulesForPlan` 已写 nextFireTime=lockDeadline（REQUIRES_NEW 独立提交）→ `planSchedule` 抛错被 :128-133 捕获 ERROR → 60s 后重新 due 再失败——隐式节流环+静默停摆。post-fix：T1′ 配置类错误 → fire FAILED 终态化（重捞面消除）→ 显式死亡；T2′ → schedule PAUSED（due 面消除）→ 显式暂停可恢复；非配置类失败维持既有重试语义+计数升级可观测——**失败语义三分类显式化**（配置损坏=隔离；瞬态=重试+升级；正常=通过）。

**红⑧ 钉形态**：坏 pauseCalendarSpec JSON 注入（测试内直接构造坏 spec 列）→ completion 轮转 → pre-fix 断言：fire 滞留 RUNNING+WARN 计数增长+无终态（当前即红[确定性——注入驱动]）；post-fix 断言：fire=FAILED+errorCode 判别 `ERR_JOB_TRIGGER_CONFIG_INVALID`+fetchRunningFires 不再重捞（下轮零 WARN 增量）+planner 侧 PAUSED 腿（坏 cron 注入→schedule=PAUSED+ERROR 留痕）+保存路径拒绝腿（坏 spec save→typed 拒绝）；`TestJobCoordinatorScanner` 27 用例零回退；闩带 ≤15s。

**被否决的备选**：
- **全异常宽度终态化/PAUSED**（catch Exception 分流）：DB 瞬态故障（连接抖动）被误判配置损坏 → 健康任务假死（终态化不可逆+PAUSED 需人工 resume）——隔离语义放大故障面。不采（白名单口径为唯一分支）。
- **仅 PAUSED 不终态化（两侧统一 PAUSED）**：completion 侧的 stuck fire 已存在（非 schedule 属性问题）——不终态化则 fire 仍 RUNNING 重捞（重试环本体不动）；PAUSED 对「已产生 fire 的完成路径」无隔离作用。不采（两侧分流为唯一解）。
- **仅终态化不 PAUSED（planner 侧也只记日志）**：planner 侧无 fire 可终态化（fire 未产生）——60s 环+静默停摆原样；PAUSED 是 planner 侧唯一隔离载体。不采。
- **死信表/quarantine 机制**：新增表+orm+清理链全套=超工单半径（audit 修复方向无此候选）；FAILED fire+错误码即模块既有账本语义内的「死信」。不采。
- **错误码落 JobApiErrors**（新码进 api 叶）：触发条件申报①（plan-first 四件链）——JobCoreErrors（非 api 叶）承载同等语义零申报成本（plan PA 段预设口径）。不采 api 落点（条件申报①零触发落款）。

**零触碰边界**：completion 既有 catch 结构与 plan 340 §2.7 注释（:111-114）零弱化零删除（分流层为新增前置 catch）；`completeSingleFire` 既有终态推进/计数段零逻辑改写（隔离层只捕获配置类分流）；planner `planSchedule` 既有分支序（isTriggerExhausted/shouldDiscard/shouldRecovery/shouldOverlay/shouldParallel :166-214）零重排（配置类 catch 在 calculateNextFireTime 调用层分流）；`TriggerSpecHelper`/`TriggerBuilder` 本体零 diff（白名单判定在调用层）；`JobFireStoreImpl` fetchRunningFires（:73-81）零 diff；`NopJobScheduleBizModel` enable/resume/pause 既有校验链零改写（试构建为新增挂点）；W-09 先例 `insertTasksAndMarkFireDispatching`（:103-121）零触碰（同链绑定面）。

## 裁定 D planner fence 域（W-09）

**决策**：**「insertFireAndAdvanceSchedule 插入前 post-fence 值匹配复查（requireEntityById 重取 fresh schedule + `fresh.nextFireTime == 调用方实体.nextFireTime`（=本 planner 写入的 lockDeadline））不等则放弃本槽（WARN+零插入零计数推进）」唯一分支**：

1. **复查形态（dispatch 先例投影，零接口签名变更）**：`insertFireAndAdvanceSchedule`（:90-114）首段（:94 hasWaitingFire 之前）新增：`NopJobSchedule fresh = scheduleDao().requireEntityById(schedule.getJobScheduleId())`（REQUIRES_NEW 事务内重取——dispatch 先例 :104 同形）+ 值匹配判定 `Objects.equals(fresh.getNextFireTime(), schedule.getNextFireTime())`——调用方实体的 nextFireTime **就是**本 planner 经 `tryLockSchedulesForPlan` 写入的 lockDeadline（:78-80 逐实体写后实体持锁值——planner 快照注释 :90-94 亲证），故值匹配=「我的 fence 仍持有该 schedule」的 CAS 复查，**无需改 IJobScheduleStore 签名**（先例 :103-115 的 requireEntityById+状态复查形态投影，接口面零 diff）。overlay/recovery 两个兄弟入口（:116-160/:162+）同面同修（同一 fence 窗口的三条插入路径——白名单不完整即窗口残留）。
2. **放弃分支语义（与 misfire 交界显式化）**：值不匹配 → `LOG.warn("nop.job.plan-slot-abandoned:fence-expired,scheduleId={},expected=…,actual=…")` → **本槽放弃：不插 fire、不推进 fireCount/activeFireCount/lastFireTime、不写 nextFireTime**——返回（REQUIRES_NEW 事务空提交）。槽位覆盖论证：B 接管时以 A 的 lockDeadline 为 due 快照插入 fire2（scheduledFireTime=A 的 lockDeadline）——放弃的槽由 fire2 承载（迟到 ≤lock-timeout 窗口的触发，非丢失触发）；「不重排」语义=放弃分支不做补偿性插入（owner doc「不支持 Misfire 补偿」声明覆盖该延迟语义——租约残余风险 E5 显式声明）。
3. **红⑨ seam 检出力口径（确定性构造）**：`nop.job.coordinator.planner.lock-timeout-ms`（缺省 60000——owner doc §五 :408 行）测试调小（如 200ms）+ planner 线程可控停顿（latch）= 端到端形态；**确定性主形态=store 级直构**（无需真实 60s 停顿）：测试依次真实调用 `tryLockSchedulesForPlan`（A 锁，实体持 lockDeadline）→ 模拟 B 接管（再次 tryLock+`insertFireAndAdvanceSchedule` 插入 fire2[不同 scheduledFireTime]）→ 以 A 的过期实体调用 `insertFireAndAdvanceSchedule`（fire1, originalDue）→ pre-fix：fire1+fire2 双存（UK 三列不同时间戳不拦——orm.xml :323-325 佐证锚）=当前即红[确定性]；post-fix：A 放弃（单 fire+WARN 留痕）=绿。latch 变体作为链路级补充腿（真实两 planner 线程交错）。变异腿必做（摘除复查→红→复修→绿）。
4. **C7-008 时钟源零代做落款**：值匹配比较的两个 Timestamp 均由既有 max 时钟路径写入（lockDeadline :75-76/DB 侧 fresh 重取）——复查不引入新时钟源、不重开 2513 注记的时钟源结论（computeDeadline 面零触碰——P3 相邻 C7-008 零代做纪律）。
5. **租约语义文档声明（E5）**：owner doc §五 `lock-timeout-ms` 行（:408 区）补租约语义句——「lock 为乐观租约：TTL 过期后他节点接管，滞后 planner 的插入前 fence 复查失败即放弃本槽（fire 由接管方承载，触发延迟 ≤TTL——非 misfire 补偿语义）」。

**理由（触发时序论证）**：缺陷时序——双 coordinator：planner A 锁定（v1→v2，nextFireTime=originalDue+60s）→ A 在 planSchedule 内停顿 ≥60s（GC/VM 冻结）→ B fetch due（nextFireTime≤now 命中 A 的过期锁）→ B CAS v2→v3 + 插入 fire2（scheduledFireTime=A.lockDeadline）→ A 苏醒以过期实体续跑：`hasWaitingFire(S, originalDue)` 精确匹配查不到 fire2（:364 时间戳不同）→ `saveEntityDirectly(fire1)`（:101）前无任何复查 → fire1 落库 → UK(jobScheduleId,scheduledFireTime,triggerSource) 对不同时间戳不拦 → **同一逻辑触发槽业务副作用执行两次**。post-fix：A′ 插入前重取 fresh（nextFireTime=B 的 lockDeadline ≠ A 的）→ 放弃+WARN → 双执行面闭合；A 的计数实体不写（版本冲突面同步消除——pre-fix 的 updateWithRetry 重载重试反而在 B 写后成功落账，post-fix 放弃分支使该重试面不触达）。

**被否决的备选**：
- **版本匹配替代值匹配**（fresh.version == schedule.version）：completion/管理面任何并发写（含合法计数推进）都会误判 fence 失效 → 放弃率虚高（plan 风险段「放弃率上升面」）；值匹配精确刻画「fence 持有」语义（只认锁值被替换）。不采。
- **插入后校验失败取消 fire**（候选 b）：先插后删=副作用窗口（fire 已可见于 dispatch 扫描）+补偿删除与并发 dispatch 竞态（删已认领 fire 的防线性问题）——插入前复查无副作用窗口。不采。
- **hasWaitingFire 放宽为 scheduleId+triggerSource 匹配**（时间戳维度放宽拦重）：把去重防线从「精确槽」放宽到「schedule 级」——正常并行/重叠策略（shouldParallel）语义被误伤（同 schedule 多活跃 fire 合法形态）。不采。
- **lock 续租机制**（planner 停顿期间心跳续租）：新增租约协议（心跳+TTL 推移+接管仲裁）=平台设施级设计（plan Non-Blocking Follow-ups 已登记「认领/计划租约统一设施候选归平台设施轮」）——超工单半径。不采（文档声明承载残余面）。

**零触碰边界**：`tryLockSchedulesForPlan`（:66-82）本体零 diff（fence 写入语义零改写）；`hasWaitingFire`（:360-370）零 diff；dispatch 先例 `insertTasksAndMarkFireDispatching`（:103-121）零触碰（投影源非修改对象）；`updateScheduleWithRetry`/orm_unload 重载重试语义零触碰（放弃分支使其不触达，非改写）；orm.xml UK（:323-325）只读佐证零 diff；`insertManualFire` 邻接面零触碰（MANUAL 源无 fence 窗口——撞 UK 显式报错为既有语义，audit 残留问题行口径）；C7-008 零代做。

## 裁定 E 本地终态语义域（W-06）

**决策**：**「二选一定夺 = 声明形态（候选 b）——owner doc 显式声明『单次执行失败=作业永久终止（FAILED 终态）』+invoker 自行兜底指引；MJ-1 钉零改写；产品代码零 diff」唯一分支**：

1. **定夺理由（声明形态为唯一分支的论证）**：(a) **产品定位对齐**——本地模式 owner doc §一/§1.4 明文「作业定义只存在于内存，重启即丢失」「不支持 Misfire 补偿、阻塞策略、超时控制」——重试/退避/自愈与「不支持 Misfire 补偿」同族能力，恢复形态=向轻量嵌入式模式导入分布式调度器语义（retryPolicy/退避上界/failedCount 上限/失败风暴防护全套），与声明定位冲突；(b) **2539 语义新鲜收口**——FAILED 终态不自愈语义刚由 2539 W-08（C10-004 三锚点+MJ-1 谓词）收口成文（:416-419 注释明示「FAILED is terminal in local mode — no self-heal」live 亲证），恢复形态=同批语义反转（MJ-1 钉 :249-257 区[m-8 口径]全部 FAILED 自愈相关断言腿同 diff 修订——2538 勘误 2 通道合法但成本/风险不对称：本批最高交互面四方联动[guard/resumeJob/fireNow/MJ-1]任何漏推演即语义回归——plan 风险段原文）；(c) **缺陷本体=声明缺口**——审计判级核心是「owner doc 未声明这一反直觉语义」（audit 边界声明对照行：声明了「不支持 Misfire 补偿」但未声明「单次失败=永久终止」），声明形态直接闭合缺陷本体；(d) **爆炸半径有界**——本地模式无持久化（重启即重建），FAILED 终态的最坏后果=进程生命周期内该 job 停摆且状态可查（getJobState=FAILED+ERROR 日志）——声明使运维预期对齐后即为诚实边界；(e) **恢复通道候选已登记**——plan Non-Blocking Follow-ups 预留「W-06 声明形态的后续自愈增强候选归后续优化轮或 R4-08+」——不关闭未来演进，只锁定本批形态。
2. **声明落点（E5 同批承载，Phase 3 落账）**：owner doc §1.4 注意事项补显式契约句——「**终态语义（CRA 8.47 R4-07 W-06）**：本地模式 job 单次执行异常（invoker 抛错/返回 ERROR 结果/触发器计算失败）即置 FAILED 终态：不再排程、`resumeJob`/`fireNow` 对 FAILED 恒拒绝（返回 false）、无自动恢复——恢复只能重启进程重建 job。**长周期 job 须在 invoker 内自行兜底瞬时异常**（捕获-降级-返回正常 JobFireResult），或改用分布式模式（retryPolicy 通道）」；§3.3 触发器类型节相邻处按需互引。
3. **MJ-1 联动定夺（对齐纪律第二条专项——一次定夺）**：**零改写**——`TestLocalJobSchedulerReentrancyR406` :249-257 区全部 FAILED 自愈相关断言腿（含 sleep 后腿，m-8 口径）与既有断言零改写零移动（「FAILED 终态不自愈：fireNow 恒拒绝+fireCount 恒定」谓词与新语义一致——声明形态未改变任何运行时行为）；2539 C10-004 三锚点整体零回退。
4. **BF-1 零触碰硬边界落款（对齐纪律第六条）**：W-06 声明形态产品代码零 diff → :500-502（handleResult 尾部 guard 仅排除 COMPLETED/FAILED）/:361（scheduleNext 无条件置 WAITING）/:461-462（abandonAfterInterleave javadoc 认知锚）**零触碰硬边界**（closure audit git 零 diff 核对）——BF-1（SUSPENDED 被完成回调复归）维持 2539 登记 watch-only，Successor Path 维持（R4-08 及其后余量轮或 R-G），本批不承接不改变去向。
5. **红⑥ 钉形态（声明形态=结构文档钉+变异腿，2537 红⑥ 诚实留痕形态）**：断言 owner doc §1.4 含 W-06 终态语义契约句（契约关键词锚断言：「FAILED 终态」「resumeJob」「fireNow」「重启」锚词）+ §3.1 异步性契约句相邻互引在位；pre-fix 旧措辞在位=结构红；post-fix=绿；变异腿（还原/删除契约句→钉红→复归→绿）诚实留痕禁伪绿；**行为腿不采**（运行时行为零变化——2539 红⑨⑩ 同形纪律）；闩带 ≤5s。
6. **2539 产物零回退落款**：六面产物（getJobDetail 持锁/addJob raced/ctx 快照/startJobUnlocked catch 收宽/settleExecution/abandonAfterInterleave）+两阶段四产物零回退（红线 1）——本裁定零产品 diff 使该落款平凡成立（closure audit 全文件 git 零 diff 核对 LocalJobScheduler/BeanMethodJobInvoker——**两文件本批零 diff 为裁定 E 的可检证据**）。

**被否决的备选**（恢复形态候选 a 整体不采+子项留档）：
- **FAILED 后按 trigger 重排带退避（failedCount++）**：见决策 1 论证 (a)–(e)；退避策略/failedCount 上限/失败风暴防护为全套新语义面；四方联动（:500 guard FAILED 排除语义/resumeJob 拒绝面/fireNow 拒绝面/MJ-1 钉修订）任何一方漏推演即回归——成本与本地模式定位不成比例。不采（R4-08+ 优化轮候选）。
- **仅 resumeJob 放开 FAILED 判据**（最小恢复通道）：resumeJob 语义=「恢复被暂停的调度」（SUSPENDED→WAITING :224-226），FAILED 语义=「执行失败终局」——放开判据混同两语义且 fireNow/:500 guard/MJ-1 联动面依旧存在（半修）。不采。
- **声明弱化形态**（只在 §五 配置表加注）：§1.4 是终态语义的声明归属位（audit 建议原文「至少应在 owner doc 显式声明」指向行为契约节）——弱落点不兑现契约句义务。不采。

**零触碰边界**：`LocalJobScheduler`/`BeanMethodJobInvoker` 产品代码零 diff（本工单为声明面修复——硬边界含 BF-1 面 :500-502/:361）；2539 六面+两阶段四产物零回退；MJ-1 钉（ReentrancyR406 :249-257 区）零改写；`TestLocalJobScheduler`/`TestLocalSchedulerResultContract`/R406 九钉零回退；owner doc §1.4 既有条款零删除（新增契约句为追加）；条件申报①–⑤全零触发（零错误码/零 beans/零 orm/零载体/零生成物）。

## 裁定 F scanner stall 域（W-07）

**决策**：**「观察型 stall 看门狗唯一分支——AbstractBatchScanner 内建独立周期看门狗任务（observe-only：在途超阈 WARN+stale 计数+最老在途年龄 gauge getter，永不取消/中断主链）+ 默认禁用配置键 + JDBC 超时配置文档建议」**：

1. **看门狗形态（候选 a/b 合一体）**：`startScanning`（:78-99）在主链 `scheduleWithFixedDelay` 之外新增同载体看门狗任务（同一 `getExecutor()` 载体——`globalTimer().executeOn(globalWorker())` C 类准绳形态，scheduleWithFixedDelay 周期=scanIntervalMs）；看门狗体 = volatile 读判定：`running && lastScanStartTimeMillis > 0 && lastScanEndTimeMillis < lastScanStartTimeMillis`（在途未闭合）且 `now − lastScanStartTimeMillis > stallThresholdMs` → **WARN 一次/轮**（跨轮持续滞留时每轮 WARN 可观测但不风暴——观察语义）+ `stallDetectedCount.incrementAndGet()` + 最老在途年龄 getter 刷新（`oldestInFlightAgeMillis` volatile，主链出口 :153 清零）；主链被挂起时看门狗线程独立存活（globalWorker 池 30 线程，单链滞留不耗尽池——载体假设成立依据=2498 §3.1.1 共租面声明）。**m-6 落款**：看门狗体为毫秒级内存检查（volatile 读+计数+日志，零 IO）——2497 §七 调度槽纪律 + §3.1.1 豁免判据 (a)(b)(d) 满足，载体合规。
2. **配置键（默认禁用）**：`nop.job.scanner.stall-watchdog-ms`（缺省 **0=禁用**——2496 `invoke-timeout-ms|0` observe-only 先例同形；≤0 禁用不 fail-fast——观测旋钮语义；>0 启用）——默认关闭=零行为变化（WARN 噪声为零），诚实 opt-in 观测面。
3. **误报防线**：observe-only=误报无害化（不 cancel/不 interrupt/不状态迁移——2496 MA-06 observe-only 纪律投影）；阈值语义=「单轮在途超过 N ms」——合法长批次（大 backlog drain）超阈产生 WARN 但无任何行为后果（运维容量信号非故障信号——stallDetectedCount 与 WARN 文案声明观察语义）；主链出口（scanOnce 正常/异常闭合点 :153/:155）刷新时间戳，看门狗判定自动复位——无状态残留面。
4. **2496 绝缘+指标族+守卫零弱化落款（红线 3——失败语义不变证明）**：修复面=挂起检测（新增看门狗旁路任务），主链 Throwable 边界（:154-158）/onScanCycleFailed/计数器/startScanning 拒绝真实化（:90-98）/W-05 CAS 守卫（:138-142）逐字零改写——看门狗在绝缘边界**之外**观测（只读 volatile 字段），失败语义与链状态机零交集；**勘误 3 收窄基线引用**：存活指标族六字段（:70-76/:224-251 区[F-m1 订正——类尾部 getters 区]）为收窄后修复面的既有基线（「有原始字段无 stall 判定」——本修补判定层非重建指标层）。
5. **JDBC 超时文档建议（E5）**：owner doc §六（绝缘契约节）补建议句——「扫描器链依赖平台连接池 socket/query timeout 配置；未配置时挂起形态由 `nop.job.scanner.stall-watchdog-ms` 观测面暴露（>0 启用）——观测不替代超时配置」。

**理由（触发时序论证）**：缺陷时序——T1（globalWorker 线程）`doScan→scanOnce→scanBatch` 内 ORM/JDBC 调用在 DB 网络半开（socket/query timeout 未配）无限期阻塞 → fixed-delay 链停在同一轮：`lastScanStartTimeMillis` 已写（:146）、`lastScanEndTimeMillis` 永不更新、零异常零日志——onScanCycleFailed 只覆盖抛异常面。planner 挂死=全平台调度停摆；checker 挂死=自愈链停摆（与 W-03 叠加：回收链本身不可观测）。模块内零信号（2496 指标族为被动 getter 无主动判定）。post-fix：T2（看门狗线程，独立周期）读 volatile 对（start 未闭合+超阈）→ WARN+计数+年龄暴露——挂起形态从零信号变显式可观测（运维可介入：重启/修超时配置）；主链语义零变化（挂起依旧挂起——诚实边界：观测面非治愈面，治愈=JDBC 超时配置声明）。

**红⑦ 钉形态（latch 挂起确定性红）**：测试子类 scanBatch 内 latch 阻塞（主链挂起）+ 看门狗阈值调小（测试注入 >0 小值）→ pre-fix 断言：超阈后无任何 stall 信号（stallDetectedCount==0 且无 WARN 载体——**当前即红[确定性——latch 挂起+阈值注入]**，断言面按勘误 3 收窄口径精确化为「在途超阈无信号」非「无指标」）；post-fix 断言：stallDetectedCount≥1+oldestInFlightAge getter 反映在途年龄+放闸后（latch 释放）判定自动复位+stallDetectedCount 不再增长；`TestAbstractBatchScanner` 9 用例+`TestScannerInFlightGuardR406` 零回退；闩带 ≤10s 且收尾必放闸（挂起的 scanBatch 线程必须释放——防测试自身挂死）。

**被否决的备选**：
- **主链内联 stall 判定**（scanBatch 出口自查）：主链挂起时出口永不达——内联判定在挂起形态下自身不执行（盲区即缺陷本体）；独立周期看门狗是唯一能在挂起时存活的观测位。不采。
- **看门狗超时中断主链**（Future cancel/Thread interrupt）：watch-dog-to-kill 形态——中断 ORM 阻塞调用的语义不可靠（JDBC 驱动中断响应性各异）+ 违反 observe-only 先例（2496 MA-06 纪律：观测不终结）；误报时中断健康长批次=行为缺陷。不采。
- **默认启用看门狗**（非零缺省）：W-07 为观测面工单——默认 WARN 对未预期部署引入新噪声源（合法长批次部署首日 WARN 洪水）；2496 invoke-timeout-ms|0 默认禁用先例同形。不采。
- **平台 metric 注册接线**（gauge 上报 metrics 体系）：模块内无 metric 注册基建（2513/2496 先例=getter+owner doc 声明形态）——上提观测设施=条件申报④相邻面。不采（getter 形态承载）。

**零触碰边界**：主链 scanOnce/startScanning/doScan/scanBatches 本体零逻辑 diff（看门狗为 startScanning/stopScanning 内新增旁路任务注册+取消配对——配对义务：stopScanning :101-107 同步 cancel 看门狗 future 防泄漏）；2496 绝缘+指标族+守卫产物零弱化（红线 3）；C2-006 P3 面（registeredCursors/newCursor）零触碰禁顺带；既有配置键零触碰（stall-watchdog-ms 为新增键）；`TestAbstractBatchScanner`/`TestScannerInFlightGuardR406` 零改写（红⑦ 新测试类追加）。

## 裁定 G 边界上限域（W-01/W-02）

### G.1 W-01：reportTaskLog 批上限+单条校验+loadedTaskIds 处置（1.4-C5-002；C10-008 宿主）

**决策**：**「批上限配置键+单条长度校验键（均 @InjectValue 注入、≤0 装配期 fail-fast）+ 超限 typed 显式失败 + loadedTaskIds 删除[m-3]」唯一分支**：

1. **批上限**：新配置键 `nop.job.log.report-max-batch-size`（缺省 **1000**）——`reportTaskLog`（:73-86）入口判 `entries.size() > max` → typed 拒绝（新错误码 `ERR_JOB_LOG_BATCH_EXCEED_LIMIT`，JobCoreErrors 新增——非 api 叶零申报）。缺省值论证：正常 worker 上报为小批量（JobLogReporter 单条 fire-and-forget 为审计亲证常态），1000 对正常面零误伤且把单事务物化上界从「HTTP body 上限间接约束」收敛到模块级显式声明（SSOT delete-batch-size|1000 同量级先例——非 R01 参数故不入 SSOT，模块内定夺+owner doc 声明）。
2. **单条校验**：新配置键 `nop.job.log.entry-max-chars`（缺省 **65536**）——validate（:88-98）扩展：logMessage 长度或 logPayload 序列化长度（`JsonTool.stringify` 后）超阈 → typed 拒绝（**复用既有 `ERR_JOB_LOG_INVALID_ENTRY`+`ARG_TASK_LOG_ID`**——「Invalid task log entry」语义精确覆盖单条超长，live import :27 零新增）。缺省值论证：64KB 单条上限覆盖正常日志行数个量级、阻断「任意长字符串+任意嵌套 Map」的物化峰值面。
3. **fail-fast 口径**：两键 `<=0` 装配期 fail-fast（`NopException`）——上限键=防护面，静默禁用即「三无状态延续」（有校验存在但被配置关闭=伪防线）；负值/零均为配置错误。
4. **loadedTaskIds 处置[m-3 落款]=删除**：:77-82 只写不读死代码面（审计复核：contains 对批内重复 taskId 会命中但仅门控冗余 add 无行为影响）——整段删除零行为变化；「按 taskId 缓存 fillRedundantColumns 结果」使用候选归 plan Non-Blocking Follow-ups（优化轮——本批不扩面）。
5. **C10-008 三面断言设计（同 diff——上限三面一套禁拆分）**：(i) **超限拒绝腿**：batch-size 配置 2 + 3 条上报 → typed `ERR_JOB_LOG_BATCH_EXCEED_LIMIT` + 零落库（当前即红[确定性]）；(ii) **默认限值不误伤腿（batch=2 最小对照）**：配置 2 + 2 条正常上报 → 全绿落库（校验逻辑边界精确性——防「上限存在但正常批被拒」过紧实现）；(iii) **单条超长腿**：logMessage 超 entry-max-chars → typed `ERR_JOB_LOG_INVALID_ENTRY` + 该批零落库（事务性：校验先于任何 save——首条校验失败即整批拒绝，零部分写入）。宿主 `TestNopJobTaskLogBizModel` 4 用例零回退叠加非替代（勘误 8 身份变化登记兑现）。

**理由（触发时序论证）**：缺陷时序——T1 单请求携带 N 条 entry（N 任意）→ body 整体物化请求线程堆（峰值≈N×单条大小）→ 循环逐条 saveEntry（每条 fillRedundantColumns 1-2 次 DB load :116/:122——事务内 ORM 缓存随 N 增长）→ 单事务时长随 N 线性膨胀 → 恶意/缺陷 worker 放大 coordinator GC 与长事务面。唯一既有约束=模块外 HTTP body 上限（非本单元保证）。post-fix：T1′ 批上限先拒（物化后但落库前拦截——事务/DB 面零消耗）；单条上限在 validate 段拒绝（先于 saveEntry）——两道闸均在 DB 写之前；正常小批量（≤1000 且单条 ≤64KB）行为零变化。

**被否决的备选**：
- **loadedTaskIds 真正使用**（taskId 缓存 fillRedundantColumns 结果）：缓存=新增实例状态（生命周期/失效面）+收益仅在批内重复 taskId 场景（JobLogReporter 正常路径单条上报零收益）——优化轮候选非本批修复面。不采[m-3]。
- **流式/分批处理**（替代整批物化上限）：入口 List 已由框架反序列化物化（模块内不可改流式）；分批子事务=事务语义拆分（部分成功面）——上限拒绝为诚实边界。不采。
- **批上限码复用 ERR_JOB_LOG_INVALID_ENTRY**：批级拒绝非「单条无效」语义——复用将模糊错误面判别（运维无法区分批超限与单条损坏）；JobCoreErrors 新增=非 api 叶零申报。不采复用（单条面复用、批面新增的分流定夺）。
- **上限码落 JobApiErrors**：触发条件申报①（plan-first 四件链）——JobCoreErrors 承载零申报。不采（条件申报①零触发落款）。

**零触碰边界**：`saveEntry`/`fillRedundantColumns` 本体零逻辑 diff（校验为入口前置层）；W-04 同文件邻接面零交叉（清理谓词不触本文件——diff 分组登记禁顺带）；`ERR_JOB_LOG_INVALID_ENTRY` 既有定义零 diff（nop-job-api 零触碰）；JobCoreErrors 仅追加码行（既有码零改写）；`TestNopJobTaskLogBizModel` 4 用例零改写（C10-008 同 diff 新钉叠加）。

### G.2 W-02：worker 双闸门默认旁路——三候选组合+联合推演（1.4-C5-004；2539 W-10 容量束联调承接）

**决策**：**「(a) JVM 在途软上限（新配置键、默认关闭）+ (b) owner doc backlog 显式配置指引（2539 :435 句扩展）+ (c) max-concurrency 语义正交声明」组合唯一分支；专用防御默认/默认开启不采**：

1. **(a) 软上限形态**：新配置键 `nop.job.worker.max-inflight-promises`（缺省 **0=禁用**）——`scanBatch` 认领段（:255-269）之前判定：`maxInflightPromises > 0 && inFlightInvokeStartMillis.size() >= maxInflightPromises` → WARN + `workerMetrics.onRejected(inflight)` + return false（本周期收尾，下周期继续——drain 提前收尾形态=audit 候选 a 原文）。**载体复用**：`inFlightInvokeStartMillis` 为 2496 MA-06 看门狗既有 registry（:425-434 oldest 计算遍历同 map 亲证）——其 size 即 JVM 在途 invoke 数，**零新增 ledger**（判定在 2496 产物面之外读其公开形态——零改写零弱化）。默认关闭论证：开启=行为变更（backlog 消化期望行为受损——plan 风险段预设「默认关闭+文档提示」对齐 audit「可选」口径）；禁用=现状零变化（双闸门旁路面由声明面承载，护栏为显式 opt-in）。
2. **(b) 文档指引（E5 扩展）**：owner doc §五 worker 表 `max-inflight-promises` 行登记 + :435 `max-concurrency` 行既有「积压恢复场景应显式配置该键」句扩展互引（「JVM 在途 promise 面另见 max-inflight-promises」）——不采运行时提示（backlog 大且双关时 WARN 唠叨=噪声面，声明面承载足矣）。
3. **(c) 语义正交声明（2539 联动核对落款）**：`max-concurrency`=**DB 在途认领行数上限**（2539 E5 精确化语义，claim 前 count-based 闸门）；`max-inflight-promises`=**JVM 内在途 promise 上限**（claim 后 executeTask 在途数）——两闸门正交（口径不同源、生效点不同、互不推导），owner doc 声明正交性，**与 2539 :435 精确化零矛盾**（联动核对义务兑现）。
4. **2498 §3.1.1 适用结论（红线 4 落款）**：W-02 零载体创建——软上限为计数闸门非执行载体；worker 执行体仍经 `getExecutor()`=globalTimer.executeOn(globalWorker) 共享形态（C 类合格准绳维持）；判定标准四要素引用不重建（无新载体即无判定需求——适用结论=不适用，声明落款）。
5. **W-02×W-10 联合推演记录（merge-adjudication §2.3-4 义务——引用 2539 裁定 G 联合推演记录消费）**：CLAIMED 行计入 `countInFlightTasks`（IN_FLIGHT 集 :158-163 亲证）→ crash 残留 CLAIMED 永久占用 max-concurrency 额度（闸门侧流失面）；**JVM promise 侧零占用**（crash 于 executeTask 前的批尾 task 无 promise——软上限不受 CLAIMED 残留影响）→ W-02 软上限**不掩盖** W-10 额度流失面（两闸门各自的流失面独立可辨：max-concurrency 被 CLAIMED 虚占、软上限不受扰）；反向：W-10 墙钟回收 CLAIMED→TIMEOUT → countInFlightTasks 释放 → max-concurrency 闸门恢复容量（**W-10 修复直接治愈 W-02 闸门侧流失面**——联合推演结论落款）；backlog 消化期望行为与 W-10 回收正交（回收的是 crash 残留行非 backlog WAITING 行）。互不掩盖双向论证闭合。
6. **红② 钉形态[F-M2 评审勘正——红判据改挂软上限启用腿]**：`max-inflight-promises` 配置 >0 + 构造在途 invokeAsync ≥ 阈值（多 job backlog 驱动）→ pre-fix 无闸门实现认领数突破上限=红（确定性——配置+构造驱动）；post-fix 同构造认领提前收尾+rejected 留痕=绿；默认配置「无闸门」观察降格为**缺陷演示记录**（红绿总册注记形态，非红判据）；默认禁用腿（缺省行为零变化断言）=双侧绿回归锚。`TestJobWorkerScanner` 18 用例+2496 看门狗钉零回退；闩带 ≤15s。

**被否决的备选**：
- **防御性默认开启软上限**（非零缺省）：行为变更面=所有未配置部署（backlog 消化受损——「上限存在但默认旁路」的镜像缺陷）；audit 判级=「已声明语义边界内的可达高水位」（P2 非 P1——行为变更不具必要性）。不采。
- **max-concurrency 缺省改非零**：直接改写 2539 E5 精确化语义的宿主键（0=不限为 owner doc 长期声明语义）+ 行为变更面同上。不采。
- **drain 循环在途超阈提前收尾（无配置键、恒启用）**：在途上限需要数值（多少算超阈）——无键即硬编码魔数=隐蔽行为变更。不采（键化+默认禁用为唯一分支）。
- **JVM 信号量硬限**（执行体并发信号量）：改变「JVM 内无信号量，执行体并发受 invoker 实际异步性约束」的 2539 :435 已声明语义（声明面改写）+ 信号量语义=阻塞认领线程而非提前收尾。不采。

**零触碰边界**：`maxConcurrency` 字段/ceiling（:52/:183-192）/resource check（:194-203）零 diff（C5-004 同区域本批为唯一修面，前批产物零触碰）；2496 看门狗产物（registry 本体/watchdog 任务）零改写（软上限只读其 size）；W-05 CAS 守卫/绝缘边界零触碰（AbstractBatchScanner 本批触面归裁定 F）；2539 :435 精确化句零改写（E5 扩展为互引追加）；`NopJobCoreConstants` 只读锚零 diff（:20/:40-44）。

## 裁定 H 保留族与回收域（W-04/W-10）

### H.1 W-04：R01 三表保留族落地（1.4-C7-004；SSOT §3.6 同构行对齐——七款全落款）

**决策**：**「ledger-retention-policy §3.4 同构行七款逐项落款——形态 A 周期清理载体（nop-job-dao 新组件）+三键零参数发明+锚列逐表落名[m-4]+dao 层删除（BizModel delete 阻断共存零放宽）+回归钉四件套含接线断言+SSOT §3.4 成员行增补义务+orm 零触碰」**：

1. **统一参数集三键（SSOT §3.1/§3.2 零发明）**：`nop.job.ledger.retention-days|30`（0=显式禁用；负=装配期 fail-fast）/`nop.job.ledger.cleanup-interval|1h`（下限 60s fail-fast）/`nop.job.ledger.delete-batch-size|1000`（≤0 fail-fast）——键常量归属新建类型化 Configs 接口（`JobLedgerConfigs`，nop-job-dao，先例=NopSysDaoConfigs 形态[SSOT §3.2 表载体列]）；缺省值与 SSOT 逐字一致。
2. **清理谓词锚列逐表落名（禁隐式选列——SSOT §3.1 第 7 项）**：
   - `nop_job_fire`：锚列 **`endTime`** + 终态谓词 `fireStatus ∈ isTerminal 集`（≥SUCCESS=30——SUCCESS/FAILED/TIMEOUT/CANCELED :53-55 亲证）。endTime=终态迁移时刻写入（completeSingleFire :171/tryMarkDispatchTimeout :382/markSuspiciousAsTimeout 同族）后不被业务回改——账龄事实列语义成立。
   - `nop_job_task`：锚列 **`endTime`** + 终态谓词 `taskStatus ∈ {SUCCESS, FAILED, TIMEOUT, CANCELED}`——**SUSPICIOUS 显式排除**（isFinished 含 SUSPICIOUS :113-127，但 SUSPICIOUS 为 resolvable-pending：删除滞留 SUSPICIOUS 行将使所属 fire 聚合永缺行——completeSingleFire tasks.isEmpty 早退 :134-135 → fire 永久 RUNNING；严格终态集=回收面与 W-10 回收链正交零踩踏）。
   - `nop_job_task_log`：锚列 **`logTime`**、**纯 logTime TTL 无终态谓词**[m-4 落款]——task_log 无状态列（saveEntry :100-112 全字段亲证零 status 写点）；append-only 审计行语义（30 天 TTL 边界=超长任务日志可被清理——声明性边界，正常任务执行时长远小于保留期）。
   - **三表独立谓词、无级联**：各表按自身锚列+谓词独立删除（子表先行序：task_log → task → fire，最小化悬挂窗）；悬挂容忍在位佐证=`fillRedundantColumns` 容错路径（:129-132「task 已被清理/不存在：仅填充 taskId」——K1 亲证）。与 retry/tcc 成员行的级联形态差异声明：nop-job 三表行无父级联 ORM 约束且无「孤儿语义破坏面」（悬挂仅影响冗余展示列），独立谓词=更简且安全。
3. **形态 A 载体与生命周期钩子（SSOT 裁定②）**：新建 `JobLedgerCleaner`（nop-job-dao）——周期链挂 `LifeCycleSupport.doStart`/`doStop` 配对 + **`timer.executeOn(worker)` hop 载体**（清理体=DB 删除 IO——2497 §七 调度槽纪律硬约束[SSOT §3.3 形态 A 约束同源]；照抄 nop-batch 先例形态[SSOT §3.4 成员 1.7-C7-003 行「LifeCycleSupport.doStart + timer.executeOn(worker).scheduleWithFixedDelay + doStop 取消」]）+ 装配=既有 `app-dao.beans.xml` +1 bean（**条件申报②触发——批内申报落款见「条件申报登记」节**）。失败包络=SSOT §3.1 第 5 项（清理执行失败 ERROR 日志 `nop.job.ledger.cleanup-fail` 含表名+失败计数，禁空 catch，单轮失败不阻断下轮——R1-01 绝缘语义消费[periodic-task-insulation.md]）。
4. **delete 阻断共存口径**：清理走 dao 层删除原语（`fireDao()/taskDao()` 按 IN 集批删——`saveEntityDirectly` 对偶面），**不经 BizModel.delete**——`NopJobFireBizModel.delete`（:81-85）`ERR_JOB_FIRE_DELETE_NOT_ALLOWED` 与 `NopJobTaskBizModel.delete`（:24-28）抛错语义零放宽（「archived, not directly deleted」用户 CRUD 契约与 TTL 基础设施生命周期正交——owner doc 保留策略小节声明共存口径[E5]）。
5. **回归钉四件套（红④——SSOT §3.4 回归钉要求）**：清理腿（终态超期行删除+子表先行序）/保留腿（未到期行+非终态行[SUSPICIOUS/RUNNING/WAITING]零误伤+活跃 fire 所属行零触碰）/fail-fast 腿（负 retention/interval<60s/batch≤0 装配拒绝）/**接线断言腿（清理体被生产生命周期链真实调用——防 1.6-C7-005 空壳先例复刻[按号防混：该反例为 1.6-C7-005 非本批 W-05]）**。当前即红[确定性——无任何清理路径：超期终态行永存]。
6. **SSOT §3.4 成员行增补义务**：W-04 落地后在 ledger-retention-policy §3.4 增补 nop-job 三表成员行（形态沿 R2-12 tcc 行——三键/锚列/谓词/载体/回归钉全列；Phase 2/3 义务，与 owner doc 保留策略小节互引）；**参数修订只走 SSOT 一处**（§3.6 纪律）。
7. **orm 零触碰（条件申报③不触发落款）**：删除谓词查询（终态+锚列范围+limit）不新增 orm 索引——保留期落地后表体量有界（≤30 天增量），hourly 限量查询可接受；慢查询证据出现→后续批条件申报③通道（SSOT §四.4 默认零触碰+R2-12 索引申报先例为反例通道，本批不启用）。

**理由（触发时序论证）**：缺陷时序——每分钟 schedule 年增 52.6 万 fire 行（广播放大 task 行）→ 扫描查询（fetchWaitingFires/fetchRunningFires/fetchDueSchedules 全部状态过滤+order by）随表深劣化 → 5s 扫描节拍拉长 → 调度吞吐下降；应用层 delete 双阻断（fire/task BizModel）运维只能绕过应用操作 DB。post-fix：T′ 每小时周期清理（形态 A 机器速率写入面唯一合法形态——SSOT 裁定②「人工 purge 不得为唯一形态」）→ 表体量收敛于保留窗 → 查询劣化面闭合；终态/在途分界由谓词保证（活跃数据零触碰——W-03/W-10 回收链、W-08 stuck fire 处理面均不 intersect：SUSPICIOUS/RUNNING 显式排除在可删集外）。

**被否决的备选**：
- **形态 C（BizModel purge action）为唯一载体**：人工速率差无解（SSOT 裁定②/§3.3 明文）；作为补充通道可后续增设非本批面。不采。
- **fire→task→task_log 级联删除**（父 IN 集传递）：三表行无 ORM cascadeDelete 约束且悬挂容忍在位（fillRedundantColumns 容错）——级联的批量拾取三段查询复杂度高于独立谓词且收益仅悬挂窗收窄（展示列级）。不采。
- **清理载体落 coordinator**（复用 scanner 基类）：清理链非扫描语义（无 cursor/无分区）且 AbstractBatchScanner 触面归 W-05/W-07（同基类跨工单耦合）；nop-batch 先例=dao 模块载体。不采。
- **orm 索引同批申报**（申报③启用）：保留期未落地前无可测查询成本证据——预设零触碰（SSOT §四.4 默认）；证据驱动申报留后续。不采。
- **task_log 加状态列以套终态谓词**：改表结构=orm 申报+写入面扩散——m-4 口径（无状态列是事实而非缺陷，纯 TTL 语义成立）。不采。

**零触碰边界**：`NopJobFireBizModel`/`NopJobTaskBizModel` delete 阻断面零放宽（仅共存口径核对）；`NopJobTaskLogBizModel` reportTaskLog 链零逻辑 diff（W-01 同文件邻接面禁顺带）；orm.xml 零 diff（申报③不触发）；既有配置键零触碰（三键为新增）；2496/2497 设施零触碰（载体消费其契约非修改）；`TestJobStoreImpl`/`TestJobFireStoreRace` 零改写（红④ 新测试类追加）。

### H.2 W-10：CLAIMED 墙钟回收扩展+javadoc 修正+per-status 年龄 gauge（1.4-C7-011；C10-006 宿主）

**决策**：**「墙钟扩展唯一分支（tryMarkTimeout 对 CLAIMED 同应用墙钟，startTime=claim 时刻[R7 口径]）+ javadoc/owner doc 修正 + per-status in-flight 年龄 gauge getter；认领租约不采（Follow-up）」**：

1. **墙钟扩展形态**：`tryMarkTimeout`（:490-563）的 `!isRunning` 门（:502-504）改为 `!isRunning(taskStatus) && !isClaimed(taskStatus)` 早退——CLAIMED 行进入墙钟路径（与 RUNNING 同段：startTime 判空 :506-509[claim 时已写 :111——null 行已被 fetchRunningTasks 过滤 :127]、timeout 配置三选一 :521-534、deadline 判定、终态化 TIMEOUT+`notifyPollManagerTaskTimeout` 回传 :562[2513 裁定①(c) 通道对新路径自动覆盖]）。durationMs 语义=now−startTime（claim 时刻起算——R7 勘误口径：「认领时钟字段为 startTime」）。
2. **plan 340 钉兼容性论证（落款）**：钉保护面=「未配执行超时的长任务不被 checker 误杀」（:615 `testLongRunningTaskWithoutTimeoutNotKilled`）；CLAIMED 墙钟回收**仅作用已配超时形态**（:521-534 三选一在 CLAIMED 路径同段生效——timeoutSeconds≤0 且 executionTimeoutMs≤0 → return 原样）——未配超时的 CLAIMED/RUNNING 均不入墙钟（钉语义不受损）；`TestJobTimeoutChecker` 33 用例（含 :615）零回退=回归门实证。
3. **javadoc 修正文案（JobTaskStoreImpl :88-99）**：矛盾声明句修正——「CLAIMED态的回收依赖 worker 存活链（SUSPICIOUS→TIMEOUT）**或执行超时配置**」保留但补实现指向：「（执行超时对 CLAIMED 的覆盖自 CRA 8.47 R4-07 W-10 起生效：tryMarkTimeout 对 CLAIMED 同应用墙钟，时钟=认领时写入的 startTime）」——javadoc 与实现矛盾闭合（docs bug 修正非语义变更）；owner doc「超时语义（三层独立）」句（:376 区）同步修正 CLAIMED 覆盖声明（E5）。
4. **per-status in-flight 年龄 gauge**：checker 每 cycle 对已捞 RUNNING_LIKE 行（含 CLAIMED——数据在手）按状态计算最老存活年龄 → 三个 volatile long（CLAIMED/RUNNING/SUSPICIOUS 最老年龄）+getter 三枚（2496 存活指标族形态镜像——零平台 metric 接线，owner doc §五 观测行承载）。额度流失可观测面：CLAIMED 最老年龄持续增长=crash 残留信号（rejected counter 之外的显式信号——audit「唯一信号 rejected counter」盲区闭合）。
5. **红⑩/C10-006 钉形态（同 diff——肯定性回收断言）**：worker 存活（桩 alive 集含其 id——复用裁定 A namingService 桩）×执行超时已配置（schedule.timeoutSeconds 注入）→ CLAIMED 行（测试直接构造 CLAIMED+startTime 已写）经墙钟转 TIMEOUT（当前即红[确定性——pre-fix :502 门静默丢弃]）；时钟口径断言（durationMs=now−startTime）；plan 340 钉零回退并列（未配超时腿=已绿锚）；`TestJobTimeoutChecker` 零改写叠加。闩带 ≤10s。

**被否决的备选**：
- **新增认领租约**（lockTimeoutMs 参数启用）：租约机制=deadline 持久化+到期判定+与墙钟超时的优先级仲裁全套设计（javadoc 自认「租约机制需设计引入」）；墙钟扩展复用数据在手（RUNNING_LIKE 已含 CLAIMED+startTime 已写 :111）零新机制；租约归 plan Non-Blocking Follow-ups（「W-09 租约语义的后续强化候选」同族平台设施轮）。不采。
- **CLAIMED 专用超时键**（认领超时独立配置）：新增配置维度=三层超时语义改四层（owner doc「三层独立」契约改写）；墙钟语义复用既有两层配置零新键。不采。
- **resetStaleWaitingTasks 扩展 CLAIMED**：stale 重置语义=「派发等待超时重派发」（WAITING 专属）；CLAIMED 已过认领点（task 已被 worker 持有）——重置回 WAITING 将造成双执行面（原 worker 可能仍活着执行）。不采（回收唯一正确去向=TIMEOUT 终态）。

**零触碰边界**：`JobTaskStateMachine` 零 diff（isClaimed 既有谓词消费——只读锚）；`tryLockTasksForExecute`/认领写入（:102-114）零 diff；`fetchRunningTasks` 过滤（:117-141）零 diff；`resetStaleWaitingTasks` 零 diff；`JobWorkerScannerImpl` executeTask 永久滞留注释（:275-277 佐证锚）零触碰（W-10 修复后该注释语义由 checker 侧覆盖——注释本身描述 worker 侧无回收路径，仍真）；与 W-03 同文件矩阵（裁定 H 附第 4 节）；2513 红线 2 五面零触碰（回传通道 :562 保持）。

## 裁定 H 附：红①–红⑩ 驱动器总表 + 零回退钉全列 + C10 注记表 + 同文件联调矩阵

### 1. 红绿判据纪律（先红后绿 E2——2 条 C10 验收前置同 diff + E2 泛在 10 钉）

全批测试零 mockito 新增使用（nop-job 测试基建=2513 mock 体系+R406 九钉 latch/seam/文档钉先例齐备；namingService 桩=装配缝非生产改动）——观测一律 JDK 并发原语+latch+手工桩+探针 subclass+配置注入缝。**orTimeout 假绿陷阱禁令**：future 完成性断言只允许同步 `get(timeout)` 捕 TimeoutException 判红。**闩带上界 ≤30s 且收尾必放闸**（红⑦ 挂起线程释放义务显式）。红绿快照落 `_tmp/` 红绿总册（命名 red-green-ledger-2540——Phase 2 创建）+逐 trial 原始日志存证（2536/2534 灭失教训纪律）。测试类注释显式归属 CRA 8.47 R4-07（`*R407` 后缀；同号防混：8.47-W-xx≠8.46-W-xx；finding ID 防混权威锚=1.4-C7-006≠1.6-C7-006≠1.5-C7-006）。**结构/变异腿诚实留痕禁伪绿**（红⑥⑨ 含结构腿+概率/变异腿——禁行为伪红禁概率假绿）；**概率腿未命中诚实登记条款**（行为红未命中→诚实登记以结构钉+变异腿承载——2539 总册先例）；**缺席断言携带实际计数**。

### 2. 红①–红⑩ 驱动器总表（每工单 ≥1 钉）

| 钉 | 工单 | 驱动器形态 | pre-fix 红判据 | post-fix 绿判据 | 伪绿防线 | 闩带 | 确定性 |
|---|---|---|---|---|---|---|---|
| 红①（=C10-008 同 diff 三面） | W-01 | 批上限 2+超批 3 条→typed 拒绝零落库/batch=2 两条正常→全绿落库/单条超长→typed 拒绝 | 超限/超长成功落库=红（确定性） | 三面 typed 判别+零部分写入 | 错误码判别（BATCH vs INVALID_ENTRY 两码分流防混淆）+宿主 4 用例并列 | ≤10s | 确定性 |
| 红② | W-02 | 软上限启用腿：配置>0+构造在途≥阈值→pre-fix 认领数突破上限=红；默认禁用腿=双侧绿回归锚[F-M2] | 启用腿下认领突破上限=红（配置+构造驱动确定性） | 软上限提前收尾+rejected 留痕/缺省零变化 | 认领计数实测（非配置存在性断言）+`TestJobWorkerScanner` 并列 | ≤15s | 确定性 |
| 红③ | W-03 | namingService 桩三腿：空集标 suspicious→恢复→不转 TIMEOUT（棘轮腿）/全 unhealthy→零新标（空集腿）/恢复+越 stale 上限→强转+计数（stale 腿） | 无条件强转/空集判死=红（桩驱动确定性） | 顺延+禁用+stale 强转三语义各绿 | 三腿互证（防顺延存在但空集漏改）+plan 340 :615 并列 | ≤10s | 确定性 |
| 红④ | W-04 | 四件套：终态超期清理/未到期+非终态（SUSPICIOUS 显式）保留/fail-fast 装配拒绝/**接线断言（清理体被 LifeCycleSupport 链真实调用）** | 无清理路径超期行永存=红（确定性） | 四件套全绿+幂等复跑 | 接线断言防空壳（1.6-C7-005 反例）+delete 阻断共存断言并列 | ≤15s | 确定性 |
| 红⑤ | W-05 | batch-size 调小 2+3 活跃 schedule+窗口外漂移行→跨轮不收敛（红）；翻页 ≤2 轮收敛+limit-hit warn（绿） | 窗口外漂移永不入窗=红（配置驱动确定性） | 收敛+warn 留痕 | 收敛轮数断言（防轮转存在但回卷缺失） | ≤10s | 确定性 |
| 红⑥ | W-06 | 结构文档钉：owner doc §1.4 W-06 终态语义契约句锚断言+变异腿（还原旧措辞→红→复归→绿） | 旧措辞在位=结构红 | 契约句在位=绿 | 文本锚断言（非全文匹配防措辞微调误报）+变异腿；MJ-1 钉零改写并列（行为锚） | ≤5s | 结构红（诚实留痕） |
| 红⑦ | W-07 | 测试子类 scanBatch latch 挂起+阈值注入→超阈无信号（红）；WARN+stallDetectedCount+年龄 getter+放闸复位（绿） | 在途超阈无信号=红（latch 确定性） | 三信号实测+复位断言 | 计数断言+放闸复位防「判定存在但永不复位」+9 用例并列 | ≤10s | 确定性 |
| 红⑧ | W-08 | 坏 pauseCalendarSpec JSON 注入→fire 滞留 RUNNING+WARN 增长（红）；FAILED+错误码判别+重捞消除+PAUSED 腿+保存拒绝腿（绿） | 重试环无终态=红（注入驱动确定性） | 终态化+隔离三腿 | 错误码判别+下轮零 WARN 增量断言（防终态化存在但仍重捞） | ≤15s | 确定性 |
| 红⑨ | W-09 | store 级直构：A 锁→B 接管插入 fire2→A 过期实体插入→双 fire 存活（红）；放弃+WARN+单 fire（绿）；latch 端到端变体补充 | 重复 fire 存活=红（确定性） | 值匹配放弃+槽位由 fire2 承载断言 | fire 计数+UK 存活核对+变异腿（摘复查→红→复修→绿） | ≤15s | 确定性 |
| 红⑩（=C10-006 同 diff） | W-10 | worker 存活×执行超时已配→CLAIMED 经墙钟转 TIMEOUT（红）；durationMs=now−startTime 口径+plan 340 未配腿并列（绿） | !isRunning 门静默丢弃=红（确定性） | 肯定性回收+时钟口径 | durationMs 断言（防转 TIMEOUT 但时钟错源）+:615 未配腿防误杀 | ≤10s | 确定性 |

### 3. 零回退钉全列（closure audit 逐钉 git 过滤）

- **基线签名（plan 勘误 7，2026-11-07 亲跑；HEAD `bd7432c618` nop-job 零产品 diff 继续有效）**：`./mvnw test -pl nop-job` solo 通道 BUILD SUCCESS EXIT=0（46.8s，Reactor 13 项目）——api 25 0F0E 0S/core 146 0F0E 0S/local 69 0F0E 0S/dao 89 0F0E 0S/worker 49 0F0E 0S/coordinator 213 0F0E 2S（2S=容器/门控面——E3 噪声源登记）/service 49 0F0E 0S/web 1 0F0E 0S/retry-adapter 3 0F0E 0S，合计 **644 tests 0F 0E 2S**。零回退实证=九模块签名维持（+新钉增量）；`-am` 批行通道全量验收归 Phase 3（三联裁定先例链）。
- **2539 R406 九钉**：`TestLocalJobDetailConsistencyR406`/`TestLocalJobSchedulerConcurrentRegistrationR406`/`TestLocalJobSchedulerReentrancyR406`（**MJ-1 谓词 :249-257 区[m-8 口径]——本裁定 E 零改写，例外未触发**）/`TestSimpleExecutionContextSnapshotR406`/`TestJobCarrierDocPinsR406`/`TestCronExpressionNoThreadLocalR406`/`TestScannerInFlightGuardR406`/`TestRpcPollRegisterRollbackR406`+既有锚（:471 型）——钉内断言零改写。
- **2513 十二钉**：成员清单以其收口档为 SSOT（引用不复制不重算——MN-8 惯例沿用）；本批点名面=`TestRpcPollTaskManagerEntryTermination` 9/`TestRemoteJobInvoker` 13/plan 340 钉（`TestJobTimeoutChecker` :615）。
- **2496 绝缘钉**：`TestAbstractBatchScanner` 9 用例/`TestJobWorkerScannerInsulation`/`:497` 型 fail-safe 锚——零改写零弱化。
- **既有锚点名（本批零回退锚）**：`TestLocalJobScheduler`/`TestLocalJobSchedulerManagementResponsiveness` 5/`TestBeanMethodJobInvoker` 17/config 两测试/`TestCronExpression` 3/`TestCronGoldenVectors`/`TestJobTriggerCalculator`/calendar 族/**`TestJobTimeoutChecker` 33 用例（含 :615 plan 340 钉——W-03/W-10 回归门）**/`TestJobCoordinatorScanner` 27/`TestJobScheduleCounterReconciler`（1S 门控面）/`TestJobWorkerScanner` 18/`TestRpcPollTaskManagerInsulation` 2/**`TestNopJobTaskLogBizModel` 4 用例（C10-008 宿主——本批同 diff 扩展对象[勘误 8 身份变化]，既有 4 用例零回退叠加非替代）**/`TestJobStoreImpl`/`TestJobFireStoreRace`（dao 零回退基座）；8.45 `*R405` 钉零站点交集零触碰。
- **全批 diff 为空核对面**：`LocalJobScheduler`/`BeanMethodJobInvoker`（裁定 E 声明形态）——**本批零 diff 为裁定 E 可检证据**；`NopJobCoreConstants`/`JobTaskStateMachine`/`JobFireStoreImpl`（fetchRunningFires 面）零 diff。

### 4. W-03/W-10 同文件（JobTimeoutCheckerImpl）diff 分组与实施次序联调矩阵[m-5 补锚]

| 维度 | W-03 面（组 A） | W-10 面（组 C） | 交界处置 |
|---|---|---|---|
| 触及子区域 | `scanTaskTimeouts` :255-295 循环级（复查分流+顺延）+ `resolveAliveWorkerIds` :297-324（空集→null）+ 新增计数字段 | `tryMarkTimeout` :490-563（!isRunning 门 :502-504 扩展为 !isRunning&&!isClaimed）+ 新增 gauge 字段 | **子区域不相交**（:295/:490 之间无共享行）；`markSuspiciousAsTimeout` :457-488 本体双方零 diff |
| 共享数据 | 复查复用 :278 `aliveWorkerIds`（每轮一次解析） | 不消费 aliveWorkerIds（墙钟路径与 liveness 正交——R7 时钟=startTime 非 worker 存活） | 零共享可变态（W-10 的 CLAIMED 回收不依赖 alive 集——存活 worker 下照常回收=C10-006 肯定性回收语义本体） |
| 测试承载 | `TestJobTimeoutChecker` 新 @Nested（红③ 三腿） | 同文件新 @Nested（红⑩/C10-006） | 同测试文件两组追加（禁拆两文件禁重复基建）——组 A/组 C 实施以**同文件单 diff 合并落盘**（谁先实施谁搭骨架：字段区一次落位[计数+gauge 两组字段相邻声明]，方法区分区改动互不越界） |
| 实施次序 | 先（顺延分流在循环级，先落不阻塞 W-10 门扩展） | 后（门扩展单点 :502-504） | 合并策略：组 A 落 W-03 时**预置** tryMarkTimeout 零改动（禁顺带扩展 CLAIMED 门——属组 C 面）；组 C 落 W-10 时基于组 A 已落形态合并；同 commit 序列禁交叉 review 点=:502-504 单行归属 W-10、:283-290 分流归属 W-03（closure audit 按行归属核对） |
| 回归门 | plan 340 :615+33 用例 | 同左+durationMs 口径 | 共享回归门一次核证 |

### 5. C10 注记表（对齐纪律第一条+第十条）

| C10 条目 | 承载面 | 本批关联工单 | 零改写/兑现核对面 |
|---|---|---|---|
| C10-006（**验收前置，同 diff 绑定**——2539 勘误 3 承接兑现） | CLAIMED 肯定性回收断言（worker 存活×执行超时→TIMEOUT；时钟=startTime[R7]） | W-10（红⑩） | 同 diff 必落（禁拆分）；plan 340 :615 零回退叠加非替代；**2539 就绪注记承接闭环**（宿主 C7-011 ∈ 本批——禁提前红空壳已解除：本体即在本批） |
| C10-008（**验收前置，同 diff 绑定**——同上） | 上限三面断言（超限拒绝/batch=2 最小对照/单条超长） | W-01（红①） | 同 diff 三面一套（禁拆分禁重复计数）；`TestNopJobTaskLogBizModel` 4 用例零回退叠加（勘误 8 身份变化兑现） |
| 既有 C10 面零改写清单 | 2539 C10-004 三锚点（ReentrancyR406——MJ-1 例外未触发）+C10-007 单类两腿+2513 十二钉（EntryTermination 9/ManagementResponsiveness 5/:615 plan 340 钉/绝缘钉全列）+2496 绝缘钉+C10-001/002/003/005/007 已处置面+C10-009 已兑现（2539 触达——**本批零遗留**） | 全批 | 红线 1/2/3 全集——closure audit git diff 逐钉核对 |
| G91 | 1.4-C10-009 已于 2539 兑现 | — | 零遗留（cra-index :476 消号注已落） |

## 条件申报登记（禁静默扩权——逐项裁定落款）

①**nop-job-api（JobApiErrors 新增错误码/SPI javadoc）→ 预设不触发，落款**：W-01 单条面复用 `ERR_JOB_LOG_INVALID_ENTRY`+`ARG_TASK_LOG_ID`（api 既有 ：122——只读引用）；W-01 批面新码 `ERR_JOB_LOG_BATCH_EXCEED_LIMIT` 与 W-08 新码 `ERR_JOB_TRIGGER_CONFIG_INVALID` 均落 **JobCoreErrors**（nop-job-core 非 api 叶——plan PA 段预设「JobCoreErrors 模块内新增」口径）；`IJobInvoker` 等 SPI javadoc 零 diff（W-06 声明句落 owner doc §1.4 非 api 面）。**零 api diff 为 closure audit git 过滤核对面。**
②**`_vfs` beans 新增 bean 定义 → 触发（批内申报落款）**：W-04 清理载体 `JobLedgerCleaner` 装配需在既有 `app-dao.beans.xml` 追加 1 个 `<bean>` 定义（载体=形态 A 周期链挂 LifeCycleSupport 钩子的唯一生产可达形态——程序化构造无法挂接 IoC 生命周期链[SSOT 裁定②钩子菜单明文]）。**申报依据**：plan PA 段预设「如 W-04 清理载体采『新增 bean 定义』形态→沿 2513/2539 PA 段先例批内闭环申报」——本裁定即为申报载体（改动面=既有 beans 文件 +1 bean 行，零新文件零新 beans 文件）；实施前本节在案即授权依据，closure audit 核对 beans diff=该 1 行。
③**`model/*.orm.xml`（W-04 索引）→ 预设不触发，落款**：orm 零触碰（裁定 H.1 决策 7——保留期落地后表体量有界，索引证据驱动留后续批）；orm.xml 仅只读佐证引用（UK :323-325——W-09 红⑨ 佐证）。
④**nop-kernel/nop-commons 载体上提 → 禁选落款**：merge-adjudication §4.5 关联 2「使用面立案、修复不上提平台」边界；裁定 F 看门狗/裁定 G 软上限/H.1 清理载体均模块内承载零上提；W-07 平台 metric 接线候选不启用（getter 形态承载——上提观测设施=相邻申报面禁选）。
⑤**`_` 生成物/`_gen/`/pom 结构 → 预设不触发，落款**：新测试落既有测试树（`*R407` 新类）；零新依赖；`JobLedgerConfigs` 为新建普通类非生成物；站点文件零 `_` 生成物。
**任一条件申报触发 → 按 AGENTS.md 四件证据链走完方可实施；②已按批内闭环申报通道在本节落款，其余未申报前实施=禁。**

## 零触碰边界总表（前批产物面 / 同文件跨批面 / 批外模块 / P3 相邻与 BF-1 / `_gen`+orm / 既有裁定文档）

- **触碰面总口径**：**17 个 main java 文件**（plan 16 站点文件[勘误 6 重写版口径：service 3+dao 4+coordinator 4+core 3（AbstractBatchScanner/TriggerBuilder/NopJobCoreConstants——后者零 diff 只读锚，实触 2]+local 2（LocalJobScheduler/BeanMethodJobInvoker）] **+1=** `NopJobScheduleBizModel`（W-08(c) 保存路径试构建——批外新发现 BF-1 登记，服务层第 4 文件）**，其中实际产品 diff 面=15 文件**（LocalJobScheduler/BeanMethodJobInvoker 零 diff=裁定 E 证据；NopJobCoreConstants 零 diff 只读锚）**跨 6 子模块**+新增 `JobLedgerCleaner`/`JobLedgerConfigs` 两文件（nop-job-dao）+六子模块测试树+文档面（`_tmp/` 红绿总册[red-green-ledger-2540——Phase 2 创建]+owner doc+SSOT §3.4 成员行[授权例外]+app-dao.beans.xml[申报②]）。批内同文件面：JobTimeoutCheckerImpl 双工单（W-03/W-10——裁定 H 附第 4 节矩阵禁交叉）；NopJobTaskLogBizModel 双面（W-01 本体+W-04 邻接——禁顺带）；JobFireStoreImpl 双引用面零 diff（W-08 支撑/W-09 先例对照）；JobScheduleStoreImpl 单面（W-09）；AbstractBatchScanner 单面（W-07——W-05 守卫+2496 产物零触碰）。
- **8.46/plan 2539 产物面（同文件跨批——禁顺带逐面）**：LocalJobScheduler 六面+R406 九钉+owner doc 八处落账（:93/:214/:329/:331/:435/:451/:480/:486）零重开零回退（红线 1）；W-06/W-07 为其预声明承接面（:416-419 注释+存活指标基线——兑现非重开）；**MJ-1 联动例外未触发**（裁定 E 零改写）。
- **8.19/2513+8.2/2496+8.3/8.4（2497/2498）产物面**：rpcPoll 五面（红线 2——W-03/W-10 同文件基线不触回传通道本体）+两阶段四产物+绝缘层+MA-05 三站点+MA-06 看门狗+存活指标族（红线 3——W-07 修复面收窄基线零弱化）+池设施契约+§3.1.1 判定标准（红线 4——W-02 零载体落款）零触碰；既有配置键全集零触碰（新增键：suspicious-stale-ms/stall-watchdog-ms/fail-escalate-threshold/report-max-batch-size/entry-max-chars/max-inflight-promises/ledger 三键——各归裁定节落款）。
- **批外模块（零 diff 红线）**：nop-job-api 产品源码（条件申报①零触发）；nop-job-web/app/meta/codegen/retry-adapter 五子模块产品源码；nop-kernel/nop-commons 载体与绝缘设施（只读引用——条件申报④禁选）；nop-message/nop-stream（跨单元已修面）；8.48+ 批次成员（nop-task/nop-wf 域零站点交集）；8.45 `*R405` 钉。
- **P3 相邻 8 条[§4.2 全集 9，C4-011 载体红线另列]**：C2-005（addJob 更新分支 active 复检——W-06 同文件零触碰）/C2-006（registeredCursors 迭代守卫——W-07 同文件零触碰禁顺带）/C4-006/C4-007/C4-011（nop-commons 载体红线）/C4-008（RpcPollTaskManager 生命周期——与 JobTimeoutCheckerImpl 异组件注记）/C4-010（cancelToken 恒 null——引用不重开）/C7-008（rpcPoll 墙钟——**W-09 fence 修复不触 computeDeadline 零代做**）/C7-012（JobLogReporter WARN 采样——零交集）。执行期意外触及禁顺带——四件证据链申报或回退。
- **BF-1 面（零触碰硬边界）**：`LocalJobScheduler` :500-502（尾部 guard）/:361（WAITING 改写）/:461-462（abandonAfterInterleave javadoc）零 diff——裁定 E 声明形态平凡成立+closure audit git 零 diff 核对；Successor Path 维持 2539 登记。
- **`_gen` 与 orm**：站点文件零 `_` 生成物；`nop-job/model/nop-job.orm.xml` 零 diff（条件申报③不触发）；`_NopJobCoreConstants`（生成常量骨架）只读引用零触碰。
- **既有裁定文档**：2513/2496/2497/2498/2509/2538/2539 设计文档、batch-proposal、merge-adjudication、inventory-p2-c10、cra-index 台账行——零回写（收敛注记归 Phase 4）；**唯一授权例外=ledger-retention-policy §3.4 成员行增补**（W-04 落地义务——SSOT §3.6 明文，非修订非重建）；本文档为唯一新增文档面（文件命名零调整）；roadmap 8.47 行（起草基线 :189、执行期 :218 区[m-2 以 live 为准]）Phase 4 承载。
- **文档面（E5——Phase 3 统一落账）**：owner doc `docs-for-ai/03-modules/nop-job.md` 八处落点（§1.4 W-06 终态语义句[裁定 E]/「超时语义」:376 W-10 CLAIMED 修正[裁定 H.2]/「worker-liveness 链细节」:378 W-03 顺延+空集语义[裁定 A]/「fire 计数自愈」:380 W-05 轮转声明[裁定 B]/「执行日志上报」:273 W-01 上限句[裁定 G.1]/§五 配置表新键登记+观测行[裁定 A/C/F/G/H]/保留策略小节新增[裁定 H.1——SSOT 互引+delete 共存声明]/源码锚点表 :514-532 刷新）+`docs-for-ai/04-reference/source-anchors.md` JOB 行+AbstractBatchScanner javadoc 轮转收敛句[裁定 B.3]+JobTaskStoreImpl javadoc 修正[裁定 H.2]——`No owner-doc update required` 仅适用 Phase 1（本设计文档 Phase），去向已显式登记。

## 端到端与接线验证义务（rule 22/23/24——随 Phase 2 落证；裁定侧落款）

- **端到端（rule 22——plan Phase 2 四链路+六面）**：≥1 条 coordinator 自愈链路覆盖 W-03/W-10 组合面（worker crash 窗 CLAIMED→（修复后）墙钟回收→fire 聚合推进——红⑩ 即链路载体+`TestJobTimeoutChecker` 扩展腿；W-03 腿=恢复场景顺延不误杀）；≥1 条触发器链路覆盖 W-08/W-09 组合（坏 spec 注入→终态化/PAUSED→stuck fire 消除→DISCARD 调度恢复[经 W-05 翻页对账]——红⑧ 即链路载体）；≥1 条本地模式链路覆盖 W-06 语义（异常注入→FAILED 终态→状态可查+契约句核验——红⑥ 结构钉+`TestLocalJobScheduler` 既有 FAILED 行为锚并列）；W-04 保留链路（终态行产生→周期清理触发→超期行消失+在途保留——红④ 接线断言即链路载体）；W-01/W-02/W-05/W-07 链路面（上限拒绝链路/软上限 backlog 链路/翻页收敛链路/stall 信号链路——各红钉即载体）。
- **接线验证（rule 23——十项全实测）**：红① 上限拒绝路径真实拦截（typed+零落库实测）/红② 软上限真实收尾认领（计数对比实测）/红③ 复查分支真实跳过强转+空集真实禁用+stale 真实强转（三腿实测）/红④ **清理体被生产生命周期链真实调用断言[防空壳]**/红⑤ cursor 翻页真实推进窗口外行（收敛实测）/红⑥ 契约句在位+行为锚并列（FAILED 行为经 `TestLocalJobScheduler` 既有锚实测）/红⑦ stall 信号真实触发+复位实测/红⑧ 终态化真实阻断重捞（下轮零 WARN 增量实测）/红⑨ 值匹配真实放弃滞后槽（fire 计数实测）/红⑩ CLAIMED 真实转 TIMEOUT（durationMs 口径实测）——每钉断言其修复真实被消费链调用，非仅字段/分支存在。
- **无静默跳过（rule 24——十项显式）**：上限 typed 拒绝显式（非静默截断维持）/闸门收尾显式（WARN+rejected——非静默 drain 维持）/顺延与禁用显式留痕（计数+WARN——非静默跳过维持）/清理失败 ERROR 显式[SSOT fail-fast 包络——非空 catch 维持]/warn 显式（limit-hit——非静默满窗维持）/声明显式（W-06 契约句——非含糊维持）/stall WARN 显式（观察信号——非静默挂起维持）/终态化显式（FAILED+错误码——非重试环维持）/放弃 WARN 显式（fence 过期——非静默双插维持）/回收终态显式（TIMEOUT+回传——非谓词丢弃维持）；新增公共分支 TODO/FIXME grep 零命中（全部站点文件全查）；拒绝/放弃/清理失败分支无空 catch/静默 return。

## 批外新发现登记（亲读发现即登记，禁瞒——参照 2538/2539 BF 形态）

- **BF-1（批外新发现——scope 精度登记，非缺陷）**：W-08(c)「保存/update 路径试构建校验」的唯一自然落点=`NopJobScheduleBizModel`（save/update 为 CrudBizModel 继承方法，enable/resume 试构建先例 :63-68/:102-107+`recalculateNextFireTime` :222-228 同类挂点）——该文件**不在 plan 16 站点清单内**（plan PA 表 row 8 列「保存/update 路径 pauseCalendarSpec 试构建校验」为修复方向但未列宿主文件）。裁定 C 采纳 (c) 并显式扩触碰面至 17 文件（零触碰边界总表增补行）；实施面=校验方法+save/update 两处覆写挂点，既有校验链零改写。
- **BF-2（watch-only 残余观察——W-08 邻接面）**：completion `fetchRunningFires`（:73-81）**无 cursor**——按 scheduledFireTime desc 固定取前 batchSize 条：持久 stuck fire 数 ≥ batchSize 时完成窗口饱和、健康 fire 饿死（与 W-05 同构窗口饥饿形态，宿主不同）。本修（裁定 C 终态化）清空配置类 stuck 源头使饱和面收敛；非配置持久失败（DB 长故障）下的饱和残余=登记级观察（与 W-05 已修面不同表不同扫描器，不扩批——successor 归后续 R4 同单元余量轮或 R-G 登记；drain 信号 :123 使单周期内多轮消化为既有缓解）。
- **BF-3（信息性核证结论——裁定 A 残余面声明的机制基础，非缺陷）**：SUSPICIOUS 对迟到真实结果的丢弃发生在 **SUSPICIOUS 阶段本身**（`isConcurrentlyFinalized` 含 SUSPICIOUS——JobTaskStateMachine :149-155+worker `handleExecutionResult` 早退 :443-445[F-m2 订正] 亲证），非仅 TIMEOUT 转换后——即裁定 A 顺延分支不重开 fence（SUSPICIOUS 单向性为 owner doc 声明的设计取舍）；顺延的收益=不 cancel 真实在途执行+误杀窗收窄，真实结果丢弃语义保持（诚实边界声明于裁定 A 理由段）。
- **BF-4（信息性——suspendJob 语义注记）**：`suspendJob` 对 COMPLETED/FAILED 早退返回 true（:241-242）——终态 job 的 suspend 为幂等成功语义（非缺陷；W-06 声明句措辞已含「resumeJob/fireNow 对 FAILED 恒拒绝」对比面，suspend 幂等成功与之并存不矛盾）。
- **BF-5（信息性——锚点勘正补充）**：plan 行文若干子锚漂移已在「plan 输入勘正」K1–K8 逐条登记（±1~10 行同区域漂移，机制零漂移）；16 文件零产品 diff @ HEAD `bd7432c618` git 亲证（唯一交界=docs(plan) commit）。
- **核证范围声明**：10 工单站点文件全量亲读（16 文件+NopJobScheduleBizModel/状态机族/常量族/orm UK/owner doc 八落点区/NopJobCoreErrors 三处错误码表关联面）；除 BF-1/BF-2 外无批外缺陷。

## 评审记录

- **设计评审 R1**（2026-11-07，agent_1e8b33f5 fresh session 只读 @ `bd7432c618`，live 抽验 ~150+ 锚点）：**PASS-with-revisions——0 Blocker + 2 Major + 8 Minor，放行 Phase 2**。三风险专项全部 PASS（裁定 A SUSPICIOUS 全空间推演[附 F-M1 null 轮语义矛盾勘正——取决策 2 不变式]/裁定 D fence 时序逐步重构+槽位承载论证[无双执行无双漏]/裁定 E 声明形态 MJ-1 兼容平凡成立+BF-1 硬边界自动满足+恢复否决充分）。M-1+m-1~m-8 九项 9/9 吸收落款（M-1 重写版经评审 git 亲证完全一致）。**F-M1**（裁定 A 决策 1/2 null 轮语义矛盾——采决策 2 不变式勘正，红③ 补可选腿）/**F-M2**（红② 默认「无闸门」断言不可红——红判据改挂软上限启用腿[配置>0+构造在途≥阈值→pre-fix 突破上限=红]，默认观察降格缺陷演示记录、默认禁用腿=双侧绿回归锚——plan :56 同步）——两项采证前已落盘。F-m1 ABS getters 锚漂移订正/F-m2 handleExecutionResult 锚订正/F-m3 §4.4 :179→:180 残余/F-m4 看门狗判定谓词 conflation 声明/F-m5 红③ 腿(i) 两相构造稳定化/F-m6 PAUSED 写路径载体点名/F-m7 计数重分类补明/F-m8 交界 commit 表述补全——随批落盘。K1–K8/BF-1~5 诚实性亲证（BF-1 第 17 文件触碰面扩登记诚实）。rule 14 合规+引用存在性 PASS。
