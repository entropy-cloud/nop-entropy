# C1 共享可变状态与可见性 — nop-retry / nop-tcc 第 1 轮初审（盲审）

- 审计单元：`nop-retry` + `nop-tcc`（合计 82 files / 7,972 lines 手写，不含 `_` 前缀生成物与 target/）
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 审计维度：C1 共享可变状态与可见性（含 F10 检查-然后-行动守卫原子性、F9 吞异常致状态不一致的 C1 侧面）
- 盲审纪律：未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何既有文件；仅以当前 live code 为准。

---

## 检查范围说明

### nop-retry 分节（8 jar 子模块，40 files / 4,100 lines）

逐文件精读的核心状态载体文件：

- `nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java`（662 行全文）
- `nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java`（全文）
- `nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java`（全文）
- `nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryTaskImpl.java`（全文）

其余子模块核对结论：

- `nop-retry-api`：IRetryEngine/IRetryTask 为无状态接口；`beans/*InputBean/*OutputBean` 为纯数据 Bean；`NopRetryApiConstants` 为常量接口。无可变共享状态。
- `nop-retry-dao`：4 个实体子类（NopRetryRecord/Attempt/DeadLetter/Policy）均为 `_gen` 生成基类的无字段子类；`NopRetryPolicy` 仅追加带默认值的只读帮助方法；`NopRetryDaoConstants` 常量接口。唯一 static 非终态字段在生成物内（`_PROP_ID_BOUND` 等，属 `_` 前缀生成文件，不作为发现对象）。
- `nop-retry-service`：4 个 BizModel 均为 `CrudBizModel` 空壳子类（仅 setEntityName）；`NopRetryConfigs`/`NopRetryConstants`/`NopRetryErrors` 为接口与错误码定义。
- `nop-retry-web`/`nop-retry-codegen`：src/main 无手写 Java（仅测试与资源）。
- `nop-retry-app`：`NopRetryApplication.static String[] globalArgs`——main() 先写后由 Quarkus 启动线程读，标准引导模式，无并发发布问题。
- `nop-retry-meta`：仅 xgen 模板/资源，无 Java 状态。

平台设施使用面（不审自身实现，只审用法）：`GlobalExecutors.globalTimer()/globalWorker()`（CHM computeIfAbsent 单例，已核实）、`LifeCycleSupport`（doStart/doStop 钩子）、ORM `updateEntityDirectly` 乐观锁语义（已下钻至 SQL 生成层核实，见 C1-003）。

### nop-tcc 分节（9 jar 子模块，42 files / 3,872 lines）

逐文件精读的核心状态载体文件：

- `nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java`（356 行全文）
- `nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java`（全文）
- `nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccBranchTransaction.java`（全文）
- `nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java`（全文）
- `nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransactionRegistry.java`（全文）
- `nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccHelper.java`（全文，无状态）
- `nop-tcc-core/src/main/java/io/nop/tcc/core/interceptor/TccRpcServiceInterceptor.java`（全文）
- `nop-tcc-core/src/main/java/io/nop/tcc/core/meta/DefaultTccServiceMetaLoader.java`、`ReflectionTccServiceMetaBuilder.java`、`TccServiceMeta.java`、`TccMethodMeta.java`、`ITccServiceMetaLoader.java`
- `nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java`（全文）
- `nop-tcc-integration/src/main/java/io/nop/tcc/integration/gateway/TccGatewayInterceptor.java`（全文）

其余子模块核对结论：

- `nop-tcc-api`：ITccEngine/ITccRecordStore 等接口、TccStatus 枚举（`getFinishedStatus()` 每次返回新建 List，非共享可变集合）、TccBranchRequest 与 beans 均为数据 Bean。
- `nop-tcc-dao`：NopTccRecord/NopTccBranchRecord 为生成基类无字段子类，仅追加只读转换方法（`getTccStatus()`/`getBranchStatus()`/`getRequest()`——getRequest 每次重新 parse，反而不产生跨线程共享实例）。
- `nop-tcc-service`：2 个 BizModel 为 CrudBizModel 空壳；`NopTccConfigs` 空接口。
- `nop-tcc-web`/`nop-tcc-codegen`：src/main 无手写 Java。
- `nop-tcc-app`：同 retry，`globalArgs` 引导模式，无问题。
- `nop-tcc-meta`：仅 xgen 资源。

上下文传播机制（按上下文传播描述，非字面 ThreadLocal）：字面 `ThreadLocal` 声明在两模块 main 源码中为零（grep 基线 C1 ThreadLocal 节为空，本次复核一致）。TCC 的上下文状态由 `TccContext`（nop-core）承载，存放在 `IContext.setAttribute(TccContext.class.getName(), ...)`；`BaseContext.attributes` 为 `ConcurrentHashMap`（已核实，nop-api-core BaseContext.java L46），线程绑定由平台 `BaseContextProvider.contextHolder()`（ThreadLocal）完成，跨线程经 `ContextProvider.thenOnContext/completeAsyncOnContext` 以**同一 IContext 实例**传播。因此"同一 IContext 被多个线程共享读写 attribute"是 TCC 上下文用法的现实形态，C1-004 依此评估。

Owner doc 校准：两文档功能/语义描述与 live code 一致（阻塞策略、UK 兜底、SUSPENDED 语义、恢复对称续跑、thread-local 赢/请求头赢优先级均在代码中定位到对应实现）。**Drift 登记（不立案）**：`nop-retry.md` 子模块表列 4 个子模块（实际 8 个，缺 api/app/codegen/meta）；`nop-tcc.md` 子模块表列 5 个（实际 9 个，缺 api/app/codegen/meta）。按审计纪律记 drift 不立案。

---

## 发现条目

### [nop-retry-nop-tcc-C1-001] TCC 全局/分支状态 CAS 失败不阻断补偿 RPC，互斥守卫基于各链私有过期内存副本

1. **发现ID**：nop-retry-nop-tcc-C1-001
2. **严重度**：P1（罕见但可具体化的竞态；TCC 语义违约可致参与方资源状态不一致）
3. **缺陷族**：F10 检查-然后-行动守卫原子性（守卫读内存副本、CAS 结果被丢弃）
4. **位置**：
   - `nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java` L160-197（updateTccStatusAsync CAS）、L189-193（0 行仅 warn 后正常返回）、L220-283（updateTccBranchStatusAsync 同构）
   - `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java` L97-117（doCancelAsync）、L119-138（doConfirmAsync）
   - `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java` L72-96、L98-122
5. **状态载体与读写方**：`NopTccRecord.status` / `NopTccBranchRecord.status`（DB 列）+ 各执行链私有加载的 ORM 内存副本。DB 列写由单语句 CAS（`updateByQuery[id+fromStatus]`）保护；内存副本仅被本链读写、从不刷新。读方：每条 endAsync/doConfirm/doCancel 链的互斥守卫；写方：调用方 endAsync 链与恢复扫描链（`checkExpiredAsync` 每轮 `new TccTransaction(false, record, this)` 新建副本）。两条链之间无任何 happens-before，唯一仲裁是 DB CAS——但 CAS 失败（updated==0）仅记 warn 后正常返回，上层 `thenCompose` 不感知失败、无条件发起分支 confirm/cancel RPC。
6. **证据片段**：

   TccRecordStore.java L186-196（CAS 失败被吞为 warn）：
   ```java
   long updated = recordDao().updateByQuery(statusCasQuery(tccRecord.getTxnId(), fromStatus), props);
   if (updated == 0) {
       LOG.warn("nop.tcc.skip-status-update-due-to-conflict:txnId={},fromStatus={},toStatus={}",
               tccRecord.getTxnId(), fromStatus, status.getCode());
       return null;
   }
   ```

   TccTransaction.java L110-111（CAS 结果不影响后续补偿）：
   ```java
   CompletionStage<Void> future = getRepository().updateTccStatusAsync(tccRecord, TccStatus.CANCELLING, null)
           .thenCompose(r -> TccRunner.cancelAllAsync(branchTxns, timeout, tccEngine.getServiceInvoker()));
   ```

   TccRunner.java L78-82（注释声称"标记失败则不执行服务函数"，实现不成立——beginConfirmAsync 对 CAS 丢失正常返回）：
   ```java
   // 如果标记状态失败，则不会去执行服务函数
   return branchTxn.beginConfirmAsync().thenCompose(arg -> {
       CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
       serviceInvoker.invokeAsync(branchRecord.getServiceName(), branchRecord.getConfirmMethod(), ...);
   ```
