# nop-stream I2 red list（权威版，移交 I3 裁决）

> Status: active（7 条 RL 已于 I4 全部修复，见 §5 修复状态；WO 部分为历史裁定记录不动）
> Created: 2026-08-12 (I1 pin-and-record 版)；2026-08-12 I2 升级为确定性 red list（门禁全量运行 + watch-only 裁定 + 聚焦探查合并）
> Sources: I1 plan `2026-08-12-1217-2-nop-stream-invariants-cycle1-I1-first-gates.md`（pin 登记）；
> I2 plan `2026-08-12-1217-3-nop-stream-invariants-cycle1-I2-invariant-driven-audit.md`（red list 权威化）；
> I0 catalog §6（red list 候选编号）
> Semantics: **I2 权威版** — 每条含 位置 / 关联不变式 / 关联 finding / 族标注 / 门禁或验证来源 / 裁决输入。
> I3 依此逐条裁决（严重度 + 派发），I4 执行修复。I2 不修复任何项、不做严重度裁决。

## 0. 门禁全量运行结果（I2 Phase 1，2026-08-12 实测）

- **mjs `all` 退出码 0**：inventory / sync / scan-iterations / self-test 四命令全绿
  （`node ai-dev/tools/check-nop-stream-invariants.mjs all` → exit 0；逐命令：
  `inventory: OK` / `sync: OK` / `scan-iterations: OK` / `self-test: OK`）。
- **scan-iterations 输出**：live 违规 = 1（`TwoPhaseCommitSinkFunction.java:83` saveState 无锁 copy，
  copy-constructor 口径），被 `mjs-pins.json` 唯一 pin 吸收 → unpinned=0、stale=0。
  **无新增 pre-existing residual、无行为漂移、无 stale pin**（处置二分：全部落入「无变化」分支）。
- **五族 JUnit 门禁 + 3 模块表完备性**（`./mvnw test -pl nop-stream-core,nop-stream-runtime,nop-stream-cep -Dtest='Test*Invariant*'`，
  surefire 实测）：TestWindowRoundTripInvariant 9 / TestSynchronizedCollectionInvariant 10 /
  TestCheckpointIDCounterInvariant 8 / TestCepReleaseSymmetryInvariant 21 / TestClusterRegistryConsistencyInvariant 8 /
  TestInvariantTableCompleteness 10 / TestRuntimeInvariantTableCompleteness 11 / TestCepInvariantTableCompleteness 7
  —— 合计 84 tests，0 failures / 0 errors / 0 skipped，BUILD SUCCESS。
- **4 条已知 residual 复核表**（I2 Phase 1，live 行号 vs I1 pin 记录）：

| # | 位置（I1 pin） | live 行号（本次复核） | 漂移 | pin 状态 | 行为是否仍匹配 I1 断言 |
|---|---|---|---|---|---|
| 1 | `JdbcClusterRegistry.java:112-115`（INSERT lease_expire_at=0L） | :112-115（:115 写 0L） | 无（±0） | mjs 无 pin（JUnit pin） | 是（`TestClusterRegistryConsistencyInvariant.testRegisterNodeVisibilityIsPinnedPerImpl` JDBC 分支断言注册后不可见） |
| 2 | `JdbcClusterRegistry.java:172-174`（getActiveNodes 按 > now 过滤） | :172-174（:174 `lease_expire_at > ?`） | 无（±0） | 同上 | 是 |
| 3 | `InMemoryClusterRegistry.java:68-81`（renewLease 忽略 leaseTimeoutMs） | :68-81（:74 只存时间戳） | 无（±0） | 同上 | 是（`testRenewLeasePerRenewalTimeoutIsPinnedPerImpl` InMemory 分支断言 expireAt-startAt ≈ 15s） |
| 4 | `TwoPhaseCommitSinkFunction.java:83`（saveState 无锁 copy） | :83（`new TreeMap<>(pendingCommits)`） | 无（±0） | mjs pin 命中（`mjs-pins.json[0]`） | 是（`testSaveStatePinsSnapshotContentComplete` 内容完整断言 + 静态扫描违规串完全匹配） |
| 5 | `TwoPhaseCommitSinkFunction.java:76-78`（setPendingCommits 接受任意 Map） | :76-78 | 无（±0） | mjs 无 pin（按「字段声明」口径无静态违规） | 是（`TestTwoPhaseCommitSinkFunction` restore 路径覆盖） |

