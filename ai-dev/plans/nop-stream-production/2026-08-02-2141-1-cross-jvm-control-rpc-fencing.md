# 39 控制面跨 JVM RPC 暴露 + fencing token String→long epoch 统一 + 分布式 abort path

> Plan Status: completed
> Last Reviewed: 2026-08-02
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 39（Work Item 39，`todo`）；`ai-dev/design/nop-stream/01-architecture-baseline.md` §五（line 227–374）；`ai-dev/design/nop-stream/checkpoint-design.md` §8.7（line 913–937）+ §13.2（line 1108–1121）
> Related: `2026-08-02-0955-8-leader-election-ha.md`（Stage 38，已完成，本 plan 前置）；`2026-07-26-0433-1-rpc-dispatcher-backpressure.md`（Stage 28，local 契约已扩容，远程化留给本 plan）
> Review Consensus: 两轮独立子 agent 对抗性审查通过（无 Blocker；round-2 可执行性评分 高）。review task IDs: ses_03d478362ffeHdE598puMy71Jb（r1）、ses_03d3d9045ffelgCrmavAoFHSTK（r2）。

## Purpose

把 nop-stream 控制面从「单 JVM 直接 Java 方法引用」推进到「经平台 RPC 框架（`MessageRpcServer`/`SimpleRpcServer` + `RpcServiceProxyFactoryBean`/`ClusterRpcClient`）跨 JVM 传输」，同时把 fencing token 从复合 String（`leaderId@epoch#recoveryGen`）统一为基于 `LeaderEpoch` 的单调 long epoch，并接通 checkpoint 超时 abort 的 distributed 路径（coordinator abort listener → `cancelTask` RPC → remote task）。完成后 Stage 40（数据面跨 JVM）与 Stage 42（多 JVM 测试基建）的前置解除。

## Current Baseline

基于 live repo 核对（2026-08-02）：

**已成立（控制面契约 + local 实现）：**
- `IStreamTaskRpcService`（4 方法：`receiveAssignment`/`triggerCheckpoint`/`cancelTask`/`updateFencingToken`）位于 `nop-stream-runtime/.../rpc/IStreamTaskRpcService.java`，`@Internal`。
- `IStreamCoordinatorRpcService`（6 方法：`receiveCheckpointAck`/`reportTaskStatus`/`reportNodeTaskLiveness`/`terminate`/`abortCheckpoint`/`getJobStatus`）位于 `nop-stream-runtime/.../rpc/IStreamCoordinatorRpcService.java`，其 Javadoc（约 line 51–53）明确写「cross-JVM callers (Stage 39) will reach the same implementation via a generated RPC proxy」。
- local 实现：`TaskManager implements IStreamTaskRpcService`（`taskmanager/TaskManager.java:73`，`cancelTask` impl `:366`，`updateFencingToken` impl `:415`）；`JobCoordinator implements IStreamCoordinatorRpcService`（`coordinator/JobCoordinator.java:79`）。
- Stage 28 已扩容控制面方法并明确把远程代理/`MessageRpcServer`/`RpcServiceProxyFactoryBean` 接线留给 Stage 39（`2026-07-26-0433-1-rpc-dispatcher-backpressure.md:51,145`）。

**已成立（HA / fencing 当前表示，Stage 38）：**
- `JobCoordinator` 已 WIRE 平台 `ILeaderElector`/`SysDaoLeaderElector`（`coordinator/JobCoordinator.java:104`，HA 生命周期状态机 STANDBY→ACTIVE，`start()` line 247–305）。
- fencing token 当前为**复合 String**：`fencingToken = AtomicReference<String>`（`:97`），`deriveHaFencingToken(LeaderEpoch, recoveryGen)` 编码为 `"{leaderId}@{epoch}#{recoveryGen}"`（`:876-877`），`recoveryGen = AtomicLong`（`:123`）。`rotateFencingTokenAndRestore()`（`:836-869`）经 `rpc.updateFencingToken(newToken)` 推送给所有 `taskRpcServices`（`:849-851`）。
- 控制面 message types 已携带 fencing：`TaskAssignment`、`CheckpointBarrierSignal`、`CheckpointAckMessage`、`TaskStatusReport`（均 `@DataBean`，含 `fencingToken` 字段）。

