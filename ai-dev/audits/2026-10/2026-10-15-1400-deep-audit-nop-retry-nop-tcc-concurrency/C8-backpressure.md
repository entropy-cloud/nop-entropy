# C8 背压、流控与过载行为 — nop-retry / nop-tcc 第 1 轮初审（盲审）

审计单元：`nop-retry`（8 jar，40 files / 4,100 行手写）+ `nop-tcc`（9 jar，42 files / 3,872 行手写）
维度：C8 背压、流控与过载行为（生产-消费边界有界降级验证）
日期：2026-10-15 ｜ 分支：concurrency-audit-remediation ｜ 盲审（未读 ai-dev/audits、plans、bugs、lessons 下任何既有文件）

## 检查范围说明

- 审计对象为当前 live code（分支 concurrency-audit-remediation），`_` 前缀生成文件（`_gen/`、`_*.beans.xml` 等）不作为发现对象；`nop-retry-engine/_dump/` 下文件为生成 dump，未纳入。
- 逐行审读的文件：`RetryEngineImpl`、`RetryScannerImpl`、`RetryTaskImpl`、`RetryRecordStoreImpl`、`NopRetryConstants`（api/dao 常量合并）、`NopRetryRecordBizModel`、`NopRetryDeadLetterBizModel`、`NopRetryConfigs`、retry beans 装配（app-engine.beans.xml / app-service.beans.xml）；`TccEngine`、`TccTransaction`、`TccRunner`、`TccBranchTransaction`、`TccTransactionRegistry`、`TccRecordStore`、`TccRpcServiceInterceptor`、`TccGatewayInterceptor`、`DefaultTccServiceMetaLoader`、`TccCoreConstants`、`NopTccRecordBizModel`、`NopTccBranchRecordBizModel`、`NopTccConfigs`、tcc beans 装配（tcc-core-defaults.beans.xml）；交叉核对平台设施 `GlobalExecutors`、`BindScheduledExecutor`、`DefaultThreadPoolExecutor`、`ExecutorHelper`、`CommonConfigs`（池容量/拒绝语义）、`FutureHelper`（futureCall/waitAll 线程语义）、`QueryBean.addOrderField`（asc/desc 语义）、`TccStatus` 码序；提交面边界证据 `NopRetryJobRetryBridge`（nop-job-retry-adapter）。
- grep 基线 `_tmp/cra-baseline-nop-retry-nop-tcc.txt` 的 C8 supplement 两节（queues/policies、rate/semaphore）为起点；基线命中与人工审读结论一致：两模块主代码零自建队列/池/信号量/限流原语，唯一并发原语为 RetryScannerImpl 两个 synchronized（生命周期守卫，非流控）。
- 判级标准：P0=主路径语义违约/包络内必然资源耗尽；P1=负载上升必然死锁/队列溢出/崩溃恢复静默丢数；P2=无观测/边界配置缺陷/理论风险；P3=优化候选。误报校准：有界声明结构、低频路径、平台设施自身实现（只审使用方式）、测试专用代码均按校准规则处理。
- 已按预注规避幻影盘点：两模块无自建队列/池，本维度全部发现围绕「DB 作为队列 + 共享池调度 + 外部驱动扫描」这一实际形态的真实积压/降级面展开。

## 生产-消费边界清单

| # | 边界 | 生产方 | 消费方 | 有界性 | 满后行为 | 与声明语义一致性 |
|---|------|--------|--------|--------|----------|------------------|
| B1 | retry 提交→执行链 | 业务调用方 / 回调链 / NopRetryJobRetryBridge（job 失败事件） | executeTask 首提内联执行（提交者线程承载首次 RPC） | 提交面无容量上限/配额；幂等键去重是唯一闸门 | DISCARD→记日志返回 success(null)；OVERWRITE→删旧建新；COMPLETED→幂等重放；唯一键冲突兜底 | 与 owner doc 一致（含 SUSPENDED 不偷偷恢复、unknown 策略静默回落 DISCARD——后者未声明）；OVERWRITE 非原子（见 C8-006） |
| B2 | 扫描→批处理（allOf 批量面） | RetryScannerImpl.doScan 循环 fetch（batchSize=100 默认，nextTriggerTime asc 最老优先） | processor 内 allOf().join() 全批阻塞等齐 | 批量有界（limit=batchSize）；批间串行（scheduleWithFixedDelay 变体：上一轮 command.run() 完成后才再调度）；批内并发=批大小（无批内并发上限） | 下一批 fetch 必须等本批全部完成（含 immediate-retry 链）；join 无超时（见 C8-002）；扫描任务体异常/拒绝→永久停扫（见 C8-001） | 「分区扫描+乐观锁租约」声明一致；阻塞等齐语义未声明 |
| B3 | 立即重试/延迟重试再调度 | executeImmediateRetry/scheduleImmediateRetry | BindScheduledExecutor(timer→globalWorker)，timer 池 5 线程、worker 池 30 线程/队列 10000 | 每记录一条定时任务，任务数≤记录数（有界）；worker 队列容量 10000（有界，共享） | 队列溢出→RejectedExecutionException→该次重试 future 异常完成，记录靠租约到期被重扫自愈；但同机制会杀死扫描器（C8-001） | 未声明共享池饱和时重试引擎的降级行为 |
| B4 | tcc 事务提交→分支补偿执行 | endAsync→doConfirmAsync/doCancelAsync | TccRunner.confirmAllAsync/cancelAllAsync 全分支并发扇出（waitAll） | 分支数无上限（数据规模决定）；扇出并发无上限（见 C8-007） | 无满后策略——一次性全部发出；补偿 RPC cancelToken=null 不可取消 | 崩溃恢复对称续跑已声明且实现一致；扇出无上限未声明 |
| B5 | 过期扫描→恢复驱动 | checkExpiredTransactions（外部驱动，页 100，逐条 get(expireGap) 串行） | fetchExpiredRecords（expireTime<now 且 status<11 且 retryTimes<max） | 页有界；**生产代码零装配调用方**（全仓库仅测试调用，见 CL-004）；取数 beginTime DESC 最新优先（见 C8-004） | 每条补偿等待上限 expireGap；耗尽 retryTimes（默认 100）后静默永久排除（见 C8-005） | 「外部驱动」为已声明形态；静默排除无观测未声明 |
| B6 | 账本清理 | cleanCompletedTransactions | removeCompletedRecords | **无批量上限**（findAllByQuery 全量物化，见 C8-003） | 无 | 只删终态契约已声明；无界批处理未声明 |

## 发现条目

### [nop-retry-nop-tcc-C8-001]
1. **发现ID**: nop-retry-nop-tcc-C8-001
2. **严重度**: P1
3. **缺陷族**: F5（挂死不可见）/ C8 过载降级（共享池饱和→引擎静默永久停摆）
4. **维度子项**: C8-2 过载降级（globalWorker 共享池占用面）
5. **位置**: nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:96-101、165-167；nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BindScheduledExecutor.java:90-116
6. **证据片段**:
   ```java
   // RetryScannerImpl.java:96
   scanFuture = getExecutor().scheduleWithFixedDelay(
           () -> doScan(processor), 0, scanIntervalMs, TimeUnit.MILLISECONDS);
   // RetryScannerImpl.java:166 — doScan 任务体投递到共享 globalWorker 执行
   return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
   // BindScheduledExecutor.java:109-111 — 任务体执行被拒绝/抛错 → future 异常完成 → 不再再调度
   } catch (Throwable e) {
       future.completeExceptionally(e);
   }
   ```
7. **事实描述**: 扫描循环经 BindScheduledExecutor 落在共享 globalWorker 池（默认 maxPoolSize=30、queueCapacity=10000，StandardThreadPoolExecutor + Abort 语义）上执行；`scheduleWithFixedDelay` 的再调度发生在 `command.run()` 成功返回之后（BindScheduledExecutor.java:95-107 手工链式再调度）。任务体一旦抛出非 Exception 的 Throwable（doScan 只 catch Exception），或 `executor.execute` 因 worker 池满抛 RejectedExecutionException，scan future 即 completeExceptionally 且**不再有任何再调度**——扫描器永久死亡。
8. **积压/降级链**: 平台共享负载上升（其它设施占满 30 线程 + 10000 队列，或一次 OOM/Error）→ doScan 任务体提交被拒/抛 Error → scan future 异常完成 → 无再调度、无重启钩子 → `nop.retry.scanner-started` 之后所有 PENDING/RETRYING 记录无人拾取 → `nop_retry_record` 表积压无界增长（DB 为唯一队列）→ 全部重试任务延迟乃至永不执行；失败方（业务调用方）早已收到提交成功响应，故障完全静默。
9. **声明契约对照**: owner doc 声明「集群扫描按分区取数」「retryingTimeoutMs 执行中锁定超时」，未声明扫描器单点永久死亡且不可恢复的降级形态；retryingTimeoutMs 租约在扫描器死亡后形同虚设（无人重拾）。
10. **触发条件/前置**: globalWorker 队列溢出（负载上升的现实事件，队列仅 10000）或任务体抛 Error；发生一次即永久。
11. **影响面**: 该节点全部重试任务停摆；集群内多节点部署时其他节点可部分缓解（分区/租约竞争），单节点部署（enableCluster=false 默认）则完全停摆。
12. **可观测性**: 仅一条 `nop.err.thread-pool.execute-fail` error 日志；无指标、无停止事件、无自动重启；停摆深度（PENDING 积压量）无任何 gauge。
13. **修复方向**: 扫描循环改用自持调度（catch Throwable 后仍再调度 + 退避）；或对 scan future 挂异常回调告警+重启；暴露 scanner 存活/最后扫描时间指标。

