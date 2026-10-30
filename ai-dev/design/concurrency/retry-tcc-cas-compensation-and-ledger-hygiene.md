# retry/tcc CAS 失败传播、补偿门控与账本卫生契约（R2-12 批十三成员裁定）

> Status: active
> Last Reviewed: 2026-10-30（plan 2515 Phase 1 产出；本批唯一设计裁决权威）
> Source: plan 2515（`ai-dev/plans/2515-cra-phase8.21-r2-12-tcc.md`）Current Baseline 13 成员表 + Baseline 勘误 6 条 + draft review R1 增补（裁定⑥两层簿记位点枚举）；发现事实源 = WI 1.6 审计目录 `ai-dev/audits/2026-10/2026-10-15-1400-deep-audit-nop-retry-nop-tcc-concurrency/`；行号 = live `80f28fefb9`（分支 `concurrency-audit-remediation`）2026-10-30 亲读回读值，与 plan 基线零漂移，执行期漂移以 live 为准并登记于 plan 勘误
> 实现锚：nop-tcc-core `impl/TccRecordStore`（全局 CAS :162-197[:188-193 0行吞]、分支 CAS :222-283[:278-283]、过期认领 :291-326、清理 :334-355）、`impl/TccTransaction`（endAsync 恢复路由注释钉 :59-84、doCancelAsync :97-117[:110-111]、doConfirmAsync :119-138[:132-133]）、`impl/TccRunner`（runBranchConfirmAsync :72-96、runBranchCancelAsync :98-122、聚合 :178-196/:198-217）、`impl/TccEngine`（checkExpiredTransactions :315-343[:331-334]、cleanCompletedTransactions :351-355）、nop-tcc-api `TccStatus`（finished :81、getFinishedStatus :108）；nop-retry-engine `impl/RetryEngineImpl`（executeTask :200-221、OVERWRITE :287-292[handleBlockStrategy :275-298]、executeImmediateRetry :361-424、scheduleImmediateRetry :426-452、executeSingleAttempt :454-479、doExecute :486-519[:519 saveAttempt 内联]、handleExecutionFailure :541-559、join :680、空字段死信兜底 :305-319、parseMap :322、startLedgerCleanup :709-753）、`impl/RetryTaskImpl`（callAsync :100）、`store/RetryRecordStoreImpl`（namespaceId :62、newRecord 推延钉 :116-121、saveAttempt :144-150、fetch 过滤 :159-166、tryLock CAS :184-206、deleteRecord :249-251、updateEntityDirectly :284、moveToDeadLetter :289、purgeExpiredLedgerRows :315-390）、`scanner/RetryScannerImpl`（doScan :180-230、catch :214/:218）；约束载体 `nop-retry/model/nop-retry.orm.xml` UK_RETRY_ATTEMPT_RECORD_NO :365-368（只读消费）；旁证 `nop-job/nop-job-retry-adapter/.../NopRetryJobRetryBridge.java:40`（只读）
> Related：R01 族级 SSOT = [ledger-retention-policy.md](ledger-retention-policy.md)（§3.4 tcc 两表成员行 + §3.6 R2-12 承接条款——裁定⑧引用不重建）；R1-01 绝缘层 = [periodic-task-insulation.md](periodic-task-insulation.md)（§3.1 扫描链/清理链零回退）；R21 同 diff 搭车先例与 ORM PA 申报口径 = plan 2514 [wf-task-scanner-fencing-and-task-wf-concurrency.md](wf-task-scanner-fencing-and-task-wf-concurrency.md)（§3.6 迁移三行范式投影）；下游 R4-10（8.50）硬依赖本批裁定①⑤⑦与 A2 令牌语义（§3.4 接缝）

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 1.6-C1-001（P1；←C3-001；C10-003） | 全局/分支状态 CAS 失败（updated==0 与异常同型）一律**异常完成**（failedStage，类型化 `ERR_TCC_STATUS_CAS_CONFLICT` 族），0 行 WARN 留痕形态废除；失败即整链短路——doCancelAsync/doConfirmAsync 的 thenCompose 分支驱动与 runBranchConfirmAsync/runBranchCancelAsync 的补偿 RPC 均不再照发；全模块内存守卫兄弟站点逐点处置（§3.1 表）；checkExpiredTransactions 超时分支 `future.cancel(true)` 封堵幽灵双驱动源（C3-008 本体归 R4-10）；DB 级补偿租约为被拒备选；恢复对称续跑路由零回退 |
| ② | 1.6-C3-002（P1；←C3-016；C10-003） | 聚合**读 DB 权威状态**（聚合前经 store 按 txnId 重载分支行，聚合输入=重载行；内存副本仅作调度判定），不采「CAS 失败重载后再聚合」二段式；全局终态 CAS 前按分支 DB 状态终裁，误报 CANCEL_SUCCESS/CONFIRM_SUCCESS 不可达 |
| ③ | 1.6-C3-012（P1；独立；C10-003） | CAS 异常完成单元格并入裁定①同一传播面（异常不再静默、零驱动不再可达聚合）；终态直写前置校验 = 终态 CAS 只能在「首跳 CAS 成功 ∧ 分支驱动完成 ∧ 聚合基于 DB 权威状态」三前提后执行，与②共用一套权威裁决机制，不建第二套 |
| ④ | 1.6-C3-003（P1；←C1-009；C10-006；A2 族令牌载体） | 即时重试每轮 attempt 前 CAS 续租 nextTriggerTime=now+租约（载体=`tryUpdateManyWithVersionCheck` 既有乐观锁）；续租 0 行=租约被抢→**败方显式中止**（failedStage 类型化错误 + WARN，非静默续跑）；拾取/接管维持「status∈{PENDING,RETRYING} ∧ nextTriggerTime<=now」双条件——续租闭合**轮间**接管面，attempt 内挂死窗由 attempt 级超时上界（< 租约）闭合（§3.4）；执行令牌语义一次定夺；newRecord 一次性推延钉被协议替代（唯一允许钉改写，改写登记在案） |
| ⑤ | 1.6-C3-005（P1）+ 1.6-C6-003（P1；←C8-006；C10-005；R10） | store 层新增 `replaceRecord` **@Transactional 原子包络**（delete+save 同事务，整体回滚旧记录保留），不采先插后删（idempotentId 唯一约束下先插必撞 UK）；替换失败→旧记录存活→`moveToDeadLetter` 死信快照 + failedStage 显式失败（零蒸发）；代际裁定采**进程内代际号/epoch + DB 行存在性双校验**（OVERWRITE 递增代际，旧链回调/续跑先校验代际再回写或短路；跨进程面由「回写 0 行=短路」承接），不采 canceled 标记（需新列=ORM 扩面）；C1-002 暴露面收窄接缝登记（本体归 R4-10） |
| ⑥ | 1.6-C3-006（P1；←C6-016）+ 1.6-C8-012（P1；←C6-001/C1-008/C1-003A）+ 1.6-C8-017（P2 搭车；C10-001/C10-008；R21 同 diff） | 记账/业务两层分层：attempt 层 doExecute whenComplete 内联 saveAttempt（:519）与 record 层 cb1 内 updateRecord/handleExecution*（:377-421）**两个位点各自独立 try/catch 隔离**（簿记失败=ERROR+计数，不改判业务结论、不以簿记异常完成 stage）；future 拓扑单一化 = executeImmediateRetry 返回 future ≡ 回调挂接同一 promise + 每条链 Throwable 兜底 completeExceptionally（E-a/E-b/E-c 三逃逸点逐一闭合，§3.6 清单）；`allOf().join()` 面 = **保留 join + 兜底**（每 future 必有终态→join 有界返回，业务级永久冻结不可再现），不改异步汇聚；`RetryTaskImpl.callAsync` try-catch(Throwable)→`failedStage`，提交面失败交付契约落 owner doc（IRpcCall javadoc 修订为备选分支，若采须批内申报 nop-api-core PA）；triggerCallback 调用点补 try（E-b） |
| ⑦ | 1.6-C6-002（P1；C10-005）+ 1.6-C7-003（P1；C10-007） | attempt UK 砖化修复默认分支 = **重拾对账 + attemptNo 取 max+1**：重拾/执行入口对 record 的遗留 RUNNING attempt 对账置 STOPPED（激活既有零使用常量），newAttempt attemptNo 改为该 recordId 下 max(attemptNo)+1（UK 碰撞从根消除）；saveAttempt 补 duplicate-key 防御兜底（resolveInsertRaceWinner 对称形态：关遗留行+max+1 重插一次，不静默）；僵尸记录 = executeRetryFromScanner 的 parseMap/策略装载异常直接走 moveToDeadLetter 既有兜底（:305-319 同族漏网补齐）+ doExecute 内 invokeAsync 同步抛补 attempt 失败落账；死信化契约句落 owner doc |
| ⑧ | 1.6-C7-005（P1；C10-007；R01 对齐 + orm PA 批内申报） | 清理周期链生产接线（形态 A，对齐 plan 2502 `startLedgerCleanup` 同构：绝缘载体 scheduleWithFixedDelay + IPeriodicTaskListener 失败可见、错配置启动期 fail-fast、retention=0 显式禁用），键族 `nop.tcc.ledger.retention-days|30`/`cleanup-interval|1h`/`delete-batch-size|1000`（R01 §3.1/§3.2 投影，零发明）；耗尽行（retryTimes≥maxRetryCount）每轮清理前置终态化为 KILLED 后入既有 finished 谓词（C7-006 接缝：终态字段=复用 status→KILLED，本体归 R4-10）；`nop-tcc.orm.xml` NopTccRecord 新增 expireTime+status+retryTimes 复合索引与 beginTime 索引（ORM PA 批内申报，§3.8 三行）+ 配套迁移脚本（非 `_` 前缀新建） |
| ⑨ | 1.6-C7-012（P1；C10-006） | 三分支定夺 = **(a) fetch 过滤放宽为全部 namespace**：移除 fetchPendingRecords 的 eq(namespaceId) 硬过滤（:162），租户隔离由分区键（含 namespaceId）与幂等 UK 承担；(b) 多实例装配、(c) 提交校验 fail-fast 均被拒（§五）；job 桥接零改码行为闭合声明；default-only 部署条件面标注保留（判级不降级） |
| ⑩ | C10 六条 + 测试缝 | 红①–红⑫驱动器（§4.1）+ 零回退钉全列（§4.2）+ C10 集落名（§4.3）；C10-003 批外接缝 C3-010/C6-015、C10-008/009 覆盖面 C6-012 批外列名 |

