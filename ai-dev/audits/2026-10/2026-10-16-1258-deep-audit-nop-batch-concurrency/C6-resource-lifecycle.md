# nop-batch 并发·异步·资源专项审计 —— C6 资源生命周期与泄漏（第 1 轮初审）

## 审计单元

nop-batch 聚合器 11 个含 Java 的 jar 子模块：api / app / biz / core / dao / dsl / exp / gen / jdbc / orm / service。手写规模 218 files / 15,698 行（src/main，不含 `_` 前缀生成物与 target/）。核心引擎位于 `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core`。

## 仓库与分支

- 仓库：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`
- 分支：`concurrency-audit-remediation`
- 审计日期：2026-10-16
- 子代理：C6 维度初审（盲审轮）

## 维度定义（C6——资源生命周期与泄漏）

open/close 配对；异常与取消路径的资源释放；连接/流/文件句柄泄漏；线程池关闭责任；监听器注册/注销配对；任务终止后的残留资源。

## 盲审纪律声明

本报告为盲审轮次产物。审计过程中**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件（目录列表除外，仅为创建本报告文件）。仅阅读了：live code、方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`、owner 文档 `docs-for-ai/03-modules/nop-batch.md`。所有「现状」字段一律填「新发现（待复核）」。

## 检查范围说明

按维度执行步骤逐项覆盖：

1. **资源类型与获取点盘点**（grep 基线 + 全量通读）：文件流（`ResourceRecordLoaderProvider`/`ResourceRecordConsumerProvider` ← `FileBatchSupport` 的 CSV/Excel reader/writer）、JDBC（`JdbcBatchLoaderProvider`/`JdbcPageBatchLoaderProvider`/`JdbcInsertBatchConsumer`/`JdbcUpdateBatchConsumer`/`JdbcKeyDuplicateFilter`/`GenInsertSqlRecordIO`）、ORM（`OrmQueryBatchLoaderProvider`/`OrmBatchConsumer`/`OrmInsertBatchConsumer`/`OrmBatchRecordSnapshotBuilder`）、状态存储（`DaoBatchStateStore`/`DaoBatchRecordHistoryStore`/`EtlTaskStateStore`）、线程池（`BatchTask.getExecutor` 默认 `GlobalExecutors.cachedThreadPool()`、`ExportDbTool`/`ImportDbTool` 自建 `DefaultThreadPoolExecutor`、`BizExportTaskBuilder`/`DefaultBizEntityExporter` 注入 executor）、分区队列（`PartitionDispatchQueue`）、临时文件（`DefaultBizEntityExporter` 导出临时文件）。gen/biz/service/app/api 模块按资源获取点 grep 命中情况抽查，未发现额外手工资源获取点（连接一律来自平台 `IConnectionManager`/`IJdbcTemplate`/`IOrmTemplate`，与反虚构预注一致）。
2. **配对释放核对**：逐获取点核对 try-with-resources / afterComplete 回调 / finally / destroy；异常、取消、超时、提前 return 路径逐一走查。
3. **注册表对称性**：12 种生命周期 listener 注册点（`BatchTaskContextImpl.onXxx`）与触发点（`fireXxx`）全量核对；`PartitionDispatchQueue.removePartition` 全仓调用方核查。
4. **关闭幂等**：`LoaderState.close`/`ConsumerState` 关闭重复调用、`IoHelper.safeCloseObject` 语义核对。
5. **owner doc 已声明边界**（判级校准）：chunk 每事务独立提交/回滚；at-least-once（失败 chunk 行重新投递，业务方自带幂等）；historyStore 与 consume 同事务；orm-reader 仅 query 模型；transactionScope none/chunk/process/consume（history 时 consume 提升 process）；`nop.batch.discard-batch-when-finished` 为既有丢弃日志（其触发条件的资源含义按预注不重复立案，仅在配对表中登记核验结论）。

---

## 一、资源获取点配对表

| # | 获取点 | 释放点 | 成功路径 | 异常路径 | 取消/超时路径 | 判定 |
|---|--------|--------|----------|----------|---------------|------|
| 1 | `ResourceRecordLoaderProvider.newLoaderState:184` `recordIO.openInput`（CSV/Excel/模型 reader） | `context.onAfterComplete → IoHelper.safeCloseObject(state.input)`（:189-191，先于 beforeRead 注册，注释明确覆盖 aggregator/beforeRead 报错） | 任务完成时关闭 | setup 抛错 → `executeAsync` catch → `onTaskComplete` → `completeExceptionally` → afterComplete 关闭（已核 `ExecutionContextImpl.completeExceptionally:131-139` 必触发 afterComplete） | chunk 失败 → fail-fast cancel → 各循环退出 → allOf → onTaskComplete → 关闭（延迟受最慢在途 chunk 约束，不泄漏） | 通过 |
| 2 | `ResourceRecordConsumerProvider.newConsumerState:103` `recordIO.openOutput` | `context.onAfterComplete → safeCloseObject(state.output)`（:105-107，先于 beginWrite） | onBeforeComplete 写 trailer+flush 后，afterComplete 关闭 | beginWrite 抛错（:121-125）→ 同上路径关闭；成功路径 fireBeforeComplete 抛错 → 转 err 路径仍 completeExceptionally → 关闭 | 同上 | 通过 |
| 3 | `JdbcBatchLoaderProvider.newState:226-230` `openConnection`（closeConnection=true 时自持连接） | `LoaderState.close()`（:169-175，synchronized；ps→dataSet→connection 顺序）；`context.onAfterComplete(err→state.close())`（:185）+ EOF 早关（:292-294） | 任务末/EOF 关闭 | SQLException 窗口内 ps 不关（见 C6-005）；主路径通过 | close 为 synchronized(state)，与 load 互斥 | 通过（附 C6-005） |
| 4 | `JdbcInsertBatchConsumer`/`JdbcUpdateBatchConsumer` 经 `jdbcTemplate.runWithConnection` | 平台 JdbcTemplate 借还 | 平台管理 | 平台管理 | 平台管理 | 通过（模块内无手工连接） |
| 5 | `OrmQueryBatchLoaderProvider` keyset 分页（`dao.findNext`） | 每次查询连接由 ORM 层管理；无长持游标 | 通过 | 通过 | 通过 | 通过 |
| 6 | `PartitionDispatchQueue` Semaphore 许可（addBatch acquire :314 / takeBatch release :181） | takeBatch 按实取释放；`removePartition`（:267-281）有完整释放实现但全仓零调用方 | 对称 | 失败时未派发记录随任务期对象 GC 回收，许可不跨任务 | `acquirePermits` 以 `finished` 标志 + 500ms tryAcquire 循环防永久阻塞（discard-batch-when-finished 为已声明边界，资源含义核验：丢弃即释放许可不入队，无许可滞留） | 通过（附 C6-006） |
| 7 | `BatchTask.getExecutor:83-88` 默认 `GlobalExecutors.cachedThreadPool()`（daemon、SynchronousQueue、maxPool 不限） | 平台全局池不关闭（设计意图） | — | — | — | 通过；用户注入 executor 为 IoC bean（owner doc「executor=线程池 Bean 名称」），生命周期归容器，引擎不关闭属合理分工（建议 owner doc 补一句声明，未立案） |
| 8 | `ExportDbTool:148-153` / `ImportDbTool:230-235` 自建 `DefaultThreadPoolExecutor`（队列 Integer.MAX_VALUE；本处仅提交「表数」个任务，实际有界） | `finally executor.destroy()`（Export:186-188 / Import:261-267）；`SyncThreadPoolExecutor.INSTANCE.destroy()` 为 no-op（nop-commons :61-63，销毁共享单例无害，已排除） | 覆盖 | **try 窗口外**的 beforeExport/beforeImport/syncConfigWithDb 异常不经过 destroy（见 C6-003）；destroy=shutdown 无 awaitTermination（见 C6-004） | — | C6-003 / C6-004 |
| 9 | `DefaultBizEntityExporter:57` 导出临时文件 | 失败即删（:69-71）；成功延迟 10 分钟删（globalTimer，:72-76） | 覆盖 | 覆盖 | 覆盖（依赖定时器存活，进程重启残留归平台临时目录治理，非本模块契约） | 通过 |
| 10 | `EtlTaskStateStore.saveStateFile:129-144` 状态文件写 | `FileHelper.writeText` 平台关闭 + ATOMIC_MOVE 原子替换（降级分支保内容完整） | 通过 | 通过 | 通过 | 通过 |
| 11 | 12 种 listener 注册（`BatchTaskContextImpl.onXxx` + `ModelBasedBatchTaskBuilderFactory.addListeners:544-622`） | 全部经 task/chunk context 的 complete/completeExceptionally 统一触发，注册表由 fire 方法消费后置 null（`ExecutionContextImpl:142-181`） | 通过 | listener 异常传播路径（fireChunkEnd 吞异常、其余 8 个传播）均不绕过 afterComplete 关闭链——listener 异常 → chunk 循环失败 → allOf → onTaskComplete → completeExceptionally → 关闭 | 同左 | 通过（Error 级异常除外，归 C6-001） |
| 12 | `DaoBatchStateStore`/`DaoBatchRecordHistoryStore` ORM 会话 | 平台 IOrmTemplate/dao 管理；rollback 时 `session.clear()`（BatchTaskBuilder:449-453 注释 + SingleSessionFunctionInvoker） | 通过 | 通过 | 通过 | 通过 |
| 13 | saveState 失败后的状态残留（completedIndex 已推进但 saveState 失败） | `processChunk:322-326` 顺序为 incCount → saveTaskState → chunkContext.complete()；saveTaskState 抛错 → catch（:328）→ fireChunkEnd(e) + saveTaskState(false,e) → completeExceptionally → loader 压实回调见 exception!=null 不推进 completedIndex（`ResourceRecordLoaderProvider.onChunkEnd:274,300`） | — | 失败不推进；重启后按 DB 中最后成功值重放，落在已声明的 at-least-once 语义内 | — | 通过（声明边界内） |