**处置结论**：无行为漂移 → 无 blocked 升级；无 stale pin → 无 pin 移除；无新增 unpinned 违规 → 无新增 red list 项。

---

## 1. red list 主体（I0 catalog §6 候选，I2 复核确认在册）

### RL-1. JdbcClusterRegistry.registerNode 写 lease_expire_at=0L（catalog #1）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java:112-115`
  （INSERT 语句 :115 仍写 `lease_expire_at=0L`）
- **关联不变式**：#5（ClusterRegistry 多实现语义一致性 — registerNode 后节点必须对 getActiveNodes 立即可见）
- **关联 finding**：R16-AR-9（`2026-05-31-adversarial-review-nop-stream-r16/01-open-findings.md`）
- **族标注**：F5（ClusterRegistry 族，已知族兄弟实例，finding-ID R16-AR-9）
- **门禁/pin 来源**：`TestClusterRegistryConsistencyInvariant.testRegisterNodeVisibilityIsPinnedPerImpl`
  JDBC 分支显式断言「注册后 getActiveNodes 为空」= 差异 pin（I1）；本次复核行为未漂移。
- **验证结论**：live 确认 :112-115 INSERT 写 0L，renewLease 后可见（per-renewal 生效路径绿）；差异属实。
- **裁决输入**：修复方向 = registerNode INSERT 写 `now + leaseTtlMs` 而非 0L（I1 red-list 记录）；
  行为影响 = 新注册节点在首次 renewLease 前对调度器不可见。

### RL-2. JdbcClusterRegistry.getActiveNodes 按 > now 过滤（catalog #2）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java:172-174`
  （:174 `WHERE lease_expire_at > ?`，now 为查询时刻）
- **关联不变式**：#5
- **关联 finding**：R16-AR-9（与 RL-1 同 finding，两个命中点）
- **族标注**：F5（已知族，R16-AR-9）
- **门禁/pin 来源**：与 RL-1 同一 pin 测试；本次复核 :172-174 无漂移。
- **验证结论**：与 RL-1 构成因果链——INSERT 写 0L ⇒ 过滤条件立即排除新节点。
- **裁决输入**：与 RL-1 联合裁决/修复（同一 finding 的互为表里两处）。

### RL-3. InMemoryClusterRegistry.renewLease 忽略 leaseTimeoutMs（catalog #3）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:68-81`
  （:74 只存 `now`，忽略 per-renewal `leaseTimeoutMs` 参数）；`:90/:98/:114` 仍用固定 `leaseTtlMs` 计算活性
- **关联不变式**：#5（renewLease 必须按 per-renewal 参数计算过期时间）
- **关联 finding**：R16-AR-18
- **族标注**：F5（已知族，R16-AR-18）
- **门禁/pin 来源**：`TestClusterRegistryConsistencyInvariant.testRenewLeasePerRenewalTimeoutIsPinnedPerImpl`
  InMemory 分支显式断言「expireAt - startAt ≈ 15s 而非 5s」= 差异 pin（I1）；本次复核无漂移。
- **验证结论**：live 确认 renewLease 忽略 leaseTimeoutMs；JDBC 实现已按参数生效（对比面）。
- **裁决输入**：修复方向 = renewLease 记录 `now + leaseTimeoutMs` 并按参数计算活性（I1 red-list 记录）；
  影响 = InMemory 语义与 JDBC 不一致（两实现语义一致性契约 violated）。

### RL-4. TwoPhaseCommitSinkFunction.saveState 无锁 copy（catalog #4）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:83`
  （`new TreeMap<>(pendingCommits)` 无 synchronized 块）
- **关联不变式**：#2（synchronized 集合迭代点必须在 synchronized 块内）
- **关联 finding**：R16-AR-1
- **族标注**：F2（SinkFunction 族，已知族，R16-AR-1）
- **门禁/pin 来源**：mjs `mjs-pins.json` pinnedViolations[0]（静态 pin，违规串精确匹配）
  + `TestSynchronizedCollectionInvariant.testSaveStatePinsSnapshotContentComplete`（行为 pin：快照内容完整，
  锁状态不在此断言）。两 pin 均未 stale、未漂移。
- **验证结论**：live :83 确认无锁 copy；并发下 saveState 与 commit/abort 路径对 pendingCommits 的
  结构性并发访问未加锁（内容完整性被行为 pin 覆盖，原子性未被 pin）。
