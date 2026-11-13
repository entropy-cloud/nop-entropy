# retry/tcc 补偿链韧性、回调环护栏与扫描上界契约（R4-10 批十四成员裁定）

> Status: active
> Last Reviewed: 2026-11-08（plan 2543 Phase 1 产出；draft review R1 后修订；本批唯一设计裁决权威）
> Source: plan 2543（`ai-dev/plans/2543-cra-phase8.50-r4-10-retry-tcc-compensation.md`）Current Baseline 14 成员表；发现事实源 = WI 1.6 审计目录 `ai-dev/audits/2026-10/2026-10-15-1400-deep-audit-nop-retry-nop-tcc-concurrency/`（merge-adjudication §3.1 判级 = 14 项全 P2）；行号 = live `3005ae8afa`（源码树与 plan 基线 `7d97f12b3e` 对 `nop-retry nop-tcc` 零 diff）逐方法亲读回读值（方法起始行），执行期漂移以 live 为准并登记于 plan 勘误
> 实现锚（方法级，均含方法起始行）：`nop-retry-engine` —— `impl/RetryEngineImpl`：handleExistingRecord :276、resolveInsertRaceWinner :295、isDuplicateKeyError :311、handleBlockStrategy :316、executeWithRetry :394、executeImmediateRetry :457、finishImmediateRetry :488、triggerCallback :802、moveToDeadLetter :855/:861、doStart :899（扫描处理器接线 :909-925）；`scanner/RetryScannerImpl`：setter :56-88、doScan :180-230；`store/RetryRecordStoreImpl`：newRecord :97、newAttempt :142、saveAttempt :175、fetchPendingRecords :230、findRecordByIdempotentId :311、replaceRecord :339、renewLease :349、getCurrentTime :506；`store/IRetryRecordStore` 同接口。`nop-tcc-core` —— `impl/TccEngine`：doStart :136、startLedgerCleanup :158、runLedgerCleanup :209、loadTransactionAsync :261、loadBranchTransactionsAsync :276、runTaskWithExitingTxnAsync :335、runTaskWithNewTxnAsync :387、runTaskWithNewTxn :424、runBranchTransactionAsync :443、checkExpiredTransactions :466、checkExpiredAsync :500、cleanCompletedTransactions :506；`impl/TccTransaction`：endAsync :61、doCancelAsync :100、doConfirmAsync :122、aggregateAndWriteFinalStatus :149、buildAuthoritativeViews :169；`impl/TccRunner`：runBranchTryAsync :40、runBranchConfirmAsync :72、runBranchCancelAsync :98、confirmAllAsync :133、isBranchCancellable :158、cancelAllAsync :164、aggregateCancelBranchStatus :178、aggregateConfirmBranchStatus :198；`impl/TccTransactionRegistry` :18-26；`impl/TccBranchTransaction`：beginConfirmAsync :92、beginCancelAsync :125、finishCancelAsync :141。`nop-tcc-dao` —— `store/TccRecordStore`：newTccRecord :104、saveTccRecordAsync :155、doUpdateTccStatus :204、saveBranchRecordAsync :258、doUpdateTccBranchStatus :271、fetchExpiredRecords :349、getCurrentTime :386、removeCompletedRecords(两参) :392、killExhaustedRecords :422、removeCompletedRecords(三参) :463。
> Related：**R2-12 批（plan 2515，completed）裁定①–⑨ 为本批前提**——本设计只引用不重建（§一 逐条核对）；R01 族级 SSOT = [ledger-retention-policy.md](ledger-retention-policy.md)；R1-01 绝缘层 = [periodic-task-insulation.md](periodic-task-insulation.md)；前批契约 = [retry-tcc-cas-compensation-and-ledger-hygiene.md](retry-tcc-cas-compensation-and-ledger-hygiene.md)

## 一、既有裁定引用核对节（零重开清单）

plan 2515 裁定①–⑨ 逐条核对，本批**零重开**：不改其产物、不回改其体裁、不弱化其钉族。

| 2515 裁定 | 结论摘要 | 本批关系 |
|---|---|---|
| ① C3-008 接缝（CAS 失败异常完成 + `future.cancel(true)` 封幽灵源） | 0 行/异常同型 failedStage；超时分支 cancel 在飞链 | **只消费**：W-04 补弃链 driving-epoch（2515 明文「弃链追踪本体归 R4-10」）；`cancel(true)` 与 CAS 传播零改动 |
| ② CAS 权威聚合（聚合读 DB 重载行 + afterCommit 完成配对） | 聚合输入 = store 重载分支行；future 提交后完成 | **只消费**：W-06 的「聚合前 DB 重载」已是 2515 产物，本批只补**聚合结算表严格化**（§3.1.6），重载机制零改写 |
| ③ 终态写前置（挂点 err==null 门控） | 驱动链异常完成不折叠终态 | **只消费**：W-07 的 short-circuit 证据保留（suppressed 通道）挂在挂点门控之外，门控本体零改 |
| ④ A2 执行令牌（续租 CAS + 败方中止 + attempt 超时上界 < 租约） | `renewLease` / `ERR_RETRY_LEASE_LOST` / 装配期 fail-fast | **只消费**：W-12 扫描上界不改续租协议；W-14 参数校验不触碰租约键语义 |
| ⑤ OVERWRITE 原子替换 + 代际取消 | `replaceRecord` @Transactional；进程内代际 + DB 行存在性 | **只消费并收窄**：W-01 只处理「原子包络之后仍以裸 UK 冲突暴露的输家」——2515 已登记的暴露面收窄接缝（C1-002 本体归本批） |
| ⑥ future 拓扑单一化 + 两层簿记隔离 + `allOf().join()` 保留 | 每条链 Throwable 兜底；簿记失败不改判业务 | **只消费**：W-05 手工 future 补 try/catch 沿用同一形态；W-11 per-record 隔离不改 join 拓扑 |
| ⑦ RUNNING attempt 对账 + 僵尸死信化 | 对账 STOPPED + max+1；解析失败死信化 | **只消费**：W-11 的 per-record 隔离是其**纵深防御第二层**（C7-014 明文定位），死信化本体零改写 |
| ⑧ tcc 清理接线 + 耗尽终态化 + 索引 | 周期链 + KILLED 终态化 + 有界批删 | **只消费**：W-13 把无界两参入口改为内部分页，复用其三参形态。**draft review R1-B1 勘误**：W-06 结算表必须把 `KILLED` 与 `CONFIRM_SUCCESS` 列为「已结算」，否则父事务永不进 `getFinishedStatus()`，反向摧毁裁定⑧ 的清理链（见 §3.1.6） |
| ⑨ namespace 扫描覆盖放宽 | fetch 去 namespace 硬过滤 | **零触碰** |

另：plan 2502 R1-07（账本保留统一批）——W-13 参数化沿用其族级 SSOT 纪律，零参数发明。2515 十三成员修复面与其钉族（`TestTccCasPropagation_C10_003` / `TestTccLedgerCleanup_C10_007` / `TestTccLedgerHygiene_C7_005` / `TestTccStoreCasConflict_C10_003` / `TestTccTransactionEndStates` / `TestRetryScannerInsulation` / `TestRetryLedgerRetention_C7_001`）**全列为零回退钉**（§4.2）。

## 二、背景与动机

R4-10 十四成员是 2515 批留下的四类残余面：

