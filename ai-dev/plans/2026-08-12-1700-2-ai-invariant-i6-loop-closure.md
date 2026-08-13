# 2 AI Invariant Loop I6 — 循环收口

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Work Item: Cycle 1 / I6. 循环收口
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md` §I6 行（todo）与 §Dependency Graph；I5 plan（`2026-08-12-1411-3`）Closure/Follow-up「I6 收口计划（待创建）：统计 + 稳态判定 + 复触发条件登记 + closure 独立 fresh session」；I3 plan（`2026-08-12-1411-1`）Closure/Follow-up「mission json 文本同步留待 I6 收口时处理」
> Related: `ai-dev/plans/2026-08-12-1700-1-ai-invariant-sixth-gate-evaluation.md`（第六门禁族评估，本 plan 稳态判定消费其裁定）；I0-I5 plans（`2026-08-12-1120-*` / `2026-08-12-1411-*`）

## Purpose

Cycle 1 循环收口：统计（五族门禁 / finding / 修复 / 验证数据）、稳态判定（零新族且零 red → 稳态暂停；有新族 → Cycle 2 派生登记）、复触发条件登记（CI 变红 / 新增 Default* 类 / 周期复探周期裁定 / CI fallback 补验 / watch 项触发条件）、mission json 授权文本同步；roadmap §I6 行 `todo`→`done`；closure 独立 fresh session。

## Current Baseline

（以下事实均于 2026-08-12 live 核实；开工前须确认前置 plan 已完成）

- **前置**：第六门禁族评估（`2026-08-12-1700-1`）——**live 状态为 `draft`（本计划起草时尚未进入执行）**：completed → 消费其裁定；未 completed → 标「评估 pending」回填。I0-I5 全部 `done`（live 证据：各 plan Plan Status: completed + roadmap 行翻转 + daily log `08-12.md`）。
- **Cycle 1 数据基线（live）**：I0 不变式目录 5 条（INV-1~5）；目标集表 4 张（Default* 33 / 编排入口 17 / ToolExecutor 30 / entry-point 8）；red-list 44 条（门禁 39 + 探查 5）零悬挂；I3 裁决 44/44（37 fix-I4 + watch + not-applicable）；I4 修复 5 commits（裁决表 37 条全落地）；I5 full-green（test + pnpm check:ai-invariants + clean install 全绿，五族门禁零命中，known-gaps 仅 5 条 N/A 零 drift）。
- **复触发条件基线（I5 Phase 4 已登记，供本 plan 定稿）**：(1) CI 变红——`invariant-gates` job fail（门禁④⑤）/ mvn test 门禁①②③ surefire fail；(2) 结构变更——新增/重命名 Default* 类 / 编排入口 / ToolExecutor / entry-point（不入表即 red）；(3) 周期复探——**周期由本 plan 裁定**；(4) CI fallback 补验——GitHub 通道首次可用时补验 `invariant-gates` 实跑（非空转判据已登记：since-mode 日志「扫描到 fix(nop-ai) commit: N」且 N>0）。
- **watch 项触发条件（I3 follow-up 登记，供本 plan 并入复触发清单）**：AskOracleExecutor oracle client 落地、R-4-1 引入消费 dnsResolver 的 HTTP client、R-2-2 plan/runtime 接线。
- **mission json 授权文本同步（I3 follow-up）**：I3 plan 裁定「P2 自动修复授权为 plan 级裁定」（mission json 原文仅 P0/P1 预授权）——文本同步留待 I6 收口时处理（non-blocking）。
- 遗留观察项（non-blocking）：scan-hollow 2 条 pre-existing high finding（`PlanReplanner.java:272` / `NoOpProviderFailoverQueue.java:34`）；doc-links 20 条 pre-existing；门禁耗时优化 candidate（I1/I4 遗留，>30s 判据）。
- 已知 flake：`nop-auth-service TestChannelScanBindLoginE2E` 的 VarCollector NPE **已于 2026-08-12 修复**（`LoginApiBizModel.buildLoginResult` null 容忍 + E2E 回归防护，见 daily log）——分诊规则仅作残余防护，不再视为活跃 flake。

## Goals

- Cycle 1 统计落盘（五族门禁 / finding / 修复 / 验证四组数据，来源可追溯）。
- 稳态判定完成：消费第六门禁族评估裁定 → 明确「稳态暂停 待复触发」或「Cycle 2 派生登记」。
- 复触发条件登记完备（4 类基线 + watch 项触发条件 + 周期裁定），mission json 授权文本同步。
- roadmap §I6 行 `todo`→`done`；closure 独立 fresh session 完成并记录证据。

## Non-Goals

- **不修复任何代码**：本 plan 是收口计划，不修代码（门禁红/缺口 → 记录并路由复触发，不在本 plan 修复）。
- **不实现第六门禁族**：评估 plan 裁定 tool-ize 时，派生登记到 Cycle 2，不在此实现。
- 不重新跑 I2 全量审计 / 不裁决新 finding（新发现记录为复触发观察项）。
- 不改五族门禁代码。

## Scope

### In Scope

- Cycle 1 统计与 full-green 记录复核。
- 稳态判定（消费第六门禁族评估裁定）。
- 复触发条件登记（含周期复探周期裁定、CI fallback 补验、watch 项触发条件）。
- mission json 授权文本同步 + roadmap §I6 收口。
- closure 独立 fresh session。

### Out Of Scope

- 代码修复（复触发路径处理）、第六门禁族实现、新审计维度、历史计划回写。

## Execution Plan

### Phase 1 - Cycle 1 统计

Status: completed
Targets: `ai-dev/logs/2026/08-12.md`、`ai-dev/backlog/nop-ai-invariant-loop-roadmap.md`、`ai-dev/audits/nop-ai-invariants/`（只读引用）

- Item Types: `Proof`

- [x] `Proof` **前置校验（fail-fast）**：I0-I5 plan 全部 completed（`grep "Plan Status: completed"` 六个文件：1120-1/2/3 + 1411-1/2/3）；第六门禁族评估 plan 状态读取——completed → 消费其裁定；未 completed（含 draft/active）→ 标「评估 pending」，**Phase 2 稳态判定条目不勾、plan 保持 `in progress`，待其 completed 后重跑本校验**；**被 cancelled/deferred → 稳态判定以 pending 记录 + 第六门禁族候选路由复触发（Cycle 2 裁定），不阻塞其余收口**。roadmap §I0-§I5 全 `done`。
- [x] `Proof` 统计落盘（来源可追溯）：五族门禁（INV-1~5 + 落地形态：3 JUnit/ArchUnit + 2 mjs + CI job）、目标集表（33/17/30/8）、red-list 44 条 → 裁决 44/44 → 修复 37 决策 **5 commits（计数以 I4 closure evidence 为准，不自行从 git 复推——subject-only 全量复推会得到含 I5 closure commit 的更大计数）**、I5 full-green 结果——写入 daily log 收口段与 roadmap §I6 行。
- [x] `Proof` 数据一致性核对：统计数字与 live 证据（surefire 报告 / mjs 输出 / plan closure evidence / daily log）逐项一致，零虚构数字。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 统计四组数据全部落盘且与 live 证据一致（核对记录）
- [x] 前置校验记录（各 plan 状态 + 第六门禁族评估裁定状态）
- [x] No owner-doc update required（ai-dev 产物，不触 docs-for-ai）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 稳态判定 + 复触发条件登记

Status: completed
Targets: `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md`、`ai-dev/audits/nop-ai-invariants/invariant-catalog.md`（**复触发登记唯一落点 = catalog 新建 §6 复触发条件登记段，只增不改既有 §1-§5 规则；roadmap §Loop Rule 只留指针**）、`missions/nop-ai-invariant-loop.json`

- Item Types: `Decision | Proof | Fix`

- [x] `Decision` **稳态判定**：消费第六门禁族评估裁定——tool-ize → 「有新族」分支（roadmap 注记 Cycle 2 派生输入，N1 边激活）；watch-only/证据不足 → 「零新族且零red」→ **稳态暂停 待复触发**；**评估 plan 被 cancelled/deferred → 稳态判定结论 = pending 记录 + 第六门禁族候选路由复触发（Cycle 2 裁定），本条目以该记录为勾选依据（不阻塞其余收口）**。判定结果写入 roadmap §I6 行。
- [x] `Decision` **周期复探周期裁定**：明确一个可执行的复探周期（如 30/60/90 天，附理由；基于扫描成本 = mjs 秒级 + 门禁随 mvn test，倾向短周期），写入复触发条件登记。
- [x] `Fix` 复触发条件登记定稿（catalog 新建 §6 唯一落点）：4 类基线（CI 变红 / 结构变更 / 周期复探含周期 / CI fallback 补验含非空转判据）+ watch 项触发条件（AskOracleExecutor client / dnsResolver client / plan/runtime 接线）+ **第六门禁族候选复评估触发条件（消费 `2026-08-12-1700-1` 裁定；watch-only 时其可判定复触发条件必须入登记，不得断裂）**。
- [x] `Fix` mission json 授权文本同步：I3 裁定「P2 自动修复授权为 plan 级裁定」反映到 `missions/nop-ai-invariant-loop.json` 授权句（不改变授权实质，仅文本与已裁定事实一致）；roadmap §Cross-Cutting 授权句同步一致。**说明：mission json 由 mission-driver 引擎 JSON.parse 读取，description 仅用于显示不注入 agent prompt——本改动是纯文档一致性动作。**
- [x] `Proof` mission json 变更验证：`node ai-dev/tools/mission-check.mjs missions/nop-ai-invariant-loop.json .` 退出码 0（若该工具不存在则用 `python3 -m json.tool missions/nop-ai-invariant-loop.json` 语法校验）+ `git diff` 确认仅授权句一处变更。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 稳态判定结论明确（稳态暂停 / Cycle 2 派生登记 / 评估 pending-cancelled 记录，三态之一）且与第六门禁族评估 plan 状态一致（评估 plan 未 completed 且非 cancelled/deferred 时该条不勾）
- [x] 复探周期已裁定并成文（附理由）
- [x] 复触发条件 4 类 + watch 项 + 第六门禁族候选复评估触发条件全部登记（catalog §6），无遗留悬空
- [x] mission json 授权文本与 I3 裁定一致（diff 记录 + JSON 合法性校验 exit 0）；roadmap §Cross-Cutting 授权句已同步
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 实际运行并记录输出——预期退出码 1 = 20 条 pre-existing 错误零新增（对照 I1-I5 基线同口径；AGENTS.md 要求修改 ai-dev 文件后必须运行该检查，工具无 baseline 机制，退出码 1 即为基线态）
- [x] No new test required: 本 plan 无产品代码改动（mission json 为配置文本，非代码）——`No new test required: 纯配置文本同步`（guide rule 25 豁免注记）
- [x] No owner-doc update required（roadmap/mission json 均为 ai-dev/ 配置产物）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 收口 + closure 独立 fresh session

Status: completed
Targets: `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md`（§I6 行）、`ai-dev/logs/2026/08-12.md`、本 plan 文件

- Item Types: `Proof`

- [x] `Proof` roadmap §I6 行 `todo`→`done`（依赖 Phase 1/2 全部完成 + 统计/判定/登记落盘）。
- [x] `Proof` 遗留观察项终态标注（**涵盖 I5 Phase 4 全部注册项，另含 I1/I4 遗留门禁耗时优化 candidate**）：scan-hollow 2 条 pre-existing、doc-links 20 条、探查工具化候选（第六门禁族评估，由 Phase 2 稳态判定兜底）、CI fallback 复触发项（由 Phase 2 复触发登记兜底）、门禁耗时优化 candidate——全部显式 non-blocking + 后继归属（复触发/Cycle 2）。
- [x] `Proof` **closure 独立 fresh session**：独立子 agent（fresh session，review-only）对全 plan 做 closure audit——逐条 Exit Criteria / Closure Gates 核对、Anti-Hollow 检查、证据写入本 plan `## Closure` 段；未通过 → 保持 `in progress` 并记录缺口。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap §I6 行 `done`，与 daily log 收口段一致
- [x] 遗留观察项全部显式 non-blocking + 后继归属（与 I5 注册清单一一对应）
- [x] closure audit 完成且证据写入 plan `## Closure`（Reviewer/Agent + 每条判据验证结果）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 实际运行并记录输出——预期退出码 1 = 20 条 pre-existing 错误零新增（对照 I1-I5 基线）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Cycle 1 统计落盘且与 live 证据一致
- [x] 稳态判定完成（消费第六门禁族评估 plan 状态/裁定，结论明确——稳态暂停 / Cycle 2 派生 / pending-cancelled 记录三态之一）
- [x] 复触发条件登记完备（4 类 + watch 项 + 周期裁定 + 第六门禁族候选复评估触发条件）
- [x] mission json 授权文本已同步
- [x] roadmap §I6 行 `todo`→`done`；Cycle 1 全 7 行 closed
- [x] 不存在被静默降级的 in-scope 项（门禁红/缺口/新观察显式路由复触发或 Cycle 2）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）统计数字非转抄（逐项回源 live 证据），（b）稳态判定与评估裁定真实一致（非自说自话），（c）复触发条件可执行（每条含触发判据）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 时执行）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0 或记录 pre-existing 基线（guide rule 5b；预期同 I5：2 条 pre-existing，无新增）
- [x] `./mvnw test -pl nop-ai -am -T 1C`（本 plan 不改代码，验证执行；门禁①②③随其执行）——门禁④⑤「零red」以 I5 记录证据为据（本 plan 零代码改动下不重跑 pnpm；若稳态判定需要 ④⑤ live 复核，可顺手执行 `pnpm check:ai-invariants` 并记录）

