# 分布式控制面 fencing 补全（cancelTask 携带 fencing epoch + deployTask slot-replace 原子化）

> Plan Status: completed
> Mission: nop-stream-productization
> Work Item: roadmap item 27
> Last Reviewed: 2026-09-04
> Source: runtime 审计报告 `ai-dev/analysis/2026-09/2026-09-01-nop-stream-runtime-module-audit.md` §2.2 F-C（接口级 fencing 缺口；审计内部 Follow-up 编号 item 26 = 本 roadmap item 27）+ W-5（原判 watch-only，由本项显式承接原子化）
> 既有裁定 supersession：`ai-dev/design/nop-stream/checkpoint-design.md`（:287、:1454）曾裁定「扩展 cancelTask RPC 携带 epoch」为跨模块公共 API 变更而拒绝、留作 successor（需 plan-first 升级）——本 plan 即该 successor 的 **plan-first 载体**（owner doc = 本节 + Phase 1 全量盘点；migration plan = 全部实现/替身一次性同改，见 Phase 1 D1；落地后该 design doc 的拒绝裁定按 Phase 3 owner-doc 项同步 supersede）
> Related: `2026-09-01-0938-3-runtime-module-audit.md`（R-14 fencing 回滚防护先例）；`2026-09-01-2217-3-composite-scenario-distributed-verification.md`（fencing 断言资产：C1 陈旧 epoch mutation 拒绝）

## Purpose

消除控制面最后一个无 fencing 的 mutating RPC 入口：`cancelTask` 不携带 fencing epoch，stale coordinator（旧 leader / 旧恢复代）可随时取消 active 代任务，破坏 fencing 单调语义（对照其余 mutating 入口均已带 epoch）。同时把 `TaskManager.deployTask` slot-replace 的非原子窗口收敛为原子操作。

## Current Baseline

（live 核对 2026-09-03，行号为当前代码）

- `IStreamTaskRpcService.cancelTask(String jobId, String vertexId, int subtaskIndex)`（IStreamTaskRpcService.java:25）——审计判定为接口唯一无 fencing epoch 的 mutating 入口；对照 `receiveAssignment` / `triggerCheckpoint` / `deployTask(descriptor, fencingEpoch)` / `updateFencingToken` 均携带。
- `TaskManager.cancelTask`（TaskManager.java:681-694）直接 `runningTasks.remove(taskKey)` + cancel + 释放 permit，无任何 epoch 校验。
- `TaskManager.deployTask` slot-replace（TaskManager.java:528-546）：`runningTasks.get(taskKey)` → `remove(taskKey)` → `put(taskKey, runningTask)` 三步非原子（对照 `receiveAssignment` 用 putIfAbsent）；并发同 key interleaving 有瞬态丢项窗口。permit 账目已由 P1 hardening 保住（tryAcquire 入口 + release 旧槽），残余风险仅并发 cancelTask/二次 deploy 在窗口内看不到映射项。
- RPC 面现状（初步 rg 核对，Phase 1 落全量清单）：cancelTask 跨 JVM 经通用反射按名分发（`StreamControlRpcServer` / `StreamControlRpcProxyFactory` / `StreamControlRpcTransformer` 无 per-method 编解码——签名加参自动流经 wire，预期 transformer 零代码变更）；main 侧真实调用点 = `JobCoordinator` abort handler（JobCoordinator.java:2055，该处可取 `getFencingEpoch()` 传参）；`SupervisionLoop` 的 cancelTaskWithMailbox 为本地私有方法（非 RPC 调用方）、`RpcDistributedExecutor` 仅注释提及。实现/替身面（初步清点）：nop-stream-runtime 内约 18 个测试替身（`TestStreamControlRpc` / `TestJobCoordinator*` 族 / `TestCoordinatorRpcControlPlane` 等）+ 接线点（`EmbeddedDistributedExecutor` 把 TaskManager 装入 RPC map——非接口实现；main 侧唯一接口实现是 `TaskManager`），**外加跨模块 test-scope 实现：`nop-sys/nop-sys-dao` 的 `TestJobCoordinatorWithSysDaoLeaderElector`（CapturingTaskRpcService，override 3 参 cancelTask）——注意 `./mvnw test -pl nop-stream -am` 构建不含 nop-sys，须专门验证（见 Phase 3 门禁）**。
- fencing 既有语义锚点：`deployTask` 拒绝陈旧 epoch 抛 typed `ERR_STREAM_FENCING_TOKEN_MISMATCH`（expected/actual 参数）；one-way RPC 下失败可观测先例 = `reportDeployFailure`（FAILED TaskStatusReport 上报协调器触发恢复）。

