# C3 异步链路正确性 — nop-stream-orchestration-thin 第 1 轮初审（盲审）

- 审计单元：`nop-stream-orchestration-thin`（`nop-stream/nop-stream-flow` + `nop-stream/nop-stream-sql` + `nop-stream/nop-stream-fraud-example`，合计 98 files / 13,792 lines src/main/java，排除 `_` 前缀生成文件）
- 维度：C3 异步链路正确性（异常传播 / 取消与超时 / 上下文传播 / 时序假设 / F1、F2 定向对照）
- 审计日期：2026-10-14；分支：concurrency-audit-remediation；盲审（未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件）
- 依据 owner doc：`docs-for-ai/03-modules/nop-stream-user-guide.md`、`docs-for-ai/03-modules/nop-stream-sql.md`
- grep 基线：`_tmp/cra-baseline-nop-stream-orchestration-thin.txt`（C3 节为空——基线 grep 未命中 CompletableFuture/Executor；本审计以人工全量核对补充，见下）

---

## 检查范围说明（异步入口清单）

基线 C3 节（`CompletableFuture|thenApply|whenComplete|...`）在三个模块 src/main/java 命中为 0。本审计以独立 grep（CompletableFuture/Future/Executor/Thread/Runnable/Timer/Scheduled、synchronized/volatile/Atomic/ConcurrentHashMap/ThreadLocal、Consumer/Listener/Callback/subscribe）+ 逐文件人工核对完成全量盘点。**异步入口全量清单**如下：

| # | 异步入口 | 位置 | 性质 |
|---|---|---|---|
| E1 | 自建线程提交 `new Thread(..., "fraud-s1-replay-cdc")` | `ReplayableCdcSourceFunction.ReplayEngine.subscribe` (L126-131) | 唯一线程创建点（基线 C4 节同源） |
| E2 | 回调接口 `Consumer<ChangeEvent> action`（CDC 事件消费回调） | `ReplayEngine.runReplay` (L165-190)；接线于父类 `DebeziumCdcSourceFunction.run` (nop-stream-connector-debezium, L260-277) | 跨线程回调：emitter 线程 → `ctx.collect` → 任务线程链路 |
| E3 | 取消回调集 `onCancelTasks`（`ICancellable.appendOnCancel/removeOnCancel/cancel`） | `ReplaySubscription` (L133-163) | `ICancellable` 契约回调面 |
| E4 | 源函数取消旗标 `volatile boolean cancelled/running` | `ReplayEngine` (L116)、`DirectoryFileSourceFunction` (L82)、`XplSourceFunction` (L45) | 协作式取消信号 |
| E5 | 静态共享求值根作用域 `ROOT_SCOPE` | `XplFunctionSupport` (L35-52)，被 Xpl{Map,Filter,FlatMap,Sink,Reduce}Function、EvalActionKeySelector、JoinSideTagFunction 全部 7 个包装器调用 | 多任务线程并发的 per-record 求值入口 |
| E6 | 静态默认解析器 `static volatile StreamScalarFunctionResolver defaultResolver` + 实例 `LinkedHashMap functions` | `StreamScalarFunctionResolver` (L31-97) | 编译期 resolve() 与运行期 call()（generated xpl 体逐记录调用）共享面 |
| E7 | 静态缓存 `ConcurrentHashMap CACHE`（spec JSON → SqlRowCompositeFunction） | `SqlRowAggregateOps` (L27-38) | 运行期逐记录 `function(specJson)` 命中 |
| E8 | 静态缓存 `GETTER_CACHE`（Class → getter 反射表） | `RecordColumnAccess` (L36-37, L82-111) | 运行期逐记录 `computeIfAbsent` |
| E9 | side-input 构建期快照注入链 | `StreamModelDslBuilder.materializeSideInputs` (L1520-1550) → `XplMapFunction.bindings` → `newCallScope(bindings)` | 构建期单线程物化、运行期多任务线程只读（期望） |
| E10 | 定时任务 / 消息监听器 / ReactiveStreams / CompletableFuture 链 | — | **三模块 src/main/java 内不存在**（checkpoint 调度、2PC 提交、事件总线均在 nop-stream-runtime / connector 模块，不在本单元） |

**规模包络对照**：基线 file count 60/17/21、line count 6202/4334/3256 与任务书一致，无规模异常。

**关键跨模块语义核对**（为判断本单元发现而只读的相邻 live code，不作为发现对象）：`DebeziumCdcSourceFunction`（connector-debezium，`pendingError` 机制 L96-105/L283-296）、`StreamSourceOperator`（nop-stream-core，collect() 先 drain 控制面 mail 再 output.collect，L263-272；`isCancelled` 接 mailbox 取消旗标 L309-317）、`EvalScopeImpl`（nop-core，`setLocalValue` 只写子作用域自身 map，L194-201；`newChildScope` 不触碰父作用域，L111-125）。

---

## 发现条目

### [nop-stream-orchestration-thin-C3-001] CDC 回放引擎失败被吞掉并伪装为作业正常 EOS（半个成功，无恢复语义）

**文件**：`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java` L165-190（runReplay），对照父类 `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java` L96-105、L283-296、L331-344

**证据片段**（ReplayableCdcSourceFunction.java L165-190）：

```java
private void runReplay(Consumer<ChangeEvent> action) {
    try {
        long resumeIndex = readReplayIndex();
        for (int i = (int) resumeIndex; i < events.size(); i++) {
            if (cancelled) { break; }
            action.accept(events.get(i));
            writeReplayIndex(i + 1L);
            ...
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } catch (Exception e) {
        LOG.error("CDC replay engine failed; cancelling source so the job fails loudly", e);
    } finally {
        // Bounded source completion: the production run() loop only exits on
        // cancel/drain, so a completed replay must cancel its own source.
        ReplayableCdcSourceFunction.this.cancel();
    }
}
```

**严重程度**：P1

**并发场景**：emitter 线程 `fraud-s1-replay-cdc`（E1）与任务线程（父类 `run()` 的 wait 循环）跨线程协作。成功路径与失败路径都收敛到同一个 `finally { cancel() }`——cancel 是该有界源的"完成信号"，因此任何 `Exception`（`writeReplayIndex` 的 offset-store 故障、`readReplayIndex` 的 `NumberFormatException`、下游 `ctx.collect` 抛出的采集链异常）都与正常放完 fixture 无法区分。

