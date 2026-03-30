# nop-stream StateBackend 技术设计文档

> 注：本文档包含目标设计项。当前代码已实现内存状态后端，
> Redis 状态后端仍处于规划阶段。

## 1. 概述

### 1.1 目标
为 nop-stream 实现一个简化的 StateBackend 机制，支持：
- **内存实现**：用于测试和简单场景
- **Redis 实现（规划）**：用于生产环境，支持持久化和故障恢复

### 1.2 设计原则
- **简化优先**：相比 Flink，去除 key-group 分区、Checkpoint 机制、TypeSerializer 抽象
- **固定 JSON 序列化**：统一使用 JSON 进行状态序列化
- **Redis Hash 存储**：使用 HSET/HGET 命令，namespace 为 key，状态名为 field
- **依赖 Redis 持久化（规划）**：不需要额外的 Checkpoint 机制

### 1.3 与 Flink 对比

| 特性 | Flink | nop-stream |
|------|-------|------------|
| StateBackend 层次 | StateBackend → KeyedStateBackend → StateTable | IStateBackend → IKeyedStateBackend |
| Key 分区 | key-group 分区（分布式） | 单 key 上下文（单机） |
| 序列化 | TypeSerializer（可配置） | JSON（固定） |
| 持久化 | Checkpoint 机制 | Redis 持久化 |
| Namespace | 支持（用于 Window） | 支持（用于 Window） |
| 状态类型 | Value/List/Map/Reducing/Aggregating | Value/Map/List（简化版） |

---

## 2. 核心接口设计

### 2.1 IStateBackend（状态后端工厂）

```java
/**
 * 状态后端接口，用于创建 KeyedStateBackend
 * 简化版本：不需要 Checkpoint 相关方法
 */
public interface IStateBackend {
    /**
     * 创建 KeyedStateBackend
     * @param keySerializer key 的序列化器（用于 JSON 序列化）
     */
    <K> IKeyedStateBackend<K> createKeyedStateBackend(Class<K> keyType);
}
```

### 2.2 IKeyedStateBackend（Keyed 状态管理）

```java
/**
 * Keyed 状态后端接口
 * 管理按 key 分区的状态
 */
public interface IKeyedStateBackend<K> extends KeyedStateStore {
    
    /**
     * 设置当前处理的 key
     * 所有后续的状态操作都针对这个 key
     */
    void setCurrentKey(K key);
    
    /**
     * 获取当前 key
     */
    K getCurrentKey();
    
    /**
     * 设置当前 namespace
     * 用于区分同一 key 下的不同状态（如不同的 Window）
     */
    void setCurrentNamespace(String namespace);
    
    /**
     * 获取当前 namespace
     */
    String getCurrentNamespace();
    
    /**
     * 关闭状态后端，释放资源
     */
    void close();
}
```

### 2.3 KeyedStateStore（状态存储接口）

**复用现有接口**，位于 `io.nop.stream.core.common.state.KeyedStateStore`

```java
public interface KeyedStateStore {
    <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties);
    <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties);
    // 可选：ListState
}
```

---

## 3. 状态类型设计

### 3.1 ValueState（值状态）

**接口**：复用 `io.nop.stream.core.common.state.ValueState`

```java
public interface ValueState<T> extends State {
    T value() throws IOException;
    void update(T value) throws IOException;
    void clear();
}
```

**实现思路**：
- **MemoryValueState**：内部使用 `Map<NamespaceAndKey, T>` 存储
- **RedisValueState**：使用 Redis Hash，key = `stateName:namespace`，field = `key`，value = JSON 序列化的值

### 3.2 MapState（映射状态）

**接口**：复用 `io.nop.stream.core.common.state.MapState`

```java
public interface MapState<UK, UV> extends State {
    UV get(UK key);
    void put(UK key, UV value);
    void putAll(Map<UK, UV> map);
    void remove(UK key);
    boolean contains(UK key);
    Iterable<Map.Entry<UK, UV>> entries();
    Iterable<UK> keys();
    Iterable<UV> values();
    Iterator<Map.Entry<UK, UV>> iterator();
    boolean isEmpty();
    void clear();
}
```

**实现思路**：
- **MemoryMapState**：内部使用 `Map<NamespaceAndKey, Map<UK, UV>>` 存储
- **RedisMapState**：
  - key = `stateName:namespace:key`
  - 使用 Redis Hash，field = `userKey`，value = JSON 序列化的值
  - 或者：整个 Map 序列化为 JSON 存储在单个 field

### 3.3 ListState（列表状态）- 可选

**简化设计**：使用 `ValueState<List<T>>` 实现

---

## 4. 实现设计

### 4.1 MemoryStateBackend（内存实现）

```
IStateBackend
    └── MemoryStateBackend
            └── MemoryKeyedStateBackend<K>
                    ├── currentKey: K
                    ├── currentNamespace: String
                    └── states: Map<String, State>  // stateName -> State
                            ├── MemoryValueState<T>
                            │       └── storage: Map<NamespaceAndKey, T>
                            └── MemoryMapState<UK, UV>
                                    └── storage: Map<NamespaceAndKey, Map<UK, UV>>
```