## 二、背景与动机

R2-12 批 13 项（12 P1 + P2 搭车 C8-017）覆盖 nop-retry/nop-tcc 四类缺陷面：

1. **CAS 失败被吞（C1-001/C3-002/C3-012）**：tcc 全局/分支状态 CAS（0 行或异常）当前被 WARN 后吞为正常返回，互斥守卫基于各链私有过期内存副本——endAsync 调用方与恢复扫描双驱动时 confirm/cancel RPC 对撞照发（参与方互斥被打破）；分支 CAS 失败不回写内存使聚合折叠误报终态（全局 CANCEL_SUCCESS × 分支 CANCEL_FAILED → 预留资源永久悬挂）；全局首跳 CAS 异常致分支零驱动仍直写终态。
2. **租约与幽灵链（C3-003/C3-005/C6-003）**：即时重试链全程不续租 nextTriggerTime，长链超 600s 租约被扫描器接管双执行；OVERWRITE delete+insert 非原子（半个成功任务蒸发无死信）且删行不取消在途旧链（幽灵任务双执行、回写落空）。
3. **簿记覆盖业务结果与拓扑断裂（C3-006/C8-012/C8-017）**：attempt/record 两层簿记写内联于结果回调，簿记瞬时 DB 异常把已成功的业务调用改判失败并重试（最危险的错误方向）；`executeImmediateRetry` 返回 future ≠ 回调挂接 future 且无 Throwable 兜底，同步 DB 异常下扫描器 `allOf().join()` 永久冻结；提交面失败交付三通道不对称。
4. **终态化与账本卫生（C6-002/C7-003/C7-005/C7-012）**：崩溃/挂死后 RUNNING attempt 经 UK 碰撞把记录永久砖化；解析/同步异常路径产生永不终止僵尸记录（144 条 ERROR/天）；tcc 清理 API 零生产调用方 + 耗尽行双重永久排除 + 全表零索引；扫描器单值 namespace 硬过滤使非 default 记录重试承诺静默违约。

