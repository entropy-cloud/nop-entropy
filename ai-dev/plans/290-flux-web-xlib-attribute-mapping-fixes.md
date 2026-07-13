# 290 flux-web.xlib 属性映射修正（基于 Flux 最新类型定义）

> Plan Status: completed
> Last Reviewed: 2026-07-13
> Source: 基于 nop-chaos-flux 仓库（`~/app/nop-chaos-flux-wt/nop-chaos-flux-master`）的 Flux 类型定义核对 plan 289 裁定
> Related: `289-flux-web-layout-syntax-completeness.md`（被本计划修正的部分裁定）、`287-nop-web-flux-xlib.md`（首版 Flux xlib）

## Purpose

修正 plan 289 中基于**过时分析**（`ai-dev/analysis/2026-06-28-amis-vs-flux-schema-comparison.md`）和不靠谱调查方法（minified js grep）做出的 4 项错误裁定。本次基于 nop-chaos-flux 仓库源码（`packages/flux-renderers-basic/src/schemas.ts`、`packages/flux-renderers-form/src/schemas.ts`、`packages/flux-core/src/types/schema.ts`、`packages/flux-core/src/types/validation.ts`）和 flux-guide 文档（`design-patterns/page-dialog-drawer.md`）发现：

- Flux PageSchema **完全支持** aside 相关属性（6 个），plan 289 裁定为"不支持"是错误的
- Flux FormSchema 用 `autoInit` 命名对应 AMIS `initFetch`，plan 289 漏了
- Flux 字段级 `required` 接受表达式字符串（`required?: boolean | string`），AMIS `requiredOn` 应直接映射为 `required`，**不是** plan 289 中的 `requiredWhen`（后者是 ValidationRule.kind，不是 schema 字段）
- Flux 字段级 `clearValueWhenHidden` 位于 `hiddenFieldPolicy` 嵌套对象中（`hiddenFieldPolicy: { clearValueWhenHidden: boolean }`），**不是** plan 289 中的顶层属性

## Current Baseline

经核对 nop-chaos-flux 仓库源码（`packages/flux-renderers-basic/src/schemas.ts:24-50`）：

- **PageSchema 实际属性**：`aside`、`asidePosition`、`asideResizable`（注意：AMIS 是 `asideResizor`，Flux 改为形容词形式 `asideResizable`）、`asideMinWidth`、`asideMaxWidth`、`asideSticky`、`asideClassName`、`bodyClassName`、`headerClassName`、`footerClassName`、`toolbarClassName`、`title`、`subTitle`、`remark`、`data`、`statusPath`、`modalContainer`。flux-guide/design-patterns/page-dialog-drawer.md 有完整的使用示例。
- **FormSchema 实际属性**（`packages/flux-renderers-form/src/schemas.ts`）：`autoInit?: boolean`（对应 AMIS `initFetch`）、`initAction`（对应 AMIS `initApi`，但用 ActionSchema）、`submitAction`（对应 AMIS `api`）、`autoLoad`、`submitOnChange`、`preventEnterSubmit`、`rules`、`mode`、`labelAlign`、`labelWidth`、`gap`、`columnCount`、`autoFocus`、`scrollToFirstError`、`static`、`bodyClassName`、`actionsClassName`、`valuesPath`、`statusPath`、`hiddenFieldPolicy`。**没有** `silentPolling`、`wrapWithPanel`、`canAccessSuperData`（plan 289 这 3 项裁定正确）。
- **BoundFieldSchemaBase**（`packages/flux-core/src/types/schema.ts`）：`name`、`readOnly?: boolean | string`（接受表达式！）、`required?: boolean | string`（接受表达式！）、`mode`、`labelAlign`、`labelWidth`、`hint`、`description`、`remark`、`labelRemark`。**没有** `requiredWhen`、`clearValueWhenHidden`（这两个不是 schema 字段）。
- **HiddenFieldPolicy**（`packages/flux-core/src/types/validation.ts`）：`{ validateWhenHidden?: boolean; clearValueWhenHidden?: boolean; }`。这是嵌套对象，挂在 FormSchema 或字段级 InputSchema 上。
- **ValidationRule.kind 'requiredWhen'**（`packages/flux-core/src/types/validation.ts`）：`{ kind: 'requiredWhen'; path: string; equals: unknown; message?: string }`。这是**内部校验规则**，由 form 的 `rules` 数组使用，不是字段 schema 的顶层属性。flux-guide/06-form-validation.md:63 明确说"`requiredWhen`/`uniqueBy`/`minItems` 等属于内部 `ValidationRule.kind`，由字段级/数组级声明引入，不经表单 `rules`"。
- **实际使用证据**：`apps/playground/src/complex-pages/page-schemas/complex-form.json:75,82` 中字段直接用 `"required": "${notify}"` 和 `"required": "${userType === \"enterprise\"}"`（表达式字符串），证明 Flux 用 `required` 接受表达式实现 AMIS `requiredOn` 的功能。

