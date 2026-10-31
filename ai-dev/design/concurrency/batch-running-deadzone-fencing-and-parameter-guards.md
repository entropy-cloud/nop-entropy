# batch RUNNING 死区 fencing 与参数护栏契约（R2-13 批八项裁定）

> Status: active
> Last Reviewed: 2026-10-30（plan 2516 Phase 1 产出；本批唯一设计裁决权威）
> Source: plan 2516（`ai-dev/plans/2516-cra-phase8.22-r2-13-batch.md`）Current Baseline 八成员表 + Baseline 勘误 7 条；发现事实源 = WI 1.7 审计目录 `ai-dev/audits/2026-10/2026-10-16-1258-deep-audit-nop-batch-concurrency/`；行号 = live `ce4f846ca1`（分支 `concurrency-audit-remediation`）2026-10-30 亲读回读值，执行期漂移以 live 为准并登记于 plan 勘误
> 实现锚：nop-batch-dao `store/DaoBatchStateStore`（启动闸门 :106-150、`tryMarkTaskRunning` :187-195、`saveTaskState` :234-256）、nop-batch-core `impl/BatchTask`（executeAsync :119-186、`onTaskComplete` :204-249、`executeChunkLoop` :251-298、`initTaskKey` :194-202）、`core/loader/PartitionDispatchQueue`（构造 :78-87、Guard 对照 :134、`acquirePermits` :337-350）、`core/BatchTaskBuilder.buildLoader`（:340-356）、nop-batch-exp `ExportDbTool.runTask`（:276-284）/`ImportDbTool`（:302-303）、nop-batch-jdbc `loader/JdbcBatchLoaderProvider`（坏门 :243-247）、nop-batch-dsl `manager/FileBatchSupport.newExcelReader`（:140-158）、nop-report-core `record/ExcelRecordInput`（beforeRead :86-98）
> Related：MA-23 fencing 权威 = [wf-task-scanner-fencing-and-task-wf-concurrency.md](wf-task-scanner-fencing-and-task-wf-concurrency.md) §3.6 裁定⑥（**本批裁定①为引用方，引用不重建**；偏离须回 2514 修订登记）；R1-01 绝缘层 = [periodic-task-insulation.md](periodic-task-insulation.md) §3.1（裁定⑥⑦失败可见语义消费其管线形态，零回退）；plan 2496（C6-001/C6-010 已修面 + owner doc §任务异常终态契约）/plan 2502（三表保留清理，零触碰）/plan 2381（primaryError CAS 钉）零回退

## 一、裁定总表

| 裁定 | 成员（判级） | 唯一结论 |
|------|------------|---------|
| ① | 1.7-C7-001（P1；族 A2 主；C10-002；N1 前置） | 零 orm 触碰 stale 接管：复用 `restartTime` + 租约超时条件扩展 `tryMarkTaskRunning` WHERE（NULL 判 stale）+ checkpoint 续租；条件 UPDATE+rowsAffected==1 单飞对齐 2514 裁定⑥；快失败守卫收窄为 live 行快速路径（错误码语义零回退）+ saveTaskState 条件 UPDATE 防僵尸写者（偏离登记回 2514 归 Phase 3） |（newTask 补租约初始写点后，NULL 仅存在于存量死区行）
| ② | 1.7-C8-001（P1；←C5-005；C10-003；N3 搭车） | `BatchTaskBuilder.buildLoader` 装配期 fail-fast：`loadBatchSize<=0` 抛类型化 NopException（携 loadBatchSize 与 capacity 公式上下文）；队列构造 Guard 与 xdef min 为备选不启用 |
| ③ | 1.7-C8-002（P1；←CL-008；C10-004） | `ExportDbTool` 复制 `ImportDbTool` 的 `>0` 守卫（消除不对称）+ `adjustBatchSize` 钳位补每任务一次 WARN；钳位上移 Guard 与 xdef 缺省改 1000 不启用 |
| ④ | 1.7-C5-001（P1；C10-011 必补；G79 联动） | 零 nop-report 触碰包络分支：恢复 `maxCountExpr` 接线 + owner doc 规模上限声明（物化面残留登记 R-G watch-only）；游标式改造 `ExcelRecordInput` 拒绝 |
| ⑤ | 1.7-C5-002（P1；N2 同步改写前置；C10-005 半边） | 坏门 `&&` 改独立兜底 `if (fetchSize != null) ps.setFetchSize(fetchSize)`（provider 层只兜底，sql 层归 JdbcHelper）；未生效路径补 WARN；N2 契约句同步改写 |
| ⑥ | 1.7-C6-002（P1；←C7-002A；C10-001） | 双层封闭单一变更集：`executor.execute` 提交点 REE 防御（future.completeExceptionally）+ executeAsync futures 提交循环 try/catch 包络（已提交幽灵链逐个显式结算后直达 onTaskComplete） |
| ⑦ | 1.7-C6-009（P2 搭车；G21；C10-002；族 A2 配套①） | S4/S5/S6 清理段显式化：S4 终态写失败 catch+ERROR 留痕不替换原始 err；S5 `metrics.endTask` 独立 try 保 `FutureHelper.complete` 必达（不改顺序）；S6 X 不替代原始 e |
| ⑧ | 1.7-C7-011（P2 搭车；G21；C10-002） | 被触发配置 `non-broadcast-consumer.batch.xml` 补 `saveState="true"` + 固定常量 taskKeyExpr（守卫接线闭合）+ owner doc 跨单元契约显式化伴随成文；singleActive 解耦与 nop-job/nop-sys 改码拒绝 |

