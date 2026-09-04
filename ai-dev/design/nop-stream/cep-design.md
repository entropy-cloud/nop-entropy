# nop-stream CEP 引擎设计

> Status: active
> Created: 2026-05-19
> Parent: `01-architecture-baseline.md` §6（集成扩展）

## 1. 定位

nop-stream-cep 是 nop-stream 中最成熟的子模块，提供复杂事件处理（CEP）能力。它从 Apache Flink CEP 项目剥离代码，去除对 Flink 的依赖，在 Nop 平台中独立可用。

**核心能力**：
- 基于模式（Pattern）的事件序列匹配
- 支持连续、非连续、带条件的事件模式
- 支持事件时间超时
- 支持匹配后的跳过策略

**使用场景**：
- 欺诈检测（短时间内多笔大额交易）
- 异常检测（地理位置异常、金额异常）
- 业务规则引擎（登录→改密→提现序列）
- 任何需要识别事件模式中时序关系的场景

## 1.1 不变式（Invariants）

> 交叉引用：`ai-dev/audits/nop-stream-invariants/invariant-catalog.md` §5 不变式 #4（覆盖失败族 F4）。

- **SharedBuffer/Lockable 释放对称性（#4）**：(a) 每个 `lock()` 必须有对称的 `release()`/`releaseOrDetach()`；(b) `release()` 时 refCounter<=0（over-release）必须 fail-fast（抛 `StreamRuntimeException`），不得静默返回 true；(c) `advanceTime`/`releaseNode`/`CepOperator` 清理路径不得遗留未释放的 SharedBuffer 条目（EventId 不得复用冲突）。
- **门禁（已入 CI）**：JUnit `TestCepReleaseSymmetryInvariant`（`nop-stream-cep/src/test/.../nfa/sharedbuffer`）——在既有反应式测试（`TestLockable`/`TestLockableOverRelease`/`TestSharedBuffer` 等 7 个）之上扩展：Lockable 参数化序列表（over-release fail-fast、对称 lock/release 守恒、releaseOrDetach 语义）+ SharedBuffer 条目生命周期对称性（lock 数与 release 数守恒、条目与事件随释放移除不泄漏）。
- **历史证据**：R16-AR-8（双重释放静默 true）、R16-AR-7（DeweyNumber 溢出）、R16-AR-6（CepOperator 清空非 start state）、R11-AR-4（pending 超时跳过 releaseNode）、R13-AR-15/R14-AR-4（advanceTime 不清理 eventsBuffer）、R11-AR-6（TOCTOU）（详见 catalog §5 不变式 #4）。

## 2. 核心架构

### 2.1 组件关系

```
Pattern DSL                    用户定义的模式
    ↓ NFACompiler
NFA (Non-deterministic Finite Automaton)   编译后的自动机
    ↓ process(event)
SharedBuffer                  存储事件和部分匹配
    ↓ 提取匹配
Map<String, List<T>>          匹配结果（模式名 → 匹配事件列表）
```

### 2.2 两种使用方式

**方式一：通过 DataStream API**

```java
PatternStream<T> ps = CEP.pattern(dataStream, pattern);
ps.select(matches -> { ... });
```

需要完整的 StreamExecutionEnvironment 管线。

**方式二：直接使用 NFA + SharedBuffer**（当前推荐）

```java
NFA<Event> nfa = NFACompiler.compile(pattern, timeoutHandling, comparator);
SharedBuffer<Event> buffer = new SharedBuffer<>(stateStore, serializer, config);

// 逐条处理事件
NFAState state = nfa.createInitialNFAState();
Collection<Map<String, List<Event>>> matches = nfa.advanceTimeSharedBuffer(
    sharedBufferAccessor, event, timestamp, state);
```

FraudDetectionDemo 使用方式二，完全绕过 DataStream API。

## 3. Pattern DSL

### 3.1 模式构建 API

Pattern 采用 Builder 风格构建：

```
Pattern.begin("patternName")      起始模式
  .where(condition)               添加条件
  .or(condition)                  或条件
  .until(condition)               终止条件（用于循环模式）
  .times(n)                       精确匹配 n 次
  .timesOrMore(n)                 匹配 n 次或更多
  .oneOrMore()                    匹配 1 次或更多
  .optional()                     匹配 0 次或 1 次
  .consecutive()                  要求连续匹配
  .allowCombinations()            允许组合匹配
  .within(Time)                   超时限制
  .followedBy("next")             非严格跟随（允许中间事件）
  .followedByAny("next")          非确定性跟随
  .next("next")                   严格跟随（不允许中间事件）
```

