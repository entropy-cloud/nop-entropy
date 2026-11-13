# C7 长时运行稳定性 — nop-retry-nop-tcc 第 2 轮深挖（盲审）

- **审计单元**：`nop-retry`（8 jar，40 files/4,100 行手写）+ `nop-tcc`（9 jar，42 files/3,872 行手写），手写口径不含 `_` 前缀生成物与 target/
- **仓库路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（branch `concurrency-audit-remediation`）
- **本轮输入**：
  1. 第 1 轮报告 `C7-long-running.md`（C7-001~012 + CL-002/003 + 单调增长源清单 G1~G6 + 挂死点清单 H1~H6）
  2. grep 基线 `_tmp/cra-baseline-nop-retry-nop-tcc.txt`（含 C7 补充正则节）
  3. live code 精读：`RetryEngineImpl`、`RetryScannerImpl`、`RetryRecordStoreImpl`、`TccEngine`、`TccTransaction`、`TccRunner`、`TccBranchTransaction`、`TccRecordStore`、`TccStatus` 全文；两模块全部 beans.xml；平台设施定向核对：`HttpRpcServiceInvoker`、`ClusterRpcServiceInvoker`、`HttpRpcService`、`HttpClientConfig`、`ApacheHttpClient(+Helper)`、`OkHttpClientImpl/Provider`、`JdkHttpClient`、`OrmEntityIdGenerator`、`QueryBean.addOrderField`、retry/tcc 两份 orm 模型
- **盲审纪律声明**：本轮仅读取了 `ai-dev/audits/2026-10/2026-10-15-1400-deep-audit-nop-retry-nop-tcc-concurrency/` 下的 `C7-long-running.md`（本维度第 1 轮）与 `dispatch-log.md` 的方法论口径节（第 5 行维度集外线索 overlay 口径）；未读取同目录 C1/C3/C6/C8 及其各轮次文件，未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件。
- **本轮目标**（对应派发指令 ①~⑤）：① 日志放大 F3 清扫；② 自愈清理面反向核查；③ fencing 正向结论边界检查；④ metrics/监控基数；⑤ rpc/超时条件结论复核。

## 深挖结论总览

| 指令 | 结论 |
|------|------|
| ① 日志放大 F3 清扫 | 19 个 ERROR/WARN 站点全枚举（见 F3 表）。**确认无界的仅第 1 轮已立案的 C7-003 族**；tcc 恢复链的每轮 ERROR（TccEngine:333/339）受 `maxRetryCount`（默认 100）轮数封顶后转静默（并入 C7-006 监控盲区，不另立案）。新登记 2 个第 1 轮未列的放大机制：终态处理内 DB 写失败的**瞬态僵尸**（自愈型）、failed attempt 的**同事件双 ERROR**（:510+:641） |
| ② 自愈清理面反向核查 | **降级证据不存在，C7-001/005 维持**。全仓 DELETE/TTL/归档路径穷举：retry 仅 2 处删除（OVERWRITE 重建、死信迁移——均为行迁移/重建而非保留期清理）；tcc 仅 `removeCompletedRecords`（零生产调用方）。两模块全部 beans.xml 仅装配 engine/store/scanner/interceptor/meta-loader，无任何调度/清理/生命周期钩子。结论升级为 F3 全枚举：**清理代码存在但零生产装配，生产路径上不存在任何自愈清理入口** |
| ③ fencing 边界检查 | 正向结论部分维持、部分收窄：(a) 版本 CAS 单调性与时钟无关（DB 原子），时钟面由 C7-007/C7-011 覆盖；(b) txnId/sid 均为 `tagSet="seq"` 平台序列生成，与账本表生命周期无关，重建无重号，正向；(c) **发现 check-then-act 窗口 → 新立案 C7-013（P2）**：分支守卫读点为陈旧内存态、CAS 0 行跳过不门控补偿 RPC |
| ④ metrics 基数 | **两模块零指标暴露**（grep `counter|metric|MeterRegistry|.tag(|label|Histogram|Timer` 仅命中 `globalTimer` 误报）。无标签基数问题（根本无标签），并入 C7-002/006 监控盲区评估，不重复立案 |
| ⑤ rpc/超时复核 | **C7-002 触发条件精确化（原表述过强）**：三种内置 HTTP client 均有默认 30s readTimeout，默认装配下单次 attempt 有界；永久挂死需自定义 invoker/显式关闭超时/慢滴响应。同时新登记 retry 侧整轮线性退化面（C7-010 的 retry 对应面）→ 新立案 C7-015（P2） |

**新增发现**：C7-013（P2）、C7-014（P2）、C7-015（P2）。**新增维度集外线索**：CL-005（C5/C9 形态）。无新增 P0/P1。

---

## ① 日志放大 F3 失效面全枚举（指令 ①）

枚举范围：两模块全部 ERROR/WARN 站点（grep 全量 19 处，见 T1 取证）+ 恢复/扫描循环内的 INFO 量级。判定列给出「每周期重复产出」条件。周期入口仅两处（与预注一致）：retry `RetryScannerImpl.doScan`（fixedDelay 5s，租约 10min）→ `RetryEngineImpl.doStart` processor；tcc `TccEngine.checkExpiredTransactions`（外部驱动 while 循环）。

### retry 扫描循环（每 scanIntervalMs=5s 驱动，僵尸记录按租约 10min 重入）

