# C1 共享可变状态与可见性 — 第 1 轮初审（盲审）

- 审计单元：`nop-stream-orchestration-thin`（nop-stream-flow / nop-stream-sql / nop-stream-fraud-example 三个模块的 src/main/java，非 `_` 前缀文件）
- 维度：C1 共享可变状态与可见性（其他维度的基盘盘点）
- 仓库根：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 日期：2026-10-14；审计代理：C1 盲审子代理（未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何既有文件）
- 基线：`_tmp/cra-baseline-nop-stream-orchestration-thin.txt`（98 files / 13,792 lines，与实盘逐一核对一致）

## 检查范围说明

| 模块 | 文件/行数（实测） | 本维度重点 |
|---|---|---|
| nop-stream-flow | 60 files / 6,202 lines | singleton bean 实例字段；静态共享 eval 根作用域（XplFunctionSupport.ROOT_SCOPE）；side-input 快照绑定面；builder 对解析模型的就地改写；InMemory resolver 的注册表视图 |
| nop-stream-sql | 17 files / 4,334 lines | static volatile default resolver + 其非线程安全 functions 表（编译期与运行期两条查找路径）；SqlRowAggregateOps/RecordColumnAccess 的 CHM 缓存；static final 不可变目录（BUILTINS/MANAGED_TYPES） |
| nop-stream-fraud-example | 21 files / 3,256 lines | 跨子任务共享 UDF 的实例字段（DirectoryFileSourceFunction.runEntered/cursors、ReplayableCdcSourceFunction 内部引擎线程与订阅回调表）；bean 共享函数的惰性初始化；演示代码按误报校准 |

核查手段：全量通读三个模块 src/main/java（98 文件）；对 grep 基线的 C1（static 非终态字段 / static 集合与日期工具 / ThreadLocal）、C2（锁载体 / computeIfAbsent）、C4（线程创建）全部条目逐条落到 live code 验证；对平台语境做了四处交叉取证——(a) `EvalScopeImpl`（nop-core）确认子作用域只写自身 variables、父作用域构造后只读；(b) `OperatorChain.deepCopy` javadoc（nop-stream-core）确认 LOCAL 执行中 **user functions 跨子任务共享同一实例**（"user functions ... are shared across subtasks to preserve captured external references"），非 Shareable 算子 parallelism>1 fail-fast；(c) `StreamSourceOperator.copyForSubtask` 确认 source function 默认共享、仅 `SubtaskShardedSourceFunction` 才按子任务拷贝；(d) `GraphExecutionPlan` 确认 LOCAL 部署自同一 JobGraph 的同一批 live UDF 实例（task location "stays stable across region restarts"），即 region restart 复用共享实例。

owner doc 对照：`docs-for-ai/03-modules/nop-stream-user-guide.md`（WI11 sideInput "构建期 get() 恰一次并冻结只读快照"、UDF 声明面、Xpl source 取消模式）、`docs-for-ai/03-modules/nop-stream-sql.md`（§3a "编译期一次解析（非逐记录查找）"——该表述与声明面运行路径存在出入，见 C1-001）。

## 发现条目

### [nop-stream-orchestration-thin-C1-001] UDF 注册表为非线程安全 LinkedHashMap，且经静态 `call()` 在逐记录热路径上无同步读取

**文件**：nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamScalarFunctionResolver.java:46-51、88-97；发射点 nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/compile/SqlScalarXplPrinter.java:186-198

**证据片段**：

```java
// StreamScalarFunctionResolver.java
private static volatile StreamScalarFunctionResolver defaultResolver;   // L31
...
private final Map<String, Function<Object[], Object>> functions = new LinkedHashMap<>();  // L46

/** registers (or replaces) a scalar function under the given SQL call name. */
public void register(String fnName, Function<Object[], Object> fn) {
    functions.put(fnName, fn);                                          // L50
}

/** Runtime entry for generated xpl bodies ... */
public static Object call(String fnName, Object... args) {
    StreamScalarFunctionResolver r = defaultResolver;
    ...
    Function<Object[], Object> fn = r.functions.get(fnName);            // L93（逐记录执行）
```

```java
// SqlScalarXplPrinter.java（WI30 声明面：UDF 调用被织入逐记录执行的 xpl 体）
requiredImports.add("io.nop.stream.sql.eval.StreamScalarFunctionResolver");
sb.append("StreamScalarFunctionResolver.call('").append(fn.getName()).append("'");
```

**严重程度**：P1（错误/重试之外的常规运行路径上，可具体化的数据竞争；无任何运行时守卫）

**并发场景**：`StreamScalarFunctionResolver` 是经 app-beans/IoC 注册的 singleton（`registerAsDefault()` 把实例挂到 static volatile `defaultResolver`）。`functions` 是普通 `LinkedHashMap`。两条访问路径并发：(1) **写**——公开 API `register()`，javadoc 明说 "registers (or replaces)"，无任何 "仅限启动期" 的约束或守卫；(2) **读**——WI30 声明面（`<sql>`/`<sqlTransform>` 中的 UDF 调用）生成的 xpl 体在**每个流记录**上调用静态 `call()` → `r.functions.get(fnName)`，运行在流任务的 subtask 线程上。volatile 只保证引用可见，不约束 map 内部状态；owner doc §3a 声称 "编译期一次解析（非逐记录查找）" 仅对 `StreamSqlExprCompiler.compileFunction`（Java 捕获闭包路径，L222-251，确实一次解析）成立，声明面运行路径是逐记录查表——文档表述与 live 路径不一致（docs bug，按 AGENTS.md 应在同任务修正）。

**触发时序**：多作业长驻进程（OpsJobManager 多作业模式）。T1 = 作业 A 的 subtask 线程执行第 N 条记录，进入 `call()` L93 `r.functions.get("fee")`（LinkedHashMap 内部 hash/遍历 bucket）；T2 = 应用/运维线程为作业 B 注册新 UDF，进入 `register()` L50 `functions.put("tax", fn)` 触发 resize/rehash。交错后果：HashMap/LinkedHashMap 并发 get-put 无 happens-before，T1 可读到撕裂/陈旧桶结构，最坏返回 null 或抛异常（树化桶上可触发 ClassCastException）；若 T2 在 T1 执行 `namesList()`/`registeredNames()`（编译期 fail-fast 文案路径，L59-61/69-71 对 keySet 迭代）期间 put，直接 `ConcurrentModificationException`。put 后 get 的结果对其他线程无可见性保证——"注册成功但其他作业查不到" 也是合法结局。

**现状**：volatile 引用 + 非并发容器；无锁、无 CopyOnWrite、无生命周期守卫（不拒绝作业启动后的 register）。

**风险**：热路径数据竞争（返回错值/null/异常）+ 注册可见性违约；多作业共享 JVM 时窗口真实存在，且平台把 UDF 声明为受支持的公共 SPI 面。

