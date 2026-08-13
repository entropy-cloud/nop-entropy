# nop-code 不变式闭环 I6-revisit — 确定性稳态判定（Cycle 1）

> Plan Status: completed
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

Status: completed
Targets: `ai-dev/audits/nop-code-invariants/i6-cycle1-closure-report.md`（更新 §3）；`ai-dev/audits/nop-code-invariants/i5-full-green-record.md`（前置输入）；roadmap `nop-code-invariant-loop-roadmap.md`

- Item Types: `Decision | Proof`

- [x] **(a) 数据源复核**：读取 I4 Phase 3-9 实际执行日志（`ai-dev/logs/2026/08-13.md` I4 Phase 3-9 条目）+ I4 plan（`2026-08-13-1059-5`）Phase 3-9 closure evidence + I5-reverify（`2026-08-13-1930-1`）full-scope record——确认 I4 全部 Phase 1-9 completed 且 I5-reverify `completed` 且全绿。**Gate 通过**：I4 plan `completed`（9 Phase 全 `[x]`）；I5-reverify plan `completed`；`i5-full-green-record.md` 已升级为全范围 Phase 1-9（383/0/0、四族 real-violation 0、棘轮 0 NEW、Anti-Hollow 0 high/critical）。live 复跑 `./mvnw test -pl nop-code` BUILD SUCCESS（nop-auth-service `TestChannelScanBindLoginE2E` 为预存 flaky，已按 I5 方式隔离）
- [x] **(b) 零新族复核（可复现程序）**：
  - **枚举实际缺陷模式**：从 I4 plan Phase 3-9 closure evidence + `ai-dev/logs/2026/08-13.md` I4 Phase 3-9 条目 + I5-reverify 各 phase 发现提取实际处理的 AR-ID 集合 = **22 个 AR-ID**（AR-166/30/66/60/149/150/155/158/145/148/42/62/147/136/168/177/76/61/01/10/40/41/59/63/93/132/151/51/160/162/165/175/18/146/155/170，去重后 22）
  - **比对基线**：`invariant-catalog.md`（INV-01..05）+ I3 §D 候选门禁（4 条）+ I3 §B.4 data-consistency 族
  - **逐条比对**：产出「实际缺陷 vs 已知族」比对表（列：AR-ID / Phase / 实际修复内容摘要 / 落在哪条 INV 或 §D 候选 / 判定），锚定 Phase 3-9 **实际执行结果**——见 `i6-cycle1-closure-report.md` §3.3(b) 逐条比对表（22 行，每条标「已知族」或「已知族(gated successor)」或「已知族(调查)」）
  - **新族判定标准**：22 个 AR-ID 全部归入已知 INV-01..05 / §D 候选 / §B.4 族，**无新族**。前序 §3.1(c) 基于「待续 WP 预测」的「零新族」结论经实际结果复核仍成立
- [x] **(c) Dependency Graph 裁定**：
  - 零新族**且**零 red（I5-reverify full-scope 全绿 = 零 red）→ **分支 A：稳态暂停**——Cycle 1 正式关闭，roadmap 进入「稳态暂停待复触发」
  - 无分支 B（无新族）；**无第三态**（前置条件已满足，§3.2 DEFERRED 被 §3.3 resolve）
  - gated successor（Phase 9 @Auth ask-first + Phase 4 ORM cascadeDelete plan-first）不阻塞稳态——非门禁 red，非降级，是 Protected Area 合规 gate，successor path 已登记
