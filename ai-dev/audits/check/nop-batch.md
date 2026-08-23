# nop-batch 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-batch（core/dsl 为主）
- 文件数: 213（src/main/java，剔除 `_gen/` 与 `_` 前缀生成文件；任务描述的 269 含生成文件）
- 覆盖范围声明:
  - **逐行深读**: batch-core 全部执行路径类（BatchTask/BatchChunkProcessor/全部 consumer/loader/分区派发队列/上下文与计数器）；batch-dsl 装配工厂 ModelBasedBatchTaskBuilderFactory、FileBatchSupport/JdbcBatchSupport/OrmBatchSupport、BatchTaskManagerImpl、BatchTaskRunner、BatchLoaderHelper；nop-batch-jdbc 全部 8 文件；nop-batch-orm 全部 7 文件；nop-batch-dao 手写部分（DaoBatchStateStore/DaoBatchRecordHistoryStore/DaoBatchHistoryStoreBuilder）；nop-batch-biz importexport 主要类；nop-batch-exp 的 EtlTaskStateStore 与工具类入口段；nop-batch-gen 的 loader/state/generator 主要类；nop-batch-service 全部 5 个 BizModel（薄 CRUD 壳）。
  - **快速浏览（未逐行）**: DSL model 类（getter/装配）、ExportDbTool/ImportDbTool 中段配置装配代码、gen 模块 model/parser、api/app 薄层、常量/错误码类、core 中纯适配器（Cast/GetProp/Identity processor、InvokerBatchLoader 等）。
  - **不在范围**: src/test、target/、全部 `_` 前缀生成文件、beans.xml 之外的资源文件（beans.xml 已核对）。
  - 交叉验证过 nop-commons（IRateLimiter/RetryPolicy）、nop-core（ExecutionContextImpl/IResource/FileResource）、nop-dao（JdbcDataSet/AbstractDaoHandler）等被依赖类的真实语义后再下结论。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 5 |
| P2 | 7 |
| P3 | 6 |

---

## 发现列表

### [P0] ListBatchLoader 分页索引错误：只处理第一个 chunk，剩余数据静默丢失或直接抛异常

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/ListBatchLoader.java:32-34`
- **维度**: D1（chunk 边界/尾块）、D8
- **证据**:
```java
public synchronized List<S> load(int batchSize, IBatchChunkContext context) {
    if (offset >= list.size())
        return Collections.emptyList();

    int n = Math.min(list.size() - offset, batchSize);
    List<S> ret = new ArrayList<>(list.subList(offset, n));
    offset += n;
    return ret;
}
```
- **现状**: `n` 是本次应读取的条数，正确的切片应为 `subList(offset, offset + n)`。现写法第二Chunk起 fromIndex=offset、toIndex=n，必然错误：
  - list.size()=250、batchSize=100：第 2 次 load 为 `subList(100, 100)` 返回**空列表** → `BatchChunkProcessor.process` 视为 EOF 返回 STOP → 任务**成功结束**，150 条数据被静默丢弃（仅处理 100 条）。
  - list.size()=150、batchSize=100：第 2 次 load 为 `subList(100, 50)` → 抛 `IllegalArgumentException(fromIndex > toIndex)`，任务失败。
  - 仅当 list.size() <= batchSize（单 chunk）时行为正确，掩盖了 bug。
- **风险**: 大规模静默数据丢失（任务还报成功），是批处理最严重的一类缺陷。触发路径现实且常用：DSL `<loader><provider>` 返回 List 时（`ModelBasedBatchTaskBuilderFactory.java:433`），以及 XPL 脚本 `BatchLoaderHelper.batchLoadWithFullList`（`BatchLoaderHelper.java:30`）。
- **建议**: 改为 `list.subList(offset, offset + n)`；补充跨多个 batchSize 的单元测试（对比同目录 `DebugBatchLoader`，它用 readCount 游标实现了正确语义，可作回归基准）。
- **误报排除**: 已确认无其他调用方绕过此实现；已核对 `BatchChunkProcessor.process` 对空列表即返回 STOP（`BatchChunkProcessor.java:52-54`），丢数据路径成立；本类无任何测试覆盖（nop-batch-core/src/test 无引用）。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 确认属实，已修复。`ListBatchLoader.load` 第 33 行 `subList(offset, n)` 改为 `subList(offset, offset + n)`（最小改动，单 chunk 行为不变）。测试：`nop-batch-core` `TestListBatchLoader#testLoadMultipleChunks`（修复前 250 条/batchSize=100 时第 2 个 chunk 返回空列表即视为 EOF，仅处理 100 条、150 条静默丢失，且再次 load 抛 `IllegalArgumentException: fromIndex(200) > toIndex(50)`）；同类 `testLoadExactMultipleChunks`（修复前 200 条仅处理 100 条）、`testLoadSinglePartialChunk`、`testLoadEmptyList` 覆盖整除/不足一个 chunk/空列表。修复后 nop-batch-core 16/16、nop-batch-dsl 8/8 测试通过。