**触发时序**：
- T1（emitter 线程）：放至第 5/10 个事件，`action.accept(...)` 内 `ctx.collect` 因下游瞬时故障抛异常（或 `writeReplayIndex` 抛异常）；
- T1：异常落入 `catch (Exception e)`，仅 LOG.error；`finally` 调用 `ReplayableCdcSourceFunction.this.cancel()`；
- T2（任务线程）：父类 `run()` 的 `while (running && !draining)` 循环观测到 `running=false` 退出，此时 `pendingError == null`，`throwPendingError()` 不执行，`run()` 正常返回；
- T2→调度器：source run() 返回 = EOS，作业终态为**正常结束（FINISHED 语义）**，剩余 5 个事件被静默丢弃；因作业非 FAILED，不触发失败重启，checkpoint 也不会在终态后被消费。

**现状**：父类已内置 R5-CON-02/CON-03 的 `pendingError` 机制（"first error observed on the CDC path ... `run()` rethrows it after the wait loop exits so the task is recognized as FAILED"，L96-105；wait 循环 L283-296 `if (pendingError != null) break;` + `throwPendingError()`）——本应通过 `pendingError` 上抛使任务 FAILED。ReplayEngine 的失败路径完全绕开了该机制，且 catch 块注释声称 "cancelling source so the job fails loudly" 与实际语义（graceful EOS）相悖。

**风险**：F9（异步异常静默吞掉致状态不一致）+ F2（失败路径半个成功无恢复语义）：回放中途失败后作业呈现"成功跑完"，产出部分结果且无任何终态异常；S1 场景作为 REPLAYABLE/exactly-once 回归锚点，其失败语义失真会掩盖同类生产 source 实现中的反模式（log + cancel = 假装 EOS）。

**建议**：失败分支不应用 cancel 收尾——将异常写入父类已有的失败通道（捕获后设 `pendingError`（该字段为 private，可在 ReplayEngine 中持有对最邻近 run 的错误回调/或父类提供 protected `failSource(Throwable)` 缝），再 cancel 以唤醒 wait 循环，使 `throwPendingError()` 上抛）；至少应把 `finally` 改为"仅成功路径 cancel、失败路径 cancel 前先标记 pendingError"。

**失效面枚举**：`ReplayableCdcSourceFunction` 全部使用面 = S1 场景 `fraud-s1-cdc.stream.xml` 的 CDC source bean、owner doc「实例锚点」S1 行、「逐源边界矩阵」debezium-cdc 行所引用的 demo 源；失效面为该唯一子类，无其他继承者（`final class ReplayEngine`），不涉及生产 `DebeziumCdcSourceFunction` 直用面（其失败路径已由 pendingError 覆盖）。

**信心水平**：行为判断 high（已核对父类 run()/cancel()/pendingError 全链路 live code）；严重度判断 medium-high（fraud-example 演示场景校准，但违反该文件自身声明的契约与 owner doc 对 S1 源 exactly-once 语义的依赖）。

**误报排除**：非"演示性简化"豁免对象——这不是演示功能性简化，而是错误路径语义与自身注释/父类机制直接矛盾的错误处理缺陷（F9 列为在范围内："吞异常导致并发状态不一致"）； InterruptedException 分支的静默处理不算缺陷（cancel 语义下的正常退出）。

**复核状态**： 已降级至 P2（主触发场景被否证——父类订阅 lambda（DebeziumCdcSourceFunction.run L260-277）已 try/catch(Exception) 将 ctx.collect 失败转入 pendingError + countDown，任务正确 FAILED，"下游瞬时故障被伪装 EOS"不成立；剩余触发面（readReplayIndex 解析失败 / writeReplayIndex offset store 故障）真实但为边缘路径；catch-all+cancel 伪装 EOS 与注释相悖、pendingError 通道未用的结构缺陷成立，不驳回。见文末「维度复核结论」）

---

### [nop-stream-orchestration-thin-C3-002] StreamScalarFunctionResolver 的 LinkedHashMap 在 register() 写者与任务线程 call() 读者之间无同步

**文件**：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamScalarFunctionResolver.java` L46-51、L59-71、L88-97

**证据片段**（L46-51、L88-97）：

```java
private final Map<String, Function<Object[], Object>> functions = new LinkedHashMap<>();

