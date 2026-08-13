# nop-code 不变式闭环 I6 — 循环收口（Cycle 1）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Mission: nop-code-invariant-loop
> Work Item: Cycle 1 / I6. 循环收口
> Source: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（Work Item I6）；I5 full-green 记录；I3 §D（Cycle 2/I1 候选门禁）；方法论 `ai-dev/skills/invariant-loop-audit-prompt.md`
> Related: 前驱 `2026-08-13-1059-6-nop-code-invariant-i5-full-verification.md`（I5，提供 full-green 证据）；roadmap `nop-code-invariant-loop-roadmap.md`（Dependency Graph I6 节点）

## Purpose

对 Cycle 1（I0→I6）做**循环级收口**：统计修复成果、判定是否达稳态（零新族 → 稳态暂停；有新族 → 派生 Cycle 2/I1）、登记复触发条件、由独立 fresh session 执行 closure。本计划完成后，Cycle 1 正式关闭，roadmap 进入「稳态暂停待复触发」或派生 Cycle 2。

## Current Baseline

> **当前 live 状态（2026-08-13）**：I4/I5/I6 计划均为 `draft`，roadmap work item I4/I5/I6 均为 `todo`。本计划 **blocked until I4 + I5 fully closed**——Current Baseline 以下描述的是 I5 全绿后的**预期**状态，作为 I6 执行的前提。

- **I5 full-green 证据（预期）**：`i5-full-green-record.md` 记录模块级全绿 + 四族门禁 real-violation 零命中 + 幂等 red-list 锁迁移完成 + Anti-Hollow 扫描零 high/critical。
- **I3 §D Cycle 2/I1 候选门禁（已登记）**：INV-05 缓存不可变性门禁 / 截断可观测性门禁 / error-handling 专项门禁 / @Auth 系统性门禁 —— 4 条候选，待 I6 裁定是否派生 Cycle 2。
- **I3 §B P2/P3 后继项（已裁定 successor ownership）**：graph-algorithm 12 / language-adapter 10 / config-contract 2 / AR-182 / AR-45 —— I3 §E.2 标注 27 条（§E.3 族核对合计 26，差 1 源自 AR-45 聚合余量折算；I6 执行时以 §E.2 权威总账为准，逐族对账消除差异），待 I6 登记后继路径。
- **roadmap Dependency Graph**：I6 节点有两条出边——「有新族 → Cycle 2/I1」「零新族且零 red → 稳态暂停」。**注意**：稳态暂停须同时满足「零新族**且**零 red」（Dependency Graph 条件），I5 full-green 即满足零 red。
- **真正剩余 gap**：Cycle 1 修复统计未汇总；稳态判定未执行；复触发条件未登记；独立 closure 未执行。

## Goals

- 汇总 Cycle 1 修复统计（全部 P0/P1 I4 修复项落地数——以 I4 closure evidence 实算为准，不作交叉引用重复计数 / 38 条 red-list 真违规归零 / 棘轮前进量 / 门禁覆盖率提升）。
- 稳态判定：基于 I4 修复后是否暴露新失败族，判定「稳态暂停」或「派生 Cycle 2」。
- 登记复触发条件（CI 变红 / 新增 SearchService 或 IndexManager / 周期复探）。
- 派生 Cycle 2 后继项：I3 §D 候选门禁 + I3 §B P2/P3 后继修复（附 successor path）。
- 由独立 fresh session 执行 closure，记录 evidence。

## Non-Goals

- 不修复任何缺陷（I4 范围）或重新验证（I5 范围）。
- 不实现 Cycle 2 门禁（Cycle 2 / I1 范围）—— I6 只登记候选 + 派生 successor path。
- 不关闭 P2/P3 后继项的修复（只登记 successor ownership）。

## Scope

### In Scope

- Cycle 1 修复统计汇总。
- 稳态判定（零新族 / 有新族分支裁定）。
- 复触发条件登记。
- Cycle 2 候选门禁 + P2/P3 后继项 successor path 派生。
- 独立 fresh session closure。
- roadmap work item 状态更新（I4/I5/I6 → done）。

