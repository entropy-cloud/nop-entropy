# C3 异步链路正确性审计 — nop-retry + nop-tcc（第 1 轮盲审）

- 审计单元：`nop-retry`（8 jar，40f/4,100 行手写）+ `nop-tcc`（9 jar，42f/3,872 行手写）
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15；审计维度：C3 异步链路正确性（第 1 轮初审，盲审）
- 盲审纪律：未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；仅以 live code + owner doc（nop-retry.md / nop-tcc.md）+ grep 基线（_tmp/cra-baseline-nop-retry-nop-tcc.txt）为输入。

## 检查范围说明（异步入口清单）

C3 维度盘点的全部异步入口（逐一定位并审查异常传播、取消/超时、上下文传播、时序假设）：

**nop-retry**
| 入口 | 位置 | 驱动方式 |
|---|---|---|
| 提交方执行链 `executeTask → executeWithRetry → executeImmediateRetry/executeSingleAttempt → doExecute` | RetryEngineImpl.java:168-489 | 调用方线程发起，CompletableFuture whenComplete 链在 RPC 回调线程续跑 |
| 即时重试定时续跑 `scheduleImmediateRetry` | RetryEngineImpl.java:394-420 | GlobalExecutors.globalTimer().executeOn(globalWorker()) 定时调度（RetryEngineImpl.java:621-626） |
| 回调任务 `triggerCallback → executeTask` | RetryEngineImpl.java:541-574 | fire-and-forget，whenComplete 仅记日志 |
| 扫描器定时驱动 `RetryScannerImpl.startScanning → doScan` | RetryScannerImpl.java:89-155 | scheduleWithFixedDelay，任务体投递 globalWorker |
| 扫描批处理 `doStart` 传入的 processor | RetryEngineImpl.java:632-652 | `CompletableFuture.allOf(futures).join()` 阻塞等待全批完成 |
| 死信重放 `retryFromDeadLetter` | RetryEngineImpl.java:76-109 | fire-and-forget（owner doc 已声明） |
| 生命周期 `pause/resume`、store 各 `@Transactional` 写 | RetryEngineImpl.java:111-155, RetryRecordStoreImpl.java 全文件 | 同步调用，被异步链在各回调线程调用 |

**nop-tcc**
| 入口 | 位置 | 驱动方式 |
|---|---|---|
| 发起方事务链 `runTaskWithNewTxnAsync`（beginAsync → task → endAsync） | TccEngine.java:236-256 | thenOnContext/completeAsyncOnContext 绑定上下文 |
| 参与既有事务 `runInTransactionAsync(txnGroup, txnId) → runTaskWithExitingTxnAsync` | TccEngine.java:144-155, 184-206 | registry 挂载/恢复 |
| 分支 try `TccRunner.runBranchTryAsync` | TccRunner.java:40-70 | beginTryAsync → invokeAsync → finishTryAsync |
| 分支 confirm `runBranchConfirmAsync`；批量 `confirmAllAsync` | TccRunner.java:72-96, 133-150 | begin 状态 CAS → confirm RPC → finish CAS |
| 分支 cancel `runBranchCancelAsync`；批量 `cancelAllAsync` | TccRunner.java:98-122, 164-176 | 同上（timeout 语义） |
| 全局补偿路由 `TccTransaction.endAsync → doConfirmAsync/doCancelAsync` | TccTransaction.java:58-138 | 内存状态路由 + FutureHelper.whenCompleteAsync 聚合 |
| 外部驱动恢复扫描 `checkExpiredTransactions → checkExpiredAsync → endAsync(true,...)` | TccEngine.java:315-348 | 外部调度（模块形态预注：无内置定时 bean），逐条 `.get(expireGap)` |
| 入站上下文桥接 `TccRpcServiceInterceptor.interceptAsync` | TccRpcServiceInterceptor.java:38-55 | thenOnContext 包装 + TccContext setCurrent/removeCurrent |
| 网关事务包装 `TccGatewayInterceptor.invoke` | TccGatewayInterceptor.java:68-112 | thenOnContext 包装，请求头赢 |

上下文传播设施（ContextProvider.thenOnContext / completeAsyncOnContext、TccContext、TccTransactionRegistry）按依赖读了实现以核对传播契约，不作为发现对象（平台设施自身实现按误报校准排除）；审计结论中仅记录其与本单元代码交互产生的缺陷。

---

## 发现条目

### [nop-retry-nop-tcc-C3-001]
- **编号**：nop-retry-nop-tcc-C3-001
- **严重度**：P1
- **缺陷族**：F2（半个成功）+ 时序假设失效
- **标题**：TCC 全局/分支状态 CAS 写失败（0 行）不短路补偿链，继续下发 confirm/cancel RPC——协调器自身不阻止双向重复补偿
- **位置**：nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:188-196、270-281（CAS 0 行仅告警后正常完成）；nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java:105-108、79-82（begin 完成后无条件 thenCompose 下发 RPC）；nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java:110-111、132-133
- **证据片段**：
  ```java
  // TccRecordStore.updateTccBranchStatusAsync
  long updated = branchDao().updateByQuery(query, props);
  if (updated == 0) {
      LOG.warn("nop.tcc.skip-branch-status-update-due-to-conflict:...");
      return null;          // stage 正常完成，调用方无法区分成功/失败
  }
  // TccRunner.runBranchCancelAsync
  return branchTxn.beginCancelAsync(timeout).thenCompose(arg -> {
      ...
      serviceInvoker.invokeAsync(branchRecord.getServiceName(), branchRecord.getCancelMethod(), ...)
  ```
- **触发时序**：同一条事务被两条链并发驱动（存续调用方超时后二次 endAsync，或恢复扫描与存活调用方对撞，见 C3-008 幽灵链放大）。链 A 的分支 CAS `TRY_SUCCESS→CANCELLING` 胜出；链 B 的同分支 CAS 以内存快照 TRY_SUCCESS 为 from 条件，DB 已变 → 0 行告警后 stage 正常完成 → `thenCompose` 照常下发第二条 cancel（或方向相反的 confirm）RPC。分支 CAS 的保护意图（终态不被 stale writer 覆盖）只落到"状态行不被覆盖"，未落到"补偿 RPC 不重复下发"。
- **影响**：同一分支并发收到两条补偿（甚至 confirm 与 cancel 各一条）RPC；参与者侧幂等头（txnId/branchId，TccRecordStore.java:113-123 写入请求快照）只是 opt-in 的业务侧防线，协调器层无强制的方向互斥。同时 begin 的"同值 CAS"（CANCELLING→CANCELLING，from=to）必然算成功，导致 retryTimes 每次对撞都 +1，配合 maxRetryTimes=100 上限被对撞流量虚耗，正式恢复周期被提前淘汰。
- **与已声明契约的对照**：owner doc 声明"cancel 开始后不允许 confirm（TCC 语义）、执行表 auth 收敛、恢复对称续跑"。本发现指出该互斥在实现层依赖内存状态守卫 + CAS，两者在并发双驱动下都不拦 RPC 下发——属于契约宣称的语义与实现之间的缺口，非已声明边界。
- **误报校准说明**：非无状态并发（有 DB 状态机与外部副作用 RPC）；触发路径具体化（见时序）；非测试代码。
- **修复方向建议**：`updateTccStatusAsync`/`updateTccBranchStatusAsync` 返回 updated 标志（或 0 行时以异常完成 stage）；`runBranch*Async` 在 CAS 未生效时短路不下发 RPC；同值 CAS 单独判定（from==to 视为幂等重入，不计 retryTimes 或直接跳过）。
- **置信度**：高（代码路径完整可循，两处证据均为主路径）。
- **交叉引用**：C3-002（后果收敛面）、C3-008（幽灵链是双驱动的持续来源）、C1-001（恢复扫描对撞）。
- **复核状态**：保留 P1；新发现（plan 2388 硬化的是路由与守卫，0 行不短路缺口未覆盖；G7-14 明言 tcc 聚合细节未逐行复核）；三处证据（TccRecordStore.java:189-192/:274-278 值完成不短路、TccRunner.java:79/:105 begin 后无条件 thenCompose、TccTransaction.java:110/:132）逐行核实成立，双驱动时序每步有代码落点，同值 CAS 必中且计 retryTimes（TccRecordStore.java:232-235）属实。