---

## 二、发现条目

### [nop-batch-C6-001] chunk 循环仅 catch Exception，Error 类 Throwable 使任务 future 永不完成，reader/writer/JDBC 连接永久泄漏、任务行永久 RUNNING

- **文件**： `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:226-263`（关键 :243）
- **证据片段**:
  ```java
  executor.execute(() -> {
      ContextProvider.runWithContext(ctx -> {
          try {
              do { ... processChunk(...) ... } while (true);
              future.complete(null);
          } catch (Exception e) {                    // ← 只捕 Exception
              ...
              future.completeExceptionally(e);
              if (!context.isCancelled())
                  context.cancel(ICancellable.CANCEL_REASON_STOP);
          } finally {
              BatchTaskGlobals.removeTaskContext();
          }
          return null;
      });
  });
  ```
- **严重程度**: P1
- **并发场景**: 单个 chunk-loop 工作线程（`GlobalExecutors.cachedThreadPool` 或用户注入池）内抛出 Error（OutOfMemoryError、xpl 脚本深递归 StackOverflowError、NoClassDefFoundError）；主线程与其他 concurrency-1 个 chunk 线程在 `allOf(futures).whenComplete` 上等待。
- **触发时序**: T1：某 chunk 线程在 `processChunk`（或 xpl processor 脚本、JDBC driver 反序列化）中抛出 `StackOverflowError`/`OutOfMemoryError` → `catch (Exception e)` 不命中 → `future.completeExceptionally` 未执行 → 该 `CompletableFuture` 永不完成（Error 沿 `executor.execute` 逃逸到工作线程未捕获处理器，仅线程死亡，无日志落 batch 错误码）。T2：主线程 `CompletableFuture.allOf(futures)` 因成员永不完成而永不触发 `whenComplete` → `onTaskComplete` 永不执行。交叠后果：(1) `stateStore.saveTaskState(true, ...)` 永不调用 → 任务行永久停留 `TASK_STATUS_RUNNING`，重启被启动闸门拒绝（`ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE`，无任何 API 可将该行置终态——`NopBatchTaskBizModel` 无 kill/cancel 端点，已 grep 确认）；(2) `context.completeExceptionally` 永不触发 → afterComplete 关闭链（文件 reader/writer、`JdbcBatchLoaderProvider` 自持连接）永不释放；(3) `task.execute()` 同步调用方在 `FutureHelper.syncGet` 上永久挂起。
- **现状**: 新发现（待复核）。错误路径资源泄漏 + 挂死不可检测（对照 F8 清理不对称、F5 挂死不可见）。
- **风险**: 声明包络内大数据量任务本就是 OOM 高危场景（大 chunk×装箱），一旦触发即同时产出「句柄泄漏 + 任务行死锁 + 调用方挂起」三重后果；任务行 RUNNING 死锁使同 taskName+taskKey 的任务永久无法重启，需人工改库。
- **建议**: `executeChunkLoop` 的 catch 改为 `catch (Throwable e)`（对 Error 保留 future.completeExceptionally + fail-fast cancel；OOM 场景下 completeExceptionally 本身可能再抛，可套 `FutureHelper.complete` 的安全变体或在 catch 内先 `future.completeExceptionally` 再处理其余清理）；同型补 `onTaskComplete` 外层兜底。修复后增加「chunk 内抛 Error → future 必完成 + 任务行必达终态」的回归测试（以注入 RuntimeException 子类无法覆盖，需直接提交抛 Error 的任务）。
- **失效面枚举**（F3，chunk 处理链上全部 `catch (Exception`/缺 Throwable 防护站点逐一登记）:
  1. `BatchTask.executeChunkLoop:243` —— 本体，后果最重（future 孤儿→三重后果）。
  2. `BatchTask.processChunk:328` —— Error 时 fireChunkEnd/saveTaskState(false,e)/chunkContext.completeExceptionally 全部跳过（chunk 级 afterComplete 不触发），但最终仍汇入站点 1。
  3. `BatchTask.executeAsync:136` —— Error 逃逸 executeAsync（无 catch (Throwable) 兜底、finally 仅 removeTaskContext），返回 future 的方法契约被破坏。
  4. `AsyncFetchPartitionDispatchLoaderProvider` fetch 循环 :133 —— Error 时 exception 引用不置位 → 消费端 `takeBatch` 在 `noMoreData`（exitFetchThread finally 置位）后返回 null → 任务以部分数据「成功」结束（静默截断，比挂起更隐蔽）。
  5. `AbstractRetryBatchConsumer.consume`（catch Exception :66 区）—— Error 跳过 snapshot.onError（OrmBatchRecordSnapshotBuilder 的 clearSession 不执行），ORM 会话不清理，汇入站点 1。对照组：`RetryConsumeHelper.retryConsume` 用 `catch (Throwable)`、`SkipConsumeHelper` 用 `catch (Throwable)`——同族内标准不一，证明 Throwable 捕获是本仓既有惯例而非风格分歧。
  6. `ExecutionContextImpl.fireAfterComplete:176`（nop-core，平台基础设施不重审，仅登记使用面影响）：per-callback `catch (Exception)` —— 若 close 回调抛 Error，列表中后续 close 回调被跳过（input-close 先注册，writer-close 后注册：writer 句柄残留），该窗口并入本条回归面。
- **信心水平**: 确定（纯代码可见，无需时序竞态）
- **误报排除**: 不属于「原语使用不是问题」——这是完成语义与清理链的守卫缺失；且同族 `RetryConsumeHelper` 已用 Throwable 捕获，说明 Error 路径在本仓被认为是真实可达的。
- **复核状态**: 已复核（独立复核）——**保留 P1**。:243 catch(Exception) 事实、Error 逃逸→future 永不结算→allOf 永不触发→onTaskComplete 不执行→afterComplete 关闭链失守→任务行永久 RUNNING→syncGet 无超时挂死，每步均经 live code 核实（ContextProvider.runWithContext 无 catch，其 finally 仅恢复/关闭 context）；触发面现实性成立（processor source 为 xpl 用户脚本，StackOverflowError/OOM 属声明包络内高危；RetryConsumeHelper:54/SkipConsumeHelper:33 用 catch(Throwable) 佐证本仓惯例）。修复耦合：仅改 catch(Throwable) 不能闭合 C6-010 的 S3 站点，两案须同一 diff（try 上移 + catch 放宽）。现状：新发现（已知未修复）。

---

### [nop-batch-C6-002] executeChunkLoop 的 executor.execute 同步拒绝（RejectedExecutionException）绕过 onTaskComplete，setup 阶段已打开的文件句柄/连接泄漏且任务行永久 RUNNING

