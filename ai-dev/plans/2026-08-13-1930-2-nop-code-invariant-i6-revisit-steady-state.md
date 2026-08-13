# nop-code 不变式闭环 I6-revisit — 确定性稳态判定（Cycle 1）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Mission: nop-code-invariant-loop
> Work Item: Cycle 1 / I6. 循环收口（revisit——确定性稳态判定）
> Source: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（header 下一行动 = I6-revisit 确定性稳态判定；Loop Rule T0）；前序 I6 interim closure `ai-dev/audits/nop-code-invariants/i6-cycle1-closure-report.md`（§3 稳态判定 DEFERRED）
> Related: 前驱 `2026-08-13-1930-1`（I5-reverify，提供 full-scope 全绿结论）；前序 I6 `2026-08-13-1059-7`（interim closure，确定性稳态判定 deferred）；I4 `2026-08-13-1059-5`（Phase 1-9 全部 completed）

## Purpose

前序 I6（`2026-08-13-1059-7`）执行了**过渡性循环收口（interim closure）**：交付了统计汇总 / 稳态判定可复现程序 / 复触发条件登记 / Cycle 2 后继派生，但**确定性稳态判定 DEFERRED**——理由是 I4 Phase 3-9 未完成，Dependency Graph 稳态判定的前置条件（I4 fully closed）未满足。现在 I4 Phase 1-9 全部 completed，I5-reverify（`2026-08-13-1930-1`）**将**对全范围执行全量独立验证；**本计划在 I5-reverify completed 且 full-scope 全绿后方可运行**确定性稳态判定：基于完整 I4+I5 数据，按 Dependency Graph 做二选一裁定（零新族且零 red → 稳态暂停；有新族 → 派生 Cycle 2/I1），更新 roadmap 状态标记，并由独立 fresh session 执行 closure。

## Current Baseline

> 已对 live repo + 前序计划核对（2026-08-13）。

- **I4 全部 Phase 1-9 completed**：I4 plan（`2026-08-13-1059-5`）`Plan Status: completed`，9 Phase 均 `completed`。Phase 4 ORM cascadeDelete + Phase 9 @Auth 阻塞待人工确认（gated，有 service-layer 降级 / 调查清单），已移入 I4 Deferred But Adjudicated。
- **I5-reverify 全范围全绿（本计划前置条件）**：`2026-08-13-1930-1` 目前 `Plan Status: draft`，尚未执行。`i5-full-green-record.md` 仍是 Phase 1-2 范围（§6/§7 说 Phase 3-9 planned）。**本计划 blocked until I5-reverify completed 且 full-scope 全绿。** 以下描述的是前置条件满足后的预期判定输入：I5-reverify 完成后，record 将更新为 Phase 1-9 全范围（模块测试全绿、四族门禁 real-violation 0、Anti-Hollow 0 high/critical、Phase 3-9 调用链连通）。**若 I5-reverify 发现 Phase 3-9 regression**：I5-reverify 会 blocked 或部分完成（不 `completed`），本计划自动 blocked，需先通过 I4 successor plan 或 reopen I4 修复 regression → I5-reverify 重跑全绿 → 本计划方可启动。Phase 1(a) 数据源复核时若发现 I5-reverify 未 `completed` 或 record 仍为 Phase 1-2 范围，立即停止执行。
- **前序 I6 interim closure（`i6-cycle1-closure-report.md`）已产出**：§1 统计、§2 裁决矩阵、§3 稳态判定程序、§4 复触发条件、§5 Cycle 2 后继派生——全部完成。但 §3 稳态判定结论为 DEFERRED（第三态），因为执行时 I4 Phase 3-9 未完成。
- **前序 I6 §3.1(c) 已建立「零新族」**：I4 待续 WP（Phase 3-9）的全部缺陷均落在 INV-01..05 已沉淀族或 I3 §D 已登记候选之内，无超出已知覆盖面的新缺陷模式。**本计划需复核**：Phase 3-9 实际执行后是否暴露了 §3.1(c) 比较时未预见的新失败模式。
- **Dependency Graph 条件**：I6 节点有两条出边——「有新族 → Cycle 2/I1」「零新族且零 red → 稳态暂停」。I5-reverify 全绿即满足零 red。
- **roadmap 状态标记（当前）**：I0-I3 done；I4 `done*`（Phase 1-9 全部落地，gated 项阻塞）；I5 `done*`（前序仅验 Phase 1-2，**需更新为 done**）；I6 `done (interim)`（**需 revisit**）。
- **真正剩余 gap**：确定性稳态判定未执行（依赖 I4 全完成 + I5 全范围验证；**前者已满足，后者待 I5-reverify completed**）；roadmap 状态标记需更新（I5 → done，I6 → done）；I6 closure report 需补充最终裁定。