## Goals

- cancelTask RPC 全链路携带 fencing epoch：接口签名、wire 消息（transformer）、全部实现与测试替身、全部调用方传协调器当前 epoch。
- TM 侧对 stale epoch 的 cancelTask fail-fast（typed fencing error，语义与 deployTask/triggerCheckpoint 对齐：显式拒绝，非静默）。
- deployTask slot-replace 对并发同 key 操作原子（无瞬态丢项窗口）；permit 账目语义不变。
- stale-cancel 拒绝在 one-way RPC 下可观测（对照 reportDeployFailure 模式裁定）。

## Non-Goals

- 不新增 RPC 入口、不改 RPC 传输框架/序列化框架。
- 不动数据面（plan `2026-09-03-1951-3`）。
- 不重构 RunningTask 生命周期与 cancel 级联语义（只收 slot-replace 原子性）。
- 不做跨版本 wire 兼容机制建设（MiniStreamCluster 同版本部署，见 D3）。

## Scope

### In Scope

- `IStreamTaskRpcService` 接口及全部实现/替身/代理/服务端分发
- `StreamControlRpcTransformer`（cancel 消息携带 epoch）
- `TaskManager`（cancelTask epoch 校验 + deployTask slot-replace 原子化）
- 调用方传 epoch（JobCoordinator / SupervisionLoop / RpcDistributedExecutor 等，以 Phase 1 盘点为准）
- focused fencing 测试 + RPC round-trip 测试 + gated 分布式回归

### Out Of Scope

- 其余 RPC 入口的 fencing（已具备）
- cancel 语义扩展（级联取消、区域取消、优雅 drain 取消）

## Execution Plan

### Phase 1 - 影响面盘点与契约裁定

Status: completed
Targets: 本 plan

- Item Types: `Decision | Proof`

- [x] 全量盘点 cancelTask 调用面与实现面（**仓库级 rg，main + test，含 nop-sys 等跨模块 test-scope 消费**；逐文件落 plan，执行时机械核对打勾）
- [x] D1 接口演进方式裁定：直接改签名 + 全实现同步改，或 default 方法桥接过渡。裁定输入：Phase 1 盘点清单与跨模块事实（接口虽有 nop-stream 内部形态，但被 nop-sys test-scope 消费，「无外部兼容承诺」须以盘点为准）；约束：桥接分支不得为未迁移调用方制造**运行时才失败**的陷阱（default-throw 先例成立前提是当时无生产调用方，而 JobCoordinator:2055 是生产调用方——桥接若被选用，必须证明全部调用方编译期迁移或显式接受失败语义）。二态裁定附依据。
- [x] D2 stale-cancel 可观测性裁定：① 仅 typed 异常 + WARN 日志（one-way 下调用方不可见）；② 对照 reportDeployFailure 模式向协调器上报拒绝状态。必须显式选择，不得静默。
- [x] D3 wire 兼容裁定：cancel 消息体增 epoch 字段后旧新混布行为（MiniStreamCluster 同版本部署，预期裁定为不做跨版本兼容并记录依据；若裁定需要 fail-fast 拒绝未知字段须说明机制）。

#### Phase 1 盘点清单（2026-09-03 live rg，仓库级）

接口作用面 = `IStreamTaskRpcService`（排除仓库内同名无关签名：`ITaskExecutionQueue.cancelTask`（nop-kernel）、`RpcTaskMonitor`（nop-rpc）、`JobScheduleStoreImpl`（nop-job）、`ResourceRecordConsumerProvider`（nop-batch）、`SupervisionLoop.cancelTaskWithMailbox`（本地私有静态方法，非 RPC 调用方）、`SplitAssignmentProxy`（nop-stream-core，注释引用））。

**main（nop-stream-runtime，4 处实际代码 + 3 处验证性核对点）**：