- **文件**： `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:143-146`（循环）、`:226`（execute 抛点）、`:106-152`（外层 try 仅有 finally 无 catch）
- **证据片段**:
  ```java
  // executeAsync：setup 已打开 reader/writer，随后才提交 chunk 循环
  loader = loaderProvider.setup(context);              // 文件 input / JDBC 连接已打开
  chunkProcessor = chunkProcessorProvider.setup(loader, context); // writer output 已打开
  context.fireTaskBegin();
  ...
  CompletableFuture<?>[] futures = new CompletableFuture[concurrency];
  for (int i = 0; i < concurrency; i++) {
      futures[i] = executeChunkLoop(context, i, chunkProcessor);   // ← executor.execute 同步抛 REE
  }
  ...
  return future;
  } finally { BatchTaskGlobals.removeTaskContext(); }   // ← 无 catch：REE 裸逃逸
  ```
- **严重程度**: P1（罕见但具体化：默认 `GlobalExecutors.cachedThreadPool` 为 SynchronousQueue+不限最大线程、从不拒绝；拒绝仅发生在用户注入的有界 executor 或停机竞态窗口）
- **并发场景**: 应用停机序列与批任务启动竞态；用户通过 `<batch task executor="beanName">` 注入有界线程池（如 `DefaultThreadPoolExecutor` bean + Abort 型拒绝策略）在队列/线程饱和时提交。
- **触发时序**: T1：`loaderProvider.setup` 打开 CSV reader（或 `JdbcBatchLoaderProvider.openConnection` 自持连接）、consumer setup 打开 output 文件，`tryMarkTaskRunning` 已将任务行置 RUNNING。T2：`executeChunkLoop` 内 `executor.execute(...)`（:226）在 lambda 执行前同步抛 `RejectedExecutionException`（RuntimeException）。T3：异常沿 for 循环 → 外层 try（无 catch）→ `executeAsync` 裸抛。交叠后果：`onTaskComplete` 从未被调用 → `context` 永不 complete/completeExceptionally → 全部 afterComplete 关闭回调不执行 → reader/output/JDBC 连接泄漏；任务行永久 RUNNING，重启被闸门拒绝（同 C6-001 后果 1）。
- **现状**: 新发现（待复核）。错误路径资源泄漏 + 提交点守卫缺失。
- **风险**: 拒绝即不可逆泄漏，且无任何日志指示资源未关闭（调用方拿到的是 REE，与泄漏无关联线索）；停机窗口中的定时批任务为现实触发面。
- **建议**: `executeAsync` 中 futures 提交循环包入 setup 同款 try/catch：任一 `executeChunkLoop` 抛出时对已创建 futures 逐个 completeExceptionally 并直接走 `onTaskComplete(future, meter, e, context)`，复用既有终态化+关闭链；`executeChunkLoop` 内对 `executor.execute` 增加 try/RE E → `future.completeExceptionally(e)` 的防御。
- **失效面枚举**（F3，全模块「submit/execute 后无完成兜底」站点清单）:
  1. `BatchTask.executeChunkLoop:226`（chunk 循环提交）—— 本体，泄漏成立。
  2. `AsyncFetchPartitionDispatchLoaderProvider.setup:116`（fetch 线程提交）—— REE 时异常传播出 setup → `executeAsync:136` catch → `onTaskComplete` → completeExceptionally → afterComplete 关闭 reader（该站点**已有**兜底，因 setup 位于受 catch 保护的阶段；对照证明缺陷仅在 chunk-loop 提交点）。
  3. `ExportDbTool.execute:176` / `ImportDbTool.execute:252`（`executor.submit` 表任务）—— REE 逃逸时 finally 的 destroy 仍执行、DataSource 自建时亦关（Import），泄漏面仅 SimpleDataSource（非池化、无实资源）；不立案。
  4. `PartitionDispatchLoaderProvider`/`PartitionDispatchQueue` —— 无 executor 提交点，不适用。
- **信心水平**: 确定（代码路径唯一且无嵌套防护）
- **误报排除**: 非平台基础设施自身问题（GlobalExecutors 不拒绝是有意的）；缺陷在模块侧提交点无完成兜底，属本模块清理责任。
- **复核状态**: 已复核（独立复核）——**保留 P1**。REE 逃逸路径核实：executeAsync 外层 try 无 catch（仅 finally），:226 同步抛出即裸逃逸，allOf 接线（:148）从未执行，onTaskComplete 不执行；此时任务行已 RUNNING（loadTaskState→tryMarkTaskRunning 先于 setup）。触发面现实性核实：默认 cachedThreadPool（ExecutorHelper:81-83，core=0/max=Integer.MAX_VALUE/SynchronousQueue）仅 shutdown 后拒绝，**默认装配不可达判定成立**；拒绝条件为用户注入有界 executor（owner doc 一等配置 executor="bean-name"）饱和或停机竞态，单一常见条件即触发。与 C6-001 同修复族（终态化阻断 S2）。现状：新发现（已知未修复）。

---

### [nop-batch-C6-003] ExportDbTool/ImportDbTool：executor 创建后、try 块之前存在未受 destroy 保护的窗口（beforeExport/beforeImport 脚本、syncConfigWithDb）——清理不对称（F8），当前窗口内无资源持有

- **文件**： `nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java:148-161`（executor 建于 :148-153，try 始于 :161，beforeExport 在 :157-159）、`nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ImportDbTool.java:230-242`（executor 建于 :230-235，try 始于 :242，beforeImport 在 :239-240）
- **证据片段**:
  ```java
  // ExportDbTool.execute()
  IThreadPoolExecutor executor;
  if (config.getThreadCount() > 1) {
      executor = DefaultThreadPoolExecutor.newExecutor("export-db", config.getThreadCount(), Integer.MAX_VALUE);
  } else {
      executor = SyncThreadPoolExecutor.INSTANCE;
  }
  IEvalScope scope = XLang.newEvalScope();
  if (config.getBeforeExport() != null) {
      config.getBeforeExport().invoke(scope);        // ← try 之外：异常则 destroy 永不执行
  }
  try { ... } finally { executor.destroy(); }
  ```
- **严重程度**: P2「理论风险，未具体化（当前无资源持有）」
- **并发场景**: 单线程工具入口（CLI `CliExportDbCommand`/`CliImportDbCommand`），无并发交错；纯异常路径问题。
- **触发时序**: `beforeExport`/`beforeImport` xpl 脚本抛异常 → `execute()` 传播 → `finally`（try 体后置）不经过 → `executor.destroy()` 未调用。**泄漏窗口核验为空**：异常发生时尚未 submit 任何任务，`ExecutorHelper.newThreadPoolExecutor` 无 prestart（`corePoolSize` 线程懒创建）→ 0 worker 线程 → `DefaultThreadPoolExecutor` 对象可回收；metrics 注册默认关闭（`ThreadPoolConfig.isEnableMetrics` 缺省 false），无全局注册表持留。同窗口的 `ExportDbTool.dataSource`（:121，`JdbcConnectionConfig.buildDataSource` → `SimpleDataSource` 非池化、持连接前无 OS 资源）从不 close，亦无实资源。Import 侧自建 HikariDataSource 已由 `needClose` 在 finally 关闭（:261-267），对称性完好。
- **现状**: 新发现（待复核）。结构不对称成立，但按现行实现写不出真实泄漏窗口，按盲审纪律降 P2 并标注理论风险。
- **风险**: 若未来有人在 try 前窗口提交任务（如 prewarm）或将 executor 换为急切建线程的实现，不对称立即变成真泄漏；属易复发族（F3 修复实例不修类别的高危形态）。
- **建议**: 将 `IEvalScope`+beforeExport/beforeImport 段移入 try（Export 侧连 `syncConfigWithDb` 一并纳入或在 syncConfigWithDb 失败路径补 `IoHelper.safeClose(dataSource)`）；或把 executor 创建下移到 try 首行。
- **失效面枚举**（F3，全仓「自建 IThreadPoolExecutor」站点清单）：`ExportDbTool.execute:148-153`（本条）、`ImportDbTool.execute:230-235`（本条）、`GlobalExecutors.cachedThreadPool/globalWorker`（平台单例，显式不关闭，豁免）、`DefaultBizEntityExporter.getExecutor`（仅引用容器 bean/全局池，无自建，豁免）。全仓无第五处自建点（grep `newExecutor|new Thread\(` 核验）。
- **信心水平**: 确定（不对称确定存在；「无泄漏」结论同样确定——懒建线程语义已核 `ExecutorHelper:73-89`）
- **误报排除**: 不按「低频路径粗粒度锁」类误报豁免——这是配对释放纪律问题，只是当前后果为空，故按规则标注理论风险而非驳回。
- **复核状态**: 已复核（独立复核）——**维持 P2 理论风险，支撑证据修正**：不对称事实成立（beforeExport :157-159 在 try :161 之外），但初审「metrics 注册默认关闭（缺省 false）」为**事实性错误**——`ThreadPoolConfig.enableMetrics` 缺省为 **true**（ThreadPoolConfig:31），beforeExport 窗口异常时 executor 经 ExecutorHelper.registerMetrics（:201-208）绑定 GlobalMeterRegistry，存在全局强引用滞留（micrometer gauge 捕获 executor）；修正后判级不变（滞留对象无线程（懒建）无 FD，泄漏面仍为空）。Import 侧「对称性完好」结论被 C6-007 推翻，见「维度复核结论」R3。现状：新发现（已知未修复）。

