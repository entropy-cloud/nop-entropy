# 44-E Per-Region Restart Limit Configurability（Region Failover 前置 #5）

> Plan Status: completed
> Last Reviewed: 2026-08-03
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Item 44（successor plan 5/5）; `ai-dev/design/nop-stream/failover-design.md` §五.5（per-region restart 计数器需求）+ §9.5（scope — sub-plan 级，优先级 #5）; `ai-dev/design/nop-stream/01-architecture-baseline.md:502`（per-region counter 注记）
> Mission: nop-stream-production
> Work Item: 44. Region-based failover — successor plan 5/5（per-region restart limit configurability）
> Related: **successor 3** `2026-08-03-1600-3-supervision-loop-execution-model.md`（supervision loop — 已 completed；交付了 in-memory per-region counter，本 plan 在其上暴露可配置上限）; **successor 4** `2026-08-03-2107-1-drain-reconnect.md`（drain/reconnect — 独立，本 plan 不依赖）; **决策 plan** `2026-08-03-1403-1-region-based-failover.md`（go confirmed）

## Purpose

将 `SupervisionLoop` 的 per-region restart 上限（`DEFAULT_MAX_RESTARTS_PER_REGION = 3`）从 package-private 硬编码常量升级为生产可配置参数，使作业可根据 region 大小 / 业务容错需求调整 scoped restart 上限。

### 审查裁正记录（2026-08-03 draft review）

本 plan 初稿（draft v1）基于"per-region counter 跨 `globalRecovery` cycle 重置导致 3×3=9 次重启风暴"的动机。独立子 agent 对抗性审查（live repo 核对）发现该前提**不成立**：

- **LOCAL 路径**（`StreamExecutionEnvironment.execute()` → `GraphModelCheckpointExecutor.executeWithCheckpoint()`（static）→ `submitAndRun()`（private static）→ `SupervisionLoop.run()`（public static））与 **DISTRIBUTED 路径**（`EmbeddedDistributedExecutor`/`RpcDistributedExecutor` → `JobCoordinator` → `globalRecovery()`）**完全分离**——两路径互不调用。
- SupervisionLoop 的 in-memory counter 在单次 `run()` 内已正确跨多次 region 重启持久（这正是现有设计），耗尽后 `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED` 直接抛给 LOCAL 路径调用者（无 globalRecovery re-execute）。
- 不存在"counter 跨 cycle 重置"问题，因不存在"cycle"。

因此本 plan（draft v2）将 scope 收窄为 `failover-design.md` §9.5 "sub-plan 级" 的真实 gap：**仅交付可配置上限**。counter 生命周期（in-memory scoped to one `run()`）保持不变——它已经正确工作。

## Current Baseline

经 live 仓库核对（2026-08-03，含独立子 agent 对抗性审查验证）：

- **Per-region counter 已存在且正确工作**：`SupervisionLoop.run`（`:183`）`Map<RegionId, AtomicInteger> regionRestartCounts`——单次 `run()` 内跨多次 region 重启持久，耗尽（`:217`）抛 `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED`。行为正确，无需改生命周期。
- **`DEFAULT_MAX_RESTARTS_PER_REGION = 3` 不可生产配置**：该常量（`SupervisionLoop.java:136`）为 `static final`（package-private）。`SupervisionLoop.run(...)` 的 package-private 全参签名（`:165`）允许传入 custom 值，但**无生产配置路径**（`StreamExecutionEnvironment` / `GraphModelCheckpointExecutor` / `CheckpointCoordinator` 均无对应 setter 或 config）——public `run(...)` 签名（`:154`）始终使用 default 3。
- **5 个 call-site 使用 public 签名**：`GraphModelCheckpointExecutor.submitAndRun`（`:738`）调用 public `SupervisionLoop.run(...)`（使用 default）。5 个 `submitAndRun` call-site（`:131,198,272,336,399`）均在 `executeWithCheckpoint` 的不同恢复/提交路径，全部 static。
- **Region ID 可用于索引**：`SubtaskTask.getRegionId()` / `RegionDecomposition` 全链路传播（successor 2 已落地）。
- **与 global counter 独立**：`JobCoordinator.restartCount`（`:224`，DISTRIBUTED 路径）与 `SupervisionLoop.regionRestartCounts`（LOCAL 路径）互不影响——两路径分离，无交互。

### 真正剩余的 gap

