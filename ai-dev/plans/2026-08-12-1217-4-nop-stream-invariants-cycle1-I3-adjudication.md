# Cycle 1 / I3 — 发现裁决与工作项拟制（Adjudication And Work-Item Dispatch）

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Draft Review: 3 轮独立子 agent 对抗性审查通过（round 1：1 Blocker（裁决规则自相矛盾）+ 4 Major + 7 Minor，全部修复；round 2：12/12 修复到位、无 Blocker/Major、verdict 可转 active，3 Minor 建议修复；round 3：3/3 验证 PASS、无新问题、verdict approved）
> Source: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Work Item I3；前置 I2 产出 `ai-dev/audits/nop-stream-invariants/red-list.md`（I2 权威版）；mission `nop-stream-invariant-loop` 授权声明；`ai-dev/audits/nop-stream-invariants/invariant-catalog.md` §1（历史严重度参考）
> Related: 前置 `2026-08-12-1217-3-nop-stream-invariants-cycle1-I2-invariant-driven-audit.md`（I2，硬串行）；后续 I4（修复执行，以本 plan 的 P0/P1 派发清单为输入）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 1 / I3. 发现裁决与工作项拟制

## Purpose

对 I2 权威 red list 逐条裁决（P0/P1/P2/P3），P0/P1 派发为 I4 修复工作项（按族组织 + 类别清扫范围 + 测试要求），新族（如有）登记 Cycle 2 / I1 派生输入（Loop Rule 预授权），P2/P3 入 Follow-up Backlog，**裁决表零悬挂**。本 plan 是纯决策计划：不写代码、不跑门禁、不修复。

## Current Baseline

> **重要：I2 red list 权威版尚不存在**（`ai-dev/audits/nop-stream-invariants/red-list.md` 目前为 I1 pin-and-record 版）——本 plan 的 Current Baseline 对 I2 产出只作"前置依赖声明"，不声称已核对不存在的文件；I2 completed 后、本 plan 执行前，须按 I2 权威版内容复核本 plan 的假设（red list 条目集、watch-only 裁定、探查发现）。

- **前置依赖（硬串行）**：I2（`2026-08-12-1217-3-...`）产出的权威 `red-list.md` 为本 plan 的输入；本 plan 执行前 I2 必须已 `completed`。
- **已知裁决输入（I1 pin-and-record 版，I2 权威版可能增减）**：4 条已确认 live defect residual——AR-1（saveState 无锁 copy）、AR-11（setPendingCommits 接受任意 Map）、AR-9（JDBC registerNode lease=0 + getActiveNodes 过滤）、AR-18（InMemory renewLease 忽略 timeout）——均为 I0 catalog §3 实测 residual，预期裁决 P0/P1（**AR-18 例外：历史严重度 P2（catalog §1），以裁决分析为准**；其余 3 条历史 P0/P1）；3 条 watch-only（AR-15 / R15-AR-8 / R15-AR-9）处置取决于 I2 动态验证结论。
- **裁决规则来源**：roadmap I3（red list 逐条裁决 → P0/P1 派 I4；新族派 Cycle 2 / I1；P2/P3 入 Follow-up Backlog；裁决表零悬挂）；mission 授权（P0/P1 自动修复预授权，同 audit-remediation 先例；结构性重构——公共 API、模块边界、Operator 接口变更——执行前人工确认）；I0 catalog §1 历史严重度仅作参考，以本 plan 裁决分析为准。
- **分类诚实性基准（guide Minimum Rules #16 + Anti-Slacking Rule）**：允许的是**已裁定**为 non-blocking 的 residual / 优化项（附依据）；禁止的是**未裁定**的 deferral 与**无依据/静默**降级。裁决表中的 P2/P3 → backlog、watch-only 保留均为"已裁定处置"，只要附裁决依据即合规。
- **I4 输入契约**：P0/P1 派发须按族组织并标注类别清扫范围（修任一 Operator / SinkFunction 必 grep 全部同类兄弟——roadmap「类别清扫强制」），I4 计划以裁决表为输入起草。

## Goals

