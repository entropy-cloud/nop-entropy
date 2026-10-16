# C3 异步链路正确性审计 — nop-retry + nop-tcc（第 2 轮深挖）

- 审计单元：`nop-retry`（8 jar）+ `nop-tcc`（9 jar）
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15；审计维度：C3 异步链路正确性（第 2 轮深挖，盲审）
- 输入：第 1 轮 C3-async-chains.md（C3-001~011，唯一豁免读取文件）+ live code + owner doc（nop-retry.md / nop-tcc.md）+ grep 基线；未读取 ai-dev/ 下其他任何文件（含 C1-round2.md 等同目录其他维度文件）。
- 本轮深挖方向：C3-002 失效面（聚合判定矩阵全单元格）、C3-005 失效面（SUSPENDED 转换 / DELETE 路径旧链处置）、取消传播链全入口、retry 回调与 tcc 补偿链 IContext 传递、死信重放异步语义。

---

## 1. 本轮发现

### [nop-retry-nop-tcc-C3-012]
- **编号**：nop-retry-nop-tcc-C3-012
- **严重度**：P1
- **缺陷族**：F2（半个成功）+ F9（异常归因错误）
- **标题**：全局终态聚合写不校验「CANCELLING/CONFIRMING 中间态 CAS 是否生效」——首跳 CAS 以异常完成时聚合读到未驱动分支的 TRY_SUCCESS，终态 SUCCESS 以陈旧 from 状态直写成功：分支补偿零执行、事务静默完结（无双链对撞要求，单链可达）
- **位置**：nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java:110-116（doCancelAsync：CAS 异常→thenCompose 跳过→聚合照跑→终态写）、132-137（doConfirmAsync 同型）；nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:162-197（updateTccStatusAsync 无目标态前置校验，fromStatus 取自内存，异常时内存不回写）；nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java:178-196、198-217（两个聚合函数均无「本分支是否被驱动过」概念，未驱动=非失败态=折叠为 SUCCESS）
- **证据片段**：
  ```java
  // TccTransaction.doCancelAsync
  CompletionStage<Void> future = getRepository().updateTccStatusAsync(tccRecord, TccStatus.CANCELLING, null)
          .thenCompose(r -> TccRunner.cancelAllAsync(branchTxns, timeout, tccEngine.getServiceInvoker()));
  // 首跳CAS抛异常 → thenCompose整体跳过（分支零驱动），直接进入：
  return FutureHelper.whenCompleteAsync(future, (response, err) -> {
      TccStatus status = TccRunner.aggregateCancelBranchStatus(branchTxns);   // 全是TRY_SUCCESS → CANCEL_SUCCESS
      return getRepository().updateTccStatusAsync(tccRecord, status, err);   // from=内存TRYING → TRYING→CANCEL_SUCCESS
  });
  // TccRecordStore.updateTccStatusAsync：无任何"中间态必须已生效"校验
  Integer fromStatus = tccRecord.getStatus();   // 首跳异常时仍是TRYING
  long updated = recordDao().updateByQuery(statusCasQuery(tccRecord.getTxnId(), fromStatus), props);
  ```
