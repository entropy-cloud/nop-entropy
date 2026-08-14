# Cycle 1 / I6 输入统计（I5 全量验证结果，唯一落点）

> Status: active（I6 收口输入，2026-08-12 I5 实测）
> Source: plan `ai-dev/plans/2026-08-12-1217-6-nop-stream-invariants-cycle1-I5-full-verification.md` Phase 1 实测；surefire 报告 + mjs 工具输出
> Note: 本文件是 I6 收口统计的**唯一落点**；`gate-inventory.json` 仅作门禁过程记录，不承担统计。

## 门禁总数（JUnit 门禁 9 个测试类，92 tests，0 failures）

实测命令：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` → BUILD SUCCESS。

| 模块 | 测试类 | 计数（surefire 实测） |
| --- | --- | --- |
| nop-stream-core | TestCheckpointIDCounterInvariant（门禁③） | 8 |
| nop-stream-core | TestSynchronizedCollectionInvariant（门禁②） | 12（I4 +2：saveState 并发 / setPendingCommits 防御拷贝） |
| nop-stream-core | TestInvariantTableCompleteness（表完备性） | 10 |
| nop-stream-runtime | TestWindowRoundTripInvariant（门禁①） | 9 |
| nop-stream-runtime | TestClusterRegistryConsistencyInvariant（门禁⑤） | 10（I4 +2：JDBC UPDATE 路径 / InMemory per-renewal） |
| nop-stream-runtime | TestWindowOperatorMergingCleanupInvariant（I4 新增，RL-6） | 4（2 pin 翻转 + 2 新增） |
| nop-stream-runtime | TestRuntimeInvariantTableCompleteness（表完备性） | 11 |
| nop-stream-cep | TestCepReleaseSymmetryInvariant（门禁④） | 21 |
| nop-stream-cep | TestCepInvariantTableCompleteness（表完备性） | 7 |
| **合计** | **9 个门禁类** | **92 tests / 0 failures / 0 errors** |

族覆盖：五族门禁（① Window round-trip ② synchronized 迭代 ③ CheckpointIDCounter ④ CEP 释放对称 ⑤ ClusterRegistry 一致性）+ 3 模块表完备性 + I4 新增 RL-6 收敛门禁。

## JUnit 测试总数（nop-stream 模块组 surefire 汇总）

实测命令：`./mvnw test -pl nop-stream -am -T 1C` → BUILD SUCCESS（01:29 min）。

| 模块 | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| nop-stream-core | 1418 | 0 | 0 | 1 |
| nop-stream-runtime | 804 | 0 | 0 | 8 |
| nop-stream-cep | 320 | 0 | 0 | 0 |
| nop-stream-rocksdb | 83 | 0 | 0 | 0 |
| nop-stream-connector | 35 | 0 | 0 | 0 |
| nop-stream-connector-jdbc | 32 | 0 | 0 | 0 |
| nop-stream-connector-batch | 35 | 0 | 0 | 0 |
| nop-stream-connector-debezium | 19 | 0 | 0 | 1 |
| nop-stream-flow | 51 | 0 | 0 | 0 |
| nop-stream-fraud-example | 25 | 0 | 0 | 0 |
| **合计** | **2822** | **0** | **0** | **10** |

统计口径：nop-stream 模块组内 surefire 汇总，不混入 `-am` 连带的上游模块（nop-kernel 等）。skipped 10 条均为既有环境依赖跳过（multi-jvm 进程 spawn 6 + Kafka/Pulsar backend E2E 2 + debezium CDC 1 + event-time window E2E 1），非失败、非 I5 新增。

## pin 数

- mjs pin：**0**（`mjs-pins.json` pinnedViolations = []；I4 移除 2PC:83 后无 stale pin、无 unpinned 违规，`all`/`inventory`/`sync`/`scan-iterations`/`self-test` 全 exit 0）。
- JUnit pin 断言：I4 翻转 4 处（ClusterRegistry 2 + MergingCleanup 2）均已转为正向断言，无残留 pin 语义。

## red list 状态

- 7 条 RL 修复确认（I4 落地，red-list.md §5 逐条证据在案）：RL-1（JDBC INSERT 0L）+ RL-2（过滤互为表里）→ `fcc71fc05`；RL-3（InMemory per-renewal）→ `fcc71fc05` 触发闭合；RL-4（saveState 无锁）→ `fcc71fc05`；RL-5（setPendingCommits 任意 Map）→ `fcc71fc05`；RL-6（cleanup 不 retire）→ `58255014b`；RL-7（ChainingOutput 侧输出丢弃）→ `b20fcd0e1`。
- **零悬挂**：全部 7 条标注修复证据 + 门禁复跑绿（I5 全量 + 门禁 92 全绿复核通过）。
- RL-3 闭合：roadmap Follow-up Backlog 条目 ✅（保留历史处置记录）。

## 新族数

- **PD-15 输出契约族在案 = 1**（I3 派生登记：不变式「任何 `Output.collect(OutputTag, X)` 必须转发至注册 side-output 消费者，不得静默丢弃」；触发证据 `ChainingOutput.java:84-86`/`WindowOperator.java:1015-1017`/`StreamTaskInvokable.java:171/:209`；I4 已按 RL-7 修复实例并做类别清扫，跨 task no-op（RecordWriterOutput/ BroadcastingRecordWriterOutput）为同族已知实例 + 线协议结构性变更 → 移交 I6 人工确认候选）。
- I5 未发现新失败类（零新失败），无新增族登记。

## e2e 结果摘要

| 测试类 | 模块 | 结果 |
| --- | --- | --- |
| TestWindowOperatorUnificationE2E | nop-stream-runtime | 6/6 绿（显式复跑 + 含于全量） |
| TestWindowEndToEnd | nop-stream-core | 7/7 绿（显式复跑 + 含于全量） |

## 附：I6 收口引用指引

- I6 稳态判定输入：red list 零悬挂（7/7 修复）+ 新族数（PD-15 在案 1，跨 task no-op 人工确认候选）+ 门禁统计（9 类 / 92 tests）+ 全量基线（2822 tests）。
- 门禁 `all` 输出：全绿模式无 stdout 属正常（`all` 分支不打印逐命令 OK），以 exit 0 判绿。
