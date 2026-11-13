# C10 测试保护力 — nop-retry / nop-tcc 并发·异步·资源专项审计（本单元最后批次）

- 审计单元：`nop-retry` + `nop-tcc` 全部 src/test（java + resources），仓库 /Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 审计维度：C10 测试保护力（roadmap 1.6 横切裁定维度）——对照本单元 C1/C3/C6/C7/C8 五维全部保留发现，逐条评估现有测试套件的回归保护力
- 输入口径：本目录五个维度文件 `## 最终保留项` 节（含维度复核结论节）合计 **74 条保留发现**（C1×9 / C3×17 / C6×16 / C7×15 / C8×17；现状全部「新发现」，无「已知未修复」）+ 7 条已确认维度集外线索 CL-001~007（单列注记，不进矩阵主体）
- 严重度口径修正：任务书正文写「14 条 P1」，但按各文件最终保留项**实际判级**枚举出的 P1 ID 为 19 条（C1-001/003、C3-001~006/012/016、C6-001/002/003、C7-001/003/005/012、C8-001/012）；以实际判级为准，P1 详表按 19 条逐条单列。
- 纪律：只读。未修改任何测试、产品代码与五个维度文件；所有判定基于逐个打开测试文件核对断言，非凭测试名推断。

## 方法说明

1. **测试面逐一打开核对**：两模块 20 个测试源文件（13 个含 `@Test` 的测试类 + 7 个 fixture/工具类）全部打开精读，登记每个测试类实际断言的行为面。
2. **产品码锚点核实**：对矩阵中关键判定，下钻产品码确认「缺陷面」的确切位置，避免把相邻面误判为缺陷面：
   - `RetryEngineImpl.doStart`（nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:639-648）扫描批处理 `CompletableFuture.allOf(futures).join()` 无超时——该 lambda 从未被任何测试执行（TestRetryEngineImpl static 块将 scan-interval 调为 1h 显式关闭后台扫描；TestRetryScannerImpl 用 noop executor 并以同步 Consumer 直调 `doScan`）。
   - `RetryScannerImpl.doScan`（scanner/RetryScannerImpl.java:118-159）内层 catch Exception 续读、外层 catch Exception；`Error` 与调度期 `RejectedExecutionException` 均逃逸。
   - `TccEngine.checkExpiredTransactions`（nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:315-349）单条 `.get(expireGap)` 超时 catch TimeoutException 后仅记日志继续（future 丢弃）。
   - `TccBranchTransaction.finishCancelAsync`（TccBranchTransaction.java:158）`response.getBizFatal()` 无 null 防护。
   - `FakeTccRecordStore`（nop-tcc-core 测试 fixture）的 `updateTccStatusAsync`/`updateTccBranchStatusAsync` **永成功、无 CAS 语义**——意味着 tcc-core 全部测试在结构上无法触及「写失败/CAS 失败后链路行为」这一缺陷面；全单元唯一带真实 CAS 语义的测试面是 nop-tcc-dao 的 `TccRecordStore`+H2。
3. **保护力三档**（按任务书定义）：
   - `有钉`：存在测试钉住该缺陷面的正确面，缺陷回退会红；
   - `部分`：测试覆盖相邻面但缺陷面未被钉住（说明缺口）；
   - `无保护`：无任何测试触及该行为面。
4. **反向验证**：本矩阵最终 **无任何一条判「有钉」**（74 条缺陷均为当前 live code 中存在的未修复缺陷，凡缺陷面被现有测试钉住的场景只可能是「钉住缺陷行为本身」，归入测试套件自身缺陷注记，不计有钉）。按要求对最接近「有钉」的 4 条 `部分` 判定做了反向验证（见「反向验证记录」节），确认其断言确实不覆盖缺陷面，维持 `部分`。
5. **缺保护登记**：P1 全部 19 条均无完整保护（12 部分 + 7 无保护），连同 P2 抽样缺口最重项，按同族归组登记为 C10-001~009（统一 P2，进 roadmap 横切裁决通道）。

---

## 一、测试资产盘点

### 1.1 nop-retry（69 个 @Test）

| 被测面 | 测试类 | @Test 数 | 实际覆盖面（打开核对） |
|--------|--------|---------|----------------------|
| api 常量/契约 | `TestNopRetryApiConstants` | 7 | 默认常量健全性（间隔/抖动/分区/bool） |
| api 任务契约 | `TestRetryTaskContract` | 4 | IRetryTask fluent wither 契约 |
| api bean | `TestNopRetryPolicyBeans` / `TestNopRetryRecordBeans` | 3+6 | Input/OutputBean round-trip、label 独立性 |
| 引擎 | `TestRetryEngineImpl`（autotest+H2，`MockRpcServiceInvoker` 注入） | 30 | newRetryTask/pause/resume/retryFromDeadLetter 正反路径、executeTask 首试/DISCARD/立即重试/延迟重试/attempt 持久化/分区/死信/bizFatal/幂等键复用/TOCTOU 首提并发（RaceWindowRecordStore 会合栅栏）/deadline/OVERWRITE/退避确定性/回调三态 |
| 扫描器 | `TestRetryScannerImpl`（纯单测，mock store+noop executor） | 6 | start/stop 幂等、doScan fetch→lock→process 主干、lock 空 continue、processor 异常不外溢、running=false 短路 |
| store | `TestRetryRecordStoreImpl`（autotest+H2） | 8 | 分区指派、fetch 过滤（到期/分区/状态）、幂等查重全状态、tryLock→RETRYING、attempt 插后更新、moveToDeadLetter 删原行 |
| service | `TestRetryRecordXbizAuthMerged` | 4 | 4 实体 mutation 管理员鉴权注解合并 |
| web | `NopRetryWebPagesTest` | 1 | 页面校验 |
| 资源 | `app-test.beans.xml`（MockRpcServiceInvoker primary）、`application.yaml`（H2 mem） | — | — |

**登记的明显空洞（retry）**：
- `RetryEngineImpl.doStart` 的扫描批处理 lambda（`allOf().join()` 载体）：**零测试**。`TestRetryEngineImpl` static 块显式把后台扫描间隔设为 1h 并注明「对扫描语义的覆盖走 executeRetryFromScanner 直调」——生产扫描驱动路径整体处于测试盲区。
- `RetryScannerImpl` 真实调度环（`scheduleWithFixedDelay` 的 REE/Error 形态、`stopScanning` 对在途 doScan 的语义、`scanFuture` 观测）：零测试（noop executor 使 startScanning 从不真实调度）。
- `RetryEngineImpl` 停机面（doStop/定时器句柄）、乐观锁冲突路径：零测试。
- 全套件零指标/零背压/零参数护栏断言（无 0/负值参数用例）。

### 1.2 nop-tcc（93 个 @Test）

| 被测面 | 测试类 | @Test 数 | 实际覆盖面（打开核对） |
|--------|--------|---------|----------------------|
| core 编排（FakeStore） | `TestTccEngineTransactions` | 9 | runInTransaction 提交/回滚、registry 复用、缺失/已完结记录拒绝、非 TRYING 拒开分支、清理 onlyCompleted 契约 |
| core 分支语义（FakeStore） | `TestTccBranchTransactionSemantics` | 13 | finishTry 四态映射、begin/finish confirm/cancel 全部状态迁移与错误码、bizFatal |
| core 聚合（纯函数） | `TestTccRunner` | 10 | aggregateConfirm/Cancel 折叠优先级、TIMEOUT_FAILED 不算已取消、null 态脏数据跳过 |
| core 终态路由（FakeStore） | `TestTccTransactionEndStates` | 6 | endAsync 成功→confirm/异常→cancel/rollbackOnly、CONFIRMING 续 confirm、CANCELLING 拒 confirm 续 cancel（P1-3.8-004 改写）、已确认分支跳过 |
| core 帮助/状态 | `TestTccHelperAndStatus` | 7 | txnGroup 归一化、状态集合（finished/rollbackOnly/cancellable） |
| core 桥接 | `TestTccRpcServiceInterceptorBridge` | 5 | 头桥接进上下文、无头不桥接、出站不桥接、既有 TL 优先、participant 不开分支 |
| core meta | `TestTccServiceMetaReflection` | 7 | 注解反射构建、Async 后缀、merge、loader 回退 |
| dao 引擎（真 store+H2） | `TestTccEngine`（AbstractTccTest） | 24 | begin/confirm/cancel 全路径落库、超时扫描 confirm/cancel 双半区崩溃恢复、挂死补偿不阻塞调度环（@Timeout+elapsed 断言）、CAS stale writer 保护、混合分支补偿隔离、Error 传播+registry 恢复、脏数据 null 状态不 NPE、孤儿分支清理、重试计数递增 |
| dao store（真 store+H2） | `TestTccRecordStore` | 9 | 记录/分支 CRUD、CAS 状态写、过期拾取、完结清理、跨组隔离、快照 TCC 头 |
| service | `TestTccRecordXbizAuthMerged` | 2 | 鉴权注解合并 |
| web | `NopTccWebPagesTest` | 1 | 页面校验 |
| 资源 | `app-test.beans.xml`（MockRpcServiceInvoker primary） | — | — |

