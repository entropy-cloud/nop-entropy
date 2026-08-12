# Cycle 1 / I4 — 修复执行（实例 + 类别清扫 + 测试）（Fix Execution: Instance + Category Sweep + Tests）

> Plan Status: active
> Last Reviewed: 2026-08-12
> Draft Review: 4 轮独立子 agent 对抗性审查通过（round 1：4 Major + 8 Minor；round 2：1 Blocker（`-pl` 裸模块名）+ 1 Major（Phase 4 测试落点）+ 6 Minor；round 3：1 Blocker（restoreFromEpoch 方法事实写反）+ 1 Major（RL-1 漏 UPDATE 分支）+ 2 Minor；round 4：4/4 修复验证 PASS、无新问题、verdict approved）
> Source: I3 裁决表 `ai-dev/audits/nop-stream-invariants/adjudication-table.md` §3「P0/P1 派发清单」（WI-1..4）+ §4 PD-15；I2 权威版 `ai-dev/audits/nop-stream-invariants/red-list.md`（RL-1..7）；roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Work Item I4；mission `nop-stream-invariant-loop` 授权声明
> Related: 前置 `2026-08-12-1217-4-nop-stream-invariants-cycle1-I3-adjudication.md`（I3，硬串行）；后续 `2026-08-12-1217-6-nop-stream-invariants-cycle1-I5-full-verification.md`（I5，全量验证）；I6 收口（以本 plan 修复结果 + PD-15 为输入）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 1 / I4. 修复执行（实例 + 类别清扫 + 测试）

## Purpose

把 I3 裁决派发的 4 个 I4 工作项（WI-1..4：RL-1+RL-2 / RL-4+RL-5 / RL-6 / RL-7）全部按「实例修复 + 类别清扫 + test-first 先红后绿 + 门禁复跑零命中」执行完毕，并顺带触发闭合 Follow-up Backlog 中被 F5 族类别清扫激活的延期项 RL-3（P2，触发条件 = 「I4 修 F5 族（WI-1）类别清扫时顺手覆盖」）。本 plan 结束时：red list 7 条 RL 全部以 live 代码修复 + 聚焦测试覆盖 + 门禁复跑全绿，pin-and-record 注册表（`mjs-pins.json` + JUnit pin 测试）与 live baseline 同步，为 I5 全量验证与 I6 收口提供已修复基线。

## Current Baseline

（2026-08-12 live 复核，全部位置已实测确认，与 I2 red list 权威版一致）

- **I3 已 completed**：裁决表零悬挂（7 RL + 3 WO），P0/P1 全部派 I4（4 工作项），显式声明「无『需人工确认』门」（均为类内部行为修复，`Output#collect(OutputTag,X)` 接口已存在，RL-7 修复的是实现侧违约）；RL-3（P2）入 Follow-up Backlog，触发条件在案；PD-15（输出契约族）派生登记移交 I6。
- **live 缺陷全部确认在册**（I2 权威版行号实测一致）：
  - RL-1 `JdbcClusterRegistry.java:112-115` INSERT 写 `lease_expire_at=0L`（P1）；RL-2 `:172-174` `WHERE lease_expire_at > ?`（P1）——因果链：0L ⇒ 新注册节点首次 renewLease 前对调度器不可见。
  - RL-3 `InMemoryClusterRegistry.java:68-81` renewLease 只存 `now` 忽略 `leaseTimeoutMs`；`:90/:98/:114` 活性计算全部用固定 `leaseTtlMs`（P2，backlog，本次触发）。
  - RL-4 `TwoPhaseCommitSinkFunction.java:83` `new TreeMap<>(pendingCommits)` 无 synchronized（P0）；RL-5 `:76-78` setter 接受任意 Map 不包装（P1）。
  - RL-6 `WindowOperator.java:773-782` onEventTime cleanup 分支（clearWindowContents + triggerContext.clear()）未调用 `retireWindow`（P1）；onProcessingTime 分支 :834-843 附近同型待清扫。**参数要点（实测）**：`MergingWindowSet` 的 mapping 形如 `{[10,80) → [10,60)}`（key = in-flight window，value = state window）；`MergingWindowSet.retireWindow(window)` 按 **key** 移除且 key 不存在时抛 `StreamException`（`MergingWindowSet.java:134-139`）——cleanup timer namespace（`triggerContext.window`）即 key，故须 retire **key 而非 stateWindow value**。
  - RL-7 `ChainingOutput.java:84-86` `collect(OutputTag, record)` 仅 LOG.warn + 丢弃（P1）；接线 `StreamTaskInvokable.java:171/:209`；call-site `WindowOperator.java:1015-1017` sideOutput(lateDataOutputTag)。**同族清扫已知实例（live 确认）**：跨 task 边界的 `RecordWriterOutput.collect(OutputTag,...)`（`StreamTaskInvokable.java:621-623`）与 `BroadcastingRecordWriterOutput.collect(OutputTag,...)`（`:681-682`）均为静默 no-op（跨 task 无线协议支持，见 Phase 4 处置规则）；`CepOperator` 侧输出亦经同一 Output 链。
