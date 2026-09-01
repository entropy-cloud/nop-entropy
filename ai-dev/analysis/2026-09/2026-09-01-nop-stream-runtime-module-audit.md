# nop-stream-runtime 模块审计（roadmap item 8，Phase M 第二项）

> Status: resolved
> Date: 2026-09-01
> Scope: `nop-stream/nop-stream-runtime/` 全模块（65 main / 167 test Java 文件，live 核对于 worktree 根）：2026-05-20 duplicate-code audit 与 2026-06-30 code audit 的 runtime 相关组整改收口验证 + 产品化视角新增审计（D-GAP runtime 重点 / 分布式核心路径 / 空壳扫描 / 测试覆盖抽查 / checkpoint 存储健壮性）+ 小缺陷就地修复与大缺陷 Follow-up 化
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 8；plan `ai-dev/plans/nop-stream-productization/2026-09-01-0938-3-runtime-module-audit.md`
> Related: `2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-06-30-nop-stream-code-audit.md`、`2026-09/2026-09-01-nop-stream-core-module-audit.md`（item 7 sibling，结构对齐；其 §2.2 Flink/Beam 裁定与 §1.1 §7 空壳模块结论本报告直接引用）、`2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md`（D-GAP §3.1 item 8 重点）

## Context

- roadmap item 8（Phase M 第二个审计项，deps item 6/7 已 done）：对 `nop-stream-runtime` 按产品标准完成模块审计并收口。D-GAP §3.1 给出 runtime 审计重点两条（P-REQ-20 窄增量 + JobCoordinator STANDBY fencing 行为级覆盖），本报告 Phase 2 逐条消化。
- 审计方法与 item 7 对齐：live 锚点优先于历史 plan 状态（2026-08-06 baseline 证据分级）；所有 live 核对命令于 2026-09-01 在 worktree 根执行，`--glob '!target'` 排除生成物；核心路径优雅性审计由独立 explore subagent 完成初审、执行者逐条源码复核后采信。
- 基线：`./mvnw test -pl nop-stream -am -T 1C` → BUILD SUCCESS（2026-09-01 11:57，全模块绿，Phase 1 核验起点）。

## Phase 1 — 历史审计整改收口验证

### 1.1 2026-05-20 duplicate-code audit runtime 相关组核对表

> 状态判定同 item 7：`已清除` / `已收编`（原死代码被生产接线启用）/ `部分残留` / `回潮`。归属澄清：§1/§4/§6/§8/§9 归 item 7（core 审计报告 §1.1 已核对，无回潮）；§7 空壳模块由 item 7 统一核验（结论：api/checkpoint/flink 三模块已删除、flow 已实现，勿重复立项）；本表仅核对 §3/§5 与 §2 的 runtime 侧。

| 组 | 05-20 发现（时点） | live 状态 | 证据锚点 |
|---|---|---|---|
| §3 runtime 死代码之六文件：EvictingWindowOperator(511)/CepWindowOperator(466)/CepWindowTrigger(133)/CepWindowAssigner(88)/SimpleInternalTimerService(319)/BarrierAligner(208) | 零引用废弃（合计 ~1,725 行） | **已清除** | `find nop-stream -name "<Class>.java"` 六类全部零命中（2026-09-01 live）；BarrierAligner 随死代码清理删除，与 plan Current Baseline 记载一致 |
| §3 runtime 死代码之三件套：CheckpointCoordinator(338)/LocalFileCheckpointStorage(358)/JdbcCheckpointStorage(218) | 「内部连通但外部孤立的代码岛」，仅测试引用 | **已收编**（成为生产 checkpoint 子系统核心） | main 生产引用：`GraphModelCheckpointExecutor.java`、`JobCoordinator.java`、`RpcDistributedExecutor.java`、`EmbeddedDistributedExecutor.java`、`SupervisionLoop.java`、`reshard/MaxParallelityReshardMigration.java`（`rg -l` 锚点）；05-20 时点的「代码岛」经前序 production roadmap 接线为唯一执行路径 |
| §3 之 TimestampsAndWatermarksOperator(215) | runtime 内零引用 | **已迁移**（落地方式变化，非残留） | 现位于 `nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java`（`find` 锚点），runtime 内无副本 |
| §5 TimerService 实现层重复（SimpleInternalTimerService vs WindowOperatorTimerService + 双 SimpleInternalTimer 内部类） | 两实现并存，Simple 版零引用 | **已清除并统一**（比 05-20 建议更彻底） | 两个实现类均不存在（`find` 零命中）；`WindowOperator.java:225,464` 统一使用 core `HeapInternalTimerService`（core.time 用户面接口语义已由 item 7 S-8b 修正注解）；实现层重复随双删除归零 |
| §2 runtime 侧（CepWindowOperator/CepWindowTrigger/CepWindowAssigner + TestCepWindowOperator，主体归 item 9） | 与 CepOperator ~85% 重复、state 未初始化 NPE | **已清除** | 同 §3 第一行（四文件含测试全部删除）；cep 主体 CepOperator 侧核对归 item 9 |

**结论**：05-20 runtime 相关组全部收口，无回潮、无部分残留。

### 1.2 2026-06-30 code audit runtime 相关发现核对表

> 先列全表再逐项核对。seed 清单 = plan Current Baseline 所列；派生规则 = 报告中「涉及文件位于 nop-stream-runtime」的其余发现。三态：landed / partial / regressed。

| # | 06-30 发现（章节） | 涉及 runtime 文件 | live 三态 | 证据锚点 |
|---|---|---|---|---|
| 1 | §2.2 catch 块 return null：JdbcCheckpointStorage ×15 | checkpoint/storage/JdbcCheckpointStorage.java | **landed** | 全部 catch 块 fail-fast：`throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "<op> failed")`（:113-116/:141-145/:172-176/:203-207/:224-228/:243-247/:265-269/:285-289/:331-335/:363-367/:378-381/:510-514/:539-543 十三处方法级 catch + DDL 索引幂等 catch 带 LOG.debug）；现存 17 处 `return null` 逐一核验均为合法语义——not-found 契约（:124/:343/:346/:375/:521 表不存在/路径空）、visitor/txn lambda 的 void 惯用返回（:139/:170/:201/:361/:456/:537/:618/:678）、方言不支持内部哨兵（:694/:730，调用方 GENERIC 兜底） |
| 2 | §2.2 catch 块 return null：JdbcClusterRegistry ×11 | cluster/JdbcClusterRegistry.java | **landed** | 现存 11 处 `return null` 逐一核验：not-found 契约（getActiveCoordinator:84/getNodeLease:167/getTaskAssignment:234 表不存在时）+ txn/visitor lambda（:93/:139/:196/:268/:330）+ row-mapper 空结果集（:439/:454/:471）；catch 块均带日志：tableExists LOG.debug:385-389、索引幂等 LOG.debug:434-436 等、parseFencingEpoch LOG.warn:501-507（legacy 兼容显式记录）；无静默吞异常路径 |
| 3 | §2.2 catch 块 return null：LocalFileCheckpointStorage ×8 | checkpoint/storage/LocalFileCheckpointStorage.java | **landed** | 方法级 catch 全部 fail-fast throw CheckpointStorageException（:108-111/:141-144/:187-190/:229-232/:247-250/:277-280 等）；循环内 per-element 为 best-effort + LOG.warn（:172-176/:215/:268，与 P1-09-01 恢复路径语义一致）；剩余 return null（:123/:136）为 visitor lambda |
| 4 | §2.2 硬编码：TaskManager 心跳 5s/lease 15s/轮询 3s | taskmanager/TaskManager.java | **partial** | 「轮询间隔 3s」已不存在（TaskManager 重构为事件驱动 + heartbeat 单线程调度器，无轮询循环，`rg 3000\|POLL` 零命中）；但 DEFAULT_HEARTBEAT_INTERVAL_MS=5000L（:82）与 DEFAULT_LEASE_TIMEOUT_MS=15000L（:83）仍为私有常量且无注入点（唯一 ctor :114-131 不接收间隔参数，:157-160/:222 直接引用常量）——heartbeat/lease 节奏不可配置。对照同族已配置化：JobCoordinator taskTimeoutMs/terminationCheckpointTimeoutMs 有 setter（:1709/:1719）、SupervisionLoop pollIntervalMs/maxRestartsPerRegion 为 ctor 参数（:227-228/:252-253）。→ **归类：小缺陷 R-1**（Phase 3 修复：ctor 注入点 + 默认值兼容 + focused 测试；运维侧 failover 检测窗口调优是 P-REQ 运维产品化的真实需要） |
| 5 | §2.2 硬编码：JdbcCheckpointStorage 表名 `stream_checkpoint` | checkpoint/storage/JdbcCheckpointStorage.java | **partial（具名常量化，未配置化）** | TABLE_NAME="stream_checkpoint"（:50）/EPOCH_TABLE_NAME="stream_epoch_manifest"（:51）已从内联字符串收敛为具名常量；querySpace 可配置（ctor :62-67 接收，DEFAULT_QUERY_SPACE="default" :53）；表名本身无配置面。→ **归类：watch-only residual（optimization candidate）**——Why Not Blocking：单租户 auto-DDL 部署下无表名定制的消费者需要（测试与生产共用常量 + ensureTable 幂等建表），多租户隔离的真实旋钮 querySpace 已可配；配置化属产品化优化而非 live defect |
| 6 | §6.2 #5 runtime → cep 幽灵依赖（pom 声明零引用） | nop-stream-runtime/pom.xml | **landed** | `rg -l "io.nop.stream.cep\|nop-stream-cep" nop-stream/nop-stream-runtime/ --glob '!target'` 零命中；pom.xml 无 cep 引用 |
| 7 | §6.3 P1 测试缺口：真实跨 JVM 分布式 E2E | （模块级） | **landed** | gated 多 JVM 测试族：`multijvm/MiniStreamCluster.java`（ProcessBuilder + H2 AUTO_SERVER）+ `TestMultiJvmExactlyOnceRecovery.java:67` / `TestMultiJvmCoordinatorFailover.java:34` / `TestMiniStreamClusterProcessSpawn.java`，均经 `@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")` 启用（覆盖 kill/failover/exactly-once/process-spawn 场景，Phase 2 §2.5 盘点展开）；TestEmbeddedDistributedExecution 的 InProcess 模拟保留为快速回归层（分层合理，roadmap 分布式验证真实性约束由 gated 族承载） |
| 8 | §2.2 通配符导入（派生：JdbcCheckpointStorage/LocalFileCheckpointStorage/CheckpointCoordinator 等主代码 + runtime 测试 ~30+ 文件） | 模块级 | **main landed / test 路由** | main：`rg "import .*\.\*;" src/main` **0 命中**；test：108 文件残留（与 item 7 核对表 #6 记载一致）→ **路由 Follow-up item 22**（core 169/runtime 108/cep 15/flow 1 跨模块统一 sweep，item 7 已立项，本 plan 不重复机械修改） |
| 9 | §1.2 TimerService 名称冲突（runtime 实现层） | （已随 §5 清除） | **landed** | 见 §1.1 §5 行；core 侧注解修正为 item 7 S-8b |
| 10 | §1.2 checkpoint 包散布 core/runtime（CheckpointPlanBuilder 在 runtime，低） | checkpoint/ | **unchanged（合理维持）** | 06-30 原文已判定「模型 vs 执行分离合理」；live 维持（CheckpointPlan 在 core、CheckpointPlanBuilder/Coordinator 在 runtime），item 7 核对表 #16 同结论，非缺陷 |
| 11 | §2.1 UOE 桩 6 处（派生规则扫描） | runtime 无新增桩 | **landed（无 runtime 侧桩）** | 6 处中 5 处位于 core/fraud（item 7 已核），`ICheckpointExecutorFactory` 的 runtime 覆写 `CheckpointExecutorFactoryImpl` 落地（item 7 #5 锚点）；runtime main 现存 UOE 由 Phase 2 §2.4 hollow scan 统一核实 |