**登记的明显空洞（tcc）**：
- `FakeTccRecordStore`（core 套件唯一 store 替身）**永成功、无 CAS 语义、无失败注入**：tcc-core 全部 45 个编排/语义测试在结构上无法表达「状态写失败/CAS 0 行」后的链路行为——这正是 C1-001/C3-001/002/012/016、C6-015 的共同缺陷面。
- `TccTransactionRegistry` 并发面（instance() 检查-然后-行动、并发嵌套 LIFO 逆序恢复）：仅单线程顺序用例。
- `confirmAllAsync`/`cancelAllAsync` 多分支扇出：最多 2 分支、全同步完成，无规模/无界/挂死混合形态。
- `checkExpiredTransactions` 二周期行为（幽灵链与下周期新链双驱动）：仅单周期（且用 maxRetryCount=1 借耗尽过滤收尾）。
- tcc 过期扫描/清理的**生产装配**（谁在调度 checkExpiredTransactions/cleanCompletedTransactions）：测试全部直调，装配面零测试（对应 CL-004/C6-005/C7-005）。
- `TccContext` 清理（CL-003）、`@SingleSession` 会话跨度（C8-011）：零测试。

### 1.3 数量级口径

- `grep -c @Test`：nop-retry 69，nop-tcc 93，合计 **162**。
- 真实并发形态测试仅 1 个（`TestRetryEngineImpl#testConcurrentFirstSubmission_onlyOneRecordAndOneExecution`，带确定性会合栅栏）+ 1 个挂死注入（`TestTccEngine#testCheckExpiredNotBlockedByHangingCompensation`，`MockRpcServiceInvoker.setHang(true)`）。
- 失败注入分布：RPC 层异常注入（tcc MockRpcServiceInvoker.setException / RecordingInvoker.failRpc / retry setResponses 错误响应）较充分；**DB/写路径失败注入为零**；调度器/线程池拒绝注入为零；时钟注入为零。

---

## 二、保护力矩阵

### 2.1 P1 详表（19 条，逐条单列）

| 发现 ID | 缺陷一句话 | 档位 | 证据测试或缺口说明 |
|---------|-----------|------|-------------------|
| C1-001 | TCC 全局/分支状态 CAS 失败不阻断补偿 RPC，互斥守卫基于私有内存副本 | 部分 | `TestTccEngine#testStaleWriterDoesNotOverwriteTerminalStatus` 钉住 store 层 CAS stale-writer 拒写（正确面）；`TestTccEngine#testStateConflictProtection` 钉住单实例内存状态机拦截。缺口：**CAS 失败后补偿链是否继续下发 RPC** 无任何测试触及（需在补偿链中途注入 CAS 失败）；core 套件 FakeStore 无 CAS 语义，结构上不可表达 |
| C1-003 | retry 终态写与运维/竞争写并发，乐观锁冲突在 whenComplete 内被丢弃，future 永不完成或半成功 | 无保护 | 全套件零乐观锁冲突注入（无 updateRecord 冲突用例）；whenComplete 转发面的所有用例（如 `testExecuteTask_shouldSucceedOnFirstTry`）均为无竞争成功路径 |
| C3-001 | TCC CAS 写失败（0 行）不短路 confirm/cancel RPC 链 | 部分 | 同 C1-001 证据；`TestTccRecordStore#testUpdateTccStatus` 只钉成功写。缺口：链中途 0 行写后 RPC 继续下发，无测试 |
| C3-002 | 补偿聚合读内存分支状态，CAS 失败不回写→全局误报 CANCEL_SUCCESS 而分支 CANCEL_FAILED | 部分 | `TestTccRunner#aggregateCancel_whenHasCancelFailed_thenCancelFailed` 钉住折叠优先级（CANCEL_FAILED 压过 CANCEL_SUCCESS）——聚合函数本身有保护；缺口：**内存副本与 DB 行分叉**的输入形态在 FakeStore（内存=DB 同一对象）下不可表达，dao 套件亦无此形态用例 |
| C3-003 | retry 即时重试链跨异步边界不续租 nextTriggerTime，租约到期扫描器并发接管双执行 | 部分 | `testExecuteTask_shouldEnterDelayedRetryAfterImmediateRetriesExhausted` 钉住延迟路径 nextTriggerTime 设置、`testExecuteTask_shouldRetryImmediatelyAndSucceed` 钉住立即重试成功（且后台扫描被 1h 静音）。缺口：长调用下扫描器与在途链的双执行竞争无测试（扫描驱动被测试显式关闭） |
| C3-004 | 扫描批 `allOf().join()` 无超时阻塞共享 worker，单个挂死 RPC 令重试扫描永久停摆 | 无保护 | 缺陷载体 `RetryEngineImpl.doStart` lambda 无任何测试执行（见方法说明#2）；retry 侧无挂死注入用例（tcc 侧的挂死用例不覆盖此载体） |
| C3-005 | OVERWRITE 删行不取消在途异步链，旧链 RPC/定时续跑产生幽灵任务 | 部分 | `testExecuteTask_shouldHandleOverwriteStrategy` 钉住 OVERWRITE 单线程换行正确面。缺口：删行后在途链存续/双执行面无测试（无并发、无在途注入） |
| C3-006 | doExecute whenComplete 内联簿记写库，簿记异常覆盖业务结果→成功改判失败并重试 | 部分 | 全部 executeTask 用例钉住簿记成功时业务结果正确传播。缺口：簿记（attempt/record 写）失败注入为零，覆盖面缺陷不可见 |
| C3-012 | 全局终态聚合写不校验首跳中间态 CAS 是否生效，异常完成时零驱动分支折叠 SUCCESS 直写终态 | 部分 | `TestTccEngine#testConfirmRpcExceptionMarksConfirmFailed` / `testCancelRpcExceptionMarksCancelFailed` 钉住 RPC 异常完成→FAILED（此前面被修复过，有回归钉）。缺口：首跳中间态 CAS 无效（DB 瞬时故障）触发折叠 SUCCESS 的路径无测试，FakeStore 不可表达 |
| C3-016 | 分支级 begin/finish CAS 异常完成时内存不回写，聚合折叠 SUCCESS→全局正常完结+分支补偿未生效 | 部分 | `TestTccBranchTransactionSemantics#testFinishConfirmSuccessAndFailure` 等钉住 outcome→状态映射（RPC 异常→CONFIRM_FAILED/CANCEL_FAILED）。缺口：CAS/DB 写异常完成时内存不回写面无测试（同 FakeStore 结构性盲区） |
| C6-001 | executeImmediateRetry 完成回调同步异常→调用方 future 永不完成且异常静默丢弃 | 部分 | 立即重试成功/失败终态均有 future.get() 完成性断言（隐式钉住 happy 完成面）。缺口：完成回调同步抛异常的形态零注入（簿记失败注入为零），future 永不完成不可见 |
| C6-002 | 崩溃/挂死后 RUNNING attempt 残留，经 UK_RETRY_ATTEMPT_RECORD_NO 碰撞将记录永久砖化 | 部分 | `testExecuteTask_shouldPersistAttemptsForEachExecution`、`TestRetryRecordStoreImpl#testSaveAttempt_shouldInsertThenUpdateSameEntity` 钉住 attempt 持久化与 attemptNo 契约。缺口：崩溃恢复路径（RUNNING 残留→重入）与 UK 碰撞处置零测试 |
| C6-003 | OVERWRITE 阻塞策略 delete+insert 非原子：半个成功后任务蒸发且无死信 | 部分 | 同 C3-005：OVERWRITE happy path 有钉，原子性/半成功面零注入（无 delete 成功+insert 失败的故障注入） |
| C7-001 | retry 三张账本表零保留机制，成功即沉淀永久行 | 无保护 | 保留/清理功能不存在，全套件亦无任何 retention 断言（结构性无保护；修复后需新增 wiring+行为测试） |
| C7-003 | 解析/同步异常路径产生永不终止僵尸记录 + 每租约周期全栈 ERROR 放大 | 无保护 | `testRetryFromDeadLetter_shouldFailWhenRequestPayloadMissing` 钉住运维重放入口的载荷校验（相邻面）；扫描驱动执行的解析异常→记录处置面零测试（后台扫描被测试静音） |
| C7-005 | tcc 账本清理 API 零生产调用方 + 耗尽行双重永久排除 + 全表零索引 | 部分 | `TestTccEngine#testRemoveCompletedRecordsDeletesNonTerminalBranchesOfFinishedParent`、`TestTccRecordStore#testRemoveCompletedRecords`、`TestTccEngineTransactions#testCleanCompletedOnlyRemovesCompleted` 钉住清理**逻辑**面。缺口：生产装配（谁调度）零测试、耗尽排除的可见性面零断言、schema 索引零断言 |
| C7-012 | 扫描器只服务单一 namespace：非 default 记录永久 PENDING、零日志零接管 | 无保护 | `IRetryRecordStore.fetchPendingRecords` 签名即无 namespace 参数；`TestRetryRecordStoreImpl#testFetchPendingRecords_shouldApplyFilters` 只断言到期/状态过滤，无 namespace 维度用例（多 namespace 场景全单元零测试） |
| C8-001 | 扫描循环被拒/Error 即静默永久停摆（REE 形态零日志） | 无保护 | `RetryScannerImpl.doScan` 只 catch Exception；REE/Error 形态与 `scheduleWithFixedDelay` 拒绝路径零测试（noop executor 使调度环从不真实运行） |
| C8-012 | executeImmediateRetry future 拓扑断裂→扫描批 allOf.join 永久冻结 | 无保护 | 与 C3-004 同载体（doStart lambda 零测试）+ C6-001 的回调异常面（零注入）；future 拓扑断裂形态无任何测试可红 |