**已成立（数据面 envelope 已有双键过滤）：**
- `StreamMessageEnvelope`（`nop-stream-core/.../execution/transport/StreamMessageEnvelope.java`）。
- `RemoteResultPartition`（`runtime/transport/RemoteResultPartition.java`）持有 `fencingToken`（String，`:58`）+ `epochId`（long，`:59`）；`RemoteInputChannel`（`runtime/transport/RemoteInputChannel.java`）**双键过滤**：String fencingToken 等值（`:213`）**且** long epochId 等值（`:220`），不匹配 silent discard。
- `RemoteGraphExecutionPlanBuilder`（`runtime/transport/RemoteGraphExecutionPlanBuilder.java:62-63`）同样持 `fencingToken`(String)+`epochId`(long)。
- **⚠️ 生产路径 long 键当前 inert**：`RemoteGraphExecutionPlanBuilder` 唯一生产调用点 `EmbeddedDistributedExecutor.java:168` 把 `epochId` 硬编码为 `0`。故生产「双键过滤」实际偏斜——long 键恒为 `0==0`（no-op），**String 键承担全部 fencing**；long 键仅被测试（`TestRemoteDataExchange` 用 `1L`/`2L`）激励。Phase 1 的「收敛」在生产上是**首次让 long 键有意义（或彻底删除它）**，属真实行为变更而非 no-op 重构。

**已成立（平台 RPC 基建可复用，但 nop-stream 内零引用）：**
- `MessageRpcServer`（`nop-rpc-core/.../message/MessageRpcServer.java`，RPC-over-message-queue，`extends LifeCycleSupport`）、`SimpleRpcServer`（`nop-rpc-simple/.../SimpleRpcServer.java`，socket-based）。
- `RpcServiceProxyFactoryBean`（`nop-rpc-core/.../reflect/RpcServiceProxyFactoryBean.java`，client 代理工厂）、`ClusterRpcClient`（`nop-rpc-cluster/.../ClusterRpcClient.java`，集群发现 + 重试，`implements IRpcService`）、`IRpcService`（`nop-api-core/.../rpc/IRpcService.java`）。
- grep 确认：`MessageRpcServer|RpcServiceProxyFactoryBean|ClusterRpcClient|MessageRpcClient` 在整个 `nop-stream/` 下零文件引用。

**已成立（local abort path）：**
- `GraphModelCheckpointExecutor.registerLocalAbortHandler()`（`execution/GraphModelCheckpointExecutor.java:714-744`）在 5 条 execute 路径（`:123,189,262,325,387`）注册：abort → `tracker.notifyCheckpointAborted` + `mailbox.signalCancel()` + `inputGate.resumeConsumptionAll()` + `task.cancel()`。捕获的 `tasks` map 是 **coordinator-JVM 内**的 tasks，跨 JVM 时失效。

**真正剩余 gap（本 plan 范围）：**
1. **无 RPC server/proxy/transport-adapter**：`rpc/` 包仅两个纯接口文件，无任何 server/proxy/transformer。
2. **无 IoC wiring**：整个 `nop-stream` 无 `beans.xml`/`_service.beans.xml`（grep 确认），`EmbeddedDistributedExecutor.execute()`（`execution/EmbeddedDistributedExecutor.java:100-240`）构造 N 个 in-process `TaskManager`，把 `taskRpcServices` map **直接**注入 `JobCoordinator`（`:150-153`），并把 coordinator 直接 `tm.setCoordinatorRpcService(coordinator)`（`:162-164`）。所有「RPC」调用都是同 heap 的 Java 方法调用；`execute()` 返回时 `coordinator.stop()`（`:221`），coordinator 无长生命周期。
3. **fencing 仍为复合 String**：数据面/控制面过滤依赖 String 等值（line 848 注释明示 "filter on String fencingToken equality"）。`01-architecture-baseline.md:356` 明确「全链路 String→long epoch 统一属 Stage 39，本节 fencing 表示仍为 String」。
4. **distributed abort 未接通**：`JobCoordinator` 从不调用 `taskRpcServices.get(nodeId).cancelTask(...)`（grep 确认 coordinator 无 cancelTask 调用点；仅 `TaskManager` impl + test fakes）。checkpoint-design §8.7 line 937 明确 deferred：「abort 接线的 distributed 部分（`IStreamTaskRpcService` 新增 `cancelTask` RPC + `JobCoordinator` 注册 abort listener）作为 Deferred」。**注**：该 design 措辞 stale——`cancelTask` 已由 Stage 28 加入接口与 `TaskManager` 实现，本 plan 只需**接通 coordinator 调用点**（非新增方法）；Phase 3 同步修正该 design 措辞。
5. **`CancelCheckpointMarker` 类不存在**：grep 全仓 `.java` 零 `class CancelCheckpointMarker`，仅在 docs/plans/analysis 中作为 deferred 概念出现（roadmap line 129,226,228）。G5/G34 ID 在 design docs 中不字面出现，对应概念为 `component-roadmap.md:189`（abort 传播通道缺口）+ checkpoint-design §13.2 line 1116（abort 必须有独立控制通道契约）。

## Goals

