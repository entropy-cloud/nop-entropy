# nop-batch 并发·异步·资源专项审计 — C1 共享可变状态与可见性（第 1 轮盲审）

- **审计单元**: nop-batch 聚合器（11 个含 Java 的 jar 子模块：api/app/biz/core/dao/dsl/exp/gen/jdbc/orm/service），src/main 手写代码 218 files / 15,698 行（不含 `_` 前缀生成物与 target/）
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度定义 (C1)**: 多线程/多任务共享的字段、集合、缓存、注册表的线程安全与内存可见性；check-then-act 竞态；非同步惰性初始化；static 可变状态；ThreadLocal 误用
- **盲审纪律声明**: 本轮未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件（包括本审计目录下其他文件）；仅阅读方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`、owner doc `docs-for-ai/03-modules/nop-batch.md` 与当前 live code。所有"现状"均为本轮独立判断，未参照历史发现编号。
- **检查范围说明**: 逐文件精读 nop-batch-core 全部运行时类（BatchTask/BatchTaskBuilder/BatchTaskContextImpl/BatchChunkContextImpl/BatchTaskGlobals/11 个 loader/19 个 consumer/8 个 processor/metrics/debug/utils），以及 orm/jdbc/gen/dsl/exp/dao/biz 模块全部含实例状态或共享引用的类；对全模块执行了 static 可变字段、ThreadLocal、Collections.synchronized、volatile、AtomicLong、CopyOnWriteArrayList、computeIfAbsent、provide/remove 配对等定向 grep 失效面枚举。单调增长源盘点属 C7 维度，本报告省略。
- **反虚构预注核实**: 实测确认 nop-batch src/main 无集中任务注册表单例、无 static 可变注册表（grep 全部 static 非 final 字段，仅命中 `NopBatchApplication.globalArgs`（main 写一次的启动参数）与生成物 `_gen` 的 `_PROP_ID_BOUND`）；分区为单任务 JVM 内内存派发（`PartitionDispatchQueue`），无跨节点分片/fencing 结构。以下发现均以实测代码为锚点。

---

## 一、共享可变状态清单（字段 × 写者 × 读者 × 同步机制 × 判定）

### A. singleton bean 实例字段

| 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `BatchTaskManagerImpl.{ormTemplate,jdbcTemplate,daoProvider,stateStore,transactionTemplate,sqlLibManager,historyStoreBuilder}` | IoC 启动期 setter 注入 | 各任务构建 | 注入后只读（NopIoC 无启动后写者） | 安全 |
| `BatchTaskRunner.{batchTaskManager,partitionResolver}` | 同上 | executeAsync | 注入后只读 | 安全 |
| `DaoBatchStateStore`（singleton）`synchronized saveTaskState` | 各任务 chunk 线程 + 任务完成线程 | 同左 | store 监视器（JVM 全局单例） | 正确性安全；全局锁串行 → CL-001（C2） |
| `AbstractBizEntityService.{graphQLEngine,daoProvider,bizObjManager,executorBean,maxConcurrency,maxBatchSize}` | 启动注入/配置 | 每请求 | 注入后只读 | 安全 |
| `JdbcBatchConsumerProvider.*` 配置字段 | 启动/构建期 | setup | setup 不回填实例字段（代码注释 + `TestJdbcBatchConsumerProviderSharedField` 守护已修复的共享字段缺陷） | 安全（已声明边界） |
| `AbstractBatchResourceHandler.{resourceLocator,resourcePath,resource,pathExpr}` | 构建期 setter | getResource(context) | 任务构建期写后只读 | 安全 |
| `BatchTaskBuilder` 全部配置字段 | DSL 构建期 | buildTask/buildLoader/buildChunkProcessor | buildTask 后 effective immutable；builder 每任务新建 | 安全 |
| `EtlTaskStateStore.taskState`（非 volatile） | 构造器 / reset() | synchronized 方法 + isCompleted() | 多数经 outer 监视器；`isCompleted()` 无锁读仅出现在构造后同线程（ExportDbTool:140 / ImportDbTool:220）；`reset()` 无生产调用方 | 当前接线无跨线程写者，不构成缺陷（登记备查） |
| `PartitionDispatchLoaderProvider/AsyncFetch...Provider.{loadBatchMultiplyFactor,maxLockQueueCountPerThread}` | 构建期 setter | setup | setup 前写完，fetch 线程经 executor.execute 建立 HB | 安全 |
| `BlockingSinkBatchConsumer.sink` / `BlockingSourceBatchLoader.source` | 构建期 setter | 各 chunk 线程 | setup 前 HB（executor 提交边） | 安全 |

### B. static 可变字段

| 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `NopBatchApplication.globalArgs`（static String[]） | main() 启动一次 | 启动期 | 先写后跑，无并发 | 安全 |
| `_gen/*._PROP_ID_BOUND`、`PROP_NAME_TO_ID` | 类初始化 | 生成实体 | 类初始化 HB（生成文件，仅登记不立案） | 安全 |
| `BatchTaskMetricsImpl` 无 static 可变字段；registry 引用 final | — | — | micrometer 自身线程安全 | 安全 |

结论：**core 无 static 可变注册表**（反虚构预注核实成立）。

### C. 全局注册表/缓存使用点

| 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `ExecutionContextImpl.attributes`（每 context 一个 ConcurrentHashMap） | setup/processor | 各线程 | CHM | 安全 |
| `BatchTaskContextImpl.getCache()` 惰性初始化 | 首个调用线程 | 各线程 | `synchronized(this)` + 局部变量快照（line 104-113） | 安全 |
| `EtlTaskStateStore.tableStores`（CHM） | getTableStore computeIfAbsent | 各表任务 | CHM 原子 | 安全 |
| `SplitBatchConsumer.setup` 局部 `activeConsumers`（CHM） | getConsumer（synchronized 方法，check-then-act） | 各 chunk 线程 | 实例监视器使 CAS 原子；map 兜底 CHM | 安全（锁偏粗 → 性能非缺陷） |
| `BatchLoaderHelper.batchLoadWithProvider` → `taskCtx.computeIfAbsent` | 各 chunk 线程 | 同左 | `IEvalScope.computeIfAbsent` = `synchronized(scope)` 原子 | 正确性安全；锁内 setup 开文件/连接 → CL-002（C2） |
| `BatchTaskContextImpl.persistVars`（MapVarSet→ConcurrentHashMap） | AbstractBatchHandler.setPersistVar | 各线程 | CHM | 安全 |
| `ResourceComponentManager.loadComponentModel`（dsl 模型缓存） | 各任务加载 | 各任务 | 平台设施（nop-core，本轮不重审） | 使用方式安全 |

### D. ThreadLocal

| 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `BatchTaskGlobals.s_taskContext`（NamedThreadLocal） | BatchTask.java:105（调用线程）、231（chunk 线程）provide；155、256 remove | xpl/监听注册助手 useTaskContext() | TL 语义；provide/remove 平衡但**不保存恢复旧值** | **缺陷 → C1-001** |

模块内仅此一处 ThreadLocal（grep 全模块确认）；`NamedThreadLocal` 由平台管理，任务线程均为 remove 平衡（池化线程无残留：provide/remove 严格成对）。

### E. 逃逸共享（构造器/getter 泄漏）

| 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `BatchTaskContextImpl` 非 volatile setup 字段（taskName/taskId/taskKey/metrics/recoverMode/partitionRange/params…） | executeAsync setup（调用线程，line 105-135） | 各 chunk/fetch 线程 | 全部写点先于 `executor.execute(...)`（line 226）与 fetch 线程提交（AsyncFetch:114）→ executor HB 安全发布 | 安全 |
| `BatchTaskContextImpl` AtomicLong 计数器的 set*/inc* 混用 | set* 仅在 loadTaskState（DaoBatchStateStore:173-176、EtlTaskStateStore.loadTableState:87-105，均 setup 阶段）；运行期仅 inc*（addAndGet）+ `setCompletedIndex`（volatile，写点 ResourceRecordLoaderProvider:301，TreeMap 压缩保证单调） | chunk 线程 + saveTaskState | setup set → executor HB；运行期无 set/inc 交叠 | 安全 |
| `BatchChunkContextImpl` 可变字段（chunkItems/completedItems/historyItems/retryCount/processCount/singleMode/latch/rowNumberMap） | 单 chunk 处理线程（processChunk 每次新建 context；async 模式下异步 worker 仅触 outputs(CLCQ)+latch） | 同线程 | 线程封闭；latch/CLQ 原语自身安全 | 安全（chunk 间隔离成立：每 chunk 新实例，父上下文仅读） |
| `BatchTaskContextImpl.params` | BatchTaskRunner/BatchTask setup | xpl 读取 | IBatchTaskContext JavaDoc line 54-56 明文声明"外部传入的只读参数，在任务执行过程中不会被修改" | 已声明边界 |
| `ResourceRecordLoaderProvider.LoaderState`（TreeMap processingItems/combinedValue） | load 闭包（synchronized(state)）/onChunkEnd（synchronized(state)） | beforeComplete 回调读 combinedValue | 写侧全部 state 监视器内；trailer 读侧经 future 完成链建立 HB（chunk future.complete → allOf.whenComplete → onTaskComplete → fireBeforeComplete） | 安全 |
| `ResourceRecordConsumerProvider.ConsumerState` | consume（synchronized(state)） | onBeforeComplete trailer | 同上 HB 链 | 安全 |
| `JdbcBatchLoaderProvider/JdbcPageBatchLoaderProvider/OrmQueryBatchLoaderProvider/BatchGenLoaderProvider.LoaderState` | load 闭包（synchronized(state)）+ close 协议（synchronized，closed 标志 + EOF 返回空） | 各 chunk 线程 | state 监视器全包含；isClosed 检查在锁内（JdbcBatchLoaderProvider:280-294 有注释与回归测试） | 安全 |
| `PartitionDispatchQueue`（partitions/count/threadId/finished/noMoreData/fetchThreadCount） | addBatch/takeBatch/completeBatch/removePartition/finish/exitFetchThread | 各消费+fetch 线程 | ReentrantLock 全包含；finished/noMoreData volatile；信号量 tryAcquire 带超时；fetchMutex 保证页序 | 安全（本模块守卫最完整的类） |
| `BatchTask.primaryErrorRef`（AtomicReference，per-execution） | chunk 线程 CAS / executeAsync reset(94) | onTaskComplete | executeAsync 每实例单次执行的约定（无运行时守卫） | 依赖约定 → C1-005（P3） |
| `ExportDbTool/ImportDbTool` 的共享 `IEvalScope scope` | execute 主线程 beforeExport.invoke（提交前） | 各表任务（经 BatchTaskContextImpl 父作用域只读继承，各任务写自己的 child scope，CHM 背书） | executor 提交 HB + child-scope 隔离 | 安全 |
| 监听器注册表（8×CopyOnWriteArrayList + onTaskBegins(swap-on-fire) + onTaskEnds(死字段)） | onXxx 注册（synchronized(this)）/fire* 遍历（COW 快照） | 各 chunk 线程 | COW 快照遍历安全；onTaskBegins 锁内 swap | 安全，但 swap-on-fire 族存在注册时序缺口 → C1-003；死字段 → C1-004 |
| `BatchGenState.{genCount,subIndex}`（sequential 模式） | load 阶段（state 锁内）/ **consume 阶段 SequentialBatchRequestGenerator.nextRequest（锁外）** | 多 chunk 线程 | 无同步 | **缺陷 → C1-002** |