| # | 文件:行 | 角色 | 动作 |
|---|---|---|---|
| M1 | `rpc/IStreamTaskRpcService.java:25` | 接口声明（唯一无 epoch 的 mutating 入口） | 签名加 `long fencingEpoch` |
| M2 | `taskmanager/TaskManager.java:681` | main 侧唯一接口实现，无 epoch 校验 | 加 `currentFencingEpoch` 校验 + 原子化 deployTask slot-replace |
| M3 | `coordinator/JobCoordinator.java:2047` | **唯一生产调用方**（分布式 abort handler） | 传 `getFencingEpoch()` |
| M4 | `rpc/StreamControlRpcTransformer.java:25` | javadoc 提及；无 per-method 代码 | 零代码变更（验证性核对） |
| M5 | `rpc/StreamControlRpcServer` / `rpc/StreamControlRpcProxyFactory` | 通用反射按名分发，无 cancelTask 专有代码 | 零代码变更（验证性核对） |
| M6 | `execution/RpcDistributedExecutor.java:259` / `execution/EmbeddedDistributedExecutor` | 注释提及 / 把 TaskManager 本体装入 RPC map（非独立实现） | 零代码变更（验证性核对；TaskManager 签名变更自动覆盖） |

**test 替身（implements `IStreamTaskRpcService` 并 override 3 参 cancelTask，共 20 个）**：nop-stream-runtime 19 个 + 跨模块 1 个：

1. `rpc/TestStreamControlRpc.java:162` RecordingTaskRpc（另有 RPC 代理调用点 :83）
2. `rpc/TestStreamControlRpcBootstrap.java:149` RecordingTaskRpc
3. `execution/TestJobCoordinatorAssignmentFromPlan.java:255` RecordingTaskRpc
4. `coordinator/TestJobCoordinatorAttemptTracking.java:191` CapturingTaskRpc
5. `coordinator/TestJobCoordinatorStandbyStateMachine.java:485` MockTaskRpcService
6. `coordinator/TestJobCoordinatorTerminationMatrix.java:249` MockTaskRpcService
7. `coordinator/TestJobCoordinator.java:557` MockTaskRpcService
8. `coordinator/TestJobCoordinatorFailoverRestore.java:475` MockTaskRpcService
9. `coordinator/TestJobCoordinatorRecoveryConcurrency.java:421` NoopTaskRpcService
10. `coordinator/TestDistributedAbortPath.java:197` RecordingTaskRpc（断言 :145-148）
11. `coordinator/TestCoordinatorRpcControlPlane.java:353` MockTaskRpcService
12. `coordinator/TestJobCoordinatorAuditFixes.java:433` RecordingTaskRpcService（standby 不发 cancel 断言 :228）
13. `coordinator/TestJobCoordinatorRemoteDeploy.java:271` RecordingTaskRpcService
14. `coordinator/TestJobCoordinatorLeaderElection.java:408` MockTaskRpcService
15. `coordinator/TestJobCoordinatorJdbcHaIntegration.java:251` CapturingTaskRpc
16. `coordinator/TestJobCoordinatorPerTaskFailure.java:287` NoopTaskRpc
17. `coordinator/TestFencingEpochUnification.java:349` CapturingTaskRpc（C1 断言资产所在类）
18. `coordinator/TestJobCoordinatorRestartStrategy.java:234` NoopTaskRpc
19. `health/TestJobCoordinatorHealthWiring.java:104` NoOpTaskRpcService
20. **跨模块** `nop-sys/nop-sys-dao/src/test/java/io/nop/sys/dao/elector/TestJobCoordinatorWithSysDaoLeaderElector.java:376` CapturingTaskRpcService（`-pl nop-stream -am` 构建不含 → Phase 3 跨模块门禁专项验证）

**test 直接调用方（TaskManager.cancelTask 直调，8 处）**：`taskmanager/TestTaskManager.java:220,283,284,450`、`taskmanager/TestTaskManagerLivenessAndReporting.java:91,192,233,256`（均需补传当前 epoch）。

#### D1 裁定：直接改签名 + 全部实现/调用方一次性同改