- `DEFAULT_MAX_RESTARTS_PER_REGION` 不可生产配置——无 config path / setter 暴露给用户。→ 本 plan 交付。

## Goals

- **可配置 per-region maxRestarts**：经生产配置路径（`StreamExecutionEnvironment` config 或等价）设置 per-region restart 上限，传入 `SupervisionLoop.run`，默认值保持 3。
- **零回归**：successor 3 已验证的 consumer-only region 重启行为不变（不配置时 default 3 行为与现状一致）。

## Non-Goals

- **Counter 生命周期变更**：counter 保持 in-memory scoped to one `run()`（已正确工作）。不改为 job-lifetime 持久、不接入 `JobCoordinator`、不跨"cycle"持久（无 cycle 可跨——LOCAL/DISTRIBUTED 路径分离）。
- **Counter checkpoint 持久化**：无必要（counter 生命周期不变）。
- **Per-region 退避策略**（exponential backoff / restart delay）：属调度优化（region-aware scheduling, G55 scope）。
- **Drain/reconnect**：属 successor 4（独立 plan）。
- **连接 LOCAL 与 DISTRIBUTED 路径**：将 SupervisionLoop 的 region-scoped restart 带到分布式 JobCoordinator 路径，属重大架构变更，远超 sub-plan 级，不在本 plan scope。

## Scope

### In Scope

- Per-region maxRestarts 经生产配置路径暴露（`StreamExecutionEnvironment` config / setter → `GraphModelCheckpointExecutor.executeWithCheckpoint` → `submitAndRun` → `SupervisionLoop.run` 全参签名）。
- 默认值 3（与当前 `DEFAULT_MAX_RESTARTS_PER_REGION` 一致）。
- 零回归：不配置时行为与现状一致。

### Out Of Scope

- Counter 生命周期变更（已正确工作）。
- Counter checkpoint 持久化。
- LOCAL ↔ DISTRIBUTED 路径连接。
- Per-region 退避策略。
- Drain/reconnect（successor 4）。

## Execution Plan

### Phase 1 — 可配置 per-region maxRestarts

Status: completed
Targets: `StreamExecutionEnvironment`（config/setter）; `GraphModelCheckpointExecutor.executeWithCheckpoint`/`submitAndRun`（参数穿透）; `SupervisionLoop.run`（全参签名使用传入值）; owner-docs

- Item Types: `Fix | Proof`

- [x] **配置路径暴露**：`CheckpointConfig`（已有 fluent builder pattern，`StreamExecutionEnvironment.execute():260` 已传递给 `executeWithCheckpoint`）增加 per-region maxRestarts 配置项（字段 + setter，默认 3）——`Fix`
- [x] **参数穿透**：config 值从 `CheckpointConfig` 读取，经 `submitAndRun`（private static wrapper，5 个 call-site `:131,198,272,336,399`）传入 `SupervisionLoop.run` **已存在的** package-private 全参签名（`:165`，已接受 `int maxRestartsPerRegion`）。`executeWithCheckpoint` 2/3 签名已接收 `CheckpointConfig`（无需新增参数），签名 2 内部使用 default——`Fix`
- [x] 组件级测试：custom 值（如 1 或 5）经配置路径传入后，`SupervisionLoop.run` 在第 N 次失败时抛 `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED`（N = custom 值）；不配置时 default 3 行为不变——`Proof`

Exit Criteria:

- [x] Per-region maxRestarts 经生产配置路径暴露（断言可观测：设置 custom 值后 SupervisionLoop 使用该值而非 default 3）
- [x] 参数穿透完整（**接线验证** #23：config → executeWithCheckpoint → submitAndRun → SupervisionLoop.run 全参签名，custom 值确实到达 SupervisionLoop，断言可观测）
- [x] **无静默跳过**（#24）：config 值非法（< 0）时 fail-fast（非静默 fallback 到 default）
- [x] **新功能测试**（#25）：新增配置项有对应测试验证 custom 值生效 + default 行为零回归
- [x] 零回归：不配置时 default 3 行为与 successor 3 验证的现状一致
- [x] owner-doc: `failover-design.md` §五.5 per-region counter 可配置落地 + 审查裁正记录（counter 生命周期不变的理由）; `01-architecture-baseline.md:502` 注记更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Per-region maxRestarts 经生产配置路径暴露且可配
- [x] 参数穿透完整（config → SupervisionLoop.run）
- [x] 零回归（不配置时行为不变）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [x] checkstyle / 代码规范检查通过
- [x] 不存在被静默降级到 deferred 的 in-scope gap
- [x] 受影响 owner docs 已同步（含审查裁正记录）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：config → 参数穿透 → SupervisionLoop 使用 custom 值 调用链运行时连通（测试断言 custom 值生效）；无空方法体/静默跳过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### Counter 生命周期持久化（job-lifetime / 跨 cycle）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 独立子 agent 审查（2026-08-03）确认 LOCAL（SupervisionLoop）与 DISTRIBUTED（globalRecovery）路径完全分离——counter 在单次 `run()` 内已正确跨多次 region 重启持久，不存在"跨 cycle 重置"问题。failover-design §五.5 原始需求（"scoped restart 不走 globalRecovery，需独立 counter"）已由 successor 3 满足。将 counter 改为 job-lifetime 持久需先连接 LOCAL/DISTRIBUTED 路径（重大架构变更，远超 sub-plan 级）。
- Successor Required: no（除非未来连接两路径）

