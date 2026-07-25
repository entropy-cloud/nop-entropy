# 27 — Targeted failover 可行性裁定 + 设计（region 模型）

> Plan Status: active
> Last Reviewed: 2026-07-26
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 27 (G57); `ai-dev/design/nop-stream/01-architecture-baseline.md` §五 Restart Strategy（line 317 显式 deferred per-region 计数器给 Stage 27）; `ai-dev/design/nop-stream/checkpoint-design.md` §8.1/§8.1.1（global epoch recovery baseline，region failover 为后续优化）; `ai-dev/analysis/nop-stream/08-gap-analysis.md` (G57 P2; G28 P2; G55 P2); Stage 20 plan Deferred（G28 design-gated，需先起草 region/drain/reconnect 设计文档）; Stage 25 plan Deferred（per-region restart 计数器）
> Mission: nop-stream-production
> Work Item: 27 (Phase 1 — 分布式运行时基础)
> Related: **Stage 20** (`2026-07-25-2200-2-partial-subtask-recovery.md`, done — G29 subtask restore；**G28 design-gated deferred 到本 Stage**)；**Stage 25** (`2026-07-26-0207-2-per-task-failure-detection.md`, done — **per-region restart 计数器 deferred 到本 Stage**)；**Stage 24** (`2026-07-26-0207-1-deployment-plan-discovery.md`, done — DeploymentAssignment)

## Purpose

本 plan 的**首要交付物是一个可行性裁定（go/no-go）+ 设计文档**：在 nop-stream 当前架构下，targeted failover 是否能提供超越 `globalRecovery()` 的收益。

draft review（独立子 agent）已通过 live code 验证确认一个**架构级事实**：`JobGraphGenerator.determinePartitionType()`（`:546-552`）从不返回 `BLOCKING`——所有边均为 pipelined（`PIPELINED`/`PIPELINED_BOUNDED`，`pipelined=true`）。因此当前每个 JobGraph = 单个 pipelined connected component = **单个 region**，vertex 级 targeted failover 等同 global failover，**零收益**。

本 plan 消费 Stage 20/25 显式 deferred 到本 Stage 的 G28（partial/region 恢复）与 per-region restart 计数器。Phase 1 必须回答：是否存在 subtask 级或引入 region 边界的可行路径使 targeted failover 有收益？若**不可行**，plan 转 `deferred` 并记录架构前置（blocking edge / region 边界），deferred 项保持 deferred 到具备前置的 successor（Stage 44）。若**可行**，Phase 2/3 实施。

## Current Baseline

经 live 仓库核对（含 draft review 独立验证）：

- **唯一恢复入口 = `globalRecovery()`**（`JobCoordinator.java:647-699`）：新 fencing token → 清空内存 working set → `assignTasks()` 全量重分配。所有失败信号（节点 lease 丢失 `:626`、per-task stall `detectFailures():603-621`、per-task FAILED 上报 `reportTaskStatus():526`）汇聚到 `globalRecovery()`。grep 全仓生产 caller 仅 `detectFailures` + `reportTaskStatus`。
- **无 per-task/per-subtask/per-region 恢复方法**；无 `recovery/` 包。
- **region 概念不存在**：grep `region|Region` 在 nop-stream 生产代码零匹配（仅 `JobCoordinator.java:151-153,649-650` 注释 forward-looking 提及本 Stage）。
- **【架构级事实，draft review 已验证】所有 edge = pipelined**：`JobGraphGenerator.determinePartitionType()`（`:546-552`）逻辑 = null partitioner → `PIPELINED`（`:549`），非 null → `PIPELINED_BOUNDED`（`:552`）。`ResultPartitionType`（`:49/:59/:70`）中 `PIPELINED(true,false)` 与 `PIPELINED_BOUNDED(true,true)` 均 `pipelined=true`；唯 `BLOCKING(false,true)` 的 `pipelined=false` **从不被产生**。因此每个 JobGraph = 单 pipelined connected component = **单 region**。
- **restart 计数器为 global-only**：`restartCount`（`:155`）仅 `globalRecovery()` 递增；`maxRestarts=3`（`:158`）。`01-architecture-baseline.md:317` 显式记录 scoped 重启需 per-region 计数器（deferred 到本 Stage）。
- **Stage 20 G28 deferred 根因**（plan `2026-07-25-2200-2` lines 12,92-97）：pipelined 有界队列按引用直连下，local 单 subtask 重启**结构上不可行**——下游死→上游 `queue.put()` 永久阻塞；上游死→下游 channel 不 close 永挂；`checkTaskFailures` 在 `awaitCompletion` 后运行（线程已退出），无法 mid-execution 重启。
- **restore 为 whole-job**：`CheckpointCoordinator.restoreFromCheckpoint()`（`:644`）无 partial variant；`GraphModelCheckpointExecutor.restoreTaskStatesFromSource()`（`:886-903`）遍历全部 vertex/subtask。G29（`restoreFromEpoch` epochId 透传，Stage 20 done）为 per-subtask 独立恢复验证，但入口仍 whole-job。
- **checkpoint-design.md §8.1.1 立场**（lines 633-651）：global epoch recovery 为 baseline；"region failover benefits only emerge after Stage 25/44 introduce the region concept"。

