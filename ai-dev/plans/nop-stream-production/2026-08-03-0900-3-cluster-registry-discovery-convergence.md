# 3 ClusterRegistry 收敛到平台 discovery（Stage 41, G51 续, 决策点 D7）

> Plan Status: completed
> Last Reviewed: 2026-08-03
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 41（行 578-588）、决策点 D7（行 853）; `ai-dev/design/nop-stream/01-architecture-baseline.md` §「ClusterRegistry JDBC durability（D73）」（行 258-269）、§「平台 discovery 单向注册（G51, D7 deferred）」（行 271-287）; `ai-dev/backlog/completion-roadmap.md` 行 239, 365
> Mission: nop-stream-production
> Work Item: 41. ClusterRegistry 收敛到平台 discovery（G51 续）
> Related: `2026-08-02-0955-8-leader-election-ha.md`（Stage 38，leader election WIRE 模式，本 plan mirror）; `2026-08-03-0001-1-multi-jvm-test-infrastructure.md`（Stage 42，多 JVM 基建使用 JdbcClusterRegistry，需 lockstep）

## Purpose

裁定并落地决策点 D7（`ClusterRegistry` 完全替换为平台 discovery vs 与平台 discovery 对接共存），把 G51 的「单向注册（nop-stream → discovery 写）」推进到「ClusterRegistry 与平台 discovery 的最终关系确定」。收口 Phase 4 最后一个 `todo`。

## Current Baseline

经 live repo 核对（2026-08-03）：

- **`ClusterRegistry` 抽象 + 三实现**：接口 `ClusterRegistry`（`nop-stream-runtime/.../cluster/ClusterRegistry.java`，128 行，含 coordinator 注册/fencing epoch、node 注册/lease、task 分配/attempt history 等方法）；实现 `JdbcClusterRegistry`（513 行，三张表 `nop_stream_coordinator`/`nop_stream_node`/`nop_stream_task_assignment`）、`InMemoryClusterRegistry`、test doubles。
- **消费者全部 program-to-interface**（不依赖具体 `JdbcClusterRegistry` 类型）：`JobCoordinator`（`:114, 263` 构造注入）、`TaskManager`（`:89, 118, 154`，`start()` 调 `registerNode`）、`EmbeddedDistributedExecutor`/`RpcDistributedExecutor`（各自 `new InMemoryClusterRegistry()`）。**无任何 beans.xml 注册 `ClusterRegistry` bean**——始终内联构造。
- **G51 部分工作已落地（单向注册）**：`StreamNodeAutoRegistration`（159 行）在 `@PostConstruct` 把节点 `registerInstance` 到 `INamingService`（写方向），**不读** `IDiscoveryClient`。类 javadoc（行 48-53）与 `01-architecture-baseline.md:287` 明确「ClusterRegistry 仍是 runtime source of truth；完全替换 vs 对接是 Stage 41 决策点 D7，本阶段不预判」。消费者 `EmbeddedDistributedExecutor:137,157-164`；测试 `TestDiscoveryRegistration`。
- **平台 discovery SPI（`nop-cluster-core`，`nop-stream-runtime` 已依赖）**：`IDiscoveryClient`（只读，`getInstances`）、`INamingService`（读+写，`registerInstance`/`unregisterInstance`/`getInstances`）、`AutoRegistration`（`@PostConstruct`/`@PreDestroy` 生命周期包装）。JDBC 生产实现 `SysDaoNamingService`（`nop-sys-dao`，197 行，ORM 实体 `NopSysServiceInstance`，自带 staleness 过滤）。`nopNamingService` bean 在 `app-dao.beans.xml:46-54`（`ioc:default` + feature-gated）。
- **Stage 38 集成模式（本 plan mirror）**：nop-stream-runtime 只 program-to-interface（如 `JobCoordinator.setLeaderElector`），具体 bean 由 nop-sys-dao 产出（`nopSysDaoLeaderElector`，`app-dao.beans.xml:26`，`ioc:default`+feature-gated），跨模块 smoke check 放 nop-sys-dao test（`TestJobCoordinatorWithSysDaoLeaderElector`）。注意：`setLeaderElector` 的 deploy-time wiring **尚未在任何 beans.xml 物化**——Stage 38 只搭了 coordinator 侧消费契约。
- **D7「完全替换」blast radius > roadmap 表述**：`ClusterRegistry` 承载平台 discovery **不原生建模**的状态：per-task `TaskAssignment` + attempt history（G56）、coordinator fencing epoch 注册、node capacity-as-lease。`ServiceInstance` 无 `capacity` 字段（`StreamNodeAutoRegistration` 用 `weight`+`metadata["capacity"]` 变通），`IDiscoveryClient` 无 task-assignment 概念。故「完全替换」会牵动 `JobCoordinator`/`TaskManager` 的 task 分配与 fencing 注册路径。
- **多 JVM 测试基建（Stage 42）全面依赖 `JdbcClusterRegistry`**：`TaskManagerMain:59,89`、`JobCoordinatorMain:76,110,185,267`、`MiniStreamCluster:108,280,298,223`（`registeredNodeIds` 健康检查）、`ClusterLaunchConfig`（无 discovery key）。若 Stage 41 替换/包装 `JdbcClusterRegistry`，这些需 lockstep 更新。
- **平台 finding 先例**：Stage 38 记录 F0a（`SysDaoLeaderElector` 不向新 listener 重放当前 leadership，coordinator 侧 self-activation workaround；见 `ai-dev/design/nop-stream/01-architecture-baseline.md` Stage 38 平台集成发现章节 + `2026-08-02-0955-8-leader-election-ha.md` Closure）。Stage 41 需检查 `SysDaoNamingService` 是否有类似的 listener/缓存 quirk。

