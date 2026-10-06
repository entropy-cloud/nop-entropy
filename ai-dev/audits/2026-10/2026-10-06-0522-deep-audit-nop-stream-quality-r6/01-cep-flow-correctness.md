# 维度 01：CEP 引擎与 XDSL StreamModel 编排正确性

## 审计范围与方法

**范围**：`nop-stream-cep` 全模块（74 个 main 文件，其中 4 个 `_gen/` 生成文件不作为审计对象）+ `nop-stream-flow`（92 个 main 文件，其中 35 个 `_gen/` 生成文件不作为审计对象），以及为验证跨模块契约而只读参考的 `nop-stream-core` 关键路径（`KeyExtractingOutput`、`KeyedStreamImpl`、`DataStreamImpl.keyBy/union/setParallelism`、`StreamGraphGenerator` 的 keySelector 传播、`StreamSourceOperator` 的 cancel 契约）与 `nop-stream-runtime` 的 state backend 注入点（`TaskCheckpointWiring`、`RemoteTaskDeploySupport`）。

**方法**：
1. 全文通读 CEP 语义核心（NFA / NFACompiler / SharedBuffer / SharedBufferAccessor / CepOperator / AfterMatchSkipStrategy 族 / Pattern / Quantifier / CepPatternBuilder / PatternStreamBuilder），逐条与 Flink 参考语义（take/proceed/ignore、Dewey versioning、within 开闭边界、skip 策略剪枝条件 `<` vs `<=`、超时部分匹配释放路径）比对。
2. 全文通读 `StreamModelDslBuilder`（1202 行）与 `AdvancedTransforms`（982 行），对照 `stream.xdef` / `pattern.xdef` 逐属性核对"声明→消费"闭环（fail-fast 面、路由、并行度、窗口、join、CEP）。
3. 深挖第 2 轮：基于初审命中的文件追踪关联路径（timer ledger ↔ drain ↔ restore、RocksDB 守卫的两条 runtime 注入链、`createTestEnvironment` 的 processingGuarantee 假默认、`TimestampsAndWatermarks` 存在性守卫的作用域）。

**已排查并排除（未列为发现）的疑点**（供复核者快速对齐）：
- `SharedBuffer.flushCache()` 用 `asMap().keySet().clear()` 清缓存：Guava 的 live-view clear 等价 `invalidateAll()`，语义正确；`advanceTime` 只逐出 cache 条目并清理 `eventsCount`，backing state 不动，被锁事件经 `getWithCache` 回读，无泄漏。
- `SharedBufferAccessor.releaseNode` 的 null-node 分支同步 pop version 栈（比 Flink 原版更正确）；`NFA.computeNextStates`/`doProcess` 对 null previousBufferEntry 有守卫，注释已说明手工建图风险。
- `EventId.hashCode` 只混 id+timestamp、`NodeId.hashCode` 不含 pageName：equals/hashCode 弱一致性（碰撞增多），非正确性缺陷。
- `Lockable.release/releaseOrDetach` 的负计数守卫与 0-ref detach 语义正确；`SharedBuffer.registerEvent` 的 EventId 溢出双守卫（循环内 + 循环外）完备。
- NFACompiler（含 until/greedy/copyWithoutTransitiveNots/createTimesState）、Quantifier、Pattern 的形状校验（notNext/notFollowedBy after optional、times 范围、within 正数、until 限定 LOOPING/TIMES）与 Flink 一致且有增强（CEP-02/CEP-08 修复痕迹在 live code 中成立）。
- `gapWithin`（PREVIOUS_AND_CURRENT window）安静 key 的超时欠触发：timer 锚定在 match 起点，状态晚进入窗口时 fire 时未到期且无后续 timer——与 Flink 参考行为一致（Flink 甚至只在全局窗口注册 timer），非本移植引入的回归。
- `StreamModelDslBuilder.build()` 用 `createTestEnvironment()`（AT_LEAST_ONCE 假默认）：xdef 对 `processingGuarantee` 声明 `!enum=STRICT_EXACTLY_ONCE`，解析产物恒非 null，`applyCheckpointConfig` 必然覆盖；纯 Java 构造 StreamModel 的路径仅测试使用，且 `CheckpointPlanBuilder` 兜底默认 STRICT_EXACTLY_ONCE。无 DSL 可达的静默降级。
- `XplSourceFunction.running` 标志：javadoc 明确声明它只是查询面、`run()` 不读、取消必须经 `ctx.isCancelled()`/collect 协作异常（`StreamSourceOperator.drainControlMails` 证实该契约成立）。文档化的故意设计，不算缺陷。
- RocksDB 守卫的两条生产注入链（`TaskCheckpointWiring` 本地 + `RemoteTaskDeploySupport` 恒 Memory）均被守卫覆盖；`setKeyedStateBackend` 直注在 main 代码中无调用方（仅测试）。
- x:extends Delta 合并：`stream.xdef` 声明 `xdef:support-extends="true"`，transforms/edges 均有 `xdef:key-attr="id"`，合并走平台通用 key-attr 语义；builder 消费的是合并后模型，未发现 flow 层特有的合并破坏点。

---

## 第 1 轮（初审）