- **门禁现状（pin-and-record，全绿但带 pin）**：mjs `check-nop-stream-invariants.mjs all` exit 0（`all` 模式下全绿无 stdout 输出；unpinned/stale 计数仅在非零时打印），`mjs-pins.json` 唯一 pin = `TwoPhaseCommitSinkFunction.java:83`（RL-4 静态违规串）；JUnit 五族门禁 84 tests 全绿（`TestWindowRoundTripInvariant` 9 / `TestSynchronizedCollectionInvariant` 10 / `TestCheckpointIDCounterInvariant` 8 / `TestCepReleaseSymmetryInvariant` 21 / `TestClusterRegistryConsistencyInvariant` 8 / 3 模块表完备性 10+11+7）；行为 pin 测试存在：`TestClusterRegistryConsistencyInvariant.testRegisterNodeVisibilityIsPinnedPerImpl`（JDBC 分支断言注册后不可见）、`testRenewLeasePerRenewalTimeoutIsPinnedPerImpl`（InMemory 分支断言 expireAt-startAt ≈ 15s）、`TestWindowOperatorMergingCleanupInvariant` 2 测试（泄漏行为 pin）、`TestSynchronizedCollectionInvariant.testSaveStatePinsSnapshotContentComplete`（快照内容完整，锁状态未 pin）。
- **Maven 模块路径事实（实测）**：nop-stream 各子模块嵌套于 `nop-stream/` 聚合 pom（root reactor 仅含 `nop-stream` 一个模块）——`-pl` 必须用嵌套路径（`-pl nop-stream/nop-stream-runtime` 等）或 `:artifactId` 选择器；`-pl nop-stream-runtime` 从仓库根执行会报 `The requested required projects ... do not exist`（已实测复现）。**依赖前提**：带 `-Dtest` 过滤的显式模块命令不带 `-am`（`-am` + 过滤会在无匹配测试的模块触发 failIfNoSpecifiedTests），依赖本地仓库已有 nop-stream 各模块 SNAPSHOT（由历史 `mvn install` 提供）；如遇依赖不可解析，先跑 `./mvnw install -pl nop-stream -am -DskipTests -T 1C` 补装。
- **checkstyle 事实（实测）**：root pom 的 checkstyle 插件默认配置整体注释（`pom.xml:140-162`），唯一生效入口是 `qa` profile（`pom.xml:471+`，`./mvnw checkstyle:check -Pqa`，configLocation=`${maven.multiModuleProjectDirectory}/checkstyle.xml`，failOnViolation=false + consoleOutput=true——以输出中无新 violation 为准）。
- **类别清扫事实**：`MergingWindowSet.retireWindow` 已存在（`MergingWindowSet.java:134`）；`WindowOperator.java:657` isWindowLate 路径已用 retireWindow（仅覆盖迟到元素路径，不覆盖 cleanup timer 路径）；`NodeDiscoveryConsistencyChecker` 存在（cluster 包，需纳入 F5 清扫核对）；`TimestampedCollector.collect(OutputTag,...)` 纯转发（:97），非丢弃点；仓库无任何「side-output 消费者注册」机制（OutputTag 仅在 ChainingOutput/TimestampedCollector/Output + WindowOperator 族出现）——RL-7 修复需引入最小消费者接线。
- **真正剩余的 gap**：7 条 RL 的代码修复 + 测试翻转 + 类别清扫 + pin 注册表同步 + RL-3 触发闭合。

## Goals

- RL-1..7 全部 live 修复（WI-1..4 逐一落地，无空壳：修复后原 pin 测试断言翻转绿、静态扫描违规清零）。
- RL-3（backlog 延期项）随 F5 族类别清扫触发闭合：InMemoryClusterRegistry 按 per-renewal `leaseTimeoutMs` 计算过期/活性，与 JDBC 实现语义一致（不变式 #5）。
- 类别清扫强制：每个族修复均产出 grep 全类兄弟核对证据（roadmap「类别清扫强制」），清扫范围按 I3 裁决表 §3 逐条执行。
- test-first 先红后绿：每个修复先翻转/新增断言为红（或静态 pin 移除后 scan 变红），再改代码转绿；证据入 plan + daily log。
- 门禁复跑零命中：JUnit 五族 + 3 表完备性 + mjs `all` exit 0；`mjs-pins.json` 移除 2PC:83 pin（修复后违规不存在 → stale-pin 语义要求移除）；JUnit pin 测试断言翻转（不再 pin 缺陷行为）。
- 文档同步：invariant-catalog 行号引用、red-list 状态（RL 修复证据）、gate-inventory、roadmap I4 状态流转与 RL-3 backlog 状态更新、daily log。
- roadmap Work Item I4 状态流转（`todo`→`planned`→`done`）。