### [nop-retry-nop-tcc-C3-002]
- **编号**：nop-retry-nop-tcc-C3-002
- **严重度**：P1
- **缺陷族**：F2（半个成功——本单元核心检查面）
- **标题**：补偿聚合读"内存分支状态"，CAS 失败的分支状态不回写内存 → 全局终态可误报 CANCEL_SUCCESS，而 DB 分支实为 CANCEL_FAILED，预留资源永久悬挂
- **位置**：nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java:178-196（aggregateCancelBranchStatus 读 getBranchStatus()）；nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java:113-116（whenCompleteAsync 聚合）；nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:226、274-281（CAS 失败不回写内存 status）；nop-tcc/nop-tcc-api/src/main/java/io/nop/tcc/api/TccStatus.java:301（恢复扫描 `status < CONFIRM_SUCCESS(11)` 不再拾取已"完结"事务）
- **证据片段**：
  ```java
  // TccRunner.aggregateCancelBranchStatus——只看内存状态
  if (status == TccStatus.CANCEL_FAILED)  return TccStatus.CANCEL_FAILED;
  ...
  return TccStatus.CANCEL_SUCCESS;
  // TccRecordStore.updateTccBranchStatusAsync——0 行时内存 status 保持旧值
  if (updated == 0) { LOG.warn(...); return null; }
  record.setStatus(status.getCode());   // 仅成功才回写
  ```
- **触发时序**：链 A（首驱动）与链 B（恢复重驱动）先后对同分支下发 cancel（承接 C3-001：B 的 begin CAS 丢失但 RPC 照发）。A 的 cancel RPC 失败 → finish 以内存 from=CANCELLING CAS `CANCELLING→CANCEL_FAILED` 胜出（DB=CANCEL_FAILED）；B 的 cancel RPC 成功 → B 的 finish 以 B 内存 from=CANCELLING CAS `CANCELLING→CANCEL_SUCCESS` 撞 A 已写值 → 0 行跳过，B 内存分支保持 CANCELLING → B 聚合读到 CANCELLING（非三种失败态）→ 返回 CANCEL_SUCCESS → B 的全局 CAS `CANCELLING→CANCEL_SUCCESS` 若先于 A 的 `CANCELLING→CANCEL_FAILED` 到达则胜出。终局：全局=CANCEL_SUCCESS（12 ≥ 11，恢复扫描永久排除）、分支=CANCEL_FAILED——参与者预留资源无任何机制再补偿。
- **影响**：TCC 不变量"分支 CANCEL_FAILED 必须续跑至成功"被全局误报终态打破；悬挂为静默故障，仅分支行 cancelErrorCode 可查。
- **与已声明契约的对照**：owner doc 的恢复对称续跑契约（CONFIRMING/CANCELLING→续跑、max-retry-times 100 淘汰）以"全局状态未完结"为前提；本缺陷使前提在并发窗口内失真。缺陷而非声明边界。
- **误报校准说明**：触发需要双链对撞 + 两次 RPC 结果分歧（一败一成），罕见但每一步都可具体化，属 P1"罕见但可具体化竞态 + 半个成功"。
- **修复方向建议**：聚合改为读 DB 权威状态（或 CAS 失败时重载分支状态后再聚合）；全局终态 CAS 之前按分支 DB 状态做最终裁决。
- **置信度**：中高（依赖两次 CAS 到达顺序，但每个中间态都有明确代码落点）。
- **交叉引用**：C3-001（机制前置）、C3-008（幽灵链提供稳定重驱动源）。
- **复核状态**：保留 P1；新发现；聚合读内存（TccRunner.java:178-196/:198-217）+ CAS 失败不回写（TccRecordStore.java:274-281）+ 恢复排除谓词 status<CONFIRM_SUCCESS（TccRecordStore.java:301）三环核实成立，坏交错（B begin 晚于 A finish → B begin/finish 双 0 行 → B 折叠 SUCCESS → B 全局终态先达）每步可信；一处叙述瑕疵不改级——恢复路径（timeout=true）B 的 finish 终态应为 TIMEOUT_SUCCESS 而非 CANCEL_SUCCESS，两者均 finished(≥11)，误报结论不变（与 round4 §2.2 勘误同口径）。

### [nop-retry-nop-tcc-C3-003]
- **编号**：nop-retry-nop-tcc-C3-003
- **严重度**：P1
- **缺陷族**：F1（异步快照跨时序假设）+ 重复副作用
- **标题**：retry 即时重试链跨异步边界持有 record 且全程不续租 nextTriggerTime，租约（retryingTimeoutMs，默认 600s）到期后扫描器并发接管同一幂等任务，双执行业务 RPC
- **位置**：nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:116-119（仅首次执行推迟一个租约周期）；nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:340-392（executeImmediateRetry 链内只改 retryCount，不改 nextTriggerTime）、509-527（handleExecutionFailure 才在链尾设 nextTriggerTime）；nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:128-147 + RetryRecordStoreImpl.java:40-43、162-164、182-205（PENDING/RETRYING 且 nextTriggerTime<=now 即拾取并 PENDING→RETRYING）
- **证据片段**：
  ```java
  // RetryRecordStoreImpl.newRecord——注释明确了首次执行的防护意图
  // 到期时间推迟一个租约周期：调用方发起首次执行期间记录保持PENDING且nextTriggerTime=now，
  // 会被扫描器误拾取并发起第二次执行（重复副作用）。推迟后扫描器只在调用方失联后接管
  record.setNextTriggerTime(new Timestamp(CoreMetrics.currentTimestamp().getTime()
          + NopRetryConstants.DEFAULT_RETRYING_TIMEOUT_MS));
  // executeImmediateRetry 失败续跑分支——无任何租约续期
  record.setRetryCount(retryCount);
  recordStore.updateRecord(record);
  scheduleImmediateRetry(record, policy, request, cancelToken, task, immediateRetryIntervalMs)
  ```
- **触发时序**：(a) 单机：policy 配 immediateRetryCount=40、immediateRetryIntervalMs=20s → 链长约 13 分钟 > 10 分钟租约；提交方线程驱动即时重试链期间，DB 行仍为 PENDING、nextTriggerTime=createTime+10min → 第 10 分钟扫描器拾取，tryLock 乐观锁成功（行版本只在锁时变过一次）→ RETRYING → executeRetryFromScanner 再跑 executeWithRetry → 与提交方链并发执行同一业务方法。(b) 跨节点/下游黑洞：首次 attempt RPC 挂起 11 分钟（invokeAsync 无超时，cancelToken=null），10 分钟租约到期后任意节点扫描器接管 → 双执行。两链还对同一行交错 updateRecord / 写 attempt（attemptNo=retryCount+1 相同 → UK 冲突，再触发 C3-006 的混淆路径）。
- **影响**：幂等键保护只挡"新提交"，不挡"扫描器接管在途任务"——业务副作用双执行；attempt 记录错乱；扫描器自身也被长链 join 拖死（C3-004）。
- **与已声明契约的对照**：owner doc 声明 retryingTimeoutMs 为"执行中锁定超时"，但未声明"执行链必须在租约内完成或续租"；newRecord 注释只堵了首次执行，未覆盖即时重试链与无超时 RPC——防护范围小于声明语义，属缺陷。
- **误报校准说明**：非有界声明结构问题——租约与链长均为可配置量，交叠窗口具体化成立。
- **修复方向建议**：即时重试每轮 attempt 前 CAS 续租 nextTriggerTime（租约被抢则中止链）；或扫描器拾取前校验"状态=RETRYING 且租约未过"双条件并拒绝接管仍在提交方手里的 PENDING（引入执行方心跳/接管令牌）。
- **置信度**：高。
- **交叉引用**：C3-004（同根的扫描线程阻塞）、C3-006（attemptNo UK 连锁）、C1-001。
- **复核状态**：保留 P1；新发现；租约默认 600s（NopRetryApiConstants.java:28 DEFAULT_RETRYING_TIMEOUT_MS）与即时重试链全程无续租（RetryEngineImpl.java:343-347/:371-375 仅写 retryCount，nextTriggerTime 仅 handleExecutionFailure:522-523 才设）核实成立；扫描器拾取条件 PENDING/RETRYING+nextTriggerTime<=now（RetryRecordStoreImpl.java:162-164）+ 版本 CAS 锁（:195-204）确认提交方在途的 PENDING 行可被接管；UK_RETRY_ATTEMPT_RECORD_NO（nop-retry.orm.xml:365）证实 attemptNo 冲突连锁；两个触发形态（长即时重试链 > 租约、首attempt RPC 挂起 > 租约）均单条件可达。

