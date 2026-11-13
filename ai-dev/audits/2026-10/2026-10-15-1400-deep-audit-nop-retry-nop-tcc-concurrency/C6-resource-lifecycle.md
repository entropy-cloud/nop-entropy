# C6 资源生命周期与泄漏 — nop-retry + nop-tcc 第 1 轮初审（盲审）

- 审计单元：`nop-retry`（8 jar）+ `nop-tcc`（9 jar），合计 82 files / 7,972 行手写代码
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15
- 盲审纪律：未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下任何文件；仅以当前 live code 为准；`_` 前缀生成文件不作为发现对象（ORM 模型中 `nop-retry/model/nop-retry.orm.xml` 为源模型，仅引用其 UK 定义作证据）。

---

## 检查范围说明（资源获取点清单）

### 形态预注核对（与任务书一致）

基线 `_tmp/cra-baseline-nop-retry-nop-tcc.txt` 的 `== C6: resource acquisition ==` 与 `== C6: closeable ==` 两段均为空：两模块主代码**零 AutoCloseable、零自建线程池/队列/信号量**。锁载体仅 retry 侧 `RetryScannerImpl` 的 `synchronized start/stopScanning`（方法级，start/stop 配对，无嵌套）。retry 调度走 `GlobalExecutors.globalTimer().executeOn(globalWorker())`（平台共享设施）；tcc 全部为 CompletableFuture 链 + 外部驱动过期扫描，主代码零锁原语——资源面为 DB 状态迁移守卫（`updateByQuery` 按 fromStatus CAS）与在途补偿竞争。本维度按此形态盘点，不虚构「分支锁」类资源。

### retry 侧资源获取点

| # | 资源 | 获取点 | 释放/配对点 |
|---|------|--------|------------|
| R1 | 扫描定时任务（scheduleWithFixedDelay 句柄） | RetryScannerImpl.java:96 | stopScanning cancel(false)（:107-109），@PostConstruct/@PreDestroy 经 LifeCycleSupport 驱动 |
| R2 | 立即重试定时器（executor.schedule 句柄） | RetryEngineImpl.java:407 | **无句柄保留，无取消路径**（C6-010） |
| R3 | NopRetryAttempt 行（RUNNING 状态插入） | RetryEngineImpl.java:456 | 正常路径终态回写 :487；崩溃/挂死/终态写失败路径**无对账**（C6-002） |
| R4 | NopRetryRecord 行（PENDING 插入） | RetryEngineImpl.java:179 | 删除仅经 moveToDeadLetter（事务内，原子）与 OVERWRITE deleteRecord（**非原子**，C6-003） |
| R5 | NopRetryDeadLetter 行 | RetryRecordStoreImpl.java:285-310 | 与 record 行删除同事务（原子配对，通过） |
| R6 | 调用方/中间 CompletableFuture | RetryEngineImpl.java:336,405 等 | 正常完成；executeImmediateRetry 内层异常路径**永不完成**（C6-001） |
| R7 | executor 字段（GlobalExecutors 包装视图） | RetryEngineImpl.java:621-626 | 平台设施共享实例，无自有生命周期（通过） |
| R8 | DB 账本保留面：COMPLETED record / attempt / dead letter | 全模块 | **无任何 retention/清理路径**（C6-004，F4） |

### tcc 侧资源获取点

| # | 资源 | 获取点 | 释放/配对点 |
|---|------|--------|------------|
| T1 | TccTransactionRegistry 条目（registry.put） | TccEngine.java:188,211,239,276 | 4/4 配对 restore（catch+whenComplete :197,204 / try-finally :216,287）（通过，见配对表） |
| T2 | TccContext thread-local | TccRpcServiceInterceptor.java:49；TccGatewayInterceptor.java:77,95 | 桥接路径 whenComplete removeCurrent（:50-51，同步异常窗口→C6-012）；网关三分支恢复（:102-111） |
| T3 | NopTccRecord 行（TRYING 插入） | TccTransaction.beginAsync → TccRecordStore:151-158 | endAsync 终态 CAS（updateTccStatusAsync :162-197）；崩溃恢复对称续跑（声明契约，通过） |
| T4 | NopTccBranchRecord 行（TRYING 插入） | TccBranchTransaction.beginTryAsync → TccRecordStore:208-218 | 补偿终态 CAS（:222-283）；物理删除仅经 removeCompletedRecords——**无生产驱动**（C6-005/C6-009） |
| T5 | 补偿 RPC 在途句柄（invokeAsync future） | TccRunner.java:82,108 | finish*Async 落库收尾；checkExpiredTransactions 超时路径**丢弃句柄**（C6-006）；finishCancelAsync NPE 路径 future 永不完成（C6-007） |
| T6 | checkExpiredTransactions 循环（外部线程驱动） | TccEngine.java:315-343 | canceller 逐条检查；InterruptedException 恢复中断位（:335-337）（通过） |
| T7 | DB 账本保留面：已放弃非终态 record/branch | TccRecordStore.java:299-303 | 仅 removeCompletedRecords 删终态；放弃记录永不清理（C6-009，F4，声明边界标注） |

---

## 发现条目

