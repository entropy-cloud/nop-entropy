# 38 统一 nop-stream 状态序列化策略为 JsonTool

> Plan Status: planned
> Last Reviewed: 2026-05-22
> Source: Plan 37 闭包审计发现的序列化设计债务；`state-management-design.md` §4.2 明确规定用 `JsonTool`
> Related: `ai-dev/plans/37-nop-stream-round3-critical-fixes.md`

## Purpose

设计文档 `state-management-design.md` §1.1 和 §4.2 明确声明 nop-stream 使用 `JsonTool`（JSON 序列化）作为统一的 checkpoint 序列化策略。但实际代码中 `MemoryKeyedStateBackend`、`OperatorSnapshotResult` 的部分方法仍使用 Java `ObjectOutputStream`，导致设计-实现不一致。本计划统一为 JSON 序列化，并在 `OperatorSnapshotResult` 上提供对象级便捷方法屏蔽序列化细节。

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

**核心障碍**（`MemoryKeyedStateBackend` 改为 JSON 的难点）：
1. `TypedNamespaceAndKey` 的 `namespace` 字段是 `Object` 类型 — Window 对象可能不可 JSON 序列化
2. 内部状态类（`MemoryValueState` 等）有 `backend` 回引用 — `transient` 语义 Java 序列化支持但 JSON 不支持
3. 状态值 `T` 是泛型 — JSON 反序列化需要类型信息

## Goals

- `MemoryKeyedStateBackend.snapshotState()` / `restoreState()` 改用 `JsonTool`
- `OperatorSnapshotResult` 移除 `putOperatorStateJava` / `getOperatorStateJava` 方法
- `StreamReduceOperator` 改用 `putOperatorStateJson` / `getOperatorStateJson`
- 所有状态序列化路径统一为 JSON
- 更新设计文档准确反映实现

## Non-Goals

- 不引入 `TypeSerializer` 体系 — 设计文档明确排除
- 不修改 `SimpleStreamOperatorFactory` 的 Java 深拷贝 — 非状态序列化
- 不修改 `ICheckpointStorage` — 已使用 `JsonTool`
- 不处理 `Window` 对象的泛型 namespace 序列化问题 — 仅确保已知使用路径正确工作

## Scope

### In Scope

- `nop-stream-core/.../common/state/backend/memory/MemoryKeyedStateBackend.java`
- `nop-stream-core/.../checkpoint/OperatorSnapshotResult.java`
- `nop-stream-core/.../operators/StreamReduceOperator.java`
- `ai-dev/design/nop-stream/state-management-design.md`
- 对应模块的测试文件

### Out Of Scope

- `nop-stream-runtime/` — `CheckpointStorage` 已使用 `JsonTool`
- `SimpleStreamOperatorFactory` — 对象深拷贝，非状态序列化
- CEP 模块、Connector 模块

## Execution Plan

### Phase 1 - 重构 MemoryKeyedStateBackend 序列化为 JSON

Status: planned
Targets: `MemoryKeyedStateBackend.java`, `nop-stream-core/src/test/`

- Item Types: `Fix`, `Proof`

**设计决策**：不序列化整个 backend 对象（含 `transient` 回引用和不可 JSON 化的内部结构），而是提取纯数据映射后 JSON 序列化。

快照格式：
- 将 `states` map 序列化为 `{stateName: [{namespace:..., key:..., value:...}, ...]}` 的 JSON 结构
- `TypedNamespaceAndKey` 的 `namespace` 和 `key` 需要可 JSON 序列化（当前已知使用场景中 key 是 String/Integer，namespace 是 String 或 TimeWindow/EventTimeSessionWindow 等 `Window` 子类）
- 反序列化时重建状态实例并 rebind

- [ ] 重构 `snapshotState()` 提取纯数据 → `JsonTool.serialize()` → `byte[]`
- [ ] 重构 `restoreState(byte[])` 从 JSON 反序列化 → 重建状态实例 → rebind
- [ ] 为 `Window` 类添加 JSON 序列化支持（确保 TimeWindow 等可正确 round-trip）
- [ ] 添加 focused test：ValueState、MapState、AppendingState、ListState 的 JSON 序列化 round-trip
- [ ] 添加 focused test：keyed state 经 checkpoint 存储后正确恢复