/** registers (or replaces) a scalar function under the given SQL call name. */
public void register(String fnName, Function<Object[], Object> fn) {
    functions.put(fnName, fn);
}
...
public static Object call(String fnName, Object... args) {
    StreamScalarFunctionResolver r = defaultResolver;
    if (r == null)
        throw new IllegalStateException(
                "no IScalarFunctionResolver default registered for UDF call: " + fnName);
    Function<Object[], Object> fn = r.functions.get(fnName);
    if (fn == null)
        throw new IllegalStateException("unknown UDF function: " + fnName);
    return fn.apply(args);
}
```

**严重程度**：P2

**并发场景**：`call()` 是 generated xpl 体（owner doc §3a："the print channel emits `StreamScalarFunctionResolver.call('fnName', arg0, ...)`"）在**每个流任务线程**上**逐记录**调用的运行期入口；`register()` 是 public 实例方法，owner doc 将其作为应用注册 UDF 的正式 API 且无任何"仅限启动期"守卫。同一 resolver 实例（app-beans bean == 静态 defaultResolver，注释明示"the app-beans bean and this static instance are the same object in production"）被编译期 `resolve()`/`contains()` 与运行期 `call()` 共享。

**触发时序**：
- T1（应用线程）：作业运行中调用 `resolver.register("newFn", fn)` → 对非线程安全 `LinkedHashMap` 执行 `put`（可能触发 resize/rehash）；
- T2/T3（并行子任务线程）：逐记录执行 `r.functions.get(fnName)` —— 与 T1 的结构性修改并发；
- 交错结果：轻则读到丢失更新/旧结构；重则 resize 期间 get 撕裂（JDK8+ 表结构损坏风险）；
- 变体 T1'（错误路径自伤）：T1 线程因未注册函数名走 fail-fast，构造错误消息调用 `namesList()`/`registeredNames()`（L60、L69-71 `new ArrayList<>(functions.keySet())`）迭代 keySet，与另一线程 `register()` 的 put 并发 → `ConcurrentModificationException` **掩盖原始 typed 错误**。

**现状**：无任何同步、无 copy-on-write、无启动后冻结守卫；`static volatile defaultResolver` 的读入局部变量 `r` 本身是安全的（单引用原子读），竞态点仅在实例 map。

**风险**：运行中注册 UDF（API 允许）时发生数据竞争；最低限度是错误诊断路径（namesList）在并发注册下 CME 吞掉原始 fail-fast 错误；最坏是任务线程 get 撕裂返回 null → `IllegalStateException: unknown UDF function`（裸运行时异常，违背错误处理两档策略）打断数据面。

**建议**：`functions` 换 `ConcurrentHashMap`（或 `Collections.synchronizedMap` + namesList 快照已有 copy）；若要保留插入序语义可用 CHM + `namesList()` 排序快照；或在 javadoc/xdef 层显式声明"启动期注册"并加运行期 fail-fast 守卫（声明边界必须带守卫才可豁免）。

**失效面枚举**：使用面 = ①generated xpl SELECT/WHERE/HAVING 标量 UDF 调用（print 通道，逐记录）；②`StreamSqlExprCompiler.compileFunction` 编译期解析；③错误路径 `namesList()`；④`registerAsDefault()`/`setDefault()` 静态换绑（该点本身 volatile 安全）。所有 SQL UDF 声明（`<sql>` 查询中的函数调用）为其失效面。

**信心水平**：race 可具体化为 high（代码事实明确）；实际触发概率 medium-low（常规用法是启动期注册后 Thread.start() 建立 happens-before，运行中注册属 API 允许但非常规）——按"罕见但可具体化的竞态"定 P2 而非 P1。

**误报排除**：不是"无状态单例被并发调用"（实例有状态 map）；不是编译期单线程路径（call 是运行期逐记录面）；`SqlRowAggregateOps.CACHE`/`RecordColumnAccess.GETTER_CACHE` 用 CHM，无同类问题（C3-006 仅涉及其 compute 回调）。

**复核状态**： 已保留（P2——register 为 owner doc nop-stream-sql.md L47 记载的正式 API 且无启动期守卫，call() 为生成 xpl 体逐记录入口，namesList CME 变体成立；P2 口径相符。见文末「维度复核结论」）

---

### [nop-stream-orchestration-thin-C3-003] side-input"冻结快照"未做防御性冻结——共享可变 map 直通运行期任务线程（F1 快照撕裂形态）

**文件**：`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java` L1520-1550（materializeSideInputs）；`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/functions/XplFunctionSupport.java` L35-52；`nop-kernel/nop-core/src/main/java/io/nop/core/lang/eval/EvalScopeImpl.java` L39-46（子作用域直接引用传入 map）

**证据片段**（StreamModelDslBuilder.java L1534-1550）：

```java
Map<String, Map<String, Object>> snapshotsByTarget = new LinkedHashMap<>();
for (StreamSideInputModel si : model.getSideInputs()) {
    Supplier<Map<String, Object>> supplier =
            beanResolver().resolve(si.getFrom(), Supplier.class);
    Map<String, Object> snapshot = supplier.get();
    if (snapshot == null) { ...typed error... }
    snapshotsByTarget.computeIfAbsent(si.getTo(), k -> new LinkedHashMap<>())
            .put(si.getId(), snapshot);
}
for (Map.Entry<String, Map<String, Object>> entry : snapshotsByTarget.entrySet()) {
    sideInputScopes.put(entry.getKey(),
            Collections.singletonMap("sideInputs", entry.getValue()));
}
```

**严重程度**：P2

**并发场景**：owner doc（用户指南 WI11 节）声明 "builder 构建期调用 `get()` 恰一次并**冻结**快照（低频更新=重启作业刷新，bean 运行期不再被调用）"。但冻结**无任何机制保证**：①`supplier.get()` 返回的 map 未做 `Collections.unmodifiableMap` 包装、未做防御性拷贝；②`Collections.singletonMap` 不可变只及外层键容器，其 value（builder 内层 `LinkedHashMap` 与用户 Supplier 返回的 map）仍然可变；③该 map 经 `XplMapFunction.bindings` → `XplFunctionSupport.newCallScope(bindings)` → `EvalScopeImpl(parent, childVars=bindings, ...)` 直接成为子作用域变量表（`this.variables = variables == null ? new HashMap<>() : variables`——传非 null 即原样引用），被所有并行子任务拷贝/线程共享。

**触发时序**：
- T0（构建线程）：`supplier.get()` 拿到应用字典 bean 的**内部 map 引用**（Supplier 返回自己维护的 HashMap 是最自然实现），装入 bindings；
- T1/T2（enrich 算子两个并行任务线程）：逐记录执行 xpl 体，经 `sideInputs['<id>']` 只读访问该 map；
- T3（应用侧字典刷新线程，或任何持有同引用的代码）：对同一 map 执行 `put`/`clear`（bean "运行期不再被调用"只是调用纪律，bean 内部线程不受约束）——与 T1/T2 的并发读无同步；
- 交错结果：HashMap 扩容期读撕裂/丢条目（富化值静默变 null → 命中 `RecordColumnAccess` 的 typed 错误或产出错误富化值）；xpl 体若写 `sideInputs['x']=...` 则直接对共享 map 并发写。
- 变体：xpl 体对绑定变量赋值（`sideInputs = ...` 顶层重绑）落在不可变 singletonMap 上会抛 UnsupportedOperationException（响亮，可接受）；但对**绑定值**（内层 map）的写完全无守卫。

**现状**：快照冻结 = 纯 javadoc/owner doc 纪律，无 unmodifiable 包装、无 fail-fast 守卫、无运行期变更检测。对照审计纪律 5："owner doc 明文声明且**带 fail-fast 守卫**的边界不是缺陷"——本边界已声明但**无守卫**，故立案。

**风险**：F1（异步快照持有活引用→快照撕裂）的实现形态：声明"冻结"的快照实际持有应用侧活引用，跨线程并发读写下富化语义静默失真；影响面随 sideInput 使用（WI11 声明面、E11 富化场景）放大。

**建议**：`materializeSideInputs` 对 `snapshot` 做防御性处理——至少 `Collections.unmodifiableMap(new LinkedHashMap<>(snapshot))`（浅拷贝阻断结构变更；值级深冻结可分层裁定）；并把"Supplier 不得保留返回引用"写为 validateDag 可检的声明纪律或在 javadoc fail-fast 化。

**失效面枚举**：`<sideInputs>` 全部声明面（owner doc 表 A "sideInputs 有限维表注入（WI11）"行）= `XplMapFunction`/`XplFilterFunction`/`XplFlatMapFunction` 的 bindings 路径（三个包装器均接受 bindings）→ `XplFunctionSupport.newCallScope(Map)` → `EvalScopeImpl` 子作用域；受影响场景 = WI11 全部 12 钉用例依赖的 sideInput 富化管线。

**信心水平**：机制缺失 high（代码事实）；触发概率 medium（需要应用侧 Supplier 保留引用或 xpl 体写绑定——两者均未被框架禁止）。

**误报排除**：`ROOT_SCOPE` 本身经核对为安全（`EvalScopeImpl.setLocalValue` 只写子作用域自身 map、父作用域从不被写入，L194-201；ROOT_SCOPE 为 static final 类初始化安全发布，变量表恒空只读）——本条不针对 ROOT_SCOPE，只针对 bindings 值 map 的可变性。

**复核状态**： 已保留（P2——owner doc L175 明文"builder 构建期调用 get() 恰一次并冻结快照"，冻结责任在 builder 而代码无 unmodifiable/防御拷贝，"声明无守卫"立案口径成立；bindings→newCallScope→EvalScopeImpl 原样引用链已证实。见文末「维度复核结论」）

---

### [nop-stream-orchestration-thin-C3-004] ReplaySubscription.onCancelTasks 为非线程安全 ArrayList，ICancellable 契约允许跨线程 append/remove 与 cancel 并发（理论风险，未具体化）

**文件**：`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java` L133-163

**证据片段**（L133-163）：

```java
private final class ReplaySubscription implements ICancellable {
    private final List<Consumer<String>> onCancelTasks = new ArrayList<>();
    @Override
    public boolean isCancelled() { return cancelled; }
    @Override
    public void cancel(String reason) {
        ReplayEngine.this.cancelled = true;
        for (Consumer<String> task : onCancelTasks) {
            task.accept(reason);
        }
    }
    @Override
    public void appendOnCancel(Consumer<String> task) {
        onCancelTasks.add(task);
    }
    @Override
    public void removeOnCancel(Consumer<String> task) {
        onCancelTasks.remove(task);
    }
}
```

**严重程度**：P2（理论风险，未具体化）

**并发场景**：`cancel(String reason)` 由父类 `DebeziumCdcSourceFunction.cancel()`（任务取消线程）与 `run()` finally 清理路径（任务线程）调用；`appendOnCancel`/`removeOnCancel` 按 `ICancellable` 契约可由任意线程调用（平台内既有调用先例：`PulsarMessageService` L593 `this.appendOnCancel(this::doOnCancel)`）。裸 ArrayList 的"迭代 vs 结构修改"跨线程并发无同步。

**触发时序**：**当前代码库内无任何生产调用方向本 subscription 注册 onCancel 回调**（基类 `DebeziumMessageSource.appendOnCancel` 为 no-op，L237-239；父类 cancel/清理路径只调 `subscription.cancel()`），因此写不出当下可复现的 T1/T2 交错——只能给出契约面交错：T1 调 `appendOnCancel(task)` 同时 T2 进入 `cancel(reason)` 迭代列表 → CME 或漏执行 cancel 任务。按审计纪律 3（写不出具体触发时序的一律降 P2 并标注），定为 P2 理论风险。

**现状**：基类同接口的 Pulsar 实现使用 no-op 规避了该面；本实现实现了真实列表却无同步，属对契约面的潜伏违约（一旦未来接线方按 Pulsar 先例注册回调即触发）。

**风险**：低概率 CME 出现在取消路径（本就罕见的路径）；更隐蔽的是漏执行已注册 cancel 任务——若该任务承担资源释放（offset flush 等）即成取消路径资源泄漏（P1 族的前置形态，当前未实现故不升级）。

**建议**：`onCancelTasks` 换 `CopyOnWriteArrayList`（读多写少、cancel 迭代语义不变）；或同步块 + 快照迭代。

**失效面枚举**：`ReplaySubscription`（本文件唯一实例化点 L130）；失效触发前置 = 未来任何对 `subscribe(...)` 返回值的 `appendOnCancel` 调用；枚举留空账：现调用面 = 父类 `cancel()` L336-339、`truncateForDrain()` L354-358、`run()` finally L299-306，三者均只调 `cancel()`。

**信心水平**：代码事实 high；现实触发 low（无现存写者）。理论风险标注适用。

**误报排除**：`cancelled` 旗标本身 volatile（L116）无可见性问题；`stop()`（L213-216）synchronized 与 `cancel(String)` 写同一 volatile 旗标，无锁序问题；不与 C3-001 重复计（本条只针对列表容器）。

**复核状态**： 已保留（P2，理论——裸 ArrayList 属实；基类 Subscription appendOnCancel 确为 no-op（DebeziumMessageSource L237-239）、Pulsar 先例 L593 属实、现无生产写者，理论级标注正确。见文末「维度复核结论」）

---

### [nop-stream-orchestration-thin-C3-005] RecordColumnAccess.GETTER_CACHE 的 computeIfAbsent 回调内做反射内省——CHM bin 锁内触发类加载的锁倒置死锁隐患（理论风险，未具体化）

**文件**：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/RecordColumnAccess.java` L36-37、L82-111