**建议**：`functions` 改 `ConcurrentHashMap`（或 `Collections.synchronizedMap`+读复制）；`register()` 文档化生命周期并在运行期变更时至少 WARN；同步修正 `nop-stream-sql.md` §3a "非逐记录查找" 的表述（声明面运行路径为逐记录静态查表）。

**失效面枚举（F3 同族全查）**：本单元内 static 非终态字段仅此一处（基线 C1 三模块清单核对：flow=0、sql=defaultResolver、fraud=0）；同族读者面 = `call()`（逐记录）+ `contains/resolve/registeredNames/namesList`（编译期）；同族发射面 = SqlScalarXplPrinter 的 UDF 分支（顶层 `<sql>` 与 `<sqlTransform>` 两条声明面共用）；族外同名先例 = core `WindowedStreamImpl.setDefaultFactory`（固定 FQCN 工厂 static setter，本单元外，不在本轮立案，移交 runtime 维度轮次核对同型缺陷）。

**信心水平**：高（竞争路径与发射点均在 live code 中逐行落实）。

**误报校准**：非"无状态单例被并发调用"（有可变注册表）；非"CHM 原语本身"（这里恰是没用并发容器）；非低频路径（每记录一次）；非有界声明缓存。

**复核状态**：已保留（P1 不变——LinkedHashMap/逐记录 `call()`/无守卫公开 `register()` 及 sql.md §3a 偏差均经 live code 逐行核实；"非线程安全集合属 Java 常识"抗辩不成立：读侧是框架生成代码的逐记录入口、写侧是无生命周期约束的公开 SPI，符合"罕见但可具体化竞态"判级。现状：新发现；同族先例 core `WindowedStreamImpl.setDefaultFactory` 已由 WI 1.1 C1 清单表裁定为"启动/配置路径粗锁豁免"，本条为公开注册 API 形态，不适用该豁免）

---

### [nop-stream-orchestration-thin-C1-002] XplFunctionSupport.ROOT_SCOPE：JVM 级静态共享可变 eval 作用域，安全性依赖结构性不变量、无运行时守卫

**文件**：nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/functions/XplFunctionSupport.java:34-52

**证据片段**：

```java
/**
 * A root scope retained across invocations. ... variables bound at
 * invocation time must live in per-call child scopes.
 */
private static final IEvalScope ROOT_SCOPE = EvalExprProvider.newEvalScope();  // L34

public static IEvalScope newCallScope() {
    return ROOT_SCOPE.newChildScope();                                         // L37-39
}

public static IEvalScope newCallScope(java.util.Map<String, Object> bindings) {
    if (bindings == null || bindings.isEmpty()) {
        return ROOT_SCOPE.newChildScope();
    }
    return ROOT_SCOPE.newChildScope(bindings);                                 // L47-51
}
```

**严重程度**：P2（理论风险，当前调用面写不出具体化损坏时序；守卫缺失形态，F10 关联）

**并发场景**：LOCAL 执行中 user functions 跨子任务共享（core `OperatorChain` javadoc 明示），因此全部 9 个 xpl 包装器（XplMap/Filter/FlatMap/Source/Sink/ReduceFunction、EvalActionKeySelector、JoinSideTagFunction、SideOutputTapOperator）在**每个记录**上从多个 subtask 线程并发调用 `ROOT_SCOPE.newChildScope()`。已对照平台实现 `EvalScopeImpl`（nop-core）：`newChildScope` 仅构造新子作用域（child 自持 variables map，`inheritParentVars=true` 时对父做**只读**递归查找 `containsValue/getValue`）；`setLocalValue` 只写子作用域自身 map。因此 ROOT_SCOPE 在类初始化（static final 安全发布）之后是"事实只读"，并发只读 HashMap 是安全的——**但这一安全性没有任何机制保障**：EvalScopeImpl 的 `variables` 是普通 HashMap、`beanProvider`/`classModelLoader` 是非 volatile 可变字段，只要任何一个未来的包装器把 ROOT_SCOPE 本身当作求值作用域传入 `body.call1(null, value, ROOT_SCOPE)`（或未来 XLang 构造引入对父作用域的赋值语义），根作用域即刻成为跨线程写共享点，所有算子所有子任务互相污染绑定变量。

**触发时序**：当前 live 调用面写不出 T1/T2 损坏交错（所有调用都经 newCallScope 产生子作用域）——按审计纪律如实降 P2 标注"理论风险，未具体化"；风险形态是**不变量侵蚀型**：单点破坏（一处误用）即全局生效且无 fail-fast。

**现状**：静态共享 + 依赖 javadoc 注释约束（"variables bound at invocation time must live in per-call child scopes"）+ 9 个调用方全部自律走 newCallScope。

**风险**：未来调用面演化（新增包装器、AI 生成代码、xpl 方言扩展）静默破坏不变量；损坏表现为跨记录/跨子任务的绑定串值，极难归因。

**建议**：廉价加固任选其一——(a) `newCallScope` 改为每次 `new EvalScopeImpl()`（ ROOT_SCOPE 语义本是空根，无需共享实例）；(b) 保留共享但在 EvalScopeImpl 上为"根作用域"提供 write-guard（isRoot 时 setLocalValue 抛 IllegalStateException）。

**失效面枚举（F3 同族全查）**：经 `newCallScope` 的全部调用方（9 处，单一漏斗点=单一修复点）：XplMapFunction.map L43、XplFilterFunction.filter L42、XplFlatMapFunction.flatMap L41、XplSourceFunction.run L58、XplSinkFunction.consume L35、XplReduceFunction.reduce L36、EvalActionKeySelector.getKey L39、JoinSideTagFunction.evalKey L50/56、SideOutputTapOperator.matches L76。各处状态：全部经漏斗，无绕行者（全仓 grep `ROOT_SCOPE` 仅本类内部 3 处引用）。

**信心水平**：中高（只读不变量经平台源码逐行验证；"未来误用"为推断）。

**误报校准**：不报"静态字段即风险"——已实证当前只读；不涉平台自身实现（EvalScopeImpl 属 nop-core，仅审使用方式）。

**复核状态**：已保留（P2 不变——对 nop-core `EvalScopeImpl` 源码复核成立：`newChildScope` 子作用域自持 variables、父作用域仅 `containsValue/getValue` 只读递归、`beanProvider/classModelLoader` 确为非 volatile 可变字段（L33-34）；ROOT_SCOPE 构造后事实只读 + 9 调用方单一漏斗 + 零守卫属实，"不变量侵蚀型理论风险"合 P2 口径。现状：新发现）

---

### [nop-stream-orchestration-thin-C1-003] DirectoryFileSourceFunction 的 CAS 单读者守卫非粘性：region restart 窗口内新尝试可静默零发射完成

**文件**：nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/DirectoryFileSourceFunction.java:110-130（守卫）、63-80（共享状态声明）

**证据片段**：