## 成员 → 裁定映射

| # | 成员（判级） | 面 | 裁定 | 修复位（live : 行） | 回归测试（缝，§四） |
|---|---|---|---|---|---|
| 1 | 1.7-C7-001（P1，族 A2 主） | RUNNING 死区无 stale 判定 | ① | DaoBatchStateStore `tryMarkTaskRunning` :187-195 WHERE 扩展 + `saveTaskState(false)` 续租 :236-239 | 红①（C10-002） |
| 2 | 1.7-C8-001（P1，←C5-005；N3 搭车） | dispatch 容量零护栏 | ② | BatchTaskBuilder.buildLoader :340-356 装配期 fail-fast | 红②（C10-003） |
| 3 | 1.7-C8-002（P1，←CL-008） | Export/Import 守卫不对称 | ③ | ExportDbTool :278 守卫 + BatchChunkProcessor :89-90 钳位 WARN | 红③（C10-004） |
| 4 | 1.7-C5-001（P1；C10-011） | excel-reader 全 sheet 物化 | ④ | FileBatchSupport :150-151 maxCount 接线恢复 + owner doc 包络声明 | 红④（C10-011） |
| 5 | 1.7-C5-002（P1；N2 前置） | fetchSize 坏门死配置 | ⑤ | JdbcBatchLoaderProvider :243-247 独立兜底 + WARN | 红⑤（C10-005 半） |
| 6 | 1.7-C6-002（P1，←C7-002A） | REE 裸逃逸绕过终态化 | ⑥ | BatchTask :255 提交点防御 + :173-175 循环包络 | 红⑥（C10-001） |
| 7 | 1.7-C6-009（P2，G21；族 A2） | 终态写失败静默吞没 | ⑦ | BatchTask.onTaskComplete :233-248 S4/S5/S6 显式化 | 红⑦（C10-002） |
| 8 | 1.7-C7-011（P2，G21） | sys-event 链守卫旁路 | ⑧ | non-broadcast-consumer.batch.xml 补 saveState+taskKeyExpr | 红⑧（C10-002） |

## 二、背景与动机

nop-batch 状态机存在两条互锁缺陷：终态保存失败或进程崩溃后，`DaoBatchStateStore.startExistingTask` 对 `taskStatus <= RUNNING` 无条件拒绝（:123-129）、`tryMarkTaskRunning` WHERE 仅 `taskStatus > RUNNING`（:192）无 stale 判定——任务行退化为永久死区，cron 每 5s 触发错误风暴（≈17,280 条/天/任务）；而终态写失败又被 `BatchTask.onTaskComplete` 的清理段静默吞没（C6-009，死区入口的静默化因子），sys-event 触发链 saveState 未配置使守卫结构性旁路（C7-011）。参数面三条独立缺陷同域：dispatch 容量 `loadBatchSize<=0` 五层装配链零护栏（Semaphore(0) 永久挂死）、Export/Import batchSize 守卫不对称（缺省 0 钳位 1 每行一 chunk 写放大）、jdbc fetchSize 坏门 `&&` 使 DSL 配置永不生效（驱动全量缓冲 OOM）；资源面 excel-reader 全 sheet 物化确定性 OOM 且 `maxCountExpr` 接线注释死代码；生命周期面 `executor.execute` 同步拒绝（REE）裸逃逸绕过 onTaskComplete（资源泄漏 + 永久 RUNNING）。

本文档一次裁定八项 + 测试缝，使 Phase 2 实施只有一种合法形态。

## 三、核心设计契约

### 3.1 裁定①：RUNNING 死区 stale 接管（2514 裁定⑥投影，引用不重建）

