# nop-stream CEP 引擎设计

> Status: active
> Created: 2026-05-19

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
- **缓存配置**：通过 `SharedBufferCacheConfig` 控制缓存大小和驱逐策略

### 5.3 状态依赖

SharedBuffer 需要通过 `KeyedStateStore` 进行状态持久化：
- 当前 `CepOperator` 使用 `SimpleKeyedStateStore`（无 key 隔离）
- 生产环境应使用 `MemoryKeyedStateBackend` 以实现 per-key 状态隔离

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

**推荐使用方式**：当前推荐使用方式二（直接使用 NFA + SharedBuffer），因为方式一（DataStream API）的 CepOperator 存在 key 隔离和 watermark 问题。

**已验证的能力**（通过 FraudDetectionDemo，使用方式二）：
- 4 种欺诈模式全部可运行
- Pattern DSL 构建复杂模式
- NFA 匹配逻辑正确
- SharedBuffer 内存模式工作正常

**已知限制**：
- 事件时间超时未生效（`currentWatermark()` 返回 `Long.MIN_VALUE`）
- SharedBuffer 传入 `null` serializer，不支持持久化
- 无 key 隔离（SimpleKeyedStateStore），多 key 场景数据混杂
