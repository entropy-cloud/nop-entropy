# nop-batch 并发·异步·资源专项审计 —— C6 资源生命周期与泄漏（第 2 轮深挖）

## 审计单元

nop-batch 聚合器 11 个含 Java 的 jar 子模块：api / app / biz / core / dao / dsl / exp / gen / jdbc / orm / service。手写规模 218 files / 15,698 行（src/main，不含 `_` 前缀生成物与 target/）。核心引擎位于 `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core`。

## 仓库与分支

- 仓库：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`
- 分支：`concurrency-audit-remediation`（HEAD 4b7c7897de）
- 审计日期：2026-10-16
- 子代理：C6 维度第 2 轮深挖（盲审轮）

## 本轮输入

1. 第 1 轮报告 `C6-resource-lifecycle.md`（C6-001~006：P1×2 / P2×2 / P3×2 + CL-012~015）——按盲审纪律唯一允许读取的审计目录文件。
2. 基线 `_tmp/cra-baseline-nop-batch.txt`（C6 两节：resource acquisition / closeable，均零命中，确认无遗漏的基线 grep 面）。
3. live code：全部 11 子模块 src/main 手写源码（本轮全文/重点通读 30+ 文件，其余按资源语义 grep 扫描）。
4. 平台依赖面抽样核实（仅验证契约，不重审）：`ExecutionContextImpl`/`ContextProvider`/`Cancellable`/`FutureHelper.complete`/`GlobalExecutors`/`TransactionalFunctionInvoker`/`JdbcTemplateImpl.currentConnection`/`DelegateRecordInput`/`LimitRecordInput`。

## 盲审纪律声明

本报告为盲审轮次产物。审计过程中**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件，唯一例外为本维度第 1 轮文件 `C6-resource-lifecycle.md`（任务指定必要输入）；未读取同目录其他维度文件。测试代码（src/test）作为 live code 阅读，未作为审计结论来源。所有「现状」字段一律填「新发现（待复核）」。

## 本轮目标（逐条执行记录）

- ① C6-001/C6-002 失效面扩展：穷举「异常逃逸→终态回调不执行」站点，产出**终态化阻断站点表**（见一）。
- ② reader/writer/连接关闭出口全枚举（F3 主表，见二）。
- ③ 事务边界资源面：transactionScope 四档连接借用/归还、rollback 波及面、DaoBatchStateStore 连接管理（见三）。
- ④ 监听器资源责任：12 种 listener 的开启/关闭职责与异常半开状态（见四）。
- ⑤ 线程池/线程生命周期收尾：fetch 线程退出路径、exp 两工具 destroy 同族扩展（见五）。

---

## 一、终态化阻断站点表（目标①）

「异常逃逸 → 任务 future 永不结算 / onTaskComplete 不执行 / afterComplete 关闭链不触发 / 任务行不停留终态」的全量站点清单。后果记法：**F**=future 不结算（调用方挂死）、**T**=onTaskComplete 不执行、**C**=afterComplete 关闭链不触发（reader/writer/连接泄漏）、**R**=任务行不停留终态（永久 RUNNING，重启被闸门拒绝）。

| # | 站点 | 逃逸异常类型 | F | T | C | R | 归属 |
|---|------|-------------|---|---|---|---|------|
| S1 | `BatchTask.executeChunkLoop:243` catch(Exception) 不中（processChunk/xpl/JDBC driver 抛 Error） | Error | ✓ | ✓ | ✓ | ✓ | C6-001（第 1 轮） |
| S2 | `executeChunkLoop:226` `executor.execute` 同步 REE（有界注入池/停机竞态） | RejectedExecutionException | ✓ | ✓ | ✓ | ✓ | C6-002（第 1 轮） |
| S3 | `executeChunkLoop:228-231` **try 块之前**的 `propagateContext(ctx, context)` + `BatchTaskGlobals.provideTaskContext(context)`（外层 `runWithContext` 的 `getOrCreateContext()` 同窗口）抛出任何 Throwable | Exception（可达性低）/ Error | ✓ | ✓ | ✓ | ✓ | **C6-010（本轮新增）** |
| S4 | `onTaskComplete:204-213` err≠null 分支内 `saveTaskState(true, err)` 抛出新异常 X：completeExceptionally 在 finally 中必达（context/future/metrics 均终态），但 X 穿透 finally 逃逸；当调用点为 `allOf().whenComplete`（:148，正常任务结束路径）时 X 仅作用于无人观察的 whenComplete 返回 future → 无日志静默丢失，任务行未写终态 | Exception | ✗ | ✗ | ✗ | ✓（R 成立） | **C6-009（本轮新增）** |
| S5 | `onTaskComplete:214-218` 外层 finally 中 `metrics.endTask` 先抛 → `FutureHelper.complete` 被跳过 → future 孤儿（context 已终态、资源已关，仅调用方挂死） | Exception/Error | ✓ | ✗ | ✗ | 视 S4 | C6-009 同清理段登记 |
| S6 | `executeAsync:136-139` setup 失败路径内 onTaskComplete 二次抛出 → X 替代原始 e 同步逃逸 executeAsync（future 已结算但未返回给调用方，可见性错乱；对照 S4 的静默——同一缺陷在两条调用路径上一静默一错乱） | Exception | ✗ | ✗ | ✗ | ✓ | C6-009 同条登记 |
| S7 | `ExecutionContextImpl.fireAfterComplete:176` per-callback catch(Exception)：某 close 回调抛 Error → 其后注册的 close 回调全部跳过（input-close 先注册、writer-close 后注册 → writer 句柄残留） | Error | ✗ | ✗ | 部分 | 部分 | C6-001 失效面 6（第 1 轮，归并不重复立案） |
| S8 | cancel 路径结算核验：loop 顶 BatchCancelException → `completeExceptionally` → allOf → onTaskComplete → `saveTaskState(true, BCE)` → `getTaskStatus` 按原因映射 KILLED/CANCELLED/SUSPENDED（DaoBatchStateStore:257-274）→ future/资源/任务行三路全闭环 | — | ✗ | ✗ | ✗ | ✗ | 通过（外部 future.cancel 与引擎解耦归 CL-014） |
| S9 | chunk 级 saveTaskState 失败（:322-323 抛）→ catch → fireChunkEnd + `saveTaskState(false,e)` 再抛 → finally `chunkContext.completeExceptionally` ✓ → 汇入 S1 的 Exception 路径正常终态化；失败 chunk 不推进 completedIndex | Exception | ✗ | ✗ | ✗ | 视任务级写入 | 通过（第 1 轮 #13，声明边界内） |
| S10 | 阻塞型（非逃逸型）不结算：`BlockingSinkBatchConsumer.consume:38` 阻塞在 sink、`RateLimitConsumer.tryAcquire`（上限 20 分钟）、`ResourceRecordLoaderProvider.waitUntilUnderLimit`（上限 60s）、JDBC/文件 read 本体——cancel 仅置 flag 不中断（`Cancellable:40-51` 无 interrupt）→ 阻塞期间 future 不结算；前两者有界，sink/read 无界 | — | 视阻塞时长 | 视 | 视 | 视 | 结构性同族（根因=取消不中断线程），与 CL-014 同根登记，不另立案 |

S3 的结构性特征（区别于 S1）：S1 是「catch 类型不够宽」（Exception→Throwable），S3 是「catch 守卫起点过晚」——try 之前存在可抛出代码，**即使改为 catch(Throwable) 也无法覆盖 S3**，必须将 try 上移至 lambda 首行。这是本轮对 C6-001 修复方案的实质性补充：只改 catch 类型不能闭合该族。

---

## 二、reader/writer/连接关闭出口全枚举（目标②，F3 主表）

grep `\.close()|safeClose|destroy\(\)|shutdown|awaitTermination` 全模块 src/main（排除 test/target），全部命中站点逐一登记。关闭出口路径覆盖 = 正常完成 / 异常 / fail-fast cancel 三路径。

| # | 资源持有点 | 关闭出口 | 正常 | 异常 | cancel | 判定 |
|---|-----------|---------|------|------|--------|------|
| 1 | `ResourceRecordLoaderProvider.newLoaderState:184` openInput（CSV/Excel/模型 reader；`ZipResourceLocator`/VFS 流委托） | 任务级 afterComplete（:189-191，先于 beforeRead 注册） | ✓ complete | ✓ setup 抛错→executeAsync catch→completeExceptionally | ✓ fail-fast→allOf→onTaskComplete | 通过（第 1 轮复核维持；本轮补充：`input.limit(maxCount)` 返回 `LimitRecordInput extends DelegateRecordInput`，close 委托底层（DelegateRecordInput:86-88），limit 包装不破坏关闭链——反虚构核验通过） |
| 2 | `ResourceRecordConsumerProvider.newConsumerState:103` openOutput（CSV/Excel/GenInsertSql writer） | 任务级 afterComplete（:105-107）+ onBeforeComplete trailer/flush（:131-149） | ✓ | ✓ beginWrite(:121-125)/aggregator 抛错同上 | ✓ | 通过 |
| 3 | `JdbcBatchLoaderProvider.newState:226-230` connection（closeConnection=true 自持 / =false 事务绑定）+ ps + JdbcDataSet | `LoaderState.close:169-175`（synchronized，ps→dataSet→connection）挂任务级 afterComplete（:185）+ EOF 早关（:292-294） | ✓ | ✓（SQLException 窗口 ps 残留=C6-005，第 1 轮）| ✓ | 通过（附 C6-005；**事务绑定连接跨任务持有的异步窗口见 C6-011**） |
| 4 | `JdbcInsertBatchConsumer:51`/`JdbcUpdateBatchConsumer` 经 `jdbcTemplate.runWithConnection` 借还；`JdbcKeyDuplicateFilter:60/74` findAll | 平台 JdbcTemplate | ✓ | ✓ | ✓ | 通过 |
| 5 | `OrmQueryBatchLoaderProvider`（keyset `dao.findNext:133`，LoaderState 仅持 lastEntity/query/dao 引用，无流无连接） | 无需关闭 | — | — | — | 通过（本轮全文复核维持） |
| 6 | `OrmBatchConsumer`/`OrmInsertBatchConsumer`/`OrmBatchHelper.assignEntity`/`OrmBatchRecordSnapshotBuilder`（session.attach/currentSession/clearSession） | 平台 ORM 会话 | ✓ | ✓（Error 跳过 snapshot.onError 归 C6-001 面 5） | ✓ | 通过 |
| 7 | `PartitionDispatchQueue` semaphore/队列/锁 | takeBatch 按实取 release（:181）+ finish（任务 afterComplete，:84-86 两个 Provider）+ completeBatch（chunk afterComplete） | ✓ | 未派发记录随任务对象 GC，许可不跨任务 | ✓（acquirePermits 500ms 轮询 finished） | 通过（附 C6-006 removePartition 死代码，第 1 轮） |
| 8 | `ExportDbTool:148-153` / `ImportDbTool:230-235` 自建 IThreadPoolExecutor | finally destroy（Export:187 / Import:262） | ✓ | **Import 侧 try 前窗口持有真实池化资源（C6-007）**；destroy=shutdown 无 await（C6-004，第 1 轮） | — | **C6-007（新）** / C6-004 |
| 9 | `ImportDbTool:123-125` 自建 HikariDataSource（needClose=true） | finally `IoHelper.safeClose(dataSource)`（:263-266）——**仅覆盖 :242 起的 try** | ✓ | ✗ syncConfigWithDb/readTableMetas 抛错时 finally 不可达 | — | **C6-007（新）** |
| 10 | `ExportDbTool:121` SimpleDataSource（非池化，无池可泄——第 1 轮核实维持，本轮无推翻证据） | 无 close（无实资源） | — | — | — | 通过（维持第 1 轮结论） |
| 11 | `DefaultBizEntityExporter:57` 导出临时文件 | 失败即删（:69-71）/ 成功延迟 10 分钟删（globalTimer :72-76） | ✓ | ✓ | ✓ | 通过（第 1 轮维持） |
| 12 | `EtlTaskStateStore.saveStateFile:129-144` 状态文件 | `FileHelper.writeText` 平台关闭 + ATOMIC_MOVE 降级替换；无长持句柄；全写路径 synchronized(this)（complete/saveTableState/isTableCompleted 单监视器） | ✓ | ✓ | ✓ | 通过 |
| 13 | `BatchGenLoaderProvider`（extends AbstractBatchResourceHandler 但无 openInput/openOutput，parseFromResource 内部自闭合） | 无需 | — | — | — | 通过 |
| 14 | `SplitBatchConsumer` 懒 setup（factory :307-309 `consumerMap.get(tag).setup(ctx.getTaskContext())`）：每 tag 首次消费时打开输出文件并注册任务级 afterComplete + onBeforeComplete | 任务级 afterComplete（注册顺序=tag 发现顺序，fireAfterComplete 按序全执行） | ✓ | ✓ | ✓ | 通过（懒打开的 writer 与 eager writer 共享同一关闭链，无遗漏出口；`getConsumer` synchronized 防并发重复 setup） |
| 15 | Excel/CSV 流的模块内部实现（`ExcelResourceIO`/`CsvResourceRecordIO`，nop-report/nop-core） | 经 IRecordInput/Output.close 由 #1/#2 关闭链触达 | 委托 | 委托 | 委托 | 模块外（本单元边界），配对接口完好 |
| 16 | 全模块其余 20+ 小类（loader/consumer 装饰器、Multi/Split/Skip/Retry/AddCompleted/Invoker/Eval 等） | grep 扫描无 open/close/sleep/lock 资源语义（仅 ListBatchLoader.load synchronized，良性） | — | — | — | 通过 |

**关闭出口单点化核验（onAfterComplete 单一出口同族穷尽）**：挂任务级 afterComplete 的全部为 4 处——ResourceRecordLoaderProvider（input）、ResourceRecordConsumerProvider（output）、JdbcBatchLoaderProvider（state.close）、AsyncFetch/PartitionDispatch 的 queue.finish；挂 chunk 级 afterComplete 的全部为 3 处——ResourceRecordLoaderProvider 的 chunk 压实回调（:167-171）、AsyncFetch :103-105 与 PartitionDispatch :75-77 的 queue.completeBatch。逐处核对三路径可达性：任务级 4 处在 S1/S2/S3 站点被阻断时全部失守（第 1 轮 P1 的后果面，本轮以 S 表落位）；chunk 级 3 处中 **AsyncFetch 的 chunk 压实回调注册在 fetch 线程的一次性 chunk context 上、永不触发**（C6-008，新）。

---

## 三、事务边界资源面（目标③）

`BatchTaskBuilder.buildChunkProcessor:384-453` 四档接线的连接借用/归还路径核对：

| 档位 | 事务包络 | 事务传播（TransactionalFunctionInvoker 缺省 REQUIRED，:23） | reader 连接波及 |
|------|---------|------|------|
| none | 无 Invoker 包装 | — | 无事务交互 |
| chunk | `InvokerBatchChunkProcessor` 包裹整个 chunk（load+process+consume 同事务，:444-447） | chunk 线程无已注册事务 → 每 chunk 新事务（线程绑定，`getRegisteredTransaction` ThreadLocal 语义） | reader 连接来自 setup 线程（通常无环境事务 → `currentConnection` 返回 null → `openConnection` 自持 closeConnection=true）→ **chunk rollback 不波及 reader**（不同连接不同线程） |
| process | `InvokerBatchConsumer` 包裹 WithHistory 外侧（:410-412） | 同上 | load 在事务外执行 ✓ |
| consume | `InvokerBatchConsumer` 包裹 WithHistory 内侧（:391-394）；有 historyStore 时自动提升为 process（:385-389，history 原子性） | 同上 | 同上 |

- **rollback 与 reader 同连接借用冲突**：唯一成立窗口 = setup 线程自身持有已注册事务（job/biz 层在事务内启动任务）→ `currentConnection(querySpace)` 返回事务绑定连接（JdbcTemplateImpl:124-129）→ closeConnection=false → LoaderState 持该连接上的 open ResultSet 直到任务结束。chunk 线程的事务仍在其各自连接上（rollback 不波及 reader）；**真正的窗口是异步用法下 setup 线程事务先于任务结束而终结** → 见 C6-011（P3，仓内当前调用面同步等待，理论风险）。
- **DaoBatchStateStore 连接管理**：loadTaskState/tryInsertNewTask 走 `runLocal`（REQUIRES_NEW 局部会话，:47/:85）；saveTaskState 走 `updateEntityDirectly`（:253-254，平台会话托管）。无手工连接持有；saveTaskState 抛错的任务行后果见 C6-009。`tryInsertNewTask` 唯一键竞争回滚仅回滚局部插入（:83-104），不污染外层会话 ✓。
- **history 与 consume 同事务**：consume→process 自动提升接线（:381-389）与第 1 轮声明边界一致，本轮核对 Invoker 包装位置（WithHistory 外侧）确认 saveProcessed 在事务内 ✓。

---

## 四、监听器资源责任（目标④）

12 个注册点（BatchTaskContextImpl:392-493 十个 onXxx + ExecutionContextImpl onBeforeComplete/onAfterComplete）与触发点全量复核，按「承担资源开启/关闭职责」镜头：

| listener | 触发点异常语义 | 资源后果 |
|----------|---------------|---------|
| onTaskBegin | fireTaskBegin（:518-530）无 per-callback 防护，**先置空列表再逐个 run** → 第 N 个回调抛异常时后续 begin 回调跳过且不可重放 | begin 型半开（后续未执行的分配类回调缺失）；关闭链仍完整（executeAsync catch → completeExceptionally）。→ CL-035 |
| onTaskEnd（DSL :552-557 → onAfterComplete） | fireAfterComplete per-callback catch(Exception) 吞+log | 释放型监听器抛 Exception 不阻断其他释放 ✓；Error 阻断后续（归 C6-001 面 6） |
| onChunkEnd | fireChunkEnd（:555-566）per-callback catch(Exception) 吞+log（C1 轮已知） | 释放型抛 Exception 被吞 → 该监听器自己的资源可能半开，但任务级关闭链（文件/连接）独立于此，不受影响；Error 中断后续 onChunkEnd 监听器（含压实回调——`onChunkEnd` 注册的 `ResourceRecordLoaderProvider.onChunkEnd` 亦在列表内，Error 时压实跳过→汇入任务失败路径，completedIndex 不推进，at-least-once 兜底） |
| onLoadBegin/onLoadEnd | fireLoadBegin/End 无防护、传播；BatchChunkProcessor.loadItems:71-75 catch(Exception) 再 fire 一次 → **双触发** | → CL-034 |
| onChunkTryBegin/onChunkTryEnd | RetryConsumeHelper:46-58 无防护传播（TryEnd 在 catch 内保证触发 ✓） | 无持有资源语义 |
| onConsumeBegin/onConsumeEnd | BatchProcessorConsumer.consumeResult:115-120 catch(Exception) 内补 fire 后 adapt 抛 ✓ | 无持有资源语义 |
| onBeforeComplete（trailer/flush、processingItems 检查、aggregator.complete） | fireBeforeComplete 无防护传播 → onTaskComplete success 分支 catch(Exception) 转 err → completeExceptionally → afterComplete 关闭链兜底 ✓ | writer 已 flush 失败 → 关闭仍执行 ✓ |

**死代码登记（修正第 1 轮 #11 的「全量配对」表述）**：`BatchTaskContextImpl:70` 的 `onTaskEnds` 字段**全仓无注册方法、无触发点**（grep 仅声明处）；`AbstractBatchResourceHandler.onTaskEnd:59-60` 为空实现且引擎零调用。任务结束回调的真实通路是 `BatchTaskGlobals.onTaskEnd → ctx.onAfterComplete`（:28-31）与 DSL `listenersModel.getOnTaskEnd()`（factory :552-557），二者均工作正常。无运行时后果，登记为第 1 轮通过项 #6/#11 的勘误，不立案。

---

## 五、线程池/线程生命周期收尾（目标⑤）

**fetch 线程（AsyncFetchPartitionDispatchLoaderProvider:114-144）退出路径全枚举**：

| 退出条件 | 机制 | 阻塞点是否可退出 |
|----------|------|----------------|
| 数据读完（EOF） | `markNoMorData` + return → finally `exitFetchThread`（latch countDown → 全部退出时 noMoreData+signalAll，唤醒消费端 takeBatch） | ✓ |
| load 抛 Exception | catch（:133-138）→ `exception.CAS` → return → finally exitFetchThread → 消费端 takeBatch 返回 null 前复查 exception（:94-100）→ 任务响亮失败 | ✓ |
| load 抛 Error | 穿透 catch → finally exitFetchThread 仍执行 → noMoreData 置位 → 消费端 `exception==null` → 空列表 → **任务以部分数据「成功」**（第 1 轮 C6-001 失效面 4，维持，归并不立案） | ✓ 退出但静默 |
| 任务取消（fail-fast） | while 条件 `!context.isCancelled()`（:118）→ 正常退出 → finally exitFetchThread | ✓（若正阻塞在 load 本体，等该次读返回） |
| 任务完成/失败终态化 | 任务 afterComplete → `queue.finish()`（:84-86）→ `queue.isFinished()` 使 while 退出 | ✓ |
| fetch 线程阻塞在 `acquirePermits`（addBatch 容量满） | 500ms `tryAcquire` 轮询 + `finished` 标志（PartitionDispatchQueue:337-350）→ 终态后丢弃数据退出（discard-batch-when-finished 为声明边界，许可不入队不滞留） | ✓ 有界 |
| fetch 线程阻塞在底层 `loader.load` 本体（JDBC read/文件 read） | 无中断机制（cancel 不 interrupt） | ✗ 无界——继承自「取消不中断」结构性边界（CL-014 同根），fetch 线程不额外放大 |

结论：**任务终态后 fetch 线程无永久泄漏路径**；唯一无界阻塞点在底层读本体，属取消语义边界而非 fetch 机制缺陷。

**ExecutorService 优雅关闭同族扩展**：grep 全模块 `ExecutorService|newFixedThreadPool|newCachedThreadPool|Executors\.|scheduledExecutor` —— nop-batch 内零原生 ExecutorService 使用；`IThreadPoolExecutor` 使用点仅 ExportDbTool:148-153 / ImportDbTool:230-235 两处（均已立案 C6-003/C6-004），`GlobalExecutors.cachedThreadPool/globalWorker/syncExecutor/globalTimer` 为平台单例（显式不关闭，豁免），`SyncThreadPoolExecutor.INSTANCE.destroy()` 为 no-op（第 1 轮已核实无害，本轮维持）。**C6-004 的 awaitTermination 缺失族在 nop-batch 内无第三成员，族封闭。**

---

## 六、发现条目（本轮新增）

### [nop-batch-C6-007] ImportDbTool 的 HikariDataSource 建于 try/finally 保护窗口之外：syncConfigWithDb/readTableMetas 抛错时连接池整体泄漏（修正第 1 轮 C6-003「Import 侧对称性完好」结论）

- **文件**： `nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ImportDbTool.java:121-146`（needClose=true 与池创建 :123-125、readTableMetas 调用 :132）、`:226`（syncConfigWithDb 在 try 之外）、`:242`（try 起点）、`:261-267`（finally 中 needClose→safeClose(dataSource)）
- **证据片段**:
  ```java
  public void execute() {
      ...
      syncConfigWithDb();          // :226 —— try 之外！内部 buildDataSource + readTableMetas 均可抛
      ...
      try {                        // :242 —— 保护从这里才开始
          ...executor.submit(...)
      } finally {
          executor.destroy();
          if (needClose) {
              IoHelper.safeClose(dataSource);   // :263-266 同步ConfigWithDb 失败时永远不可达
              dataSource = null;
          }
      }
  }

  public void syncConfigWithDb() {
      JdbcConnectionConfig conn = config.getJdbcConnection();
      if (this.dataSource == null) {
          this.needClose = true;                                   // :124
          this.dataSource = buildDataSource(conn);                 // :125 —— threadCount>1/并发配置时为 Hikari 池
      }
      ...
      readTableMetas();                                            // :132 —— JdbcMetaDiscovery 连库发现元数据，可抛
  }

  protected DataSource buildDataSource(JdbcConnectionConfig conn) {
      if (config.getConcurrencyPerTable() != null || config.getThreadCount() > 0) {
          DataSourceConfig config = conn.toDataSourceConfig();
          config.setIdleTimeout(Duration.of(10, ChronoUnit.SECONDS));
          return new HikariDataSourceFactory().newDataSource(config);   // —— 真实池：housekeeping 线程 + 初始连接
      }
      return conn.buildDataSource();
  }
  ```
- **严重程度**: P2
- **并发场景**: 单线程 CLI/embedded 入口（`CliImportDbCommand`/注入调用），无并发交错；纯异常路径配对缺陷。触发条件为常规运维面：配置了 `threadCount>1` 或 `concurrencyPerTable`（此时 buildDataSource 走 Hikari 池分支）且目标库元数据发现失败（URL 错误、权限不足、网络超时——`readTableMetas` 内 `JdbcMetaDiscovery.forDataSource(...).discover(...)` 需建立真实连接）。
- **触发时序**: T1：`execute():226` 进入 `syncConfigWithDb`，:124 置 `needClose=true`，:125 创建 HikariDataSource（池按 HikariCP 缺省急切建立 initial/min 连接 + housekeeping 调度线程）。T2：:132 `readTableMetas` 抛出（元数据发现失败）。T3：异常沿 `execute()` 向上传播，**:242 的 try 从未进入** → :261 的 finally 不执行 → `safeClose(dataSource)` 永不调用 → 整个 Hikari 池（打开的 JDBC 连接/socket FD + housekeeping 线程）泄漏，直至进程退出（HikariCP 线程为 daemon，CLI 场景 JVM 退出即回收；**常驻进程内重复调用 ImportDbTool.execute 时每次失败累积一个池**，FD 持续增长）。对照第 1 轮 C6-003 的核验记录：「Import 侧自建 HikariDataSource 已由 needClose 在 finally 关闭（:261-267），对称性完好」——该结论只覆盖了 try 进入后的窗口，漏掉了 syncConfigWithDb 本身位于 try 之前且**在 Import 侧该窗口持有真实池化资源**这一事实（Export 侧窗口内为非池化 SimpleDataSource 无实资源，第 1 轮对 Export 的结论维持不变）。
- **现状**: 新发现（待复核）。对第 1 轮 C6-003 的实质性修正：C6-003 的「泄漏窗口核验为空」仅对 ExportDbTool 成立；ImportDbTool 的同型窗口持有真实资源，对称性并不完好。
- **风险**: 常驻进程（测试套件反复执行、embedded 用法）中 FD/连接累积；CLI 场景后果有限（daemon 线程随 JVM 退出）但池内已建立的 DB 连接在失败后仍保持打开直到进程退出，目标库侧可见悬挂会话。
- **建议**: 将 `syncConfigWithDb()` 调用移入 try 块首行（:242 之前并入 try），使 needClose 的 finally 覆盖池创建后的全部失败路径；或 catch 中显式 `if (needClose) IoHelper.safeClose(dataSource)` 后重抛。与 C6-003 的修复（beforeImport 段移入 try）可合并为同一 diff：execute() 全体重排为「无资源段 → try { 可能持资源段 } finally { 释放段 }」。
- **失效面枚举**（F3，exp 两工具 try 前窗口资源持有清单）：`ExportDbTool.execute:121-159`（窗口内 SimpleDataSource 非池化无实资源 + executor 懒建线程，维持第 1 轮「理论风险」判定）；`ImportDbTool.execute:218-240`（**窗口内 HikariDataSource 真实池，本条**）。两处同一修复模式可一并闭合。
- **信心水平**: 确定（代码路径唯一；Hikari 分支触发条件、finally 不可达性均为纯代码可见）
- **误报排除**: 非「平台关闭钩子兜底」——needClose/finally 是本类自己声明的关闭责任，缺陷在其覆盖范围不含资源创建点；非重复立案——第 1 轮 C6-003 明确记录 Import 侧「对称性完好」，本条为对该核验遗漏的修正而非同一站点的重复报告。
- **复核状态**: 已复核（独立复核）——**保留 P2**。HikariDataSource 建于 syncConfigWithDb:125（try :242 之外）事实成立；同窗口可抛点不止 readTableMetas:132，还包括 DialectManager.getDialectForDataSource:128（dialect 未配置时连库探测）；finally :261-267 不可达核实。**小勘误**：Hikari 分支条件为 `threadCount > 0`（buildDataSource:139）且 concurrencyPerTable!=null，本条表述「threadCount>1」偏窄——threadCount=1 即走 Hikari 分支，触发面略宽于描述，判级不变。C6-003 对 Import 侧的修正裁定成立（见 C6-resource-lifecycle.md「维度复核结论」R3）。现状：新发现（已知未修复）。

---

### [nop-batch-C6-008] saveState（断点续传）+ AsyncFetch 分区派发组合：chunk 压实回调注册在 fetch 线程的一次性 chunk context 上永不触发——completedIndex 永不推进、任务必然以 ERR_BATCH_PROCESSING_ITEMS_NOT_EMPTY 失败

- **文件**： `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/AsyncFetchPartitionDispatchLoaderProvider.java:114-144`（fetch 线程以自建 ctx 调用底层 loader）、`ResourceRecordLoaderProvider.java:163-177`（loader lambda 在**调用线程**的 ctx 上注册 chunk 级 afterComplete）、`:216-228`（processingItems 与任务级 onBeforeComplete 完整性检查）、`BatchTaskBuilder.java:342-344`（fetchThreadCount>0 时包装 buildLoader1）
- **证据片段**:
  ```java
  // AsyncFetchPartitionDispatchLoaderProvider.setup —— fetch 线程
  executor.execute(() -> {
      try {
          while (!context.isCancelled() && !queue.isFinished()) {
              IBatchChunkContext ctx = context.newChunkContext();   // ← 一次性 ctx，之后无人 complete/completeExceptionally
              ctx.setConcurrency(fetchThreadCount);
              ctx.setThreadIndex(threadIndex);
              synchronized (fetchMutex) {
                  List<S> list = loader.load(loadBatchSize, ctx);   // ← 底层 loader 的 chunk 回调注册在这个 ctx 上
                  ...
  ```
  ```java
  // ResourceRecordLoaderProvider.setup —— 返回的 loader lambda
  return (batchSize, ctx) -> {
      ctx.onAfterComplete(err -> {                    // ← 消费线程路径下 ctx=消费 chunk（会 complete）✓
          synchronized (state) { onChunkEnd(ctx, err, state); }   //    fetch 线程路径下 ctx=fetch 一次性 ctx ✗
      });
      synchronized (state) { return load(batchSize, state, ctx); }
  };
  ```
- **严重程度**: P2
- **并发场景**: DSL 任务同时配置 `<file-reader saveState="true">`（或任务级 saveState）与 `<dispatcher fetchThreadCount>0`。xdef（nop/schema/task/batch.xdef:76-85）中 saveState 与 dispatcher 无互斥约束，组合 schema 合法、构建期无守卫（ModelBasedBatchTaskBuilderFactory:191-192 与 :250-252 独立接线）。fetch 线程数≥1 即触发，与 concurrency 无关。
- **触发时序**: T1：fetch 线程调用底层 loader.load，`ResourceRecordLoaderProvider` 的 lambda 在 **fetch 线程自建的 `BatchChunkContextImpl`** 上注册 chunk 级 afterComplete 压实回调（onChunkEnd：标记 processingItems=true、压实推进 completedIndex、notifyAll）。T2：该 ctx 在本轮循环后即被丢弃，**任何代码路径都不会对其调用 complete/completeExceptionally**（`BatchChunkContextImpl` 的回调列表为己方所有，ExecutionContextImpl.complete 仅被 processChunk 对**消费线程**的 chunk ctx 调用）→ 压实回调永不执行。T3：消费线程的 loader 调用走 dispatch 包装 resultLoader（AsyncFetch:88-112），仅在消费 ctx 上注册 `queue.completeBatch`，底层压实回调无从挂接——对照同步 `PartitionDispatchLoaderProvider:70`，其 fetcher 闭包 `() -> loader.load(loadBatchSize, ctx)` 捕获**消费线程** ctx，压实正常工作（同族对照证明缺陷为 AsyncFetch 专有）。T4：读入的每一行使 `processingItems` 永久滞留 false（load:336 放入、onChunkEnd 才能标记完成）：行数 ≤ maxProcessingItems(50000) 时任务读完，任务结束 `fireBeforeComplete` 触发 :221-227 的完整性检查 `processingItems.size()>0` → 必抛 `ERR_BATCH_PROCESSING_ITEMS_NOT_EMPTY` → saveTaskState(true, e) → **任务必然以失败终态**；行数 >50000 时 `waitUntilUnderLimit` 阻塞 `overLimitWaitMillis`(60s) 后抛 `ERR_BATCH_TOO_MANY_PROCESSING_ITEMS` → fetch 线程失败退出 → 任务失败。completedIndex 全程为 0，断点续传完全失效。
- **现状**: 新发现（待复核）。资源状态机（processingItems/completedIndex）的清理出口在 async 派发下断裂；失败响亮、无静默数据丢失（completedIndex 不推进 → 重启按 0 重放，at-least-once 声明边界内），故 P2 而非 P1。
- **风险**: 功能级：该组合下任务 100% 失败且错误信息（processing items not empty）不指向真实根因（dispatcher 组合），排障成本高；运维面：60 秒阻塞后才失败，任务表现为长时间假死。
- **建议**: 三选一：(1) 构建期守卫——ModelBasedBatchTaskBuilderFactory 在 saveState 且 fetchThreadCount>0 时显式报错（最小修复，与 validateConsumers:221-236 同型）；(2) fetch 线程循环末尾对一次性 ctx 调用 completeExceptionally(EOF 标记) 或直接复用不带 chunk 上下文的 load 路径，把压实职责移交给消费 chunk（消费 ctx 已含 items 与 rowNumber，压实本可在消费侧完成）；(3) 文档声明组合非法并在 xdef 加互斥断言。修复后补「saveState + AsyncFetch + 全量读毕」回归测试（现有 TestAsyncFetchLoaderFailure 仅覆盖 Exception 传播）。
- **失效面枚举**（F3，chunk 级 afterComplete 全部注册点在三派发形态下的触达性）：① 无 dispatcher——消费 ctx 直达底层 loader，压实正常 ✓；② 同步 PartitionDispatchLoaderProvider:70——fetcher 捕获消费 ctx，压实正常 ✓；③ AsyncFetchPartitionDispatchLoaderProvider:125——注册在 fetch 一次性 ctx，**永不触发（本条）**。chunk 级 completeBatch 回调三形态均注册在消费 ctx，正常 ✓。
- **信心水平**: 确定（回调注册线程、ctx 生命周期、complete 调用点全集均为纯代码可见；无时序竞态依赖）
- **误报排除**: 非消费端问题——消费线程的 ctx 正常 complete；非 saveState 单独缺陷——无 dispatcher 时同一 Provider 工作正常（测试 TestBatchTaskCheckpoint 覆盖）；非「配置不应如此」可豁免——schema 未禁止且构建期无守卫，静默接受非法组合属模块责任。
- **复核状态**: 已复核（独立复核）——**保留 P2**。fetch 一次性 ctx（AsyncFetch:119）注册 chunk 压实回调（ResourceRecordLoaderProvider:167-171）且无人 complete 核实；对照同步 PartitionDispatchLoaderProvider:70 fetcher 闭包捕获消费 ctx 正常工作核实；xdef 无互斥（batch.xdef:10/76-85）+ 构建期无守卫（validateConsumers:221 仅查 consumer）核实；ERR_BATCH_PROCESSING_ITEMS_NOT_EMPTY 终态检查（:221-227）核实。维持 P2。现状：新发现（已知未修复）。

---

### [nop-batch-C6-009] onTaskComplete 经 allOf().whenComplete 调用时 saveTaskState 的终态写失败被静默吞没：context/future 已终态但任务行永久 RUNNING，失败事实无任何日志

- **文件**： `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:204-219`（err≠null 分支与外层 finally）、`:148-150`（whenComplete 调用点）、`io.nop.api.core.util.FutureHelper:105-111`（complete 语义）
- **证据片段**:
  ```java
  if (err != null) {
      try {
          if (stateStore != null) {
              stateStore.saveTaskState(true, err, context);   // ← 此处抛出新异常 X
          }
      } finally {
          context.completeExceptionally(err);                 // ← finally 保证执行：context 终态、资源关闭 ✓
      }                                                        // ← 但 X 穿透 finally 继续逃逸，无人捕获无人记录
  }
  ...
  } finally {
      if (metrics != null)
          metrics.endTask(meter, err == null);                // ← 若此处先抛，FutureHelper.complete 被跳过
      FutureHelper.complete(future, null, err);
  }
  ```
- **严重程度**: P2
- **并发场景**: 任务正常结束路径（全部 chunk future 汇入 `CompletableFuture.allOf(futures).whenComplete` → onTaskComplete）；任务结束瞬间 DB 抖动/锁超时/连接池耗尽使 `DaoBatchStateStore.saveTaskState:253-254` 的 `updateEntityDirectly` 抛出。单任务单事件，无竞态依赖。
- **触发时序**: T1：某 chunk 失败 → fail-fast → allOf 交付 err → onTaskComplete 进入 err≠null 分支。T2：`saveTaskState(true, err)` 抛出 X（如 DB 连接超时）。T3：finally 中 `completeExceptionally(err)` 执行——afterComplete 关闭链触发（reader/writer/连接全部释放）、metrics 已在随后结算、`FutureHelper.complete(future, null, err)` 以**原始业务异常 err** 结算任务 future。T4：X 从 onTaskComplete 逃逸到 whenComplete 回调层——CompletableFuture 的回调异常仅使其**返回的** future 异常完成，而该返回值无人持有、无日志 → **X 静默丢失**。T5：调用方拿到 err（以为是任务失败的全部事实），但任务行的终态写（FAILED/KILLED）从未成功 → 行停留 RUNNING → 重启被启动闸门（ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE）永久拒绝，且没有任何线索表明「终态写失败」这一事件发生过。同清理段孪生缺口：若 T2 未发生而 `metrics.endTask`（S5）先抛，则 `FutureHelper.complete` 被跳过 → future 孤儿 → `execute()` 同步调用方在 syncGet 上永久挂死（context/资源已终态，仅 future 不结算）。第二调用路径的对照表现：经 `executeAsync:136-139` setup 失败路径进入 onTaskComplete 时，X 会替代原始 e 从 executeAsync 同步抛出（调用方看到的是状态写失败而非 setup 根因，可见性错乱）——同一缺陷在两条路径上分别表现为静默与错乱。
- **现状**: 新发现（待复核）。终态化「半程失败」不可观测形态：资源面关闭成功（好于 C6-001/002），但任务行终态与失败事实丢失。
- **风险**: 与 C6-001/002 共享最重后果之一（任务行永久 RUNNING、无 kill 端点、需人工改库），且本条**不需要** Error 级异常或线程池拒绝这类罕见前置——任务结束瞬间的普通 DB 抖动即可触发；运维按 err 重试永远被闸门拒绝。
- **建议**: err≠null 分支将 saveTaskState 包入自身 try-catch（Exception 记 LOG.error("nop.batch.task-final-state-save-fail", X) 并在可尝试时降级重试一次或写入替代审计通道），确保 X 不穿透；外层 finally 调整顺序为 `FutureHelper.complete` 先行、metrics.endTask 套 try-catch（future 结算优先于指标，两者均不得互相阻断）。修复后补「saveTaskState 在终态写时抛异常 → 必有日志 + future 必结算」回归测试（mock IBatchStateStore 注入失败）。
- **失效面枚举**（F3，onTaskComplete 清理段全部「后续步骤可阻断前序步骤」站点）：`onTaskComplete:204-213`（本条 X 逃逸）、`:214-218`（metrics.endTask 阻断 FutureHelper.complete，S5）、`executeAsync:136-139`（X 替换原始 e 逃逸，S6）。三站点同段同修。
- **信心水平**: 确定（CompletableFuture 回调异常不外泄、finally 执行顺序、DaoBatchStateStore 无内部捕获均为代码可见；`saveTaskState` synchronized 但异常路径无兜底已核 :234-255）
- **误报排除**: 非「DB 挂了什么都做不了」——缺陷不是写不进 DB 本身（那不可避免），而是**写失败这一事实被完全吞没**（连日志都没有），使「永久 RUNNING」从可诊断退化为不可诊断；非平台 CompletableFuture 语义误用指控——缺陷在使用方未对终态写这一关键步骤做失败显式化。
- **复核状态**: 已复核（独立复核）——**保留 P2**（标注）。onTaskComplete 清理段结构（:204-219：X 穿透内层 finally 后仍经外层 finally 结算 future）、whenComplete 静默吞没（返回 future 无人持有）、metrics.endTask 先于 FutureHelper.complete（S5）、setup 路径 X 替换原始 e 逃逸（S6）全部核实。维持 P2，标注：任务行永久 RUNNING 后果与 P1 族同重且触发面更宽（终态写是每任务必经步骤、普通 DB 抖动即可命中），但资源面已关闭、future 必以原始 err 结算、调用方 fail-loud——后果窄于 C6-001/002；修复排期建议紧随 P1 族。现状：新发现（已知未修复）。

---

### [nop-batch-C6-010] executeChunkLoop 工作线程 lambda 中 propagateContext/provideTaskContext 位于 try 之外：「catch 守卫起点过晚」型终态化阻断站点，改 catch(Throwable) 无法闭合

- **文件**： `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:228-232`（关键结构）、`ContextProvider.java:157-167`（runWithContext 契约：lambda 异常直接穿透到执行线程）
- **证据片段**:
  ```java
  ContextProvider.runWithContext(ctx -> {
      propagateContext(ctx, context);                    // :229 —— try 之前，可抛出
      BatchTaskGlobals.provideTaskContext(context);      // :231 —— try 之前
      try {                                              // :232 —— 守卫从这里才开始
          do { ... processChunk(...) ... } while (true);
          future.complete(null);
      } catch (Exception e) {                            // :243 —— 第 1 轮 C6-001 指出的 catch 类型问题
          ...
          future.completeExceptionally(e);
          ...
      } finally { BatchTaskGlobals.removeTaskContext(); }
      return null;
  });
  ```
- **严重程度**: P2（Error 级后果与 C6-001 同重；Exception 级可达性低——propagateContext 内部为 context 属性复制/关闭态检查，抛 Exception 需平台 context 传播实现异常或关闭竞态）
- **并发场景**: 任一 chunk 循环工作线程（cachedThreadPool 或注入池）在进入主循环之前的初始化段抛出 Throwable。propagateContext 链路：`svcCtx.getContext().isClosed()` 检查（:267-277）→ `ContextProvider.propagateContext(ctx, baseCtx, true)`（:284，复制 tenant/user/traceId/RPC header 传播）；`runWithContext` 的 `getOrCreateContext()`（ContextProvider:159，位于其自身 try 之外）同窗口。
- **触发时序**: T1：工作线程执行 lambda，:229 propagateContext 抛出 Throwable X。T2：X 不经过 :243 的 catch（守卫未开始），沿 runWithContext（其 finally 仅恢复/关闭 context）穿透到 `executor.execute` 的 Runnable 边界 → 线程未捕获处理器（无 batch 错误码日志）。T3：`future.complete(null)/completeExceptionally` 均未执行 → 该 future 永不完成 → `allOf` 永不触发 → `onTaskComplete` 永不执行 → 与 C6-001 完全相同的三重后果：afterComplete 关闭链永不触发（reader/writer/JDBC 连接泄漏）+ 任务行永久 RUNNING + 同步调用方挂死。**结构性要点**：即便按 C6-001 建议把 :243 改为 catch(Throwable)，S3 仍不闭合——X 在 try 之前抛出，守卫位置本身错误；必须将 try 上移至 lambda 首行（propagateContext/provideTaskContext 纳入守卫）才能闭合。
- **现状**: 新发现（待复核）。C6-001 失效面扩展的第 7 站点，且为「守卫起点」新类别（第 1 轮失效面 1-6 均为 catch 类型/位置在 try 内部的问题）。
- **风险**: 与 C6-001 同后果族；修复方案若只采纳「catch Exception→Throwable」则留下本站点为残余漏洞，属「修复实例不修类别」高危形态。
- **建议**: 将 :232 的 try 上移至 :228（lambda 体内首行），使 propagateContext/provideTaskContext/runWithContext 创建段全部纳入守卫；catch 按 C6-001 建议放宽为 Throwable。与 C6-001 合并为同一 diff；回归测试复用 C6-001 建议的「注入 Error」测试并将注入点移至 propagateContext（如以抛 Error 的 IServiceContext stub 触发）。
- **失效面枚举**（F3，executeChunkLoop lambda 全部 try 前代码）：`LOG.info`（:227，参数求值不可能抛，豁免）、`propagateContext`（:229，本条）、`provideTaskContext`（:231，ThreadLocal.set 不可能抛，豁免）、`runWithContext` 的 context 创建（ContextProvider:158-159，同窗口并入本条）。守卫起点修正后全闭合。
- **信心水平**: 确定（结构为代码可见；Exception 级具体触发器未能具体化——propagateContext 的 Exception 可达性依赖平台 context 实现的失败模式，按纪律如实标注：Error 级具体、Exception 级理论）
- **误报排除**: 非重复立案——C6-001 的站点是 catch(Exception) 类型不足且限于 try 内路径；本条证明存在 try 之外的逃逸点，修复集合不同；非「原语使用不是问题」——守卫起点决定完成语义能否达成。
- **复核状态**: 已复核（独立复核）——**保留 P2**。propagateContext :229 / provideTaskContext :231 在 try :232 之前核实；`ContextProvider.runWithContext` 自身无 catch 且 `getOrCreateContext()` 在其 try 之外（ContextProvider:157-167，finally 仅恢复/关闭 context）核实——**「改 catch(Throwable) 无法闭合该站点、必须 try 上移」的结构性论断成立**。与 C6-001 同 diff 修复（回归测试注入点移至 propagateContext）。维持 P2（Error 级后果同 C6-001；Exception 级可达性低，初审已如实标注）。现状：新发现（已知未修复）。

---

### [nop-batch-C6-011] JdbcBatchLoaderProvider 在 setup 线程持有事务绑定连接（closeConnection=false）跨越事务生命周期：异步用法下事务终结后继续在已归还池的连接上读取（当前仓内调用面同步等待，理论风险）

- **文件**： `nop-batch/nop-batch-jdbc/src/main/java/io/nop/batch/jdbc/loader/JdbcBatchLoaderProvider.java:225-230`（借用决策）、`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:124-129`（currentConnection 语义）
- **证据片段**:
  ```java
  boolean closeConnection = false;
  Connection connection = jdbcTemplate.currentConnection(querySpace);  // setup 线程的已注册事务连接
  if (connection == null) {
      connection = jdbcTemplate.openConnection(querySpace);
      closeConnection = true;
  }
  ...
  ResultSet rs = ps.executeQuery();        // 在该连接上打开游标，LoaderState 持有至任务结束
  ```
- **严重程度**: P3「理论风险，未具体化（仓内当前无调用方进入该时序）」
- **并发场景**: 需要「事务内调用 executeAsync 且返回的 future 不在事务内存续期内等待」——setup 在调用线程执行时 `currentConnection` 返回该线程注册事务的连接；调用方方法返回 → 事务提交/回滚 → 连接归还池；chunk 线程随后继续 `load()` 从已归还池的连接读取（与后续借用者并发使用同一连接 + 在已终结事务的连接上继续读游标）。
- **触发时序**: T1：调用线程在注册事务内调用 `executeAsync`（如 BatchTaskRunner.executeAsync 的 CompletionStage 被异步消费）。T2：setup 借得事务绑定连接（closeConnection=false），executeQuery 打开流式游标。T3：调用线程方法返回，事务边界提交 → 连接回池。T4：chunk 线程调用 load → `RecordInputImpls.defaultReadBatch(state.dataSet,...)` 在池化连接上继续 fetch → 该连接可能已同时借给其他请求（跨线程并发使用）或被池 reset，读取结果不可信/协议错乱。**可达性核验**：仓内现有调用方 `IBatchTask.runTask`（default → syncGet）、`ImportDbTool:350`/`ExportDbTool:311`（task.execute 同步）、`DefaultBizEntityExporter:60`（executeAsync 但 loader 为 ORM/GraphQL 无 JDBC 连接持有）均不进入该时序；时序成立需要未来的异步 JDBC-reader 调用方，故按纪律标 P3 理论风险。
- **现状**: 新发现（待复核）。结构性契约缺口：setup 时按「setup 线程当前事务」决策连接归属，但连接被持有到任务结束，两个生命周期不一致。
- **风险**: 一旦出现异步 JDBC-reader 调用方即成为池连接交叉使用的高危缺陷（数据错乱+并发协议错误），且无任何守卫或告警。
- **建议**: 二选一：(1) JdbcBatchLoaderProvider 放弃 currentConnection 借用，一律 openConnection 自持（查询侧不参与调用方事务是安全默认，且与 chunk 事务边界解耦）；(2) 保留借用但在 LoaderState 记录来源事务并在 afterComplete 校验事务仍存活，不存活则记 WARN。同时 owner doc（docs-for-ai/03-modules/nop-batch.md）声明 jdbc-reader 与环境事务的关系。
- **失效面枚举**（F3，事务绑定连接跨生命周期的借用点）：`JdbcBatchLoaderProvider.newState:226`（本条，nop-batch 内唯一 currentConnection 借用点，grep 核验）；`JdbcInsertBatchConsumer`/`JdbcUpdateBatchConsumer`/`JdbcKeyDuplicateFilter` 均为调用期借还（runWithConnection/findAll），无跨期持有，豁免。
- **信心水平**: 确定（借用语义、持有跨度、仓内调用面均为代码可见；「理论风险」标注针对触发调用方当前不存在）
- **误报排除**: 非同步用法指控——同步等待时任务结束先于事务终结，窗口不存在；非平台连接池问题——持有决策在本模块。
- **复核状态**: 已复核（独立复核）——**保留 P3 理论风险**。currentConnection:226 借用决策与 LoaderState 持有至任务结束核实；仓内调用面（ImportDbTool:350/ExportDbTool runTask 均同步 execute、DefaultBizEntityExporter executeAsync 但 loader 为 ORM/GraphQL 无 JDBC 连接持有）核实——触发调用方当前不存在。维持 P3 理论。现状：新发现（已知未修复）。

---

## 七、维度集外线索

### [nop-batch-CL-033]（维度集外线索，不立案展开）
- 形态归属：C8（数据完整性/at-least-once 边界）。
- 证据：`RetryBatchLoader.java:43-45,61-68` + `JdbcBatchLoaderProvider.load:285-295` / `ResourceRecordLoaderProvider.loadItems:394-399` —— load 重试在**同一 chunk、同一游标**上进行：`readBatch` 已从游标读出但尚未返回的行随异常丢弃，重试从游标当前位置继续 → 中断窗口内的行被静默跳过。已声明的 at-least-once 仅覆盖 consume 失败重放（completedIndex 机制），不覆盖 load 侧部分读失败。无 dispatcher 时底层 load 失败直接任务失败（无游标继续问题，文件 reader 重启重读）故窗口限于 loadRetryPolicy 配置 + 流式/顺序游标 reader。
- 严重度候选：P2（配置可达、静默丢数）。
- **复核处置（独立复核）**: **确认**。RetryBatchLoader.load（:39-68）同 chunk 同游标重试核实；readBatch 部分读出后异常时已消费行随异常丢弃、重试自游标当前位置继续的机制成立。P2 候选维持（触发面=loadRetryPolicy 配置 + 流式/顺序游标 reader，与初审一致）。

### [nop-batch-CL-034]（维度集外线索，不立案展开）
- 形态归属：C1（异常传播/监听器语义，F9 超集登记）。
- 证据：`BatchChunkProcessor.loadItems:67-76` —— `fireLoadEnd(ctx, null)`（:71）中某 onLoadEnd 监听器抛出 X → catch（:74）→ `fireLoadEnd(ctx, X)`（:75）**再次触发全部监听器**：非抛出监听器被以 null 与 X 各调用一次（双调用），抛出监听器被以自身异常再次调用。依赖「恰好一次」语义的监听器（计数、一次性释放）被破坏；对照 fireChunkEnd（吞）/fireAfterComplete（吞）/fireTaskBegin（传播但列表置空）——同一注册表中四种 fire 语义互不一致。
- 严重度候选：P3（需监听器有副作用语义，DSL 内置监听器无此用法）。
- **复核处置（独立复核）**: **确认**。fireLoadEnd 双触发（BatchChunkProcessor.loadItems：:71 以 null 触发 → 某监听器抛 X → catch :74 → :75 以 X 重触发全部监听器）核实。P3 候选维持。

### [nop-batch-CL-035]（维度集外线索，不立案展开）
- 形态归属：C1（初始化原子性）。
- 证据：`BatchTaskContextImpl.fireTaskBegin:518-530` —— 无 per-callback 防护且**先置空 onTaskBegins 列表再逐个 run**：第 N 个回调抛异常 → 前 N-1 已执行、后续全部跳过且列表已置空不可重放；位于异常回调之后的分配类 initializer 永不执行。executeAsync catch(Exception) → onTaskComplete → completeExceptionally 仍会关闭**已注册**的 afterComplete 资源（Exception 级无泄漏），后果限于「部分 begin 回调缺失」的半初始化状态。Error 级并入 C6-001 族。
- 严重度候选：P3。
- **复核处置（独立复核）**: **确认**。fireTaskBegin 先置空 onTaskBegins 列表再逐个 run、无 per-callback 防护（BatchTaskContextImpl:518-530）核实；半初始化后果限于 begin 型回调缺失，afterComplete 关闭链完整（Exception 级）。P3 候选维持（Error 级并入 C6-001 族）。

---

## 八、检查通过项（本轮新增核验口径，无发现）

1. **cancel 路径三路闭环**（S8）：loop 顶 BatchCancelException → completeExceptionally → allOf → onTaskComplete → `getTaskStatus` 按 cancelReason 映射 KILLED/CANCELLED/SUSPENDED（DaoBatchStateStore:257-274）→ future/afterComplete/任务行全结算 ✓。`Cancellable.cancel` CAS 单赢家 + 回调 Throwable 防护（nop-commons:40-51,107-124）✓。
2. **BlockingSourceBatchLoader 取消语义**：ctx/task 双重 isCancelled 检查，STOP→空列表（fail-fast 静默退出，配合失败兄弟 future 的异常完成语义一致）、SUSPEND/SKIP→BatchCancelException ✓。
3. **LimitRecordInput/DelegateRecordInput 关闭委托**：`input.limit()` 包装不破坏 safeCloseObject(state.input) 的关闭链（DelegateRecordInput.close→input.close）✓（反虚构核验：曾怀疑 limit 包装导致底层句柄泄漏，已排除）。
4. **SplitBatchConsumer 懒 setup 的资源配对**：每 tag 首次消费时 setup 打开输出并注册任务级 afterComplete + onBeforeComplete，与 eager writer 共享同一关闭链；`getConsumer` synchronized 防并发重复 setup ✓。
5. **transactionScope 四档连接波及**：chunk rollback 不波及 reader（线程绑定事务、reader 连接来自 setup 线程）——除 C6-011 的异步窗口外全部组合核验通过；history 自动提升的 Invoker 位置保证 saveProcessed 在事务内 ✓。
6. **fetch 线程退出路径**：EOF/Exception/cancel/finish 四路均经 finally exitFetchThread；acquirePermits 500ms 轮询 finished 有界退出；任务终态后无永久泄漏 ✓（Error 静默截断归 C6-001 面 4）。
7. **ExecutorService 优雅关闭族封闭**：nop-batch 内 IThreadPoolExecutor/ExecutorService 使用点穷尽为 exp 两工具（已立案），无第三成员 ✓。
8. **EtlTaskStateStore 单监视器**：全部变更方法 synchronized(this)，saveStateFile 写-原子替换配对，无长持句柄 ✓。
9. **BatchGenLoaderProvider**：虽继承 AbstractBatchResourceHandler 但无流资源，parseFromResource 自闭合 ✓。
10. **DefaultBizEntityExporter future 链**：executeAsync→thenApply→whenComplete 清理临时文件，异常路径 err!=null 即删 ✓（loader 为 GraphQL/ORM 无连接持有，C6-011 不适用）。

## 九、勘误（对第 1 轮报告）

1. C6-003「Import 侧自建 HikariDataSource 已由 needClose 在 finally 关闭，对称性完好」——**不成立**：needClose 的 finally 位于 :242 起的 try，而池创建于 :226 的 syncConfigWithDb（try 之前），该窗口失败时池泄漏 → 见 C6-007。Export 侧结论维持。
2. 第 1 轮「检查通过项 11 / 维度第 3 步」中「12 种 listener 注册点与触发点全量核对通过」——补勘误：`BatchTaskContextImpl.onTaskEnds`（:70）为无注册方法、无触发点的死字段；`AbstractBatchResourceHandler.onTaskEnd` 为空实现且零调用；真实任务结束回调通路为 BatchTaskGlobals.onTaskEnd→onAfterComplete 与 DSL onTaskEnd listener，均正常。无运行时后果（见四）。

---

## 十、总计与终止判定

| 类别 | 编号 | 一句话 | 严重度 |
|------|------|--------|--------|
| 发现 | nop-batch-C6-007 | ImportDbTool 的 HikariDataSource 建于 try 保护窗口外，syncConfigWithDb 失败即整池泄漏（修正 C6-003 对 Import 侧结论） | P2 |
| 发现 | nop-batch-C6-008 | saveState + AsyncFetch 组合：chunk 压实回调挂在 fetch 一次性 ctx 永不触发，completedIndex 不推进、任务必然失败 | P2 |
| 发现 | nop-batch-C6-009 | onTaskComplete 经 whenComplete 调用时 saveTaskState 终态写失败被静默吞没：行永久 RUNNING 且失败事实无日志 | P2 |
| 发现 | nop-batch-C6-010 | executeChunkLoop 的 try 守卫起点过晚（propagateContext/provideTaskContext 在 try 之外）：改 catch(Throwable) 亦无法闭合的终态化阻断站点 | P2 |
| 发现 | nop-batch-C6-011 | JdbcBatchLoaderProvider 持事务绑定连接跨越事务生命周期（异步用法池连接交叉使用；当前调用面同步等待） | P3 理论风险 |
| 线索 | nop-batch-CL-033 | load 侧部分读失败重试静默跳行（at-least-once 不覆盖 load 窗口，C8） | P2 候选 |
| 线索 | nop-batch-CL-034 | fireLoadEnd 异常路径双触发监听器（C1/F9 超集） | P3 候选 |
| 线索 | nop-batch-CL-035 | fireTaskBegin 先置空后执行、无防护：部分 begin 回调缺失不可重放（C1） | P3 候选 |

**本轮发现 5 条（P2×4、P3×1）+ 维度集外线索 3 条（CL-033~035）。无新增 P0/P1。**

**终止信号判定：建议终止，无需第 3 轮。** 依据：(a) 本轮全部新发现为 P2/P3，无 P0/P1（深挖继续规则的终止条件满足）；(b) 目标① 的终态化阻断站点表已闭全——S1~S10 十个站点覆盖 executeAsync/executeChunkLoop/onTaskComplete/whenComplete/cancel/saveTaskState 全部终态化路径，S3/S4/S5/S6 四个新站点与第 1 轮 C6-001/002 共享同一修复族（try 上移 + catch Throwable + 清理段显式化），无第 4 类新形态；(c) 目标② 的 F3 关闭出口枚举已穷尽（16 行主表 + afterComplete 单一出口 4+3 处逐一核对，LimitRecordInput 委托、SplitBatchConsumer 懒 setup 两个候选疑点经反虚构核验排除）；(d) 目标⑤ 的 ExecutorService 关闭族在 nop-batch 内确认封闭（无第三成员），fetch 线程四路退出 + 有界阻塞核验通过；(e) 目标③④ 的剩余线索（CL-033~035）均属 C1/C8 域且已完整登记证据链，C6 维度内无未闭合的资源持有点。