### [P1] AsyncFetchPartitionDispatchLoaderProvider 吞掉 fetch 线程异常，任务可带错"成功"结束（含 0 条数据）

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/AsyncFetchPartitionDispatchLoaderProvider.java:88-95、128-136`
- **维度**: D4（异常吞噬）、D1、D8
- **证据**:
```java
IBatchLoader<S> resultLoader = (batchSize, ctx) -> {
    Exception err = exception.get();
    if (err != null)
        throw NopException.adapt(err);          // 只在 takeBatch 之前检查一次

    MapOfInt<List<S>> map = queue.takeBatch(batchSize, ctx.getThreadIndex(), null);
    if (map == null) {
        return Collections.emptyList();          // 没有再次检查 exception
    }
    ...
// fetch 线程失败路径：
} catch (Exception e) {
    LOG.info("nop.batch.exit-fetch-thread-when-fail:threadIndex={}", threadIndex, e);
    exception.set(e);
    return;
} finally {
    queue.exitFetchThread();
}
```
- **现状**: fetch 线程失败仅 `exception.set(e)` 并 `exitFetchThread()`（置 noMoreData 并唤醒等待者）。消费线程在进入 `takeBatch` 后才发生失败时，`takeBatch` 返回 null → 返回空列表 → STOP → `BatchTask.onTaskComplete` 以 **err==null 走成功分支**，异常只留在 INFO 日志里。最极端场景：任务启动时 fetch 即失败（DB 不可达），任务处理 0 条记录却标记 COMPLETED。
- **风险**: 数据加载失败被静默吞掉，任务状态与事实相反（假成功）；下游依赖任务状态判断数据完整性时造成数据缺失。DB 级错误还只记 INFO 级日志。
- **建议**: `takeBatch` 返回 null 后二次检查 `exception.get()` 再决定返回空/抛错；fetch 失败日志升为 ERROR。
- **误报排除**: 已核对 `PartitionDispatchQueue.exitFetchThread/finish` 与 `takeBatch` 的 null 返回条件（`PartitionDispatchQueue.java:186-191`），确认唤醒路径成立；确认 `resultLoader` 中无其他检查点。

### [P1] 断点续传与文件写出器组合：重启后输出文件被截断，已完成记录的产出丢失

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/ResourceRecordConsumerProvider.java:99`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/ResourceRecordLoaderProvider.java:223-231`；`nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java:110`
- **维度**: D1（断点位置）、D8（at-most-once 数据丢失）
- **证据**:
```java
// ResourceRecordConsumerProvider: 重启时重新打开输出（无 append 语义）
state.output = recordIO.openOutput(resource, encoding);   // FileResource.getOutputStream() -> new FileOutputStream(file) 截断

