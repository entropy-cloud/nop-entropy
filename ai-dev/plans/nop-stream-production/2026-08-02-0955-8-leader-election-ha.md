# 8 Leader election / HA（WIRE ILeaderElector + standby coordinator + fencing-on-leadership）

> Plan Status: active
> Last Reviewed: 2026-08-02
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1 发现 1 Blocker + 6 Major + 4 Minor，全部修复；round 2 确认 no Blocker、recovery fencing composite-token 方案自洽、Phase 1↔2 无矛盾、Rule #10 合规）
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 38；`00-vision.md` §九（分布式执行三面分离，控制面 `IStreamCoordinatorRpcService`）；`00-vision.md` §八 不变量 #8（旧 coordinator 必须被 fencing）
> Mission: nop-stream-production
> Work Item: 38. Leader election / HA（G24, G25，P1）
> Related: 前置 Phase 1（DeploymentPlan/discovery Stage 24、per-task failure detection Stage 25 已完成）；后继 `Stage 39`（跨 JVM RPC + fencing token 统一为单调 epoch）—— 本 plan 与 Stage 37（`2026-08-02-0955-7`）无执行依赖，可并行；**本 plan 是 Phase 4 分布式接线的关键路径入口，unblock Stage 39/40/42**

## Purpose

为 `JobCoordinator` 接入平台 leader 选举实现 HA（G24），并交付 standby coordinator + fencing-on-leadership（G25）。nop-stream 是平台 `SysDaoLeaderElector` 的**首个生产用户**。具体收口三件事：(1) `JobCoordinator` 从「单实例、随机 UUID fencing」升级为「leader-gated」——在分布式/HA 模式下，coordinator 实例先竞选 leader，当选后才激活控制面（assignTasks / triggerCheckpoint / collectAck），落选则保持 standby；(2) fencing token 在 HA 模式下来自当选的 `LeaderEpoch`，取代裸随机 UUID，使「leadership 切换 = fencing 轮转」成立（不变量 #8）；(3) 解耦当前被混用的「recovery fencing」与「leadership fencing」——`globalRecovery()` 当前每次恢复都轮转 token（`JobCoordinator.java:661-663`），HA 模式下 leadership epoch 只应在 leadership 切换时轮转。

**Stage 边界**：本 plan 交付 leader-election WIRE + standby + fencing-on-leadership，fencing 表示仍为 String（避免 churn 现有 task-RPC 过滤路径 `RemoteInputChannel`/`TaskAssignment`/`CheckpointBarrierSignal`）。跨 JVM RPC 传输（`IStreamTaskRpcService` 经 `MessageRpcServer` 暴露）+ 把 String token 统一为单调 long epoch 属 **Stage 39**。

## Current Baseline

> 已核对 live repo（`JobCoordinator.java`、`ILeaderElector.java`、`SysDaoLeaderElector.java`、`nop-stream/nop-stream-runtime/pom.xml`、`EmbeddedDistributedExecutor.java`、控制/数据面 fencing 调用点）。