| # | 站点 | 级别 | 每周期重复产出条件 | 封顶性 | 对账 |
|---|------|------|--------------------|--------|------|
| R1 | RetryScannerImpl.java:149 `execute-retry-fail` | ERROR 全栈 | 批内任一记录在 processor 内同步抛出（损坏 JSON 裸抛 :290 / invoker 同步抛）；**每条僵尸记录每租约周期（10min）1 条** | **无界**（僵尸永不终止） | C7-003 已立案；本轮新增其批量中断副作用 → C7-014 |
| R2 | RetryEngineImpl.java:641 `scanner.execute-failed` | ERROR 全栈 | (a) 每次 failed attempt 的 future 异常完成（与 R4 同事件**双写**）；(b) **瞬态僵尸**：`handleExecutionFailure/Success/moveToDeadLetter` 内 `recordStore.updateRecord/saveAttempt` DB 写失败 → 异常从未消费的 whenComplete 逃逸 → 记录不计数不终态，每租约周期重试至 DB 恢复 | (a) 有界（maxRetryCount 封顶/记录）；(b) 有界但持续期=DB 故障期 | 新登记面；R2(b) 为 C7-003 失效面扩展（自愈型僵尸，不另立案） |
| R3 | RetryEngineImpl.java:510 `task-failed` | ERROR 全栈 | 每次计数失败（retryCount+1 路径） | 有界（≤maxRetryCount+1/记录） | 正常 |
| R4 | RetryEngineImpl.java:492/592/597 deadline-exceeded / moved-to-dead-letter | WARN | 每记录一次性 | 有界（1/记录） | 正常 |
| R5 | RetryEngineImpl.java:230 `insert-conflict-without-winner` | WARN | 每次提交撞 UK 且竞胜者已消失（并发 OVERWRITE/死信迁移窗口） | 非周期（提交驱动） | 罕见，正常 |
| R6 | RetryEngineImpl.java:566/570 `callback-failed`/`callback-response-error` | ERROR/WARN | 每次回调记录终态事件 | 常规有界；**C7-008 成环配置下无界** | 与 C7-008 叠加评估 |

**retry 侧同事件双 ERROR 登记**：一次 scanner 驱动的失败 attempt 同时产出 R3（:510，`handleExecutionFailure` 内）与 R2（:641，processor whenComplete）两条全栈 ERROR——同一异常对象打印两遍，量级翻倍。属日志卫生面，随 R1/R2 修复面一并处理，不单独立案。

### tcc 恢复循环（外部驱动 while 轮次；单条补偿 `get(expireGap)` 上限）

| # | 站点 | 级别 | 每周期重复产出条件 | 封顶性 | 对账 |
|---|------|------|--------------------|--------|------|
| T1 | TccEngine.java:333 `compensation timeout` | ERROR 全栈 | 参与方 cancel/confirm **挂死**（不返回不断连）：该记录每轮被重拾（expireTime 被置为 nextCheckTime=now，下一轮即再到期）→ 每轮 1 条 | **有界但量大**：`maxRetryCount`（默认 100）轮后转**静默**（C7-006） | 放大窗口 = 100 × 驱动周期；并入 C7-006 评估 |
| T2 | TccEngine.java:339 `failed for txnId` | ERROR 全栈 | 补偿链以异常完成：本轮实测仅两类——(a) 恢复链内 DB 写失败；(b) 分支守卫同步抛（`beginConfirmAsync`/`beginCancelAsync` 的 ERR_TCC_INVALID_*，仅分支状态与全局路由错配的脏数据触发）。**注意：参与方"持续返回业务失败"不产生任何每轮日志**——失败被聚合进分支状态（TccRunner:178-196），仅 DB 行变化 | 有界：100 轮后静默 | 并入 C7-006；其"业务失败零日志"面加强该案的监控盲区结论 |
| T3 | TccTransaction.java:89 `end-compensate-fail` | ERROR | 条件 `err2 != null && ex != null`——恢复路径 ex==null 恒不触发；仅业务调用方路径一次性 | 非周期 | 正常 |
| T4 | TccTransaction.java:151 + TccRunner.java:141/182/202 `ignore-branch-with-null-status` | WARN ×4 | 分支 status 列脏数据（null）：同一脏分支每轮在 isRollbackOnly/confirmAllAsync/两个 aggregate 各命中 1 次 → **每轮最多 4 条** | 有界：100 轮后随事务静默 | 新登记面；量级低，随 C7-006 修复（耗尽终态化）一并消除 |
| T5 | TccRecordStore.java:190/276 `skip-status-update-due-to-conflict` | WARN ×2 | CAS 0 行真实写冲突 | 与并发写冲突同阶 | 第 1 轮已判有界，维持；与 C7-007 恢复风暴耦合时按风暴规模放大 |
| T6 | INFO 量级：`nop.tcc.cancel`/`confirm`/`resume-cancel`（TccTransaction:98/120/72）+ `branch-begin-*/branch-finish-*`（TccBranchTransaction 各 begin/finish） | INFO | 每轮每卡住事务 3~6 条 INFO | 有界：100 轮/事务 → 单事务年级最多 ~600 条 INFO×N 分支 | 新登记量级面，不入案 |
| T7 | TccRecordStore.java:307-322 `fetchExpiredRecords` 内层 `for(i<100)` CAS 重试循环 | — | 双驱动竞争下每轮最多 100 次全表查询（叠加 C7-005 零索引） | 有界（100 次/轮） | 并入 C7-005 评估 |

**① 结论**：除 C7-003（retry 永久僵尸，P1 已立案）外，所有周期性 ERROR/WARN 放大均有轮数封顶（100 轮）或事件封顶；tcc 侧封顶后的**静默**面全部归入 C7-006。无新的无界放大站点。

---

## ② 自愈清理面反向核查（指令 ②）