// ResourceRecordLoaderProvider: 恢复时按 completedIndex 跳过已处理行
private long getSkipCount(IBatchTaskContext context) {
    long processedRowNumber = context.getCompletedIndex();
    long skipCount = this.skipCount;
    if (processedRowNumber > skipCount) skipCount = processedRowNumber;
    return skipCount;
}
```
- **现状**: 读侧实现了完整断点续传（saveState + completedIndex 持久化 + 重启跳过）；写侧 `ResourceRecordConsumerProvider` 每次任务 setup 都重新 `openOutput`，底层 `FileResource.getOutputStream()` 即 `new FileOutputStream(file)`，**截断重建**。重启（同 taskKey，`DaoBatchStateStore.loadTaskState0` 恢复 completedIndex）后：读侧从断点继续，写侧把上次已写出的部分全部抹掉，被跳过的行**永远不会重写**。`IResourceRecordOutputProvider.openOutput` 接口没有任何 append/续写支持。
- **风险**: 崩溃恢复后输出文件只含断点之后的记录，静默不完整。受影响组合：DSL fileReader(saveState)+fileWriter、exp 模块 ExportDbTool/ImportDbTool（EtlTaskStateStore 同样按 completedIndex 续传，输出 sql/csv/excel 文件按表名静态命名）。
- **建议**: 至少在文档/模型校验层禁止"saveState 读 + 静态输出路径写"组合；或为文件输出提供 append/临时文件+rename 提交语义；恢复模式检测到输出文件已存在且非空时报错而非截断。
- **误报排除**: 已核对 `FileResource.getOutputStream()`（nop-core，`new FileOutputStream(file)` 默认截断）；已核对 DSL 装配中 fileWriter 的 filePath 为静态模型配置（`FileBatchSupport.java:95-106`）；已核对重启确会恢复 completedIndex（`DaoBatchStateStore.java:110`）。前提条件（同路径重启）在静态配置下必然成立。

### [P1] JdbcBatchLoaderProvider 多线程并发下，数据读尽后兄弟线程再 load 会触碰已关闭的 ResultSet，任务在处理完成后失败

- **文件**: `nop-batch/nop-batch-jdbc/src/main/java/io/nop/batch/jdbc/loader/JdbcBatchLoaderProvider.java:271-283`（配合 175-184 的共享 loader）
- **维度**: D3（多线程共享状态）、D1、D2
- **证据**:
```java
List<T> load(int batchSize, IBatchChunkContext context, LoaderState state) {
    List<T> list = RecordInputImpls.defaultReadBatch(state.dataSet, batchSize, ...);
    if (list.isEmpty()) {
        state.close();        // 关闭 ps/dataSet/(连接)
    }
    return list;
}
// setup(): 所有并发线程共享同一个 loader/state，仅 synchronized(state) 串行化调用
```
- **现状**: concurrency>1 且直接使用该流式 loader（无 dispatch 分区包装）时，所有线程共享同一 LoaderState。线程 A 读到空 → `state.close()` → 返回空并 STOP；线程 B 下一轮 `load` 在锁上排队后进入，对已关闭的 PreparedStatement/ResultSet 执行 `readBatch` → SQLException → chunk 失败 → 任务 FAILED。即数据全部处理完之后，任务因竞态被标为失败（stateStore 记录失败、重试语义被破坏）。
- **风险**: 多线程 JDBC 批任务在收尾阶段随机失败；配合重启策略会导致无意义的整体重跑（依赖 consumer 幂等）。
- **建议**: `LoaderState` 增加 closed 标志，`load` 进入时若已 closed 直接返回空列表；或空读时不立即 close，统一交给 `context.onAfterComplete` 注册的 `state.close()`。
- **误报排除**: 已核对 `BatchTask.executeChunkLoop`（`BatchTask.java:209-246`）确认多线程共享同一 `chunkProcessor`/loader；已核对 `LoaderState.close()` 关闭 ps（JDBC 规范关闭 ps 即关闭 rs）；已核对 dispatch 模式下（`PartitionDispatchLoaderProvider`）fetcher 空返回即置 noMoreData、不会再调底层 loader，故该竞态限于"非 dispatch + concurrency>1"配置——现实可配（DSL jdbcReader + concurrency>1）。

### [P1] 异步分区派发：任务失败后 fetch 线程可永久阻塞在 semaphore.acquire()，线程与 JDBC 连接泄漏

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/PartitionDispatchQueue.java:310-315`（addBatch）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/AsyncFetchPartitionDispatchLoaderProvider.java:109-138`
- **维度**: D2（连接/资源释放）、D3
- **证据**:
```java
// PartitionDispatchQueue.addBatch —— 无超时、仅可被 interrupt，但无人 interrupt
try {
    semaphore.acquire(data.size());
} catch (InterruptedException e) { ... }