## Deferred But Adjudicated

### 第六门禁族实现（若评估 plan 裁定 tool-ize）

- Classification: `out-of-scope improvement`（Cycle 2 / I1 派生，Loop Rule 预授权）
- Why Not Blocking Closure: 实现不影响 Cycle 1 收口——「有新族」本身就是稳态判定的合法分支（N1 边），派生登记完成即收口职责完成
- Successor Required: `yes`（Cycle 2 / I1 派生计划）
- Successor Path: Loop Rule 预授权派生（PD-n 先例链）

### 门禁耗时优化（ArchUnit 扫描范围 + ast-grep 合并）

- Classification: `optimization candidate`
- Why Not Blocking Closure: I1 判据「首跑 >30s 再评估」**未单独计时（I5 仅有全 reactor 总时长 03:23，无单门禁计时），判据未触发**；五族门禁零命中 baseline 不受影响
- Successor Required: `no`（复触发登记内，周期复探时复核）

## Non-Blocking Follow-ups

- 复触发路径登记（CI 首次可用补验 invariant-gates / 周期复探执行 / 结构变更触发）——由后续 Cycle 或人工按登记执行。
- watch 项触发条件（AskOracleExecutor / dnsResolver / plan-runtime 接线）——登记即 closed，触发时走复触发。