- **形态菜单投影声明**：按 2514 裁定⑥形态菜单（条件 UPDATE 抢占 / 单飞判据 rowsAffected==1 / 租约 / 实体写路径列集约束）落地 nop-batch `nop_batch_task` 实例；**不重建第二套 fencing 语义**。若实施被迫引入偏离裁定⑥菜单的 nop-batch 侧新语义，须回 2514 设计文档修订登记（附理由）。
existing-path 无需补抢占写点；insert-path 于 newTask（:276-291）补租约初始写点一行 `task.setRestartTime(CoreMetrics.currentTimestamp())`（insert 即持租约，仍在 store 内、零 orm）——评审 R2 probe(a)：NULL 判 stale 否则会使 insert-path 活行在首 chunk 前被接管（双重执行，既有钉 testConcurrentStart_freshKey 必红）租约列分支 (a) 被拒：新增列触发 ORM plan-first 申报 + 迁移脚本 + codegen 重建三重成本，而 (b) 以既有列即达同构语义。
- **快失败守卫 stale 感知化（第三变更点，缺此则扩展谓词机制不可达）**：`startExistingTask` :123-129 既有守卫对 `taskStatus <= RUNNING` 无条件 throw，死区行到不了 `tryMarkTaskRunning` 的扩展 WHERE——守卫同步收窄为 live 行快速路径：`taskStatus <= RUNNING` 且 `(restartTime != null && restartTime > staleCutoff)` 的行维持 EXIST_RUNNING 响亮失败（live 行未到期，无需进 DB 谓词）；restartTime 为 NULL 或 `<= staleCutoff` 的 stale RUNNING 行**放行**进入 `tryMarkTaskRunning`，由条件 UPDATE rowsAffected 单飞裁决（竞态下另一实例先行接管时 rowsAffected==0，:145-149 既有响亮失败兜底）。`staleCutoff = now - nop.batch.task.stale-lease-ms` 计算**复用为闸门守卫与 WHERE 谓词共用常量**（单点计算，两处消费同一值，无二源）。KILLED 行 :108-113 先行拒绝不变；错误码族语义零回退（收窄仅改变触发条件集合，不改错误语义）。
- **stale 谓词唯一形态**：`tryMarkTaskRunning` WHERE 扩展为 `sid=? AND (taskStatus > RUNNING OR (taskStatus <= RUNNING AND (restartTime IS NULL OR restartTime <= :staleCutoff)))`，其中 `staleCutoff = now - nop.batch.task.stale-lease-ms`（与闸门守卫共用常量，见上）；接管成功（rowsAffected==1）后 :152-158 既有行内刷新段原子更新 taskStatus/restartTime/workerId/execCount（既有代码零改）。rowsAffected==0 维持既有 `ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE` 响亮失败（:145-149 零回退）。KILLED 行在 :108-113 先行拒绝、不进入 stale 接管（用户终态决定不被 fencing 覆写）；`ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_KILLED` 语义零回退。〔myExecCount = context task-record 实体的实例本地 execCount（insert-path=1、existing-path incExecCount 后与 DB 一致），非 IBatchTaskContext 新增字段、不得读 DB 最新值；接线验证断言 rowsAffected==0 分支真实触发〕
- **NULL 判 stale 定夺（唯一结论 = restartTime IS NULL 判 stale、可接管）**：存量死区行两条来源——(i) insert-path 行经 `tryInsertNewTask`→`newTask`（:276-291）创建，`newTask` 不写 restartTime，死区后 restartTime 恒 NULL；(ii) 历史版本行。若 NULL 不判 stale，缺陷主体（insert-path 死区）恰恰不可接管，本缺陷修复失去意义；NULL 判 stale 同时覆盖全部存量死区行，接管语义与「租约到期即可重入」对齐。
- **续租语义（防误伤活实例）**：租约续期由既有 per-chunk checkpoint 承接——续租写点 = `DaoBatchStateStore.saveTaskState` :234-239 计数列刷新段前置一行 `task.setRestartTime(CoreMetrics.currentTimestamp())`（**store 侧单点，非 BatchTask 调用侧**；BatchTask :358/:377 为既有调用点零改），活任务随 checkpoint 周期续租，接管窗 = checkpoint 间隔 + 租约余量。**列事实登记**：`restartTime` 列 `tagSet="clock"`（`nop-batch.orm.xml` :72-73）为声明性标注、**非运行时自动维护列**——无框架机制自动刷新，全仓非生成写点仅 :153 一处（测试侧 `AutoTestHelper` :19 为唯一消费者）；本裁定的续租行即写点第二处，均在 store 内。键 `nop.batch.task.stale-lease-ms` 缺省 **600000**（10 分钟），配置 `<=0` 时 stale 谓词退化为恒 false（关闭接管，等价现状）——运维逃生门显式声明，非静默降级。
- **实体写路径列集约束（2514 裁定⑥评审⑤投影 + 接管后僵尸写者 fencing）**：stale 接管只经 `tryMarkTaskRunning` 直发 UPDATE 语句写 taskStatus/restartTime（+接管后既有实体刷新段）。**僵尸写者面（单飞判据盲区）**：rowsAffected==1 只覆盖接管瞬间——被接管实例（僵尸）此后调用的 `saveTaskState`（:234-256，`updateEntityDirectly` 无条件全字段写）会覆写新实例已刷新的 taskStatus/workerId/进度列。唯一结论 = **`saveTaskState` 改条件 UPDATE：`WHERE sid=? AND exec_count=:myExecCount`**（execCount 随每次进入 RUNNING 递增 :157，为天然 fencing token；context 持有启动时的 execCount），rowsAffected==0 即该实例已被接管/重启——记 ERROR（`nop.err.batch.task-state-write-lost-fencing` 形态，携 taskId/execCount）后放弃本次状态写（不抛出打断清理链，失败事实显式可见——与裁定⑦不吞语义同型）。**偏离登记（按本批对齐纪律条款）**：saveTaskState 条件 UPDATE 为 2514 裁定⑥「限定列集或写前 reload」菜单的等价实现（fencing by execCount，语义同层：防陈旧写者覆写他方抢占状态），回填 2514 修订登记归 Phase 3（随 owner doc 落账同步提交，不得模块内静默发明）。
- **双层并存声明**：快失败守卫收窄后（:123-129 stale 感知化）仍为 live 行快速路径 + 事中兜底、错误码语义零回退；fencing 为事前接管——与 2514 裁定⑥「fencing 事前抢占 / status 守卫事中兜底，双层并存不合并」层级条款同构。运维 force-kill action 备选分支呈现不启用：裸 CrudBizModel 手工改库为现状运维形态，新增运维端点属扩面归 R4-12（C7-012 接缝：同一状态机写者 DaoBatchStateStore，本体归 R4-12 不代做）。
- **N1 参数化改写方案（修复 diff 前置义务）**：`TestDaoBatchStateStore` 三用例（`testLoadTaskState_runningInstance_rejectedLoudly` :106 / `testConcurrentStart_freshKey_onlyOneInstanceEntersRunning` :174 / `testConcurrentRestartFromCompleted_onlyOneInstanceGainsRunning` :202）以「显式构造 live/stale 两态前置行」参数化——live 行（restartTime=now，租约未到期）期望维持 EXIST_RUNNING 响亮拒绝（语义零变更）；stale 行（restartTime=now-2×lease 或 NULL）期望接管成功（status=RUNNING、execCount 递增、workerId 刷新）。改写前后语义对照留痕随同 diff 登记；其余闸门正确面用例零触碰。
- **时序 A/B 双路径测试形态**：时序 A（终态保存失败死区）= 正常启动 → saveTaskState(true) DB 故障注入（裁定⑦留痕面同点）→ 行停留 RUNNING → 以 stale 前置（restartTime 过期）重新启动 → 接管成功绿；时序 B（进程崩溃死区）= 直接注入 stale RUNNING 行（restartTime=NULL 两态各一）→ 重启序列 → 接管成功且 checkpoint 续租后第二实例在租约窗内被拒（双层并存验证）。

