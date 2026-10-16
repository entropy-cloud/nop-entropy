# C8 背压·流控·过载行为 — nop-batch 深度审计（第 1 轮盲审初审）

- **审计单元**: nop-batch 聚合器（11 个含 Java 的 jar 子模块：api/app/biz/core/dao/dsl/exp/gen/jdbc/orm/service；nop-batch-sys/meta/web/codegen 无 src/main Java），215 files / 15,698 行手写（src/main，不含 `_` 前缀生成物与 target/）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度定义（C8）**: 无界队列/并发放大/速率失控/资源争用失控；生产消费速率失配；过载可观测性；配置无护栏。目标：验证上游快于下游时系统有界降级，而非无界积压或静默丢弃。
- **盲审纪律声明**: 本轮为盲审。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件。仅阅读方法论文件（`ai-dev/skills/concurrency-resource-audit-prompt.md`）、owner doc（`docs-for-ai/03-modules/nop-batch.md`）、`_tmp/cra-baseline-nop-batch.txt` C8 两节与当前 live code。任务书中转述的 C5 轮候选（CL-007/CL-008/CL-009）仅以任务书文字为限给出复核终判，未回读历史审计内容。
- **审计日期**: 2026-10-16

## 检查范围：队列/池/流控载体清单（全量盘点）

| # | 载体 | 位置 | 类型 | 容量/上界 | 满载行为 | 判定 |
|---|------|------|------|-----------|----------|------|
| 1 | PartitionDispatchQueue | `nop-batch-core/.../loader/PartitionDispatchQueue.java` | ArrayDeque×N partition + ReentrantLock/Condition(notEmpty) + **Semaphore(capacity) 许可背压** + CountDownLatch(fetchThreadCount)。**非 BlockingQueue，无 put/take 阻塞语义**（反虚构预注核实属实） | capacity = loadBatchSize × loadBatchMultiplyFactor(固定 30，无生产 setter 调用方) | addBatch→acquirePermits 500ms tryAcquire 循环阻塞等待；finish 后丢弃并打 `nop.batch.discard-batch-when-finished` INFO 日志 | 机制本身有界降级成立（capacity>0 时）；capacity≤0 无护栏 → C8-001 |
| 2 | 同步派发变体 | `PartitionDispatchLoaderProvider.java:61` | 同上，fetchThreadCount=0，消费者线程自带 fetcher | 同上 | 同上 | C8-001 同族 |
| 3 | ResourceRecordLoaderProvider.processingItems | `ResourceRecordLoaderProvider.java:67,73,311,341` | TreeMap 行号账本（file-reader 在途上限） | maxProcessingItems=50000（缺省）+ overLimitWaitMillis=60s | 等待 60s 后 fail-fast 抛 ERR_BATCH_TOO_MANY_PROCESSING_ITEMS | **正向范例**：有界 + fail-fast + 带参数错误码——PartitionDispatchQueue 缺的正是这种 fail-fast 终态 |
| 4 | BatchProcessorConsumer async 输出缓冲 | `BatchProcessorConsumer.java:58,62,85-99` | ConcurrentLinkedQueue + CountDownLatch（asyncProcessor=true 时） | ≤ chunk 输出数（chunk 有界） | latch 超时（缺省 `nop.batch.async-process-timeout`=10min）→ cancel + NopTimeoutException | 有界 + 超时 fail-fast，OK |
| 5 | 全局兜底池 GlobalExecutors.cachedThreadPool | `nop-commons/.../GlobalExecutors.java:71-83`（maxPoolSize=-1 不限、queueCapacity=0）；使用点 `BatchTask.java:87`、`BatchTaskBuilder.java:337`（dispatch executor 兜底）、`ExportDbTool.java:284`、`ImportDbTool.java:296`（per-table 内层） | 共享无界线程池 | 线程数无上界 | 每任务新建线程 | 无并发上界护栏 + xdef 声明与实现相反 → C8-003 |
| 6 | 表级池 DefaultThreadPoolExecutor.newExecutor(name, threadCount, **Integer.MAX_VALUE**) | `ExportDbTool.java:150`、`ImportDbTool.java:232` | 无界队列线程池 | 队列 Integer.MAX_VALUE；积压面=配置表数（export-all-tables 可达数千） | 无拒绝场景（队列无界）；表任务全部立即入队 | 基线已知事实复核终判=低危维持（见「基线复核」节） |
| 7 | SyncThreadPoolExecutor.INSTANCE | `ExportDbTool.java:152`、`ImportDbTool.java:234` | caller-runs | - | - | OK |
| 8 | RateLimitConsumer → DefaultRateLimiter | `RateLimitConsumer.java:21,36-40`、`BatchTaskBuilder.java:419-420` | 平台 IRateLimiter（nop-batch 唯一限流设施使用点；基线 rate/semaphore 节核实无 IRateLimiter/ISemaphore 其他生产使用，TestRateLimitConsumer 为测试代码） | tryAcquire(permits, 20min) 固定超时 | 超时抛 ERR_BATCH_RATE_LIMIT_ACQUIRE_TIMEOUT（fail-closed，不静默放行） | 机制 OK；装配期相容性无校验 → C8-006 |
| 9 | BlockingSinkBatchConsumer → IBlockingSink.sendMulti | `BlockingSinkBatchConsumer.java:36-43` | 平台 IBlockingQueue 设施（阻塞反压） | 由 sink 实现决定 | 阻塞发送 | 正确使用平台设施，OK |
| 10 | 批任务 metrics | `metrics/BatchTaskMetricsImpl.java`（仅 `nop-batch-core/src/test/.../TestBatchTask.java:33-35` 构造） | Micrometer Timer/Counter | - | - | **零生产接线** → C8-004 |

### 生产-消费边界清单

| 边界 | 生产方 | 消费方 | 缓冲 | 上游快于下游时 |
|------|--------|--------|------|----------------|
| B1 | fetch 线程（AsyncFetch，fetchThreadCount 个，缺省在全局 cached 池） | chunk 线程（concurrency 个） | PartitionDispatchQueue，30×loadBatchSize 条 | fetch 阻塞在 acquirePermits（有界降级成立；capacity≤0 时见 C8-001） |
| B2 | loader 游标（ORM keyset / JDBC ResultSet / 文件 RecordInput） | 同 chunk 线程 processor→consumer | chunk 内列表（batchSize，adjustBatchSize 钳位≥1） | 同步调用链天然反压（consume 慢→loader 停读），无额外队列，OK |
| B3 | file-reader 读取 | chunk 完成 compact | processingItems ≤50000 | 等 60s 后 fail-fast（正向范例） |
| B4 | async processor 异步产出 | consume 统一消费 | 输出队列 ≤chunk | latch 10min 超时 fail-fast |
| B5 | 表任务提交 | 表级池 threadCount 线程 | 无界队列（积压=表数） | 无界积压（低危，见基线复核） |
| B6 | 表内 chunk 循环 | 全局 cached 池 | 无队列 | 线程数=concurrencyPerTable，无上界（C8-003） |

### 定向审计结论（任务书要求逐项覆盖）