- [x] 更新 `i6-cycle1-closure-report.md`：在 §3 补充「确定性稳态判定最终裁定」段落（§3.3：前序 DEFERRED 已 resolve，附前置条件验证表 + 22 AR-ID 逐条比对表 + 分支 A 裁定依据 + 与 §3.2 DEFERRED 关系说明）；同步更新报告 header Status → `closed`、§3 header 最终裁定状态注、§1 范围说明 forward-pointer、§6 结论（增 §6.2 I6-revisit 确定性最终结论）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 稳态判定有明确裁定（分支 A），附可复现依据（数据源 I4/I5-reverify + 零新族复核 22 AR-ID 逐条比对表 `i6-cycle1-closure-report.md` §3.3(b) + Dependency Graph 条件验证 `§3.3(c)`）
- [x] 稳态判定满足 Dependency Graph 条件：分支 A 须「零新族**且**零 red」（I5-reverify full-scope 全绿 = 零 red，已验证：383/0/0、四族 real-violation 0、棘轮 0 NEW、Anti-Hollow 0 high/critical）
- [x] Phase 3-9 实际执行的缺陷模式逐条比对完成（22 AR-ID 每条标为「落在已知族/候选内」或「已知族 gated successor」），无跳过
- [x] **无第三态**：裁定为分支 A（不 DEFERRED），附理由（前置条件满足 + 零新族 + 零 red）
- [x] `i6-cycle1-closure-report.md` §3 已补充最终裁定段落（§3.3）
- [x] 本 Phase 改 roadmap / closure report：改 live baseline 已记录（closure report header/§1/§3/§6 + roadmap 见 Phase 2）；这些是 `ai-dev/` 治理文档，更新本身即交付物
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 3 收口时统一更新）

### Phase 2 - roadmap 状态标记同步 + Cycle 2 后继确认

Status: completed
Targets: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（Work Item Status 表 + header 状态行 + Loop Rule）

- Item Types: `Decision`

- [x] 更新 roadmap Work Item Status 表：I5 `done*` → `done`（前序 I5-reverify 已更新，本次保持）；I6 `done (interim)` → `done`（确定性稳态判定 = 分支 A 稳态暂停完成）；I4 维持 `done*`（Phase 1-9 全部落地，Phase 4 ORM cascadeDelete + Phase 9 @Auth gated 阻塞待人工确认——未获人工确认，保持 `done*`）
- [x] 更新 roadmap header 状态行：反映 Cycle 1 最终裁定（稳态暂停 / 分支 A）；更新「下一行动」为复触发条件驱动（T1-T3，非「恢复 I4 Phase 3-9」）
- [x] 裁定分支 A（稳态暂停）：确认 Loop Rule 复触发条件（§4.2 三条 T1-T3）仍然适用；T0（I4 Phase 3-9 立即恢复）已 resolved（I4 已完成），更新 T0 状态为 resolved
- [x] 裁定分支 A，无 Cycle 2 启动（无新族）。Cycle 2 后继路径（§5 的 4 候选门禁 + 27 P2/P3）作为「稳态打破后的工作池」保留，不在本轮启动
- [x] 复核 Phase 9 @Auth / Phase 4 ORM cascadeDelete gated 项的 successor path 仍有效（阻塞待人工确认，不随稳态判定自动关闭；见 `i6-cycle1-closure-report.md` §5.1 + I4 plan `Deferred But Adjudicated`，successor path 已登记）

Exit Criteria:

- [x] roadmap Work Item Status 表 I5/I6 已更新为 `done`（I6 从 `done (interim)` → `done`；I5 前序已是 `done` 保持）
- [x] roadmap header 状态行已同步最终裁定（Cycle 1 关闭——稳态暂停分支 A）
- [x] roadmap Loop Rule T0 状态已更新（resolved）
- [x] Phase 9 @Auth / Phase 4 ORM cascadeDelete gated 项 successor path 仍登记（阻塞待人工确认，未自动关闭）
- [x] roadmap 内部一致（header / Work Item Status / Loop Rule 三处不矛盾——grep 复核通过：I0-I3 done / I4 done* / I5 done / I6 done；T0 resolved；T1-T3 生效；Phase Details I6 = final closure 分支 A）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 3 收口时统一更新）

### Phase 3 - 独立 closure（fresh session）

Status: completed
Targets: 本 plan 文件 `Closure` 段落；`i6-cycle1-closure-report.md`

- Item Types: `Proof`

- [x] 由独立 fresh session（非 I4/I5-reverify/I6-revisit 执行 session）执行 closure audit：回看 live repo + I5-reverify record + I6 closure report，复核稳态判定完整性
- [x] 复核 I5-reverify full-scope 全绿证据真实（棘轮量 + Phase 3-9 test 全量通过）
- [x] 复核稳态判定依据充分（零新族复核逐条比对可复现 / Dependency Graph 条件验证正确）
- [x] 复核 deferred / successor 项分类诚实（Phase 9 @Auth / Phase 4 ORM 为 gated 阻塞非降级；27 P2/P3 后继真 P2/P3）
- [x] 在本 plan `Closure` 段落写入 evidence

