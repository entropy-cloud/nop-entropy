# 44-C Supervision Loop Execution Model（Region Failover 前置 #3）

> Plan Status: active
> Last Reviewed: 2026-08-03
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Item 44（successor plan 3/5）; `ai-dev/design/nop-stream/failover-design.md` §3.5（supervision loop 可行性）+ §五.3（架构前置 #3）+ §9.2（blast radius — GraphModelCheckpointExecutor）+ §9.5（scope — plan 级，硬前置为 successor 1+2）
> Mission: nop-stream-production
> Work Item: 44. Region-based failover — successor plan 3/5（supervision loop execution model）
> Related: **successor 1** `2026-08-03-1600-1-blocking-edge-materialization-point.md`（物化重放能力 — 硬前置，本 plan 重启的 consumer 消费其重放）; **successor 2** `2026-08-03-1600-2-region-identification.md`（region ID — 硬前置，本 plan 据其决定重启范围）; **successor 4** drain/reconnect（TBD — 本 plan 控制面 + plan 4 数据面 reconnect 可部分并行，§9.5）; **successor 5** per-region counter（TBD — 依赖本 plan 重启入口）; **决策 plan** `2026-08-03-1403-1-region-based-failover.md`（go confirmed）

## Purpose

将 `GraphModelCheckpointExecutor` 的 `submitAndRun`→`awaitCompletion` 全量阻塞执行模型改为 supervision loop，支持 mid-execution 检测单 task 失败并触发该 task（及其 region）的重启。本 plan 交付**控制面机制**（mid-execution 失败检测 + 单 task/region 重启调度入口 + globalRecovery 兜底保留），是 region-based failover 的"何时重启、重启谁"控制基础。

**依赖说明**：硬依赖 successor 1（物化重放能力）+ successor 2（region ID）——supervision loop 需 region ID 决定重启范围，重启的 consumer 需物化重放提供数据。本 plan 以 `draft` 就绪，执行须在 successor 1+2 land 之后（`failover-design.md` §9.5："1 与 2 是其余项的硬前置"）。

## Current Baseline

经 live 仓库核对（2026-08-03）：

- **执行模型 = 全量阻塞**：`submitAndRun`（`GraphModelCheckpointExecutor.java:717-722`）submit 全部 task → `executor.awaitCompletion()` 阻塞至全部完成。
- **失败检测仅在 awaitCompletion 返回后**：`checkTaskFailures`（`:724-730`）有 5 个 call-site（`:134,201,275,355,402`），均在 `awaitCompletion()` 返回后运行；**无 mid-execution 单 task 失败检测入口**。这 5 个 call-site 分布在不同恢复/提交路径（initial submit、restore、recovery 等）。
- **唯一恢复入口 = globalRecovery（whole-job）**：`JobCoordinator.globalRecovery()` 新 fencing token → 清空 working set → 全量重分配；无 per-task/per-region mid-execution 重启。
- **`restartCount` 仅 globalRecovery 递增**；`maxRestarts=3`；无 per-region 计数器。
- **mailbox 执行模型（Stage 17）已落地**：task 内 `processInput`/`processMail` 交错循环——mid-execution 终止 task 需与此交互（终止时未处理 mail 的处理、重启后 mailbox 状态）。
- **决策已落**：`failover-design.md` §3.5 裁定 supervision loop 属 Stage 44 scope（执行模型变更）；§五.3 + §9.2 blast radius 明确 `GraphModelCheckpointExecutor` 需改 `submitAndRun`/`awaitCompletion` 模型；§9.5 确认 plan 级 + 与 successor 4 可部分并行（控制面/数据面解耦）。

### 真正剩余的 gap

- supervision loop（mid-execution 检测 + 单 task 重启）完全缺失——本 plan 交付对象。
- 依赖 successor 1+2（region ID + 物化重放）作为重启范围与数据重放基础。

## Goals