- **触发时序**：单链（存活调用方或恢复扫描任一）进入 doCancelAsync：第一步 `TRYING→CANCELLING` 的 REQUIRES_NEW 写库遭遇瞬时异常（连接被重置/锁等待超时/主从切换秒级抖动——FutureHelper.futureCall 对同步异常 reject，TccRecordStore.java:162-163 + FutureHelper.java:122-129）→ thenCompose 跳过，cancelAllAsync 从未执行，branchTxns 内存状态保持 fresh-load 的 TRY_SUCCESS → whenCompleteAsync 聚合（FutureHelper.java:354-370，err!=null 照常回调）→ 无 BIZ_CANCEL_FAILED/CANCEL_FAILED/TIMEOUT_FAILED → 返回 CANCEL_SUCCESS → 第二步 `updateTccStatusAsync(CANCEL_SUCCESS, err)` 以内存 from=TRYING 发起 CAS，DB 已恢复则 1 行命中 → 全局=CANCEL_SUCCESS（finished，endTime 落库），分支全部停留 TRY_SUCCESS。恢复路径变体：全局 CANCELLING 续跑（endAsync(true,...) 路由 doCancelAsync）时首跳 `CANCELLING→CANCELLING` 异常，聚合读到 fresh-load 的 TRY_SUCCESS/CANCELLING 分支（均非失败态）→ 同样折叠 CANCEL_SUCCESS。doConfirmAsync 对称：TRYING→CONFIRM_SUCCESS，分支零 confirm。
- **影响**：TCC 不变量「进入 CANCELLING/CONFIRMING 后必须补偿到终态」被异常路径旁路——预留资源永久悬挂且全局表显示正常完结；调用方（runTaskWithNewTxnAsync 的 completeAsyncOnContext 链）收到正常完成的业务结果；err 参数仅作为 errorCode 写入记录列，不改变终态判定。与 C3-002 的区别：无需双链对撞与 RPC 结果分歧，仅要求"第一次写失败、毫秒级后第二次写成功"的 DB 瞬时故障形态，单链即可达。
- **与已声明契约的对照**：owner doc 崩溃恢复契约（对称续跑、cancel 开始后不允许 confirm）以「全局状态机真实流转」为前提；本缺陷使终态可以从未经过中间态直接落成。CAS 的 from 条件保护的是"行不被 stale writer 覆盖"，未保护"终态不得跳过中间态"——语义缺口，非声明边界。
- **误报校准说明**：非无状态并发；触发窗口窄（两次 REQUIRES_NEW 写之间 DB 恢复）但每一步有明确代码落点且无任何守卫拦截；后果方向性最严重（未补偿→报成功），按 campaign 口径「罕见但可具体化 + 半个成功」判 P1（与第 1 轮 C3-006 判级同标准）。
- **修复方向建议**：聚合终态写之前校验"本链已将全局推入对应中间态"（首跳 CAS 结果显式传递，异常/0 行时终态写改写为 CANCEL_FAILED/CONFIRM_FAILED 或直接失败）；updateTccStatusAsync 增加终态目标的前置状态白名单（CANCEL_SUCCESS 仅接受 from=CANCELLING，CONFIRM_SUCCESS 仅接受 from=CONFIRMING）。
- **置信度**：高（机制链完整，每处落点均经 FutureHelper/ContextProvider 实现核对，无假说环节）。
- **交叉引用**：C3-001（同函数的 0 行变体——本发现是异常完成变体）、C3-002（聚合读内存的同一根因在异常路径的新单元格）、C3-008（恢复重驱动是另一类触发源）。
- **复核状态**：保留 P1；新发现；首跳 CAS 异常→thenCompose 整体跳过→err!=null 照常聚合（TccTransaction.java:110-116/:132-137 + FutureHelper.java:354-370 err 透传，均已亲核）→陈旧 from 直写终态，单链可达、双写窗口与 C3-006/C3-016 同级，逐环核实成立，维持 P1。

### [nop-retry-nop-tcc-C3-013]
- **编号**：nop-retry-nop-tcc-C3-013
- **严重度**：P2
- **缺陷族**：F1（异步活引用跨时序）+ F3（状态守卫缺失，旧链未取消族）
- **标题**：retry pause/resume 无状态 CAS 守卫、在途执行链持活实体无隔离回写：SUSPENDED 被在途链 handleExecutionFailure 直更覆盖回 PENDING（暂停静默失效、副作用继续）；反向交错把 COMPLETED 翻成 SUSPENDED，resume 后扫描器重执行已成功的业务方法
- **位置**：nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:111-134（pause：read-check-write 无 CAS）、136-155（resume）、509-527（handleExecutionFailure：522-525 对在途链持有的 record 实例 setStatus(PENDING)+updateRecord）、499-507（handleExecutionSuccess 同型）；nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:280-283（updateRecord→updateEntityDirectly）；nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:534-547（updateDirectly→flushUpdate 按主键直写脏属性，无版本谓词；版本校验仅存在于 lazy-load 路径 OrmSessionImpl.java:396-404）
- **证据片段**：
  ```java
  // RetryEngineImpl.pause——先读后写，无CAS，无对在途链的任何 fences
  int status = record.getStatus() != null ? record.getStatus() : 0;
  if (status == RETRY_RECORD_STATUS_SUSPENDED) throw ...;
  if (status == RETRY_RECORD_STATUS_COMPLETED) throw ...;
  record.setStatus(RETRY_RECORD_STATUS_SUSPENDED);
  recordStore.updateRecord(record);          // 独立实体实例，按主键直更
  // RetryEngineImpl.handleExecutionFailure——异步回调线程，持有同一行的另一个实例
  record.setNextTriggerTime(new Timestamp(nextTriggerTime));
  record.setStatus(RETRY_RECORD_STATUS_PENDING);   // 无条件覆盖DB当前值
  recordStore.updateRecord(record);          // updateEntityDirectly 无版本条件
  ```
