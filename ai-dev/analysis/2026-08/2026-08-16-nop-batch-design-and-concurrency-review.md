# nop-batch 功能完整性 + 并发/安全缺陷深度审查

> Status: resolved
> Date: 2026-08-16
> Scope: nop-batch 全模块（nop-batch-core / nop-batch-dao / nop-batch-dsl / nop-batch-exp / nop-batch-orm），涉及 ORM 层（nop-persistence）仅作证据核查
> Conclusion: 经 4 个独立子 agent 两轮交叉核查达成共识。确认 3 个 copy-paste 类 bug + 2 个数据丢失路径（P0 级）+ 多个并发/设计缺陷。修复建议拆分至后续 plan。

## Context

- 问题：nop-batch 的功能设计是否完整？并发性、安全性上是否存在微妙 bug？
- 方法：逐文件精读核心执行引擎、retry/skip 链、partition dispatch、state store/resume、DSL builder；每条发现经 git blame 溯源、独立子 agent 交叉核查（两轮，见 References）。
- 约束：仅分析不改码。

## Analysis

### 一、P0：已确认的代码 Bug（copy-paste 级）

#### Bug 1: `setHistoryItemCount`/`incHistoryItemCount` 写错计数器

- 位置：`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTaskContextImpl.java:307-314`
- 现象（已复核行号）：
  ```java
  public void setHistoryItemCount(long count) { processItemCount.set(count); }      // 应为 historyItemCount.set
  public void incHistoryItemCount(int count)  { processItemCount.addAndGet(count); } // 应为 historyItemCount.addAndGet
  ```
  `getHistoryItemCount` 读 `historyItemCount`（:302-304），读写不一致。
- 影响：
  - `historyItemCount` 恒为 0；`processItemCount` 被双重累加（与 `incProcessItemCount` 叠加）。
  - `BatchTask.incCount`（BatchTask.java:331）的 `completeCount - historyCount` 失真。
  - `EtlTaskStateStore.saveTableState:110` 持久化 historyCount 恒 0；`loadTableState:87` 恢复时把 history 写进 process 计数。
  - **跨 resume 累积膨胀**：`processItemCount` 被双重累加后经 `saveTableState:112` 持久化为 processedCount，下次 resume（:94-95）恢复回来 → 计数逐轮膨胀。
- 溯源：git blame = commit `daa0f8e27`（2025-12-21，"增加batch幂等处理检查"）。
- 验证：无测试覆盖 `getHistoryItemCount`；4 个审计 agent 中 3 个确认（1 个对 inc 行判定有误，已按源码直接复核纠正）。

#### Bug 2: `onConsumeEnd` 注册到 `onChunkTryEnd` 列表

- 位置：`BatchTaskContextImpl.java:465-473`
- 现象：`onConsumeEnd` 把 action 加入 `onChunkTryEnd`；`fireConsumeEnd`（:600-607）读 `onConsumeEnd` 列表（恒空）。
- 影响：
  - DSL `onConsumeEnd` 监听器（`ModelBasedBatchTaskBuilderFactory.java:582-588`）永不触发；
  - 配置了 retryPolicy 时会在每次 chunkTry 重试时刻被错误触发（语义漂移）；
  - 未配置 retryPolicy 时（`fireChunkTryEnd` 只在 `RetryConsumeHelper.retryConsume` 中调用）**完全不会触发**——两个错误叠加。
- 溯源：git blame = commit `f10b3f7f7b`（2024-12-10）。
- 关联：`fireChunkTryBegin`/`fireChunkTryEnd` 仅被 `RetryConsumeHelper` 调用，无 retryPolicy 时 DSL 的 `onChunkTryBegin/End` 监听器同样是死代码（审计 agent A 补充）。

#### Bug 3: `ResourceRecordLoaderProvider.skip()` aggregator 模式下双倍消费 —— 会造成 resume 静默丢数据

- 位置：`ResourceRecordLoaderProvider.java:233-248`
- 现象：
  ```java
  S item = input.next();
  if (filter != null && filter.accept(item, state.context)) continue;
  aggregator.aggregate(input.next(), state.combinedValue); // 每轮循环消费两条
  ```