1. **补偿链证据与方向（W-01/W-04/W-05/W-06/W-07）**：OVERWRITE 原子包络已落地，但并发输家仍以裸 UK 冲突暴露、且对已被合法替换的 stale 行造死信；恢复超时弃链无 driving-epoch，苏醒后可与新链争写终态；`finishCancelAsync` 对 `response==null` 缺判空（三处 finish* 中唯一疏漏）且手工 future 对回调同步抛出不设防；批次内单分支同步抛中断整批（后续分支零驱动）；CANCELLING 标记落库失败即短路，记录滞留 TRYING 且无失败证据，恢复扫描按「无回滚证据 → confirm」把失败事务以确认方向释放预留资源。
2. **注册表身份（W-02）**：`instance()` get→new→set 非原子 + 叠放式 put/restore。
3. **守卫与恢复入口（W-03）**：分支创建守卫读永不刷新的内存副本；补偿期间新插入的分支不在入口快照内，无驱动入口。
4. **时钟、环护栏与批量边界（W-08/W-09/W-10/W-11/W-12/W-13/W-14）**：tcc 全链原始墙钟；回调策略成环无深度护栏（idempotentId 逐环追加 `_callback`，永不撞 UK，自维持无限副作用）；tcc 恢复页内串行 `60 × expireGap` 级无上界且 fetch 无 try/catch；retry 扫描 `stream().map()` 惰性流被单条同步异常中断（确定性饿死）；排水式单轮无上界；无界两参清理重载仍可达；扫描参数零校验（0 值静默空转、负间隔紧循环）。

本文档一次裁定 A–E 五组，使 Phase 2 实施只有一种合法形态。

## 三、核心设计契约

### 3.1 裁定 A：补偿链韧性域（W-01/W-02/W-03/W-04/W-05/W-06/W-07）

#### 3.1.1 W-01：OVERWRITE 输家分流（唯一形态 = 异常类型 + 重读身份二元判据）

`handleBlockStrategy` OVERWRITE 分支的 `recordStore.replaceRecord(existingRecord, newRecord)`（`RetryEngineImpl:337`）失败后**按「是否为 UK 冲突」与「重读行 sid 身份」二元分流**。catch 内按 idempotentId 重读一次（复用 `findRecordByIdempotentId`）。

| 判据 | 处置 | 死信 |
|---|---|---|
| `isDuplicateKeyError(e)` **且** 重读行非空且 `sid != existingRecord.getSid()` | 并发 OVERWRITE 输家：键已被对端合法替换 → WARN 留痕 + 返回 `ApiResponse.success(null)`（键位上已有新记录的 OVERWRITE 语义结果已由赢家满足） | **不造** |
| `isDuplicateKeyError(e)` **且** 重读行非空且 `sid == existingRecord.getSid()` | 事务已回滚、旧记录真实存活 → `moveToDeadLetter(existingRecord, "OVERWRITE_REPLACE_FAILED", ...)` + `failedStage(ERR_RETRY_RECORD_OVERWRITTEN)`（2515 形态保持） | **造死信** |
| `isDuplicateKeyError(e)` **且** 重读行 == null | 行在读取前被并发删除 → WARN + `failedStage(ERR_RETRY_OVERWRITE_RACE_LOST)` | 不造 |
| **非 UK 异常**（瞬时 DB 故障/锁超时/序列化失败） | 事务已整体回滚、旧记录**确定存活**，但「是否存在并发赢家」不可判定 → **只做非破坏性处理**：WARN + `failedStage(ERR_RETRY_RECORD_OVERWRITTEN)`，**不造死信、不删除旧行** | 不造 |

- **draft review R1-M8 采纳**：死信是破坏性动作（`moveToDeadLetter` 先写死信行再 `deleteEntityDirectly`，`RetryRecordStoreImpl:390`），只有在「UK 冲突 ⟹ 冲突行必已提交可见」这一强信号下才可执行；非 UK 异常时对 stale 行造死信会把「旧行即将被赢家替换」与「旧行真实失败」混同。故非 UK 分支退为纯失败传播（调用方可重试），不以猜测驱动删除。
- 新增错误码 `ERR_RETRY_OVERWRITE_RACE_LOST`（`nop.err.retry.overwrite-race-lost`，`.param(recordId)`）；`ERR_RETRY_RECORD_OVERWRITTEN` 语义不变。
- **不做**：不重试 replace、不重入 `handleExistingRecord`（与赢家形成 ping-pong）、不引入行锁（ORM 无此原语，2515 已裁定拒绝 DB 级租约）。原子包络、`bumpGeneration`、代际短路全部零改动。

#### 3.1.2 W-02：注册表原子身份 + 条件恢复（唯一形态）

- **锁载体 = 属性 Map 本体**（draft review R1-M5 采纳）：`instance()` 的原子创建段锁在 `context.getAttrs()` 上做双重检查，**不锁 `context` 对象**——`DelegateContext`/租户包装上下文（`DelegateContext.getAttrs()` 委托至同一底层 `BaseContext.attributes`）下，两个线程可持有不同 wrapper 对象而共享同一 Map，锁 wrapper 对象不互斥；锁 Map 才互斥。实现形态：

  ```
  Map<String,Object> attrs = context.getAttrs();
  TccTransactionRegistry registry = (TccTransactionRegistry) attrs.get(CONTEXT_KEY);
  if (registry == null) {
      synchronized (attrs) {
          registry = (TccTransactionRegistry) attrs.get(CONTEXT_KEY);
          if (registry == null) {
              registry = new TccTransactionRegistry();
              context.setAttribute(CONTEXT_KEY, registry);
          }
      }
  }
  return registry;
  ```

  不用 `attrs.computeIfAbsent(...)`：`IContext.getAttrs()` 的契约未保证返回并发 Map，`synchronized` + 双重检查对任意 `Map` 实现安全。
- **测试 seam（draft review R1-M5 采纳）**：新增包级（package-private）`static TccTransactionRegistry instance(IContext context)`，`instance()` 取上下文后委托它。红②可确定性构造：把同一 `BaseContext` 实例分发给 N 个线程（`ContextProvider` 是 ThreadLocal，直接用公共 `instance()` 得到的是 N 个独立上下文，**无法**构造竞态——此 seam 是钉可构造性的必要条件）。
- **条件恢复（消除叠放互踩）**：档位式 `put(old)` 恢复改为**仅当我方事务仍是当前值时才恢复**：`if (registry.get(txnGroup) == txn) registry.put(txnGroup, old);`。四处 put/restore（`runTaskWithExitingTxnAsync:335` / `runTaskWithNewTxnAsync:387` / `runTaskWithNewTxn:424`；`runTaskWithExitingTxn` 的 synchronous 版同改）统一改用该条件恢复。
- **单线程语义零变化**：嵌套（外 A / 内 B）在条件恢复下仍严格后进先出（内层完成 `get()==B` → 恢复 A；外层完成 `get()==A` → 恢复 old）。红②须同时固定「同上下文并发单例」与「嵌套恢复仍正确」两面。
- **不动 api-core**：`IContext` 无原子 compute 原语，新增即 `nop-api-core` plan-first，本批明确拒绝。
- **声明面**：owner doc 声明「注册表作用域 = IContext；同上下文并发进入被防御性支持但非推荐调用形态」。

#### 3.1.3 W-03：分支创建守卫 DB 复核 + 补偿期孤儿分支收敛（双腿）

**腿 1 — 创建守卫改 DB 复核（唯一形态 = fail-close）**：
`runBranchTransactionAsync`（`TccEngine:443`）当前是同步方法返回 `FutureHelper.reject(...)` 或直接构造分支。改为**异步复核链**：内存守卫（`txn.getTccStatus() != TRYING`）保留为事前快路径；随后 `return repository.getTccRecordAsync(txnGroup, txnId).thenCompose(record -> ...)`：

