# C8 背压·流控·过载行为 — nop-batch 深度审计（第 2 轮盲审深挖）

- **审计单元**: nop-batch 聚合器（11 个含 Java 的 jar 子模块：api/app/biz/core/dao/dsl/exp/gen/jdbc/orm/service；218 files / 15,698 行手写，src/main 口径）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **本轮输入**: 第 1 轮报告 `C8-backpressure.md`（C8-001~006）、基线 `_tmp/cra-baseline-nop-batch.txt` C8 两节、live code（逐行核实，文件清单见各条目）
- **盲审纪律声明**: 本轮为盲审深挖。除第 1 轮 C8 本维度文件外，未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何其他文件（含同目录其他维度文件与 WI 1.6 报告）。任务书中转述的 C5 轮 fetchSize 死配置口径、WI 1.6 CL-002 globalWorker 占用形态，均仅以任务书文字为限做对照复核，未回读历史审计内容。
- **审计日期**: 2026-10-16
- **本轮目标**: ① 参数护栏族 F3 全枚举（产出矩阵表）；② 过载可观测性扩展（C8-004 家族）；③ 生产消费速率失配深水区；④ consumer 侧反压；⑤ 多任务共存过载。

---

## ① 参数护栏矩阵表（F3 全枚举——本维度核心交付）

登记口径：**缺省值**（DSL xdef / Java API 两列）、**0/负值行为**（实测代码路径）、**上界守卫**（有无）、**装配点**（xdef 校验 → Builder Guard → 运行时归一三层）。判定使用：❌无护栏 / ⚠️部分护栏 / ✅有护栏。

### A. 任务级参数（BatchTaskBuilder ↔ batch.xdef）

| # | 参数 | xdef 声明 | Java 缺省 | 0/负值行为（实测） | 上界守卫 | 装配点链 | 判定 |
|---|------|-----------|-----------|--------------------|----------|----------|------|
| A1 | batchSize | `!int`（必填，无 min/无 max） | builder=100 | ≤0 → `BatchChunkProcessor.adjustBatchSize:89-90` **静默钳 1，无告警**；巨值（如 10^9）静默接受 → loader LIMIT 巨值 → 内存面 | 无下界无上界 | xdef 无 min → builder 直通 → adjustBatchSize 钳位 | ❌（C8-002 已立案 0 值面；上界面本轮矩阵新增登记） |
| A2 | concurrency | `!int=0` | 0 | `BatchTask:77` 归一 ≤0→1（文档化行为，合理） | **无** | xdef 无 min/max → builder 直通 → BatchTask 归一下界 | ⚠️下界✅/上界❌（C8-003 已立案） |
| A3 | rateLimit | `double`（可空，无 min） | 0 | ≤0 → `BatchTaskBuilder:419` `>0` 判定静默禁用限流 | 无 | xdef 无 min → builder 直通 | ❌（C8-006 已立案） |
| A4 | jitterRatio | `double`（可空） | 0 | ≤0 静默等效 0；**≥1 时 range≥batchSize → 随机钳 1**（`adjustBatchSize:86-90`，nextInt(-8,8)×range/8 可为负） | 无 | xdef 无 min → builder 直通 | ❌（C8-006 F3 ④ 同族登记） |
| A5 | startLimit | `!int=0` | 0 | `BatchTask:99-100` `context.getStartLimit()<=0` 才落 builder 值；负 startLimit 落库后等效「不再限制启动次数」语义未声明 | 无 | 任务控制参数，非流控 | ⚠️（低危，随 C8-001 修复一并对齐即可） |
| A6 | asyncProcessTimeout | `duration`（可空） | 0 | ≤0 → `BatchProcessorConsumer:88-89` 落 `CFG_BATCH_ASYNC_PROCESS_TIMEOUT`（10min） | 无上界 | **正确的运行时归一范例**（0=用缺省而非失效） | ✅ |
| A7 | skipPolicy.maxSkipCount | `long`（可空） | 0 | `BatchSkipPolicy.shouldSkip`：`maxSkipCount>0 && skipCount>=max` 才拒跳 → **≤0 = 无界跳过（全部错误静默吞掉）** | 无上界（语义即无界） | xdef 无 min | ⚠️哨兵语义=无界，且与 A8 的负值哨兵**不一致** |
| A8 | retryPolicy.maxRetryCount | `int`（可空） | RetryPolicy=2 | `RetryPolicy.isExceedRetryCount`：`max<0 → false` = **负值=无限重试**；retryDelay/maxRetryDelay setter 有 `Guard.nonNegativeInt` | 负值=无界（声明语义） | 同文件内 setRetryDelay 有 Guard、setMaxRetryCount 无——**同文件不对称** | ⚠️（哨兵与 A7 不一致：skip 用 ≤0、retry 用 <0 表示无界） |

### B. 派发参数（BatchDispatchConfig ↔ batch.xdef dispatcher）

| # | 参数 | xdef 声明 | Java 缺省 | 0/负值行为（实测） | 上界守卫 | 装配点链 | 判定 |
|---|------|-----------|-----------|--------------------|----------|----------|------|
| B1 | loadBatchSize | `!int=100`（无 min） | **BatchDispatchConfig 字段缺省 0** | 0 → `Semaphore(0)` 静默丢全量（内置 loader）或永久挂死（不守 0 的 loader）；负 → `new Semaphore(负)` IAE（fail-fast 但无配置上下文） | 无 | xdef 无 min → `buildDispatchConfig:359` 直通 → `BatchTaskBuilder:340` 直通 → 两 Provider 构造直通 → `PartitionDispatchQueue:78` 直通 | ❌（C8-001 已立案，全链 5 层无护栏） |
| B2 | fetchThreadCount | `!int=0` | 0 | 0 → 同步派发；**负值静默走同步派发分支**（`BatchTaskBuilder:342` `>0` 判定，模式切换无告警）；>0 → 每线程占兜底池 | **无上界**（C8-003 立案） | xdef 无 min/max → builder 直通 | ❌（上界）/ ⚠️（负值静默模式切换，本轮矩阵新增登记） |
| B3 | dispatcher.executor | `bean-name` | null | — | — | null → `BatchTaskBuilder:336-337` 兜底全局 cached 池（C8-003） | ❌ |
| B4 | partitionIndexField / partitionFn | 可空 | **缺省 random**：`buildDispatchConfig:377` `MathHelper.random().nextInt(0, Short.MAX_VALUE)` | — | 分区上限隐式 Short.MAX_VALUE=32767（`limitHash:386` floorMod） | 两者均未配置时静默随机分区（见 CL-039） | ❌（语义静默降级） |
| B5 | loadBatchMultiplyFactor | **无 xdef** | Provider 字段 30 | ≤0 → capacity=loadBatchSize×factor ≤0 → C8-001 同族挂死/IAE | 无 | 公共 setter **全仓零调用方**（幽灵旋钮，C8-009 立案） | ❌ |
| B6 | maxLockQueueCountPerThread | **无 xdef** | Provider 字段 100 | ≤0 → 每次 take 锁 1 个 partition 即 break（行为收敛无危害）；巨值 → break 条件失效（仅影响公平性） | 无 | 生效路径已核实：Provider setup → `queue.setMaxLockQueueCountPerThread`（Async:80 / Sync:63）→ `takeBatch:171` break 条件——**机制生效但外部零配置入口**（C8-009） | ⚠️机制✅/可配置性❌ |

