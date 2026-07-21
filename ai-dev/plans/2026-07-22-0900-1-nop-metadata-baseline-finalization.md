# 2026-07-22-0900-1 nop-metadata Baseline Finalization

> Plan Status: completed
> Last Reviewed: 2026-07-22
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（S1/G2 状态过期）、`01-architecture-baseline.md` §八（待定问题剩余 1 项）、2026-07-21-1200-1/2 deferred 项
> Related: `302-enterprise-semantic-layer-phase1.md`（S1, completed）、`2026-07-21-1000-1-nop-metadata-taglabel-governance-workflow.md`（G2, completed）

## Purpose

把 nop-metadata 从「持续开发」过渡到「基线稳定」状态。同步 roadmap 和架构基线中的过期状态，正式裁定所有剩余待定问题和 deferred 项，让下一阶段工作从一份准确的 baseline 开始。

## Current Baseline

- roadmap `nop-metadata-roadmap.md` 中的 S1 和 G2 状态过期：S1 显示 `planned (plan 302)`，但 plan 302 已于 2026-07-20 完成；G2 显示 `todo`，但 plan 2026-07-21-1000-1 已于 2026-07-21 完成（G2-1/G2-2/G2-3 全部落地）
- `01-architecture-baseline.md` §八「待定问题」共 7 项，6 项已裁定关闭，剩余 1 项「通用 Domain 的来源」当前仅为「现状声明（2026-07-17）」，未完成正式裁定
- 2026-07-21-1200-2（code quality plan）有 9 项 deferred，全部归类为 `optimization candidate` / `watch-only residual`，`Successor Required: no`，但未在 architecture baseline 或 roadmap 中正式收口
- 2026-07-21-1200-1（P1 runtime defects plan）有多个 deferred 项（Micro ORM fixes, ORM column ordering, ErrorCode file splitting），均未在 architecture baseline §八 中登记
- `00-vision.md` 和 `07-ai-integration.md` 仍为 draft 状态，但所有 roadmap Phase 1-4 已实现完成

## Goals

- roadmap 中 S1、G2 状态更新为 `done`，与 live plan 状态一致
- §八「通用 Domain 的来源」正式裁定：确认现状满足需求，标记为 `adjudicated as residual-risk-only / watch-only`
- 所有跨计划 deferred 项在 architecture baseline 中显式收口登记
- `00-vision.md` 更新为 `final` 状态（所有 4 Phase 已实现）
- `01-architecture-baseline.md` §八 精简为仅保留非 blocking 的 watch-only 项

## Non-Goals

- 不修改任何已完成 plan 的状态或文本（仅 roadmap 和 architecture baseline 为代表当前 baseline 的文档）
- 不触发 codegen 或编译
- 不引入新功能
- 不重新裁定已关闭的 deferred 项

## Scope

### In Scope

- roadmap S1: `planned` → `done`
- roadmap G2: `todo` → `done`
- roadmap `## Work Item Status` header 确认存在且为单一状态块
- §八「通用 Domain 的来源」正式裁定：当前 MetaDomain 导入时从 IOrmModel domain 填充已覆盖用例；维护方式为「从 ORM IOrmModel 导入时自动填充，不单独维护来源」——判定为 `watch-only residual`
- 从已完成计划中搜集所有 deferred 项（09-02 `NopMetadataException` 使用推广、09-06 ErrorCode 变量名一致性等），在 architecture baseline §八 追加 follow-up 登记
- `00-vision.md` 状态从 `draft` 改为表示「已实现」的 text（保留 draft 标记，添加已实现注解）
- `07-ai-integration.md` 状态——评估是否列为下一阶段工作项（roadmap 外），或标记为 superseded

### Out Of Scope

- 任何代码变更
- plan 文件文本修改（历史计划按 Minimum Rules #20 不主动回写）
- 新的功能设计

## Execution Plan

### Phase 1 - Roadmap 状态同步

Status: completed
Targets: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`

- Item Types: `Fix`

- [x] S1 状态从 `planned` 更新为 `done`
- [x] G2 状态从 `todo` 更新为 `done`
- [x] 确认 `## Work Item Status` 表为全文件唯一的动态状态区，无散落过期状态
- [x] 更新 roadmap `Last Updated` 日期
- [x] roadmap `Current Baseline` 追加说明：所有 roadmap Phase（P1-P4, P1+, S1-S3, G1-G3, SR, Opt 系列）均已实现
- [x] 更新 roadmap Dependency Graph 中 S1 和 G2 节点颜色从黄色(N) / 紫色(G) 改为绿色（显示为已实现）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap 中 S1 显示 `done`（非 `planned`）
- [x] roadmap 中 G2 显示 `done`（非 `todo`）
- [x] `grep -c "status:.*planned\|status:.*todo" ai-dev/design/nop-metadata/nop-metadata-roadmap.md` 返回 0——搜索范围仅限 `## Work Item Status` 段落的 plan-level 状态标记（如 `: todo`、`: planned`）。非 plan 状态标记（如设计文档引用文字中包含 `'todo'`）不算。
- [x] `nop-metadata-roadmap.md` Dependency Graph 中 S1 和 G2 的 style 行从 `fill:#ffd`(S1)/`#fdf`(G2) 改为 `fill:#dfd,stroke:#3a3`（与 P1-SR 一致的绿色已实现样式）
- [x] owner doc 已更新：`nop-metadata-roadmap.md`（本 plan 为 owner doc 更新）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - §八 待定问题裁定 + Deferred 收口

