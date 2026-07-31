# 323 flux-web 页面容器 Body 渲染（tab/step 内嵌容器 + wizard/group 页面类型）

> Plan Status: completed
> Last Reviewed: 2026-08-01（执行完成 + 独立 closure audit APPROVE）
> Source: `ai-dev/design/flux-web-container-body-rendering.md`；nop-chaos-flux 仓库 schema（`~/app/nop-chaos-flux-wt/nop-chaos-flux-master`）
> Related: `290-flux-web-xlib-attribute-mapping-fixes.md`、`322-flux-normalize-action-native-format.md`、`ai-dev/design/render-mode-switch-design.md`

## Purpose

补齐 flux-web.xlib 对 xview.xdef 容器化 body 配置的消费能力：页面级 tab/step 支持内嵌 body 容器渲染（无 page 时渲染 body），并新增 wizard、group 两种页面类型的 Flux JSON 生成，全部输出对齐 nop-chaos-flux 权威 schema。

## Current Baseline

- **xview.xdef 已支持 body 配置**（commit `1e094b9e0`）：`tab`/`step` 的 `page` 放宽为可选 `string`，且带 `xdef:ref="UiContainerModel" xdef:bean-body-prop="body" xdef:body-type="list"`；新增 `group`（二维网格，columns/gap/autoFlow/alignItems/justifyItems/responsiveColumns）与 `complex`（left/right/top/bottom 槽位）页面类型；`UiPageModel` 增加 `colSpan`/`rowSpan`。`tabModel.body` 解析为 `List<UiContainerModel>`。
- **flux-web 分派现状**：`flux-web/impl_GenPage.xpl` 仅分派 crud/picker/simple/tabs 四类；wizard/group/complex 落入 `GenPageDefault` 抛 `nop.err.web.unknown-page-type`。
- **flux-web page_tabs.xpl（live defect）**：输出 `<tabs j:list="true">` 生成 JSON 字段名 `tabs`，而 Flux `TabsSchema` 读取 `items` 字段——当前 tabs 页面在 Flux 渲染下为 **silent no-op**（tab 内容不渲染）；且 `FluxTabDefaultAttrs` 输出 `name` 字段，Flux `TabsItemSchema` 用 `key`/`value` 定位激活 tab。
- **flux-web GenLayoutTabs（form 级 tabs，live defect）**：`flux-web.xlib:195` 已输出 `<items j:list="true">`（字段名正确），但 tab 项内容写在 `<tab j:list="true">` 子元素生成 `tab:` 字段（非 `body`），且无 `key`——Flux `nestedRegions` 只提取 `items[].body`，form 级 tabs 内容为 **silent no-op**。
- **xview.xdef 的 step 已支持可选 `page` 属性**（`xview.xdef:193` `page="string"`，语义"对应一个表单页面"），step 渲染需按 page > body > name 优先级处理（`name="!string"` 必填，name 兜底恒命中）。
- **Flux schema 权威事实**（nop-chaos-flux 源码）：
  - `TabsSchema` = `{type:'tabs', items: [{key,title,body}]}`；`items[].body` 由编译期 `deepFields.nestedRegions`（`basic-renderer-definitions.ts:459-520`）提取为 region，JSON 直接写 `body: [...]` 数组即可渲染（权威示例：nop-chaos-flux 仓库的 docs/components/tabs/example.json，其 `items[].body` 直接写数组）。
  - `WizardSchema` = `{type:'wizard', steps: [{key,title,body}]}`；`steps[].body` 同样经 nestedRegions 提取（`layout-renderer-definitions.ts:126-210`）。
  - `GridSchema` = `{type:'grid', items: [{body, colSpan, rowSpan}], columns, gap, autoFlow, alignItems, justifyItems, responsiveColumns}`；`items[].body` 经 nestedRegions 提取（`layout-renderer-definitions.ts:296-330`）。与 xview `group` 字段一一对应（autoFlow 枚举值 `row-dense` → `row dense` 需映射；`alignItems/justifyItems` 的 `baseline` 不在 Flux 枚举）。
  - Flux **无** complex（四槽位）容器、**无**页面级 picker schema（`PickerSchema` 仅为表单字段类型）。
