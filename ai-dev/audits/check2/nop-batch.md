# nop-batch 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-batch
- 文件数: 250（src/main/java，含 `_gen/` 与 `_` 前缀生成文件 37 个）；非生成实现文件 213 个
- 覆盖范围声明: 深读约 100 个实现文件，覆盖全部逻辑承载类：core 的 consumer/loader/processor/impl/metrics/utils 全量（63 个）、dsl 的 manager/support/runner/utils/model 包装类（10 个）、jdbc 全量（7 个）、orm 全量（7 个）、biz 的 importexport 主要类（6 个）、exp 的工具/状态/处理器（8 个）、dao 的 store/history（3 个）、gen 的 generator/loader/model（9 个）、service（2 个）；并对全部 src/main/java 做了模式扫描（SimpleDateFormat/@Value/@Inject private/printStackTrace/catch 吞噬/随机数等）。未深读区域: api 模块 DataBean、dao 的 entity 类、dsl `_gen` 模型、exp/config 其余配置 Bean（均为纯数据类/生成物，抽查无逻辑）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 5 |
| P2 | 6 |
| P3 | 9 |

## 发现列表

### [P1] BizExportTaskBuilder.newExcelWriter 未设置 resourceLocator，默认 XLSX 导出初始化即 NPE

- **文件**: `nop-batch/nop-batch-biz/src/main/java/io/nop/batch/biz/importexport/BizExportTaskBuilder.java:81-88`
- **维度**: D1
- **证据**:
```java
protected IBatchConsumerProvider<Object> newExcelWriter(BizEntityExportConfig config) {
    ExcelResourceIO<Object> recordIO = newExcelIO(config);
    ResourceRecordConsumerProvider<Object> writer = new ResourceRecordConsumerProvider<>();
    writer.setPathExpr(getPathExpr());
    writer.setRecordIO(recordIO);          // 未调用 setResourceLocator(...)
    addMeta(writer, config);
    return writer;
}
```
而 `nop-batch-core/.../common/AbstractBatchResourceHandler.java:47-57`：
```java
public IResource getResource(IBatchTaskContext context) {
    if (resource != null) return resource;
    String resourcePath = this.resourcePath;
    if (pathExpr != null) resourcePath = ConvertHelper.toString(pathExpr.invoke(context));
    return resourceLocator.getResource(resourcePath);   // resourceLocator 为 null → NPE
}
```
- **现状**: `DefaultBizEntityExporter.exportByQuery` 在 `exportFormat` 为空时默认使用 XLSX（`BizExportTaskBuilder.buildConsumer` 第 69-73 行），走 `newExcelWriter`。该 writer 没有设置 `resourceLocator`，`ResourceRecordConsumerProvider.newConsumerState` 第一行 `getResource(context)` 即对 null 的 `resourceLocator` 解引用抛 NPE。同文件中 `newCsvWriter`（第 113-118 行）正确调用了 `writer.setResourceLocator(VirtualFileSystem.instance())`，形成对照。
- **风险**: 模块默认注册的 `nopDefaultBizEntityExporter` bean（`_vfs/nop/biz/beans/biz-report-defaults.beans.xml:7`）在默认导出格式（XLSX）下任务 setup 阶段必然 NPE，整个实体导出功能不可用（CSV 格式可用）。失败发生在写任何数据之前，无数据损坏，故不定级 P0。
- **建议**: `newExcelWriter` 中补 `writer.setResourceLocator(VirtualFileSystem.instance())`（与 `newCsvWriter` 一致；导出临时文件路径来自 `ResourceHelper.getTempResource`，位于 VFS 可达路径）。
- **误报排除**: 已读 `ResourceRecordConsumerProvider.newConsumerState`（第 95-99 行首先调用 `getResource`）、`AbstractBatchResourceHandler` 全文（无默认 locator、无其他赋值点）、`ModelBasedBatchTaskBuilderFactory.getWriter0` 第 652-656 行（对 excelWriter 只再 set aggregator/metaProvider，不设 locator）、`ExportDbTool.newResourceConsumer`（第 382-389 行，正确设置 locator 的正例），确认无任何后续路径补设 locator。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. `newExcelWriter` 补 `writer.setResourceLocator(VirtualFileSystem.instance())`（与 `newCsvWriter` 一致，附注释说明缺省时 `getResource` 对 null locator 解引用 NPE）。红验证：`TestBizExportTaskBuilderLocator#testExcelWriterHasResourceLocator` 修复前失败于 `expected: not <null>`（locator 缺失的直接证据；深层 NPE 形态与审计推演一致）。nop-batch-biz 3/0/0。

### [P1] FileBatchSupport.newExcelWriter 未设置 resourceLocator，DSL `<excelWriter>` 消费者初始化即 NPE

- **文件**: `nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/FileBatchSupport.java:162-172`
- **维度**: D1
- **证据**:
```java
public static ResourceRecordConsumerProvider<Object> newExcelWriter(BatchExcelWriterModel consumerModel,
                                                                    IBeanProvider beanContainer) {
    IResourceRecordOutputProvider<Object> recordIO = newExcelIO(consumerModel);
    //IResourceLoader resourceLoader = loadResourceLoader(consumerModel.getResourceLoader(), beanContainer);

    ResourceRecordConsumerProvider<Object> writer = new ResourceRecordConsumerProvider<>();
    writer.setPathExpr(consumerModel.getFilePath());
    writer.setRecordIO(recordIO);
    //writer.setResourceLoader(resourceLoader);
    return writer;
}
```
- **现状**: 注释掉的代码显示 locator 设置被误删。同文件 `newFileWriter`（第 95-106 行）通过 `loadResourceLocator`（缺省 `ZipResourceLocator.INSTANCE`）设置了 locator，`newExcelReader` 用 `VirtualFileSystem.instance()`，唯独 excel 写入路径缺失。
- **风险**: 任何通过 batch DSL 配置 `<consumers><excelWriter filePath="..."/></consumers>` 的任务在 setup 时 NPE，Excel 导出消费者完全不可用（功能级故障，非数据损坏）。
- **建议**: 恢复被注释的 locator 设置（Excel 写入目标通常在 VFS，建议 `VirtualFileSystem.instance()` 或按模型属性解析 bean）。
- **误报排除**: 已读 `AbstractBatchResourceHandler.getResource` 确认无默认值；已读 `ModelBasedBatchTaskBuilderFactory.getWriter0` 确认 excelWriter 分支不再设置 locator；正例对照 `ExportDbTool.newResourceConsumer`。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. `FileBatchSupport.newExcelWriter` 补 `writer.setResourceLocator(VirtualFileSystem.instance())`（与同文件 `newExcelReader` 一致），并清除误导性的注释残留；`beanContainer` 参数保留（公开签名）。红验证：`TestFileBatchSupportLocator#testExcelWriterHasResourceLocator` 修复前失败于 `expected: not <null>`。nop-batch-dsl 13/0/0。

