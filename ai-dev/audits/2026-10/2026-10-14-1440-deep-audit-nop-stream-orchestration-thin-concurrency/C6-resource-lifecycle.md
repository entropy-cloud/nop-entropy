# C6 资源生命周期与泄漏 — nop-stream-orchestration-thin 第 1 轮初审（盲审）

- 审计单元：`nop-stream/nop-stream-flow`（60 files / 6,202 lines）+ `nop-stream/nop-stream-sql`（17 / 4,334）+ `nop-stream/nop-stream-fraud-example`（21 / 3,256），合计 98 files / 13,792 lines，仅 src/main/java 非 `_` 前缀文件。
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation，HEAD 8dcfa4cd0c）。
- Owner doc：`docs-for-ai/03-modules/nop-stream-user-guide.md`、`docs-for-ai/03-modules/nop-stream-sql.md`（全文已读）。
- grep 基线：`_tmp/cra-baseline-nop-stream-orchestration-thin.txt`（全文已读）。
- 盲审纪律：未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；仅基于 live code。
- 复核状态：本文件全部条目为初审（盲审），均为 未复核。

## 检查范围说明（资源获取点清单）

对 98 个文件做了三类全覆盖定位：机器基线（C4 线程/C6 资源获取/closeable 三节）+ 补充 grep（`ShutdownHook|ExecutorService|new Thread|addListener|register/unregister|FileInputStream|Files.*|getConnection|createTempFile|deleteOnExit` 等）+ 对全部命中文件与全部运行期类的人工全文阅读。单元内资源获取点全量清单如下：

| # | 资源类型 | 获取点（文件:行） | 释放/配对面 | 初判 |
|---|---|---|---|---|
| R1 | 文件输入流 | `DirectoryFileSourceFunction.emitRemaining`（fraud `DirectoryFileSourceFunction.java:159`）`new FileInputStream(file)` | try-with-resources 包裹，成功/`cancelled` 提前 return/异常三条路径均关闭 | 配对完整 |
| R2 | 线程（emitter） | `ReplayableCdcSourceFunction$ReplayEngine.subscribe`（fraud `ReplayableCdcSourceFunction.java:127-129`）每次 subscribe 新建守护线程 | `runReplay` finally 无条件 `outer.cancel()`；循环体轮询 `cancelled`；park 时间有界（emitDelayMs/finishLingerMs）；cancel 不 interrupt（协作式取消，与平台 xpl source 取消模式一致） | 有界退出，见 C6-002/C6-005 关联面 |
| R3 | 订阅句柄 + onCancelTasks 注册表 | `ReplaySubscription`（fraud `ReplayableCdcSourceFunction.java:133-163`）`appendOnCancel`/`removeOnCancel`/`cancel` | 基类 `DebeziumCdcSourceFunction.run` finally `subscription.cancel()` + 置 null（connector `DebeziumCdcSourceFunction.java:297-307`）；`truncateForDrain` 亦 cancel（:352-367） | 不对称/非幂等 → C6-002 |
| R4 | 消息引擎（ReplayEngine） | 基类 `run()` 每 run 新建（`createMessageSource`，fraud 覆写 :97-105） | 基类 finally `source.stop()` + 置 null | 配对完整（基类承载） |
| R5 | 引擎失败监听器注册表 | 基类 `DebeziumEngineWrapper.registerFailureListener`（connector :248-256） | 基类 finally `unregisterFailureListener`（:298） | 配对完整（基类承载，单元外） |
| R6 | offset 静态注册表 | `NopStreamOffsetBackingStore.forConnector(name)`（基类 initializeState :400/:413/:431） | first-run 路径 `clearConnector`（:440-444）；restore 路径注册后由运行期共享（AR-03 语义） | 单元外（nop-message），列入残留边界 |
| R7 | NFA / SharedBuffer / SharedBufferAccessor | `FraudDetectionDemo.runPattern`/`runUnusualAmountPattern`（fraud `FraudDetectionDemo.java:110-115, 139-144`） | accessor 走 try-with-resources（:175）；`nfa.close()` 仅 happy path | 演示 main，按误报校准排除（见误报排除节） |
| R8 | per-record 求值 scope | `XplFunctionSupport.ROOT_SCOPE.newChildScope()`（flow `XplFunctionSupport.java:35-52`；消费点 XplMap/Filter/FlatMap/Reduce/Sink/Source、EvalActionKeySelector、SideOutputTapOperator、JoinSideKeySelector） | `EvalScopeImpl`（nop-core `EvalScopeImpl.java:96-107`）child 仅持 parent 引用、parent 不持 child——无父→子累积，scope 随调用帧 GC | 配对完整（无泄漏） |
| R9 | 静态函数注册表（UDF default） | `StreamScalarFunctionResolver.setDefault/registerAsDefault`（sql `StreamScalarFunctionResolver.java:42-44, 79-81`） | `close()` 为空壳，不清除 static default | register/close 不对称 → C6-003 |
| R10 | 反射 getter 缓存 | `RecordColumnAccess.GETTER_CACHE`（sql `RecordColumnAccess.java:36-37, 82-86`） | 无任何逐出/上限 | 无界声明 → C6-004（理论） |
| R11 | 复合聚合函数缓存 | `SqlRowAggregateOps.CACHE`（sql `SqlRowAggregateOps.java:27-38`） | 无逐出，但 javadoc 声明"每 pipeline 一条"（spec JSON 编译期内嵌、恒定） | 已声明的有界边界，非缺陷 |
| R12 | DISTINCT 去重账本 | `DistinctAggregateWrapper.seen`（sql `StreamSqlAggregation.java:611-638`） | 窗口聚合随窗清除；连续聚合随作业生命周期——javadoc + owner doc 双处声明（"declared cost, no approximation"） | 已声明的边界，非缺陷（F4 归 C5 维度） |
| R13 | sideInput 快照 | `StreamModelDslBuilder.materializeSideInputs`（flow `StreamModelDslBuilder.java:1526-1550`）Supplier.get() 构建期恰一次 | 冻结只读快照随函数实例存活（job 生命周期），owner doc 声明"低频更新=重启作业刷新" | 已声明边界 |
| R14 | 游标注册表 | `DirectoryFileSourceFunction.cursors`（fraud :68, 218-228, 248-298） | snapshot/restore/read/advance 全部 `synchronized(cursors)`；restore 空 map 守卫防清兄弟 subtask 状态 | 配对完整 |
| R15 | bean 解析器 | `GlobalBeanFunctionResolver`/`BeanContainerFunctionResolver`/`InMemoryBeanFunctionResolver`（flow builder 包） | 纯查找，无注册面 | 无资源 |
| R16 | 构建期验证/编译 | `StreamConfValidator`/`StreamModelLinter`/`StreamSchemaRegistry`/`AdvancedTransforms`/`StreamSqlCompiler`/`EqlExprSupport`/`SqlScalarXplPrinter` | 纯构建期对象图操作，grep 证实无线程/IO/连接 | 无资源 |