- **xjson 输出规则**：xjson 模式下子元素 tagName 在 **OBJECT 上下文**中作为 JSON 属性名（`<tabs j:list="true">` → `tabs: [...]`），在 **LIST 上下文**中成为 `type`（`CollectJObjectHandler.newObject(loc, tagName)`）；`j:key` 可改写属性名（`j:key="items"` 把 `<tabs>` 改写为 `items` 字段）；`<_>` 为匿名对象（不输出 type 字段）。

## Goals

- 修复 `page_tabs.xpl` 的 `tabs` → `items` 字段名 defect，使页面级 tabs 在 Flux 下真正渲染。
- 修复 form 级 `GenLayoutTabs` 缺陷：tab 项内容移入 `body` 字段（当前在 `tab` 字段，Flux 不识别）、补 `key` ← `name`。
- 支持 tab 内嵌 body：`tab` 无 `page` 但设置 `body` 时渲染 body 中的容器节点（crud/simple/tabs/wizard/group），有 `page` 时维持 `LoadPage` 行为，`name` 兜底保持向后兼容。
- 新增 wizard 页面类型：`impl_GenPage.xpl` 分派 wizard，新建 `flux-web/page_wizard.xpl` 输出 Flux `WizardSchema`；step 支持 `page`/`body`/`name` 优先级内容来源（`step.page` → LoadPage，`step.body` → 容器分派）。
- 新增 group 页面类型：`impl_GenPage.xpl` 分派 group，输出 Flux `GridSchema`（body 子容器包为 `items[].body`，透传 colSpan/rowSpan 与网格属性；`responsiveColumns` 暂不输出，`normal`/`baseline` 对齐值过滤）。
- 共享容器分派逻辑抽取为单一内部标签（`GenContainerModel`），页面级与 body 级复用，防止实现漂移。
- 新增/更新 `TestFluxWebGen` 测试覆盖 tab body、wizard step body、group 渲染，输出 JSON 断言对齐 Flux schema 字段名。
- 同步更新 `docs-for-ai/02-core-guides/flux-rendering.md` 与 `layout-syntax-reference.md`。

## Non-Goals

- **不实现 complex 页面类型**（Flux 无对应容器），维持抛 `nop.err.web.unknown-page-type`。
- **不实现页面级 picker**（Flux 无页面级 picker schema），`page_picker.xpl` 保持现状，后续单独评估。
- **不修改 AMIS `web.xlib` / `web/` 模板**（AMIS 侧消费 xview body 另行评估）。
- **不修改 nop-chaos-flux 仓库**（字段名对齐由 flux-web 侧完成）。
- **不改 xview.xdef schema**（body 模型侧已完成，本次仅消费侧）；唯一例外是允许**注释级**补充（complex 未实现说明，与 group 既有注释对齐，不改任何 schema 语义）。

## Scope

### In Scope

- `flux-web.xlib`：新增共享容器分派标签 `GenContainerModel`；修正 `GenLayoutTabs`（tab 项内容移入 `body`、补 `key`）。
- `flux-web/impl_GenPage.xpl`：新增 wizard/group 分派。
- `flux-web/page_tabs.xpl`：`items` 字段名 + tab body 渲染 + key 映射。
- 新建 `flux-web/page_wizard.xpl`：WizardSchema 输出 + step body 渲染。
- 新建 `flux-web/page_group.xpl`：GridSchema 输出 + colSpan/rowSpan 透传。
- `TestFluxWebGen.java` + 测试资源（view.xml/page.yaml）。
- `docs-for-ai/02-core-guides/flux-rendering.md`、`layout-syntax-reference.md`。
- `ai-dev/design/flux-web-container-body-rendering.md`（执行完成后标注状态）。

### Out Of Scope

- complex 页面类型实现。
- 页面级 picker。
- AMIS 侧（web.xlib / web/）任何改动。
- nop-chaos-flux 仓库任何改动。
- xview.xdef 的 schema 调整。

## Execution Plan

