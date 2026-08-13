# 1 AI Invariant Loop — 第六门禁族评估与裁定

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Work Item: Cycle 1 / I6 前置 — 探查工具化候选裁定（第六门禁族评估）
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md` §Loop Design / §I6；I2 plan（`2026-08-12-1120-3`）Non-Blocking Follow-ups「探查工具化候选：若兄弟路径探查发现高频模式，建议在 I6 收口时评估沉淀为第六门禁族（Cycle 2 派生输入）」；I5 plan（`2026-08-12-1411-3`）遗留观察项「探查工具化候选（兄弟路径高频模式 → 第六门禁族评估，I6 裁定）」
> Related: `ai-dev/plans/2026-08-12-1700-2-ai-invariant-i6-loop-closure.md`（I6 循环收口，消费本 plan 裁定）

## Purpose

对 I2/I5 显式登记的「探查工具化候选」完成评估与裁定：兄弟路径探查 / 类别清扫是否构成值得沉淀的高频失败模式，是否应沉淀为第六个不变式族（INV-6）与对应门禁。裁定结果（tool-ize 或 watch-only residual）作为 I6 稳态判定的「有新族 / 零新族」分支输入（roadmap §Dependency Graph：`I6 -- 有新族 --> N1[Cycle 2 / I1 新不变式]` / `I6 -- 零新族且零red --> SS[稳态暂停]`）。

## Current Baseline

（以下事实于 2026-08-12 live 核实）

- 现有五族门禁全部落地且零命中（I5 full-green，live 证据见 I5 Closure Evidence）：①②③ = JUnit/ArchUnit（surefire 实测全绿），④⑤ = mjs（`check-ai-tool-executor-boundary.mjs` / `check-fix-commit-diff.mjs`），CI `invariant-gates` job 已接线（GitHub 通道缺失，fallback 登记）。
- **探查工具化候选的登记沿革**：I2 Phase 2 对抗探查（git 时域 diff + 兄弟路径 grep + 接线抽查，方法论见 `red-list-2026-08.md` §3/§4）发现新 finding 5 条（R-2-1/2/3、R-4-1、R-5-1），均为「门禁表外新面」而非兄弟路径高频模式本身；I2 在探查中投入的**人工步骤**（对每族 grep 全类兄弟、对修复面做类别清扫）被登记为工具化候选——I2 Non-Blocking Follow-ups 原文：「若兄弟路径探查发现高频模式，建议在 I6 收口时评估沉淀为第六门禁族（Cycle 2 派生输入）」。
- **候选登记来源区分（重要，避免来源失实）**：I2 显式登记的候选 = **兄弟路径探查**（I2 Non-Blocking Follow-ups 原文）；「类别清扫（修一族穷举一族）」是 **I4 执行纪律**（I4 Closure Gates 记录），**没有独立的工具化登记**。本 plan 将两者作为同一模式合并评估，该 scope 决定须在评估文档中记录在案。
- **Loop Rule 预授权**：mission json「Loop Rule 预授权下轮 I1 自动派生（PD-n 先例链）」——若裁定 tool-ize，Cycle 2 / I1 实现计划自动派生，本 plan 只做评估与裁定。
- 已知 pre-existing 基线（不影响本裁定）：scan-hollow 2 条 high finding（`PlanReplanner.java:272` / `NoOpProviderFailoverQueue.java:34`）；doc-links 20 条 pre-existing 错误；`nop-auth-service TestChannelScanBindLoginE2E` VarCollector NPE 已于 2026-08-12 修复（`LoginApiBizModel.buildLoginResult` null 容忍 + E2E 回归防护，见 daily log），不再视为活跃 flake。
- 授权边界：本 plan 纯评估 + 裁定，零产品代码改动；公共 API 变更不适用（无 API 变更）。

## Goals

- 完成探查工具化候选的评估：候选定义、失败模式证据盘点（历史教训 + 本次 Cycle 1 实际数据）、检测方法可行性与成本评估。
- 产出**明确裁定**（二选一，不留模糊态）：`tool-ize`（沉淀 INV-6 + 门禁形态设计，派生 Cycle 2 I1）或 `watch-only residual`（记录理由 + 复触发条件）。
- 裁定落盘：评估结论写入 `ai-dev/audits/nop-ai-invariants/`（评估文档 + catalog §2 候选条登记），供 I6 稳态判定消费。

## Non-Goals

- **不实现第六门禁族**：若裁定 tool-ize，实现属 Cycle 2 / I1（Loop Rule 自动派生），本 plan 只交付裁定与门禁形态规格（检测方法 / 触发范围 / 判定标准）。
- 不改现有五族门禁代码（①-⑤ 保持 I4 终态零变更）。
- 不执行新一轮全量审计（I2 已覆盖 Cycle 1 范围）。
- **不收口 I6**：稳态判定 / 复触发条件登记 / closure audit 属 `2026-08-12-1700-2` plan。

## Scope

### In Scope

- 候选定义与证据盘点（评估文档）。
- 检测方法设计与可行性评估（含成本/收益、误报面、与现有门禁①⑤的边界）。
- 裁定 + 落盘（catalog / 评估文档 / 复触发条件输入）。

### Out Of Scope

- 第六门禁实现（Cycle 2 / I1）。
- 五族门禁修改。
- I6 收口动作（统计 / 稳态判定 / 复触发登记 / closure audit）。

## Execution Plan

### Phase 1 - 候选评估与证据盘点

Status: completed
Targets: `ai-dev/audits/nop-ai-invariants/sixth-gate-family-evaluation.md`（新建评估文档）、`ai-dev/audits/nop-ai-invariants/red-list-2026-08.md`（只读引用）、`ai-dev/lessons/`（历史教训引用）、`ai-dev/plans/2026-08-12-1411-2-ai-invariant-i4-fix-execution.md`（只读引用，类别清扫执行记录 + Closure Gates L196）、`ai-dev/plans/2026-08-12-1120-3-ai-invariant-i2-gate-driven-audit.md`（只读引用，兄弟路径探查执行记录）

- Item Types: `Proof | Decision`

- [x] `Proof` **候选定义**：明确「探查工具化候选」指什么——将 I2 人工执行的「兄弟路径探查」与 I4 的「类别清扫」纪律自动化（对某族任一实例被新增/修改时，强制检查该族全部兄弟实例），而非门禁覆盖新实例声明（已有①-④）；并记录「I2 登记 + I4 纪律合并评估」的 scope 决定。
- [x] `Proof` **失败模式证据盘点**：从历史证据（Lesson 05 虚假关闭 / Lesson 08 接线教训 / 6 deep audit 兄弟实例族：secure-default 6 兄弟、timeout 5 兄弟、清理 3 兄弟）与 Cycle 1 实际数据（I2 兄弟路径探查产出、I4 类别清扫执行记录）评估「同一族实例修改后兄弟未重审」是否构成**高频**失败模式。**Cycle 1 读数必须如实**：I2 探查 5 条 finding 中 R-2-1/R-2-2/R-4-1 为门禁表外**新面**、R-5-1 为门禁精度、**R-2-3（MemberFanOutDispatcher / TeamTaskFlowOrchestrator，catalog §3.2 排除理由被 live 证据推翻）是兄弟探查在既有面上发现的漏网实例（误排除），其发现依赖人工清扫纪律——这是候选前提的 1 起正证而非反证**；I4 类别清扫执行记录（Closure Gates L196 断言行 + Phase 6 执行记录「逐族复核无遗漏、无新缺口」）核对是否发现其他漏网实例。证据不足时如实记录「证据不足」而非臆断。
- [x] `Decision` **检测方法设计（候选规格）**：设计 INV-6 的候选门禁形态（如：对 `fix(nop-ai)` commit 触碰某族任一实例时，静态扫描要求该族全表实例重审声明 / 或作为审计流程门禁而非 commit 门禁），并评估可行性：误报面、与门禁⑤（fix-commit diff）的边界、CI 形态、维护成本。**Anti-Hollow 维度（必评）**：所选机制必须论证机械可验证性（行为式断言或不可空转的判定标准），显式对照 Lesson 08「声明 ≠ 接线」——声明式重审门禁不得成为空转门禁。
- [x] `Proof` 与现有门禁边界核对：确认 INV-6 候选不重复门禁①-④（表完备性）+ 门禁⑤（fix-commit 实质 diff）的拦截语义（表完备性 vs 实例修改联动），无 overlap 或记录 overlap 裁定。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 评估文档存在且含候选定义、证据盘点（含历史 + Cycle 1 数据）、检测方法候选规格、边界核对
- [x] 证据盘点结论明确（高频 / 非高频 / 证据不足——三态之一，不得用模糊词）
- [x] No owner-doc update required（ai-dev 审计产物，不触 docs-for-ai）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 裁定与落盘

Status: completed
Targets: `ai-dev/audits/nop-ai-invariants/sixth-gate-family-evaluation.md`（裁定段）、`ai-dev/audits/nop-ai-invariants/invariant-catalog.md`（**§2 登记候选条，标注 `INV-6 候选`，不改 §2 标题计数**）、roadmap §Work Item 表下新增一行注记（非 I6 行本身，指明评估 plan 路径与裁定状态）

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` 裁定二选一：**tool-ize** → 门禁形态规格作为 Cycle 2 / I1 派生输入（含 INV-6 陈述、覆盖失败族、检测方法、预期目标集）；**watch-only residual** → 记录 Why Not Blocking Closure（证据不足或成本 > 收益）+ **可判定的复触发条件**（必须是可观测事件或周期锚点，如「周期复探时复核」/「出现第 2 起同族漏网实例时复核」，不得使用「时机合适时再评估」类模糊时间词）。
- [x] `Fix` 裁定落盘：评估文档写「裁定 + 理由 + 复触发条件」；catalog §2 登记候选条（标注 `INV-6 候选`）；roadmap Work Item 表下注记行指向本 plan。
- [x] `Proof` 裁定与证据一致性核对：tool-ize 裁定必须由 Phase 1 证据支撑（高频模式成立或防回退价值明确）；watch-only 裁定必须写明证据不足/成本理由，不得以「以后再说」充当裁定。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定已落盘（评估文档 + catalog §2 候选条 + roadmap 注记三处一致）
- [x] tool-ize 裁定附 INV-6 规格（陈述/覆盖族/检测方法/目标集）；watch-only 裁定附理由 + 复触发条件——两者必居其一
- [x] No new test required: 本 plan 纯评估 + 裁定，零代码改动（guide rule 25 豁免注记）
- [x] No owner-doc update required（ai-dev 审计产物）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 评估文档 + 裁定落盘完成（I6 稳态判定的「有新族/零新族」输入就绪）
- [x] 裁定非模糊（tool-ize 或 watch-only residual 二态，含理由与复触发条件）
- [x] 无 in-scope live defect 被降级（本 plan 无代码面，不存在可降级项——核查评估过程未发现被静默忽略的确认缺陷）
- [x] 受影响 owner docs 无需更新（`No owner-doc update required`，ai-dev 产物）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证裁定结论有评估文档证据支撑（非空裁定）；tool-ize 时 INV-6 规格可被 Cycle 2 I1 直接消费（含目标集/检测方法/机械可验证性论证）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 时执行）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 实际运行并记录输出——预期退出码 1 = 20 条 pre-existing 错误零新增（对照 I1-I5 基线；本 plan 新增评估文档/catalog/roadmap 注记不得引入新错误，新文档目标引用为自消 warning 允许）
- [x] checkstyle / 代码规范检查：N/A（零代码改动）