**复核状态**：修正后维持 P1；新发现（同族关联：WI 1.1 C8-011 / WI 1.2 MA-06 / WI 1.5 MA-04 共享池占用族，均不同模块；Error 形态与同目录 C7-004 同代码同现象——C7 终裁 P2，判级 lens 差异注记见文末维度复核结论，Phase 6 合并）；机制逐步核实成立（BindScheduledExecutor 实测 ：101-104 catch(Throwable)→单条 ERROR→completeExceptionally 不重排、:109-111 REE 分支→completeExceptionally **零日志**），「静默」表述需分形态修正：Error 形态有单条 `nop.err.thread-pool.execute-fail` ERROR（有日志 ≠ 有自愈，不影响停摆结论，且初审第 12 项本已如实记载）；REE 形态连单条日志都不存在——可观测性比初审表述更差；P1 承载面 = REE 过载形态（队列溢出为本维度 P1 判据字面枚举项）+ 两形态共有不可逆全量停摆后果（单节点默认部署 complete）。

### [nop-retry-nop-tcc-C8-002]
1. **发现ID**: nop-retry-nop-tcc-C8-002
2. **严重度**: P2
3. **缺陷族**: F5（挂死不可见）/ C8-2 批处理阻塞面
4. **维度子项**: C8-1 扫描→批处理边界（allOf 批量面）
5. **位置**: nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:632-652；RetryScannerImpl.java:113-155
6. **证据片段**:
   ```java
   // RetryEngineImpl.java:637-648（doStart 注册的 processor）
   CompletableFuture<?>[] futures = records.stream()
           .map(record -> executeRetryFromScanner(record, null)  // cancelToken=null
                   ...
                   .toCompletableFuture())
           .toArray(CompletableFuture[]::new);
   // 等待所有 record 执行完成 —— 无超时
   CompletableFuture.allOf(futures).join();
   ```
7. **事实描述**: 批处理 processor 对整批 future 做**无超时 join**，且扫描路径传入 `cancelToken=null`（无取消通道）。`executeRetryFromScanner` 的 future 在 immediate-retry 场景下还串接整个 `scheduleImmediateRetry` 链（RetryEngineImpl.java:394-420），join 等待时间 = 批内最慢记录的完整重试链时长。任一记录的 `rpcServiceInvoker.invokeAsync` 挂死（若 RPC 层无强制超时）则 join 永不返回。
8. **积压/降级链**: 一条下游挂死 → join 永久阻塞 doScan 循环（占用 1 个 globalWorker 线程）→ scheduleWithFixedDelay 的下一轮永不触发 → 该 namespace 全部待重试记录停摆；retryingTimeoutMs（10 分钟）租约到期也无法生效——重拾动作由同一个被冻结的扫描循环执行 → 降级不可自愈；同时批内 100 条记录实体被 `@SingleSession` 会话钉住（RetryScannerImpl.java:113）横跨整个冻结期。
9. **声明契约对照**: owner doc 声明 retryingTimeoutMs 是「执行中锁定超时」，暗示执行时长有界；实现中该超时只解锁数据库锁、不能终结执行本身，且扫描器冻结时连解锁重拾都不发生——声明与实现存在落差。
10. **触发条件/前置**: 单条下游 RPC 挂死且 RPC 基础设施未施加超时；概率低但后果为全量停摆，与 C8-001 同族（区别：C8-001 是永久死亡，本条是永久冻结）。
11. **影响面**: 单节点全 namespace 重试吞吐归零；理论风险（取决于 RPC 层超时保证，引擎层自身无第二道防线）。
12. **可观测性**: 冻结期间无任何日志（processor 内 whenComplete 只在 future 完成时打点）；无扫描滞后指标，外部无法区分「正常积压」与「扫描冻结」。
13. **修复方向**: join 加超时（如 per-record deadline）；扫描执行传入可取消 token；或批内并发受控消费（信号量）+ 单记录超时后按 RETRYING 租约回队。

**复核状态**：保留 P2；新发现（同码同面跨维度关联：同目录 C7-002/C7-015 已由 C7 维度独立立案并终裁 P2 条件化——默认装配 JdkHttpClient readTimeout=30s 封顶单 attempt，永久冻结前提收窄为自定义无超时 invoker/显式禁超时，Phase 6 合并）；join 无超时（:648）与 cancelToken=null（:638）逐行核实成立，行号无漂移。

### [nop-retry-nop-tcc-C8-003]
1. **发现ID**: nop-retry-nop-tcc-C8-003
2. **严重度**: P2
3. **缺陷族**: C5 形态（无界候选）/ C8-3 批处理边界（批量端点条数上限）
4. **维度子项**: C8-3 批处理边界（清理端点）
5. **位置**: nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:332-355
6. **证据片段**:
   ```java
   // TccRecordStore.java:344-354（removeCompletedRecords）
   List<NopTccRecord> records = recordDao().findAllByQuery(query);   // 无 limit
   if (!records.isEmpty()) {
       List<String> txnIds = new ArrayList<>(records.size());
       for (NopTccRecord record : records) { txnIds.add(record.getTxnId()); }
       QueryBean branchQuery = new QueryBean();
       branchQuery.addFilter(FilterBeans.in(NopTccBranchRecord.PROP_NAME_txnId, txnIds));  // 巨型 IN
       branchDao().deleteByQuery(branchQuery);
   }
   recordDao().deleteByQuery(query);
   ```
7. **事实描述**: `cleanCompletedTransactions` 是 tcc 账本唯一的容量回收机制，但其实现一次性 `findAllByQuery` 物化**全部**超保留期的完结事务实体（无 limit/分页），再收集 txnIds 构造单条巨型 `IN` 删除分支。与之对照，同文件的 `fetchExpiredRecords` 严格 pageSize=100 分页。
8. **积压/降级链**: 长期未运行清理 / 首次启用保留期清理 / 事务量突发（如补偿风暴后大批 txn 落终态）→ 单次清理加载数十万至百万级实体 + 等长 txnIds 列表 + 万项 IN 子句 → 驱动线程内存峰值 / ORM 会话膨胀 / DB 单语句压力 → 清理自身 OOM 或超时失败 → 账本继续增长（正反馈）。回收机制自身无界，是本维度「批量端点条数上限」的直接违约面。
9. **声明契约对照**: owner doc 声明「只删除已成功完结/取消的事务，未知状态保留等待人工处理」（语义正确）；批量无上限未声明。
10. **触发条件/前置**: 完结事务存量大于内存可承受规模；与 CL-004（清理驱动本身无装配）叠加时，「首次运行清理」即为最大批量场景。
11. **影响面**: tcc 驱动线程/宿主进程稳定性；DB 单语句性能。理论风险（取决于存量规模），但退化链完全具体。
12. **可观测性**: 无删除行数日志、无分批进度；失败仅靠事务回滚异常浮出。
13. **修复方向**: 按 beginTime 分页循环删除（每批 ≤N 千），IN 子句改分批；记录每批删除行数。

**复核状态**：保留 P2；新发现；findAllByQuery 无 limit（:344）+ 巨型 IN（:351）+ deleteByQuery（:352/:354）逐行核实成立，与同文件 fetchExpiredRecords 分页（:308 findPageByQuery）对照属实；条件面注记：清理驱动当前无生产装配（CL-004），触发需宿主实际运行清理。