- **裁决输入**：修复方向 = saveState 内 `synchronized (pendingCommits)` 包裹 copy；影响 = 并发 snapshot
  期间可能读到中间态或触发 CME 的窗口（行为 pin 证明当前快照内容一致，未证明并发安全）。

### RL-5. TwoPhaseCommitSinkFunction.setPendingCommits 接受任意 Map（catalog #5）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:76-78`
  （setter 未包装 synchronizedMap；调用方 `StreamSinkOperator.java:157` restore 路径已主动包装，部分缓解）
- **关联不变式**：#2
- **关联 finding**：R16-AR-11
- **族标注**：F2（已知族，R16-AR-11）
- **门禁/pin 来源**：行为由既有 `TestTwoPhaseCommitSinkFunction` restore 路径覆盖；锁状态由 mjs
  扫描器按「字段声明」口径兜底——本 setter 不声明 synchronized 字段，故无静态违规。
- **验证结论**：live :76-78 确认 setter 直接赋值；当前唯一调用方已包装，但 setter 契约本身不防护
  未来调用方传入非同步 Map（watch-only 于 I1，I2 复核维持 in-scope red list——setter 防护必要性成立，
  因 restore 路径外存在其它潜在调用面，且契约未文档化）。
- **裁决输入**：修复方向 = setter 内部包装 `Collections.synchronizedMap(new TreeMap<>(pending))`
  或文档化调用方契约（I1 red-list 记录）。

### RL-6. WindowOperator onEventTime cleanup 未 retire 合并窗口（R15-AR-8 升格，I2 新增）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:773-782`
  （onEventTime cleanup 分支：clearWindowContents + triggerContext.clear()，但未调用
  `mergingWindows.retireWindow(stateWindow)`）
- **关联不变式**：#1 语义扩展——Window 生命周期收敛（cleanup 后 MergingWindowSet 映射必须收敛，
  无窗口状态泄漏；对照 Flink onEventTime cleanup 的 retireWindow 语义）
- **关联 finding**：R15-AR-8（`2026-05-31-adversarial-review-nop-stream-r9/01-open-findings.md` 头部
  确认表，原判 fixed 失准）
- **族标注**：F1（Operator 族，已知族新实例）
- **门禁/pin 来源**：I2 Phase 2 动态验证 PIN 测试
  `TestWindowOperatorMergingCleanupInvariant.testSessionWindowCleanupRetainsMergingWindowIsPinned` +
  `testSessionWindowCleanupAndLaterElementMergesIntoStaleRangeIsPinned`（2/2 绿，测试本身断言当前
  泄漏行为作为回归基线；I4 修复后断言翻转）。
- **验证结论**：动态验证确认——cleanup 后 MergingWindowSet 仍保留 [10,80)→[10,60) 映射并 persist
  入 checkpoint 状态（映射无界增长 = 窗口状态泄漏）；cleanup 后到达的重叠元素（t=55）合并进 stale
  范围生成 [10,105) 而非 [55,105)（被清理窗口范围复活）。静态对照：:656-658 isWindowLate 路径
  的 retire 只覆盖迟到元素，不覆盖 cleanup timer 路径。
- **裁决输入**：修复方向（I4）= onEventTime cleanup 分支补 `mergingWindows.retireWindow(stateWindow)`；
  影响 = 长跑任务 checkpoint 状态无界增长 + cleanup 后新窗口范围错误；I3 需裁决严重度（建议 ≥P2，
  状态增长为渐进性）。

---

## 2. watch-only residual 裁定（I2 Phase 2 动态验证结果）

> 每项含 场景描述 / 验证方式 / 结果 / 裁定结论。验证载体：git-committed focused tests（绿）。