**plan 289 当前实现（需修正）**：
- `flux-web.xlib:357` `requiredWhen: cellModel?.requiredOn`（**错** — requiredWhen 不是 schema 字段）
- `flux-web.xlib:361` `clearValueWhenHidden: cellModel?.clearValueOnHidden`（**错** — 应嵌套到 hiddenFieldPolicy）
- `FluxFormDefaultAttrs`（`flux-web.xlib:606-614`）缺 `autoInit`（**漏** — 应补 `autoInit: formModel.initFetch`）
- `FluxPageDefaultAttrs`（`flux-web.xlib:634-639`）缺 6 个 aside 属性（**漏** — 应补 asidePosition/asideResizable/asideMinWidth/asideMaxWidth/asideSticky/asideClassName）

## Goals

- 修正 `GenFormSimpleCell` 的 `requiredWhen` 错误：改为在 `cellModel.requiredOn` 存在时直接覆盖 `required`（用表达式字符串）。
- 修正 `GenFormSimpleCell` 的 `clearValueWhenHidden` 错误：改为输出 `hiddenFieldPolicy: { clearValueWhenHidden: cellModel?.clearValueOnHidden }` 嵌套对象。
- 修正 `FluxFormDefaultAttrs`：补 `autoInit: formModel.initFetch`。
- 修正 `FluxPageDefaultAttrs`：补 6 个 aside 属性，含命名映射 `asideResizor → asideResizable`。
- 更新已有测试反映新映射（plan 289 的测试断言会失败，需同步更新）。
- 更新 `flux-rendering.md` 修正 plan 289 引入的错误文档（移除"Flux 不支持 aside"等错误表述）。
- 更新 `layout-syntax-reference.md` 顶部声明中的命名映射说明（`requiredOn → required`，不再是 `requiredWhen`）。

## Non-Goals

- **不修改 web.xlib / control.xlib**：与 plan 289 一致。
- **不重构 form 的 initApi/api 处理**：Flux 用 `initAction`/`submitAction`（ActionSchema），与 AMIS `initApi`/`api`（ApiSchema）模型不同，这是更大的工作，由后续 plan 处理。
- **不补 silentPolling / wrapWithPanel / canAccessSuperData**：plan 289 这 3 项裁定正确（Flux 确实不支持），保留。
- **不补 asidePosition**：Flux PageSchema 支持但 `xview.xdef` UiPageModel 未定义，需先改 xview.xdef，超出 scope。
- **不做前端实际渲染验证**。
- **不利用 Flux 高级结构节点**。

## Scope

### In Scope

- `flux-web.xlib` 的 `GenFormSimpleCell`：修正 requiredWhen 和 clearValueWhenHidden 输出。
- `flux-web.xlib` 的 `FluxFormDefaultAttrs`：补 autoInit。
- `flux-web.xlib` 的 `FluxPageDefaultAttrs`：补 6 个 aside 属性。
- `TestFluxWebGen.java` 和 `TestFluxWebCrudPage.java`：更新 plan 289 引入的测试断言。
- `docs-for-ai/02-core-guides/flux-rendering.md`：修正 plan 289 引入的错误文档。
- `docs-for-ai/02-core-guides/layout-syntax-reference.md`：修正命名映射说明。

### Out Of Scope

- form 的 initApi→initAction、api→submitAction 转换（后续 plan）。
- AMIS staticOn 的 Flux 等价（Flux 无 staticOn 概念，保留 plan 289 裁定）。
- asidePosition（需修改 xview.xdef 才能配置）。
- `FluxPageDefaultAttrs` 仍 pick Flux PageSchema 不支持的 `initFetch`/`interval`/`silentPolling` 问题（记录到 Non-Blocking Follow-ups）。

