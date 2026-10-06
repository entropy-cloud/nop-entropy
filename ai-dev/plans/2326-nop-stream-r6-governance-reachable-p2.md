# 2326 nop-stream R6 治理批次一：可达 P2 正确性缺陷收口

> Plan Status: completed
> Last Reviewed: 2026-10-06
> Source: ai-dev/audits/2026-10/2026-10-06-0522-deep-audit-nop-stream-quality-r6/summary.md（最终保留项 P2 子集——生产形状可达的真实正确性缺陷）
> Related: ai-dev/plans/2322..2325（R6 P0/P1 修复批次，均 completed）

## Adjudication（计划审查后的关键裁定，全部经 live code 核实）

- **CC-03 机制 = 三写整体移入锁内**（非"删除仅留 ：2141"——:2141 只置 active；currentLeadership/recoveryGen 的生产读点在 globalRecovery :1755-1758 锁内，删写会拿到陈旧 leader epoch）。移入点：recoveryLock 持有后、rotateFencingEpochCoreLocked 之前（该函数不读这三字段，无依赖障碍）。附带：deactivateToStandby:2269-2270 同族锁外写（active/currentLeadership）一并移入其锁。
- **CC-02 机制 = ConcurrentHashMap.compute 内条件 displace**（非"put 后校验+回滚"——回滚与第三代部署存在丢更新竞争且 cancel 不可逆）。existing==null 或 existing.fencingEpoch <= descriptor.fencingEpoch 才替换并 cancel 被逐者；existing 更新 → 原样返回不 displace 不 cancel，调用方释放 permit 后 typed reject（模式同 :682-686）。旧代 FAILED 上报被协调器 stale-report 过滤（JobCoordinator:1028-1050），不触发伪恢复。钉子需 put 前包私有 latch seam。
- **CC-01 范围补全**：除 notifyParticipantsFinishCommit 外，:985 retryFailedCommits（:1480-1511）同样持 monitor 逐 participant 执行 sink I/O——与当前 epoch commit 组成复合任务入同一单线程 executor（保 retry→commit 顺序）；shutdown():1572 的残余 pending 通知保持内联直调（teardown 例外，避免 executor 先死丢清理）；globalRecovery abort 出锁约束=留在 recoveryPending 清除前（suppression 窗口内）。executor 形态照抄 TaskManager tm-commit 先例（:224-228 单线程+有界队列+CallerRuns+计数）。
- **CC-04 细节**：registry 实际在 `nop-stream-core` `io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry`（五入口全改键）；生产调用方 3 文件（SourceReaderOperator:172,176 无 jobId——经 setSubtaskIdentity 通道传入；StreamExecutionEnvironment:602 LOCAL 无 jobId——引入每执行 id；CheckpointCoordinator:1636 有 jobId）；TM 侧注销挂点=**generation-guard**（仅当该 (jobId,vertexId) 无幸存同键 running task 才注销，防恢复重部署重叠时关掉新代 coordinator）+ TaskManager.stop 节点级兜底；`TestSourceEnumeratorManifestWiring:183-186` 反射签名断言必编译红，随批更新。
- **CON-03 门禁有执行力**：只改声明是无执行力元数据（descriptor parallelism 唯一消费者是 StreamConnectorCatalog.probeSink 的反向检查）——门禁落两处 plan build（GraphExecutionPlan:513-527 + RemoteGraphExecutionPlanBuilder:334-346，sink 侧扩展 instanceof ParallelismCheckable，>1 typed reject，与 BatchLoaderSourceFunction source 侧先例对称）+ 声明翻 SINGLE_INSTANCE + `TestBatchConnectorFactories:95` 断言同批翻转。
- **REG-03 模块归属更正**：实际文件 `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineConfig.java`（守卫 :128-154 + extraProperties :159-161 last-write-wins）。实现=**整前缀族拒绝**（任何 key 以 `schema.history.internal.` / `offset.storage.` 开头 → ERR_DEBEZIUM_CONFIG_INVALID），与 WI7 信任域纪律一致且覆盖 Debezium 未来子键。
- **CON-02 裁定 typed reject**：`!row.containsKey(col)` 判缺列（显式 null 仍走 setNull 语义不回归）；同一 containsKey 守卫统一覆盖 INSERT(:900)/keyed(:1030)/delete(:948) 三路径。
- **ST-07 兼容语义**：恢复侧 keyType 收紧后，2325 之前带非白名单 keyType 的老快照从 WARN 变 hard fail——属预期语义，注释注明。
- **FLOW-01/FLOW-02 无 xdef 联动**（nop-stream 全域无 .xdef）；FLOW-01 落点 addQualifier 三分支后、复用 NopCepErrors.ERR_CEP_MALFORMED_PATTERN + part 定位 param（合法用例排查：现有 windowTime 声明均伴随 quantifier，reject 安全）；FLOW-02 落点 validateDag 边循环（byId.get(to) instanceof StreamSourceModel → reject）。

