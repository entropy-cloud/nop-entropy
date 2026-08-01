# 324 flux-web complex 页面类型渲染（收口验证）

> Plan Status: completed
> Last Reviewed: 2026-08-01
> Source: commit `f7c45373d`（complex 实现已先行落地，2026-08-01 13:47）；plan 323 §Deferred But Adjudicated 的 complex 条目（原 out-of-scope，Successor Required: yes）
> Related: `323-flux-web-container-body-rendering.md`、`290-flux-web-xlib-attribute-mapping-fixes.md`、`287-nop-web-flux-xlib.md`
> 注：本 plan 为**收口验证型**（对齐 plan 323 的 audit 式 closure 先例）：实现代码已由用户提交的 commit `f7c45373d` 落地，本 plan 不做新代码变更，只做独立验证、文档一致性核对与独立 closure audit。

## Purpose

收口 plan 323 遗留的 complex successor 项：独立验证已落地的 complex 页面类型实现（xview.xdef 四槽位 schema → flux-web Flux `PageSchema` 渲染）正确性、文档一致性、无回归，完成独立 closure audit 后关闭。

## Current Baseline（live 状态，2026-08-01 逐一核实）

- **xview.xdef complex schema 已落地**（`xview.xdef:226-231`）：`<complex name="!string" xdef:name="UiComplexPageModel" xdef:ref="UiPageModel" xdef:bean-tag-prop="type">` + `header`/`footer`/`aside`/`body` 四个子元素（每个 `xdef:ref="UiContainerModel"`，容器列表经 `xdef:bean-body-prop="body"` 解析）——与 Flux `PageSchema` 四区域一一对应。**`xdef:bean-tag-prop="type"` 已补齐**（缺失时 `pageModel.type` 无法命中分派）。
- **分派与渲染已落地**：`flux-web/impl_GenPage.xpl:37-39` 含 complex 分派分支；`flux-web/page_complex.xpl` 存在（`FluxPageDefaultAttrs` + `initApi` 外壳 + 四槽位 `j:list` 数组 + `size() > 0` 非空守卫 + 槽位内逐容器 `GenContainerModel` 分派）。
- **schema 事实（审查修正）**：`UiContainerModel` 的子元素是**六类**——`crud`/`picker`/`simple`/`tabs`/`wizard`/`group`（`xview.xdef:67-217`，`picker` 在 `:121`）；**complex 不在其中**。因此 `GenContainerModel` 分派五类（crud/simple/tabs/wizard/group，不含 picker）是符合 schema 的；picker 落入 otherwise 显式抛 `nop.err.web.unknown-page-type`（`flux-web.xlib:60`，符合 no-silent-noop）。
- **测试已落地**：`test-flux-complex.view.xml`/`page.yaml`/`xmeta` 夹具存在；`TestFluxWebGen.testComplexPageEmitsFourSlots`（`:365-418`）断言 `type='page'` 外壳 + 四槽位数组 + 分派结果（simple→`form` name 来自 `formModel.id`；crud→`crud` name 来自 `table.name`，与 `container_simple.xpl:39`/`grid_crud.xpl:23,33` 实际行为一致）。surefire（2026-08-01 18:44）：TestFluxWebGen 18 例 0 failures；nop-web 全量 71 例 0 failures。
- **nop-xdefs 已重装**（`~/.m2` jar 内 xview.xdef 已是新 schema——xview.xdef 是 jar 资源，不重装则运行时用旧定义报 `node-unexpected-tag-name`）。
- **文档已同步**：`flux-rendering.md` complex 段落、`source-anchors.md` EXT-009、design doc `flux-web-container-body-rendering.md` R3 修订、`ai-dev/logs/2026/08-01.md` 条目。
- **剩余 gap**：无 in-scope 实现缺口；需要独立验证证据与 closure 收口。

## Goals

- 独立验证 complex 实现满足收口要求：schema 修复、分派、四槽位渲染、测试、文档五方面与 live repo 一致。
- nop-web 全量回归确认无既有测试被破坏。
- 独立子 agent 执行 closure audit 并写入 evidence（plan guide #27），关闭 plan。
- 修正审查发现的事实错误记录（`picker` 属 `UiContainerModel` 子元素，六类非五类）。

## Non-Goals

