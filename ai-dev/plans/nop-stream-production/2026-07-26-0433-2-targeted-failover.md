# 27 — Targeted failover 可行性裁定 + 设计（region 模型）

> Plan Status: deferred
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

Status: completed
Targets: `ai-dev/design/nop-stream/failover-design.md`（新增）；`ai-dev/design/nop-stream/checkpoint-design.md` §8（增补 region failover 小节）；`ai-dev/design/nop-stream/01-architecture-baseline.md` §五 Restart Strategy；`ai-dev/backlog/nop-stream-production-roadmap.md`（Stage 27 状态更新）

- Item Types: `Decision | Proof`

- [x] **可行性调查**（回答以下，每条不留 open）：
  - **架构事实确认**：当前 `JobGraphGenerator` 全 pipelined → 单 region → vertex 级 targeted = global（零收益）。此事实已由 draft review 验证，Phase 1 正式记录于 `failover-design.md` §2.1。
  - **subtask 级 blast-radius**：在 all-pipelined 图中，forward 边（parallelism>1）理论上仅对端 subtask 受影响。**裁定**：理论可行但 moot——受 drain/reconnect 不可达阻断（§3.2/§3.3）。partitioner 分布调查确认生产代码仅 `ForwardPartitioner` + `KeySelectorPartitioner`。
  - **region 边界引入可行性**：引入 blocking edge 需改变流式连续执行模型假设（`graph-model-design.md:143` BLOCKING=批式，当前未使用；`:204` 假设所有 vertex 同时启动）。**裁定**：需 vision 决策，超出本 Stage scope（§3.4）。
  - **supervision loop 可行性**：mid-execution 重启需将 `submitAndRun`→`awaitCompletion` 全量阻塞模型改为可观测单 task 失败 + 重启调度。**裁定**：属 Stage 44 执行模型变更（§3.5）。
  - **drain/reconnect 可行性（可行性关键路径）**：by-reference `LinkedBlockingQueue` 下 scoped 重启存在三个结构死锁（上游 `queue.put()` 永久阻塞 / 下游 channel 不 close 永挂 / 无 mid-execution 重启入口）。**裁定：drain/reconnect 不可设计（无 blocking edge 前提下）→ 直接 no-go**（§3.3，关键路径）。
  - **partitioner 分布调查**：生产代码仅 `ForwardPartitioner`（point-to-point→PIPELINED）+ `KeySelectorPartitioner`（hash/all-to-all→PIPELINED_BOUNDED）；无生产 Rebalance/Rescale partitioner（仅测试 stub）。
- [x] **go/no-go 裁定**：**NO-GO**。targeted failover 在 nop-stream 当前 all-pipelined + by-reference-queue 架构下不可行；所需前置（blocking edge + supervision loop + drain/reconnect + region 概念 + per-region 计数器）超出本 Stage scope。裁定记录于 `failover-design.md` §一/§四。
- [x] **go 路径设计文档**（仅 go 时完整）：N/A — 裁定为 no-go，不执行。
- [x] **no-go 路径收口**：plan 转 `deferred`（Plan Status 已改）；架构前置明确记录于 `failover-design.md` §五（blocking edge + region 概念 + supervision loop + drain/reconnect + per-region 计数器）；建议归属 Stage 44 / vision 决策；roadmap Stage 27 标记为 `done`（裁定交付：no-go）；G57/G28(续)/per-region-counter 保持 deferred → Stage 44（记录于 `failover-design.md` §六 + plan `Deferred But Adjudicated`）。
- [x] 更新 `checkpoint-design.md` §8.1.2（新增 region failover 裁定小节，引用 `failover-design.md`）+ `01-architecture-baseline.md` §五 Restart Strategy（line 333 更新为 no-go 裁定引用）。

Exit Criteria:

- [x] `failover-design.md` 存在，含架构事实确认（全 pipelined → 单 region）+ go/no-go 裁定（NO-GO）。
- [x] 若 go：N/A（裁定为 no-go）。
- [x] 若 no-go：plan 顶部 `Plan Status` 改为 `deferred`；架构前置明确记录（`failover-design.md` §五，含 drain/reconnect 不可设计结论）；roadmap Stage 27 标记为 `done`（裁定交付）；G28/per-region-counter 归属明确（Stage 44，`failover-design.md` §六）。
- [x] `checkpoint-design.md` §8.1.2 + `01-architecture-baseline.md` §五 已更新。
- [x] **Phase 2/3 在 go 裁定后重新审视**：裁定为 no-go → Phase 2/3 不执行（Status: cancelled），不修订其 Exit Criteria。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 — Region/blast-radius 模型（仅 go 时执行）

Status: cancelled（Phase 1 裁定 NO-GO，go gate 未通过，本 Phase 不执行）
Targets: `nop-stream-core/.../jobgraph/` 或 `nop-stream-runtime/.../recovery/`（新建包，若 go）

- Item Types: `Fix`