### 3.2 Pattern 模型层次

nop-stream-cep 包含一套基于 Nop XMeta 的模型定义：

| 模型类 | 职责 |
|--------|------|
| `CepPatternModel` | 完整的模式定义（包含多个 PatternPart） |
| `CepPatternGroupModel` | 模式组（组合多个子模式） |
| `CepPatternSingleModel` | 单个模式步骤（包含条件、量词） |
| `CepPatternPartModel` | 模式片段（可复用的子模式） |

`CepPatternBuilder` 从模型对象构建 Flink 风格的 Pattern 对象。

### 3.3 条件类型

| 条件 | 实现 | 语义 |
|------|------|------|
| `SimpleCondition` | Lambda/匿名类 | 对单个事件的条件判断 |
| `SubtypeCondition` | instanceof | 事件类型过滤 |

## 4. NFA（非确定有限自动机）

### 4.1 设计决策

**选了什么**：基于 NFA 的模式匹配，与 Flink CEP 的实现一致。

**为什么**：
1. NFA 天然支持非确定性匹配（一个事件可能触发多条转移路径）
2. NFA 可以高效处理复杂模式（循环、可选、组合）
3. Flink CEP 的 NFA 实现经过大规模验证

### 4.2 NFA 编译

`NFACompiler` 将 Pattern 树编译为 NFA 状态图：

```
Pattern Tree                    NFA States
  begin("start")          →     START_STATE
    .where(...)                   |
  followedBy("middle")    →     MIDDLE_STATE
    .where(...)                   |
  next("end")             →     END_STATE
    .where(...)                   |
                               FINAL_STATE
```

编译过程：
1. 递归遍历 Pattern 树
2. 为每个 Pattern 节点创建 NFA State
3. 创建 State 转移（包括 Take/Ignore/Proceed 三种类型）
4. 处理量词（times/oneOrMore/optional）→ 创建循环转移

### 4.4 NFA 状态转移类型

| 转移类型 | 语义 | 对应 Pattern 关系 |
|---------|------|------------------|
| **Take** | 消费当前事件，状态前进。将事件记录到 SharedBuffer | `where()` 条件满足时 |
| **Ignore** | 忽略当前事件，状态不变。事件不进入 SharedBuffer | `where()` 条件不满足时 |
| **Proceed** | 不消费事件，直接前进到下一状态。用于实现非严格跟随 | `followedBy()` 的间隙 |

### 4.5 匹配过程详解

NFA 的匹配核心是 **事件驱动的状态扩散**：

```
advanceTime(event, timestamp)
  │
  ├── 获取当前所有活跃的 computation states（部分匹配状态）
  │     每个 computation state 包含:
  │     - 当前 NFA 状态（START / MIDDLE / END / FINAL）
  │     - SharedBuffer 中的 Dewey 版本号（指向部分匹配的事件链）
  │     - 开始时间戳（用于 within() 超时判断）
  │
  ├── 对每个 computation state，遍历该状态的所有出边（转移）:
  │     │
  │     ├── Take 转移:
  │     │     if (condition.matches(event)):
  │     │       在 SharedBuffer 中追加事件
  │     │       创建新的 computation state（状态前进 + 新 Dewey 编号）
  │     │     else:
  │     │       不产生新状态
  │     │
  │     ├── Ignore 转移:
  │     │     原状态保持不变，不做任何记录
  │     │     （这个分支不需要创建新状态）
  │     │
  │     └── Proceed 转移:
  │           不消费事件，直接前进到目标状态
  │           创建新的 computation state（状态前进 + 原事件链不变）
  │           这就是 followedBy() 允许中间事件的机制
  │
  ├── 收集所有新产生的 computation states
  │
  ├── 对每个新状态:
  │     if (state == FINAL_STATE):
  │       从 SharedBuffer 中提取匹配事件链
  │       构建 Map<patternName, List<Event>> 结果
  │       应用 AfterMatchSkipStrategy 决定后续搜索起点
  │       → 加入 matches 集合
  │
  └── 处理超时:
        if (within 约束存在 && timestamp - state.startTimestamp > within):
          清理该 computation state
          如果有 TimedOutPartialMatchHandler → 回调通知超时部分匹配
```

**关键机制**：同一个事件可能同时匹配多个 Take 转移（到达不同的 NFA 状态），产生多个新的 computation state。这就是 **非确定性** 的来源——NFA 同时探索所有可能的匹配路径。SharedBuffer 通过 Dewey 编号为每条路径维护独立的版本。

### 4.6 SharedBuffer 的事件共享机制

