# nop-batch 并发·异步·资源专项审计 — C1 共享可变状态与可见性（第 2 轮盲审深挖）

- **审计单元**: nop-batch 聚合器（11 个含 Java 的 jar 子模块：api/app/biz/core/dao/dsl/exp/gen/jdbc/orm/service），src/main 手写代码 218 files / 15,698 行（不含 `_` 前缀生成物与 target/）
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **本轮输入**: 第 1 轮报告（同目录 `C1-shared-mutable-state.md`，C1-001~C1-005 + CL-001~CL-006）；基线 `_tmp/cra-baseline-nop-batch.txt`（C1 三节 + C2 锁载体节）；live code（本轮全部重新取证）
- **盲审纪律声明**: 本轮未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件，唯一例外为本维度第 1 轮文件 `C1-shared-mutable-state.md`（任务指定的必要输入）；未读取同目录 C5/C6/C7/C8 等其他维度文件。所有现状判断均为本轮对 live code 的独立取证。
- **本轮目标（对应深挖指令①~⑤）**:
  - ① C1-001 失效面扩展：`BatchTaskGlobals.provide/remove/current` 全调用站配对矩阵 + `NamedThreadLocal`/ThreadLocal 同族穷举
  - ② C1-002 家族扩展：batch-gen 子模块全部共享可变状态盘点（BatchGenState 之外）+ core 侧 `concurrency>1` 时 chunk 上下文隔离性核查
  - ③ C1-003 swap-on-fire 家族全扫：全部回调注册站点守卫形态判定表 + "中途注册被静默丢弃"变体登记
  - ④ 发布安全与可见性：非 final 字段跨线程发布、double-checked locking 残留、EtlState/EtlTaskStateStore 跨线程可见性
  - ⑤ 并发 chunk 下的 listener 触发重入：CopyOnWriteArrayList 注册表 fire 期间并发注册语义、同一 listener 并发多次触发面

---

## 一、指令① — C1-001 失效面扩展：BatchTaskGlobals provide/remove 配对矩阵

### 1.1 全仓调用站点配对矩阵（grep 全仓 src/main + xlib/xpl/task.xml 资源，逐一登记）

| # | 站点 | 所在线程 | 动作 | 旧值保存/恢复 | 配对纪律 | 判定 |
|---|---|---|---|---|---|---|
| P1 | `BatchTask.executeAsync` → `BatchTaskGlobals.provideTaskContext`（BatchTask.java:105） | executeAsync 调用线程 | `set`（直接覆盖） | **无**（不读 `s_taskContext.get()` 保存旧值） | ↔ R1 同方法 finally | 覆盖型 provide：嵌套执行时破坏外层 |
| R1 | `BatchTask.executeAsync` finally（:155） | 同 P1 线程 | `remove` | — | ↔ P1 | 成对但**清空型**：不恢复 P1 之前的旧值 |
| P2 | `BatchTask.executeChunkLoop` lambda → provide（:231） | chunk 线程（executor 提交） | `set`（直接覆盖） | **无** | ↔ R2 同 lambda finally | 同型 |
| R2 | `BatchTask.executeChunkLoop` lambda finally（:256） | 同 P2 线程 | `remove` | — | ↔ P2 | 同型 |
| U1~U6 | `BatchTaskGlobals.useTaskContext()` 的 6 个消费助手：onTaskBegin(:24)/onTaskEnd(:29)/onBeforeTaskEnd(:34)/onChunkBegin(:39)/onBeforeChunkEnd(:43)/onChunkEnd(:47) | 任意调用线程 | `get` | — | 无配对需求 | **均无 null 守卫** → TL 缺失时确定性 NPE |

配对完整性结论：provide 站点全仓仅 P1/P2 两处，remove 站点仅 R1/R2 两处，两对各自同方法内 try/finally 严格成对（池化线程无残留泄漏——第 1 轮结论维持）。模块外（nop-batch 之外）无任何 `provideTaskContext/removeTaskContext` 调用方；`BatchTaskGlobals` 未绑定到任何 xlib/xpl 标签（本轮 grep 全部 `*.xlib/*.xpl/*.task.xml` 零命中），即 U1~U6 六个动态注册助手当前**仓内零调用方**，仅作为公开 Java API 供下游使用——C1-003 的"中途动态注册"触发面因此校准为"下游用户可达、仓内无现成触发者"。

### 1.2 平台同族 ThreadLocal 站点对照（穷举 `NamedThreadLocal`/`ThreadLocal` 全仓 main 代码）

| 载体 | 纪律形态 | 对照结论 |
|---|---|---|
| `BatchTaskGlobals.s_taskContext`（nop-batch 唯一 TL，baseline C1 节确认） | set/remove 平衡但**不保存恢复** | **唯一离群者** |
| `ResourceDependsManager.dependsStack`（nop-core，"resource-depends-stack"） | `ignoreDepends`（:133-141）save-old → set(null) → finally restore；`collectDepends`（:81-104）栈式 push/pop + 空栈时 remove | 标准 save/restore 范本 |
| `ContextProvider.runWithProxyContext`（nop-api-core，:147-155） | attach(new) → finally attach(prevContext) | 标准 save/restore 范本 |
| `ContextProvider.runWithContext`（:157-167） | 保存 oldContext → finally 条件 close；`BaseContext.close()`（BaseContext.java:315-327）仅在 `context == this` 时自清理 TL——池化 chunk 线程上重复运行不会残留 closed context（本轮逐行核实的池化安全性） | 条件清理，无嵌套破坏 |
| `Cancellable.appendOnCancel`（nop-commons） | 注册时已取消 → 立即执行回调（tryRunCallbacks swap-on-canceled） | **注册-触发竞态的正确范本**（无静默丢弃），与 C1-003 的 onTaskBegin 形成直接对照 |