---

## 二、发现条目

### [C1-001] BatchTaskGlobals ThreadLocal provide/remove 不保存恢复外层上下文，嵌套批任务执行后外层任务上下文永久丢失

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:105,155,231,256`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskGlobals.java:9-21,38-48`
- **证据片段**:
  ```java
  // BatchTask.executeAsync（调用线程）
  BatchTaskGlobals.provideTaskContext(context);      // :105 直接覆盖
  try { ... return future; }
  finally { BatchTaskGlobals.removeTaskContext(); }  // :155 直接清除，不恢复旧值

  // executeChunkLoop（chunk 线程，同型）
  BatchTaskGlobals.provideTaskContext(context);      // :231
  try { ... } finally { BatchTaskGlobals.removeTaskContext(); } // :256
  ```
- **严重程度**: P2
- **并发场景**: 外层批任务 A 的 chunk 工作线程（或提交 executeAsync 的调用线程）在其 processor/consumer 代码中同步执行子批任务 B（`task.execute(ctx)` = `FutureHelper.syncGet(executeAsync(ctx))`，`IBatchTask.java:21-23`）；两任务共用同一线程的同一个 `NamedThreadLocal` 槽位。
- **触发时序**: 确定性序列，无需竞态。T1（任务 A 的 chunk 线程，TL=A 由 :231 设置）→ 执行 processor 内嵌的 B.executeAsync：:105 `provideTaskContext(B)` 覆盖 TL=A → B 的 chunk 循环提交到异步 executor 后 :155 `removeTaskContext()` 清空 T1 的 TL → 控制返回 A 的 processor，此后 T1 上 `BatchTaskGlobals.useTaskContext()` 返回 null → 后续任何 xpl 侧 `BatchTaskGlobals.onChunkBegin/onTaskBegin/...`（`BatchTaskGlobals.java:38-48` 直接 `useTaskContext().onChunkBegin(...)`）抛 NPE，或依赖 TL 的用户代码静默拿不到任务上下文；外层 A 余下所有 chunk 均处于无 TL 状态。若 executor 为同步执行器（SyncThreadPoolExecutor，ExportDbTool concurrency=1 路径），内层 :231/:256 还会把调用线程刚设置的 TL 再清一次。
- **现状**: provide/remove 严格成对（池化线程无残留），但两处 provide 站点均无"保存旧值→finally 恢复"结构；嵌套执行时外层上下文被确定性清除。
- **风险**: 批套批组合（processor 内触发子任务、`<batch:Execute>` 类联动）下，外层任务后半程的监听注册与 TL 依赖代码失效；失败模式为响亮 NPE，暂无数据损坏路径。owner doc 未声明"批任务禁止嵌套"，也无守卫阻止。
- **建议**: 两处 provide 站点改为 save/restore 结构（`Object prev = s_taskContext.get(); ... finally { if (prev != null) provideTaskContext((IBatchTaskContext) prev); else removeTaskContext(); }`），或在 `provideTaskContext` 内检测非空旧值时打 WARN；复用平台 `NamedThreadLocal` 惯例即可，无需新设施。
- **失效面枚举（F3）**: 模块内 TL 唯一（grep `ThreadLocal` 全 src/main 仅 `BatchTaskGlobals.java:9`）。同族 provide 站点共 2 处：`BatchTask.java:105`（本缺陷主现场）、`BatchTask.java:231`（同型无恢复，内层循环正常路径靠外层 finally 兜底，嵌套时同为破坏点）。remove 站点 2 处（:155、:256）与 provide 一一配对，配对本身无缺失。模块外无其他调用方（grep provideTaskContext 全仓仅 BatchTask）。
- **信心水平**: 确定（代码路径确定；触发依赖嵌套批组合这一条件）
- **误报排除**: 不属于"TL 在池化线程未清理"误报类（remove 成对、无残留）；也不是"平台设施自身问题"（NamedThreadLocal 为平台类，缺陷在调用方不恢复旧值的用法）。判 P2 而非 P1 的校准依据：组合路径条件化触发，后果响亮可定位，非默认装配即触发。
- **复核状态**: 未复核
- **复核状态**：保留 P2；新发现；全链路 file:line 与嵌套 DSL 三要素亲证（batch.xlib:47-51/batch-gen.xlib:51-64/demo task.xml），但 `useTaskContext()` 全仓零调用方使失效末步条件化，两条件合取不达 P1（详见「维度复核结论」二）。