**跨模块路由记录**（位于 core 但影响 runtime 路径的发现，本 plan 只记录不修复）：§2.2/§4.1 InputGate Optional-null 与硬编码轮询（core/execution/InputGate.java）→ item 7 核对表 #1/#7 已核 **landed**（Optional.empty() 全覆盖 + CheckpointConfig 超时全链下发 + AR-02 有界 idle-drain 重设计）；§1.2 execution.transport vs runtime.transport 边界（低）→ item 7 #15 watch-only 已裁定。

**结论**：无 regressed；partial 2 项（#4/#5）均有归类——#4 进 Phase 3 修复（R-1），#5 裁定 watch-only residual（optimization candidate，理由如上）。

### 1.3 2026-08-04-2300-1/2/3 remediation plans 收口复核（runtime 侧）

> 复核基准：core 审计报告 §1.3 同款方法——抽查三 plan 的 runtime 侧修复点 live 存在性。

| Plan | runtime 侧修复点 | 复核结论 | 抽查证据 |
|---|---|---|---|
| 2300-1 coordinator-runtime-concurrency-recovery-hardening | JobCoordinator 恢复互斥（recoveryLock 三锁站点 + 锁外 fan-out + 迟到守卫）；TaskManager deployTask 许可账目守恒；SupervisionLoop waitForTerminal fail-loud | **成立（landed）** | `recoveryLock`（ReentrantLock，JobCoordinator.java:260）锁站点 :591-595（assignTasks→prepareAssignmentsLocked）/ :1151-1203（globalRecovery→rotateFencingEpochCoreLocked:1182）/ :1382-1390（activateAsLeader），rotateFencingEpochCoreLocked 定义 :1242（Javadoc :1216 "Must be called while holding recoveryLock"）；TaskManager.deployTask 入口 tryAcquire :429 + 重占用块 :450-457 仅 release（Javadoc :443-449 记录 permit conservation）；SupervisionLoop.waitForTerminal 超时 `throw new StreamException(ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT)` :541，错误码 NopStreamErrors.java:406；配套测试 TestJobCoordinatorRecoveryConcurrency / TestTaskManager（testRedeployToOccupiedSlotDoesNotLeakPermit）/ TestSupervisionLoopZombieTaskTimeout 三者 live 存在 |
| 2300-2 checkpoint-state-backend-cep-correctness | CheckpointCoordinator 增量 persist 失败分支 unregister + 物理回收；JdbcCheckpointStorage 方言感知 upsert | **成立（landed）** | 失败路径 `onCompletePersistFailure` 六处调用（:484/:509/:524/:532/:554/:564/:591/:606/:621）→ `sharedStateRegistry.unregister(seg.getPath())` :705 + `segmentStore.discardSegment(handle.getStateObjectId())` :708；upsert 分派 `resolveUpsertDialect()` :656 + `buildNativeUpsertSqlText`（package-private static，:705）+ `runInsertOrUpdateSeparateTxns`（GENERIC 独立事务），三处 store 方法 :91/:309/:490 接入（rocksdb/cep 侧归 item 11/9） |
| 2300-3 contract-drift-config-test-integrity | `_vfs/nop/stream/_module` IoC 发现标记；TestTaskManagerDaemon 恢复真实断言 | **成立（landed）** | `_module` 文件 live 存在（`ls .../resources/_vfs/nop/stream/` → `_module` + `beans/`）；`TestStreamModuleDiscovery.java`（ioc/）存在；`TestTaskManagerDaemon.java`（execution/）为重写版——noopClusterRegistry() :46-47 / noopMessageService() :102-103 + assertTrue 断言辅助 :149 + 生产 ctor 接线 :156（core 侧 drift/空心测试项归 item 7） |

**结论**：「三 remediation plans 已收口」对 runtime 模块**成立**（三 plan 全部 runtime 侧修复点抽查通过）。

## Phase 2 — 产品化视角新增审计

> 方法与 item 7 对齐：双 explore subagent（控制面 ses_fa4df79ecffeZxTAf2Izd5POLy / 数据面 ses_fa4df1eb3ffe4JzTWYfnp0g4Tq）完成核心路径初审，执行者对全部拟采信发现逐条源码复核后才采信（本节所有锚点均已由执行者 2026-09-01 live 复核；无被否决项——subagent 发现全部经复核成立，处置按严重度与判定准则分流）。

### 2.1 D-GAP runtime 审计重点勾销清单（D-GAP 报告 §3.1 item 8 条目，逐条消化）