- **supervision loop**：替代 `awaitCompletion` 的**被动 block-wait 检测模型**为**主动 mid-execution 检测**（失败不再等到 awaitCompletion 返回才被发现；`awaitCompletion` 仍可作为最终同步点保留，其返回后由 `checkTaskFailures` 做终态校验）。
- **单 task/region 重启调度入口**：检测到失败后，可终止并重启该 task（按 successor 2 的 region ID 决定范围；重启的 consumer 经 successor 1 物化重放获取数据）。
- **5 个 `checkTaskFailures` call-site 收敛**：明确每个 call-site 在 supervision loop 模型下的归属（保留为 post-completion 终态校验 / 收敛入 supervision 检测）。
- **globalRecovery 兜底保留**：region 重启失败时 fallback 到 whole-job recovery（不删除既有恢复路径）。
- **零回归**：无 region/物化的既有作业，supervision loop 退化为等价于 `awaitCompletion`（行为不变）。

## Non-Goals

- **reconnect-to-live-queue 切换**（successor 4）——本 plan 重启的 consumer 从 successor 1 物化点重放已物化数据；重放完毕后切换回 live 主 queue 的 reconnect 协议属 plan 4。
- drain/reconnect 数据面协议（successor 4）。
- per-region restart 计数器（successor 5）——本 plan 暂用 global restartCount 兜底。
- region-aware scheduling（G55）。
- 跨 JVM supervision（in-process 先正确）。
- 改变 exactly-once 语义（global epoch recovery baseline 不变，`failover-design.md` §七）。

## Scope

### In Scope

- supervision loop 执行模型（mid-execution 单 task 失败检测 + 回调，替代 awaitCompletion 全量阻塞）。
- 单 task 终止 + 重启调度入口（按 successor 2 region ID 决定重启范围；复用 successor 1 物化重放）。
- 5 个 `checkTaskFailures` call-site 收敛方案落地。
- mailbox 交互：终止 task 时未处理 mail（timer/checkpoint mail）的处理契约。
- globalRecovery 兜底保留（region 重启失败 fallback）。
- 零回归：无 region 作业 supervision loop 等价 awaitCompletion。
- E2E：有限输入 → 物化 → 单 task 失败 → 检测 → 重启重放 → 已处理数据 exactly-once 验证。

### Out Of Scope

- successor 4 reconnect-to-live-queue + drain/reconnect 完整协议。
- successor 5 per-region restart 计数器。
- region-aware scheduling（G55）。
- 跨 JVM supervision。

## Execution Plan

### Phase 1 — Supervision loop + 单 task/region 重启

Status: planned
Targets: `GraphModelCheckpointExecutor.java`（submitAndRun/awaitCompletion → supervision loop + 5 call-site 收敛）; `TaskExecutor`; `JobCoordinator`（重启入口 + globalRecovery 兜底）; owner-docs

- Item Types: `Fix | Decision | Proof`

- [ ] supervision loop：`awaitCompletion` 全量阻塞 → 可观测单 task 失败的检测模型（mid-execution 检测；行为契约记录于 owner-doc）——`Fix`
- [ ] 5 个 `checkTaskFailures` call-site（`:134,201,275,355,402`）收敛方案——`Decision`：明确每个 call-site 归属。默认方案：supervision loop 承担 mid-execution 检测；既有 `checkTaskFailures` 调用**保留为 post-completion 终态校验**（awaitCompletion 返回后确认无 FAILED 残留），与 supervision loop 并存而非互斥——mid-execution 检测负责"运行中尽早发现"，post-completion 校验负责"终态一致性兜底"。逐 call-site 核对其所属恢复路径（initial/restore/recovery）是否需调整。
- [ ] 单 task 终止 + 重启调度入口（按 successor 2 region ID 决定范围；重启 consumer 经 successor 1 物化重放）——`Fix`
- [ ] mailbox 交互契约：终止 task 时未处理 mail（timer/checkpoint mail）的处理（drain 或丢弃 + 重启后重建）；记录于 owner-doc——`Decision`
- [ ] 终止 exactly-once 安全性：论证单 task 终止不丢数据（终止触发 successor 1 物化重放，已物化数据可重放；drain 在途数据的完整协议属 successor 4，但本 plan 终止即触发重放，重放范围内 exactly-once 成立）——`Decision`
- [ ] globalRecovery 兜底保留（region 重启失败 → fallback whole-job recovery；不删除既有路径）——`Fix`
- [ ] 零回归：无 region 既有作业 supervision loop 等价 awaitCompletion——`Proof`
- [ ] E2E：有限输入 → 物化 → 注入单 task 失败 → supervision loop 检测 → 该 task/region 重启 → 物化重放 → 已处理数据 exactly-once（无丢失无重复）——`Proof`