### [C1-002] BatchGenState 共享游标计数器（genCount/subIndex）在 consume 阶段被多 chunk 线程无锁驱动，check-then-act 竞态可致生成序破坏或任务假失败

- **文件**: `nop-batch/nop-batch-gen/src/main/java/io/nop/batch/gen/generator/BatchGenState.java:44-46,64-100`；`nop-batch/nop-batch-gen/src/main/java/io/nop/batch/gen/generator/SequentialBatchRequestGenerator.java:32-52`；`nop-batch/nop-batch-gen/src/main/java/io/nop/batch/gen/loader/BatchGenLoaderProvider.java:58-75`
- **证据片段**:
  ```java
  // BatchGenState（被所有 SequentialBatchRequestGenerator 共享）
  public boolean hasNext() { return genCount < totalCount; }        // :44 读
  public Object next(...) {
      if (!hasNext()) throw new IllegalStateException(...);          // :65 check
      genCount++;                                                    // :68 act（非原子）
      if (isSequential()) return new SequentialBatchRequestGenerator<>(subCases, context);
      ...
      BatchGenState state = subCases.get(subIndex);                  // :78 读
      return state.next(context, produce);
  }
  private boolean subHasNext() { ... subIndex++; ... }               // :88-100 check-then-act

  // SequentialBatchRequestGenerator（consume 阶段在 LoaderState 锁外运行）
  BatchGenState subCase = subCases.get(subIndex);                    // :39
  if (!subCase.hasNext()) return null;                               // :40 check
  subIndex++;                                                        // :43 act
  ```

- **严重程度**: P2
- **并发场景**: gen 任务（generator loader + `useBatchRequestGenerator="true"`，DSL 布尔属性默认 false、无与 concurrency 的联动约束，`batch.xdef:13`）配置 `concurrency>1`：load 阶段在 `synchronized(state)` 内产出 N 个 `SequentialBatchRequestGenerator`（每个持有**同一组** `subCases` BatchGenState 实例），consume 阶段（`BatchProcessorConsumer`→`BatchSequentialProcessor.process`）由多个 chunk 线程**在 LoaderState 锁外**并发调用 `nextRequest`/`onResponse` 驱动共享计数器。
- **触发时序**: T1（chunk 线程 1）在 `SequentialBatchRequestGenerator.nextRequest` 执行到 :40 `subCase.hasNext()`（subIndex=2，hasNext=true）→ T2（chunk 线程 2）同时执行到 :43 `subIndex++` 与 `subHasNext()` 内 :96 `subIndex++`（同一 subIndex 被消费两次/越过边界）→ T1 随后 `subCases.get(subIndex)` 取到越界后的 case 或耗尽的 state → 触发 `:75 IllegalStateException("nop.err.batch.generator-already-finished")`（chunk 失败→任务 fail-fast），或两线程同时通过同一 case 的 hasNext 检查导致该 case 记录重复生成、下一 case 配额（`BatchGenStateBuilder.buildSubStates` 按 weight 预分配的 totalCount）被吞占——生成输出与 case 顺序/配额语义违约。genCount 同理：两线程同读 `genCount=totalCount-1` 各自通过 ：65 检查后双双自增，超发记录。
- **现状**: load 阶段的 genCount 驱动有 `synchronized(state)` 守卫（BatchGenLoaderProvider:58-62），但 sequential 模式把真正的生成推进转移到了 consume 阶段的共享 subCases 计数器上，完全无同步。
- **风险**: 条件满足时（generator+sequential+并发）任务随机失败或产出与声明 case 结构不符的数据；不满足条件时无影响。非默认装配触发，故 P2。
- **建议**: 方向一：generator loader 任务在 build 期检测 `useBatchRequestGenerator && concurrency>1` 时 fail-fast 或强制 concurrency=1；方向二：`BatchGenState.next/subHasNext` 改为 synchronized（对象即游标载体，粒度天然正确）；方向三：SequentialBatchRequestGenerator 的 nextRequest 本身加锁（每 generator 独立实例不行——共享的是 BatchGenState，锁应落在 BatchGenState 上）。
- **失效面枚举（F3）**: `BatchGenState` 可变状态的全部写点/读-改-写点逐一登记：① `BatchGenLoaderProvider.load:71` → `next()`（LoaderState 锁内，安全）；② `SequentialBatchRequestGenerator.nextRequest:47`（`subCase.next(ctx,false)`，锁外，**缺陷现场**）；③ `nextRequest:49`（`subCase.next(ctx,true)`，锁外，**缺陷现场**）；④ `subHasNext:96 subIndex++`（锁外，**缺陷现场**）；⑤ `BatchGenState.next:68 genCount++`（经②③到达时锁外）；⑥ `onResponse:64` 读 `subIndex-1`（锁外，与④交叠时可能取错 case 的 outputVars）。非 sequential（weighted）模式的 subCases 驱动发生在 load 锁内（BatchGenState.next:73-80 经 load 到达），安全。
- **信心水平**: 确定（竞态窗口与共享载体确定；触发需三项配置组合）
- **误报排除**: 非平台设施自身问题（全是本模块类）；非"原语使用即问题"——这里是复合 check-then-act 缺原子性；`ILssue`（hasNext/next 分离）在单线程下是合法迭代器风格，缺陷仅在多线程共享时成立，已按"条件化竞态"降 P2 而非按数据损坏级虚报。
- **复核状态**: 未复核
- **复核状态**：保留 P2；新发现；竞态载体与 F3 五站点逐一亲证成立；触发叙事两处归属精度修正（:43 为 per-generator 字段非共享载体、sequential 配额经 buildSeqStates 非 buildSubStates），不改判级（详见「维度复核结论」二）。