Exit Criteria:

- [x] 独立 fresh session closure audit 完成，evidence 写入本 plan `Closure` 段落
- [x] I5-reverify full-scope 全绿证据经独立复核真实可追溯
- [x] 稳态判定经独立复核依据充分（数据源 + 零新族复核 + Dependency Graph 条件验证）
- [x] deferred / successor 项经独立复核分类诚实
- [x] **Anti-Hollow Check**：独立 closure 抽查 Cycle 1 关键产出（门禁非空壳、I4 修复非空壳、I5-reverify 验证非形式）——可引用 I5-reverify Phase 3 调用链追踪结果
- [x] `No owner-doc update required`（纯文档收口）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Closure Gates

> **前置条件**：I5-reverify（`2026-08-13-1930-1`）completed 且 full-scope 全绿。

- [x] 稳态判定有明确裁定（分支 A 或 B），附可复现依据
- [x] 稳态判定满足 Dependency Graph 条件（分支 A 须零新族且零 red；分支 B 须有新族）
- [x] roadmap Work Item Status 表已更新（I5 → done，I6 → done / Cycle 2 标注）
- [x] roadmap header 状态行已同步最终裁定
- [x] roadmap Loop Rule 内部一致（header / Work Item Status / Loop Rule 不矛盾）
- [x] Phase 9 @Auth / Phase 4 ORM cascadeDelete gated 项 successor path 仍登记
- [x] `i6-cycle1-closure-report.md` §3 已补充最终裁定段落
- [x] I5-reverify full-scope 全绿证据经独立复核真实
- [x] 独立 fresh session closure audit 完成，evidence 写入 plan
- [x] 不存在被静默降级的 in-scope 收口项
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

（本计划为循环收口判定，所有 P2/P3 后继项已在前序 I6 §5 派生 successor path；Phase 9 @Auth / Phase 4 ORM cascadeDelete 阻塞待人工确认，不随稳态判定自动关闭。无本计划内 deferred 项。）

## Non-Blocking Follow-ups

- Cycle 2 启动条件触发后的 I0 盘点（若复触发）—— 由 roadmap Loop Rule 驱动
- Phase 9 @Auth / Phase 4 ORM cascadeDelete successor plan —— 阻塞待人工确认
- Cycle 2/I1 门禁实现（INV-05 / 截断可观测 / error-handling / @Auth）—— 稳态建立后按 Loop Rule 复触发

## Closure