### [P1] AsyncFetchPartitionDispatchLoaderProvider：任务取消/失败且队列满时 fetch 线程永久阻塞（线程泄漏）

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/AsyncFetchPartitionDispatchLoaderProvider.java:109-138`；配合 `nop-batch-core/.../loader/PartitionDispatchQueue.java:310-315`
- **维度**: D2 / D3
- **证据**:
```java
// AsyncFetchPartitionDispatchLoaderProvider.setup() 提交的 fetch 线程
synchronized (fetchMutex) {
    List<S> list = loader.load(loadBatchSize, ctx);
    ...
    queue.addBatch(list);          // 内部 semaphore.acquire(data.size()) 无超时、不可被 finish() 唤醒
}
// PartitionDispatchQueue.addBatch()
try {
    semaphore.acquire(data.size());   // 阻塞等待消费线程 takeBatch 释放许可
} catch (InterruptedException e) { ... }
// PartitionDispatchQueue.finish() 只置标志并 signalAll，不释放许可
public void finish() { lock.lock(); try { finished = true; notEmpty.signalAll(); } finally { lock.unlock(); } }
```
- **现状**: fetch 线程在 `addBatch` 内阻塞于 `Semaphore.acquire`（容量 `loadBatchSize * loadBatchMultiplyFactor`）。许可只能由消费线程 `takeBatch` 释放。当任一 chunk 处理失败后 `BatchTask.executeChunkLoop` fail-fast 取消 context（`BatchTask.java:230-237`），消费线程全部退出，任务完成回调只调用 `queue.finish()`——不释放任何许可。此刻若信号量许可已耗尽（队列中有积压，正是高负载下的常见状态），fetch 线程永久阻塞在 `acquire` 上；它还持有 `fetchMutex`，其余 fetch 线程也永久阻塞在 synchronized 上。执行器为 `GlobalExecutors.cachedThreadPool()`（`BatchTaskBuilder.buildLoader` 第 332-333 行；daemon 线程、maxPoolSize 无限），每次失败的 dispatch 任务最多泄漏 `fetchThreadCount` 个线程。
- **风险**: DSL `<loader><dispatcher fetchThreadCount="n">`（batch.xdef 第 80-85 行支持）+ 任意 chunk 失败/取消 → 线程泄漏累积，长期运行的服务端资源耗尽。
- **建议**: `finish()` 时按 `count` 释放许可（或 `drainPermits`），使 `acquire` 能被唤醒后重检 `context.isCancelled()`；或改用 `tryAcquire(timeout)` 循环并在循环内检查取消状态。
- **误报排除**: 已读 `PartitionDispatchQueue` 全文（finish/addBatch/takeBatch 的许可账目：takeBatch 释放、removePartition 释放、finish 不释放）、`BatchTask.executeChunkLoop` 的 fail-fast 取消路径、`onAfterComplete(err -> queue.finish())` 注册（第 84-86 行）、`GlobalExecutors.cachedThreadPool` 实现（daemon、keepAlive 不影响阻塞中的线程）。同步版 `PartitionDispatchLoaderProvider` 不受影响（fetcher 在消费线程内执行，消费线程自身可继续 take 释放许可），已分析排除。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题（已被 plan344 修复覆盖）. commit `7defae0e69`（2026-08-24）：`PartitionDispatchQueue.addBatch` 的无超时 `semaphore.acquire` 改为 `acquirePermits`（`tryAcquire(permits, 500ms)` 循环 + `finished` 检查，队列已结束时丢弃数据返回 false，注释完整记录动机），并新增 `isFinished()`；`AsyncFetchPartitionDispatchLoaderProvider` 的 fetch 循环条件追加 `!queue.isFinished()`。本次按当前代码逐行核对（`PartitionDispatchQueue.java:337-349`、fetch 线程 lambda）确认修复在位。钉定测试 `TestPartitionDispatchQueueFinish#testAddBatchReturnsAfterFinish` 本次重跑绿。nop-batch-core 33/0/0。

### [P1] JdbcPageBatchLoaderProvider：分页游标在查询执行前推进，加载失败重试时整页数据被跳过

- **文件**: `nop-batch/nop-batch-jdbc/src/main/java/io/nop/batch/jdbc/loader/JdbcPageBatchLoaderProvider.java:85-95`
- **维度**: D1
- **证据**:
```java
List<T> load(int batchSize, IBatchChunkContext context, LoaderState state) {
    LongRangeBean range = state.range;
    if (range == null) {
        range = LongRangeBean.longRange(0, batchSize);
    } else {
        range = LongRangeBean.longRange(range.getEnd(), batchSize);  // 下一页 offset = offset+limit
    }
    state.range = range;              // <-- 游标先持久化到 state
    return jdbcTemplate.findPage(state.sql, range.getOffset(), (int) range.getLimit(), rowMapper);  // <-- 查询后置
}
```
- **现状**: `state.range` 在 `findPage` 执行前就推进。若 `findPage` 抛出异常（瞬时 DB 抖动），上层 `RetryBatchLoader`（与 `loadRetryPolicy` 配套的加载重试包装，`BatchTaskBuilder.buildLoader0` 第 360-365 行）捕获后重新调用 `load`，此时 `state.range` 已指向下一页，失败页被静默跳过。
- **风险**: 配置了 `loadRetryPolicy` 的分页加载任务在任一次分页查询瞬时失败后丢失整页数据（pageSize 条），且无任何报错。属于静默数据丢失。该类是 public 扩展点（本仓库内部无调用方，供下游应用使用），"分页 + 重试"是其设计组合场景。
- **建议**: 查询成功后再提交游标：先 `findPage`，成功后 `state.range = range`；或失败回滚 `state.range`。
- **误报排除**: 已读 `RetryBatchLoader`（异常捕获后无状态回滚地重试 `loader.load`）、`LongRangeBean.getEnd()`（= offset+limit，确认游标语义）。本仓库内确无 `JdbcPageBatchLoaderProvider` 的调用方（grep 验证），已在严重度中考虑该因素（未升 P0）。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 游标提交后置：`findPage` 成功返回后才写 `state.range = range`（查询失败时 state 仍指向上次成功页，重试重新加载失败页），附注释说明动机。红验证：`TestJdbcPageBatchLoaderRetry#testCursorNotAdvancedWhenPageQueryFails` 修复前失败于 `expected: <[0, 0]> but was: <[0, 100]>`（JDK Proxy 假 IJdbcTemplate 记录 findPage offset：首查失败后重试跳到 offset=100，整页跳过的直接证据）；修复后重试回到 offset=0 且成功后正常推进到 100。nop-batch-jdbc 3/0/0。