- **(a) capacity 来源与配置边界**：见 C8-001。`loadBatchSize=0` → `Semaphore(0)` 双端环形等待/静默 0 记录；负值 → `new Semaphore(负数)` 抛 `IllegalArgumentException`（JDK 构造器语义，fail-fast 但错误面无配置上下文）；DSL 装配点（`ModelBasedBatchTaskBuilderFactory.buildDispatchConfig:356-385`）与 xdef（`batch.xdef:81` `loadBatchSize="!int=100"`）均无 min>0 校验。
- **(b) 丢弃路径数据完整性**：见 C8-005。结论：at-least-once 语义成立，丢弃数据均未派发、未计入 completedIndex，任务终态为 fail/cancel，resume 可重取；残余面是可审计性而非正确性。
- **(c) 多分区许可竞争公平性**：`Semaphore` 非公平（`new Semaphore(capacity)` 无 fair 参数）；但 AsyncFetch 模式下 fetch+addBatch 全程持 `fetchMutex`（`AsyncFetchPartitionDispatchLoaderProvider.java:124-132`），许可竞争实际被 fetchMutex 串行化，请求尺寸均等（每页 loadBatchSize），无具体化饿死链；500ms 重试粒度仅贡献延迟上界。不立案。
- **(d) removePartition 零调用方的流控连带**：`PartitionDispatchQueue.removePartition:267-281` 全仓无生产调用方（死代码本身不立案）。流控影响：许可回收完全依赖「takeBatch 取出即释放」（`:179-181`）与 finish 丢弃；实测守恒成立（takeBatch 释放数 = addBatch 获取数，count 同步增减）。若未来接线，其「释放 undelivered + 清 threadId」语义在锁内执行，与现有路径不冲突。无运行时影响，登记为观察项。
- **消费慢于生产时的积压路径**：B1 有界（许可背压）；B2 同步链天然反压；SplitBatchConsumer/WithHistoryBatchConsumer/MultiBatchConsumer 均为同线程顺序消费、无中间队列，下游反压能力=同步阻塞传导，机制成立。SplitBatchConsumer 的 activeConsumers 账本面见 CL-022。
- **fetch 线程异常退出后的消费者挂起面（任务书告知的 C5 轮 CL-007 候选）——终判：已闭合**。live code `AsyncFetchPartitionDispatchLoaderProvider.java:95-99`（takeBatch 返回 null 后二次检查 `exception` AtomicReference 并重抛）、`:133-138`（fetch 异常记录根因 + LOG.error + exitFetchThread countDown → 终止条件 `count<=0 && fetchThreadCount.getCount()==0 && noMoreData`（`PartitionDispatchQueue.java:190`）可达）。残余面（不立案）：异常到任务失败之间，消费者会把队列中已入队的 ≤30×loadBatchSize 条继续处理完才失败——失败延迟有界但不为 0，属可接受的 drain-then-fail 语义。
- **ExportDbTool 缺省 batchSize=0（C5 轮 CL-008 候选）——终判：深化立案为 C8-002**。
- **DefaultThreadPoolExecutor 队列 Integer.MAX_VALUE（C5 轮 CL-009 候选）——终判：维持低危，不立案**（积压面=表数、单元素为小对象、表数由配置枚举；无界队列使 threadCount 失去拒绝语义但不构成资源耗尽链；建议修复时一并对齐 `QueueOverflowPolicy`，不再展开）。
- **反虚构预注核实**：PartitionDispatchQueue 确为 ArrayDeque+ReentrantLock+Semaphore 组合，非 BlockingQueue；nop-batch 生产代码无其他 IRateLimiter/ISemaphore 使用（RateLimitConsumer 一处除外）；分区派发为单任务 JVM 内机制，无跨节点流控声明，未按跨节点口径立项。

---

## 发现条目

### [nop-batch-C8-001] dispatch 模式 loadBatchSize≤0 无装配护栏：静默 0 记录「成功」或永久挂死

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/PartitionDispatchQueue.java:78-83`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/AsyncFetchPartitionDispatchLoaderProvider.java:78`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/PartitionDispatchLoaderProvider.java:61`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:339-347`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchDispatchConfig.java:10`；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:81`
- **证据片段**:
  ```java
  // PartitionDispatchQueue.java:78-79 —— capacity 直通 Semaphore，无 Guard
  public PartitionDispatchQueue(int capacity, Function<T, Integer> partitionFn, int fetchThreadCount) {
      this.semaphore = new Semaphore(capacity);
  // BatchDispatchConfig.java:10 —— Java API 缺省 0
  private int loadBatchSize;
  // BatchTaskBuilder.java:340,343 —— 直通乘法
  int loadBatchSize = dispatchConfig.getLoadBatchSize();
  loader = new AsyncFetchPartitionDispatchLoaderProvider<>(..., loadBatchSize, ...);
  // AsyncFetchPartitionDispatchLoaderProvider.java:78 —— capacity = loadBatchSize * 30
  new PartitionDispatchQueue<>(loadBatchSize * loadBatchMultiplyFactor, ...)
  ```
- **严重程度**: P1
- **并发场景**: chunk 消费线程（takeBatch）与 fetch 线程（AsyncFetch executor 提交）对 Semaphore(0) 的双端等待；主线程在 executeAsync 中等待 allOf(futures)。
- **触发时序**: 【静默丢数终态——内置 reader 均命中】T0：DSL 写 `<dispatcher loadBatchSize="0">`（xdef `!int=100` 允许显式 0）或 Java API 持缺省 `BatchDispatchConfig`（loadBatchSize=0 直通）→ setup 计算 capacity=0×30=0 → `Semaphore(0)` 构造成功（合法值，无异常）。T1：chunk 线程 takeBatch → fetcher=`loader.load(0)`；OrmQueryBatchLoaderProvider:133 `dao.findNext(...,0)` → `OrmEntityDao.findPage(sql,0,0)` → LIMIT 0 空列表；JDBC loader `RecordInputImpls.defaultReadBatch` 显式注释「maxCount=0 表示一条都不读」（`RecordInputImpls.java:43-44`）→ 空页。T2：`PartitionDispatchQueue.java:223` `noMoreData=true` → 下一轮 `count<=0 && fetchCount==0 && noMoreData`（`:190`）成立 → 返回 null → resultLoader 返回 emptyList → BatchChunkProcessor STOP → 全部 future 正常完成 → saveTaskState(success)。**全表数据零处理、零告警、任务状态成功**。【挂死终态——不守 0 的自定义 loader】loader 忽略 batchSize=0 返回 N>0 条 → addBatch → acquirePermits(N)（`:337-350`）`while(!finished) tryAcquire(N,500ms)` 永不成功 → fetch 线程持 fetchMutex 环形等待；消费者 takeBatch 因 `fetchThreadCount.getCount()>0` 永不满足终止条件 → await(500ms) 死循环。`finished` 只能由任务级 onAfterComplete 触发，而任务结束又依赖这些线程返回——环形等待。`context.cancel()` 仅置标志：acquirePermits 只检查 `finished` 不检查 cancelled，takeBatch 等待环同样不检查 → **取消也无效，任务永久挂死且无超时**。
- **现状**: 容量=loadBatchSize×30 的乘法装配链上没有任何一层做 >0 校验：xdef 无 min 约束、BatchDispatchConfig 缺省 0、BatchTaskBuilder.buildLoader 直通、两个 Provider 构造直通、PartitionDispatchQueue 构造直通。
- **风险**: Java API 装配（BatchDispatchConfig 缺省值）一次即静默丢全量数据且任务报成功——比失败更危险（下游拿到空产物无感知）；DSL 显式 0 或负值路径同理（负值走 `new Semaphore(-N)` IAE，fail-fast 但错误信息无配置上下文）。挂死终态占用 chunk 线程与 fetch 线程直至进程重启。
- **建议**: 三层防线取其一即可闭环：(1) `PartitionDispatchQueue` 构造器 `Guard.checkArgument(capacity > 0, ...)`（NopException + 配置参数上下文）；(2) `BatchTaskBuilder.buildLoader:340` 对 `loadBatchSize<=0` 抛 ERR_BATCH 配置错误（对齐 ResourceRecordLoaderProvider 的 fail-fast 风格）；(3) xdef `loadBatchSize="!int=100"` 增加 min 约束语义（若 xdef 支持）或 xlib 校验。平台已有 `io.nop.commons.concurrent.semaphore.HighWatermarkSemaphore` 可作为后续水位背压增强，但本轮缺陷只需 Guard。
- **失效面枚举（F3）**: ① `AsyncFetchPartitionDispatchLoaderProvider.java:78`（异步派发）——命中；② `PartitionDispatchLoaderProvider.java:61`（同步派发）——命中；③ `BatchDispatchConfig.setLoadBatchSize`（Java API 装配）——缺省 0 直通，命中；④ `batch.xdef:81` DSL 属性——无 min 校验，命中；⑤ 同族对照 `ImportDbTool.java:301-302` 对 `config.getBatchSize()>0` 有守卫（证明同类守卫在本仓库是既定惯例，dispatch 链缺失）；⑥ `PartitionDispatchQueue` 的 `takeBatch:134` 有 `Guard.checkArgument(batchSize > 0)`（消息文本还写错为 "must be non negative"，实查 >0）——证明作者对 0 值有意识但只守了消费侧未守容量侧。
- **信心水平**: 确定（全链路代码逐行核实，含 JDK Semaphore 构造语义与 ORM/JDBC limit=0 行为）
- **误报排除**: 不属于「有界声明存在的队列」误报——容量=0 不是声明边界而是装配缺陷；也不属于「低频路径粗粒度同步」误报——这是主路径数据丢失/挂死。
- **复核状态**: 已复核（独立复核）——**保留 P1，附 ORM 子面重大事实修正**。装配链 5 层无 >0 守卫（xdef:81 / BatchDispatchConfig:10 / BatchTaskBuilder:340,343,346 / 两 Provider :78,:61 / PartitionDispatchQueue:79）、Semaphore(0) 挂死环、取消无效前提（`Cancellable.cancel:40-51` 仅触发 onCancel 回调、不触发 afterComplete→finish，对照 `ExecutionContextImpl.completeExceptionally:131-139`）逐点核实成立。**修正①**：初审「orm-reader → findPage(sql,0,0) → LIMIT 0 空列表 → 静默空任务」不成立——`LongRangeBean(0,0).isEmpty()==true`（`LongRangeBean.java:139-141`），`JdbcTemplateImpl.buildPagedSql:387-392` 与 `JdbcHelper.prepareStatement:190` 对 (0,0) 均**不加 LIMIT**（`LimitOffsetPaginationHandler:24` / `OffsetFetchPaginationHandler:24` 的 `limit>0||offset>0` 门均不放行）→ **内建 orm-reader 实际为全表单页加载 → addBatch(N_all) 对 Semaphore(0) 永久阻塞 → 挂死（附全表内存尖峰）**，后果比「静默空任务」更重，挂死面因此扩大到最常用的 orm-reader，P1 维持且更稳固。**修正②**：静默空任务面收窄至 jdbc/file/generator/list 族（`RecordInputImpls.java:43-44`「maxCount=0 表示一条都不读」核实）。**合并裁定建议**：与 C5-005 同位点，按 C5 复核既定登记以 C8-001 P1 为主处置项合并；C5 复核「标准内建 loader 对 batchSize=0 均返回空页」的校准对 orm-reader 不成立（其引证遗漏 OrmQueryBatchLoaderProvider），需在 Phase 6/7.1 合并时一并修正。现状：新发现（git log 核实 nop-batch 相关代码最后修复批次为 2026-08-24 bcac549f47，本审计后无修复提交）。