- 控制面控制调用（`receiveAssignment`/`triggerCheckpoint`/`cancelTask`/`updateFencingToken`/`reportTaskStatus`/`receiveCheckpointAck` 等）能经**真实 RPC 传输**（`MessageRpcServer` 或 `SimpleRpcServer`）到达对端实现，而非直接 Java 引用。
- `JobCoordinator` 通过 `RpcServiceProxyFactoryBean` + `ClusterRpcClient`（或 `MessageRpcClient`）获得远程 `IStreamTaskRpcService` 代理；引入分布式 dispatcher 形态使 coordinator 具备长生命周期并按 nodeId 路由到远程 task。
- 引入 nop-stream 首个 IoC wiring（`beans.xml`）装配 RPC server/proxy。
- fencing token 在控制面与数据面统一为基于 `LeaderEpoch` 的**单调 long epoch** 比较，保留两个 fencing 不变量（stale-leader 拒绝 + 同 leader 内上一轮 recovery 拒绝）。
- distributed abort path 接通：coordinator 注册 abort listener，对远程 task 经 `cancelTask` RPC 触发取消，闭合 checkpoint-design §13.2 line 1116「abort 信号必须有独立于数据流的控制通道」契约的 distributed 部分。
- G5（`CancelCheckpointMarker`）/ G34（abort 跨 JVM 数据 channel 传播）在本 plan 内裁定：实现、或裁定为 Decision-only（无消费方则不引入空壳类，遵循 plan guide #24）。

## Non-Goals

- 真正的**多 JVM 进程编排**（启动 N 个 TaskManager JVM + 1 个 coordinator JVM、端口/主题分配、进程级 kill/restart、CI 集成）——Stage 42。本 plan 的 E2E 用真实 RPC 传输（`MessageRpcServer` over `IMessageService`，可使用 `LocalMessageService` 或 H2-backed `SysDaoMessageService` 作传输介质）在同一 JVM 内验证 RPC 层确被穿越，区分于直接方法引用。
- **数据面 IMessageService 跨 JVM 接线**（`RemoteResultPartition`/`RemoteInputChannel` 注入 `SysDaoMessageService`/`PulsarMessageService` 真实后端）——Stage 40。本 plan 只动数据面 envelope 的 **fencing 字段表示**，不动传输后端接线。
- **unaligned checkpoint**（G6）——Stage 43。
- **region-based failover**（G28 续/G57）——Stage 44（需 5 项架构前置，见 `failover-design.md` §五）。
- **ClusterRegistry → 平台 discovery 收敛**（D7 决策点「完全替换 vs 对接」需人确认）——Stage 41。
- 多并发 checkpoint 完整多 epoch 追踪——Stage 45（Stage 19 已落基础）。
- 移植 Flink Netty 网络栈 / credit-based / ACK_WINDOW（vision §三 约束 7 永久排除，Stage 28 已删除 `CREDIT_BASED`/`ACK_WINDOW`）。

## Scope

### In Scope

- 控制面 RPC server adapter（基于 `MessageRpcServer`/`SimpleRpcServer`）暴露 `IStreamTaskRpcService`（task 侧）；可选同途径暴露 `IStreamCoordinatorRpcService`（coordinator 侧，用于 task→coordinator 上行）。
- client 远程代理（`RpcServiceProxyFactoryBean` + `ClusterRpcClient`/`MessageRpcClient`）使 coordinator 持有远程 `IStreamTaskRpcService`、task 持有远程 `IStreamCoordinatorRpcService`。
- 分布式 dispatcher 形态：长生命周期 coordinator + 按 nodeId 路由的远程 proxy 注入（替代 `EmbeddedDistributedExecutor` 直接 map 注入），兑现 `IStreamExecutionDispatcher` Javadoc（line 34）「异步 submit + poll dispatcher deferred to Stage 39」。
- nop-stream 首个 `beans.xml`（`_service.beans.xml` 或 module beans）装配 RPC server + proxy bean。
- fencing token String→long epoch 统一：`JobCoordinator` 派生、控制面 message types 字段、数据面 envelope 字段（`RemoteResultPartition`/`RemoteInputChannel`/`RemoteGraphExecutionPlanBuilder`/`StreamMessageEnvelope`）、`updateFencingToken` RPC 签名与传播。
- distributed abort path：coordinator abort listener → 远程 `cancelTask` RPC；与既有 local abort path（`registerLocalAbortHandler`）关系裁定（local 路径仍为同 JVM fast-path 或收敛为统一路径）。
- G5/G34 裁定与（如裁定需要）`CancelCheckpointMarker` 作为已恢复 channel 的补充通知（非主 abort 机制，主 abort 仍为控制通道，见 checkpoint-design §8.7/§13.2）。

### Out Of Scope

- 见 Non-Goals 全部条目。
- 数据面 envelope 的**传输后端**接线（Stage 40）。
- 跨 JVM SST 文件传输（Stage 40）。
- 改变三面架构划分（控制面/数据面/编排面）。

## Execution Plan

### Phase 1 - fencing token String→long epoch 统一

