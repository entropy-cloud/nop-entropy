# 37 nop-stream Round3 对抗性审查关键缺陷修复

> Plan Status: completed
> Last Reviewed: 2026-05-22
> Completed: 2026-05-22
> Source: `ai-dev/audits/2026-05-22-adversarial-review-nop-stream-r2/adversarial-review-round3.md`（N73-N93）
> Related: `ai-dev/plans/35-nop-stream-window-watermark-test-supplementation.md`, `ai-dev/plans/36-nop-stream-feature-completion-with-tests.md`

## Purpose

修复 nop-stream Round 3 对抗性审查发现的高优先级正确性缺陷（N73-N78），确保 timer 生命周期、graph model keyed state、reduce operator 状态持久化等核心功能在正常使用路径上不崩溃、不丢失数据。

## Current Baseline

- HeapInternalTimerService 在 timer callback 中删除 timer 时会 ConcurrentModificationException（N73）
- HeapInternalTimer.getKey() 永远返回 null，keyed timer 回调无法确定当前 key（N74）
- Graph Model 执行路径（StreamTaskInvokable.wireOperators）缺少 KeyExtractingOutput，keyed state 在 graph 执行中完全失效（N75）
- StreamReduceOperator 使用 transient HashMap 存储状态，checkpoint 后状态丢失（N76）
- WindowAggregationOperator trigger state key 使用 `#` 分隔符存在碰撞风险（N77）
- StreamTaskInvokable.invokeMiddle/invokeSink 不发送 MAX_WATERMARK（N83）
- processInputGate 不处理 WatermarkStatus（N84）
- nop-stream 全模块已有 test pass 基线（./mvnw test -pl nop-stream）

## Goals

- 修复所有 P0 级别 timer 生命周期 bug（N73、N74），使 HeapInternalTimerService 在正常使用路径上不崩溃
- 修复 graph model 路径的 keyed state 支持（N75），使 keyBy().reduce()/window() 在 graph model 路径中正确工作
- 修复 StreamReduceOperator 状态持久化（N76），使 reduce 结果在 checkpoint 恢复后不丢失
- 修复 WindowAggregationOperator trigger state key 碰撞（N77）
- 修复 StreamTaskInvokable MAX_WATERMARK 传播（N83）和 WatermarkStatus 处理（N84）
- 为每个修复添加 focused test 验证

## Non-Goals

- 不统一三套 timer 实现（N91）— 属于架构重构，需要单独计划
- 不优化 CepOperator per-event cache flush（N79）— 属于性能优化
- 不修复 NFACompiler NOT_FOLLOW 静默丢弃（N81）— CEP 功能增强，需要单独计划
- 不修复 connector 资源管理问题（N85、N86）— P2，不影响正确性
- 不增加 CEP 测试覆盖（N90）— 大规模测试补充需要单独计划

## Scope

### In Scope

- `nop-stream-core/.../operators/HeapInternalTimerService.java`（N73、N74）
- `nop-stream-core/.../execution/StreamTaskInvokable.java`（N75、N83、N84）
- `nop-stream-core/.../operators/StreamReduceOperator.java`（N76）
- `nop-stream-core/.../operators/WindowAggregationOperator.java`（N77）
- 对应模块的测试文件

### Out Of Scope

- `nop-stream-cep/` 模块的修改
- `nop-stream-connector/` 模块的修改
- `nop-stream-runtime/` 模块的 WindowOperator（已有大量已知问题，需要独立修复计划）
- timer 系统统一重构
- CEP NFACompiler 修改

## Execution Plan

### Phase 1 - 修复 HeapInternalTimerService CME + key 缺失（N73、N74）

Status: done
Targets: `nop-stream-core/.../operators/HeapInternalTimerService.java`, `nop-stream-core/src/test/`

- Item Types: `Fix`, `Proof`

**设计决策（N74）**：不修改 `InternalTimerService<N>` 接口签名。在 `HeapInternalTimerService` 中添加 `Supplier<Object> currentKeySupplier` 字段（构造函数注入），`registerEventTimeTimer` 时从 supplier 获取当前 key 并存入 `TimerEntry`。这与 `WindowOperatorTimerService`（runtime 模块）的 `Supplier<K>` 模式一致。

