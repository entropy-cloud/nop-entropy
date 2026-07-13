# 289 flux-web.xlib Layout 语法完整性修复

> **Partially superseded by: `290-flux-web-xlib-attribute-mapping-fixes.md`**（2026-07-13）
> 本计划的 mandatory 优先级链修复（Phase 1）仍然有效。但 Phase 2 中的以下裁定经 nop-chaos-flux 源码核对发现有误，已由 plan 290 修正：
> - `requiredWhen: cellModel?.requiredOn` → 应为 `required: cellModel?.requiredOn`（requiredWhen 是 ValidationRule.kind，不是 schema 字段）
> - 顶层 `clearValueWhenHidden` → 应为嵌套对象 `hiddenFieldPolicy: { clearValueWhenHidden: ... }`
> - FluxFormDefaultAttrs 不补 initFetch → Flux 用 `autoInit` 命名，应补
> - FluxPageDefaultAttrs 不补 aside* → Flux 完全支持 aside 属性（含 `asideResizor → asideResizable` 命名映射）

> Plan Status: completed
> Last Reviewed: 2026-07-12
> Source: layout 语法对比审计（基于 `docs-for-ai/02-core-guides/layout-syntax-reference.md` 对照 `flux-web.xlib` vs `web.xlib`）
> Related: `287-nop-web-flux-xlib.md`（首版 Flux xlib 实现计划，已完成）、`288-docs-restructure-frontend-flux-amis.md`（前端文档重构，已完成）

## Purpose

修复 `flux-web.xlib` 在首版实现（plan 287）中遗留的 layout 语法支持缺陷，使其完整对齐 `docs-for-ai/02-core-guides/layout-syntax-reference.md` 中描述的所有 layout DSL 语义，并消除 `GenFormSimpleCell` / `FluxFormDefaultAttrs` / `FluxPageDefaultAttrs` 相对 `web.xlib` 同名标签的功能性遗漏。

plan 287 的 closure audit 已通过，但本次审计发现其对比粒度停留在"标签名集合一致 + 控件 type 名映射正确"，未对 layout 语法核心标签（`GenFormSimpleCell`）逐行比对 mandatory 优先级链与 cell 动态属性输出，导致若干 cell 级配置在 Flux 渲染器下静默失效。本计划收口这些缺陷。

## Current Baseline

经独立子 agent 全面对比（task ses_0a992d73fffe7HPgJ7yzhTNQIP），当前 live repo 状态：

- **标签集合完整**：`flux-control.xlib`（75 标签）与 `control.xlib`（75 标签）一一对应；`flux-web.xlib`（37 标签）与 `web.xlib`（37 标签）功能性一一对应。**无标签缺失**。5 对 DefaultAttrs 中 3 对（Grid/Tabs/Tab）仅前缀差异、属性清单完全一致；2 对（Form/Page）存在属性清单差异（见下方具体 bullet）。
- **已确认有意设计**（已由 `docs-for-ai/02-core-guides/flux-rendering.md` AMIS vs Flux 对照表背书）：`visibleOn`→`visible`、`disabledOn`→`disabled`、`group`→`flex`/`container`、`fieldSet`→`fieldset`、`divider`→`separator`、`columnRatio`→`flex`、`hidden`→`_hidden`、`label:false`→`label:''`、无 `staticOn`、`NormalizeAction` 的 onClick DAG 设计。
- **疑似 bug 或功能缺失**（本计划要处理的）：
  - `flux-web.xlib:GenFormSimpleCell` 第 338-340 行 `mandatory` 处理缺少 `formCell.mandatory ||` 前缀，导致 layout `*` 修饰符（`docs-for-ai/02-core-guides/layout-syntax-reference.md` §3.1）和 §11.4 中通过 layout `*` 标记查询必填（区别于 `ui:queryMandatory` 机制，后者 `flux-web.xlib:340` 已正常工作）在 Flux 渲染器下完全失效。AMIS 优先级链为 `formCell > cellModel > propMeta`，Flux 退化为 `cellModel > propMeta`。
  - `flux-web.xlib:GenFormSimpleCell` cell 对象输出缺失 `requiredOn` 和 `clearValueOnHidden`。`web.xlib:393-394` 输出 `requiredOn: cellModel?.requiredOn` 与 `clearValueOnHidden: cellModel?.clearValueOnHidden`，Flux 完全丢弃。其中 `clearValueOnHidden` 是 `nop-kernel/nop-xdefs/.../xui/form.xdef` 第 93 行明确支持的 cell 属性（第 89 行注释说明）。
  - `FluxFormDefaultAttrs`（`flux-web.xlib:606-614`）相对 `FormDefaultAttrs`（`web.xlib:690-699`）缺失 4 个属性：`initFetch`、`silentPolling`（`form.xdef:14-15、25` 层面已定义为表单模型通用属性，是否 Flux 真不支持需要调查）；`wrapWithPanel`（plan 287 closure audit 时声明为 AMIS 特有并有意移除，需复核该裁定是否准确）；`canAccessSuperData`（plan 287 closure audit 已修复一次，但本次再比对仍发现缺失，需复核 closure 是否回退或当时的修复不完整）。
  - `FluxPageDefaultAttrs`（`flux-web.xlib:634-639`）相对 `PageDefaultAttrs`（`web.xlib:719-726`）缺失全部 5 个 `aside*` 属性（`asideResizor` / `asideMinWidth` / `asideMaxWidth` / `asideClassName` / `asideSticky`）。`ai-dev/analysis/2026-06-28-amis-vs-flux-schema-comparison.md` §2.2 已明确记录 Flux 不支持 aside 容器（"无，靠 flex 布局组合"），因此这 5 个属性预期不应补全到 `FluxPageDefaultAttrs`，但需在 `flux-rendering.md` 显式记录该限制（目前未记录）。