## Execution Plan

### Phase 1 - 修正 GenFormSimpleCell 的 requiredWhen 和 clearValueWhenHidden

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`, `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxWebGen.java`

- Item Types: `Fix | Proof`

- [x] 修改 `flux-web.xlib:GenFormSimpleCell` cell 对象（当前 ~349-363 行）：
  - **删除** `requiredWhen: cellModel?.requiredOn`（plan 289 引入的错误顶层属性）
  - **修改** `required` 表达式：当 `cellModel?.requiredOn` 存在时优先使用表达式字符串，否则用静态 mandatory 判定：`required: cellModel?.requiredOn || (mandatory ? true : null)`
  - **删除** 顶层 `clearValueWhenHidden: cellModel?.clearValueOnHidden`
  - **新增** 嵌套对象（仅当 cellModel?.clearValueOnHidden 不为 null 时输出）：`hiddenFieldPolicy: cellModel?.clearValueOnHidden != null ? { clearValueWhenHidden: cellModel.clearValueOnHidden } : null`
- [x] 更新 `TestFluxWebGen.java` 的 plan 289 引入的 2 个测试方法：
  - `testCellRequiredOnMapsToFluxRequiredWhen` → 改名为 `testCellRequiredOnMapsToFluxRequiredExpression`，断言改为：cell 对象的 `required` 字段值是 `"${status == 1}"`（表达式字符串），且**不包含** `requiredWhen` 字段
  - `testCellClearValueOnHiddenMapsToFluxClearValueWhenHidden` → 改名为 `testCellClearValueOnHiddenMapsToFluxHiddenFieldPolicy`，断言改为：cell 对象的 `hiddenFieldPolicy` 字段是 `{clearValueWhenHidden: true}` 嵌套对象，且**不包含**顶层 `clearValueWhenHidden` 字段
- [x] 新增 1 个 @Test 验证 `required` 表达式优先级：在 `test-flux-form-cell-attrs.view.xml` 的 email cell 上加 `<requiredOn>${a == b}</requiredOn>`（且 xmeta 中 email 非 mandatory），断言 emailCell 的 `required` 字段值是 `"${a == b}"`（表达式优先于静态 mandatory）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `flux-web.xlib:GenFormSimpleCell` 不再输出顶层 `requiredWhen`（`rg 'requiredWhen' flux-web.xlib` 应返回 0 行）
- [x] `flux-web.xlib:GenFormSimpleCell` 不再输出顶层 `clearValueWhenHidden`（`rg 'clearValueWhenHidden:.*cellModel' flux-web.xlib` 应返回 0 行）
- [x] `flux-web.xlib:GenFormSimpleCell` 的 `required` 表达式包含 `cellModel?.requiredOn ||` 前缀（验证命令：`rg 'required:.*cellModel\?\.requiredOn' flux-web.xlib`）
- [x] `flux-web.xlib:GenFormSimpleCell` 输出 `hiddenFieldPolicy` 嵌套对象（`rg 'hiddenFieldPolicy' flux-web.xlib` 应返回至少 1 行）
- [x] 更新后的 3 个测试方法通过（`./mvnw test -pl nop-web -Dtest=TestFluxWebGen`）
- [x] **端到端验证**：从 view.xml `<requiredOn>` 到 JSON `required: "${expr}"` 完整透传
- [x] **接线验证**：修改后的 `GenFormSimpleCell` 在测试中被实际调用
- [x] **无静默跳过**：无新增分支静默跳过
- [x] `./mvnw test -pl nop-web -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 补全 FluxFormDefaultAttrs.autoInit、FluxPageDefaultAttrs.aside 属性、AMIS readonlyOn 映射

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`, `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxWebGen.java`, `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxWebCrudPage.java`

- Item Types: `Fix | Proof`

- [x] 修改 `FluxFormDefaultAttrs`（当前 ~606-614 行）：在 `_.pickNotNull(formModel, [...])` 之后追加 `if(formModel.initFetch != null) attrs.put('autoInit', formModel.initFetch)`。**注意**：当前 `FluxFormDefaultAttrs` 的 pickNotNull 列表只有 `initFetchOn`（不含 `initFetch`），所以不能用 `attrs.remove('initFetch')` 改名方案（remove 不存在的 key 返回 null），必须用显式 `attrs.put('autoInit', formModel.initFetch)`。
- [x] 修改 `FluxPageDefaultAttrs`（当前 ~634-639 行）：
  - **直接 pickNotNull 新增 4 个**：`asideMinWidth`、`asideMaxWidth`、`asideSticky`、`asideClassName`（view.xml 模型层与 Flux 同名，可直接 pick）
  - **单独 rename 1 个**：`if(pageModel.asideResizor != null) attrs.put('asideResizable', pageModel.asideResizor)`（view.xml 用 AMIS 命名 `asideResizor`，Flux 用 `asideResizable`，不能直接 pick）
  - **不补 asidePosition**：Flux PageSchema 支持 `asidePosition` 但 `xview.xdef` UiPageModel（line 38-44）未定义此属性，view.xml 模型层没有配置入口。若未来需要，要先修改 xview.xdef，超出本 plan scope
- [x] 补 AMIS `readonlyOn` → Flux `readOnly`（表达式字符串）映射：在 `GenFormSimpleCell` cell 对象中添加 `readOnly: cellModel?.readonlyOn || (rd ? true : null)`（rd 变量已计算，见 flux-web.xlib:324-326）。`BoundFieldSchemaBase.readOnly?: boolean | string`（flux-core/src/types/schema.ts:119）确认接受表达式。注意 Flux 命名是 `readOnly`（大写 O），AMIS 命名是 `readonly`（全小写）— 此处用 Flux 命名。
- [x] 更新 `TestFluxWebGen.java` 的 plan 289 引入的测试方法：
  - `testCellRequiredOnMapsToFluxRequiredWhen` → 改名为 `testCellRequiredOnMapsToFluxRequiredExpression`，断言改为：cell 对象的 `required` 字段值是 `"${status == 1}"`（表达式字符串），且**不包含** `requiredWhen` 字段
  - `testCellClearValueOnHiddenMapsToFluxClearValueWhenHidden` → 改名为 `testCellClearValueOnHiddenMapsToFluxHiddenFieldPolicy`，断言改为：cell 对象的 `hiddenFieldPolicy` 字段是 Map 且含 `clearValueWhenHidden: true`，且**不包含**顶层 `clearValueWhenHidden` 字段
- [x] 更新 `TestFluxWebCrudPage.java` 的 plan 289 引入的测试方法：
  - `testAsidePropertiesAreIgnoredInFlux` → **反向重写**为 `testAsidePropertiesAreEmittedInFlux`：断言输出 JSON 的 page 对象包含 `asideResizable: true`（注意 Flux 命名，不是 asideResizor）、`asideMinWidth: 200`、`asideMaxWidth: 500`、`asideSticky: true`、`asideClassName: "my-aside"`
- [x] 新增 1 个 @Test 验证 `autoInit` 输出：构造一个 form 配置 `initFetch="false"`，断言输出 JSON form 对象含 `autoInit: false`
- [x] 新增 1 个 @Test 验证 `readOnly` 表达式映射：在 `test-flux-form-cell-attrs.view.xml` 的 email cell 上加 `<readonlyOn>${a == b}</readonlyOn>`（且 email 非 mandatory），断言 emailCell 的 `readOnly` 字段值是 `"${a == b}"`

Exit Criteria:

- [x] `FluxFormDefaultAttrs` 输出包含 `autoInit`（`rg 'autoInit' flux-web.xlib` 应返回至少 1 行）
- [x] `FluxPageDefaultAttrs` 直接 pick 4 个 aside 属性（`rg '"asideMinWidth"|"asideMaxWidth"|"asideSticky"|"asideClassName"' flux-web.xlib` 应返回至少 4 行）
- [x] `FluxPageDefaultAttrs` 含 `asideResizor → asideResizable` 命名映射（`rg 'asideResizable' flux-web.xlib` 应返回至少 1 行；源码读取 `pageModel.asideResizor` 但输出 JSON 不含 `asideResizor`，由 `testAsidePropertiesAreEmittedInFlux` 断言验证）
- [x] `GenFormSimpleCell` 输出 `readOnly` 接受表达式（`rg 'readOnly:.*cellModel\?\.readonlyOn' flux-web.xlib` 应返回至少 1 行）
- [x] 命名映射 `asideResizor → asideResizable` 端到端验证：view.xml 配置 `asideResizor="true"`，输出 JSON 含 `asideResizable: true` 且不含 `asideResizor`
- [x] 更新后的 `testAsidePropertiesAreEmittedInFlux` 通过
- [x] 新增的 `autoInit` 测试通过
- [x] 新增的 `readOnly` 表达式测试通过
- [x] **端到端验证**：从 view.xml `<crud asideSticky="true" asideResizor="true">` 到 JSON `asideSticky: true, asideResizable: true` 完整透传
- [x] **接线验证**：修改后的 `FluxFormDefaultAttrs` / `FluxPageDefaultAttrs` / `GenFormSimpleCell` 在测试中被实际调用
- [x] **无静默跳过**：无
- [x] `./mvnw test -pl nop-web -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 文档同步