### Phase 1 - 共享容器分派标签 + tabs 字段名修复与 tab body 渲染

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`, `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/page_tabs.xpl`, `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxWebGen.java`

- Item Types: `Fix | Proof`

- [x] 在 `flux-web.xlib` 新增内部标签 `GenContainerModel`（outputMode="xjson"，`thisLib` 引用）：接受一个 `UiContainerModel`（含 `type` 属性），**Phase 1 先只分派 `crud`/`simple`/`tabs`**（`wizard`/`group` 分支在 Phase 2/3 扩展并列入对应 Targets），`otherwise` 抛 `nop.err.web.unknown-page-type`。crud 分支复用 `grid_crud.xpl` 的无外壳片段，simple 分支复用 `GenFormBody`（`flux-web.xlib:104-127`，`page_simple.xpl` 即用其渲染 form body）——均不包 page 外壳。页面级与 body 级共用。
- [x] 修正 `page_tabs.xpl`：`<tabs j:list="true">` 改为输出 `items` 字段（`j:key="items"` 或等价写法）；tab 项输出 `key`（来自 tab `name`，保持 title/icon 等现有属性映射）。
- [x] `page_tabs.xpl` tab body 渲染：`tab.page` 存在 → `LoadPage(tab.page)`；无 `page` 但 `tab.body` 非空 → 对 body 中每个 `UiContainerModel` 调用 `GenContainerModel` 收集为数组；无 `page` 无 `body` → 维持 `LoadPage(tab.name)` 兜底（`name` 在 xview.xdef 中必填，兜底恒命中）。**防御性 throw**（page/body/name 三者皆无，合法 schema 下不可达）保留在 `GenContainerModel` 分派与渲染路径中。
- [x] 修正 `flux-web.xlib` 中 `GenLayoutTabs`（form 级 tabs）：tab 项内容从 `<tab j:list="true">` 移入 `body` 字段（`items[].body` 为表单行 JSON 数组），补 `key` ← tab `name`。
- [x] 新增测试资源：`test-flux-tabs.view.xml` + `test-flux-tabs.page.yaml`（页面含 tabs 类型，含一个 page tab、一个 body tab——body 内嵌 simple 或 crud 容器），以及含 `layoutControl="tabs"` 的 form 测试资源。**注意：page tab 引用的页面不能存在同名 `.page.yaml`**（否则 `LoadPage` 返回 `x:extends` 对象而非 Flux JSON，见设计 §3.1 裁定）。
- [x] `TestFluxWebGen.java` 新增测试：
  - page tab 渲染：定位 `type=='tabs'` 节点（页面级输出为 `{type:'page', body:[{type:'tabs', items:[...]}]}`，tabs 节点在 `page.body[0]`，用辅助方法定位而非顶层解析），断言 `items` 数组存在、`items[].key` 与 `name` 一致
  - body tab 渲染：`items[].body` 为数组且内容为内嵌容器的 Flux JSON（如 `type='form'`/`type='crud'`），非 LoadPage 结果
  - 字段名缺陷回归：tabs 节点内断言 `items != null && tabs == null`（不能用 `rg '"tabs"'`——`"type":"tabs"` 必然命中；且顶层恒为 page 外壳，必须在 tabs 节点内断言）
  - form 级 `layoutControl="tabs"`：form body 输出 `items` 字段且 `items[].body` 含表单行 JSON（非 `tab` 字段）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `flux-web.xlib` 存在 `GenContainerModel` 标签且被 `page_tabs.xpl` 在运行时调用（接线验证，见下）
- [x] `page_tabs.xpl` 输出 JSON 的 tabs 节点含 `items` 字段且无 `tabs` 数组属性（`rg 'j:key="items"|items j:list' flux-web.xlib` / `flux-web/page_tabs.xpl` 可观察；测试在定位到的 tabs 节点内断言 `tabs == null`）
- [x] `GenLayoutTabs` 输出 `items[].body`（tab 项内容入 body）且 tab 项含 `key`（form 级 tabs 修复）
- [x] 新增 4 个测试方法全部通过（page tab / body tab / 字段名回归 / form 级 tabs）
- [x] **端到端验证**：`test-flux-tabs.page.yaml`（view.xml → GenPage → Flux JSON）中 body tab 的 `items[].body` 直接包含内嵌容器 JSON 数组，字段结构与 Flux `TabsSchema` 一致
- [x] **接线验证**：body 渲染路径确实调用 `GenContainerModel`（测试断言 body tab 内容来自容器分派而非 `LoadPage`，且无 `type='page'` 外壳）
- [x] **无静默跳过**：`tab.page` 与 `tab.body` 与 `tab.name` 三者皆无的防御性 throw 分支存在且有单元测试（`name` 必填使合法 view.xml 无法构造此场景——以手工构造 model 的方式测试：照 `TestFluxNormalizeAction.java:35-42` 先例用 `XplLibHelper.loadLib(...).getTag(...).invokeWithNamedArgs(XLang.newEvalScope(), args)` 直接调用 `GenContainerModel`，传入无 type 的 `UiContainerModel` 断言抛 `nop.err.web.unknown-page-type`，并在测试注释中说明防御性）
- [x] 相关 `docs-for-ai/02-core-guides/flux-rendering.md` tabs 映射说明已更新（或明确记录 Phase 4 统一更新）——Phase 4 统一更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - wizard 页面类型 + step body 渲染

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/impl_GenPage.xpl`, 新建 `flux-web/` 目录下的 `page_wizard.xpl`, `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`（`GenContainerModel` 扩展 wizard 分支）, `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxWebGen.java`

