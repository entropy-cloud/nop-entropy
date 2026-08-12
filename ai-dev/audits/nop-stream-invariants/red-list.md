# nop-stream I1 red list（pin-and-record，移交 I2）

> Status: active
> Created: 2026-08-12 (I1, plan `2026-08-12-1217-2-nop-stream-invariants-cycle1-I1-first-gates.md`)
> Semantics: **pin-and-record** — I1 只沉淀门禁并 pin 当前行为，不修复任何差异；行为漂移才会触发
> CI 红。本文件 = 已知差异移交 I2（red list 生成）的载体，每条含 `文件:行` 证据 + pin 位置。
> I2 依此跑门禁 → red list → I3 裁决 → I4 修复。

## 1. 门禁② TwoPhaseCommitSinkFunction.saveState 无锁 copy（R16-AR-1）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:83`
  （`new TreeMap<>(pendingCommits)` 无 synchronized 块）
- **关联不变式**：#2（synchronized 集合迭代点必须在 synchronized 块内）
- **对应 finding**：R16-AR-1（`2026-05-31-adversarial-review-nop-stream-r16/01-open-findings.md`）
- **行为 pin（JUnit）**：`TestSynchronizedCollectionInvariant.testSaveStatePinsSnapshotContentComplete`
  —— 断言快照内容完整（pending 全部条目原样进入 snapshot），锁状态不在此断言。
- **静态 pin（mjs）**：`ai-dev/audits/nop-stream-invariants/mjs-pins.json`
  `pinnedViolations[0]`（`TwoPhaseCommitSinkFunction.java:83 iteration of synchronized
  collection 'pendingCommits' (copy-constructor) outside synchronized block`）——违规集 ⊆ pin 集 → 绿。
- **I2 处理**：red list #4（I0 catalog §6 编号），修复 = saveState 内 `synchronized (pendingCommits)` 包裹 copy。

## 2. 门禁② TwoPhaseCommitSinkFunction.setPendingCommits 接受任意 Map（R16-AR-11）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:76-78`
  （setter 未包装 synchronizedMap；调用方 `StreamSinkOperator.java:157` restore 路径已主动包装，部分缓解）
- **关联不变式**：#2
- **对应 finding**：R16-AR-11
- **pin**：行为由既有测试覆盖（`TestTwoPhaseCommitSinkFunction` restore 路径）；锁状态由 mjs
  扫描器按「字段声明」口径兜底——本 setter 不声明 synchronized 字段，故无静态违规；差异登记
  **watch-only**，I2 复核 setter 防护必要性。
- **I2 处理**：red list #5（I0 catalog §6 编号），修复 = setter 内部包装
  `Collections.synchronizedMap(new TreeMap<>(pending))` 或文档化调用方契约。

## 3. 门禁⑤ JdbcClusterRegistry.registerNode 写 lease_expire_at=0L（R16-AR-9）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java:112-115`
  （INSERT 仍写 `lease_expire_at=0L`，:115）；`:172-174` getActiveNodes 仍按 `lease_expire_at > now` 过滤（:174）
- **关联不变式**：#5（registerNode 后节点必须对 getActiveNodes 立即可见）
- **对应 finding**：R16-AR-9
- **行为 pin（JUnit）**：`TestClusterRegistryConsistencyInvariant.testRegisterNodeVisibilityIsPinnedPerImpl`
  —— JDBC 分支显式断言「注册后 getActiveNodes 为空」并注明差异 pin 移交 I2；renewLease 后可见
  （per-renewal 生效路径保持绿）。
- **I2 处理**：red list #1/#2（I0 catalog §6 编号），修复 = registerNode INSERT 写 `now + leaseTtlMs`
  而非 0L。

## 4. 门禁⑤ InMemoryClusterRegistry.renewLease 忽略 leaseTimeoutMs（R16-AR-18）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:68-81`
  （renewLease 只存时间戳 :74，忽略 leaseTimeoutMs 参数）；`:90/:98/:114` 仍用固定 `leaseTtlMs`
- **关联不变式**：#5（renewLease 必须按 per-renewal 参数计算过期时间）
- **对应 finding**：R16-AR-18
- **行为 pin（JUnit）**：`TestClusterRegistryConsistencyInvariant.testRenewLeasePerRenewalTimeoutIsPinnedPerImpl`
  —— InMemory 分支显式断言「expireAt - startAt ≈ 15s 而非 5s」并注明差异 pin 移交 I2。
- **I2 处理**：red list #3（I0 catalog §6 编号），修复 = renewLease 记录 per-renewal
  `timestamp + leaseTimeoutMs` 或按参数计算 TTL。

## 5. 非门禁红项（I0 watch-only residual，I2 动态验证）

- `LocalFileCheckpointStorage.java:116-138` 按文件名 ID 排序（R16-AR-15，级联解除后 watch-only）。
- `WindowOperator.java:776-783` onEventTime cleanup 路径未见 `retireWindow`（R15-AR-8，MergingWindowSet
  收敛性待 I2 动态验证）。
- `InputGate.java:625-674` per-id `inFlightAlignments` 对齐（R15-AR-9），`maxConcurrentCheckpoints>1`
  场景语义待 I2 验证。

---

## 移交声明

- I1 不修复以上任何项（Non-Goals：门禁跑出的 red list 属 I4；本 plan 只做 pin-and-record 登记）。
- 门禁红/绿语义：CI 红的触发条件 = 行为偏离被 pin 的当前行为（防回归），而非「已知违背即红」。
- 下一站：I2（red list 生成，`2026-08-12-1217-3-...`）。