## 发现条目

### [nop-stream-orchestration-thin-C6-001] ReplayableCdcSourceFunction：replay 引擎异常被降级为"干净 EOS"——半个成功无恢复语义（吞异常 × 半成功）

**文件:行号**：`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java:165-190`（关联生产基类 `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:283-296`）

**证据片段**：

```java
// ReplayableCdcSourceFunction.java:165-190
private void runReplay(Consumer<ChangeEvent> action) {
    try {
        long resumeIndex = readReplayIndex();          // 可抛 NumberFormatException
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

```java
// 生产基类对"终端引擎失败"的对照契约（DebeziumCdcSourceFunction.java:292-296）
if (pendingError != null) {
    // Rethrow after the finally block has unsubscribed and stopped the
    // source, so cleanup never races the failure propagation.
    throwPendingError();
}
```

**严重程度**：P1

**并发场景**：emitter 线程（`fraud-s1-replay-cdc`）在任务线程之外独立跑 replay；任何 replay 侧异常（fixture 解码失败、`writeReplayIndex` IO/状态异常、`readReplayIndex` 对损坏 checkpoint 状态抛 `NumberFormatException`）都发生在 emitter 线程上，基类 `run()` 的 `pendingError`/failure-listener 失败通道（R5-CON-03 契约）对该引擎双实现完全不生效——`ReplayEngine` 既不向 `pendingError` 写入，也没有注册 connector-name 失败监听器。

**触发时序/泄漏链**：① 作业从 checkpoint 恢复，`initializeState` 还原 replay index；② 若还原文本非数字（状态损坏/手工编辑 checkpoint），`readReplayIndex()`（:192-203 `Long.parseLong(text.trim())`）在 emitter 线程抛 `NumberFormatException`；③ 被 `catch (Exception)` 捕获 → 仅 LOG.error；④ finally 调 `ReplayableCdcSourceFunction.this.cancel()` → 基类 `running=false` + latch countDown → `run()` 等待循环正常退出且 `pendingError == null` → 任务以 **成功/EOS 终态** 结束，作业对外报告成功；⑤ 实际结果：零事件或部分事件重放，无重试、无告警、无恢复语义。泄漏对象是"作业成功语义"本身：错误被吞成日志 + 半个成功。日志文案声称 "so the job fails loudly"，但 cancel 所走基类 `cancel()` 路径（:331-344）不产生任何失败信号——机制与声明相反。

**现状**：catch(Exception) 分支只记日志；异常与正常完成走同一条 finally-cleanup 路径，无失败传播通道。

**风险**：错误路径上作业假成功（silent data loss），恢复语义缺失；同类链覆盖 `writeReplayIndex` 抛出（offset store 异常）与 emitter 内 `toChangeEvent` 转换异常（fixture 演进时）。审计单元为演示场景（fraud-example 校准规则），但该类是 owner doc 点名的 S1 场景 source（`nop-stream-user-guide.md` 实例锚点表），且直接继承生产 `DebeziumCdcSourceFunction` 并绕过其 R5-CON-02/03 已声明的 fail-loud 契约——按"暴露生产代码缺陷/违反已声明契约"立案标准成立。

**建议**：`catch (Exception e)` 分支不应走 `cancel()` 收尾，而应将异常经基类既有失败通道传播（写入 `pendingError` 字段等价物或注册 `DebeziumEngineWrapper.registerFailureListener` 回调使 `run()` rethrow），使任务 FAILED 并从 checkpointed offset 重启；或将 runReplay 失败包装后从 subscribe 的 consumer 链重抛。

**失效面枚举**：`ReplayableCdcSourceFunction.runReplay`；所有引用 S1 场景的 demo 模型（`/nop/stream/demo/fraud-s1-cdc.stream.xml`）与 fraud-example E2E/多 JVM gated 测试；`ReplayableCdcSourceFunctionTest` 族（若存在）在"损坏 replay index"用例上会由红变绿失效。

**信心水平**：高（机制链完整、代码路径全部人工核对；严重度评级受演示代码校准影响，故不评 P0）。

**误报排除**：`InterruptedException` 分支（restore interrupt + cancel 收尾）是协作式取消的合法路径，不在本发现内；正常完成路径的 finally-cancel 是有界源 EOS 的声明机制（:186-188 注释），亦不在内。

**复核状态**: 已降级至 P2（与同单元 C3-001 复核结论同向自洽：同一代码点同一失效层——collect 异常主触发已被父类订阅 lambda 的 pendingError 通道否证，残余触发面 readReplayIndex NFE / writeReplayIndex 为内存 CHM store + 索引自产自销的边缘路径，按 C3-002"罕见但可具体化→P2"先例定 P2；catch-all+cancel 伪装 EOS 与 "fails loudly" 注释相悖、pendingError 通道可用而未用的结构缺陷成立，不驳回。见文末「维度复核结论」。现状：新发现）

---

### [nop-stream-orchestration-thin-C6-002] ReplaySubscription：取消回调注册表三重偏离平台 Cancellable 契约——cancel 非幂等、列表无同步、无逐回调异常隔离

**文件:行号**：`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java:133-163`（对照平台参考实现 `nop-kernel/nop-commons/src/main/java/io/nop/commons/lang/impl/Cancellable.java:40-124`）

**证据片段**：

```java
// ReplayableCdcSourceFunction.java:147-162（审计单元）
@Override
public void cancel(String reason) {
    ReplayEngine.this.cancelled = true;
    for (Consumer<String> task : onCancelTasks) {   // 非幂等：每次 cancel 全量重跑
        task.accept(reason);                        // 无逐任务异常隔离
    }
}
@Override
public void appendOnCancel(Consumer<String> task) {
    onCancelTasks.add(task);                        // 普通 ArrayList，无同步
}
```

```java
// 平台参考实现 Cancellable.java:44-51（契约基准：幂等 + 同步 + 逐回调隔离）
synchronized (this) {
    if (cancelled)
        return;
    this.cancelReason = cancelReason;
    this.cancelled = true;
}
tryRunCallbacks();   // tryRunCallbacks 内逐任务 try/catch（:115-123）
```

**严重程度**：P2（理论风险，未具体化到当前接线——见下）

**并发场景**：`cancel()` 由取消方线程调用（作业 cancel RPC / 基类 run finally 的清理线程），`appendOnCancel`/`removeOnCancel` 由任务线程调用。三条缺陷：① `cancel` 无 check-and-set，重复调用全量重跑回调；② 普通 `ArrayList` 跨线程读写无同步——cancel 迭代中并发 add/remove 可抛 `ConcurrentModificationException`、漏执行或 `ArrayIndexOutOfBoundsException`；③ 单个回调抛异常中断整个循环，其余清理任务不执行（平台实现逐任务 try/catch 并 LOG）。另有一个注册/触发不对称：`ReplayEngine.stop()`（:213-216）与正常完成路径只置 `cancelled`，**不执行** `onCancelTasks`——经 stop 收尾时已注册的清理任务被跳过。

**触发时序/泄漏链**：当前接线不可达是明确的——全仓 grep 证实 `appendOnCancel` 在本订阅上无任何生产调用方，`onCancelTasks` 恒为空表，故今天无实际泄漏。但基类已存在**真实双重 cancel 路径**：自然完成时 emitter finally → `outer.cancel()`（`DebeziumCdcSourceFunction.cancel` :336-339 `sub.cancel()`）之后，基类 run finally 再次 `subscription.cancel()`（:299-307）——一旦未来有清理任务注册，同一任务将被执行两次（对不可重入清理如 delete-temp-file/countDown 是二次释放面）。泄漏链（条件触发）：注册清理任务 → 引擎经 `stop()` 或自然完成收尾 → 注册表不被执行（stop 路径）或被执行两次（双 cancel 路径）→ 临时资源滞留或二次释放。

**现状**：三处均与平台同类接口的参考实现偏离；`ReplayEngine` 是生产 `DebeziumMessageSource` 的测试双胞胎，但其 `ICancellable` 实现未复用 `io.nop.commons.lang.impl.Cancellable`。

**风险**：作为"换一个 bean 即得真实 CDC"升级路径上的样板代码（类 javadoc :42-44 自述），该实现会被复制到用户代码；潜在 CME/漏清理/二次清理。

**建议**：直接组合平台 `Cancellable`（`private final Cancellable delegate = new Cancellable()`，各方法转发），或至少：cancel 加 check-and-set 幂等门、换 `CopyOnWriteArrayList`、逐回调 try/catch。

**失效面枚举**：`ReplayableCdcSourceFunction$ReplaySubscription` 全部四个公开方法；基类 `DebeziumCdcSourceFunction.run` finally 与 `truncateForDrain` 的 cancel 调用点；未来在该订阅上注册清理钩子的任何调用方。

**信心水平**：中高（缺陷本身确凿、与平台参考实现差异可定位；"当前不可达"的判定基于全仓 grep 证据）。

**误报排除**：`isCancelled`/`getCancelReason` 读 `ReplayEngine.cancelled`（volatile）无同步问题，不在发现内；`stop()` 的 synchronized 修饰本身无缺陷，缺陷是其绕过回调注册表的语义不对称。

**复核状态**: 已保留（P2——cancel 无 check-and-set、普通 ArrayList 无同步、无逐回调隔离三点逐一证实，平台 `Cancellable` 契约基准（:44-51, :115-123）核实；onCancelTasks 恒空、今日无实际泄漏，理论风险定级恰当。见文末「维度复核结论」。现状：新发现）

---

### [nop-stream-orchestration-thin-C6-003] StreamScalarFunctionResolver：静态 default 只注册不清除——register/close 不对称，close 后残留可达

**文件:行号**：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamScalarFunctionResolver.java:31-44, 73-97`

