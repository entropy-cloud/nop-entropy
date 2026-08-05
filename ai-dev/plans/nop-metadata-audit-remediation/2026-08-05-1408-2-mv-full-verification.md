# MV 全量验证（V.1 全量 build + test / V.2 独立子代理 closure audit / V.3 P0/P1 可追溯性收口）

> Plan Status: active
> Last Reviewed: 2026-08-05
> Draft Review: 2 轮独立子 agent 对抗性审查通过（第 1 轮 0 Blocker + 3 Major + 3 Minor 全部修复；第 2 轮复审 3 Major + 3 Minor 修复全部 PASS（F5 措辞补全），2 Minor 已按复审建议修复，0 Blocker / 0 Major 残留，裁定可执行）。Session: ses_02f71de0affe3QwJZYemCD1J2W / ses_02f6b6515ffebgj1jOR25Ow8C1。
> Mission: nop-metadata-audit-remediation
> Work Item: MV（V.1 全量 build + test；V.2 独立子代理 closure audit；V.3 所有 P0/P1 finding 可追溯至修复或 deferred）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MV 里程碑 + 里程碑依赖图）、`ai-dev/audits/arm-index-nop-metadata.md`（追踪矩阵载体）、MR4 裁决记录（plan-2026-08-05-1408-1 产出）
> Related: 执行顺序 `{2}` of 3 — **启动门禁（执行时核查，非既成事实）**：MR4 done（roadmap R4.1 → done + plan-2026-08-05-1408-1 completed，不满足则不启动并上报）；本 plan 为**验证计划**（不改产品行为代码，产出 = 验证记录 + 追踪矩阵 + 独立 closure audit 报告），为 MG（G.1-G.3）提供输入。

## Purpose

执行 roadmap MV 里程碑三项收口工作：(a) V.1 全量 build + test（对 MR1-MR4 全部修复后的完整基线做最终验证，确认绿色基线保持）；(b) V.2 独立子代理 closure audit（fresh session 独立审阅，验证**所有 P0/P1 finding 可追溯至修复或 deferred**，追踪矩阵写入 arm-index）；(c) V.3 可追溯性收口（追踪矩阵完整性核对 + roadmap V 行 → done）。MV 是 nop-metadata-audit-remediation 路线图修复闭环的最终验证层，其产出为 MG 知识沉淀提供事实输入。

## Current Baseline

经 2026-08-05 live repo 核对（MR1-MR4 全部收口后，本 plan 启动时须重新实测）：

- roadmap MV 行（V.1/V.2/V.3）状态全部 `todo`；**Deps（按 roadmap 行）：V.1 依赖 MR4 done、V.2 依赖 V.1、V.3 依赖 V.2**——本 plan 启动时核查 MR4 是否真 done（roadmap R4.1 → done + plan-2026-08-05-1408-1 completed）；MG 行（G.1-G.3）全部 `todo`（依赖 MV done）
- 修复闭环现状（MR1-MR3 收口 + MR4 裁决）：roadmap MR1/MR2/MR3 段全部行 done；MR4 段 R4.1 预期 done（**启动门禁实测核查，见 Related/Phase 1**；本 plan 起草时 R4.1 为 todo、plan-2026-08-05-1408-1 为 draft——不满足门禁则不启动）；P0-MA7.1-01 + P1×4 + P2-MA5-401 must-fix + 13 项 P2 in-scope 修复全部 landed；deferred 项终态登记于 roadmap R 行 + arm-index §P2
- 绿色基线记录：MR3 收口实测 **857 tests / 0 failures / 0 errors / 0 skipped**（2026-08-05，`-pl nop-metadata` 无 -am surefire 口径）；M0.4 基线 813/0（2026-08-04）；MR4 若含代码变更以 MR4 收口实测为准
- **追踪矩阵现状**：arm-index-nop-metadata.md 为 P0/P1 追踪唯一事实源（`## P0 发现追踪` 节 + §P1 finding → fixed/deferred 终态 + §P2 裁决终态 + 归属更正记录）；MV V.2/V.3 在此基础上升级为完整追踪矩阵（含 MR4 裁决段）
- **验证基线注意（2026-08-05 实测，须以此为准而非旧注记）**：工作树 clean（仅本批次未跟踪 plan 文件），无需 stash；**pre-existing 失败清单已随 HEAD 82dbd170c 更新**——commit 132b60979 的 xview.xdef 回归（nop-xlang TestXDefParse/TestGenericDslParser + nop-metadata-web NopMetadataWebPagesTest）**已在 82dbd170c 修复**（2026-08-05 14:07 HEAD，全量 `-pl nop-metadata -am test` BUILD SUCCESS 0 failures，见 08-05 日志 §3-11），不再属于 pre-existing；残余 pre-existing = nop-stream-rocksdb TestRocksDBIncrementalRestoreAndBenchmark 性能基准 flaky + nop-stream TestAsyncSnapshotPipeline 超时竞态——验证时按此归因，发现新失败不得套用旧归因
- **可追溯性范围**：四轮历史审计（07-19/07-20×2/07-21/07-23）+ MA1-MA7 全部 finding（P0/P1 级，含 P2 裁决项）→ 修复计划（MR1-MR4）→ 终态（fixed/deferred）的完整链条

