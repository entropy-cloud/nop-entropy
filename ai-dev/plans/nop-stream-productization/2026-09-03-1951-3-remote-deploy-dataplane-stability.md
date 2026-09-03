# remote-deploy 数据面通道收敛与持续运行稳定性修复（roadmap items 28 + 31）

> Plan Status: completed
> Mission: nop-stream-productization
> Work Item: roadmap items 28 + 31
> Last Reviewed: 2026-09-04
> Source: runtime 审计报告 `ai-dev/analysis/2026-09/2026-09-01-nop-stream-runtime-module-audit.md` §2.2 F-D/W-8（审计内部 Follow-up 编号 item 27 = roadmap item 28）；稳定性演练报告 `ai-dev/analysis/2026-09/2026-09-03-distributed-stability-exercise-report.md`（item 31 证据：6 runId 留档于 `_tmp/mini-stream-cluster/`）
> Related: `2026-09-01-2217-3-composite-scenario-distributed-verification.md`（分布式验证资产 C0—C3）；`2026-09-02-2216-2-stability-performance-exercise.md`（演练装置 `TestStabilityExerciseMultiJvm`）；`2026-09-03-1723-3-checkpoint-manifest-versioning-checksum.md`（manifest 校验和，restore 路径相关）

## Purpose

消除 remote-deploy 数据面的累计流量停摆缺陷族（全对订阅 + 队列满永久阻塞 + 无回压传导 + stall 诱发恢复耗尽 recovery cap 的继发控制面失效），使 item 15 演练报告判定「被 item 28 阻塞」的分布式持续运行稳定性基线成立；同时承接 `JdbcCheckpointStorage.loadRetainedEpochManifests` override（W-8 / 2300-2 遗留 P2）。

本 plan 合并执行 roadmap item 28 与 item 31：item 31 是 item 28 的证据锐化与修复范围扩展（同一代码面），分拆会导致 SubtaskPlanBuilder/RemoteInputChannel 被两个计划先后各改一遍。写回时两 item 分别记 done。

## Current Baseline

（live 核对 2026-09-03，行号为当前代码）

- **全对订阅**：`RemoteGraphExecutionPlanBuilder.buildRemoteOnly`（RemoteGraphExecutionPlanBuilder.java:103-130）为整个 job graph 的每个 (src subtask s, tgt subtask t) 对构建 RemoteResultPartition + RemoteInputChannel；`RemoteInputChannel` 构造器即订阅 topic（RemoteInputChannel.java:156 `messageService.subscribe`）。`buildRemoteOnly` 共三类调用方：① TM 侧 `SubtaskPlanBuilder.buildSubtaskPlan`（SubtaskPlanBuilder.java:104-147，本地重建全图后只挑出 assigned subtask 的 invokable——其余 subtask 的通道对共享 backend 保持订阅、无人读取，**本计划病灶**）；② 单 JVM 执行器 `RpcDistributedExecutor`（RpcDistributedExecutor.java:285）与 `EmbeddedDistributedExecutor`（EmbeddedDistributedExecutor.java:207）——remoteDeployMode=false 时在单 JVM 内运行**全部** subtask，全矩阵通道全部被消费（**收敛不得破坏该语义**）；注意 `RpcDistributedExecutor` 在 remoteDeployMode=true 时 :285 仍无条件构建全对 plan（构造器即订阅）而 installInvokablesAndRun 跳过安装循环（:416-441）——**协调器侧通道订阅后零消费，与病灶①同构**（活测试 `TestRpcDistributedExecutorRemoteDeployE2E` 走此路径；演练拓扑 JobCoordinatorMain 不建数据面 plan，Phase 4 检测不到该实例）。
- **队列满永久阻塞**：`RemoteInputChannel.EnvelopeConsumer.onMessage` 用 `queue.put(element)`（RemoteInputChannel.java:406，默认容量 1024 的 LinkedBlockingQueue，:68）——无人消费的通道填满后阻塞消息服务 dispatch 线程；JDBC 轮询后端下 dispatch 线程被阻塞 = 该 TM 全部数据面停摆（EOS 仅 producer close 后解锁）。
- **演练定量证据**（报告 Phase 3/4，runId `1788380205294-1` / `1788381152134-1` / `1788382070775-1` / `1788383926464-1` / `1788385016277-1` / `1788386243853-1`）：跨 TM 通道累计 ~800—1000 条记录后永久 jam（200 行/s@5s / 20 行/s@40s / 10 ev/s@60s）；停摆签名 = durable epoch 冻结 + 输出冻结 + 进程全活 + `nop_stream_msg_queue` 单调涨（22k/18k/41k）+ TM 日志 `RemoteInputChannel -- Interrupted while enqueueing decoded element`；无回压传导（JDBC 消息表无界缓冲，源全速写完 12000 行而消费侧死亡）。健康短跑 `nop_stream_msg_queue` 亦有 ~34/s 残留增长——报告归因「全对订阅派生垃圾消息」，live 核对修正：该表行由 **send 侧** INSERT（watermark 向全部 target partition 广播、barrier、heartbeat、控制面 RPC 复用同一消息表），订阅收敛**不消除表计数**；订阅收敛消除的是无人消费通道的**投递面**。
- **继发控制面失效**：jam 诱发 per-task liveness stall 检测（JobCoordinator.java:1389-1415）→ `requestRecovery` → globalRecovery 计数达 `maxRestarts=3`（:242）后 cap exceeded fail（:1505-1509），此后真实节点 kill/租约到期永久不可恢复（CHAOS-1 轮 3—8 直接证据：fencing 停在 4，轮 7—8 SIGSTOP 租约到期无轮转）。
- **JDBC retained manifests**：`JdbcCheckpointStorage` 未 override `loadRetainedEpochManifests`（core `ICheckpointStorage.java:62` default 仅返回 latest）——Stage-31 重启恢复（SharedStateRegistry 引用计数重建，消费点 CheckpointCoordinator.java:1550）在 JDBC 后端降级。演练全走 LocalFile 路径未触发（未静默绕过，有核验记录）。
- **transport 后端语义（D2 裁定输入，live 盘点）**：演练 JDBC 后端 `PollingJdbcMessageService` 位于 nop-stream-runtime **测试树**（launch/ harness，非平台层）——消息消费后不删除（每订阅独立 cursor）、`poll()` 对 consumer 异常 catch 后 cursor 照常推进；`LocalMessageService` 无 backlog 重放（订阅前发布的消息不可见——当前安全性来自「构造器在 plan build 期订阅，早于任何 task 运行」的时序）。
- **短时基线不受影响**：C0—C3 gated 13/13 + legacy 7/7 绿（≤~500 记录）；修复不得回退。演练正向证据（须保持）：HA 租约 failover 严格递增、50ms 节流档 checkpoint 持续推进、retained manifests 有界。