**DELETE/清理/TTL/归档路径穷举**（grep `deleteBy|removeBy|deleteEntity|DELETE FROM|cleanup|purge|archive|ttl` 全量命中 4 处）：

| # | 路径 | 位置 | 语义 | 是否保留期清理 |
|---|------|------|------|----------------|
| D1 | `RetryRecordStoreImpl.deleteRecord` → `deleteEntityDirectly` | RetryRecordStoreImpl.java:247-249 | 仅被 `handleBlockStrategy` OVERWRITE 分支调用（RetryEngineImpl.java:257）：重提交时删除旧非终态行重建 | 否（提交时行为，非保留期） |
| D2 | `moveToDeadLetter` 内 `deleteEntityDirectly(record)` | RetryRecordStoreImpl.java:309 | 死信迁移：record 行删除 + deadLetter 行新增（**净增长**：1 行换 1 行 + attempt 孤儿化） | 否（迁移而非清理） |
| D3 | `TccRecordStore.removeCompletedRecords`：branch `deleteByQuery`（:352）+ record `deleteByQuery`（:354） | TccRecordStore.java:332-355 | 唯一的保留期清理实现（`beginTime < now-retention` + finished 状态过滤） | 是，但见下 |
| D4 | （无）`nop_retry_attempt`/`nop_retry_dead_letter` 的任何删除 | 全文件 grep | attempt 与死信表**连 API 都不存在** | — |

**生产装配核查**（beans.xml 全量阅读）：
- retry：`app-engine.beans.xml` 仅装配 `IRetryRecordStore`/`IRetryScanner`/`RetryEngineImpl` 三个 bean；`_engine.beans.xml` 为空。引擎 `doStart` 仅启动 scanner（RetryEngineImpl.java:632-651），无清理逻辑。
- tcc：`tcc-core-defaults.beans.xml`（metaLoader/TccEngine/RpcServiceInterceptor）、`app-dao.beans.xml`（TccRecordStore）、`tcc-gateway.beans.xml`（gateway interceptor）、`app-service.beans.xml`（纯 import）。无任何调度器/启动钩子/定时任务 bean。
- **全仓 grep（main 源码，含两模块之外）**：`checkExpiredTransactions`/`cleanCompletedTransactions`/`removeCompletedRecords` 仅命中接口定义（ITccEngine.java:48/55、ITccRecordStore.java:35）与实现（TccEngine.java:315/351、TccRecordStore.java:334），**零调用方**（retry 桥接模块 `NopRetryJobRetryBridge`/`MessageConsumeFailureBridge` 仅调用 `IRetryEngine.newRetryTask` 提交面，不触及恢复/清理）。

**② 结论（对 C7-001/005 的反向核查裁定）**：未找到任何生产装配的自愈清理入口，**无降级证据**。结论按指令升级为 F3 全枚举陈述：**清理代码存在（tcc `cleanCompletedTransactions` + retry 连 API 均无）但零生产装配；生产路径上 retry 三表与 tcc 两表的唯一"删除"是死信迁移（净增长）与 OVERWRITE 重建；C7-001/005 维持 P1 原判**。

---

## ③ fencing 正向结论边界检查（指令 ③）

### a) 时钟回拨 / DB 主从延迟下的 token 单调性 —— 正向维持

retry 认领 fencing = orm `version` 列 CAS（`tryUpdateManyWithVersionCheck`），tcc 认领/状态 fencing 同为 version CAS（TccRecordStore.java:319）+ `[id+fromStatus]` 单语句 CAS（:188/:274）。三者均为 **DB 端原子比较-交换，单调性与墙钟无关**，主从延迟不影响主库上的 CAS 正确性。受时钟影响的只有**判时值**（nextTriggerTime/expireTime 的写入与比较），该面已由 C7-007（tcc 全链路墙钟）、C7-011（retry 首提租约混用本地钟）覆盖，本轮无新增混用点（复核 `RetryRecordStoreImpl` 全部时间写入：:118 本地钟（已立案）、:136/:200/:314 均为 DB 估计钟）。

### b) token 溢出 / 重置 —— 正向

- txnId（`nop-tcc.orm.xml` TXN_ID `tagSet="seq"`）与 retry 三表 sid（同 tagSet="seq"）均由平台 `ISequenceGenerator` 生成（`OrmEntityIdGenerator.genSeq` → `generateString/generateLong`）：序列状态独立于账本表生命周期，**清空重建事务表不会重发 txnId**；即便序列异常撞号，PK 唯一约束使后果显性化为插入报错而非 fencing 混淆。正向。
- `version` int 溢出：僵尸记录 144 次更新/天 → 2^31 次 ≈ 4 万年，不可具体化，不立案。
- `retryTimes/retryCount` int：受 maxRetryCount（100/默认 3）封顶，无溢出面（第 1 轮结论维持）。

### c) 恢复链与业务链并发的 fencing 读点 —— **发现 check-then-act 窗口，立案 C7-013**

全局状态列在全部实测交错下均受 CAS 保护（正面对账：`doCancelAsync`/`doConfirmAsync` 的 stale in-memory 守卫仅是快路径，真实防护是 `updateTccStatusAsync` 的 `[txnId+fromStatus]` CAS——本轮逐交错推演确认全局 status 不会被双写污染）。但发现两处读点/门控缺口：**(1)** 分支创建守卫读的是业务调用方持有的**陈旧内存态**（TccEngine.java:295，整段业务调用期间永不刷新）；**(2)** `updateTccStatusAsync` CAS 0 行跳过**不门控**后续补偿 RPC（`thenCompose(cancelAllAsync/confirmAllAsync)` 无条件执行，TccTransaction.java:110-111/132-133）。→ C7-013。

