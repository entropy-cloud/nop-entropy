# nop-stream I3 裁决表（Adjudication Table）— Cycle 1 / I3

> Status: active
> Created: 2026-08-12
> Source: I2 权威版 `ai-dev/audits/nop-stream-invariants/red-list.md`（RL-1..7 + WO-1..3 裁定）；I2 探查报告 `ai-dev/audits/nop-stream-invariants/I2-probing-report.md`
> Plan: `ai-dev/plans/2026-08-12-1217-4-nop-stream-invariants-cycle1-I3-adjudication.md`
> Consumption: I4（`2026-08-12-1217-5-...`，以本表「P0/P1 派发清单」为输入）；I6（以本表「Cycle 2 派生登记」为输入）
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