### 真正剩余的 gap

- **G57**：无 targeted failover（仅 globalRecovery）— in scope（本 Stage 核心裁定对象）。
- **G28**（续）：无 partial/region 恢复 — in scope（Stage 20 deferred successor）。
- **per-region restart 计数器**（Stage 25 deferred）— in scope。

### 明确 Out Of Scope

- **G55 region-aware scheduling**：本 Stage 不做按 region 的 slot/node 调度优化。
- **跨 JVM region failover + 1000-vertex scale（Stage 44）**。
- **coordinator HA（Stage 46）**。

## Goals

- **首要**：通过设计文档裁定 targeted failover 在 nop-stream 当前 all-pipelined 架构下是否可行（go/no-go）。须回答 subtask 级 blast-radius 是否有收益、是否需引入 region 边界（blocking edge）、supervision loop 是否需执行模型变更。
- 若 **go**：设计并实施 in-process targeted failover（region/blast-radius 模型 + supervision loop + 受限重启 + per-region 计数器），使单 task 失败只重启受影响子集。
- 若 **no-go**：plan 转 `deferred`，记录架构前置（blocking edge / region 边界引入需 vision 决策），deferred 项保持 deferred 到 successor。
- 消费 Stage 20/25 deferred 项（G28 partial 恢复 + per-region 计数器），给出明确归属。

## Non-Goals

- region-aware scheduling（G55）。
- 跨 JVM region failover + scale（Stage 44）。
- coordinator HA（Stage 46）、rescale 恢复（Stage 35）、unaligned checkpoint（Stage 43）。
- **预先承诺 targeted failover 一定可行**——本 plan 的核心是裁定可行性，不假设结论。

## Scope

### In Scope

- 可行性设计文档 `ai-dev/design/nop-stream/failover-design.md`（go/no-go 裁定 + 若 go 则含 region/blast-radius/drain-reconnect/supervision-loop/per-region-counter/partial-restore 设计）。
- **go 路径（条件性）**：region/blast-radius 识别 + supervision loop + 受限重启 + per-region 计数器 + detectFailures 路径切换。
- **no-go 路径**：plan 转 `deferred`，记录架构前置，更新 roadmap Stage 27 状态。

### Out Of Scope

- region-aware scheduling（G55）、跨 JVM region failover（Stage 44）、coordinator HA（Stage 46）。

## Execution Plan

> **go/no-go gate**：Phase 1 产出可行性裁定。若裁定为 **no-go**（当前架构下 targeted failover 不可行或需超出本 plan scope 的架构变更），plan 执行 Phase 1 的 no-go 收口（转 `deferred` + 记录前置 + 更新 roadmap），**不执行 Phase 2/3**。若裁定为 **go**，Phase 2/3 的实施细节以 Phase 1 设计文档裁定为准（Phase 2/3 Exit Criteria 在 Phase 1 go 裁定后重新审视修订）。

### Phase 1 — 可行性裁定 + 设计文档

Status: planned
Targets: `ai-dev/design/nop-stream/failover-design.md`（新增）；`ai-dev/design/nop-stream/checkpoint-design.md` §8（增补 region failover 小节）；`ai-dev/design/nop-stream/01-architecture-baseline.md` §五 Restart Strategy；`ai-dev/backlog/nop-stream-production-roadmap.md`（Stage 27 状态更新）