本文档一次裁定十项 + 测试缝，使 Phase 2 实施只有一种合法形态。

## 三、核心设计契约

### 3.1 裁定①：CAS 失败异常完成 + 门控短路（同变更集含②③机制）

- **传播形态唯一结论 = 异常完成**（不采失败标记返回值）：`updateTccStatusAsync`/`updateTccBranchStatusAsync` 的 `updated==0` 分支与执行异常统一收敛为 `CompletableFuture.failedStage`（类型化 `ERR_TCC_STATUS_CAS_CONFLICT` 族新错误码，`.param(txnId/branchId/fromStatus/toStatus)`；0 行保留现有 WARN 留痕后转为异常完成，非静默吞）。选异常完成的理由：(a) `thenCompose` 链天然短路——首跳 CAS 失败时分支驱动（cancelAllAsync/confirmAllAsync）与聚合段不再执行，「败方不发 RPC」由组合子结构保证，无需在每个调用点插标记判据；(b) 失败标记需改签名且每个消费点手工判空，漏判一点即复刻对撞面。
- **扫描循环容忍度显式声明**（plan 风险条款闭合）：败方异常完成对 `checkExpiredTransactions` 零放大——单条补偿已在 per-record try/catch 内（:328-340，`Exception` 分支 ERROR 后继续下一条）；调用方 endAsync 的异常经 whenComplete（:86）回调传播给业务调用方（:88-93 补偿 err2 仅留痕不传播的现状保持，业务 ex==null 时补偿失败经门控显式传播——业务本就需感知补偿未接管）。败方短路不改变赢方链的任何行为。
- **门控短路点位清单（全模块内存守卫兄弟站点逐点处置）**：

| 站点 | 现状（live 行） | 处置 |
|------|----------------|------|
| 全局 CAS 首跳 | `doCancelAsync` :110-111 / `doConfirmAsync` :132-133 | CAS 失败异常完成 → thenCompose 不执行 → 分支零驱动即零 RPC；短路点在 store，thenCompose 段零改码即受保护（挂点门控见下行——**结构性短路不外推到挂点**） |
| 分支 CAS | `updateTccBranchStatusAsync` :278-283 | 同型异常完成；beginConfirmAsync/beginCancelAsync（TccBranchTransaction）经 thenCompose 短路 → finish* 不执行 → 补偿 RPC 不照发 |
| 内存短路守卫 | `runBranchConfirmAsync` :75 / `runBranchCancelAsync` :101 | 保留为事前快路径（零回退），语义不变；事中权威由分支 CAS 异常完成承接 |
| 聚合挂点 | endAsync `whenComplete` :86（非 whenCompleteAsync）+ `doCancelAsync` :113-116 / `doConfirmAsync` :134-137 | 挂点补 **err==null 显式门控**：驱动链（含首跳 CAS）异常完成时回调**不执行终态折叠**——全局保持 CANCELLING/CONFIRMING 交恢复扫描续跑，异常向上传播；err==null 时才聚合（DB 权威，裁定②）并写终态。:88-93 补偿段 err2 仅留痕不传播（现状保持）——调用方异常感知论证精确到 ex==null 半边：业务 ex==null 时补偿失败经门控以 err 传播给调用方，不再被留痕吞掉 |
| 幽灵双驱动源 | `checkExpiredTransactions` :331-334 | TimeoutException 分支补 `future.cancel(true)`（在飞补偿链取消）+ ERROR 留痕后继续下一条；超时弃链追踪本体（C3-008）归 R4-10，本批只封本条双驱动源（接缝登记） |

- **零回退**：`endAsync` CONFIRMING/CANCELLING 对称续跑路由（:59-84 注释钉）零回退——本裁定只改 CAS 失败路径，路由判定本体零 diff；`fetchExpiredRecords` 版本 CAS 认领（:319）零回退。DB 级补偿租约为被拒备选（§五）。

### 3.2 裁定②：聚合读 DB 权威状态