- 裁决表：I2 red list 每条 → 严重度（P0/P1/P2/P3）+ 族归属 + 处置（派 I4 / 入 backlog / watch-only 关闭或保留），**零悬挂**。
- P0/P1 → I4 工作项派发清单（按族组织、含类别清扫范围与测试要求）。
- 新族（如有）→ Cycle 2 / I1 派生登记（不变式陈述 + 触发证据，PD-n 先例链编号，供 I6 正式追加）。
- P2/P3 → Follow-up Backlog 登记。
- roadmap Work Item I3 状态流转（`todo`→`planned`→`done`）。

## Non-Goals

- **不修复任何项**（属 I4；裁决后未派发的项绝不修复）。
- **不跑门禁 / 不探查 / 不补验证**（属 I2；裁决遇信息不足时记录"需 I2 补充"并返回，不自行验证）。
- **不写新门禁**（新族门禁属 Cycle 2 / I1）。
- **不正式追加 Cycle 2 work item 到 roadmap**（追加动作属 I6 收口；I3 只登记派生输入记录）。

## Scope

### In Scope

- 裁决表拟制（I2 red list 逐条：严重度 / 族 / 处置）。
- P0/P1 派发清单（按族 + 类别清扫范围 + 测试要求 + 门禁复跑要求）。
- 新族派生登记（如有）+ P2/P3 Follow-up Backlog。
- 独立共识审查（裁决表分类诚实性）。
- `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（新文件）+ `ai-dev/logs/` 更新。

### Out Of Scope

- 修复（I4）、门禁运行（I2）、Cycle 2 派生（I6）、新门禁（Cycle 2 / I1）。
- 结构性重构方案的拟定（若某 P0/P1 项需要公共 API / 模块边界变更，只标注"需人工确认"门，具体方案由 I4 前置人工确认流程处理，不在本 plan 内设计）。

## Execution Plan

### Phase 1 - 裁决表拟制

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（新文件，待产出）

- Item Types: `Decision`
- [x] 与 I2 权威版对照复核本 plan 假设（red list 条目集、watch-only 裁定、探查发现），差异逐条记录在案（本 plan 执行前 I2 必须已 completed，差异不应出现；出现则按 I2 权威版修正基线）
- [x] 逐条裁决 I2 red list，**裁决表主键 = finding-ID（AR-n 稳定编号）**，每条：严重度 + 族归属 + 处置 + 依据。严重度标尺：**P0** = 数据丢失 / 损坏或核心恢复语义破坏；**P1** = 现实场景正确性 / 并发安全缺陷；**P2** = 健壮性 / 资源泄漏 / 边界场景；**P3** = 次要治理或优化。参考历史 finding 严重度（catalog §1）+ 影响面（数据正确性 / 泄漏 / 并发安全 / 恢复语义）+ 修复成本
- [x] 处置枚举：**P0/P1 → 派 I4**（已知族直接派发；新族 P0/P1 确认 defect 走**双轨**：I4 修复 + Cycle 2 派生登记（Phase 2 执行），仍逐条定严重度）；**P2/P3 → 入 Follow-up Backlog**（已裁定处置，附依据即合规）；**watch-only → 确认 I2 裁定结论并记录**（转述不重裁；I2 已裁定 verified 的项处置为"关闭"）
- [x] 每条裁决依据逐条记录（历史严重度参考 + 影响面分析 + I2 验证 / 探查结论引用），禁止无依据裁决
- [x] 裁决表写入 `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（每条：finding-ID / 位置 / 族 / 严重度 / 处置 / 依据 / I2 结论引用）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁决表存在且 I2 red list 每条一行，**零悬挂**（无未裁决条目）
- [x] 每条裁决依据可追溯（引用 I2 red list 条目 + 影响面分析）
- [x] 假设复核记录存在（与 I2 权威版无差异，或差异已按权威版修正）
- [x] 信息不足处置：裁决遇信息不足的条目标 `NEEDS_I2_SUPPLEMENT` + 记录提交人 / 日志并升级阻塞（预期罕见——I2 Phase 4 契约保证"每条含裁决输入，I3 可直接逐条裁决"）；**阻塞解除 = I2 补充产出后重开本 plan Phase 1**；有此类条目则该 Phase 不标 completed
- [x] No owner-doc update required（裁决为过程产出，不改变行为契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 工作项派发

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/adjudication-table.md`；`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` 的 `## Follow-up Backlog` 节（本 mission roadmap 当前无此节，如缺则创建；repo 惯例见 `nop-stream-production-roadmap.md:867`、`nop-stream-independent-audit-roadmap.md:516`）