### [P1] DaoBatchRecordHistoryStore.saveProcessed 空实现，DSL historyStore 断点去重功能静默失效

- **文件**: `nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/history/DaoBatchRecordHistoryStore.java:76-79`
- **维度**: D8 / D1
- **证据**:
```java
@Override
public void saveProcessed(Collection<S> filtered, Throwable exception, IBatchChunkContext context) {

}
```
接口契约（`nop-batch-core/.../IBatchRecordHistoryStore.java:12-24`）：
```java
/**
 * 用于记录已处理过的记录，避免重复处理。一般和处理函数在一个事务中，确保成功处理时一定会保存处理记录
 */
public interface IBatchRecordHistoryStore<S> {
    Collection<S> filterProcessed(Collection<S> records, IBatchChunkContext context);
    void saveProcessed(Collection<S> filtered, Throwable exception, IBatchChunkContext context);
}
```
- **现状**: `WithHistoryBatchConsumer.consume`（core 第 57-63 行）成功后调用 `historyStore.saveProcessed(filtered, null, context)` 以记录已处理记录，但默认 DAO 实现是空方法，从不向 `NopBatchRecordResult` 表写入任何数据。`filterProcessed` 查询 `resultStatus=0` 的记录永远为空。该 store 是通过已注册 bean `nopDaoBatchHistoryStoreBuilder`（`_vfs/nop/batch/beans/app-batch-dao.beans.xml:11`）接入 `ModelBasedBatchTaskBuilderFactory.addHistoryStore`（第 289-317 行）的默认实现。
- **风险**: 任何在 batch DSL 中配置 `<historyStore>` 且使用默认 builder 的任务：断点/重启后全部记录被重复处理。若消费者非幂等（如重复发送通知、重复插入），造成业务数据重复。整个过程无任何告警，用户以为去重生效。
- **建议**: 实现 `saveProcessed`：成功时按 `model.getRecordKeyExpr()` 批量插入 `NopBatchRecordResult(resultStatus=0)`；或若刻意留空，在 builder 处显式报错/打 WARN，避免静默失效。
- **误报排除**: 已 grep 全仓库确认无其他组件向 `NopBatchRecordResult` 写入 `resultStatus` 记录（service 模块的 BizModel 仅为通用 CRUD）；已读 `WithHistoryBatchConsumer` 调用链、`DaoBatchHistoryStoreBuilder`、`ModelBasedBatchTaskBuilderFactory.addHistoryStore`、beans.xml 注册。另一种实现 `JdbcKeyDuplicateFilter` 不依赖 saveProcessed（其数据源是目标表本身），已区分。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题（已被 plan344 修复覆盖）. commit `7defae0e69`（2026-08-24）：`saveProcessed` 在 `exception == null`（处理成功）时逐条写入 `NopBatchRecordResult`（batchTaskId/recordKey=resultStatus 0/recordInfoExpr 序列化），失败不写、重启重处理（方向安全），事务与 consume 同事务。本次按当前代码逐行核对实现与注释在位。钉定测试 `TestDaoBatchRecordHistoryStore`（3 用例：成功落库字段断言/失败不落库对照/recordInfo）本次重跑绿。nop-batch-dao 3/0/0。

### [P2] DefaultBizEntityImporter.importFile 返回 null（已注册为默认 bean）

- **文件**: `nop-batch/nop-batch-biz/src/main/java/io/nop/batch/biz/importexport/DefaultBizEntityImporter.java:11-17`
- **维度**: D8
- **证据**:
```java
public class DefaultBizEntityImporter implements IBizEntityImporter {
    @Override
    public CompletionStage<BizEntityImportResponseBean> importFile(IResource resource,
                                                                   String bizObjName,
                                                                   BizEntityImportConfig config,
                                                                   IServiceContext context) {
        return null;
    }
}
```
注册（`_vfs/nop/biz/beans/biz-report-defaults.beans.xml:8`）：
```xml
<bean id="nopDefaultBizEntityImporter" class="io.nop.batch.biz.importexport.DefaultBizEntityImporter"/>
```
- **现状**: 接口声明返回 `CompletionStage<BizEntityImportResponseBean>`，默认实现返回 null。任何注入 `IBizEntityImporter` 并链式调用（`.thenApply` 等）的下游代码立即 NPE。
- **风险**: 依赖该默认 bean 的导入功能调用即 NPE；契约违约。当前仓库内无调用方（grep 验证），为下游应用暴露的扩展点，故定 P2。
- **建议**: 抛出 `UnsupportedOperationException`/带错误码的 `NopException`，或提供最小可用实现。
- **误报排除**: 已读 `IBizEntityImporter` 接口与 beans.xml 注册；grep 确认仓库内无调用方。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题（已被 plan344 修复覆盖）. commit `7defae0e69`（2026-08-24）：`importFile` 改抛 `UnsupportedOperationException`（英文消息，说明占位实现并提示覆盖 bean），不再返回 null。当前代码核对在位（类注释同步更新）。钉定测试 `TestDefaultBizEntityImporter#testImportFileThrowsNotImplemented` 本次重跑绿。nop-batch-biz 3/0/0。

