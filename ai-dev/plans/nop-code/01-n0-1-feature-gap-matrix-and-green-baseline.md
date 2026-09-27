# 01 N0.1 缺口跟踪矩阵与绿色基线

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N0.1;`ai-dev/design/nop-code/` 9 份设计文档待做项;`ai-dev/analysis/2026-09-23-codegraph-survey-vs-nop-code.md`(P0/P1/P2 建议)
> Related: roadmap 全部 WI(本矩阵是 NG.1 收口对照物)
> Draft Review: R1(agent_e4659516,1 Blocker + 2 Major + 5 Minor)全修订 → R2 delta 复核 PASS(2026-09-27)

## Purpose

为 nop-code 功能补全 roadmap 建立两个执行前提:

1. **缺口跟踪矩阵**:roadmap 38 个 Work Item ↔ 设计文档待做项 ↔ 调研建议的三方对照矩阵,落盘 `ai-dev/audits/nop-code/`,作为 NG.1"逐 Work Item 对照缺口矩阵确认零残留"的对照物。
2. **绿色基线**:roadmap 验收命令 `./mvnw test -pl nop-code -am -T 1C` 全绿,并记录基线数字(模块数/测试数/耗时)。

## Current Baseline

- **roadmap 验收命令当前 FAILURE**(2026-09-27 实测,exit 1):
  - `nop-auth-web 2.0.0-SNAPSHOT` FAILURE——`io.nop.auth.web.NopAuthWebPagesTest.testValidateAllPages` 报 `NopEvalException[nop.err.xlang.exec.get-prop-on-null-obj]`:`formModel.layout` 为 null 时访问 `formModel.layout.simpleTable`,页面 `/nop/auth/pages/NopAuthLoginAttempt/main.page.yaml`。
  - `nop-code-app` 因依赖 nop-auth-web 被 **SKIPPED**(其测试从未执行)。
  - **其余 nop-code 全部 12 个模块 SUCCESS**:nop-code / nop-code-core / nop-code-flow / nop-code-lang-java / nop-code-lang-python / nop-code-lang-typescript / nop-code-codegen / nop-code-dao / nop-code-meta / nop-code-api / nop-code-service / nop-code-web。注:`-T 1C` 并行日志交错,各模块测试数以 surefire 报告为准,不采信日志行归属;权威基线数字以 Phase 3 全绿运行为准。
- **失败根因**(live 追踪):`nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthLoginAttempt/_gen/_NopAuthLoginAttempt.view.xml`(2026-09-23 提交 d746ce4aeb 的代码生成产物)中 `<form id="view|edit">` 含**空** `<layout/>`;运行时该空节点求值为 null。`web.xlib` GenFormBody(L141-148)与 `flux-web.xlib` GenFormBody(L154-161)及两处 GenAccordion(`web.xlib` L164、`flux-web.xlib` L177)直接访问 `formModel.layout.simpleTable`/`formModel.layout.groups`,无 null 防护。同仓其它实体页面的 `_gen` 基座 layout 均有字段内容(如 `_NopAuthDept.view.xml`),故仅此新页面触发。
- **缺口三方对照材料已在档**:roadmap §2 全 38 WI todo;设计文档待做项(2026-09-27 提取,覆盖 `ai-dev/design/nop-code/` 下 9 份:`00-vision.md`、`01-architecture-baseline.md`、`query-api-design.md`、`search-integration-design.md`、`graph-analysis-design.md`、`graph-discovery-and-export-design.md`、`semantic-edge-design.md`、`flow-analysis-design.md`、`ai-e2e-acceptance-design.md`);survey §8.1 P0/P1/P2 建议清单。
- **矩阵落点目录 `ai-dev/audits/nop-code/` 当前不存在**,mission 配置(`missions/nop-code-feature-completion.json`)指定 `auditsDir: ai-dev/audits/nop-code`、`plansDir: ai-dev/plans/nop-code`。

## Goals

- roadmap 验收命令 `./mvnw test -pl nop-code -am -T 1C` 全绿(exit 0),含 nop-auth-web 与 nop-code-app。
- `ai-dev/audits/nop-code/nop-code-feature-gap-matrix.md` 建立:每个 roadmap WI ↔ 设计文档出处 ↔ survey 建议编号 ↔ 范围外豁免登记,状态列与 roadmap 动态状态区一致。
- 基线数字(测试总数/模块数/耗时)写入矩阵文档头部与当日 daily log。

## Non-Goals

- 不实施 roadmap 任何功能 WI(N0.2 起)。
- 不修改 NopAuthLoginAttempt 的 `_gen` 生成产物(硬规则)与 codegen 模板(空 layout 为生成物合法形态,防护应在渲染侧)。
- 不调整 nop-auth 权限/认证模型(Protected Area ask-first,本计划只做前端渲染 null 防护)。
- 不评估或修订 roadmap 范围(矩阵只做对照登记,发现的新缺口回灌 roadmap 由 roadmap 流程裁决)。

## Scope

### In Scope

- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web.xlib` GenFormBody + GenAccordion 中 `formModel.layout.*` 访问的 null 防护。
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib` 同位置的 null 防护。
- 新建 `ai-dev/audits/nop-code/nop-code-feature-gap-matrix.md`。
- 基线运行记录 + daily log。

### Out Of Scope

- `NopAuthLoginAttempt` 页面产物的重新生成。
- roadmap 各 WI 的实现。
- 其它模块(非 nop-code 依赖闭包)的测试修复。

## Execution Plan

### Phase 1 - 基线解锁:layout null 防护

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web.xlib`、`flux-web.xlib`

- Item Types: `Fix`

- [x] `web.xlib` GenFormBody:`formModel.layout.simpleTable` → `formModel?.layout?.simpleTable`;`formModel.layout.firstTable` → `formModel?.layout?.firstTable`;两处 `formModel.layout.groups` → `formModel?.layout?.groups`(L141-148,accesses 在 L142/144/145/148)
- [x] `web.xlib` GenAccordion:`formModel.layout.groups` → `formModel?.layout?.groups`(L164)
- [x] `flux-web.xlib` GenFormBody 四处(L155 tabs 分支 groups、L157 simpleTable、L158 firstTable、L161 otherwise groups)+ GenAccordion 一处(L177)
- [x] 语义核对:layout 为 null/空时 GenFormBody 走 `otherwise` 分支、`GenLayoutGroups` 对 null layoutGroups 空迭代渲染空表单体(不抛错、不静默新增行为;`ForOfExecutable` 对 null items 返回 null);`?.` 写法在 web.xlib 已有先例(L403 `formModel?.defaultColumnRatio`)

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-frontend-support/nop-web -am -T 1C` 通过(nop-web 自身回归;2026-09-27 BUILD SUCCESS exit 0)
- [x] `./mvnw test -Dtest=NopAuthWebPagesTest -pl nop-auth/nop-auth-web -am -Dsurefire.failIfNoSpecifiedTests=false -T 1C` 通过(原失败测试;`-am` 必须携带,否则本机 `~/.m2` 无 io.nop SNAPSHOT 依赖无法解析;Tests run: 1, Failures: 0, Errors: 0)
- [x] 行为语义:layout 非空页面的渲染路径无变化(改动仅为 null 防护,无分支逻辑变更)
- [x] **无静默跳过**:null layout 渲染空表单体是生成物声明的合法语义(空 `<layout/>`),非吞错
- [x] **No new test required**:既有 `NopAuthWebPagesTest.testValidateAllPages` 即本缺陷的回归测试(修复前红、修复后绿),覆盖 `/nop/auth/pages/NopAuthLoginAttempt/main.page.yaml` 全量页面渲染校验路径
- [x] No owner-doc update required:`docs-for-ai/` 中无 web.xlib GenFormBody layout 行为的规范性描述;本次为缺陷防护,不改变文档化行为
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 缺口跟踪矩阵

Status: completed
Targets: `ai-dev/audits/nop-code/nop-code-feature-gap-matrix.md`

- Item Types: `Proof`

- [x] 矩阵主体:38 行(每 roadmap WI 一行),列:WI 编号/名称/Item Type/设计文档出处(文件+章节)/survey 建议编号(§8.1)/状态(todo)/验证物
- [x] 范围外豁免登记表:MCP、Hypergraph、Obsidian、Neo4j Cypher、Elasticsearch、IDE/LSP、交互式可视化、Token 效率分级、Swift/ObjC 桥、AI Workflow 提示库(与 roadmap §1 范围外表一致,注明否决出处)
- [x] 设计文档待做项 ↔ WI 反向覆盖检查:上列 9 份设计文档提取的全部待做/目标条目均能映射到某 WI 或范围外登记,无孤儿条目;孤儿条目(如有)显式列出并注明"回灌 roadmap 待裁决"
- [x] 文档头部记录 commit 与模块清单(基线测试总数/耗时等权威数字由 Phase 3 统一写入,本 Phase 不预填)

Exit Criteria:

- [x] 矩阵文件存在于 `ai-dev/audits/nop-code/nop-code-feature-gap-matrix.md`,38 WI 全覆盖
- [x] 反向覆盖检查完成,无未登记孤儿条目(或孤儿条目已显式登记待裁决)
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0(2026-09-27,No errors found)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 绿色基线确认