## Goals

修复必须覆盖（item 31 明确的三要素 + item 28 两点）：

1. **per-subtask 通道按需订阅**：TM remote-deploy 路径下，deploy 单个 subtask 的 TM 只订阅该 subtask 消费的 input topics（消除无人消费通道的订阅投递；producer 侧向全部 target topic 的发送能力保持；单 JVM 执行器模式全订阅语义不回退）。
2. **队列满语义**：无人消费/下游停滞时 dispatch 线程不被单通道永久阻塞；不得静默丢数据破坏 exactly-once（任何丢弃/失败路径必须可观测且可恢复/可重放）。
3. **stall 恢复预算与真实故障恢复的区分**：jam/stall 诱发的连续自动恢复不得耗尽全局 recovery cap 使真实故障永久不可恢复。
4. **JdbcCheckpointStorage.loadRetainedEpochManifests override**（JDBC 后端 Stage-31 恢复与 LocalFile 对等）。
5. **gated 多 JVM 复验**：演练 jam 场景（>10³ 记录持续流）完整收敛 + 持续流量下 kill 恢复演练通过；短时基线 C0—C3 不回退。

## Non-Goals

- credit-based 精细流控/反压协议/性能调优（仅消除停摆与无界阻塞；吞吐优化不是本计划验收项）
- TM 侧 ops 端点、TM→JC 指标上报通道、队列水位直测 gauge（roadmap item 32）
- LOCAL 模式数据面（不经 remote transport）
- `IMessageService` 平台层（core 平台接口）改造；`PollingJdbcMessageService` 为 nop-stream-runtime **测试树** harness（非平台层），其改造仅当 D2 裁定需要且范围可控时纳入——若涉及生产级传输语义重构则显式移出并记 successor，且不得因此把 item 31 三要素推出本 plan（D2 候选必须在 harness 既有语义可支撑的范围内成立，见 Current Baseline 后端语义盘点）

## Scope

### In Scope

- `RemoteGraphExecutionPlanBuilder` / `SubtaskPlanBuilder` / `RemoteInputChannel`（订阅切面与队列满语义）
- `JobCoordinator`（stall→recovery→cap 交互，按 D3 裁定范围）
- `JdbcCheckpointStorage`（retained manifests override）
- 设计裁定记录（`ai-dev/design/nop-stream/` owner doc 增补）
- gated 多 JVM 复验 + `distributed-runbook.md` §7 条目更新 + roadmap items 28/31 写回

### Out Of Scope

- BP-1 背压全格/CHAOS-2 HA 全格复验（按 D4 裁定纳入或 watch-only，见 Phase 4）
- 演练报告历史文档改写（resolved 历史文档默认不改，复验记录落 daily log / runbook）

## Execution Plan

### Phase 1 - 设计裁定

Status: completed
Targets: `ai-dev/design/nop-stream/`（数据面收敛设计节，落最终设计状态）

- Item Types: `Decision`