- **不做新代码变更**（实现已落地；若验证发现 live defect，转入 Non-Blocking Follow-ups / successor，不在本 plan 内修）。
- `GenContainerModel` 不加 complex/picker 分支（complex 不在 `UiContainerModel`；picker 保持 otherwise 显式抛错）。
- 不暴露 `asidePosition`（complex schema 未定义，aside 固定左侧）。
- 不改 AMIS `web.xlib` / `web/` 模板、不改 nop-chaos-flux 仓库。
- 不回写已 closed 的 plan 323（历史记录，见 plan guide #20）。
- 不验证 commit `f7c45373d` 中用户单独附加的 e2e 修复（`CrudListPage`/`FluxAdapter`，与本 plan 的 complex 功能无耦合）。

## Scope

### In Scope

- 验证 xview.xdef complex schema（`bean-tag-prop` + 四槽位）与实现一致（Proof）。
- 验证 `impl_GenPage.xpl` complex 分派 + `page_complex.xpl` 四槽位渲染（Proof）。
- 运行 nop-web 全量测试确认无回归（Proof）。
- 核对文档一致性：`flux-rendering.md`、`source-anchors.md`、design doc、daily log（Proof）。
- 工具验证：`check-doc-links.mjs --strict`、`scan-hollow-implementations.mjs`、`check-plan-checklist.mjs --strict`（Proof）。
- 独立子 agent closure audit + evidence 写入（Proof）。

### Out Of Scope

- 新代码实现（验证若发现 live defect，转 follow-up）。
- e2e 测试修复（commit `f7c45373d` 中用户单独项）。
- `asidePosition` / AMIS 侧 / nop-chaos-flux 仓库 / 页面级 picker。

## Execution Plan

### Phase 1 - 实现正确性验证（代码 + 测试）

Status: completed
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/xview.xdef`, `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/impl_GenPage.xpl`, `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/page_complex.xpl`, `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxWebGen.java`

- Item Types: `Proof`

- [x] live 核对：`xview.xdef` complex 定义含 `xdef:bean-tag-prop="type"` + 四槽位；`impl_GenPage.xpl` 含 complex 分派；`page_complex.xpl` 四槽位经 `GenContainerModel` 分派且空槽位守卫不输出。
- [x] focused 测试：`./mvnw test -pl nop-frontend-support/nop-web -am -Dtest='TestFluxWebGen#testComplexPageEmitsFourSlots' -Dsurefire.failIfNoSpecifiedTests=false`。
- [x] 全量回归：`./mvnw test -pl nop-frontend-support/nop-web -am`。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] schema/分派/渲染代码与 Current Baseline 描述一致（read/rg 可观察）
- [x] `testComplexPageEmitsFourSlots` 通过（focused 运行）
- [x] nop-web 全量测试通过且无回归（71 例基线，含既有 @Disabled skip）
- [x] **接线验证**：`impl_GenPage.xpl` complex 分派 → `page_complex.xpl` → `GenContainerModel` 运行时调用链连通（端到端 view.xml → Flux JSON 四槽位断言 + 代码追踪）
- [x] **无静默跳过**：`GenContainerModel` otherwise 显式抛 `nop.err.web.unknown-page-type`；`page_complex.xpl` 空槽位守卫不输出（无空方法体/continue/吞异常）
- [x] `ai-dev/logs/2026/08-01.md` 验证结果已记录（或注明无需新增）

### Phase 2 - 文档一致性验证

Status: completed
Targets: `docs-for-ai/02-core-guides/flux-rendering.md`, `docs-for-ai/04-reference/source-anchors.md`, `ai-dev/design/flux-web-container-body-rendering.md`, `ai-dev/logs/2026/08-01.md`

- Item Types: `Proof`

- [x] 核对 `flux-rendering.md`：标题/介绍"四种"、complex 段落（PageSchema 四槽位映射、aside 默认 left、"不支持的类型"仅留 picker）与实际输出 JSON 一致。
- [x] 核对 `source-anchors.md` EXT-009 含 complex 与 `PageSchema`。
- [x] 核对 design doc R3 修订记录 + 结论 5/§3.5 从"不实现"改为"已实现"。
- [x] 核对 `ai-dev/logs/2026/08-01.md` 顶部 complex 条目存在。
- [x] 文档事实修正：`UiContainerModel` 子元素表述为六类（crud/picker/simple/tabs/wizard/group），纠正"五类不含 picker"的错误表述（`flux-web.xlib:28-31` 注释、`flux-rendering.md:291` 若有则修正）。
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`。

Exit Criteria:

- [x] 各文档与 live 实现一致（抽查 complex 段/锚点/design doc/日志）
- [x] `UiContainerModel` 六类（含 picker）的事实表述已在受影响文档/注释修正（或确认既有表述无事实错误）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/2026/08-01.md` 已更新

### Phase 3 - 工具验证与 closure 收口

Status: completed
Targets: 工具 + plan 自身

- Item Types: `Proof`

- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high`（注意模块在 `nop-frontend-support/` 下，`--module nop-web` 会解析到不存在的目录导致真空绿）。
- [x] 独立子 agent（fresh session，不同 task_id）执行 closure audit，逐条核对本 plan 的 Exit Criteria 与 Closure Gates。
- [x] audit 通过后写入 Closure Evidence 并运行 `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/324-flux-web-complex-page-rendering.md --strict`。

Exit Criteria:

- [x] `scan-hollow-implementations.mjs` 退出码 0（0 findings）
- [x] 独立子 agent closure audit 完成且 verdict APPROVE（无阻止项）
- [x] `check-plan-checklist.mjs ... --strict` 退出码 0（全项勾选 + Closure Evidence 已写入）
- [x] `ai-dev/logs/2026/08-01.md` 收口记录已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] complex schema 修复已落地（`xdef:bean-tag-prop="type"`）且 `pageModel.type == 'complex'` 分派命中（验证通过）
- [x] complex 页面渲染已落地：`page_complex.xpl` 输出 Flux `PageSchema` 四槽位，槽位容器经 `GenContainerModel` 分派（验证通过）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响的 owner docs（`flux-rendering.md`、`source-anchors.md`、design doc）已与 live baseline 一致
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`page_complex.xpl` 在运行时被 `impl_GenPage.xpl` 的 complex 分派调用，（b）`page_complex.xpl` 的四槽位在运行时调用 `GenContainerModel`（端到端 view.xml → Flux JSON 断言），（c）无空方法体/静默跳过
- [x] `./mvnw test -pl nop-frontend-support/nop-web -am` 通过（71 例 0 failures，无回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/324-flux-web-complex-page-rendering.md --strict` 退出码 0

## Deferred But Adjudicated

### asidePosition 属性未暴露

- Classification: `watch-only residual`
- Why Not Blocking Closure: Flux `PageSchema.asidePosition` 默认 `'left'`（`page.tsx:56`），不暴露时 aside 固定左侧，行为确定且可用；暴露需 xview.xdef schema 加属性 + `FluxPageDefaultAttrs` 透传，属优化项。
- Successor Required: no

### GenContainerModel 不加 complex / picker 分支

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `UiContainerModel` 子元素为六类（crud/picker/simple/tabs/wizard/group，`xview.xdef:67-217`），**不含 complex**——complex 仅页面级（`pages` 下），schema 层即禁止作为 body 子容器；picker 保持 `GenContainerModel` otherwise 显式抛 `nop.err.web.unknown-page-type`（no-silent-noop，无静默降级）。
- Successor Required: no

### e2e 测试修复（commit `f7c45373d` 附加项）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `CrudListPage` 编辑等待竞态加固与 `FluxAdapter` 列头占位偏移修复是用户单独提交的配套项，与本 plan 的 complex 页面渲染验证无耦合；其正确性由 e2e 测试管线覆盖，不属本 plan 收口范围。
- Successor Required: no

## Non-Blocking Follow-ups

- complex schema 暴露 `asidePosition`（Flux `PageSchema` 支持 left/right）——优化项，需 schema + 透传两步。
- 页面级 picker（plan 323 遗留：Flux 无页面级 picker schema，`page_picker.xpl` 为 AMIS 遗留）——out-of-scope。
- plan 323 的 Non-Blocking Follow-ups（`responsiveColumns` 转换、`startStep` 语义、`FluxTabDefaultAttrs` 对齐核查）不受本 plan 影响，继续挂账。

## Closure

Status Note: complex 页面类型实现（commit `f7c45373d`）经独立 closure audit 逐条核实，五个 Closure Gates + Anti-Hollow (a)(b)(c)(d) 全部 PASS，三项工具检查退出码 0，nop-web 全量 71 例无回归。三个 Phase（实现/文档/工具+audit）均 completed。无 in-scope live defect；两项 deferred（asidePosition、GenContainerModel 不加 complex/picker）均 non-blocking 且 `Successor Required: no`。本 plan 可关闭。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure-audit（fresh session，task_id `ses_0430d603bffeU8DYcyQss1Z5Vm`，general subagent，非实现者 self-audit）
- Audit Session: `ses_0430d603bffeU8DYcyQss1Z5Vm`
- Evidence:
  - Phase 1 Exit Criteria（实现正确性）：PASS — `xview.xdef:226-231` complex 定义含 `xdef:bean-tag-prop="type"` + 四槽位（header/footer/aside/body 各 `xdef:ref="UiContainerModel" xdef:body-type="list" xdef:bean-body-prop="body"`）；`impl_GenPage.xpl:37-39` complex 分派 → `page_complex.xpl`；`page_complex.xpl:5/10/15/20` 四槽位 `xpl:if` 守卫 + `:7/12/17/22` `GenContainerModel` 分派。
  - Phase 2 Exit Criteria（文档一致性）：PASS — `flux-rendering.md:354-373` complex 段（四槽位映射 + aside 默认 left + `asidePosition` 未暴露 + 实现锚点）；`source-anchors.md:32` EXT-009 含 complex/PageSchema；`flux-web-container-body-rendering.md:11` R3 + `:22` 结论 5 + `:166` §3.5"已实现"；`08-01.md:144-156` complex 条目。附带修正 `flux-web.xlib:28-32` 注释事实错误（`UiContainerModel` 六类含 picker，picker 落入 otherwise 抛错，而非"body 容器类型不含 picker"）。
  - Phase 3 Exit Criteria（工具 + audit）：PASS — 见下三项工具退出码 + 独立 audit verdict。
  - Closure Gate「schema 修复落地」：PASS — `xview.xdef:226` `xdef:bean-tag-prop="type"`，`pageModel.type == 'complex'` 命中 `impl_GenPage.xpl:37` 分派（测试无 unknown-page-type 异常佐证）。
  - Closure Gate「渲染落地」：PASS — `page_complex.xpl` 输出 `PageSchema` 四槽位经 `GenContainerModel` 分派。
  - Closure Gate「无静默降级 deferred」：PASS — Deferred/Non-Blocking 仅含 asidePosition（optimization）与 GenContainerModel 无 complex/picker 分支（out-of-scope），无 in-scope live defect。
  - Closure Gate「owner docs 同步」：PASS — 见 Phase 2 evidence。
  - Closure Gate「独立子 agent closure-audit」：PASS — 本条即该 audit 的记录。
  - Anti-Hollow (a) 接线：PASS — `impl_GenPage.xpl:37-38` complex 分支 `<c:include src="page_complex.xpl"/>` 运行时可达。
  - Anti-Hollow (b) 四槽位运行时调用 GenContainerModel：PASS — `page_complex.xpl:7/12/17/22` `<c:for>` 内真实调用（端到端测试 `testComplexPageEmitsFourSlots` 断言四槽位数组含 form/crud 分派结果佐证）。
  - Anti-Hollow (c) 无静默跳过：PASS — `flux-web.xlib:59-61` `GenContainerModel` otherwise `<c:throw errorCode="nop.err.web.unknown-page-type">`；`page_complex.xpl` 空槽位 `xpl:if` 守卫（无空方法体/continue/吞异常）。
  - Anti-Hollow (d) 端到端断言：PASS — `TestFluxWebGen.testComplexPageEmitsFourSlots:365-418` 断言 `type='page'` 外壳 + header/body/footer 数组含 `form`（name 来自 formModel.id）+ aside 数组含 `crud`（name 来自 table.name）。
  - Deferred 分类诚实性：PASS — 两 deferred 项均带 `Why Not Blocking Closure` + `Successor Required: no`；无 in-scope live defect 降级。
  - 工具结果（executor 运行，audit 独立复核 live code 佐证）：`./mvnw test -pl nop-frontend-support/nop-web -am` = BUILD SUCCESS，71 tests 0 failures 1 skipped（pre-existing @Disabled），TestFluxWebGen 18 例；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high` 退出码 0（0 findings）。

Follow-up:

- complex schema 暴露 `asidePosition`（Flux `PageSchema` 支持 left/right）——优化项，需 schema + `FluxPageDefaultAttrs` 透传两步。
- 页面级 picker（Flux 无页面级 picker schema，`page_picker.xpl` 为 AMIS 遗留）——out-of-scope。
- plan 323 的 Non-Blocking Follow-ups（`responsiveColumns` 转换、`startStep` 语义、`FluxTabDefaultAttrs` 对齐核查）不受本 plan 影响，继续挂账。
- 无剩余 plan-owned work。