- **附带发现（不在本计划 scope，记录为 follow-up）**：`web.xlib:914` 的 `GenValidator` 标签 include 了不存在的 `web/impl_GenValidator.xpl`，应为 `std/impl_GenValidator.xpl`（flux-web.xlib:674 已正确引用 std/）。属 web 侧遗留 bug，独立处理。

## Goals

- 修复 `flux-web.xlib:GenFormSimpleCell` 的 mandatory 优先级链，使 layout `*` 修饰符在 Flux 渲染器下与 AMIS 行为一致（`formCell.mandatory > cellModel.mandatory > propMeta.mandatory`）。
- 补全 `GenFormSimpleCell` 输出的 `requiredOn` 和 `clearValueOnHidden` 属性（或调查确认 Flux 真不支持后，在 `flux-rendering.md` 显式记录限制并附 test 证明行为可观测）。
- 调查并裁定 `FluxFormDefaultAttrs` 与 `FluxPageDefaultAttrs` 的缺失属性：能补则补，不能补则在文档中显式记录 Flux 不支持的原因，并附 focused test。
- 为每项修复添加 regression 测试，从 `flux-web:GenForm` 入口到输出 JSON 端到端验证必填/条件必填/隐藏清空/初始化拉取等行为。
- 更新 `docs-for-ai/02-core-guides/flux-rendering.md` 记录已确认的 Flux 限制（如果有），并在 `layout-syntax-reference.md` 顶部声明该文档同样适用于 Flux 渲染器（消除"本文档以 AMIS 渲染器实现为准"导致的歧义）。

## Non-Goals

- **不修改 `web.xlib` / `control.xlib`**：本次只补 Flux 侧缺陷。`web.xlib:GenValidator` 引用错误作为 follow-up 单独处理。
- **不废弃 AMIS 管线**：两套管线继续并存，废弃时机由后续计划决定（plan 287 已 deferred）。
- **不利用 Flux 高级结构节点**：plan 287 已 deferred 的 fragment/loop/recurse/reaction/data-source 仍在 successor plan 范畴。
- **不做前端实际渲染验证**：仅在 Java 侧通过 `pageProvider.getPage()` 生成 JSON 并断言结构。前端渲染验证由前端团队负责。
- **不引入 Flux 独有控件**（condition-builder/transfer/key-value 等）：plan 287 已 deferred。
- **不重构 `NormalizeAction`**：其 onClick DAG 设计已确认为有意，不在本次改动范围。

## Scope

### In Scope