- `record == null`（行已删除）→ `FutureHelper.reject(ERR_TCC_MISSING_TRANSACTION_RECORD)`（复用既有错误码）。
- `record.getTccStatus() != TRYING` → `FutureHelper.reject(ERR_TCC_TRANSACTION_NOT_ALLOW_START_BRANCH)`（复用既有错误码与参数）。
- 复核读**异常**（DB 故障）→ `FutureHelper.reject(ERR_TCC_BRANCH_GUARD_CHECK_FAILED)`（新错误码，落在 `nop-tcc-core` 的 `TccCoreErrors`，**不进 `nop-tcc-api`**）。
- 直行路径：`newBranchRecord` + `TccRunner.runBranchTryAsync(...)` 移入 `thenCompose` 内。

**fail-close 理由**：放行 = 可能向已回滚事务写入预留资源（不可逆）；拒绝 = 调用方在父事务不可用的明确信号下失败。代价 = 每分支 +1 次主键读（`getEntityById`，包络可接受）；不做缓存（缓存即重新引入陈旧副本）。

**腿 2 — 补偿期孤儿分支收敛（唯一形态 = endAsync 末段收敛重读，最多 3 轮）**：
不新增 store/api 查询方法（Non-Goal：不改 `nop-tcc-api`）。收敛点在 `TccTransaction.aggregateAndWriteFinalStatus`（`TccTransaction:149`）——该处已按裁定② 重载 DB 分支行，本批把它从「只用于聚合」升级为「同时用于覆盖面收敛」：

1. 重载行 `rows` 与本次驱动的 `branchTxns` 按 branchId 对齐，求差集 `orphans = rows - branchTxns`（入口快照之外的分支）。
2. `orphans` 非空 → 以 `TccBranchTransaction(row, engine)` 构造视图，**按方向调用既有的隔离批量助手**驱动一轮：
   - cancel 方向 → `TccRunner.cancelAllAsync(orphanViews, timeout, invoker)`
   - confirm 方向 → `TccRunner.confirmAllAsync(orphanViews, invoker)`
   （复用 W-06 落地后的「逐分支 try/catch + 汇入 waitAll」形态——**不另写**一个无隔离的收敛循环，draft review R1-M3(a) 采纳。）
3. 驱动后**重读**分支行并重新聚合；最多 3 轮。
4. 3 轮后仍有「本方向可驱动且未结算」的分支行 → **不折叠终态**，`failedStage(ERR_TCC_BRANCH_CONVERGENCE_EXHAUSTED)`（新错误码）——全局保持 CANCELLING/CONFIRMING 交恢复扫描；下次扫描重入 `endAsync` 时再次走同一路径。

**驱动前必须按方向筛（draft review R1-M3(b) 采纳）**——`cancelAllAsync`/`confirmAllAsync` 内部已各含筛条件（`isBranchCancellable` / null+CONFIRM_SUCCESS 跳过），故步骤 2 直接复用即自动获得筛选，不会对 `CONFIRM_SUCCESS` 调 `beginCancelAsync`（那会抛 `ERR_TCC_INVALID_CANCEL_BRANCH_STATUS`）。此即「复用批量助手」而非「直接调 runBranch*Async」的原因。

**残余窗（诚实声明，draft review R1-M3(c) 采纳）**：腿 1 的 DB 复核与分支插入之间仍有 TOCTOU——若「腿 1 读到 TRYING → 本链 CAS 到 CANCELLING/CONFIRMING → 该分支插入提交」交错发生，该分支在步骤 1 的最终重读中仍可见（重读发生在其提交之后则被收敛；发生在其提交之前则不可见）。故：

- **必然消除**：变体 B 的**主流形态**（补偿期间新插入且已提交的分支在此前无任何驱动入口）——收敛步骤 2-3 覆盖。
- **残余**：分支 ISN'T IN 最终重读快照且在终态 CAS 之后才提交。要彻底闭合需 DB 级约束（父状态外键/事务级校验），属 ORM 扩面，本批拒绝。该残余登记为 `watch-only residual`（plan Deferred 节 + owner doc 声明：分支创建的最终一致性由腿 1 的 DB 复核兜住—分支插入后本链立即 CAS 使其不可再插）。

#### 3.1.4 W-04：弃链 driving-epoch（唯一形态）

`TccEngine` 维护进程内驱动纪元 `ConcurrentHashMap<String txnId, AtomicLong> drivingEpochs`。

- `protected long beginDriving(String txnId)`：`computeIfAbsent(...).incrementAndGet()` 并返回新纪元。
- 恢复链取纪元：`checkExpiredAsync`（`TccEngine:500`）在构造 `TccTransaction` 时传入当前纪元（新增模块内包级构造参数 `long drivingEpoch`，`0` = 非纪元管理链——存活调用方链与 `loadTransactionAsync` 路径传 0，行为零变化）。
- 超时分支：先 `future.cancel(true)`（2515 产物保持），再 `beginDriving(txnId)` 推进纪元 → 旧链令牌即刻失效。
- 写点闸：`aggregateAndWriteFinalStatus` 前置校验 `drivingEpoch > 0 && !isActiveDriving(txnId, drivingEpoch)` → WARN + `failedStage(ERR_TCC_DRIVING_ABANDONED)`（新错误码），**不折叠终态、不回写**。
- `protected boolean isActiveDriving(String txnId, long epoch)` 语义（draft review R1-M6 采纳，显式定义）：
  - 条目存在 → `epoch == 当前纪元`；
  - **条目不存在 → inactive（返回 false）**。安全性依据（写入 owner doc 的不变式）：条目只在「终态写成功后 `endDriving` 移除」时消失；而终态父事务不再被 `fetchExpiredRecords` 的 `status < CONFIRM_SUCCESS` 谓词拾取，且 TCC txnId 一次性（同一 txnId 不会复活），故条目消失后**不会**再有合法恢复链携带该 txnId 的纪元发起写点校验；任何携带纪元到达写点的链必然是条目存在期的弃链。
- **残余（诚实声明）**：闸在**写点**，不阻止弃链已发起的分支补偿 RPC。由 2515 裁定①（CAS 失败即短路）+ TCC 参与方幂等性承接；owner doc 声明该残余。

#### 3.1.5 W-05：null response 判空 + 手工 future 全兜底（唯一形态）

- `TccBranchTransaction.finishCancelAsync`（:141）：`Boolean.TRUE.equals(response.getBizFatal())` 前补 `response != null &&`——对齐 `finishTryAsync`/`finishConfirmAsync` 的判空疏漏；`response == null && ex == null` 落到「非成功响应」末支（按 `timeout ? TIMEOUT_FAILED : CANCEL_FAILED` 记录，可重试），不再 NPE。
- `TccRunner` 三个 `runBranch*Async`（:40/:72/:98）：**把 `begin*Async()` 调用本身与 lambda 体一起**纳入 try/catch（draft review R1-M1 采纳——`begin*Async()` 在 `thenCompose` lambda **之外**调用，只包 lambda 捕不到它）：

  ```
  CompletableFuture<X> future = new CompletableFuture<>();
  try {
      branchTxn.beginConfirmAsync().thenCompose(arg -> {
          try { ...原有点火与 whenComplete... } catch (Throwable t) { future.completeExceptionally(t); }
          return future;   // 原 lambda 语义
      }).exceptionally(t -> { future.completeExceptionally(t); return null; });
  } catch (Throwable t) {
      future.completeExceptionally(t);
  }
  return future;
  ```

  即：**外层 try** 捕 `begin*Async()` 同步抛；**lambda 内 try** 捕 finish/normalizer 同步抛；**`exceptionally` 尾** 捕 `thenCompose` stage 的异步异常。三者共同保证手工 future 必有终态。
- 不以 `FutureHelper.whenCompleteAsync` 作为 Throwable 正例引用——其内部只 catch `Exception`（draft review R1-M1 勘误），本批统一用 `catch (Throwable)`。