### 3.1.1 本批配置键一览（缺省值一次定夺，零键名二源）
> 日志键 `nop.err.batch.task-state-write-lost-fencing`（saveTaskState fencing 丢弃写分支）

| 键 | 缺省 | 裁定 | 语义 |
|----|------|------|------|
| `nop.batch.task.stale-lease-ms` | 600000 | ① | RUNNING 行 stale 接管租约（checkpoint 续租窗）；`<=0` 关闭接管（运维逃生门，等价现状显式声明） |
| 错误码 `ERR_BATCH_DISPATCH_LOAD_BATCH_SIZE_INVALID` | — | ② | loadBatchSize<=0 装配期 fail-fast（BatchErrors 族，param 携 loadBatchSize + capacity 公式） |
| 日志 `nop.err.batch.jdbc-fetch-size-unset` | 每查询一次 | ⑤ | fetchSize 未生效路径 WARN |
| 日志 `nop.err.batch.task-terminal-save-fail` | 每次失败 | ⑦ | 终态写失败 ERROR 留痕（携 taskId/taskName） |

### 3.2 裁定②：dispatch 容量 Guard（三层防线一次定夺）

- **唯一形态 = `BatchTaskBuilder.buildLoader` 装配期 fail-fast**（:340-356 dispatchConfig 分支入口）：`loadBatchSize <= 0` 抛类型化 NopException（BatchErrors 族新增 `ERR_BATCH_DISPATCH_LOAD_BATCH_SIZE_INVALID`），param 携 `loadBatchSize` 与 capacity 公式上下文 `capacity = loadBatchSize × loadBatchMultiplyFactor`（AsyncFetch 缺省 ×30，:40）——复核修正①口径：orm-reader 路径修复后期望终态 = fail-fast **非空任务成功**，而非「静默空任务」。**setter 护栏先例对比**：同批前序 plan 2498 曾以 `BatchDispatchConfig.setFetchThreadCount` setter 护栏（:34-40，超界类型化 fail-fast）处置 fetchThreadCount——loadBatchSize **不采同型 setter Guard**：buildLoader :340-356 为全部 dispatch 路径（AsyncFetch :78 / 同步 :61 两 provider、DSL 与程序化装配共同）的**必经点**，builder 单点覆盖面与 setter 等价且错误上下文更完整（可携 capacity 公式）；setter Guard 只拦 `BatchDispatchConfig` 载体、拦不住未来绕过 config 的直构路径。队列构造器 Guard 备选拒绝：`PartitionDispatchQueue` :78-83 直通 `new Semaphore(capacity)`，但构造期 Guard 位于装配链第五层、错误上下文已丢失且错过 jdbc/file 族的静默空任务面；xdef min 约束备选拒绝（触 nop-xdefs 须登记，且拦不住程序化 `BatchDispatchConfig` Java API 路径 :16 缺省 0）。
- **cancel 第二释放入口断言**：`acquirePermits` :337-350 已带 500ms tryAcquire 循环 + finished 退出、`removePartition` :267-281 释放未派发许可——修复后断言 cancel/finish 期间 acquirePermits/takeBatch 可退出（非永久阻塞），防 Semaphore(0) 挂死回潮。C8-005 同位点吸收面随本裁定闭合。
- **N3 搭车**：`TestPartitionDispatchQueueFinish`/`TestAsyncFetchLoader*` 的 `latch.await()` 补超时（`TestBatchBugVerification` latch+`@Timeout(30)` 先例形态），防护栏回潮时构建悬挂。
- **族 A4 次序约束成文**：本裁定落地后向 R4-12 就绪注记显式声明「C8-009 幽灵旋钮参数化前置已满足」（先堵危险值、再谈暴露旋钮；C8-009 本体归 R4-12）。