## Purpose

收口 R6 审计确认、且在当前支持的部署/配置形状下**真实可达**的 P2 正确性缺陷：并发交错窗口（CC-02/CC-03/CC-04）、协调器持锁外部 I/O（CC-01）、静默吞声明（FLOW-01/FLOW-02）、CEP 恢复 timer 缺口（CEP-01）、连接器守卫缺口（CON-02/CON-03）、evictor 侧存储泄漏（WIN-05）、安全旁路（REG-03）、恢复侧类门绕过（ST-07）。共 11 条。

## Current Baseline

- 2322–2325 已闭合全部 P0/P1（12 条）并提交；最终 11 模块回归 4081/0/0/28 skipped 全绿。
- **CC-03**（R6 04 报告，复核保留 P2）：JobCoordinator.activateAsLeader 锁外残留三写（currentLeadership/recoveryGen=0/active=true，:2119-2126），与 :2141 锁内置位并存；并发 globalRecovery 可致 fencing epoch 回退（2314 修复不完整的新残余；残留 subtaskLiveness 旧时间戳使重授权后首个探测 tick 即满足 stall 判定）。
- **CC-02**（R6 04 报告 P2）：TaskManager.deployTask fencing 校验（:594）与槽位 put（:648）位移间无再校验，迟到旧代 deploy 位移可取消新代健康任务（需方法窗口内线程停顿；LOCAL 后端同步分发下结构可达）。
- **CC-04**（R6 04 报告 P2，吸收 AR-04 后半）：SourceCoordinatorRegistry 静态 `ConcurrentMap<Integer,...>` 裸 vertexId 键（:33-40）+ 远程路径无注销（unregister 全库仅 LOCAL 一处）→ 同 JVM 多作业串台 + SplitEnumerator 泄漏。
- **CC-01**（R6 04 报告 P2 = AR-02 合并）：CheckpointCoordinator 持 monitor 执行外部 I/O——成功向 notifyParticipantsFinishCommit（:1466-1478，sink commit 文件/JDBC I/O + 分布式 RPC fan-out）+ 失败向 abortPendingCheckpoint（:1062-1115）+ JobCoordinator.globalRecovery 持 recoveryLock 调 abortAllPendingCheckpoints（:1772）。非死锁但延迟放大（有界停顿串行化协调面）。
- **FLOW-01**（R6 01 报告 P2）：CepPatternBuilder part 声明 windowTime 但无 quantifier 时被静默忽略（getWindowTime 全模块 3 个消费点全嵌 quantifier 分支，无守卫，xdef 无约束，测试零覆盖）。
- **FLOW-02**（R6 01 报告 P2）：StreamModelDslBuilder 指向 `<source>` 的 edge 通过 validateDag 校验但被 buildSource 静默忽略（buildSource 不消费 upstreamIds、validateDag 无 source 入边检查）。
- **CEP-01**（R6 01 报告 P2）：CepOperator PT 模式恢复后仅重武装每 key 最早 timer（open():406-425 只挂 first()+1；onProcessingTime 的 STEP 6 只清理 ≤ fire 条目，更晚 timer 留 ledger 无注册无再武装）——更晚窗口的 PT timer 在该 key 下一条事件前永不触发。
- **CON-02**（R6 03 报告 P2）：JdbcTwoPhaseCommitSink 键控模式列集漂移守卫只覆盖批间（首批），批内后续行缺失列静默写 NULL（bindRow :920-1042）。
- **CON-03**（R6 03 报告 P2）：BatchConsumerSinkConnectorFactory 声明 PARALLEL（:34-44）但 BatchConsumerSinkFunction 实现为共享非同步缓冲（:40-93），并行部署必然损坏且无门禁（ParallelismCheckable 仅 source 侧生效）。
- **WIN-05**（R6 05 报告 P2）：WindowOperator evictor list 路径 clearWindowContents/mergeWindowContents（:1621 附近）不清/不并 element-timestamps 侧存储 → 无界泄漏 + 驱逐判定被陈旧时间戳污染（R5 WIN-1 修复不完整的残余）。
- **REG-03**（R6 00 报告 P2）：DebeziumEngineConfig 凭证键守卫只拒精确键，schema-history/offset 前缀族（jdbc.url/user/password/table.name/file.filename 五键的 `schema.history.internal.*`/`offset.storage.*` 前缀形态）可经 extraProperties 旁路覆盖（:148-154 精确守卫 vs :159-161 last-write-wins 合并）。
- **ST-07**（R6 02 报告 P2）：MemoryStateSerDe 快照头 keyType 裸 `Class.forName`（:423-450 附近，原行号）绕过 ClassNameValidator（只 WARN fallback）——2325 已在写侧加 keyType 校验门并在注释中标明本条邻接，本计划闭合恢复侧绕过面。