Exit Criteria:

- [ ] `MemoryKeyedStateBackend.snapshotState()` 内部不使用 `ObjectOutputStream`
- [ ] `MemoryKeyedStateBackend.restoreState()` 内部不使用 `ObjectInputStream`
- [ ] 所有内部状态类型（Value/Map/Appending/List）的 JSON round-trip 正确
- [ ] 新 focused test 全部通过
- [ ] 无静默跳过：序列化失败时抛异常而非返回空
- [ ] No owner-doc update required（Phase 3 统一更新）

### Phase 2 - 移除 Java 序列化便捷方法，统一使用 JSON

Status: planned
Targets: `OperatorSnapshotResult.java`, `StreamReduceOperator.java`, `nop-stream-core/src/test/`

- Item Types: `Fix`, `Proof`

- [ ] 从 `OperatorSnapshotResult` 移除 `putOperatorStateJava()` / `getOperatorStateJava()` 方法
- [ ] `StreamReduceOperator` 改用 `putOperatorStateJson()` / `getOperatorStateJson()`
- [ ] 更新 `TestStreamReduceOperator` 验证 JSON 序列化路径
- [ ] 搜索确认无其他调用点使用 `putOperatorStateJava`

Exit Criteria:

- [ ] `OperatorSnapshotResult` 不包含任何 `ObjectOutputStream` / `ObjectInputStream` 引用
- [ ] `StreamReduceOperator` 使用 `putOperatorStateJson` / `getOperatorStateJson`
- [ ] 全量搜索确认 `putOperatorStateJava` 无调用点
- [ ] `./mvnw test -pl nop-stream -am` 全通过
- [ ] No owner-doc update required（Phase 3 统一更新）

### Phase 3 - 更新设计文档

Status: planned
Targets: `ai-dev/design/nop-stream/state-management-design.md`

- Item Types: `Fix`

- [ ] 更新 §4.2 明确说明 JSON 序列化的具体格式（状态数据提取 → JSON → byte[]）
- [ ] 更新 §4.4 反映 JSON 序列化已全面实施
- [ ] 在 §8 已知限制中添加：namespace 对象必须可 JSON 序列化的约束

Exit Criteria:

- [ ] 设计文档中无"Java 序列化"或 `ObjectOutputStream` 作为状态序列化策略的描述
- [ ] 设计文档准确反映代码实现的序列化格式
- [ ] `ai-dev/logs/` 当日日志已更新

### Phase 4 - 构建验证 + 日志更新

Status: planned
Targets: 全模块

- Item Types: `Proof`

- [ ] `./mvnw test -pl nop-stream -am` 全通过
- [ ] 更新 `ai-dev/logs/` 当日日志

Exit Criteria:

- [ ] `./mvnw test -pl nop-stream -am` 全通过
- [ ] `ai-dev/logs/` 当日日志已更新

## Closure Gates

- [ ] `MemoryKeyedStateBackend` 使用 `JsonTool` 序列化（无 `ObjectOutputStream`）
- [ ] `OperatorSnapshotResult` 不含 Java 序列化方法
- [ ] 所有算子状态通过 JSON 序列化
- [ ] 设计文档与代码实现一致
- [ ] 无新增空壳实现或静默跳过
- [ ] `./mvnw test -pl nop-stream -am` 全通过
- [ ] 独立子 agent closure-audit 已完成

## Risks

1. **Window namespace 的 JSON 序列化**：如果 `Window` 子类（`TimeWindow`、`EventTimeSessionWindow`）没有无参构造器或不可 JSON 化，Phase 1 会阻塞。缓解：检查 Window 类的实际结构，必要时添加 JSON 注解或自定义序列化器
2. **泛型类型擦除**：`HashMap<TypedNamespaceAndKey, T>` 中的 `T` 在运行时类型擦除，反序列化时需要从 `StateDescriptor.valueType` 获取类型信息。缓解：快照时保存 `valueType` 类名
3. **向后兼容性**：已有的 Java 序列化 checkpoint 数据无法被新版本读取。缓解：nop-stream 当前尚无生产部署，不存在需要迁移的历史 checkpoint 数据