### 3.3 裁定③：batchSize 守卫对称（族 A3 共享面）

- **唯一结论 = ExportDbTool 复制 ImportDbTool 的 `>0` 守卫**（`ImportDbTool` :302-303 为既定惯例，ExportDbTool :278 现无守卫直传）：`if (config.getBatchSize() > 0) builder.batchSize(config.getBatchSize())`——不对称闭合，缺省 0 不再进入钳位路径。`adjustBatchSize` 钳位（BatchChunkProcessor :89-90 `ret<=0→1`）**保留 + 补每任务一次 WARN**（对齐 `nop.batch.orm-reader-no-query` 静默兜底可观测先例）——钳位上移 builder 装配期 Guard 被拒（共享位点在 processor 深处，装配面 Guard 已由裁定②覆盖 dispatch 链，双 Guard 语义重叠）；xdef `batchSize="!int=0"` 缺省改 1000 备选拒绝（触 nop-xdefs 须登记，非必需）。
- **族 A3 接缝**：本裁定封闭 adjustBatchSize 共享钳位位点后，C8-010（biz 钳位无下界穿透）复现通道收窄，本体归 R4-12 不代做——R4-12 消费本裁定 WARN/Guard 面。放大面回归断言：chunk 计数与 per-chunk INFO 量（BatchTask :336/:386）随缺省配置路径断言有界。

### 3.4 裁定④：excel-reader 物化包络（C10-011 必补，G79 双轨联动）

- **一次定夺 = (b) 零 nop-report 触碰包络分支**：恢复 `FileBatchSupport.newExcelReader` :150-151 注释的 `maxCountExpr` 接线（截断能力复活，C5-006 能力缺位主体顺带闭合）+ owner doc `nop-batch.md` 显式声明 excel-reader 规模上限与适用边界（全 sheet 物化形态、大数据量须走 csv/jdbc-reader 或显式 maxCount）。**行为变更声明（纳入 Phase 3 owner doc 落点）**：已配置 maxCount 的存量链路将从全量读取变为截断读取（接线恢复前该配置无效、数据全量消费；恢复后超出 maxCount 的条目不再进入处理）——属可观测行为变更而非纯能力新增，owner doc 升级注记须显式声明该差异，不做静默兼容降级。
- **游标式改造 (a) 拒绝理由**：触碰 `ExcelRecordInput.beforeRead`（:86-98，:97 `collector.getResult()` 全 sheet List 常驻）本体需跨模块申报 + `ExcelHelper.readAllSheets` 等全量 API 消费面回归（plan Risks 条），成本与风险不成比例；物化面残留登记 **R-G watch-only residual**（owner doc 声明 + C10-011 钉在场，不构成静默降级——缺陷判级陈述与「包络声明+截断，非内存修复」口径一致）。
- **C10-011 回归钉（两分支均强制，本分支形态）**：`TestBatchTaskDsl#testImportExcel`/`TestExportDbTool#testExportXlsx` 零断言面补齐——maxCountExpr 生效断言（配置 maxCount 后读取条目数 ≤ 上限）+ 物化路径行为断言（collector 全量路径存在性登记为已声明包络，缺陷态红判据以「maxCount 无效」表达而非「内存超限」）。
- **G79 双轨联动**：5.4-C5-004 为本缺陷第二实例（R3-04 批）——本裁定为位点 SSOT，R3-04 随其 P1 修复引用本裁定不重做；两批同时在飞时 R3-04 为消费方。

### 3.5 裁定⑤：fetchSize 坏门独立兜底（N2 同步改写前置）