### [nop-retry-nop-tcc-C3-004]
- **编号**：nop-retry-nop-tcc-C3-004
- **严重度**：P1
- **缺陷族**：F9 邻域（异步链无超时/无取消 → 活性丧失）
- **标题**：扫描批处理以 `CompletableFuture.allOf(futures).join()` 无超时阻塞共享 globalWorker 线程，单个挂死 RPC 令该节点重试扫描永久停摆
- **位置**：nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:632-652（doStart processor）；458-463（doExecute invokeAsync 传 cancelToken=null，无 orTimeout/超时包装）；nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:96-101（scheduleWithFixedDelay 下一次扫描须等本次返回）
- **证据片段**：
  ```java
  retryScanner.startScanning(records -> {
      CompletableFuture<?>[] futures = records.stream()
              .map(record -> executeRetryFromScanner(record, null)   // cancelToken = null
                      .whenComplete((resp, ex) -> { if (ex != null) LOG.error(...); })
                      .toCompletableFuture())
              .toArray(CompletableFuture[]::new);
      // 等待所有 record 执行完成
      CompletableFuture.allOf(futures).join();   // 无超时
  });
  ```
- **触发时序**：批次内任一下游服务黑洞挂起（不关连接、不回包），其 invokeAsync future 永不完成 → join() 永不返回 → doScan 任务体占住 globalWorker（默认 30 线程之一）→ scheduleWithFixedDelay 不再触发下一轮 → 本节点全部 16 分区的待重试记录停摆；即时重试续跑也调度在同一 globalWorker，持续消耗剩余 worker。无任何超时/取消逃逸路径。
- **影响**：主路径（重试引擎的调度主循环）可达的无限期停摆；停摆期间租约继续到期，多节点部署下由其他节点接管（掩盖），单节点部署则重试能力整体丧失。
- **与已声明契约的对照**：owner doc/模块形态预注声明"retry 调度走 GlobalExecutors"，未声明批处理必须整体阻塞——join 是实现选择而非契约；无超时属缺陷。
- **误报校准说明**：非平台设施自身问题（GlobalExecutors 正常），缺陷在本单元对它的使用方式；触发具体化（下游挂起是重试引擎的存在理由，概率不低）。
- **修复方向建议**：批处理改异步汇聚（allOf.thenAccept 回调驱动，不阻塞调度线程）；对每条 record 链加 `orTimeout(retryingTimeoutMs)` 或传入可取消 token；join 移出调度线程。
- **置信度**：高。
- **交叉引用**：C3-003（停摆与接管互为因果）、C3-011（stopScanning 不等待在途扫描）。
- **复核状态**：保留 P1；新发现；allOf(futures).join() 无超时（RetryEngineImpl.java:648）+ scanner 传 cancelToken=null（:638）+ doExecute 透传（:458-463）+ scheduleWithFixedDelay 串行语义（RetryScannerImpl.java:96-101）四处核实成立；下游黑洞即重试模块的核心场景，停摆路径是调度主循环，P1 成立。

### [nop-retry-nop-tcc-C3-005]
- **编号**：nop-retry-nop-tcc-C3-005
- **严重度**：P1
- **缺陷族**：F1（异步快照活引用）+ 幽灵任务
- **标题**：OVERWRITE 阻塞策略删除旧行后不取消在途异步链：旧链的 in-flight RPC/已调度即时重试继续执行业务副作用，终态回写落在已删行上（静默 0 行或异常），形成幽灵任务与双执行
- **位置**：nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:255-260（OVERWRITE：delete→save→execute 新链，旧链无感知）；340-392/394-420（旧链在途 attempt 与已调度续跑不受任何取消控制）；463-488（doExecute 完成回调对旧 record 对象回写）；nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:280-283（updateEntityDirectly 直更，行已删时 0 行或异常）；全模块无任何 cancelToken 对调度续跑的取消管道
- **证据片段**：
  ```java
  case NopRetryConstants.BLOCK_STRATEGY_OVERWRITE:
      LOG.info("nop.retry.block-strategy-overwrite:recordId={}", existingRecord.getSid());
      recordStore.deleteRecord(existingRecord);          // 旧链不知情
      NopRetryRecord newRecord = recordStore.newRecord(task, request);
      recordStore.saveRecord(newRecord);
      return executeWithRetry(newRecord, policy, request, cancelToken, task);
  // scheduleImmediateRetry：定时器仍持有旧 record，到期照常执行
  executor.schedule(() -> { executeImmediateRetry(record, ...) ... }, immediateRetryIntervalMs, ...)
  ```
- **触发时序**：任务 A 首次 attempt 在途（下游慢，nextTriggerTime=createTime+10min）→ 调用方超时，同幂等键重提，policy=OVERWRITE → 删 A 行、建 B 行、执行 B → A 的 RPC 最终完成：(i) 成功 → handleExecutionSuccess 对已删行 updateRecord（0 行静默丢状态或抛异常），且 triggerCallback 仍执行、仍会以 `A_callback` 幂等键落新记录；(ii) 失败 → handleExecutionFailure 回写同样落空，已调度的 scheduleImmediateRetry 定时器到期后**再次发起业务 RPC**。B 链同时也在执行同一业务方法 → 双执行。
- **影响**：OVERWRITE 的"以新请求替换"语义在异步面上不成立——旧请求的副作用无法撤回也无法停止；产生孤儿 attempt/回调记录；若 ORM 直更抛异常，异常沿旧链 whenComplete 传播，污染旧链调用方 already 返回的 future（无消费者时仅日志）。
- **与已声明契约的对照**：owner doc 声明 OVERWRITE="删除已有未完成记录并以新请求替换"——未声明"替换会中止在途执行"（也做不到），但当前实现使替换后新旧两条链同时存活，超出声明语义，属缺陷（幽灵任务族，维度第 3 项点名检查面）。
- **误报校准说明**：触发窗口=旧链在途期间发生同键重提，调用方超时重提正是 retry 模块的目标场景，不属低频。
- **修复方向建议**：记录级"代际号/epoch"，OVERWRITE 递增；旧链每次回调先 CAS 校验代际再回写/续跑；至少在 OVERWRITE 时对已知在途 record 打 canceled 标记并让回调短路。
- **置信度**：高。
- **交叉引用**：C3-003（同类双执行，不同入口）、C3-006（回写异常混淆路径）。
- **复核状态**：保留 P1；新发现；OVERWRITE 删行不取消旧链（RetryEngineImpl.java:255-260）+ 定时器持旧 record（:407-417）+ 回写落空（RetryRecordStoreImpl.java:280-283）核实成立；F3 兄弟站点经 grep 穷尽：全模块删行点仅 OVERWRITE（RetryEngineImpl.java:257）与 moveToDeadLetter（RetryRecordStoreImpl.java:309）两处，后者已由 round2 §2.3 归并本发现失效面，无缺漏。

### [nop-retry-nop-tcc-C3-006]
- **编号**：nop-retry-nop-tcc-C3-006
- **严重度**：P1
- **缺陷族**：F9（异步异常归因错误 → 状态不一致/重复副作用）
- **标题**：doExecute 的 whenComplete 内联执行 attempt 簿记写库，簿记异常覆盖业务结果 → 已成功的业务调用被判定为失败并重试（重复副作用）
- **位置**：nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:463-488（doExecute whenComplete 内 saveAttempt）；429-447（executeSingleAttempt 外层 whenComplete 以 (null, ex) 进入 handleExecutionFailure）；341-389（executeImmediateRetry 同型：ex!=null 分支无法区分业务失败与簿记失败）；RetryRecordStoreImpl.java:141-149（saveAttempt 二次保存走 updateEntityDirectly）
- **证据片段**：
  ```java
  return rpcServiceInvoker.invokeAsync(...)
      .whenComplete((response, ex) -> {
          ...
          recordStore.saveAttempt(attempt);   // 抛异常则整个 stage 以簿记异常完成，response 丢失
      });
  // executeSingleAttempt 外层：
  .whenComplete((response, ex) -> {
      if (ex != null) {
          handleExecutionFailure(record, policy, ex, task);   // 簿记异常被当成业务失败 → retryCount++、重排
  ```
