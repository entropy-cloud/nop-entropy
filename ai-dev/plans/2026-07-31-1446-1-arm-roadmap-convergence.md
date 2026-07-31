# Roadmap & Arm-Index Convergence

> Plan Status: completed
> Mission: audit-remediation
> Work Item: MA4 status convergence + arm-index MA5.7 修正 + P2/P3 deferred successor registration
> Last Reviewed: 2026-07-31
> Source: `ai-dev/backlog/audit-remediation-roadmap.md`, `ai-dev/audits/arm-index.md`, plan `2026-07-31-0000-2-arm-ma4-audit.md`, plan `2026-07-31-1024-2-arm-mv-validation.md`
> Draft Review: 3 轮独立子 agent 对抗性审查通过（含想象性分析），无 Blocker/Major（final round: ses_048f81646ffewvRZlyLjhRv3p0）

## Purpose

把 audit-remediation roadmap 与 arm-index 收敛到 live 事实状态：MA4.1-4.5 审计实际已完成（报告存在、MA4 audit plan 已 closure），但 roadmap 状态列仍为 `todo`；同时把 MA4 批次的 P2/P3 deferred findings 登记为明确的 successor 指针（其余里程碑 P2/P3 按 roadmap 规则 1 登记为 watch-only successor，后续批次另行规划）。本计划是纯文档计划，不含代码变更。

## Current Baseline

- MA4 audit plan `ai-dev/plans/2026-07-31-0000-2-arm-ma4-audit.md` 已 `completed`（2026-07-31，独立子 agent closure audit 证据，5 份报告 CONTENT_VERIFIED）
- 5 份 MA4 报告存在且有实际发现：`ai-dev/audits/2026-07-31-XXXX-arm-MA4.1-nop-ai-typesafety.md`（5 P2）、`2026-07-31-0539-arm-MA4.2-nop-ai-style.md`（7 P2）、`2026-07-31-XXXX-arm-MA4.3-nop-ai-test-coverage.md`（4 P2 + 6 P1 已由 MR2/MR4 修复；P2 含 -14，见 Phase 1 裁定）、`2026-07-31-arm-MA4.4-nop-ai-test-effectiveness.md`（4 P2）、`2026-07-31-XXXX-arm-MA4.5-nop-ai-doc-consistency.md`（7 P2，明细为准）
- arm-index `ai-dev/audits/arm-index.md` 已将 MA4.1-4.5 标记 `done`（含报告链接与 finding 计数）
- roadmap `ai-dev/backlog/audit-remediation-roadmap.md:63-68` 仍显示 4.1-4.5 为 `todo`，且头部 `最后更新：2026-07-30` 未含 MA4 完成记录 — **stale**
- arm-index MA5.7 行（`2026-07-31-arm-MA5.7-nop-ai-fix-verification.md`）P1 列显示 `5(open)+1(fixed)`，为 MA5.7 审计时点（MR2/MR3 落地前）快照；MV 矩阵（arm-index §P0/P1 可追溯性矩阵）显示全部 61 行 P1 `fixed`、open=0 — 该行计数为历史时点，需标注最终状态
- roadmap 规则 1：`本 roadmap 只处理 P0 和 P1。P2/P3 记录为 deferred successor`；MR1/MR2/MR3 plans 的 `Deferred But Adjudicated` 段将各批 P2/P3 分类为 `out-of-scope improvement`，但未登记 successor plan 路径
- 本批次后续计划 `2026-07-31-1446-2-arm-ma4-p2-code-quality.md`（MA4.1/MA4.2/MA4.5 P2）与 `2026-07-31-1446-3-arm-ma4-p2-test-quality.md`（MA4.3/MA4.4 P2）即 P2/P3 deferred 的第一批 successor

## Goals

- roadmap MA4 5 行状态收敛为 `done`，owner doc 指向对应审计报告
- arm-index MA5.7 行修正为最终状态（全部 open 已由 MR2/MR3 修复），消除历史时点计数误导
- roadmap ↔ arm-index ↔ 审计报告三方状态一致，无 stale `todo`
- 在 roadmap 中登记 P2/P3 deferred successor 指针（指向本批两个 P2 批量修复计划）
- 纯文档变更，无代码变更，`./mvnw` 构建验证豁免

## Non-Goals

- 不重跑任何审计（MA4 已审计完成）
- 不修复 P2/P3 finding 本身（由 successor 计划 `2026-07-31-1446-2` / `2026-07-31-1446-3` 承担）
- 不扩展本计划到 MCP 三模块（roadmap 明示排除，独立审计）
- 不回写历史 completed 计划的文本（Plan Guide 规则 #20）