- **触发时序**：(a) 正向覆盖：记录被扫描器锁定（RETRYING）且 attempt 在途，运维 pause（RETRYING 通过守卫，写 SUSPENDED 成功）→ attempt 失败返回 → 回调线程 handleExecutionFailure 对链上实例 setStatus(PENDING)+updateRecord → updateDirectly 无版本谓词，SUSPENDED 被 PENDING 覆盖 → 下个扫描周期（scanIntervalMs 默认 5s）重新拾取，被暂停的任务继续执行业务副作用，运维无任何告警。(b) 反向翻终态：pause 读到 status=PENDING（通过守卫）→ 链的 attempt 成功返回写 COMPLETED → pause 的写后到 → 行变 SUSPENDED（业务已成功）→ 同幂等键新提交按 handleExistingRecord 走 handleBlockStrategy 被 DISCARD/OVERWRITE 分流（幂等键被僵尸行占据）；运维 resume → PENDING + nextTriggerTime=now → 扫描器 executeRetryFromScanner 重新执行已成功的业务方法（双执行），扫描路径不经过 COMPLETED 幂等重放检查。
- **影响**：暂停语义在记录最需要被暂停的时刻（正在疯狂重试=attempt 在途是常态而非例外）系统性失效；反向形态造成已完成任务的重复执行。均为静默，仅 DB 行 status 可查。
- **与已声明契约的对照**：owner doc 声明「SUSPENDED 是运维暂停，重复提交不偷偷恢复执行」——(a) 形态正是"偷偷恢复执行"，声明语义与实现不符；(b) 形态超出声明的覆盖面（声明只管重复提交路径，未管 pause/resume 与执行链的交错）。缺陷而非边界。
- **误报校准说明**：pause/resume 是低频运维入口，但触发窗口=attempt 在途期，与"运维选择暂停的动机"高度重合（暂停的对象正是执行中的任务）；非有界声明结构问题。综合判 P2。
- **修复方向建议**：pause/resume 改条件更新（updateByQuery 按 [id+from 状态] CAS，0 行即抛状态冲突）；执行链回写前重载行状态校验（或 handleExecution* 的 updateRecord 改为 CAS from=本链锁定态，0 行视为记录已被运维/其他链接管，放弃回写并告警）。
- **置信度**：高（updateDirectly 无版本谓词已经 OrmSessionImpl 实现核对）。
- **交叉引用**：C3-003（在途链长期化的另一成因）、C3-005（同族"旧链未取消"，本发现是其 SUSPENDED 转换方向的失效面）、第 1 轮盘点表中 pause/resume 入口（初审未发现，本轮补登记）。
- **复核状态**：保留 P2；新发现；pause/resume 读-查-写无 CAS（RetryEngineImpl.java:111-155）+ 在途链无条件回写（:499-527）+ OrmSessionImpl.updateDirectly 无版本谓词（:534-547，复核亲核确认）成立，双向交错时序均可具体化；F3 失效面补充（不改判级）：executeImmediateRetry 链内 retryCount 写（:345-346/:373-374）属同族无条件直更，修复时应一并纳入。

### [nop-retry-nop-tcc-C3-014]
- **编号**：nop-retry-nop-tcc-C3-014
- **严重度**：P3
- **缺陷族**：生命周期/取消语义（取消传播不完整）
- **标题**：引擎 doStop 的取消面不含已调度的即时重试定时器：scheduleImmediateRetry 丢弃 ScheduledFuture 且无在途链登记，doStop 仅 stopScanning——停机后 globalTimer 中到期的定时器仍发起业务 RPC 并回写记录
- **位置**：nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:394-420（scheduleImmediateRetry：407 行 executor.schedule 返回的 ScheduledFuture 被丢弃，仅返回业务 future）、654-660（doStop 只调 retryScanner.stopScanning()）、621-626（getExecutor=globalTimer().executeOn(globalWorker())）
- **证据片段**：
  ```java
  // scheduleImmediateRetry——定时器句柄未保存，无取消管道
  executor.schedule(() -> {
      executeImmediateRetry(record, policy, request, cancelToken, task)...
      return null;
  }, immediateRetryIntervalMs, TimeUnit.MILLISECONDS);   // ScheduledFuture 被丢弃
  protected void doStop() {
      if (retryScanner != null) {
          retryScanner.stopScanning();   // 唯一的停机取消动作
  ```