**NamespaceAndKey**：
```java
class NamespaceAndKey {
    String namespace;
    Object key;
    // equals/hashCode based on both
}
```

### 4.2 RedisStateBackend（Redis 实现）

```
IStateBackend
    └── RedisStateBackend
            ├── redisClient: IRedisClient (Nop 平台的 Redis 抽象)
            ├── keyPrefix: String  // 用于区分不同的作业，如 "stream:job1:"
            └── createKeyedStateBackend()
                    └── RedisKeyedStateBackend<K>
                            ├── currentKey: K
                            ├── currentNamespace: String
                            ├── stateNameSerializer: (stateName) -> redisKey
                            └── redisClient: IRedisClient
                                    ├── RedisValueState<T>
                                    │       └── redisKey: "stream:job1:stateName:namespace"
                                    │       └── field: key (JSON serialized)
                                    └── RedisMapState<UK, UV>
                                            └── Option A: 整个 Map 存储在单个 field
                                            └── Option B: 每个用户 key 一个 field
```

**Redis Key 设计**：
- **ValueState**: `HSET stream:job1:valueState:namespace key1 "jsonValue" key2 "jsonValue"`
- **MapState**: `HSET stream:job1:mapState:namespace:key1 userKey1 "jsonValue" userKey2 "jsonValue"`

### 4.3 JSON 序列化

**使用 Nop 平台的 JSON 工具**：
```java
// 序列化
String json = JsonTool.serialize(value);

// 反序列化
T value = JsonTool.deserialize(json, valueType);
```

---

## 5. 集成 WindowOperator

### 5.1 当前 WindowOperator 状态

**被注释的代码**（`WindowOperator.java`）：
- `windowState` - 窗口状态
- `mergingSetsState` - 合并窗口集合状态
- `InternalTimerService` - 定时器服务

### 5.2 恢复策略

1. **注入 IStateBackend**：
   ```java
   public class WindowOperator<K, IN, OUT> {
       private final IStateBackend stateBackend;
       private IKeyedStateBackend<K> keyedStateBackend;
       
       @Override
       public void open() {
           this.keyedStateBackend = stateBackend.createKeyedStateBackend(keyType);
       }
   }
   ```

2. **使用 KeyedStateStore 获取状态**：
   ```java
   // 原来被注释的代码
   // InternalAppendingState<K, W, IN> windowState;
   
   // 新实现
   private ValueState<IN> windowState;  // 简化为 ValueState
   
   @Override
   public void open() {
       ValueStateDescriptor<IN> descriptor = 
           new ValueStateDescriptor<>("window-contents", inType);
       this.windowState = keyedStateBackend.getState(descriptor);
   }
   ```

3. **setCurrentKey 集成**：
   ```java
   @Override
   public void processElement(StreamRecord<IN> record) {
       K key = keySelector.getKey(record.getValue());
       keyedStateBackend.setCurrentKey(key);  // 设置当前 key
       keyedStateBackend.setCurrentNamespace(window.toString());  // 设置 namespace 为 window
       
       // 后续操作自动针对当前 key
       IN currentValue = windowState.value();
       // ...
   }
   ```

---

## 6. 使用示例

### 6.1 内存实现示例

```java
// 创建 StateBackend
IStateBackend stateBackend = new MemoryStateBackend();

// 创建 KeyedStateBackend
IKeyedStateBackend<String> keyedBackend = 
    stateBackend.createKeyedStateBackend(String.class);

// 设置当前 key
keyedBackend.setCurrentKey("user123");
keyedBackend.setCurrentNamespace("window-1h");

// 获取状态
ValueStateDescriptor<Long> descriptor = 
    new ValueStateDescriptor<>("transactionSum", Long.class);
ValueState<Long> sumState = keyedBackend.getState(descriptor);

// 使用状态
Long currentSum = sumState.value();
sumState.update(currentSum + 100);
```

### 6.2 Redis 实现示例

```java
// 创建 Redis 客户端
IRedisClient redisClient = ...; // Nop 平台的 Redis 客户端

// 创建 StateBackend
IStateBackend stateBackend = 
    new RedisStateBackend(redisClient, "stream:fraud-detection:");

// 创建 KeyedStateBackend
IKeyedStateBackend<String> keyedBackend = 
    stateBackend.createKeyedStateBackend(String.class);

// 后续使用与内存实现完全相同
keyedBackend.setCurrentKey("user123");
keyedBackend.setCurrentNamespace("window-1h");
ValueState<Long> sumState = keyedBackend.getState(
    new ValueStateDescriptor<>("transactionSum", Long.class));

Long currentSum = sumState.value();
sumState.update(currentSum + 100);
```

---

## 7. 测试策略

### 7.1 单元测试