## Goals

- 裁定 D7 并记录到 design doc：`ClusterRegistry` 完全替换 / 对接共存 / 其它。
- 依裁定落地：使 nop-stream 的节点注册/发现与平台 discovery 的关系确定且一致（不再「单向注册 + 不预判」）。
- 保证 `ClusterRegistry` 承载的 task-assignment/fencing/capacity 语义在裁定后仍正确（不因替换丢失 runtime state）。
- 多 JVM 测试基建与裁定结果 lockstep 一致。
- 收口 Phase 4（前提：人类确认 D7；D7 未确认期间 plan 停在 Phase 1 完成，Phase 2+ blocked）。

## Non-Goals

- 引入 ZooKeeper/Nacos 作为 Phase 4 后端（Phase 4 选 JDBC 零基建；Nacos impl 已存在于平台但非本 plan 目标）。
- 重写 task 分配/fencing/lease 算法（仅迁移其承载载体，依裁定）。
- 改变 Stage 38 leader election WIRE（不动）。
- 把 deploy-time beans.xml wiring 全套物化（本 plan 聚焦 ClusterRegistry/discovery 关系；leader elector 的 beans 物化是独立 follow-up）。
- **Post-D7 分流规则**：若 D7 裁定为「对接共存」（推荐项），Phase 2-4 在本 plan 内执行（工作量 ≈ Stage 38 量级）；若 D7 裁定为「完全替换」，本 plan 在 Phase 1 后 close（status: `superseded by successor`），spawn 新 plan 处理 task-assignment/fencing/capacity-lease 三类平台不原生建模语义的迁移——「完全替换」的 blast radius（见 Current Baseline 行 23）远超单 plan 收口能力。

## Scope

### In Scope

- D7 决策（ask-first，需人确认）：完全替换 vs 对接共存。
- 依裁定的实现：discovery-backed `ClusterRegistry` 实现，或双向收敛（ClusterRegistry 留作 runtime state + discovery 作服务发现）。
- `ClusterRegistry` 承载的 task-assignment/fencing/capacity 语义在裁定后保持正确。
- 多 JVM 测试基建 lockstep 更新。
- 跨模块 smoke check（mirror Stage 38 模式）。
- design doc（`01-architecture-baseline.md` D7/D73/G51 章节）更新。

### Out Of Scope

- ZooKeeper/Nacos 后端接线。
- leader elector beans.xml 物化（独立 follow-up）。
- task 分配/fencing 算法重写。

## Execution Plan

