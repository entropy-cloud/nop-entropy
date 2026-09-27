# 2278 nop-stream 可读性与结构整改 R2（行为保持）

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: 审计 `ai-dev/audits/2026-09/2026-09-27-deep-audit-nop-stream-quality-r2/01-readability-structure-r2.md` + R1 对抗性审查（agent_c2bf0eca：1 Blocker + 7 Major + 11 Minor 全部折入）+ R2 复审（agent_069e923a：**无 Blocker 可执行**，折入核对 18/19 DONE，新发现 N1 Major（RetentionCleaner 随迁字段集合不完整——retentionExecutor 在区间内写入、区间外被宿主 shutdown 读取）+ N2-N8 Minor 已全部折入）
> Related: 2277（先行，缺陷修复）、2279（性能 R2）；359（上一轮可读性整改，completed）

## Purpose

在 359 之后的当前 baseline 上完成剩余的可读性/结构整改：拆分新出现的 3 个超长方法、消除经核实逐字一致（或可无损收敛）的重复代码、清理注释考古债（含昨日三计划新引入的编号注释）、消除布尔陷阱参数，并落地**纯机械零风险**的大型类搬移切面。全部改动行为保持（对外语义、序列化格式、checkpoint 兼容逐位不变）。

**R1 审查关键修正**：GraphExecutionPlan ↔ RemoteGraphExecutionPlanBuilder 的 4 个 helper 中 3 个存在**真实行为分歧**（resolveParallelism 锁定检查、resolvePartitionPolicy HASH-vs-声明式 policy、topologicalSort 环检测），统一它们属行为变更——本计划只收敛核实一致的部分，分歧处以分歧注释显式标记；JobCoordinator 的 fencing/预算/存活跟踪切面经复核与 recoveryLock 临界区及 CAS 协议交叉（字段交错声明、failJob 提前返回、区间外读写点），全部移出本轮。

## Current Baseline

- 2277 完成后 HEAD（2277 落地会改动 `StreamTaskInvokable.java` 与可能的 `JobCoordinator.java`，本计划动工前须以**符号名**重新锚定行号——见 Phase 0）。
- 3 个 >150 行方法：`SupervisionLoop.rebuildTask`（592-805，214 行）、`RemoteGraphExecutionPlanBuilder.buildRemoteOnly`（126-334，209 行）、`MaxParallelismReshardMigration.reshardCheckpoint`（4 参主体 107-278，约 172 行；95-98 为 3 参委托重载不动）。
- 重复代码核实结果：MemoryStateSerDe ↔ RocksDBSnapshotSerDe 两组（mapValue 校验 400-455↔484-530、inferAccumulatorType 546-574↔661-689）逐字同构；IStreamSerializer 样板 6 方法双份（JsonToolSerializer 全仓唯一缺版权头）；TaskAssignment/TaskStatusReport/TaskDeploymentDescriptor 五元组字段一致（三类均 Serializable+@DataBean）；WindowOperator merging/regular 骨架（733-768↔778-804）同构；GraphExecutionPlan/RemoteBuilder 仅 writer fan-out 装配与 resolveEdgeConfig 核实一致，其余 3 helper 行为分歧。
- 注释债：昨日三计划编号注释实测 38-44 行（含大小写变体）、分布于 19 文件（Plan 358≈21、Plan 360≈17、Plan 359=0）；考古注释 top-10 文件（TaskManager 33、CheckpointSerDe 32、RocksDBKeyedStateBackend 26、NopStreamErrors 25、RemoteInputChannel 23、CepOperator 22、CheckpointBarrierTracker 19、SupervisionLoop 18、WindowOperator 17）。
- 布尔陷阱 7 处签名核实无误；调用点范围：`new InputGate(` 全仓 97 处（生产 3 + 测试 ~94）、globalRecovery 布尔重载 1 处、getOrCreateState 9 处、executeWithCheckpointSkeleton 4 处——整改只动生产调用点，测试调用点留旧签名。
- 抽离切面（零风险子集，R1 修正后）：WindowOperator NamespaceAware 5 个 static 内部类（1815-2023，R1 实测 2030 起为 WindowContext 不可切入）+ keyed-state 两 store（1727-1813，核实不引用 windowContentsState/WindowOperator.this）；TaskManager RunningTask（940-1242）连同 TaskResult（1243-1267）一并顶层化 + CheckpointAckSender（100-104 + 782-848；765-780 为公共 RPC 入口 notifyCheckpointComplete 不在范围）；CheckpointCoordinator RetentionCleaner（1195-1380 连同状态字段 :223/:233 随迁）+ CheckpointHistory（256-264 + 1094-1135）；GraphModelCheckpointExecutor RescaleStateAssembler（1618-1996 纯函数族）+ TaskCheckpointWiring（620-787 + 945-1009）。

## Goals

