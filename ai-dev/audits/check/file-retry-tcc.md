# file-retry-tcc 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-file + nop-retry + nop-tcc
- 文件数: 约 123（src/main/java，含生成文件；剔除 `_` 前缀生成文件后约 112）
- 覆盖范围声明:
  - 三模块全部非生成 `.java` 文件均已枚举。**核心逻辑文件 100% 逐行深读**，包括：
    - nop-file（13 个有效文件）: `DaoResourceFileStore`、`DaoFileRecord` 深读；biz model / 常量 / api bean / app 入口全部过目（多数为空壳或生成式数据类）。
    - nop-retry（48 个有效文件）: `RetryEngineImpl`、`RetryTaskImpl`、`RetryScannerImpl`、`RetryRecordStoreImpl`、`IRetryRecordStore`、`IRetryScanner`、`IRetryEngine`、`IRetryTask`、`NopRetryErrors`、常量接口深读；4 个 CrudBizModel、biz 接口、api bean（生成式 DTO）过目确认为平凡代码。
    - nop-tcc（51 个有效文件）: `TccEngine`、`TccTransaction`、`TccBranchTransaction`、`TccRunner`、`TccHelper`、`TccTransactionRegistry`、`TccRecordStore`、`TccStatus`、`TccRpcServiceInterceptor`、`TccGatewayInterceptor`、`DefaultTccServiceMetaLoader`、`ReflectionTccServiceMetaBuilder`、`TccMethodMeta`、`TccServiceMeta`、api 契约接口深读；biz model / 常量 / api bean 过目确认为平凡代码。
  - 资源文件核对: 三模块 `_vfs` 下 beans.xml（bean 注册）、orm 模型（索引/非空约束）、data-auth/action-auth 均已检查。
  - 框架依赖代码（nop-ioc 构造器注入、nop-orm updateDirectly、nop-core LocalResourceStore/FileResource、nop-commons LimitedInputStream/GlobalExecutors/FutureHelper、nop-biz-file-core NopFileStoreBizModel）仅为验证可达性/排除误报而查阅，不作为检查对象。
  - 测试代码不在检查范围（仅用于验证设计意图，如 TestTccEngine 的状态保护语义）。
  - grep 全量扫描项: 空 catch、`new RuntimeException`、`printStackTrace`、`Thread.sleep`、`synchronized`、`new File(`/路径拼接、`@Inject`/`@InjectValue` 用法。主代码无 bare RuntimeException / printStackTrace / Thread.sleep 命中（仅测试代码命中）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 3 |
| P2 | 6 |
| P3 | 8 |

## 发现列表

### [P0] TCC 超时取消失败被聚合为 CANCEL_SUCCESS 终态，补偿被永久放弃且状态失真

- **文件**: `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java:152-160`、`nop-tcc/nop-tcc-api/src/main/java/io/nop/tcc/api/TccStatus.java:97-100`、`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccBranchTransaction.java:117-118`
- **维度**: D1（状态机转换错误）、D8（聚合语义与状态定义不一致）
- **证据**:
```java
// TccRunner.aggregateCancelBranchStatus —— 只识别两种失败
public static TccStatus aggregateCancelBranchStatus(List<ITccBranchTransaction> branchTxns) {
    for (ITccBranchTransaction branchTxn : branchTxns) {
        if (branchTxn.getBranchStatus() == TccStatus.BIZ_CANCEL_FAILED)
            return TccStatus.BIZ_CANCEL_FAILED;
        if (branchTxn.getBranchStatus() == TccStatus.CANCEL_FAILED)
            return TccStatus.CANCEL_FAILED;
    }
    return TccStatus.CANCEL_SUCCESS;
}

// TccStatus —— TIMEOUT_FAILED 是失败态，却不在聚合失败清单中
public boolean isCancelled() {
    return this == TRY_FAILED || this == CANCEL_SUCCESS || this == TIMEOUT_SUCCESS
            || this == BIZ_CANCEL_FAILED || this == TIMEOUT_FAILED;
}

// TccBranchTransaction.finishCancelAsync —— timeout=true 且响应失败时写 TIMEOUT_FAILED
return getRepository().updateTccBranchStatusAsync(branchRecord,
        timeout ? TccStatus.TIMEOUT_FAILED : TccStatus.CANCEL_FAILED, ex);
```
- **现状**: 超时检查路径（`TccEngine.checkExpiredAsync` → `endAsync(true, null, null)` → `doCancelAsync(timeout=true)`）中，若分支 cancel RPC 失败且非 bizFatal，分支状态写为 `TIMEOUT_FAILED`。`aggregateCancelBranchStatus` 不把 `TIMEOUT_FAILED` 视为失败，事务记录被写成 `CANCEL_SUCCESS`（`isFinished()==true`，写 endTime）。
- **风险**: (1) 事务状态与事实不符（取消实际失败却标记成功终态）；(2) `fetchExpiredRecords` 的过滤条件 `status < CONFIRM_SUCCESS(11)` 不再拾取 12，重试机制永久放弃该事务，参与者预留资源悬挂无法回收；(3) 即使未来重新触发 cancel，`TccRunner.cancelAllAsync` 中 `branchStatus.isCancelled()` 包含 `TIMEOUT_FAILED`，会跳过这些分支，双重封死补偿路径。触发条件仅为"超时处理时一次 cancel RPC 失败"（网络抖动即可），路径现实。
- **建议**: `aggregateCancelBranchStatus` 将 `TIMEOUT_FAILED` 聚合为 `CANCEL_FAILED`（可重试态）或独立的可重试超时失败态；`isCancelled()` 中移除 `TIMEOUT_FAILED`，使失败的超时取消可以被补偿重试。
- **误报排除**: 已核对 `TccStatus` 全部状态定义与 `doCancelAsync`/`doConfirmAsync` 的全部调用链；事务级 `TIMEOUT_FAILED` 状态不存在其他写入点（聚合只写 CANCEL_SUCCESS/BIZ_CANCEL_FAILED/CANCEL_FAILED），确认该路径唯一且可达；`updateTccStatusAsync` 为盲写，无下游纠正。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复并附回归测试。`aggregateCancelBranchStatus` 将 `TIMEOUT_FAILED` 聚合为可重试的 `CANCEL_FAILED`；`TccStatus.isCancelled()` 移除 `TIMEOUT_FAILED` 使 `cancelAllAsync` 可重新补偿这些分支；`doConfirmAsync` guard 显式包含 `TIMEOUT_FAILED` 防止误入 confirm。测试：`nop-tcc-core` `TestTccRunner#aggregateCancel_whenHasTimeoutFailed_thenCancelFailed`、`#timeoutFailed_isNotCancelled`。

