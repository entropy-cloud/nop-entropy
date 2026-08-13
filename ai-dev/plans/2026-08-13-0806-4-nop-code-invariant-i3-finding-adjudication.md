# nop-code 不变式闭环 I3 — 发现裁决（Cycle 1）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Mission: nop-code-invariant-loop
> Work Item: Cycle 1 / I3. 发现裁决
> Source: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（Work Item I3 + Phase Details I2–I6）；方法论 `ai-dev/skills/invariant-loop-audit-prompt.md`
> Related: 前驱 `2026-08-13-0806-3-nop-code-invariant-i2-invariant-driven-audit.md`（I2，本计划消费其 red list + coverage matrix + adversarial-probe 报告）；后继 I4（修复执行，消费本计划裁决的 P0/P1 修复队列）

## Purpose

把 I2 产出的 **red list（门禁命中分类）+ 覆盖矩阵（95 条 open 悬空发现的门禁覆盖状态）+ 对抗探查新发现** 统一**逐条裁决**，产出一份零悬挂的裁决矩阵，明确：

1. 每条「真违规」+「未覆盖-需手动修复」发现归入哪个修复优先级（`P0` 立即修 / `P1` 本 Cycle 修）。
2. 每条发现归入哪种处置（`P0/P1 I4 修复` / `P2/P3 后继修复计划（successor ownership）` / `接受为残余风险（watch-only/optimization）` / `前提过时（stale）` / `移出范围（out-of-scope）`）。
3. 派给 I4 的修复队列：按失败族 + 不变式归类的「类别清扫」工作包（roadmap I4 类别清扫面：修任一 SearchService 全实体加载必 grep 全部 SearchService；修任一删除路径必穷举全部删除路径）。

本计划只产出裁决文档，不改任何代码。裁决表必须零悬挂——每条 in-scope 发现落到且只落到一种终态。

## Current Baseline

> 依赖 I2 产出。本节为 I2 完成后的预期基线（I2 计划 `2026-08-13-0806-3`）。I2 未完成前，本计划保持 `draft`。

- **I2 产出为本计划输入**（前驱计划须先 completed）：
  - `ai-dev/audits/nop-code-invariants/i2-red-list.md`：四族门禁命中逐条分类（真违规 / 已接受有界 / 门禁误报）+ 对抗探查新增段。
  - `ai-dev/audits/nop-code-invariants/i2-coverage-matrix.md`：95 条 open 悬空发现的门禁覆盖状态（已被门禁覆盖 / 部分覆盖 / 未覆盖-需手动修复）。
  - `ai-dev/audits/nop-code-invariants/i2-adversarial-probe.md`：并发 + OOM + 跨文件孤儿/搜索引擎去同步三面探查结论。
- **已知 red list 输入规模**（从 I1 baseline 推算，I2 定稿）：
  - query-limit 真违规：至少 AR-168（buildFilePathCache）+ AR-177（getProjectFilePaths）两条确定真违规；其余 31 条由 I2 分类。
  - entity-field-min 真违规：findImplementations（AR-64/86）、resolveQualifiedNamesToIds（AR-75）等全实体加载点。
  - idempotency red-list 锁：`indexDirectory`/`indexFile`（duplicate-key 23505，已锁定为真违规）。
- **悬空发现处置输入**：95 条 open 中，门禁覆盖状态由 I2 标注。「未覆盖-需手动修复」的发现（预计集中在 concurrency-lock/data-consistency/graph-algorithm/language-adapter/error-handling 族——这些族无 I1 门禁直接覆盖）须由本计划逐条裁决。
- **裁决授权**（roadmap Cross-Cutting）：P0/P1 自动修复预授权；ORM/API 模型结构变更执行前人工确认；新门禁入 CI 需 committed 回归测试。
- **类别清扫面**（roadmap I4）：① 修任一 SearchService 的全实体加载必 grep 全部 SearchService 方法；② 修任一删除路径必穷举全部 useLogicalDelete 实体的删除路径（live 无 useLogicalDelete → 退化为穷举全部删除路径的物理删除一致性）。