### [C1-003] 生命周期回调"锁内 swap-on-fire"注册路径的守卫检查的是 isDone 而非"已触发"，任务中途注册的回调被静默丢弃（永不执行）

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTaskContextImpl.java:392-402,517-530`；`nop-kernel/nop-core/src/main/java/io/nop/core/context/ExecutionContextImpl.java:83-106,142-163,165-181`
- **证据片段**:
  ```java
  // BatchTaskContextImpl
  public void onTaskBegin(Runnable action) {
      synchronized (this) {
          if (isDone()) {                       // 只检查"已完成"
              throw new IllegalStateException("nop.err.execution-already-completed");
          }
          if (this.onTaskBegins == null)        // fireTaskBegin 置 null 后这里会 new 新列表
              this.onTaskBegins = new ArrayList<>();
          onTaskBegins.add(action);             // 永远不会被触发
      }
  }
  public void fireTaskBegin() {
      List<Runnable> callbacks;
      synchronized (this) {
          callbacks = this.onTaskBegins;
          if (callbacks != null) this.onTaskBegins = null;  // swap-on-fire
      }
      ...
  }
  ```
- **严重程度**: P2
- **并发场景**: 任务 setup 线程在 `BatchTask.executeAsync:135` 执行 fireTaskBegin；任务处理期内任一 chunk 线程通过 `BatchTaskGlobals.onTaskBegin`（xpl 监听注册助手，`BatchTaskGlobals.java:23-26`）动态注册回调。同型缺口在平台基类 `ExecutionContextImpl.onBeforeComplete/onAfterComplete`（fireBeforeComplete:146-147 / fireAfterComplete:168-170 同为锁内 swap-on-fire + isDone 守卫）。
- **触发时序**: T1（setup 线程）已执行完 `fireTaskBegin()`（onTaskBegins=null，任务尚未 done）→ T2（chunk 线程）在首个 chunk 处理中调用 `ctx.onTaskBegin(cleanup)`：进入 synchronized，`isDone()`=false 不抛，`onTaskBegins==null` → new ArrayList → add → 返回成功 → T2 以为注册成功 → `fireTaskBegin` 不会再被调用 → cleanup 回调静默丢弃；若该回调承担资源释放（onTaskBegin 注释语义即"任务开始时分配资源"的配对面），释放不执行。非交错竞态而是"fire 后注册"这个窗口内的确定性丢失，窗口从 fireTaskBegin 持续到任务 complete。
- **现状**: 守卫语义与触发语义脱节：注册端用"已完成"挡迟到注册，触发端用"置 null"保证只发一次，两者之间留下静默吞掉区。9 个 COW 列表回调（onChunkBegin 等）无此问题——COW 快照遍历下迟到注册对**后续** chunk 仍生效；只有 swap-on-fire 的 3 个载体（onTaskBegins、beforeCompletes、afterCompletes）存在丢弃窗口。
- **风险**: 中途注册的终局回调（清理/汇总/审计）静默不执行 → 资源清理不对称（F8 关联面）；对调用方零反馈，排障困难。
- **建议**: onTaskBegin（含平台 onBeforeComplete/onAfterComplete）在持锁检测到"已触发"标记（新增 volatile boolean fired 或复用非空判断）时抛 IllegalStateException（与 isDone 路径同型响亮失败），或立即内联执行回调；同时 `BatchTaskGlobals.onTaskBegin` 文档注明仅限 setup 阶段调用。
- **失效面枚举（F3）**: 同族"注册端守卫 vs 触发端 swap"载体全量清单：① `BatchTaskContextImpl.onTaskBegin/onTaskBegins`（本缺陷）；② `ExecutionContextImpl.onBeforeComplete/beforeCompletes`（同型，fireBeforeComplete:146-147 swap；chunk 与 task context 均继承）；③ `ExecutionContextImpl.onAfterComplete/afterCompletes`（同型，fireAfterComplete:168-170 swap，`BatchTaskGlobals.onTaskEnd`、AsyncFetch:84、ResourceRecord*:105/189/199、JdbcBatchLoaderProvider:185、EtlTaskStateStore 经由它注册——注册点全部在 setup 阶段，当前接线不触发，缺口为潜在面）；④-⑫ `BatchTaskContextImpl` 的 onChunkBegin/onBeforeChunkEnd/onChunkEnd/onChunkTryBegin/onChunkTryEnd/onConsumeBegin/onConsumeEnd/onLoadBegin/onLoadEnd 共 9 处为 COW 列表，无 swap-on-fire，无此缺陷（逐一排除）。
- **信心水平**: 确定（守卫与触发的代码结构确定；"任务中途动态注册"是非常规但接口公开允许的用法）
- **误报排除**: 非低频路径粗锁的优雅性问题（是正确性缺口）；非平台设施"自身"——BatchTaskContextImpl 属 nop-batch 本模块代码，平台同型仅作 F3 登记；判 P2 因当前仓内所有注册点（addListeners→addTaskInitializer→executeAsync:124-127）均在 fire 之前，触发需使用者绕过常规装配。
- **复核状态**: 未复核
- **复核状态**：保留 P2；新发现；onTaskBegin:392-402/fireTaskBegin:518-530 守卫-触发脱节亲证，仓内注册相位全部在 fire 前（addListeners→executeAsync:124-127→fire:135 实证），条件化 P2 成立。

### [C1-004] onTaskEnds 字段为死载体：12 种生命周期回调清单中 onTaskEnd 在 BatchTaskContextImpl 上声明后无任何注册/触发点

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTaskContextImpl.java:70`
- **证据片段**:
  ```java
  private List<Runnable> onTaskBegins;                  // :69 有 onTaskBegin/fireTaskBegin 配对
  private List<Consumer<Throwable>> onTaskEnds;         // :70 全仓无写入、无读取、无 fire
  ```
