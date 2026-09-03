# remote-deploy 数据面通道收敛与持续运行稳定性修复（roadmap items 28 + 31）

> Plan Status: active
> Mission: nop-stream-productization
> Work Item: roadmap items 28 + 31
> Last Reviewed: 2026-09-03
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

Status: planned
Targets: `ai-dev/design/nop-stream/`（数据面收敛设计节，落最终设计状态）

- Item Types: `Decision`

- [ ] D1 per-subtask 订阅收敛切面。候选：① TM remote-deploy 路径（`SubtaskPlanBuilder`）增 assigned-subtask 视角：consumer channels 只为 assigned subtask 订阅（本 subtask 的全部 upstream (s→t_my) 通道），producer partitions 仍按全 target 矩阵构建（发送侧不订阅）；② 通道构建保持全图但订阅惰性化/按需开启。硬约束：(a) InputGate 必须仍见本 subtask 全部 upstream channels（barrier 对齐/unaligned channel-state 语义不回退）；(b) **单 JVM 执行器模式（remoteDeployMode=false 的 Rpc/Embedded 路径）全订阅语义不回退**（收敛不得做成 builder 无差别默认行为而破坏全部 subtask 消费）；(c) **全图 plan 构建结构保留**——`DeployedSubtaskPlan.getPlan()` 供 `RemoteTaskDeploySupport` checkpoint/rescale 使用，收敛只作用于订阅，不裁剪构建结构；(d) **订阅开启时点不得依赖「订阅早于生产」的隐含时序**——`LocalMessageService` 无 backlog 重放，惰性订阅候选必须论证首消息安全（JDBC cursor-0 重放不可作为唯一安全论证）；(e) restore-time rescale 路径不回退；topic 命名确定性（`StreamTopicNaming`）不变；TM 间同构图语义（fingerprint 一致钉定）不回退；(f) **`RpcDistributedExecutor:285` 在 remoteDeployMode=true 下的协调器侧全对订阅（同族实例，见 Current Baseline）**：二态裁定——纳入收敛（注意该实例收敛终态与 TM 路径不同型：remoteDeployMode=true 下协调器侧零 subtask 运行、全部通道无人消费，收敛 = 协调器侧零订阅/订阅不激活，而非「只订 assigned 输入」）或显式移出 scope 附理由并同步调整 Closure Gate「全对订阅消除」措辞；不允许不裁定。
- [ ] D2 队列满语义。候选：① 有界等待 + 超时转 typed 可恢复失败（触发该 task 恢复、消息可重放）；② 非阻塞投递 + 消费进度/重投协议。硬约束：(a) dispatch 线程永不被单通道永久阻塞；(b) 不静默丢数据——重放可行性按 Current Baseline 后端语义盘点论证：消费后不删除 + cursor 照常推进（异常吞）⇒ 候选①的超时失败须经**通道内可观测标志**（decodeError 先例）触发恢复，不能依赖 onMessage 异常上抛；重放来源 = 恢复后新订阅 cursor-0 重读（新 epoch fencing 过滤）+ producer 从 checkpoint 重发；(c) 语义对 LOCAL 后端（LocalMessageService 无重放）同样安全。
- [ ] D3 stall 恢复预算区分。候选：① stall 诱发恢复独立预算/冷却窗口（真实故障恢复预算不被 stall 消耗）；② 论证 D1+D2 根因消除后「stall 诱发连续恢复耗尽 cap」路径不可达 + regression 测试钉定。验收锚点（两种候选均须满足）：CHAOS-1 形态（持续流量 + kill TM ≥2 轮）在恢复预算内可恢复、真实 kill 不因历史 stall 恢复而丧失恢复能力。
- [ ] D4 复验范围裁定：BP-1（三档节流）与 CHAOS-2（HA failover）全格复验纳入 Phase 4，或 residual watch-only（附理由：jam 根因消除后该格判据的覆盖归属）。
- [ ] 裁定记录落 design doc（最终设计状态；无 Proposed vs Current 对比节）并过 doc-links

Exit Criteria:

- [ ] D1—D4 全部落档且相互一致（D2 的选择不破坏 D1 的订阅收敛）；实现者按裁定可直接写码（想象性分析通过）
- [ ] design doc 更新后 `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] `ai-dev/logs/` 当日条目更新

### Phase 2 - per-subtask 订阅收敛 + 队列满语义实现

Status: planned
Targets: `RemoteGraphExecutionPlanBuilder.java`、`SubtaskPlanBuilder.java`、`RemoteInputChannel.java`

- Item Types: `Fix | Proof`

- [ ] 按 D1 实现：TM remote-deploy 路径下，deploy 单 subtask 后该 TM 的消息订阅集只含本 subtask 消费的 input topics；全图 plan 构建结构保留
- [ ] focused 测试（订阅清单断言）：以 recording/fake IMessageService 断言 TM remote-deploy 单 subtask 后的订阅 topic 集精确等于期望集（无他人通道、无全对残留）——覆盖多前缀/多入边 vertex 形态；并覆盖 union 语义：同一 TM 连续部署 2 个 subtask 后订阅集 == 各自输入集之并（累积不重置）
- [ ] focused 测试（单 JVM 模式不回退）：`RpcDistributedExecutor`/`EmbeddedDistributedExecutor` 路径全矩阵通道订阅与消费语义回归（既有测试点名 + 必要时补断言）；remoteDeployMode=true 协调器侧订阅按 D1(f) 裁定核验
- [ ] 按 D2 实现队列满语义
- [ ] focused 测试（队列满）：小容量通道 + 无读者连续投递 → dispatch 线程不被永久阻塞且 D2 语义生效（超时失败可恢复/重放收敛，或按裁定协议收敛）；正常路径（有读者）按序投递不丢不重
- [ ] 既有 remote transport 单测回归（通道/barrier/unaligned channel-state/rescale 族）

Exit Criteria:

- [ ] 订阅清单断言测试绿（Anti-Hollow：真实 buildSubtaskPlan 路径的订阅行为，非类型存在）
- [ ] 单 JVM 执行器模式回归绿（D1 硬约束 (b) 的验证锚点）
- [ ] 队列满语义测试绿；任何丢弃/失败路径可观测（typed 错误或显式计数），无静默吞
- [ ] 既有 transport 族测试不回退
- [ ] owner-doc 裁定：Phase 1 design doc 增补节与实现落地后实态核对同步（订阅语义/队列语义如与裁定有偏差须回写）；或显式 `No owner-doc update required`
- [ ] `ai-dev/logs/` 当日条目更新

### Phase 3 - stall/recovery 区分 + JDBC retained manifests

Status: planned
Targets: `JobCoordinator.java`、`JdbcCheckpointStorage.java`

- Item Types: `Fix | Decision | Proof`

- [ ] 按 D3 实现（机制方案），或完成不可达论证 + regression 测试钉定（论证方案须有测试证明 jam 根因消除后 stall 检测不再被通道积压诱发）
- [ ] `JdbcCheckpointStorage` override `loadRetainedEpochManifests`（retained set 语义与 LocalFileCheckpointStorage 对等：多 epoch 保留集、count 上界）
- [ ] focused 测试：JDBC 后端多 epoch 写入后 retained 集加载正确 + 与既有 LocalFile 对应用例行为一致
- [ ] **接线验证**：override 被 CheckpointCoordinator 真实恢复路径消费（CheckpointCoordinator.java:1550 调用点，用 JDBC 后端走真实 loadRetainedEpochManifests 路径的测试断言，非仅 override 单测）

Exit Criteria:

- [ ] D3 验收锚点测试绿（持续流量 + kill ≥2 轮可恢复、恢复预算不被 stall 诱发恢复耗尽——单进程或多 JVM 形态按裁定，至少一条多 JVM 证据在 Phase 4）
- [ ] JDBC override focused 测试 + 接线验证绿
- [ ] owner-doc 裁定：checkpoint/state-management 相关 design doc 的 retained manifests 双存储语义节同步；否则显式 `No owner-doc update required`
- [ ] `ai-dev/logs/` 当日条目更新

### Phase 4 - gated 多 JVM 端到端复验

Status: planned
Targets: fraud-example gated 套件、`distributed-runbook.md`

- Item Types: `Proof`

- [ ] **端到端验证（Anti-Hollow 主证据）**：SOAK-3 全参数复跑（S2 场景、200 行/s × 180s = 36000 行，为 jam 阈值 ~10³ 的 36 倍），经 `TestStabilityExerciseMultiJvm`（gated + preserve-artifacts）——判据与 harness 实现口径一致：① 终态输出 == 期望集（multiset 无重复无丢失）② durable epoch 推进 ≥5 且无 >120s gap（harness SOAK-3 实际断言，非报告 Phase-1 定义的 60s）③ hang 各独立判据均不触发（gap>120s、活跃尾部队列增长、输出不收敛——harness 各自独立判 fail，非合取口径）
- [ ] CHAOS-1 简化复验：持续流量 + kill TM ≥2 轮（含恢复确认）——每轮 fencing epoch 严格递增 + 终态 == 期望集（真实故障恢复能力不被历史恢复耗尽）
- [ ] 既有 C0—C3 gated 13 项 + legacy 套件回归绿（短时基线不回退）
- [ ] D4 裁定项执行（BP-1/CHAOS-2 复验留档，或 watch-only 记录）
- [ ] `distributed-runbook.md` §7 item 28 条目更新：已知边界（已定量触发）→ 已修复 + 复验命令锚点；演练报告为 resolved 历史文档不改写，复验证据落 daily log

Exit Criteria:

- [ ] SOAK-3 与 CHAOS 复验 run 留档（runId + 判据逐条结果）
- [ ] C0—C3 + legacy 回归绿
- [ ] runbook 更新后 `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] `ai-dev/logs/` 收口条目更新