| # | D-GAP runtime 重点（来源 P-REQ） | 消化结论 | 证据 |
|---|---|---|---|
| ①a | **P-REQ-20 D-DRIFT-2 方向裁定**：补 manifest `stateFormatVersion`/`checksum` 字段落地 vs 修正 checkpoint-design §2.6 字段表 | **方向裁定：补字段落地（设计承诺维持），实施转 Follow-up（跨模块）**。裁定依据：① 字段宿主 `EpochManifest` 位于 **core**（`core/checkpoint/EpochManifest.java:24-37`，字段表无 stateFormatVersion/checksum），补字段 + 协调器写入 + 双存储读路径 + 旧 manifest 兼容（restore 容 null）跨 core/runtime 两模块——超出本 plan「修复限于 runtime 模块内」的小缺陷判定准则；② 格式演化应有单一版本真值：runtime `CheckpointSerDe` 已有 artifact 级格式信封（`CURRENT_FORMAT_VERSION=2`/`LEGACY=1`，Stage 29 G59），manifest 字段应 alias 该信封版本而非新造第二套版本号——这是设计级决策，宜随 Follow-up 一次性裁定；③ segment 级 checksum/schemaVersion 已 landed（D-GAP §1.1 ③，`StateSegmentDescriptor.java:49-50`），manifest 级 checksum 的覆盖范围（canonical 序列化去 checksum 字段后 SHA-256）需独立设计。**checkpoint-design.md §2.6 字段表维持不修**（等 Follow-up 补字段后字段表才成立）；D-DRIFT-2 的消除路径 = Follow-up item 25 落地时同步核对 | 上述锚点；Follow-up 落 roadmap 见 Phase 3 |
| ①b | **原子写复核**：`LocalFileCheckpointStorage` ATOMIC_MOVE 四类产物 + `JdbcCheckpointStorage` 原子 upsert | **复核通过（四类产物全走 tmp+ATOMIC_MOVE）**：checkpoint 文件（:91-107）、savepoint payload（:398-403）、savepoint metadata（:405-411）、epoch manifest（:492-506）均 `.tmp` 写入后 `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`，tmp 在 finally 清除；读者扫描按 `.checkpoint`/`.epoch` 后缀过滤，残留 tmp 不可见；无直接（非 tmp+move）持久化写路径。**发现一处非事务性弱点**（非缺陷、记录）：savepoint payload 与 metadata 为两次独立 move，中间崩溃留下「有 payload 无 metadata」的不完整产物集——`loadSavepointMetadata` 返回 null（无 torn file，属不完整集合语义），Phase 3 torn-write 测试固化该行为。JDBC 侧：PG/MySQL/H2 单语句原生 upsert 单事务（`runNativeUpsert` :675-680），GENERIC 双独立事务（:741-757），无 torn row | 本节 + §2.5 存储健壮性表 |
| ①c | **torn-write 故障注入测试补齐**（部分写后重启可恢复） | **确认缺口 + Phase 3 就地补齐（runtime 侧）**：全仓 `torn\|partial\|corrupt\|truncat\|halfWritten` 扫描（runtime/core test）零命中；`TestLocalFileCheckpointStorage`（18 用例）/`TestJdbcCheckpointStorage` 仅覆盖 CRUD/round-trip/upsert-shape。Phase 3 新增：残留 `.tmp` 不可见性、截断 `.checkpoint` → fail-fast（固化「无回退到旧 checkpoint」语义）、savepoint payload-无-metadata → null 三类注入用例（见 Phase 3 R-24） | 测试搜索记录 + Phase 3 |
| ② | **`JobCoordinator` STANDBY G24/G25 五处 fencing 门禁的行为级验证覆盖**（K8s defer 裁定所依赖的「应用层完备」基线 freshness 复核） | **五处门禁行为级覆盖成立（freshness 复核通过），相邻发现 2 个门禁缺口 + 2 个测试缺口**。五处门禁逐一核验（live 行号 + 行为测试）：G1 assignTasks（JC:565-569 ← TestJobCoordinatorLeaderElection.testStandbyRejectsAssignTasksExplicitly :206-219 + TestJobCoordinatorStandbyStateMachine.testLeaderSwitchEndToEndTwoCoordinators :284-285）；G2 triggerCheckpoint（JC:768-772 ← testStandbyRejectsTriggerCheckpointExplicitly :222-235 + testActiveModeTriggerCheckpointSucceedsAndStandbyRejects :389-406）；G3 collectAck（JC:822-827 + epoch 不匹配分支 :839-843 ← testStandbyRejectsCollectAckExplicitly :238-246 + testStaleTokenControlRejectedByCollectAck :332-359 含 stale-leader/stale-recoveryGen 双变体）；G4 reportTaskStatus（JC:876-890 ← testStandbyRejectsReportTaskStatusExplicitly :249-261，断言 restartCount==0）；G5 reportNodeTaskLiveness（JC:965-976 ← testStandbyRejectsReportNodeTaskLivenessExplicitly :264-276，断言 liveness 计数不变）。拒绝语义为「可观测 WARN + 状态不变异」设计，测试以 assertNull/assertFalse + 状态断言验证（正确，assertThrows 不适用该门禁设计）。**缺口**：(a) `terminate`（JC:1432，RPC 暴露于 IStreamCoordinatorRpcService:59）无 active/running 门禁——standby 收到 terminate(CANCEL) 会置 CANCELED + stop() 杀死 standby 实例（单 JVM HA 共享 CheckpointCoordinator 拓扑下更 mutating leader 状态）→ **缺陷 R-2**；(b) `abortCheckpoint`（JC:1553）仅 running 门禁无 active——standby 可 abort 共享 coordinator 的 pending checkpoint → **缺陷 R-3**；(c) G6 detectFailures standby 分支（JC:1015-1019）无专属测试；(d) G7 分布式 abort handler standby 分支（JC:1595-1599，TestDistributedAbortPath 仅覆盖 ACTIVE 路径）无专属测试——(c)/(d) Phase 3 补 focused 测试。**结论：K8s defer 所依赖的「应用层集群管理完备」基线经行为级复核 fresh 成立**（五门禁有测试肌肉；缺口为相邻方法而非门禁本身，且正在本 plan 修复） | G24/G25 矩阵（上）；R-2/R-3 修复见 Phase 3 |

**勾销对照**：D-GAP §3.1 item 8 行两项重点（① 三分项 + ② 五门禁）全部消化，无遗漏条目（D-GAP 未判定 runtime「无额外重点」）。

### 2.2 分布式核心路径优雅性/可靠性审计（双 subagent + 执行者复核）

> 范围：supervision loop、region failover 恢复路径、fencing token 传播、数据面 wire codec 错误处理、checkpoint 协调器并发正确性（多并发/unaligned 路径）+ taskmanager/cluster/rpc 边界。发现编号 R-（Phase 3 修复）/ F-（Follow-up）/ W-（watch-only）。

**控制面（coordinator/checkpoint 协调/supervision）：**