### [nop-retry-nop-tcc-C8-004]
1. **发现ID**: nop-retry-nop-tcc-C8-004
2. **严重度**: P2
3. **缺陷族**: C8-1 生产-消费边界（恢复驱动取数顺序饿死）
4. **维度子项**: C8-1 过期扫描→恢复驱动边界
5. **位置**: nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:297-305、307-322
6. **证据片段**:
   ```java
   // TccRecordStore.java:297-305（fetchExpiredRecords）
   query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_expireTime, minTime));
   query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_status, TccStatus.CONFIRM_SUCCESS.getCode()));
   query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_retryTimes, maxRetryCount));
   query.setLimit(pageSize);
   query.addOrderField(NopTccRecord.PROP_NAME_beginTime, true);   // true=DESC（QueryBean.addOrderField(name, desc)）
   ...
   record.setRetryTimes((record.getRetryTimes() == null ? 0 : record.getRetryTimes()) + 1);  // 拾取即计费
   ```
7. **事实描述**: 恢复取数按 `beginTime DESC`（已核实 `addOrderField(name, desc)` 布尔语义，true=desc，OrderFieldBean.java:45）——**最新过期的优先**。每轮拾取即把 expireTime 推到 nextCheckTime 并将 retryTimes+1。对照 retry 侧 `fetchPendingRecords` 用 `nextTriggerTime asc` 最老优先（RetryRecordStoreImpl.java:176），两模块取数策略相反。
8. **积压/降级链**: 过期事务积压超过单轮处理量（页 100，且恢复驱动逐条串行 `get(expireGap)`，单轮耗时可达 100×expireGap）且新过期事务持续到账 ≥ 每轮 100 条 → 每轮 fetch 都取走「最新」的 100 条并推出窗口 → **最老的过期事务（参与方预留资源悬挂最久）被无限推迟**，永不进入补偿；同时被拾取的记录即使补偿尚未执行也已消耗 retryTimes 预算（拾取即计费），预算被扫描节奏而非补偿结果消耗。
9. **声明契约对照**: owner doc 声明「超时扫描恢复对补偿中间态对称续跑」，未声明取数顺序；retry 侧最老优先与 tcc 侧最新优先的矛盾未在任何文档裁定。
10. **触发条件/前置**: 过期事务到账速率持续高于恢复吞吐（串行补偿放大了该概率，见覆盖度节）；需要外部驱动实际在跑（当前默认装配缺位，CL-004）。
11. **影响面**: 最老事务的预留资源（TCC Try 阶段冻结的资金/库存类资源）无限期悬挂；理论风险（需持续过载），但 LIFO 方向性缺陷确定存在。
12. **可观测性**: 无积压深度指标；被饿死的记录不会产生任何日志（它从未被 fetch 到）。
13. **修复方向**: 取数改 beginTime ASC（最老优先，与 retry 侧一致）；retryTimes 计费移到补偿实际发起时（checkExpiredAsync 成功提交后）。

**复核状态**：保留 P2；新发现；`addOrderField(beginTime, true)`=DESC（OrderFieldBean.java:45 实测 desc/asc 渲染语义）与 retry 侧 `addOrderField(nextTriggerTime, false)`=ASC（RetryRecordStoreImpl.java:176 实测）方向相反逐行核实成立；拾取即计费（:315）核实；LIFO 饿死方向性缺陷确定存在；条件面注记：恢复驱动当前无生产装配（CL-004）。

### [nop-retry-nop-tcc-C8-005]
1. **发现ID**: nop-retry-nop-tcc-C8-005
2. **严重度**: P2
3. **缺陷族**: F2（半个成功）/ F5（挂死不可见）——过载下补偿耗尽后的静默永久排除
4. **维度子项**: C8-4 过载可观测 + C8-1 恢复驱动边界
5. **位置**: nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:302-303；TccEngine.java:319-343
6. **证据片段**:
   ```java
   // TccRecordStore.java:302-303
   // 重试次数未超过上限，超过上限的事务会被忽略，不再处理
   query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_retryTimes, maxRetryCount));
   // TccEngine.java:332-334 —— 唯一相关日志仅在单次补偿超时时
   } catch (TimeoutException e) {
       LOG.error("TccEngine.checkExpiredTransactions compensation timeout for txnId={}", ...);
   ```
7. **事实描述**: retryTimes 达到 `nop.tcc.default-max-retry-times`（默认 100）的未完结事务被过滤器**静默永久排除**：不再被恢复驱动拾取、不能进入清理（未达终态、`removeCompletedRecords` 只删 finished）、不产生任何「耗尽」日志或计数。事务与其分支行永久滞留中间态（TRYING/CANCELLING/CONFIRMING 等），参与方预留资源悬挂。
8. **积压/降级链**: 下游持续故障期间，恢复驱动每轮拾取即 retryTimes+1（C8-004 的拾取计费放大消耗速度）→ 100 轮内补偿未成功 → 记录越过阈值 → 从此对恢复驱动和清理**双向不可见** → 每个越过阈值的事务成为一行永久中间态垃圾 + 一份悬挂预留 → 该状态无任何观测出口（日志/指标/死信侧车均无），运维只能靠人工扫表发现。
9. **声明契约对照**: owner doc 提到 100 默认值仅出现在 CANCELLING 修复历史叙述中（「分支补偿计数耗尽后被扫描窗口永久排除」被定性为修复前 bug）；「修复后仍存在通用耗尽排除且无观测」未被声明为边界。代码注释（:302）声明了排除机制本身，未声明其静默性。
10. **触发条件/前置**: 任一参与方连续 100 轮补偿失败（持续网络分区/参与方下线）；下游长时间故障时必然成批出现。
11. **影响面**: 数据面为「崩溃恢复静默丢数」的变体——补偿不再发生且无人知晓；账面为永久中间态行累积。
12. **可观测性**: 零。阈值判定发生在 SQL filter 内，越过时刻无代码路径可挂日志。
13. **修复方向**: fetch 时对 `retryTimes == maxRetryCount` 的记录打 warn 日志 + 计数指标（或移入独立死信表）；owner doc 补声明。

**复核状态**：保留 P2；新发现；过滤器静默排除（TccRecordStore.java:302-303）与引擎侧仅有 per-pass timeout/fail 两类日志（TccEngine.java:332-339）核实成立——越过阈值时刻无任何代码路径可观测属实；owner doc（nop-tcc.md）仅在 P1-3.8-004 修复历史叙述中提及 100 默认值，未声明通用耗尽排除的静默性，对照属实；条件面注记：排除路径以恢复驱动实际在跑为前提（CL-004），故维持 P2 而非 P1。

### [nop-retry-nop-tcc-C8-006]
1. **发现ID**: nop-retry-nop-tcc-C8-006
2. **严重度**: P2
3. **缺陷族**: F2（过载拒绝路径的「半个成功」）
4. **维度子项**: C8-1 提交边界（OVERWRITE 声明语义实现核对）+ C8-5 F2 对照
5. **位置**: nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:255-260；RetryRecordStoreImpl.java:246-249、274-278
6. **证据片段**:
   ```java
   // RetryEngineImpl.java:255-260（handleBlockStrategy OVERWRITE 分支）
   case NopRetryConstants.BLOCK_STRATEGY_OVERWRITE:
       LOG.info("nop.retry.block-strategy-overwrite:recordId={}", existingRecord.getSid());
       recordStore.deleteRecord(existingRecord);            // 无事务包裹
       NopRetryRecord newRecord = recordStore.newRecord(task, request);
       recordStore.saveRecord(newRecord);                   // 独立 REQUIRES_NEW 事务
       return executeWithRetry(newRecord, policy, request, cancelToken, task);
   ```
7. **事实描述**: OVERWRITE 的「删旧建新」是两个独立存储调用（`deleteRecord` 无 @Transactional，`saveRecord` 为 REQUIRES_NEW），无外层事务。删除成功后、插入前发生异常或进程崩溃 → 旧记录已物理删除、新记录未落库、无死信侧车——原任务与新任务双双消失，幂等键被释放。对照同文件 `moveToDeadLetter`（RetryRecordStoreImpl.java:285-310）则正确使用了 @Transactional 包裹「存死信+删原记录」。
8. **积压/降级链**: 提交方（含 job-retry 桥接等自动提交源）以 OVERWRITE 策略重复提交 → 删除旧记录后 DB 抖动/进程崩溃 → 新记录缺失、旧任务蒸发 → 调用方若不重试则任务静默丢失（无死信、无终态、无日志可追）；若重试则副作用以全新记录重放。同时旧记录若处于 RETRYING 在途执行（扫描器正在跑 attempt），覆盖后在途 attempt 的完成回调对已删行做 update，执行结果被静默丢弃——被拒绝的旧任务没有留下任何终态痕迹，构成拒绝路径的「半个成功」。
9. **声明契约对照**: owner doc 声明 OVERWRITE=「删除已有未完成记录并以新请求替换」——替换语义已声明且主路径实现一致（含 COMPLETED 不允许覆盖的幂等重放收敛，实现正确）；**非原子窗口与在途任务蒸发未声明**。
10. **触发条件/前置**: delete 与 save 之间的 DB 异常或进程崩溃（窗口极窄）；或对执行中记录发起 OVERWRITE（运维场景，窗口为整次 attempt 时长）。
11. **影响面**: 单任务丢失；因幂等键释放，后续重提交会重建，实际丢数窗口限于「调用方不再重试」的场景。
12. **可观测性**: 删除有一条 info 日志（block-strategy-overwrite），但「删后未建」无独立信号。
13. **修复方向**: 删旧+建新收进同一事务（对齐 moveToDeadLetter 的做法）；或先建新（临时键）后删旧的反序补偿。