- **严重程度**: P3
- **并发场景**: 不涉及运行期并发（字段永不被访问）；风险是状态盘点与后续维护层的误导——C1 盘点时它形似"第二个任务终局回调通道"，实际 onTaskEnd 语义由 `ExecutionContextImpl.onAfterComplete`（经 `BatchTaskGlobals.onTaskEnd:28-31`）承接。
- **触发时序**: 无（不可触发；结构性死代码）。按盲审口径仍给出可证伪判据：`grep -rn onTaskEnds nop-batch --include="*.java"` 仅命中声明行本身。
- **现状**: 未删未接线的私有可变字段。
- **风险**: 误导后续开发以为存在独立的 onTaskEnd 注册面（例如在其上加同步/加 fire 时不了解 onAfterComplete 已承载该语义），造成双重触发或漏触发。
- **建议**: 删除该字段；若意图是未来接线 `fireTaskEnd`，补 TODO 注释指向 onAfterComplete 的既有承载。
- **失效面枚举（F3）**: 同类"声明未接线"状态载体排查：grep 全模块所有私有字段与注册/触发配对，仅此一处（onTaskBegins 有配对；8 个 COW 列表均有配对；ExecutionContextImpl.beforeCompletes/afterCompletes 有配对）。无兄弟漏网点。
- **信心水平**: 确定
- **误报排除**: 不属于"无状态只读字段"误报类；作为 C1 附带代码健康项登记而非并发缺陷，判级 P3 与"命名/结构问题"校准一致。
- **复核状态**: 未复核
- **复核状态**：保留 P3；新发现；grep 全仓 `onTaskEnds` 仅声明行 BatchTaskContextImpl.java:70，死字段成立。

