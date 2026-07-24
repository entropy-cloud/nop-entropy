# CEP 引擎源码级对比分析

> **Plan**: `docs/plans/nop-stream-flink-comparison/2026-07-24-1000-4-cep-comparison.md`
> **Generated**: 2026-07-25
> **Method**: Direct source reading of Flink `release-1.20.0` at `~/sources/flink/flink-libraries/flink-cep/` and nop-stream at `nop-stream/nop-stream-cep/`.
> **Plans 316/317 status**: Both `active`, deliverables `01-flink-source-audit.md`/`02-nopstream-live-audit.md` absent. Per plan precedent, supplemented via direct source reading.
> **Preamble — Roadmap Corrections**:
> - Roadmap lists "CEP 使用 SimpleKeyedStateStore 而非统一状态后端" as a known gap. **This is inaccurate.** Production CepOperator (`CepOperator.java:202-224`) uses `getKeyedStateBackend()` → `stateBackend.createKeyedStateBackend()` → fallback `MemoryKeyedStateBackend`. `SimpleKeyedStateStore` is test-only (24+ test file references, zero production imports). The **real gap** is the state backend wiring pipeline (whether the runtime properly injects `IKeyedStateBackend` into operators via `setKeyedStateBackend()`, and whether the checkpoint snapshot/restore path is called on the state backend).
> - Roadmap lists "[gap] nop-stream-cep 的 NFA 状态未参与 checkpoint" as a known gap. **This is inaccurate.** NFAState is stored in `ValueState<NFAState>` named `"nfaStateName"` (line 218) and participates in keyed state backend checkpoint. The operator's `snapshotState()` (line 307) calls `super.snapshotState()` which snapshots the keyed state backend (including NFAState), and saves `currentWatermark` + `registeredEventTimeTimers` as operator state. Restore happens via `restoreState()` (line 317). The real gap is whether the state backend's `snapshotState()` / `restoreState()` is actually called by the runtime's checkpoint coordinator.
> **Consumable by**: Item 8 (Gap Analysis)

---

## 1. NFA 编译/执行路径对比

### 1.1 架构映射

| 组件 | Flink | nop-stream | 匹配度 |
|------|-------|-----------|--------|
| 编译器 | `NFACompiler` → `NFAFactoryCompiler` | `NFACompiler` → `NFAFactoryCompiler` | Match |
| 工厂 | `NFAFactoryImpl` → `createNFA()` | `NFAFactoryImpl` → `createNFA()` | Match |
| 运行时引擎 | `NFA<T>` | `NFA<T>` | Match |
| 状态容器 | `NFAState` (partialMatches + completedMatches queues) | `NFAState` (partialMatches + completedMatches queues) | Match |
| 状态类型 | Start/Final/Normal/Pending/Stop | Start/Final/Normal/Pending/Stop | Match |
| 转换类型 | TAKE/IGNORE/PROCEED | TAKE/IGNORE/PROCEED | Match |

### 1.2 NFACompiler 路径

**Flink** (`NFACompiler.java:72-90`):
- `NFACompiler.compileFactory(Pattern, boolean)` → `NFAFactoryCompiler.compileFactory()`:
  1. `checkPatternNameUniqueness()`
  2. `checkPatternSkipStrategy()`
  3. `createEndingState()` → `$endState$` Final state
  4. `createMiddleStates(sink)` — walks pattern chain **backwards**
  5. `createStartState(sink)` — marks first state as Start
  6. `checkPatternWindowTimes()`
- Creates `NFAFactoryImpl(windowTime, windowTimes, states, timeoutHandling)`

**nop-stream** (`NFACompiler.java`):
- Identical structure and method decomposition. Same backwards traversal, same state type generation (TAKE/IGNORE/PROCEED edges), same `NFAFactoryImpl` output.

**Key methods correspondence:**

| Method | Flink | nop-stream | Match |
|--------|-------|-----------|-------|
| createEndingState | `NFAFactoryCompiler.java:319-323` | Equivalent | Yes |
| createStartState | `NFAFactoryCompiler.java:393-397` | Equivalent | Yes |
| createMiddleStates | `NFAFactoryCompiler.java:331-383` | Equivalent | Yes |
| createSingletonState | `NFAFactoryCompiler.java:690-768` | Equivalent | Yes |
| createLooping | `NFAFactoryCompiler.java:845-893` | Equivalent | Yes |
| createTimesState | `NFAFactoryCompiler.java:584-626` | Equivalent | Yes |
| createStopState | `NFAFactoryCompiler.java:461-472` | Equivalent | Yes |
| createGroupPatternState | `NFAFactoryCompiler.java:779-804` | Equivalent | Yes |