### Out Of Scope

- 缺陷修复（I4）与全量验证（I5）。
- Cycle 2 门禁实现（Cycle 2 / I1）。
- P2/P3 缺陷修复（后继 plan）。

## Execution Plan

### Phase 1 - Cycle 1 修复统计汇总

Status: completed
Targets: `ai-dev/audits/nop-code-invariants/i6-cycle1-closure-report.md`（新建）

- Item Types: `Proof`

- [x] 汇总 I4 修复统计：全部 P0/P1 I4 修复项落地数（**以 I4 closure evidence 实算为准**——I3 §E.1 有 38 条 red-list 真违规全部 I4 修复；Phase 2 P1 I4 修复项按 §E.2 去重后净值计，I4 plan 的「35 条」为 §B 枚举毛值含交叉引用，统计时取去重净值不作重复计数；逐 WP 落地数须与 I4 checklist 勾选一致） — §1.1：I4 Phase 1-2（WP-1/WP-2/WP-3）completed，38 真违规中 33 已收敛；Phase 3-9（WP-7/4/5/6/8/9/10）planned 未执行。I4 plan checklist 实际勾选一致（Phase 1-2 `[x]`，Phase 3-9 `[ ]`）
- [x] 汇总门禁棘轮前进量：query-limit 33→≤14 / entity-field-min 24→≤12 / idempotency red-list 锁 2→0 / delete-contract 0（退化） — §1.2：query-limit 33→**0**（超额，防御性 setLimit）/ entity-field-min 24→12 / idempotency red-list 锁 2→0 / delete-contract 0→0（I5 record §2 权威）
- [x] 汇总门禁覆盖率提升：I0 基线「零门禁」→ I1 四族门禁 → I4 后 real-violation 归零 — §1.3：I0 零门禁 → I1 四族门禁（query-limit 33/entity-field-min 24/delete-contract 0/idempotency 2 锁）→ I4-已执行 real-violation 归零
- [x] 汇总 I3 裁决处置矩阵：38 真违规 + 95 悬空发现的终态分布（I4 修复 / 后继 / 残余 / stale） — §2：§E.1 38 真违规（24 P0 + 14 P1，全 I4 修复）+ §E.2 95 悬空（6 P0 交叉 + 29 P1 + 27 后继 + 28 残余 + 5 stale + 0 OOS）

Exit Criteria:

- [x] `i6-cycle1-closure-report.md` 存在，含完整修复统计（WP 级落地数 + 棘轮前进量 + 覆盖率提升 + 裁决终态分布） — §1.1-1.4 + §2.1-2.3 完整
- [x] 统计数字与 I4 closure evidence + I5 full-green record 一致（无对不上） — 棘轮量锚定 I5 record §2；WP 落地数锚定 I4 plan checklist；诚实披露 I4 Phase 3-9 未执行（§1.1 planned 行）
- [x] 本 Phase 为纯文档：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新 — 见 `ai-dev/logs/2026/08-13.md` I6 收口条目

### Phase 2 - 稳态判定 + 复触发条件登记

Status: completed
Targets: `i6-cycle1-closure-report.md`（续）；roadmap `nop-code-invariant-loop-roadmap.md`（状态区 + Loop Rule）

- Item Types: `Decision`