Status: completed
Targets: 基线命令运行记录

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-code -am -T 1C` exit 0,记录:各 nop-code 模块 SUCCESS、nop-auth-web SUCCESS、nop-code-app 执行且 SUCCESS(2026-09-27 BUILD SUCCESS,reactor 全模块 SUCCESS 无 FAILURE/SKIPPED)
- [x] 基线数字(总测试数/耗时)回填矩阵文档头部(Tests run 13153 / Failures 0 / Errors 0 / Skipped 96;Wall Clock 3:26 min;closure audit 更正:初记 26306/192 为 awk 双计错误,已经两类日志行独立合计交叉验证更正)
- [x] roadmap N0.1 状态改 `done`(独立 closure audit 放行后已同步 roadmap §2)

Exit Criteria:

- [x] 命令 exit 0 且 reactor summary 无 FAILURE/SKIPPED
- [x] 基线数字(总测试数/耗时)已写入矩阵文档头部
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**:只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后,才能将 `Plan Status` 改为 `completed`。

- [x] Phase 1 修复后原失败测试 `NopAuthWebPagesTest` 通过(Tests run 1 / Errors 0,BUILD SUCCESS)
- [x] roadmap 验收命令 `./mvnw test -pl nop-code -am -T 1C` 全绿(BUILD SUCCESS exit 0,reactor 全模块 SUCCESS)
- [x] 缺口矩阵落盘且反向覆盖无孤儿(38 WI 全覆盖;3 个 watch-only 孤儿显式登记于矩阵 §五)
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect(closure audit 核实 Deferred 区为空、follow-up 为 nop-auth 2.x 产物策略问题 non-blocking)
- [x] 受影响的 owner docs 已同步:`docs-for-ai/03-modules/nop-code.md` 无需更新(无 nop-code 代码变更);`ai-dev/logs/` 已更新;matrix 属新档不涉及既有 owner doc
- [x] 独立子 agent closure-audit 已完成并记录证据(agent_3550bcaa,2026-09-27)
- [x] **Anti-Hollow Check**:closure audit 已验证入口点(testValidateAllPages → validateAllPages)→ 故障点(xlib L144,修复前日志 L2719334 四点重合)→ 出口(focused test 绿 + 全量基线绿)链路真实连通
- [x] `./mvnw compile -pl nop-frontend-support/nop-web -am`(xlib 为资源文件,编译验证以测试替代:nop-web 模块 BUILD SUCCESS 91 tests)
- [x] `./mvnw test -pl nop-code -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-web --severity high` 退出码 0(Critical/High 0 findings)
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

(无——本计划无 deferred 项)

## Non-Blocking Follow-ups

- `NopAuthLoginAttempt` 编辑表单空 layout 的产品语义(是否应由 codegen 产出带字段清单的 layout)属 nop-auth 2.x 产物策略问题,与本基线无关,不阻塞。

## Closure

Status Note: N0.1 交付物(缺口矩阵 + 绿色基线)全部落地。基线命令初跑 FAILURE(nop-auth-web 预存缺陷:2026-09-23 生成产物空 `<layout/>` + xlib 非空假设),经 Phase 1 null 防护修复后 BUILD SUCCESS(reactor 全模块 SUCCESS,nop-code 13 模块 + nop-auth-web + nop-code-app;Tests run 13153 / Failures 0 / Errors 0 / Skipped 96)。缺口矩阵 38 WI 全覆盖 + 15 项范围外豁免登记 + 反向覆盖检查(3 个 watch-only 孤儿显式登记待裁决)。独立 closure audit 初裁 REJECT(唯一 Major:基线数字 awk 双计 26306/192),已按审计交叉验证更正为 13153/96 三处(plan/矩阵/daily log)后放行。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: agent_3550bcaa(独立 fresh-session closure auditor,2026-09-27)
- Audit Session: agent_3550bcaa-1bea-490d-b667-6ce21552ce5a
- Evidence:
  - Phase 1 Exit Criteria 全 PASS:xlib 9 处防护逐行核实(grep 非防护访问零命中);git diff --numstat 5+/5- 原位改写;nop-web 回归 BUILD SUCCESS(91 tests);focused test Tests run 1 / Errors 0
  - Phase 2 Exit Criteria 全 PASS:矩阵 38 行与 roadmap §2 逐一对应(N0×2+N1×4+N2×4+N3×2+N4×3+N5×6+N6×5+N7×2+N8×1+N9×7+NG×2);3 个 WI 出处抽查全真实;反向覆盖 10 份文档、3 孤儿显式登记
  - Phase 3 Exit Criteria 全 PASS:BUILD SUCCESS / [447/447] / Total time 3:26 min / nop-auth-web 9.085s + nop-code-app 0.604s 均执行;基线数字经审计更正为 13153/96(模块级 Results 行与单测试类行两种独立口径合计一致)
  - Closure Gates 全 PASS:Anti-Hollow 链路四点重合闭环(入口 testValidateAllPages → 修复前日志 L2719334 → 修复行 L144 → focused/全量绿);`scan-hollow-implementations --module nop-web --severity high` exit 0;`check-doc-links --strict` exit 0;`check-plan-checklist --strict` exit 0(证据写入后实测)
  - Deferred 项分类检查:Deferred 区为空;矩阵 §五 3 孤儿经逐 WI 核对确不在 38 WI 内,watch-only 分类诚实
  - 审计裁定:初裁 REJECT(基线数字双计)→ 更正后 APPROVE,无需重跑构建或复审代码

Follow-up:

- NopAuthLoginAttempt 空 layout 的 codegen 产物策略(nop-auth 2.x 产品语义问题,崩溃缺陷本体已修复)——non-blocking
- 矩阵 §五 3 个 watch-only 孤儿(多仓/文档节点模型、语义边参与图算法、test_gap 覆盖率接入)——successor 由 roadmap 裁决承载