- **触发时序**：第一次 saveAttempt（invoke 前）成功 → 业务 RPC 成功返回 → 回调线程执行第二次 saveAttempt 时遭遇瞬时 DB 故障（连接池打满/锁超时/主从切换秒级抖动）→ stage 以簿记异常完成，response 被替换 → 外层判定失败 → handleExecutionFailure 使 retryCount+1 并重排/重扫 → 同一业务副作用再次执行。并发双链下 attemptNo 相同还会先撞 `UK(recordId, attemptNo)`（RetryRecordStoreImpl.java:130-139），同型混淆。
- **影响**："成功被执行为失败"——恰是重试系统最危险的错误方向；错误码/错误栈记录的是簿记异常，排障误导。
- **与已声明契约的对照**：owner doc 声明"每次执行尝试写一条 NopRetryAttempt"，未声明簿记失败可以改判业务结论；缺陷而非边界。
- **误报校准说明**：需要"首写成功、二写失败"的窄窗口，罕见但可具体化；后果方向性严重（成功→重试），按 P1"罕见但可具体化 + 半个成功"判级。
- **修复方向建议**：先捕获 response/ex 到局部变量并完成业务判定，簿记写放 `handleRunAfter`（`whenCompleteAsync` + 独立 try/catch 记日志），或用 handle 分层：业务结论层与簿记层解耦。
- **置信度**：高（机制确凿，窗口窄）。
- **交叉引用**：C3-003（attemptNo UK 冲突来源）、C3-005（同类回写落空）。
- **复核状态**：保留 P1；新发现；簿记写内联于 whenComplete（RetryEngineImpl.java:487）+ 外层 ex!=null 即判业务失败（:430-432）+ executeImmediateRetry 同型（:341-389）核实成立，「首写成功/二写瞬时 DB 失败」时序每步可信且无守卫拦截，成功被改判为失败的方向性后果成立。

### [nop-retry-nop-tcc-C3-007]
- **编号**：nop-retry-nop-tcc-C3-007
- **严重度**：P2
- **缺陷族**：时序假设（上下文 registry 的 LIFO 恢复假设）
- **标题**：TccTransactionRegistry 挂载/恢复依赖"嵌套链按 LIFO 完成"，并发嵌套（外层先完成）时恢复错位，上下文残留已完结事务，后续同组调用 join 到 stale 事务产生孤儿分支
- **位置**：nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:186-206（runTaskWithExitingTxnAsync：old 捕获 + 完成时 put(old)）、239-255（runTaskWithNewTxnAsync 同型，恢复挂在整链含 endAsync 之后）；nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransactionRegistry.java:18-26（registry 挂在 IContext 属性上）
- **证据片段**：
  ```java
  ITccTransaction old = registry.put(txnGroup, txn);
  ...
  return thenOnContext(f).whenComplete((ret, err) -> {
      registry.put(txnGroup, old);      // 假设本链是最后完成者
  });
  ```
- **触发时序**：同一 IContext 上同 txnGroup 并发嵌套两条链（业务在事务 task 内 fire-and-forget 发起第二个同组 TCC 调用）：链外层先 put(txn1, old=null)、链内层 put(txn2, old=txn1)；若外层任务 future 先完成 → 外层恢复 put(txnGroup, null)；内层后完成 → put(txnGroup, txn1)——上下文永久残留已完结的 txn1 → 同上下文下一次 `runInTransactionAsync(txnGroup)`（无 txnId 分支，TccEngine.java:226-229 registry.get 命中）join 到 finished 事务：分支 beginTryAsync 走 saveEntityDirectly 无 CAS 插入 TRYING（TccRecordStore.java:208-218），附着在已完结事务上，confirm/cancel 均被 doConfirmAsync/doCancelAsync 的 finished 守卫（TccTransaction.java:103-108、129-130）no-op → try 副作用已发生但永无补偿。
- **影响**：孤儿分支（参与者预留悬挂）+ 误入 finished 事务的报错面；需要"同上下文同组并发嵌套且外层先完成"的特定形态，故 P2。
- **与已声明契约的对照**：owner doc 多跳传播契约只覆盖跨请求头桥接，未声明同上下文并发嵌套语义——属未定义时序下的实现缺陷（非声明边界，也非误报）。
- **误报校准说明**：同步嵌套（顺序完成）与跨线程（各自 IContext）均安全；仅并发嵌套+逆序完成触发，理论成分高于 C3-001~006，按规则降 P2 并明示。
- **修复方向建议**：恢复时校验"当前 registry 值==本链 txn 才回退 old"（比较后置换），或改用作用域栈。
- **置信度**：中。
- **交叉引用**：C3-001（孤儿分支的另一种成因）。
- **复核状态**：保留 P2；新发现；registry 捕获-恢复（TccEngine.java:188/:203-205）与 finished 守卫 no-op（TccTransaction.java:103-108/:125-130）、无条件分支插入（TccRecordStore.java:208-218）核实成立；触发需「同上下文同组并发嵌套 + 外层先完成」的特定形态，维持原判 P2（条件化）。

### [nop-retry-nop-tcc-C3-008]
- **编号**：nop-retry-nop-tcc-C3-008
- **严重度**：P2
- **缺陷族**：幽灵任务 + 对撞放大器
- **标题**：恢复扫描 `.get(expireGap)` 超时后不取消在途补偿链，幽灵链与下一周期新链并发驱动同一事务，且 fetch 已 bump retryTimes/expireTime，多周期持续叠加
- **位置**：nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:328-340（TimeoutException 仅记日志后继续下一条，future 未 cancel）；345-348（checkExpiredAsync 直接 endAsync(true,null,null)）；nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:312-321（fetch 时 bump retryTimes、推进 expireTime 到 nextCheckTime）
- **证据片段**：
  ```java
  // 单条记录的补偿等待加上限，取expireGap作为超时时间：
  // 单个补偿挂死时跳过该记录继续处理下一条，不再永久阻塞整个调度循环
  checkExpiredAsync(record).toCompletableFuture().get(expireGap, TimeUnit.MILLISECONDS);
  } catch (TimeoutException e) {
      LOG.error("TccEngine.checkExpiredTransactions compensation timeout for txnId={}", record.getTxnId(), e);
  ```
- **触发时序**：参与者对 cancel RPC 黑洞（不回包）→ 链 A 在途超过 expireGap → 扫描循环超时跳过 → 下一检查窗口 fetchExpiredRecords 重新拾取（retryTimes<100、expireTime=nextCheckTime 已到）→ 链 B 对同一事务再次 endAsync(true,...) → 与仍在途的链 A 形成稳定双驱动（正是 C3-001/002 的对撞前提）。RPC 真正超时取决于底层客户端配置，模块内无兜底，故每周期叠加一条幽灵链直至 RPC 层超时或 retryTimes 耗尽。
- **影响**：把偶发对撞变成结构性重复对撞；retryTimes 被幽灵期虚耗，100 次上限提前淘汰后中间态永久遗弃（该遗弃本身是 owner doc 已声明的边界，但对撞放大不是）。
- **与已声明契约的对照**：`.get(expireGap)` 的注释声明了"不再永久阻塞调度循环"这一目标且达成；"超时后旧链未取消 + 下周期重驱动"未在声明范围内，属缺陷面（维度第 3 项幽灵任务点名项）。因需 RPC 层长期无超时配合，降 P2。
- **误报校准说明**：非误报，但触发依赖底层 RPC 无超时的部署形态，理论成分高于主路径发现，按规则 3 标注并降级。
- **修复方向建议**：超时分支对 abandoned 链打cancel 标记（或 record 上记 drivingEpoch，endAsync 前校验）；给 endAsync 链挂 orTimeout 使其自终。
- **置信度**：中高。
- **交叉引用**：C3-001、C3-002（后果落点）、C1-001。
- **复核状态**：保留 P2；新发现；.get(expireGap) 超时仅记日志不取消在途链（TccEngine.java:331-334）+ fetch bump retryTimes/expireTime（TccRecordStore.java:313-316）核实成立；依赖底层 RPC 无超时的部署形态，维持 P2 条件化标注。

### [nop-retry-nop-tcc-C3-009]
- **编号**：nop-retry-nop-tcc-C3-009
- **严重度**：P2（理论风险，未具体化到现行实现）
- **缺陷族**：F9（异常/边界致 future 永不完成）
- **标题**：finishCancelAsync 在 (response==null && ex==null) 时于 `response.getBizFatal()` 处 NPE，异常发生在 TccRunner 的 whenComplete 回调内、内层手工 future 已创建但无人完成 → 整条 cancel 链挂死
- **位置**：nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccBranchTransaction.java:153-163（isBizSuccess 判空后 getBizFatal 未再判空）；nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java:107-119（手工 future 仅在内层 whenComplete 中完成，回调同步抛出则无人完成）
- **证据片段**：
  ```java
  if (response != null && response.isBizSuccess()) { return ...CANCEL_SUCCESS/TIMEOUT_SUCCESS...; }
  if (Boolean.TRUE.equals(response.getBizFatal())) {    // response==null 时 NPE
      return ...BIZ_CANCEL_FAILED...;
  }
  // TccRunner.runBranchCancelAsync
  CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
  serviceInvoker.invokeAsync(...).whenComplete((ret, ex) -> {
      branchTxn.finishCancelAsync(timeout, ret, ex).whenComplete(...);  // finish 同步抛 NPE → future 永不完成
  ```
