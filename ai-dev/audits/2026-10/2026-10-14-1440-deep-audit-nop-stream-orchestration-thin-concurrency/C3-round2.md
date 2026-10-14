# C3 异步链路正确性 — nop-stream-orchestration-thin 第 2 轮深挖（盲审）

- 审计单元：`nop-stream-orchestration-thin`（`nop-stream-flow` + `nop-stream-sql` + `nop-stream-fraud-example`，src/main/java）
- 维度：C3 异步链路正确性 第 2 轮（第 1 轮：C3-001~007，见同目录 `C3-async-chains.md`）
- 审计日期：2026-10-14；分支：concurrency-audit-remediation；盲审（未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件，唯一例外为本维度第 1 轮文件）
- 本轮深挖方向：C3-001 失效面同族清扫（F3）、xpl/eval 通道 IContext/ThreadLocal 传播核对、取消/interrupt 逐入口核对、时序假设（初始化先于首个请求 / 恢复先于运行）抽验、第 1 轮移交边界中归属本单元的回调/生命周期面复点

---

## 1. 本轮发现

### [nop-stream-orchestration-thin-C3-008] DirectoryFileSourceFunction.initializeState 的 REPLACE 语义（cursors.clear()）在 region 重启 Phase-3 提交-继续编排下可与仍在运行的兄弟子任务 run() 交错——已推进游标被清零回检查点位，窗口内记录重发（重复发射，违反 REPLAYABLE 声明）

**文件**：`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/DirectoryFileSourceFunction.java` L261-285（initializeState）、L68（共享 cursors）、L41-44（javadoc 声明）

**证据片段**（L274-284）：

```java
Map<String, Long> restored = (Map<String, Long>) raw;
if (restored.isEmpty()) {
    // A subtask whose snapshot carried no cursors must NOT wipe cursors a
    // sibling subtask already restored into the shared map (a scale-up
    // subtask restores empty state).
    return;
}
synchronized (cursors) {
    cursors.clear();
    putAllCursors(restored);
}
```

配套共享契约（同类 L41-44、L64-68；及跨模块只读证据）：

```java
// DirectoryFileSourceFunction.java L41-44
 * Because the runtime shares one source-function instance across the subtasks of the
 * source vertex, every subtask snapshots the SAME full cursor map; on restore a subtask
 * only applies non-empty restored state, so whichever subtask wins the read race
 * resumes from the checkpointed cursors (deterministic under parallelism rescale).
```

**严重程度**：P1（罕见但可具体化竞态；效果为恢复后静默重复发射——对 REPLAYABLE/STRICT_EXACTLY_ONCE 声明是正确性违约）

**并发场景**：本类按 javadoc 契约**跨子任务共享单一实例**（`StreamSourceOperator.copyForSubtask()` 对非 `SubtaskShardedSourceFunction` 直接传同一 sourceFunction 引用——nop-stream-core `StreamSourceOperator.java` L227-247）。共享 `cursors` map 因此同时暴露给：兄弟子任务的 `run()` 读者/推进者（`advanceCursor`，L224-228）、兄弟子任务 `initializeState` 的 clear+putAll 写者、checkpoint `snapshotState` 快照读者。`synchronized(cursors)` 只保证**结构一致性**，不保证**语义**：非空 restore 的 REPLACE（clear→putAll）会抹掉兄弟子任务已推进但尚未 checkpoint 的游标。

**触发时序**（已按 live code 具体化；初始部署路径被证明安全，窗口仅在 region 重启编排）：