- Item Types: `Decision | Proof`

- [ ] **可行性调查**（回答以下，每条不留 open）：
  - **架构事实确认**：当前 `JobGraphGenerator` 全 pipelined → 单 region → vertex 级 targeted = global（零收益）。此事实已由 draft review 验证，Phase 1 正式记录于设计文档。
  - **subtask 级 blast-radius**：在 all-pipelined 图中，单 subtask 失败时，仅 point-to-point（forward/rescale）连接的对端 subtask 受影响（vs all-to-all hash/rebalance 全部受影响）。**须回答**：subtask 级粒度的 targeted restart 是否有足够收益（parallelism>1 + forward/rescale 占比）值得 drain/reconnect 的复杂度？
  - **region 边界引入可行性**：是否可通过引入 blocking edge / 物化点创建 region 边界（使多 region 成立）？**须回答**：blocking edge 与流式低延迟连续执行是否冲突？是否需 vision（`00-vision.md` §四/§七）决策？
  - **supervision loop 可行性**：mid-execution 重启是否需执行模型变更（当前 `checkTaskFailures` 在 `awaitCompletion` 后运行）？**须回答**：变更量是否在本 plan scope 内？还是属 Stage 44？
  - **drain/reconnect 可行性（可行性关键路径）**：pipelined 队列按引用直连下，scoped 重启如何避免上游 `queue.put()` 永久阻塞 / 下游 channel 不 close 永挂（Stage 20 G28 deferred 根因）？**须回答**：是否存在不引入 blocking edge 即可解决死锁的 drain/reconnect 设计？若无，则 go 不可达成。此调查是 go/no-go 的**关键路径**（drain/reconnect 不可设计 → 直接 no-go）。
  - **partitioner 分布调查**：调查 nop-stream 典型 DAG 的 partitioner 分布（`ForwardPartitioner`、`KeySelectorPartitioner`/hash、rebalance 等）与 parallelism 使用，判断 subtask 级 blast-radius 是否有收益（forward/rescale + parallelism>1 时仅对端 subtask 受影响）。参考类：`ForwardPartitioner`、`DataStreamImpl` 中 partitioner 选择逻辑、`JobGraphGenerator` 中 partitioner→edge 映射。
- [ ] **go/no-go 裁定**：基于上述调查，给出明确裁定（go = subtask 级或 region 级 targeted failover 在当前架构下可行且值得；no-go = 需架构前置超出本 plan scope）。
- [ ] **go 路径设计文档**（仅 go 时完整）：region/blast-radius 模型 + drain/reconnect（解决 Stage 20 G28 死锁根因）+ supervision loop + partial restore scope + per-region restart 计数器。
- [ ] **no-go 路径收口**（仅 no-go 时）：plan 转 `deferred`；记录架构前置（blocking edge / region 边界 / supervision loop 执行模型变更 / drain/reconnect 不可设计）+ 建议归属（Stage 44 或新 vision 决策 plan）；**roadmap Stage 27 标记为 `done`（裁定交付：no-go，参照 Stage 20 将 G28 deferred 但 Stage 20 自身标 done 的先例），实现归属记录到 Stage 44**；G28/per-region-counter 保持 deferred 记录于 successor（Stage 44）。注：roadmap status 值仅 `todo/planned/done`（无 `deferred`），裁定交付即 `done`。
- [ ] 更新 `checkpoint-design.md` §8（增补 region failover 小节，引用 `failover-design.md`，定位与 global recovery 关系）+ `01-architecture-baseline.md` §五 Restart Strategy。

Exit Criteria:

- [ ] `failover-design.md` 存在，含架构事实确认（全 pipelined → 单 region）+ go/no-go 裁定。
- [ ] 若 go：设计文档覆盖 region/blast-radius + drain/reconnect（回答 Stage 20 死锁根因）+ supervision loop + partial restore + per-region 计数器，每项有裁定（无 TBD）。
- [ ] 若 no-go：plan 顶部 `Plan Status` 改为 `deferred`；架构前置明确记录（含 drain/reconnect 不可设计结论）；roadmap Stage 27 标记 `done`（裁定交付）；G28/per-region-counter 归属明确（Stage 44）。
- [ ] `checkpoint-design.md` §8 + `01-architecture-baseline.md` §五 已更新。
- [ ] **Phase 2/3 在 go 裁定后重新审视**：若 go，Phase 1 完成时据设计文档裁定修订 Phase 2/3 Exit Criteria（使其与设计结论一致，非沿用占位符）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 — Region/blast-radius 模型（仅 go 时执行）