// fetch 线程：addBatch 在 fetchMutex 临界区内执行
synchronized (fetchMutex) {
    List<S> list = loader.load(loadBatchSize, ctx);
    ...
    queue.addBatch(list);
}
```
- **现状**: 队列容量为 `loadBatchSize * loadBatchMultiplyFactor`（默认 30 页）。任务失败时消费者线程全部退出，队列剩余数据无人释放许可；`context.onAfterComplete -> queue.finish()` 只置 finished 标志，**不释放 semaphore 许可也不中断等待线程**。fetch 线程若正阻塞在 `semaphore.acquire(data.size())`（此时还持有 `fetchMutex`，连带阻塞其他 fetch 线程），将永久挂起，底层 JdbcBatchLoaderProvider 的连接/游标随之泄漏（其 close 只注册在 task context 的 onAfterComplete，但该线程永不返回）。
- **风险**: 每次失败的任务可能泄漏 1~fetchThreadCount 个线程与数据库连接（cachedThreadPool 线程不回收）；多次失败后连接池耗尽。
- **建议**: `finish()` 中释放全部剩余许可（`semaphore.release(count)`）或改用 `tryAcquire(timeout)` + 检查 `finished/cancelled` 后退出；fetch 循环在 addBatch 前检查 `context.isCancelled() || queue.isFinished()`。
- **误报排除**: 已核对 `finish()`（`PartitionDispatchQueue.java:115-123`）只 signalAll 不动 semaphore；已核对 `exitFetchThread` 与 acquire 无交互；确认 acquire 阻塞时 `while (!context.isCancelled())` 检查已通过、无法生效。

### [P1] rateLimit 与 singleMode 组合时限流许可被放大 batchSize 倍，实际限流严重失真

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/RateLimitConsumer.java:29`；装配顺序 `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:401-407`
- **维度**: D8（契约不匹配）、D1
- **证据**:
```java
// RateLimitConsumer：每次 consume 取"整个 chunk 输入条数"个许可
rateLimiter.tryAcquire(chunkContext.getChunkItems().size(), RATE_LIMIT_TIMEOUT);

// BatchTaskBuilder 装配顺序：SingleMode 包在 RateLimit 外层
if (rateLimit > 0) consumer = new RateLimitConsumer<>(consumer, ...);
if (singleMode) consumer = new SingleModeBatchConsumer<>(consumer);   // 逐条调用内层 consume
```
- **现状**: singleMode=true 时 `SingleModeBatchConsumer` 对每条记录调用一次 `RateLimitConsumer.consume(singletonList(item), ctx)`，而每次调用获取的许可数是 `chunkItems.size()`（= batchSize）。整 chunk 消耗 `batchSize * batchSize` 个许可，实际吞吐被压到配置值的 1/batchSize（如 batchSize=100、rateLimit=100/s，实际约 1 条/s）。另外许可按输入条数（chunkItems=S）计，而 consume 的 items 是 processor 输出（R），有过滤/展开时与"每秒处理多少条记录"的语义也有偏差。
- **风险**: 限流配置在常见组合下偏离一个数量级以上；下游保护（QPS 配额）失效或任务被过度节流。
- **建议**: RateLimitConsumer 按 `items.size()`（本次实际消费条数）取许可，或把 RateLimit 移到 SingleMode 内层；至少在装配处对两标志同用时告警。
- **误报排除**: 已核对包装顺序（builder 401-407 行赋值链）与 `chunkItems` 的赋值时机（`BatchChunkProcessor.loadItems` 在整 chunk 加载后设置），确认每条 item 的 consume 都读到整 chunk 大小。

### [P2] AbstractRetryBatchConsumer 重试失败后抛原始异常 e，重试阶段的异常 e2 丢失

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/AbstractRetryBatchConsumer.java:63-69`
- **维度**: D4（丢 cause）
- **证据**:
```java
try {
    retryConsume(snapshot, items, context);
} catch (Exception e2) {
    if (snapshot != null)
        snapshot.onError(e2);
    throw NopException.adapt(e);      // 抛的是第一次的 e，不是 e2
}
```
- **现状**: 重试仍失败时向上抛的是**首次**异常 e；e2 仅传给 snapshot（多数场景 snapshot==null，直接丢弃）。e2 既不在 cause 链也无 suppressed。外层 `SkipConsumeHelper.consumeWithSkipPolicy` 将基于过时的 e 判定 skip，若 e2 才是真正的失败原因（如重试时数据库已恢复但出现新的数据错误），skip/失败决策与事实不符；排障时也看不到最终错误。
- **风险**: 错误诊断信息丢失；skip 判定基于错误异常。
- **建议**: 抛 `NopException.adapt(e2)` 或在 e 上 `addSuppressed(e2)` 后抛出。
- **误报排除**: 已核对 `RetryConsumeHelper.retryConsume` 内部虽有 LOG.error 记录 e2（`RetryConsumeHelper.java:55-57`），但异常对象本身未传递给调用方；确认 `NopException.adapt(e)` 不会附带 e2。

### [P2] ResourceRecordLoaderProvider 每次 load 都向 TaskContext 注册 onBeforeComplete 回调，回调列表无界增长

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/ResourceRecordLoaderProvider.java:164-173`；根因配合 `nop-kernel/nop-core/.../ExecutionContextImpl.java:82-93`（纯 append、无去重）
- **维度**: D2（资源泄漏）、D6
- **证据**:
```java
return (batchSize, ctx) -> {
    ctx.onAfterComplete(err -> { ... });                       // chunk 级，正常
    ctx.getTaskContext().onBeforeComplete(() -> {              // 任务级，每次 load 都追加一个
        if (state.getProcessingItemsSize() > 0)
            throw new IllegalStateException("processingItems must be empty");
    });
    synchronized (state) {
        return load(batchSize, state, ctx);
    }
};
```
- **现状**: loader lambda 在**每次** load（每 chunk，dispatch 模式下每页 fetch）调用时都向任务级 context 的 `beforeCompletes` 列表追加一个新 lambda（每个都捕获 state 与外部变量）。长任务（如 1000 万行 / batchSize=100 → 10 万 chunk）会在任务上下文上累积 10 万个回调对象，任务结束前无法释放；结束时还要逐一执行（结果相同，纯浪费）。
- **风险**: 长批处理任务内存随 chunk 数线性增长（隐性内存泄漏）；任务收尾时的重复检查。附带问题：该检查抛裸 `IllegalStateException("processingItems must be empty")`，无错误码、无任务上下文参数。
- **建议**: 把 onBeforeComplete 注册移到 `setup()`（state 创建处）只注册一次；裸 ISE 换成 `NopException + ErrorCode + .param(...)`。
- **误报排除**: 已核对 `ExecutionContextImpl.onBeforeComplete` 为 `beforeCompletes.add(callback)` 且仅 `fireBeforeComplete` 时一次性清空（任务存续期内一直累积）；确认 setup 内其他 onAfterComplete/onBeforeComplete（input 关闭、aggregator complete）只注册一次，唯独该检查在每个 load 路径内。