### [CEP-01] 处理时间模式 checkpoint 恢复后，仅最早 pending timer 被重新武装，后续窗口 timer 永不触发
- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:406-425`（open() 重武装）、`CepOperator.java:917-934`（registerTimer 全部注册点）、`CepOperator.java:1099-1133`（onProcessingTime 无再武装）
- **证据片段**:
  ```java
  // open() — R5 CEP-7 fix
  // Only the earliest pending timestamp per key gets a wall-clock
  // registration: the fire callback drains the full due set for that key
  // (audit-sanctioned shape).
  if (isProcessingTime && registeredEventTimeTimersByKey != null
          && getProcessingTimeService() != null) {
      for (Map.Entry<Object, java.util.TreeSet<Long>> entry : registeredEventTimeTimersByKey.entrySet()) {
          ...
          internalTimerService.registerProcessingTimeTimer(
                  io.nop.stream.core.common.state.VoidNamespace.INSTANCE,
                  entry.getValue().first() + 1);
      }
  }
  ```
  可复现 grep：`grep -n "registerProcessingTimeTimer" CepOperator.java` → 仅 2 个注册点（open() 重武装 :421、registerTimer :919），`onProcessingTime` 的 STEP 1-6 无任何再武装调用。
- **严重程度**: P2
- **现状**: 恢复后每 key 只重武装 `first()+1` 一个墙钟 timer；该次 fire 只处理"当时已到期"的 bucket 与窗口，而 ledger 中更晚的窗口 timer（如 `processEvent` 注册的 `timestamp + windowTime`）既没有墙钟注册、fire 回调也不为它再武装。
- **风险**: 处理时间模式 + 窗口 pattern + checkpoint 恢复后，同一 key 存在多层 pending timer（bucket timer + 窗口 timer）时：最早一次 fire 消耗掉 bucket 并清理其 ledger 条目，之后更晚到期的窗口超时永远不会触发（PT 模式无 watermark 自愈）——超时回调丢失、partial match 及其 SharedBuffer 条目在该 key 下一条事件到来前无限滞留。R5 CEP-7 注释声称"the fire callback drains the full due set"，该假设对"晚于 fire 时刻才到期"的窗口 timer 不成立。
- **建议**: 两处任选：(a) `onProcessingTime` 末尾对该 key 仍存在的 ledger 首条目再武装一次墙钟 timer（与 open() 重武装同构，形成链式自愈）；(b) open() 恢复时为每 key 的所有 ledger 条目注册墙钟 timer（MemoryProcessingTimeService 支持多次注册；量级 = pending timer 数）。
- **信心水平**: 确定（代码路径完全 traced：注册点全集已 grep 验证；恢复场景的行为差异可由 open()+onProcessingTime 代码直接推出）
- **误报排除**: 这不是"恢复语义本来如此"的误报——注释自己声称的覆盖假设（"drains the full due set"）与代码实际能力不符，属于修复意图与实现的偏差；也不是与 Flink 一致的继承行为（Flink 恢复后由 `forEachProcessingTimeTimer` 逐 timer 重放注册）。
- **复核状态**: 未复核

### [CEP-02] drainDueBuckets 逐桶先 advanceTime 再处理桶内事件，within 边界语义与参考实现相悖
- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:1216-1235`（drainDueBuckets 循环体）
- **证据片段**:
  ```java
  for (Long timestamp : new ArrayList<>(dueTimestamps)) {
      List<IN> elements = elementQueueState.get(timestamp);
      if (elements == null) {
          LOG.trace("Timer timestamp {} has no queued bucket ...");
          continue;
      }
      advanceTime(nfaState, timestamp);          // ← 桶事件处理前先推进超时评估
      try (Stream<IN> stream = sort(elements)) {
          stream.forEachOrdered(event -> processEvent(nfaState, event, timestamp));
      }
      elementQueueState.remove(timestamp);
  }
  ```
- **严重程度**: P2
- **现状**: 事件时间模式下，每个到期 bucket 先以桶时间戳执行 `NFA.advanceTime(ts)`（含 `>=` 超时判定与超时回调发射），再处理该桶事件；Flink 参考实现是先排空全部桶（process 无超时检查）、最后以 watermark 统一 advanceTime。
- **风险**: 边界场景结果不同：partial match 起于 t0、within=W，事件 e 的时间戳恰为 t0+W——参考实现中 e 先扩展 match（新状态 previousTimestamp=ts，STEP 3 判 `ts-ts=0<W` 存活，match 完成）；本实现中 match 先被超时释放并发射 timeout 回调，e 只能另起新 match。同一输入流在两套引擎上产生"完成 match"vs"超时回调"的分歧；同时同一 watermark 内 timeout 结果与 match 结果的发射顺序也被提前交错，迁移用户会遇到难以归因的结果差异。
- **建议**: 二选一并固化语义：(a) 对齐参考实现——把逐桶 advanceTime 移出循环，仅保留 onEventTime STEP 3 的 `advanceTime(currentWatermark())`（逐桶版对 PT 模式 `drainDueBuckets(MAX_VALUE, dueOnly=false)` 路径同样成立）；(b) 若"先超时"是有意裁定，则在 `drainDueBuckets` javadoc 与 docs-for-ai 的 CEP 语义文档中显式声明该边界行为并加回归测试钉住。
- **信心水平**: 确定（代码分叉事实确定；边界"哪边更正确"属语义裁定，但与参考实现不一致这一事实无需裁定）
- **误报排除**: 不是常见误报"实现细节顺序无关"——此处顺序直接决定 `isStateTimedOut`（`>=`）在 e 到达前是否已释放 match，属可观察的匹配结果差异，而非仅日志/性能顺序差异。
- **复核状态**: 未复核

### [CEP-03] CepOperator 注入给 NFA 条件的 RuntimeContext 是硬编码常量（subtask 0/1、任务名固定）
- **文件**: `nop-stream/nop-entropy-master/nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:606-618`
- **证据片段**:
  ```java
  private void initNfa() {
      nfa = nfaFactory.createNFA();
      cepRuntimeContext = new CepRuntimeContext(new io.nop.stream.core.common.functions.RuntimeContext() {
          @Override public int getIndexOfThisSubtask() { return 0; }
          @Override public int getNumberOfParallelSubtasks() { return 1; }
          @Override public String getTaskName() { return "cep-operator"; }
      }, keyedStateStore);
  ```