Status: planned
Targets: `nop-stream-core/.../jobgraph/` 或 `nop-stream-runtime/.../recovery/`（新建包，若 go）

- Item Types: `Fix`

- [ ] 按 Phase 1 设计实现 region/blast-radius 识别（vertex 级或 subtask 级，取决于 Phase 1 裁定）。
- [ ] affected-set 计算（失败 task → 受影响集合）。

Exit Criteria:

- [ ] **Phase 1 go 裁定后重新审视并修订**本 Exit Criteria（当前为占位）。
- [ ] region/blast-radius 识别对典型 DAG 正确分组，focused test 覆盖（场景由 Phase 1 裁定决定）。
- [ ] **无静默跳过**（#24）：边界情况显式处理。
- [ ] owner-doc：`failover-design.md` 已记录算法。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 — Supervision loop + 受限重启 + per-region 计数器（仅 go 时执行）

Status: planned
Targets: `nop-stream-runtime/.../recovery/`；`JobCoordinator.java`；`GraphModelCheckpointExecutor.java`

- Item Types: `Fix`

- [ ] 按 Phase 1 设计实现 supervision loop（mid-execution 重启入口，解决 `checkTaskFailures` 在 `awaitCompletion` 后的限制）。
- [ ] drain/reconnect（解决 Stage 20 G28 死锁根因）。
- [ ] targeted 重启（受影响集合从 durable epoch 恢复 + source replay）。
- [ ] per-region restart 计数器 + 上限（Stage 25 deferred）。
- [ ] `detectFailures`/`reportTaskStatus` 路径切换（优先 targeted，globalRecovery fallback）。

Exit Criteria:

- [ ] **Phase 1 go 裁定后重新审视并修订**本 Exit Criteria（当前为占位）。
- [ ] **端到端验证**（#22）：单 task 失败 → 仅受影响子集重启 → 非受影响部分继续运行 → job 完成。E2E 断言非受影响 task attemptNumber 不变。
- [ ] **接线验证**（#23）：测试断言 detectFailures 在可 targeted 时走 targeted 路径（非 globalRecovery）。
- [ ] per-region 计数器：focused test 验证超上限降级。
- [ ] **无静默跳过**（#24）：无法确定 region/drain 失败/restore 缺数据时显式失败或降级。
- [ ] **Anti-Hollow**：targeted 路径运行时确实被调用（E2E verify），非仅类型存在。
- [ ] owner-doc + `ai-dev/logs/` 已更新。

## Closure Gates

- [ ] go/no-go 裁定已做出并记录于 `failover-design.md`。
- [ ] **若 go**：G57/G28(续)/per-region-counter 落地，E2E 验证 targeted 重启收益（非空壳）。
- [ ] **若 no-go**：plan 转 `deferred`，架构前置记录，G28/per-region-counter 归属明确 successor，roadmap 更新。
- [ ] **不声称关闭 G55**（region-aware scheduling 仍 out-of-scope）。
- [ ] 不存在被静默降级的 in-scope gap。
- [ ] 受影响 owner docs 已同步。
- [ ] 独立子 agent closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**（仅 go）：closure audit 验证 targeted 路径运行时被调用、旧 attempt 终止、非受影响部分未被重启、无死锁。
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过（go 路径含代码变更时）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（go 路径）。

## Deferred But Adjudicated

### G55 region-aware scheduling

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: region-aware scheduling（按 region 分配 slot/node）是调度优化，非 failover 正确性前置。
- Successor Required: no

### 跨 JVM region failover + scale（Stage 44）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 跨 JVM + scale 属 Stage 44。
- Successor Required: yes
- Successor Path: Stage 44 (`44-region-failover`)

## Non-Blocking Follow-ups

- region 识别缓存（大 DAG 性能优化）。
- region 边界动态调整（Stage 35/37 rescale）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<待 closure audit 填写>>
- Evidence: <<待 closure audit 填写>>

Follow-up:

- <<待 closure audit 填写>>