SharedBuffer 的核心价值：**同一事件可以被多个部分匹配引用，但只存储一份**。

```
事件 A ──→ [页面0]
事件 B ──→ [页面0]    ← A 和 B 物理存储各一份
事件 C ──→ [页面1]

匹配路径1: A → B → C    Dewey: 1.0.0
匹配路径2: A → C         Dewey: 2.0
匹配路径3: B → C         Dewey: 3.0

引用计数: A=2, B=2, C=3
```

当匹配完成并应用 AfterMatchSkipStrategy 后，引用计数为 0 的事件页面可以被释放。这就是 SharedBuffer 的垃圾回收机制——通过引用计数而非定时清除。

## 5. SharedBuffer

### 5.1 设计决策

**选了什么**：基于引用计数 + Dewey 编号版本控制的共享事件存储。

**为什么**：
1. CEP 场景中同一事件可能被多条匹配路径引用（NFA 的非确定性），复制事件代价高
2. Dewey 编号为每条匹配路径提供唯一的版本标识，支持同一事件的多次匹配
3. 引用计数机制实现精确的垃圾回收：只有当事件不再被任何活跃的部分匹配引用时才释放

**拒绝了什么**：
- 为每个部分匹配独立复制事件列表 → 内存开销随匹配路径数线性增长
- 定时全量清除 → 无法区分活跃匹配和已完成匹配引用的事件

### 5.2 数据结构

SharedBuffer 内部维护：
- **事件页面**：按时间戳分页存储事件（物理存储唯一副本）
- **Dewey 编号**：为每个部分匹配维护唯一的版本号（如 `1.2.0`），编码匹配路径的分支历史
- **引用计数**：跟踪每个事件被多少个活跃的部分匹配引用
- **缓存**：`eventsBufferCache`（事件）与 `entryCache`（节点）使用 Guava `Cache` 作为 LRU 缓存原语，容量由 `SharedBufferCacheConfig` 控制

### 5.2.1 缓存选型决策

**选了什么**：Guava `com.google.common.cache.Cache`，经 `CacheBuilder.newBuilder().maximumSize(slots).recordStats().removalListener(...).build()` 构造。

**为什么**：
1. **Guava 已是仓库管理的直接依赖**：`nop-commons` 显式声明 `com.google.guava:guava`，`nop-stream-cep` 经 `nop-stream-core → nop-commons` 传递获得 compile classpath；`nop-stream-cep` 模块的 `pom.xml` 同时显式声明 guava 以表意清晰。
2. **内建原子 LRU + 统计**：`maximumSize` 提供原子级 LRU 驱逐（无竞态窗口），`recordStats()` 提供 hit/miss/eviction 统计（被 `CEP_CACHE_STATISTICS_INTERVAL` 周期日志消费），`removalListener` 仅在 SIZE 驱逐时输出 debug 日志。
3. **API 契约清晰**：`getIfPresent`（nullable 返回，无 checked exception）、`invalidate`（手动移除）、`asMap()`（提供 `forEach`/`putAll`/`keySet().removeAll`/`keySet().removeIf` 等批操作）。

**拒绝了什么**：
- 自定义 `LruCache`（已删除）：`ConcurrentHashMap` + 访问序 `LinkedHashMap` 双结构，`put()` 与 `evictOverflow()` 之间存在非原子窗口，多线程下可能 over-eviction。虽 `SharedBuffer` 单线程访问（per-key `SharedBufferAccessor`），代码结构仍脆弱。
- Caffeine 迁移：`nop-commons` 已声明 Caffeine 依赖，技术上可用，但不拒绝；当前 roadmap 与 Flink CEP 一致选择 Guava。Async cache refresh 等高级能力不在当前 scope。

**RemovalListener 契约**：仅当 removal cause 为 `SIZE`/`COLLECTED`/`EXPIRED`（即 `wasEvicted()` 语义）时输出 debug 日志；`EXPLICIT`（`invalidate`/`invalidateAll`）/`REPLACED`（同 key 覆盖）静默，因为它们属于 write-through / `flushCache` clear-on-success 的正常语义。

**flushCache 语义**：write-back flush + clear-on-success（与 LruCache 时代相同）：snapshot cache → `putAll` 到 state → 成功时 `keySet().removeAll` 清空 cache（EXPLICIT，不触发 eviction 统计）→ 异常时 `putAll` 回填 cache。

### 5.3 状态依赖