### 2.2 P2 归组表（38 条）

| 组 | 成员 ID | 组缺陷一句话 | 档位 | 证据测试或缺口说明 |
|----|---------|-------------|------|-------------------|
| G1 OVERWRITE/幂等原子性 | C1-002、C8-006 | OVERWRITE delete+insert 非原子/无 UK 兜底，并发或故障下半个成功 | 部分 | `testExecuteTask_shouldHandleOverwriteStrategy`（happy path）、`testConcurrentFirstSubmission_onlyOneRecordAndOneExecution`（**首次插入** TOCTOU 有确定性会合栅栏钉住，属同族相邻面）；缺口：OVERWRITE 分支的并发/UK 冲突/半成功注入零测试 |
| G2 驱动 future 完成性 | C1-008、C6-016 | whenComplete 转发面生产者同步抛出使 future 永不完成；attempt 终态写失败毒化业务结果 | 部分/无保护 | C1-008：happy 完成性被全部 executeTask 用例隐式钉住，同步抛出形态零注入→部分；C6-016：attempt 写失败注入为零→无保护 |
| G3 registry/上下文结构 | C1-004、C3-007 | registry 身份分裂；LIFO 恢复并发嵌套逆序错位 | 部分 | `TestTccEngineTransactions#testRunInTransactionReusesRegisteredTxn`、`TestTccEngine#testNestedRunInTransaction`、`testErrorThrowStillCompensatesAndRestoresRegistry` 钉住单线程复用/恢复面；并发逆序完成与 instance() 竞争面零测试 |
| G4 全局取消 vs 分支 | C1-007、C7-013 | 取消认领后分支仍可创建；终态父事务分支失恢复入口；守卫读陈旧内存副本 | 部分 | `testBranchRejectedWhenTxnNotTrying`（内存守卫）+ `testRemoveCompletedRecordsDeletesNonTerminalBranchesOfFinishedParent`（孤儿清理）钉住两端相邻面；缺口：陈旧内存副本下的竞争创建窗口、store 层无条件插入面零测试 |
| G5 恢复扫描幽灵链 | C3-008、C6-006、C6-015 | `.get(expireGap)` 超时不取消在途补偿链，幽灵链与下周期新链双驱动；future 丢弃致可二次补偿；CANCELLING 标记落库失败短路致方向翻转 | 部分 | `testCheckExpiredNotBlockedByHangingCompensation` 钉住**不永久阻塞**（elapsed<15s + 中间态 + retryTimes 递增）；`testCancellingStateRejectsConfirm` 钉住 CANCELLING 续跑 cancel 面。缺口：在途链不取消、二周期双驱动、标记写失败形态零断言（该用例以 maxRetryCount=1 借耗尽过滤回避了二周期） |
| G6 无防护输入 | C3-009、C6-007 | finishCancelAsync 对 (null,null)/(response==null) NPE，手工 future 无人完成→cancel 链挂死 | 无保护 | `TestTccBranchTransactionSemantics#testFinishCancelOutcomes` 只测 (resp,null) 与 (null,ex)，(null,null) 形态零用例；FakeStore 不产生 null 响应路径 |
| G7 逐分支隔离 | C3-010 | confirmAllAsync 越界状态分支无逐分支隔离，中断批次补偿并误报 CONFIRM_SUCCESS | 部分 | `testAlreadyConfirmedBranchSkippedOnConfirm`（confirm 侧跳过已确认）与 `testCancelSkipsAutoConfirmedBranch`（cancel 侧混合分支隔离，修复回归钉）为相邻面；缺口：无效状态分支同步抛异常中断批次→误折叠形态零测试 |
| G8 pause/resume 竞争 | C3-013 | 无状态 CAS 守卫，在途链无条件回写覆盖 | 部分 | `testPause_shouldSucceedForPendingRecord`/`testResume_shouldSucceedForSuspendedRecord`/`testResubmitAfterSuspended_shouldStaySuspendedWithoutCrash` 钉住同步面（重提交不翻 SUSPENDED 有明确断言）；缺口：在途链 whenComplete 回写竞争面零测试 |
| G9 账本零保留 | C6-004 | retry DB 账本零保留/清理路径，三表无界增长 | 无保护 | 功能不存在，零断言（同 C7-001 结构性无保护） |
| G10 tcc 驱动装配 | C6-005 | 过期扫描与完结清理无生产驱动，TRYING 残留永不回收 | 部分 | `testTimeoutWithoutBranches`/`testCancelRecoveryAfterCrash`/`testConfirmRecoveryAfterCrash` 钉住扫描**逻辑**；装配面（bean/调度存在性）零测试，逻辑删除或断线不会红 |
| G11 遗留记录工具面 | C6-009 | 已放弃非终态记录与分支永久残留且永不补偿，无人工工具面 | 部分 | 孤儿分支清理用例钉住「已完结父+非终态分支可清理」缓解面；缺口：放弃态记录的补偿/人工处置面零测试 |
| G12 retry 扫描线停摆 | C6-008、C7-002、C7-004、C7-015、C8-002 | 扫描批 join 无超时单点挂死全 namespace 冻结；单 RPC 挂死整体永久停摆；Error 逃逸终止周期任务；整轮排水串行心跳无上限退化 | 无保护 | 缺陷载体 `RetryEngineImpl.doStart` lambda 零测试执行（后台扫描被测试静音）；retry 侧无挂死注入/调度环容错用例（tcc 侧挂死用例不覆盖此载体）；排水轮形态零测试 |
| G13 清理端点边界 | C8-003 | tcc 清理端点无界批处理（全量物化+巨型 IN） | 部分 | 清理功能三用例钉住正确删除面；边界（limit/分批）不存在故零断言 |
| G14 恢复取数序 | C8-004 | fetchExpiredRecords beginTime DESC 最新优先→最老饿死+拾取即计费 | 无保护 | `testFetchExpiredRecords` 只断言「能拾取」，排序零断言 |
| G15 耗尽可见性 | C8-005、C7-006 | 补偿预算耗尽后静默永久排除，双向不可见零观测 | 部分 | `testCheckExpiredNotBlockedByHangingCompensation` 以断言**钉住了排除行为本身**（记录停留 CONFIRMING、循环结束）——钉的是缺陷行为（见测试套件自身缺陷注记 N1）；观测面（日志/指标/移交）零断言 |
| G16 补偿扇出 | C8-007 | 全分支一次性并发扇出无上限+不可取消+僵尸链累积 | 无保护 | 全部 endAsync 用例 ≤2 分支且同步完成；扇出规模/取消通道零测试 |
| G17 指标缺失 | C8-008 | 积压/拒绝/死信化/驱动健康零指标 | 无保护 | 全套件零指标断言（无可钉之物，属结构性缺失） |
| G18 回调环护栏 | C7-008、C8-009 | 回调策略成环无护栏，副作用与死信自增殖 | 部分 | `testCallback_shouldTriggerOnSuccess/OnFailure/shouldNotTriggerWhenDisabled` 钉住回调触发面；环形态被测试注释**显式回避**（见注记 N2），护栏零测试 |
| G19 提交契约 | C8-017 | 提交面失败交付三通道不对称（IRpcCall 契约缺口） | 无保护 | api 契约测试（TestRetryTaskContract）只覆盖 fluent wither；失败交付通道零测试 |
| G20 时钟 | C7-007、C7-011 | tcc 期限全链路原始墙钟（NTP 风暴）；retry 首提租约混用本地与 DB 估计时钟 | 无保护/部分 | C7-007：时钟抽象零注入→无保护；C7-011：`testExecuteTask_shouldRecordFailedAttemptForDelayedRetryPath` 显式预热 `recordStore.getCurrentTime()`（DB 估计时钟被使用但一致性面未断言）→部分 |
| G21 tcc 期限扫描规模 | C7-010 | 过期扫描整轮耗时随挂死补偿数线性放大（100×expireGap 上限） | 无保护 | 挂死用例只测单条记录+单周期；多记录整轮时长上界零断言 |
| G22 扫描批粒度 | C7-014 | 批内单点同步异常中断整批流式执行，靠后记录确定性饿死 | 部分 | `testDoScan_processorException_doesNotPropagate` 钉住「异常不杀死扫描环、下一 fetch 继续」（单记录批）；同批剩余记录续跑面零断言（饿死窗不可见） |
| G23 租约竞争/双执行 | C1-009 | 首执行防双执行仅靠 newRecord 一次性推延 nextTriggerTime，扫描器与在途链并发双执行 | 部分 | `testConcurrentFirstSubmission_onlyOneRecordAndOneExecution` 钉住**提交期** TOCTOU（相邻面）；扫描器接管在途长调用的租约窗零测试（见 RV-3） |
| G24 参数护栏（retry） | C8-013 | 批量/速率参数零护栏（0 值静默空转/负值退化/紧循环/无界批） | 无保护 | 零 0/负值参数用例（scanner 测试只设正常值 100/5000） |

