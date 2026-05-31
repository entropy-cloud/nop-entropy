# 维度 04：数据模型与状态设计

> 注：nop-stream 是流处理引擎框架模块，没有传统 ORM 模型。本维度调整为检查流处理引擎内部的数据模型和状态设计质量。

## 第 1 轮（初审）

### [维度04-01] CEP NFAState 与 JSON 检查点序列化机制不兼容

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFAState.java:28-63`，`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:106,205`，`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:588-597`
- **证据片段**:
```java
// NFAState.java 第 28-63 行 — PriorityQueue 字段，无默认构造函数
public class NFAState {
    private Queue<ComputationState> partialMatches;   // PriorityQueue
    private Queue<ComputationState> completedMatches; // PriorityQueue
    private boolean stateChanged;
    private boolean isNewStartPartialMatch;
    public NFAState(Iterable<ComputationState> states) { ... }
```
```java
// CepOperator.java 第 106, 205 行 — 直接将 NFAState 存入 ValueState
private transient ValueState<NFAState> computationStates;
computationStates = keyedStateStore.getState(
    new ValueStateDescriptor<>(NFA_STATE_NAME, NFAState.class));
```
```java
// MemoryStateSerDe.java 第 588-597 行 — JSON 反序列化需要默认构造函数
private <T> T deserializeValue(Object obj, Class<T> type) {
    if (obj == null) return null;
    if (type.isInstance(obj)) return (T) obj;
    String json = JsonTool.serialize(obj, false);
    return JsonTool.parseBeanFromText(json, type);  // NFAState 无默认构造函数 → 失败
}
```
- **严重程度**: P1
- **现状**: `NFAState` 内部使用 `PriorityQueue<ComputationState>`（非标准 JSON 可序列化类型），且无默认构造函数。但 `CepOperator` 将 `NFAState` 作为 `ValueState<NFAState>` 存入 `MemoryKeyedStateBackend`，checkpoint 路径经过 JSON 序列化管线。`PriorityQueue` 在 Jackson 反序列化时会变成 `ArrayList`，且 `NFAState` 缺少默认构造函数导致 `parseBeanFromText()` 失败。
- **风险**: CEP 作业启用 checkpoint 后重启恢复时，NFAState 的 JSON 反序列化将失败，导致 CEP 模式匹配状态丢失或运行时异常。
- **建议**: 为 `NFAState` 添加 Jackson 注解支持（如 `@JsonCreator`、`@JsonProperty`），或实现自定义的 `TypeSerializer<NFAState>` 以避免 JSON 序列化管线的不兼容。
- **信心水平**: 很可能
- **误报排除**: 不是"看起来不优雅"——这是一个可导致 CEP checkpoint 恢复失败的结构性序列化不兼容问题。
- **复核状态**: 未复核

---

### [维度04-02] StateSnapshot / CompletedCheckpoint 内部可变 Map 暴露

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/StateSnapshot.java:25-27`，`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CompletedCheckpoint.java:73-75`
- **证据片段**:
```java
// StateSnapshot.java 第 25-27 行
public Map<String, Object> getStateData() {
    return stateData;  // 直接暴露可变内部引用
}

// CompletedCheckpoint.java 第 73-75 行
public Map<TaskLocation, TaskStateSnapshot> getTaskStates() {
    return taskStates;  // 直接暴露可变内部引用
}
```
- **严重程度**: P2
- **现状**: `getStateData()` 和 `getTaskStates()` 直接返回内部可变 Map，未包装为 `Collections.unmodifiableMap()`。同文件中 `getVertices()` 等已正确使用不可变包装，说明有此惯例但未全面执行。
- **风险**: 调用方无意修改可破坏检查点数据一致性。
- **建议**: 返回 `Collections.unmodifiableMap()` 包装。
- **信心水平**: 确定
- **误报排除**: 同项目中已有正确模式（JobGraph.getVertices()），此处不一致是真实遗漏。
- **复核状态**: 未复核

---

### [维度04-03] MemoryKeyedStateBackend.states 使用非线程安全 HashMap

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:63`
- **证据片段**:
```java
// MemoryKeyedStateBackend.java 第 63、106-113 行
private final Map<String, Object> states = new HashMap<>();

public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
    ValueState<T> state = (ValueState<T>) states.get(stateProperties.getName());
    if (state == null) {
        state = new MemoryValueState<>(this, stateProperties);
        states.put(stateProperties.getName(), state);
    }
    return state;
}
```
- **严重程度**: P2
- **现状**: `states` 字段为 `HashMap`，`getState()` 使用 check-then-act 模式。当前单线程场景下安全，但 `snapshotState()` 可能被 checkpoint 线程并发调用。`TaskStateSnapshot` 已使用 `ConcurrentHashMap`，说明项目对并发有意识但此处未做防护。
- **风险**: 未来引入并发 checkpoint 时可能触发 ConcurrentModificationException。
- **建议**: 将 `HashMap` 改为 `ConcurrentHashMap`。
- **信心水平**: 很可能
- **误报排除**: 同项目中的 `TaskStateSnapshot` 已做正确处理，此处不一致是真实遗漏。
- **复核状态**: 未复核

---

### [维度04-04] CheckpointCoordinator.currentFingerprint 字段声明位置异常

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:515`
- **证据片段**:
```java
// 第 31-53 行（正常字段声明区域）
private final String jobId;
private final String pipelineId;
private final CheckpointIDCounter checkpointIdCounter;
// ... 其他字段 ...

// 第 515 行（异常位置）
private StreamModelFingerprint currentFingerprint;
```
- **严重程度**: P2
- **现状**: `currentFingerprint` 字段声明在类的第 515 行（文件末尾附近），远离其余字段声明（第 31-53 行）。
- **风险**: 维护者容易遗漏该字段在序列化/克隆/equals 中的处理。
- **建议**: 将 `currentFingerprint` 移至其他字段声明区域。
- **信心水平**: 确定
- **误报排除**: 违反了同类中字段集中声明的惯例，且可能影响代码正确性维护。
- **复核状态**: 未复核

---

## 维度复核结论

| 发现 | 判定 | 说明 |
|------|------|------|
| [维度04-01] NFAState 序列化不兼容 | **降级为 P2（高）** | 问题确认存在（NFAState、ComputationState、EventId、NodeId、DeweyNumber 整个对象图均不可 JSON 反序列化），但 CepOperator Javadoc 已明确标注为未完成功能（"Checkpoint consistency is not guaranteed"），有 LOG.warn 提示。降级原因：这是已知的未完成功能而非隐含 bug。 |
| [维度04-02] StateSnapshot 可变 Map 暴露 | 保留 P2 | 确认 |
| [维度04-03] MemoryKeyedStateBackend 非线程安全 | 保留 P2 | 确认 |
| [维度04-04] CheckpointCoordinator 字段位置异常 | 保留 P2 | 确认 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 04-01 | P2 | NFAState.java / CepOperator.java / MemoryStateSerDe.java | CEP NFAState 整个对象图不可 JSON 反序列化，checkpoint 恢复会失败（已标注未完成功能） |
| 04-02 | P2 | StateSnapshot.java / CompletedCheckpoint.java | 内部可变 Map 直接暴露，未包装为 unmodifiableMap |
| 04-03 | P2 | MemoryKeyedStateBackend.java | states 使用非线程安全 HashMap，与 TaskStateSnapshot 的 ConcurrentHashMap 模式不一致 |
| 04-04 | P2 | CheckpointCoordinator.java | currentFingerprint 字段声明在文件末尾（第 515 行），远离其余字段（第 31-53 行） |