- **触发时序**：即时重试链第 N 轮失败，scheduleImmediateRetry 排定 N+1 轮（interval 可达分钟级）→ 运维 stop 引擎（doStop→stopScanning）→ 应用进入关闭流程但进程未退 → 定时器到期 → executeImmediateRetry 照常 doExecute：发起业务 RPC、写 attempt、updateRecord。已调度定时器的集合在引擎内无任何登记（对比 scanFuture 有句柄可 cancel）。
- **影响**：优雅停机后仍产生业务副作用与簿记写；关闭期数据源先关闭时这些写以异常告终（沿 whenComplete 传播到无消费者的 future）。单独危害有限（daemon 线程、窗口=最后一个 interval），与 C3-011 同族。
- **与已声明契约的对照**：owner doc 未声明停机等待/取消语义（同 C3-011 的边界缺口）；scanFuture 有句柄而 retry 定时器无句柄是实现不对称，非声明取舍。
- **误报校准说明**：低频路径（停机时刻 + pending 定时器 + 进程未立即退出三重条件），按判级规则 P3。
- **修复方向建议**：引擎维护 pendingRetryFutures 集合，doStop 统一 cancel(true)；或即时重试任务体执行前检查 engine running 标志短路。
- **置信度**：高。
- **交叉引用**：C3-011（同族：stopScanning cancel(false) 不中断在途 doScan）、C3-005（定时器即幽灵链的载体之一）。
- **复核状态**：保留 P3；新发现；executor.schedule 返回的 ScheduledFuture 被丢弃（RetryEngineImpl.java:407-417）+ doStop 仅 stopScanning（:654-660）核实成立，维持 P3。

### [nop-retry-nop-tcc-C3-015]
- **编号**：nop-retry-nop-tcc-C3-015
- **严重度**：P3
- **缺陷族**：F3（去重盲区/异步协调缺失）
- **标题**：死信重放绕过幂等键机制：retryFromDeadLetter 裸 invokeAsync 不建 record，在途重放对同键新提交完全不可见——重放与同键 executeTask 并发时 DISCARD/OVERWRITE 均无法阻塞，双执行无任何协调点
- **位置**：nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:76-109（108 行裸 invokeAsync，无幂等检查、无在途登记）；171-175（executeTask 的去重入口 findRecordByIdempotentId 只查 nop_retry_record 表）；nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:229-244（findRecordByIdempotentId 实现现状）
- **证据片段**：
  ```java
  // retryFromDeadLetter——校验只覆盖字段非空，执行前无任何同键互斥
  ApiRequest<?> request = JsonTool.parseBeanFromText(requestPayload, ApiRequest.class);
  return rpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, null);   // 直发，不建record
  // 而新提交路径的去重完全依赖 record 表：
  NopRetryRecord existingRecord = recordStore.findRecordByIdempotentId(...);   // 查不到在途重放
  ```