---

## ④ metrics/监控基数核查（指令 ④）

grep `counter|metric|MeterRegistry|.tag(|label|Histogram|Timer`（两模块 main 源码，排除 `_gen`/test/target）命中 0 个真实指标站点（唯一命中为 `GlobalExecutors.globalTimer()` 的 `Timer\b` 误报）。

**结论：两模块零指标暴露**——无计数器、无直方图、无健康指标。长时运行下可观测性完全依赖日志（见 ① 的 F3 表）与 DB 行巡检。无标签基数风险（根本不存在标签体系）。按指令与 C7-002（停摆无积压指标）、C7-006（耗尽静默）**合并评估，不重复立案**；合并后的监控盲区清单：积压深度、上次扫描完成时间、僵尸记录数、补偿耗尽数、恢复轮耗时——五项均无指标，修复方向已分别记入 C7-002/006/015。

---

## ⑤ rpc/超时条件结论复核（指令 ⑤）→ C7-002 触发条件精确化

**调用链实测**（retry 与 tcc 的 `IRpcServiceInvoker` 注入面）：

1. 引擎侧（retry）：`RetryEngineImpl` 对 in-flight future **无任何引擎侧超时**——`deadlineTimeoutMs` 仅在派发**前**检查（:302-311，`elapsed > deadline` 才死信），不施加于已派发的调用；scanner 路径 `executeRetryFromScanner(record, null)` 的 cancelToken 为 null（:638），无取消传播；`CompletableFuture.allOf(futures).join()`（:648）无超时。
2. 引擎侧（tcc）：恢复链单条补偿有 `get(expireGap)` 上限（TccEngine.java:331）；业务链 `endAsync` 无包裹（调用方请求超时兜底）。
3. **传输层实测（第 1 轮残留边界的闭合）**：仓内 `IRpcServiceInvoker` 实现仅 `HttpRpcServiceInvoker`/`ClusterRpcServiceInvoker` 两个，均最终委托 `HttpRpcService` → `IHttpClient`。`HttpRpcService.toHttpRequest` **不设置 per-request timeout**（`HttpRequest.timeout` 默认 0）；但三种内置 client 均应用 `HttpClientConfig` 默认值——**connectTimeout=10s、readTimeout=30s、writeTimeout=30s**（HttpClientConfig.java:35-37）：
   - Apache：`defaultRequestConfig` 含 `setResponseTimeout(readTimeout=30s)`（ApacheHttpClientHelper.java:96-98），per-request 仅在 `request.getTimeout()>0` 时覆盖（ApacheHttpClient.java:191-196）；
   - OkHttp：`builder.readTimeout(config.getReadTimeout())`（OkHttpClientProvider.newBuilder）；
   - JDK：request timeout=0 时显式回退 `config.getReadTimeout()`（JdkHttpClient.java:218-221）。

**C7-002 触发条件修订**：默认 HTTP 装配下，单次 attempt 被 30s readTimeout 封顶 → future 必然完成 → `join()` 有限阻塞。**「单次 RPC 挂死即永久停摆」的前提收窄为以下三种 invoker 配置**：
- (a) 部署注入**无内部超时的自定义 `IRpcServiceInvoker`**（引擎按类型注入，模块不强制任何超时纪律）；
- (b) 部署显式将传输层超时置 0/禁用（如 OkHttp `readTimeout(0)` = 无超时；Apache responseTimeout 置 0）；
- (c) **慢滴响应**：OkHttp readTimeout 为单次 read 操作间超时，服务端以 <30s 间隔持续输出字节可无限拖延总时长（非配置错误，运行时可触发但属退化服务端行为）。

机制本体（join 无超时、fixedDelay 同任务不重入、无心跳可见性、`stopScanning` 对阻塞线程无效）维持不变；判级从 P1（任意下游挂死即触发）修订为**条件化 P2**（触发需上述三种配置/行为之一），并登记其默认装配下的残留退化面 → **新立案 C7-015**（整轮排水线性放大，C7-010 的 retry 对应面）。

---

## 发现条目（本轮新增）

