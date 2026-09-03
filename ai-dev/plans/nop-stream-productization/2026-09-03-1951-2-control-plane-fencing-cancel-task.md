# 分布式控制面 fencing 补全（cancelTask 携带 fencing epoch + deployTask slot-replace 原子化）

> Plan Status: active
> Mission: nop-stream-productization
> Work Item: roadmap item 27
> Last Reviewed: 2026-09-03
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

Status: planned
Targets: 本 plan

- Item Types: `Decision | Proof`

- [ ] 全量盘点 cancelTask 调用面与实现面（**仓库级 rg，main + test，含 nop-sys 等跨模块 test-scope 消费**；逐文件落 plan，执行时机械核对打勾）
- [ ] D1 接口演进方式裁定：直接改签名 + 全实现同步改，或 default 方法桥接过渡。裁定输入：Phase 1 盘点清单与跨模块事实（接口虽有 nop-stream 内部形态，但被 nop-sys test-scope 消费，「无外部兼容承诺」须以盘点为准）；约束：桥接分支不得为未迁移调用方制造**运行时才失败**的陷阱（default-throw 先例成立前提是当时无生产调用方，而 JobCoordinator:2055 是生产调用方——桥接若被选用，必须证明全部调用方编译期迁移或显式接受失败语义）。二态裁定附依据。
- [ ] D2 stale-cancel 可观测性裁定：① 仅 typed 异常 + WARN 日志（one-way 下调用方不可见）；② 对照 reportDeployFailure 模式向协调器上报拒绝状态。必须显式选择，不得静默。
- [ ] D3 wire 兼容裁定：cancel 消息体增 epoch 字段后旧新混布行为（MiniStreamCluster 同版本部署，预期裁定为不做跨版本兼容并记录依据；若裁定需要 fail-fast 拒绝未知字段须说明机制）。

Exit Criteria:

- [ ] 调用/实现面清单完整落 plan（rg 可复核，含测试替身）
- [ ] D1—D3 全部二态裁定落 plan，无未裁定项

### Phase 2 - 接口与实现

Status: planned
Targets: `IStreamTaskRpcService.java`、`TaskManager.java`、`StreamControlRpcTransformer.java`、`StreamControlRpcServer/ProxyFactory`、调用方、测试替身

- Item Types: `Fix`

- [ ] cancelTask 签名增 fencing epoch（按 D1）；TM 实现校验 `currentFencingEpoch`，stale 抛 typed `ERR_STREAM_FENCING_TOKEN_MISMATCH`（参数含 expected/actual）
- [ ] transformer/服务端分发/代理按盘点清单核对（预期零代码变更——通用反射分发自动携带新参；验证性核对而非改码项）、全部实现与测试替身同步更新（含跨模块 nop-sys 替身）
- [ ] 全部调用方传协调器当前 epoch（逐个对照 Phase 1 清单核对打勾；main 侧 JobCoordinator:2055 传 `getFencingEpoch()`）
- [ ] deployTask slot-replace 原子化：get→remove→put 收敛为对同一 key 的单原子操作语义；permit 账目（tryAcquire 入口 + 旧槽 release）不变；**同时覆盖 build 失败回滚路径**（TaskManager.java:567 的无条件 `runningTasks.remove(taskKey)` 在 interleaving 后可能误删后继任务的映射——与 W-5 同类窗口，一并收敛或显式裁定排除）
- [ ] 按 D2 实现 stale-cancel 可观测路径

Exit Criteria:

- [ ] rg 验证（仓库级，含 nop-sys；按 Phase 1 清单限定 `IStreamTaskRpcService` 作用面，避免仓库内同名无关签名如 ITaskExecutionQueue.cancelTask 误报）：无旧签名 cancelTask 残留（main + test）
- [ ] stale epoch cancelTask 被 typed 拒绝且 D2 裁定的可观测路径生效
- [ ] **无静默跳过**：stale cancel 不是 LOG 后正常返回，而是显式失败语义（异常或上报，依 D2）
- [ ] **接线验证**：cancelTask 新签名在真实调用链上被调用（调用方传 epoch 的代码审查 + Phase 3 RPC round-trip 测试双证据）
- [ ] owner-doc 裁定：接口契约变更点已裁定文档动作（`ai-dev/design/nop-stream/checkpoint-design.md` 的拒绝裁定 supersede 动作在 Phase 3 统一执行；本 Phase 显式记录裁定结果，不得默默跳过）
- [ ] `ai-dev/logs/` 当日条目更新