```java
private final TreeMap<String, Long> cursors = new TreeMap<>();   // L68 synchronized(cursors) 守护，正确

private transient java.util.concurrent.atomic.AtomicBoolean runEntered =
        new java.util.concurrent.atomic.AtomicBoolean(false);    // L79-80

@Override
public void run(SourceContext<String> ctx) throws Exception {
    // Parallel-subtask guard: the shared UDF must be read by exactly one subtask,
    // otherwise every subtask would re-emit the whole directory (duplicates).
    if (!runEntered.compareAndSet(false, true)) {                // L114
        return;                                                  // L115 —— 落败者静默完成
    }
    try {
        for (File file : discoverFiles()) { ... emitRemaining(file, ctx); }
        ...
    } finally {
        runEntered.set(false);                                   // L128 —— 非粘性复位
    }
}
```

**严重程度**：P2（取消/重启路径上罕见但可具体化的竞态；演示模块按校准降级立案，守卫模式是生产 source 的复制模板）

**并发场景**：LOCAL 部署中同一 UDF 实例被 source vertex 的所有 subtask 线程共享，且 region restart 从同一 JobGraph 复用同一批 live 实例（core `GraphExecutionPlan` L450 注释：task location "stays stable across region restarts"；`StreamSourceOperator.copyForSubtask` 确认非分片 source function 共享实例）。守卫的意图是"同一时刻恰一个 subtask 读目录"；但 `finally` 中的无条件 `set(false)` 使守卫在"上一次运行尚未退出完毕"与"下一次运行已开始"之间只提供 CAS 瞬时判定，不提供尝试间的先后保证。

**触发时序**：T1 = attempt-1 的 subtask 线程在 L169 观察到 `cancelled=true`，从读循环返回，**尚未执行到** L128 finally；协调器随即触发 region restart（LOCAL 同进程重部署，同一共享实例）；T2 = attempt-2 线程调用 `run()`，L114 CAS 因 `runEntered` 仍为 true 而失败 → L115 直接 return → attempt-2 的 source task 以"正常完成、零记录"终结；T1 此时才执行 `set(false)`——为时已晚。后果：有界源在恢复尝试上提前 EOF，作业静默丢掉全部剩余输入（无报错、无重试信号）。

**现状**：CAS 守卫正确覆盖了并行 subtask 首入竞争（声明契约范围内），`cursors` 的锁、快照拷贝、空恢复守卫、transient 读回重建均做得规范；唯独守卫的复位时机使"取消→重启"路径存在语义缺口。

**风险**：restart 语义下静默数据丢失（半个成功、无恢复信号）；fraud-example 是文档钦点的场景样例与 gated 多 JVM 测试底座，该守卫写法会被后续 source 实现复制。

**建议**：守卫绑定尝试身份而非全局布尔——如 run() 进入时记录 attempt/generation，落败 return 前先自旋等待在跑尝试退出；或取消路径不复位、仅在**成功自然完成**时复位；或改用"锁 + 完成标志"区分"在跑"与"已跑完"。

**失效面枚举（F3 同族全查）**：本单元内共享 UDF 上的进入守卫共 1 处（runEntered）；同族共享状态 = `cursors`（synchronized，无此缺口）、`cancelled`（volatile，正确）；姊妹类 `ReplayableCdcSourceFunction` 无并行子任务守卫（其并发语义委托给生产基类 `DebeziumCdcSourceFunction`，在本单元之外，移交 runtime 轮次核对 `SubtaskShardedSourceFunction` 实现面）。

**信心水平**：中高（交错序列具体；restart 握手窗口窄，实际命中率低——故 P2 非 P1）。

**误报校准**：非"声明边界"——javadoc 只声明并行 subtask 互斥，未声明 restart 语义；非演示性简化——守卫是显式的正确性机制而非省略。

**复核状态**：已保留（P2 不变，但触发路径修正——守卫非粘性属实；惟本条命名的"LOCAL 同进程 region restart"时序经运行时源码复核**不成立**：`SupervisionLoop.restartRegion` Phase 2 `waitForTerminal`（10s fail-loud zombie 预算）严格等待旧任务到达终态，而 `SubtaskTask` 终态仅在 `inv.invoke()` 返回后才置位，即旧 attempt 的 `finally{set(false)}` 必然先于新 attempt 的 `run()` 执行；同机制经另一活路径仍可具体化——分布式恢复 `TaskManager.deployTask` 对被替换槽位**不等待旧线程退出**（RunningTask javadoc 自认"thread winds down asynchronously; the fresh deployTask puts the new RunningTask under the same key immediately"）且嵌入式派发按引用传递同一 JobGraph、`deepCopy` 共享同一 source function 实例。维持 P2 立案，修复与回归须按修正后路径设计，详见维度复核结论节。现状：新发现；同族先例 WI 1.2 C1-003（reader 交付竞态）与 MA-07 C3-014（重启交叠面），均 open）

---

### [nop-stream-orchestration-thin-C1-004] sideInput "只读快照" 契约无强制：Supplier 返回的 Map 裸存并绑定进逐记录 xpl 作用域，可变引用跨记录/跨子任务逃逸

**文件**：nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:1526-1550（materializeSideInputs）；消费面 builder/functions/XplMapFunction.java:37-43、XplFilterFunction.java:37-43、XplFlatMapFunction.java:37-43

**证据片段**：

```java
// StreamModelDslBuilder.materializeSideInputs（WI11）
Supplier<Map<String, Object>> supplier =
        beanResolver().resolve(si.getFrom(), Supplier.class);
Map<String, Object> snapshot = supplier.get();                       // L1534
if (snapshot == null) { ... throw ... }
snapshotsByTarget.computeIfAbsent(si.getTo(), k -> new LinkedHashMap<>())
        .put(si.getId(), snapshot);                                  // L1543-1544 —— 裸引用
...
sideInputScopes.put(entry.getKey(),
        Collections.singletonMap("sideInputs", entry.getValue()));   // L1547-1548
```

```java
// XplMapFunction（消费面：绑定表直接进入每个记录的求值作用域）
private final java.util.Map<String, Object> bindings;                // L37
...
Object result = body.call1(null, value, XplFunctionSupport.newCallScope(bindings));  // L43
```

**严重程度**：P2（已声明契约缺运行时强制；单线程内即可具体化的跨记录共享可变状态违约）

**并发场景**：owner doc（user-guide WI11 行）声明快照是"构建期 get() 恰一次并**冻结**的**只读快照**"。live code：`supplier.get()` 的返回 Map 未做 `Collections.unmodifiableMap` 包装、未做防御拷贝，直接经 `sideInputScopes` → `Xpl*Function.bindings` → `newCallScope(bindings)` 进入每个记录的 xpl 作用域。任何 map/filter/flatMap 的 xpl 体执行 `sideInputs['dict'].put(k,v)`（XLang 对 Map 值的方法调用不受限）即就地改写"只读"快照。LOCAL 并行>1 时该函数经序列化按子任务拷贝（爆炸半径收敛到单 subtask，但该 subtask 的**整个生命周期**内快照被污染，且"P=1 时无拷贝"意味着全作业共享同一份）；同时 Supplier bean 自身仍持有同一 Map 引用——"bean 运行期不再被调用"的声明不妨碍 bean 在别的通道改写它。