结论：BatchTask 的两处 provide 是全仓唯一不保存恢复旧值的 TL 用法；修复方向（save/restore）在平台内有三个现成范本可循。

### 1.3 C1-001 失效面扩展结论（升级证据，不升级判级）

第 1 轮将触发条件描述为"非常规但接口公开允许的用法"。本轮取证将触发条件修正为**DSL 一等组合特性路径**：

1. `batch.xlib` 的 `<batch:Execute>`（batch.xlib:14-58）默认 `asyncExec` 缺省为 falsy → `batchTask.execute(batchTaskContext)` **同步**执行（:50），即嵌套执行的 TL 覆盖路径是 DSL 显式支持的模式；
2. `batch-gen.xlib` 的 `PostExtendsForTaskFlow`（batch-gen.xlib:54-63）会把 `<task:Execute taskModelPath=... inputs="${{item,consume,batchChunkCtx}}">` **自动嵌入 processor 源**——processor 内执行任务流是框架自动接线；任务流内再含 `<batch:Execute>` step（nop-cli demo `batch-gen-demo.task.xml:9,32`、`batch-demo.task.xml:8,28` 即为此形态）即构成"外层批任务 chunk 线程 → 任务流 → 内层批任务同步执行"的完整链；
3. 该链上 C1-001 的确定性序列不变（内层 :155 remove 后外层 TL 永久丢失，U1~U6 后续调用 NPE 或 TL 依赖代码静默失效），且 demo 文件证明各环节均为官方示例用法。

判级维持 **P2**：链条仍需用户在同一任务流中组合"processor 引用 taskModelPath + 后续 batch:Execute step"（或显式批套批），非默认装配即触发；后果响亮（NPE）可定位。修复建议不变（save/restore 结构），并新增一条：`provideTaskContext` 检测到非空旧值时打 WARN 日志，可为 DSL 组合场景提供可观测性。

---

## 二、指令② — C1-002 家族扩展：batch-gen 全共享可变状态 + core chunk 隔离核查

### 2.1 batch-gen 子模块共享可变状态全盘点（876 行全读）

| 载体 | 写者 | 读者 | 同步 | 判定 |
|---|---|---|---|---|
| `BatchGenState.{genCount,subIndex}` | load 阶段（LoaderState 锁内，BatchGenLoaderProvider:59-62）/ **consume 阶段 SequentialBatchRequestGenerator.nextRequest 锁外** | 多 chunk 线程 | 无（consume 侧） | **已立案 C1-002**（维持） |
| `SequentialBatchRequestGenerator.{subIndex,subContext}`（:23-24） | nextRequest（chunk 循环线程）/ onResponse（**async 模式下异步回调线程**） | 同左 | 无 | **新发现 → C1-006**（sync 模式下线程封闭安全） |
| `BatchGenContextImpl.{scope,producer}`（:17-18） | 构造器 / `setEvalScope`/`setProducer`（:44-50，全仓无运行期调用方） | 各 generator | 构造后只读 | 安全 |
| `BatchGenContextImpl.newSubContext()`（:30-32） | 每 generator 构造时一次 | 该 generator 独占 | 每 generator 独立 child scope（局部 map 为 **HashMap**，EvalScopeImpl:97 `threadSafe ? CHM : HashMap`，`newChildScope()` 默认 `threadSafe=false`） | sync 模式封闭安全；**async 模式成为竞态载体 → 归入 C1-006** |
| `BatchTemplateBasedProducer`（:23-42） | 构造器（registry/exprParser 均 final） | 各 generator | 无状态 | 安全 |
| `BatchGenModel`/`BatchGenCaseModel`（model 层） | BatchGenModelParser.parseFromResource（setup 期一次） | load/consume 各线程 | setup 后只读（mergedTemplate setter 仅 parse 期） | 安全 |
| `BatchGenLoaderProvider.LoaderState{genModel,genState,genContext}`（:44-48） | setup | load 闭包 | `synchronized(state)` 全包含（:59-62） | load 侧安全 |
| `BatchGenStateBuilder`（:17-53） | 静态方法 | 构造期 | 无状态（CollectionHelper.sumDouble 只读入参） | 安全 |

### 2.2 core 侧 `concurrency>1` chunk 上下文隔离性核查（逐字段）