### [nop-retry-nop-tcc-C6-001] executeImmediateRetry 完成回调同步异常导致调用方 future 永不完成且异常静默丢弃
- **发现ID**：nop-retry-nop-tcc-C6-001
- **严重度**：P1
- **缺陷族**：F8（资源清理不对称：future 在错误路径永不释放）+ F9（静默吞异常）
- **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:336-391`（关键 344-357、359-361、371-385）
- **证据片段**：
```java
// RetryEngineImpl.java:336-361
CompletableFuture<ApiResponse<?>> result = new CompletableFuture<>();
...
doExecute(record, request, cancelToken)
        .whenComplete((response, ex) -> {
            if (ex != null) {
                ...
                } else {
                    handleExecutionFailure(record, policy, ex, task);   // (a) 内部 updateRecord/moveToDeadLetter 同步抛出
                    result.completeExceptionally(ex);                   //     —— (a) 抛出则本行不执行
                }
        } else if (response != null && response.isOk()) {
            handleExecutionSuccess(record, policy, task);               // (b) 内部 updateRecord/triggerCallback 同步抛出
            result.complete(response);                                  //     —— (b) 抛出则本行不执行
```
```java
// RetryEngineImpl.java:391 —— 方法返回的是 result，而非 whenComplete 的返回 stage
        return result;
```
`doExecute(...).whenComplete(...)` 的返回 stage（捕获了回调异常的那一个）被**整体丢弃**，无人观察、无人日志。
- **资源与泄漏链**：路径 = 立即重试阶段（`immediateRetryCount>0` 的 policy）任意一次尝试完成时，`handleExecutionSuccess`→`recordStore.updateRecord`、`handleExecutionFailure`→`updateRecord`/`moveToDeadLetter`、`triggerCallback`→`executeTask`→`loadPolicy`/`saveRecord` 任一同步抛出（DB 抖动/约束冲突即可）→ `result` 既不 complete 也不 completeExceptionally → 调用方拿到的 future **永久挂起**（`IRetryTask.callAsync` 的提交方若 `syncGet` 则请求线程永久阻塞）。资源 = 1 个永不完成的 CompletableFuture 及其全部依赖链；频率 = 每次完成期 DB 写失败即 1 次。
- **影响分析**：错误路径资源泄漏（挂起的请求句柄/线程）+ F9（异常零日志，排障不可见）。对比 `executeSingleAttempt`（:429-446）将 whenComplete stage 直接返回给调用方，异常可传播、不挂起——同文件内两条路径行为不对称，佐证本条为缺陷而非设计。
- **与已声明边界的区分**：已声明契约（DISCARD/OVERWRITE、SUSPENDED、COMPLETED 幂等重放、超时双参、回调开关）均不覆盖「完成回调内同步异常」场景；无任何声明豁免该路径。
- **误报校准核对**：非 try-with-resources 覆盖路径；非测试专用代码；非平台设施自身实现（是模块对平台 future 的使用方式缺陷）。
- **修复方向**：将 `handleExecutionSuccess/Failure`、`scheduleImmediateRetry` 包入 try/catch，catch 中 `result.completeExceptionally(e)` 并 LOG.error；或统一改为 `return doExecute(...).whenComplete(...)` 形态。
- **置信度**：高（代码路径确定，抛点均为真实同步 DB 调用）
- **交叉引用**：复合后果（副作用重复）见维度集外线索 CL-001；与 C6-008 复合（scanner join 挂起）；与 C3/C1 报告的同文件异步链发现可能重叠，待合稿去重。
- **复验建议**：构造 immediateRetryCount>0 + MockRpcServiceInvoker 成功返回 + recordStore.updateRecord 抛异常的用例，断言调用方 future 在有限时间内完成且异常可见。
- **复核状态**：保留（P1 维持）；新发现；逐行核实 ：336-391 回调内 5 个同步 DB 抛点（updateRecord:346/374、handleExecutionFailure:356/384、handleExecutionSuccess:360→updateRecord:501/triggerCallback→executeTask→saveRecord:179）任一抛出即 result 永不完成且 whenComplete 返回 stage（:391 return result，捕获 stage 被丢弃）零观测——两条件（opt-in policy + 单次 DB 写失败）非罕见合取，时序每步可信；同族站点排查完备（executeSingleAttempt 直接返回 stage 不挂起、scheduleImmediateRetry 的 lambda 受 BindScheduledExecutor.executeOn catch Throwable 保护）；备注：DEFAULT_IMMEDIATE_RETRY_COUNT=0，需显式配置 immediateRetryCount>0。

### [nop-retry-nop-tcc-C6-002] 崩溃/挂死后 RUNNING attempt 行永久残留，经 UK_RETRY_ATTEMPT_RECORD_NO 碰撞将记录永久砖化（不可恢复、不可死信化）
- **发现ID**：nop-retry-nop-tcc-C6-002
- **严重度**：P1
- **缺陷族**：F8（acquire 无 release/对账：attempt 行 RUNNING 获取后无失败路径收敛）+ F4（残留行阻塞活体记录）
- **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:454-456,487`；`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:134,142-149`；`nop-retry/model/nop-retry.orm.xml:364-368`；`nop-retry/nop-retry-dao/src/main/java/io/nop/retry/dao/_NopRetryDaoConstants.java:34,49,54`
- **证据片段**：
```java
// RetryEngineImpl.java:454-456 —— attempt 行以 RUNNING 落库
NopRetryAttempt attempt = recordStore.newAttempt(record);
attempt.setStatus(NopRetryConstants.RETRY_ATTEMPT_STATUS_RUNNING);
recordStore.saveAttempt(attempt);
// :487 —— 仅 RPC 正常回到 whenComplete 时才写终态；崩溃/挂死/此处写失败则 RUNNING 永久
recordStore.saveAttempt(attempt);
```
```java
// RetryRecordStoreImpl.java:134 —— attemptNo 派生自 retryCount（失败时才在 handleExecutionFailure 中 +1）
attempt.setAttemptNo((record.getRetryCount() != null ? record.getRetryCount() : 0) + 1);
```
```xml
<!-- nop-retry/model/nop-retry.orm.xml:364-368 -->
<!-- Unique constraint on (recordId, attemptNo) combination -->
<index name="UK_RETRY_ATTEMPT_RECORD_NO" unique="true">
    <column name="recordId"/>
    <column name="attemptNo"/>
</index>
```
`_NopRetryDaoConstants.java`：`RETRY_ATTEMPT_STATUS_RUNNING = 1`（:34）、`STOPPED = 4`（:49）、`CANCELED = 5`（:54）——grep 全主代码，STOPPED/CANCELED **零使用**，无任何对账/收敛路径。
- **资源与泄漏链**：路径 = (1) JVM 在 RPC 在途时崩溃/重启，或 (2) `invokeAsync` 返回的 future 永不完成（下游无超时），或 (3) :487 终态写失败 → attempt#N 以 RUNNING 永久残留，且 `retryCount` 未递增（只在 `handleExecutionFailure`:512-513 递增）→ 租约（默认 retryingTimeoutMs=600s）到期后扫描器重拾记录 → `newAttempt` 再次产出 attemptNo=N → `saveAttempt` 首插命中 `UK_RETRY_ATTEMPT_RECORD_NO` 唯一键冲突同步抛出（`RetryRecordStoreImpl.saveAttempt`:142-149 无 duplicate-key 容错，与 record 侧 `resolveInsertRaceWinner` 的兜底不对称）→ 记录停在 RETRYING、无状态迁移、无 retryCount 递增、无死信 → **每个租约周期重复同样的 UK 失败，永久循环**。运维 `resume`（置 PENDING）也无法绕过 attempt 行碰撞。资源 = 每次崩溃/挂死 1 条记录永久不可用 + 每周期 1 次无效扫描与 1 条异常日志。
- **影响分析**：错误/崩溃路径上的记录级永久资源占用：该 (namespace,group,idempotentId) 键的任务再也无法执行也无法死信化（同键新提交被 COMPLETED 重放/阻塞策略拦截），只能人工删 attempt 行。频率=每次 mid-attempt 崩溃或 RPC 永久挂起，工程上完全可发生。
- **与已声明边界的区分**：moveToDeadLetter 留下孤儿 attempt 行是已声明边界（RetryRecordStoreImpl:306-308 注释）；但「RUNNING attempt 无对账 + UK 碰撞砖化活记录」不在任何声明内。
- **误报校准核对**：非低频路径粗粒度锁；非有界声明池；泄漏链具体（路径×资源×频率均可写出）。
- **修复方向**：(1) saveAttempt 首插对 duplicate-key 容错（同 record 侧 resolveInsertRaceWinner 形态：查既有 RUNNING 行，继承或推进其状态）；或 (2) doExecute 重试前先关闭同 recordId 的遗留 RUNNING attempt（置 STOPPED）；或 (3) attemptNo 改取 max(attemptNo)+1。
- **置信度**：高（UK 定义于源 ORM 模型，代码路径逐行核实）
- **交叉引用**：与 C1 报告的记录状态机发现可能交叉；stopScanning 后在途 attempt 亦留 RUNNING（并入本条）。
- **复验建议**：集成测试：插入 RUNNING attempt#1 后调用 scanner 重拾路径，断言记录可继续推进（不因 UK 卡死）；崩溃恢复语义测试。
- **复核状态**：保留（P1 维持，附子项修正）；新发现；砖化链路径 (1) 崩溃与 (2) future 永不完成逐行核实（attemptNo=retryCount+1 仅在 handleExecutionFailure:512-513 递增、UK_RETRY_ATTEMPT_RECORD_NO 实际 :365-368、saveAttempt:142-149 无 duplicate-key 容错 vs record 侧 resolveInsertRaceWinner:222-241 不对称、STOPPED/CANCELED 仅 :49/:54 定义零使用、resume:147-149 仅 SUSPENDED 可用无法绕过）——时序 7 步每步代码可信；**子项修正**：原文路径 (3)「:487 终态写失败」不产生 UK 碰撞（该异常路由 handleExecutionFailure 使 retryCount+1，下次 attemptNo=N+1 无碰撞），其后果归 C6-016，RUNNING 残留本身成立但非砖化路径；F3 完备性：全仓唯一约束仅 2 处（:264/:365）、tcc ORM 零 unique（grep 核实），attempt 表外无同族站点。

### [nop-retry-nop-tcc-C6-003] OVERWRITE 阻塞策略 delete+insert 非原子：半个成功后任务蒸发且无死信
- **发现ID**：nop-retry-nop-tcc-C6-003
- **严重度**：P1
- **缺陷族**：F8（删除与替换不对称、无原子包络）+ 半个成功（C5 形态，本维度以 DB 记录生命周期登记）
- **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:255-260`；`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:246-249,274-278`
- **证据片段**：
```java
// RetryEngineImpl.java:255-260 —— 先删后插，两行之间无事务包络
case NopRetryConstants.BLOCK_STRATEGY_OVERWRITE:
    LOG.info("nop.retry.block-strategy-overwrite:recordId={}", existingRecord.getSid());
    recordStore.deleteRecord(existingRecord);        // (1) 无 @Transactional，自动提交
    NopRetryRecord newRecord = recordStore.newRecord(task, request);
    recordStore.saveRecord(newRecord);               // (2) REQUIRES_NEW 独立事务，失败不再回滚 (1)
    return executeWithRetry(newRecord, policy, request, cancelToken, task);
```
```java
// RetryRecordStoreImpl.java:246-249 —— deleteRecord 无事务注解
@Override
public void deleteRecord(NopRetryRecord record) {
    getRecordDao().deleteEntityDirectly(record);
}
// :274-278 —— saveRecord 为 REQUIRES_NEW，与 (1) 不可能同事务
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
@Override
public void saveRecord(NopRetryRecord record) {
```
- **资源与泄漏链**：路径 = 同幂等键已有未完成记录时以 OVERWRITE 重复提交 → (1) 旧 record 行删除并提交 → (2) 新行插入失败（确定性触发：新 requestPayload 超列宽/超限约束时必败；偶发触发：DB failover、锁超时）→ 旧任务已被删除、新记录未建立、无死信快照（死信路径只服务 maxRetry/bizFatal/deadline）→ 该幂等键任务**整体蒸发**，仅调用方收到一次异常。资源 = 1 条 record 行（含 payload 快照）丢失。频率 = OVERWRITE + (2) 失败的组合；约束类失败是确定性可复现的。
- **影响分析**：错误路径上的半个成功：删除已提交而替换未完成，违背模块自身「死信保留全量快照」的可恢复性设计（nop-retry.md 执行语义）。与 DISCARD 分支的只读行为对比，OVERWRITE 是唯一的破坏性分支，却无原子保护。
- **与已声明边界的区分**：声明契约只规定 OVERWRITE 语义（"删除已有未完成记录并以新请求替换"），未声明删除/替换可非原子；COMPLETED 记录不咨询 OVERWRITE 的裁定（:194-201 注释）与本条无关。
- **误报校准核对**：泄漏链具体（确定性触发点可写）；非 try-with-resources 可覆盖类别；不是「删除是 OVERWRITE 语义故无害」——语义允许删除旧记录，但未授权删除后新记录建立失败时什么都不留。
- **修复方向**：为 delete+save 包一层 `@Transactional` store 方法（如 `replaceRecord`）；或先插新行（临时状态）成功后再删旧行；或 save 失败补偿性恢复旧记录。
- **置信度**：高
- **交叉引用**：C5 维度若在审「半个成功」应以本条为输入；与 C6-004（账本保留）相关。
- **复验建议**：用超长 payload + OVERWRITE 的单测断言：旧记录仍存在或存在等效死信快照，不允许静默蒸发。
- **复核状态**：保留（P1 维持）；新发现；核实 deleteRecord 实际 ：247-249 无 @Transactional（原文 ：246-249 轻微漂移）、saveRecord REQUIRES_NEW :274-278、RetryEngineImpl 全类与 RetryTaskImpl.callAsync 链路均无事务包络——delete 自动提交与 REQUIRES_NEW 插入不可能同事务，约束类失败确定性复现，半个成功后任务整体蒸发且无死信面；DISCARD 只读对比与 moveToDeadLetter 已声明孤儿边界（:306-309 注释）均核实，本条不在任何声明内。

### [nop-retry-nop-tcc-C6-004] retry 侧 DB 账本零保留/清理路径：COMPLETED record、attempt、dead letter 三表无界增长（F4，受 DB 约束单独标注）
- **发现ID**：nop-retry-nop-tcc-C6-004
- **严重度**：P2
- **缺陷族**：F4（无界账本，DB 账本面）
- **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java`（全文无任何 delete/清理入口，除 :247-249 deleteRecord 与 :285-310 moveToDeadLetter）；`grep -rn "delete|clean|purge|ttl" nop-retry/{service,dao}/src/main` 零命中（仅 xbiz 生成 CRUD 的 admin 写入口）
- **证据片段**：
```java
// RetryRecordStoreImpl.java:499-502 —— 成功路径仅置 COMPLETED，无任何清理/归档
private void handleExecutionSuccess(NopRetryRecord record, NopRetryPolicy policy, IRetryTask task) {
    record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_COMPLETED);
    recordStore.updateRecord(record);
    LOG.info("nop.retry.task-completed:recordId={}", record.getSid());
```
```java
// RetryRecordStoreImpl.java:306-309 —— 死信化只删单条原记录，attempt 成孤儿（此孤儿化已声明）
// 死信保存成功后删除原 record 行，使同一 idempotentId 可以重新提交（plan 342 裁定）。
// 副作用：deadLetter.record 关系悬空（无 FK/cascade），attempt 行成为孤儿；
getRecordDao().deleteEntityDirectly(record);
```
tcc 侧有 `removeCompletedRecords`（TccRecordStore:334）而 retry 侧无任何对应物——模块间不对称亦佐证缺口。
- **资源与泄漏链**：路径 = 每个成功任务永久留 1 条 COMPLETED record（含全量 requestPayload JSON 快照，可能很大）+ N 条 attempt 行；每次死信化永久留 1 条 dead letter + 孤儿 attempts；无 TTL/批量清理/归档。泄漏量 = 与业务提交量线性，单行 KB 级；频率 = 每次提交。是慢速无界账本（DB 约束兜底为磁盘，非代码）。
- **影响分析**：长驻系统表膨胀、扫描查询（fetchPendingRecords 全表过滤）退化；payload 快照放大存储成本。与 C6-002 叠加时，RUNNING 残留行同时是活体障碍。
- **与已声明边界的区分**：孤儿 attempt（死信化后）已声明；COMPLETED record 与 dead letter 的零保留策略**未在任何 owner doc/注释中声明**（对比 tcc 的 cleanCompletedTransactions 注释明确契约）。
- **误报校准核对**：DB 账本按任务书要求单独标注（F4，受 DB 约束）；判 P2 而非 P1：单行小、增长慢、不触主路径错误。
- **修复方向**：对齐 tcc 侧形态提供 `cleanCompletedRecords(retentionTime)`（只删 COMPLETED 与超期死信），并声明保留契约；或接入平台清理任务。
- **置信度**：高
- **交叉引用**：C6-002、C6-009；F4 族归并点。
- **复验建议**：确认 nop-retry 无隐藏的 ops/clean 接口（已 grep 核实）；补契约声明或清理实现。
- **复核状态**：保留（P2 维持）；新发现；独立 grep 复核证实 IRetryRecordStore 接口 16 方法无任何清理入口、retry 主代码 delete/clean/purge/ttl/retention 零命中、与 tcc 侧 removeCompletedRecords（TccRecordStore:334）不对称成立——F4 DB 账本面判 P2 符合任务书单独标注要求。

### [nop-retry-nop-tcc-C6-005] TCC 过期扫描与完结清理均无生产驱动：缺省部署下 TRYING 残留与参与者预留资源永不回收
- **发现ID**：nop-retry-nop-tcc-C6-005
- **严重度**：P2
- **缺陷族**：F4（账本滞留）+ F8（回收机制缺位）；同时是声明边界的部署面缺口
- **位置**：`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:315,351`；`nop-tcc/nop-tcc-core/src/main/resources/_vfs/nop/tcc/beans/tcc-core-defaults.beans.xml:12`；grep 证据：`checkExpiredTransactions`/`cleanCompletedTransactions` 的调用点全部位于 `src/test`（TestTccEngine*.java），main 代码与全部 beans.xml 零调用
- **证据片段**：
```java
// TccEngine.java:314-315, 350-351 —— 两个回收入口均为 public API，无内部调度器调用
@Override
public void checkExpiredTransactions(long expireGap, int maxRetryCount, ICancelToken canceller) {
...
@Override
public void cleanCompletedTransactions(long retentionTime) {
```
```xml
<!-- tcc-core-defaults.beans.xml:12 —— 引擎 bean 无任何 start/schedule 属性 -->
<bean id="nopTccEngine" class="io.nop.tcc.core.impl.TccEngine" />
```
- **资源与泄漏链**：路径 = 调用方进程在 TRY/CONFIRM/CANCEL 之间崩溃 → 全局/分支记录停留在中间态 → 依赖 `checkExpiredTransactions` 恢复续跑（P1-3.8-004 契约）→ 但平台（模块+全部 beans 装配）不提供任何驱动者，任务书亦声明「外部驱动过期扫描」为模块形态 → 缺省部署该函数永不执行：中间态记录、分支行、**参与者 TCC 预留资源**（try 阶段冻结的业务资源）永久悬挂。`cleanCompletedTransactions` 同理永不执行（见 C6-009）。泄漏量 = 每次 TCC 链路中途崩溃 1 组记录+外部资源；频率 = 崩溃率。
- **影响分析**：崩溃恢复对称续跑的状态机代码（本单元重点修复成果）在缺省装配下不可达，等于恢复能力未交付；对比 retry 侧扫描器有完整 LifeCycle 装配（app-engine.beans.xml + @PostConstruct），tcc 侧形态不对称。
- **与已声明的区分**：「外部驱动」是任务书声明的模块形态——故不判 P1 缺陷，判 P2 边界配置缺陷：平台以可运行产品形态交付时未给出参考 driver（job/quartz/cron 任一）或文档指引（nop-tcc.md 未写明必须外部驱动及其参数）。
- **误报校准核对**：非主路径泄漏（崩溃路径）；具体化程度高（装配与调用面逐点核实为空）。
- **修复方向**：提供 `nop-tcc-schedule` 参考装配（如复用 nop-job 适配）或在 nop-tcc.md 显著声明驱动契约（expireGap/maxRetryCount/retentionTime 推荐值与驱动频率）。
- **置信度**：高
- **交叉引用**：C6-006、C6-009 依赖本条所述驱动存在才谈得上运行时行为。
- **复验建议**：在部署文档/模块文档补驱动契约；增加一个最小 driver 装配样例。
- **复核状态**：保留（P2 维持）；新发现；独立 grep 复核证实 checkExpiredTransactions/cleanCompletedTransactions 在 main 代码仅 TccEngine:315/:351 定义与 ITccEngine:48/:55 接口声明、全部 beans.xml 零调用、tcc 主代码零 schedule 原语、tcc-core-defaults.beans.xml:12 裸 bean——对比 retry 侧 app-engine.beans.xml+LifeCycleSupport 完整装配的不对称成立；nop-tcc.md 崩溃恢复语义节未写外部驱动契约，「边界配置缺陷」P2 判级恰当。

### [nop-retry-nop-tcc-C6-006] checkExpiredTransactions 单条补偿超时后丢弃 future：在途补偿不可取消、记录可被并发二次补偿
- **发现ID**：nop-retry-nop-tcc-C6-006
- **严重度**：P2
- **缺陷族**：F8（在途句柄释放不对称：超时路径放弃但不取消）+ C1 竞态面（双补偿，依赖声明边界缓解）
- **位置**：`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:328-340`；`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java:82,108`（补偿 RPC 均传 null cancelToken）
- **证据片段**：
```java
// TccEngine.java:328-340
try {
    // 单条记录的补偿等待加上限，取expireGap作为超时时间：
    // 单个补偿挂死时跳过该记录继续处理下一条，不再永久阻塞整个调度循环
    checkExpiredAsync(record).toCompletableFuture().get(expireGap, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    LOG.error("TccEngine.checkExpiredTransactions compensation timeout for txnId={}",
            record.getTxnId(), e);          // 仅日志：future 被丢弃，底层补偿链继续运行
}
```
```java
// TccRunner.java:108 —— cancel RPC 无 cancelToken/超时句柄可取消
serviceInvoker.invokeAsync(branchRecord.getServiceName(), branchRecord.getCancelMethod(), branchRecord.getRequest(), null)
```
- **资源与泄漏链**：路径 = 某事务补偿 RPC 挂死超过 expireGap → `.get` 抛 TimeoutException，future 被丢弃但 `endAsync` 补偿链仍在后台运行且**无任何取消手段**（invokeAsync 传 null token）→ 记录的 expireTime 已被 fetchExpiredRecords 推进到 nextCheckTime（TccRecordStore:313-316）→ nextCheckTime 到期后同一记录被再次拾取 → 第二条补偿链与第一条**并发运行**，依赖 `updateTccStatusAsync` 的 fromStatus CAS（TccRecordStore:188）与参与者幂等去重兜底。资源 = 每次超时泄漏 1 条不可取消的在途补偿链（网络连接+CF 链），持续到 RPC 自行超时/返回。
- **影响分析**：有界（每挂死记录 1 条）但不可观测（future 丢弃后其结果无人消费，日志只有 TimeoutException 一行）；双 confirm/cancel 到达参与者是 TCC 幂等契约的声明边界（声明中「补偿幂等」为参与者义务），故双补偿本身按声明边界处理，本条只就「句柄放弃不取消、结果不可观测」登记。
- **与已声明边界的区分**：单条补偿加超时防调度循环阻塞已在注释中声明（:329-330），属已声明设计；未声明的是放弃后既不取消也不追踪结果。
- **误报校准核对**：非平台设施自身实现；理论前提（RPC 挂死超 expireGap）低频，故 P2。
- **修复方向**：记录 future 句柄（如 per-txnId 在途表），超时后至少 DEBUG 观测其最终结果；为补偿 RPC 传入与 expireGap 对齐的 cancelToken/超时。
- **置信度**：中高（代码确定；双补偿的实际危害依赖参与者实现，未在单元内）
- **交叉引用**：与 C1 报告的 CAS 守卫评估交叉；C6-005（无驱动则本条不可达）。
- **复验建议**：单测：补偿挂死>expireGap 后断言调度循环继续且后续重拾时行为符合幂等预期。
- **复核状态**：保留（P2 维持）；新发现；核实 TccEngine:328-340 超时仅 LOG.error、TccRunner:82/:108 双补偿 RPC 均传 null cancelToken、fetchExpiredRecords TccRecordStore:313-316 推进 expireTime、:188 fromStatus CAS 兜底——单条超时已声明（:329-330 注释）与放弃后不取消不追踪的未声明面区分准确；触发前提（RPC 挂死超 expireGap）低频，P2 恰当。

### [nop-retry-nop-tcc-C6-007] finishCancelAsync 对 response==null 无防护：NPE 在回调中抛出后 future 永不完成，全局 cancel 链挂起、registry/上下文滞留
- **发现ID**：nop-retry-nop-tcc-C6-007
- **严重度**：P2
- **缺陷族**：F8（future 永不完成→CF 链与 registry 条目滞留）+ F9（NPE 被丢弃的 stage 吞掉，零日志）
- **位置**：`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccBranchTransaction.java:153-163`（NPE 点 :158）；`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java:108-121`
- **证据片段**：
```java
// TccBranchTransaction.java:153-163 —— 兄弟方法 finishTryAsync(:70) 用 `response == null ||`、
// finishConfirmAsync(:118) 用 `response != null &&`，仅 finishCancelAsync 在 :158 裸解引用
if (response != null && response.isBizSuccess()) {
    return getRepository().updateTccBranchStatusAsync(branchRecord,
            timeout ? TccStatus.TIMEOUT_SUCCESS : TccStatus.CANCEL_SUCCESS, ex);
}

if (Boolean.TRUE.equals(response.getBizFatal())) {   // ← response==null 时 NPE
```
```java
// TccRunner.java:109-119 —— NPE 抛在 whenComplete 回调里，被丢弃的返回 stage 吞掉，
// future(:107) 永不 complete → cancelAllAsync.waitAll 永不返回
serviceInvoker.invokeAsync(...)
        .whenComplete((ret, ex) -> {
            branchTxn.finishCancelAsync(timeout, ret, ex).whenComplete((ret2, ex2) -> {
```
- **资源与泄漏链**：路径 = cancel RPC 以 ex==null、ret==null 完成（取决于 IRpcServiceInvoker 契约，标准实现通常不给 null，故为理论前提）→ `finishCancelAsync` NPE → 异常进入被丢弃的 whenComplete stage → `runBranchCancelAsync` 的 `future` 永不完成 → `cancelAllAsync.waitAll`、`doCancelAsync`、`endAsync` 整链挂起 → `runTaskWithNewTxnAsync` 外层 whenComplete（TccEngine:253-255）不执行 → **TccTransactionRegistry 条目不恢复**、上下文属性滞留至上下文消亡；全局记录停 CANCELLING 直到超时重扫。资源 = 1 条挂起 CF 链 + 1 个 registry 条目/请求。
- **影响分析**：与 C6-001 同型的「回调同步抛→future 永不完成」缺陷在 tcc 侧的镜像；三处 finish* 方法中独有的 null 不对称使其更像笔误而非契约假设（try/confirm 两个兄弟方法都做了 null 防护）。
- **与已声明边界的区分**：任务书声明 tcc 资源面为「DB 状态迁移守卫与在途操作竞争」，本条正是在途操作（在途回调）的异常路径；无声明豁免 null response。
- **误报校准核对**：因 ret==null 可达性依赖 invoker 契约，降 P2 标注「理论风险，未具体化到任一内置 invoker」；但修复成本与不对称证据充分。
- **修复方向**：:158 改为 `if (response != null && Boolean.TRUE.equals(response.getBizFatal()))`；顺带审计 TccRunner 三个 run* 方法对「回调抛同步异常」的通用防护。
- **置信度**：中（缺陷确定；触发前提依赖 invoker 契约）
- **交叉引用**：与 C6-001 同族；与 C2（registry 恢复）评估交叉。
- **复验建议**：单测：Mock invoker 返回 (null, null)，断言链在有限时间完成且分支状态落 CANCEL_FAILED/TIMEOUT_FAILED。
- **复核状态**：保留（P2 维持，理论标注）；新发现；核实 :158 裸解引用 vs 兄弟方法 finishTryAsync:70（`response == null ||`）/finishConfirmAsync:118（`response != null &&`）的不对称、TccRunner:108-119 丢弃 whenComplete stage → future 永不完成 → waitAll/endAsync 整链挂起 → TccEngine:253-255 registry 不恢复；**子项明细**：触发前提 = invoker 以 (null,null) 正常完成——内置 ClusterRpcServiceInvoker/HttpRpcServiceInvoker 均构造 ApiResponse 非空，可达性未具体化到任一内置 invoker，按 campaign 规则落「理论风险未具体化」P2 标注位（兄弟不对称 + 一行修复 + 挂链后果支撑不降 P3）。

### [nop-retry-nop-tcc-C6-008] scanner 批处理以无超时 allOf().join() 阻塞共享 globalWorker 线程：单条挂死即扫描线停摆
- **发现ID**：nop-retry-nop-tcc-C6-008
- **严重度**：P2
- **缺陷族**：F8（在途句柄无包络释放）+ C4 形态（共享池线程占用）
- **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:635-649`；`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:96-101,165-167`
- **证据片段**：
```java
// RetryEngineImpl.java:635-648 —— doStart 注册的 processor 在扫描任务内同步 join 全批
retryScanner.startScanning(records -> {
    CompletableFuture<?>[] futures = records.stream()
            .map(record -> executeRetryFromScanner(record, null)   // cancelToken=null，无超时包络
                    ...
            .toArray(CompletableFuture[]::new);
    // 等待所有 record 执行完成
    CompletableFuture.allOf(futures).join();                        // 无超时
});
```
```java
// RetryScannerImpl.java:165-167 —— 扫描任务投递到全局共享有界池
protected IScheduledExecutor getExecutor() {
    return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
}
```
- **资源与泄漏链**：路径 = 批内任一重试 RPC 的 future 永不完成（scanner 路径 cancelToken=null 且无超时包络；若下游 invoker 无内建超时）→ `join()` 永久阻塞 → (1) `scheduleWithFixedDelay` 语义下后续扫描全部停摆（重试积压无人处理），(2) 有界 `globalWorker`（CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE）被占 1 线程。资源 = 1 个共享池线程 + 扫描线 liveness；频率 = 每次永久挂死 1 份。
- **影响分析**：与 C6-001 复合（immediateRetry 阶段挂起的 result future 同样锚死 join）；即使下游普遍有超时，join 也使批处理退化为串行墙钟（max 单条时延×批内失败条数），扫描间隔（默认 5s）名存实亡。
- **与已声明边界的区分**：「retry 调度走 GlobalExecutors」是声明形态（通过）；「批内 join 无超时、cancelToken=null」未声明，非平台设施自身缺陷（是使用方式）。
- **误报校准核对**：挂死前提依赖下游超时配置，未具体化到必现路径，故 P2 而非 P1。
- **修复方向**：`join()` 改带超时（如 deadlineTimeoutMs 或 scanInterval 上限），超时记日志并放行下一轮；scanner 路径为 invokeAsync 传入超时 cancelToken。
- **置信度**：中高
- **交叉引用**：C6-001、C4 维度（共享池占用若有专报应归并）。
- **复验建议**：用永不完成的 Mock invoker 验证扫描循环在有限时间后继续取新记录。
- **复核状态**：保留（P2 维持）；新发现；核实 doStart :635-649 无超时 allOf().join()、scanner 路径 :638 cancelToken=null、getExecutor :165-167 投递 globalWorker（GlobalExecutors:85-91 有界池 maxPoolSize 配置核实）——join 永久阻塞同时停摆扫描线（BindScheduledExecutor:107 重臂在 command.run() 返回后才执行）并占 1 有界线程；挂死前提依赖下游无超时 invoker，未具体化到必现路径，P2 恰当。

### [nop-retry-nop-tcc-C6-009] TCC 已放弃（retryTimes 耗尽）非终态记录与分支永久残留且永不补偿，无人工处理工具面（F4，DB-bounded，声明边界标注）
- **发现ID**：nop-retry-nop-tcc-C6-009
- **严重度**：P2
- **缺陷族**：F4（无界账本，DB 账本面，受 DB 约束单独标注）
- **位置**：`nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:299-303,313-316,334-355`
- **证据片段**：
```java
// TccRecordStore.java:301-303 —— 扫描窗口排除：全局 retryTimes 达上限后永不再拾取
query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_status, TccStatus.CONFIRM_SUCCESS.getCode()));
// 重试次数未超过上限，超过上限的事务会被忽略，不再处理
query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_retryTimes, maxRetryCount));
```
```java
// TccRecordStore.java:337-339 —— 清理只删终态
query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_beginTime, minTime));
if (onlyCompleted)
    query.addFilter(FilterBeans.in(NopTccRecord.PROP_NAME_status, TccStatus.getFinishedStatus()));