### [P2] ResourceRecordConsumerProvider.consume 无并发保护，concurrency>1 时多线程交错写同一文件

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/ResourceRecordConsumerProvider.java:89-93, 151-171`
- **维度**: D3
- **证据**:
```java
@Override
public IBatchConsumer<R> setup(IBatchTaskContext context) {
    ConsumerState<R> state = newConsumerState(context);
    return (items, ctx) -> consume(items, state);      // 多 chunk 线程共享 state，无同步
}
...
void consume(Collection<R> items, ConsumerState<R> state) {
    ...
    state.output.writeBatch(items);      // IRecordOutput（CSV/Excel 等）非线程安全
    state.output.flush();
}
```
- **现状**: `BatchTask` 支持 `concurrency > 1`（多个 chunk 线程并发调用 consumer，`BatchTask.java:136-140` 注释明确 "loader/processor/consumer都需要是线程安全的"）。文件写入 consumer 共享一个 `IRecordOutput` 且 `consume` 无任何同步。对照同模块读侧 `ResourceRecordLoaderProvider.setup`（第 175-177 行）对 `load` 做了 `synchronized (state)`。
- **风险**: 任务配置 concurrency>1 且 consumer 为 fileWriter/excelWriter/csv 时，多线程并发 `writeBatch` 产生交错行/损坏文件（输出数据错误）。
- **建议**: 与读侧对齐，`consume` 内 `synchronized (state)`（或文档明确文件 writer 仅支持单线程并在 setup 校验 concurrency）。
- **误报排除**: 已读 `BatchTaskBuilder.buildChunkProcessor` 确认 consumer 链中无任何针对文件 writer 的串行化包装；已读 `ResourceRecordLoaderProvider` 的同步实现作为同模块对照；`BatchTask` 确认并发执行模型。触发需要 concurrency>1 的特定配置，故 P2。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. `ResourceRecordConsumerProvider.consume` 整体包 `synchronized (state)`（与读侧 `ResourceRecordLoaderProvider` 的 `synchronized(state)` 对齐，附注释），多 chunk 线程对同一 `IRecordOutput` 的 writeBatch 串行化。红验证：`TestResourceRecordConsumerConcurrentWrite#testConcurrentConsumeIsSerialized` 修复前失败于 `concurrent writeBatch calls must be serialized on ConsumerState ==> expected: <0> but was: <1>`（t1 进入慢速 write 后 t2 交错进入的 overlap 计数）；修复后 0 overlap。nop-batch-core 33/0/0。

### [P2] EtlTaskStateStore：非原子状态文件写入 + 并发 HashMap 访问（ImportDbTool 多线程模式）

- **文件**: `nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/state/EtlTaskStateStore.java:39-54, 75-78, 104-118`
- **维度**: D3 / D2
- **证据**:
```java
public synchronized boolean isTableCompleted(String tableName) { ... }   // 有锁
...
class EtlTableStateStore implements IBatchStateStore {
    public boolean isCompleted() {
        return taskState.makeTableState(tableName).isCompleted();       // 无锁：可能并发 mutate HashMap
    }
}
...
synchronized void saveTableState(String tableName, boolean complete, IBatchTaskContext context) {
    ...
    FileHelper.writeText(stateFile, JsonTool.serialize(taskState, true), null);  // 直接覆写，非 temp+rename
}
```
- **现状**: (1) `EtlTableState.makeTableState` 内部是普通 `HashMap.computeIfAbsent`（`EtlTaskState.java:35-43`）；`saveTableState`/`loadTableState`/`complete` 加锁，但 `EtlTableStateStore.isCompleted()` 无锁。`ImportDbTool.execute`（threadCount>1 时）主线程在循环中调用 `isTableCompleted`/`isCompleted` 与后台表任务并发。(2) 状态文件在每个 chunk 保存时被 `writeText` 直接覆写，无 temp+rename 原子替换；进程在写入中途崩溃/被 kill 会留下截断的 JSON，下次启动 `JsonTool.parseBeanFromResource` 解析失败，全部表断点丢失。
- **风险**: (1) 并发下 `ConcurrentModificationException` 或 HashMap 结构损坏（D3）；(2) 断点文件损坏导致 ETL 全量重跑（检查点机制失效，D2）。
- **建议**: `isCompleted` 加 synchronized；状态文件写入改为写临时文件后原子 rename。
- **误报排除**: 已读 `EtlTaskState`（HashMap 字段）、`ImportDbTool.execute` 的多线程提交路径（第 230-254 行，threadCount>1 用 `DefaultThreadPoolExecutor`）、`ExportDbTool.execute` 同样模式。触发依赖 threadCount>1 或进程中断，属特定条件，P2。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（结构性）. (1) `EtlTableStateStore.isCompleted()` 改为委托外层 `isTableCompleted`（synchronized，与 load/save/complete 的锁协议一致），不再无锁调 `makeTableState`；(2) `saveTableState`/`complete` 的状态文件写入收敛为 `saveStateFile()`：先写同目录 `.tmp` 再 `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`（`AtomicMoveNotSupportedException` 时退化非原子替换，先例 `FileTwoPhaseCommitSink`）。复核附注（对审计前提的修正）：live code 中无锁的 `EtlTableStateStore.isCompleted()` 无生产调用方（`ImportDbTool`/`ExportDbTool` 走 `isTableCompleted`（已加锁）与任务级 `isCompleted`（boolean 读）），生产可达的实际风险是状态文件非原子覆写在进程中途被 kill 后留下截断 JSON、断点全丢。免红理由：HashMap 竞态为概率性（HEAD 上 30 轮 × 24 表并发压测未复现）、写中途崩溃无法在测试中确定性模拟，属结构正确性修复；钉定测试 `TestEtlTaskStateStoreAtomicWrite`（`testSaveTableStateWritesCompleteJsonWithoutTempLeftover`：落盘 JSON 完整可解析 + 无 .tmp 残留；`testConcurrentIsCompletedAndSaveDoNotCorruptState`：并发压测无异常）。nop-batch-exp 7/0/0。

### [P2] ExportDbTool/ImportDbTool 将含 JDBC 密码的配置整体输出到 INFO 日志