**触发时序**：T1 = 记录 N 的 xpl 体执行 `sideInputs['dict']['vip'] = true`（写共享快照）；T2 = 记录 N+1 的同一体执行 `sideInputs['dict']['vip']`（读到 T1 的写入——同一 subtask 线程序列化，happens-before 天然成立，故这不是数据竞争而是**语义违约**：声明不可变的构建期快照被运行期改写，窗口聚合/风控判定从记录 N+1 起基于被污染维表）。P>1 时各 subtask 各自污染各自的拷贝，同一声明在不同 subtask 上产出不同维表视图——快照撕裂。

**现状**：null 检查 fail-fast 做了（L1535-1542），broadcast=false 拒绝做了，唯独"只读"这一核心契约零强制、零文档化免责。

**风险**：声明契约静默失效类缺陷；用户按文档写的模型在特定 xpl 写法下产出难复现的错误聚合，与"冻结快照、低频更新=重启刷新"的运维心智直接冲突。

**建议**：`materializeSideInputs` 中对 snapshot 做 `Collections.unmodifiableMap`（必要时深拷贝一层），使写路径在运行期抛 UnsupportedOperationException——把契约违约从"静默污染"变为"即时 fail-fast"，与该平台 Anti-Hollow 风格一致。

**失效面枚举（F3 同族全查）**：绑定写入点 1 处（materializeSideInputs L1543-1548）；绑定消费面 3 处（XplMapFunction.bindings / XplFilterFunction.bindings / XplFlatMapFunction.bindings，三者为同构复制代码，修一处声明需同步三处 javadoc）；非 sideInput 的 bindings 传入恒为 null（buildMap/buildFilter/buildFlatMap 仅 sideInput 路径注入），无其他同族入口。

**信心水平**：高（单线程交错即成立，无时序依赖）。

**误报校准**：非"已声明且带 fail-fast 守卫的边界"——声明了边界但恰恰没有守卫；非 bean 形态路径（bean 形态已被 typed 拒绝，仅 xpl 形态可达）。

**复核状态**：已保留（P2 不变——materializeSideInputs 裸存、bindings 进逐记录 `newCallScope`、owner doc user-guide L175/L311 与 builder javadoc 的"冻结只读快照"声明均核实属实，零强制成立；单线程即可违约非数据竞争而是语义违约，P2 口径准确。引文行号有 ±5 行漂移（XplMapFunction.bindings 实际 L31/call L48 等），代码引文逐字属实，不构成驳回理由。现状：新发现；同族对照 WI 1.2 C1-005（SideCollector ctx 槽位共享可变引用跨 subtask 逃逸），open）

---

### [nop-stream-orchestration-thin-C1-005] StreamModelDslBuilder.build() 就地改写调用方持有的已解析 StreamModel：模型对象被构建后成为被污染的共享可变状态

**文件**：nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:580-586（expandSqlModel 终局改写）、613-621+795 起（expandSqlTransforms 剥离拼接）、841 起（renameIds 对 XNode 树改写）

**证据片段**：

```java
// expandSqlModel()：把 <sql> 编译产物整体替换进父模型
model.setTransforms(compiled.getTransforms());   // L580
model.setEdges(compiled.getEdges());             // L581
model.setSchemas(compiled.getSchemas());         // L582
model.setAggregators(compiled.getAggregators());// L583
model.setJoins(compiled.getJoins());             // L584
model.setWindowingStrategies(compiled.getWindowingStrategies()); // L585
model.setSql(null);                              // L586
```

**严重程度**：P2（理论风险，未具体化——单元内标准加载链每次 parse 产生新模型，无现成跨线程共享路径；但平台语境下解析产物缓存是常态，此处把"模型"变成了"构建耗材"）

**并发场景**：`build()` 不是纯函数：`<sql>` 展开与 `<sqlTransform>` 剥离拼接都直接改写入参 `StreamModel` 及其 XNode 树。平台语境明确提示 XDSL/流管线解析产物存在全局缓存惯例（模型管理器按资源路径缓存解析结果）；一旦宿主按平台惯例缓存了已解析模型并在两个线程/两次构建中复用同一实例：T1 = 构建 A 执行 L586 `model.setSql(null)`；T2 = 构建 B 同时在 `expandSqlModel` L520 读 `model.getSql()` 或在 validateDag 遍历 `model.getTransforms()`——set 与遍历之间无任何同步，B 可能基于半改写状态（sql 已置空但 transforms 未替换，或 transforms 已替换而 edges 未替换）建出撕裂图。顺序复用（同线程 build 两次）也会静默改变第二次的输入（第二次 `getSql()` 为 null，靠展开后的幂等残态碰巧通过）。

**触发时序**：单元内写不出现实触发路径（user-guide "标准加载链" 每次构建前 fresh parse；RemotePipelineResolver 在 TM 侧重解析）——按纪律降 P2 标注"理论风险，未具体化"。风险为**平台惯例耦合型**：缺陷不在这三行代码本身，而在"构建会破坏输入"这一未声明的副作用使该类型不可安全进入平台既有的缓存/复用模式。

**现状**：javadoc 未声明 build() 消耗/改写模型；`of()` 工厂不拷贝模型。

**风险**：与平台模型缓存、Delta 派生、多作业复用同一定义的演化方向正面冲突；一旦上游引入缓存即从理论转现实。

**建议**：二选一并 javadoc 声明——(a) build() 首行对 model 做防御性深拷贝（XNode 可拷贝），保持 of() 幂等可重入；(b) 显式契约化 "build() consumes the model" 并在第二次 build 时 fail-fast（`alreadyBuilt` 标志）。

**失效面枚举（F3 同族全查）**：模型改写点共 3 族：expandSqlModel（L580-586，6 个 setter）、expandSqlTransformVertex 剥离/拼接（transforms/edges 列表增删 + stripAndRenameFragment/renameIds 对 XNode 树的属性改写，L783-870）、declareComponentRegistries 侧无改写。全部集中在 build() 入口之下，单一加拷贝点即可全覆盖。

**信心水平**：中（副作用确凿；跨线程触发依赖单元外的缓存惯例）。

**误报校准**：未按 P1 报——无单元内可达的共享路径；未按"非缺陷"放过——模型作为共享可变状态的发布面确有缺口。