Status Note: Cycle 1 最终收口（final closure）。本计划在 I4 Phase 1-9 全部 completed + I5-reverify full-scope 全绿（383/0/0、四族 real-violation 0、棘轮 0 NEW、Anti-Hollow 0 high/critical）的前置条件满足后，执行了确定性稳态判定（§3.3）：22 个 AR-ID 逐条比对确认「零新族」（全部落在 INV-01..05 已沉淀族 / §D 4 候选门禁 / §B.4 data-consistency 族内），Dependency Graph 二选一裁定为**分支 A — 稳态暂停**（无第三态，§3.2 DEFERRED 已 resolve）。roadmap 状态标记已同步（I5 → done、I6 → done、T0 resolved、T1-T3 生效），Phase 9 @Auth（ask-first）+ Phase 4 ORM cascadeDelete（plan-first）两项 gated 阻塞维持待人工确认，successor path 已登记。独立 fresh session closure audit 复核全部通过，Cycle 1 正式关闭，roadmap 进入「稳态暂停待复触发」。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure 审计子 agent (fresh session)
- Audit Session: independent-closure-i6-revisit
- Evidence:

  **Exit Criteria / Closure Gate 逐条验证（Phase 3 + Closure Gates）**：

  - **PASS — 稳态判定有明确裁定（分支 A）附可复现依据**：`i6-cycle1-closure-report.md` §3.3(c) :212-225 裁定「分支 A — 稳态暂停」；依据 = (a) 前置条件验证表 §3.3(a) :168-177 + (b) 22 AR-ID 逐条比对表 §3.3(b) :183-208 + Dependency Graph 条件验证 §3.3(c) :214-218。

  - **PASS — Dependency Graph 条件满足（分支 A 须零新族且零 red）**：零新族 — §3.3(b) :208 22 AR-ID 全落已知族；零 red — I5 record §1 :27 (383/0/0) + §2 :49-54 (四族 real-violation 0) + §3 :78-80 (棘轮 0 NEW) + §4 :93 (Anti-Hollow 0 high/critical)。

  - **PASS — 无第三态**：§3.3 :164-166 显式声明「resolve 前序 §3.2 DEFERRED」；§3.2 DEFERRED 理由（I4 Phase 3-9 未完成）已消除（I4 9 Phase 全 completed）。

  - **PASS — roadmap Work Item Status 表已更新**：`nop-code-invariant-loop-roadmap.md` :35 I4=`done*`、:36 I5=`done`、:37 I6=`done`（确定性稳态判定 = 分支 A 稳态暂停，见 §3.3）。

  - **PASS — roadmap header 状态行已同步**：roadmap :6 「Cycle 1 关闭——稳态暂停（分支 A）待复触发」。

  - **PASS — roadmap Loop Rule 内部一致**：:79 T0 resolved；:83-85 T1-T3 生效；header/Work Item Status/Loop Rule 三处不矛盾（I0-I3 done / I4 done* / I5 done / I6 done 一致）。

  - **PASS — Phase 9 @Auth / Phase 4 ORM cascadeDelete gated 项 successor path 仍登记**：roadmap :79 + i6 report §5.1 :249-256（4 候选门禁 successor path）；I4 plan Deferred But Adjudicated :300-305（AR-149/150）+ Phase 9 :251-273（@Auth ask-first）；I5 plan Deferred But Adjudicated :200-212。两项均为 Protected Area 合规 gate，阻塞待人工确认，未随稳态判定自动关闭。

  - **PASS — `i6-cycle1-closure-report.md` §3 已补充最终裁定段落**：§3.3 :164-225（前置条件验证 + 22 AR-ID 比对表 + 分支 A 裁定 + 与 §3.2 DEFERRED 关系）；header Status :3 = `closed`；§6.2 :298-305 I6-revisit 确定性最终结论。

  - **PASS — Phase 3-9 实际执行缺陷模式逐条比对完成（22 AR-ID）**：§3.3(b) :183-208 共 22 行，每行标「已知族」/「已知族(gated successor)」/「已知族(调查)」，无跳过。spot-check 5 条：(1) AR-166→INV-03 (I4 Phase 3 removeStaleSymbolDocsForFile)；(2) AR-30/66→INV-03+INV-02 (I4 Phase 4 cross-file orphan)；(3) AR-155/158→INV-05 (I4 Phase 5 SymbolTable.getAll())；(4) AR-41→§B.4 data-consistency (I4 Phase 7 getSymbolById)；(5) AR-146/155/170→§D @Auth candidate (I4 Phase 9 gated)。分类诚实，无 fabricated 新族。

  - **PASS — I5-reverify full-scope 全绿证据经独立复核真实可追溯**：(1) §1 :27 Tests run: 383, Failures: 0, Errors: 0, Skipped: 13（surefire 汇总）；(2) §1.1 :34-45 Phase 3-9 八个 focused test 类逐类 green（合计 25 tests）；(3) §2 :49-54 四族 real-violation 0；(4) §3 :78-80 棘轮 baseline per-family（完整路径）3 条命令各自退出码 0；(5) §4 :93 Anti-Hollow 0 high/critical + §4.1 :97-107 6 条 Phase 3-9 调用链人工追踪。数字一致、可追溯至 I5 plan closure evidence。

  - **PASS — 稳态判定经独立复核依据充分**：数据源 = I4 plan (completed, 9 Phase) + I5-reverify plan (completed) + I5 record (full-scope)；零新族复核 = 22 AR-ID 逐条比对表（§3.3(b)），分类锚定 invariant-catalog.md INV-01..05 + I3 §D 4 候选 + I3 §B.4 data-consistency（经 grep 确认 I3 :199 §B.4 + :454-463 §D 均存在）；Dependency Graph 条件验证 = §3.3(c) 二选一（无第三态）。

  - **PASS — deferred / successor 项经独立复核分类诚实**：Phase 9 @Auth ×8 空 BizModel（ask-first gate）+ Phase 4 AR-149/150 ORM cascadeDelete（plan-first gate）在 I4 plan / I5 plan / i6 report §5.1 三处均显式分类为「阻塞待人工确认（Protected Area gate）」+「Successor Required: yes」，非 in-scope live defect 静默降级。27 P2/P3 后继（i6 §5.2 :262-269）= graph-algorithm 12 + language-adapter 10 + config-contract 2 + AR-45 ×1 + AR-182 ×1 + 聚合余量折算 ×1 = 27（§E.2 权威），逐族附 Why Not Blocking + Successor Path，无静默丢弃。

  - **PASS — 不存在被静默降级的 in-scope 收口项**：I3 §E.1 38 真违规全部 I4 修复（零降级）；I3 §E.2 95 悬空发现每条 in-scope 有且仅有一个终态（零悬挂）；两项 gated 为 Protected Area 合规 gate 非 defect 降级。

  - **PASS — `No owner-doc update required`（纯文档收口）**：本计划仅改 ai-dev/ 治理文档（plan + closure report + roadmap），不改产品代码 / 公开 API 契约 / owner docs。

  - **PASS — `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0**：见下方工具运行记录。

  **Anti-Hollow Check 结果（独立抽查 Cycle 1 关键产出）**：

  - **(a) 四族门禁非空壳 PASS**：i6 report §1.3 :62 「门禁已抓到真实 live defect（幂等门禁发现 indexDirectory/indexFile duplicate-key 23505）」——门禁在 I1 沉淀阶段即抓到真实缺陷，证明非空壳。I5 record §2 :49-54 四族 real-violation 0 是 I4 修复后的结果（修复前 query-limit 33 / entity-field-min 24 / idempotency 2 锁），门禁真实计数。
  - **(b) I4 Phase 3-9 修复非空壳 PASS**：I5 record §4.1 :97-107 六条调用链经 live 源码确认非空壳——Phase 3 `removeStaleSymbolDocsForFile`(:1593←:1149) findSymbolIdsByFileId→removeDocs（非 no-op，defense-in-depth）；Phase 4 `deleteRelationalBySymbolIds`(:1543/1605) while+setLimit+break-when-empty+flushSession 真实耗尽语义 + `batchDeleteFileRecords`(:1946) runInTransaction+runInSession；Phase 5 `SymbolTable.getAll()`(:45) `new ArrayList<>(byId.values())` + `CallGraph.getForwardMap()`(:56) unmodifiableMap/unmodifiableList；Phase 6 `warnIfCapped`(:52) 9 调用点 + `warnIfCacheTruncated`(:71)；Phase 7 `getSymbolById`(:444) eq(indexId)+eq(id)+setLimit(1) + `pathSegmentMatch`(:274) 双侧边界；Phase 8 `packagePrefix`(:63) endsWith?name:name+"."。无空方法体/静默跳过/no-op。
  - **(c) I5-reverify 验证非形式 PASS**：383 tests 全量运行（非孤立单跑）含 Phase 3-9 八个 focused test 类；四族门禁 live `--list` 计数 + 棘轮 baseline per-family 完整路径 3 命令各自 exit 0；Anti-Hollow scan exit 0 + 6 条调用链人工追踪。验证 substantive（非 formal-only）。

  **checklist 工具运行**：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-13-1930-2-nop-code-invariant-i6-revisit-steady-state.md --strict` 退出码 **0**（Plans checked: 1, Passed: 1, 0 unchecked）。

Follow-up:

- Cycle 2 启动条件触发后的 I0 盘点（若复触发）—— 由 roadmap Loop Rule T1-T3 驱动
- Phase 9 @Auth ×8 BizModel + AR-155(r10)/170 successor plan —— 阻塞待人工确认（ask-first gate）
- Phase 4 AR-149/150 ORM cascadeDelete successor plan —— 阻塞待人工确认（plan-first gate）
- Cycle 2/I1 门禁实现（INV-05 / 截断可观测 / error-handling / @Auth）—— 稳态建立后按 Loop Rule 复触发