Status: completed
Targets: `nop-stream-runtime/coordinator/JobCoordinator.java`、`nop-stream-runtime/transport/RemoteResultPartition.java`、`nop-stream-runtime/transport/RemoteInputChannel.java`、`nop-stream-runtime/transport/RemoteGraphExecutionPlanBuilder.java`、`nop-stream-core/execution/transport/StreamMessageEnvelope.java`、控制面 message types（`TaskAssignment`/`CheckpointBarrierSignal`/`CheckpointAckMessage`/`TaskStatusReport`）、**cluster registry 持久化层（`ClusterRegistry.java:25,86,94` + `CoordinatorInfo.java:21` + `TaskAssignment.java:24` + `JdbcClusterRegistry`/`InMemoryClusterRegistry` + `fencing_token VARCHAR(255)` DDL）**、`IStreamTaskRpcService.updateFencingToken`、`ai-dev/design/nop-stream/01-architecture-baseline.md`、`ai-dev/design/nop-stream/checkpoint-design.md`

- Item Types: `Fix`、`Decision`、`Proof`

- [x] **Decision**：确定单调 long epoch 表示方案——单一组合单调计数器（leadership 切换与 recovery 均递增）vs.（leaderEpoch long + recoveryGen long）双 long 复合——并在 `checkpoint-design.md` §fencing 记录决策与保留的两个不变量证明（stale-leader 拒绝 + 同 leader 上一轮 recovery 拒绝）。决策须回答：数据面过滤从「String 等值 + long epochId 等值」双键收敛为「单 long epoch 比较」后，两个不变量如何同时成立。
- [x] **Decision（持久化边界表示）**：裁定 `ClusterRegistry` 持久化层的 fencing 表示——(A) 迁移签名 + DDL 为 long（`fencing_token VARCHAR(255)`→`BIGINT`，影响已部署库，需迁移脚本）vs. (B) 在 `JobCoordinator`→`ClusterRegistry` 边界用 `String.valueOf(longEpoch)` 维持 String 持久化表示（运行时内存 long、持久化 String，边界做转换）。记录于 `checkpoint-design.md` + `01-architecture-baseline.md` §ClusterRegistry（D73）。裁定须与「grep 确认 `JobCoordinator` 不再产生复合 String」Exit Criteria 自洽——选 (B) 时须明确持久化边界 String 是 `String.valueOf(long)` 单值、非复合 `leaderId@epoch#recoveryGen`。
- [x] **Decision（非 HA 模式 epoch 派生）**：当前非 HA 模式（`leaderElector==null`）用随机 UUID（String）。改 long 后须显式裁定非 HA epoch 值派生（如常量 0、或本地单调计数器），保证 Proof (d)「非 HA 零回归」语义成立。
- [x] **Fix**：`JobCoordinator` fencing token 从 `AtomicReference<String>` 改为基于 `LeaderEpoch` 的单调 long 表示；`deriveHaFencingToken` 相应变更；`rotateFencingTokenAndRestore()` 推送 long epoch。
- [x] **Fix**：数据面 envelope fencing 字段与 `RemoteInputChannel`/`RemoteResultPartition`/`RemoteGraphExecutionPlanBuilder` 过滤改按 long epoch 比较（收敛双键为单键或显式说明保留双 long 的理由）。
- [x] **Fix**：控制面 message types（`TaskAssignment`/`CheckpointBarrierSignal`/`CheckpointAckMessage`/`TaskStatusReport`）fencing 字段同步为 long epoch。
- [x] **Fix**：`IStreamTaskRpcService.updateFencingToken` RPC 签名与 `TaskManager` impl 同步为 long epoch；既有 test fakes 同步。
- [x] **Proof**：focused tests 覆盖——(a) stale long epoch envelope 在数据面被拒、current epoch 被收；(b) leadership 切换推进 epoch 后旧 epoch 控制调用被拒；(c) 同 leader 内 `globalRecovery()` 推进 epoch 后上一轮 recovery 的 stale task 被拒；(d) 非 HA 模式（elector==null）零回归（随机/单调行为保持 fencing 有效）。

Exit Criteria:

- [x] 上述 Decision 已写入 `checkpoint-design.md` 并明确两不变量证明。
- [x] grep 确认 `JobCoordinator` 不再产生/比较复合 String fencing token（`leaderId@epoch#recoveryGen` 字面量在 live code 中消失，仅 design doc 作为历史/决策记录保留）。
- [x] 数据面 envelope 过滤为 long epoch 比较（`RemoteInputChannel.java` 原 String 比较条件 `:211` + long 比较条件 `:219` 收敛为单一 long 比较，或显式保留双 long 并在设计文档说明；`:213`/`:220` 为 debug 日志行）。
- [x] focused tests（a–d）全绿，且为**新增**测试（显式断言 stale/current epoch 行为），非仅「原测试通过」。
- [x] **无静默跳过**：fencing 不匹配仍为显式 discard（带 warn/debug 日志），不改为吞异常或空分支（见 plan guide #24）。
- [x] 相关 `ai-dev/design/nop-stream/01-architecture-baseline.md` §Coordinator fencing（line 343–366）+ `checkpoint-design.md` 已更新（line 356「属 Stage 39」标注收敛为「已落地」）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 控制面跨 JVM RPC 暴露 + 远程代理 + IoC 接线