**证据片段**：

```java
// StreamScalarFunctionResolver.java:42-44, 73-81, 88-97
public static void setDefault(StreamScalarFunctionResolver resolver) {
    defaultResolver = resolver;                 // 只进不出
}
...
@Override
public void close() {
    // no owned resources; subclass hook        // close 不清除 static default
}
public void registerAsDefault() {
    setDefault(this);
}
public static Object call(String fnName, Object... args) {
    StreamScalarFunctionResolver r = defaultResolver;   // close 后仍解析到旧实例
    ...
    return fn.apply(args);
}
```

**严重程度**：P2（理论风险，未具体化——单容器生命周期内不可触发）

**并发场景/触发时序**：owner doc（`nop-stream-sql.md` §3a）声明生命周期契约："provider 实现 AutoCloseable 由 bean 容器传播 close"。时序链：① 应用 bean 以 `registerAsDefault()` 为 init 钩子把实例钉进 static `defaultResolver`；② 容器关闭（测试套件多个 BeanContainerBuilder 顺序启停 / dev 热重启 / 同 JVM 第二容器装配）触发 `close()`；③ `close()` 不做 `setDefault(null)` 也不与静态解绑——旧实例仍是编译期 `compileFunction`（`StreamSqlExprCompiler.java:222-231` 经 `getDefault()`）与运行期 `call()` 的解析目标；④ 若子类覆写 `close()` 释放了真实资源（javadoc 明示这是设计意图），close 后任何在途/迟到的 UDF 调用打到已释放资源上——use-after-close 无守卫；⑤ 同时旧实例被 static 强引用，持有其类加载器，阻碍容器级 GC。

