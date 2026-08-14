# Cycle 3 / I6 输入统计（I5 全量验证结果，唯一落点）

> Status: active（I6 收口输入，2026-08-13 I5 实测）
> Source: plan `ai-dev/plans/2026-08-13-1040-1-nop-stream-invariants-cycle3-I5-full-verification.md` Phase 1 实测；surefire 报告 + mjs 工具输出
> Note: 本文件是 I6 收口统计的**唯一落点**；`gate-inventory.json` 仅作门禁过程记录，不承担统计；`cycle3-I1-input.md` 为 Cycle 3 起点统计（I1 落档），不回写。

## 门禁总数（JUnit 门禁 11 个测试类，112 tests，0 failures）

实测命令：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` → BUILD SUCCESS（依赖本地仓库 nop-stream SNAPSHOT，本次全量 run 已装）。

| 模块 | 测试类 | 计数（surefire 实测） |
| --- | --- | --- |
| nop-stream-core | TestCheckpointIDCounterInvariant（门禁③） | 8 |
| nop-stream-core | TestSynchronizedCollectionInvariant（门禁②） | 12 |
| nop-stream-core | TestInvariantTableCompleteness（表完备性） | 10 |
| nop-stream-core | TestOutputContractInvariant（输出契约族 #6） | 10 |
| nop-stream-core | TestWiringExistenceInvariant（I1 新增，wiring 存在性族 #7） | 10 |
| nop-stream-runtime | TestWindowRoundTripInvariant（门禁①） | 9 |
| nop-stream-runtime | TestClusterRegistryConsistencyInvariant（门禁⑤） | 10 |
| nop-stream-runtime | TestWindowOperatorMergingCleanupInvariant（RL-6 收敛门禁） | 4 |
| nop-stream-runtime | TestRuntimeInvariantTableCompleteness（表完备性） | 11 |
| nop-stream-cep | TestCepReleaseSymmetryInvariant（门禁④） | 21 |
| nop-stream-cep | TestCepInvariantTableCompleteness（表完备性） | 7 |
| **合计** | **11 个门禁类** | **112 tests / 0 failures / 0 errors / 0 skipped** |

族覆盖：五族门禁（① Window round-trip ② synchronized 迭代 ③ CheckpointIDCounter ④ CEP 释放对称 ⑤ ClusterRegistry 一致性）+ 输出契约族 #6 + wiring 存在性族 #7（I1 新增）+ 3 模块表完备性 + RL-6 收敛门禁。计数与 I1 门禁基线（`cycle3-I1-input.md`，112 tests）**精确一致，无 anomaly**（Cycle 3 零代码变更）。

## JUnit 测试总数（nop-stream 模块组 surefire 汇总）

实测命令：`./mvnw test -pl nop-stream -am -T 1C` → BUILD SUCCESS（01:26 min）。

| 模块 | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| nop-stream-core | 1465 | 0 | 0 | 1 |
| nop-stream-runtime | 821 | 0 | 0 | 8 |
| nop-stream-cep | 327 | 0 | 0 | 0 |
| nop-stream-rocksdb | 85 | 0 | 0 | 0 |
| nop-stream-connector | 35 | 0 | 0 | 0 |
| nop-stream-connector-jdbc | 32 | 0 | 0 | 0 |
| nop-stream-connector-batch | 35 | 0 | 0 | 0 |
| nop-stream-connector-debezium | 19 | 0 | 0 | 1 |
| nop-stream-flow | 51 | 0 | 0 | 0 |
| nop-stream-fraud-example | 25 | 0 | 0 | 0 |
| **合计** | **2895** | **0** | **0** | **10** |

统计口径：nop-stream 模块组内 surefire 汇总，不混入 `-am` 连带的上游模块（nop-kernel 等）。skipped 10 条均为既有环境依赖跳过（multi-jvm 进程 spawn 等 runtime 8 + core 1 + debezium CDC 1），非失败、非 I5 新增。基线对比：Cycle 2 2833 → Cycle 3 2895（增量 = Cycle 3 / I1 新增 wiring 门禁用例 + I2/I3 期间随 I1 落地的相关用例；**与 I1 全量回归基线 2895 精确一致**）。

## pin 数

- mjs pin：**0**（`mjs-pins.json` pinnedViolations = []；无 unpinned 违规、无 stale pin；`node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0，六命令 inventory / sync / scan-iterations / scan-output-contract / scan-wiring / self-test 全绿，全绿模式无 stdout 属正常）。
- `internal-creation` = 注册表 disposition 非 pin（I1 裁定，`setKeyedStateBackend`/`setOperatorStateBackend` main 零调用点 = design-intent 内部创建，V1 carve-out 附理由受棘轮约束），维持不变。