Status: completed
Targets: 新建 `nop-stream-runtime/.../rpc/` 下 server/proxy/transformer 类；`nop-stream-runtime/.../execution/`（distributed dispatcher）；首个 `nop-stream-runtime/.../beans.xml`；`ai-dev/design/nop-stream/01-architecture-baseline.md` §五

- Item Types: `Fix`、`Decision`、`Proof`

- [x] **Decision（接线拓扑设计，先于实现）**：在 `01-architecture-baseline.md` §五 补一段「跨 JVM 控制面接线拓扑」，覆盖：(a) per-nodeId RPC topic/server 命名约定（task 侧暴露 `IStreamTaskRpcService` 的 topic、coordinator 侧暴露 `IStreamCoordinatorRpcService` 的 topic）；(b) coordinator 如何构造并持有「按 nodeId 路由的远程 `IStreamTaskRpcService` proxy map」（替代 `JobCoordinator` 构造器内直接 `taskRpcServices` map，见 `EmbeddedDistributedExecutor.java:150-153`）；(c) RPC server 生命周期归属（task 进程持有 task 侧 server、coordinator 进程持有 coordinator 侧 server）；(d) coordinator 长生命周期如何兑现（`execute()` 返回不再立即 `stop()`，见 `:221`）；(e) 与 `EmbeddedDistributedExecutor` 的关系裁定（新 `IStreamExecutionDispatcher` 实现 vs. 重构既有，兑现 `IStreamExecutionDispatcher.java:34` deferred 契约）。此设计 note 是 Phase 2 其余 Fix 项的前置——无拓扑图则 Fix 项无法落地（闭合 plan guide 历史教训 #8「design 写了 what 没写 how to wire → hollow」）。
- [x] **Decision（`beans.xml` 非空壳裁定）**：nop-stream 首个 `beans.xml` 必须被本 stage **至少一个自动化测试实际加载**，不得沦为 checked-box hollow 工件（plan guide #11/#24）。二选一并记录于 plan：(A) E2E 测试经 NopIoC 容器 bootstrap 加载 `beans.xml` 装配 RPC server/proxy（首选——beans.xml 在被验证路径内）；或 (B) `beans.xml` 为 Stage 42 多 JVM 部署脚手架，本 stage 另加一个 dedicated bean-bootstrap 单测（启动容器、断言 RPC server/proxy bean 实例化与基本连线），确保结构非空壳。裁定须回答：E2E 程序化构造（沿用 `EmbeddedDistributedExecutor` 风格）与 IoC 装配如何共存而不互斥。
- [x] **Decision**：server 传输选型——`MessageRpcServer`（over `IMessageService`，与数据面后端同构，topic 寻址）vs. `SimpleRpcServer`（socket）——并在 `01-architecture-baseline.md` §五 cross-JVM staging table（line 370–374）记录选定方案与拒绝另一方案的理由。决策须考虑：与 Stage 40 数据面 `IMessageService` 后端的统一性、vision §三 约束 7（不引入 Flink Netty 栈）。已知平台先例：`ReflectiveRpcService` + `SimpleRpcServer` 已能按参数名 Map 映射多参 void 方法（如 `triggerCheckpoint(barrier, fencingToken)`），评估是否需要自造 `IRpcMessageAdapter`。
- [x] **Fix**：实现控制面 RPC server adapter，把 `IStreamTaskRpcService`（task 侧）经选定 server 暴露；如 task→coordinator 上行需要，同途径暴露 `IStreamCoordinatorRpcService`（coordinator 侧）。若 stream message types 需经 `IRpcMessageAdapter`/message transformer 适配 `ApiRequest`/`ApiResponse`，实现该适配（验证 `IRpcService.callAsync(serviceMethod, ApiRequest, ...)` 默认反射分发是否足够，不足才加 adapter）。
- [x] **Fix**：实现 client 远程代理装配——`RpcServiceProxyFactoryBean`（或 `ClusterRpcProxyFactoryBean`）+ `ClusterRpcClient`/`MessageRpcClient`——使 coordinator 持有按 nodeId 路由的远程 `IStreamTaskRpcService`，task 持有远程 `IStreamCoordinatorRpcService`。
- [x] **Fix**：引入分布式 dispatcher 形态（`IStreamExecutionDispatcher` 新实现或重构 `EmbeddedDistributedExecutor`），使 coordinator 具备**长生命周期**（`execute()` 返回不立即 `stop()`），并经远程 proxy 而非直接 map 注入控制面对端。兑现 `IStreamExecutionDispatcher.java:34` deferred 契约。
- [x] **Fix**：引入 nop-stream 首个 `beans.xml`（`_service.beans.xml`）装配 RPC server bean + proxy bean（遵循 NopIoC：bean 显式声明、`@Inject` 用 protected/setter、`@InjectValue` 注配置）。
- [x] **Proof**：E2E 测试——coordinator 与 task 经**真实 RPC 传输**（`MessageRpcServer` over `LocalMessageService` 或 H2 `SysDaoMessageService` 作传输介质）通信，断言控制调用（`triggerCheckpoint`/`updateFencingToken`/`reportTaskStatus`）**穿越 RPC 层**（如计数器/mock verify server handler 被调用、或代理对象非直接引用），而非直接 Java 方法调用。

