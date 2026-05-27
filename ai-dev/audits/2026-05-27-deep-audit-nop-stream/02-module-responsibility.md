# 维度 02：模块职责与文件边界

**审计日期**: 2026-05-27

## 超大文件 (> 800 行)

| 文件 | 行数 | 严重程度 |
|------|------|---------|
| `nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java` | 1251 | P1 |
| `nop-stream-cep/.../nfa/compiler/NFACompiler.java` | 1090 | P1 |
| `nop-stream-runtime/.../operators/windowing/WindowOperator.java` | 1088 | P1 |
| `nop-stream-cep/.../nfa/NFA.java` | 969 | P2 |
| `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java` | 804 | P1 |

## 发现

### D02-01: MemoryKeyedStateBackend 1251 行 — 职责过多
- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java`
- **严重程度**: P1
- **现状**: 该类同时包含：状态后端核心逻辑、多种内部 State 实现（ReducingState, ListState, MapState, AggregatingState, ValueState）、序列化逻辑。内部类占比极大。
- **风险**: 修改一种 State 类型可能意外影响其他类型；难以单独测试各 State 实现。
- **建议**: 将各 State 内部类（如 `ReducingStateImpl`, `ListStateImpl`）提取为独立文件或至少独立内部类文件。

### D02-02: NFACompiler 1090 行 — 复杂度过高
- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java`
- **严重程度**: P1
- **现状**: 13 个 `@SuppressWarnings("unchecked")` 注解，大量泛型擦除操作。包含 NFA 构建的完整逻辑，包括 group pattern、optional、times 等多种模式的处理。
- **风险**: 高复杂度增加 bug 引入概率；泛型擦除使得类型错误在编译时无法捕获。
- **建议**: 按构建阶段拆分为 `NFAStateBuilder`、`NFATransitionBuilder`、`NFAGroupHandler` 等类。

### D02-03: WindowOperator 1088 行 — 核心运算器
- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`
- **严重程度**: P2
- **现状**: Window 操作的完整实现，包含窗口生命周期、触发器调用、状态管理、延迟数据处理。10 个 `@SuppressWarnings("unchecked")` 注解。
- **风险**: 复杂度高，但 WindowOperator 作为核心运算器，职责范围合理。
- **建议**: 可考虑将 MergingWindowSet 相关逻辑进一步独立。

### D02-04: GraphModelCheckpointExecutor 804 行 — 多种职责混合
- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`
- **严重程度**: P1
- **现状**: 同时负责：检查点触发、barrier 注入、状态恢复、执行计划构建、任务监控。多个 static 方法。
- **风险**: 检查点恢复逻辑修改可能影响触发逻辑。
- **建议**: 至少将恢复逻辑提取到 `CheckpointRestorer` 类。

### D02-05: 4 个空壳模块无实际代码
- **文件**: nop-stream-api, nop-stream-checkpoint, nop-stream-flink, nop-stream-flow
- **严重程度**: P2
- **现状**: 仅有 pom.xml，无 Java 代码。注释标记为 "placeholder, planned but not implemented"。
- **风险**: 占用 reactor 构建时间。
- **建议**: 与 D01-01 合并处理。

### D02-06: _gen 文件正常
- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/_gen/`
- **严重程度**: —
- **现状**: 4 个 `_gen` 文件（`_CepPatternModel.java`, `_CepPatternPartModel.java`, `_CepPatternGroupModel.java`, `_CepPatternSingleModel.java`），时间戳均为 2026-03-25，未被手动修改。包含标准的 `@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",...})` 注解。
- **结论**: ✅ 合规

### D02-07: 重复的 TypeSerializer 接口
- **文件**: `nop-stream-core/.../common/typeinfo/TypeSerializer.java` vs `nop-stream-core/.../common/typeutils/TypeSerializer.java`
- **严重程度**: P1
- **现状**: 两个包中存在同名 `TypeSerializer` 接口：
  - `typeinfo.TypeSerializer`: 简单接口，只有 `serialize(T)` 和 `deserialize(byte[])`
  - `typeutils.TypeSerializer`: 更完整的接口（来自 Apache Flink），包含 `copy`, `createInstance`, `duplicate` 等
  - 实际使用中，`CepOperator`、`WindowOperator`、`SharedBuffer` 等核心类使用 `typeutils.TypeSerializer`
  - `typeinfo.TypeSerializer` 仅在 `SimpleTypeSerializer` 和部分内部代码中使用
- **风险**: 同名接口容易混淆，导入错误的包不会在编译时报错（接口签名不兼容会在运行时暴露）。
- **建议**: 重命名 `typeinfo.TypeSerializer` 为 `SimpleTypeSerializer` 或 `StreamTypeSerializer` 以消除歧义。

### D02-08: NFA.java 硬编码 "Failure happened in filter function." 
- **文件**: `nop-stream-cep/.../nfa/NFA.java:788,832`
- **严重程度**: P2
- **现状**: 使用 `StreamRuntimeException` 而非 `StreamException`，消息为硬编码英文。
- **风险**: 错误分类不一致。
- **建议**: 改用 `NopException.adapt(e)` 或 `StreamException`。

## 总结

模块职责整体清晰，核心问题是 3 个超大文件（>1000行）和重复的 TypeSerializer 接口。_gen 文件未被手动修改。