- [x] D1 per-subtask 订阅收敛切面。裁定 = 候选①（assigned-subtask 视角订阅收敛，拒绝惰性订阅）：构建器订阅范围三态——全订阅（单 JVM 执行器默认，语义逐字不变）/ 指定 subtask 集订阅（TM remote-deploy：每次 deploy = assigned (vertex, subtaskIndex) 单例；被收敛通道构造但不订阅（永不订阅，无激活时序假设）；producer 全矩阵照常）/ 零订阅（D1(f) 二态裁定 = **纳入收敛**：RpcDistributedExecutor remoteDeployMode=true 协调器侧 plan 零订阅构建，构建结构保留，终态 = 零订阅非「只订 assigned 输入」）。硬约束 (a)—(f) 逐条论证落 `dataplane-transport-design.md` §二（InputGate 通道集不变/单 JVM 不回退/全图结构保留供 RemoteTaskDeploySupport/不依赖订阅时序/结构语义不变/协调器实例收敛）；union 累积语义 + 未订阅通道读取 typed 快速失败防护落档
- [x] D2 队列满语义。裁定 = 候选①（有界等待 + 超时转通道内 typed 可观测失败，拒绝非阻塞+重投协议）：入队有界等待默认 10s（构造可调）；超时 = 满窗零消费进展 ⇒ decodeError 同款通道内标志 + 读者 surfaced typed 异常（不得以正常 EOS 形态返回）；失败不依赖 onMessage 上抛；重放论证 = JDBC 新订阅 cursor-0 重读 + 新 epoch fencing 过滤 + producer 自 checkpoint 重发；LOCAL 后端安全论证 = 保留进程内背压（槽位释放即成功），仅把「消费者死亡→生产者静默挂死」升级为可见失败；健康路径按序不丢不重。落 `dataplane-transport-design.md` §三
- [x] D3 stall 恢复预算区分。裁定 = 候选①机制 + 候选②证据双轨：恢复触发按原因分池——节点租约到期/FAILED 报告/兼容入口 → 真实故障预算（maxRestarts=3 值与超限 failJob 不变）；liveness stall → 独立 stall 预算（maxStallRestarts=3）+ 冷却窗口（stallRecoveryCooldownMs=30s，窗内跳过含可观测 WARN，超限 failJob）；检测器分类 = 节点失效优先归真实故障；fencing 轮转不因原因变化。拒绝纯候选②理由（结构性属性 + 机制成本极小）+ 根因消除证据锚点（Phase 2/3 focused + Phase 4 CHAOS-1）落 `dataplane-transport-design.md` §四
- [x] D4 复验范围裁定：**BP-1 与 CHAOS-2 均全格复验纳入 Phase 4**（非 watch-only）。理由：BP-1 是唯一「持续输入 + 慢消费者持续排空」形态（D2 不误伤健康慢消费者的核心回归点，500ms 档跨旧 jam 阈值）；CHAOS-2 唯一失败判据（failover 后 fencing 轮转 + 终态收敛）归因 jam 抑制重部署，全格复验以证据关闭「新 leader 重发 assignment」遗留问题而非以论证代验证。落 `dataplane-transport-design.md` §五
- [x] 裁定记录落 design doc（最终设计状态；无 Proposed vs Current 对比节）并过 doc-links：新建 `ai-dev/design/nop-stream/dataplane-transport-design.md`（§一问题/§二 D1/§三 D2/§四 D3/§五 D4/§六 JDBC retained manifests 契约/§七使用契约）+ README.md 注册（Updated 头 + 架构基线层条目 + 阅读顺序 3b）

Exit Criteria:

- [x] D1—D4 全部落档且相互一致（D2 的选择不破坏 D1 的订阅收敛）；实现者按裁定可直接写码（想象性分析通过：订阅范围参数形态、通道构造-订阅分离、溢出标志 read 路径 surfaced、预算分池计数器与冷却判断均可在 live 代码定位落点）
- [x] design doc 更新后 `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `ai-dev/logs/` 当日条目更新

### Phase 2 - per-subtask 订阅收敛 + 队列满语义实现

Status: completed
Targets: `RemoteGraphExecutionPlanBuilder.java`、`SubtaskPlanBuilder.java`、`RemoteInputChannel.java`

- Item Types: `Fix | Proof`

- [x] 按 D1 实现：`RemoteGraphExecutionPlanBuilder.buildRemoteOnly` 增订阅范围参数（null = 全订阅；非空 set = 仅目标 subtask ∈ set 的通道订阅；空 set = 零订阅），通道一律**构造**（全图 plan 结构保留），订阅激活按范围判定；`SubtaskPlanBuilder.buildSubtaskPlan` 传 assigned 单例集；producer 全矩阵照常。TM remote-deploy 下单 subtask deploy 后该 TM 订阅集只含本 subtask 消费的 input topics
- [x] focused 测试（订阅清单断言）：`TestSubscriptionScopeConvergence`（8 用例，recording IMessageService + 真实 buildSubtaskPlan/buildRemoteOnly 路径）——中位 subtask（多上游 + 多入边 A→B/D→B）订阅集精确等于本 subtask 输入集；sink subtask 见全部 upstream 通道（约束 (a)）；source subtask 零订阅；union 语义（同 service 连续 B/0+B/1 = 两者之并，无全对残留）；全图结构保留（A×2/B×2/C×1 subtask 齐全）
- [x] focused 测试（单 JVM 模式不回退）：`nullScopeSubscribesEveryChannelPair_singleJvmExecutorSemantics`（null 范围 = 8/8 全对订阅）+ `emptyScopeSubscribesNothing...`（D1(f) 协调器零订阅形态且全图结构保留）+ 既有 E2E 点名回归（`TestRpcDistributedExecutorRemoteDeployE2E`/`TestEmbeddedDistributedExecutor`/`TestRemoteDeployCheckpointWiringE2E`/`TestRpcDistributedExecutorE2E` 全绿——remoteDeployMode=true 协调器零订阅下数据面经 TM 通道正常流动）；另加未订阅通道读取 typed 快速失败防护用例
- [x] 按 D2 实现：`RemoteInputChannel` 入队 `queue.put` → `queue.offer(enqueueOfferTimeoutMs)`（默认 10s，构造参数可调 + `getEnqueueOfferTimeoutMs`）；超时（满窗零消费进展）→ `overflowError` typed 标志（`ERR_STREAM_CHANNEL_OVERFLOW` 新错误码，携带 timeoutMs/topic 参数）+ finished + EOS 唤醒读者；read 路径 take/poll 后先检查通道错误再判 EOS（禁止以正常 EOS 形态吞掉溢出失败）；`isOverflowed()` 可观测
- [x] focused 测试（队列满）：`TestRemoteInputChannelQueueFull`（3 用例）——无读者满队列：dispatch（LocalMessageService 同步派发面）阻塞 ≈ 有界窗（300ms 实测断言）非永久，typed 溢出错误 + 错误码/参数断言；健康读者：50 条按序不丢不重；健康慢读者（50ms/条持续排空）：连续背压下 offer 全部成功、零误报（BP-1 500ms 档语义钉定）
- [x] 既有 remote transport 单测回归（通道/barrier/unaligned channel-state/rescale 族）：runtime 模块全量 990/0/0 绿（含 TestRemoteDataExchange/TestRemoteInputChannelHeartbeat/TestInputGateSingleChannelRemoteLiveness/TestBufferPoolRemoteExclusion/channel-state rescale 族/TestDataPlane*BackendE2E）

Exit Criteria:

- [x] 订阅清单断言测试绿（Anti-Hollow：真实 buildSubtaskPlan 路径的订阅行为，非类型存在）——`TestSubscriptionScopeConvergence` 8/8
- [x] 单 JVM 执行器模式回归绿（D1 硬约束 (b) 的验证锚点）——null 范围全对订阅断言 + 4 个 E2E 点名 + runtime 990/0/0
- [x] 队列满语义测试绿；任何丢弃/失败路径可观测（typed 错误或显式计数），无静默吞——`TestRemoteInputChannelQueueFull` 3/3 + `isOverflowed()`/ERROR 日志可观测
- [x] 既有 transport 族测试不回退——runtime 990/0/0 + 全量 `./mvnw test -pl nop-stream -am -T 1C` 11 模块 BUILD SUCCESS（fraud 110/0/0/13）
- [x] owner-doc 裁定：Phase 1 design doc（`dataplane-transport-design.md`）§二/§三与实现落地实态核对一致（订阅范围三态/构造-订阅分离/10s 默认有界窗/typed 溢出禁止 EOS 形态返回/未订阅读取 typed 防护均按裁定落地，无偏差需回写）
- [x] `ai-dev/logs/` 当日条目更新

### Phase 3 - stall/recovery 区分 + JDBC retained manifests

Status: completed
Targets: `JobCoordinator.java`、`JdbcCheckpointStorage.java`

- Item Types: `Fix | Decision | Proof`

- [x] 按 D3 实现（候选①机制 + ②证据双轨）：`JobCoordinator` 恢复触发增原因维度（`requestRecovery(RecoveryCause)`：NODE_FAILURE/TASK_STALL/OTHER，无参入口委派 OTHER = 真实故障预算语义不变）；`detectFailures` 分类（节点租约失效优先归真实故障、纯 liveness 停滞归 stall）；stall 独立预算（`stallRestartCount`/`maxStallRestarts=3`，超限 failJob）+ 冷却窗（`stallRecoveryCooldownMs=30s`，窗内 WARN 跳过不烧预算不转 fencing）；`globalRecovery(boolean stallTriggered)` 预算分池、fencing 轮转/abort/reassignment 全同；health/event 序号用全池总数（跨池单调）。根因消除证据（②轨）：Phase 2 队列满测试证明通道积压不再永久阻塞 dispatch（jam 不再诱发 liveness 停滞）+ 本 Phase detectFailures 分类测试
- [x] `JdbcCheckpointStorage` override `loadRetainedEpochManifests`（`stream_epoch_manifest` per-epoch 行，epoch 降序 + LIMIT count；count≤0/表缺失 → 空集；typed wrap 对齐 sibling 方法）
- [x] focused 测试：`TestJdbcCheckpointStorage#testLoadRetainedEpochManifestsMultiEpochNewestFirstCountBounded`（5 epoch 写入 → count=3 取 5/4/3 最新优先 + count=10 全集 + count=0 空 + 未知 job 空）——与 LocalFile 对应用例（`TestEpochManifestPersistence` retained 路径）行为一致
- [x] **接线验证**：`TestCheckpointCoordinatorJdbcRetainedManifests`（真实 `CheckpointCoordinator.restoreSharedStateRegistry` 消费点，JDBC 后端）——3 次 RocksDB 增量 checkpoint 落 JDBC manifest（共享 SST 去重）→ 新 coordinator 同存储 + segmentStore `restoreSharedStateRegistry()` → registry 每 hash ref-count == 3（仅多 epoch retained 集可达，latest-only default 只得 1）+ GC map 覆盖全部 retained epoch（cp1/cp2/cp3）
- [x] D3 focused 测试：`TestJobCoordinatorStallRecoveryBudget` 5 用例——stall ×3 只烧 stall 预算（restartCount=0）+ 满风暴后真实故障恢复仍进行（CHAOS-1 锚点 coordinator 级）；冷却窗跳过（预算不烧 + fencing 不转）；stall cap 超限 failJob 且真实预算不动；detectFailures 真实分类（纯 liveness 停滞 → stall 预算；租约到期（双信号并存）→ 真实预算，node loss 优先）