### WO-1. LocalFileCheckpointStorage 按文件名 ID 排序（AR-15）→ **verified（移除 watch-only）**

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java:116-138`
  （getLatestCheckpoint 按文件名 extractCheckpointId 排序，:127-131）
- **场景**：乱序写入多个 checkpoint 的混合 ID 场景——写 300 → 100 → 250 → 200（非单调、非按写入序），
  验证 getLatestCheckpoint 选取 max-ID（300）而非最后写入的（200）；删除 300 后回退到 200（而非 mtime 最新）。
- **验证方式**：git-committed focused test
  `TestLocalFileCheckpointStorage.testGetLatestCheckpointSelectsMaxIdUnderOutOfOrderWrites` +
  `testGetLatestCheckpointAfterDeletingMaxId`（nop-stream-runtime/src/test，2026-08-12，13/13 绿）。
- **结果**：max-ID 排序选取正确（乱序混合 ID 下仍取 300；`getLatestCheckpoints(2)` 返回 [300, 250] 降序；
  删除 max 后回退下一 max）。**级联解除的论证链完整** = 单调恢复（`CheckpointCoordinator.java:896-900`
  单调推进守卫，live 复核 + 门禁③ `TestCheckpointIDCounterInvariant` 佐证）+ 排序选取正确（本次动态验证）。
- **裁定**：**verified** —— 移除 watch-only 标注；不升格 red list。级联（恢复选错 ID）已由
  单调 ID + 正确排序双重解除。

### WO-2. WindowOperator onEventTime cleanup 未见 retireWindow（R15-AR-8）→ **升格 red list（RL-6）**

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:773-783`
  （onEventTime cleanup 分支 :773-782 只做 clearWindowContents + triggerContext.clear()，**未调用
  `mergingWindows.retireWindow(stateWindow)`**；I1 记录 :776-783，本次复核 live 以 :773-783 为准）
- **场景**：session gap 50 的 merging window——元素 t=10 建 session [10,60)，t=30 合并为 [10,80)
  （maxTimestamp=79，cleanup timer 在 79）；advance watermark 至 79 → trigger fire（sum=30 输出）+
  cleanup 分支执行。
- **验证方式**：git-committed focused test
  `TestWindowOperatorMergingCleanupInvariant.testSessionWindowCleanupRetainsMergingWindowIsPinned` +
  `testSessionWindowCleanupAndLaterElementMergesIntoStaleRangeIsPinned`
  （nop-stream-runtime/src/test，2026-08-12，2/2 绿）。
- **结果**：**泄漏确认**——cleanup 后 MergingWindowSet 仍保留 [10,80)→[10,60) 映射条目（persist 到
  checkpoint 状态），映射不收敛（无界增长）；且 cleanup 后到达的重叠元素（t=55）会合并进 stale
  范围 [10,80)，生成 [10,105) 而非 [55,105)——被清理窗口范围复活。对照 Flink 语义：cleanup 分支
  应 `retireWindow(stateWindow)` 使映射收敛。
- **裁定**：**升格 red list（RL-6，新条目）** —— 移除 watch-only；修复方向（I4）= onEventTime
  cleanup 分支补 `mergingWindows.retireWindow(stateWindow)`。
- **证据对照**：R16 修复确认表 R15-AR-8 原判 fixed 失准——原修复（:644 triggerContext.onMerge +
  :656-658 isWindowLate retire 路径）只覆盖合并与迟到元素路径，未覆盖 cleanup timer 路径。

### WO-3. InputGate per-id inFlightAlignments（R15-AR-9）→ **verified（移除 watch-only）**

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:625-674`
  （handleBarrierNonRecursive per-id inFlightAlignments；maxConcurrentCheckpoints>1 语义）
- **场景**：maxConcurrentCheckpoints>1 重叠 barrier——(a) aligned 模式：barrier 6 在 ch0 排队于
  barrier 5 之后（ch0 被 align(5) 阻塞时 b6 已写入），align(5) 完成后 b6 必须**全新**对齐
  （不继承 align(5) 的 receivedChannels/blockedChannels）；(b) AT_LEAST_ONCE 模式：id 5/6 跨
  channel 交错到达（5→ch0, 6→ch1, 5→ch1, 6→ch0），各 id 独立跟踪。
- **验证方式**：git-committed focused tests
  `TestInputGateMultiEpochBarrier.testAlignedOverlapBarrierQueuedOnBlockedChannelAlignsFreshly` +
  `testAtLeastOnceOverlappingIdsTrackedPerIdWithoutCrossTalk`
  （nop-stream-core/src/test，2026-08-12，5/5 绿）。
- **结果**：aligned 重叠场景两 id 按序各发射一次、无残留 in-flight 状态；AT_LEAST_ONCE 交错场景
  各 id 独立对齐（首达即发射、重复合并、全达即移除），无串扰、无静默丢弃、无状态泄漏。
  **端到端路径走通**：ResultPartition.write → InputChannel → handleBarrierNonRecursive → read()
  返回对齐完成结果（非组件级孤立断言）。
- **裁定**：**verified** —— 移除 watch-only 标注；不升格 red list。

---

## 3. 聚焦对抗探查发现（I2 Phase 3）

> 完整探查报告：`ai-dev/audits/nop-stream-invariants/I2-probing-report.md`（Phase 3 产出）。
> 本节仅收录进入 red list 的探查发现（如有）；非 defect 的优化级发现在探查报告中登记，不阻塞移交。

### RL-7. ChainingOutput 静默丢弃 side-output（R15-AR-4 确认仍 live，I2 探查新增）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ChainingOutput.java:84-86`
  （`collect(OutputTag, record)` 仅 LOG.warn + 丢弃）；生产接线 `StreamTaskInvokable.java:171/:209`