### 1.3 NFA 运行时引擎

**Flink** (`NFA.java`):
- `process()` (line 232) → `doProcess()` (line 350): iterates partial matches, calls `computeNextStates()`, collects Final/Stop/Normal states, calls `processMatchesAccordingToSkipStrategy()`
- `computeNextStates()` (line 614): builds `OutgoingEdges` via `createDecisionGraph()`, processes TAKE/IGNORE transitions with DeweyNumber versioning, preserves Start state, releases previous buffer entry
- `advanceTime()` (line 262): checks `windowTime` (global) and `windowTimes` (per-state) for each partial match, emits timed-out patterns, calls `processMatchesAccordingToSkipStrategy()`
- `createDecisionGraph()` (line 806): recursive PROCEED-following, collects IGNORE/TAKE edges

**nop-stream** (`NFA.java`):
- Same method structure: `process()` → `doProcess()` → `computeNextStates()` / `advanceTime()` / `processMatchesAccordingToSkipStrategy()`
- `computeNextStates()` (line 621), `createDecisionGraph()` (line 814), `advanceTime()` (line 266): identical logic to Flink
- `ConditionContext` inner class (line 893): lazy materialization of matched events for iterative conditions — same as Flink
- `EventWrapper` inner class (line 542): lazy event registration + release on close — same as Flink

### 1.4 NFAState

**Flink** (`NFAState.java`):
- `partialMatches: Queue<ComputationState>` (PriorityQueue)
- `completedMatches: Queue<ComputationState>` (PriorityQueue)
- `stateChanged: boolean` — dirty flag for checkpoint optimization
- `isNewStartPartialMatch: boolean` — signals timer registration
- `COMPUTATION_STATE_COMPARATOR` — orders by `startEventID.timestamp` then `startEventID.id`

**nop-stream** (`NFAState.java`):
- Identical fields and semantics
- Same `COMPUTATION_STATE_COMPARATOR`
- Same dirty flag lifecycle: `isStateChanged()` / `setStateChanged()` / `resetStateChanged()`

### 1.5 差异分析

| 差异 | 分类 | 严重性 | 详情 |
|------|------|--------|------|
| NFACompiler 路径 | OK | — | 完全匹配（Flink 源码剥离） |
| NFA process/advanceTime 路径 | OK | — | 完全匹配 |
| NFAState 数据结构 | OK | — | 完全匹配 |
| ComputationState 字段 | OK | — | 完全匹配 |
| DeweyNumber 版本语义 | OK | — | 完全匹配 |
| EventWrapper 生命周期 | OK | — | 完全匹配 |

**结论**: NFA 编译/执行路径属于 clean extraction — nop-stream 与 Flink 的 NFA 核心引擎在实现上完全一致，无退化或缺口。

---

## 2. SharedBuffer 实现对比

### 2.1 架构映射

| 组件 | Flink | nop-stream |
|------|-------|-----------|
| 主类 | `SharedBuffer<V>` (356 lines) | `SharedBuffer<V>` (375 lines) |
| 访问器 | `SharedBufferAccessor` (378 lines) | `SharedBufferAccessor` |
| 版本方案 | `DeweyNumber` (281 lines) | `DeweyNumber` |
| 引用计数 | `Lockable<T>` (209 lines) | `Lockable<T>` |
| 事件 ID | `EventId (id, timestamp)` | `EventId (id, timestamp)` |
| 节点 ID | `NodeId (EventId, pageName)` | `NodeId (EventId, pageName)` |
| 节点数据 | `SharedBufferNode` (edges list) | `SharedBufferNode` (edges list) |
| 边数据 | `SharedBufferEdge (target, version)` | `SharedBufferEdge (target, version)` |

### 2.2 状态后端集成

**Flink** (`SharedBuffer.java:74-118`):
- Three `MapState` backends from `KeyedStateStore`:
  - `"sharedBuffer-events"` → `MapState<EventId, Lockable<V>>`
  - `"sharedBuffer-events-count"` → `MapState<Long, Integer>`
  - `"sharedBuffer-entries-with-lockable-edges"` → `MapState<NodeId, Lockable<SharedBufferNode>>`