```
`cleanCompletedTransactions` 注释（TccEngine:352-353）："契约：只删除已成功完结/取消的事务，未知状态保留等待人工处理。"
- **资源与泄漏链**：路径 = 事务补偿反复失败至 retryTimes≥maxRetryCount（默认 nop.tcc.default-max-retry-times=100）→ 被扫描窗口永久排除（声明行为）→ 记录与分支行以非终态（如 CANCEL_FAILED/BEFORE_TIMEOUT）永久留存，`removeCompletedRecords` 按 onlyCompleted=true 永不触及 → 参与者预留资源随记录一起永久悬挂。泄漏量 = 每个放弃事务 2+ 行 + 外部资源；频率 = 持续失败的事务占比；DB-bounded（磁盘），代码层无界。
- **影响分析**：「等待人工处理」是声明契约，但平台未提供任何人工处理面：无按 id 恢复/kill（KILLED 状态主代码零写入点，grep 核实）的运维 API，无「放弃中」记录的告警查询；人工处理目前只能直改 DB。放弃量随 `fetchExpiredRecords` 每轮 bump 持续累积。
- **与已声明边界的区分**：放弃本身已声明（扫描窗口排除 + 清理契约注释 + nop-tcc.md 恢复语义节）；未声明的是放弃后的可观测/可干预手段缺失——按「边界配置缺陷」计 P2 而非缺陷。
- **误报校准核对**：DB 账本面按任务书单独标注；不与「补偿幂等」声明混淆。
- **修复方向**：提供运维入口（kill/re-drive 指定 txnId，写 KILLED 或重置 retryTimes）；为 retryTimes≥上限的非终态记录提供告警查询/看板。
- **置信度**：高
- **交叉引用**：C6-005（清理函数本身无驱动）；KILLED 写入点缺失可与 C9（可观测性）维度线索互认。
- **复验建议**：确认主代码确无 KILLED 写入（已 grep）；核对 ops 文档是否有放弃事务处理指引（当前无）。
- **复核状态**：保留（P2 维持）；新发现；独立 grep 复核证实 KILLED 仅 TccStatus:68/:82/:109 定义与 TccRunner:161 守卫读取、主代码零写入点，fetchExpiredRecords :301-303 扫描窗口排除与 removeCompletedRecords :338-339 onlyCompleted 过滤成立——放弃语义已声明（nop-tcc.md 恢复语义节 + TccEngine:352-353 契约注释）、可观测/可干预手段缺失未声明，「边界配置缺陷」P2 判级恰当。

### [nop-retry-nop-tcc-C6-010] 立即重试定时器句柄丢弃：停机/生命周期停止后 在途 schedule 仍会触发执行
- **发现ID**：nop-retry-nop-tcc-C6-010
- **严重度**：P3
- **缺陷族**：F8（schedule 句柄获取无取消配对）
- **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:394-420`（schedule 于 :407）、`:654-660`（doStop 仅停 scanner）
- **证据片段**：
```java
// RetryEngineImpl.java:405-417 —— ScheduledFuture 句柄被丢弃，无取消入口
IScheduledExecutor executor = getExecutor();
CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();

executor.schedule(() -> {
    executeImmediateRetry(record, policy, request, cancelToken, task)
```
```java
// RetryEngineImpl.java:654-660 —— 停机只回收扫描器，不回收在途立即重试定时器
@Override
protected void doStop() {
    if (retryScanner != null) {
        retryScanner.stopScanning();
```
- **资源与泄漏链**：路径 = 立即重试链挂起等待下一跳（immediateRetryIntervalMs）时进程进入 doStop → scanner 停止但已 schedule 的定时器到点仍执行 `executeImmediateRetry` → 发起新 RPC、写新 attempt 行。资源 = 在途定时任务与 RPC；量 = 停机时刻挂起的立即重试链数，一次性。
- **影响分析**：优雅停机 draining 不完整：关闭后仍产生 DB 写与出站调用；因记录状态机为 DB 驱动且 lease 兜底，实际危害有限（不产生错误状态），故 P3 优化候选。
- **与已声明边界的区分**：未声明；亦未声明 stop 需排空在途（stopScanning 用 cancel(false) 同样放行在途批次——两者风格一致，可视作「停机=停止接新活、不排空」的隐式边界，故降 P3）。
- **误报校准核对**：非泄漏型（定时器必然执行完毕释放），是 draining 语义缺口。
- **修复方向**：保留 ScheduledFuture 集合并在 doStop cancel(false)；或文档声明停机语义。
- **置信度**：高（行为确定）/ 危害低
- **交叉引用**：与 C6-008 同属停机/在途面。
- **复验建议**：如裁定停机语义需排空再升 P2。
- **复核状态**：保留（P3 维持）；新发现；核实 schedule 句柄 ：407 丢弃、doStop :654-660 仅回收 scanner——draining 语义缺口属实；「停机=停止接新活不排空」与 stopScanning cancel(false) 放行在途的风格一致性论证成立，P3 恰当。

