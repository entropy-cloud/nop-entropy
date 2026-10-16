# C1 共享可变状态与可见性 — nop-retry / nop-tcc 第 2 轮深挖（盲审）

- 审计单元：`nop-retry` + `nop-tcc`（82 files / 7,972 lines 手写，不含 `_` 前缀生成物与 target/）
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 本轮输入：第 1 轮 `C1-shared-mutable-state.md`（C1-001~006）+ grep 基线 `_tmp/cra-baseline-nop-retry-nop-tcc.txt` + live code 复核
- 盲审纪律：除第 1 轮文件外未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；未读取同目录其他维度（C3/C6/C7/C8、dispatch-log）文件。
- 本轮目标（按深挖追加指令）：① C1-001 失效面扩展（CAS 不短路的全部状态推进站点）② C1-003 失效面扩展（retry 链全部回调点异常落点）③ 发布安全专项 ④ 状态枚举转移守卫矩阵 ⑤ DB 状态-内存副本一致性的其他站点。

---

## 1. 本轮发现

### [nop-retry-nop-tcc-C1-007] 全局取消认领后分支仍可创建（守卫读永不刷新的内存副本），且终态父事务下的分支失去一切恢复入口——跨终态错误 confirm 或 TRYING 分支永久悬挂

1. **发现ID**：nop-retry-nop-tcc-C1-007
2. **严重度**：P2（C1-001 同族前提上再叠加一个合取条件——认领窗口内创建新分支；后果严重：参与方资源跨终态提交或永久悬挂，按纪律条件化竞态降 P2）
3. **缺陷族**：F10 检查-然后-行动守卫原子性（创建守卫读私有内存副本）+ 补偿覆盖面缺口（分支批次快照装载 × 恢复扫描仅查父表）的复合失效面
4. **位置**：
   - `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java` L292-305（runBranchTransactionAsync，守卫 L295）
   - `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java` L58-59（endAsync 首动作一次性装载分支批次）
   - `nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java` L301（fetchExpiredRecords 状态过滤 `status < CONFIRM_SUCCESS(11)`，排除 CANCEL_SUCCESS(12) 等全部终态）
   - 恢复入口穷尽（grep 核实）：分支记录唯一的运行期读取路径是 `getBranchRecordsAsync`（TccRecordStore L141-147），唯一调用方是 `loadBranchTransactionsAsync`（TccEngine L126），唯一调用方是 `endAsync`（TccTransaction L59）——不存在任何独立的分支扫描/补偿入口。
5. **状态载体与读写方**：`nop_tcc_record.status`（DB，全局 CAS 保护）+ 调用方链私有内存副本（`tccRecord.status` 仅在**本链** `updateTccStatusAsync` CAS 成功时同步，TccRecordStore L194，跨链从不刷新）；`nop_tcc_branch_record.status`（DB）。写方：调用方链的 runBranchTransactionAsync（新分支插入）与恢复链的 cancel 批次；读方：L295 创建守卫（读内存副本）、cancelAllAsync 批次（endAsync 入口快照）、fetchExpiredRecords（仅父表）。
6. **证据片段**：

   TccEngine.java L294-299（创建守卫读调用方链私有副本，恢复链翻转 DB 后该副本永不刷新）：
   ```java
   // 如果事务已经结束，则不能执行分支
   if (txn.getTccStatus() != TccStatus.TRYING)
       return FutureHelper.reject(new NopException(ERR_TCC_TRANSACTION_NOT_ALLOW_START_BRANCH)
               .param(ARG_TXN_ID, txn.getTxnId()).param(ARG_TXN_GROUP, txn.getTxnGroup())
               ...
   ```

   TccTransaction.java L58-59（分支批次在 endAsync 入口一次装载，此后新建分支不可见）：
   ```java
   public CompletionStage<Void> endAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
       return tccEngine.loadBranchTransactionsAsync(this).thenCompose(branchTxns -> {
   ```

   TccRecordStore.java L299-303（恢复扫描仅查父表且排除全部终态父事务）：
   ```java
   // 已经超时
   query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_expireTime, minTime));
   // 状态为还没有结束
   query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_status, TccStatus.CONFIRM_SUCCESS.getCode()));
   ```