## Goals

- V.1：全量 build + test 实测通过（`./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` + `./mvnw test -pl nop-metadata -am -T 1C`），测试计数记录（**0 failures 为硬门禁**：nop-metadata 子树 0 failures 且无未归因失败；pre-existing 清单内失败按归因记录不阻塞；清单外失败 = blocker，见 Phase 1 失败归因项）
- V.2：独立子 agent（fresh session）closure audit 完成——所有 P0/P1 finding 可追溯至修复或 deferred，追踪矩阵写入 arm-index，audit 证据 repo-observable
- V.3：追踪矩阵完整性核对通过（无 untraceable finding、无终态悬置），roadmap V.1/V.2/V.3 → done
- 为 MG（G.1-G.3 lessons/skills/docs 沉淀）提供最终事实输入

## Non-Goals

- 不执行 MG（G.1-G.3，由 plan-2026-08-05-1408-3 承接）
- 不进行新审计（MA1-MA7 已 done；MV 只做追踪核对与验证，不新开审计维度）
- 不修复 MV 验证中发现的回归（记录为 finding + 上报；不在此计划内静默修复，修复由上报后的新计划承载）
- 不修改产品行为代码（本计划为验证计划；发现的问题走上报路径）
- 不处理 P3（roadmap 规则 1）

## Scope

### In Scope

- V.1：全量 build + test（命令见 Goals），测试计数与归因记录
- V.2：独立子代理 closure audit（fresh session）：P0/P1 追踪矩阵 + Anti-Hollow + deferred 分类 + 各修复计划 closure 证据抽查
- V.3：追踪矩阵写入 arm-index + 完整性核对 + roadmap V 行 → done
- 验证中发现问题的上报与记录（不静默修复）

### Out Of Scope

- MG（plan-2026-08-05-1408-3）
- 新审计、P3 处置、产品行为代码变更、平台模板变更

## Execution Plan

### Phase 1 - V.1 全量 build + test

Status: planned
Targets: `nop-metadata`（8 子模块）+ 上游依赖（-am 范围）

- Item Types: `Proof`

