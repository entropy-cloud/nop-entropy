# C8 背压、流控与过载行为 — nop-retry / nop-tcc 第 2 轮深挖（盲审）

- **审计单元**：`nop-retry`（io.nop.retry，40 files / 4,100 行手写）+ `nop-tcc`（io.nop.tcc，42 files / 3,872 行手写）；手写口径不含 `_` 前缀生成物与 target/
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`
- **本轮输入**：
  1. 第 1 轮报告 `C8-backpressure.md`（发现 C8-001~011 + CL-004；P1×1、P2×8、P3×2）
  2. grep 基线 `_tmp/cra-baseline-nop-retry-nop-tcc.txt`（含 C8 supplement 两节）
  3. live code：nop-retry/、nop-tcc/ 全部 main 源码逐文件审读；测试代码仅用于确认行为契约
- **盲审纪律声明**：本轮仅读取了 `ai-dev/audits/2026-10/2026-10-15-1400-deep-audit-nop-retry-nop-tcc-concurrency/C8-backpressure.md`（本维度第 1 轮文件，唯一例外）；未读取同目录任何其他维度文件（C1/C3/C6/C7 及 dispatch-log）、未读取 ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件。owner doc 仅读 `docs-for-ai/03-modules/nop-retry.md` 与 `docs-for-ai/03-modules/nop-tcc.md`（第 1 轮已建立的使用契约基线）。
- **本轮目标**（对应深挖指令 ①~⑤）：
  - ① C8-001 失效面扩展 + 停摆形态统一枚举（产出「停摆/退化面统一表」）
  - ② 批量与并发参数组合边界（护栏判定）
  - ③ 多实例并发放大（认领粒度 / CAS / 租约互斥核对）
  - ④ 回调/补偿风暴流控面（扇出宽度上界判定）
  - ⑤ 阻塞调用占用面（共享线程上下文判定）

---

## 0. 本轮事前核验（反虚构预注执行 + 设施重验）

| 核验项 | 结论 | 证据 |
|---|---|---|
| 两模块内专用信号量/限流器/独立队列/Quartz 式调度器 | **不存在**。grep `semaphore\|ratelimit\|BlockingQueue\|QueueOverflowPolicy\|IPool` 于两模块 main 源码零命中 | 本轮 grep（§⑤ 记录） |
| globalWorker 池参数 | maxPoolSize=30（`nop.commons.concurrent.global-worker.maxPoolSize` 默认）、queueCapacity=10000（ThreadPoolConfig.java:25）、拒绝语义=抛 `RejectedExecutionException`（StandardThreadPoolExecutor.RejectHandler:205-209，Abort） | `GlobalExecutors.java:85-95`、`CommonConfigs.java:26-27` |
| `BindScheduledExecutor.scheduleWithFixedDelay` 再调度语义 | 手工链式：`executor.execute(() -> { command.run() 成功 → schedule(下一轮); })`；`command.run()` 抛 Throwable 或 `executor.execute` 被拒 → `future.completeExceptionally` → **无任何再调度**（BindScheduledExecutor.java:88-116） | 重验，与第 1 轮 C8-001 证据一致 |
| `FutureHelper.futureCall` | **调用线程上同步执行**（try{task.call()}catch(Throwable)→reject）——tcc 全部 store「async」方法实为同步 DB 操作包装 | `FutureHelper.java:122-129` |
| `IOrmEntityDao.tryUpdateManyWithVersionCheck` | **行级**乐观锁 CAS：逐实体 update，version 冲突实体静默跳过（`orm_disableVersionVersionCheckError(true)`），返回实际更新成功的实体 | `IOrmEntityDao.java:37-45` |
| CL-004（tcc 恢复驱动/清理无生产装配） | **本轮复核仍成立**：`checkExpiredTransactions`/`cleanCompletedTransactions` 全仓库调用方仅测试（TestTccEngine*.java × 7 处），beans 装配不变 | 本轮 grep |
| `QueryBean.setLimit` | 无任何校验，limit 原样透传 SQL 层 | `QueryBean.java:213-215` |

---

## 1. 本轮新增发现

### [nop-retry-nop-tcc-C8-012]
1. **发现ID**: nop-retry-nop-tcc-C8-012
2. **严重度**: P1（判级注记：触发需策略启用立即重试 + DB 瞬时故障落在两个特定同步点，属「罕见但可具体化」；后果为确定性、永久性、部分形态全静默的全 namespace 停摆，故按校准规则判 P1 并注明）
3. **缺陷族**: F5（挂死不可见）/ C8 过载降级——**never-completing future 族**（返回的 future 与回调挂接的 future 不是同一对象，回调体同步抛异常时返回 future 永不完成）
4. **位置**: nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:336-392（executeImmediateRetry：`result` 于 ：336 创建、于 ：392 返回）、:405-419（scheduleImmediateRetry：`future` 于 ：405 创建、lambda 内才挂接完成逻辑）、:500-501（handleExecutionSuccess：updateRecord 先于 task-completed 日志）、:340-347（cb1 回调体）、:648（scanner allOf join）
5. **状态载体与读写方**: `executeImmediateRetry` 局部 `CompletableFuture result`（读方=executeRetryFromScanner 返回值→scanner 的 allOf.join；写方=cb1 回调体内部）；`scheduleImmediateRetry` 局部 `CompletableFuture future`（读方=上一跳 whenComplete；写方=**仅** delay 后执行的 lambda 体）。两个写方路径上任何同步抛出都使「写」永不发生，而异常只落在**未被持有**的中间 future 上。
6. **证据片段**:
   ```java
   // RetryEngineImpl.java:336-341 —— result 与 doExecute 链是两个不同 future
   CompletableFuture<ApiResponse<?>> result = new CompletableFuture<>();
   ...
   doExecute(record, request, cancelToken)          // ← (a) 此调用同步抛（newAttempt/saveAttempt DB 错）→ 下行不执行
           .whenComplete((response, ex) -> {        // cb1：↑抛则 cb1 从未挂接；(b) cb1 体抛（updateRecord/moveToDeadLetter/
               ...                                   //     scheduleImmediateRetry 调度错）→ 仅 CB 所挂的中间 future 异常完成，无人观察
               scheduleImmediateRetry(...)           // ← (c) 返回 :405 的 future，唯一写方是 delay 后的 lambda
                       .whenComplete(...);           //     lambda 内 executeImmediateRetry 同步抛 → whenComplete 从未挂接
           });
   return result;                                    // ← (a)(b)(c) 三种形态下 result 永不完成
   ```
   ```java
   // RetryEngineImpl.java:407-417 —— 唯一写方的同步抛出路径；schedule 返回值被丢弃
   executor.schedule(() -> {
       executeImmediateRetry(record, policy, request, cancelToken, task)   // ← 同步抛（DB 错）→ 下行 whenComplete 不执行
               .whenComplete(...);
       return null;
   }, immediateRetryIntervalMs, TimeUnit.MILLISECONDS);                    // 返回的 future（BindScheduledExecutor 内部）被丢弃
   ```
7. **并发场景或触发时序**:
   - **T1（延迟重试体同步抛 → 扫描器永久冻结）**：T1.1 策略 P 配置 `immediateRetryCount=3, immediateRetryIntervalMs=1000`，记录 R 被扫描器拾取，首提失败 → retryCount=1 → scheduleImmediateRetry 定时 1s；T1.2 延迟执行时刻 DB 抖动（连接池耗尽/故障切换——恰是过载形态）→ `doExecute`→`saveAttempt` 同步抛 RuntimeException；T1.3 异常传播出 lambda → `BindScheduledExecutor.executeOn` 捕获 → 完成**被丢弃的**调度内部 future + 一条平台级 `nop.err.thread-pool.execute-fail` 日志；T1.4 `:405 future` 永不被完成 → 上一跳 whenComplete（:347）永不触发 → 该跳 `result` 永不完成 → `executeRetryFromScanner` future 永不完成 → **`allOf(futures).join()`（:648）永久阻塞**；T1.5 scheduleWithFixedDelay 下一轮再调度以 `command.run()` 返回为前提 → 永不返回 → 扫描器永久冻结；retryingTimeoutMs 租约到期无法自愈（重拾动作由同一冻结循环执行）；恢复仅剩进程重启/手工 stop+start。
   - **T2（回调体同步抛 → 全静默冻结，零日志）**：T2.1 首提/延迟尝试完成 → cb1 执行 → `handleExecutionSuccess` → `updateRecord(record)`（:501，先于 :502 的 task-completed 日志）DB 写失败抛出；T2.2 抛出仅使 cb1 所挂接的中间 future 异常完成，该 future 非返回值、无人持有，CompletableFuture 对未观察异常不打印 → **引擎链路零日志**；T2.3 同 T1.4-T1.5，扫描器永久冻结。T2'（提交方路径）：业务调用 `executeTask` 返回的 future 永不完成 → 调用方若同步等待则请求线程悬挂，若异步则静默无下文。
8. **现状**: 无任何超时/兜底——join 无超时（第 1 轮 C8-002 已登记）、租约自愈依赖同一冻结循环、失败侧无死信路径（记录未进终态）。第 1 轮 C8-002 的挂死前提是「下游 RPC 挂死且 RPC 层无超时」（外部设施）；本条前提是引擎**自身链路**的同步 DB 异常传播结构缺陷，两弱点叠加。
9. **风险**: 过载/DB 故障期间（正是背压机制最需要工作的窗口）重试引擎从「降级」直接跳到「永久停摆」：该节点全部 PENDING/RETRYING 记录无人拾取、`nop_retry_record` 积压无界增长、业务调用方早已收到提交成功响应；T2 形态连定位入口都没有（无日志、无指标、线程 dump 只见 join 阻塞）。
10. **建议修复方向**: (1) `executeImmediateRetry` 改为「回调体整体 try-catch(Throwable)，任何同步抛出显式 `result.completeExceptionally`」——即消除「返回 future ≠ 回调挂接 future」的结构；(2) `scheduleImmediateRetry` lambda 体同法包裹；(3) `doExecute` 的前置持久化移入异步链或 try-catch 后转 failedStage；(4) scanner join 加超时（与 C8-002 修复合并）；(5) 对 `executor.schedule` 返回值挂 exceptionally 日志（当前被丢弃）。
11. **F3 失效面枚举**: 逃逸点 E-a=`doExecute` 前置持久化（newAttempt/saveAttempt，DB 异常）；E-b=cb1 回调体（updateRecord/moveToDeadLetter/triggerCallback 内 executeTask 的同步 DB 异常）；E-c=延迟任务体内上述任一点（经 scheduleImmediateRetry 放大为永不完成）；均为**同步异常逃逸**而非线程竞争，触发即确定性复现。与 C8-001 族（异常逃逸→循环静默死亡）同源但形态不同：C8-001 是「任务体抛出→future 异常完成→不再调度」（死亡有 future 可观察），本条是「future 永不完成→循环体阻塞在 join」（冻结且不可观察）。
12. **信心水平**: 机制=高（逐行核验 future 挂接拓扑与 BindScheduledExecutor 丢弃语义）；触发频率=中（需 immediateRetry 配置非默认 + DB 故障时点重合；DB 连接池耗尽在过载期是现实事件）。
13. **误报排除**: (a) `immediateRetryCount` 默认 0（NopRetryApiConstants.java:25）→ 走 executeSingleAttempt 路径，其返回值即回调挂接链，回调抛出正常异常完成 future → join 收到 CompletionException → doScan catch(Exception) 捕获 → 循环存活、记录靠租约自愈——**缺陷面仅覆盖启用立即重试的策略**；(b) 首提路径 executeTask 的同步抛直接传播给业务调用方（可观察），不在缺陷面内；(c) 正常 DB 无故障时不触发；(d) 非「平台设施自身实现」——BindScheduledExecutor 只是传播介质，缺陷载体是 RetryEngineImpl 的 future 拓扑。

**复核状态**：保留 P1；新发现；本轮亲自逐步复核 future 拓扑断裂成立——(a) 返回对象与挂接对象确实不同一：`result` 于 RetryEngineImpl.java:336 创建、:391 返回（初审记 :392 系微漂），其全部写方（:350-353/:378-381 complete/completeExceptionally）都在 cb1（:341-389）或 cb1 内挂接的 scheduleImmediateRetry `.whenComplete`（:348-354/:376-382）内；`future`（:405）唯一写方在 ：407-417 的 delay-lambda 内；(b) T1 时序逐步可信：延迟跳 `executeImmediateRetry` 内 `doExecute`（:340）的同步段 `newAttempt/saveAttempt`（:454-456）DB 抛 → 异常冲出 lambda → BindScheduledExecutor :101-104 catch(Throwable)→单条平台 ERROR→完成**被丢弃的**调度内部 future；:405 `future` 的 whenComplete（:409）从未挂接 → 上一跳 cb2（:348-354）永不触发 → 上一跳 `result` 永不完成 → allOf().join()（:648）永久阻塞 → scheduleWithFixedDelay 链（command.run() 返回后才重排，BindScheduledExecutor :100-107 实测）永停——每步均在 live code 核实；T2 零日志形态核实：cb1 体 `updateRecord`（:346/:374）或 handleExecutionSuccess `updateRecord`（:501，先于 ：502 task-completed 日志）抛出仅使 whenComplete 派生的**无人持有的中间 stage** 异常完成（JDK 语义），返回 `result` 不受影响；(c) 判级：按「罕见但可具体化可判 P1」校准规则终裁维持——前提两条件各自常规（immediateRetryCount>0 为标准策略选项非异常配置；DB 瞬时故障为过载期常态事件），合取罕见但每步可具体化；后果确定性、永久性、部分形态全静默（T2）；F3 逃逸点枚举 E-a/E-b/E-c 经复核完备（首跳 E-a 同步传播给扫描循环可观察、延迟跳 E-a/E-c 放大为永不完成、cb2 体抛（doExecute :487 saveAttempt）落在链尾被 cb1 接住不冻结——与第 3 轮 F9 结论一致）；第 3 轮家族全扫 F1-F25 复核结论「全仓仅 F6/F7 一处现实断裂拓扑」成立（F18/F19/F20 同型拓扑经亲自核验默认装配无注入源，F18 宿主可插拔 ITccExceptionChecker 默认无 bean 已实测 tcc-core-defaults.beans.xml）。

### [nop-retry-nop-tcc-C8-013]
1. **发现ID**: nop-retry-nop-tcc-C8-013
2. **严重度**: P2（边界配置缺陷 + 0 值形态全静默，可写出触发时序）
3. **缺陷族**: C8-3 批处理边界（批量参数无护栏）/ F5（0 值形态挂死不可见）
4. **位置**: nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:56-64（setScanIntervalMs/setBatchSize 无校验）、:96-101（scheduleWithFixedDelay 透传）、:128（fetchPendingRecords(batchSize,…)）；nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:157（query.setLimit(limit) 透传）；nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryBean.java:213-215（setLimit 无校验）
5. **状态载体与读写方**: `batchSize`、`scanIntervalMs` 实例字段——写方=IoC `@InjectValue`（`nop.retry.scanner.batch-size|100`、`scan-interval-ms|5000`，无范围约束）；读方=每轮扫描循环（fetch limit 与调度间隔）。
6. **证据片段**:
   ```java
   // RetryScannerImpl.java:61-64 —— 无任何范围校验
   @InjectValue("@cfg:nop.retry.scanner.batch-size|100")
   public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
   // RetryRecordStoreImpl.java:157 —— limit 原样进 SQL
   query.setLimit(limit);
   // BindScheduledExecutor.java:107 —— 下一轮以 delay 重调度，delay=scanIntervalMs 原样透传
   schedule(command, delay, delay, unit, future, timerHandle);
   ```
7. **并发场景或触发时序**:
   - **T1（batch-size=0 → 全静默永久空转）**：运维将 `nop.retry.scanner.batch-size` 误配为 0（空串/占位符解析为 0）→ 每轮 `fetchPendingRecords` 返回空 → doScan break → 扫描器保持「运行中」但**永久不处理任何记录**；仅 debug 级 `fetched-records:count=0`（生产默认 debug 关闭）→ 与「无积压」完全不可区分，`nop.retry.scanner-started` 照常打印，积压无界增长（C8-008 零指标叠加后无任何观测出口）。
   - **T2（batch-size=-1 → 每轮 SQL 错误、永久退化）**：setLimit(-1) 透传 SQL 层 → 多数方言报错（方言相关）→ 每轮 `nop.retry.scanner.scan-failed` error 日志，本轮剩余步骤跳过，下一轮原样重试——永久退化但不死亡（ Exception 被 doScan 外层 catch）。
   - **T3（scan-interval-ms<0 → 零间隔紧循环）**：scheduleWithFixedDelay 的 delay 透传为负 → BindScheduledExecutor 链式 `timer.schedule(task, 负delay)` 立即到期 → **上一轮 command.run() 返回后零间隔重扫** → 扫描退化为 busy-loop 连续 drain，DB 按 fetch+lock 满频敲击（正常该间隔承担的「让租约过期记录浮出」节奏消失）。
   - **T4（batch-size 巨值 → 无界批）**：batch-size=10⁶ → 单次 fetch 物化百万级实体（limit 透传无上限）+ 批内并发=批大小（allOf join 无批内并发上限）→ 与 C8-003（清理端点无界）同族、与 C8-002（join 冻结面）叠加。
8. **现状**: setter 零校验、启动期零 fail-fast；QueryBean.setLimit 同样不校验；owner doc（nop-retry.md）未声明 batch-size/scan-interval-ms 的合法边界。本轮指令②要求「owner doc 明文声明且带 fail-fast 守卫的边界不算缺陷」——两者皆无，故立案。
9. **风险**: 配置错误以「引擎静默失效/退化/DB 满频」形式落地且全部不可通过正常日志定位（T1 完全静默）；与 C8-001/002/012 叠加时积压不可恢复。
10. **建议修复方向**: setter 内校验（batch-size∈[1, 上限如 1000]、scan-interval-ms≥100）并在越界时抛异常 fail-fast（IoC 装配期即失败）；owner doc 补边界声明。
11. **F3 失效面枚举**: 参数面×4（0/负 batch、负 interval、巨值 batch）；每面触发形态=配置一次、效果持续整个进程生命周期（无自愈）；未与 C8-001 重叠（非异常逃逸族），与 C8-003 同族（无界批量端）但端点不同。
12. **信心水平**: 高（全部为透传路径的静态事实，无行为推测）。
13. **误报排除**: (a) 默认值 100/5000 本身无缺陷——立案对象是「无护栏」而非默认值；(b) scan-interval-ms 巨值/0 等无害形态不立案；(c) tcc 侧 pageSize=100 为硬编码常量（TccEngine.java:316），不在此面。

**复核状态**：保留 P2；新发现；setter 零校验（RetryScannerImpl :56-64 实测）、`query.setLimit(limit)` 透传（RetryRecordStoreImpl :157、QueryBean.setLimit :213-215 实测无校验）逐点核实成立；「owner doc 明文声明 + fail-fast 守卫」双条件在全部参数上均不成立经复核维持；T1（=0 静默空转）/T2（负值 SQL 退化）/T3（负 interval 紧循环）形态经调度链路核对可信。

### [nop-retry-nop-tcc-C8-014]
1. **发现ID**: nop-retry-nop-tcc-C8-014
2. **严重度**: P3
3. **缺陷族**: C8-3 批量/速率参数边界（声明了限速参数但零执行——死护栏）
4. **位置**: nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:110（写入 `branchRecord.setMaxRetryTimes(defaultMaxRetryTimes)`）、:301-303（唯一生效的预算过滤用全局 `NopTccRecord.retryTimes`）；nop-tcc/nop-tcc-core/src/main/java 全目录 grep `getMaxRetryTimes` 零执行性读方（仅 api bean getter）
5. **状态载体与读写方**: `nop_tcc_branch_record.max_retry_times` 列——写方=分支创建（default-max-retry-times=100 注入值）；读方=**无任何引擎代码**（grep 证实 core/dao/main 零调用；仅 Input/OutputBean 映射）。有效补偿预算实为**全局事务级** `nop_tcc_record.retryTimes < maxRetryCount`（fetchExpiredRecords SQL filter，:303）。
6. **证据片段**:
   ```java
   // TccRecordStore.java:110 —— 分支级预算写入
   branchRecord.setMaxRetryTimes(defaultMaxRetryTimes);
   // TccRecordStore.java:303 —— 唯一生效的预算闸门是全局级
   query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_retryTimes, maxRetryCount));
   ```
   本轮 grep：`getMaxRetryTimes` 在 nop-tcc main 源码的全部命中为接口声明与 bean getter，零执行性调用。
7. **并发场景或触发时序**: 非时序型缺陷。任何分支表查询都会展示 `maxRetryTimes=100` 列，暗示「每分支补偿预算」；实际补偿是否继续仅由全局事务级 retryTimes（+1/轮拾取，C8-004 拾取即计费）决定——分支行的预算列从不参与任何判定。
8. **现状**: 表级可见的护栏参数为死配置；owner doc（nop-tcc.md）提及 100 默认值时叙述的是全局扫描窗口排除，未声明存在分支级预算，也未声明其未生效。
9. **风险**: 运维按分支列调预算无效（误导性护栏）；无法按分支维度调补偿预算；放大 C8-005 的「全局耗尽→双向静默排除」的不可解释性（明明分支显示预算未耗尽却被排除）。
10. **建议修复方向**: 二选一并声明：(a) 在恢复驱动补偿判定中真正读取分支 maxRetryTimes 作为第二道闸门；(b) 移除分支级字段写入、owner doc 声明预算仅全局级。
11. **F3 失效面枚举**: 无失控面（全局预算真实生效，无速率失控）；缺陷面=护栏声明与执行的偏差（信任面错位）。
12. **信心水平**: 高（grep 全量证实零读方）。
13. **误报排除**: 非第 1 轮 C8-005 重复——C8-005 是「全局耗尽后静默排除无观测」，本条是「分支级预算参数从未被执行」；两者可同批修复但缺陷载体不同。

**复核状态**：保留 P3；新发现；本轮独立 grep 复核 `getMaxRetryTimes` 在 nop-tcc main 源码全部命中仅接口声明、api Input/OutputBean getter 与 `_gen` 实体（零执行性读方）成立；写入点 ：110 与唯一生效的全局闸门 ：303 对照核实成立；**同族关系注记（供 Phase 6 合并）**：与 C8-016（policy 级 retryingTimeoutMs 死配置）同族同型——均为「ORM/文档层声明护栏参数 + 零引擎读方」的信任面错位，载体不同（tcc 分支预算 vs retry 策略租约），判级一致（P3/P3），建议合并处置。

### [nop-retry-nop-tcc-C8-015]
1. **发现ID**: nop-retry-nop-tcc-C8-015
2. **严重度**: P3
3. **缺陷族**: C8 多实例并发放大（分区互斥 fail-open + 空实例分支零观测）
4. **位置**: nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/PartitionResolver.java:95-98（`servers == null || servers.isEmpty() → return null`，无日志）、:104-108（myRange.isEmpty → warn + return null）；消费方 nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:167-174（partitions==null → 不追加分区过滤 → 全表扫描）
5. **状态载体与读写方**: `resolvePartitions()` 返回值（含 10s TTL 缓存）——写方=NamingService 查询；读方=每轮 doScan 的 fetch 过滤条件。null 语义=「不做分区过滤」。
6. **证据片段**:
   ```java
   // PartitionResolver.java:95-98 —— naming 服务不可用/实例列表空 → 静默 fail-open
   List<ServiceInstance> servers = namingService.getInstances(svcName);
   if (servers == null || servers.isEmpty()) {
       return null;                       // 无任何日志
   }
   ```
7. **并发场景或触发时序**: T1 N 节点以 `enableCluster=true` 部署、分区互斥正常运行；T2 naming 服务抖动/实例租约过期（或 serviceName 配置错导致列表恒空）→ 各节点 `resolvePartitions` 返回 null（空列表分支零日志）；T3 全部 N 节点同时退化为**全表扫描** → 每轮 N×全量 fetch + 对同一批记录并发 `tryLockRecordsForProcess` CAS（输家实体版本冲突被丢弃）→ DB 查询/写放大 N 倍、锁竞争 waste；T4 naming 恢复或 TTL(10s) 过后自愈。
8. **现状**: fail-open（可用性优先）是设计取向；但「空列表」分支与「实例不在列表」分支（后者有 warn 日志 `nop.cluster.partition.my-instance-not-found`）观测不对称——最常见的退化形态（列表空）零日志。owner doc 声明「集群扫描按分区取数」，未声明 naming 失败时静默退化为全表扫描。
9. **风险**: 退化期 DB 负载 N 倍放大；正反馈窗口（naming 抖动恰在系统高负载时更可能发生）；CAS 保护双执行故无正确性影响——判 P3。
10. **建议修复方向**: 空列表分支加 warn 日志 + 计数指标；可选：naming 失败时沿用上次缓存分区（stale-over-open）而非立即 fail-open。
11. **F3 失效面枚举**: 外部依赖挂死（naming）→ 静默互斥失效；自愈边界=TTL 10s + naming 恢复；无永久面。
12. **信心水平**: 高（静态事实）；影响幅度依赖 naming 可用性与节点数。
13. **误报排除**: (a) `enableCluster=false` 默认部署下单节点全表扫描是声明行为，不在面内；(b) 双执行正确性由行级 CAS 保护（本轮核验 tryUpdateManyWithVersionCheck 行级语义），无重复副作用面；(c) 非 nop-job 专属问题——PartitionResolver 为共享设施，本条按 retry 消费方使用方式立案。

**复核状态**：保留 P3；新发现（平台设施 PartitionResolver 本体属 nop-cluster，按 retry 消费面立案，与第 2 轮口径一致）；空列表分支零日志（PartitionResolver :95-98 实测 return null 无 LOG）与 my-instance-not-found 分支有 warn（:105-108）的观测不对称核实成立；**行号修正（不改判级）**：消费方「partitions==null → 不追加分区过滤」的实际代码在 RetryRecordStoreImpl.fetchPendingRecords :167-174（初审误记为 RetryScannerImpl.java:167-174，该处实为 getExecutor()），分区解析入口为 RetryScannerImpl :125/:161-163；fail-open + TTL 10s（:42）+ CAS 保正确性的 P3 校准恰当。

---

## 2. 停摆/退化面统一表（深挖指令①产出，供 Phase 4 跨维度合并裁定）

两模块全部周期/循环体穷举结果：自调度构造共 3 处（RetryScannerImpl.scheduleWithFixedDelay、RetryEngineImpl.scheduleImmediateRetry 链、TccEngine.checkExpiredTransactions while，另有 fetchExpiredRecords for(100) 认领内循环与 endAsync 按轮重驱动的准循环）。grep `scheduleWithFixedDelay|scheduleAtFixedRate|while(|do {` 全量核对无遗漏。

| # | 循环体 | 调度机制 | 触发形态 | 逃逸点 → 后果 | 与第 1 轮关系 |
|---|--------|---------|---------|---------------|---------------|
| S1 | RetryScannerImpl.doScan（do-while drain） | scheduleWithFixedDelay 经 BindScheduledExecutor 手工链式再调度 | 异常逃逸 | 任务体 Throwable/Error → future 异常完成 → **永久死亡** | **C8-001 重叠**（本轮重验机制成立） |
| S2 | 同上 | 同上 | 异常逃逸 | `executor.execute` 被拒（worker 池 30/10000/Abort 满）→ future 异常完成 → **永久死亡** | **C8-001 重叠** |
| S3 | 同上 | 同上 | 异常逃逸（Exception 级） | fetch/lock 抛 Exception → 外层 catch → **本轮 pass 退化**，下一轮正常再调度；processor 内 Exception → 内层 catch → 批次跳过，循环继续 | 第 1 轮覆盖度节已判「F2 合格」，重叠（非缺陷面） |
| S4 | 同上 | 同上 | 慢滴/外部依赖挂死 | 单条 RPC 挂死且 RPC 层无超时 → allOf.join 永久阻塞 → **永久冻结**（线程占用 1 个 globalWorker） | **C8-002 重叠** |
| S5 | 同上 | 同上 | **future 永不完成（新形态）** | 立即重试链同步抛（E-a/E-b/E-c，见 C8-012）→ join 永久阻塞 → **永久冻结**（延迟重试体抛仅一条平台日志；成功回调体抛**零日志**） | **新面 → C8-012（P1）** |
| S6 | executeImmediateRetry ⇄ scheduleImmediateRetry 自调度链 | executor.schedule（timer→worker） | 异常逃逸（调度拒绝） | executor.schedule 抛 RejectedExecutionException → 链上 future 异常完成 → 记录保持 RETRYING，租约到期被重扫自愈 | 第 1 轮 B3 已声明形态，非缺陷 |
| S7 | triggerCallback → executeTask（回调链准循环） | 每跳新幂等键 `_callback(_callback…)` | 配置环 + 无界链 | callbackPolicyId 自指/互指 → 每跳新记录+重放副作用，无深度上限 | **C8-009 重叠** |
| S8 | TccEngine.checkExpiredTransactions（while pass，外部驱动一次性 pass） | 外部调度（**当前无生产装配，CL-004 复核成立**） | 异常逃逸 | `fetchExpiredRecords` 抛 RuntimeException → **本 pass 中止**、异常传播给驱动调用方（pass 语义，非进程级死亡）；Error 穿透 `catch (Exception)`（:338）→ 同为 pass 中止 | 形态新登记（pass 级、条件于 CL-004），P3 级表行，不单独立案 |
| S9 | 同上 | 同上 | 慢滴/外部依赖挂死 | 单条补偿挂死 → `get(expireGap)` 超时跳过（有 error 日志）→ **在途链存活**，下一轮重拾再扇出 → 僵尸链按轮累积 | **C8-007 重叠**；**多实例并发变体 = 新面**（见 §4-③） |
| S10 | 同上 | 同上 | 慢滴 | pass 时长上界=100×expireGap（页 100 串行逐条）+ beginTime DESC 最新优先 → 老事务饿死 | **C8-004 重叠** |
| S11 | 同上 | 同上 | 预算耗尽 | retryTimes≥100 过滤器内静默排除，无日志无指标 | **C8-005 重叠** |
| S12 | endAsync → doConfirmAsync/doCancelAsync 全分支扇出 | 按恢复轮次重驱动 | 扇出 | 分支数无上限一次性并发发出，cancelToken=null 不可取消 | **C8-007 重叠** |
| S13 | 同上 | 同上 | 异常逃逸 | `aggregateConfirmBranchStatus` 抛 NopException（状态机破坏）→ `FutureHelper.whenCompleteAsync` catch(Exception) → promise 异常完成 → 传播至驱动 `.get()` → catch(Exception) 记日志继续 | 本轮核验**受保护**，非缺陷 |
| S14 | TccRecordStore.fetchExpiredRecords for(100) 认领重试 | 同步内循环 | 竞态退化为空 | 认领冲突 → refetch（对方 bump expireTime 后不再命中）→ 空即返回；100 次上界 | 本轮核验**有界**，非缺陷 |
| S15 | TccRecordStore.removeCompletedRecords 清理 | 外部驱动单次 | 无界批 | findAllByQuery 全量物化 + 巨型 IN | **C8-003 重叠** |
| S16 | retry 死记录死信重试（executeRetryFromScanner 坏记录分支） | 租约到期重扫 | 慢滴脉冲 | moveToDeadLetter 抛 DB 异常 → 死信化失败 → 记录 RETRYING，每租约周期重复尝试（有日志、有界脉冲） | 本轮核验自限，非缺陷 |

**小结**：停摆/退化面家族收敛为 4 类——(1) 异常逃逸→永久死亡（S1/S2，C8-001）；(2) 阻塞/永不完成→永久冻结（S4/S5，C8-002 + **新 C8-012**）；(3) 慢滴/挂死→退化+僵尸累积（S8/S9/S10，C8-004/007）；(4) 配置无护栏→静默失效（**新 C8-013**）。新面仅 S5（升级为 P1 发现）与 S8（pass 级，登记不立案）。

---

## 3. 深挖指令② — 批量/并发/速率参数清单与护栏判定

| 参数 | 默认 | 来源 | 无上限/越界时护栏 | 判定 |
|---|---|---|---|---|
| nop.retry.scanner.batch-size | 100 | @InjectValue | **无**（0=静默空转、负=SQL 错、巨值=无界批） | **缺陷 → C8-013** |
| nop.retry.scanner.scan-interval-ms | 5000 | @InjectValue | **无**（负值→零间隔紧循环；JDK 不拒负 delay） | **缺陷 → C8-013** |
| nop.retry.scanner.retrying-timeout-ms | 600000 | @InjectValue | 负/小值→租约窗口收缩，双执行概率升；无 fail-fast | 边界注记（at-least-once 固有窗口，见 §4-③） |
| policy.maxRetryCount（retry） | 3（record 快照 DEFAULT_MAX_RETRY_COUNT） | policy 表 | 巨值→重试更久，预算语义仍收敛（达上限必死信） | 有界，非缺陷 |
| policy.immediateRetryCount / IntervalMs | 0 / 1000 | policy 表 | count>0 启用 S5 链；interval=0→紧重试但受 retryCount 预算有界 | 有界；其**失效形态**入 C8-012 面 |
| policy.initialIntervalMs / maxIntervalMs | 5000 / 60000 | policy 表 | 指数位移封顶 `min(retryCount-1,10)` + min(maxInterval)（RetryEngineImpl.java:605-610）→ 退避有界 | 有界，非缺陷 |
| policy.deadlineTimeoutMs | 24h | policy 表 | 超限→死信（handleDeadlineExceeded） | 有界，非缺陷 |
| nop.tcc.default-txn/branch-timeout-ms | 60000 / 10000 | @InjectValue | expireGap 语义的基准；负值→fetchExpiredRecords minTime 落在未来→**未过期记录被提前拾取计费** | 表行：驱动无生产装配（CL-004），参数由宿主传入，条件面不立案 |
| nop.tcc.default-max-retry-times | 100 | @InjectValue | 全局补偿预算，SQL filter 真实生效 | 生效（耗尽静默性已由 C8-005 立案）；**分支级同名参数为死配置 → C8-014** |
| tcc fetch pageSize | 100 硬编码 | TccEngine.java:316 | 常量不可配 | 有界，非缺陷 |
| expireGap / retentionTime | 宿主传入 | ITccEngine 参数 | 无校验（负 expireGap 见上） | 同上，条件面 |
| 组合放大（batchSize×N 实例、pageSize×串行补偿时长） | — | — | 平台设施 IBlockingQueue/QueueOverflowPolicy/IPool/IRateLimiter **零使用**（grep 证实）；唯一下限闸门=globalWorker 池容量（30/10000/Abort，其拒绝语义反成 C8-001 死因） | 平台设施未参与约束，事实登记 |

**②结论**：两模块参数面唯一的 fail-fast 是 JDK 对非法 delay 的天然行为（且负 delay 恰好不被拒）；「owner doc 明文声明 + fail-fast 守卫」的组合在全部参数上均不成立。

---

## 4. 深挖指令③④⑤ — 多实例放大 / 风暴流控 / 阻塞占用

### ③ 多实例并发放大
- **retry 认领互斥**：无 leader 选举/分布式锁/租约服务；互斥=`tryLockRecordsForProcess`（REQUIRES_NEW 事务 + `tryUpdateManyWithVersionCheck` **行级** CAS，PENDING→RETRYING + nextTriggerTime=now+retryingTimeoutMs）。认领粒度=行级；认领与执行窗口=**租约期**：attempt 时长超过 retryingTimeoutMs（默认 10min）后，RETRYING 记录（仍在 PENDING_STATUSES fetch 集内）可被他节点/本节点下一轮重新认领，**在途首执行不取消、不感知**→ 同一记录并发双执行业务 RPC。owner doc 仅声明「retryingTimeoutMs：执行中锁定超时」，未声明窗口后果；但重试引擎本质 at-least-once 且 retryCount 预算封顶总执行次数（maxRetryCount+1 后死信）→ **按已声明边界固有窗口登记，非缺陷**（合并裁定如需升级属 C2/正确性维度）。
- **retry 取数放大**：`enableCluster=false`（默认）→ 分区过滤为 null → 每节点全表扫描；=true 且 naming 健康 → 分区互斥（DEFAULT_PARTITION_COUNT=16）；naming 失败 → fail-open 全表（**新 C8-015**）。单实例约束：owner doc **未声明**，代码无守卫（分区互斥即守卫本体）。
- **tcc 认领互斥**：`fetchExpiredRecords` 版本 bump 即认领——竞争输家 refetch 得空、pass 终止（S14），同一 pass 内互斥成立；跨 pass（expireGap 后）+ 在途链存活 → **同一事务并发双补偿**：双方 beginCancelAsync 的分支 CAS 为 CANCELLING→CANCELLING 同值 CAS 恒成功、全局 CAS 同理（updateTccStatusAsync 按同 from 值匹配），状态机层面无法区分「续跑」与「并发重复」，唯一拦阻是参与方幂等（TccRecordStore.newBranchRecord 注释声明的 txnId/branchId 头幂等契约）。放大=僵尸链×节点数并发存活。判级：**C8-007 的多实例并发变体**（新面，登记于 S9 行，不另立 ID；修复方向同 C8-007 的在途去重 + 认领互斥延长）。前提注记：恢复驱动当前无生产装配（CL-004 复核成立），多实例面在装配落地前为潜在面。

### ④ 回调/补偿风暴流控面
- **tcc 补偿扇出**：`confirmAllAsync`/`cancelAllAsync` 对全分支**一次性并发发出**（waitAll=allOf），扇出宽度=分支数（`newBranchRecord` 无每事务分支上限）→ **并行扇出、宽度无界**（C8-007 重叠）；分支级 DB 记账（begin/finish CAS）经 `FutureHelper.futureCall` 在驱动线程**串行**同步执行（每分支 2 次 REQUIRES_NEW），RPC 才是真并发——大事务补偿的驱动线程串行段=2N 次 DB 事务，`get(expireGap)` 超时只放弃等待、N 条在途链存活（S9）。补偿失败→再入扫描→再补偿的乘法放大速率护栏=`expireGap` 轮间隔 + 全局 retryTimes 预算（100）→ 预算耗尽面已由 C8-004/005 立案，无新增无护栏乘法面。
- **retry 回调链路**：`triggerCallback→executeTask` 每记录单条回调任务，**串行链**（无扇出）；风暴面=C8-009（自指链）+ C8-010（提交无配额）重叠，无新增面。回调任务完成再次触发回调的结构面即 C8-009 本体。
- **立即重试批内并发**：批内 100 条的 immediate-retry 链并发执行、join 等齐——批内并发=批大小，第 1 轮 B2 已登记，重叠。

### ⑤ 阻塞调用占用面（grep：`syncGet|waitResult|Thread.sleep|synchronized|.join()|.get(`，noise 过滤后全量人工核对）

| 阻塞点 | 调用方上下文 | 判定 |
|---|---|---|
| RetryEngineImpl.java:648 `allOf(futures).join()` | globalWorker 扫描线程（共享池 30 线程） | **C8-002 已立案**（冻结占用 1/30 共享线程），不重复立案 |
| RetryEngineImpl doExecute/scheduleImmediateRetry 的同步 DB 段（newAttempt/saveAttempt/updateRecord） | 扫描 worker 线程 / 首提请求线程 / 延迟重试 worker 线程 | 短同步 DB 段，非共享线程长占；其**失败形态**即 C8-012（本轮立案） |
| TccEngine.java:331 `.get(expireGap)` | 外部驱动线程（当前无生产装配） | 条件表行：若宿主装配到请求线程/globalWorker，则阻塞=逐条×expireGap 串行（单 pass 上界 100×expireGap）；随 CL-004 裁定一并处理 |
| TccEngine.java:311 `syncGet`（runBranchTransaction）+ ITccEngine:59/63、ITccTransaction:40/44 sync 默认方法 | 同步 API 面，仓库内生产调用方为零（拦截器全走 Async 变体） | by-design 同步语义，非缺陷 |
| RetryScannerImpl.java:90/105 `synchronized`（start/stopScanning） | 生命周期守卫 | 第 1 轮已判非流控，不重复立案 |
| tcc `finishXxxAsync`/retry `doExecute` 完成回调内的同步 REQUIRES_NEW DB 事务 | **invokeAsync 完成回调线程**（线程模型属 nop-rpc 平台设施） | **维度集外线索 CL-006**（见下） |

---

## 5. 维度集外线索

### [维度集外线索-CL-006]（维度集外线索，不立案展开）
1. **发现ID**: nop-retry-nop-tcc-CL-006
2. **形态归属**: C5/C6（资源占用与线程模型）/ rpc 维度；条件性，依赖平台设施线程模型
3. **位置**: nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccBranchTransaction.java:107-122、141-164（finishConfirmAsync/finishCancelAsync）→ nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:222-283（updateTccBranchStatusAsync，FutureHelper.futureCall=**同步** DB 事务）；nop-retry/.../RetryEngineImpl.java:463-488（doExecute 的 whenComplete 内 saveAttempt）
4. **证据片段**: `FutureHelper.futureCall` 实现为调用线程同步执行（FutureHelper.java:122-129）；上述方法全部挂在 `invokeAsync(...).whenComplete(...)` 链上——**DB 写事务在与 RPC 完成回调相同的线程上同步执行**。
5. **线索描述**: 若 IRpcServiceInvoker 实现（nop-rpc/nop-cluster，平台设施，超出本单元边界）在共享 IO/event-loop 线程上执行完成回调，则每笔补偿/重试完成的 REQUIRES_NEW DB 事务直接占用平台 RPC 线程——DB 抖动经此前传为 RPC 线程池停顿，耦合全部同进程流量。属于「引擎把同步 DB 工作放进回调链」的使用方式选择，但后果载体是平台线程模型，需 rpc 维度核实 invokeAsync 完成线程后裁定。
6. **严重度**: 未定级（条件面，供 rpc/资源维度合并裁定）。
7. **处置建议**: rpc 维度确认完成回调线程；若为共享 IO 线程，则 finishXxx/saveAttempt 应转移至 worker 池执行（如 thenCompleteAsync 指定 executor）。

**复核状态**：**确认**（线程模型已本仓核实，终裁严重度留跨单元裁定）；形态真实——finishConfirmAsync/finishCancelAsync（TccBranchTransaction :107-122/:141-164）→ updateTccBranchStatusAsync（TccRecordStore :220-283，@Transactional(REQUIRES_NEW) + FutureHelper.futureCall 调用线程同步执行，实测 catch(Throwable)）全部挂在 invokeAsync 完成回调上核实成立；线程模型核实结论：默认装配链 ClusterRpcServiceInvoker（rpc-cluster-defaults.beans.xml primary 别名 nopRpcServiceInvoker）→ HttpRpcService.callAsync（.thenApply 同步变换）→ JdkHttpClient（http-client-jdk.beans.xml `nopRawHttpClient` ioc:default）`sendAsync` 且 builder.executor() 恒挂专用 DefaultThreadPoolExecutor（JdkHttpClient :108-112）——完成回调运行在 **JDK HttpClient 的进程级共享专用线程池**（单例 nopRawHttpClient 全进程共用），非 NIO event-loop，但属全进程 RPC 响应处理共享面：回调内同步 DB 事务抖动将阻塞同客户端全部响应完成。与本维度停摆/积压发现不重复、有独立行动意义（DB 工作移出完成回调线程）；归属 rpc/C4/C6 维度裁定。

---

## 6. 覆盖度与残留边界

**本轮已覆盖**：
1. ① 循环体全量穷举（grep 证明 3 个自调度构造 + 2 个内循环无遗漏），统一表 S1-S16 含形态与重叠标注。
2. ② 参数面全量登记（@InjectValue 10 处 + policy 字段 9 项 + 硬编码常量），护栏判定逐条给出；平台设施约束零使用为 grep 证实的事实。
3. ③ 认领机制落到 `tryUpdateManyWithVersionCheck` 行级语义与 CAS 查询构造（同值 CAS 无法区分续跑/重复为关键新证据）。
4. ④ 两条风暴链路（tcc 补偿扇出、retry 回调链）均判定串行/并行属性与护栏。
5. ⑤ 阻塞点全量 grep + 上下文归类，共享线程上下文仅 :648 join（已立案）与条件性驱动线程/回调线程（表行 + CL-006）。
6. 反虚构预注执行：零流控设施证实；`FutureHelper.whenCompleteAsync` 仅 catch(Exception)（FutureHelper.java:367-371）已核查——当前 tcc handler 无 Error 逃逸路径（S13 受保护），登记为轮 3 扫描点。

**残留边界**：
- `invokeAsync` 完成线程模型（CL-006 裁定前提）、RPC 层超时保证（C8-002 前提）属平台设施。
- 驱动装配缺位（CL-004）留协调方裁定；③/⑤ 的 tcc 条件面以其裁定为前提。
- retry 扫描器生产启动链（IoC 容器对 LifeCycleSupport bean 的 start 时机）未逐层展开——本轮发现均以「扫描器已启动」为前提，与第 1 轮口径一致。
- biz 层为空壳 CrudBizModel 派生（本轮复读证实），批量 mutations 上限属 nop-biz 平台设施。

## 7. 第 3 轮深挖方向与终止信号

**本轮新增：P1×1（C8-012）、P2×1（C8-013）、P3×2（C8-014/015），出现新流控失效面（never-completing future 族）→ 触发深挖继续规则。**

建议第 3 轮方向：
1. **never-completing-future 族模式化全扫**：以「返回的 future ≠ 回调挂接的 future」为模式系统扫两模块全部链式构造（本轮人工钉死 executeImmediateRetry 1 处；`FutureHelper.whenCompleteAsync` 的 catch(Exception)-only 是同一族的平台级温床，当前 tcc handler 无 Error 逃逸但新增内部调用即触雷）；同时裁定 `executeTask` 的双后置条件契约（同步抛 vs future 异常完成）对外部调用方的一致性。
2. **双时系一致性表**：deadlineTimeoutMs(24h)/retryingTimeoutMs(10min)/join 无超时/expireGap 四个时系的组合行为矩阵（本轮完成部分：租约窗口与 C8-012 冻结的正交性），供修复优先级排序。
3. **驱动装配落地的捆绑裁定**：若 CL-004 裁定补默认驱动，需捆绑裁决宿主线程（⑤ 表行）、参数校验（② 表行）、多实例互斥（③）与 C8-013 同批 fail-fast。

**终止信号：不终止**——新增 P1 且出现第 1 轮未覆盖的停摆家族；但枚举已强收敛（第 2 轮全部循环体穷举完毕，新发现集中于链式 future 拓扑而非新循环体），第 3 轮按上述方向 1 收口后可终止。