- **平台 leader 选举已存在且可编译期可达**：`nop-stream-runtime/pom.xml:25` 已依赖 `nop-cluster-core`。`ILeaderElector`（`io.nop.cluster.elector.ILeaderElector`）API：`getHostId()` / `getLeaderId()` / `getCurrentEpoch()` / `getLeaderEpoch()` / `addElectionListener(ILeaderElectionListener)` / `isLeader()` / `whenElectionCompleted()` / `restartElection()`。`LeaderEpoch(leaderId, epoch, expireAt)`——`epoch` 为单调递增 long。
- **JDBC 实现 `SysDaoLeaderElector`**（`io.nop.sys.dao.elector`，`nop-sys-dao` 模块）：继承 `AbstractPollingLeaderElector`，基于 `NopSysClusterLeader` 实体 + lease（`refreshTime`/`expireAt`/`leaseMs`），`changeLeader` 时 `leaderEpoch++`。`@Inject setOrmTemplate` / `@Inject setDaoProvider`。**不在 nop-stream 编译期依赖中**——本 plan 只对 `ILeaderElector` 接口编程；`SysDaoLeaderElector` 作为部署期 bean 经 IoC 注入。
- **`JobCoordinator` 当前无 leader 选举、无 standby**：
  - `fencingToken: AtomicReference<String>`（`:93`），`start()` 若未预设则生成随机 UUID（`:198-203`），`setFencingToken(token)` setter（`:865`）。
  - **`globalRecovery()`（`:647-699`）每次恢复都轮转 token**：`:661-663` `UUID.randomUUID()` + `fencingToken.getAndSet(newToken)` + `:666` 重新注册 ClusterRegistry + `:674-676` `rpc.updateFencingToken(newToken)` 推送给所有 TaskManager。即「recovery fencing」与「leadership fencing」当前混用同一 token。
  - 控制面方法均 gate on `running`：`assignTasks()`（`:282-292`）、`triggerCheckpoint()`（`:390-394`）、`collectAck()`（`:440-450`，额外校验 `fencingToken != null` 且 `token.equals(ack.getFencingToken())`）。
  - 构造器（`:163-184`）入参：`(jobId, coordinatorId, deploymentPlan, clusterRegistry, checkpointCoordinator, taskRpcServices)`，无 elector。
- **`JobCoordinator` 构造点**：生产 `EmbeddedDistributedExecutor:150-168`（单实例，`fencingToken = UUID.randomUUID()` `:104`，`setFencingToken` `:155`）；测试 4 处（`TestJobCoordinatorAssignmentFromPlan` / `TestJobCoordinator` / `TestCoordinatorRpcControlPlane` / `TestJobCoordinatorPerTaskFailure` / `TestJobCoordinatorAttemptTracking` / `TestJobCoordinatorRestartStrategy`）。新增 elector 必须向后兼容（重载/可选 setter），不破坏既有测试。
- **fencing 已贯通控制面 + 数据面**（但 token 是随机 UUID，非 leadership epoch）：控制面 `TaskAssignment.fencingToken`（assignTasks `:340-343`）、`CheckpointBarrierSignal.fencingToken`（triggerCheckpoint `:416`）、`collectAck` 校验（`:453-457`）；数据面 `RemoteInputChannel`/`RemoteResultPartition`/`StreamMessageEnvelope` 按 `fencingToken + epochId` 过滤 stale envelope。TaskManager 侧 `IStreamTaskRpcService.updateFencingToken(token)` 接受 token 更新。
- **不存在内存/测试用 leader elector**：仓库仅有 `AbstractLeaderElector` / `AbstractPollingLeaderElector` / `SysDaoLeaderElector`。`SysDaoLeaderElector` 依赖 JDBC 轮询，单测直接用会过重/过慢——本 plan 需提供一个测试可控的 `ILeaderElector` 双控（test double），可确定性 grant/revoke leadership 并发射 `LeaderEpoch`。
- **`00-vision.md` §九** 已定义三面分离（控制面 `IStreamCoordinatorRpcService`），§八 不变量 #8「旧 coordinator 必须被 fencing」——HA 正是该不变量的落地，无需改 vision。

## Goals

- **G24 leader 选举 WIRE**：`JobCoordinator` 在 HA 模式下经 `ILeaderElector` 竞选 leader，当选后激活控制面，落选保持 standby。elector 可注入（`null` = 嵌入式/local 模式，保持当前随机 UUID 行为，零回归）。
- **G25 standby coordinator + fencing-on-leadership**：standby 实例存活但不执行控制面动作（不 assignTasks/triggerCheckpoint/collectAck）；leadership grant 时激活并以当选 `LeaderEpoch` 作为 fencing；leadership loss 时去活（停止控制动作）。
- **解耦 recovery fencing 与 leadership fencing**：HA 模式下 leadership epoch 只在 leadership 切换时轮转；`globalRecovery()`（同一 leader 内的作业重启）不再轮转 leadership token（Decision 记录具体方案）。
- **平台集成回传**：作为 `SysDaoLeaderElector` 首个生产用户，把集成问题（lease 时长、epoch 单调性、`ILeaderElectionListener` 契约等）回传 `nop-sys-dao`。