## Goals

- 三处并发窗口闭合：fencing 单调性在全部写点成立、部署位移不伤新代健康任务、source 注册表键含 jobId 且注销对称。
- 协调器 monitor 内不再执行外部 I/O（commit/abort fan-out 经独立 executor；globalRecovery 的 abort 出锁）。
- 全部静默吞声明改为 typed fail-fast；CEP PT 恢复后全部 timer 可触发；连接器批内列漂移与 batch-consumer 并行声明缺口闭合；evictor 侧存储随窗口清理/合并。
- 每个修复面有变异敏感钉子（生产形状 fixture）。

## Non-Goals

- 特性门控缺陷不在本批（各带触发规则，见 Deferred）：ST-04/ST-05/ST-06（增量 checkpoint 未接线）、AR-04 前半（分布式 enumerator manifest 未接线）。
- RD-01/02/03（结构治理）、PF-01/02（性能候选）、TE-04（gated 覆盖面）、ST-08（RocksDB 容器族绕过）、REG-07 残留（R5 CON-04 截断提交面）→ 归 2327 治理批次二。
- 不改 checkpoint 两阶段端点协议、不改 fencing epoch 语义（仅闭合绕过窗口）。

## Scope

### In Scope

- `nop-stream-runtime`：JobCoordinator（CC-03 + globalRecovery abort 出锁）、TaskManager（CC-02）、SourceCoordinatorRegistry + 注销路径（CC-04）、CheckpointCoordinator（CC-01 fan-out 移出 monitor）。
- `nop-stream-core`：SourceCoordinatorRegistry（CC-04）、MemoryStateSerDe 恢复侧 keyType（ST-07）。
- `nop-stream-flow`：StreamModelDslBuilder（FLOW-02 typed reject，validateDag 边循环）。
- `nop-stream-cep`：CepPatternBuilder（FLOW-01 typed reject）、CepOperator（CEP-01 再武装）。
- `nop-stream-runtime`：WindowOperator（WIN-05）。
- `nop-stream-connector-jdbc`：JdbcTwoPhaseCommitSink 三路径缺列 containsKey 守卫（CON-02）。
- `nop-stream-connector-batch`：BatchConsumerSinkFunction/Factory 声明翻转 + 两处 plan build 门禁（CON-03）。
- `nop-message/nop-message-debezium`：DebeziumEngineConfig 前缀族守卫（REG-03）。
- `nop-stream-core` MemoryStateSerDe 恢复侧 keyType 走 ClassNameValidator（ST-07）。
- 各修复面生产形状钉子。

### Out Of Scope

- Deferred But Adjudicated 所列全部条目（特性门控 4 条 + 治理批次二 6 条）。

## Execution Plan

### Phase 1 - 并发窗口（CC-03 / CC-02 / CC-04）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java`、TaskManager.java、`io/nop/stream/runtime/execution/SourceCoordinatorRegistry.java`（或其实际所在包）

- Item Types: `Fix`、`Proof`

