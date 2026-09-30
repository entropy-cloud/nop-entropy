# 368 nop-stream 审计 R5 波次——P0/P1 缺陷修复与治理批次

> Plan Status: active
> Last Reviewed: 2026-09-30
> Source: `ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/`（9 份维度报告 + summary，125 项发现）+ `ai-dev/audits/2026-09/2026-09-30-0530-adversarial-review-nop-stream/01-open-findings.md`（对抗审查 11 项）；plan 审查记录：独立子 agent 对抗性审查（含想象性分析）2026-09-30 第 1 轮（1 Blocker + 7 Major + 5 Minor）与第 2 轮复审（13 点 12 PASS），全部发现已折入本版
> Related: `ai-dev/plans/366-nop-stream-audit-r4-quality-perf.md`（上一波次，已 completed）

## Purpose

修复 2026-09-30 R4 轮深度审计（9 维度 + 开放式对抗审查，基线 HEAD=66eaa9ae91）确认的 P0×1、P1 代码缺陷×11、P1 文档缺陷×4，完成高价值 P2 缺陷批次与治理批次，并为**全部剩余发现给出逐项裁定**（修复 / Deferred But Adjudicated / Non-Blocking Follow-ups，见"剩余发现裁定总表"）。修复以"响亮失败优于静默错误"为准绳，每项修复带聚焦回归测试。

## Current Baseline

- 基线 HEAD = 66eaa9ae91；`cd nop-stream && ../mvnw test -pl nop-stream-core,nop-stream-flow,nop-stream-runtime,nop-stream-cep,nop-stream-rocksdb -am` 全绿（`_tmp/r5-baseline-test.log` EXIT=0）。HEAD 之后有一个不触 nop-stream 的 docs-only 提交，基线声明仍成立。
- **AR-01（P0）已由主 agent 亲自核实**：路由用 `(key.hashCode() & Integer.MAX_VALUE) % numPartitions`（`nop-stream-core/.../datastream/DataStreamImpl.java:403`）；状态属主用 `(stableHash(key) & 0x7FFFFFFF) % maxParallelism` 后按连续 KeyGroupRange 归属子任务（`nop-stream-core/.../state/shard/KeyGroupAssignment.java:91` `assignToKeyGroup`）；`KeyGroup.DEFAULT_MAX_PARALLELISM = 128`（`nop-stream-core/.../state/shard/KeyGroup.java:41`）。两公式仅当 maxParallelism==parallelism 且 key-group 与子任务 1:1 时重合；默认配置（128≠p）下 rescale/reshard 归置与记录路由系统性错位。
- **ST-03 已核实**：`isStableValueHashType` 对 `Enum` 返回 true（`KeyGroupAssignment.java:203`）→ `stableHash` 直接用 `key.hashCode()`（identity hash，跨 JVM 不稳定）。
- 关键实现面事实（独立审查子 agent 逐行核实）：
  - HASH partitioner 在 `DataStreamImpl.keyBy():261` 构造（`KeySelectorPartitioner`，Serializable 可烤入参数），构造点经 `env.getCheckpointConfig().getStateBackend().getMaxParallelism()`（`CheckpointConfig.java:324`、`IStateBackend.java:52`）可得 maxParallelism；stateBackend 为 null 是默认常态，此时有效默认 = `KeyGroup.DEFAULT_MAX_PARALLELISM`（`TaskCheckpointWiring.java:172-175` 路径 new MemoryStateBackend）。
  - memory serde 重材料化工具存在但是 `MemoryStateSerDe.deserializeKey`（:667）**private 实例方法**，需先抽为 core 内静态工具再复用。
  - RocksDB 快照头已写 `keyType`（`RocksDBSnapshotSerDe.java:90`），三个 restore 入口不读它（`putEntry:409` 用 JSON 原生 key 算 group）。
  - `PendingCheckpoint.abort`（:168-179）双重 CAS 与审计一致；同文件 `forceFail`（:211-229）是"读-判断-补完 future"的修复先例。
  - CC-02 的 pendingReplay 数据源是 store（`InputChannel.activateMaterializationReplay:224-238` `point.replay(fromEpoch)` 快照），非 drain 返回值；`drainBufferedElements`（:514-536）释放 permit、不动 finished 标志，可单测顺序直调构造交错。
  - CON-01 账本为 (epoch_id, subtask_id) 两列 PK、DDL 仅 3 列、guard 命中仅 INFO（`JdbcTwoPhaseCommitSink.java:301/408-419/537-541`）；**sink 上下文当前无 jobId**（`RuntimeContext` 仅 `getTaskName()`；`copyForSubtask(int)` 只携带 index），身份管道需新建。
  - CON-02/03 的引擎失败面在 `nop-message-debezium` 的 `DebeziumEngineWrapper`（`EngineCompletionCallback` 私有内部类 :152、`start()` 不接受失败回调 :71）；nop-stream 侧载体是 `DebeziumCdcSourceFunction`（当前无 pendingError 字段）。
  - REG-01 三条件均可观测：F1 跳过在 `SupervisionLoop.restartRegion:498-510`；no-mat 内部边在 `buildConsumerInvokableWithReplay:817`；checkpoint 回滚在 `resolveConsistentCutEpochAndRestoreOperators`。
  - CEP-01：`registerTimer:808-814` PT 分支不写 ledger；`drainDueBuckets:1063+` 仅凭 ledger；`reconcileTimerLedgerIfNeeded:1107+` 每 key 仅一次。
- 其余 P1/P2 事实由 9 份审计报告逐条锚定（文件+行号+证据片段），执行时以 live code 复核为准。
- plan 366 回归审计（`00-plan366-regression.md`）：9 项修复全部落地无 fix-of-fix 回归；REG-01 为修复后新暴露的语义缺口。CC-02 是 plan 366 已登记 follow-up"回放窗口先存缝隙"的确认升级（真实数据错误）。

## Goals

- P0 AR-01：keyBy 记录路由与 keyed-state 属主统一到同一公式（stableHash → key-group → 属主子任务），POJO/enum key 跨 JVM 稳定；先落真实路由 E2E 钉子测试确证缺陷，再修复。
- P1 代码 11 项全部修复（ST-01/02/03、CC-01/02/03、CON-01/02/03、CEP-01、REG-01），每项带能捕获回归的聚焦测试。
- P1 文档 4 项全部修复（DC-01/02/03/04），示例与 live API 一致、模块表 10/10。
- 高价值 P2 缺陷批次（Phase 7，11 项）与治理批次（Phase 8）落地。
- 剩余全部发现（P2/P3）逐项裁定并记录于"剩余发现裁定总表"，closure gate 可验证。

## Non-Goals

- 不重开既往 Deferred 裁定：A3-A6/B2/B7、G1-G3、D4/A11、跨模块测试脚手架合并、RemoteTaskDeploySupport 语义分歧（本轮抽查未被破坏）。
- 不做 JobCoordinator 拆分（RD-05）、executor 重复收敛（RD-06/07/08）、大并发面重构（CC-06/07/17）——见裁定总表。
- 不做性能优化实施（PF 组维持"无 ≥2% 低风险可收割项"收敛裁定）；基准缺口只登记 follow-up。
- 不引入新功能。公开 API 形状仅允许两类缺陷修复必需的最小变更：(1) Phase 1 partitioner 构造参数（烤入 maxParallelism）；(2) Phase 4 `copyForSubtask` 家族的 @Internal 签名扩展（任务身份管道，CON-01/CON-05 共用）。
- nop-message-debezium 上游模块**仅允许一处最小变更**：`DebeziumEngineWrapper` 暴露引擎失败回调（CON-03 必需）；其余上游模块代码只读。