- **文件**: `nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java:127`；`nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ImportDbTool.java:135`；`nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/config/_gen/_JdbcConnectionConfig.java:221`
- **维度**: D5
- **证据**:
```java
// ExportDbTool.syncConfigWithDb()
LOG.info("nop.export-db.config=\n{}", JsonTool.serialize(config, true));   // config 内含 jdbcConnection
// _JdbcConnectionConfig 序列化方法（生成代码）
out.putNotNull("password", this.getPassword());    // 密码明文参与序列化
```
- **现状**: `ExportDbConfig`/`ImportDbConfig` 包含 `jdbcConnection`（username/password），序列化时 password 以明文输出并被 `LOG.info` 打印到应用日志。字段无任何脱敏注解。
- **风险**: 数据库口令落入日志文件（日志通常长期留存、多方可读），违反敏感信息保护要求。
- **建议**: 序列化前将 password 替换为掩码（如 `***`），或仅在 DEBUG 且脱敏后输出。
- **误报排除**: 已读 `_JdbcConnectionConfig` 生成的序列化方法确认 password 无脱敏；已读两处 `LOG.info` 调用上下文确认序列化对象为完整 config。`_gen` 文件本身不可改，应在上层序列化前脱敏。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 新增 `io.nop.batch.exp.DbToolHelper.toMaskedConfigJson`：`JsonTool.beanToJsonObject` 生成序列化副本后将 `jdbcConnection.password` 置为 `***` 再输出（原 config 对象不动，真实密码仍供 buildDataSource 使用）；`ExportDbTool`/`ImportDbTool` 的 `syncConfigWithDb` 两处 `LOG.info` 均改用该助手。红验证：编译级红——`TestDbToolConfigMasking` 引用尚不存在的 `DbToolHelper`，HEAD 上 testCompile 失败于 `找不到符号 ×3`（沿用 plan344 对 `TestJdbcBatchLoaderClosedState` 的编译级红先例；工具方法无既有调用路径可做行为红）；修复后 3 用例绿：secret-pass 不出现 / username 保留 / 原 config 密码不变。nop-batch-exp 7/0/0。

### [P2] BatchTaskBuilder：配置 dispatcher 时 loadRetryPolicy 被静默忽略

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:328-365`
- **维度**: D8
- **证据**:
```java
protected IBatchLoader<S> buildLoader(IBatchTaskContext context) {
    if (dispatchConfig != null) {
        ...
        loader = new AsyncFetchPartitionDispatchLoaderProvider<>(this::buildLoader1, ...)
                .setup(context);               // 使用 buildLoader1：不含 RetryBatchLoader 包装
        ...
    } else {
        loader = buildLoader0(context);        // 仅此路径包装 RetryBatchLoader(loadRetryPolicy)
    }
}
private IBatchLoader<S> buildLoader1(IBatchTaskContext context) {
    IBatchLoader<S> loader = this.loader.setup(context);
    if (singleSession && singleSessionInvoker != null)
        loader = new InvokerBatchLoader<>(singleSessionInvoker, loader);
    return loader;                             // 未应用 loadRetryPolicy
}
```
- **现状**: `loadRetryPolicy` 只在非 dispatch 路径（`buildLoader0` 第 360-365 行）生效；DSL 同时配置 `<dispatcher>` 与 `<loadRetryPolicy>` 时，后者被静默丢弃，加载失败直接抛错终止任务而非按策略重试。
- **风险**: 配置语义漂移：用户以为有加载重试，实际没有；任务在 DB 抖动时直接失败。
- **建议**: 在 dispatch 路径同样包装 `RetryBatchLoader`（包装 `buildLoader1` 返回值），或在配置校验阶段对 dispatcher + loadRetryPolicy 组合报错。
- **误报排除**: 已读 `buildLoader/buildLoader0/buildLoader1` 全部三条路径与 `ModelBasedBatchTaskBuilderFactory`（第 191-193 行设置 loadRetryPolicy、第 223-225 行设置 dispatchConfig，两者互不排斥），确认无其他应用点。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题（已被 plan344 修复覆盖）. commit `7defae0e69`（2026-08-24）：`buildLoader1`（dispatch 路径）补 `loadRetryPolicy != null` 时外包 `RetryBatchLoader`，包装顺序与 `buildLoader0` 一致，附注释。本次按当前代码逐行核对在位（AsyncFetch 与同步 PartitionDispatch 共用该路径）。钉定测试 `TestBatchTaskDispatchLoadRetry#testLoadRetryPolicyAppliedWithDispatchConfig` 本次重跑绿。nop-batch-core 33/0/0。

### [P2] consumers 配置 forTag 但未配置 tagger 时：多 consumer 场景被静默替换为空消费者，单 consumer 场景 forTag 被静默忽略

- **文件**: `nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/ModelBasedBatchTaskBuilderFactory.java:250-286`；`nop-batch-core/.../consumer/MultiBatchConsumerProvider.java:22-28`
- **维度**: D8
- **证据**:
```java
List<IBatchConsumerProvider<Object>> list = map.remove(null);   // 无 forTag 的消费者
if (map.isEmpty()) {
    if (list != null) builder.consumer(MultiBatchConsumerProvider.fromList(list));
} else {
    List<IBatchConsumerProvider<Object>> writers = new ArrayList<>();
    if (splitter != null) { ... }        // splitter 为 null（未配置 tagger）时跳过
    if (list != null) writers.add(...);
    builder.consumer(MultiBatchConsumerProvider.fromList(writers));  // writers 为空时 fromList 返回 null
}
// MultiBatchConsumerProvider.fromList: providers.isEmpty() → return null
// BatchTaskBuilder.buildChunkProcessor: consumer==null → EmptyBatchConsumer.instance()
```
- **现状**: 所有 consumer 都带 `forTag` 且未配置 `<tagger>` 时：`splitter==null`、`list==null`、`writers==[]` → `builder.consumer(null)` → 整条消费链被替换为 `EmptyBatchConsumer`，任务"成功"跑完但数据全部被丢弃，无任何告警。若仅一个带 forTag 的 consumer 且无 tagger：走第 250-252 行分支，forTag 被静默忽略，消费全部记录。batch.xdef（第 165-167 行）仅以注释描述 forTag 语义，无校验。
- **风险**: 配置错误被静默吞掉，轻则全部数据丢弃（下游以为已导出/已写入），重则全量数据被非预期 consumer 消费。
- **建议**: 在 `buildTask` 中校验：存在 forTag consumer 但 tagger 为 null 时抛出带定位信息的 `NopException`。
- **误报排除**: 已读 `buildTask` 完整分支、`MultiBatchConsumerProvider.fromList`、`BatchTaskBuilder.buildChunkProcessor` 第 369-371 行（null → EmptyBatchConsumer）、`getTagger`（第 616-627 行，tagger 未配置返回 null）、batch.xdef 的 forTag 定义（无 mandatory 校验）。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. `ModelBasedBatchTaskBuilderFactory.buildTask` 顶部新增 `validateConsumers`：任一 consumer 配置 `forTag` 且任务未配置 tagger 时抛 `NopException(ERR_BATCH_TASK_CONSUMER_FOR_TAG_NO_TAGGER)`（新错误码，params: batchTaskName/consumerLocation，同时覆盖审计指出的多 consumer 静默空消费与单 consumer forTag 静默忽略两种形态）。红验证：`TestBatchTaskForTagValidation#testForTagWithoutTaggerRejected` 与 `#testSingleForTagConsumerWithoutTaggerRejected` 修复前均失败于 `expected: <nop.err.batch.task-consumer-for-tag-no-tagger> but was: <nop.err.batch.task-no-loader>`（即校验缺失时配置被放行、继续走到无关报错的形态）；对照用例 `#testPlainConsumersWithoutForTagPassValidation` 前后均绿（校验放行）。i18n：batch 模块 i18n 资源仅含 entity/prop label（`nop-batch-meta` 的 yaml），错误码按模块惯例走 `ErrorCode.define` 内联消息，08-24 同批新码亦无 i18n 条目。nop-batch-dsl 13/0/0。