- Item Types: `Decision | Follow-up`
- [x] P0/P1 派发清单：按族组织为 I4 工作项，**落点 = `ai-dev/audits/nop-stream-invariants/adjudication-table.md`「P0/P1 派发清单」小节**（每条：目标类 + 缺陷描述 + 预期行为 + **类别清扫范围**（grep 全类兄弟清单）+ 测试要求（test-first 先红后绿）+ 门禁复跑要求）；结构性重构项（公共 API / 模块边界 / Operator 接口）单独标注"需人工确认"（mission 授权声明：I4 执行前走人工确认流程，不改变其 P0/P1 必须修的裁决）
- [x] 新族派生登记（如有）：**落点 = `ai-dev/audits/nop-stream-invariants/adjudication-table.md` 独立小节「Cycle 2 派生登记」**，每条含不变式陈述 + 触发证据（`文件:行`）+ PD-n 编号（本仓尚无已铸造 PD-n（live grep 实测 0 处）；首号 = **PD-15**，接续 `ai-dev/lessons/` 最高编号 14）；供 I6 按 Loop Rule 正式追加 Cycle 2 / I1 work item
- [x] P2/P3 处置：入 roadmap `## Follow-up Backlog` 节（记录位置 / 严重度 / 未来触发条件），与 roadmap 裁决规则及既有 roadmap 惯例一致
- [x] 零悬挂复核：裁决表每条处置与派发清单一一对应（无"已裁决但无处可去"项）

Exit Criteria:

- [x] P0/P1 派发清单存在（按族 + 类别清扫范围 + 测试要求 + 门禁复跑要求）或显式声明"无 P0/P1 项"
- [x] 新族派生登记存在（如有；PD-n 编号 + 触发证据 + 落点 `ai-dev/audits/nop-stream-invariants/adjudication-table.md`）或显式声明"无新族"
- [x] Follow-up Backlog 已更新（或显式声明"无 P2/P3 项"）
- [x] 零悬挂复核通过：处置 ↔ 派发一一对应
- [x] No owner-doc update required（派发为过程产出）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 共识审查与移交

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/adjudication-table.md`；`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`

- Item Types: `Decision | Follow-up`
- [x] 独立子 agent（fresh session，review-only 禁改文件）审查裁决表：**分类诚实性**（已确认 live defect 不得**无依据/静默**降级至 P2/P3 / non-blocking 区——guide Minimum Rules #16 + Anti-Slacking Rule：已裁定处置附依据即合规，未裁定 deferral 即违规）、零悬挂、严重度合理性（对照 P0-P3 标尺）、派发完整性（含新族双轨）
- [x] 修复审查发现的 Blocker / Major 问题，必要时复审（每轮 fresh session）
- [x] roadmap Work Item I3 状态流转记录（本 plan 转 active 时 `todo`→`planned`；closure audit 通过后 `planned`→`done`）

Exit Criteria:

- [x] 独立审查记录存在（含审查结论与问题清单）；全部 Blocker 已解决
- [x] 无 in-scope live defect 被无依据降级至 backlog / watch-only（分类诚实性复核结论在案；已裁定处置附依据即合规）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 裁决表零悬挂（I2 red list 全部条目有处置）
- [x] P0/P1 派发 I4 清单完整（含类别清扫范围 + 测试要求）
- [x] 新族派生登记齐全（如有）或显式"无新族"；P2/P3 backlog 在案或显式"无"
- [x] 无已确认 live defect 被无依据/静默降级到 non-blocking 区（分类诚实性：已裁定处置附依据即合规；未裁定 deferral 即违规）
- [x] 独立子 agent closure-audit 已完成并记录证据（`ai-dev/logs/`）
- [x] **Anti-Hollow Check**：派发清单的每条与裁决表一一对应（非空壳派发）；backlog 条目不伪装成已修复；无空方法体 / 静默跳过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 本 plan 为纯决策 / 文档计划（无代码变更）：`./mvnw compile/test` 构建门禁与 `scan-hollow-implementations.mjs` 不适用，已按 guide「纯文档计划」删除

## Deferred But Adjudicated

无 in-scope deferred 项：结构性重构 P0/P1 项不属 deferral（裁决仍是"必须修"，执行门 = 人工确认，见 Phase 2 派发清单标注）；P2/P3 与 watch-only 保留均为 Phase 1 内**已裁定**处置，不入本段。

## Non-Blocking Follow-ups

- I4 计划起草时以本 plan 的 P0/P1 派发清单为输入。
- Cycle 2 派生登记（如有）**显式移交 I6**：I6 按 Loop Rule 以 `ai-dev/audits/nop-stream-invariants/adjudication-table.md`「Cycle 2 派生登记」小节为输入正式追加 roadmap work item。
- 若 I2 探查记录了"需更长周期验证"的场景（如极端并发压力），由 I4 / I5 视情况覆盖，不阻塞本 plan 关闭。

## Closure

Status Note: I2 权威 red list 全部 7 条 RL + 3 条 WO 逐条裁决零悬挂，P0/P1 派发 I4（4 工作项按族 + 类别清扫 + test-first + 门禁复跑），新族登记 PD-15（输出契约族，移交 I6），P2（RL-3）入 Follow-up Backlog；纯决策/文档计划，无代码变更，consensus review + closure audit 双独立 fresh-session 通过。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: independent subagent `ses_00aa8e1c9ffeT7fgsLaWiK8fd2`（closure audit，review-only）；`ses_00ab2c496ffeiTg5aVaY4yO115`（consensus review，review-only）
- Evidence:
  - **Phase 1 Exit Criteria 1-5 全部 PASS**：adjudication-table.md 存在（7 RL 行 + §0 假设复核 5 行差异表 + 零悬挂 §5）；每条依据引用 red-list + 影响面；无 NEEDS_I2_SUPPLEMENT；log 08-12.md 有 Phase 1-2 条目。
  - **Phase 2 Exit Criteria 6-9 全部 PASS**：§3 WI-1..4 六要素齐全（目标类/缺陷/预期/类别清扫/test-first/门禁复跑），显式声明无「需人工确认」门；§4 PD-15 编号正确（lessons 最高 14）；roadmap Follow-up Backlog 节含 RL-3 条目；§5 处置 ↔ 派发一一对应。
  - **Phase 3 Exit Criteria 10-12 全部 PASS**：fresh-session 共识审查 verdict approved（无 Blocker/Major，4 Minor 全部采纳修复）；无依据降级检查通过（RL-3 P2→backlog 附 TaskManager:83/:212 实测加固依据；RL-6 上浮附 I2 动态验证依据；WO 转述不重裁）；log 已更新。
  - **Closure Gates 1-9 全部 PASS**：零悬挂；派发完整；PD-15 + backlog 在案；Anti-Slacking 合规；Anti-Hollow 通过（派发 ↔ 裁决一一对应、backlog 标 🔲 Open 未伪装修复、产出文件非空壳）；`node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors）；纯文档计划声明成立（git diff 核对 nop-stream/ 代码目录零改动，`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS 03:43 全绿、mjs `all` exit 0 作基线复核）。
  - **Live 事实抽样 10 处全部属实**：JdbcClusterRegistry.java:115 写 0L / :174 `> ?`、InMemoryClusterRegistry.java:68-81、TwoPhaseCommitSinkFunction.java:76-83、WindowOperator.java:773-782、ChainingOutput.java:84-86、TaskManager.java:83/:212、EmbeddedDistributedExecutor.java:134、RpcDistributedExecutor.java:191。
  - Deferred 项分类检查：无 in-scope live defect 被降级（RL-3 为已裁定处置附依据，合规）。

Follow-up:

- I4 计划（`2026-08-12-1217-5-...`）以 `adjudication-table.md` §3 派发清单为输入起草（WI-1..4）。
- Cycle 2 派生登记 PD-15 显式移交 I6：以 §4 为输入正式追加 Cycle 2 / I1 work item（Loop Rule 预授权）。
- RL-3（Follow-up Backlog）：I4 修 F5 族类别清扫时或复探触发时评估修复。
- no remaining plan-owned work.