- `flux-web.xlib` 的 `GenFormSimpleCell` 标签：补 mandatory 优先级链 + cell 动态属性。
- `flux-web.xlib` 的 `FluxFormDefaultAttrs`：调查并裁定全部缺失属性（`initFetch` / `silentPolling` / `wrapWithPanel` / `canAccessSuperData`）。
- `flux-web.xlib` 的 `FluxPageDefaultAttrs`：基于已有分析确认 `aside*` 属性不补全（Flux 不支持 aside 容器）。
- `flux-web.xlib` 现有测试：扩展 `TestFluxWebGen` 覆盖 layout `*` 修饰符、`requiredOn`、`clearValueOnHidden`。
- `docs-for-ai/02-core-guides/flux-rendering.md`：记录 Flux 已确认不支持的限制。
- `docs-for-ai/02-core-guides/layout-syntax-reference.md`：声明本文档对 AMIS 和 Flux 两种渲染器均适用，仅在 Flux 有限制的语法点上交叉引用 `flux-rendering.md`。

### Out Of Scope

- `web.xlib` / `control.xlib` 的任何修改。
- `web.xlib:GenValidator` 引用错误修复（作为 Non-Blocking Follow-up）。
- Flux 前端实际渲染验证。
- Flux 高级结构节点利用。
- 废弃 AMIS 管线。
- `NormalizeAction` 的任何改动。

## Execution Plan

### Phase 1 - 修复 GenFormSimpleCell 的 mandatory 优先级链

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`

- Item Types: `Fix | Proof`

- [x] 修改 `flux-web.xlib:GenFormSimpleCell`（当前第 338-340 行），将
  ```
  let mandatory = cellModel?.mandatory ?? propMeta?.mandatory;
  if(mode.startsWith('query'))
      mandatory = cellModel?.mandatory ?? propMeta?.['ui:queryMandatory']
  ```
  改为与 `web.xlib:369-371` 对齐（行为语义：mandatory 优先级链改为 `formCell.mandatory > cellModel.mandatory > propMeta.mandatory`，与 web.xlib 一致）：
  ```
  let mandatory = formCell.mandatory || cellModel?.mandatory ?? propMeta?.mandatory;
  if(mode.startsWith('query'))
      mandatory = formCell.mandatory || cellModel?.mandatory ?? propMeta?.['ui:queryMandatory']
  ```
  注：此表达式与 `web.xlib:369-371` 完全一致，XLang 解析器允许 `||` 与 `??` 无括号混用（web.xlib 中已长期运行）。
- [x] 在 `nop-web/src/test/resources/_vfs/nop/test/pages/` 下创建专用测试 fixture（`test-flux-form-mandatory.view.xml` + 对应 `test-flux-form-mandatory.xmeta` + 4 个 `test-flux-form-mandatory-*.page.yaml` wrapper），其 form layout 包含 `*email[Email]` 修饰符，且 objMeta/xmeta 中 `email` prop **未**声明 `mandatory="true"`（确保测试结果仅由 `*` 修饰符驱动，不被 xmeta mandatory 干扰）。
- [x] 在 `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxWebGen.java`（已存在的测试类，参照其现有 `testGenFormProducesFluxJson` 方法模式）中新增至少 3 个 `@Test` 方法：
  - **layout `*` 修饰符生效**：使用上一步的 fixture，调用 `flux-web:GenForm` 渲染，断言输出 JSON 中对应 cell 的 `required: true`。
  - **layout `*` 优先级覆盖 propMeta**：构造一个 view.xml，其 form layout 为 `*fieldA`，但 objMeta 中 `fieldA` prop 显式 `mandatory="false"`。断言输出 JSON 中 `required: true`（layout 层级胜出）。
  - **查询表单 `*` 生效**：构造一个 `editMode="query"` 的 form，layout 为 `*status[Status]`，断言生成的 `filter_status` cell 的 `required: true`。
- [x] 在 `TestFluxWebGen.java` 中保留一个对照用例：未使用 `*` 且 propMeta 非 mandatory 的字段应输出 `required: null`，证明修复未引入回归。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `flux-web.xlib:GenFormSimpleCell` 的 mandatory 表达式包含 `formCell.mandatory ||` 前缀（验证命令：`rg 'formCell\.mandatory \|\| cellModel' nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib` 应返回至少 2 行）
- [x] 新增的 3 个 `@Test` 方法全部通过（`./mvnw test -pl nop-web -Dtest=TestFluxWebGen`）
- [x] 对照用例（未使用 `*` 的字段不输出 required）通过，证明无回归
- [x] **端到端验证**：从 `flux-web:GenForm` 入口到 JSON 输出，`*email` 修饰符确实反映为 `required: true`
- [x] **接线验证**：修改后的 `GenFormSimpleCell` 在测试中被 `GenFormBody → GenFormCell → GenFormSimpleCell` 调用链触达（通过测试断言证明）
- [x] **无静默跳过**：本 Phase 是补全缺失逻辑，无新增分支；不涉及静默跳过
- [x] `./mvnw test -pl nop-web -am` 通过
- [x] 若本 Phase 改变 live baseline：`docs-for-ai/02-core-guides/flux-rendering.md` 已更新（在本计划 Phase 3 统一更新）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 3 收尾时统一写日志）

### Phase 2 - 补全 GenFormSimpleCell 的 cell 动态属性 + 裁定 DefaultAttrs 缺失属性

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`