## Non-Goals

- **不做 I5 的全量验证收口**（`./mvnw test -pl nop-stream -am -T 1C` 全绿记录、相关 e2e 汇总、full-green 记录属 `2026-08-12-1217-6-...`）。本 plan 只跑聚焦门禁复跑 + WI-4 要求的全量测试跑（I3 裁决表 WI-4 门禁复跑要求明确含全量）。
- **不建 Cycle 2 门禁**（输出契约族不变式沉淀属 Cycle 2 / I1，I6 按 PD-15 正式追加后执行）。
- **不裁决新族 / 不评估非族候选**（R13-AR-9 / R16-AR-19,20 / R8-AR-59 / R16-AR-13 的 I2 评估存档属 I6 Loop Rule 输入，本 plan 不触碰）。
- **不修复不在 red list 的发现**（I2 probing report PR-2 及以下未入 red list 的优化级发现，不因本 plan 顺手修改；如需登记走 I6）。**例外**：类别清扫（roadmap 强制）发现的**同类 in-task 静默丢弃实例**属类别清扫范围，按 Phase 4 处置规则修复或记录移交（跨 task 线协议类结构性变更移交 I6 人工确认，不静默忽略也不越权修）。
- **不引入公共 API / 模块边界 / Operator 接口变更**（I3 裁决表 §3 已显式声明无「需人工确认」门；RL-7 若需新增 side-output 消费者注册点，必须是 ChainingOutput 内部/链式接线层面的最小新增，不改 `Output` 接口契约）。

## Scope

### In Scope

- WI-1：族 F5 ClusterRegistry（RL-1 + RL-2 联合，R16-AR-9）+ 触发闭合 RL-3（R16-AR-18）。
- WI-2：族 F2 TwoPhaseCommitSinkFunction（RL-4 P0 + RL-5 P1，R16-AR-1 + R16-AR-11）。
- WI-3：族 F1 WindowOperator cleanup 收敛（RL-6，R15-AR-8）。
- WI-4：输出契约族 ChainingOutput side-output 转发（RL-7，R15-AR-4，双轨 PD-15 移交 I6）。
- 每族类别清扫（按裁决表 §3 清扫范围逐条执行并留证据）。
- 门禁 pin 注册表同步（`mjs-pins.json` 移除 2PC:83；JUnit pin 测试断言翻转；gate-inventory 更新）。
- 文档同步（catalog / red-list / roadmap / daily log）。

### Out Of Scope

- I5 全量验证收口与 e2e 汇总记录（plan 6）。
- Cycle 2 / I1 新门禁建设与 roadmap 正式追加（I6）。
- 非 red list 的探查发现与非族候选评估。
- 结构性重构（公共 API / 模块边界 / Operator 接口变更）。

## Execution Plan

### Phase 1 - 族 F5：ClusterRegistry 注册即可见 + per-renewal 租约语义（WI-1 + RL-3）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java`、`.../cluster/InMemoryClusterRegistry.java`、`.../cluster/NodeDiscoveryConsistencyChecker.java`、`nop-stream-runtime/src/test/java/io/nop/stream/runtime/cluster/TestClusterRegistryConsistencyInvariant.java`

- Item Types: `Fix`