### [nop-batch-C8-002] ExportDbTool 缺省 batchSize=0 → 每行一个 chunk：吞吐塌缩 + 日志/状态写放大（与 ImportDbTool 守卫不对称，F3）

- **文件**: `nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java:277`；`nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ImportDbTool.java:301-302`；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/db/export-db.xdef:11`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/processor/BatchChunkProcessor.java:83-92`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:301,322-323`
- **证据片段**:
  ```java
  // export-db.xdef:11 —— 缺省 0（import-db.xdef:7 相同）
  threadCount="!int=0" batchSize="!int=0" concurrencyPerTable="int"
  // ExportDbTool.java:277 —— 无条件传入
  builder.batchSize(config.getBatchSize());
  // ImportDbTool.java:301-302 —— 兄弟入口有守卫
  if (config.getBatchSize() > 0)
      builder.batchSize(config.getBatchSize());
  // BatchChunkProcessor.java:89-90 —— 0 被静默钳位为 1，无告警
  if (ret <= 0)
      ret = 1;
  ```
- **严重程度**: P1
- **并发场景**: 单表导出 chunk 循环线程（concurrencyPerTable>1 时为全局 cached 池多线程）；每 chunk 驱动 listener、状态存储（EtlTaskStateStore 文件写）与日志。
- **触发时序**: T0：用户配置 export-db 且未写 batchSize（xdef 缺省 0，这是缺省装配而非罕见配置）→ ExportDbTool:277 无守卫直传 `builder.batchSize(0)`。T1：BatchChunkProcessor.adjustBatchSize(0) → 静默钳位 1（`:89-90`，无 WARN 日志）。T2：每行数据触发一个完整 chunk 生命周期——`BatchTask.processChunk` 每次 2 条 INFO 日志（`process-chunk-begin:301` / `process-chunk-end:351`，单条含 8+ 字段）+ fireChunkBegin/End + `stateStore.saveTaskState(false,null,context)`（`:322-323`，EtlTaskStateStore 同步文件状态写）+ consumer 调用。T3：100 万行表 → 100 万 chunk → ≥200 万条 INFO（按单条 300+ 字节估算仅此即数百 MB 日志）+ 100 万次同步状态文件 I/O + 100 万次 consumer 调用开销。导出结果正确但吞吐塌缩 1-3 个数量级，日志磁盘被洪水填充（C7 放大同根因）。
- **现状**: Export 与 Import 两个兄弟入口对同一缺省 0 配置的守卫不一致（F3 同族漏修形态）：Import 有 `>0` 守卫落回 builder 缺省 100，Export 无守卫落到 BatchChunkProcessor 的静默钳位。
- **风险**: 缺省装配 + 单一常见条件（忘记配 batchSize）即触发持续资源放大；大量行的表导出从分钟级退化为小时级并伴随日志/IO 洪水；钳位无告警使配置缺陷完全不可见（对照 C5 轮以来「静默兜底必须可观测」的 owner doc 既有口径，如 orm-reader-no-query WARN 先例）。
- **建议**: (1) ExportDbTool:277 复制 ImportDbTool:301 的 `if (config.getBatchSize() > 0)` 守卫（最小修复，消除不对称）；(2) BatchChunkProcessor.adjustBatchSize 的 `ret<=0→1` 钳位增加一次性 WARN 日志（对齐 `nop.batch.orm-reader-no-query` 先例），或上移为 builder 装配期 Guard；(3) 可选：export-db.xdef/import-db.xdef 的 batchSize 缺省值改为 1000 与 builder 缺省对齐。
- **失效面枚举（F3）**: ① `ExportDbTool.java:277` 无守卫——命中；② `ImportDbTool.java:301` 有守卫——已修兄弟；③ `BatchChunkProcessor.adjustBatchSize:89-90` 静默钳位（所有 batchSize≤0 的装配入口共同终点）——命中；④ `export-db.xdef:11` 与 `import-db.xdef:7` 缺省值同为 0——两处 schema 均未消除缺省 0；⑤ DSL 路径 `BatchTaskBuilder.batchSize` 缺省 100 + `batch.xdef:9` `batchSize="!int"` 必填——DSL 主路径不受影响（守卫面仅在 exp 工具）；⑥ 同族「参数静默钳位」还有 `BatchTask.java:77` `concurrency<=0→1`（该处语义合理，≤0 归一为单线程是文档化行为，不立案）。
- **信心水平**: 确定
- **误报排除**: 不属于「有界声明存在」误报——没有任何文档声明 batchSize=0 等价于逐行处理；也不是纯性能 P3——量级为默认装配下的必然放大且有资源后果，符合「负载上升必然触发」的 P1 口径。
- **复核状态**: 已复核（独立复核）——**保留 P1**。全部证据点核实：export-db.xdef:11 与 import-db.xdef:7 缺省同为 `batchSize="!int=0"`、ExportDbTool:277 无守卫直传、ImportDbTool:301-302 `>0` 守卫（不对称成立）、adjustBatchSize:89-90 静默钳位无告警、BatchTask:301/351 每 chunk 双 INFO（单条 8+ 字段）、:322-323 每 chunk saveTaskState、EtlTaskStateStore 为同步文件状态写（stateFile + saveStateFile）。P1 终裁依据：xdef 缺省装配 + 单一常见条件（忘配 batchSize）确定性触发；写放大含日志磁盘耗尽面（百万行表 → ≥200 万条 INFO，数百 MB 量级）与百万次同步状态文件 I/O；钳位静默使缺陷不可感知。与 C5-002（默认装配确定性放大判 P1 的先例）口径一致；若修复口径以「不可逆数据/内存损失」为 P1 硬门槛，本条属 P1/P2 边界案例，建议维持 P1 以保持与 C5-002 一致。现状：新发现。

### [nop-batch-C8-003] concurrency/executor 无上界护栏 + 缺省兜底全局 cached 池 + xdef 声明与实现相反（SyncExecutor vs 无界并行）

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:77,83-88,143-146`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:336-337`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:71-83`；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:2,9`；`nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java:281-284`
- **证据片段**:
  ```java
  // batch.xdef:2 —— 声明：不配 executor 用 SyncExecutor 串行
  @concurrency 同时启动多少个线程去并行处理。设置了concurrency的情况下，还需要设置executor才会
  真正并行执行，否则会使用SyncExecutor在当前线程上串行执行
  // BatchTask.java:83-88 —— 实现：兜底是全局无界 cached 池
  static Executor getExecutor(Executor executor) {
      if (executor != null) return executor;
      return GlobalExecutors.cachedThreadPool();
  }
  // GlobalExecutors.java:77-78
  config.setMaxPoolSize(-1); // 不限制线程池大小
  config.setQueueCapacity(0);
  ```
- **严重程度**: P2（含文档误导加重说明）
- **并发场景**: 全部 batch 任务的 chunk 循环（BatchTask.java:143-146 提交 `concurrency` 个常驻 chunk-loop 任务）+ AsyncFetch 的 fetchThreadCount 个 fetch 任务（BatchTaskBuilder:337 兜底同池）+ Export/Import 每表内层 chunk 循环（ExportDbTool:284 / ImportDbTool:296，与外层表级池形成 threadCount × concurrencyPerTable 乘积）。
- **触发时序**: 【线程爆炸】T0：DSL 配 `concurrency="5000"` 且不配 executor（用户按 xdef:2 声明理解「不配 executor＝串行安全」，concurrency 值被误信为无效）→ BatchTask:87 兜底全局 cached 池（maxPoolSize=-1）。T1：executeAsync 提交 5000 个长驻 chunk-loop 任务 → queueCapacity=0 无排队 → cached 池为每个任务新建线程 → 5000 个平台线程 × ~1MB 栈预留 ≈ 5GB 虚拟内存、调度压力骤增 → OOM/进程崩溃。【共享池占用放大】T0'：普通任务（concurrency=8，不配 executor）长时间跑批 → 8 个全局池线程被占用数小时（cached 池 keepalive 只回收空闲线程，运行中任务不释放）→ 平台其他使用 `GlobalExecutors.cachedThreadPool()` 的功能与所有批任务共享同一无上界池，全局线程数 = Σ(各任务 concurrency)，无跨任务总量护栏。【exp 乘积放大】ExportDbConfig threadCount=32 × concurrencyPerTable=16 → 峰值 512 个全局池线程，两参数无乘积上界校验。
- **现状**: concurrency 与 fetchThreadCount 均无上界校验（xdef `concurrency="!int=0"`、`fetchThreadCount="!int=0"`），缺省 executor 兜底为全局无界池；xdef 头注释声明的 SyncExecutor 串行行为与实现（无界并行）完全相反，声明边界失真使护栏缺失不可感知。
- **风险**: 单个错误配置即可压垮整个共享全局池进而影响进程内其他功能；文档声明反向诱导用户做出危险配置；线程爆炸难与其他故障区分（无任务级线程数指标）。
- **建议**: (1) 修正 batch.xdef:2 声明与 `docs-for-ai` 对应描述，明确「不配 executor 时 chunk 循环运行于全局 cached 池、线程数=concurrency」；(2) 装配期护栏：concurrency/fetchThreadCount 设合理上界（如 Guard ≤ CPU×N 或全局配置项 `nop.batch.max-concurrency-cap`），超界 fail-fast；(3) 中期：兜底池改为 `GlobalExecutors.globalWorker()`（有 maxPoolSize 配置）或平台 `ThreadPoolConfig` 有界池 + QueueOverflowPolicy 声明。
- **失效面枚举（F3）**: ① `BatchTask.java:87` 任务 executor 兜底——命中；② `BatchTaskBuilder.java:337` dispatch executor 兜底——命中；③ `ExportDbTool.java:284` / ④ `ImportDbTool.java:296` per-table 内层兜底——命中（与外层 `:150`/`:232` 表级池构成乘积）；⑤ `batch.xdef:2` 声明失真——命中；⑥ `docs-for-ai/03-modules/nop-batch.md:14,212`（「并发处理：concurrency + executor 线程池」「并发线程数（需配合 executor）」）沿用了同一边界失真，需随代码一并修正（owner doc 更新义务）。
- **信心水平**: 确定（声明与实现的不一致是逐行比对事实；线程爆炸链为配置错误驱动，数字为估算但量级可靠）
- **误报排除**: 不属于「无状态单例并发调用」类误报；也不以「cachedThreadPool 原语本身」立案——立案对象是「兜底选择 + 无上界护栏 + 声明失真」的复合配置面。判 P2 而非 P1：需要显式异常配置才触发，非默认装配。
- **复核状态**: 已复核（独立复核）——**保留 P2**。batch.xdef:2 声明文本（「不配 executor 会使用 SyncExecutor 在当前线程上串行执行」）与 BatchTask.getExecutor:83-88 → GlobalExecutors.cachedThreadPool（:77-78 `setMaxPoolSize(-1)` + `setQueueCapacity(0)`）的反差逐行核实；concurrency/fetchThreadCount 无上界（xdef `!int=0`）、Export/ImportDbTool per-table 兜底（:284/:296）核实。**声明失真的定性边界裁定**：batch.xdef:2 注释是随产品分发的 DSL schema 工件（用户直接可见契约），其与实现相反按**产品缺陷随修**处理，不按 owner-doc drift 豁免；`docs-for-ai/03-modules/nop-batch.md:14` 与参数表「并发线程数（需配合 executor）」行沿用同一失真，属 owner-doc 漂移，登记随修义务（F3⑥ 已含）不单独立案。线程爆炸链为显式异常配置驱动，P2 判级恰当。现状：新发现。

### [nop-batch-C8-004] 过载可观测性结构性缺失：BatchTaskMetricsImpl 零生产接线，积压/丢弃/拒绝零指标

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/metrics/BatchTaskMetricsImpl.java:21-41`（全仓唯一构造点为测试 `nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/TestBatchTask.java:33-35`）；短路点 `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:102,187,296`、`BatchChunkProcessor.java:62`、`BatchProcessorConsumer.java:55,110`；对照兄弟模块 `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:111`
- **证据片段**:
  ```java
  // BatchTask.java:102 —— metrics 恒为 null（无任何生产 setMetrics 调用）
  IBatchTaskMetrics metrics = context.getMetrics();
  Object meter = metrics == null ? null : metrics.beginTask();
  // 对照：nop-task 生产接线先例（TaskFlowManagerImpl.java:111）
  taskRt.setMetrics(new TaskFlowMetricsImpl(GlobalMeterRegistry.instance(), null, taskName, taskVersion));
  ```