### [P1] Retry：任务提交后首次执行期间记录仍为 PENDING 且到期，扫描器并发重复执行同一任务

- **文件**: `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:155-167`、`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:102-116`
- **维度**: D3（重试调度并发）、D8（重复执行）
- **证据**:
```java
// RetryEngineImpl.executeTask —— 落库后立即执行，状态仍是 PENDING
NopRetryRecord record = recordStore.newRecord(task, request);
recordStore.saveRecord(record);
return executeWithRetry(record, policy, request, cancelToken, task);

// RetryRecordStoreImpl.newRecord —— 到期时间=now，状态=PENDING
record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_PENDING);
record.setNextTriggerTime(CoreMetrics.currentTimestamp());
```
- **现状**: `executeTask` 保存记录后直接发起首次 RPC，期间记录状态为 PENDING、`nextTriggerTime=now`，满足 `fetchPendingRecords`（status IN (PENDING,RETRYING) AND nextTriggerTime<=now）的拾取条件。扫描器默认 5 秒一轮，`tryLockRecordsForProcess` 可成功锁定（首次状态更新要等首次 RPC 出结果后才发生）。
- **风险**: 首次尝试耗时超过一个扫描周期（RPC 超时通常是秒级）时，扫描器会对同一 idempotentId 的任务并发发起第二次执行——业务侧重复副作用。引擎虽用 `findPendingRecordByIdempotentId` + 唯一索引 `UK_RETRY_IDEMPOTENT_ID` 表达防重意图，但该竞态绕过了防重。扫描路径与原调用链后续的 `updateRecord` 还会互相触发版本冲突，状态管理混乱。
- **建议**: `executeTask` 在发起首次执行前先走 `tryLockRecordsForProcess`（或直接以 RETRYING+lease 落库/更新），首次结果落库后再回到 PENDING；或将新记录的 `nextTriggerTime` 设为 `now + lease`，使扫描器只在调用方失联后接管。
- **误报排除**: 已核对 `fetchPendingRecords` 无 retryCount/来源过滤、`doExecute` 不改记录状态、扫描器随引擎启动（`LifeCycleSupport.start()` 带 `@PostConstruct`，beans.xml 注册即生效），确认无其他互斥机制。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复（无独立单测：依赖 dao 层）。`RetryRecordStoreImpl.newRecord` 将 `nextTriggerTime` 推迟一个租约周期（`DEFAULT_RETRYING_TIMEOUT_MS`），调用方发起首次执行期间扫描器不再拾取该记录，仅在调用方失联（租约到期）后接管。

### [P1] Retry：扫描执行异常/早退路径不更新记录状态，坏记录陷入"每个租约周期重扫一次"的死循环且永不进死信