- **唯一结论 = 聚合读 DB 权威状态**（不采「CAS 失败重载后再聚合」二段式）：`aggregateCancelBranchStatus`/`aggregateConfirmBranchStatus` 的输入改为聚合前经 store 按 txnId 重载的分支行权威状态（复用 `getBranchRecordsAsync` 一次批量查询；分支计数与重载行按 branchId 对齐）。理由：二段式只在「CAS 失败」分支重载，正常成功路径仍聚合内存副本——内存副本在多驱动场景同样陈旧（赢方之外的读者）；统一重载消除双数据源，聚合函数本体（BIZ_CANCEL_FAILED/CANCEL_FAILED/TIMEOUT_FAILED→CANCEL_FAILED 优先级，:178-217 语义）零改写。
- **提交可见性条款（唯一结论 = store CAS future 改 afterCommit 完成）**：`@Transactional` store 方法现于事务提交前即完成 future（方法返回时提交尚未发生），聚合回调在另一连接重载可读 pre-CAS 旧值——重载权威化失效。裁定：全局/分支状态 CAS 的 future 在**事务提交回调（TransactionSynchronization afterCommit）中完成**，聚合挂点必然晚于提交感知——重载必见本链 CAS 后状态；不采「重载查询跳 executor 异步」二段式（仅概率性错开线程，无提交先行保证）。
> afterCommit 完成**必须配对** afterCompletion（rolled-back/commit-failed）分支 → completeExceptionally（按裁定①同型收敛），CAS 方法体同步异常即时 failedStage——任何路径下 CAS future 必有终态，零「永不完成」形态残留（评审 R2 新-1 条款）。
- **终裁口径**：全局终态 CAS（updateTccStatusAsync 到终态）执行前，聚合结果必来自 DB 权威分支状态——「全局 CANCEL_SUCCESS × DB 分支 CANCEL_FAILED」不可达（聚合必见 CANCEL_FAILED → 全局折叠 CANCEL_FAILED）；聚合自身失败（重载查询异常）按裁定①异常完成传播，不折叠任何终态。

### 3.3 裁定③：CAS 异常单元格并入① + 零驱动不可直写终态

- CAS **异常**（非 0 行）与 0 行在同一 failedStage 单元格收敛（3.1），thenCompose 链断裂即分支零驱动——异常路径不再静默到达聚合。
- **终态直写前置校验形态 = 挂点显式 err==null 检查（不外推 thenCompose 短路）**：聚合挂点 whenComplete（:86/:113-116/:134-137）挂在整个链后，首跳 failedStage 时该回调仍会以 err != null 执行——故三前提「首跳 CAS 成功 ∧ 分支驱动完成 ∧ 聚合基于 DB 权威状态」以回调内显式 err==null 检查实现：err != null 即跳过终态折叠（保持 CANCELLING/CONFIRMING 交恢复扫描续跑）并向上传播；err==null 才聚合写终态。与裁定②共用同一权威裁决机制，不建第二套。误报终态（分支从未补偿而事务「完结」）不可达断言 = 红③。

### 3.4 裁定④：续租协议（A2 族执行令牌载体）

- **续租**：`executeImmediateRetry` 每轮 attempt 发起前执行一次续租 CAS——`tryUpdateManyWithVersionCheck`（:184-206 既有载体）将 nextTriggerTime 推进 `now + NopRetryApiConstants.DEFAULT_RETRYING_TIMEOUT_MS`（600s，零新参数；写放大包络：每轮一次单行版本 CAS，量级=即时重试轮数，可接受）；扫描侧 `tryLockRecordsForProcess` 形态零改写。
- **被抢中止（败方显式语义）**：续租 0 行 = 租约被扫描器接管（或他链抢持）→ 本链**立即中止**：返回 failedStage（类型化 `ERR_RETRY_LEASE_LOST` 族）+ WARN 留痕；不静默续跑、不重抢。中止即该链不再发起 attempt、不再回写（回写义务随链终止消灭）。
- **拾取/接管双条件与残留窗（评审三选一，采 (a)）**：扫描器拾取维持「status∈{PENDING,RETRYING} ∧ nextTriggerTime<=now」（:162-166 双条件零改写）。续租闭合**轮间**接管面（长链跨轮超租约 + C1-009 首执行窗——newRecord 一次性推延 :116-121 注释钉被协议替代，**唯一允许的钉改写**，改写前后语义对照随同 diff 留痕）；**attempt 内残留窗显式声明**：单次 attempt RPC 挂死超过租约（600s）时续租位不推进、扫描器 `tryLockRecordsForProcess` 版本 CAS 必然成功、执行令牌二元组不被扫描器消费——RPC 副作用双发面闭合的唯一结论 = **attempt 级超时上界 < 租约**（doExecute invokeAsync 挂 cancelToken/policy 执行超时，上界缺省取 `DEFAULT_RETRYING_TIMEOUT_MS` 的分数档，装配期 fail-fast 校验 < 租约），挂死 attempt 在租约到期前被取消落账 → 租约到期接管读到的是已终态化的 attempt，双执行不可达。红④绿判据相应改写为「轮间不接管 + attempt 内窗由 timeout 上界闭合」。
- **执行令牌语义（一次定夺，供裁定⑤⑥⑦与 R4-10 消费）**：执行令牌 = 「续租成功后的 record 版本号 + record 进程内代际号（裁定⑤）」二元组；任何回写/续跑动作前校验令牌（版本 CAS 天然校验版本半边，代际校验见 3.5）。A2 族其余成员（C3-015/C7-015/CL-001）归 R4-10 时引用本语义，不重建。

### 3.5 裁定⑤：OVERWRITE 原子替换 + 代际取消（C3-005+C6-003 同族单设计）

- **原子包络唯一结论 = store 层 `replaceRecord(existingRecord, newRecord)` @Transactional 单方法**（REQUIRED 同事务；**方法体内不消费既有 `saveRecord`（REQUIRES_NEW :276-278 经代理挂新事务）与 `deleteRecord`（:249-251 裸自动提交）**——自调用绕拦截器与传播分叉两坑均排除，直接用 dao 原语 deleteEntityDirectly + saveEntityDirectly 在同一事务内执行）：事务失败整体回滚 → 旧记录存活、零蒸发。
- **失败死信快照**：replaceRecord 抛出（约束类/DB 类失败）→ 旧记录已回滚存活 → 调用方（handleBlockStrategy OVERWRITE 分支）catch 后对旧记录执行 `moveToDeadLetter(record, 替换失败原因)` + 返回 failedStage——对齐 owner doc「死信保留全量快照」可恢复性设计，任务不再不可恢复丢失。**C1-002 接缝**：原子包络落地后「并发覆盖裸 UK 冲突暴露」面自然收窄；C1-002 本体归 R4-10 不代做，此处仅登记暴露面收窄。
- **代际取消唯一结论 = 进程内代际号/epoch + DB 行存在性双校验**（不采 canceled 标记——需 ORM 新列扩面，且零 DDL 方案已充分）：引擎维护 per-recordId `AtomicLong` 代际，OVERWRITE 重建时递增；旧链（已调度续跑定时器 :439-449、在途回调闭包）每次回调/续跑/回写前校验「代际一致 ∧ DB 行仍在」，任一不满足即短路（INFO 留痕，非吞）。跨进程残留面由 DB 半边承接：旧链回写 `updateEntityDirectly`（:284）对已删行 0 行 → 显式检测 0 行并短路（不再静默落空）。零回退：nop-retry.orm.xml 零触碰（Non-Goal 维持）。