### [P2] 配置 dispatchConfig 时 loadRetryPolicy 被静默忽略

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:328-365`
- **维度**: D4（重试策略配置失效）、D8
- **证据**:
```java
if (dispatchConfig != null) {
    ...
    loader = new AsyncFetchPartitionDispatchLoaderProvider<>(this::buildLoader1, ...)
            或 new PartitionDispatchLoaderProvider<>(this::buildLoader1, ...);
} else {
    loader = buildLoader0(context);       // 只有这里包装 RetryBatchLoader
}

private IBatchLoader<S> buildLoader1(IBatchTaskContext context) {
    IBatchLoader<S> loader = this.loader.setup(context);      // 无 loadRetryPolicy
    ...
}
private IBatchLoader<S> buildLoader0(IBatchTaskContext context) {
    IBatchLoader<S> loader = this.loader.setup(context);
    if (loadRetryPolicy != null)
        loader = new RetryBatchLoader<>(loader, loadRetryPolicy);
    return loader;
}
```
- **现状**: DSL 模型同时允许配置 `loadRetryPolicy` 与 `loader/dispatcher`（`ModelBasedBatchTaskBuilderFactory.newTaskBuilder:191-193` 与 `:223-225` 互不感知），但 dispatch 路径的 `buildLoader1` 不包装 `RetryBatchLoader`，loadRetryPolicy 静默失效。
- **风险**: 用户配置了加载重试却完全不生效，瞬时 DB 抖动直接导致任务失败；与无 dispatcher 时的行为不一致，难以排查。
- **建议**: `buildLoader1` 中同样包装 `RetryBatchLoader`，或两配置同用时启动报错。
- **误报排除**: 已通读 buildLoader 全部三个分支确认无其他包装点；已确认 DSL 工厂不会在别处补上 loadRetryPolicy。

### [P2] DaoBatchStateStore 同 taskKey 并发启动为 check-then-act 竞态：无唯一键保障，可双重执行

- **文件**: `nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java:73-79、128-145、157`；`nop-batch/model/nop-batch.orm.xml`（NopBatchTask 无 unique-keys）
- **维度**: D3、D8
- **证据**:
```java
if (task.getTaskStatus() <= NopBatchDaoConstants.TASK_STATUS_RUNNING) {
    throw new NopException(ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE) ...
}
...
task.setTaskStatus(TASK_STATUS_RUNNING);
taskDao.updateEntityDirectly(task);
```
- **现状**: "查状态→比对→置 RUNNING" 在 `runLocal`（REQUIRES_NEW 普通事务）内完成，无行锁（无 SELECT FOR UPDATE）、无乐观锁拦截（updateEntityDirectly 绕过），且 `nop_batch_task` 表模型**没有定义 (taskName, taskKey) 唯一键**（nop-auth 等模块的 orm 模型均有 `<unique-keys>`，本模型整文件无一处）。两个并发启动（job 触发 + 手工重启、或多实例部署）都读到非 RUNNING 状态 → 双双置 RUNNING → 同一 taskKey 双重并发执行，saveTaskState 互相覆盖计数与状态。`saveTaskState` 的 `synchronized` 只保护单 JVM。另 `loadExistingTask` 按 `execCount` **升序**取第一条（`addOrderField(PROP_NAME_execCount, true)`），一旦出现重复记录会选中最早/执行次数最少的记录，与"取最近一次执行"的恢复直觉相反。
- **风险**: 断点任务被并发重复执行（重复处理数据）；任务状态/计数被交叉覆盖。
- **建议**: 给 (taskName, taskKey) 加数据库唯一键；置 RUNNING 用条件 update（`where taskStatus > RUNNING`）并检查影响行数，或使用悲观锁读。
- **误报排除**: 已核对 `AbstractDaoHandler.runLocal` 语义（REQUIRES_NEW 事务、非锁定读）；已核对 orm 模型无 unique-keys 定义；已确认 `updateEntityDirectly` 不做版本检查。

### [P2] DaoBatchRecordHistoryStore.saveProcessed 空实现：基于 DAO 的处理历史去重机制实际不生效

- **文件**: `nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/history/DaoBatchRecordHistoryStore.java:76-79`
- **维度**: D1（断点/重启重复处理）、D8
- **证据**:
```java
@Override
public void saveProcessed(Collection<S> filtered, Throwable exception, IBatchChunkContext context) {
    // 完全为空
}
```
- **现状**: `filterProcessed` 会查询 `nop_batch_record_result` 中 `resultStatus=0` 的记录来跳过已处理项，但引擎内**没有任何代码写入**该表（全仓 grep 确认只有通用 CRUD API `NopBatchRecordResultBizModel` 供外部手工维护）。saveProcessed 永远不落库 → 重启后 filterProcessed 永远返回全量 → 配置了 dao historyStore 的任务在重启后把所有记录重新处理一遍。
- **风险**: "避免重复处理"的配置（`WithHistoryBatchConsumer` + dao store）静默退化为 at-least-once 全量重放；用户以为有断点去重，实际没有。虽然方向安全（重复而非丢失），但与配置意图相悖。
- **建议**: 实现 saveProcessed（成功时批量写入 resultStatus=0），或在 DSL 装配处检测到该实现时告警/拒绝，明确其为未完成的桩。
- **误报排除**: 已全仓检索 NopBatchRecordResult 的写入方（仅 CRUD BizModel）；已确认 `DaoBatchHistoryStoreBuilder` 直接 new 该类无子类覆写。

### [P2] DefaultBizEntityImporter.importFile 返回 null 的"缺省实现"被注册为默认 bean

- **文件**: `nop-batch/nop-batch-biz/src/main/java/io/nop/batch/biz/importexport/DefaultBizEntityImporter.java:11-16`；`nop-batch/nop-batch-biz/src/main/resources/_vfs/nop/biz/beans/biz-report-defaults.beans.xml:8`
- **维度**: D1（NPE）、D7
- **证据**:
```java
public class DefaultBizEntityImporter implements IBizEntityImporter {
    @Override
    public CompletionStage<BizEntityImportResponseBean> importFile(IResource resource, ...) {
        return null;                       // 未实现，返回 null
    }
}
// beans.xml: <bean id="nopDefaultBizEntityImporter" class="...DefaultBizEntityImporter"/>
```
- **现状**: 该类以 `nopDefaultBizEntityImporter` 注册为平台缺省 bean（本仓内无其他 IBizEntityImporter 实现）。下游应用按接口解析并调用 importFile 时得到 null → 调用方 `.thenApply/返回给前端` 直接 NPE，且没有任何"未实现"提示。
- **风险**: 缺省路径必 NPE；错误信息无法定位（无异常、仅 null）。
- **建议**: 抛 `UnsupportedOperationException`/带错误码的 NopException，或移除该 bean 注册直到实现完成。
- **误报排除**: 已确认 beans.xml 注册事实；已全仓检索确认仓内无调用方（属对外扩展点），风险对象是下游应用——按"公共扩展点契约"记录。

### [P2] RateLimitConsumer 忽略 tryAcquire 返回值：等待 20 分钟超时后限流被静默绕过（fail-open）

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/RateLimitConsumer.java:28-31`
- **维度**: D4、D8
- **证据**:
```java
public void consume(Collection<R> items, IBatchChunkContext chunkContext) {
    rateLimiter.tryAcquire(chunkContext.getChunkItems().size(), RATE_LIMIT_TIMEOUT);  // 返回值被丢弃
    consumer.consume(items, chunkContext);
}
```
- **现状**: Guava RateLimiter 语义为超时拿不到许可返回 false（`DefaultRateLimiter.tryAcquire` 已核对），此处不检查返回值，false 时照常消费。即限流持续超载 20 分钟后，后续请求全部绕过限流直接放行——限流器作为下游保护机制 fail-open。附注（超范围但相关）：`DefaultRateLimiter.getAcquireFailCount()` 误返回 acquireSuccessCount（nop-commons 拷贝错误），统计亦失真。
- **风险**: 高压场景下保护失效，可能打垮被保护的下游（外部 API 配额、DB）。
- **建议**: `if (!tryAcquire(...)) throw new NopTimeoutException(...)` 或在 metric 中计数并按策略降级，不应静默放行。
- **误报排除**: 已核对 `IRateLimiter.tryAcquire` javadoc 与 `DefaultRateLimiter` 实现确认 false 语义；确认 RATE_LIMIT_TIMEOUT=20 分钟常量。