- Two Guava Caches as LRU write-through: `eventsBufferCache`, `entryCache`
- `flushCache()` → bulk-puts cached entries to state, called by `SharedBufferAccessor.close()`

**nop-stream** (`SharedBuffer.java`):
- Same three `MapState` backends from `KeyedStateStore`
- Same two caches — but uses `ConcurrentHashMap` instead of Guava Cache (Flink uses Guava `CacheBuilder`)
- Same `flushCache()` pattern, called by `SharedBufferAccessor.close()`

### 2.3 各辅助类对比

| 类 | Flink | nop-stream | Match |
|----|-------|-----------|-------|
| **DeweyNumber** | `increase()`, `addStage()`, `isCompatibleWith()`, `getRun()` | Same | Yes |
| **Lockable** | `lock()` increment, `release()` CAS decrement, `releaseOrDetach()` | Same | Yes |
| **EventId** | `(int id, long timestamp)`, Comparable | Same | Yes |
| **NodeId** | `(EventId eventId, String pageName)` | Same | Yes |
| **SharedBufferNode** | `List<Lockable<SharedBufferEdge>> edges` | Same | Yes |
| **SharedBufferEdge** | `(NodeId target, DeweyNumber version)` | Same | Yes |
| **SharedBufferAccessor** | `put()`, `extractPatterns()` (DFS), `materializeMatch()`, `lockNode()`, `releaseNode()`, `registerEvent()` | Same | Yes |

### 2.4 差异分析

| 差异 | 分类 | 严重性 | 详情 |
|------|------|--------|------|
| SharedBuffer 三表结构 | OK | — | 完全匹配 |
| DeweyNumber 版本语义 | OK | — | 完全匹配 |
| Lockable 引用计数 | OK | — | 完全匹配 |
| extractPatterns DFS 路径 | OK | — | 完全匹配 |
| materializeMatch 路径 | OK | — | 完全匹配 |
| 缓存实现 | ExtractionDegradation | P3 | Flink 使用 Guava Cache (`CacheBuilder`) 带 LRU 驱逐 + `RemovalListener` flush；nop-stream 使用 `ConcurrentHashMap` 无自动驱逐。nop-stream 的 `flushCache()` 仅在 `SharedBufferAccessor.close()` 时调用，高吞吐下缓存可能无限增长 |
| advanceTime 清理 | OK | — | 两者都通过 `SharedBufferAccessor.advanceTime()` 清理 events-count |

**结论**: SharedBuffer 实现属于 clean extraction，主要差异是缓存实现退化（Guava Cache → ConcurrentHashMap），这在高吞吐场景下可能导致缓存无限增长。

---

## 3. CepOperator 状态后端集成对比

### 3.1 状态管理架构

| 方面 | Flink | nop-stream |
|------|-------|-----------|
| NFA state | `ValueState<NFAState>` via `context.getKeyedStateStore()` | `ValueState<NFAState>` via `keyedStateStore.getState()` |
| Event queue | `MapState<Long, List<IN>>` via `context.getKeyedStateStore()` | `MapState<Long, List<IN>>` via `keyedStateStore.getMapState()` |
| Shared buffer | `SharedBuffer` using `KeyedStateStore.getMapState()` | `SharedBuffer` using `keyedStateStore.getMapState()` |
| State store source | `context.getKeyedStateStore()` (injected by runtime) | `getKeyedStateBackend()` → `stateBackend.createKeyedStateBackend()` → fallback `MemoryKeyedStateBackend` |

### 3.2 nop-stream 状态后端接线路径

**`CepOperator.open()` (lines 202-217):**
```
IKeyedStateBackend<?> backend = getKeyedStateBackend();          // line 202
if (backend == null && this.stateBackend != null) {              // line 203
    this.keyedStateBackend = this.stateBackend.createKeyedStateBackend(Object.class); // line 205
    applyPendingRestoreState();                                   // line 207
    backend = getKeyedStateBackend();                            // line 208
}
if (backend != null) {
    keyedStateStore = backend;                                    // line 211 — use real backend
} else {
    LOG.warn("... fallback MemoryKeyedStateBackend ...");         // line 213-216
    keyedStateStore = new MemoryKeyedStateBackend<>(Object.class); // line 216
}
```