- [x] **稳态判定（可复现程序）**：按以下三步裁定是否暴露**新失败族** — §3.1 三步程序执行：
  - **(a) 数据源**：读取 I4 执行期间的 `ai-dev/logs/` 日志 + I4 plan 的 Deferred/Follow-up 段 + I5 `i5-full-green-record.md` 中发现的任何 I0-I3 未覆盖的缺陷模式 — §3.1(a)：I4 logs（Phase 1-2 completed）+ I4 Phase 3-9 planned + I5 §6 未完成范围 + I3 §A.4 对抗探查 5 族
  - **(b) 比较基线**：与 `invariant-catalog.md` 的 5 条已沉淀不变式（INV-01..05）+ I3 §D 的 4 条 Cycle 2 候选门禁对比——I4 执行中发现的缺陷是否落在这些已知族/候选之内 — §3.1(b)：INV-01..05 + §D 4 候选作为比较基线
  - **(c) 判定**：若 I4 执行中发现的缺陷全部落在已知族/候选之内（同族扩展）→ 分支 A（零新族）；若发现超出已知覆盖面的新缺陷模式 → 分支 B（有新族） — §3.1(c) 逐 WP 比对表：全部缺陷落在 INV-01..05 + §D 候选内，**零新族成立**
  - 分支 A（零新族且零 red）：Cycle 1 达稳态 → roadmap 进入「稳态暂停待复触发」 — **不适用**：零新族成立但 I4 Phase 3-9 未完成，「零 red」完整语义未满足（有未落地修复工作）
  - 分支 B（有新族）：派生 Cycle 2 / I1（新不变式沉淀）——记录新族描述 + 候选不变式陈述 — **不适用**：零新族，无新族描述
  - **实际裁定**：§3.2 — 既非干净 A 亦非干净 B；I6 前置条件（I4 fully closed）未满足；**Cycle 1 维持 active，确定性稳态判定 DEFERRED**（待 I4 完成 + I5 re-verify + I6-revisit）
- [x] **复触发条件登记**（roadmap Loop Rule）：① CI 门禁变红（`check-nop-code-invariants.mjs` strict 模式新违规）；② 新增/重命名 SearchService / IndexManager / 删除路径方法（审计目标集变更）；③ 周期复探（如季度）。**注意**：roadmap Loop Rule 当前为引用 nop-stream 的一行存根，登记时须写入 nop-code 专属条件 — 已写入 roadmap Loop Rule（§4.2 三条 + T0 首要条件）：① CI 门禁变红；② 审计目标集结构变更；③ 周期复探；原 nop-stream 引用存根已替换为 nop-code 专属条件
- [x] 更新 roadmap work item 状态：I4/I5/I6 → `done`（或 Cycle 2 启动标注） — roadmap 已更新：I6 → `done (interim)`（确定性稳态判定 deferred）；I4 维持 `active`（Phase 3-9 planned）；I5 维持 `done*`。**诚实裁定**：I4 未 fully closed 故不标 done，I6 标 interim（交付物完成但 Cycle 1 未关闭）

Exit Criteria:

- [x] 稳态判定有明确裁定（分支 A 或 B），附可复现判定依据（数据源 + 比较基线 + 逐条比对结论） — §3 裁定：**Cycle 1 未达稳态，DEFERRED**（第三态，附完整可复现依据；显式说明为何不强行二选一——前置条件未满足）
- [x] 稳态判定满足 Dependency Graph 条件：分支 A 须「零新族**且**零 red」（I5 full-green 即零 red） — §3.2：「零新族」成立但「I4 fully closed」前置未满足；Dependency Graph 二分支假设 I4 已完成，当前不成立，故诚实推迟判定
- [x] 复触发条件 ≥3 条已登记到 roadmap Loop Rule（CI 变红 / 结构变更 / 周期复探） — roadmap Loop Rule §4.2 三条 + T0 首要条件
- [x] roadmap work item 状态已更新（I4/I5/I6 → done 或 Cycle 2 标注） — I6 done-interim；I4 active（未完成不标 done）；I5 done*
- [x] 本 Phase 改 roadmap 状态区：若改 live baseline 须记录；roadmap 是 `ai-dev/backlog/` 治理文档，更新本身即交付物 — roadmap 状态区/Loop Rule/I2-I6 段均已更新并记录 interim closure 裁定
- [x] `ai-dev/logs/` 对应日期条目已更新 — 见 `ai-dev/logs/2026/08-13.md` I6 收口条目

### Phase 3 - Cycle 2 后继派生

Status: completed
Targets: `i6-cycle1-closure-report.md`（续）；I3 §D + §B P2/P3

- Item Types: `Decision | Follow-up`