7. **触发时序**（全局事务 X，defaultTxnTimeout=60000ms，双驱动入口均有代码锚点）：
   - t0：调用方链开启 X（TRYING），分支 B1 的 try RPC 悬挂。
   - t1（>60s）：恢复扫描 `fetchExpiredRecords` 版本 CAS 认领 X → `endAsync(true,null,null)` → 全局 CAS TRYING→CANCELLING 成功 → cancel 批次 = 装载时刻快照 [B1] → B1 cancel 后 `aggregateCancelBranchStatus` 全成功 → 全局 CAS CANCELLING→CANCEL_SUCCESS(12) 成功，**父事务终态**。
   - t2：调用方链 task.apply 仍在继续（慢 RPC 场景），对下一服务发起 `runBranchTransactionAsync` → L295 守卫读**内存副本 TRYING**（从未刷新）→ 放行 → 新分支 B3 插入 DB（TRYING）→ try 成功 → B3=TRY_SUCCESS。
   - 变体 A（调用方正常完成）：调用方 endAsync(ok) → 内存 curStatus=TRYING → 非 rollbackOnly → doConfirmAsync → 内存守卫通过 → 全局 CAS TRYING→CONFIRMING **0 行**（DB=CANCEL_SUCCESS）→ warn 跳过 → confirmAllAsync → B3 分支 CAS TRY_SUCCESS→CONFIRMING **成功**（B3 行无人动过）→ **B3 confirm RPC 照发**——参与方在全局事务已回滚（CANCEL_SUCCESS）后提交预留资源；这不是 C1-001 的双驱动对撞（B3 从未进入任何 cancel 批次），而是"孤儿分支被独立确认"的新失效面。
   - 变体 B（调用方进程崩溃，或 endAsync 在 loadBranchTransactionsAsync 处 DB 失败）：B3 停留 TRY_SUCCESS/TRYING；父事务已是终态 12，被 L301 过滤排除，分支表无任何扫描入口 → **B3 永久悬挂**，参与方预留资源永不释放（除非参与方自建超时，模块外）。
8. **影响**：TCC 互斥不变量在"跨终态"方向被打破（变体 A：rollback 后 confirm）；或补偿覆盖面出现永久缺口（变体 B：资源泄漏且平台侧不可自愈，cleanup 只删终态父+其既有分支，`removeCompletedRecords` L344-354 同样以父快照 txnIds 为界）。全局/分支状态列本身无 DB 损坏。
9. **误报校准**：第 1 轮 C1-001 的 F3 已把 L295 登记为"分支创建守卫，内存副本"同族，但当时失效面只展开到"join 进正在补偿的事务/RPC 对撞"；本轮新立案的是两个此前未展开的面：①终态父事务不再有任何恢复入口（L301 过滤 + 分支无独立扫描，已穷尽 grep）②补偿批次为入口快照、后建分支天然不可见。"崩溃恢复对称续跑"契约声明的是单链路由，未声明"终态父事务的遗留分支可被续跑"或"取消认领后分支创建被阻断"——后者代码注释（L294"如果事务已经结束"）表明意图即阻断，属实现缺口非声明边界。
10. **F3 同族兄弟清单**（"守卫/覆盖面读私有或快照视图"全类枚举）：
    - `TccEngine.runBranchTransactionAsync` L295 —— 本发现主位（创建守卫）。
    - `TccTransaction.endAsync` L59 —— 分支批次入口快照装载（后建分支不可见），同根因复合面。
    - `TccRecordStore.fetchExpiredRecords` L301 —— 恢复面排除终态父事务 + 无分支独立扫描（设计缺口与守卫缺口叠加放大）。
    - `TccEngine.checkTransactionActive` L170-182 —— join 守卫读内存副本（第 1 轮已登记，本轮确认其 false-negative 方向同样放行"向 CANCELLING 事务创建分支"）。
    - `TccRunner.confirmAllAsync` L145 / `cancelAllAsync` L171 —— 批次内跳过判定读内存副本（第 1 轮已登记）。