### [nop-retry-nop-tcc-C6-011] 分支补偿上限 maxRetryTimes 只写不读：分支级上限从未被执行
- **发现ID**：nop-retry-nop-tcc-C6-011
- **严重度**：P3
- **缺陷族**：F8（计数资源写入后无消费配对）/ 数据面与状态机脱节
- **位置**：`nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:110`（写入）、`:232-236`（retryTimes 累加）；grep：`getMaxRetryTimes` 在 main 代码零读取（仅 api bean 与测试）
- **证据片段**：
```java
// TccRecordStore.java:110 —— 每个分支记录写入上限
branchRecord.setMaxRetryTimes(defaultMaxRetryTimes);
// :232-236 —— 每次进入补偿中间态累加
if (status == TccStatus.CONFIRMING || status == TccStatus.CANCELLING || status == TccStatus.BEFORE_TIMEOUT) {
    Integer retryTimes = (record.getRetryTimes() == null ? 0 : record.getRetryTimes()) + 1;
    props.put(NopTccBranchRecord.PROP_NAME_retryTimes, retryTimes);
```
唯一生效的上限是 `fetchExpiredRecords` 对**全局**记录 retryTimes 的过滤（:303，参数来自外部调用者）。
- **资源与泄漏链**：不构成泄漏，是「每分支补偿预算」这一声明资源（列+配置）分配后从未被消费：分支补偿次数实际只受全局上限约束，单分支可被补偿远超 defaultMaxRetryTimes 次（全局记录每轮也 bump，两者同速增长时数值上相近，但语义上限错位）。
- **影响分析**：死数据列误导排查（DBA 看到 maxRetryTimes=100 以为生效）；nop-tcc.md「分支补偿计数耗尽…被扫描窗口永久排除」的表述与实现（全局计数排除）存在口径差。
- **与已声明边界的区分**：文档口径含混属于 owner doc 缺陷；按 C6 判 P3 优化候选。
- **误报校准核对**：非泄漏、非竞态，登记为最小化发现。
- **修复方向**：要么在 fetchExpiredRecords/补偿入口消费分支 maxRetryTimes，要么删列并在 nop-tcc.md 更正口径。
- **置信度**：高
- **交叉引用**：与 C6-009 的放弃语义相关。
- **复验建议**：合稿时与文档类发现归并。
- **复核状态**：保留（P3 维持）；新发现；独立 grep 复核证实 getMaxRetryTimes 在 main 代码仅 ITccBranchRecord:37 接口与 api/entity bean getter（零业务读取），写入点 TccRecordStore:110、累加点 :232-236 属实——「只写不读」死数据列登记 P3 恰当。