**复核状态**：保留 P2；新发现；OVERWRITE 非原子（RetryEngineImpl :257-259 deleteRecord→saveRecord 两独立存储调用）+ deleteRecord 无 @Transactional（RetryRecordStoreImpl :246-249）+ saveRecord REQUIRES_NEW（:274-278）+ moveToDeadLetter 正确 @Transactional 对照（:285-310）逐行核实成立，行号无漂移；声明契约对照属实（owner doc :31 仅声明替换语义）。

### [nop-retry-nop-tcc-C8-007]
1. **发现ID**: nop-retry-nop-tcc-C8-007
2. **严重度**: P2
3. **缺陷族**: C8-2 过载降级（tcc 补偿扇出积压面）
4. **维度子项**: C8-1 tcc 事务提交→补偿执行边界 + C8-2 补偿扇出
5. **位置**: nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java:133-150、164-176；TccEngine.java:331
6. **证据片段**:
   ```java
   // TccRunner.java:135-149（confirmAllAsync，cancelAllAsync 同构）
   List<CompletionStage<?>> futures = new ArrayList<>(branchTxns.size());
   for (ITccBranchTransaction branchTxn : branchTxns) {
       ...
       futures.add(runBranchConfirmAsync(branchTxn, serviceInvoker));
   }
   return FutureHelper.waitAll(futures);      // 全分支一次性并发发出
   // TccEngine.java:331 —— 驱动侧仅"放弃等待"，不取消在途链
   checkExpiredAsync(record).toCompletableFuture().get(expireGap, TimeUnit.MILLISECONDS);
   ```
7. **事实描述**: 单事务的 confirm/cancel 对全部分支**一次性并发扇出**，分支数仅由数据规模决定（`newBranchRecord` 无每事务分支数上限），无并发上限、无分批。补偿 RPC 全部 `invokeAsync(..., null)`（TccRunner.java:82、108）——cancelToken=null 不可取消。恢复驱动的 `get(expireGap)` 超时只是调用方停止等待，**在途补偿链继续存活**。
8. **积压/降级链**: 大事务（分支数成千上万，多跳传播下可跨服务累积）进入补偿 → 一次性 N 路并发补偿 RPC → 瞬时打满共享 RPC/worker 资源、挤占同进程其它流量的降级空间；若补偿对端挂死 → `get(expireGap)` 超时放弃，但 N 条僵尸链各自挂在 RPC 层等待 → 下一轮恢复驱动重新拾取同一记录再扇出一批 → 同一事务的在途僵尸补偿链按轮次线性累积，无任何取消/去重在途机制（分支幂等由参与方保证，但资源消耗不归参与方管）。
9. **声明契约对照**: owner doc 声明崩溃恢复对称续跑与多跳传播优先级，未声明扇出并发模型；「补偿无并发上限、不可取消」未声明。
10. **触发条件/前置**: 单事务分支数大（数据相关，无配置上限）；或参与方挂死持续多个恢复轮次。
11. **影响面**: 共享基础设施瞬时过载波及非 TCC 流量；僵尸链内存/CPU 滞留。理论风险偏配置缺陷（分支数取决于业务用法），降级链具体。
12. **可观测性**: 单次补偿超时有 error 日志（TccEngine.java:333）；僵尸链存量、扇出并发度均无指标。
13. **修复方向**: 扇出分批+并发上限（如每事务并发 K）；补偿调用传 cancelToken 并在驱动超时时取消；在途链注册表去重。

**复核状态**：保留 P2；新发现；全分支一次性并发扇出（TccRunner :133-150/:164-176 + FutureHelper.waitAll）+ 补偿 invokeAsync cancelToken=null（:82/:108）+ 驱动 `get(expireGap)` 仅放弃等待（TccEngine :331）+ 无在途注册表去重逐行核实成立；多实例并发变体（第 2 轮 §③ 登记）同面归并；条件面注记：恢复驱动当前无生产装配（CL-004），僵尸链累积以驱动多轮运行为前提。

### [nop-retry-nop-tcc-C8-008]
1. **发现ID**: nop-retry-nop-tcc-C8-008
2. **严重度**: P2
3. **缺陷族**: F3（失效面无观测）/ C8-4 过载可观测
4. **维度子项**: C8-4 过载可观测（积压深度/拒绝数/策略发生计数）
5. **位置**: 全模块（grep MeterRegistry/Counter/gauge/metrics 于 nop-retry、nop-tcc src/main 零命中，仅 CoreMetrics 时间工具）；关键无观测点位：RetryEngineImpl.java:252-264（DISCARD/OVERWRITE 仅 info 日志）、RetryRecordStoreImpl.java:152-180（fetchPendingRecords 无积压计数）、TccRecordStore.java:291-326（fetchExpiredRecords 无排除计数）
6. **证据片段**:
   ```java
   // RetryEngineImpl.java:251-253 —— 过载闸门的唯一"观测"
   case NopRetryConstants.BLOCK_STRATEGY_DISCARD:
       LOG.info("nop.retry.block-strategy-discard:recordId={}", existingRecord.getSid());
       return FutureHelper.success(ApiResponse.success(null));
   ```
7. **事实描述**: 两模块主代码零 Micrometer/指标埋点。C8 要求的四类过载信号全部缺失：(a) 积压深度——PENDING/RETRYING 记录数、TRYING/未完结事务数无 gauge；(b) 拒绝/丢弃数——DISCARD、OVERWRITE、幂等重放仅 info 日志无计数；(c) 死信化速率——moveToDeadLetter 仅 warn 日志；(d) 驱动健康——retry 扫描最后成功时间、tcc 恢复驱动是否在跑（CL-004：默认根本没跑）均无心跳指标。
8. **积压/降级链**: 任何过载/停摆场景（C8-001 扫描死亡、C8-002 冻结、C8-005 排除、CL-004 无驱动）发生时，系统表现与「正常低速」完全无法区分 → 运维无告警入口 → 积压恶化至业务方感知（任务永不执行/预留资源对不上账）时已不可定位时间起点。可观测缺位把每个过载缺陷的实际发现时间推后一个量级。
9. **声明契约对照**: owner doc 未声明任何指标契约；DISCARD/OVERWRITE 的行为语义已声明但「发生频率可观测」未声明——判定为无观测缺陷而非声明边界。
10. **触发条件/前置**: 任意过载事件。
11. **影响面**: 全部 C8 发现的运维可见性；平台层 ExecutorHelper.registerMetrics 有线程池指标，但模块级业务积压信号为零。
12. **可观测性**: 本条即可观测性缺口本身。
13. **修复方向**: 暴露最小指标集：按 namespace/group 的 PENDING/RETRYING/SUSPENDED 计数 gauge、DISCARD/OVERWRITE/dead-letter 计数器、scanner 心跳（最后扫描时间戳）、tcc 未完结事务计数与 retryTimes 分布。

**复核状态**：保留 P2；新发现；本轮独立 grep（MeterRegistry/Counter/gauge/metrics 于 nop-retry、nop-tcc 全 main 源码）复核零命中成立；DISCARD/OVERWRITE 仅 info 日志（:251-253/:255-256）、fetchPendingRecords 无积压计数、fetchExpiredRecords 无排除计数逐点核实成立。

### [nop-retry-nop-tcc-C8-009]
1. **发现ID**: nop-retry-nop-tcc-C8-009
2. **严重度**: P2
3. **缺陷族**: C8-1 提交边界（无界任务生产链）
4. **维度子项**: C8-1 retry 提交→执行链（回调递归提交面）
5. **位置**: nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:541-574（triggerCallback）、555-557（新键构造）、504-506 与 518-520（成功/失败两侧触发点）
6. **证据片段**:
   ```java
   // RetryEngineImpl.java:555-557
   IRetryTask callbackTask = newRetryTask(callbackService, callbackMethod)   // 回调原任务 service/method
           .withPolicyId(policy.getCallbackPolicyId())
           .withIdempotentId(record.getIdempotentId() + "_callback");
   // :563 异步执行回调任务 —— 回调任务自身完成时又走 handleExecutionSuccess → triggerCallback
   executeTask(callbackTask, callbackRequest, null)...
   ```