### [P3] AbstractRetryBatchConsumer：重试阶段失败后抛出的是首次异常，最终失败原因丢失

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/AbstractRetryBatchConsumer.java:63-69`
- **维度**: D4
- **证据**:
```java
try {
    retryConsume(snapshot, items, context);
} catch (Exception e2) {
    if (snapshot != null)
        snapshot.onError(e2);
    throw NopException.adapt(e);    // 抛出首次异常 e，e2 仅进入 snapshot.onError
}
```
- **现状**: 重试链路最终失败时（`RetryConsumeHelper.retryConsume`/`RetryOneByOneBatchConsumer` 抛出 e2），对外抛出的仍是第一次的 `e`。e2 只被内部 LOG 记录（`RetryConsumeHelper` 第 55 行）和 snapshot 感知，调用方与 stateStore 拿到的错误码/堆栈是旧异常。
- **风险**: 排障误导：真正的最终失败原因（如重试时的连接中断）不出现在任务失败记录里。
- **建议**: 优先抛 `NopException.adapt(e2)`，或将 e 作为 e2 的 suppressed 附加。
- **误报排除**: 已读 `RetryConsumeHelper`/`RetryOneByOneBatchConsumer` 确认 e2 的传播路径，确认无其他外抛点。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题（已被 plan344 修复覆盖）. commit `7defae0e69`（2026-08-24）：重试失败 catch 改抛 `NopException.adapt(e2)`，首次异常 `e2.addSuppressed(e)`（`e2 == e` 跳过防自抑制），附注释。当前代码核对在位。钉定测试 `TestRetryConsumerExceptions#testRetryConsumerThrowsFinalExceptionWithFirstSuppressed` 本次重跑绿。nop-batch-core 33/0/0。

### [P3] RetryBatchLoader：首次加载抛出的 BatchCancelException 也会进入重试循环

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/RetryBatchLoader.java:36-43`
- **维度**: D4 / D1
- **证据**:
```java
public List<S> load(int batchSize, IBatchChunkContext context) {
    try {
        return loader.load(batchSize, context);
    } catch (Exception e) {              // 包含 BatchCancelException
        return retryLoad(batchSize, e, context);   // 未先重抛取消异常
    }
}
```
- **现状**: `retryLoad` 内部对后续异常有 `catch (BatchCancelException e) { throw e; }`（第 60-61 行），但首次加载的取消异常会被送入重试：先 sleep 重试延迟、再重试一次加载，才可能传播。对照消费侧 `RetryConsumeHelper.checkRetry` 前置的取消短路（`AbstractRetryBatchConsumer.consume` 第 39-40 行）语义不一致。
- **风险**: 任务取消/停止时加载阶段多做一次真实 DB 查询并延迟退出；极端情况下（loader 每次都抛取消异常且 retryPolicy 允许）取消被拖长。
- **建议**: `load` 的 catch 中先判断 `BatchCancelException` 直接重抛。
- **误报排除**: 已读 `retryLoad` 全文与消费侧对照实现。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题（已被 plan344 修复覆盖）. commit `7defae0e69`（2026-08-24）：首次 load 的 catch 增加 `BatchCancelException` 分支置于 `Exception` 之前直接 rethrow，取消异常不进退避循环，附注释。当前代码核对在位。钉定测试 `TestRetryConsumerExceptions#testRetryLoaderRethrowsCancelWithoutRetry` 本次重跑绿。nop-batch-core 33/0/0。

### [P3] RateLimitConsumer 忽略 tryAcquire 返回值，且限流计数以 chunkItems 而非实际 items 为基数

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/RateLimitConsumer.java:27-31`
- **维度**: D6 / D1
- **证据**:
```java
@Override
public void consume(Collection<R> items, IBatchChunkContext chunkContext) {
    rateLimiter.tryAcquire(chunkContext.getChunkItems().size(), RATE_LIMIT_TIMEOUT);  // 返回值被忽略
    consumer.consume(items, context);
}
```
- **现状**: `IRateLimiter.tryAcquire(permits, timeout)` 语义为"超时拿不到许可返回 false"（已读 `nop-commons/.../IRateLimiter.java` javadoc），返回 false 时代码照样继续消费——等待 20 分钟后限流被直接放行。另外计数用 `chunkItems.size()`（整 chunk 加载数）而非 `items.size()`（重试时可能是扣除已完成记录后的子集），重试场景会多扣许可。
- **风险**: 限流仅是 best-effort；下游保护（如对第三方 API 限速）在持续过载时失效。
- **建议**: `tryAcquire` 返回 false 时抛出超时异常；基数改用 `items.size()`。
- **误报排除**: 已读 `IRateLimiter`/`DefaultRateLimiter` 实现确认返回值语义；已读 `AbstractRetryBatchConsumer` 确认重试时 items 可能是子集而 chunkItems 不变。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题（已被 plan344 修复覆盖）. commit `7defae0e69`（2026-08-24）：`RateLimitConsumer.consume` 改按 `items.size()` 取许可（修复 singleMode 许可放大）+ `tryAcquire` 返回 false 时抛 `NopTimeoutException(ERR_BATCH_RATE_LIMIT_ACQUIRE_TIMEOUT)`（fail-closed）+ `permits > 0` 守卫，两处均附注释。当前代码核对在位。钉定测试 `TestRateLimitConsumer`（3 用例：`#testAcquireTimeoutFailsClosed`/`#testPermitsFollowItemsNotChunkItems`/`#testSingleModeDoesNotAmplifyPermits`）本次重跑绿。nop-batch-core 33/0/0。