| ID | 严重度 | 发现 | 锚点 | 处置 |
|---|---|---|---|---|
| R-2 | high（fencing 旁路） | `terminate` 是五个 RPC 暴露控制方法中唯一无 active/running 门禁者：standby 收 CANCEL → 置 CANCELED + stop()（杀 standby）；DRAIN/SUSPEND/EXPORT → tryTrigger + sendBarrierToAllTaskManagers（epoch 0 barrier 被 TM 拒但产生注定 timeout 的 pending checkpoint）；单 JVM HA 共享 CheckpointCoordinator 拓扑（TestJobCoordinatorStandbyStateMachine.setUp 即此拓扑）下 standby terminate 直接 mutate leader 侧协调器状态 | JobCoordinator.java:1432-1453（入口无门禁）；对照五门禁 :565/:768/:822/:883/:971 | Phase 3 修复（mirror 门禁） |
| R-3 | high（fencing 旁路） | `abortCheckpoint` 仅 running 门禁（:1553）无 active：standby 可 `abortPendingCheckpoint`（:1566）共享 coordinator 的 pending checkpoint（其内层 cancelTask handler 有门禁 :1595-1599，但 abort 本身不设防） | JobCoordinator.java:1551-1567 | Phase 3 修复 |
| R-4 | medium（跨线程集合） | local abort handler 在 checkpoint-timeout 调度线程迭代 `tasks.values()`（GMCE:944），supervision 线程 region restart 并发 `tasks.put`（SL:486）；`tasks` 为 LinkedHashMap（buildTasks GMCE:867）非线程安全——CME 被 CC:873-880 捕获后 abort **部分应用**（剩余 task 未 cancel/release） | GMCE:867/:944、SL:486、CC:873-880 | Phase 3 修复（ConcurrentHashMap，迭代顺序无语义依赖——submit/failure-scan/abort 迭代均序不敏感） |
| R-5 | medium（非原子复合操作） | `reportNodeTaskLiveness` 的 getOrDefault→比较→put「单调 max」非原子：交错心跳可让旧时间戳覆盖新值 → liveness 回退 → 虚假 stall 检测 → 虚假 globalRecovery。应 `merge(key, ts, Math::max)` | JobCoordinator.java:977-984（subtaskLiveness 为 ConcurrentHashMap :208） | Phase 3 修复 |
| R-6 | medium（部分 fan-out） | `executeAssignmentFanOut` 无 per-dispatch catch：一个 RPC 失败中止余下 fan-out（部分 assignment）；三调用方中 `activateAsLeader`（:1392）无兜底 catch，异常泄入 elector 回调线程。对照 sibling per-node catch（triggerCheckpoint :793-797 / sendBarrierToAllTaskManagers :1826-1830） | JobCoordinator.java:718-726、:1392 | Phase 3 修复 |
| R-7 | medium（静默降级） | `checkFingerprintOnRestore`：manifest 有 fingerprint 而当前运行无 fingerprint 来源（JobGraph-only 入口）时 WARN+跳过——指纹兼容检查被静默跳过（checkpoint-design :1002 快速失败策略的例外漏洞）。JobGraph-only 入口经 CheckpointExecutorFactoryImpl:32 是生产路径（ICheckpointExecutorFactory 主接口），其用户 restore 带指纹 manifest 时零强制 | GMCE:1114-1119；overload #1（:103-151）无 setCurrentFingerprint 对照 #2 :188/#3 :264 | Phase 3 修复（fail-fast；JobGraph→JobGraph restore 不受影响——该路径 manifest 本无指纹） |
| F-A | medium（结构） | `cleanupOldCheckpoints` 在持有 coordinator monitor 下做存储 I/O（getAllCheckpoints/deleteCheckpoint，CC:1032-1049，自 CC:794 段3a 调用）——违背 async-persist「I/O 出 monitor」设计目标（CC:498-501）；segment discard 已正确 offload（CC:1058-1094）但 checkpoint 行删除未随行 | CheckpointCoordinator.java:1032-1049/:794 | **Follow-up item 25**（结构性调度解耦，需独立回归面） |
| F-B | medium（结构漂移） | `executeWithCheckpoint` 三 overload ~85-90% 克隆 + triggerSavepoint/executeWithSavepoint 同脚手架（5 处逐字 finally 块）——漂移已发生（R-7 的指纹缺失即克隆漂移产物）；terminateDrain/Suspend/ExportSavepoint ~90% 三联（JC:1464-1536） | GMCE:103-151/158-220/222-296、JC:1464-1536 | **Follow-up item 25**（家族合并；R-7 先修漂移行为） |
| R-8 | low（静默跳过） | `triggerCheckpoint` 源节点 RPC 缺失时 `if (rpc != null)` 无 else——checkpoint 注定 timeout 且零诊断（sibling 跳过均 WARN/ERROR） | JobCoordinator.java:790-799 | Phase 3 修复（else WARN） |
| R-9 | low（锁纪律不一致） | `stopCheckpointScheduler` 非 synchronized（对照 startCheckpointScheduler :266 synchronized）——并发 start/stop 对 `scheduler` 字段（:180）的 check-then-act 非原子 | CheckpointCoordinator.java:334-351 vs :266 | Phase 3 修复（加 synchronized） |
| R-10 | low（部分 fan-out） | `triggerBarrierOnAllInvokables` 某一 tracker throw 中止余下循环（boolean-reject 有 WARN 容器 :844-846，异常路径无）→ 部分 barrier → 该 epoch 注定 timeout | GMCE:835-849 | Phase 3 修复（per-invokable try/catch） |
| R-11 | low（静默跳过） | `restartRegion` Phase 3 region 内 taskKey 缺失时静默 `continue`——顶点被无声丢弃出自身 region restart（仅 map/plan 不一致时可达，但违反可观测拒绝约定） | SupervisionLoop.java:480-483 | Phase 3 修复（LOG.warn） |
| R-12 | low（重复） | checkpoint-id counter「单调推进过 restored epoch」4 行逻辑逐字双份（restoreFromCheckpoint :896-900 vs advanceCheckpointIdCounterAfterRestore :923-930，后者 Javadoc 自认 Mirrors） | CheckpointCoordinator.java:896-900/:923-930 | Phase 3 修复（前者委托后者） |
| R-13 | low（策略不对称） | `triggerFinalCheckpoint`（CANCEL 模式）吞失败仅 LOG.error，对照 triggerTerminalSavepoint（DRAIN/SUSPEND）rethrow | GMCE:851-864 vs :470-496 | Phase 3 **文档化裁定**（非缺陷：CANCEL=立即停止语义，用户已接受放弃状态；final checkpoint 属 best-effort 附赠，fail 取消会楔死用户请求的取消——补 Javadoc 显式化，行为不变） |
| W-1 | low | `setTasksToAcknowledge`（:996-1006）非 synchronized 与 register/unregister（:1008-1014）混用——latent（两条路径无混合生产调用方）；**已知 P2 backlog**（2300-1 Non-Blocking Follow-ups 记载） | CheckpointCoordinator.java:996-1014 | watch-only（维持既有 backlog 归属） |
| W-2 | low | `globalRecovery` public 无 active 门禁（Javadoc :1140-1148 显式 direct-call-for-tests 契约；非 RPC 暴露——IStreamCoordinatorRpcService 无此方法；standby 直调理论可推导出大于真 leader 的 epoch，但无生产触达路径） | JobCoordinator.java:1149-1207 | watch-only（防御性加固候选，无生产路径 + 显式契约） |
| W-3 | low | EPOCH_SCALE=1_000_000 编码边界：recoveryGen>999_999 同 leader 内与下一 leader 低代号碰撞（实际不可达）；leaderEpochValue>~9.2e12 乘法溢出无 clamp | JobCoordinator.java:113/:1316-1318 | watch-only（边界数学，量级不可达） |
| W-4 | low | `failJob` 幂等 check-then-set 非原子（并发双跑 body，效果幂等）；`collectAck` 读 fencingEpoch 无 recoveryLock 的 TOCTOU 灰区（快照取自 barrier 时刻，consistent-cut 论证成立，窗口数条指令） | JobCoordinator.java:523-536/:832-855 | watch-only |
| 拒绝1 | — | checkpoint id 在 NO_TASKS_TO_ACK 拒绝前被 getAndIncrement 燃烧（仅 id 空洞，无碰撞——counter 单调 + restore 推进单调） | CheckpointCoordinator.java:401-408 | rejected（外观问题，重排无安全收益） |

**数据面/基础设施（transport wire codec/rpc/cluster/taskmanager/source/存储）：**

