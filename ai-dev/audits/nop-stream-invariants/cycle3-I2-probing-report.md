# Cycle 3 / I2 聚焦对抗探查报告（wiring 族门禁盲区 + P2 backlog 触发评估）

> 状态: active（Cycle 3 / I2 Phase 3 产出，2026-08-13 实测）
> 日期: 2026-08-13
> 来源: plan `2026-08-13-0805-2-nop-stream-invariants-cycle3-I2-invariant-driven-audit.md` Phase 3
> 范围: nop-stream wiring 族（不变式 #7）——7 服务注入 API 面 + 消费方 + 接线点 + 登记盲区清单 a-e + 非族候选
> 方法: `ai-dev/skills/open-ended-adversarial-review-prompt.md` 聚焦对抗探查（非全仓漫游），每发现含 位置 / 场景 / 影响 / 族标注
> 去重: 已对照 `cycle3-I1-input.md`（11 门禁类 112 tests / 0 pin / 7 服务 / 消费方 3 类）、`wiring-registry.json`（7 services × 接线点 × 消费方表）、
> `mjs-pins.json`（空表）、open-audit `2026-08-12-1217-open-audit-nop-stream-invariant-loop.md`（P0-01/P1-02 先例链 + P2 批次 + 总评 2/3 点）、
> multi-audit `2026-08-12-1217-multi-audit-nop-stream-invariant-loop.md`（P2-01~P2-23 批次）；Cycle 1/2 的 probing-report 不复用不追加

## 0. 探查范围声明（覆盖面清单）

聚焦于注册表目标集 + 盲区清单 a-e，未做全仓漫游。实际逐项检查的类/文件：

- **服务注入 API 面（7）**：`AbstractStreamOperator.java`（setOutput :140 / setProcessingTimeService :148 / setStateBackend :152 /
  setKeyedStateBackend :165 / setOperatorStateBackend :173 / setTimeServiceManager :181 / setSnapshotCallback :320）。
- **接线点（9）**：`StreamTaskInvokable.java`（setupProcessingTimeServices :456-468 → :462/:463、wireOperators :230/:268、
  fan-out 尾 :287/:293、wireTailToRecordWriter :422-428 → :426、setupSnapshotCallbacks :430-438 → :435）、
  `GraphModelCheckpointExecutor.java`（wireTaskCheckpointPipeline :687 → :742，初始 :660 / 重建 :838 复用）。
- **消费方（3 类 10 行）**：`CepOperator.java`（:290/:300/:373/:381/:382/:401/:479/:490）、`WindowOperator.java`（:471/:479）、
  `ProcessOperator.java`（:46）；`StreamTaskInvokable.java` :345/:353 = 自身 getter 声明（非消费调用点，不登记）。
- **盲区 a 同族复探面**：checkpoint 服务（`CheckpointCoordinator` / `CheckpointBarrierTracker` / `SharedStateRegistry`）、
  watermark 服务（`TimestampsAndWatermarksOperator` + `StreamGraphGenerator` :455-473 生产创建点）、
  其他 `set*Service` 注入点（全 main+test grep 分类）；`setTimerService`（StreamingRuntimeContext :70 / ProcessOperator :62）。
- **盲区 b 时序组合面**：`CepOperator.open`（:247-355，守卫 :373）、`WindowOperator.open`（:464-519，守卫 :471/:479-484）、
  `SupervisionLoop.rewireCheckpointPipeline`（:816-840，重建重接）、`ProcessingTimeServiceDriver`（:111-147，驱动启动时序）。
- **盲区 c 恢复路径面**：`CepOperator.restoreState`（:444-462）/ `applyPendingRestoreState`（:259）、
  `WindowOperator.restoreState`（:573+）/ restoreTimers（:489-492）/ pane 快照（:405-416）、
  `HeapInternalTimerService.snapshotTimers/restoreTimers`（:229/:254）、`PendingCheckpoint`（:162-212 future 语义）。
- **盲区 d 窗口/CEP 清理语义面**：`WindowOperator`（triggerAccumulators :2043-2071 / elementTimestampsState :451-462/:1337 /
  computePaneInfo :965-991 / 合并路径 :653-737 / 常规路径 :739-770 / onEventTime :772-845 / emitWindowContents :914-963）、
  `CepOperator` STEP-5（:563-592）。
