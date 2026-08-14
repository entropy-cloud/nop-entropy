# Cycle 2 / I6 输入统计（I5 全量验证结果，唯一落点）

> Status: active（I6 收口输入，2026-08-12 I5 实测）
> Source: plan `ai-dev/plans/2026-08-12-1217-12-nop-stream-invariants-cycle2-I5-full-verification.md` Phase 1 实测；surefire 报告 + mjs 工具输出
> Note: 本文件是 I6 收口统计的**唯一落点**；`gate-inventory.json` 仅作门禁过程记录，不承担统计；`cycle2-I1-input.md` 为 Cycle 2 起点统计（I1 落档），不回写。

## 门禁总数（JUnit 门禁 10 个测试类，102 tests，0 failures）

实测命令：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` → BUILD SUCCESS（依赖本地仓库 nop-stream SNAPSHOT，本次全量 run 已装）。

| 模块 | 测试类 | 计数（surefire 实测） |
| --- | --- | --- |
| nop-stream-core | TestCheckpointIDCounterInvariant（门禁③） | 8 |
| nop-stream-core | TestSynchronizedCollectionInvariant（门禁②） | 12 |
| nop-stream-core | TestOutputContractInvariant（I1 新增，输出契约族 #6） | 10（含 I4 翻转 fail-fast case + 透传目标敏感迁移用例） |
| nop-stream-core | TestInvariantTableCompleteness（表完备性） | 10 |
| nop-stream-runtime | TestWindowRoundTripInvariant（门禁①） | 9 |
| nop-stream-runtime | TestClusterRegistryConsistencyInvariant（门禁⑤） | 10 |
| nop-stream-runtime | TestWindowOperatorMergingCleanupInvariant（RL-6 收敛门禁） | 4 |
| nop-stream-runtime | TestRuntimeInvariantTableCompleteness（表完备性） | 11 |
| nop-stream-cep | TestCepReleaseSymmetryInvariant（门禁④） | 21 |
| nop-stream-cep | TestCepInvariantTableCompleteness（表完备性） | 7 |
| **合计** | **10 个门禁类** | **102 tests / 0 failures / 0 errors / 0 skipped** |

族覆盖：五族门禁（① Window round-trip ② synchronized 迭代 ③ CheckpointIDCounter ④ CEP 释放对称 ⑤ ClusterRegistry 一致性）+ 输出契约族 #6（I1 新增）+ 3 模块表完备性 + RL-6 收敛门禁。计数与 I4 后基线（102）精确一致，无 anomaly。

## JUnit 测试总数（nop-stream 模块组 surefire 汇总）

实测命令：`./mvnw test -pl nop-stream -am -T 1C` → BUILD SUCCESS（03:32 min）。

| 模块 | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| nop-stream-core | 1428 | 0 | 0 | 1 |
| nop-stream-runtime | 805 | 0 | 0 | 8 |
| nop-stream-cep | 320 | 0 | 0 | 0 |
| nop-stream-rocksdb | 83 | 0 | 0 | 0 |
| nop-stream-connector | 35 | 0 | 0 | 0 |
| nop-stream-connector-jdbc | 32 | 0 | 0 | 0 |
| nop-stream-connector-batch | 35 | 0 | 0 | 0 |
| nop-stream-connector-debezium | 19 | 0 | 0 | 1 |
| nop-stream-flow | 51 | 0 | 0 | 0 |
| nop-stream-fraud-example | 25 | 0 | 0 | 0 |
| **合计** | **2833** | **0** | **0** | **10** |

统计口径：nop-stream 模块组内 surefire 汇总，不混入 `-am` 连带的上游模块（nop-kernel 等）。skipped 10 条均为既有环境依赖跳过（multi-jvm 进程 spawn 等 runtime 8 + core 1 + debezium CDC 1），非失败、非 I5 新增。基线对比：Cycle 1 2822 → Cycle 2 2833（增量 = I4 新增用例）。

## pin 数

- mjs pin：**0**（`mjs-pins.json` pinnedViolations = []；I4 移除 RWO/BRWO 2 条过渡 pin 后无 stale pin、无 unpinned 违规，`all` exit 0）。
- JUnit pin 断言：I4 翻转（fail-fast case + 透传目标敏感迁移）后无残留 pin 语义，`TestOutputContractInvariant` 10/10 全绿。

## red list 状态

- **C2-RL-1（RWO 跨 task no-op）/ C2-RL-2（BRWO 跨 task no-op）**：I4 已修复（`88bc0270c`，RWO :649-659 / BRWO :717-727 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`），注册表分类 `fail-fast`、pin 移除，门禁复跑零命中——**修复确认**。
- **C2-RL-3（注册表 disposition 措辞过 claim）+ C2-PR-4（E2E 覆盖缺口）**：P3 backlog（I3 裁决 §7.2/§10，附依据即合规），不驱动独立修复计划；本 plan 如实记录实际覆盖事实（见下方发射点覆盖事实表），处置归 I6 引用。
- **零悬挂**：C2-RL-1/2 修复在案 + C2-RL-3 P3 已裁决 → red list 零悬挂。