**复核状态**：已保留（P2 不变——expandSqlModel 6 setter、sqlTransform 剥离拼接、renameIds 改写均核实属实；单元内全部调用方 fresh parse 核实成立（StreamConfValidator.parseModel 逐次解析、quickstart 模板、RemotePipelineResolver TM 侧重解析），"理论风险未具体化"披露诚实，合 P2"理论竞态"口径。现状：新发现）

---

### [nop-stream-orchestration-thin-C1-006] UserHistoryEnricher.lastCityState 在跨子任务共享 bean 实例上做无同步惰性初始化（后果良性，模式有传播风险）

**文件**：nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/UserHistoryEnricher.java:45-54

**证据片段**：

```java
private transient ValueState<String> lastCityState;               // L47（非 volatile）

private ValueState<String> lastCityState() {
    if (lastCityState == null) {                                  // L49 检查
        KeyedStateStore store = getRuntimeContext().getKeyedStateStore();
        lastCityState = store.getState(LAST_CITY_STATE_DESC);     // L51-52 行动
    }
    return lastCityState;                                         // L53
}
```

**严重程度**：P3（防御性/模式风险；竞争后果良性）

**并发场景**：`process` 仅支持 bean 形态（owner doc 表 A），该 bean 是 IoC singleton；LOCAL 执行中 user functions 跨 subtask 线程共享（core OperatorChain 契约），因此并行>1 时多个 subtask 线程并发首次调用 `lastCityState()`，形成经典 check-then-act 惰性初始化竞争：两线程可同时看到 null、各自 `getState`、相互覆盖字段（字段非 volatile，一方的写入对另一方还可能长期不可见导致重复初始化）。

**触发时序**：T1、T2 同刻进入 L49 均见 null → 各自 L52 赋值 → 后写覆盖先写；两线程各自继续用**不同的 ValueState 句柄**。良性原因：生产 KeyedStateStore 对相同描述符返回同一共享存储（对照 DemoKeyedStateStore javadoc 对生产契约的反向陈述），且描述符是 static final 共享常量，两句柄逻辑等价。无数据损坏。

**现状**：无同步、无 volatile、无守卫；fraud-example 演示模块。

**风险**：后果良性但模式处于文档钦点样例（"真实示例：fraud-example 的 UserHistoryEnricher"）中，易被当作 KeyedProcessFunction 惰性初始化的模板复制；复制到"句柄不等价"的场景（如自带缓存的 wrapper）即成真缺陷。

**建议**：改为 `open()`/首次 processElement 前一次性初始化（KeyedProcessFunction 生命周期钩子），或在字段上加 volatile 并接受重复初始化；demo 亦可加一行注释声明单读者假设。

**失效面枚举（F3 同族全查）**：本模块内 bean 共享函数上的惰性初始化仅此 1 处；同族 `UserTransactionHistory` 每记录 new（无惰性字段）；`DirectoryFileSourceFunction.runEntered`（见 C1-003，已单列）；sql/flow 两模块经 grep 核实无实例级 check-then-act 惰性初始化。

**信心水平**：高（竞争存在确凿；良性判定依赖生产 store 契约陈述）。

**误报校准**：fraud-example 演示代码——按校准仅在"暴露生产可复制缺陷模式"时立案，本条以此为由立案并明确标注 P3。

**复核状态**：已保留（P3 不变——check-then-act/非 volatile transient 属实；良性依据核实：DemoKeyedStateStore javadoc L25-27 反向陈述生产 store 契约（"a production KeyedStateStore returns the same shared storage for equal descriptors"），引擎侧 ProcessOperator 为共享函数装配真实 keyed backend，等价句柄论成立。现状：新发现；同族先例 WI 1.2 C1-006（共享 Rich udf 生命周期 last-writer-wins），open——同根因共享面、不同载体）

---

### [nop-stream-orchestration-thin-C1-007] InMemoryBeanFunctionResolver：可变注册表的"只读视图"实为活视图，item-14 部署快照路径可随注册并发撕裂

**文件**：nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/InMemoryBeanFunctionResolver.java:23-37

**证据片段**：

```java
private final Map<String, Object> beans = new LinkedHashMap<>();   // L27

public InMemoryBeanFunctionResolver register(String name, Object bean) {
    beans.put(name, bean);                                         // L29
    return this;
}

/**
 * Item 14 (distributed): read-only view of the registered bindings, so a
 * launch-side factory can snapshot the bean set into a serializable pipeline
 * spec ({@code RemotePipelineSpec}) for cross-JVM deployment.
 */
public Map<String, Object> getBeans() {
    return Collections.unmodifiableMap(beans);                     // L36
}
```

**严重程度**：P3（理论风险，未具体化——注册方与快照方在现有时序上分处两个生命周期阶段）

**并发场景**：`getBeans()` 返回的 unmodifiableMap 是**委托视图**而非快照：持有者在迭代时若另一线程 `register`，迭代直接 ConcurrentModificationException，且视图内容随底层 map 漂移——与 javadoc "snapshot the bean set" 的措辞相悖（"快照"承诺的是副本语义）。item-14 用途是把 bean 集合序列化进 `RemotePipelineSpec` 跨 JVM 部署：若 launch 线程序列化视图时有任何并发注册（宿主在 launch 前补注册 bean 是合理时序），序列化读到撕裂结构或抛 CME。

**触发时序**：单元内无并发的注册/快照调用方（测试先 register 后构建；launch 在注册完成后）——按纪律降 P2→P3 理论风险标注。交交错写不出 live 序列，风险随"launch 前动态补注册"用法出现。

**现状**：活视图 + 可变底层；类定位为测试脚手架（javadoc："Tests use this"）但居于 src/main/java 且被 item-14 分布式路径标注为生产用途。

**风险**：命名误导（getBeans/getSnapshot 语义漂移）；与 C1-001 同族的"注册表并发纪律缺失"，但频度与暴露面小一个量级。

**建议**：`getBeans()` 返回 `new LinkedHashMap<>(beans)` 真副本（调用点低频，拷贝成本可忽略）；或改名 `beansView()` 并在 javadoc 撤回 "snapshot" 措辞。

**失效面枚举（F3 同族全查）**：单元内"活视图暴露"共 1 处；同族 `ReplayableCdcSourceFunction.getEventSpecs()`（L93-95）同样是 unmodifiableList 活视图——但其底层列表构造后仅读（构造器防御性拷贝 `new ArrayList<>(eventSpecs)` 后无写者），判定无风险，登记为已核查差异项。

**信心水平**：中高。

**误报校准**：未按 P2 报——无 live 并发时序；未漏报——"snapshot" 语义与实现不符属于可见的契约缺口。

**复核状态**：已保留（P3 不变——L24 LinkedHashMap/L26-29 register/L36-38 unmodifiableMap 活视图与 javadoc "snapshot the bean set" 措辞相悖均核实属实；getBeans 实际调用面 = fraud DistributedScenarioSupport L502（RemotePipelineSpec 部署快照）+ 1 处测试，与 item-14 叙述一致，单元内无并发注册/快照时序属实。现状：新发现）