- **严重程度**: P2
- **并发场景**: 全部执行路径（chunk 循环、load/process/consume、task 起止）在 metrics==null 分支下静默跳过埋点；过载场景（fetch 积压、consumer 拖慢、finish 丢弃、表级池排队）发生时无任何指标可见。
- **触发时序**: T0：任何生产入口（BatchTaskRunner.executeAsync / IBatchTaskManager / Export/ImportDbTool / DefaultBizEntityExporter）构造 BatchTaskContextImpl 后均不调用 `setMetrics`（全仓 grep 确认生产代码零调用，仅测试构造）。T1：任务运行，beginTask/endTask/beginChunk/endLoad/endProcess/endConsume 全部走 null 短路。T2：过载发生——PartitionDispatchQueue 积压（`count`/`availablePermits` 仅 trace 级日志 `:183-185,198-199`）、`nop.batch.discard-batch-when-finished` 丢弃（INFO 日志 `:344`，无计数器）、表级池无界队列积压（无 stats 输出接线）——运维侧只有日志可查，无 gauge/counter 可告警。
- **现状**: 指标实现类完整存在（task/chunk/load/process/consume 成败 Timer + retry/skip/load/consume Counter），但没有任何 beans.xml 装配或代码路径在生产环境实例化它；nop-task 同平台兄弟模块有生产接线先例，nop-batch 缺失。
- **风险**: C8 维度要求的「过载可观测」整体不成立：积压深度、丢弃量、拒绝数、池水位均不可告警；过载只能靠用户报障或日志排查发现；C8-001/002/003 的多个缺陷在发生时同样不可见（例如 discard 发生只留一条 INFO）。
- **建议**: (1) 在 `BatchTaskManagerImpl.newBatchTaskContext`（或 BatchTaskRunner）按任务接线 `new BatchTaskMetricsImpl(GlobalMeterRegistry.instance(), null, jobName, taskName)`（照抄 nop-task 先例）；(2) 为 PartitionDispatchQueue 增加积压 gauge（count/capacity）与 discard counter（挂到 IBatchTaskMetrics 或独立 METER）；(3) 丢弃与 fetch 退出日志从 INFO 升级为带计数的结构化埋点。
- **失效面枚举（F3）**: ① `BatchTask`（task 级）——无接线；② `BatchChunkProcessor`（load 级）——无接线；③ `BatchProcessorConsumer`（process/consume 级）——无接线；④ `SkipConsumeHelper.java:26`（skip 计数）——无接线；⑤ 对照 `TaskFlowManagerImpl:111`——兄弟模块已接线，证明平台设施就绪、缺口在 nop-batch 装配层；⑥ Export/ImportDbTool 自建 BatchTaskContextImpl——同样无 metrics，工具路径全盲。
- **信心水平**: 确定（全仓 grep 复核生产构造点为零）
- **误报排除**: 不是「平台基础设施自身不重审」范围——立案对象是 nop-batch 模块的装配缺失，指标实现与 registry 均为本模块/平台既有设施，属「该用未用」。
- **复核状态**: 已复核（独立复核）——**保留 P2**。全仓独立 grep 复核：`BatchTaskMetricsImpl` 生产构造零点（唯一构造点 `TestBatchTask:33`）、`setMetrics` 生产调用零点（仅接口/实现声明 + 测试 `:35`）、nop-batch 全部 beans.xml 零引用；兄弟先例 `TaskFlowManagerImpl:111,131` 生产接线核实；`BatchTask:102-103,187,296-298` / `BatchChunkProcessor:62-63,78-79` / `BatchProcessorConsumer:55,65,74-75,110,123-124` 的 null 短路面核实。与 C8-007（接口数据源缺陷）、C8-008（载体水位无出口）为三层独立缺口，三者修复需按第 2 轮小结的顺序约束绑定。现状：新发现。