## Scope

### In Scope

- `ai-dev/backlog/audit-remediation-roadmap.md` 的 MA4 状态列 + 头部更新记录 + successor 登记段
- `ai-dev/audits/arm-index.md` 的 MA5.7 行状态标注
- `ai-dev/audits/arm-index.md` 报告清单中 MA4.1/MA4.3/MA4.4/MA4.5 四行 finding 计数与报告明细的差异裁定与修正（见 Phase 1 裁定段，属"收敛到 live 事实"；MA4.2 行已一致不需改）
- 一致性核对（roadmap/arm-index/审计报告三方）

### Out Of Scope

- 任何 `src/` 代码变更
- MA4.1-4.5 之外其他里程碑的状态（已 done）
- P2/P3 修复实现

## Execution Plan

### Phase 1 — MA4 完成证据核验

Status: completed
Targets: `ai-dev/audits/2026-07-31-*MA4*.md`、`ai-dev/plans/2026-07-31-0000-2-arm-ma4-audit.md`

- Item Types: `Proof`

- [x] 核验 5 份 MA4 报告存在且含实际 finding（非空壳报告），记录文件路径与 finding 计数
- [x] 核验 MA4 audit plan 的 Closure 段含独立子 agent 审计证据
- [x] 核验 arm-index MA4.1-4.5 行状态为 `done`

> **计数差异裁定（已知基线）**：arm-index 报告清单各行 finding 计数与报告明细不完全一致。本 plan 统一裁定：**以报告明细为准**，Phase 3 同步修正 arm-index 对应行计数：
> - MA4.1：报告明细 7 个 finding（P2=5：-01/02/03/05/06；P3=2：-04/07）→ arm-index 行 `11(P2:5, P3:6)` 修正为 `7(P2:5, P3:2)`
> - MA4.2：报告明细 P2=7、P3=7（含 465+ 空白行实例归并为 7 类 P3）→ arm-index 行 `14(P2:7, P3:7)` 与明细一致，不改
> - MA4.3：报告明细 13 个 finding + 1 positive（P1=6：-01/02/03/04/05/07；P2=4：-06/08/12/14；P3=3：-09/11/13；Positive=1：-10）→ arm-index 行 `8(P2:8)` 修正为 `P1:6, P2:4, P3:3, Positive:1`；**MA4.3-14 为 P2 且有 successor 承接（见 1446-3 计划），不得静默丢弃**
> - MA4.4：报告明细 P2=4（-04/05/06/08）、P3=3（-01/02/03）、N/A=1（-07）→ arm-index 行 `8(P2:3, P3:5)` 修正为 `P2:4, P3:3, N/A:1`
> - MA4.5：报告明细 P2=7（-001~007）、P3=2（-008/009）；报告自身摘要表（P2=6/P3=3）与明细矛盾，以明细为准 → arm-index 行 `9(P2:6, P3:3)` 修正为 `9(P2:7, P3:2)` 并附摘要矛盾说明；MA4.3 报告摘要表同样存在内部矛盾（摘要 P2=3 漏计 -14），修正时一并附注

Exit Criteria:

- [x] 5 份报告路径 + P2/P3 计数（以报告明细为准）与 arm-index 的差异已按上述裁定记录（arm-index 修正动作在 Phase 3 执行，本 Phase 仅核验并记录）
- [x] MA4 audit plan Closure 证据可引用（session id / 日期）
- [x] 计数差异裁定已记录到本 plan 与 daily log
- [x] 核验结论记录到 `ai-dev/logs/2026/07-31.md`

### Phase 2 — roadmap MA4 状态同步

Status: completed
Targets: `ai-dev/backlog/audit-remediation-roadmap.md`

- Item Types: `Fix`

- [x] 将 4.1-4.5 行 Status 从 `todo` 改为 `done`，Owner Doc 指向对应审计报告路径
- [x] 更新头部 `最后更新` 为 v4（含 MA4 done + successor 登记说明）
- [x] 新增 `## P2/P3 Deferred Successors` 段，登记：
  - `2026-07-31-1446-2-arm-ma4-p2-code-quality.md`（MA4.1/MA4.2/MA4.5 P2 批量）
  - `2026-07-31-1446-3-arm-ma4-p2-test-quality.md`（MA4.3 P2 含 MA4.3-14 + MA4.4 P2 批量）
  - 其余 MA1-MA6 P2/P3 登记为 `watch-only residual`（即 roadmap 规则 1 的 deferred successor 记录：后续批次按严重度另行规划，本批不入 scope）