### 3.6 裁定⑥：完成期记账失败族 + future 拓扑单一化 + C8-017 契约半边（R21 同 diff）

- **两层簿记位点逐一隔离**（draft review R1 Minor-6，禁只修一层）：
  - attempt 层：`doExecute` whenComplete 内联 `saveAttempt`（:519）——移入独立 try/catch，簿记异常 ERROR+计数（`nop.retry.attempt.bookkeep-fail`），**stage 以业务 response/ex 完成**，簿记异常不再成为 stage 完成异常（逃逸入口关闭）；
  - record 层：cb1（:373-421）内 `updateRecord`/`handleExecutionSuccess`/`handleExecutionFailure`——业务结论（成功/失败/重试判定）**先于**簿记写确定并锁定，簿记写移入独立 try/catch（`whenCompleteAsync` 或回调体内隔离段），簿记失败显式可见不改判（红⑦：二次 saveAttempt 瞬时 DB 故障注入下业务结论保持）。
- **future 拓扑单一化（返回 = 挂接）**：`executeImmediateRetry` 重构为返回与回调链同一 promise——所有路径（成功/失败/重试续排/bizFatal 死信）的终态结算都落在返回的 future 上；续排路径经 `scheduleImmediateRetry` 的挂接 future 链式传导至同一 promise。三逃逸点逐一闭合清单：
  - **E-a**（:439 `executor.schedule` 调度内部 future 被丢弃）：delay-lambda 体 try/catch(Throwable) → 异常显式传导至持有 future（completeExceptionally），不再悬置；
  - **E-b**（triggerCallback :595 executeTask 调用无 try）：调用点补 try-catch(Throwable) → ERROR 留痕（回调失败不影响主结论）；
  - **E-c**（cb1 内 updateRecord/handleExecution* 同步 DB 异常零日志）：由两层簿记隔离承接（上层）+ 段内 catch 留痕。
- **Throwable 兜底**：每条链（executeImmediateRetry/scheduleImmediateRetry/executeSingleAttempt）出口统一兜底 handle——任何未预期 Throwable 使返回 future completeExceptionally（不悬置、不吞）。
- **`allOf().join()` 面唯一结论 = 保留 join + 兜底**（不采异步汇聚）：扫描器 :680 join 的每个输入 future 在裁定⑥修复后必有终态（成功/失败/兜底异常）→ join 有界返回，业务级永久冻结形态不可再现；R1-01 绝缘层管 Throwable 重臂、本批管 future 终态完备，两层正交零回退（`TestRetryScannerInsulation` 保持绿）。
- **C8-017 契约半边（同 diff，R21 纪律）**：`RetryTaskImpl.callAsync`（:100）try-catch(Throwable) → `CompletableFuture.failedStage(ex)`——同步抛/ future 异常/永不完成三通道收敛为 future 单通道；提交面失败交付契约（等待非必需、fire-and-forget 受支持、失败保证经 future 交付）落 `nop-retry.md` owner doc（**预设默认分支零平台层触碰**；`IRpcCall` javadoc 修订为备选分支，若 Phase 2 采须按头部 PA 口径批内申报 nop-api-core 后方可实施）。

### 3.7 裁定⑦：RUNNING attempt 砖化 + 僵尸记录终态化

- **attempt UK 容错三分支定夺 = 重拾对账 + attemptNo 取 max+1（组合为唯一默认分支）**：执行入口（executeWithRetry，含 scanner 重拾与 OVERWRITE 新链）对 record 名下遗留 RUNNING attempt 执行对账——置 STOPPED（激活 `_NopRetryDaoConstants` 零使用的 STOPPED/CANCELED 常量，KILLED 类终态语义）+ endTime 落账；`newAttempt` 的 attemptNo 由 `retryCount+1`（:136）改为该 recordId 下 `max(attemptNo)+1`（对账后单行 max 查询）——UK_RETRY_ATTEMPT_RECORD_NO（:365-368，只读消费）碰撞从根消除，崩溃恢复语义 = RUNNING attempt#1 在场时 scanner 重拾可继续推进（红⑨）。saveAttempt 补 duplicate-key 防御兜底（与 record 侧 `resolveInsertRaceWinner` :254-268 对称：catch UK → 对账关闭遗留行 → max+1 重插一次；兜底触发即 WARN，不静默）。**「终态写失败不产生 UK 碰撞」勘误 2 维持**：该面归 C3-006 簿记隔离，不并入本裁定。
- **僵尸记录死信化**：`executeRetryFromScanner` 对 `loadPolicyOrDefault` + `JsonTool.parseMap`（:321-322）包 try/catch，异常直接走 `moveToDeadLetter` 既有兜底（:305-319 同族漏网路径补齐）+ failedStage——损坏 JSON/策略装载异常的记录首次失败即死信化，不再每租约周期 144 条 ERROR 循环重拾；`doExecute` 内 invokeAsync **同步抛**时 whenComplete 未挂接的面补 try/catch——attempt 落 FAILED 落账（终态化）+ 业务链 failedStage。死信化契约句（「解析/同步异常路径的记录首败即死信化，死信保留全量快照」）落 owner doc。