### 3.3 Checkpoint 参与

**Flink** (`CepOperator.java`):
- `snapshotState()`: `super.snapshotState()` snapshots keyed state backend (NFAState + element queue + shared buffer). No additional operator state needed — all CEP state is keyed state.
- `restoreState()`: `super.restoreState()` restores keyed state backend.

**nop-stream** (`CepOperator.java:307-335`):
- `snapshotState()` (line 307): Calls `super.snapshotState()` (snapshots keyed state backend), then saves `currentWatermark` and `registeredEventTimeTimers` as **operator state** via `result.putOperatorState()`.
- `restoreState()` (line 317): Calls `super.restoreState()` (restores keyed state backend), then restores `currentWatermark` and `registeredEventTimeTimers` from operator state.

### 3.4 差异分析

| 差异 | 分类 | 严重性 | 详情 |
|------|------|--------|------|
| NFAState 参与 checkpoint | OK (see note) | — | NFAState 存储在 `ValueState<NFAState>` 中，通过 keyed state backend snapshot/restore。**前提**：state backend 的 snapshot/restore 被 runtime 调用 |
| 状态后端接线优先级 | OK | — | `getKeyedStateBackend()` → `this.stateBackend.createKeyedStateBackend()` → `MemoryKeyedStateBackend` fallback。**不**使用 `SimpleKeyedStateStore` |
| 状态后端注入可行性 | Wiring Gap | P1 | `getKeyedStateBackend()` 来自 `AbstractStreamOperator`。在 nop-stream runtime 中，operator 的 state backend 是否被正确注入 `setKeyedStateBackend()` 尚未验证。如果 runtime 不调用 `setKeyedStateBackend()`，则 `getKeyedStateBackend()` 返回 null，走 `stateBackend.createKeyedStateBackend()` 路径。如果 `stateBackend` 也为 null，则回退到 `MemoryKeyedStateBackend`，不参与 checkpoint |
| checkpoint snapshot/restore 路径 | Wiring Gap | P1 | `snapshotState()` 和 `restoreState()` 是否被 runtime 的 checkpoint coordinator 调用尚未验证。`super.snapshotState()` 依赖 `AbstractStreamOperator` 的 snapshot 实现，该实现需要调用 keyed state backend 的 `snapshotState()` — 这需要在 runtime 层验证 |
| Watermark/timers 作为 operator state | Improvement | — | nop-stream 额外持久化 watermark 和 timers 作为 operator state，Flink 由内部 timer service 管理。这是从提取过程中 Flink 的 InternalTimerService 依赖被简化后的适应 |
| 设计注释偏差 | Doc | P2 | `CepOperator.java:80-83` 的 Javadoc 注释声称 "hardcoded MemoryKeyedStateBackend"，但实际代码有完整的 backend → fallback 分级。注释过时 |

**结论**: 核心 CepOperator 的状态集成代码在 nop-stream 中**正确实现**（包括分级 fallback、snapshot/restore 接线），但**存在两个外部 wiring gap**：
1. runtime 是否调用 `setKeyedStateBackend()` 注入 backend
2. runtime 的 checkpoint coordinator 是否真正调用 operator 的 `snapshotState()` / `restoreState()`

这两个 gap 不属于 CepOperator 本身的问题，而是 operator 生命周期管理在 runtime 层的完整连接问题。

---

## 4. 事件时间超时处理对比

### 4.1 内部 Watermark 跟踪

**Flink** (`CepOperator.java`):
- `currentWatermark` 字段在 `CepOperator` 中不存在。Flink 依赖 `InternalTimerService` 的 watermark 概念
- Watermark 通过 `AbstractStreamOperator.processWatermark()` → `InternalTimeServiceImpl.advanceWatermark()` 传播
- Timer 回调通过 `Triggerable.onEventTime()` 触发

**nop-stream** (`CepOperator.java:165`):
- `currentWatermark` 字段初始化为 `Long.MIN_VALUE`
- `processWatermark()` (line 338): 更新 `currentWatermark`，调用 `onEventTime()`
- `onEventTime()` (line 397): 从 `elementQueueState` 获取排序时间戳，对于每个 `timestamp <= currentWatermark`，先调用 `advanceTime()` 再 `processEvent()`
- `timerService.currentWatermark()` (line 235): 返回 `CepOperator.this.currentWatermark`

### 4.2 超时检测路径