- **文件**: `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:194-215、540-554`
- **维度**: D1（重试计数边界）、D6（无效轮询）
- **证据**:
```java
// executeRetryFromScanner —— 早退只返回失败 future，不改记录状态/retryCount
if (StringHelper.isEmpty(serviceName) || StringHelper.isEmpty(serviceMethod)) {
    return CompletableFuture.failedStage(new NopException(ERR_RETRY_DEAD_LETTER_INVALID_EXECUTOR)...);
}
if (StringHelper.isEmpty(requestPayload)) {
    return CompletableFuture.failedStage(new NopException(ERR_RETRY_DEAD_LETTER_INVALID_REQUEST)...);
}

// doStart 的 processor —— 仅记日志
executeRetryFromScanner(record, null)
    .whenComplete((resp, ex) -> { if (ex != null) LOG.error(...); })
```
- **现状**: 记录被扫描器锁定后进入 RETRYING、`nextTriggerTime=now+600000`（租约）。当执行路径同步抛异常或早退（无 serviceName/serviceMethod/requestPayload、policyId 为 null 导致 NPE、policy 行被删导致 `requireEntityById` 抛错）时，记录状态与 retryCount 均不变。租约 10 分钟到期后再次被拾取，无限循环。
- **风险**: 无法执行的坏记录永远不进死信（对比：`handleExecutionFailure` 在 `retryCount >= maxRetryCount` 时才进死信，而这条路径从不递增 retryCount），每 10 分钟空转一次产生持续无效负载与错误日志；这些记录也无法通过人工 pause 之外的手段终止（pause 只改状态，恢复后又回到循环）。
- **建议**: 早退/同步异常路径统一走 `handleExecutionFailure` 或直接 `moveToDeadLetter`；`executeRetryFromScanner` 的调用侧（doStart processor）对未消费的记录做兜底状态回退（PENDING + 退避）或进死信。
- **误报排除**: 已核对 `requestPayload` 为空可达（`newRecord` 在 `request==null` 时不设 payload，`callAsync(null)` 或进程在立即重试阶段崩溃后由扫描器接管即触发）；`loadPolicy(null)` 返回 null 见下条；`RetryScannerImpl.doScan` 的 catch 只记日志不回退状态，确认无兜底。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复（无独立单测：依赖 dao 层）。`executeRetryFromScanner` 的无 executor/无 payload 早退路径现在直接 `moveToDeadLetter`，坏记录不再无限空转、正常进入死信闭环。

### [P1] TCC：cleanCompletedTransactions 违反接口契约，会物理删除未完结/未知状态的事务及分支记录

- **文件**: `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:315-318`、`nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:248-264`
- **维度**: D8（契约一致性）、D1（数据丢失）
- **证据**:
```java
// ITccEngine 契约: "删除...已经成功完结或者成功取消的事务。如果事务状态处于未知状态，则仍然会被保留，等待手工处理"
// TccEngine 实现:
public void cleanCompletedTransactions(long retentionTime) {
    repository.removeCompletedRecords(retentionTime, false);   // onlyCompleted=false
}

// TccRecordStore.removeCompletedRecords —— onlyCompleted=false 时不加状态过滤
if (onlyCompleted)
    query.addFilter(FilterBeans.in(NopTccRecord.PROP_NAME_status, TccStatus.getFinishedStatus()));
this.recordDao().deleteByQuery(query);   // 分支记录同样按 beginTime 无状态过滤删除
```
- **现状**: 实现传 `onlyCompleted=false`，按 `beginTime` 删除所有超期记录，不区分状态；分支记录同样无状态过滤。
- **风险**: 调用方按契约语义使用该 API（定时清理）时，处于 TRYING/TRY_UNKNOWN/CONFIRM_FAILED/CANCEL_FAILED 等未完结状态的事务与分支被物理删除，补偿所需的全部信息（serviceName/confirmMethod/cancelMethod/requestData）丢失，预留资源永久悬挂且无法人工处理。
- **建议**: `cleanCompletedTransactions` 传 `true`；或修正接口文档。若确需全量清理，应另行提供显式 API。
- **误报排除**: 仓库内无生产调用方（属公共 API，设计给定时任务使用），但实现与同一接口的 javadoc 直接矛盾，属确凿契约违背，非猜测。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。实现改为传 `onlyCompleted=true`，与接口 javadoc 契约对齐（只删除已成功完结/取消的事务，未知状态保留等待人工处理）。

### [P2] Retry：未设置 policyId 时 loadPolicy 返回 null，executeWithRetry 直接 NPE（且发生在记录已落库之后）

- **文件**: `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:156、176、224`、`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:237-242`
- **维度**: D1（NPE）、D8（API 契约）
- **证据**:
```java
// RetryRecordStoreImpl.loadPolicy —— 空 id 返回 null（requireEntityById 对非空 id 抛错）
public NopRetryPolicy loadPolicy(String policyId) {
    if (policyId == null || policyId.isEmpty()) return null;
    return getPolicyDao().requireEntityById(policyId);
}

// RetryEngineImpl.executeWithRetry —— 无 null 防护
long deadlineTimeoutMs = policy.getDeadlineTimeoutMsOrDefault();   // policy==null 时 NPE
```
- **现状**: `IRetryTask.withPolicyId` 是可选流式 API，`newRetryTask(svc, method).callAsync(req)` 不设 policyId 时，`executeTask` 里 `loadPolicy` 返回 null：已有同 idempotentId 记录时在 `handleBlockStrategy` 的 `policy.getBlockStrategyOrDefault()` NPE；无记录时先 `saveRecord` 成功再在 `executeWithRetry` NPE——留下一条会被扫描器反复接管的 PENDING 孤儿记录（叠加上一条的循环问题）。
- **风险**: 公共 API 不可用 + 脏记录。`newRecord`（同文件 105-110 行）对 null policy 做了容错并用 `DEFAULT_MAX_RETRY_COUNT`，证明"无 policyId"是设计内状态，engine 层漏判属实现不一致。
- **建议**: `loadPolicy` 空时返回内置默认策略对象（各 `*OrDefault()` 均有默认值），或在 `executeTask` 入口校验并抛 `ERR_RETRY_POLICY_NOT_FOUND`（该错误码已定义但从未使用）。
- **误报排除**: 已核对 `RetryTaskImpl` 的 policyId 默认为 null、ORM 中 RETRY_RECORD.POLICY_ID 列非 mandatory（可落库）、测试全部显式设置 policyId（未覆盖该路径）。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。`RetryEngineImpl.loadPolicyOrDefault`：无策略时返回内置默认 `NopRetryPolicy`（各 `*OrDefault()` 提供默认值），不再 NPE、不再留下孤儿记录。