- [x] **test-first 先红**：翻转 `TestClusterRegistryConsistencyInvariant.testRegisterNodeVisibilityIsPinnedPerImpl` JDBC 分支断言（注册后 getActiveNodes 立即可见）；翻转 `testRenewLeasePerRenewalTimeoutIsPinnedPerImpl` InMemory 分支断言（expireAt-startAt ≈ per-renewal `leaseTimeoutMs` 参数，如 5s，而非固定 15s）；新增一致性用例——InMemory renew 自定义 timeout 后活性按参数计算（覆盖 getActiveNodes / evictExpiredNodes / getNodeLease；含自定义 timeout 到期后淘汰、未到期活性）+ JDBC「注册 → 短租约过期 → 重新注册（UPDATE 路径）→ 立即可见」子用例（覆盖 UPDATE 分支不刷新 lease_expire_at 的同一违规）；「注册后立即可见」语义由翻转的 JDBC 分支断言覆盖（InMemory 注册后恒可见与 ttl 无关，不另设冗余用例）；跑 `./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestClusterRegistryConsistencyInvariant` 确认**翻转断言红**（新增用例红/绿皆可——「自定义 timeout 到期后淘汰」子断言在现固定 15000ms 代码下即红；**先红载体 = 翻转的 pin 断言**；如实记录红输出）。**红输出记录（2026-08-12 实测）**：`Tests run: 10, Failures: 4`——`testRegisterNodeVisibilityIsPinnedPerImpl (jdbc)` expected 1 but was 0（:119）；`testJdbcReregisterAfterLeaseExpiryIsImmediatelyVisible` expected 1 but was 0（:172，UPDATE 分支不刷新）；`testRenewLeasePerRenewalTimeoutIsPinnedPerImpl (inmemory)` expireAt 断言 false（:136）；`testInMemoryRenewLeaseHonorsPerRenewalTimeout` :153 自定义 300ms timeout 到期后仍活性（固定 15s 兜底）
- [x] **RL-1 修复**（`JdbcClusterRegistry.registerNode`）：**INSERT 分支**（:112-115）`lease_expire_at` 写有效租约到期时间（`now + 15000L`），不再写 0L；**UPDATE 分支**（:106-110，exists → UPDATE `SET endpoint/capacity/last_heartbeat_at`，当前不刷新 `lease_expire_at`）同样写入 `now + 15000L`——节点重启后重新注册走 UPDATE 路径，不刷新则 `lease_expire_at` 停留旧值（0L 或已过期）→ 注册后仍不可见，与 RL-1 同一违规；**默认租约时长固定为 15000L**（= InMemory `LEASE_TIMEOUT_MS` 默认值 = `TaskManager.DEFAULT_LEASE_TIMEOUT_MS`，维持两实现同语义）；renewLease UPDATE 语义不变；getActiveNodes 过滤（`:174` `lease_expire_at > ?`）语义保持不变（RL-2 与 RL-1 互为表里，INSERT/UPDATE 修复后即成立，不单独改过滤）
- [x] **RL-3 修复**（`InMemoryClusterRegistry`）：renewLease 记录按 `leaseTimeoutMs` 参数计算的过期时间而非固定 `leaseTtlMs`；registerNode 默认租约按 `leaseTtlMs` 计算；`getNodeLease` / `evictExpiredNodes` / `getActiveNodes` 活性判定全部与存储的过期时间一致（按参数而非固定 ttl）——**禁止静默跳过**：任一方法若无法按参数计算，必须抛异常而非吞掉
- [x] **类别清扫（F5 族）**：grep `ClusterRegistry` 全部实现与 lease 相关方法（`InMemoryClusterRegistry.registerNode/renewLease/getActiveNodes/evictExpiredNodes/getNodeLease` + `JdbcClusterRegistry` 全部 lease SQL 点 INSERT/UPDATE/SELECT）；同步核对 `NodeDiscoveryConsistencyChecker` 活性/可见性判定逻辑与两实现一致；清扫证据（grep 清单 + 逐点核对结论）写入本 plan 或 daily log。**清扫证据（2026-08-12）**：全族 grep 见下方 §类别清扫证据汇总；逐点核对——InMemory 5 方法全部读存储的 expireAt（无固定 ttl 残留）；JDBC INSERT/UPDATE/renewLease 三 SQL 点均写有效租约，getNodeLease（mapLeaseInfo `active = leaseExpireAt > now`）与 getActiveNodes（`lease_expire_at > ?`）判定一致；`NodeDiscoveryConsistencyChecker` 只消费 `getActiveNodes()`（无独立 lease 计算），其可见性视图随两实现修复自动一致；`JdbcLeaderElector` 为 leader 选举独立租约（非 ClusterRegistry 族），不属清扫范围；`StreamNodeAutoRegistration` 仅接平台 discovery（`namingService.registerInstance`），不涉及 ClusterRegistry lease，兼容
- [x] **test-first 后绿**：跑 `./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestClusterRegistryConsistencyInvariant` 全绿（含翻转 + 新增用例）；两实现同语义断言保持（不变式 #5）。**绿输出（2026-08-12 实测）**：`Tests run: 10, Failures: 0, Errors: 0`（原 8 参数化 + 新增 2 一致性用例）；同族回归 `TestInMemoryClusterRegistry` 7 + `TestInMemoryClusterRegistryFixes` 9 + `TestJdbcClusterRegistry` 21 全绿；`node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] RL-1/RL-2 修复落地：JDBC registerNode 后节点立即对 getActiveNodes 可见（翻转后的 pin 测试绿；**INSERT 与 UPDATE 双分支均写入有效租约**——「注册 → 短租约过期 → 重新注册（UPDATE 路径）→ 立即可见」子用例绿）
- [x] RL-3 触发闭合：InMemory renewLease 按 `leaseTimeoutMs` 参数计算过期时间，活性计算与存储一致（翻转后的 pin 测试绿 + 新增一致性用例绿）
- [x] 类别清扫证据存在（ClusterRegistry 全实现 lease 路径 + NodeDiscoveryConsistencyChecker 核对结论）
- [x] `TestClusterRegistryConsistencyInvariant` 全绿；`node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0
- [x] 无静默跳过：InMemory 修复无空分支/吞异常路径（code review 核对）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No owner-doc update required for this phase（行为契约不变；`invariant-catalog.md` 行号引用同步归 Phase 5 收口）

### Phase 2 - 族 F2：TwoPhaseCommitSinkFunction 并发防护（WI-2：RL-4 P0 + RL-5 P1）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java`、`nop-stream-core/src/test/java/io/nop/stream/core/common/functions/sink/TestSynchronizedCollectionInvariant.java`、`ai-dev/audits/nop-stream-invariants/mjs-pins.json`