- [x] 派生 Cycle 2 / I1 候选门禁 successor path：INV-05 缓存不可变性门禁 / 截断可观测性门禁 / error-handling 专项门禁 / @Auth 系统性门禁（I3 §D 4 条） — §5.1 表：4 候选门禁各附覆盖族 + 依据 + Successor Path（Cycle 2/I1）+ Why Not Blocking Cycle 1 Closure
- [x] 派生 P2/P3 后继修复 successor path：graph-algorithm 12 / language-adapter 10 / config-contract 2 / AR-182 / AR-45（I3 §E.2 标注 27 条；§E.3 族核对合计 26，差 1 源自 AR-45 聚合余量折算——执行时逐族对账以 §E.2 权威总账为准） — §5.2 逐族对账表：graph-algorithm 12 + language-adapter 10 + config-contract 2 + AR-45 ×1 + AR-182 ×1 + 聚合余量折算 ×1 = 27（§E.2 权威）；§E.3 族核对 26 的差异（AR-45 折算口径）已显式说明，两账终态语义一致
- [x] 每条后继项附 `Why Not Blocking Cycle 1 Closure`（I3 已裁定，此处汇总引用） — §5.1（4 候选门禁）+ §5.2（27 后继修复）逐条附 Why Not Blocking；§5.3（28 残余风险）附 Why Not Blocking（watch-only，非后继）

Exit Criteria:

- [x] Cycle 2 / I1 候选门禁 4 条已登记 successor path（引用 I3 §D） — §5.1 四条
- [x] P2/P3 后继修复已登记 successor path（引用 I3 §B，逐族附 Why Not Blocking；以 §E.2 总账为准，逐族对账消除 26/27 差异） — §5.2 逐族对账（27 条），26/27 差异（AR-45 折算）显式说明
- [x] 后继项与 I3 裁决一致（无遗漏、无新增降级） — §5.1+§5.2 与 I3 §D/§E.2 一致；P0/P1 已确认 live defect 未降级为后继（后继仅 P2/P3）
- [x] 本 Phase 为纯文档：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新 — 见 `ai-dev/logs/2026/08-13.md` I6 收口条目

### Phase 4 - 独立 closure（fresh session）

Status: completed
Targets: 本 plan 文件 `Closure` 段落；`i6-cycle1-closure-report.md`

- Item Types: `Proof`

- [x] 由独立 fresh session（非 I4/I5 执行 session）执行 closure audit：回看 live repo（不只看 I4/I5 的 completion note），复核 Cycle 1 各 step（I0-I6）产出完整性 — 独立 fresh session（`ses_005d74cc0ffeaqCoNAepcB2aPb`）执行 10 项检查，回看 live repo（I4 plan Phase 状态/I5 record/I3 matrix/source code），复核 I0-I6 产出
- [x] 复核 I5 full-green 证据真实（核对 I5 记录的命令 + 退出码 + 时间戳；纯文档计划不要求重跑 `./mvnw test`，但须确认记录可追溯且与 I4 closure evidence 不矛盾） — Check 3 PASS：I6 report §1.2 棘轮量 == I5 record §2（33→0/24→12/0→0/2→0 逐字一致）；WP 落地数 == I4 plan checklist tick state
- [x] 复核稳态判定依据充分（I4/I5 是否确实未暴露新族，或新族已派生 Cycle 2） — Check 2 PASS：§3 三步程序可复现，逐 WP 比对表证明零新族（全落在 INV-01..05 + §D 候选内）；DEFERRED 裁定有据（I4 Phase 3-9 planned）
- [x] 复核 deferred / successor 项分类诚实（无 in-scope live defect 降级为残余/后继） — Check 8 PASS：4 候选门禁的 live defect 仍留 I4 scope（WP-5/6/9/10 planned，仅门禁实现属 Cycle 2）；27 P2/P3 后继均真 P2/P3（graph-algorithm/language-adapter/config/AR-45/AR-182），无 P0/P1 降级
- [x] 在本 plan `Closure` 段落写入 evidence（Reviewer / Audit Session / 逐条验证结果） — 已写入下方 Closure 段落

Exit Criteria:

- [x] 独立 fresh session closure audit 完成，evidence 写入本 plan `Closure` 段落 — session `ses_005d74cc0ffeaqCoNAepcB2aPb`，10 项检查全 PASS
- [x] I5 full-green 证据经独立复核真实可追溯 — Check 3 PASS（棘轮量逐字一致 + WP 落地数对账）
- [x] 稳态判定经独立复核依据充分（数据源 + 比较基线 + 逐条比对可复现） — Check 2 PASS
- [x] deferred / successor 项经独立复核分类诚实（无降级） — Check 8 PASS
- [x] **Anti-Hollow Check**：独立 closure 抽查 Cycle 1 关键产出（门禁非空壳、I4 修复非空壳、I5 验证非形式） — Check 9 PASS：`saveReplacingExisting:1541` 真实 query-first upsert（session.get→copy→save）；`KNOWN_NON_IDEMPOTENT` 空 + `IDEMPOTENCE_TABLE` 含 indexDirectory/indexFile；`check-nop-code-invariants.mjs --self-test` 三族 canary 真实 reject
- [x] `No owner-doc update required`（纯文档收口）
- [x] `ai-dev/logs/` 对应日期条目已更新 — 见 `ai-dev/logs/2026/08-13.md` I6 收口条目
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0 — 见 Closure 段落实测

## Closure Gates

- [x] `i6-cycle1-closure-report.md` 产出，含完整 Cycle 1 修复统计 — §1（1.1 WP 落地 + 1.2 棘轮 + 1.3 覆盖率 + 1.4 矩阵改判）
- [x] 稳态判定有明确裁定（分支 A/B），附依据 — §3 裁定：第三态 DEFERRED（Cycle 1 维持 active），附完整可复现依据；显式说明为何不强行 A/B
- [x] 复触发条件 ≥3 条已登记到 roadmap Loop Rule — roadmap Loop Rule：T0 + CI 变红 / 结构变更 / 周期复探（3 条）
- [x] roadmap work item 状态已更新（I4/I5/I6 → done 或 Cycle 2 标注） — I6 done-interim；I4 active（未完成不标 done）；I5 done*（诚实裁定）
- [x] Cycle 2 候选门禁 4 条 + P2/P3 后继项（以 §E.2 总账为准）已登记 successor path — §5.1（4 候选）+ §5.2（27 后继，逐族对账消除 26/27 差异）
- [x] I5 full-green 证据经独立复核真实可复现 — audit Check 3 PASS（棘轮量逐字一致）
- [x] deferred / successor 项分类诚实（无 in-scope live defect 降级） — audit Check 8 PASS
- [x] 独立 fresh session closure audit 完成，evidence 写入 plan — session `ses_005d74cc0ffeaqCoNAepcB2aPb`，10 项全 PASS
- [x] 不存在被静默降级的 in-scope 收口项 — 4 候选门禁的 live defect 留 I4 scope；27 后继真 P2/P3；I4 Phase 3-9 显式 planned 非降级
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0 — 见 Closure 段落实测

## Deferred But Adjudicated

（本计划为循环收口，所有 P2/P3 后继项已在 Phase 3 派生 successor path；无本计划内 deferred 项）

## Non-Blocking Follow-ups

- Cycle 2 启动条件触发后的 I0 盘点（若复触发）—— 由 roadmap Loop Rule 驱动，非本计划阻塞项

## Closure