### [nop-retry-nop-tcc-C6-012] TccRpcServiceInterceptor 桥接路径 setCurrent 与 removeCurrent 配对存在同步异常窗口
- **发现ID**：nop-retry-nop-tcc-C6-012
- **严重度**：P3
- **缺陷族**：F8（thread-local 设置与清除不对称窗口）
- **位置**：`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/interceptor/TccRpcServiceInterceptor.java:46-54`
- **证据片段**：
```java
// TccRpcServiceInterceptor.java:46-53
if (inv.isInbound() && tccContext == null) {
    TccContext fromRequest = TccContext.buildFromRequest(inv.getRequest());
    if (fromRequest != null) {
        TccContext.setCurrent(fromRequest);                 // (1) thread-local 已设置
        return thenOnContext(runWithTccContext(fromRequest, inv)).whenComplete((ret, err) ->
                TccContext.removeCurrent(fromRequest));     // (2) 仅当 (3) 正常返回才挂接清除
    }
}
// (3) runWithTccContext —— getMethodMeta / runInTransactionAsync 的同步前段若抛出，
//     异常直接穿出 interceptAsync，(2) 的 whenComplete 从未挂接 → TccContext 滞留工作线程
```
- **资源与泄漏链**：路径 = `serviceMetaLoader.getMethodMeta` 或引擎同步前段抛 RuntimeException（缺省 `DefaultTccServiceMetaLoader.getMethodMeta` 仅做 map/header 读取，**无可达抛点**；自定义 ITccServiceMetaLoader 实现可能抛出）→ thread-local TccContext 滞留 RPC 工作线程 → 同线程下一请求 `TccContext.getCurrent()` 命中陈旧上下文（跨请求污染面归 C1/C2，本条只登记滞留本身）。资源 = 1 个 thread-local 条目，线程存活期内滞留。
- **影响分析**：理论风险，未具体化（缺省装配链无可达抛点）；修复成本一行（try/catch 包裹或在 runWithTccContext 首行前挂 finally）。
- **与已声明边界的区分**：多跳桥接 thread-local 赢/请求头赢的优先级已声明（nop-tcc.md）；异常窗口未声明。
- **误报校准核对**：按「理论风险」规则降 P3 标注。
- **修复方向**：`setCurrent` 后立即 try/finally 或将 whenComplete 挂接改为 `CompletableFuture.completedFuture(null).handleFirst` 形态保证注册即清理。
- **置信度**：高（窗口存在）/ 低（可达性）
- **交叉引用**：TccGatewayInterceptor.java:90-111 的同型窗口（newCtx 分支同步抛出）已一并核对：其缺省可达性同样要求 registry 已有条目+proceedInvoke 同步抛，且 oldContext==null，条件更窄，不单列。
- **复验建议**：以自定义 meta loader 抛异常的单测钉定清理行为。
- **复核状态**：保留（P3 维持，理论标注）；新发现；核实 ：46-53 setCurrent(:49) 与 whenComplete 挂接(:50-51) 之间的同步异常窗口结构存在、缺省 DefaultTccServiceMetaLoader.getMethodMeta 仅 map/header 读取无可达抛点、TccGatewayInterceptor（实际位于 nop-tcc-integration）同型窗口更窄——「理论风险未具体化」P3 判级恰当。