- **关联不变式**：新族候选——输出契约族（side-output 转发契约）：「任何 `Output.collect(OutputTag, X)`
  调用必须被转发到注册的 side-output 消费者，不得静默丢弃」
- **对应 finding**：R15-AR-4（`2026-05-31-adversarial-review-nop-stream-r9/01-open-findings.md`，原判 P1，
  R16 确认表「仍存在」；本次 live 复核确认未修复）
- **族标注**：新族（输出契约族）；历史同源 finding-ID R15-AR-4
- **触发证据（`文件:行` 链）**：`WindowOperator.java:1015-1017` sideOutput(lateDataOutputTag) →
  `output.collect(lateDataOutputTag, element)` → `ChainingOutput.java:84-86` 丢弃（仅 LOG.warn）；
  链式接线 `StreamTaskInvokable.java:171/:209`（`new ChainingOutput<>(wiredInput)`）
- **验证/探查结论**：live 确认丢弃点仍在；默认链式部署下 late-data 侧输出与 ProcessWindowFunction
  多输出静默丢失（契约违约，无 fail-fast）；R16-AR-14（OperatorChain.processElement 广播）已随
  类重构消失，但本丢弃点未随重构修复
- **裁决输入**：修复方向（I4）= ChainingOutput.collect(OutputTag,...) 转发至下游 side-output 通道或
  fail-fast；严重度 P1-P2（I3 裁决）；门禁表达候选 = 输出契约族不变式（供 I6 评估 Cycle 2 派生）

---

## 4. 移交声明（I3 裁决输入就绪）

- **零悬挂核对**（I0 catalog §6 全部 8 项有处置）：5 个 red list 候选（RL-1..RL-5）在册；
  I2 新增 RL-6（R15-AR-8 升格）+ RL-7（R15-AR-4 探查确认）；3 个 watch-only 全部逐项裁定——
  WO-1（AR-15）verified、WO-2（R15-AR-8）升格 RL-6、WO-3（R15-AR-9）verified。**零悬挂达成**。
- 每条 red list 项含：位置（`文件:行`）+ 关联不变式 + 关联 finding-ID + 族标注 + 验证/探查结论 +
  修复方向参考 —— I3 可直接逐条裁决严重度（P0-P3）与派发。
- 门禁保持全绿（pin-and-record 语义下，I2 未引入新 CI 红；mjs `all` exit 0 + 五族 JUnit 0 failures 复验在案）。
- I2 未改动任何被测类代码（Non-Goals 遵守）；全部结论基于 live 代码 + 测试载体。
- 顺带同步：I0 catalog §3 的 R15-AR-8 行号引用（:776-783 → :773-783，live 行号以本次复核为准）——由 Phase 4 写入 catalog。

---

## 5. 修复状态（I4 执行结果，2026-08-12）

> I4 plan `2026-08-12-1217-5-nop-stream-invariants-cycle1-I4-fix-execution.md` 执行完毕（test-first 先红后绿 + 类别清扫 + 门禁复跑零命中）。全部 7 条 RL（含触发闭合的 RL-3）已修复；RL-1..7 修复证据见下。WO 部分（§2）为 I2 历史裁定记录，不动。