## Scope

### In Scope

- `nop-stream-core`：DataStreamImpl 路由、KeyGroupAssignment（含 G38 javadoc 改写）、PendingCheckpoint 相关（runtime）、MemoryStateSerDe 重材料化工具抽取、KeyGroupRangeRestoreFilter、KeyGroupReshard、CheckpointSerDe。
- `nop-stream-rocksdb`：RocksDBSnapshotSerDe restore 重材料化、MapState 快照分组键。
- `nop-stream-runtime`：JobCoordinator（fencing 轮换隔离、cancelTask fan-out、subtaskLiveness recovery 清理、中断恢复、zombie 心跳 attempt 过滤短期修复）、SupervisionLoop（REG-01 fail-fast、CC-02 回放缝隙、AR-04 控制事件、REG-07/AR-05 attach 防御）。
- `nop-stream-connector`（MessageSourceFunction 退订、唯一键冲突判定）、`nop-stream-connector-jdbc`（2PC 账本命名空间）、`nop-stream-connector-debezium`（pendingError 轮询重抛、引擎线程死亡接线）、`nop-stream-connector-batch`（并行度 fail-fast、currentOffset 同步）。
- `nop-stream-cep`：CepOperator PT ledger、指标注册表隔离、NFACompiler null-target 边、MalformedPatternException 分层。
- `nop-stream-flow`：`<custom><source>` xpl body 静默蒸发的 fail-fast。
- 文档：`nop-stream/README.md`、`docs-for-ai/03-modules/nop-stream-user-guide.md`、`ai-dev/design/nop-stream/`（cep-design、01-architecture-baseline、state-management-design、checkpoint-design、failover-design）、`docs-for-ai/INDEX.md`、`docs-for-ai/02-core-guides/error-handling.md`。
- 测试：上述修复的聚焦回归测试 + surefire fork 超时护栏。

### Out Of Scope

- nop-message-core / nop-batch-core 上游模块代码（只读）。
- K8s/YARN 编排、SQL/Table API 等 non-goal 能力。
- 性能基准建设（除已登记 follow-up 外）。

## Execution Plan

### Phase 1 - P0 AR-01：路由/属主公式统一（含 ST-03）

Status: completed
Targets: `nop-stream-core/.../datastream/DataStreamImpl.java`、`nop-stream-core/.../state/shard/KeyGroupAssignment.java`、KeyGroupRange 归属逻辑、既有 rescale/reshard E2E 测试族

- Item Types: `Fix | Proof`

- [x] **钉子测试先行**：新增 E2E 测试（放 nop-stream-runtime，与既有 `TestKeyGroupRescaleDispatchE2E` 同族），用**真实路由路径**（fromElements→keyBy→KeyedProcess/聚合→实际分区投递）产出 savepoint，再 rescale 恢复，断言恢复后各 key 的状态落在能继续收到对应记录的子任务（当前预期 FAIL，确证 AR-01）。
- [x] 修复：KeyGroupAssignment 新增"key → 属主子任务"单一入口（`assignToSubtask(key, maxParallelism, parallelism)`：stableHash → key-group → KeyGroupRange 属主）；`DataStreamImpl.keyBy()` 构造 partitioner 时烤入 maxParallelism——取 `env.getCheckpointConfig().getStateBackend().getMaxParallelism()`，stateBackend 未配置时取 `KeyGroup.DEFAULT_MAX_PARALLELISM`，仅非法值（<1）fail-fast。
- [x] ST-03 修复：`isStableValueHashType` 的 Enum 分支改为按 `((Enum<?>) key).name()` 的 String hash 参与 stableHash。
- [x] 同步改写 `KeyGroupAssignment` G38 javadoc（:33 "routing parity" 表述——修复后 routing 与 ownership 公式真正合一，javadoc 必须反映新契约）。
- [x] 单元测试（core）：路由公式与属主公式对 String/Integer/Date/POJO/Enum key 全等；enum key stableHash 与 `name()` String hash 相等且跨实例构造稳定；stateBackend 未配置时默认 128 生效、非法值 fail-fast。
- [x] 既有 rescale/reshard 测试族中**手工构造 savepoint 的测试**改注真实路由口径或保留并注明差异（不删除覆盖）。

Exit Criteria:

- [x] 钉子 E2E 测试存在且在修复前可复现错位（复现输出记录于 log）、修复后通过。
- [x] `nop-stream-core` 与 `nop-stream-runtime` 测试全绿。
- [x] Enum key 的 `KeyGroupAssignment.stableHash` 等于 `name()` String hash（测试断言通过）。
- [x] owner doc：`ai-dev/design/nop-stream/state-management-design.md`（或 failover-design 的 G38 段）的路由/属主契约表述已与新公式一致。
- [x] `ai-dev/logs/2026/09-30.md` 已更新。

### Phase 2 - 状态子系统 P1（ST-01 abort future 死代码、ST-02 restore 重材料化、ST-06 serde 响亮失败）

Status: completed
Targets: `nop-stream-runtime/.../checkpoint/PendingCheckpoint.java`、`nop-stream-rocksdb/.../RocksDBSnapshotSerDe.java`、`nop-stream-core/.../MemoryStateSerDe.java`（工具抽取）、`nop-stream-core/.../state/shard/KeyGroupRangeRestoreFilter.java`、`nop-stream-core/.../state/shard/KeyGroupReshard.java`、`nop-stream-runtime/.../checkpoint/storage/CheckpointSerDe.java`

- Item Types: `Fix`

- [x] ST-01：`PendingCheckpoint.abort` 消除双重 CAS——参照同文件 `forceFail` 的"读-判断-补完"先例：abort 后无论 CAS 结果如何，future 一律以 abort 原因 `completeExceptionally`（幂等，不覆盖已完成的正常 complete）。测试：超时 abort 后等待方收到 `ERR_STREAM_CHECKPOINT_ABORTED`（而非 TimeoutException）。（`PendingCheckpoint.abort` 重写 + `TestPendingCheckpointAbortFuture` 4 用例；修复前钉子验证：协调器预 CAS 场景等待方永不释放（`_tmp/r5-p2-prefix-runtime.log`））
- [x] ST-02（前置：把 `MemoryStateSerDe.deserializeKey` 的重材料化逻辑抽为 core 内静态工具）：RocksDBSnapshotSerDe 三个 restore 入口从快照头读 `keyType` 并在计算 key-group 前重材料化 key；`KeyGroupRangeRestoreFilter` 与 `KeyGroupReshard` 在过滤/迁移前重材料化。非原始类型 key（Date/enum/bean）恢复后 key-group 与 live 路径一致。（新增 `StateKeyRematerializer`（core shard）；RocksDB 三入口经 `putEntry`/`restoreMapState` 重材料化（backend keyType 优先、快照头 `keyType` 回退）；filter/reshard 增加 keyType 重载，memory serde、`RescaleStateAssembler`、`MaxParallelismReshardMigration` 三个 caller 同步传入）
- [x] ST-06：`CheckpointSerDe.deserializeCheckpoint`（:166-175）对缺字段损坏行响亮失败（带行上下文的异常），不再返回 null 静默冷启动。（新增 `ERR_STREAM_CHECKPOINT_DATA_CORRUPT`，携带 jobId/checkpointId/缺失字段列表；`data==null/empty` 仍是合法"无 checkpoint"）
- [x] 测试：bean key / Date key 各一条 checkpoint→restore→可读写（RocksDB 与 memory 后端各一）；rescale 过滤前后条目数守恒且落位正确；损坏 manifest 行恢复响亮失败。（新增 4 测试类 20 用例：`TestRocksDBRestoreKeyRematerialization` 3、`TestMemoryRescaleNonPrimitiveKeyRestore` 2、`TestRestoreKeyRematerialization` 6、`TestCheckpointSerDeCorruptRecord` 5、另有 ST-01 的 4）

