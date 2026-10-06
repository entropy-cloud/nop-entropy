# 维度 05：键控路由/属主一致性 + 窗口语义

## 审计范围与方法

- **范围**：keyBy 路由（KeySelectorPartitioner、KeyGroupAssignment 公式链、maxParallelism 全部入口）、本地/远程部署路由一致性（RemoteTaskDeploySupport、AssignmentPlanner、spec 模式）、checkpoint 属主元数据（TaskEpochSnapshot/CheckpointSerDe/materializeKeyGroupOwnership）、RescaleStateAssembler（rescale 重分布 + WIN-2/4 守卫）、WindowOperator（分配/合并/触发/清理/evictor/迟到/侧输出/pane-tracking）、TimestampsAndWatermarksOperator 与 idle source、InputGate 多入边 watermark 对齐、HeapInternalTimerService 注册/取消/snapshot/restore 对称性。
- **方法**：全部结论来自 HEAD live code 阅读；追了三条 restore 入口（本地 manifest/原始 checkpoint、savepoint、remote deploy restoreDeployedSubtaskFromStorage）与两条建图入口（DataStream API、StreamModelDslBuilder）到执行端的公式链；用只读 grep 对"per-key auxiliary operator state 键全集 vs 守卫列表"、"markActive 生产者全集"、"setTargetKeyGroupRange 调用者全集"做了封闭式枚举。遵守盲审纪律：未读 ai-dev/audits|plans|bugs|lessons|logs 下任何文件。
- **编号说明**：R5 批次已使用 KR-1/2/3、WIN-2/4、FLOW-5；本轮新发现沿用前缀、序号从 06/05 起跳，避免与历史条目混淆。
- **核对为健康的关键面**（避免后续重复审计，简要记录）：路由公式单一来源成立——record 路由（`DataStreamImpl.KeySelectorPartitioner.partition` → `KeyGroupAssignment.assignToSubtask`）与 keyed state 属主（`MemoryKeyedStateBackend`/`RocksDBKeyedStateBackend` → `assignToKeyGroup`）收敛同一公式链；maxParallelism 四个入口均有校验：keyBy 时（`DataStreamImpl.keyBy` R5 KR-3b fail-fast）、execute 时（`StreamExecutionEnvironment.validateKeyedRoutingParity`，Graph frozen vs configured backend 相等校验）、restore 时（`assertStampedMaxParallelismConsistency`，R5 KR-2）、remote 注入（`AssignmentPlanner.extractKeyGroupMaxParallelism` 装入 descriptor → `RemoteTaskDeploySupport.provisionStateBackends` 用同一 M 建后端；spec 模式两侧同为默认 128，DSL 声明 per-node maxParallelism 会 fail-fast，当前无可漂移源）；`stableHash` 的 JSON+murmur3 与 enum-by-name 合同、null key 双侧一致（routing 返 0，backend group 0 属主即 subtask 0）；`KeyGroupReshard`/`MaxParallelismReshardMigration` 的 ST-02 rematerialize 与 fail-fast 契约自洽；`TimeWindow.mergeWindows`/`MergingWindowSet.addWindow` 为 Flink 忠实移植（含 mergeResult==newWindow 自合并守卫）；timer serde 对称（fired timer 不入 snapshot、restoreWatermark 单调、AR-22 key rematerialization 带类型 fail-fast）；EventTimeTrigger/CountTrigger/PurgingTrigger 语义与 Flink 一致。

## 第 1 轮（初审）