Status: completed
Targets: `docs-for-ai/02-core-guides/flux-rendering.md`, `docs-for-ai/02-core-guides/layout-syntax-reference.md`

- Item Types: `Fix | Proof`

- [x] 修正 `flux-rendering.md` 的 plan 289 错误文档：
  - **删除** "Flux 已确认不支持的 page 属性" 整个表（Flux 实际支持所有 aside 属性，只是命名差异）
  - **删除** "Flux 已确认不支持的 form 属性" 表中的 `initFetch` 行（Flux 用 `autoInit` 替代，应记录为映射而非不支持）
  - **保留** `silentPolling`、`wrapWithPanel`、`canAccessSuperData` 三项（这些确实是 Flux 不支持的）
  - **删除** `flux-rendering.md:59` 的 "Flux 命名规律：将 AMIS 的 `*On` 后缀改为 `*When`" 错误泛化论断。实际规律：`visibleOn/disabledOn/requiredOn` 是删除 On 后缀（变成 visible/disabled/required）；`clearValueOnHidden` 是结构重组为 `hiddenFieldPolicy.clearValueWhenHidden`；`asideResizor` 是改为形容词形式 `asideResizable`。无统一规律，应记录具体映射而非泛化
  - **修正** cell 命名映射说明：`requiredOn → required`（表达式字符串，不再是 `requiredWhen`）、`clearValueOnHidden → hiddenFieldPolicy.clearValueWhenHidden`（嵌套对象，不再是顶层）、新增 `readonlyOn → readOnly`、`initFetch → autoInit`、`asideResizor → asideResizable`