- Item Types: `Fix`

- [x] **test-first 先红**：从 `mjs-pins.json` 移除 2PC:83 pin → `node ai-dev/tools/check-nop-stream-invariants.mjs scan-iterations` 必须变红（**确定性红 = mjs pin 移除后的 scan 变红**；setter 防护断言与并发用例修复前不一定红——单线程行为等价 + CME 概率性，属补充性验证而非先红载体，如实记录即可）；在 `TestSynchronizedCollectionInvariant` 新增 setter 防护断言（setPendingCommits 传入非同步 Map 后，类内迭代路径行为仍安全/等价）；新增并发用例：saveState 与 commit/abort 线程并发下无 CME 且快照完整（复用/扩展 `TestTwoPhaseCommitSinkFunction` 或新建并发测试类；并发断言建议确定性化——固定多轮压测或注入确定性交错）。**红输出记录（2026-08-12 实测）**：`scan-iterations: 1 violation(s)`（`TwoPhaseCommitSinkFunction.java:83 iteration of synchronized collection 'pendingCommits' (copy-constructor) outside synchronized block`）exit 1——确定性红达成
- [x] **RL-4 修复**：saveState 的 copy 放入 `synchronized (pendingCommits)` 块内完成（:83）
- [x] **RL-5 修复**：setPendingCommits 内部包装 `Collections.synchronizedMap(new TreeMap<>(pending))`（I3 裁决表二选一中的首选；选择后记入 plan/daily log，且调用方 `StreamSinkOperator.java:157` 既有包装不重复即可、语义兼容——双层包装无害；全仓无调用方传 null，null 时 TreeMap ctor NPE = fail-loud 而非静默）
- [x] **类别清扫（F2 族）**：grep 全类 `pendingCommits` 全部使用点逐一核对锁路径（实测使用点 = 字段 :31、ctor :34、getPendingCommits :73、setPendingCommits :81 + `saveState` :87-92 + `finishCommit` 经 `getPendingCommits()` 的 :101-118 已 synchronized + **`restoreFromEpoch`（:153，实现 `CheckpointParticipant` 接口，生产调用方 `GraphModelCheckpointExecutor`，4+ 测试文件引用）内的 :154 使用点及其迭代块 :158-167/:178-180/:190-192——其中 :158 已 synchronized，是与 saveState 修复后锁一致性核对的对照面**；`recover(long checkpointId)`（:67）为**零调用点死方法**——记录，不修复）；grep 全部 `Collections.synchronized*` 字段迭代点对照不变式 #2 表（live = 4 类：TwoPhaseCommitSinkFunction / StreamSinkOperator:157 / SourceReaderOperator:98 / LocalSourceCoordinator:167，全部在门禁表内且 mjs 全量扫描覆盖）；grep 同类 SinkFunction 兄弟（`StreamSinkOperator` restore 路径 :146-158、`BatchConsumerSinkFunction` buffer 路径）核对是否同型缺陷——BatchConsumerSinkFunction buffer 为普通 ArrayList + 文档显式声明「designed for concurrent invocation...unsynchronized by design」（单线程执行模型，非 synchronized 集合契约违约，不在不变式 #2 范围）；清扫证据留档
- [x] **test-first 后绿**：移除 pin 后 `scan-iterations` 绿（违规清零 → `violations ⊆ pins` 恒真）；`TestSynchronizedCollectionInvariant`（10→12 新增）全绿；并发用例绿；`./mvnw test -pl nop-stream/nop-stream-core` 全量绿。**绿输出（2026-08-12 实测）**：`node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（违规清零 + 无 stale pin——pin 移除后 mjs 全绿证明修复落地）；`TestSynchronizedCollectionInvariant` 12/12 绿；`./mvnw test -pl nop-stream/nop-stream-core` 1418 tests 0 failures BUILD SUCCESS

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] RL-4 修复：saveState copy 在 synchronized 块内（静态扫描 2PC:83 违规消失，无需 pin）
- [x] RL-5 修复：setter 防护断言绿（非同步 Map 传入后迭代路径等价安全）
- [x] 并发用例绿（saveState vs commit/abort 并发：无 CME、快照完整）
- [x] 类别清扫证据存在（pendingCommits 全使用点——含 `restoreFromEpoch` 的 synchronized 迭代块 :158/:178/:190——+ synchronized 迭代点表 + SinkFunction 兄弟核对；`recover` 死方法处置记录）
- [x] `mjs-pins.json` 已移除 2PC:83 pin 且 `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（无 stale pin）
- [x] `./mvnw test -pl nop-stream/nop-stream-core` 全量绿
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No owner-doc update required for this phase（类内部行为修复，行为契约不变）