Exit Criteria:

- [ ] supervision loop 存在并替代 `awaitCompletion` 的全量阻塞语义（mid-execution 可检测失败，行为可观测：失败不再等到 awaitCompletion 返回才被发现）
- [ ] 单 task/region 重启入口存在且被 supervision loop 调用（**接线验证** #23：失败 task 被重启，重启计数/标志位可观测）
- [ ] 5 个 `checkTaskFailures` call-site 收敛方案已落地（每个 call-site 归属明确，无路径丢失失败检测）
- [ ] mailbox 交互契约已定义并落地（终止时未处理 mail 的处理可观测）
- [ ] **端到端验证**（#22）：有限输入 → 物化 → 单 task 失败 → supervision loop 检测 → 该 task/region 重启 → 物化重放 → **已处理数据 exactly-once**（断言重启发生 + 重放数据无丢失无重复）。**范围说明**：本 plan E2E 覆盖"重放完毕为止"；重放后切换回 live queue 持续处理（reconnect）属 successor 4——本 plan E2E 用有限输入确保重放即终点，避免对 successor 4 reconnect 的隐式依赖（Anti-Hollow #22：不靠 successor 4 也能验证本 plan 的控制面 + 重放 exactly-once）。
- [ ] **无静默跳过**（#24）：supervision loop 检测到失败必须显式处理（重启或升级 globalRecovery），不静默 `continue`/吞异常
- [ ] globalRecovery 兜底保留且可达（region 重启失败时触发 fallback，断言可观测）
- [ ] 零回归：无 region 既有作业行为不变，既有测试全绿
- [ ] owner-doc: `failover-design.md` §3.5/§五.3 supervision loop 落地状态 + call-site 收敛方案 + mailbox 交互契约; `01-architecture-baseline.md` §五 Restart Strategy 如涉及执行模型则同步
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] supervision loop mid-execution 检测 + 单 task/region 重启端到端可用（重放范围内 exactly-once）
- [ ] 5 个 `checkTaskFailures` call-site 收敛方案落地，无路径丢失失败检测
- [ ] mailbox 交互契约落地
- [ ] globalRecovery 兜底保留且可达
- [ ] 零回归（无 region 既有作业行为不变）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [ ] checkstyle / 代码规范检查通过
- [ ] 不存在被静默降级到 deferred 的 in-scope gap（reconnect/drain 协议 + per-region counter 已显式归属 successor 4/5，非 in-scope）
- [ ] 受影响 owner docs 已同步
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：supervision loop → 失败检测 → 单 task 重启 → 物化重放 调用链运行时连通（不只是类型系统）；无空方法体/静默跳过/no-op
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### reconnect-to-live-queue 切换 + 完整 drain/reconnect 协议

- Classification: `out-of-scope improvement`（数据面协议，属 successor 4）
- Why Not Blocking Closure: 本 plan 重启的 consumer 从 successor 1 物化点重放已物化数据（控制面 + 重放 exactly-once 成立）；重放后切换回 live queue 的 reconnect + drain 在途数据的完整安全协议属 successor 4。本 plan E2E 用有限输入确保重放即终点，不依赖 successor 4。
- Successor Required: yes（successor 4）

### per-region restart 计数器

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 计数器与上限（防重启风暴）属 successor 5；本 plan 交付重启入口，计数器在其上构建。本 plan 暂用 global restartCount 兜底。
- Successor Required: yes（successor 5）

## Non-Blocking Follow-ups

- supervision loop 调度策略优化（重启优先级、限流、退避）
- 跨 JVM supervision（in-process 先正确）

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<每条 Exit Criterion / Closure Gate 验证结果 + check-plan-checklist 退出码 + Anti-Hollow 结果>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