- **唯一形态**：`JdbcBatchLoaderProvider.newState` :243-247 坏门 `if (sql.getFetchSize() != -1 && fetchSize != null)` 改独立兜底 `if (fetchSize != null) ps.setFetchSize(fetchSize)`——query/sql 文本路径 SQL 对象 fetchSize 恒 -1 使 `&&` 门恒 false、provider fetchSize 静默丢弃的缺陷闭合；streaming 分支（`streaming && dialect.getStreamingFetchSize() != null` :243）保持优先不变。sql 层 fetchSize 已由 JdbcHelper 处理，provider 层只兜底——`||` 语义由「分层兜底」表达而非字面 or。streaming=false 且 fetchSize 未配置路径补 WARN（驱动全量缓冲风险可观测，`nop.err.batch.jdbc-fetch-size-unset` 形态，每查询一次）。变体 B（20 个 `.dialect.xml` 仅 mysql 定义 streamingFetchSize）与 autocommit 未关同族事实登记不扩批，归 R4-12（C7-009/C6-011 邻接面注记）。
- **N2 同步改写方案（修复 diff 前置义务）**：`TestJdbcBatchSupport:180` `testNewJdbcReaderKeepsDefaultsWhenModelEmpty` 的「unset fetchSize stays null so loader uses SQL-level default」契约句改写为「provider 层独立兜底：显式配置即生效，未配置走驱动/sql 层缺省」——字段 null 断言保留（装配面缺省未变），语义注释句更新 + 新增两条断言：DSL 配置 fetchSize 传递到 provider 字段；记录型 Proxy 断言查询时 `ps.setFetchSize` 真实调用（`TestJdbcBatchConsumerProviderSharedField`/`TestJdbcPageBatchLoaderRetry` 先例形态，补 C10-005 的 C5-002 半边）。改写前后语义对照留痕随同 diff。

### 3.6 裁定⑥：REE 裸逃逸封闭 + C7-002A 幽灵链结算

- **唯一形态 = 双层封闭单一变更集**：(i) 提交点防御——`executeChunkLoop` :255 `executor.execute` 调用包 try/REE，捕获后 `future.completeExceptionally(rex)`（lambda 未执行的提交失败无泄漏面，仅需 future 终态化使 allOf 触发）；(ii) 循环包络——executeAsync futures 提交循环（:173-175）包 try/catch：任一提交抛出时已建 futures[0..i-1] 逐个 `completeExceptionally`（**C7-002A 已提交幽灵链显式结算**——future 终态化使 allOf 立即触发，不中断已运行加载线程，其数据消费由既有 fail-fast `context.cancel(CANCEL_REASON_STOP)`（:288-289 同形态）短路承接）后直达 `onTaskComplete` 既有终态化+关闭链（经 catch (Exception e) → onTaskComplete(future, meter, e, context) 路径，与 setup 失败 :165-169 同型）。
- **零回退钉清单**：plan 2496 C6-001 catch Throwable（live :275-285，注释 :276-278）与 C6-010 守卫首行（注释 :258-259）零触碰；plan 2381 primaryError CAS（`primaryErrorRef` :50-53、onTaskComplete 消费 :209-214、executeChunkLoop 收集 :280-283）零触碰；owner doc `nop-batch.md` §任务异常终态契约（:220-229）零回退；REE 路径显式结算不吞（rule 24）。
- **判级不降声明**：默认 cachedThreadPool 下 REE 不可达 / 注入有界池饱和与停机竞态为触发面——修复不改变判级登记。C10-001 断言形态 = Error 注入 + REE 注入双路径 → future 必结算 → 任务行必达终态 → claim 无残留。

### 3.7 裁定⑦：清理段显式化（S4/S5/S6 同段同修，族 A2 配套裁定①）

- **S4**：onTaskComplete err≠null 分支 `saveTaskState(true, err)`（:236-238）抛新异常 X 穿透 finally——catch X + ERROR 留痕（`nop.err.batch.task-terminal-save-fail`，携 taskId/taskName）+ 失败计数；**不吞不替换原始 err**，`context.completeExceptionally(err)`（:240）必达语义保持。终态写失败后的行残留（停留 RUNNING）由裁定① stale 接管承接（族 A2 闭环声明：C6-009 修复使死区入口可见，裁定①使死区可退出）。
- **S5**：`metrics.endTask`（:244-245）包独立 try/catch（ERROR 留痕），**不改 finally 内顺序**（plan Risks 裁定⑥⑦重排风险——保「future 必结算 + afterComplete 必达 + primaryError 归因」三契约，最小重排）；`FutureHelper.complete`（:247）必达性断言入 C10-002。
- **S6**：setup 失败路径（:165-169 进入 onTaskComplete）中 saveTaskState 抛 X 时 X 不替代原始 e——catch X 聚合日志、对外 future 以原始 e 结算。
- **族 A2 关系**：C6-009 位点与修复独立于 C7-001、不合并；同批落地且共用「终态写失败显式可见」语义；修复排期紧随 P1 族（同批）即本设计文档配套声明的落地形式。

### 3.8 裁定⑧：sys-event 链守卫接线（A2 注记搭车）