## 共享可变状态清单表

| # | 载体（字段/对象） | 位置 | 写者 | 读者 | 同步机制 / happens-before | 判定 |
|---|---|---|---|---|---|---|
| 1 | `StreamScalarFunctionResolver.defaultResolver`（static volatile） | sql/eval/StreamScalarFunctionResolver.java:31 | `setDefault`/`registerAsDefault`（IoC init、测试） | `getDefault`（编译期）、`call`（逐记录，任务线程） | volatile 引用发布 | 引用发布安全；**指向对象内部可变表无保护 → C1-001 (P1)** |
| 2 | `StreamScalarFunctionResolver.functions`（LinkedHashMap 实例字段） | 同上:46 | `register()`（公开 API，任意线程任意时刻） | `call()` 逐记录（subtask 线程）、`contains/resolve/namesList`（编译期） | **无** | **C1-001 (P1)** |
| 3 | `XplFunctionSupport.ROOT_SCOPE`（static final IEvalScope，构造后事实只读） | flow/builder/functions/XplFunctionSupport.java:34 | 无（结构上仅子作用域写自身 map） | 9 个 xpl 包装器逐记录 `newChildScope`（多 subtask 线程并发） | static final 安全发布 + 子作用域写隔离（结构性不变量，无守卫） | 当前安全；**不变量侵蚀风险 → C1-002 (P2)** |
| 4 | sideInput 快照 Map（Supplier 返回值，经 bindings 逃逸） | flow/builder/StreamModelDslBuilder.java:1534-1548 | Supplier bean（构建期一次）；**xpl 体可写（未禁止）** | 每记录 xpl 求值（subtask 线程） | 构建期冻结（构造 happens-before 任务线程启动）；运行期写无保护 | **只读契约无强制 → C1-004 (P2)** |
| 5 | 已解析 `StreamModel`（可变 bean，build() 就地改写） | flow/builder/StreamModelDslBuilder.java:580-586、613-621 | build()（expandSqlModel/expandSqlTransforms） | 同一 build() 内后续 pass；（潜在）复用宿主 | 单构建线程内顺序性；跨构建无 | 单元内单线程安全；**复用/缓存场景理论风险 → C1-005 (P2)** |
| 6 | `DirectoryFileSourceFunction.cursors`（TreeMap） | fraud/scenario/DirectoryFileSourceFunction.java:68 | 读者 subtask 线程 advanceCursor；恢复线程 initializeState | 同实例其他 subtask 的 snapshotState（各自任务线程） | `synchronized(cursors)` 全覆盖读/写/快照/清空 | 正确（对照核实四个访问点全部在锁内） |
| 7 | `DirectoryFileSourceFunction.runEntered`（AtomicBoolean，transient+readObject 重建） | 同上:79-80 | run() CAS / finally set | run() CAS | CAS 原子；无跨尝试先后序 | 并行首入正确；**restart 窗口缺口 → C1-003 (P2)** |
| 8 | `DirectoryFileSourceFunction.cancelled`（volatile） | 同上:82 | cancel()（协调线程） | 读循环逐字节轮询 | volatile | 正确 |
| 9 | `ReplayEngine.cancelled`（volatile）+ `ReplaySubscription.onCancelTasks`（ArrayList） | fraud/scenario/ReplayableCdcSourceFunction.java:116、134 | cancel(reason)（取消线程）/appendOnCancel/removeOnCancel（生产基类注册路径） | runReplay 轮询（emitter 线程）；cancel 迭代 | volatile 覆盖标志；**ArrayList 无同步** | 标志正确；回调表竞争仅在"注册与取消并发"时成立，注册方在生产基类（单元外）→ 清单登记，立案移交 runtime 轮次 |
| 10 | emitter daemon 线程读写的 finals（events/offsetStore）与 lambda 逃逸 `this` | 同上:112-131 | 构造线程 | emitter 线程 | final 字段冻结 + volatile cancelled；线程在 subscribe()（run 路径）而非构造器启动 | 安全发布（无构造器启线程） |
| 11 | `UserHistoryEnricher.lastCityState`（transient，非 volatile） | fraud/scenario/UserHistoryEnricher.java:47 | 惰性初始化（首次 processElement） | 后续每记录 | **无**（check-then-act） | 竞争存在、后果良性 → **C1-006 (P3)** |
| 12 | `SqlRowAggregateOps.CACHE`（static final CHM） | sql/eval/SqlRowAggregateOps.java:27-38 | computeIfAbsent（逐记录首见） | 逐记录 function() | CHM computeIfAbsent（原子）；值为构造后不可变的 SqlRowCompositeFunction（final 字段、无记录态） | 正确；有界性=不同管线 spec 数（javadoc 已声明） |
| 13 | `RecordColumnAccess.GETTER_CACHE`（static final CHM） | sql/eval/RecordColumnAccess.java:36-37、82-86 | computeIfAbsent(collectGetters) | 逐记录 getterFor | CHM 原子；缓存值为构建后不再修改的 HashMap（CHM put 安全发布） | 正确；有界性=记录类数量 |
| 14 | `StreamSqlAggregations.BUILTINS` / `StreamSqlExprCompiler.MANAGED_TYPE_BY_NAME` / `StreamSqlCompiler.MANAGED_TYPES`（static final 不可变） | sql/eval/StreamSqlAggregations.java:32-45 等 | 无（类初始化后只读） | 编译期 | static final + unmodifiableMap | 正确 |
| 15 | `StreamSchemaRegistry`（per-build 不可变注册表） | flow/builder/StreamSchemaRegistry.java:74-139 | resolveSchemas（build 期一次） | build 期校验、WI17 SQL 编译查询 | 构造后 unmodifiable 包装、无 setter；**非全局缓存**（每 build 新实例） | 正确（审计任务点名的"编译产物缓存"疑点经查为 per-build 对象，非共享缓存） |
| 16 | `StreamModelDslBuilder` 实例字段（streamRegistry/sideInputScopes/resolvedSinkBeans/schemaRegistry/preBuildEnvironmentConfigurer） | 同上:152-176 | build() 及其 withPreBuildEnvironment | 同一构建内 | 每构建新实例、构建期单线程（of() 工厂、无静态共享） | 正确（transient 构建对象） |
| 17 | `InMemoryBeanFunctionResolver.beans` + getBeans() 活视图 | flow/builder/InMemoryBeanFunctionResolver.java:27-36 | register() | resolve/contains/getBeans（item-14 部署快照） | 无 | **C1-007 (P3)** |
| 18 | `XplSourceFunction.running`（volatile） | flow/builder/functions/XplSourceFunction.java:45 | cancel()（close 路径线程） | isRunning()（测试面）；run() 明确不读 | volatile | 正确（javadoc 显式声明 run 不读该标志、取消走 ctx） |
| 19 | 聚合累加器（SqlAccShape 归一化的 Object[]、Acc.count++/totalAmount、DistinctAggregateWrapper.seen） | sql/eval/StreamSqlAggregation.java 各 Agg、fraud/scenario/TransactionWindowAggregate.Acc 等 | add/merge（所属 keyed 分区的唯一任务线程） | getResult（同线程）；checkpoint 序列化 | keyed 路由保证同 key 单线程；状态随窗口/keyed state 归属 | 正确（非跨线程共享；`count++` 非原子但无并发读者） |
| 20 | `UserTransactionHistory` 双 ValueState 读改写（count/total） | fraud/state/UserTransactionHistory.java:51-68 | update()（key 分区线程） | getAverage/getCount（同线程） | keyBy 路由单写者；ValueState 归属 keyed backend | 正确（RMW 非原子但单线程序内） |
| 21 | ThreadLocal | — | — | — | — | 三模块零使用（基线与全仓 grep 双确认），池化线程残留问题不存在 |
| 22 | SimpleDateFormat/Random/日期工具 static 共享 | — | — | — | — | 三模块零使用（MockTransactionGenerator 为确定性 fixture，无 Random 字段） |