- **严重程度**: P3
- **现状**: 即使在多并行度部署中（`copyForSubtask(TaskLocation)` 已携带 job/vertex/subtask 身份，:347-353），NFA 条件拿到的 RuntimeContext 恒报 subtask 0、总并行度 1。
- **风险**: 用户 `RichIterativeCondition` 若依赖 `getIndexOfThisSubtask()` 做分片/去重/采样类逻辑，在并行部署下静默得到错误分片；排查时日志中的 "cep-operator" 任务名也无法定位实际 subtask。
- **建议**: 将 `copyForSubtask` 已有的 TaskLocation（或至少 subtaskIndex）传递到 `initNfa()`，用真实值构造 RuntimeContext；无 location 时保留现值并 WARN。
- **信心水平**: 确定
- **误报排除**: 不是"测试专用路径"误报——`copyForSubtask(TaskLocation)` 是生产部署入口（R5-CEP-03 注释自述其携带部署身份），身份已到达 operator 却止步于 metrics，未进入条件上下文。
- **复核状态**: 未复核

### [CEP-04] CepOperator×RocksDB 守卫按类名字符串匹配且只覆盖 stateBackend 工厂路径，直连 keyed backend 可绕过；报错文案内嵌审计编号
- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:472-487`
- **证据片段**:
  ```java
  // R5 CEP-1: the RocksDB backend ignores the JavaStreamSerializer contract ...
  if (this.stateBackend.getClass().getName().contains("RocksDB")) {
      throw new StreamException(ERR_STREAM_STATE_ERROR)
              .param(ARG_DETAIL, "CEP operators do not support the RocksDB state"
                      + " backend (SharedBuffer Lockable is not JSON-serializable and"
                      + " the RocksDB backend ignores the JavaStreamSerializer"
                      + " contract) — use the Memory state backend (R5 CEP-1)");
  }
  this.keyedStateBackend = this.stateBackend.createKeyedStateBackend(effectiveKeyClass);
  ```
- **严重程度**: P3
- **现状**: 守卫仅在 `getKeyedStateBackend()==null && stateBackend!=null` 分支内生效：(a) 判定依据是类名 `contains("RocksDB")`，任何包装/委派类（名字不含 RocksDB 但 `createKeyedStateBackend` 产出 RocksDBKeyedStateBackend）静默穿过；(b) `AbstractStreamOperator.setKeyedStateBackend(RocksDBKeyedStateBackend)` 是 public API，直注时 `getKeyedStateBackend()!=null`，守卫整体跳过（当前 main 代码无此调用方，仅测试使用）；(c) 面向用户的错误信息尾部带内部审计编号 "(R5 CEP-1)"，对平台用户是不可解析的噪声。
- **风险**: 未来新增委派 backend（如加密/指标包装层）或第三方直注 keyed backend 时，CEP 将以第一读取即损坏的方式运行在 RocksDB 上——正是该守卫要防的静默损坏形态；文案中的审计编号降低错误信息的可交付性。
- **建议**: (a) 守卫改为对 `stateBackend.createKeyedStateBackend(...)` 产物做类型判定（`instanceof RocksDBKeyedStateBackend` 或按已注册 backend 类型标记接口），或保留名字判定但移到产物创建之后双重校验；(b) 文案删去 "(R5 CEP-1)"（对 `setKeyedStateBackend` 直注路径可加同款 fail-fast 于 `open()` 头部）。
- **信心水平**: 确定（守卫覆盖面事实确定；"包装类绕过"在当前代码中无现实触发方，故 P3）
- **误报排除**: 不是"守卫已足够"的误报——本条不否认当前生产接线已被覆盖（TaskCheckpointWiring/RemoteTaskDeploySupport 已核实），报告的是守卫的防御纵深缺口与文案缺陷，均可在 live code 直接定位。
- **复核状态**: 未复核

### [CEP-05] SKIP_TO_FIRST/SKIP_TO_LAST 缺少 afterMatchSkipTo 时以裸 IllegalArgumentException 失败，违反模块错误分层
- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:224-231`、`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/SkipToElementStrategy.java:41-43`
- **证据片段**:
  ```java
  // CepPatternBuilder（XDSL pattern 属性 → 策略）
  case SKIP_TO_FIRST: {
      strategy = AfterMatchSkipStrategy.skipToFirst(partModel.getAfterMatchSkipTo());
      break;
  }
  // SkipToElementStrategy 构造器
  SkipToElementStrategy(String patternName, boolean shouldThrowException) {
      this.patternName = notNull(patternName, "patternName");   // Guard.notNull → IllegalArgumentException
  ```
- **严重程度**: P3
- **现状**: XDSL 声明 `afterMatchSkipStrategy="SKIP_TO_FIRST"` 而未声明 `afterMatchSkipTo`（pattern.xdef 对二者无联动约束）时，构建期抛 `IllegalArgumentException("IsNull:patternName")`——既不是模块对该类形状错误规定的 `MalformedPatternException`，也不携带出错的 pattern part 名与位置（`StreamException`/`MalformedPatternException` 在本文件其他分支均带 `.param(ARG_PART_NAME...)`/`.loc(...)`）。
- **风险**: 配置错误能 fail-fast（不会静默），但错误分类（IllegalArgumentException vs MalformedPatternException）与可定位性破坏了本模块已建立的错误契约：依赖 `MalformedPatternException` 做配置诊断的上层（如 conf-validate 报告、StreamConfValidator 的 error-code 提取）拿到的是 uncategorized 异常，用户无法知道该补哪个 pattern 的哪个属性。
- **建议**: 在 `CepPatternBuilder.getAfterMatchSkipStrategy` 中先校验 `getAfterMatchSkipTo()` 非空，缺失时抛 `MalformedPatternException(ERR_CEP_MALFORMED_PATTERN).param(ARG_PATTERN_DETAIL, "afterMatchSkipTo is required for " + kind + " on pattern ...").source(partModel)`。
- **信心水平**: 确定
- **误报排除**: 不是"Guard.notNull 也算 fail-fast 就行"的误报——本模块错误处理规范明确两层策略（公共 API 用 ErrorCode 体系），同文件同类形状错误（ERR_CEP_PATTERN_PART_NOT_ALLOW_LOOP）均用带参 typed 异常，此处是同一契约下的漏网点。
- **复核状态**: 未复核