- [x] 全文件扫描确认无其他 stale 状态行（注意：roadmap 规则段有 2 处叙述性 `todo` 字样（执行模式/初始状态说明），非状态列，人工判断排除）

Exit Criteria:

- [x] roadmap 中不存在与 arm-index 状态冲突的 `todo` 行
- [x] successor 段包含明确 plan 路径与承接范围
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/2026/07-31.md` 已更新

### Phase 3 — arm-index MA5.7 行修正 + MA4 计数行对齐

Status: completed
Targets: `ai-dev/audits/arm-index.md`

- Item Types: `Fix`

- [x] 将 MA5.7 行 P1 计数 `5(open)+1(fixed)` 修正为最终状态：5 项 open 均已被 MR2/MR3 修复**或裁定关闭**（P1-MA5-002、P1-MA5.3-001/002 → MR2；MA5.2 F-016 → MR3；P1-MA5-003 → MR3+MV 裁定为 SPI 扩展点契约），证据为 MV 矩阵 61 行 P1 fixed/open=0；或改为引用 MV 矩阵的说明文字，避免历史时点计数误导
- [x] 按 Phase 1 裁定修正报告清单 MA4.1/MA4.3/MA4.4/MA4.5 四行 finding 计数（MA4.1 → `7(P2:5,P3:2)`、MA4.3 → `P1:6,P2:4,P3:3,Positive:1`、MA4.4 → `P2:4,P3:3,N/A:1`、MA4.5 → `9(P2:7,P3:2)`），并记录 MA4.5 报告摘要矛盾说明；MA4.2 行已一致不改
- [x] 核对 arm-index 状态汇总行（`已完成 32 | 进行中 0 | 待办 0`）与修改后一致

Exit Criteria:

- [x] arm-index MA5.7 行不再显示误导性 open 计数（或显式标注历史时点 + 最终状态）
- [x] MA4.1/MA4.3/MA4.4/MA4.5 行计数与报告明细一致（按 Phase 1 裁定）
- [x] 修改后 arm-index 全文交叉引用（报告清单 ↔ P1 汇总表 ↔ MV 矩阵）一致
- [x] `ai-dev/logs/2026/07-31.md` 已更新

### Phase 4 — 三方一致性终检 + closure

Status: completed
Targets: roadmap、arm-index、审计报告

- Item Types: `Proof | Follow-up`

- [x] 逐行比对 roadmap 状态列与 arm-index 报告清单状态列（比对范围：M0/MA1-MA6/MR1-MR4/MV/MG 各行；其中 MA 行与 arm-index 报告清单一一对应，MR/MV/MG 行与 arm-index 状态汇总及里程碑段比对），无冲突
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）
- [x] 独立子 agent closure audit（记录到 Closure 段）

Exit Criteria:

- [x] 三方一致核对表写入本 plan 或 daily log
- [x] check-doc-links 退出码 0
- [x] 独立 closure audit 证据已记录
- [x] No owner-doc update required（本计划全部改动位于 ai-dev 范畴；`docs-for-ai/` 不涉及）

## Closure Gates

> 纯文档计划（仅修改 `ai-dev/` 下文件），按 Plan Guide 纯文档计划豁免，`./mvnw` 构建验证条目删除。

- [x] roadmap MA4.1-4.5 状态为 `done` 且 owner doc 指向真实报告
- [x] P2/P3 successor 指针已登记且指向存在文件
- [x] arm-index MA5.7 行不再误导；MA4.1/4.3/4.4/4.5 行计数与报告明细一致（按 Phase 1 裁定）
- [x] roadmap ↔ arm-index ↔ 审计报告三方一致
- [x] 不存在被静默降级的 in-scope 项（本计划无 finding 归属；MA4.3-14 由 1446-3 承接，非静默丢弃）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）5 份报告确实存在且含 finding（非空壳），（b）文档内容与 live 状态一致（非仅改状态字），（c）计数裁定以报告明细为准且可复核
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-31-1446-1-arm-roadmap-convergence.md --strict` 退出码 0（closure 时）

## Deferred But Adjudicated