### 2.3 P3 归组表（17 条）

| 组 | 成员 ID | 组缺陷一句话 | 档位 | 证据测试或缺口说明 |
|----|---------|-------------|------|-------------------|
| H1 惰性初始化/可变静态 | C1-005、C1-006 | executor 非同步惰性初始化；ReflectionTccServiceMetaBuilder.INSTANCE public static 非 final | 无保护 | 双初始化与字段可变性均无测试触及（meta 功能测试为相邻功能面，不断言字段终态性） |
| H2 停机/调度残余 | C3-011、C3-014、C6-010 | stopScanning cancel(false) 不中断在途 doScan；已调度即时重试定时器停机后仍触发；定时器句柄丢弃 | 部分/无保护 | C3-011：`testStartStopScanning_isIdempotent` 钉住幂等面，在途中断语义零断言→部分；C3-014/C6-010：stop 后在途 schedule 行为零测试→无保护 |
| H3 死信重放协调 | C3-015 | 死信重放绕过幂等键，与同键新提交双执行无协调点 | 部分 | `testRetryFromDeadLetter_shouldInvokeRpcWhenValid` 钉住重放功能面（无条件发起 RPC——即无协调，功能面与缺陷面同体）；竞争协调零测试 |
| H4 分支准入 | C3-017 | 守卫读内存+插入无父状态条件+清单一次加载→孤儿分支 | 部分 | `testBranchRejectedWhenTxnNotTrying` 钉住内存守卫面；陈旧副本+条件插入缺失面零测试（dao 层插入无父状态条件无用例） |
| H5 死配置 | C6-011、C7-009、C8-014、C8-016 | 分支 maxRetryTimes 只写不读；nextRetryTime 死字段；retryingTimeoutMs 引擎级零读方 | 无保护/部分 | maxRetryTimes 家族（C6-011/C7-009/C8-014）：测试 fixture 反复 `setMaxRetryTimes(5)` 但从不断言执行——「只写不读」被 fixture 无意识固化，执行面零测试→无保护；C8-016：`testTryLockRecordsForProcess_shouldMovePendingToRetrying` 以 60000L 实参钉住锁函数功能面（引擎读方仍零）→部分 |
| H6 扫描线韧性 | C6-013、C6-014 | 锁全失败无退避热循环；扫描线单点静默死亡（Error/拒绝不重臂、scanFuture 零观测） | 部分/无保护 | C6-013：`testDoScan_lockEmpty_retriesNextBatch` 钉住 continue 环形态（fix 加退避不会红，环面有保护、退避缺失面不可见）→部分；C6-014：Error/REE/重臂/观测零测试→无保护 |
| H7 会话/上下文 | C8-011、C6-012 | @SingleSession 会话横跨整批 join 驻留；桥接 setCurrent/removeCurrent 同步异常窗口 | 无保护/部分 | C8-011：零测试→无保护；C6-012：桥接 5 用例钉住 happy/TL 优先/participant 面，异常窗配对零断言→部分 |
| H8 参数护栏/观测（tcc+通用） | C8-010、C8-015 | 无 namespace/group 提交配额；分区解析空列表静默 fail-open | 无保护 | 两者均为「缺失的护栏」，全套件零配额/空列表参数用例 |

---

## 三、缺保护发现清单（C10-001 起，统一 P2，进 roadmap 横切裁决通道）

### nop-retry-nop-tcc-C10-001
- **严重度**：P2（缺保护登记横切通道）
- **缺口描述**：retry 驱动 future 的**完成性保证**与**结果保全**完全无失败注入覆盖。`executeImmediateRetry`/`doExecute` 的 whenComplete 簿记链在簿记写库失败或回调同步抛出时的行为（future 永不完成、成功被改判失败、乐观锁冲突被吞）没有任何测试可变红；现有用例全部走无注入成功路径。
- **涉及缺陷 ID**：C1-003、C1-008、C3-006、C6-001、C6-016、C8-012
- **建议测试形态**：故障注入型单测——以可编程 `IRetryRecordStore` 装饰器（参照既有 `RaceWindowRecordStore` 模式）在 `updateRecord`/`saveAttempt` 上注入同步异常与乐观锁冲突，断言：调用方 future 在有限时间内完成（或按契约失败）、业务成功结果不被簿记异常覆盖、异常被记录而非静默丢弃；另补回调体同步抛出形态。
- 缺口涉及 P1：是（C1-003、C6-001、C8-012）。