- 前置：source 顶点 parallelism ≥ 2（DSL Item 29 支持每 transform 声明 parallelism），存在已完成 checkpoint 且 cursors 非空（至少消费过一行），source region 内某任务失败触发 region-scoped restart。
- T_sup（监督线程，`SupervisionLoop.restartRegion` Phase 3，nop-stream-runtime L546-574）：`rebuildTask(task-0)`（deepCopy 后经 `resolveConsistentCutEpochAndRestoreOperators` → `restoreOperatorsFromState` → `StreamSourceOperator.restoreState` → 本类 `initializeState`（非空）→ clear+putAll 为 cp 状态）→ **`executor.submitTask(task-0)` 异步提交立即返回**（nop-stream-core `TaskExecutor.submitTask(SubtaskTask)` L206-221，`executorService.submit` 不等待）→ 循环继续 `rebuildTask(task-1)`。
- T_exec0（任务线程）：task-0 `invoke()` → `operatorChain.open()` → `sourceOp.run()` → `runEntered` CAS 胜出 → `emitRemaining` 逐行发射并 `advanceCursor(f1, 5000)`（已推进越过 cp 位点）。
- T_sup：task-1 的 rebuild → restore（task-1 的快照携带**同一份非空** cursor map——"every subtask snapshots the SAME full cursor map"）→ 本类 `initializeState` 再次执行 `synchronized(cursors){ cursors.clear(); putAll({f1: 0}) }` —— **此时 T_exec0 正在 run()**。
- T_exec0：下一个 `currentCursor(f1)`（L218-222）读到 0 → 从 cp 位点重发 [cp, 5000) 区间已发出的行 → **重复发射**；且 task-1 随后 run() CAS 落败静默退出（L114-115），无人察觉。

Phase 1/2（L512-532）先取消并等待 region 内全部任务终态，故 clear 不会撞上仍在 CANCELING 的任务；窗口严格来自 Phase 3 "提交一个、继续恢复下一个" 的流水线化。

**现状**：`restored.isEmpty()` 守卫（L275-280）只防"空状态抹掉兄弟**已恢复**的游标"（restore-vs-restore 次序），未防"非空 restore 抹掉兄弟**已运行推进**的游标"（restore-vs-run）。初始本地部署路径经核对安全：`GraphModelCheckpointExecutor.executeWithCheckpointSkeleton` 中 restore 阶段（L256 `restoreFromCheckpoint`）严格先于 `buildTasks`/`submitAndRun`（L274-279），restore 全部完成后才提交任何任务。

**风险**：恢复场景下 [checkpoint, wipe 点] 区间行被重发一次以上；下游为 keyed 富化/聚合时重复计数。S2 源声明 `getSourceConsistency() == REPLAYABLE` 且 javadoc 明示 "deterministic under parallelism rescale"（L239-243、L41-44）——本窗口使该声明在 region 重启 + 并行 > 1 组合下失真。同族外溢面（不在本单元，移交）：父类 `DebeziumCdcSourceFunction.initializeState` → `NopStreamOffsetBackingStore.setOffsets` 的 REPLACE（`data.clear()`，nop-message-debezium L194-205）在共享静态注册表 map 上存在同一 restore-vs-run 窗口；因回放引擎不回读 index，直接效果弱化为"offset 短暂回退、若恰在其间做 checkpoint 则下次恢复重放"。

**建议**：三选一（任一即闭合）：① restore 侧放弃对"运行中共享 map"的 REPLACE——initializeState 仅在 `runEntered` 为 false（实例静默）时执行 clear+putAll，否则拒绝/告警 typed；② 本类持有 checkpoint 代次（epoch）标记，restore 时仅接受 ≥ 当前代次的状态并跳过对已运行实例的覆盖；③ 编排侧（nop-stream-runtime，移交）：region 重启 Phase 3 改为"全部 rebuild+restore 完成后再统一 submit"，消除 restore-vs-run 流水线窗口（该修改同时闭合 CDC 偏移的兄弟面）。

**失效面枚举**：`DirectoryFileSourceFunction` 全部使用面 = S2 场景 `fraud-s2-file.stream.xml` 的 directory source bean（resources/_vfs/nop/stream/demo/）、owner doc `nop-stream-user-guide.md`「实例锚点」S2 行、「逐源边界矩阵」directory 行；触发前置 = source 顶点 parallelism ≥ 2 + 非空 cursor checkpoint + source region 内 region-scoped restart（`SupervisionLoop.restartRegion`，shipped 恢复路径）。兄弟面（移交）：`DebeziumCdcSourceFunction`/`NopStreamOffsetBackingStore.setOffsets`（connector-debezium / nop-message 单元）。无其他实现者（无子类）。