- **三分支一次定夺 = (a) 配置补线 + (c) owner doc 伴随成文；(b) singleActive 解耦拒绝**：被触发配置 `non-broadcast-consumer.batch.xml` 补 `saveState="true"` + `taskKeyExpr` 固定常量（如 `taskKeyExpr="const 'non-broadcast'"` 形态，语义=全部署单活跃键）——守卫经 `ModelBasedBatchTaskBuilderFactory` :191-192 门控接线 stateStore，(taskName,taskKey) 唯一键与启动闸门（含裁定① stale 接管）结构性覆盖该触发链。(b) DSL 层 singleActive 解耦声明拒绝：扩面且须评估与裁定①闸门复用，非本批最小面。owner doc 跨单元契约句伴随成文：「重叠防护依赖 nop-job 调度语义 + nop-sys claim 原子性，batch 侧守卫为批内兜底」。
- **UUID 绕过面处置边界**：`initTaskKey` :199-200 无 taskKeyExpr 时 generateUUID 为无状态任务既有语义**保留**（plan 2496/2381 已修面零回退、不 DSL 层禁用）；随 (a) 补 taskKeyExpr 后该链不再走 UUID，每执行一行死数据增长面由 C7-003 保留策略（plan 2502）承接。job.yaml `enabled|false` 缺省关闭（:2）与 cron `0/5`（:6）条件面标注保留、判级不因此降级；nop-job/nop-sys 侧零改码（跨单元前提划出不立案）。

## 四、测试缝与零回退钉

### 4.1 红①–红⑧驱动器（先红后绿，E2；缺陷态红判据 + 修复后绿口径）

| 红 | 载体 | 缺陷态红 | 修复后绿 |
|----|------|---------|---------|
| ①（C10-002 半） | `TestDaoBatchStateStore` N1 参数化改写 + stale RUNNING 行注入（restartTime=过期/NULL）+ saveTaskState 抛错/崩溃后重启序列 | 死区行永久拒启（EXIST_RUNNING 风暴） | stale 行接管成功（rowsAffected==1、行内刷新）；live 行仍响亮拒绝 |
| ②（C10-003） | `TestBatchTaskDispatchLoadRetry`/`TestPartitionDispatchQueueFinish` 扩展：loadBatchSize=0/负 注入（含 orm-reader 路径） | orm 挂死 / 静默空任务 | 装配期 fail-fast 携配置上下文；cancel 期间 acquire 可退出；N3 latch 超时在场 |
| ③（C10-004） | `TestExportDbTool` 扩展：export-db 缺省 batchSize=0 | 每行一 chunk（chunk 计数/INFO 放大） | 守卫落缺省不装配 batchSize；钳位 WARN 触发断言 |
| ④（C10-011） | `TestBatchTaskDsl#testImportExcel` 补断言 | maxCountExpr 接线无效（配置被忽略） | 接线恢复后截断生效；包络声明登记 |
| ⑤（C10-005 半） | 记录型 Proxy 断言 setFetchSize 调用（N2 同文件扩展） | DSL 配置 fetchSize 永不调用 | 独立兜底后调用必达；N2 改写对照留痕 |
| ⑥（C10-001） | 拒绝型 executor 注入 + Error 注入（`TestBatchBugVerification` 形态扩展） | REE 后资源泄漏 + future 永不结算 + 行 RUNNING | 双层封闭后 future 必结算、幽灵链显式终态、行达终态、claim 无残留 |
| ⑦（C10-002 半） | saveTaskState 终态写 DB 故障注入 | 零日志静默吞没 | ERROR 留痕 + 计数 + 原始 err 不替换 + complete 必达 |
| ⑧（C10-002 半） | sys-event 链重叠触发（`TestSysEventBatchTrigger` 扩展） | 守卫零防护（stateStore 未接线） | 接线后第二实例 EXIST_RUNNING 响亮失败 |

### 4.2 零回退钉（全列，钉零回改逐条 git diff 为空核对；行号 = live `ce4f846ca1`）

- `BatchTask.java` plan 2496 注释钉：C6-010 守卫首行 :258-259 + C6-001 catch Throwable :275-285（plan 记 :260-268，live 微漂移，机制零变）——裁定⑥零触碰。
- `BatchTask.java` plan 2381 primaryError 钉：:50-53 / :209-214 / :280-283——裁定⑥⑦零触碰。
- `DaoBatchStateStore.java` 闸门既有钉：快失败守卫 :122-129 **收窄为 live 行快速路径（stale 感知化，裁定①第三变更点——错误码语义零回退，仅触发条件集合变化）**、`tryMarkTaskRunning` 单飞判据注释 :183-195、启动冲突 WARN :143-149、`ERR_..._WHEN_KILLED` :108-113——裁定① WHERE 扩展 + 守卫收窄，错误语义零回退。
- owner doc `nop-batch.md` §任务异常终态契约（:220-229）与 §账本保留策略（:230-247，plan 2502）——零触碰。
- `TestDaoBatchStateStore` 闸门正确面 7 例——N1 改写仅限三用例参数化（显式 live/stale 两态构造），其余零触碰；`TestBatchBugVerification`/`TestBatchTaskCheckpoint` 相邻钉零回改。
- N2 钉改写前后对照：改写前 = 「unset fetchSize stays null so loader uses SQL-level default」（:180，死配置契约的装配层表达）；改写后 = null 断言保留 + 契约句改为 provider 层独立兜底语义 + 新增配置传递与运行时调用断言——唯一允许的两处钉改写之一（另一处 = N1），对照痕随 diff 登记。
- ImportDbTool `>0` 守卫（:302-303）与 `acquirePermits` finished 退出（:337-350）——裁定②③引用惯例零改写。