**Flink** (`NFA.advanceTime()`, `CepOperator.onEventTime()`):
- `NFA.advanceTime()`: 检查 `windowTime`（全局）和 `windowTimes`（per-state）的超时
- `isStateTimedOut()`: `!isStartState(state) && windowTime > 0L && timestamp - startTimestamp >= windowTime`
- 超时处理: 如果 `handleTimeout` 为 true，materialize 并 emit 超时模式；否则释放 buffer entry
- Timer 注册: `registerTimer(timestamp + windowTime)` 当 `isNewStartPartialMatch`

**nop-stream** (`NFA.advanceTime()`, `CepOperator.onEventTime()`):
- 完全相同的 `isStateTimedOut()` 逻辑（`NFA.advanceTime()` line 348-353）
- 相同的 timer 注册逻辑（`processEvent()` line 555-568）
- 相同的时间戳排序和 batch 处理（`onEventTime()` line 397-456）
- 额外增加了 dangling partial match 清理（`onEventTime()` line 428-456）

### 4.3 差异分析

| 差异 | 分类 | 严重性 | 详情 |
|------|------|--------|------|
| 内部 watermark 跟踪 | OK (Design Difference) | — | nop-stream 使用独立的 `currentWatermark` 字段而非 Flink 的 `InternalTimerService.advanceWatermark()`。语义等价 |
| 超时检测逻辑 | OK | — | 完全匹配（同一 `isStateTimedOut` 实现） |
| Timer 注册 | OK | — | 完全匹配 |
| onEventTime 流程 | OK | — | 完全匹配（STEP 1-4 流程一致） |
| Dangling match 清理 | Improvement | — | nop-stream 额外实现了 dangling partial match 清理（`onEventTime()` line 428-456），Flink 无此逻辑 |
| 外部 watermark 传播 | Wiring Gap | P1 | **真正缺口**：watermark 从 runtime 传播到 CepOperator 的管路是否连通。包括：(a) watermark operator 自动插入；(b) 多输入 watermark 对齐。CepOperator 内部的 watermark 处理代码正确，但外部触发路径未验证 |

**结论**: CepOperator 内部的事件时间超时处理代码**完全正确**且与 Flink 语义等价。额外实现了 dangling match 清理（改进）。真实缺口在于外部 watermark 从 runtime 传播到 CepOperator 的管路是否连通（属于 roadmap item 10/11 范畴）。

---

## 5. 匹配后策略对比

### 5.1 策略层次

| 策略 | Flink | nop-stream | Match |
|------|-------|-----------|-------|
| AfterMatchSkipStrategy (abstract) | ✓ | ✓ | Yes |
| NoSkipStrategy | ✓ (singleton) | ✓ (singleton) | Yes |
| SkipToNextStrategy | ✓ (singleton) | ✓ (singleton) | Yes |
| SkipPastLastStrategy | ✓ (singleton) | ✓ (singleton) | Yes |
| SkipToFirstStrategy | ✓ | ✓ | Yes |
| SkipToLastStrategy | ✓ | ✓ | Yes |
| SkipRelativeToWholeMatchStrategy (abstract) | ✓ | ✓ | Yes |
| SkipToElementStrategy (abstract) | ✓ | ✓ | Yes |

### 5.2 策略行为

| 策略 | shouldPrune 谓词 | nop-stream | Match |
|------|-----------------|-----------|-------|
| NoSkip | N/A (`isSkipStrategy=false`) | Same | Yes |
| SkipToNext | `startEventID <= pruningId` | Same | Yes |
| SkipPastLast | `startEventID <= pruningId` | Same | Yes |
| SkipToFirst | `startEventID < pruningId` | Same | Yes |
| SkipToLast | `startEventID < pruningId` | Same | Yes |

### 5.3 差异分析

| 差异 | 分类 | 严重性 |
|------|------|--------|
| 策略完整度（全部5种） | OK | — |
| 行为语义 | OK | — |
| `prune()` 方法逻辑 | OK | — |

**结论**: AfterMatchSkipStrategy 实现完全匹配，无缺口或退化。

---

## 6. 声明式模型对比

### 6.1 nop-stream 独有模型

nop-stream 提供了 XDSL-backed 的 CEP 声明式模型，Flink 标准 API 不包含：