**信心水平**：机制与交错 high（共享契约、异步 submit、restore 链路均为本轮逐文件核对的 live code）；现实触发概率 medium-low（需并行>1 + 失败重启组合；shipped S2 场景 pipeline parallelism="1" 不触发）——按"罕见但可具体化竞态"定 P1 而非 P2；按 demo 校准未升 P0。

**误报排除**：非"演示性简化"——本类是 production-form 的 REPLAYABLE source（javadoc 显式声明 exactly-once 契约），缺陷是该契约在合法编排下的正确性违约；`synchronized(cursors)` 已正确覆盖结构一致性，本条不针对数据竞争性撕裂（F1 的结构面）而针对 REPLACE 语义与运行中读者的交错；初始部署路径已核对安全，不扩大指控面。

**复核状态**： 已保留（P1，机制修正——wipe 可达性三要素全部证实（copyForSubtask() 对非 SubtaskSharded 共享 source 实例、Phase-3 rebuild→restore→异步 submit 流水线、非空守卫只防 restore-vs-restore）；但发现内"同 run 内下一个 currentCursor 重读→重发 [cp,5000)"机制不成立：currentCursor 每 emitRemaining 仅读一次（L152）、发射由本地 offset 驱动、in-flight 文件游标被 wipe 后由下一次 advanceCursor 自愈；实际违约路径（复核重建）= 已完成/已推进文件游标被清零后在余下运行期持续滞后→后续 checkpoint 携带落后于同 epoch 下游进度的源位点→恢复重放已反映窗口（键控聚合重复计入）；补充前置：task-1 须在重启时非 COMPLETED（F1 分支跳过已完成任务），CAS 落败者启动后微秒级终态，现实窗口要求 task-1 尚未起跑（线程池排队/饥饿可达）。仍满足"罕见但可具体化 + REPLAYABLE 契约违约"→ 维持 P1。见 C3-async-chains.md 文末「维度复核结论」）

---

### [nop-stream-orchestration-thin-C3-009] UserHistoryEnricher 的 keyed-state 访问依赖"每子任务独立 RuntimeContext"假设，而 ProcessOperator 跨子任务共享同一 UDF 实例且各子任务 open() 互不同步地覆写共享实例的 RuntimeContext——parallelism ≥ 2 时系统性收敛到单一 keyed backend，跨子任务 keyed 状态污染（本轮纠正第 1 轮排除项 6 的事实基础）

**文件**：`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/UserHistoryEnricher.java` L47-55（懒初始化）、L51、L62（getRuntimeContext 消费点）

**证据片段**（L47-55、L61-62）：

```java
private transient ValueState<String> lastCityState;

private ValueState<String> lastCityState() {
    if (lastCityState == null) {
        KeyedStateStore store = getRuntimeContext().getKeyedStateStore();
        lastCityState = store.getState(LAST_CITY_STATE_DESC);
    }
    return lastCityState;
}
...
UserTransactionHistory history =
        new UserTransactionHistory(getRuntimeContext().getKeyedStateStore());
```

跨模块只读证据（根因链，全为 live code）：

```java
// nop-stream-core ProcessOperator.java L42-44 —— 共享契约（javadoc 明示）
public ProcessOperator<IN, OUT> copyForSubtask() {
    return new ProcessOperator<>(userFunction);   // 同一 UDF 实例传给每个子任务
}
// ProcessOperator.java L92-102 —— 每个子任务 open() 把自己的 backend 装进共享上下文
if (userFunction instanceof RichFunction) {
    ...
    src.setKeyedStateStore(keyedStateBackend);    // 本子任务的 keyedStateBackend
    src.setTimerService(userTimerService);
}
// nop-stream-core AbstractUdfStreamOperator.java L72-74
FunctionUtils.setFunctionRuntimeContext(userFunction, new StreamingRuntimeContext());
// → FunctionUtils.java L51-56 → AbstractRichFunction.setRuntimeContext：普通字段写，无同步
```

**严重程度**：P1（P0 邻接：parallelism ≥ 2 的并行部署下为**系统性**数据污染而非窄窗口竞态；shipped 场景 parallelism="1" 不触发，且根因载体在单元外，按单元校准定 P1）