### [nop-retry-nop-tcc-C7-013] tcc 分支创建守卫读陈旧内存态 + CAS 跳过不门控补偿 RPC：终态父事务下可孵化孤儿分支，调用方死亡后预留资源永久悬挂（F2/F5，check-then-act）
① **发现ID**：nop-retry-nop-tcc-C7-013
② **维度子项**：C7 边界核查 ③c（恢复链与业务链并发时 fencing 校验读点是否在 CAS 之前）
③ **严重度与判级理由**：P2——三重条件合取（Try 阶段慢于 60s defaultTxnTimeout + expireGap；父事务被恢复链终结后业务仍创建新分支；业务进程在其补偿 endAsync 前死亡），但后果链完全具体化（孤儿分支 + 参与方预留资源永久悬挂、恢复不可发现、无日志无指标），按「条件化竞态降 P2」判级；不判 P1 因生产触发需业务 Try 超过 60s+ 窗口
④ **位置**：守卫读点 `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:295`（`txn.getTccStatus() != TccStatus.TRYING` 读内存态，业务调用期间永不刷新）；分支立即落库 `TccRecordStore.java:206-218`（`saveBranchRecordAsync` REQUIRES_NEW）；CAS 不门控 `TccTransaction.java:110-111`（doCancelAsync）/`:132-133`（doConfirmAsync）——`updateTccStatusAsync(...).thenCompose(cancelAllAsync/confirmAllAsync)` 无条件执行；CAS 0 行跳过点 `TccRecordStore.java:188-193`
⑤ **证据片段**：
```java
// TccEngine.java:294-299 —— 守卫读取业务侧持有的 TccTransaction 内存态，非 DB 现值
public <T> CompletionStage<T> runBranchTransactionAsync(ITccTransaction txn, ...) {
    // 如果事务已经结束，则不能执行分支
    if (txn.getTccStatus() != TccStatus.TRYING)   // ← 内存态，恢复链 CAS 写的是另一个对象
        return FutureHelper.reject(...);
    ITccBranchRecord branchRecord = getTccRecordRepository().newBranchRecord(txn.getTccRecord(), branchRequest);
```
```java
// TccTransaction.java:110-111 —— CAS 结果不门控补偿 RPC：0 行跳过仍执行 cancelAllAsync
CompletionStage<Void> future = getRepository().updateTccStatusAsync(tccRecord, TccStatus.CANCELLING, null)
        .thenCompose(r -> TccRunner.cancelAllAsync(branchTxns, timeout, tccEngine.getServiceInvoker()));
```
⑥ **机制**：业务调用方在 `runTaskWithNewTxnAsync` 期间持有自己的 `TccTransaction`（内存 status=TRYING）；恢复链对同一 txnId 认领/取消时写的是**自己加载的实体对象**，业务侧内存态永不感知。于是恢复链将父事务推入终态（CANCEL_SUCCESS）后，业务侧仍可通过 :295 守卫并经 `beginTryAsync`（REQUIRES_NEW **立即提交**）在已终态父事务下落库新分支行。此后分叉：(a) 业务存活 → 其 endAsync 的全局 CAS 0 行跳过（WARN）但 `thenCompose` 仍发 cancel——分支自愈、参与方收到额外 cancel RPC（幂等负担转嫁参与方）；(b) 业务进程死亡 → 分支行永久滞留 TRYING/TRY_SUCCESS，恢复发现是**父记录驱动**的（`fetchExpiredRecords` 过滤 `status < CONFIRM_SUCCESS`，父已终态不再入选），分支永不可恢复
⑦ **触发时序 T1/T2**：T0=业务调用 `runInTransactionAsync` 创建事务 X（TRYING，expireTime=begin+60s），分支 B1 的 Try RPC 慢（>60s+expireGap 才返回）；T1=X 进入恢复窗：版本 CAS 认领（retryTimes=1）→ endAsync(true) → B1(TRYING) 是 rollbackOnly（TccStatus.java:93-95：`isInTransaction() && !isAllowConfirm()`）→ doCancelAsync → CAS TRYING→CANCELLING 成功 → cancelAllAsync([B1])（B1→BEFORE_TIMEOUT→cancel RPC）→ 聚合 CAS → **CANCEL_SUCCESS（终态）**；T2=B1 的 Try 终于返回，业务代码继续为同事务创建分支 B2：:295 守卫读内存态 TRYING → 通过 → B2(TRYING) 立即提交、参与方预留资源；T3a（业务存活）=业务 endAsync → 全局 CAS from TRYING 0 行跳过 → 但 cancelAllAsync 仍执行 → B2 被 cancel（自愈 + 重复 RPC 面）；T3b（业务死亡）=**B2 永久孤儿**：参与方预留资源悬挂，无日志（无任何代码路径触及该分支）、无指标（④ 零指标）、`removeCompletedRecords` 即便接线也仅物理删行不补偿
⑧ **时间尺度**：窗口 = [恢复轮认领父事务, 业务 endAsync] 之间的任意交错点，单次事务级；后果在 T3b 分支下永久化
⑨ **触发前提**：业务 Try 阶段时长 > `nop.tcc.default-txn-timeout-ms`(60s) + expireGap（慢参与方/长 Try）且多分支事务在父被恢复终结后继续开分支
⑩ **运行影响**：T3b 下参与方（资金/库存类）预留资源静默泄漏，运维只能靠全表比对 `nop_tcc_branch_record.txnId ∈ 父终态集合 AND 分支非终态` 发现（无现成视图）；T3a 下补偿 RPC 重复投递，将 TCC 参与方幂等契约的执行压力从协调者转移到参与方
⑪ **缺陷族**：F2（半个成功）+ F5（监控盲区）；check-then-act（读点不在 CAS 之前的守卫）
⑫ **对账**：第 1 轮覆盖度节「tcc 扫描认领有 fencing：版本 CAS + [id+fromStatus] CAS，双驱动实例不会双写终态」——本条**不推翻**该结论（全局 status 列确受保护），登记的是其边界外的新面：守卫读点与 RPC 门控；与已知交叉「在途补偿超时丢弃」不同面（本条是分支孵化与补偿门控，非补偿超时）
⑬ **修复方向**：`runBranchTransactionAsync` 开新分支前从 store 重读父事务状态（DB 现值守卫，替代内存态判定）；`doCancelAsync/doConfirmAsync` 将补偿 RPC 门控在全局 CAS 结果上（0 行跳过则跳过补偿并改走重读路由）；对「父终态 + 分支非终态」增加巡检查询或 WARN
⑭ **信心水平**：高（守卫读点、REQUIRES_NEW 立即落库、CAS 不门控三处均直接可读；时序已具体化；T3b 的参与方行为按 TCC 参与方标准契约推演）
⑮ **误报排除**：不是「恢复取消进行中事务」本身（那是 TCC 设计语义，参与方须容忍 cancel-before-try）；不是全局状态双写（CAS 已保护）；T3a 自愈路径证明该窗口不是必现损坏，故降 P2
⑯ **复核状态**：保留 P2；新发现；四处读点/门控缺口逐一核实——守卫读内存态（业务侧 `TccTransaction` 持有自身 `tccRecord` 实体，恢复链 CAS 写的是另一加载实体且 `updateTccStatusAsync` 仅回写自身对象 :194，业务侧内存态永不刷新）、`saveBranchRecordAsync` REQUIRES_NEW 立即落库（:206-218）、doCancelAsync :110-111 / doConfirmAsync :132-133 CAS 后 `thenCompose` 无条件执行补偿 RPC、分支表无独立扫描入口（`fetchExpiredRecords` 仅驱动父记录，孤儿分支永不可达）；TRYING.isRollbackOnly=true（TccStatus :89-95）证实 T1 取消路由；三条件合取降 P2 恰当，T3b 孤儿分支后果链具体。