### nop-retry-nop-tcc-C10-002
- **严重度**：P2
- **缺口描述**：retry 扫描驱动线的**停摆韧性**零保护。生产扫描批处理载体（`RetryEngineImpl.doStart` 的 `allOf().join()` lambda）零测试执行（测试显式静音后台扫描）；`RetryScannerImpl` 调度环的 REE/Error 形态、doScan 异常后重臂、挂死注入均无用例。本组缺陷回归或修复均不可见。
- **涉及缺陷 ID**：C3-004、C6-008、C7-002、C7-004、C7-014、C7-015、C8-001、C8-002、C6-013、C6-014
- **建议测试形态**：调度环容错单测（真实 `IScheduledExecutor` + 可编程 invoker）：(a) `scheduleWithFixedDelay` 抛 REE 后断言日志/重臂/状态可见；(b) processor 抛 `Error` 后断言环不死；(c) invoker 挂死时断言批处理有界完成（参照 tcc 侧 `testCheckExpiredNotBlockedByHangingCompensation` 的 elapsed 断言模式）；(d) 批内 3 记录、第 1 条同步抛出，断言同批其余记录仍被执行。
- 缺口涉及 P1：是（C3-004、C8-001）。

### nop-retry-nop-tcc-C10-003
- **严重度**：P2
- **缺口描述**：TCC 补偿链对**状态写失败/CAS 失败的短路语义**零保护。core 套件 `FakeTccRecordStore` 永成功且无 CAS 语义（结构性不可表达）；dao 套件有真实 CAS 但只测了 store 层拒写原语，从未在补偿链中途注入 CAS 失败断言「RPC 不再下发/聚合不误折叠」。
- **涉及缺陷 ID**：C1-001、C3-001、C3-002、C3-010、C3-012、C3-016、C6-015
- **建议测试形态**：dao 层（真 store+H2）链路注入测试——先以 `updateEntityDirectly` 把全局/分支推进到与内存副本分叉的状态（参照 `testStaleWriterDoesNotOverwriteTerminalStatus` 的分叉构造手法），再驱动 endAsync/checkExpiredAsync，断言：CAS 失败分支不产生 confirm/cancel RPC（MockRpcServiceInvoker 计数）、全局终态不误折叠为 SUCCESS、内存不回写时聚合取 DB 态。
- 缺口涉及 P1：是（C1-001、C3-001、C3-002、C3-012、C3-016）。

### nop-retry-nop-tcc-C10-004
- **严重度**：P2
- **缺口描述**：TCC 恢复扫描的**多周期行为**（幽灵链、双驱动、耗尽可见性）与**扇出规模**零保护。现有挂死用例仅单记录+单周期（借 maxRetryCount=1 耗尽过滤收尾），二周期双驱动、在途链取消、耗尽记录的观测/移交、全分支扇出上限均无断言。
- **涉及缺陷 ID**：C3-008、C6-006、C7-006、C7-010、C8-005、C8-007
- **建议测试形态**：二周期扫描测试（可编程 invoker：首轮挂死、次轮恢复）：断言同一条记录的补偿 RPC 总量（无双驱动）、首条在途链被取消或失效（无幽灵叠加）；耗尽记录用例断言可见性信号（日志/状态标记）而非仅「循环结束」；多分支（≥3）混合状态扇出用例断言补偿全部下发且无越界状态中断。
- 缺口涉及 P1：否（全 P2，但为 C8-005/C7-006 修复的直接回归面）。

### nop-retry-nop-tcc-C10-005
- **严重度**：P2
- **缺口描述**：retry **OVERWRITE 原子性与 attempt 崩溃恢复**零保护。OVERWRITE 只有单线程 happy path；首次插入 TOCTOU 有钉但 OVERWRITE 分支的 UK 冲突兜底/半成功注入为零；RUNNING attempt 崩溃残留→UK 碰撞砖化的恢复路径零测试。
- **涉及缺陷 ID**：C1-002、C3-005、C6-002、C6-003、C8-006
- **建议测试形态**：(a) 装饰器在 OVERWRITE 的 insert 半段注入约束冲突/异常，断言旧记录不被删除或任务不蒸发（原子性契约）；(b) 手工预置 RUNNING attempt 行后重放同记录，断言不发生 UK 碰撞砖化（恢复语义）；(c) OVERWRITE 并发双提交（复用 RaceWindow 会合栅栏手法）断言恰好一个链存活。
- 缺口涉及 P1：是（C6-002、C6-003）。

### nop-retry-nop-tcc-C10-006
- **严重度**：P2
- **缺口描述**：retry 记录**租约/接管竞争面**零保护。扫描器与在途调用链的双执行窗口（租约不续租、首执行仅靠一次性推延）、pause/resume 与在途链回写竞争、OVERWRITE 幽灵链、死信重放与同键新提交的协调、非 default namespace 断链——全部无测试触及（后台扫描在测试中被系统性静音，使这一整面不可测）。
- **涉及缺陷 ID**：C1-009、C3-003、C3-013、C3-015、C7-011、C7-012
- **建议测试形态**：双驱动竞争测试——真实调度 + 慢 invoker（长调用期间手动触发一次 `executeRetryFromScanner`），断言 RPC 总调用次数（双执行检测，幂等键级）；pause 后完成在途链断言终态不被回写翻转为 PENDING；namespace 维度用例（非 default 记录 + 扫描器运行）断言被拾取或显式告警。
- 缺口涉及 P1：是（C3-003、C7-012）。

### nop-retry-nop-tcc-C10-007
- **严重度**：P2
- **缺口描述**：两模块账本**保留/清理的生产装配与边界**零保护。清理逻辑本身有钉（tcc 侧 3 用例），但「谁在生产环境驱动扫描与清理」零测试（逻辑断线不会红）；retry 侧保留机制整体缺失（无物可钉）；清理/取数的边界（limit、排序、索引）零断言。
- **涉及缺陷 ID**：C6-004、C6-005、C6-009、C7-001、C7-003、C7-005、C8-003、C8-004
- **建议测试形态**：(a) 装配存在性测试（beans/autotest 环境断言扫描驱动与清理调度的 bean 存在且 doStart 后被调度——防「逻辑有、接线无」回归）；(b) retry 保留机制实现后补删除行为+保留窗用例；(c) 取数排序断言（最老先拾取）与分批上限断言；(d) ORM 模型索引断言（schema 元数据校验）。
- 缺口涉及 P1：是（C7-001、C7-003、C7-005）。

### nop-retry-nop-tcc-C10-008
- **严重度**：P2
- **缺口描述**：**参数护栏、死配置执行面与观测面**零保护。批量/速率参数 0/负值、分区空列表 fail-open、提交配额缺失、双模块零指标、分支级 maxRetryTimes/retryingTimeoutMs 死配置——这类「缺失的护栏/执行面」在套件中既无用例也无 fixture 意识（fixture 反复写死配置字段反而固化了只写不读现状）。
- **涉及缺陷 ID**：C6-011、C7-009、C8-008、C8-010、C8-013、C8-014、C8-015、C8-016、C8-017
- **建议测试形态**：参数校验表驱动用例（batchSize=0/-1、scanIntervalMs=0/-1、空分区串→断言显式拒绝或 fail-closed+告警）；死配置执行面用例（设 maxRetryTimes=1 的分支补偿两次后断言停止——修复后生效）；死信/积压指标冒烟断言（触发一次死信后指标计数可读）。
- 缺口涉及 P1：否（全 P2/P3，量广面散，作为一批护栏回归登记）。

### nop-retry-nop-tcc-C10-009
- **严重度**：P2
- **缺口描述**：TCC **结构并发与无防护输入**零保护。registry instance() 检查-然后-行动、并发嵌套 LIFO 逆序恢复、finishCancelAsync (null,null)、桥接上下文清理异常窗、executor 惰性双初始化、可变静态字段——全部无测试触及；现有相关用例均为单线程 happy/已知态面。
- **涉及缺陷 ID**：C1-004、C1-005、C1-006、C3-007、C3-009、C3-011、C3-014、C6-007、C6-010、C6-012
- **建议测试形态**：(a) (null,null)/null-response 边值用例（现 `testFinishCancelOutcomes` 补两行形态即可，断言以错误完成而非 NPE 挂死）；(b) 并发嵌套 runInTransaction 逆序完成用例（两线程交错 begin/end，断言 registry 恢复正确配对）；(c) 停机后定时器不触发的时序用例（stop 后推进时钟/等待周期，断言零 RPC）。
- 缺口涉及 P1：否（本组全 P2/P3）。

---

## 四、测试套件自身缺陷注记（修复阶段须一并改写，不另立发现）