- [x] 按 Phase 1 设计实现 region/blast-radius 识别（vertex 级或 subtask 级，取决于 Phase 1 裁定）。— N/A：Phase 1 裁定 no-go（drain/reconnect 不可设计，需 blocking edge 前置），实现 deferred 到 Stage 44。
- [x] affected-set 计算（失败 task → 受影响集合）。— N/A：同上。

Exit Criteria:

- [x] **Phase 1 go 裁定后重新审视并修订**本 Exit Criteria（当前为占位）。— N/A：裁定为 no-go，不修订。
- [x] region/blast-radius 识别对典型 DAG 正确分组，focused test 覆盖（场景由 Phase 1 裁定决定）。— N/A：no-go，不实现。
- [x] **无静默跳过**（#24）：边界情况显式处理。— N/A：no-go，无新代码。
- [x] owner-doc：`failover-design.md` 已记录算法。— N/A：no-go 路径，`failover-design.md` 记录的是 no-go 裁定（§四裁定汇总），非实现算法。
- [x] `ai-dev/logs/` 对应日期条目已更新。— 已更新（Phase 1 收口记录覆盖 Phase 2/3 取消）。

### Phase 3 — Supervision loop + 受限重启 + per-region 计数器（仅 go 时执行）

Status: cancelled（Phase 1 裁定 NO-GO，go gate 未通过，本 Phase 不执行）
Targets: `nop-stream-runtime/.../recovery/`；`JobCoordinator.java`；`GraphModelCheckpointExecutor.java`

- Item Types: `Fix`

- [x] 按 Phase 1 设计实现 supervision loop（mid-execution 重启入口，解决 `checkTaskFailures` 在 `awaitCompletion` 后的限制）。— N/A：Phase 1 裁定 no-go（supervision loop 属 Stage 44 执行模型变更），deferred 到 Stage 44。
- [x] drain/reconnect（解决 Stage 20 G28 死锁根因）。— N/A：Phase 1 裁定 drain/reconnect 不可设计（无 blocking edge 前提），deferred 到 Stage 44。
- [x] targeted 重启（受影响集合从 durable epoch 恢复 + source replay）。— N/A：同上。
- [x] per-region restart 计数器 + 上限（Stage 25 deferred）。— N/A：scoped 重启入口不存在，deferred 到 Stage 44。
- [x] `detectFailures`/`reportTaskStatus` 路径切换（优先 targeted，globalRecovery fallback）。— N/A：同上。

Exit Criteria:

- [x] **Phase 1 go 裁定后重新审视并修订**本 Exit Criteria（当前为占位）。— N/A：裁定为 no-go，不修订。
- [x] **端到端验证**（#22）：单 task 失败 → 仅受影响子集重启 → 非受影响部分继续运行 → job 完成。E2E 断言非受影响 task attemptNumber 不变。— N/A：no-go，不实现。
- [x] **接线验证**（#23）：测试断言 detectFailures 在可 targeted 时走 targeted 路径（非 globalRecovery）。— N/A：no-go，不实现。
- [x] per-region 计数器：focused test 验证超上限降级。— N/A：no-go，不实现。
- [x] **无静默跳过**（#24）：无法确定 region/drain 失败/restore 缺数据时显式失败或降级。— N/A：no-go，无新代码；现有 `globalRecovery()` 路径不静默跳过（`JobCoordinator` FAILED 后 `assignTasks()` 显式拒绝）。
- [x] **Anti-Hollow**：targeted 路径运行时确实被调用（E2E verify），非仅类型存在。— N/A：no-go，无 targeted 路径引入。
- [x] owner-doc + `ai-dev/logs/` 已更新。— `failover-design.md` 记录 no-go 裁定；logs 已更新。

## Closure Gates