**证据片段**（L82-111）：

```java
private static java.lang.reflect.Method getterFor(Class<?> clazz, String column) {
    Map<String, java.lang.reflect.Method> getters =
            GETTER_CACHE.computeIfAbsent(clazz, RecordColumnAccess::collectGetters);
    return getters.get(column);
}

private static Map<String, java.lang.reflect.Method> collectGetters(Class<?> clazz) {
    Map<String, java.lang.reflect.Method> result = new java.util.HashMap<>();
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            ...
```

**严重程度**：P2（理论风险，未具体化）

**并发场景**：`get()` 是 bean 形态记录在运行期逐记录的列访问路径（非 Map 记录走 `getterFor`），多并行任务线程并发首触不同 record 类时同时进入 `GETTER_CACHE.computeIfAbsent`。`collectGetters` 回调内 `getDeclaredMethods()` 可触发**参数/返回类型的懒加载**——在持有 ConcurrentHashMap bin 锁的状态下申请类加载锁，与"持有类加载锁、随后首触另一个 getter 缓存桶"的线程形成经典锁倒置（JDK-8161372 同族形态：lambda 内类加载 + CHM）。

**触发时序**：需要 T1 在 bin 锁内完成类型 T 加载、T2 持有 T 的初始化锁并首触 bin 相邻键——在本引擎的执行模型（bean 记录类一般在作业启动前已加载、经序列化反序列化产生）下**无法给出具体可复现交错**，按纪律 3 降 P2 并标注理论风险。

