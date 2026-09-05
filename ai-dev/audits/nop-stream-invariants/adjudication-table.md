# nop-stream I3 裁决表（Adjudication Table）— Cycle 1 / I3（含 Cycle 2 / I3 §7-§10、Cycle 3 / I3 §12-§14）

> Status: active
> Created: 2026-08-12
> Source: Cycle 1 — I2 权威版 `ai-dev/audits/nop-stream-invariants/red-list.md`（RL-1..7 + WO-1..3 裁定）；I2 探查报告 `ai-dev/audits/nop-stream-invariants/I2-probing-report.md`。Cycle 2 — 权威版 `red-list.md` §1-§3（C2-RL-1..3 + C2-PR-1..5）+ `cycle2-I2-probing-report.md`（§7-§10，本文件追加节）。Cycle 3 — 权威版 `red-list.md` §0-§4（C3-RL-1..10 + C3-PR-1..8）+ `cycle3-I2-probing-report.md`（§12-§14，本文件追加节）
> Path note 2026-09-03 (plan 1723-2 / item 23): StreamTaskInvokable moved to io.nop.stream.core.execution.task (pure package move, zero behavior change); paths above re-pointed, in-row line numbers are cycle-time evidence snapshots (machine pins live in the two registries, re-pinned post-move).
> Plan: Cycle 1 — `ai-dev/plans/2026-08-12-1217-4-...`；Cycle 2 — `ai-dev/plans/2026-08-12-1217-10-nop-stream-invariants-cycle2-I3-adjudication.md`；Cycle 3 — `ai-dev/plans/2026-08-13-0805-3-nop-stream-invariants-cycle3-I3-adjudication.md`
> Consumption: I4（Cycle 1 = `2026-08-12-1217-5-...`；Cycle 2 = `2026-08-12-1217-11-...`；Cycle 3 = 下轮 I4 计划另立，以 §13「P0/P1 派发清单」为输入）；I6（以 §9/§14 派生登记 + 扩展候选移交为输入）
> Semantics: **每条 I2 red list 条目一行，零悬挂**——严重度（P0-P3）+ 族归属 + 处置 + 依据，禁止无依据裁决。

## 0. 假设复核记录（Phase 1）

> 本 plan 的 Current Baseline 假设以 I1 pin-and-record 版（4 residual + 3 watch-only）为输入；
> I2 权威版（`red-list.md`，2026-08-12 I2 completed 后）复核对照，逐条差异记录如下。I2 已 `completed`
> （plan `2026-08-12-1217-3-...` Status: completed，58/58 checklist 勾选），故按 I2 权威版修正基线：

| # | 本 plan 假设（I1 版） | I2 权威版事实 | 差异处置 |
|---|---|---|---|
| 1 | 4 条 residual：AR-1 / AR-11 / AR-9 / AR-18 | 5 条在册：RL-1（AR-9 命中点 1）、RL-2（AR-9 命中点 2）、RL-3（AR-18）、RL-4（AR-1）、RL-5（AR-11） | 无实质差异（AR-9 在权威版拆为两条命中点，符合 red-list 约定）→ 按权威版逐条裁决 |
| 2 | 3 条 watch-only：AR-15 / R15-AR-8 / R15-AR-9 | WO-1（AR-15）→ **verified 关闭**；WO-2（R15-AR-8）→ **升格 RL-6**；WO-3（R15-AR-9）→ **verified 关闭** | 与假设一致（plan 声明处置取决于 I2 动态验证）→ 按权威版记录裁定，不重裁 |
| 3 | 未列出 RL-6（I2 动态验证可能升格） | RL-6 升格在册（R15-AR-8，onEventTime cleanup 不 retireWindow） | 预期内差异（plan 声明"处置取决于 I2 动态验证结论"）→ 纳入裁决表 |
| 4 | 未列出 RL-7 | RL-7 探查新增在册（R15-AR-4 确认仍 live，ChainingOutput 静默丢弃 side-output，新族候选） | **按权威版修正基线**（I2 Phase 3 新增发现）→ 纳入裁决表（新族双轨） |
| 5 | — | 4 个非族候选（R13-AR-9 / R16-AR-19,20 / R8-AR-59 / R16-AR-13）全部**不升格**（I2 探查报告 §3） | 非 red list 条目，无需裁决；记录在案（I2 报告存档），I6 按 Loop Rule 评估 |

**复核结论**：I2 权威版 7 条 red list + 3 条 watch-only 裁定全部在册，零悬挂（I2 移交声明 §4 核对一致）；
本 plan 裁决表覆盖全部 7 条 RL（含差异 #3/#4 修正），watch-only 处置全部转述记录。**无需要 I2 补充的信息不足条目。**

## 1. 裁决表（I2 red list 逐条）

> 严重度标尺：**P0** = 数据丢失/损坏或核心恢复语义破坏；**P1** = 现实场景正确性/并发安全缺陷；
> **P2** = 健壮性/资源泄漏/边界场景；**P3** = 次要治理或优化。
> 处置枚举：P0/P1 → 派 I4；P2/P3 → Follow-up Backlog（已裁定处置，附依据即合规）；
> watch-only → 转述 I2 裁定（verified → 关闭；升格 → 并入 RL 裁决）。

| finding-ID（RL） | 位置（live） | 族 | 严重度 | 处置 | 依据（历史严重度 + 影响面 + I2 结论引用） |
|---|---|---|---|---|---|
| RL-1 `JdbcClusterRegistry.registerNode` 写 lease_expire_at=0L | `JdbcClusterRegistry.java:112-115` | F5（ClusterRegistry 族） | **P1** | **派 I4**（与 RL-2 联合，同一 finding R16-AR-9） | 历史 R16-AR-9 = P1（catalog §1 F5）。影响面 = 现实场景正确性：新注册节点在首次 renewLease 前对调度器（getActiveNodes）不可见，违反不变式 #5(a)「registerNode 后必须立即可见」；多节点集群启动/扩容场景下调度器无法立即调度新节点（节点可注册但无人认领）。I2 验证：`red-list.md` RL-1「live 确认 :112-115 INSERT 写 0L，renewLease 后可见；差异属实」。修复成本低（INSERT 写 now+leaseTtlMs 或等效）。 |
| RL-2 `JdbcClusterRegistry.getActiveNodes` 按 > now 过滤 | `JdbcClusterRegistry.java:172-174` | F5（ClusterRegistry 族） | **P1** | **派 I4**（与 RL-1 联合，同一 finding R16-AR-9） | 与 RL-1 互为表里（INSERT 写 0L ⇒ 过滤立即排除新节点），联合裁决 P1。I2 验证：`red-list.md` RL-2「与 RL-1 构成因果链——INSERT 写 0L ⇒ 过滤条件立即排除新节点」。 |
| RL-3 `InMemoryClusterRegistry.renewLease` 忽略 leaseTimeoutMs | `InMemoryClusterRegistry.java:68-81`（:74 只存 now；:90/:98/:114 固定 leaseTtlMs） | F5（ClusterRegistry 族） | **P2** | **Follow-up Backlog** | 历史 R16-AR-18 = P2（catalog §1 F5）。影响面 = 健壮性/语义一致性（**双向**）：InMemory 为嵌入式/单机执行模式（生产接线 `EmbeddedDistributedExecutor.java:134`、`RpcDistributedExecutor.java:191`），per-renewal timeout 被忽略 → 配置超时 < 固定 15s 时**延迟淘汰**（故障检测延迟），配置超时 > 15s 时**健康节点被提前淘汰**；均无数据丢失、无恢复语义破坏、无并发危害。**潜伏性加固（live 实测）**：生产唯一 renewLease 调用方 `TaskManager.java:83/:212` 硬编码 `DEFAULT_LEASE_TIMEOUT_MS=15000L` == `InMemoryClusterRegistry.java:23` 默认 `LEASE_TIMEOUT_MS=15000L`（:40 默认构造）——**当前生产接线下两值重合，行为零可见偏差**。I2 验证：`red-list.md` RL-3「live 确认 renewLease 忽略 leaseTimeoutMs；JDBC 实现已按参数生效（对比面）」+ 门禁⑤ InMemory 分支 pin 断言。裁决例外依据（plan 声明 AR-18 以裁决分析为准）：影响面属健壮性级别（延迟/提前淘汰，当前零可见偏差），不满足 P1「现实场景正确性」的量级（无错误结果、无丢失），维持历史 P2 → backlog。 |
| RL-4 `TwoPhaseCommitSinkFunction.saveState` 无锁 copy | `TwoPhaseCommitSinkFunction.java:83`（`new TreeMap<>(pendingCommits)` 无 synchronized） | F2（SinkFunction 族） | **P0** | **派 I4** | 历史 R16-AR-1 = P0（catalog §1 F2「saveState() 无锁遍历 synchronizedMap」）。影响面 = **核心恢复语义破坏**：2PC sink 的 checkpoint 快照 = 恢复时的 pendingCommits 唯一事实源；无锁 copy 在 commit/abort 线程并发写 pendingCommits 时可能 CME（checkpoint 失败）或撕裂快照（部分拷贝）→ restore 后 pendingCommits 不完整 → exactly-once 提交语义破坏 = 数据一致性风险。I2 验证：`red-list.md` RL-4「live :83 确认无锁 copy；行为 pin 证明快照内容完整，原子性未被 pin」+ mjs pin 静态命中。 |
| RL-5 `TwoPhaseCommitSinkFunction.setPendingCommits` 接受任意 Map | `TwoPhaseCommitSinkFunction.java:76-78`（setter 未包装 synchronizedMap） | F2（SinkFunction 族） | **P1** | **派 I4** | 历史 R16-AR-11 = P1（catalog §1 F2）。影响面 = 并发安全防御缺口：setter 契约不防护非同步 Map 传入，一旦未来调用方（restore 路径外）传入非同步 Map，全部迭代路径的同步保证失效（CME 风险）；当前唯一调用方 `StreamSinkOperator.java:157` 已主动包装（部分缓解），但契约未防护、未文档化。I2 验证：`red-list.md` RL-5「live :76-78 确认 setter 直接赋值；当前唯一调用方已包装，但 setter 契约本身不防护未来调用方（I2 复核维持 in-scope）」。修复成本低（setter 内部包装 `Collections.synchronizedMap(new TreeMap<>(pending))` 或文档化调用方契约）。 |
| RL-6 `WindowOperator.onEventTime` cleanup 未 retire 合并窗口 | `WindowOperator.java:773-782`（cleanup 分支 clearWindowContents + triggerContext.clear()，无 `retireWindow`） | F1（Operator 族） | **P1** | **派 I4** | 历史 R15-AR-8 = P2（catalog §1 F1）；I2 建议 ≥P2（`red-list.md` RL-6）。影响面 = **现实场景正确性 + 状态泄漏**：I2 动态验证确认两种后果——(a) cleanup 后 MergingWindowSet 保留 [10,80)→[10,60) 映射并 persist 入 checkpoint 状态（**无界增长 = 长跑任务状态泄漏**）；(b) cleanup 后到达的重叠元素（t=55）合并进 stale 范围生成 [10,105) 而非 [55,105)（**被清理窗口范围复活 = 输出窗口范围错误**）。session window + late data 是标准现实场景，错误输出语义（非仅渐进泄漏）支撑上浮 P1。修复成本低（cleanup 分支补 `retireWindow(stateWindow)`，对照 Flink 语义）。 |
| RL-7 `ChainingOutput` 静默丢弃 side-output | `ChainingOutput.java:84-86`（`collect(OutputTag, record)` 仅 LOG.warn + 丢弃）；接线 `StreamTaskInvokable.java:171/:209` | **新族（输出契约族）** | **P1** | **派 I4**（双轨：I4 修复 + Cycle 2 派生登记 PD-15，见 §4） | 历史 R15-AR-4 = P1（原判 P1，R16 确认表「仍存在」；I2 探查确认未修复）。影响面 = **现实场景正确性（契约违约 + 静默丢失，无 fail-fast）**：默认链式部署下 late-data 侧输出（`WindowOperator.java:1015-1017` sideOutput(lateDataOutputTag)）与 ProcessWindowFunction 多输出被静默丢弃——用户配置的 side-output 数据通道无声丢失；主通道不受影响、恢复语义不受影响，故不达 P0；历史 P1 一致。修复方向 = 转发至下游 side-output 通道或 fail-fast（禁止静默跳过，Rule #24）。 |