7. **事实描述**: 回调任务复用原任务的 service/method（owner doc 明确「当前实现仍回调原任务 service/method」），幂等键追加 `_callback` 后缀；回调任务完成时若其策略（callbackPolicyId 指向的策略）也 callbackEnabled，则再次触发回调，幂等键变为 `..._callback_callback`——**每跳都是全新幂等键**，唯一键去重与 DISCARD/OVERWRITE 均无法拦截。无深度上限、无总量上限、无已访问策略环检测。
8. **积压/降级链**: callbackPolicyId 自指（指向自身或与其它策略互指，且 trigger=ALWAYS/ON_SUCCESS）→ 任务成功执行业务方法 → 生成 `_callback` 新记录并**再次执行同一业务方法** → 成功 → 生成 `_callback_callback` 记录再执行 → …… nop_retry_record 表每轮 +1 行无界增长，且业务副作用被无限重放（每次都是真实 RPC）。生产速率 = 单任务执行周期，持续吞噬 DB 与下游服务。
9. **声明契约对照**: owner doc 声明回调「仍回调原任务 service/method」与 `_callback` 后缀，未声明链式深度风险与自指配置的后果——属边界配置缺陷（需管理员配置出环），但代码侧零防护。
10. **触发条件/前置**: 策略配置成环（自指或互指）且环上策略 callbackEnabled=true；一次性配置错误即永久生产。
11. **影响面**: 记录表无界增长 + 下游业务方法被无限重放（副作用放大）；理论风险（依赖错误配置），生产链完全具体。
12. **可观测性**: 每跳有 callback-success info 日志，但无链深/计数指标，从日志难以察觉「同一业务方法的 _callback_n 序列」异常。
13. **修复方向**: 回调链深度上限（如 idempotentId 中 `_callback` 计数 ≥1 即不再触发回调）；或回调任务强制使用不含回调的内置策略；owner doc 补声明禁止自指配置。

**复核状态**：保留 P2；新发现；回调链每跳新幂等键（:555-557 `+_callback`）+ 成功/失败两侧触发点（:504-506/:518-520）+ 回调任务完成再次走 handleExecutionSuccess→triggerCallback 的递归结构逐行核实成立；owner doc（nop-retry.md:81）声明 `_callback` 后缀但未声明链式深度风险，对照属实；判级注记：需管理员配置出环（callbackPolicyId 自指/互指），属「条件化理论风险」但生产链完全具体，P2 校准恰当。

### [nop-retry-nop-tcc-C8-010]
1. **发现ID**: nop-retry-nop-tcc-C8-010
2. **严重度**: P3
3. **缺陷族**: C8-1 提交边界（无配额）
4. **维度子项**: C8-1 retry 提交→执行链
5. **位置**: nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:168-189（executeTask 提交面）；RetryRecordStoreImpl.java:88-127（newRecord 无配额检查）
6. **证据片段**:
   ```java
   // RetryEngineImpl.java:171-179 —— 唯一的提交闸门是幂等键查重
   NopRetryRecord existingRecord = recordStore.findRecordByIdempotentId(
           task.getNamespaceId(), task.getGroupId(), task.getIdempotentId());
   if (existingRecord != null) {
       return handleExistingRecord(existingRecord, policy, request, cancelToken, task);
   }
   NopRetryRecord record = recordStore.newRecord(task, request);
   recordStore.saveRecord(record);      // 无 namespace/group 容量上限、无速率限制
   ```
7. **事实描述**: 提交面无任何容量配额（每 namespace/group 最大未完成记录数）与速率限制；DISCARD/OVERWRITE 是幂等去重而非准入控制，不同幂等键之间互不设限。DB 是唯一有界介质（磁盘），扫描消费速率上限为 batchSize/批时长。
8. **积压/降级链**: 上游故障风暴产生大量**不同**幂等键的提交（job 失败风暴经 NopRetryJobRetryBridge 的 fireId 天然互异）→ 记录数无界增长 → DB 压力、扫描持续满负荷循环（doScan 在积压期间退化为连续 drain，scanIntervalMs 失效）→ 恢复时间与积压量线性相关。结构有界（磁盘），无失控，故 P3 优化候选。
9. **声明契约对照**: owner doc 声明命名空间/分组隔离，未声明容量配额；「DB 即队列」为模块固有设计，判为已声明边界的一部分，仅配额缺失列为优化项。
10. **触发条件/前置**: 上游持续过载产生海量不同幂等键。
11. **影响面**: 消费延迟随积压线性增长；与 C8-001/002 叠加时积压不可恢复。
12. **可观测性**: 积压深度无 gauge（并入 C8-008）。
13. **修复方向**: namespace/group 级未完成记录数配额 + 提交侧快速拒绝；与 C8-008 指标同批落地。

**复核状态**：保留 P3；新发现；提交面唯一闸门=幂等键查重（:171-175）、newRecord 无配额检查（RetryRecordStoreImpl :88-127）核实成立；「DB 即队列」为固有设计、判优化候选的校准恰当。

### [nop-retry-nop-tcc-C8-011]
1. **发现ID**: nop-retry-nop-tcc-C8-011
2. **严重度**: P3
3. **缺陷族**: C8-2 批处理边界（会话驻留时长）
4. **维度子项**: C8-2 批处理阻塞面的资源驻留
5. **位置**: nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:113-155（@SingleSession doScan + 循环内 processor.accept）；RetryEngineImpl.java:648（join）
6. **证据片段**:
   ```java
   // RetryScannerImpl.java:113-151
   @SingleSession
   protected void doScan(Consumer<List<NopRetryRecord>> processor) {
       ...
       do {
           ...
           List<NopRetryRecord> lockedRecords = recordStore.tryLockRecordsForProcess(records, retryingTimeoutMs);
           ...
           processor.accept(lockedRecords);     // 整批 join 阻塞在此，ORM 会话横跨全程
       } while (running);
   ```
7. **事实描述**: `@SingleSession` 使 ORM 会话横跨整个 doScan 循环体，批内 100 条锁定记录实体（含 requestPayload 快照）在 processor 阻塞 join 期间保持 managed 状态；批时长被最慢记录的完整重试链（含 immediate-retry 间隔）放大后，会话驻留时间可达分钟级。
8. **积压/降级链**: 慢批 → 会话内 managed 实体 + 关联 attempt 实体长时间驻留 → 单节点内存驻留面扩大（有界：批大小×负载长度，非泄漏）；do-while 连续 drain 时旧批实体在新批 fetch 前才释放，驻留峰值约为两批。结构有界，无耗尽路径，故 P3。
9. **声明契约对照**: @SingleSession 为框架声明的事务会话语义，使用方式未见违约；列为驻留时长优化项。
10. **触发条件/前置**: 批内存在慢记录（长重试链/慢 RPC）。
11. **影响面**: 内存驻留峰值；无正确性影响。
12. **可观测性**: 无（线程池指标不覆盖 ORM 会话驻留）。
13. **修复方向**: processor 消费前 detach 批实体或缩短会话边界至 lock 阶段。

**复核状态**：保留 P3；新发现；@SingleSession doScan（:113）+ 循环内 processor.accept（:147）整批 join 驻留结构核实成立；结构有界（批大小×负载长度）、无耗尽路径，P3 驻留优化校准恰当。

### [维度集外线索-CL-004]（维度集外线索，不立案展开）
1. **发现ID**: nop-retry-nop-tcc-CL-004
2. **形态归属**: C5（无界账本增长）/ F5（挂死不可见）形态；P1 级线索，按 overlay 规则登记，不在 C8 立案展开
3. **位置**: nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:315-355（checkExpiredTransactions / cleanCompletedTransactions）；nop-tcc/nop-tcc-core/src/main/resources/_vfs/nop/tcc/beans/tcc-core-defaults.beans.xml:12（nopTccEngine 仅注册为普通 bean）
4. **证据片段**:
   ```xml
   <!-- tcc-core-defaults.beans.xml —— 全部 TCC 装配 -->
   <bean id="nopTccEngine" class="io.nop.tcc.core.impl.TccEngine" />
   <bean id="nopRpcServiceInterceptor_tcc" class="io.nop.tcc.core.interceptor.TccRpcServiceInterceptor" />
   ```
   全仓库 grep（Java/beans/xml，排除 target）：`checkExpiredTransactions`、`cleanCompletedTransactions` 的调用方仅有 ITccEngine 接口声明、TccEngine 实现与 3 处测试（TestTccEngine*.java）。nop-retry 侧有跨模块装配先例（nop-job-retry-adapter 桥接提交面），tcc 恢复驱动无对应物。