### C. chunk/消费链参数

| # | 参数 | 位置 | 0/负值行为（实测） | 上界守卫 | 判定 |
|---|------|------|--------------------|----------|------|
| C1 | processor 过滤链 / consumer 聚合 | BatchTaskBuilder:396-439 | — | — | 不涉数值护栏 |
| C2 | useBatchRequestGenerator / singleMode / retryOneByOne / singleSession | boolean | — | — | 不涉 |
| C3 | transactionScope | enum | — | — | 不涉 |

### D. reader/writer 数值参数（jdbc-reader / file-reader）

| # | 参数 | xdef | 0/负值行为（实测） | 判定 |
|---|------|------|--------------------|------|
| D1 | jdbc-reader fetchSize | `int`（可空） | **DSL 路径死配置**：`JdbcBatchLoaderProvider:246` 条件 `sql.getFetchSize() != -1 && fetchSize != null`，而 `SQL` 缺省 fetchSize=-1（`SQL.java:109`）→ DSL 只配 fetchSize 时条件恒 false；`JdbcHelper.setFetchSize:229` 只应用 sql 自带值（>0 或 MIN_VALUE）→ **两处各自为政，xdef 声明的调速器在常见路径不生效**（CL-038，对照 C5 家族口径成立） | ❌ |
| D2 | jdbc-reader maxRows | `long` | `:250` `>0` 守卫，负/0=不限制（合理） | ✅ |
| D3 | jdbc-reader queryTimeout / maxFieldSize | `int` | `:241`/`:257` `>0` 守卫 | ✅ |
| D4 | jdbc-reader streaming | `!boolean=false` | false 且 fetchSize 死配置 → MySQL 驱动缺省整结果集物化（内存面，随 CL-038 登记） | ⚠️ |
| D5 | file-reader maxCountExpr | expr | -1=不限制（xdef 注释声明） | ✅ |
| D6 | file-reader 内部 maxProcessingItems=50000 / overLimitWaitMillis=60s | 非 xdef（Provider 字段，setter 无生产调用方） | 超 50000 在途 → 等 60s → `ERR_BATCH_TOO_MANY_PROCESSING_ITEMS` fail-fast（`:312`）——**本模块流控参数的正向范例**（有界+超时+带参数错误码）；setter 同为幽灵旋钮但缺省值安全 | ✅ |

### E. 工具/服务装配点（ExportDbConfig / ImportDbConfig / BizExportConfig）

| # | 参数 | xdef 缺省 | 装配点行为（实测） | 判定 |
|---|------|-----------|--------------------|------|
| E1 | export-db batchSize | `!int=0` | `ExportDbTool:277` 无守卫直传 → 钳 1（C8-002 已立案） | ❌ |
| E2 | import-db batchSize | `!int=0` | `ImportDbTool:301-302` `>0` 守卫落回 builder 缺省 100 | ✅ |
| E3 | export/import threadCount | `!int=0` | ≤1 → SyncThreadPoolExecutor（串行）；>1 → 专用池 `newExecutor(name, threadCount, Integer.MAX_VALUE)`（无界队列，第 1 轮维持低危） | ⚠️ |
| E4 | concurrencyPerTable（含表级 concurrency 覆盖） | `int`（可空） | `getConcurrency` 链：表级→全局→1；负值 → builder.concurrency(负) → BatchTask 归一 1 | ⚠️下界✅/上界❌（与 E3 构成 threadCount×concurrencyPerTable 无乘积校验，C8-003 F3 ③④） |
| E5 | export-db fetchSize | `int`（可空） | `ExportTableConfig.buildSQL:43-50` 写入 SQL 对象 → `JdbcHelper.setFetchSize` 生效——**与 D1 不同，此路径 fetchSize 是活的**（两口径并存易误推） | ✅ |
| E6 | export-db streaming | `!boolean=true` | true → `JdbcBatchLoaderProvider:243-244` 方言流式 fetchSize | ✅ |
| E7 | biz 导出 maxConcurrency | cfg `nop.biz.report.export.max-concurrency|1` | `DefaultBizEntityExporter:41-42` **只钳上界不保下界**：client 传 batchSize=0/负 → `:44-45` 上界钳不触发 → `BizExportTaskBuilder:48` 直传 builder → 钳 1（C8-002 同族经 HTTP 路径可达，见 C8-010 F3 ④） | ⚠️（本模块唯一的双参数上界钳制范例，但缺下界） |
| E8 | biz 导出 maxBatchSize | cfg `...max-batch-size|1000` | 同 E7 | ⚠️ |

**矩阵小结**：15 个数值型流控参数中，具备完整三层护栏（xdef min + Builder Guard + 运行时归一）的为 **0 个**；具备运行时归一下界的 4 个（A2/A6/E4/同步派发缺省）；具备上界钳制的仅 biz 导出 1 处（E7/E8）；幽灵旋钮（公共 setter 零调用方）3 个（B5/B6/D6）。第 1 轮 C8-001/002/003/006 分别对应 B1、A1+E1、A2+B2+B3+E3/E4、A3/A4 的立案；本轮新增立案见 C8-007~010。

---

## ② 过载可观测性扩展（C8-004 家族复核 + 延伸）