- **触发时序**：仅当某 IRpcServiceInvoker 实现违约以 null 结果正常完成 future（接口 Javadoc 明确"保证不为 null"，IRpcServiceInvoker.java:23-26）→ finishCancelAsync NPE 同步抛出 → `future` 永不完成 → waitAll 挂起 → 恢复扫描靠 `.get(expireGap)` 逃逸（留下幽灵链，见 C3-008），存活调用方路径则调用 future 永久挂起。
- **影响**：挂死面真实存在（手工 future 模式对回调同步抛出不设防），但触发依赖 SPI 违约，按规则 3 标注"理论风险"降 P2。
- **与已声明契约的对照**：接口契约保证非 null——守住契约则不可达；缺陷属防御缺失而非现行缺陷。
- **误报校准说明**：第三方/测试 invoker 违约并非不可想象（同文件 finishTryAsync/finishConfirmAsync 均对 null response 有防御，唯独此分支缺失，属疏漏一致性），保留登记。
- **修复方向建议**：补 `response == null` 分支（对齐 finishConfirmAsync 的处理）；TccRunner 三个 runBranch*Async 的回调体包 try/catch 将异常完成到手工 future。
- **置信度**：高（代码事实确凿）/低（可达性依赖违约）。
- **交叉引用**：C3-008。
- **复核状态**：保留 P2（理论风险，条件化）；新发现；finishCancelAsync.java:158 `response.getBizFatal()` 于 response==null&&ex==null 时 NPE 核实成立，且为三个 finish* 中唯一无判空分支（finishTryAsync:70、finishConfirmAsync:118 均判空，疏漏一致性属实）；手工 future 未兜回调同步异常（TccRunner.java:109-119）成立；触发依赖 IRpcServiceInvoker 实现违约（接口 Javadoc 明确「保证不为 null」，已亲核），维持 P2 理论风险标注。

### [nop-retry-nop-tcc-C3-010]
- **编号**：nop-retry-nop-tcc-C3-010
- **严重度**：P2
- **缺陷族**：F2 + F9（批量补偿无逐分支隔离）
- **标题**：confirmAllAsync 循环内 beginConfirmAsync 对非可 confirm 状态同步抛异常，中断批次中其余分支的补偿驱动；失败聚合并读不到已写终态，可把"从未 confirm 的 TRY_SUCCESS 分支"折叠为全局 CONFIRM_SUCCESS
- **位置**：nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java:133-150（循环仅跳过 null/CONFIRM_SUCCESS，其余直接进入 runBranchConfirmAsync）、79-81 + TccBranchTransaction.java:92-103（beginConfirmAsync 对越界状态 throw 而非 failedStage）、198-217（aggregateConfirmBranchStatus 只识别 CONFIRM_FAILED/cancelled）；对照 TccTransaction.java:132-137（聚合后即 CAS 全局终态）
- **证据片段**：
  ```java
  // TccRunner.confirmAllAsync
  if (status == TccStatus.CONFIRM_SUCCESS) continue;
  futures.add(runBranchConfirmAsync(branchTxn, serviceInvoker));  // TRY_FAILED 等状态→begin 同步抛
  // TccBranchTransaction.beginConfirmAsync
  if (status != TccStatus.TRY_SUCCESS && status != TccStatus.CONFIRMING
          && status != TccStatus.CONFIRM_FAILED && status != TccStatus.CONFIRM_SUCCESS)
      throw new NopException(ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS)...;
  ```
- **触发时序**：confirm 批次中混入一个非 confirmable 分支（典型来源：C3-001 对撞把某分支写成 CANCELLING/TRY_UNKNOWN，或人工修数脏数据）→ 循环推进到该分支时同步抛出 → 排在其后的分支从未进入 confirm 调度 → 异常沿 thenCompose 使 doConfirmAsync 的 future 失败 → whenCompleteAsync 仍执行聚合：未驱动分支内存状态保持 TRY_SUCCESS（非 CONFIRM_FAILED 非 cancelled）→ 聚合返回 CONFIRM_SUCCESS → 全局 CAS 置 CONFIRM_SUCCESS → 未补偿分支被终态掩埋（修复注释自认 null 状态会"中断整个 confirm 批次"，但同型中断面对非 null 越界状态依旧敞开）。
- **影响**：部分分支 confirm 永久缺席而全局报成功——半个成功的隐蔽变体；cancel 方向有 isBranchCancellable 预筛（TccRunner.java:167-173）同型风险仅剩 check 与 begin 之间的 TOCTOU 窄窗。
- **与已声明契约的对照**：owner doc 声明 confirm 状态机单向性，未声明"批次内单分支异常可牺牲其余分支"；缺陷。触发需脏/碰撞状态，降 P2。
- **误报校准说明**：正常单链状态序列不可达（confirm 批次只会含 TRY_SUCCESS/CONFIRMING/CONFIRM_FAILED），依赖 C3-001 对撞或脏数据——已如实降级。
- **修复方向建议**：循环内逐分支 try/catch（异常进该分支 failedStage 汇入 waitAll）；begin*Async 改返回 failedStage；聚合前重载 DB 分支状态。
- **置信度**：中。
- **交叉引用**：C3-001、C3-002（同一对撞族的状态来源）。
- **复核状态**：保留 P2；新发现；confirmAllAsync 仅跳过 null/CONFIRM_SUCCESS（TccRunner.java:140-147）+ beginConfirmAsync 越界同步抛（TccBranchTransaction.java:97-102）+ 聚合双出路（cancelled→throw→终态写跳过卡 CONFIRMING／非 cancelled 越界态→折叠 CONFIRM_SUCCESS）经 round4 扩展核实成立；触发需对撞/脏数据，维持 P2；round4 对本发现遗留「check 与 begin 间 TOCTOU 窄窗」的空窗化关闭经核成立（同一线程对同一内存对象连续读、中间无 await 点）。

### [nop-retry-nop-tcc-C3-011]
- **编号**：nop-retry-nop-tcc-C3-011
- **严重度**：P3
- **缺陷族**：生命周期/取消语义
- **标题**：stopScanning 使用 `cancel(false)` 不中断在途 doScan，而 doScan 受 C3-004 影响可能无限期不返回——engine doStop 立即返回但扫描任务仍占用 worker
- **位置**：nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:104-111（stopScanning）；113-155（doScan 仅在批次边界检查 running）
- **证据片段**：
  ```java
  public synchronized void stopScanning() {
      running = false;
      if (scanFuture != null) {
          scanFuture.cancel(false);   // 不中断在途任务
          scanFuture = null;
      }
  }
  ```
- **触发时序**：停机时恰有一批含挂死 RPC 的记录在 join() 中 → doStop 返回、应用继续关闭，worker 线程与在途 RPC 仍存活，直到进程退出被 daemon 线程回收。
- **影响**：优雅停机语义不完整；单独危害有限（daemon 线程），与 C3-004 叠加时延长关停噪音。
- **与已声明契约的对照**：无契约声明停机等待语义——边界缺陷，优化候选。
- **误报校准说明**：低频路径（停机时刻 + 挂死叠加），按判级规则 P3。
- **修复方向建议**：cancel(true) 或 doStop 等待在途批次完成（带上限）；配合 C3-004 的超时改造自然收敛。
- **置信度**：高。
- **交叉引用**：C3-004。
- **复核状态**：保留 P3；新发现；cancel(false) 不中断在途 doScan（RetryScannerImpl.java:105-111）与 doScan 仅批次边界查 running（:114-155）核实成立，低频停机路径维持 P3。

---

## 维度集外线索

无。本轮 C3 盘点中未发现 C2/C4/C5/C9 形态且达到 P0/P1 的线索（`checkExpiredTransactions` 的逐条 `.get` 阻塞与 `fetchPendingRecords` 循环经评估均在有界/可恢复范围，不足 P1，不登记）。

## 覆盖度与残留边界