- 影响（审计 agent B 升级，比"聚合错乱"更严重）：
  1. **resume 静默丢数据**：skipCount=N 时实际消费原始行 1..2N，load 从 2N+1 开始 → 行 N+1..2N 从未交给 processor，无任何计数暴露；
  2. **文件剩余 ≤ N 时整段丢失**：skip 耗尽输入 → load 返回空 → 任务"成功"结束且 completedIndex 不更新 → 尾部记录无感丢失；
  3. **奇数剩余时启动异常**：最后一次 `aggregate(input.next())` 对耗尽输入再取，真实实现抛 `IllegalStateException("no more records")`（`JsonlRecordInput.java:93-95`）；
  4. aggregator 统计双重错乱（偶数行被聚合且错位）。
- 溯源：L243 的 `input.next()` 自 2023-03-07 原始提交（ef5086e2eb）即存在，长期潜伏。
- 正确写法：`aggregator.aggregate(item, ...)`。
- 验证：`TestRecordLoader` 未配置 aggregator，无覆盖。

### 二、P0/P1：数据丢失路径与状态机缺陷

#### 缺陷 4（P0）：chunk 失败不通知兄弟线程（fail-fast 缺失）→ 阻塞 loader 下 FAILED 永不落库

- 位置：`BatchTask.processChunk` 错误路径（BatchTask.java:294-314）。
- 现象：
  - 失败路径只 `chunkContext.completeExceptionally(e)`，全模块 grep 确认无任何对 taskContext 的 `cancel()` 调用（唯一 `.cancel()` 在 `BatchProcessorConsumer.java:92`，且是 chunk 级、仅异步超时路径）；
  - `Cancellable.isCancelled`（`nop-kernel/nop-commons/.../Cancellable.java:36-43`）仅在显式 cancel 时置位；`ExecutionContextImpl.completeExceptionally`（nop-kernel/nop-core/...:121-127）不影响 cancelled；
  - `allOf(...).whenComplete`（BatchTask.java:140-142）等待所有线程结束才回调 `onTaskComplete`。
- 影响（agent C 确认并修正影响面）：
  - 有限 loader：兄弟线程继续消费并提交写入剩余全部数据（每个成功 chunk 仍 `saveTaskState(false,...)`，DB 记录恒为 RUNNING 误导监控与 `loadTaskState0:73` 的互斥检查），直到数据耗尽；
  - **阻塞/轮询 loader（BlockingSourceBatchLoader）**：兄弟线程永久阻塞在 load 内 → allOf 永不完成 → FAILED 永不落库 + 线程泄漏；
  - 缺陷仅在 `concurrency > 1` 时显现。
- 建议：任一 chunk 失败 → `taskContext.cancel(CANCEL_REASON_FAIL)` + 立即保存失败状态。

#### 缺陷 5（P0，潜伏型）：`BatchCancelException` 状态映射被 `CompletionException` 包装打断（取消/挂起状态永不落库）

- 位置：`DaoBatchStateStore.getTaskStatus`（:180-197）+ `BatchTask.java:140-142`。
- 现象：`allOf.whenComplete` 交付的 err 必然是 `CompletionException`（包装底层异常），`err instanceof BatchCancelException` 恒为 false。
- 影响（round-2 审计精确化）：
  - 主执行路径（chunk loop）上 SUSPENDED/CANCELLED/KILLED 三分支**完全不可达**——即使外部 `taskContext.cancel(SKIP/SUSPEND)`，落库仍是 FAILED；
  - SUSPENDED/CANCELLED 子分支更是**无条件死代码**：`CANCEL_REASON_SUSPEND`/`CANCEL_REASON_SKIP` 全仓库无任何写点；
  - setup 失败路径（BatchTask.java:128-131）传原始异常、结构上可达，但现实无触发者；
  - 当前全仓库**不存在任何外部 cancel 入口**（BatchTaskGlobals 无 cancel API，NopBatchTaskBizModel 是裸 CRUD）→ 属"潜伏缺陷"：一旦接线 kill 按钮/任务平台取消，直接命中错误状态。
- 建议：在 `BatchTask.onTaskComplete` 内、`stateStore.saveTaskState` 之前 unwrap `CompletionException`（仅入库判定用解包值；`completeExceptionally`/future 完成保留原值）；修复后需配套 cancel 入口 + 回归测试（cancel 后断言落库 KILLED）。

#### 缺陷 6（P0）：失败 chunk 的行会被兄弟成功 chunk 压实越过 → resume 数据丢失（at-most-once）