- **触发时序**：运维对死信 D 发起 retryFromDeadLetter，重放 RPC 慢（在途数分钟）；期间业务方以同一 (namespaceId, groupId, idempotentId) 重新提交（死信化后该键恰好空闲，这是 owner doc 声明的"死信后同幂等键可重新提交"用法）→ executeTask 查 record 表为空 → 新链正常执行 → 同一业务请求两路并发执行。两次手动重放并发（双击）同型。
- **影响**：业务副作用双执行，且无任何记录留下并发证据（重放不落任何表）。触发依赖人工操作与慢 RPC 叠加，频率低。
- **与已声明契约的对照**：owner doc 声明重放「fire-and-forget，不新建 record、不改死信状态；不是重新入队」——声明覆盖簿记面，但未声明"重放在途窗口内幂等键保护整体失效"这一后果；「幂等键支持」作为模块头号特性，其保护范围在重放路径的缺口未被声明覆盖，属声明与实现的缝隙而非已声明边界。
- **误报校准说明**：手动低频入口 + 需同键重提交/二次重放并发叠加，按判级规则 P3。
- **修复方向建议**：重放期间以临时 record 行（或分布式锁）占住幂等键，完成后删除/转状态；最低成本方案：重放入口对同死信加进行中标记（DB 字段或锁表），二次重放与同键新提交在窗口内被 DISCARD。
- **置信度**：高（代码事实直接）/ 中（实际触发频率评估）。
- **交叉引用**：C3-005（双执行的另一入口，但 OVERWRITE 有键可查、本发现连键都不可见）。
- **复核状态**：保留 P3；新发现；裸 invokeAsync 不建 record（RetryEngineImpl.java:108）+ 去重仅查 nop_retry_record（:171-175）+ 死信化删行释放幂等键（RetryRecordStoreImpl.java:309）核实成立；grep 确认裸发入口仅 retryFromDeadLetter 一处（triggerCallback 走 executeTask 自带 `_callback` 键去重），F3 完备，维持 P3。

---

## 2. 排除项/定向核查

以下为本轮按深挖指令逐项定向核查后确认干净、或归并为前轮发现失效面（不另立编号）的条目：

1. **waitAll 语义（C3-002 矩阵前置）**：`FutureHelper.waitAll` = `CompletableFuture.allOf`（FutureHelper.java:389-413）——等待全部分支 future 完成（含失败），聚合不会被在途分支短路。「聚合早于分支完成读到中间态」的单元格排除。
2. **C3-002 失效面矩阵全单元格核对（confirm/cancel 两方向）**：
   - cancel 方向：被 isBranchCancellable 跳过的 cancelled 类分支（TRY_FAILED/CANCEL_SUCCESS/TIMEOUT_SUCCESS/BIZ_CANCEL_FAILED/CONFIRM_SUCCESS/KILLED）落入 CANCEL_SUCCESS 为正确语义（无需补偿）；CREATED 分支行不可能持久化（saveBranchRecordAsync 才落库）。
   - confirm 方向的 CAS-失败-内存不回写机制与 C3-002 同根（begin/finish 双 CAS 均适用），按纪律归并为 C3-002 失效面，不另立编号。
   - **新结果单元格（登记为 C3-010 失效面扩展）**：`aggregateConfirmBranchStatus` 遇 cancelled 分支时 throw（TccRunner.java:208-214），该 throw 发生在 `FutureHelper.whenCompleteAsync` 的 handler 内被 catch（FutureHelper.java:365-368）→ `updateTccStatusAsync` 被整体跳过 → 全局卡 CONFIRMING，恢复扫描每轮重拾 bump retryTimes，耗尽后按已声明边界放弃。触发前置与 C3-010 相同（需 C3-001 对撞或脏数据产生 CONFIRMING 全局 + cancelled 分支的组合），后果从"误报成功"变为"卡死中间态"，不另立编号。
   - null 状态分支在聚合侧被静默 continue（告警后不参与判定）：驱动侧排除已由注释声明；聚合侧的残留是"未知状态分支被全局 SUCCESS 折叠"，属脏数据前提下的接受残留，登记不判级。