- **N1** `TestTccEngine#testCheckExpiredNotBlockedByHangingCompensation`：以 `maxRetryCount=1` + 断言记录停留 CONFIRMING 中间态、循环正常结束收尾——**把 C8-005/C7-006 的「耗尽即静默永久排除」缺陷行为当作预期契约钉住**。修复引入耗尽处置（死信/移交/告警）时，本用例预期必须同步改写，否则成为回归阻碍。属 P1-3.8-004 先例（`TestTccTransactionEndStates` 类注释记载的「原断言固化恢复死锁缺陷，随修复改写」）的现役同类。
- **N2** `TestRetryEngineImpl#testCallback_shouldTriggerOnSuccess/OnFailure` 注释「回调策略必须是独立策略且自身不再配置 callback，否则会递归触发回调链」——测试以**回避缺陷配置**的方式规避 C7-008/C8-009 的环形态，套件编码了「危险配置存在且无护栏」这一事实；护栏实现后应补环形态用例。
- **N3** retry/tcc 两侧 `MockRpcServiceInvoker` 均无慢响应/部分失败/乱序完成形态（tcc 侧仅 setException/setHang 两态，retry 侧仅序列响应）——G16 扇出、G14 排序、G5 幽灵链等面**结构性不可测**的根因在测试基建；修复阶段建议随 C10-004/C10-006 一并增强 invoker，而非仅加用例。
- **N4** `TestRetryEngineImpl` static 块以 1h 扫描间隔静音后台扫描是合理的隔离手法，但使生产驱动路径（doStart lambda）成为永久盲区；C10-002 落地时需为该载体单开不依赖后台扫描的直调用例（保持隔离前提）。

## 五、反向验证记录（对最接近「有钉」的 4 条 部分 判定，读断言核实其不覆盖缺陷面）

1. **RV-1（C1-001/C3-001 ← `TestTccEngine#testStaleWriterDoesNotOverwriteTerminalStatus`）**：该测试直调 `tccRecordStore.updateTccStatusAsync(record, CANCELLING, null)`，断言 DB 保持 CONFIRM_SUCCESS——只钉 store 层 CAS 拒写原语；全程无补偿链执行，无 RPC 计数断言。「CAS 失败后链路是否继续下发 RPC」确实未被覆盖，维持部分。
2. **RV-2（C6-006/C3-008 ← `TestTccEngine#testCheckExpiredNotBlockedByHangingCompensation`）**：断言仅三点——elapsed<15000ms、分支停留 CONFIRMING、retryTimes=1。无「在途 future 被取消」断言（挂死 invoker 的 future 被有意遗弃）；无二周期断言（maxRetryCount=1 使下轮拾取被过滤回避）。幽灵链/双驱动面确实未被覆盖，维持部分（且构成注记 N1）。
3. **RV-3（C1-002/C1-009 ← `TestRetryEngineImpl#testConcurrentFirstSubmission_onlyOneRecordAndOneExecution`）**：会合栅栏在 `findPendingRecordByIdempotentId` 查重之后——精确钉住**首次插入**的 TOCTOU 窗，断言恰好 1 行+1 次 RPC。OVERWRITE 的 delete+insert 窗与「扫描器接管在途长调用」窗均不在此测试路径上，维持部分。
4. **RV-4（C7-014 ← `TestRetryScannerImpl#testDoScan_processorException_doesNotPropagate`）**：批内仅 1 条记录，processor 抛 IllegalStateException 后断言不外溢+fetchCount=2——钉住「环不死、下一 fetch 继续」；同批**剩余记录**是否续跑（饿死面）无断言（结构性无法断言：批只有 1 条），维持部分。

## 六、统计

### 6.1 全量（74 条）

| 档位 | 条数 | 占比 |
|------|-----|------|
| 有钉 | 0 | 0% |
| 部分 | 40 | 54.1% |
| 无保护 | 34 | 45.9% |

（未修复缺陷审计的预期形态：凡缺陷面被现有测试钉住者只可能是钉住缺陷行为本身，已全部归入注记 N1/N2，不计有钉。）

分维度：C1 有钉 0/部分 6/无保护 3；C3 有钉 0/部分 14/无保护 3；C6 有钉 0/部分 9/无保护 7；C7 有钉 0/部分 6/无保护 9；C8 有钉 0/部分 5/无保护 12。

### 6.2 P1 保护力专项（19 条）

| 档位 | 条数 | 明细 |
|------|-----|------|
| 有钉 | 0 | — |
| 部分 | 12 | C1-001、C3-001、C3-002、C3-003、C3-005、C3-006、C3-012、C3-016、C6-001、C6-002、C6-003、C7-005 |
| 无保护 | 7 | **C1-003、C3-004、C7-001、C7-003、C7-012、C8-001、C8-012** |

P1 无保护共性：retry 侧全部集中于「测试静音后台扫描」造成的生产驱动路径盲区（C3-004/C8-012 的 join 载体、C7-003 的扫描驱动执行、C7-012 的 namespace 面）与簿记失败注入缺失（C1-003）；加上零保留/零 namespace 的结构性缺失（C7-001/C7-012）。tcc 侧 P1 无部分保护全因 FakeStore 无 CAS 语义+dao 套件未做链路中途注入（12 条部分级 P1 的共同缺口，见 C10-003）。

### 6.3 缺保护发现

9 条（C10-001~009），其中涉 P1 的 6 条（C10-001/002/003/005/006/007），全部统一 P2 严重度进入 roadmap 横切裁决通道。

### 6.4 测试资产量级

162 @Test（retry 69 / tcc 93）；真实并发形态用例 1、挂死注入用例 1、DB/写路径失败注入 0、调度器拒绝注入 0、时钟注入 0。

---

## 七、CL-001~007 注记（维度集外线索，不进矩阵主体）

| 线索 ID | 一句话 | C10 保护力注记 |
|---------|--------|---------------|
| CL-001 | 完成写失败后 RETRYING 存活记录被扫描器重复执行副作用 | 无保护：完成写失败注入为零（C10-001 同根），扫描器重复执行竞争在测试中不可达（后台扫描被静音） |
| CL-002 | TccContext 清理挂 whenComplete，永不完成 future 时残留 IContext | 无保护：桥接/上下文清理的异常窗零测试（同 C6-012 缺口） |
| CL-003 | 同上形态（C2 面），复用环境上下文入口可达 | 同 CL-002 注记 |
| CL-004 | tcc 过期扫描/清理零生产装配 | 无保护：装配面零测试；修复若补默认驱动，须按 C10-007(a) 补装配存在性回归 |
| CL-005 | removeCompletedRecords 无 limit 全量物化 + txnIds 无界列表 | 部分：清理功能面有钉、边界面零断言（同 C8-003/C10-007(c)） |
| CL-006 | 完成回调内同步 DB 事务占用 RPC 共享完成线程池 | 无保护：线程占用/池健康零测试 |
| CL-007 | retryFromDeadLetter 零生产装配/运维闭环出口缺位 | 部分：重放功能面有钉（`testRetryFromDeadLetter_shouldInvokeRpcWhenValid`），装配与运维闭环面无保护 |

---

*C10 子代理（测试保护力维度）· 2026-10-16 · 只读纪律：未修改任何测试/产品代码/五维文件；产出仅本文件。*

---

## C10 复核结论（独立复核子代理，2026-10-16）

复核口径：与初审相互独立，以怀疑姿态对保护力矩阵逐行打开测试文件核对断言（非凭测试名）、对 C10-001~009 逐条裁定、对 N1~N4 逐条核实、对五维文件最终保留项做口径对账。只读复核：除本节外未修改任何内容。

### R1. 矩阵抽验明细（26 行，超出 ≥10 行要求）

**P1 行（9 条，要求 ≥4）：**

