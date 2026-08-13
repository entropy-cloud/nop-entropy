# nop-code 不变式闭环 I6 — 循环收口（Cycle 1）

> Plan Status: active
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

Status: planned
Targets: `ai-dev/audits/nop-code-invariants/i6-cycle1-closure-report.md`（新建）

- Item Types: `Proof`

- [ ] 汇总 I4 修复统计：全部 P0/P1 I4 修复项落地数（**以 I4 closure evidence 实算为准**——I3 §E.1 有 38 条 red-list 真违规全部 I4 修复；Phase 2 P1 I4 修复项按 §E.2 去重后净值计，I4 plan 的「35 条」为 §B 枚举毛值含交叉引用，统计时取去重净值不作重复计数；逐 WP 落地数须与 I4 checklist 勾选一致）
- [ ] 汇总门禁棘轮前进量：query-limit 33→≤14 / entity-field-min 24→≤12 / idempotency red-list 锁 2→0 / delete-contract 0（退化）
- [ ] 汇总门禁覆盖率提升：I0 基线「零门禁」→ I1 四族门禁 → I4 后 real-violation 归零
- [ ] 汇总 I3 裁决处置矩阵：38 真违规 + 95 悬空发现的终态分布（I4 修复 / 后继 / 残余 / stale）

Exit Criteria:

- [ ] `i6-cycle1-closure-report.md` 存在，含完整修复统计（WP 级落地数 + 棘轮前进量 + 覆盖率提升 + 裁决终态分布）
- [ ] 统计数字与 I4 closure evidence + I5 full-green record 一致（无对不上）
- [ ] 本 Phase 为纯文档：`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 稳态判定 + 复触发条件登记

Status: planned
Targets: `i6-cycle1-closure-report.md`（续）；roadmap `nop-code-invariant-loop-roadmap.md`（状态区 + Loop Rule）

- Item Types: `Decision`

- [ ] **稳态判定（可复现程序）**：按以下三步裁定是否暴露**新失败族**：
  - **(a) 数据源**：读取 I4 执行期间的 `ai-dev/logs/` 日志 + I4 plan 的 Deferred/Follow-up 段 + I5 `i5-full-green-record.md` 中发现的任何 I0-I3 未覆盖的缺陷模式
  - **(b) 比较基线**：与 `invariant-catalog.md` 的 5 条已沉淀不变式（INV-01..05）+ I3 §D 的 4 条 Cycle 2 候选门禁对比——I4 执行中发现的缺陷是否落在这些已知族/候选之内
  - **(c) 判定**：若 I4 执行中发现的缺陷全部落在已知族/候选之内（同族扩展）→ 分支 A（零新族）；若发现超出已知覆盖面的新缺陷模式 → 分支 B（有新族）
  - 分支 A（零新族且零 red）：Cycle 1 达稳态 → roadmap 进入「稳态暂停待复触发」
  - 分支 B（有新族）：派生 Cycle 2 / I1（新不变式沉淀）——记录新族描述 + 候选不变式陈述
- [ ] **复触发条件登记**（roadmap Loop Rule）：① CI 门禁变红（`check-nop-code-invariants.mjs` strict 模式新违规）；② 新增/重命名 SearchService / IndexManager / 删除路径方法（审计目标集变更）；③ 周期复探（如季度）。**注意**：roadmap Loop Rule 当前为引用 nop-stream 的一行存根，登记时须写入 nop-code 专属条件
- [ ] 更新 roadmap work item 状态：I4/I5/I6 → `done`（或 Cycle 2 启动标注）

Exit Criteria:

- [ ] 稳态判定有明确裁定（分支 A 或 B），附可复现判定依据（数据源 + 比较基线 + 逐条比对结论）
- [ ] 稳态判定满足 Dependency Graph 条件：分支 A 须「零新族**且**零 red」（I5 full-green 即零 red）
- [ ] 复触发条件 ≥3 条已登记到 roadmap Loop Rule（CI 变红 / 结构变更 / 周期复探）
- [ ] roadmap work item 状态已更新（I4/I5/I6 → done 或 Cycle 2 标注）
- [ ] 本 Phase 改 roadmap 状态区：若改 live baseline 须记录；roadmap 是 `ai-dev/backlog/` 治理文档，更新本身即交付物
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Cycle 2 后继派生

Status: planned
Targets: `i6-cycle1-closure-report.md`（续）；I3 §D + §B P2/P3

- Item Types: `Decision | Follow-up`

- [ ] 派生 Cycle 2 / I1 候选门禁 successor path：INV-05 缓存不可变性门禁 / 截断可观测性门禁 / error-handling 专项门禁 / @Auth 系统性门禁（I3 §D 4 条）
- [ ] 派生 P2/P3 后继修复 successor path：graph-algorithm 12 / language-adapter 10 / config-contract 2 / AR-182 / AR-45（I3 §E.2 标注 27 条；§E.3 族核对合计 26，差 1 源自 AR-45 聚合余量折算——执行时逐族对账以 §E.2 权威总账为准）
- [ ] 每条后继项附 `Why Not Blocking Cycle 1 Closure`（I3 已裁定，此处汇总引用）

Exit Criteria:

- [ ] Cycle 2 / I1 候选门禁 4 条已登记 successor path（引用 I3 §D）
- [ ] P2/P3 后继修复已登记 successor path（引用 I3 §B，逐族附 Why Not Blocking；以 §E.2 总账为准，逐族对账消除 26/27 差异）
- [ ] 后继项与 I3 裁决一致（无遗漏、无新增降级）
- [ ] 本 Phase 为纯文档：`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 独立 closure（fresh session）