### [nop-retry-nop-tcc-C7-014] scanner processor 批内单点同步异常中断整批流式执行：排序在僵尸记录之后的同页记录被反复跳过（确定性饿死）（F5 邻接，批量原子性缺陷）
① **发现ID**：nop-retry-nop-tcc-C7-014
② **维度子项**：C7-1 日志放大 F3 清扫的衍生失效面 + C7-4 自愈语义（扫描公平性）
③ **严重度与判级理由**：P2——入口依赖 C7-003 的僵尸记录存在（低频入口），但一旦存在，对同页排序靠后的记录的饥饿是**确定性的、每租约周期重复的**；饿死的记录仍保留重试承诺（后续周期 sid 排序不变时永久顺延），故不判 P1
④ **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:637-645`（stream.map 惰性求值，`toArray` 触发逐条执行）；`RetryScannerImpl.java:146-150`（catch 作用于整批）；排序键 `RetryRecordStoreImpl.java:176-177`（`addOrderField(nextTriggerTime, false)` + `addOrderField(sid, false)`，经 QueryBean.java:435 确认 boolean=desc，即均升序）
⑤ **证据片段**：
```java
// RetryEngineImpl.java:637-645 —— 惰性流：map 内同步执行 executeRetryFromScanner，
// 第 k 条同步抛出即中断 toArray，第 k+1..N 条的 future 根本不会被创建
CompletableFuture<?>[] futures = records.stream()
        .map(record -> executeRetryFromScanner(record, null)...)   // :290 parseMap 裸抛
        .toArray(CompletableFuture[]::new);