7. **触发时序**（双分支事务，全部步骤均有对应代码路径）：全局事务 X（TRYING，expireTime=begin+60s）含分支 B1（已 TRY_SUCCESS）、B2（try 仍在参与方执行中）。
   - t0：B2 的 try 超过 defaultTxnTimeout=60000ms，X 过期。
   - t1 恢复线程 A：`fetchExpiredRecords` 版本 CAS 认领 X → `endAsync(true,null,null)` → 内存副本 status=TRYING → `isRollbackOnly`(B2=TRYING)=true → doCancelAsync → 内存守卫（L100-108）通过 → 全局 CAS TRYING→CANCELLING 成功 → B1 分支 CAS TRY_SUCCESS→CANCELLING 成功 → B1 的 cancel RPC 在途（秒级）。
   - t2 调用方线程 B（存活，A 的分支 CAS 提交前已加载分支副本：B1=TRY_SUCCESS、B2=TRY_SUCCESS）try 全部成功返回 → endAsync(ok) → 内存副本 tccRecord=TRYING（自 start 起从未刷新）→ 非 CONFIRMING/CANCELLING 命中、isRollbackOnly=false、response ok → doConfirmAsync → 内存守卫（L122-130）读私有副本 TRYING 通过 → 全局 CAS TRYING→CONFIRMING **0 行**（DB=CANCELLING）→ warn 跳过 → confirmAllAsync → runBranchConfirmAsync：内存副本 isConfirmed()=false → beginConfirmAsync 内存守卫（TRY_SUCCESS）通过 → 分支 CAS TRY_SUCCESS→CONFIRMING **0 行** → warn 跳过 → **B1 的 confirm RPC 照发**。
   - t3：参与方 B1 收到 cancel 与 confirm 对撞（或 confirm 落在 cancel 之后）。"confirm 开始后不允许回滚"在参与方被破坏；cancel 已释放预留资源而 confirm 再次提交业务变更 → 业务状态不一致。
   - 次生时序（恢复对恢复）：`TccEngine.checkExpiredTransactions` L331 `checkExpiredAsync(record).toCompletableFuture().get(expireGap,...)` 超时后**不取消在途补偿**（future 未 cancel），被认领记录 expireTime 已推至 nextCheckTime；若首轮补偿因分支 RPC 悬挂超过 expireGap 仍未完成，下轮扫描再次认领同一条记录并发起第二条 endAsync → 与第一条形成双驱动，同样经"CAS 丢失败不阻断"放大为重复分支 RPC。
8. **影响**：TCC 核心不变量（confirm/cancel 互斥）在参与方侧被打破，预留资源释放与确认提交可能同时发生；全局状态列虽被 CAS 保护不损坏，但状态机的互斥承诺仅存在于单链内存视图。补偿结果聚合（aggregateConfirmBranchStatus L198-217）读到的仍是各链私有副本，可能聚合出与 DB 不符的终态。
9. **误报校准**：已声明契约核对——"崩溃恢复对称续跑"（CONFIRMING→续跑 confirm 等）声明的是**单链恢复路由**，未声明"调用方 end 与恢复扫描互斥"；"执行表 dao 直写不受 biz auth 约束"与本发现无关。代码注释（TccTransaction L101"避免与 confirm 路径互相覆盖中间态"、TccRunner L78"标记失败则不执行服务函数"）表明意图即互斥，实现存在缺口，非声明边界。触发前提（事务超时 + 调用方存活完成）是"超时控制"特性的正常工作场景，非滥用。
10. **F3 同族兄弟清单**（同一根因"互斥守卫读私有内存副本 + CAS 失败不阻断"的全类枚举）：
    - `TccTransaction.doCancelAsync` L100-108 内存守卫 —— 同族未修（本发现主位）。
    - `TccTransaction.doConfirmAsync` L122-130 内存守卫 —— 同族未修。
    - `TccRunner.runBranchConfirmAsync` L75 `isConfirmed()` 内存短路 —— 同族未修。
    - `TccRunner.runBranchCancelAsync` L101 `isCancelled()` 内存短路 —— 同族未修。
    - `TccBranchTransaction.beginConfirmAsync` L94-103 / `beginCancelAsync` L125-138 内存守卫 + 无条件 CAS —— 同族未修。
    - `TccEngine.runBranchTransactionAsync` L295 `txn.getTccStatus() != TccStatus.TRYING`（分支创建守卫，内存副本）—— 同族未修。
    - `TccEngine.checkTransactionActive` L170-182（finished 检查，内存副本；false-negative 方向允许 join 进正在补偿的事务）—— 同族未修。
    - `TccRecordStore.updateTccStatusAsync` L175/L183-186 CAS 前先改内存 endTime/error 字段、失败后不回滚内存（内存漂移，低危）—— 同族未修。
11. **修复方向**：让 CAS 结果显式传播（`updated==0` 时返回失败/标记），doConfirmAsync/doCancelAsync 与 runBranchConfirmAsync/runBranchCancelAsync 在 CAS 失败时短路不发 RPC；或引入 DB 级"补偿租约"列（casu 认领）使恢复与调用方路径在 DB 层互斥；同时为 checkExpiredTransactions 超时分支补 `future.cancel` 或在飞集合去重。
12. **置信度**：高（代码路径逐行核实；时序每一步都有代码锚点）。
13. **备注**：全局状态列与分支状态列的写入本身有 CAS 保护，不存在 DB 列级数据损坏；缺陷在"守卫→行动"之间。参加方自身幂等性（TCC 契约要求 confirm/cancel 幂等）可缓解重复同类 RPC（双 cancel），但不能缓解 cancel/confirm 对撞。
14. **复核状态**：保留 P1；新发现；证据逐行核实成立（TccRecordStore L188-193 CAS 失败仅 warn 后正常返回、TccTransaction L110-111/L132-133 thenCompose 无条件进入补偿批次、TccRunner L79-82 beginConfirmAsync 对 CAS 丢失正常返回后照发 RPC），时序每步有代码锚点，前置条件（事务超时时调用方存活并最终完成）为超时控制特性的正常工作场景而非 ≥3 罕见合取；cra-index 台账（WI 1.1–1.5 全部为 stream/job/wf/task 单元）与 plans 2282/2386/2388/342/347 均无本条目（plan 2388 修的是单链 CANCELLING 恢复路由，与本条"调用方 end 与恢复扫描双驱动"不同面）。

### [nop-retry-nop-tcc-C1-002] retry OVERWRITE 分支 delete+insert 无 UK 冲突兜底，并发覆盖以裸约束冲突暴露