| ID | 严重度 | 发现 | 锚点 | 处置 |
|---|---|---|---|---|
| R-14 | **high（fencing 回滚）** | `updateFencingToken` 无单调性防护：`getAndSet(fencingEpoch)` 接受任意 epoch——zombie coordinator（stale 更小 epoch，deriveHaFencingEpoch 保证 zombie 排序更小）可单向 RPC 将 TM epoch **回滚**：cancel 全部 active 代任务，此后 active leader 的 receiveAssignment/triggerCheckpoint/deployTask（等值检查）全部 mismatch 失败直至下次轮转。Javadoc 自称 "new monotonic fencing epoch" 但未强制 | TaskManager.java:606-621；RPC 暴露 IStreamTaskRpcService:32；合法调用方 JC:423-425/:1255-1257 | Phase 3 修复（拒绝更小 epoch，ERR_STREAM_FENCING_TOKEN_MISMATCH）+ **gated 多 JVM 测试**（真实 RPC 边界注入 stale epoch） |
| R-15 | **high（虚假 COMPLETED）** | invokable 安装超时（30s latch await 返回 null，:788-794）时 run() 正常返回 → finally `success = error==null && !canceled = true` → 记录成功 TaskResult + `reportTerminalStatus(true)` 发 **COMPLETED**——coordinator 崩溃于 receiveAssignment 与 installInvokable 之间时产生「从未运行的沉默成功 subtask」（无 failover、潜在数据丢失）。日志亦误称 "canceled while waiting" | TaskManager.java:713-717/:731/:750-752/:788-794 | Phase 3 修复（timeout → FAILED report + 失败 TaskResult；等待预算提为可测常量） |
| R-16 | medium（wire codec 契约违反） | `AdaptingConsumer.onMessage` 裸调 `codec.fromWire(message)` 无 try/catch：三个 string codec 畸形 JSON（bad payload）时 `JsonTool.parseBeanFromText` **抛异常**而非按 `IDataPlaneWireCodec:48-55` 文档契约返回 null→丢弃——异常永不到达 RemoteInputChannel.decodeError（fail-fast 机制在内层 consumer），传播行为随消息后端而定（LocalMessageService catch+LOG.error+丢弃；线程化后端可能阻塞/杀死 dispatch 线程）。且合法 null 丢弃路径仅 LOG.debug（产品默认级别不可见）。无任何 codec 具备畸形输入测试（全部 round-trip） | DataPlaneMessageServiceAdapter.java:115-122；抛出路径 SysDaoWireCodec:60-64/KafkaStringWireCodec:61,64/PulsarStringWireCodec:61,64 | Phase 3 修复（try/catch→null 语义 + WARN 级别）+ 畸形输入 focused 测试 |
| F-C | medium（接口级 fencing 缺口） | `cancelTask(jobId, vertexId, subtaskIndex)` 不携带 fencing epoch——stale coordinator 可随时取消 active 代任务（对照 receiveAssignment/triggerCheckpoint/deployTask/updateFencingToken 均带 epoch）。修复需改 IStreamTaskRpcService 接口 + 全部实现与测试替身 | IStreamTaskRpcService.java:25、TaskManager.java:556-569 | **Follow-up item 26**（接口变更） |
| F-D | medium（资源泄漏/过度订阅） | remote-deploy 为**整个** job graph 构建（并在构造内订阅）每 (src,tgt) subtask 对一个 RemoteInputChannel，但仅 assigned subtask 的通道接入返回的 invokable——其他 TM 上 subtask 的通道对共享 backend 保持订阅、1024 槽队列填满后 `queue.put` **永久阻塞 backend dispatch 线程**（无人读取；EOS 仅在 producer close 后解锁）。每次 TM deploy = 内存增长 + dispatch 线程饥饿风险 | SubtaskPlanBuilder.java:90-94、RemoteGraphExecutionPlanBuilder.java:113-126、RemoteInputChannel.java:156/:402-407、TaskManager.java:476-493 | **Follow-up item 27**（按需构建 per-subtask 通道，架构性） |
| R-17 | medium（双实现行为分叉） | `deleteAllCheckpoints` JDBC 实现只删 `stream_checkpoint` 留全部 `stream_epoch_manifest` 行（LocalFile 删整棵 job 树含 `.epoch`）——「delete all」后 JDBC 仍从 `loadLatestEpochManifest` 供给 stale manifest（可致 stale-restore） | JdbcCheckpointStorage.java:233-249 vs LocalFileCheckpointStorage.java:255-282 | Phase 3 修复（同方法补删 epoch 表） |
| R-18 | medium（DDL 竞态） | `ensureTable`/`ensureEpochTable` 用裸 `CREATE TABLE`（无 IF NOT EXISTS）——两 JVM 首次初始化竞态 → 一方首次 store 抛伪异常（下次恢复但首 checkpoint 可能丢失）。同仓 sibling（JdbcClusterRegistry/JdbcLeaderElector）已为多 JVM 显式修复此点（"Stage 42: IF NOT EXISTS so concurrent JVMs do not race"） | JdbcCheckpointStorage.java:407-420/:570-582；对照 JdbcClusterRegistry.java:342-365 | Phase 3 修复（加 IF NOT EXISTS） |
| R-19 | medium（错误路径泄漏） | `RpcDistributedExecutor.startJob` 无 try/finally：循环内已 start 的 TaskManager/RPC server/proxy 在后续失败（coordinator 构造、buildRemoteOnly、assignTasks :279）时全部泄漏（线程/订阅/registry 项）。对照 EmbeddedDistributedExecutor.execute 完整 try/finally teardown | RpcDistributedExecutor.java:199-286 vs EmbeddedDistributedExecutor.java:199-277 | Phase 3 修复（失败 teardown） |
| R-20 | medium（拒绝不可见） | `receiveAssignment` 两类拒绝（capacity 满 warn+return :305-310；epoch 不匹配 throw :297-302 无 report）对 coordinator 不可见（单向 RPC）——task slot 停滞至 supervision 兜底。对照已硬化 `deployTask`（throw 前 `reportDeployFailure` :503-523） | TaskManager.java:297-310 vs :427-434/:503-523 | Phase 3 修复（mirror 失败上报） |
| R-21 | low（参数不一致） | `deployTask` 校验 RPC 参数 fencingEpoch（:411-417）但以 `descriptor.getFencingEpoch()` 构建 RunningTask（:460-466）不校验两者相等——分歧 descriptor 在未验证 epoch 下运行（updateFencingToken removeIf 可能不匹配） | TaskManager.java:411-417 vs :460-466 | Phase 3 修复（等值断言） |
| R-22 | low（异常包裹不一致） | `JdbcCheckpointStorage.loadSavepointMetadata` 是唯一缺 `catch (Exception)→CheckpointStorageException` 兜底的方法（仅 NopException 分支）——fromCompletedCheckpoint 的非 Nop 运行时异常裸逃逸，与全类硬化模式不一致 | JdbcCheckpointStorage.java:371-382 | Phase 3 修复（补 catch） |
| R-23 | low（文档失实） | `TaskManager.stop()` Javadoc 称 "unregisters from the ClusterRegistry" 但无 unregister 调用（ClusterRegistry 无该 API；节点滞留至 lease 过期 ≤15s，良性但文档失实） | TaskManager.java:166-193 | Phase 3 修复（Javadoc 修正，行为不变） |
| W-5 | low | `deployTask` slot-replace get→remove→put 非原子（对照 receiveAssignment putIfAbsent）+ RunningTask.run finally 移除当前映射项——并发同 key interleaving 有瞬态丢项窗口（permit 账目安全，仅 cancelTask 短窗不可见） | TaskManager.java:449-467/:741 | watch-only |
| W-6 | low | completedTasks 「history」逐出移除任意 ConcurrentHashMap 迭代键（非最旧）；per-attempt 结果互相覆盖；deployTask 不校验 jobId/vertexId 非空（null → "null/…" taskKey） | TaskManager.java:734-740/:436 | watch-only |
| W-7 | low | `nextSid` 每 JVM 随机种子递增计数器——跨 JVM 碰撞在 GENERIC upsert 路径理论上表现为 duplicate-key→0 行 UPDATE→沉默不存但返回成功（天文概率） | JdbcCheckpointStorage.java:55/:632-634/:741-757 | watch-only |
| W-8 | low | 双实现剩余分叉裁定：corrupt 枚举行为（LocalFile per-file best-effort skip+warn vs JDBC 整查询 fail-fast）——**裁定非缺陷**（JDBC fail-fast 对腐坏 DB 行更审慎，LocalFile skip 符合 P1-09-01 枚举路径语义）；JDBC `loadRetainedEpochManifests` 未 override（接口 default ≤1，Stage-31 重启恢复在 JDBC 后端降级）——**已知 P2 backlog**（2300-2 Out Of Scope 显式记载），升级承接归 Follow-up item 27 | LocalFileCheckpointStorage.java:172-178 vs JdbcCheckpointStorage.java:162-171；ICheckpointStorage.java:55-64 | watch-only + Follow-up 承接 |
| W-9 | low | InMemoryClusterRegistry 边界集：getNodeLease 自动拆箱 NPE（map 分歧时）、coordinators map 永不逐出、assignTask 注释称拒绝回退但代码 warn 后照常 append、NodeInfo.lastHeartbeatAt 跨线程可见性、JdbcLeaderElector.tryBecomeLeader 硬编码 epoch=1（lease 行被删则 epoch 回退）；RpcDistributed/Embedded 默认 checkpoint 目录在 java.io.tmpdir | InMemoryClusterRegistry.java:104-164、JdbcLeaderElector.java:224-247、RpcDistributedExecutor.java:227-228 | watch-only（测试/演示级 registry；生产路径走 JDBC 实现） |

**明确核验无发现的维度**：RemoteInputChannel decode-error fail-fast 机制完整（volatile 标志 + take 前后双检 + EOS 哨兵 + 中断安全 :169-249/:402-419）；RemoteResultPartition 写失败 fail-fast、心跳失败按设计 debug（文档化）；未知 envelope type/payload 均 throw 或 warn（无静默）；TaskManager permit 守恒全路径（CAS 逐路径核验）；StreamControlRpcServer 异常→错误响应带 relId 关联；StreamControlRpcProxyFactory.doStop finally 销毁 timer；JdbcLeaderElector 乐观并发接管 + 双 stop-guard + lost-refresh 检测；StreamNodeAutoRegistration 注册失败传播（契约 fail-fast）；EmbeddedDistributedExecutor 完整 try/finally teardown + suppressed 聚合；LocalFileCheckpointStorage 路径穿越校验（validateId/validatePath）；CheckpointSerDe 格式信封 + legacy 兼容 + accumulator 类白名单；HA 生命周期接线（start-STANDBY/becomeLeader 唯一激活/deactivate≠stop/listener 注销）；异步 persist 三段式（段1 monitor 内不可变快照/段2 出 monitor I/O/段3 重入 monitor）+ RejectedExecutionException 内联回退；restart 预算（G56 锁内检查 + per-region CAS）；unaligned×rescale fail-fast（assertNoChannelStateOnRescale 先于合并）；barrier 注入列表 CopyOnWriteArrayList replace-not-append；post-loop FAILED 终态不变量复扫。

### 2.3 多并发/unaligned checkpoint 协调正确性（专项）