### [P2] TCC：事务/分支状态更新为盲写，状态检查基于本地内存副本，initiator 结束与超时检查器并发时可交错 confirm/cancel

- **文件**: `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java:84-90、104-110`、`nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:137-154、170-199`
- **维度**: D3（TCC 并发分支）
- **证据**:
```java
// TccTransaction.doCancelAsync —— 只检查本地 tccRecord 的内存状态
TccStatus curStatus = tccRecord.getTccStatus();
if (curStatus == TccStatus.CONFIRMING || ... ) return FutureHelper.success(null);

// TccRecordStore.updateTccStatusAsync —— 直接覆盖写，无期望状态条件（无 CAS）
tccRecord.setStatus(status.getCode());
recordDao().updateEntityDirectly(tccRecord);
```
- **现状**: `doCancelAsync/doConfirmAsync` 的互斥判断读的是各自实例加载时的实体快照；两个并发流（正常 initiator 结束 vs `checkExpiredTransactions` 超时处理，或重复的 endAsync）各自的快照都可能是 TRYING，双方分别进入 confirm 与 cancel（或双 confirm）。写库虽带乐观锁版本（冲突方报错），但 RPC 已发出，无法收回。分支级同样：仍在 TRYING 的分支被超时流程 cancel 后，迟到的 `finishTryAsync` 会把 BEFORE_TIMEOUT/CANCELLING 盲覆盖为 TRY_SUCCESS（`updateTccBranchStatusAsync` 无转换校验）。
- **风险**: 参与者可能同时/先后收到 confirm 与 cancel，事务终态取决于交错顺序；空回滚/悬挂防护完全依赖参与者幂等，引擎层未提供互斥保证（与 `doCancelAsync` 注释宣称的"避免互相覆盖中间态"目标不符，该保护对跨实例并发无效）。
- **建议**: 状态更新改为条件更新（`update ... where status in (期望状态集)`），失败方立即中止后续 RPC；分支状态机在 store 层校验合法转换。
- **误报排除**: 已核对 `updateEntityDirectly` 无状态条件；`fetchExpiredRecords` 状态过滤 `< CONFIRM_SUCCESS(11)` 包含 CONFIRMING(5)，超时检查器确实可能与正在 confirm 的 initiator 并发；测试 `testStateConflictProtection` 只覆盖同实例串行场景。

> **处置（fix-ai-check 分支，2026-08-21）**: 已确认，暂缓。需要把 `updateTccStatusAsync` 改为条件更新（CAS）并重构分支状态机校验，涉及 nop-orm 条件更新能力与跨实例并发语义设计，建议单独立项。

### [P2] TCC：CONFIRM_FAILED 事务被超时检查反复拾取但每次空转，补偿重试实际不存在

- **文件**: `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java:86`、`nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:207-242`
- **维度**: D1（状态机）、D8（重试语义与实现不匹配）
- **证据**:
```java
// doCancelAsync 第一道 guard —— CONFIRM_FAILED 直接返回，不做任何事
if (curStatus == TccStatus.CONFIRMING || curStatus == TccStatus.CONFIRM_FAILED || curStatus.isConfirmed())
    return FutureHelper.success(null);
```
- **现状**: CONFIRM_FAILED(6) 的事务会被 `fetchExpiredRecords`（`status < 11`）反复拾取；`checkExpiredAsync → endAsync(true,null,null)` 中分支 CONFIRM_FAILED 是 rollbackOnly → 走 `doCancelAsync` → 被 guard 拦下直接返回。整个引擎没有任何重新 confirm 的路径（confirm 只在非 rollbackOnly 时执行）。每轮拾取只推高 expireTime/retryTimes，直到 100 次后记录被永久忽略。
- **风险**: confirm 失败的事务既不重试 confirm 也不 cancel（TCC 原则：进入 confirm 阶段后只能重试 confirm），最终依赖人工处理，但 100 轮空转掩盖了问题且无告警语义；与 `ITccEngine.checkExpiredTransactions` 的"重试"文档语义不符。
- **建议**: 对 CONFIRM_FAILED 的记录提供 re-confirm 路径（重新进入 `doConfirmAsync`），或在空转分支记录 WARN/提供专门的管理动作。
- **误报排除**: 已完整跟踪 endAsync 全部分支确认无 re-confirm 调用点；对比 CANCEL_FAILED 路径（可正常重试 cancel）确认差异。