| 行 | 抽验证据（亲自打开核对） | 裁定 |
|----|------------------------|------|
| C1-001（部分） | `TestTccEngine#testStaleWriterDoesNotOverwriteTerminalStatus`（:599-615）确为直调 `updateTccStatusAsync` + 断言 DB 保持 CONFIRM_SUCCESS，全程无补偿链、无 RPC 计数断言；`testStateConflictProtection`（:186-197）确为单实例内存状态机拦截。「CAS 失败后补偿 RPC 是否继续」确无测试触及 | **维持部分** |
| C1-003（无保护） | 通读 `TestRetryEngineImpl` 全文 + `RaceWindowRecordStore` 装饰器：`updateRecord` 仅透传，全套件零乐观锁冲突注入；所有 whenComplete 转发用例均为无竞争成功路径 | **维持无保护** |
| C3-003（部分） | `testExecuteTask_shouldEnterDelayedRetryAfterImmediateRetriesExhausted`（:286-325）确钉 nextTriggerTime 非空 + PENDING 存续；`testExecuteTask_shouldRetryImmediatelyAndSucceed`（:242-280）钉立即重试成功。扫描器接管窗零测试（static 块 1h 静音，:62-68） | **维持部分** |
| C3-004（无保护） | grep 全部 retry 测试：`.start()` 仅 `Thread.start()`（:675），`doStart` 零调用；产品码 `RetryEngineImpl.doStart` 的 `allOf(futures).join()` 无超时载体属实（:637-655） | **维持无保护** |
| C3-012（部分） | `testConfirmRpcExceptionMarksConfirmFailed`/`testCancelRpcExceptionMarksCancelFailed`（:224-273）确以 `setException` 注入并断言 FAILED 落库（修复回归钉存在）；首跳中间态 CAS 无效→折叠 SUCCESS 路径无测试（FakeTccRecordStore 无 CAS 语义） | **维持部分** |
| C6-002（部分） | `testExecuteTask_shouldPersistAttemptsForEachExecution`（:330-376）+ `TestRetryRecordStoreImpl#testSaveAttempt_shouldInsertThenUpdateSameEntity`（:183-207）确钉 attempt 持久化与 attemptNo 契约；RUNNING 残留→UK 碰撞恢复路径零测试 | **维持部分** |
| C7-005（部分） | 清理逻辑三用例属实（`testRemoveCompletedRecordsDeletesNonTerminalBranchesOfFinishedParent` :622-651、`TestTccRecordStore#testRemoveCompletedRecords` :130-147、`testCleanCompletedOnlyRemovesCompleted` :153-161）；生产装配零测试经 beans 核实（tcc-core-defaults.beans.xml 仅有 nopTccEngine 等 3 bean，无任何调度驱动；`checkExpiredTransactions`/`cleanCompletedTransactions` 在 main 代码中零调用方） | **维持部分** |
| C7-012（无保护） | 产品码 `RetryRecordStoreImpl.fetchPendingRecords`（:160）确以 `namespaceId == store.namespaceId`（默认 default）硬过滤且 beans 零覆盖配置；`TestRetryRecordStoreImpl#testFetchPendingRecords_shouldApplyFilters`（:78-100）只断言到期/状态过滤；多 namespace 用例全单元为零 | **维持无保护** |
| C8-001（无保护） | `RetryScannerImpl.doScan` 确为双层 `catch (Exception)`（:141-144 内层、:153-155 外层），`Error` 逃逸；调度期 REE 在 `startScanning`（:92-100，`running=true` 先于 `scheduleWithFixedDelay` 且无捕获）逃逸；`TestRetryScannerImpl` noop executor（:139-157）使调度环从不真实运行，REE/Error 形态零用例 | **维持无保护** |

**「部分」行（9 条，要求 ≥2）：** G7/C3-010（`testCancelSkipsAutoConfirmedBranch` :375-411 + `testAlreadyConfirmedBranchSkippedOnConfirm` :172-183 确钉两端隔离相邻面；越界状态分支中断批次形态零用例——所有分支 fixture 均为合法状态）；G22/C7-014（RV-4 逐字属实：批内确仅 1 条记录）；G18（回调三用例 :839/:872/:907 确钉触发面，环形态被注释显式回避）；G23/C1-009（RV-3 属实：会合栅栏确在查重返回 null 之后，:1004-1018）；H6/C6-013（`testDoScan_lockEmpty_retriesNextBatch` :227-247 确钉 continue 环）；H5/C8-016（`testTryLockRecordsForProcess` :168-180 确以 60000L 实参钉功能面，引擎读方零断言）；H2/C3-011（`testStartStopScanning_isIdempotent` :190-195 仅幂等面）；G5/RV-2（挂死用例断言确仅 elapsed<15000 + CONFIRMING + retryTimes=1 三点）；C3-002（`aggregateCancel_whenHasCancelFailed_thenCancelFailed` :115-119 确钉折叠优先级；FakeStore 内存=DB 同一对象，分叉形态确不可表达）。**全部维持部分。**

**「无保护」行（8 条，要求 ≥2）：** G6（`testFinishCancelOutcomes` :154-171 确仅 (resp,null)/(null,ex)/(timeout,null,ex) 三形态，(null,null) 零用例；产品码 `finishCancelAsync` 中 `response.getBizFatal()` 在 ex==null 且 response==null 时确 NPE——锚点属实）；G16/C8-007（通读 TestTccEngine/TestTccTransactionEndStates/TestTccEngineTransactions：全部 endAsync 用例确 ≤2 分支且同步完成）；G24/C8-013（scanner 测试确仅设 batchSize=100/scanIntervalMs=5000 正常值）；G14/C8-004（`testFetchExpiredRecords` :115-127 确仅断言「能拾取」，排序零断言）；G17/C8-008（grep 两模块测试目录：零指标断言，仅 CoreMetrics 时间戳）；H1（`ReflectionTccServiceMetaBuilder.INSTANCE` 确为 public static 非 final（:14），meta 测试仅使用不断言终态性；`RetryEngineImpl.getExecutor()`（:621-625）确非同步惰性初始化且零双初始化测试）；H5 maxRetryTimes 家族（fixture 5 处 `setMaxRetryTimes(5)` 只写，grep main 代码零 `getMaxRetryTimes` 读方——「只写不读」在产品码层亦证实）；G9/C6-004（`TestRetryRecordStoreImpl` 通读零保留/清理断言）。**全部维持无保护。**

**抽验小结：26 行抽验 0 行需要改档。** C10 矩阵的证据描述与测试实物逐行相符，未发现凭测试名推断或档位虚高/虚低。

### R2. C10-001~009 复核裁定表

| 发现 | a) 档位自洽 | b) 测试形态可执行 | 裁定 |
|------|-----------|-----------------|------|
| C10-001 | ✓ 成员 C1-003/C6-016/C8-012 无保护、C1-008/C3-006/C6-001 部分，与矩阵一致 | ✓ `RaceWindowRecordStore` 装饰器模式现存同包可复用 | **维持** |
| C10-002 | ✓ 成员全在部分/无保护档（C7-014/C6-013 为部分，其残余缺口确属扫描环族） | ✓ `DefaultScheduledExecutor` 已在 `TestRetryEngineImpl.createTestExecutor` 使用；挂死 elapsed 断言模式有 tcc 先例 | **维持** |
| C10-003 | ✓ 7 成员全部部分档，与矩阵逐条相符 | **注记**：建议形态中「MockRpcServiceInvoker 计数」——dao 侧 mock 现无调用计数器（仅 lastRequest），需随用例一并补计数器（core 套件 `RecordingInvoker` 已有先例），非不可执行 | **维持（附注记）** |
| C10-004 | ✓ 6 成员全 P2，与矩阵 G5/G15/G16/G21 档位一致 | ✓ dao mock 的 `setHang` 为 volatile 布尔，二周期「首轮挂死、次轮恢复」可直接翻转实现 | **维持** |
| C10-005 | ✓ 5 成员全部部分档 | ✓ OVERWRITE insert 半段注入可经装饰器；RUNNING attempt 预置行可行（`RETRY_ATTEMPT_STATUS_RUNNING` 常量现存） | **维持** |
| C10-006 | ✓ 6 成员档位相符（C3-003/C7-012 为 P1） | 注记：慢 invoker 需先增强 retry 侧 mock（现仅同步序列响应）——N3 已自我声明该依赖，自洽 | **维持** |
| C10-007 | ✓ 8 成员档位相符，涉 P1 三条正确 | ✓ (a) 为「修复后」形态（现确无生产驱动 bean 可断言——经 beans 核实）；(d) ORM 索引断言经 autotest schema 元数据可行 | **维持** |
| C10-008 | ✓ 9 成员全 P2/P3，不涉 P1 正确 | ✓ 参数表驱动用例仅需 plain 实例化，设施齐备 | **维持** |
| C10-009 | ✓ 10 成员全 P2/P3 | ✓ (a) 在现 `testFinishCancelOutcomes` 补两行形态即可（TccBranchTransaction 可直接以 FakeBranchRecord 实例化）；(b)(c) 设施齐备 | **维持** |