---

### [nop-batch-C6-004] exp 两工具 executor.destroy()=shutdown() 无 awaitTermination：失败路径 execute() 返回后兄弟表任务仍在后台运行，状态文件被并发续写、CLI 已返回错误码

- **文件**： `nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java:176-188`、`nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ImportDbTool.java:252-267`；根因载体 `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultThreadPoolExecutor.java:91-93`
- **证据片段**:
  ```java
  // DefaultThreadPoolExecutor
  public void destroy() {
      executor.shutdown();          // ← 不 awaitTermination、不 shutdownNow
  }
  // ExportDbTool.execute()
  FutureHelper.getFromFuture(FutureHelper.waitAll(futures));  // 任一表任务失败 → 此处抛
  ...
  } finally {
      executor.destroy();           // 兄弟在途/排队表任务继续运行
  }
  ```
- **严重程度**: P2
- **并发场景**: `threadCount>1` 多表并行导入/导出；主线程（waitAll 等待者）与表任务工作线程（`DefaultThreadPoolExecutor`，daemon=false 非守护线程）。
- **触发时序**: T1：表 A 任务抛错 → `FutureHelper.getFromFuture` 抛 → 进入 finally。T2：`executor.destroy()` 仅 shutdown —— 已在途的表 B/C 任务继续执行、队列中未启动的表 D 任务仍会被 shutdown 后的池按序执行（shutdown 语义：拒绝新提交、存量照跑）。T3：`execute()` 返回，CLI `picocli` 已产出错误退出码；但非守护工作线程钉住 JVM，表 B/C/D 继续向输出目录写文件、`EtlTaskStateStore.saveStateFile` 继续原子替换状态文件（`synchronized(EtlTaskStateStore)` 与主线程无后续竞争，但与用户「任务已失败」的认知相悖）。交叠后果：用户按错误码重跑 → 与仍在跑的兄弟表任务并发写同一输出文件/状态文件（state 文件有原子替换保护、目标 CSV 无）；进程退出时间不可预期。
- **现状**: 新发现（待复核）。关闭责任履行不完整（shutdown 而非 shutdown+await(+shutdownNow 降级)——平台已有现成工具 `ThreadHelper.shutdownAndAwaitTermination`）。
- **风险**: CLI 语义层「返回≠终止」；重跑叠加导致输出文件交错损坏（import 侧有 `JdbcKeyDuplicateFilter` 幂等缓冲，导出侧无任何保护）。
- **建议**: `destroy()` 调用后补 `awaitTermination`（平台 `ThreadHelper.shutdownAndAwaitTermination(service, timeout, unit)` 现成）；失败路径显式选择语义——要么 await 所有表任务收尾后再返回，要么 shutdownNow+await 并把「部分表未完成」写入结果；owner doc 补充失败路径的终止语义声明。
- **失效面枚举**（F3，`destroy()` 全部调用点清单）：`ExportDbTool.execute:187`（本条）、`ImportDbTool.execute:262`（本条）、nop-batch 内其余无 IThreadPoolExecutor.destroy 调用点（grep 核验）；平台侧 IoC bean 生命周期销毁走容器钩子，不在本模块责任面。
- **信心水平**: 确定
- **误报排除**: 非平台基础设施重审——缺陷在使用方选择了「发后不管」的关闭方式而非平台提供的等价关闭工具；后果落在生产路径（多表失败是常规运维场景）。
- **复核状态**: 已复核（独立复核）——**保留 P2**。destroy()=executor.shutdown() 无 awaitTermination（DefaultThreadPoolExecutor:91-93）核实；工作线程非守护（newExecutor 缺省 daemon=false，DefaultThreadPoolExecutor:30）钉住 JVM 核实；shutdown 后排队表任务照跑（ThreadPoolExecutor 语义）。维持 P2。现状：新发现（已知未修复）。

---

### [nop-batch-C6-005] JdbcBatchLoaderProvider.newState 的 SQLException 清理分支不关闭已创建的 PreparedStatement（closeConnection=false 时残留至事务结束）

- **文件**： `nop-batch/nop-batch-jdbc/src/main/java/io/nop/batch/jdbc/loader/JdbcBatchLoaderProvider.java:233-276`（ps 创建 :234，清理分支 :272-276）
- **证据片段**:
  ```java
  try {
      PreparedStatement ps = JdbcHelper.prepareStatement(dialect, connection, sql);
      if (queryTimeout > 0) { ... ps.setQueryTimeout(...); }       // ← 这些 setter 抛 SQLException 时
      if (streaming ...) { ps.setFetchSize(...); } else { ... }    //    ps 已创建
      if (maxRows > 0) { ps.setMaxRows(...); }
      ...
      ResultSet rs = ps.executeQuery();                             // ← 或此处抛
      ...
  } catch (SQLException e) {
      if (closeConnection)
          IoHelper.safeCloseObject(connection);   // ← 只关连接，ps 未关
      throw dialect.getSQLExceptionTranslator().translate(sql, e);
  }
  ```
- **严重程度**: P3
- **并发场景**: 任务 setup 线程（单次）；无并发交错，纯异常路径配对缺陷。
- **触发时序**: `prepareStatement` 成功后、`LoaderState` 构造前任一 `SQLException`（setQueryTimeout/setFetchSize/setMaxRows/executeQuery）→ catch 关闭自建 connection（此时按 JDBC 契约其语句随之关闭，无泄漏）；但当 `closeConnection=false`（`jdbcTemplate.currentConnection` 返回事务绑定连接，任务在环境事务内 setup）→ 共享连接不关 → 该 ps 保持 open，直至外部事务提交/回滚连接归还池时由池回收。单次 setup 失败最多残留 1 个 ps，自愈边界=事务生命周期。
- **现状**: 新发现（待复核）。量级有界（1 ps/次 setup 失败）故判 P3 而非 P1/P2。
- **风险**: 长事务（transactionScope=process 包裹大 chunk）中 Oracle 类「max open cursors」紧张的库上可放大为可观测错误；平时无感。
- **建议**: catch 中先 `IoHelper.safeCloseObject(ps)` 再按需关 connection（close 幂等，`LoaderState.close` 同款顺序）；或构造 `LoaderState` 后复用其 close() 做清理。
- **失效面枚举**（F3，nop-batch 内全部手工 JDBC 资源获取点）：`JdbcBatchLoaderProvider.newState:234-261`（本条，唯一手工 PreparedStatement 获取点）；`JdbcPageBatchLoaderProvider.load:93`（findPage，平台管理，豁免）；`JdbcInsertBatchConsumer:51`/`JdbcUpdateBatchConsumer:54`（runWithConnection + JdbcBatcher，平台管理，豁免）；`JdbcKeyDuplicateFilter`（findAll，平台管理，豁免）；`GenInsertSqlRecordIO`（Writer 委托 IRecordOutput.close，配对完好，豁免）。
- **信心水平**: 确定
- **误报排除**: 非「平台连接管理已兜底」可豁免——ps 的关闭责任在本类 catch 分支内被显式遗漏，仅在 closeConnection=true 分支被 JDBC 连接关闭隐式覆盖，两分支行为不一致。
- **复核状态**: 已复核（独立复核）——**保留 P3**。catch 分支（:272-276）只关 connection 不关 ps 事实成立；closeConnection=false（事务绑定连接）时 ps 残留至外部事务结束，量级 1 ps/次 setup 失败。维持 P3。现状：新发现（已知未修复）。

---

### [nop-batch-C6-006] PartitionDispatchQueue.removePartition 全仓零调用方：清理 API 为死代码；失败任务的分区队列残留为任务期对象、无跨任务泄漏