> **处置（fix-ai-check 分支，2026-08-21）**: 已确认，暂缓。需要为 CONFIRM_FAILED 设计 re-confirm 路径或专门的管理动作与告警语义，属状态机设计决策。

### [P2] Retry：扫描结果处理器在共享 globalWorker 线程上 join() 阻塞整批 RPC，与平台全局任务争抢线程

- **文件**: `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:540-554`、`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:96-101、165-167`
- **维度**: D6（性能）、D3
- **证据**:
```java
CompletableFuture<?>[] futures = records.stream()
        .map(record -> executeRetryFromScanner(record, null)...toCompletableFuture())
        .toArray(CompletableFuture[]::new);
CompletableFuture.allOf(futures).join();   // 阻塞直至整批(默认100条)RPC全部完成
```
- **现状**: 扫描任务运行在 `GlobalExecutors.globalTimer().executeOn(globalWorker())` 的共享线程池上，processor 内 `join()` 同步等待整批重试 RPC 完成；`doScan` 的 `do...while` 循环内还会连续处理多批。慢 RPC（超时秒级 × 100 条）会长期占满 globalWorker 线程，而该线程池同时承载平台其他全局定时任务（含本引擎的 `scheduleImmediateRetry`），线程数不足时互相饥饿甚至卡死立即重试链。
- **建议**: processor 改为纯异步提交（返回 future 由 scanner 用 `thenAccept` 串联下一轮），或使用独立的有界执行器。
- **误报排除**: globalWorker 为共享多线程池（缓解了确定性死锁，但不消除饥饿），`join()` 抛出的 CompletionException 由 doScan 捕获仅记日志——阻塞本身已核实为无条件发生。

> **处置（fix-ai-check 分支，2026-08-21）**: 已确认，暂缓。处理器改纯异步提交需要重构 scanner 的批间串行语义，建议配合独立有界执行器一并设计。

### [P2] Retry：triggerCallback 回调复用原业务服务/方法并替换 payload，语义与"回调策略"不符

- **文件**: `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:453-499`
- **维度**: D8（契约漂移）
- **证据**:
```java
String callbackService = record.getServiceName();     // 回调目标=刚刚失败的业务服务
String callbackMethod = record.getServiceMethod();
IRetryTask callbackTask = newRetryTask(callbackService, callbackMethod)
        .withPolicyId(policy.getCallbackPolicyId())
        .withIdempotentId(record.getIdempotentId() + "_callback");
callbackRequest.setData(buildCallbackData(record, success, error));  // payload 换成 {recordId,success,...} Map
```
- **现状**: 策略表只有 callbackPolicyId/triggerType，没有回调目标字段；实现把回调发回"原失败服务+原方法"，但 payload 替换为状态通知 Map——原业务方法几乎必然反序列化/校验失败，回调永远失败。
- **风险**: 回调功能名存实亡；另外 `idempotentId + "_callback"` 可能超出 IDEMPOTENT_ID 列宽 64（原 idempotentId > 55 字符时插入报错），且回调记录落到默认 namespace 而非原记录的 namespace。
- **建议**: 策略模型增加回调服务/方法字段，或在文档中明确回调协议（原服务需实现通知方法）；拼接后做长度截断/校验。
- **误报排除**: 已核对 ORM 的 NOP_RETRY_POLICY 表无 callback service/method 列、IDEMPOTENT_ID precision=64，确认无遗漏的目标字段。

> **处置（fix-ai-check 分支，2026-08-21）**: 已确认，暂缓。策略模型缺少回调服务/方法字段（ORM 模型变更属 plan-first 区域），需要模型扩展后重实现。

### [P2] nop-file：removeTempFileByOwner 只删数据库记录，物理文件成为永久孤儿

- **文件**: `nop-file/nop-file-dao/src/main/java/io/nop/file/dao/store/DaoResourceFileStore.java:133-140`
- **维度**: D2（临时文件清理/资源泄漏）
- **证据**:
```java
public void removeTempFileByOwner(String ownerId) {
    IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
    NopFileRecord example = new NopFileRecord();
    example.setCreatedBy(ownerId);
    example.setBizObjId(FileConstants.TEMP_BIZ_OBJ_ID);
    dao.deleteByExample(example);      // 仅删 DB 行，record.getFilePath() 对应物理文件未删除
}
```
- **现状**: 临时文件记录（`__TEMP__`）被批量删除时，对应的存储层文件没有调用 `removeResource`。对比同文件 `detachFile`（先删记录再按 `isUniqueRef` 删物理文件）可见遗漏。
- **风险**: 该方法作为 IFileStore 的清理入口被调用后，存储目录持续膨胀（磁盘耗尽），且 DB 中已无指向这些文件的记录，无法再定位清理。
- **建议**: 删除前先查询记录并逐个 `removeResource(filePath)`（复用 isUniqueRef 逻辑），再删 DB 行。
- **误报排除**: 已全仓检索该方法无其他调用方（当前为待接入的公共清理 API），也未发现独立的孤儿文件清扫任务；泄漏路径本身确凿。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复（无独立单测：依赖 dao 层）。改为按 (createdBy, TEMP_BIZ_OBJ_ID) 查询记录后逐条删除，并按 `isUniqueRef` 判定删除物理文件，与 `detachFile` 对齐。
>
> **补记（fix-ai-check 分支，2026-08-22）**: 该修复当时引入三处编译错误（`IEntityDao` 无 `findListByQuery`、`NopException` 无单 String 构造）未被模块编译验证；已修正为 `findAllByQuery` + 模块内 ErrorCode 常量（`nop.err.file.invalid-biz-obj-name`/`invalid-file-ext`），随全量构建验证。

