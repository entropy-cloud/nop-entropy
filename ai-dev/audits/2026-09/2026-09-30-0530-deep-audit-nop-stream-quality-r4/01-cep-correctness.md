# nop-stream-cep CEP 引擎正确性深度审计（R4 轮 · 01-cep-correctness）

- 审计日期：2026-09-30
- 审计人：CEP 正确性深度审计 agent（R4 重试轮，前次因速率限制中断，本轮全量重做）
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-master`
- 审计对象（逐文件精读，live code 为准）：
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java`（1153 行，全文）
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java`（1109 行，全文）+ `NFAStateNameHandler.java`
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/`（SharedBuffer / SharedBufferAccessor / SharedBufferNode / SharedBufferEdge / Lockable / NodeId / EventId，全文）
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`（1350 行，全文）+ `CepRuntimeContext` / `StreamRecordComparator`
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/Pattern.java`（745 行，全文）+ `Quantifier` / `GroupPattern` / `WithinType` / `conditions/*`（全文）
  - `nfa/aftermatch/*`（全部 7 个策略类，全文）、`nfa/ComputationState` / `NFAState` / `State` / `StateTransition` / `DeweyNumber`（全文）
  - 边界接线：`CEP.java` / `PatternStreamBuilder.java` / `model/builder/CepPatternBuilder.java`（模式装配段）/ `configuration/SharedBufferCacheConfig.java` / `NopCepConfigs.java`
  - 引擎边界核对：`OperatorChain.deepCopy` / `AbstractStreamOperator.copyForSubtask`（nop-stream-core）、`MemoryKeyedStateBackend` 键控语义、`TaskManager` 线程模型、`StreamMetricsRegistries`
- 历史收敛项（不重复报告）：CEP SharedBuffer stack desync（2026-08 已修，本轮验证修复仍在位）；plan 366（processWatermarkStatus1/2 删除、LATE_ELEMENTS_DROPPED 接线，本轮验证在位）；A3-A6/B2/B7、G1-G3、D4/A11（Deferred）；R5-TE-03、R5-TE-10、R5-PF-05、R5-PF-06、R5-ST-03（本轮其他 agent 已独立报告，未在本文重复展开）。
- 方法：静态精读 + 对每个可疑语义构造小场景做状态推演（推演路径在正文给出）；未修改任何代码；未运行测试（结论均附代码级证据与推演，供后续以最小用例复核）。

---

## 发现总览

| 编号 | 严重程度 | 标题 |
|------|---------|------|
| R5-CEP-01 | **P1** | 处理时间 + 比较器模式下，bucket 排水由事件时间 timer ledger 驱动，但 PT 模式从不写 ledger 且 reconcile 每 key 只执行一次——每个 key 第一轮 timer 之后，后续所有缓冲事件永久滞留 |
| R5-CEP-02 | **P2** | greedy + until + optional 组合下 `originalStateMap.get(proceedState.getName())` 返回 null，编译出 target 为 null 的 PROCEED 边，运行期在 until 条件首次命中时 NPE 崩任务 |
| R5-CEP-03 | P3 | `numLateRecordsDropped` 注册在 JVM 级 composite registry，同名 counter 被全部 CepOperator 子任务/算子/作业共享，指标串数 |
| R5-CEP-04 | P3 | cache 统计 timer 关闭竞态：`close()` cancel(false) 后已入队的回调可 re-arm 并覆盖 future，close 之后仍周期性触发 |
| R5-CEP-05 | P3 | `copyForSubtask` 按引用共享 nfaFactory（编译期 State 图与 IterativeCondition 实例），并行子任务对共享 Rich 条件重复/竞争驱动 setRuntimeContext/open/close |
| R5-CEP-06 | P3 | `NFAState.STATE_COMPARATOR` 通过 `toString().split("\\.")` + `Integer.parseInt` 比较 DeweyNumber（equals/hashCode 路径），字符串往返解析且与 `COMPUTATION_STATE_COMPARATOR` 双标准并存 |
| R5-CEP-07 | P3 | `Lockable.equals/hashCode` 把可变的 refCounter 计入相等性——当前仅作 MapState value 未触发，但作为 key/去重集合使用时会按引用计数判等，属语义地雷 |
| R5-CEP-08 | P3 | `times(0,0)` / `times(-1,2)` 等非法参数抛裸 `IllegalArgumentException`（Guard），违反本模块"模式形态错误一律 MalformedPatternException"的错误分层约定 |

**分布：P0 × 0，P1 × 1，P2 × 1，P3 × 6，合计 8 项。**

---

### [R5-CEP-01] PT+comparator 模式下第一轮 timer 触发后 bucket 永久滞留（ledger 驱动排水与 PT 计时器断链）

