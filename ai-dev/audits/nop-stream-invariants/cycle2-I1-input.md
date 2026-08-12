# Cycle 2 / I1 输入统计（输出契约族门禁结果，唯一落点）

> Status: active（Cycle 2 / I1 收口输入，2026-08-12 实测）
> Source: plan `ai-dev/plans/2026-08-12-1217-8-nop-stream-invariants-cycle2-I1-output-contract-gates.md`；surefire 报告 + mjs 工具输出
> Note: 本文件是 Cycle 2 门禁统计的**唯一落点**，供 Cycle 2 / I2 消费；`cycle1-I6-input.md` 已被 I6 消费，不回写不追加。

## 门禁总数（JUnit 门禁 10 个测试类，102 tests，0 failures）

实测命令：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` → BUILD SUCCESS。

| 模块 | 测试类 | 计数（surefire 实测） |
| --- | --- | --- |
| nop-stream-core | TestCheckpointIDCounterInvariant（门禁③） | 8 |
| nop-stream-core | TestSynchronizedCollectionInvariant（门禁②） | 12 |
| nop-stream-core | TestInvariantTableCompleteness（表完备性） | 10 |
| nop-stream-core | **TestOutputContractInvariant（I1 新增，不变式 #6 输出契约族）** | **10（参数化 4 实现类 + ChainingOutput 转发/fail-fast + TimestampedCollector 透传×3 + 接线断言）** |
| nop-stream-runtime | TestWindowRoundTripInvariant（门禁①） | 9 |
| nop-stream-runtime | TestClusterRegistryConsistencyInvariant（门禁⑤） | 10 |
| nop-stream-runtime | TestWindowOperatorMergingCleanupInvariant（RL-6） | 4 |
| nop-stream-runtime | TestRuntimeInvariantTableCompleteness（表完备性） | 11 |
| nop-stream-cep | TestCepReleaseSymmetryInvariant（门禁④） | 21 |
| nop-stream-cep | TestCepInvariantTableCompleteness（表完备性） | 7 |
| **合计** | **10 个门禁类（9 → 10）** | **102 tests / 0 failures / 0 errors** |

族覆盖：五族门禁（① Window round-trip ② synchronized 迭代 ③ CheckpointIDCounter ④ CEP 释放对称 ⑤ ClusterRegistry 一致性）+ 输出契约族（不变式 #6，I1 新增）+ 3 模块表完备性 + RL-6 收敛门禁。

## 输出契约族门禁细节（I1 新增）

- **JUnit**：`TestOutputContractInvariant`（nop-stream-core，10 用例）——registry-backed 参数化穷举 4 个 main `Output` 实现类（`ChainingOutput`=forward / `TimestampedCollector`=forward / `RecordWriterOutput`+`BroadcastingRecordWriterOutput`=pinned-known-violation），读 `output-contract-registry.json`（分类以注册表为准）；ChainingOutput 转发+无消费者 fail-fast（`ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）；TimestampedCollector 透传 + 透传目标敏感（包装 no-op 丢弃 / 包装 RWO = 等价跨 task 丢弃）；反射实例化断言空体 no-op；`registerSideOutputConsumer` ↔ `ChainingOutput.sideOutputConsumers` 接线断言。
- **mjs**：`scan-output-contract` 子命令（V1 类级枚举 / V2 失效类 / V3 行为漂移 / V4 新增发射点 / V5 失效发射点）——独立运行 + `all` 均 exit 0；self-test 正反例覆盖 V1–V5（含 committed `fixtures/OutputContractFixture.java`）；未识别方法体 / 解析失败显式 fail。
- **发射点注册表**：`output-contract-registry.json`（6 发射点：`ProcessOperator.java:111/:134`、`WindowOperator.java:1030/:1860`、`CepOperator.java:483/:777`；in-task = E2E 覆盖 / 跨 task 可达 = `HG-01` 关联）。

## E2E 基线

实测命令：`./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestSideOutputChainingE2E` → BUILD SUCCESS。

- `TestSideOutputChainingE2E`（nop-stream-runtime）**3/3 绿**：端到端转发（sideOutput(lateDataOutputTag) → ChainingOutput → 消费者）/ 无消费者 fail-fast（`ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）/ StreamTaskInvokable 接线（注册后 wiring 生成的 ChainingOutput 可达）。

## pin 数

- mjs pin：**2**（`mjs-pins.json` pinnedViolations = 2，均为不变式 #6 跨 task 实例过渡 pin）：
  - `RWO-cross-task-noop`：`StreamTaskInvokable$RecordWriterOutput`（:645 空体 no-op，`key` = 扫描器实际违规串）
  - `BRWO-cross-task-noop`：`StreamTaskInvokable$BroadcastingRecordWriterOutput`（:705 空体 no-op，`key` = 扫描器实际违规串）
  - 关联 `HG-01`（人工确认门）；removalTrigger = Cycle 2 / I4 interim fail-fast 修复落地 + 注册表分类更新后移除，禁静默移除。
- JUnit pin 语义：RWO/BRWO 反射断言 = pinned-known-violation（分类以注册表为准，分类迁移须同步注册表与断言）。

## red list 状态（Cycle 2 / I2 输入）

- 不变式 #6 门禁全绿（JUnit 102/102 + mjs `all` exit 0 + E2E 3/3）——为 Cycle 2 / I2 提供确定性全绿基线；I2 以此 + 对抗探查产出 red list。
- 跨 task 缺口（RWO/BRWO 空体 no-op）以过渡 pin 登记在案，未修复（interim fail-fast 预授权分派 Cycle 2 / I4；线协议 = `HG-01` 人工确认门）。