> 与 Phase 1 修改同一文件 `GenFormSimpleCell` 的不同行段（Phase 1 改 mandatory 表达式 ~338-340 行，Phase 2.1/2.2 改 cell 对象 ~349-363 行），无功能依赖，但 merge 时需注意行号偏移。建议执行顺序：先完成 (2.3)/(2.4) 调查并填写 Decision Log，再执行 (2.1)/(2.2)/(2.5)/(2.6)。

- Item Types: `Fix | Decision | Proof`

- [x] **(2.1) 补 `requiredWhen`（Flux 命名，对应 AMIS `requiredOn`）**：在 `GenFormSimpleCell` 输出的 cell 对象中添加 `requiredWhen: cellModel?.requiredOn`。**调查发现**（2026-07-12）：通过解压 `pkg-nop-chaos-flux-C3hPTSdm.js.gz` 并 grep，确认 Flux 运行时中存在 `requiredWhen`（13 次出现，schema 字段 `kind: 'requiredWhen'`）和 `requiredUnless`（13 次），但**没有** `requiredOn`。Flux 命名规律：将 AMIS 的 `*On` 后缀改为 `*When`（与 `visible`/`disabled` 命名规律一致）。
- [x] **(2.2) 补 `clearValueWhenHidden`（Flux 命名，对应 AMIS `clearValueOnHidden`）**：在 `GenFormSimpleCell` 输出的 cell 对象中添加 `clearValueWhenHidden: cellModel?.clearValueOnHidden`。**调查发现**：Flux 运行时中存在 `clearValueWhenHidden`（11 次出现，`typeof t.clearValueWhenHidden == 'boolean'`），但**没有** `clearValueOnHidden`。Flux 命名规律：将 AMIS 的 `On` 后缀改为 `When`。`form.xdef:93` 在 view.xml 模型层仍使用 `clearValueOnHidden`（AMIS 命名），Flux 渲染器将其重命名为 `clearValueWhenHidden` 输出。
- [x] **(2.3) 裁定 `FluxFormDefaultAttrs` 4 个缺失属性**：对 `initFetch` / `silentPolling` / `wrapWithPanel` / `canAccessSuperData` 逐一裁定。调查方法：解压 `pkg-nop-chaos-flux-C3hPTSdm.js.gz` 到 `_tmp/flux/flux.js` 并 `rg -o '<prop>'` 计数。结果：4 个属性在 Flux 运行时中**均 0 次出现**。裁定结论：4 个属性均归类为"Flux 不支持"（initFetch/silentPolling 无等价物，wrapWithPanel/canAccessSuperData 是 AMIS 数据域/布局机制专有）。处理：记录到 `Deferred But Adjudicated` + 在 `flux-rendering.md` 显式记录限制。
  - `wrapWithPanel`：维持 plan 287 closure audit 的"AMIS 特有"裁定，本次复核确认
  - `canAccessSuperData`：维持 plan 287 closure audit 的"删除即最终状态"，本次显式记录为"已确认有意省略"
  - `initFetch`：Flux 不支持该属性，不补全
  - `silentPolling`：Flux 不支持该属性，不补全