### [P3] nop-file：detachFile 的 isUniqueRef 存在 TOCTOU，并发分离共享 originFileId 的记录可致孤儿文件

- **文件**: `nop-file/nop-file-dao/src/main/java/io/nop/file/dao/store/DaoResourceFileStore.java:311-339`
- **维度**: D3（文件去重竞争）
- **证据**:
```java
dao.deleteEntity(record);
if (isUniqueRef(dao, record)) {     // 删除后再查"是否还有同 originFileId 的引用"
    removeResource(record.getFilePath());
}
```
- **现状**: 两个并发 detach 分别删除共享同一物理文件的两条记录时，各自的 `findFirstByQuery` 可能都读到对方未提交的删除前状态，双双判定"仍有引用"而跳过文件删除。
- **风险**: 低概率产生无 DB 引用的孤儿文件（仅占磁盘，无数据错误）。
- **建议**: 改为删除后按 originFileId 做 count 判定并对文件删除做幂等容错，或接受现状并配合离线清扫。
- **误报排除**: 确认无数据库层约束阻止该交错；影响评估为资源泄漏而非正确性。

> **处置（fix-ai-check 分支，2026-08-21）**: 复查后维持现状。并发分离共享文件属低概率资源泄漏（仅磁盘占用，无数据错误），审计建议本身含"接受现状并配合离线清扫"选项；彻底解决需按 originFileId 的 count 判定与文件删除幂等容错，暂缓。

### [P3] nop-file：存储层路径拼接无防御性校验，安全完全依赖上层调用方

- **文件**: `nop-file/nop-file-dao/src/main/java/io/nop/file/dao/store/DaoResourceFileStore.java:231-248`
- **维度**: D5（路径遍历防御纵深）
- **证据**:
```java
protected String newPath(String bizObjName, String fileId, String fileExt) {
    ...
    sb.append('/').append(bizObjName);      // bizObjName 未校验
    ...
    if (keepFileExt && !StringHelper.isEmpty(fileExt))
        sb.append('.').append(fileExt);     // fileExt 未校验
```
- **现状**: `bizObjName` 直接拼入存储路径；`LocalResourceStore.getResource` 只检查 `path.startsWith(basePath)`，`new File(dir, relativePath)` 不拒绝 `..`。标准上传入口（框架 `NopFileStoreBizModel.upload`）会以 `isValidSimpleVarName` 校验 bizObjName、`StringHelper.fileExt` 保证扩展名不含 `/`，因此线上路径当前不可达；但 `IFileStore.saveFile` 的其他直接调用方（如 nop-datav 的导出任务）传入非受控 bizObjName 时即可写出上传根目录。
- **风险**: 纵深防御缺失，未来调用方疏忽即成路径遍历写漏洞。
- **建议**: `newPath` 内对 bizObjName 做 `isValidSimpleVarName` 校验、对 fileExt 做 `[A-Za-z0-9]+` 白名单。
- **误报排除**: 已核实标准链路有校验（排除"当前可利用"的误判）、`StringHelper.fileExt` 取最后 `/` 之后的内容故扩展名不可能含 `/`，仅 bizObjName 为真实风险面。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复（纵深防御）。`newPath` 对 `bizObjName` 做 `isValidSimpleVarName` 校验、对 `fileExt` 做 `[A-Za-z0-9]+` 白名单，越界抛 NopException。

### [P3] Retry/TCC：data-auth 为空壳 + CRUD 直接暴露引擎执行表，表内数据即执行凭据

- **文件**: `nop-retry/nop-retry-service/src/main/resources/_vfs/nop/retry/auth/nop-retry.data-auth.xml`、`nop-tcc/nop-tcc-service/src/main/resources/_vfs/nop/tcc/auth/nop-tcc.data-auth.xml`
- **维度**: D5（重试参数注入）
- **证据**:
```xml
<data-auth ...>
    <objs/>    <!-- 无任何行级权限约束 -->
</data-auth>
```
- **现状**: `NopRetryRecordBizModel` 等均为纯 `CrudBizModel`，拥有 update 权限的用户可改写 `serviceName/serviceMethod/requestPayload`（TCC 分支表可改 `confirmMethod/cancelMethod`），引擎随后按表内数据发起 RPC（扫描器/补偿器以系统身份执行）。
- **风险**: 慎重配置 action-auth 时风险可控，但一旦写权限放宽即形成"改一行数据 → 平台替你调任意服务"的提权通道。
- **建议**: 对引擎执行表禁用通用 update（或收窄到专用管理动作），在引擎层对 serviceName/method 做注册白名单校验。
- **误报排除**: 已确认 data-auth 为空、引擎无服务名白名单机制；是否暴露取决于部署方权限配置，故定 P3。