Exit Criteria:

- [x] D3 验收锚点测试绿（持续流量 + kill ≥2 轮可恢复、恢复预算不被 stall 诱发恢复耗尽——单进程级 5 用例绿（含「stall 风暴后真实故障恢复仍进行」锚点）；多 JVM 证据在 Phase 4 CHAOS-1 简化复验承载（plan 预期拆分））
- [x] JDBC override focused 测试 + 接线验证绿（28/28 + 1/1，含 ref-count=3 多 epoch 断言）
- [x] owner-doc 裁定：`checkpoint-design.md` §9 Restart 恢复段补「双存储对等（items 28+31 / W-8）」句（JDBC per-epoch 行承载 + 契约指针 `dataplane-transport-design.md` §六）；D3 预算分池最终状态已在 `dataplane-transport-design.md` §四（Phase 1 落档，实态一致）
- [x] `ai-dev/logs/` 当日条目更新

### Phase 4 - gated 多 JVM 端到端复验

Status: completed
Targets: fraud-example gated 套件、`distributed-runbook.md`

- Item Types: `Proof`

- [x] **端到端验证（Anti-Hollow 主证据）**：SOAK-3 全参数复跑（S2 场景、200 行/s × 180s = 36000 行，为 jam 阈值 ~10³ 的 36 倍）**PASS**（runId `1788456304950-1`，墙钟 281.6s，`_tmp/mini-stream-cluster/` 留档 + preserve-artifacts）——判据逐条：① 终态输出 == 期望集（multiset 无重复无丢失，180 窗口行）✅ ② durable epoch 推进 55 次（≥5）且 max gap 5010ms（<120s）✅ ③ hang 独立判据均不触发（gap/活跃尾部队列增长/输出不收敛均无）✅。注：首轮 run（`1788455717401-1`）暴露演练装置 queue-depth 启发式误校准（insert-only 后端 send 侧计数单调增长被误判泄漏——本 plan Current Baseline 已修正归因「订阅收敛不消除表计数」）；就地修复演练基建（`ExerciseSampler.queueDepthUnboundedGrowth` 增 epoch 无推进耦合 = jam 签名保留 100%，钉定测试同步更新）后按**原参数**重跑合法闭环
- [x] CHAOS-1 简化复验：持续流量 + kill TM ×2 轮（`-Dexercise.chaos.rounds=2 -Dexercise.chaos.partitionFromRound=3`）**PASS**（runId `1788456616196-1`，墙钟 547.2s）——每轮 fencing epoch 严格递增（1→2→3，chaos-events.jsonl 留档 outcome=recovered ×2）+ 终态 == 期望集（exactly-once）；D3 多 JVM 锚点成立：真实 kill 恢复能力完整（jam 根因消除后无 stall 恢复消耗真实预算）
- [x] 既有 C0—C3 gated 13 项 + legacy 套件回归绿：场景 gated **7/7 绿**（TestS1MultiJvmE2E 2 + TestS2MultiJvmE2E 2 + TestS2RestoreRescaleMultiJvmE2E 1（C2）+ TestScenarioBackpressureMultiJvmE2E 2（C3））+ 序列化 6 绿（fraud-example 默认套件 110/0/0/13 含）；legacy **8/8**（ProcessSpawn 3 + Failover 3 + HealthStateAndAlerts 1 + ExactlyOnceRecovery 1——后者首跑失败为 2026-09-04 早间已 A/B 裁定的 pre-existing 负载相关恢复检测超时（同签名 `initial=1 recovered=1`，plan 1951-2 留档），隔离复跑绿 2.1s，非回归）
- [x] D4 裁定项执行（BP-1 与 CHAOS-2 均全格复验留档）：**BP-1 PASS**（runId `1788457179765-1`，墙钟 492.8s）——三档节流 50/200/500ms 各窗口 checkpoint 推进 29/30/30 次（原 200/500ms 档 jam 消除；500ms 档 = D2 有界等待不误伤健康慢消费者实证）+ 释放后终态 == 期望集；**CHAOS-2 FAIL → 新缺陷路由**（runId `1788457688118-1`，墙钟 1050s）——租约翻转子判据 PASS（leaseEpoch 1→2→3 严格递增 ×2）但终态不收敛；根因 = **pre-existing HA 接管缺陷**（新 leader `activateAsLeader` 租约获取 + fencing 轮转后，assignment 重发 INSERT 与旧 leader 存留行唯一键冲突 → become-leader listener 中止 → 接管半途而废）；同一签名存在于 item 15 演练产物（`1788385016277-1` coordinator-2/3 日志）——其时报告归因「jam 抑制重部署」修正为本缺陷；数据面健康（queue 平台 1236、无泄漏信号）。路由 = roadmap **item 34**（非 items 28/31 缺陷族：D1—D3 锚点全绿 + 缺陷在租约接管路径而非数据面）
- [x] `distributed-runbook.md` §7 item 28 条目更新：全对订阅 + 队列满 + JDBC retained manifests 三条目 → **已修复 + 复验命令锚点**（runId + 判据结果）；另落三条新诚实披露：HA 接管中止（item 34，含 item 15 归因修正）/ EpochManifest retention 缺失（item 33，SOAK-3 发现 137 文件 vs maxRetained=5，item 15「有界」正观察系 jam 早停所致）/ queue-depth 启发式再校准说明 + BP-1 全档复验结果；演练报告为 resolved 历史文档不改写，复验证据落 daily log + runbook