### [P3] SingleModeBatchConsumer 异常路径不恢复 singleMode 标志

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/SingleModeBatchConsumer.java:17-24`
- **维度**: D1、D3（状态泄漏）
- **证据**:
```java
public void consume(Collection<R> items, IBatchChunkContext context) {
    boolean singleMode = context.isSingleMode();
    context.setSingleMode(true);
    for (R item : items) {
        consumer.consume(Collections.singletonList(item), context);   // 抛异常则下一行不执行
    }
    context.setSingleMode(singleMode);                                // 无 finally
}
```
- **现状**: 内层 consume 抛异常时 singleMode 停留为 true。同 chunk 上下文内若外层（如 SkipBatchConsumer）捕获异常后继续处理，后续逻辑看到错误的 singleMode。对照 `AbstractRetryBatchConsumer`（有 finally 恢复）与 `RetryConsumeHelper`，此处遗漏。
- **风险**: 低——chunk 通常随异常终止；但与 skip 策略组合时行为依赖于该标志的处理器会出错。
- **建议**: try/finally 恢复。
- **误报排除**: 已对照同包两个正确实现确认这是遗漏而非约定。

### [P3] BatchTask.getExecutor 死分支：concurrency 归一化后 syncExecutor 分支不可达

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:71-85`
- **维度**: D1（代码卫生）
- **证据**:
```java
this.concurrency = concurrency <= 0 ? 1 : concurrency;   // 永远 >= 1
...
this.executor = getExecutor(executor, concurrency);

static Executor getExecutor(Executor executor, int concurrency) {
    if (executor != null) return executor;
    if (concurrency > 0) {                 // 恒真
        return GlobalExecutors.cachedThreadPool();
    } else {
        return GlobalExecutors.syncExecutor();   // 死代码
    }
}
```
- **现状**: 构造器已把 concurrency 归一为 >=1，else 分支永不执行；同时 concurrency=1 的单线程任务也使用 cachedThreadPool（每 chunk 循环占一个可缓存线程），与"单线程同步执行"的直觉不符（功能正常，开销略增）。
- **风险**: 轻微；误导维护者以为存在 sync 路径。
- **建议**: 删除死分支；单线程时可考虑 syncExecutor 直跑。
- **误报排除**: 已核对构造器归一化顺序在 getExecutor 调用之前。