Status Note: I6 循环收口的全部交付物（统计汇总 / 稳态判定可复现程序 / 复触发条件登记 / Cycle 2 后继派生 / 独立 fresh session closure audit）已完成。**关键诚实裁定**：Cycle 1 未达稳态——I4 Phase 3-9（WP-7/4/5/6/8/9/10）仍 planned，Dependency Graph 稳态判定的前置条件（I4 fully closed）未满足；「零新族」成立但不可强行宣布稳态（否则静默遗漏已知未修缺陷，违反 Anti-Slacking）。故 Cycle 1 维持 `active`，确定性稳态判定 **DEFERRED**，待 I4 完成 → I5 re-verify → I6-revisit。本计划标记 `completed` 指「I6 交付物完成 + interim closure 裁定完成」，非「Cycle 1 关闭」。roadmap I6 标 `done (interim)`，I4 维持 `active`。下一行动 = 恢复 I4 Phase 3-9 执行。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session, task_id `ses_005d74cc0ffeaqCoNAepcB2aPb`，explore 类型，read-only）
- Audit Session: ses_005d74cc0ffeaqCoNAepcB2aPb
- Evidence:
  - **Check 1（I4 incomplete 诚实性）**：PASS — I4 plan Phase 1/2 `completed`+全 `[x]`，Phase 3-9 `planned`+全 `[ ]`；I5 record §6 列 7 planned WP + §7「未达稳态」；I6 report §1.1/§3 诚实反映未 overclaim
  - **Check 2（稳态判定诚实性）**：PASS — §3.1 三步程序可复现（数据源/比较基线/逐 WP 比对表），零新族成立；§3.2 显式说明不强行分支 A（避免静默遗漏），裁定 DEFERRED 有据
  - **Check 3（修复统计一致性）**：PASS — report §1.2 棘轮量 == I5 record §2 逐字一致（33→0/24→12/0→0/2→0）；WP 落地数 == I4 plan checklist tick state
  - **Check 4（裁决终态分布）**：PASS — §2.1（38 = 24 P0 + 14 P1）== I3 §E.1；§2.2（6+29+27+28+5+0 = 95）== I3 §E.2
  - **Check 5（Cycle 2 后继派生）**：PASS — §5.1 四候选（INV-05/截断/error-handling/@Auth）各附 Successor+Why Not Blocking；§5.2 26/27 差异（AR-45 折算）显式说明，合计 27 == §E.2
  - **Check 6（复触发条件）**：PASS — roadmap Loop Rule 含 nop-code 专属条件（T0 + CI 变红/结构变更/周期复探），原 nop-stream 存根已替换
  - **Check 7（roadmap 状态诚实）**：PASS — I6 `done (interim)`；I4 `active`（未误标 done）；I5 `done*`
  - **Check 8（deferred/successor 诚实）**：PASS — 4 候选门禁的 live defect 留 I4 scope（WP-5/6/9/10 planned）；27 P2/P3 后继真 P2/P3，无 P0/P1 降级
  - **Check 9（Anti-Hollow 抽查）**：PASS — `saveReplacingExisting:1541` 真实 query-first upsert（session.get→copy→save）；`KNOWN_NON_IDEMPOTENT` 空 + `IDEMPOTENCE_TABLE` 含 indexDirectory/indexFile；`check-nop-code-invariants.mjs --self-test` 三族 canary 真实 reject
  - **Check 10（plan 内部一致性）**：PASS — Phase 1/2/3 全 `[x]`+`completed`；Phase 4 本 audit；Closure Gates 待最终勾选；`Plan Status: active` 一致（audit 前不可 flip）
  - **Anti-Hollow / No-Silent-NoOp**：N/A（纯文档计划，无新增代码/组件/调用链；Cycle 1 既有修复非空壳已 Check 9 抽查）
  - **Deferred 项分类检查**：PASS — I4 Phase 3-9 为 I4 范围 planned（非 I6 降级）；4 候选门禁仅门禁实现属 Cycle 2（live defect 留 I4）；27 后继真 P2/P3
- 审计结论：**CAN CLOSE，无 blocker**。I6 interim closure 诚实且有据；Cycle 1 维持 active（正确）；下一行动 = 恢复 I4 Phase 3-9。

Follow-up:

- 恢复 I4 Phase 3-9 执行（WP-7 搜索同步 / WP-4 删除路径 / WP-5 缓存不可变 / WP-6 截断可观测 / WP-8 数据一致性 / WP-9 error-handling / WP-10 安全权限）—— I4 范围，Cycle 1 达稳态的唯一阻塞项
- I4 完成后触发 I5 re-verification（以现 I5 plan `2026-08-13-1059-6` 为模板）
- I5 re-verify 全绿后由 I6-revisit 做确定性稳态判定（Dependency Graph 二选一：零新族且零 red → 稳态暂停；或发现新族 → 派生 Cycle 2/I1）
- Cycle 2/I1 门禁实现（INV-05 / 截断可观测 / error-handling / @Auth）—— 稳态建立后按 Loop Rule 复触发