- 位置：`ResourceRecordLoaderProvider.onChunkEnd`（:250-295）。
- 现象（round-2 审计逐环节确认，完整时序推演见 audit 记录）：
  - loader 通过 `ctx.onAfterComplete`（:165-169）注册回调，chunk 失败时 `completeExceptionally` **同步**触发（`ExecutionContextImpl.java:121-127` → fireAfterComplete 在调用线程内联执行）；
  - :263 无条件 `put(rowNumber, true)`——失败 chunk 的行仍被标记 done；压实循环（:270-280）只看 value 不看 exception → 失败 chunk 自己的 onChunkEnd 已把其行**物理移出 TreeMap**；
  - `exception == null` gate（:288）只阻止失败 chunk 本次 setCompletedIndex，保护不了后续；
  - 兄弟成功 chunk 压实越过已失踪的失败行 → `setCompletedIndex` → 经 per-chunk save（BatchTask.java:288-289，读的是已越界索引）与失败收尾 save（:183）**两条路径都落盘**；
  - 重启后 `getSkipCount`（:223-231）按越界索引跳行 → **事务已回滚、未落库的记录永远不会被重处理 → 数据丢失**。
- 边界条件：失败 chunk 需为 map 头部（最早未完成区间）才触发越界——这是主导交错场景（前序兄弟通常已压实）。
- 阻断机制核查：`onBeforeComplete` 的 "processingItems must be empty" 检查只被 err==null 路径触发（BatchTask.java:167），失败路径不调 fireBeforeComplete → 无阻断。
- 即：单线程失败路径下"at-least-once + 保守"成立；并发失败路径退化为 at-most-once。
- 附带确认：per-chunk save 发生在 `chunkContext.complete()`（:291）之前 → 盘上索引恒滞后一个 chunk（成功路径无害，最终 save 补齐；失败路径反而是越界落盘的放大器）。
- 建议：失败 chunk 的行不标记 done（或标记后由失败路径回滚），压实前检查 chunk 失败标记。

#### 缺陷 7（P1）：记录级幂等（historyStore → NopBatchRecordResult）写路径未实现 —— 形同虚设

- 位置：`nop-batch-dao/.../history/DaoBatchRecordHistoryStore.saveProcessed`（:76-79，空方法）。
- 现象（round-2 审计确认）：
  - `filterProcessed`（:34-53）查询 `NopBatchRecordResult` 中 resultStatus=0 的记录——**全仓库无任何写入方**（93 处 grep 穷举：唯一消费者是此查询；BizModel 是管理端 CRUD 不在执行链路；sql-lib/deploy 无 INSERT）；
  - 结论：`nop_batch_record_result` 表无生产者，history 去重永远过滤不到任何记录。
- 影响面修正（round-2 审计收窄）：
  - **`JdbcKeyDuplicateFilter` 的空 saveProcessed 不算缺陷**：其 `filterProcessed` 查的是业务目标表的 key 是否已存在，业务写入本身就是 key 落库，去重闭环成立。缺陷仅限 `DaoBatchRecordHistoryStore` / `nop_batch_record_result` 路径；
  - DSL 用户配置 `<historyStore>` 后每个 chunk 执行一次必然为空的查询——纯性能开销、零去重收益；任务重跑时所有记录被重复处理；
  - **文档过度承诺**：`docs-for-ai/03-modules/nop-batch.md:11` 宣称"记录级幂等：NopBatchRecordResult 表追踪每条记录状态"与实际不符（docs bug，应修正）。
- 附带（修正原报告 Q3 的相反结论）：`WithHistoryBatchConsumer`（:57-64）在 consume scope 下位于事务包装**之外**，成功路径 `saveProcessed` 在事务提交**之后**执行（原报告"提交前"说法有误）；process scope 下在事务内（同库原子）。当前空实现使该差异无实际影响；未来实现落盘时 consume scope 的崩溃窗口只导致良性重跑（at-least-once）。
- 次要：`DaoBatchRecordHistoryStore.buildRecordMap`（:56-67）对相同 recordKey 静默覆盖去重。

### 三、P1/P2：并发与设计缺陷

#### 缺陷 8（P1）：`DaoBatchStateStore` 双实例启动竞态（check-then-act）

