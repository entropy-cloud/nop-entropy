# WfTaskScanner fencing 与 task/wf 并发契约（R2-11 批六项裁定）

> Status: active
> Last Reviewed: 2026-10-30（plan 2514 Phase 1 产出；独立 draft review R2 FAIL→修订闭环[Major×4 修订：裁定① store 接线/锁序声明、裁定⑤ 逐出执行位同 key 内联+引用一致 remove、裁定⑥ 迁移方案三行+实体列集约束、裁定① action 锁段收窄出锁投递；Minor×4 修订：实体写路径列集/sync×executor upgrade 声明/外置表拒绝行/退避计数 map 有界]；本批唯一设计裁决权威 + R12 族 fencing 语义权威载体）
> Source: plan 2514（`ai-dev/plans/2514-cra-phase8.20-r2-11-task-wf.md`）Current Baseline 六成员表 + Baseline 勘误 6 条 + draft review R1 增补（裁定①站点④默认分支、裁定⑤ owner 表态机制）；发现事实源 = WI 1.5 审计目录 `ai-dev/audits/2026-10/2026-10-15-0930-deep-audit-nop-wf-nop-task-concurrency/`；行号 = live `83052aa1ff`（分支 `concurrency-audit-remediation`）2026-10-30 亲读回读值，执行期漂移以 live 为准并登记于 plan 勘误
> 实现锚：nop-task-core `impl/TaskImpl`（SUSPENDED 游离写 :159-160、四终态 driver :186-192/:202-208/:237-243/:252-258）、`impl/TaskRuntimeImpl`（cancel SUSPENDED 驱动 :97-105）、`impl/TaskFlowManagerImpl`（强引用注册表 :63-66、getRateLimiter :175-194、getSemaphore :197-207、warnIfOverCap :209-215）、`builder/TaskStepEnhancer`（executor :120-121 / sync :161-162 / 校验链 :132-155）、`step/SyncTaskStepWrapper` :15-18、`step/ExecutorTaskStepWrapper` :27-70、`step/ThrottleTaskStepWrapper` :55-65；nop-wf-core `impl/WorkflowManagerImpl`（getWorkflow :79-87）、`engine/WorkflowEngineImpl`（runXpl :1255、isExecGroupComplete :1267、doTransition :1280、checkEnd 调用 :1316/:1535、checkEnd 体 :1578-1595、doEndWorkflow :1597-1619）、`engine/ExecGroupSupport`（vote 判定 :33-72）；nop-wf-dao `store/DaoWorkflowStore`（saveWfRecord :130、findDueActivatedSteps :181-186、findRemindActivatedSteps :189-194）；nop-wf-scheduler `WfTaskScanner`（due :78-119、race skip :98-103、remind :122-141、remindCount RMW :128-135）；条带锁范式先例 = nop-task-dao `DaoTaskStateStore.rowLock`（64 槽 :344-352）
> Related：周期任务绝缘契约 = [periodic-task-insulation.md](periodic-task-insulation.md)（MA-23 扫描链 riding 面，§3.1 零回退）；R01 族级 SSOT = [ledger-retention-policy.md](ledger-retention-policy.md)（MA-08 进程内投影，§3.6 待增 R2-11 传导行）；R02 分批范式 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/inventory-ledgers.md` §二#17（MA-05 选型依据）；出锁投递/终结驱动先例 = [job-rpcpoll-termination-and-invoker-lock.md](job-rpcpoll-termination-and-invoker-lock.md)（裁定③ 校验链同码同位投影自其 §3.7 评审纪律）；R2-13（1.7-C7-001）引用本文档裁定⑥，不重建第二套 fencing

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 1.5-C3-001（P1；MA-02 主，←C3-011/C3-012/C1-002/C3-015） | wfInstanceId 级进程内条带锁（64 槽，rowLock 范式）经 `IWorkflowStore.withWfLock` 接口暴露、`DaoWorkflowStore` 实现（core 定义/dao 实现，依赖方向合法）串行化判定-迁移段 + 锁内重读兄弟步骤最新已提交状态；五站点逐一处置（§3.1 表，action 站点=锁段收窄出锁投递）；`saveWfRecord` 6 调用点全覆盖；跨进程残留显式声明；MA-17 实例线程封闭契约节（§3.2） |
| ② | 1.5-C1-001（P1；MA-01 主，←C2-001/C7-016/C1-009） | SUSPENDED 转移纳入 `synchronized(taskState)` 监视器（与四终态 driver 同监视器）+ 终态前置检查；C1-009 复活面闭合；resume 非终态确认后显式重置 ACTIVE 落盘；timeout-cancel 分类=KILLED；零新同步结构 |
| ③ | 1.5-C3-002（P1；MA-03 主，←C4-002/C2-010） | sync×executor 同池嵌套**构建期 fail-fast 禁止**（TaskStepEnhancer 校验链同码同位，ERR_TASK_STEP_CONFIG_INVALID 族）——唯一形态定夺为 fail-fast，不采专用池隔离；syncGet 接入 cancel 唤醒 + 时限 `nop.task.step.sync-wait-timeout-ms`（缺省 600000，0=仅取消唤醒） |
| ④ | 1.5-C5-001（P1；MA-05 主，←C8-001） | R02 分批范式：QueryBean limit+orderBy(dueTime) 分页游标推进（due/remind 同构同修）；批次上限 `nop.wf.scheduler.scan-batch-size`=500、时间预算 `nop.wf.scheduler.scan-time-budget-ms`=30000；毒步骤收敛=失败退避推进 dueTime（2^n，基准 60s 封顶 1h，零新字段零 DDL）；MA-06 显式失败计数字段接缝归 R4-08 |
| ⑤ | 1.5-C5-003（P1；MA-08 主，←C8-004；R01 族成员） | 键空间治理默认分支=**lastAccess+空闲逐出**（逐出延后到同 key computeIfAbsent 内联执行 + 引用一致 remove，无独立清扫位——permit 池完整性结构保证）；owner 表态出口两分支成文（§3.5），未表态→默认治理兜底；键 `nop.task.global-gate.idle-evict-ms`=300000 / `nop.task.global-gate.max-entries`=10000（超限周期 WARN + 渐进收敛口径）；registry.size gauge `nop.task.global-gate.registry-size`；淘汰参数键回 R01 SSOT 修订登记；C10-009 反向钉先改写（含并发获取×逐出交错 assertSame 断言） |
| ⑥ | 1.5-C7-001（P1；MA-23 主，←C1-005；R12 fencing 族建立批） | 条件 UPDATE 抢占（新增 `scanHolderId`/`scanLeaseTime` 两列，**ORM 结构 plan-first 批内申报**）+ holderId=`nodeId:UUID` + 单飞判据=rowsAffected==1 + remindCount DB 原子自增（`COALESCE(remindCount,0)+1`）；引擎 status 守卫零回退；R1-01 绝缘层 riding 零回退；R2-13 引用边界成文 |
| ⑦ | C10 验收前置 | C10 八条（001/003/004/005/006/008/009/011）随本体同 diff；红①–红⑥测试形态 + 零回退钉全列（§四） |

## 成员 → 裁定映射

| # | 成员（判级） | 面 | 裁定 | 修复位（live : 行） | 回归测试（缝，§四） |
|---|---|---|---|---|---|
| 1 | 1.5-C3-001（P1，MA-02 主；C3-011/C3-012/C1-002/C3-015 吸收） | wf 五站点快照竞态（判定-迁移无互斥） | ① | WorkflowEngineImpl :1264-1307 锁段、checkEnd :1578-1595、join 查建 :336-376、action 锁段收窄（runXpl 出锁）、endSubFlow 父侧 + `IWorkflowStore.withWfLock`/DaoWorkflowStore 锁体 + saveWfRecord 6 点 | 红②（C10-003/004/005） |
| 2 | 1.5-C1-001（P1，MA-01 主；C2-001/C7-016/C1-009 吸收） | SUSPENDED 游离写 × cancel 非原子 + KILLED 复活 | ② | TaskImpl :159-160 入监视器、TaskRuntimeImpl :97-105 语义保持、resume ACTIVE 重置 | 红①（C10-001） |
| 3 | 1.5-C3-002（P1，MA-03 主；C4-002/C2-010 吸收） | sync×executor 同池嵌套死锁 | ③ | TaskStepEnhancer 校验链（:132-155 同位插入）+ SyncTaskStepWrapper :15-18 有界等待 | 红③（C10-006） |
| 4 | 1.5-C5-001（P1，MA-05 主；C8-001 吸收） | 扫描无 LIMIT 全量驻留 + 毒步骤不收敛 | ④ | DaoWorkflowStore :181-194 查询族 + WfTaskScanner :78-119 消费游标 + 失败退避 | 红④（C10-008） |
| 5 | 1.5-C5-003（P1，MA-08 主；C8-004 吸收；R01 族成员） | global 闸门强引用注册表 × 动态 key 无界 | ⑤ | TaskFlowManagerImpl :175-215 治理改造 + owner doc 包络声明 | 红⑤（C10-009 先改写） |
| 6 | 1.5-C7-001（P1，MA-23 主；C1-005 吸收；R12 建立批） | 扫描无 fencing：due source 双跑 + remindCount 丢失 | ⑥ | nop-wf-dao 抢占 UPDATE + remind 自增 + WfTaskScanner :78-141 接线 | 红⑥（C10-011） |

## 二、背景与动机

R2-11 批六项 P1 覆盖 nop-task/nop-wf 三类缺陷面：

1. **跨请求快照竞态（MA-02 五站点）**：`getWorkflow` 每请求新建 `WorkflowImpl`（:79-87），步骤快照随实例固化——组完成判定（读快照）与组迁移（写库）之间无跨请求互斥，兄弟事务写不同 step 行乐观锁无冲突：and/seq 组双提交各自「成功」而组迁移永久丢失（流程滞留中间态，runAutoTransitions 只遍历 ACTIVATED 无补救）、流程终结滞留（checkEnd 异步触发读快照）、join 并发到达双实例（查建无互斥、orm 无唯一约束）、同 step action 副作用先于守卫双跑（runXpl :1255 先于 doTransition :1280）、兄弟子流结束回滚输家丢整个事务（C3-015）。
2. **task 引擎写者无守卫与同池自嵌套（MA-01/MA-03）**：SUSPENDED 转移是第五个 taskStatus 写者且游离于 taskState 监视器外（:159-160）——cancel 读到 ACTIVE 只设 token 不驱动 KILLED，kill/超时取消静默丢失，任务可被 resume 复活（C1-009 反向交错：KILLED 被 SUSPENDED 裸覆写）；`sync=true` 外层包装 × `executor` 内层包装同池嵌套时调用线程阻塞等待池内任务（syncGet 无超时、不响应取消唤醒），池饱和即全体永久互等。
3. **资源包络与多节点 fencing（MA-05/MA-08/MA-23）**：到期/提醒扫描无 LIMIT 全量载入单 ORM 会话 + 失败毒步骤 dueTime 永不清（每轮重入、集合单调不收敛）；global 闸门强引用注册表 × 动态 keyExpr 键空间持久无界（`${input.*}` 直通永久键，WARN-once 不可操作）；WfTaskScanner 多节点/重叠扫描无 fencing——dueAction source 双跑副作用先于状态守卫（近确定性双跑、成功侧静默翻倍）+ remindCount 双会话读-改-写丢失更新。

本文档一次裁定六项 + 测试缝，使 Phase 2 实施只有一种合法形态。

## 三、核心设计契约

### 3.1 裁定①：wfInstanceId 条带锁 + 五站点处置表

- **默认分支唯一结论 = wfInstanceId 级进程内条带锁 + 锁内重读**。**接线形态唯一结论 = 条带锁经 `IWorkflowStore` 接口暴露、由 `DaoWorkflowStore` 提供实现**：`IWorkflowStore`（nop-wf-core 既有接口）新增 `withWfLock(String wfId, Supplier<T> action)`（语义形态，签名以实施为准）——nop-wf-core 定义接口契约、nop-wf-dao 实现锁体（依赖方向 dao→core 合法：core 不依赖 dao，引擎只消费 store 接口，`WorkflowEngineImpl` 经 `step.getStore()`/注入的 store 取锁，绝不直接引用 `DaoWorkflowStore` 具体类）。锁体与 `DaoTaskStateStore.rowLock` 同构（64 槽静态槽位数组，key=`wfInstanceId` hash 取槽），实现落 nop-wf-dao；锁粒度为判定→迁移→落盘的完整段；进入锁后先经 store 重读同组兄弟步骤行（覆盖每请求 WorkflowImpl 快照互不可见缺陷），再执行组完成判定与迁移。重读形态 = 锁内按 wfId 加载全部 ACTIVATED 兄弟步骤的已提交状态并刷新判定输入；快照对象不复用请求初载值。vote 快照口径（`ExecGroupSupport.isVoteGroupComplete` :33-72：`member==step` 计完成票、`isExcludeInExecGroup` 成员级排除、缺省 passPercent=0.5）**零回退**——本裁定只改变快照数据的时新性，不改判定语义。
- **五站点逐一处置表**：

| 站点 | 现状（live 行） | 处置 |
|------|----------------|------|
| 组迁移（C3-011/C3-012 主面） | `isExecGroupComplete` :1267 判定读快照 → `doTransition` :1280 写库，双提交各自「成功」迁移永久丢失 | 锁段覆盖 :1264-1307（execGroup 判定 + skipExecGroupMembers + doTransition + doExitStep + activateNextSeqStep）；双线程并发 action 同 and 组 → 迁移恰好一次 |
| 流程终结滞留 | `checkEnd` :1578-1595 经 delayExecute :1316/:1535 异步触发，`willEnd`/`isAllStepsHistory` 读快照 | checkEnd 全段纳入 wf 锁；双实例并发 checkEnd → doEndWorkflow 恰好一次（`wf.isEnded()` 复检移入锁内） |
| join 双实例（and+or 两型） | join 步骤实例创建点（engine :336-376）查「已存在实例」与新建之间无互斥，orm 无 (wfId,stepName,joinGroup) 唯一约束 | 默认分支 = **锁内原子查建**：同一 wfInstanceId 锁内「查已存在 join 实例 → 复用或新建」串行化，双到达只产单实例。**零 DDL 前提下不采 DB 唯一约束**（orm.xml 结构变更触发 plan-first 且需要新增约束列语义）；应用层占位条件更新仅为锁方案不足时的备选，本裁定判定锁内查建充分（双到达必同 wf 实例）不启用 |
| action 双跑（C1-002） | `runXpl` :1255 副作用先于 doTransition :1280 守卫 | 默认分支 = **锁段收窄（三选一裁定取 ii）**：`runXpl` 本体**不持锁**（用户 source 可长执行，持锁将封锁同 wf 全部操作且跨 wf 互调构成 ABBA 死锁面——wf A 的 source 调 wf B 的 action 时 A 锁内取 B 锁，与反向调用交错即环）；锁段形态 = `runXpl` **前状态双检**（入 action 链先锁内校验 step 仍 ACTIVATED）+ `runXpl` 执行（无锁）+ **迁移段重取锁终检**（doTransition 前锁内复检 step 状态，已迁移/非 ACTIVATED 即拒绝——第二到达者的 source 已双跑一次但迁移被终检拒绝，`ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` 兜底复用；形态同 [job-rpcpoll-termination-and-invoker-lock.md](job-rpcpoll-termination-and-invoker-lock.md) §3.7 prepareJobLocked/startJobUnlocked 出锁投递范式投影）。**双跑窗残余论证**：锁外 runXpl 窗内第二个到达者可完整执行 source（副作用双跑一次）——该残余面仅存于同 wf 同 step 并发同 action 提交（审批流语义下单 step 单 owner 顺序操作，用户侧近乎不可达）且引擎 status 终检保证状态面恰好一次迁移，不引入占位/唯一约束（其仅为锁方案不足时备选，本裁定判定残余面可接受）；跨进程残留由 §3.1 边界声明统一承接 |
| 子流回滚重放（C3-015） | 兄弟子流结束经 `endSubFlow`（WorkflowCoordinatorImpl :55-75）通知父流程，输家乐观锁异常丢整个事务 | 父流程侧 notifySubFlowEnd 段纳入父 wf 锁 + 锁内重读父步骤；输家不再以乐观锁异常失败——串行化后只有一次有效迁移，另一侧见状态已迁移走幂等短路（INFO 留痕） |

- **`saveWfRecord` 6 调用点覆盖约束（C1-010）**：wf 实例状态写点 `WorkflowEngineImpl` :148/:470/:659/:682/:1615 + `WfRuntime` :321 转发体——全部 6 点必须处于 wf 条带锁段内（`doEndWorkflow` 内 :1615 随 checkEnd 锁段天然覆盖；:148 start、:470/:682 activate、:659 suspend 各自入口段补锁）；漏接任一点即复刻「状态位非原子」残留面。
- **跨进程残留边界显式声明**：条带锁只覆盖单 JVM；多节点部署下五站点的跨请求互斥退化为 DB 乐观锁（现状），跨进程竞态残留 = 多节点同时判定同组完成（双迁移由 DB 乐观锁拦截一方，但判定面快照仍可能各自成功）。DB 级 `SELECT ... FOR UPDATE` 为备选分支，本裁定不启用（引入 DB 锁放大与事务边界耦合，单节点部署为主形态下收益不成比例）；多节点部署 + 关键审批流场景的幂等要求落 owner doc 声明。
- **锁序声明**：wf 条带锁与 task 侧 `taskState` 监视器为独立锁域，全平台无任何路径同时持双域锁（wf 引擎不触 task taskState，task 引擎不取 wf 条带锁；nop-ai 编排层跨两者仅经异步 future 边界，不嵌套持锁）——无环成立。锁域内唯一嵌套 = withWfLock 段内经 store 重读（DB 访问，无进程锁获取）；禁止在 withWfLock 段内调用会获取其他 wf 条带锁的引擎入口（§3.1 action 行 ABBA 面由此条约束兜底：跨 wf 互调经 runXpl 无锁窗自然无环）。
- **MA-17 实例线程封闭契约节（随裁定落 owner doc 句，Phase 3 统一落账）**：`WorkflowImpl`/`WorkflowStepImpl` 实例为每请求新建（`getWorkflow` :79-87）、步骤快照随实例固化，**不保证实例级线程安全，禁止跨请求/跨线程持有**；并发正确性由 wfInstanceId 条带锁在引擎判定-迁移段提供，不由实例同步提供。

### 3.2 裁定②：task SUSPENDED 转移监视器收敛

- **唯一结论 = :159-160 写+落盘纳入 `synchronized(taskState)`**，块内前置终态检查（`skipTerminalOverwrite` 同判：已终态则不写 SUSPENDED，走既有终态路径语义）。与四终态 driver（:186-192/:202-208/:237-243/:252-258）同监视器，判定-写入原子化：交错双序均收敛——执行线程先入锁则写 SUSPENDED 成功，cancel（:97-105）后入锁见 SUSPENDED 驱动 KILLED；cancel 先入锁写 KILLED 则挂起分支终态前置检查短路，C1-009 复活面（KILLED 被 SUSPENDED 裸覆写）闭合。
- **C1-009 resume 半边**：resume 路径在非终态确认（监视器内 `isTerminal()` 复检）后显式重置 ACTIVE 并落盘；终态任务 resume 显式拒绝（既有 isTerminal 短路语义保持）。
- **timeout-cancel 分类裁定**：挂起任务被 timeout-cancel 记 **KILLED 非 TIMEOUT**——TIMEOUT 语义保留给在飞步骤的 step-timeout（`driveTaskTerminal` reason 分发），挂起任务无在飞步骤、取消归类为 kill 语义，与 plan 364 [05-03] 驱动路径（恒 KILLED）一致；cancel 放弃路径（非 SUSPENDED 非 ACTIVE 命中）补 debug 留痕。
- **零新同步结构声明**：仅复用 taskState 监视器，无新锁、无锁序新增（该裁定若违背须 C2 锁序复核）。

### 3.3 裁定③：sync×executor 构建期互斥 + syncGet 有界等待

- **唯一形态定夺 = 构建 fail-fast，不采专用池隔离**。`sync && !StringHelper.isEmpty(executor)` 组合在 `TaskStepEnhancer.wrap` 装配期拒绝，落既有校验链同码同位（timeout 硬失败 :132-137 / rateLimit 硬失败 :147-155 之间），`invalidStepConfig` 形态抛 ERR_TASK_STEP_CONFIG_INVALID 族——与 [02-06] 先例同码同位零新结构。专用池隔离被拒：嵌套可经 retry/多层步骤间接加深，池隔离只防直接同池一层且引入装配面；模型层误用应在装配期响亮失败（rule 24）。
- **syncGet 有界等待（C2-010 闭合）**：`SyncTaskStepWrapper.execute` :15-18 的 `.sync()` 改为带取消唤醒的等待——接入 stepRt 取消 token（cancel 触发等待中断/异常完结，与 `ExecutorTaskStepWrapper` 取消兜底 :51-70 对称）+ 可配置时限键 `nop.task.step.sync-wait-timeout-ms`，缺省 **600000**（10 分钟），`0`=不限时（仅取消唤醒），负值装配期 fail-fast。时限超时抛 ERR_TASK_STEP_CONFIG_INVALID 族超时错误（显式失败，非静默吞）。C4-002 池治理视角随 fail-fast + 有界等待闭合挂死面；TaskQueue grow-then-queue 本体治理归 R4 边界声明不代做。
- **C10-006 回归形态**：pool-size=1 专用池 + sync×executor 模型注入 → 缺陷态构建被拒（修复后形态），缺陷面另以 syncGet 时限变体验证等待有界。
- **upgrade 声明**：本裁定为破坏性构建期约束——存量模型若存在 sync+executor 组合，升级后装配即 fail-fast；**存量模型须先移除该组合再升级**（owner doc 升级注记落 Phase 3，随 fail-fast 契约句一并声明；不做静默兼容降级，rule 24）。

### 3.4 裁定④：扫描分批游标 + 毒步骤收敛

- **R02 三范式选型 = 分批**（inventory-ledgers §二#17）：`findDueActivatedSteps` :181-186 / `findRemindActivatedSteps` :189-194 改 `QueryBean.limit(batchSize)` + `orderBy(dueTime asc)`（remind 侧 orderBy(remindTime)），消费侧 `WfTaskScanner` 按游标推进（上批末条 dueTime 为下批下界谓词 `>`），无全量物化；单轮扫描在时间预算内推进多批，预算耗尽剩余面留待下轮（cron 1min/5min 自补偿）。
- **配置键与缺省值一次定夺**：`nop.wf.scheduler.scan-batch-size` 缺省 **500**（`<=0` 装配期 fail-fast）；`nop.wf.scheduler.scan-time-budget-ms` 缺省 **30000**（`<0` fail-fast，`0`=单批即止）。
- **毒步骤收敛（唯一结论 = 失败退避推进 dueTime，零新字段零 DDL）**：due action 执行失败（:112-118 ERROR 分支）时将该步骤 dueTime 推进 `now + min(60s × 2^n, 1h)`（n=该步骤连续失败轮次，由扫描器按步骤内存计数 + dueTime 推进量自恢复；重启后计数归零按基准 60s 重启退避，可接受）；成功后步骤经 action 迁移退出 ACTIVATED 扫描集。收敛判据 = 失败条目退出本轮扫描集且重入频率指数衰减，扫描集有界收敛。
- **MA-06 接缝定界（一次写清）**：本批定下的字段语义 = dueTime 兼作「下次重试时间」（退避记账载体，扫描谓词唯一时间字段）；显式失败计数列/隔离标记列/due-backlog gauge 等 MA-06 本体四失效面归 R4-08——R4-08 若需独立失败计数字段，由其自带 ORM 结构申报，不回溯本批零 DDL 前提。
- **退避计数 map 有界策略**：扫描器内存的连续失败轮次计数 map 有界——**entry 随该步骤退出 ACTIVATED 扫描集（成功迁移/被取消）时摘除**；额外兜底 cap=10000（超出即整体清空并 WARN 一次，计数归零重启退避，只损退避精度不损收敛性）；重启丢计数为已声明可接受语义（§3.4 毒步骤收敛条）。
- **remind 半区同构同修不豁免**；精度备注维持：remindTime 引擎侧无写入点（remind 半区当前恒空集），P1 由 due 半区独立支撑。查询形状（limit/orderBy 存在）与收敛性（毒步骤退出）断言 = C10-008 随同 diff。

### 3.5 裁定⑤：闸门注册表键空间治理（owner 表态出口）

- **两分支呈现（owner 表态出口成文）**：(a) 准入约束——global=true 时 keyExpr 须解析为声明式低基数键，动态 EL 键构建期拒绝 + owner doc 声明「动态 global key 不支持」缺口（本族降文档声明缺口）；(b) 空闲逐出治理——保留动态键用法，lastAccess + 空闲超阈逐出。**表态机制**：owner = nop-task owner doc 维护方，表态落本文档 Decision 记录（本节追加「owner 表态：YYYY-MM-DD，分支 X」一行）；未表态→默认治理分支兜底，不阻塞实施。
- **设计者唯一预设默认分支 = (b) lastAccess+空闲逐出**：audit C5-003 认定动态 key 系 global throttle 特性主流用法（`${userId}`/`${entityId}` 直通），准入约束把合法特性变为构建期拒绝属行为面收缩；逐出在约束前提下正确性无损。**permit 池完整性约束（plan 364 [05-04] 修订前提，逐出仅当**：信号量 permits 满额（无在途持有者，以 permits==maxPermits 为判据）且 rate limiter/semaphore 自 lastAccess 起空闲 ≥ `nop.task.global-gate.idle-evict-ms`（缺省 **300000**）。
- **逐出执行位唯一结论（三选一裁定取 iii，含 i 机制原语）**：**逐出延后到下一次同 key 的 `computeIfAbsent` compute 函数内联执行**——命中已有 entry 时锁内（CHM bin 锁）先按判据复核（空闲超阈且 permits 满额），命中则 `map.remove(key, entryRef)` **引用一致校验移除**（两参 remove，防并发替换误删他实例）后新建返回新实例；不满足判据则续用旧实例并刷新 lastAccess。**无独立清扫位**（不采遍历式惰性清扫）：全 map 扫描与单 key 获取之间存在 check-then-act 窗——另一线程同刻获取同 key 拿到旧实例、清扫方随即 remove + 新建即复现 permit 分裂（Major-3 判据）；同 key compute 内联天然同 bin 锁串行，配合引用一致 remove 窗口收缩至 compute 返回→acquire 完成且跨越 idle 阈值（缺省 300s）的停顿面，量级远超调度停顿、实践不可达该窗。max-entries 超限时的处理相应收窄：**超限只触发周期 WARN（60s 间隔）+ gauge 可见，不做全 map 强制清扫**（无独立清扫位即无全 map 扫描位）；上界由「同 key 复访即触发该 key 逐出复核」渐进收敛——冷 key（不复访）驻留为有界残留（≤历史取值域），由 owner doc 包络声明承接。不可逐出（在用）entry 挤压超限时周期性 WARN + fail-fast 包络声明落 owner doc（R01 族纪律三要素投影：上限=max-entries[渐进收敛口径]、逐出=空闲逐出、fail-fast=错配置装配期拒绝）。**输入直通面覆盖**：`${input.*}` 直通键为普通 entry 同逐出规则，无豁免。
- **R01 对齐投影**：MA-08 本体为进程内注册表（形态菜单 A–D 之外，非 DB 账本表），本批零 DB 保留参数；但裁定引入淘汰参数键两枚（idle-evict-ms/max-entries）→ **触发 R01 SSOT §3.6 修订登记义务**（增 R2-11 传导行：进程内形态、键名、缺省值、理由），随 plan Phase 3 owner-doc 落账同步提交 SSOT 修订，不得模块内静默发明。
- **观测半面**：gauge `nop.task.global-gate.registry-size`（两注册表各一，tag registry=rate-limiter/semaphore，经 GlobalMeterRegistry，未初始化静默跳过——绝缘层观测通道同形态）；MA-09 stats 分页/聚合全量治理归 R4-08 注记。
- **C10-009 反向钉改写契约（先改写后修本体）**：`TestPhase4TerminalAndConcurrency.globalGates_strongRefRegistry_sameInstanceAcrossManyKeys`（:175-194）保留「同 key 同实例」断言（permit 池不分裂语义不变，重命名反映治理后契约），新增断言：注入超过 max-entries 的空闲动态键 + 时钟推进超 idle-evict-ms → 复访 key 触发逐出、新建实例（registry.size 回落）；在用 entry（permits 非满额）不被逐出对照断言；**红⑤补断言（Major-3 对应）**：并发获取 × 逐出交错（多线程同 key 获取与同 key 逐出复核竞争，latch 对齐）下 `assertSame` 仍成立——同一时刻窗口内所有获取者拿到同一实例（permit 池不分裂的结构性证明）。

### 3.6 裁定⑥：WfTaskScanner fencing（R12 族建立批）

- **唯一形态 = 条件 UPDATE 抢占（holderId+租约）**：nop-wf-dao 新增扫描抢占列 `scanHolderId`（VARCHAR(64) 可空）+ `scanLeaseTime`（TIMESTAMP 可空）——**ORM 结构 plan-first 批内申报**（`nop-wf/model/nop-wf.orm.xml` 两列 + 迁移脚本随批，本设计文档即申报载体；除此之外零 orm 触碰）。抢占语义：`UPDATE nop_wf_step_instance SET scanHolderId=:holder, scanLeaseTime=now+lease WHERE stepId=:id AND status=ACTIVATED AND (scanLeaseTime IS NULL OR scanLeaseTime < now)`——rowsAffected==1 赢单飞，==0 败者以既有 race skip INFO 形态跳过（:98-103 形态复用，非吞）。租约 `nop.wf.scheduler.scan-lease-ms` 缺省 **120000**（2 分钟，> action 执行 p99；崩溃节点租约到期后他节点可重入，at-least-once 语义保持）。跨进程/单节点重叠扫描同机制覆盖（裁定⑥覆盖 due+remind 两路径）。
- **迁移方案三行（Major-2 裁定）**：(a) **DDL 语句形态** = `ALTER TABLE nop_wf_step_instance ADD COLUMN IF NOT EXISTS scan_holder_id VARCHAR(64) NULL;` + `ALTER TABLE nop_wf_step_instance ADD COLUMN IF NOT EXISTS scan_lease_time TIMESTAMP NULL;`——以 PostgreSQL `ADD COLUMN IF NOT EXISTS` 幂等方言为基准口径（部署脚本可重复执行）；其他方言部署由 DBA 按语义等价转写，平台只维护 postgresql 基准脚本。(b) **脚本落点** = 新建**非 `_` 前缀**迁移脚本 `nop-wf/deploy/sql/postgresql/`（如 `2026-10-add-wf-step-scan-fencing.sql`）——避开 `_create_nop-wf.sql` 生成物（AGENTS.md `_` 前缀硬停）；若需同步 `_create` 全量建表脚本，须走 orm 模板/生成链改动 + 重建声明，不在本批顺手手改。(c) **生成链路声明**：`_gen` 下实体类 `NopWfStepInstance` 由 `nop-wf/model/nop-wf.orm.xml` 经 codegen 重建生成（mvn install 覆写）——两列接入实体只改 orm.xml 源模型后重建，**禁止手改 `_gen/*.java`**。
- **实体写路径列集约束（评审 ⑤ 增补）**：`scanHolderId`/`scanLeaseTime` 两列**只经直发 UPDATE 语句读写**（条件抢占与放弃/租约清理），不经 ORM 实体逐字段落盘——扫描链内既有实体写路径（裁定④ 退避 dueTime 推进、remind 的 remindCount 之外的实体字段写）必须**限定列集**（orm 直发语句只 SET 目标列）或**写前 reload 实体**再改写，防止以扫描会话中的陈旧实体全字段 save 覆写他节点刚写入的抢占列（自踩 fencing）。
- **holderId 生成**：`nodeId:UUID`——nodeId 取配置 `nop.wf.scheduler.node-id`（缺省 = 主机名 + 进程启动随机后缀，装配期一次生成）；UUID 每次抢占新生成，仅诊断归因用，fencing 判据唯一为条件 UPDATE 原子性。
- **remindCount DB 原子自增**：:128-135 读-改-写废弃，改 `UPDATE ... SET remindCount=COALESCE(remindCount,0)+1, remindTime=NULL WHERE stepId=:id` 直发语句（经 stepDao），丢失更新消除；remind 扫描同样先抢占后执行 listener。
- **层级关系与零回退**：引擎 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` 现役兜底保持（:98-103 race skip 钉 `TestWfTaskScanner.testScanDueTasksIgnoresCurrentStepStatusRace` 零回退）——fencing 为事前抢占，status 守卫为事中兜底，双层并存不合并；nop-job LocalJobScheduler 无集群锁的部署形态声明落 owner doc（fencing 使多节点 cron 重叠无害化，不引入 nop-cluster 依赖）。扫描链 riding R1-01 绝缘层零回退：单条抢占/执行失败仍走条级 catch（:97-118 形态），链存活与失败可见契约（periodic-task-insulation §3.1）不回归。
- **R2-13 引用边界条款**：本文档裁定⑥为 R12 族平台 fencing 语义权威——holderId/条件 UPDATE 抢占/单飞判据/租约与 status 守卫层级关系在此定稿；R2-13（1.7-C7-001 RUNNING 死区）到达时引用对齐，不重建第二套 fencing。平台层上收申报触发条件：若后续批需要跨模块通用扫描抢占原语（nop-kernel/nop-api-core 层），须彼批申报框架核心 plan-first——本批预设 nop-wf 模块内零平台层触碰。
- **与裁定①锁的关系**：抢占为 DB 层原子操作，不持进程内 wf 锁；赢单后续 `invokeActionAsync` 进入引擎方按裁定①取 wf 锁——先 DB 抢占后进程锁，无锁序环。

### 3.7 本批配置键一览（缺省值一次定夺，零键名二源）

| 键 | 缺省 | 裁定 | 语义 |
|----|------|------|------|
| `nop.task.step.sync-wait-timeout-ms` | 600000 | ③ | sync 包装等待时限；0=不限时（仅取消唤醒）；负值装配期 fail-fast |
| `nop.wf.scheduler.scan-batch-size` | 500 | ④ | 扫描分批单批上限；`<=0` 装配期 fail-fast |
| `nop.wf.scheduler.scan-time-budget-ms` | 30000 | ④ | 单轮扫描时间预算；`<0` fail-fast，0=单批即止 |
| `nop.task.global-gate.idle-evict-ms` | 300000 | ⑤ | 闸门注册表空闲逐出阈值（配 max-entries 惰性清扫） |
| `nop.task.global-gate.max-entries` | 10000 | ⑤ | 注册表超限周期 WARN 阈值（逐出为同 key 复访渐进收敛口径，无全 map 清扫位） |
| `nop.wf.scheduler.scan-lease-ms` | 120000 | ⑥ | 扫描抢占租约时长（崩溃恢复窗） |
| `nop.wf.scheduler.node-id` | 主机名+启动随机后缀 | ⑥ | holderId 归因前缀（装配期一次生成） |
| gauge `nop.task.global-gate.registry-size` | — | ⑤ | 注册表规模 gauge（两注册表，tag registry） |

## 四、裁定⑦：测试缝与零回退钉

### 4.1 红①–红⑥驱动器（先红后绿，E2；缺陷态红判据 + 修复后绿口径）

| 红 | 载体 | 缺陷态红 | 修复后绿 |
|----|------|---------|---------|
| ①（C10-001） | `TestPhase4TerminalAndConcurrency` 扩展：latch 控制挂起分支写 SUSPENDED 前挂起 × 并发 cancel 线程 | KILLED 不即时驱动（cancel 只设 token）/KILLED 被 SUSPENDED 覆写 | KILLED 即时驱动；C1-009 复活面对称绿 |
| ②（C10-003/004/005） | nop-wf-core 测试树：双线程并发 action 同 and 组 / 末段并发完成 / join 并发到达 | 组迁移丢失（下游未创建）/实例滞留 ACTIVATED/join 双实例 | 迁移恰好一次、不滞留、单实例 |
| ③（C10-006） | pool-size=1 专用池 sync×executor 装配 + syncGet 时限变体 | 缺陷态互等超时；修复面=构建期拒绝 | 装配 fail-fast 抛 ERR_TASK_STEP_CONFIG_INVALID + syncGet 取消/时限路径有界 |
| ④（C10-008） | 毒步骤注入（due action 恒失败）+ 查询形状断言 | 扫描集不收敛、无 limit/orderBy | 查询形状存在 + 毒步骤退避退出本轮且重入衰减 |
| ⑤（C10-009） | 动态高基数键注入（时钟注入） | registry.size 无界 | 有界回落 + 在用 entry 零逐出对照 |
| ⑥（C10-011） | 双 scanner 竞速（`TestWfTaskScanner` proxy 注入体系 :447-592 扩展 / DB 集成） | 触发次数==2、remindCount 丢失更新 | 触发恒==1、remindCount 终值恒==轮数 |

### 4.2 零回退钉（全列，钉零回改逐条 git diff 为空核对）

- `TestPhase4TerminalAndConcurrency` [05-03] `suspendedTask_cancel_drivesKilledWithoutResume`（:159-171）——MA-01 关键钉：修复后 cancel 仍对 SUSPENDED 即时驱动 KILLED（监视器收敛不改该语义）。
- 同文件 [05-04] `globalGates_strongRefRegistry_sameInstanceAcrossManyKeys`（:175-194）——按 §3.5 C10-009 契约**先改写**（保留同 key 同实例断言 + 新增上界/不逐出对照），改写记录随同 diff 在案。
- `TestWfTaskScanner.testScanDueTasksIgnoresCurrentStepStatusRace`（:112）——引擎 status 守卫兜底零回退；其余 6+ 测试与 proxy 注入体系零回改。
- `ExecGroupSupport` vote 快照口径（:33-72 成员级排除/`member==step` 计票/缺省 0.5）——裁定①零回退（判定语义不改、只改快照时新性）。
- `RetryTaskStepWrapper` executor 隔离（`TaskStepEnhancer` :115-118，plan 2498 落地面）——零触碰零回退。
- plan 364 [05-02] `DaoTaskStateStore.rowLock` 范式体、[05-03] cancel 驱动块（:97-105）、[05-04] 强引用注释（:59-66）——引用不重开、零 diff。
- R1-01 绝缘层契约（扫描链条级 catch 形态 :97-118）——零回退。

### 4.3 C10 八条集落名

C10-001（MA-01 收口门，红①）、C10-003（MA-02 组迁移面，红②）、C10-004（C3-011 末段并发面，红②）、C10-005（C10-012 join 双实例面，红②）、C10-006（MA-03 收口门 pool-size=1，红③）、C10-008（MA-05 收口门查询形状+收敛，红④）、C10-009（MA-08 反向钉先改写，红⑤）、C10-011（MA-23 双 scanner 竞速，红⑥）——八条均随对应本体同 diff 落地（E2）；补测类声明：新增断言即新钉、缺陷态红→修复后绿；上游环境抖动按 plan 2512/2513 三联裁定处理留痕。

### 4.4 接线与无静默跳过义务（rule 23/24）

- **接线验证**：裁定② SUSPENDED 写点真实经 taskState 监视器路径断言（非仅 synchronized 关键字存在）；裁定① wf 条带锁真实被 action/checkEnd/join 链消费断言（并发交错测试即接线证据）；裁定⑤ 治理真实被 `getRateLimiter`/`getSemaphore` computeIfAbsent 路径消费断言（gauge 值随注入变化）；裁定⑥ 条件 UPDATE 真实被 scanner 循环调用且抢占失败分支可见（skip INFO 断言，非仅 SQL 存在）。
- **无静默跳过**：裁定③ 构建期拒绝显式抛 ERR_TASK_STEP_CONFIG_INVALID 族（非静默忽略组合）；裁定⑥ 抢占失败显式 skip 日志路径（复用 race skip INFO 形态）；裁定④ 失败退避路径显式可见（ERROR + 退避量留痕，非空 catch）；逐出清扫显式计数/日志；全部新增公共分支无空方法体/TODO。
- **端到端验证义务**（rule 22，归 Phase 2）：task 全链（模型构建 fail-fast 拒绝面→执行→挂起→cancel→终态落盘）+ wf 全链（and 组双人提交→组迁移→流程终结 + due 扫描 job 启用→抢占→action 恰好一次→dueTime 推进收敛）。

## 五、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ①③ DB 唯一约束解 join 双实例 / `SELECT FOR UPDATE` 作为默认 | ORM/DDL 结构 plan-first + 锁放大与事务耦合；锁内原子查建在单 JVM 内充分，跨进程面显式声明残留 |
| ① 各站点分散加锁（只锁 doTransition / 只锁 checkEnd） | 判定-迁移窗在站点间共享，分散锁复刻「部分原子」残留；单一 wf 锁段一次闭合五站点 |
| ② 新增独立 SUSPENDED 锁/状态机重写 | 与四终态 driver 监视器分叉即第二同步结构；同监视器收敛零新增锁序 |
| ③ 专用池隔离 sync×executor 嵌套 | 只防直接一层、装配面扩张、间接嵌套漏防；模型误用应装配期响亮失败 |
| ④ setLimit+fail-fast 一次截断 | 截断外到期步骤被静默滞留（比全量载入更危险）；分批游标保消费完整性 |
| ④ 毒步骤隔离标记列 | 需 ORM 结构变更且为 MA-06 本体面；dueTime 退避零 DDL 即达收敛 |
| ⑤ 准入约束作默认分支 | 动态 key 系特性主流用法，构建期拒绝属行为面收缩；owner 未表态不得否定存量用法 |
| ⑤ 后台逐出线程/定时器 | 零新线程原则；惰性清扫由 computeIfAbsent 路径内联即充分 |
| ⑥ 分布式调度锁（nop-cluster 依赖） | pom 越界 + 部署形态假设；条件 UPDATE 在现有 ORM 会话内即闭合 |
| ⑥ 幂等键 wfId+stepId+dueTime 去重 | 仍需唯一约束（DDL）且重放窗语义复杂；holderId 租约一次定夺 |
| ⑥ dueTime 兼作租约（免新增列） | 污染业务到期语义并与裁定④退避记账冲突 |
| ⑥ fencing 状态外置表（独立 `nop_wf_step_scan_claim` 表，外置 claim 表为全新表，`CREATE TABLE IF NOT EXISTS` 对存量部署会正常建表生效（恰与 ADD COLUMN 相反），免 ALTER 属实；但代价是抢占谓词跨表两段式 + 孤儿 claim 清理面，不抵两列内联零额外实体的收益。

## 六、与已有设计的关系

- [periodic-task-insulation.md](periodic-task-insulation.md)：R1-01 平台方案——WfTaskScanner 扫描链的链存活/失败可见面消费其 §3.1 语义，裁定⑥零回退（§3.6）；两层正交（设施管链存活，本批管 DB 抢占）。
- [ledger-retention-policy.md](ledger-retention-policy.md)：R01 族级 SSOT——MA-08 为族成员，裁定⑤三要素投影进程内形态；淘汰参数键触发 §3.6 修订登记（义务已声明）；MA-05 分批为 R02 范式非 R01 面。
- [job-rpcpoll-termination-and-invoker-lock.md](job-rpcpoll-termination-and-invoker-lock.md)：构建期校验同码同位纪律与「一处定夺+被拒备选成表」范式投影；其 §3.7 评审 Blocker（监视器可重入机制约束）为本批裁定②「不引入新同步结构」的同类机械约束来源。
- 下游：R2-13（8.22）引用裁定⑥不重建（Deps 勘误申报归 plan Phase 4）；R4-08（8.48）/R4-09（8.49）软依赖就绪注记（MA-06 毒步骤字段接缝=§3.4、MA-09 观测面接缝=§3.5、C7-014 分立声明）归 plan Phase 4。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/03-modules/nop-task.md`（§挂起与恢复原子化契约句、sync×executor 约束、global-gate 键空间治理节、源码锚点补行）+ `docs-for-ai/03-modules/nop-wf.md`（扫描/fencing/组语义契约补节，含 MA-17 实例线程封闭声明）+ `docs-for-ai/02-core-guides/workflow-configuration.md`（dueAction 多节点语义句，按实际）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