- **盲区 e P2 backlog 面**：open-audit P2-01~P2-10（`CheckpointCoordinator` :203/:772-831/:849-879/:1106-1161、
  `InMemoryClusterRegistry` :99-123）、multi-audit 预枚举候选（beans.xml ×2、`WindowOperatorFactoryImpl` :151-189、
  `RocksDBKeyedStateBackend` :205-210 vs `RocksDBIncrementalRestore` :143-155、`GraphModelCheckpointExecutor` 1728 行、
  `TestWindowOperatorCorrectness` :459-493/:551-585/:607-625）。
- **非族候选面**：`setCurrentKey`/`setKeyContextElement1/2`（AbstractStreamOperator :330/:335/:353）、
  `HeapInternalTimerService`（:33-180 触发机制 vs 记账结构）、`ProcessingTimeServiceDriver`（:111-147 驱动对齐）。

## 1. 探查发现

### C3-PR-1. 盲区 a：checkpoint / watermark / 其他运行时服务——无 P0-01 同形态新实例（逐服务结论）

- **位置**：`GraphModelCheckpointExecutor.java:581/:707/:714`（CheckpointCoordinator / CheckpointBarrierTracker / setBarrierTracker 生产创建+接线）、
  `CheckpointCoordinator.java:1327`（SharedStateRegistry 内部创建）、`StreamGraphGenerator.java:458`（TimestampsAndWatermarksOperator 生产创建）、
  `StreamTaskInvokable.java:456-468`（PTS/TSM 构造接线，4 构造全调）。
- **场景**：plan `2026-08-13-0132-1` Non-Blocking Follow-up 登记——checkpoint 服务 / watermark 服务 / 其他运行时服务是否存在
  P0-01 同形态（main 零接线、测试全 mock 注入规避）。逐服务 `set*Service` / 注入点调用方归属（main vs test）grep 全量分类。
- **逐服务结论**：
  - *checkpoint 服务*：`CheckpointCoordinator` / `CheckpointBarrierTracker` / `SharedStateRegistry` 全部 **main 创建 + main 接线**
    （GraphModelCheckpointExecutor 装配 / CheckpointCoordinator 内部构造），无注入 API 依赖——**已接线，无仅测试注入实例**。
  - *watermark 服务*：`TimestampsAndWatermarksOperator` 消费 `processingTimeService`（继承字段），生产接线 = StreamTaskInvokable 4 构造
    → setupProcessingTimeServices（:462）恒在 open() 前注入——**已接线**；测试直接构造（`TestPeriodicWatermarkAdvancement` 等 7 处）
    属正常单测形态（测试注入 mock 不构成 P0-01，因生产接线存在）。
  - *其他 `set*Service`*：全 main grep 命中仅 `StreamingRuntimeContext.setTimerService`（:70，ProcessOperator :62 内部包装注入——
    internal-creation 形态）、`TaskManager.setCoordinatorRpcService`（:606，main 接线 EmbeddedDistributedExecutor:196 /
    RpcDistributedExecutor:263）、RPC 面（`StreamControlRpcServer` :72/:78、`StreamControlRpcProxyFactory` :67/:75，均为 main 装配）——
    **全部 main 已接线，零仅测试注入实例**。
  - *测试侧注入统计*：test grep 仅命中已登记 API（setStateBackend 39 / setKeyedStateBackend 28 / setOperatorStateBackend 1 /
    setCoordinatorRpcService 3）——无未登记注入面。
- **族标注**：已知族（#7 wiring 族）**复探零新实例**——P0-01 同形态在 checkpoint/watermark/其他运行时服务面不存在。
- **结论**：plan `2026-08-13-0132-1` Non-Blocking 登记项处置为「复探完成，无新实例」→ 该 Follow-up 可闭合。

### C3-PR-2. 盲区 a 续：`TimestampsAndWatermarksOperator` 静默守卫形态复核——plan 1 裁定成立