- **文件**： `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/PartitionDispatchQueue.java:267-281`（removePartition）
- **证据片段**:
  ```java
  public void removePartition(int partitionIndex) {
      lock.lock();
      try {
          PartitionQueue<T> pq = partitions.remove(partitionIndex);
          if (pq != null) {
              int undelivered = pq.queue.size();
              pq.queue.clear();
              count -= undelivered;
              semaphore.release(undelivered);   // 许可对称释放，实现本身正确
              pq.threadId = -1;
          }
      } finally { lock.unlock(); }
  }
  ```
- **严重程度**: P3
- **并发场景**: 无（零调用方）；登记目的为「清理 API 无调用方」事实的资源残留面核验。
- **触发时序**: 不适用（无触发面）。残留面核验：任务失败时 `partitions` 中未派发记录随 `PartitionDispatchQueue` 实例变为不可达——该实例仅被 `PartitionDispatchLoaderProvider.setup`/`AsyncFetch...setup` 的任务级局部闭包与 `context.onAfterComplete(queue::finish)` 回调（任务 context 生命周期=单次执行）引用，任务结束即整体 GC；`count`/semaphore 均为实例字段，无静态/全局持有。结论：**无跨任务残留泄漏**，仅「提供了正确实现但无人调用」的死代码与契约缺口（想丢弃某分区时无处下手）。
- **现状**: 新发现（待复核；「零调用方」为派单已知事实，本轮独立 grep `removePartition` 复核确认）。
- **风险**: 无运行时风险；维护性风险（死代码暗示有人认为应清理而从未接线；若未来任务转为长驻复用同一队列实例，残留面立即变为真实）。
- **建议**: 要么接线（如 chunk 失败时按 partitionIndex 丢弃该分区积压并跳过），要么删除 API 并在 Javadoc 声明「失败任务整队列随任务对象回收」的既定语义，防未来误持。
- **失效面枚举**（F3，PartitionDispatchQueue 全部公有方法配对核查）：`addBatch`(acquire)/`takeBatch`(release) 配对；`completeBatch`（队列摘除+signalAll）有调用方（两个 Provider 的 chunk afterComplete）；`finish`（finished+signalAll）有调用方（task afterComplete）；`exitFetchThread`（latch countDown+noMoreData）有调用方（AsyncFetch finally）；`markNoMorData` 有调用方（AsyncFetch 空页）；`removePartition` **无调用方**（本条）。
- **信心水平**: 确定
- **误报排除**: 非误报豁免类——按维度第 5 步「注册表对称性：列出只有 add 没有 remove 的注册点清单」的产出要求登记，判级按实际后果（无泄漏）取 P3。
- **复核状态**: 已复核（独立复核）——**保留 P3**。removePartition 全仓（非仅 nop-batch）grep 零调用方核实；实现本身正确；残留为任务期对象、无跨任务泄漏。维持 P3。现状：新发现（已知未修复）。

---

## 三、维度集外线索

### [nop-batch-CL-012]（维度集外线索，不立案展开）
- 形态归属：C2（锁）/C9（热路径争用）。
- 证据：`nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java:234` —— `public synchronized void saveTaskState(...)`。DaoBatchStateStore 为注入 `BatchTaskManagerImpl` 的单例 bean，全平台所有批任务共享；每个 chunk 执行一次 `saveTaskState(false,...)`（BatchTask.processChunk:322-323），锁内含 ORM `updateEntityDirectly` 数据库写——所有任务的进度持久化在单一监视器上全局串行化，DB 慢时放大为全局吞吐上限。
- 严重度候选：P2（可证明的争用热点；正确性无碍——saveTaskState 本就需要互斥，但锁对象应为任务/任务行而非单例 bean）。
- **复核处置（独立复核）**: **确认**。synchronized saveTaskState（:234）+ 锁内 updateTask DB I/O（:253-254）、每 chunk 一次（BatchTask:322-323）、singleton bean 全平台共享均核实。族关系：与 CL-016（C7 轮）为**同一站点同一事实的跨维度重复登记**（修复台账须合并去重）；CL-020/CL-036 为同模式（锁内 I/O）不同站点，合并修复面。P2 候选维持。

### [nop-batch-CL-013]（维度集外线索，不立案展开）
- 形态归属：C3（异步链——超时后幽灵任务）。
- 证据：`nop-batch-core/.../consumer/BatchProcessorConsumer.java:85-99` —— asyncProcessor 超时后 `batchChunkCtx.cancel(CANCEL_REASON_TIMEOUT)` + 抛 NopTimeoutException，但已提交的异步 processor 任务无 future 跟踪、无取消：它们继续在底层 executor 上运行，向已废弃的 `outputs` 队列写入、countDown 已无人等待的 latch；chunk 失败 → 任务终态化 → afterComplete 关闭 reader/writer 后，幽灵 processor 仍可能经 inject 服务继续写 DB（在途写不在任何事务终态管控内）。
- 严重度候选：P1 候选（asyncProcessor + 超时为显式配置项，触发链具体）。
- **复核处置（独立复核）**: **确认机制；P1 候选降为 P2 候选**。幽灵 processor 机制核实（BatchProcessorConsumer:85-99：超时仅 cancel+抛 NopTimeoutException，已提交异步 processor 无 future 跟踪无取消）。触发面核实：asyncProcessor 为 xdef 显式属性（batch.xdef:15，缺省缺省→false 同步模式）、全仓 XML **零使用**、超时缺省 10 分钟（BatchConfigs:15-16）——默认装配不可达且 opt-in 无仓内先例；后果为 fail-loud 超时后的后台副作用续跑。跨维度重复：与 CL-017（C7 轮，P2 候选）为同一事实，处置须合并并统一判级，按 P2 候选对齐。

### [nop-batch-CL-014]（维度集外线索，不立案展开）
- 形态归属：C3（取消语义）。
- 证据：`nop-batch-core/.../impl/BatchTask.java:91-157` —— `executeAsync` 返回的 `CompletableFuture.cancel` 与引擎取消标志（`Cancellable` volatile flag，`ExecutionContextImpl extends Cancellable`，无线程中断）完全解耦：外部对返回 future 调用 cancel(true) 后，全部 chunk 线程照常跑完，loader 连接/文件句柄按正常完成时点释放；模块内亦未暴露任何面向外部的 `context.cancel` 入口（nop-batch 域内 grep 仅 fail-fast 与 async 超时两处内部调用）。调用方若以为 cancel 即停止，产生幽灵整任务。
- 严重度候选：P2（语义未声明+守卫缺失形态；实际停止手段缺失）。
- **复核处置（独立复核）**: **确认**。域内 context.cancel 仅两处内部调用（BatchTask:254 fail-fast、BatchProcessorConsumer:92 超时）核实，无外部取消入口；interrupt() 均为 InterruptedException 后自恢复模式，引擎不中断工作线程；返回 future 的 cancel 与 Cancellable volatile flag 解耦。P2 候选维持（与第 2 轮 S10 阻塞型不结算同根）。

### [nop-batch-CL-015]（维度集外线索，不立案展开）
- 形态归属：F9（吞异常/异常掩蔽，C1 域；超出 CL 限定四形态，按超集登记并注明）。
- 证据：`nop-batch-dao/.../store/DaoBatchStateStore.java:234-236` + `nop-batch-core/.../impl/BatchTask.java:136-139` —— 启动闸门拒绝（RUNNING 已有实例/KILLED/COMPLETED 不可重启/startLimit 超限，`loadTaskState` 抛 NopException）时 `setTaskRecord` 未执行 → 属性为 null → 异常路径 `onTaskComplete → saveTaskState(true, err, context)` 内 `task.setCompleteItemCount(...)` NPE，NPE 从 `executeAsync` catch 块裸逃逸（返回 future 已在 finally 中以原始错误完成）：`execute()` 同步调用方拿到原始闸门错误（正确），`executeAsync()` 调用方（BatchTaskRunner → job 引擎）拿到的是裸 NPE，根因被掩蔽。
- 严重度候选：P2（常见触发条件——双节点重复调度即命中；后果为错误掩蔽而非状态损坏，saveState=true 时必现）。
- **复核处置（独立复核）**: **驳回**。证据链断裂：`loadTaskState` 位于 executeAsync:**116**，在 catch(Exception)→onTaskComplete 的内层 try（:123-140）**之外**——启动闸门拒绝（startExistingTask 各 throw / tryMarkTaskRunning 冲突）沿外层 try（无 catch，:154 仅 finally）**直接同步传播出 executeAsync**，onTaskComplete/saveTaskState 根本不执行；「setTaskRecord 未执行（闸门拒绝前提）」与「saveTaskState 执行（onTaskComplete 被调用前提）」在当前代码中互斥。同步（execute→syncGet）与异步调用方拿到的都是原始 NopException，不存在「NPE 掩蔽根因」路径。BatchTask.java 在本审计前后 git 历史无变更（最近提交为 plan 2381），非「已修复」而是初审误读控制流。

