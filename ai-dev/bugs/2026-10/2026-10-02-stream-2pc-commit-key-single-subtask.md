# 2026-10-02 nop-stream 2PC commit-key 全部归属单 subtask（已修复，plan 2306 Phase 4 收口）

> 状态：**已修复**（plan 2306 Phase 4，2026-10-03）。TestParallel2PcJdbcE2E 全绿。

## Problem

- `nop-stream-fraud-example` 的 `TestParallel2PcJdbcE2E` 失败（全仓 reactor 构建与单独复跑均失败，非 flaky）。
- 断言：JDBC exactly-once sink 的提交台账必须包含「同一 epoch 由 ≥2 个不同 subtask id 提交」的证据；实际 ledger 10 个分区的提交全部归属 subtask-1（`{0=[1], 1=[1], ..., 9=[1]}`）。
- 位置：`nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestParallel2PcJdbcE2E.java`（断言处）；被测面为 `nop-stream-connector-jdbc` 的两阶段提交 exactly-once sink 的 commit-key 归属语义。

## Diagnostic Method（plan 2306 Phase 4 补全）

执行期症状已从 WI0 口径（ledger 有数据但全归 subtask-1）演变为 run1 数据表为空。两阶段探针定位：

1. 一次性探针测试 dump 管线两端：data={} 且 ledgerSubtasks={}，且 CheckpointCoordinator 对每个 checkpoint 报 "Failed to store checkpoint"——STRICT_EXACTLY_ONCE 下 2PC 依赖 checkpoint 完成提交，零提交即零落库。
2. 取持久化线程异常栈：`nop.err.core.json.only-data-bean-is-serializable`（JdbcTwoPhaseCommitSink$DmlOp）——checkpoint 状态里的 DML 缓冲对象未标 @DataBean，JsonTool 拒绝序列化 → 所有 checkpoint 存储失败。
3. 修复序列化后复跑：data 断言通过、ledger={0..8=[1]}——回到 WI0 口径（全归 subtask-1）。
4. 直接调用 KeyGroupAssignment.assignToSubtask("key-0".."key-29", 128, 2)：30 个 key 全部返回 1——stableHash 对 String 键走 Object#hashCode()（31 底数算术哈希，顺序 key 族哈希值线性相邻），key-group 全部落入同一 subtask 的连续区间；murmur3 对照分布 20/10。

- WI0 全仓基线 run（2026-10-02，`-Pcoverage test -T 1C -fae`）发现该模块测试失败；surefire 报告显示 120 个测试中仅此 1 个 failure。
- 排除 flaky 假设：`mvnq -- jacoco:prepare-agent test -pl :nop-stream-fraud-example -Dtest=TestParallel2PcJdbcE2E` 单独复跑，同样失败（`Tests run: 1, Failures: 1`，4.9s）。
- 未再深挖实现（超出 WI0 边界）：commit-key 的生成/归集路径（connector-jdbc 的 recoverable writer 与提交台账）待独立立项诊断。

## Root Cause（嫌疑 A 成立，两层产品缺陷；嫌疑 B 排除）

- **缺陷 1（提交面）**：`JdbcTwoPhaseCommitSink$DmlOp` 未标 `@DataBean`，checkpoint 状态 JSON 序列化被 JsonTool 守卫拒绝 → 所有 checkpoint 存储失败 → STRICT_EXACTLY_ONCE 下 2PC 永不提交（该缺陷掩盖了原 WI0 症状）。
- **缺陷 2（路由面）**：`KeyGroupAssignment.stableHash` 对 String/数值键直接用 `Object#hashCode()`（算术递增），顺序命名 key 族（key-0/key-1/...，最常见的键命名模式）哈希值线性相邻 → key-group 塌缩到单一 subtask 的连续区间，P>1 静默退化为单 subtask（WI0 原症状的根因）。
- 嫌疑 B（测试拓扑退化）排除：E2E 拓扑 HASH 边 + P=2 声明正确，per-subtask copy 机制（copyForSubtask）工作正常，两 subtask 均已 ack checkpoint。