Exit Criteria:

- [x] PendingCheckpoint abort future 语义测试通过（abort 原因可见）。
- [x] bean/Date key 的 RocksDB 与 memory restore 测试通过（修复前应失败——执行时先跑一次记录输出于 log）。（修复前实测：memory Date rescale 错位落子任务、RocksDB Date MapState 恢复读 miss（null）；记录于 `_tmp/r5-p2-prefix-check.log`/`r5-p2-prefix-rocksdb.log`/`r5-p2-prefix-runtime.log`）
- [x] `nop-stream-core`、`nop-stream-rocksdb`、`nop-stream-runtime` 测试全绿。（三模块全量 1106 tests；除并行 Phase 4 在途的 `TestE2EJdbcTwoPhaseCommitSink` 4 errors（其 `stream_epoch_ledger_v2` 测试 schema 未同步）外全绿，本 Phase 触及面 0 回归；`_tmp/r5-p2-tests.log`）
- [x] owner doc：`ai-dev/design/nop-stream/checkpoint-design.md` 的 abort 语义段与新行为一致。（§8.7 新增"pending future 完成契约（ST-01）"段；§8.3.1 失败语义补 ST-06；`state-management-design.md` §3.1 新增"key 重材料化先于 group 计算（ST-02）"段）
- [x] `ai-dev/logs/2026/09-30.md` 已更新。

### Phase 3 - 协调/并发 P1（CC-01 wedge、CC-02 回放缝隙、CC-03 cancel fan-out）+ 同面 P2/P3

Status: completed
Targets: `nop-stream-runtime/.../coordinator/JobCoordinator.java`、`nop-stream-runtime/.../execution/SupervisionLoop.java`、`nop-stream-core/.../execution/InputChannel.java`

- Item Types: `Fix`

- [x] CC-01：`rotateFencingEpochCoreLocked` 对每节点 `updateFencingToken` 推送 try/catch 隔离（失败节点进入重试/恢复触发，不穿透中止整个 recovery）；`taskAssignmentMap.clear()` 移到推送成功之后或等价化（保证任何失败路径下 detectFailures 仍有工作集/哨兵兜底触发恢复）。测试：注入推送异常 → recovery 继续推进而非永久 wedge。
- [x] CC-02（含 AR-04、REG-07/AR-05 防御）：消除"先存缝隙"双投递。实现选型（审查确认两表述数学等价，取实现简单者）：attach 时对队列做第二次 drain，**丢弃**二次 drain 所得（其内容已含于 store 快照即回放集合），交付顺序 = store 回放段 → 后续活读段，全局有序保持；同步处理 drain 丢弃 watermark/control 事件的问题（AR-04：二次 drain 中的控制事件转交新 gate 而非丢弃）；`attachPendingReplay` 二次调用显式 fail-fast（REG-07/AR-05 隐式替换契约防御）。测试：以接缝顺序直调构造"T1 drain → 生产者写入 → T3 激活"交错，断言每条记录恰好交付一次、顺序保持；控制事件不丢。
- [x] CC-03：`stop()`/`terminateCancel()` 向所有已分配 TM fan-out `cancelTask`（best-effort + 失败记日志，不阻塞协调端关闭）。测试：stop 后 stub RPC 收到全部已分配任务的 cancelTask。
- [x] CC-04：recovery 清理 `subtaskLiveness` 旧代条目（测试：recovery 后陈旧时间戳不触发 stall）。
- [x] CC-05（短期修复）：`reportNodeTaskLiveness` 用已携带的 attemptNumber 对照 ClusterRegistry 当前 attempt，拒绝旧代 zombie 心跳（不需要 DTO 扩展；DTO 契约扩展留后续波次）。测试：旧 attempt 心跳被拒。
- [x] CC-11：`terminateWithTerminalSavepoint` 恢复中断位。
- [x] REG-02（N3 残余 TOCTOU）：COMPLETED liveness 键清除与在途心跳 merge 的竞态收口（putIfAbsent/merge 语义收窄，键不存在时不插入）。测试：并发交错下冻结时间戳不回插。

Exit Criteria:

- [x] CC-01/02/03/04/05、REG-02 各有聚焦测试且通过；CC-02 测试在修复前能复现双投递（执行时先跑一次记录）。（修复前复现：`_tmp/r5-p3-prefix-repro.log`——T1 drain → producer 写入 → T3 激活交错下 R2 双投递；新增 2 测试类 17 用例全绿）
- [x] `grep -n "cancelTask" .../JobCoordinator.java` 显示 stop/terminateCancel 路径存在调用（非仅 checkpoint abort 路径）。（`cancelAllAssignedTasks` 由 `stop()` 调用，`terminateCancel`/DRAIN/SUSPEND 终态均经 `stop()`）
- [x] `nop-stream-runtime`（含 core 联动）测试全绿。（`_tmp/r5-p3-tests.log` EXIT=0：core 1630 + runtime 1123，0 failures 0 errors）
- [x] owner doc：`ai-dev/design/nop-stream/failover-design.md` 的 region 重启回放/停止语义段与 CC-02/REG-01（Phase 5）新行为一致（可与 Phase 5 合并更新）。（§2.3 新增"恢复健壮性契约"段（CC-01/03/04/05/11+REG-02）；§五.4 reconnect-to-live-queue 与 Exactly-once 论证按 CC-02 四步序列改写（含 REG-03 哨兵口径顺带归真）；REG-01 失败语义段留 Phase 5 合并）
- [x] `ai-dev/logs/2026/09-30.md` 已更新。

> 执行记录（2026-09-30）：CC-02 实现取审查预案中的"等价排除语义"——live code 证实两处与"盲目丢弃"预案不符并已按任务预案偏离：① `drainBufferedElements` 先排空 pendingReplay 再排 queue，attach 后直调会把刚附的回放段抽走 → 二次 drain 定格在 attach **之前**（此刻 pendingReplay 为空，drain 即 queue-only），随快照一同组装；② 盲目丢弃会丢失"快照之后入队"的非重复记录（且 dual-write 先 store 后 enqueue 的次序使丢弃无法与快照包含性对齐）→ 改为实例同一性去重（store 与 queue 持同一对象引用，IdentityHashMap 集合），快照段 + 保留段一次 attach，AR-04 控制事件（watermark/status/latency marker 转发、陈旧 barrier ≤cut 丢弃、在途 barrier >cut 保留）同段处理。残余窗口（如实登记于 owner doc）：producer 线程恰好挂起于 dual-write 与 enqueue 之间横跨 T3→T3' 时该条仍可能双投递——窗口从"drain→attach 全程"收窄到指令级挂起。CC-05 的"当前 attempt"对照取物化工作集 `taskAssignmentMap`（与 ClusterRegistry 由 prepareAssignmentsLocked 同步写，免逐心跳注册表往返）；无当前 assignment 的心跳不可分类且无害（detectFailures 只读有 assignment 的键），放行——既有 R-5 单调 merge 测试口径保持。REG-02 取审计建议的 completed-tombstone 集合 + 专用监视器（比"键不存在不插入"更优：保住首次心跳建项语义，且 COMPLETED-remove 与心跳-merge 的临界区互斥真正闭合竞态）。CC-01 哨兵触发路径暴露 health 状态机 RECOVERING→RECOVERING 拒绝 → recovery 的健康回调改为安全包装（观测层失败不阻断恢复）。测试基建：`CoordinatorTestSupport.RecordingTaskRpcService` 增加 `failUpdateFencingToken`/`failCancelTask` 注入位。过程中的环境偏差：并行会话共享仓库下 in-process javac 偶发 `CompilerException: ConcurrentModificationException`（plexus 参数表竞态，与 plan 366 Phase 2 记录同源），`-Dmaven.compiler.fork=true` 绕过；全程未动 CEP/connector/StreamOperator 文件，Phase 4 的 `deepCopy(location)` 接线未回退。