1. **发现ID**：nop-retry-nop-tcc-C1-002
2. **严重度**：P1（罕见但可具体化的竞态；违反已声明契约"输家按 blockStrategy 处理，不以裸约束冲突暴露"）
3. **缺陷族**：F10 检查-然后-行动守卫原子性（find→delete→insert 非原子，且缺首提兜底）
4. **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java` L243-266（handleBlockStrategy），对照 L168-189（executeTask 的有兜底版本）
5. **状态载体与读写方**：`nop_retry_record` 行（DB）+ `UK_RETRY_IDEMPOTENT_ID` 唯一键。写方：提交线程（executeTask 主路径）与 OVERWRITE 覆盖路径；读方：findRecordByIdempotentId 查重。查重与 delete/insert 之间无事务边界、无锁；主路径用唯一键冲突兜底（resolveInsertRaceWinner），OVERWRITE 路径没有。
6. **证据片段**（RetryEngineImpl.java L255-260）：
    ```java
    case NopRetryConstants.BLOCK_STRATEGY_OVERWRITE:
        LOG.info("nop.retry.block-strategy-overwrite:recordId={}", existingRecord.getSid());
        recordStore.deleteRecord(existingRecord);
        NopRetryRecord newRecord = recordStore.newRecord(task, request);
        recordStore.saveRecord(newRecord);
        return executeWithRetry(newRecord, policy, request, cancelToken, task);
    ```
    对照同文件 L178-187 主路径的兜底：
    ```java
    try {
        recordStore.saveRecord(record);
    } catch (Exception e) {
        NopRetryRecord raceWinner = resolveInsertRaceWinner(task, e);
        if (raceWinner == null)
            throw e;
        // 并发首提的TOCTOU窗口（plan 2282 G7-14-01）：唯一键冲突兜底，
    ```
7. **触发时序**：已有 PENDING 记录 R（namespaceId, groupId, idempotentId = K），两调用方 T1/T2 同时以 K 提交且 policy=OVERWRITE。
    - t1：T1、T2 的 findRecordByIdempotentId 均返回 R。
    - t2：T1 delete R 成功、saveRecord(R1') 成功。
    - t3：T2 delete R（行已不存在，0 行）→ saveRecord(R2') → `UK_RETRY_IDEMPOTENT_ID` 冲突 → NopException(ERR_SQL_DUPLICATE_KEY) 直接抛给 T2 调用方（saveRecord 无 try/catch，也非 executeTask 的兜底包裹范围）。
    - 变体时序：T2 的 delete 稍后于 T1 的 insert 执行——deleteRecord 按 R.sid 删除（R1' 有新 sid），删除 no-op 后同样在 insert 处撞唯一键。
8. **影响**：败方收到裸 SQL 约束冲突异常而非按 DISCARD/OVERWRITE 语义分流，直接违反 owner doc（nop-retry.md L33"并发首提兜底……不以裸数据库约束冲突暴露"）；调用方若未特判会将其当系统错误处理。无数据损坏（DB 唯一键守住单记录不变量）。
9. **误报校准**：主路径同窗口已有兜底（plan 2282 G7-14-01），说明该契约是声明且已在同类窗口实施过的要求，OVERWRITE 分支是漏网同族位点而非新语义提案；"SUSPENDED 不偷偷恢复/COMPLETED 幂等重放"等声明边界与本位点无冲突。
10. **F3 同族兄弟清单**（`recordStore.saveRecord` 全部调用位点）：
    - `RetryEngineImpl.executeTask` L179 —— 已有 resolveInsertRaceWinner 兜底（已修）。
    - `RetryEngineImpl.handleBlockStrategy` L259 —— 无兜底（本发现）。
    全类共 2 处，1 处已修 1 处未修；无第三位点。
11. **修复方向**：将 OVERWRITE 分支的 saveRecord 纳入与 executeTask 相同的 try/catch + resolveInsertRaceWinner 兜底；冲突时按 handleExistingRecord(raceWinner,...) 分流。
12. **置信度**：高。
13. **备注**：delete→insert 窗口内第三方 findRecordByIdempotentId 查不到记录会走新插入并撞唯一键——该第三方若是主路径则有兜底，不另立案。
14. **复核状态**：降级 P2（条件化）；新发现；位点与机制核实成立（L255-260 OVERWRITE 无兜底、saveRecord 全类恰 2 处已 grep 证实、delete 0 行容忍后 insert 撞 UK），但 P1 依据"违反 owner doc L33"属过延——nop-retry.md L33 明文限定"两方同时以同一幂等键**首发**时"，OVERWRITE 重提竞争窗口不在声明字面范围，残留缺陷定性为与 plan 2282 修复模式同族的漏网位点；影响有界（败方收到裸约束冲突异常，无损坏/无挂起/无状态机破坏，UK 守住不变量，败方可重试），且触发需 OVERWRITE 策略 + 已有未完成记录 + 两方并发重提，按判级规则降 P2。

### [nop-retry-nop-tcc-C1-003] retry 记录终态写与运维/竞争写并发：乐观锁冲突异常在 whenComplete 回调内被丢弃，驱动 future 永不完成或半成功无恢复

1. **发现ID**：nop-retry-nop-tcc-C1-003
2. **严重度**：P1（罕见但可具体化的竞态；异步异常静默吞掉致状态不一致 + 扫描线程永久阻塞 + 半个成功无恢复语义）
3. **缺陷族**：F10（load→check→update 守卫非原子）+ F9（吞异常致并发状态不一致）的 C1 侧面
4. **位置**：
   - `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java` L112-134（pause）、L137-155（resume）、L329-392（executeImmediateRetry）、L422-447（executeSingleAttempt）、L499-527（handleExecutionSuccess/Failure）
   - `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java` L281-283（updateRecord → updateEntityDirectly）
   - 语义来源（平台侧，已下钻核实）：`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlHelper.java` L342-351（UPDATE WHERE 含 `AND version=?`）、`EntityPersisterImpl.java` L508-524（0 行且未 disable 版本错误时抛 ERR_ORM_UPDATE_ENTITY_NOT_FOUND）
5. **状态载体与读写方**：`nop_retry_record` 行（DB，乐观锁 version 列）+ 各执行链持有的 ORM 内存副本。写方：执行链终态写（handleExecutionSuccess/Failure、moveToDeadLetter）与运维写（pause/resume）、OVERWRITE 竞争写；读方：各自内存副本上的状态检查。pause/resume 的"检查-然后-行动"跨 load 与两次独立 DB 调用，无原子性；执行链的终态写也不重读 DB。两写方相遇时 ORM 乐观锁使后写者失败（0 行 → 抛异常），该异常的落点决定后果。
6. **证据片段**（RetryEngineImpl.java L340-358，回调内先做 DB 写、异常即中断 result 完成）：
    ```java
    doExecute(record, request, cancelToken)
            .whenComplete((response, ex) -> {
                if (ex != null) {
                    int retryCount = (record.getRetryCount() != null ? record.getRetryCount() : 0) + 1;
                    if (retryCount < immediateRetryCount) {
                        record.setRetryCount(retryCount);
                        recordStore.updateRecord(record);          // 抛出 → 下方 schedule/result 均不执行
                        scheduleImmediateRetry(...)
                                .whenComplete((r, e) -> { ... result.complete...});
                    } else {
                        handleExecutionFailure(record, policy, ex, task);  // 内部 updateRecord 抛出 → 下行不执行
                        result.completeExceptionally(ex);
                    }
                } else if (response != null && response.isOk()) {
                    handleExecutionSuccess(record, policy, task);  // 内部 updateRecord 抛出 → result.complete 不执行
                    result.complete(response);
    ```
    方法 L391 `return result;`——`.whenComplete(...)` 的返回 stage（携带回调异常）被丢弃，`result` 由回调显式完成，回调中途抛出即永不完成。
7. **触发时序**：记录 R 执行中（RETRYING，scanner 租约内，内存副本 version=5）。
    - t1：管理员 pause(R)：loadRecord（读到 version=5、status=RETRYING）→ 状态检查通过 → setStatus(SUSPENDED) → updateRecord（WHERE version=5）成功，version→6。
    - t2：执行链业务调用成功返回 → handleExecutionSuccess → updateRecord（WHERE version=5）→ **0 行 → ERR_ORM_UPDATE_ENTITY_NOT_FOUND 抛出**。
    - 路径 A（executeImmediateRetry）：回调异常被丢弃，`result` 永不完成 → 同步调用方（callAsync→syncGet）永久挂起；scanner 路径下 processor 内 `CompletableFuture.allOf(futures).join()`（L648，无超时）永久阻塞一个 globalWorker 线程（doScan 经 BindScheduledExecutor 派发到 worker 执行，已核实），反复发生即 worker 池慢性失血；该 RetryScannerImpl 实例后续扫描全部停摆。
    - 路径 B（executeSingleAttempt）：异常经 whenComplete 派生 stage 传给调用方/scanner（不挂起），但业务副作用（RPC）已执行、记录停留在对方写入的 SUSPENDED 状态 → 管理员按"未执行"resume → PENDING → 扫描器重放业务调用 → **副作用重复执行**（半个成功无恢复语义）。
8. **影响**：路径 A 造成线程永久挂起与扫描停摆（长时运行下资源慢性耗尽）；路径 B 造成已成功副作用被重放。两路径均源于同一根因：终态写与运维写竞争 + 回调异常落点未设计。
9. **误报校准**：`updateEntityDirectly` 经 SQL 层核实确为版本条件更新并抛错（非静默丢失更新），因此本发现不是"DB 行丢失更新"而是"守卫竞争 + 异常落点"；声明契约"retryingTimeoutMs 执行中锁定超时"覆盖的是 scanner 间租约竞争，不覆盖运维 pause/resume 与执行链终态写的竞争；"SUSPENDED 运维暂停语义"恰是本竞态中被破坏的语义（暂停态下副作用实际已发生）。回调抛异常非业务正常失败路径，属 F9 范围（吞异常致状态不一致）。
10. **F3 同族兄弟清单**（记录状态 load→check→update 位点）：
    - `RetryEngineImpl.pause` L112-134 —— TOCTOU（本发现主位）。
    - `RetryEngineImpl.resume` L137-155 —— TOCTOU（同族未修，双 resume 幂等无危害、resume vs 终态写同构）。
    - `RetryEngineImpl.handleExistingRecord` L210-215 COMPLETED 重放判定（stale 读；失败方向是"多一次成功重放"，低危）—— 同族登记。
    - `RetryRecordStoreImpl.tryLockRecordsForProcess` L184-205 —— **正确示范**：版本 CAS + 失败者过滤，不在缺陷族内。
    - executeImmediateRetry 回调 L341-389（异常落点缺陷）vs executeSingleAttempt L429-447（异常正确传播）—— 同一回调模式的一对正反实现。
11. **修复方向**：executeImmediateRetry 回调整体 try/catch，任何异常兜底 `result.completeExceptionally`；pause/resume 改为条件更新（WHERE status=期望态）并以更新行数判定成功；终态写冲突时记录 warn 并按"副作用已发生但状态被运维接管"显式留痕。
12. **置信度**：高（ORM 乐观锁语义下钻至 SQL 生成与结果检查两层核实）。
13. **备注**：本条不作为维度集外线索展开——其"future 永不完成"侧面属在集内 C3（异步链）维度形态，此处仅作 C1 守卫原子性的后果描述，供 C3 维度按需承接。
14. **复核状态**：保留 P1；新发现；平台语义独立下钻复核成立（GenSqlHelper UPDATE WHERE 含 `AND version=?`、EntityPersisterImpl.checkUpdateResult L508-524 在 0 行且未 disable 版本错误时抛 ERR_ORM_UPDATE_ENTITY_NOT_FOUND、nop-retry.orm.xml `versionProp="version"` 证实版本列存在），回调结构（executeImmediateRetry L340-389 + L391 `return result`）与 pause/resume load→check→update TOCTOU（L112-155）逐行核实；两处非承重勘误：第 7 字段叙述中的 version 数值（5→6）为示意（实际还含 scanner tryLock 的一跳 bump，机制不变）、EntityPersisterImpl 实际路径为 `io/nop/orm/persister/`（非 `io/nop/orm/model/`），均不影响机制与判级。

### [nop-retry-nop-tcc-C1-004] TccTransactionRegistry.instance() 检查-然后-行动可产生注册表身份分裂；registry put/restore 叠放恢复在同上下文并发下互踩

1. **发现ID**：nop-retry-nop-tcc-C1-004
2. **严重度**：P2（理论竞态，依赖应用层在同一 IContext 内并发进入同 txnGroup；模块自身代码不产生该并发模式——写不出模块内可达时序，按纪律降 P2）
3. **缺陷族**：F10 检查-然后-行动（惰性初始化无同步 + 叠放式 put/restore）
4. **位置**：
   - `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransactionRegistry.java` L18-26
   - `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java` L184-206（runTaskWithExitingTxnAsync）、L236-256（runTaskWithNewTxnAsync）、L273-289（runTaskWithNewTxn）
5. **状态载体与读写方**：注册表本体是 `ContextualizedRegistry`（内部 `ConcurrentHashMap`，已核实 nop-commons ContextualizedRegistry.java L15），实例挂在 `IContext.attribute[CONTEXT_KEY]`（BaseContext.attributes 为 ConcurrentHashMap）。写/读方：runInTransaction* 系列的 get/put/restore。`instance()` 的 get→null→new→setAttribute 无同步；put(old)+whenComplete(put(old)) 是叠放恢复模式。
6. **证据片段**（TccTransactionRegistry.java L18-26）：
    ```java
    public static TccTransactionRegistry instance() {
        IContext context = ContextProvider.getOrCreateContext();
        TccTransactionRegistry registry = (TccTransactionRegistry) context.getAttribute(CONTEXT_KEY);
        if (registry == null) {
            registry = new TccTransactionRegistry();
            context.setAttribute(CONTEXT_KEY, registry);
        }
        return registry;
    }
    ```
    TccEngine.java L203-205：
    ```java
    return thenOnContext(f).whenComplete((ret, err) -> {
        registry.put(txnGroup, old);
    });
    ```
7. **触发时序**（需要"同一 IContext 实例被两线程并发使用"前提，Nop 上下文跨线程传播传同一实例，平台 `BaseContext.executeWithContext` 甚至为此打印 `nop.warn.context.concurrent-execute-with-same-context` 警告）：线程 A、B 共享请求上下文，同时首次调用 runInTransactionAsync("g")：两者都 `instance()`→getAttribute 为 null→各自 new→后 setAttribute 者赢→先者的注册表成孤儿；A、B 各自把不同 TccTransaction 注册进不同 registry→双方都走 newTransaction 分支为同一逻辑操作建两条 TCC 事务；后续 getCurrentTransaction 读到的是赢者 registry，输者事务脱管。叠放恢复互踩时序：A put(txnA)→A 的 whenComplete 在 B put(txnB) 之后执行 put(old=null)→B 的注册被清掉→B 的后续分支 join 判定失效。模块内全部调用（TccRpcServiceInterceptor / TccGatewayInterceptor）均为单请求单线程顺序进入，写不出模块内可达时序——按审计纪律降 P2 并标注：理论风险，未在模块内具体化。
8. **影响**（若前提成立）：同一逻辑操作分裂为两条 TCC 事务（双 try/双补偿）；注册表项提前清空使并发进入者误判"无事务"再次新建。属性 map 本身为 ConcurrentHashMap，无 map 结构损坏。
9. **误报校准**：注册表上下文级（非全局单例）与 `ContextualizedRegistry` 用法面属本维度盘点范围（任务书明示）；平台 ConcurrentHashMap 实现本身不立案，立案点在模块侧的 get→new→set 与 put/restore 序列。
10. **F3 同族兄弟清单**（registry 生命周期位点全枚举）：
    - `TccTransactionRegistry.instance()` L18-26 —— 惰性初始化无同步（本发现）。
    - `TccEngine.runTaskWithExitingTxnAsync` L188/197/204 put/restore —— 叠放恢复同族。
    - `TccEngine.runTaskWithNewTxnAsync` L239/254 put/restore —— 叠放恢复同族。
    - `TccEngine.runTaskWithNewTxn` L276/287 put/restore（同步版）—— 叠放恢复同族。
    - `TccEngine.runTaskWithExitingTxn` L211/216 put/restore —— 叠放恢复同族。
    全部同族未修；单线程上下文内语义正确，修复需整体设计（如 context 级锁或 compute 式原子注册）。
11. **修复方向**：instance() 改为 `context.attributes.computeIfAbsent` 等价原子形式；或在文档钉定"同一 IContext 禁止并发进入 runInTransaction* 同组"为平台契约。
12. **置信度**：中高（机制确定，模块内可达性不足）。
13. **备注**：TccGatewayInterceptor L89 `final TccContext[] newCtxRef` 数组逃逸（L96 写 / L107 读）经核查由 CompletableFuture 链的完成语义建立 happens-before，属安全发布，仅入清单不立案。
14. **复核状态**：保留 P2；新发现；instance() get→null→new→setAttribute 无同步（L18-26）与叠放 put/restore 全部 4 组位点（L188/197/204、L239/254、L276/287、L211/216）核实成立，ContextualizedRegistry 内部 CHM、BaseContext.attributes 为 ConcurrentHashMap（L46）、平台并发同上下文警告（BaseContext L255）均独立证实；模块内两拦截器调用面均单请求顺序进入，无模块内可达时序，维持 P2 并保留「理论风险，未在模块内具体化」标注。

### [nop-retry-nop-tcc-C1-005] RetryEngineImpl.executor 非同步惰性初始化（良性双初始化）

1. **发现ID**：nop-retry-nop-tcc-C1-005
2. **严重度**：P3（优化候选）
3. **缺陷族**：F10（惰性初始化守卫非原子）
4. **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java` L53、L621-630
5. **状态载体与读写方**：`private IScheduledExecutor executor`（非 volatile）。写方：setExecutor（IoC/测试）与 getExecutor 惰性赋值（任意调用线程）；读方：scheduleImmediateRetry 的调度线程。
6. **证据片段**（L621-626）：
    ```java
    protected IScheduledExecutor getExecutor() {
        if (executor == null) {
            executor = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
        }
        return executor;
    }
    ```
7. **触发时序**：两线程同时首次调用 getExecutor → 双双判空 → 双双赋值。`executeOn` 每次调用 new 一个 `BindScheduledExecutor` 包装（已核实 IScheduledExecutor.java L24-26），故两线程拿到**不同 wrapper 实例**——但 wrapper 仅绑定同一底层 globalTimer/globalWorker 单例（GlobalExecutors 内 CHM computeIfAbsent），调度行为等价，末次赋值获胜后统一使用。无功能损害。
8. **影响**：无直接损害；仅为未来引入有状态 executor 时埋雷（scheduleImmediateRetry L402-404 注释表明测试确有替换 executor 的用法，届时双初始化可能短暂将任务投给旧 executor）。
9. **误报校准**：非"无状态单例被并发调用"豁免（字段可变），但因底层池单例故降 P3。
10. **F3 同族兄弟清单**：`RetryScannerImpl.getExecutor` L165-167 直接返回新 wrapper（无缓存字段，无此问题）；全模块其余 get* 为纯读。家族规模 1 处。
11. **修复方向**：改为 final 急切初始化或 volatile + 同步；测试替换语义保留 setter。
12. **置信度**：高。
13. **备注**：无。
14. **复核状态**：保留 P3；新发现；L53 非 volatile + L621-626 惰性初始化核实成立，executeOn 每次调用 new BindScheduledExecutor 包装（IScheduledExecutor L24-26）而底层 globalTimer/globalWorker 为 CHM 单例，良性双初始化定性成立；F3 对照 RetryScannerImpl.getExecutor L165-167（已核实无缓存字段），家族闭环。

### [nop-retry-nop-tcc-C1-006] ReflectionTccServiceMetaBuilder.INSTANCE 为 public static 非 final 可变字段

1. **发现ID**：nop-retry-nop-tcc-C1-006
2. **严重度**：P3（卫生类，当前无写路径、无实际竞争）
3. **缺陷族**：C1 静态可变字段盘点（可变性未收口）
4. **位置**：`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/meta/ReflectionTccServiceMetaBuilder.java` L14
5. **状态载体与读写方**：`public static ReflectionTccServiceMetaBuilder INSTANCE = new ...`——类初始化时安全发布（static init 有 JVM 保证），当前全库仅 4 处读（DefaultTccServiceMetaLoader L64 + 3 处测试），无任何再赋值位点（已 grep 全库核实）。
6. **证据片段**（L13-14）：
    ```java
    public class ReflectionTccServiceMetaBuilder {
        public static ReflectionTccServiceMetaBuilder INSTANCE = new ReflectionTccServiceMetaBuilder();
    ```
7. **触发时序**：无可达触发时序（无写路径）——按纪律此为盘点项而非竞态立案；风险仅在于非 final 使外部代码可替换实例并以非安全方式发布。
8. **影响**：无现实影响；风格/防御性收口候选。
9. **误报校准**：grep 基线 C1"static non-final fields"节未捕获该行（模式差异），本次人工盘点补录；不与"平台基础设施自身实现"豁免冲突（本类属 tcc 模块自有代码）。
10. **F3 同族兄弟清单**：两模块 main 源码全部 static 非 final 字段仅 3 处——`ReflectionTccServiceMetaBuilder.INSTANCE`（本条）、`NopRetryApplication.globalArgs` L16、`NopTccApplication.globalArgs` L22（后两者为 main 引导先写后读，无问题）。家族闭环，无遗漏。
11. **修复方向**：加 `final`（或改私有 + 静态方法门面）。
12. **置信度**：高。
13. **备注**：DefaultTccServiceMetaLoader.serviceMetas（HashMap）仅 @PostConstruct 单线程填充、运行期只读，读侧经容器启动 happens-before 安全——入清单不立案。
14. **复核状态**：保留 P3；新发现；L14 `public static` 非 final 核实成立，全库 grep 独立复核确认无再赋值位点、static 非 final 可变字段家族恰 3 处（本条 + NopRetryApplication.globalArgs L16 + NopTccApplication.globalArgs L22），家族闭环；当前无写路径无竞争，卫生类收口维持 P3。

---

## 共享可变状态清单表

### nop-retry 分节

| # | 载体 | 位置 | 类别 | 写方 | 读方 | happens-before 机制 | 判定 |
|---|------|------|------|------|------|---------------------|------|
| R1 | RetryEngineImpl.rpcServiceInvoker/recordStore/retryScanner | RetryEngineImpl L50-52 | singleton bean 实例字段 | IoC setter（启动期一次性） | 多线程 | 容器启动→请求线程 Thread.start HB | 安全 |
| R2 | RetryEngineImpl.executor | L53, L621-630 | 实例字段惰性初始化 | getExecutor/setExecutor | scheduleImmediateRetry | 无同步（良性，见 C1-005） | P3 |
| R3 | NopRetryRecord 实例（内存副本） | executeWithRetry 链全程 | 经参数/future 链逃逸的隐式共享 | 执行链回调（RPC 完成线程/timer 线程） | 同链后续回调 | CompletableFuture 完成语义 HB（链式串行） | 安全（链内）；跨链由 DB 乐观锁仲裁，异常落点缺陷见 C1-003 |
| R4 | NopRetryAttempt 实例 | doExecute L454-488 | 同上 | doExecute 回调 | 同链 | 同上 | 安全 |
| R5 | RetryTaskImpl 可变 builder 字段 | RetryTaskImpl L23-27 | 构建期可变对象 | withXxx（构建线程） | executeTask（同线程传递） | 线程封闭惯例（平台无强制） | 安全（约定） |
| R6 | RetryScannerImpl.running | L39 | 实例字段（volatile） | start/stopScanning（synchronized） | doScan（timer→worker 线程） | volatile 写读 | 安全 |
| R7 | RetryScannerImpl.scanFuture | L40 | 实例字段（非 volatile） | start/stopScanning | 同方法内 | synchronized 互斥保护 | 安全 |
| R8 | RetryScannerImpl.scanIntervalMs/batchSize/retryingTimeoutMs | L35-37 | 配置字段 | @InjectValue（启动期） | doScan | 启动 HB | 安全 |
| R9 | RetryScannerImpl.partitionResolver | L34 | 聚合平台 PartitionResolver | setNamingService 等（启动期） | resolvePartitions（scan 线程） | 启动 HB | 安全（平台实现不审） |
| R10 | RetryRecordStoreImpl.namespaceId/groupId/daoProvider | L55-65 | 配置字段 | IoC（启动期） | 多线程 | 启动 HB | 安全 |
| R11 | nop_retry_record 行状态/version | DB | 数据库状态机 | 提交链/扫描链/运维 | 各链内存副本 | 乐观锁 CAS（version 条件更新）+ UK | CAS 在但异常落点缺陷见 C1-003；OVERWRITE 兜底缺失见 C1-002 |
| R12 | RetryRecordStoreImpl.PENDING_STATUSES | L40 | static final 不可变集合 | 无 | 多线程 | 类初始化 | 安全 |
| R13 | NopRetryApplication.globalArgs | L16 | static 非 final | main（先写） | Quarkus 启动线程（后读） | 线程启动 HB | 安全 |
| R14 | 长整型/复合递增位点：record.retryCount++（L343/371/512）、retryTimes 类推 | — | 读-改-写 | 执行链 | DB | 乐观锁使冲突方失败（不静默丢失） | 受护（后果归 C1-003） |

### nop-tcc 分节

| # | 载体 | 位置 | 类别 | 写方 | 读方 | happens-before 机制 | 判定 |
|---|------|------|------|------|------|---------------------|------|
| T1 | TccEngine.repository/serviceInvoker/exceptionChecker/apiResponseNormalizer | TccEngine L52-55 | singleton bean 实例字段 | IoC setter（启动期） | 多线程 | 启动 HB | 安全 |
| T2 | NopTccRecord/NopTccBranchRecord 内存副本（每链私有） | TccTransaction/TccBranchTransaction 全程 | 经参数/DB 加载逃逸 | 本链 updateTccStatusAsync 成功时同步内存 | 本链守卫/聚合 | 链内串行；跨链无 HB，DB CAS 仲裁但结果被弃 | **C1-001 主载体** |
| T3 | nop_tcc_record.status（DB） | TccRecordStore L160-204 | 数据库状态机 | 调用方链/恢复链 | 各链副本 | 单语句 CAS（[txnId+fromStatus]） | CAS 在，失败不阻断（C1-001） |
| T4 | nop_tcc_branch_record.status/retryTimes（DB） | TccRecordStore L220-283 | 数据库状态机 | 同上 | 同上 | 单语句 CAS（[branchId+fromStatus]）；retryTimes++ 与状态同语句受同一 CAS 保护 | CAS 在，失败不阻断（C1-001） |
| T5 | nop_tcc_record.expireTime/retryTimes 认领 | TccRecordStore L291-326 | 扫描认领 | fetchExpiredRecords | 多扫描实例 | tryUpdateManyWithVersionCheck 版本 CAS + 失败重试 100 轮 | 安全（正确示范） |
| T6 | TccTransactionRegistry（IContext 属性） | TccTransactionRegistry L15-31 | 上下文级注册表（内部 CHM） | runTaskWith* put/restore | getCurrentTransaction | CHM 原子单操作；get→new→set 与叠放 restore 无原子性 | **C1-004**（P2） |
| T7 | IContext.attributes（TccContext 存取） | TccContext L62-81 + BaseContext L46 | 上下文属性 map（ConcurrentHashMap） | setCurrent/removeCurrent（拦截器/网关） | getCurrent | CHM 单操作原子；removeAttribute(k,v) 身份删除 | 安全（传播机制如上节描述；平台 ThreadLocal 绑定自身不审） |
| T8 | DefaultTccServiceMetaLoader.serviceMetas | L24 | 实例 HashMap（初始化后只读） | @PostConstruct init 单线程 | getMethodMeta（RPC 线程） | 启动 HB | 安全 |
| T9 | ReflectionTccServiceMetaBuilder.INSTANCE | L14 | static 非 final | 无（当前） | 多线程 | 类初始化 | C1-006（P3） |
| T10 | TccGatewayInterceptor.newCtxRef | L89-110 | 单元素数组逃逸 | 任务 lambda（任意线程） | whenComplete（任意线程） | CompletableFuture 完成语义 HB | 安全（清单备案，见 C1-004 备注） |
| T11 | TccEngine.checkExpiredTransactions 驱动线程 | L315-343 | 外部驱动循环 | fetch/补偿 | 同线程 | 顺序执行；`.get(expireGap)` 超时不取消在途补偿 | 超时泄漏并入 C1-001 次生时序 |
| T12 | NopTccApplication.globalArgs | L22 | static 非 final | main | 启动线程 | 线程启动 HB | 安全 |
| T13 | long/double 与复合递增位点 | TccRecordStore L233 retryTimes++；无共享 long/double 裸读写 | 读-改-写 | 补偿链 | DB | retryTimes++ 在 CAS 语句内；内存侧 L235 CAS 失败时内存漂移（并入 C1-001 低危备注） | 受护/低危 |

---

## 覆盖度与残留边界

**文件覆盖**：两模块 82 个手写文件全部枚举（find 结果逐个归类）；其中并发状态载体相关的 13 个文件逐行精读（RetryEngineImpl、RetryScannerImpl、RetryRecordStoreImpl、RetryTaskImpl、TccEngine、TccTransaction、TccBranchTransaction、TccRunner、TccTransactionRegistry、TccHelper、DefaultTccServiceMetaLoader、ReflectionTccServiceMetaBuilder、TccRecordStore）+ 2 个拦截器（TccRpcServiceInterceptor、TccGatewayInterceptor）+ 平台语义核实文件 6 个（TccContext、ContextualizedRegistry、BaseContext、IOrmEntityDao/OrmEntityDao/OrmSessionImpl/EntityPersisterImpl/GenSqlHelper/JdbcEntityPersistDriver/BindScheduledExecutor/GlobalExecutors）。api 层接口/数据 Bean/常量接口/空壳 BizModel/实体无字段子类/meta 资源模块按类别清点，确认无隐藏可变静态或 ThreadLocal（与 grep 基线 C1 节交叉验证一致，并补录基线漏捕的 ReflectionTccServiceMetaBuilder.INSTANCE）。

**规模包络对照**：手写规模与 owner doc 声明的引擎/DAO/服务/集成分层相符；两模块主代码零自建队列/池/信号量（基线 C4/C8 复核一致），锁载体仅 RetryScannerImpl 两个 synchronized 方法（本维度核对其守卫字段 running volatile / scanFuture 锁内访问，均安全）；重试状态在 DB、无内存任务注册表——未做幻影盘点。

**发布安全**：两模块无构造器启动线程/注册监听器位点（基线 C4 交叉验证）；异步发布全部经 ScheduledExecutor/CompletableFuture 链建立 HB。

**残留边界**（本维度明确不深究、留给对应机制/维度）：
1. 平台 `PartitionResolver`、`GlobalExecutors`、`ContextProvider` 线程绑定、NopIoC 容器启动 HB 保证、ORM 会话/事务管理器自身实现——按"平台基础设施自身实现不审"豁免，仅核实了 updateEntityDirectly 的版本条件语义与 BaseContext.attributes 的 CHM 事实。
2. 参与方服务自身的幂等性（TCC 契约由业务承担），C1-001 的对撞后果以其为缓解项。
3. "future 永不完成"（C1-003 路径 A）的异步链语义细节属在集内 C3 维度形态，本文件仅记录后果与锚点；按 overlay 规则 C3 形态不标维度集外线索。
4. **维度集外线索（CL）**：本轮未发现 C2/C4/C5/C9 形态的 P0/P1 线索，CL 序列空缺（无条目）。
5. Owner doc drift 两条（nop-retry.md 子模块表 4/8、nop-tcc.md 5/9）已登记不立案。

**发现汇总**：C1-001（P1，tcc CAS 失败不阻断补偿）、C1-002（P1，retry OVERWRITE 无 UK 兜底）、C1-003（P1，retry 终态写竞争异常落点）、C1-004（P2，registry 身份分裂/叠放恢复）、C1-005（P3，executor 惰性初始化）、C1-006（P3，static 非 final INSTANCE）。

---

## 维度复核结论

- 复核人：C1 维度独立复核子代理（与初审互不独立个体，怀疑姿态逐条复核）
- 复核输入：本文件（第 1 轮 C1-001~006）+ `C1-round2.md`（第 2 轮 C1-007~009）+ live code（nop-retry/、nop-tcc/ main 源码）+ 历史对照面（cra-index.md、plans 2282/2386/2388/342/347/2461–2466、owner docs）
- 复核方法：逐条亲开发现引用的 file:line 核对证据；平台语义声明（ORM 乐观锁、FutureHelper、ContextualizedRegistry、BaseContext、IScheduledExecutor、IEntityDao、TccStatus 编码与谓词）全部下钻源码独立证实；F3 同族清单逐项 grep 验证完备性；现状对照 cra-index 台账与 plans（WI 1.1–1.5 审计对象为 nop-stream/nop-job/nop-wf/nop-task，与本单元代码无交集；plans 2282/2386/2388/342/347 修复的是不同位点/不同面，经 git log 核实对应 commit 真实存在且不覆盖本维度任何发现）。

### 逐条裁定表

| 发现ID | 裁定 | 现状标注 | 一句话理由 |
|--------|------|---------|-----------|
| nop-retry-nop-tcc-C1-001 | 保留 P1 | 新发现 | 证据逐行核实、时序每步有代码锚点、前置为超时特性正常工作场景（2 条件非 ≥3 罕见合取），cra-index/历史 plans 无同条目 |
| nop-retry-nop-tcc-C1-002 | 降级 P2（条件化） | 新发现 | 位点与机制成立，但 P1 依据"违反 owner doc L33"过延（该声明限定"并发首提"窗口），影响有界（裸异常可重试、无损坏/挂起），触发条件化 |
| nop-retry-nop-tcc-C1-003 | 保留 P1 | 新发现 | ORM 乐观锁语义独立下钻证实（version 条件更新 + 0 行抛错），回调异常落点与 pause/resume TOCTOU 逐行核实；两处非承重勘误见子项明细 |
| nop-retry-nop-tcc-C1-004 | 保留 P2 | 新发现 | 机制全部证实，模块内无可达时序，维持「理论风险，未在模块内具体化」标注 |
| nop-retry-nop-tcc-C1-005 | 保留 P3 | 新发现 | 惰性初始化与良性双初始化定性核实，底层池单例故无功能损害 |
| nop-retry-nop-tcc-C1-006 | 保留 P3 | 新发现 | static 非 final 可变字段家族 grep 闭环（恰 3 处），当前无写路径，卫生类 |
| nop-retry-nop-tcc-C1-007 | 保留 P2 | 新发现 | 守卫/快照/恢复面缺口/入口闭合四项核心事实全部独立证实，变体 B（孤儿分支永久悬挂）无条件可达；变体 A 需配置条件（见子项批注），与 P2 条件化判级相符 |
| nop-retry-nop-tcc-C1-008 | 保留 P2 | 新发现 | 主位与次生位点逐行核实，retry 链 8 个 whenComplete 位点矩阵 grep 复核吻合，FutureHelper 语义下钻证实；一处非承重措辞勘误见条目内复核状态 |
| nop-retry-nop-tcc-C1-009 | 保留 P2 | 新发现 | 推延窗口/拾取条件/租约仅扫描器间/常量不同源四项逐行核实，单条件（单调用 >600s）可达；时序完备性达 P1 线，维持 P2 的裁量理由见子项明细 |

### P1 与调整项子项复核明细

**C1-001（保留 P1）**
- 证据：全部引用片段与 live code 一致（行号漂移 ≤3 行，以实际代码为准：CAS 失败吞为 warn 实际在 TccRecordStore L188-193）；`updateTccBranchStatusAsync` L220-283 同构 CAS-失败-不阻断核实成立。
- 时序：t1 恢复链与 t2 调用方链的每一步均有代码锚点；关键前提"调用方分支副本在恢复链分支 CAS 提交前装载"对应 endAsync 入口的 `loadBranchTransactionsAsync`（TccTransaction L59），窗口真实存在。复核另证实一条更宽的对撞路径：分支 finishTry 与恢复链分支 CAS 相遇时，败方同样"warn 后照发 RPC"——缺陷面比原叙述更宽，不改变判级。
- 判级：维持 P1。后果为参与方侧 confirm/cancel 对撞（TCC 互斥不变量破），参与方幂等性只缓解同类重复不缓解对撞；触发前提是超时控制的设计场景。
- F3：8 个同族位点逐一核实（doCancelAsync L100-108、doConfirmAsync L122-130、runBranchConfirmAsync L75、runBranchCancelAsync L101、beginConfirmAsync L92-104、beginCancelAsync L125-138、runBranchTransactionAsync L295、checkTransactionActive L170-182 + updateTccStatusAsync 内存漂移 L173-186），无缺漏。
- 误报排除：nop-tcc.md"崩溃恢复对称续跑"声明的是单链恢复路由（P1-3.8-004，plan 2388），不含调用方/恢复互斥声明；代码注释表明互斥意图，属实现缺口非声明边界。

**C1-003（保留 P1）**
- 证据：回调结构（`.whenComplete` 内 DB 写 + `return result`）逐行核实；`updateRecord → updateEntityDirectly`（RetryRecordStoreImpl L281-283）核实；ORM 乐观锁三层下钻全部独立证实（GenSqlHelper UPDATE 含 `AND version=?`；EntityPersisterImpl.checkUpdateResult L508-524 0 行抛 ERR_ORM_UPDATE_ENTITY_NOT_FOUND；nop-retry.orm.xml `versionProp="version"`）。
- 时序：pause 与执行链终态写相遇的交错可信（运维 pause 是正常操作）；路径 A（immediate 回调吞异常 → result 永不完成 → scanner `allOf().join()` L648 永久阻塞 worker）与路径 B（singleAttempt 异常传播误报失败 → resume 重放副作用）两条后果链均复核成立。
- 判级：维持 P1（线程慢性失血 + 副作用重放，运维操作即可触发相遇）。
- 勘误（不立案）：① 叙述 version 数值示意化（实际含 tryLock bump）；② EntityPersisterImpl 实际在 `io/nop/orm/persister/` 而非 `io/nop/orm/model/`，行号 L508-524 吻合。
- F3：6 项清单（pause/resume/handleExistingRecord/tryLockRecordsForProcess 正例/正反回调对）核实完备。

**C1-002（降级 P2）子项明细**
- 证据：核实成立——OVERWRITE 分支 L255-260 delete+insert 无兜底；主路径 L178-187 有 resolveInsertRaceWinner；`saveRecord` 全类 grep 恰 2 处，F3 闭环；deleteEntityDirectly 0 行无异常（ORM 层无 delete-not-found 错误路径），主变体（delete no-op 后 insert 撞 UK）成立。
- 判级调整理由：① owner doc 契约字面范围——nop-retry.md L33"并发**首提**兜底……不以裸约束冲突暴露"限定首次提交窗口，OVERWRITE 重提竞争不在其字面范围内，原 P1 的"违反已声明契约"依据减弱为"与 plan 2282 修复模式同族的漏网位点"（resolveInsertRaceWinner 注释虽预告了并发 OVERWRITE 可致竞胜者消失，但那只说明主路径兜底把 OVERWRITE 视为外部成因，不构成对 OVERWRITE 分支自身的契约声明）；② 影响有界：败方收到 ERR_SQL_DUPLICATE_KEY 裸异常，无数据损坏（UK 守住单记录不变量）、无挂起、无状态机破坏，败方可重试；③ 触发条件化：需 OVERWRITE 策略 + 已有未完成记录 + 两方并发重提。
- 现状：plan 2282（commit 03d05ed88c）修复的是主路径先查后插窗口，未覆盖本位点，无任何台账条目 → 新发现。

**C1-007（保留 P2）子项批注**
- 四项核心事实独立证实：① L295 守卫读调用方链私有副本（beginAsync 后仅本链 CAS 成功时同步，L194）；② endAsync L58-59 分支批次入口快照；③ fetchExpiredRecords L301 `lt(status, CONFIRM_SUCCESS(11))` 排除全部终态（TccStatus 编码 CONFIRM_SUCCESS=11/CANCEL_SUCCESS=12/BIZ_CANCEL_FAILED=13/TIMEOUT_SUCCESS=14/KILLED=15 已核对源码）；④ 分支无独立恢复入口（`getBranchRecordsAsync` main 源码调用链闭合于 endAsync，grep 双向核实）。
- 变体 B（终态父下孤儿分支永久悬挂，removeCompletedRecords 仅按父快照 txnIds 清证据）无条件可达，独立支撑 P2。
- 变体 A 批注（不改判级）：发现叙述的配置（B1 常规分支被取消至 TIMEOUT_SUCCESS/CANCEL_SUCCESS）下，调用方 confirm 批次会在 B1 处被 beginConfirmAsync 的同步抛异常中断（该状态不在 TRY_SUCCESS/CONFIRMING/CONFIRM_FAILED/CONFIRM_SUCCESS 允许集），confirm RPC 不会到达 B3；变体 A 的"跨终态 confirm 照发"仅在前置分支不拦截批次的配置下成立（前置分支为自动确认 CONFIRM_SUCCESS 被跳过 / 无前置分支 / 前置分支处于 CONFIRMING 续跑态）。此为触发面收窄批注，守卫缺口 + 恢复缺口的事实与 P2 条件化判级不变。
- 现状：无历史条目 → 新发现；与 C1-001 的承接关系（修 C1-001 消除变体 A 的 RPC 但不消除变体 B）经核实成立。

**C1-009（保留 P2）裁量理由**
- 事实全部核实：newRecord L116-119 固定 600s 推延且注释自声明"只在调用方失联后接管"；拾取条件 L163-164 对在途调用链不可见；tryLock L184-205 版本 CAS 仅仲裁扫描器之间；保护窗口用 API 层硬编码常量（NopRetryApiConstants L28）与扫描器可配置 retryingTimeoutMs（RetryScannerImpl L71-74）不同源。
- 维持 P2 的裁量：时序完备性实际达到 P1 线（单一条件即可达），但①租约缺失设计下"超时即接管"是固有形态，>10min 单调用属非典型而非正常工作负载；②重试引擎对 scanner 路径本为 at-least-once 语义，被破坏的是代码注释自声明的意图而非 owner doc 契约。是否升 P1 留给 7.1 合并裁定（副作用重复对资源型操作的后果权重属裁定输入，非复核事实问题）。

### 统计

- **保留 9 条**：P1×2（C1-001、C1-003）、P2×5（C1-004、C1-007、C1-008、C1-009、C1-002 降级后计入）、P3×2（C1-005、C1-006）
- **降级 1 条**：C1-002（P1→P2，契约范围过延 + 影响有界 + 条件化）
- **驳回 0 条**
- **现状标注**：新发现 ×9（历史审计与 cra-index 台账均无同条目；plans 2282/2386/2388/342/347 修复位点与本维度发现无交集，经 git log 核实）

---

## 最终保留项

| 发现ID | 标题 | 最终严重度 | 现状标注 |
|--------|------|-----------|---------|
| nop-retry-nop-tcc-C1-001 | TCC 全局/分支状态 CAS 失败不阻断补偿 RPC，互斥守卫基于各链私有过期内存副本 | P1 | 新发现 |
| nop-retry-nop-tcc-C1-003 | retry 记录终态写与运维/竞争写并发：乐观锁冲突异常在 whenComplete 回调内被丢弃，驱动 future 永不完成或半成功无恢复 | P1 | 新发现 |
| nop-retry-nop-tcc-C1-002 | retry OVERWRITE 分支 delete+insert 无 UK 冲突兜底，并发覆盖以裸约束冲突暴露 | P2（降级） | 新发现 |
| nop-retry-nop-tcc-C1-004 | TccTransactionRegistry.instance() 检查-然后-行动可产生注册表身份分裂；registry put/restore 叠放恢复在同上下文并发下互踩 | P2 | 新发现（理论风险，未在模块内具体化） |
| nop-retry-nop-tcc-C1-007 | 全局取消认领后分支仍可创建（守卫读永不刷新的内存副本），且终态父事务下的分支失去一切恢复入口 | P2 | 新发现 |
| nop-retry-nop-tcc-C1-008 | retry 显式 future + whenComplete 转发模式：生产者/调度体同步抛出使驱动 future 永不完成 | P2 | 新发现 |
| nop-retry-nop-tcc-C1-009 | retry 首执行防双执行仅靠 newRecord 一次性推延 nextTriggerTime，长调用下扫描器与在途调用链并发双执行 | P2 | 新发现 |
| nop-retry-nop-tcc-C1-005 | RetryEngineImpl.executor 非同步惰性初始化（良性双初始化） | P3 | 新发现 |
| nop-retry-nop-tcc-C1-006 | ReflectionTccServiceMetaBuilder.INSTANCE 为 public static 非 final 可变字段 | P3 | 新发现 |

（驳回项：无；全部 9 条 ID 均保留在案。）
