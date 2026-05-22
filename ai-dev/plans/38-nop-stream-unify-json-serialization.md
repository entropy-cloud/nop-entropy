# 38 统一 nop-stream 状态序列化策略为 JsonTool

> Plan Status: planned
> Last Reviewed: 2026-05-22
> Review Round: 2（Round 1: 3 Blocker + 6 Major 已整合；Round 2: 2 Blocker + 4 Major 已整合）
> Source: Plan 37 闭包审计发现的序列化设计债务；`state-management-design.md` §4.2 明确规定用 `JsonTool`
> Related: `ai-dev/plans/37-nop-stream-round3-critical-fixes.md`

## Purpose

设计文档 `state-management-design.md` §1.1 和 §4.2 明确声明 nop-stream 使用 `JsonTool`（JSON 序列化）作为统一的 checkpoint 序列化策略。Nop 平台核心约束：所有内部结构必须支持 JSON 序列化。但实际代码中 `MemoryKeyedStateBackend`、`OperatorSnapshotResult` 的部分方法仍使用 Java `ObjectOutputStream`，导致设计-实现不一致。本计划统一为 JSON 序列化。

## Current Baseline

**设计文档声明**（`state-management-design.md` §4.2）：
> Checkpoint 时状态通过 Nop 平台的 `JsonTool` 序列化为 JSON

**实际代码现状**：

| 组件 | 序列化方式 | 与设计文档一致？ |
|------|-----------|:---:|
| `JdbcCheckpointStorage` | `JsonTool.serialize()` | ✅ |
| `LocalFileCheckpointStorage` | `JsonTool.serialize()` | ✅ |
| `MemoryKeyedStateBackend.snapshotState()` | `ObjectOutputStream.writeObject(this)` | ❌ |
| `OperatorSnapshotResult.putOperatorStateJava()` | `ObjectOutputStream` | ❌ |
| `StreamReduceOperator` | 使用 `putOperatorStateJava()` | ❌ |
| `SimpleStreamOperatorFactory` | `ObjectOutputStream`（对象深拷贝） | 不涉及（非状态序列化） |

**审查发现的 Blocker（Round 1）**：

1. **B-02 快照格式缺少类型元信息**：`HashMap<TypedNamespaceAndKey, T>` 中 T 被类型擦除，JSON 反序列化无法恢复类型。快照必须包含 stateType、valueType、keyType 等元信息。
2. **B-03 StateDescriptor 含 Class<T> 字段**：`Class` 对象不能 JSON 序列化。恢复时需要从快照元信息中获取类型类名，通过 `Class.forName()` 重建。
3. **B-01 Window 子类无无参构造器**：`TimeWindow` 只有 `(long, long)` 构造器，`GlobalWindow` 是私有构造单例。需添加 JSON 序列化支持。

## Goals

- 所有 Window 子类支持 JSON round-trip
- `MemoryKeyedStateBackend.snapshotState()` / `restoreState()` 改用 `JsonTool`，快照包含完整类型元信息
- `OperatorSnapshotResult` 移除 `putOperatorStateJava` / `getOperatorStateJava` 方法
- `StreamReduceOperator` 改用 `putOperatorStateJson` / `getOperatorStateJson`
- 所有状态序列化路径统一为 JSON
- 设计文档补充"所有内部结构必须支持 JSON 序列化"约束，并定义 JSON 快照 schema
- 端到端验证：算子状态 → snapshotState → CheckpointStorage → restoreState → 状态正确恢复

## Non-Goals

- 不引入 `TypeSerializer` 体系 — 设计文档明确排除
- 不修改 `SimpleStreamOperatorFactory` 的 Java 深拷贝 — 非状态序列化（`MemoryKeyedStateBackend` 保留 `Serializable` 接口以兼容此路径）
- 不修改 `ICheckpointStorage` — 已使用 `JsonTool`
- 不修改 `IKeyedStateBackend.snapshotState()` 的 `byte[]` 返回签名 — 接口签名变更影响面大，当前仅在实现层将 `byte[]` 的内容从 Java 序列化改为 JSON。设计文档 §4.2 的"对象接口"描述是目标架构，本计划实现内部 JSON 序列化但不改接口
- 不为所有可能的 namespace 类型提供通用 JSON 支持 — 仅确保 TimeWindow、GlobalWindow、String、VoidNamespace 等已知类型的 round-trip