### [CEP-06] keyed CEP 流的异构 key 类：首 key 类被 write-once 捕获并固定到下一次 restore，其余类 key 的状态在恢复时报错
- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:951-979`（captureKeyClass/resolveEffectiveKeyClass）
- **证据片段**:
  ```java
  private void captureKeyClass(Object key) {
      if (capturedKeyClass == null && key != null) {
          capturedKeyClass = key.getClass();     // write-once：首 key 定类
      }
  }
  private Class<?> resolveEffectiveKeyClass() {
      if (keyClass != null) return keyClass;
      if (capturedKeyClass != null && capturedKeyClass != Object.class) return capturedKeyClass;
      ...
  ```
- **严重程度**: P3
- **现状**: 若 KeySelector 在运行期产出异构 key（如 XDSL `EvalActionKeySelector` 的表达式对不同记录返回 Long 与 String），内存运行一切正常（backend 以 Object.class 创建），但首个非 null key 的类被捕获、持久化，并在下一次 restore 时作为唯一 key 类固定给 `MemoryStateSerDe` 重物质化。
- **风险**: 异构 key 流在 checkpoint 后无法恢复——`rematerializeLedgerKey`/`MemoryStateSerDe` 对非该类 key 抛 typed StreamException，作业从"可运行"变为"恢复失败"。触发需要用户侧 keyExpr 写错（病态输入），且失败是显式的（非静默损坏），故仅 P3。
- **建议**: `captureKeyClass` 对后续 key 做类一致性检查：发现异构 key 时在运行期 fail-fast（指出第几个 key 与首 key 类冲突），把故障从恢复期提前到摄入期。
- **信心水平**: 很可能（代码路径确定；实际触发依赖用户写出异构 keyExpr，未构造端到端复现）
- **误报排除**: 不是"文档已声明的设计"误报——代码注释只声明了"首 key 定类"这一机制本身，未声明对异构 key 的行为契约；异构 key 场景在机制内是未定义行为（运行期 OK、恢复期崩），提前 fail-fast 是缺口而非已有裁定。
- **复核状态**: 未复核

### [FLOW-01] CepPatternPartModel 的 windowTime 在未声明任何 quantifier 时被静默忽略，违反本模块 no-silent-drop 契约
- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:166-204`（addQualifier）；声明面 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/pattern.xdef`（`windowTime="duration"` 无联动约束）
- **证据片段**:
  ```java
  private Pattern addQualifier(Pattern pattern, CepPatternPartModel partModel) {
      ...
      if (partModel.isOneOrMore()) {
          pattern = pattern.oneOrMore(partModel.getWindowTime());   // windowTime 仅在此消费
      }
      if (partModel.getTimes() != null) {
          pattern = pattern.times(..., partModel.getWindowTime()); // 仅在此消费
      }
      if (partModel.getTimesOrMore() != null) {
          pattern = pattern.timesOrMore(partModel.getTimesOrMore(), partModel.getWindowTime()); // 仅在此消费
      }
      // 三个分支都不命中时：windowTime 无任何消费点、无任何校验，静默丢弃
  ```
  可复现 grep：`grep -n "getWindowTime" nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java` → 3 处，全部嵌于 quantifier 条志内；`grep -rn "windowTime" nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/TestCepPatternBuilderModel.java` → 无覆盖。
- **严重程度**: P2
- **现状**: `<part name="a" windowTime="5s"/>`（无 oneOrMore/times/timesOrMore）解析成功、构建成功，声明的步进窗口不产生任何运行时效果。
- **风险**: 用户以为声明了逐事件窗口约束，实际 NFA 对该 pattern 无任何时间限制——部分匹配无限滞留（正是 within 类声明要防的形态），且无任何构建期信号；与 builder 类注释自陈的 anti-Hollow 保证（"declared ... attributes are either consumed or rejected at build time — never silently"）直接冲突，属于该项目此前多轮修复的同一缺陷类（FL-1/P1-XDSL-6 类）在 cep 模型面上的残留。
- **建议**: 在 `addQualifier`（或 `buildGroupPattern` 入口）加守卫：`partModel.getWindowTime() != null && 无任何 quantifier` 时抛 typed 异常（ERR_CEP_MALFORMED_PATTERN，带 part name + location），提示 windowTime 必须与 oneOrMore/times/timesOrMore 同用。
- **信心水平**: 确定
- **误报排除**: 不是"xdef 注释已暗示 windowTime 属于 quantifier"的误报——pattern.xdef 对 windowTime 无任何注释或联动约束（`<until>` 有"仅适用于oneOrMore"注释，windowTime 没有）；且本条判据是模块自陈的 no-silent-drop 契约，不是审美判断。
- **复核状态**: 未复核

### [FLOW-02] 指向 `<source>` 的 edge 被静默忽略：validateDag 不拒绝、buildSource 不消费 upstreamIds
- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:457-502`（validateDag 无 source 入边检查）、`StreamModelDslBuilder.java:805-829`（buildTransform 对 source 直接 return buildSource(env, t)，upstreamIds 未用）
- **证据片段**:
  ```java
  private Object buildTransform(StreamExecutionEnvironment env, StreamTransformModel t,
                                Set<String> upstreamIds) {
      // Dispatch on the parsed Java subtype.
      if (t instanceof StreamSourceModel) {
          return buildSource(env, (StreamSourceModel) t);   // upstreamIds 被丢弃
      }
      ...
  // validateDag 仅校验 id 唯一/引用存在/边属性被消费，无 "source 不得有入边" 检查
  ```
- **严重程度**: P2
- **现状**: 声明 `<edge from="x" to="someSource"/>` 时：边引用校验通过、拓扑排序把 source 排在 x 之后、但 source 构建完全忽略上游——该边对编译产物无任何影响，build() 报告成功。
- **风险**: 声明拓扑与建图产物不一一对应（正是本维度核心审查项）：用户若误以为数据可"汇入" source（如把 source 当处理节点复用 id），得到的是零错误 + 与声明不符的执行图；与同模块 R5 FLOW-2（不可达子树 fail-fast）保护的同类风险敞口（错边静默吞）在另一方向上仍然存在。
- **建议**: 在 `validateDag` 中加一条：`byId.get(e.getTo()) instanceof StreamSourceModel` 时抛 `ERR_STREAM_INVALID_ARG`（"source transform '%s' cannot have an incoming edge"），定位到边与 source 两处 location。
- **信心水平**: 确定
- **误报排除**: 不是"边只是声明、source 无需上游"的通用误报——本模块对边的处理契约是"每条边要么被消费要么 fail-fast"（P1-XDSL-5 决策矩阵、HASH-into-union/join 的显式拒绝都在做这件事）；对 source 入边这一形态恰恰漏掉了同款处理。
- **复核状态**: 未复核

### [FLOW-03] R5 FLOW-5 拒绝信息指示"or a HASH edge"，而触发错误的边本身就是 HASH 边——修复指引自相矛盾
- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:1124-1131`（applyEdgePartition），同文案重复于 `StreamModelDslBuilder.java:1153-1160`（applyDeclaredParallelism）
- **证据片段**:
  ```java
  if (targetParallelism != ((DataStreamImpl) keyed).getTransformation().getParallelism()) {
      throw new StreamException(ERR_STREAM_EDGE_PARTITION_UNSUPPORTED)
              .param(ARG_EDGE_ID, edge.getId())
              .param(ARG_DETAIL, "keyed-stage parallelism " + targetParallelism
                      + " differs from the upstream routing parallelism "
                      + ... + " — FORWARD routing would concentrate all records on subtask 0"
                      + " and rescale would split state from traffic (R5 FLOW-5;"
                      + " declare equal parallelism, or a HASH edge)");
  }
  ```
- **严重程度**: P3
- **现状**: 该分支只在 `resolveEdgePartition(...)==HASH` 时可达（applyEdgePartition 的 HASH 分支内），错误文案却建议用户"declare equal parallelism, or a HASH edge"——用户按提示去找/声明 HASH 边会原地打转。
- **风险**: 拒绝本身是正确的（R5 FLOW-5 裁定），但自相矛盾的修复指引会把用户引向无效排查路径，构成真实的排障成本；applyDeclaredParallelism 的副本（针对 `<keyBy parallelism>` 声明）用同一文案，同样不符语境。
- **建议**: HASH 分支文案改为只保留 "declare equal parallelism"（或解释"keyed stage 的并行度必须与上游路由并行度一致"）；applyDeclaredParallelism 分支改为 "declare equal parallelism, or drop the parallelism attribute on <keyBy>"。
- **信心水平**: 确定
- **误报排除**: 不是"文案美化"类误报——错误信息中给用户的修复动作选项在触发分支内是不可能执行的（该分支的前提就是当前已是 HASH 边），指引性错误属于功能性文案缺陷；且该文案在两处硬编码重复，会一起漂移。
- **复核状态**: 未复核

---

## 深挖第 2 轮追加

### [FLOW-04] R5 CEP-2 watermark 供给守卫按"全模型是否存在 <timestampsAndWatermarks>"判定，分支拓扑下可假绿（仅 WARN 级）
- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:759-765`
- **证据片段**:
  ```java
  // R5 CEP-2: event-time CEP requires an upstream watermark supply. Scan
  // the model for a <timestampsAndWatermarks> node; without one the
  // pipeline never emits watermarks ...
  boolean hasWatermarkSupply = owner.model().getTransforms() != null && owner.model().getTransforms().stream()
          .anyMatch(x -> x instanceof io.nop.stream.flow.model.StreamTimestampsAndWatermarksModel);
  if (!hasWatermarkSupply) {
      LOG.warn("CEP pattern [{}] runs in event-time mode but the pipeline declares no"
              + " <timestampsAndWatermarks> node — ...");
  }
  ```
- **严重程度**: P3
- **现状**: 守卫做的是模型全局 `anyMatch`，而非沿 `<cep>` 节点的上游边做可达性检查。当 `<timestampsAndWatermarks>` 声明在另一条无关分支（如 union 的另一输入）而 CEP 所在分支全程无 watermark 注入时，WARN 被抑制，CEP 依旧永不收到 watermark。
- **风险**: 守卫的设计目的（让"静默无 watermark"在构建期可见）在多分支拓扑下失效——恰是拓扑越复杂越容易踩的形态。因当前只是 WARN（typed rejection 是注释里承认的未竟事项），实际后果是"该警告的覆盖不全"，故 P3。
- **建议**: 把判定改为从 `<cep>` 节点出发沿 `edges` 反向 BFS，检查上游可达集合中是否含 `StreamTimestampsAndWatermarksModel`（validateSinkReachability 已有同款反向遍历可复用）；中期按注释裁定升级为 typed rejection。
- **信心水平**: 确定（判定作用域与边拓扑解耦是代码事实；实际触发需要多分支模型）
- **误报排除**: 不是"WARN 宽松是既定裁定"的误报——注释裁定的是"WARN 而非 typed rejection"这一严厉程度，未裁定"用全局 anyMatch 代替拓扑可达性"这一判定口径；本条针对口径，不针对严厉程度。
- **复核状态**: 未复核

（除 FLOW-04 外，深挖第 2 轮对其余盲区的核查结论已并入"审计范围与方法"的排除清单：SharedBuffer 缓存/释放语义、EventWrapper 注册-释放闭环、skip 策略 `<`/`<=` 剪枝边界、quantifier 组合校验、KeyExtractingOutput 与 CepOperator 的 key 假设一致性、`createTestEnvironment` 假默认不可达、`x:extends` 平台级 key-attr 合并无 flow 特有破坏点——均未发现新的高价值问题。）

---

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|----------|----------|------------|
| CEP-01 | P2 | nop-stream/nop-stream-cep/.../operator/CepOperator.java:406-425, 917-934, 1099-1133 | PT 模式恢复后仅重武装每 key 最早 timer，更晚的窗口 timer 永无墙钟注册、超时永不触发 |
| CEP-02 | P2 | nop-stream/nop-stream-cep/.../operator/CepOperator.java:1216-1235 | drainDueBuckets 逐桶先 advanceTime 再处理事件，within 精确边界事件无法扩展到期 match（与参考实现相悖） |
| CEP-03 | P3 | nop-stream/nop-stream-cep/.../operator/CepOperator.java:606-618 | NFA 条件拿到的 RuntimeContext 恒为 subtask 0/1 + 固定任务名，多并行度下失真 |
| CEP-04 | P3 | nop-stream/nop-stream-cep/.../operator/CepOperator.java:472-487 | RocksDB 守卫按类名字符串匹配且只盖工厂路径（直注 keyed backend 可绕过）；文案内嵌 "(R5 CEP-1)" |
| CEP-05 | P3 | nop-stream/nop-stream-cep/.../model/builder/CepPatternBuilder.java:224-231 | SKIP_TO_FIRST/LAST 缺 afterMatchSkipTo 时抛裸 IllegalArgumentException，非 MalformedPatternException 且不定位 part |
| CEP-06 | P3 | nop-stream/nop-stream-cep/.../operator/CepOperator.java:951-979 | 异构 key 类流：首 key 类 write-once 固定，其余类 key 的状态在恢复期 typed 报错（摄入期无守卫） |
| FLOW-01 | P2 | nop-stream/nop-stream-cep/.../model/builder/CepPatternBuilder.java:166-204 | part 声明 windowTime 但无 quantifier 时静默忽略，违反 no-silent-drop 契约 |
| FLOW-02 | P2 | nop-stream/nop-stream-flow/.../builder/StreamModelDslBuilder.java:457-502, 805-829 | 指向 `<source>` 的 edge 校验通过但被静默忽略（buildSource 不消费 upstreamIds） |
| FLOW-03 | P3 | nop-stream/nop-stream-flow/.../builder/StreamModelDslBuilder.java:1124-1131, 1153-1160 | R5 FLOW-5 拒绝文案建议 "or a HASH edge"，在 HASH 边分支内指引自相矛盾 |
| FLOW-04 | P3 | nop-stream/nop-stream-flow/.../builder/AdvancedTransforms.java:759-765 | CEP watermark 供给 WARN 守卫按模型全局 anyMatch 判定，多分支拓扑下可假绿 |

**分布统计**：P0 × 0；P1 × 0；P2 × 4（CEP-01、CEP-02、FLOW-01、FLOW-02）；P3 × 6（CEP-03、CEP-04、CEP-05、CEP-06、FLOW-03、FLOW-04）。合计 10 项。

**总体结论**：CEP 语义核心（NFA 状态转移、Dewey versioning、SharedBuffer 引用计数与释放、skip 策略、超时部分匹配处理、模式 API 校验）与 Flink 参考实现高度一致且带有多轮可见的防御性修复；XDSL→运行时图的映射（拓扑校验、边属性消费矩阵、join/window/aggregate/CEP 装配、并行度与 keyBy 约束）的 fail-fast 纪律总体扎实。本轮保留项集中在两条时间路径的恢复/边界行为（CEP-01/02）与两处静默吞声明的残留缺口（FLOW-01/02），未发现 P0/P1 级错误行为或核心契约违约。

---

## 维度复核结论

> 复核人：独立审计复核员（与初审无关）。方法：逐条亲读被引文件行号区间核对证据真实性，必要时读周边代码、调用方与测试；对 4 条 P2（CEP-01/02、FLOW-01/02）从代码独立推导问题是否成立；CEP-02 另与 apache/flink release-1.17 官方源码（NFA.java、CepOperator.java）做了两轮独立比对。现状归属对照 `ai-dev/audits/2026-10/2026-10-05-0523-deep-audit-nop-stream-quality-r5/summary.md`（唯一允许历史来源）。

| 发现 | 判定 | 现状归属 | 复核要点 |
|------|------|----------|----------|
| CEP-01 | 保留 P2 | R5 修复残余（R5 CEP-7） | 引文与代码一致；注册点全集 grep 复核成立（`registerProcessingTimeTimer` 调用点仅 open() :421 与 registerTimer :919）；恢复路径独立推导成立，见重点复核 |
| CEP-02 | **驳回** | — | 核心事实前提不成立：Flink 官方 CepOperator.onEventTime 的逐桶循环内同样先 `advanceTime(nfaState, timestamp)` 再 processEvent（release-1.17 源码两轮独立抓取互证），本地实现与参考逐点同构；见重点复核 |
| CEP-03 | 保留 P3 | R5 修复残余（R5 CEP-3） | :609-613 硬编码属实；copyForSubtask(:346-367) 身份只进 metrics 属实。证据路径有笔误（"nop-stream/nop-entropy-master/nop-stream/…" 重复前缀），正确路径见最终保留项 |
| CEP-04 | 保留 P3 | R5 修复残余（R5 CEP-1） | 守卫 :476 按类名 contains 判定且仅在 `backend==null && stateBackend!=null` 分支内属实；`AbstractStreamOperator.setKeyedStateBackend`(:175) 为 public、main 代码零调用方（grep 复核，仅 13 个测试文件）属实；文案含 "(R5 CEP-1)" 属实 |
| CEP-05 | 保留 P3 | 新发现 | CepPatternBuilder:225/229 直传 `getAfterMatchSkipTo()` → `SkipToElementStrategy` 构造器 `Guard.notNull` → `IllegalArgumentException("IsNull:patternName")`（Guard.java:82-86 实证）；pattern.xdef:8 仅有文档注释、无联动校验。证据细节修正：本文件其他分支的同类形状错误用 StreamRuntimeException+param/source（如 :77-79），初审写为 "MalformedPatternException…loc" 略有出入，不影响结论 |
| CEP-06 | 保留 P3 | R5 修复残余（R5 ST-02/ST-13 key 定类链） | captureKeyClass write-once(:956-960)、resolveEffectiveKeyClass 优先级(:968-979)、snapshotState 持久化 keyClass(:705-708)、rematerializeLedgerKey typed 抛错(:798-823) 与 MemoryStateSerDe 类型守卫（ERR_STREAM_TYPE_MISMATCH 等）逐点核实；机制成立，触发依赖病态 keyExpr，失败显式，P3 恰当 |
| FLOW-01 | 保留 P2 | 新发现 | `getWindowTime` 全模块消费点仅 CepPatternBuilder:176/180/184 三处且全嵌 quantifier 分支、无守卫（grep 复核）；pattern.xdef:29 无联动约束；TestCepPatternBuilderModel.java 存在但零 windowTime 覆盖。证据细节修正：所引 "consumed or rejected" 契约注释在 StreamModelDslBuilder(:123-127/:452/:620，FLOW edge 面)，CepPatternBuilder 本身无类注释，契约归属引用有误但不影响缺陷成立 |
| FLOW-02 | 保留 P2 | 新发现（R5 FLOW-2 同族反方向） | buildTransform(:805-809) 对 source 直接 `return buildSource(env, t)`，upstreamIds 丢弃且无空集校验；buildSource(:846-858) 签名无 upstream；validateDag(:457-502) 及其三个子校验均无 "source 不得有入边"。独立推导成立，见重点复核 |
| FLOW-03 | 保留 P3（证据修正） | R5 修复残余（R5 FLOW-5） | applyEdgePartition(:1107) 经 :1109 门控为 HASH-only 分支，其文案(:1131)确含 ", or a HASH edge" 自相矛盾——主主张成立；**但** applyDeclaredParallelism 的副本(:1160)实际为 "(R5 FLOW-5; declare equal parallelism)"，无 "or a HASH edge" 尾巴、且该建议在其语境（`<keyBy parallelism>` 声明失配）下恰当——初审"同一文案重复于两处、同样不符语境"不成立；两处文案已经漂移这一事实反而佐证硬编码重复的维护风险 |
| FLOW-04 | 保留 P3 | R5 修复残余（R5 CEP-2） | AdvancedTransforms:759 模型全局 anyMatch 属实，与 CEP 节点上游拓扑解耦；当前仅 WARN（注释自陈 typed rejection 未竟），P3 恰当 |

### P2 及以上重点复核详情

**CEP-01（成立，P2）**。独立推导：
1. 注册点全集：`InternalTimerService.registerProcessingTimeTimer` 仅两个调用点——open() :421 与 registerTimer :919（grep 实证）。open() 每 key 只注册 `ledger.first()+1` 一个墙钟 timer；onProcessingTime(:1099-1133) STEP 1-6 无任何再武装；registerTimer 仅被 bufferEvent(:1045) 与 processEvent(:1282/:1290) 调用（即仅新事件到达时）；processWatermark 的逐 key 自愈路径被 `if (!isProcessingTime)`(:830) 排除。
2. 恢复场景推导：key K 的 ledger 恢复出 {T1(bucket), T2(window deadline)}、T1 < T2。open() 只挂墙钟 T1+1。fire 时 STEP 2 排空全部桶、STEP 3 advanceTime(T1+1)（T2 未到、窗口不评估）、STEP 6 仅删除 <= T1+1 的条目——T2 存留 ledger 但已无任何墙钟注册，且无机制再武装。T2 的窗口超时在该 key 下一条事件（触发 bufferEvent→registerTimer→下一轮 fire 的 STEP 3 advanceTime(now) 补处理逾期超时）到达前永不触发：超时回调丢失、partial match 与 SharedBuffer 条目滞留。
3. 定性核对：open() 注释自陈 "the fire callback drains the full due set for that key" 仅对"fire 时刻已到期"的条目成立，对更晚到期的窗口 timer 不成立——"修复意图与实现偏差"的定性准确。
4. 严重程度维持 P2：checkpoint/restore 是核心路径、PT 模式无 watermark 自愈、失败静默；缓解面为滞留有界（止于该 key 下一条事件）且需 PT+window+同 key 多层 pending timer 叠加。采纳初审建议 (a)（onProcessingTime 末尾对该 key ledger 首条目链式再武装，与 open() 同构）。

**CEP-02（驳回）**。初审核心前提 "Flink 参考实现是先排空全部桶（process 无超时检查）、最后以 watermark 统一 advanceTime" 与事实不符：
1. 对 apache/flink release-1.17 `flink-cep/.../operator/CepOperator.java` 两轮独立抓取互证：onEventTime 先 `getSortedTimestamps()`，循环 `while (peek() <= currentWatermark)` 内逐桶**先 `advanceTime(nfaState, timestamp)`**、再对排序后事件 `processEvent(...forEachOrdered)`、再 `elementQueueState.remove(timestamp)`；循环结束后才 `advanceTime(nfaState, currentWatermark())` 收尾——与本地 drainDueBuckets(:1216-1235) + onEventTime STEP 3(:1069) 的结构逐点同构。
2. 边界场景独立推导（partial match 起于 t0、within=W、事件 e@t0+W 完成 pattern）：Flink 在处理桶内 e 之前同样以 ts=t0+W 执行 advanceTime；`isStateTimedOut` 两边同一公式 `!isStartState && windowTime>0 && timestamp-startTimestamp >= windowTime`（本地 NFA.java:401；Flink 同为 `>=`），match 先被超时释放——两套引擎产生相同结果（超时而非 match），初审"参考实现 match 完成 vs 本实现超时回调"的分歧不存在。
3. 附加核对：Flink 的 doProcess 不含事件时间戳超时检查（本地 doProcess 同样不含，:404-490 全文核对），不构成第二分叉点。
结论：不存在与参考实现的可观察行为分歧，全案驳回；初审引用的"Flink 参考形状"系记忆偏差。

**FLOW-01（成立，P2）**。builder 全路径核查：buildFromModel(:40-47) 只消费 pattern 级 within/gapWithin；buildGroupPattern(:50-95) 对每个 part 调 addQualifier；addQualifier(:166-204) 中 `getWindowTime` 仅出现于 :176/:180/:184 三个 quantifier 分支内，无 else/守卫（全模块 grep 复核无其他消费点，:1271 起的 CepOperator 消费的是 NFA 侧已建成的窗口）；pattern.xdef:29 `windowTime="duration"` 为普通属性，注释块(:20-27)只解释 oneOrMore/optional/times/greedy/timesOrMore，windowTime 连说明都没有、更无联动校验；TestCepPatternBuilderModel.java 存在但不含 windowTime（grep 实证）。因此 `<part windowTime="5s"/>` 无 quantifier 时解析成功、构建成功、运行期无任何时间约束——静默丢弃成立且无任何构建期信号。P2 维持：触发仅需用户对属性语义的正常误读，无需病态输入，且后果正是 within 类声明要防的"部分匹配无限滞留"形态。

**FLOW-02（成立，P2）**。核对：validateDag(:457-502) 检查 transform id 非空/唯一、edge id 唯一、from/to 引用存在，随后 validateEdgeDeclarations/validateJoinDeclarations/validateSinkReachability——三者均不检查 "edge 的 to 不得是 StreamSourceModel"（P1-XDSL-5 决策矩阵 :620-660 只覆盖 HASH/keyExpr/flow-control 属性面）；buildTransform(:805-809) 对 StreamSourceModel 直接 `return buildSource(env, (StreamSourceModel) t)`，upstreamIds 参数被丢弃且无非空校验；buildSource(:846-858) 不接受上游输入。因此 `<edge from="x" to="someSource"/>` 校验全绿、build() 报告成功、该边对产物零影响——声明拓扑与执行图不一一对应成立。P2 维持：与 R5 FLOW-2（不可达子树 fail-fast）保护的风险同族但方向相反（这次是"多出来的边"被静默吞）；修复建议（validateDag 中对 `byId.get(e.getTo()) instanceof StreamSourceModel` 的入边做 typed 拒绝）可行且与现有校验风格一致。

## 最终保留项（复核后）

| 编号 | 复核后严重程度 | 文件路径 | 一句话摘要 | 现状归属 |
|------|----------------|----------|------------|----------|
| CEP-01 | P2 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:406-425,917-934,1099-1133 | PT 模式恢复后仅重武装每 key 最早 ledger timer，更晚窗口 timer 无墙钟注册且 fire 回调不再武装，超时在该 key 下一条事件前永不触发 | R5 修复残余（R5 CEP-7） |
| CEP-03 | P3 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:606-618 | NFA 条件的 RuntimeContext 硬编码 subtask 0/1 + 固定任务名，多并行度下失真（部署身份止步于 metrics） | R5 修复残余（R5 CEP-3） |
| CEP-04 | P3 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:472-487 | RocksDB 守卫按类名 contains 判定且只盖工厂路径（public setKeyedStateBackend 直注可绕过，当前无 main 调用方）；文案内嵌 "(R5 CEP-1)" | R5 修复残余（R5 CEP-1） |
| CEP-05 | P3 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:224-231 | SKIP_TO_FIRST/LAST 缺 afterMatchSkipTo 时抛裸 IllegalArgumentException("IsNull:patternName")，非模块 typed 异常且不定位 part | 新发现 |
| CEP-06 | P3 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:951-979 | 异构 key 类流运行期正常、checkpoint 后首 key 类被固定，其余类 key 恢复期 typed 报错（摄入期无守卫） | R5 修复残余（R5 ST-02/ST-13） |
| FLOW-01 | P2 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:166-204 | part 声明 windowTime 但无 quantifier 时静默忽略（无消费点/校验/测试），违反模块 no-silent-drop 纪律 | 新发现 |
| FLOW-02 | P2 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:457-502,805-829 | 指向 `<source>` 的 edge 校验全绿但被静默忽略（buildSource 不消费 upstreamIds），声明拓扑与产物不符 | 新发现（R5 FLOW-2 同族反方向） |
| FLOW-03 | P3 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:1107-1131 | HASH-only 分支的拒绝文案建议 "or a HASH edge"，指引自相矛盾（applyDeclaredParallelism 副本无此尾巴且语境恰当，初审该子主张不成立） | R5 修复残余（R5 FLOW-5） |
| FLOW-04 | P3 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:751-765 | CEP watermark 供给 WARN 守卫按模型全局 anyMatch 判定而非上游可达性，多分支拓扑下可假绿 | R5 修复残余（R5 CEP-2） |

**复核统计**：初审 10 条 → 保留 9（P2×3：CEP-01、FLOW-01、FLOW-02；P3×6：CEP-03/04/05/06、FLOW-03/04）、驳回 1（CEP-02）。现状归属：R5 修复残余 6（CEP-01/03/04/06、FLOW-03/04）、新发现 3（CEP-05、FLOW-01、FLOW-02）。P2 总数由 4 降为 3。