## 2. watch-only 裁定记录（转述 I2，不重裁）

> 依据 I2 Phase 2 动态验证（git-committed focused tests，全绿）：`red-list.md` §2。

| watch-only 条目 | I2 验证方式 | I2 裁定 | I3 处置 |
|---|---|---|---|
| WO-1 AR-15 `LocalFileCheckpointStorage` 按文件名 ID 排序 | `TestLocalFileCheckpointStorage`（2 测试，乱序混合 ID 取 max、删除回退，13/13 绿）+ 级联解除论证链（CheckpointCoordinator:896-900 单调守卫 + 门禁③） | **verified** | **关闭**（移除 watch-only，不升格 red list） |
| WO-2 R15-AR-8 `WindowOperator` cleanup 不 retireWindow | `TestWindowOperatorMergingCleanupInvariant`（2 测试，泄漏 PIN 确认） | **升格 red list（RL-6）** | **并入**（已在 §1 RL-6 裁决，不重复处置） |
| WO-3 R15-AR-9 `InputGate` per-id inFlightAlignments | `TestInputGateMultiEpochBarrier`（2 测试，重叠/交错 barrier per-id 独立对齐，5/5 绿，端到端走通） | **verified** | **关闭**（移除 watch-only，不升格 red list） |

## 3. P0/P1 派发清单（I4 工作项，按族组织）

> I4 计划（`2026-08-12-1217-5-...`）以本清单为输入起草。每条含：目标类 + 缺陷描述 + 预期行为 +
> **类别清扫范围**（grep 全类兄弟，roadmap「类别清扫强制」）+ 测试要求（test-first 先红后绿）+ 门禁复跑要求。
> **结构性重构标注**：本清单 4 个工作项均为**类内部行为修复**，不涉及公共 API / 模块边界 / Operator 接口变更
> （`Output#collect(OutputTag, X)` 接口已存在，RL-7 修复的是实现侧违约）→ **无「需人工确认」门，显式声明**。

### WI-1. 族 F5 — ClusterRegistry：registerNode 注册即可见（RL-1 + RL-2 联合，R16-AR-9）

- **目标类**：`JdbcClusterRegistry`（`nop-stream-runtime/.../cluster/JdbcClusterRegistry.java`）
- **缺陷描述**：registerNode INSERT 写 `lease_expire_at=0L`（:112-115），getActiveNodes 按 `lease_expire_at > now` 过滤（:172-174）→ 新注册节点在首次 renewLease 前对调度器不可见。
- **预期行为**：registerNode 写入有效租约到期时间（如 `now + leaseTtlMs`），注册后节点立即对 getActiveNodes 可见（不变式 #5(a)）；getActiveNodes 过滤语义不变。
- **类别清扫范围**：grep `ClusterRegistry` 全部实现与 lease 相关方法——`InMemoryClusterRegistry.registerNode/renewLease/getActiveNodes/evictExpiredNodes`（:59/:68/:98/:114 活性计算）、`JdbcClusterRegistry` 全部 lease SQL 点（INSERT/UPDATE/SELECT）；同步核对 `NodeDiscoveryConsistencyChecker`。
- **测试要求（test-first）**：先红后绿——翻转 `TestClusterRegistryConsistencyInvariant.testRegisterNodeVisibilityIsPinnedPerImpl` JDBC 分支 pin 断言（注册后可见）；新增「自定义 leaseTtlMs 注册后立即可见」用例；两实现同语义断言保持。
- **门禁复跑要求**：门禁⑤ `TestClusterRegistryConsistencyInvariant`（8）+ `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0。

### WI-2. 族 F2 — TwoPhaseCommitSinkFunction 并发防护（RL-4 P0 + RL-5 P1，R16-AR-1 + R16-AR-11）

- **目标类**：`TwoPhaseCommitSinkFunction`（`nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java`）
- **缺陷描述**：(a) saveState(:83) `new TreeMap<>(pendingCommits)` 无 synchronized → CME / 撕裂快照（P0，恢复语义破坏面）；(b) setPendingCommits(:76-78) 接受任意 Map，不防护非同步 map 破坏同步保证（P1）。
- **预期行为**：saveState 在 `synchronized (pendingCommits)` 块内完成 copy；setPendingCommits 内部包装 `Collections.synchronizedMap(new TreeMap<>(pending))`（或文档化调用方契约，二选一，I4 定稿）。
- **类别清扫范围**：grep 全类 `pendingCommits` 全部使用点（saveState/finishCommit/preCommit/abort/recover/restoreFromEpoch）+ 全部 `Collections.synchronized*` 字段迭代点（不变式 #2 表）+ 同类 SinkFunction 兄弟（`StreamSinkOperator` restore 路径 :146-158、`BatchConsumerSinkFunction` buffer 路径）。
- **测试要求（test-first）**：先红后绿——mjs 静态违规清零（移除 `mjs-pins.json[0]` pin 后 scan-iterations 仍绿 = 修复生效）；并发测试（saveState 与 commit/abort 并发线程下无 CME + 快照完整）；setter 接受非同步 Map 后迭代路径行为等价；`TestSynchronizedCollectionInvariant` 新增断言（setter 防护）。
- **门禁复跑要求**：`TestSynchronizedCollectionInvariant`（10）+ mjs `all` exit 0（pin 更新语义：移除 2PC:83 pin 后违规集 ⊆ pin 集仍成立）+ `./mvnw test -pl nop-stream-core` 全量。

### WI-3. 族 F1 — WindowOperator cleanup 收敛（RL-6，R15-AR-8）

- **目标类**：`WindowOperator`（`nop-stream-runtime/.../operators/windowing/WindowOperator.java`）
- **缺陷描述**：onEventTime cleanup 分支（:773-782）只 clearWindowContents + triggerContext.clear()，未调用 `mergingWindows.retireWindow(stateWindow)` → MergingWindowSet 映射无界增长（checkpoint 状态泄漏）+ cleanup 后重叠元素合并进 stale 范围（窗口范围错误）。
- **预期行为**：cleanup 分支补 `mergingWindows.retireWindow(stateWindow)`（对照 Flink 语义），映射随 cleanup 收敛，cleanup 后到达的重叠元素从新范围合并（[55,105) 而非 [10,105)）。
- **类别清扫范围**：grep `WindowOperator` 全部 cleanup/retire 路径（onEventTime :725-790 + onProcessingTime :791+ 双分支）+ `MergingWindowSet` 全部调用点（retireWindow/persist/getStateWindow）+ 其它 Window 算子 merge 语义（如有）。
- **测试要求（test-first）**：先红后绿——`TestWindowOperatorMergingCleanupInvariant` 2 个 PIN 测试断言翻转（泄漏 → 收敛：cleanup 后映射为空/目标条目移除、persist 状态不增长；cleanup 后 t=55 生成 [55,105)）+ 长跑 checkpoint 状态收敛用例。
- **门禁复跑要求**：`TestWindowRoundTripInvariant`（9）+ `TestWindowOperatorMergingCleanupInvariant` + Window E2E（`TestWindowOperatorUnificationE2E` / `TestWindowEndToEnd`）复跑。

### WI-4. 新族 输出契约族 — ChainingOutput side-output 转发（RL-7，R15-AR-4，双轨）

- **目标类**：`ChainingOutput`（`nop-stream-core/.../operators/ChainingOutput.java`）
- **缺陷描述**：`collect(OutputTag, record)`（:84-86）仅 LOG.warn + 丢弃 → 默认链式部署下 late-data 侧输出 / ProcessWindowFunction 多输出静默丢失（契约违约，无 fail-fast）。
- **预期行为**：`Output#collect(OutputTag, X)` 调用必须被转发到注册的 side-output 消费者；若转发通道在链式接线中不可用，则 fail-fast（抛异常），禁止静默丢弃（Rule #24）。
- **类别清扫范围**：grep `Output.collect(OutputTag` 全部 call-site（`WindowOperator.sideOutput` :1015-1017 等）+ 全部 `Output` 实现类（`ChainingOutput` 及链式接线兄弟 `StreamTaskInvokable.java:171/:209`）+ `OutputTag` 注册/消费路径。
- **测试要求（test-first）**：先红后绿——端到端：`WindowOperator` sideOutput(lateDataOutputTag) → 链式 `ChainingOutput` → side-output 消费者收到记录（Anti-Hollow：入口到出口完整连通）；无消费者接线时 fail-fast 断言。
- **门禁复跑要求**：全量 `./mvnw test -pl nop-stream -am -T 1C` + mjs `all` + Window E2E 复跑。
- **双轨标注**：本工作项同时登记 Cycle 2 派生（§4，PD-15）——I4 修复 + Cycle 2 输出契约族不变式门禁沉淀，两轨独立推进。

## 4. Cycle 2 派生登记（新族，供 I6 正式追加）

> 按 Loop Rule 预授权，I3 登记派生输入，I6 以本小节为输入正式追加 Cycle 2 / I1 work item。
> PD-n = Precedent Derivation 先例链编号（本仓首个 PD 号 = **PD-15**，接续 `ai-dev/lessons/` 最高编号 14，live grep 实测无已铸造 PD-n）。

### PD-15 — 输出契约族（side-output 转发契约）

- **不变式陈述**：任何 `Output.collect(OutputTag, X)` 调用必须被转发到注册的 side-output 消费者，不得静默丢弃。
- **触发证据（`文件:行`）**：`ChainingOutput.java:84-86`（丢弃点：仅 LOG.warn）→ `WindowOperator.java:1015-1017`（sideOutput(lateDataOutputTag) call-site）→ 链式接线 `StreamTaskInvokable.java:171/:209`（`new ChainingOutput<>(wiredInput)`）。
- **历史同源 finding**：R15-AR-4（`2026-05-31-adversarial-review-nop-stream-r9/01-open-findings.md`，原判 P1「仍存在」，I2 探查确认未修复）。
- **Cycle 2 / I1 建议门禁表达候选**：JUnit（侧输出转发端到端路径）+ 静态扫描（`collect(OutputTag` call-site 与丢弃点匹配检查），I6 按 Loop Rule 评估定稿。
- **移交**：显式移交 I6（本 plan Non-Blocking Follow-ups 第 2 条）。

## 5. 零悬挂复核（Phase 2）

| 条目 | 严重度 | 处置 | 落点 |
|---|---|---|---|
| RL-1 | P1 | 派 I4 | §3 WI-1 |
| RL-2 | P1 | 派 I4 | §3 WI-1（联合） |
| RL-3 | P2 | Follow-up Backlog | `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`「## Follow-up Backlog」（2026-08-12 新增，见 roadmap） |
| RL-4 | P0 | 派 I4 | §3 WI-2 |
| RL-5 | P1 | 派 I4 | §3 WI-2（联合） |
| RL-6 | P1 | 派 I4 | §3 WI-3 |
| RL-7 | P1 | 派 I4（双轨）+ Cycle 2 派生 | §3 WI-4 + §4 PD-15 |
| WO-1 | — | 关闭（verified） | §2 |
| WO-2 | — | 关闭（并入 RL-6） | §1 RL-6 |
| WO-3 | — | 关闭（verified） | §2 |

**复核结论**：7 条 red list + 3 条 watch-only 全部有处置，处置与派发一一对应（4 个 I4 工作项 + 1 条 backlog + 2 条 verified 关闭 + 1 条并入 RL 裁决），**零悬挂达成**，无「已裁决但无处可去」项。