### Phase 4 - 连接器 P1（CON-01/02/03）+ 同面 P2

Status: completed
Targets: `nop-stream-connector-jdbc/.../JdbcTwoPhaseCommitSink.java`、`nop-stream-connector-debezium/.../`（DebeziumCdcSourceFunction 等）、`nop-message-debezium/.../DebeziumEngineWrapper.java`（仅失败回调暴露一处）、`nop-stream-connector/.../MessageSourceFunction.java`、`nop-stream-connector-batch/.../BatchLoaderSourceFunction.java`

- Item Types: `Fix`

- [x] CON-01：JDBC 2PC 幂等账本键加入任务身份命名空间。身份管道：扩展 `copyForSubtask` 家族的 @Internal 签名携带（jobId + 算子标识）到达 sink（与 CON-05 共用同一管道；若实现中发现更小的部署期注入面，可改用并在此记录）。账本表迁移：启用带版本新表名（如 `stream_epoch_ledger_v2`），旧 3 列表不迁移、不读——跨版本升级首次部署建新表（响亮自洽，无静默混用）；guard 命中路径 INFO 升级 WARN 并带完整键上下文。测试：两条支路同 epoch/subtask 互不误伤；同版本 checkpoint 恢复路径自洽。
- [x] CON-02：`DebeziumCdcSourceFunction` 新增 `pendingError` 字段——wrapper 消费者捕获 collector 异常置 `pendingError`，`run()` 的等待循环每轮检查并重抛（任务失败、offset 不推进）。测试口径：注入 collector 异常 → run() 线程抛出该异常（不再宣称"dispatchEvent 抛出"）。
- [x] CON-03：`DebeziumEngineWrapper` 暴露失败回调（最小 API 面：`start(Consumer<Throwable>)` 或等价 setter）；`DebeziumCdcSourceFunction` 注册回调置 `pendingError`，与 CON-02 同一 `run()` 轮询路径触发任务失败。测试：引擎错误回调触发后任务进入 FAILED。
- [x] CON-04：MessageSourceFunction cancel-before-subscribe 竞态修复 + failed 退出路径退订（防 consumer 泄漏）。测试：cancel 与 subscribe 交错不泄漏订阅。
- [x] CON-05（与 AR-10 顺带）：batch-loader 源在 parallelism>1 时 fail-fast（经 CON-01 身份管道或部署期检查获得并行度），替代 N 倍重复投递；`currentOffset` 加同步（volatile/原子）。
- [x] AR-07：Debezium 路径 `if (!draining)` 恒真死条件清除（行为保持）。

Exit Criteria:

- [x] CON-01/02/03/04/05 各有聚焦测试且通过。
- [x] 账本新表名版本化方案在恢复路径自洽（同版本恢复测试通过）；旧表不迁移行为已在 user-guide 登记（owner doc，见下）。
- [x] `nop-stream-connector`、`nop-stream-connector-jdbc`、`nop-stream-connector-batch`、`nop-stream-connector-debezium` 四模块测试全绿（nop-message-debezium 若有测试亦须通过）。
- [x] owner doc：`docs-for-ai/03-modules/nop-stream-user-guide.md`（或 jdbc-2pc 章节）登记账本 v2 表与升级行为。
- [x] `ai-dev/logs/2026/09-30.md` 已更新。

> 执行记录（2026-09-30）：身份管道选定 `copyForSubtask(TaskLocation)` 重载族（TaskLocation 为 core 既有部署身份 DTO，不造新类型）；接线点 `GraphExecutionPlan.createSubtasks` / `RemoteGraphExecutionPlanBuilder.assembleSubtasks` / `SupervisionLoop.restartRegion`（重启用原部署 TaskLocation 保证命名空间跨重启稳定）。CON-05 走 plan 允许的部署期检查支线：新 core 接口 `ParallelismCheckable`，两处 plan builder 在建 subtask 前调用，并行度由此获得。上游变更面确认仅 `DebeziumEngineWrapper` 一个文件（connector-name 键控静态失败监听器 register/unregister/notify；`DebeziumMessageSource` 在禁改清单内，静态键控是与 offset registry 同身份约定的唯一可达通道）。证据：`_tmp/r5-p4-tests.log`（connector 四模块 -am 全绿 + nop-stream-runtime 全绿）、`ai-dev/logs/2026/09-30.md` Phase 4 条目。

### Phase 5 - CEP P1（CEP-01）+ REG-01 fail-fast + CEP P2

Status: completed
Targets: `nop-stream-cep/.../operator/CepOperator.java`、`nop-stream-runtime/.../execution/SupervisionLoop.java`（REG-01 组合检测）、`nop-stream-cep/.../nfa/compiler/NFACompiler.java`

- Item Types: `Fix`

- [x] CEP-01：PT 模式 bucket 排水不再依赖事件时间 ledger——PT 分支同步写 ledger 并在 `onProcessingTime` 做 ledger 清理（或 PT 模式回退 `elementQueueState.keys()` 桶迭代，二选一以 live 代码结构定，选择记录于 log）。测试：PT+comparator 模式，两轮 timer 后全部缓冲事件被排水匹配（修复前第二轮后丢失——先跑一次记录）。（选型方案 (a)：`registerTimer` PT 分支镜像写台账 + `onProcessingTime` STEP-6 清理；选型理由与偏离见执行记录）
- [x] REG-01：region 重启组合"内部边无物化 × consumer 状态已回滚（有 checkpoint）× producer 已 COMPLETED（F1 跳过）"为不可恢复的数据丢失窗口——在 `restartRegion` 的 F1 skip 处检测该组合，响亮失败（job fail，错误码 + 完整上下文：边、producer/consumer 子任务、checkpoint epoch）。测试：组合场景下恢复显式失败并带可定位错误。（新错误码 `ERR_STREAM_RESTART_UNREPLAYABLE_INTERNAL_EDGE`；检测点提升到重建循环之前，理由见执行记录）
- [x] CEP-02：NFACompiler 对 greedy+until+optional 组合不再编译出 null-target PROCEED 边——编译期拒绝（MalformedPatternException）或修正图语义（以最小正确修复为准，选择记录于 log）。测试：该组合不再在运行期 NPE。（选型修正图语义：读取 miss 回退未改写的 proceedState；理由见执行记录）
- [x] CEP-08：`times(0,0)`/负参数抛 `MalformedPatternException`（替代裸 IllegalArgumentException）。（times 家族入口校验上移；times(0,0) 的 OPTIONAL 副作用移到校验后）