| 类 | 角色 | 描述 |
|----|------|------|
| `CepPatternModel` | 顶层模型 | 包含 `parts`（pattern 列表）、`start`、`within`、`gapWithin`、`afterMatchSkipStrategy`、`afterMatchSkipTo` |
| `CepPatternSingleModel` | 单个 pattern | 继承 `_CepPatternPartModel`，增加 `where`（`IEvalFunction`）、`until`（`IEvalFunction`） |
| `CepPatternGroupModel` | 分组 pattern | 继承 `_CepPatternPartModel`，增加 `parts`、`start`、`afterMatchSkipStrategy`、`afterMatchSkipTo` |
| `CepPatternBuilder` | 转换器 | 将 `CepPatternModel` 转换为 Flink 兼容的 `Pattern<T,?>` API |

### 6.2 模型能力对照

| 能力 | Flink Pattern API | nop-stream CepPatternModel | 覆盖 |
|------|-------------------|---------------------------|------|
| 单事件 pattern | `Pattern.begin("name")` | `CepPatternSingleModel` | ✓ |
| 顺序连接 | `next()` / `followedBy()` | `followKind` = `next` / `followedBy` | ✓ |
| 非确定性连接 | `followedByAny()` | `followKind` = `followedByAny` | ✓ |
| 否定 | `notNext()` / `notFollowedBy()` | `followKind` = `notNext` / `notFollowedBy` | ✓ |
| 条件 | `.where()` | `CepPatternSingleModel.where` (`IEvalFunction`) | ✓ |
| 直到条件 | `.until()` | `CepPatternSingleModel.until` (`IEvalFunction`) | ✓ |
| 可选 | `.optional()` | `optional = true` | ✓ |
| 循环匹配 | `.oneOrMore()` | `oneOrMore = true` | ✓ |
| 重复次数 | `.times(N)`, `.times(N, M)` | `times` (`IntRangeBean`) | ✓ |
| 贪婪 | `.greedy()` | `greedy = true` | ✓ |
| 连续匹配 | `.consecutive()` | `consecutive = true` | ✓ |
| 组合匹配 | `.allowCombinations()` | `allowCombinations = true` | ✓ |
| 全局窗口 | `.within(Duration)` | `CepPatternModel.within` | ✓ |
| 状态间窗口 | `.within(Duration, PREVIOUS_AND_CURRENT)` | `gapWithin` | ✓ |
| 分组 pattern | `begin("g", Pattern.begin(...))` | `CepPatternGroupModel` | ✓ |
| 匹配后策略 | `.skipToFirst()` / `.skipToLast()` | `afterMatchSkipStrategy` (enum) | ✓ |
| XDSL 声明式 | 无 | XDSL schema (`/nop/schema/stream/pattern.xdef`) | **新增** |

### 6.3 差异分析

| 差异 | 分类 | 严重性 |
|------|------|--------|
| XDSL 声明式模型 | Improvement | —（Flink 无此功能） |
| API 等价性 | OK | — |
| IEvalFunction 条件 | Design Difference | —（nop-stream 使用 nop-xlang 的 `IEvalFunction`，Flink 使用 `IterativeCondition`） |
| 条件语义兼容 | OK | —（`CepPatternBuilder` 将 `IEvalFunction` 适配为 `IterativeCondition`） |

**结论**: nop-stream 的 CepPatternModel 是 Flink Pattern API 的纯超集，基于 XDSL schema 增加了声明式建模能力。无语义缺口或退化。`CepPatternBuilder` 正确地将 XDSL 模型转化为 Flink 兼容的 Pattern API。

---

## 7. 差距汇总表