- **文件**：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`
- **严重程度**：P1
- **证据片段 1**（行 808-814，`registerTimer`：PT 分支只注册处理时间计时器，从不写事件时间 ledger）：
  ```java
  private void registerTimer(long timestamp) {
      if (isProcessingTime) {
          internalTimerService.registerProcessingTimeTimer(VoidNamespace.INSTANCE, timestamp + 1);
      } else {
          internalTimerService.registerEventTimeTimer(VoidNamespace.INSTANCE, timestamp);
      }
  }
  ```
- **证据片段 2**（行 1063-1073，`drainDueBuckets`：排水的候选集合完全来自 ledger，ledger 为空直接返回）：
  ```java
  Object key = currentRegistrationKey();
  reconcileTimerLedgerIfNeeded(key);
  TreeSet<Long> ledger = registeredEventTimeTimersByKey == null
          ? null
          : registeredEventTimeTimersByKey.get(key);
  if (ledger == null || ledger.isEmpty()) {
      // Reconciled invariant: no ledger entries means no queued buckets.
      return;
  }
  ```
- **证据片段 3**（行 1107-1114，reconcile 每 key 一次后永不再做）：
  ```java
  private void reconcileTimerLedgerIfNeeded(Object key) throws Exception {
      if (reconciledTimerKeys != null && reconciledTimerKeys.contains(key)) {
          return;
      }
      if (reconciledTimerKeys == null) {
          reconciledTimerKeys = new HashSet<>();
      }
      reconciledTimerKeys.add(key);
  ```
- **证据片段 4**（行 979-995，`onProcessingTime` 全文无任何 ledger 清理；对照 `onEventTime` 行 967-976 有 `timers.removeIf(timer <= time)` 清理——PT 路径连过期 ledger 项也不删，陈旧项永久占位）。
- **现状**：plan 2279 把 bucket 排水从"扫描 `elementQueueState.keys()`"改为"从 per-key timer ledger 取候选"（ET 模式下 `bufferEvent → registerTimer → registerEventTimeTimer → registerEventTimeTimerForKey` 会同步写 ledger，且 reconciliation 兜底，自洽）。但 PT + comparator 模式（`processElement` 行 788-791 走 `bufferEvent`）下：`registerTimer` 走 PT 分支，**ledger 永远不会被 bucket 注册填充**；每个 key 唯一的一次补救是首次 drain 时的 reconciliation 回填，此后 `reconciledTimerKeys` 命中即跳过，新 bucket 的时间戳永远进不了 ledger。
- **推演路径**（PT + comparator，public API 可达：`CEP.pattern(input, pattern, comparator).inProcessingTime().build(...)`，无任何校验拦截该组合）：
  1. key=1 事件 e1 在处理时间 T1 到达 → `elementQueueState[T1]=[e1]`，注册 PT timer(T1+1)。ledger[1] 为空。
  2. T1+1 触发 → `onProcessingTime` → `drainDueBuckets(MAX, dueOnly=false)` → reconcile 首次执行：观察到 bucket {T1}，回填 ledger[1]={T1} → 正常排水（这就是现存测试 `TestCepOperatorMultiKeyWatermark.processingTimeCallbackRestoresKeyContext` 覆盖并验证通过的场景——它只 fire 一轮）。
  3. key=1 事件 e2 在 T3（T3>T1）到达 → `elementQueueState[T3]=[e2]`，注册 PT timer(T3+1)；**ledger[1] 仍为 {T1}**。
  4. T3+1 触发 → `drainDueBuckets` → reconcile 命中已 reconcile 跳过 → ledger={T1} → `elementQueueState.get(T1)==null` → continue → **返回。T3 bucket 永久无人访问**。
  5. 此后该 key 的所有事件重复步骤 3-4：匹配永不输出、`elementQueueState` 与 SharedBuffer 注册事件无界增长；且 PT 模式不接 watermark，无任何自愈路径（除非进程重启触发重新 reconcile）。
- **风险**：高。对该配置属静默数据丢失 + 无界状态增长；用户感知为"CEP 偶发（实际是首轮之后）全部失灵"，与 `state.backend`、并行度无关。非 PT+comparator 组合不受影响（ET 模式 ledger 由 `registerEventTimeTimer` 维护；PT 无 comparator 模式不走 `bufferEvent`）。
- **建议**：三选一——(a) `registerTimer` 的 PT 分支同步调用 `registerEventTimeTimerForKey(currentRegistrationKey(), timestamp)`（ledger 退化为"bucket 注册表"，drain 后沿用 `onEventTime` 式清理，需为 `onProcessingTime` 补 STEP-5 清理）；(b) `drainDueBuckets` 在 PT 模式（dueOnly=false）直接迭代 `elementQueueState.keys()`，恢复 plan 2279 之前的排水候选源；(c) 最小防御：`PatternStreamBuilder.build` 在 `isProcessingTime && comparator != null` 时抛 `StreamException`（符合本项目"静默接受不支持的配置即缺陷"的裁定基准，但会砍掉一个名义上支持的 API 组合，属行为变更需 owner 裁定）。推荐 (a)。
- **信心水平**：高（代码路径单线可推，无并发假设；唯一不确定的是"该组合是否被人为视为 unsupported"，但代码与文档均未声明）。
- **误报排除**：`onProcessingTime` 的 STEP-3 `advanceTime` 只做窗口超时剪枝，不排水 bucket（`advanceTime` 不读 `elementQueueState`）；reconcile 的 `observed==null → return` 不影响本结论（首火必有 bucket，见推演步骤 2）；ET 模式不受影响。

---

### [R5-CEP-02] greedy + until + optional 编译出 null-target PROCEED 边，运行期 until 命中即 NPE

- **文件**：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java`（根因）；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java`（崩溃点）
- **严重程度**：P2
- **证据片段 1**（NFACompiler.java 行 613-621，`createTimesState`：仅当 `from != to` 才向 `originalStateMap` 写入副本）： 
  ```java
  if (currentPattern.getQuantifier().hasProperty(Quantifier.QuantifierProperty.GREEDY)
          && times.getFrom() != times.getTo()) {
      if (untilCondition != null) {
          State<T> sinkStateCopy = copy(sinkState);
          originalStateMap.put(sinkState.getName(), sinkStateCopy);
      }
      updateWithGreedyCondition(sinkState, takeCondition);
  }
  ```
- **证据片段 2**（NFACompiler.java 行 749-759，`createSingletonState`：GREEDY+until+optional 读取 `originalStateMap.get(proceedState.getName())`，无 miss 防护）：
  ```java
  if (isOptional) {
      if (currentPattern.getQuantifier().hasProperty(Quantifier.QuantifierProperty.GREEDY)) {
          final IterativeCondition<T> untilCondition =
                  (IterativeCondition<T>) currentPattern.getUntilCondition();
          if (untilCondition != null) {
              singletonState.addProceed(
                      originalStateMap.get(proceedState.getName()),
                      new RichAndCondition<>(proceedCondition, untilCondition));
          }
  ```
- **证据片段 3**（NFA.java 行 986-989，`createDecisionGraph`：PROCEED 目标入栈后立刻解引用，NPE 发生在 per-transition try 之外，不会被包装成 `ERR_CEP_NFA_FILTER_EXECUTION_FAILED`）：
  ```java
  while (!states.isEmpty()) {
      State<T> currentState = states.pop();
      Collection<StateTransition<T>> stateTransitions = currentState.getStateTransitions();
  ```
- **现状**：`originalStateMap` 的写入仅在两处——`createTimesState` 行 617-618（键 = `sinkState.getName()`，且要求 `times.from != times.to`）与 `createLooping` 行 881-885（键 = `copyWithoutTransitiveNots` 结果的名字）。读取处键 = `proceedState.getName()`。两条可达路径使读取必然 miss（返回 null）：
  - **(a) 最小触发**：`begin("a").where(c).times(0,1).greedy().until(u)`。`Pattern.times(0,1)` 置 OPTIONAL 并调整为 `Times(1,1)`（Pattern.java 行 521-525），于是行 614-615 的 `from != to` 为假、副本从未写入；行 637 传入的 `proceedState == sinkState`，`originalStateMap.get(...)` → null → `addProceed(null, [true AND until])`。该 chain 每一步 API 调用均合法，`MalformedPatternException` 不会抛出。`CepPatternBuilder.addQualifier`（`times(begin,last)` 允许 begin=0 + isGreedy + until）同样可达。
  - **(b) 变体**：中链 `... .followedBy("b").notFollowedBy("n").followedBy("m").oneOrMore().greedy().optional().until(u)`。`copyWithoutTransitiveNots` 因 m 的祖先含 NOT_FOLLOW 且 m 为 OPTIONAL 而真正复制 sink（行 519，`createState(sinkState.getName(), ...)` 经 `getUniqueInternalName` 产生**新的内部名**），`createLooping` 以副本名写入 `originalStateMap`；而 `createTimesState` 的 `proceedState` 是外层原名 sink → 键不匹配 → get → null。
- **推演路径**（以 (a) 为例）：首事件 e 且 `until(e)==true` → 该 singleton（即 Start 态）的转移集：TAKE 条件含 `NOT(until)` 为假；PROCEED(null, `until(e)==true`) 命中 → `visited.add(null)` 成功 → `states.push(null)` → 下一轮 `pop()` 得 null → 行 988 NPE。异常未被任何 try 包裹（行 993 的 try 在 for 循环体内），经 `CepOperator.drainDueBuckets` 的 lambda 包装为 `ERR_STREAM_STATE_ERROR` → 任务失败/恢复循环；若 until 永不命中则地雷静默潜伏。变体 (b) 中 `findFinalStateAfterProceed`（NFA.java 行 952 `transition.getTargetState().isFinal()`）同样会 NPE（此处被 try 包裹，包装为 `ERR_CEP_NFA_FILTER_EXECUTION_FAILED`）。
- **风险**：中。合法 API 形状在编译期零告警，运行期在"until 条件首次命中"这一必然到达的时点崩溃；模型层（`CepPatternBuilder`）同样可达。该缺陷为 Flink 移植同源（Flink 的 `originalStateMap` 键控逻辑相同），但 nop 未如其他移植差异点一样补校验。
- **建议**：在 `createSingletonState` 读取处对 miss 显式防御：`originalStateMap.getOrDefault` 语义不存在时回退 `proceedState` 本身（until 命中时跳过副本优化、直接走普通 proceed，语义损失仅为"丢失 until 命中时绕过 transitive NOT 的副本优化"），或更好的做法是在 `NFAFactoryCompiler.compileFactory` 入口拒绝 `GREEDY && until != null && Times.from == Times.to && OPTIONAL` 组合并抛 `MalformedPatternException`（fast-fail，与模块既有校验风格一致）。另建议给 `originalStateMap` 补充"读取后清空"或断言，防止跨 pattern 编译残留。
- **信心水平**：高（两条触发路径均有完整的编译期/运行期代码链证据；未实际运行用例，故按本项目惯例定 P2 而非 P0）。
- **误报排除**：`oneOrMore().greedy().until()`（非 optional，首 pattern）不受影响——`createSingletonState` 的读取在 `if (isOptional)` 之内；`createLooping` 的 GREEDY+until 分支（行 879-893）自带 `copy(sinkState)`、不读 `originalStateMap`，故纯 looping 形状安全；`times(from,to)` from<to 时行 617 已写入且键与 `sinkState`（= `proceedState`，见行 424 两参同传）一致，安全。

---

### [R5-CEP-03] LATE_ELEMENTS_DROPPED 指标注册在 JVM 级共享 registry，跨算子/子任务/作业串数

- **文件**：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`
- **严重程度**：P3
- **证据片段**（行 111、344-345）：
  ```java
  private static final String LATE_ELEMENTS_DROPPED_METRIC_NAME = "numLateRecordsDropped";
  ...
  this.numLateRecordsDropped = StreamMetricsRegistries.registry()
          .counter(LATE_ELEMENTS_DROPPED_METRIC_NAME);
  ```
- **现状**：`StreamMetricsRegistries.registry()`（nop-stream-core `StreamMetricsRegistries.java` 行 30-41）是 JVM 级静态 `CompositeMeterRegistry`；Micrometer `registry().counter(name)` 对同名 meter 返回同一实例。plan 366 接线的 `numLateRecordsDropped.increment()`（行 803）因此把同一 JVM 内所有 CepOperator 实例（多子任务、多作业、多 pattern）的丢弃数累加到同一个计数器上。
- **风险**：低-中（可观测性语义缺陷，不影响正确性）：单算子/单 key 维度的丢弃归因丢失；两个作业（如同一 JVM 内嵌多个 EmbeddedJob）的指标相互污染，告警与容量评估失真。plan 366 的验证测试（`TestCepOperatorLateRecordsDroppedMetric`）在单算子场景下察觉不到。
- **建议**：以算子身份打 tag：`registry().counter("numLateRecordsDropped", "operator", getTaskName(), "subtask", indexOfSubtask)`，或在 open() 时用带任务名的名称注册。与 Flink 的 per-task metric scope 对齐。
- **信心水平**：高。
- **误报排除**：非"指标未接线"问题——plan 366 的接线本身工作正常（单实例场景计数正确），本条只针对注册粒度。

---

### [R5-CEP-04] cache 统计计时器关闭竞态：close 后仍可周期性触发

- **文件**：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`
- **严重程度**：P3
- **证据片段**（行 551-559 与 568-573）：
  ```java
  void onCacheStatisticsTimer(long timestamp) {
      if (partialMatches != null) {
          partialMatches.logCacheStatistics();
      }
      if (cacheStatsIntervalMs > 0) {
          // Re-arm anchored to the fire time (not current time) to avoid drift.
          cacheStatsTimerFuture = getProcessingTimeService().registerTimer(
                  timestamp + cacheStatsIntervalMs, this::onCacheStatisticsTimer);
      }
  }
  ...
  void releaseCacheStatisticsTimer() {
      if (cacheStatsTimerFuture != null) {
          cacheStatsTimerFuture.cancel(false);
          cacheStatsTimerFuture = null;
      }
  }
  ```
- **现状**：`close()`（行 576-584）先 `releaseCacheStatisticsTimer()` 再关 NFA。`cancel(false)` 不中断已提交到 `ProcessingTimeService` 调度线程的执行；若回调已开始执行或已入队，它会在 close 之后再次 `registerTimer` 并把新 future 写回 `cacheStatsTimerFuture`，此后无任何路径再取消它——统计 timer 以 `cacheStatsIntervalMs`（默认 30 分钟）为周期在算子关闭后继续触发，持有已关闭的 `partialMatches` 引用。
- **风险**：低。默认 30 分钟一跳、回调体只做日志（`partialMatches != null` 判空在），实际危害是测试/嵌入式场景的线程与对象生命周期泄漏、以及 close 后日志噪音；若未来回调体变重则放大。回调线程与任务线程的内存可见性依赖 `cacheStatsTimerFuture` 的非同步访问（transient 普通字段），存在可见性缺口。
- **建议**：引入 `volatile boolean closed` 标志，`onCacheStatisticsTimer` re-arm 前检查；或 re-arm 时保存返回的 future 供 close 二次取消（改为 cancel 返回前循环一次）。注释中"Done before releasing partialMatches so the timer does not fire during teardown"的意图是对的，但 `cancel(false)` 不满足该意图的强一致版本。
- **信心水平**：中高（竞态窗口取决于 `ProcessingTimeService` 实现的调度语义；生产注入的 ScheduledExecutorService 语义下成立）。
- **误报排除**：`onCacheStatisticsTimer` 与 `onProcessingTime` 的回调分离（plan 366 产物）本身正确，本条不涉及统计 timer 误触发 CEP 处理。

---

### [R5-CEP-05] copyForSubtask 按引用共享 NFA 编译图，Rich 条件生命周期跨子任务竞争

- **文件**：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java`
- **严重程度**：P3
- **证据片段 1**（CepOperator.java 行 308-319）：
  ```java
  @Override
  public CepOperator<IN, KEY, OUT> copyForSubtask() {
      return new CepOperator<>(
              inputSerializer,
              isProcessingTime,
              nfaFactory,          // ← 按引用共享：内含编译期 State 图与全部 IterativeCondition 实例
              comparator,
              afterMatchSkipStrategy,
              getUserFunction(),
              lateDataOutputTag,
              keyClass);
  }
  ```
- **证据片段 2**（NFA.java 行 193-201，每个子任务的 `open()` 都在共享 State 图的转移条件上驱动 Rich 生命周期）：
  ```java
  public void open(RuntimeContext cepRuntimeContext, Configuration conf) {
      for (State<T> state : getStates()) {
          for (StateTransition<T> transition : state.getStateTransitions()) {
              IterativeCondition<T> condition = transition.getCondition();
              FunctionUtils.setFunctionRuntimeContext(condition, cepRuntimeContext);
              FunctionUtils.openFunction(condition, conf);
          }
      }
  }
  ```
- **现状**：`NFAFactoryImpl.createNFA()`（NFACompiler.java 行 1104-1107）复用同一 `Collection<State<T>>`；每个子任务 `initNfa()` 对共享条件实例执行 `setRuntimeContext + open`，`close()` 时对共享条件执行 `closeFunction`。引擎侧 `TaskManager` 用线程池并发执行同顶点的多个子任务（TaskManager.java 行 170-171、437），因此并行度 > 1 时：(1) 条件的 RuntimeContext 为 last-write-wins；(2) 同一 Rich 条件的 `open(Configuration)` 被多次驱动（用户条件若持有 open 期初始化的状态即被重复初始化/污染）；(3) 一个子任务 close 会关闭其他运行中子任务仍在调用的条件；(4) 并发 `filter()` 共享可变条件实例无线程安全保证。
- **风险**：低-中：无状态 lambda 条件（当前全部测试与示例的形态）在"只差 RuntimeContext"下行为一致，实际危害潜伏；一旦用户条件实现 `RichIterativeCondition` 并持有状态（接口是 public 扩展点，`TestCepOperatorConditionLifecycle` 表明该形态受支持），并行度 > 1 即语义漂移。引擎对 user function 的共享是既定约定（`OperatorChain.deepCopy` javadoc、`WindowOperator` 同样按引用共享 trigger），但"NFA 编译图 + Rich 生命周期被 per-subtask 驱动"是 CEP 特有的放大面。
- **建议**：`copyForSubtask` 对 `nfaFactory` 做一次 Java 序列化深拷贝（`AbstractStreamOperator.copyForSubtask` 默认实现即此策略；NFA 图与条件均 Serializable，成本仅并行启动时一次），或将条件生命周期改为 per-operator 条件实例克隆。若裁定"条件必须无状态"为契约，则在 `IterativeCondition` javadoc 与校验中显式声明。
- **信心水平**：中（共享事实确证；实际危害取决于用户条件形态与引擎对同顶点子任务的并发调度，二者均非 CEP 可控）。
- **误报排除**：`comparator`/`afterMatchSkipStrategy`/user function 的共享符合引擎约定，不计入；`cepRuntimeContext` 本身是 per-operator 的（CepOperator.java 行 499-511），问题仅在它被写到共享条件上。

---

### [R5-CEP-06] NFAState 的辅助比较器用字符串往返解析比较 DeweyNumber，且与主比较器双标准并存

- **文件**：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFAState.java`
- **严重程度**：P3
- **证据片段**（行 133-155）：
  ```java
  private static final Comparator<ComputationState> STATE_COMPARATOR =
          Comparator.<ComputationState, String>comparing(ComputationState::getCurrentStateName)
                  .thenComparing((c1, c2) -> { ... return compareDeweyNumber(v1, v2); })
                  .thenComparingLong(ComputationState::getStartTimestamp)
                  .thenComparingLong(ComputationState::getPreviousTimestamp);

  private static int compareDeweyNumber(DeweyNumber a, DeweyNumber b) {
      int minLen = Math.min(a.length(), b.length());
      String[] da = a.toString().split("\\.");
      String[] db = b.toString().split("\\.");
      for (int i = 0; i < minLen; i++) {
          int cmp = Integer.compare(Integer.parseInt(da[i]), Integer.parseInt(db[i]));
  ```
- **现状**：`equals/hashCode`（行 163-179，测试与状态断言路径）每次调用对每个 ComputationState 做 `toString()`（StringBuilder）+ `split`（正则）+ `parseInt`；`DeweyNumber` 本身持有 `int[]`，可直接按位比较（`Arrays.compare`）。同时该类存在两个语义不同的比较器：`COMPUTATION_STATE_COMPARATOR`（按 startEventID 时间，驱动 PriorityQueue 排序）与 `STATE_COMPARATOR`（按 stateName+version，仅用于 equals 归一化），无命名/文档区分，维护时易错用。
- **风险**：低。正确性成立（字符串表示与 int[] 双射，`length()` 回退保证前缀序），纯属维护成本与 equals 热路径（测试大规模状态比对、未来若用于生产比对）的不必要分配；`Collections.EMPTY_LIST.<T>iterator()`（NFA.java 行 1137）同类原始类型 raw-use 也归入本条的类型卫生项。
- **建议**：`compareDeweyNumber` 改为对 `int[]` 直接逐位比较（`Integer.compare(a.deweyNumber[i], b.deweyNumber[i])`，可在 DeweyNumber 上暴露 `int at(int idx)`）；两个比较器改名/加 javadoc 区分用途。
- **信心水平**：高。
- **误报排除**：`COMPUTATION_STATE_COMPARATOR`（行 54-73）本身是 Serializable 静态类、检查点序列化安全（注释所述修复在位），不在本条范围。

---

### [R5-CEP-07] Lockable 相等性包含可变引用计数，属面向未来改动的语义地雷

- **文件**：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java`
- **严重程度**：P3
- **证据片段**（行 101-115）：
  ```java
  @Override
  public boolean equals(Object o) {
      ...
      Lockable<?> lockable = (Lockable<?>) o;
      return refCounter.get() == lockable.refCounter.get() && Objects.equals(element, lockable.element);
  }

  @Override
  public int hashCode() {
      return Objects.hash(refCounter.get(), element);
  }
  ```
- **现状**：`Lockable` 在当前代码中只作为 `MapState` 的 value 与 Guava cache 的 value 使用（`SharedBuffer.java` 行 136-162），从不作为 key 或参与集合去重，因此缺陷未显性化。但 value-语义的 `equals/hashCode` 把**随每次 lock/release 变化的引用计数**纳入相等性：任何未来代码将 Lockable 放入 Set/Map key、或在"缓冲内容是否变化"的意义上比较新旧值（缓存一致性检查的自然写法），都会因计数瞬时不同而误判不等（hashCode 随之漂移，若已入 HashMap 期间被 lock 将直接丢失条目）。
- **风险**：低（当前无触发点），但属于"按 element 判等"直觉与实际语义的背离，且该类是 public API 面。
- **建议**：`equals/hashCode` 只基于 `element`（或按 identity 语义显式声明 final 类并删除 equals/hashCode），把 refCounter 比较移到专门的测试断言工具。
- **信心水平**：高（缺陷形态确证；触发前提为未来改动）。
- **误报排除**：`release()`/`releaseOrDetach()` 的过释放防护与 CAS 循环正确（`TestLockableOverRelease` 在位），不涉及。

---

### [R5-CEP-08] Pattern.times 非法参数抛裸 IllegalArgumentException，违反模块错误分层

- **文件**：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/Pattern.java`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/Quantifier.java`
- **严重程度**：P3
- **证据片段**（Pattern.java 行 515-525；Quantifier.java 行 200-208）：
  ```java
  public Pattern<T, F> times(int from, int to, @Nullable Duration windowTime) {
      Guard.checkArgument(from <= to, "from must be <= to");
      checkIfNoNotPattern();
      checkIfQuantifierApplied();
      ...
      if (from == 0) {
          this.quantifier.optional();
          from = 1;
      }
      this.times = Quantifier.Times.of(from, to, windowTime);   // Times ctor: Guard.checkArgument(from > 0 / to >= from)
  ```
  ```java
  private Times(int from, int to, @Nullable Duration windowTime) {
      Guard.checkArgument(from > 0, "The from should be a positive number greater than 0.");
      Guard.checkArgument(to >= from, "The to should be a number greater than or equal to from: " + from + ".");
  ```
- **现状**：`times(0,0)`（`from<=to` 通过 → optional 置位 → `Times.of(1,0)`）、`times(-1,2)`、`times(2,1)` 均以 `java.lang.IllegalArgumentException`（Guard 原生异常，无错误码、无 `MalformedPatternException` 包装）从 builder 链路逃逸。同一类"模式形态非法"失败在同文件其他位置（`where` null 条件以外）全部走 `MalformedPatternException(ERR_CEP_MALFORMED_PATTERN)`（如 `within` 非正窗口、name 含 ':'），两层错误策略（AGENTS.md Code Conventions：public API 用 ErrorCode 体系）在此处断裂；且 `times(0,0)` 在抛出前已对 quantifier 产生了 `OPTIONAL` 副作用（若该 Pattern 对象后续被复用/异常被吞，状态被污染）。
- **风险**：低。上游若按 `MalformedPatternException` 捕获做诊断（`TestErrorDiagnosticsEnhancement` 方向），这三类输入会以未分类异常穿透。
- **建议**：`Times` 构造参数校验上移到 `Pattern.times(...)` 入口并抛 `MalformedPatternException`；`from == 0` 的 optional 副作用移到全部校验通过之后。
- **信心水平**：高。
- **误报排除**：`Guard.notNull(condition)` 类 null 检查（如 `where` 行 195）同样抛 IAE，但 null 条件属编程错误而非"模式形态"，本条只覆盖 times 数值域这一类。

---

## 已排查并判定为非缺陷的项（含推演，供后续轮次免重复）

以下各项均曾作为候选进行场景推演，最终以代码证据排除；记录在此以免后续轮次重复消耗。

1. **"notFollowedBy 触发 discard 路径丢弃 re-added start 态 → NFA 永久死亡"候选**：`NFA.doProcess` 中 `shouldDiscardPath` 为真时 `statesToRetain`（含 re-added start）确被丢弃（NFA.java 行 452-468），且行 456-463 注释自证知晓该风险。推演结论：**经 public Pattern API 不可达**。stop 态进入决策图需要 start 态存在到 stop 的 PROCEED 链，而 stop 边只落在"notFollow 模式之后继模式"的状态上；start 态到达该状态必须先消费事件（TAKE），或经 optional 的 PROCEED（trueFunction）跳过——后者要求 notFollowedBy 直接跟在 optional 元素之后，而 `Pattern.notFollowedBy/notNext` 显式拒绝 OPTIONAL 前驱（Pattern.java 行 350-355、379-384）；模型层 `CepPatternBuilder.buildFollow` 走同一 API，屏障同样成立。逐形态验证：`begin(a).optional().followedBy(b).notFollowedBy(c).followedBy(d)` 弃权事件 c 只能命中 start 的 IGNORE（start 态 IGNORE 被 `handleIgnoreEdge` 行 806 显式跳过）、`begin(a).oneOrMore().notFollowedBy(b).followedBy(c)` 的 stop 边在 state_c 上而 start 是 firstOfLoop singleton（无 PROCEED）、looping 态在途匹配被 b 击杀属正确 NOT 语义且 start 作为独立计算态存活。null-guard（行 464-467）+ API 屏障 + 手工构造 State 图需越过编译器，三者叠加判定为当前不可达；若未来模型层新增绕过 `notFollowedBy` OPTIONAL 检查的装配路径，此项应升级为 P0 复审。
2. **skip 策略剪枝误伤 start 态**：`AfterMatchSkipStrategy.prune`（aftermatch/AfterMatchSkipStrategy.java 行 112-119）仅剪 `getStartEventID() != null` 的状态，start 态该字段恒 null（ComputationState.createStartState），不会命中 `releaseNode`；同时"startEventID 非空 ⟹ previousBufferEntry 非空"不变式成立（仅 `handleTakeEdge` 从 start 置该字段且同时写入新 entry），故 `prune` 内的 `releaseNode` 无 null 风险。
3. **SharedBuffer release 对称性（历史 desync 修复在位）**：`SharedBufferAccessor.releaseNode` 行 302-311 的 lockstep pop 修复仍在；`releaseOrDetach` 的 0 计数幂等（行 72-85）与"重复访问合法"注释（行 279-292）自洽；`TestCepReleaseSymmetryInvariant` 在位。
4. **watermark 到达时的超时触发顺序**：`processWatermark`（CepOperator.java 行 717-764）先更新 `currentWatermark` 再按 ledger 收集 due keys 并逐 key `setCurrentKey → onEventTime`；`onEventTime` 的 STEP2（按桶升序：先 `advanceTime(bucketTs)` 再处理桶内事件，`isStateTimedOut` 的 `>=` 边界使截止桶事件不能"抢救"已到期匹配，与 Flink 边界语义一致）→ STEP3 `advanceTime(watermark)` → STEP5 ledger 清理。STEP2 期间新注册的窗口 timer 若 `<= watermark`，其超时语义已被 STEP3 覆盖，STEP5 删除属正确簿记，无重复/丢失发射。乱序事件按 `timestamp > currentWatermark` 缓冲、同 ts 事件按 comparator 稳定排序（无 comparator 时按到达序），跨桶天然按 ts 升序——顺序稳定性成立。
5. **状态恢复完备性**：`NFAState`（含 SerializableStateComparator）与 ComputationState/DeweyNumber/NodeId/EventId 全 Serializable，经 JavaStreamSerializer 走 byte[]（CepOperator.java 行 396-414）；窗口时间来自 pattern（restore 后由同一 pattern 重编译，state 内部名由 `NFAStateNameHandler` 确定性生成）一致；恢复的 watermark/timer ledger/keyClass 均在 `open()` 之前落位（行 324-334 顺序注释与 `TestCepCheckpointRestoreE2E` 对应）。pattern 版本变更导致的 state name 漂移会以 `ERR_CEP_NFA_*_STATE_CHECK_FAILED` fail-fast，非静默。
6. **keyBy 后 pattern 状态隔离**：`elementQueueState`/`computationStates`/SharedBuffer 三个 MapState 均为 keyed state；SharedBuffer cache 以 `ScopedId(scope=key, id)` 隔离（SharedBuffer.java 行 97-134），legacy 无 scope 访问器以 close 时 `flushCache()` 兜底；`EventId` 计数器（eventsCount）为 keyed state，跨 key 不串号。
7. **`SharedBuffer.advanceTime` 只驱逐 cache 不删 backing state**（行 286-290）：被引用事件的 Lockable 仍在 keyed state 中，后续 `getEvent` miss 时回源重载，引用计数路径不经过 cache 生存期——cache-only 驱逐安全；`registerEvent` 的冲突探测同时查 cache 与 backing state（行 324），去重不破。
8. **事件注册泄漏**：`NFA.EventWrapper`（NFA.java 行 599-637）惰性注册 + close 释放；无 TAKE 边时不注册（零泄漏）；`registerEvent` 失败路径（SharedBuffer.java 行 343-348）回滚 cache、eventsCount 计数多加 1 仅造成 id 跳号，无害。
9. **greedy 语义（`updateWithGreedyCondition`）**：对 sink 全转移 AND `NOT(takeCondition)` 强制贪婪，until 命中时经 `originalStateMap` 副本旁路（语义为 FLINK 同源的"until 打破 NOT 累积"），除 R5-CEP-02 的 get-miss 外行为正确；`times(n).greedy()`（from==to）的 greedy 静默无效与 Flink 一致，不计。
10. **SKIP_TO_FIRST/LAST 目标缺失**：默认（`shouldThrowException=false`）回退 no-skip，`throwExceptionOnMiss()` 才抛 `ERR_CEP_SKIP_TO_MISSING_ELEMENT`/`ERR_STREAM_SKIP_NO_MATCH`（SkipToElementStrategy.java 行 57-87），与 Flink 契约一致；编译期 `checkPatternSkipStrategy`（NFACompiler.java 行 230-246）已拦截模式定义中不存在的名字。
11. **cache 配置是否被静默忽略**：`new SharedBufferCacheConfig()` 无参构造读取 `NopCepConfigs` 三个配置项（SharedBufferCacheConfig.java 行 37-41），`CepOperator.initStateAccess` 使用之——配置生效，非缺陷。
12. **plan 366 回归确认**：`processWatermarkStatus1/2` 无残留（全文 grep）；`numLateRecordsDropped` 已接线（行 803，粒度问题另见 R5-CEP-03）；cache 统计 timer 与 CEP 处理 timer 回调分离（行 543-560）正确。

---

## 结论

- **P0：0 项。P1：1 项（R5-CEP-01）。P2：1 项（R5-CEP-02）。P3：6 项（R5-CEP-03 ~ 08）。合计 8 项。**
- P1（R5-CEP-01）详述：处理时间 + 自定义比较器的公开 API 组合下，每个 key 只有第一轮 timer 触发前的缓冲事件会被排水；此后所有 bucket 永久滞留 `elementQueueState`，匹配静默丢失、状态无界增长。根因是 plan 2279 的 ledger 驱动排水假设"bucket 注册必然写 ledger"，该假设仅在事件时间分支成立（`CepOperator.registerTimer` 行 808-814），而 reconciliation 是每 key 一次性的（行 1107-1131）。修复建议见正文（PT 分支同步写 ledger 或 PT 排水回退桶扫描；最少限度应拒绝该组合）。
- P2（R5-CEP-02）详述：`times(0,1).greedy().until(...)`（及中链 oneOrMore+greedy+optional+until 且祖先含 notFollow）在编译期生成 target 为 null 的 PROCEED 边（`NFACompiler.createSingletonState` 行 749-759 读取 `originalStateMap` 无 miss 防护），运行期 until 条件首次命中即 NPE 崩任务。API 与模型 builder 均不拦截该形状。建议编译期 fast-fail 或读取处回退 `proceedState`。
- NFA 核心转移语义（Dewey 版本预算、loop/consume/proceed、greedy×optional 编译、SKIP 家族剪枝）、SharedBuffer 引用计数与 release 对称性、事件时间超时边界与乱序稳定性、keyed 隔离与恢复完备性：本轮逐项推演未发现新的正确性缺陷；历史修复（desync、plan 366）均在位。