5. **线索描述**: TCC 崩溃恢复与账本清理两个入口在仓库内**无任何生产装配与调度**。「外部驱动过期扫描」是本次审计的已声明契约，故不作为 C8 立案对象；但登记以下事实供裁决：(a) 默认部署下任何超时事务的补偿永不发生、完结事务永不清理，`nop_tcc_record`/`nop_tcc_branch_record` 未完结行与参与方预留资源随超时事务数无界累积（C5 形态）；(b) 累积过程零指标零日志，与 C8-008 叠加后不可见（F5 形态）；(c) owner doc 只描述恢复机制语义，未声明「宿主必须自行装配调度，否则恢复为死代码」这一使用前提。
6. **严重度**: P1（默认部署包络内，超时一旦发生即确定性累积；是否降级取决于「外部驱动为宿主责任」这一契约成立与否，请协调方裁定）。
7. **处置建议**: 若裁定为缺口，最小修复为 tcc-service 提供可选默认调度 bean（配置开关，默认关或开均可）+ owner doc 补使用前提声明。

**复核状态**：**确认**；复核仍成立（本轮独立 grep 复核：checkExpiredTransactions/cleanCompletedTransactions 全仓库调用方仅 ITccEngine 声明、TccEngine 实现与测试（TestTccEngineTransactions ×2、TestTccEngine ×5、TestTccRpcServiceInterceptorBridge stub ×2），tcc-core-defaults.beans.xml 实测仅 3 bean（metaLoader/engine/interceptor），无任何调度装配）；形态真实、与 C8-003/004/005/007 等条件面发现不重复（彼等以驱动存在为前提，本条是驱动缺位本体）、有独立行动意义；P1 级建议维持，终裁（是否补默认驱动 vs 声明宿主责任契约）留协调方/跨单元裁定。

## 覆盖度与残留边界

**已覆盖（C8 五个子项）**：
1. 生产-消费边界盘点：6 条边界（B1-B6）全部核对容量与满后行为；DISCARD/OVERWRITE/COMPLETED 幂等重放/SUSPENDED 语义与 owner doc 逐条比对一致（发现 C8-006 为唯一非原子偏差，unknown blockStrategy 静默回落 DISCARD 已在 B1 备注）。
2. 过载降级：globalWorker（30 线程/10000 队列/Abort）占用面全链核对（C8-001、C8-002、C8-011）；tcc 补偿扇出（C8-007）。BindScheduledExecutor 的手工链式再调度语义（拒绝/Throwable→永久停）为本次新核实证据。
3. 批处理边界：扫描批 batchSize=100（配置上限，fetch 带 limit）；恢复页 pageSize=100；**清理端点无上限**（C8-003）；tcc 分支扇出无上限（C8-007）。
4. 过载可观测：grep 证实零指标（C8-008）；扫描/恢复驱动健康无心跳；耗尽排除静默（C8-005）。
5. F2 对照：OVERWRITE 拒绝路径半个成功（C8-006）；批处理部分失败语义核查为**合格**——processor 内每记录 whenComplete 独立记日志，allOf.join() 抛出的首个异常被 doScan catch(Exception) 捕获后循环继续（RetryScannerImpl.java:146-150），单记录失败不影响批内其他记录也不杀扫描循环；TCC 聚合状态（aggregateCancelBranchStatus/aggregateConfirmBranchStatus）失败优先折叠，无「部分成功误报全成功」面。

**残留边界**：
- `rpcServiceInvoker.invokeAsync` 的实际超时/池化行为属平台设施实现（nop-rpc/nop-cluster），本单元只审使用方式；C8-002 的严重度以此为前提标注「理论风险」。
- `tryUpdateManyWithVersionCheck`、`updateByQuery` CAS、ORM 会话管理属 nop-dao 平台设施，未展开。
- 外部驱动装配缺位（CL-004）按已声明契约不立案，留协调方裁决。
- nop-job-retry-adapter 属 nop-job 单元，仅作 B1 提交面证据引用，未审计其内部。
- 测试源集（MockRpcServiceInvoker、TestTccEngine* 等）按误报校准排除。
- retry web 层与 tcc web 层 crud 端点为平台 CrudBizModel 派生（本单元内为空壳 BizModel），批量 mutations 的条数上限属 nop-biz 平台设施实现，未展开。

---

## 维度复核结论

复核人：C8 维度独立复核子代理（与初审/第 2/3 轮代理相互独立）｜日期：2026-10-16｜基线：分支 concurrency-audit-remediation live code
复核方式：逐条亲自打开发现引用的 file:line 核对证据（行号漂移以实际代码修正）；对照历史审计面（cra-index.md、同目录 C1/C3/C6/C7 各轮、WI 1.1–1.5 台账、ai-dev/bugs、ai-dev/lessons、owner docs）；P1 逐子项复核（证据/时序/判级/F3）。

### 一、逐条裁定表

| 发现ID | 初判 | 裁定 | 现状 | 一句话理由 |
|--------|------|------|------|-----------|
| C8-001 | P1 | **修正后维持 P1** | 新发现 | 机制逐步核实成立（Throwable→单条 ERROR 后不重排、REE→零日志后不重排，均永久死亡）；「静默」表述分形态修正（见子项明细）；P1 承载面=REE 过载形态（队列溢出为本维度 P1 判据字面枚举项）+不可逆全量停摆 |
| C8-002 | P2 | 保留 P2 | 新发现（同码同面跨维度关联 C7-002/C7-015，C7 终裁 P2 条件化，Phase 6 合并） | join 无超时+cancelToken=null 逐行核实；默认装配 JdkHttpClient readTimeout=30s 封顶单 attempt，P2 校准与 C7 一致 |
| C8-003 | P2 | 保留 P2 | 新发现 | findAllByQuery 无 limit+:351 巨型 IN+:354 deleteByQuery 逐行核实；触发需清理驱动实际运行（CL-004 条件面） |
| C8-004 | P2 | 保留 P2 | 新发现 | beginTime DESC（addOrderField 第二参=desc 实测）与 retry 侧 ASC 方向相反核实成立；拾取即计费(:315)核实；条件面=CL-004 |
| C8-005 | P2 | 保留 P2 | 新发现 | 过滤器静默排除+越阈时刻零代码路径可观测核实成立；条件面=CL-004（驱动缺位故维持 P2 非 P1） |
| C8-006 | P2 | 保留 P2 | 新发现 | deleteRecord 无事务(:246-249)+saveRecord REQUIRES_NEW(:274-278)+无外层事务(:257-259)与 moveToDeadLetter 正确对照(:285-310)逐行核实 |
| C8-007 | P2 | 保留 P2 | 新发现 | 一次性全分支扇出+cancelToken=null+get(expireGap) 仅弃等+无在途去重逐行核实；多实例变体（第 2 轮 §③）同面归并 |
| C8-008 | P2 | 保留 P2 | 新发现 | 本轮独立 grep 复核两模块 main 源码零指标命中成立 |
| C8-009 | P2 | 保留 P2 | 新发现 | 回调链每跳新幂等键+两侧触发点+递归结构逐行核实；条件化理论风险（需配置出环）P2 校准恰当 |
| C8-010 | P3 | 保留 P3 | 新发现 | 提交面唯一闸门=幂等查重、无配额核实成立；优化候选校准恰当 |
| C8-011 | P3 | 保留 P3 | 新发现 | @SingleSession doScan+整批 join 驻留核实成立；结构有界无耗尽路径 |
| C8-012 | P1 | **保留 P1** | 新发现 | future 拓扑断裂逐步核实（详见子项明细）；「罕见但可具体化」按校准规则判 P1 成立 |
| C8-013 | P2 | 保留 P2 | 新发现 | setter 零校验+setLimit 透传实测成立；「owner doc 声明+fail-fast 守卫」双条件全参数面不成立复核维持 |
| C8-014 | P3 | 保留 P3 | 新发现 | 本轮独立 grep 复核 getMaxRetryTimes 零执行性读方成立；与 C8-016 同族（Phase 6 合并） |
| C8-015 | P3 | 保留 P3（行号修正） | 新发现 | 空列表零日志/有日志分支不对称核实成立；消费方行号修正为 RetryRecordStoreImpl:167-174（初审误记 RetryScannerImpl:167-174，该处实为 getExecutor()），不改判级 |
| C8-016 | P3 | 保留 P3 | 新发现 | 本轮独立 grep 复核 policy 级 retryingTimeoutMs 零引擎读方+生效租约链（scanner 级配置）成立；与 C8-014 同族（Phase 6 合并） |
| C8-017 | P2 | 保留 P2 | 新发现 | IRpcCall future-only 契约文本+6 组同步抛注入点+调用方 catch 面逐一核实成立；「契约性缺口、当下无仓库内破绽」前提属实 |