## Non-Goals

- **跨 JVM RPC 传输**（`IStreamTaskRpcService` 经 `MessageRpcServer`/`SimpleRpcServer` 远程暴露，`RpcServiceProxyFactoryBean` + `ClusterRpcClient` 远程代理）→ Stage 39。本 plan 的 leader-switch E2E 在**单进程**内用两个 coordinator 实例 + 测试 elector 模拟。
- **fencing token 表示统一为单调 long epoch**（全链路 String→long）→ Stage 39。本 plan 保留 String 表示（`leaderId@epoch` 编码），避免 churn 数据面过滤路径。
- **完整 HA 测试矩阵**（脑裂、commit uncertainty、fencing edge case 全覆盖、多 JVM kill/restart）→ Stage 46。本 plan 交付 leader-switch 基本路径 + fencing 拒绝 stale-epoch 控制。
- **HA checkpoint store / `CompletedCheckpointStore` 冗余**（G32/G35）→ Stage 46。
- **Coordinator HA across physical JVMs**（进程编排、端口/主题分配）→ Stage 42 测试基建。
- **`ClusterRegistry` 收敛到平台 discovery**（Stage 41）。

## Scope

### In Scope

- `JobCoordinator` 接入 `ILeaderElector`（可选注入）；leader-gated 生命周期（start/stop/active/standby 状态机）。
- fencing token 在 HA 模式来自当选 `LeaderEpoch`（String 编码，如 `leaderId@epoch`）。
- 解耦 recovery fencing vs leadership fencing 的 Decision + 实现。
- standby 行为：非 leader 时控制面方法拒绝/休眠；leadership grant/loss 激活/去活。
- 测试可控 `ILeaderElector`（test double，确定性 grant/revoke）。
- leader-switch 单进程 E2E（两 coordinator + 测试 elector）+ fencing 拒绝 stale-epoch 控制。
- 平台 `SysDaoLeaderElector` 集成 smoke check + 问题回传 `nop-sys-dao`。

### Out Of Scope

- 跨 JVM RPC 传输、String→long epoch 统一（Stage 39）。
- 完整 HA 测试矩阵、HA checkpoint store（Stage 46）。
- 多 JVM 进程编排测试基建（Stage 42）。
- `ClusterRegistry` 平台 discovery 收敛（Stage 41）。

## Execution Plan

### Phase 1 - ILeaderElector WIRE 进 JobCoordinator 生命周期 + fencing-on-leadership + 测试 elector

Status: planned
Targets: `JobCoordinator.java`（构造器重载或可选 setter 注入 `ILeaderElector`；`start()`/状态机/fencing 来源）；新增测试用 `ILeaderElector` 双控（`nop-stream-runtime` test 目录）；`EmbeddedDistributedExecutor.java`（**保持单实例非 HA**，仅作 local 模式 carrier，不注入 elector；HA 注入点由分布式 bootstrap/部署期 beans.xml 承担，本 plan 用测试 elector 验证）；向后兼容既有 6 个测试构造点

- Item Types: `Fix | Decision | Proof`

> **HA 生命周期状态机（闭合 B1）**：`start()` 注册 `addElectionListener` 后**立即返回**，coordinator 进入 **STANDBY 初态**（非 RUNNING）。状态转换由 election listener 回调驱动：
> - `becomeLeader(epoch)` 回调（本节点当选）→ 转 **ACTIVE**，派生 fencing token（见下），注册 ClusterRegistry，进入相当于 RUNNING 的控制面就绪
> - `becomeFollower(epoch)` 回调（本节点落选或被抢leader）→ 转/保持 **STANDBY**（`SysDaoLeaderElector` 在异常/续期失败路径会传 `onBecomeFollower(null)`，`onStop()` 默认也调 `becomeFollower(null)`——`null` epoch 同样按 STANDBY 安全降级处理，不改变动作）
> - **关键**：`whenElectionCompleted()` **不等于** ACTIVE 转换条件——它只表示「本轮选举已出结果」，结果可能是别人当选（见 `AbstractLeaderElector.onElectionCompleted` 在 follower 路径也触发）。实现**禁止**用 `whenElectionCompleted().thenRun(running=true)`，否则 follower 会误入 ACTIVE，破坏不变量 #8。`whenElectionCompleted()` 仅可用作「初始角色已确定」的同步点（如启动期等待）
> - 非 HA 模式（elector == null）：`start()` 保持既有行为（生成随机 UUID + 进 RUNNING），`active` 标志恒为 `true`（不受 standby gate 约束），零回归