### [KR-06] Stage 35 rescale 恢复路由在生产路径不可达：keyed 标记在 plan 构建时永远为 false
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1310-1311, 1502-1511`（配合 `runtime/checkpoint/CheckpointPlanBuilder.java:163-168`、`runtime/execution/TaskCheckpointWiring.java:166-177`、`runtime/operators/windowing/WindowOperator.java:489-492`）
- **证据片段**:
  ```java
  // GraphModelCheckpointExecutor.java:1310
  boolean vertexKeyed = isVertexKeyed(checkpointPlan, vertexId, oldSubtasks);
  boolean rescale = vertexKeyed && oldParallelism > 0 && oldParallelism != newParallelism;
  ```
  ```java
  // CheckpointPlanBuilder.java:163 —— 标记来源：plan 构建时 keyedStateBackend 必为 null
  if (op instanceof AbstractStreamOperator) {
      AbstractStreamOperator<?> abstractOp = (AbstractStreamOperator<?>) op;
      if (abstractOp.getKeyedStateBackend() != null) {
          keyedKey = "operator-" + opIndex + "-keyed";
      }
  }
  ```
  ```java
  // TaskCheckpointWiring.java:169-175 —— wiring 只 provision IStateBackend 字段（非 keyedStateBackend）
  if (abstractOp.getStateBackend() == null) { ... abstractOp.setStateBackend(stateBackend); }
  // WindowOperator.java:492 —— keyedStateBackend 只在 open() 创建（invoke() 内，restore 之后）
  this.keyedStateBackend = this.stateBackend.createKeyedStateBackend(keyClass);
  ```
- **严重程度**: P1
- **现状**: `isVertexKeyed` 依赖 `OperatorStateMapping.hasKeyedState()`，而该标记在 `CheckpointPlanBuilder.build` 时从 `getKeyedStateBackend()` 读取——该字段只在 `open()`（任务 invoke() 内）创建，且晚于一切 restore 入口。三条 restore 路径全部先建 plan 后 open：本地 skeleton（build 于 line 230，restore 于 line 248，open 于 submitAndRun line 263 起）、savepoint runtime（line 362 vs 369+）、remote deploy（`wireDeployedSubtask` 先 `provisionStateBackends`——但它 set 的是 `stateBackend` 字段，builder 读的是另一个字段 `keyedStateBackend`，其 javadoc 声称"provision BEFORE plan build 使 mappings 检出 keyed state"实际不成立）。因此 `rescale` 恒为 false，`buildRescaledTaskState`/KeyGroupRange 重分布/`assertNoOperatorStateOnScaleUp`（NEW-A + R5 WIN-2/4 Phase 1 守卫）在生产不可达；`MemoryKeyedStateBackend.setTargetKeyGroupRange`/`RocksDBKeyedStateBackend.setTargetKeyGroupRange` 在 main code 中零调用者（全部调用在 src/test）。实测后果：keyed vertex **并行度缩小**恢复时走 1:1 lookup 静默成功——每个存活 subtask 恢复同 index 旧快照的 keyed state（`MemoryStateSerDe.java:345-351` 仅在 targetKeyGroupRange 非空时过滤），key-group 已易主的状态被错置、被裁撤 subtask 的 key-group 数据整体丢失；**并行度扩大**时新 index lookup 抛通用 `ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED`（响但误导）。配套不对称：`materializeKeyGroupOwnership` 仅在 `triggerSavepoint`（line 438）调用，周期 checkpoint 永不打 (P,M) 戳，R5 KR-2 一致性检查对周期 checkpoint 恒旁路。
- **风险**: 文档与注释声称"Restore-time parallelism rescale is honored"（`restoreDeployedSubtaskFromStorage` javadoc），运维按此变更并行度将得到静默错置/丢失的 keyed state（窗口/CEP 内容错桶、输出错误无任何告警）；已投入的 Stage 35/NEW-A/WIN-2/4 全套防护与 KR-2 校验形同虚设，误导后续开发以为 rescale 已闭环。
- **建议**: 二选一并补回归测试：(a) 让标记可满足——`CheckpointPlanBuilder`/`provisionStateBackends` 判定"潜在 keyed"改用可静态判定的信号（如 operator 是否 keyed 类型：WindowOperator/CepOperator/ProcessOperator/OverWindowOperator/EquiJoinOperator，或 `stateBackend != null && vertex 有 keyBy 前驱`），或 (b) restore 端不依赖 plan 标记，直接对 `oldParallelism != newParallelism` 的 vertex 按 keyed 快照存在性（`taskState.getKeyedStates()` 非空）触发 `buildRescaledTaskState`。同时把 `materializeKeyGroupOwnership` 挂到周期 checkpoint 完成路径，使 KR-2 检查对所有 epoch 生效。
- **信心水平**: 确定（三个 plan 构建点与 open() 时序、provision 字段错位、setTargetKeyGroupRange 零生产调用者均为直接代码证据）
- **误报排除**: 为什么不是"测试已覆盖所以路径可达"——覆盖 rescale 的测试（TestKeyGroupRangeBackendRestore 等）全部直接调 `setTargetKeyGroupRange`/`buildRescaledTaskState`，绕过了 `isVertexKeyed` 这一道生产闸门；也不是"标记在别处补设"——已枚举 `setStateBackend`/`setKeyedStateBackend` 全部 main 调用点，`CheckpointPlan.build` 三个调用点无一晚于 open()。
- **复核状态**: 未复核

### [KR-07] 全部 keyed 快照共用裸键 "keyed-state"：链内 ≥2 个 keyed 算子时后写覆盖前写，checkpoint 静默丢状态 + 恢复交叉污染
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:253-262`（配合 `operators/AbstractStreamOperator.java:308-312`、`operators/ProcessOperator.java:70-73`、`runtime/execution/RescaleStateAssembler.java:564-581`、`jobgraph/JobGraphGenerator.java:343-382`）
- **证据片段**:
  ```java
  // CheckpointBarrierTracker.java:253 —— mapping 无 keyed 标记（见 KR-06）⇒ keyedKey 恒 null ⇒ 裸键直写
  String keyedKey = getKeyedStateStorageKey(operatorIndex);
  if (keyedKey != null && ...) { snap.putKeyedState(keyedKey + "-" + entry.getKey(), ...); }
  else if (snapshot.getKeyedStates() != null) {
      for (...) { snap.putKeyedState(entry.getKey(), entry.getValue()); }  // "keyed-state" 互相覆盖
  }
  ```
  ```java
  // AbstractStreamOperator.java:308 —— 每个有 keyed 后端的算子都写同一个裸键
  if (keyedStateBackend != null) {
      StateSnapshot snapshot = keyedStateBackend.snapshotState();
      if (snapshot != null && !snapshot.isEmpty()) { result.putKeyedState("keyed-state", snapshot); }
  }
  // ProcessOperator.java:70 —— 无条件自建 keyed 后端（即使不在 keyed 流上）
  if (keyedStateBackend == null && stateBackend != null) {
      keyedStateBackend = stateBackend.createKeyedStateBackend(Object.class);
  ```
- **严重程度**: P1
- **现状**: 因 KR-06 的标记死因，ACK 侧所有 keyed 快照都落在任务快照的裸键 `"keyed-state"` 上，`TaskStateSnapshot.putKeyedState` 后写覆盖前写。链内出现 ≥2 个"有非空 keyed state 的算子"即触发：`canChain`（JobGraphGenerator:343-382）只查并行度/前向/单边/`isChainable()`（默认 true，无 keyed 约束），而 ProcessOperator 无条件自建 keyed 后端、WindowOperator/CepOperator/OverWindowOperator/EquiJoinOperator 在 open() 自建——`keyBy().window(...).apply(..).process(fn)`、甚至 `.process(f1).process(f2)`（两者都用了 keyed state store）都会链成一个 chain。每次 checkpoint，后 ACK 算子的快照覆盖窗口/前一个 process 的 keyed 内容；restore 侧（RescaleStateAssembler.buildSnapshotFromTaskState:577-581 裸键回退）两个算子都读到同一份（对方的）快照——一边丢失、一边被灌入异构状态。代码自己在注释里承认该假设（"A chain has at most one keyed-state-bearing operator by construction"，RescaleStateAssembler:574-576）但没有任何机制保证它。
- **风险**: 对使用了链式多 keyed 算子拓扑的作业，checkpoint 每周期静默丢失除最后一个写者外全部 keyed 状态；恢复后窗口内容/CEP/键控值状态消失或错配，且无任何报错。属于核心数据损坏风险，普通用户用 DataStream API 即可踩中。
- **建议**: 短期：`canChain` 增加"链内已存在 keyed 后端持有者则断链"约束，或 ProcessOperator 仅在 `KeyedStream.process` 路径（有 keyBy 前驱/显式声明）才自建 keyed 后端；根治：让 `CheckpointPlanBuilder` 的 keyed 标记可静态判定（同 KR-06 建议 (a)），使 ACK 写 `operator-N-keyed` 前缀键、restore 按前缀读，彻底消除裸键共享面；两者都需补"链式双 keyed 算子 checkpoint/restore"回归测试。
- **信心水平**: 很可能（覆盖机制为直接代码证据；"两个非空 keyed 快照同链"的可达性经 canChain 规则与 ProcessOperator 无条件自建后端推得，未跑端到端复现）
- **误报排除**: 为什么不是"空快照不写入所以无碰撞"——覆盖仅在双方快照均非空时发生，这一点已计入现状限定；也不是"jobgraph 天然不会链 keyed 算子"——canChain 全部 7 条规则无一涉及 keyed 语义，`isChainable()` 默认 true 且无任何窗口/CEP/process 工厂覆写（grep 全仓只有接口默认实现一处）。
- **复核状态**: 未复核

