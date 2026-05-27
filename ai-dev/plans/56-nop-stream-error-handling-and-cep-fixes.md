# Plan 56: nop-stream Error Handling Standardization & CEP Correctness Fix

> Plan Status: **completed**
> Created: 2026-05-26
> Parent Goal: nop-stream 模块完善
> Review: Round 1 FAIL (CEP fix approach non-viable), revised

## Purpose

Deep audit round 1 发现 nop-stream 存在两类 P1 问题：错误处理不一致（27 处裸 RuntimeException）和 CEP 公共 API 正确性缺陷（PatternStreamBuilder.inputSerializer=null 导致运行时 `IllegalArgumentException` from `Guard.notNull`）。本 plan 修复这两类问题。

**Cross-plan dependency**: Plan 57（import 排序）必须在本 plan 之后执行，因为本 plan 会引入 `StreamException` import。

## Goals

1. **CEP CepOperator inputSerializer 可空化**：将 `CepOperator.inputSerializer` 从 `Guard.notNull` 改为 `@Nullable`。因为 `SharedBuffer` 构造函数接受 `TypeSerializer` 但从不使用它（dead parameter），`inputSerializer` 目前没有运行时作用。改为可空使公共 API 入口 `CEP.pattern()` → `PatternStream.select()` 不再崩溃
2. **RuntimeException → StreamException 标准化**：将 core/runtime/cep 中 25 处 `throw new RuntimeException(...)` 替换为 `throw new StreamException(...)`（保留 FunctionUtils 私有构造函数和 SharedBuffer 的单独处理）
3. **SharedBuffer 异常处理统一**：将 SharedBuffer.java 两处 `throw new RuntimeException(exception)` 替换为 `NopException.adapt(exception)`（与同类方法 lines 304/327 一致）
4. **Connector 参数校验标准化**：将 connector 模块中 11 处 `IllegalArgumentException` 替换为 `StreamException`
5. **FunctionUtils 私有构造函数修正**：`throw new RuntimeException()` → `throw new UnsupportedOperationException("Utility class")`

## Non-Goals

- 不改动静默吞异常的 catch 块（需要逐案分析是否为有意的 shutdown 清理逻辑）
- 不引入新的 ErrorCode 定义（`StreamException` 作为域异常已足够）
- 不改动 import 排序（Plan 57 负责）
- 不改动 core/runtime/cep 之外的 `IllegalArgumentException`（其他模块如 fraud-example 的 IAE 是合理的参数校验）

## Current Baseline

- 构建通过：`./mvnw clean install -pl nop-stream -am -T 1C` ✅
- 测试通过：305 tests, 0 failures ✅
- `PatternStreamBuilder.inputSerializer = null`（line 139），`CepOperator` 构造函数 `Guard.notNull(inputSerializer)` 会抛 `IllegalArgumentException("IsNull:inputSerializer")`
- `SharedBuffer` 接受 `TypeSerializer<V> valueSerializer` 参数但从未存储或使用它
- `TypeInformation` 接口只有 `getTypeClass()`，没有 `createSerializer()`
- 27 处 `throw new RuntimeException(...)` 分布在 17 个源文件中（core: 21, runtime: 1, cep: 2, util: 1, connector: 0+2cep）
- SharedBuffer.java 两处 `throw new RuntimeException(exception)`（lines 161, 193）
- connector 模块 11 处 `throw new IllegalArgumentException(...)`
- `FunctionUtils.java:69` 私有构造函数 `throw new RuntimeException()`

## Exit Criteria

- [x] `CepOperator.inputSerializer` 字段标记 `@Nullable`，`Guard.notNull` 移除，`SharedBuffer` 构造函数接受 null
- [x] 新增测试验证 `CEP.pattern(stream, pattern).select(fn)` 端到端可用
- [x] core 和 runtime 源码中 0 处 `throw new RuntimeException`（grep 验证，不含 SharedBuffer 两处和 FunctionUtils）
- [x] SharedBuffer.java 中 2 处 `throw new RuntimeException(exception)` 改为 `NopException.adapt(exception)`
- [x] connector 源码中 0 处 `throw new IllegalArgumentException`（grep 验证）
- [x] `FunctionUtils` 私有构造函数抛 `UnsupportedOperationException`
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全量通过

## Execution

### Slice 1: CEP CepOperator 可空化修复

- [x] 1.1 修改 `CepOperator.java`：inputSerializer 字段加 `@Nullable`，移除 `Guard.notNull`，改为直接赋值
- [x] 1.2 新增 `TestCepPatternStreamE2E` 测试，验证从 `CEP.pattern()` 到 pattern 匹配输出的端到端路径
- [x] 1.3 运行 `./mvnw test -pl nop-stream/nop-stream-cep -am`

### Slice 2: Error Handling 标准化 (core + runtime + cep + connector)

- [x] 2.1 替换 core 中所有 `throw new RuntimeException(...)` 为 `throw new StreamException(...)`
- [x] 2.2 替换 runtime 中 `throw new RuntimeException(...)` 为 `throw new StreamException(...)`
- [x] 2.3 替换 SharedBuffer.java 两处 `throw new RuntimeException(exception)` 为 `NopException.adapt(exception)`
- [x] 2.4 替换 connector 中所有 `throw new IllegalArgumentException(...)` 为 `throw new StreamException(...)`
- [x] 2.5 修复 `FunctionUtils` 私有构造函数
- [x] 2.6 运行 `./mvnw test -pl nop-stream -am -T 1C`

## Closure Gates

- [x] 所有 Exit Criteria 逐条通过
- [x] `rg "throw new RuntimeException" nop-stream/nop-stream-core/src/main/java/ nop-stream/nop-stream-runtime/src/main/java/` 返回 0 结果
- [x] `rg "throw new RuntimeException" nop-stream/nop-stream-cep/src/main/java/` 仅剩 SharedBuffer 以外的结果（SharedBuffer 改用 NopException.adapt）
- [x] `rg "throw new IllegalArgumentException" nop-stream/nop-stream-connector/src/main/java/` 返回 0 结果
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全量通过
- [x] daily log updated