### 4.3 C10 五条集落名

C10-001（红⑥，`TestBatchBugVerification` 扩展）、C10-002（红①红⑦红⑧，`TestDaoBatchStateStore` + BatchTask 故障注入 + `TestSysEventBatchTrigger` 扩展）、C10-003（红②，dispatch 测试族扩展）、C10-004（红③，`TestExportDbTool` 扩展）、C10-011（红④，`TestBatchTaskDsl`/`TestExportDbTool` 补断言）——五条均随对应本体同 diff 落地（E2）；补测类声明：新增断言即新钉、缺陷态红→修复后绿；上游环境抖动按 plan 2516 勘误 5 三联裁定处理留痕。

### 4.4 接线与无静默跳过义务（rule 23/24）

- **接线验证**：裁定① stale 谓词真实被 `tryMarkTaskRunning` 条件 UPDATE 消费且接管分支 rowsAffected 断言（非仅 SQL 存在）；裁定② Guard 真实被 buildLoader 装配链调用断言；裁定③ WARN 真实被缺省配置路径触发断言；裁定⑤ setFetchSize 运行时调用 Proxy 断言；裁定⑥ futures 包络在 REE 注入下结算全部已提交链断言；裁定⑧ saveState 接线后守卫真实经过断言。
- **无静默跳过**：裁定①接管失败保留既有响亮异常语义（非静默放行）；裁定⑥ REE 路径显式结算（非吞）；裁定⑦终态写失败显式 ERROR+计数（非空 catch）；裁定③钳位 WARN 非静默；全部新增公共分支无空方法体/TODO。

## 五、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ① 新增租约列（(a) 分支） | ORM plan-first 申报 + 迁移 + codegen 三重成本；restartTime 已有生产写点（:153），零 orm 即达同构 fencing 语义 |
| ① NULL 不判 stale | 存量死区主体（insert-path 行 restartTime 恒 NULL）恰恰不可接管，修复失去意义 |
| ① 运维 force-kill action 作为主修复 | 仍是人工介入，不消除死区根因；扩面归 R4-12（C7-012） |
| ② 队列构造器 Guard / xdef min | 错误上下文丢失、拦不住 Java API 装配路径；builder 入口单点覆盖五层装配链 |
| ③ 钳位上移 builder Guard / xdef 缺省改 1000 | 与裁定②语义重叠 / 触 nop-xdefs 须登记非必需；守卫对称 + WARN 已闭合 |
| ④ 游标式改造 ExcelRecordInput | 跨模块触碰 + 全量 API 消费面回归风险；包络 + maxCount 截断零触碰即达声明边界 |
| ⑤ 恢复 `&&` 门左半边（写 sql.fetchSize） | sql 层归 JdbcHelper 既有职责；provider 层独立兜底更小且契约清晰 |
| ⑥ 仅提交点防御（无循环包络） | futures[i] 中途抛出时已建幽灵链无人结算（C7-002A 残留）；双层缺一不可 |
| ⑦ 重排 finally（complete 先于 endTask） | plan 2496/2381 双重已修面上重排风险；独立 try 即保 complete 必达 |
| ⑧ DSL singleActive 解耦 | 扩面且与裁定①闸门复用关系未评估；配置补线成本最低即闭合 |

## 六、与已有设计的关系

- [wf-task-scanner-fencing-and-task-wf-concurrency.md](wf-task-scanner-fencing-and-task-wf-concurrency.md)：裁定①消费其 §3.6 形态菜单（条件 UPDATE/rowsAffected==1/租约/列集约束）——引用不重建，偏离须回 2514 修订登记；键族 `nop.batch.task.*` 与 `nop.wf.scheduler.*` 同构异域，零键名冲突。
- [periodic-task-insulation.md](periodic-task-insulation.md)：裁定⑥⑦的「失败可见、不吞不替换」语义消费其 §3.1 管线形态；nop-batch 批链非周期设施载体，零 riding 面，仅语义对齐。
- plan 2496/2502/2381 已修面（§4.2 钉清单）零回退；下游 R4-11/R4-12 硬依赖本批——裁定①②③⑧接缝（C7-012/C8-009/C8-010/C5-009）与 A4 次序声明为其既有裁定引用面；R3-04 消费裁定④位点 SSOT（G79 不双计）。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/03-modules/nop-batch.md`（RUNNING 死区与 fencing 契约节、dispatch/batchSize 护栏契约句、excel-reader 规模边界声明、fetchSize 语义声明、终态写失败可见性契约句、sys-event 链守卫覆盖声明、源码锚点补行）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