- [ ] **Decision（fencing 来源与编码）**：HA 模式 fencing token 来源裁定——(a) 用 `LeaderEpoch.leaderId@epoch`（叠加 recovery generation，见下）字符串作为 token，或 (b) 引入 `LeaderFencingToken` 值对象封装并提供 `toString()`。推荐 (a)，最小改动且 Stage 39 再统一为 long。Decision 记录：非 HA 模式（elector == null）保持当前随机 UUID 行为零回归
- [ ] **Decision（recovery fencing vs leadership fencing 解耦，闭合 M6 表述）**：当前 `globalRecovery()`（`:661-663`）每次恢复轮转整个 token。HA 模式采用 **composite token = `leaderId@epoch#recoveryGen`**：
  - **leadership epoch 组件（`leaderId@epoch`）仅在 leadership 切换时变化**（由 elector grant 新 `LeaderEpoch` 驱动）
  - **recovery generation 组件（`#recoveryGen`）在每次 `globalRecovery()` 内递增**（同一 leader 内的作业重启）
  - **完整 composite token 每次 `globalRecovery()` 仍须轮转并经 `updateFencingToken` 推送给所有 TaskManager**（`:674-676` 行为保留）——否则上一轮 recovery 的 stale task 不被 fencing
  - 即「token 仅在 leadership 切换轮转」指的是 **epoch 部分**；recovery 仍轮转 **recoveryGen 部分**并推送完整 token。此方案同时满足两个 fencing 需求：stale leader 旧 token（epoch 不同）被拒 + 同一 leader 内上一轮 recovery task（recoveryGen 不同）被拒
  - **删除原 option (b)**（`globalRecovery` 不轮转 token，靠 attempt number fencing）——数据面当前按 `fencingToken + epochId` 字符串等值过滤，**不按 attemptNumber 过滤**，option (b) 需改数据面过滤逻辑，越出本 plan scope（属 Stage 39），故排除
- [ ] `JobCoordinator` 增加可选 `ILeaderElector leaderElector` 字段 + 注入（构造器重载或 setter）；`null` 时走既有随机 UUID 路径
- [ ] HA 模式 `start()`：注册 `addElectionListener`，**立即返回进 STANDBY**；`becomeLeader(epoch)` 回调 → 转 ACTIVE，从 `LeaderEpoch` 派生 composite token 初值（`recoveryGen=0`），注册 ClusterRegistry；`becomeFollower` 回调 → STANDBY。**禁止用 `whenElectionCompleted()` 作为 ACTIVE 条件**
- [ ] HA 模式 `globalRecovery()` 改造：`recoveryGen++` 并轮转完整 composite token（保留 `:666` 注册 + `:674-676` `updateFencingToken` 推送），epoch 部分不变
- [ ] **线程安全**：active/standby 状态在 elector 回调线程翻转，控制面方法在其他线程读取——状态字段用 `volatile`/`AtomicReference` 保证可见性（明确写出，避免实现者遗漏）
- [ ] 提供测试可控 `ILeaderElector` 双控（实现 `ILeaderElector` 全部方法：`getHostId`/`getLeaderEpoch`/`addElectionListener`/`whenElectionCompleted`/`restartElection`/`isLeader`），可被测试确定性 `grantLeadership(hostId, epoch)` / `revokeLeadership()`，同步触发 `ILeaderElectionListener`。仅 test scope，非生产组件
- [ ] 既有构造点零回归：`EmbeddedDistributedExecutor` 及 6 个测试在 elector==null 时行为不变（`TestJobCoordinator` / `TestJobCoordinatorAssignmentFromPlan` / `TestJobCoordinatorAttemptTracking` / `TestJobCoordinatorRestartStrategy` / `TestJobCoordinatorPerTaskFailure` / `TestCoordinatorRpcControlPlane` 全绿）