## Deferred But Adjudicated

### 第六门禁族的实现（若裁定 tool-ize）

- Classification: `out-of-scope improvement`（本 plan 只评估裁定；实现 = Cycle 2 / I1，Loop Rule 预授权自动派生）
- Why Not Blocking Closure: 本 plan 交付物 = 裁定 + 规格；实现不影响 Cycle 1 稳态判定输入（有新族 → 派生 Cycle 2，零新族 → 稳态暂停，两者都是 I6 的合法收口结果）
- Successor Required: `yes`（若 tool-ize）— Cycle 2 / I1 派生计划
- Successor Path: Loop Rule 预授权派生（PD-n 先例链）

## Non-Blocking Follow-ups

- 门禁运行耗时优化（ArchUnit 扫描范围收窄 + mjs ast-grep 合并评估）——I1/I4 遗留 optimization candidate，I6 收口时按「首跑 >30s 再评估」判据复核登记，不在本 plan 范围。

## Closure

Status Note: 评估 + 裁定完成——裁定 = `watch-only residual`（证据盘点结论 = 非高频；Why Not Blocking Closure = 机械可验证核心已由门禁①-④ 全表复查覆盖 + 行为级联动重审不可机械验证（Anti-Hollow / Lesson 08）+ 成本 > 收益；复触发条件 3 条可判定，交 I6 复触发登记）。落盘三处一致（评估文档 §4 / catalog §2 `INV-6 候选` 条 / roadmap 注记行）。I6 稳态判定消费本裁定 → 「零新族且零red」→ 稳态暂停 待复触发。本 plan 零产品代码改动（纯 ai-dev 审计产物）。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session，task `ses_00aa7577fffedsAAv65FbjaJJl`，review-only 零文件改动）
- Evidence:
  - **Phase 1 Exit Criteria 逐条 PASS**：评估文档存在且 §1 候选定义 + §2 证据盘点（历史 + Cycle 1 如实读数：R-2-3 = 唯一正证、R-2-1/2/4 新面、R-5-1 门禁精度，与 red-list §3 / I4 Phase 6「逐族复核无遗漏、无新缺口」交叉核对一致）+ §3 检测方法候选规格（含 Anti-Hollow §3.3）+ §5 边界核对；§2.3 结论「非高频」三态明确无模糊词；`git status --short` 零产品代码/零 docs-for-ai 变更；daily log Phase 1 条目在位（08-12.md:13）。
  - **Phase 2 Exit Criteria 逐条 PASS**：裁定三处一致（评估文档 §4.1-4.3 / catalog §2 `INV-6 候选` 条标注不计入「5 条」计数且 §2 标题未改 / roadmap Work Item 表下注记行 ✅，I6 行保持 `todo` 未动）；watch-only 附理由（非高频 + 成本>收益 + 机械可验证性）+ 3 条可判定复触发条件（周期复探 ≥2 起漏网 / 事件锚点第 1 起行为级回归 / 结构性锚点 Cycle 2 新族首轮 ≥2 起——无模糊时间词）；No new test required + No owner-doc update required 注记在位；daily log Phase 2 条目在位（08-12.md:1）。
  - **Closure Gates 逐条 PASS**：C1 裁定二态明确；C2 零代码面 + R-2-3/R-5-1 已由 I4 修复（git log `6e74232bd`/`e9b2bee76` 等）；C3 Anti-Hollow = 裁定可追溯至 §2 证据 + §3.3 机械不可验证性论证（非空裁定）；C4 `check-plan-checklist.mjs --strict` 退出码 0（Passed 1 / Failed 0）；C5 `check-doc-links.mjs --strict` 退出码 1 = 20 个 pre-existing error + 3 个 warning 零新增（全部位于既有文件：INDEX.md:217 / 各模块 roadmap 旧引用 / 0615-3 plan 3 warning / audit-prompt:9；本 plan 新增四文件零断链）；C6 checkstyle N/A（零代码改动）；C7/C8 Phase 1/2 全 [x] + Status completed + 无残留 [ ]。
  - **测试基线**：`./mvnw test -pl nop-ai -am -T 1C` 共 4 次运行——1 次瞬时失败（17:24，无 surefire failure 报告留存，未复现）+ 3 次连续 BUILD SUCCESS（17:28 / 17:34 03:59 / 17:39 04:54，`_tmp/sixth-gate-eval-test-run.log` 留存末次）；审计时全树 1345 个 surefire XML 零 failures/errors。
  - **Anti-Hollow 结论**：裁定由 live 可验证证据支撑（非空心散文）——非高频结论可追溯至 Cycle 1 正证计数（恰 1 起 R-2-3）+ 根因 owner-doc drift 的机械承接（补表 + 门禁②表完备性 + live `orTimeout` 于 `MemberFanOutDispatcher.java:326` / `TeamTaskFlowOrchestrator.java:618`）；声明式重审门禁必然空转是机制级论证（Lesson 08 同构），三处落盘一致复现。
  - **审计发现**：零 Blocker 零 Major；2 Minor（`_tmp` 日志仅留存末次 maven 运行，瞬时失败不可从产物直接复验——终态证据充分；复触发条件 (1) 周期锚定 I6 登记——可判定，非模糊词）。verdict **APPROVED**。
  - **Deferred 分类检查**：Deferred = 第六门禁族实现（out-of-scope improvement，tool-ize 未触发，无 successor）；Non-Blocking Follow-ups = 门禁耗时优化（I1/I4 遗留）；无 in-scope live defect 被降级。