## Closure

Status Note: Cycle 1 循环收口完成——统计落盘（五族门禁 / finding / 修复 / 验证四组数据，全部回源 live 证据零虚构）、稳态判定 = **稳态暂停 待复触发**（消费 `2026-08-12-1700-1` completed 裁定 `watch-only residual` → 零新族且零 red 分支）、复触发条件登记完备（catalog §6.1-6.3：四类基线 + watch 项 + 第六门禁族候选复评估触发条件 + 周期 30 天裁定）、mission json 授权文本已同步（I3 裁定反映）。roadmap §I6 行 `done`，Cycle 1 全 7 行 closed。closure 独立 fresh session 完成（APPROVED，详见证据）。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: mission-driver 独立 closure audit fresh session（`2026-08-12-111911-mission-driver`，review-only，未复用实现 session）
- Audit Session: `2026-08-12-111911-mission-driver`
- Evidence:
  - Phase 1 EC 逐条 PASS：统计四组数据落盘于 roadmap §I6 行 + daily log `08-12.md` Phase 1 条目；live 回源——catalog §3.1-§3.4 表 33/17/30/8、gate-gaps.yaml 终态 5 条 N/A、5 个门禁测试类 + 2 mjs + CI job `invariant-gates`（`.github/workflows/maven.yml:48`）全部在位；前置校验 = 六份 I0-I5 plan `Plan Status: completed` + 评估 plan completed（live grep 逐文件复核）
  - Phase 2 EC 逐条 PASS：稳态判定 = 评估 plan completed → 消费裁定 `watch-only residual` → 稳态暂停（roadmap §I6 行 + 评估 plan closure 记录 `ses_00aa7577fffedsAAv65FbjaJJl`）；复探周期 30 天成文（catalog §6.1 #3 + roadmap）；catalog §6（6.1 四类基线 / 6.2 watch 项 3 条 / 6.3 第六门禁族候选复评估触发条件 3 条）live 全部在位；mission json JSON 合法（`python3 -m json.tool` exit 0）+ diff 仅授权句一处 + roadmap §Cross-Cutting 同步；doc-links exit 1 = 20 pre-existing 零新增
  - Phase 3 EC 逐条 PASS：roadmap §I6 `done` 与 daily log 收口段一致；遗留观察项全部显式 non-blocking + 后继归属（scan-hollow 2 / doc-links 20 → 周期复探复核面；探查工具化候选 → 评估裁定 + §6.3；CI fallback → §6.1 #4；门禁耗时优化 → Deferred，判据未触发）；closure audit 本 session 完成；checklist exit 0（`plan-check.mjs --strict`）；doc-links exit 1 基线；daily log 收口条目已更新
  - Closure Gates 11 条逐条 PASS：统计回源（surefire XML 5 测试类 17 tests 0 fail 0 error + catalog 表 + I4 closure evidence 5 commits）；稳态判定与评估裁定一致（watch-only residual，非自说自话）；复触发条件每条含可判定触发判据（catalog §6 逐条 live 复核）；无静默降级项（Deferred 分类核对见下）；`plan-check.mjs --strict` exit 0（closure 时复跑）；`scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0 + 2 条 pre-existing（PlanReplanner:272 / NoOpProviderFailoverQueue:34，同 I5 基线零新增）；`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（04:26，Finished 19:13:40；门禁①②③ 5 测试类 17 tests 全 0 fail——surefire XML 逐类核对 tests/errors/skipped/failures）；门禁④⑤零red 以 I5 full-green 记录为据（本 plan 零代码改动）
  - Anti-Hollow 检查：统计数字逐项回源 live 证据（非转抄）；稳态判定 = 消费已 completed 评估 plan 的裁定原文（watch-only residual，双文档交叉一致）；复触发条件每条含触发判据与复触发动作（可执行非空转）
  - Deferred 分类检查：第六门禁族实现 = `out-of-scope improvement`（稳态判定合法分支，Successor = Cycle 2 / Loop Rule 预授权派生）；门禁耗时优化 = `optimization candidate`（I1 判据未触发，复触发周期内复核）；Non-Blocking Follow-ups 仅复触发路径执行项——无 in-scope live defect / contract drift 被降级
  - 构建/静态检查：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS；checkstyle N/A（零产品代码改动，纯文档收口）

