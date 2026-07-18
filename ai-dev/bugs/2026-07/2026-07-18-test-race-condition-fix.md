# 2026-07-18 测试竞态条件修复

## Problem

三个测试因竞态条件不稳定，偶现失败：

1. `TestDrainableSourceSupport.testDrainableSourceTruncateStopsConsuming` — `assertFalse(collected.isEmpty())` 失败，collected 为空。
2. `TestCheckpointCoordinator.testSchedulerStartStop` — `assertTrue(coord.getNumberOfPendingCheckpoints() >= 1)` 失败，pending checkpoint 为 0。
3. `TestDebeziumCdcSourceFunction` / `TestConnectorResourceManagement` — `DebeziumEngineConfig.buildProperties` 抛出 `NullPointerException`。

## Diagnostic Method

- 前两个测试都使用固定 `Thread.sleep(N)` 等待异步线程完成工作，但 sleep 结束不能保证目标状态已达到。
- 第三个问题的 NPE 堆栈直指 `DebeziumEngineConfig.java:41` → `Properties.setProperty("database.user", null)`。测试只设了 `name`/`connectorType`/`databaseHost`，没设 `databaseUser`。
- 第三问题虽然是 NPE，但根因是 `buildProperties` 对所有 `DebeziumConfig` 字段假设非 null，而单元测试构造的 config 仅填充了必要字段。

## Root Cause

1. **`TestDrainableSourceSupport`**：主线程 sleep(100ms) 后调 `truncateForDrain()`，但 source 线程可能还没开始循环 collect。`draining=true` 导致 while 循环一次都不执行。
2. **`TestCheckpointCoordinator.testSchedulerStartStop`**：主线程 sleep(200ms) 后断言有待处理 checkpoint，但调度器线程可能还没触发第一次 checkpoint。
3. **`DebeziumEngineConfig.buildProperties`**：`props.setProperty("database.user", config.getDatabaseUser())` — `databaseUser` 为 null 时 `Properties.setProperty` 抛 NPE。

## Fix

1. **`TestDrainableSourceSupport.java`**：用带 1 分钟超时的 `while (collected.isEmpty())` 忙等替代固定 sleep，确保 source 线程已开始生产数据。
2. **`TestCheckpointCoordinator.java`**：同样用带 1 分钟超时的 `while (getNumberOfPendingCheckpoints() < 1)` 忙等替代固定 sleep。
3. **`DebeziumEngineConfig.java`**：对 `databaseUser` 和 `databasePassword` 加 null 检查，只在非 null 时 set。

## Tests

- `TestDrainableSourceSupport.testDrainableSourceTruncateStopsConsuming` — 验证 drain 后 source 线程退出，且 drain 前有数据被 collect。
- `TestCheckpointCoordinator.testSchedulerStartStop` — 验证调度器启动后能触发至少一个 checkpoint。
- `TestDebeziumCdcSourceFunction` (10 tests), `TestConnectorResourceManagement` (3 tests) — 全部通过，不再报 NPE。

## Affected Files

- `nop-stream/nop-stream-connector/src/test/java/io/nop/stream/connector/TestDrainableSourceSupport.java`
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestCheckpointCoordinator.java`
- `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineConfig.java`

## Notes For Future Refactors

- 涉及异步线程的测试应优先使用忙等 + 超时模式，而非固定 sleep。
- `DebeziumEngineConfig.buildProperties` 应对所有可空字段做 null 保护，或由 builder 确保必填字段非 null。