Exit Criteria:

- [x] CEP-01/02/08、REG-01 各有聚焦测试且通过；CEP-01 测试在修复前能复现丢失（记录）。（修复前复现 `_tmp/r5-p5-prefix-repro.log`：CEP-01 第二轮 `got: [a1->end]` 丢失、窗口轮同型；CEP-02 两变体 NPE / ERR_CEP_NFA_FILTER_EXECUTION_FAILED）
- [x] `nop-stream-cep`、`nop-stream-runtime` 测试全绿。（`_tmp/r5-p5-tests.log` EXIT=0：core 1630 + flow 118 + cep 380 + runtime 1124 = 3252 tests，0 failures 0 errors）
- [x] owner doc：`ai-dev/design/nop-stream/cep-design.md` 的 PT 排水语义段（若有相关表述）与修复一致；REG-01 失败语义并入 failover-design（与 Phase 3 合并更新）。（cep-design 新增 §10"处理时间模式的 bucket 排水与 timer 台账"；failover-design §2.3 恢复健壮性契约新增 REG-01 条目 + §五.4 Writer 保留条目补 F1 失败语义边界）
- [x] `ai-dev/logs/2026/09-30.md` 已更新。

> 执行记录（2026-09-30，Phase 5）：**CEP-01 选型 (a)（PT 分支同步写 ledger）**——live code 中 `drainDueBuckets(dueOnly=false)` 的候选集完全来自台账，方案 (b)（PT 回退桶扫描）会放弃 plan 2279 的"免全桶扫描"收益并使 ET/PT 双候选源分叉；实现为 `registerTimer` PT 分支在注册墙钟 timer 的同时 `registerEventTimeTimerForKey` 镜像写台账（桶注册与窗口超时注册同路径覆盖），`onProcessingTime` 新增 STEP-6 台账清理（`removeIf(timer <= fireTime)`，与 `onEventTime` 的 ET 清理同构；未来的窗口台账项存活至自身触发轮）。台账字段 javadoc 同步声明 ET/PT 双模式语义。**REG-01 检测点偏离**：计划写"F1 skip 处检测"，实现将守卫 `failFastOnUnreplayableInternalEdge` 提升到 restartRegion 的 Phase 2（cancel/wait）之后、Phase 3 重建循环之前——检测条件即 F1 组合（非 COMPLETED 须重建任务 × `matPoint == null` 且 `partition.isFinished()` 的输入通道 × 存在已完成 checkpoint；finished ⇒ producer 走 EOS-COMPLETED 路径而失败/取消路径不 seal 分区，故无误报面），提升位置保证触发时 region 内无半重建/半重提交任务。检测从 consumer 侧扫描（`ResultPartition` 无 owner 元数据、`RecordWriter.getPartitions()` 为 package-private、plan Non-Goals 禁止第三类 core 公开 API 面扩展），producer 归因经 JobGraph 拓扑（in-region 入边源顶点 + 其 COMPLETED subtaskIndex 列表）写入错误 detail；新错误码 `ERR_STREAM_RESTART_UNREPLAYABLE_INTERNAL_EDGE`（`nop.err.stream.restart-unreplayable-internal-edge`，携带 regionId / consumer vertexId / taskIndex / checkpointId / detail）。无 checkpoint 的同拓扑重启行为保持（`TestRegionRestartInternalEdgeE2E` 3 用例零回归；`TestSupervisionLoopConsistentCut` 3 用例证明物化边不受影响）。**CEP-02 选型：修正图语义**（`createSingletonState` 读取 miss 回退未改写的 `proceedState`）而非编译期拒绝——`times(0,1).greedy().until` 是合法 API 形状，拒绝属行为回归；`from == to` 时 `createTimesState` 从未对 proceedState 施加 greedy 条件改写，回退目标即精确语义；变体 (b)（`copyWithoutTransitiveNots` 改名副本导致键不匹配）下回退目标与普通 optional PROCEED 边同目标，语义与非 greedy optional 一致（代价仅为审计所述"丢失 until 命中时绕过 transitive NOT 的副本优化"）。两个审计变体均有运行期测试钉死。**CEP-08**：`times(int)`/`times(from,to,...)`/`timesOrMore(int)` 数值域校验上移到 Pattern 入口并抛 `MalformedPatternException(ERR_CEP_MALFORMED_PATTERN)`（英文消息、ARG_PATTERN_DETAIL）；`times(0,0)` 的 OPTIONAL 副作用移到全部校验通过之后（拒绝路径不再污染 quantifier；`Quantifier.Times` 构造器 Guard 保留为内部防线）。测试面：新增 `TestCepOperatorProcessingTimeBucketDrain`（2 用例：三轮 timer 排水 + 窗口超时轮后续轮排水/台账有界）、`TestGreedyUntilOptionalNullTarget`（2 用例：审计变体 a/b）、`TestRegionRestartUnreplayableInternalEdge`（1 用例：错误码 + producer/consumer/epoch 全上下文断言）；`TestPatternValidation` 4 处 IAE 断言改 MPE 并新增 `times(0,0)`/`times(0)`/拒绝不污染 quantifier 3 用例。过程偏差：CEP-01 测试两轮数据修正（end 事件名须满足 pattern 条件；name-comparator 下 start 事件须排序在 end 之前）与 PTS mock 改一次性触发（fireDue 不重放已触发 timer）——均属测试基建修正，非实现偏差。发现但未修（登记）：REG-01 守卫对"consumer 已消费完 residual 但尚未观察到 EOS 即失败"的窄场景会保守失败（此时窗口实际无缺失）——响亮失败方向符合本 plan 准绳，不做豁免；"producer FAILED（分区未 seal）+ 有 checkpoint"的负向矩阵无专门钉子（守卫条件 `partition.isFinished()` 结构性排除，producer 重建重发语义由 plan 366 四案例矩阵覆盖）。`node ai-dev/tools/check-doc-links.mjs --strict` EXIT=0。

### Phase 6 - 文档面 P1/P2（DC-01..13）

Status: completed
Targets: `nop-stream/README.md`、`docs-for-ai/03-modules/nop-stream-user-guide.md`、`ai-dev/design/nop-stream/cep-design.md`、`ai-dev/design/nop-stream/01-architecture-baseline.md`、`ai-dev/design/nop-stream/state-management-design.md`、`ai-dev/design/nop-stream/README.md`、`docs-for-ai/INDEX.md`、`docs-for-ai/02-core-guides/error-handling.md`

- Item Types: `Fix`

- [x] DC-01/02：README 与 user-guide 快速开始示例改为默认配置下可运行（对齐 quickstart Topology1 的显式降档写法或声明 guarantee），修正后以 live classes 实际运行示例代码验证不再抛 `nop.err.stream.invalid-state`。
- [x] DC-03：cep-design.md「方式二」示例改用真实 API（`compileFactory(...).createNFA()` + `advanceTime(accessor, nfaState, ts, skipStrategy)`）。
- [x] DC-04：README 模块表补齐 10/10（rocksdb、connector-jdbc、connector-batch、connector-debezium）；01-architecture §二模块树同步。
- [x] DC-05：README:35 XDSL「规划中」与模块表「活跃」矛盾消除。
- [x] DC-06：docs-for-ai/INDEX.md nop-stream 清单补齐并修正 flow 描述。
- [x] DC-07：error-handling.md 与 AGENTS.md 对齐（NopStreamErrors 全英文为合规现状，移除"默认中文"误导或登记例外口径）。
- [x] DC-08/09：state-management-design「纯内存 HashMap」与 01-architecture「composite fencing token」残留表述归真。
- [x] DC-10..13：过期行号锚点、xdef 头注释「五层」、design/README 索引漏 failover-design、裸文件名引用。