## Goals

- 产出 `ai-dev/audits/nop-code-invariants/i3-adjudication-matrix.md`：I2 red list 真违规 + 未覆盖悬空发现 + 对抗探查新发现的**逐条裁决表**，零悬挂。
- 每条裁决含：发现 ID（AR-XX / gate-hit / probe-N）/ 当前 live 位置 / 优先级（P0|P1|P2/P3）/ 处置（P0/P1 I4 修复 | P2/P3 后继修复计划 | 接受残余 | stale | out-of-scope）/ 归属不变式 / 类别清扫归属族。
- 派给 I4 的修复队列按「类别清扫面」组织：每个工作包 = 一个失败族 × 不变式，含该族全部待修条目 + test-first 要求。
- 对「接受为残余风险」的条目逐条写明 `Why Not Blocking Closure`（为何不影响当前 supported baseline）。

## Non-Goals

- 不修复任何违规 —— 那是 I4。
- 不沉淀新不变式门禁（INV-05 等）—— 属 Cycle 2 / I1。
- 不改产品代码 / ORM 模型 / API 契约（本计划纯裁决文档）。
- 不重新审计（不重跑门禁、不重做对抗探查）—— 消费 I2 已产出的分类与覆盖状态。
- 不决定 Cycle 收口（稳态判定 / 复触发条件）—— 那是 I6。

## Scope

### In Scope

- 裁决 I2 red list 的全部「真违规」条目（query-limit + entity-field-min + idempotency red-list 锁 + 对抗探查新增）。
- 裁决 I2 覆盖矩阵中「未覆盖-需手动修复」的全部悬空发现。
- 对每条裁决分配优先级（P0/P1）与处置终态（I4 修复 / 接受残余 / stale / out-of-scope）。
- 按类别清扫面组织 I4 修复队列工作包。
- 对 ORM/API 模型结构变更类裁决（如 cascadeDelete 缺失 AR-149/150）标注「执行前人工确认」。**注**：AR-179（FlowMembership 缺 indexId 列）经 live 核对前提已过时（`nop-code.orm.xml:881-882` 已有 indexId 列 + `ix_nop_code_flow_membership_index_id` 索引），须改判为 stale-premise，不作为 ORM 结构变更派 I4。

### Out Of Scope

- I4 的修复执行（代码实现）。
- 已被 I2 标为「已接受有界」或「门禁误报」的条目（这些已在 I2 终态，不需 I3 再裁决——I3 只裁决「真违规」+「未覆盖需修」）。
- 新门禁实现（Cycle 2 / I1）。
- 其他模块的裁决（范围独立）。

## Execution Plan

### Phase 1 - red list 真违规裁决 + I4 修复队列（门禁驱动）

Status: completed
Targets: `ai-dev/audits/nop-code-invariants/i3-adjudication-matrix.md`（新建）；输入 = `i2-red-list.md` 真违规段

- Item Types: `Decision`

- [x] 对 I2 red list 的全部「真违规」条目逐条裁决优先级：`P0`（数据正确性/OOM/并发损坏/幂等性硬失败）/ `P1`（性能/可观测性/降级语义）；idempotency red-list 锁（indexDirectory/indexFile duplicate-key 23505）须裁决为 P0
- [x] 对每条真违规写明处置终态：默认 `I4 修复`；若某条经核对实为「已接受有界」或「stale 前提」，显式改判并附理由（不允许静默降级）
- [x] 按类别清扫面组织 I4 工作包：① OOM-查询上限族（query-limit 真违规全部，修任一须 grep 全部无 setLimit 调用点）；② OOM-字段最小化族（entity-field-min 真违规，修任一须穷举全部全实体加载点）；③ 幂等性族（indexDirectory/indexFile 重试安全）
- [x] 每个工作包标注 test-first 要求：修复后须使对应门禁命中数下降（棘轮前进）+ red-list 锁移入 green 表

Exit Criteria:

- [x] `i3-adjudication-matrix.md` 存在，red list 真违规段每条有优先级（P0|P1）+ 处置终态
- [x] 优先级分配有依据（P0 = 数据正确性/OOM/并发/幂等硬失败；P1 = 性能/可观测性/降级），非随意标注
- [x] I4 工作包按类别清扫面组织，每个工作包含该族全部待修条目（无遗漏）
- [x] 每个工作包有 test-first 要求（修复后门禁命中下降 / red-list 锁转 green）
- [x] **Anti-Slacking**：已确认 live defect（如 idempotency 锁）只能 `I4 修复`，不得降级为 `Follow-up`；不允许用 `optional`/`if time permits` 替代状态裁定
- [x] 本 Phase 为纯裁决文档：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 悬空发现（未覆盖-需手动修复）裁决

Status: completed
Targets: `i3-adjudication-matrix.md`（追加段）；输入 = `i2-coverage-matrix.md` 的「未覆盖-需手动修复」发现

- Item Types: `Decision`

- [x] 对 I2 覆盖矩阵中「未覆盖-需手动修复」的全部 open 发现逐条裁决。**处置终态（按严重度分流，每条有且仅有一个）**：
  - `P0 I4 修复`（数据正确性/OOM/并发损坏/幂等硬失败，自动预授权）
  - `P1 I4 修复`（性能/可观测性/降级语义，自动预授权）
  - `P2/P3 → 后继修复计划（successor ownership）`（非 Cycle 1 I4 自动授权范围；须附显式后继归属路径 + `Why Not Blocking Cycle 1 Closure`——如：非门禁覆盖、非正确性/契约硬失败、低影响面）
  - `接受为残余风险（watch-only residual / optimization candidate）`（仅限非 live-defect 的优化项；须附 `Why Not Blocking Closure`）
  - `前提过时（stale）`（live 核对前提不成立，如 AR-179）
  - `移出范围（out-of-scope）`（如前端页面契约属其他模块）
- [x] 对每条裁决附 live 位置（`文件:行`）+ 归属失败族 + 是否属于 I4 类别清扫面
- [x] 对 `P2/P3 → 后继修复计划` 与 `接受为残余风险` 的条目逐条写明 `Why Not Blocking Cycle 1 Closure`（为何不影响当前 supported baseline / 不阻塞不变式闭环收口；如：非门禁覆盖 + 低频触发 + 有降级路径 + 影响面有限）
- [x] 对 `移出范围` 的条目写明移出理由 + 后继归属（如：前端页面契约属其他模块 roadmap）
- [x] 对 ORM/API 模型结构变更类裁决（AR-149 NopCodeFile cascadeDelete / AR-150 NopCodeSymbol.usages cascadeDelete 等）标注「plan-first / 执行前人工确认」（AGENTS.md Protected Areas）；AR-179（FlowMembership 缺 indexId 列）须 live 复核——`nop-code.orm.xml:881-882` 已有 indexId 列，前提疑似过时，改判 stale 而非派 ORM 变更

Exit Criteria:

- [x] 「未覆盖-需手动修复」全部发现逐条裁决，每条有且仅有一个处置终态
- [x] 裁决表零悬挂（无「待定」「未判定」）
- [x] `P2/P3 → 后继修复计划` / `接受为残余风险` 每条有 `Why Not Blocking Cycle 1 Closure` + 显式后继归属；`移出范围` 每条有移出理由 + 后继归属
- [x] ORM/API 模型结构变更类裁决有「执行前人工确认」标注（非自动修复）
- [x] 族聚合裁决计数与逐条一致（P0 N / P1 M / 后继 S / 残余 K / stale L / out-of-scope J，总和 == 未覆盖发现总数）
- [x] **Anti-Slacking**：P0/P1 已确认 live defect / contract drift 不得降级为残余或后继（必须 I4 修复）；P2/P3 已确认 defect 不得静默丢弃——只能走 `后继修复计划（显式 successor ownership）` 或 `optimization candidate`（均须附 Why Not Blocking + 后继路径），不得用 `optional`/`if time permits`/`后续再说` 替代状态裁定
- [x] 本 Phase 为纯裁决文档：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - I4 修复队列定稿 + 零悬挂确认