### Phase 3 - 验证

Status: planned
Targets: runtime 测试 + gated 场景

- Item Types: `Proof`

- [ ] focused 单测：stale-epoch cancelTask 拒绝（断言错误码与参数）/ valid-epoch cancelTask 生效（任务取消 + permit 释放）/ deployTask 并发同 key 原子性（竞态注入：并发二次 deploy + cancelTask + **build 失败 interleave**，终态映射项一致、permit 账目守恒）
- [ ] RPC round-trip：`TestStreamControlRpc` 扩展——cancel 消息跨 RPC 携带 epoch；one-way 语义下「stale 被拒」用**服务端副作用断言**（RecordingTaskRpc 计数模式 / `probeStaleEpochRejection` 日志增量模式先例，MultiJvmTestSupport.java:172-203），不得写成期望调用方收到异常。证据口径：round-trip 证明载参 + TM focused 单测证明拒绝 + 反射分发包点 = 分段拼接证明全链；如 closure audit 要求链式证据，可扩展 `probeStaleEpochRejection` 的 cancelTask 变体（gated），执行时裁定
- [ ] 既有 fencing 断言回归：C1 陈旧 epoch mutation 拒绝 / R-14 套件（gated 多 JVM）不回退
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] **跨模块构建门禁**：`./mvnw test -pl nop-sys/nop-sys-dao -am`（或等效含 nop-sys 的编译/测试命令）通过——捕获 `-pl nop-stream -am` 覆盖不到的跨模块替身破坏
- [ ] gated 启用态绿：fraud-example 场景 gated 13 项 + runtime multi-JVM legacy 7 项（启用命令沿 `distributed-runbook.md` §5）

Exit Criteria:

- [ ] 新增测试逐条钉定上述行为（测试类/方法名落 plan）
- [ ] gated 回归绿（短时基线不回退）
- [ ] 工具门禁 exit 0：`scan-hollow-implementations.mjs --module nop-stream-runtime --severity high`、`check-nop-stream-invariants.mjs`（wiring pin 如漂移按指引 re-pin）
- [ ] owner-doc 同步：`ai-dev/design/nop-stream/checkpoint-design.md`（supersede :287/:1454 拒绝裁定 + §8.7/§13.2 abort 契约中 cancelTask 语义同步）+ `component-roadmap.md` + `distributed-runbook.md`（RPC 面描述）+ `docs-for-ai/03-modules/nop-stream.md`（如涉及运维契约）；每份要么更新要么显式记录无需求
- [ ] `ai-dev/logs/` 收口条目更新

## Closure Gates

- [ ] F-C 收敛：cancelTask 全链路携带 fencing epoch 且 TM 侧 stale 拒绝（Phase 2/3 证据）
- [ ] W-5 承接：deployTask slot-replace 原子化（含 build 失败回滚路径处置）+ permit 账目不变（竞态测试证据）
- [ ] 行为增量仅为声明的 fencing 校验与原子化，无其他漂移
- [ ] 不存在被静默降级到 deferred/follow-up 的 in-scope 项
- [ ] 受影响 owner docs 已同步（checkpoint-design.md supersession + component-roadmap.md + distributed-runbook.md + docs-for-ai owner doc，逐份记录动作）
- [ ] 独立子 agent closure audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：cancelTask fencing 在真实 RPC/分布式调用链生效（非仅类型存在）；无静默跳过
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] 跨模块门禁（含 nop-sys 的构建/测试）通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` exit 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（若本计划修改了 docs/ai-dev 文件）

## Deferred But Adjudicated

（预期：无；若 D1 裁定产生桥接过渡项在此记录分类与理由）

## Non-Blocking Follow-ups

（收口时填充；预期：其余 mutating 入口 fencing 完备后的观察项）

## Closure

Status Note: （关闭时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent closure audit）
- Evidence: （每条 Exit Criterion / Closure Gate 的 PASS/FAIL 与 live 锚点；check-plan-checklist / scan-hollow 退出码；Anti-Hollow 调用链追踪结果）

Follow-up:

- （只记录 non-blocking follow-up；或明确写 no remaining plan-owned work）