| RL | 严重度 | 修复 commit | 翻转/新增测试（先红后绿） | 门禁复跑 |
|---|---|---|---|---|
| RL-1（JDBC INSERT 0L）+ RL-2（过滤互为表里） | P1 | `fcc71fc05` | `TestClusterRegistryConsistencyInvariant.testRegisterNodeVisibilityIsPinnedPerImpl`（JDBC 分支翻转）+ 新增 `testJdbcReregisterAfterLeaseExpiryIsImmediatelyVisible`（UPDATE 路径子用例） | 4 失败红 → 10/10 绿 |
| RL-3（InMemory per-renewal） | P2 触发闭合 | `fcc71fc05` | `testRenewLeasePerRenewalTimeoutIsPinnedPerImpl`（InMemory 分支翻转）+ 新增 `testInMemoryRenewLeaseHonorsPerRenewalTimeout` | 同上 |
| RL-4（saveState 无锁 copy） | P0 | `fcc71fc05` | mjs pin（mjs-pins.json 2PC:83）移除后 scan 变红 → 修复后违规清零；新增 `TestSynchronizedCollectionInvariant.testSaveStateConcurrentWithCommitAbortIsSafe` | `mjs all` exit 0；`nop-stream-core` 1418 全绿 |
| RL-5（setPendingCommits 任意 Map） | P1 | `fcc71fc05` | 新增 `testSetPendingCommitsWrapsUnsafeMap`（防御性拷贝确定性断言 + 并发冒烟） | 同上 |
| RL-6（cleanup 不 retire merging window） | P1 | `58255014b` | `TestWindowOperatorMergingCleanupInvariant` 2 pin 断言翻转 + 新增长跑收敛 + processing-time 分支用例（onProcessingTime 同型缺失一并修复） | 4 失败红 → 4/4 绿；WindowRoundTrip 9 / Window E2E 13 全绿 |
| RL-7（ChainingOutput 静默丢弃 side-output） | P1 | `b20fcd0e1` | 新增 `TestSideOutputChainingE2E`（端到端转发 / 无消费者 fail-fast / StreamTaskInvokable 接线 3 用例）——先红 3/3，修复后 3/3 绿 | runtime 804 / core 1418 / cep 320 全绿；`mjs all` exit 0 |

- **修复基线门禁复跑（2026-08-12 实测）**：五族 JUnit 门禁 92 tests 0 failures（原 84 + ClusterRegistry +2 / SynchColl +2 / MergingCleanup +2 + 表完备性不变）；`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS；`node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（无 stale pin、无未 pin 违规）；`mjs-pins.json` pinnedViolations 已清空（2PC:83 修复后移除）。
- **类别清扫结论汇总（四族）**：
  - F5：两实现全部 lease 路径一致（InMemory 5 方法 + JDBC INSERT/UPDATE/renewLease/getActiveNodes/getNodeLease）；`NodeDiscoveryConsistencyChecker` 仅消费 `getActiveNodes()`，视图自动一致；`JdbcLeaderElector`（leader 选举租约）不属本族。
  - F2：`pendingCommits` 全使用点锁路径核对（saveState 修复 + finishCommit/restoreFromEpoch 对照面已同步）；synchronized 集合 4 类全在门禁表；`BatchConsumerSinkFunction` buffer 为文档化 unsynchronized by design（非契约违约）；`recover(:67)` 零调用点死方法记录不修。
  - F1：`MergingWindowSet` 生产消费方仅 WindowOperator；retireWindow 三调用点（:657 既有 + :788/:854 双 cleanup 修复）全部以 mapping key 为参数；生产 MergingWindowAssigner 仅 EventTimeSessionWindows；purge ≠ GC cleanup（Flink 等价语义）不在清扫范围。
  - 输出契约族：`ChainingOutput` 为唯一 in-task 静默丢弃点（已修）；`TimestampedCollector.collect(OutputTag)` 纯转发（非丢弃点）；**跨 task no-op（`RecordWriterOutput` :645 / `BroadcastingRecordWriterOutput` :706）记录为同族已知实例 + 处置依据（线协议结构性变更）→ 移交 I6 人工确认候选**；call-site 全核对（WindowOperator.sideOutput :1029-1031 / CepOperator :482-483/:770）。
- **双轨标注**：RL-7 修复为 Cycle 2 / PD-15（输出契约族不变式，I6 追加）提供 live 修复基线；跨 task 侧输出线协议结构性变更移交 I6 人工确认。
- **RL-3 backlog 状态**：已由 I4 触发闭合（roadmap Follow-up Backlog 条目状态已更新，保留历史处置记录）。

---

## 附：I1 版（pin-and-record）原始记录

> I1 版 red-list（2026-08-12）以 pin-and-record 语义登记 4 条 residual + 3 条 watch-only，本权威版
> 由 I2 复核升级：4 条 residual 全部 live 复核在册（§1 RL-1..5）、3 条 watch-only 动态验证裁定（§2，
> 其中 R15-AR-8 升格为新条目 RL-6）。原记录中每条的「I2 处理」路径已并入对应 RL 项的「裁决输入」。