- **位置**：`TimestampsAndWatermarksOperator.java:81-89`（scheduleNextWatermarkTimer，:82 `if (processingTimeService != null)` 静默守卫）。
- **场景**：watch-only residual 复核——plan `2026-08-13-0132-1` 裁定「接线后自然失效，语义不破坏」是否成立（live 复核接线后
  PTS 非 null 路径 + 守卫分支可达性）。
- **live 复核**：生产创建点 `StreamGraphGenerator.java:458` → StreamNode → 算子链 → `StreamTaskInvokable` 4 构造
  （:151/:164/:180/:195）全部调用 setupProcessingTimeServices → :462 无条件注入（**无分支、无条件**）→ 接线后 PTS 恒非 null；
  守卫分支（:82 为 false 时跳过定时注册）**生产不可达**，仅直接 open() 的测试路径可达（`TestWatermarkIntervalConfig` 等——
  测试内显式 setProcessingTimeService 或 interval=0 场景）。interval=0（TestCepProductionExecutionE2E:104）本就不注册定时器。
- **影响**：守卫为防御性残留，接线后自然失效；若未来接线被移除，watermark 定时器静默跳过（事件驱动发射仍在 processElement
  兜底 :107-127）——语义降级但非 NPE/崩溃。plan 1 裁定「接线后自然失效，语义不破坏」**成立（live 证据在案）**。
- **族标注**：已知族（#7 wiring 族 guard 形态残余，plan 1 裁定延续）。
- **结论**：维持 watch-only residual 处置（不升格 red list；如需 fail-fast 升级属 I3 裁决范围，非本 plan）。

### C3-PR-3. 盲区 b：注入/恢复时序组合面——无新 null 面 / 无重复注入 / 无顺序依赖破坏

- **位置**：`CepOperator.open` :247-355（守卫 :373-380 WARN+skip；timer 注册表守卫 :283-285）、
  `WindowOperator.open` :464-519（registerTimerService :470-472 null 守卫 + PT WARN :479-484）、
  `SupervisionLoop.rewireCheckpointPipeline` :816-840（:838 复用 wireTaskCheckpointPipeline）、
  `ProcessingTimeServiceDriver` :111-147（:115-118 任务线程 fire 对齐）。
- **场景**：open-audit 总评第 3 点（P0-02 组合爆炸实例教训）——构造函数注入（PTS/TSM）vs `open()` vs `restoreState()` vs
  `rebuildTask` 重接顺序组合下是否存在新的 null 面 / 重复注入 / 顺序依赖破坏。
- **逐组合核查**：
  - *构造注入 vs open()*：setupProcessingTimeServices 在构造器内（javadoc :446-450「BEFORE any operatorChain.open()」），
    open() 内 PTS/TSM 恒非 null；CepOperator 守卫 :373 / WindowOperator 守卫 :479 为直接单测兜底——**无新 null 面**。
  - *restoreState() vs open() 顺序*：两算子 restore-before-open 均已钉（CepOperator :278-285「restoreState() may run BEFORE open()」+
    applyPendingRestoreState :259；WindowOperator restoredTimerSnapshot :486-492 + pane 快照 :405-416 注释）——**无顺序依赖破坏**。
  - *rebuildTask 重接*：rewireCheckpointPipeline :816-840 与初始注册共用同一 wireTaskCheckpointPipeline helper
    （:838 = :660 同函数）——重建 task 接线与初始不可区分——**无重复注入**（新算子实例重接，旧实例 unwire :824 后弃置）。
  - *driver 启动时序*：PTS driver 延迟至 invoke() 启动（:475-481 注释「constructed-but-never-invoked invokable does not leak a thread」），
    open() 注册的 timer 由 driver 首个 fire 周期接管——**时序对齐，无窗口期丢失**。
- **族标注**：已知族（#7 wiring 族）组合面核查——无新 null 面 / 无重复注入 / 无顺序依赖破坏。
- **结论**：组合面无新发现，判定依据维持（供 I3 参考）。

### C3-PR-4. 盲区 c：恢复路径行为盲区——descriptor 路径 snapshot→restore 服务注入面保持 + timer 注册表恢复闭环