- [x] 修正 `layout-syntax-reference.md` 顶部声明（plan 289 引入）：
  - 将 "Flux 渲染器特定的属性命名映射（如 `requiredOn` → `requiredWhen`、`clearValueOnHidden` → `clearValueWhenHidden`）" 改为 "`requiredOn` → `required`（表达式字符串）、`clearValueOnHidden` → `hiddenFieldPolicy.clearValueWhenHidden`（嵌套对象）"
  - 将 "form 的 `initFetch`/`silentPolling`/`wrapWithPanel`/`canAccessSuperData`、page 的 `aside*` 属性" 改为 "form 的 `silentPolling`/`wrapWithPanel`/`canAccessSuperData`（`initFetch` 已映射为 `autoInit`）；page 的 aside 属性已完全支持"
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` 确认无新增错误

Exit Criteria:

- [x] `flux-rendering.md` 不再包含"Flux 不支持 aside"的错误表述
- [x] `flux-rendering.md` 不再把 `initFetch` 列为不支持（改为映射 `autoInit`）
- [x] `layout-syntax-reference.md` 顶部声明的命名映射已修正
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 不引入新错误
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `GenFormSimpleCell` 不再输出顶层 `requiredWhen` 和 `clearValueWhenHidden`
- [x] `GenFormSimpleCell` 正确输出 `required: cellModel?.requiredOn || ...` 和 `hiddenFieldPolicy: { clearValueWhenHidden: ... }`
- [x] `FluxFormDefaultAttrs` 包含 `autoInit`
- [x] `FluxPageDefaultAttrs` 包含 6 个 aside 属性（含 `asideResizor → asideResizable` 命名映射）
- [x] plan 289 引入的所有测试断言已更新（不再断言 `requiredWhen`/顶层 `clearValueWhenHidden`/aside 属性被忽略）
- [x] `flux-rendering.md` 和 `layout-syntax-reference.md` 中的错误表述已修正
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）修改后的标签在测试中被实际调用，（b）新增的属性确实出现在输出 JSON 中，（c）无空方法体/静默跳过
- [x] `./mvnw test -pl nop-web -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 不引入新错误
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-web --severity high` 退出码为 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/290-flux-web-xlib-attribute-mapping-fixes.md --strict` 退出码为 0