**现状**：缓存本体 ConcurrentHashMap 原子性正确（并发首触同 key 只算一次）；仅回调副作用（类加载）置于锁内构成隐患。

**风险**：罕见死锁挂起两个任务线程（作业卡死无失败态）；或极端情况下 computeIfAbsent 递归自计（同 key 回调内再查同缓存——本回调不含此形态，排除）。

**建议**：两段式消除锁内副作用——先 `get`，miss 时在锁外 `collectGetters(clazz)` 再 `putIfAbsent`（允许重复计算，结果幂等）；该形态同时消解锁倒置与 bin 锁内重活。

**失效面枚举**：所有 bean 形态记录列访问（`RecordColumnAccess.get` 的非 Map 分支）= SQL 编译产物中的列读取求值面（WI9 `StreamRecordEvaluator` 列编译、`StreamSqlAggregation` 参数求值）；Map 形态记录不受影响。

**信心水平**：机制 hazard high（模式公认）；本代码库内可触发性 low。理论风险标注适用。

**误报排除**：不否缓存本身（CHM 用法正确）；不否"有界"（按 Class 键，类集合有界）；仅否回调副作用位置。

**复核状态**： 已保留（P2，理论——computeIfAbsent 锁内反射内省模式属实（JDK-8161372 同族）；本引擎执行模型下无法给出具体交错，理论级标注适用。见文末「维度复核结论」）

---

### [nop-stream-orchestration-thin-C3-006] SqlRowAggregateOps.CACHE 进程级静态缓存无上界无淘汰——动态编译 SQL 的服务形态下缓慢累积

**文件**：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/SqlRowAggregateOps.java` L27-38

**证据片段**（L27-38）：

```java
private static final ConcurrentHashMap<String, SqlRowCompositeFunction> CACHE =
        new ConcurrentHashMap<>();

