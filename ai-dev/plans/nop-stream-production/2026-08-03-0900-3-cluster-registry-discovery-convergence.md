# 3 ClusterRegistry 收敛到平台 discovery（Stage 41, G51 续, 决策点 D7）

> Plan Status: active
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

Status: planned
Targets: `ai-dev/design/nop-stream/01-architecture-baseline.md`（行 258-287 D73/G51/D7 章节）; 决策记录

- Item Types: `Decision`

- [ ] 完成 D7 blast radius 审计：列出「完全替换」需迁移的 `ClusterRegistry` 方法及其消费者（`JobCoordinator`/`TaskManager` 的 task 分配、fencing 注册、capacity-lease 路径），与「对接共存」的差异。
- [ ] 检查 `SysDaoNamingService` 是否存在 listener/缓存 quirk（类比 Stage 38 F0a），记录到决策。
- [ ] 向人类提交 D7 裁定请求（ask-first）：完全替换 vs 对接共存 + 推荐项 + blast radius 证据。**Phase 2+ 在人类确认前为 blocked。**

Exit Criteria:

- [ ] `01-architecture-baseline.md` D7 章节写明审计结论 + 推荐项 + 拒绝的替代方案及原因
- [ ] D7 裁定请求已提交人类（记录提交渠道与日期）；Phase 2+ Status 标注「blocked on D7 confirmation」直至确认
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 依裁定落地 ClusterRegistry ↔ discovery 关系（Fix，gated on Phase 1）

Status: planned
Targets: `nop-stream-runtime/.../cluster/`（`ClusterRegistry`/`JdbcClusterRegistry`/`InMemoryClusterRegistry`/`StreamNodeAutoRegistration`）; `coordinator/JobCoordinator.java`; `taskmanager/TaskManager.java`; `execution/*DistributedExecutor.java`

- Item Types: `Fix`

- [ ] 依 D7 裁定实现：若「对接共存」——补齐 discovery 读方向（`IDiscoveryClient` 消费）与 ClusterRegistry 的双向一致性契约；若「完全替换」——提供 discovery-backed `ClusterRegistry` 实现并迁移 task-assignment/fencing/capacity 承载。
- [ ] 保证 `ClusterRegistry` 现有语义（task 分配、fencing epoch 注册、capacity-lease）在裁定后行为不变（focused 回归测试）。
- [ ] 消除「单向注册 + 不预判」的临时状态（`StreamNodeAutoRegistration` javadoc 与 baseline 行 287 的 deferred 措辞收敛为最终关系）。

Exit Criteria:

- [ ] **行为验证**：存在测试证明裁定后节点注册/发现/task 分配/fencing 注册的端到端正确性
- [ ] **回归**：`ClusterRegistry` 现有 focused 测试（`TestJdbcClusterRegistry`、`TestDiscoveryRegistration` 等）全绿，且 task-assignment/fencing 路径行为不变
- [ ] **接线验证**（guide #23）：discovery 读/写路径在运行时确实被 `JobCoordinator`/`TaskManager` 调用（非空壳）
- [ ] **无静默跳过**（guide #24）：discovery 不可达或冲突时显式失败，不静默 fallback
- [ ] **新功能测试**（guide #25）：列明裁定后新增/变更行为的测试
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 通过
- [ ] `01-architecture-baseline.md` D7/G51/D73 章节更新为最终关系（删除「deferred/不预判」措辞）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 多 JVM 测试基建 lockstep + 跨模块 smoke（Fix + Proof）

Status: planned
Targets: `nop-stream-runtime/.../launch/`（`TaskManagerMain`/`JobCoordinatorMain`/`ClusterLaunchConfig`/`SharedJdbcInfrastructure`）; `multijvm/MiniStreamCluster.java`; `nop-sys-dao` test scope（smoke，mirror `TestJobCoordinatorWithSysDaoLeaderElector`）

- Item Types: `Fix | Proof`

- [ ] 多 JVM 测试基建与裁定 lockstep：`TaskManagerMain`/`JobCoordinatorMain`/`MiniStreamCluster` 的节点注册/健康检查路径（当前用 `JdbcClusterRegistry.getActiveNodes()`）依裁定更新；`MiniStreamCluster.getHarnessRegistry()`（`:280`）返回类型为 concrete `JdbcClusterRegistry`（非接口），若 wrapper/replace 需调整为 `ClusterRegistry` 接口；`ClusterLaunchConfig` 补齐 discovery 相关 key（若需）。
- [ ] 跨模块 smoke check（放 nop-sys-dao test scope，mirror Stage 38 模式）：`SysDaoNamingService` ↔ nop-stream 消费者的集成回传。
- [ ] 多 JVM 端到端验证：节点注册经裁定后的载体可见，coordinator 能发现 task manager（`MiniStreamCluster` 健康检查通过）。

Exit Criteria:

- [ ] **端到端验证**（guide #22）：存在多 JVM 测试从 TaskManager 注册 → coordinator 发现 → task 分配 → 执行完整走通（gated 测试可用 `@EnabledIfSystemProperty`）
- [ ] **接线验证**（guide #23）：裁定后的注册/发现路径在多 JVM 运行时被调用（非空壳）
- [ ] 多 JVM gated 测试不破坏默认 `./mvnw test` 套件
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过；跨模块 smoke 在 nop-sys-dao 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 文档与 roadmap 收口（Fix）

Status: planned
Targets: `01-architecture-baseline.md`; `ai-dev/backlog/nop-stream-production-roadmap.md`（Stage 41 行 61、决策点 D7 行 853）; `ai-dev/backlog/completion-roadmap.md:239,365`

- Item Types: `Fix`

- [ ] `01-architecture-baseline.md` D7/D73/G51 章节更新为最终裁定关系。
- [ ] roadmap Stage 41 状态由 `todo` → `done`（closure 后）；决策点 D7 行记录裁定结果。
- [ ] `completion-roadmap.md:239,365` 的「决策点：完全替换 vs 对接」更新为已裁定。

Exit Criteria:

- [ ] 文档与 live repo 一致（D7 已裁定并落地）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] D7 已裁定并记录（完全替换 or 对接共存），且人类已确认（ask-first 满足）
- [ ] 裁定已落地，`ClusterRegistry` 现有语义（task 分配/fencing/capacity）行为不变（回归全绿）
- [ ] 多 JVM 测试基建与裁定 lockstep，跨模块 smoke 通过
- [ ] Phase 4（最后一个 `todo`）收口 → Phase 4 完成
- [ ] 不存在被静默降级到 deferred 的 in-scope 缺口
- [ ] 受影响 owner docs（`01-architecture-baseline.md`、roadmap、completion-roadmap）已同步
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证裁定后的注册/发现路径在运行时被调用（多 JVM 测试证据），无空壳/no-op
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0

## Deferred But Adjudicated

### leader elector beans.xml deploy-time 物化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Stage 38 搭了 `JobCoordinator.setLeaderElector` 消费契约与 `nopSysDaoLeaderElector` bean 定义，但 deploy-time wiring 未在任何 beans.xml 物化。本 plan 聚焦 ClusterRegistry/discovery；leader elector 物化是独立 follow-up，不影响 D7 收口。
- Successor Required: `no`（watch-only，独立 follow-up）

## Non-Blocking Follow-ups

- Nacos 后端接线（平台 `NacosNamingService` 已存在，非本 plan 目标）。
- Stage 42 多 JVM 基建在裁定后若引入 discovery key，可顺带增强 CI 集成。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<待 closure audit 填写>>

Follow-up:

- <<no remaining plan-owned work 或列明 non-blocking follow-up>>