1. **BatchTaskMetricsImpl 零接线复核（维持 C8-004）**：生产 beans.xml 全查（非生成、非 dump 共 4 个：`nop-batch-dsl/.../batch-dsl.beans.xml`、`nop-batch-service/.../app-service.beans.xml`、`nop-batch-biz/.../biz-report-defaults.beans.xml`、`nop-batch-dao/.../app-batch-dao.beans.xml`）——bean 仅 nopBatchTaskManager/nopBatchTaskRunner/两个 exporter/state store，**无任何 Metrics bean**；全仓 xml grep `BatchTaskMetrics` 零命中（含生成物）。Java 侧 `setMetrics` 唯一调用点仍是测试 `TestBatchTask:35`；`BatchTaskManagerImpl.newBatchTaskContext:85-87` 与 `BatchTaskRunner.executeAsync` 均裸建 `BatchTaskContextImpl` 不设 metrics。**C8-004 判定维持，无翻案。**
2. **IBatchTaskMetrics 全方法数据源审计（新发现 C8-007）**：接口 16 个方法中，`retry(count)` 在 nop-batch 全部生产代码**零调用点**（grep `\.retry(` 仅命中接口与实现）——重试计数实际只进 `BatchTaskContext.incRetryCount`（`RetryConsumeHelper:32`），即使按 C8-004 建议接线 metrics，retryCounter 仍恒 0。另有 `getLoadItemCount()` 返回 `loadSuccessTimer.count()+loadFailureTimer.count()`——Timer 采样次数=**chunk 次数**（beginLoad 每 chunk 一次），而非装载条数；正确来源应是 `endLoad` 里按条数递增的 `loadCounter`（`BatchTaskMetricsImpl:120-123`）。即：**接线后 load 条数指标仍是错的**。
3. **积压深度读取 API 存在性（新发现 C8-008）**：`PartitionDispatchQueue` 对外仅有 `getCapacity()/isFinished()/getMaxLockQueueCountPerThread()`；积压值 `count` 与 `semaphore.availablePermits()` 均为 private，仅出现在 trace/debug 日志（`:184,192,198`）。且 queue 实例是两个 Provider `setup()` 的**局部变量**，仅被 lambda 闭包捕获，无字段持有、无注册表——**未来接 gauge 连挂载点都不存在**，修复 C8-004 时必须先补结构性出口。
4. **discard/拒绝计数可见性面**：三条丢弃路径（acquirePermits INFO、takeBatch 静默 null、removePartition 死代码）计数面与第 1 轮 C8-005 结论一致，无新路径。**池水位**：`ThreadPoolConfig.enableMetrics` 缺省 true，但 `DefaultThreadPoolExecutor` 全文零 metrics/meter/gauge 引用，`getEnableMetrics()` 全仓（main）零消费方——**声明性死标志**，cached 池/globalWorker 的活跃线程数与队列深度在任何 registry 都不存在（并入 C8-008）。

## ③ 生产消费速率失配深水区（定向核验，机制面无新立案）