Exit Criteria:

- [x] 接线拓扑设计 note 已写入 `01-architecture-baseline.md` §五（per-nodeId topic、proxy map、server 生命周期、coordinator 长生命周期、dispatcher 关系裁定）。
- [x] `rpc/` 包新增 server adapter + proxy 装配 + （如需）message transformer 类；`beans.xml` 存在且装配之。
- [x] **beans.xml 非空壳**：`beans.xml` 被本 stage ≥1 个自动化测试实际加载（E2E 经 IoC bootstrap，或 dedicated bean-bootstrap 单测），断言 RPC server/proxy bean 实例化（plan guide #11/#24）。
- [x] **端到端验证**（plan guide #22）：从 dispatcher 入口（`execute`/submit）→ coordinator 控制调用 → RPC 传输 → remote task handler 全路径跑通，断言 RPC 层被穿越。
- [x] **接线验证**（plan guide #23）：coordinator 持有的 `IStreamTaskRpcService` 在 distributed 形态下确为 RPC 代理（运行时调用经 server handler dispatch），非直接 `TaskManager` 引用；测试含显式断言（计数器/标志位/mock verify）。
- [x] **无静默跳过**：RPC 失败/对端不可达为显式异常或错误传播，非空 catch/continue（plan guide #24）。
- [x] `01-architecture-baseline.md` §五 cross-JVM staging table（line 374「Stage 39」）更新为「已 WIRE」，并按实际暴露方向（task 侧 + coordinator 侧）记录；server 选型 Decision 已记录。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 分布式 abort path 收口 + G5/G34 裁定

Status: completed
Targets: `nop-stream-runtime/coordinator/JobCoordinator.java`、`nop-stream-runtime/execution/GraphModelCheckpointExecutor.java`、`IStreamTaskRpcService`（如签名变化）、`ai-dev/design/nop-stream/checkpoint-design.md` §8.7/§13.2

- Item Types: `Fix`、`Decision`、`Proof`

- [x] **Fix**：接通 distributed abort——`JobCoordinator` 注册 abort listener，checkpoint 超时/abort 时对远程 task 经 `cancelTask` RPC 触发取消（闭合 checkpoint-design §8.7 line 937 deferred 项 + §13.2 line 1113「coordinator abort must terminate blocked alignment reads」+ line 1116「abort 信号必须有独立于数据流的控制通道」契约的 distributed 部分）。
- [x] **Decision**：裁定 local abort path（`registerLocalAbortHandler` 同 JVM tasks map）与 distributed abort path 的关系——local fast-path 保留 + distributed 补充，或收敛为统一经 `cancelTask` 路径（同 JVM 时 cancelTask 走直接引用 fast-path）。记录于 checkpoint-design §8.7。
- [x] **Decision（G5/G34）**：裁定 `CancelCheckpointMarker`（G5）与 abort 跨 JVM 数据 channel 传播（G34）是否需要独立于 `cancelTask` RPC 控制通道的额外机制。主 abort 机制为控制通道 `cancelTask` RPC（checkpoint-design §13.2 line 1116）。若裁定 `CancelCheckpointMarker` 作为「已恢复 channel 的补充通知」有真实消费方则实现其事件类型；若无消费方则裁定为 **Decision-only**（不引入无消费方的空壳类，遵循 plan guide #24；记录于 checkpoint-design + 本 plan Deferred）。
- [x] **Proof**：E2E 测试——distributed 形态下 checkpoint 超时 → coordinator abort listener 触发 → `cancelTask` RPC 到达 remote task → task 进入 CANCELING/CANCELED（mailbox `signalCancel` + `interrupt` unblock），blocked alignment read 被打断。断言 remote task 确被取消（状态转换 + 计数器/mock verify RPC handler 收到 cancelTask）。

Exit Criteria:

- [x] distributed abort path E2E 全绿：abort → remote task cancel 确经 `cancelTask` RPC（非仅 local path）。
- [x] **接线验证**（plan guide #23）：`cancelTask` RPC handler 在 remote task 侧运行时确被调用（计数器/标志/mock verify）。
- [x] **端到端验证**（plan guide #22）：从 coordinator abort 触发 → RPC → remote task 取消 → blocked read 打断，完整路径跑通。
- [x] G5/G34 裁定已记录（实现 or Decision-only），无空壳类作为「正常」实现（plan guide #24）。
- [x] checkpoint-design §8.7（line 937 deferred 标注 + stale「新增 cancelTask」措辞）+ §13.2（line 1113/1116 abort 契约）更新为 distributed 已落地；§13.2 line 1119（channel 心跳）显式标注「保留 Deferred，属 Stage 43，out-of-scope」。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。
>
> **纯文档计划**：如果计划不涉及任何代码变更（仅修改 `docs/` 或 `ai-dev/` 下的文件），`./mvnw test`、`./mvnw lint` 等构建验证条目可以直接从 Closure Gates 中删除，不需要执行。

- [x] Stage 39 roadmap deliverables 全部落地或显式裁定（fencing 统一 / RPC server+proxy / 分布式 dispatcher / distributed abort / G5-G34 裁定）。
- [x] 控制面调用经真实 RPC 传输（`MessageRpcServer`/`SimpleRpcServer`）到达对端，非直接 Java 引用——E2E + 接线验证证据。
- [x] fencing token 在控制面+数据面统一为单调 long epoch，两不变量（stale-leader / 同 leader 上轮 recovery）均有 focused test。
- [x] distributed abort path E2E（abort → cancelTask RPC → remote task cancel → blocked read 打断）。
- [x] nop-stream 首个 `beans.xml` 装配 RPC bean。
- [x] 无静默跳过/空壳实现（fencing 不匹配显式 discard；RPC 失败显式传播；G5/G34 无消费方则 Decision-only 不引入空壳）。
- [x] `01-architecture-baseline.md` §五（line 326/356/374）+ `checkpoint-design.md` §8.7/§13.2 已同步到 live baseline。
- [x] 独立子 agent closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 验证 (a) RPC 调用链运行时连通（dispatcher→coordinator→RPC proxy→server handler→task），(b) distributed abort 的 cancelTask RPC 运行时被调用，(c) 无空方法体/静默 no-op。
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high`：Stage 39 新增/修改代码（`rpc/StreamControlRpc*`、`RpcDistributedExecutor`、`registerDistributedAbortHandler`、fencing 字段、`beans.xml`）**0 finding**（closure audit ses_03cfb7271ffebY7r5e2dg7o1Pf 逐文件核对）。工具因 nop-stream 模块**既有** 12 个 high finding 退出码 1——全部是 `UnsupportedOperationException` fail-fast 守卫（plan guide #24 明确规定此为非空壳的正确模式：CEP `GroupPattern`/`RuntimeContext`/`StreamingRuntimeContext`/`FunctionUtils`/`Trigger`/`DemoKeyedStateStore`）+ 1 个 rocksdb 注释，均**先于本 plan 存在且不在本 plan scope**。本 plan 引入的代码无空壳/静默 no-op。

## Deferred But Adjudicated

### G5 `CancelCheckpointMarker` / G34 abort 数据 channel 传播（如裁定 Decision-only）

- Classification: `out-of-scope improvement`（仅在 Phase 3 Decision 裁定为「无独立消费方」时落入此项）
- Why Not Blocking Closure: 主 abort 机制为控制通道 `cancelTask` RPC（checkpoint-design §13.2 line 1116 硬契约）。`CancelCheckpointMarker` 仅为「已恢复 channel 的补充通知」，其价值依赖 future stage（如 Stage 43 unaligned / Stage 45 多并发）是否需要 in-data-flow marker。无消费方时引入空壳类违反 plan guide #24。
- Successor Required: `yes`（若 Stage 43/45 出现真实消费方）
- Successor Path: Stage 43 / Stage 45 plan

## Non-Blocking Follow-ups

- 多 JVM 进程编排 E2E（Stage 42）——本 plan 同 JVM 内 RPC 传输验证已足够解除 Stage 42 前置。
- 数据面 envelope 传输后端接线（Stage 40）——本 plan 只动 fencing 字段表示。

## Closure

Status Note: Stage 39 三 Phase 全部落地并通过独立 closure audit。fencing token 统一为单调 long epoch（`leaderEpochValue * EPOCH_SCALE + recoveryGen`，数据面双键收敛为单 long 比较，两不变量证明）；控制面经 `MessageRpcServer`+`RpcServiceProxyFactoryBean` 跨 JVM RPC；分布式 dispatcher（长生命周期 coordinator，`RpcDistributedExecutor`）；distributed abort path（`cancelTask` RPC 独立控制通道）；G5/G34 Decision-only 不引入空壳。642 tests 0 failures。
Completed: 2026-08-02

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent ses_03cfb7271ffebY7r5e2dg7o1Pf（独立 read-only + run-tests session）
- Audit Session: ses_03cfb7271ffebY7r5e2dg7o1Pf（2026-08-02）
- Evidence:
  - **Phase 1 Exit Criteria — ALL PASS**：live code 无复合 String fencing token（仅 `JdbcClusterRegistry.java:59` 历史注释）；`JobCoordinator.java:123` `AtomicLong fencingEpoch`，`:918` `public static long deriveHaFencingEpoch(long,long)`；`StreamMessageEnvelope` 仅 `long epochId`（fencingToken 字段已删）；`RemoteInputChannel.java:212` 单 long 比较（双键收敛）；控制面 message types 均为 `long fencingEpoch`（TaskAssignment:28/CheckpointBarrierSignal:27/CheckpointAckMessage:30/TaskStatusReport:42/CoordinatorInfo:26/IStreamTaskRpcService:23,32）；ClusterRegistry Option B（long API + `JdbcClusterRegistry` `String.valueOf(long)` 入 VARCHAR，DDL 未迁移）；`TestFencingEpochUnification` 5 tests（a 数据面 stale / b leadership 切换 / c 同 leader recovery / d 非 HA / 编码主导）0 failures。
  - **Phase 2 Exit Criteria — ALL PASS（Anti-Hollow）**：`StreamControlRpcServer`（`MessageRpcServer`+`ReflectiveRpcService`+`CorrelatingRpcService`，real `doStart`）、`StreamControlRpcProxyFactory`（`MessageRpcClient`+`RpcServiceProxyFactoryBean`+`RpcChannelState`）、`StreamControlRpcTransformer`（void→oneWay，request-response timeout+id）、`StreamControlRpcTopics` 无空方法体；`stream-control-rpc.beans.xml`（nop-stream 唯一 beans.xml）经 `TestStreamControlRpcBootstrap` 用 NopIoC 加载，断言 bean 实例化 + RPC round-trip；`RpcDistributedExecutor.startJob` 注入 RPC 代理（非直接 TaskManager 引用）+ 长生命周期 `DistributedJobHandle`；`TestStreamControlRpc` `assertFalse(proxy instanceof RecordingTaskRpc)` + 计数器断言；`TestRpcDistributedExecutorE2E` full pipeline 正确结果集证明 assignment 穿越 RPC。
  - **Phase 3 Exit Criteria — ALL PASS**：`JobCoordinator.registerDistributedAbortHandler`（:1179-1209）在 `CheckpointCoordinator.setAbortHandler` 注册真实 lambda，对 `taskAssignmentMap` 调 `rpc.cancelTask`；`RpcDistributedExecutor:195` 调用之；`TestDistributedAbortPath` `cancelTaskCount==2` + `cancelTaskKeys.size()==2` 经 RPC 到达 `RecordingTaskRpc`；G5/G34 Decision-only（`grep "class CancelCheckpointMarker"` → 0 matches）；checkpoint-design §8.7（:954）/§13.2（:1130/1133）/§13.2.1（:1139-1145）已同步。
  - **Closure Gates — ALL PASS（13/13，从 live evidence 满足）**。
  - **`./mvnw test -pl nop-stream -am -T 1C`**：Tests run: 642, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS。
  - **Anti-Hollow Check**：(a) RPC 调用链运行时连通——`TestRpcDistributedExecutorE2E` + `TestStreamControlRpc` 证明；(b) distributed-abort cancelTask 运行时被调用——`TestDistributedAbortPath` 证明；(c) 新 Stage 39 代码（rpc/、RpcDistributedExecutor、abort handler、fencing）无空方法体/静默 no-op。
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0**（Passed: 1, Failed: 0）。
  - **`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high`**：Stage 39 新增/修改代码 0 finding（closure audit 逐文件核对：`rpc/StreamControlRpc*`、`RpcDistributedExecutor`、`registerDistributedAbortHandler`、fencing、`beans.xml` 均无空方法体/静默 no-op）。工具退出码 1 是因 nop-stream 模块**既有** 12 个 high finding（全部 `UnsupportedOperationException` fail-fast 守卫——plan guide #24 规定的非空壳正确模式；CEP/core/fraud-example/rocksdb），均先于本 plan 存在且不在本 plan scope。本 plan 引入代码无空壳。
  - **Deferred 诚实性**：G5/G34 Decision-only（无 `CancelCheckpointMarker` 空壳，Successor Stage 43/45）；§13.2 channel 心跳 Deferred 属 Stage 43（out-of-scope）。

Follow-up:

- Stage 42：多 JVM 进程编排 E2E（本 plan 同 JVM RPC 穿越验证已解除其前置）。
- Stage 40：数据面 IMessageService 后端接线（本 plan 仅改 envelope fencing 字段表示）。
- Stage 43/45：若出现 in-data-flow cancel marker 真实消费方，重新裁定 `CancelCheckpointMarker`。
- 无 Stage 39 剩余 plan-owned work。