**已覆盖**：
- nop-retry 全部异步入口：RetryEngineImpl（executeTask/executeWithRetry/executeImmediateRetry/scheduleImmediateRetry/executeSingleAttempt/doExecute/handleExecution*/triggerCallback/retryFromDeadLetter/doStart/doStop）、RetryScannerImpl（start/stop/doScan）、RetryTaskImpl、RetryRecordStoreImpl 全部写路径与两个查询路径、pause/resume。
- nop-tcc 全部异步入口：TccEngine（runInTransaction* 四个入口、runBranchTransaction*、registry 挂载恢复、checkExpiredTransactions、cleanCompletedTransactions）、TccTransaction（endAsync 路由、doConfirmAsync/doCancelAsync、isRollbackOnly）、TccRunner（try/confirm/cancel 单分支与批量、两个聚合函数）、TccBranchTransaction（begin/finish 六个状态迁移点）、TccRecordStore（两个 CAS 更新、fetchExpiredRecords、save/insert 路径）、TccRpcServiceInterceptor、TccGatewayInterceptor、TccContext/TccTransactionRegistry。
- 上下文传播设施（ContextProvider.thenOnContext/completeAsyncOnContext、FutureHelper.waitAll/whenCompleteAsync/futureCall）读实现核对：tcc 的 registry 恢复与 TccContext 清理经 thenOnContext 传导验证为上下文绑定执行（未列为发现）；retry 的 IContext 不传入异步链（扫描器驱动无业务上下文）符合模块形态。
- owner doc 已声明契约逐条对照：DISCARD/OVERWRITE+UK 兜底（C3-005 指出 OVERWRITE 异步面缺口）、SUSPENDED 语义（代码一致，无发现）、COMPLETED 幂等重放（一致）、retryingTimeoutMs/deadlineTimeoutMs（C3-003 指出租约不续期）、回调开关（一致，fire-and-forget 带日志）、执行表 auth 收敛（非 C3 面）、崩溃恢复对称续跑（路由本身正确，C3-001/002/008 指出对撞面）、多跳传播优先级不对称（TccRpcServiceInterceptor thread-local 赢 / TccGatewayInterceptor 请求头赢，与声明一致，桥接 setCurrent/removeCurrent 配对经 thenOnContext 验证无泄漏）。

**残留边界（未深入，不作为结论）**：
- 平台设施自身（GlobalExecutors 线程池、Nop ORM updateDirectly 对已删行的精确行为——0 行静默或抛异常两种分支均已计入 C3-005 结论，不影响判级）。
- IRpcServiceInvoker 各具体实现（RPC 客户端超时配置）在本单元之外，C3-004/008 的可达性以"客户端无超时"为前提成立，有超时时降为窄窗。
- 测试代码（FakeTccRecordStore 等）与 `_gen/` 生成物按纪律排除。
- nop-job-retry-adapter 等消费方对 retry 引擎的调用形态不在本单元。

---

## 维度复核结论

- 复核人：C3 维度独立复核子代理（与初审代理相互独立，怀疑姿态复核）
- 复核日期：2026-10-16；仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 复核输入：C3-async-chains.md（001~011）+ C3-round2.md（012~015）+ C3-round3.md（016/017）+ C3-round4.md（验证轮）+ live code（nop-retry/nop-tcc main 源码逐文件亲读：TccRecordStore/TccRunner/TccTransaction/TccBranchTransaction/TccEngine/TccStatus/TccTransactionRegistry/TccRpcServiceInterceptor/RetryEngineImpl/RetryScannerImpl/RetryRecordStoreImpl/NopRetryApiConstants/nop-retry.orm.xml）+ 平台设施（FutureHelper/IRpcServiceInvoker/OrmSessionImpl 亲读核对）+ 历史对照面（cra-index.md、G7 2026-09-30 全量审计 G7-14 章节、plans 2461~2465、ai-dev/bugs、owner docs nop-retry.md/nop-tcc.md）。

### 逐条裁定表

| 发现ID | 初审判级 | 裁定 | 现状 | 一句话理由 |
|---|---|---|---|---|
| C3-001 | P1 | **保留 P1** | 新发现 | 三处证据（0 行值完成不短路/begin 后无条件 thenCompose/终态直写）逐行核实成立，双驱动时序每步有代码落点，同值 CAS 必中且虚耗 retryTimes 属实 |
| C3-002 | P1 | **保留 P1** | 新发现 | 聚合读内存+CAS 失败不回写+恢复谓词排除三环成立，坏交错每步可信；一处叙述瑕疵（恢复路径 finish 终态应为 TIMEOUT_SUCCESS）不改级，两者均 finished 折叠结论不变 |
| C3-003 | P1 | **保留 P1** | 新发现 | 租约 600s 与即时重试链全程无续租经代码核实，扫描器拾取条件+版本 CAS 锁确认在途行可被接管，两触发形态均单条件可达 |
| C3-004 | P1 | **保留 P1** | 新发现 | join() 无超时+cancelToken=null+fixedDelay 串行四处核实，下游黑洞是本模块核心场景，停摆的是调度主循环 |
| C3-005 | P1 | **保留 P1** | 新发现 | OVERWRITE 删行不取消旧链+定时器持旧 record+回写落空成立；F3 兄弟站点 grep 穷尽无缺漏（删行点仅 2 处，moveToDeadLetter 已归并失效面） |
| C3-006 | P1 | **保留 P1** | 新发现 | 簿记写内联 whenComplete+外层 ex 即判失败成立，「首写成功/二写瞬时失败」时序每步可信且无守卫，成功→重试方向性后果成立 |
| C3-007 | P2 | **保留 P2** | 新发现 | registry 捕获-恢复与 finished 守卫 no-op 成立；触发需同上下文同组并发嵌套+逆序完成，维持条件化 P2 |
| C3-008 | P2 | **保留 P2** | 新发现 | .get 超时不取消+fetch bump 成立；依赖底层 RPC 无超时部署形态，维持条件化 P2 |
| C3-009 | P2 | **保留 P2** | 新发现 | :158 getBizFatal 未判空为三个 finish* 中唯一疏漏属实，手工 future 未兜同步异常属实；触发依赖 SPI 违约（接口 Javadoc 保证非 null 已亲核），维持理论风险 P2 |
| C3-010 | P2 | **保留 P2** | 新发现 | confirmAllAsync 仅跳过 null/CONFIRM_SUCCESS+begin 同步抛+聚合双出路（throw 卡 CONFIRMING/折叠 SUCCESS）成立；round4 对其遗留 TOCTOU 窄窗的空窗化关闭经核采纳 |
| C3-011 | P3 | **保留 P3** | 新发现 | cancel(false)+doScan 仅批次边界查 running 成立，低频停机路径维持 P3 |
| C3-012 | P1 | **保留 P1** | 新发现 | 首跳异常→零驱动→err 照常聚合→陈旧 from 直写终态，单链可达，FutureHelper err 透传实现亲核吻合 |
| C3-013 | P2 | **保留 P2** | 新发现 | pause/resume 无 CAS+在途链无条件回写+updateDirectly 无版本谓词（亲核）成立；F3 补充：即时重试链 retryCount 写（:345-346/:373-374）为同族无条件直更，修复时一并纳入，不改判级 |
| C3-014 | P3 | **保留 P3** | 新发现 | ScheduledFuture 丢弃+doStop 仅 stopScanning 成立，维持 P3 |
| C3-015 | P3 | **保留 P3** | 新发现 | 裸 invokeAsync 不建 record+去重仅查 record 表+死信键释放成立；grep 确认裸发入口仅此一处，F3 完备 |
| C3-016 | P1 | **保留 P1** | 新发现 | 五环独立复验全部成立（见下），单链可达+双写窗口与 C3-006/012 同级+后果方向最严重 |
| C3-017 | P3 | **保留 P3** | 新发现 | 守卫读内存快照+无条件插入+清单一次加载+级联删证据成立，非组合用法前提维持 P3 |

### P1 子项明细（证据 / 时序 / 判级 / F3 四子项，第 1 轮 P1×6 + 第 2/3 轮 P1×2）

**C3-001**（CAS 写失败不短路）
- 证据：TccRecordStore.java:189-192（全局 0 行 warn+return null 值完成）、:274-278（分支同型）；TccRunner.java:79/:105（begin 完成后无条件 thenCompose 下发 RPC）；TccTransaction.java:110-111/:132-133。全部亲读属实，行号漂移 ±3 行内。
- 时序：双驱动（恢复扫描对撞存活调用方，或同事务二次驱动）→ 败方 begin CAS 0 行但 thenCompose 照发 RPC。每步有代码落点，无假说环节。
- 判级：P1 成立——冲突源具体化（C3-008 幽灵链使其结构化），后果为协调器层无方向互斥的双向重复补偿。
- F3：兄弟站点=updateTccStatusAsync:189-192 + updateTccBranchStatusAsync:274-278 两个 0 行跳过点 + 同值 CAS 计 retryTimes:232-235，均已枚举，无缺漏。