CompletableFuture.allOf(futures).join();
```
```java
// RetryRecordStoreImpl.java:176-177 —— 同批记录 nextTriggerTime 相同（tryLock 用同一 now），
// 页内次序仅由 sid 升序决定
query.addOrderField(PROP_NAME_nextTriggerTime, false);   // asc
query.addOrderField(PROP_NAME_sid, false);               // asc
```
⑥ **机制**：`tryLockRecordsForProcess` 对同批全部记录写同一 `now + retryingTimeoutMs`（RetryRecordStoreImpl.java:190-200）→ 下个周期重拾时同批记录的 `nextTriggerTime` 相等，fetch 页内次序退化为 **sid 升序**（sid 为 seq 序列单调递增）。僵尸记录 Z（C7-003 入口：损坏 JSON 在 :290 裸抛）若 sid 小于同页某些记录 M，则 processor 流在 Z 处同步中断 → **排序在 Z 之后的全部 M 本周期零执行**（但已被置 RETRYING、租约 +10min）→ 每 10 分钟重复同一循环。M 的重试、死信前的最后一次重试被无限顺延；因 sid 排序固定，这是**确定性饿死**而非随机抖动
⑦ **触发时序 T1/T2**：T1=僵尸 Z（sid=100）与记录 M（sid=150，正常任务，第 1 次重试到期）同页被拾取；T2=processor 流先执行 Z → :290 裸抛 → M 未执行但已被锁定 RETRYING（租约至 +10min）；T3=每 10 分钟重复 T1-T2 → M 的重试 SLA 从退避计划的秒/分钟级退化为「永不（直到 Z 消失）」。对照：sid < 100 的同页记录正常推进
⑧ **时间尺度**：僵尸存续期全长（C7-003 判定为永续）；受影响面 = 每个与 Z 同页且 sid 更大的到期记录（批量默认 100）
⑨ **触发前提**：存在一条 C7-003 型僵尸记录（损坏 payload / invoker 同步抛）且其 sid 排序非页尾
⑩ **运行影响**：重试承诺静默违约（与 C7-012 同「半个成功」形态但机制不同：这是执行器缺陷而非覆盖面缺口）；死信兜底被顺延 → 故障业务方以为「最多 3 次重试+死信」实际无限等待；与 C7-003 的日志放大叠加（每周期 1 条 ERROR）
⑪ **缺陷族**：F5（覆盖/公平性盲区）+ F9 邻接（同步异常的批次级传染）
⑫ **对账**：C7-003 已立案僵尸本体与日志放大——本条登记其**第三后果面**（批内传染/饿死），修复面不同（C7-003 修复反序列化兜底即可消除本条主触发，但批次隔离是独立的纵深防御）；与 C7-002 停摆不同（本条不停摆，循环继续，只是跳过尾部记录）
⑬ **修复方向**：processor 内改为逐条 try/catch（per-record 隔离，单条异常不中断流）；或将 `executeRetryFromScanner` 的反序列化/守卫失败统一改为返回 `failedStage`（与 :275-287 空字段分支一致）而非裸抛
⑭ **信心水平**：高（流惰性求值、同批 nextTriggerTime 同值、sid seq 递增三要素均直接可读/可推演）
⑮ **误报排除**：同批记录并非全部饿死——sid 排序在 Z 之前的记录每周期正常执行并推进终态（已在机制中说明）；`records.isEmpty()`/`lockedRecords.isEmpty()` 的 continue 分支不产生本问题（本条仅针对锁定成功的批次）
⑯ **复核状态**：保留 P2；新发现；确定性饿死三要素核实成立——stream.map 惰性求值 + toArray 中断（Java 流标准语义，:637-645）、同批 nextTriggerTime 同值（tryLock 对整批写同一 `now + retryingTimeoutMs`，:190/:200）、fetch 排序 `nextTriggerTime asc + sid asc`（:176-177，`QueryBean.addOrderField(name, desc)` :435 证实 false=asc）且 sid 为 seq 单调序列；入口依赖 C7-003 僵尸（低频）但饿死一旦存在即确定性重复，P2 恰当；修复面（批次隔离/统一 failedStage）独立于 C7-003 反序列化兜底，纵深防御定位合理。

### [nop-retry-nop-tcc-C7-015] retry 扫描整轮「排水式」串行执行：单轮时长线性于积压深度（下游批量超时场景），扫描心跳无上限退化；C7-002 触发条件修订的载体（F5 邻接，C7-010 的 retry 对应面）
① **发现ID**：nop-retry-nop-tcc-C7-015
② **维度子项**：C7-2 挂死可检测性（看门狗节律退化，retry 侧）+ 指令 ⑤ 结论修订
③ **严重度与判级理由**：P2——默认装配下单次 attempt 有 30s 传输层上界（⑤ 实测），停摆不永久，但 doScan 的 do-while 排水循环使**单轮时长 = 批次数 × 最慢批耗时**，下游批量 30s 超时时一次 doScan 可达小时级；期间积压继续增长、无心跳指标（④ 零指标），租约语义被间接破坏（见 ⑥）
④ **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:119-154`（do-while `fetch 直到空`）；`RetryEngineImpl.java:637-648`（批内 join 等待最慢 attempt）；传输上界证据见 ⑤ 节（HttpClientConfig.java:35-37、ApacheHttpClientHelper.java:96-98、JdkHttpClient.java:218-221）
⑤ **证据片段**：
```java
// RetryScannerImpl.java:120-134 —— 排水循环：一次 doScan 内反复 fetch 直到取空，
// 期间 scheduleWithFixedDelay 的下一轮永不开始（fixedDelay 语义）
do {
    ...
    List<NopRetryRecord> records = recordStore.fetchPendingRecords(batchSize, partitions);
    if (records.isEmpty()) break;
    List<NopRetryRecord> lockedRecords = recordStore.tryLockRecordsForProcess(records, retryingTimeoutMs);
    ...
    processor.accept(lockedRecords);   // → RetryEngineImpl:648 join 最慢 attempt
} while (running);
```
⑥ **机制**：两层放大叠加。(a) **批内**：每批 join 至最慢 attempt，默认装配下界 ≈ 30s（传输 readTimeout）；批内 100 条并行发起但 join 串行等待整批。(b) **轮内**：doScan 排水整个积压——下游整体故障（全部返回超时）时，每条记录要经历「30s 失败 → 退避 → 到期 → 再 30s」循环，积压 N 条时单轮 ≥ ⌈N/100⌉ × 30s（N=2000 → ≥10 分钟；N=12000 → ≥1 小时）。**租约语义破坏点**：`retryingTimeoutMs`（10min）是「扫描器接管后须在该窗口内完成」的假设，单轮超 10 分钟时后续批次记录从锁定到实际执行早已跨过其他实例的认领窗口（双实例部署下叠加已知交叉「重试链不续租」→ 重复执行面扩大）
⑦ **触发时序 T1/T2**：T1=下游服务全面劣化（每请求 30s 超时返回），积压 5,000 条；T2=doScan 单轮 ≥ 50 批 × 30s ≈ 25 分钟，期间 fixedDelay 不重入（单轮在跑）、无任何「扫描进行中/积压深度」指标；T3=运维视角：重试系统「看似存活」（线程在跑、无 ERROR 风暴——超时失败被计数入退避）但每条记录的实际重试间隔从退避计划的分钟级退化为 25 分钟+，且双实例下租约过期重拾造成同记录双执行窗口
⑧ **时间尺度**：线性于积压深度（⌈N/100⌉ × 30s）；下游恢复后随退避收敛
⑨ **触发前提**：下游批量慢/超时（默认装配）或 ⑤ 所列三种无界配置（此时退化为 C7-002 原判的永久停摆）
⑩ **运行影响**：恢复 RTO 与重试 SLA 在故障场景退化 1-2 个数量级；与 ④ 零指标叠加完全不可见；`stopScanning` 优雅停机在排水轮期间延迟至多一轮时长
⑪ **缺陷族**：F5（节律退化）；C7-010 同族（tcc 恢复轮 100×expireGap 放大）的 retry 对应面，机制不同（排水循环 vs 逐条补偿超时）
⑫ **对账**：**C7-002 触发条件修订**：原判「单次 RPC 挂死即整体永久停摆（P1）」的前提「invoker 调用可无限挂死」在默认装配下不成立（三种内置 client 均 30s readTimeout 封顶）——触发条件收窄为自定义无超时 invoker / 显式禁用超时 / 慢滴响应三种配置（见 ⑤ 节），停摆面降为条件化 P2 并入本条统一评估；原 C7-002 的机制链（join 无超时、无心跳、fixedDelay 不重入、stopScanning 无效）作为本条的证据面维持。Phase 4 裁定时建议将 C7-002 与本条合并处置
⑬ **修复方向**：doScan 限定单轮最大批次数/最大时长（超出即返回，余量交下一轮 fixedDelay）；批内 join 加 `retryingTimeoutMs` 上限（与租约一致）；补「上次扫描完成时间/积压深度」指标（与 C7-002 修复方向合并）
⑭ **信心水平**：高（排水循环与 join 结构直接可读；30s 上界为三种内置 client 实测；积压×批次的算术平凡）
⑮ **误报排除**：不是 C7-002 的重复立案（本条是默认装配下仍有界的退化面，C7-002 的永久停摆面已收窄为配置条件）；批内 100 条为并行派发，单批耗时 ≈ 最慢条而非求和
⑯ **复核状态**：保留 P2；新发现；do-while 排水循环（scanner :120-151）+ 批内 join 最慢 attempt（engine :648）+ **BindScheduledExecutor 在 worker 上 command.run() 返回后才重排下一轮**（:72-105——较初审「fixedDelay 语义」更强的证据，轮内不重入与停摆即永停同时成立）核实；默认装配单批 30s 上界经传输层证据链核实成立（见 C7-002 ⑭），⌈N/100⌉×30s 算术平凡，租约跨窗推演合理（双实例不续租为已立案交叉）；**与 C7-002 自洽性**：本条 ⑫/⑨ 与第 2 轮 ⑤ 节互引一致，作为 C7-002 触发条件修订的载体成立；Phase 6 建议合并处置（不合并条目），见 C7-long-running.md「维度复核结论」。