### [C1-005] BatchTask 实例复用/重入无运行时守卫，依赖"每执行新建实例"的代码注释约定（F10）

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:43-48,91-94`
- **证据片段**:
  ```java
  // P1-3.3-003（plan 2381 Phase 2）：…全仓调用点均每次新建 BatchTask 单次 executeAsync，per-execution 字段并发安全。
  final AtomicReference<Throwable> primaryErrorRef = new AtomicReference<>();
  ...
  public CompletableFuture<Void> executeAsync(IBatchTaskContext context) {
      primaryErrorRef.set(null);   // :94 复用执行时与在途执行的 CAS 收集直接交叠
  ```
- **严重程度**: P3
- **并发场景**: 同一 `BatchTask` 实例被两个线程同时（或时间重叠地）调用 executeAsync。当前全仓调用点（`BatchTaskManagerImpl.newBatchTask:90-118`、`DefaultBizEntityExporter:52`、`ExportDbTool.runTask:296`、`ImportDbTool` 同型）均每执行新建实例；saveState 任务另有 DB 启动闸门（`DaoBatchStateStore.tryMarkTaskRunning:187-195` 条件更新）兜底同键并发。
- **触发时序**: 理论风险，未具体化（无现实触发方）。若未来出现实例复用：T1 执行 :94 reset 时 T2 的 chunk 线程正处 `primaryErrorRef.compareAndSet(null,e)`（:248）→ 根因被清、终态误判；且两次 setup 会各自 buildLoader（`ResourceRecordLoaderProvider.newLoaderState` 重复打开同一输出文件等）。
- **现状**: 契约只存在于注释（且注释口径是"当前调用点恰好如此"），无 `started` CAS/状态位 fail-fast。
- **风险**: 未来新增调用方复用实例时引入难排查的终态误判与资源重复打开；当前无实际影响。
- **建议**: executeAsync 入口加 `AtomicBoolean` CAS 占位（false→true，重复执行抛 IllegalStateException），与注释声明的契约对齐；成本一次 CAS。
- **失效面枚举（F3）**: 同族"单次执行契约无守卫"载体：① `BatchTask.executeAsync`（本条）；② `IBatchLoaderProvider.setup`/`IBatchConsumerProvider.setup` 各实现（重复 setup 会重复开文件/连接——但 setup 仅由 executeAsync 调用，守卫①即覆盖全部入口，逐一核对无独立暴露面）；③ `SplitBatchConsumer.setup` 每调用新建 activeConsumers，重复 setup 语义自洽。平台侧 Cancellable/ExecutionContext 有各自的 done/claim 守卫，不缺。
- **信心水平**: 确定（守卫缺失为实）＋触发面为理论
- **误报排除**: 不报"AtomicReference 使用"本身；不把"当前无调用方复用"夸大成活性缺陷——按盲审判级校准"写不出具体现实触发链的理论竞态"降 P3（低于标准 P2 理论风险档，因 saveState 路径已有 DB 级闸门兜底且注释已自我声明契约）。
- **复核状态**: 未复核
- **复核状态**：保留 P3；新发现；6 个生产调用点均每次新建（buildTask 全量 grep 实证）+ DB 启动闸门（DaoBatchStateStore:187-195）兜底亲证，理论风险 P3 正确；plan 2381 防御性 reset（BatchTask.java:92-94）为同点位既有缓解，非本条闭环。

---

## 三、维度集外线索（不立案展开）

### [nop-batch-CL-001]（维度集外线索，不立案展开）
- **形态归属**: C2（锁与同步深水区）
- **严重度候选**: P2（可证明的争用热点）
- **一句话证据**: `DaoBatchStateStore` 为全 JVM 单例（`BatchTaskManagerImpl:65` 单点 @Inject）且 `saveTaskState` 为 `synchronized`（`DaoBatchStateStore.java:234`），锁内含 `updateTask`（DB 更新，:253-254）——所有并发批任务的所有 chunk 提交串行经过同一把全局锁包住一次数据库往返。
- **复核状态**：确认；建议归属 C2 / P2；synchronized saveTaskState 与锁内 updateTask 亲证，singleton 注入点唯一。

### [nop-batch-CL-002]（维度集外线索，不立案展开）
- **形态归属**: C2（computeIfAbsent 内重逻辑）
- **严重度候选**: P2/P3
- **一句话证据**: `BatchLoaderHelper.batchLoadWithProvider:21` 经 `IEvalScope.computeIfAbsent`（`IEvalScope.java:45-53`，`synchronized(this)`）执行 `provider.setup`（ResourceRecordLoaderProvider 可开文件/编码探测），且此后每次 load 都要过任务级 eval-scope 监视器——原子性成立，锁扩展为热路径代价。
- **复核状态**：确认；建议归属 C2 / P3（原 P2/P3 取低档：原子性成立、纯锁扩展代价，无正确性后果）。

### [nop-batch-CL-003]（维度集外线索，不立案展开）
- **形态归属**: C3（异步链路，阻塞折叠）
- **严重度候选**: P2
- **一句话证据**: `GraphQLFetchResultBatchLoader.load:29` 在 chunk 工作线程上 `FutureHelper.syncGet(loadAsync(...))` 阻塞等待 GraphQL 异步结果，异步异常/取消语义在阻塞点折叠为同步异常，超时控制交由底层 future。
- **复核状态**：确认；建议归属 C3 / P3（原 P2 降档：同步 `IBatchLoader.load` 契约下阻塞折叠为接口形态使然，超时由底层 future 治理，无无界阻塞面）。

### [nop-batch-CL-004]（维度集外线索，不立案展开）
- **形态归属**: C4（线程池治理）/C5
- **严重度候选**: P2
- **一句话证据**: `ExportDbTool.java:150`、`ImportDbTool.java:232` 以 `DefaultThreadPoolExecutor.newExecutor(name, threadCount, Integer.MAX_VALUE)` 建表级并行池（队列容量 Integer.MAX_VALUE 无界），finally `executor.destroy()`；表任务 concurrency>1 时又切到 `GlobalExecutors.cachedThreadPool()`（:284/:296 附近，无界线程数）。
- **复核状态**：确认；建议归属 C5（C4 关联）/ P2；ExportDbTool:150/ImportDbTool:232 队列 Integer.MAX_VALUE 与 :284/:296 cachedThreadPool 亲证。

### [nop-batch-CL-005]（维度集外线索，不立案展开）
- **形态归属**: C3/F9（回调异常语义不对称）
- **严重度候选**: P3
- **一句话证据**: `BatchTaskContextImpl` 仅 `fireChunkEnd`（:555-566）对监听器异常逐个 catch 记日志，其余 8 个 fire*（:496-541,:568-607）直接传播——监听器抛错即 chunk 失败→任务 fail-fast，响亮但语义不一致。
- **复核状态**：确认；建议归属 C3/F9 / P3；fireChunkEnd（:555-566）逐 callback catch Exception 与其余 8 个 fire* 直接传播亲证；Error 级缺口并入 CL-026（C6-001）口径。

### [nop-batch-CL-006]（维度集外线索，不立案展开）
- **形态归属**: C9（热路径串行化核对项）
- **严重度候选**: P3（多为游标语义所必需）
- **一句话证据**: 6 个 per-setup LoaderState/ConsumerState 的 `synchronized(state)` 站点（ResourceRecordLoaderProvider:173、ResourceRecordConsumerProvider:176、OrmQueryBatchLoaderProvider:102、JdbcBatchLoaderProvider:187、JdbcPageBatchLoaderProvider:79、BatchGenLoaderProvider:59）串行化全部 chunk 的读/写游标推进——正确性必需（单游标），concurrency>1 时为天然串行点，C9 需评估其吞吐上限声明。
- **复核状态**：确认；建议归属 C9 / P3；六站点逐一亲证；与 CL-027 合并口径（generator 游标串行点为同族第 7 站点）。

---

## 四、误报排除与已验证安全项汇总

1. **`completedIndex` 可见性**: volatile 写点唯一运行期来源 `ResourceRecordLoaderProvider.onChunkEnd:301`，TreeMap 头部压缩保证值单调，读方（saveTaskState/下次恢复 getSkipCount）均为 volatile 读——无撕裂、无回退。
2. **AtomicLong set/inc 交叠**: 所有 set*Count 调用点（DaoBatchStateStore:173-176、EtlTaskStateStore.loadTableState:87-105）均在 chunk 线程启动前（setup 阶段），executor 提交边建立 HB；运行期纯 inc（addAndGet）——无丢失更新。
3. **chunk 上下文隔离**: `processChunk:292` 每 chunk `newChunkContext()`，可变字段线程封闭；并发 chunk 共享的只有任务级 volatile/AtomicLong/COW 列表/CHM——隔离成立。
4. **EvalScope 跨线程共享**: 任务 scope=CHM 背书，chunk scope=child scope（各自 CHM 本地 map + 父只读继承），`ExportDbTool/ImportDbTool` 的共享父 scope 仅在 executor 提交前写——无数据竞争。
5. **PartitionDispatchQueue**: 全部共享字段（partitions/count/threadId）在 ReentrantLock 内、finished/noMoreData volatile、等待点均带 500ms 超时（F5 满足）、fetchMutex 保页序——本模块同步最完善的类，无发现。
6. **Cancellable 取消可见性**: `cancelled` volatile（平台类），fail-fast `context.cancel(CANCEL_REASON_STOP)`（BatchTask:254）对兄弟 chunk 循环顶 `isCancelled()`（:234）可见。
7. **EtlTaskStateStore 锁模型**: synchronized 方法全部声明在外部类（loadTableState:84/saveTableState:108/complete:51），内部类 EtlTableStateStore 经由外部类方法进入 → 全表任务共用 outer 监视器，saveStateFile（临时文件+原子 move）互斥成立——初审曾疑"内部类监视器分裂"，经逐行核实**不成立**，予以排除。残余两点（`isCompleted():39` 无锁读 taskState、`reset():35` 无锁写）当前接线无跨线程对手方，不立案。
8. **JdbcBatchLoaderProvider 关闭协议**: isClosed 检查/close/load 全在 `synchronized(state)` 内（:169-179,187,280-294），EOF 返回空列表语义有注释与测试锚点——无 use-after-close 竞态。
9. **JdbcBatchConsumerProvider 实例字段回填缺陷已修**：setup 局部化字段读取（代码注释 + TestJdbcBatchConsumerProviderSharedField），不重复立案。
10. **params 只读契约**：`IBatchTaskContext.getParams` JavaDoc 明文"任务执行过程中不会被修改"——运行期 setParam 属契约违约而非引擎缺陷。

## 五、总计

| 判级 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 3 | C1-001（TL 嵌套清除）、C1-002（BatchGenState 游标竞态）、C1-003（swap-on-fire 注册丢弃） |
| P3 | 2 | C1-004（onTaskEnds 死字段）、C1-005（重入无守卫，理论风险） |
| 维度集外线索 | 6 | CL-001~CL-006（C2×2、C3×2、C4×1、C9×1） |

C1 结论：nop-batch 的共享可变状态守卫整体质量较高（volatile/AtomicLong/COW/CHM/per-state 监视器使用规范，chunk 隔离与游标状态封闭设计清晰）；3 条 P2 均为**组合/时序条件化**缺口（嵌套批执行、generator+并发配置、中途动态注册），无默认装配即触发的主路径数据竞争。

---

## 维度复核结论（独立复核子代理，2026-10-16）

> 复核人：C1 维度独立复核子代理（与初审代理相互独立，怀疑姿态逐条复核）。复核对象：本文件 C1-001~005 + CL-001~006 与 `C1-round2.md` C1-006~007 + CL-026/027，合计 7 条发现 + 8 条 CL 线索。
>
> **格式变体注记**：第 1 轮发现条目头部使用短 ID 变体 `[C1-001]`~`[C1-005]`（无单元前缀），维度集外线索使用完整 ID `[nop-batch-CL-001]`；第 2 轮新发现同为短 ID `[C1-006]`/`[C1-007]`。本节统一以完整 ID `nop-batch-C1-001` 口径裁定。各条目原有 `- **复核状态**: 未复核` 字段按禁改规则保留原样，其下方追加的 `**复核状态**：…` 行为本次复核回填，以追加行为准。

### 一、逐条裁定表

| 发现 ID | 裁定 | 现状 | 一句话理由 |
|---|---|---|---|
| nop-batch-C1-001 | 保留 P2 | 新发现 | 全部 file:line 亲证、嵌套链路三要素实证成立；但失效末步（`useTaskContext()` 消费）全仓零调用方，「嵌套执行 + 后续 TL 消费」两条件合取不达 P1 |
| nop-batch-C1-002 | 保留 P2 | 新发现 | 共享游标竞态载体与 F3 五站点逐一亲证成立；触发叙事两处归属精度修正（不判级，见二） |
| nop-batch-C1-003 | 保留 P2 | 新发现 | onTaskBegin/fireTaskBegin 守卫-触发脱节亲证；仓内注册相位全部在 fire 前，条件化 P2 正确 |
| nop-batch-C1-004 | 保留 P3 | 新发现 | grep 全仓 `onTaskEnds` 仅声明行 BatchTaskContextImpl.java:70，死字段成立 |
| nop-batch-C1-005 | 保留 P3 | 新发现 | 6 个生产调用点均每次新建 + DB 启动闸门兜底均亲证；无现实触发链，理论风险 P3 正确 |
| nop-batch-C1-006 | 保留 P2 | 新发现 | 装配现实性三证据与跨线程机制全部亲证（见二）；三属性组合非默认，P2 正确 |
| nop-batch-C1-007 | 保留 P3 | 新发现 | complete() 两段窗口与失败路径跳过 beforeCompletes 均为代码结构事实（亲证）；现实后果良性，P3 校准正确 |

### 二、特别复核指令与 P1 候选/精度子项明细

**C1-001（特别指令：嵌套链路可达性核实 + P1 重估）**
- 证据子项：BatchTask.java:105/155/231/256、BatchTaskGlobals.java:9-21（provide=set / remove=remove，无 save-restore）、U1~U6 六助手（:23-48）无 null 守卫、IBatchTask.java:21-23 `execute = FutureHelper.syncGet(executeAsync)`——逐行亲证，行号零漂移。
- 链路子项（三要素全部实证）：① batch.xlib `<batch:Execute>` 的 `asyncExec` 为 optional 属性（:17），缺省 falsy → :50 `batchTask.execute(batchTaskContext)` 同步执行；② batch-gen.xlib `PostExtendsForTaskFlow`（:51-64）对 `processor[task:taskModelPath]` 自动嵌入 `<task:Execute taskModelPath=... inputs="${{item,consume,batchChunkCtx}}">`；③ demo 形态实证（batch-gen-demo.task.xml:9,32、batch-demo.task.xml:8,28 确为「任务流内含 batch:Execute step」）。**精度修正**：两 demo 的 batch:Execute 步骤均显式 `batch:asyncExec="true"`（流程级异步），非「缺省同步」示例；但 executeAsync 的 provide(:105)/remove(:155) 无论 asyncExec 取值都在调用线程同步段执行，链路可达性结论不变。
- 判级子项（P1 重估）：P1 要求「时序每步可信」，本链最后一步断裂——`BatchTaskGlobals.useTaskContext()` 全仓（src/main + 全部 xlib/xpl/xml 资源）零调用方（本轮双 grep 实证），引擎自身后续执行不读 TL；确定性丢失成立，但确定性 **NPE** 需「嵌套执行 + 该线程后续存在 TL 消费者」两条件合取且第二步仓内无实例。嵌套 DSL 组合属用户编排的一等路径但非默认装配。**维持 P2**；第 2 轮的证据升级（自「非常规用法」修正为「DSL 一等组合路径」）予以确认，修复优先级应相应前置（save/restore 成本极低）。
- 精度修正（影响面表述）：原触发时序「外层 A 余下所有 chunk 均处于无 TL 状态」过宽——仅执行嵌套的那个 chunk 线程 TL 被清空，A 的其他 chunk 线程各自在 ：231 独立 provide、不受影响（除非同样执行嵌套）。
- 精度修正（子分支表述）：「同步执行器（SyncThreadPoolExecutor，ExportDbTool concurrency=1 路径）」子分支与现状装配不符——BatchTask.getExecutor（:83-88）在 executor==null 时无条件回退 cachedThreadPool，SyncThreadPoolExecutor 仅出现于 ExportDbTool:152 的表级 futures 池；该子分支不影响主时序（主时序仅依赖嵌套 executeAsync 在同线程执行 provide/remove）。

**C1-002（子项明细）**
- 证据子项：hasNext:44-46、next:64-86（genCount++ :68）、subHasNext:88-100（subIndex++ :96）、BatchGenLoaderProvider:58-62 load 锁、SequentialBatchRequestGenerator:39-43 锁外驱动共享 subCases——全部亲证成立。
- 精度修正一（不判级）：触发时序中「:43 subIndex++」为 SequentialBatchRequestGenerator 的 per-generator 实例字段，sync 模式下每 generator 由单 chunk 线程处理（线程封闭）；跨 chunk 真正共享的竞态载体是各 sub-case BatchGenState 的 genCount（nextRequest :40/:47/:49 → BatchGenState:65/:68）与嵌套 sequential 子 case 的 subHasNext（:96）。F3 表列位本身正确。
- 精度修正二（不判级）：「配额（buildSubStates 按 weight 预分配的 totalCount）被吞占」引用了 weighted 模式构建器；缺陷现场 sequential 模式经 buildSeqStates（BatchGenStateBuilder:18-30）每子 case 独占全量 totalCount。竞态后果（超发/`generator-already-finished` ISE/同 case 重复消费）不受影响。
- 判级子项：generator loader + sequential + concurrency>1 三配置合取、非默认装配，P2 正确。

**C1-006（特别指令：装配现实性核实）**
- 装配子项：① xdef 层 `useBatchRequestGenerator="!boolean=false"`（batch.xdef:13）与 `asyncProcessor="boolean"`（:15）为两个独立布尔属性，无联动约束、无互斥校验；② builder 层 BatchTaskBuilder:396-403 对该组合零校验；③ BatchProcessorConsumer:70 注释明文支持「processor 内部可能异步执行」，chunk latch 的 `countDown()` 生产代码零调用方（由异步 processor 契约负责）——异步 processor 属公开扩展面，组合可达性成立。
- 机制子项：BatchSequentialProcessor:33-48 do-while 不等响应继续 nextRequest（结构性跑赢）；subIndex/subContext 实例字段（:23-24）；onResponse:64-72 跨线程读 subIndex 并写 subContext 局部 map——`newChildScope()` 默认 `threadSafe=false` → HashMap（IEvalScope:73-75 + EvalScopeImpl:94-98 亲证）。全部成立。
- 判级子项：三属性组合非默认 → P2 正确；「近确定性错位」成立（循环必然先于响应推进，越界时退化为 IndexOutOfBoundsException 响亮失败）。第 1 轮 C1-002 失效面⑥的归属修正（仅 async 模式取错 case）经核实正确。

**C1-003/004/005/007（常规子项复核）**
- C1-003：注册守卫（:392-402 isDone + null→new ArrayList）与触发 swap（:518-530）亲证；9 个 COW 列表（BatchTaskContextImpl:71-81）与 9 个注册方法逐一配对亲证；U1~U6 全仓零调用方实证 → 条件化 P2 维持。笔误登记：第 1 轮 §E 表「8×CopyOnWriteArrayList」应为 9，以 C1-003 F3 与实际代码为准。
- C1-004：grep 全仓仅声明行 ：70，死字段成立，P3 维持。
- C1-005：`buildTask()` 全部 6 个生产调用点（BatchTaskManagerImpl:93/107/117、DefaultBizEntityExporter:52、ExportDbTool:296、ImportDbTool:337）均每次新建实例亲证；tryMarkTaskRunning 条件更新闸门（DaoBatchStateStore:187-195）亲证；「写不出现实触发链」成立，P3 维持。
- C1-007：complete() 两段结构（ExecutionContextImpl:109-128）、completeExceptionally 跳过 beforeCompletes（:131-139）、守卫仅查 isDone（:83-93）、失败路径 BatchTask:204-212、trailer 注册点 ResourceRecordConsumerProvider:131/142 与 processingItems 检查 ResourceRecordLoaderProvider:221-227——全部亲证；仓内注册相位全部在 fire 前（addListeners → addTaskInitializer → executeAsync:124-127 → fireTaskBegin:135），变体一为理论面、变体二确定性但后果良性，P3 校准正确。

### 三、维度集外线索处置（确认/驳回二态）

| 线索 | 处置 | 建议归属维度/严重度 | 一句话依据 |
|---|---|---|---|
| nop-batch-CL-001 | 确认 | C2 / P2 | DaoBatchStateStore:234 synchronized + :253-254 锁内 DB 更新亲证；stateStore 单点 @Inject 亲证。形态真实、独立行动意义成立 |
| nop-batch-CL-002 | 确认 | C2 / P3 | BatchLoaderHelper:21-22 + IEvalScope.computeIfAbsent（synchronized default，:44-53）亲证；原子性成立、纯锁扩展代价，取 P3 档 |
| nop-batch-CL-003 | 确认 | C3 / P3 | syncGet 亲证；同步 `IBatchLoader.load` 契约下阻塞折叠为接口形态使然、超时由底层 future 治理，建议自 P2 降档 P3 |
| nop-batch-CL-004 | 确认 | C5（C4 关联）/ P2 | ExportDbTool:150/ImportDbTool:232 无界队列 + :284/:296 cachedThreadPool 亲证 |
| nop-batch-CL-005 | 确认 | C3/F9 / P3 | fireChunkEnd（:555-566）与其余 8 个 fire* 的异常语义不对称亲证；Error 级缺口归 CL-026（C6-001）口径 |
| nop-batch-CL-006 | 确认 | C9 / P3 | 六站点逐一亲证；与 CL-027 合并口径 |
| nop-batch-CL-026 | **确认，与 C6-001 合并裁定** | 归属 nop-batch-C6-001（P1） | 同位点实证：BatchTask.java:243 `catch (Exception e)`（本轮独立亲证）= C6-round2 终态化阻断站点表 S1 首行；Error → future 永不完成的后果面（资源泄漏/任务行 RUNNING/调用方挂死）归 C6-001，不重复立案 |
| nop-batch-CL-027 | 确认 | C9 / P3（并入 CL-006 口径） | generator 游标串行点为 CL-006 同族第 7 站点，合并评估吞吐声明 |

### 四、现状标注依据

- **新发现（7/7 条 + 8/8 条 CL）**：`cra-index.md` grep `nop-batch` 零条目——WI 1.1–1.6 审计对象为 stream/job/wf/task/retry/tcc，与本单元零代码交集，同族不同模块仍标新发现。
- 历史审计对照：2026-09 deep-audit `07-job-task-batch-retry-tcc.md` 与 code-review `3.3/3.4-batch-deep-audit` 对 BatchGenState/BatchTaskContextImpl 仅涉裸 ISE/错误码问题（G7-09-01 等），无本组并发缺陷记录；plan 2381 P1-3.3-003（primaryErrorRef）与 plan 2282 G7-04-01（启动闸门）为 C1-005 同点位的既有部分缓解，已在条目复核状态行备注，不构成任何条目的「已修复」闭环。
- git log nop-batch 近 8 个 commit 仅为 build/infra 与 R1.15/R1.16 记录面修复，无并发修复——无「已修复」项、无「已知未修复」项。

### 五、复核统计

| 项 | 数量 | 明细 |
|---|---|---|
| 发现裁定 | 7 | 保留 7（P2×4：C1-001/002/003/006；P3×3：C1-004/005/007）；降级 0；驳回 0 |
| CL 线索 | 8 | 确认 8；驳回 0；其中 CL-026 并入 nop-batch-C6-001、CL-027 并入 CL-006 口径（独立行动项净减 2） |
| 现状标注 | 15 | 新发现 15（已知未修复 0 / 已修复 0） |
| 证据复核 | 7+8 | 全部 file:line 亲自打开核对，行号零漂移；精度修正 4 处（C1-001 影响面/子分支、C1-002 两处、demo asyncExec 形态），均不改判级 |
| F3 失效面 | 7 | 均非空且经独立 grep 验证闭合，无补全项；格式笔误 1 处（§E「8×COW」应为 9，已登记） |

---

## 最终保留项

| ID | 标题 | 最终严重度 | 现状 |
|---|---|---|---|
| nop-batch-C1-001 | BatchTaskGlobals ThreadLocal provide/remove 不保存恢复外层上下文，嵌套批任务执行后外层任务上下文永久丢失 | P2 | 新发现 |
| nop-batch-C1-002 | BatchGenState 共享游标计数器（genCount/subIndex）在 consume 阶段被多 chunk 线程无锁驱动 | P2 | 新发现 |
| nop-batch-C1-003 | 生命周期回调 swap-on-fire 注册守卫检查 isDone 而非已触发，任务中途注册的回调被静默丢弃 | P2 | 新发现 |
| nop-batch-C1-006 | SequentialBatchRequestGenerator 实例字段（subIndex/subContext）asyncProcessor 模式跨线程无同步驱动：outputVars 错位 + HashMap 数据竞争 | P2 | 新发现 |
| nop-batch-C1-004 | onTaskEnds 死载体字段 | P3 | 新发现 |
| nop-batch-C1-005 | BatchTask 实例复用/重入无运行时守卫（理论风险） | P3 | 新发现 |
| nop-batch-C1-007 | ExecutionContextImpl complete() 两段窗口注册丢弃 + 失败路径 beforeCompletes 确定性不执行且未声明 | P3 | 新发现 |

### 维度集外线索（确认）

| ID | 形态归属 | 严重度 | 处置注记 |
|---|---|---|---|
| nop-batch-CL-001 | C2 | P2 | 独立行动项 |
| nop-batch-CL-002 | C2 | P3 | 独立行动项 |
| nop-batch-CL-003 | C3 | P3 | 独立行动项（建议自 P2 降档） |
| nop-batch-CL-004 | C5/C4 | P2 | 独立行动项 |
| nop-batch-CL-005 | C3/F9 | P3 | 独立行动项 |
| nop-batch-CL-006 | C9 | P3 | 与 CL-027 合并口径 |
| nop-batch-CL-026 | C6 | P1 | 与 nop-batch-C6-001 合并裁定（同位点 S1），不另立 |
| nop-batch-CL-027 | C9 | P3 | 并入 CL-006 |
