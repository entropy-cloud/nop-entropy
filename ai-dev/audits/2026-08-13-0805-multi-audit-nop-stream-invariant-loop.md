# 2026-08-13-0805 nop-stream 多维审计报告

> Audit Status: closed
> Audit Type: multi-dimensional
> Mission: nop-stream-invariant-loop
> Processed: 2026-08-13 — P1-INV-2/P1-INV-1 → plan `ai-dev/plans/2026-08-13-1243-1-nop-stream-engine-state-recovery-and-timer-fixes.md`；P1-XDSL-5/P1-XDSL-6/P1-09-02 → plan `2026-08-13-1243-2-nop-stream-flow-dsl-contract-fixes.md`；P1-DOC-01/02/03/P1-01-01 → plan `2026-08-13-1243-3-nop-stream-cross-module-contract-remediation.md`；P2 全部（26 条）→ roadmap `## Follow-up Backlog`（`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` 2026-08-13 0805 multi-audit 批次）

## 基本信息

- **审核模块**: `nop-stream/` 全模块组（core / runtime / cep / flow / rocksdb / connector / connector-batch / connector-jdbc / connector-debezium / fraud-example，共 10 子模块，620 main + 483 test Java 文件）
- **审核日期**: 2026-08-13
- **审核范围**: 代码、配置（beans.xml / xdef / XDSL 实例）、测试、公共契约（导出面、API 表面、BOM）、架构文档一致性（ai-dev/design/nop-stream/*、docs-for-ai 锚点）
- **执行维度**: 01 依赖图与模块边界 / 03 API 表面积与契约 / 08 IoC 与 Bean 配置 / 09 错误处理与错误码 / 10 XDSL 与 XLang / 11 模型对齐 / 15 类型安全 / 16+21 测试覆盖与有效性 / 18 文档-代码一致性 / 20 跨模块契约 + 不变式家族专项（窗口 / 2PC Sink / CEP / Checkpoint）
- **方法**: 7 个并行审计子 agent（4 波第一轮 + 3 波复核），高风险发现经独立复核 agent 逐条验证（fraud-example 样例、ioc:default 冲突实测实验、triggerAccumulators 泄漏、InputGate 饿死、BOM 幽灵条目、RocksDB 降级）；关键证据由主 agent 抽查复核（grep 交叉验证）。

## 执行统计

| 波次 | 维度 | 初审发现 | 独立复核 | 复核结论 |
|------|------|---------|---------|---------|
| W1 | 01+20 依赖与跨模块契约 | 7 | 01-02, 01-01 | 01-02 降级 P1→P2；01-01 保留 P1 |
| W1 | 03+08 API 表面与 IoC | 5 | 03-01 | 03-01 降级 P1→P2（实测确认冲突但无生产加载路径） |
| W1 | 09+15 错误处理与类型安全 | 15 | 09-01, 09-09 | 09-01 降级 P1→P2；09-09 降级 P1→P2 |
| W1 | 不变式族（窗口/2PC/CEP/Checkpoint） | 8 | INV-1, INV-2 | INV-1 维持 P1（长运行引擎泄漏）；INV-2 维持 P1 |
| W2 | 16+21 测试覆盖与有效性 | 10 | —（低风险批量，P2 全部） | 保留 |
| W2 | 18 文档-代码一致性 | 11 | DOC-01/02/03 | 保留 P1×3（独立核对过代码侧证据） |
| W2 | 10+11 XDSL 与模型 | 14 | XDSL-1（fraud 样例） | XDSL-1 降级 P0→P2（死文件，零运行时影响） |

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 9 | 输入对齐超时饿死、trigger 状态泄漏、DSL 声明属性静默忽略×2、flow 错误码体系脱节、rocksdb split-package、文档契约漂移×3 |
| P2 | 22 | 死代码/死错误码、测试反模式、bean 注释契约矛盾、示例文件破损、行号漂移等 |

---

## P1 发现

### [P1-INV-2] InputGate 对齐超时 / unaligned 逃生检查被持续数据流饿死（barrier 对齐家族）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:472-536`
- **证据片段**:
  ```java
  while (true) {
      // 内层循环：任何 channel 读到记录立即 return（:508）
      ... return Optional.of(element);
      // 整轮 sweep 无任何返回才执行 elapsed 检查（:515-536）
      if (oldest != null && barrierAlignment && ...) {
          if (unalignedCheckpointEnabled && elapsed > unalignedThreshold) // 1s 逃生
              return switchToUnalignedAndEmit(oldest);
          if (elapsed > barrierAlignmentTimeout) // 30s 超时
              throw new StreamException(ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT)...
      }
  }
  ```
- **严重程度**: P1 — 违约行为：默认开启的 unaligned 逃生（1s）与对齐超时 fail-fast（30s）在其设计目标场景（背压慢流持续有数据）下永不触发。
- **现状**: elapsed 检查仅在"整轮 sweep 无数据"（全 channel 空转 50ms）时评估；对齐期间已收 barrier 的 channel 被阻塞、剩余活跃 channel 持续出数据 → 每次 read() 都在 :508 返回，检查永不执行。独立复核确认控制流并补强后果链。
- **风险**: 对齐卡死时失败检测从设计值 1s/30s 劣化到 coordinator 侧 `checkpointTimeout`（默认 600s）→ 每个卡住的对齐等 10 分钟才 abort，连续 checkpoint 超时失败（默认 3 次阈值）可致作业失败；unaligned 模式下本应捕获的 in-flight ChannelState 永不捕获，恢复语义退化。违反 checkpoint-design §2.4/§2.11 契约。
- **建议**: elapsed 检查移到每次 read() 入口（与数据返回解耦）或由独立 timer 驱动；补"对齐期间活跃 channel 持续出数据"的回归测试（现有 3 个相关测试全部用 stuck 空 channel，恰好覆盖不到）。
- **信心水平**: 确定
- **误报排除**: 独立复核逐行确认控制流 + 全库 elapsed 检查仅此一处；现有测试场景（`TestInputGateAlignmentTimeout`/`TestUnalignedCheckpointBackpressure` 的 stuck channel）正是检查**能**运行的场景，反证持续流量路径未被覆盖。
- **复核状态**: 已保留（独立复核维持 P1）

### [P1-INV-1] WindowOperator.triggerAccumulators 无界泄漏 + timer 路径 PURGE 不对称（窗口家族第三次同类变体）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:258, 543-551, 587, 2043-2071`
- **证据片段**:
  ```java
  protected Map<String, SimpleAccumulator<?>> triggerAccumulators;   // :258 只 put 不 remove
  // snapshotState :543-551 —— 每 checkpoint 全量 clone 全部条目
  for (Map.Entry<String, SimpleAccumulator<?>> entry : triggerAccumulators.entrySet()) { ... }
  // onEventTime PURGE 分支 :814-819 —— 只 clearWindowContents，无 triggerContext.clear()
  // （元素路径 :762-765 有 clear，不对称）
  ```
- **严重程度**: P1 — 确定的内存/快照体积泄漏（长运行引擎核心算子）。
- **现状**: `triggerAccumulators` 仅在 :398 init、:536 close 清空、:543 快照、:587 恢复、:2044 put——全库无任何 `remove()`/`clear()` 调用（grep 验证）；cleanup/merge/retire 路径均不删除条目；独立复核确认"值被 clear 重置但条目残留"，CountTrigger 计数残留推论不成立（时间路径永不 FIRE），但**无界增长 + checkpoint 体积膨胀 + 恢复后死条目永存**确定成立。
- **风险**: 长运行窗口作业（正是 nop-stream 的设计定位）map 无限增长 → OOM；每 checkpoint 全量拷贝死条目 → 快照体积随作业寿命增长；恢复后死条目永久存在。`TestTriggerAccumulatorsCheckpoint` 只测 round-trip 不测清理。
- **建议**: 在 `clearWindowContents`/`retireWindow`/merge 路径删除 `trigger_<key>\u0000<window>\u0000*` 前缀条目；timer 路径 PURGE 补 `triggerContext.clear()`；补"清理后条目被移除"的回归测试。
- **信心水平**: 确定
- **误报排除**: 独立复核确认事实层面全部成立；与 `paneTracking`（:1398 有 `paneTracking.remove(paneKey)`）形成同文件对照，证明非设计意图而是漏删。
- **复核状态**: 已保留（独立复核确认泄漏成立，按长运行生产引擎定位维持 P1）

### [P1-XDSL-5] DSL `<edge>` 的 partition/keyExpr/流控属性全部静默忽略，resolveEdgePartition 是死代码

- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:208-263, 414-422`
- **证据片段**:
  ```java
  public PartitionPolicy resolveEdgePartition(String from, String to) {   // 全仓仅定义处，0 调用者
      ...
      PartitionPolicy p = e.getPartition();
      return p == null ? PartitionPolicy.FORWARD : p;
  }
  // buildTransforms 中 edges 仅用于拓扑排序；grep getFlowControlPolicy/getKeyExpr
  // 在 main 代码 0 引用 → 声明 HASH/REBALANCE/BROADCAST 的边被静默降级为 FORWARD
  ```
- **严重程度**: P1 — 违约行为：数据分区语义静默失效且无任何警告，与 builder 注释宣称的 "Anti-Hollow guarantees" 直接矛盾。
- **现状**: `resolveEdgePartition` 定义后从未被调用（主 agent grep 复核确认）；edge 的 `partition`/`keyExpr`/`flowControlPolicy`/`queueCapacity`/`receiveWindow`/`packetSize` 六个属性在 main 代码零消费；`test-reduce-pipeline.stream.xml:22` 的 `partition="HASH"` 恰好依赖此静默降级才能通过测试——测试"通过"掩盖了分区未实现。
- **风险**: 分布式场景下 keyBy 语义的 HASH 分区、重平衡全部失效 → 结果错误且无诊断；违反 No-Silent-No-Op 原则。
- **建议**: 实现分区应用（HASH 经 keyExpr 映射到 keyBy/partition API、REBALANCE/BROADCAST 对应 DataStream 算子），未实现时对非 FORWARD 边构建期 fail-fast。
- **信心水平**: 确定
- **误报排除**: 主 agent 复核 `rg resolveEdgePartition` 仅命中定义行；5 个属性 getter 在非 `_gen` main 代码 0 引用。
- **复核状态**: 已保留（主 agent 抽查复核）

### [P1-XDSL-6] DSL checkpoint 配置 6 字段与窗口策略 trigger/allowedLateness/accumulationMode 静默忽略

- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:124-150`；`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:108-158`
- **证据片段**:
  ```java
  // applyCheckpointConfig 仅消费 enabled/interval/processingGuarantee/timeout/maxConcurrentCheckpoints/minPause/
  // maxRetainedCheckpoints/jobTerminationMode；barrierAlignmentTimeout/maxConsecutiveCheckpointFailures/
  // storageType/jobId/pipelineId/<storageConfig> 六个 getter 在 main 代码 0 引用，也无 fail-fast
  // buildWindow 只读 strategyRef/windowFnId；triggerId/allowedLateness/accumulationMode getter 0 引用
  ```
- **严重程度**: P1 — 违约行为：用户声明的严格一致性配置（barrier 对齐超时、storageType）与窗口语义（允许迟到、累积模式）被静默丢弃。
- **现状**: 用户配置 `barrierAlignmentTimeout`/`storageType`/`storageConfig` 被静默丢弃；声明 `ACCUMULATING` 得到 `DISCARDING` 行为；迟到事件处理与触发时机与声明不符。
- **风险**: checkpoint 行为与声明不符，生产故障难定位；窗口结果错误（迟到事件被丢）无任何诊断。
- **建议**: 实现字段映射；短期内至少对"非默认值即 fail-fast"（复用 `failFastOnUnsupportedRegistries` 机制）。
- **信心水平**: 确定
- **误报排除**: 6 个 checkpoint getter + 3 个窗口 getter 逐一 grep 0 命中；`TestAdvancedTransforms` 中 `triggerId="t"` 也从未被消费，证明非测试盲区。
- **复核状态**: 已保留

### [P1-09-02] nop-stream-flow 模块 58 处裸异常、零错误码/模块异常类使用

- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:88,158-188,199-220,255,294-391,432`；`AdvancedTransforms.java:100-372`（共 58 处）
- **证据片段**:
  ```java
  if (model.hasStreams()) {
      throw new UnsupportedOperationException(
              "not yet implemented: <streams> registry has no execution consumer");
  }
  throw new IllegalArgumentException("Duplicate Stream DSL transform id: " + t.getId());
  ```
- **严重程度**: P1 — 契约漂移：整个模块脱离 Nop 两档错误处理体系（模块内可用 `StreamException`，但裸 `RuntimeException` 子类被 AGENTS.md 明令禁止）。
- **现状**: flow 模块 grep `NopStreamErrors|ERR_STREAM_|StreamException` 全部 0 命中；58 处抛裸 IAE/UOE/ISE。
- **风险**: 上层无法按错误码程序化处理；DSL 规划期错误（用户配置错误 vs 引擎缺陷）无法区分；`getErrorCode()` null 使统一错误上报失效。
- **建议**: 引入 `StreamException(ERR_STREAM_...)` 或至少 `StreamException(String)`；为高频错误（duplicate id、not-yet-implemented）定义错误码。
- **信心水平**: 确定
- **误报排除**: error-handling.md 反模式表明确禁止裸 RuntimeException；消息本身英文清晰仅类型不合规。
- **复核状态**: 已保留

### [P1-01-01] nop-stream-rocksdb 类驻留在 core 命名空间，跨 jar split-package

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/**`（17+ 类）；对比 core 的 `io.nop.stream.core.common.state.backend.{memory/*}`
- **证据片段**:
  ```
  io/nop/stream/core/common/state/backend/rocksdb/        ← rocksdb 模块自身类（非 io.nop.stream.rocksdb.*）
  io/nop/stream/core/common/state/backend/rocksdb/incremental/
  ```
- **严重程度**: P1 — 模块边界违反：唯一一个不遵守"io.nop.<module>.*"命名空间约定的子模块（其余 9 个均遵守）。
- **现状**: 历史拆分（代码从 core 迁出未重打包）残留；runtime/cep/connector 均用独立命名空间，仅 rocksdb 污染 core 命名空间。
- **风险**: (a) 消费者无法从 FQCN 判断类归属 jar；(b) split-package 在 JPMS 下非法，core 未来新增同名包内类会类冲突；(c) 检索 core 状态后端实现时遗漏 rocksdb 模块。
- **建议**: 迁移到 `io.nop.stream.rocksdb.*` 命名空间（实现 core 接口无需同包）。大范围重命名，需 plan-first + 人工确认（结构性重构）。
- **信心水平**: 确定
- **误报排除**: Nop 平台各模块均使用独立命名空间；违规点在"实现类放置于 core 命名空间"而非依赖方向（依赖方向正确）。
- **复核状态**: 已保留（独立复核未发现反驳证据）
- **处置记录（2026-08-14，successor 执行完成）**: plan `2026-08-13-1243-3` Phase 3 预设路径「未批准 → 取消 + successor」→ 2026-08-14 用户人工批准 → successor = plan `2026-08-14-0900-2` 执行完成（commit `6b742c86d`）：17 main + 13 test 类迁移至 `io.nop.stream.rocksdb[.incremental]`，runtime test 7 文件引用同步（含 FQN 内联 2 文件 6 处），grep 零残留，全量回归 3050 tests / 0 failures，独立 closure audit PASS，roadmap backlog 条目已 closed。本条目 = **closed**。

### [P1-DOC-01] checkpoint-design §2.4 声称"重叠 barrier 抛 ERR_STREAM_CHECKPOINT_ABORTED"，与代码及文档自身 §2.8.1 D1 三方矛盾

- **文件**: `ai-dev/design/nop-stream/checkpoint-design.md:101`（§2.4 ALIGNING 实现状态表）vs `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:87-89`
- **证据片段**:
  ```java
  // InputGate.java:87-89（Stage 45 实际行为）
  * Replaces the legacy single pendingBarrier/... fields so that overlapping barrier ids
  * no longer throw and an aborted epoch's straggling barrier is discarded instead of
  * corrupting the next epoch's alignment (design §2.8.1 D1).
  ```
- **严重程度**: P1 — 文档契约漂移：设计文档的"实现状态表"描述 Stage 45 前的旧行为，与 live code 和文档自身 D1 决策矛盾；`ERR_STREAM_CHECKPOINT_ABORTED` 在 InputGate 仅 import，唯一抛出点在 GraphModelCheckpointExecutor abort 路径。
- **现状**: §2.4 表格未随 Stage 45 多 epoch 对齐重构更新。
- **风险**: 读者按 §2.4 理解对齐语义会误判多 in-flight 行为；排查 abort/对齐 bug 时被误导。
- **建议**: 修改 §2.4 ALIGNING 行：删除"重叠 barrier 抛错"，改为"重叠 barrier id 按 in-flight 集合逐 id 对齐，迟到/被 abort 的 barrier 被丢弃（§2.8.1 D1）"。
- **信心水平**: 确定
- **误报排除**: 已全文检索该错误码在 InputGate 无抛出语句（仅 import），并对照 D1 原文。
- **复核状态**: 已保留

### [P1-DOC-02] checkpoint-design §2.5 声称快照含"输入 channel watermark 和 idle 标记"，代码无此状态

- **文件**: `ai-dev/design/nop-stream/checkpoint-design.md:177`（§2.5 Snapshot 内容表）vs `TaskEpochSnapshot.java`（全 201 行无 watermark/idle 字段）
- **证据片段**:
  ```
  | watermark state | 输入 channel watermark 和 idle 标记 |   ← §2.5 契约行
  ```
  实现侧：`TaskEpochSnapshot`/`TaskStateSnapshot`/`EpochManifest` 无 watermark/idle 字段；checkpoint 两个包对 watermark 零引用；唯一持久化点是 `HeapInternalTimerService.java:238` 的 task 级 currentWatermark（随 timer state），非 per-channel。
- **严重程度**: P1 — 文档契约漂移：§2.5 是事实性契约表，watermark 行未标注 spec-only，其他行（channel state）均标注落地状态。
- **现状**: 恢复后各 channel 水位归零重爬；timer 服务级水位恢复有单调守卫掩盖，窗口正确性暂不受损。
- **风险**: 若未来依赖恢复后输入水位/空闲状态语义（如恢复后立即触发事件时间定时器），产生文档承诺之外的语义缺口。
- **建议**: 将该行标注"spec-only / 未实现"；或落地 TaskEpochSnapshot.channelWatermarks 并接线 InputGate 快照（对应 [P2-INV-7]）。
- **信心水平**: 确定
- **误报排除**: 已分别检索 checkpoint 两包及全部算子 snapshot 路径，唯一 watermark 持久化点是 timer 服务内部水位，语义不符。
- **复核状态**: 已保留

### [P1-DOC-03] nop-stream/README.md 声称 DISTRIBUTED 模式"规划中"，实际已实现

- **文件**: `nop-stream/README.md:7`
- **证据片段**:
  ```
  > **注:** RuntimeTopology 处于规划阶段，LOCAL 模式当前通过 GraphExecutionPlan 直接执行，
  > DISTRIBUTED 模式规划通过 IStreamExecutionDispatcher 调度。
  ```
  实际：`EmbeddedDistributedExecutor.java` / `RpcDistributedExecutor.java` 均实现 `IStreamExecutionDispatcher`；控制面 RPC（Stage 39）、数据面后端（Stage 40）、remote-deploy 多 JVM（Stage 42）全部落地，含 E2E 测试（主 agent 复核确认文件存在）。
- **严重程度**: P1 — 文档契约漂移：README 是普通读者第一入口，声称分布式不可用（"规划中"）与代码及 01-architecture-baseline.md 直接冲突。
- **现状**: RuntimeTopology 类确实 0 Java 引用（概念性阶段），但"DISTRIBUTED 规划中"半句严重过期。
- **风险**: 用户据 README 判定分布式不可用而放弃使用或重复调研。
- **建议**: 更新为"LOCAL 经 GraphExecutionPlan + TaskExecutor；DISTRIBUTED 经 IStreamExecutionDispatcher（EmbeddedDistributedExecutor / RpcDistributedExecutor，含 Stage 39-42 跨 JVM 能力）"。
- **信心水平**: 确定
- **误报排除**: 已确认 RuntimeTopology 0 Java 引用、两执行器与 E2E 均存在，README 只能解释为过期。
- **复核状态**: 已保留

---

## P2 发现

### [P2-INV-3] builder（生产）evictor 路径不记录元素时间戳，TimeEvictor 静默失效
- **文件**: `WindowOperator.java:924-935, 1336-1349`；`WindowOperatorBuilder.java:115-123`
- **证据**: `elementTimestampsState` 仅在 `windowStateDescriptor == null` 分支创建；builder 四个入口全部传非空 descriptor → evictor 收到的时间戳全部是当前 watermark。`TestWindowOperatorEvictorTimestamps` 只用 legacy 路径，未覆盖生产 builder 路径。
- **风险**: evictor 时间语义静默漂移；TimeEvictor（API 预留）变为 no-op。
- **建议**: builder 路径也创建并配对维护 elementTimestampsState；或对时间敏感 evictor 构建期 fail-fast。

### [P2-INV-4] 持久化 checkpoint JSON 恢复对 POJO key 静默错配（keyed state + timer + CEP 全受影响）
- **文件**: `MemoryStateSerDe.java:814-820`；`HeapInternalTimerService.java:314-320`；`WindowOperator.java:1056-1065`
- **证据**: `serializeKey` 原样进出无类型信息；POJO key 经 JsonTool 恢复为 LinkedHashMap → `TypedNamespaceAndKey.equals` 失配 → keyed state 静默"消失"（lookup miss 而非报错）。String/Long 内置类型安全。
- **风险**: POJO key 作业从外部存储 checkpoint 恢复后全部 keyed state 静默从空状态继续——无声数据错误；违反 state-management-design §6.4 JsonTool round-trip 约束。
- **建议**: key 序列化加类型信息（`@type` + 按 keyClass 重建），或对非内置类型 key 持久化路径 fail-fast。

### [P2-INV-5] FileTwoPhaseCommitSink：失败 checkpoint 的孤儿 temp 文件 + manifest 跨作业 epoch 冲突
- **文件**: `nop-stream-connector/.../file/FileTwoPhaseCommitSink.java:131-142, 168-181`
- **证据**: (1) checkpoint N 快照失败后全局恢复时，N 批次不在任何 durable 快照 → `.epoch-N.tmp` 永久孤儿；(2) manifest key 仅 epochId，两作业复用 outputDir 且 epoch 重叠时新作业 commit 被跳过并丢批次。
- **风险**: 磁盘孤儿累积；跨作业复用目录时数据丢失（配置误用场景无防护）。
- **建议**: 恢复时扫描清理孤儿 temp；manifest key 加 pipeline/job 前缀。

### [P2-INV-6] NFAState 的 PriorityQueue 经 JSON 持久化恢复后类型/顺序可能失效（猜测）
- **文件**: `nop-stream-cep/.../nfa/NFAState.java:35-37`；`MemoryStateSerDe.java:844-864`
- **证据**: `Queue<ComputationState>` 接口字段经 JsonTool 物化类型未验证；`COMPUTATION_STATE_COMPARATOR` 静态字段无法随 JSON 恢复。若物化为无比较器队列 → 恢复即抛错；内存路径（现有测试）无此问题。
- **风险**: CEP 作业从持久化 checkpoint 恢复后崩溃或匹配乱序。
- **建议**: NFAState 增加显式 JSON 兼容构造（重注入比较器）；补"持久化 JSON round-trip CEP 状态"测试。信心中等——需一次 JsonTool 实测确认。

### [P2-INV-7] InputGate 通道 watermark 状态未进 checkpoint（设计 §2.5 契约缺口，与 DOC-02 同源）
- **文件**: `InputGate.java:77`（`currentWatermarks` 纯内存）
- **证据**: 无 snapshot/restore 接口；恢复后通道水位归零重爬；下游窗口有 timer 单调守卫缓解。
- **建议**: 将通道水位（或 min）加入 TaskEpochSnapshot。

### [P2-INV-8] CheckpointBarrierTracker 无 checkpointId 的 ACK 在多 in-flight 下可致提前完成
- **文件**: `CheckpointBarrierTracker.java:167-178`
- **证据**: `cpId < 0` 回退 `mostRecentInFlight()`，多 epoch 时旧 ACK 记到新 epoch 头上；有 WARN 无防护。生产路径均打标，仅 legacy/第三方构造触发。
- **建议**: 回退路径 fail-fast 或按 operatorIndex 去重。

### [P2-01-02] 平台 BOM 含幽灵条目（nop-stream-api / nop-stream-checkpoint）+ 缺失 4 个真实模块；tests/pom.xml 引用不存在 artifact
- **文件**: `nop-bom/pom.xml:1184-1188, 1256-1262`；`tests/pom.xml:864-868, 888-892`（主 agent grep 复核确认）
- **证据**: BOM 声明 `nop-stream-api`（L1186）、`nop-stream-checkpoint`（L1258），仓库无此两模块（git e13895fd8 删除空壳模块时未同步）；缺 connector-batch/jdbc/debezium/rocksdb 四条目。
- **风险**: tests 模块一旦重新启用（根 pom 仅注释）依赖解析必失败；外部消费者无法经 BOM 管理 4 个真实模块版本。独立复核确认 dependencyManagement 不触发解析、当前构建不受影响 → P1→P2。
- **建议**: 删幽灵条目、补 4 个真实模块条目、tests/pom.xml 清理。

### [P2-03-01] 两个 beans.xml 重复声明同一 ioc:default bean id，同容器加载必然失败
- **文件**: `stream-control-rpc.beans.xml:34-35` + `stream-data-plane.beans.xml:68-69`
- **证据**: 独立复核实测：`addResource` 第二个文件立即抛 `ERR_IOC_DUPLICATE_BEAN_DEFINITION`（ioc:default 去重逻辑被注释于 BeansDefinition.java:81-89）；两文件注释均指引"deployed together"为预期用法（依赖已失效的机制）。
- **风险**: 按注释部署必失败；但生产容器不自动加载（`isAppBeans` 过滤仅 app-*.beans.xml），测试刻意单文件加载回避 → 无仓库内触发路径。
- **建议**: 移除 control-rpc 中的 streamMessageService 声明（注释改为"由 data-plane 提供"），或恢复 ioc:default 去重（平台级，plan-first）。backlog P2-11 已登记，本审计独立确认。

### [P2-03-02/XDSL-1~4] fraud-detection.stream.xml 是破损的"唯一生产 DSL 示例"（4 处缺陷）
- **文件**: `nop-stream/nop-stream-fraud-example/src/main/resources/_vfs/nop/stream/demo/fraud-detection.stream.xml:13-14, 31-34, 70-72, 94, 99-108`
- **证据**: (1) 根元素 `x:extends="/nop/schema/stream/stream.xdef"` 缺 `x:schema`（实测加载抛 ERR_XDSL_NO_SCHEMA；"缺 xmlns:x 致解析失败"子表述被复核驳回——Nop 解析器容忍前缀未绑定，失败点是 x:schema）；(2) `bean="transactionSourceFunction"` 全仓唯一悬空引用；(3) `<cep>` 上游是 `<map>` 非 KeyedStream + cep 缺 bean 属性（AdvancedTransforms.java:286-290, 304-308 双重 fail-fast）；(4) `windowTime="30s"` 在非循环 part 上被 CepPatternBuilder 丢弃。
- **风险**: 误导使用者照抄；独立复核确认死文件（零 Java 引用、fraud-example 无 flow/xlang 依赖）→ P0 降级 P2。
- **建议**: 修根元素写法 + 补 keyBy + 补 bean/内联 + 修 pattern 时间窗，或加一个加载该文件的测试钉住。

### [P2-09-01] RocksDBKeyedStateBackend 恢复路径 listColumnFamilies 失败静默降级 + Options 句柄泄漏
- **文件**: `RocksDBKeyedStateBackend.java:204-209`；`RocksDBIncrementalRestore.java:148-155, 186-191`
- **证据**: catch RocksDBException → 空列族列表/仅默认 CF，无日志无 rethrow；`cfNameOf` 失败伪装 `"__default__"` 被 isDefaultCf 跳过。独立复核：数据丢失路径几乎不存在（list 失败通常伴随 open 失败 fail-fast），但恢复路径违背自声明契约（RocksDBKeyedStateBackend.java:830-832 注释"throws rather than silently degrading"）+ 诊断黑洞；L207 `new Options` 从未 close 泄漏原生句柄（复核补充）。
- **建议**: 区分"首次启动空目录"（允许）与"损坏/权限错误"（LOG + rethrow）；Options 用 try-with-resources。

### [P2-09-03/05/08] 死错误码 11 个 + 对应场景抛裸异常（SstFileChecksum / CheckpointCoordinator 配置校验）
- **文件**: `SstFileChecksum.java:56-59`；`CheckpointCoordinator.java:1355-1367`；`NopStreamErrors.java`（死码清单）
- **证据**: `ERR_STREAM_HASH_NOT_AVAILABLE` 定义存在但 SstFileChecksum 抛裸 ISE（对照组 StateSchemaResolver 正确使用）；`ERR_STREAM_CONFIG_ERROR` 死码但 validateIncrementalConfig 抛裸 UOE/ISE；11 死码含窗口聚合器 3 个（对应场景抛字符串 StreamException）。
- **建议**: 激活错误码替换裸异常，顺手消灭死码。

### [P2-09-06] ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT 缺 {detail} 占位符，诊断信息被丢弃
- **文件**: `NopStreamErrors.java:222-224`；`WindowOperator.java:1588-1591`
- **证据**: 声明 ARG_DETAIL 但描述无占位符；抛出点传了 targetType 关键诊断信息 → i18n 渲染后丢弃。`ERR_STREAM_CHECKPOINT_EXECUTOR_SAVEPOINT_FAILED` 同病（:82-83）。
- **建议**: 描述追加 `": {detail}"`。

### [P2-09-07] WindowOperatorFactoryImpl 类型推断 catch 吞异常无日志
- **文件**: `WindowOperatorFactoryImpl.java:70-78`
- **证据**: `createAccumulator()` 异常时静默降级 Object 类型，无 LOG；降级后 RocksDB 状态路径可能延迟报错且与根因脱节。
- **建议**: catch 内 `LOG.warn(..., e)`。

### [P2-09-02b] core/runtime 错误路径裸 IllegalStateException 8 处 + flow 58 处（见 P1-09-02）+ 参数校验裸 IAE ~70 处
- **文件**: `LocalSourceCoordinator.java:127,150,267,274`；`KeyGroupReshard.java:97,104,116`；`StateSegmentDescriptor.java:95`；`KeyGroupAssignment.java` 等
- **证据**: 消息英文清晰、cause 保留、fail-fast 意图明确，仅异常类型不合规；`ERR_STREAM_INVALID_ARG`/`ERR_STREAM_STATE_ERROR` 死码可覆盖。
- **建议**: 批量替换为 `StreamException(ERR_STREAM_...)`；优先核心错误路径。

### [P2-09-10] 资源清理 catch 静默吞异常（3 处）
- **文件**: `FileTwoPhaseCommitSink.java:292-298`；`DebeziumCdcSourceFunction.java:151-163`
- **证据**: best-effort 清理无日志；temp 文件删除失败残留孤儿无从发现。
- **建议**: LOG.warn 留证。

### [P2-15-01/02] 自定义异常 ProcessingTimeCallbackException extends RuntimeException；Map<String,Object> 353 处
- **文件**: `TaskProcessingTimeService.java:152-159`；`TaskStateSnapshot.java:24-25` 等
- **证据**: 内部 marker 异常未继承 NopException；Map 使用集中于 checkpoint 序列化边界（平台常见模式，仅部分可结构化）。
- **建议**: 改 extends StreamRuntimeException；序列化边界保留，内部传递可引入轻量 DTO。

### [P2-03-03] Configuration.java / StreamConstants.java 为空占位类
- **文件**: `nop-stream-core/.../configuration/Configuration.java:13-21`；`StreamConstants.java:10`
- **证据**: 两类零成员零引用；实际配置键宿主是 NopCepConfigs；空壳公共类型进入导出面误导调用方。
- **建议**: 删除或并入 NopCepConfigs 统一宿主。

### [P2-03-04] execution 包 4 个跨模块内部类未标 @Internal
- **文件**: `InputGate/SubtaskTask/ResultPartition/RecordWriter.java`（类声明）
- **证据**: 同包 16 个类均标注（CheckpointBarrierTracker/TaskExecutor 等），4 个执行内核类漏标；runtime 跨模块继承（RemoteResultPartition extends ResultPartition）。
- **建议**: 补 @Internal + 同步 source-anchors。

### [P2-03-05] beans.xml 声明未使用的 feature 命名空间，注释声称"feature-gated"但无门控
- **文件**: `stream-control-rpc.beans.xml:29,21-22`；`stream-data-plane.beans.xml:60`
- **建议**: 删除死命名空间，注释改"commented-out deployment template"。

### [P2-01-03] core 反向软耦合 runtime：反射类名 + Javadoc 死链
- **文件**: `WindowedStreamImpl.java:162-165`（反射 `io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl`）；`WindowAssigner.java:72-74`（Javadoc 指向不存在的 `io.nop.stream.runtime.task.StreamTask`）
- **证据**: core 无 import runtime（合规），但反射字符串与 javadoc 死链是边界残留；`setDefaultFactory()` 无调用方。
- **建议**: 修正 Javadoc；评估窗口工厂注册收口到 StreamComponents/SPI。

### [P2-01-04] nop-stream-runtime 以 compile scope 依赖 nop-message-core
- **文件**: `nop-stream/nop-stream-runtime/pom.xml:35-42`
- **证据**: main 代码零 `import io.nop.message.core.*`（接口在 nop-api-core 传递链）；依赖存在仅因模块 beans.xml 默认接线 LocalMessageService。compile scope 把默认实现泄漏给所有下游消费者编译 classpath。
- **建议**: scope 改 runtime，或注释明确决策。

### [P2-01-05/06/07] fraud-example 隐性依赖 core；flow 死 test-jar 依赖；rocksdbjni 版本内联硬编码
- **文件**: `nop-stream-fraud-example/pom.xml:18-23`；`nop-stream-flow/pom.xml:36-42`；`nop-stream-rocksdb/pom.xml:20-24`
- **建议**: 显式声明 core；删除 flow test-jar 依赖；rocksdbjni 版本收口到 nop-dependencies 属性。

### [P2-DOC-04~11] 设计文档行号/计数/措辞漂移（批量）
- **文件**: `checkpoint-design.md:101`（5 处行号过期）；`cep-design.md:253`（CepOperator.open 行号过期）；`00-vision.md:132` vs `01-architecture-baseline.md:13,100` vs `README.md:5`（执行管线"五层/六阶段"三处口径不一，baseline D71 已裁定未回改）；`nop-stream/README.md:11-18`（模块表缺 rocksdb/connector-batch/jdbc/debezium 4 个）；`README.md:35`（XDSL 主入口"规划中"已落地）；`source-anchors.md:210`（STRM-026 状态机缺 SCHEDULED/DEPLOYING 中间态）；`ai-dev/design/nop-stream/README.md:105`（runtime.checkpoint 8 类 vs 实际 10）；`window-design.md:128-130`（ACCUMULATING_AND_RETRACTING 失败点/异常类型漂移）。
- **建议**: 批量回写行号（或改为方法名引用）；统一管线口径为 baseline 六阶段；README 模块表补齐 10 项。

### [P2-TST-1~8] 测试反模式与隔离瑕疵（批量，全部 P2）
- **文件**: `TestCheckpointConfig.java:27-92`（P-1 往返测试未标 low-value）；`TestSinkTransformation.java:304-318` 等 5 处（P-2 instanceof Serializable）；`TestCheckpointConfig.java:193-198`（P-4 镜像常量）；`TestWindowOperatorBasic.java:26-42`（P-5 assertNotNull 新对象）；8 处 `testSerialization` 命名（P-6）；`TestConcurrencySafety.java:39-50` + flow 2 测试类（P-7 全局静态清理不完整）；`TestDebeziumCdcSourceCompletion.java:24-30`（唯一 @Disabled，"run() 无自然完成"契约未固化）。
- **建议**: 按 unit-test-antipatterns 逐条整改；TST-6/7 补 finally/@AfterAll 清理；@Disabled 改写为契约测试。

### [P2-TST-9] checkpoint JSON round-trip 状态值类型覆盖偏窄（POJO/Queue 盲区）
- **文件**: `TestCheckpointSerDeConsistency.java:60-92`；`TestCheckpointSerDeChannelState.java:38-51`
- **证据**: SerDe 专测全部 String key/value；POJO 值仅附带覆盖；NFAState Queue 不经过 JSON 持久化测试——与 INV-4/INV-6 直接相关。
- **建议**: 补复合类型（POJO 值 + 内嵌 List/Map/Queue）round-trip 用例。

### [P2-XDSL-8~14] DSL 次要缺口（批量）
- **文件**: `stream.xdef:112-121,170-178`（source/sink 的 outputType/inputType/params 静默忽略）；`stream.xdef:188-193`（custom 的 source 子节点忽略）；`stream.xdef:124-129`（节点级 watermarkInterval 忽略 + 双 null 无 fail-fast）；`stream.xdef:175-177,190-192`（param 未显式声明属性，违反 xdef-and-xdsl 规则 4）；`StreamModelDslBuilder.java:156-189`（windowingStrategies/patterns 声明无消费者不 fail-fast，与 8 注册表不对称）；`CepPatternBuilder.java:145-204`（or() 无直接表达、times/oneOrMore 互斥无 DSL 校验）；`pattern.xdef:29`（subType 属性名与判别字段 type 易混淆）。
- **建议**: 非空声明 fail-fast 或实现；文档规则对齐。

---

## 总评

nop-stream 模块组整体工程质量高：依赖图健康（无环、core 为唯一根、connector 族干净），错误处理骨架健全（core/runtime/cep 常规路径 fail-fast + cause + param + LOG 留证执行到位），测试质量优秀（483 测试文件，10 个表驱动 invariant 门禁 + mjs 扫描器双挂 CI，关键恢复路径测试断言正确结果而非无异常），锚点文档 36/37 行为描述准确，xdef/生成类/枚举完全一致。

**P0 = 0**。最接近 P0 的两项是 P1-INV-2（默认开启的 unaligned 逃生在其设计目标场景下失效，失败检测从 1s/30s 劣化到 10 分钟，可致连续 checkpoint 失败）与 P1-INV-1（长运行窗口作业的 trigger 状态无界泄漏）——两者都是"不变式族第三次同类变体"的确认案例：INV-1 是窗口家族状态生命周期不对称的又一次命中（已修复族的新路径），INV-2 是 barrier 对齐家族的饿死路径（现有测试恰好只覆盖检查能运行的场景）。

**文档契约漂移集中在三处**：checkpoint-design §2.4（重叠 barrier 行为，Stage 45 未回写）、§2.5（watermark state 契约未实现）、README（DISTRIBUTED 与 XDSL 均已落地但标注"规划中"，模块表缺 4 个模块）。**执行层静默降级集中在 nop-stream-flow**：edge 分区、checkpoint 子集、窗口策略属性声明后零消费且无 fail-fast，与 builder 自称的 "Anti-Hollow guarantees" 矛盾——这是 DSL 声明式入口最大的真实缺口。

## 优先修复建议

1. **P1-INV-2**：InputGate elapsed 检查与数据返回解耦（补持续流量回归测试）
2. **P1-INV-1**：triggerAccumulators 清理路径补全（清理/merge/retire/timer-PURGE）
3. **P1-XDSL-5/6**：flow 静默忽略属性组 fail-fast（或实现）——分区语义是数据正确性缺口
4. **P1-09-02**：flow 模块错误码体系接入
5. **P1-DOC-01/02/03**：设计文档契约回写（§2.4 行为、§2.5 标注、README 状态）
6. **P1-01-01**：rocksdb 命名空间迁移（结构性重构，plan-first + 人工确认）
7. P2 批次：BOM 清理、beans.xml 重复声明、fraud 示例修复、错误码激活、SerDe 复合类型测试

## 本次审核盲区自评

- 未运行完整 `./mvnw test -pl nop-stream -am`（只读审计，未执行构建；构建验证由 mission 的 I5 阶段负责）
- INV-6（NFAState PriorityQueue JSON 物化）未经 JsonTool 实测，仅代码路径推断
- 类型安全维度抽查为主（1235 处 cast 无法全量逐查）
- 并发竞态类发现依赖静态分析，未做压力/并发实测（除复核 agent 的 ioc 冲突实验）
- nop-stream-connector-batch（nop-batch 桥接）与 nop-stream-fraud-example 的语义细节未深挖（非不变式族核心路径）