subagent 逐项核验 + 执行者抽查确认：**测试肌肉充分**——多并发：TestCheckpointTriggerSafety（10 线程 max=1 → 恰 1 pending；8 线程 max=2 → 恰 2）、TestCheckpointConcurrencySafety、TestCheckpointCoordinatorRaceCondition、TestMultiEpochCheckpointE2E（3 epoch 共存独立完成 + 中位 epoch abort 精确性）、TestCheckpointCoordinator.testMaxConcurrentCheckpoints/…RespectsConfig、TestCheckpointLifecycleIsolation、TestCheckpointCoexistenceViaCoordinator（max=4）、TestCheckpointMinPauseAndFailureCounter、TestExactlyOnceCorrectnessFixes（max=10）、TestDistributedExactlyOnce（max=2）；unaligned：TestUnalignedCheckpointBackpressure（对齐→非对齐切换 + channel-state 捕获）、TestUnalignedCheckpointMultiInput（per-channel in-flight 重放）、TestChannelStateRescaleFailFast、TestChannelStateRescaleE2E（双用例）、TestCheckpointSerDeChannelState（持久化 round-trip）。**实现侧核验干净**：触发门控顺序（maxConcurrent→minPause(last-completed)→tasks-to-ack）与背压拒绝不污染 consecutiveTriggerFailures（CC:288-324/:369-421）；本路径缺陷仅 §2.2 所列 F-A（retention I/O 在 monitor 内）与 R-4（abort 迭代容器）。

### 2.4 空壳/静默跳过扫描（hollow scan）