#### 3.1.6 W-06：逐分支隔离 + 聚合结算表（唯一形态）

**逐分支隔离**：`confirmAllAsync`（:133）/`cancelAllAsync`（:164）循环体内对 `runBranch*Async(...)` 调用包 `try/catch(Throwable)`：失败分支 ERROR 留痕（含 txnId/branchId/status，`branchTxn` 在作用域内）+ 把 `CompletableFuture.failedStage(t)` 加入 futures（汇入 `waitAll`，不中断后续分支）。循环前筛条件保持不变（confirm：`null` 跳过 + `CONFIRM_SUCCESS` 跳过；cancel：`isBranchCancellable` 筛）。**不改 `begin*Async` 的抛错语义**（单点改在循环层，一处覆盖三个调用点）。

**聚合结算表（draft review R1-B1 采纳——废原「其余一律失败」规则）**：
「成功」当且仅当**每个分支在本方向上都已结算**；「已结算」= 已到达该方向的终态 **或** 该方向本就不驱动它（被上述筛条件跳过）。逐状态表：

| 分支 DB 状态 | cancel 方向 | confirm 方向 |
|---|---|---|
| `CANCEL_SUCCESS` / `TIMEOUT_SUCCESS` / `BIZ_CANCEL_FAILED` | 已结算（成功侧） | 不可 confirm ⟹ **抛 `ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS`**（2515 语义，零回退） |
| `TRY_FAILED` | 已结算（try 未成功，无需 cancel） | 同上抛（`isCancelled()` 含 TRY_FAILED） |
| `CONFIRM_SUCCESS` | 已结算（本就不需 cancel；无 confirmMethod 的自动确认分支常态） | 已结算（成功侧） |
| `KILLED` | 已结算（终态，不受本链控制；`isBranchCancellable` 亦排除） | 已结算（终态） |
| `CANCEL_FAILED` / `TIMEOUT_FAILED` | 未成功 ⟹ `CANCEL_FAILED` | 抛 `ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS`（`TIMEOUT_FAILED` 不属 `isCancelled()`，见下行注） |
| `CONFIRM_FAILED` | ⟹ `CANCEL_FAILED`（补偿方向已定，不应 cancel；保守失败） | 未成功 ⟹ `CONFIRM_FAILED` |
| `TRY_SUCCESS` / `TRYING` / `TRY_UNKNOWN` / `CANCELLING` / `CONFIRMING` / `CREATED` / `BEFORE_TIMEOUT` | 可驱动但未结算 ⟹ `CANCEL_FAILED` | 可驱动但未结算 ⟹ `CONFIRM_FAILED` |
| `null`（脏数据） | WARN 跳过（不参与） | WARN 跳过（不参与） |

- 注：`TIMEOUT_FAILED` 不在 `isCancelled()` 中（`TccStatus:94-99`），故 confirm 方向对它是「非 cancelled 的异常态」→ 按 `CONFIRM_FAILED` 处理（而非抛）；表内「抛」仅限 `isCancelled()` 为真者，与 2515 `aggregateConfirmBranchStatus` 现行 `throw` 分支严格对齐。**实施口径**：保留 2515 的 `isCancelled() → throw`，其后新增「未到本方向终态 → 本方向 FAILED」，`CONFIRM_FAILED`/`CANCEL_FAILED`/`TIMEOUT_FAILED` 的既有优先级（`aggregateCancelBranchStatus:178-196` 的 BIZ/CANCEL/TIMEOUT 依次返回）零改写、只在其后接新规则。
- **为何不破坏 2515 裁定⑧（B1 核心）**：`KILLED` 与 `CONFIRM_SUCCESS` 被显式列为已结算 ⟹ 含此类分支的父事务仍能折叠到 `CONFIRM_SUCCESS`/`CANCEL_SUCCESS` ⟹ 进 `getFinishedStatus()` ⟹ 清理链零损伤。
- 现有 `TestTccRunner` 八条聚合断言全部保持（全成功/含 CONFIRM_FAILED/含 cancelled 抛/BIZ/CANCEL/TIMEOUT/null 跳过）；**新增**直接调 `TccRunner.aggregate*` 的结算表断言。

#### 3.1.7 W-07：失败证据与恢复方向（三件套）

1. **CANCELLING/CONFIRMING 标记落库有限重试**：`doCancelAsync`（:100）/`doConfirmAsync`（:122）的首跳状态迁移经模块内 `retryStatusWrite(supplier)` 助手：**仅对非 `ERR_TCC_STATUS_CAS_CONFLICT` 错误**重试，最多 3 次（CAS 冲突是语义冲突，重试无意义）。三次仍失败 → ERROR 留痕 + `failedStage`（不吞）。
2. **证据保留（suppressed）**：`TccEngine.runTaskWithNewTxnAsync`（:387）的 catch 分支由 `.thenCompose(v2 -> failedFuture(e))` 改为
   `txn.endAsync(false, null, e).handle((v2, err2) -> { if (err2 != null) { e.addSuppressed(err2); LOG.error("nop.tcc.end-compensate-fail", err2); } return null; }).thenCompose(v -> failedFuture(e))`
   ——业务异常优先传播，补偿链异常以 suppressed 保留（`handle` 吞补偿异常后由末段重抛业务异常；原 `thenCompose` 在源异常时整段跳过，是 2515 复核已证伪「业务异常丢失」的反面形态，本项只补 suppressed 证据面）。
3. **恢复方向显式决策（唯一形态 = 超时恢复默认取消）**：`endAsync`（:61）判定顺序中，**`timeout == true` 且 `curStatus` 处于未定向态（`TRYING`/`TRY_SUCCESS`/`CREATED`/`null`）时路由 `doCancelAsync`**；既有 CONFIRMING/CONFIRM_FAILED → confirm、CANCELLING/CANCEL_FAILED → cancel 两条对称续跑路由（2515/plan 2388 产物）**前置且零改动**；调用方驱动的 `endAsync(false, ...)` 保持既有语义。
   - **与既有钉的关系（draft review R1 问询面）**：`TestTccTransactionEndStates` 不调用 `endAsync(true, ...)`（已核），故零回退；`TestTccEngineTransactions`/`TestTccCasPropagation_C10_003` 中经由 `endAsync(true,...)` 的用例若存在，须在 Phase 2 逐条核对方向期望——**这是本批唯一预期的既有测试语义面变更，须在实施期登记**（不是弱化断言，而是按新契约校正期望值；若发现断言承载的是「超时恢复 confirm」这一旧缺省语义，则按 E-2543 勘误登记并改成 cancel 期望）。
   - 理由：超时恢复的语义是「无人认领的事务必须回滚」——确认一条没跑完的业务链是最严重的错误方向；记录仍为 TRYING 时不存在「confirm 已经开始」的证据，不违反 TCC「confirm 开始后不可回滚」。
   - 与 §3.1.6 结算表协同：CANCELLING 写失败致分支未驱动时，父事务保持 TRYING，下一轮恢复走 cancel 方向——失败事务以 cancel 方向收敛，方向翻转不可达。

### 3.2 裁定 B：时钟与期限域（W-08）

**唯一时空形态 = 两个显式 seam + 方向分工**（draft review R1-M7 采纳：原设计只命名了读侧 seam，写侧靠「覆写 fake dao」不可轻构造）：