11. **修复方向**：L295 守卫改为条件写/DB 复核（如分支插入前对父记录做 TRYING 条件 CAS 或依赖父状态外键约束语义）；或分支插入语句携带 `WHERE 父status=TRYING` 的认领语义；恢复面增加"终态父事务下非终态分支"的对账查询；endAsync 完成前重读一次分支表收敛窗口。
12. **置信度**：高（恢复入口穷尽经 grep 双向核实：getBranchRecordsAsync 唯一调用链闭合于 endAsync；时序每步有代码锚点；TccStatus 编码 11/12 与过滤条件已核对源码）。
13. **备注**：变体 A 与 C1-001 共享"调用方 end CAS 失败不短路"根因，修 C1-001（CAS 结果传播）可同时消除变体 A 的 confirm RPC，但**不能**消除变体 B（悬挂面与 CAS 无关，属覆盖面缺口）——故独立立案。
14. **复核状态**：保留 P2；新发现；四项核心事实独立证实——L295 守卫读链私有副本（beginAsync 后仅本链 CAS 成功时同步，TccRecordStore L194）、endAsync L58-59 批次入口快照、fetchExpiredRecords L301 `lt(status,11)` 排除全部终态（TccStatus 终态编码 11–15 已核对源码）、分支无独立恢复入口（getBranchRecordsAsync main 调用链闭合于 endAsync，grep 双向核实），变体 B（孤儿分支永久悬挂，removeCompletedRecords L334-355 仅按父快照清证据）无条件可达；一处批注（不改判级）：变体 A 的"B3 confirm RPC 照发"需前置分支不拦截批次——发现叙述配置下 B1 的取消终态（TIMEOUT_SUCCESS/CANCEL_SUCCESS 等）不在 beginConfirmAsync 允许集（TRY_SUCCESS/CONFIRMING/CONFIRM_FAILED/CONFIRM_SUCCESS），会同步抛异常在 B1 处中断整批 confirm，变体 A 仅在前置分支为自动确认（CONFIRM_SUCCESS 被跳过）/无前置分支/CONFIRMING 续跑态等配置下成立；触发面收窄与 P2 条件化判级相符。

### [nop-retry-nop-tcc-C1-008] retry 显式 future + whenComplete 转发模式：生产者/调度体同步抛出使驱动 future 永不完成（C1-003 同族失效面扩展，新结构位点 + 全回调点矩阵收口）