### Phase 3 - 族 F1：WindowOperator cleanup 收敛（WI-3：RL-6）

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`、`nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestWindowOperatorMergingCleanupInvariant.java`

- Item Types: `Fix`

- [ ] **test-first 先红**：翻转 `TestWindowOperatorMergingCleanupInvariant` 2 个 pin 测试断言（泄漏 → 收敛：cleanup 后 MergingWindowSet 映射目标条目移除、persist 状态不增长；cleanup 后到达的重叠元素 t=55 生成 [55,105) 而非 [10,105)）；新增长跑场景「多次 session 开合后 checkpoint 状态收敛」用例；跑测试确认红（记录翻转断言的红输出）
- [ ] **RL-6 修复**：onEventTime cleanup 分支（:773-782）补 `mergingWindows.retireWindow(triggerContext.window)`——**参数 = in-flight 窗口（mapping key），不是 stateWindow（mapping value）**：cleanup timer 以 actualWindow 注册，`triggerContext.window` 即 mapping key；`retireWindow(stateWindow)` 会因 key 不存在抛 `StreamException`（`MergingWindowSet.java:134-139` 实测语义）；对照 Flink 语义 = timer namespace 即映射 key；非 merging 路径（mergingWindows == null）不需要 retire
- [ ] **类别清扫（F1 族）**：grep `WindowOperator` 全部 cleanup/retire 路径（onEventTime :725-790 + onProcessingTime :791+ 双分支——onProcessingTime cleanup 分支 :834-843 存在同型缺失（只 clearWindowContents + clear()，无 retire），**一并修复**）；grep `MergingWindowSet` 全部调用点（retireWindow :134 / persist / getStateWindow）核对收敛语义；核对其它 Window 算子 merge 语义（如有同型窗口算子）——**只修同类实例，不顺手改无关行为**
- [ ] **onProcessingTime 修复的测试要求**：与 onEventTime 同型修复必须配套测试——能通过 `HeapInternalTimerService` 推进 processing-time 的聚焦测试（如新增/扩展 `TestWindowOperatorMergingCleanupInvariant` 的 processing-time 分支用例）；若推进路径不可测，显式记录 `No new test required: <原因>`（Rule #25）并说明规避方法
- [ ] **test-first 后绿**：`TestWindowOperatorMergingCleanupInvariant`（翻转 + 新增）全绿；`TestWindowRoundTripInvariant`（9）全绿；Window E2E（`TestWindowOperatorUnificationE2E` / `TestWindowEndToEnd`）复跑绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] RL-6 修复落地：cleanup 后 MergingWindowSet 映射收敛（翻转 pin 测试绿 + persist 状态不增长）
- [ ] cleanup 后重叠元素从新范围合并（t=55 → [55,105)），无 stale 范围复活（翻转 pin 测试绿）
- [ ] 类别清扫证据存在（双 cleanup 分支 + MergingWindowSet 调用点 + 其它窗口算子核对结论）
- [ ] `TestWindowRoundTripInvariant`（9）+ `TestWindowOperatorMergingCleanupInvariant` + Window E2E 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] No owner-doc update required for this phase（行为修复与 Flink 语义对齐，无契约变更）

### Phase 4 - 输出契约族：ChainingOutput side-output 转发（WI-4：RL-7，双轨 PD-15）

Status: planned
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ChainingOutput.java`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java`（接线）、**`nop-stream/nop-stream-runtime/src/test/...`（新增 side-output 端到端测试——入口是 runtime 的 `WindowOperator.sideOutput(lateDataOutputTag)`，测试必须放在 runtime 模块；core 不能反向依赖 runtime）**

- Item Types: `Fix`

- [ ] **test-first 先红**：新增端到端测试（**落点 nop-stream-runtime/src/test**）——`WindowOperator.sideOutput(lateDataOutputTag)` → 链式 `ChainingOutput` → side-output 消费者收到记录（无消费者注册时 fail-fast 抛异常）；**既有 `new ChainingOutput(...)` 构造点适配核查**：grep 全部直接构造点（live 实测 = 41 处测试构造点 + 2 处 main 接线 `StreamTaskInvokable.java:171/:209`；分布于 TestDistributedExactlyOnce 11 / TestRocksDBStateBackendE2E 6 / TestCheckpointEndToEnd 6 / TestBarrierPropagation 6 / TestE2ECheckpointAndRecovery 4 等），选注册方案后逐一确认适配（构造参数默认值兼容或测试侧同步适配，编译断裂必须先行处理）；跑测试确认红（当前丢弃/无消费者通道）
- [ ] **RL-7 修复**：`ChainingOutput.collect(OutputTag, record)` 转发至注册的 side-output 消费者；链式接线（`StreamTaskInvokable.java:171/:209`）支持消费者注册；无消费者接线时 fail-fast（抛异常），**禁止静默丢弃**（Rule #24：不得保留 LOG.warn + 丢弃路径）。**最小契约约束（防止执行者即兴发明）**：注册机制限于 `ChainingOutput` 级别（构造参数或方法级注册 per-tag consumer，选型须与既有 ~10 处直接构造点兼容）+ `StreamTaskInvokable` 暴露可选注册入口（测试可触达）；`Output` 接口契约不变；无消费者 = fail-fast（默认）。**用户可见行为变更声明**：修复后默认链式部署下，使用 `lateDataOutputTag`（经 `WindowOperatorBuilder` 接线，字段 :58 / builder 方法 :101-102 / ctor 传参 :197）或 ProcessFunction/CepOperator 多输出的任务，在首个侧输出记录时**fail-fast 崩溃而非静默丢弃**（I3 裁决认可的方向；已核实仓库无既有测试依赖静默丢弃）
- [ ] **类别清扫（输出契约族）**：grep `Output.collect(OutputTag` 全部 call-site（`WindowOperator.sideOutput` :1015-1017、ProcessWindowFunction 多输出路径、`CepOperator.java:482-483` 等）确认转发契约覆盖；grep 全部 `Output` 实现类（`ChainingOutput` + `TimestampedCollector` + `StreamTaskInvokable` 内部 `RecordWriterOutput` :621-623 / `BroadcastingRecordWriterOutput` :681-682）逐一核对各自 collect(OutputTag) 行为。**清扫发现处置规则（roadmap 类别清扫强制 vs Non-Goals 边界）**：
  - in-task 丢弃点（与 RL-7 同根因，如链式路径内其它静默丢弃）→ 本 plan 一并修复（同类实例，只修同类）；
  - 跨 task 边界 no-op（`RecordWriterOutput` / `BroadcastingRecordWriterOutput`：侧输出需线协议支持）→ **不静默修复也不静默忽略**：记录为「同族已知实例 + 处置依据（跨 task 转发 = 线协议结构性变更，mission 授权要求结构性变更执行前人工确认）」→ 移交 I6 评估（人工确认候选），证据入清扫清单
- [ ] **test-first 后绿**：端到端测试绿（side-output 消费者收到记录）；无消费者 fail-fast 断言绿；`./mvnw test -pl nop-stream/nop-stream-runtime` 全量绿（端到端测试所在模块）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**（Rule #22/#23）：从 `WindowOperator.sideOutput(lateDataOutputTag)` 入口到 side-output 消费者出口完整连通（测试断言消费者实际收到记录，非组件级孤立断言）
- [ ] **接线验证**（Rule #23）：链式部署下 `StreamTaskInvokable` 创建的 `ChainingOutput` 确实把 side-output 记录送达注册消费者（运行时连通，非仅类型存在）
- [ ] **无静默跳过**（Rule #24）：无消费者接线时 fail-fast（测试断言异常抛出），`ChainingOutput` 中无 LOG.warn+丢弃路径残留（code review + grep 核对）
- [ ] 类别清扫证据存在（collect(OutputTag call-site + Output 实现类 + OutputTag 消费路径核对结论）
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime` 全量绿（端到端测试所在模块）
- [ ] 双轨标注在案：本 Phase 修复为 Cycle 2 / PD-15（I6 追加）提供 live 修复基线
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] No owner-doc update required for this phase（`Output` 接口契约不变，修复为实现侧）