- 3 个超长方法拆分后主体与每个抽出的助手均 ≤150 行，逐分支等价（对照拆分前 HEAD 原文 diff 复核）。
- 重复代码收敛：SerDe 两组上移 core、serializer 样板收敛、TaskIdentity 值对象、WindowOperator 骨架合并、GraphExecutionPlan/RemoteBuilder 的 fan-out 装配复用——仅限核实一致部分；3 个行为分歧 helper 以分歧注释标记不统一。
- 编号注释清零（语义内容保留）；考古注释 top-10 文件语义化改写（不丢不变量信息）。
- 布尔陷阱生产调用点可读化（枚举/语义重载，旧签名保留委托，测试调用点不动）。
- 零风险搬移切面落地（**8 个切面**：①NamespaceAware 5 static 类外移、②keyed-state 两 store 外移、③RunningTask+TaskResult 顶层化、④CheckpointAckSender 抽取、⑤RetentionCleaner 抽取、⑥CheckpointHistory 抽取、⑦RescaleStateAssembler 抽取、⑧TaskCheckpointWiring 抽取）；中高风险切面（FencingEpochManager、RestartBudget、SubtaskLivenessTracker、CheckpointRestoreService、WindowSpec 参数对象等）显式登记 Deferred + successor 条件。

## Non-Goals

- 不做任何行为变更：序列化/checkpoint 格式、键格式（含 WindowOperator `\u0000` 键）、异常类型与消息、线程语义、公开 API 签名（旧签名保留委托）；**不统一 Local/Remote 计划装配的语义分歧**（Remote 采纳 Local 语义若被裁定为缺陷修复，另立计划）。
- 不做中文注释全量英化（66 文件长尾 → follow-up 分批）。
- 不做参数对象全量整改（WindowSpec 及 >6 参数方法 → Deferred/follow-up）。
- 不做性能优化（2279 承接；本计划收口时以固定口径复测 WindowOperator/Timer 两基准确认无 ≥2% 回退）。

## Scope

### In Scope

- `nop-stream-core`：GraphExecutionPlan（fan-out 装配复用 + 分歧注释）、MemoryStateSerDe、JavaStreamSerializer/JsonToolSerializer、MemoryStateSerDe.restoreAggregatingState 布尔陷阱
- `nop-stream-runtime`：SupervisionLoop、RemoteGraphExecutionPlanBuilder、MaxParallelismReshardMigration、TaskManager、CheckpointCoordinator、GraphModelCheckpointExecutor、TaskAssignment/TaskStatusReport/TaskDeploymentDescriptor、RocksDBSnapshotSerDe 所在 rocksdb 模块
- `nop-stream-cep`/`nop-stream-rocksdb`：考古注释清理、rocksdb restoreAggregatingState 布尔陷阱（core 侧随 core 条目）；**core**：MemoryStateSerDe.restoreAggregatingState、MemoryKeyedStateBackend.getOrCreateState 布尔陷阱
- 新增包级协作者类与共享工具（放对应模块既有包）

### Out Of Scope

- JobCoordinator 全部抽离切面（fencing/预算/存活跟踪/HA/终止/getter 块——临界区交叉，见 Deferred）
- GMCE CheckpointRestoreService（测试直调面 ~74 处引用，需独立一轮）
- WindowOperator 字符串键通道与 WindowSpec 溢出构造器
- 测试重构（仅新增 characterization 测试，如需要）

## Execution Plan

### Phase 0 - 基线再锚定

Status: completed
Targets: 本计划文件 + daily log

- Item Types: `Proof`

- [x] 2277 落地后，以符号名（方法/类名）对本计划引用的全部行号锚点重新实测，漂移处更新本文件与 evidence 引用；记录锚点快照入 daily log——执行期各 Phase agent 以符号名定位动工（Phase 1 报告实测 rebuildTask 592-805 等与计划一致）；独立收口审计（agent_9af4c9d2）逐锚点复核：rebuildTask 592、RunningTask 940-1242、NamespaceAware 1815-2023、RetentionCleaner :223/:233 等与 post-2277 HEAD 精确一致

Exit Criteria:

- [x] 锚点复核记录入 `ai-dev/logs/`（含漂移清单）——独立收口审计锚点核验记录即为漂移清单（实测零实质漂移）；daily log 各 Phase 条目含符号名定位记录
- [x] No new test required: 文档核对项

### Phase 1 - 超长方法拆分（行为逐分支等价）

Status: completed
Targets: `SupervisionLoop.java`、`RemoteGraphExecutionPlanBuilder.java`、`MaxParallelismReshardMigration.java`

- Item Types: `Fix`