**并发场景**：S1 `<process id="enrich" bean="userHistoryEnricher"/>`（fraud-s1-cdc.stream.xml L46）→ `AdvancedTransforms` L933 `keyed.process((KeyedProcessFunction) fn)` → `KeyedStreamImpl` L211 `new ProcessOperator<>(processFunction)`。`ProcessOperator.copyForSubtask()` 显式声明"the user process function is shared across subtasks"——N 个并行子任务的算子副本持有**同一个** `UserHistoryEnricher` 实例。每个子任务在自己的任务线程上执行 `open()`：`AbstractUdfStreamOperator.open()` 向共享实例**无同步地覆写** `runtimeContext`（普通 transient 字段），随后 `ProcessOperator.open()` 向该（最后写入者的）context 装入**本子任务**的 keyedStateBackend。

**触发时序**（parallelism = 2，普通声明式配置）：

- T0/T1（两个子任务线程，并发）：各自 `open()` → 交替执行共享实例上的 `setRuntimeContext(new StreamingRuntimeContext())` 与 `src.setKeyedStateStore(backend_i)` —— 两次无同步的字段写互相竞争，且可能观察到部分装配的 context；终态为 last-writer-wins（设 backend_1 胜出）。
- 稳态：T0 处理 user-alice、T1 处理 user-bob，二者的 `getRuntimeContext().getKeyedStateStore()` 返回**同一个** backend_1 → 两个线程在同一 MemoryKeyedStateBackend 上交替 `setCurrentKey` 并读写 ValueState → alice 的 count/total/lastCity 写到 bob 的键槽（或反之）——**键控状态跨键、跨子任务静默污染**；T0 在 T1 open 完成前已开始的处理流，其 context 还会被运行中换底。
- 附带：`lastCityState` 懒初始化（check-then-act，无同步）在同一共享实例上被两线程并发首触（该点被本条主导，不单独立案）。

**现状**：第 1 轮排除项 6 以"子任务实例不跨线程共享"豁免了本类——本轮核对 `ProcessOperator.copyForSubtask` 证明该前提对 ProcessOperator 包裹的 UDF **不成立**（StreamMap/StreamFilter 同样共享 UDF，但其包裹的 Xpl 包装器无状态故无碍；含状态的 RichFunction UDF 是暴露面）。`getRuntimeContext().getKeyedStateStore()` 是该平台 KeyedProcessFunction 访问键控状态的**唯一合法通道**，即任何含状态 keyed-process UDF 在并行 > 1 下均受同一根因影响。

**风险**：富化语义系统性失真（用户历史均值/前城错串），欺诈检测误报/漏报；状态错串会被 checkpoint 持久化放大。根因修复面在 nop-stream-core（ProcessOperator 不应共享含状态 RichFunction，或应提供每子任务 RuntimeContext），影响所有 keyed-process UDF——建议以独立条目移交 nop-stream-core 单元审计。

**建议**：单元内（防御性）：`UserHistoryEnricher` 在 `open()`/`initializeState` 一次性解析并缓存本子任务 state handle（而非逐记录 `getRuntimeContext()`），可消除运行中换底窗口但**不能**消除共享 backend 本身；真正的修复在根因侧：`ProcessOperator.copyForSubtask` 对 `RichFunction` UDF 改为序列化深拷贝（或 `setRuntimeContext` 前置到每个子任务独有实例），并让 `open()` 的 context 装配只作用于本子任务副本。

**失效面枚举**：本单元 = `UserHistoryEnricher`（S1 enrich 节点，唯一 KeyedProcessFunction UDF）；同型 UDF 面核查：`FraudAlertPatternFunction`/`TransactionWindowAggregate`/`AlertCountAggregate`（无 runtime-context 依赖，纯 accumulator 语义，不受影响）、全部 Xpl 包装器（无状态）。根因外溢面（移交 nop-stream-core 单元）：`ProcessOperator` 全部含状态 RichFunction UDF 使用面 + `AbstractUdfStreamOperator.open` 对共享函数的 context 覆写 + `StreamMap`/`StreamFilter` 的共享契约（对无状态 UDF 无害）。触发前置 = 任一 keyed-process transform 声明 parallelism ≥ 2（DSL Item 29 支持）。