## 覆盖度与残留边界

**覆盖声明**：三个模块 src/main/java 全部 98 文件 / 13,792 行全量通读（生成基类 `_gen/*` 与 `_` 前缀文件除外，flow/model 的 30 个空壳子类仅核对其继承面）；grep 基线五类载体（static 非终态、static 集合/日期工具、ThreadLocal、锁载体、computeIfAbsent）与 C4 线程创建、C3 异步链条目逐条落到 live code；对 4 处平台语境（EvalScopeImpl 子作用域语义、OperatorChain/StreamSourceOperator 共享 UDF 契约、GraphExecutionPlan restart 复用、IoC app-beans 注册）做了源码级交叉取证。

**规模包络**：owner doc 未为三模块声明行数包络；实测与审计任务书给定规模完全一致（60/6,202 + 17/4,334 + 21/3,256 = 98/13,792），无异常膨胀文件需要包络裁定。

**本维度移交其他维度的残留点**：
1. `ReplayableCdcSourceFunction.ReplaySubscription.onCancelTasks`（ArrayList，写者=生产基类注册路径）——竞争成立与否取决于单元外 `DebeziumCdcSourceFunction` 的注册线程模型 → 移交 runtime/连接器维度的 DebeziumCdcSourceFunction 审计（清单 #9）。
2. core `WindowedStreamImpl.setDefaultFactory` 静态工厂先例（C1-001 的族外同型）→ 移交 nop-stream-core 轮次。
3. `SqlRowAggregateOps.begin()` 每记录对同一 specJson 做两次 CHM 查找（L44）——热路径可量化微浪费，P3 性能候选，归 C9 热路径维度。
4. emitter daemon 线程创建未入池、无命名外泄防护（ReplayableCdcSourceFunction L127）——归 C4 线程/池维度。
5. 逐记录 `new UserTransactionHistory` + 每记录 `newCallScope` 分配——归 C9；后者若证成开销可并入 C1-002 的修复一并处理。

**误报排除记录**（已查证不立案）：无状态 singleton 并发调用（AdvancedTransforms/StreamConfValidator/StreamModelLinter/StreamSqlCompiler/SqlScalarXplPrinter/CompileErrors/各 resolver——全为 static 方法或 per-run 实例）；CHM 原语本身（#12/#13）；static final 不可变目录（#14）；keyed 单线程累加器（#19/#20）；DemoKeyedStateStore（javadoc 显式声明 demo-only 边界且不支持的 state 类型 fail-fast，符合"已声明边界"豁免）；FraudDetectionDemo/MockTransactionGenerator/各 Pattern 静态工厂（单线程演示入口与纯函数）；DirectoryFileSourceFunction 的"每子任务快照同一游标表 + 读竞赛恢复"（javadoc 显式声明且确定性恢复有测试锚点，属已声明边界）；跨 maxParallelism reshard 守卫（owner doc 明文预期行为）。

**owner doc 偏差（同任务应修的 docs bug）**：`docs-for-ai/03-modules/nop-stream-sql.md` §3a "编译期一次解析（非逐记录查找）" 与 WI30 声明面运行路径（SqlScalarXplPrinter 发射的逐记录 `StreamScalarFunctionResolver.call` 静态查表）不符——见 C1-001。

## 维度复核结论（独立复核子代理）

- 复核子代理：C1 独立复核子代理（与初审/round2 盲审代理不同角色；职责为逐条验证与三态判定，不新增发现）
- 日期：2026-10-14；仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation，HEAD 8dcfa4cd0c）
- 方法：对 C1-001~007 全部 7 条重读 live code 逐条验证（文件/行号/证据引文/触发时序/判级口径）；平台语境交叉取证在初审 4 处基础上扩展至**运行时重启纪律**（nop-stream-runtime `SupervisionLoop.restartRegion`/`waitForTerminal`、nop-stream-core `SubtaskTask` 终态迁移、`TaskManager.deployTask` 槽位替换、`RunningTask` 线程退出序、`OperatorChain.deepCopy`/`StreamSourceOperator.copyForSubtask` 引用共享语义、`RemoteGraphExecutionPlanBuilder` L313）；历史对照限 ai-dev/audits/ 下 nop-stream 相关目录（WI 1.1 core-runtime、WI 1.2 state-connectors、r5/r6 quality、adversarial×2）+ cra-index.md + git log，未读 ai-dev/plans、ai-dev/bugs、ai-dev/lessons。
- round2 复核：**0 新增成立，终止声明背书**。独立抽查其全部承重负面结论——2.1 读者矩阵（含 `namesList()` 全单元零调用方）、2.2 兄弟 resolver 字段面（StreamAggregatorFunctionResolver 仅 serialVersionUID、GlobalBeanFunctionResolver 无状态 INSTANCE）、2.3 构造器 this 逃逸 0（ReplayEngine 线程在 subscribe() run 路径启动、final 字段 + volatile cancelled）、2.4 `++` 落点全为局部变量（emitRemaining offset 局部变量核实）、2.6 cursors 恢复交错否决推演（四个访问点全部 `synchronized(cursors)` 核实）、2.7 活视图家族 9 处——全部复核成立。