Exit Criteria:

- [x] SOAK-3 与 CHAOS 复验 run 留档（runId + 判据逐条结果）：SOAK-3 `1788456304950-1`（①②③ 全过）+ 首轮启发式校准证据 run `1788455717401-1`；CHAOS-1 `1788456616196-1`（fencing 1→2→3 + exactly-once）；BP-1 `1788457179765-1`（三档 29/30/30）；CHAOS-2 `1788457688118-1`（租约 PASS/接管中止 → item 34）
- [x] C0—C3 + legacy 回归绿（场景 gated 7/7 + 序列化 6（默认套件绿内含）+ legacy 8/8（1 处 pre-existing 负载 flake 隔离复跑绿，2026-09-04 早间 A/B 裁定引用））
- [x] runbook 更新后 `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `ai-dev/logs/` 收口条目更新

## Closure Gates

- [x] F-D 收敛：无人消费通道的全对订阅消除（TM remote-deploy 路径 = SubtaskPlanBuilder 单例订阅集 + RpcDistributedExecutor remoteDeployMode=true 协调器零订阅；订阅清单断言 TestSubscriptionScopeConvergence 8/8 含 union/多入边/全对零订阅读者语义）+ dispatch 线程无永久阻塞（TestRemoteInputChannelQueueFull 3/3：有界窗实测 + typed 溢出 + 慢读者零误报）
- [x] item 31 三要素覆盖：per-subtask 收敛（D1）/ 队列满语义（D2 有界等待 + ERR_STREAM_CHANNEL_OVERFLOW）/ stall 恢复预算区分（D3：TestJobCoordinatorStallRecoveryBudget 5/5 含「stall 风暴后真实恢复仍进行」锚点 + CHAOS-1 多 JVM 证据 fencing 1→2→3）
- [x] W-8 承接：JDBC `loadRetainedEpochManifests` override（ORDER BY epoch_id DESC LIMIT count）+ 接线验证（TestCheckpointCoordinatorJdbcRetainedManifests：真实 restoreSharedStateRegistry 消费点，多 epoch ref-count==3 断言仅经 retained 集可达）
- [x] **端到端验证**：SOAK-3 全参数（36000 行 = 旧 jam 阈值 36 倍）完整收敛 exactly-once + epoch 推进（gated 留档 runId `1788456304950-1`，verdict=pass，55 次推进 max gap 5010ms）
- [x] 短时基线不回退（C0—C3 gated 7/7 + 序列化 6（默认套件绿内含）+ legacy 8/8（1 处 pre-existing 负载 flake 隔离复跑绿）+ 全量 11 模块 BUILD SUCCESS（closure audit 独立复跑确认））
- [x] 无静默数据丢弃（exactly-once multiset 断言 + 丢弃/失败路径可观测：溢出 = typed 错误码 + timeoutMs/topic 参数 + ERROR 日志 + isOverflowed() 钩子）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope 项（CHAOS-2 终态判据与 manifest retention = **范围外 pre-existing 缺陷**，显式路由 successor items 34/33 附分类与 Why-Not-Blocking；D4 裁定两项全格复验均已执行非 watch-only）
- [x] owner docs / runbook 已同步（dataplane-transport-design.md 新建 + README 注册 + checkpoint-design.md §9 双存储句 + distributed-runbook.md §7 修复锚点与归因修正）
- [x] 独立子 agent closure audit 已完成并记录证据（fresh session `ses_f9781e482ffe2cTz95Xlzo5zvZ`，**CLOSURE-AUDIT: APPROVED**，0 Blocker / 0 Major / 2 Minor（均为收口仪式本身）+ 3 Info；证据见 Closure 段）
- [x] **Anti-Hollow Check**：从场景入口（XDSL/env → MiniStreamCluster 真实多 JVM spawn → JobCoordinatorMain → assignTasks remoteDeploy → deployTask RPC → TaskManager → SubtaskPlanBuilder 订阅收敛构建）到 sink 输出（36000 行 exactly-once 收敛 = 调用链运行时连通的端到端证明）；audit 逐链核验（RemoteGraphExecutionPlanBuilder.java:126/172、SubtaskPlanBuilder.java:135、RemoteInputChannel.java:90/567/361、JobCoordinator.java:1543/1513/1582、JdbcCheckpointStorage.java:589）无空方法体/静默跳过/no-op 正常实现；scan-hollow --severity high = 0 findings
- [x] roadmap items 28/31 已写回 `done`（closure audit 通过后、Plan Status 置 `completed` 前执行；写回记录落 Closure 段——不在任何 Phase 内）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（11 模块 BUILD SUCCESS；closure audit 独立复跑同绿）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0（completed 转换后复跑确认）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` exit 0（0 findings，audit 独立复跑）
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0（audit 独立复跑）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（0 errors 0 warnings，31,463 refs，audit 独立复跑）