**结论：C10-001~009 全部维持登记，无驳回、无降档；两处可执行性注记（C10-003 计数器、C10-006 invoker 增强）均已被 N3 或 core 先例覆盖，不构成登记缺陷。**

### R3. 补充发现（自 grep 抽查，登记 1 条，编号续 C10-010）

**nop-retry-nop-tcc-C10-010（P2）**
- **缺口描述**：retry 扫描驱动执行入口（`RetryEngineImpl.executeRetryFromScanner`，包私有）的**回归保护被测试注释虚报**。`TestRetryEngineImpl` static 块（:62-68）声称「本类对扫描语义的覆盖走 executeRetryFromScanner 直调（见 testExecuteRetryFromScanner*）」，但**全套件不存在任何名为 `testExecuteRetryFromScanner*` 的测试方法**；该方法实际仅 1 处直调（deadline 用例 :715）。入口自身的坏记录死信守卫（空 serviceName/method/payload → moveToDeadLetter + failedStage，即 C7-003 的修复面，:268-290）零用例——C7-003 行登记的「扫描驱动执行的解析异常→记录处置面零测试」与此吻合，本条将其落实为可执行缺口并登记测试资产失真本身。
- **涉及缺陷 ID**：C7-003（修复面回归保护）。
- **建议测试形态**：同包直调 `executeRetryFromScanner` 三形态：(a) 坏载荷/坏 service → 断言记录进死信且不再被 `fetchPendingRecords` 拾取（钉 C7-003 修复面）；(b) 正常载荷+成功响应 → 断言 COMPLETED 与 attempt 落库；(c) 失败 → retryCount 递增 + PENDING + nextTriggerTime 推进；并修正 static 块注释指向真实用例名。
- **可执行性**：方法为包私有、测试类同包（io.nop.retry.engine.impl），可直接调用；不依赖后台扫描解静音。
- 缺口涉及 P1：否（C7-003 所在 P1 组已有 C10-007 覆盖装配面，本条补入口行为面与资产失真）。

**附注 N5（测试套件自身缺陷，随 C10-010 一并改）**：`TestRetryEngineImpl` static 块注释引用不存在的 `testExecuteRetryFromScanner*` 用例，属覆盖声明失真——与 N1（钉住缺陷行为）、N2（回避缺陷配置）同族的第三种形态：**注释声称的覆盖不存在**。修复阶段随 C10-010 用例落地一并修正注释。

**其余排查面已核清、无需补登记**（列出以防重复劳动）：时钟注入（零，与 G20 一致）、调度拒绝注入（零，与 C8-001 一致）、store 层双扫描器 tryLock 竞争与 RETRYING 过期行回收（实质归属 C6-002/C10-005(b)/C10-006 已登记面）、桥接上下文清理（CL-002/003 已注记）、死信重放装配（CL-007 已注记）、tcc service 鉴权测试 2 用例与实体数匹配。

### R4. N1~N4 注记复核

| 注记 | 核对证据 | 裁定 |
|------|---------|------|
| N1 | `testCheckExpiredNotBlockedByHangingCompensation`（:438-479）：确以 `maxRetryCount=1`（:470）收尾、断言分支停留 CONFIRMING（:477）+ retryTimes=1（:478）；行内注释「下一轮过滤排除，循环结束」自证把耗尽排除当预期契约。P1-3.8-004 先例确载于 `TestTccTransactionEndStates` 类注释（:21-22） | **属实** |
| N2 | `testCallback_shouldTriggerOnSuccess`（:845）与 `OnFailure`（:878）注释逐字为「回调策略必须是独立策略且自身不再配置 callback，否则会递归触发回调链」——以回避缺陷配置规避环形态 | **属实** |
| N3 | dao 侧 `MockRpcServiceInvoker` 实为 setResponse（默认成功）/setException/setHang 三态，core 套件另有内联成功 lambda 与 `RecordingInvoker(failRpc)`；**无有界慢响应/部分失败/乱序完成形态**成立。retry 侧 mock 确仅同步序列响应（:82-90 永 completedFuture） | **属实（表述精度注记：tcc 侧为「三态两故障形」而非「两态」，不影响结论）** |
| N4 | static 块（:62-68）确以 `nop.retry.scanner.scan-interval-ms=3600000` 静音后台扫描且注明理由；doStart lambda 零测试执行经 grep 证实 | **属实（另见 R3/N5：该注释还引用了不存在的用例名，失真形态比 N4 记载的更进一层）** |

### R5. 口径一致性核对（对五维文件 `## 最终保留项`）

| 口径项 | C10 声明 | 五维文件实测 | 一致 |
|--------|---------|-------------|------|
| 保留项总数 | 74（C1×9/C3×17/C6×16/C7×15/C8×17） | 9+17+16+15+17=74，五文件「驳回项：无」均核对 | ✓ |
| P1 判级 19 条 | C1-001/003、C3-001~006/012/016、C6-001/002/003、C7-001/003/005/012、C8-001/012 | 五文件逐条判级逐一相符（C7-012 为「P1（条件面）」、C8-001 为「P1（修正后维持）」，C10 按实际判级计入，处理正确） | ✓ |
| CL-001~007 单列注记 | 7 条不进矩阵主体 | CL-001（C6 文件）、CL-002/003/005（C7 文件）、CL-004/006/007（C8 文件）；C1/C3 文件集外线索节均为「无」；dispatch-log 记录累计 7 条相符 | ✓ |
| @Test 计数 | retry 69 / tcc 93 / 合计 162 | 逐文件 grep 计数相符（retry 30+8+7+6+6+4+4+3+1=69；tcc 24+13+10+9+9+7+7+6+5+2+1=93） | ✓ |
| 档位统计 | 部分 40 / 无保护 34 | 按 P1(12+7)+P2(22+16)+P3(6+11) 逐格验算=40/34；分维度 C1(6/3)、C3(14/3)、C6(9/7)、C7(6/9)、C8(5/12) 逐 ID 归档验算全部相符 | ✓ |

### R6. 修正行清单（均不改变档位与统计）

1. **方法说明#1 资产计数失真**：「两模块 20 个测试源文件（13 个含 @Test 的测试类 + 7 个 fixture/工具类）」有误。实测为 **30 个测试 Java 源文件（20 个含 @Test + 10 个 fixture/工具类）**（retry 13=9+4，tcc 17=11+6）。@Test 总数 162 与逐类计数不受影响，纯资产盘点叙述错误。
2. **N3 表述精度**：「tcc 侧仅 setException/setHang 两态」应为「三态两故障形」（另有默认成功 setResponse 与 core 侧内联 lambda/RecordingInvoker）；本意（无有界慢响应/部分失败/乱序完成）成立。
3. **C10-003 可执行性附注**：建议形态中「MockRpcServiceInvoker 计数」需先为 dao 侧 mock 补计数器（现存 mock 仅 lastRequest）；core 套件 RecordingInvoker 为现成先例。

### R7. 最终统计裁定

- **保护力矩阵 74 条档位判定：26 行抽验 0 行改档，全部维持。**
- **缺保护登记 C10-001~009：全部维持，另补 1 条（C10-010，含 N5 注记），合计 10 条。**
- **测试套件自身缺陷注记 N1~N4：全部属实，另补 N5（覆盖声明失真）。**
- **最终保护力统计维持原判：74 条 = 有钉 0 / 部分 40（54.1%）/ 无保护 34（45.9%）；P1 19 条 = 部分 12 / 无保护 7；P1 无保护 7 条（C1-003、C3-004、C7-001、C7-003、C7-012、C8-001、C8-012）维持。**
- 复核纪律：只读。本节为对 C10 文件的唯一追加，未改动原文任何字符，未修改任何测试/产品代码/五维文件。

*C10 复核子代理 · 2026-10-16*