Follow-up:

- 复触发登记项的执行（CI 补验 / 周期复探 30 天 / 结构变更 / watch 项触发）——按 catalog §6 登记执行，触发时按 Loop Rule 复触发（下轮 I2/I3 → I4）。
- 无剩余 plan-owned work（Cycle 1 全部 7 行 closed；若稳态判定外派生 Cycle 2，由 Loop Rule 预授权另行派生计划）。

## Draft Review Records

- Round 1（fresh session `ses_00ad16f54ffeffMntUIYVwC8za`）：4 Major + 9 Minor，全部修订（mission json 验证步骤、评估 pending 下游行为、Current Baseline 与 live 状态一致、check-doc-links 步骤、6 文件计数、Source 出处、5 commits 计数源、遗留项清单对应、Cross-Cutting 同步、复触发落点、耗时优化理由、④⑤ 依据注明）。
- Round 2（fresh session `ses_00aca369cffe5bP0EARB02DJOG`）：1 Major + 4 Minor，全部修订（doc-links 预期退出码改 exit 1 = 20 pre-existing 零新增、flake 已修复事实更新、catalog §6 新建落点、遗留项表述改「涵盖」、评估 plan 第三态处置）。
- Round 3（fresh session `ses_00ac5a00bffeismOMb6vquIn74`）：1 Major + 2 Minor，全部修订（第三态处置在 Phase 2 item/EC/Closure Gate 三处传播闭环——pending-cancelled 记录为勾选依据；Closure Gate 复触发登记范围补第六门禁族候选复评估触发条件；doc-links EC 措辞消除与 AGENTS.md 冲突歧义）。共识达成 → Plan Status: active。