### [nop-retry-nop-tcc-C6-013] doScan 在「有记录但全部锁定失败」时无退避热循环
- **发现ID**：nop-retry-nop-tcc-C6-013
- **严重度**：P3
- **缺陷族**：C6 邻接（DB 查询资源自旋）/ 优化候选
- **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:137-151`
- **证据片段**：
```java
// RetryScannerImpl.java:137-151
List<NopRetryRecord> lockedRecords = recordStore.tryLockRecordsForProcess(records, retryingTimeoutMs);
...
// lock 为空（并发竞争），继续下一轮
if (lockedRecords.isEmpty()) {
    continue;                       // 立即回到 fetch → 同一批记录再次 fetch/lock，无 sleep/退避
}
...
} while (running);
```
- **资源与泄漏链**：路径 = 多节点竞争下本节点全部 CAS 失败（tryUpdateManyWithVersionCheck 返回空）→ `continue` 立即重新 fetch 同一批 PENDING 记录 → fetch+lock 循环自旋直至竞争者提交。资源 = DB 查询 QPS 与 globalWorker CPU；量 = 竞争持续期间每秒多次全批查询。正常竞争毫秒级自愈；持续失败需病态条件（如时钟估计漂移导致 tryUpdate 永远 0 行）。
- **影响分析**：低概率、自愈型；但一旦锁更新因乐观锁系统性失败（版本列异常）则变成无限自旋，无日志暴露（空锁定不记日志）。
- **与已声明边界的区分**：未声明；与 C2/C4 维度的忙等评估相邻，本条仅以资源消耗登记。
- **误报校准核对**：判 P3：正常路径自愈快，无泄漏。
- **修复方向**：lockedRecords 为空时 sleep(scanIntervalMs) 或计数退避 + DEBUG 日志。
- **置信度**：高（行为确定）/ 危害低
- **交叉引用**：C2（循环）、C4（CPU/池占用）维度可交叉认领。
- **复验建议**：如 C2/C4 已立案同点位则合并，避免重复计数。
- **复核状态**：保留（P3 维持）；新发现；核实 ：141-143 lockedRecords 为空 continue 无 sleep/退避、fetch 失败不改 nextTriggerTime 故同批记录立即可重取——热循环行为确定、正常竞争毫秒级自愈、无日志暴露，P3 恰当。

---

### [维度集外线索-CL-001] 立即重试完成写失败后记录存活的 RETRYING 状态引发扫描器重复执行业务副作用（C5 形态，P1）
- **发现ID**：nop-retry-nop-tcc-CL-001（维度集外线索，不立案展开）
- **所属维度**：C6（本报告 C6-001 的复合后果，形态属 C5 一致性/半个成功）
- **严重度**：P1
- **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:359-361`（写失败点）+ `RetryRecordStoreImpl.java:116-119,200`（租约与重拾机制）
- **证据片段**：
```java
// RetryEngineImpl.java:359-361 —— RPC 成功但 COMPLETED 写失败（C6-001 挂起路径的前半段）
} else if (response != null && response.isOk()) {
    handleExecutionSuccess(record, policy, task);   // updateRecord 抛出 → DB 仍是 RETRYING
    result.complete(response);
```
```java
// RetryRecordStoreImpl.java:116-118 —— 记录对扫描器可见性由租约决定，RETRYING 存活即会被重拾
// 到期时间推迟一个租约周期：调用方发起首次执行期间记录保持PENDING且nextTriggerTime=now，
// 会被扫描器误拾取并发起第二次执行（重复副作用）。推迟后扫描器只在调用方失联后接管
```
- **线索链**：RPC 副作用已发生 → COMPLETED 更新失败 → 记录保持 RETRYING → 租约（默认 600s）到期 → 扫描器重拾并**再次执行同一业务请求** → 业务副作用重复（at-least-once）。提交方 API 契约是幂等重放（COMPLETED→直接成功）而非重复执行。
- **登记理由**：跨入 C5 一致性维度（副作用重复/半个成功），本维度不展开修复方案；C6-001 修复（异常不再静默、调用方获知失败）不消解本线索——记录仍存活且会被重拾，需独立的「完成写失败时的记录处置」设计（如写失败转死信或标记）。
- **严重度依据**：错误路径 + 可具体化（一次 DB 抖动）+ 副作用重复危害高。
- **交叉引用**：C6-001、C1/C5 维度报告。
- **复核状态**：确认（建议归属 C5，严重度 P1）；新发现；形态逐步核实：handleExecutionSuccess RetryEngineImpl:500-501 updateRecord 抛出 → DB 记录保持 PENDING/RETRYING（newRecord 初始租约 RetryRecordStoreImpl:118-119、tryLock 续租 :200），租约到期 fetchPendingRecords（PENDING_STATUSES :40-43）重拾 → executeRetryFromScanner 再次执行业务 RPC——注释 ：116-118 自证该重复形态正是租约机制要防的原始缺陷；与 C6-001（挂起机制）、C6-016（attempt 记账机制）触发点与机制均独立，C6-001 修复不消解本线索，独立行动意义成立（需「完成写失败时记录处置」设计）；单次 DB 写失败触发 + 副作用重复危害高，P1 合理，最终判级由 C5 合稿统一裁定。

---

## 资源获取点配对表

| 获取点 | 释放点 | 成功路径 | 异常路径 | 取消/超时路径 | 结论 |
|--------|--------|----------|----------|----------------|------|
| retry scanner scheduleWithFixedDelay（RetryScannerImpl:96） | stopScanning cancel(false)（:107-109） | 配对 | 配对（cancel(false) 放行在途，running 标志收敛） | — | 通过 |
| retry 立即重试 executor.schedule（RetryEngineImpl:407） | 无 | 自然执行 | 自然执行 | 无取消（C6-010） | **P3 不配对** |
| retry attempt 行 RUNNING（RetryEngineImpl:456） | 终态 saveAttempt（:487） | 配对 | 崩溃/挂死/写失败无对账（C6-002） | 同左 | **P1 不配对** |
| retry record 行（saveRecord :179） | moveToDeadLetter 原子删（Store:285-310）/ OVERWRITE delete（:257） | 配对 | OVERWRITE delete+save 非原子（C6-003） | — | **P1 半配对** |
| retry dead letter 行（Store:304） | 无删除（保留即语义） | — | — | — | F4 登记（C6-004） |
| retry 调用方 future（:336 等） | complete/completeExceptionally | 配对 | executeImmediateRetry 内层永不完成（C6-001） | 同左 | **P1 不配对** |
| tcc registry put（TccEngine:188,211,239,276） | put(txnGroup, old)（:197,204,216,254,287） | 4/4 配对 | 4/4 配对（catch+whenComplete/finally 双保险） | future 永不完成时延迟至上下文消亡（C6-007 触发面） | 通过 |
| tcc TccContext setCurrent（拦截器:49） | removeCurrent（:50-51） | 配对 | 同步异常窗口（C6-012，P3） | future 永不完成时滞留至上下文消亡 | 通过（附 P3） |
| tcc 网关 setCurrent 三分支（Gateway:77,95） | whenComplete 三分支恢复（:102-111） | 配对 | 同步抛出窗口（更窄，并入 C6-012） | 同上 | 通过 |
| tcc 全局/分支记录状态迁移 | begin→finish 终态 CAS（TccRecordStore:162-197,222-283） | 配对 | fromStatus CAS 守卫，重试/恢复续跑（声明契约） | 超时恢复对称续跑（声明契约） | 通过 |
| tcc 补偿 RPC 在途句柄（TccRunner:82,108） | finish*Async 落库 | 配对 | ex→FAILED 落库重试 | checkExpired 超时丢弃句柄（C6-006）；NPE 路径永不完成（C6-007） | **P2 不配对** |
| tcc checkExpiredTransactions 循环 | canceller 检查 + InterruptedException 恢复中断（TccEngine:319,325,335-337） | 配对 | 配对 | 配对 | 通过 |
| tcc 记录物理删除 | removeCompletedRecords（TccRecordStore:334-355，分支随父删除） | 契约配对 | — | 无生产驱动（C6-005/C6-009） | **P2 驱动缺位** |

---

## 覆盖度与残留边界

**覆盖范围**：审计单元 82f/7,972 行全部纳入扫描；本维度深读并逐点核对配对的主代码文件——retry：`RetryEngineImpl`、`RetryTaskImpl`、`RetryScannerImpl`、`RetryRecordStoreImpl`、`IRetryEngine/IRetryTask/IRetryScanner/IRetryRecordStore`、`NopRetryConstants/NopRetryErrors`、全部 beans.xml 装配、`nop-retry.orm.xml`（UK 证据）；tcc：`TccEngine`、`TccTransaction`、`TccBranchTransaction`、`TccTransactionRegistry`、`TccRunner`、`TccHelper`、`TccRecordStore`、`TccRpcServiceInterceptor`、`TccGatewayInterceptor`、`DefaultTccServiceMetaLoader`、`ReflectionTccServiceMetaBuilder/TccServiceMeta`（缓存面核实无界外增长）、`ITccEngine/ITccTransaction/ITccRecordStore/TccStatus`、全部 beans.xml；平台依赖仅读调用面契约：`LifeCycleSupport`、`ContextualizedRegistry`、`TccContext`、`GlobalExecutors`、`IScheduledExecutor.executeOn`、`FutureHelper`（futureCall/syncGet/waitAll）。

**声明契约核对结果**：retry 侧 DISCARD/OVERWRITE+UK 兜底、SUSPENDED 语义、COMPLETED 幂等重放、超时双参、回调开关均按声明实现且资源面无违反；tcc 侧执行表 auth 收敛、崩溃恢复对称续跑、多跳传播优先级均核实成立；「两模块主代码零自建队列/池/信号量、tcc 零锁原语、retry 锁载体仅 scanner」与基线一致，未发现幻影资源。