SharedBuffer 需要通过 `KeyedStateStore` 进行状态持久化：
- `CepOperator.open()`（`CepOperator.java:209`）从 `stateBackend` 创建 `IKeyedStateBackend`（与 `WindowOperator` 同一模式）；若未配置 state backend，fallback 到 `MemoryKeyedStateBackend` 并发 WARN 日志（checkpoint 一致性不保证）。
- 所有 CEP 状态（`computationStates` ValueState、`elementQueueState` MapState、SharedBuffer 引用）落到 `IKeyedStateBackend`，参与 checkpoint/restore。
- 平台 `SimpleKeyedStateStore` 仍存在但**不再被 `CepOperator` 使用**——它是 nop-stream-core 内部的简易实现（`common/state/simple/`），仅作测试或无算子后端场景的占位；生产 CEP 走 `IKeyedStateBackend` 统一路径（G18/G19/G20 已闭环，见 `nop-stream-production-roadmap.md` Current baseline）。

## 6. CepOperator

### 6.1 作为算子

`CepOperator` 是 CEP 引擎在 DataStream API 中的算子封装：

```
OneInputStreamOperator<IN, OUT>
  └── CepOperator<IN, OUT>
        ├── Pattern 定义的 NFA
        ├── SharedBuffer 事件存储
        ├── PatternProcessFunction 用户回调
        └── NFAState 状态（每个 key 独立）
```

### 6.2 CepWindowOperator

`CepWindowOperator` 将 CEP 作为窗口算子集成：
- 使用 `CepWindowAssigner` 将事件分配到 CEP 窗口
- 使用 `CepWindowTrigger` 在模式匹配完成时触发
- 复用 WindowOperator 的框架，但用 NFA 替代传统的窗口聚合

## 7. 匹配后策略

### 7.1 AfterMatchSkipStrategy

匹配成功后如何跳过后续匹配：

| 策略 | 语义 |
|------|------|
| `NoSkip` | 不跳过，每个匹配都报告 |
| `SkipPastLastStrategy` | 跳过上一个匹配的最后一条事件 |
| `SkipToFirstStrategy` | 跳到指定模式的第一条匹配事件 |
| `SkipToLastStrategy` | 跳到指定模式的最后一条匹配事件 |

### 7.2 FollowKind

模式间的跟随关系：

| FollowKind | 语义 |
|---|---|
| `STRICT` | 严格跟随（next），不允许中间事件 |
| `SKIP_TILL_NEXT` | 跳过非匹配事件（followedBy） |
| `SKIP_TILL_ANY` | 非确定性跟随（followedByAny） |

## 8. 成熟度与限制

**推荐使用方式**：方式二（直接使用 NFA + SharedBuffer）已通过 FraudDetectionDemo 验证；方式一（DataStream API）的 `CepOperator` 现已通过 `IKeyedStateBackend` 统一接入 keyed state（key 隔离已闭环，见 §5.3），但事件时间 watermark 路径仍有已知限制（见下"已知限制"）。两种方式的选择取决于是否需要 checkpoint 与 keyed state。

**已验证的能力**（通过 FraudDetectionDemo，使用方式二）：
- 4 种欺诈模式全部可运行
- Pattern DSL 构建复杂模式
- NFA 匹配逻辑正确
- SharedBuffer 内存模式工作正常

**已知限制**：
- 事件时间超时未生效（`currentWatermark()` 返回 `Long.MIN_VALUE`）
- SharedBuffer 传入 `null` serializer，不支持持久化
- `CepOperator.open()` 未配置 state backend 时 fallback 到 `MemoryKeyedStateBackend` + WARN（checkpoint 一致性不保证）；正确用法是显式配置 `IStateBackend`（如 `MemoryStateBackend`）使 CEP 走 `IKeyedStateBackend` 统一 checkpoint 路径

## 9. 恢复键类与 timer 台账（AR-10/AR-11 — Plan 2026-09-04-1326-1 Phase 4 D4）

**问题**：`CepOperator` 以 `Object.class` 自建 keyed 后端，旁路平台键重物化守卫（`MemoryStateSerDe.deserializeKey` 仅当 `keyType != Object.class` 才生效）→ 非 String 键（含默认非 keyed 路径的 `Byte` 键）经 JSON 持久化往返后类漂移（`Long(9)` → `Integer(9)`），恢复出的 `nfaState/eventQueues/SharedBuffer` 落漂移键下，运行时访问永远 miss（状态静默清零）；timer 台账以裸 key 对象持久化，同样漂移 → 恢复后排水以漂移键跑 `onEventTime`，真实键的 pending timer 从未真实发射。

**裁定（D4）——「确定性通道钉死 + 首 key 捕获 + checkpoint 携带」混合方案**：