依据：(a) 盘点证实接口全部消费方在仓库内（main 实现 1 个 + 生产调用方 1 个 + 测试替身 20 个 + 测试直调 8 处），无仓外消费者；唯一跨模块消费是 nop-sys-dao test-scope，同仓编译，Phase 3 跨模块门禁兜底；(b) default 桥接的先例前提（deployTask/notifyCheckpointComplete default-throw 成立时「无生产调用方」）不成立——JobCoordinator:2047 是生产调用方：default 委托桥（4 参 → 3 参）会让任何未迁移实现**静默丢弃 epoch**（运行时才失败的陷阱，plan 明令禁止）；default-throw 桥则把编译期迁移信号降级为「cancel 真被触发时才炸」的测试期陷阱；(c) D3 裁定同版本部署，无旧签名 wire 存续需求。编译器即迁移清单，无运行时残留面。

#### D2 裁定：方案 ①（typed `ERR_STREAM_FENCING_TOKEN_MISMATCH` + 显式 WARN 日志），不上报 FAILED TaskStatusReport

依据：reportDeployFailure 先例**语义不可迁移**——deployTask/receiveAssignment 上报 FAILED 的前提是「任务确实未落地，恢复是协调器的正确响应」；而 stale-cancel 被拒时任务在 active 代下**健康运行**，异常方是 stale 协调器。若按 ② 上报 FAILED：(a) active 协调器按 jobId/vertexId/subtaskIndex 归因到存活 attempt，会对健康任务触发虚假恢复；(b) 等于让 zombie 协调器间接改变 active 代状态——恰是 fencing 要阻止的危害类别。stale cancel 的正确响应是「不作为 + 拒绝可观测」：TM 侧抛 typed `ERR_STREAM_FENCING_TOKEN_MISMATCH`（expected/actual 参数，与 deployTask/triggerCheckpoint 对齐）+ 抛出点显式 WARN（含 job/vertex/subtask/expected/actual）；one-way 下调用方不可见与 triggerCheckpoint/notifyCheckpointComplete 完全同构（仓库既定语义），服务端可观测面 = 异常经 RPC 分发层落日志（gated multi-JVM 套件的 `probeStaleEpochRejection` 正是按此口径断言 `ERR_STREAM_FENCING_TOKEN_MISMATCH`/`process-request-fail` 日志标记）。

#### D3 裁定：不做跨版本 wire 兼容（同版本部署不变式）

依据：仓库内全部部署拓扑（MiniStreamCluster、TaskManagerMain/JobCoordinatorMain、RpcDistributedExecutor）从同一次构建拉起同版本 JVM，无滚动升级需求（Non-Goals 已排除跨版本机制建设）。混布行为推演（仅记录，不建设）：DefaultRpcMessageTransformer 按参数名绑定——旧 server 收新消息会忽略多余 `fencingEpoch` 条目（fail-open，故混布不被支持且不承诺）；新 server 收旧消息缺参绑定失败 → typed RPC 错误（fail-fast）。无需新增拒绝未知字段机制。

Exit Criteria:

- [x] 调用/实现面清单完整落 plan（rg 可复核，含测试替身）— 见「Phase 1 盘点清单」节，M1–M6 + 替身 20 + 直调 8 处
- [x] D1—D3 全部二态裁定落 plan，无未裁定项 — D1 直接改签名 / D2 方案① / D3 不做跨版本兼容，各附依据

### Phase 2 - 接口与实现

Status: completed
Targets: `IStreamTaskRpcService.java`、`TaskManager.java`、`StreamControlRpcTransformer.java`、`StreamControlRpcServer/ProxyFactory`、调用方、测试替身

- Item Types: `Fix`