- [x] go/no-go 裁定已做出并记录于 `failover-design.md`（NO-GO，§一/§四）。
- [x] **若 go**：N/A（裁定为 no-go）。
- [x] **若 no-go**：plan 转 `deferred`（Plan Status 已改）；架构前置记录于 `failover-design.md` §五；G28/per-region-counter 归属明确 successor（Stage 44，§六 + Deferred But Adjudicated）；roadmap Stage 27 更新为 `done`。
- [x] **不声称关闭 G55**（region-aware scheduling 仍 out-of-scope，Deferred But Adjudicated 已记录）。
- [x] 不存在被静默降级的 in-scope gap（G57/G28/per-region-counter 均显式裁定为 deferred → Stage 44，附 Why Not Blocking Closure）。
- [x] 受影响 owner docs 已同步（`failover-design.md` 新增；`checkpoint-design.md` §8.1.2；`01-architecture-baseline.md` §五 line 333）。
- [x] 独立子 agent closure-audit 已完成并记录证据（见 Closure Audit Evidence）。
- [x] **Anti-Hollow Check**（仅 go）：N/A（裁定为 no-go，无新代码引入，无 targeted 路径空壳风险）。
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过（no-go 路径无代码变更，baseline 绿；见 Closure Audit Evidence）。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` — 12 pre-existing high findings（CEP GroupPattern / RuntimeContext keyed-state guards / FunctionUtils / Trigger / fraud-demo / TaskManager:283 placeholder），全部为本 plan 之前已存在、与本 plan scope 无关；本 plan 零代码变更，未引入新空壳。

## Deferred But Adjudicated

### G57 targeted failover 实现

- Classification: `out-of-scope improvement`（架构前置未满足，非优化降级）
- Why Not Blocking Closure: Stage 27 裁定 no-go——all-pipelined→单 region（vertex 级零收益）+ drain/reconnect 不可设计（by-reference 队列死锁）。实现需 blocking edge + supervision loop 前置，超出 in-process scope。裁定交付（`failover-design.md`）即 Stage 27 的 done 交付物。
- Successor Required: yes
- Successor Path: Stage 44（`44-region-failover`）/ 新 vision 决策 plan（引入 blocking edge）

### G28（续）partial/region 恢复

- Classification: `out-of-scope improvement`（架构前置未满足）
- Why Not Blocking Closure: Stage 20 deferred 到 Stage 27；Stage 27 裁定 drain/reconnect 不可设计（需 blocking edge 解耦 producer/consumer 生命周期）。partial restore 入口仍 whole-job。
- Successor Required: yes
- Successor Path: Stage 44

### per-region restart 计数器

- Classification: `out-of-scope improvement`（架构前置未满足）
- Why Not Blocking Closure: Stage 25 deferred 到 Stage 27；Stage 27 裁定 scoped 重启入口不存在（需 supervision loop）。`restartCount` 仍 global-only。
- Successor Required: yes
- Successor Path: Stage 44

### G55 region-aware scheduling

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: region-aware scheduling（按 region 分配 slot/node）是调度优化，非 failover 正确性前置。Stage 27 裁定 no-go，未引入 region 概念。
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

Status Note: Stage 27 裁定 NO-GO。targeted failover 在 nop-stream 当前 all-pipelined + by-reference-queue 架构下不可行：(1) `determinePartitionType()` 从不返回 BLOCKING → 全 pipelined → 单 region → vertex 级 targeted = global（零收益）；(2) by-reference `LinkedBlockingQueue` 下 drain/reconnect 不可设计（三个结构死锁）；(3) 解除 no-go 需五项架构前置（blocking edge + region 概念 + supervision loop + drain/reconnect + per-region 计数器），超出 in-process scope。裁定交付物 = `failover-design.md`。G57/G28(续)/per-region-counter deferred → Stage 44。Plan Status = deferred（实现 deferred 到 Stage 44）；roadmap Stage 27 = done（裁定交付）。
Completed: 2026-07-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore, task_id `ses_064a0de2dffeAPa5SdYWWkjsaU`）
- Audit Session: ses_064a0de2dffeAPa5SdYWWkjsaU
- Evidence:
  - **Check 1 (design doc)**: PASS — `failover-design.md` 含 NO-GO 裁定（§一）、架构事实（§2.1，`determinePartitionType` 行号与 live code `JobGraphGenerator.java:546-553` 一致）、drain/reconnect 不可达（§3.3）、架构前置（§五，5 项）、G57/G28/counter 归属（§六）。
  - **Check 2 (live code)**: PASS — `ResultPartitionType.java` PIPELINED(true,false)/PIPELINED_BOUNDED(true,true) 均 pipelined=true，BLOCKING(false,true) pipelined=false；`JobCoordinator.globalRecovery():647` 唯一恢复入口，restartCount :155 仅 globalRecovery 递增；`region|Region` 生产代码零匹配（仅注释 :151/:649）。
  - **Check 3 (checkpoint-design)**: PASS — §8.1.2 新增 "Region failover 可行性裁定（Stage 27 — NO-GO）" 小节，引用 `failover-design.md`。
  - **Check 4 (architecture-baseline)**: PASS — line 333 更新为 Stage 27 NO-GO 裁定引用。
  - **Check 5 (roadmap)**: PASS — Stage 27 标记 `done`（裁定交付 NO-GO），详细 section 反映 no-go + deferred → Stage 44。
  - **Check 6 (plan consistency)**: PASS — Plan Status=deferred，Phase 1=completed（全 [x]），Phase 2/3=cancelled（全 [x] N/A），Closure Gates 全 [x]，Deferred But Adjudicated 含 G57/G28/counter → Stage 44。
  - **Check 7 (no code changes)**: PASS — git status 仅 4 个 .md 修改 + 1 个 .md 新增，零 .java 变更。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`: exit 0（all 41 items checked）。
  - `./mvnw test -pl nop-stream -am -T 1C`: exit 0（baseline 绿，无代码变更）。
  - Anti-Hollow: N/A（no-go 路径，零代码变更，无新组件引入）。
  - Deferred 项分类检查：G57/G28/per-region-counter 均为架构前置未满足的 out-of-scope improvement（非 live defect 降级），附 Why Not Blocking Closure + Stage 44 successor path。

Follow-up:

- G57/G28(续)/per-region-counter 实现归属 Stage 44（需 blocking edge + supervision loop 前置）。
- blocking edge 引入需 vision 决策（`00-vision.md` §四/§七 流式连续执行定位）。
- 无 plan-owned 剩余工作（本 plan 为裁定交付，实现全部 deferred）。