### 二、P1 发现子项复核明细

#### C8-001（扫描循环被拒/Error 即静默永久停摆）——完整证据链

- **a) 两形态在该调度语义下的实际后果**（BindScheduledExecutor.java 实测，行号较初审微漂）：
  - **Error/Throwable 形态**（command.run() 抛出）：:101-104 `catch (Throwable e)` → 单条 `LOG.error("nop.err.thread-pool.execute-fail", e)` → `future.completeExceptionally(e)` → **return，不重排**。与 C7-004 复核修正后的事实一致（初审 C8-001 第 12 项可观测性「仅一条 error 日志」本已如实记载，无需修正）。
  - **REE 形态**（`executor.execute` 被 worker 池拒绝）：:109-111 外层 `catch (Throwable e)` → `future.completeExceptionally(e)`，**该 catch 分支无任何 LOG 语句**——REE 形态连单条日志都不存在，可观测性比初审表述**更差**；StandardThreadPoolExecutor RejectHandler（:205-209 Abort）+ globalWorker 默认 maxPoolSize=30（CommonConfigs:26-27）/queueCapacity=10000（ThreadPoolConfig:25）实测确认可达。
  - 两形态共同后果：scan future 异常完成后 `running` 仍为 true（RetryScannerImpl 无任何观察点/重启钩子，stopScanning 语义背离）——「不再有任何再调度→扫描器永久死亡」成立。
- **b) 「静默」表述修正**：初审「仅一条 error 日志」只对 Error 形态成立；REE 形态为完全零日志。**有单条 ERROR ≠ 有自愈**：无指标、无停止事件、无自动重启的结论不变；停摆深度（PENDING 积压量）无 gauge 不变（C8-008）。此为事实表述修正，非机制弱化。
- **c) 终裁：修正后维持 P1**。理由链：(1) 触发时序具体且每步经代码核实，非 ≥3 罕见条件合取亦非纯理论——REE 形态触发=平台共享池饱和（全平台 stream/job/wf/task/ai/batch 共用该池，极端故障风暴下 30 线程+10000 队列填满是 C8 维度本位场景，「队列溢出」为本维度 P1 判据字面枚举项）；Error 形态触发=扫描路径任意 Error（OOM 等可由 C8-013 巨批/C8-003 无界清理在同进程内制造）。(2) 后果不可逆且全量：负载事件消退后扫描器**不恢复**，重试处理永久停止直至进程重启，DB 积压无界增长，提交方早已收到成功响应——单节点默认部署（enableCluster=false）影响面 complete。(3) **跨维度判级差异注记（Phase 6 合并裁定输入）**：Error 形态与同目录 C7-004 同代码同现象，C7 复核终裁 P2（C7 立案 lens=Error 检测盲区，触发频率低）；C8-001 的 P1 承载面是 REE 过载形态（C7-004 未覆盖）+两形态共有的不可逆停摆后果——两维度判级不构成事实矛盾，合并时建议以「过载触发形态 P1 / Error 触发形态 P2」拆分口径或由协调方统一。
- **F3 完备性**：初审第 12 项可观测性+第 8 项积压/降级链已覆盖同族站点（S1/S2 行重叠登记）；平台侧 catch 点（BindScheduledExecutor）为 C7 复核补录站点，两形态均已入本明细，无新增缺漏。

#### C8-012（executeImmediateRetry 返回 future ≠ 回调挂接 future）——完整证据链

- **a) 返回对象与挂接对象确实不同一**（RetryEngineImpl.java 实测）：
  - `result` 于 :336 创建、**:391 返回**（初审记 :392 系微漂）；`result` 的全部写方（:350-353、:357、:361、:369、:378-381、:385 的 complete/completeExceptionally）均位于 cb1（:341-389 闭包）或 cb1 内挂接的 `scheduleImmediateRetry(...).whenComplete(...)`（:348-354/:376-382）内。
  - `scheduleImmediateRetry` 的 `future` 于 :405 创建、:419 返回；其**唯一写方**是 :407-417 delay-lambda 内 :409-415 的 whenComplete；`executor.schedule` 返回的调度内部 future（BindScheduledExecutor.schedule :55-62 手工 promise）被**丢弃**。
  - 拓扑结论成立：`result` 与 cb 实际挂接的中间 stage 是不同对象；`future` 与 delay-lambda 实际执行的调度内部 future 是不同对象。
- **b) 同步 DB 异常下 result 永不完成的时序逐步复核（每步 live code 核实可信）**：
  - T1（延迟跳）：T1.1 immediateRetryCount>0 策略（默认 0，NopRetryApiConstants:25 实测——非默认前提成立）首提失败→:343-347 调度下一跳；T1.2 延迟跳 `executeImmediateRetry`（:408 lambda 内调用）的 `doExecute`（:340）同步段 `newAttempt/saveAttempt`（:454-456，futureCall 无包裹的直连 DB 写）DB 抛；T1.3 异常冲出 lambda → BindScheduledExecutor :101-104 catch(Throwable)→单条平台 ERROR→完成被丢弃的调度内部 future；T1.4 :405 `future` 的 whenComplete（:409）从未挂接→上一跳 cb2（:348-354）永不触发→上一跳 `result` 永不完成→executeRetryFromScanner future 永不完成→`allOf(futures).join()`（:648）永久阻塞；T1.5 scheduleWithFixedDelay 链在 worker 上 command.run() 返回后才重排（BindScheduledExecutor :100-107 实测）→扫描器永久冻结，租约重拾由同一冻结循环承载→不可自愈。**每步成立。**
  - T2（回调体）：cb1 内 `recordStore.updateRecord`（:346/:374）或 `handleExecutionSuccess`→`updateRecord`（:501，先于 :502 task-completed 日志）DB 抛→按 JDK whenComplete 语义仅使 cb1 所挂接的派生 stage 异常完成（无人持有，零日志）→`result` 不受影响永不完成→同 T1.4-T1.5 冻结。**成立。**
  - 反向核验（非缺陷面确认）：cb2 体抛（doExecute :487 saveAttempt）落在 invokeAsync 链尾→被 cb1 以 ex 接住→正常走失败分支，**不冻结**（与第 3 轮 F9 一致）；首跳 E-a 同步抛传播给扫描循环→doScan 内层 catch(:148-150) 接住→批次跳过循环存活、记录靠租约自愈（与第 3 轮 F6 行注记一致）。
- **c) 前提对判级的影响——终裁：保留 P1**。判级校准规则「罕见但可具体化可判 P1」的两个前提各自常规：immediateRetryCount>0 是标准策略选项（非异常配置），DB 瞬时故障（连接池耗尽/故障切换）是过载期常态事件；合取罕见但每个可具体化，触发即确定性复现（同步异常逃逸，非竞争）。后果为确定性、永久性、部分形态全静默（T2 零日志）的全节点停摆。P1 维持。**F3 完备性复核**：E-a/E-b/E-c 枚举经复核完备（含 scheduleImmediateRetry 调度拒绝这一低频注入点已被 E-c 括注覆盖）；第 3 轮家族全扫「全仓仅 F6/F7 一处现实断裂拓扑」经抽验 F18/F19/F20（TccRunner 手工 promise 拓扑同型、默认装配无 cb-throw 注入源，ITccExceptionChecker 默认无 bean 经 tcc-core-defaults.beans.xml 实测）成立。

#### 不确定项/条件面发现子项注记（非 P1，随表备注）

- C8-004/C8-005/C8-007：触发均以恢复驱动实际运行为前提（CL-004：当前零生产装配）——条件面已在各自复核状态行标注，若 Phase 6 裁定补默认驱动，三者应同批复核判级。
- C8-002：与 C7-002/C7-015 同码同面（扫描器冻结），C7 终裁 P2 条件化；本维度 P2 维持，Phase 6 合并避免双计。

### 三、维度集外线索处置（CL-004 / CL-006 / CL-007）