## Scope

### In Scope

- `nop-stream-core/.../windowing/windows/TimeWindow.java` — 添加 `@JsonCreator`
- `nop-stream-core/.../windowing/windows/GlobalWindow.java` — 添加 JSON 支持
- `nop-stream-core/.../common/state/backend/memory/MemoryKeyedStateBackend.java` — JSON 序列化
- `nop-stream-core/.../checkpoint/OperatorSnapshotResult.java` — 移除 Java 序列化方法
- `nop-stream-core/.../operators/StreamReduceOperator.java` — 改用 JSON
- `ai-dev/design/nop-stream/state-management-design.md` — 更新
- 测试文件：
  - `nop-stream-core/src/test/` — 新增 focused test
  - `nop-stream-runtime/.../TestCheckpointRecovery.java` — 验证兼容
  - `nop-stream-runtime/.../TestE2EWindowOperatorWithCheckpoint.java` — 验证兼容
  - `nop-stream-core/.../TestOperatorSnapshot.java` — 验证兼容
  - `nop-stream-core/.../TestOperatorSnapshotResult.java` — 移除 Java 序列化测试用例（Phase 3）

### Out Of Scope

- `nop-stream-runtime/` 源码 — `CheckpointStorage` 已使用 `JsonTool`
- `SimpleStreamOperatorFactory` — 对象深拷贝，非状态序列化
- CEP 模块、Connector 模块

## Design Decisions

### DD-1: JSON 快照格式（解决 B-02、B-04、B-05）

快照包含两层：元信息层 + 数据层。

```
{
  "keyType": "java.lang.String",
  "states": {
    "stateName": {
      "stateType": "ValueState",          // ValueState | MapState | AppendingState | ListState
      "valueType": "java.lang.Long",      // StateDescriptor.valueType 类名
      "accumulatorType": "...",           // 仅 AppendingState
      "mapKeyTypes": ["java.lang.String", "java.lang.Integer"],  // 仅 MapState
      "entries": [
        {
          "namespace": { "@type": "TimeWindow", "start": 100, "end": 200 },
          "key": "user1",
          "value": 42
        }
      ]
    }
  }
}
```

- `@type` 字段用于 namespace 的多态反序列化
- `entries` 列表替代 `HashMap<TypedNamespaceAndKey, T>`（避免 JSON Map key 限制）
- `valueType` 类名用于 `JsonTool.parseBeanFromText(json, Class.forName(valueType))` 的类型推断

### DD-2: Window 类的 JSON 序列化（解决 B-01）

- `TimeWindow`：添加 `@JsonCreator` + `@JsonProperty` 注解（项目已有先例：`CheckpointPlan.java`）
- `GlobalWindow`：序列化为字符串常量 `"GlobalWindow"`，反序列化时返回 `GlobalWindow.get()` 单例。通过自定义 `JsonTool` 配置或 `@JsonDeserialize` 实现

### DD-3: StateDescriptor 恢复机制（解决 B-03、R2-02）

快照中保存 descriptor 关键信息（stateType、valueType、accumulatorType），不直接序列化 `StateDescriptor` 对象。恢复时直接构建状态实例（**不走 `getState()` → 逐条填充的路径**，因为内部状态类的 `storage` 是 `private final`，无法批量填充）：

1. 从快照读取 stateType + keyType + valueType + accumulatorType 等元信息
2. 用 `Class.forName()` 重建所需 Class 对象
3. 直接构造 `MemoryValueState` / `MemoryMapState` / `MemoryInternalAppendingState` / `MemoryInternalListState` 实例
4. 直接构造对应的 `storage` HashMap，填充快照中的 entries 数据
5. 将构造好的状态实例放入 `states` Map
6. 调用 `rebindStateBackends()` 绑定 backend 引用

注意：因为内部状态类是 `MemoryKeyedStateBackend` 的 `private static` 内部类，恢复代码在 `MemoryKeyedStateBackend.restoreState()` 内部，可以直接访问 `storage` 字段。

### DD-4: MemoryKeyedStateBackend 保留 Serializable