- **位置**：`CepOperator.restoreState` :444-462（watermark :447-451 / timers :452-460 → open :283-285 守卫 + applyPendingRestoreState :259）、
  `WindowOperator.restoreState` :573+ → open restoreTimers :489-492 / pane 快照 :405-416、`HeapInternalTimerService` :229/:254（snapshot/restore）。
- **场景**：open-audit 总评第 2 点（门禁覆盖传递与注册，不覆盖恢复行为）——descriptor 路径 snapshot→restore 往返中服务注入面是否保持
  （timer 服务注册表恢复 / state backend 恢复后 getter 面）。
- **live 复核**：restore 数据面（watermark / timers / pane / pending keyed state）全部经 snapshotState/restoreState 保存并延迟应用到
  open() 后的对象面（timer 服务为 open() 新建 HeapInternalTimerService → restoreTimers 回灌 → registerTimerService :471 注册到 TSM；
  CepOperator registeredEventTimeTimers :283-285 只在 null 时重建 + :452-460 恢复回灌）；state backend 恢复 = applyPendingRestoreState
  → keyedStateBackend 非 null → getter 面可用。门禁覆盖确认：`TestWindowRoundTripInvariant` / `TestCepReleaseSymmetryInvariant` /
  `TestE2EWindowAggregateRestore` / `TestE2EManifestRestoreIdAdvance` 在案（Phase 1 实测全绿）。
- **联动触发评估（P2-01/P2-02）**：见 §2 触发评估表——**均触发**（checkpoint 协调面恢复/失败路径证据完整）。
- **族标注**：已知族（#7 wiring 族恢复路径核查）+ checkpoint 面（P2-01/P2-02 触发确认）。
- **结论**：注入面在 snapshot→restore 往返保持；恢复行为盲区本身无新服务注入缺口——门禁盲区（不覆盖恢复行为）由既有测试 + 本报告
  P2 触发评估补齐，供 I3 裁决。

### C3-PR-5. 盲区 d：窗口/CEP 清理语义 P2-04~P2-08 触发确认（行号已按 live 重定位）

- **位置**（live 重定位 vs open-audit 旧行号）：P2-04 `WindowOperator.java:2043-2071`（open-audit :1944-1957 过期）、
  P2-05 `:451-462/:1337-1358`（open-audit :442-453/:1230-1233 过期）、P2-06 `:965-991/:653-737`（open-audit :917-943 过期）、
  P2-07 `:729-731 vs :762-765`（open-audit :681-683/:714-717 过期）、P2-08 `CepOperator.java:563-592`（open-audit :540-554 过期）。
- **逐条触发确认**：
  - *P2-04（triggerAccumulators 永不裁剪 + 纯 FIRE 不调 clear）*：live :2050 `stateKey = "trigger_" + key + SEP + window + SEP + descriptor`，
    :2052-2063 只增不删；全文件 grep 无 `triggerAccumulators.remove`（仅 :536 close 置空）；合并路径纯 FIRE（:722-727）与常规路径纯 FIRE
    （:755-760）均不调 triggerContext.clear()——**触发确认**（长运行窗口 map 无界 + 复发窗口复用陈旧累加器）。
  - *P2-05（evictor descriptor 路径不建 elementTimestampsState）*：live :451-462 `else` 分支（windowStateDescriptor == null）才建
    elementTimestampsState；descriptor 路径（builder 恒传 stateDesc）为 null → storeElementTimestamp :1337 早退 → emitWindowContents
    :924-932 全部元素拿当前 watermark 兜底 → TimeEvictor 永不驱逐——**触发确认**。
  - *P2-06（合并路径 pane 键错位）*：live computePaneInfo :965-991 paneKey = key + SEP + windowNamespace(actualWindow)（:993-995）；
    合并路径清除用 stateWindow 命名空间（:729-731 clearWindowContents(key, stateWindow)）——(key, actualWindow) pane 条目泄漏 +
    DISCARDING 清错命名空间——**触发确认**。
  - *P2-07（合并路径 purge 缺 triggerContext.clear）*：live 合并路径 :729-731 仅 clearWindowContents 无 triggerContext.clear()，
    对照常规路径 :762-765 有——不对称确认——**触发确认**。
  - *P2-08（CepOperator STEP-5 超时基准）*：live :573 `timerService.currentWatermark() < cs.getStartTimestamp() + wt`——用
    startTimestamp（start state 为 -1）判定，与 NFA 逐状态窗口（PREVIOUS_AND_CURRENT）语义不一致；清理路径 :579-591 不触发
    processTimedOutSequences（超时事件静默丢弃）；size==1 守卫（:564）使破坏性路径巧合安全——**触发确认**。