## 6. I6 收口裁定记录（Cycle 1 / I6，供 Cycle 2 / I1 与 Cycle 2 / I3 引用）

> Status: active（I6 落档，2026-08-12）
> Plan: `ai-dev/plans/2026-08-12-1217-7-nop-stream-invariants-cycle1-I6-closure-and-cycle2-derivation.md`
> Consumption: Cycle 2 / I1（plan `2026-08-12-1217-8-...`，门禁范围 = 本节裁定）；Cycle 2 / I3（跨 task interim fail-fast 预授权确认输入，见下）
> 输入核对：`cycle1-I6-input.md`（I5 唯一落点）与 on-disk surefire 一致（2822 tests / 0 failures / 0 errors / 10 skipped；门禁 9 类 / 92 tests）；mjs pin 0；red list 零悬挂（7/7 + RL-3 闭合）；新族数 = 1。

### 6.1 PD-15 新族裁定

- **裁定**：PD-15 输出契约族 = **正式新族**（不变式 #6：「任何 `Output.collect(OutputTag, X)` 调用必须被转发到注册的 side-output 消费者，不得静默丢弃；无注册消费者 → fail-fast」）。
- **依据**：I2 探查登记（PR-1 → RL-7）+ I3 派生登记（§4 PD-15，历史 pre-fix 行号 `ChainingOutput.java:84-86` / `WindowOperator.java:1015-1017` / `StreamTaskInvokable.java:171/:209`）+ I4 修复基线（`b20fcd0e1`，`ChainingOutput.java:111` 转发 + 无消费者 fail-fast `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）+ I5 零新失败（无新增族）。
- **live 复核（I6 实测）**：发射点 6 个（`ProcessOperator.java:111/:134` / `WindowOperator.java:1030/:1860` / `CepOperator.java:483/:777`）；接线点 `ChainingOutput.java:111` / `StreamTaskInvokable.java:310-313`（registerSideOutputConsumer → 共享 consumer map）连通。
- **派生**：依 Loop Rule「新族强制沉淀」→ Cycle 2 / I1 正式追加（roadmap Work Item 表六行，I1 附触发证据）。

### 6.2 跨 task side-output 缺口双层裁决

- **事实**：`RecordWriterOutput.collect(OutputTag)`（`StreamTaskInvokable.java:645` 空体）/ `BroadcastingRecordWriterOutput.collect(OutputTag)`（:706 空体）；跨 task 生产可达接线 `GraphExecutionPlan.java:454-458` → `StreamTaskInvokable.java:239/:245/:352` → tail 算子 setOutput(RecordWriterOutput/BroadcastingRecordWriterOutput)。= 同族（输出契约族）**已确认契约缺口**（P1，静默数据丢失，生产可达，影响类同 RL-7），非「无调用方」。按 Minimum Rules #15 归 `Fix` 类。
- **层次 1（interim fail-fast，自动信封内）**：空体 → fail-fast（抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 风格异常）——类内部行为修复（private 嵌套类，`Output` 接口零变更），同 RL-7 修复先例（I4 自动执行）；Rule #24「功能未实现时快速失败而非静默忽略」→ P1 预授权自动修复。**预裁决分派 Cycle 2**：经 Cycle 2 / I3 确认 → Cycle 2 / I4 执行；Cycle 2 / I1 门禁以过渡 pin 全绿落地，I4 修复后移除 pin。
- **层次 2（线协议支持，人工确认门）**：跨 task side-output 真正支持 = RecordWriter 线协议结构性重构（公共内部机制变更）→ 执行门 = 人工确认（mission Cross-Cutting「结构性重构执行前人工确认」，不在自动信封内）→ 登记**人工确认待办 `HG-01`**（证据 = 6 发射点 + 接线路径；修复方向 = 跨 task 侧输出序列化/路由/消费注册；触发条件 = 人工批准 + 跨 task side-output 需求出现（或 CI 门禁红暴露新实例）；Successor = 人工确认后另立 plan）。
- **保护性覆盖（四重留痕，未降级）**：① in-task fail-fast（I4 `b20fcd0e1`）；② Cycle 2 / I1 门禁（类级枚举 + call-site 注册表 + 三态分类）；③ 过渡 pin 2 条（`mjs-pins.json`，关联 `HG-01`；移除 = Cycle 2 / I4 interim fail-fast 落地 + 注册表分类更新，禁静默移除；`HG-01` 线协议支持落地属增强不阻塞 pin 移除）；④ Follow-up Backlog `HG-01` 待办条目。interim fail-fast 属自动信封不入 backlog。

### 6.3 Cycle 2 / I1 门禁范围裁定（不变式 #6 一等门禁）

- **行为三态分类**：全 main `Output` 实现类 `collect(OutputTag, X)` 行为 ∈ {转发到注册消费者 / 无消费者 fail-fast / 已知违约 pin}。live 4 个实现类：`ChainingOutput`（转发 + fail-fast）、`TimestampedCollector`（纯转发，分类以被包装对象语义为准）、`RecordWriterOutput` / `BroadcastingRecordWriterOutput`（已知违约 → pin）。
- **类级枚举完备性**：新增 main `Output` 实现类不入注册表即红（`scan-output-contract`，注册表 = `ai-dev/audits/nop-stream-invariants/output-contract-registry.json`）。
- **call-site 注册表**：6 发射点全覆盖（`ProcessOperator.java:111/:134` / `WindowOperator.java:1030/:1860` / `CepOperator.java:483/:777`），新增发射点即红；跨 task 可达点标注 `HG-01` 关联。
- **过渡 pin**：跨 task 实例以 2 条过渡 pin 登记（`mjs-pins.json`，条目含 `key` + 关联 `HG-01`；移除 = Cycle 2 / I4 interim fail-fast 修复落地后，禁静默移除）。
- **落点**：Cycle 2 / I1（plan `2026-08-12-1217-8-...`）为本裁定直接消费者。

### 6.4 复触发登记摘要

- Cycle 2 触发原因 = 新族沉淀（PD-15）+ 跨 task 缺口人工确认门；三选一复触发继续生效（① CI 任一不变式门禁变红；② nop-stream 核心类结构变更（新增/重命名 Operator/SinkFunction/Checkpoint 机制/Output 实现类）；③ 周期复探）；人工确认待办触发 = 人工批准跨 task 线协议变更（roadmap §Loop Rule 已登记）。

---

## 7. Cycle 2 / I3 裁决表（权威版，2026-08-12）

> Status: active（Cycle 2 / I3 产出，plan `2026-08-12-1217-10-nop-stream-invariants-cycle2-I3-adjudication.md`）
> Source: Cycle 2 / I2 权威版 `red-list.md`（C2-RL-1..3 + C2-PR-1..5，2026-08-12 实测）；前置 I6 §6.2 预裁决（interim fail-fast 预授权输入）
> Consumption: Cycle 2 / I4（plan `2026-08-12-1217-11-...`，以 §8「P0/P1 派发清单」为输入）；I6 收口（以 §9 派生登记 + C2-PR-2/5 扩展候选移交为输入）
> Semantics: **Cycle 2 / I2 red list + 探查发现逐条一行，零悬挂**——严重度（P0-P3）+ 族归属 + 处置 + 依据，禁止无依据裁决；I6 §6.2 预裁决的 interim fail-fast 确认结论在 §7.3。

### 7.1 假设复核记录（Phase 1）

> 本 plan 的 Current Baseline 假设以 Cycle 2 / I2 完成前的登记基线为输入；I2 权威版（`red-list.md`，2026-08-12 I2 completed 后）逐条对照复核如下。I2 已 `completed`（plan `2026-08-12-1217-09-...` Status: completed，Closure Audit 11/11 PASS），按 I2 权威版修正基线。

| # | 本 plan 假设 | I2 权威版事实 | 差异处置 |
|---|---|---|---|
| 1 | red list 条目集 = 2 条过渡 pin 已知实例（C2-RL-1 RWO / C2-RL-2 BRWO） | 权威版 §1 = **3 条**：C2-RL-1/2（pin 实例）+ **C2-RL-3**（注册表 disposition 措辞过 claim 评估项，I2 Phase 1 首发） | 预期内差异（plan 声明「I2 可能新增 red list 条目」）→ 按权威版纳入裁决（§7.2） |
| 2 | 2 条过渡 pin 维持（跨 task 实例未修复） | 权威版 §2 裁定表：**均维持**（live 空体 + pin key 精确匹配 + 三方一致 + 接线路径复核） | 无差异 |
| 3 | 探查发现 = 聚焦对抗探查产物（盲区 a-d） | 权威版 §3 = **C2-PR-1..5** 在案；**无新独立族**；2 个扩展候选（C2-PR-2 门禁形态覆盖 / C2-PR-5 控制面陈述扩展）供 I6 Loop Rule 评估 | 无实质差异（plan 声明「可能增减」；细节以权威版为准）→ 逐条裁决（§7.2） |
| 4 | 注册表行号 RWO :645 / BRWO :705（以 live / pin / 注册表为准；I6 §6.2 原文记 :706） | 权威版 §0 复核表确认 :645/:705（BRWO :705 = 方法体起始行，扫描语义；§6.2 原文 :706 = 闭括号行记法） | 无差异（按 :705 口径记录；§6.2 原文已注明以 live/pin/注册表为准） |
| 5 | 门禁基线 = 10 门禁类 / 102 tests / 0 failures + mjs all exit 0 + E2E 3/3 | 权威版 §0 实测一致（102 tests / 0 failures / 0 errors / 0 skipped；mjs 5 命令全 OK；E2E 3/3） | 无差异 |

**复核结论**：无实质基线差异（#1 为预期内新增，按权威版纳入）；**无信息不足条目**（每条均含裁决输入，I2 Phase 4 契约满足 → 无 `NEEDS_I2_SUPPLEMENT`，无阻塞升级）；注册表行号零漂移（0 处移动，无需更新）。
**非族候选追溯（补记）**：I2 已裁定的非族候选（R16-AR-14 OperatorChain 广播 / R16-AR-19,20 BatchConsumerSinkFunction buffer）在 `red-list.md` §3 与 `cycle2-I2-probing-report.md` §3 存档（均不升格 + 理由），不在本 plan 裁决范围（非 red list 条目），随权威版零悬挂移交核对覆盖，追溯链完整。

### 7.2 裁决表（Cycle 2 / I2 red list + 探查发现逐条）

> 严重度标尺：**P0** = 数据丢失 / 损坏或核心恢复语义破坏；**P1** = 现实场景正确性 / 并发安全缺陷；
> **P2** = 健壮性 / 资源泄漏 / 边界场景；**P3** = 次要治理或优化。
> 处置枚举：P0/P1 → 派 I4；P2/P3 → Follow-up Backlog（已裁定处置，附依据即合规）；非 defect / 已检查无问题 → 关闭（转述 I2 结论不重裁）。

| finding-ID（I2 权威版） | 位置（live 2026-08-12） | 族 | 严重度 | 处置 | 依据（历史严重度 + 影响面 + I2 结论引用） |
|---|---|---|---|---|---|
| C2-RL-1 `StreamTaskInvokable$RecordWriterOutput.collect(OutputTag)` 跨 task 空体 no-op（过渡 pin `RWO-cross-task-noop`） | `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java:645-647`（:646 仅注释「Side outputs not supported in cross-task exchange」） | 已知族（不变式 #6 输出契约族；跨 task 兄弟实例，RL-7 修复 `b20fcd0e1` 的同族残余） | **P1** | **派 I4**（interim fail-fast，自动信封——§7.3 确认通过，无「需人工确认」门） | I6 §6.2 预裁决 = P1（已确认契约缺口）；历史 RL-7 = P1 先例（同族静默丢弃）。影响面 = **现实场景正确性（契约违约 + 静默数据丢失）**：跨 task 多 vertex 部署下 tail 算子（WindowOperator / CepOperator / ProcessOperator）的 6 个发射点全部可达（接线链 §7.2 依据：`GraphExecutionPlan.java:454-461` → `wireOperators :239/:245` / `wireTailToRecordWriter :352` → `setOutput(RWO/BRWO)`），side-output 发射全部落入空体 = 静默丢弃，无 fail-fast / 无日志；主通道与恢复语义不受影响故不达 P0。**P0/P1 边界显式说明**：标尺 P0 含「数据丢失」字面，本项静默丢弃的为用户 side-output 数据，但主数据通道、checkpoint 恢复语义、exactly-once 保证均不受影响，且与同族 RL-7（I3 Cycle 1 裁 P1）先例一致 → 维持 P1（现实场景正确性），不升 P0。I2 验证：`red-list.md` C2-RL-1「live :645-647 空体确认，行为与 pin 描述 / 注册表分类一致；pin key 精确匹配；行号零漂移」+ §2 接线路径复核。修复方向（I6 §6.2 层次 1 + I2 裁决输入）= 空体 → 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 风格异常（先例 `ChainingOutput.java:119`，含 `ARG_OUTPUT_TAG` / `ARG_DETAIL` 参数），类内部行为修复。修复成本低。 |
| C2-RL-2 `StreamTaskInvokable$BroadcastingRecordWriterOutput.collect(OutputTag)` 跨 task 空体 no-op（过渡 pin `BRWO-cross-task-noop`） | 同文件 :705-706（空体无注释） | 已知族（不变式 #6 输出契约族，同上） | **P1** | **派 I4**（与 C2-RL-1 联合，同一工作项 WI-C2-1，§8） | 同 C2-RL-1（BRWO = 多 fanOut writer 下游形态，`wireOperators :242-245` 组装；单 fanOut / 单 writer 路径 = RWO）。**BRWO 独立影响面**：多下游 vertex 广播场景（fanOutWriters.size() > 1）下同一静默丢弃面按下游数放大——每个下游都收不到 side-output 且无任何提示；与 RWO 同一修复点（方法体）联合派发，修复成本同 C2-RL-1。I2 验证：`red-list.md` C2-RL-2「live :705-706 空体确认；pin key 精确匹配；行号零漂移」。P0/P1 边界裁定同 C2-RL-1（主通道 / 恢复语义不受影响，RL-7 先例一致 → P1）。 |
| C2-RL-3 注册表 disposition 措辞与 E2E 实际覆盖不符（过 claim） | `ai-dev/audits/nop-stream-invariants/output-contract-registry.json` emissionPoints 表（6 条中 5 条 disposition 写 "E2E covered by TestSideOutputChainingE2E"，实际该 E2E 仅覆盖 WindowOperator late-data 路径 :1030） | 已知族（不变式 #6 输出契约族审计证据质量问题，非代码缺陷） | **P3** | **Follow-up Backlog**（与 C2-PR-4 联动，同一 backlog 条目，§10） | 历史无先例（本版 red list 首发）。影响面 = **次要治理 / 审计证据可追溯性**：措辞过 claim 不改变门禁行为（mjs 不消费 disposition 字段，V4 注册表判定不受影响），无任何运行时正确性影响；修复成本低（修订措辞 与/或 扩展 E2E）。I2 验证：`red-list.md` C2-RL-3「TestSideOutputChainingE2E（3 用例）源码确认仅覆盖 WindowOperator late-data 路径；ProcessOperator:111/:134、WindowOperator:1860、CepOperator:483/:777 共 5 个发射点无 E2E 覆盖（C2-PR-4 实测）」+ §0「已记录，供 I3 评估，不静默放过」。已裁定处置附依据即合规；未来触发条件 = I4 类别清扫或复探时评估措辞修订 / E2E 扩展。 |
| C2-PR-1 透传链中间形态（6 发射点直连算子 `output` 字段，不经 TimestampedCollector） | `ProcessOperator.java:111/:134`、`WindowOperator.java:1860`、`CepOperator.java:777`（:1030/:483 同型） | 已知族（不变式 #6）门禁表达完备性观察 | —（非 defect） | **关闭**（记录在案，不重裁） | I2 探查结论「检查后无问题」（`cycle2-I2-probing-report.md` C2-PR-1）：`Collector<T>` 不继承 `Output`，用户函数无法经 collector 发 side-output；生产 `new TimestampedCollector(` 调用点 3 处均包装算子 output；main 无更深层 Output 包装链；JUnit 包装 RWO 断言 = 合成场景但分类语义有效。无处置动作。 |
| C2-PR-2 扫描器静默跳过形态（匿名类 `new Output<>(){}` / `record implements Output` / raw `OutputTag tag`） | `ai-dev/tools/check-nop-stream-invariants.mjs`（parseTypeStructure :622-652 / V4 声明 regex :795-799） | 已知族（不变式 #6）门禁表达扩展候选 | —（非 defect，当前 0 实例） | **关闭**（不派发）+ **显式移交 I6**（Loop Rule 评估 Cycle 3 / I1 门禁形态覆盖；§9 记录） | I2 探查结论（C2-PR-2）：显式 fail 五路径全部实现 + self-test 覆盖；静默跳过三形态当前 0 实例（grep 实测）；false-positive 方向 fail-loud 安全。影响面 = 未来引入匿名 / record Output 实现或 raw OutputTag 声明时门禁静默放过（门禁表达缺口，非当前 live defect）。显式裁定「不派生 PD-16」（依据 = 非新失败类、无失败实例、已知族门禁表达扩展，派生与否 = I6 Loop Rule 显式评估，本行记录供 I6 可见）。 |
| C2-PR-3 同一 OutputTag 重复注册消费者 = 静默覆盖（last-wins） | `ChainingOutput.java:67-69`（`sideOutputConsumers.put`）、`StreamTaskInvokable.java:310-313`（同型 put） | 已知族（不变式 #6）API 语义观察项 | **P3** | **Follow-up Backlog**（§10） | I2 探查结论（C2-PR-3）：「I3 可裁 P3」。影响面 = 健壮性 / 易误配（第二个注册者静默遮蔽第一个，数据只到一处）；**不违反不变式 #6**（仍转发到"一个"注册消费者，无静默丢弃）；生产当前无重复注册调用面（唯一注册入口 = StreamTaskInvokable / ChainingOutput 公开方法）。优化候选（重复注册 fail-fast 或广播语义），已裁定处置附依据即合规；未来触发条件 = 多消费者接线需求出现或类别清扫时评估。 |
| C2-PR-4 E2E 覆盖缺口（6 发射点仅 1 个有 E2E） | `TestSideOutputChainingE2E`（nop-stream-runtime/.../integration/，3 用例）仅覆盖 `WindowOperator.java:1030` | 已知族（不变式 #6）覆盖缺口评估项 | **P3** | **Follow-up Backlog**（与 C2-RL-3 联动，同一 backlog 条目，§10） | I2 探查结论（C2-PR-4）：缺口确认，优化级候选，**不升格 red list**（I2 裁定）。影响面 = 未覆盖发射点（ProcessOperator:111/:134、WindowOperator:1860、CepOperator:483/:777）的端到端转发 / 无消费者 fail-fast 行为无回归测试兜底（风险 = 算子侧 ctx.output 接线形态差异，如 timestamp 处理）；门禁 V4 注册表 + ChainingOutput 行为断言在案（非裸奔）。未来触发条件 = I4 类别清扫或 E2E 扩展评估。 |
| C2-PR-5 RWO/BRWO 控制面方法（emitWatermarkStatus / emitLatencyMarker）跨 task 空体 | `StreamTaskInvokable.java:640-642/:650-652`（RWO，注释文档化）/ `:701-702/:709-710`（BRWO，空体无注释） | 不变式 #6 陈述扩展候选（同根因族——跨 task Output 方法无线协议支持；**非独立新族**） | —（非 defect） | **关闭**（不派发）+ **显式移交 I6**（Loop Rule 评估不变式 #6 陈述扩展或新立控制面不变式；§9 记录） | I2 探查结论（C2-PR-5）：影响 = 控制面遥测 / 空闲检测降级，**非用户数据丢失**；RWO 两处有注释（文档化选择，Rule #24 合规）、BRWO 两处空体无注释（code-style 级观察）；修复需 RecordWriter 线协议扩展（同 `HG-01` 处置门——人工确认，不在 I4 自动信封内）。显式裁定「不派生 PD-16」（依据 = 非新独立族、同根因族扩展候选，扩展 / 派生判定 = I6 Loop Rule 显式评估，本行记录供 I6 可见）。 |

### 7.3 interim fail-fast 预授权确认（信封复核结论：**通过**，2026-08-12 实测）

> I6 §6.2 层次 1 预裁决确认流程——按本 plan Phase 1「失败路径必须闭合」要求执行**真实检查**（live 代码 + I2 权威版证据核对），非形式确认。

- **目标形态**：`StreamTaskInvokable$RecordWriterOutput`（:611 `private static class`）/ `$BroadcastingRecordWriterOutput`（:660 `private static class`）的 `collect(OutputTag, X)` 方法体（:645-647 / :705-706）——**private 嵌套类方法体行为**，无 public API 暴露。
- **`Output` 公共接口零变更**：`io.nop.stream.core.operators.Output.java:41` 接口 `collect(OutputTag<X>, StreamRecord<X>)` 已存在于 :59；修复只改 private 类方法体，接口签名 / 默认方法 / 其他实现类（ChainingOutput / TimestampedCollector）零改动。
- **RecordWriter 线协议零变更**：修复不触碰 `RecordWriter` / `ResultPartition` / 跨 task 序列化 / 路由路径。
- **模块边界零变更**：修复全部落在 `StreamTaskInvokable.java` 单文件内，无跨模块依赖变化、无新增公共内部机制。
- **Rule #24 合规**：空体 no-op（静默）→ 显式 fail-fast（抛异常），非静默降级；先例 = `ChainingOutput.java:119`（`ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` + `ARG_OUTPUT_TAG` / `ARG_DETAIL` 参数，RL-7 修复 `b20fcd0e1` 落地）。
- **I2 权威版证据复核**：`red-list.md` C2-RL-1/2（I6 §6.2 预裁决 + pin / 注册表分类 + 接线路径复核）与信封边界全部一致；**I2 权威版未显示 `Output` 接口被改动、未显示修复需动模块边界、RWO/BRWO 确认为 private 嵌套类**（§2 裁定表 + §0 复核表在案）。
- **结论：信封通过** → C2-RL-1/2 确认派发 = **自动修复信封内**（interim fail-fast，同 RL-7 先例），**无「需人工确认」门**；I4 派发清单按信封内标注（§8 WI-C2-1）；`HG-01` 线协议支持**明确排除**（不在本工作项范围，I6 已登记待办延续）。
- **失败分支**：未触发（信封复核全部边界项通过；无 `NEEDS_I2_SUPPLEMENT`、无升级阻塞）。

### 7.4 零悬挂复核（Phase 2）

| 条目 | 严重度 | 处置 | 落点 |
|---|---|---|---|
| C2-RL-1 | P1 | 派 I4（interim fail-fast，信封内） | §8 WI-C2-1 |
| C2-RL-2 | P1 | 派 I4（联合） | §8 WI-C2-1 |
| C2-RL-3 | P3 | Follow-up Backlog | roadmap「## Follow-up Backlog」（2026-08-12 新增，C2-RL-3 + C2-PR-4 合并条目，§10） |
| C2-PR-1 | — | 关闭（记录在案） | §7.2 |
| C2-PR-2 | — | 关闭 + 移交 I6（扩展候选） | §7.2 + §9 |
| C2-PR-3 | P3 | Follow-up Backlog | roadmap「## Follow-up Backlog」（2026-08-12 新增，§10） |
| C2-PR-4 | P3 | Follow-up Backlog（与 C2-RL-3 合并） | roadmap「## Follow-up Backlog」（§10） |
| C2-PR-5 | — | 关闭 + 移交 I6（扩展候选） | §7.2 + §9 |

**复核结论**：8 条（3 red list + 5 探查发现）全部有处置，处置与派发一一对应（1 个 I4 工作项 + 2 条 backlog + 4 条关闭 / 移交 + C2-PR-2/5 显式移交 I6），**零悬挂达成**，无「已裁决但无处可去」项。

## 8. Cycle 2 / I3 P0/P1 派发清单（I4 工作项，按族组织）

> I4 计划（plan `2026-08-12-1217-11-...`）以本清单为输入起草。每条含：目标类 + 缺陷描述 + 预期行为 +
> **类别清扫范围**（grep 全类兄弟，roadmap「类别清扫强制」）+ 测试要求（test-first 先红后绿）+ 门禁复跑要求。
> **结构性重构标注**：本清单 1 个工作项 = **类内部行为修复**（private 嵌套类方法体），`Output` 公共接口零变更 /
> RecordWriter 线协议零变更 / 模块边界零变更 → **信封通过（§7.3），无「需人工确认」门，显式声明**。

### WI-C2-1. 族 #6 — 跨 task interim fail-fast（C2-RL-1 + C2-RL-2 联合，I6 §6.2 层次 1 预授权确认）

- **目标类**：`StreamTaskInvokable$RecordWriterOutput` / `$BroadcastingRecordWriterOutput`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java`，private 嵌套类）
- **缺陷描述**：`collect(OutputTag, X)` 空体 no-op（RWO :645-647，:646 仅注释「Side outputs not supported in cross-task exchange」/ BRWO :705-706 空体无注释）→ 跨 task 多 vertex 部署下 tail 算子（WindowOperator / CepOperator / ProcessOperator）6 发射点的 side-output 发射全部静默丢弃（契约违约，无 fail-fast；RL-7 同族残余）。
- **预期行为**：无注册消费者时抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 风格异常（先例 `ChainingOutput.java:119`，含 `ARG_OUTPUT_TAG` / `ARG_DETAIL` 参数），**禁止静默丢弃（Rule #24）**；RWO :646 注释同步更新与 fail-fast 语义一致。
- **类别清扫范围**：全部 main `Output` 实现类（4 个：ChainingOutput / TimestampedCollector / RWO / BRWO——兄弟行为核对，RWO/BRWO 为同缺陷双实例）+ 全部 `collect(OutputTag` call-site（6 发射点 + TimestampedCollector:98 转发）+ 接线链（`GraphExecutionPlan.java:454-458` → `StreamTaskInvokable.wireOperators :239/:245` / `wireTailToRecordWriter :352`）。
- **测试要求（test-first 先红后绿）**：翻转 `TestOutputContractInvariant` pinned-known-violation 分支反射断言（RWO `partition.size()==0` / BRWO `sideReceived.size()==0` → 无消费者 fail-fast 断言）+ 跨 task 端到端 fail-fast 用例（RWO / BRWO 两路径，含接线可达验证）。
- **门禁复跑要求**：`node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（scan-output-contract 零命中）+ `TestOutputContractInvariant` 全绿 + `./mvnw test -pl nop-stream -am -T 1C` 全量 BUILD SUCCESS。
- **收尾三连（I4 内，三处一致）**：① 注册表分类更新 = RWO/BRWO `pinned-known-violation` → `fail-fast`（含 `TimestampedCollector` disposition / subSemantics 同步——其现文「包装 RWO = 等价跨 task 丢弃」在修复后不再成立，需同步为包装 fail-fast 对象 = fail-fast 语义）；② 过渡 pin 2 条移除（`RWO-cross-task-noop` / `BRWO-cross-task-noop`，removalTrigger 已满足，禁静默移除）；③ 门禁复跑零命中（注册表 + JUnit 断言 + pin 移除三处同步一致）。
- **信封边界（显式声明）**：信封通过（§7.3）→ 无「需人工确认」门；`HG-01` 线协议支持**明确排除**（不属本项，I6 待办延续）。
- **兜底核对**：I2 权威版仍含 C2-RL-1/2（§2 pin 裁定均维持，未 verified）→ 派发成立；「部分 verified」边缘未触发（2 条 pin 均维持，无已 verified 条目）。

### 其余 P0/P1

**显式声明：无其他 P0/P1 项**——C2-RL-3 = P3（治理 / 文档措辞）；C2-PR-1/2/5 = 非 defect（关闭 / 移交 I6）；C2-PR-3/4 = P3（backlog）。

## 9. Cycle 3 派生登记（Cycle 2 / I3 版）

- **显式声明：无新独立族**（I2 权威版 §3/§4 结论复核一致——全部发现属已知族（不变式 #6 输出契约族）或其后继扩展候选）→ **无 PD-16 派生登记**（PD-16 = 本仓下一铸号：`max(lessons 最高编号 14, 已铸 PD 最大值 15) + 1 = 16`，live grep 复核无已铸 PD-16；编号保留，由 I6 按需铸造）。
- **已知族扩展候选（显式移交 I6 Loop Rule 评估，本小节为 I6 输入记录）**：
  - **C2-PR-2（门禁表达扩展候选）**：建议 Cycle 3 / I1 评估 scan-output-contract 增加匿名类 / record Output 实现形态显式 fail 或枚举，及 raw OutputTag 声明覆盖；触发证据 = `ai-dev/tools/check-nop-stream-invariants.mjs` parseTypeStructure :622-652 / V4 声明 regex :795-799；当前 0 实例。显式裁定「不派生 PD-16」（依据 = 非新失败类、无失败实例、已知族门禁表达扩展）。
  - **C2-PR-5（不变式 #6 陈述扩展候选）**：不变式陈述扩展候选 =「跨 task 部署下 Output 控制面方法（emitWatermarkStatus / emitLatencyMarker）不得静默丢弃，或显式 fail-fast / 文档化」；触发证据 = `StreamTaskInvokable.java:640-642/:650-652/:701-702/:709-710`；修复需线协议扩展（`HG-01` 门）。显式裁定「不派生 PD-16」（依据 = 非独立新族、同根因族扩展候选、控制面遥测非数据丢失）。
- **移交**：两条候选显式移交 I6（本 plan Non-Blocking Follow-ups 第 2 条），I6 按 Loop Rule 评估正式追加 Cycle 3 / I1 work item 或维持稳态。

## 10. Cycle 2 / I3 Follow-up Backlog 登记

> 已裁定处置（附依据即合规），不驱动独立修复计划；当 I4/I5 类别清扫或复探触发其适用场景时评估修复。落点 = roadmap「## Follow-up Backlog」。

- **C2-RL-3 + C2-PR-4**（注册表 disposition 措辞过 claim + 6 发射点 E2E 覆盖缺口，P3 优化）→ roadmap 新增 backlog 条目「输出契约族审计证据准确性 + E2E 覆盖扩展」（2026-08-12）。
- **C2-PR-3**（同一 OutputTag 重复注册 last-wins 静默覆盖，P3 优化）→ roadmap 新增 backlog 条目「side-output 消费者重复注册语义」（2026-08-12）。

---

## 11. Cycle 2 / I6 收口裁定记录（稳态暂停，2026-08-12）

> Status: active（I6 落档，plan `2026-08-12-1217-13-nop-stream-invariants-cycle2-I6-closure-and-trigger-determination.md`）
> Consumption: 后续 cycle 触发时引用（复触发条件见 roadmap §Loop Rule「Cycle 2 / I6 稳态登记」）；`HG-01` 人工确认门延续
> 输入核对：`cycle2-I6-input.md`（I5 唯一落点）与 on-disk surefire 一致（2833 tests / 0 failures / 0 errors / 10 skipped；门禁 10 类 / 102 tests / 0 failures；mjs pin 0 + `all` exit 0 实测）；red list 零悬挂（C2-RL-1/2 修复 `88bc0270c` 在案 + C2-RL-3 P3 已裁决）；新族数 = 0。

### 11.1 收口统计确认（2026-08-12 I6 实测）

- 全量 2833 tests / 0 failures / 0 errors / 10 skipped（逐模块与 `cycle2-I6-input.md` 精确一致）；门禁 10 类 / 102 tests / 0 failures（CheckpointIDCounter 8 / SynchronizedCollection 12 / OutputContract 10 / core 表完备 10 / WindowRoundTrip 9 / ClusterRegistry 10 / MergingCleanup 4 / runtime 表完备 11 / CepRelease 21 / cep 表完备 7）；mjs `all` exit 0（pins 0，`mjs-pins.json` pinnedViolations = []）；e2e 4/4 + 6/6 + 7/7（I5 记录）。
- 报告 mtime 窗口核对：448 份 TEST-*.xml mtime = 2026-08-12 23:15-23:16（I5 执行窗口内）；I5 后无后续构建（git log 最后提交 = I6 plan 建档 `2286ddc2d`）→ **无出入**。

### 11.2 稳态 / 派生总裁定：**稳态暂停**（Cycle 3 不派生）

- **判定输入**：零新族（0）+ red list 零悬挂（C2-RL-1/2 修复 + C2-RL-3 P3 裁决）+ 门禁全绿 → Loop Rule「零新族且 red list 零 → 稳态暂停」成立；「新族强制沉淀」强制分支未触发（C2-PR-2/5 = 非新失败类，I3 §9 显式裁定）。
- **C2-PR-2（扫描器静默跳过形态）→ 维持稳态**：0 实例（grep 实测无匿名 / record Output / raw OutputTag）；显式 fail 五路径 + self-test 已覆盖（fail-loud）；派生 = 完整循环机器 vs 零实例收益不匹配；复触发 = main 出现匿名 / record Output 实现或 raw OutputTag 声明。
- **C2-PR-5（控制面方法陈述扩展）→ 维持稳态**：控制面遥测降级非数据丢失；修复依赖 `HG-01` 线协议人工确认门（执行门未过，派生则循环在 I4 停滞）；RWO :644/:662 已注释文档化、BRWO :713/:730 空体无注释（code-style 级观察已登记）；复触发 = `HG-01` 人工批准后评估不变式 #6 陈述扩展与 RWO/BRWO 控制面处理。
- **`HG-01` 处置**：**未批准**（全仓无人工批准记录）→ 维持 backlog 待办条目 `pending human confirmation`；四重保护性覆盖复核在案（① in-task fail-fast `88bc0270c`/`b20fcd0e1`；② 门禁类级枚举 / call-site 注册表 / 三态分类；③ pin 移除留痕；④ backlog 条目）；不登记 Successor plan（执行门未过）。

### 11.3 复触发登记摘要

- 三选一继续生效：① CI 任一不变式门禁变红；② nop-stream 核心类结构变更（新增/重命名 Operator/SinkFunction/Checkpoint 机制/Output 实现类）；③ 周期复探（默认每 major release 或季度，取早）。
- 本 plan 新增触发：C2-PR-2 形态出现（main 匿名 / record Output 实现或 raw OutputTag 声明）；C2-PR-5 = `HG-01` 人工批准跨 task 线协议变更（含不变式 #6 陈述扩展评估）。
- 人工确认待办触发 = 人工批准跨 task 线协议结构性变更（`HG-01`，登记见 roadmap Follow-up Backlog）。

---

## 12. Cycle 3 / I3 裁决表（权威版，2026-08-13）

> Status: active（Cycle 3 / I3 产出，plan `2026-08-13-0805-3-nop-stream-invariants-cycle3-I3-adjudication.md`）
> Source: Cycle 3 / I2 权威版 `red-list.md` §0-§4（C3-RL-1..10 + C3-PR-1..8，2026-08-13 实测）；`cycle3-I2-probing-report.md`
> Consumption: Cycle 3 / I4（下轮 mission-driver 另立 plan，以 §13「P0/P1 派发清单」为输入）；I6 收口（以 §14 派生登记为输入）
> Semantics: **Cycle 3 / I2 red list 逐条一行，零悬挂**——严重度（P0-P3）+ 族归属 + 处置 + 依据，禁止无依据裁决。

### 12.1 假设复核记录（Phase 1）

> 本 plan 的 Current Baseline 假设以 Cycle 3 / I2 完成前的登记基线为输入；I2 权威版（`red-list.md`，2026-08-13 I2 completed 后）逐条对照复核如下。I2 已 `completed`（plan `2026-08-13-0805-2-...` Status: completed，Closure Audit APPROVE），按 I2 权威版修正基线。

| # | 本 plan 假设 | I2 权威版事实 | 差异处置 |
|---|---|---|---|
| 1 | red list 条目集 = 门禁结果（预期全绿）+ 注册表裁定 + 探查发现（可能含 checkpoint/watermark 同族实例、新族候选） | 权威版 §1 = **C3-RL-1..10**：3 条记录性（C3-RL-1 门禁全绿 / C3-RL-2 注册表 9/9 维持 / C3-RL-3 组合面恢复面无缺口）+ 6 条复探确认项（C3-RL-4..9）+ 1 条处置记录（C3-RL-10）；§3 探查发现 C3-PR-1..8 | 预期内差异（plan 声明「I2 可能增减条目」）→ 按权威版纳入裁决（§12.2） |
| 2 | 仅测试注入复探结果：checkpoint/watermark 服务同族实例「若确认 → 裁决严重度并派发/入 backlog；若未确认 → 关闭条目」 | C3-PR-1：checkpoint/watermark/其他 `set*Service` 逐服务**零 P0-01 同形态新实例**（全部 main 创建+接线）→ 关闭条目（无派发） | 符合 plan 声明（未确认 → 关闭）→ §12.3 记录关闭，不派发 |
| 3 | `TimestampsAndWatermarksOperator` residual 复核：维持 watch-only / 升格修复 | C3-PR-2：:82-84 守卫接线后 PTS 恒非 null、守卫分支生产不可达——plan `2026-08-13-0132-1` 裁定「接线后自然失效，语义不破坏」**成立** | 符合 plan 声明 → §12.3 记录维持 watch-only，不升格 |
| 4 | 恢复路径/时序组合面探查：可能含新族候选 | C3-PR-3（组合面无新 null 面/无重复注入/无顺序破坏）+ C3-PR-4（恢复路径注入面保持）+ C3-PR-8（非族候选全部不升格）——**无新独立族** | 符合 plan 声明（新族「如有」；实际无）→ §14 显式声明「无新独立族」 |
| 5 | P2 backlog 触发评估表：已触发条目入裁决面 | C3-PR-6 触发评估表：open-audit P2-01..08 触发（→ C3-RL-4..8）、P2-09 claim 过期不触发（C3-RL-10）、P2-10 不触发（工具面）；multi-audit P2-11/P2-01/P2-03/P2-05 触发（→ C3-RL-9）、其余逐条一行依据不触发 | 无实质差异 → 按权威版逐条裁决 |
| 6 | 注册表行号（以 live / 注册表 / I2 权威版为准） | 权威版 §0/§2：7 API + 9 接线点 + 消费方 3 类 11 行全部 live 零漂移，内部 9/9 维持 | 无差异（I2 已实测复核） |

**复核结论**：无实质基线差异（#2/#3/#4 均在 plan 声明的预期变化范围内）；**无信息不足条目**（I2 Phase 4 契约满足，每条含裁决输入 → 无 `NEEDS_I2_SUPPLEMENT`，无阻塞升级）。

### 12.2 裁决表（Cycle 3 / I2 red list 逐条）

> 严重度标尺（沿 Cycle 2 / I3 先例）：**P0** = 数据丢失/损坏或核心恢复语义破坏；**P1** = 现实场景正确性/并发安全缺陷；**P2** = 健壮性/资源泄漏/边界场景；**P3** = 次要治理或优化。
> 处置枚举：P0/P1 → 派 I4；P2/P3 → Follow-up Backlog（已裁定处置，附依据即合规）；记录性/已 verified → 关闭（转述 I2 结论不重裁）。

| finding-ID（I2 权威版） | 位置（live 2026-08-13） | 族 | 严重度 | 处置 | 依据（历史严重度 + 影响面 + I2 结论引用） |
|---|---|---|---|---|---|
| C3-RL-1 门禁全绿（记录性） | mjs `all` exit 0 六命令 + JUnit 11 类 112 tests 0 failures + pin 空表 + E2E 8/8（`red-list.md` §0） | —（无缺陷） | — | **关闭**（记录在案，不重裁） | I2 结论：无新违规、无行为漂移、无 stale pin、无新增非 pin red list 项（`red-list.md` §0 处置结论）。无修复项、无派发。 |
| C3-RL-2 注册表 9/9 维持（记录性） | 7 服务注入 API + 9 接线点 + 消费方 3 类 11 行全部 live 零漂移（`red-list.md` §0 复核表 / §2 接线点复核表） | —（无缺陷） | — | **关闭**（记录在案，不重裁） | I2 结论：注册表 9/9 维持、零 pin 维持、三方一致（注册表 ↔ pin ↔ JUnit 断言同源）（`red-list.md` §2）。无修订项。 |
| C3-RL-3 组合面/恢复面核查无缺口（记录性） | 构造注入 vs open vs restoreState vs rebuildTask 四维组合 + snapshot→restore 服务注入面保持（C3-PR-3/C3-PR-4） | —（无缺陷） | — | **关闭**（记录在案，不重裁） | I2 结论：无新 null 面、无重复注入、无顺序依赖破坏；恢复路径注入面保持、timer 注册表恢复闭环在案。判定依据维持，供 I4 参考。 |
| C3-RL-4 checkpoint 协调面（P2-01 checkpointSuccessMap 无界增长 + P2-02 onCompletePersistFailure 不 complete future + P2-03 getNodeLease 无锁 NPE） | `CheckpointCoordinator.java:1117`（put，fail/abort 路径无 remove，:805/:1155/:1204 三处 remove）/ `CheckpointCoordinator.java:821-831`（:826 直接 `set(FAILED)` 绕过 `pending.fail()`）/ `InMemoryClusterRegistry.java:99-108`（:104 自动拆箱 NPE；**live 实测 `getNodeLease` main 零调用方**） | 已知族（open-audit P2 批次触发确认；checkpoint/cluster 面） | **P2**（逐条） | **Follow-up Backlog**（三条合并登记，roadmap 2026-08-13 升级条目） | 历史 open-audit P2-01/02/03 = P2。影响面逐条：**P2-01** = 资源泄漏/健壮性——checkpointSuccessMap 在 fail/abort 路径 put 后无 remove，高频 abort/fail 场景（生产现实场景）下 map 无界增长（内存泄漏），无数据丢失、无恢复语义破坏（checkpoint id 本身单调，不影响恢复正确性），不达 P1 → **P2**。**P2-02** = 健壮性/失败路径语义——`:826` 只 `set(FAILED)` 不 `pending.fail()`，future 消费者（JobCoordinator :1439/:1462/:1485 + GraphModelCheckpointExecutor :353/:485）等待至超时（live 复核均为 `.get(timeout)` 有界等待）——持久化失败被伪装成超时（延迟故障检测、掩盖根因、日志误导），但**不造成数据丢失/损坏**（超时后按超时路径处理，恢复语义仍成立）、无静默吞掉（状态已置 FAILED）→ **P2**（失败路径健壮性，不达 P1 现实场景正确性——最终仍会失败/超时，无错误结果）。**P2-03** = 边界/并发潜伏——`:104` 自动拆箱 NPE（`evictExpiredNodes` 并发移除导致 `leaseStartTimes` 有而 `leaseExpireTimes` 无）；**live 实测 `getNodeLease` 在 main 零调用方**（仅测试调用），当前生产无触发路径（潜伏缺陷，公共 API 面）→ **P2**（边界场景/潜伏并发缺陷；若未来生产消费方出现则需升格 P1，I4 修复候选 = 防御检查或锁内读，对照 `getActiveNodes :134` 防御先例）。 |
| C3-RL-5 WindowOperator.triggerAccumulators 永不裁剪 + 纯 FIRE 不调 clear（P2-04 触发确认） | `WindowOperator.java:2043-2071`（stateKey :2050 只增不删，全文件零 remove，仅 :536 close 置空）；纯 FIRE 路径 :722-727（合并）/ :755-760（常规）不调 triggerContext.clear() | 已知族（open-audit P2-04 触发确认；窗口面） | **P2** | **Follow-up Backlog**（roadmap 2026-08-13 升级条目） | 历史 open-audit P2-04 = P2。影响面 = 资源泄漏/边界——长运行窗口 map 无界（内存泄漏）+ 复发窗口复用陈旧累加器（FIRE 后不 clear，同窗口再次 FIRE 时累加陈旧值——触发状态语义偏差）；无主数据丢失（窗口内容状态本身不受累加器 map 影响）、无恢复语义破坏 → **P2**。I2 验证：C3-PR-5 触发确认（live :2050 只增不删 + 纯 FIRE 双路径不 clear）。修复方向候选 = cleanup timer 路径同步删除 trigger_* 条目 + 合并路径迁移。 |
| C3-RL-6 evictor descriptor 路径不建 elementTimestampsState（P2-05 触发确认） | `WindowOperator.java:451-462`（仅 null-descriptor else 分支建 elementTimestampsState）/ :1337（storeElementTimestamp 早退）/ :924-932（emitWindowContents 拿当前 watermark 兜底）——TimeEvictor 永不驱逐 | 已知族（open-audit P2-05 触发确认；窗口面） | **P2** | **Follow-up Backlog**（roadmap 2026-08-13 升级条目） | 历史 open-audit P2-05 = P2。影响面 = 健壮性/边界——descriptor 路径（builder 恒传 stateDesc = 常规路径）elementTimestampsState 恒 null → `storeElementTimestamp` 早退 → TimeEvictor 永不驱逐（evictor 语义静默失效）；TimeEvictor 标 `@Internal`（内部 API 潜伏），影响 = 内存占用上升（元素不被驱逐）+ evictor 语义不生效，无数据丢失/损坏 → **P2**。I2 验证：C3-PR-5 触发确认（live :451-462 else 分支 + :1337 早退）。 |
| C3-RL-7 合并路径 pane 跟踪键错位 + purge 缺 triggerContext.clear（P2-06/P2-07 触发确认） | `WindowOperator.java:965-995`（computePaneInfo/paneKey = key + SEP + actualWindow）/ :729-731（合并路径清除用 stateWindow——(key, actualWindow) pane 条目泄漏 + DISCARDING 清错命名空间 + purge 无 triggerContext.clear，对照常规路径 :762-765） | 已知族（open-audit P2-06/P2-07 触发确认；窗口面） | **P2** | **Follow-up Backlog**（roadmap 2026-08-13 升级条目） | 历史 open-audit P2-06/P2-07 = P2。影响面 = 健壮性/边界——合并路径清除键基准差异（pane 键 actualWindow vs 清除 stateWindow）→ (key, actualWindow) pane 条目泄漏（资源泄漏）+ DISCARDING 清错命名空间（触发状态/窗口内容清理语义偏差）+ 合并路径 purge 无 triggerContext.clear（与常规路径不对称，触发状态残留）；仅合并窗口 + DISCARDING 组合场景（边界），无主数据丢失 → **P2**。I2 验证：C3-PR-5 触发确认（live :965-995 vs :729-731 键基准差异可证）。 |
| C3-RL-8 CepOperator STEP-5 超时基准错误 + 绕过 TimedOutPartialMatchHandler（P2-08 触发确认） | `CepOperator.java:563-592`（onEventTime STEP-5：:573 `cs.getStartTimestamp() + wt` 判定，start state 为 -1；:579-591 清理不触发 processTimedOutSequences——超时事件静默丢弃；:564 size==1 守卫）+ :636-660（onProcessingTime 同构 :646） | 已知族（open-audit P2-08 触发确认；CEP 面） | **P2** | **Follow-up Backlog**（roadmap P2-08 条目升级登记，附裁决引用） | 历史 open-audit P2-08 = P2；I2 权威版定性 = 「P2 级候选（潜伏地雷）」（red-list.md C3-RL-8 裁决输入 + probing C3-PR-5「破坏性路径巧合安全」）。**独立共识审查（2026-08-13）对首版 P1 升格裁定 REJECT，触发面分析成立 → 修正为 P2**：(a) STEP-5 清理仅在 `partialMatches.size()==1 && completedMatches.isEmpty()`（:564）时触发；`NFA.java:736-747` 事件处理后**无条件重建 fresh start state** → size==1 ⟹ 唯一 partial match 必为 start state（startTimestamp=-1，previousBufferEntry=null——无节点可 release，惰性清理、下一事件重建）；(b) 对 start state，NFA 语义（`isStateTimedOut :352-353` `!isStartState` 守卫）= **永不过期、无超时事件可通知**——「start state 单匹配等待首个事件 → 超时事件静默丢弃」无用户可见损失（handler 无通知义务）；(c) 真实影响面收敛为边界/潜伏：恢复快照中 fresh start 被 skip-strategy 裁剪后、单非 start 匹配 + per-state window（PREVIOUS_AND_CURRENT）边角场景的基准差异（:573/:646 `startTimestamp + wt` vs per-state `prevTs + wt_s`）——无主数据丢失、无恢复语义破坏、非现实场景高频触发 → **P2**（边界/潜伏语义不一致，对齐 I2 候选级）。修复方向候选（backlog 触发时）= 以 `NFA.isStateTimedOut` 语义统一 STEP-5 基准 + 清理前评估走超时通知路径；**触发条件** = I4/I5 类别清扫（CEP 面）或 per-state windowTimes 使用面扩展或复探时评估。I2 验证：C3-PR-5 触发确认（谓词语义与 NFA 逐状态窗口不一致 + 清理不触发 processTimedOutSequences）。 |
| C3-RL-9 multi-audit P2 预枚举候选触发确认（P2-11 beans 重复 id / P2-01 serializer 死字段 / P2-03 RocksDB Options 泄漏 / P2-05 上帝类） | `stream-control-rpc.beans.xml:34` + `stream-data-plane.beans.xml:38/:68`（同一 bean id `streamMessageService`；live 复核两文件同目录 `_vfs/nop/stream/beans/` → 同容器双加载冲突成立）；`WindowOperator.java:151/:161`（keySerializer/windowSerializer 死字段）+ `WindowOperatorFactoryImpl.java:151-189`（createDummySerializer :166-171 反射失败返回 null）；`RocksDBKeyedStateBackend.java:206-210`（Options 无 try-with-resources，对照 RocksDBIncrementalRestore :151 正确写法）；`GraphModelCheckpointExecutor.java`（1728 行，执行与恢复职责混合） | 已知族（multi-audit P2 批次预枚举触发确认——beans 面 / 窗口面 / RocksDB 面 / 架构面，非新族） | **P2**（逐条） | **Follow-up Backlog**（四条合并登记，roadmap 2026-08-13 升级条目） | 历史 multi-audit P2-11/P2-01/P2-03/P2-05 = P2。影响面逐条：**P2-11** = 配置健壮性——同容器双加载冲突（bean id 重复，行为 = 覆盖/失败取决于加载顺序），无数据丢失（IoC 装配面），边界/配置场景 → **P2**。**P2-01** = 死字段（维护性）+ createDummySerializer 反射失败返回 null（契约违约，潜在 NPE——当前反射路径不触发则为潜伏；对照 TimeWindowSerializer 返回真实实例）→ **P2**（边界/潜伏）。**P2-03** = 资源泄漏——RocksDB Options 原生资源未关闭（对照 :151 正确写法），重复打开场景泄漏 FD/内存 → **P2**。**P2-05** = 架构/维护性——上帝类 1728 行（执行与恢复职责混合），重构 = 结构性重构 → 需人工确认（不入 I4 自动信封），治理级 → **P2**（backlog，触发 = 类别清扫/架构专项时评估）。I2 验证：C3-PR-6 §2 表 4 条逐一 live 复核触发成立。 |
| C3-RL-10 open-audit P2-09 引用 claim 过期（multi P0-01 触发条件已解决，处置记录） | `TestWindowOperatorCorrectness.java:551-585/:607-625`（MixedTypeWindowOperator 2 处实例化 :554/:610 + `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT` 断言 :576-581） | 不属 #7 wiring 族——claim 过期处置记录 | —（非缺陷） | **关闭**（处置记录，不派发）+ **backlog 修订**（open-audit P2-09 条目更新为「claim 过期，触发条件已解决」） | I2 结论（C3-PR-7）：multi P0-01（merge fail-fast 零回归测试）触发条件**已解决**——2 处实例化 + 2 个 assertThrows 回归用例在案（:551/:607），open-audit P2-09 声称「全部仍 live 未修复 / 零实例化」与 live 不符 = claim 过期。非缺陷、无修复项 → 关闭；roadmap P2-09 backlog 条目按处置记录修订（roadmap 2026-08-13）。 |

### 12.3 仅测试注入复探 + residual 裁决（转述 I2，不重裁）

> 依据 I2 Phase 3 探查（C3-PR-1/C3-PR-2），I3 记录裁决结论。

| 条目 | I2 探查结论 | I3 处置 |
|---|---|---|
| 同族"仅测试注入"复探（checkpoint/watermark/其他运行时服务） | C3-PR-1：checkpoint 服务（CheckpointCoordinator :581 / CheckpointBarrierTracker :707 / SharedStateRegistry :1327 内部创建）+ watermark 服务（StreamGraphGenerator :458 生产创建）+ 其他 `set*Service`（setTimerService / setCoordinatorRpcService / RPC 面）**全部 main 创建 + main 接线，零 P0-01 同形态新实例** | **关闭**（无派发；plan `2026-08-13-0132-1` Non-Blocking 登记项 = 复探完成可闭合，roadmap 相应条目已闭合记录在案） |
| `TimestampsAndWatermarksOperator` 静默守卫形态 | C3-PR-2：:82-84 守卫接线后 PTS 恒非 null（4 构造无条件注入）→ 守卫分支生产不可达；plan `2026-08-13-0132-1` 裁定「接线后自然失效，语义不破坏」成立 | **维持 watch-only residual**（不升格 red list；如未来接线被移除则需重新评估，触发 = 接线面结构变更时复探） |

### 13. Cycle 3 / I3 P0/P1 派发清单（I4 工作项，按族组织）

> I4 计划（下轮 mission-driver 另立 plan）以本清单为输入起草。每条含：目标类 + 缺陷描述 + 预期行为 +
> **类别清扫范围**（grep 全类兄弟，roadmap「类别清扫强制」）+ 测试要求（test-first 先红后绿）+ 门禁复跑要求 +
> **收尾三连（如适用）**（`wiring-registry.json` 注册表分类更新 / `mjs-pins.json` 过渡 pin 增删 / JUnit 断言三处一致同步——沿 WI-C2-1 先例）。

### 其余 P0/P1

**显式声明：无 P0/P1 项（I4 不立 plan，直接进入 I5 或 I6 判定，本 plan Non-Blocking Follow-ups 第 1 条）**——C3-RL-1/2/3 = 记录性（关闭）；C3-RL-4 = P2（checkpoint/cluster 面三条，backlog 升级）；C3-RL-5/6/7 = P2（窗口面，backlog 升级）；C3-RL-8 = P2（CEP 面，**独立共识审查修正：首版 P1 升格 REJECT，触发面分析（size==1 ⟹ start state、无超时通知义务、惰性清理）证实 I2「潜伏地雷」定性 → 维持 P2**，backlog 升级）；C3-RL-9 = P2（multi-audit 四条，backlog 升级，其中 P2-05 上帝类重构 = 结构性重构需人工确认，不入自动信封）；C3-RL-10 = 处置记录（关闭 + backlog 修订）。

## 14. Cycle 4 派生登记（Cycle 3 / I3 版）

- **显式声明：无新独立族**（I2 权威版 §3/§4 结论复核一致——全部发现属已知族（#7 wiring 族兄弟实例 / open-audit P2 批次 / multi-audit P2 批次）或既有 backlog 批次）→ **无 PD-16 派生登记**（PD-16 = 本仓下一铸号：`max(lessons 最高编号 14, 已铸 PD 最大值 15) + 1 = 16`，live grep 复核无已铸 PD-16；编号保留，由 I6 按需铸造）。
- **已知族扩展候选**：无新增（C3-PR-8 非族候选全部「不升格」+ 理由在案：setCurrentKey/setKeyContextElement1/2 非服务注入面 / HeapInternalTimerService 触发机制对齐 / StreamTaskInvokable 自身 getter 声明）；plan `2026-08-13-0132-3` Non-Blocking（HeapInternalTimerService 对齐）处置闭合。
- **移交**：显式移交 I6（本 plan Non-Blocking Follow-ups 第 1 条），I6 按 Loop Rule 评估（预期 = 稳态判定输入：零新族）。

## 15. Cycle 3 / I3 Follow-up Backlog 登记

> 已裁定处置（附依据即合规），不驱动独立修复计划；当 I4/I5 类别清扫或复探触发其适用场景时评估修复。落点 = roadmap「## Follow-up Backlog」。2026-08-13 新增/升级登记（open-audit 批 8 条 + multi-audit 批 4 条升级 + 1 条修订）：

- **C3-RL-4（checkpoint/cluster 面，P2）** → roadmap P2-01/P2-02/P2-03 三条目升级（触发确认 + 裁决 P2 维持 backlog，附裁决引用）。
- **C3-RL-5（窗口面，P2）** → roadmap P2-04 条目升级（触发确认 + 裁决 P2，cleanup timer 路径同步删 trigger_* 条目）。
- **C3-RL-6（窗口面，P2）** → roadmap P2-05 条目升级（触发确认 + 裁决 P2，descriptor 路径创建时间戳状态或并入窗口内容状态）。
- **C3-RL-7（窗口面，P2）** → roadmap P2-06/P2-07 条目升级（触发确认 + 裁决 P2，pane 键与清除路径统一命名空间基准 + 合并路径补 triggerContext.clear()）。
- **C3-RL-8（CEP 面，P2）** → roadmap P2-08 条目升级（触发确认 + 裁决 P2 维持 backlog——独立共识审查修正，触发条件 = I4/I5 类别清扫（CEP 面）或 per-state windowTimes 使用面扩展或复探时评估）。
- **C3-RL-9（multi-audit 面，P2）** → roadmap multi P2-11/P2-01/P2-03/P2-05 四条目升级（触发确认 + 裁决 P2，beans 二选一加载 / serializer 消费或删字段 / RocksDB try-with-resources / 上帝类重构（架构级，需人工确认））。
- **C3-RL-10（处置记录）** → roadmap open-audit P2-09 条目修订（claim 过期，multi P0-01 触发条件已解决——处置记录非缺陷）。
- **维持 backlog 原状（watch-only residual）**：open-audit P2-10（工具面，未触发）+ multi-audit 其余未触发条目（逐条一行依据在案，C3-PR-6 §2 表）——已裁定处置附依据即合规，不升格不降级。

## 16. Cycle 3 / I3 零悬挂复核（Phase 2）

| 条目 | 严重度 | 处置 | 落点 |
|---|---|---|---|
| C3-RL-1 | — | 关闭（记录性） | §12.2 |
| C3-RL-2 | — | 关闭（记录性） | §12.2 |
| C3-RL-3 | — | 关闭（记录性） | §12.2 |
| C3-RL-4 | P2（P2-01/02/03） | Follow-up Backlog | roadmap「## Follow-up Backlog」（2026-08-13 升级，§15） |
| C3-RL-5 | P2 | Follow-up Backlog | roadmap（2026-08-13 升级，§15） |
| C3-RL-6 | P2 | Follow-up Backlog | roadmap（2026-08-13 升级，§15） |
| C3-RL-7 | P2 | Follow-up Backlog | roadmap（2026-08-13 升级，§15） |
| C3-RL-8 | **P2** | Follow-up Backlog | roadmap（2026-08-13 升级，§15；独立共识审查修正 P1 → P2） |
| C3-RL-9 | P2（P2-11/01/03/05） | Follow-up Backlog | roadmap（2026-08-13 升级，§15） |
| C3-RL-10 | —（处置记录） | 关闭 + backlog 修订 | §12.2 + roadmap P2-09 修订（§15） |
| C3-PR-1（仅测试注入复探） | — | 关闭（零新实例） | §12.3 |
| C3-PR-2（守卫 residual） | — | 维持 watch-only | §12.3 |
| C3-PR-3（时序组合面） | — | 关闭（无缺口，经 C3-RL-3） | §12.2 C3-RL-3 行 + probing C3-PR-3 |
| C3-PR-4（恢复路径） | — | 关闭（无缺口，经 C3-RL-3） | §12.2 C3-RL-3 行 + probing C3-PR-4 |
| C3-PR-5（窗口/CEP 触发确认） | —（证据项，并入裁决） | 并入 C3-RL-5..8 | §12.2 C3-RL-5..8 行 |
| C3-PR-6（P2 触发评估表） | —（评估表） | 触发 → C3-RL-4..9；未触发维持原状 | §12.2 + §15 |
| C3-PR-7（multi P0-01 已解决） | —（处置记录） | 关闭（claim 过期，经 C3-RL-10） | §12.2 C3-RL-10 行 |
| C3-PR-8（非族候选） | —（不升格） | 关闭（不升格 + 理由在案） | §14 + probing C3-PR-8 |

**复核结论**：10 条 red list + 8 条探查发现全部有处置，处置与派发一一对应（无 I4 派发——显式声明无 P0/P1；6 条 backlog 升级 + 1 条 backlog 修订 + 3 条关闭 + 1 条 watch-only 维持 + 探查项全部显式处置），**零悬挂达成**，无「已裁决但无处可去」项。

---

## 18. AR-22 严重级裁定（2026-08-13，plan `2026-08-13-1615-1` Phase 1）

> Status: adjudicated（plan `2026-08-13-1615-1-nop-stream-ar22-timer-key-json-restore-materialization.md` Phase 1 Decision）
> 输入：roadmap backlog AR-22 条目（plan `2026-08-13-1243-1` Phase 1 类别清扫登记）+ 本表 §1 AR-01（P0 先例，键控面）

| finding | 位置（live） | 族 | 严重度 | 处置 | 依据 |
|---|---|---|---|---|---|
| AR-22 Timer 键 JSON 恢复类型漂移（Long < 2^31 → Integer；@DataBean POJO → LinkedHashMap） | `HeapInternalTimerService.java:327-332`（`TimerEntry.fromSerializableForm` 原样 cast）+ `CheckpointSerDe.java:474-475`（@type 路由）+ `WindowOperator.java:558-560/:489-492`（snapshot/restore→open 延迟应用） | 同 AR-01 族（JSON round-trip 数值键类型漂移 → 类敏感 equals 静默 miss） | **P0** | **派 Fix**（本 plan Phase 2，机制对齐 `MemoryStateSerDe.deserializeKey`） | **按 AR-01 先例链四对比项裁定**：① 同机制——JSON 持久化经 TextScanner round-trip，`Long(123)` 写为 `"123"` 恢复成 `Integer(123)`（AR-01 完全同机制，键控面已修，本条目走 operator state 面 `internal-timers`，`deserializeKey` 修复不覆盖）；② 触发面——恢复后 timer 回调 `onEventTime`/`onProcessingTime` 经 `setCurrentKey(firedKey)`（`WindowOperator.java:789-799/:871-877`）访问键控状态，`TypedNamespaceAndKey.equals` 类敏感（Integer vs Long miss）→ 窗口内容/触发结果静默丢失；定时器窗口（EventTimeTrigger/ContinuousEventTimeTrigger/ProcessingTimeTrigger）作业重启场景必达（`TestE2EWindowAggregateRestore` 已证明重启路径可用）；③ 后果——均静默状态丢失，无异常无日志（与 AR-01 相同）；④ 生产可达性——作业级重启 + 窗口定时器为既有功能，storageType=local 为既有配置。**双后端同受影响**：operator state 的 `internal-timers` 走共享 checkpoint JSON 路径（`CheckpointSerDe.serializeOperatorStates`/`deserializeOperatorState`，storage 层后端无关），Memory 与 RocksDB 后端恢复同一路径（RocksDB 仅替换键控面 serde = AR-01 面）。**边界发现（复现测试暴露，落档）**：非 @DataBean POJO 键在 JSON 持久化序列化点即抛 `ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE`（`JsonSerializer.java:157-158` onlyForDataBean 守卫）→ checkpoint 响亮失败，非静默丢失（JsonTool 层 fail-fast，Guide #24 合规）；静默漂移面 = Long 数值键（本 Fix 面）+ @DataBean POJO 键（同 Fix 面覆盖）。 |

**裁定结论**：AR-22 = **P0**（与 AR-01 同机制同后果同触发面，走 operator state 面 `internal-timers`），按 P0/P1 路径以 Fix 落地（本 plan Phase 2）；测试先红后绿证据在案（`TestHeapInternalTimerServiceSnapshotRestore` 两新用例 red 实测：Long 键 `ClassCastException: Integer cannot be cast to Long` + POJO 键 fired key = `LinkedHashMap {id=k1, seq=42}`）。backlog 状态流转见 roadmap AR-22 条目。

---

## 17. Cycle 3 / I6 收口裁定记录（稳态暂停，2026-08-13）

> Status: active（I6 落档，plan `2026-08-13-1040-2-nop-stream-invariants-cycle3-I6-closure-and-trigger-determination.md`）
> Consumption: 后续 cycle 触发时引用（复触发条件见 roadmap §Loop Rule「Cycle 3 / I6 稳态登记」）；`HG-01` 人工确认门延续
> 输入核对：`cycle3-I6-input.md`（I5 唯一落点）与 on-disk surefire 一致（2895 tests / 0 failures / 0 errors / 10 skipped，逐模块一致；门禁 11 类 / 112 tests / 0 failures；mjs pin 0（`mjs-pins.json` pinnedViolations = []）；报告 mtime 11:15-11:16 在 I5 执行窗口内（收口提交 `f5e18a0dc` 11:17:11 为最后提交，无后续构建覆盖；I6 执行前工作树无 src 变更））；red list 零悬挂（C3-RL-1..10 全裁决在案 + C3-PR-1..8 全处置）；新族数 = 0。

### 17.1 收口统计确认（2026-08-13 I6 实测）

- 全量 2895 tests / 0 failures / 0 errors / 10 skipped（core 1465 / runtime 821 / cep 327 / rocksdb 85 / connector 35 / connector-jdbc 32 / connector-batch 35 / connector-debezium 19 / flow 51 / fraud-example 25，逐模块与 `cycle3-I6-input.md` 精确一致）；门禁 11 类 / 112 tests / 0 failures（CheckpointIDCounter 8 / SynchronizedCollection 12 / OutputContract 10 / WiringExistence 10 / core 表完备 10 / WindowRoundTrip 9 / ClusterRegistry 10 / MergingCleanup 4 / runtime 表完备 11 / CepRelease 21 / cep 表完备 7）；mjs `all` exit 0（I5 实测，pins 0）；e2e 8/8（PT 3/3 + CEP 4/4 + Supervision 1/1）；注册表零漂移 live 抽查（wiring 7 API × 9 接线点 + output-contract 6 发射点全部命中）。
- **Cycle 3 执行路径事实**：I1 → I2 → I3 → I5 → I6（I4 不立 plan——I3 §13 显式声明无 P0/P1，`ai-dev/plans/` 无 Cycle 3 / I4 文件）→ 统计口径 = 门禁 11 类 / 112 tests（10 → 11，I1 新增 wiring 族）、red list = C3-RL-1..10 全裁决零悬挂、新族数 = 0。

### 17.2 稳态 / 派生总裁定：**稳态暂停**（Cycle 4 不派生）

- **判定输入**：零新族（0，I3 §14 显式声明「无新独立族」+ I5 零新失败 + 全仓 grep 无已铸 PD-16）+ red list 零悬挂（C3-RL-1..10 全裁决 + C3-PR-1..8 全处置）+ 门禁全绿 → Loop Rule「零新族且 red list 零 → 稳态暂停」成立；「新族强制沉淀」强制分支未触发（I3 §14 显式裁定无新独立族 → 无派生义务）。
- **显式依据（成本 / 收益 / 触发面 / 棘轮影响）**：① I3 §14 显式无新独立族（C3-PR-3/4/8 全部不升格 + 理由在案）；② Cycle 3 无新失败类（I5 全绿 2895 tests / 0 failures）；③ C3-PR-2 watch-only residual（接线后自然失效，语义不破坏）不构成派生输入（§12.3 维持）；④ I1 已沉淀不变式 #7 一等门禁（11 类 / 112 tests + scan-wiring V1–V5）→ 门禁面完整；⑤ 派生 Cycle 4 = 六份 plan + 六轮执行的完整循环机器，为**零新族**的预防性派生——机器重量与收益不匹配；⑥ 复触发登记（三选一 + C3-RL-8 / C2-PR-2 / `HG-01` 触发）可覆盖未来风险。
- **`HG-01` 处置**：**未批准**（全仓 daily log / backlog 无人工批准记录）→ 维持 backlog 待办条目 `pending human confirmation`；四重保护性覆盖复核在案（① in-task fail-fast `88bc0270c`/`b20fcd0e1`；② 门禁类级枚举 / call-site 注册表 / 三态分类；③ pin 移除留痕 `mjs-pins.json` PIN REMOVAL TRACE；④ backlog 条目）；不登记 Successor plan（执行门未过）。

### 17.3 复触发登记摘要

- 三选一继续生效：① CI 任一不变式门禁变红；② nop-stream 核心类结构变更（新增/重命名 Operator/SinkFunction/Checkpoint 机制/Output 实现类）；③ 周期复探（默认每 major release 或季度，取早）。
- 本 plan 新增触发：**C3-RL-8 CEP 面触发条件** = I4/I5 类别清扫（CEP 面）或 per-state windowTimes 使用面扩展或复探时评估（P2 backlog 升级条目触发）；C3-RL-4..9 其余 backlog 条目维持既有触发条件（类别清扫 / 复探 / 配置需求出现），不逐一重裁；**C2-PR-2 形态出现**（main 匿名 / record Output 实现或 raw OutputTag 声明）；**`HG-01` 人工批准**（跨 task 线协议结构性变更，批准后另立 Successor plan）。
- 人工确认待办触发 = 人工批准跨 task 线协议结构性变更（`HG-01`，登记见 roadmap Follow-up Backlog）。

### 17.4 Cycle 3 收口结论（供后续 cycle 引用）

- Cycle 3 = 新族沉淀驱动受限派生（不变式 #7 wiring 存在性门禁落地，I1）+ 全量验证全绿（I5）+ 稳态暂停（I6）。无 I4 修复面（I3 显式声明无 P0/P1）。red list 零悬挂：C3-RL-1/2/3 记录性关闭 + C3-RL-4..9 P2 backlog 升级（附触发条件）+ C3-RL-10 关闭 + backlog 修订；C3-PR-1..8 全显式处置。新族数 = 0 → 无 Cycle 4 派生。`HG-01` 人工确认门延续（未批准）。