---

## 维度集外线索（本轮新增）

### [维度集外线索-CL-005] removeCompletedRecords 以无 limit 的 findAllByQuery 全量物化已完结事务后再删除（C5/C9 形态线索，不立案展开）
- 维度集外线索（C5 批量无界物化 / C9 逐行循环形态，P2 级候选），供 C5/C9 维度对账：
- 证据：`nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:344` —— `List<NopTccRecord> records = recordDao().findAllByQuery(query);`（query 无 `setLimit`），随后 :346-351 将全部 txnId 物化为内存 `ArrayList`，:352/:354 再 deleteByQuery。
- 链：该 API 虽零生产装配（② 节，C7-005 维持），但 `ITccEngine.cleanCompletedTransactions(retentionTime)` 是公开契约（ITccEngine.java:55），首次按注释建议接线时即触发：保留期窗口内已完结行全量载入进程内存（C7-005 的时间尺度推算：年级百万行级 × 实体开销）→ OOM 面一次性引爆 + 长事务占持。`branchQuery` 的 `in(txnId, txnIds)` 列表同样无界。
- 维度集外线索，不立案展开。
- **复核状态**：确认；归属 C5（批量无界物化）/C9，建议严重度 P2；形态核实——TccRecordStore :344 `findAllByQuery` 无 `setLimit` 全量物化、:346-351 txnIds 全量 ArrayList、:352/:354 deleteByQuery 均属实；与 C7-005 不重复（彼 = 清理零驱动 + 零索引增长面，此 = 清理 API 自身的物化面），有独立行动意义（接线清理前必须分批/限流）；同族先例 WI 1.1 C5-010（不同模块）。

---

## 覆盖度与残留边界（本轮）

**已闭合的第 1 轮残留边界**：
1. 「rpc 底层 HTTP client 内部超时未审」→ **已闭合**：三种内置 client 默认 30s readTimeout（⑤ 节），C7-002 触发条件已精确化。
2. 「tcc 外部驱动部署形态超出仓库」→ 维持（② 节进一步确认仓库内零接线且 beans 无钩子，本边界归部署文档面）。

**本轮新残留边界**：
1. 参与方幂等契约（cancel-before-try、重复 cancel、Try/cancel 乱序到达的处理义务）是 TCC 参与方业务代码的责任面，本仓无法审计参与方实现（C7-013 的 T3a 后果评估以此为前提）。
2. `ISequenceGenerator` 的具体实现（DB 序列/号段/雪花）属平台设施单元，③b 的「重建无重号」结论以「序列状态独立于账本表」为前提，未逐实现核验。
3. 自定义 `IRpcServiceInvoker` 的生产装配形态（是否总有 HTTP 兜底）属部署面，超出本仓库可证范围；仓内仅两个实现且均有界。

**覆盖声明**：指令 ①~⑤ 全部执行完毕——① 枚举了两模块全部 19 个 ERROR/WARN 站点 + 恢复循环 INFO 量级面；② 穷举了全部 4 处删除路径与 5 份 beans.xml 并全仓 grep 调用方；③ 按 a/b/c 三边界完成核查并产出 C7-013；④ 确认零指标；⑤ 闭合传输层超时边界并修订 C7-002。两模块调度入口实测与预注一致（retry Scanner+Engine、tcc 外部驱动恢复链），未发现第三调度入口。

## 终止信号判定与第 3 轮建议

**终止信号：成立（无新增高严重度、无新的无界失效面）**。本轮无新增 P0/P1；三条新增发现（C7-013/014/015）均为条件化 P2；指令 ①②④ 的枚举全部闭合（日志站点无新的无界放大、清理面确证零装配、指标确认为零），未发现第 1 轮之外的新单调增长源（G1~G6 清单无增补）与新挂死点形态（H1~H6 无增补；C7-013 的孤儿分支为 H4「不产出也不报错」的已知形态在新位置的投影）。

按派发规则的弱建议：**无需第 3 轮**。仅当 Phase 4 复核对以下两点产生新线索时可考虑点状补审（非整轮）：(1) C7-013 的参与方幂等契约面是否需要独立于协调者的立案；(2) C7-002+C7-015 合并裁定后是否需要补「自定义 invoker 生产装配清单」的部署面取证（超出本仓库）。