**C3-002**（内存聚合误报）
- 证据：TccRunner.java:178-196/:198-217（聚合只读内存分支状态，失败态白名单外折叠 SUCCESS）；TccRecordStore.java:274-281（0 行/异常均不回写）；:301（status<CONFIRM_SUCCESS(11) 恢复排除）。属实。
- 时序：B begin 晚于 A finish 是关键序——A finish 以 from=CANCELLING 胜出写 CANCEL_FAILED，B begin（from=CANCELLING 内存）0 行、B cancel RPC 成功、B finish 0 行、B 内存残留非失败态、B 折叠 SUCCESS、B 全局终态先达。逐步重建成立；叙述瑕疵一处：恢复路径 timeout=true 时 B finish 目标态应为 TIMEOUT_SUCCESS 而非 CANCEL_SUCCESS，两者均 finished(≥11)，误报结论不变（不改判级，登记备注）。
- 判级：P1 成立——前置冲突与 C3-001 同源，RPC 结果分歧（一败一成）在对撞场景是常规形态而非额外巧合。
- F3：confirm 方向同根机制已由 round2 §2.2 归并本发现失效面，矩阵格 3 处置正确。

**C3-003**（重试不续租双执行）
- 证据：NopRetryApiConstants.java:28（DEFAULT_RETRYING_TIMEOUT_MS=600_000）；RetryRecordStoreImpl.java:116-119（仅首执行推迟一租约）；RetryEngineImpl.java:343-347/:371-375（即时重试续跑仅写 retryCount，无续租）；:522-523（仅 handleExecutionFailure 链尾设 nextTriggerTime）；RetryRecordStoreImpl.java:162-164/:195-204（PENDING/RETRYING+到期即拾取+版本 CAS 锁）。全部属实。
- 时序：(a) 即时重试链长（次数×间隔可配）>10 分钟租约，提交方在途行保持 PENDING 到期被扫；(b) 首 attempt RPC 挂起>租约。均单条件可达；attemptNo=retryCount+1 同值撞 UK_RETRY_ATTEMPT_RECORD_NO（orm:365）连锁属实。
- 判级：P1 成立——续租缺口是结构性的（链内任何位置都无续租调用），触发窗口=租约与链长交叠，为模块目标场景。
- F3：续租缺失面=executeImmediateRetry 两处续跑分支+doExecute 链体，无其他在途持锁面，无缺漏。

**C3-004**（join 无超时停摆）
- 证据：RetryEngineImpl.java:648（allOf(futures).join() 无超时）；:638（executeRetryFromScanner 传 null cancelToken）；:458-463（doExecute 透传，无 orTimeout）；RetryScannerImpl.java:96-101（scheduleWithFixedDelay 下轮须等本轮返回）。属实。
- 时序：批次内任一下游挂起→join 永不返回→doScan 占住 globalWorker→fixedDelay 不再触发。单条件（下游黑洞）可达，该条件即重试引擎存在理由。
- 判级：P1 成立——主调度循环活性丧失，多节点掩盖、单节点整体丧失。
- F3：join 仅此一处（doStart processor），无其他调度线程阻塞点，无缺漏。

**C3-005**（OVERWRITE 幽灵链）
- 证据：RetryEngineImpl.java:255-260（delete→save→execute 新链，旧链无感知）；:407-417（定时器持旧 record 到期照跑）；:463-488（回调对旧 record 回写）；RetryRecordStoreImpl.java:280-283（updateEntityDirectly 直更）。属实。
- 时序：旧链在途+同键重提 OVERWRITE（调用方超时重提正是模块目标场景）→旧 RPC 完成后回写落空/回调仍发/定时器再发业务 RPC→与新链双执行。成立。
- 判级：P1 成立——触发非低频（OVERWRITE 为标准阻塞策略），幽灵任务+双执行后果不可撤回。
- F3：grep 穷尽全模块删行点——OVERWRITE（RetryEngineImpl.java:257）与 moveToDeadLetter（RetryRecordStoreImpl.java:309）共 2 处，后者已由 round2 §2.3 归并本发现失效面（同型回写落空），无缺漏。

**C3-006**（簿记异常覆盖业务结果）
- 证据：RetryEngineImpl.java:487（whenComplete 内联 saveAttempt）；:430-432（外层 ex!=null→handleExecutionFailure）；:341-389（executeImmediateRetry 同型）；RetryRecordStoreImpl.java:141-149（二次保存走 updateEntityDirectly，2026-08-18 bug 修复后的现状）。属实。
- 时序：首写成功→业务 RPC 成功→二写遇瞬时 DB 故障→stage 以簿记异常完成→外层判失败→retryCount+1 重排→业务副作用再执行。每步可信，窗口窄但无任何守卫。
- 判级：P1 成立（campaign 口径「罕见但可具体化+半个成功」，与 C3-012/016 同标准）——「成功被执行为失败」是重试系统最危险方向。
- F3：簿记异常改判面=doExecute 单点（executeSingleAttempt/executeImmediateRetry 两消费方），无缺漏。

**C3-012**（全局首跳异常→零驱动→误报终态）
- 证据：TccTransaction.java:110-116/:132-137；FutureHelper.java:122-129（futureCall 同步异常 reject，亲核）、:354-370（whenCompleteAsync err 透传给 handler，亲核）。属实。
- 时序：首跳 REQUIRES_NEW 写失败→thenCompose 跳过（分支零驱动）→err 照常聚合→fresh-load 分支全非失败态→折叠 SUCCESS→陈旧 from（TRYING/CANCELLING）终态 CAS 于 DB 恢复后命中。单链可达，双写窗口形状与 C3-016 一致。
- 判级：P1 成立，与 C3-006/016 同标准。
- F3：doCancelAsync/doConfirmAsync 两方向对称变体均已枚举。

**C3-016**（分支级 CAS 异常→折叠 SUCCESS——特别复核指令项，独立验证）
- 五环逐环独立重验（不依赖 round3/round4 文本）：
  1. 分支 CAS 失败→内存不回写：TccRecordStore.java:274-281（0 行 warn+return null；:280 setStatus 仅 1 行命中执行）+ futureCall 异常 reject（FutureHelper.java:122-129）——两失败模式内存均保持旧值。成立。
  2. begin 失败→thenCompose 跳过→RPC 零下发（TccRunner.java:105-108）；finish 失败→手工 future 以 ex2 异常完成（:110-118）。begin 同步白名单守卫在 DB 写之前（TccBranchTransaction.java:97-102/:131-135），与 C3-010 同步抛路径不重叠。成立。
  3. waitAll=allOf（FutureHelper.java:388-413）失败透传不短路；doConfirmAsync/doCancelAsync 聚合挂 whenCompleteAsync，err!=null 照常回调（:354-370）。成立。
  4. 聚合白名单：cancel 方向阻断集={BIZ_CANCEL_FAILED(:186), CANCEL_FAILED(:188), TIMEOUT_FAILED(:192)}，TRY_SUCCESS/CANCELLING/BEFORE_TIMEOUT 落入 :195 默认 CANCEL_SUCCESS；confirm 方向阻断={CONFIRM_FAILED(:206)}+cancelled throw(:208-214)，TRY_SUCCESS/CONFIRMING 折叠 :216 CONFIRM_SUCCESS。逐状态核对成立。
  5. 双写窗口：全局首跳成功即回写内存（TccRecordStore.java:194）→终态 CAS from 自洽→DB 恢复后 1 行命中→finished+endTime（:172-176）；恢复谓词 status<11 永久排除 CANCEL_SUCCESS(12)。反向（终态写同败）自愈面亦核成立（恢复每周期新建 TccTransaction，TccEngine.java:345-347）。
- 触发时序四子变体 (a)-(d) 走查全部成立；(c) 的 finishCancelAsync「结果未知按失败记录以便重试」意图（TccBranchTransaction.java:147-148 注释）被其写失败路径击穿的对照属实。
- 判级：P1 确认——协调器主路径（存活调用方与恢复扫描共用 endAsync 链）默认装配即经此代码，触发条件为「分支写失败+毫秒级后全局写成功」的瞬时 DB 故障窗口，单链可达，与 C3-006/C3-012 判级口径一致；不构成 P0（非默认装配+单一常见条件即触发）。round4 §2.1 的五环复核结论与本实测一致，采纳。

### C3-round4 修正与聚合矩阵核对结论