- [x] **(2.4) 确认 `aside*` 属性不补全**：基于 `ai-dev/analysis/2026-06-28-amis-vs-flux-schema-comparison.md` §2.2 已明确记录"Flux 无 aside 容器，靠 flex 布局组合"，确认 `asideResizor` / `asideMinWidth` / `asideMaxWidth` / `asideClassName` / `asideSticky` 这 5 个属性**不应**补到 `FluxPageDefaultAttrs`。处理方式：记录到 `Deferred But Adjudicated` + 在 `flux-rendering.md` 显式记录限制。规则 24 通过 doc 显式记录满足。
- [x] **(2.5) 补测试**：在 `TestFluxWebGen.java` 中新增 2 个 `@Test`：`testCellRequiredOnMapsToFluxRequiredWhen`、`testCellClearValueOnHiddenMapsToFluxClearValueWhenHidden`。fixture `test-flux-form-cell-attrs.view.xml` 中 `<cell id="name" clearValueOnHidden="true"><requiredOn>${status == 1}</requiredOn></cell>`（注：`requiredOn` 是 disp.xdef:61 的**子元素**而非属性，本次执行时发现并修正了 fixture 写法）。
- [x] **(2.6) 补对照**：在 `TestFluxWebCrudPage.java` 中新增 `testAsidePropertiesAreIgnoredInFlux`，在 `test-flux-crud.view.xml` 的 `<crud>` 上配置 `asideSticky="true" asideResizor="true" asideMinWidth="200" asideMaxWidth="500" asideClassName="my-aside"`，断言输出 JSON 不含这 5 个属性名。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `GenFormSimpleCell` 输出包含 `requiredWhen: cellModel?.requiredOn`（验证命令：`rg 'requiredWhen:.*cellModel' nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib` 应返回至少 1 行）
- [x] `GenFormSimpleCell` 输出包含 `clearValueWhenHidden: cellModel?.clearValueOnHidden`（验证命令：`rg 'clearValueWhenHidden:.*cellModel' nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib` 应返回至少 1 行）
- [x] (2.3) 4 个属性的裁定结论已记录到本 plan 的 `Decision Log` 段落；4 个属性全部归类为"Flux 不支持"，记录到 `Deferred But Adjudicated`
- [x] (2.4) `aside*` 5 个属性的"不补全"裁定已记录到 `Deferred But Adjudicated`（Phase 3 中将在 `flux-rendering.md` 显式记录限制）
- [x] 新增测试用例通过（`./mvnw test -pl nop-web -Dtest=TestFluxWebGen,TestFluxWebCrudPage` — 16 个测试全过）
- [x] **端到端验证**：从 view.xml 中 `<cell><requiredOn>...</requiredOn></cell>` 配置到 JSON 输出 `requiredWhen` 字段完整透传；`clearValueOnHidden="true"` 到 `clearValueWhenHidden: true` 完整透传
- [x] **接线验证**：修改后的 `GenFormSimpleCell` 在测试中被实际调用（通过测试断言证明）
- [x] **无静默跳过**：(2.3)(2.4) 调查结论已写入 Decision Log + Deferred But Adjudicated（Phase 3 中将同步到 `flux-rendering.md`）
- [x] `./mvnw test -pl nop-web -am` 通过
- [x] 若本 Phase 改变 live baseline：相关 docs 已更新（在本计划 Phase 3 统一更新）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 3 收尾时统一写日志）

### Phase 3 - 文档同步与端到端验证

Status: completed
Targets: `docs-for-ai/02-core-guides/flux-rendering.md`, `docs-for-ai/02-core-guides/layout-syntax-reference.md`, `docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Fix | Proof`

- [x] 更新 `docs-for-ai/02-core-guides/layout-syntax-reference.md`：
  - 顶部声明修改：将第 7 行原文 `> **本文以 AMIS 渲染器（\`web.xlib\`）的实现为准。**` 改为同时适用于 AMIS 和 Flux 的声明（如"本文同时适用于 AMIS（`web.xlib`）和 Flux（`flux-web.xlib`）两种渲染器，除非显式标注 `(AMIS only)` 或 `(Flux only)`"）。验证命令：`rg '本文以 AMIS 渲染器' docs-for-ai/02-core-guides/layout-syntax-reference.md` 应无返回（说明已替换）。
  - §3.1 必填标记、§11.4 查询必填：确认示例同时适用于 AMIS 与 Flux（Phase 1 修复完成后应成立）
  - 在 Flux 有限制的语法点（如有）添加交叉引用到 `flux-rendering.md`
- [x] 更新 `docs-for-ai/02-core-guides/flux-rendering.md`：
  - 在 AMIS vs Flux 差异表下新增子表"Flux 已确认不支持的 form/page 属性"，列出 Phase 2 决策结果
  - 在"AMIS vs Flux 关键差异"表中补充说明：layout `*` 必填修饰符、`requiredOn`、`clearValueOnHidden` 在 Flux 中**完全支持**（与 AMIS 一致）