### 3.8 裁定⑧：tcc 清理接线 + 耗尽终态化 + 索引（R01 对齐 + ORM PA 批内申报）

- **清理周期链（形态 A，防 C7-005 空壳先例回归）**：对齐 plan 2502 `startLedgerCleanup`（:709-753）同构——**钩子载体与装配落点一次定夺**：TccEngine 为普通 IoC bean（`tcc-core-defaults.beans.xml` `nopTccEngine` :12 亲读在案；其 `checkExpiredTransactions` 现零生产调用方——恢复扫描宿主装配属 CL-004/R4-10 边界，本批不扩），清理周期链经 **LifeCycleSupport 生命周期接口化**（doStart 启动链/doStop 取消，beans 装配 init-method 触发）挂生产可达钩子，落点 = 既有 `tcc-core-defaults.beans.xml` 装配行补属性（零新 beans 文件）；绝缘载体 `scheduleWithFixedDelay` + `IPeriodicTaskListener`（失败 ERROR+计数、链由设施重臂）；错配置（负保留期/周期<60s 下限/批删≤0）启动期 fail-fast；`retention-days=0` 显式禁用；每轮执行：①耗尽行终态化前置步——`UPDATE status=KILLED, endTime WHERE status<CONFIRM_SUCCESS AND retryTimes>=maxRetryCount AND expireTime<now`（KILLED 复用既有 `TccStatus.KILLED`，入 `getFinishedStatus()` :108 后即被既有清理谓词消费；**C7-006 接缝**：终态字段由本批定〔复用 status 列，零新列〕，观测/干预工具本体归 R4-10 消费同一语义）；②`removeCompletedRecords(retention, true)`（:334-355 既有，分支随父级联）按锚列 beginTime + 批删上限有界清理。**R01 对齐**：键族/缺省值/包络以 `ledger-retention-policy.md` §3.1/§3.2 为唯一事实源（本批零参数发明，键名沿 `nop.<module>.ledger.*` 规则落 `nop.tcc.ledger.*`），§3.4 传导表成员行已随本设计追加；后续参数修订只走该 SSOT。**防空壳回归断言**：清理体被调度器真实调用的接线断言为红⑪必选面（1.6-C7-005 先例形态），且同时断言 `tcc-core-defaults.beans.xml` 装配行存在（rule 23 接线 + 装配双面）。
- **索引（ORM PA 批内申报落地形态）**：
  - (a) **语句形态** = `CREATE INDEX IF NOT EXISTS idx_tcc_record_expire ON nop_tcc_record (expire_time, status, retry_times);` + `CREATE INDEX IF NOT EXISTS idx_tcc_record_begin_time ON nop_tcc_record (begin_time);`——PostgreSQL 幂等方言为基准口径，其他方言部署按语义等价转写（2514 §3.6(a) 范式投影）；**纯新增 CREATE INDEX 语句，非生成物改动、零表结构变更、零实体字段变更**。
  - (b) **脚本落点** = 新建非 `_` 前缀迁移脚本 `nop-tcc/deploy/sql/postgresql/`（如 `2026-10-add-tcc-record-indexes.sql`）；`_create_nop-tcc.sql`（`_` 前缀生成物）零手改，其同步走 orm 模板/生成链重建，不在本批顺手改。
  - (c) **生成链声明**：`nop-tcc/model/nop-tcc.orm.xml`（live 150 行，`<indexes>` 零命中已亲读复核）为源模型，索引接入仅改该模型 NopTccRecord `<indexes>` 段后经 codegen 重建，**禁止手改 `_gen/*.java`**；实体无新增字段 → 生成物 diff 仅限元数据面。orm PA 申报闭环 = plan 2515 头部 PA 行 + 本节 + Phase 1 独立评审，三载体在案。

### 3.9 裁定⑨：namespace 扫描覆盖

- **唯一结论 = fetch 过滤放宽为全部 namespace**：移除 `fetchPendingRecords` 的 `eq(namespaceId, namespaceId)` 硬过滤（:162），扫描器拾取面覆盖全部 namespace 的 PENDING/RETRYING 记录。理由：租户隔离实际由分区键（namespaceId:groupId:idempotentId → partitionIndex，:123-127）与幂等 UK（含 namespaceId）承担——同 namespace 聚落同一分区，扫描分区分配语义不变；fetch 的 namespace 过滤是单值 store 装配（:62 普通 setter 无分片装配）造成的意外收窄，非隔离设计。store 的 namespaceId 字段保留为提交缺省值用途（`findRecordByIdempotentId` 等显式传参路径零改写）。
- **行为闭合声明**：nop-job 侧零改码——`NopRetryJobRetryBridge` :40 `withNamespaceId(event.getNamespaceId())` 透传照旧，fetch 放宽后非 default 记录失败进入 PENDING 即可被扫描器拾取接管（红⑫）。**条件面标注保留**：default-only 部署不触发本缺陷、若某部署显式依赖「扫描面按 namespace 隔离」语义，属部署契约声明（落 owner doc 扫描覆盖声明节），判级不因条件面降级。

### 3.10 本批新增配置键与错误码一览（缺省值一次定夺，零键名二源）

| 项 | 值 | 裁定 | 语义 |
|----|----|------|------|
| `nop.tcc.ledger.retention-days` | 30（0=显式禁用） | ⑧ | tcc 两表保留期（R01 §3.1 投影） |
| `nop.tcc.ledger.cleanup-interval` | 1h（下限 60s） | ⑧ | 清理周期 |
| `nop.tcc.ledger.delete-batch-size` | 1000 | ⑧ | 单批删除上限 |
| `ERR_TCC_STATUS_CAS_CONFLICT` 族 | — | ①③ | 全局/分支 CAS 失败类型化错误（0 行/异常同型） |
| `ERR_RETRY_LEASE_LOST` 族 | — | ④ | 续租被抢败方中止错误 |
| `nop.retry.attempt.bookkeep-fail` | — | ⑥ | attempt 簿记失败 ERROR 通道（+计数） |