## Deferred But Adjudicated

### CHAOS-2 终态收敛判据（非 items 28/31 in-scope 项，Phase 4 复验发现 pre-existing 缺陷）

- Classification: `moved to explicit successor ownership`（roadmap item 34，`todo`）
- Why Not Blocking Closure: CHAOS-2 的失败判据（failover 后 fencing 轮转 + 终态收敛）根因是 **HA 接管路径的 assignment 唯一键冲突**（`activateAsLeader` listener 中止），非 items 28/31 的数据面缺陷族——同一签名存在于修复前的 item 15 演练产物（`1788385016277-1`），证明 pre-existing 且与 jam 无关（jam 只是掩盖了它）；items 28/31 的验收锚点（SOAK-3 36× 阈值 exactly-once / CHAOS-1 kill 恢复 / BP-1 背压语义 / C0—C3 短时基线）全绿，数据面收敛目标已达成。租约 failover 子判据（本修复域相邻但不属本 plan scope）复验 PASS。归因修正（item 15 报告「jam 抑制重部署」→ 实为接管中止）已落 runbook §7 + roadmap item 34
- Successor Required: yes
- Successor Path: roadmap item 34（HA failover 接管修复：attempt 计数种子化 / assignment upsert + G56 单调不变量保持）

### EpochManifest retention 缺失（Phase 4 SOAK-3 发现）

- Classification: `moved to explicit successor ownership`（roadmap item 33，`todo`）
- Why Not Blocking Closure: manifest 文件累积是磁盘缓慢泄漏（bounded per run、不影响 exactly-once/稳定性判据）；SOAK-3 in-plan 判据（①②③）不含 manifest 有界性；item 15 的「retained manifests 有界」正观察本身是 jam 早停假象（归因修正已落 runbook）。非 items 28/31 in-scope 缺陷（属 checkpoint retention 域）
- Successor Required: yes
- Successor Path: roadmap item 33（retention 路径同步裁剪 epoch manifests，双存储）

## Non-Blocking Follow-ups