- Item Types: `Fix | Proof`

- [x] `impl_GenPage.xpl` 新增 `<when test="${pageModel.type == 'wizard'}"><c:include src="page_wizard.xpl"/></when>` 分支。
- [x] 新建 `page_wizard.xpl`：输出 Flux `WizardSchema`，**包 `page` 外壳**（与 crud/simple/tabs 一致：`{type:'page', body:[{type:'wizard', steps:[...]}]}`）。
- [x] wizard 属性映射按设计 §3.3 表执行：`mode`/`actionPrevLabel`/`actionNextLabel`/`actionNextSaveLabel`/`actionFinishLabel` 透传；`startStep` **暂不映射**（Flux 按 0-based 下标实现、xview 为 1-based 模板串，语义错位，见设计 §3.3 与 Non-Blocking Follow-ups）；`className`/`actionClassName`/`initFetch`/`initFetchOn`/`initApi`/`reload`/`redirect`/`target` 丢弃。
- [x] step 渲染（与 Phase 1 tab 同优先级）：`step.page` 存在 → `LoadPage(step.page)`；无 `page` 但 `step.body` 非空 → 对 body 中每个 `UiContainerModel` 调用 `GenContainerModel`（扩展 wizard 分支）收集为 `steps[].body` 数组；两者皆无 → `LoadPage(step.name)` 兜底（name 必填恒命中）。`steps[].key` ← step `name`。
- [x] `GenContainerModel` 增加 `wizard` 分派分支（调用 `page_wizard.xpl` 的核心片段，不包 page 外壳）。
- [x] 新增测试资源：`test-flux-wizard.view.xml` + `test-flux-wizard.page.yaml`（wizard 页面，step 内嵌 body 容器：一个 step 内嵌 simple form，一个 step 内嵌 crud；**step page 引用的页面不能有同名 `.page.yaml`**）。
- [x] `TestFluxWebGen.java` 新增测试：输出 JSON `type='page'` 外壳 → body 内 `type='wizard'`、`steps` 数组存在、`steps[].body` 为内嵌容器 JSON 数组、`steps[].key` 与 `name` 一致；含 `step page` 的 wizard 测试断言 `steps[].body` 为 LoadPage 结果（无同名 page.yaml 时）。

Exit Criteria:

- [x] `impl_GenPage.xpl` 存在 wizard 分派分支（`rg 'wizard' impl_GenPage.xpl` 可观察）
- [x] `page_wizard.xpl` 存在且被 wizard 分派分支 include（接线验证：页面加载时无 `unknown-page-type` 异常）
- [x] 新增 wizard 测试通过（含 `steps[].body` 数组断言、`step.page` 分支断言）
- [x] **端到端验证**：`test-flux-wizard.page.yaml` → Flux JSON 的 `steps[].body` 与 `WizardStepSchema.body`（SchemaInput 数组）一致，无多余 `type='page'` 嵌套（仅外层一个 page 外壳）
- [x] **无静默跳过**：`step.body` 为空且无 `page` 时输出 LoadPage(name) 结果（name 必填，恒有兜底）；`GenContainerModel` 的 wizard 分支被 step body 渲染实际调用（测试断言）
- [x] 相关 `docs-for-ai/02-core-guides/flux-rendering.md` wizard 映射说明已更新（或明确记录 Phase 4 统一更新）——Phase 4 统一更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - group 页面类型（Flux GridSchema）

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/impl_GenPage.xpl`, 新建 `flux-web/` 目录下的 `page_group.xpl`, `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`（`GenContainerModel` 扩展 group 分支）, `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxWebGen.java`

- Item Types: `Fix | Proof`

- [x] `impl_GenPage.xpl` 新增 `<when test="${pageModel.type == 'group'}"><c:include src="page_group.xpl"/></when>` 分支。
- [x] 新建 `page_group.xpl`：输出 Flux `GridSchema`（`type='grid'`），**包 `page` 外壳**（`{type:'page', body:[{type:'grid', items:[...]}]}`）。
- [x] 属性映射按设计 §3.4 表执行：`columns`/`gap` 透传；`autoFlow` 枚举映射（`row-dense`→`row dense`、`column-dense`→`column dense`）；`alignItems`/`justifyItems` **过滤 `normal` 与 `baseline`**（均不在 Flux 枚举，其余透传）；**`responsiveColumns` 暂不输出**（xview string 与 Flux 对象 `{sm,md,lg}` 类型不匹配，转换规则未定义，透传会被 Flux 静默回退到基础 columns）。
- [x] body 渲染：group 的每个 body 子容器包为 `items[].body`（数组），子容器 `colSpan`/`rowSpan` 透传为 `items[].colSpan/rowSpan`（仅继承 `UiPageModel` 的容器 crud/tabs 支持 span；simple/wizard/group 容器可入 body 但无 span 字段），`items[].key` 来自子容器 `name`（Flux 运行时 React key 契约，非 GridItemSchema 声明字段）。
- [x] `GenContainerModel` 增加 `group` 分派分支（调用 `page_group.xpl` 的核心片段，不包 page 外壳）。
- [x] 新增测试资源：`test-flux-group.view.xml` + `test-flux-group.page.yaml`（group 页面，body 含 2 个子容器，一个带 colSpan/rowSpan）。
- [x] `TestFluxWebGen.java` 新增测试：输出 JSON 含 `type='grid'`、`items` 数组、`items[].body` 内嵌容器、`items[].colSpan/rowSpan` 透传、`columns`/`gap`/`autoFlow` 映射正确。

Exit Criteria:

- [x] `impl_GenPage.xpl` 存在 group 分派分支（`rg 'group' impl_GenPage.xpl` 可观察）
- [x] `page_group.xpl` 存在且被 group 分派分支 include
- [x] 新增 group 测试通过（含 colSpan/rowSpan 与 autoFlow 映射断言）
- [x] **端到端验证**：`test-flux-group.page.yaml` → Flux JSON `type='grid'` 的 `items[].body` 与 `GridItemSchema.body` 一致；根节点仅一个 `page` 外壳
- [x] **无静默跳过**：`alignItems='baseline'` 与 `alignItems='normal'` 均被过滤不输出（测试断言），其余合法值透传；`responsiveColumns` 配置了也不输出（测试断言无该字段）；无 body 子容器时 `items` 不输出或输出空数组（与 Flux `GridSchema.items?` 可空契约一致）
- [x] 相关 `docs-for-ai/02-core-guides/flux-rendering.md` group 映射说明已更新（或明确记录 Phase 4 统一更新）——Phase 4 统一更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 文档同步与设计文档状态更新

Status: completed
Targets: `docs-for-ai/02-core-guides/flux-rendering.md`, `docs-for-ai/02-core-guides/layout-syntax-reference.md`, `ai-dev/design/flux-web-container-body-rendering.md`

- Item Types: `Fix | Proof`

- [x] `flux-rendering.md` 更新：新增 tabs/wizard/group 的 Flux JSON 映射说明（`items`/`steps`/`grid` 字段名、`body` 数组直接书写、key 映射规则）；移除或修正"tabs 字段"旧表述（如存在）。
- [x] `layout-syntax-reference.md` 更新：tabs/wizard/group 语法说明与 body 内嵌示例；`page` vs `body` 优先级说明。
- [x] `ai-dev/design/flux-web-container-body-rendering.md` 状态保持/确认 `active`（若执行中有设计调整，同步修正设计文档；design 只记录最终状态）。
- [x] `docs-for-ai/INDEX.md` / `docs-for-ai/04-reference/source-anchors.md` 检查是否需要新增锚点（如新增 xpl 标签），需要则更新。
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` 确认 0 错误。