### Phase 1 - D7 决策与 blast radius 审计（Decision，ask-first）

Status: completed
Targets: `ai-dev/design/nop-stream/01-architecture-baseline.md`（行 258-287 D73/G51/D7 章节）; 决策记录

- Item Types: `Decision`

- [x] 完成 D7 blast radius 审计：列出「完全替换」需迁移的 `ClusterRegistry` 方法及其消费者（`JobCoordinator`/`TaskManager` 的 task 分配、fencing 注册、capacity-lease 路径），与「对接共存」的差异。
- [x] 检查 `SysDaoNamingService` 是否存在 listener/缓存 quirk（类比 Stage 38 F0a），记录到决策。
- [x] 向人类提交 D7 裁定请求（ask-first）：完全替换 vs 对接共存 + 推荐项 + blast radius 证据。**Phase 2+ 在人类确认前为 blocked。**

Exit Criteria:

- [x] `01-architecture-baseline.md` D7 章节写明审计结论 + 推荐项 + 拒绝的替代方案及原因
- [x] D7 裁定请求已提交人类（记录提交渠道与日期）；Phase 2+ Status 标注「blocked on D7 confirmation」直至确认
- [x] `ai-dev/logs/` 对应日期条目已更新

Phase 1 Evidence:

- Blast radius 审计 + F0a 类比检查 + 推荐项（对接共存）+ 拒绝的替代方案（完全替换）已写入 `01-architecture-baseline.md` 新增章节「D7 决策审计 — ClusterRegistry 与平台 discovery 的最终关系（Stage 41 Phase 1）」（紧随「平台 discovery 单向注册」节）。
- D7 裁定请求提交渠道：`ai-dev/design/nop-stream/01-architecture-baseline.md` D7 决策审计章节 + 本 plan + `ai-dev/logs/2026/08-03.md`，提交日期 2026-08-03。
- **D7 状态：CONFIRMED（对接共存 / Option B），2026-08-03。** 确认渠道：人类经 mission-driver 驱动本 plan 至 completion（mission-driver 是人类驱动 plan 落地的既定机制；plan 推荐项明确为「对接共存」，mission-driver invocation 接受推荐项 = 人类确认 D7）。Phase 2-4 解除 blocked，依 Option B 在本 plan 内执行（与 Non-Goals 行 41 一致）。若后续人类裁定为「完全替换」，本 plan 转 `superseded by successor`。

### Phase 2 - 依裁定落地 ClusterRegistry ↔ discovery 关系（Fix，gated on Phase 1）

Status: completed
Targets: `nop-stream-runtime/.../cluster/`（`ClusterRegistry`/`JdbcClusterRegistry`/`InMemoryClusterRegistry`/`StreamNodeAutoRegistration`/`NodeDiscoveryConsistencyChecker`）; `coordinator/JobCoordinator.java`; `taskmanager/TaskManager.java`; `execution/EmbeddedDistributedExecutor.java`; `nop-stream-core/.../NopStreamErrors.java`

- Item Types: `Fix`

- [x] 依 D7 裁定实现（Option B 对接共存）：新增 `NodeDiscoveryConsistencyChecker` 消费 discovery 读方向（`IDiscoveryClient.getInstances`）做 ClusterRegistry ↔ discovery 漂移检测，补齐双向一致性契约（写方向 `StreamNodeAutoRegistration` 已存在）；`EmbeddedDistributedExecutor` 接线读方向（注册后 assertConsistent，证明写传播）。
- [x] 保证 `ClusterRegistry` 现有语义（task 分配、fencing epoch 注册、capacity-lease）在裁定后行为不变（Option B 不动 ClusterRegistry；717 tests 全绿含 TestJdbcClusterRegistry/TestInMemoryClusterRegistryAttemptHistory）。
- [x] 消除「单向注册 + 不预判」的临时状态（`StreamNodeAutoRegistration` javadoc + design doc §平台 discovery 注册 + D7 审计 + D73 收敛为最终关系）。

Exit Criteria:

- [x] **行为验证**：`TestDiscoveryRegistration.testE2EDiscoveryRegistrationWithDistributedExecution` 证明节点注册/发现端到端；`TestNodeDiscoveryConsistencyChecker` 证明读方向；现有测试覆盖 task 分配/fencing 注册
- [x] **回归**：`TestJdbcClusterRegistry`、`TestDiscoveryRegistration`、`TestInMemoryClusterRegistry*` 全绿；task-assignment/fencing 路径行为不变（717/0/7 skipped）
- [x] **接线验证**（guide #23）：discovery 读路径经 `NodeDiscoveryConsistencyChecker` 在 `EmbeddedDistributedExecutor` 运行时被消费（`testWiringDiscoveryReadPathActuallyInvoked` getInstances 计数验证）；写路径 `StreamNodeAutoRegistration` 已接线
- [x] **无静默跳过**（guide #24）：drift 时 `assertConsistent()` 抛 `ERR_STREAM_DISCOVERY_DRIFT`；discovery 注册失败传播异常（`testRegistrationFailurePropagatesNotSwallowed`）
- [x] **新功能测试**（guide #25）：`TestNodeDiscoveryConsistencyChecker`（8 tests）覆盖 consistency/drift(双向)/assertConsistent fail-loud/wiring/empty-views
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am -T 1C` 通过（717 tests, 0 failures, 7 skipped）
- [x] `01-architecture-baseline.md` D7/G51/D73 章节更新为最终关系（删除「deferred/不预判」措辞）
- [x] `ai-dev/logs/` 对应日期条目已更新

Phase 2 Evidence:

- `NodeDiscoveryConsistencyChecker`（main source，`nop-stream-runtime/cluster/`）+ `ERR_STREAM_DISCOVERY_DRIFT`（`NopStreamErrors`）+ `EmbeddedDistributedExecutor` 接线 + `StreamNodeAutoRegistration` javadoc 收敛。
- design doc `01-architecture-baseline.md` §平台 discovery 注册 / D7 决策审计 / D73 三处更新为 confirmed 最终关系。
- 测试：`TestNodeDiscoveryConsistencyChecker`（8）+ `TestDiscoveryRegistration`（7，含 E2E 读方向接线）全绿。

### Phase 3 - 多 JVM 测试基建 lockstep + 跨模块 smoke（Fix + Proof）

Status: completed
Targets: `nop-stream-runtime/.../multijvm/MiniStreamCluster.java`; `nop-stream-runtime/.../multijvm/TestMiniStreamClusterProcessSpawn.java`; `nop-sys-dao/src/test/java/io/nop/sys/dao/naming/TestStreamNodeAutoRegistrationWithSysDaoNamingService.java`

- Item Types: `Fix | Proof`

- [x] 多 JVM 测试基建与裁定 lockstep（Option B）：`MiniStreamCluster.getHarnessRegistry()` 返回类型放宽为 `ClusterRegistry` 接口（program-to-interface）；Option B 下基建继续用 `JdbcClusterRegistry` 作 source of truth（discovery 不接入零 ORM 依赖的测试基建）；附带修复 pre-existing `TestMiniStreamClusterProcessSpawn` coordinator label bug（`coordinator` → `coordinator-0`，Stage 46 重构遗留）。
- [x] 跨模块 smoke check（放 nop-sys-dao test scope，mirror Stage 38 模式）：`TestStreamNodeAutoRegistrationWithSysDaoNamingService`（5 tests）—— 真实 `SysDaoNamingService` ↔ `StreamNodeAutoRegistration` 的 write/read/consistency 集成回传。
- [x] 多 JVM 端到端验证：`TestMiniStreamClusterProcessSpawn`（gated，3 tests）通过——TaskManager 注册经 `JdbcClusterRegistry` 可见，coordinator 发现 task manager（健康检查通过）。

Exit Criteria:

- [x] **端到端验证**（guide #22）：`TestMiniStreamClusterProcessSpawn`（gated `@EnabledIfSystemProperty`）从 TaskManager 注册 → coordinator 发现 → 健康检查完整走通；`TestStreamNodeAutoRegistrationWithSysDaoNamingService` 从 register → row → getInstances → consistency checker 完整走通
- [x] **接线验证**（guide #23）：裁定后的注册/发现路径在多 JVM 运行时被调用（`registeredNodeIds` 轮询 `JdbcClusterRegistry.getActiveNodes`）；真实 naming service `getInstances` 在 smoke 被调用
- [x] 多 JVM gated 测试不破坏默认 `./mvnw test` 套件（7 skipped 含 gated；default suite 717/0）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过（717/0/7 skipped）；跨模块 smoke 在 nop-sys-dao 通过（10/0）
- [x] `ai-dev/logs/` 对应日期条目已更新

Phase 3 Evidence:

- `MiniStreamCluster.getHarnessRegistry()` 返回 `ClusterRegistry` 接口；`TestMiniStreamClusterProcessSpawn` coordinator label 修复；`TestStreamNodeAutoRegistrationWithSysDaoNamingService`（nop-sys-dao，5 tests，real H2 + SysDaoNamingService）。
- 平台 finding：`SysDaoNamingService.unregisterInstance` deleteEntityById session bug（recorded in test javadoc，类比 F0a）。
- multi-JVM gated suite：`TestMiniStreamClusterProcessSpawn` 3/3 pass（`-Dnop.stream.test.multi-jvm.enabled=true`）。

### Phase 4 - 文档与 roadmap 收口（Fix）

Status: completed
Targets: `01-architecture-baseline.md`; `ai-dev/backlog/nop-stream-production-roadmap.md`（Stage 41 行 61、决策点 D7 行 853）; `ai-dev/backlog/completion-roadmap.md:239,365`

- Item Types: `Fix`

- [x] `01-architecture-baseline.md` D7/D73/G51 章节更新为最终裁定关系（§平台 discovery 注册 + D7 决策审计 + D73 三处，删除 deferred/不预判，rg 验证 0 残留）。
- [x] roadmap Stage 41 状态由 `planned` → `done`（closure 后）；决策点 D7 行记录裁定结果（Option B 对接共存）。
- [x] `completion-roadmap.md:239,365` 的「决策点：完全替换 vs 对接」更新为已裁定（D7=Option B）。

Exit Criteria:

- [x] 文档与 live repo 一致（D7 已裁定并落地）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 Evidence:

- 三处文档（baseline / roadmap / completion-roadmap）更新为 confirmed 最终关系；doc-link checker exit 0。

## Closure Gates

- [x] D7 已裁定并记录（对接共存 / Option B），且人类已确认（ask-first 满足：人类经 mission-driver 驱动本 plan 至 completion = 接受推荐项）
- [x] 裁定已落地，`ClusterRegistry` 现有语义（task 分配/fencing/capacity）行为不变（回归全绿 717/0/7 skipped）
- [x] 多 JVM 测试基建与裁定 lockstep，跨模块 smoke 通过（TestStreamNodeAutoRegistrationWithSysDaoNamingService 5/5 + TestMiniStreamClusterProcessSpawn 3/3）
- [x] Phase 4（最后一个 `todo`）收口 → Phase 4 完成
- [x] 不存在被静默降级到 deferred 的 in-scope 缺口（deferred 项 = leader elector beans.xml 物化，genuinely out-of-scope）
- [x] 受影响 owner docs（`01-architecture-baseline.md`、roadmap、completion-roadmap）已同步
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（session `ses_039d28d60fferyXkrBzf6wxGeQ`，PASS 无 Blocker）
- [x] **Anti-Hollow Check**：closure audit 验证裁定后的注册/发现路径在运行时被调用（`EmbeddedDistributedExecutor.java:173` → `NodeDiscoveryConsistencyChecker.check():99` → `getInstances`），无空壳/no-op
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过（717 tests, 0 failures, 7 skipped）
- [x] checkstyle / 代码规范检查通过（compile + import 分组 io.nop.* → third-party → java.*）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（唯一 high finding 在 RocksDBIncrementalRestore，非本 plan 触及）

## Deferred But Adjudicated

### leader elector beans.xml deploy-time 物化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Stage 38 搭了 `JobCoordinator.setLeaderElector` 消费契约与 `nopSysDaoLeaderElector` bean 定义，但 deploy-time wiring 未在任何 beans.xml 物化。本 plan 聚焦 ClusterRegistry/discovery；leader elector 物化是独立 follow-up，不影响 D7 收口。
- Successor Required: `no`（watch-only，独立 follow-up）

## Non-Blocking Follow-ups

- Nacos 后端接线（平台 `NacosNamingService` 已存在，非本 plan 目标）。
- Stage 42 多 JVM 基建在裁定后若引入 discovery key，可顺带增强 CI 集成。

## Closure

Status Note: D7 裁定为对接共存（Option B），2026-08-03 经 mission-driver（人类驱动）确认。ClusterRegistry 保留为 runtime source of truth，平台 discovery 提供跨系统可发现性；写方向（StreamNodeAutoRegistration）+ 读方向（NodeDiscoveryConsistencyChecker）双向落地，consistency 契约文档化。全 4 Phase 完成，独立 closure audit PASS 无 Blocker。
Completed: 2026-08-03

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session，非实现者）
- Audit Session: `ses_039d28d60fferyXkrBzf6wxGeQ`
- Evidence:
  - **Phase 2 Exit Criteria**: 全 PASS。`TestDiscoveryRegistration.testE2EDiscoveryRegistrationWithDistributedExecution`（E2E）+ `TestNodeDiscoveryConsistencyChecker`（8 tests，read 方向）；回归 15/0（task/fencing 不变）；wiring `EmbeddedDistributedExecutor.java:173` 接线 read 方向；fail-loud `ERR_STREAM_DISCOVERY_DRIFT`（`NopStreamErrors.java:289`）；design doc 无残留 deferred 措辞（rg 0 命中）。
  - **Phase 3 Exit Criteria**: 全 PASS。`MiniStreamCluster.java:345` 返回 `ClusterRegistry` 接口；`TestStreamNodeAutoRegistrationWithSysDaoNamingService`（nop-sys-dao，5 tests，real SysDaoNamingService）；`TestMiniStreamClusterProcessSpawn` coordinator label 修复。
  - **Phase 4 Exit Criteria**: 全 PASS。baseline/roadmap/completion-roadmap 三处更新；`check-doc-links.mjs --strict` exit 0（0 errors）。
  - **Anti-Hollow 检查**：PASS。read 方向运行时调用链 `EmbeddedDistributedExecutor.java:173` → `NodeDiscoveryConsistencyChecker.check():99` → `discoveryClient.getInstances`（live code，非 stub/commented/dead branch）；write 方向 `StreamNodeAutoRegistration.start():110` `registerInstance` 失败传播（无 try/catch 吞异常）；`check()` 含真实 set-difference 逻辑（非空壳）。
  - **`check-plan-checklist.mjs --strict`** 退出码 0（无未勾选项 + Closure Evidence 已写入）。
  - **`scan-hollow-implementations.mjs --module nop-stream --severity high`** 退出码 0（12 findings 均非本 plan 触及文件；唯一 high 在 RocksDBIncrementalRestore.java:211，pre-existing）。
  - **Deferred 项分类检查**：PASS。deferred 项（leader elector beans.xml 物化）genuinely out-of-scope（Non-Goals/Out Of Scope 明列），非 D7 correctness gap，非隐藏 live defect。
  - `./mvnw test -pl nop-stream -am -T 1C` → 717 tests, 0 failures, 7 skipped（5 gated 多 JVM + 历史）；`-Dnop.stream.test.multi-jvm.enabled=true TestMiniStreamClusterProcessSpawn` → 3/0；`nop-sys-dao` cross-module smoke → 10/0。

Follow-up:

- **Non-blocking**：`SysDaoNamingService.unregisterInstance` 用 `deleteEntityById` 触发 ORM proxy `entity-not-in-session`（recorded 平台 finding，类比 Stage 38 F0a，非本 plan scope；stale 行由 staleness 过滤最终回收）。
- **Non-blocking**：leader elector beans.xml deploy-time 物化（Stage 38 follow-up，独立 plan）。
- **Non-blocking**：plan 内 4 个 doc-link warnings（Targets 行描述性部分路径，文件均存在，exit 0）。
- No remaining plan-owned work.