## Goals

- 运行确定性稳态判定（可复现程序）：基于 I5-reverify full-scope 全绿 + Phase 3-9 实际执行后复核「零新族」，按 Dependency Graph 做二选一裁定。
- 更新 roadmap 状态标记：I5 `done*` → `done`；I6 `done (interim)` → `done`（或标注 Cycle 2 启动）。
- 更新 `i6-cycle1-closure-report.md`：补充确定性稳态判定最终裁定段落。
- 由独立 fresh session 执行 closure audit。
- roadmap header 状态行同步更新（反映 Cycle 1 关闭 / 稳态暂停 / Cycle 2 启动）。

## Non-Goals

- 不修复任何缺陷——若稳态判定发现新族，只派生 Cycle 2 successor path，不在本计划修。
- 不重新验证（I5-reverify 范围）。
- 不实现 Cycle 2 门禁（Cycle 2 / I1 范围）——本计划只做判定 + 派生 successor path。
- 不执行 Phase 9 @Auth / Phase 4 ORM cascadeDelete（阻塞待人工确认）。

## Scope

### In Scope

- 确定性稳态判定（零新族复核 + Dependency Graph 二选一裁定）。
- roadmap 状态标记更新（I5 → done，I6 → done / Cycle 2 标注）。
- `i6-cycle1-closure-report.md` 最终裁定补充。
- 独立 fresh session closure audit。
- roadmap header 状态行同步。

### Out Of Scope

- 缺陷修复 / 全量验证（I4 successor / I5-reverify）。
- Cycle 2 门禁实现（Cycle 2 / I1）。
- Phase 9 @Auth / Phase 4 ORM cascadeDelete（gated）。
- P2/P3 后继修复（已在前序 I6 §5.2 派生 successor path）。

## Execution Plan

### Phase 1 - 确定性稳态判定

Status: planned
Targets: `ai-dev/audits/nop-code-invariants/i6-cycle1-closure-report.md`（更新 §3）；`ai-dev/audits/nop-code-invariants/i5-full-green-record.md`（前置输入）；roadmap `nop-code-invariant-loop-roadmap.md`

- Item Types: `Decision | Proof`

- [ ] **(a) 数据源复核**：读取 I4 Phase 3-9 实际执行日志（`ai-dev/logs/2026/08-13.md` I4 Phase 3-9 条目）+ I4 plan（`2026-08-13-1059-5`）Phase 3-9 closure evidence + I5-reverify（`2026-08-13-1930-1`）full-scope record——确认 I4 全部 Phase 1-9 completed 且 I5-reverify `completed` 且全绿。**Gate**：若 I5-reverify 未 `completed` 或 record 仍为 Phase 1-2 范围 → **立即停止执行，本 plan blocked**
- [ ] **(b) 零新族复核（可复现程序）**：
  - **枚举实际缺陷模式**：从 I4 plan Phase 3-9 closure evidence + 任何 I4 successor plan closure evidence（如有，因 I4 已 completed 不能原地改）+ `ai-dev/logs/2026/08-13.md` I4 Phase 3-9 条目 + I5-reverify 各 phase 发现（如有）提取实际处理的 AR-ID 集合
  - **比对基线**：`invariant-catalog.md`（INV-01 实体加载字段最小化 / INV-02 删除路径物理删除契约 / INV-03 增量索引幂等性 / INV-04 查询结果上限 / INV-05 缓存对象不可变性）+ I3 §D 候选门禁（4 条：INV-05 缓存不可变性门禁 / 截断可观测性门禁 / error-handling 专项门禁 / @Auth 系统性门禁）+ I3 §B.4 data-consistency 族（§B.1-3 已被 INV-01..04 覆盖，§B.5/§B.6 已被 §D 候选覆盖，§B.4 是唯一「已知族但既无 INV 也无 §D 候选」的族，故单独点名）
  - **逐条比对**：产出一张「实际缺陷 vs 已知族」比对表（列：AR-ID / Phase / 实际修复内容摘要 / 落在哪条 INV 或 §D 候选 / 判定「已知族内」或「新族」），按 AR-ID 逐条（而非按 WP 聚合），锚定 Phase 3-9 **实际执行结果**而非预测
  - **新族判定标准**：若某 AR-ID 的实际缺陷模式无法归入任何已知 INV-01..05 / §D 候选 / §B.4 族，则标为「新族」→ 触发分支 B