**现状**：`close()` 显式空实现；无 `unregisterAsDefault`/`removeDefault` 对偶 API；`setDefault` 替换时也不关闭前一实例。

**风险**：多容器/重启场景下的 stale-provider 与 use-after-close；单容器生产形态（NopIoC 一次装配终身持有）下无实际影响，故降 P2 并标注理论风险。

**建议**：`close()` 中增加 `if (defaultResolver == this) setDefault(null);`；`setDefault` 替换前对旧值调 `close()`（或文档声明替换语义不传播 close，与 `WindowedStreamImpl.setDefaultFactory` 先例对齐口径）。

**失效面枚举**：`StreamScalarFunctionResolver.close/setDefault/registerAsDefault/call`；`StreamSqlExprCompiler.compileFunction` 的编译期解析；owner doc §3a 示例代码（`registerAsDefault()` 用法）作为公共契约面。

**信心水平**：高（不对称性代码确凿；实际触发依赖非常规容器生命周期）。

**误报排除**：`functions` LinkedHashMap 的运行期读（`call()` :93）与启动期写（`register`）之间由线程启动建立 happens-before，非缺陷；owner doc "provider 本身不持资源"的声明覆盖默认实现，本发现针对的是关闭语义与静态解绑缺失而非资源持有本身。

**复核状态**: 已保留（P2——close 空壳、无 unregisterAsDefault/removeDefault 对偶、setDefault 替换不关旧值三点证实；owner doc `nop-stream-sql.md` :47 "provider 实现 AutoCloseable 由 bean 容器传播 close" 契约声明核实；单容器生命周期内不可触发，理论风险定级恰当。见文末「维度复核结论」。现状：新发现）

---

### [nop-stream-orchestration-thin-C6-004] RecordColumnAccess.GETTER_CACHE：per-Class 静态反射缓存无界、无逐出

**文件:行号**：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/RecordColumnAccess.java:36-37, 82-111`

**证据片段**：

```java
// RecordColumnAccess.java:36-37
private static final ConcurrentHashMap<Class<?>, Map<String, java.lang.reflect.Method>> GETTER_CACHE =
        new ConcurrentHashMap<>();