Exit Criteria:

- [ ] HA 模式下 `JobCoordinator.start()` 立即返回且 coordinator 处于 STANDBY（未当选不 ACTIVE，断言）；`becomeLeader` 回调后才转 ACTIVE 且 fencing token 为 `LeaderEpoch` 派生（断言 token 含当选 epoch，非随机 UUID）
- [ ] **`whenElectionCompleted()` 语义验证**：测试构造「选举结果为本节点落选」场景，断言本节点保持 STANDBY（不因 election completed 误入 ACTIVE）——这是 B1 的核心防护测试
- [ ] 非 HA 模式（elector==null）行为零回归：上述 6 个测试全绿
- [ ] **接线验证**：`addElectionListener` 在运行时确实被调用（测试 elector grant → coordinator 收到回调 → 状态转换，计数器/标志位断言）
- [ ] **无静默跳过**：HA 模式下 elector 注入但本节点处于 STANDBY 时，`assignTasks`/`triggerCheckpoint` 显式拒绝（抛异常或返回失败），不静默 no-op
- [ ] 测试 elector 双控可用（grant/revoke 确定性触发 listener），单测覆盖
- [ ] **新增功能测试**：leader-gated start（STANDBY 初态 + becomeLeader 转 ACTIVE）、`whenElectionCompleted` 落选不 ACTIVE、fencing 来自 LeaderEpoch 三条核心行为各有 focused test
- [ ] `ai-dev/design/nop-stream/` 记录 leader-election WIRE 决策（HA 生命周期状态机、fencing composite token 方案、recovery vs leadership fencing 解耦）；若仅 CoordinatorInfo/ClusterRegistry 无语义变更则对相应 owner doc 写 `No owner-doc update required`
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Standby coordinator 状态机（active/standby + leadership-loss 去活 + grant 激活）

Status: planned
Targets: `JobCoordinator.java`（active/standby 状态、控制面方法 gate、`globalRecovery` HA 改造、deactivate vs stop 区分）；`ILeaderElectionListener` 回调处理；`assignTasks`/`triggerCheckpoint`/`collectAck`/`reportTaskStatus`/`reportNodeTaskLiveness` 入口

- Item Types: `Fix | Proof`

- [ ] 引入 active/standby 状态（在 `running` 之上，`volatile`/原子）：`becomeLeader` 回调 → active；`becomeFollower` 回调 → standby
- [ ] standby 时控制面方法**显式拒绝**（闭合 M5）：`assignTasks`（`:282`）、`triggerCheckpoint`（`:390`）、`collectAck`（`:440`）在 standby 状态显式拒绝（日志 + 返回失败/抛异常），不静默执行
- [ ] **`reportTaskStatus`（`:486-491`）/ `reportNodeTaskLiveness`（`:546`）当前是静默跳过**（`!running` 时 debug log + `return`，正是 Rule #24 禁止模式）：HA standby 下必须改为**显式拒绝/记录**（不能静默吞掉 task 状态上报），否则 standby coordinator 静默忽略 task 状态，fencing 边界出漏洞
- [ ] **leadership-loss 去活 ≠ stop()（闭合 M2）**：`stop()`（`:225-236`）调用 `failureDetector.shutdownNow()`（`:231`）+ `checkpointCoordinator.shutdown()`（`:233`）**不可逆**。standby 去活**不调用 `stop()`**，而是翻转一个可逆的 active 标志位（控制面方法 gate 于此），**保留** failure detector 线程与监听以便重新当选；in-flight checkpoint 不再 commit。只有作业真正终止（CANCEL/FAIL）才调 `stop()`
- [ ] **`globalRecovery()` HA 模式交互（闭合 M1）**：`detectFailures()`（`start()` 调度）→ `globalRecovery()`（`:626`）。HA active 模式下 `globalRecovery()` 仍可触发，但其内部按 Phase 1 composite-token 方案执行：`recoveryGen++`、轮转完整 composite token、保留 `:666` 注册 + `:674-676` `updateFencingToken` 推送 + `:696` `assignTasks`。standby 模式下 `detectFailures` 触发的 recovery 应被 active gate 拦截（standby 不主导 recovery）。明确：HA 模式不新建独立 recovery 路径，而是在既有 `globalRecovery` 上叠加 recoveryGen 语义 + active gate
- [ ] leadership-grant 激活：收到 grant 回调 → 转 active，派生新 fencing token（新 epoch + `recoveryGen=0`），从 latest checkpoint 重建控制面工作集（复用 `globalRecovery` 的 restore 段 `:678-696`，但 epoch 部分来自新 leadership 而非随机 UUID）
- [ ] **stale-epoch 控制拒绝**：standby/旧 leader 发出的控制（携带旧 token）被 task 侧拒绝——复用现有 `collectAck` token 校验（`:453-457`）+ TaskManager `updateFencingToken` 机制；验证旧 token 的 `TaskAssignment`/`CheckpointBarrierSignal` 不被接受