Status: completed
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §八

- Item Types: `Fix`, `Decision`

- [x] 正式裁定「通用 Domain 的来源」：添加裁定文本「MetaDomain 的来源为 ORM IOrmModel 导入时自动填充（OrmModelImporter 已实现），不在导入路径外单独维护来源。运行时从 IOrmModel.domainList 填充，不引入新的同步机制。裁定日期：2026-07-22。」
- [x] 将 deferred 项从跨计划统一收集入口写入 §八 follow-up 段：列出所有 `optimization candidate` / `watch-only residual` 项并标注来源 plan
- [x] 标记 `00-vision.md` 为 `final` 状态（全部 4 Phase 已实现，附加实现注解）
- [x] 更新 `01-architecture-baseline.md` header 状态为 `stable`（当前阶段开发完成）

Exit Criteria:

- [x] §八「通用 Domain 的来源」待定问题标记为 `adjudicated`（非纯现状声明）
- [x] architecture baseline §八 中所有待定问题均已标记关闭或收口
- [x] `00-vision.md` 状态更新并添加实现注解
- [x] owner doc 已更新：`01-architecture-baseline.md`（§八 裁定+收口）、`00-vision.md`（状态→final）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] roadmap 所有条目状态与 live code/plan 一致
- [x] §八 无未裁定的待定问题
- [x] 所有从已完成 plan 收集的 deferred 项已归入 architecture baseline 的公开收口记录
- [x] `00-vision.md` 状态已反映当前实现完成度
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] No code changes required — 本 plan 不触发编译/测试

## Deferred But Adjudicated

### 通用 Domain 的独立维护机制

- Classification: `watch-only residual`
- Why Not Blocking Closure: 当前从 ORM IOrmModel 导入时自动填充 MetaDomain 已覆盖所有用例。如未来需要独立维护（e.g. 外部数据源引入非 Nop ORM 的 Domain），需引入新的同步机制或手动录入入口。当前无此需求。
- Successor Required: no

## Non-Blocking Follow-ups

- `NopMetadataException` tier-2 使用推广（09-02, P3）
- ErrorCode 变量名与字符串值一致性治理（09-06, P3）
- `ai-dev/design/nop-metadata/07-ai-integration.md` 文档评估：列为下一阶段 roadmap 外工作项的候选入口

## Closure

Status Note: Phase 1 and Phase 2 all items completed. Documentation-only plan — no code changes. Independent closure audit verified all exit criteria against live repo.
Completed: 2026-07-22

Closure Audit Evidence:

- Reviewer / Agent: independent closure auditor (mission-driver closure audit subagent)
- Audit Session: 2026-07-22-closure-audit-1
- Evidence:
  - **Phase 1 Exit Criteria**:
    - PASS: roadmap S1 shows `done` at `nop-metadata-roadmap.md:36`
    - PASS: roadmap G2 shows `done` at `nop-metadata-roadmap.md:40`
    - PASS: `grep -c "status:.*planned\|status:.*todo" nop-metadata-roadmap.md` returns 0 (no plan-level stale status in Work Item Status section)
    - PASS: S1 dependency graph style = `fill:#dfd,stroke:#3a3` (green, done) at `nop-metadata-roadmap.md:299`
    - PASS: G2 dependency graph style = `fill:#dfd,stroke:#3a3` (green, done) at `nop-metadata-roadmap.md:303`
    - PASS: owner doc `nop-metadata-roadmap.md` updated (Last Updated, Current Baseline, Dependency Graph)
    - PASS: `ai-dev/logs/2026/07-22.md` contains plan execution entry
  - **Phase 2 Exit Criteria**:
    - PASS: §八「通用 Domain 的来源」adjudicated as `adjudicated as residual-risk-only / watch-only` at `01-architecture-baseline.md:1537`
    - PASS: §八 all pending issues closed or collected in follow-up table (`01-architecture-baseline.md:1544-1555`)
    - PASS: `00-vision.md` Status: `final` with implementation annotation (`00-vision.md:3-4`)
    - PASS: `01-architecture-baseline.md` Status: `stable` (`01-architecture-baseline.md:3`)
    - PASS: owner docs updated (`00-vision.md`, `01-architecture-baseline.md`)
    - PASS: `ai-dev/logs/2026/07-22.md` contains plan execution entry for Phase 2
  - **Closure Gates**:
    - PASS: roadmap all items consistent with live code/plan — Work Item Status all `done`, no stale planned/todo
    - PASS: §八 no unadjudicated pending issues
    - PASS: deferred items collected in §八 follow-up table
    - PASS: `00-vision.md` status reflects completion
    - PASS: closure audit completed and evidence recorded (this section)
    - PASS: No code changes required — documentation-only plan (no compile/test gate)
  - **Anti-Hollow Check**: EXEMPT — documentation-only plan (no code changes). Zero code paths to inspect for hollow implementations or silent no-ops. This exemption is valid because the plan modifies no source, test, or configuration files.
  - **Deferred Item Classification Check**: PASS — all deferred items correctly classified as `watch-only residual` or `optimization candidate`. No in-scope live defect or contract drift demoted to non-blocking.
  - **Five-point consistency**: Plan Status `completed` / Phase 1 Status `completed` / Phase 2 Status `completed` / all Exit Criteria `[x]` / Closure Gates all `[x]` / Log entry present — all agree.

Follow-up:

- no remaining plan-owned work