- [x] cancelTask 签名增 fencing epoch（按 D1）；TM 实现校验 `currentFencingEpoch`，stale 抛 typed `ERR_STREAM_FENCING_TOKEN_MISMATCH`（参数含 expected/actual）— `IStreamTaskRpcService.java:25` 签名 `cancelTask(String,String,int,long)`（javadoc 契约）；`TaskManager.cancelTask` 入口 `!=` 校验 + WARN（含 job/vertex/subtask/expected/actual）+ typed throw（与 triggerCheckpoint/notifyCheckpointComplete 对齐）
- [x] transformer/服务端分发/代理按盘点清单核对（预期零代码变更——通用反射分发自动携带新参；验证性核对而非改码项）、全部实现与测试替身同步更新（含跨模块 nop-sys 替身）— M4/M5 核对：`StreamControlRpcTransformer`（仅 javadoc 提及）、Server/ProxyFactory 无 per-method 代码，零变更成立；替身 20/20 迁移（nop-stream 19 + nop-sys 1，`@Override` 编译期强制）；直调 8 处补传当前 epoch
- [x] 全部调用方传协调器当前 epoch（逐个对照 Phase 1 清单核对打勾；main 侧 JobCoordinator:2055 传 `getFencingEpoch()`）— JobCoordinator.java:2050 abort handler 传 `getFencingEpoch()`（唯一生产调用方）；测试直调/round-trip 调用点全迁移（rg 复核无 3 参残留）
- [x] deployTask slot-replace 原子化：get→remove→put 收敛为对同一 key 的单原子操作语义；permit 账目（tryAcquire 入口 + 旧槽 release）不变；**同时覆盖 build 失败回滚路径**（TaskManager.java:567 的无条件 `runningTasks.remove(taskKey)` 在 interleaving 后可能误删后继任务的映射——与 W-5 同类窗口，一并收敛或显式裁定排除）— 收敛：`put` 返回 displaced entry 单原子换槽（每个被换出任务恰好交给其 displacer cancel + release）；build 失败回滚改条件 remove `remove(taskKey, runningTask)`（仅删自有条目；临时回退旧代码验证新测试确实抓得住该缺陷：expected 1 but was 0 后恢复修复）
- [x] 按 D2 实现 stale-cancel 可观测路径 — WARN + typed throw；无 FAILED 上报（D2 裁定依据落 plan）

Exit Criteria:

- [x] rg 验证（仓库级，含 nop-sys；按 Phase 1 清单限定 `IStreamTaskRpcService` 作用面，避免仓库内同名无关签名如 ITaskExecutionQueue.cancelTask 误报）：无旧签名 cancelTask 残留（main + test）— rg `cancelTask\(String` 仅剩 ITaskExecutionQueue/RpcTaskMonitor（无关签名，盘点已排除）；`.cancelTask(` 调用点全部 4 参
- [x] stale epoch cancelTask 被 typed 拒绝且 D2 裁定的可观测路径生效 — `TestTaskManager#testCancelTaskRejectsStaleEpochAndLeavesTaskRunning`（错误码 + expected/actual 参数断言 + 任务存活 + permit 不变）
- [x] **无静默跳过**：stale cancel 不是 LOG 后正常返回，而是显式失败语义（异常或上报，依 D2）— typed throw（非 log-and-return）
- [x] **接线验证**：cancelTask 新签名在真实调用链上被调用（调用方传 epoch 的代码审查 + Phase 3 RPC round-trip 测试双证据）— JobCoordinator:2050 传 `getFencingEpoch()`；`TestDistributedAbortPath` 断言 server 端收到 epoch == 协调器当前 epoch；`TestStreamControlRpc` 断言 epoch 经真实 RPC pair round-trip
- [x] owner-doc 裁定：接口契约变更点已裁定文档动作（`ai-dev/design/nop-stream/checkpoint-design.md` 的拒绝裁定 supersede 动作在 Phase 3 统一执行；本 Phase 显式记录裁定结果，不得默默跳过）— 裁定：checkpoint-design §2.8.1 D3 option (A) 部分 supersede + §8.7/§13.2 语义同步 + component-roadmap §3 两行 + runbook §1 拓扑图，均在 Phase 3 执行；docs-for-ai nop-stream.md 无需求（不描述内部 RPC 契约）
- [x] `ai-dev/logs/` 当日条目更新 — 2026-09-04 条目（Phase 2/3 执行记录 + 收口）

### Phase 3 - 验证

Status: completed
Targets: runtime 测试 + gated 场景

- Item Types: `Proof`