### [P3] RetryBatchLoader 首次加载抛出 BatchCancelException 也会进入重试流程

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/RetryBatchLoader.java:37-43`
- **维度**: D4（取消语义）
- **证据**:
```java
public List<S> load(int batchSize, IBatchChunkContext context) {
    try {
        return loader.load(batchSize, context);
    } catch (Exception e) {              // BatchCancelException 也是 Exception，首次也会被吞入重试
        return retryLoad(batchSize, e, context);
    }
}
```
- **现状**: 重试循环内部对 BatchCancelException 有专门放行（60-62 行），但**首次** load 抛出的取消异常会被吞进 retryLoad，多执行一次 cancel 检查/退避后才透传。取消响应延迟一个退避周期。
- **风险**: 轻微：任务取消/killed 时的停顿。
- **建议**: 首次 catch 中先判 `BatchCancelException` 直接 rethrow。
- **误报排除**: 已核对 BatchCancelException 继承关系（NopException→Exception）与 retryLoad 内部处理顺序。

### [P3] 错误处理规范违背合集：中文错误消息与裸 IllegalArgumentException/IllegalStateException

- **文件**（均为 src/main/java）:
  - `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/utils/RangeSplitUtils.java:113、122、151、194`（中文消息如 `"参数 bigInteger 不能为空."`、`"根据字符串进行切分时仅支持 ASCII 字符串..."` + 裸 IAE）
  - `nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/ModelBasedBatchTaskBuilderFactory.java:632`（`throw new IllegalArgumentException("nop.err.batch.null-writer:" + ...)`）
  - `nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java:347`（同模式）
  - `nop-batch/nop-batch-biz/src/main/java/io/nop/batch/biz/importexport/BizExportTaskBuilder.java:77`（同模式）
  - `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/ResourceRecordLoaderProvider.java:172`（裸 ISE，见 P2 回调累积条目）
- **维度**: D4、D7（错误消息须英文、模块级异常/ErrorCode 约定）
- **现状**: 平台约定错误消息用英文、经 NopException+ErrorCode 或模块异常类抛出。以上位置用裸 RuntimeException 族 + 字符串拼接（部分用 `nop.err.*` 前缀模拟错误码但未走 ErrorCode 机制），RangeSplitUtils 直接中文。
- **风险**: 错误无法按 ErrorCode 国际化/定位；违反仓库 AGENTS.md 约定。
- **建议**: 迁移到各模块 Errors 类 + NopException。
- **误报排除**: 已核对 BatchDslErrors/NopBatchDaoErrors 等 ErrorCode 机制存在且被其他路径正常使用，属局部遗漏而非机制缺失。

### [P3] ModelBasedBatchTaskBuilderFactory.safeLoad 对 chunk 上下文加锁，无实际互斥效果

- **文件**: `nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/ModelBasedBatchTaskBuilderFactory.java:421-425`
- **维度**: D3（无效同步）
- **证据**:
```java
private List<Object> safeLoad(IEvalFunction fn, int batchSize, IBatchChunkContext ctx) {
    synchronized (ctx) {
        return (List<Object>) fn.call2(null, batchSize, ctx, ctx.getEvalScope());
    }
}
```
- **现状**: 每个 chunk 拥有独立 ctx，`synchronized(ctx)` 在多线程步骤中锁的是各线程自己的对象，不提供任何跨线程互斥。若意图是串行化非线程安全的用户 source 函数，则未生效；若无此意图，则锁是纯开销并误导维护者。
- **风险**: 用户以为 `<loader><source>` 多线程下被串行化，实际并发执行。
- **建议**: 要么去掉锁并在文档声明 source 需线程安全，要么用共享对象（task 级）做锁。
- **误报排除**: 已核对 `BatchTask.processChunk` 每 chunk new 一个 `BatchChunkContextImpl`（`BatchTaskContextImpl.newChunkContext`），锁对象确为每 chunk 独立。

### [P3] BatchLoaderHelper 使用固定缓存 key，多 provider 复用同一 task context 时互相串扰

- **文件**: `nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/utils/BatchLoaderHelper.java:13、15-22`
- **维度**: D1
- **证据**:
```java
static final String KEY_LOADER = "batchLoader";