- `TccRecordStore` 新增两个可覆写 seam，**全部 9 个时钟调用点**改走它们（`newTccRecord:104`、`saveTccRecordAsync:155`、`doUpdateTccStatus:204` 的 endTime、`saveBranchRecordAsync:258` 的 beginTime/expireTime、`doUpdateTccBranchStatus:271` 的 endTime、`fetchExpiredRecords:349` 的 minTime/nextCheckTime、`getCurrentTime:386`、`removeCompletedRecords` 两参/三参的 minTime、`killExhaustedRecords:422` 的 now）：
  - `protected long nowForRead(IOrmEntityDao<NopTccRecord> dao)` = `dao.getDbEstimatedClock().getMinCurrentTimeMillis()` —— **过期判定/清理窗口下界**（只有「确定已过期」才触发恢复，与 `expireTime < minTime` 谓词同向）。
  - `protected long nowForWrite(IOrmEntityDao<NopTccRecord> dao)` = `dao.getDbEstimatedClock().getMaxCurrentTimeMillis()` —— **所有落库时间戳**（beginTime/expireTime/endTime/nextCheckTime）。写入取上界，使 expireTime 偏后（保守，绝不提前恢复）。
- **`fetchExpiredRecords` 的 `nextCheckTime` 是写入，用 `nowForWrite`**（原草案把它隐含在 min 侧，draft review R1-M7 指为混源）：`minTime = nowForRead() - expireGap`；`nextCheckTime = nowForWrite() + checkInterval`。
- **净效果**：任意两库时钟偏差与本地墙钟偏差都不会造成「写入早 → 立即过期」或「写入晚 → 永不判过期」的夹逼错误。
- **DB 时钟回退语义声明**：`DbEstimatedClock` 拉取失败时回退本地单调估算（其 `fetch()` 内部带缓存与本地差量），故 NTP 步进只影响拉取间隔内的估算窗口，不再直接改写过期判定基准。部署要求（DB 与宿主时钟同步精度）由 owner doc 时钟节声明。
- **不做**：不引入 `IClock` 平台注入（R4-32 跨单元批）；不新增配置键；不改 orm 模型。

### 3.3 裁定 C：扫描与批量上界域（W-10/W-11/W-12/W-13/W-14）

#### 3.3.1 W-10：tcc 恢复轮双上界（唯一形态 = 总预算 + 连续超时熔断，不并行）

- 保持页内串行驱动（**不并行化**：并行化同时改变参与方 RPC 并发面与既有失败语义）。
- **单轮总时长预算**（draft review R1-M2 采纳——仅靠连续超时熔断无法约束「慢但成功」路径）：`checkExpiredTransactions`（:466）在**每条记录开始前**检查本轮已耗时；超过 `nop.tcc.recovery.max-round-duration-ms|60000` → INFO 留痕（已处理条数）+ 结束本轮（`break` 外层 while）。单轮耗时有硬上界（含慢成功路径）。
- **连续超时熔断**：`consecutiveTimeouts` 计数；单条 TimeoutException 时 `++`（并保持 2515 的 `future.cancel(true)` 在飞链取消），成功一条清零；达到 `nop.tcc.recovery.max-consecutive-timeouts|3` → ERROR 留痕 + 结束本轮。
- `repository.fetchExpiredRecords(...)` 包 `try/catch(Exception)`：ERROR 留痕后结束本轮，不再让一次 DB 异常静默终止恢复节律。
- 观测：`recoveryTimeoutCount` / `recoveryCircuitBreakCount`（AtomicLong + getter）——「熔断已触发」必须可观测（否则重犯 C10-004 保护力缺口）。
- 测试缝：`checkExpiredAsync` 由 `private` 提升为 `protected`（仅可见性，无签名变化），使超时/耗时/条数可在单测确定性驱动；耗时判定复用 W-12 同款可覆写 `protected long currentTimeMillis()`（本类新增，缺省 `CoreMetrics.currentTimeMillis()`）。
- **C10-004 口径（draft review R1-m5 修正）**：本批交付 = 「单轮时长有上界 + 连续超时熔断 + 熔断可观测」；`checkExpiredTransactions` 现零生产调用方（C6-005 dormant，G19 批外），故 closure 记录「保护力已就位但无生产接线」，**不**记为端到端保护已交付。

#### 3.3.2 W-11：retry 扫描 per-record 隔离（唯一形态 = 显式循环 + 单条兜底）

`RetryEngineImpl.doStart`（:899）的扫描处理器由 `records.stream().map(...).toArray()`（:909-925）改为**显式 for 循环**：

```
for (NopRetryRecord record : records) {
    try {
        futures.add(executeRetryFromScanner(record, null)
                .whenComplete((resp, ex) -> { if (ex != null) LOG.error("nop.retry.scanner.execute-failed:recordId={}", record.getSid(), ex); })
                .toCompletableFuture());
    } catch (Throwable t) {
        scannerDispatchFailureCount.incrementAndGet();
        LOG.error("nop.retry.scanner.record-dispatch-failed:recordId={}", record.getSid(), t);
    }
}
CompletableFuture.allOf(futures).join();
```

- 语义：单条同步抛**不再中断本批其余记录**（C7-014 的「确定性饿死」：第 k 条抛出时第 k+1..N 条必须仍被派发）；被跳过记录 ERROR + 计数显式可见（**非静默**），其租约在下一租约周期到期后重新拾取。
- 剩余同步抛点 `executeRetryFromScanner` 尾部的 `loadPolicyOrDefault`/`parseMap` 已在 2515 裁定⑦ 包 try/catch 并死信化——本项是其纵深第二层，不改第一层。`join()` 拓扑零改动（2515 裁定⑥）。

#### 3.3.3 W-12：单轮上界（唯一形态 = 批次数与时长双上界，先到先断）

新增配置键（`nop.retry.scanner.*`，缺省值一次定夺；各键以 `IConfigReference` 常量落 `RetryEngineConfigs` 或本类 `@InjectValue` setter，与既有键同形态）：

| 键 | 缺省 | 语义 |
|---|---|---|
| `nop.retry.scanner.max-batches-per-round` | 10 | 单次 `doScan` 最多处理的**非空批**数（见下口径）；达到即结束本轮 |
| `nop.retry.scanner.max-round-duration-ms` | 60000 | 单次 `doScan` 时长上界；**每批开始前**检查，超出即结束本轮 |

- **「批」口径（draft review R1-m3 采纳）**：计数 = 实际进入 `processor.accept(lockedRecords)` 的**非空批**次数；`lockedRecords.isEmpty()` 的 `continue` 轮**不**消耗预算（否则锁竞争的 store 会静默烧完预算）。
- 时长判定取**可注入 seam**：`RetryScannerImpl` 新增 `protected long currentTimeMillis()`（缺省 `CoreMetrics.currentTimeMillis()`），单测子类覆写即可确定性驱动。
- 结束本轮原因（批数/时长/积压排空）以 INFO 留痕（含 batches/locked 计数），`finally` 照旧刷新 `lastScanDurationMillis`/`scanCount`。不打断**在途批**（打断会破坏 2515 裁定⑥ 的 future 终态完备前提）。

#### 3.3.4 W-13：无界两参清理重载（唯一形态 = 保留签名 + 批内分页 + 传播显式）