private SqlRowCompositeFunction function(String specJson) {
    return CACHE.computeIfAbsent(specJson, s -> {
        SqlRowAggregateSpec spec = SqlRowAggregateSpec.fromJson(s);
        return StreamSqlAggregations.buildComposite(spec);
    });
}
```

**严重程度**：P3（优化候选）

**并发场景**：`function(specJson)` 是 continuous GROUP BY 生成 xpl 体（begin/merge/toRow）逐记录调用的运行期入口；CHM 保证并发首触只编译一次，无竞态。问题在生命周期：key 为 spec JSON 全文，静态强引用永不清除。javadoc 自述"per compiled pipeline 一条"的包络只在**管线数量有限**的部署下成立。

**触发时序**：非交错竞态而是累积型：一个长期运行的服务进程（如平台化的 `<sql>`/`<sqlTransform>` 编译服务，每租户/每查询 JSON 参数不同）反复编译新管线 → 每个 distinct spec JSON 永久占一条（key JSON 全文 + composite 函数对象 + 其闭包的 group-key evaluators）→ 无淘汰路径。

**现状**：已声明"effectively unbounded by one entry per pipeline"——声明了假设但未声明拒绝条件，也无 size 上限。

**风险**：动态多租户编译场景下缓慢老年代增长；非包络内必然 OOM（静态管线部署完全无碍），故 P3。

**建议**：若确有动态编译服务形态，换有界缓存（平台 `io.nop.commons.cache` 或简单 LRU）；否则在 javadoc 显式声明"仅适用于管线集合有限的部署"即足以闭合。

**失效面枚举**：composite/sql-row-agg 聚合声明面（WI8c/WI9/WI17 的 `composite` 与 `SqlRowAggregateSpec.FN_ID` 两条解析路径，见 `StreamAggregatorFunctionResolver.resolve` L44-58）。

**信心水平**：high（事实明确）；严重度按 P3 校准（现有部署形态皆静态管线）。

**误报排除**：`StreamSqlAggregations.BUILTINS`、`StreamSqlExprCompiler.MANAGED_TYPE_BY_NAME` 均为 static final 固定闭集，无累积面。

**复核状态**： 已保留（P3——静态无界缓存事实成立，javadoc 自认"effectively unbounded"；现有部署形态皆静态管线，优化候选定级正确。见文末「维度复核结论」）

---

### [nop-stream-orchestration-thin-C3-007] ReplayEngine 取消无中断响应点——cancel 后 emitter 线程仍可发射至多一条事件且 sleep 不被打断（有界幽灵任务）

**文件**：`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java` L125-131、L147-152、L165-190、L213-216

**证据片段**（L125-131、L168-180）：

```java
@Override
public ICancellable subscribe(Consumer<ChangeEvent> action) {
    Thread emitter = new Thread(() -> runReplay(action), "fraud-s1-replay-cdc");
    emitter.setDaemon(true);
    emitter.start();
    return new ReplaySubscription();
}
...
for (int i = (int) resumeIndex; i < events.size(); i++) {
    if (cancelled) { break; }
    action.accept(events.get(i));
    writeReplayIndex(i + 1L);
    if (emitDelayMs > 0) {
        Thread.sleep(emitDelayMs);
    }
}
```

**严重程度**：P3

**并发场景**：`cancel(String)`/`stop()` 仅置 `volatile cancelled`，不 `emitter.interrupt()`。取消响应点只有两处循环内检查 + `Thread.sleep` 的（不可达）中断——因为无人中断。幽灵窗口 = 一次 `action.accept` 全程 + 至多 `emitDelayMs`/`finishLingerMs` 的 sleep（fixture 有界值）。

**触发时序**：T2（任务取消线程）执行 `subscription.cancel(...)` 置位时，T1（emitter）恰在 `action.accept(events.get(i))` 内部 → T1 完成本条发射（`ctx.collect` 在任务已取消后触发父类 consumer 的 pendingError 记录或协作中止异常——又被 C3-001 的 catch(Exception) 吞掉）+ `writeReplayIndex(i+1)` 推进 offset + 继续 sleep 到下一检查点才退出。

**现状**：fixture 有界（事件数有限、delay 值有限）、daemon 线程不阻断 JVM 退出；对 demo 场景影响可忽略，故按演示校准定 P3 而非 P1。

**风险**：取消延迟有界但非即时；取消后的最后一条事件消费了下游资源且其 offset 已推进（若该作业随后被恢复，恢复点越过这条未确认记录——在 C3-001 的失败伪装语义叠加下不可诊断）。

**建议**：`ReplaySubscription.cancel` 持有 emitter 引用并 `interrupt()`；`runReplay` 已有 InterruptedException 分支可正确收尾。或将 emitter 线程引用纳入 subscription 生命周期。

**失效面枚举**：S1 CDC 源的取消路径（REST stop、作业 cancel、DRAIN 后的 subscription 清理）；无其他订阅者。

**信心水平**：high（行为确定）；影响按 demo 校准 low。

**误报排除**：非"幽灵任务无界"（fixture 上界明确）；`Thread.sleep` 在 `InterruptedException` 分支有恢复中断标志的正确写法（L181-182）。

**复核状态**： 已保留（P3——无 emitter 引用、无 interrupt、volatile 旗标取消均证实；fixture 有界 + daemon，demo 校准 P3 正确。注：发现内"协作中止异常被 C3-001 的 catch 吞掉"一节实际先落入父类 lambda（同 C3-001 否证方向），不影响本条 P3 结论。见文末「维度复核结论」）

---

## 覆盖度与残留边界

**核对为安全、不立案的关键面**（防止后续轮次重复立案）：

1. **XplFunctionSupport.ROOT_SCOPE 共享静态作用域**：7 个包装器（XplMap/Filter/FlatMap/Sink/ReduceFunction、EvalActionKeySelector、JoinSideTagFunction）每次调用经 `ROOT_SCOPE.newChildScope(...)` 取子作用域。核对 nop-core `EvalScopeImpl` live code：`setLocalValue` 只写子作用域自身 `variables`（L194-201），求值沿父链**只读**（`getValue`/`containsValue` L146-167），ROOT_SCOPE 的变量表恒空且 static final 安全发布 → 无跨线程写共享面。C3-003 针对的仅是 bindings **值** map 的可变性，与本条正交。
2. **DirectoryFileSourceFunction 游标推进次序（collect 后 advanceCursor）**：初看疑似 F1 快照撕裂（snapshot 落在 collect 与 advanceCursor 之间 → 恢复重复发射）。核对 `StreamSourceOperator.collect()`（nop-stream-core L263-272）：checkpoint mail 的 `drainControlMails()` 在 `output.collect(element)` **之前**执行——snapshotState/emitBarrier 总是在"当前记录尚未发出、游标尚未推进"的一致点上运行，恢复后当前记录重发且不属于已提交 epoch，恰好满足 exactly-once。**collect 后推进游标在该 runtime 语义下是安全次序**，不立案。`synchronized(cursors)` 的必要性来自多子任务共享 UDF 实例（snapshot 在非读者子任务线程上执行），已正确覆盖 map 级一致性。
3. **DirectoryFileSourceFunction.runEntered CAS 守卫**：第二..N 子任务静默 return（零输出 EOS）——javadoc 明文声明的"whichever subtask wins the read race"边界且带 CAS 守卫，按纪律 5 不立案（观察项：静默无日志，生产化时应留 skip 指标）。
4. **XplSourceFunction.cancel() 不影响 run()**：javadoc 明文声明（body 经 ctx.isCancelled()/collect 协作中止退出）+ owner doc「内联 xpl source 的取消模式」明文声明轮询契约；`StreamSourceOperator.isCancelled` 接 mailbox 取消旗标。已声明边界且带守卫（协作中止异常），不立案。**不轮询也不 collect 的 xpl source 体将无法取消**——这是声明内的用户责任面。
5. **无 double-subscribe**：父类 `run()` 由 `runEntered` CAS 守卫单次进入，`createMessageSource`+`subscribe` 每次 run 各一次；`ReplayEngine.subscribe` 每次调用新建线程，无重复发射通道。
6. **UserHistoryEnricher / UserTransactionHistory**：per-key 单线程处理语义下 read-modify-write（count/total 两状态对）无并发窗口；中途失败的半个更新由 checkpoint 回滚收敛。`lastCityState` 懒初始化无同步但子任务实例不跨线程共享。非 C3 立案对象（唯 note：`IllegalStateException` 裸抛违背错误处理两档策略，属 C8/风格维度）。
7. **DemoKeyedStateStore / FraudDetectionDemo / MockTransactionGenerator / 4 个 Pattern 类 / 2 个 Aggregate 类**：单线程 demo main 或纯函数/POJO；DemoKeyedStateStore 的 disconnected-state 简化已由其 javadoc 显式声明 DEMO-ONLY——演示性简化，按校准不立案。
8. **build 期类**（StreamModelDslBuilder、AdvancedTransforms、StreamSchemaRegistry、StreamModelLinter、StreamConfValidator、StreamSqlCompiler、StreamSqlExprCompiler、EqlExprSupport、StreamAggregatorFunctionResolver、GlobalBeanFunctionResolver、BeanContainerFunctionResolver、InMemoryBeanFunctionResolver）：无静态可变状态、单线程构建生命周期内使用；`StreamSqlAggregations.BUILTINS`/`MANAGED_TYPE_BY_NAME` 为 static final 闭集。
9. **平台设施对照**：三模块无线程池/阻塞队列使用（唯一线程创建点已审），不存在绕过 `io.nop.commons.concurrent`（IThreadPoolExecutor/IBlockingQueue）设施的手写轮子；上下文传播面——流任务线程不依赖 IContext ThreadLocal（xpl 作用域显式传参、BeanProvider 走全局 BeanContainer），未发现异步边界丢上下文路径。

**残留边界（本单元不可闭合、移交后续维度/单元）**：
- checkpoint 调度与 barrier 对齐、2PC sink 提交、作业事件总线、分布式恢复均在 `nop-stream-runtime`（不在本单元）——C3-001 的修复缝（pendingError 暴露为 protected）如需动父类则涉及 nop-stream-connector-debezium 单元。
- `EvalScopeImpl`/`EvalExprProvider` 属 nop-core 框架核心（protected area），本审计仅以其 live code 为据判定使用方安全，不审其内部。
- C3 维度三模块内**无 CompletableFuture/超时 future/定时器**——取消/超时语义的引擎面（checkpointTimeout、barrierAlignmentTimeout 等）不在本单元代码内，仅经 owner doc 确认声明面存在。

**发现统计**：P1 × 1（C3-001），P2 × 4（C3-002、C3-003、C3-004 理论、C3-005 理论），P3 × 2（C3-006、C3-007）。缺陷族命中：F9（C3-001）、F1（C3-003 快照撕裂形态）、F2（C3-001 半个成功）；F3（修实例不修类别）在本单元无失效面枚举留空的反例（各发现的失效面均已枚举）。

---

## 维度复核结论（独立复核子代理）

- 复核子代理：独立复核 subagent（与初审者不同角色；职责为逐条验证 + 保留/降级/驳回，不补充新发现）
- 复核日期：2026-10-14；分支：concurrency-audit-remediation；仓库：nop-entropy-concurrency-audit
- 复核输入：本文件（C3-001~007）、`C3-round2.md`（C3-008~009）、`C3-round3.md`（0 新增 + 终止声明），全文完整读取
- 复核方法：逐条重读单元内 live code 核对文件/行号/证据；C3-008/C3-009 追至跨模块载体逐一验证时序链（nop-stream-core `operators/StreamSourceOperator`、`operators/ProcessOperator`、`operators/AbstractUdfStreamOperator`、`operators/AbstractStreamOperator`、`jobgraph/OperatorChain`、`datastream/KeyedStreamImpl`、`execution/task/TaskExecutor`、`common/functions/AbstractRichFunction`、`util/FunctionUtils`；nop-stream-runtime `execution/SupervisionLoop`、`execution/GraphModelCheckpointExecutor`；nop-message-debezium `DebeziumMessageSource`、`engine/NopStreamOffsetBackingStore`）；现状标注对照同 campaign WI 1.1（2026-10-14-0130）与 WI 1.2（2026-10-14-0330）审计目录、`ai-dev/audits/cra-index.md` 及 git log（基线 b3a9c76bed..HEAD）
- 引用偏差登记（不影响证据效力）：初审引用路径 `io/nop/stream/core/operator/`（单数），实际包为 `io/nop/stream/core/operators/`（复数）、`execution/task/`；所引行号与内容均与 live 一致

### 逐条判定表

| 编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C3-001 | P1 | **降级 → P2** | 主触发被 live code 否证：父类订阅 lambda（DebeziumCdcSourceFunction.run L260-277）已 try/catch(Exception) 将 ctx.collect 失败转入 pendingError + completionLatch.countDown，任务正确 FAILED——"下游瞬时故障被伪装 EOS"时序不成立；剩余触发面（readReplayIndex NumberFormatException / writeReplayIndex offset store 故障）真实存在但为边缘路径（内存 CHM store、索引状态自产自销），按本维度自身校准先例（C3-002"罕见但可具体化→P2"）定 P2；catch-all+cancel 伪装 EOS、注释"fails loudly"与实际语义相悖、pendingError 通道可用而未用的结构缺陷成立，不驳回 | 新发现（WI 1.2 两次显式登记本类为"范围外，登记供该模块审计引用"，无同发现先例；基线后唯一 stream 提交 8dcfa4cd0c 为 kafka-2pc，不触及本单元） |
| C3-002 | P2 | 保留 | register 为 owner doc（nop-stream-sql.md L47）记载的正式 API 且无启动期守卫，call() 为生成 xpl 体逐记录入口，namesList CME 掩盖原始 fail-fast 错误变体成立；P2 口径相符 | 新发现 |
| C3-003 | P2 | 保留 | owner doc L175 明文"builder 构建期调用 get() 恰一次并**冻结**快照"——冻结责任文义落在 builder，代码无 unmodifiable/防御拷贝（"声明无守卫"立案口径成立）；bindings→newCallScope→EvalScopeImpl 原样成为子作用域变量表已证实 | 新发现 |
| C3-004 | P2（理论） | 保留 | 裸 ArrayList 属实；基类 Subscription appendOnCancel 确为 no-op（DebeziumMessageSource L237-239）、Pulsar 先例（PulsarMessageService L593）属实、当前无生产写者——理论级标注正确 | 新发现 |
| C3-005 | P2（理论） | 保留 | computeIfAbsent 锁内反射内省模式属实（JDK-8161372 同族）；本引擎执行模型下无法给出具体交错，理论级标注适用 | 新发现 |
| C3-006 | P3 | 保留 | 静态无界缓存事实成立，javadoc 自认"effectively unbounded by one entry per pipeline"；现有部署形态皆静态管线，优化候选定级正确 | 新发现 |
| C3-007 | P3 | 保留 | 无 emitter 引用、无 interrupt、volatile 旗标取消均证实；fixture 有界 + daemon 线程，demo 校准 P3 正确（发现内"协作中止异常被 C3-001 catch 吞掉"一节实际先落入父类 lambda，与 C3-001 同一否证方向，不影响 P3 结论） | 新发现 |
| C3-008 | P1 | 保留（机制修正） | wipe 可达性三要素全部证实：①共享实例（copyForSubtask() 对非 SubtaskShardedSourceFunction 直传同一引用，DirectoryFileSourceFunction 未实现分片接口）；②Phase-3 rebuild→restore→`executor.submitTask` 异步提交→循环继续（SupervisionLoop L569-575 + TaskExecutor L206-219）；③非空守卫只防 restore-vs-restore（L275-280）；初始部署路径 restore 先于 buildTasks/submitAndRun 核实安全。但发现内"同 run 内下一个 currentCursor(f1) 重读→重发 [cp,5000)"机制不成立——currentCursor 每 emitRemaining 仅读一次（L152），发射由本地 offset 驱动，in-flight 文件游标被 wipe 后由下一次 advanceCursor 立即自愈；实际违约路径（复核重建）：wipe 落在 task-0 已推进/已完成文件上且该文件本 run 不再被推进→此后每个 checkpoint 携带落后于同 epoch 下游进度的源位点→恢复重放已反映窗口（键控聚合重复计入）——REPLAYABLE/"deterministic under parallelism rescale"违约结论不变；补充前置（初审未析）：task-1 须在重启时非 COMPLETED（Phase-3 F1 分支 L556-568 跳过已完成任务的 rebuild+restore），CAS 落败者启动后微秒级终态，现实窗口要求 task-1 尚未起跑（线程池排队/饥饿可达，WI 1.1 C8-013 已证池饱和形态存在）——仍满足"罕见但可具体化竞态 + 正确性违约"→ 维持 P1 | 新发现（WI 1.1 对 SupervisionLoop 仅涉 C4/C8 维度面，无 restore-vs-run 游标面先例） |
| C3-009 | P1 | 保留 | 全链路证实且强于发现陈述：ProcessOperator.copyForSubtask 共享 UDF（L42-44）、AbstractUdfStreamOperator.open 无同步向共享实例覆写 RuntimeContext、ProcessOperator.open 装入本子任务 keyedStateBackend（L92-102）、getRuntimeContext().getKeyedStateStore() 为平台键控状态唯一合法通道均核实；且即使两次 open 完全顺序无交错，共享实例终态仍收敛 last-writer-wins——subtask-0 以 subtask-1 的 backend 处理记录，属系统性缺陷而非纯竞态；shipped 场景 parallelism="1"（fraud-s1-cdc/fraud-s2-file XML 实核）不触发，单元校准 P1 正确 | 新发现（WI 1.1 全目录零 ProcessOperator/RuntimeContext 覆盖，根因载体 core 单元未登记） |

### 复核方法说明：对每条发现的反驳自检

- C3-001：被审计方最强反驳 = "collect 失败已由父类 R5-CON-02 通道正确处理"——**反驳成立**（降级依据）；次级反驳 = "offset store 为内存 CHM 不会失败"——**部分成立**（触发面收窄但 catch-all 结构缺陷无可反驳）。
- C3-008：被审计方最强反驳 = "task-0 不会重读 currentCursor，无重复发射"——**反驳成立**（发现内机制描述错误）；但反证复核自身的重建路径后，"检查点携带滞后源位点→恢复重复计入"无法被反驳，且 wipe 窗口比发现陈述的更宽（覆盖余下整个运行期）。
- C3-009：被审计方最强反驳 = "open 交错是窄窗口竞态"——**反驳失败且反向加强**（顺序 open 亦系统性收敛）。
- C3-002~007：反驳自检均未推翻事实面与定级口径。

### 不确定项（显式列出）

1. C3-008 修正机制的"重复计入"幅度依赖下游 epoch 回滚（materialization replay 一致切点）与源重放的合成结果——复核确认源位点滞后必致恢复窗口重叠，但重复条数上界未做引擎级仿真验证。
2. C3-005 锁倒置在 JDK 25 实现下 getDeclaredMethods 是否实际触发参数/返回类型懒加载属 JVM 实现相关——理论标注维持，不升级亦不驳回。
3. C3-001 降级后与 C3-007 是否合并处置（同文件同取消/失败语义簇）属 7.1 批量修复裁决事项，复核不预设。

### 高风险项（建议子项复核）

- **C3-008**：复核修正后的违约路径（checkpoint 源位点滞后→恢复重复计入）建议由 nop-stream-runtime 单元审计对 restartRegion Phase-3 编排做子项级确认（编排侧修复"全部 rebuild+restore 完成后再统一 submit"可同时闭合 CDC offset 兄弟面）。
- **C3-009**：根因载体 ProcessOperator/AbstractUdfStreamOperator 位于 nop-stream-core（WI 1.1 已收口但未覆盖此面），建议移交登记时以子项复核确认"所有含状态 RichFunction UDF"的失效面外溢范围。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C3-008 | P1 | nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/DirectoryFileSourceFunction.java | region 重启 Phase-3 流水线下兄弟 initializeState REPLACE 清零已推进游标（复核修正机制：违约路径为后续 checkpoint 携带滞后源位点→恢复重放已反映窗口；前置：task-1 重启时非 COMPLETED） | 新发现 |
| C3-009 | P1 | nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/UserHistoryEnricher.java | 共享 UDF × 各子任务 open() 覆写共享 RuntimeContext→并行 ≥2 键控状态系统性收敛单一 backend（顺序 open 亦然；根因载体 ProcessOperator 在 nop-stream-core，移交） | 新发现 |
| C3-001 | P2（降级） | nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java | runReplay catch-all+cancel 将 emitter 线程失败（offset 索引解析/store 故障）伪装为正常 EOS，绕开父类可用而未用的 pendingError 通道；主触发（collect 异常）已被父类 lambda 覆盖故降级 | 新发现 |
| C3-002 | P2 | nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamScalarFunctionResolver.java | LinkedHashMap register 写者与任务线程 call() 读者无同步；运行期注册竞态 + namesList CME 掩盖原始 fail-fast 错误 | 新发现 |
| C3-003 | P2 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java（+ builder/functions/XplFunctionSupport.java） | side-input"冻结快照"无防御性冻结，Supplier 活引用直通运行期多任务线程（F1 撕裂形态；owner doc 声明 builder 冻结但无守卫） | 新发现 |
| C3-004 | P2（理论） | nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java | ReplaySubscription.onCancelTasks 裸 ArrayList 违反 ICancellable 跨线程契约（现无生产写者，理论风险） | 新发现 |
| C3-005 | P2（理论） | nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/RecordColumnAccess.java | GETTER_CACHE computeIfAbsent 锁内反射内省的类加载锁倒置隐患（理论风险） | 新发现 |
| C3-006 | P3 | nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/SqlRowAggregateOps.java | 进程级静态 CACHE 无上界无淘汰，动态编译服务形态下缓慢累积 | 新发现 |
| C3-007 | P3 | nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java | ReplayEngine 取消无 interrupt 响应点（有界幽灵窗口 + offset 越过未确认记录） | 新发现 |

**复核统计**：9 条全裁 —— 保留 8（P1 × 2、P2 × 4、P3 × 2）、降级 1（C3-001 P1→P2）、驳回 0。最终口径：P1 × 2、P2 × 5（含理论标注 2 项）、P3 × 2。现状标注：9 条全部为**新发现**（WI 1.1/WI 1.2 无同发现登记；WI 1.2 明确将 fraud-example 登记为"范围外，供本模块审计引用"；基线 b3a9c76bed 之后无触及本单元的提交，无已修复项）。