1. **chunk scope 隔离（本轮重点复核，结论：隔离成立）**：`BatchChunkContextImpl` 构造器（:39-43）调用 `super(context.getEvalScope())` → 命中 `ExecutionContextImpl` 受保护构造器（ExecutionContextImpl.java:49-52）`parentScope.newChildScope(attributes)`——chunk 上下文的 eval scope 是**任务 scope 的 child scope**，其局部 map 是 chunk 自身的 `attributes` CHM；`setLocalValue(VAR_BATCH_CHUNK_CTX, this)`（BatchChunkContextImpl:42）落在 chunk 本地 map，不写共享任务 scope。并发 chunk 各持独立局部 map，变量解析沿父链只读继承——**本轮曾怀疑"多个 chunk 写共享 scope 同一槽位"，经逐行核实不成立**，第 1 轮"chunk 间隔离成立"结论维持并补齐了构造器级证据链。
2. **任务级共享面在并发 chunk 下的复合操作**：11 个 `AtomicLong` 计数器——运行期全部为 `addAndGet`（inc*），`set*Count` 调用点经逐一 grep 确认**全部在 setup 阶段**（DaoBatchStateStore.loadTaskState0:170-179、EtlTaskStateStore.loadTableState:84-106，均在 chunk 线程提交前），无 set/inc 交叠；`completedIndex` volatile 且唯一运行期写点 `ResourceRecordLoaderProvider.onChunkEnd:301` 在 `synchronized(state)` 内、TreeMap 头部压缩保证单调；8+2 个 COW 注册表/CHM 原语自身安全。
3. **`BatchTask.incCount` 两步复合（BatchTask.java:364-367）**：`incCompleteItemCount(delta)` 与 `incHistoryItemCount(h)` 是两次独立原子操作，并发 chunk 间可产生跨计数器撕裂读（saveTaskState 先读 complete 后读 history 之间另一 chunk 完成了第二步）。校准：两计数器间无引擎侧不变式（无任何代码校验 `complete >= history` 之类），撕裂读仅影响恢复时计数展示；数据级恢复依赖 `completedIndex`（单调、锁内推进）与 history store，符合已声明的 at-least-once 契约——**登记为已验证边界，不立案**。
4. **cancel 复合操作**：`Cancellable.cancel`（Cancellable.java:35-45）已带 synchronized CAS 单赢家纪律（WI20/MSG-F-25 注释锚点），多 chunk fail-fast（BatchTask:253-254）与外部 kill 并发时 cancelReason 归因确定——第 1 轮结论复核成立。
5. **fireChunkBegin/chunkContext.setConcurrency/setThreadIndex**（BatchTask:292-294）：每 chunk 新实例上单线程写，线程封闭。

---

## 三、指令③ — C1-003 swap-on-fire 家族全扫：回调注册站点守卫形态判定表

全部注册方法（接口 `IBatchTaskContext` 9 项 + 继承 `IExecutionContext` 2 项 + 死字段 1 项 + 平台 Cancellable 对照）逐一判定：

| # | 注册方法 | 载体（声明处） | 载体类型 | 注册守卫 | 触发方式 | "fire 后注册"丢弃变体 | 仓内实际注册点及相位 |
|---|---|---|---|---|---|---|---|
| 1 | `onTaskBegin` | `onTaskBegins`（BatchTaskContextImpl:69，非 final） | ArrayList **swap-on-fire** | `synchronized(this)` + `isDone()`（:392-402） | `fireTaskBegin`（:518-530）锁内 swap→锁外执行 | **有**（C1-003 主现场：fire 后→done 前注册被接受且永不触发） | ModelBasedBatchTaskBuilderFactory:547（addTaskInitializer→executeAsync:125，先于 :135 fire——安全相位） |
| 2 | `onBeforeComplete` | `beforeCompletes`（ExecutionContextImpl:33） | ArrayList **swap-on-fire** | `synchronized(this)` + `isDone()`（:83-93） | `fireBeforeComplete`（:142-163）锁内 swap→锁外执行 | **有**，且窗口更细：`complete()` 两段式（:109-128）——`fireBeforeComplete` swap 之后、`done=true` 之前注册被接受且永不触发；成功路径 BatchTask:192 与 complete() 内两次 fire 可兜住极窄窗口 | ResourceRecordConsumerProvider:131,142（setup 相位）、ResourceRecordLoaderProvider:199,221（setup 相位）——均先于 fireTaskBegin |
| 3 | `onAfterComplete` | `afterCompletes`（ExecutionContextImpl:34） | ArrayList **swap-on-fire** | `synchronized(this)` + `isDone()`（:96-106） | `fireAfterComplete`（:165-181）swap→逐个 catch Exception | **有**（对称缺口），但 `complete()` 中 `done=true` 先于 fireAfterComplete（:120-127）→ 迟到注册落到 isDone 抛错路径；丢弃窗口实际仅存在于 `completeExceptionally` 的锁内 done 设置与 swap 之间的纳秒级区间 | PartitionDispatchLoaderProvider:65,75、AsyncFetchPartitionDispatchLoaderProvider:84,103、ResourceRecordConsumerProvider:105、ResourceRecordLoaderProvider:167,189、JdbcBatchLoaderProvider:185、ExportDbTool:300、ImportDbTool:340、BuilderFactory:554——setup/executeAsync 相位（:65/:84/:189 为 task ctx setup 相位；:75/:103/:167 为 **per-chunk ctx 注册于 load 闭包**，chunk ctx 的完成在同一线程 → 注册先于触发，无并发窗口）；ExportDbTool:300/ImportDbTool:340 为任务提交前调用线程 |
| 4-12 | `onChunkBegin`/`onBeforeChunkEnd`/`onChunkEnd`/`onChunkTryBegin`/`onChunkTryEnd`/`onConsumeBegin`/`onConsumeEnd`/`onLoadBegin`/`onLoadEnd` | 9 个 `final CopyOnWriteArrayList`（BatchTaskContextImpl:71-81） | COW（无 swap） | `synchronized(this)` + `isDone()`（:405-493） | fire*（:496-607）无锁读 final 字段 + 快照遍历 | **无丢弃**：fire 后注册对后续 chunk/consume 事件生效（isDone=false 期间），仅错过注册瞬间正在进行的那一次 fire（COW 快照语义） | BuilderFactory:561-621（addTaskInitializer 相位）；fire 站点：BatchTask:310/314/316/326/339、BatchChunkProcessor:67-75、BatchProcessorConsumer:115-120、RetryConsumeHelper:46-58 |
| 13 | `onTaskEnd`（宣称语义） | `onTaskEnds`（BatchTaskContextImpl:70） | 私有字段，**无注册/触发点** | 无 | 无 | 死载体（C1-004 维持） | 无 |
| 14 | （对照）`Cancellable.appendOnCancel` | `callbacks`（Cancellable） | ArrayList **swap-on-canceled** | 无守卫 | cancel→tryRunCallbacks | **无**：注册时已取消则立即执行（:80-97）——守卫语义正确的同族范本 | ExportDbTool/ImportDbTool 事务与资源清理路径 |