---

## 四、检查通过项（无发现，记录核验口径）

1. **FileBatchSupport/ResourceRecord* open-close 配对**：reader 打开（openInput）先注册 afterComplete 关闭再执行 beforeRead/aggregator/skip（`ResourceRecordLoaderProvider:184-193` 显式注释该顺序动机）；writer 同型（:103-107）；关闭经 `ExecutionContextImpl.complete/completeExceptionally` 双路径必达，per-callback 异常吞并保证关闭链不断（Exception 级）。异常 chunk 中途失败 → loop catch → allOf → onTaskComplete → completeExceptionally → 关闭，时序闭环。
2. **OrmQueryBatchLoaderProvider 连接/session 生命周期**：keyset 分页无长持游标，连接按查询借还（平台 ORM 层），无手工资源。
3. **PartitionDispatchQueue 信号量对称性**：acquire/release 数值配对（:181 按实取释放）；`acquirePermits` 超时循环防 fetch 线程永久阻塞（已声明 discard 语义的资源含义核验：丢弃不入队、许可未滞留）。
4. **saveState 断点残留**：失败 chunk 不推进 completedIndex（onChunkEnd exception!=null 双重防护 :274/:300）；「completedIndex 已推进但 saveState 失败 → 重启重复」落在已声明 at-least-once + 业务幂等契约内，不立案。
5. **chunk 事务 rollback 资源释放**：transactionScope=chunk/process 的 Invoker 包装由平台 TransactionalFunctionInvoker/SingleSessionFunctionInvoker 托管，rollback 时 session.clear（BatchTaskBuilder:449-453 注释确认）；模块内无手工事务边界。
6. **listener 异常路径**：12 种 listener 注册/触发全量配对（BatchTaskContextImpl:392-607）；listener 异常传播（8 个）与吞并（fireChunkEnd、fireAfterComplete per-callback）均不绕过 afterComplete 关闭链（Exception 级；Error 级缺口归 C6-001 失效面 6）。
7. **DefaultBizEntityExporter 临时文件**：失败即删、成功延迟删，配对完整。
8. **EtlTaskStateStore**：临时文件+ATOMIC_MOVE（降级保完整）+全方法 synchronized，写侧对称。
9. **SyncThreadPoolExecutor.INSTANCE 被 destroy**：no-op 实现（nop-commons:61-63），排除「共享单例被关停」候选缺陷（反虚构核验）。

---

## 五、总计

| 类别 | 编号 | 一句话 | 严重度 |
|------|------|--------|--------|
| 发现 | nop-batch-C6-001 | chunk 循环仅 catch Exception，Error 使 future 永不完成 → reader/writer/连接泄漏+任务行永久 RUNNING+调用方挂死 | P1 |
| 发现 | nop-batch-C6-002 | executor.execute 同步拒绝绕过 onTaskComplete，setup 已开资源泄漏+任务行永久 RUNNING | P1 |
| 发现 | nop-batch-C6-003 | exp 两工具 destroy 保护窗口不含 beforeExport/beforeImport/syncConfigWithDb（当前无资源持有） | P2 理论风险 |
| 发现 | nop-batch-C6-004 | exp 两工具 destroy=shutdown 无 awaitTermination，失败后兄弟表任务后台续跑、输出/状态文件被续写 | P2 |
| 发现 | nop-batch-C6-005 | JdbcBatchLoaderProvider SQLException 分支不关 PreparedStatement（事务绑定连接时残留至事务结束，量级 1） | P3 |
| 发现 | nop-batch-C6-006 | PartitionDispatchQueue.removePartition 零调用方（死代码；残留为任务期对象，无跨任务泄漏） | P3 |
| 线索 | nop-batch-CL-012 | DaoBatchStateStore 单例 synchronized saveTaskState 全局串行化所有任务进度写（C2/C9） | P2 候选 |
| 线索 | nop-batch-CL-013 | asyncProcessor 超时后幽灵 processor 任务无跟踪无取消，终态化后仍写 DB（C3） | P1 候选 |
| 线索 | nop-batch-CL-014 | executeAsync 返回 future cancel 与引擎取消标志解耦，外部无真实停止手段（C3） | P2 候选 |
| 线索 | nop-batch-CL-015 | 启动闸门拒绝路径 saveTaskState NPE 掩蔽根因并裸逃逸 executeAsync（F9/C1，超集登记） | P2 候选 |

**发现 6 条（P1×2、P2×2、P3×2）+ 维度集外线索 4 条（CL-012~015）。**

---

## 维度复核结论（独立复核子代理，2026-10-16）

复核身份与方法：本节由 C6 维度独立复核子代理产出，与初审/第 2 轮代理相互独立，以怀疑姿态逐条复核。全部 11 条发现的 file:line 证据均亲自打开 live code 核对（行号漂移以实际代码为准，未发现实质性漂移）；两条 P1 逐步验证触发时序；平台支撑类（`ContextProvider.runWithContext`、`GlobalExecutors`/`ExecutorHelper`、`DefaultThreadPoolExecutor`、`FutureHelper`、`ExecutionContextImpl`、`ThreadPoolConfig`、`JdbcConnectionConfig`、`IBatchTask`、`NopBatchTaskBizModel`）逐一核实；历史对照面（`cra-index.md`、plans 2461~2466、`git log`）仅用于现状标注，不影响独立判定。

### R0 现状标注（全部条目）

- 分支 `concurrency-audit-remediation` 上**无任何 nop-batch 代码修复**：`git log -- nop-batch/` 最近提交为 CRA 启动前的全代码复审修复（plan 2381/2382）；CRA WI 1.1~1.6 的修复单元为 nop-stream / nop-job / nop-wf-nop-task / nop-retry-nop-tcc，与 nop-batch 零代码交集；工作区 clean。
- **本维度 11 条发现现状一律标注：新发现（已知未修复）**。WI 1.1~1.6 的同族修复（如 kafka-2pc producer 终态泄漏）不构成 nop-batch 的修复证据，同族不同模块仍为新发现。

### R1 逐条裁定表（发现 11 条）