**信心水平**：机制 high（共享契约、open 覆写、唯一合法状态通道三段均为逐文件核对的 live code，且共享契约有 javadoc 明文自证）；严重度校准 medium（单元内 shipped 场景 parallelism=1 不触发；平台级并行部署则为确定性污染）。

**误报排除**：非"无状态单例并发调用"误报校准——`UserHistoryEnricher` 是**有状态**（transient ValueState 缓存 + 平台挂接的 keyed backend）且共享契约由 `ProcessOperator` javadoc 明文承认"may capture external state"；非 fraud-example 演示性简化——S1 keyed-state 富化是 owner doc「实例锚点」S1 行声明的 production-form revival（类 javadoc L27-30 自证）；不与 C3-008 重复（C3-008 是 source 恢复编排窗口，本条是 process UDF 共享契约，不同类、不同载体、不同交错）。

**复核状态**： 已保留（P1——全链路 live code 证实且强于发现陈述：即使两次 open 完全顺序无交错，共享实例终态仍收敛 last-writer-wins 的 context+backend，subtask-0 以 subtask-1 的 backend 处理记录，属系统性缺陷而非纯竞态；shipped 场景 parallelism="1"（fraud-s1/s2 XML 实核）不触发，单元校准 P1 正确。见 C3-async-chains.md 文末「维度复核结论」）

---

## 2. 排除项/定向核查

本轮按深挖指令逐方向核对，以下各项**核对为安全/不立案**，附理由（防止后续轮次重复立案）：