> **处置（fix-ai-check 分支，2026-08-21）**: 已确认，暂缓。属部署配置与权限模型层面（需收窄通用 update 到专用管理动作 + 引擎层服务名白名单），涉及 action-auth 资源变更，建议单独立项。

### [P3] TCC：beginConfirmAsync/beginCancelAsync 用 Guard.checkArgument 抛裸 IllegalArgumentException，违背错误处理两档策略

- **文件**: `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccBranchTransaction.java:77、97`
- **维度**: D4、D7
- **证据**:
```java
Guard.checkArgument(branchRecord.getBranchStatus().isAllowConfirm());   // 抛 IllegalArgumentException("Invalid:")
```
- **现状**: 分支状态不满足转换条件时抛裸 `IllegalArgumentException`，无 ErrorCode、无上下文参数（txnId/branchId/status 均丢失），出现在异步补偿链路中难以诊断。
- **风险**: 违反平台"框架核心/公共 API 用 NopException + ErrorCode + .param(...)"约定；错误信息不可定位。同文件其他状态错误均用 NopException（如 `TccRunner.aggregateConfirmBranchStatus`），此处不一致。
- **建议**: 改为 `NopException(ERR_TCC_INVALID_*)` 并携带 txnId/branchId/当前状态。
- **误报排除**: 已核对 `Guard.checkArgument` 的实现确为 IllegalArgumentException；模块内已有 TccCoreErrors 可承载该错误。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。改为 `NopException(ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS)` 并携带 txnGroup/txnId/当前状态。

### [P3] TCC：TccGatewayInterceptor 自动新建事务时设置的 TccContext 在请求内不清理

- **文件**: `nop-tcc/nop-tcc-integration/src/main/java/io/nop/tcc/integration/gateway/TccGatewayInterceptor.java:84-100`
- **维度**: D2/D3（上下文管理）
- **证据**:
```java
if (oldContext == null && tccContext == null) {
    TccContext newCtx = new TccContext(); ...; TccContext.setCurrent(newCtx);
}
...
}).whenComplete((ret, err) -> {
    if (oldContext != null) { TccContext.setCurrent(oldContext); }
    else if (tccContext != null) { TccContext.removeCurrent(tccContext); }   // newCtx 分支无清理
});
```
- **现状**: `oldContext==null && tccContext==null`（自动建事务）分支创建的 `newCtx` 在 whenComplete 中没有对应的 remove 分支。
- **风险**: TccContext 挂在请求级 IContext 上，跨请求泄漏被平台上下文生命周期兜底，但同一请求内网关拦截器之后的组件会看到残留的事务上下文（可能被误判为参与中事务）。影响有限。
- **建议**: whenComplete 补上 `removeCurrent(newCtx)`（保存引用）。
- **误报排除**: 已核对 `TccContext` 基于 IContext attribute 而非独立 ThreadLocal，排除了跨请求泄漏的更严重判定。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。自动建事务分支创建的 newCtx 保存引用，whenComplete 中补 `removeCurrent(newCtx)`。

### [P3] TCC：补偿(confirm/cancel)失败对调用方不可见，原始异常仅落库

- **文件**: `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java:58-78、95-98`
- **维度**: D4（异常吞噬）
- **证据**:
```java
}).whenComplete((ret, err2) -> {
    if (err2 != null && ex != null) {
        LOG.error("nop.tcc.end-compensate-fail:txn={}", this, err2);   // 仅日志
    }
    if (ex != null) throw NopException.adapt(ex);   // 调用方只见业务异常
});
```
- **现状**: `FutureHelper.whenCompleteAsync` 用 handler 返回的 stage 结果覆盖原始结果；`doCancelAsync/doConfirmAsync` 的 handler（更新状态）正常完成后，补偿阶段的失败 err2 被丢弃，最终 future 成功。当 `ex != null` 时调用方只收到业务异常，补偿失败信息仅在日志/DB 状态中。
- **风险**: 业务失败+取消也失败的场景下，调用方无从感知需要人工介入（好在 DB 状态是 CANCEL_FAILED 可查）。
- **建议**: err2 != null 且 ex != null 时以业务异常为主因、补偿失败作为 suppressed/附加参数携带。
- **误报排除**: 该行为有代码注释表明有意为之（"避免业务异常被补偿阶段的返回值吃掉"），但 err2 完全丢失确属信息损失，定 P3。