Exit Criteria:

- [x] `flux-rendering.md` 含 tabs/wizard/group 的 Flux JSON 映射说明，且与实际输出 JSON 一致（抽查测试输出）
- [x] `layout-syntax-reference.md` 含 body 内嵌示例与 page/body 优先级说明
- [x] 设计文档状态为 `active` 且内容与实现一致
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（无新增错误；pre-existing 错误需注明）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `page_tabs.xpl`/`GenLayoutTabs` 输出 `items` 字段（Flux `TabsSchema`），无 `tabs` 字段残留
- [x] tab/step/group body 渲染全部落地且输出与 nop-chaos-flux `TabsSchema`/`WizardSchema`/`GridSchema` 一致
- [x] wizard/group 页面类型分派落地，不再抛 `nop.err.web.unknown-page-type`
- [x] complex 维持显式抛错（未被静默降级为 no-op）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect（尤其 `tabs` 字段名 defect）
- [x] `TestFluxWebGen` 新增测试全部通过（body tab / page tab / wizard / group / 字段名回归）
- [x] 受影响的 owner docs（`flux-rendering.md`、`layout-syntax-reference.md`）已同步，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`GenContainerModel` 在运行时被 page_tabs/page_wizard/page_group 调用（非仅存在），（b）端到端 view.xml → Flux JSON 路径完整，（c）无空方法体/静默跳过
- [x] `./mvnw test -pl nop-frontend-support/nop-web -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 不引入新错误
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high` 退出码为 0（注意：模块在 `nop-frontend-support/` 下，`--module nop-web` 会解析到不存在的目录导致真空绿）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/323-flux-web-container-body-rendering.md --strict` 退出码为 0

## Deferred But Adjudicated

### complex 页面类型

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Flux（nop-chaos-flux）无 left/right/top/bottom 四槽位容器 schema，拼装方案无 Flux 侧验证路径；xview.xdef schema 已就绪，独立特性后续评估。
- Successor Required: yes
- Successor Path: 后续独立 plan（schema 已就绪，仅需消费侧）

### 页面级 picker

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Flux 无页面级 picker schema（`PickerSchema` 仅为表单字段类型），现有 `page_picker.xpl` 输出为 AMIS 遗留，与本次 body 渲染无耦合。
- Successor Required: yes
- Successor Path: 后续独立 plan 或 Flux 端 picker 页面 schema 落地后

### AMIS 侧消费 xview body 配置

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 用户明确本计划仅实现 flux-web 侧；AMIS 渲染器（web.xlib）为独立消费路径。
- Successor Required: no

## Non-Blocking Follow-ups

- `responsiveColumns` 的 xview string → Flux 对象 `{sm,md,lg}` 转换规则（本次暂不输出）
- xview `startStep`（1-based 模板串）→ Flux `value`（0-based index 实现，schema 注释自称 1-based——Flux 注释与实现矛盾）的语义转换规则（本次暂不映射）
- `FluxTabDefaultAttrs` 的 `hash`/`reload`/`closable` 等属性在 Flux `TabsItemSchema` 中的对齐情况核查（本次仅保证 key/title/body 核心契约）
- `FluxTabsDefaultAttrs` 的 `tabsMode` 枚举值（Flux `TabsMode` 含 `chrome`/`tiled`/`sidebar` 等）与 xview 现有取值集合的差距核查
- xview wizard 的 `reload`/`redirect`/`target` 到 Flux `onChange`/`onStepCommit`/`onComplete` 事件映射（本次丢弃）
- LoadPage 的 `.page.yaml` 分支（`x:extends` 对象）在 Flux 下的消费语义（服务端解析或禁用该路径）

## Closure

Status Note: 4 个 Phase 全部 completed、Closure Gates 全勾选；独立 closure audit verdict APPROVE（无阻止项）；两个 live defect（page_tabs `tabs` 字段名、GenLayoutTabs tab 内容未入 body）均已修复并有回归测试；wizard/group 页面类型落地；complex/picker 等 deferred 项分类诚实（out-of-scope）。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_046b3d7b4ffeL8PTP50RNw4YaW`，research-only）
- Evidence:
  - Phase 1 exit criteria 全 PASS：`GenContainerModel` 标签存在（`flux-web.xlib:33-64`，otherwise 抛 `nop.err.web.unknown-page-type`）且运行时被 `container_tabs.xpl:15`/`container_wizard.xpl:23`/`container_group.xpl:35` 调用（接线验证）；`page_tabs.xpl`/`container_tabs.xpl` 输出 `items` 字段且无 `tabs` 数组字段（测试 `TestFluxWebGen.testPageTabsEmitsItemsFieldWithKey`）；`GenLayoutTabs` 输出 `items[].body` + `key`（`flux-web.xlib:233-247`）；`FluxTabDefaultAttrs` 补 `key` ← name（`flux-web.xlib:998`）；4 个新测试方法 + 防御性 throw 测试全部通过
  - Phase 2 exit criteria 全 PASS：wizard 分派（`impl_GenPage.xpl:31-33`）、`page_wizard.xpl` + `container_wizard.xpl`、step 优先级 page>body>name（`container_wizard.xpl:14-32`）、属性映射符合设计 §3.3（测试断言 startStep/className/initFetch/reload 均不输出）
  - Phase 3 exit criteria 全 PASS：group 分派（`impl_GenPage.xpl:34-36`）、`page_group.xpl` + `container_group.xpl`、GridSchema 映射符合设计 §3.4（autoFlow 枚举映射、normal/baseline 过滤、responsiveColumns 不输出、colSpan/rowSpan 经 `prop_allow` 守卫透传、items[].key ← name；测试 `testGroupPageEmitsGridSchema`）
  - Phase 4 exit criteria 全 PASS：`flux-rendering.md`/`layout-syntax-reference.md`/`source-anchors.md` EXT-009 已同步并与实际输出一致；design doc 状态 active；`check-doc-links.mjs --strict` 0 errors
  - Closure Gates：`./mvnw test -pl nop-frontend-support/nop-web -am` 63 例 0 failures（含 1 个既有 @Disabled skip）；`scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high` exit 0（0 findings）；`check-plan-checklist.mjs ... --strict` exit 0（全项勾选 + Closure Evidence 写入后复跑）
  - Anti-Hollow 检查：端到端调用链 page_tabs.xpl → container_tabs.xpl → GenContainerModel → container_simple/container_crud 连通（audit 逐文件追踪 + 端到端测试断言）；无空方法体/静默跳过（GenContainerModel otherwise 显式 throw，complex 维持 GenPageDefault throw）
  - Deferred 项分类检查：complex（Flux 无四槽位容器，design §4.4）、页面级 picker（Flux PickerSchema 仅表单字段类型）、AMIS 侧（用户 scope 裁定）均为 out-of-scope improvement；Non-Blocking Follow-ups（responsiveColumns/startStep/属性对齐核查）为优化/定义项——无 in-scope live defect 被降级
  - 审计非阻塞注记已处理：全模块测试复跑 63 例绿（gate 满足）；`TestFluxWebGen` 注释位置 cosmetic 修正；日志与 commit 见 `ai-dev/logs/2026/08-01.md`

Follow-up:

- `responsiveColumns` xview string → Flux `{sm,md,lg}` 转换规则（后续 plan 定义）
- `startStep`（1-based 模板串）→ Flux 0-based index 转换（Flux 注释与实现矛盾，需 Flux 侧先裁定）
- `FluxTabDefaultAttrs` 的 hash/reload/closable 等在 Flux `TabsItemSchema` 的对齐核查（watch-only）
- LoadPage 的 `.page.yaml` 分支在 Flux 下的消费语义（服务端解析或禁用该路径）
- no remaining plan-owned work