## Non-Blocking Follow-ups

- 连接 LOCAL 与 DISTRIBUTED 路径（将 region-scoped restart 带到 JobCoordinator 分布式路径）——重大架构变更，需独立 design + plan
- Per-region 退避策略（exponential backoff / restart delay）
- Per-region 上限的动态调整（据 region 大小 / 历史失败率自适应）

## Closure

Status Note: Per-region restart 上限已从 `SupervisionLoop` package-private 硬编码常量升级为 `CheckpointConfig` 生产可配置参数（默认 3，fail-fast 拒绝负值）。参数经 `submitAndRun`（5 call-site 全部穿透）传入 `SupervisionLoop.run` 已存在的全参签名。Counter 生命周期不变（in-memory scoped to one `run()`，审查裁正确认 LOCAL/DISTRIBUTED 路径分离，无"跨 cycle 重置"问题）。7 tests 全绿（4 CheckpointConfig + 3 SupervisionLoop behavioral），739 tests 零回归。Stage 44 region-based failover 全部 5 个 successor plans 已交付。
Completed: 2026-08-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore，read-only closure audit，task ses_0383fc1f2ffedDTb3Zo69frCQF）— VERDICT: PASS（无 blocker）
- Evidence: 独立 read-only 审计确认全部 exit criteria 在 live 代码中满足。`CheckpointConfig.DEFAULT_MAX_RESTARTS_PER_REGION = 3`（`:31`）经 field `maxRestartsPerRegion`（`:97`，默认 3）+ getter（`:164-166`）+ fail-fast setter 拒绝负值抛 `IllegalArgumentException`（`:176-183`）+ fluent `Builder.maxRestartsPerRegion`（`:372-375`）暴露。参数穿透：`submitAndRun` 新增 `int maxRestartsPerRegion` 参数（`:748-755`），5 个 call-site（`:131,199,274,339,403`）全部传 `checkpointConfig.getMaxRestartsPerRegion()`，无遗漏；`executeWithCheckpoint(StreamModel,PartitionedPlan,DeploymentPlan)` 内部 `new CheckpointConfig()` 使用 default 3（零回归）。`SupervisionLoop.run` 全参签名用 `count > maxRestartsPerRegion` 抛 `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED`（`SupervisionLoop.java:252-258`）。测试非 stub：`TestCheckpointConfig`（default/setter/builder/fail-fast）、`TestSupervisionLoopRestartLimitConfig`（real 2-region always-failing-sink graph + 可观测 consume 计数：=1→2、=0→1、default=3→4 + assertThrows `supervision-restart-exhausted`）。无 TODO/FIXME/空方法体/静默跳过。docs 已更新：`failover-design.md` §五.5（`:175-183`）+ `01-architecture-baseline.md:502` 均含 `CheckpointConfig.maxRestartsPerRegion` + counter 生命周期说明。Plan Status + Phase 1 Status 均 `completed`，21 个 checklist item 全部 `[x]`，0 个残留 `- [ ]`。`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` → 739 tests 0 failures。`scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0。

Follow-up:

- 连接 LOCAL（SupervisionLoop）与 DISTRIBUTED（JobCoordinator.globalRecovery）路径以将 region-scoped restart 带到分布式路径（重大架构变更，需独立 design + plan）。
- Per-region 退避策略（exponential backoff / restart delay）——G55 region-aware scheduling scope。
- Per-region 上限动态自适应（据 region 大小 / 历史失败率）。