...
// RecordColumnAccess.java:82-86 —— 只有 put 侧，全仓无任何 remove/evict/上限
private static java.lang.reflect.Method getterFor(Class<?> clazz, String column) {
    Map<String, java.lang.reflect.Method> getters =
            GETTER_CACHE.computeIfAbsent(clazz, RecordColumnAccess::collectGetters);
    return getters.get(column);
}
```

**严重程度**：P2（理论风险，未具体化）

**并发场景/触发时序**：register/evict 不对称（C6.4 注册表对称性）：每个新 `Class` 键进入即永驻。泄漏链只有在"记录类动态生成且数量随运行期增长"时才能具体化（例如脚本/热部署派生的记录类族）——本单元全部已声明用法（SQL 查询的 bean 记录列访问）中记录类是编译期固定的有限集合，缓存规模 = 应用记录类型数，有界。因此无法写出包络内必然耗尽的具体链，按纪律降 P2 标注。

**现状**：无上限、无逐出、无 stats；`collectGetters` 结果（全类公有 getter 表）比单列查找大得多，一个类缓存整表。

**风险**：长驻进程 + 动态类场景下的老年代滞留；`ConcurrentHashMap` 本身线程安全，无并发缺陷。

**建议**：若不欲引入依赖，加 javadoc 有界性声明（与 `SqlRowAggregateOps.CACHE` :21-23 的"per pipeline 一条"声明同款式）即可消除审计歧义；或挂接平台既有弱引用缓存设施。

**失效面枚举**：`RecordColumnAccess.getterFor/collectGetters`；所有经 SQL 标量表达式访问 bean 记录列的编译产物（`StreamSqlExprCompiler.compileColumn` → `RecordColumnAccess`）。

**信心水平**：高（代码事实）；泄漏具体化可能性低（当前用法有界）。

**误报排除**：`SqlRowAggregateOps.CACHE` 不立案——其有界性已在 javadoc 显式声明（"the emitted body text is constant per compiled pipeline, so the cache is effectively unbounded by one entry per pipeline"，`SqlRowAggregateOps.java:21-23`），属"已声明的边界"。

**复核状态**: 已保留（P2——GETTER_CACHE 只进不出、无 remove/evict/上限证实（:36-37, :82-86）；当前记录类为编译期固定有限集合、缓存规模有界，无包络内必然耗尽链，理论风险定级恰当。见文末「维度复核结论」。现状：新发现）

---

### [nop-stream-orchestration-thin-C6-005] ReplayEngine.subscribe：无重复订阅防护——每次调用新起 emitter 线程，重复订阅即并发重放 + offset 互踩

**文件:行号**：`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java:125-131, 165-177, 205-211`（被覆写契约：`nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:72-80, 155-163`）

**证据片段**：

```java
// ReplayableCdcSourceFunction.java:126-131
@Override
public ICancellable subscribe(Consumer<ChangeEvent> action) {
    Thread emitter = new Thread(() -> runReplay(action), "fraud-s1-replay-cdc");
    emitter.setDaemon(true);
    emitter.start();                       // 无 started/subscribed 守卫；无订阅登记
    return new ReplaySubscription();
}
```

```java
// 被覆写的生产契约：DebeziumMessageSource.subscribe 允许多订阅并登记
public ICancellable subscribe(Consumer<ChangeEvent> action) {
    Subscription subscription = new Subscription(action);
    subscriptions.put(action, subscription);   // ConcurrentHashMap 登记、cancel 时移除
    startEngineIfNeeded();                     // 引擎只起一次
    return subscription;
}
```

**严重程度**：P2（理论风险，当前接线不可达）

**并发场景/触发时序**：生产基类的 `subscribe` 语义允许多订阅者（`dispatchEvent` 迭代订阅表广播）。`ReplayEngine` 把 `subscribe` 语义改写为"每次调用启动一个独立重放线程"，且无 `started` 门。若任何调用方按生产契约在同一引擎上 subscribe 两次：两个 emitter 并发迭代同一 `events` 列表 → 同一事件双发（下游 fraud 检测双计）；两者交错执行 `writeReplayIndex(i+1)`（:173, 205-211）→ 后写者回退先写者的 offset（线程 A 写 5 后线程 B 写 3）→ 任务失败恢复后从更小 index 重放已消费事件，破坏类 javadoc :37-40 声明的 "exactly-once source semantics, consistency REPLAYABLE"。

**现状**：当前唯一调用方（基类 `run` :260，每 run 每 subtask 恰一次，引擎实例每 run 新建）不会触发；`runEntered` CAS（基类 :227-229）挡住同实例双 run。故为契约面缺口而非可达缺陷。

**风险**：该类是测试双胞胎 + 升级路径样板（javadoc 自述"换 bean 即真实 CDC"），后续维护者按生产 `subscribe` 语义使用即触发；不防重复订阅也使 `subscribe` 返回的多个订阅共享同一个 `cancelled` 标志，任一订阅 cancel 会静默终止全部订阅的 replay。

**建议**：`ReplayEngine` 增加 `AtomicBoolean subscribed` 门——二次 subscribe typed fail-fast（与平台"connector-name fail-fast"风格一致），或复用基类订阅登记语义（登记 + `isCancelled` 隔离）。

**失效面枚举**：`ReplayableCdcSourceFunction$ReplayEngine.subscribe/stop`；`ReplaySubscription`（共享 cancelled 标志面）；任何按 `DebeziumMessageSource.subscribe` 生产语义调用该双胞胎的代码（含未来测试）。

**信心水平**：中高（契约偏离确凿；可达性判定依赖当前接线单一调用方的 grep 证据）。

**误报排除**：emitter 为 daemon 线程不阻 JVM 退出；cancel 不 interrupt 属协作式取消声明模式（与 `XplSourceFunction` javadoc 一致），park 时间有界（emitDelayMs/finishLingerMs 为构造参数显式声明），均非缺陷。

**复核状态**: 已保留（P2——subscribe 每次调用新起 emitter 线程、无 started/subscribed 门、多订阅共享同一 cancelled 标志三点证实；生产契约 `DebeziumMessageSource.subscribe`（登记 + startEngineIfNeeded）核实；当前唯一调用方（基类 run :260，每 run 恰一次）不可达，契约面缺口定级恰当。见文末「维度复核结论」。现状：新发现）

## 资源获取点配对表

| 获取点 | 释放点 | 成功路径 | 异常路径 | 取消/中断路径 | 超时/提前 return | 幂等/二次释放 | 判定 |
|---|---|---|---|---|---|---|---|
| `DirectoryFileSourceFunction.emitRemaining` FileInputStream（:159） | TWR 自动 close | ✔ | ✔（异常穿 TWR） | ✔（`cancelled` return :169-175 经 TWR） | ✔（cursor>=file return :153） | close 幂等 | 配对完整 |
| `ReplayEngine.subscribe` emitter 线程（:127-129） | runReplay finally `outer.cancel()` + 循环轮询 cancelled（:169, 185-189） | ✔ | ✔（finally 无条件） | ✔（轮询/park 有界退出；无 interrupt，协作式声明） | ✔ | 线程单次启动（无 subscribe 门 → C6-005） | 有界，附条件 |
| `ReplaySubscription.onCancelTasks`（:134-157） | cancel 触发执行 | —（触发面） | 单回调异常中断后续（C6-002③） | — | — | 非幂等 + 无同步（C6-002①②）；stop 绕过注册表 | 不对称 → C6-002 |
| `ReplayEngine`（每 run 新建，:97-105/258） | 基类 finally `source.stop()` + 置 null（connector :308-316） | ✔ | ✔ | ✔ | ✔ | stop() synchronized 可重入 | 配对完整（基类承载） |
| `subscription`（基类 run :260 / finally :299-307 / truncateForDrain :354-358） | cancel + 置 null | ✔ | ✔ | ✔ | ✔ | 生产 Cancellable 幂等；ReplaySubscription 非幂等（C6-002） | 基类完整/双胞胎偏离 |
| `DebeziumEngineWrapper` 失败监听器（基类 :248/:298） | unregister | ✔ | ✔（finally） | ✔ | ✔ | 按 connector name 键控 | 配对完整（单元外） |
| `NopStreamOffsetBackingStore.forConnector`（基类 :400/:413/:431） | first-run `clearConnector`（:440-444）；restore 路径不 clear（AR-03 语义：恢复即覆盖） | ✔ | — | — | — | 静态注册表按名覆盖 | 单元外，残留边界注明 |
| `FraudDetectionDemo` NFA/SharedBuffer（:110-115, 139-144） | `nfa.close()` 仅 happy path（:130, 162）；accessor TWR（:175） | ✔ | ✘（循环内抛出则 nfa 不 close） | — | — | — | 演示 main，校准排除 |
| `XplFunctionSupport.ROOT_SCOPE.newChildScope()`（:35-52，全部 Xpl* 函数/KeySelector/TapOperator 消费） | 随调用帧 GC（EvalScopeImpl parent 不持 child，nop-core :96-107） | ✔ | ✔ | ✔ | ✔ | 每调用新建，无共享可变态 | 配对完整 |
| `StreamScalarFunctionResolver.defaultResolver`（:42-44, 79-81） | 无（close 空壳） | — | — | — | — | register/close 不对称 | → C6-003 |
| `RecordColumnAccess.GETTER_CACHE`（:36-37, 84） | 无逐出 | — | — | — | — | 只进不出 | → C6-004 |
| `SqlRowAggregateOps.CACHE`（:27-38） | 无逐出；javadoc 声明每 pipeline 一条 | — | — | — | — | — | 已声明有界，非缺陷 |
| `DistinctAggregateWrapper.seen`（StreamSqlAggregation :611-638） | 窗口清除/作业生命周期；javadoc+owner doc 双声明 | — | — | — | — | — | 已声明边界（F4 归 C5） |
| `sideInputs` 快照（StreamModelDslBuilder :1526-1550） | 构建期一次物化，随函数实例存活 | ✔ | ✔（构建期异常即构建失败） | — | — | Supplier.get() 恰一次（声明契约） | 配对完整 |
| `DirectoryFileSourceFunction.cursors`（:68） | 进程内存态；snapshot/restore 全程 synchronized（:219-228, 252-254, 281-284）；空 restore 守卫（:275-280） | ✔ | ✔ | ✔ | ✔ | clear+putAll 原子于监视器 | 配对完整 |
| `runEntered` AtomicBoolean 门（DirectoryFileSourceFunction :79-80, 114-129；基类 :92, 227, 317） | finally 复位 | ✔ | ✔（finally） | ✔ | ✔（第二 subtask return） | CAS 门 + 复位 | 配对完整 |

## 覆盖度与残留边界

- **覆盖度**：98/98 文件纳入扫描（机器基线三节 C4/C6/closeable + 补充 grep 十类模式）；全部命中资源生命周期的文件人工全文阅读（fraud 21 文件中运行期 16 文件全文、5 个纯 POJO 经结构 grep 判定；flow 60 文件中运行期面 12 文件全文、其余为模型 POJO/构建期验证器经基线+grep 判定无资源；sql 17 文件中运行期面 8 文件全文、其余为构建期编译器经基线+grep 判定）。跨单元对照阅读（不作为发现对象）：`DebeziumCdcSourceFunction`、`DebeziumMessageSource`、`NopStreamOffsetBackingStore`、`Cancellable`、`EvalScopeImpl`——用于核定单元内代码的配对契约。
- **C6 面总体单薄**：单元内唯一文件 IO（1 处 TWR 覆盖）、唯一线程创建（1 处）、唯一 AutoCloseable（1 处空壳）都在 fraud-example；flow/sql 两模块经全量定位证实为构建期纯对象图代码，无运行期资源。
- **残留边界 1（单元外）**：`NopStreamOffsetBackingStore.forConnector` 静态注册表与 `DebeziumEngineWrapper` 失败监听器注册表的配对语义归属 nop-message/nop-stream-connector-debezium 单元，本轮仅核定调用侧配对完整，注册表本体未审。
- **残留边界 2（演示校准）**：`FraudDetectionDemo` 的 `nfa.close()` 不在 finally（异常路径 NFA/SharedBuffer 不清理）与 `DemoKeyedStateStore` 的"按名断连"状态语义，均属演示 main 的一次性进程形态，按误报校准规则不立案；若该 demo 被改造为长驻嵌入形态需重新评估。
- **残留边界 3（文档漂移，非缺陷）**：owner doc `nop-stream-user-guide.md` WI10 逐源边界矩阵（:129）仍将 `DirectoryFileSourceFunction` 标注为"非 REPLAYABLE source（如 fraud demo 的 DirectoryFileSourceFunction）"，而 live code `DirectoryFileSourceFunction.java:241-243` 返回 `REPLAYABLE` 且实现了 cursor checkpointing——代码语义已升级、文档示例未同步，属 docs bug（建议 owner 在 docs 维护任务中修正该行），不影响本轮任何发现的定级。
- **残留边界 4（产线 sink 侧）**：本单元不含 sink 连接器实现（`FileTwoPhaseCommitSink`/`JdbcTwoPhaseCommitSink` 属 nop-stream-connector 族单元）；S2 模型引用的 sink 生命周期不在本审计面。

## 维度复核结论（独立复核子代理）

- 复核子代理标识：C6 维度独立复核子代理（与初审盲审者不同角色，逐条重读 live code 验证；本节为 append-only 追加，不改前文）。
- 复核日期：2026-10-14。
- 复核输入：本文件（C6-001~005 + 16 项资源获取点配对表）、`C6-round2.md`（C6-006）、`C6-round3.md`（C6-007）、`C6-round4.md`（0 新增 + 闭合矩阵 + 终止声明），全文完整读取。
- 复核方法：逐条发现重读 live code 核对文件/行号与证据真实性，独立推演泄漏链/生命周期链与可达性；关键争议点（实例复用契约）下钻至运行时三层代码锚点（接口声明 → sharded 复制分支 → 序列化往返实现）。
- 复核纪律遵守：仅读取 ai-dev/audits/ 下同单元 4 份 C6 文件 + 同 campaign C3/C1 文件的维度复核结论节（用于 C6-001 与 C3-001、C6-007 与 C1-003 的跨维度一致性核对）+ `cra-index.md` + git log；未读取 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/。历史现状对照结论：WI 1.1（nop-stream-core-runtime）/WI 1.2（nop-stream-state-connectors）审计目录与 cra-index 台账均无本单元任何类的登记（WI 1.2 明确将 fraud-example 登记为"范围外，登记供该模块审计引用"）；基线 b3a9c76bed 之后无触及本单元 src/main 的提交（唯一 stream 提交 8dcfa4cd0c 为 connector-kafka，不触及本单元），工作区对本单元无未提交改动。

### 逐条判定表

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C6-001 | P1 | **降级 → P2** | 同一代码点同一失效层，与 C3-001 复核结论同向对齐：collect 异常主触发已被父类订阅 lambda 的 pendingError 通道（DebeziumCdcSourceFunction.run :260-277）否证，残余触发面（readReplayIndex NFE / writeReplayIndex）为内存 CHM store + 索引自产自销的边缘路径，按本 campaign 校准先例（C3-002"罕见但可具体化→P2"）定 P2；catch-all+cancel 伪装 EOS、"fails loudly" 注释与语义相悖、pendingError 通道可用而未用三项结构缺陷成立，不驳回 | 新发现（与 C3-001 为同一代码点的同族面，合并处置归 7.1 裁决；无历史登记、无修复提交） |
| C6-002 | P2 | **保留** | cancel 非幂等/ArrayList 无同步/无逐回调隔离三点逐一证实，平台 `Cancellable`（:44-51, :115-123）契约基准核实；注册表恒空、今日无实际泄漏，理论风险定级恰当 | 新发现，未修复 |
| C6-003 | P2 | **保留** | close() 空壳、无 unregisterAsDefault 对偶、setDefault 替换不关旧值三点证实；owner doc `nop-stream-sql.md` :47 AutoCloseable 传播契约声明核实；单容器内不可触发，理论风险定级恰当 | 新发现，未修复 |
| C6-004 | P2 | **保留** | GETTER_CACHE 只进不出、无逐出（:36-37, :82-86）证实；当前记录类编译期固定、缓存有界，理论风险定级恰当 | 新发现，未修复 |
| C6-005 | P2 | **保留** | subscribe 每调用新起线程、无 started 门、多订阅共享 cancelled 标志证实；生产契约（subscriptions 登记 + startEngineIfNeeded，DebeziumMessageSource :72-80）核实；唯一调用方不可达，契约面缺口定级恰当 | 新发现，未修复 |
| C6-006 | P1 | **驳回** | 载重前提"region restart 复用同一 source 实例"被证伪：该类经继承实现 SubtaskShardedSourceFunction，全部建链/重建路径经 serializationRoundTripCopy 逐 run 产出全新实例，残留 emitter 的 finally-cancel 只写旧实例、无法跨 run 投毒（反证链见下） | 不适用（驳回；衍生的单元外 docs bug——基类 javadoc :230-231 "shares the source function" 对 sharded 基类自身不准确——移交 docs 维护，不属本单元立案对象） |
| C6-007 | P1 | **保留** | 证据链逐环成立：cancelled 非 transient 唯一写点无复位、teardown 必经 cancel、非 sharded → deepCopy 共享同一实例、重启 run 循环顶零事件 EOS 假成功；不需交叠窗口、纯顺序陈旧即可触发，P1（半个成功）定级成立 | 新发现，未修复 |

### 关键裁定证据锚点

1. **C6-001 降级的维度自洽性**：C3-001 复核（`C3-async-chains.md` 维度复核结论节）已裁定：主触发（collect 异常伪装 EOS）被父类 lambda 否证；残余面（readReplayIndex NFE / writeReplayIndex）真实但边缘。C6-001 的具名触发链（步骤② readReplayIndex NFE）正是该残余面——同一机制、同一失效层，故同向降级 P2。C6 维度视角（资源生命周期/半个成功）不改变可达性与校准。另注一处初审枚举瑕疵（按纪律不改条目原文）：风险节"emitter 内 toChangeEvent 转换异常"不成立——toChangeEvent 在 `createMessageSource`（:100-103）内执行，而 createMessageSource 由基类 run() :258 在 try 块内调用，异常正确传播使任务 FAILED，不在被吞面内；该瑕疵不影响残余具名触发面承载的降级结论。
2. **C6-006 驳回反证链**（三级锚点）：① 接口声明——`DebeziumCdcSourceFunction implements SubtaskShardedSourceFunction<ChangeEvent>`（:59-61），`ReplayableCdcSourceFunction extends DebeziumCdcSourceFunction`（fraud :46）经继承使运行时 `instanceof SubtaskShardedSourceFunction` 为 TRUE；② 复制分支——`OperatorChain.deepCopy(TaskLocation)`（:274-280）→ 接口默认 `copyForSubtask(location)` → `copyForSubtask(location.getTaskIndex())`（StreamOperator :234-235）→ `StreamSourceOperator.copyForSubtask(int)` sharded 分支（:240-246）；③ 全新实例——`DebeziumCdcSourceFunction.copyForSubtask(int)`（:524-530）调 `serializationRoundTripCopy`（DebeziumSourceSupport :188-204，真 Java 序列化往返），每次部署/重建产出全新对象图。src/main 全部建链/重建调用点共 3 处（GraphExecutionPlan :454、RemoteGraphExecutionPlanBuilder :313、SupervisionLoop :847），无一绕过该分支（remote 侧 taskIndex==0 亦强制 copy，R5 REG-1）。残留 emitter 经 `ReplayEngine` 内部类的外部类引用绑定旧实例，finally-cancel 写旧 running/旧 latch/已置 null 的旧 subscription/source——新 run 在新副本上执行且入口复位四件套，毒杀链不可达。第 4 轮 §2.7"单元内 grep SubtaskShardedSourceFunction 零命中→全部落入 legacy shared-instance 语义"对 ReplayableCdcSourceFunction 成员判定有误：文本 grep 看不见继承接口；该误判使第 2~4 轮未检验 sharded 分支对本类可达性的影响。被审计方最强反驳即上述 ①-③，初审方无可用于维持 P1 的反证。
3. **C6-007 与 C1-003 复核修正的跨维度一致性**：C1-003 复核修正的是其触发时序——LOCAL `restartRegion` Phase 2 `waitForTerminal` 保证旧 attempt 的 `finally{set(false)}` 先于新 attempt `run()`，故"守卫非粘性零发射"需经分布式 `deployTask` 槽位替换的交叠窗口具体化（P2 竞态校准）。C6-007 不依赖交叠：`waitForTerminal` 的先行关系恰是 teardown `cancel()`（经 closeOperatorChains → StreamSourceOperator.close :392-400）happens-before 新 run() 读 `cancelled` 的保证——纯顺序陈旧即可触发，且两条重建路径（LOCAL SupervisionLoop :847 与 embedded-distributed deployTask → SubtaskPlanBuilder → RemoteGraphExecutionPlanBuilder :313）对本类（非 sharded）共享同一实例。C1-003 修正反而强化了 C6-007 的实例共享前提；两者为同文件不同机制（runEntered 竞态 vs cancelled 陈旧），P2/P1 判级差异由机制强度决定（竞态 vs 确定性顺序触发），无不自洽。

### 不确定项（显式列出）

1. C6-001：writeReplayIndex 实际不可抛的判定基于 `NopStreamOffsetBackingStore` 静态 CHM 内存结构（:59-63，本轮独立核实）；"外部损坏 checkpoint/replay index 状态"的具体注入面（手工编辑/存储损坏）未做运行时演练——P2 定级按理论具体化口径成立，若未来 offset store 落盘则需重评。
2. C6-007：S2 模型（`fraud-s2-file*.stream.xml` 等）的具体 region 拓扑未逐模型重验——"源任务失败/被取消后落入可重启 region"依赖 SupervisionLoop 通用路径锚点（:334-349 失败触发 + maxRestartsPerRegion 预算 + Phase 3 非 COMPLETED 即重建），机制成立但具体模型接线未核对。
3. C6-006 驳回无残留不确定：三级锚点（①②③）均为 src/main 代码事实，且 serializationRoundTripCopy 对 ReplayableCdcSourceFunction 的可序列化性由其已在生产部署路径实际运行佐证。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C6-007 | P1 | `nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/DirectoryFileSourceFunction.java` | cancel 置位的 volatile cancelled 跨 run 永不复位（非 transient、唯一写点 :232、无入口复位），区域重启按运行时契约共享同一实例（非 sharded → deepCopy 共享）时，重启 run 循环顶零事件静默 EOS 假成功——违反本类 REPLAYABLE 恢复声明，重试预算一次未消耗即无声数据截断 | 新发现，未修复 |
| C6-001 | P2（降级） | `nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java` | runReplay catch-all+finally-cancel 将 emitter 线程引擎侧失败（replay index 解析/store 写入）伪装为干净 EOS，绕开父类可用而未用的 pendingError 通道；主触发（collect 异常）已被父类 lambda 覆盖，与 C3-001 复核同向降级 | 新发现，未修复（与 C3-001 同代码点同族面，合并处置归 7.1） |
| C6-002 | P2 | 同上文件 | ReplaySubscription 取消回调注册表三重偏离平台 Cancellable 契约（cancel 非幂等 / ArrayList 无同步 / 无逐回调隔离）+ stop() 绕过注册表的语义不对称；当前注册表恒空，无实际泄漏 | 新发现，未修复 |
| C6-003 | P2 | `nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamScalarFunctionResolver.java` | 静态 default 只注册不清除——close() 空壳无解绑、setDefault 替换不关旧值；多容器/热重启场景 stale-provider 与 use-after-close 理论面 | 新发现，未修复 |
| C6-004 | P2 | `nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/RecordColumnAccess.java` | GETTER_CACHE per-Class 静态反射缓存无界无逐出；当前记录类编译期固定有界，动态类族场景理论滞留 | 新发现，未修复 |
| C6-005 | P2 | `nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java` | ReplayEngine.subscribe 无重复订阅防护——每次调用新起 emitter 线程、多订阅共享 cancelled 标志，偏离生产 subscribe 登记契约；当前唯一调用方不可达 | 新发现，未修复 |

（C6-006 已驳回，不列入本表。）

**复核统计**：7 条全裁——保留 5（P1 × 1、P2 × 4）、降级 1（C6-001 P1→P2）、驳回 1（C6-006）。最终口径：P1 × 1（C6-007）、P2 × 5（含降级并入的 C6-001）。现状标注：6 条保留/降级项全部为**新发现、未修复**（WI 1.1/WI 1.2 无同发现登记，cra-index 无本单元条目，基线后无触及本单元的提交）。

**需子项复核的高风险项**：C6-007——修复方案（run 入口复位 cancelled vs 代际守卫）触及与 C1-003（P2，触发路径已修正为分布式 deployTask 槽位替换）同文件的跨 run 实例复用契约面；回归测试须新增"cancel 后对同一实例再 run 断言全量重发"用例，且修复须在 LOCAL SupervisionLoop 与 embedded-distributed deployTask 两条重建路径上同时验证后再收口。