| 编号 | 初判 | 复核裁定 | 裁定要点 |
|---|---|---|---|
| C6-001 | P1 | **保留 P1** | :243 catch(Exception) 事实成立；Error 逃逸链每步核实：`ContextProvider.runWithContext` 无 catch（finally 仅恢复/关闭 context，:157-167）→ future 仅在 try 内结算（:242/:250），Error 时均跳过 → `allOf`(:148) 永不结算 → `onTaskComplete`（loop 路径上 context.completeExceptionally 的唯一调用点）不执行 → afterComplete 关闭链（input/output/JDBC LoaderState.close 的唯一出口）永不触发 → 任务行停 RUNNING（tryMarkTaskRunning 先行置位，重启被 startExistingTask:123-129 闸门拒绝）→ `FutureHelper.syncGet`（:158-173）无超时、调用方永久挂起。触发面现实性成立：processor source 为 xpl 用户脚本（用户代码占比高），深递归 StackOverflowError / 声明包络内大数据量 OOM / NoClassDefFoundError 属现实可达；同族 RetryConsumeHelper:54 / SkipConsumeHelper:33 用 catch(Throwable) 证明本仓既有惯例。**修复耦合**：仅改 catch(Throwable) 不能闭合 C6-010 的 S3 站点（守卫起点过晚），两案必须同一 diff（try 上移至 lambda 首行 + catch 放宽 Throwable）。 |
| C6-002 | P1 | **保留 P1** | REE 逃逸路径核实：executeAsync 外层 try 无 catch（:154-156 仅 finally），REE 自 :226 同步抛出即裸逃逸，allOf 接线（:148）从未执行 → onTaskComplete 不执行；此时任务行已 RUNNING（loadTaskState→tryMarkTaskRunning 先于 setup）。触发面现实性：默认 cachedThreadPool 已核（ExecutorHelper:81-83，core=0/max=Integer.MAX_VALUE/SynchronousQueue）仅 shutdown 后拒绝，**默认装配不可达判定成立**；拒绝条件=用户注入有界 executor（owner doc 一等配置 `executor="bean-name"`）饱和或停机竞态，单一常见条件即触发且不可逆。与 C6-001 同修复族（终态化阻断 S2）。 |
| C6-003 | P2 理论 | **维持 P2 理论（支撑证据修正）** | 不对称事实成立（beforeExport :157-159 在 try :161 之外）。但初审支撑证据有误：`ThreadPoolConfig.enableMetrics` **缺省为 true**（ThreadPoolConfig:31），非初审所称「缺省 false」——beforeExport 窗口异常时 executor 经 `ExecutorHelper.registerMetrics`（:201-208）以 ExecutorServiceMetrics 绑定 GlobalMeterRegistry，**存在全局强引用滞留**（micrometer gauge 捕获 executor）。修正后判级不变：滞留对象无线程（懒建核实）无 FD，OS 资源泄漏面仍为空；但「无全局注册表持留」一句不成立，按复核纪律如实更正。Import 侧「对称性完好」结论被 C6-007 推翻（见 R3）。 |
| C6-004 | P2 | **保留 P2** | destroy()=executor.shutdown() 无 awaitTermination（DefaultThreadPoolExecutor:91-93）核实；工作线程非守护（`newExecutor` 缺省 daemon=false，DefaultThreadPoolExecutor:30）钉住 JVM 核实；shutdown 后排队表任务照跑（ThreadPoolExecutor 语义）。维持 P2。 |
| C6-005 | P3 | **保留 P3** | catch 分支（:272-276）只关 connection 不关 ps 事实成立；closeConnection=false（事务绑定连接）时 ps 残留至外部事务结束，量级 1 ps/次 setup 失败。维持 P3。 |
| C6-006 | P3 | **保留 P3** | removePartition **全仓**（非仅 nop-batch）grep 零调用方核实；实现本身正确；残留为任务期对象、无跨任务泄漏。维持 P3。 |
| C6-007 | P2 | **保留 P2** | HikariDataSource 建于 syncConfigWithDb:125（try :242 之外）事实成立；同窗口可抛点不止 readTableMetas:132，还包括 `DialectManager.getDialectForDataSource`:128（dialect 未配置时连库探测）；finally :261-267 不可达核实。**小勘误**：Hikari 分支条件为 `threadCount > 0`（buildDataSource:139）且 concurrencyPerTable!=null，本条表述「threadCount>1」偏窄——threadCount=1 即走 Hikari 分支，触发面略宽于描述，判级不变。 |
| C6-008 | P2 | **保留 P2** | fetch 一次性 ctx（AsyncFetch:119）注册 chunk 压实回调（ResourceRecordLoaderProvider:167-171）且无人 complete 核实；对照同步 PartitionDispatchLoaderProvider:70 fetcher 闭包捕获消费 ctx 正常工作核实；xdef 无互斥（batch.xdef:10/76-85）+ 构建期无守卫（validateConsumers:221 仅查 consumer）核实；ERR_BATCH_PROCESSING_ITEMS_NOT_EMPTY 终态检查（:221-227）核实。维持 P2。 |
| C6-009 | P2 | **保留 P2（标注）** | onTaskComplete 清理段结构（:204-219）、whenComplete 静默吞没（返回 future 无人持有）、metrics.endTask 先于 FutureHelper.complete（S5）、setup 路径 X 替换原始 e 逃逸（S6）全部核实。维持 P2，标注：任务行永久 RUNNING 后果与 P1 族同重且触发面更宽（终态写是每任务必经步骤），但资源面已关闭、future 必以原始 err 结算、调用方 fail-loud——后果窄于 C6-001/002；修复排期建议紧随 P1 族。 |
| C6-010 | P2 | **保留 P2** | propagateContext :229 / provideTaskContext :231 在 try :232 之前核实；`runWithContext` 自身无 catch 且 `getOrCreateContext()` 在其 try 之外（ContextProvider:157-167）核实——**「改 catch(Throwable) 无法闭合该站点、必须 try 上移」的结构性论断成立**。与 C6-001 同 diff 修复（回归测试注入点移至 propagateContext）。维持 P2（Error 级后果同 C6-001；Exception 级可达性低，初审已如实标注）。 |
| C6-011 | P3 理论 | **保留 P3 理论** | currentConnection:226 借用决策与 LoaderState 持有至任务结束核实；仓内调用面（ImportDbTool:350 / ExportDbTool runTask 均同步 execute；DefaultBizEntityExporter executeAsync 但 loader 为 ORM/GraphQL 无 JDBC 连接持有）核实——触发调用方当前不存在。维持 P3 理论。 |

### R2 F3 完备性补全

复核性 grep `catch (Exception|catch (Throwable)` 全量核对 nop-batch 六个含引擎代码子模块（20 处命中）：初审两轮的失效面/终态化站点表（C6-001 面 1-6 + S1~S10）已覆盖全部实质站点（BatchTask :136/:200/:243/:328、AsyncFetch :133、BatchChunkProcessor :74、AbstractRetryBatchConsumer :41/:65、RetryConsumeHelper:54/SkipConsumeHelper:33 对照、fireChunkEnd BatchTaskContextImpl:561、ResourceRecordConsumerProvider 本地收敛路径、WithHistoryBatchConsumer :64、BatchProcessorConsumer :119）。**无需补全**。

### R3 C6-003 修正裁定（对第 1 轮的正式勘误）

第 1 轮 C6-003 记载「Import 侧自建 HikariDataSource 已由 needClose 在 finally 关闭（:261-267），对称性完好」——**复核确认该结论不成立**：`needClose=true` 与池创建均在 `syncConfigWithDb`（ImportDbTool:121-125，execute():226 调用，位于 try :242 之前），该窗口失败时 finally 不可达，整池泄漏——即 C6-007。C6-003 的结构性不对称事实本身成立，但其「当前窗口内无资源持有」的核验仅对 ExportDbTool 成立；泄漏面以 C6-007 为独立条目。C6-003 另一处支撑证据更正：enableMetrics 缺省 true（见 R1 行 3）。两处更正均不改 C6-003 的 P2 理论判级。

### R4 维度集外线索处置（含 CL-010 独立复核）

| 线索 | 裁定 | 要点 |
|---|---|---|
| CL-010（C5 轮确认归属 C6） | **确认** | 独立核实：JdbcBatchLoaderProvider setup 期 openConnection+executeQuery（:226-261）、关闭出口=任务级 afterComplete（:185）+EOF 早关（:292-294）；挂死复合场景下连接与游标长期占用成立。标注：(a) 泄漏为**条件性**——需 C3 族挂死缺陷先行触发，本条自身非独立触发面；(b) concurrency>1 共享单连接单游标为 synchronized(state) 串行化的设计选择，正确性无碍，吞吐上限归 C9/C5 域；(c) 与 C6-011 同族（setup 期获取 × 持有生命周期错配）。P2 候选维持。 |
| CL-012 | **确认** | synchronized saveTaskState（:234）+ 锁内 updateTask DB I/O（:253-254）、每 chunk 一次（BatchTask:322-323）、singleton bean 全平台共享均核实。**族关系**：与 CL-016（C7 轮）为**同一站点同一事实的跨维度重复登记**（修复台账须合并去重）；CL-020（per-execution loader synchronized(state) 锁内 DB I/O）与 CL-036（ResourceRecordLoaderProvider 锁内 LOG.info）为同模式不同站点（锁内 I/O 族，合并修复面）。P2 候选维持。 |
| CL-013 | **确认机制；P1 候选降为 P2 候选** | 幽灵 processor 机制核实（BatchProcessorConsumer:85-99：超时仅 cancel+抛 NopTimeoutException，已提交异步 processor 无 future 跟踪无取消，输出追加进已废弃 outputs、countDown 无人等待的 latch）。触发面核实：asyncProcessor 为 xdef 显式属性（batch.xdef:15，缺省 false 同步模式）、全仓 XML 零使用、超时缺省 10 分钟（BatchConfigs:15-16）——默认装配不可达且 opt-in 无仓内先例；后果为 fail-loud 超时后的后台副作用续跑。**跨维度重复**：与 CL-017（C7 轮，P2 候选）为同一事实，处置须合并并统一判级，按 P2 候选对齐。 |
| CL-014 | **确认** | 域内 context.cancel 仅两处内部调用（BatchTask:254 fail-fast、BatchProcessorConsumer:92 超时）核实，无外部取消入口；interrupt() 均为 InterruptedException 后自恢复模式，引擎不中断工作线程；返回 future 的 cancel 与 Cancellable volatile flag 解耦。P2 候选维持（与 S10 阻塞型不结算同根）。 |
| CL-015 | **驳回** | 证据链断裂：`loadTaskState` 位于 executeAsync:**116**，在 catch(Exception)→onTaskComplete 的内层 try（:123-140）**之外**——启动闸门拒绝（startExistingTask 各 throw / tryMarkTaskRunning 冲突，DaoBatchStateStore:108-150）沿外层 try（无 catch，:154 仅 finally）**直接同步传播出 executeAsync**，onTaskComplete/saveTaskState 根本不执行；「setTaskRecord 未执行（闸门拒绝前提）」与「saveTaskState 执行（onTaskComplete 被调用前提）」在当前代码中互斥。同步（execute→syncGet）与异步调用方拿到的都是原始 NopException，不存在「NPE 掩蔽根因」路径。BatchTask.java 在本审计前后 git 历史无变更（最近提交 plan 2381），非「已修复」而是初审误读控制流。 |
| CL-033 | **确认** | RetryBatchLoader.load（:39-68）同 chunk 同游标重试核实；readBatch 部分读出后异常时已消费行随异常丢弃、重试自游标当前位置继续的机制成立。P2 候选维持（触发面=loadRetryPolicy 配置 + 流式/顺序游标 reader）。 |
| CL-034 | **确认** | fireLoadEnd 双触发（BatchChunkProcessor.loadItems：:71 以 null 触发 → 监听器抛 X → catch :74 → :75 以 X 重触发全部监听器）核实。P3 候选维持。 |
| CL-035 | **确认** | fireTaskBegin 先置空 onTaskBegins 再逐个 run、无 per-callback 防护（BatchTaskContextImpl:518-530）核实；半初始化后果限于 begin 型回调缺失，afterComplete 关闭链完整（Exception 级）。P3 候选维持（Error 级并入 C6-001 族）。 |