Exit Criteria:

- [ ] standby 实例的 `assignTasks`/`triggerCheckpoint`/`collectAck`/`reportTaskStatus`/`reportNodeTaskLiveness` 被显式拒绝（断言拒绝且非静默 no-op——区别于当前 `reportTaskStatus` 的 debug-log+return 静默模式）
- [ ] leadership-loss → 翻转 active 标志转 standby（断言状态转换 + 控制面停止），**且 failure detector 未被 shutdown**（断言可重新当选）；重新 grant → 转 active（断言 detector 仍存活、从 latest checkpoint 重建工作集）
- [ ] **deactivate vs stop 区分验证**：leadership-loss 后 `stop()` 未被调用（failure detector 线程存活）；只有终止模式才调 `stop()`
- [ ] **`globalRecovery` HA 交互验证**：active 模式下 `globalRecovery` 用 composite token（recoveryGen 递增、epoch 不变）并推送；standby 模式下不主导 recovery
- [ ] **接线验证**：election listener 的 grant/loss 回调在运行时确实驱动 active/standby 转换（标志位/计数器断言）
- [ ] **端到端验证（leader-switch）**：两 coordinator 实例共享测试 elector——grant A（A active, B standby）→ revoke A + grant B（A standby, B active with 新 epoch）→ A 的旧 token 控制被 B 的新 epoch 体系拒绝
- [ ] **无静默跳过**：standby 下控制面方法（含 `reportTaskStatus`/`reportNodeTaskLiveness`）显式失败；revoke 回调缺失/异常时转 standby 安全降级（不静默继续 active）
- [ ] **新增功能测试**：standby 拒绝控制面（含 reportTaskStatus 非静默）、leadership-loss 去活（detector 存活）、grant 激活重建工作集、globalRecovery HA composite-token 各有 focused test
- [ ] `ai-dev/design/nop-stream/` 记录 active/standby 状态机、deactivate-vs-stop、stale-epoch fencing 契约
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 平台 SysDaoLeaderElector 集成 smoke check + 问题回传

Status: planned
Targets: `nop-sys-dao`（`SysDaoLeaderElector`）；集成验证（AutoTest JDBC 或部署期 beans.xml 注入）；问题回传记录

- Item Types: `Proof | Follow-up`

- [ ] **集成 smoke check（真实 JDBC 为默认路径，闭合 M3 hollow 风险）**：在 nop-stream 集成测试（AutoTest H2 或部署期 beans.xml 注入）中用**真实 `SysDaoLeaderElector`**（JDBC 后端）验证 HA 模式可启动、可竞选、lease 续期正常。参照仓库已有的 `nop-sys-dao` 测试 `TestDaoLeaderElector`（证明 JDBC 选举可测）。**逃生门设高门槛**：仅当 H2 AutoTest 真实尝试失败后才允许以测试 elector 的语义等价单测替代，且必须记录失败原因——不可默认走 mock（否则 Phase 3 退化为 Phase 1 已做的事，「首个生产用户集成回传」Goals 落空）
- [ ] **平台问题回传**：作为 `SysDaoLeaderElector` 首个生产用户，把集成发现的问题（lease 时长默认、`ILeaderElectionListener` 回调线程模型、epoch 单调性保证、`restartElection()` 语义等）记录到 `ai-dev/logs/` 或 `ai-dev/analysis/`；如需修改 `nop-sys-dao`，单独 issue/plan（本 plan 不改 nop-sys-dao 源码）
- [ ] 验证 HA 模式 fencing 与既有数据面 `RemoteInputChannel`/`RemoteResultPartition` token+epochId 过滤兼容（token 表示仍为 String，应零改动；显式断言不回归）