## 四、裁定⑩：测试缝与零回退钉

### 4.1 红①–红⑫驱动器（先红后绿，E2；缺陷态红判据 + 修复后绿口径）

| 红 | 载体 | 缺陷态红 | 修复后绿 |
|----|------|---------|---------|
| ①（C10-003） | tcc 双链对撞：恢复链 fetchExpired 认领 × 调用方 endAsync 并发（真并发注入） | confirm/cancel RPC 对撞照发（CAS 0 行被吞） | 败方 CAS 异常完成、补偿 RPC 恒单发 |
| ②（C10-003） | 双链 cancel 结果分歧注入 | 全局误报 CANCEL_SUCCESS（内存陈旧聚合） | 终态与 DB 分支权威状态一致 |
| ③（C10-003） | 全局首跳 CAS 同步异常注入 | 折叠 SUCCESS 直写终态（分支零驱动） | 异常传播、终态不直写 |
| ④（C10-006） | 长即时重试链 > 租约（600s，注入时钟）+ attempt 内挂死注入 | 扫描器接管双执行（轮间 + attempt 内窗） | 轮间续租保活/被抢败方中止不接管；attempt 内窗由超时上界（< 租约）闭合（挂死 attempt 取消落账后接管不双执行） |
| ⑤（C10-005） | OVERWRITE 重提 × 旧链在途 | 旧链续跑双执行、回写落空 | 代际短路、回写显式 0 行短路 |
| ⑥（C10-005） | 超限 payload OVERWRITE（约束失败确定性复现） | 任务蒸发无死信 | 原子替换成功或旧记录死信快照 |
| ⑦（C10-001 真异常注入，非串行假保护） | 二次 saveAttempt 瞬时 DB 故障注入 | 成功被改判失败并重试 | 业务结论保持、簿记失败可见（ERROR+计数） |
| ⑧（C10-001+C10-008） | T1（doExecute 前置持久化）/T2（cb1 回调体）同步 DB 异常注入 | join 永久冻结（部分形态零日志） | future 异常完成、扫描循环存活；C8-017 三通道断言随同 diff |
| ⑨（C10-005） | RUNNING attempt#1 注入 + scanner 重拾 | UK 抛出记录永久砖化 | 对账 STOPPED + max+1 推进 |
| ⑩（C10-007） | 损坏 payload 注入 | 每租约周期重拾零推进（僵尸） | 首败即死信化 |
| ⑪（C10-007） | 清理任务缺位/耗尽行注入（含接线断言） | 零清理零终态 | 周期清理+耗尽行 KILLED 终态化+索引生效（EXPLAIN/查询计划面） |
| ⑫（C10-006） | 非 default namespace 记录失败注入 | 永不接管（fetch 硬过滤） | 正常拾取接管；job 桥接零改码面断言 |

### 4.2 零回退钉（全列，钉零回改逐条 git diff 为空核对）

- `TestRetryScannerInsulation`（R1-01 绝缘契约）——裁定⑥ join 面与扫描链修复零回退。
- `TestRetryLedgerRetention_C7_001`（plan 2502 保留清理）+ `startLedgerCleanup`/`purgeExpiredLedgerRows` 现行实现——裁定⑧零触碰零回退。
- owner doc 成文契约：`nop-retry.md` §扫描链（:83-91 绝缘）、§账本保留策略（:92-108）；`nop-tcc.md` §崩溃恢复语义（:50-57 对称续跑+耗尽排除声明）——裁定①②零回退、裁定⑧只落终态化半边不改其判级。
- `TccTransaction.endAsync` 恢复路由注释钉（:59-84，plan 2388）——裁定①②③零 diff。
- `TccRecordStore.fetchExpiredRecords` 版本 CAS 认领（:319）——零回退。
- `resolveInsertRaceWinner` 兜底（:254-268）——裁定⑦对称引用其形态，本体零改写。
- `newRecord` 一次性推延注释钉（:116-121）——**唯一允许的钉改写**（裁定④协议替代），改写前后语义对照留痕登记。
- `TccRunner` 聚合优先级语义（BIZ/CANCEL/TIMEOUT→CANCEL_FAILED，:178-217）与 `TccStatus` 谓词族（:81/:97-101/:108）——裁定②只换输入源，函数语义零改写。

### 4.3 C10 六条集落名

C10-001（C3-006+C8-012 收口门，红⑦/红⑧真异常注入）、C10-003（C1-001+C3-002+C3-012 收口门，红①②③；覆盖面另含 C3-010/C6-015 批外——**R4-10 随本体补测，Phase 4 就绪注记列名**）、C10-005（C6-003+C6-002+C3-005 收口门，红⑤⑥⑨；C1-002 归 R4-10 不代做）、C10-006（C3-003+C7-012 收口门，红④红⑫；C3-013/C3-015/C7-011 批外）、C10-007（C7-003+C7-005 收口门，红⑩红⑪；C7-001 已随 2502 闭合、C6-005/C8-003/C8-004 dormant/R4-10）、C10-008（C8-017 收口门，红⑧三通道断言；覆盖面另含 C7-009/C8-016/C8-008/C8-010/C8-013/C8-015 批外、**C6-012[P3] 批外亦为 C10-009 覆盖面**）——六条均随对应本体同 diff 落地（E2）；补测类声明：新增断言即新钉、缺陷态红→修复后绿；上游环境抖动按 plan 2515 Baseline 勘误 5 三联裁定处理留痕。

### 4.4 接线与无静默跳过义务（rule 23/24）