- [x] 修复 N73：在 `advanceWatermark` L106-111，将 `entry.getValue()` 的 HashSet 复制为 `new ArrayList<>(entry.getValue())` 后再遍历触发 timer，避免 callback 中的 `deleteEventTimeTimer` 修改正在遍历的集合
- [x] 修复 N74：HeapInternalTimerService 构造函数添加 `Supplier<Object> currentKeySupplier` 参数（重载保留无参构造器以兼容现有调用点）；`TimerEntry<N>` 改为 `TimerEntry<N>` 持有 `(Object key, N namespace, long timestamp)` 三元组；`registerEventTimeTimer` 从 supplier 获取 key；`HeapInternalTimer.getKey()` 返回 `timerEntry.key`
- [x] 添加测试 TestHeapInternalTimerService：验证 (1) timer callback 中删除 timer 不崩溃、(2) timer 携带正确 key、(3) advanceWatermark 单调性、(4) key supplier 为 null 时 getKey 返回 null

Exit Criteria:

- [x] HeapInternalTimerService.advanceWatermark 在 callback 调用 deleteEventTimeTimer 时不抛 CME
- [x] InternalTimer.getKey() 返回注册时 currentKeySupplier 提供的 key 值
- [x] 新测试 TestHeapInternalTimerService 全部通过
- [x] **无静默跳过**：修改后的方法无空方法体或静默忽略
- [x] **向后兼容**：无参构造器仍可用（key supplier 为 null，getKey 返回 null，与当前行为一致）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 修复 MAX_WATERMARK 传播 + WatermarkStatus 处理（N83、N84）

Status: done
Targets: `nop-stream-core/.../execution/StreamTaskInvokable.java`, `nop-stream-core/src/test/`

- Item Types: `Fix`, `Proof`

**注意**：N75（KeyExtractingOutput）因设计链路问题移至 Deferred。审查发现两个根本性障碍：(1) `StreamReduceOperator` 不实现 `KeyContext` 接口，无法被 `KeyExtractingOutput` 包装；(2) keySelector 在跨 chain 场景下不可达（keyBy 节点与 reduce 节点被 partitioner 分隔到不同 chain）。这两个问题需要架构层面的设计决策，不在此 plan 范围内解决。

- [x] 修复 N83：`invokeMiddle()` 在 `processInputGate(headInput)` 之后、`outputWriter.close()` 之前，调用 `headInput.processWatermark(Watermark.MAX_WATERMARK)`；`invokeSink()` 在 `processInputGate(headInput)` 之后同样添加 `headInput.processWatermark(Watermark.MAX_WATERMARK)`
- [x] 修复 N84：`processInputGate()` 添加 `else if (element.isWatermarkStatus()) { headInput.processWatermarkStatus(element.asWatermarkStatus()); }` 分支（Input 接口已有 processWatermarkStatus 方法，L55）
- [x] 添加测试：(1) MAX_WATERMARK 通过 InputGate 到达 downstream operator；(2) WatermarkStatus 元素不被静默丢弃

Exit Criteria:

- [x] invokeMiddle/invokeSink 在 InputGate 耗尽后发送 MAX_WATERMARK
- [x] WatermarkStatus 元素不被静默丢弃（处理分支非空方法体）
- [x] **接线验证**：headInput.processWatermarkStatus 确实被 processInputGate 调用
- [x] **无静默跳过**：WatermarkStatus 处理分支不使用空方法体
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 修复 StreamReduceOperator 状态持久化（N76）

Status: done
Targets: `nop-stream-core/.../operators/StreamReduceOperator.java`, `nop-stream-core/src/test/`

- Item Types: `Fix`, `Proof`

**设计决策（N76）**：选择 `snapshotState()/restoreState()` 方案——覆写 `AbstractStreamOperator` 的 `snapshotState` 和 `restoreState`，将 HashMap 序列化为 byte[]。不依赖 keyedStateBackend（因为 graph model 路径中 keyedStateBackend 的初始化链路不明确），保持与现有 MemoryKeyedStateBackend 的序列化模式一致。

- [x] 修复 N76：为 StreamReduceOperator 添加 `snapshotState()` 方法（先调用 `super.snapshotState()` 保留 keyedStateBackend 状态，再追加 HashMap 序列化数据到 result）；添加 `restoreState()` 方法（先调用 `super.restoreState()` 恢复 keyedStateBackend，再从额外数据恢复 HashMap）
- [x] 添加测试：process elements → snapshotState → 创建新 operator → restoreState → 继续处理 → 验证 reduce 状态正确恢复

Exit Criteria:

- [x] StreamReduceOperator 在 checkpoint 恢复后保留已 reduce 的状态
- [x] 新测试验证 snapshot/restore 跨 operator 实例正确工作
- [x] **端到端验证**：keyBy().reduce() 在 checkpoint 恢复后继续正确累加
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 修复 WindowAggregationOperator trigger state key 碰撞（N77）

Status: done
Targets: `nop-stream-core/.../operators/WindowAggregationOperator.java`, `nop-stream-core/src/test/`

- Item Types: `Fix`, `Proof`

**设计决策（N77）**：使用 `WindowKey<K,W>` 作为 Map key 的一部分。新增 `TriggerStateKey<K,W>` record 类持有 `(WindowKey<K,W> windowKey, String descriptorName)`，实现 `equals/hashCode`。替换 `triggerState` 的 `Map<String, ...>` 为 `Map<TriggerStateKey<K,W>, ...>`。

- [x] 修复 N77：新增 `TriggerStateKey<K,W>` 静态内部类（持有 WindowKey + descriptorName）；`triggerState` 类型从 `Map<String, ...>` 改为 `Map<TriggerStateKey<K,W>, ...>`；`getSimpleAccumulator` 和 `purgeWindow` 使用新 key 类型
- [x] 添加测试：使用包含特殊字符的 key 验证 trigger state 不碰撞

Exit Criteria:

- [x] 包含 `#` 字符的 key 不再导致 trigger state 碰撞
- [x] 新测试验证特殊字符 key 的 trigger state 隔离
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 构建验证 + 日志更新

Status: done
Targets: 全模块

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-stream` 全通过
- [x] 更新 `ai-dev/logs/` 当日日志

Exit Criteria:

- [x] `./mvnw test -pl nop-stream -am` 全通过
- [x] checkstyle 通过
- [x] `ai-dev/logs/` 当日日志已更新

## Closure Gates

- [x] N73 CME 修复 + 测试验证
- [x] N74 timer key 修复 + 测试验证
- [x] N76 StreamReduceOperator 状态持久化 + 跨实例恢复测试
- [x] N77 trigger state key 碰撞修复 + 测试验证
- [x] N83 MAX_WATERMARK 传播修复 + 测试验证
- [x] N84 WatermarkStatus 处理修复 + 测试验证
- [x] 无新增空壳实现或静默跳过
- [x] `./mvnw test -pl nop-stream -am` 全通过
- [x] 独立子 agent closure-audit 已完成

## Deferred But Adjudicated

### N75 Graph Model 路径 KeyExtractingOutput（跨 chain keyed state 支持）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 两轮审查发现两个根本性障碍：(1) `StreamReduceOperator` 不实现 `KeyContext`，无法被 `KeyExtractingOutput` 包装；(2) keySelector 在跨 chain 场景下不可达（keyBy 节点与 reduce 节点被 non-null partitioner 分隔到不同 chain）。需要架构层面的设计决策（如何跨 chain 传递 keySelector、哪些 operator 需要实现 KeyContext），超出本 plan 范围
- Successor Required: yes
- Successor Path: 需要新的 graph model keyed state 架构改进计划

### N78 三套独立 timer 实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 架构重构需要设计决策（保留/合并哪套），不影响当前 bug 修复
- Successor Required: yes
- Successor Path: 需要新的架构重构计划

### N79 CepOperator per-event cache flush

- Classification: `optimization candidate`
- Why Not Blocking Closure: 性能问题，不影响正确性
- Successor Required: yes
- Successor Path: 性能优化计划

### N81 NFACompiler NOT_FOLLOW 静默丢弃

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: CEP 功能增强，需要单独设计和测试
- Successor Required: yes
- Successor Path: CEP 功能完善计划

### N85 BatchConsumerSinkFunction 资源泄漏

- Classification: `optimization candidate`
- Why Not Blocking Closure: P2 资源泄漏，不影响数据正确性
- Successor Required: yes

## Non-Blocking Follow-ups

- N80 SharedBuffer.advanceTime EventId 碰撞风险 — 需要特定时序触发，watch-only
- N82 SkipToFirst/SkipToLast 静默退化 — CEP skip 策略增强
- N86 MessageSourceFunction unchecked cast — connector 改进
- N87-N89 测试质量问题 — 测试补充计划
- N90 CEP 零覆盖功能 — 大规模测试补充
- N91-N93 代码质量问题 — 代码质量改进