**失败路径不对称（本轮新核实，归入 C1-007）**：任务失败路径走 `completeExceptionally`（BatchTask:211）→ **只 fire afterCompletes，beforeCompletes 永不触发**。确定性后果：ResourceRecordConsumerProvider:131/142 注册的 trailer 写出（`endWrite+flush`）与 ResourceRecordLoaderProvider:221-227 注册的 processingItems 非空检查，在失败任务上**确定性跳过**。后果校准：失败任务不写 trailer 语义上合理（输出即不完整），processingItems 检查跳过无恢复风险（completedIndex 未推进，恢复从头开始）——但该不对称未在任何接口 JavaDoc 声明，回调注册者无从知晓"注册的 beforeComplete 在失败路径必然不执行"。

**C1-003 家族结论**：swap-on-fire 载体共 3 个（onTaskBegins、beforeCompletes、afterCompletes），均存在"守卫查 isDone 而非已触发"的注册-触发脱节；仓内全部注册点的相位均在对应 fire 之前（本轮逐一核对 :1-3 列注册点），触发需下游动态注册（U1~U6 助手或 processor 内 `ctx.onXxx`）。第 1 轮 P2 判级维持，家族清单由 3 载体扩充为上表 14 行全量判定。

---

## 四、指令④ — 发布安全与可见性专项

1. **double-checked locking 残留**：全模块 grep `synchronized` 邻接 null 检查，唯一锁内惰性初始化为 `BatchTaskContextImpl.getCache()`（:104-113，synchronized **方法** + 局部变量快照，正确形态）；`ExportDbTool:122`/`ImportDbTool:127` 的 dialect 惰性初始化仅在 main 单线程路径。**无 broken DCL**。
2. **非 final 字段跨线程发布**：`BatchTaskContextImpl` 全部 setup 字段（taskName/taskId/taskKey/taskVersion/metrics/recoverMode/partitionRange/params/flowId/flowStepId/allowStartIfComplete/startLimit）写点在 `executeAsync` 调用线程、先于 `executor.execute`（BatchTask:226）与 fetch 线程提交（AsyncFetch:114+）→ executor HB 安全发布（第 1 轮结论复核维持）；运行期唯一 set 点为 `setCompletedIndex`（volatile）。本轮新增核实：`DaoBatchStateStore.saveTaskState` 从 chunk 线程读这些字段——读取发生在 chunk 线程自身启动之后，HB 链完整；`NopBatchTask` ORM 实体的跨线程字段写全部经 store 监视器（`saveTaskState` synchronized :234；`startExistingTask` 的写在 setup 相位调用线程）→ 序列化一致。
3. **`ExportDbTool`/`ImportDbTool` 共享 `IEvalScope scope`**：`XLang.newEvalScope()`（ExportDbTool:155）主线程构建，`beforeExport.invoke(scope)`（:157-159）主线程执行先于 `executor.submit`（:176）→ HB；各表任务经 `BatchTaskContextImpl(null, scope)` 取 child scope（ExecutionContextImpl:49-52），共享父 scope 任务期只读——隔离成立。
4. **`EtlState`**（core/utils）：`volatile long` + `AtomicLongFieldUpdater` addAndGet（:12-24）——正确组合；plain setter `setInsertCount/setUpdateCount` 全仓无运行期调用方（仅 bean 访问器，反序列化用），不构成 set/inc 交叠面。**安全**。
5. **`EtlTaskStateStore`**：`taskState` 引用非 volatile，写点仅构造器与 `reset()`；`reset()` 全仓无生产调用方（本轮再核实：grep 仅命中声明与测试），`isCompleted()` 无锁读（:39-41）的现实调用点 `ExportDbTool:140` 在任何表任务提交之前（主线程）→ 无跨线程对手方——第 1 轮"登记备查"维持。`EtlTaskState.tableStates` 为懒建 **HashMap**，但全部触达点（makeTableState/resetTableState/getTableStates→serialize）均在 `EtlTaskStateStore` 的 synchronized 方法内（外部类监视器）→ 监视器封闭成立（第 1 轮第 7 条排除结论复核维持）；`saveStateFile` 临时文件 + 原子 move，串行于同一监视器。
6. **`PartitionDispatchQueue`**：`finished`/`noMoreData` volatile（:64-65），其余共享字段 ReentrantLock 全包含——第 1 轮"守卫最完整"结论复核维持。**AsyncFetch 的 `AtomicReference<Exception> exception`**（AsyncFetch:89）经 CAS/读传递 fetch 线程失败——原子原语，可见性成立。