## Fix（2026-10-03）

- **缺陷 1**：`DmlOp` 标记 `@DataBean`（对齐 TaskLocation 等既有 checkpoint 状态类模式）；`JdbcTwoPhaseCommitSink.commit()` 增加 normalizeBatch——JSON 往返后 DmlOp 漂移为 LinkedHashMap，提交前按 kind/row 回水合（instanceof DmlOp 快路径保持组件级测试兼容）。全仓复跑补遗：normalizeBatch 首版拒绝了遗留裸行 Map 形态（dmlMode 引入前 INSERT 批直接持有行 Map；TestJdbcTwoPhaseCommitLedgerNamespace/TestJdbcTwoPhaseCommitSinkParallelIsolation 沿用该形态注入）——已补兼容分支：无 kind/row 的 Map 按 INSERT 语义回水合。
- **缺陷 2**：`stableHash` 对所有值类型统一走 canonical JSON + murmur3_32（类 javadoc 本就承诺 canonical JSON 跨 JVM 确定性；murmur 分布实证 20/10）。**兼容性裁定**：key→key-group 映射改变，旧 checkpoint 的 keyed state 布局不兼容——2.0.0-SNAPSHOT 预发布阶段接受；record routing 与 state ownership 仍经同一函数保持 AR-01 parity。受影响既有测试改判：TestKeyRoutingOwnershipParity.enumKeyHashesByNameNotByIdentity（enum 与 String 公式分离后各按己公式断言）、TestKeyGroupAssignment.routingParityForBuiltInTypes（"legacy hashCode 平价"用例改写为 murmur 公式断言 + 新增 sequentialKeysSpreadAcrossSubtasks 缺陷签名守卫）。

## Tests

- 守门测试 `TestParallel2PcJdbcE2E` 转绿（multiset/恢复/per-subtask 证据/D1 拒绝四断言全过）。
- 新增/改写回归：TestKeyGroupAssignment.routingForBuiltInTypesUsesCanonicalJsonMurmur + sequentialKeysSpreadAcrossSubtasks；TestKeyRoutingOwnershipParity 枚举断言按新合同调整。
- nop-stream 全套件 1653 测试零失败（陈旧 surefire 报告清理后复核）。
- 全仓 `test -T 1C -fae` 复跑收口记录：本轮修复暴露的三个回归全部修复（connector 遗留形态兼容、TestCellDataLocators 贴边断言按锚点语义修正、TestPredefinedColorsIndex 冲突断言按首注册优先语义修正——后两者为测试断言与裁定语义不符，非产品缺陷）；nop-batch-core `testFailFastStopsSiblingThreads` 在 -T 1C 全仓高并发下偶发（期望 IllegalStateException 实得 BatchCancelException 的线程时序竞争），孤立复跑两连绿，本 plan 零文件涉及 nop-batch——裁定为 plan 外既有负载敏感 flake，另行观察。

## Affected Files

- `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java`（@DataBean + normalizeBatch）
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupAssignment.java`（stableHash 统一公式）
- `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/common/state/shard/TestKeyGroupAssignment.java`、`TestKeyRoutingOwnershipParity.java`（合同改判）
- `nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestParallel2PcJdbcE2E.java`（守门测试，未改断言）

## Notes For Future Refactors

- exactly-once sink 的提交台账断言（per-subtask commit-key evidence）是对 2PC 语义的关键守门，重构提交路径时不得弱化该断言。
- stableHash 公式变更对已存 checkpoint 的 keyed state 布局不兼容——若 2.0.0 正式发布前需要存量兼容，须引入带版本的状态迁移（本 plan 裁定不做，记录于此）。
- 顺序命名 key 族的哈希分布问题在 murmur3 统一后消除；后续新增键类型（含 POJO）默认走 JSON+murmur 路径，勿再为"性能"添加 hashCode 快路径。