| # | 维度 | 发现 | 分类 | 严重性 | 说明 |
|---|------|------|------|--------|------|
| 1 | NFA 编译/执行 | 完全匹配 | OK | — | Clean extraction |
| 2 | SharedBuffer | 完全匹配（除缓存实现） | OK | — | Clean extraction |
| 3 | SharedBuffer 缓存 | 使用 ConcurrentHashMap 替代 Guava Cache | ExtractionDegradation | P3 | 无自动 LRU 驱逐，高吞吐下缓存可能无限增长 |
| 4 | CepOperator 状态后端 | 分级接线（backend → fallback）**正确实现** | OK | — | NOT using SimpleKeyedStateStore in prod |
| 5 | CepOperator 状态后端注入 | Runtime 是否调用 `setKeyedStateBackend()` 未验证 | Wiring Gap | P1 | 外部依赖 — 需要 runtime 层审计 |
| 6 | Checkpoint snapshot/restore | Runtime 是否调用 operator snapshot/restore 未验证 | Wiring Gap | P1 | 外部依赖 — 需要 runtime 层审计 |
| 7 | 设计注释过时 | Javadoc 声称 "hardcoded MemoryKeyedStateBackend" 与实际代码不符 | Doc | P2 | `CepOperator.java:80-83` |
| 8 | 事件时间超时 - 内部处理 | 完全匹配（额外 dangling match 清理） | OK (Improvement) | — | nop-stream 额外实现了 dangling partial match 清理 |
| 9 | 事件时间超时 - 外部 watermark | Watermark 从 runtime 到 operator 的传播管路未验证 | Wiring Gap | P1 | 外部依赖 — 需要 runtime 层审计 |
| 10 | AfterMatchSkipStrategy | 全部5种策略完全匹配 | OK | — | Clean extraction |
| 11 | 声明式模型 | XDSL-backed CepPatternModel 是 Flink API 的纯超集 | Improvement | — | 新增能力, Flink 无此功能 |
| 12 | IEvalFunction 条件 | nop-xlang 条件引擎替代 Flink IterativeCondition | Design Difference | — | 语义兼容，通过 CepPatternBuilder 适配 |

### 优先级定义

| 等级 | 含义 | 数量 |
|------|------|------|
| P0 | Correctness blocking | 0 |
| P1 | Design contract violation (关键功能缺失 — wiring gaps) | 3 |
| P2 | Missing capability / doc | 1 |
| P3 | Optimization/minor | 1 |
| Improvement | 超越 Flink 的功能 | 2 |
| OK | 完全匹配 | 6 |

---

## 8. 修复建议

### P1 关键修复

| 建议 | 对应差距 | 所属 Item |
|------|---------|-----------|
| 审计 runtime 层：验证 `AbstractStreamOperator.setKeyedStateBackend()` 在 operator 初始化时被调用 | #5 | Item 11 |
| 审计 runtime 层：验证 checkpoint coordinator 调用 operator 的 `snapshotState()` / `restoreState()` | #6 | Item 11 |
| 审计 runtime 层：验证 watermark 从 source 到 operator 的完整传播路径（TimestampsAndWatermarksOperator 自动插入、多输入对齐） | #9 | Item 10 |

### P2 重要修复

| 建议 | 对应差距 |
|------|---------|
| 更新 `CepOperator.java:80-83` 的 Javadoc 注释，反映真实的分级 backend 接线逻辑 | #7 |

### P3 小改进

| 建议 | 对应差距 |
|------|---------|
| 考虑为 SharedBuffer 缓存添加 LRU 驱逐或大小限制，防止高吞吐下的无限增长 | #3 |

---

## 9. 与 Plans 316/317 的协调说明

本分析原计划依赖 `01-flink-source-audit.md` 和 `02-nopstream-live-audit.md` 的 subsection 结构进行成对对比。由于两个 plan 仍为 `active` 状态且交付物不存在，本分析进行了直接的源码级对比。

**与 roadmap 已知缺口的对比：**

| Roadmap 声称缺口 | 实际发现 | 修正 |
|------------------|---------|------|
| "CEP 使用 SimpleKeyedStateStore 而非统一状态后端" | Production CepOperator 不使用 SimpleKeyedStateStore；使用分级 backend 接线 | **不准确** — 真实缺口是 runtime 层面的状态后端注入，非 CepOperator 代码本身 |
| "nop-stream-cep 的 NFA 状态未参与 checkpoint" | NFAState 存储在 `ValueState<NFAState>` 中，通过 keyed state backend snapshot/restore | **不准确** — CepOperator 代码层面 checkpoint 参与已实现；真实缺口是 runtime 是否调用 snapshot/restore |
| "CepOperator 内部 watermark 不正确（Long.MIN_VALUE）" | `currentWatermark` 初始化为 Long.MIN_VALUE 但 `processWatermark()` 正确更新并触发 `onEventTime()` | **不准确** — 内部处理正确；真实缺口是外部 watermark 传播 |

---

## 10. 引用索引

### Flink 类/方法引用