- 位置：`DaoBatchStateStore.loadTaskState0`（:44-118）。
- 现象：
  - 新任务路径：读无记录 → 双 INSERT（SID 为 seq 生成，无主键冲突；NopBatchTask 表无任何唯一索引/unique 约束，`model/nop-batch.orm.xml` 全文件无 `<index>`）→ 同一 taskKey 双实例并行执行，`ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE` 保护被绕过；
  - 下轮启动 `loadExistingTask`（:128-145）按 execCount desc 取 first，两条同 execCount 记录胜负不确定 → taskKey 幂等语义破坏；
  - 已有任务路径依赖 version 乐观锁兜底（`GenSqlHelper.genUpdateSql` WHERE version=?, 0 行 → `ERR_ORM_UPDATE_ENTITY_NOT_FOUND`），失败形式隐晦。
- 附带：`saveTaskState` 为 `synchronized` 实例方法（:157），store 是 IoC 单例（`app-batch-dao.beans.xml:9`）→ 所有任务共享一把锁 + 每 chunk 一次 REQUIRES_NEW 写库，多任务并发吞吐瓶颈。
- 建议：加 (taskName, taskKey) 唯一索引 + INSERT 冲突兜底；store 级锁改为按 taskKey 分片。

#### 缺陷 9（P2）：PartitionDispatch 跨页乱序 + 隐式线程安全契约

- 位置：`PartitionDispatchQueue.takeBatch`（:123-226）、`PartitionDispatchLoaderProvider`（:69-84）、`AsyncFetchPartitionDispatchLoaderProvider`（:103-129）。
- 现象 1（乱序）：同步模式 N 个消费线程、异步模式 fetchThreadCount 个专职线程并发 `loader.load` + `addBatch`；同一 partition 内记录顺序 = 各页 addBatch 到达顺序，跨页乱序可能。类注释"partitionIndex相同的任务总是顺序被处理"只保证"不并发"，不保证"源序"。
- 现象 2（契约）：fetcher 并发调用要求底层 loader 线程安全；内置 loader（OrmQuery/ResourceRecord/Jdbc/BatchGen/List）均有内部 `synchronized(state)` 兜底，**风险集中在自定义/DSL loader**。DSL `safeLoad` 的 `synchronized(ctx)`（`ModelBasedBatchTaskBuilderFactory.java:421-425`）是 per-chunk 对象锁：同步模式每线程一个 ctx（锁不住跨线程），异步模式每次 load 新建 ctx（同一线程内都锁不到同一对象）→ 完全无效。
- 验证：`TestPartitionDispatchLoader` 用 `Set` 断言只验无重复不验顺序；异步测试 fetchThreadCount=1 未覆盖多 fetch 线程。
- 建议：文档声明 loader 线程安全要求；需要源序时 fetchThreadCount=1；safeLoad 改共享锁。

#### 缺陷 10（P2）：AsyncFetch 取消/结束时 fetch 线程永久阻塞（线程泄漏）

- 位置：`AsyncFetchPartitionDispatchLoaderProvider`（:103-129）+ `PartitionDispatchQueue.addBatch:302`。
- 现象（agent D 新发现）：fetch 线程阻塞在 `semaphore.acquire`，任务 cancel 或正常结束时若队列残留未派发项（permits 未释放、消费者已退出），fetch 线程**永久阻塞**在 cachedThreadPool 线程上，`finally exitFetchThread` 永不执行。重复取消运行 → 线程累积泄漏。
- 建议：addBatch 的 acquire 增加可中断/可取消路径（任务取消时唤醒 fetch 线程）。

#### 缺陷 11（P2）：retry/skip 链与计数口径问题

- 11a：`RetryConsumeHelper.checkRetry`（:19-31）retryPolicy==null 时无限热重试（无 sleep）。builder 层有 null 保护（`BatchTaskBuilder.java:410-417`），仅直接 new `RetryAllBatchConsumer`/`RetryOneByOneBatchConsumer` 传 null 时触发；非 null 但 `getRetryDelay` 恒返 0 的 policy 也会忙循环。建议构造参数非空校验。
- 11b：`RetryConsumeHelper.retryConsume`（:67-68）过滤 completed items 后 `retryItems.isEmpty()` 时静默 return，原始异常被吞掉且无日志（agent A 补充）。
- 11c：`BatchTask.java:300-302` 失败 chunk 的部分完成仍计入 completeItemCount——实际只发生在 retry/skip 链场景（默认配置失败 chunk 贡献 0，agent D 收窄）。
- 11d：`BatchProcessorConsumer`（:64-99）异步 processor 同步抛异常时异常直接冲出 consume()，await 不会执行、不会挂起；超时兜底仅保护"process 正常返回但 countDown 永不发生"的场景（agent D 修正原报告"必现挂起"说法）。失败 item 的 `incProcessItemCount`（:69）已计入，失败也计入计数。