- [x] 更新 `docs-for-ai/04-reference/source-anchors.md`：`EXT-009` 描述补充"完整支持 layout DSL"+ 引用 `flux-rendering.md` 的限制说明。
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` 确认链接无新增断裂（pre-existing 错误记录但不阻塞）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `layout-syntax-reference.md` 顶部声明已改为同时适用于 AMIS 和 Flux
- [x] `flux-rendering.md` 包含 Phase 2 决策结果的显式记录（form 4 属性限制 + page aside* 限制 + cell 命名映射）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码：本计划新增/修改文件不引入新错误（修复了 plan 289 中 1 个 broken link + flux-rendering.md 中 1 个 BOUNDARY 错误；剩余 1 个 pre-existing 错误 `layout-syntax-reference.md:116` 是 `@remark` 必填+跨列示例被工具误识为链接，与本计划无关）
- [x] **端到端验证**：通过源码对照证明 Flux 和 AMIS 行为一致 — `rg 'formCell\.mandatory|requiredOn|requiredWhen|clearValueOnHidden|clearValueWhenHidden' web.xlib flux-web.xlib` 显示两个文件在 mandatory 表达式完全一致，requiredOn↔requiredWhen 和 clearValueOnHidden↔clearValueWhenHidden 仅命名差异
- [x] `ai-dev/logs/` 对应日期条目已更新（在 Closure 阶段统一写日志）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `flux-web.xlib:GenFormSimpleCell` 的 mandatory 表达式包含 `formCell.mandatory ||` 前缀（修复 layout `*` 修饰符）— `flux-web.xlib:338,340`
- [x] `flux-web.xlib:GenFormSimpleCell` 输出 `requiredWhen` 和 `clearValueWhenHidden`（Flux 命名）— `flux-web.xlib:357,361`
- [x] `FluxFormDefaultAttrs` / `FluxPageDefaultAttrs` 的缺失属性已裁定（4 个 form 属性 + 5 个 aside* 属性全部归类 Flux 不支持，记录在 Decision Log + Deferred But Adjudicated + flux-rendering.md）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect（Deferred 项分类经 closure audit 验证为诚实的 `watch-only residual`）
- [x] `docs-for-ai/02-core-guides/layout-syntax-reference.md` 已声明同时适用于 AMIS 和 Flux
- [x] `docs-for-ai/02-core-guides/flux-rendering.md` 已记录 Phase 2 调查结论
- [x] 受影响的 owner docs 已同步到 live baseline（layout-syntax-reference.md + flux-rendering.md + source-anchors.md EXT-009）
- [x] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段落）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）修复后的 `GenFormSimpleCell` 在测试中被实际调用（通过 6 个 @Test 端到端证明），（b）新增的 requiredWhen/clearValueWhenHidden 确实出现在输出 JSON 中（通过 JSON 断言证明），（c）无空方法体/静默跳过（scan-hollow 退出 0）
- [x] `./mvnw test -pl nop-web -am` 通过（16 tests, 0 failures, BUILD SUCCESS）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码：本计划新增/修改文件不引入新错误（剩余 1 个 pre-existing 错误 `layout-syntax-reference.md:116` 与本计划无关）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-web --severity high` 退出码为 0（0 findings）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/289-flux-web-layout-syntax-completeness.md --strict` 退出码为 0

## Decision Log

> Phase 2 的属性裁定结论填入此处。每条记录：属性名 → 裁定（补全 / 不补全）→ 依据。

**FluxFormDefaultAttrs 缺失属性裁定**（调查方法：解压 `pkg-nop-chaos-flux-C3hPTSdm.js.gz` 并 grep，4 个属性在 Flux 运行时中均 0 次出现）：
- `initFetch`: **不补全** — Flux 不支持该属性（0 次出现）。`initFetch` 控制 AMIS initApi 自动触发开关，Flux 无等价机制。记录到 `Deferred But Adjudicated`。
- `silentPolling`: **不补全** — Flux 不支持该属性（0 次出现）。`silentPolling` 是 AMIS 特有的"轮询时隐藏 loading"概念，Flux 无等价机制。记录到 `Deferred But Adjudicated`。
- `wrapWithPanel`: **不补全** — Flux 不支持该属性（0 次出现）。**维持 plan 287 closure audit 的"AMIS 特有"裁定**，本次复核确认。`wrapWithPanel` 是 AMIS Form 特有的"用 panel 包裹表单"布局概念，Flux 用 flex 布局机制。记录到 `Deferred But Adjudicated`。
- `canAccessSuperData`: **不补全** — Flux 不支持该属性（0 次出现）。**维持 plan 287 closure audit 的"删除即最终状态"裁定**。`canAccessSuperData` 是 AMIS 数据域继承机制专有（"能否访问父级数据"），Flux 用显式 ScopeRef 机制，数据访问模型不同。本次显式记录为"已确认有意省略"。记录到 `Deferred But Adjudicated`。

**FluxPageDefaultAttrs 缺失属性裁定**：
- `asideResizor` / `asideMinWidth` / `asideMaxWidth` / `asideClassName` / `asideSticky`: **不补全** — 基于 `ai-dev/analysis/2026-06-28-amis-vs-flux-schema-comparison.md` §2.2 已明确记录"Flux 无 aside 容器，靠 flex 布局组合"，且本次 grep 在 Flux 运行时中确认这 5 个属性均 0 次出现。Flux 前端不消费这些属性。记录到 `Deferred But Adjudicated`。

**Cell 动态属性 Flux 命名映射发现**（调查中的重要发现）：
- AMIS `requiredOn`（disp.xdef:61 子元素）→ Flux `requiredWhen`（13 次出现，`kind: 'requiredWhen'`）
- AMIS `clearValueOnHidden`（form.xdef:93 属性）→ Flux `clearValueWhenHidden`（11 次出现）
- Flux 命名规律：将 AMIS 的 `*On` 后缀改为 `*When`（与 `visible`/`disabled` 命名规律一致）

## Deferred But Adjudicated

### FluxFormDefaultAttrs 不补全的 4 个属性

- Classification: `watch-only residual`
- Why Not Blocking Closure: 通过解压 Flux 运行时 `pkg-nop-chaos-flux-C3hPTSdm.js.gz` 并 grep，确认 `initFetch` / `silentPolling` / `wrapWithPanel` / `canAccessSuperData` 在 Flux 中均 0 次出现。这 4 个属性是 AMIS 特有概念（initFetch 控制 initApi 自动触发、silentPolling 控制轮询 loading、wrapWithPanel 是 AMIS Form 布局机制、canAccessSuperData 是 AMIS 数据域继承机制），Flux 无对应机制。在 view.xml 中配置这些属性时，Flux 渲染器会静默忽略（`_.pickNotNull(formModel, [...])` 不 pick 即不输出）。已在 `docs-for-ai/02-core-guides/flux-rendering.md` 显式记录限制。
- Successor Required: no

### FluxPageDefaultAttrs 不补全的 5 个 aside* 属性

- Classification: `watch-only residual`
- Why Not Blocking Closure: `ai-dev/analysis/2026-06-28-amis-vs-flux-schema-comparison.md` §2.2 已明确记录"Flux 无 aside 容器，靠 flex 布局组合"，本次 grep 在 Flux 运行时中确认 `asideResizor` / `asideMinWidth` / `asideMaxWidth` / `asideClassName` / `asideSticky` 均 0 次出现。Flux 用 flex 布局组合实现侧边栏，不消费这些 AMIS aside 属性。已在 `docs-for-ai/02-core-guides/flux-rendering.md` 显式记录限制。`testAsidePropertiesAreIgnoredInFlux` 测试证明配置了这些属性的 view.xml 在 Flux 渲染时不报错但属性被忽略。
- Successor Required: no

## Non-Blocking Follow-ups

- `web.xlib:914` 的 `GenValidator` include 路径错误（`web/impl_GenValidator.xpl` 不存在，应为 `std/impl_GenValidator.xpl`）：与 Flux 修复无关，作为独立小修复处理。
- Flux 高级结构节点（fragment/loop/recurse/reaction/data-source）利用：plan 287 已 deferred。
- 废弃 web.xlib / control.xlib：plan 287 已 deferred。

## Closure

Status Note: plan 289 修复了 plan 287（首版 Flux xlib）遗留的 layout 语法支持缺陷。三处代码修改真实落地（mandatory 优先级链 + requiredWhen + clearValueWhenHidden），调用链端到端连通（由 16 个通过的测试证明），文档同步完成，deferred 项分类诚实，无 hollow 实现，无被降级的 live defect。Flux 渲染器现在完整支持 `layout-syntax-reference.md` 描述的所有 layout DSL 语法。独立 fresh-session closure audit 全部 PASS。
Completed: 2026-07-12

Closure Audit Evidence:

- Reviewer / Agent: Independent subagent (fresh session, did not participate in drafting or execution)
- Audit Session: ses_0a94f65c4ffe0VWrMR3p5U4Ie8
- Evidence:
  - **Phase 1**（mandatory 修复）— 全部 9 条 Exit Criteria PASS：
    - `flux-web.xlib:338,340` 包含 `formCell.mandatory || cellModel` 前缀（与 `web.xlib:369,371` 逐字符一致）
    - 3 个 @Test（`testLayoutStarMarkedFieldIsRequired`/`testLayoutStarOverridesPropMetaMandatoryFalse`/`testLayoutStarMarkedQueryFieldIsRequired`）+ 1 对照（`testControlCaseNoStarNoRequired`）通过
  - **Phase 2**（cell 动态属性 + DefaultAttrs 裁定）— 全部 11 条 Exit Criteria PASS：
    - `flux-web.xlib:357` `requiredWhen: cellModel?.requiredOn`、`flux-web.xlib:361` `clearValueWhenHidden: cellModel?.clearValueOnHidden`
    - 4 个 FluxFormDefaultAttrs 属性 + 5 个 FluxPageDefaultAttrs aside* 属性全部归类"Flux 不支持"，记录到 Decision Log + Deferred But Adjudicated + flux-rendering.md
    - 3 个新测试通过（`testCellRequiredOnMapsToFluxRequiredWhen`/`testCellClearValueOnHiddenMapsToFluxClearValueWhenHidden`/`testAsidePropertiesAreIgnoredInFlux`）
  - **Phase 3**（文档同步）— 全部 5 条 Exit Criteria PASS：
    - `layout-syntax-reference.md` L7 顶部声明改为同时适用于 AMIS 和 Flux
    - `flux-rendering.md` 新增"Flux 已确认不支持的 form/page 属性"表 + cell 命名映射说明
    - `source-anchors.md` EXT-009 描述补充
  - **Anti-Hollow Check** — PASS：
    - (a) 调用链 `GenForm → GenFormBody → GenFormCell → GenFormSimpleCell` 在测试中被运行时触达（通过 6 个 @Test 端到端证明，非组件级单测）
    - (b) 新增的 requiredWhen/clearValueWhenHidden 字段确实出现在输出 JSON 中（通过 JSON 断言证明）
    - (c) `scan-hollow-implementations.mjs --module nop-web --severity high` 退出 0，无空方法体/静默跳过
  - **Deferred 项分类检查** — PASS：
    - FluxFormDefaultAttrs 4 属性 + FluxPageDefaultAttrs 5 aside* 属性分类为 `watch-only residual` 合理（Flux 已确认不支持，不是 live defect 降级）
    - `web.xlib:914 GenValidator` 路径错误作为 Non-Blocking Follow-up，plan Non-Goals 三处显式声明 out-of-scope，非偷偷降级
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/289-flux-web-layout-syntax-completeness.md --strict` 退出码：0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-web --severity high` 退出码：0
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码：1（仅剩余 1 个 pre-existing 错误 `layout-syntax-reference.md:116` 的 `@remark` DSL 被误识为链接，与本计划无关；本计划新增/修改文件零错误）
  - `./mvnw test -pl nop-frontend-support/nop-web -am` BUILD SUCCESS（16 tests, 0 failures, 0 errors）

Follow-up:

- `web.xlib:914` 的 `GenValidator` include 路径错误（`web/impl_GenValidator.xpl` 不存在，应为 `std/impl_GenValidator.xpl`）：与 Flux 修复无关，作为独立小修复处理（Non-Blocking Follow-up）
- Flux 高级结构节点（fragment/loop/recurse/reaction/data-source）利用：plan 287 已 deferred
- 废弃 web.xlib / control.xlib：plan 287 已 deferred
- 前端实际渲染验证（nop-chaos-flux 浏览器渲染）：plan 287 已 deferred