### R5 争议点与判级张力（如实登记，不改裁定）

1. **C6-009 的 P2/P1 边界**：任务行永久 RUNNING 后果与 P1 族同重，触发面（终态写 DB 失败）比 C6-001/002 的 Error/REE 更宽。维持 P2 的依据是后果三要素中资源与调用方两面完好（无泄漏、fail-loud），仅终态持久化一面失守。若按「后果×触发概率」排修复优先级，本条实际应高于其余 P2。
2. **CL-013 跨维度判级不一致**：同一事实在 C6 轮记 P1 候选、C7 轮记 P2 候选（CL-017）。复核按触发面（默认装配不可达 + opt-in 零使用）对齐 P2 候选；合并处置时不得双记。
3. **C6-001「无任何 API 可将该行置终态」的表述精度**：NopBatchTaskBizModel 确无专用 kill/cancel 端点（grep 核实，仅 CrudBizModel 继承），但通用 CRUD 变更接口理论上可直改 taskStatus 字段（取决于实体权限配置）。建议修复时收敛表述为「无专用终止端点，通用写路径不在契约内」。不改变 P1 判定。
4. **C6-003 支撑证据错误（enableMetrics 缺省值）**：初审以「缺省 false」论证窗口无持留，实际缺省 true；因滞留对象无 OS 资源，判级侥幸不变——此类「结论对、论据错」在修复验证时须以复核后的论据为准。

### R6 复核统计

- 发现 11 条：**保留 11 / 降级 0 / 驳回 0**；判级全部维持（P1×2、P2×6、P3×3），其中 2 条支撑证据修正不改判级（C6-003 metrics 缺省值、C6-007 Hikari 触发条件表述）。
- 线索 8 条（CL-010/012/013/014/015/033/034/035）：**确认 7 / 驳回 1**（CL-015）；候选判级调整 1（CL-013 P1→P2 候选，对齐 CL-017）。
- 跨维度重复登记 2 组（修复台账须合并）：CL-012=CL-016（同站点）、CL-013=CL-017（同事实）。
- 现状：11 条发现 + CL-010 全部为新发现（已知未修复），无 CRA 期修复证据。

---

## 最终保留项（修复阶段输入）

| 编号 | 一句话 | 终判严重度 | 修复族 |
|---|---|---|---|
| nop-batch-C6-001 | chunk 循环仅 catch Exception，Error 使 future 永不完成 → reader/writer/连接泄漏+任务行永久 RUNNING+调用方挂死 | P1 | 终态化阻断族（同 diff：try 上移 + catch Throwable） |
| nop-batch-C6-002 | executor.execute 同步 REE 绕过 onTaskComplete，setup 已开资源泄漏+任务行永久 RUNNING | P1 | 终态化阻断族（S2） |
| nop-batch-C6-010 | executeChunkLoop try 守卫起点过晚（propagateContext/provideTaskContext 在 try 外），改 catch(Throwable) 亦无法闭合 | P2 | 终态化阻断族（S3，与 C6-001 同 diff） |
| nop-batch-C6-009 | onTaskComplete 经 whenComplete 调用时 saveTaskState 终态写失败被静默吞没：任务行永久 RUNNING 且无日志 | P2 | 清理段显式化族（S4/S5/S6 同段同修） |
| nop-batch-C6-007 | ImportDbTool HikariDataSource 建于 try 窗口外，syncConfigWithDb 失败即整池泄漏（修正 C6-003 Import 侧结论） | P2 | exp 工具 try 重排族（与 C6-003 同 diff） |
| nop-batch-C6-004 | exp 两工具 destroy=shutdown 无 awaitTermination，失败后兄弟表任务后台续跑、输出/状态文件被续写 | P2 | 优雅关闭族（ThreadHelper.shutdownAndAwaitTermination） |
| nop-batch-C6-008 | saveState + AsyncFetch 组合：chunk 压实回调挂 fetch 一次性 ctx 永不触发，completedIndex 不推进、任务必然失败 | P2 | 构建期守卫/压实职责移交（三选一，建议守卫） |
| nop-batch-C6-003 | exp 两工具 destroy 保护窗口不含 beforeExport/beforeImport（当前 OS 资源泄漏面为空；executor 对象经全局 MeterRegistry 滞留——复核修正后的论据） | P2 理论风险 | exp 工具 try 重排族（与 C6-007 同 diff） |
| nop-batch-C6-005 | JdbcBatchLoaderProvider SQLException 分支不关 PreparedStatement（事务绑定连接时残留至事务结束，量级 1） | P3 | catch 内补 safeClose(ps) |
| nop-batch-C6-006 | PartitionDispatchQueue.removePartition 全仓零调用方（死代码；残留为任务期对象，无跨任务泄漏） | P3 | 接线或删除 API 并声明语义 |
| nop-batch-C6-011 | JdbcBatchLoaderProvider 持事务绑定连接跨越事务生命周期（异步用法池连接交叉使用；当前调用面同步等待） | P3 理论风险 | 一律自持连接或 afterComplete 校验事务存活 |
| CL-010 | jdbc-reader setup 期持连接/游标、唯一关闭出口挂 afterComplete，挂死复合时长期占用（条件性；归属 C6） | P2 候选 | 随 C3 族挂死修复评估；与 C6-011 同族 |
| CL-012 | DaoBatchStateStore 单例 synchronized saveTaskState 锁内 DB I/O 全局串行化（=CL-016，合并处置） | P2 候选 | 锁内 I/O 族（CL-012/016/020/036 合并修复面） |
| CL-013 | async processor 超时后幽灵任务无跟踪无取消（=CL-017，合并处置；候选对齐 P2） | P2 候选 | future 跟踪/取消传播或文档声明 |
| CL-014 | executeAsync 返回 future cancel 与引擎取消标志解耦，外部无真实停止手段（与 S10 同根） | P2 候选 | 取消语义声明/入口补齐 |
| CL-033 | load 侧部分读失败重试静默跳行（at-least-once 不覆盖 load 窗口） | P2 候选 | C8 域，游标快照或文档声明 |
| CL-034 | fireLoadEnd 异常路径双触发监听器 | P3 候选 | fire 语义统一 |
| CL-035 | fireTaskBegin 先置空后执行、无防护：部分 begin 回调缺失不可重放 | P3 候选 | per-callback 防护同族化 |

**复核后终态：发现 11 条全部保留（P1×2、P2×6、P3×3）+ 线索确认 7 条（驳回 CL-015）。终态化阻断族（C6-001/002/010 + S4~S6）为同一修复 diff 的四个成员，应合并实施并共用「Error 注入 → future 必结算 → 任务行必达终态」回归测试。**