Status: planned
Targets: 本 plan 文件 `Closure` 段落；`i6-cycle1-closure-report.md`

- Item Types: `Proof`

- [ ] 由独立 fresh session（非 I4/I5 执行 session）执行 closure audit：回看 live repo（不只看 I4/I5 的 completion note），复核 Cycle 1 各 step（I0-I6）产出完整性
- [ ] 复核 I5 full-green 证据真实（核对 I5 记录的命令 + 退出码 + 时间戳；纯文档计划不要求重跑 `./mvnw test`，但须确认记录可追溯且与 I4 closure evidence 不矛盾）
- [ ] 复核稳态判定依据充分（I4/I5 是否确实未暴露新族，或新族已派生 Cycle 2）
- [ ] 复核 deferred / successor 项分类诚实（无 in-scope live defect 降级为残余/后继）
- [ ] 在本 plan `Closure` 段落写入 evidence（Reviewer / Audit Session / 逐条验证结果）

Exit Criteria:

- [ ] 独立 fresh session closure audit 完成，evidence 写入本 plan `Closure` 段落
- [ ] I5 full-green 证据经独立复核真实可追溯
- [ ] 稳态判定经独立复核依据充分（数据源 + 比较基线 + 逐条比对可复现）
- [ ] deferred / successor 项经独立复核分类诚实（无降级）
- [ ] **Anti-Hollow Check**：独立 closure 抽查 Cycle 1 关键产出（门禁非空壳、I4 修复非空壳、I5 验证非形式）
- [ ] `No owner-doc update required`（纯文档收口）
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Closure Gates

- [ ] `i6-cycle1-closure-report.md` 产出，含完整 Cycle 1 修复统计
- [ ] 稳态判定有明确裁定（分支 A/B），附依据
- [ ] 复触发条件 ≥3 条已登记到 roadmap Loop Rule
- [ ] roadmap work item 状态已更新（I4/I5/I6 → done 或 Cycle 2 标注）
- [ ] Cycle 2 候选门禁 4 条 + P2/P3 后继项（以 I3 §E.2 总账为准）已登记 successor path
- [ ] I5 full-green 证据经独立复核真实可复现
- [ ] deferred / successor 项分类诚实（无 in-scope live defect 降级）
- [ ] 独立 fresh session closure audit 完成，evidence 写入 plan
- [ ] 不存在被静默降级的 in-scope 收口项
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

（本计划为循环收口，所有 P2/P3 后继项已在 Phase 3 派生 successor path；无本计划内 deferred 项）

## Non-Blocking Follow-ups

- Cycle 2 启动条件触发后的 I0 盘点（若复触发）—— 由 roadmap Loop Rule 驱动，非本计划阻塞项

## Closure

Status Note: （待执行完成后填写：Cycle 1 是否达稳态 / 是否派生 Cycle 2）
Completed: 

Closure Audit Evidence:

- Reviewer / Agent: （待独立 closure audit 填写）
- Audit Session: 
- Evidence: （待填写）