- [ ] **(c) Dependency Graph 裁定**：
  - 若「零新族**且**零 red」（I5-reverify full-scope 全绿 = 零 red）→ **分支 A：稳态暂停**——Cycle 1 正式关闭，roadmap 进入「稳态暂停待复触发」
  - 若「有新族」→ **分支 B：派生 Cycle 2 / I1**——记录新族描述 + 候选不变式陈述 + successor plan path
  - **禁止第三态**：前序 I6 的 DEFERRED 是因为前置条件未满足（I4 未完成）；现在前置条件已满足，必须做确定性二选一裁定
- [ ] 更新 `i6-cycle1-closure-report.md`：在 §3 补充「确定性稳态判定最终裁定」段落（标注前序 DEFERRED 已 resolve，附本裁定依据 + 裁定结果）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 稳态判定有明确裁定（分支 A 或 B），附可复现依据（数据源 + 零新族复核逐条比对表 + Dependency Graph 条件验证）
- [ ] 稳态判定满足 Dependency Graph 条件：分支 A 须「零新族**且**零 red」（I5-reverify full-scope 全绿 = 零 red）；分支 B 须有具体新族描述
- [ ] Phase 3-9 实际执行的缺陷模式逐条比对完成（每条标为「落在已知族/候选内」或「新族」），无跳过
- [ ] **无第三态**：裁定为分支 A 或分支 B（不 DEFERRED），附理由
- [ ] `i6-cycle1-closure-report.md` §3 已补充最终裁定段落
- [ ] 本 Phase 改 roadmap / closure report：若改 live baseline 须记录；这些是 `ai-dev/` 治理文档，更新本身即交付物
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - roadmap 状态标记同步 + Cycle 2 后继确认

Status: planned
Targets: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（Work Item Status 表 + header 状态行 + Loop Rule）

- Item Types: `Decision`

- [ ] 更新 roadmap Work Item Status 表：I5 `done*` → `done`（全范围验证完成，描述文本同步更新——移除「I4 P3-9 未完成 → 需 re-verify」）；I6 `done (interim)` → `done`（确定性稳态判定完成）；I4 维持 `done*`（Phase 1-9 全部落地，gated 项阻塞——或若人工确认通过则 → `done`）
- [ ] 更新 roadmap header 状态行：反映 Cycle 1 最终裁定（稳态暂停 / Cycle 2 启动）；更新「下一行动」为复触发条件驱动（而非「恢复 I4 Phase 3-9」）
- [ ] 若裁定分支 A（稳态暂停）：确认 Loop Rule 复触发条件（§4.2 三条）仍然适用；T0（I4 Phase 3-9 立即恢复）已 resolved（I4 已完成），更新 T0 状态
- [ ] 若裁定分支 B（Cycle 2）：在 roadmap 添加 Cycle 2 启动标注 + 新族描述
- [ ] 复核 Phase 9 @Auth / Phase 4 ORM cascadeDelete gated 项的 successor path 仍有效（阻塞待人工确认，不随稳态判定自动关闭）