## Closure Gates

- [ ] F-D 收敛：无人消费通道的全对订阅消除（TM remote-deploy 路径 + D1(f) 裁定覆盖的实例；订阅清单断言）+ dispatch 线程无永久阻塞（队列满语义测试）
- [ ] item 31 三要素覆盖：per-subtask 收敛 / 队列满语义 / stall 恢复预算区分（D3 验收锚点证据）
- [ ] W-8 承接：JDBC `loadRetainedEpochManifests` override + 接线验证
- [ ] **端到端验证**：SOAK-3 全参数（36000 行）完整收敛 exactly-once + epoch 推进（gated 留档）
- [ ] 短时基线不回退（C0—C3 13/13 + legacy 绿）
- [ ] 无静默数据丢弃（exactly-once 断言 + 丢弃/失败路径可观测）
- [ ] 不存在被静默降级到 deferred/follow-up 的 in-scope 项（D4 裁定除外，须附分类与理由）
- [ ] owner docs / runbook 已同步
- [ ] 独立子 agent closure audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：从场景入口（XDSL/env → 分布式部署 → TM 数据面）到 sink 输出的调用链运行时连通；无空方法体/静默跳过/no-op 正常实现
- [ ] roadmap items 28/31 已写回 `done`（closure audit 通过后、Plan Status 置 `completed` 前执行；写回记录落 Closure 段——不在任何 Phase 内）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` exit 0
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

## Deferred But Adjudicated

（Phase 1 D4 裁定后填充；预期候选：BP-1/CHAOS-2 全格复验 watch-only、credit-based 流控 optimization candidate——均须附 Why Not Blocking Closure）

## Non-Blocking Follow-ups

（收口时填充；预期：item 32 观察面缺口链接、背压传导量化观察项）

## Closure

Status Note: （关闭时填写；含 roadmap items 28/31 写回记录）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent closure audit）
- Evidence: （每条 Exit Criterion / Closure Gate 的 PASS/FAIL 与 live 锚点；SOAK-3/CHAOS runId 与判据结果；check-plan-checklist / scan-hollow 退出码；Anti-Hollow 端到端调用链追踪结果）

Follow-up:

- （只记录 non-blocking follow-up；或明确写 no remaining plan-owned work）