- [x] focused 单测：stale-epoch cancelTask 拒绝（断言错误码与参数）/ valid-epoch cancelTask 生效（任务取消 + permit 释放）/ deployTask 并发同 key 原子性（竞态注入：并发二次 deploy + cancelTask + **build 失败 interleave**，终态映射项一致、permit 账目守恒）— 4 用例（`TestTaskManager`）：`testCancelTaskRejectsStaleEpochAndLeavesTaskRunning`（typed 错误码 + expected/actual + stale/future 双向 + 任务存活）/ `testCancelTaskWithCurrentEpochCancelsAndReleasesPermit` / `testSecondDeployAtomicallyDisplacesAndCancelsPrevious`（同 key 二次 deploy：displaced 退出、successor 独占、净 permit 变更 0）/ `testFailedDeployDoesNotRemoveSuccessorRegistryEntry`（**确定性竞态注入**：阻塞式失败图 park deploy 线程于 build 相位 → successor 换槽运行 → 放行失败 → 断言 successor 条目存活 + permit 守恒；临时回退旧代码复验 expected 1 but was 0 = 测试真实钉住缺陷）
- [x] RPC round-trip：`TestStreamControlRpc` 扩展——cancel 消息跨 RPC 携带 epoch；one-way 语义下「stale 被拒」用**服务端副作用断言**（RecordingTaskRpc 计数模式 / `probeStaleEpochRejection` 日志增量模式先例，MultiJvmTestSupport.java:172-203），不得写成期望调用方收到异常。证据口径：round-trip 证明载参 + TM focused 单测证明拒绝 + 反射分发包点 = 分段拼接证明全链；如 closure audit 要求链式证据，可扩展 `probeStaleEpochRejection` 的 cancelTask 变体（gated），执行时裁定 — `TestStreamControlRpc#taskControlCallsTraverseRpcToServerImpl` 增 `lastCancelEpoch` 断言（epoch 7 经真实 RPC pair 存活）；`TestDistributedAbortPath#checkpointAbortFiresCancelTaskRpcAtAllRemoteTasks` 增 `cancelTaskEpochs == coordinator.getFencingEpoch()` 断言（abort handler → RPC → server 全链载参）。**执行时裁定：不扩展 probeStaleEpochRejection 的 cancelTask gated 变体**——分段证据（round-trip 载参 + TM focused typed 拒绝 + 反射分发泛化性 + 既有 triggerCheckpoint probe 先例共享同一 one-way 拒绝面）已覆盖链条，增量 gated 变体不改变证据强度
- [x] 既有 fencing 断言回归：C1 陈旧 epoch mutation 拒绝 / R-14 套件（gated 多 JVM）不回退 — `TestFencingEpochUnification`（含 C1）全绿；R-14 `TestTaskManager#testUpdateFencingTokenRejectsEpochRollback` 绿；gated C1（`TestS1MultiJvmE2E#s1MultiJvmKillRecoverFencingExactlyOnce` + `TestS2MultiJvmE2E#s2MultiJvmKillRecoverFencingExactlyOnce`，含行为级「旧 epoch 控制面 mutation 在 TM RPC 边界被拒」日志断言）绿（13/13 之一）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿 — 全绿（首轮出现 1 例 `TestBufferPool`（nop-stream-core，本 diff 未触碰该模块）并行负载下 timing 抖动：单独复跑绿、整体复跑绿，与本变更无关）
- [x] **跨模块构建门禁**：`./mvnw test -pl nop-sys/nop-sys-dao -am`（或等效含 nop-sys 的编译/测试命令）通过——捕获 `-pl nop-stream -am` 覆盖不到的跨模块替身破坏 — 通过：nop-sys-dao 43/43 全绿（含迁移后的 `TestJobCoordinatorWithSysDaoLeaderElector` 4/4）
- [x] gated 启用态绿：fraud-example 场景 gated 13 项 + runtime multi-JVM legacy 7 项（启用命令沿 `distributed-runbook.md` §5）— fraud-example **13/13 绿**（S1 2 + S2 2 + RestoreRescale 1 + Backpressure 2 + Serialization 6）；legacy **6/7 绿 + 1 项与本变更无关的 pre-existing 失败**：`TestMultiJvmExactlyOnceRecovery#multiJvmDeployKillRecoverFencing`（恢复检测 90s 超时未轮转 epoch）经 **git-stash A/B 对照实验**（同机同命令：stash 本 diff 后同样失败、相同签名 `initial=1 recovered=1`；恢复本 diff 复跑 failover 3/3 绿）判定为 baseline 既有问题（本机负载相关的恢复检测超时），非本 plan 回归——记录于 Non-Blocking Follow-ups

Exit Criteria:

- [x] 新增测试逐条钉定上述行为（测试类/方法名落 plan）— 见上 4+2 用例清单
- [x] gated 回归绿（短时基线不回退）— 场景 13/13；legacy 6/7 + 1 pre-existing（A/B 对照证据，无回退）
- [x] 工具门禁 exit 0：`scan-hollow-implementations.mjs --module nop-stream-runtime --severity high`、`check-nop-stream-invariants.mjs`（wiring pin 如漂移按指引 re-pin）— 两者 exit 0（无 re-pin 需要）
- [x] owner-doc 同步：`ai-dev/design/nop-stream/checkpoint-design.md`（supersede :287/:1454 拒绝裁定 + §8.7/§13.2 abort 契约中 cancelTask 语义同步）+ `component-roadmap.md` + `distributed-runbook.md`（RPC 面描述）+ `docs-for-ai/03-modules/nop-stream.md`（如涉及运维契约）；每份要么更新要么显式记录无需求 — checkpoint-design 4 处（§2.8.1 D3 option (A) 部分 supersession 注记 / §8.7 分布式 abort 段补 fencing 语义与 D2 可观测性 / §13.2 两行契约 / §13.2.1 Successor supersession 注记）；component-roadmap §3 abort 接线 + abort 传播通道两行；distributed-runbook §1 拓扑图注记（mutating 入口全携带 fencing epoch）；docs-for-ai nop-stream.md **无需求**（运维契约不描述内部控制面 RPC 签名；其 fencing 描述为用户级恢复语义，仍准确）
- [x] `ai-dev/logs/` 收口条目更新 — 2026-09-04 条目

## Closure Gates

- [x] F-C 收敛：cancelTask 全链路携带 fencing epoch 且 TM 侧 stale 拒绝（Phase 2/3 证据）— 接口/实现/唯一生产调用方/20 替身/8 直调全迁移（rg 零残留）；TM typed 拒绝 focused 测试 + abort handler 传 epoch 的 RPC round-trip 断言
- [x] W-5 承接：deployTask slot-replace 原子化（含 build 失败回滚路径处置）+ permit 账目不变（竞态测试证据）— 单原子 put-displaced + 条件 remove 回滚；确定性 interleave 测试（回退旧代码复验必红）+ 二次 deploy 净 permit 0 + 全路径守恒断言
- [x] 行为增量仅为声明的 fencing 校验与原子化，无其他漂移 — diff 面 = 3 main 文件（接口签名 + TM cancel 校验/slot-replace/回滚 + JC 调用点传参）+ 测试迁移；cancel 原有语义（remove/cancel/permit release/日志）逐字保留
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope 项 — Deferred 区为空；唯一 follow-up 为 pre-existing gated 环境失败（A/B 对照证据，非本 plan in-scope 缺陷）
- [x] 受影响 owner docs 已同步（checkpoint-design.md supersession + component-roadmap.md + distributed-runbook.md + docs-for-ai owner doc，逐份记录动作）— 见 Phase 3 Exit Criteria（docs-for-ai 显式无需求）
- [x] 独立子 agent closure audit 已完成并记录证据 — 见下 Closure 节（session `ses_f97eeefdaffeKnPxF1UCRJ0CUp`，CLOSURE-AUDIT: APPROVED，0 Blocker / 0 Major / 3 Minor）
- [x] **Anti-Hollow Check**：cancelTask fencing 在真实 RPC/分布式调用链生效（非仅类型存在）；无静默跳过 — JobCoordinator:2050 传 `getFencingEpoch()` → 真实 RPC pair（TestDistributedAbortPath server 端断言 epoch 相等）→ TM typed 拒绝（focused 测试）+ gated C1 kill-recover-fencing（13/13 含行为级日志断言）；拒绝 = WARN + typed throw 非静默返回
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] 跨模块门禁（含 nop-sys 的构建/测试）通过 — nop-sys-dao 43/43
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（若本计划修改了 docs/ai-dev 文件）— 0 errors 0 warnings

## Deferred But Adjudicated

（无——D1 裁定直接改签名，未产生桥接过渡项）

## Non-Blocking Follow-ups