### MA4 P2 finding 的修复

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划为纯状态收敛；P2 finding 修复由明确 successor 计划（`2026-07-31-1446-2` / `2026-07-31-1446-3`）承接，非丢失。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/2026-07-31-1446-2-arm-ma4-p2-code-quality.md`、`ai-dev/plans/2026-07-31-1446-3-arm-ma4-p2-test-quality.md`

### MA1-MA3/MA5-MA6 的 P2/P3 findings

- Classification: `watch-only residual`
- Why Not Blocking Closure: MR1/MR2/MR3 计划已逐批裁定为 out-of-scope improvement，无已确认 live defect 残留（MV 矩阵 open=0）；本登记即 roadmap 规则 1 的 deferred successor 记录——后续批次按严重度另行规划，不构成当前 closure 阻塞。
- Successor Required: `no`（后续批次另行规划，非本批 scope）

## Non-Blocking Follow-ups

- 本批次后继计划执行完成后，可继续按 P2 严重度排序规划 MA1-MA3/MA5-MA6 的 P2 批量修复

## Closure

Status Note: roadmap MA4 状态、arm-index MA5.7 行与 MA4 计数行均已收敛到 live 事实，P2/P3 successor 已登记；纯文档计划，全部改动位于 ai-dev 范畴，独立 closure audit 通过。
Completed: 2026-07-31

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent（fresh session `ses_048efe4faffezX4hf4wQyy5FOv`）
- Audit Session: `ses_048efe4faffezX4hf4wQyy5FOv`
- Evidence:
  - Phase 1 Exit Criteria：PASS — 5 份 MA4 报告存在且含真实 finding（severity + 文件路径 + 行号 + 证据片段）；计数裁定与报告明细一致（MA4.1 P2=5/P3=2、MA4.2 P2=7/P3=7、MA4.3 P1=6/P2=4/P3=3/Positive=1、MA4.4 P2=4/P3=3/N/A=1、MA4.5 P2=7/P3=2）；MA4 audit plan Closure 段含独立子 agent 证据 `ses_04afc182affeaLQsefLSKtbhgQ`（2026-07-31，5 报告 CONTENT_VERIFIED）；arm-index MA4.1-4.5 行全部 `done`
  - Phase 2 Exit Criteria：PASS — roadmap 4.1-4.5 `done` + Owner Doc 指向 5 份真实报告（roadmap:64-68）；头部 v4（roadmap:3）；`## P2/P3 Deferred Successors` 段登记 1446-2/1446-3 且文件存在（roadmap:244-253）；无 stale 状态行（仅规则段 2 处叙述性 `todo`）；check-doc-links exit 0；daily log 已更新
  - Phase 3 Exit Criteria：PASS — MA5.7 行显示最终状态（MA5.7 时点 5 open + 1 fixed，5 项 open 均由 MR2/MR3 修复或裁定关闭，MV 矩阵 61 行 P1 fixed/open=0，arm-index:39）；MA4.1/4.3/4.4/4.5 四行计数与报告明细一致（arm-index:28-32）；计数说明段含 MA4.3/MA4.5 摘要表内部矛盾附注（arm-index:46）；状态汇总 已完成 32 与 32 行报告清单一致；报告清单 ↔ P1 汇总表 ↔ MV 矩阵交叉引用一致
  - Phase 4 Exit Criteria：PASS — roadmap 45 个工作项状态列全部 `done`，与 arm-index 32 行 `done` 及 MV 矩阵 61 行 P1 `fixed` open=0 无冲突；check-doc-links exit 0（0 errors / 4 warnings 全部位于未提交 successor plan 1446-3 既有引用，非本 plan 引入）；三方一致核对表已写入 daily log 2026/07-31.md；No owner-doc update required（docs-for-ai/ 未涉及）
  - Closure Gates：PASS — 9/9 全部满足（见上）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-31-1446-1-arm-roadmap-convergence.md --strict` 退出码 0
  - Anti-Hollow 检查：PASS — 5 份报告含详细 finding（非空壳）；文档改动反映 live 事实（计数从报告明细重推导、MA5.7 行引用 MV 矩阵、successor 指针指向存在文件）
  - Deferred 项分类检查：PASS — MA4 P2 修复为 out-of-scope improvement 且由 1446-2/1446-3 承接（非丢失）；MA1-MA3/MA5-MA6 P2/P3 为 watch-only residual（MV open=0，无 in-scope live defect 被降级）
  - Overall verdict: PASS（首轮审计 REJECT 仅因 plan 文件维护未完成——Phase 4/Closure Gates/Closure 段未填写；内容审计全部 PASS，维护后复验通过）

Follow-up:

- 本批次后继计划（1446-2/1446-3）执行完成后，可继续按 P2 严重度排序规划 MA1-MA3/MA5-MA6 的 P2 批量修复（watch-only residual）
- no remaining plan-owned work

## Optional Sections

## Risks And Rollback

- 纯文本修改，逐文件可回滚；`check-doc-links.mjs --strict` 保证链接完整性。
- 若核验发现 MA4 报告实际缺失或空壳（与 arm-index 矛盾），则暂停 Phase 2 并将该事实升级为 blocker，不强行标记 done。