#### 缺陷 12（P2）：次要项

- 12a：`EtlTaskStateStore.saveTableState`（:104-118）每 chunk 全量序列化写整个状态文件（synchronized 串行）→ 多表多任务 IO 放大。
- 12b：`ModelBasedBatchTaskBuilderFactory.limitHash`（:352-356）`Math.abs(Integer.MIN_VALUE)` 仍为负 → 负 partition 索引；`IntHashMap` 支持负 key，功能无实害，仅超出设计区间 [0, 32766]（agent D 降级）。
- 12c：`WithHistoryBatchConsumer.java:40` 用 `List.contains` 计算 history 集合，O(n²)。
- 12d：`PartitionDispatchQueue.removePartition`（:254-268）仓库内无任何调用方（dead code，commit `3ae3b12a5` 引入）；锁外正在 fetcher.get() 的线程随后 addBatch 可经 `computeIfAbsent`"复活"已移除 partition（agent D 补充）。
- 12e：`ResourceRecordLoaderProvider` filter+saveState 组合下行号空间可能错位：completedIndex 统计的是过滤后记录的行号，resume 的 skip 按原始行跳过 → 欠跳 → 重复处理一批已完成的过滤后记录（at-least-once 放大；依赖 `IRecordInput.getReadCount` 语义，待验证，agent B 标注）。

### 四、功能设计完整性评估

已覆盖（成熟度高）：chunk 引擎、并发线程、事务 scope（none/chunk/process/consume）、skip/retry/retryOneByOne + snapshot 恢复、rateLimit、jitter、partition dispatch（同步/异步预取）、记录级幂等（historyStore）、状态持久化（DB/文件）、metrics、DSL + 监听器、async processor、singleSession、导入导出（nop-batch-biz）、ETL 工具（nop-batch-exp）。

缺失/待补充：

| 缺口 | 说明 | 关联 |
|---|---|---|
| 记录级幂等写路径 | DaoBatchRecordHistoryStore.saveProcessed 空实现，NopBatchRecordResult 表无生产者（JdbcKeyDuplicateFilter 不受影响）；`docs-for-ai/03-modules/nop-batch.md:11` 存在过度承诺需修正 | 缺陷 7 |
| 运行时 kill/cancel API | 只有 cancel 标志位 + KILLED 残留处理，无"停止运行中任务"入口；且 cancel 后状态映射还坏了 | 缺陷 5 |
| 失败传播规范 | fail-fast + 兄弟线程取消 + FAILED 即时落库 | 缺陷 4、6 |
| 分布式互斥 | 无唯一索引/分布式锁 | 缺陷 8 |
| 语义文档化 | at-least-once、并发 resume 边界、loader 线程安全契约未成文 | 缺陷 6、9 |
| DB loader 断点续传 | 仅文件 loader 支持行号续传；Orm/Jdbc reader 断点只能重跑全部 | — |
| 任务报告/监控 | metrics 有接口无默认落地；web 侧无专用管理 API（pause/resume/手动触发） | — |
| 测试缺口 | 无多线程+状态恢复+失败注入测试、无 aggregator+resume 测试（Bug 3 逃逸）、无 onConsumeEnd 测试（Bug 2 逃逸）、无 DaoBatchStateStore 集成测试（缺陷 8 逃逸）、无 history 写路径测试（缺陷 7 逃逸） | 全部 |

### 五、修复优先级

- P0（正确性，立即可修 + 回归测试）：Bug 1、Bug 2、Bug 3；缺陷 4（fail-fast）、缺陷 5（CompletionException unwrap + cancel 入口）、缺陷 6（失败行压实回滚）。
- P1（需设计后实施）：缺陷 7（实现 NopBatchRecordResult 写路径 + 事务边界；同步修正 `docs-for-ai/03-modules/nop-batch.md` 的过度承诺）、缺陷 8（启动互斥 + 唯一索引）。
- P2（语义/文档/性能）：缺陷 9、10、11、12。
- 测试补强：多线程 + 失败注入 + resume 集成测试；historyCount/onConsumeEnd/aggregator+resume/取消状态落库单测。