- **族标注**：均为 open-audit P2 批次已登记条目（checkpoint/窗口/CEP 面）触发确认——**非 #7 wiring 族，非新族**；
  触发确认后升格为复探确认项入 red list（C3-RL-4~C3-RL-8），不裁决严重度（属 I3）。
- **结论**：P2-04~P2-08 全部触发成立（live 证据 + 重定位行号在案）。

### C3-PR-6. 盲区 e：P2 backlog 触发条件评估表（open-audit 批次 + multi-audit 预枚举）

见 §2 完整评估表。要点：open-audit P2-01/P2-02/P2-03 触发（checkpoint/cluster 面 live 证据完整）；P2-04~P2-08 触发（§1 C3-PR-5）；
P2-09 引用复核 = multi-audit P0-01 触发条件已解决（见 C3-PR-7，claim 过期）；P2-10 不触发（工具盲区非 wiring 探查面）；
multi-audit P2-11/P2-01/P2-03/P2-05 触发（beans 重复 id / serializer 死字段 / RocksDB Options 泄漏 / 上帝类）；
multi P2-18~21 不触发（文档治理类，一行依据见 §2 表）。

### C3-PR-7. multi-audit P0-01 触发条件已解决（open-audit P2-09 claim 过期）

- **位置**：`TestWindowOperatorCorrectness.java:551-585`（testMergeTwoRawWindowValuesFailsFast）+ :607-625（testMergeAccumulatorWithRawValueFailsFast）——
  MixedTypeWindowOperator **2 处实例化**（:554/:610）且 :576/:580 断言 `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT` 抛错。
- **场景**：open-audit P2-09 声称「P0-01（merge fail-fast 零回归测试）……全部仍 live 未修复」且
  「TestWindowOperatorCorrectness.java:453-487 MixedTypeWindowOperator 仍零实例化」——**live 复核与该 claim 不符**。
- **live 复核**：Multi-audit P0-01 两个断言面（raw+raw merge fail-fast / accumulator+raw fail-fast）**已有回归测试覆盖**
  （:551/:607 用例，assertThrows + 错误码断言）——触发条件「零回归测试」**已解决**（Cycle 3 派生前修复落地的回归测试，
  无行为漂移——测试全绿）。MixedTypeWindowOperator 死类 claim 亦过期（2 处实例化）。
- **族标注**：不属 #7 wiring 族——触发条件解决声明（供 I3 关闭 multi-audit P0-01 引用项；open-audit P2-09 需修订）。
- **结论**：P2-09（open-audit 批）不触发（claim 过期），记录为处置结论供 I3/backlog 修订。

### C3-PR-8. 非族候选评估（每候选：升格 / 不升格 + 理由）

| 候选 | live 复核（2026-08-13） | 裁定 | 理由 |
|---|---|---|---|
| `setCurrentKey` / `setKeyContextElement1/2`（AbstractStreamOperator :330/:335/:353） | 参数类型非服务类型（Object / StreamRecord），V4 明确不触发（registry violationSemantics :201 在案）；main 调用面 = 算子处理链内部 | **不升格** | 非服务注入面（wiring 族门禁表达正确排除），无新不变式候选 |
| `HeapInternalTimerService` 计时器真实触发机制 vs 记账结构对齐（plan `2026-08-13-0132-3` Non-Blocking） | 驱动 → mailbox → fireDueTimersOnTaskThread（ProcessingTimeServiceDriver :135-147）→ fireProcessingTimeTimers :140 与 TreeMap 记账（:35-36）对齐；nextProcessingTimeTimer volatile 单读（:47）驱动调度器——真实触发机制与记账结构一致 | **不升格** | 对齐验证通过（触发机制 = 记账结构消费者），plan 3 Non-Blocking 处置闭合 |
| `StreamTaskInvokable` 自身 getter（getProcessingTimeService :353 / getTimeServiceManager :345） | 供运行时接线断言/诊断（javadoc :341-355），非 AbstractStreamOperator 消费调用点 | **不升格** | 注册表消费方枚举语义正确（V2 仅 main getter 调用点归属），无漂移 |