### 逐条判定表

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C1-001 | P1 | **保留** | LinkedHashMap 注册表 + 逐记录 `call()` 读 × 无守卫公开 `register()` 写全部属实；"非线程安全集合属 Java 常识"抗辩不成立——读侧是框架生成代码的逐记录入口、写侧是无生命周期约束的公开 SPI，"罕见但可具体化"合 P1 口径；sql.md §3a 偏差属实 | 新发现（本单元无前置历史；同族先例 core `WindowedStreamImpl.setDefaultFactory` 已由 WI 1.1 C1 清单表裁定为"启动/配置路径粗锁豁免"，本条为公开注册 API 形态，不适用该豁免；WI 1.2 注册表族 MANIFEST_JVM_LOCKS=state-connectors C1-004 为 open） |
| C1-002 | P2 | **保留** | `EvalScopeImpl` 源码复核成立：子作用域自持 variables、父作用域仅只读递归、`beanProvider/classModelLoader` 确为非 volatile 可变字段（L33-34）；ROOT_SCOPE 事实只读 + 9 调用方单一漏斗 + 零守卫，"不变量侵蚀型理论风险"合 P2 | 新发现 |
| C1-003 | P2 | **保留（触发路径修正）** | 守卫非粘性属实，但原命名触发路径（LOCAL 同进程 region restart）经运行时源码复核**不成立**：`restartRegion` Phase 2 `waitForTerminal`（10s fail-loud zombie 预算）严格等待旧任务终态，而 `SubtaskTask` 终态仅在 `inv.invoke()` 返回后置位——旧 attempt 的 `finally{set(false)}` 必然先于新 attempt `run()`；同机制经另一活路径仍可具体化：分布式恢复 `TaskManager.deployTask` 槽位替换**不等待旧线程退出**（RunningTask javadoc 自认 "thread winds down asynchronously; the fresh deployTask puts the new RunningTask under the same key immediately"）且嵌入式派发按引用传同一 JobGraph、`deepCopy` 共享同一 source function 实例 | 新发现（本单元无前置历史；同族先例 WI 1.2 C1-003 reader 交付竞态、MA-07 C3-014 重启交叠面，均 open） |
| C1-004 | P2 | **保留** | 快照裸存 + bindings 进逐记录 `newCallScope` + owner doc（user-guide L175/L311）与 builder javadoc "冻结只读快照"声明零强制均属实；单线程语义违约非数据竞争，P2 口径准确（引文行号 ±5 行漂移、代码逐字属实，不足以驳回） | 新发现（同族对照 WI 1.2 C1-005 SideCollector ctx 槽位共享可变引用跨 subtask 逃逸，open） |
| C1-005 | P2 | **保留** | expandSqlModel 6 setter / sqlTransform 剥离 / renameIds 改写属实；单元内全调用方 fresh parse 核实成立（StreamConfValidator.parseModel 逐次解析、quickstart、RemotePipelineResolver TM 侧重解析），"理论风险未具体化"披露诚实，合 P2"理论竞态"口径 | 新发现 |
| C1-006 | P3 | **保留** | check-then-act / 非 volatile transient 属实；良性依据核实：DemoKeyedStateStore javadoc L25-27 反向陈述生产 store 契约、引擎 ProcessOperator 为共享函数装配真实 keyed backend，等价句柄论成立；P3 模式风险定位准确 | 新发现（同族先例 WI 1.2 C1-006 共享 Rich udf 生命周期 last-writer-wins，open——同根因共享面、不同载体） |
| C1-007 | P3 | **保留** | L24/L26-29/L36-38 活视图与 javadoc "snapshot the bean set" 措辞相悖属实；getBeans 实际调用面 = fraud DistributedScenarioSupport L502（RemotePipelineSpec 部署快照）+ 1 测试，单元内无并发时序属实，P3 准确 | 新发现 |

### 不确定项（显式列出）

1. **C1-003 触发路径修正**：初审正文的触发时序（LOCAL region restart）被本复核证伪，可具体化时序改判于分布式恢复 `deployTask` 槽位替换路径（嵌入式同 JVM、descriptor 按引用携带 JobGraph 时成立；跨 JVM 真序列化部署因 `runEntered` transient+readObject 重建而不成立）。按复核纪律未改正文，修复批次（7.1）与回归测试须按修正路径重新设计；若修复仅覆盖 LOCAL 演示路径，残余暴露仅剩 distributed/embedded-distributed 模式。
2. **C1-001 措辞精化**：nop-stream-sql 模块自身无注册 scalar resolver 的 app-beans（app-aggregator.beans.xml 仅注册聚合 resolver）；生产注册为应用侧经自身 app-beans 调 `registerAsDefault()`。不影响竞争分析（IoC init 后运行期 `register()` 仍无守卫），修复文档化生命周期时宜一并写明。
3. **C1-004 行号漂移**：证据行号与 HEAD 存在 ±5 行漂移（XplMapFunction.bindings 实际 L31、`call1` 实际 L48；XplFlatMapFunction L35/L51；expandSqlModel setter 实际 L581-587），引文代码逐字正确——立案事实不受影响，修复 PR 引用行号时应以本节为准。

## 最终保留项

（保留 7 / 降级 0 / 驳回 0；含保留项，无驳回项）

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C1-001 | P1 | nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamScalarFunctionResolver.java:46,49-51,88-97 | 非线程安全 LinkedHashMap 注册表被逐记录 `call()` 无锁读 × 公开 `register()` 任意线程写，无守卫无生命周期文档；声明面运行路径与 sql.md §3a"编译期一次解析"不符（docs bug 同修） | 新发现，未修复 |
| C1-002 | P2 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/functions/XplFunctionSupport.java:34-52 | JVM 级静态共享 eval 根作用域，安全性仅靠结构性不变量（9 调用方自律走 newCallScope），无运行时写守卫 | 新发现，未修复 |
| C1-003 | P2 | nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/DirectoryFileSourceFunction.java:111-130 | CAS 单读者守卫 finally 非粘性复位：跨尝试共享实例的恢复部署窗口内新尝试可静默零发射完成（触发路径经复核修正为分布式恢复 deployTask 槽位替换） | 新发现，未修复（触发时序以维度复核结论节修正为准） |
| C1-004 | P2 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:1526-1550 | sideInput"冻结只读快照"契约零强制：Supplier 返回 Map 裸存并绑定进逐记录 xpl 作用域，可变引用跨记录/跨子任务逃逸 | 新发现，未修复 |
| C1-005 | P2 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:580-587,613-621,841起 | build() 就地改写调用方持有的已解析 StreamModel（含 XNode 树），模型对象构建后成为被消耗的共享可变状态，javadoc 未声明、of() 不拷贝 | 新发现，未修复 |
| C1-006 | P3 | nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/UserHistoryEnricher.java:45-54 | 共享 bean 实例上 ValueState 句柄无同步惰性初始化（竞争后果良性），处于文档钦点样例中有复制传播风险 | 新发现，未修复 |
| C1-007 | P3 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/InMemoryBeanFunctionResolver.java:24-38 | getBeans() 返回活视图冒充"snapshot"，并发注册下 item-14 部署快照序列化可撕裂/CME | 新发现，未修复 |

**需子项复核的高风险项**：C1-003（触发路径经本复核修正，修复方案与回归测试须按分布式恢复 deployTask 路径重验后再收口）；C1-001（唯一 P1，修复面含公共 SPI 语义文档化 + sql.md §3a docs bug 同步修正）。