本节无新缺陷。

---

## 五、指令⑤ — 并发 chunk 下的 listener 触发重入

1. **COW 注册表 fire 期间并发注册的语义（逐一核实 9 个 fire* 实现，BatchTaskContextImpl:496-607）**：fire* 方法在 `synchronized(this)` **之外**读取 final COW 字段引用并以增强 for 遍历——迭代器是读取瞬间的数组快照。并发注册（持有 `this` 监视器，:405-492）与 fire 并行时：新 listener **不参与正在进行的那一次事件**（事件粒度错过），但注册本身不丢失、对后续同类事件生效。无丢更新、无阻塞、无死锁（fire 不持锁）。语义登记：`onChunkBegin` 等每次 chunk 都 fire → 错过一次后立即可见；`onConsumeBegin/End` 每 chunk 的 consume 阶段 fire → 同理。
2. **同一 listener 被同一事件并发触发多次**：并发 chunk 各自 fire 同一任务级 listener（不同 chunkContext 参数）→ 引擎侧不串行化（设计使然，单游标类 loader 才有 state 锁）；listener 自身线程安全为隐含契约。DSL 注册的 listener 经 `ctx.getEvalScope()`（BuilderFactory:562 等）收到 **chunk child scope** → xpl 侧变量写落在 chunk 本地 map，并发 chunk 的同名 listener 写互不踩踏——隔离成立。
3. **fire 期间回调内重注册**：回调内调 `ctx.onChunkBegin(...)` → COW add 成功（不影响当前迭代快照）→ 后续事件生效——无重入问题；回调内调 `ctx.onTaskBegin(...)`（swap-on-fire 载体）→ 落入 C1-003 丢弃变体（fireTaskBegin 锁外执行回调，此时 isDone=false、onTaskBegins 已 swap 为 null → new ArrayList → 永不触发）——登记为 C1-003 的重入子型，不另立案。
4. **fireTaskBegin 不可重复触发**：swap 后 onTaskBegins=null → 二次 fire 无操作；`ExecutionContextImpl.complete()` 的两段 fireBeforeComplete（BatchTask:192 与 complete() 内 :117）由 swap 保证只发一次——重复触发面排除。
5. **fire 回调异常语义**：仅 `fireChunkEnd`（:555-566）逐 callback catch Exception；其余 8 个 fire* 直接传播——第 1 轮 CL-005 维持（本轮补充：catch 的是 Exception 而非 Throwable，Error 仍会中断后续 listener——并入 CL-026 口径）。

本节无新立案缺陷；COW 快照语义与重入面核实为"已验证安全项"。

---

## 六、新发现条目

### [C1-006] SequentialBatchRequestGenerator 实例字段（subIndex/subContext）在 asyncProcessor 模式下被 nextRequest 与 onResponse 跨线程无同步驱动：outputVars 错位关联 + HashMap 局部 scope 数据竞争