public static <T> List<T> batchLoadWithProvider(int batchSize, IBatchChunkContext chunkCtx, IBatchLoaderProvider<T> provider) {
    IBatchTaskContext taskCtx = chunkCtx.getTaskContext();
    IBatchLoaderProvider.IBatchLoader loader = taskCtx.computeIfAbsent(KEY_LOADER, k -> provider.setup(taskCtx));
    return loader.load(batchSize, chunkCtx);
}
```
- **现状**: loader 缓存在 task context 的固定属性 `"batchLoader"` 上。同一任务上下文中若第二处代码（另一段 XPL、嵌套任务）用**不同的 provider** 调用本方法，会命中第一个 provider 的缓存 loader，返回错误数据源的结果。
- **风险**: 低频但后果是数据源错乱；随 XPL 复用增加而上升。
- **建议**: key 中纳入 provider 身份（identityHashCode/显式 name 参数）。
- **误报排除**: 已核对 `computeIfAbsent` 为 context 属性级缓存且 key 为常量；无其他命名空间隔离。

---

## 补充说明（未列为发现的核对项）

- **D5 安全**: JDBC 写入器（JdbcInsertBatchConsumer/JdbcUpdateBatchConsumer/JdbcKeyDuplicateFilter）全部经 `sb.typeParam` 参数绑定，未发现值拼接；表名/列名来自受控 DSL 模型。未发现文件路径注入（路径来自模型/表达式，属管理员配置面）。
- **D7 平台规范**: 未发现 `@Inject` 私有字段注入（全部 setter 注入）；配置注入正确使用 `@InjectValue`；bean 均在 `_vfs` 下 beans.xml 显式定义；未发现 `_` 前缀文件被手改的迹象（本审计只读）。
- SkipConsumeHelper 的整 chunk 粗粒度 skip（失败后未完成的记录全部计为 skipped 不再处理）与 `BatchSkipPolicy.maxSkipCount=0` 表示不限次为设计选择，配合 `RetryOneByOneBatchConsumer` 可实现逐条精细 skip，未列为缺陷；但其"默认无上限 + 默认过滤一切非 Error 异常"（`BatchSkipPolicy.java:42-51`）值得使用方注意。
- PartitionDispatchQueue 主流程（takeBatch/addBatch/completeBatch/removePartition 的许可与计数账目、fetchMutex 保序）经逐行核对未发现错误；`ResourceRecordLoaderProvider.onChunkEnd` 的 failed 行不推进 completedIndex 的压实逻辑正确（有注释与实现互证）。
