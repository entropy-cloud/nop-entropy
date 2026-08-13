# 3 AI Invariant Loop I5 — 全量验证

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Work Item: Cycle 1 / I5. 全量验证
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md` §I5；I1 plan（`2026-08-12-1120-2`）Closure 遗留：CI `invariant-gates` job 实跑验证显式登记为 I5 验证项
> Related: `ai-dev/plans/2026-08-12-1411-2-ai-invariant-i4-fix-execution.md`（I4，前置）；I6 收口计划（待 I5 完成后创建）

## Purpose

对 Cycle 1 修复后的 live baseline 做全量验证：`./mvnw test -pl nop-ai -am -T 1C` + 五族门禁零命中 + full-green 记录；补齐 I1 遗留的 CI `invariant-gates` job 实跑验证（GitHub Actions 上真实执行门禁④⑤）；产出 I6 收口的稳态判定输入（门禁零命中 + 复触发条件基线）。

## Current Baseline

（以下事实均于 2026-08-12 live 核实；I5 开工前须确认 I4 已完成）

- I4 修复（前置，fail-fast：I4 plan status = completed + 裁决表 44/44 存在 + known-gaps 终态 = 仅 watch/not-applicable 条目——**终态条目数以 I3 裁决为准**，预期 gate-2 4 N/A + gate-4 1 N/A 及 I3 裁决新增的 N/A/watch 条目；若 I3 将 R-2-1/R-2-2 裁为 not-applicable 则 N/A 条目多于 4+1）。
- 五族门禁产物（I1 落地，live 在位）：
  - JUnit/ArchUnit：`TestInvariantGate1SecureDefault`（agent）/ `TestInvariantGate1SecureDefaultShell`（shell）/ `TestInvariantGate2OrchestrationTimeout` / `TestInvariantGate3EntryPointCleanup`（随 `./mvnw test -pl nop-ai -am -T 1C` 执行）。
  - mjs：`check-ai-tool-executor-boundary.mjs`（门禁④）/ `check-fix-commit-diff.mjs`（门禁⑤，subject-only 匹配修复后版本）；聚合 `pnpm check:ai-invariants`。
- CI：`.github/workflows/maven.yml` 新增 `invariant-gates` job（`fetch-depth: 0` + pnpm 10 + node 20 + `pnpm install --frozen-lockfile` + `pnpm check:ai-invariants`）；**I1 Closure 显式记录「GitHub Actions 实跑无法本地验证，显式登记为 I5 验证项」**。
- 全量验证命令（mission 配置）：test = `./mvnw test -pl nop-ai -am -T 1C`；build = `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C`。
- 已知 pre-existing 基线：scan-hollow 2 条 high finding（`PlanReplanner.java:272` / `NoOpProviderFailoverQueue.java:34`，2026-08-01 commit，I1 已记录非本 mission 引入）；doc-links 20 个 pre-existing 错误（其他组 roadmap 引用）；**已知 flake**：`nop-auth-service TestChannelScanBindLoginE2E` 偶发失败（VarCollector NPE，2026-08-09 既有，隔离运行通过，I1 closure 记录）——该模块在 `-pl nop-ai -am` reactor 内。
- **命令实跑范围（实测）**：`./mvnw -pl nop-ai -am` reactor 含 **151 个项目**（nop-ai 21 模块 + 全部上游依赖闭包：01-nop-kernel、nop-core-framework、nop-auth-service、nop-task-core、nop-stream-core 等）——远超「全聚合模块」；MCP 模块无测试目录不在审计目标集内，此范围差异不影响验证结论，但**无关模块失败须按 Phase 1 分诊规则处理**。
- I2 遗留观察：探查工具化候选（兄弟路径高频模式 → I6 评估第六门禁族）。

## Goals

- 全量验证命令全绿并记录：`./mvnw test -pl nop-ai -am -T 1C` + `pnpm check:ai-invariants` + `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C`（构建基线，无关模块 flake 按分诊规则处理）。
- 五族门禁零命中：门禁①-⑤全部通过，known-gaps 清单与门禁实际输出精确一致（零 drift）。
- CI `invariant-gates` job 实跑验证完成（push/PR 触发在 GitHub Actions 上执行成功）或显式记录无法验证的理由与替代证据。
- full-green 记录落地（daily log / 验证文档），构成 I6 稳态判定输入：门禁零命中 + 复触发条件（CI 变红 / 新增 Default* 类 / 周期复探）基线。

## Non-Goals

- **不修任何代码**：验证中发现门禁红或缺口 → 记录并路由回 I4（或按裁决表处理），不在 I5 内修复。
- **不裁决新 finding**（I3 已关闭；新发现记录为观察项供 I6/下一 Cycle）。
- **不收口/稳态判定**（I6，独立计划）。
- 不重新跑 6 deep/ARM 全量审计。

## Scope

### In Scope

- 全量验证命令执行与结果记录。
- 五族门禁零命中验证（含 known-gaps drift 核对）。
- CI `invariant-gates` job 实跑验证。
- full-green 记录 + 复触发条件基线（I6 输入）。

### Out Of Scope

- 修复（I4）、裁决（I3）、收口（I6）。
- 新审计维度。

## Execution Plan

### Phase 1 - 全量验证执行

Status: completed
Targets: `nop-ai/**`（全模块组——含 MCP 模块；`-pl nop-ai -am` 命令实际覆盖 nop-ai 模块 + 上游依赖闭包共 151 个项目，MCP 模块无测试目录、不在审计目标集内，此范围差异不影响验证结论）

- Item Types: `Proof`

- [x] `Proof` 前置校验（fail-fast）：I4 完成证据（**I4 plan status = completed** + 裁决表 44/44 存在 + known-gaps 终态 = 仅 watch/N/A 条目；门禁测试类与 mjs 脚本在位）；不满足则停并记录，不得在修复未完成时假验证。
- [x] `Proof` `./mvnw test -pl nop-ai -am -T 1C` 全绿（含门禁①②③），记录 surefire 结果（各门禁测试类 test 数 / 0 fail）。**无关模块失败分诊规则**：若 nop-ai 模块 surefire 全绿但 reactor 中无关模块失败（如已知 flake `nop-auth-service TestChannelScanBindLoginE2E`）→ 隔离重跑确认（`./mvnw test -pl <模块>` 或单测类隔离运行）→ 通过则记录「隔离重跑通过，非本 mission 引入」并继续，不阻塞门禁零命中结论；nop-ai 模块自身失败 → 记录并路由回 I4（不在此修）。
- [x] `Proof` `pnpm check:ai-invariants`（门禁④⑤，post-I4 版本）全绿，记录 exit 0 + 输出摘要。
- [x] `Proof` `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS（构建基线）。

Exit Criteria:

- [x] 三条全量命令全部绿（或无关模块 flake 已按分诊规则隔离确认），结果记录于 daily log
- [x] surefire 门禁测试结果逐类记录（无 fail）
- [x] 无 owner-doc update required；**No new test required: 本 plan 纯验证不改代码**（guide rule 25 豁免注记）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 门禁零命中 + known-gaps drift 核对

Status: completed
Targets: `ai-dev/audits/nop-ai-invariants/gate-gaps.yaml`、`invariant-catalog.md` 目标集表

- Item Types: `Proof`

- [x] `Proof` 门禁①表完备性复验：`find nop-ai -name "Default*.java" ...` 命令输出 vs catalog §3.1 表（33 类）diff 为零；全部带 `@SecureDefault` 注解（grep 抽查 + 门禁测试实判）。
- [x] `Proof` 门禁②表完备性复验：编排入口表（14 + 表扩展条目）命令输出 vs 表 diff 为零；timeout 声明实判全绿。**判据优先级说明**：以门禁测试内嵌机械反查（`TestInvariantGate2OrchestrationTimeout` 的表完备性用例）为权威判据 + 按 catalog §3.2 原文命令抽查；**catalog §4 与 §3.2 #① 的浓缩复现命令（`{engine,tool}` brace 展开）输出 ≠ 表行数**（会把 CallAgentExecutor 一并抓出、executeAllowedCalls 为 void 不被 future 正则捕获；实测 22 行 ≠ 表 14 行）——不得用 brace 展开命令计数做 diff 判据；如需抽查只跑 engine 单包 grep（与 §3.2 #① 注释「11 行」一致）。
- [x] `Proof` 门禁③④⑤复验：gate-3 8/8 成对；gate-4 30 实例声明成立（mjs exit 0）；gate-5 subject 匹配零违规。
- [x] `Proof` known-gaps drift 核对：清单条目 vs 门禁实际输出——零差异（清单内条目全部仍存在且理由成立；清单外零新缺口）。

Exit Criteria:

- [x] 四张目标集表复验全部与 live 一致（零 diff）
- [x] 五族门禁零命中（含表完备性）
- [x] known-gaps drift = 零（记录核对结果）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - CI 实跑验证（I1 遗留项）

Status: completed
Targets: `.github/workflows/maven.yml` `invariant-gates` job

- Item Types: `Proof`

- [x] `Proof` **门前检查（先于任何触发动作）**：`git remote -v` 确认是否存在 GitHub remote；确认 workflow 触发条件（push 到 master / PR 到 master / **`workflow_dispatch` 手动触发**）与当前分支是否可触发。**本仓库事实**：当前唯一 remote 为 gitee（`https://gitee.com/canonical-entropy/nop-entropy.git`），无 GitHub remote、无 `gh` CLI——**预期直接走 fallback 路径（下条），不得在无触发通道时推送等待**。
- [x] `Proof` **fallback 路径（预期路径，非异常）**：若 Phase 3 门前检查确认无 GitHub 触发通道，显式记录理由（remote 清单 + workflow 触发条件）与替代证据——本地 `pnpm check:ai-invariants` 全绿（含 self-test）+ workflow YAML 解析通过（`python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/maven.yml'))"` 或等价的 node/pnpm 解析——**注：PyYAML 按 YAML 1.1 解析，`on:` 键会被当作布尔 key 不报错，此命令仅作语法冒烟，不构成语义校验**）+ JUnit 门禁随 `./mvnw test -pl nop-ai -am -T 1C` 实跑证据（surefire 报告）——并登记为 I6 收口时的复触发条件（CI 首次可用时补验）。
- [x] `Proof` 若 GitHub 通道可用：推送/PR 触发，`invariant-gates` job 实际执行；验证 `pnpm install --frozen-lockfile` + `pnpm check:ai-invariants` 成功（绿色）。**非空转判据（按实际执行模式）**：CI 中 `check-fix-commit-diff.mjs` 走 since-mode（`--since 2026-07-31`，`pnpm check:ai-invariants` 不带 `--base-ref`），判据 = 日志出现「扫描到 fix(nop-ai) commit: N」且 N>0（而非查找「PR 区间」字样）——**前提：I4 提交沿用 mission commitFormat `fix(nop-ai)`**；若 I4 已修 R-5-1（subject-only），`5ebad065e`/`c1362dc77` 不再被扫入。**（N/A 已勾：门前检查确认无 GitHub 触发通道，本条件分支未执行——按 plan 条件结构走 fallback 路径，非空转判据登记供 I6 CI 首次可用时复用）**

Exit Criteria:

- [x] 门前检查结果记录（remote 清单 + 触发条件 + 结论：可触发/走 fallback）
- [x] `invariant-gates` job 实跑成功（若可触发），门禁⑤日志 N>0 证据记录；或 fallback 路径完成（理由 + 替代证据 + I6 复触发登记）
- [x] 记录 CI 验证结果于 daily log（含 job 链接或 fallback 记录）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - full-green 记录 + I6 输入

Status: completed
Targets: `ai-dev/logs/`、roadmap §I5 行

- Item Types: `Fix | Proof`

- [x] `Fix` full-green 记录：验证结果汇总（命令 / 退出码 / 门禁计数 / known-gaps 终态）写入 daily log 与 roadmap §I5（`todo`→`done` 前由本 plan 执行记录支撑）。
- [x] `Proof` 复触发条件基线登记（供 I6 收口）：CI 变红（门禁 job fail）/ 新增或重命名 Default* 类 / 编排入口 / ToolExecutor 实现（结构变更触发 Loop Rule）/ 周期复探（I6 裁定周期）——现状基线 = 门禁零命中 + known-gaps 仅 watch/N/A。
- [x] `Proof` 遗留观察项汇总（供 I6）：scan-hollow pre-existing 基线（按 I4 closure 后重跑结果记录）、doc-links pre-existing 20 条、探查工具化候选（第六门禁族评估）、CI fallback 复触发项（若 Phase 3 走 fallback）——均标注 non-blocking。

Exit Criteria:

- [x] full-green 记录完成（daily log + roadmap §I5）
- [x] 复触发条件基线成文（现状 + 触发条件 + 后续处理路径）
- [x] 遗留观察项全部显式标注 non-blocking 及后继归属
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全量验证命令全绿并记录（test / check:ai-invariants / clean install）
- [x] 五族门禁零命中，known-gaps drift 为零
- [x] CI `invariant-gates` 实跑验证完成**或 fallback 路径完成**（门前检查理由 + 替代证据 + I6 复触发登记，见 Phase 3）——I1 遗留项闭合
- [x] full-green 记录 + 复触发条件基线已写入（I6 输入就绪）
- [x] 不存在被静默降级的 in-scope 项（验证中发现的门禁红/缺口已路由 I4 或显式记录）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）门禁在测试链上真实执行（JUnit 门禁 surefire 实跑证据——本仓库无 GitHub 通道时，CI job 实跑不构成可验证项，以 Phase 3 门前检查记录 + fallback 替代证据 + I6 复触发登记为闭合依据），（b）门禁绿是修复后实判而非清单豁免
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 时执行）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0 或记录 pre-existing 基线（guide rule 5b；预期 2 条 high finding——`PlanReplanner.java:272` / `NoOpProviderFailoverQueue.java:34`，2026-08-01 commit，同 I1 closure 记录；若 I4 改动 plan/runtime 面则按 I4 closure 后基线重新记录）
- [x] `./mvnw test -pl nop-ai -am -T 1C`（本 plan 不改代码，验证执行）

## Deferred But Adjudicated

None（本 plan 纯验证；验证中发现的未裁决事项显式路由 I4 或记录为 I6 观察项）

## Non-Blocking Follow-ups

- I6 收口计划（待本 plan 完成后创建）：统计 + 稳态判定 + 复触发条件登记 + closure 独立 fresh session。
- 第六门禁族评估（探查工具化候选，I6 裁定）。

## Closure

Status Note: 全量验证三条命令全绿 + 五族门禁零命中（known-gaps drift 零）+ CI `invariant-gates` fallback 路径完成（无 GitHub 触发通道，I1 遗留项闭合）+ full-green 记录与复触发条件基线已写入（I6 输入就绪）。本 plan 纯验证零代码改动，无 in-scope 残留。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general 类型，fresh session `ses_00adefef6ffeH9RQLOtyMMdtQm`，review-only 禁改文件）
- Evidence:
  - Phase 1（全量验证执行）逐条 PASS：`_tmp/i5-mvnw-test.log` BUILD SUCCESS + 5 门禁类 0 fail（Gate1 agent 5 / shell 3、Gate2 agent 3 / gateway 3、Gate3 3）；surefire XML 5 份 tests=5/3/3/3/3 failures=0 errors=0 skipped=0（真实 testcase 非跳过）；`_tmp/i5-pnpm-check.log` 门禁④⑤ self-test 全 PASS + 30 实例 PASS + 8 commit 零违规；`_tmp/i5-clean-install.log` BUILD SUCCESS；I4 前置（plan completed + 裁决表 44 零悬挂 + gate-gaps 终态）live 核验通过。
  - Phase 2（门禁零命中 + drift 核对）逐条 PASS：`find` 命令 = 33 行 == catalog §3.1 表；33/33 类 `@SecureDefault`（grep -L 零文件）；gate-gaps.yaml 仅 5 条 N/A（gate-2 4 + gate-4 1），gate-1/gate-3 空，与门禁实判输出精确一致（零 drift 零 stale）。
  - Phase 3（CI 实跑 fallback）逐条 PASS：`git remote -v` 仅 gitee 无 GitHub、无 gh CLI；workflow `invariant-gates` job 配置 live 复核（fetch-depth: 0 + pnpm 10 + node 20 + frozen-lockfile + check:ai-invariants）；fallback 替代证据 = pnpm 全绿 + YAML parse OK + surefire 报告 + I6 复触发登记（非空转判据已写）。
  - Phase 4（full-green + I6 输入）逐条 PASS：daily log 08-12.md 含 I5 Phase 1-4 条目（倒序）；roadmap §I5 行 `todo`→`done`；复触发条件基线（CI 变红 / 结构变更 / 周期复探 / CI fallback）+ 遗留观察项（scan-hollow 2 条 pre-existing、doc-links 20 条、第六门禁族评估）全部落档并标注 non-blocking。
  - Closure Gates 8 条全勾且底层证据全部 live 核验（无静默降级：I5 日志「偏差：无」，无门禁红/缺口需要路由 I4）。
  - `node ai-dev/tools/check-plan-checklist.mjs ... --strict` 退出码 0（审计时 + closure 时复跑）。
  - Anti-Hollow 检查 PASS：门禁真实执行（surefire XML 真实 testcase 计数 + mjs 真实扫描输出「目标集: 30 实例」「扫描到 fix(nop-ai) commit: 8」——非空转/非豁免）；门禁绿 = 修复后实判（33/33 注解 + 17 入口声明 + 30 实例声明 + 8 commit 实质 diff）。
  - `scan-hollow-implementations.mjs --module nop-ai --severity high` = 2 条 pre-existing 基线（`PlanReplanner.java:272` / `NoOpProviderFailoverQueue.java:34`，2026-08-01 commit，I4 closure 后重跑无变化——gate 条款允许记录基线）。
  - 审计发现 2 Minor（daily log 叙述性笔误：mvnw test 时间表述与实际 Finished 16:34:15 不符、gate-2 计数 16+4≠17）——已修正（时间改为实测、计数改为 13 declared + 4 N/A），不影响验证结论。
  - Deferred 项分类检查：无 deferred；Non-Blocking Follow-ups 仅 I6 收口计划 + 第六门禁族评估（optimization candidate，non-blocking 理由明确）。

Follow-up:

- I6 收口计划（待创建）：统计 + 稳态判定 + 复触发条件登记 + closure 独立 fresh session。
- 第六门禁族评估（探查工具化候选，I6 裁定）。

## Draft Review Records

- Round 1（fresh session `ses_00b6410fcffe5WEGNN1w04qnKs`）：3 Major + 7 Minor，全部修订（门前检查/fallback 预期路径、Anti-Hollow 豁免子句、scan-hollow gate、since-mode 判据、brace 命令警告、MCP 范围、终态判据、YAML 命令、rule 25、措辞）。
- Round 2（fresh session `ses_00b5587a7ffeZ4zlNBKZYlbdbe`）：1 Major + 3 Minor，全部修订（151 项目 reactor 范围 + nop-auth flake + 分诊规则、§3.2 brace 警告、Goals 命令计数、workflow_dispatch）。
- Round 3（fresh session `ses_00b45560fffeC0xh9fHWPD2Z2B`）：verdict 可执行（零 Blocker 零 Major；2 Minor：PyYAML 冒烟局限 + commitFormat 前提——均已注明）。共识达成 → Plan Status: active。