- **`TestMultiJvmExactlyOnceRecovery#multiJvmDeployKillRecoverFencing` 本机 pre-existing 失败（watch-only residual，非本 plan 引入）**：kill TM 后 90s 内恢复未轮转 epoch（`initial=1 recovered=1`）。git-stash A/B 对照（同机同命令：baseline 同样失败、相同签名；本 diff 下 failover 3/3 绿）证实与本变更无关；推测为失败检测路径（~60s stall 检测 + 5s tick）在本机负载下超出 90s 测试预算。Why Not Blocking Closure：短时基线无回退（A/B 同型失败 = 零 delta），本 plan 的 fencing 语义由 13/13 场景 gated（含同型 kill-recover-fencing 断言）+ focused 测试钉定。Successor Required: no（环境/基线观察项；若持续复现可升级为独立 bug note）。

## Closure

Status Note: F-C/W-5 双缺陷收敛：cancelTask 成为携带 fencing epoch 的 fenced mutating 入口（接口 + 唯一生产调用方 + 20 替身 + 8 直调全迁移，rg 零残留）；TM 侧 stale epoch typed 拒绝（与 deployTask/triggerCheckpoint 对齐，D2 裁定 WARN + throw 不上报 FAILED）；deployTask slot-replace 单原子化（put-displaced）+ build 失败回滚条件化（确定性 interleave 测试回退旧代码复验必红）。全部 Phase/门禁绿；唯一 follow-up 为 A/B 对照证实的 pre-existing gated 环境失败（非本 plan in-scope）。
Completed: 2026-09-04

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent fresh session（未参与实现）
- Audit Session: `ses_f97eeefdaffeKnPxF1UCRJ0CUp`
- Evidence:
  - 每条 Exit Criterion：**全 PASS**——(P1) 盘点清单与 live rg 一致（接口 4 参 @ IStreamTaskRpcService.java:35；TaskManager + 20 替身全部 4 参，含 nop-sys-dao :376；作用面外同名签名仅 ITaskExecutionQueue 2 参等已排除项）；(P2) TaskManager.java:699-706 WARN + typed `ERR_STREAM_FENCING_TOKEN_MISMATCH`（expected/actual）非 log-and-return；:543-551 单原子 put-displaced；:576 两参条件 remove；(P3) 4 focused 用例 + 2 round-trip 断言逐条 live 核对（interleave 测试确证 park-deploy-thread-inside-build 竞态注入成立，:918/:974-978）；跨模块 43/43；gated 13/13 + legacy 6/7（1 pre-existing A/B 证据）；(P2 owner-doc) checkpoint-design :289/:1239/:1441/:1444/:1456 + component-roadmap :186/:189 + runbook :15-16 + docs-for-ai rg 无 cancelTask（无需求裁定成立）。
  - 每条 Closure Gate：**全 PASS**（F-C/W-5 行为级证据、无漂移、无静默降级、owner-docs 同步、Anti-Hollow 全链追踪：JobCoordinator:2050 `getFencingEpoch()` → embedded 直装 / distributed 反射代理 → StreamControlRpcServer → TaskManager epoch check → RunningTask.cancel，epoch 端到端载运）。
  - 审计现场复跑：focused 套件（TestTaskManager/TestStreamControlRpc/TestDistributedAbortPath/TestFencingEpochUnification）**39/0/0 BUILD SUCCESS**；`scan-hollow-implementations --severity high` exit 0（0 findings）；`check-nop-stream-invariants` exit 0。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 时复验）。
  - Deferred 项分类检查：Deferred 区为空；唯一 follow-up = pre-existing gated 环境失败（A/B 同型失败零 delta），非 in-scope 缺陷降级。
  - 诚实性：checkbox 39×[x]，closure 时唯一未勾项 = 审计自身 gate（本条勾选后闭合）。
- Minor findings（已处置）：杂散工作区噪声 `nop-format/.../samples/generated-pie-chart/_rels/.rels`（行尾抖动，非本 plan 文件，不纳入提交）；plan 内 JobCoordinator 调用点行号 :2047/:2050/:2055 系同点行漂移（非矛盾）。

Follow-up:

- `TestMultiJvmExactlyOnceRecovery#multiJvmDeployKillRecoverFencing` 本机 pre-existing 恢复检测超时（watch-only residual，A/B 证据见 Non-Blocking Follow-ups；无 successor 需求，持续复现则升级为独立 bug note）。
- 无剩余 plan-owned work。