Exit Criteria:

- [ ] `SysDaoLeaderElector` **真实 JDBC** 集成 smoke check 通过（HA 模式可正常竞选/续期）；若走逃生门，记录 H2 尝试失败原因 + 测试 elector 等价覆盖范围
- [ ] 平台集成问题已记录（`ai-dev/logs/` 或 `ai-dev/analysis/`），需 nop-sys-dao 变更的项已拆分为独立 follow-up（非本 plan 范围）
- [ ] 数据面 fencing 过滤零回归（既有 `RemoteInputChannel`/`RemoteResultPartition` 测试全绿）
- [ ] **无静默跳过**：HA 模式下 elector 启动失败/无法竞选时 fail-fast（不静默退化为单实例）
- [ ] `ai-dev/design/nop-stream/`（architecture-baseline 或新增 ha-design）记录 HA 部署形态（控制面 leader election + 数据面 IMessageService）；`source-anchors.md` 视需要补 leader 选举锚点
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 通过（若集成测试跨模块，扩展 `-pl` 范围）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] G24 leader 选举 WIRE 落地（JobCoordinator leader-gated，elector 可注入，非 HA 模式零回归）
- [ ] G25 standby coordinator + fencing-on-leadership 落地（active/standby 状态机、leadership grant/loss 激活/去活、stale-epoch 控制被拒绝）
- [ ] recovery fencing vs leadership fencing 解耦 Decision 有结论且实现一致
- [ ] leader-switch 单进程 E2E 通过（两 coordinator + 测试 elector）
- [ ] 平台 SysDaoLeaderElector 集成 smoke check 通过，集成问题已记录/回传
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 owner-doc drift
- [ ] 受影响 owner docs（`ai-dev/design/nop-stream/` HA 相关）同步到 live baseline
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：election listener 回调在运行时确实驱动 active/standby（非 stub）；standby 拒绝非静默；无空方法体/静默跳过
- [ ] `./mvnw compile -pl nop-stream/nop-stream-runtime -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime -am`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本 plan> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 跨 JVM leader-switch 与多 JVM kill/restart E2E

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 多 JVM 进程编排测试基建属 Stage 42；本 plan 的 leader-switch 语义由单进程两实例 + 测试 elector 等价覆盖（leadership 状态机、fencing 轮转、stale 拒绝不依赖物理 JVM）。跨 JVM 传输本身属 Stage 39
- Successor Required: `yes`
- Successor Path: Stage 39（跨 JVM RPC）+ Stage 42（多 JVM 测试基建）+ Stage 46（完整 HA 矩阵）

### fencing token 表示统一为单调 long epoch

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 用 `LeaderEpoch` 派生 String token 已满足「leadership 切换 = fencing 轮转」语义（不变量 #8）；全链路 String→long 统一是表示层清理，属 Stage 39
- Successor Required: `yes`
- Successor Path: Stage 39（fencing token 统一）

## Non-Blocking Follow-ups

- HA checkpoint store / `CompletedCheckpointStore` 冗余（G32/G35，Stage 46）
- 完整 HA 测试矩阵：脑裂、commit uncertainty、fencing edge case（Stage 46）
- `ClusterRegistry` 收敛到平台 discovery（Stage 41）
- 如 Phase 3 发现需修改 `nop-sys-dao`，单独立 plan（不在本 plan 范围）

## Closure

Status Note: <<完成时填写>>
Completed: <<未完成>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<执行后填写>>

Follow-up:

- <<完成时填写；或明确写 no remaining plan-owned work>>