3. **C3-005 失效面（DELETE 路径旧链处置）**：`moveToDeadLetter` 删除原行（RetryRecordStoreImpl.java:285-310）与 OVERWRITE 的 delete 同为"删行不取消在途链"，在途链回写落空机制完全同型（0 行静默）；双链同达 max-retry 各写一条死信为数据冗余（idempotentId 非唯一已声明）。均归并 C3-005 失效面，不另立编号。SUSPENDED 转换方向的新机制已单独立案为 C3-013。
4. **取消传播链全入口清点**：stopScanning（C3-011）；scanFuture 定时任务体 running 边界检查（干净）；scheduleImmediateRetry 丢弃 ScheduledFuture（新立案 C3-014）；TCC 侧无任何 future.cancel 调用（checkExpiredTransactions 的 canceller 仅在记录间检查，记录内 endAsync 不可中断——有界，接受）；retry 调用方 cancelToken 取消仅作用于当次 invokeAsync，记录仍由扫描器续跑——符合 retry 引擎"调用方弃后继续"的模块形态（owner doc 未声明取消语义，注记为未定义语义边界，不判级）。
5. **上下文传播复核（维持第 1 轮结论，全部干净）**：
   - `runInTransactionAsync(txnGroup, txnId)` 入口无 thenOnContext 包装，但 `FutureHelper.futureCall` 同步执行（FutureHelper.java:122-129，完成态 ResolvedPromise），thenCompose 在调用线程内联执行——registry 挂载/恢复同上下文，无断裂。
   - `runTaskWithNewTxnAsync`：thenOnContext0 经 `context.execute` 完成 promise（ContextProvider.java:186-200），外层 whenComplete 的 registry 恢复在 context 绑定线程执行——干净。
   - `TccGatewayInterceptor` 的 TccContext 恢复 whenComplete 挂在 thenOnContext 结果上，经 context.execute 完成——恢复动作绑定正确 context；TccContext 存储于 IContext 属性而非裸 ThreadLocal（TccContext.java:62-81），无池化线程残留面。
   - TCC 补偿链上下文：恢复扫描线程以空上下文驱动 confirm/cancel，参与者定位依赖 try 时刻写入 requestData 快照的 txn 头（TccRecordStore.java:113-123），快照自足——干净。
   - retry 侧 IContext 不入异步链（回调/扫描线程空上下文执行，@Transactional REQUIRES_NEW 不依赖调用方上下文）——模块形态，排除。
6. **死信重放其余语义**：fire-and-forget 本身已声明；无超时（C3-004 族）但为调用方可见 future 的手动一次性调用，不另立案；幂等键盲区已立案 C3-015。
7. **checkExpiredTransactions 外层 while 有持续超时记录时不退出**：由 fetchExpiredRecords 每轮 bump retryTimes + maxRetryCount 上限有界化，终局为已声明的放弃边界——排除。
8. **endAsync whenComplete 中补偿异常仅在 ex!=null 时记日志**（TccTransaction.java:86-94）：err2 本身仍会作为 endAsync 的异常完成传播给调用方，仅日志观测面有缺口，非并发正确性缺陷——排除。

---

## 3. 终止声明

本轮产出新发现（C3-012 P1、C3-013 P2、C3-014/C3-015 P3），故不输出"未发现新的高价值问题"终止信号。

深挖盲区清单已全部执行完毕：聚合判定矩阵两方向全单元格穷举（干净单元格与归并单元格见第 2 节）、SUSPENDED/DELETE 旧链处置形态已核查并立案、取消传播全入口清点完毕（无未登记入口残留）、上下文传播经平台设施实现核对维持第 1 轮结论、死信重放异步语义核对完毕。**剩余无未探明的高价值线索，本维度深挖在第 2 轮后具备终止条件，不建议第 3 轮。**

---

## 4. 覆盖声明

**本轮新增覆盖（第 1 轮未深入的定向面）**：
- TccTransaction.doCancelAsync/doConfirmAsync 的异常完成路径（第 1 轮仅覆盖 0 行正常完成路径）——产出 C3-012。
- 聚合判定矩阵两方向全部输入单元格（含 waitAll 语义、whenCompleteAsync 异常吞噬语义的实现核对）。
- RetryEngineImpl.pause/resume 与在途执行链的交错（第 1 轮盘点列入入口清单但未产出发现）——产出 C3-013；含 OrmSessionImpl.updateDirectly 无版本谓词的实现核对。
- 引擎生命周期取消面（scheduleImmediateRetry 的 ScheduledFuture 丢弃、doStop 取消范围）——产出 C3-014。
- retryFromDeadLetter 全路径（第 1 轮仅登记为已声明 fire-and-forget）——产出 C3-015。
- 平台设施语义核对：FutureHelper.waitAll/futureCall/whenCompleteAsync/bindCancelToken、ContextProvider.thenOnContext/thenOnContext0/completeAsyncOnContext、TccContext（IContext 属性存储）、OrmEntityDao.tryUpdateManyWithVersionCheck 与 updateDirectly 的版本语义对比——仅用于校准本单元缺陷，不作为发现对象。

**仍排除（与第 1 轮一致）**：平台设施自身内部实现（GlobalExecutors、IRpcServiceInvoker 具体实现的超时配置）；测试代码与 `_gen/` 生成物；消费方模块（nop-job-retry-adapter 等）；ORM lazy-load 版本校验路径的自身合理性。