1. **非 keyed 全局路径（确定性）**：`PatternStreamBuilder` 非 keyed 分支构造 `CepOperator` 时显式传 `Byte.class`——其 `NullByteKeySelector` 恒返回 `(byte) 0`，类确定且不依赖任何运行时观察。
2. **keyed 路径（首 key 捕获 + checkpoint 携带）**：`KeyedStream`/`KeySelector` 无键类通道（用户 lambda 无法静态得知返回类），构建期不裁。运行时首 key 到达（上游 `KeyExtractingOutput` 先于 `processElement` 置好 backend 当前键）捕获 `key.getClass()`；每次 checkpoint 把解析出的键类作为 `cep-key-class` operator state 持久化。恢复时 `restoreState` 先于 `open()` 读出该类 → `createKeyedStateBackend(clazz)` 以真实键类建后端 → `MemoryStateSerDe` 键重物化守卫对全部 CEP keyed state 生效（漂移键回原始类）。
3. **时序论证**：携带键类的 checkpoint 必然存在键类——keyed state 只在 key 到达后存在，首 key 到达即捕获；空键作业的 checkpoint 无 keyed state，恢复为空（键类空缺无害）。恢复链 `restoreState`（读类）→ `open()`（建后端 + `applyPendingRestoreState`）已被 `TestCepCheckpointRestoreE2E` 钉死，键类在一切恢复动作之前可用。
4. **timer 台账类型化持久化（AR-11）**：快照形态 `{"keyClass": 类名, "key": 规范值, "timers": [t…]}`；恢复时经与 `MemoryStateSerDe.deserializeKey` 同构的 JSON 重物化（`JsonTool.serialize → parseBeanFromText(clazz)`）还原原始键对象——台账键 == 后端真实键，排水（`processWatermark` 逐键切换上下文 + `onEventTime`）对真实键执行，pending timer 真实发射；台账条目在排水时清理（有界）。无 `keyClass` 的历史形态（pre-AR-11 checkpoint）回落裸键 + WARN（不静默丢弃）。
5. **DSL 路径裁定**：`AdvancedTransforms.buildCep`（经 `CEP.pattern`）走同一 `PatternStreamBuilder` 通道——keyed 输入按首 key 捕获；本轮**不**给 `stream.xdef`/`KeyedStream` 加 `keyType` 声明属性。

**拒绝的替代方案**：

- **(a) API 扩展（`CEP.pattern`/`KeyedStream` 携带可选键类 + DSL `keyType` 属性）**：nop-stream-core `KeyedStream` 是跨模块公共 API（Protected Area，plan-first），且 `keyBy(KeySelector)` 的既有调用面（lambda/方法引用）无法受益——只有显式声明调用点才传类，默认路径依旧裸奔；DSL `keyExpr` 的静态类型推导对表达式语言不可靠。为覆盖「lambda 键选择器」这一主形态，运行时捕获是必要机制，API 面扩展只服务少数显式声明场景，收益/侵入比不成立。F-05（`WindowedStreamImpl` 四路径 `Object.class`，归 plan {2}）如后续需要键类通道，可复用本裁定引入的 checkpoint 携带机制。
- **(b-pure) 纯运行时首 key 捕获（不持久化键类）**：恢复发生在新实例 `open()`，此刻尚无 key 到达——捕获机制对恢复路径失效（循环依赖：需要键类才能正确恢复键，需要键才能捕获类）。checkpoint 携带打破该循环。
- **(c) 仅 CEP 侧显式 setter + DSL 声明**：默认非 keyed 路径（用户从未配置）不经过任何显式声明面，`Byte` 键漂移不设防。

**测试**：`TestCepKeyClassRecovery`——`longKeyedStateAddressableAfterJsonRoundTripRestore`（Long 键 JSON 往返：backend `getKeyType()==Long`（接线验证）+ 恢复键 equals 原始键且 `getClass()` 一致 + kill/restore e2e 恰一次匹配）、`pendingTimersFireForRealKeyAfterRestore`（台账键恢复为 Long 非 Integer + 水位推进后 timeout handler 真实发射 + 队列排空 + 台账清空）、`nonKeyedDefaultPathRestoresUnderByteKey`（默认路径 `Byte.class` 同口径）。

**成熟度更新（§8 已知限制的收敛）**：`CepOperator` keyed 后端不再以 `Object.class` 创建（显式 `Byte.class` / checkpoint 携带类 / 捕获类三通道解析）；CEP 恢复的键类漂移与 timer 台账漂移已修复。