### Phase 5 - 门禁复跑、pin 注册表同步与文档收口

Status: planned
Targets: `ai-dev/audits/nop-stream-invariants/`（red-list.md / invariant-catalog.md / gate-inventory.json / mjs-pins.json）、`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`、`ai-dev/logs/`

- Item Types: `Fix | Proof | Follow-up`

- [ ] **门禁复跑**：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` 全绿（原 84 + 本 plan 新增/翻转）；`node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（violations ⊆ pins，无 stale pin；全绿模式下无 stdout 输出属正常）；WI-4 要求的 `./mvnw test -pl nop-stream -am -T 1C` 全量跑绿（I3 裁决表 WI-4 门禁复跑要求）
- [ ] **pin 注册表同步**：`mjs-pins.json` 移除 2PC:83（RL-4 修复后违规不存在）；JUnit pin 测试断言翻转记录（`TestClusterRegistryConsistencyInvariant` 2 处 + `TestWindowOperatorMergingCleanupInvariant` 2 处 → 不再 pin 缺陷行为）；gate-inventory.json 更新（pin 状态 / 断言方向 / 涉及类清单）
- [ ] **red-list.md 状态更新**：RL-1..7 逐条标注「已修复 + 证据（修复 commit / 翻转测试名 + 门禁复跑结果）」；WO 部分不动；RL-3 标注触发闭合
- [ ] **roadmap 同步**：Work Item I4 状态流转记录（本 plan 转 active 时 `todo`→`planned`；closure audit 通过后 `planned`→`done`）；`## Follow-up Backlog` 的 RL-3 条目状态更新为「已由 I4 触发闭合」（保留历史处置记录）
- [ ] **catalog 行号引用同步**：invariant-catalog §3 中 R15-AR-8 / AR-1 / AR-11 / AR-9 / AR-18 等行号引用以本 plan 修复后 live 为准复核更新
- [ ] **类别清扫证据汇总**：四族清扫清单 + 逐点核对结论汇总写入本 plan 或 daily log（grep 范围与 I3 裁决表 §3 一致）
- [ ] `ai-dev/logs/` 对应日期条目已更新（Phase 1-5 逐 phase 追加）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 门禁全绿：五族 JUnit（原 84 + 新增/翻转）0 failure + mjs `all` exit 0 + `./mvnw test -pl nop-stream -am -T 1C` 全量绿
- [ ] pin 注册表与 live baseline 一致（无 stale pin、无未 pin 静态违规、JUnit pin 断言方向为「已修复」而非「缺陷 pin」）
- [ ] red-list.md 全部 7 条 RL 标注修复证据；RL-3 backlog 状态已闭合
- [ ] roadmap Work Item I4 状态与本文档、daily log 一致
- [ ] **No owner-doc update required 裁定复核**：本 plan 全部 Phase 为类内部行为修复，行为契约不变；catalog/red-list/gate-inventory 属审计文档同步（已列入本 Phase）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全部 7 条 red list RL（RL-1..7，**含触发闭合的 RL-3**）已修复，无 in-scope 残留 live defect
- [ ] test-first 证据在案（每条修复均有先红后绿记录：翻转测试红输出 + 修复后绿输出）
- [ ] 四族类别清扫证据在案（与 I3 裁决表 §3 清扫范围一致）
- [ ] 门禁复跑零命中：五族 JUnit + 3 表完备性 0 failure；mjs `all` exit 0；`./mvnw test -pl nop-stream -am -T 1C` 全量绿
- [ ] pin 注册表同步：`mjs-pins.json` 无 stale pin、无未 pin 违规；JUnit pin 断言全部为「已修复」方向
- [ ] 无静默跳过（Rule #24）：RL-7 fail-fast 断言绿、无 LOG.warn+丢弃残留；其它修复无空方法体/吞异常路径
- [ ] **Anti-Hollow Check**：RL-7 端到端路径（sideOutput → ChainingOutput → 消费者）运行时连通已验证；每族修复组件与既有调用方调用链连通（非仅类型存在）
- [ ] 无 in-scope live defect 被降级到 deferred / follow-up（RL-3 为触发闭合，非延期）
- [ ] 受影响审计文档（red-list / catalog / gate-inventory / roadmap backlog）已同步，`docs-for-ai/` 无需更新（行为契约不变，显式声明 No owner-doc update required）
- [ ] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [ ] `./mvnw test -pl nop-stream -am -T 1C`（mission 验证命令）
- [ ] checkstyle 通过（`./mvnw checkstyle:check -Pqa -pl nop-stream -am`，root pom 默认配置已注释、仅 qa profile 生效——以输出中无新增 violation 为准，failOnViolation=false 不阻断 exit code）

