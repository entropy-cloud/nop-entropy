# 2026-10-02 nop-stream 2PC commit-key 全部归属单 subtask（缺陷嫌疑，未修复）

> 状态：**发现待修**——本条为 WI0 基线（plan 2292）测试暴露的确定性失败，按 roadmap 硬边界记入 bugs/ 通道；修复需独立立项，不在测试 roadmap WI 内夹带。

## Problem

- `nop-stream-fraud-example` 的 `TestParallel2PcJdbcE2E` 失败（全仓 reactor 构建与单独复跑均失败，非 flaky）。
- 断言：JDBC exactly-once sink 的提交台账必须包含「同一 epoch 由 ≥2 个不同 subtask id 提交」的证据；实际 ledger 10 个分区的提交全部归属 subtask-1（`{0=[1], 1=[1], ..., 9=[1]}`）。
- 位置：`nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestParallel2PcJdbcE2E.java`（断言处）；被测面为 `nop-stream-connector-jdbc` 的两阶段提交 exactly-once sink 的 commit-key 归属语义。

## Diagnostic Method

- WI0 全仓基线 run（2026-10-02，`-Pcoverage test -T 1C -fae`）发现该模块测试失败；surefire 报告显示 120 个测试中仅此 1 个 failure。
- 排除 flaky 假设：`mvnq -- jacoco:prepare-agent test -pl :nop-stream-fraud-example -Dtest=TestParallel2PcJdbcE2E` 单独复跑，同样失败（`Tests run: 1, Failures: 1`，4.9s）。
- 未再深挖实现（超出 WI0 边界）：commit-key 的生成/归集路径（connector-jdbc 的 recoverable writer 与提交台账）待独立立项诊断。

## Root Cause（嫌疑，待立项确认）

- 嫌疑 A（产品侧）：JDBC 2PC sink 的 commit key 未包含 subtask 身份，或并行拓扑下多 subtask 的提交请求被归集到同一提交者，导致 per-subtask 提交证据丢失。
- 嫌疑 B（测试侧）：该 E2E 场景的并行拓扑在本地执行时实际退化为单 subtask/分区（subtask-id 分配与分区数配置问题），断言前提不成立。

## Fix

- 未修复。待独立立项确认嫌疑 A/B；若为 A，修复点在 `nop-stream-connector-jdbc` 提交路径；若为 B，修正 E2E 拓扑/断言。

## Tests

- 既有测试 `TestParallel2PcJdbcE2E` 本身即守门测试；修复立项时以其回归通过为验收之一，并按 Bug Fix Test Coverage Rule 评估是否补组件级回归测试。

## Affected Files

- `nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestParallel2PcJdbcE2E.java`
- `nop-stream/nop-stream-connector-jdbc/`（嫌疑修复面，待诊断）

## Notes For Future Refactors

- exactly-once sink 的提交台账断言（per-subtask commit-key evidence）是对 2PC 语义的关键守门，重构提交路径时不得弱化该断言。
- 全仓 reactor 构建下该失败会连带 SKIP 下游模块（`-fae` 语义），影响 CI 数据完整性——修复前 `tests` 相关聚合验证需绕开该模块。