1. **发现ID**：nop-retry-nop-tcc-C1-008
2. **严重度**：P2（后果与 C1-003 路径 A 同级——调用方永久挂起 + globalWorker 线程慢性失血 + 扫描实例停摆；但触发以基础设施故障为条件——attempt 落库瞬时失败/executor 停机，非正常运维操作相遇，降 P2）
3. **缺陷族**：F9 吞异常致状态不一致的 C1 侧面（完成信号唯一挂接点之前的同步抛出无兜底）——C1-003 同族的新结构位点：不是"回调体中途抛出"，而是"返回 future 的生产者调用本身同步抛出，whenComplete 从未挂接"
4. **位置**：
   - `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java` L405-419（scheduleImmediateRetry 的 scheduled lambda，主位）、L347/L375（回调内调用 scheduleImmediateRetry 的同步抛出）、L454-456（doExecute 同步前置 DB 写）
   - 平台语义核实：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/FutureHelper.java` L122-129（futureCall 捕获 Throwable → rejected future，**同步安全**——排除 TccRecordStore 全部异步方法）
5. **状态载体与读写方**：`CompletableFuture<ApiResponse<?>> future`（L405，scheduled lambda 与外层回调 L348-354 之间的完成信号）与 `result`（L336，方法返回值）。写方：仅 lambda 内 `.whenComplete((resp, ex) -> future.complete...)`（L409-414）与外层回调 `.whenComplete((r, e) -> result.complete...)`（L348-354）两个显式完成点；读方：syncGet 调用线程 / scanner `allOf().join()`（L648）。executeImmediateRetry 的同步前置段（doExecute → `recordStore.saveAttempt(attempt)` L456，真实 DB 写）在 lambda 内**先于** future 挂接执行。
6. **证据片段**：

   RetryEngineImpl.java L407-417（生产者同步抛出 → complete 永不执行，也无 completeExceptionally 兜底）：
   ```java
   executor.schedule(() -> {
       executeImmediateRetry(record, policy, request, cancelToken, task)
               .whenComplete((resp, ex) -> {
                   if (ex != null) {
                       future.completeExceptionally(ex);
                   } else {
                       future.complete(resp);
                   }
               });
       return null;
   }, immediateRetryIntervalMs, TimeUnit.MILLISECONDS);
   ```

   RetryEngineImpl.java L454-456（lambda 内同步 DB 写，瞬时故障即抛出）：
   ```java
   NopRetryAttempt attempt = recordStore.newAttempt(record);
   attempt.setStatus(NopRetryConstants.RETRY_ATTEMPT_STATUS_RUNNING);
   recordStore.saveAttempt(attempt);
   ```
7. **触发时序**：记录 R 处于 immediate 阶段（retryCount < immediateRetryCount），首次尝试失败 → 外层回调 L347 调度 scheduleImmediateRetry → interval 后 timer 触发 lambda。
   - t1：lambda 内同步执行 executeImmediateRetry → doExecute → saveAttempt 遇瞬时 DB 故障（连接抖动/锁超时）抛出。
   - t2：lambda 异常终止；`future`（L405）的 complete/completeExceptionally 均未挂接执行 → future 永不完成 → 外层回调 L348-354 永不触发 → `result`（L336）永不完成。
   - t3：syncGet 调用方（callAsync 业务路径）永久挂起；scanner 路径下 `CompletableFuture.allOf(futures).join()`（L648，无超时）永久阻塞一个 globalWorker 线程，该 RetryScannerImpl 实例后续扫描全部停摆（与 C1-003 路径 A 同一后果链）。
   - 次生位点：L347/L375 的 `scheduleImmediateRetry(...)` 调用本身同步抛出（getExecutor 惰性初始化失败 / executor.schedule 在引擎停机时 RejectedExecutionException）——抛出点在外层 whenComplete 回调体内，同样中止回调 → result 永不完成。
8. **影响**：与 C1-003 路径 A 完全同型的资源慢性耗尽与请求悬挂，但触发源不同（本条 = 基础设施瞬时故障 + 停机竞态；C1-003 = 运维写竞争）。记录与 DB 状态本身一致（attempt 写失败无半写），损害集中在完成信号丢失。
9. **误报校准**：不是"回调内异常被丢弃"的 C1-003 原位点复述——本条的抛出发生在 whenComplete 挂接之前（生产者同步段），修复方式不同（需在 lambda 体内 try/catch 兜底 completeExceptionally，而非包裹回调体）；"低频路径粗粒度锁/无状态单例并发"豁免不适用（这是完成协议缺陷，非锁问题）。
10. **F3 同族兄弟清单**（retry 链全部 8 个 whenComplete 位点穷举 + tcc 同型位点，本轮 grep 矩阵全量核对）：
    - `executeImmediateRetry` 外层回调 L341-389 —— C1-003 原位（回调体内 updateRecord/handleExecution*/moveToDeadLetter 抛出 → result 永不完成）。
    - `executeImmediateRetry` 内层转发回调 L348-354 / L376-382 —— 回调体仅 complete，安全；但 `scheduleImmediateRetry(...)` 调用的同步抛出落入外层回调 → 归入本发现次生位点。
    - **`scheduleImmediateRetry` scheduled lambda L407-417 —— 本发现主位**（生产者同步抛出，新结构位点）。
    - `executeSingleAttempt` 回调 L430-446 —— 异常正确传播（whenComplete 派生 stage 携带），正例；但 handleExecutionSuccess 在 RPC 成功后抛出会把成功误报为失败（stage exceptional）→ 记录未被 COMPLETED → 下轮扫描重放副作用，属 at-least-once 语义内，登记不立案。
    - `doExecute` 回调 L463-488 —— 末次 saveAttempt 抛出 → stage exceptional → 上层按业务失败重试 → 副作用重放（at-least-once 语义内，登记不立案）。
    - `triggerCallback` 回调 L564-573 —— 体仅日志，安全；但其 `executeTask(callbackTask,...)` 的**同步段**可抛：loadPolicyOrDefault L162 → `loadPolicy` → `requireEntityById`（已核实 IEntityDao.java L150-155：缺失即抛 UnknownEntityException，loadPolicyOrDefault L164 的 null 分支为死代码）——callbackPolicyId 被删除时该同步抛落入外层回调（immediate 路径 → result 永不完成）或 executeSingleAttempt 回调（→ stage 误报失败），归入 C1-003/本发现的触发源枚举。
    - `doStart` processor 内回调 L639-643 —— 体仅日志，安全。
    - tcc 同型位点（触发更弱，登记不立案）：`TccRunner` L57-67 / L83-93 / L109-119 同为"显式 future + 转发 whenComplete"，回调体内 `normalizer.toApiResponse(ret)`（L58）与 `tccEngine.isSafeFailException(ex)`（L63，用户提供的 ITccExceptionChecker）同步抛出时 future 永不完成；TccRecordStore 全部异步方法经 futureCall 包裹已排除同步抛出。
11. **修复方向**：scheduled lambda 体整体 try/catch，任何同步异常兜底 `future.completeExceptionally`；L347/L375 调用点同样以 try/catch 包裹兜底 result；TccRunner 三处同型改为 `handle`/`whenCompleteAsync`（FutureHelper.whenCompleteAsync L354-370 已有 try/catch 正例）或 lambda 内兜底。
12. **置信度**：高（位点逐行核实；futureCall/whenCompleteAsync 的平台语义已下钻源码）。
13. **备注**：本条为 C1-003 的 F3 扩展成果中唯一够格独立立案的位点；其余位点已在第 10 字段矩阵中闭环（安全/正例/语义内/触发弱四类全覆盖），retry 链回调点无遗漏。
14. **复核状态**：保留 P2；新发现；主位与机制逐行核实——scheduled lambda L407-417 内 executeImmediateRetry 的同步前置段（含 doExecute L454-456 saveAttempt 真实 DB 写）先于 L409 whenComplete 挂接，同步抛出即 `future`（L405）永不完成；次生位点 L347/L375 的 scheduleImmediateRetry 同步抛出（getExecutor/executor.schedule RejectedExecutionException）落入外层回调同样成立；retry 链 8 个 whenComplete 位点（L341/348/376/409/430/463/564/639）经独立 grep 与矩阵吻合；FutureHelper futureCall（捕获 Throwable→reject，TccRecordStore 全部异步方法同步安全）与 whenCompleteAsync L354-370（try/catch→completeExceptionally）语义下钻证实；一处非承重措辞勘误：loadPolicyOrDefault L164 的 null 分支并非常规意义死代码（policyId 为 null/empty 时可达），准确表述是"policyId 非空但策略被删时 requireEntityById 先抛 UnknownEntityException、null 兜底对该触发路径不可达"。

### [nop-retry-nop-tcc-C1-009] retry 首执行防双执行仅靠 newRecord 一次性推延 nextTriggerTime（固定 10 分钟、不可配置、无续租），长调用下扫描器与在途调用链并发双执行同一业务调用

1. **发现ID**：nop-retry-nop-tcc-C1-009
2. **严重度**：P2（触发条件单一明确——单次业务调用超过 600s；后果：成功在途副作用被并发重放 + 终态写冲突级联；注释自声明的不变量被实现背离）
3. **缺陷族**：F10 检查-然后-行动守卫原子性（调用链在途事实与扫描器拾取判定之间无共享互斥量，仅靠时间推延近似）+ C1 可见性（扫描器对"调用链是否存活"不可见）
4. **位置**：
   - `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java` L116-119（newRecord 推延窗口）、L152-180（fetchPendingRecords 拾取条件）、L184-205（tryLockRecordsForProcess 仅仲裁扫描器之间）
   - `nop-retry/nop-retry-api/src/main/java/io/nop/retry/api/NopRetryApiConstants.java` L28（`DEFAULT_RETRYING_TIMEOUT_MS = 10 * 60 * 1000L`）
5. **状态载体与读写方**：`nop_retry_record` 行（status=PENDING、next_trigger_time、version）。调用链首执行期间**不改 status、不续租**（终态写仅出现在 handleExecutionSuccess/Failure）；扫描器以 `status IN (PENDING,RETRYING) AND nextTriggerTime<=now` 拾取（L163-164），以版本 CAS 加锁（L204，只挡其他扫描器）。调用链从未参与任何租约协议。
6. **证据片段**：

   RetryRecordStoreImpl.java L116-119（注释自声明"只在调用方失联后接管"，实现为固定单次推延）：
   ```java
   // 到期时间推迟一个租约周期：调用方发起首次执行期间记录保持PENDING且nextTriggerTime=now，
   // 会被扫描器误拾取并发起第二次执行（重复副作用）。推迟后扫描器只在调用方失联后接管
   record.setNextTriggerTime(new Timestamp(CoreMetrics.currentTimestamp().getTime()
           + NopRetryConstants.DEFAULT_RETRYING_TIMEOUT_MS));
   ```

   RetryRecordStoreImpl.java L163-164（拾取条件对在途调用链不可见）：
   ```java
   query.addFilter(FilterBeans.in(PROP_NAME_status, PENDING_STATUSES));
   query.addFilter(FilterBeans.le(PROP_NAME_nextTriggerTime, now));
   ```
7. **触发时序**：
   - t0：调用方 executeTask 首提成功（REQUIRES_NEW），记录 PENDING、nextTriggerTime=t0+600s、version=0；随即发起业务 RPC，该 RPC 悬挂 11 分钟（慢外部依赖/大报文处理）。
   - t0+600s：扫描器 fetchPendingRecords 命中（status 仍 PENDING——首执行不改状态；nextTriggerTime 已到）→ tryLockRecordsForProcess 版本 CAS 成功（PENDING→RETRYING，version→1，只挡扫描器同行）→ executeRetryFromScanner → **第二次并发执行同一 serviceName/serviceMethod/requestPayload**。
   - t0+660s：调用方 RPC 成功返回 → handleExecutionSuccess → updateRecord（内存副本 version=0）→ **0 行**（扫描器已 bump）→ ERR_ORM_UPDATE_ENTITY_NOT_FOUND → 落入 C1-003 已立案的异常落点缺陷（immediate 路径 result 永不完成 / singleAttempt 路径误报失败）。
   - 净效果：业务副作用已执行两次（调用方成功 + 扫描器在途/已成功），记录终态由后写者决定。
8. **影响**：违反代码自声明不变量（"扫描器只在调用方失联后接管"——实现只保证"600s 后接管"，把"慢"误当"失联"）；重复副作用对 TCC 类资源型操作即双重预留。附带缺陷细节：保护窗口用的是 API 层硬编码常量 DEFAULT_RETRYING_TIMEOUT_MS，与扫描器可配置的 retryingTimeoutMs（RetryScannerImpl L71-74）**不同源**——运维调大扫描器租约无法同步放宽首执行保护窗口。
9. **误报校准**：第 1 轮 R11 将该行登记为"乐观锁 CAS + UK 受护"，未展开调用方-扫描器方向；已声明契约"retryingTimeoutMs 执行中锁定超时"（owner doc）覆盖扫描器间租约竞争，不覆盖本窗口；at-least-once 语义声明的是"失败后重试"，本条是"成功在途中被重放"，且 L116-119 注释表明实现意图正是排除它——非声明边界。触发前提（单调用 >10min）是重试引擎的合法工作负载而非滥用。
10. **F3 同族兄弟清单**（"时间推延代替在途互斥"位点全枚举）：
    - `RetryRecordStoreImpl.newRecord` L116-119 —— 本发现主位（首执行窗口）。
    - `RetryEngineImpl.handleBlockStrategy` OVERWRITE 分支 L258-260 —— 覆盖重建的新记录同样以 now+600s 推延，同窗口同构（修主位时一并覆盖）。
    - `RetryEngineImpl.handleExecutionFailure` L522-525 —— 重试路径写 nextTriggerTime=now+interval 后 status=PENDING，扫描器在 interval 后接管是**声明语义**（此处调用链已结束），非缺陷，列此为边界对照。
    - 家族外确认：executeImmediateRetry 的 interval 调度（L394-420）不经过 DB 拾取通道，无此窗口。
11. **修复方向**：首执行前将记录置 RETRYING 并以 tryUpdateManyWithVersionCheck 完成"提交+占锁"原子化（调用链持有租约，终态写时释放）；或将推延窗口与扫描器 retryingTimeoutMs 配置同源并允许策略级覆盖；长期方向为租约续期（执行回调内 bump nextTriggerTime）。
12. **置信度**：高（拾取条件、状态转移时机、版本冲突级联逐行核实；常量值已核对源码）。
13. **备注**：本条与 C1-003 的关系：C1-003 立案的是"相遇后的异常落点"，本条立案的是"相遇本身不该发生而发生"（前置窗口）；两修不互替。
14. **复核状态**：保留 P2；新发现；四项事实逐行核实——newRecord L116-119 固定 600s 单次推延且注释自声明"只在调用方失联后接管"、拾取条件 L163-164 对在途调用链不可见（首执行不改 status 不续租）、tryLockRecordsForProcess L184-205 版本 CAS 仅仲裁扫描器之间、保护窗口用 API 层硬编码 DEFAULT_RETRYING_TIMEOUT_MS（NopRetryApiConstants L28）与扫描器可配置 retryingTimeoutMs（RetryScannerImpl L71-74）不同源；时序单条件（单调用 >600s）即可达，时序完备性达 P1 线；维持 P2 的裁量理由（租约缺失设计下超时接管属固有形态、>10min 单调用属非典型负载、被破坏的是代码注释自声明意图而非 owner doc 契约）见第 1 轮文件复核结论节——是否升 P1 留给 7.1 合并裁定。

---

## 2. 排除项/定向核查

### 2.1 发布安全专项（第 1 轮未专项盘点，本轮补做）

- grep（`new Thread` / `.start()` / `addListener` / `addObserver` / `registerListener`，两模块 src/main 全量、排除 `_gen`）：**零命中**。两模块确认无构造器启动线程、无构造器注册监听器位点。
- 构造器逐一核对：`TccRpcServiceInterceptor(ITccEngine, ITccServiceMetaLoader)`（L32-36）、`TccTransaction`/`TccBranchTransaction`/`TccEngine`/`TccRunner`（字段 final 赋值）、`RetryTaskImpl(this, ...)`（RetryEngineImpl L72——工厂方法内传递 this，非构造器内发布，且 RetryTaskImpl 无被回调的异步入口）、`RetryEngineImpl`/`RetryScannerImpl`/`RetryRecordStoreImpl`/`TccRecordStore`（无参构造 + IoC setter）——**无 this 逃逸**。
- 异步发布面复核：全部经 `CompletableFuture` 链完成语义 / `ScheduledExecutor` 提交建立 HB；`TccGatewayInterceptor.newCtxRef` 数组逃逸维持第 1 轮"安全发布"判定（T10）。**结论：发布安全方向干净，无新发现。**

### 2.2 状态枚举转移守卫矩阵（写点 × 守卫 × 落盘 全量）

**retry（nop_retry_record.status）**：

| 写点 | 守卫 | 落盘路径 | 判定 |
|---|---|---|---|
| newRecord L102 →PENDING | 无（新行） | saveRecord（REQUIRES_NEW 插入，UK 兜底仅主路径有——C1-002） | 已立案 |
| tryLockRecordsForProcess L197-198 PENDING→RETRYING | 版本 CAS 批量（L204，正确示范） | tryUpdateManyWithVersionCheck | 干净 |
| pause L132 →SUSPENDED | load→check（TOCTOU） | updateRecord（版本条件） | C1-003 已立案 |
| resume L152 →PENDING | load→check（TOCTOU） | 同上 | C1-003 已立案 |
| handleExecutionSuccess L500 →COMPLETED | 无状态前置，仅版本仲裁 | updateRecord | C1-003 已立案 |
| handleExecutionFailure L524 →PENDING | 同上 | updateRecord | C1-003 已立案 |
| moveToDeadLetter（RetryRecordStoreImpl L287-310） | @Transactional 包裹死信插入+删除；删除为版本条件（GenSqlHelper L186-191 `AND version=?` 已核实） | saveDeadLetter + deleteEntityDirectly | 干净（并发双跑时败方回滚或双死信行，后者为低危重复记录，不立案） |
| OVERWRITE L257 delete+insert | 无 UK 兜底 | deleteRecord + saveRecord | C1-002 已立案 |

**tcc（nop_tcc_record.status / nop_tcc_branch_record.status）**：

| 写点 | 守卫 | 落盘路径 | 判定 |
|---|---|---|---|
| newTccRecord L91 →CREATED | 无（内存态） | 随 saveTccRecordAsync L154 →TRYING 插入（新行，无需 CAS） | 干净 |
| updateTccStatusAsync L188（→CANCELLING/CONFIRMING/聚合终态） | 单语句 CAS [txnId+fromStatus] | updateByQuery；**失败不阻断上层** | C1-001/C1-007 已立案 |
| fetchExpiredRecords L313-319 认领 | 版本 CAS + 100 轮重试（正确示范） | tryUpdateManyWithVersionCheck | 干净 |
| newBranchRecord L107 →CREATED / saveBranchRecordAsync L211 →TRYING | **L295 内存副本守卫（本轮 C1-007 主位）** | 插入 | C1-007 已立案 |
| finishTryAsync L64/66/72/74/79 | 分支 CAS [branchId+fromStatus] | updateByQuery | CAS 在，失败不阻断——C1-001 族 |
| beginConfirmAsync L103 / beginCancelAsync L136 | 内存守卫（同族）+ 分支 CAS | updateByQuery | C1-001 族 |
| finishConfirmAsync L115/119/121 / finishCancelAsync L149/154/159/162 | 分支 CAS | updateByQuery | 同上 |
| removeCompletedRecords L334-355 | `status IN finishedStatus` 条件删除 | deleteByQuery（父-分支同事务） | 干净（与在途补偿的相交后果归 C1-001/C1-007） |

**矩阵结论**：全部写点已归位——本轮新增立案 1 处（分支创建守卫，C1-007），其余为第 1 轮已立案位点或干净项，无第三类遗漏。

### 2.3 DB 状态-内存副本一致性其他站点（可见性窗口）

- retry：pause/resume 每次调用 `loadRecord` 全新加载（无副本陈旧问题）；scanner 链副本自 tryLock 起陈旧，终态写由版本仲裁（C1-003 已立案）；`handleExistingRecord` L210 COMPLETED 判定 stale 读（第 1 轮 F3 已登记，失败方向低危）。
- tcc：`endAsync` 路由读 `tccRecord.getTccStatus()`（L63/100/122）为链私有副本——C1-001 主载体；`checkTransactionActive` L177 同族（第 1 轮已登记）；`TccRecordStore.getTccRecordAsync` L134 按 txnGroup 校验拒绝跨组加载（防御正确）。**无新增未立案位点。**

### 2.4 平台语义核实（本轮新下钻，支撑上述判定）

- `IEntityDao.requireEntityById`（nop-dao IEntityDao.java L150-155）：null 即抛 UnknownEntityException → loadPolicyOrDefault 的 null 分支为死代码（触发源归入 C1-008 第 10 字段）。
- `GenSqlHelper.genDeleteSql` L186-191：DELETE 含 `AND version=?` → deleteEntityDirectly 为版本条件删除（retry moveToDeadLetter / OVERWRITE delete 的仲裁语义成立）。
- `FutureHelper.futureCall` L122-129 / `whenCompleteAsync` L354-370：前者同步抛出转 rejected future（TccRecordStore 全部异步方法同步安全）；后者 handler 异常转 completeExceptionally（doCancelAsync/doConfirmAsync 聚合路径无丢失完成风险——与 TccRunner 裸 whenComplete 形成平台内正反对照）。
- `TccStatus` 编码与谓词：CONFIRM_SUCCESS=11、CANCEL_SUCCESS=12 等终态 ≥11，证实 fetchExpiredRecords L301 过滤排除全部终态父事务（C1-007 前提）；`isBranchCancellable(TRYING)=true` 证实取消批次会取消装载时已存在的 TRYING 分支（悬挂面仅限批次后建分支）。

### 2.5 同族登记（不另立案）

- `TccGatewayInterceptor` L70-77/L102-110 的 `TccContext` setCurrent/restore 与 `TccRpcServiceInterceptor` L46-53 setCurrent/removeCurrent：叠放式 save/restore，与 C1-004 的 registry put/restore 同根因同前提（同一 IContext 被两线程并发使用，模块内不产生该并发）；作为 **C1-004 的 F3 扩展位点**登记，维持 P2 不变，不另立案。
- `RetryEngineImpl.doExecute` L463-488 / `executeSingleAttempt` L430-446 的"成功后写失败→副作用重放"：at-least-once 声明语义内（见 C1-008 第 10 字段），登记不立案。
- `moveToDeadLetter` 并发双跑可能产生重复死信行（败方 delete 0 行时）：低危数据重复，非状态机破坏，登记不立案。

---

## 3. 终止声明

本轮按深挖追加指令的 5 个方向（C1-001 失效面矩阵、C1-003 回调位点矩阵、发布安全专项、状态转移守卫矩阵、DB-内存一致性站点）全部完成收口：

1. **CAS 写失败不短路的站点矩阵**已穷尽（retry 2 处 saveRecord 位点、tcc 全局 4 个调用点 × 分支 7 个调用点全部定位归档）；
2. **retry 链全部 8 个 whenComplete 位点**逐一定性（C1-008 第 10 字段矩阵），tcc 链 12 个位点核验完毕；
3. **发布安全专项**零命中；
4. **状态转移守卫矩阵**（retry 8 写点 / tcc 16 写点）无第三类遗漏；
5. **DB-内存副本一致性**无新增未立案位点。

**C1 维度深挖终止。** 第 3 轮无剩余未核查盲区；C1-007 变体 A 的消除依赖 C1-001 修复，已在备注中声明承接关系。

---

## 4. 覆盖声明

- **序号接续**：本轮新发现 C1-007、C1-008、C1-009，接续第 1 轮 C1-001~006；累计 C1-001~009。
- **严重度汇总**：本轮 P2×3（C1-007 tcc 终态父事务下分支悬挂/跨终态 confirm；C1-008 retry 完成信号丢失新结构位点；C1-009 retry 首执行防双执行窗口）。第 1 轮 P1×3（C1-001/002/003）、P2×1（C1-004）、P3×2（C1-005/006）维持不变。
- **文件覆盖**：本轮在两模块 82 文件内新增/复核精读 12 个核心文件（RetryEngineImpl、RetryScannerImpl、RetryRecordStoreImpl、NopRetryConstants 链、TccEngine、TccTransaction、TccBranchTransaction、TccRunner、TccTransactionRegistry、TccRecordStore、TccStatus、TccRpcServiceInterceptor、TccGatewayInterceptor）+ 平台语义 5 处（IEntityDao、GenSqlHelper、FutureHelper、TccContext、NopRetryApiConstants）；其余文件沿用第 1 轮逐类清点结论，未发现与第 1 轮冲突的事实。
- **定向 grep**：发布安全（new Thread/监听器/this 逃逸）、whenComplete 全量（retry 8 / tcc 12）、恢复入口闭合性（getBranchRecordsAsync 调用链）——均留痕于正文。
- **盲审声明**：本轮仅读取任务书指派的第 1 轮文件与 grep 基线；未读取 ai-dev/ 其他目录及同审计目录其他维度文件。