### 六、chunk 失败语义 Q&A 固化（2026-08-16 追加）

**Q1：到达 BatchTask.processChunk catch 块的失败，是经过 retry 和 skip 之后的失败吗？skip 后会报失败吗？**

是（主路径）。消费链包装顺序（BatchTaskBuilder.buildChunkProcessor:410-422）为 Skip 最外、Retry 在内：

```
SkipBatchConsumer → RetryAll/RetryOneByOne → … → 业务 consumer
```

- 消费/处理异常先由 retryPolicy 重试，耗尽（getRetryDelay < 0）才抛出；
- 抛出后由 SkipBatchConsumer 判定 shouldSkip（SkipConsumeHelper.java:33-47）：可跳过 → **吞掉异常**，仅 incSkipItemCount，chunk 按成功路径正常完成（fireChunkEnd(null)、saveTaskState(false, null)）——**skip 吞掉的失败不会导致 chunk 失败**；
- 因此到达 processChunk catch 的失败 = retry 耗尽 **且** skip 拒绝（无 skipPolicy / 超 maxSkipCount / skipExceptionFilter 拒绝 / Error / BatchCancelException）。

三个不经 skip 的例外：load 阶段异常（RetryBatchLoader 耗尽后直接失败，skip 只包 consumer 链）；fireBeforeComplete/fireBeforeChunkEnd 监听器回调异常；stateStore.saveTaskState 自身异常。

**Q2：chunk 失败时整体 index 应该停在失败处吗？现在停吗？**

应该停在失败 chunk 首行之前（连续完成前缀末尾），resume 重跑失败行。**现在不停**（缺陷 6）：失败 chunk 的行仍被标记 done 并从 tracking map 压实移除，兄弟成功 chunk 压实越过"已失踪"的失败行推进索引并落盘 → 重启按越界索引跳行 → 失败行（事务已回滚）永不重处理。唯一安全交错：失败 chunk 之前还有 in-flight 行（map 头部为 false）时压实被挡住。

**Q3：chunk 失败会导致整体失败吗？**

最终会，但严重延迟（缺陷 4）：失败线程仅退出自己的 loop；allOf 等所有兄弟线程耗尽剩余数据后才 saveTaskState(true, err) → FAILED；期间兄弟线程继续消费提交、DB 恒为 RUNNING、计数持续更新；阻塞型 loader 下兄弟线程永久阻塞 → FAILED 永不落库 + 线程泄漏；且因缺陷 5，失败原因即使是显式 cancel，落库也是 FAILED 而非 KILLED/CANCELLED。仅 concurrency=1 时立即失败。

正确设计应为：失败 → 立即通知兄弟线程停止（fail-fast）→ 索引停在失败前缀 → FAILED 即时落库 → resume 重跑失败行。目前三步全不成立。

### 七、单元测试验证（2026-08-17 取证完成，同日完成修复并复验通过）

测试采用**确定性并发编排**（CountDownLatch 控制线程推进点，被测钩子内按信号决定 报错/放行/阻塞；所有等待带超时防挂死，遵循 `docs-for-ai/02-core-guides/testing.md` 异步防挂起规则；无任何 sleep/轮询依赖，重复运行结果恒定）：

- 位置：`nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/TestBatchBugVerification.java`、`nop-batch/nop-batch-dsl/src/test/java/io/nop/batch/dsl/manager/TestLimitHash.java`
- 断言风格：全部按**正确语义**断言——首跑失败 = bug 存在的直接证据（8/8 失败，逐一命中预测的失败模式）；修复后全部转绿（8/8 + 1/1 通过，3 次连跑确定性成立）。
- 完整取证输出与编排细节见 `nop-batch-review-audits/test-verification.md`。