`MemoryKeyedStateBackend` 保留 `Serializable` 接口以兼容 `SimpleStreamOperatorFactory` 的 Java 深拷贝路径。`snapshotState()` / `restoreState()` 改用 JSON，但 `writeObject()` / `readObject()` 路径（用于深拷贝）仍通过 Java 序列化。

## Execution Plan

### Phase 0 - 修复 MapStateDescriptor 类型信息丢失（R2-01）

Status: planned
Targets: `MapStateDescriptor.java`, `nop-stream-core/src/test/`

- Item Types: `Fix`, `Proof`

**前置条件**：`MapStateDescriptor` 构造器丢弃了 `keyClass` 和 `valueClass` 参数，导致 JSON 快照无法提取 MapState 的类型元信息。必须先修复此 bug。

- [ ] `MapStateDescriptor` 保存 `keyClass` 和 `valueClass` 为字段，添加 getter 方法
- [ ] 添加 focused test：验证 `MapStateDescriptor.getKeyClass()` / `getValueClass()` 返回正确类型

Exit Criteria:

- [ ] `MapStateDescriptor` 的构造器正确保存 keyClass 和 valueClass
- [ ] `getKeyClass()` / `getValueClass()` getter 可用
- [ ] 新 focused test 全部通过
- [ ] No owner-doc update required

### Phase 1 - Window 类添加 JSON 序列化支持

Status: planned
Targets: `TimeWindow.java`, `GlobalWindow.java`, `nop-stream-core/src/test/`

- Item Types: `Fix`, `Proof`

- [ ] `TimeWindow` 添加 `@JsonCreator` + `@JsonProperty("start")` + `@JsonProperty("end")`
- [ ] `GlobalWindow` 添加 JSON 序列化支持（序列化为字符串常量或使用自定义处理）
- [ ] 添加 focused test：TimeWindow JSON round-trip、GlobalWindow JSON round-trip

Exit Criteria:

- [ ] `JsonTool.serialize(new TimeWindow(100, 200))` 后 `JsonTool.parseBeanFromText(json, TimeWindow.class)` round-trip 正确
- [ ] `GlobalWindow` JSON round-trip 返回同一单例
- [ ] 新 focused test 全部通过
- [ ] No owner-doc update required（Phase 4 统一更新）

### Phase 2 - 重构 MemoryKeyedStateBackend 序列化为 JSON

Status: planned
Targets: `MemoryKeyedStateBackend.java`, `nop-stream-core/src/test/`

- Item Types: `Fix`, `Proof`

- [ ] 按 DD-1 格式重构 `snapshotState()`：提取元信息 + 数据 → `JsonTool.serialize()` → `byte[]`
- [ ] 按 DD-3 机制重构 `restoreState(byte[])`：解析元信息 → 重建 descriptor → 创建状态实例 → 填充数据 → rebind
- [ ] 添加 `StateSnapshotSchema` 内部类封装快照格式
- [ ] 添加 focused test：4 种状态类型（Value/Map/Appending/List）的 JSON round-trip
- [ ] 验证 `TestOperatorSnapshot` 兼容（nop-stream-core）
- [ ] 验证 `TestCheckpointRecovery` 兼容（nop-stream-runtime）
- [ ] 验证 `TestE2EWindowOperatorWithCheckpoint` 兼容（nop-stream-runtime）

Exit Criteria:

- [ ] `MemoryKeyedStateBackend.snapshotState()` 内部不使用 `ObjectOutputStream`
- [ ] `MemoryKeyedStateBackend.restoreState()` 内部不使用 `ObjectInputStream`
- [ ] 快照 JSON 包含 keyType、stateType、valueType 元信息
- [ ] 所有 4 种内部状态类型的 JSON round-trip 正确
- [ ] 3 个既有测试文件改造后仍通过
- [ ] 无静默跳过：序列化失败时抛异常而非返回空
- [ ] No owner-doc update required（Phase 4 统一更新）

### Phase 3 - 移除 Java 序列化便捷方法，统一使用 JSON

Status: planned
Targets: `OperatorSnapshotResult.java`, `StreamReduceOperator.java`, `nop-stream-core/src/test/`

- Item Types: `Fix`, `Proof`