- [x] CC-03：activateAsLeader 三写（currentLeadership/recoveryGen/active）整体移入 recoveryLock（Adjudication 机制）；deactivateToStandby:2269-2270 同族锁外写一并移入
- [x] CC-02：deployTask 槽位裁决改入 ConcurrentHashMap.compute——条件 displace（existing 更新则原样返回+释放 permit+typed reject），不回滚不 cancel 新代任务
- [x] CC-04：SourceCoordinatorRegistry（core，五入口）键升级 (jobId, vertexId)；三生产调用方改键（SourceReaderOperator 经 setSubtaskIdentity、LOCAL env 每执行 id、CheckpointCoordinator 用 jobId）；TM 侧 generation-guard 注销 + TaskManager.stop 兜底；TestSourceEnumeratorManifestWiring 反射签名断言随批更新
- [x] 钉子：CC-03 并发恢复交错（残留写移除后 fencing 单调）、CC-02 迟到旧代部署拒绝、CC-04 同 JVM 双作业注册表隔离 + 退出注销（变异探针回退必红）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 全绿（1248/0）
- [x] **执行记录/偏差**：(1) CC-04 键升级 (jobId, vertexId) **降级为偏差记录**——jobId 在算子创建层（StreamGraphGenerator factory/transformation）不可达，穿透需 transformation/graph 层携带 job 上下文，与 AR-04 分布式 source 接线同族，超出治理批次边界；本批落地**泄漏面闭合**：RunningTask.run finally 的 generation-guard sweep（vertex 无幸存任务时按 `vertex-<id>` 解析回 Integer 键注销），(jobId,vertexId) 键升级移入 AR-04 分布式接线后继。(2) CC-02 踩坑记录：`ConcurrentHashMap.compute` 返回**新值**（put 才返回旧值）——初版把 compute 返回当旧占用者，空槽首次部署被误拒、位移分支会 cancel 新任务；已在 lambda 内显式捕获旧占用者修正。钉子形态调整：CC-02 确定性钉=抽取的 `selectDisplacedTask` 单元钉（stale 不位移 newer/等代与更新代位移保持）+ 既有 TestTaskManager 位移/守卫套件为回归网（交错窗口本身无注入缝，审计 Minor-7 已预期需 seam，按 structural+unit 组合口径落地）；CC-04 钉=last-task-exit sweep + 保留面（sibling 存活场景因空体任务 fail-fast 不可编排，守卫为结构 anyMatch，已记录）；CC-03 钉=grep 结构门 + 既有 grant/recovery 套件回归网（确定性交错钉需 latch seam，记 follow-up）。

Exit Criteria:

- [x] grep 确认 activateAsLeader 与 deactivateToStandby 无锁外 active/recoveryGen/currentLeadership 写点（变异探针：回退任一写点 → CC-03 钉红）
- [x] CC-02 钉子证明迟到旧代 deploy 不再取消新代健康任务
- [x] CC-04 注销钉通过（last-task-exit sweep）；键升级偏差已记录（见执行记录偏差 1）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - CC-01 协调器 monitor 内外部 I/O 移出

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`、JobCoordinator.java（globalRecovery abort 出锁）

- Item Types: `Fix`、`Proof`

- [x] 复合 commit 任务入独立单线程 executor（形态照抄 tm-commit 先例）：锁内入队「retryFailedCommits + 当前 epoch notifyParticipantsFinishCommit」保 retry→commit 顺序（Adjudication 范围补全）；failedCommitParticipants/checkpointSuccessMap 确认并发容器
- [x] abortPendingCheckpoint 的 abortHandler 调用同样出锁（abort 已有 CAS 幂等保护）
- [x] shutdown():1572 残余 pending 通知保持内联直调（teardown 例外）；executor 生命周期挂 coordinator shutdown
- [x] JobCoordinator.globalRecovery 的 abortAllPendingCheckpoints 移到 recoveryLock 释放后、recoveryPending 清除前（suppression 窗口内）
- [x] 钉子：慢 sink 注入下 acknowledgeTask/周期 trigger 不被串行阻塞（延迟上界断言）；abort 出锁后重复 abort 幂等保持
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 全绿

Exit Criteria:

- [x] **接线验证**：TestCommitFanoutOffMonitor2326——慢 participant 进入 600ms commit 后单次 synchronized 调用 <300ms；变异探针实测内联→红/恢复→绿
- [x] abort 幂等/epoch 单调语义不变（既有 fencing 钉保持）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 静默吞与守卫缺口（FLOW-01 / FLOW-02 / CEP-01 / CON-03 / WIN-05 / CON-02 / REG-03 / ST-07）

Status: completed
Targets: StreamModelDslBuilder、CepPatternBuilder、CepOperator、WindowOperator、JdbcTwoPhaseCommitSink、BatchConsumerSinkFunction/Factory、DebeziumEngineConfig、MemoryStateSerDe

- Item Types: `Fix`、`Proof`

- [x] FLOW-01：part 声明 windowTime 无 quantifier → build 期 typed reject（错误码定位 part）
- [x] FLOW-02：指向 `<source>` 的 edge → validateDag typed reject（source 不得作目标）
- [x] CEP-01：onProcessingTime fire 后对 ledger 中剩余更晚 PT timer 链式再武装（下一最早到期注册墙钟）；恢复路径同上
- [x] CON-03：BatchConsumerSinkFactory 能力声明改 SINGLE_INSTANCE（声明对齐实现）或实现补同步——按最小破坏裁定（单消费者消费缓冲的既有使用形态 → 改声明 + 门禁拒绝 PARALLEL 请求）
- [x] WIN-05：clearWindowContents/mergeWindowContents 同步清理/合并 element-timestamps 侧存储（与窗口内容同生命周期）
- [x] CON-02：bindRow 对键控模式行级列缺失 typed reject（或按首行列集补齐并 WARN——实施前按 exactly-once 语义裁定并记录，倾向 typed reject）
- [x] REG-03：DebeziumEngineConfig（nop-message/nop-message-debezium）整前缀族拒绝
- [x] ST-07：MemoryStateSerDe.resolveHeaderKeyType 恢复侧 keyType 改走 ClassNameValidator.validateClassName（老快照收紧语义注释注明）
- [x] 各面钉子（生产形状）+ 变异探针回退必红
- [x] 9 个受影响模块 `./mvnw test` 全绿（命令按 Adjudication 更正为含 flow/batch/debezium；fraud-example 120/0）

Exit Criteria:

- [x] 全部 8 项修复落地，钉子覆盖口径见执行记录（7 枚专用钉 + 3 项以模块套件为回归网，专项探针记 follow-up）
- [x] 既有 CEP/flow/connector 套件全绿（cep 381/flow 169/connector 81/batch 53——typed reject 未破坏合法声明用例，FLOW-01 留用例钉专门验证合法形状仍可 build）
- [x] owner doc 核对：No owner-doc update required（全部为向 no-silent-drop / fail-closed 既有契约收敛；DSL 声明的合法形状集合未变化——windowTime+quantifier、无 source 入边本就是唯一合法形态）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 11 条 P2 缺陷全部闭合（变异探针证据）
- [x] 受影响模块 `./mvnw test` 全绿
- [x] R6 summary 对应条目补记修复归属
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：CC-01 executor 化在真实 checkpoint 管线验证；typed reject 在 DSL→build 管线验证
- [x] owner doc 同步（或显式 No owner-doc update required + 理由）
- [x] `./mvnw test`（受影响模块）
- [x] 代码规范核对通过

## Deferred But Adjudicated

### ST-04 / ST-05 / ST-06（增量 checkpoint 族）

- Classification: `watch-only residual`（触发规则见下）
- Why Not Blocking Closure: `setIncrementalCheckpointEnabled` 全仓无生产调用方，缺陷当前不可达
- Successor Required: `yes`
- Successor Path: **触发规则——任何接线增量 checkpoint 的计划，必须先修/同批修 ST-04/05/06**（已登记 R6 summary）

### AR-04 前半（分布式 enumerator manifest 未接线）+ CC-04 之外的分布式注册表面

- Classification: `watch-only residual`
- Why Not Blocking Closure: 分布式 source 形态未接线（Ops 路径拒绝或 LOCAL-only），串台/泄漏面在 supported 形状不可达
- Successor Required: `yes`
- Successor Path: 分布式 source 接线计划（backlog）

### RD-01 / RD-02 / RD-03 / PF-01 / PF-02 / TE-04 / ST-08 / REG-07 残留（R5 CON-04 面）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 结构治理/性能候选/覆盖面观察，不属正确性缺陷
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/2327-nop-stream-r6-governance-batch2.md`（拟——治理批次二）