| 测试 | 验证目标 | 编排方式 | 首跑实际值（bug 证据） | 修复后 |
|---|---|---|---|---|
| bug1_historyItemCount | Bug 1 计数器写错 | 纯单线程 | history=0（应 5）、process 被污染为 5 | ✅ |
| bug2_onConsumeEnd | Bug 2 监听器挂错列表 | 纯单线程 | fireConsumeEnd 不触发；fireChunkTryEnd 错误触发 | ✅ |
| bug3_skipWithAggregator | Bug 3 resume 双倍消费+静默丢数据 | 纯单线程 | aggregated=[2,4,6,7,8]（应 [1,2,3,4,5]）；skip 消费 6 行；行 4、5 从未投递 | ✅ |
| defect4_failFastMissing | 缺陷 4 兄弟线程不停止 | concurrency=2 + 多级 latch 门控（F1 失败等待 S1 已记录；S2 只在 cancel 回调后投递；pass-through processor 启用逐条 isCancelled 检查点拒绝在途投递） | 失败后 [S2,S3] 仍被处理；future 等数据耗尽才异常完成 | ✅（处理后仅 [S1]，cause=原始异常） |
| defect5_completionExceptionMasksCancel | 缺陷 5 状态映射死代码 | concurrency=1 + 假 stateStore 捕获 err | err=CompletionException（cause 才是 BatchCancelException） | ✅ |
| defect6_completedIndexPastFailedChunk | 缺陷 6 压实越过失败行 → resume 丢数据 | **单线程受控顺序**驱动两个 chunk context（缺陷本质是 onChunkEnd 调用顺序问题，非线程调度问题）+ 二段 resume | index=4（应 0）；resume 首批=[5,6]（应 [1,2]）→ 失败行永不重投 | ✅ |
| defect9_partitionOrderingScrambled | 缺陷 9 同分区跨页乱序 | fetcher 内阻塞：T1 取到 page1 后扣住不入队，T2 的 page2 先入队处理，顺序完全由 latch 决定 | 处理序=[a2,a1]（应 [a1,a2]） | ✅（fetch+入队串行化后 T2 阻塞在互斥锁上，恒为 [a1,a2]） |
| defect11a_nullRetryPolicyNoGiveUp | 缺陷 11a null policy 无放弃逻辑 | 200 次失败后成功（有限化证明无界） | attempts=201（应 1） | ✅（首次失败即以原始异常放弃） |
| defect12b_limitHashNegative | 缺陷 12b 负 partition 索引 | 纯单线程（Integer.MIN_VALUE） | limitHash=-2（应 ≥0） | ✅（Math.floorMod，经 mvn 真实测试验证） |

不在单测范围：缺陷 7（saveProcessed 空实现需 DB 断言）、缺陷 8（双实例竞态需并发 DB 会话）——留待后续修复的集成测试。

### 八、修复记录（2026-08-17）

| 缺陷 | 修复 | 文件 |
|---|---|---|
| Bug 1 | set/incHistoryItemCount 改写 historyItemCount 字段 | BatchTaskContextImpl.java:307-314 |
| Bug 2 | onConsumeEnd 注册到 onConsumeEnd 列表 | BatchTaskContextImpl.java onConsumeEnd |
| Bug 3 | skip() 聚合已读取的 item（不再二次 next） | ResourceRecordLoaderProvider.skip() |
| 缺陷 4 | executeChunkLoop 失败时先 completeExceptionally(future) 再 cancel(STOP)（幂等，不覆盖已有 reason）——保证 allOf 报告原始异常 + 兄弟线程 fail-fast | BatchTask.java executeChunkLoop |
| 缺陷 5 | onTaskComplete 入口解包 CompletionException（仅影响 stateStore/completeExceptionally/future 的异常值，保留根因） | BatchTask.java unwrapCompletionException |
| 缺陷 6 | onChunkEnd 失败 chunk 不标记 done（保持 false），压实停在失败行前 | ResourceRecordLoaderProvider.onChunkEnd |
| 缺陷 9 | fetch+addBatch 在 fetchMutex 临界区内完成（同步变体在 takeBatch 内、异步变体在 fetch 循环内），保证页按源序入队 | PartitionDispatchQueue.java、AsyncFetchPartitionDispatchLoaderProvider.java |
| 缺陷 11a | checkRetry 对 null policy 首次失败即抛出原始异常 | RetryConsumeHelper.checkRetry |
| 缺陷 12b | limitHash 改用 Math.floorMod（非负） | ModelBasedBatchTaskBuilderFactory.limitHash |

