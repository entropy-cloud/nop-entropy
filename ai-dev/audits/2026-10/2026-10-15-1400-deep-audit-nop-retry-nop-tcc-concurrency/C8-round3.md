# C8 背压、流控与过载行为 — nop-retry / nop-tcc 第 3 轮收口（盲审）

- **审计单元**：`nop-retry`（io.nop.retry，40 files / 4,100 行手写）+ `nop-tcc`（io.nop.tcc，42 files / 3,872 行手写）；手写口径不含 `_` 前缀生成物与 target/
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`
- **本轮输入**：
  1. 第 1 轮报告 `C8-backpressure.md`（发现 C8-001~011 + CL-004；P1×1、P2×8、P3×2）
  2. 第 2 轮报告 `C8-round2.md`（发现 C8-012~015 + CL-006；P1×1、P2×1、P3×2 + 停摆面统一表 S1~S16）
  3. live code：nop-retry/、nop-tcc/ 全部 main 源码逐文件审读；平台设施 `FutureHelper`/`ContextProvider`/`BindScheduledExecutor` 仅作行为引用（缺陷归属两模块使用站点）
- **盲审纪律声明**：本轮仅读取了 `ai-dev/audits/2026-10/2026-10-15-1400-deep-audit-nop-retry-nop-tcc-concurrency/` 下的 `C8-backpressure.md` 与 `C8-round2.md`（本维度前序轮次文件，唯一例外）；未读取同目录任何其他维度文件（C1/C3/C6/C7、dispatch-log）、未读取 ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件。owner doc 仅读 `docs-for-ai/03-modules/nop-retry.md` 与 `docs-for-ai/03-modules/nop-tcc.md`（第 1/2 轮已建立的使用契约基线）。
- **本轮目标**（收口轮，逐条执行）：
  - ① 「返回 future ≠ 回调挂接 future」家族全扫：两模块全部返回 `CompletionStage`/`Future` 的方法逐一核对异常传播拓扑（产出全量站点表 F1~F25）
  - ② retry `executeTask` 双后置条件契约裁定（javadoc/owner 语义 vs 实际调用方）
  - ③ 四时系（retryingTimeoutMs / deadlineTimeoutMs / join 无超时 / expireGap）组合矩阵快速收口——仅登记「形同虚设」行

---

## 0. 本轮事前核验（反虚构预注执行 + 设施行为重验）

| 核验项 | 结论 | 证据 |
|---|---|---|
| 两模块内专用超时管理器类 / 独立 future 工具类 | **不存在**（预注成立）。两模块全部 future 操作直接使用 JDK CompletableFuture/CompletionStage 组合子 + 平台 `FutureHelper`/`ContextProvider` | 本轮 grep 全量（§1 站点清单来源） |
| `FutureHelper.futureCall` | 调用线程**同步执行**，`try{task.call()}catch(**Throwable**)→reject`——**永不向调用方同步抛出**，返回的 future 恒可完成（立即完成或异常完成） | FutureHelper.java:122-129 |
| `FutureHelper.whenCompleteAsync` | 手工 promise 拓扑：`future.whenComplete(cb)`，cb 内 `try{handler(...).whenComplete(...)}catch(**Exception**)→promise.completeExceptionally`。**仅 catch Exception**：handler 抛 Error 时异常落在 `future` 的内部依赖 stage（无人持有），**promise 永不完成**；handler 返回的 stage 若永不完成，promise 同样永不完成 | FutureHelper.java:354-370 |
| `ContextProvider.thenOnContext0` | **已带平台级加固**：cb 内 `catch(**Throwable**)→promise.completeExceptionally`，注释明言修复目标正是「promise 将永不完成」形态 | ContextProvider.java:183-200 |
| `ContextProvider.completeAsyncOnContext` | cb 外层 `catch(**Exception**)`：handler 同步抛 Error → 外层 `f` 永不完成（与 whenCompleteAsync 同为 Exception-only 温床） | ContextProvider.java:231-247 |
| `FutureHelper.waitAll` | 空→立即完成；1→`thenRun`；n→`allOf`。输入 future 之一永不完成 → 结果永不完成（传播性） | FutureHelper.java:389-413 |
| `DefaultApiResponseNormalizer.toApiResponse(null)` | 返回 `ApiResponse.success(null)`，**无 NPE**（failure 路径 `toApiResponse(ret)` 的 ret 为 null 时安全） | DefaultApiResponseNormalizer.java:18-27 |
| 默认装配 | tcc 全部装配仅 3 bean：`nopTccServiceMetaLoader`、`nopTccEngine`、`nopRpcServiceInterceptor_tcc`——**`ITccExceptionChecker` 默认无 bean**（`exceptionChecker==null` → `isSafeFailException` 恒 false，不执行宿主代码）；`apiResponseNormalizer` 用默认单例 | tcc-core-defaults.beans.xml、TccEngine.java:55、91-95 |
| `DefaultTccServiceMetaLoader.getMethodMeta` | 纯内存 map + header 读取，无 DB、无现实抛点 | DefaultTccServiceMetaLoader.java:76-91 |
| `TccRecordStore.newTccRecord/newBranchRecord` | `dao.newEntity()+initEntityId()` 为内存操作（无 DB 往返），DB 写全部落在 futureCall 包裹的 save*Async 中 | TccRecordStore.java:82-126 |
| 重验第 2 轮遗留扫描点（whenCompleteAsync 的 Error 逃逸温床） | §1 F16/F22 给出结论：当前两模块 handler 体无可具体化的 Error 注入源 | 本轮 |

---

## 1. 深挖指令① —「返回 future ≠ 回调挂接 future」家族全扫

### 1.1 站点清单来源（穷举声明）

grep `CompletionStage|CompletableFuture|Future<` 于两模块 main 源码 → 15 个文件；grep `whenComplete|thenCompose|thenApply|thenRun|handle(|exceptionally|allOf|waitAll|futureCall|futureApply|thenOnContext|completeAsyncOnContext|syncGet` → 全部链式构造点 47 处，逐条人工归并为 25 个站点（F1~F11=retry，F12~F25=tcc）。**无遗漏声明**：retry 侧 4 个含 future 类型文件的每一处链式调用均已入表；tcc 侧 11 个含 future 类型文件的每一处均已入表（接口 default 同步方法 `syncGet` 类 5 处归并为 F17/F25 附注）。

### 1.2 全量站点核验表

判定口径：**返回对象**=方法最终 return 给调用方的对象；**挂接对象**=内部异常实际落地的对象。核验通过=两者同一，或异常经 thenCompose/thenApply 组合子转发必达返回对象，或中间层有 `catch(Throwable)` 兜底。

| # | 站点 | 返回对象 | 挂接对象 | 是否同一 | 异常注入点与核验结论 |
|---|------|---------|---------|---------|---------------------|
| F1 | `RetryEngineImpl.retryFromDeadLetter` :76-109 | 校验失败路径=手工 completed/failed future；主路径=`rpcServiceInvoker.invokeAsync(...)` 原样返回 | 主路径：invokeAsync 返回对象本身 | **同一** | 核验通过。注入点=`loadDeadLetter`(:77，DB 读)、`JsonTool.parseBeanFromText`(:107，脏 payload) —— 均为**同步抛**（future 尚未产生），直接传播给调用方，可观察；属双后置条件面（→ C8-017），非 never-completing |
| F2 | `RetryTaskImpl.callAsync → executeTask` :168-189 | 分流：`FutureHelper.success`/`failedStage`/`handleExistingRecord`/`executeWithRetry` 结果 | 同左（透传） | 同一（透传型） | 核验通过 + 双后置条件面（→ C8-017）。注入点（同步抛，future 未产生）：`loadPolicyOrDefault`(:169)、`findRecordByIdempotentId`(:171)、`resolveInsertRaceWinner` 重抛(:183)、OVERWRITE 的 `deleteRecord/saveRecord`(:257/259)、`handleDeadlineExceeded→moveToDeadLetter`(:493)、`doExecute` 前置 `newAttempt/saveAttempt`(:454-456)。全部同步传播给调用方（提交方 try-catch 可观察，见 §2）；**唯一非同步形态 = immediate-retry 拓扑 → F6/F7（C8-012 已立案，本轮复核仍 live：result 于 ：336 创建 ：391 返回，写方仅 cb1/cb2）** |
| F3 | `handleExistingRecord/handleBlockStrategy` :203-266 | COMPLETED/DISCARD/default=`FutureHelper.success`（ResolvedPromise 恒完成）；OVERWRITE=透传 executeWithRetry | 同左 | 同一 | 核验通过。OVERWRITE 同步 DB 抛 → 传播给调用方（可观察；原子性面为 C8-006 已立案，不重复） |
| F4 | `executeWithRetry` :295-318 | deadline 超限=`CompletableFuture.failedStage`（恒完成）；否则透传 | 同左 | 同一 | 核验通过。`moveToDeadLetter`(:493) DB 同步抛 → 传播给调用方（可观察） |
| F5 | `executeRetryFromScanner` :268-293 | 坏记录=`failedStage`（恒完成）；正常=透传 executeWithRetry | 同左 | 同一 | 核验通过。坏记录分支 `moveToDeadLetter`(:278/285) 与 `JsonTool.parseMap`(:290) 同步抛 → 冲出 doStart map lambda → doScan 内层 catch(:146-150) → 本批跳过 + error 日志 + 租约到期自愈（第 2 轮 S16 已登记形态，重叠非缺陷） |
| F6 | `executeImmediateRetry` :329-392 | `result`（:336 手工 promise） | cb1 挂接于 `doExecute` 链（:341）；`scheduleImmediateRetry` 链挂接于 :348/:376 | **不同一** | **C8-012 已立案（本轮逐行复核仍 live）**。注入点 E-a=doExecute 前置持久化同步抛（首跳同步传播可观察；延迟跳经 F7 放大为永不完成）；E-b=cb1 体（updateRecord/moveToDeadLetter/triggerCallback 内 executeTask 同步抛）→ 落在无人持有的中间 future → result 永不完成；E-c=延迟任务体同步抛经 ：408 同型放大 |
| F7 | `scheduleImmediateRetry` :394-420 | `future`（:405 手工 promise） | 唯一写方=delay 后 lambda 内 :409 的 whenComplete | **不同一** | **C8-012 已立案**。`executor.schedule` 返回值被丢弃；lambda 内 `executeImmediateRetry` 同步抛 → BindScheduledExecutor 完成**被丢弃的**调度内部 future + 1 条平台日志 → `future` 永不完成 → 上一跳 whenComplete 永不触发 → result 永不完成 |
| F8 | `executeSingleAttempt` :422-447 | `doExecute(...).whenComplete(cb)` 的**链尾** | cb1 体异常落在链尾自身 | **同一** | **核验通过**。cb1 体抛（handleExecutionSuccess/Failure/moveToDeadLetter 的 DB 异常）→ 落在返回对象上 → 扫描路径被 doStart `.whenComplete`(:639-643) 记日志 + join 收到 CompletionException（循环存活）；提交路径以 failed future 交付调用方。对照 F6：同型回调抛出在 immediate-retry 拓扑下才升格为永不完成——**缺陷载体是拓扑而非回调体本身**（与 C8-012 误报排除(a)一致，本轮复核成立） |
| F9 | `doExecute` :449-489 | `invokeAsync(...).whenComplete(cb2)` 链尾 | cb2 体异常落在链尾自身 | **同一** | 核验通过。cb2 体抛（saveAttempt DB 异常）→ 链尾异常完成 → 下游 cb 将其作为 ex 接住（F8 的 cb1 或 F6 的 cb1）。注记（不立案）：簿记失败与业务失败在此合流，均消耗 retry 预算——attempt 记账 DB 故障被当作业务失败重试，属 F2 误归因小面，有日志可查，非背压面 |
| F10 | `triggerCallback` :541-574 | 无返回（fire-and-forget） | 日志 cb 挂接于 executeTask 结果 | — | 核验通过 + 重叠注记。`executeTask`(:563) **无 try 包裹**：其同步抛直接冲入外层 cb（=C8-012 E-b 面，重叠）；若回调策略启用 immediate-retry，回调 future 永不完成 → 仅日志 cb 不触发（与 C8-012 T2 同族，重叠不另立） |
| F11 | `doStart` 扫描 processor :632-652 | 无返回 | 每记录 `.whenComplete`(日志) + `allOf().join()` | — | 核验通过（本站点自身的 whenComplete 可观察）。join 永久阻塞面 = C8-002/C8-012 已立案，不重复 |
| F12 | `TccRecordStore` 6 个 async 方法 ：129-283 | `FutureHelper.futureCall` 结果 | futureCall 内部 catch(Throwable)→reject | **同一（恒完成）** | 核验通过 ×6。DB 异常一律转为异常完成的 future，**tcc 存储层不存在同步抛注入点**；其「调用线程同步执行 DB 事务」的线程占用面 = CL-006（未定级，留 rpc 维度） |
| F13 | `TccEngine.loadTransactionAsync/loadBranchTransactionsAsync` :110-135 | thenApply 链尾 | 组合子转发 | 同一 | 核验通过。lambda（`newTccTransaction`/`newBranchTransactions`）为纯构造，无现实抛点；源 future 由 F12 保证恒完成 |
| F14 | `runInTransactionAsync(txnGroup,txnId,task)` :144-155 | thenCompose 链尾 | 组合子转发 | 同一 | 核验通过。`checkTransactionActive` 同步抛在 **thenCompose lambda 内** → thenCompose 语义保证转发到返回 future → 异常完成（可观察） |
| F15 | `runTaskWithExitingTxnAsync` :184-206 | `thenOnContext(f).whenComplete(恢复registry)` | whenComplete 体异常落在返回对象自身 | 同一 | 核验通过。`task.apply` 同步抛已被 `catch(Throwable)` 接住（恢复 registry 后重抛，修复后代码）→ 传播出方法 → 由调用方 thenCompose 转发；`thenOnContext0` 平台级 `catch(Throwable)`（ContextProvider.java:192-196）兜底 context 关闭形态；whenComplete 体 `registry.put` 无现实抛点，即便抛也落在返回对象上（可观察） |
| F16 | `runTaskWithNewTxnAsync` :236-256 | `thenOnContext(beginAsync).thenCompose(...).whenComplete(registry)` | 组合子转发 | 同一 | 核验通过。`beginAsync`=F12 类 futureCall（无同步抛）；`task.apply` 同步抛被 `catch(Throwable)` 接住并 endAsync 补偿后转 failedFuture（修复后代码，复核成立）；`completeAsyncOnContext` handler 体 = `endAsync(...)`（本身无同步抛，见 F22）+ `toApiResponse`（默认实现无抛点）；`.whenComplete(registry.put)` 无现实抛点。**温床注记（不立案）**：`completeAsyncOnContext` 仅 catch Exception——handler 体未来若加入可抛 Error 的语句，外层 `f` 永不完成；当前无注入源 |
| F17 | `runBranchTransactionAsync` :292-306（+同步包装 ：309-312） | 状态守卫失败=`FutureHelper.reject`（恒完成）；正常=透传 TccRunner | 同左 | 同一 | 核验通过 + 双后置条件注记。`newBranchRecord`(:301) 同步执行，其中 `JsonTool.stringify(apiRequest)`(:122) 对不可序列化 request 可同步抛 → 传播给调用方；两类调用方均可观察：拦截器路径在 task.apply 内（被 F16 的 catch(Throwable) 接住 → endAsync 补偿 → failedFuture），同步包装经 `syncGet` 重抛 |
| F18 | `TccRunner.runBranchTryAsync` :40-70 | `beginTryAsync().thenCompose(...)` 链尾（内含手工 `future` :56） | cb1(:57) 体异常落在 `task.apply(...)` 的 whenComplete 中间 stage（无人持有）；手工 `future` 仅由 cb1/nested cb 完成断 | **不同一（拓扑同型 C8-012）** | **核验通过（默认装配）+ 条件告警行**。cb1 体同步抛候选逐一排查：`normalizer.toApiResponse`（默认单例，instanceOf 判断，无抛点）、`finishTryAsync` 体（:57-58）pre-futureCall 语句=LOG/`StringHelper.isEmpty`/`toResponseError` 构造（无抛点）+ **`tccEngine.isSafeFailException(ex)`(:63)——宿主可插拔回调，默认装配无 bean（恒 false 不执行宿主代码）**。时序（条件化）：宿主注册的 ITccExceptionChecker 在 ex!=null 时抛 RuntimeException → cb1 抛 → nested `.whenComplete`(:58) 从未挂接 → 手工 `future` 永不完成 → thenCompose 链尾永不完成 → runInTransactionAsync 整链悬挂 → 存活调用方（GraphQL/网关请求 future）永久悬挂、无任何日志。默认装配无注入源 → 不立案，登记为「宿主扩展契约须声明不得抛出」的告警行 |
| F19 | `runBranchConfirmAsync` :72-96 | `beginConfirmAsync().thenCompose(...)` 链尾（内含手工 `future` :81） | 同 F18 拓扑 | **不同一（拓扑同型）** | **核验通过（默认装配）**。`beginConfirmAsync` 的状态守卫同步抛(:99)发生在方法体位置 → 传播给 `confirmAllAsync` 循环 → 后者被 `doConfirmAsync` 的 thenCompose lambda 调用（TccTransaction.java:133）→ thenCompose 转发 → `future`(:132) 异常完成 → `whenCompleteAsync` handler 接住（聚合状态 + 落库）→ 整链异常完成（可观察）。`finishConfirmAsync` 体 pre-futureCall 语句仅 LOG（无抛点），futureCall 层 catch(Throwable)。cb1 体无宿主回调（对照 F18 的 isSafeFailException），**默认装配下无 cb-throw 注入源** |
| F20 | `runBranchCancelAsync` :98-122 | 同 F19 拓扑 | 同上 | **不同一（拓扑同型）** | **核验通过（默认装配）**。`beginCancelAsync` 守卫同步抛(:132) → 同 F19 路径转发可观察。filter(:171)与守卫(:131)间状态翻转的竞态：两处读取同一内存对象且同线程连续执行，默认装配下无并发写方（恢复驱动为单线程 pass；跨 pass 在途链不修改本列表）→ 无具体时序，不立案 |
| F21 | `confirmAllAsync/cancelAllAsync` :133-150/:164-176 → `FutureHelper.waitAll` | waitAll 结果 | — | 传播性 | 核验通过。分支 future 均为 F19/F20 产物（默认装配恒完成）；waitAll 的永不完成**仅**经由 F18 型条件注入间接发生。null 状态分支/不可取消分支的跳过守卫（修复后代码）消除了 begin* 同步抛中断整批的旧面 |
| F22 | `TccTransaction.endAsync` :58-95 + `doCancelAsync/doConfirmAsync` :97-138 | `loadBranchTransactionsAsync().thenCompose(...).whenComplete(尾cb)` 链尾；do* 分支=`whenCompleteAsync(future, handler)` | 组合子转发 + whenCompleteAsync catch(Exception) | 同一 | **核验通过**。尾 cb 的 `throw NopException.adapt(ex)`(:93) 为**有意重抛**（防业务异常被补偿结果吞掉）→ 落在 endAsync 返回 stage 上 → 由 F16 的 `complete(f, ret2, err2)` 以 err2 交付 → 可观察；聚合 handler 抛 `NopException`（aggregateConfirmBranchStatus :210）→ Exception 级 → whenCompleteAsync catch → promise 异常完成（可观察）。**温床注记（不立案，第 2 轮遗留扫描点的收口结论）**：handler 体含聚合循环 + futureCall，当前无 Error 注入源；若未来 handler 体加入可抛 Error 代码，promise 永不完成（FutureHelper.java:365 仅 catch Exception）——登记为「修改 handler 体时的强约束」 |
| F23 | `TccRpcServiceInterceptor.interceptAsync` :39-55 | inbound 桥接=`thenOnContext(runWithTccContext(...)).whenComplete(移除TccContext)`；其余=透传 | whenComplete 挂接于 thenOnContext 结果 | 同一（挂接后） | **条件告警行（不立案，理论风险未具体化）**。结构面：`TccContext.setCurrent(fromRequest)`(:49) 发生在 whenComplete 挂接**之前**——若 `runWithTccContext` 同步抛，whenComplete 从未挂接，**thread-local TccContext 泄漏到线程**（下一请求继承陈旧 txn 上下文）。同步抛注入点逐一排查：`getMethodMeta`（纯内存，无抛点）、`runInTransactionAsync`（futureCall + 内存 newTccRecord + registry，默认装配无现实抛点）。默认装配下写不出具体触发时序 → 按规则标注「理论风险，未具体化」，修复方向=setCurrent/removeCurrent 改 try/finally 包裹。此为「清理回调未挂接」变体（家族近邻，非 never-completing 本体） |
| F24 | `TccGatewayInterceptor.invoke` :69-112 | `thenOnContext(runInTransactionAsync(...)).whenComplete(恢复TccContext)` | 同 F23 | 同一（挂接后） | **条件告警行（同 F23）**。`setCurrent(tccContext)`(:77) 先于 whenComplete 挂接(:102)；`runInTransactionAsync` 同步抛（默认装配无现实注入点）→ 恢复逻辑永不执行 → TccContext 泄漏。task lambda 内的同步抛（`proceedInvoke` 等）已被 F16 的 catch(Throwable) 接住 → 链继续 → 清理正常执行（修复后代码复核成立）。同标注「理论风险，未具体化」 |
| F25 | `checkExpiredTransactions` :315-343（驱动侧） | 无返回 | `checkExpiredAsync(...).get(expireGap)` | — | 核验通过（本站点自身有界）。`.get(expireGap)` 超时仅放弃等待，在途链存活 = C8-007/S9 已立案；`catch(InterruptedException)` 正确恢复中断位；Error 穿透 `catch(Exception)`(:338) → pass 中止 + 传播给驱动调用方（pass 语义，第 2 轮 S8 已登记，非缺陷） |

### 1.3 ① 结论

- **确认断裂（永不完成）站点**：全仓**仅 F6/F7 一处拓扑**（= C8-012，已立案，本轮复核 live）。其结构特征为「返回手工 promise + 唯一写方在延迟任务体/回调体内，而写方前置语句存在引擎自身同步 DB 抛点」。
- **同型拓扑但核验通过**：F18/F19/F20 的手工 `future` 拓扑与 C8-012 同型，但 tcc 侧cb 体内的语句全部为 LOG/内存操作/futureCall（catch(Throwable)），**默认装配下不存在 cb-throw 注入源**；唯一现实差异点是 F18 的宿主可插拔 `ITccExceptionChecker`（默认无 bean）。
- **家族近邻（非 never-completing 本体）**：F23/F24 的「setCurrent 先于回调挂接」清理泄漏变体，默认装配无注入源，理论风险。
- **平台温床登记**（使用站点约束，非两模块缺陷）：`FutureHelper.whenCompleteAsync` 与 `ContextProvider.completeAsyncOnContext` 均仅 catch Exception；`FutureHelper.futureCall` 与 `ContextProvider.thenOnContext0` 为 catch Throwable 的正确范本。两模块当前全部 handler/cb 体无可具体化的 Error 注入源。

---

## 2. 深挖指令② — retry `executeTask` 双后置条件契约裁定

**契约文本（现状）**：
- 公共入口为 `IRetryTask.callAsync`（继承 `IRpcCall`，nop-api-core）。`IRpcCall` javadoc：「返回的 ApiResponse 保证不为 null」「如果调用失败，会通过 CompletionStage#toCompletableFuture() 抛出异常」——**失败交付的唯一声明通道是 future**；对同步异常未置一词，对调用方是否必须等待 future 亦未置一词。`executeTask` 本身不在 `IRetryEngine`/`IRetryTask` 任何接口上（package-private），无独立 javadoc。
- owner doc（nop-retry.md）仅对 `retryFromDeadLetter` 声明了「fire-and-forget，不新建 record、不改死信状态」；对 `executeTask`/`callAsync` 的提交方等待义务**零声明**。

**实际调用方（生产代码全量）**：
1. `NopRetryJobRetryBridge.onFireFailed`（nop-job-retry-adapter :49-57）：`callAsync(...).whenComplete(仅日志)`——**fire-and-forget**，外层 `try-catch(Exception)`(:36-60) 兜同步抛。
2. `MessageConsumeFailureBridge.submit`（nop-message-core :102-110）：同型——fire-and-forget + 外层 catch(Exception)(:111-113)。
3. 引擎内部 `triggerCallback`（RetryEngineImpl :563）：`executeTask(...).whenComplete(仅日志)`——fire-and-forget，**无 try 包裹**（同步抛冲入外层 cb，即 C8-012 E-b 面）。

**裁定**：
1. **不要求调用方等待**——fire-and-forget 是事实上的受支持且被使用模式（两个生产桥接方 + 内部回调均如此）。任务不因不等待而丢失：记录在 future 完成前已落库（`saveRecord` :179），扫描器兜底执行（at-least-once 保持）。
2. **双后置条件成立（缺口）**：F2 列出的 5 组同步抛注入点使 `callAsync` 可能「抛异常而非返回失败 future」。只按 IRpcCall 契约消费 future（不 catch 同步异常）的第三方调用方将丢失这批失败；fire-and-forget 调用方若不自行 catch（如 triggerCallback）则失败无观察点。仓库内两个桥接方恰好都有外层 catch(Exception)，**当下无仓库内破绽**。
3. **fire-and-forget × C8-012 提交方路径的后果定性**：immediate-retry 策略下首次提交即进入 F6 拓扑；T2' 形态（业务 future 永不完成）发生时，两个桥接方的 whenComplete 日志回调永不触发——**失败交付三通道（同步抛/future 异常/永不完成）中第三通道对 fire-and-forget 观察者结构性不可见（零日志零指标）**；但任务本体不丢（store 兜底），损失面=提交方结果可见性与等待型调用方的请求线程悬挂。

→ 立案为 **C8-017**（P2，契约缺口，见 §4）。

---

## 3. 深挖指令③ — 四时系组合矩阵（形同虚设行登记）

四时系定义：`deadlineTimeoutMs`（policy，总截止，默认 24h，**≤0 显式关闭**——`deadlineTimeoutMs > 0` 才检查，RetryEngineImpl:303）、`retryingTimeoutMs`（**扫描器级**配置 `nop.retry.scanner.retrying-timeout-ms|600000`，租约窗口；**policy 级同名字段为零读方死配置 → C8-016**）、`allOf(futures).join()`（扫描批等齐，无超时）、`expireGap`（tcc 恢复驱动参数，同时充当拾取间隔基准与单条补偿 `get()` 超时，驱动入口内 `checkInterval≡expireGap` 硬耦合，TccEngine:317）。

| # | 配置组合 | 哪个超时形同虚设 | 后果与触发时序 | 判定 |
|---|---------|----------------|---------------|------|
| M1 | 任意 policy 且 `immediateRetryCount>0`、`deadlineTimeoutMs>0`（含默认 24h） | **deadline 在立即重试链内形同虚设（代码结构性，非配置错误）** | deadline 检查仅存在于 `executeWithRetry` 入口(:302-311)；立即重试链 `scheduleImmediateRetry → executeImmediateRetry`(:408) **不经过 executeWithRetry**，链内各跳均不查 deadline。时序：记录 R 首提时距 deadline 仅剩 1s，但 immediateRetryCount=5、interval=60s → 5 跳全部执行完毕（每跳真实 RPC），deadline 从不拦截；链结束后下一次扫描拾取才恢复检查 | **新登记行**（P3）：链时长上界=immediateRetryCount×interval，自限；修复方向=链内各跳复用 deadline 检查 |
| M2 | `deadlineTimeoutMs=0`（显式关闭）+ `maxRetryCount` 配巨值（如 10⁶） | **deadline 维度整体关闭，退出条件仅剩次数维度** | 永久失败的任务每次扫描拾取都真实执行 RPC（每租约周期一次 + retryCount+1），无时间维度出口 → 下游持续被打桩，次数耗尽前记录永无终态。时序：下游永久 4xx/5xx 故障 + 上述配置 → 数月量级真实 RPC 重放 | **新登记行**（P3，配置组合缺陷）：两个各自已声明的护栏组合后互相拆台；修复方向=deadline=0 时校验 maxRetryCount 上界或告警 |
| M3 | 宿主**绕过 `checkExpiredTransactions` 直连 `ITccRecordStore.fetchExpiredRecords`** 且传 `checkInterval < expireGap` | **认领窗口（expireTime 推进量=checkInterval）塌缩** | 默认驱动入口内 `checkInterval≡expireGap`（TccEngine:317）→ 拾取推进 `expireTime=(now−expireGap)+expireGap=now`，认领保护期=expireGap，成立。宿主自定义驱动传小 checkInterval → 推进量不足 → 下一轮（甚至同轮并发驱动）立刻重新命中 `expireTime < now−expireGap` 过滤器 → 跨驱动并发双补偿（在途链不取消，C8-007 放大） | **新登记行**（P3，条件面：需宿主直连 store，CL-004 裁定捆绑）；修复方向=store 层对 checkInterval<expireGap 做 fail-fast 或下限钳制 |
| M4 | 扫描器 join 被冻结（C8-002/C8-012 任一形态） | **retryingTimeoutMs 整体形同虚设**：租约到期后的重拾动作由同一冻结循环执行 | 已立案面（C8-002 第 8 项、C8-012 T1.5），矩阵收口归档：租约超时只在「扫描循环存活」前提下有意义 | 已立案（交叉引用，不重复） |
| M5 | `expireGap` < 单条补偿实际时长（参与方慢） | `get(expireGap)` 只放弃等待不终结在途链 → 同一事务按轮次重拾再扇出，expireGap 的「间隔」语义对补偿风暴无节流作用 | 已立案面（C8-007/S9），矩阵收口归档 | 已立案（交叉引用，不重复） |

---

## 4. 本轮新增发现

### [nop-retry-nop-tcc-C8-016]
1. **发现ID**: nop-retry-nop-tcc-C8-016
2. **严重度**: P3（与 C8-014 同族：可见护栏参数零执行——误导性死配置，无直接失控面）
3. **缺陷族**: C8-3 批量/速率参数边界（声明了租约参数但零执行——死护栏）/ 信任面错位
4. **维度子项**: C8-1 生产-消费边界（认领租约窗口的配置面）
5. **位置**: nop-retry/nop-retry-dao/src/main/java/io/nop/retry/dao/entity/NopRetryPolicy.java:63-66（`getRetryingTimeoutMsOrDefault`——唯一读方为 api Input/OutputBean DTO 映射）；实际生效租约=nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:37、71-74、137（扫描器级 `nop.retry.scanner.retrying-timeout-ms|600000`）→ RetryRecordStoreImpl.java:184-201（`tryLockRecordsForProcess(records, retryingTimeoutMs)` 直接以入参推进 `nextTriggerTime`）
6. **状态载体与读写方**: `nop_retry_policy.retrying_timeout_ms` 列——写方=策略管理（owner doc 明文列为 NopRetryPolicy 关键字段「执行中锁定超时」，nop-retry.md:48）；读方=**无任何引擎代码**（本轮 grep：`getRetryingTimeoutMsOrDefault|getRetryingTimeoutMs()` 在 main 源码全部命中仅 NopRetryPolicy.java 定义与 NopRetryPolicyInputBean/OutputBean 映射）。有效租约=扫描器全局单值。
7. **证据片段**:
   ```java
   // RetryScannerImpl.java:137 —— 生效租约来自扫描器配置，与 policy 无关
   List<NopRetryRecord> lockedRecords = recordStore.tryLockRecordsForProcess(records, retryingTimeoutMs);
   // NopRetryPolicy.java:63-66 —— policy 级同名字段：唯一定义 + DTO 透传，零引擎读方
   public long getRetryingTimeoutMsOrDefault() {
       Long value = getRetryingTimeoutMs();
       return value != null ? value : DEFAULT_RETRYING_TIMEOUT_MS;
   }
   ```
8. **并发场景或触发时序**: 非时序型缺陷。运维按业务需要为高频 policy A 配置短租约（如 30s，期望失败任务快速回到 PENDING 重拾）、为长任务 policy B 配置长租约（如 30min，防 at-least-once 双执行）→ 两配置**均被静默忽略**，全部记录共用扫描器全局 10min 租约：A 的故障恢复延迟 20 倍于预期，B 的长 attempt（>10min）照旧进入双执行窗口。行为与运维心智模型（owner doc 字段表）直接矛盾。
9. **现状**: owner doc（nop-retry.md:48）将 `retryingTimeoutMs` 列为 **NopRetryPolicy 关键字段**并释义「执行中锁定超时」——声明位置与执行位置错位；无任何文档声明「policy 级该字段不生效」。
10. **风险**: 租约窗口是本模块唯一的 at-least-once 双执行闸门（第 2 轮 §③ 裁定为「按已声明边界固有窗口」——该裁定的前提恰是运维可按 policy 调租约，前提不成立）；调参无效导致的误配无任何日志/告警。
11. **建议修复方向**: 二选一并补 owner doc：(a) 扫描器拾取后按各记录的 policy 解析租约（`record.getPolicyId() → policy.getRetryingTimeoutMsOrDefault()`）传入 tryLockRecordsForProcess（需逐记录推进，替代当前批统一值）；(b) 移除 policy 级字段（ORM/DTO/owner doc 同步）并在扫描器配置文档补「租约为节点级全局值」。
12. **F3 失效面枚举**: 无失控面（全局租约真实生效，无双执行面扩大）；缺陷面=护栏声明与执行的偏差，与 C8-014（tcc 分支级 maxRetryTimes 死配置）同族不同载体，可同批修复。
13. **信心水平**: 高（grep 全量证实零执行性读方；生效路径逐行核验）。
14. **误报排除**: (a) 非第 1/2 轮已报条目——第 2 轮 §③ 参数表将 `retrying-timeout-ms` 归为「扫描器参数，负/小值→租约窗口收缩」，未识别 policy 级同名字段死配置；(b) 非平台设施问题——字段本体在 nop-retry 实体；(c) DTO 映射存在说明该字段在设计上预期被策略消费，非废弃残留的猜测可由 owner doc 字段表佐证。

**复核状态**：保留 P3；新发现；本轮独立 grep 复核 `getRetryingTimeoutMs` 在 nop-retry main 源码全部命中仅实体定义（NopRetryPolicy.java:63-66）、api Input/OutputBean 映射与 `_gen` 实体（零引擎执行性读方）成立；生效租约链（RetryScannerImpl :37/:71-74 @InjectValue :600000 → :137 tryLockRecordsForProcess → RetryRecordStoreImpl :200 nextTriggerTime=now+retryingTimeoutMs）逐行核实成立；owner doc（nop-retry.md:48）确将 retryingTimeoutMs 列为 policy 关键字段——声明与执行错位属实；**同族关系注记（供 Phase 6 合并）**：与 C8-014 同族同型死配置，判级一致（P3/P3），建议合并处置。

### [nop-retry-nop-tcc-C8-017]
1. **发现ID**: nop-retry-nop-tcc-C8-017
2. **严重度**: P2（判级注记：站点全部具体可枚举、时序确定，但仓库内现有调用方均有外层 catch(Exception) 兜底，当下无破绽；缺口咬合面=第三方/未来调用方与 fire-and-forget 观察者，故 P2 而非 P3）
3. **缺陷族**: C8 提交边界契约缺口（失败交付三通道不对称：同步抛 / future 异常 / 永不完成）
4. **维度子项**: C8-1 提交边界（提交方 API 后置条件契约）——第 2 轮遗留裁定的收口
5. **位置**: 契约声明=nop-kernel/nop-api-core/src/main/java/io/nop/api/core/rpc/IRpcCall.java:10-27（「如果调用失败，会通过 CompletionStage#toCompletableFuture() 抛出异常」——唯一声明通道为 future）；实现=nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java（同步抛注入点：:169 loadPolicyOrDefault、:171 findRecordByIdempotentId、:183 resolveInsertRaceWinner 重抛、:257/:259 OVERWRITE delete/save、:493 handleDeadlineExceeded→moveToDeadLetter、:454-456 doExecute 前置持久化）；调用方=NopRetryJobRetryBridge.java:36-60、MessageConsumeFailureBridge.java:82-113（均有外层 catch）、RetryEngineImpl.java:563 triggerCallback（无 try）
6. **状态载体与读写方**: `IRetryTask.callAsync` 的后置条件——写方=RetryEngineImpl.executeTask 的 7 条返回/抛出路径；读方=调用方的失败感知通道（future 消费 vs 同步 catch）。
7. **证据片段**:
   ```java
   // RetryEngineImpl.java:168-171 —— future 尚未产生即可同步抛（DB 读失败）
   CompletionStage<ApiResponse<?>> executeTask(IRetryTask task, ApiRequest<?> request, ICancelToken cancelToken) {
       NopRetryPolicy policy = loadPolicyOrDefault(task.getPolicyId());   // ← DB 读，同步抛
       NopRetryRecord existingRecord = recordStore.findRecordByIdempotentId(...);  // ← DB 读，同步抛
   // IRpcCall.java —— 契约只声明了 future 通道
   // 「如果调用失败，会通过 CompletionStage#toCompletableFuture() 抛出异常」
   ```
8. **并发场景或触发时序**: T1（契约型调用方丢失败）：第三方按 IRpcCall 契约编写 `task.callAsync(req, null).whenComplete((r, e) -> alert(e))`，不加 try-catch → DB 抖动命中 ：171 → 同步 RuntimeException 直接冲出调用方代码帧，alert 回调从未注册也从未触发 → 失败仅以栈顶异常存在，fire-and-forget 语境下无人接住。T2（fire-and-forget 全盲）：同 T1 但调用方有 catch（仓库内现状）→ 失败被记日志，可观察；但若策略启用 immediate-retry 且命中 C8-012 T2' 形态 → future 永不完成 → whenComplete 永不触发、catch 亦无事发生 → **失败对三种通道全部不可见，仅剩 store 侧记录兜底**。
9. **现状**: `executeTask` 不在任何接口上，无 javadoc；`IRpcCall` javadoc 声明 future-only 通道与实现不符；owner doc 对提交方等待义务零声明（仅 retryFromDeadLetter 声明 fire-and-forget）。仓库内两个生产桥接方恰好都有外层 catch(Exception)，当下无仓库内破绽——缺口是契约性的而非行为性的。
10. **风险**: 契约是集成方行为的唯一依据；「部分失败走 future、部分失败走同步抛」使失败监控（按 e 回调告警）对 DB 类故障（恰是重试引擎最该工作的窗口）静默失效；三通道不对称与 C8-012 叠加后，过载窗口的提交失败完全不可观测。
11. **建议修复方向**: (1) 短平快：`RetryTaskImpl.callAsync` 加 try-catch(Throwable) 将同步抛转 `CompletableFuture.failedStage`（消灭双后置条件，与 C8-012 修复同批）；(2) `IRpcCall` javadoc 或 owner doc 明确声明等待非必需、fire-and-forget 受支持、失败保证经 future 交付（修复 1 落地后声明即真）；(3) triggerCallback 的 executeTask 调用补 try-catch（C8-012 E-b 面收窄）。
12. **F3 失效面枚举**: 注入面 ×6（上列行号）；每面触发=引擎依赖（DB/时钟）故障时点，与 C8-012 的 E-a 同源不同表现（E-a 在 F6/F7 拓扑内放大为永不完成，本条是其在普通路径的可观察形态的契约缺口）。
13. **信心水平**: 高（契约文本、注入点、调用方逐行核验）。
14. **误报排除**: (a) 非要求所有方法不得同步抛——Java 惯例允许；立案对象是「契约只声明 future 通道」与实现的不一致；(b) 非 C8-012 重复——C8-012 立案的是永不完成拓扑本身，本条裁定的是提交方契约与三通道不对称（第 2 轮 §7 方向 1 明确遗留本裁定）；(c) retryFromDeadLetter 的 fire-and-forget 声明（owner doc:79）与本条不冲突——本条主张的是把该语义提升为全提交面的显式契约。

**复核状态**：保留 P2；新发现；IRpcCall javadoc（:9-13 实测「如果调用失败，会通过 CompletionStage#toCompletableFuture() 抛出异常」，对同步异常与调用方等待义务零声明）核实成立；6 组同步抛注入点（:169/:171/:183 重抛/:257/:259/:493/:454-456）逐一核实成立；RetryTaskImpl.callAsync（:100-101）直通 executeTask 核实；两个生产桥接方外层 catch(Exception)（NopRetryJobRetryBridge onFireFailed、MessageConsumeFailureBridge.submit）与 triggerCallback 无 try 包裹（:563）均核实成立——「当下无仓库内破绽、缺口是契约性」的判级前提属实，P2 校准恰当。

---

## 5. 维度集外线索

### [维度集外线索-CL-007]（维度集外线索，不立案展开）
1. **发现ID**: nop-retry-nop-tcc-CL-007
2. **形态归属**: 功能装配缺位（同 CL-004 形态）；C8 关联=死信重放是 C8-006/死信链路的运维闭环出口，出口无调用面则链路只进不出
3. **位置**: nop-retry/nop-retry-api/src/main/java/io/nop/retry/api/IRetryEngine.java:19；实现 RetryEngineImpl.java:76-109；全仓库 grep（java/xbiz/xml/vue/js，排除 target）：生产调用方**零命中**，仅 IRetryEngine 接口、实现、两处测试（TestRetryTaskContract、TestNopRetryJobRetryBridge 的 stub）
4. **证据片段**: nop-retry-service 的 xbiz 仅 4 个 crud 模型描述（NopRetryDeadLetter.xbiz 为薄扩展，无 retryFromDeadLetter 方法暴露）；nop-retry-web 无任何引用。
5. **线索描述**: owner doc（nop-retry.md:79）声明的「死信重放 = 手动单次重放」入口在仓库内无 web/xbiz/biz 装配——运维无法经由任何暴露面触达该方法。与 CL-004（tcc 恢复驱动无装配）同属「已声明机制无生产入口」；区别在于本方法是手动运维动作而非自动驱动。
6. **严重度**: 未定级（功能覆盖缺口，非并发缺陷；供功能覆盖维度/协调方裁定）。
7. **处置建议**: 若裁定为缺口，最小修复=NopRetryDeadLetterBizModel 增加 `retryFromDeadLetter` biz 方法（透传 engine）+ xbiz 暴露；owner doc 补装配说明。

**复核状态**：**确认**；本轮独立 grep 复核成立——retryFromDeadLetter 全仓库（java/xbiz/xml，排除 target）命中仅 IRetryEngine.java:19 接口声明、RetryEngineImpl 实现、测试三处（TestRetryTaskContract、TestRetryEngineImpl ×4、TestNopRetryJobRetryBridge stub），NopRetryDeadLetterBizModel 实测为裸 CrudBizModel 派生（无任何 biz 方法暴露）；owner doc（nop-retry.md:79）确声明「死信重放 = 手动单次重放」入口——声明机制无生产入口属实；形态真实、与 C8-006/死信链路发现不重复（彼为链路行为缺陷，本条为运维闭环出口缺位）、有独立行动意义；非并发缺陷，严重度留功能覆盖/协调方裁定。

---

## 6. 覆盖度与残留边界

**本轮已覆盖（收口三指令）**：
1. ① 家族全扫：25 个站点（F1~F25）全量核验，站点清单由双 grep 穷举（future 类型文件 × 链式构造点）并逐文件复核；确认断裂仅 C8-012 一处拓扑（live 复核）；tcc 侧 3 个手工 promise 站点（F18~F20）同型但默认装配无注入源；平台温床（whenCompleteAsync/completeAsyncOnContext 的 Exception-only catch）登记为使用站点强约束；第 2 轮遗留扫描点（S13 的 Error 逃逸温床）收口于 F16/F22 注记。
2. ② 契约裁定：IRpcCall javadoc / owner doc / 3 个生产调用方三方核对，裁定「不要求等待、fire-and-forget 受支持但失败交付三通道不对称」→ C8-017。
3. ③ 四时系矩阵：5 行（M1~M3 新登记形同虚设行，M4/M5 为已立案面归档）；矩阵副产品发现 policy 级 retryingTimeoutMs 死配置 → C8-016。

**残留边界**：
- F18/F23/F24 的条件告警行依赖宿主扩展代码（ITccExceptionChecker / 自定义 ITccRecordStore）行为，仓库内不可闭合裁定；已给出「宿主回调不得抛出」的契约告警与 try/finally 加固方向。
- `invokeAsync` 完成线程模型（CL-006 裁定前提）与 RPC 层超时保证（C8-002 前提）属平台设施，未展开。
- CL-004（tcc 驱动装配缺位）本轮复核未发现装配变化，维持原状留协调方裁定；M3以其裁定为前提。
- JsonTool.stringify/parse 的具体异常条件属平台设施（nop-core），本轮仅按「可抛出」作保守假设。

## 7. 终止信号判定

**终止信号：家族全扫闭合。**

依据：① 的全扫显示——两模块全部 future 返回站点中，确认断裂（返回对象 ≠ 挂接对象且无兜底）**仅 C8-012 一处**（已立案）；其余站点全部「核验通过」（24 站点中的 22 个，含 3 个条件告警行与 2 个平台温床注记行），条件化风险全部依赖宿主扩展代码或 Error 级注入（默认装配无可具体化时序）。本轮新增 C8-016（P3，C8-014 同族死配置）、C8-017（P2，契约裁定落地，非新失效家族）——无新 P0/P1，无第 1/2 轮未覆盖的新停摆/流控家族。第 2 轮 §7 预判的「第 3 轮按方向 1 收口后可终止」成立。**不建议第 4 轮**；遗留开口（CL-004 驱动装配捆绑裁定、CL-006 回调线程模型、F18/F23/F24 宿主契约告警）均为跨维度裁定项，非 C8 单维度深挖对象。

**本轮新增汇总**：C8-016（P3，policy 级 retryingTimeoutMs 死护栏）、C8-017（P2，提交面双后置条件契约缺口）；CL-007（死信重放零生产装配）；矩阵新登记行 M1~M3；核验通过站点 22 个（含条件告警 3 行）。