- `removeCompletedRecords(long retentionTime, boolean onlyCompleted)`（两参，`TccRecordStore:392`）**签名保留**（零 api 变更），实现改为：内部 `while` 循环调用**私有非注解**批助手 `doRemoveCompletedBatch(retentionTime, onlyCompleted, batchSize)`（把现三参方法体抽成该私有助手，三参方法改为 `@Transactional` 包一层调用它），直到某批删除行数为 0；每批 LOG.info 进度（累计 deleted）。批大小取 `nop.tcc.ledger.delete-batch-size`。
- **事务传播决策（draft review R1-M4 采纳，显式裁定）**：两参方法**保留自身 `@Transactional`**，故整个循环在**单一事务**内（不是每批一事务）。理由：(a) 两参形态是公共契约（`ITccRecordStore`），调用方期望「一次调用要么全删要么全不删」的原子性——改为每批独立事务会静默改变契约；(b) 自调用不会绕事务拦截器**因为循环体是私有助手而非 `@Transactional` 方法**（2515 记录的 `replaceRecord` 自调用陷阱在此不成立）；(c) 缺陷本体是「`findAllByQuery` 无 limit 全量物化 + 巨型 IN」，分页后每批只物化 `batchSize` 行，内存包络已闭合；(d) 生产清理路径走三参形态（`runLedgerCleanup:209`），单事务长锁面不在生产路径上。
- javadoc 修订：两参标注「内部按批分页，单批物化上限 = `nop.tcc.ledger.delete-batch-size`；整个调用仍是单一事务」。
- **CL-005（C8-003 同位点主处置）随本项关闭**：清理端点不再有「无 limit 的 `findAllByQuery` 全量物化 → 巨型 IN」形态。

#### 3.3.5 W-14：扫描参数 fail-fast（唯一形态 = setter 校验 + store 防御）

- `RetryScannerImpl` 三个 `@InjectValue` setter 加校验，越界即抛 `ERR_RETRY_SCANNER_CONFIG_INVALID`（新错误码，`.param(configKey, configValue)`），**装配期 fail-fast**：
  - `batch-size ∈ [1, 1000]`（上限与单批实体物化包络对齐；缺省 100 合法）
  - `scan-interval-ms >= 100`（「紧循环」与「正常扫描」分界；负值/0 一律拒绝）
  - `retrying-timeout-ms > 0`
- `RetryRecordStoreImpl.fetchPendingRecords`（:230）补防御：`limit <= 0` 抛 `ERR_RETRY_SCANNER_CONFIG_INVALID`（不再把 0/-1 透传给 `query.setLimit` 造成静默空转或方言 SQL 错误）。
- **存量升级注记**：既有部署若配置 0/负值/超上限值，升级后**启动失败**（fail-fast 而非静默降级）——owner doc 参数边界节与升级注记必须点名（对齐 plan 2541 throttle 校验先例）。

### 3.4 裁定 D：回调环护栏域（W-09）

- **唯一形态 = 运行期回调深度护栏（引擎侧，不依赖 policy-save hook）**：
  - 深度定义（draft review R1-m2 采纳——只计**生成的后缀**，避免业务 idempotentId 恰好含 `_callback` 的假阳性）：`depth(record) = 对 record.getIdempotentId() 用正则 `(_callback)+$` 匹配后统计重复次数`。
  - 护栏点：`triggerCallback`（:802）在构造 `callbackTask` **之前**算 `depth`；`depth >= maxCallbackDepth` → `LOG.error("nop.retry.callback-depth-exceeded:recordId={},depth={},maxDepth={}")` + `callbackDepthExceededCount++` + **直接 return**（不创建回调记录、不写死信、不改主记录结论——主业务结果已定，回调只是旁路）。
  - 缺省 `nop.retry.callback.max-depth|3`；`<= 0` = 不限制（显式 opt-out，owner doc 声明）。
  - 环检测充分性：A→A 或 A→B→A 的每一环都使 `idempotentId` 增长一次 `_callback` 后缀，故深度上界即环长上界；自环在 depth=1 起计，maxDepth=3 容许两跳后阻断，终结「自维持无限副作用 + 无界记录增长」。
  - **不做**装载期链环校验（需 `nop-retry-service` BizModel 的 policy-save hook，不在本批 Scope）；不改写 idempotentId 键形状（保持 `_callback` 语义与既有 UK 兼容）。

### 3.5 裁定 E：新增配置键、错误码与观测项一览（零二源）

| 项 | 值/形态 | 落地位置 | 裁定 |
|---|---|---|---|
| `nop.tcc.recovery.max-consecutive-timeouts` | 3 | `TccEngineConfigs`（`IConfigReference`） | W-10 |
| `nop.tcc.recovery.max-round-duration-ms` | 60000 | `TccEngineConfigs` | W-10 |
| `nop.retry.scanner.max-batches-per-round` | 10 | `RetryScannerImpl` `@InjectValue` setter + getter | W-12 |
| `nop.retry.scanner.max-round-duration-ms` | 60000 | 同上 | W-12 |
| `nop.retry.callback.max-depth` | 3（<=0 = 不限制） | `RetryEngineImpl` `@InjectValue` setter + getter | W-09 |
| `nop.retry.scanner.batch-size` 合法域 | [1, 1000] | setter 校验 | W-14 |
| `nop.retry.scanner.scan-interval-ms` 合法域 | >= 100 | setter 校验 | W-14 |
| `ERR_RETRY_OVERWRITE_RACE_LOST` | `nop.err.retry.overwrite-race-lost` | `nop-retry-engine` `NopRetryErrors` | W-01 |
| `ERR_RETRY_SCANNER_CONFIG_INVALID` | `nop.err.retry.scanner-config-invalid` | `NopRetryErrors`（scanner 类与 engine 类同包；若分处两包则各自定义常量后引用，禁复制错误码串） | W-14 |
| `ERR_TCC_DRIVING_ABANDONED` | `nop.err.tcc.driving-abandoned` | `nop-tcc-core` `TccCoreErrors` | W-04 |
| `ERR_TCC_BRANCH_GUARD_CHECK_FAILED` | `nop.err.tcc.branch-guard-check-failed` | `TccCoreErrors` | W-03 |
| `ERR_TCC_BRANCH_CONVERGENCE_EXHAUSTED` | `nop.err.tcc.branch-convergence-exhausted` | `TccCoreErrors` | W-03 |
| `TccEngine.recoveryTimeoutCount` / `recoveryCircuitBreakCount` | AtomicLong + getter | `TccEngine` | W-10 |
| `RetryEngineImpl.scannerDispatchFailureCount` / `callbackDepthExceededCount` | AtomicLong + getter | `RetryEngineImpl` | W-11/W-09 |

## 四、裁定 F：红钉总表与零回退钉

### 4.1 红①–红⑭（W-01…W-14 一一对应；缺陷态红 → 修复后绿）