**残留边界（本维度未深审，交由后续/其他维度）**：
1. `IRpcServiceInvoker` 具体实现的内建超时语义在单元外——C6-001/002/007/008 中「future 永不完成」分支均以该契约为前提，若平台契约保证所有 invoker 必完成，相应分支危害降级（不影响 C6-002 的崩溃分支与 C6-003 的确定性分支）。
2. `FutureHelper`/`ContextProvider`/`GlobalExecutors`/ORM 会话（@SingleSession）内部实现为平台设施，仅按契约评估。
3. nop-graphql-core 的 `TccContextInvoker` 不在本单元（可选项，非缺省装配）。
4. C5/C1 形态后果（副作用重复、双补偿、陈旧上下文跨请求污染）仅以 CL-001 及交叉引用登记，不在本维度展开。
5. ORM `tryUpdateManyWithVersionCheck`/`updateByQuery` CAS 语义按 owner doc 采信，未深读 dao 层实现。

---

## 维度复核结论

- 复核人：C6 维度独立复核子代理（与初审代理相互独立）
- 复核日期：2026-10-16
- 复核方法：逐条亲自打开发现引用的 file:line 核对证据（行号漂移以实际代码修正）；历史盲审解除后对照 `ai-dev/audits/cra-index.md`、WI 1.1–1.5 审计目录（stream/job/wf/task）、owner docs（nop-retry.md / nop-tcc.md）；`git log --all -- nop-retry nop-tcc` 核实无相关修复 commit。对 P1×3 与理论型/不确定发现逐子项（证据/时序/判级/F3 失效面）复核。

### 逐条裁定表

| 发现ID | 裁定 | 最终严重度 | 现状 | 一句话理由 |
|--------|------|-----------|------|-----------|
| C6-001 | 保留 | P1（维持） | 新发现 | 回调内 5 个同步 DB 抛点 + 捕获 stage 被丢弃（:391 return result）逐行核实，两条件非罕见合取，同族站点排查完备 |
| C6-002 | 保留（子项修正） | P1（维持） | 新发现 | 砖化链 7 步逐行可信（崩溃路径 (1) / future 永不完成路径 (2)）；路径 (3)「:487 写失败」不产生 UK 碰撞（retryCount 已递增），修正归 C6-016，P1 不受影响 |
| C6-003 | 保留 | P1（维持） | 新发现 | delete 自动提交与 REQUIRES_NEW 插入不可能同事务，约束类失败确定性复现，半个成功后任务蒸发无死信 |
| C6-004 | 保留 | P2（维持） | 新发现 | IRetryRecordStore 16 方法零清理入口、主代码 grep 零命中、与 tcc 侧 removeCompletedRecords 不对称，F4 判 P2 恰当 |
| C6-005 | 保留 | P2（维持） | 新发现 | 两回收入口 main 零调用、beans 零驱动、retry/tcc 装配不对称核实，owner doc 无驱动契约，「边界配置缺陷」P2 恰当 |
| C6-006 | 保留 | P2（维持） | 新发现 | 超时仅日志 + null cancelToken + expireTime 推进重拾链逐行核实，已声明（单条超时）与未声明（不取消不追踪）区分准确 |
| C6-007 | 保留（理论标注） | P2（维持） | 新发现 | :158 裸解引用 vs 兄弟方法双参防护的不对称确证；触发前提依赖 invoker 违约，按规则落 P2 理论标注位 |
| C6-008 | 保留 | P2（维持） | 新发现 | 无超时 join + cancelToken=null + globalWorker 有界池（GlobalExecutors:85-91）核实；挂死前提未具体化，P2 恰当 |
| C6-009 | 保留 | P2（维持） | 新发现 | 扫描窗口排除 + onlyCompleted 清理 + KILLED 零写入点独立 grep 核实，放弃已声明 / 干预面缺失未声明的切分准确 |
| C6-010 | 保留 | P3（维持） | 新发现 | schedule 句柄丢弃 + doStop 仅停 scanner 核实，draining 缺口与 stopScanning cancel(false) 风格一致论证成立 |
| C6-011 | 保留 | P3（维持） | 新发现 | getMaxRetryTimes 零业务读取（仅接口/bean getter）独立 grep 核实，「只写不读」P3 恰当 |
| C6-012 | 保留（理论标注） | P3（维持） | 新发现 | 同步异常窗口结构存在，缺省 meta loader 无可达抛点，理论风险 P3 恰当 |
| C6-013 | 保留 | P3（维持） | 新发现 | lock 为空 continue 无退避核实，fetch 失败不改 nextTriggerTime 故同批立即可重取，自愈型 P3 恰当 |
| C6-014（R2） | 保留 | P3（维持） | 新发现 | BindScheduledExecutor 链死 + running 自锁 + scanFuture 零观测逐行核实；与 nop-job C4-003 跨模块同族同机制（独立佐证），零代码交集仍标新发现 |
| C6-015（R2） | 保留（证据修正） | P2（维持） | 新发现 | 方向翻转核心链（CANCELLING 写失败→补偿短路→TRYING 滞留→恢复 CONFIRM）逐行成立；**F9 子项驳回**：endAsync:86-94 实际重抛业务异常并 LOG.error 保留补偿异常，「业务异常丢失/无日志」不成立 |
| C6-016（R2） | 保留 | P2（维持） | 新发现 | attempt 终态写失败改写业务结果 → 重复执行/死信化链逐行核实，与 CL-001/C6-002/C6-001 机制边界清晰 |

**CL-001 处置裁定**：**确认**。裁定依据：①形态真实存在于 live code——handleExecutionSuccess（RetryEngineImpl:500-501）updateRecord 抛出后 DB 记录保持 PENDING/RETRYING（初始租约 RetryRecordStoreImpl:118-119、续租 :200），租约到期经 fetchPendingRecords（:40-43 PENDING_STATUSES）重拾并再次执行业务 RPC；RetryRecordStoreImpl:116-118 注释自证「扫描器误拾取并发起第二次执行（重复副作用）」正是本线索形态；②与 C6 维度发现不重复——C6-001 是挂起机制（:391 丢弃 stage）、C6-016 是 attempt 记账机制（:487），本线索是 record COMPLETED 写机制（:360/:501），触发点与修复面均独立；③独立行动意义成立——C6-001 修复（调用方获知失败）不消解记录存活被重拾的问题，需要独立的「完成写失败时记录处置」设计（转死信/标记/补偿性收敛）。建议归属维度：**C5（一致性/半个成功）**；建议严重度：**P1**（单次 DB 写失败触发 + 业务副作用重复 + 链路逐步可核实）；与 C6-016 合稿时建议归并为「完成期记账失败」家族统一裁定后果严重度。

### P1 与不确定项子项明细

**C6-001（P1）**：①证据 ✓——:336 result、:340-389 回调、:391 return result（捕获回调异常的 stage 被丢弃零观测）；抛点五处均为真实同步 DB/联动调用：updateRecord（:346/:374）、handleExecutionFailure（:356/:384 → updateRecord:525 / moveToDeadLetter:517）、handleExecutionSuccess（:360 → updateRecord:501 / triggerCallback:505 → executeTask:563 → loadPolicy/saveRecord:179）。②时序 ✓——T1 opt-in policy（immediateRetryCount>0，默认 0）→ T2 任一尝试完成 → T3 回调内单次 DB 写失败 → T4 result 永不完成 → T5 syncGet 调用方线程永久阻塞；每步代码可信，条件数 2 非罕见合取。③F3 ✓——executeSingleAttempt 直接返回 stage（异常可传播不挂起）、scheduleImmediateRetry 的 lambda 受 BindScheduledExecutor.executeOn catch Throwable 保护、retryFromDeadLetter 直接返回 invoker stage：future-never-completes 族在 retry 侧仅此一处，tcc 侧镜像为 C6-007。④判级 P1 维持（备注默认值条件）。

**C6-002（P1）**：①证据 ✓——:454-456 RUNNING 落库、:487 唯一终态写点、:134 attemptNo=retryCount+1、saveAttempt:142-149 无 duplicate-key 容错（vs record 侧 resolveInsertRaceWinner:222-241 兜底的不对称）、UK 实际 :365-368（原文 :364-368 含注释行，漂移 1 行）、STOPPED/CANCELED 仅 _NopRetryDaoConstants:49/:54 定义零使用。②时序 ✓（7 步）——attempt#N RUNNING + RPC 在途 → 崩溃/重启（或 future 永不完成）→ retryCount 未递增（仅 handleExecutionFailure:512-513 递增）→ 租约 600s（DEFAULT_RETRYING_TIMEOUT_MS=10min 核实）到期 → 扫描器重拾 → newAttempt attemptNo=N → saveAttempt 首插 UK 冲突同步抛 → scanner catch(Exception):148-150 吞掉、记录停 RETRYING，每租约周期重复；resume 仅 SUSPENDED 可用（:147-149）无法绕过；任何路径均不可达 moveToDeadLetter（doExecute 在 invokeAsync 前即抛）——「不可恢复、不可死信化」确证。③子项修正——原文路径 (3)「:487 终态写失败」：该异常被上游 whenComplete 路由至 handleExecutionFailure 使 retryCount+1，下次 attemptNo=N+1 无碰撞，实际后果是 C6-016 的重复执行而非砖化；RUNNING 永久残留本身成立，但砖化结论仅由路径 (1)(2) 承载。④F3/E4 ✓——nop-retry.orm.xml 全部唯一约束仅 UK_RETRY_IDEMPOTENT_ID（:264，经 resolveInsertRaceWinner 分流无砖化）与本 UK 两处，nop-tcc.orm.xml 零 unique（grep 核实），attempt 表外无同族站点。

**C6-003（P1）**：①证据 ✓——handleBlockStrategy :255-260 先删后插无包络；deleteRecord 实际 :247-249（原文 :246-249 漂移 1 行）无 @Transactional；saveRecord :274-278 REQUIRES_NEW；RetryEngineImpl 全类与 RetryTaskImpl.callAsync 调用链均无事务注解。②时序 ✓——T1 OVERWRITE + 既有未完成记录 → T2 delete 自动提交 → T3 saveRecord 独立事务失败（确定性：payload 超列宽/超限约束；偶发：DB failover/锁超时）→ T4 旧任务蒸发、无死信快照；确定性分支可复现。③误报排除 ✓——DISCARD 只读对比成立；COMPLETED 不咨询 OVERWRITE（:194-201 注释）与本条无关；moveToDeadLetter 孤儿 attempt 是已声明边界（:306-309），本条不在声明内。④判级 P1 维持（确定性触发 + 数据不可逆丢失）。