Exit Criteria:

- [x] README/user-guide 示例经实际运行验证通过（运行证据记录于 log）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] 审计报告 DC-01..13 指出的失实表述逐条复核不存在。
- [x] `ai-dev/logs/2026/09-30.md` 已更新。

> 执行记录（2026-09-30，Phase 6 文档批次）：DC-01/02 两处快速示例对齐 quickstart Topology1 显式降档写法（`env.getCheckpointConfig().setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE)` + 门控注释；user-guide 另保持 Phase 4 的 jdbc 台账内容不动），并对 `nop-stream-core/nop-stream-runtime` target/classes 实际编译运行探针验证：修正前示例复现 `nop.err.stream.invalid-state`（REPLAYABLE/TWO_PHASE_COMMIT 双门槛报错），修正后 README 探针输出 `6/8/10` 且 `DC01_PROBE_OK`、user-guide 探针（含 `enableCheckpointing`，runtime SPI 工厂接线）完成 checkpoint 0 且 `DC02_PROBE_OK`；探针临时文件已删除。DC-03 示例按 live API 重写（`NFACompiler#compileFactory(...).createNFA()` + `nfa.open` + `SharedBuffer#getAccessor`（AutoCloseable）+ `advanceTime` 返回 `Tuple2`（f0=matches/f1=timeouts）+ `process`，签名逐一对照 `NFA.java:193/237/266` 与 `FraudDetectionDemo#consumeEvent`）。DC-10 三处行号锚点改为符号锚点（`CheckpointCoordinator#advanceCheckpointIdCounterAfterRestore`（live :983/:986）、`CepOperator#open()`（live :322）、`AbstractStreamOperator#snapshotState`（live :275））。DC-07 登记双模块族英文口径（nop-ai + nop-stream（`NopStreamErrors` 全英文 `\p{Han}` 零命中），并区分异常消息 vs define 描述两类语言规则）。`node ai-dev/tools/check-doc-links.mjs --strict` EXIT=0（仅存 3 个 warning 位于 `ai-dev/plans/nop-bytecode/06-resource-leak-v1.md`，非本轮范围、本轮之前已存在）。daily log 条目由主 agent 按 Phase 汇总回写。

### Phase 7 - P2 缺陷批次（确认缺陷择要，11 项）

Status: planned
Targets: `nop-stream-rocksdb/.../RocksDBSnapshotSerDe.java`（ST-04）、`nop-stream-runtime/.../checkpoint/storage/CheckpointSerDe.java`（ST-07 恢复入口）、`nop-stream-flow`（AR-02）、`nop-stream-runtime/.../cluster/`（AR-03 reshard）、`nop-stream-cep`（CEP-03）、connector 三处（CON-07/08/10）、测试三处（TE-05/09/16）

- Item Types: `Fix`

- [ ] ST-04：RocksDB MapState 快照按 `namespace+"|"+rawKey` 字符串分组——改为无歧义编码（length-prefix 或转义），消除分隔符碰撞合并两 base key 数据。测试：含 "|" 的 key 对不再互串。
- [ ] ST-07：显式 savepoint 恢复三级查找全 miss 时响亮失败（拒绝静默 fresh start；错误信息列出三级查找路径）。测试：显式 savepoint miss → 恢复失败且错误含路径。
- [ ] AR-02：`<custom><source>` xpl body 声明但模型解析后静默蒸发——解析期 fail-fast（声明未消费即报错）或实现消费（以最小修复为准）。测试：带 custom source body 的模型解析报错/或行为生效。
- [ ] AR-03：reshard 缩容丢弃被裁子任务 operator state 且"守恒校验"结构上不可能失败——缩容遇 operator state 时 fail-fast（或守恒校验改为可失败的真实断言）。测试：缩容 + operator state → 显式失败。
- [ ] CEP-03：`numLateRecordsDropped` 从 JVM 级共享 registry 改为算子/子任务作用域注册。测试：两个并行实例指标互不串数。
- [ ] CON-07：FileSourceReader 单行聚合缓冲设上界，超限响亮失败（防无换行大文件 OOM）。测试：超长行 fail-fast。
- [ ] CON-08：JdbcCheckpointStorage 唯一键冲突判定不再靠异常消息嗅探（改用 SQLState/厂商码或先查后写等可靠判定）。测试：真冲突与真错误路径分流正确。
- [ ] CON-10：FileSourceReader.openSplit 校验 split 路径归属源目录（legacy 无 checksum checkpoint 的路径篡改面收口）。测试：越界 split 路径拒绝。
- [ ] TE-05：B1 source-enumerator manifest 接线补"真驱动"测试——断言接线方法**被调用**（spy/stub 录制调用），而非只证方法存在。
- [ ] TE-09：allowedLateness>0 与迟到记录 side output 两条用户可见路径补测试。
- [ ] TE-16：教科书式枚举存在性测试删除或替换为行为测试。

Exit Criteria:

- [ ] 11 项各有聚焦测试且通过（行为变更项先跑修复前复现并记录）。
- [ ] 受影响模块（rocksdb/runtime/flow/cep/connector*）测试全绿。
- [ ] `ai-dev/logs/2026/09-30.md` 已更新。
- [ ] owner doc：若 AR-02/AR-03 涉及 XDSL/reshard 对外语义变化，`docs-for-ai` 或 design 对应段同步；否则写明 No owner-doc update required（行为仅从静默错改为响亮失败）。

### Phase 8 - 治理批次（可读性 + 测试护栏 + 杂项）

Status: planned
Targets: `nop-stream-runtime/.../transport/RemoteInputChannel.java`、`nop-stream-core/.../exceptions/NopStreamErrors.java`、`nop-stream-core/.../state/backend/IStateBackend.java`、`nop-stream/` 父 pom、注释与测试杂项、仓库根 `_tmp-*.log`

- Item Types: `Fix`

- [ ] RD-01：CONTROL_HEARTBEAT 死协议残留清除（RemoteInputChannel 接收分支 :642-648、常量、StreamMessageEnvelope 失实 javadoc :48-55、`TestRemoteInputChannelHeartbeat` 删除/改名）。
- [ ] RD-02：NopStreamErrors 9 个孤儿错误码逐个裁定——能接线的接线（含 ERR_STREAM_INIT_ERROR），确无归属的删除（grep 模式与排除项记录于 log）。
- [ ] RD-03：IStateBackend「RedisStateBackend」、WatermarkStatus「SourceStreamTask/StreamTask/StreamSource」失实 javadoc 修正。
- [ ] RD-04：KafkaStringWireCodec :36 失实注释修正（"zero logic duplication"声明与 fromWire/extractData 逐字复制事实不符；重复本身维持 Deferred）。
- [ ] RD-10：TtlCleanupStrategy lazyEviction 失效旋钮处置（移除或显式 no-op 文档化，选择记录于 log）。
- [ ] REG-03/04：三处"EOS 哨兵留在队列"失实注释与 `rebuildTask` javadoc 归真。
- [ ] REG-05：FileSourceReader.readBuf transient 字段 inline 初始化器移除（潜伏 NPE 防御）。
- [ ] TE-01/TE-14：`size() >= 0` 永真断言改为有效断言（含残留处）。
- [ ] TE-02：N1 单元回归测试补 `@Timeout`。
- [ ] TE-04/06/08：退化构造器测试、getter/setter 往返三处、名实不符测试名——删除/替换/改名。
- [ ] TE-15：nop-stream 父 pom surefire `forkedProcessTimeoutInSeconds` 配置——**按模块实测校准**（forkCount=4 + reuseForks 下按 fork 全生命周期计时，先实测 nop-stream-runtime 单 fork 耗时再定值，避免误杀）。
- [ ] CON-06：BatchConsumerSink close() 日志改为首条记录摘要（截断），不再全文输出。
- [ ] 仓库根 `_tmp-*.log` 残留（14 个，合计约 487MB）清理：审计证据日志移入 `_tmp/`，纯临时日志删除。