Status: completed
Targets: `i3-adjudication-matrix.md`（收口段）；交付物 = I4 修复队列工作包清单

- [x] 合并 Phase 1（门禁驱动）+ Phase 2（悬空驱动）的 P0/P1 条目为统一 I4 修复队列，按类别清扫面排序（OOM 族优先 → 幂等族 → 并发族 → 数据一致性族 → 其余）
- [x] 对每个工作包标注：涉及的不变式 / 涉及的 `文件:行` 清单 / test-first 验证点 / 修复后预期的门禁命中下降数 / 是否需 ORM/API 人工确认
- [x] 全量零悬挂确认：I2 red list 真违规 + 未覆盖悬空发现 + 对抗探查新发现，每条均已落到终态（P0/P1 I4 修复 / P2/P3 后继修复计划 / 接受残余 / stale / out-of-scope）
- [x] 登记「Cycle 2 / I1 候选输入」：I2 裁定需要沉淀为新门禁的族（如 INV-05），记录为 I6 收口时派生的依据

Exit Criteria:

- [x] I4 修复队列工作包清单存在，每个工作包自包含（不变式 + 文件:行 + test-first + 预期门禁下降 + 人工确认标注）
- [x] 全量裁决零悬挂（red list 真违规 + 未覆盖悬空 + 对抗探查新发现均有终态）
- [x] 工作包排序有依据（OOM/幂等优先，因影响数据正确性与可用性）
- [x] Cycle 2 / I1 候选输入已登记（无门禁覆盖但需长期治理的族）
- [x] 裁决矩阵可作为 I4 的确定性输入（I4 按工作包执行即可，无需重新裁决）
- [x] **无静默跳过**：零悬挂确认须逐族核对，不得用「其余类似」省略
- [x] 本 Phase 为纯裁决文档：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯文档计划**：本计划不涉及任何代码变更（仅新建/追加 `ai-dev/audits/nop-code-invariants/i3-adjudication-matrix.md`）。`./mvnw test`/`compile`/checkstyle 不适用，从本节删除。

- [x] `i3-adjudication-matrix.md` 存在，覆盖 I2 red list 真违规 + 未覆盖悬空发现 + 对抗探查新发现
- [x] 裁决表零悬挂（每条 in-scope 发现有且仅有一个终态）
- [x] I4 修复队列工作包按类别清扫面组织，每个自包含
- [x] `接受为残余风险` / `P2/P3 → 后继修复计划` 每条有 `Why Not Blocking Cycle 1 Closure` + 后继归属；P0/P1 已确认 live defect 未被降级（必须 I4 修复）
- [x] ORM/API 模型结构变更类裁决有「执行前人工确认」标注
- [x] Cycle 2 / I1 候选输入已登记
- [x] 不存在被静默降级到 deferred 的 in-scope 裁决项
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本计划新增文档零断链

## Deferred But Adjudicated

### Cycle 2 / I1 新门禁沉淀

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: I3 只裁决「是否需要新门禁」并登记候选；门禁实现属 Cycle 2 / I1（Loop Rule 预授权），不在 Cycle 1 / I3 范围。
- Successor Required: yes
- Successor Path: Cycle 2 / I1（由 I6 收口时按 Loop Rule 派生）

## Non-Blocking Follow-ups

- I4 按 I3 修复队列工作包执行修复（属 I4）
- I6 收口时根据 Cycle 2 / I1 候选输入决定是否开启新 Cycle（属 I6）

## Closure