- **接线验证**：裁定① CAS 失败传播真实短路分支驱动（双链对撞交错断言即证据，非仅 store 返回值存在）；裁定④ 续租 CAS 真实被即时重试链每轮调用（非仅方法存在）；裁定⑤ replaceRecord/代际校验真实被 handleBlockStrategy/回调路径消费；裁定⑥ 返回 future 完成态 ≡ 挂接链完成态（含异常路径）断言；裁定⑧ 清理任务真实被 beans 装配/周期链调用 + 索引真实生效（EXPLAIN 面）；裁定⑨ 放宽后非 default 记录真实被拾取。
- **无静默跳过**：裁定① CAS 失败显式异常完成（非 warn 后吞）；裁定④ 被抢中止显式（WARN+failedStage，非静默续跑）；裁定⑥ E-a/E-b/E-c 显式闭合（无永不完成形态残留）；裁定⑦⑧ 终态化路径显式可见（非空 catch）；全部新增公共分支无空方法体/TODO。
- **端到端验证义务**（rule 22，归 Phase 2）：retry 全链（提交→首执行→租约内即时重试→失败退避→扫描重拾→死信化→[接线面]清理，覆盖裁定④⑤⑥⑦⑨）；tcc 全链（Try→分支 RPC→恢复扫描认领→CAS 门控补偿→聚合终态→[接线面]周期清理，覆盖裁定①②③⑧）。

## 五、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ① CAS 失败返回失败标记（布尔/枚举） | 需改签名且每个消费点手工判空，漏判一点即复刻对撞面；thenCompose 组合子对异常完成的结构性短路零漏点 |
| ①② DB 级补偿租约（SELECT FOR UPDATE/租约列） | ORM/DDL 扩面 + 锁放大；进程内异常完成 + DB 权威聚合已闭合双驱动与误报面 |
| ② CAS 失败重载后再聚合（二段式） | 只覆盖失败分支，成功路径仍聚合内存副本——多驱动下内存副本同样陈旧；统一 DB 重载消除双数据源 |
| ④ 续租改扫描侧轮询心跳 | 写放大与扫描周期耦合；attempt 前 CAS 单点续租即闭合接管窗 |
| ⑤ 先插后删 / 失败补偿恢复旧记录 | 先插后删在任何事务边界下必撞幂等键 UK（旧行在场，约束冲突确定性失败）；缺陷本体是 delete+save 两独立事务的无包络组合，原子性由 `replaceRecord` @Transactional 单方法保证，废弃的是该组合而非删除-插入次序本身；补偿恢复引入第二失败面，事务回滚天然保旧记录 |
| ⑤ canceled 取消标记列 | 需 nop-retry.orm.xml 新列（Non-Goal 零触碰 + PA 扩面）；进程内代际 + DB 行存在性双校验零 DDL 即达 |
| ⑥ 异步汇聚替代 `allOf().join()` | 拓扑重写放大变更面；join 冻结根因是 future 无终态，兜底使每 future 必有终态后 join 有界，保留 join 变更面最小 |
| ⑥⑦ 簿记失败静默吞掉 / 空 catch | rule 24 违例；簿记失败必须 ERROR+计数显式可见 |
| ⑦ attemptNo 维持 retryCount+1 仅做 duplicate-key 容错 | 只治标：每次重拾仍撞 UK 再兜底一轮，砖化窗残留；对账+max+1 从根消除碰撞 |
| ⑨ 多实例装配扫描器（按 namespace 分片） | 装配面扩张、分区机制已承担分布；fetch 放宽零装配变更即闭合 |
| ⑨ 提交时校验 namespace 匹配 fail-fast | 把「扫描覆盖缺口」转嫁给提交方报错，重试承诺仍违约；放宽才是承诺修复 |
| ⑧ 保留策略参数模块内自定缺省 | R01 族级 SSOT 一处定稿纪律；键名/缺省值只引用 §3.1/§3.2 |

## 六、与已有设计的关系

- [ledger-retention-policy.md](ledger-retention-policy.md)：R01 族级 SSOT——裁定⑧为 plan 2502 收口注记「nop-tcc 两表清理归 R2-12、落地对齐本 SSOT」的承接执行：§3.4 传导表 tcc 两表成员行随本设计追加（锚列/键族/清理序引 SSOT 零发明），参数修订只走该文档；形态 A 周期链消费其 §3.1 第 5 项 fail-fast 包络。
- [periodic-task-insulation.md](periodic-task-insulation.md)：R1-01 绝缘层——裁定⑥ join 面、裁定⑧清理链消费其链存活/失败可见语义，全部扫描/清理面修复零回退（§3.1）；两层正交（设施管 Throwable 重臂，本批管 future 终态完备与业务级冻结）。
- [wf-task-scanner-fencing-and-task-wf-concurrency.md](wf-task-scanner-fencing-and-task-wf-concurrency.md)：裁定⑧索引迁移三行（语句形态/脚本落点/生成链声明）投影其 §3.6(b)/(c) 范式；orm PA 批内申报口径沿其先例。
- 下游：R4-10（8.50）硬依赖本批——批末就绪注记含裁定①（C3-008 弃链接缝）、⑤（C1-002 暴露面收窄）、⑦（C7-006 终态字段消费）接缝边界与裁定④ A2 令牌语义引用指向；R4-32（C7-011 时钟族）本批零触碰。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/03-modules/nop-retry.md`（OVERWRITE 原子化/代际语义、续租契约、簿记-业务分层契约、提交面失败交付契约[C8-017]、僵尸死信化契约、namespace 扫描覆盖声明、attempt 终态化声明、源码锚点补行）+ `docs-for-ai/03-modules/nop-tcc.md`（CAS 传播契约、聚合权威裁决契约、清理接线与 retention 声明[以设计文档与 R01 SSOT 为准]、索引声明、耗尽终态化接缝、源码锚点补行）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