- [ ] **启动门禁核查（Proof）**：确认 MR4 done（roadmap R4.1 → done + plan-2026-08-05-1408-1 completed，check-plan-checklist 抽查无未勾选项）；**任一不满足则不启动并上报**——执行结果记录
- [ ] **clean 基线准备（Proof）**：核对工作树状态（2026-08-05 实测 clean，仅未跟踪 plan 文件；如出现新变更则 stash 或记录），确认 HEAD 提交与 Current Baseline 一致（82dbd170c 已含 xview.xdef 修复）——执行结果记录
- [ ] 运行 `./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C`，记录 BUILD SUCCESS/FAIL——执行结果记录
- [ ] 运行 `./mvnw test -pl nop-metadata -am -T 1C`，记录测试计数（surefire 汇总：tests/failures/errors/skipped），与 MR3 收口基线（857/0/0）对比——执行结果记录
- [ ] 失败归因（Proof）：任何失败逐项归因——**按 2026-08-05 更新的 pre-existing 清单（仅 rocksdb 性能 flaky + TestAsyncSnapshotPipeline 超时竞态）**；132b60979 xview.xdef 回归已由 HEAD 82dbd170c 修复，不得再套用旧归因；**新引入失败（非 pre-existing）→ 不得归因放行：记录 finding（finding ID 登记）+ 上报，V.1 不得标 completed，处置完成前不进入 V.2/V.3**（不静默修复，修复走上报后的新计划）——执行结果记录
- [ ] 验证结果写入 roadmap V.1 行 + `ai-dev/logs/` 对应日期条目——执行结果记录

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` BUILD SUCCESS（或失败已逐项归因且记录）
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 0 failures（nop-metadata 子树口径；pre-existing 失败归因记录不阻塞）
- [ ] 测试计数与基线对比记录 repo-observable（roadmap V.1 行 + daily log）
- [ ] `No new test required`: 本 Phase 为验证执行（不改产品行为代码；发现的回归走上报路径）
- [ ] 文档变化：roadmap V.1 行 → done 或记录失败；`No owner-doc update required`（本 Phase 无 docs-for-ai 变更）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - V.2 独立子代理 closure audit

Status: planned
Targets: `ai-dev/audits/arm-index-nop-metadata.md`（追踪矩阵）+ MR1-MR4 计划文件 + roadmap

- Item Types: `Proof`

- [ ] **启动独立子代理（Proof）**：启动 fresh session 子代理（closure-audit-prompt.md），不复用本 plan 执行 session——task ID 记录
- [ ] **P0/P1 追踪矩阵核对（Proof）**：独立子代理逐项核对——arm-index `## P0 发现追踪` + §P1 全部 P0/P1 finding → 对应修复计划（MR1-MR4）→ 终态（fixed 代码位置/测试名 或 deferred 归类 + Why Not Blocking Closure）；每项 PASS/FAIL + 证据（live code path / test name / roadmap 行 / arm-index 行）——核对结果记录
- [ ] **Anti-Hollow 抽查（Proof）**：独立子代理抽查关键修复的运行时调用链连通（如 xwf 审批流 → Processor → reJudge；改名后 bean id 接线；UK 发射断言测试），确认无空壳/静默跳过——抽查结果记录
- [ ] **deferred 分类检查（Proof）**：独立子代理核查全部 deferred 项归类合规（watch-only residual / optimization candidate / out-of-scope improvement），无 in-scope live defect 被降级（对照 Minimum Rule #16）——检查结果记录
- [ ] 追踪矩阵写入 arm-index（`## P0 发现追踪` 节状态列 + §P1 finding 状态列 + MR4 裁决段 + MV audit 段），audit 结论（READY_TO_CLOSE / NOT_READY + 问题清单）记录——执行结果记录

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 独立子代理（fresh session）audit 完成，audit 证据（task ID + 逐项 PASS/FAIL + 证据来源）repo-observable
- [ ] 全部 P0/P1 finding 已核对可追溯性（无 untraceable 项；有则记录问题清单并上报）
- [ ] Anti-Hollow 抽查 + deferred 分类检查完成，结论记录
- [ ] 追踪矩阵已写入 arm-index（V.2 audit 段）
- [ ] `No new test required`: 本 Phase 为独立审计执行（不改产品行为代码）
- [ ] 文档变化：arm-index 更新；`No owner-doc update required`（docs-for-ai 不变，G.3 承接）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - V.3 可追溯性收口 + roadmap V 行 done

Status: planned
Targets: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MV 段）+ `ai-dev/audits/arm-index-nop-metadata.md`

- Item Types: `Decision | Proof`