### [P3] WithHistoryBatchConsumer 对 filtered 集合做 O(n²) 的 contains 扫描

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/WithHistoryBatchConsumer.java:36-54`
- **维度**: D6
- **证据**:
```java
if (filtered.size() != items.size()) {
    ...
    for (R item : items) {
        if (!filtered.contains(item)) {     // filtered 通常为 ArrayList，contains 为 O(n)
            history.add(item);
        }
    }
```
- **现状**: 部分记录被历史过滤时，对每个 item 在 `filtered`（`filterProcessed` 返回的 List）上线性查找，整体 O(n²)。batchSize 较大（如 ImportDbTool 的 maxBatchSize 或 DSL 配置数千）时每 chunk 产生可观的无效比较。
- **风险**: 大批量 + 高历史命中率场景的 CPU 浪费（无正确性影响）。
- **建议**: 先 `new HashSet<>(filtered)` 再判断。
- **误报排除**: 已读两个 `filterProcessed` 实现（`DaoBatchRecordHistoryStore` 返回 ArrayList、`JdbcKeyDuplicateFilter` 返回 ArrayList），确认返回类型非 Set。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 部分过滤分支先 `new HashSet<>(filtered)` 再判断 contains（两个循环共用，附注释说明 O(n²)→O(n) 与语义等价性：equals 一致的元素结果一致）。免红理由：行为等价的纯性能修复，无可观察行为差异。钉定测试：`TestWithHistoryBatchConsumer#testPartiallyFilteredRecordsGoToHistoryConsumer`（部分过滤时 historyConsumer 恰收补集 [b,d]）与 `#testAllPassThroughDoesNotTriggerHistory`（全通过不触发 history 路径），修复前后均绿（等价性钉定）。nop-batch-core 33/0/0。

### [P3] ResourceRecordLoaderProvider.setup 每次 load 都向任务级 onBeforeComplete 注册新回调，无上限累积

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/ResourceRecordLoaderProvider.java:164-178`
- **维度**: D6
- **证据**:
```java
return (batchSize, ctx) -> {
    ctx.onAfterComplete(err -> { ... });
    ctx.getTaskContext().onBeforeComplete(() -> {          // 每次 load 注册一次
        if (state.getProcessingItemsSize() > 0)
            throw new IllegalStateException("processingItems must be empty");
    });
    synchronized (state) { return load(batchSize, state, ctx); }
};
```
- **现状**: `onBeforeComplete` 注册在 taskContext（任务生命周期）上，但注册动作发生在每个 chunk 的 load 里。N 个 chunk 后任务上下文累积 N 个功能相同的回调（检查同一 state），任务结束前一直持有。
- **风险**: 长任务（十万级 chunk）下内存与 complete 阶段的重复执行开销；检查逻辑幂等，无正确性影响。
- **建议**: 将该检查注册移到 `setup` 中一次性完成（state 在 setup 时已创建）。
- **误报排除**: 已读 `BatchTaskContextImpl` 的回调容器（CopyOnWriteArrayList，任务生命周期）与 `ExecutionContextImpl` 用法，确认注册次数与 chunk 数一致。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题（已被 plan344 修复覆盖）. commit `7defae0e69`（2026-08-24）：`onBeforeComplete` 注册移到 `newLoaderState` 的 `saveState` 分支（setup 恰一次），裸 ISE 换 `NopException(ERR_BATCH_PROCESSING_ITEMS_NOT_EMPTY)`（params: processingItems/readCount/resourcePath），附注释。当前代码核对在位（`ResourceRecordLoaderProvider.newLoaderState` saveState 分支）。钉定测试 `TestResourceRecordLoaderCallbackLeak#testBeforeCompleteRegisteredOnceAcrossLoads` 本次重跑绿。nop-batch-core 33/0/0。

### [P3] SingleModeBatchConsumer 异常路径不恢复 singleMode 标志

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/SingleModeBatchConsumer.java:16-24`
- **维度**: D1
- **证据**:
```java
public void consume(Collection<R> items, IBatchChunkContext context) {
    boolean singleMode = context.isSingleMode();
    context.setSingleMode(true);
    for (R item : items) {
        consumer.consume(Collections.singletonList(item), context);   // 抛出则跳过恢复
    }
    context.setSingleMode(singleMode);    // 无 finally
}
```
- **现状**: 内层 consumer 抛异常时 `setSingleMode(singleMode)` 不执行。在标准链条中该标志最终由外层 `AbstractRetryBatchConsumer` 的 finally 恢复（第 70-72 行），但当 retryPolicy 与 skipPolicy 均未配置时异常直接上抛，chunk 上下文的 singleMode 残留为 true。由于失败的 chunk 上下文随即被丢弃，实际影响极小。
- **风险**: 非标准组装（直接复用 SingleModeBatchConsumer 且无外层恢复）时上下文状态污染。
- **建议**: 用 try/finally 恢复。
- **误报排除**: 已读 `BatchTaskBuilder.buildChunkProcessor` 的组装顺序（SingleMode 在 Retry 内层）与 `AbstractRetryBatchConsumer` 的 finally，确认标准链下的实际影响有限。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题（已被 plan344 修复覆盖）. commit `7defae0e69`（2026-08-24）：consume 改 try/finally 恢复 singleMode，附注释。当前代码核对在位。钉定测试 `TestRetryConsumerExceptions#testSingleModeFlagRestoredWhenConsumeFails` 本次重跑绿。nop-batch-core 33/0/0。

### [P3] JdbcBatchConsumerProvider.setup 就地回填共享字段 fields（bean 复用时的状态污染）

- **文件**: `nop-batch/nop-batch-jdbc/src/main/java/io/nop/batch/jdbc/consumer/JdbcBatchConsumerProvider.java:88-96`
- **维度**: D3
- **证据**:
```java
@Override
public IBatchConsumer<R> setup(IBatchTaskContext context) {
    if (fields == null || fields.isEmpty()) {
        fields = jdbcTemplate.getTableMeta(querySpace, tableName).getFieldMetas();   // 修改成员字段
    }
    ...
}
```
- **现状**: 该类带 `@Inject` setter（可注册为共享 bean）。`setup` 将按表元数据回填的结果写入实例字段 `fields`：同一 bean 实例若被多个任务/多表复用，第二次 setup 直接沿用第一次的表元数据；并发 setup 时存在数据竞争。DSL 路径每次构建新实例，不受影响。
- **风险**: 仅在将 provider 注册为单例 bean 且跨表复用时触发（字段集合错用 → 写错列）。
- **建议**: 回填到局部变量，不修改成员字段。
- **误报排除**: 已读 `JdbcBatchSupport.newJdbcWriter`（每次 new 实例，DSL 路径安全）与类的 `@Inject` 注解（bean 用途），确认风险仅限 bean 复用场景。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. setup 的表元数据回填改为局部变量 `List<? extends IDataFieldMeta> fields = this.fields`（成员字段不再被 setup 修改，`getFields()` 对外语义不变），附注释说明共享 bean 复用与并发 setup 的风险。红验证：`TestJdbcBatchConsumerProviderSharedField#testSetupFetchesTableMetaForEachTable` 修复前失败于 `expected: <[t1, t2]> but was: <[t1]>`（同一 provider 实例第二次 setup 沿用第一次的表元数据，未再取 t2 元数据）；修复后每表各自取元数据。nop-batch-jdbc 3/0/0。

### [P3] SplitBatchConsumer.lazyInit 字段从未被使用（死代码）

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/SplitBatchConsumer.java:36-44`
- **维度**: D1（可维护性）
- **证据**:
```java
private final boolean lazyInit;

public SplitBatchConsumer(IRecordSplitter<R, T, IBatchChunkContext> splitter,
                          BiFunction<String, IBatchChunkContext, IBatchConsumer<T>> consumerProvider,
                          boolean lazyInit) {
    ...
    this.lazyInit = lazyInit;
}
```
- **现状**: 全类无任何读取 `lazyInit` 的代码（`getConsumer` 无条件即时创建）。调用方 `ModelBasedBatchTaskBuilderFactory` 第 276-278 行传入 `false`。
- **风险**: 误导维护者以为存在懒加载语义；无运行时影响。
- **建议**: 删除该字段或实现其语义。
- **误报排除**: 已读全类确认无读取点。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 删除 `lazyInit` 字段与构造器参数（3 参改 2 参），全仓 grep 确认唯一调用方为 `ModelBasedBatchTaskBuilderFactory`（同步更新）。该类位于 nop-batch-core 实现模块（非 nop-batch-api 跨模块公共 API），构造器收缩不触及 plan-first 保护区。免红理由：死代码删除、无行为语义变化。nop-batch-core 33/0/0、nop-batch-dsl 13/0/0 全量回归绿。

### [P3] BatchGenModel.mergeMap 条件笔误（`m1.isEmpty()` 应为 `m2.isEmpty()`，当前行为恰好等价）

- **文件**: `nop-batch/nop-batch-gen/src/main/java/io/nop/batch/gen/model/BatchGenModel.java:146-153`
- **维度**: D1（边界条件）
- **证据**:
```java
Map<String, Object> mergeMap(Map<String, Object> m1, Map<String, Object> m2) {
    if (m1 == null || m1.isEmpty())
        return m2;
    if (m2 == null || m1.isEmpty())   // 第二个条件应为 m2.isEmpty()
        return m1;
    return (Map<String, Object>) JsonMerger.instance().merge(m1, m2);
}
```
- **现状**: 典型复制粘贴笔误。因第一分支已保证 m1 非空，第二分支中 `m1.isEmpty()` 恒为 false，实际语义退化为 `m2 == null → m1`；而"m2 为空 map 时返回 m1"与 merge(m1, {}) 结果一致，故当前行为恰好正确。
- **风险**: 未来重构（如调整分支顺序）时极易引入真实 bug。
- **建议**: 修正为 `m2.isEmpty()`。
- **误报排除**: 已推演两个分支的全部分支组合确认当前行为等价；已读 `mergeWithParent` 调用上下文。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 笔误修正：第二分支 `m1.isEmpty()` → `m2.isEmpty()`，行内注释记录历史笔误与"当前行为恰好等价"的原因。免红理由：审计已推演当前行为恰好等价（第一分支保证 m1 非空，恒 false），属行为等价的防未来回归修正，无可观察行为差异。钉定测试：`TestBatchGenModelMergeMap#testMergeMapBranches`（4 个空值分支 + 双非空 merge），修复前后均绿（语义钉定，防止未来调整分支顺序引入真实 bug）。nop-batch-gen 2/0/0。

## 其他核实说明（未列入发现的排查项）

- `JdbcBatchLoaderProvider.newState` 中 `if (sql.getFetchSize() != -1 && fetchSize != null) ps.setFetchSize(fetchSize)` 疑似配置失效——经核实 `SQL.SqlBuilder.fetchSize` 缺省为 0（非 -1），queryBuilder/sqlText 路径生成的 SQL `getFetchSize()==0`，条件恒真，DSL fetchSize 实际生效，非 bug。
- `BatchSkipPolicyModel.buildSkipPolicy` 对 `getExceptionFilter()` 无判空——经核实 batch.xdef 中 `<skipPolicy>` 的 `<exceptionFilter>` 子元素为必填（无 optional 标记），配置路径下不会 NPE；程序化构造（ImportDbTool）不走该模型。
- D7 扫描（`@Inject` private、Spring `@Value`、beans.xml 注册）全部通过：nop-batch 内无违规（注入均为 setter 注入，配置注入用 `@InjectValue`）。
- `ResourceRecordLoaderProvider.onChunkEnd` 的 completedIndex 压实逻辑（多线程乱序完成 + 失败行保持 false）经推演正确：compaction 在 `synchronized(state)` 内单调推进，失败 chunk 不推进 completedIndex，恢复时不会跳过未成功记录。
- `PartitionDispatchQueue.takeBatch` 同步 fetch 模式的许可/锁账目经推演无死锁（消费线程可在 fetcher 阻塞时继续 take 释放许可）。