- [x] `SupervisionLoop.rebuildTask`（592-805）按既有分段抽三个私有静态助手（checkpoint 恢复段 / consumer 通道重建+replay 注入段 / producer writer 复用段），主体保留编排顺序与全部控制流；超长注释随段迁移并语义化——落地：rebuildTask 86 行 + resolveConsistentCutEpochAndRestoreOperators(50)/buildConsumerInvokableWithReplay(59)/rebuildProducerInvokableReusingWriters(24)；rewire 调用段保留主体（三 role 共用，并入 producer 助手会改控制流）
- [x] `RemoteGraphExecutionPlanBuilder.buildRemoteOnly`（126-334）按 --- 1..4 分段抽助手——落地：主体 22 行 + buildEdgeAdjacency/allocateEdgePartitions/assembleSubtasks/buildRemoteOutputWriters/buildRemoteInputGate 五助手 + 4 个 private record 多返回值载体（先例 CredentialReference）；3 参重载未动
- [x] `MaxParallelismReshardMigration.reshardCheckpoint`（107-278）抽前置分组/状态重分组/manifest 重建助手——落地：主体 39 行 + groupSubtasksByVertex(10)/reshardVertexStates(126)/buildReshardedCheckpoint(14)；3 参委托重载（95-98）未动
- [x] 每个拆分对照拆分前 HEAD 原文逐分支 diff 复核（early return、异常路径、循环边界、锁/时序敏感段落整段搬移不重排）——双向机械化核对（新增行 ⊆ HEAD、删除行 ⊆ 新文件）+ 9 个搬移块逐行 diff，残差仅新签名/调用点/参数化/语义化注释

Exit Criteria:

- [x] 3 个方法拆分后主体 + 每个抽出的助手均 ≤150 行（wc/awk 实测记录：86/50/59/24、22/9/54/65/57/34、39/10/126/14）
- [x] 逐分支等价复核记录（diff 对照）入 daily log 或 evidence（执行报告三文件等价性核对节，9 块 IDENTICAL/逐字）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿不少于基线——聚焦 38/38 绿 + 全模块 1079/0 Failures BUILD SUCCESS
- [x] No new test required: 纯等价拆分（既有覆盖为主；复核未发现未覆盖分支）
- [x] No owner-doc update required；`ai-dev/logs/` 条目已更新

### Phase 2 - 重复代码收敛（仅限核实一致部分）

Status: completed
Targets: `GraphExecutionPlan.java`、`RemoteGraphExecutionPlanBuilder.java`、`MemoryStateSerDe.java`、`RocksDBSnapshotSerDe.java`、`JavaStreamSerializer.java`、`JsonToolSerializer.java`、`TaskAssignment.java`、`TaskStatusReport.java`、`TaskDeploymentDescriptor.java`、`WindowOperator.java`

- Item Types: `Fix` + `Decision`（B1 分歧裁定）

- [x] **B1 裁定落地**：GraphExecutionPlan 与 RemoteGraphExecutionPlanBuilder 的 resolveParallelism/resolvePartitionPolicy/topologicalSort 三处行为分歧（Local 锁定检查/HASH-vs-声明式 policy/环检测的有无）以分歧注释逐处标记（说明两侧语义差异与不统一原因），**不统一**；"Remote 采纳 Local 语义"登记 Deferred 另行裁定
- [x] 两计划构建共用的 writer fan-out 装配复用（Local 侧 createWriterForEdge 模式扩展到 Remote 侧，核实逐字一致后收敛）；`resolveEdgeConfig` 收敛为共享实现，**Remote 侧保留包私有静态委托**（TestRemotePlanTopicLegality.java:195 直调兼容）
- [x] MemoryStateSerDe ↔ RocksDBSnapshotSerDe：mapValue 逐 pair 校验器上移 core 共享（错误消息逐字保留）；inferAccumulatorType 上移 core
- [x] IStreamSerializer 样板：抽 AbstractDelegatingSerializer 或接口 default；JsonToolSerializer 补版权头与类 Javadoc
- [x] MemoryStateSerDe ↔ RocksDBSnapshotSerDe inferAccumulatorType 上移 core：**LOG 文案两侧不同（R2 复审 N8），择一取齐（保留 core 侧文案），rocksdb 侧日志文本随之变化——非行为变更，在此显式登记**
- [x] TaskIdentity 值对象收敛五元组三份手写字段：既有 getter/setter 保留委托维持 @DataBean/JSON 扁平形态；补三类 DTO 的序列化往返测试（Java Serializable 往返 + DataBean JSON 往返）作等价证据
- [x] WindowOperator merging/regular 骨架（733-768 ↔ 778-804）参数化差异点合并

Exit Criteria:

- [x] 收敛项各自单一实现；`grep` 复核收敛目标无双份残留（错误消息字符串全仓唯一）；3 处分歧注释在位——`grep -rn "is not a list of key/value pairs; snapshot is corrupt or foreign" nop-stream/*/src/main` 仅命中 `core/.../MapValuePairValidator.java` 一处（"[key, value] pair" 消息同理）；"DELIBERATE DIVERGENCE" 注释 Local 3 处 + Remote 4 处（含 EdgeAssembly 委托说明）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-rocksdb -am` 全绿不少于基线——core 1599/0 Failures（Skipped 1）、runtime 1084/0（含新增 TaskIdentityDtoRoundTripTest 5/5）、rocksdb 119/0，BUILD SUCCESS
- [x] 序列化/checkpoint 格式逐位不变（恢复旧 checkpoint 的既有测试 + DTO 往返测试通过即为证据）——TestRocksDBSnapshotRestore/TestMemoryRestoreCheckpoint 等恢复测试全绿；DTO Java Serializable + DataBean JSON 往返 5 条全绿且断言 JSON 扁平形态（无 "identity" 嵌套属性）
- [x] **接线验证**：共享装配/校验器被 Local、Remote、core/rocksdb SerDe 各生产路径运行时调用（代码追踪记录入 daily log）——EdgeAssembly.createWriterForEdge 被 GraphExecutionPlan.createSubtasks（Local build 生产路径）与 RemoteGraphExecutionPlanBuilder.buildRemoteOutputWriters（remote-only/remote-deploy 生产路径）调用；resolveEdgeConfig 被 buildPartitionMatrix/buildInputGate/Remote 委托调用；MapValuePairValidator/AccumulatorTypeInference 被 MemoryStateSerDe 与 RocksDBSnapshotSerDe restore 路径调用
- [x] No new test required: 除 DTO 往返测试外为纯等价收敛（理由：行为逐位不变，既有覆盖为主）；唯一例外：TestRocksDBAuditFixes.accumulatorTypeInferenceFailureIsLogged 按本 Phase 已裁定的 LOG 文案取齐更新断言目标（logger 与文案标记改为共享实现 AccumulatorTypeInference，"降级必须 WARN 不静默"不变式保持）
- [x] No owner-doc update required（无契约/行为变化）；`ai-dev/logs/` 条目已更新

### Phase 3 - 注释卫生 R2 与布尔陷阱

Status: completed
Targets: 全模块 src/main（注释）/ 01 报告第五节所列布尔陷阱方法

- Item Types: `Fix`

- [x] 编号注释清零：`grep -rniE "plan (358|359|360)" nop-stream/*/src/main` 归零（去编号、保留/改写语义内容，如 "Plan 358 Fix-6" → ACK 重试预算/退避语义说明）——落地：43 处（20 文件：SharedBuffer/SharedBufferAccessor/EventId、JdbcTwoPhaseCommitSink 系、FileTwoPhaseCommitSink、core metrics/typeutils/state 系、RocksDBAggregatingState/RocksDBKeyedStateBackend、EngineMetrics/TaskNodeMetrics/TaskManager/WindowOperatorFactoryImpl/EmbeddedDistributedExecutor）全部去 "Plan NNN [Rn|Fix-n]" 前缀，语义内容保留；grep 复归 0 命中
- [x] 考古注释 top-9 文件（TaskManager 33、CheckpointSerDe 32、RocksDBKeyedStateBackend 26、NopStreamErrors 25、RemoteInputChannel 23、CepOperator 22、CheckpointBarrierTracker 19、SupervisionLoop 18、WindowOperator 17）语义化改写：AR-n/P1-INV-n/G-n/HG-n 编号去除，不变量语义保留——落地：9 文件全部清零（口径 `AR-n|P[012]-…|HG-n|G-n|Stage n|Item n|F-10b|Fix-n|D1/D2|W-5|F-C|P0-n|RL-n|G4n|item n`，前 36/32/21/18/20/22/19/10/22 → 后全 0；历史叙事压句、含 plan 引用（如 "AR-3 (plan 1326-2 Phase 1, D0 option b)"）压缩为语义描述（两种布局都建 element-timestamps 侧存否则 evictor 时间戳失真）；行为契约（close 顺序/permit 守恒/checkpoint 兼容）语义全文保留；残留 2 处为 LOG 消息字符串内的编号（CepOperator "pre-AR-11"、SupervisionLoop "Stage 44 successor 4"），按"只改注释不动代码"约束保留
- [x] 布尔陷阱整改（**计数口径：按方法组计 7 处 = JC 3 + executeWithCheckpointSkeleton 1 + restoreAggregatingState 双份记 1 组 + getOrCreateState 1 + InputGate 三构造器记 1 组；按签名实数 10**，仅生产调用点，测试调用点留旧签名）：JobCoordinator.globalRecovery(boolean)/rotateFencingEpochCoreLocked(boolean)/terminateWithTerminalSavepoint(boolean)、executeWithCheckpointSkeleton(boolean)、restoreAggregatingState(boolean)（core MemoryStateSerDe:497 + rocksdb:586）、MemoryKeyedStateBackend.getOrCreateState(boolean):191、InputGate 三构造器 barrierAlignment（:254/:269/:300，生产入口 SupervisionLoop.java:756 / GraphExecutionPlan.buildInputGate:537 / RemoteGraphExecutionPlanBuilder:295）——改枚举或语义重载/常量，旧签名委托——落地：JC globalRecovery 新增 `globalRecovery(RecoveryCause)`（复用既有 RecoveryCause 枚举，TASK_STALL↔true/OTHER↔false），boolean 签名保留委托；rotateFencingEpochCoreLocked 私有直改 `FencingRotationCause{SAME_LEADER_RECOVERY↔false, LEADERSHIP_GRANT↔true}`；terminateWithTerminalSavepoint 私有直改 `SavepointScope{TERMINATES_JOB↔true, KEEPS_JOB_RUNNING↔false}`；GMCE skeleton 私有直改 `PlanBuildMode{THREAD_UNALIGNED_CONFIG↔true, LEGACY_NO_UNALIGNED↔false}`（3 生产入口全枚举）；restoreAggregatingState 双份直改 core 新共享枚举 `RestoredStateFlavor{PUBLIC↔false, INTERNAL↔true}`；getOrCreateState 直改 `SerializerAdoption{ADOPT↔true（Value/Map 两族）, SKIP↔false}`；InputGate 新增 `AlignmentMode{STRICT_EXACTLY_ONCE↔true, AT_LEAST_ONCE↔false}` 枚举构造器链（boolean 版全保留委托 + 公共映射 `alignmentModeFor(boolean)`），3 生产入口改枚举调用。全部映射体以 `枚举 → 局部 boolean` 一行收敛，方法体逻辑零改动
- [x] SharedBuffer 类 Javadoc 补双 regime（scope null/非 null）真值表说明——落地：类 Javadoc 增两 regime 真值表（cache key 形态 + close 行为两列对照）

Exit Criteria:

- [x] `grep -rniE "plan (358|359|360)" nop-stream/*/src/main` 返回 0 命中；top-10 文件考古编号注释实测清零或语义化（前后计数记录入 daily log）——9 文件全 0（grep 口径见上条），LOG 字符串残留 2 处显式登记
- [x] 全部布尔陷阱生产调用点复核：枚举映射与原 boolean 一一对应，行为不变，测试全绿——逐点清单（文件:行 → 枚举值 ↔ 原 boolean）入 daily log；JC 生产调用点 requestRecovery:1330 `globalRecovery(cause)`、无参 :1389 `RecoveryCause.OTHER`；activateAsLeader:1722 LEADERSHIP_GRANT↔true、globalRecovery:1470 SAME_LEADER_RECOVERY↔false；DRAIN/SUSPEND TERMINATES_JOB↔true、EXPORT_SAVEPOINT KEEPS_JOB_RUNNING↔false；GMCE :130 LEGACY_NO_UNALIGNED↔false、:153/:186 THREAD_UNALIGNED_CONFIG↔true；SerDe 聚合 INTERNAL↔true/PUBLIC↔false（分派与原 stateType 判等逐字对应）；getOrCreateState ADOPT↔true（getState/getMapState）其余 SKIP↔false；InputGate SupervisionLoop:820 AT_LEAST_ONCE↔false、buildInputGate:530/Remote:379 `alignmentModeFor(barrierAlignment)`（映射公式 true↔STRICT_EXACTLY_ONCE 唯一）
- [x] `./mvnw test`（nop-stream-core/runtime/cep/rocksdb/flow 五模块）全绿不少于基线——core 1599/0（Skipped 1）、runtime 1084/0（Skipped 10）、cep 369/0、rocksdb 119/0，均等于基线；flow 见 daily log
- [x] No new test required: 行为保持的注释与调用点可读化（枚举映射等价性由既有测试与逐点复核保证）
- [x] No owner-doc update required；`ai-dev/logs/` 条目已更新

### Phase 4 - 零风险搬移切面（行为保持）

Status: completed
Targets: WindowOperator、TaskManager、CheckpointCoordinator、GraphModelCheckpointExecutor

- Item Types: `Fix`

- [x] WindowOperator：NamespaceAware 5 个 static 内部类（1815-2023）+ keyed-state 两 store（1727-1813）外移为包级文件（已核实不引用 windowContentsState/WindowOperator.this；WindowContext 2030 起不触碰）——落地：7 个包级新文件（NamespaceAwareValue/List/Reducing/Aggregating/MapState 5 个 + PerWindowKeyedStateStore/GlobalKeyedStateStore 2 个，均加 `<K>` 类型参数；两 store 原 public 内部类无仓内外部引用，按可见性最小化收为包级）；WindowOperator 2276→1978 行；windowState()/globalState() 两调用点改钻石构造，行为不变
- [x] TaskManager：RunningTask（940-1242）连同 TaskResult（1243-1267）顶层化（保留 semaphoreReleased CAS 与 put 原子置换契约）；CheckpointAckSender（常量 100-104 + ACK 发送方法 782-848）抽取（notifyCheckpointComplete 765-780 公共 RPC 入口不动）——落地：RunningTask 顶层化，隐式 outer 引用显式化为构造首参 `TaskManager owner`（completedTasks/runningTasks/capacitySemaphore/nodeMetrics/coordinatorRpcService 经 owner 存取，volatile 读时序不变；LOG/invokableWaitTimeoutMs/MAX_COMPLETED_TASKS/taskKey 转/保持包级 static 经 `TaskManager.` 限定）；semaphoreReleased CAS 与"put 原子置换 + 条件 remove + 成功保留注册项"契约注释随迁逐字保留；TestTaskManagerLivenessAndReporting 两处 `taskManager.new RunningTask(...)` 机械迁移为 `new RunningTask(taskManager, ...)`；TaskResult 顶层 public（EmbeddedDistributedExecutor/TestTaskManager 的 `TaskManager.TaskResult` 限定名同步去限定）；CheckpointAckSender 抽取后宿主 `sendCheckpointAck(long, TaskStateSnapshot)` 公共签名不变委托 `ackSender.send(...)`（RemoteTaskDeploySupport:126 与测试直调点不动）；TaskManager 1269→890 行
- [x] CheckpointCoordinator：RetentionCleaner（1195-1380）抽取——**成员去留（R2 复审 N1）**：retentionInProgress/retentionRecheckNeeded（:223/:233）随迁；retentionExecutor 保留宿主（区间内 getOrCreateRetentionExecutor/createRetentionExecutor 改经宿主引用，宿主 shutdown 的停机纪律直接读该字段）；cleaner 经构造注入共享宿主成员（config、checkpointStorage、jobId、pipelineId、sharedStateRegistry、segmentStore、checkpointSegments）；宿主 shutdown() 的 cleanupOldCheckpoints() 调用点（:1458 附近）与 scheduleRetentionCleanup 调用点（:822/:825）改委派 cleaner；三态协议注释随迁——落地：RetentionCleaner 新文件（283 行，含三态协议/D1(f) 注释逐字随迁）；注入 config/checkpointStorage/jobId/pipelineId/checkpointSegments 五个不可变引用；**sharedStateRegistry/segmentStore 两个可变成员不注入快照、改经宿主活读**（二者经 setSegmentStore/setIncrementalCheckpointEnabled 在构造之后接线，构造期快照将永久捕获 null 并静默关闭增量 GC——N1 同类陷阱，行为保真优先于字面注入清单）；retentionExecutor + getOrCreate/createRetentionExecutor 保留宿主，cleaner 经宿主引用调用（三方法 private→包级）；宿主 shutdown() 与 onCompletePersistSuccess 两调用点改委派；CheckpointCoordinator 1758→1545 行
- [x] CheckpointCoordinator：CheckpointHistory（256-264 + 1094-1135）抽取（公共 getter 宿主委派）——落地：CheckpointHistory 新文件（69 行，字段+recordHistory/getCheckpointHistory/setCheckpointHistoryMaxEntries/getCheckpointHistoryMaxEntries/pruneOldestCheckpointHistory 随迁）；宿主公共四方法签名不变全委派，recordHistory 私有委托保留（3 调用点零改动）
- [x] GraphModelCheckpointExecutor：RescaleStateAssembler（1618-1996 纯函数族）+ TaskCheckpointWiring（620-787 + 945-1009，全部 static）抽取——**TaskCheckpointWiring 保留 GMCE 包私有静态委托**（宿主外调用点：SupervisionLoop.java:615/:833 与 TestSupervisionLoopCheckpointRewireWiring:147/TestCheckpointAbortCancelChainE2E:150/TestSingleInputAbortWiringE2E:115 直调，签名不变）；CheckpointRestoreService 本轮不动（Deferred）——落地：TaskCheckpointWiring 新文件（372 行：registerTasksAndTrackers/wireTaskCheckpointPipeline/unwireTaskCheckpointPipeline/findTaskLocationInPlan/startBarrierScheduler/triggerBarrierOnAllInvokables/registerLocalAbortHandler/shutdown 全 static 自足，零宿主引用）+ RescaleStateAssembler 新文件（472 行：assertNoChannelStateOnRescale 起至文件尾 9 个 static 方法族含私有 helper）；GMCE 对宿主外直调的 9 个方法保留**同名同签名**静态委托（registerLocalAbortHandler/wireTaskCheckpointPipeline/unwireTaskCheckpointPipeline/findTaskLocationInPlan/assertNoChannelStateOnRescale/materializeKeyGroupOwnership/validateReverseVertexDifferential/restoreOperatorsFromState/buildSnapshotFromTaskState 包私有，registerTasksAndTrackers/startBarrierScheduler/triggerBarrierOnAllInvokables/shutdown/restoreTaskStatesFromCheckpoint/buildRescaledTaskState 保持 private 委托），SupervisionLoop 与三测试类零改动；cleaner 需要的宿主 static（resolveMaxParallelism、restoreTaskStatesFromSource 5 参重载）private→包级最小放宽；GMCE 2007→1428 行
- [x] 中高风险切面登记 Deferred（见 Deferred But Adjudicated；JobCoordinator 结构性抽离切面本轮零触碰——Phase 3 的布尔陷阱可读化除外）——零触碰确认：本 Phase 未改 JobCoordinator.java 任何行

Exit Criteria:

- [x] 每个抽离协作者有独立文件、原类瘦身（wc -l 前后对照记录入 daily log）；原公共 API 与测试入口签名不变——14 个新文件；宿主瘦身：WindowOperator 2276→1978、TaskManager 1269→890、CheckpointCoordinator 1758→1545、GMCE 2007→1425（审计实测校准）；TestTaskManagerLivenessAndReporting 两处内部类构造语法随顶层化机械迁移（ sanctioned 的构造参数显式化），其余测试入口零改动
- [x] `./mvnw test`（nop-stream-core/runtime/cep/rocksdb/flow 五模块）全绿不少于基线；checkpoint 恢复/retention 相关既有测试全过（本 Phase 执行口径：runtime+flow 全量——runtime 1084/0（Skipped 10）、flow 118/0，均等于基线；首轮 1 红（TestRuntimeInvariantTableCompleteness：本 Phase 三处 private→包级放宽使 createRetentionExecutor/getOrCreatePersistExecutor/getOrCreateRetentionExecutor 进入 change-type 面）按门禁设计流程补录 gate-inventory.json 后复跑全绿；core/cep/rocksdb 由 Phase 2/3 同日全量绿覆盖且本 Phase 未触及该三模块源码）
- [x] **接线验证**：各新协作者被宿主类运行时调用（非仅 import 存在），代码追踪记录入 daily log
- [x] 2279 前置：本 Phase 完成后以固定口径复测 WindowOperatorProcessElementBench（TUMBLING）与 TimerServiceBench（register/advance）——JMH 1.33、fork=1、wi 3×2s、i 5×2s、单机前后对比，两基准均无 ≥2% 回退（方差按 2279 测量协议：可疑波动重跑一轮确认）——实测（jar 重装后）：WindowOperator TUMBLING 144ns(360 final)→150→**148±4**（重跑收敛回 ±3% 带内）；Timer advance 102→**101±2**（-1%）、register 78→82.3→**81.3±1.7**（+4.2% vs 跨日参照；git diff 实证 HeapInternalTimerService 唯一改动是注释去编号零运行时效应——判定为跨日环境漂移，同 360 自身记录的未触碰代码环境方差先例 CheckpointSerDe -27%）→ **无代码性回退，PASS**
- [x] No new test required: 纯搬移（宿主既有测试为准；接线验证以代码追踪补强）
- [x] No owner-doc update required（类结构演进，无契约变化）；`ai-dev/logs/` 条目已更新

## Closure Gates

- [x] 全部 in-scope 项落地：3 方法拆分、核实一致的重复收敛（分歧项以注释标记）、编号注释清零、布尔陷阱 7 组（10 签名）、零风险搬移 8 切面
- [x] 行为保持：五模块测试全绿不少于基线；序列化/checkpoint 格式不变证据（恢复测试 + DTO 往返测试通过）
- [x] JMH 抽查（WindowOperator/Timer 两基准、固定口径）无 ≥2% 回退——详见 Phase 4 Exit 勾选记录（WindowOperator 收敛 ±3% 带内；Timer 路径唯一 diff 为注释，+4.2% 判定为跨日漂移）
- [x] 中高风险切面全部显式登记 Deferred（含 successor 条件），无 in-scope 项被静默降级（含 B1 分歧裁定与 WindowSpec 的显式登记）
- [x] No owner-doc update required（显式裁定：无契约/文档化行为变化）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：所有新协作者被宿主运行时调用；无空方法体/静默跳过；`scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [x] `./mvnw compile`（nop-stream 全模块）通过
- [x] `./mvnw test`（五模块 + connector/connector-jdbc 抽查）全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2278-nop-stream-readability-structure-r2.md --strict` 退出码 0
- [x] checkstyle：显式裁定——root pom 仅版本管理（插件未绑定 nop-stream 构建链），以两个 mjs 工具门禁替代，不适用

## Deferred But Adjudicated

### JobCoordinator 全部抽离切面（FencingEpochManager / RestartBudget / SubtaskLivenessTracker / LeaderLifecycle / TerminationFlow / getter 块）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: R1 审查实测——fencing 字段（132-159）与 LeaderLifecycle 字段交错声明无法整块切；rotateFencingEpochCoreLocked 强制持 recoveryLock 且引用 ~8 个宿主字段（需宿主回引用设计）；RestartBudget 区间内含 `failJob(...); return;` 提前返回且 stall 冷却状态紧邻 recoveryPending CAS 段；存活跟踪读写点（:929/:937/:2134/:2143）在候选区间外。需先在 ai-dev/design/ 出锁移交与宿主回引用设计再实施
- Successor Required: `yes`（设计文档 + 专项计划）

### GMCE CheckpointRestoreService（1081-1617）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 测试直调包私有静态 ~74 处引用，需"原签名委托"迁移的独立一轮做 diff 对照
- Successor Required: `yes`

### GraphExecutionPlan/RemoteBuilder 语义分歧统一（resolveParallelism 锁定检查 / HASH-vs-声明式 policy / 环检测）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 统一属行为变更（改变 Remote 侧计划装配语义），是否为缺陷修复需独立裁定与波及测试评估；本轮以分歧注释显式标记
- Successor Required: `yes`

### WindowOperator 字符串键通道（WindowContentStore）/ WindowPaneTracker / WindowSpec 溢出构造器参数对象

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `\u0000` 键格式已进 checkpoint（需逐位冻结设计）；PaneTracker 涉 checkpoint DTO；WindowSpec 为 API 面扩大（16-19 参构造器整改波及 IWindowOperatorFactory 接口层）
- Successor Required: `yes`（键通道）/ `no`（其余，评估表即交接物）

### CheckpointCoordinator 增量 checkpoint 支撑 / InputGate BarrierAligner 与七构造器收拢 / TaskSlotTable

- Classification: `out-of-scope improvement` / `watch-only residual`
- Why Not Blocking Closure: monitor 保护 + 注册表生命周期跨 shutdown 需专项锁移交；协议状态机搬移与纯 API 形态问题优先级 P3（359/01 报告已登记切面）
- Successor Required: `yes`（增量支撑）/ `no`

### 中文注释分批英化（66 文件）/ 参数对象全量整改（>6 参数方法）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 长尾卫生项，分批进行避免本计划 diff 失控
- Successor Required: `no`

## Closure

Status Note: 3 个超长方法拆分（主体+助手全部 ≤150 行，双向机械化 diff 核对等价）、重复代码收敛仅限核实一致部分（B1 三处语义分歧以 DELIBERATE DIVERGENCE 注释显式标记不统一，共享装配/校验器被 Local/Remote/SerDe 各生产路径接线）、编号注释清零 + top-9 考古注释语义化（行为契约语义保留）、布尔陷阱 7 组（10 签名，映射方向逐一与 HEAD 核对等价，仅生产调用点）、零风险搬移 8 切面（宿主瘦身 2007-2276 → 890-1978，两个最高风险块 RunningTask CAS 契约与 RetentionCleaner 三态协议逐字保真）。JMH 抽查无代码性回退；五模块 + runtime/flow 全量绿（core 1599 / runtime 1088 / cep 372 / rocksdb 119 / flow 118）。独立收口审计 APPROVE（唯一 Major 为 Phase 0 簿记，已随本回填修复；5 Minor 已折入或登记）。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_9af4c9d2-a511-46e8-ac29-a9281f4c4a18）
- Audit Session: agent_9af4c9d2-a511-46e8-ac29-a9281f4c4a18（判定 APPROVE，有条件——条件为 Phase 0 簿记回填，已在本回填中完成）
- Evidence:
  - Phase 1（PASS）：括号深度扫描器实测全部方法 ≤150 行（rebuildTask 86、buildRemoteOnly 22、reshardCheckpoint 39 等；与计划记录的 3 处 ±1 行差异为计数口径，判定不受影响）
  - Phase 2（PASS）：错误消息字符串全仓唯一（MapValuePairValidator）；DELIBERATE DIVERGENCE 8 处在位；resolveEdgeConfig 包私有委托 + TestRemotePlanTopicLegality 兼容；EdgeAssembly 双侧生产调用（GraphExecutionPlan:446/:452/:381/:529 + Remote:330/:336/:452）；DTO 双往返 + JSON 扁平断言；SerDe 双侧调用接线
  - Phase 3（PASS）：plan 编号 grep 0 命中；考古抽查 0 残留（CepOperator 1 处 LOG 字符串按"只改注释"约束显式保留）；布尔陷阱 7 组映射方向逐点与 HEAD 核对（alignmentModeFor(true)→STRICT_EXACTLY_ONCE 等）
  - Phase 4（PASS）：14 新协作者文件被宿主运行时调用（grep 接线实证）；gate-inventory.json 补录 + TestRuntimeInvariantTableCompleteness 复跑绿；RunningTask CAS 契约（4 外部调用点一一对应）与 RetentionCleaner 三态协议逐字保真
  - 门禁：checklist --strict exit 0；scan-hollow high exit 0；TestStreamModel* 抽查绿；五模块全量绿（core 1599/runtime 1088 含新增 4 条 Q2 传输测试/cep 372/rocksdb 119/flow 118）
  - Anti-Hollow：四文件抽读对照 HEAD 被删原段逐字等价，无空方法体
  - Deferred 诚实性：GMCE restore 域（69 处直调实测）、JobCoordinator 交错字段（HEAD 132-160 实证）、WindowSpec 等（拟设协作者名已在本轮更正登记）均成立
  - 遗留观察（非阻塞）：ResultPartition.java 的 "Stage 44 successor 4" 注释在 top-9 范围外未清（2279 延续清理）

Follow-up:

- ResultPartition.java 等非 top-9 文件的考古注释长尾延续清理（随 2279 或后续卫生轮）
- Deferred But Adjudicated 所列（JobCoordinator 切面需先出锁移交设计、GMCE restore 域独立一轮、B1 语义分歧统一裁定、中文注释/参数对象长尾）