- [ ] 从 `OperatorSnapshotResult` 移除 `putOperatorStateJava()` / `getOperatorStateJava()` 方法
- [ ] `StreamReduceOperator` 改用 `putOperatorStateJson()` / `getOperatorStateJson()`，序列化时将 `Map<Object, T>` 转为 entries 数组保留 key 类型信息
- [ ] 更新 `TestStreamReduceOperator` 验证 JSON 序列化路径（含非 String key 场景）
- [ ] 全量搜索确认无其他调用点使用 `putOperatorStateJava`

Exit Criteria:

- [ ] `OperatorSnapshotResult` 不包含任何 `ObjectOutputStream` / `ObjectInputStream` 引用
- [ ] `StreamReduceOperator` 使用 `putOperatorStateJson` / `getOperatorStateJson`
- [ ] 全量搜索确认 `putOperatorStateJava` 无调用点
- [ ] `./mvnw test -pl nop-stream -am` 全通过
- [ ] No owner-doc update required（Phase 4 统一更新）

### Phase 4 - 更新设计文档

Status: planned
Targets: `ai-dev/design/nop-stream/state-management-design.md`

- Item Types: `Fix`

- [ ] §1.1 补充 Nop 平台核心约束：所有内部结构必须支持 JSON 序列化
- [ ] §2.3 Namespace 节补充：用作 namespace 的类型必须满足 JsonTool round-trip 要求
- [ ] §4.2 定义 JSON 快照 schema（引用 DD-1 格式）
- [ ] §4.4 更新对比表反映 JSON 序列化已全面实施
- [ ] §8 已知限制：新增 Window 子类必须支持 JSON 序列化才能用作 namespace

Exit Criteria:

- [ ] 设计文档中无"Java 序列化"或 `ObjectOutputStream` 作为状态序列化策略的描述
- [ ] 设计文档包含 JSON 快照 schema 定义
- [ ] 设计文档包含"所有内部结构必须支持 JSON 序列化"约束
- [ ] `ai-dev/logs/` 当日日志已更新

### Phase 5 - 构建验证 + 端到端测试 + 日志更新

Status: planned
Targets: 全模块

- Item Types: `Proof`

- [ ] `./mvnw test -pl nop-stream -am` 全通过
- [ ] 端到端验证：包含 keyed state 的算子经 snapshotState → restoreState 后状态行为正确
- [ ] 更新 `ai-dev/logs/` 当日日志

Exit Criteria:

- [ ] `./mvnw test -pl nop-stream -am` 全通过
- [ ] 端到端测试验证从算子状态到 checkpoint 恢复的完整管线
- [ ] `ai-dev/logs/` 当日日志已更新

## Closure Gates

- [ ] Window 子类支持 JSON round-trip
- [ ] `MemoryKeyedStateBackend` 使用 `JsonTool` 序列化（快照包含类型元信息）
- [ ] `OperatorSnapshotResult` 不含 Java 序列化方法
- [ ] 所有算子状态通过 JSON 序列化
- [ ] 设计文档包含 JSON 快照 schema 和 JSON 序列化约束
- [ ] 无新增空壳实现或静默跳过
- [ ] `./mvnw test -pl nop-stream -am` 全通过（含端到端验证）
- [ ] 独立子 agent closure-audit 已完成

## Risks

1. **JsonTool 对 `Class` 对象的序列化**：`Class<K>` 不能直接 JSON 化。缓解：快照中存储类名字符串，恢复时 `Class.forName()` 重建
2. **向后兼容性**：已有的 Java 序列化 checkpoint 数据无法被新版本读取。缓解：nop-stream 当前尚无生产部署，不存在需要迁移的历史 checkpoint 数据
3. **MapState 嵌套 Map 的 value 类型**：JSON 反序列化默认丢失精确类型（如 Long → Integer）。缓解：使用 `valueType` 元信息 + `JsonTool.parseBeanFromText(json, type)` 精确反序列化
4. **SimpleAccumulator 子类无参构造器**：恢复 AppendingState 时通过 `Class.forName(accumulatorType).getDeclaredConstructor().newInstance()` 重建 accumulator。需确保所有 SimpleAccumulator 子类有无参构造器。缓解：验证现有实现均满足此约束
5. **namespace @type 多态反序列化**：DD-1 使用 `@type` 字段标识 namespace 类型。`JsonTool` 内置不直接支持 `@type` 多态，需在 `snapshotState()` / `restoreState()` 中手动处理 namespace 的序列化和反序列化