| 红 | 工单 | 载体与可构造 seam | 缺陷态红判据（**对 2515 缓解后基线**） | 修复后绿判据 |
|---|---|---|---|---|
| ① | W-01 | 确定性分支腿：驱动 catch 分支——(a) UK 冲突 + 重读返回对端新行（sid 不同）；(b) UK 冲突 + 重读同行；(c) 非 UK 异常 | (a) 对 stale 行造死信（死信表新增）；(c) 非 UK 异常亦造死信 | (a) 成功且死信零新增；(b) 死信 + `ERR_RETRY_RECORD_OVERWRITTEN`；(c) 死信零新增 + `ERR_RETRY_RECORD_OVERWRITTEN` |
| ② | W-02 | `instance(IContext)` 包级 seam：同一 `BaseContext` 分发给 N 线程（barrier 同步）+ 嵌套 put/restore 序 | N 线程拿到多个不同实例；条件恢复缺失致对端注册被清 | 全部线程同一实例；嵌套恢复外→内正确；覆盖后不误清 |
| ③ | W-03 | 腿 1：fake store 父记录状态 = 终态 / null / 读抛；腿 2：父事务补偿期间新插分支（fake store 在 `getBranchRecordsAsync` 第二次调用返回孤儿行） | 腿 1：终态父下 branch try 仍发起；腿 2：孤儿分支零驱动、终态仍折叠成功 | 腿 1：三种 reject 各自命中（`NOT_ALLOW_START_BRANCH`/`MISSING_TRANSACTION_RECORD`/`GUARD_CHECK_FAILED`）；腿 2：孤儿分支被驱动、聚合基于收敛后行集 |
| ④ | W-04 | 恢复链超时 → 推进纪元 → 老链写终态（`checkExpiredAsync` protected 直调 + 纪元 seam） | 老链终态写成功（与新链争写） | 老链写被拒（`ERR_TCC_DRIVING_ABANDONED`）、新链权威；条目缺失语义 = inactive |
| ⑤ | W-05 | (a) `response==null && ex==null` 直调 `finishCancelAsync`；(b) 覆写 `beginCancelAsync` 同步抛；(c) invoker 完成回调中抛 | (a) NPE；(b)(c) 手工 future 永不完成 | (a) 落 `CANCEL_FAILED`/`TIMEOUT_FAILED`；(b)(c) future 异常完成 |
| ⑥ | W-06 | (a) confirm 批次混入未驱动 `TRY_SUCCESS` 分支（直接调 `TccRunner.aggregate*` 面 + 批量路径面双断言）；(b) 批次内某分支 begin 同步抛 | (a) 该分支被折叠为成功；(b) **后续分支零派发**（这是本批真实 live 缺陷面） | (a) 聚合 = `CONFIRM_FAILED`；(b) 后续分支仍被驱动 + 失败分支进 waitAll |
| ⑦ | W-07 | CANCELLING 写失败注入（非 CAS 错误）+ 业务异常；`endAsync(true,...)` 于 TRYING 记录 | 记录滞留 TRYING 且业务异常无 suppressed；超时恢复走 confirm 方向 | 有限重试后 failedStage；suppressed 在位；超时恢复走 cancel 方向 |
| ⑧ | W-08 | 覆写 `nowForRead`/`nowForWrite` seam（fake dao 时钟偏移），断言读/写两侧取值 | 过期判定随本地墙钟步进误判；写侧读侧混源 | 判定走 `nowForRead`（min）、全部落库走 `nowForWrite`（max）；时钟回拨不触发恢复风暴 |
| ⑨ | W-09 | 回调链自环（policy A → callbackPolicyId A）连续孵化 | 记录无限增长（`X_callback_callback…`） | depth ≥ 3 拒孵 + ERROR + 计数；记录数有界 |
| ⑩ | W-10 | `checkExpiredTransactions` + 恒挂死补偿（`checkExpiredAsync` 覆写挂死 future）+ 可覆写 `currentTimeMillis()` 推进 | 单轮耗时随条数无上界（含慢成功路径）；fetch 抛即整轮静默终止 | 总预算到即结束本轮 + 连续超时熔断 + 双计数；fetch 异常 ERROR 后结束本轮 |
| ⑪ | W-11 | processor 前序记录同步抛（stub `executeRetryFromScanner` 抛） | 后续记录零派发（惰性流中断） | 后续记录全部派发；失败记录 ERROR + 计数 |
| ⑫ | W-12 | 覆写 `currentTimeMillis()` 推进 + 积压多于 `max-batches-per-round` | 单轮排水整个积压 | 达非空批数/时长即结束本轮，剩余留待下轮（下轮继续处理至排空） |
| ⑬ | W-13 | 存量完结行 > 单批上限 | 单次 `findAllByQuery` 全量物化（断言单次物化行数 = batchSize 而非全量） | 多批分页：`findAllByQuery` 调用次数 > 1 且每次返回 ≤ batchSize，最终全量删尽 |
| ⑭ | W-14 | setter 传 0/-1/1001；store `fetchPendingRecords(0, ...)` | 静默接受 | `ERR_RETRY_SCANNER_CONFIG_INVALID` 立即抛 |

- **禁止 blanket「概率腿未命中诚实登记」**：仅红①允许「确定性分支腿为主 + 并发腿（真双 OVERWRITE 竞速）为辅」双形态；其余十三项必须确定性红（结构/编译/行为注入）。
- **5 项部分缓解项的红必须先对 2515 缓解后基线成立**：W-01 → 红①(a)(c)（2515 后的裸 UK + 无条件死信形态）；W-04 → 红④（`cancel(true)` 已在位，缺的是写点闸）；W-06 → 红⑥(b)（2515 已跳过 null-status，缺的是未驱动分支与批内隔离，**注意 红⑥(a) 的「折叠成功」在当前基线不可达**——2515 裁定③ 的 err==null 门控已阻断，见下）；W-11 → 红⑪（2515 的 try/catch 在 `executeRetryFromScanner` 内部，缺的是 processor 流级中断）；W-13 → 红⑬（有界三参已存在，缺的是两参全量物化）。
- **红⑥ 缺陷态判据勘误（draft review R1-B2 采纳）**：当前基线上「confirm 批次混入越界状态 → 全局折叠 CONFIRM_SUCCESS」**不可复现**——同步抛使 `thenCompose` stage 失败，`whenCompleteAsync` 以 `err != null` 进入 `aggregateAndWriteFinalStatus` → 立即 reject（2515 裁定③）。故 **红⑥ 的缺陷态红只能是「后续分支零派发 + 失败分支未进 waitAll」**；聚合结算表的严格化断言是**直接调 `TccRunner.aggregate*` 的单元级钉**（不声称能经管线复现），二者分别登记、不得混记为同一红。这样 5 项部分缓解的「必须先红」门也真正可满足。

### 4.2 零回退钉（全列；git diff 逐条为空核对）

- `TestTccCasPropagation_C10_003`（3）——裁定① CAS 传播与门控。
- `TestTccLedgerCleanup_C10_007`（4）——裁定⑧ 清理接线。
- `TestTccLedgerHygiene_C7_005`（3）——裁定⑧ 账本卫生。
- `TestTccStoreCasConflict_C10_003`（2）——裁定①② afterCommit 与 CAS。
- `TestTccTransactionEndStates`（6）——TCC 终态方向总闸（不覆盖 `endAsync(true,...)`，见 §3.1.7 注）。
- `TestRetryScannerInsulation`——R1-01 绝缘契约。
- `TestRetryLedgerRetention_C7_001`——R1-07 保留策略。
- `TestTccRunner` 既有八条聚合/筛分支断言——§3.1.6 只新增，不弱化。
- `TestTccBranchTransactionSemantics` / `TestTccEngineTransactions`——§3.1.5/§3.1.7 变更须逐条核对；若期望值按新契约变化，登记 E-2543 勘误并保留原断言语义强度（不得删断言）。
- owner doc 成文契约：`nop-retry.md` 续租/簿记/死信化/扫描覆盖；`nop-tcc.md` CAS 传播/聚合权威/清理/索引/崩溃恢复。
- 2515 产物：`replaceRecord` 事务包络、`renewLease`、`reconcileStaleRunningAttempts`、`killExhaustedRecords`、`removeCompletedRecords(3参)`、`runInNewTxn` afterCommit 配对。

### 4.3 C10 / CL 注记

- **C10-002（扫描/批处理保护力）→ W-11 + W-12 同 diff**：红⑪（per-record 隔离真异常注入）+ 红⑫（单轮上界真截断）。
- **C10-004（弃链/超时恢复保护力）→ W-04 + W-10 同 diff**：红④（超时取消后不双驱动）+ 红⑩（单轮预算 + 熔断耗时有界）。**口径**：W-10 记「保护力就位、生产接线缺位（C6-005 dormant）」；不得记为端到端保护已交付。
- **C10-009（注册表身份/回调终结保护力）→ W-02 + W-05 同 diff**：红②（同上下文并发 instance() 单例）+ 红⑤（null response / 回调同步抛不发散）。
- C1-002 → C10-005 已由 2515 兑现，本批仅消费不重造。
- C8-003 → CL-005 同位点主处置随 W-13 关闭（§3.3.4）。