## 2. P2 backlog 触发条件评估表（盲区 e 完整落档）

### open-audit P2 批次（10 条）

| # | 条目 | 触发条件 | live 复核证据（2026-08-13） | 裁定 |
|---|---|---|---|---|
| P2-01 | checkpointSuccessMap 无界增长 | checkpoint 协调面复探 | `CheckpointCoordinator.java:1117` put（notifyParticipantsFinishCommit，success/fail/abort/shutdown 四路径）；remove 仅 :805（success）/ :1155（retry 成功）/ :1204（shutdown）——fail/abort 路径 put 无 remove | **触发**（复探确认项 C3-RL-4） |
| P2-02 | onCompletePersistFailure 不 complete future | 同上 | :821-831 直接 `pending.getStatus().set(FAILED)`（:826）绕过 `pending.fail()`（PendingCheckpoint :175-186 才 completeExceptionally）；future 消费者 = JobCoordinator :1439/:1462/:1485 + GraphModelCheckpointExecutor :353/:485 | **触发**（复探确认项 C3-RL-4） |
| P2-03 | InMemoryClusterRegistry.getNodeLease 无锁双 map 读 NPE | cluster 面复探 | `InMemoryClusterRegistry.java:99-108` 无锁；:104 `long expireAt = leaseExpireTimes.get(nodeId)` 自动拆箱 NPE（evictExpiredNodes :110-123 并发移除）；对照 getActiveNodes :134 有防御 null 检查 | **触发**（复探确认项 C3-RL-4） |
| P2-04 | triggerAccumulators 永不裁剪 + FIRE 不 clear | 类别清扫相邻面 | WindowOperator :2043-2071 只增不删（C3-PR-5 详） | **触发**（C3-RL-5） |
| P2-05 | evictor descriptor 路径不建 elementTimestampsState | 同上 | :451-462 else 分支仅建；:1337 早退；TimeEvictor 永不驱逐（C3-PR-5 详） | **触发**（C3-RL-6） |
| P2-06 | 合并路径 pane 跟踪键错位 | 同上 | :965-995 paneKey=actualWindow vs :729-731 清除=stateWindow（C3-PR-5 详） | **触发**（C3-RL-7） |
| P2-07 | 合并路径 purge 缺 clear | 同上 | :729-731 vs :762-765 不对称（C3-PR-5 详） | **触发**（C3-RL-7） |
| P2-08 | CepOperator STEP-5 超时基准错误 | 同上 | :573 startTimestamp + wt vs NFA PREVIOUS_AND_CURRENT；:579-591 不通知 handler（C3-PR-5 详） | **触发**（C3-RL-8） |
| P2-09 | 前次 multi-audit 发现复核 | 复探 | multi P0-01（merge fail-fast 零回归测试 / MixedTypeWindowOperator 零实例化）**已解决**（C3-PR-7：2 处实例化 + 2 断言用例）——claim 过期；其余 multi P2-01~23 见下 | **不触发（claim 过期，处置记录供 I3/backlog 修订）** |
| P2-10 | scan-output-contract V4 嵌套泛型盲区 | 门禁工具面 | 属输出契约族门禁工具（非本 plan 探查面/不改门禁 Non-Goal） | **不触发**（一行依据：scan-output-contract 工具面本探查未接触；工具盲区登记在 open-audit 批，派生评估属 I6） |

### multi-audit P2 批次（23 条，预枚举候选 4 条 + 其余一行依据）