### [nop-batch-C8-005] discard-batch-when-finished 丢弃路径：at-least-once 语义成立，但丢弃量不可审计 + setup 窗口丢弃无任务级可见性

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/PartitionDispatchQueue.java:332-350`（acquirePermits + 丢弃日志）、`:115-123`（finish）、`:141-142`（takeBatch finished 即 null）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/AsyncFetchPartitionDispatchLoaderProvider.java:84-86`（任务级 onAfterComplete→finish）、`:114-144`（fetch 循环与 setup 期启动）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:132-140`（setup 先于 fireTaskBegin）
- **证据片段**:
  ```java
  // PartitionDispatchQueue.java:339-345
  while (!finished) {
      if (semaphore.tryAcquire(permits, 500, TimeUnit.MILLISECONDS))
          return true;
  }
  LOG.info("nop.batch.discard-batch-when-finished:size={}", permits);
  return false;
  // AsyncFetchPartitionDispatchLoaderProvider.java:114-118 —— setup 期即启动 fetch 线程
  for (int i = 0; i < fetchThreadCount; i++) {
      executor.execute(() -> { while (!context.isCancelled() && !queue.isFinished()) { ...
  ```
- **严重程度**: P3
- **并发场景**: fetch 线程（生产）与任务终态回调（onAfterComplete→finish，任务主线程/最后一个 chunk 线程）竞争；丢弃发生在「fetch 线程仍存活 + finished=true」的窗口。
- **触发时序**: 【数据完整性审计结论，非缺陷】finish() 仅由任务级 onAfterComplete 触发（BatchTask 成功路径 `context.complete()`:199 / 失败路径 `completeExceptionally`:211，经核实 `ExecutionContextImpl.completeExceptionally:131-139` 确实触发 afterComplete 回调）。可达窗口：T0 `loaderProvider.setup(context)` 在 `BatchTask.executeAsync:132` 执行时即启动 fetch 线程；T1 若随后 `chunkProcessorProvider.setup` 或 `fireTaskBegin()`（`:135`）抛异常 → catch → onTaskComplete → completeExceptionally → finish()；T2 fetch 线程在 T1 之后仍会拉取一页 → addBatch → acquirePermits 立即判 finished → 丢弃该页（每 fetch 线程至多 1 页，有界）。【语义核验】被丢数据两类：a) 已 fetch 未入队页（acquirePermits 丢弃）；b) 队列中未派发项（takeBatch `:141` finished 即返回 null，容量 ≤30×loadBatchSize）。两类均未派发给任何 consumer、未计入 completedIndex/completeItemCount；任务终态为 fail 或 cancel → saveState 保存失败态 → at-least-once + 断点续传语义下 resume 重取，**无静默丢失**。【残余缺口】丢弃仅 INFO 单行日志、无累计计数、无指标、无任务级汇总字段（结合 C8-004 metrics 零接线），高频取消/失败场景下丢弃规模不可审计。
- **现状**: 丢弃机制设计正确（避免 fetch 线程在 finish 后永久阻塞泄漏底层连接，注释 `:332-335` 明确此动机），判级仅针对可审计性缺口。
- **风险**: 大规模取消/失败运维场景下，「任务实际处理了多少、丢了多少」无法对账；丢弃日志混在 INFO 级易被日志策略过滤。
- **建议**: (1) 丢弃累计值挂入任务上下文（如 `context.incSkipItemCount` 同型计数）并在任务终态日志输出；(2) 结合 C8-004 的 metrics 接线增加 discard counter；(3) 可选：对 setup 窗口（fireTaskBegin 失败路径）打专门 WARN。
- **失效面枚举（F3）**: ① `acquirePermits:344`（fetch 侧丢弃）——命中（INFO 无计数）；② `takeBatch:141`（队列驻留项丢弃）——静默返回 null，连日志都没有，命中且更弱；③ `removePartition:267-281`（第三条丢弃路径，含许可释放）——零调用方（死代码不立案，其丢弃有 javadoc 语义但无触发）；④ 同族对照 `ResourceRecordLoaderProvider` overLimit fail-fast——有带参数错误码，证明本模块对「不可处理数据」的正规出口是 ErrorCode，discard 路径降级为 INFO 属不一致。
- **信心水平**: 确定（语义）；可审计性缺口为设计取舍候选
- **误报排除**: 遵守任务书已声明契约——`discard-batch-when-finished` 日志本身不立案；本条立案对象是「丢弃量可审计性 + setup 窗口可见性」，且明确记录语义核验通过结论，防止复核轮误升判级。
- **复核状态**: 已复核（独立复核）——**保留 P3**。语义核验面独立重放：finish 唯一触发点 onAfterComplete（AsyncFetchProvider:84-86）、`ExecutionContextImpl.completeExceptionally:131-139` → fireAfterComplete 核实、setup 先于 fireTaskBegin（BatchTask:132-135）核实；丢弃路径 acquirePermits:344 INFO 无计数、takeBatch:141-142 静默返回 null、removePartition:267-281 零调用方（grep 复核）均与初审一致。可审计性缺口为 P3 设计取舍候选，判级恰当，维持。现状：新发现。

### [nop-batch-C8-006] batchSize×rateLimit 装配期无相容性校验：必死组合运行期才爆炸；rateLimit≤0 静默禁用

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/RateLimitConsumer.java:21,36-40`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:418-420`；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:9`（`rateLimit="double"` 无 min）
- **证据片段**:
  ```java
  // RateLimitConsumer.java:21
  static final long RATE_LIMIT_TIMEOUT = 1000 * 60 * 20L; // 20分钟
  // :36-39 —— fail-closed 正确，但 batchSize 巨值时必超时
  int permits = items.size();
  if (permits > 0 && !rateLimiter.tryAcquire(permits, RATE_LIMIT_TIMEOUT)) {
      throw new NopTimeoutException(ERR_BATCH_RATE_LIMIT_ACQUIRE_TIMEOUT)...
  // BatchTaskBuilder.java:419 —— 负值/0 静默跳过，无告警
  if (rateLimit > 0)
      consumer = new RateLimitConsumer<>(consumer, new DefaultRateLimiter(rateLimit));
  ```
- **严重程度**: P3
- **并发场景**: 单 chunk 消费线程原子获取整 chunk permits；concurrency>1 时多线程在同一 DefaultRateLimiter 上竞争令牌。
- **触发时序**: T0：DSL 配 `batchSize="100000"` + `rateLimit="10"`（每秒 10 条）→ 装配期无任何校验（xdef 两个属性独立、builder 不交叉检查）。T1：首个 chunk consume → tryAcquire(100000, 20min)。T2：10/s 供给 100000 permits 需 10000s ≫ 20min 超时 → 抛 ERR_BATCH_RATE_LIMIT_ACQUIRE_TIMEOUT → chunk 失败 → fail-fast 任务失败。该组合是静态可判定的必死配置（batchSize > rateLimit × 20min × 60 = rateLimit×1200 即必死），却在每次部署后于运行期首个 chunk 才暴露。另一面：`rateLimit="-5"` 被 `:419` 的 `>0` 判定静默吞掉——用户以为限流生效实际无限制，与「配置错误必须显式报错」的模块内既有口径（ERR_BATCH_TASK_CONSUMER_FOR_TAG_NO_TAGGER 先例）不一致。
- **现状**: 限流 fail-closed 语义正确（超时不放行），缺的是装配期交叉校验与负值告警。
- **风险**: 必死配置延迟到运行期暴露（每次浪费一个任务启动周期）；负值静默禁用造成「以为限流了」的过载误配置——下游保护被无意拆除。
- **建议**: (1) BatchTaskBuilder.buildChunkProcessor 装配期校验：`rateLimit > 0 && batchSize > rateLimit * RATE_LIMIT_TIMEOUT/1000` 时抛配置错误或 WARN（给出「batchSize 须 ≤ rateLimit×1200」提示）；(2) `rateLimit < 0` 抛 ERR 配置错误（xdef 层加 min=0 语义或 xlib 校验）。
- **失效面枚举（F3）**: ① `BatchTaskBuilder.java:419` rateLimit 静默判——命中；② `RateLimitConsumer` 超时常量硬编码 20min 无配置化——单点，无兄弟；③ xdef `rateLimit="double"` 无 min/上界——命中；④ 兄弟对照：同文件 `jitterRatio` 负值同样静默（`adjustBatchSize:85` 仅 `>0` 生效，负值等效 0）——同族面，本轮一并登记不单独立案。
- **信心水平**: 确定
- **误报排除**: RateLimitConsumer 的 fail-closed 已是正确实现（任务书提示的「超时拿不到许可不能静默放行」已落实），本条不否定机制本身，只针对装配护栏缺口，判 P3 合适。
- **复核状态**: 已复核（独立复核）——**保留 P3**。RateLimitConsumer:21（`RATE_LIMIT_TIMEOUT = 1000*60*20L`）与 :36-40 fail-closed（超时抛 ERR_BATCH_RATE_LIMIT_ACQUIRE_TIMEOUT，含按 items.size() 取许可的放大修复注释）核实；BatchTaskBuilder:419-420 `>0` 静默禁用核实；batch.xdef:12 `rateLimit="double"` 无 min 核实；必死公式数学核实：permits 条需 `permits/rateLimit` 秒，超 20min（1200s）即必死 → `batchSize > rateLimit×1200` 静态可判。装配期交叉校验缺失与负值静默禁用均为可验证事实，P3 恰当。现状：新发现。

---

## 维度集外线索（不立案展开）

### [nop-batch-CL-022]（维度集外线索，不立案展开）
- **形态归属**: C6/F8（资源生命周期·注册账本只增不删）
- **严重度候选**: P3
- **一句话证据**: `SplitBatchConsumer.java:44,60-71` — `activeConsumers` ConcurrentHashMap 按 tag 只增不删，tag 空间无界时（splitter 动态产出任意 tag）任务存续期内单调增长；生命周期以单次任务执行为界，故低危。

### [nop-batch-CL-023]（维度集外线索，不立案展开）
- **形态归属**: C9（并发高性能·串行化瓶颈）
- **严重度候选**: P3
- **一句话证据**: `JdbcBatchLoaderProvider.java:186-189`、`OrmQueryBatchLoaderProvider.java:101-105`、`ResourceRecordLoaderProvider.java:166-176` — concurrency>1 且无 dispatch 包装时所有 chunk 线程在 `synchronized(state)` 上串行执行 load 阶段，读取并行度被钳为 1（各 loader 同族形态，需量化后由 C9 立案）。

### [nop-batch-CL-024]（维度集外线索，不立案展开）
- **形态归属**: C4/F5（线程池异常终止·挂死不可见）
- **严重度候选**: P2 候选
- **一句话证据**: `PartitionDispatchQueue.java:147,168`（threadId 认领）+ `BatchTask.executeChunkLoop:226`（chunk 任务由外部 executor 执行）— chunk 线程在 takeBatch 与 completeBatch 之间被 executor 强杀/拒绝时 partition claim 永久泄漏，count>0 使其余消费者 takeBatch 永久等待且无 stall 检测/看门狗（依赖 C8-001 修复后单独评估触发面）。

### [nop-batch-CL-025]（维度集外线索，不立案展开）
- **形态归属**: C3（取消传播）
- **严重度候选**: P3
- **一句话证据**: `PartitionDispatchQueue.java:204,229,339-343` — takeBatch 的 await(500ms) 环与 acquirePermits 的 tryAcquire 环均不检查 `context.isCancelled()`，取消信号只能经数据耗尽/finish 间接传播，取消延迟上界=队列排空时间（≤30×loadBatchSize 条 + 在途 chunk）。

---

## 基线复核终判（任务书指定的三项）

| 项 | 候选 | 本轮终判 | 依据 |
|----|------|----------|------|
| fetch 线程异常退出后消费者挂起 | C5 轮 CL-007 候选 | **已闭合，不立案** | live code `AsyncFetchPartitionDispatchLoaderProvider.java:95-99` 有 exception 二次检查重抛、`:133-138` 记根因；终止条件可达（`PartitionDispatchQueue.java:190`）。残余 drain-then-fail 延迟有界，见「定向审计结论」 |
| ExportDbTool 缺省 batchSize=0 | C5 轮 CL-008 候选 | **深化立案为 C8-002（P1）** | 实测 xdef 缺省 0 + Export 无守卫 + adjustBatchSize 静默钳位 1 + 与 Import `:301` 不对称 |
| DefaultThreadPoolExecutor 队列 Integer.MAX_VALUE | C5 轮 CL-009 候选 | **维持低危，不立案** | 积压面=表数（配置枚举有界）、单元素小对象、executor 在 finally 中 destroy（`ExportDbTool.java:186-188`、`ImportDbTool.java:261-262`）；修复建议：与平台 QueueOverflowPolicy 对齐 |

## 本轮小结

- 发现：6 条（P1×2、P2×2、P3×2），全部附具体触发时序/过载路径数字与 F3 失效面枚举，无「理论风险未具体化」占位条目。
- CL 线索：4 条（CL-022 ~ CL-025）。
- 基线复核终判：3 项（1 项闭合、1 项深化立案、1 项维持低危）。
- 核心结论：PartitionDispatchQueue 的许可背压机制在 capacity>0 时是有界降级的正确设计（fetch 阻塞→消费者排水→许可回收，守恒成立）；本维度实质缺陷集中在**装配层护栏**（capacity/batchSize/concurrency 三个参数的 0 值与上界均无校验）与**过载可观测性**（metrics 零生产接线），而非队列机制本身。
- 给修复方的最小闭环路径：C8-001 = PartitionDispatchQueue 构造 Guard 一行；C8-002 = ExportDbTool 复制 Import 守卫一行 + 钳位 WARN；C8-003 = xdef 声明修正 + concurrency 上界；C8-004 = 照抄 nop-task metrics 接线先例。

---

## 维度复核结论（独立复核）

- **复核人**: C8 维度独立复核子代理（与初审代理相互独立）
- **复核日期**: 2026-10-16
- **复核范围**: 第 1 轮 C8-001~006 + CL-022~025、第 2 轮 C8-007~010 + CL-037~039 + 参数护栏矩阵 A1~A8/B1~B6/D1~D6/E1~E8；对照面含 cra-index、C5/C6 同审计复核结论、plans 2461~2466、WI 1.6 同族条目、owner doc `docs-for-ai/03-modules/nop-batch.md`、git log。

### 一、裁定汇总

| 条目 | 初审判级 | 复核裁定 | 判级变动 | 现状标注 | 核心复核结论 |
|------|---------|---------|---------|---------|-------------|
| C8-001 | P1 | **保留 P1** | 无（附重大事实修正） | 新发现 | 装配链 5 层无守卫、挂死环、cancel 无效（`Cancellable.cancel:40-51` 只走 onCancel 不走 afterComplete→finish）全部核实；**ORM 子面「LIMIT 0 空列表→静默空任务」被证伪**——`LongRangeBean(0,0).isEmpty()==true` 使 `buildPagedSql`/两分页 handler 均不加 LIMIT（`LimitOffsetPaginationHandler:24`、`OffsetFetchPaginationHandler:24` 的 `limit>0\|\|offset>0` 门对 (0,0) 不放行）→ 内建 orm-reader 实际**全表单页加载后挂死**（附全表内存尖峰），后果比初审描述更重；静默空任务面收窄至 jdbc/file/generator/list 族（`RecordInputImpls:43-44`）。挂死面扩大到最常用 reader，P1 更稳固 |
| C8-002 | P1 | **保留 P1** | 无 | 新发现 | 不对称（Export:277 无守卫 vs Import:301-302 有守卫）、静默钳位（adjustBatchSize:89-90）、每行双 INFO（BatchTask:301,351）+ 每 chunk 状态文件写（:322-323 + EtlTaskStateStore）全部核实。P1 终裁：默认装配确定性触发 + 日志磁盘耗尽面 + 钳位静默不可感知；与 C5-002「默认装配确定性放大判 P1」先例口径一致。边界说明：若以「不可逆数据/内存损失」为 P1 硬门槛则属边界案例，建议维持 P1 保持口径一致 |
| C8-003 | P2 | **保留 P2** | 无 | 新发现 | xdef:2 声明 vs BatchTask:83-88→cachedThreadPool（GlobalExecutors:77-78 maxPoolSize=-1/queue=0）反差核实。定性裁定：batch.xdef 注释是随产品分发的 schema 工件，声明失真按**产品缺陷随修**，不按 owner-doc drift 豁免；`docs-for-ai/03-modules/nop-batch.md:14` 及参数表行属 owner-doc 漂移，登记随修义务（F3⑥）不单独立案 |
| C8-004 | P2 | **保留 P2** | 无 | 新发现 | 独立 grep 复核生产构造/接线/beans.xml 全零，nop-task 先例 TaskFlowManagerImpl:111 核实，无翻案 |
| C8-005 | P3 | **保留 P3** | 无 | 新发现 | at-least-once 语义与 setup 窗口（completeExceptionally:131-139→fireAfterComplete）独立重放一致；discard 无计数、takeBatch 静默 null、removePartition 零调用方核实 |
| C8-006 | P3 | **保留 P3** | 无 | 新发现 | fail-closed、20min 超时、`>0` 静默禁用、必死公式 `batchSize > rateLimit×1200`（permits/rateLimit 秒 > 1200s）核实 |
| C8-007 | P2 | **保留 P2** | 无 | 新发现 | `.retry(` 生产零调用点、RetryConsumeHelper:32 唯一计数点、getLoadItemCount:157-159 错源 vs loadCounter:122 对照、SkipConsumeHelper:41-42 先例全部核实。微小笔误：接口方法实为 17 个非 16，不改判级 |
| C8-008 | P2 | **保留 P2** | 无 | 新发现 | 两个字段 private 无读取 API、queue 局部变量闭包不可达、enableMetrics=true 零消费（DefaultThreadPoolExecutor 零 metrics 引用、getEnableMetrics 全仓 main 零调用）逐点核实 |
| C8-009 | P2 | **保留 P2** | 无 | 新发现 | 两 setter 全仓零外部调用方、Builder 唯一构造点不透传、xdef 不暴露、内部生效路径（takeBatch:171 break 条件）核实——「幽灵旋钮」（内部生效但无入口）定性准确，与 CL-038 死配置（有入口不生效）机理相反的对照成立 |
| C8-010 | P2 | **保留 P2** | 无 | 新发现 | biz 钳制只判 `>` 上界（batchSize=0 穿透）、BizExportTaskBuilder:48 直传、globalWorker 缺省 30/10000（CommonConfigs:26-27 + ThreadPoolConfig:27）、core/exp 四处 cached 兜底无钳制，逐点核实 |

**统计**：发现 10 条——保留 10（P1×2、P2×6、P3×2），驳回 0，降级 0，升级 0，判级变动 0；事实修正 2 处（C8-001 ORM 子面、C8-007 方法计数笔误）。CL 线索 7 条——确认 5（CL-022/023/025/037/039）、确认但改列处置 1（CL-024）、驳回独立立案 1（CL-038 与 C5-002 同点重复）。

### 二、C8-001 终裁详述（本轮核心）

1. **两面后果的独立验证结果**（与初审/任务书转述的差异已加粗）：
   - 内建 **orm-reader**：`load(0)` → `dao.findNext(...,0)` → `findPage(sql,0,0)` → `new LongRangeBean(0,0)` → `JdbcTemplateImpl.buildPagedSql:387-392` 与 `JdbcHelper.prepareStatement:190` 两条路径对 (0,0) 均**不加 LIMIT**（分页 handler 的 `limit>0||offset>0` 门不放行、`LongRangeBean.isEmpty()==limit==0`）→ **全表单页加载 → addBatch(N_all) → acquirePermits(N_all) 对 Semaphore(0) 永久阻塞 → 任务挂死**（全表驻留内存；fetch 线程持 fetchMutex 阻塞，其余 fetch 线程 park 在监视器；消费者 takeBatch 终止条件 `count<=0 && fetchCount==0 && noMoreData` 因 fetch 线程未退出永不可达）。**非静默空任务**。
   - 内建 **jdbc-reader / file-reader**（经 `IRecordInput.readBatch` → `RecordInputImpls.defaultReadBatch:43-44`「maxCount=0 表示一条都不读」）及 generator/list 族：**空页 → EOF → 静默空任务「成功」全量丢数**——此面核实成立。
   - 自定义不守 0 的 loader：挂死——核实成立。
   - **cancel 无效前提**：外部 `context.cancel()` 走 `Cancellable.cancel:40-51`（仅置 cancelled + onCancel 回调），不触发 afterComplete → `queue.finish()` 不执行 → acquirePermits 只看 `finished` 的环与 takeBatch 等待环均不解 → 挂死持续，无超时。核实成立。
2. **判级**：P1 维持。Java API 缺省 `BatchDispatchConfig`（loadBatchSize=0）+ dispatch 模式即触发，双终态（orm 挂死/内存尖峰；jdbc/file 静默丢全量）均为 P1 级；负值走 `new Semaphore(负)` IAE fail-fast 但无配置上下文。
3. **合并裁定建议**：与 C5-005 同位点，按 C5 复核既定登记**以 C8-001 P1 为主处置项合并**（C5-005 P2 作为同点从项吸收）；本条 ORM 事实修正同时推翻 C5 复核结论「标准内建 loader 对 batchSize=0 均返回空页」中 orm-reader 部分（其引证仅覆盖 generator/list/record-input 三族，遗漏 OrmQueryBatchLoaderProvider 链），Phase 6/7.1 合并时需一并修正，避免修复验证用错期望终态（验证 orm 路径时应期望「挂死+内存尖峰」而非「空任务成功」）。
4. **修复建议确认**：三层防线任一即可闭环（PartitionDispatchQueue 构造 Guard / buildLoader:340 校验 / xdef min 语义）；补一条——Guard 消息应带 loadBatchSize 与 capacity 公式上下文，避免负值 IAE 现状的「无配置上下文」问题。

### 三、参数护栏矩阵抽验结果

抽验 15 行（超出任务要求的 5 行）：A1、A2、A5、A6、A7、A8、B1、B2、B4、B5、B6、D1、D6、E1、E2、E3、E5、E7（实际 18 行）与 live code 一致：
- A8：`RetryPolicy.maxRetryCount` 缺省 2（:24）、setter 无 Guard（:78-80）vs `setRetryDelay` 有 `Guard.nonNegativeInt`（:89-91）同文件不对称、`isExceedRetryCount:141-146` `max<0→false`=无限重试——全对。
- B2：`BatchTaskBuilder:342` `>0` 判定使负 fetchThreadCount 静默走同步派发——对。
- D1：`JdbcBatchLoaderProvider:246` 条件 + `SQL.java:109` 缺省 fetchSize=-1 + `JdbcHelper.setFetchSize:229` 只应用 sql 自带值——死配置成立；E5 对照（`ExportTableConfig.buildSQL:43-50` 写入 SQL 对象 → 活配置）成立。
- B4：`buildDispatchConfig:377` 缺省 `MathHelper.random().nextInt(0, Short.MAX_VALUE)` 随机分区、`limitHash:383-387` floorMod Short.MAX_VALUE——对。
- 矩阵小结「三层护栏完整者为 0」维持。唯一随动修正：B1 行「0 → 静默丢全量（内置 loader）」对 orm-reader 应改为「全表加载后挂死」，对 jdbc/file/generator/list 维持——判定 ❌ 不变。

### 四、CL 线索处置

| CL | 处置 | 依据 |
|----|------|------|
| CL-022 | **确认**（P3，归 C6/F8） | `SplitBatchConsumer.setup:43-45` activeConsumers 为 setup 局部 ConcurrentHashMap、consume 只 put 不 remove——任务存续期内按 tag 单调增长、生命周期以单次任务为界，与线索描述一致 |
| CL-023 | **确认**（P3，归 C9） | 三 loader 的 `synchronized(state)` load 段核实：Orm :101-105 / Jdbc :185-190 / Resource :166-176——load 并行度钳为 1 |
| CL-024 | **确认机制、建议改列处置**（原 P2 候选 → 建议并入 C6-001 修复族验证面；若单独立案建议 P3） | claim 认领（takeBatch:168）与释放（completeBatch:283-302，经 chunk ctx onAfterComplete）核实；触发面收敛分析：常规 interrupt 路径 takeBatch 抛 InterruptedException → processChunk catch → completeExceptionally → completeBatch 释放；executor 同步 REE 在 claim 之前（executeAsync 裸逃逸，属 C6-001 兄弟面）；真正可达的 claim 泄漏面=Error 逃逸/硬杀，与 C6-001「Error 使 future 永不完成」同一终态化缺口——应作为 C6-001 终态化修复的验证断言（completeBatch/claim 释放），不独立 P2 立案 |
| CL-025 | **确认**（P3，归 C3） | takeBatch await 环（:204,229）与 acquirePermits 环（:339-343）只检查 finished 不检查 cancelled，取消延迟上界=队列排空时间——核实 |
| CL-037 | **确认**（P3，归 C9） | `getConsumer` 全方法 synchronized（:59-70）含 `activeConsumers.get(tag)` 快路径——核实 |
| CL-038 | **驳回独立立案**（与 C5-002 同点重复；增量对照面并入 C5-002 备注） | 缺陷点与 C5-002 为同一代码同一缺陷（`JdbcBatchLoaderProvider:245-247` 条件 + SQL 缺省 -1 + JdbcHelper:229），本轮 C5 已立案 P1；CL-038 的增量仅有 E5 活性对照与「与 C8-009 镜像」关系说明，价值并入 C5-002。事实本身经核实成立（驳回的是重复立项，非事实） |
| CL-039 | **确认**（P3，归 C5/C9） | `buildDispatchConfig:376-378` 缺省随机 partitionFn、`:383-387` limitHash floorMod(Short.MAX_VALUE) 核实；同审计 C5/C6/C7 无同点立案，无重复 |

### 五、争议点与跨维度移交

1. **C8-001 ORM 子面（唯一重大事实修正）**：初审与 C5 复核的「内建 loader 均 LIMIT 0/空页」校准对 orm-reader 均不成立。本复核已给出完整证据链（LongRangeBean.isEmpty → buildPagedSql/JdbcHelper 双路径 → 两分页 handler 门条件）；请 Phase 6/7.1 合并裁定时采纳，并同步修正 C5 复核结论的相关表述。该修正不改 P1 判级，但改变修复验证的期望终态（orm 路径挂死 vs 空任务）。
2. **C8-002 的 P1 边界性**：写放大无数据损失，若平台裁定口径以「不可逆损失」为 P1 硬门槛，本条是唯一可能被降 P2 的候选；本复核基于「默认装配确定性触发 + 磁盘耗尽面 + C5-002 先例一致性」建议维持 P1，最终由合并裁定统一口径。
3. **C8-003 声明失真的定性边界**：schema 工件内注释失真按产品缺陷随修（非 owner-doc drift 豁免）——本复核裁定，供修复方区分两个修改面（batch.xdef 随代码修，docs-for-ai 按 docs 义务修）。
4. **CL-024 与 C6-001 的归并**：claim 泄漏的可达触发面与 C6-001 的 Error 终态化缺口重合，建议合并处置并加验证断言，避免双条修复互相等待。
5. **同族对照（WI 1.1–1.6）**：本模块 10 条与 WI 1.1–1.6 各单元（含 WI 1.6 C8-013 参数零护栏、C8-004 零指标族、C8-008 可观测性族）无同文件同点重复，全部为新发现；家族形态（参数零护栏/零指标/幽灵旋钮）为跨模块复发模式，建议 Phase 6 合并裁定时按家族归组统计。

## 最终保留项（复核后）

| # | 条目 | 终判级 | 一句话裁定 |
|---|------|--------|-----------|
| 1 | nop-batch-C8-001 | P1 | dispatch 链 loadBatchSize≤0 五层无护栏——orm-reader 全表加载后挂死（含内存尖峰）、jdbc/file/generator/list 静默空任务丢全量、自定义 loader 挂死且 cancel 无效；与 C5-005 合并、以本条为主处置项 |
| 2 | nop-batch-C8-002 | P1 | ExportDbTool 缺省 batchSize=0 每行一 chunk（双 INFO+状态文件写）+ 与 Import 守卫不对称 + 钳位静默；与 C5-002 同为「默认装配确定性放大」P1 口径 |
| 3 | nop-batch-C8-003 | P2 | 兜底全局 cached 池无上界 + batch.xdef:2 声明与实现相反（schema 工件缺陷随修）+ owner doc 漂移随修义务 |
| 4 | nop-batch-C8-004 | P2 | BatchTaskMetricsImpl 零生产接线（独立 grep 复核零翻案） |
| 5 | nop-batch-C8-005 | P3 | discard 路径 at-least-once 成立；丢弃量不可审计 + takeBatch 静默 null 为残余缺口 |
| 6 | nop-batch-C8-006 | P3 | batchSize×rateLimit 必死组合（>rateLimit×1200）装配期不可见 + rateLimit≤0 静默禁用 |
| 7 | nop-batch-C8-007 | P2 | metrics.retry 零数据源 + getLoadItemCount 错源（Timer 采样=chunk 次数）；与 C8-004 绑定修复 |
| 8 | nop-batch-C8-008 | P2 | 积压/池水位无编程出口（queue 闭包不可达 + enableMetrics 死标志）；C8-004 前置 |
| 9 | nop-batch-C8-009 | P2 | loadBatchMultiplyFactor/maxLockQueueCountPerThread 幽灵旋钮；C8-001 Guard 修复必须先于参数化 |
| 10 | nop-batch-C8-010 | P2 | core/exp 无界 vs biz 有界+钳制的护栏双标；biz 钳制无下界致 batchSize=0 经 HTTP 穿透复现 C8-002 |

CL 线索维持「不立案展开」定位：CL-022/023/025/037/039 确认移交对应维度；CL-024 改列 C6-001 修复族验证面；CL-038 驳回独立立案（并入 C5-002）。