**C6-007（P2 理论）**：①证据 ✓——:158 `Boolean.TRUE.equals(response.getBizFatal())` 在 ex==null 且 response==null 时 NPE；兄弟方法 finishTryAsync:70 `response == null ||`、finishConfirmAsync:118 `response != null &&` 均有防护，三处独有的不对称支持「笔误而非契约假设」；TccRunner:108-119 丢弃 whenComplete 返回 stage → future(:107) 永不完成 → cancelAllAsync.waitAll/doCancelAsync/endAsync 挂起 → TccEngine:253-255 registry.put 恢复不执行。②时序前提——invoker 以 (ret=null, ex=null) 正常完成；内置 ClusterRpcServiceInvoker/HttpRpcServiceInvoker 均构造非空 ApiResponse，可达性未具体化到任一内置 invoker。③判级——按 campaign 校准规则落 P2「理论风险，未具体化」标注位维持（修复成本一行 + 不对称证据 + 挂链后果支撑不降 P3）。

**C6-012（P3 理论）**：①证据 ✓——TccRpcServiceInterceptor:46-53 窗口结构存在（setCurrent:49 与 whenComplete 挂接：50-51 之间，runWithTccContext 同步前段抛出则清除永不挂接）。②可达性——缺省 DefaultTccServiceMetaLoader 仅 map/header 读取无可达抛点，需自定义 ITccServiceMetaLoader 实现；TccGatewayInterceptor（实际位于 nop-tcc-integration，非 nop-tcc-core，修正注记）同型窗口条件更窄。③判级 P3 维持。

**C6-015（P2，含证据修正）**：①核心链 ✓——(B) doCancelAsync:110-111 updateTccStatusAsync(CANCELLING) 抛 DB 异常 → thenCompose 短路 → cancelAllAsync 不执行 → 全局记录 DB 滞留 TRYING（CAS :188-193 写失败即跳过）；恢复入口 TccEngine:345-348 endAsync(true,null,null) → TRYING + 全 TRY_SUCCESS → isRollbackOnly=false（TccStatus:93-95，TRY_SUCCESS isAllowConfirm=true）且 isFailed(null,null)=false → doConfirmAsync（:119-138 无 TRYING 守卫拦截）→ CONFIRM_SUCCESS——失败事务以确认方向翻转属实。(A) 崩溃窗口（task 抛异常后、CANCELLING 提交前进程崩溃）结构成立，owner doc（nop-tcc.md 崩溃恢复语义节）仅声明补偿中间态对称续跑，TRYING 恢复方向未声明。②**F9 子项驳回**——「调用方收到补偿链 DB 异常、业务异常 e 无 suppressed/无日志丢失」不成立：endAsync 的 whenComplete（TccTransaction:86-94）在补偿链失败时 LOG.error("nop.tcc.end-compensate-fail", err2) 保留补偿异常证据，随后 `throw NopException.adapt(ex)` 重抛业务异常——调用方实际收到业务异常（本条复验建议断言 (1) 按现实现即通过）；该子项对应的修复方向「业务异常优先完成/addSuppressed」失效，其余修复方向（证据持久化解耦、恢复 confirm WARN/可配置方向）有效。③判级——核心以替代证据成立、两链条件叠加低频，P2 维持。

### 同族重叠与关联注记（供 Phase 6 合并裁定，不合并条目）

1. **future 永不完成族**：C6-001（retry 侧）与 C6-007（tcc 侧镜像）同族跨模块，两轮间无新增成员；round2 未产生该族新条目。
2. **记账-结果耦合族**：C6-001（挂起形态）与 C6-016（改写形态）同根（RetryEngineImpl 回调内同步 DB 记账），失败形态相反，修复应统一设计；C6-016 与 CL-001 同后果（副作用重复）不同机制，C6-016 交叉引用已预告合稿归并。
3. **中间态残留族**：C6-002（attempt RUNNING 残留，retry）与 C6-015（TRYING 残留，tcc）形态相邻，后果不同（UK 砖化 vs 方向翻转）。
4. **驱动缺位族**：C6-005（恢复/清理函数无生产驱动）为 C6-006/C6-009 运行时行为的前置条件，合稿时保持引用关系。
5. **跨模块同族关联**：C6-014 与 nop-job 审计 C4-003（nop-job C10-005 引证）为同一平台机制（BindScheduledExecutor 链死 + running 自锁）的跨模块同族发现，按「同族不同模块仍标新发现」规则处理，修复时建议平台级统一（BindScheduledExecutor 观测钩子或模块侧 whenComplete 观测）。

### 行号漂移与事实修正汇总

- 行号漂移（证据内容一致，不改判级）：C6-001 引用 :344-357/:371-385 → 实际 :342-358/:362-388；C6-002 UK :364-368 → :365-368；C6-003 deleteRecord :246-249 → :247-249；C6-014 引用 BindScheduledExecutor:99-107 与实际一致。其余引用行号逐点核实无误。
- 事实修正 2 处：C6-002 路径 (3)（不产生 UK 碰撞，归 C6-016）；C6-015 F9 子项（业务异常实际重抛、补偿异常有日志）。
- 位置注记 1 处：TccGatewayInterceptor 实际位于 nop-tcc-integration（非 nop-tcc-core），配对表 T2 行引用行号（:77,:95,:102-111）核实无误。

### 统计

- 复核范围：16 条发现 + CL-001，全部逐条亲自核证 live code。
- 裁定分布：**保留 16 / 降级 0 / 驳回 0**；其中 2 条附证据修正注记（C6-002 子项、C6-015 F9 子项），判级全部维持初审。
- 判级分布：P1×3（C6-001/002/003）、P2×8（C6-004~009、015、016）、P3×5（C6-010~014）。
- 现状标注：**17/17 新发现**（含 CL-001）——WI 1.1–1.5 审计对象为 stream/job/wf/task 与 retry/tcc 代码零交集（仅平台设施边界引用）；cra-index 无 retry/tcc 条目；`git log --all -- nop-retry nop-tcc` 核实无本批发现对应修复。
- CL-001：**确认**，建议归属 C5、P1。

---

## 最终保留项

| 发现ID | 标题 | 最终严重度 | 现状 |
|--------|------|-----------|------|
| nop-retry-nop-tcc-C6-001 | executeImmediateRetry 完成回调同步异常导致调用方 future 永不完成且异常静默丢弃 | P1 | 新发现 |
| nop-retry-nop-tcc-C6-002 | 崩溃/挂死后 RUNNING attempt 行永久残留，经 UK_RETRY_ATTEMPT_RECORD_NO 碰撞将记录永久砖化 | P1 | 新发现 |
| nop-retry-nop-tcc-C6-003 | OVERWRITE 阻塞策略 delete+insert 非原子：半个成功后任务蒸发且无死信 | P1 | 新发现 |
| nop-retry-nop-tcc-C6-004 | retry 侧 DB 账本零保留/清理路径：COMPLETED record、attempt、dead letter 三表无界增长 | P2 | 新发现 |
| nop-retry-nop-tcc-C6-005 | TCC 过期扫描与完结清理均无生产驱动：缺省部署下 TRYING 残留与参与者预留资源永不回收 | P2 | 新发现 |
| nop-retry-nop-tcc-C6-006 | checkExpiredTransactions 单条补偿超时后丢弃 future：在途补偿不可取消、记录可被并发二次补偿 | P2 | 新发现 |
| nop-retry-nop-tcc-C6-007 | finishCancelAsync 对 response==null 无防护：NPE 后 future 永不完成、registry/上下文滞留（理论风险，未具体化到内置 invoker） | P2 | 新发现 |
| nop-retry-nop-tcc-C6-008 | scanner 批处理以无超时 allOf().join() 阻塞共享 globalWorker 线程：单条挂死即扫描线停摆 | P2 | 新发现 |
| nop-retry-nop-tcc-C6-009 | TCC 已放弃非终态记录与分支永久残留且永不补偿，无人工处理工具面 | P2 | 新发现 |
| nop-retry-nop-tcc-C6-010 | 立即重试定时器句柄丢弃：停机后在途 schedule 仍会触发执行 | P3 | 新发现 |
| nop-retry-nop-tcc-C6-011 | 分支补偿上限 maxRetryTimes 只写不读：分支级上限从未被执行 | P3 | 新发现 |
| nop-retry-nop-tcc-C6-012 | TccRpcServiceInterceptor 桥接路径 setCurrent/removeCurrent 配对同步异常窗口（理论风险） | P3 | 新发现 |
| nop-retry-nop-tcc-C6-013 | doScan 在「有记录但全部锁定失败」时无退避热循环 | P3 | 新发现 |
| nop-retry-nop-tcc-C6-014 | 扫描线单点静默死亡：Error/拒绝后不重臂、scanFuture 零观测、running 自锁（与 nop-job C4-003 跨模块同族） | P3 | 新发现 |
| nop-retry-nop-tcc-C6-015 | endAsync 补偿链 CANCELLING 标记落库失败短路 → TRYING 残留经无证据恢复被 CONFIRM（方向翻转；F9 子项经复核驳回，见复核结论） | P2 | 新发现 |
| nop-retry-nop-tcc-C6-016 | attempt 终态写失败毒化业务结果：成功被误判失败，触发副作用重复/预算虚耗/成功操作死信化 | P2 | 新发现 |

（驳回项：无。以下 ID 保留备查：无驳回条目。）

### 维度集外线索（确认）

| 线索ID | 标题 | 建议归属 | 建议严重度 | 现状 |
|--------|------|---------|-----------|------|
| nop-retry-nop-tcc-CL-001 | 立即重试完成写失败后记录存活的 RETRYING 状态引发扫描器重复执行业务副作用 | C5（一致性/半个成功） | P1 | 新发现；确认独立于 C6-001/C6-016，需「完成写失败时记录处置」专项设计 |