| # | 条目 | 触发条件 | live 复核证据（2026-08-13） | 裁定 |
|---|---|---|---|---|
| multi P2-11 | 两 beans.xml 同一 bean id `streamMessageService` | beans 面（wiring 相邻） | `stream-control-rpc.beans.xml:34`（ioc:default）+ `stream-data-plane.beans.xml:38/:68`（:38 无 default + :68 default）——两文件同时加载同一容器冲突成立（live 行号 :34-35 / :68-69） | **触发**（复探确认项 C3-RL-9） |
| multi P2-01 | WindowOperator keySerializer/windowSerializer 死字段 + dummy serializer createInstance null | WindowOperator 面（探查接触） | :151/:161 字段仅声明/构造/notNull/copy；`WindowOperatorFactoryImpl.java:151-189` createDummySerializer :166-171 反射失败返回 null（对照 TimeWindowSerializer 返回真实实例） | **触发**（复探确认项 C3-RL-9） |
| multi P2-03 | RocksDB Options 原生资源未关闭 | RocksDB 面（state backend 接线相邻） | `RocksDBKeyedStateBackend.java:206-210` `Options options = new Options(...)` 无 try-with-resources；对照 `RocksDBIncrementalRestore.java:151` `try (Options listOpts = ...)` 正确写法——疏漏成立 | **触发**（复探确认项 C3-RL-9） |
| multi P2-05 | GraphModelCheckpointExecutor 上帝类（1595 行 → 1728 行） | 执行/恢复职责面（探查接触） | live 1728 行，执行（registerTasksAndTrackers :644）与恢复（:1598 注释 restore path）职责混合 | **触发**（复探确认项 C3-RL-9） |
| multi P2-02 | （按 multi-audit 清单条目语义） | 类别清扫 | 本条为 multi-audit P2 批次内条目，本探查面（wiring/恢复）未接触其代码面 | **不触发**（一行依据：代码面本探查未接触，维持 backlog 原状） |
| multi P2-04 | （同上） | 同上 | 同上 | **不触发**（一行依据同 multi P2-02） |
| multi P2-06~P2-10 | （同上） | 同上 | 同上 | **不触发**（一行依据同 multi P2-02） |
| multi P2-12~P2-17 | （同上） | 同上 | 同上 | **不触发**（一行依据同 multi P2-02） |
| multi P2-18 | INDEX.md 子模块清单缺 2 模块 | 文档治理 | 纯文档条目，本探查零代码面接触 | **不触发**（一行依据：文档治理类，非 wiring/恢复代码面） |
| multi P2-19 | OutputTag javadoc 示例不可编译 | 文档治理 | 同上 | **不触发**（一行依据同 P2-18） |
| multi P2-20 | STRM-026 锚点文档不完整 | 文档治理 | 同上 | **不触发**（一行依据同 P2-18） |
| multi P2-21 | INDEX.md nop-stream-flow 措辞不准确 | 文档治理 | 同上 | **不触发**（一行依据同 P2-18） |
| multi P2-22 / P2-23 | （按 multi-audit 清单） | 类别清扫 | 本探查面未接触 | **不触发**（一行依据同 multi P2-02） |

> 注：multi-audit 批次条目以 live 文档为准（roadmap 头注「21 条」为陈旧计数，multi-audit 文档实际 P2-01~P2-23）；
> 本表对预枚举候选 4 条（P2-11/P2-01/P2-03/P2-05）逐一 live 复核并触发确认，其余条目标注「不触发 + 一行依据」
> （该文件/面本探查未接触，维持 backlog 原状，不升格不降级，供 I3）。

## 3. 盲区 a-e 逐项处置声明

- **a) 同族"仅测试注入"复探**：checkpoint 服务 / watermark 服务 / 其他 `set*Service` 注入点逐服务 grep 归属分类完成
  （C3-PR-1）——**全部已接线，零仅测试注入新实例**；`TimestampsAndWatermarksOperator` 静默守卫复核完成（C3-PR-2）
  ——plan 1 裁定成立（接线后守卫分支生产不可达）。**plan `2026-08-13-0132-1` Non-Blocking 登记项处置：复探完成可闭合**。