- **文件**: `nop-batch/nop-batch-gen/src/main/java/io/nop/batch/gen/generator/SequentialBatchRequestGenerator.java:23-24,32-52,62-74`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/processor/BatchSequentialProcessor.java:30-52`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/BatchProcessorConsumer.java:53-107`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:398-403`
- **证据片段**:
  ```java
  // SequentialBatchRequestGenerator —— 实例字段，无任何同步
  private int subIndex;                 // :23
  private IBatchGenContext subContext;  // :24（child scope，局部 map 为 HashMap，EvalScopeImpl:97）

  public S nextRequest(IBatchChunkContext context) {          // chunk 循环线程调用
      subContext.getEvalScope().setLocalValue(..., context);  // :33 写 HashMap
      ...
      subIndex++;                                             // :43
      return (S) subCase.next(subContext, true);              // :49
  }
  public void onResponse(R response, IBatchChunkContext context) {  // 响应回调线程调用
      int lastIndex = subIndex - 1;                               // :64 读-推导
      BatchGenState subCase = subCases.get(lastIndex);            // :65
      subContext.getEvalScope().setLocalValue(...);               // :68 写同一 HashMap
  }

  // BatchSequentialProcessor —— 响应回调的交付时机取决于内层 processor
  S request = seq.nextRequest(context);                  // :35 循环立刻推进
  processor.process(request, response -> {
      seq.onResponse(response, context);                 // :42 异步 processor 时在回调线程执行
      consumer.accept(response);
  }, context);
  ```
- **严重程度**: P2
- **并发场景**: 生成器任务同时配置 `useBatchRequestGenerator="true"` 与 `asyncProcessor="true"`（batch 任务模型一等布尔属性，`_BatchTaskModel` xml attr；`BatchTaskBuilder:399` 将 processor 包进 `BatchSequentialProcessor`，`BatchProcessorConsumer` 进入 async 模式：outputs 切换为 ConcurrentLinkedQueue :106、注册 chunk latch :58，且 ：70 注释明文支持"processor 内部可能异步执行"）。async 模式下 `processor.process(request, responseCallback)` 在响应到达前返回，`BatchSequentialProcessor` 的 do-while **不等响应**继续下一轮 `nextRequest`——结构性跑赢，而非尾窗口。同步 processor 模式下 onResponse 与 nextRequest 同线程串行（subIndex-1 恒正确），无此问题——这同时修正了第 1 轮 C1-002 失效面⑥"锁外交叠时可能取错 case"的模糊表述：取错仅发生在 async 模式。
- **触发时序**: T1（chunk 循环线程）第 k 轮 `nextRequest`：`subIndex: n→n+1`，返回 request_n，提交异步处理 → **不等待**，立即第 k+1 轮 `nextRequest`：`subIndex: n+1→n+2`，并经 ：33 写 subContext 的 HashMap 局部 map → T2（异步 worker，processor 完成回调）处理完 request_n 后执行 `onResponse`：`lastIndex = subIndex-1 = n+1`（应为 n）→ `subCases.get(n+1)` 取到**下一个 case** 的 outputVars → 错误的输出变量模板被求值并写入任务 scope（:68-72），生成数据关联错位；同时 T1（:33）与 T2（:68/:72）并发写同一非线程安全 HashMap → 丢失更新乃至结构性破坏。多 chunk 并发时叠加 C1-002 的共享 subCases 竞态，错位面扩大。
- **现状**: 接口 `IBatchRequestGenerator` 的 Javadoc 未声明 nextRequest/onResponse 的线程归属契约；`SequentialBatchRequestGenerator` 假定"onResponse 严格紧跟对应 nextRequest"的单线程序，该假定仅在同步 processor 下成立；DSL 对 `useBatchRequestGenerator + asyncProcessor` 组合无校验。
- **风险**: 配置组合满足时，输出变量关联错位是**近确定性**的（循环必然跑赢异步响应），且伴随 HashMap 数据竞争；生成类任务（数据构造/压测数据生成）产出静默错误关联数据，无响亮失败。三项配置均非默认值，故 P2 而非 P1。
- **建议**: 方向一：`BatchTaskBuilder.buildTask` 检测 `useBatchRequestGenerator && asyncProcessor` 时 fail-fast（两者语义冲突：生成器依赖严格请求-响应序）；方向二：`BatchSequentialProcessor` 持有每 generator 的 pending 队列，response 回调携带序号回队，onResponse 按序消费（侵入较大）；方向三（最小改动）：在 `IBatchRequestGenerator` Javadoc 声明"nextRequest 与 onResponse 必须同线程串行"契约，并在 async 包装处抛不支持。推荐方向一。
- **失效面枚举（F3）**: `SequentialBatchRequestGenerator` 可变状态全量：① `subIndex` 写点 :43（nextRequest，chunk 线程）；② `subIndex` 读点 :35/:40/:64（:64 为跨线程读——缺陷现场）；③ `subContext` scope 写点 :33（chunk 线程）、:68/:72（回调线程——缺陷现场）；④ 共享 `subCases` 元素 genCount/subIndex（C1-002 已立案）；⑤ `IBatchRequestGenerator` 的唯一实现类即本类（grep 确认），无其他实现受同一契约缺口影响；⑥ `BatchProcessorConsumer` async 模式的 outputs CLQ 与 latch 原语自身安全（:58/:91/:106），不受影响。
- **信心水平**: 确定（结构确定：async 模式下循环必然先于响应推进；具体错位需 generator+sequential/weighted subCases+asyncProcessor 组合配置）
- **误报排除**: 非原语使用问题——是"实例字段跨线程无同步 + 接口无线程契约"的复合缺陷；不把"当前仓内无 generator+asyncProcessor 组合的示例文件"夸大为不可达（三个属性均为 DSL 公开面，`batch-gen-demo.task.xml` 已用 generator，组合仅一步之遥）；亦不并入 C1-002（共享游标竞态）——两者载体不同（per-generator 实例字段 vs 跨 generator 共享 BatchGenState）、修复点不同（builder 校验/接口契约 vs BatchGenState 加锁）。
- **复核状态**: 未复核
- **复核状态**：保留 P2；新发现；装配现实性三证据（batch.xdef:13/:15 无联动约束、BatchTaskBuilder:396-403 零校验、BatchProcessorConsumer:70 async 契约公开面）与跨线程机制（do-while 结构性跑赢、HashMap 局部 scope）全部独立亲证。

### [C1-007] ExecutionContextImpl 终态协议的两处缺口：complete() 两段窗口内 onBeforeComplete 注册被接受且永不触发；失败路径 beforeCompletes 确定性不执行且未声明

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/context/ExecutionContextImpl.java:108-139,142-163`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:189-213`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/ResourceRecordConsumerProvider.java:128-150`
- **证据片段**:
  ```java
  public void complete() {
      synchronized (this) { if (done) return; }        // :112-115 第一段：占位检查
      fireBeforeComplete();                            // :117 swap beforeCompletes（锁外）
      boolean win = false;
      synchronized (this) { if (!done) { done = true; win = true; } }   // :120-125 第二段：置 done
      if (win) fireAfterComplete(null);                // :126-127
  }
  public void completeExceptionally(Throwable exception) {
      synchronized (this) { if (done) return; this.error = exception; this.done = true; }  // :132-137
      fireAfterComplete(exception);                    // :138 —— beforeCompletes 永不 fire
  }
  // onBeforeComplete 注册守卫只查 isDone（:83-93）
  ```