### [KR-08] idle source 恢复永不向下游发 WatermarkStatus.ACTIVE：一次空闲事件后该通道被永久逐出 watermark min-merge
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimestampsAndWatermarksOperator.java:96-123, 174-188`（配合 `common/eventtime/WatermarksWithIdleness.java:62-79`、`execution/InputGate.java:1188-1193, 1218-1243`）
- **证据片段**:
  ```java
  // TimestampsAndWatermarksOperator.java:97 —— 只翻本地旗标，不发 ACTIVE 状态
  public void processElement(StreamRecord<T> element) throws Exception {
      this.idle = false;                       // ← 无 output.emitWatermarkStatus(ACTIVE)
      ...
  // OperatorWatermarkOutput.markActive() 只有生成器调用；grep 全仓无任何生成器调用 markActive
  // WatermarksWithIdleness.java:63 —— onEvent 同样只清本地旗标
  public void onEvent(T event, long eventTimestamp, WatermarkOutput output) {
      watermarks.onEvent(event, eventTimestamp, output);
      idlenessTimer.activity();
      isIdleNow = false;                       // ← 无 output.markActive()
  ```
  ```java
  // InputGate.java:1188 —— 下游通道解除静音的唯一途径是收到 ACTIVE 状态
  // A watermark from a currently-idle channel is recorded but does not
  // drive the combined output (the channel must first signal ACTIVE ...)
  if (channelIdle[channelIndex]) { return Optional.empty(); }
  ```
- **严重程度**: P1
- **现状**: `markIdle`（AR-9 修复）会把 IDLE 状态跨任务发到下游，`InputGate.handleWatermarkStatusNonRecursive` 是 `channelIdle[i]` 复位为 false 的唯一代码路径，且仅由 ACTIVE 状态触发。但空闲恢复侧：本算子 `processElement` 直接翻本地 `idle` 旗标（后续 emitWatermark 会照常发出，只是被下游"记录但不参与 min"），`WatermarksWithIdleness.onEvent` 也不调 `output.markActive()`；全仓 grep 证实 markActive 在 main code 中没有任何调用者（WatermarkOutputMultiplexer 有实现但无生产消费者）。结果：任何使用 `WatermarkStrategy.withIdleness()` 的通道一旦触发过 idle，数据恢复后其 watermark 永远不参与下游 min 合并；若全部通道都经历一次 idle→resume，下游事件时间彻底冻结，事件时间窗口/CEP 永不再触发，且完全无告警。
- **风险**: AR-9"空闲必须跨任务边界"契约只落地了 IDLE 半边，ACTIVE 半边断裂；多分区有倾斜的作业（withIdleness 的目标场景）在第一次空闲发生后事件时间推进退化为"从未空闲通道的 min"，全部空闲过一次后永久停摆——窗口输出静默丢失。
- **建议**: `processElement` 在 `idle==true` 时改走 `markActive()`（经 OperatorWatermarkOutput 发 ACTIVE，顺带保持旗标一致）；`WatermarksWithIdleness.onEvent` 对齐 Flink 增加 `output.markActive()`；补"idle→resume→watermark 恢复推进"的跨任务回归测试（断言 InputGate channelIdle 复位）。
- **信心水平**: 确定（IDLE 下发、下游 valve 语义、ACTIVE 无生产者三段均为直接代码证据，逻辑闭环）
- **误报排除**: 为什么不是"下游收到记录会隐式激活通道"——InputGate 注释与实现均明确"must first signal ACTIVE"，`handleWatermarkStatusNonRecursive` 之外无任何对 `channelIdle` 的写 false；也不是"生成器会在周期 emit 时 markActive"——`onPeriodicEmit` 仅 `watermarks.onPeriodicEmit(output)`，WatermarksWithIdleness 的 emit 分支不含 markActive。
- **复核状态**: 未复核

### [WIN-05] evictor 路径的 element-timestamps 侧存储在清理/合并时既不清除也不合并：状态泄漏 + 重复命名空间下驱逐判定被污染
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:1608-1636`（早退分支 1621-1627）、`1746-1795`（合并分支）、`1057-1067`（假时间戳回退）、`1540-1553`（向泄漏列表追加）；`WindowOperatorBuilder.java:127-135`（evictor ⇒ ListStateDescriptor）
- **证据片段**:
  ```java
  // WindowOperator.java:1621 —— descriptor(list) 路径清 list 后直接 return，
  // 跳过下方 fallback 分支才有的 elementTimestampsState.remove(...)
  if (newListWindowState != null) {
      IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
      typedBackend.setCurrentKey(key);
      newListWindowState.setCurrentNamespace(stateWindow);
      newListWindowState.clear();
      return;                                    // ← element-timestamps 泄漏
  }
  ...
  windowContentsState.remove(WINDOW_VALUE_KEY);
  if (elementTimestampsState != null) {          // :1633 仅 MapState fallback 路径可达
      elementTimestampsState.remove(ELEMENT_TIMESTAMPS_KEY);
  }
  ```
- **严重程度**: P2
- **现状**: `elementTimestampsState` 仅在 `evictor != null` 时创建，而 builder 规定 evictor ⇒ ListStateDescriptor，即**所有带 evictor 的作业都走 list 分支**。`clearWindowContents` 的 list 分支提前 return，purge/DISCARDING 触发/清理定时器三类路径都遗留下该 (key, stateWindow) 命名空间的 element-timestamps 列表。后果一：每个窗口 pane 泄漏一份时间戳列表，随时间无界增长并进入 checkpoint；后果二：对命名空间可复用的窗口（用户自建 GlobalWindows+evictor+Purging 组合），`storeElementTimestamp` 会 `get` 到泄漏列表继续 append，下次 fire 时 `getElementTimestamps` 返回旧条目，`emitWindowContents` 按 index 对齐读到陈旧时间戳（超出部分再以 currentWatermark 假戳回退，WindowOperator:1061-1065），TimeEvictor/自定义 evictor 的驱逐判定被污染。后果三：`mergeWindowContents` list 分支（session+evictor 场景）既不把 source 的 element-timestamps 并入 target，也不清理 source 命名空间的时间戳（`newListWindowState.clear()` 只清 list）——合并后 target 的时间戳数量少于元素数，多出的元素全部拿到假时间戳，TimeEvictor 永远视其为"新元素"不驱逐。
- **风险**: 长跑 evictor 作业 keyed state 无界增长（恢复变慢、checkpoint 膨胀）；session/count+TimeEvictor 组合在合并或复用窗口后驱逐语义静默失真。
- **建议**: `clearWindowContents` list/appending 分支在 return 前补 `elementTimestampsState` 清理（与 MapState 分支对齐）；`mergeWindowContents` list 分支将 source 时间戳按 append 顺序并入 target 后再清 source 命名空间；补 evictor+session 清理/合并的 state 形状断言测试。
- **信心水平**: 确定（三个分支的代码路径均为直接证据；泄漏与合并缺口不需要运行时假设）
- **误报排除**: 为什么不是"Flink 同款行为"——Flink 的 ListState 元素带 TimestampedValue 时间戳，无独立侧存储，不存在此裂缝；本实现的侧存储是自有设计（窗口注释自述），其清理责任落在 `clearWindowContents`，而该方法只有 MapState 分支履行了责任。
- **复核状态**: 未复核

## 深挖第 2 轮追加

### [KR-09] WIN-2 Phase 1 的 per-key auxiliary 守卫列表与 live 写入键集不合：漏 "process-internal-timers" / "equi-join-buffer" / "over-window-buffer"
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RescaleStateAssembler.java:137-141`（对照 `core/operators/ProcessOperator.java:122`、`core/operators/join/EquiJoinOperator.java:152`、`runtime/operators/windowing/OverWindowOperator.java:136`）
- **证据片段**:
  ```java
  // RescaleStateAssembler.java:137 —— 守卫名单（scale-down 拒绝依据）
  private static boolean isPerKeyAuxiliaryOperatorState(String stateKey) {
      return stateKey.equals("internal-timers") || stateKey.equals("cep-event-time-timers")
              || stateKey.equals("trigger-accumulators") || stateKey.equals("pane-tracking")
              || stateKey.equals("reduce-state");
  }
  // ProcessOperator.java:122 —— 漏网：per-key timer（HeapInternalTimerService 条目按 (key,namespace) 存储）
  result.putOperatorState(TimerStateKeys.PROCESS_INTERNAL_TIMERS, internalTimerService.snapshotTimers());
  // EquiJoinOperator.java:152 / OverWindowOperator.java:136 —— 漏网：per-key join/over-window 缓冲
  result.putOperatorState(BUFFER_STATE /* "equi-join-buffer" */, buffer);
  result.putOperatorState(BUFFER_STATE /* "over-window-buffer" */, buffer);
  ```
- **严重程度**: P2
- **现状**: 主代码 `putOperatorState` 字面量全集（可复现 grep：`grep -rnE "putOperatorState\(" nop-stream --include=*.java`，排除测试/target/透传形参后 14 处）中，per-key auxiliary 类共 8 个键：internal-timers、cep-event-time-timers、trigger-accumulators、pane-tracking、reduce-state（5 个在名单内）+ **process-internal-timers、equi-join-buffer、over-window-buffer（3 个不在）**。排除项：cep-current-watermark、cep-key-class、source-offset、source-reader-splits、file-cursors、cdc-offsets、pending-commits、participant-* 为子任务级标量/非 per-key，1:1 拷贝合理。R5 ST-11 刚给 ProcessOperator 加了 timer 快照（"process-internal-timers"），WIN-2/4 Phase 1 守卫同期落地却未同步该键；EquiJoinOperator/OverWindowOperator 的缓冲按 `setCurrentKey(equiKey)` 逐键读写，同属会被 1:1 拷贝与 key-group 重分布撕裂的状态。注意：该守卫当前因 KR-06 处于不可达状态，故实际风险为零；一旦 KR-06 修复（守卫复活）而名单未补，scale-down 仍会静默撕裂 timer/join 缓冲与窗口内容。
- **风险**: KR-06 修复后第一波并行度缩小即复现 WIN-2 原缺陷（fired window 找不到内容被静默跳过）；守卫名单成为新的"隐藏契约点"。
- **建议**: 名单改为引用常量（`TimerStateKeys.PROCESS_INTERNAL_TIMERS` 等）并补齐三个键；中期把"per-key auxiliary operator state"改为算子自声明接口（`declaresPerKeyAuxiliaryState()`），消除字符串名单这一漂移面。
- **信心水平**: 确定（键集与名单均为封闭枚举）
- **误报排除**: 为什么不是"夸大守卫范围"——三条漏网键都满足守卫的定义标准（按 key 分片、随 keyed state 重分布而必须同迁）；也不是"已被其他机制覆盖"——scale-down 唯一防线就是这个名单。
- **复核状态**: 未复核

### [WIN-06] advanceWatermark/fireProcessingTimeTimers 采用"先收集后触发"：触发回调中新注册的 ≤ 当前 watermark 的定时器要等下一次推进才触发（Flink 为内联排水）
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java:186-207`（处理时间同构 `160-179`）
- **证据片段**:
  ```java
  // HeapInternalTimerService.java:192 —— 先把全部到期 timer 摘下，再逐个 fire
  List<Map.Entry<Long, Set<TimerEntry<K, N>>>> toFire = new ArrayList<>();
  while (true) {
      Map.Entry<Long, Set<TimerEntry<K, N>>> entry = eventTimeTimers.firstEntry();
      if (entry == null || entry.getKey() > newWatermark) { break; }
      eventTimeTimers.pollFirstEntry();
      toFire.add(entry);
  }
  for (... : toFire) { ... triggerable.onEventTime(new HeapInternalTimer<>(...)); }
  // fire 期间 onEventTime 里注册的新 timer（ts <= newWatermark）不在 toFire 中 → 错过本次推进
  ```
- **严重程度**: P3
- **现状**: Flink 的 `InternalTimerServiceImpl.advanceWatermark` 在同一排水循环内 fire，触发回调（如 ContinuousEventTimeTrigger 在 interval 边界续注册下一边界）注册的 `ts <= watermark` 的新 timer 会立即级联触发；本实现先摘后发，这类 timer 要等到 watermark 下一次前进。watermark 若长期停滞（源收尾、idle），该级联触发被无限推迟。
- **风险**: ContinuousEventTimeTrigger/Delta 类续注册触发器的中间触发延迟或缺失；与 Flink 语义漂移，通常表现为窗口晚发而非丢发，影响有限。
- **建议**: 排水循环改为"peek→poll→fire→重新 peek"的内联模式（或 fire 后循环检查 `firstEntry() <= watermark`），处理时间路径同改；补续注册触发器的级联触发测试。
- **信心水平**: 很可能（代码结构差异确凿；实际影响依赖触发器是否在回调内续注册到期 timer，未逐个触发器验证）
- **误报排除**: 为什么不是"行为等价"——`toFire` 快照在 fire 开始前定型，与 Flink 的逐 timer 重 peek 循环在"回调注册到期 timer"这一点上可观察地不同；也不是"窗口路径不会续注册到期 timer"——EventTimeTrigger.onMerge/ContinuousEventTimeTrigger.onEventTime 均会注册 `<= 当前 watermark` 的时间点（窗口已被 watermark 越过时）。
- **复核状态**: 未复核

### [KR-10] keyGroupOwnership (P, M, range) 属主戳仅在 savepoint 路径写入，周期 checkpoint 恢复时 R5 KR-2 校验恒旁路
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:432-440`（唯一写入点）与 `runtime/checkpoint/storage/CheckpointSerDe.java:769-771, 817-818`（读侧对称）
- **证据片段**:
  ```java
  // GraphModelCheckpointExecutor.java:438 —— materializeKeyGroupOwnership 全仓唯一调用点（triggerSavepoint 内）
  if (completed != null) {
      // Materialize per-subtask KeyGroupRange ownership so the savepoint records ...
      materializeKeyGroupOwnership(completed, rt.execPlan);
      savepointPath = rt.storage.storeCheckPoint(completed);
  }
  // CheckpointSerDe.java:769 —— 读侧：无戳（周期 checkpoint 产物）即静默跳过 KR-2 检查
  if (epoch.isKeyGroupOwnershipMaterialized()) {
      map.put("parallelism", epoch.getParallelism());
      map.put("maxParallelism", epoch.getMaxParallelism());
  ```
- **严重程度**: P3
- **现状**: 写/读 SerDe 本身对称，但写侧只有 `triggerSavepoint` 调用 `materializeKeyGroupOwnership`；周期 checkpoint（CheckpointType.CHECKPOINT，占运行期 epoch 的绝大多数）完成的 CompletedCheckpoint 携带的是未变形的 TaskStateSnapshot，`isKeyGroupOwnershipMaterialized()==false`。于是 R5 KR-2 的"checkpoint (P,M) 与 live 路由上界不一致即 fail-fast"只对 savepoint 生效：从周期 checkpoint 恢复时，maxParallelism 已被改动的快照不会被该检查拦截（叠加 KR-06 的 rescale 死路，实际走向 1:1 静默恢复）。
- **风险**: KR-2 防护对最常见的 epoch 形态不设防；运维对"改 M 后从自动 checkpoint 恢复"得到的是无告警的旧布局状态。
- **建议**: 在 CheckpointCoordinator 完成 epoch 的路径（与 savepoint 同一落库点前）补调 `materializeKeyGroupOwnership`；或在 restore 侧对"keyed 快照存在但无戳"的周期 checkpoint 给出 WARN，提示该 epoch 的 (P,M) 未经证明。
- **信心水平**: 确定（唯一调用点 + 读侧跳过分支均为直接代码证据）
- **误报排除**: 为什么不是"读侧已容忍即无问题"——容忍是兼容 legacy 形态的有意设计，但写侧长期不覆盖周期 checkpoint 使 KR-2 的保护范围退化为 savepoint-only，这是覆盖面缺陷而非兼容性设计本身。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|---|---|---|---|
| KR-06 | P1 | nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:1310 | keyed 标记 plan 期恒 false ⇒ Stage 35 rescale 路由生产不可达，keyed 并行度缩小恢复静默错置/丢失 keyed state |
| KR-07 | P1 | nop-stream-core/.../execution/CheckpointBarrierTracker.java:253 | 全部 keyed 快照共用裸键 "keyed-state"，链内 ≥2 个 keyed 算子时后写覆盖前写，checkpoint 静默丢状态 + 恢复交叉污染 |
| KR-08 | P1 | nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java:98 | idle→active 恢复不发 WatermarkStatus.ACTIVE，下游通道被永久逐出 watermark min-merge，事件时间停摆 |
| WIN-05 | P2 | nop-stream-runtime/.../windowing/WindowOperator.java:1621 | evictor list 路径清理/合并不清 element-timestamps 侧存储：无界泄漏 + 重复命名空间下驱逐判定被假时间戳污染 |
| KR-09 | P2 | nop-stream-runtime/.../execution/RescaleStateAssembler.java:137 | per-key auxiliary 守卫名单漏 process-internal-timers/equi-join-buffer/over-window-buffer（当前随 KR-06 一并休眠） |
| WIN-06 | P3 | nop-stream-core/.../operators/HeapInternalTimerService.java:192 | 先收集后触发：回调内续注册的到期 timer 错过本次 watermark 推进（Flink 为内联级联） |
| KR-10 | P3 | nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:438 | (P,M,range) 属主戳仅 savepoint 写入，周期 checkpoint 恢复时 KR-2 maxParallelism 校验恒旁路 |

**计数口径**：发现 7 项（P1×3、P2×2、P3×2；无 P0）。可复现 grep：
- KR-06：`grep -rn "setTargetKeyGroupRange" nop-stream --include=*.java`（main 命中仅为定义/getter，调用者全在 src/test）；`grep -rn "isVertexKeyed" nop-stream-runtime --include=*.java`（1 定义 1 使用，输入为 checkpointPlan mappings）。
- KR-07：`grep -rn "isChainable" nop-stream --include=*.java`（仅接口默认 true，无 keyed 覆写）；`grep -n "putKeyedState(\"keyed-state\"" .../AbstractStreamOperator.java`（裸键唯一写入点）。
- KR-08：`grep -rn "markActive" nop-stream --include=*.java`（main 命中仅定义与无消费者的 WatermarkOutputMultiplexer，无生成器调用）。
- KR-09：`grep -rnE "putOperatorState\(" nop-stream --include=*.java`（main 14 处，per-key auxiliary 8 键 vs 名单 5 键）。
- 排除项说明：non-per-key 标量键（cep-current-watermark、cep-key-class、source-offset、source-reader-splits、file-cursors、cdc-offsets、pending-commits、participant-*）不计入 KR-09 名单缺口；WindowOperator 迟到元素早退合并后孤儿 contents（late retire 后 cleanup timer 早退不清 contents）因触发窗口极窄（需元素恰在 WM==cleanupTime 边界到达且触发合并）未列入保留项，仅在审计过程中记录。

## 维度复核结论

> 复核人：独立审计复核员（与初审者无关），2026-10-06。方法：对 7 条发现逐条重读被引文件行号、独立重跑全部封闭式 grep（setTargetKeyGroupRange / isVertexKeyed / markActive / putOperatorState / channelIdle 写点 / CheckpointPlanBuilder.build 调用点）、对 3 个 P1 独立重推完整触发链并主动寻找反证（含 canChain 规则 8 的 ChainingStrategy 检查、InputGate 611-616 行第二处 channelIdle 写点、WindowOperatorBuilder 其他调用方）；未运行 mvn，未修改任何产品代码。

| 发现 | 判定 | 现状归属 | 复核要点 |
|---|---|---|---|
| KR-06 | 保留 P1 | 新发现（并使 R5 KR-2 / WIN-2/4 Phase 1 修复处于生产不可达的休眠面） | 三个 plan 构建点（GraphModelCheckpointExecutor:230/362、RemoteTaskDeploySupport:110）全部早于 restore（248/369-371/140）且早于 open()；字段错位实锤：provisionStateBackends(:177-189) set `stateBackend`，builder(:165) 读 `keyedStateBackend`，后者 main 代码仅 4 个算子在 open() 创建、setter 零 main 调用者；代码自认两处（AbstractStreamOperator:229 "Backend not ready yet (open() hasn't run)"、RescaleStateAssembler:565-567 "pre-open → always null"）；setTargetKeyGroupRange main 仅定义（Memory:580/RocksDB:482），buildRescaledTaskState main 唯一调用点在 `if (rescale)` 内(:1363)；MemoryStateSerDe:345-351 过滤确以 `range != null` 为门槛，scale-down 1:1 静默错置成立 |
| KR-07 | 保留 P1（修正一处例证） | 新发现 | 覆盖机制全链实锤：AbstractStreamOperator:307-312 裸键写入、tracker 裸键分支(:258-262，keyedKey 因 KR-06 恒 null)、TaskStateSnapshot.putKeyedState=map.put 后写覆盖、恢复侧 RescaleStateAssembler:577-580 两个算子都读同一裸键、注释自认(:574-576)。**修正**：`keyBy().window().apply().process()` 同链示例不成立——WindowOperator 唯一 DataStream API 创建路径 WindowedStreamImpl.transform:304-305 钉死 `ChainingStrategy.NEVER`（canChain 规则 8 拒绝），WindowOperatorBuilder 无其他生产调用方；`.process(f1).process(f2)`（DataStreamImpl:254-258 / KeyedStreamImpl:207 默认 ALWAYS）、CepOperator→下游（PatternStreamBuilder:167 → 默认 ALWAYS）、EquiJoin/OverWindow（AdvancedTransforms keyed/dataStream.transform → ALWAYS）仍可达，P1 维持 |
| KR-08 | 保留 P1 | 新发现（AR-9 早期修复只落地 IDLE 半边，AR-9 非 R5 批次项） | 三段独立重推闭环：(1) 全仓 grep `markActive` main 命中仅声明(WatermarkOutput:49)与三处实现（Multiplexer:190/222、T&WO:183-188），零调用者；(2) T&WO 内部类与 processElement 共享同一外层 `idle` 字段——processElement:98 静默翻旗标而 markActive(:183-188) 的 ACTIVE 发射无人触发，WatermarksWithIdleness.onEvent:63-67 亦无 markActive；(3) InputGate `channelIdle` 复位仅两处（:616 单通道 else 分支、:1237），均由 ACTIVE 状态元素触发，:1188-1193 空闲通道 watermark "记录但不驱动"。全通道经历一次 idle→resume 后无任何通道能驱动 min-merge，事件时间冻结成立 |
| WIN-05 | 保留 P2 | R5 已修复的新残余（R5 WIN-1 修复不完整：merge 仍不合并时间戳）+ 新发现（clear 路径泄漏为 R5 未覆盖面） | 逐分支核对成立：clearWindowContents list 分支(:1621-1627)清 list 后提前 return、MapState fallback 分支(:1633-1634)清 elementTimestamps——不对称确认；merge list 分支(:1746-1795)合并元素并清 source list，但既不并入也不清 element-timestamps；storeElementTimestamp(:1540-1553) get→add→put 追加语义使泄漏列表污染复用命名空间的驱逐判定；emitWindowContents:1061-1065 假时间戳回退确认。builder evictor⇒ListState 在 aggregate(:127)与 reduce(:150)双路径核实 |
| KR-09 | 保留 P2（随 KR-06 休眠的限定正确） | R5 已修复的新残余（R5 WIN-2/4 Phase 1 守卫名单与 R5 自家 ST-11 新增键未同步） | 封闭枚举独立复现：main 14 处 putOperatorState，per-key auxiliary 8 键 vs 名单 5 键（RescaleStateAssembler:137-141）；三个漏网键实锤——process-internal-timers（TimerStateKeys:26，ProcessOperator:122）、equi-join-buffer（EquiJoinOperator:80）、over-window-buffer（OverWindowOperator:57），后两者均为 PerKeyOrderedBuffer 按 key 分片；守卫唯一生产入口在 buildRescaledTaskState(:220) 内，随 KR-06 一并休眠 |
| WIN-06 | 保留 P3 | 新发现 | 结构确认：advanceWatermark:192-200 先全部摘下入 `toFire`、201-206 再逐个 fire，回调内续注册的 ≤watermark timer 不在批内；:187-189 `newWatermark <= currentWatermark` 早退使该 timer 在 watermark 停滞期被无限推迟；fireProcessingTimeTimers:160-179 同构。Flink InternalTimerServiceImpl 为 peek→poll→fire 内联排水，语义漂移成立；后果依赖触发器续注册行为，初审"很可能"定级恰当 |
| KR-10 | 保留 P3 | R5 已修复的新残余（R5 KR-2 读侧接线修复未覆盖周期 checkpoint 写侧） | 独立确认唯一写入点（triggerSavepoint:438，全仓无第二调用）与读侧跳过分支（CheckpointSerDe:768-778 仅 `isKeyGroupOwnershipMaterialized()` 时写 (P,M,range)、:817-824 无戳即不 set）；周期 checkpoint（含 unaligned 通道态提升的 TaskEpochSnapshot）永不带戳 → assertStampedMaxParallelismConsistency(:1433-1451) 恒跳过，KR-2 保护退化为 savepoint-only |

### P1 重点复核详情

**KR-06（rescale 恢复路由生产不可达）——独立推导链，裁定：保留 P1**

1. 时序：三个 plan 构建点全仓封闭（grep `CheckpointPlanBuilder.build` main = 3 处），分别位于 executeWithCheckpointSkeleton(:230)、prepareSavepointRuntime(:362)、RemoteTaskDeploySupport.wireDeployedSubtask(:110)；三条 restore 入口（:248 manifest 恢复、:369-371 savepoint 恢复、:140 remote 恢复）与 open()（submitAndRun :263/:404 起，算子 open() 在任务线程 invoke 内）的先后由代码自身佐证——AbstractStreamOperator.restoreState:226-231 在 keyedStateBackend 为 null 时注释"Backend not ready yet (open() hasn't run)"并挂起 pendingRestoreState，WindowOperator:476 注释"restoreState() (called before open())"。
2. 字段错位：RemoteTaskDeploySupport:105-107 javadoc 声称"provision BEFORE plan build 使 mappings 检出 keyed state"，但 provisionStateBackends(:181-186) 只调 `setStateBackend`；CheckpointPlanBuilder:165 读的是 `getKeyedStateBackend()`。`setKeyedStateBackend` main 零调用者；`createKeyedStateBackend` main 仅 ProcessOperator:71/EquiJoinOperator:128/OverWindowOperator:112/WindowOperator:492 四处，全部在 open() 内。三种部署形态（LOCAL skeleton、savepoint runtime、remote carry/spec 重建——后者同走 wireDeployedSubtask）无一处能在 build 前使 keyedStateBackend 非空。
3. 恒 false 推导：isVertexKeyed(:1502-1511) 只读 plan mappings 的 hasKeyedState() → 恒 false → rescale(:1311) 恒 false → buildRescaledTaskState（main 唯一调用 :1363）与 assertNoChannelStateOnRescale/assertNoOperatorStateOnScaleUp/KeyGroupRange 路由全部不可达；RescaleStateAssembler 自身注释(:565-567)白纸黑字承认"pre-open → always null"。
4. setTargetKeyGroupRange 封闭枚举独立复现：main 命中仅 MemoryKeyedStateBackend:580 与 RocksDBKeyedStateBackend:482 两个定义，其余 8 处全在 src/test；测试（TestKeyGroupRangeBackendRestore 等）直接调 setter/直调 buildRescaledTaskState，绕过 isVertexKeyed 生产闸门，与初审误报排除一致（另核 TestChannelStateRescaleE2E:74 注释——测试靠手工构造带 `-keyed` 键的 plan mappings 制造 vertexKeyed=true，反证生产路径无法自然到达）。
5. 后果实测链：MemoryStateSerDe:345-351 过滤以 `backend.getTargetKeyGroupRange() != null` 为门槛——生产从不 set → keyed 快照全量载入 → scale-down 同 index 1:1 静默恢复（错置+裁撤 subtask 数据丢失）、scale-up 新 index lookup 抛 ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED。
6. 反证搜寻：未发现任何 post-open 重建 plan 的路径（SupervisionLoop.rebuildTask 复用启动期同一 plan 对象，且 region restart 不改并行度）；未发现 isVertexKeyed 的其他输入源。**维持 P1**。

**KR-07（裸键 "keyed-state" 后写覆盖）——独立推导链，裁定：保留 P1（修正例证）**

1. ACK 侧：CheckpointBarrierTracker.migrateOperatorStates(:244-263)——keyedKey=getKeyedStateStorageKey(operatorIndex)（:315-324，hasKeyedState 恒 false → 返回 null）→ 走 :258-262 裸键直写；每个链内算子经 StreamTaskInvokable:501-502 的独立 snapshotCallback(opIndex) 各自 ACK 进同一 TaskStateSnapshot（triggerCheckpoint:123 每 epoch 一份）。
2. 覆盖语义：TaskStateSnapshot.putKeyedState(:89-90) 为 ConcurrentHashMap.put，后写覆盖前写；写入源 AbstractStreamOperator.snapshotState:307-312 仅在快照非空时写 "keyed-state"（双方均非空才碰撞，初审限定正确）。
3. 恢复侧：RescaleStateAssembler.buildSnapshotFromTaskState:556-582——hasKeyedState 恒 false → 两个算子都命中 :577-580 裸键回退读同一份（最后写者的）快照：先写者状态丢失、被灌入异构状态，无任何报错。
4. 可达性（反证搜寻的结果）：canChain(JobGraphGenerator:343-395) 共 8 条规则，第 8 条 ChainingStrategy 检查是初审未提及的潜在反证——核查结果：WindowOperator 经 WindowedStreamImpl.transform:304-305 以 NEVER 创建，是硬链边界，**初审"window+process 同链"例证被驳回**；但 ProcessOperator（DataStreamImpl.process:254-258）、CepOperator（PatternStreamBuilder:167 keyedStream.transform）、EquiJoin/OverWindow（AdvancedTransforms:554/742）全部走默认 ALWAYS 工厂，`.process(f1).process(f2)`（双双使用 keyed state store）、cep→process、join/over-window→process 均可成链且属普通 DataStream/DSL 用法。机制与主风险不变，**维持 P1**。

**KR-08（idle 恢复永不发 ACTIVE）——独立推导链，裁定：保留 P1**

1. IDLE 半边（已落地）：T&WO.OperatorWatermarkOutput.markIdle:175-180 发 WatermarkStatus.IDLE（AR-9）；StreamSourceOperator.markAsTemporarilyIdle:266-268 同样只发 IDLE。
2. 恢复半边（断裂）：markActive 实现(:183-188)存在但全仓 grep main 零调用者；processElement:98 直接 `this.idle = false`（内部类与外层共享同一 `idle` 字段——即静默重置了本应经 markActive 发 ACTIVE 的同一状态位）；WatermarksWithIdleness.onEvent:63-67 只清本地 isIdleNow（Flink 同名类在 onEvent 中调 `output.markActive()`，此处缺失，与初审建议一致）。
3. 下游永久逐出：InputGate channelIdle 写点全仓封闭枚举=5 处（:361/:403 初始化、:614/:616 单通道、:1222/:1237 多通道），复位（false）仅 :616 与 :1237 两处且均由 ACTIVE 状态元素触发；:1188-1193 空闲通道 watermark"记录（currentWatermarks 已更新）但不驱动 min"。结论：任何通道一次 idle→resume 后，其 watermark 永久退出 min-merge；全部通道各经历一次后 :1191 早退使新 min 永不计算，事件时间窗口/CEP 触发冻结、无告警。
4. 反证搜寻：单通道路径(:611-618)的 else 分支处理的是 ACTIVE 状态（status.isIdle()==false），不构成"非 ACTIVE 复位"反证；ChainingOutput/StreamUnionOperator/EquiJoinOperator 等仅透传状态不产生 ACTIVE。**维持 P1**。

### 与 fraud E2E 红灯的关系判断（交叉验证）

当前 HEAD 下 TestS2OfflineReshardE2E 等 4 个 fraud E2E 因 R5 FLOW-5 守卫（StreamModelDslBuilder:1119-1131/1148-1160，**构建期**拓扑守卫）红灯。复核判定：

1. **该红灯与 KR-06/KR-09 无因果关系**：FLOW-5 在 DSL 建模期 fail-fast，早于任何 restore；KR-06/KR-09 位于 restore 期状态装配层。修复 KR-06 不会使这些测试转绿，反之亦然。
2. **fixture 修复转绿不会走进 KR-06 的"不可达"分支**：S2 家族四个场景的 run 间作业并行度均不变——fraud-s2-file.stream.xml 声明 parallelism="1" 且 buildEnv 不改并行度；TestS2OfflineReshardE2E 只离线迁移动 maxParallelism（128→256），作业 P 恒定；TestS2RecoveryAndRescaleE2E javadoc（:46-54）明确声明 restore-time rescale 的 P>1 形态不在覆盖范围；TestS2RestoreRescaleMultiJvmE2E 的 "TM 2→3" 仅改承载拓扑（MiniStreamCluster/S2ScenarioPipelineFactory 均不设并行度，作业 P 来自 XML）。因此对齐并行度声明后这些测试走的是同并行度 1:1 恢复路径——转绿**既不反驳也不覆盖** KR-06；整个 fraud E2E 家族（按设计）从未测过并行度变更恢复这一分支。
3. **反向关系**：若将来有人把 drill 改成真正的 P 变更恢复（如 keyed vertex 2→3），将恰好落入 KR-06 的死分支——scale-up 走 1:1 lookup 抛通用 RESTORE_FAILED、scale-down 静默错置——不修 KR-06 该 E2E 无法合法转绿。KR-06 是未来任何真 rescale E2E 的先决阻塞项，本红灯反而凸显 rescale 路径零运行时覆盖的现状。
4. KR-09 的守卫位于死分支内部，fixture 修复不改变其休眠状态。

## 最终保留项（复核后）

| 编号 | 复核后严重程度 | 文件路径 | 一句话摘要 | 现状归属 |
|---|---|---|---|---|
| KR-06 | P1 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1310 | keyed 标记 plan 期恒 false ⇒ Stage 35 rescale 路由生产不可达，keyed 并行度缩小恢复静默错置/丢失 keyed state（扩大时抛通用错误） | 新发现（R5 KR-2/WIN-2/4 Phase 1 修复随之休眠） |
| KR-07 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:253 | 全部 keyed 快照共用裸键 "keyed-state"，链内 ≥2 个非空 keyed 算子（process×2 / cep→process / join·over→process；window 因 ChainingStrategy.NEVER 除外）时后写覆盖前写，checkpoint 静默丢状态 + 恢复交叉污染 | 新发现 |
| KR-08 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimestampsAndWatermarksOperator.java:98 | idle→active 恢复不发 WatermarkStatus.ACTIVE（markActive 零调用者），下游通道被永久逐出 watermark min-merge，全通道空闲过一次后事件时间停摆 | 新发现（AR-9 只落地 IDLE 半边） |
| WIN-05 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:1621 | evictor list 路径清理/合并不清不并 element-timestamps 侧存储：无界泄漏 + 复用命名空间下驱逐判定被假时间戳污染 | R5 已修复的新残余（WIN-1 修复不完整）+ 新发现（clear 路径） |
| KR-09 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RescaleStateAssembler.java:137 | per-key auxiliary 守卫名单漏 process-internal-timers/equi-join-buffer/over-window-buffer（当前随 KR-06 一并休眠，实际风险为零） | R5 已修复的新残余（WIN-2/4 Phase 1 名单与 ST-11 不同步） |
| WIN-06 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java:192 | 先收集后触发：回调内续注册的到期 timer 错过本次 watermark 推进，watermark 停滞期被无限推迟（Flink 为内联级联） | 新发现 |
| KR-10 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:438 | (P,M,range) 属主戳仅 savepoint 写入，周期 checkpoint 恢复时 R5 KR-2 maxParallelism 校验恒旁路 | R5 已修复的新残余（KR-2 读侧已接线、写侧未覆盖周期 checkpoint） |

**复核统计**：7/7 保留（3 P1、2 P2、2 P3 维持原级，无升降级、无驳回）；1 处例证修正（KR-07 的 window 链例）不影响定级。现状归属：新发现 ×4（KR-06/07/08、WIN-06）、R5 已修复的新残余 ×3（WIN-05、KR-09、KR-10，其中 WIN-05 兼有新发现面）。