Exit Criteria:

- [ ] roadmap Work Item Status 表 I5/I6 已更新为 `done`（或 Cycle 2 标注）
- [ ] roadmap header 状态行已同步最终裁定
- [ ] roadmap Loop Rule T0 状态已更新（resolved / 仍 active）
- [ ] Phase 9 @Auth / Phase 4 ORM cascadeDelete gated 项 successor path 仍登记（阻塞待人工确认）
- [ ] roadmap 内部一致（header / Work Item Status / Loop Rule 三处不矛盾）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 独立 closure（fresh session）

Status: planned
Targets: 本 plan 文件 `Closure` 段落；`i6-cycle1-closure-report.md`

- Item Types: `Proof`

- [ ] 由独立 fresh session（非 I4/I5-reverify/I6-revisit 执行 session）执行 closure audit：回看 live repo + I5-reverify record + I6 closure report，复核稳态判定完整性
- [ ] 复核 I5-reverify full-scope 全绿证据真实（棘轮量 + Phase 3-9 test 全量通过）
- [ ] 复核稳态判定依据充分（零新族复核逐条比对可复现 / Dependency Graph 条件验证正确）
- [ ] 复核 deferred / successor 项分类诚实（Phase 9 @Auth / Phase 4 ORM 为 gated 阻塞非降级；27 P2/P3 后继真 P2/P3）
- [ ] 在本 plan `Closure` 段落写入 evidence

Exit Criteria:

- [ ] 独立 fresh session closure audit 完成，evidence 写入本 plan `Closure` 段落
- [ ] I5-reverify full-scope 全绿证据经独立复核真实可追溯
- [ ] 稳态判定经独立复核依据充分（数据源 + 零新族复核 + Dependency Graph 条件验证）
- [ ] deferred / successor 项经独立复核分类诚实
- [ ] **Anti-Hollow Check**：独立 closure 抽查 Cycle 1 关键产出（门禁非空壳、I4 修复非空壳、I5-reverify 验证非形式）——可引用 I5-reverify Phase 3 调用链追踪结果
- [ ] `No owner-doc update required`（纯文档收口）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Closure Gates

> **前置条件**：I5-reverify（`2026-08-13-1930-1`）completed 且 full-scope 全绿。

- [ ] 稳态判定有明确裁定（分支 A 或 B），附可复现依据
- [ ] 稳态判定满足 Dependency Graph 条件（分支 A 须零新族且零 red；分支 B 须有新族）
- [ ] roadmap Work Item Status 表已更新（I5 → done，I6 → done / Cycle 2 标注）
- [ ] roadmap header 状态行已同步最终裁定
- [ ] roadmap Loop Rule 内部一致（header / Work Item Status / Loop Rule 不矛盾）
- [ ] Phase 9 @Auth / Phase 4 ORM cascadeDelete gated 项 successor path 仍登记
- [ ] `i6-cycle1-closure-report.md` §3 已补充最终裁定段落
- [ ] I5-reverify full-scope 全绿证据经独立复核真实
- [ ] 独立 fresh session closure audit 完成，evidence 写入 plan
- [ ] 不存在被静默降级的 in-scope 收口项
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

（本计划为循环收口判定，所有 P2/P3 后继项已在前序 I6 §5 派生 successor path；Phase 9 @Auth / Phase 4 ORM cascadeDelete 阻塞待人工确认，不随稳态判定自动关闭。无本计划内 deferred 项。）

## Non-Blocking Follow-ups

- Cycle 2 启动条件触发后的 I0 盘点（若复触发）—— 由 roadmap Loop Rule 驱动
- Phase 9 @Auth / Phase 4 ORM cascadeDelete successor plan —— 阻塞待人工确认
- Cycle 2/I1 门禁实现（INV-05 / 截断可观测 / error-handling / @Auth）—— 稳态建立后按 Loop Rule 复触发

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<session ID>>
- Evidence:
  - <<逐条 Exit Criterion / Closure Gate 验证结果>>

Follow-up:

- <<完成时填写>>