**测试覆盖**：
1. `MemoryStateBackend` 基本功能
   - 创建 ValueState
   - 创建 MapState
   - key 切换
   - namespace 切换

2. `RedisStateBackend` 基本功能
   - 使用 Embedded Redis 进行测试
   - ValueState CRUD
   - MapState CRUD
   - JSON 序列化/反序列化
   - 持久化验证（重启后恢复）

3. 综合测试
   - 多 key 并发访问
   - 多 namespace 隔离
   - 状态清理

### 7.2 集成测试

1. **WindowOperator 集成**
   - 使用 MemoryStateBackend
   - 时间窗口聚合
   - Count 触发

2. **CEP 集成**
   - CEP + StateBackend
   - 模式匹配 + 状态查询

---

## 8. 包结构

```
nop-stream-core/
└── src/main/java/io/nop/stream/core/common/state/
    ├── IStateBackend.java (新增)
    ├── IKeyedStateBackend.java (新增)
    ├── KeyedStateStore.java (已存在)
    ├── ValueState.java (已存在)
    ├── MapState.java (已存在)
    ├── State.java (已存在)
    ├── ValueStateDescriptor.java (已存在)
    ├── MapStateDescriptor.java (已存在)
    └── simple/
        └── SimpleKeyedStateStore.java (修改：支持 key 上下文)

nop-stream-core/
└── src/main/java/io/nop/stream/core/common/state/memory/
    ├── MemoryStateBackend.java (新增)
    └── MemoryKeyedStateBackend.java (新增)

nop-stream-core/
└── src/main/java/io/nop/stream/core/common/state/redis/
    ├── RedisStateBackend.java (新增)
    └── RedisKeyedStateBackend.java (新增)
```

---

## 9. 实施计划

### Phase 1: StateBackend 核心实现（1-2天）
- [x] 定义 IStateBackend 接口
- [ ] 定义 IKeyedStateBackend 接口
- [ ] 实现 MemoryStateBackend
- [ ] 实现 RedisStateBackend
- [ ] 单元测试

### Phase 2: WindowOperator 集成（1天）
- [ ] 恢复 WindowOperator 中被注释的 State 代码
- [ ] 集成 StateBackend
- [ ] 实现时间窗口聚合示例

### Phase 3: CEP 集成（1天）
- [ ] CEP 与 Stream API 集成
- [ ] CEP 与 StateBackend 集成

### Phase 4: 完整示例（1天）
- [ ] 欺诈检测示例
- [ ] 文档和演示

---

## 10. 风险和挑战

### 10.1 技术风险
1. **Redis 性能**：高并发下 Redis 可能成为瓶颈
   - **缓解**：支持 pipeline 命令，批量操作

2. **JSON 序列化性能**：频繁序列化可能影响性能
   - **缓解**：对于简单类型（Long, String），考虑直接存储

3. **内存管理**：MemoryStateBackend 可能内存泄漏
   - **缓解**：提供 clearAll() 方法，定期清理

### 10.2 兼容性风险
1. **Flink API 兼容**：简化后的 API 与 Flink 不兼容
   - **决策**：接受，优先简单性

2. **现有代码影响**：需要修改 WindowOperator
   - **缓解**：保持原有接口，只修改实现

---

## 11. 参考资料

### 11.1 Flink StateBackend 源码分析

**核心接口层次**：
```
StateBackend (工厂接口)
    └── KeyedStateBackend<K> (状态管理接口)
            └── AbstractKeyedStateBackend<K> (基础抽象类)
                    └── HeapKeyedStateBackend<K> (内存实现)
                              └── StateTable<K,N,S> (核心存储)
                                        └── StateMap[] (按key-group分区)
```

**关键设计要点**：
1. **StateBackend** 是轻量级工厂类，只包含配置信息
2. **KeyedStateBackend** 使用 `setCurrentKey()` 设置当前处理的 key
3. **StateTable** 按 key-group 分区，支持分布式扩展
4. **状态类型** 通过工厂模式创建（HeapValueState::create, HeapMapState::create）
5. **序列化** 使用 TypeSerializer，支持 schema evolution

### 11.2 Flink 源码位置

- StateBackend 接口：`flink-runtime/src/main/java/org/apache/flink/runtime/state/StateBackend.java`
- KeyedStateBackend 接口：`flink-runtime/src/main/java/org/apache/flink/runtime/state/KeyedStateBackend.java`
- HeapKeyedStateBackend：`flink-runtime/src/main/java/org/apache/flink/runtime/state/heap/HeapKeyedStateBackend.java`
- StateTable：`flink-runtime/src/main/java/org/apache/flink/runtime/state/heap/StateTable.java`
- HeapValueState：`flink-runtime/src/main/java/org/apache/flink/runtime/state/heap/HeapValueState.java`
- HeapMapState：`flink-runtime/src/main/java/org/apache/flink/runtime/state/heap/HeapMapState.java`

---

*文档创建时间：2026-03-03*
*基于 Flink 1.x 源码分析*
