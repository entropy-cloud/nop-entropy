# [nop-stream-orchestration-thin] C2/C4/C5/C7/C8/C9 抽查批次（第 1 轮初审，盲审）

- 审计单元：`nop-stream/nop-stream-flow`（60 files / 6,202 lines）+ `nop-stream/nop-stream-sql`（17 / 4,334）+ `nop-stream/nop-stream-fraud-example`（21 / 3,256）的 src/main/java（非 `_` 前缀生成文件）
- 审计类型：roadmap 1.3 裁定的抽查批次——定向核对，不要求全量盘点
- 盲审纪律：未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下任何既有文件；仅审计当前 live code（分支 concurrency-audit-remediation @ 8dcfa4cd0c）
- 依据：基线 `_tmp/cra-baseline-nop-stream-orchestration-thin.txt`（C2/C4/C5 用附录 C 基线节；C7/C8/C9 用 supplement 节命中计数）+ owner docs `docs-for-ai/03-modules/nop-stream-user-guide.md`、`docs-for-ai/03-modules/nop-stream-sql.md` + 审计人沿 sql 展开管线（`StreamSqlCompiler` 产物 xpl 体 → `SqlRowAggregateOps`/`SqlScalarXplPrinter` 发射的静态调用）与 flow 算子路径（`Xpl*Function` 族 → `XplFunctionSupport.newCallScope` → `EvalActionKeySelector`/`JoinSideTagFunction`/`SideOutputTapOperator`/`XplWatermarkStrategy`）圈定的每记录热路径站点

---

## 抽查范围与站点圈定声明

### C2 锁与同步原语（抽查）
- **已核对站点**（基线「C2: lock carriers」节 + 「C2: computeIfAbsent/compute」节全量命中）：
  - nop-stream-flow：lock carriers 节 = **0 命中**（全模块唯一并发原语是 `XplSourceFunction.java:45` 的 `volatile boolean running`，已读全文核对）；computeIfAbsent × 2 = `StreamModelDslBuilder.java:1268`（`consumerEdgesByTag`，validateDag 构建期局部 map）、`StreamModelDslBuilder.java:1543`（`snapshotsByTarget`，`materializeSideInputs` 构建期局部 map）——两处均已读上下文核实为 build 期局部变量、computeIfAbsent 体内仅 `new ArrayList<>()`/`new LinkedHashMap<>()` 轻分配，无重逻辑/无递归自引用/无 I/O。
  - nop-stream-sql：lock carriers 节 = **0 命中**；computeIfAbsent × 2 = `SqlRowAggregateOps.java:34`（`CACHE`）、`RecordColumnAccess.java:84`（`GETTER_CACHE`）——两处已读全文核对（见 C2 节分析）。
  - nop-stream-fraud-example：`DirectoryFileSourceFunction.java` synchronized × 4（219/225/252/281，`cursors` TreeMap 监视器锁）、`ReplayableCdcSourceFunction.java:214`（`synchronized stop()`）——四个文件全部读毕。补充 grep（`synchronized|volatile|Atomic|ConcurrentHashMap|ThreadLocal|new Thread|Executor|Timer`）确认 3 模块无其他并发原语站点。
- **圈定依据**：基线节命中站点全量核对 + 全模块并发原语补充 grep 兜底。
- **未盘点面声明**：锁序图仅覆盖上述抽查站点（flow/sql 无实例锁，无锁序边；fraud 两个 source function 各持单一锁、无嵌套取锁，锁序图为平凡图，见 C2 节产出）。平台设施（`EvalScopeImpl`、NopIoC BeanContainer）自身实现不审，仅按量化目的只读核对。

### C4 线程池与执行器治理（抽查）
- **已核对站点**（基线「C4: thread creation and pools」节全量命中）：flow = 0 命中；sql = 0 命中；fraud = 1 命中：`ReplayableCdcSourceFunction.java:127` `new Thread(..., "fraud-s1-replay-cdc")`——全文读毕（含 `ReplaySubscription` 生命周期、`runReplay` 退出路径）。
- **圈定依据**：基线节命中 + `ExecutorService|Executors.|ScheduledExecutor|commonPool|ForkJoin` 补充 grep（3 模块均 0 命中）。
- **未盘点面声明**：runtime 侧 TaskExecutor/checkpoint 线程池归 `nop-stream-core-runtime`/`nop-stream-state-connectors` 审计单元，不在本单元。

### C5 内存有界性与大数据量（抽查）
- **已核对站点**：基线「C5: unbounded candidates count」三模块均 = **0**、readAllFiles = 0。按抽查口径补验「高命中类 + 集合增长面」：`StreamSqlAggregation.java:615/634`（`seen` map，C8 supplement 命中带出）、`StreamSqlAggregation.DistinctCountAgg`（ LinkedHashSet 累积器）、`SqlRowAggregateOps.CACHE`、`RecordColumnAccess.GETTER_CACHE`、`DemoKeyedStateStore.java:100`（demo）、`UserTransactionHistory`（keyed 双标量）、`DirectoryFileSourceFunction.cursors`（per-file 路径键）、`ReplayableCdcSourceFunction.eventSpecs`（构造期 fixture）、`StreamModelDslBuilder` 构建期 map 群。
- **圈定依据**：基线节 + C7/C8 supplement 命中面回溯（`seen.put`/`values.put` 即集合增长点）。
- **未盘点面声明**：窗口 pane 状态、CEP SharedBuffer 等引擎级容器归 core/state 审计单元。

### C7 长时运行稳定性（抽查）
- **已核对站点**（基线「C7 supplement」节命中 + 沿路径扩查）：flow 命中 5 条均为 WI27 join timeout 的**构建期 fail-fast 校验注释/代码**（`StreamModelDslBuilder.java:1889-1906`、`AdvancedTransforms.java:838-840`）与 lint 台账注释——已核实为声明面校验而非运行期缺陷面；sql 节 = 0 命中；fraud 命中 = `UserTransactionHistory`、`UserHistoryEnricher`（全读）。扩查：`ReplayableCdcSourceFunction.runReplay` 失败路径、`XplSourceFunction` 取消契约、模块内全部 `Thread.sleep`/wait 点（`ReplayableCdcSourceFunction.java:175/179`、`DirectoryFileSourceFunction.java:125/212`——均有界）。
- **圈定依据**：基线节命中 + C7 要求的等待点/失败自愈路径定向扩查。
- **未盘点面声明**：fencing epoch、健康状态机、checkpoint 自愈语义归 runtime 审计单元。

### C8 背压、流控与过载行为（抽查）
- **已核对站点**（基线「C8 supplement」节命中 + 生产-消费边界走查）：flow 命中全部为构建期局部 `put`（builder map 群，逐条核对为 build 生命周期）；sql 命中 = `StreamSqlAggregation.java:615/634`（DISTINCT seen，已核对声明契约）；fraud 命中 = `DemoKeyedStateStore.java:100`（DEMO-ONLY 已声明）。边界走查：`ReplayEngine` 发射线 → `action.accept`（直推 collect，无中间队列）、`DirectoryFileSourceFunction` 单线程循环 → `ctx.collect`、边级流控四属性（WI14 typed 拒绝——owner doc 表 B 已声明）。
- **圈定依据**：基线节命中 + 源→汇边界逐条核对（本单元无自建队列/信号量/限流器）。
- **未盘点面声明**：`ResultPartition` 有界队列、`EdgeConfig.queueCapacity`、`QueueOverflowPolicy` 归 core/runtime/connector 审计单元。

### C9 并发高性能·热路径（抽查）
- **已核对站点**（基线「C9 supplement」命中计数 flow=16/sql=1/fraud=1；按「sql 展开管线 + flow 算子路径每记录站点」圈定）：
  - sql 求值管线（每记录）：`RecordColumnAccess.get`（含 `GETTER_CACHE` 路径）、`SqlRowAggregateOps.begin/merge/toRow`（连续聚合发射体）、`SqlRowCompositeFunction.add/getResult`、`StreamSqlAggregation` 各 Agg 累加器、`DistinctAggregateWrapper.add/dedupKey`、`SqlCastHelper.cast`、`StreamScalarFunctionResolver.call`（xpl 通道 UDF 静态发射）、`StreamSqlExprCompiler` 编译产物闭包（编译一次、每记录 eval——`compileFunction` 编译期绑定 `fn` 已核实）。
  - sql 展开管线（构建期，冷路径）：`StreamSqlCompiler`（全读，无静态可变态）、`SqlScalarXplPrinter`（每记录发射体文本生成器——发现 C9-003）。
  - flow 算子路径（每记录）：`XplMapFunction`/`XplFilterFunction`/`XplFlatMapFunction`/`XplReduceFunction`/`XplSinkFunction`/`XplSourceFunction.run`、`XplFunctionSupport.newCallScope`（静态 ROOT_SCOPE 共享——已核对 `EvalScopeImpl` 实现）、`EvalActionKeySelector.getKey`、`JoinSideTagFunction.map/evalKey`、`SideOutputTapOperator.processElement/matches`、`XplWatermarkStrategy.createTimestampAssigner` 闭包、`XplWatermarkGenerator.onEvent`。
  - fraud 每记录站点：`UserHistoryEnricher.processElement`、`TransactionWindowAggregate.Acc.add`、`AlertCountAggregate.Acc.add`（均为单线程任务内调用，无共享态）。
- **圈定依据**：owner doc「编译管线为五阶段……LOCAL 模式经 GraphExecutionPlan + TaskExecutor 直接执行」声明的每记录执行面 = 算子链函数；sql 连续聚合的 xpl 发射体（`SqlRowAggregateOps.begin/merge/toRow` 内嵌 spec JSON）即每记录路径。
- **未盘点面声明**：`IEvalFunction.call1` 内部的 XLang 解释/编译执行体、序列化器编解码性能归平台设施（nop-core/nop-xlang），不审。

---

## C2 锁与同步原语（抽查）

### 抽查产出：锁载体清单与锁序图（仅抽查站点）