1. **CAS 调用点计数勘误（19→20）**：复核以 grep 全仓库独立复现——saveTccRecordAsync×1（TccTransaction.java:54）+ updateTccStatusAsync×4（:110/:115/:132/:136）+ saveBranchRecordAsync×1（TccBranchTransaction.java:53）+ updateTccBranchStatusAsync×14（:64/:66/:72/:74/:79/:103/:115/:119/:121/:136/:149/:154/:159/:162）= **20 处**（另有 TestTccEngine.java:602/:611 两处测试调用，按纪律排除）。round3 头部「19 处」确为算术笔误，round4 勘误正确，采纳。
2. **矩阵 9 格闭合**：格 1=C3-001、格 2=C3-012、格 3=C3-002、格 4=C3-010+round2 扩展（聚合 throw 被 FutureHelper.java:365-367 catch→终态写跳过，亲核成立）、格 5=C3-016；格 6（部分成功/失败聚合语义干净）、格 8（终态写自身失败自愈）经复核逐状态独立走查确认干净；格 9（null/未知状态折叠）维持「登记不判级」处置。**「矩阵无第 5 个未立案错误终态/悬挂单元格」的闭合结论与本实测一致，维持。**
3. **round4 对前轮的其余修正**：(a) C3-010 遗留 TOCTOU 窄窗空窗化关闭——经核成立（cancelAllAsync:171 预筛与 beginCancelAsync:131 守卫为同线程对同一内存对象的同步连续读，中间无 await 点）；(b) C3-002 时序叙述记录性瑕疵（B 内存值 TRY_SUCCESS 或 CANCELLING 均非失败态，结论不变）——成立，本复核另登记同型瑕疵一处（恢复路径 finish 终态值，见裁定表）。均不改判级。
4. **矩阵结论与本裁定无冲突**；round4 无新增发现（C3-018 起空缺）与复核结果一致。

### 现状标注依据

- **17 条全部标注「新发现」**：(a) WI 1.1–1.5 审计对象为 stream/job/wf/task（cra-index.md 报告清单），plans 2461~2465 grep 零命中 nop-retry/nop-tcc——同族不同模块，零交集；(b) 历史最近对照 G7（2026-09-30 全量审计 07-job-task-batch-retry-tcc.md）对 nop-tcc 补偿面明言「confirmAllAsync/cancelAllAsync 分支聚合细节未逐行复核」「本轮未发现新问题」，其 G7-14-01（幂等键生命周期）已经 plan 2282 修复（现状代码 findRecordByIdempotentId 无状态过滤+resolveInsertRaceWinner 即修复产物，与本轮 17 条无重叠）、G7-14-04（回调目标复用）为独立功能缺陷；(c) plan 2386/2388 硬化（代码注释可见：confirm/cancel 路由守卫、BIZ/TIMEOUT_FAILED 聚合、执行表 auth、多跳传播）修的是相邻问题，本轮 17 条为其残留缺口。无「已知未修复」、无「已修复」项。

### F3 失效面完备性核对汇总

- C3-005：删行点 grep 穷尽 2 处，无缺漏。
- C3-013：补全一处同族无条件直更（executeImmediateRetry retryCount 写），不改判级。
- C3-015：裸发入口 grep 穷尽仅 retryFromDeadLetter 一处，无缺漏。
- C3-017：分支准入守卫单点（TccEngine.java:295，同步版 :309-312 委托同路径），无缺漏。
- 其余发现 F3 节枚举与 grep 实测一致。

### 统计

- 复核发现总数：17（第 1 轮 11 + 第 2 轮 4 + 第 3 轮 2；第 4 轮 0 新增）
- **保留 17 / 降级 0 / 驳回 0**
- 最终判级分布：P1×8（C3-001~006、012、016）、P2×5（C3-007~010、013）、P3×4（C3-011、014、015、017）
- 现状分布：新发现×17；已知未修复×0；已修复×0
- 采纳 round4 修正 2 项（计数 19→20、TOCTOU 空窗化关闭）；登记不改级叙述瑕疵 2 处（C3-002 恢复路径 finish 终态值、round4 已登记的 C3-002 内存值瑕疵）

### 争议点

无阻断性争议。两处记录性备注（均不改判级、不改事实内容）：
1. C3-002 触发时序中恢复链（timeout=true）的分支 finish 终态写的是 TIMEOUT_SUCCESS 而非 CANCEL_SUCCESS——两者均为 finished(≥11)，折叠与恢复排除结论不变。
2. C3-013 的失效面按复核补全即时重试链 retryCount 写两处（同族无条件直更），修复设计时应一并覆盖。

---

## 最终保留项

| # | 发现ID | 标题（短） | 最终严重度 | 现状 |
|---|---|---|---|---|
| 1 | nop-retry-nop-tcc-C3-001 | TCC 全局/分支状态 CAS 写失败（0 行）不短路补偿链，继续下发 confirm/cancel RPC | P1 | 新发现 |
| 2 | nop-retry-nop-tcc-C3-002 | 补偿聚合读内存分支状态，CAS 失败不回写→全局误报 CANCEL_SUCCESS 而分支 CANCEL_FAILED，预留资源永久悬挂 | P1 | 新发现 |
| 3 | nop-retry-nop-tcc-C3-003 | retry 即时重试链跨异步边界不续租 nextTriggerTime，租约到期扫描器并发接管同一任务双执行 | P1 | 新发现 |
| 4 | nop-retry-nop-tcc-C3-004 | 扫描批处理 allOf().join() 无超时阻塞共享 worker，单个挂死 RPC 令本节点重试扫描永久停摆 | P1 | 新发现 |
| 5 | nop-retry-nop-tcc-C3-005 | OVERWRITE 删行不取消在途异步链，旧链 RPC/定时续跑继续产生副作用，幽灵任务与双执行 | P1 | 新发现 |
| 6 | nop-retry-nop-tcc-C3-006 | doExecute whenComplete 内联簿记写库，簿记异常覆盖业务结果→成功被改判失败并重试 | P1 | 新发现 |
| 7 | nop-retry-nop-tcc-C3-007 | TccTransactionRegistry LIFO 恢复假设在并发嵌套逆序完成时错位，上下文残留已完结事务产生孤儿分支 | P2 | 新发现 |
| 8 | nop-retry-nop-tcc-C3-008 | 恢复扫描 .get(expireGap) 超时不取消在途补偿链，幽灵链与下周期新链结构化双驱动 | P2 | 新发现 |
| 9 | nop-retry-nop-tcc-C3-009 | finishCancelAsync 对 (null,null) 输入 getBizFatal NPE 且手工 future 无人完成→整条 cancel 链挂死（理论风险，条件化：依赖 IRpcServiceInvoker 违约） | P2 | 新发现 |
| 10 | nop-retry-nop-tcc-C3-010 | confirmAllAsync 对越界状态分支无逐分支隔离，中断批次补偿驱动并可折叠误报 CONFIRM_SUCCESS | P2 | 新发现 |
| 11 | nop-retry-nop-tcc-C3-011 | stopScanning cancel(false) 不中断在途 doScan，停机语义不完整 | P3 | 新发现 |
| 12 | nop-retry-nop-tcc-C3-012 | 全局终态聚合写不校验首跳中间态 CAS 是否生效，异常完成时零驱动分支折叠 SUCCESS 直写终态（单链可达） | P1 | 新发现 |
| 13 | nop-retry-nop-tcc-C3-013 | pause/resume 无状态 CAS 守卫、在途链无条件回写覆盖（SUSPENDED 被翻回 PENDING / COMPLETED 被翻成 SUSPENDED） | P2 | 新发现 |
| 14 | nop-retry-nop-tcc-C3-014 | doStop 取消面不含已调度即时重试定时器（ScheduledFuture 丢弃），停机后仍发起业务 RPC | P3 | 新发现 |
| 15 | nop-retry-nop-tcc-C3-015 | 死信重放绕过幂等键机制，在途重放对同键新提交不可见，双执行无协调点 | P3 | 新发现 |
| 16 | nop-retry-nop-tcc-C3-016 | 分支级 begin/finish CAS 异常完成时内存不回写，聚合折叠 SUCCESS→全局正常完结+分支补偿未生效（单链一次 DB 瞬时故障） | P1 | 新发现 |
| 17 | nop-retry-nop-tcc-C3-017 | 分支准入守卫读内存+插入无父状态条件+清单一次加载，非组合用法产生永无补偿的孤儿分支 | P3 | 新发现 |

（驳回项：无。ID C3-001~017 连续无空缺，备查即本表。）