| 线索 | 处置 | 核实结论 |
|------|------|---------|
| CL-004（tcc 过期扫描/清理零生产装配） | **确认**（P1 级建议维持，终裁留协调方） | 本轮独立 grep 复核「第 2 轮声称仍成立」属实：checkExpiredTransactions/cleanCompletedTransactions 全仓库调用方仅接口声明、实现与测试（TestTccEngineTransactions ×2、TestTccEngine ×5、TestTccRpcServiceInterceptorBridge stub ×2）；tcc-core-defaults.beans.xml 实测仅 3 bean，无调度装配。owner doc（nop-tcc.md）声明恢复语义但未声明「宿主必须自行装配调度」使用前提。形态真实、与 C8 条件面发现不重复（C8-003/004/005/007 以驱动存在为前提，本条是缺位本体）、独立行动意义成立（补默认调度 bean 或声明宿主责任契约）。|
| CL-006（完成回调内同步 REQUIRES_NEW DB 事务占用回调线程） | **确认**（线程模型已本仓核实，严重度终裁留 rpc/C4/C6 跨单元） | 形态核实：finishConfirmAsync/finishCancelAsync（TccBranchTransaction :107-122/:141-164）→updateTccBranchStatusAsync（TccRecordStore :220-283，REQUIRES_NEW+futureCall 调用线程同步执行）全部挂在 invokeAsync 完成回调上。线程模型核实：默认装配 ClusterRpcServiceInvoker（primary 别名）→HttpRpcService.callAsync（thenApply 同步变换）→JdkHttpClient `sendAsync` 且 builder.executor() 恒挂专用 DefaultThreadPoolExecutor（JdkHttpClient :108-112）——完成回调运行在 **JDK HttpClient 进程级共享专用线程池**（单例 nopRawHttpClient 全进程共用），非 NIO event-loop，但为全进程 RPC 响应处理共享面：回调内同步 DB 抖动将阻塞同客户端全部响应完成。不重复、独立行动意义成立（DB 工作移出完成回调）。|
| CL-007（retryFromDeadLetter 零生产装配） | **确认**（严重度留功能覆盖/协调方裁定） | 本轮独立 grep 复核成立：全仓库（java/xbiz/xml，排除 target）命中仅接口声明、实现与测试；NopRetryDeadLetterBizModel 实测为裸 CrudBizModel 派生无任何 biz 方法暴露；owner doc（nop-retry.md:79）确声明该手动重放入口。声明机制无生产入口属实；与 C8-006/死信链路发现不重复（彼为行为缺陷，本条为运维闭环出口缺位）。|

### 四、停摆面统一表（S1~S16）抽验结果

抽验 S1、S5、S9 三行，全部与 live code 一致：

| 行 | 抽验点 | 结果 |
|----|--------|------|
| S1 | RetryScannerImpl.doScan 任务体 Throwable → BindScheduledExecutor catch(Throwable)（:101-104）→ 单条 ERROR → future 异常完成 → 不重排 → 永久死亡 | **一致**（行号微漂：catch 实际 :101-104，S 表所属第 2 轮引 :82-91 为 C7-004 旧行号） |
| S5 | 立即重试链同步抛 → join 永久冻结；延迟跳抛仅一条平台日志（:102）、成功回调体抛零日志（无人持有中间 stage） | **一致**（与 C8-012 T1/T2 逐步复核结论吻合） |
| S9 | 单条补偿挂死 → get(expireGap) 超时跳过（TccEngine :331-334 有 error 日志）→ 在途链存活（cancelToken=null，TccRunner :82/:108）→ 下一轮重拾再扇出，僵尸链按轮累积 | **一致**（引擎内无在途注册表/去重实测成立） |

其余行经本轮逐条复核过程的侧面覆盖（S2=C8-001 REE 形态、S3/S13/S14/S16=核验通过行、S4=C8-002、S7=C8-009、S8=pass 级登记、S10/S11=C8-004/005、S12=C8-007、S15=C8-003）未见与 live code 矛盾。

### 五、复核统计

- **17 条发现**：保留/维持 17（其中**修正后维持 2**：C8-001 可观测性表述分形态修正并补跨维度判级注记、C8-015 消费方行号修正）；**降级 0；驳回 0**。判级分布：**P1×2**（C8-001、C8-012）、**P2×9**（C8-002~009、C8-013、C8-017）、**P3×5**（C8-010、C8-011、C8-014、C8-015、C8-016）。
- **现状标注**：17/17 新发现（WI 1.1–1.5 审计对象为 stream/job/wf/task，与 retry/tcc 代码零交集；历史 full-code-review 修复——plan 2282/2388/342/347 等——均先于本次审计基线且不覆盖本维度发现；ai-dev/bugs、ai-dev/lessons 零 retry/tcc 关联条目）。同族跨模块关联 3 处已备注（C8-001↔WI 1.1 C8-011/WI 1.2 MA-06/WI 1.5 MA-04 共享池族）。
- **同码同面跨维度条目（Phase 6 合并裁定输入，非本维度驳回）**：C8-002↔C7-002/C7-015；C8-001（Error 形态）↔C7-004；停摆家族判级建议按「触发形态拆分口径」合并。
- **死配置同族**：C8-014（tcc 分支级 maxRetryTimes）与 C8-016（retry policy 级 retryingTimeoutMs）同族同型（声明护栏零执行、信任面错位），判级一致 P3/P3，建议 Phase 6 合并处置。
- **CL 线索**：确认 3 / 驳回 0 / 待跨单元裁定 1（CL-006 严重度终裁；CL-004、CL-007 处置建议已给出，裁定权在协调方）。

---

## 最终保留项

| # | 发现ID | 标题 | 最终严重度 | 现状 |
|---|--------|------|-----------|------|
| 1 | nop-retry-nop-tcc-C8-001 | 扫描循环被拒/Error 即静默永久停摆（两形态，REE 形态零日志） | P1（修正后维持） | 新发现 |
| 2 | nop-retry-nop-tcc-C8-012 | executeImmediateRetry future 拓扑断裂→allOf.join 永久冻结（部分形态零日志） | P1 | 新发现 |
| 3 | nop-retry-nop-tcc-C8-002 | 扫描批 allOf.join 无超时+无取消通道→单点挂死全 namespace 冻结 | P2 | 新发现 |
| 4 | nop-retry-nop-tcc-C8-003 | tcc 清理端点无界批处理（全量物化+巨型 IN） | P2 | 新发现 |
| 5 | nop-retry-nop-tcc-C8-004 | tcc 恢复取数 beginTime DESC 最新优先→最老事务饿死+拾取即计费 | P2 | 新发现 |
| 6 | nop-retry-nop-tcc-C8-005 | 补偿预算耗尽后静默永久排除（双向不可见零观测） | P2 | 新发现 |
| 7 | nop-retry-nop-tcc-C8-006 | OVERWRITE 删旧建新非原子→拒绝路径半个成功 | P2 | 新发现 |
| 8 | nop-retry-nop-tcc-C8-007 | tcc 补偿全分支一次性并发扇出无上限+不可取消+僵尸链累积 | P2 | 新发现 |
| 9 | nop-retry-nop-tcc-C8-008 | 两模块过载信号零指标（积压/拒绝/死信化/驱动健康全缺） | P2 | 新发现 |
| 10 | nop-retry-nop-tcc-C8-009 | 回调链自指配置→无界记录增长+副作用无限重放 | P2 | 新发现 |
| 11 | nop-retry-nop-tcc-C8-013 | 批量/速率参数零护栏（0 值静默空转/负值退化/紧循环/无界批） | P2 | 新发现 |
| 12 | nop-retry-nop-tcc-C8-017 | 提交面失败交付三通道不对称（IRpcCall 契约缺口） | P2 | 新发现 |
| 13 | nop-retry-nop-tcc-C8-010 | 提交面无 namespace/group 容量配额 | P3 | 新发现 |
| 14 | nop-retry-nop-tcc-C8-011 | @SingleSession 会话横跨整批 join 驻留 | P3 | 新发现 |
| 15 | nop-retry-nop-tcc-C8-014 | tcc 分支级 maxRetryTimes 死配置（零执行读方） | P3 | 新发现 |
| 16 | nop-retry-nop-tcc-C8-015 | 分区解析空列表静默 fail-open（观测不对称+行号修正） | P3 | 新发现 |
| 17 | nop-retry-nop-tcc-C8-016 | retry policy 级 retryingTimeoutMs 死配置（零引擎读方，与 C8-014 同族） | P3 | 新发现 |

### 维度集外线索（确认）

- **CL-004**（tcc 过期扫描/清理零生产装配，P1 级建议）：确认；终裁（补默认驱动 vs 宿主责任契约声明）留协调方；C8-003/004/005/007 的条件面以其裁定为前提捆绑。
- **CL-006**（完成回调内同步 DB 事务占用 RPC 共享完成线程池）：确认（线程模型本仓核实：JDK HttpClient 进程级共享专用线程池，非 event-loop）；严重度终裁留 rpc/C4/C6 跨单元。
- **CL-007**（retryFromDeadLetter 零生产装配/运维闭环出口缺位）：确认；严重度留功能覆盖/协调方裁定。