## Deferred But Adjudicated

### RL-3（InMemoryClusterRegistry per-renewal 租约语义，R16-AR-18）

- Classification: 记录条目（非 deferred 分类——原 Follow-up Backlog 项，触发条件 = 「I4 修 F5 族（WI-1）类别清扫时顺手覆盖」，本 plan Phase 1 已命中触发条件，**已进入 in-scope 修复**，此处仅为触发记录）
- Why Not Blocking Closure: 不适用——本项已进入本 plan in-scope（Phase 1）修复，不再是 deferred 项；roadmap backlog 状态随 Phase 5 更新为闭合
- Successor Required: no

### I2 非族候选评估（R13-AR-9 / R16-AR-19,20 / R8-AR-59 / R16-AR-13）

- Classification: `watch-only residual`
- Why Not Blocking Closure: I2 探查报告已逐项评估并裁定「不升格」；与本 plan 7 条 RL 的修复无关联；按 Loop Rule 属 I6 评估输入，不阻塞本 plan 修复基线成立
- Successor Required: yes
- Successor Path: `ai-dev/audits/nop-stream-invariants/I2-probing-report.md` §3 → I6 收口评估

## Non-Blocking Follow-ups

- I2 probing report 中未入 red list 的优化级发现（PR-2 及以下）：不阻塞本 plan，如需处置登记 I6 评估。
- 输出契约族门禁建设（Cycle 2 / I1，PD-15）：双轨移交 I6 正式追加 roadmap work item 后执行，不阻塞本 plan。
- I5 全量验证与 full-green 记录：`2026-08-12-1217-6-...` 承接，不阻塞本 plan 关闭（本 plan 已含 WI-4 要求的全量跑）。

## Closure

Status Note: <<closure audit 完成后填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent，closure audit 后填写>>
- Evidence: <<closure audit 后填写：每条 Exit Criterion / Closure Gate 的 PASS/FAIL + live code path / test name；check-plan-checklist 退出码；Anti-Hollow 检查结果；Deferred 项分类检查>>

Follow-up:

- <<closure 后填写：只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