## 新族数

- **预期 0 实测 0**：I5 全量验证（2833 tests）+ 门禁（102 tests）+ e2e（4/4 + 6/6 + 7/7）全绿，零新失败；无新失败类登记，Cycle 3 不派生（I6 稳态判定输入）。
- 已知族在案：PD-15 输出契约族（I1 门禁 + I4 类别清扫后实例零遗留，`HG-01` 人工确认门延续，属 I6 处置项非新族）。

## e2e 结果摘要

| 测试类 | 模块 | 结果 |
| --- | --- | --- |
| TestSideOutputChainingE2E | nop-stream-runtime | **4/4 绿**（in-task 3 用例 + I4 新增跨 task fail-fast 第 4 用例：1 writer RWO / 2 writers BRWO 全路径；显式复跑 + 含于全量） |
| TestWindowOperatorUnificationE2E | nop-stream-runtime | 6/6 绿（显式复跑 + 含于全量） |
| TestWindowEndToEnd | nop-stream-core | 7/7 绿（显式复跑 + 含于全量） |

## E2E 发射点覆盖事实表（C2-RL-3/C2-PR-4 背景，供 I6 引用）

> 2026-08-12 I5 实测：对照 `TestSideOutputChainingE2E` 源码（4 用例全部经 `TestableWindowOperator` late-data 路径发射）与注册表 6 发射点 live 核对。注册表措辞未改（P3 backlog 处置不变）；I4 第 4 用例覆盖跨 task fail-fast 出口路径（RWO/BRWO）而非新发射点。

| 发射点（live 行号 2026-08-12 核对） | E2E 覆盖 |
| --- | --- |
| `ProcessOperator.java:111`（processElement ctx.output → `output.collect(tag, record)`） | **无**（E2E 未实例化 ProcessOperator；单元层亦无 OutputTag 发射断言） |
| `ProcessOperator.java:134`（onTimer ctx.output → `output.collect(tag, record)`） | **无** |
| `WindowOperator.java:1030`（late-data `sideOutput()` → `output.collect(lateDataOutputTag, element)`） | **有**（TestSideOutputChainingE2E 全部 4 用例经此路径发射） |
| `WindowOperator.java:1860`（窗口函数 ctx.output → `output.collect(tag, record)`） | **无** |
| `CepOperator.java:483`（late-data → `output.collect(lateDataOutputTag, element)`） | **无**（TestCepSkipStrategyE2E 仅 import OutputTag，测试内匿名 collect 为 mock 非发射点） |
| `CepOperator.java:777`（pattern ctx.output → `output.collect(tag, record)`） | **无** |

结论：6 发射点中仅 `WindowOperator.java:1030` 有 E2E 覆盖，其余 5 个发射点零 E2E 覆盖——与 C2-RL-3 措辞过 claim / C2-PR-4 缺口一致；处置 = P3 backlog（I3 已裁决），I6 引用本表评估。

## 附：I6 收口引用指引

- I6 稳态判定输入：red list 零悬挂（C2-RL-1/2 修复确认 + C2-RL-3 P3 已裁决）+ 新族数（0，不派生 Cycle 3）+ 门禁统计（10 类 / 102 tests）+ 全量基线（2833 tests / 0 failures）+ e2e 摘要（4/4 + 6/6 + 7/7）+ 发射点覆盖事实表。
- 门禁 `all` 输出：全绿模式无 stdout 属正常（`all` 分支不打印逐命令 OK），以 exit 0 判绿。
- 遗留处置归 I6：`HG-01` 线协议人工确认门（延续，非本 plan 项）；C2-PR-2/5 扩展候选评估（I3 §9 移交）。