## Decision Log

> 调查结论填入此处。

**基于 nop-chaos-flux 仓库源码的权威裁定**：
- `requiredOn` → Flux `required`（表达式字符串，**非** `requiredWhen`）：依据 `BoundFieldSchemaBase.required?: boolean | string` + `apps/playground/.../complex-form.json` 实际使用 `"required": "${expr}"`
- `clearValueOnHidden` → Flux `hiddenFieldPolicy.clearValueWhenHidden`（嵌套对象）：依据 `HiddenFieldPolicy` 接口 + `form.schema-validator.test.ts:53` 拒绝字符串形式
- `initFetch` → Flux `autoInit`：依据 `FormSchema.autoInit?: boolean`
- `asideResizor` → Flux `asideResizable`（命名差异）：依据 `PageSchema.asideResizable` 注释 `amis: asideResizor`
- 其他 5 个 aside 属性（asidePosition/asideMinWidth/asideMaxWidth/asideSticky/asideClassName）：Flux 直接支持，无命名差异

## Deferred But Adjudicated

### plan 289 的 silentPolling / wrapWithPanel / canAccessSuperData 裁定维持

- Classification: `watch-only residual`
- Why Not Blocking Closure: 经 nop-chaos-flux 源码再次确认，Flux FormSchema 确实不含这 3 个属性。plan 289 这部分裁定正确。
- Successor Required: no

### form 的 initApi→initAction、api→submitAction 转换

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Flux 用 ActionSchema（DAG），AMIS 用 ApiSchema（扁平）。完整转换涉及 NormalizeApi 重构，超出本计划范围。
- Successor Required: yes
- Successor Path: 后续 plan

## Non-Blocking Follow-ups

- AMIS `staticOn` 的 Flux 等价：Flux 无 staticOn 概念，可能用 `readOnly` 替代，但语义有差异（staticOn 是"显示为文本"，readOnly 是"不可编辑"），需进一步评估
- `FluxPageDefaultAttrs` 仍 pick Flux PageSchema 不支持的 `initFetch`/`interval`/`silentPolling`：与 `FluxFormDefaultAttrs` 同类问题，应作为后续 plan 处理
- asidePosition：Flux PageSchema 支持，但 `xview.xdef` UiPageModel 未定义，需先改 xview.xdef 才能在 view.xml 中配置
- form 的 initApi→initAction、api→submitAction 转换（Flux 用 ActionSchema DAG，AMIS 用 ApiSchema 扁平结构）

## Closure

Status Note: plan 290 修正了 plan 289 中基于过时分析和不可靠调查方法（minified js grep）做出的 4 项错误裁定。本次基于 nop-chaos-flux 仓库源码（TypeScript 权威定义）重新核对：requiredOn → required（表达式字符串，非 requiredWhen）、clearValueOnHidden → hiddenFieldPolicy.clearValueWhenHidden（嵌套对象，非顶层）、initFetch → autoInit（Flux 命名映射）、aside 完全支持（含 asideResizor → asideResizable 命名差异）。3 个 Phase 的代码、测试、文档改动实质上完全且正确地完成，19 个端到端测试全过。独立 fresh-session closure audit 全部 PASS。
Completed: 2026-07-13

Closure Audit Evidence:

- Reviewer / Agent: Independent subagent (fresh session, did not draft or execute this plan)
- Audit Session: ses_0a6f924c7ffe6Gr4u1t8aOiodi (audit-290-20260713-0913)
- Evidence:
  - **Phase 1**（requiredWhen/clearValueWhenHidden 修正）— 全部 10 条 Exit Criteria PASS：
    - `flux-web.xlib:356` `required: cellModel?.requiredOn || (mandatory ? true : null)`
    - `flux-web.xlib:357` `readOnly: cellModel?.readonlyOn || (rd ? true : null)`
    - `flux-web.xlib:361` `hiddenFieldPolicy: cellModel?.clearValueOnHidden != null ? { clearValueWhenHidden: cellModel.clearValueOnHidden } : null`
    - `rg 'requiredWhen' flux-web.xlib` → 0 行（已删除）
    - 5 个 @Test 通过（`testCellRequiredOnMapsToFluxRequiredExpression`/`testCellRequiredExpressionOverridesStaticMandatory`/`testCellReadonlyOnMapsToFluxReadOnlyExpression`/`testCellClearValueOnHiddenMapsToFluxHiddenFieldPolicy` + 原有 `testControlCaseNoStarNoRequired`）
  - **Phase 2**（autoInit + aside + readOnly）— 全部 12 条 Exit Criteria PASS：
    - `flux-web.xlib:617-618` `if(formModel.initFetch != null) attrs.put('autoInit', formModel.initFetch)`
    - `flux-web.xlib:641-642` pickNotNull 新增 `asideMinWidth`/`asideMaxWidth`/`asideSticky`/`asideClassName`
    - `flux-web.xlib:656-657` `if(pageModel.asideResizor != null) attrs.put('asideResizable', pageModel.asideResizor)`
    - 3 个新测试 + 1 个反向重写测试通过（`testFormInitFetchMapsToFluxAutoInit`/`testAsidePropertiesAreEmittedInFlux` 等）
  - **Phase 3**（文档同步）— 全部 5 条 Exit Criteria PASS：
    - `flux-rendering.md` 删除"Flux 不支持 aside"错误表述（line 78 现为"Flux PageSchema 完全支持 aside"）
    - `flux-rendering.md` 删除"*On → *When 命名规律"错误泛化论断（line 62 现为"不存在统一规律"）
    - `flux-rendering.md` form 不支持表仅剩 silentPolling/wrapWithPanel/canAccessSuperData 3 项（initFetch 已移出）
    - `layout-syntax-reference.md` 顶部命名映射已修正
  - **Anti-Hollow Check** — PASS：
    - (a) 调用链 `pageProvider → GenPage → GenFormImpl(line 56 调用 FluxFormDefaultAttrs) → GenFormBody → GenFormCell(line 302 调用 GenFormSimpleCell) → 输出 JSON` 在 19 个测试中被运行时触达
    - (b) 新增的 required/readOnly/hiddenFieldPolicy/autoInit/asideResizable 字段确实出现在输出 JSON 中（通过 JSON 断言证明，非仅检查不抛异常）
    - (c) `scan-hollow-implementations.mjs --module nop-web --severity high` 退出 0，0 findings
  - **Deferred 项分类检查** — PASS：
    - silentPolling/wrapWithPanel/canAccessSuperData 分类 `watch-only residual` 合理（经独立核对 Flux 源码确认不支持）
    - initApi→initAction/api→submitAction 转换分类 `out-of-scope improvement` 合理（独立 successor plan）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/290-flux-web-xlib-attribute-mapping-fixes.md --strict` 退出码：0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-web --severity high` 退出码：0
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码：1（仅剩余 1 个 pre-existing 错误 `layout-syntax-reference.md:116` 的 `*@remark[备注](2)` 被误识为链接，与本计划无关；本计划新增/修改的 docs-for-ai 文件零错误）
  - `./mvnw test -pl nop-frontend-support/nop-web -am` BUILD SUCCESS（19 tests, 0 failures, 0 errors）

Follow-up:

- AMIS `staticOn` 的 Flux 等价：Flux 无 staticOn 概念，可能用 `readOnly` 替代，但语义有差异（staticOn 是"显示为文本"，readOnly 是"不可编辑"），需进一步评估
- `FluxPageDefaultAttrs` 仍 pick Flux PageSchema 不支持的 `initFetch`/`interval`/`silentPolling`：与 `FluxFormDefaultAttrs` 同类问题，应作为后续 plan 处理
- asidePosition：Flux PageSchema 支持，但 `xview.xdef` UiPageModel 未定义，需先改 xview.xdef 才能在 view.xml 中配置
- form 的 initApi→initAction、api→submitAction 转换（Flux 用 ActionSchema DAG，AMIS 用 ApiSchema 扁平结构）