1. **F3 同族清扫（C3-001 失效面扩展）：fraud-example 全部 catch 点**——`grep -rn "catch"` 全模块仅 5 处：`ReplayableCdcSourceFunction` L181/L183（= C3-001 本体）、`UserHistoryEnricher` L73/L80（IOException → **rethrow** IllegalStateException，正确传播，无 catch-and-continue）、`FraudDetectionDemo` L95（catch → LOG + **rethrow**，"fail loudly (item 11 FX-2)" 注释自证）。`CdcChangeDecoder`/`TransactionLineParser` 无 catch。**本单元不存在 C3-001 之外的第二处 catch-and-cancel / catch-and-continue**。
2. **offsetStore 跨线程面（ReplayEngine emitter 线程 `writeReplayIndex` vs 任务线程 `snapshotState`/`getOffsets`）**：`NopStreamOffsetBackingStore` 底座为 `ConcurrentHashMap<ByteBuffer,ByteBuffer>`，set/get/快照均 copy-on-write/read（L165-178、L211-218）——交错安全，不立案（`setOffsets` 的 clear+putAll REPLACE 属 C3-008 已移交的兄弟面，其数据面为 CHM 无结构撕裂）。
3. **DirectoryFileSourceFunction 取消响应性（C3-007 同族抽验）**：`run()`/`emitRemaining` 执行在**任务线程**上；`SubtaskTask.cancel()`（nop-stream-core L163-167）对执行线程 `t.interrupt()` → `emitLine` 的 `Thread.sleep(lineDelayMs)` 立即以 InterruptedException 退出（签名 `throws InterruptedException` 正确上抛）。幽灵窗口仅一个 8192 缓冲迭代内（微秒级）。与 C3-007 有本质区别：C3-007 的缺陷载体是**无人持有引用、无人可中断的分离 emitter 线程**；本类线程可被中断，不立案。
4. **xpl/eval 通道 IContext/ThreadLocal 传播**：三模块 src/main/java 对 `IContext|ThreadLocal|ContextStack` **零命中**（grep 核对；Stream 的 SourceContext/ProcessFunction.Context 为运行时记录上下文，非平台 IContext ThreadLocal）。全部 xpl 包装器（XplMap/Filter/FlatMap/Sink/Reduce、EvalActionKeySelector、JoinSideTagFunction、SideOutputTapOperator）以显式 `IEvalScope` 传参、IContext 位传 `null`，求值沿 `ROOT_SCOPE.newChildScope` 只读父链（第 1 轮排除项 1 已核）——**不存在跨线程 IContext 丢失/串号路径**，方向闭合。
5. **XplFlatMapFunction collector**：`out` 为每次调用的参数（L50-53），无共享字段复用；bindings 共享 map 的可变性已由 C3-003 覆盖，无新面。
6. **StreamSqlAggregation 累加器（含 DistinctAggregateWrapper 的 seen-map，L599-639）**：`createAccumulator()` 每键/每窗新对象；函数实例字段全 final（arg 评估器不可变）；`add/merge` 只变异累加器入参——无状态单例并发调用，误报校准豁免。`merge` 回放丢失侧记录的语义（L632-637）为键控单线程面，无并发窗口。
7. **SqlRowCompositeFunction / StreamRecordEvaluator / EvalActionKeySelector / JoinSideKeySelector / JoinSideTagFunction / SideOutputTapOperator**：字段全 final 不可变，逐记录 fresh child scope；`SideOutputTapOperator.matches` 的条件异常直接从 `processElement` 传播（无吞）；`copyForSubtask` 返回新实例（condition 共享但不可变）。安全。
8. **RecordColumnAccess Map 分支 containsKey+get 两步**（L53-58）：记录对象在单子任务内单线程流动，应用侧并发改写记录 map 属应用违约，非单元缺陷面；bean 分支与 GETTER_CACHE 已由 C3-005 覆盖。
9. **SqlRowAggregateOps.CACHE 的 computeIfAbsent 回调**（L33-38）：回调为纯内存构建（`fromJson` + `buildComposite`，JSON 解析 + 评估器组装，无可触发类加载的反射内省）——与 C3-005 的"锁内类加载"形态不同，CHM 用法正确。
10. **StreamScalarFunctionResolver.close()**（L74-76）：no-op 子类钩子，**不**清空静态 default——不存在"close 后运行中 UDF 调用失败"的关闭竞态面；`setDefault`/`registerAsDefault` 的 volatile 换绑安全（C3-002 已计实例 map，静态面无新问题）。
11. **Bean 解析器三件套**（BeanContainer/GlobalBean/InMemoryBeanFunctionResolver）：resolve/contains 仅在**构建期**被 `StreamModelDslBuilder` 调用（source/sink/UDF/sideInput bean 物化），无逐记录运行期读者面；`InMemoryBeanFunctionResolver` 的裸 `LinkedHashMap`（L27）遵循 register-then-start happens-before，不构成 C3-002 同型运行期竞态（C3-002 的特殊性恰在其 `call()` 是生成 xpl 体的逐记录入口）。
12. **初始部署 restore-vs-run 时序**：`GraphModelCheckpointExecutor.executeWithCheckpointSkeleton`（nop-stream-runtime L248-279）次序为 registerTasks → **restoreFromCheckpoint（全部子任务 restore 完成，单线程顺序）** → buildTasks → submitAndRun——初始路径无 restore 与 run 的重叠，C3-008 的窗口不适用于该路径（发现内已限定）。
13. **region 重启 Phase 1/2**：全部 region 任务先取消并等终态（SupervisionLoop L512-532）——clear 不会撞上取消中的任务；仅 Phase 3 提交-继续流水线化构成窗口（已并入 C3-008 触发时序）。
14. **剩余 fraud-example 类静态面**（FraudAlertPatternFunction、TransactionWindowAggregate、AlertCountAggregate、TxSummaryRow、AlertSummaryRow、CdcChangeDecoder、TransactionLineParser、EnrichedTransaction、UserTransactionHistory、MockTransactionGenerator）：仅 serialVersionUID/ValueStateDescriptor 常量，无可变静态状态；第 1 轮排除项 6/7 对这些类维持成立（UserHistoryEnricher 的豁免理由由 C3-009 纠正）。
15. **fraud-example 处置对照**：本轮两个发现均满足"暴露生产代码缺陷或违反 owner doc 已声明契约"立案门槛——C3-008 违反类自身 REPLAYABLE/"deterministic under parallelism rescale" javadoc 契约；C3-009 是平台唯一合法 keyed-state API 在声明契约下的失效，二者均非演示功能性简化。

---

## 3. 终止声明