Status Note: I3 发现裁决完成。产出 `ai-dev/audits/nop-code-invariants/i3-adjudication-matrix.md`，对 I2 red list 38 条真违规 + 95 条 open 悬空发现 + 5 条对抗探查新发现逐条裁决，零悬挂（每条 in-scope 发现有且仅有一个终态）。I4 修复队列按 10 个类别清扫工作包（WP-1..WP-10）组织，每个自包含（不变式 + 文件:行 + test-first + 预期门禁下降 + 人工确认标注），可作为 I4 确定性输入。idempotency red-list 锁（indexDirectory/indexFile）裁决为 P0 I4 修复（未降级）；38 真违规全部 I4 修复；ORM 结构变更（AR-149/150/51）标 plan-first、权限模型（AR-146(r8)/155(r10)/170）标 ask-first；AR-179/AR-153(r10)/AR-180 经 live 复核改判 stale。INV-05 等 4 项门禁登记为 Cycle 2/I1 候选。纯文档计划，无产品代码/ORM/API 变更。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor（fresh explore subagent，task_id `ses_00708443cffepC3CqGX0Km7W2y`）
- Audit Session: ses_00708443cffepC3CqGX0Km7W2y
- Evidence:
  - **Exit Criteria（Phase 1/2/3）**：PASS——独立 audit 报告 Check 1-9，9 项实质检查全部 PASS（Check 7 WP-9/WP-10 文件:行 延迟到 I4 sweep 时枚举，为文档化设计选择，非静默跳过）。三 Phase 均 `Status: completed` + 全部 `[x]`。
  - **Closure Gates**：PASS——逐条核对（见上方勾选）。裁决矩阵覆盖 red list 真违规 + 未覆盖悬空 + 对抗探查新发现；零悬挂；I4 工作包自包含；残余/后继每条附 Why Not Blocking；ORM/Auth 人工确认标注齐全；Cycle2/I1 候选已登记。
  - **输入消费正确性**：PASS——red list 真违规 38（query-limit 19 + entity-field-min 12 + idempotency 2 + adversarial 5）+ open 95 计数与 I2 输入一致。
  - **Anti-Slacking**：PASS——idempotency 锁 P0 I4；38 真违规全 I4 修复；data-consistency 10 + auth 3 + error-handling 子串/吞原子集均 P1 I4，无降级；P2/P3 项均附 Why Not Blocking + Successor Path。
  - **live 复核 stale（关键）**：PASS——AR-179（`nop-code.orm.xml:881-882` indexId 列 + `:918-920` 索引）✓；AR-153(r10)（`:210` uk_nop_code_index_name）✓；AR-149/150（`:256`/`:391` usages 无 cascadeDelete，对比 `:164` NopCodeIndex.usages 有 cascadeDelete）✓。audit 逐行复核 ORM 行号准确。
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan> --strict`**：退出码 0（Closure Gates 全勾选 + Closure Evidence 已写入）。
  - **`node ai-dev/tools/check-doc-links.mjs --strict`**：本计划新增文档 `i3-adjudication-matrix.md` 零断链（不在断链清单内）；全局退出码 1 源自其他模块 20 条预存断链（INDEX.md/其他 roadmap/skills），非本计划引入（与 I0/I1/I2 收口同模式）。
  - **Anti-Hollow / No-Silent-NoOp**：N/A（纯文档计划，无新增代码/组件/调用链）。
  - **Deferred 项分类检查**：PASS——P2/P3 后继与残余项均为 (a) 辅助/非核心特性（graph-algorithm/language-adapter）、(b) 优化项（performance/dead-code/硬编码）、(c) 窄面语义（AR-45/93/182/157）、(d) stale（AR-04/092/180/179/153(r10)），无 in-scope live defect 被降级到 deferred。
- 独立 audit 结论：**CAN CLOSE，无 blocker**。audit 指出的 2 处轻微计数/脚注瑕疵（§B 汇总表算法、§E.1 脚注 mis-attribution）已在收口前修正（§B 汇总与 §E.2 对齐到 95；§E.1 脚注更正为 incremental-desync AR-45 折入聚合）。

Follow-up:

- I4 按本矩阵 §C 工作包（WP-1..WP-10）执行修复；WP-4（AR-149/150）需 ORM plan-first 人工确认，WP-10（auth）需 ask-first。
- I6 收口时根据 §D（Cycle 2/I1 候选：INV-05 + 截断可观测 + error-handling + @Auth）决定是否开启 Cycle 2 新门禁沉淀。
- WP-9 执行时须逐 AR-ID 落实 error-handling 18 条聚合的 S（子串/吞异常→P1）/H（硬编码→残余）精确拆分。