## red list 状态

- **C3-RL-1..10 全部已裁决，零悬挂**（I3 裁决表 `adjudication-table.md` §12-§16）：C3-RL-1/2/3 = 记录性关闭；C3-RL-4/5/6/7/8/9 = **P2 升级登记 backlog**（附依据即合规，不驱动独立修复计划）；C3-RL-10 = 关闭 + backlog 修订。
- **C3-PR-1..8 全部显式处置**：C3-PR-1 仅测试注入复探零新实例关闭；C3-PR-2 守卫 residual 维持 watch-only；C3-PR-3..8 显式处置行在案（§16）。
- 本 plan 全量验证未引入类别清扫动作（纯验证零代码变更）→ **P2/P3 backlog 触发条件（类别清扫 / 复探 / 多消费者接线需求出现）均未触发**，backlog 条目状态未改动（C3-RL-4..9 / C2-RL-3 / C2-PR-3 维持）。
- 结论：red list 零悬挂 + backlog 触发评估零触发。

## 新族数

- **预期 0 实测 0**：I5 全量验证（2895 tests）+ 门禁（112 tests）+ e2e（8/8）全绿，零新失败；I3 §14 已显式声明无新独立族（无 PD-16 派生登记），I5 实测与之一致，Cycle 4 不派生（I6 稳态判定输入）。

## e2e 结果摘要

| 测试类 | 模块 | 结果 |
| --- | --- | --- |
| TestProcessingTimeWindowProductionE2E | nop-stream-runtime | **3/3 绿**（显式复跑 + 含于全量；含不变式 #7 接线运行时连通断言——PT 无服务 fail-fast 断言在 cep 侧，属跨 task E2E 既有归属，不重复） |
| TestCepProductionExecutionE2E | nop-stream-cep | **4/4 绿**（显式复跑 + 含于全量） |
| TestSupervisionLoopCheckpointReconnectE2E | nop-stream-runtime | **1/1 绿**（显式复跑 + 含于全量） |

合计 **8/8 绿**，与 I2 复跑基线一致。

## Cycle 3 执行路径事实

- **I1 → I2 → I3 → I5 → I6**，**I4 不立 plan**（I3 §13 显式声明「无 P0/P1 项 → I4 不立 plan，直接进入 I5 或 I6 判定」；`ai-dev/plans/` 无 Cycle 3 / I4 计划文件，已核实）。
- I1（`2026-08-13-0805-1`，wiring 存在性门禁沉淀）→ I2（`2026-08-13-0805-2`，不变式驱动审计）→ I3（`2026-08-13-0805-3`，发现裁决）全部 `completed`；本 plan（I5）为纯验证（零代码变更，零门禁新增）。

## 附：I6 收口引用指引

- I6 稳态判定输入：red list 零悬挂（C3-RL-1..10 全裁决在案 + C3-PR-1..8 全处置）+ backlog 触发评估（零触发）+ 新族数（0，不派生 Cycle 4）+ 门禁统计（11 类 / 112 tests）+ 全量基线（2895 tests / 0 failures）+ e2e 摘要（8/8）+ Cycle 3 执行路径事实（I4 不立 plan）。
- 门禁 `all` 输出：全绿模式无 stdout 属正常（`all` 分支不打印逐命令 OK），以 exit 0 判绿。
- 遗留处置归 I6：`HG-01` 线协议人工确认门（延续，非本 plan 项）；稳态判定 / Cycle 4 派生评估归 I6。