C3 维度第 2 轮深挖完成。第 1 轮 E1-E10 异步入口全量清单之外，本轮已补齐：生命周期/恢复回调面（initializeState/restoreState/copyForSubtask 共享契约）、取消/interrupt 逐入口响应点（含任务线程中断语义）、IContext/ThreadLocal 传播方向（零命中闭合）、F3 catch 同族清扫（全 rethrow 闭合）、时序假设抽验（初始部署 restore→run 次序、region 重启 Phase 1-3 次序均落到 live code 行号）。

**新增发现 2 项（C3-008、C3-009，均 P1）后，未发现新的高价值问题。C3 维度深挖终止。**

遗留移交（非本单元立案对象，已写入对应发现的失效面枚举）：
- → nop-stream-runtime 单元：C3-008 的编排侧根因（restartRegion Phase 3 提交-继续流水线化）；RPC/remote-deploy 路径的 per-TM restore→run 次序未逐一核对，建议随迁。
- → nop-stream-core 单元：C3-009 的根因载体（ProcessOperator/AbstractUdfStreamOperator/AbstractRichFunction 共享契约），影响所有含状态 RichFunction UDF。
- → nop-stream-connector-debezium / nop-message 单元：C3-008 兄弟面（DebeziumCdcSourceFunction.initializeState → NopStreamOffsetBackingStore.setOffsets 的 REPLACE clear+putAll 于共享静态注册表 map）。

---

## 4. 覆盖声明

**本轮精读文件**（单元内）：
- fraud-example 全 21 个 src/main/java 文件中的运行期类：ReplayableCdcSourceFunction（全文）、DirectoryFileSourceFunction（全文）、UserHistoryEnricher（全文）、FraudDetectionDemo（catch 点及 main 生命周期）、CdcChangeDecoder/TransactionLineParser/FraudAlertPatternFunction/TransactionWindowAggregate/AlertCountAggregate/UserTransactionHistory/MockTransactionGenerator（静态面 sweep）+ 第 1 轮已覆盖类不重复。
- nop-stream-flow：SideOutputTapOperator（全文）、XplSourceFunction/XplSinkFunction/XplReduceFunction/XplFlatMapFunction（全文）、XplFunctionSupport/JoinSideTagFunction/EvalActionKeySelector/JoinSideKeySelector（全文）、BeanContainerFunctionResolver/GlobalBeanFunctionResolver/InMemoryBeanFunctionResolver（全文）。
- nop-stream-sql：StreamScalarFunctionResolver（全文）、RecordColumnAccess（全文）、StreamSqlAggregation（全文，含全部 8 个 agg + wrapper）、StreamRecordEvaluator/SqlRowCompositeFunction（全文）。

**跨模块只读证据**（判定用，不作为发现对象）：nop-stream-core `ProcessOperator`（全文）、`AbstractUdfStreamOperator`（open 段）、`AbstractStreamOperator`（restoreState 段）、`StreamSourceOperator`（copyForSubtask/restoreState/snapshotState 段）、`SubtaskTask`（全文）、`TaskExecutor`（全文）、`StreamTaskInvokable`（invoke/invokeSource 段）、`OperatorChain.deepCopy`（全文）、`AbstractRichFunction`/`FunctionUtils`（全文）、`DataStreamImpl`/`KeyedStreamImpl`（process 工厂点）；nop-stream-runtime `GraphModelCheckpointExecutor`（骨架/restore 段）、`SupervisionLoop`（restartRegion/rebuildTask/restore 段）、`RescaleStateAssembler`（restoreOperatorsFromState 段）、`TaskManager`（提交点 grep）、`RpcDistributedExecutor`（部署次序 grep）；nop-message-debezium `NopStreamOffsetBackingStore`（全文）。

**与第 1 轮的关系**：未修改第 1 轮文件；C3-009 纠正第 1 轮"覆盖度与残留边界"排除项 6 中"子任务实例不跨线程共享"的事实前提（ProcessOperator 共享契约），该纠正已在本发现"现状"段显式声明。

**发现统计（本轮）**：P1 × 2（C3-008、C3-009）。缺陷族命中：F2（C3-008 恢复路径重复发射的半个成功形态）、F9（C3-009 无吞异常但状态静默污染，按状态不一致计）；F3（修实例不修类别）：C3-008 已枚举 connector-debezium 兄弟面并移交，未留空账。