- **严重程度**: P3
- **并发场景**: 其一，任务完成线程执行 `complete()` 的 :117（swap 已发生）到 :125（done 置位）之间，另一线程调用 `onBeforeComplete`：isDone()=false 通过守卫 → `beforeCompletes == null` → new ArrayList → add 成功 → 两个 fireBeforeComplete 均已过去 → 回调静默丢弃。其二（无需竞态，确定性）：任务失败路径 `onTaskComplete`（BatchTask:204-213）只调 `completeExceptionally` → 仅 afterCompletes 触发 → setup 期注册的 beforeCompletes（ResourceRecordConsumerProvider:131/142 的输出文件 trailer 写出+flush、ResourceRecordLoaderProvider:221-227 的 processingItems 非空检查）**必然不执行**——`IExecutionContext.onBeforeComplete` 的 Javadoc 未声明这一失败路径不对称。
- **触发时序**: 变体一：T1（最后完成的 chunk 线程）在 `complete():117` 执行 fireBeforeComplete（swap）→ T2（另一 chunk 线程的 processor 代码）此刻调用 `ctx.onBeforeComplete(cleanup)`：持锁查 isDone=false → 建新列表 add → T1 继续 ：120-125 置 done → :127 fireAfterComplete（不触 beforeCompletes）→ cleanup 永不执行且无任何反馈。窗口宽度为 fireBeforeComplete 执行期（含全部已注册回调的运行时间，可达毫秒级），比 C1-003 的"fire 后至 done"窗口更宽。变体二：任意任务抛非取消异常 → saveTaskState(err) → completeExceptionally → trailer 跳过（后果良性：失败输出本不应有 trailer；processingItems 检查跳过无恢复风险，因 completedIndex 未推进）。
- **现状**: 守卫与触发同 C1-003 家族（查 isDone 而非已触发），但本条独立价值在两处新事实：complete() 两段结构使丢弃窗口横跨全部已注册回调的执行时间；失败路径跳过 beforeCompletes 是确定性行为且无文档声明。
- **风险**: 变体一为理论面（需并发动态注册，仓内注册点相位全部在前，同 C1-003 校准）；变体二为确定性语义缺口，现实后果良性但依赖"失败输出无 trailer 无害"这一未成文假设——若未来注册的 beforeComplete 承担与成败无关的清理（如临时文件删除），失败路径将确定性泄漏。
- **建议**: `onBeforeComplete/onAfterComplete` 的 Javadoc 补充声明"失败路径（completeExceptionally）仅触发 afterComplete"；`ExecutionContextImpl` 增加 volatile `beforeFired` 标志，注册守卫在已 fire 时抛 IllegalStateException（与 isDone 路径同型响亮失败）——一并消除 C1-003 的窗口。nop-batch 侧无需独立改动。
- **失效面枚举（F3）**: 平台侧同型载体 3 个：① `ExecutionContextImpl.complete()` 两段窗口（本条主现场）；② `completeExceptionally` 跳过 beforeCompletes（本条第二现场）；③ `BatchTaskContextImpl.onTaskBegins`（C1-003 已立案，窗口同源）。排除：`Cancellable`（swap-on-canceled + 已取消立即执行，无此缺口）；chunk 级 beforeCompletes（chunk 完成与注册同线程，无并发窗口）；BatchTask 成功路径的两次 fireBeforeComplete（:192 与 complete() 内）由 swap 保证恰好一次，重复触发面排除。
- **信心水平**: 确定（两段窗口与失败路径跳过均为代码结构事实；变体一触发依赖并发动态注册）
- **误报排除**: 不报 complete() 两段结构本身（其占位-重检设计是对的，缺口在注册守卫语义）；不把"失败路径跳过 trailer"报为数据缺陷（语义合理）——立案点是**未声明的确定性不对称 + 理论注册丢弃**，按 P3 校准（低于 C1-003，因其现实触发面更窄且后果良性）。
- **复核状态**: 未复核
- **复核状态**：保留 P3；新发现；complete() 两段窗口（ExecutionContextImpl:109-128）、失败路径跳过（:131-139）与 trailer 注册点（ResourceRecordConsumerProvider:131/142）全部独立亲证，现实后果良性，P3 校准正确。

---

## 七、维度集外线索（不立案展开）

### [nop-batch-CL-026]（维度集外线索，不立案展开）
- **形态归属**: C3/C5（异步链路完成保证 / 异常类型不对称）
- **严重度候选**: P2/P3
- **一句话证据**: `BatchTask.executeChunkLoop` 的 lambda 仅 `catch (Exception e)`（BatchTask.java:243）——chunk 线程抛 Error/OOME 时 `future.completeExceptionally` 不执行 → `allOf` 永不完成 → `executeAsync` 返回的 future 永久挂起（调用方如 `task.execute()` 的 `FutureHelper.syncGet` 无限阻塞），且 primaryErrorRef 未收集、`context.cancel` fail-fast 不触发、chunkContext 不 complete（其 afterComplete 的 processingItems 标记一并丢失）；同型缺口在 `BatchTask.onTaskComplete`（:190-213 仅捕获 Exception）与 fire* 监听器逐个 catch Exception（BatchTaskContextImpl:559-563）。
- **复核状态**：确认，**与 nop-batch-C6-001 合并裁定**（同位点：BatchTask.java:243 = C6-round2 终态化阻断站点表 S1 首行，本轮独立亲证；后果面归 C6-001，P1，不另立）。