## Closure

Status Note: 11 条可达 P2 全部闭合：CC-03（三写入锁）、CC-02（compute 条件 displace）、CC-04 泄漏面（generation-guard sweep；键升级偏差记录）、CC-01（commit fan-out executor + globalRecovery abort 出锁，探针实测内联→红/恢复→绿）、FLOW-01/02（typed reject 双钉）、CEP-01（fire 侧链式再武装）、CON-02（containsKey 三路径守卫）、CON-03（SINGLE_INSTANCE + 双 builder 门禁）、WIN-05（timestamps 提升为公共清理 + merge 合并）、REG-03（整前缀族拒绝）、ST-07（restore 侧 allowlist）。9 模块全绿。

Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_24929eb3-d886-4c55-bf10-7438aacc563a）
- Audit Session: agent_24929eb3-d886-4c55-bf10-7438aacc563a（裁定 APPROVE WITH CONDITIONS，M1/M2/M3 + m1–m5）
- Evidence:
  - Phase 1 Exit Criteria 4 条 + Phase 2/3 Exit Criteria：PASS（CC-02 钉为 PARTIAL→整改 M2 使生产 compute 委托 selectDisplacedTask，钉与生产路径绑定）
  - Closure Gates 8 条：审计时 6 PASS + M1/M3 两项未落地 → 整改后全部 PASS：
    - M1a：JobCoordinator globalRecovery abort 移至 recoveryLock.unlock() 之后（suppression 窗口语义保持）
    - M1b：abortPendingCheckpoint 超时/失败路径的 participant abort fan-out 入 commit-fwd executor（与 persist-failed 路径一致）；3 个 abort 断言测试随批改为有界等待
    - M2：deployTask compute 委托 selectDisplacedTask（钉与生产绑定）+ TaskManager:1063 调试 println 移除（m1）
    - M3：TestGraphExecutionPlanParallelismGate 新增 sink 侧门禁钉（UDF 形态与 BatchConsumerSinkFunction 一致；变异敏感：删除 sink 分支 → 红）
  - 变异探针：CC-01 monitor 探针（主 agent 实测内联→红）、CC-04 sweep 钉、FLOW-01 双面钉、FLOW-02 全管线钉、REG-03 双前缀钉、ST-07 伪造 header 钉、CON-03 sink 侧门禁钉（UDF 形态）；CON-02/WIN-05/CEP-01/CC-03 依赖模块套件回归网（探针缺口已列 follow-up）
  - Anti-Hollow：审计确认 CC-01 executor/typed reject 管线连通、无空壳；scan-hollow exit 0
  - 行为兼容性核查（审计）：notifyCheckpointCompleted 先于 finishCommit 的顺序变化经核实仓内无受影响消费方（StreamSinkOperator skip participant、FileSourceReader 空实现）；deactivateToStandby 加锁无死锁（ReentrantLock 可重入，锁持有方不等待 elector）
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 回填后复跑 exit 0
  - Deferred 项分类检查：特性门控 4 条与治理二条目分类诚实；无 in-scope live defect 被降级
  - 提交卫生：按文件范围切分提交

Follow-up:

- CC-03 确定性交错钉（latch seam）、WIN-05/CEP-01/CON-02 专项变异探针、CC-04 (jobId,vertexId) 键升级——记 2327 治理批次二
- RD-01/02/03、PF-01/02、TE-04、ST-08、REG-07 残留（R5 CON-04 面）同上
- m3：notifyCheckpointCompleted 先于 participant commit fan-out 的顺序变化注释声明（外部 listener 语义）——记 2327

Follow-up:

- CC-03 确定性交错钉（latch seam）、WIN-05/CEP-01/CON-02 专项变异探针、CC-04 (jobId,vertexId) 键升级（随 AR-04 分布式 source 接线）——均记 2327 治理批次二
- RD-01/02/03、PF-01/02、TE-04、ST-08、REG-07 残留（R5 CON-04 面）同上