- [ ] 追踪矩阵完整性复核（Proof）：V.2 产出问题清单（如有）逐项处置（上报 / 记录为 follow-up 或 blocker）——执行结果记录
- [ ] roadmap V.1/V.2/V.3 行 → done（V.2 注明独立子代理 audit session ID；V.3 注明追踪矩阵完整性结论）——执行结果记录
- [ ] MV 收口记录写入 `ai-dev/logs/`（含验证计数、audit 结论、问题处置）——执行结果记录
- [ ] 为 MG 提供输入清单（G.1 失败模式候选 / G.2 维度候选 / G.3 文档缺口候选）——执行结果记录

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] V.2 问题清单（如有）已处置或显式记录为非阻塞
- [ ] roadmap V.1/V.2/V.3 全部 → done，可追溯（audit session ID + 计数 + 结论）
- [ ] MG 输入清单已记录（G.1/G.2/G.3 候选）
- [ ] 文档变化：roadmap + arm-index + daily log 更新；`No owner-doc update required`（docs-for-ai 变更由 G.3 承接）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。
>
> **纯验证计划**：本 plan 不改产品行为代码（验证 + 审计 + 追踪矩阵），构建验证条目以 V.1 实测为准，`./mvnw test` 类门禁即 V.1 本身。

- [ ] V.1 全量 build + test 实测完成（0 failures，或失败已逐项归因记录）
- [ ] V.2 独立子代理 closure audit 完成并记录证据（写入本 plan Closure 段 + arm-index）
- [ ] V.3 追踪矩阵完整性核对通过（所有 P0/P1 finding 可追溯至修复或 deferred，无悬置）
- [ ] 无验证中发现的问题被静默忽略（全部记录 + 处置或上报）
- [ ] roadmap V.1/V.2/V.3 → done
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（本 plan 自身 closure 也须独立——V.2 的 audit session 不可复用为本 plan closure audit，须另开 fresh session；**若复用则视为无独立 closure audit**）
- [ ] **Anti-Hollow Check**：V.2 抽查结论 + 本 plan closure audit 复核（无空壳/静默跳过作为正常实现）
- [ ] `./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` + `./mvnw test -pl nop-metadata -am -T 1C`（即 V.1 实测；作为本 plan 的构建门禁记录）
- [ ] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，历史惯例 "checkstyle N/A"）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（本 plan 修改 arm-index（ai-dev 下），不预期修改 docs-for-ai/；若修改则必跑）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时，与 V.2 Anti-Hollow 抽查互为印证）

## Deferred But Adjudicated

### 验证中发现但判定非阻塞的观察（V.3 处置后填写）

- Classification: `watch-only residual | optimization candidate | out-of-scope improvement`（如有）
- Why Not Blocking Closure: <<填写>>
- Successor Required: `yes | no`
- Successor Path: <<如需要则填写>>

## Non-Blocking Follow-ups

- 验证中发现的 pre-existing 失败（nop-stream-rocksdb 性能 flaky / nop-stream TestAsyncSnapshotPipeline 超时竞态）维持归因记录，处置归对应任务；132b60979 xview.xdef 回归已由 HEAD 82dbd170c 修复，不再列入
- MG（plan-2026-08-05-1408-3）承接 lessons/skills/docs 沉淀

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent（须为 V.2 之外的新 fresh session）>>
- Audit Session: <<session ID>>
- Evidence:
  - V.1 实测结果（命令 + 计数 + 归因）
  - V.2 独立 audit 结论（P0/P1 追踪矩阵 PASS/FAIL + 问题清单处置）
  - V.3 收口记录（roadmap V 行 done + 追踪矩阵位置）
  - Anti-Hollow 检查结果
  - `check-plan-checklist.mjs --strict` 退出码

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work（MG 承接）>>

## Optional Sections

- `## Risks And Rollback`：V.2 独立 audit 若发现 untraceable finding 或 live defect 被降级 → 记录 blocker 并上报（不静默通过）；本 plan 自身 closure audit 必须独立于 V.2 session（防自审）
- `## Outdated Note`：验证计数与基线对比以执行时重新实测为准，不以 MR3 收口记录替代