### [nop-batch-CL-027]（维度集外线索，不立案展开）
- **形态归属**: C9（吞吐上限声明核对）
- **严重度候选**: P3
- **一句话证据**: `SequentialBatchRequestGenerator` 即使按 C1-002 建议将游标推进锁到 BatchGenState 上，sequential 生成的 consume 阶段仍是全局单游标串行点（所有 chunk 线程在单一 case 序列上争锁推进），与第 1 轮 CL-006（6 个 per-setup LoaderState 锁串行化游标）同族——C9 评估 generator 类任务并发收益声明时需计入。
- **复核状态**：确认；并入 nop-batch-CL-006 口径（C9 / P3，generator 游标串行点为同族第 7 站点）。

---

## 八、第 1 轮发现的复核与修正汇总

| 第 1 轮条目 | 本轮复核结论 |
|---|---|
| C1-001（P2，TL 不恢复旧值） | **维持 P2，证据升级**：配对矩阵确认全仓仅 2 provide/2 remove 且均无恢复；平台 3 个同族载体均为 save/restore 范本（BatchTask 是唯一离群者）；嵌套执行被证实为 DSL 一等组合路径（batch.xlib Execute 默认同步 + batch-gen.xlib PostExtendsForTaskFlow 自动嵌入 task:Execute + demo task xml 现成形态）；U1~U6 助手无 null 守卫为确定性 NPE 失败模式 |
| C1-002（P2，BatchGenState 共享游标） | **维持 P2**；失效面⑥（onResponse 取错 case）从本条拆出并精确化——sync 模式同线程恒正确，仅 async 模式成立，升级为独立发现 C1-006 |
| C1-003（P2，swap-on-fire 丢弃） | **维持 P2**；家族清单扩充为 14 行全量判定表（含 per-chunk ctx 注册、Cancellable 正确范本对照）；新增 C1-007 记录 complete() 两段窗口的更宽变体 |
| C1-004（P3，onTaskEnds 死字段） | 维持，无新证据 |
| C1-005（P3，实例复用无守卫） | 维持；本轮未发现新的复用调用点 |
| CL-001~CL-006 | CL-006 与新 CL-027 合并口径（generator 游标串行点）；其余维持 |

## 九、已验证安全项汇总（本轮新增）

1. **chunk scope 隔离的构造器级证据**：`BatchChunkContextImpl` → `ExecutionContextImpl(parentScope)` → `parentScope.newChildScope(attributes CHM)`——VAR_BATCH_CHUNK_CTX 等运行期 chunk 变量落在 chunk 本地 map，并发 chunk 不互踩（本轮曾怀疑写共享 scope，逐行核实排除）。
2. **counter set/inc 无运行期交叠**：全部 `set*Count` 调用点经 grep 确认在 setup 相位（DaoBatchStateStore:170-179、EtlTaskStateStore:84-106）；运行期纯 addAndGet。
3. **`BatchTask.incCount` 两步计数撕裂**：跨计数器撕裂读存在但无不变式受害者，恢复语义由 completedIndex/history store 保证 at-least-once——边界内，不立案。
4. **DCL**：无残留；唯一锁内惰性初始化 getCache() 形态正确。
5. **EtlState/EtlTaskStateStore**：ALFU+volatile 正确；HashMap tableStates 监视器封闭；isCompleted()/reset() 无跨线程对手方（维持备查）。
6. **COW fire 重入**：快照语义、fire 不持锁、回调内重注册不丢失（swap-on-fire 载体除外）、重复 fire 由 swap 排除。
7. **发布安全**：setup 字段 executor HB 链完整；DaoBatchStateStore 实体监视器封闭；ExportDbTool 共享父 scope 提交前写完。
8. **Cancellable 取消**：CAS 单赢家纪律复核成立。

## 十、总计与终止信号判定

| 判级 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 1（新增） | C1-006（SequentialBatchRequestGenerator async 竞态） |
| P3 | 1（新增） | C1-007（complete() 两段窗口 + 失败路径 beforeCompletes 不对称） |
| 维度集外线索 | 2（新增） | CL-026（Error 致任务 future 永久挂起）、CL-027（generator 游标串行点） |

**终止信号判定**：本轮无新增 P0/P1（第 3 轮触发条件不满足）。深挖指令①~⑤ 指定的全部失效面已完成穷举并登记：①配对矩阵闭合（全仓 4 站点 + 平台 5 载体对照）；②batch-gen 876 行全读盘点闭合 + core chunk 隔离核实成立；③14 行注册站点守卫判定表闭合（仓内注册点相位逐一核对，无漏网点）；④发布面/DCL/EtlState 核实无新缺陷；⑤COW 重入语义核实无新缺陷。新增条目（1×P2、1×P3、2×CL）均为指令②③的直接产物且其家族成员已全部盘点，不存在已识别但未登记的残留失效面。**结论：C1 维度深挖在第 2 轮后终止**；C1-006 与 CL-026 建议移交修复规划（C1-006 优先，builder 期 fail-fast 成本最低），C1-007/CL-027 可随平台侧改进批量处理。