1. **loadBatchMultiplyFactor=30 语义精确化**：乘的是 **dispatch 的 loadBatchSize，与 chunk batchSize 完全无关**（`AsyncFetchPartitionDispatchLoaderProvider:78` / `PartitionDispatchLoaderProvider:61` 均为 `loadBatchSize * loadBatchMultiplyFactor`）。三速率解耦：底层 loader 每页读 loadBatchSize（dispatch 模式下 ORM `dao.findNext(..., loadBatchSize)`，`OrmQueryBatchLoaderProvider` load 段核实）；队列容量=30×loadBatchSize **条记录**；chunk 每次 take ≤builder batchSize（不足即部分返回）。内存量级：在途驻留 = 30×loadBatchSize×平均记录大小 + concurrency×batchSize×记录大小（已 take 在处理的不占许可）。xdef 缺省 loadBatchSize=100 → 3000 条驻留（1KB/条≈3MB，安全）；loadBatchSize=10000 → 30 万条驻留（1KB/条≈300MB）——**30× 面积随 loadBatchSize 线性放大且无上界**（矩阵 B1/B5 联动）。
2. **fetch 线程与 chunk 线程锁竞争热点（500ms 环空转率实测口径）**：`acquirePermits` 的 `while(!finished) tryAcquire(permits,500ms)`（`:337-350`）阻塞时线程 park 在 Semaphore 内部，**2 次唤醒/秒、每次一次 CAS 尝试，CPU 代价可忽略**；takeBatch 消费者 `notEmpty.await(500ms)`（`:204,229`）同样 park，每消费者 2 次唤醒/秒，每次唤醒重入 `lock` 并全量扫描 `partitions.randomForEachEntry`——扫描代价 O(#partitions)：常规 partitionFn（数据分区数，个位数~百）可忽略；缺省随机 partitionFn 下 #partitions ≤ min(在途条数, 32767)，loadBatchSize=10000+concurrency=16 的极端形状 ≈ 16×2×32767 ≈ 100 万次 entry 访问/秒——可测量但远非饱和。**Async 模式 fetchMutex 护送（convoy）**：fetch 阻塞在 acquirePermits 时持有 fetchMutex，其余 fetch 线程 park 在监视器上（CPU 可忽略）；消费者只走 `lock`，不受 fetchMutex 影响——背压传导方向正确。
3. **sync 派发模式活性证明（本轮新增，闭合「生产者即消费者」疑点）**：sync 模式下消费者自身 fetch+addBatch（fetcher!=null）。最坏形状：消费者 C1 持 fetchMutex 阻塞在 acquirePermits（许可耗尽），C2..Cn 阻塞在 fetchMutex 上。许可只被**在队未取**条目持有（addBatch 取、takeBatch 还），被认领（threadId）队列的线程消费完毕后先走 `completeBatch`（释放认领）再进下一轮 takeBatch——其 pass-1 扫描**无需 fetchMutex** 即可取走队列数据并归还许可 → C1 在 ≤500ms 内解锁。活性成立的充要前提是 capacity>0（C8-001 已立案 0 值面：factor 死旋钮若被置 0 亦复现同族挂死，见 C8-009）。**maxLockQueueCountPerThread 实际生效路径**：非死配置（Provider setup 内部接线到 queue，break 条件在 `takeBatch:171` 实际执行），但外部零配置入口、xdef 不暴露——「死配置」定性修正为**幽灵旋钮**（C8-009），与 C5 fetchSize 家族（xdef 声明但实现不生效，CL-038）机理相反：一个是配了没用，一个是能用但没入口。

## ④ consumer 侧反压（定向核验，机制面无新立案）

- **同步传导链完整**：BatchChunkProcessor.process → consumer.consume 全链同线程顺序调用（MultiBatchConsumer `:40-44` 顺序迭代、WithHistory `:35-79` 同线程 filter+consume+saveProcessed、ResourceRecordConsumerProvider 文件写阻塞 I/O）——下游慢 → chunk 线程阻塞 → loader 停读（B2 天然反压）/ dispatch 队列排水 → 许可归还 → fetch 阻塞。**全模块 consumer 侧无一个中间缓冲队列**，反压能力=同步阻塞传导，机制成立。
- **BatchProcessorConsumer.outputs 缓冲语义**：`newOutputs()` 按 chunk 新建，async 时 ConcurrentLinkedQueue / 同步时 ArrayList；容量上界=chunk 条数×每条 processor 产出数（`outputs::add` 由 processor 决定，框架不设限）——processor 每条产出无界属理论面（产出数由业务 processor 定义），按本轮规则**判 P2 以下不立案**（写不出具体过载路径数字）。async latch 超时 fail-fast（10min 缺省，A6 归一正确）复核无误。
- **新线索 CL-037**：SplitBatchConsumer.getConsumer **全方法 synchronized**（`:60-71`），含 `activeConsumers.get(tag)` 快路径——concurrency>1 且 tag 数多时，所有 chunk 线程的每轮 consume 都在同一监视器上串行过一道锁（下游 writer 本身线程安全，该锁非必需语义）。归 C9 串行化瓶颈形态。

## ⑤ 多任务共存过载（新发现 C8-010）

- **使用点分布实测**：nop-batch 对 GlobalExecutors 的引用共 6 处——`BatchTask:87`（任务兜底）、`BatchTaskBuilder:337`（dispatch 兜底）、`ExportDbTool:284`/`ImportDbTool:296`（per-table 内层）→ 均为 **cachedThreadPool（maxPoolSize=-1 无界、queueCapacity=0）**；`AbstractBizEntityService:54-55`（biz 导出：concurrency>1 → **globalWorker（有界：corePoolSize=4/maxPoolSize=cfg 30/queueCapacity=10000）**，否则 syncExecutor）；`DefaultBizEntityExporter:72`（globalTimer 延迟删临时文件）。另有 `ModelBasedBatchTaskBuilderFactory:210` 按名解析。**同模块内形成两套形态：core/exp 链无界无钳制，biz 链有界有钳制**（对照任务书转述的 WI 1.6 CL-002 globalWorker 占用形态：本模块 biz 导出的 chunk 循环是长时占用型——每个 chunk-loop 任务从启动占住池线程到任务结束）。
- **量化（cached 池无隔离）**：JVM 内同时运行的 DSL 任务各自贡献 `concurrency + fetchThreadCount` 个线程（BatchTask:143-146 concurrency 个常驻 chunk-loop + AsyncFetch fetchThreadCount 个）；Export/Import 每工具实例峰值 `threadCount × concurrencyPerTable` 个全局池线程（表级专用池只跑表任务，表内 chunk 全在 cached 池）。N 个任务共存时全局池线程数 = Σ 各任务贡献，**无任何跨任务总量护栏、无排队、无拒绝**（queueCapacity=0 直接新建线程）。
- **量化（globalWorker 有界但无隔离）**：K 个并发 biz 导出 × min(请求 concurrency, cap=默认 1) 个长时占用线程 vs 池容量 30+10000 队列；cap 提到 8 且 K=10 时 → 80 个 chunk-loop 争 30 线程，50 个排队；globalWorker 同时服务 retry-engine/job-scanner/datav/web-page-provider/leader-elector 等全仓 ≥17 个使用点（repo-wide grep 实测 18 文件）——导出洪水将**无优先级地**推迟这些平台功能的任务执行（有界降级成立，但缺隔离与公平性）。默认 cap=1 时每个导出走 syncExecutor 占 HTTP 线程——另一极端：导出时长直接转嫁请求线程。

---

## 发现条目（第 2 轮新增）

### [nop-batch-C8-007] IBatchTaskMetrics.retry() 零生产调用点：接线后重试指标恒 0；getLoadItemCount() 语义错误返回 chunk 计数

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchTaskMetrics.java:42`（`void retry(int count)`）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/RetryConsumeHelper.java:32`（`context.incRetryCount()`，唯一重试计数点）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/metrics/BatchTaskMetricsImpl.java:157-159`（getLoadItemCount 错源）、`:120-123`（endLoad 的 loadCounter 才是条数源）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/AbstractRetryBatchConsumer.java`（重试主路径，无 metrics 引用）
- **证据片段**:
  ```java
  // 全仓 grep "\.retry(" （nop-batch main）→ 仅接口声明与 BatchTaskMetricsImpl:147-149 实现，零调用点
  // RetryConsumeHelper.checkRetry:32 —— 重试只进 context 计数
  context.incRetryCount();
  // BatchTaskMetricsImpl.getLoadItemCount:157-159 —— Timer 采样次数=chunk 次数，非条数
  public long getLoadItemCount() {
      return loadSuccessTimer.count() + loadFailureTimer.count();
  }
  // 对照：endLoad:122 —— 条数计数器已存在但未被 getter 使用
  loadCounter.increment(count);
  ```
- **严重程度**: P2
- **并发场景**: 多 chunk 线程并发重试（RetryAllBatchConsumer/RetryOneByOneBatchConsumer）时，重试流量在任何指标面上不可见；过载导致的重试风暴（下游过载→消费失败→重试放大）恰好是最需要监控的信号。
- **触发时序**: T0：假设按 C8-004 建议在 BatchTaskManagerImpl.newBatchTaskContext 接线 BatchTaskMetricsImpl。T1：任务运行，消费失败触发重试——`AbstractRetryBatchConsumer.consume` catch 分支 → checkRetry → `RetryConsumeHelper.checkRetry:32` 仅 `context.incRetryCount()`，**无任何 metrics.retry 调用**。T2：任务结束，retryCounter.count()==0，与 context.getRetryItemCount()>0 矛盾——运维按指标判断「无重试」，实际重试放大正在发生。同任务中 `getLoadItemCount()` 返回值=chunk 加载次数（如 3452 次而非 345 万条），按名取数（item count）做容量对账直接错 3 个数量级。
- **现状**: 接口 16 方法中 retry 无数据源、getLoadItemCount 错源；getProcessItemCount 因 beginProcess 恰好逐条调用而碰巧正确；getSkipItemCount/getConsumeItemCount 正确。指标 API 的 4 个 getter 中 1 个错、1 个计数器（retry）成孤儿。
- **风险**: C8-004 的修复若只做「照抄 nop-task 接线」，交付的是一个**部分失真的观测面**：重试风暴（过载最常见的二级效应）不可见，装载条数对账错误——比没有指标更具误导性（数字存在但不信）。
- **建议**: (1) 在 `RetryConsumeHelper.checkRetry`（或 AbstractRetryBatchConsumer catch 分支）补 `metrics.retry(...)` 调用（metrics 从 context.getTaskContext().getMetrics() 取，null 安全短路与 SkipConsumeHelper:41-43 同型）；(2) `getLoadItemCount()` 改为 `loadCounter.count()`；(3) 本条修复应与 C8-004 绑定为一个 plan 项，避免「接线完成」的假闭环。
- **失效面枚举（F3）**: ① `RetryConsumeHelper.checkRetry`（RetryAll/RetryOneByOne 共同路径）——无 metrics 调用，命中；② `AbstractRetryBatchConsumer.consume` catch 分支——命中；③ `IBatchTaskMetrics.getLoadItemCount` 实现（BatchTaskMetricsImpl:157）——语义错，命中；④ 对照 `SkipConsumeHelper:41-43`——skipError 有调用（同文件先例证明该模式应为惯例）；⑤ 对照 `BatchProcessorConsumer:111` beginConsume(count)——consume 侧数据源完整，证明缺口非设计取舍而是遗漏。
- **信心水平**: 确定（全仓 grep 零调用点 + getter 源逐行比对）
- **误报排除**: 不属于 C8-004 重复立案——C8-004 是「实现类零接线」，本条是「接口方法零数据源 + getter 语义错」，两者独立成立且修复动作不同（前者加装配，后者加埋点+改实现）。
- **复核状态**: 已复核（独立复核）——**保留 P2**。独立 grep 复核 `.retry(` 在 nop-batch main 零调用点、RetryConsumeHelper:32 为唯一重试计数点、`BatchTaskMetricsImpl.getLoadItemCount:157-159` 错源（Timer 采样次数=chunk 次数）与 `endLoad:120-123` 的 `loadCounter.increment(count)` 正确数据源对照、SkipConsumeHelper:41-42 skipError 调用先例、BatchProcessorConsumer:110-124 consume 侧完整数据源，全部核实。微小笔误：IBatchTaskMetrics 方法实为 17 个非 16，不改判级。与 C8-004 绑定为一个 plan 项的顺序约束成立。现状：新发现。

### [nop-batch-C8-008] 积压水位/池水位结构性不可观测：PartitionDispatchQueue 无读取 API 且实例不可达；ThreadPoolConfig.enableMetrics=true 为零实现死标志

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/PartitionDispatchQueue.java:46,72,125-131`（semaphore/count private，对外仅 getCapacity/isFinished）；`AsyncFetchPartitionDispatchLoaderProvider.java:75-80` 与 `PartitionDispatchLoaderProvider.java:58-63`（queue 为 setup 局部变量）；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ThreadPoolConfig.java:31`（enableMetrics=true）；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultThreadPoolExecutor.java`（全文零 metrics 引用）
- **证据片段**:
  ```java
  // PartitionDispatchQueue —— 积压两个核心水位均为 private，仅 trace/debug 日志可见
  private final Semaphore semaphore;          // :46
  private int count;                          // :72  当前尚未被处理的记录数
  public int getCapacity() { return capacity; }   // 对外只读容量，不读水位
  // AsyncFetchPartitionDispatchLoaderProvider.setup:78 —— 实例只活在闭包里
  PartitionDispatchQueue<S> queue = new PartitionDispatchQueue<>(...);
  // ThreadPoolConfig:31 —— 声明
  private boolean enableMetrics = true;
  // DefaultThreadPoolExecutor —— grep "Metrics|meter|gauge" 零命中；getEnableMetrics 全仓（main）零消费方
  ```
- **严重程度**: P2
- **并发场景**: dispatch 模式任务积压（fetch 快于消费）、全局 cached 池线程暴涨（C8-003 场景）、globalWorker 被长任务占满（C8-010 场景）——三类过载的载体水位在运行期均无编程出口。
- **触发时序**: T0：任务运行中，dispatch 队列积压逼近 capacity（30×loadBatchSize）。T1：运维/告警系统想读积压深度——`PartitionDispatchQueue` 无 `getCount()/getAvailablePermits()`；即使想反射，**连 queue 实例都拿不到**：它是 setup() 局部变量，仅被 resultLoader/onAfterComplete/fetch-runnable 三个闭包捕获，无字段、无注册表、无 METER 挂载。T2：想退而求其次监控载体池——`GlobalExecutors` 各池从未向 GlobalMeterRegistry 注册任何 gauge，`enableMetrics` 标志无任何代码消费。T3：过载只能靠 trace 日志（生产默认关闭）或用户报障发现。
- **现状**: 与 C8-004（指标实现类零接线）叠加形成双层缺口：实现层无接线 + 载体层无出口。修复 C8-004 的最小路径（newBatchTaskContext 接线 BatchTaskMetricsImpl）**解决不了**本条——task/chunk/load Timer 均不含积压与池水位。
- **风险**: C8 维度「过载有界降级可验证」不成立：降级是否发生、积压离上限多远、池被谁占满，全部不可观测；C8-001（capacity=0 挂死）、CL-024（claim 泄漏挂死）类故障的发现时间无下界。enableMetrics 死标志还会误导运维以为池指标已开启。
- **建议**: (1) PartitionDispatchQueue 增加只读水位 API（`getCount()`/`getAvailablePermits()`/`getPartitionCount()`），Provider 将 queue 挂到 IBatchTaskContext attributes（或 IBatchTaskMetrics 扩展 backlog gauge 接口）；(2) DefaultThreadPoolExecutor 实现 enableMetrics：向 GlobalMeterRegistry 注册 executor.name 维度的 active/pool-size/queue-size gauge（Micrometer ExecutorServiceMetrics 现成封装）；(3) 若暂不实现，至少把 enableMetrics 缺省改 false 并在 javadoc 声明未实现，消除「已开启」错觉。
- **失效面枚举（F3）**: ① `PartitionDispatchQueue.count/semaphore` 无读取 API——命中；② 两个 Provider 的 queue 局部变量无出口——命中；③ `GlobalExecutors.cachedThreadPool()/globalWorker()` 无 gauge 注册——命中；④ `ThreadPoolConfig.enableMetrics` 零消费方——命中；⑤ discard/拒绝路径计数（C8-005 已立案的可审计性缺口）无挂载点——同族联动；⑥ 对照兄弟模块 `nop-task` TaskFlowMetricsImpl——同样只覆盖 task 级 Timer，平台层无积压 gauge 先例可抄，需本模块首创。
- **信心水平**: 确定（逐行核实字段可见性、实例逃逸路径与全仓零消费方）
- **误报排除**: 不与 C8-004 重复——本条立案对象是「载体水位无编程出口 + 死标志」，即使 C8-004 修复后本条仍独立成立；不以「trace 日志存在」认定可观测（生产默认关闭且非结构化指标）。
- **复核状态**: 已复核（独立复核）——**保留 P2**。逐点核实：`PartitionDispatchQueue` semaphore:46/count:72 private 且无 getCount/getAvailablePermits（对外仅 getCapacity:129/isFinished:125）、queue 为两 Provider setup 局部变量仅闭包捕获（Async:78 / Sync:61）、`ThreadPoolConfig:31` `enableMetrics = true` 与 DefaultThreadPoolExecutor 全文零 metrics/meter/gauge 引用（grep 计数=0）、getEnableMetrics 全仓 main 零消费方。与 C8-004/C8-007 的三层缺口结构与修复顺序约束（先水位 API、再接线、再绑定 retry 数据源）核实成立。现状：新发现。

### [nop-batch-C8-009] loadBatchMultiplyFactor/maxLockQueueCountPerThread 幽灵旋钮：流控核心常量公共 setter 全仓零调用方、DSL 不可配，factor≤0 复现 C8-001 同族挂死

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/AsyncFetchPartitionDispatchLoaderProvider.java:40-41,58-72`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/PartitionDispatchLoaderProvider.java:31-32,41-55`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/PartitionDispatchQueue.java:67,103-109`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:342-346`（构造 Provider 后不调任何 setter）；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:80-85`（dispatcher 不暴露两参数）
- **证据片段**:
  ```java
  // AsyncFetchPartitionDispatchLoaderProvider —— 两个流控常数
  private int loadBatchMultiplyFactor = 30;      // :40 队列容量倍数
  private int maxLockQueueCountPerThread = 100;  // :41 单次 take 最大认领分区数
  // 全仓 grep setLoadBatchMultiplyFactor/setMaxLockQueueCountPerThread（外部调用方）
  //   → 仅 Provider 自身 setup 内 queue.setXxx（Async:80 / Sync:63），无任何生产/测试外部调用
  // BatchTaskBuilder:343,346 —— 唯一构造点，new 完直接 .setup(context)，setter 不可达
  loader = new AsyncFetchPartitionDispatchLoaderProvider<>(this::buildLoader1, executor,
          fetchThreadCount, loadBatchSize, dispatchConfig.getPartitionFn()).setup(context);
  ```
- **严重程度**: P2
- **并发场景**: dispatch 模式全部任务的内存驻留量（30×loadBatchSize）与单次 take 的分区公平性（100 分区）由两个**不可配置且未在任何文档/xdef 声明**的硬编码常数决定；未来任何接线者（配置中心、DSL 属性、bean 注入）都可能置 factor≤0。
- **触发时序**: 【现状】T0：任何 DSL/Java 装配 dispatch —— factor 恒 30、maxLock 恒 100，公共 setter 无调用方（矩阵 B5/B6）。【未来接线危害链】T1：假设 DSL 增加 `loadBatchMultiplyFactor` 属性并被误配 0（xdef 若照 `!int=0` 惯例给缺省 0 则**缺省即触发**）→ capacity=loadBatchSize×0=0 → `Semaphore(0)` → fetch 每页 addBatch 永久阻塞于 acquirePermits（持 fetchMutex）+ 消费者 takeBatch 终止条件 `count<=0 && fetchCount==0 && noMoreData` 永不满足 → C8-001 同族任务挂死，取消无效。T2：maxLockQueueCountPerThread 被置 Integer.MAX_VALUE → takeBatch break 条件（`:171`）失效 → 单线程一次锁全部非空分区，其他消费者空转 500ms 环（公平性退化，无挂死）。
- **现状**: 旋钮可塑性（public setter）与不可达性（零调用方、无 DSL 入口、无文档）并存；乘法链 `loadBatchSize × factor` 与 30× 内存面积（③ 已量化：loadBatchSize=10000 → 30 万条驻留）全部隐式。
- **风险**: 当前为可维护性/可观测性缺陷而非运行时缺陷（缺省值安全）；但它是 C8-001 修复的**前置约束**——若修复方在 PartitionDispatchQueue 构造器加 `capacity>0` Guard（第 1 轮建议），同时有人接线 factor 参数，Guard 会兜住 0/负值；若不加 Guard 而先接线参数，则新增一条缺省 0 的挂死装配路径。修复顺序必须明确。
- **建议**: (1) 短期：两参数去掉 public setter 或改为 package-private + 常量语义注释（消除「可配置」错觉）；(2) 中期：若确需可配，在 `BatchDispatchConfig` 增加字段 + `BatchTaskBuilder.buildLoader` 装配期 Guard（`factor>=1`、`maxLock>=1`）+ xdef 属性声明 min 语义，并在 owner doc 登记 30× 内存面积公式；(3) 与 C8-001 的 Guard 修复绑定评审。
- **失效面枚举（F3）**: ① AsyncFetchPartitionDispatchLoaderProvider:40（factor=30 无守卫）——命中；② PartitionDispatchLoaderProvider:31（同）——命中；③ 两者 :41（maxLock=100 无守卫）——命中；④ BatchTaskBuilder:342-346 唯一构造点不透传——命中（不可达根因）；⑤ batch.xdef:80-85 dispatcher 无对应属性——命中（DSL 无入口）；⑥ 同族对照 D6（ResourceRecordLoaderProvider maxProcessingItems setter 同为幽灵旋钮但缺省安全 + fail-fast 出口）——证明「幽灵旋钮」在本模块是家族形态，仅 factor 因乘法链有挂死后果而升级立案。
- **信心水平**: 确定（全仓 grep 外部调用方为零 + 构造点逐行核实）
- **误报排除**: maxLockQueueCountPerThread 不按「死配置」立案（第 1 轮任务书要求的 C5 口径对照结论）：其内部生效路径（Provider→queue setter→takeBatch break 条件）已逐行核实**实际执行**，缺陷定性为「无外部配置入口」而非「配置不生效」——与 CL-038 的 jdbc-reader fetchSize（声明了但不生效）机理相反，两者不可混淆。
- **复核状态**: 已复核（独立复核）——**保留 P2**。全仓独立 grep：`setLoadBatchMultiplyFactor`/`setMaxLockQueueCountPerThread` 外部调用方为零（命中仅为两 Provider 自身定义 + Provider→queue 内部接线 Async:80/Sync:63）；BatchTaskBuilder:343,346 唯一构造点 new 后直接 `.setup()` 不透传；batch.xdef:80-85 dispatcher 仅暴露 fetchThreadCount/loadBatchSize/executor/partitionIndexField/partitionFn。「幽灵旋钮」定性（内部生效但无入口）与 C8-001 的修复顺序约束（Guard 先于参数化）核实成立。现状：新发现。

### [nop-batch-C8-010] 护栏双标：core/exp 链无界 cached 池零钳制，同模块 biz 导出链却有界+钳制（globalWorker 30 线程/10000 队列）——且 biz 钳制只保上界，batchSize=0 穿透复现 C8-002

- **文件**: `nop-batch/nop-batch-biz/src/main/java/io/nop/batch/biz/importexport/DefaultBizEntityExporter.java:41-50`（钳制+globalWorker）；`AbstractBizEntityService.java:40-60`（maxConcurrency/maxBatchSize 注入与 executor 选择）；`nop-batch/nop-batch-biz/src/main/java/io/nop/batch/biz/importexport/BizExportTaskBuilder.java:48`（batchSize 直传）；对照 `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:83-88`、`BatchTaskBuilder.java:336-337`、`nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java:281-284`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:71-96`、`ThreadPoolConfig.java:19-31`
- **证据片段**:
  ```java
  // DefaultBizEntityExporter —— 本模块唯一的参数钳制先例
  if (config.getConcurrency() > maxConcurrency) config.setConcurrency(maxConcurrency); // 默认 cap=1
  if (config.getBatchSize() > maxBatchSize)     config.setBatchSize(maxBatchSize);     // 默认 cap=1000
  // AbstractBizEntityService.getExecutor —— 有界池选择
  if (config.getConcurrency() > 1) return GlobalExecutors.globalWorker(); // maxPoolSize=cfg 30, queue=10000
  return GlobalExecutors.syncExecutor();
  // BizExportTaskBuilder:48 —— 上界钳不保下界：client batchSize=0 原样穿透
  builder.concurrency(config.getConcurrency()).batchSize(config.getBatchSize());
  // 对照 core：BatchTask:87 / BatchTaskBuilder:337 —— 无界兜底 + 零钳制
  return GlobalExecutors.cachedThreadPool();  // maxPoolSize=-1, queueCapacity=0
  ```
- **严重程度**: P2
- **并发场景**: ⑤ 双含量化：(a) core/exp 链 N 个任务共存 → 全局 cached 池线程数 = Σ(各任务 concurrency + fetchThreadCount) + Σ Export/Import 实例 threadCount×concurrencyPerTable，无总量护栏（C8-003 已立案单任务面，本条立案**共存面与双标**）；(b) biz 链 K 个并发导出 × min(请求 concurrency, cap) 个**长时占用**线程 vs globalWorker 30 线程 + 10000 队列——池同时服务全仓 ≥17 个使用点（repo-wide grep：retry-engine、nop-job scanner、datav×4、web×2、cluster×2、message-debezium、sys-dao、nop-ioc、nop-core、ai-shell、batch-biz 自身）。
- **触发时序**: 【双标面】T0：同样的「导出」业务，走 biz 接口（HTTP 报表导出）→ 钳制生效、有界池；走 exp 工具（export-db.xdef）→ threadCount×concurrencyPerTable 无乘积校验、cached 池无界。两条路径在同一个 jar 聚合器内，运维无法用同一心智模型预估线程占用。【biz 穿透面】T1：client 提交 exportByQuery 带 batchSize=0 → `:44` `0 > 1000` false 不钳 → `BizExportTaskBuilder:48` builder.batchSize(0) → adjustBatchSize 钳 1 → 每行一 chunk（C8-002 同款写放大：每行 2 条 INFO + 每行一次 EtlTaskStateStore 同步文件写），且这是**面向外部请求**的路径（用户可控参数直达）。
- **现状**: maxConcurrency 缺省 1、maxBatchSize 缺省 1000——防住了「大」，没防住「零/负」；core 链两者皆无。
- **风险**: (a) 平台线程容量规划失去单点依据——同一负载按入口不同差一个数量级；(b) globalWorker 被导出 chunk 长任务占满时（K×min(concurrency,cap)>30），retry 扫描/选主/页面渲染等平台功能任务进入 10000 深队列，**降级有界但无优先级**，故障表现为无关功能随机变慢；(c) biz 路径 batchSize=0 由外部请求触发，比 exp 工具（运维配置）更易命中。
- **建议**: (1) biz 钳制补下界：`if (config.getBatchSize() <= 0) config.setBatchSize(1000)`（或 builder 缺省 100 对齐），concurrency 同理归一；(2) core/exp 链照抄 biz 钳制模式：BatchTaskBuilder 增加 `@InjectValue` 风格的平台配置项 `nop.batch.max-concurrency-cap`（C8-003 建议的具体落点），Export/ImportDbTool 增加 threadCount×concurrencyPerTable 乘积上界；(3) owner doc `docs-for-ai/03-modules/nop-batch.md` 增补「两套 executor 形态对照表」（本报告 ⑤ 使用点分布可直接引用）。
- **失效面枚举（F3）**: ① `BatchTask:87` + `BatchTaskBuilder:337` + `ExportDbTool:284` + `ImportDbTool:296` 四处 cached 兜底——无钳制，命中；② `DefaultBizEntityExporter:41-45` 钳制缺下界——命中；③ `BizExportTaskBuilder:48` batchSize 直传——穿透点，命中；④ `ExportDbConfig`/`ImportDbConfig` threadCount×concurrencyPerTable 无乘积校验（xdef 均无 min/max）——命中；⑤ globalWorker 共享面 17+ 使用点无隔离——命中（机制属平台层，nop-batch 侧先守好自己的钳制）；⑥ 对照 `AbstractBizEntityService:55` syncExecutor 缺省——导出占请求线程的另一半形态，登记不立案。
- **信心水平**: 确定（双标为逐行比对事实；线程数/队列深度为 GlobalExecutors/ThreadPoolConfig 实测缺省值；「长时占用」由 BatchTask.executeChunkLoop 的常驻循环结构得出）
- **误报排除**: 不与 C8-003 重复——C8-003 立案对象是「单任务兜底池无上界 + xdef 声明失真」，本条立案对象是「模块内两套护栏标准并存 + 共享池共存挤占量化 + biz 钳制下界缺口」；globalWorker 本身有界，不按无界池口径重审。
- **复核状态**: 已复核（独立复核）——**保留 P2**。逐点核实：DefaultBizEntityExporter:41-45 两处钳制均只判 `>` 上界（batchSize=0 穿透面成立）、BizExportTaskBuilder:48 `builder.concurrency(...).batchSize(...)` 直传、AbstractBizEntityService:51-56 `concurrency>1 → globalWorker` 否则 syncExecutor、globalWorker 缺省 `maxPoolSize=30`（CommonConfigs:26-27 `global-worker.maxPoolSize|30`）+ `queueCapacity=10000`（ThreadPoolConfig:27）、core/exp 链四处 cached 兜底无钳制。biz 穿透路径（外部请求可控参数直达）与「双标」逐行比对成立。现状：新发现。

---

## 维度集外线索（不立案展开）

### [nop-batch-CL-037]（维度集外线索，不立案展开）
- **形态归属**: C9（并发高性能·串行化瓶颈）
- **严重度候选**: P3
- **一句话证据**: `SplitBatchConsumer.java:60-71` — `getConsumer` 全方法 synchronized（含 `activeConsumers.get(tag)` 快路径），concurrency>1 且 tag 多时所有 chunk 线程的每轮 consume 在同一监视器串行过锁；下游 writer 自身线程安全，快路径加锁非必需（可改 double-checked 或 ConcurrentHashMap.computeIfAbsent）。

### [nop-batch-CL-038]（维度集外线索，不立案展开）
- **形态归属**: C5（配置无效/死配置家族，流控相邻）
- **严重度候选**: P3
- **一句话证据**: `JdbcBatchLoaderProvider.java:243-247` 条件 `sql.getFetchSize() != -1 && fetchSize != null`，而 `SQL.java:109` 缺省 fetchSize=-1、`JdbcHelper.setFetchSize:229` 只应用 sql 自带值——DSL `jdbc-reader fetchSize`（batch.xdef:130）在 SQL 未自带 fetchSize 的常见路径**静默不生效**；非 streaming（xdef 缺省 false）时 MySQL 驱动按缺省行为整结果集物化，流控调速器声明与实现脱钩（与 C8-009 maxLockQueueCountPerThread「能配但没入口」互为镜像）。export-db 路径 fetchSize 经 `ExportTableConfig.buildSQL:43-50` 写入 SQL 对象，是活的——同一模块两种接法并存。

### [nop-batch-CL-039]（维度集外线索，不立案展开）
- **形态归属**: C5（配置静默降级）+ C9 相邻
- **严重度候选**: P3
- **一句话证据**: `ModelBasedBatchTaskBuilderFactory.java:376-378` — dispatcher 未配 partitionIndexField 且未配 partitionFn 时，缺省 partitionFn=`MathHelper.random().nextInt(0, Short.MAX_VALUE)`：每条记录随机分区，「同 partition 顺序处理」的 dispatch 核心承诺静默失效（顺序敏感消费者会在多线程下乱序），且 partitions map 可增长至 32767 个队列、takeBatch 每次唤醒全量扫描 O(#partitions)（③ 已量化极端形状 ≈100 万 entry 访问/秒）。至少应要求二者必配或打 WARN。

---

## 反虚构预注复核（第 2 轮终判，未翻案）

| 预注 | 本轮复核 | 终判 |
|------|----------|------|
| PartitionDispatchQueue 非 BlockingQueue（ArrayDeque+ReentrantLock+Semaphore） | 逐行重读 `PartitionDispatchQueue.java` 全文，结构一致 | **维持** |
| 许可守恒（takeBatch 取出即释放 `:179-181`）与 removePartition 零调用方无流控影响 | grep 全仓 removePartition 生产调用方仍为零（仅定义 `:267`）；守恒路径重核一致 | **维持** |
| 本模块无 IRateLimiter/ISemaphore 平台设施使用（rateLimit 为自有 DefaultRateLimiter 实现） | grep nop-batch main 无新增命中；RateLimitConsumer+DefaultRateLimiter 仍是唯一限流点 | **维持** |
| 第 1 轮 CL-025（取消不检查于 500ms 环） | 本轮在 acquirePermits/takeBatch 环再次核实：两环均只检查 finished，不检查 cancelled | **维持，不重复立案** |

## 本轮小结与终止信号判定

- **新增发现**: 4 条（C8-007~010），全部 **P2**，无 P0/P1；每条均附触发时序与 F3 失效面，无「理论风险未具体化」占位（BatchProcessorConsumer.outputs 的 processor 自定义产出上界一面按规则判 P2 以下不立案，已在 ④ 说明理由）。
- **CL 线索**: 3 条（CL-037~039）。
- **参数护栏矩阵**: 15 个数值型流控参数全枚举（A1~A8/B1~B6/D1~D6/E1~E8），三层护栏完整者为 0；矩阵可直接作为 F3 家族修复 plan 的 checklist（每行已标注对应第 1/2 轮立案号或「登记不立案」）。
- **深挖继续规则判定**: 无新增 P0/P1 → 不强制第 3 轮。**终止信号成立**：① 矩阵闭合——BatchTaskBuilder/BatchDispatchConfig/BatchChunkProcessor 及全部装配点（dsl/exp/biz）的数值参数已穷举，无未登记的流控参数；② ③④ 的机制面（许可背压、同步反压传导、fetchMutex 活性）经定向核验为健全，第 1 轮「机制本身有界降级成立」结论扩展至 sync 模式活性证明；⑤ 共存挤占已量化并由 C8-003/C8-010 双条覆盖。剩余未闭合面均属其他维度（CL-037→C9、CL-038→C5、CL-039→C5/C9、CL-024→C4/F8）。**建议本维度终止，不再启动第 3 轮。**
- **给修复方的顺序约束**（关键新增结论）：C8-001 的 Guard 修复必须**先于/同时**于 C8-009 的参数化改造（否则新增缺省 0 挂死装配路径）；C8-004 接线必须**绑定** C8-007（否则交付失真观测面）并**先落地** C8-008 的水位 API（否则积压 gauge 无挂载点）。