| 类 | 路径 |
|----|------|
| `NFACompiler` | `flink-cep/.../nfa/compiler/NFACompiler.java` |
| `NFA` | `flink-cep/.../nfa/NFA.java` |
| `NFAState` | `flink-cep/.../nfa/NFAState.java` |
| `ComputationState` | `flink-cep/.../nfa/ComputationState.java` |
| `SharedBuffer` | `flink-cep/.../nfa/sharedbuffer/SharedBuffer.java` |
| `SharedBufferAccessor` | `flink-cep/.../nfa/sharedbuffer/SharedBufferAccessor.java` |
| `DeweyNumber` | `flink-cep/.../nfa/DeweyNumber.java` |
| `Lockable` | `flink-cep/.../nfa/sharedbuffer/Lockable.java` |
| `EventId` | `flink-cep/.../nfa/sharedbuffer/EventId.java` |
| `NodeId` | `flink-cep/.../nfa/sharedbuffer/NodeId.java` |
| `SharedBufferNode` | `flink-cep/.../nfa/sharedbuffer/SharedBufferNode.java` |
| `SharedBufferEdge` | `flink-cep/.../nfa/sharedbuffer/SharedBufferEdge.java` |
| `CepOperator` | `flink-cep/.../operator/CepOperator.java` |
| `AfterMatchSkipStrategy` | `flink-cep/.../nfa/aftermatch/AfterMatchSkipStrategy.java` |
| `NoSkipStrategy` | `flink-cep/.../nfa/aftermatch/NoSkipStrategy.java` |
| `SkipPastLastStrategy` | `flink-cep/.../nfa/aftermatch/SkipPastLastStrategy.java` |
| `SkipToFirstStrategy` | `flink-cep/.../nfa/aftermatch/SkipToFirstStrategy.java` |
| `SkipToLastStrategy` | `flink-cep/.../nfa/aftermatch/SkipToLastStrategy.java` |
| `SkipToNextStrategy` | `flink-cep/.../nfa/aftermatch/SkipToNextStrategy.java` |

### nop-stream 类/方法引用

| 类 | 路径 |
|----|------|
| `NFACompiler` | `nop-stream-cep/.../nfa/compiler/NFACompiler.java` |
| `NFA` | `nop-stream-cep/.../nfa/NFA.java` |
| `NFAState` | `nop-stream-cep/.../nfa/NFAState.java` |
| `ComputationState` | `nop-stream-cep/.../nfa/ComputationState.java` |
| `SharedBuffer` | `nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java` |
| `SharedBufferAccessor` | `nop-stream-cep/.../nfa/sharedbuffer/SharedBufferAccessor.java` |
| `DeweyNumber` | `nop-stream-cep/.../nfa/DeweyNumber.java` |
| `Lockable` | `nop-stream-cep/.../nfa/sharedbuffer/Lockable.java` |
| `EventId` | `nop-stream-cep/.../nfa/sharedbuffer/EventId.java` |
| `NodeId` | `nop-stream-cep/.../nfa/sharedbuffer/NodeId.java` |
| `SharedBufferNode` | `nop-stream-cep/.../nfa/sharedbuffer/SharedBufferNode.java` |
| `SharedBufferEdge` | `nop-stream-cep/.../nfa/sharedbuffer/SharedBufferEdge.java` |
| `CepOperator.open()` | `nop-stream-cep/.../operator/CepOperator.java:202-217` |
| `CepOperator.snapshotState()` | `nop-stream-cep/.../operator/CepOperator.java:307-314` |
| `CepOperator.restoreState()` | `nop-stream-cep/.../operator/CepOperator.java:317-335` |
| `CepOperator.processWatermark()` | `nop-stream-cep/.../operator/CepOperator.java:338-347` |
| `CepOperator.onEventTime()` | `nop-stream-cep/.../operator/CepOperator.java:397-457` |
| `CepOperator.currentWatermark` | `nop-stream-cep/.../operator/CepOperator.java:165` |
| `AfterMatchSkipStrategy` | `nop-stream-cep/.../nfa/aftermatch/AfterMatchSkipStrategy.java` |
| `CepPatternModel` | `nop-stream-cep/.../model/CepPatternModel.java` |
| `CepPatternSingleModel` | `nop-stream-cep/.../model/CepPatternSingleModel.java` |
| `CepPatternGroupModel` | `nop-stream-cep/.../model/CepPatternGroupModel.java` |
| `CepPatternBuilder` | `nop-stream-cep/.../model/builder/CepPatternBuilder.java` |