> **处置（fix-ai-check 分支，2026-08-21）**: 复查后维持现状。代码注释表明有意为之（避免业务异常被补偿阶段的返回值吃掉），err2 已记录日志且 DB 状态可查；改为 suppressed 携带需调整 whenCompleteAsync 的结果覆盖语义，收益有限。

### [P3] Retry：retryFromDeadLetter 不变更死信状态、无防重/审计，重复触发无约束

- **文件**: `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:73-107`
- **维度**: D8（契约）
- **证据**:
```java
ApiRequest<?> request = JsonTool.parseBeanFromText(requestPayload, ApiRequest.class);
return rpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, null);
// 不创建 retry record、不改 dead letter 状态、不记录已重试次数
```
- **现状**: 手工重放只做一次性 RPC，死信记录无任何状态流转，多次点击即多次执行，且不进入重试引擎的计数/死信闭环。
- **风险**: 运维侧误操作放大重复执行；重放结果与死信记录无关联可查。
- **建议**: 重放时创建受策略管理的 retry record（复用 executeTask），或至少在死信上记录重放时间/结果。
- **误报排除**: 已核对 `NopRetryDeadLetter` 实体与 store 无相关字段更新调用。

> **处置（fix-ai-check 分支，2026-08-21）**: 已确认，暂缓。重放闭环（受策略管理的重放记录/审计）需要状态模型扩展，建议单独立项。

### [P3] TCC：runTaskWithExitingTxnAsync 中 task 同步抛异常时 Registry 不恢复

- **文件**: `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:179-188`
- **维度**: D3（上下文一致性）
- **证据**:
```java
ITccTransaction old = registry.put(txnGroup, txn);
return thenOnContext(task.apply(txn)).whenComplete((ret, err) -> {
    registry.put(txnGroup, old);      // task.apply 同步抛异常时此行不执行
});
```
- **现状**: `task.apply(txn)` 若同步抛出（而非返回失败 future），`whenComplete` 不会挂接，registry 中残留当前事务；请求内后续同组 TCC 操作会绑定到已结束/无关的事务上（如分支被 `ERR_TCC_TRANSACTION_NOT_ALLOW_START_BRANCH` 拒绝）。
- **风险**: 触发面窄（需用户 task 同步抛异常），且泄漏随请求级 IContext 结束而终止。
- **建议**: 用 try/catch 将同步异常转为失败 future 后再进入链式调用（对比 `runTaskWithNewTxn` 的 try/finally 正确处理）。
- **误报排除**: 已核对 `runTaskWithNewTxn`（246-262 行）有 try/finally 而 Exiting 版本缺失，确认为遗漏而非设计。

## 其他核对结论（无发现）

- **D7 平台规范**: 三模块无 private 字段注入（全部 setter/构造器注入，且已验证 Nop IoC 支持 `autowireConstructorArgs` 构造器注入）；`@InjectValue` 用法正确；所有 bean 均在 `_vfs` 下 beans.xml 显式定义（nopFileStore/IRetryRecordStore/IRetryScanner/RetryEngineImpl/nopTccEngine/两个拦截器/DefaultTccServiceMetaLoader）；主代码无 bare RuntimeException、无 printStackTrace、无 Thread.sleep；错误码均走模块 ErrorCode + i18n。唯一规范瑕疵见 [P3] Guard.checkArgument 条目。
- **nop-file 上传/下载主链路**: 已知长度与未知长度两条路径的最大长度约束均有效（`LimitedInputStream` 超限抛 `ERR_IO_STREAM_SIZE_EXCEED_LIMIT`，无静默截断）；`saveFile` 的临时资源在 finally 中关闭与删除，异常路径回滚已保存资源；`getFileResource/getRecord` 的越权访问检查（bizObjName/objId/fieldName 比对）正确。
- **nop-retry 幂等**: `UK_RETRY_IDEMPOTENT_ID(namespaceId,groupId,idempotentId)` 唯一索引兜底了 find-then-insert 竞态（并发插入第二笔报约束冲突，不产生重复记录）；`moveToDeadLetter` 删除原 record 行使同 idempotentId 可重新提交（代码注释声明为既定裁定，attempt 行成为孤儿属已知取舍，不计发现）。
- **nop-retry 计数边界**: 立即重试与延迟重试的 retryCount 递增、`retryCount >= maxRetryCount` 进死信、指数退避 `1L << min(retryCount-1,10)` 封顶 + maxInterval 封顶 + jitter，均已核对无越界/无限循环（除前述坏记录循环）。
- **nop-tcc 空回滚/分支登记**: `runBranchTryAsync` 先 `beginTryAsync` 持久化 TRYING 再执行业务（防悬挂登记），`finishTryAsync` 对 safeFail/未知异常区分 TRY_FAILED/TRY_UNKNOWN，`shouldStartBranch` 的 inbound/initiator 规则与 `aggregateConfirmBranchStatus` 对 cancelled 分支的防御性抛错均已核对。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。`task.apply(txn)` 同步异常时先恢复 `registry.put(txnGroup, old)` 再抛出（与 `runTaskWithNewTxn` 的 try/finally 对齐）。