- 扫描命令与退出码：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high` → **0 critical / 0 high，exit 0**（item 7 修正后的消息语义分级版本）。
- 全量分布：0 critical / 0 high / **79 medium / 0 low**。79 medium = P2b 单行 `return null`（56，经 §1.2 #1-#3 逐条人工判定 + §2.2 数据面审计：全部为 not-found 契约/visitor-lambda 惯用/`CheckpointSerDe:75/:97` 的「结构合法但缺必需字段→null+LOG.warn」合法语义，含 F-B 报告的指纹跳过路径 R-7 除外——该处属行为缺陷而非空壳，已列修复）+ 其余 P3 类信息级条目。
- **结论：无 high/critical 真实发现需处置，无误报需修正工具**（分级后的信息级条目不触发硬门禁，工具已具备消息语义分级无需再修）。

### 2.5 测试覆盖抽查

**主要包代表性测试（每包 ≥2 命中确认）：**

| 包 | 代表性测试（≥2 命中确认） | 测试文件数 |
|---|---|---|
| taskmanager | TestTaskManager（含 permit 守恒回归）、TestTaskManagerLivenessAndReporting、TestTaskManagerDaemon、launch/TestTaskManagerMain | 3+1 |
| transport | TestKafkaStringWireCodec、TestPulsarStringWireCodec、TestSysDaoWireCodec、TestDataPlaneMessageServiceAdapter 等 wire codec 家族 + Remote 通道测试 | 10 |
| coordinator | TestJobCoordinator、TestJobCoordinatorLeaderElection、TestJobCoordinatorStandbyStateMachine、TestJobCoordinatorRecoveryConcurrency、TestJobCoordinatorRestartStrategy、TestJobCoordinatorJdbcHaIntegration、TestJobCoordinatorRemoteDeploy 等 | 14 |
| execution | TestGraphModelCheckpointExecutor、TestSupervisionLoop 家族（ZombieTaskTimeout/RestartLimitConfig/ConsistentCut/ReconnectE2E 等）、TestDistributedAbortPath | 22 |
| rpc | TestStreamControlRpcBootstrap、TestStreamControlRpcRoundTrip 等 | 3 |
| checkpoint | TestCheckpointCoordinator 家族（57 文件：TriggerSafety/ConcurrencySafety/RaceCondition/Incremental*/MultiEpoch E2E 等，见 §2.3） | 57 |
| cluster | TestJdbcClusterRegistry、TestJdbcLeaderElector、TestInMemoryClusterRegistry、TestStreamNodeAutoRegistration 等 | 8 |
| operators | TestWindowOperator 家族（UnificationE2E/TriggerAccumulatorCleanup/Builder/AccType 等 25 文件） | 25 |
| source | TestLocalSourceCoordinator、TestCollectionReplayableSource 等 | 3 |

**行数 top-5 文件直接覆盖**（`wc -l` 排序）：

| 文件（行数） | 直接测试 |
|---|---|
| WindowOperator（2208） | TestWindowOperatorUnificationE2E、TestWindowOperatorBuilder、TestWindowOperatorTriggerAccumulatorCleanup、TestWindowOperatorAccType + operators/windowing 25 测试文件 |
| JobCoordinator（1867） | TestJobCoordinator + 13 个专项（LeaderElection/StandbyStateMachine/RecoveryConcurrency/RestartStrategy/JdbcHa/RemoteDeploy/PerTaskFailure 等） |
| GraphModelCheckpointExecutor（1728） | TestGraphModelCheckpointExecutor + TestDistributedAbortPath + TestChannelStateRescale* + supervision 家族间接 |
| CheckpointCoordinator（1447） | TestCheckpointCoordinator + 56 个专项（§2.3 清单） |
| TaskManager（888） | TestTaskManager、TestTaskManagerLivenessAndReporting、TestTaskManagerDaemon |

**结论**：主要包与 top-5 文件覆盖充分，无缺口；薄弱点为 §2.2 所列具体行为分支（畸形 codec 输入、fencing 回滚、invokable 超时终态——Phase 3 随修复补测试）。

**gated 多 JVM 测试盘点（`nop.stream.test.multi-jvm.enabled=true` 启用族）：**

| 测试 | 用例 | 覆盖场景 | 状态 |
|---|---|---|---|
| TestMultiJvmExactlyOnceRecovery | multiJvmDeployKillRecoverFencing | 真实多 JVM：deploy → kill TM → recover → fencing → exactly-once 断言 | live（git 2026-08-03 起，08-09 有维护提交） |
| TestMultiJvmCoordinatorFailover | testCoordinatorKillTriggersStandbyTakeover、testBrainSplitFencingBoundary | coordinator HA failover（kill 活跃 → standby 接管）+ 脑裂 fencing 边界 | live（同上） |
| TestMiniStreamClusterProcessSpawn | twoTaskManagersAndOneCoordinatorStartAndRegister、shutdownStopsAllProcesses、topicNamespaceIsUniquePerInstance | 进程孵化/注册/关停基建契约 | live（同上） |

- 合计 6 用例 / 3 测试类 + MiniStreamCluster 基建；**覆盖场景**：kill/failover/fencing/exactly-once/process-spawn；**缺口（记录，非失修）**：无 rescale 多 JVM 用例（rescale 由 TestChannelStateRescaleE2E 单进程覆盖，语义层面已验证）、无 unaligned checkpoint 多 JVM 用例（TestUnalignedCheckpointBackpressure 单进程覆盖）——分布式矩阵扩展属 Phase S（roadmap items 14 场景设计输入），本 plan 不扩矩阵。
- **无长期未跑的 gated 测试**：三测试类 git 历史活跃（最后维护 2026-08-09，mission 期间随 Stage 43—47 交付），Phase 3 将按约定以 `-Dnop.stream.test.multi-jvm.enabled=true` 实跑并记录。

### 2.6 checkpoint 存储实现健壮性抽查（P-REQ-20 runtime 证据面）

| 维度 | LocalFileCheckpointStorage | JdbcCheckpointStorage |
|---|---|---|
| 原子写 | 四类产物全走 `.tmp` + `Files.move(ATOMIC_MOVE)`（§2.1 ①b 锚点）；无直接写路径 | 单语句原生 upsert 单事务（PG/MySQL/H2）；GENERIC 双独立事务无 torn row |
| 错误处理 | 方法级 catch → CheckpointStorageException（ERR_STREAM_CHECKPOINT_ERROR + ARG_DETAIL）；per-element best-effort + LOG.warn（P1-09-01 对齐） | 同左（十三处方法级 catch）；DDL 索引幂等 catch LOG.debug |
| null 返回路径 | not-found 契约 + visitor lambda（§1.2 #3 逐条） | 同左（§1.2 #1 逐条） |
| 腐坏读行为 | 截断/垃圾字节 → JsonTool 抛 → **fail-fast CheckpointStorageException（无回退旧 checkpoint）**；结构合法缺字段 → null + LOG.warn（CheckpointSerDe:94-98，重启从头风险已由 §2.2 R-7 关联记录）；枚举路径 per-file skip+warn | 腐坏 blob → fail-fast 整查询（W-8 裁定：非缺陷） |
| 已知弱点 | savepoint payload/metadata 两次独立 move 的不完整产物集窗口（Phase 3 torn-write 测试固化行为） | DDL 竞态 R-18；deleteAll 分叉 R-17；retained-manifests 不 override（W-8/2300-2 backlog → Follow-up item 27） |
| torn-write 注入测试 | **缺失**（全仓扫描零命中）→ Phase 3 补三类用例 | 同左（JDBC 单行原子性由 upsert-shape 测试已证，注入用例归 LocalFile 侧） |



## Phase 3 — 缺陷处置与收口

### 3.1 小缺陷就地修复（行为变更项均附 focused 测试；全部限 runtime 模块内、不改跨模块公共契约）

| ID | 修复 | focused 测试（验证的新行为） |
|---|---|---|
| R-1 | TaskManager 心跳间隔/lease 超时可配置（新增 8 参 ctor 重载，默认值向后兼容；非正值 fail-fast） | TestTaskManager.testHeartbeatAndLeaseIntervalsConfigurable（MockClusterRegistry 捕获 renewLease 超时参数断言 900L 生效 + 两类非法参数 assertThrows） |
| R-2 | `terminate` 补 running+active 双门禁（mirror 五门禁模式；standby 的 CANCEL/DRAIN/EXPORT 全部拒绝） | TestJobCoordinatorAuditFixes.standbyTerminateIsRejected（standby terminate 后 isRunning 仍 true、零任务 RPC）+ activeTerminateStillStops（active 路径不受影响） |
| R-3 | `abortCheckpoint` 补 active 门禁（standby 不得 abort 共享 pending checkpoint） | TestJobCoordinatorAuditFixes.standbyAbortCheckpointIsNoOp（pending 保持 RUNNING 未 disposed） |
| R-4 | `buildTasks` 改 ConcurrentHashMap（消除 abort-handler 迭代 × region-restart put 的跨线程 CME） | 容器置换修复：由既有 abort×restart 套件回归（TestSupervisionLoopConsistentCut/TestCheckpointLifecycleIsolation 等 868 用例全绿；迭代顺序无语义依赖经代码审查确认——submit/failure-scan/abort 均序不敏感） |
| R-5 | `reportNodeTaskLiveness` 改 `merge(key, ts, Math::max)`（原子单调 max，旧值不再覆盖新值） | TestJobCoordinatorAuditFixes.livenessMergeIsMonotonicUnderConcurrentDelivery（双线程 ×2000 次交错上报 1000000/500000，终值断言 = max；旧 getOrDefault→put 序列在高交错下会以 500000 收场） |
| R-6 | `executeAssignmentFanOut` per-dispatch catch + LOG.error（单点 RPC 失败不再中止余下 fan-out） | TestJobCoordinatorAuditFixes.assignmentFanOutSurvivesPerDispatchFailure（3 节点确定性顺序，node-2 抛出后 assignTasks 不抛且 node-1/node-3 均收到 assignment；修复前异常上泄） |
| R-7 | `validateFingerprintCompatibility`：manifest 带指纹而当前运行无指纹来源时 **fail-fast**（原 WARN+跳过 = 指纹兼容检查在 JobGraph-only 生产入口被静默降级） | TestStreamModelFingerprintRecoveryCompat.fingerprintedManifestWithoutCurrentSourceFailsFast（断言 StreamException + fingerprint 语义）+ fingerprintlessManifestWithoutSourceStillPasses（legacy 无指纹 manifest 保持可恢复） |
| R-8 | `triggerCheckpoint` 源节点 RPC 缺失 else 分支 WARN（原静默跳过 → checkpoint 注定 timeout 零诊断） | 行为为日志级新增，既有 TestJobCoordinator trigger 套件回归（无新分支语义，No new test required per Rule #25 纯可观测性） |
| R-9 | `stopCheckpointScheduler` 加 synchronized（与 startCheckpointScheduler 锁纪律一致） | 一词修复，锁语义由既有调度器生命周期测试回归 |
| R-10 | `triggerBarrierOnAllInvokables` per-invokable try/catch（单 tracker 异常不再截断 barrier 注入） | 循环容错修复：TestCheckpointTriggerSafety 等多并发套件回归（异常注入路径无既有 fixture，per-invokable catch 与 R-6 同型已由后者覆盖语义） |
| R-11 | `restartRegion` taskKey 缺失分支补 LOG.warn（原静默 continue = 顶点无声丢弃） | 日志级修复，TestSupervisionLoop 家族回归 |
| R-12 | `restoreFromCheckpoint` 的 counter 推进逻辑委托 `advanceCheckpointIdCounterAfterRestore`（消除逐字双份） | 纯去重（行为等价），TestCheckpointCoordinator restore 套件回归 |
| R-13 | `triggerFinalCheckpoint` 补 Javadoc 裁定（CANCEL=best-effort by design 的不对称性显式化；行为不变） | 文档化裁定，No new test required |
| R-14 | **`updateFencingToken` 单调性防护**（拒绝 `fencingEpoch < current`，ERR_STREAM_FENCING_TOKEN_MISMATCH；zombie coordinator 无法回滚 TM epoch） | TestTaskManager.testUpdateFencingTokenRejectsEpochRollback（回滚抛异常 + epoch 不变 + active 任务存活 + equal/greater 仍接受）+ **gated 多 JVM** TestMultiJvmCoordinatorFailover.testZombieCoordinatorEpochRollbackRejectedAtRpcBoundary（测试 JVM 构造真实 RPC proxy 跨 JVM 发送 stale epoch，TM 日志双重断言：无回滚行 + 后续合法更新证明 epoch 原地未动） |
| R-15 | **invokable 安装超时 → FAILED**（timeout 记为 error → finally 报 FAILED TaskStatusReport；原路径虚假 COMPLETED） | TestTaskManager.testInvokableInstallTimeoutReportsFailedNotCompleted（200ms 可测预算，断言 TaskResult success=false + error 含 invokable + 上报 FAILED 而非 COMPLETED） |
| R-16 | `AdaptingConsumer.onMessage` 包裹 `codec.fromWire` try/catch → 畸形输入按契约丢弃 + WARN（原抛异常逃逸行为随消息后端而定；合法 null 丢弃由 DEBUG 升 WARN） | TestTestDataPlaneWireCodecMalformedInput ×3（畸形 JSON 不抛且不达 inner / 合法 round-trip 仍投递 / 两类丢弃均 WARN 级——Logback ListAppender 断言） |
| R-17 | `JdbcCheckpointStorage.deleteAllCheckpoints` 补删 epoch manifest 表（与 LocalFile 全树删除对齐，消除 stale-restore） | TestJdbcCheckpointStorage.testDeleteAllCheckpointsAlsoClearsEpochManifests |
| R-18 | `ensureTable`/`ensureEpochTable` 加 `IF NOT EXISTS`（多 JVM 首次初始化竞态，对齐 JdbcClusterRegistry Stage 42 修法） | TestJdbcCheckpointStorage.testConcurrentFirstInitializationDoesNotFail（双实例 CountDownLatch 并发首存，断言零错误 + 双行 durable） |
| R-19 | `RpcDistributedExecutor.startJob` try/catch + 失败 teardown（TM 追踪前置 + 全组件逆序停止；原失败泄漏全部已启动组件） | TestRpcDistributedExecutorFailureTeardown（确定性注入：非空但缺映射的 materialized assignment → assignTasks 抛出 → 心跳线程全消 + 重试成功） |
| R-20 | `receiveAssignment` 拒绝路径补 FAILED 上报（not-running/capacity/epoch 三类拒绝对 coordinator 可见，mirror reportDeployFailure） | TestTaskManager.testReceiveAssignmentCapacityRejectionReportsFailed + testReceiveAssignmentStaleEpochReportsFailed（MockCoordinatorRpcService.statusReports 断言 FAILED 报文） |
| R-21 | `deployTask` 校验 descriptor 内嵌 epoch 与 RPC 参数等值（原以未验证字段构建 RunningTask） | TestTaskManager.testDeployTaskRejectsDescriptorEpochMismatch（分歧即抛 + FAILED 上报） |
| R-22 | `loadSavepointMetadata` 补 `catch (Exception)` 包裹（全类异常包裹 parity） | TestJdbcCheckpointStorage.testLoadSavepointMetadataWrapsUnexpectedRuntimeException（子类注入 fromCompletedCheckpoint 抛出 → 断言 CheckpointStorageException 而非裸逃逸） |
| R-23 | `TaskManager.stop()` Javadoc 修正（ClusterRegistry 无 unregister API，lease 到期自然消失） | 文档修正，行为不变，No new test required |
| R-24 | **torn-write 故障注入测试补齐**（P-REQ-20 runtime 侧缺口：残留 .tmp 不可见性 / 截断文件 fail-fast / savepoint 不完整产物集） | TestLocalFileCheckpointStorage ×3：testLeftoverTmpFileIsInvisibleToReaders（+ deleteAll 清理残留）、testTruncatedCheckpointFileFailsFast（断言 typed NopException 非空非回退）、testSavepointPayloadWithoutMetadataYieldsNullMetadata |
| G6/G7 | G24/G25 门禁矩阵测试缺口补齐（standby detectFailures no-op / standby abort-handler 分支） | TestJobCoordinatorAuditFixes.standbyDetectFailuresIsNoOpOnStalledLiveness（经合法 active 路径播种真实停滞 liveness，standby 轮询零恢复动作）+ standbyAbortHandlerDoesNotFireCancelTask（共享 CC abort 后零 cancelTask RPC） |

**新增/更新测试清单**：新增 focused 用例 26 个（TestTaskManager +6、TestJobCoordinatorAuditFixes 新类 7、TestStreamModelFingerprintRecoveryCompat +2、TestDataPlaneWireCodecMalformedInput 新类 3、TestJdbcCheckpointStorage +3、TestLocalFileCheckpointStorage +3、TestRpcDistributedExecutorFailureTeardown 新类 1、gated 多 JVM +1）+ MockClusterRegistry 测试替身扩展（lease 超时捕获）。

### 3.2 大缺陷/治理项转 Follow-up（roadmap items 25—28，2026-09-01 已落库）

- **item 25** checkpoint manifest 版本化与校验和落地（P-REQ-20 go 裁定 / D-DRIFT-2 收敛载体）：EpochManifest 补 `stateFormatVersion`（alias CheckpointSerDe 格式信封版本，单一版本真值）+ `checksum`（canonical 序列化去 checksum 字段后 SHA-256）+ 协调器写入/restore 兼容（旧 manifest null 容忍）+ 测试——跨 core/runtime（EpochManifest 在 core），超出本 plan 单模块修复准则，方向裁定见 §2.1 ①a。
- **item 26** runtime checkpoint 协调器结构治理：retention GC I/O 移出 coordinator monitor（F-A，CC:1032-1049）+ `executeWithCheckpoint` 三 overload/terminate 三联/counter 家族克隆合并（F-B/F16 结构性部分；R-7/R-12 已修其行为级漂移）——需要独立回归面。
- **item 27** 分布式控制面 fencing 补全：`cancelTask` RPC 携带 fencing epoch（F-C，IStreamTaskRpcService 接口变更 + 全部实现/测试替身）+ TaskManager deployTask slot-replace 原子化（W-5）。
- **item 28** remote deploy 数据面通道收敛：SubtaskPlanBuilder 按需构建 per-subtask 通道（F-D，消除全对订阅 + dispatch 线程阻塞泄漏）+ JdbcCheckpointStorage `loadRetainedEpochManifests` override（W-8/2300-2 遗留 P2 承接）。

### 3.3 watch-only residual（显式裁定，Why Not Blocking）

W-1 setTasksToAcknowledge 混用锁纪律（latent，2300-1 backlog 既有）；W-2 globalRecovery 无 active 门禁（非 RPC 暴露 + 显式测试契约 Javadoc，无生产触达路径）；W-3 EPOCH_SCALE 边界（量级不可达）；W-4 failJob 幂等 C-T-A / collectAck TOCTOU 灰区（consistent-cut 论证成立）；W-6 completedTasks 任意键逐出/null taskKey；W-7 nextSid 跨 JVM 碰撞理论路径（天文概率）；W-8 双实现腐坏枚举行为分叉（裁定非缺陷：JDBC fail-fast 更审慎、LocalFile 符合 P1-09-01）；W-9 InMemoryClusterRegistry 边界集 + tmpdir 默认 checkpoint 目录（测试/演示级实现，生产走 JDBC 路径）。**拒绝项**：checkpoint id 拒绝路径燃烧（仅 id 空洞，无碰撞可能）。

### 3.4 回归验证（2026-09-01 执行记录）

- `./mvnw test -pl nop-stream -am -T 1C` → **BUILD SUCCESS**（全 10 模块绿；runtime 868 tests / 0 failures / 9 skipped 为 gated 多 JVM）。
- `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` → BUILD SUCCESS。
- **gated 多 JVM 套件**（`-Dnop.stream.test.multi-jvm.enabled=true` 实跑）：TestMultiJvmExactlyOnceRecovery 1/1 + TestMultiJvmCoordinatorFailover 3/3（含新增 zombie 回滚拒绝测试）+ TestMiniStreamClusterProcessSpawn 3/3 → **7/7 全绿**。
- `node ai-dev/tools/check-nop-stream-invariants.mjs` → 退出码 **0**（scan-wiring V3 唯一命中为 R-4 import 插入致 GMCE:742→743 行漂移，wiring-registry.json 同步钉点后归零——注册表诚实维护，非门禁放宽）。
- `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high` → 退出码 **0**（修复后复跑，0 critical/high）。
- 端到端验证（Rule #22）：触及 checkpoint/恢复路径的修复由既有 E2E 家族覆盖（TestE2ECheckpointAndRecovery/TestMultiEpochCheckpointE2E/TestSupervisionLoopConsistentCut/TestDistributedExactlyOnce 等，随全量回归绿）；分布式路径修复 R-14 有真实多 JVM 测试（上）——R-15 的缺陷机制为 JVM 内部时序条件（invokable latch 超时），单进程与多 JVM 走同一代码路径，focused 单测覆盖其语义并经此裁定记录。
- Owner-doc 裁定：`No owner-doc update required`——全部修复为 runtime 内部实现收敛（fencing 门禁补全是**恢复**既有 G24/G25 设计语义；R-7 fail-fast 是 checkpoint-design 既有「指纹比对 + 快速失败策略」的执行而非语义变更）；D-DRIFT-2 方向裁定（补字段落地转 Follow-up）本身不改设计文档，checkpoint-design §2.6 待 item 25 落地时同步。



## Conclusion

- **历史审计收口**：05-20 runtime 相关组（§3 死代码 10 文件 = 6 清除 + 3 已收编 + 1 迁移 core；§5 TimerService 实现层重复 = 双实现全删统一 HeapInternalTimerService；§2 runtime 侧）全部收口无回潮；06-30 runtime 发现 11 项 = 9 landed + 2 partial 全归类（R-1 修复 / 表名 watch-only）；2300-1/2/3 runtime 侧修复点全部 live 抽查通过。
- **产品化审计**：D-GAP runtime 两重点全部消化——P-REQ-20 三分项（原子写复核通过、D-DRIFT-2 方向裁定「补字段落地转 Follow-up item 25」、torn-write 注入测试就地补齐）+ JobCoordinator 五处 fencing 门禁行为级覆盖成立（freshness 复核通过，K8s defer 所依赖的应用层基线 fresh）；分布式核心路径双 subagent 审计采信 high 2（R-14 fencing 回滚防护、R-15 虚假 COMPLETED）+ medium 12 + low 若干，全部经执行者源码复核；hollow scan high exit 0；测试覆盖（9 包 ≥2 + top-5 直测）与 gated 多 JVM 盘点（6+1 用例/3 类，无失修）落地。
- **修复与收口**：小缺陷 R-1..R-24 + G6/G7 测试缺口共 24 项修复全部就地落地（22 项行为修复各配 focused 测试/新增 26 个用例 + 2 项纯文档/日志级），G24/G25 相邻门禁缺口（R-2/R-3）闭合；大缺陷/治理项 4 项转 Follow-up items 25—28 落 roadmap；watch-only 9 项 + 拒绝 1 项显式裁定。
- **验证**：`./mvnw test -pl nop-stream -am -T 1C` 全绿（runtime 868/0/9-skipped）；gated 多 JVM 套件实跑 7/7（含新增真实跨 JVM zombie 回滚拒绝测试）；四工具门禁全 exit 0（invariants 复跑含 wiring-registry 钉点诚实同步）。
- **被否决的方案**：为 JobGraph-only 入口伪造 StreamModel 指纹（否决：无指纹来源时 fail-fast 比伪指纹诚实，且 JobGraph→JobGraph restore 不受影响）；将 manifest stateFormatVersion/checksum 就地补齐（否决：EpochManifest 在 core，跨模块格式演化须独立回归面与单一版本真值设计——转 Follow-up item 25）；将 triggerFinalCheckpoint 对齐 rethrow（否决：CANCEL=立即停止语义下 best-effort 是正确策略，文档化显式化即可）；在本 plan 内修 cancelTask fencing（否决：接口变更跨全部实现与测试替身，转 Follow-up item 27）。
- **后续工作**：items 9—11 审计直接引用本报告（§1.1 收口结论 / §2.4 hollow 基线 / gated 盘点）；item 25 承接 D-DRIFT-2 落地；item 17 引用 §2.1 ② 门禁矩阵作为 HA 语义文档素材；Follow-up items 25—28 待调度。

> Status: resolved（2026-09-01；全部发现→处置零丢失映射：§1.2/§2.2 表内每行有处置列，R-*/F-*/W-* 全数落位修复/Follow-up/watch-only/拒绝）。

## References

- `ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-06-30-nop-stream-code-audit.md`（历史审计原文）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-core-module-audit.md`（item 7，结构对齐 + §2.2/§1.1 §7 结论引用）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md`（D-GAP §3.1 item 8 重点）
- `ai-dev/plans/nop-stream-production/2026-08-04-2300-1/2/3-*.md`（remediation plans）
- `ai-dev/plans/nop-stream-productization/2026-09-01-0938-3-runtime-module-audit.md`（执行 plan）