Exit Criteria:

- [ ] RD-01 后 `grep -rn "CONTROL_HEARTBEAT" nop-stream/` 仅零或合理残留（逐处复核记录）。
- [ ] RD-02 后 `NopStreamErrors` 无零引用错误码（口径记录于 log）。
- [ ] TE 项测试改动全部通过；surefire 超时配置生效且 nop-stream 全模块测试在配置值内通过（实测校准记录）。
- [ ] `nop-stream` 全模块编译 + 受影响模块测试全绿。
- [ ] `ai-dev/logs/2026/09-30.md` 已更新。

### Phase 9 - Closure Audit 与收口

Status: planned
Targets: 本 plan、`ai-dev/logs/2026/09-30.md`、owner docs

- Item Types: `Proof`

- [ ] 全模块回归：`cd nop-stream && ../mvnw test -pl nop-stream-core,nop-stream-flow,nop-stream-runtime,nop-stream-cep,nop-stream-rocksdb,nop-stream-connector,nop-stream-connector-jdbc,nop-stream-connector-batch,nop-stream-connector-debezium -am`。
- [ ] 独立子 agent closure audit（fresh session）：逐 Phase Exit Criteria + Closure Gates 对照 live repo 验证 + 裁定总表抽查（Deferred 项分类诚实性），evidence 写入本 plan Closure 段。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0。
- [ ] Anti-Hollow：从入口点追踪 AR-01 修复后的路由链（DataStream keyBy → partitioner → TaskExecutor 投递 → keyed state 属主），确认运行时同一公式贯通；无空方法体/静默跳过。
- [ ] owner docs 同步复核：Phase 1-7 各自 owner doc 更新项已完成；`docs-for-ai/04-reference/source-anchors.md` 如有锚点变化则更新。

Exit Criteria:

- [ ] 全模块测试 EXIT=0（日志存 `_tmp/`）。
- [ ] closure audit evidence 已写入下方 Closure 段（含每条 Gate 的 PASS/FAIL）。
- [ ] 两个工具退出码均为 0（输出记录于 log）。
- [ ] `ai-dev/logs/2026/09-30.md` 收口条目已更新。

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（P0×1 + P1 代码×11 + P1 文档×4 + Phase 7 的 11 项 + Phase 8 治理项）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（路由/属主公式、错误码分层、文档契约）
- [ ] 每项修复带聚焦回归测试且修复前可复现（或注明为何不可复现——如需真实多 JVM 时序）
- [ ] 必要 focused verification 已完成（逐 Phase 模块测试 + Phase 9 全模块回归）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（所有延期项已进入"剩余发现裁定总表"并附理由）
- [ ] 受影响的 owner docs 已同步到 live baseline（Phase 1-7 各自 owner doc 项 + Phase 6 文档面）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）路由/属主修复在运行时连通（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile`（nop-stream 全模块）
- [ ] `./mvnw test`（nop-stream 全模块）
- [ ] checkstyle / 代码规范检查通过（import 分组抽查受影响文件）

## 剩余发现裁定总表（不在执行 Phase 内的全部审计发现）

> 口径：审计 R4 轮全部 125 项中，未进入 Phase 1-8 执行项的发现逐条登记于此。分类只允许 `watch-only residual` / `optimization candidate` / `out-of-scope improvement`；confirmed live defect 若未修复，必须给出"为何 supported baseline 仍成立"的明确理由。

| 编号 | 裁定分类 | 理由（为何不阻塞 closure） | Successor |
|------|---------|---------------------------|-----------|
| ST-05 容器类型 ValueState 元素类型丢失 | watch-only residual | 需要类型系统级设计（容器元素类型管道），当前触发面为"容器型 ValueState + 恢复"组合；MapState 主路径已修复（ST-04/ST-02 覆盖主损坏面） | yes：状态序列化专项（backlog） |
| ST-08 `__java_bytes__` marker 判别歧义 | watch-only residual | 触发需用户 Map 值恰好使用该 magic key，窄；后果为响亮恢复失败非静默 | no |
| ST-09 错误风格双轨（17 处 StreamException 无 ErrorCode） | out-of-scope improvement | 风格收敛类；模块内部异常 + 英文字符串是 AGENTS.md 两档策略允许的档位 | no |
| ST-10 RocksDB restore 先清后写半恢复 | watch-only residual | fail-fast 语义正确；半恢复 DB 可由下次 restore 重建（清库在先） | no |
| ST-11 restoreRangeInto 关闭顺序 | watch-only residual | 后端 close 兜底释放 CF handle，无泄漏证据 | no |
| ST-12 TTL 仅 checkpoint 时清理 | optimization candidate | 资源上界专项；当前 checkpoint 周期清理下增长有界（随 checkpoint 频率） | yes：资源上界专项（backlog，与 CON-11 同波次） |
| ST-13 targetKeyGroupRange restore 后不复位 | watch-only residual | 防御性缺失，当前无复用路径触发 | no |
| ST-14 TaskLocation "\|" 分隔符脆弱 | watch-only residual | 触发需 pipelineId 含 "\|"（系统生成的 id 不含）；fallback 占位 Location 互相覆盖影响可观测性非正确性 | no |
| ST-15 accumulator 启发式误判 | watch-only residual | 误判后果为响亮恢复失败；启发式有 LOG 观测面 | no |
| ST-16 retention 双平面界限不一致 | watch-only residual | 保留策略语义差异，不丢数据；统一需保留策略专项 | yes：checkpoint 保留策略专项（backlog） |
| ST-17 方言降级 debug 日志 + deleteAll 非原子 | watch-only residual | 降级仅影响可观测性；deleteAll 中途失败可重试（幂等删除） | no |
| ST-18 增量快照共享 SST 本地路径依赖 | watch-only residual | 最终 fail-fast 无静默；需 checkpoint 存储层设计 | yes：checkpoint 存储专项（backlog） |
| REG-06 plan 366 文本 injectElements 未回写 | watch-only residual | 历史已完成计划按 guide 不回写；事实以本 plan 与 daily log 记录为准（执行时在 daily log 登记勘误） | no |
| RD-05 JobCoordinator 2355 行拆分（四条拆分线） | out-of-scope improvement | 纯结构治理，行为保持验证成本高（2355 行并发核心）；本轮多项行为修复落在同文件，同轮拆分会污染修复 diff 可审性 | yes：可读性波次（backlog） |
| RD-06 Embedded/Rpc executor ~60 行三段重复 | out-of-scope improvement | 行为保持重构，无缺陷 | yes：可读性波次（backlog） |
| RD-07 窗口函数 ProcessWindowContextAdapter 30 行重复 | out-of-scope improvement | 行为保持重构（Flink 血统，抽包级共享类），可作下轮可读性波次首选 | yes：可读性波次（backlog） |
| RD-08 fraud-example 双 Aggregate ~50 行重复 | out-of-scope improvement | 示例代码重复，非产品面缺陷 | yes：可读性波次（backlog） |
| TE-07 toString/hashCode/equals 镜像测试家族（16 处） | out-of-scope improvement | 测试价值低但删除属大面积测试面改动，与行为修复混同会稀释 diff；随可读性波次批量处置 | yes：可读性波次（backlog） |
| AR-08 重复 from→to 边不校验、findEdge 取首条 | watch-only residual | 触发需用户声明重复边（当前模型构造面不产生）；partition 声明顺序依赖失效的后果是配置被忽略而非数据错误 | yes：flow 波次（backlog，与 AR-06/AR-09 同批） |
| CC-06 monitor 内 N×阻塞 RPC fan-out | optimization candidate | 结构性重构（monitor 与 fan-out 线程分离），行为等价验证成本高；当前规模下停顿窗口可接受 | yes：checkpoint 编排并发重构（backlog） |
| CC-07 deployTask 失败后 liveness 永久豁免 | watch-only residual | 审计确认的 P2 live defect，**不否认其真实性**：触发需"部署传输失败 + 物化拓扑"窄组合，且 CC-01/CC-03 修复收敛了 wedge 的两个更大入口；部署宽限期修复需要部署状态机改动，不宜与本轮行为修复混同 diff | yes：fencing/部署状态机专项（backlog） |
| CC-08 SubtaskTask 三态竞态（P3） | watch-only residual | 后果是多余 region restart（可自愈），非数据错误 | no |
| CC-09 abortBarrierAlignment check-then-act 缝 | watch-only residual | 30s 超时兜底 | no |
| CC-10 commitExecutor 单线程无界队列 | watch-only residual | 慢提交积压有 checkpoint 超时上限兜底 | yes：资源上界专项（backlog，与 ST-12/CON-11 同波次） |
| CC-12 rewireCheckpointPipeline 非原子 | watch-only residual | 单 checkpoint 超时自愈 | no |
| CC-13 RemoteInputChannel sentinel 入队失败无人唤醒 | watch-only residual | 生产路径有界 poll 免疫 | no |
| CC-14 双并发 close TOCTOU 重复 EOS | watch-only residual | 下游幂等无害 | no |
| CC-15 JdbcLeaderElector epoch=1 硬编码 | watch-only residual | 触发需租约行丢失（运维事故域），叠加条件才成回卷；HA 语义改动需独立设计 | yes：HA 专项（backlog） |
| CC-16 leaderEpoch=0 哨兵派生 | watch-only residual | 审计低信心 + Jdbc elector 不触发 | no |
| CC-17 taskExecutor 无界队列 + 终端 RPC 同步发送 | watch-only residual | 与 CC-06 同族结构性优化 | yes：checkpoint 编排并发重构（backlog） |
| CEP-04 cache 统计 timer 关闭竞态 | watch-only residual | 后果为 close 后周期空转至进程退出，无正确性影响 | no |
| CEP-05 copyForSubtask 共享 nfaFactory Rich 条件竞争 | watch-only residual | 需 factory-per-subtask 语义设计；触发需 Rich 条件 + 并行子任务组合 | yes：CEP 专项（backlog） |
| CEP-06 NFAState toString().split 比较器 | watch-only residual | P3 辅助比较器双标准；主比较器正确 | yes：CEP 专项（backlog） |
| CEP-07 Lockable.equals 计入引用计数 | watch-only residual | 当前无触发路径 | no |
| TE-03 CEP qualifier 家族语义断言增强 | out-of-scope improvement | 测试增强需 CEP 语义矩阵上下文；现有有效测试 ≈90% | yes：CEP 测试深化（backlog） |
| TE-10 NFA 超时×skip 交叉测试 | out-of-scope improvement | 同上（CEP 测试深化） | yes：同上 |
| TE-11 attachPendingReplay 并发安全测试 | watch-only residual | Phase 3 CC-02 的确定性交错测试已覆盖核心交错；全并发压力测试属基建建设 | no（部分已由 Phase 3 覆盖） |
| TE-12 coordinator RPC stub 录制语义漂移 | watch-only residual | stub 与真实实现的语义差异面已锚定；修复需 stub 架构收敛 | no |
| TE-13 TestAwait 五模块副本 | out-of-scope improvement | 既往波次 Deferred 裁定维持（本轮实测零漂移） | yes：跨模块脚手架收敛（既往登记） |
| CON-09 GENERIC 方言 LIMIT 不可移植 | watch-only residual | 响亮失败方向安全（Oracle/SQLServer/达梦恢复显式报错） | no |
| CON-11 JDBC 2PC 整 epoch 驻留内存无上限 | optimization candidate | 资源上界专项 | yes：资源上界专项（backlog） |
| RD-09 任务身份 DTO 三胞胎样板 | out-of-scope improvement | plan 2278 已文档化取舍；CON-01 身份管道若落地可顺带评估收敛 | no |
| RD-11 taskIndex/subtaskIndex 双命名 | watch-only residual | 跨 core/registry 公开面改名属 API 漂移风险，收益纯一致性 | no |
| RD-12 import 分组系统性偏离（274 文件） | out-of-scope improvement | 需全仓口径裁定（AGENTS.md 与 MA4.2-14 裁定张力），单独立项 | yes：全仓风格裁定（backlog） |
| RD-13 Stage-NN 考古注释家族（main 273 处） | out-of-scope improvement | 全量清理是大面积注释改动，污染本轮行为修复 diff；本轮仅归真同面失实注释（REG-03/04） | yes：可读性波次（backlog） |
| AR-06 xpl watermark onPeriodicEmit 恒空 | out-of-scope improvement | XDSL 声明面小缺陷，随 flow 波次处理 | yes：flow 波次（backlog） |
| AR-09 checkpoint interval=0 静默禁用 | out-of-scope improvement | 同上（与 F-04b 0-语义双标一并裁定） | yes：flow 波次（backlog） |
| AR-11 HashPartitionRouter 整记录哈希回退分支 | watch-only residual | 当前不可达（防御性分支） | no |
| PF-01..11 性能候选 + 基准缺口 | optimization candidate | 沿袭 plan 360/2279/366 收敛裁定——无 ≥2% 低风险可收割项；缺口登记 follow-up | no（follow-up 登记） |

## Deferred But Adjudicated

> 本 plan 无未裁定的延期项——全部延期裁定见上表。表内 `watch-only residual` / `optimization candidate` / `out-of-scope improvement` 均附理由与 successor 归属；successor=yes 的条目归属 `ai-dev/backlog/` 登记（Phase 9 收口时统一写入）。

## Non-Blocking Follow-ups

- 基准缺口补口径（TaskDispatchLoop 中间层 / 嵌入态 checkpoint / Memory shard 路由 + namespace 交替档）——性能波次接手。
- 裁定总表中 successor=yes 的 backlog 登记（Phase 9 落盘 `ai-dev/backlog/`）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<待 closure audit 填写>>
- Evidence: <<待填写>>

Follow-up:

- 见 Non-Blocking Follow-ups 与裁定总表