修复后验证：
- `./mvnw test -pl nop-batch/nop-batch-core`：**12 run / 0 fail / 0 skip**（8 个回归测试 + 4 个存量测试），3 次连跑确定性成立
- `nop-batch-orm/dao/jdbc/gen/exp/biz/service`：BUILD SUCCESS
- `TestLimitHash`（nop-batch-dsl）：1/1 通过
- `nop-batch-sys` 失败为**预先存在**的 H2 schema 环境问题（NOP_SYS_SEQUENCE 表缺失；git stash 基线复测同样失败，与本次修复无关）
- 附带解决：nop-batch-dsl 单模块构建失败系 target 中**陈旧类文件**（PartitionResolver 移包前的裸引用），`mvn clean` 后恢复

未修复（后续计划）：缺陷 7（NopBatchRecordResult 写路径实现 + 事务边界）、缺陷 8（启动互斥 + 唯一索引）、缺陷 10（fetch 线程取消泄漏）、缺陷 12 其余项。文档已同步：`docs-for-ai/02-core-guides/batch-dsl.md`（fail-fast/retry-null/dispatcher 顺序语义）、`docs-for-ai/03-modules/nop-batch.md`（修正记录级幂等的过度承诺、补充 at-least-once 语义）。

## Conclusion

- nop-batch 功能设计成熟度较高，但存在 3 个已确认 copy-paste 类 bug + 3 条 P0 级缺陷（fail-fast 缺失、取消状态映射失效、并发失败路径数据丢失）+ 记录级幂等空实现。
- 关键风险组合：`concurrency>1 + saveState + chunk 失败` → 兄弟线程继续写数据 + 失败行被压实丢失 + FAILED 状态延迟落库，三问题叠加。
- **2026-08-17：P0 全部修复并回归验证通过**（9 项：Bug 1/2/3 + 缺陷 4/5/6/9/11a/12b，见"八、修复记录"）；缺陷 7（history 写路径）/8（启动互斥）/10（fetch 线程泄漏）/12 其余项留待后续计划。
- 后续工作：建议新建 `ai-dev/plans/` 处理剩余缺陷；at-least-once/线程安全语义已同步至 `docs-for-ai/02-core-guides/batch-dsl.md`。

## Open Questions

- [ ] 缺陷 4 修复形态：taskContext.cancel vs 编排层中断（需确认 BatchCancelException 在 skip/retry 链上的传播）
- [ ] 缺陷 6 修复形态：失败行标记回滚 vs 压实前检查 chunk 失败
- [ ] 缺陷 7 修复形态：是否引入"提交后写历史"事务同步钩子 + 首版落盘实现
- [ ] 12e 的 getReadCount 语义需实测确认

## References

- Audit 记录（两轮独立子 agent 核查）：
  - Round 1：`ai-dev/analysis/2026-08/nop-batch-review-audits/round1-*.md`（4 个 agent 分片核查）
  - Round 2：`ai-dev/analysis/2026-08/nop-batch-review-audits/round2-*.md`（对升级/修正后的断言复核）
- 源码：`nop-batch/nop-batch-core/.../impl/BatchTask.java`、`BatchTaskContextImpl.java`、`BatchChunkContextImpl.java`、`ResourceRecordLoaderProvider.java`、`PartitionDispatchQueue.java`、`BatchTaskBuilder.java`、`RetryConsumeHelper.java`、`WithHistoryBatchConsumer.java`、`BatchProcessorConsumer.java`
- 源码：`nop-batch/nop-batch-dao/.../DaoBatchStateStore.java`、`DaoBatchRecordHistoryStore.java`；`nop-batch/nop-batch-jdbc/.../JdbcKeyDuplicateFilter.java`；`nop-batch/nop-batch-exp/.../EtlTaskStateStore.java`；`nop-batch/nop-batch-dsl/.../ModelBasedBatchTaskBuilderFactory.java`
- 框架层证据：`nop-kernel/nop-commons/.../Cancellable.java`、`nop-kernel/nop-core/.../ExecutionContextImpl.java`、`nop-persistence/nop-orm/.../OrmSessionImpl.java`、`GenSqlHelper.java`、`OrmEntityState.java`
- 关联文档：`docs-for-ai/02-core-guides/batch-dsl.md`、`docs-for-ai/03-modules/nop-batch.md`
