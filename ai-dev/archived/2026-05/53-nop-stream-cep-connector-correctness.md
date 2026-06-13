# Plan 53: nop-stream CEP + Connector Correctness Fixes

> Plan ID: 53
> Status: completed
> Created: 2026-05-26
> Parent Goal: 完善 nop-stream 模块

## Goals

1. **CepOperator 对接 IKeyedStateBackend**：替换自建 `SimpleKeyedStateStore`，使用 `AbstractStreamOperator.keyedStateBackend` 提供的标准状态后端，使 CEP 状态可被 checkpoint 快照和恢复。
2. **BatchConsumerSinkFunction Bug N53 修复**：`flush()` 在 `consumer.consume()` 失败时不清空 buffer，导致重复处理。
3. **runtime → cep 幽灵依赖移除**：runtime pom 声明了 cep 依赖但无代码引用。

## Non-Goals

- DslModelParser 加载 .pattern.xml（后续 plan）
- TwoPhaseCommitSinkFunction（后续 plan）
- fraud-example 修复（后续 plan）
- BatchLoaderSourceFunction checkpoint restore（后续 plan）

## Current Baseline

- 305 测试全通过，10 子模块编译成功（2026-05-26 00:13）
- CepOperator 使用 `new SimpleKeyedStateStore()` 而非 `getKeyedStateBackend()`，CEP 状态无法被 checkpoint 持久化
- BatchConsumerSinkFunction.flush() 在 consume() 抛异常时不执行 buffer.clear()
- nop-stream-runtime/pom.xml 声明 nop-stream-cep 依赖但无代码引用

## Execution Slice

### Slice 1: CepOperator IKeyedStateBackend 对接 + Checkpoint 支持

- [x] 修改 CepOperator.open()：用 `getKeyedStateBackend()` 替代 `new SimpleKeyedStateStore()`
- [x] 修改 CepOperator：通过 IKeyedStateBackend 自动获得 snapshotState 支持（父类 AbstractStreamOperator 已实现）
- [x] 更新 CepRuntimeContext 使用 KeyedStateStore 接口（不再绑定 SimpleKeyedStateStore）
- [x] 更新现有测试适配新接口
- [x] 添加 CEP 状态 checkpoint/restore 端到端测试（已有 TestCepOperatorStateRecovery）
- [x] `./mvnw test -pl nop-stream/nop-stream-cep -am` 通过

### Slice 2: BatchConsumerSinkFunction Bug N53 修复

- [x] 修改 flush()：在 try-finally 中保证 buffer.clear() 执行
- [x] 重写并启用 TestBatchConsumerSinkFunctionFailure 测试
- [x] `./mvnw test -pl nop-stream/nop-stream-connector -am` 通过

### Slice 3: runtime → cep 幽灵依赖移除

- [x] 从 nop-stream-runtime/pom.xml 移除 nop-stream-cep 依赖
- [x] 验证编译和测试通过：`./mvnw test -pl nop-stream/nop-stream-runtime -am`

## Exit Criteria

1. CepOperator 使用 IKeyedStateBackend（不是 SimpleKeyedStateStore）
2. CepOperator 的 keyed state 可被 checkpoint 快照和恢复
3. BatchConsumerSinkFunction.flush() 在 consume() 失败时不再重复发送已处理记录
4. TestBatchConsumerSinkFunctionFailure 测试启用并通过
5. nop-stream-runtime 不再依赖 nop-stream-cep
6. `./mvnw test -pl nop-stream -am -T 1C` 全量通过

## Closure Gates

- [x] 所有 3 个 slice 的 exit criteria 满足
- [x] 全量构建和测试通过
- [x] Anti-hollow 检查：CepOperator 在 stateBackend 可用时创建 IKeyedStateBackend，自动参与 checkpoint
- [x] Daily log 更新

Closure Audit Evidence (retroactive):

- Reviewer / Agent: Retrospective code audit via git history
- Evidence: All checklist items confirmed complete. Plan status verified consistent with codebase state.