- **b) 注入/恢复时序组合面**：构造注入 vs open vs restoreState vs rebuildTask 四维组合逐一核查（C3-PR-3）——
  **无新 null 面 / 无重复注入 / 无顺序依赖破坏**（双算子 restore-before-open 延迟应用模式在案、rebuild 复用同一 wire helper、
  driver 延迟启动时序对齐）。
- **c) 恢复路径行为盲区**：descriptor 路径 snapshot→restore 服务注入面保持（C3-PR-4）——timer 注册表恢复闭环
  （restoreTimers → registerTimerService）+ state backend getter 面可用；门禁盲区（不覆盖恢复行为）由既有恢复测试 +
  P2-01/P2-02 触发评估（C3-RL-4）补齐——**无新服务注入缺口**。
- **d) 窗口/CEP 清理语义盲区**：P2-04~P2-08 五条全部**触发确认**（C3-PR-5，行号已按 live 重定位：triggerAccumulators :2044 /
  timestamps 早退 :1337 / computePaneInfo :965 / STEP-5 :563-592）——升格复探确认项 C3-RL-5~C3-RL-8 供 I3 裁决；
  **open-audit 引用行号过期警示兑现**（探查以 live 重定位为准）。
- **e) P2 backlog 触发条件评估**：open-audit 批 10 条逐条评估（P2-01/02/03/04/05/06/07/08 触发 → C3-RL-4~C3-RL-8；
  P2-09 不触发 claim 过期；P2-10 不触发工具面）——完整表见 §2；multi-audit 批候选预枚举 4 条触发（C3-RL-9）+ 其余
  逐条一行依据（不触发）——**无静默跳过**。

## 4. 已知族新实例与历史 finding 对应

- C3-PR-1（同族复探零新实例）：已知族（#7 wiring 族）——对应 plan `2026-08-13-0132-1` Non-Blocking Follow-up 登记项（处置 = 闭合）。
- C3-PR-2（TimestampsAndWatermarksOperator 守卫）：已知族（#7 guard 形态残余）——对应 plan 1 裁定延续（watch-only residual）。
- C3-PR-3（时序组合面）：已知族（#7 wiring 族组合核查）——对应 open-audit 总评第 3 点（P0-02 组合爆炸教训）。
- C3-PR-4（恢复路径盲区）：已知族（#7 wiring 族恢复核查）——对应 open-audit 总评第 2 点；联动 P2-01/P2-02（open-audit 批）。
- C3-PR-5（P2-04~P2-08 触发确认）：open-audit P2 批次已登记条目触发确认——对应 open-audit 文档各条（live 行号已更新）。
- C3-PR-6（P2 backlog 评估表）：对应 open-audit P2 批次 + multi-audit P2 批次（roadmap Follow-up Backlog）。
- C3-PR-7（multi P0-01 已解决）：对应 multi-audit P0-01 / open-audit P2-09（claim 过期，供 I3/backlog 修订）。
- C3-PR-8（非族候选）：对应 registry violationSemantics V4 排除清单 + plan `2026-08-13-0132-3` Non-Blocking（HeapInternalTimerService 对齐）。
- 历史 finding 对照：无新发现对应未登记历史 finding（open-audit P0-01/P1-02/P0-02/P1-03/P1-04/P1-05 已由 plan 1/2/3 修复闭环）。

## 5. 盲区自评与移交

- **盲区自评**：本次探查为聚焦式（注册表目标集 + 登记盲区清单 a-e），未做全仓漫游；可能遗漏——connector/flow 模块内
  非 wiring 形状的服务注入类机制、极端并发下 checkpointSuccessMap/getNodeLease 竞态实证（需专门 harness，静态推演已闭环）、
  multi-audit 批次中「一行依据不触发」条目的深层语义（维持 backlog 原状，未升级也未关闭）。
- **移交**：触发确认项 C3-RL-4~C3-RL-9 全部带 live 证据随本报告 + red-list.md 移交 I3（严重度裁决 + 派发）；
  零触发条目维持 backlog 原状；同族复探/时序组合/恢复路径/守卫形态复核结论在案（供 I3 参考）；无新族候选派生
  （全部发现归属已知族 #7 或既有 backlog 批次——I6 按 Loop Rule 评估时无新增输入）。