### 4.4 接线与无静默跳过义务（rule 23/24）

- **接线验证**：W-01 四分支真实被 `handleBlockStrategy` catch 消费（断言重读次数与死信表）；W-02 条件恢复真实被四处 put/restore 调用；W-03 腿 1 `getTccRecordAsync` 真实在 `runBranchTransactionAsync` 异步链上（断言 store 读计数），腿 2 收敛驱动真实发起分支 RPC；W-04 写点闸真实拦截（断言 reject 错误码）；W-05 三条 completion 通道各有断言；W-06 循环 catch 真实使后续分支被驱动；W-10 预算/熔断真实结束本轮；W-11 循环真实派发后续记录；W-12 上界真实截断；W-13 分页循环真实多批。
  - **W-10 接线口径**：`checkExpiredTransactions` 现零生产调用方（C6-005 dormant，G19 批外）——接线验证采「单测直调 + 生产接线可行性声明（本批只补上界与熔断，不引入新调用方）」双面，不得以「方法名被引用」冒充接线。
- **无静默跳过**：W-01 四分支全显式（无非 UK 的静默死信）；W-04 弃链写拒绝显式；W-05 三通道全兜底；W-07 重试耗尽显式 failedStage；W-09 拒孵显式 ERROR + 计数；W-10/W-11/W-12/W-14 全部显式留痕/抛错。无空方法体、无空 catch、无 `return null` 占位。
- **端到端**（rule 22）：≥1 条 tcc 补偿链端到端（Try → 分支 RPC → 超时恢复认领 → CAS 门控补偿 → 聚合终态）；≥1 条 retry 扫描端到端（积压 → 单轮上界 → 下一轮继续至排空）。

## 五、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| W-01 输家重试 replace 或重入 `handleExistingRecord` | 与赢家形成 ping-pong 无限覆盖；异常类型 + 重读身份二元判据已给出确定结论 |
| W-01 非 UK 异常也造死信 | 死信含删除动作；「是否存在并发赢家」不可判定时不得以猜测驱动破坏性操作（draft review R1-M8） |
| W-02 改用 api-core 原子 compute 原语 | `nop-api-core` 变更 = plan-first 且属框架公共 API；`synchronized(attrs)` + 条件恢复零 api 面即达 |
| W-02 锁 `context` 对象 | `DelegateContext`/租户包装下两线程可持不同 wrapper 而共享同一 Map，锁 wrapper 不互斥（draft review R1-M5） |
| W-02 仅以文档声明「同上下文禁并发」 | 只是把缺陷转为使用约束；载体修复成本极低，两者合并（载体 + 声明）更充分 |
| W-03 新增 store/api 的「孤儿分支扫描」查询方法 | `nop-tcc-api` 签名变更触发 plan-first；endAsync 末段收敛重读复用既有 `getBranchRecordsAsync` |
| W-03 分支插入携带 `WHERE 父status=TRYING` 认领语义 | ORM 无跨表条件插入原语；父状态与分支插入跨表，需新 DAO 面 |
| W-03 收敛循环自写逐分支驱动 | 会复刻无隔离缺陷；直接复用 W-06 落地后的 `confirmAllAsync`/`cancelAllAsync`（draft review R1-M3） |
| W-04 在 RPC 层真正中止在途补偿 | 传输层无统一取消合同；cancel 只终结 future，故在写点设纪元闸是唯一可靠位置 |
| W-06 仅把 `begin*Async` 同步抛改为 failedStage | 单点改在循环层覆盖三个调用点，且 `begin*` 的 throw 语义仍被其他调用方依赖 |
| W-06「未到终态一律 FAILED」原案 | `KILLED`/`CONFIRM_SUCCESS` 分支会被误判未结算，父事务永不进 finished ⟹ 反向摧毁 2515 裁定⑧ 清理链（draft review R1-B1） |
| W-07 恢复默认方向维持 confirm（无证据时） | 会把未完成的业务以确认方向释放预留资源（最严重错误方向）；超时语义应为回滚 |
| W-07 CANCELLING 写失败无限重试 | CAS 冲突不可重试；非 CAS 错误 3 次已覆盖瞬时故障，长期 DB 故障由恢复扫描兜底 |
| W-08 文档只声明「部署必须 chrony slew」 | 把缺陷转嫁部署；同仓库 retry 侧已有正确设施（`DbEstimatedClock`），tcc 应对齐 |
| W-08 单 seam（只覆写 `getDbEstimatedClock`） | 写侧走 `CoreMetrics.currentTimestamp()` 内联、且 `nextCheckTime` 是写入，单 seam 不可确定性驱动且混源（draft review R1-M7） |
| W-09 装载期 policy 环校验 | 需 policy-save hook（`nop-retry-service` BizModel），不在本批 Scope；运行期深度护栏即达「无界副作用不可达」 |
| W-09 改写 idempotentId 键形状（如剥离 `_callback`） | 破坏既有 UK 语义与幂等重放契约；紧贴尾部的后缀计数即达 |
| W-10 页内并行化补偿 | 同时改变参与方 RPC 并发面与失败语义；预算 + 熔断以最小变更达「耗时有界」目标 |
| W-10 仅连续超时熔断 | 慢成功路径仍可 `pageSize × expireGap × pages`，不构成耗时有界（draft review R1-M2） |
| W-11 把整个 processor 包 try/catch | 现状已是整批级（2515）；缺陷正是批级传染，必须降到 per-record |
| W-12 硬中断在途批 | 破坏 2515 裁定⑥ 的 future 终态完备前提；上界只控本轮起点 |
| W-12 以循环迭代计数消耗预算 | 锁竞争下的 `continue` 轮会静默烧完预算（draft review R1-m3）；按非空批计 |
| W-13 两参改为每批独立事务 | 静默改变公共契约的原子性；分页已闭合内存包络，单事务面不在生产路径（draft review R1-M4） |
| W-13 删除两参重载 | 公共接口签名删除 = 破坏契约 |
| W-14 越界值静默回落缺省 | 正是「0 值静默空转」缺陷形态；必须 fail-fast |

## 六、与已有设计的关系

- [retry-tcc-cas-compensation-and-ledger-hygiene.md](retry-tcc-cas-compensation-and-ledger-hygiene.md)：R2-12 裁定①–⑨ 为本批前提（§一 逐条核对）；本设计只补残余面，不改其机制体裁。**W-06 结算表是唯一需要显式对齐裁定⑧ 的地方**（§3.1.6 已对齐）。
- [ledger-retention-policy.md](ledger-retention-policy.md)：W-13 的批大小键与 W-14 的参数边界沿用其族级 SSOT 纪律；本批**不新增**保留策略参数。
- [periodic-task-insulation.md](periodic-task-insulation.md)：W-10/W-12 的上界与熔断在绝缘层之内工作（绝缘层管 Throwable 重臂，本批管单轮耗时有界），两层正交。
- 下游：R4-32（C7-011 墙钟族）不因 W-08 而免除——本批只把 tcc 时钟源切到 DB 估算钟，跨单元 IClock 注入仍归 8.72。
- owner doc 落点（Phase 3 统一落账）：`docs-for-ai/03-modules/nop-retry.md`（参数边界节 + 回调深度护栏 + 扫描单轮上界 + per-record 隔离 + OVERWRITE 输家语义 + 非 UK 异常不死信声明）、`docs-for-ai/03-modules/nop-tcc.md`（时钟/期限节 + 补偿链韧性与 suppressed + 超时恢复默认 cancel + 分支守卫 DB 复核 + 收敛残余窗 + 弃链写点闸残余 + 注册表作用域）、`docs-for-ai/04-reference/source-anchors.md`（按实际）。