- roadmap item 32（观察面缺口：TM 侧 io 指标不经 JC 暴露 + 队列水位直测 gauge 缺）保持 `todo`——本 plan 的复验以 JC 面 + 存储面代理完成全部判据，观察面缺口不阻塞稳定性基线成立（与 item 15 裁定一致）
- 演练装置 queue-depth 启发式已随本 plan 再校准（epoch 无推进耦合）；若未来消息后端支持消费后删除/表回收，可再校准回纯深度口径（观察项，无 successor 需求）
- `TestMultiJvmExactlyOnceRecovery` 负载相关恢复检测超时 flake（2026-09-04 两次留档：plan 1951-2 A/B 裁定 + 本 plan 隔离复跑绿）——watch-only，多 JVM 套件运行避免并行负载

## Closure

Status Note: items 28+31 的停摆缺陷族已按三要素修复（D1 per-subtask 订阅收敛 / D2 队列满有界等待 + typed 溢出 / D3 stall 恢复预算分池）并承接 W-8（JDBC retained manifests override + 接线验证）；分布式持续运行稳定性基线以全参数 gated 复验成立（SOAK-3 36× 旧阈值 exactly-once、CHAOS-1 kill 恢复 fencing 严格递增、BP-1 三档全推进、C0—C3/legacy 短时基线不回退）。Phase 4 复验发现两个范围外 pre-existing/相邻缺陷，显式路由 successor（item 33 manifest retention、item 34 HA 接管 duplicate-key 中止——后者修正了 item 15 报告的归因），无 in-scope 项被降级。roadmap items 28/31 已写回 `done`（2026-09-04，写回记录 = roadmap 行 + Last updated 头）。
Completed: 2026-09-04

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent fresh session（closure audit task `ses_f9781e482ffe2cTz95Xlzo5zvZ`）
- Verdict: **CLOSURE-AUDIT: APPROVED**（0 Blocker / 0 Major / 2 Minor（均为收口仪式自身步骤：Closure Gates 勾选 + roadmap 写回，已随后执行）/ 3 Info（gated 套件未由 audit 复跑——以留档产物 + 默认套件 + 11 模块全量独立复跑缓解；Current Baseline 行号漂移属标注日期的既定基线；CHAOS-1 maxGap 70s 与语义一致））
- Evidence:
  - Phase 1—4 逐条 Exit Criterion：PASS（audit 逐项 live 核验——Phase 2 订阅范围三态/有界等待/typed 溢出/未订阅防护实码锚点 RemoteGraphExecutionPlanBuilder.java:126,172 / SubtaskPlanBuilder.java:135 / RpcDistributedExecutor.java:294 / RemoteInputChannel.java:90,567,361；Phase 3 RecoveryCause/预算分池/冷却/JDBC override 锚点 JobCoordinator.java:1543,1513,1582 / JdbcCheckpointStorage.java:589；Phase 4 五个 runId 产物存在且 verdict 与 plan 一致（SOAK-3 pass/CHAOS-1 pass/BP-1 pass/CHAOS-2 criteria-violated 含 duplicate-key 证据/首轮 SOAK-3 启发式校准证据））
  - Closure Gates 16/16：PASS（见上节逐条证据；audit per-gate 表全 PASS，唯一 PENDING 项 = roadmap 写回，已执行）
  - 独立复跑：focused 4 类 17/0/0/0 绿（audit 现场）+ 全量 11 模块 BUILD SUCCESS + check-plan-checklist/scan-hollow(0 findings)/check-nop-stream-invariants/check-doc-links(0/0, 31,463 refs) 全 exit 0（audit 现场复跑）
  - Anti-Hollow 检查：MiniStreamCluster → JobCoordinatorMain → assignTasks(remoteDeploy) → deployTask RPC → TaskManager.deployTask → SubtaskPlanBuilder（订阅收敛）→ RemoteInputChannel/RemoteResultPartition → sink 36000 行 exactly-once 收敛 = 调用链运行时连通；无空方法体/静默跳过（scan-hollow 0 findings）
  - 诚实性：CHAOS-2 终态判据与 manifest retention 显式路由 successor items 34/33（duplicate-key 同签名存于 item 15 产物 1788385016277-1 = pre-existing 证明；plan In Scope 不含 HA 接管/manifest retention）；无 in-scope 项静默降级
- 文本一致性（规则 19）：Plan Status completed ↔ 四 Phase completed 且 checklist 全 [x] ↔ Closure Gates 16/16 [x] ↔ Deferred But Adjudicated 2 项附分类理由 ↔ Non-Blocking Follow-ups 3 项 ↔ `ai-dev/logs/2026/09-04.md` 五条目（Phases 1—3/Phase 4/closure）↔ roadmap items 28/31 `done` + Last updated——八处一致

Follow-up:

- roadmap item 33（EpochManifest retention 缺失，`todo`）：retention 路径同步裁剪 epoch manifests（双存储）
- roadmap item 34（HA failover 接管 duplicate-key 中止，`todo`）：attempt 计数种子化 / assignment upsert + G56 单调不变量保持
- roadmap item 32（观察面缺口）保持 `todo`；`TestMultiJvmExactlyOnceRecovery` 负载 flake watch-only（两轮留档）
- 无剩余 plan-owned work