| 站点 | 锁载体 | 保护对象 | 访问点 | 锁内操作 | 锁序边 |
|---|---|---|---|---|---|
| `DirectoryFileSourceFunction.cursors` | 实例监视器（`synchronized(cursors)`） | `TreeMap<String,Long>` 光标表 | 219/225（读/写）、252（快照拷贝）、281（clear+putAll） | map 操作 + `new TreeMap<>(cursors)` 拷贝；**无** I/O、无回调（`ctx.collect` 在锁外） | 无（唯一锁，无嵌套） |
| `ReplayableCdcSourceFunction$ReplayEngine.stop()` | 实例监视器（`synchronized` 方法） | 仅写 `volatile cancelled` | 214 | 单字段赋值 | 无 |
| `SqlRowAggregateOps.CACHE` | `ConcurrentHashMap`（无显式锁） | spec JSON → 复合聚合函数 | 34 `computeIfAbsent` | 体内纯 CPU（JSON 解析 + 组装），无 I/O、无递归查本 map | 无 |
| `RecordColumnAccess.GETTER_CACHE` | `ConcurrentHashMap`（无显式锁） | Class → getter 表 | 84 `computeIfAbsent` | 体内纯 CPU（`getDeclaredMethods` 反射扫描），不递归查本 map | 无 |
| `StreamModelDslBuilder:1268/1543` | 构建期局部 map + CHM 语义不必要 | 构建期局部集合 | build 期单线程 | `new ArrayList/LinkedHashMap` | 无（冷路径） |

锁序图：平凡图（全单元无任何「持锁 A 取锁 B」边），无环，无死锁面。DCL 面：`StreamScalarFunctionResolver.defaultResolver` 为 `volatile`（38-44 行），读写不构成 DCL 缺陷；`DirectoryFileSourceFunction.runEntered` 为 AtomicBoolean + `readObject` 重初始化（79-104 行），序列化往返后守卫完整。F5 对照：本单元 wait/await/poll(timeout) 站点 = 0（仅 4 处有界 `Thread.sleep`），无无超时等待点。

### 发现条目