Follow-up:

- 裁定结果（watch-only + 3 条可判定复触发条件）交 I6 循环收口（`2026-08-12-1700-2`）稳态判定消费，并显式进入其复触发登记——候选复核机制不在 I6 关闭后断裂。
- no remaining plan-owned work。

## Draft Review Records

- Round 1（fresh session `ses_00ad186faffe7kjNnNy4rUob9w`）：1 Major + 7 Minor，全部修订（watch-only 复触发下游交接、catalog 登记位置钉死 §2、roadmap 注记落点、Phase 2 Item Types 补 Proof、I2/I4 来源叙事区分、Targets 补 I4/I2 引用、复触发可判定性约束）。
- Round 2（fresh session `ses_00aca48eaffeJq1nli1qOs69iM`）：1 Major + 5 Minor，全部修订（R-2-3 证据叙事改为如实盘点——漏网兄弟正证而非反证、Goals 残留「或 §5」、检测方法补 Anti-Hollow 维度、doc-links 实跑 gate、INV-1-⑤ 记号修正）。
- Round 3（fresh session `ses_00ac5ad99ffeaB44V5F9U21CIo`）：verdict 可直接执行（零 Blocker 零 Major；2 Minor 建议——flake 已修复叙述、I4 引用补 Phase 6 锚点——均已修订）。共识达成 → Plan Status: active。