### [nop-stream-orchestration-thin-C2-001] StreamScalarFunctionResolver 静态可变 default + 无同步 LinkedHashMap 的注册/分发竞态与运行期静默换绑
- **文件**：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamScalarFunctionResolver.java:31-51, 88-97`
- **证据片段**：
  ```java
  private static volatile StreamScalarFunctionResolver defaultResolver;
  ...
  private final Map<String, Function<Object[], Object>> functions = new LinkedHashMap<>();
  /** registers (or replaces) a scalar function under the given SQL call name. */
  public void register(String fnName, Function<Object[], Object> fn) {
      functions.put(fnName, fn);
  }
  ...
  public static Object call(String fnName, Object... args) {
      StreamScalarFunctionResolver r = defaultResolver;
      ...
      Function<Object[], Object> fn = r.functions.get(fnName);
  ```
- **严重程度**：P2
- **并发场景**：`call(...)` 是 xpl 通道 UDF 的**每记录**分发入口（`SqlScalarXplPrinter.java:190-191` 对 `<sql>`/`<sqlTransform>` 编译产物发射 `StreamScalarFunctionResolver.call('fnName', ...)`，运行期逐记录执行）；`register` 是公开的运行期注册口（javadoc 明言 "registers (or replaces)"）。读侧（任务线程，逐记录 `LinkedHashMap.get`）与写侧（`register`/`registerAsDefault`，任意线程）之间**无任何同步**。
- **触发时序**：T0 作业 A 运行中，其 SQL 表达式含 UDF，任务线程逐记录执行 `call("strLen", ...)` 读 `functions`；T1 运维/应用代码在不重启作业的情况下调用同一实例（或另一 provider 实例经 `registerAsDefault` 抢占 default 后）`register("strLen2", fn2)` 触发 `LinkedHashMap.put`——若该 put 恰逢 get 侧观测到 resize/树化中间态，get 可返回错误槽位或抛未定义异常（HashMap 并发读写无 happens-before 保证）；T2 若调用的是 `registerAsDefault()`，`defaultResolver` 被静默替换为另一实例，已编译作业的**后续每记录** UDF 分发立即改道新实例，新实例缺同名函数时逐记录抛 `IllegalStateException: unknown UDF function`（裸 RuntimeException 族，违反错误处理两档策略）。
- **现状**：`defaultResolver` 为 volatile（可见性 OK），但 `functions` 为裸 `LinkedHashMap`，`register`/`call` 间无锁、无 CHM、无快照发布。
- **风险**：运行期重注册场景下（a）并发读写 UB（低概率数据错乱/异常）；（b）default 换绑后已运行作业的 UDF 行为**无编译期感知地静默改道**——owner doc `nop-stream-sql.md` §3a 声明「编译期一次解析（非逐记录查找）」，xpl 通道的实际分发是逐记录查全局静态，与声明契约不符（`StreamSqlExprCompiler.compileFunction` 通道——`StreamSqlExprCompiler.java:239` 编译期捕获 `fn`——才符合该声明，两通道行为不一致）。
- **建议**：① `functions` 改 `ConcurrentHashMap`（读多写少，零成本消除 UB）；② `register`/`setDefault` 文档化「仅限作业启动前」并在作业运行期调用时打 WARN 或 fail-fast（可经运行期作业注册表感知）；③ 让 xpl 通道与 eval 通道对齐（发射体改为引用编译期捕获的函数，或至少 javadoc 修正 §3a 契约表述）。
- **失效面枚举（F3 同族 grep）**：同族兄弟逐一核对——`StreamAggregatorFunctionResolver.java`（全读：无任何静态/实例可变态，纯构建期解析，**clean**）；`SqlRowAggregateOps.CACHE`（CHM + 不可变 entry，**clean**）；`RecordColumnAccess.GETTER_CACHE`（CHM + 只读 Map 值，**clean**）；同模式先例 `WindowedStreamImpl.setDefaultFactory`（core 单元，不在本单元失效面内，登记为跨单元同族线索）。失效面 = `StreamScalarFunctionResolver` 单类（`getDefault`/`setDefault`/`registerAsDefault`/`call` 四个静态面）。
- **信心水平**：高（代码事实确定）；竞态触发依赖「运行期重注册」这一运维路径，生产常见形态（IoC 启动期注册一次）不触发。
- **误报排除**：非「无状态单例并发调用」（存在可变态）；非低频粗锁问题；`volatile` 只保证引用可见、不保护 `functions` 内部。fraud-example 校准不适用（本类为 sql 生产模块）。
- **复核状态**：已保留（P2——文件/行号/证据引文逐字核实：L31 volatile default、L46 裸 LinkedHashMap、L49-51 公开 register 写、L88-97 逐记录静态读，读写间零同步属实；sql.md L47「编译期一次解析（非逐记录查找）」声明偏差属实——eval 通道 compileFunction 编译期捕获 fn、xpl 通道逐记录查静态，两通道不一致确认。cross-ref：与 C1-001 为同一机制——C1 维度复核已保留 P1（其行号 L46/49-51/88-97 与本条一致），本条为 C2 锁与同步维度视角，P2 在该视角自洽；合并裁定阶段应归并为单一发现，最终判级随 C1-001 的 P1 归并。现状：新发现，未修复——WI30 closure audit（ai-dev/audits/nop-stream-sql-enhancement/wi30-closure-audit.md）仅登记实现/打印通道功能面 PASS，未登记并发缺陷；基线 b3a9c76bed 后无触及本单元的提交）

### [nop-stream-orchestration-thin-C2-002] sideInput 冻结快照 map 被直接用作每记录调用 scope 的可写变量存储——写路径穿透「只读快照」契约（UOE 崩溃或跨记录污染）
- **文件**：`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/functions/XplFunctionSupport.java:47-52`；`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:1540-1549`；`nop-kernel/nop-core/src/main/java/io/nop/core/lang/eval/EvalScopeImpl.java:96-107`（量化核对用）
- **证据片段**：
  ```java
  // XplFunctionSupport
  public static IEvalScope newCallScope(java.util.Map<String, Object> bindings) {
      if (bindings == null || bindings.isEmpty()) {
          return ROOT_SCOPE.newChildScope();
      }
      return ROOT_SCOPE.newChildScope(bindings);
  }
  // EvalScopeImpl（平台侧，量化核对）
  public IEvalScope newChildScope(Map<String, Object> childVars) {
      EvalScopeImpl scope = new EvalScopeImpl(this, childVars, true);
      return scope;
  }
  // 构造器：this.variables = variables == null ? new HashMap<>() : variables;  ← 直接引用，无拷贝
  // StreamModelDslBuilder.materializeSideInputs 末尾
  sideInputScopes.put(entry.getKey(),
          Collections.singletonMap("sideInputs", entry.getValue()));
  ```
- **严重程度**：P2
- **并发场景**：WI11 声明 sideInput 时，`XplMapFunction/XplFilterFunction/XplFlatMapFunction` 持有的 `bindings`（`Collections.singletonMap("sideInputs", snapshotsUnion)`，外层不可变、**内层 snapshot map 为 Supplier 原样返回的可变 map**，未做 `unmodifiableMap` 防御包装）经 `newCallScope(bindings)` 成为**每记录**子 scope 的 `variables` 字段本体。XLang 求值期对 scope 的任何 `setLocalValue` 写（编译期 slot 分配未覆盖的动态赋值/未声明变量赋值回退路径，`AssignIdentifier.assign` 的 `slotIndex < 0` 分支）落在该共享 map 上。
- **触发时序**：形态 A（fail-fast 崩溃）：xpl body 执行未声明变量赋值（如 `total = total + event.amount` 无 `let` 声明）→ `rt.setLocalValue` → `singletonMap.put` → `UnsupportedOperationException`，逐记录抛出、作业失败，错误面指向 XLang 内部而非声明面（诊断成本高）。形态 B（静默污染）：body 经 `sideInputs['dict']` 取得**内层可变 snapshot map** 并写入（如做本地缓存/去重）→ 同一 subtask 内该 map 跨记录、跨同拷贝算子共享，「构建期冻结只读快照」契约（owner doc WI11 节 + builder javadoc "frozen read-only snapshot"）被穿透，记录 N 的写入在记录 N+1 可见；若运行时共享 UDF 实例跨 subtask（`DirectoryFileSourceFunction` javadoc 声明的 runtime 共享形态），则升级为跨线程对同一 `LinkedHashMap` 的并发读写 UB。
- **现状**：机制层（bindings map 直通 scope variables）确定；触发依赖 body 写入风格——已核实编译期 `let` 走 `EvalFrame` slot（`AssignIdentifier.java:48-53`），不落 scope map，故常见声明式 body 不触发；未声明赋值与内层 map 直改是可达回退面。
- **风险**：契约违反（声明冻结、实现未冻结）+ 两种具体失败形态；多 subtask 共享实例形态下为数据竞争。
- **建议**：① `materializeSideInputs` 对 snapshot 做 `Collections.unmodifiableMap` 深度防御包装（成本仅构建期一次）；② `newCallScope(bindings)` 将 bindings **拷贝**进子 scope（或 EvalScope 支持只读 bindings 层 + 可写 overlay 层）；③ 对 `singletonMap` 直通路径至少加 javadoc 契约声明「body 不得写 scope 变量」。
- **失效面枚举（F3 同族 grep）**：`newCallScope(bindings)` 非空 bindings 调用点全量 = `XplMapFunction.java:48`、`XplFilterFunction.java:48`、`XplFlatMapFunction.java:51`（三处同缺陷面，均为 WI11 sideInput 目标算子）；`bindings == null` 路径（`XplReduceFunction`/`XplSinkFunction`/`XplSourceFunction`/`EvalActionKeySelector`/`JoinSideTagFunction`/`SideOutputTapOperator`/watermark 两体）每记录 variables 为**新建 HashMap**，写入仅跨调用丢失、不跨记录污染（**无此缺陷**，但有 C9-001 的分配面）。失效面 = WI11 sideInput 注入的全部 xpl-fn 形态目标（map/filter/flatMap）。
- **信心水平**：机制链高（构造器引用语义 + 写回退路径均有代码锚点）；实际生产触发率低（依赖 body 风格），故 P2 而非 P1。
- **误报排除**：非锁/CHM 原语自身问题；`ROOT_SCOPE` 静态共享本身**安全**（已核对 `EvalScopeImpl.newChildScope` 不写父 scope，父子仅引用关系）——不将该面误报为缺陷；跨 subtask 深拷贝形态（`StreamRecordEvaluator` javadoc 声明的 chain 逐 subtask 序列化拷贝）下单线程，不构成即时竞态，缺陷主体是契约穿透 + 有条件竞态。
- **复核状态**：已保留（P2——机制链逐锚点核实：XplFunctionSupport L47-52 bindings 直通 newChildScope、EvalScopeImpl L39 构造器直接引用无拷贝、L103-107 newChildScope(childVars)、L205-218 setLocalValue→variables.put 直写、AssignIdentifier L58-69 slotIndex<0 回退 rt.setLocalValue，全部属实；materializeSideInputs 快照未经 unmodifiableMap 包装（实际 put 在 L1547-1548，处于引用范围 1540-1549 内）属实；user-guide L175/L311「冻结只读快照」声明零强制属实。cross-ref：与 C1-004 为同一机制——C1 维度复核已保留 P2，判级自洽，合并裁定阶段归并。现状：新发现，未修复——无前置历史登记、基线 b3a9c76bed 后无触及本单元的修复提交）

---

## C4 线程池（抽查）

### 抽查产出：线程创建点清单（仅抽查站点）

| 站点 | 形态 | 命名 | 守护 | 队列/拒绝 | 退出路径 | 生命周期对称性 |
|---|---|---|---|---|---|---|
| `ReplayableCdcSourceFunction.java:127` | 裸 `new Thread`（非池——单发有界任务，池化无收益） | `fraud-s1-replay-cdc` ✓ | `setDaemon(true)` ✓ | 无队列（直推 `action.accept`，背压由下游 collect 阻塞承担） | `runReplay` 有界循环结束/`cancelled` 短退出/finally 自取消，线程必终 | subscribe 一次→一线程，随任务生命周期终止；无池泄漏面 |
| flow / sql | 0 命中（grep `new Thread|ExecutorService|Executors.|ScheduledExecutor|commonPool|ForkJoin` 全空） | — | — | — | — | — |

定时任务面：三模块 `scheduled` 节均 0 命中，无 `scheduleAtFixedRate`（F9 停调度不可见面不存在）。本单元无共享池、无 caller-runs、无池内提交同池任务形态。

### 本轮抽查无发现（C4）

唯一线程创建站点 `ReplayableCdcSourceFunction` 经逐项核对：核心参数不适用（单发线程）；无队列故无拒绝策略面；命名/守护位齐全；`emitDelayMs` 切片 sleep 保证 cancel 响应有界（线程最长阻塞一个 `emitDelayMs`/`finishLingerMs` 后检查 `cancelled` 退出）；线程不随 bean 泄漏（runReplay 必达 finally 自取消并自然退出）。其异常路径的吞没问题不是线程治理缺陷，归 C7-001（F9）。已核对站点清单见节首。

---

## C5 内存有界性（抽查）

### 抽查产出：无界风险清单（仅抽查站点）

| # | 增长容器 | 上界由谁保证 | 判定 |
|---|---|---|---|
| 1 | `StreamSqlAggregation$DistinctAggregateWrapper.seen`（`StreamSqlAggregation.java:601-617,627-639`） | **owner doc 明文声明**（nop-stream-sql.md §2「精确去重内存代价 = 每组去重值数量——窗口聚合随窗清除，持续聚合随作业生命周期」）+ 类 javadoc 同文（「declared cost, no approximation」） | 已声明边界——持续聚合路径随作业生命周期线性增长是有意语义；非缺陷，登记为容量规划输入 |
| 2 | `DistinctCountAgg` 累积器 `LinkedHashSet`（433-470） | 窗口作用域，随窗清除（javadoc + WI0(d) 双声明） | 有界（窗内去重值基数） |
| 3 | `SqlRowAggregateOps.CACHE`（27-38） | 键 = 编译产物内嵌 spec JSON 常量，基数 = 编译管线数（javadoc 自声明）；值不可变 | 有界 |
| 4 | `RecordColumnAccess.GETTER_CACHE`（36-37, 82-111） | 键 = 已加载 Class，基数 ≤ classloader 空间 | 有界 |
| 5 | `DemoKeyedStateStore`（90-141） | 类 javadoc 顶部 **DEMO-ONLY** 声明 + 「not a template」 | 已声明边界 |
| 6 | `UserTransactionHistory`（per-user count/total 双标量） | 基数 = user 基数（键控特征固有）；平台 `StateTtlConfig` 可配 TTL，demo 未配 | 特征固有，登记于 C7 单调增长源清单 |
| 7 | `DirectoryFileSourceFunction.cursors` | 键 = 目录内文件路径数 | 有界 |
| 8 | `ReplayableCdcSourceFunction.eventSpecs` | 构造期 fixture 入参 | 有界 |
| 9 | `StreamModelDslBuilder` 全部 map 群（C8 supplement 21 条命中逐一核对） | builder 实例生命周期 = 一次 build | 有界 |

无界 API 使用点（全量载入/全量文件读入/无 prefetch 上限消费）：`DirectoryFileSourceFunction.emitRemaining` 为 8KB 定长 buffer 逐块读（157 行），非全量载入 ✓。物化 vs 流式：sql 展开管线产物为模型 XML 文本（构建期一次性，非运行期）✓。

### 本轮抽查无发现（C5）

基线 C5 计数为 0 且补验的 9 个增长面均有明确上界或属 owner doc 明文声明的边界（条目 1/5）。条目 1 同时登记为 C7 单调增长源，供容量规划引用；不构成缺陷立案（纪律第 5 条：明文声明且带语义边界的成本不是缺陷）。

---

## C7 长时运行稳定性（抽查）

### 抽查产出

**单调增长源清单**（仅抽查站点）：① `DistinctAggregateWrapper.seen`——持续聚合随作业生命周期（已声明，见 C5 条目 1）；② `UserTransactionHistory` 双标量——随 user 基数与运行时长（特征固有；demo 未配 TTL，真实部署应配 `StateTtlConfig`）；③ 其余容器均随窗/随 build/随文件数有界。日志放大面：本单元逐记录日志 = 0（唯一 `LOG.error` 在失败路径）。

**挂死点清单**（仅抽查站点）：① `XplSourceFunction.run`——执行任意 xpl body，若 body 既不轮询 `ctx.isCancelled()` 也不调用 `collect` 则任务线程永久阻塞；owner doc「内联 xpl source 的取消模式」已明文声明该契约（「不调用 collect 的循环体必须依赖轮询退出」），wrapper 自身 `running` 标志明确声明不可达 body（javadoc 34-37 行）——**已声明边界**，非缺陷；挂死可见性归引擎健康检测面（runtime 单元）。② 4 处 `Thread.sleep` 均有界（emitDelayMs/finishLingerMs/lineDelayMs 构造参数）。③ 无 `wait/await/park/lockInterruptibly` 站点。

重试风暴面：本单元无重试逻辑（0 处 retry/backoff）。时钟回拨敏感逻辑：本单元时间均取自事件时间戳/`System.currentTimeMillis()` 单调场景（`FraudDetectionDemo` 基准时间），无回拨敏感判定。epoch/generation：不在本单元（runtime 单元）。

### 发现条目

### [nop-stream-orchestration-thin-C7-001] ReplayEngine 异常被吞没并转为自身 cancel——「回放完成」与「回放失败」不可区分，作业以正常终结掩盖半程数据（F9/F2）
- **文件**：`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java:165-190`
- **证据片段**：
  ```java
  private void runReplay(Consumer<ChangeEvent> action) {
      try {
          long resumeIndex = readReplayIndex();
          for (int i = (int) resumeIndex; i < events.size(); i++) {
              ...
              action.accept(events.get(i));
              writeReplayIndex(i + 1L);
              ...
          }
          ...
      } catch (Exception e) {
          LOG.error("CDC replay engine failed; cancelling source so the job fails loudly", e);
      } finally {
          // Bounded source completion: the production run() loop only exits on
          // cancel/drain, so a completed replay must cancel its own source.
          ReplayableCdcSourceFunction.this.cancel();
      }
  }
  ```
- **严重程度**：P2
- **并发场景**：发射线程（`fraud-s1-replay-cdc`）与消费侧任务线程跨线程协作：发射线程内 `action.accept` / `writeReplayIndex` 抛出的任何异常走 catch 分支仅记日志，随后 finally 与**正常完成走同一条** `cancel()` 路径。`cancelled` 标志无「失败」语义位，外部（引擎/下游）无从区分有界回放自然完成与中途失败。
- **触发时序**：T0 回放至第 k 个事件，`action.accept` 抛异常（下游 collect 协作中止以外的失败，如 offsetStore 写入失败 `writeReplayIndex` → `Long.parseLong`/`set` 异常）；T1 catch 仅 `LOG.error`；T2 finally `cancel()`；T3 生产 run() 循环观察到 cancel 优雅退出，作业**以正常终结收场**，下游 2PC sink 正常提交已收到的 k 条——剩余 `events.size()-k` 条 fixture 事件**静默丢失**，作业状态/退出码与成功运行不可区分（日志一行除外）。
- **现状**：注释自称 "fails loudly"，但实际唯一响铃是日志行；`cancel()` 语义被复用为「完成」与「失败」双义。
- **风险**：F9 静默吞异常 + F2 半个成功（部分数据 + 成功状态）；依赖该 demo 做回归基线的场景测试会把截断输出误判为通过。
- **建议**：① `ReplayEngine` 增加 `volatile Exception failure` 失败位，在 cancel 前记录；② 有界完成与失败取消分流——失败路径向上游任务线程传递异常（如经 `SourceContext`/任务失败钩子），或至少让 `ReplayableCdcSourceFunction` 覆写 run/ close 依据 failure 位抛出；③ fraud-example 校准下最低成本修复：`finally` 前区分失败分支注释与测试断言（现有测试若依赖 cancel 完成语义需同步钉住失败注入用例）。
- **失效面枚举（F3 同族 grep）**：同族对照——`DirectoryFileSourceFunction` 的失败面**不吞**：`discoverFiles` 目录缺失直接抛 `IllegalStateException`（138 行）、`emitRemaining` 的 IO 异常直接从 `run` 传播给引擎失败路径（**clean**，反证本条非模块统一样式而是 ReplayEngine 局部缺陷）；`XplSourceFunction` 异常直接传播（javadoc「Exceptions thrown by the xpl body propagate to the caller unchanged (no silent swallow)」，`XplFunctionSupport.java` 头注，**clean**）。失效面 = `ReplayableCdcSourceFunction$ReplayEngine.runReplay` 单点。
- **信心水平**：高（代码路径确定）；「作业以正常终结收场」的最后一步取决于引擎对 source cancel 的处置（runtime 单元），本单元内事实为「异常不向上传播 + 完成失败同路径」。
- **误报排除**：fraud-example 演示校准不豁免本条——它违反的是 owner doc 已声明契约（用户指南「内联 xpl source 的取消模式」节声明取消为协作语义而非失败掩盖通道；且类 javadoc 自称 "fails loudly" 与实现不符）；非测试专用代码（本类是 S1 场景的 source bean）。
- **复核状态**：已保留（P2——ReplayableCdcSourceFunction L165-190 逐字核实：catch(Exception) 仅 LOG（L184）、finally 与正常完成同走 cancel()（L188）、cancelled 单标志无失败位（L116）、getCancelReason 失败后仍返回 CANCEL_REASON_STOP（L142-144）均属实；剩余触发面（readReplayIndex Long.parseLong / writeReplayIndex offset store 故障）真实存在。cross-ref：与 C3-001 为同一代码点——C3 维度复核已将 C3-001 由 P1 降级 P2（父类订阅 lambda pendingError 通道覆盖 collect 失败主触发）；本条立案即为 P2 且触发时序已自限于「协作中止以外的失败」，与 C3 复核后判级同向自洽，维持 P2，合并裁定阶段归并（同代码点应并案处置）。现状：新发现，未修复——WI 1.2 三次将本类登记为「范围外，登记供该模块审计引用」（state-connectors C4-thread-pools.md L211/251/337），无同发现先例；基线后无触及本单元的提交）

---

## C8 背压（抽查）

### 抽查产出：生产-消费边界清单（仅抽查站点）

| 边界 | 生产者 | 消费者 | 中间队列 | 满后行为 |
|---|---|---|---|---|
| `ReplayableCdcSourceFunction` 发射线程 → 引擎 | `fraud-s1-replay-cdc` 线程（`action.accept(events.get(i))` 直推） | 生产 `DebeziumCdcSourceFunction` run 循环 → `SourceContext.collect` | **无**（同步直推） | 下游背压天然阻塞发射线程（collect 阻塞即节流）——与声明的协作取消/背压语义一致 ✓ |
| `DirectoryFileSourceFunction` → 引擎 | 单任务线程逐行 `ctx.collect`（207-216） | 引擎 sink 链 | 无 | 同上，阻塞即背压 ✓ |
| xpl inline source body → 引擎 | 用户 body `ctx.collect` | 同上 | 无 | owner doc 声明的取消/背压协作契约 ✓ |
| 边级流控（声明面） | — | — | — | `flowControlPolicy/queueCapacity/receiveWindow/packetSize` 四属性构建期 typed 拒绝（WI14 裁定，owner doc 表 B）——**已声明边界**，非「该用未用」：引擎级 `EdgeConfig.queueCapacity→ResultPartition` 背压归 runtime 单元 |
| DISTINCT 去重 set/map（`StreamSqlAggregation`） | 记录流入 | 窗口清除/作业生命周期 | 内存 map | 见 C5 条目 1（已声明成本） |

信号量/限流面：本单元 0 站点（平台 `ISemaphore`/`IRateLimiter` 在本单元无该用未用——源限速声明面 `SourceRateLimiter` 归 core/runtime）。批处理边界：无批处理路径。过载可观测：无积压深度/拒绝计数指标站点（引擎面，runtime 单元）。过载拒绝路径的 F2（半个成功）：本单元无拒绝路径；唯一半个成功面是 C7-001（失败伪装完成），已立案互参。

### 本轮抽查无发现（C8）

五条边界逐条核对均为「无中间队列的同步直推 + 下游阻塞背压」，与 owner doc 声明的连续流模型一致；无 QueueOverflowPolicy 该用未用面（本单元无任何队列）；无许可泄漏面（无信号量）；无批上限绕过面（无批）。已核对站点清单见节首。

---

## C9 并发高性能·热路径（抽查）

### 抽查产出：热路径清单（仅抽查站点，均为每记录执行）

| # | 站点 | 每记录动作 | 分配/锁 | 判定 |
|---|---|---|---|---|
| 1 | `XplMapFunction.map` / `XplFilterFunction.filter` / `XplFlatMapFunction.flatMap` / `XplReduceFunction.reduce` / `XplSinkFunction.consume` / `XplSourceFunction.run` | `newCallScope(...)` 1 次 + body 求值 | 1 `EvalScopeImpl` + 1 `HashMap`（bindings 非空时复用共享 map，见 C2-002） | 设计固有（XLang scope 隔离契约），登记基准缺口 |
| 2 | `EvalActionKeySelector.getKey`（40-42）/ `JoinSideTagFunction.evalKey`（53-60）/ `SideOutputTapOperator.matches`（74-77） | **双跳** scope 链：`newCallScope().newChildScope(singletonMap)` | 2 `EvalScopeImpl` + 1 `HashMap` + 1 `singletonMap` / 每记录（JoinSideTagFunction 每 key 表达式一套） | **C9-001（P3）** |
| 3 | `XplWatermarkStrategy.createTimestampAssigner` 闭包（1258-1268）/ `XplWatermarkGenerator.onEvent`（1308-1316） | 1 次 `newCallScope()` + body | 1+1 | 同 #1 |
| 4 | `RecordColumnAccess.get`（Map 记录） | `containsKey` + `get` 两次哈希探测 | 0 | justified（null 与缺失需区分以 fail-fast）✓；（bean 记录）`Method.invoke` 反射每列每记录——设计固有，基准缺口 |
| 5 | `SqlRowAggregateOps.begin`（43-45） | `function(specJson)` **调用两次** | 2 次 CHM 查找（hashCode 已缓存，无重哈希） | **C9-002（P3）** |
| 6 | `DistinctAggregateWrapper.add/dedupKey`（594-618） | `String.valueOf(v)` + `containsKey`+`put` | 1 String 装箱/记录 | DISTINCT 精确去重声明成本 ✓ |
| 7 | `StreamScalarFunctionResolver.call`（88-97，xpl 通道 UDF） | volatile 读 + `LinkedHashMap.get` + varargs `Object[]` | 1 数组/调用 | 功能面归 C2-001；性能面可忽略 |
| 8 | `SqlScalarXplPrinter` 发射的 xpl 体（WHERE/BETWEEN/CASE/HAVING/projection） | 每记录解释执行 | 子表达式**重复求值** | **C9-003（P3）** |
| 9 | `SqlCastHelper.cast`（每记录 CAST） | switch + ConvertHelper | 0 结构性分配 ✓ | clean |
| 10 | fraud `UserHistoryEnricher.processElement` / 两个 `Acc.add` | keyed state 读写/BigDecimal 累加 | 每记录 1 个 `UserTransactionHistory` 新实例（61-62 行——轻对象，字段仅两个 state 句柄获取） | fraud 校准：登记为优化候选，不立案 |

锁争用面：热路径全局锁/单例 synchronized = 0（flow/sql 无实例锁；`GETTER_CACHE`/`CACHE` 为无锁 CHM 读）✓。串行化瓶颈面：本单元算子均为单输入串行函数，无线程间并行逻辑被单线程点串行化（并行度由引擎分区承担）✓。

### 发现条目

### [nop-stream-orchestration-thin-C9-001] keyBy/join/tap 每记录双跳 scope 链——中间空 scope 纯属多余分配
- **文件**：`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/EvalActionKeySelector.java:40-42`；`.../builder/functions/JoinSideTagFunction.java:53-60`；`.../builder/SideOutputTapOperator.java:74-77`
- **证据片段**：
  ```java
  // EvalActionKeySelector.getKey —— 每记录执行
  return (K) keyExpr.invoke(XplFunctionSupport.newCallScope()
          .newChildScope(java.util.Collections.singletonMap("event", value)));
  // JoinSideTagFunction.evalKey —— 每记录每 key 表达式执行
  composite.add(expr.invoke(XplFunctionSupport.newCallScope()
          .newChildScope(Collections.singletonMap("event", event))));
  ```
- **严重程度**：P3
- **并发场景**：非竞态，热路径分配放大。`newCallScope()` 产出 child1（1 `EvalScopeImpl` + 1 空 `HashMap`），随后 `newChildScope(singletonMap)` 产出 child2（1 `EvalScopeImpl`，variables = singletonMap）。child1 全程空载，仅作为父链一跳存在；语义上等价于 `XplFunctionSupport.newCallScope(singletonMap)` 单跳。
- **量化口径**：频率 = 每记录（keyBy 每条、join 每侧每条、tap 每条且仅条件判定时）。单次代价链 = 2 对象分配 + 1 HashMap 桶数组懒初始化 + 1 singletonMap ≈ 数十纳秒 + 年轻代 GC 压力；多 key join（K 个 key 表达式）放大 K 倍（2K scope + K singletonMap + 1 composite list）。对比最优形态（1 scope + 0 HashMap）约 2× 分配量。
- **现状**：三处站点相同模式；`XplMap/Filter/FlatMap/Sink/Reduce/SourceFunction` 及 watermark 两体已是单跳正确形态。
- **风险**：高吞吐（百万记录/秒级）下 GC 分配速率可量化上升；无正确性影响。
- **建议**：三处改为 `XplFunctionSupport.newCallScope(Collections.singletonMap("event", value))`（一次分配）；或 `XplFunctionSupport` 增加 `newEventScope(Object event)` 专用工厂。同步补 JMH 基准锚点。
- **失效面枚举（F3 同族 grep）**：全单元 `newCallScope` 16 处调用逐一核对——双跳形态仅上述 3 处（4 个调用点，含 evalKey 两分支）；其余 12 处为单跳（clean）。
- **信心水平**：高（代码事实）；性能影响量级为中低（XLang 求值本身的开销主导，scope 分配占比未基准化）。
- **误报排除**：不作为缺陷只作优化候选（P3）；平台 scope 实现自身不审。
- **复核状态**：已保留（P3——三处站点双跳形态逐字核实（EvalActionKeySelector L40-41、JoinSideTagFunction L53-54/58-59、SideOutputTapOperator L76-77）；分配量对比成立：双跳 = 2 EvalScopeImpl + 1 空 HashMap 对象 + 1 singletonMap = 4 次分配，最优单跳 = 1 EvalScopeImpl + 1 singletonMap = 2 次，2× 关系准确。量化口径一处表述含糊（不影响裁定）：「1 HashMap 桶数组懒初始化」实为 `new HashMap<>()` 对象分配（桶数组因空 map 从未写入而不分配），分配量结论不变。现状：新发现，未修复）

### [nop-stream-orchestration-thin-C9-002] 连续聚合 map 体每记录重复查找复合函数缓存
- **文件**：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/SqlRowAggregateOps.java:43-45`
- **证据片段**：
  ```java
  /** map body: one raw record → one accumulator row (create + first add). */
  public static Object begin(String specJson, Object record) {
      return function(specJson).add(record, function(specJson).createAccumulator());
  }
  ```
- **严重程度**：P3
- **并发场景**：非竞态。`function(specJson)`（CHM 无锁读）在同一表达式中调用两次；specJson 为发射体内嵌常量字符串，`String.hashCode` 已缓存，第二次为纯重复查找。
- **量化口径**：频率 = 连续聚合（无窗口 GROUP BY）管线每输入记录 1 次（`StreamSqlCompiler.compileContinuousAggregate:651-657` 发射体 `SqlRowAggregateOps.begin("<spec>", event)`）。单次代价 = 1 次多余 `ConcurrentHashMap.get`（O(1)，spec JSON 较长时 equals 比较仍 O(len)——CHM 命中需 equals 校验键，长 spec（多聚合键 JSON 数百字节）下每次重复查找多付一次全串 equals）。
- **现状**：`merge`/`toRow` 均单次查找，仅 `begin` 双查。
- **风险**：可量化微浪费；无正确性影响。
- **建议**：局部变量提取 `var fn = function(specJson); return fn.add(record, fn.createAccumulator());`。
- **失效面枚举（F3 同族 grep）**：`function(specJson)` 调用点 5 处——`begin`×2（缺陷面）、`merge`×1、`toRow`×1（clean）。
- **信心水平**：高；影响微小（记录在案防扩散）。
- **误报排除**：非锁争用；量级过低不构成 P2。
- **复核状态**：已保留（P3——SqlRowAggregateOps L43-45 begin 同表达式两次 `function(specJson)` 逐字属实；merge L51-53 / toRow L59-61 单次属实；频率口径（连续聚合管线每记录 1 次）与 function() 的 CHM computeIfAbsent 读（L33-38）相符；spec JSON 常量 hashCode 已缓存、长 spec equals O(len) 的代价链成立。现状：新发现，未修复）

### [nop-stream-orchestration-thin-C9-003] xpl 发射通道对同一子表达式生成重复求值文本——BETWEEN test ×2、简单 CASE test ×分支数，与 eval 通道（单次求值）行为/成本双分叉
- **文件**：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/compile/SqlScalarXplPrinter.java:155-157, 216-241, 291-305`
- **证据片段**：
  ```java
  case SqlBetweenExpr:
      printBetween((SqlBetweenExpr) expr, sb);   // 内部：
  private void printBetween(SqlBetweenExpr expr, SqlOperator..., StringBuilder sb) {
      printBinaryOp(expr.getTest(), SqlOperator.GE, expr.getBegin(), ge);  // test 第 1 次入文本
      printBinaryOp(expr.getTest(), SqlOperator.LE, expr.getEnd(), le);    // test 第 2 次入文本
  ...
  // 简单 CASE：testText 嵌入每个 when 分支条件
  cond = "(((" + testText + ") == null || (" + whenText + ") == null) ? false : ...";
  body = "((" + cond + ") ? (" + thenText + ") : (" + body + "))";
  ```
- **严重程度**：P3
- **并发场景**：非竞态，生成代码的每记录求值放大。`print` 是构建期文本生成器，产物 xpl 体运行期逐记录解释执行：`BETWEEN x BETWEEN a AND b` 的 `x` 文本出现 2 次 → 每记录求值 2 次；`CASE test WHEN w1 THEN t1 WHEN w2 THEN t2 ...` 的 `test` 文本嵌入每个 when 条件 → 未命中前每记录求值 2..M 次。
- **量化口径**：频率 = `<sql>`/`<sqlTransform>` 编译产物含 WHERE-BETWEEN/CASE 的管线每记录。代价链：test 为纯列引用时每记录多付 1..M-1 次列探测（`map.containsKey`+`get`，数十 ns/次）；test 为算术链/UDF 调用时按子树成本 ×(重复次数) 放大——UDF 经 `SqlScalarXplPrinter:186-198` 发射为静态 `call(...)`，**有副作用或高成本的 UDF 在 BETWEEN/CASE 内被重复执行**，与 eval 通道行为分叉（`StreamSqlExprCompiler.compileCase:159-172` 将 test 求值一次存局部变量 `Object test = t.eval(r)`）。
- **现状**：等价性钉测试（类 javadoc 声明的「same record matrix through both engines」）覆盖纯函数语义面；重复求值的成本/副作用面未见钉。
- **风险**：热路径可量化浪费 + 双通道在非纯 UDF 下可观察行为分叉；无数据竞争。
- **建议**：① 生成 `let __t = (<testText>);` 前置绑定，条件体引用 `__t`（对齐 eval 通道单次求值语义）；② 或在 WI9 等价性钉中补「副作用 UDF 置于 BETWEEN/CASE test」的反事实用例，显式声明通道差异为边界。
- **失效面枚举（F3 同族 grep）**：`print(expr.getTest())/print(test)` 重复打印点 = `printBetween`（test ×2）、`printCase`（test ×M 分支）；`printBinary`/`printAnd`/`printOr`/`printNot`/`printIn` 各子表达式均单次打印（clean）。失效面 = 经 `printWhere`/`printHaving`/`projectionNode` 发射的全部 `<sql>`/`<sqlTransform>` 产物中的 BETWEEN 与简单 CASE。
- **信心水平**：高（文本生成事实可直接从产物核对）；性能权重中低。
- **误报排除**：语义等价性对纯表达式成立，不虚报正确性缺陷；重复求值仅对非纯/高成本子树立案。
- **复核状态**：已保留（P3——printBetween L291-305 test 文本 ×2（L295/297 两次 printBinaryOp 传入 getTest()）、printCase testText 嵌入每分支 cond（L230-235，引用范围 216-241 覆盖主体）、UDF 发射 L186-198 静态 `StreamScalarFunctionResolver.call` 逐字属实；compileCase L159-172 test 求值一次存局部变量属实。复核补充观察（不改判级、不改条目原文）：本条 F3 枚举称 printBinary/printAnd/printOr/printNot/printIn「clean」不准确——三值 null-guard 发射模式使操作数文本多次嵌入产物（printBinary 操作数 ×2、printAnd/printOr 操作数 ×3、printNot ×2、printUnary MINUS ×2、printIn probe ×(N+1)），失效面大于枚举范围，属同缺陷类同量级，修复时应一并覆盖，不改变本条 P3 结论。现状：新发现，未修复）

### 基准缺口登记（C9）
1. flow 每记录 scope 链分配（C9-001）无 JMH 基准——`nop-benchmark` 中无 stream 求值相关基准（仓扫无命中），建议补 `XplFunctionScopeAllocBenchmark`（单跳 vs 双跳、bindings 有/无）。
2. sql 求值管线（`RecordColumnAccess` 反射路径 vs Map 路径、`SqlRowAggregateOps` 端到端每记录吞吐、C9-003 重复求值倍率）无基准锚点——owner doc 未声明每记录求值规模包络，性能回归不可观测。
3. `XplWatermarkGenerator.onEvent` 每 body 调用 scope 分配在水位高频管线（watermarkInterval=0 逐事件形态）下逐事件执行——无独立基准，登记观测点。

---

## 附：发现汇总

| ID | 维度 | 标题（缩写） | 严重度 | 信心 |
|---|---|---|---|---|
| nop-stream-orchestration-thin-C2-001 | C2 | UDF 静态 default 竞态 + 运行期静默换绑 + 与「编译期一次解析」契约不符 | P2 | 高 |
| nop-stream-orchestration-thin-C2-002 | C2 | sideInput 冻结快照直通每记录 scope 可写存储（UOE 崩溃/跨记录污染/有条件跨线程 UB） | P2 | 机制高/触发低 |
| nop-stream-orchestration-thin-C4-（无） | C4 | 本轮抽查无发现（唯一线程站点核对合规） | — | — |
| nop-stream-orchestration-thin-C5-（无） | C5 | 本轮抽查无发现（9 增长面均有界或已声明） | — | — |
| nop-stream-orchestration-thin-C7-001 | C7 | ReplayEngine 异常吞没转 cancel，完成/失败不可区分（F9/F2 半个成功） | P2 | 高 |
| nop-stream-orchestration-thin-C8-（无） | C8 | 本轮抽查无发现（5 边界同步直推背压合规） | — | — |
| nop-stream-orchestration-thin-C9-001 | C9 | 每记录双跳 scope 链分配放大 | P3 | 高 |
| nop-stream-orchestration-thin-C9-002 | C9 | begin() 每记录重复缓存查找 | P3 | 高 |
| nop-stream-orchestration-thin-C9-003 | C9 | xpl 发射通道子表达式重复求值（BETWEEN×2 / CASE×M）+ 双通道行为分叉 | P3 | 高 |

**P0=0 / P1=0 / P2=3 / P3=3**。三个无发现维度（C4/C5/C8）的已核对站点清单与未盘点面声明已分别在对应节首列明。

---

## 深挖第 2 轮追加（C2/C7/C9）

- 深挖口径：仅对第 1 轮有发现的三个维度（C2/C7/C9）做定向补核；C4/C5/C8 维持第 1 轮闭合。盲审纪律延续：未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下除本批次前序轮次文件外的任何文件。
- 本轮新增核对输入：全模块 grep `wait(|notify|Condition|LockSupport|park(|ReadWriteLock|StampedLock|ReentrantLock`（C2 等待原语/高级锁兜底）、`catch` 全清单（C7 同族）、`metric|gauge|Meter|Counter|label|cardinal`（C7 基数专项）、非 final static 字段 sweep（C2 静态可变态兜底）、`nop-benchmark/nop-benchmark-stream` 全清单（C9 基准缺口复核）、core `TimestampsAndWatermarksOperator` 只读契约核对（C9 水位面归类用，不审 core）。

### C2 第 2 轮

**本轮核对范围**（第 1 轮锁序图仅覆盖基线命中站点，本节补齐三类）：

1. **C2-001/002 失效面兄弟站点——bindings 调用点之外的 evalScope 创建点全量核对**：全单元 `newCallScope` 调用点 16 处逐一归类——
   - bindings 非空（C2-002 已立案缺陷面）：`XplMapFunction.java:48`、`XplFilterFunction.java:48`、`XplFlatMapFunction.java:51`（3 处，无新增）。
   - 双跳形态（C9-001 已立案性能面，C2 面无缺陷）：`EvalActionKeySelector.java:40-41`、`JoinSideTagFunction.java:53-54/58-59`、`SideOutputTapOperator.java:76-77`（4 个调用点，无新增）。
   - 无 bindings 单跳（**本节核对的兄弟站点，全部 clean**）：`XplReduceFunction.java:37`、`XplSinkFunction.java:37`、`XplSourceFunction.java:60`、`AdvancedTransforms.java:1268`（XplWatermarkStrategy.createTimestampAssigner 闭包体）、`AdvancedTransforms.java:1314`（XplWatermarkGenerator.onEvent）——每记录 variables 均为 `newCallScope()` 新建的独立 HashMap，写入仅随 scope 丢弃，无共享可变 map、无跨记录污染面，不构成 C2-002 同族。
   - `ROOT_SCOPE` 静态创建点全单元仅 `XplFunctionSupport.java:35` 一处（static final，`EvalExprProvider.newEvalScope()`），无第二静态 scope 源；未发现绕过 `newCallScope` 的 `EvalScopeImpl`/`newChildScope` 直接构造点。
2. **等待原语（基线未覆盖的等待点）**：grep `wait(|notify|notifyAll|Condition|LockSupport|park(|await` 三模块 **0 真实命中**（仅 `IterativeCondition`/`getCondition`/`decomposeOnCondition` 词法误命中）。4 处 `Thread.sleep`（`ReplayableCdcSourceFunction.java:175/179`、`DirectoryFileSourceFunction.java:125/212`）第 1 轮已核有界。无无超时等待点。
3. **ReadWriteLock/StampedLock/ReentrantLock 使用点**：grep **0 命中**。锁载体集合与第 1 轮锁序图一致（`DirectoryFileSourceFunction.cursors` 监视器 + `ReplayEngine.stop()` synchronized + 2 个 CHM），无新锁边、仍为平凡图。
4. **静态可变态补充 sweep**：非 final static 字段全单元仅 `StreamScalarFunctionResolver.defaultResolver`（C2-001 已立案，无新兄弟）。`DemoKeyedStateStore` 全文复核：DEMO-ONLY 声明（类 javadoc 23-38 行）、单线程 demo main 使用、无跨线程共享锁面（维持 C5 条目 5 已声明边界判定）。

**本轮发现**：无。

**排除项**：上述 4 类核对站点全部 clean；无新增锁载体/等待点/静态可变态。

**覆盖声明**：本节与第 1 轮合并后，C2 的站点覆盖 = 基线命中站点全量 + 全单元 16 个 newCallScope 调用点 + 全部并发原语 grep 兜底（三重口径均 exhausted）；未盘点面仍为平台设施自身实现（`EvalScopeImpl`、NopIoC BeanContainer）与 runtime 单元的 TaskExecutor/checkpoint 线程。

**C2 维度深挖终止。**

### C7 第 2 轮

**本轮核对范围**：

1. **「完成信号被失败路径复用」同族清扫（C7-001 扩展）**：三模块 `catch` 块全清单逐一核对——
   - fraud（5 处）：`FraudDetectionDemo.java:95-101` catch 后 **rethrow** `IllegalStateException`（注释明言 exit non-zero，"fails loudly" 成立——clean，且反证 C7-001 的吞没形态非模块统一样式）；`UserHistoryEnricher.java:73-82` 两处 catch(IOException) 均 rethrow `IllegalStateException`（clean）；`ReplayableCdcSourceFunction.java:181-182` catch(InterruptedException) 恢复中断位后落 finally cancel——中断即外部停止请求，cancel 是正确响应（clean）；`ReplayableCdcSourceFunction.java:183-188` catch(Exception) 仅 LOG 后落 finally cancel——即 C7-001 本体（不重复立案）。
   - flow（16 处）：`StreamConfValidator`（7 处）、`StreamModelLinter:67`、`AdvancedTransforms:470/624/903`、`StreamModelDslBuilder:563/572/690/699/1509/2289`——全部为**构建期校验**的 typed error 包装（NopException→ValidationIssue/StreamException），声明面 fail-fast，非运行期完成信号复用（clean）。
   - sql（4 处）：`StreamAggregatorFunctionResolver.java:81`、`SqlRowAggregateSpec.java:137`、`RecordColumnAccess.java:66`、`StreamSqlExprCompiler.java:617`——全部 rethrow 为带上下文的 typed `StreamException`/`invalidArg`（clean）。
   - 同点佐证（不另立案）：`ReplaySubscription.getCancelReason()`（`ReplayableCdcSourceFunction.java:142-144`）在失败后同样返回 `ICancellable.CANCEL_REASON_STOP`——失败位缺失是 `ReplayEngine.cancelled` 单标志的同一缺陷点，归 C7-001 修复面（修复①的 failure 位应同时驱动 getCancelReason）。
2. **metrics label 基数专项**：grep `metric|gauge|Meter|Counter|label|cardinal` 三模块 **0 真实站点命中**（唯一 "cardinality" 为 `StreamSqlAggregation.java:429` DistinctCountAgg javadoc 文字；flow 命中均为 "symmetric" 词法误命中）。本单元无任何 metric/gauge 注册点 → 无 label 基数面。基线 C7 supplement 节的命中（第 1 轮已判定 flow 5 条构建期注释、fraud UserTransactionHistory/UserHistoryEnricher）不含 metric 站点，与本节 grep 结论一致。

**本轮发现**：无。

**排除项**：catch 全清单 25 处中仅 C7-001 本体 1 处立案（已在前轮）；metrics 基数专项无站点。

**覆盖声明**：本节与第 1 轮合并后，C7 覆盖 = 基线 supplement 命中 + 三模块 catch 全清单 + 等待点/sleep 全清单（4 处，有界）+ metrics 站点 grep 兜底。未盘点面不变：fencing epoch、健康状态机、checkpoint 自愈语义归 runtime 审计单元；core `TimestampsAndWatermarksOperator` 的周期 timer 语义归 core 单元（见 C9 第 2 轮水位面归类）。

**C7 维度深挖终止。**

### C9 第 2 轮

**本轮发现**

### [nop-stream-orchestration-thin-C9-004] eval 通道 compileBetween 对 BETWEEN test 每记录重复求值 ×2——C9-003 同族成员，且第 1 轮「eval 通道单次求值」的对照表述需修正
- **文件**：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamSqlExprCompiler.java:489-500`
- **证据片段**：
  ```java
  private static StreamRecordEvaluator compileBetween(SqlBetweenExpr expr) {
      StreamRecordEvaluator test = compileScalar(expr.getTest());
      ...
      return r -> {
          Object geBegin = compareOp(test.eval(r), begin.eval(r), SqlOperator.GE);
          Object leEnd   = compareOp(test.eval(r), end.eval(r), SqlOperator.LE);   // test 第 2 次求值
          Boolean inRange = logicalAnd(geBegin, leEnd);
          return inRange == null ? null : (not != inRange);
      };
  }
  ```
- **严重程度**：P3
- **并发场景**：非竞态，每记录求值放大。`compileBetween` 产物的闭包在同一表达式内调用 `test.eval(r)` 两次（GE 与 LE 各一次）；`test` 为编译期捕获的子求值器（含 UDF/算术链时为任意成本子树）。这是 C9-003（xpl 发射通道 BETWEEN test 文本 ×2）在 **eval 通道**的同族成员——第 1 轮 C9-003 的 F3 枚举只覆盖了 printer 通道产物，且以 `compileCase`（159-172 行，test 求值一次存局部变量）作为「eval 通道单次求值」的行为对照——该对照对 BETWEEN 不成立：eval 通道 CASE 单次、BETWEEN ×2。
- **触发时序**：T0 作业含 `WHERE x BETWEEN a AND b`（x 为列引用或子表达式），每记录进入闭包；T1 第 1 次 `test.eval(r)`（列探测 containsKey+get 或子树求值）；T2 第 2 次 `test.eval(r)` 重复同一计算；若 test 是经 `compileFunction` 编译的 UDF（244-250 行闭包，`fn.apply(args)`），该 UDF 每记录执行 2 次——有副作用的 UDF（计数、外部调用）行为与 SQL 单次求值语义分叉，且与 xpl 通道（C9-003，同 ×2）在 BETWEEN 上其实一致、在 CASE 上分叉（×M vs ×1）——第 1 轮「与 eval 通道（单次求值）行为/成本双分叉」的表述按此修正为通道×表达式矩阵。
- **现状**：`begin`/`end` 各求值一次（正确）；`compileIn`（502-530）probe 单次、values 预编译（clean）；`compileCase` test 单次（clean）——仅 BETWEEN 的 test 双求值。
- **风险**：热路径可量化浪费（纯列引用时多 1 次列探测，UDF/算术链时 ×2 子树成本）；非纯 UDF 下语义面与单次求值 SQL 语义分叉。无数据竞争。
- **建议**：与 C9-003 建议同构——`Object tv = test.eval(r);` 提取局部变量后两次 `compareOp(tv, ...)`（一行改动，同时使 eval 通道与 compileCase 的单次求值性质一致）；或在 WI9 等价性钉中补「副作用 UDF 置于 BETWEEN test」反事实用例声明两通道边界。
- **失效面枚举（F3 同族 grep）**：`StreamSqlExprCompiler` 全部 compileXxx 闭包逐一体核对——`test.eval` 重复调用仅 `compileBetween` 1 处；`compileBinary`/`compileAnd`/`compileOr`/`compileNot`/`compileUnary`/`compileIsNull`/`compileCast`/`compileCase`/`compileIn`/`compileFunction` 各子表达式均单次求值（clean）。失效面 = eval 通道（`compileScalar` 产物，用于 projection/aggregate arg/join keyExpr）产物中的 BETWEEN。
- **信心水平**：高（闭包体代码事实可直接核对）；性能权重中低（与 C9-003 同量级）。
- **误报排除**：不虚报正确性缺陷（纯表达式语义等价）；装箱分配（Boolean/Long/Double 返回值）为表达式求值形态固有，不立案；`compileFunction` 的 `Object[] args` 每记录分配为 `IScalarFunctionResolver` SPI 声明契约（`Function<Object[], Object>`）固有，登记为设计边界非缺陷。
- **复核状态**：已保留（P3——compileBetween L489-500 test.eval(r) ×2（L495/496）逐字属实；compileCase L162 test 单次、compileIn L512 probe 单次属实；F3 枚举经全 compileXxx 闭包核对准确（compileBinary L429-449 / compileAnd L458 / compileOr L464 / compileNot L469 / compileUnary L475 / compileIsNull L484 / compileCast L138 / compileFunction L244-250 各子表达式均单次求值）；对第 1 轮 C9-003「eval 通道单次求值」对照表述的修正成立（eval 通道实为 CASE 单次、BETWEEN ×2）。现状：新发现，未修复）

**本轮排除项（同族清扫）**：

1. **水位传播**：`XplWatermarkGenerator.onEvent`（`AdvancedTransforms.java:1312-1315`）每事件 1+1 scope 分配——第 1 轮基准缺口 3 已登记，无新增。空 `onPeriodicEmit`（1318-1319）经 core 只读核对归类为**声明面一致**：DSL 模型（`StreamTimestampsAndWatermarksModel`）仅声明 assigner+generator 两个 xpl body、无周期 body，xpl 生成器按契约在 onEvent 体内经 `output` 自发水位；core `TimestampsAndWatermarksOperator` 周期 timer 调用该 no-op 属跨单元语义面（watermarkInterval>0 时周期 timer 空转 + 每 record 门控重复调用 onPeriodicEmit），归 core 审计单元，登记为跨单元观察点（本单元无可修缺陷）。
2. **window 分配**：`AdvancedTransforms.resolveWindowAssigner/resolveTrigger/resolveEvictor`（514/366/442 行）均为构建期一次性解析；每记录 window 分配（pane 分配、assigner.assignWindow）在 core 单元 → 本单元排除。
3. **序列化路径**：本单元 src/main/java 无每记录序列化代码（`CoderModel` 为构建期模型类；fraud POJO 走默认 Serializable；sql 无 coder 站点）；编解码基准已有 `nop-benchmark-stream/StreamElementCodecRoundTripBench` 覆盖 core 面 → 本单元排除。
4. **sql 聚合器每记录分配**：`StreamSqlAggregation` 全部 Agg（CountStarAgg/CountAgg/SumAgg/AvgAgg/MinMaxAgg/MaxByAgg/DistinctCountAgg，142-540 行）`add` 均为累积器**原地变异**（SumAgg 248-268、AvgAgg 321-330、MinMaxAgg 495-514 等），仅 Long/Double 装箱固有分配（clean）；`SqlRowCompositeFunction.add`（61-74 行）原地变异 0 结构性分配，`getResult` 的 row List 每结果发射一次非每记录（clean）；`SqlAccShapeUtil.asArray` 为检查性 cast 无分配（clean）。
5. **fraud 每记录站点**：`CdcChangeDecoder.flatMap`（34-66）/`TransactionLineParser.map`（29-46）——BigDecimal/String 分配为 CDC 解码固有、malformed 输入 fail-fast（与 C7-001 反证同向）；`DirectoryFileSourceFunction` 每行 `advanceCursor`（synchronized TreeMap.put O(log n)，单读者无争用）——demo 校准，登记优化候选不立案。

**基准缺口登记修正（对第 1 轮 C9 基准缺口 1/3 的事实修正）**：

- 第 1 轮缺口 1 括注「`nop-benchmark` 中无 stream 求值相关基准（仓扫无命中）」**不准确**：`nop-benchmark/nop-benchmark-stream` 存在且含 16 个基准（`WindowOperatorProcessElementBench`、`WatermarkEmitBench`、`TimerServiceBench`、`StreamElementCodecRoundTripBench`、`NfaProcessBench`、`CepOperatorBench` 等，覆盖 core/runtime 面）。缺口本身成立但应收窄表述并改挂靠点：grep `XplFunctionSupport|newCallScope|SqlRowAggregateOps|RecordColumnAccess|StreamSqlExprCompiler|XplMapFunction|EvalScope` 于 `nop-benchmark-stream/src/main/java` **0 命中**——即「flow xpl-fn scope 链（C9-001）与 sql 求值管线（含 C9-004 重复求值倍率）无基准」属实，补基准时应放入既有 `nop-benchmark-stream` 模块而非新建。
- 缺口 3 相邻事实：`WatermarkEmitBench`（elementOnly/watermarkOnly/elementPlusFiringWatermark 三态）已覆盖 core operator 水位发射路径；xpl generator body 变体（`XplWatermarkGenerator.onEvent` 的 body+scope 成本）未覆盖——缺口维持，挂靠点同上。
- 无其他遗漏高频面：本单元每记录面已三路扫尽（第 1 轮算子路径 + 第 2 轮 sql 闭包/聚合器/watermark/window/序列化抽验），基准缺口登记集合（scope 分配、sql 求值端到端、watermark body）已完备。

**覆盖声明**：本节与第 1 轮合并后，C9 覆盖 = 第 1 轮「sql 展开管线 + flow 算子路径」每记录站点 + 第 2 轮 eval 通道闭包全体、聚合器全体、watermark/window/序列化三面抽验、fraud 解码路径、基准模块核对。未盘点面不变：`IEvalFunction.call1` 内部 XLang 解释/编译执行体、序列化器编解码实现归平台设施（nop-core/nop-xlang）；window pane/CEP SharedBuffer/TaskExecutor 归 core/runtime 单元。

**C9 维度深挖终止。**

---

### 深挖第 2 轮汇总

| ID | 维度 | 标题（缩写） | 严重度 | 信心 |
|---|---|---|---|---|
| nop-stream-orchestration-thin-C9-004 | C9 | eval 通道 compileBetween test ×2 每记录重复求值（C9-003 同族，修正第 1 轮双通道对照表述） | P3 | 高 |
| C2 / C7 | C2/C7 | 无新发现（兄弟站点/等待原语/高级锁/catch 全清单/metrics 基数专项均 clean） | — | — |

**第 2 轮新增：P0=0 / P1=0 / P2=0 / P3=1**。累计（两轮）：P0=0 / P1=0 / P2=3 / P3=4。三个深挖维度均出具终止信号。

---

## 抽查批次复核结论（独立复核子代理，按维度分套）

- 复核子代理：独立复核 subagent（与初审者不同角色，未参与第 1/2 轮抽查）
- 复核日期：2026-10-14
- 复核对象：本文件全部 7 条发现（C2-001/002、C7-001、C9-001/002/003/004）+ 三个无发现维度（C4/C5/C8）的清洁站点声明
- 复核方法：逐条重读 live code（分支 concurrency-audit-remediation @ 8dcfa4cd0c）核对文件/行号/证据引文/时序/量化口径/判级；历史对照限 ai-dev/audits/ 下 nop-stream 相关目录（含同 campaign WI 1.1/WI 1.2 审计目录、cra-index.md、nop-stream-sql-enhancement/wi30-closure-audit.md）+ git log；未读 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/
- 交叉引用对照：C1-shared-mutable-state.md 复核节（C1-001 保留 P1、C1-004 保留 P2）、C3-async-chains.md 复核节（C3-001 降级 P1→P2）

### 逐维度判定表

**C2 锁与同步原语**

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C2-001 | P2 | **保留** | 证据引文逐字属实（volatile default + 裸 LinkedHashMap + 无守卫公开 register + 逐记录静态读），sql.md §3a 声明偏差属实；**cross-ref：与 C1-001 同一机制（C1 维度复核保留 P1），本条为锁与同步视角的 P2 自洽，合并裁定阶段归并为单一发现、最终判级随 C1-001 P1** | 新发现，未修复（WI30 closure audit 仅登记功能面 PASS，非并发缺陷登记） |
| C2-002 | P2 | **保留** | 机制链五锚点全部核实（bindings 直通构造器无拷贝、setLocalValue 直写共享 map、slotIndex<0 回退存在），「冻结只读快照」声明零强制属实；**cross-ref：与 C1-004 同一机制（C1 维度复核保留 P2），判级自洽，合并裁定阶段归并** | 新发现，未修复 |

**C7 长时运行稳定性**

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C7-001 | P2 | **保留** | catch-all 仅 LOG + 完成失败同走 cancel + cancelled 单标志无失败位 + getCancelReason 同盲全部逐字属实，剩余触发面（replay index 解析/offset store 故障）真实；**cross-ref：与 C3-001 同一代码点（C3 维度复核降级 P1→P2——父类 pendingError 通道否证 collect 主触发），本条立案即为 P2 且触发时序已自限于非 collect 失败，与 C3 复核后判级同向自洽，维持 P2，合并裁定阶段同代码点并案** | 新发现，未修复（WI 1.2 三次登记本类为「范围外，登记供该模块审计引用」，无同发现先例） |

**C9 并发高性能·热路径**

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C9-001 | P3 | **保留** | 三站点双跳形态逐字属实；4 次分配 vs 2 次 = 2× 的对比成立（「桶数组懒初始化」表述含糊但为空 map 对象分配，结论不变） | 新发现，未修复 |
| C9-002 | P3 | **保留** | begin 同表达式双查逐字属实、merge/toRow 单次属实、每记录频率口径与发射体相符 | 新发现，未修复 |
| C9-003 | P3 | **保留** | printBetween test ×2 / printCase test ×M / UDF 静态发射逐字属实，compileCase 单次对照属实；**复核补充观察：F3 枚举称 printBinary/printAnd/printOr/printNot/printIn「clean」不准确——三值 null-guard 模式使操作数文本多次嵌入（×2/×3/×(N+1)），失效面大于枚举，同缺陷类同量级，不改判级** | 新发现，未修复 |
| C9-004 | P3 | **保留** | compileBetween test.eval ×2 逐字属实；F3 枚举经全 compileXxx 闭包核对准确；对第 1 轮双通道对照表述的修正成立 | 新发现，未修复 |

（上表现状标注统一口径：基线 b3a9c76bed 之后本分支无任何触及本审计单元 src/main/java 的提交（git log 核空），本单元属 CRA 首轮并发审计（WI 1.3），无前置并发审计登记过上述任一机制——故 7 条全部为「新发现，未修复」，无「已知未修复」「已修复」项。）

### C4/C5/C8 清洁声明抽查结果（各抽 2 站点独立核对）

| 维度 | 抽查站点 | 复核结论 |
|---|---|---|
| C4 | `ReplayableCdcSourceFunction.java:127`（fraud-s1-replay-cdc 线程） | **清洁成立**——命名/daemon 位/单发有界/runReplay 必达 finally 自取消逐项核实 |
| C4 | flow/sql 两模块线程池 grep 兜底 | **清洁成立**——`new Thread\|ExecutorService\|Executors.\|ScheduledExecutor\|commonPool\|ForkJoin` 复 grep 均 0 命中 |
| C5 | `StreamSqlAggregation.DistinctAggregateWrapper.seen`（L601-639） | **清洁成立**——增长面属实且 sql.md L35 明文声明「精确去重内存代价 = 每组去重值数量——窗口随窗清除、持续聚合随作业生命周期」 |
| C5 | `RecordColumnAccess.GETTER_CACHE`（L36-37, 82-111） | **清洁成立**——CHM + 键 = 已加载 Class、体内纯反射扫描、值只读、不递归查本 map |
| C8 | ReplayEngine 发射线程 → `action.accept`（L172 同步直推） | **清洁成立**——无中间队列，下游 collect 阻塞即背压，与声明协作语义一致 |
| C8 | `DirectoryFileSourceFunction` 单线程循环 → `ctx.collect`（L210） | **清洁成立**——无队列、无信号量、无限流面，失败直接从 run 传播（与 C7-001 的反证方向一致） |

### 不确定项（显式列出）

1. **C9-001 量化表述**：「1 HashMap 桶数组懒初始化」实际是 `new HashMap<>()` 对象分配（桶数组因中间 scope 从未被写入而不分配）——表述含糊不影响 2× 分配量结论，已在该条复核状态行注明。
2. **C9-003 F3 失效面枚举偏差**：printBinary/printAnd/printOr/printNot/printUnary/printIn 并非 clean（操作数文本多次嵌入产物、运行期重复求值），失效面大于第 1 轮枚举——不改判级（同缺陷类、同量级），已在该条复核状态行注明，修复面应一并覆盖。
3. **C2-002 跨 subtask 形态**：「runtime 共享 UDF 实例跨 subtask 升级为跨线程 UB」依赖引擎侧部署语义（DirectoryFileSourceFunction javadoc 声明的 runtime 共享形态），本单元内无法完全证实/否证——发现已自带「有条件竞态」标注，机制主体（契约穿透）不依赖该形态，可接受。
4. C2-001/C2-002/C7-001 三条与 C1-001/C1-004/C3-001 为同一机制的维度视角或同代码点，最终判级与立案归并由合并裁定阶段统一处置（本复核不预设归并后判级，仅声明自洽方向：C2-001 随 C1-001 P1、C2-002 随 C1-004 P2、C7-001 与 C3-001 并案 P2）。

---

## 最终保留项（按维度分套）

**C2 锁与同步原语（2 项）**

| 编号 | 最终严重程度 | 文件路径 | 摘要 | 现状 |
|---|---|---|---|---|
| C2-001 | P2（本维度视角；同一机制 C1-001 复核保留 P1，合并裁定归并） | nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamScalarFunctionResolver.java:31-51,88-97 | 静态 volatile default + 裸 LinkedHashMap 注册表：公开 register 写 × 逐记录静态 call 读零同步（竞态 UB + registerAsDefault 运行期静默换绑），xpl 通道逐记录查表与 sql.md §3a「编译期一次解析」声明不符 | 新发现，未修复 |
| C2-002 | P2 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/functions/XplFunctionSupport.java:47-52；nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:1526-1550 | sideInput 冻结快照 map 直通每记录 scope 可写 variables（写路径穿透只读契约：未声明赋值 UOE 崩溃 / 内层可变 map 跨记录污染） | 新发现，未修复 |

**C7 长时运行稳定性（1 项）**

| 编号 | 最终严重程度 | 文件路径 | 摘要 | 现状 |
|---|---|---|---|---|
| C7-001 | P2 | nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java:165-190 | ReplayEngine 异常仅 LOG 后 cancel 完成，「回放完成」与「回放失败」不可区分，作业正常终结掩盖半程数据（与 C3-001 同代码点，并案处置） | 新发现，未修复 |

**C9 并发高性能·热路径（4 项）**

| 编号 | 最终严重程度 | 文件路径 | 摘要 | 现状 |
|---|---|---|---|---|
| C9-001 | P3 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/EvalActionKeySelector.java:40-41；.../functions/JoinSideTagFunction.java:53-59；.../SideOutputTapOperator.java:76-77 | keyBy/join/tap 每记录双跳 scope 链，中间空 scope 纯属多余分配（4 vs 2 次分配 = 2×） | 新发现，未修复 |
| C9-002 | P3 | nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/SqlRowAggregateOps.java:43-45 | 连续聚合 begin 每记录对复合函数缓存重复查找 ×2（长 spec 下多付一次全串 equals） | 新发现，未修复 |
| C9-003 | P3 | nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/compile/SqlScalarXplPrinter.java:155-157,216-241,291-305 | xpl 发射通道 BETWEEN test ×2 / CASE test ×M 重复求值文本；复核补充：null-guard 模式使 printBinary/printAnd/printOr/printNot/printUnary/printIn 同样重复（×2/×3/×(N+1)），修复面应一并覆盖 | 新发现，未修复 |
| C9-004 | P3 | nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamSqlExprCompiler.java:489-500 | eval 通道 compileBetween test 每记录重复求值 ×2（C9-003 同族；修正第 1 轮「eval 通道单次求值」的对照表述） | 新发现，未修复 |

（无驳回项。降级项：无——7 条全部维持原判级；三条 cross-ref 机制的最终判级归并见上表及不确定项 4。）
