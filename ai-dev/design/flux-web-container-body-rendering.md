# flux-web 页面容器 Body 渲染设计

**日期**：2026-07-31
**范围**：`nop-frontend-support/nop-web` 的 `flux-web.xlib` 及 `flux-web/` 目录模板，`nop-kernel/nop-xdefs` 的 `xview.xdef`（已完成 schema 侧，本设计定义消费侧契约；本次仅允许注释级补充）
**状态**：active（已与 plan 323 达成共识，2026-07-31）

**修订记录**：

- R1（2026-07-31）：首轮独立审查 9 项裁决——step 有可选 `page` 属性（优先级 page > body > name）；GenLayoutTabs 真缺陷为 tab 项内容未入 `body` 且缺 `key`（`items` 字段名本已正确）；body 分派 Phase 1 先 crud/simple/tabs、wizard/group 后扩展；responsiveColumns 暂不输出；`normal`/`baseline` 均过滤；WizardSchema 无 startStep；hollow-scan 模块路径；`rg '"tabs"'` 校验不可用；throw 分支防御性。
- R2（2026-07-31）：第二轮审查 4 项裁决——startStep→value 语义错位（Flux 0-based 实现）移入 Follow-ups；xview.xdef 仅注释级补充；grid item key 属运行时契约；colSpan/rowSpan 限 crud/tabs。另修订结论 1 措辞（`page` 与 `body` 为优先级关系而非互斥）。
- R3（2026-08-01）：**complex 页面类型已实现**（取代 §3.5/§4.4 的"不实现"裁定）。实现路径与 §4.4 当初假设（拼装 left/right/top/bottom）不同：xview.xdef 的 complex schema 被重设计为 `header`/`footer`/`aside`/`body` 四槽位（`xdef:ref="UiPageModel"`），正好一一对应 Flux `PageSchema` 的四区域，无需拼装、无需 Flux 侧新增容器。消费侧由 `flux-web/page_complex.xpl` + `impl_GenPage.xpl` 的 complex 分派实现；`GenContainerModel` 不变（complex 仅页面级，不作为 body 子容器）。
**灵感来源**：nop-chaos-flux 仓库（`~/app/nop-chaos-flux-wt/nop-chaos-flux-master`）的 `TabsSchema` / `WizardSchema` / `GridSchema` 及 `deepFields.nestedRegions` 编译机制

---

## 一、设计结论

1. **`page` 与 `body` 是优先级关系而非互斥**：页面级 `tab` 与 `step` 节点同时支持 `page` 属性（均已在 xview.xdef 中放宽为可选，`xview.xdef:160,193`）与内嵌 `body` 子容器。渲染优先级为 `page` > `body` > `name` 兜底；`page` 与 `body` 都不存在且无 `name` 兜底时抛配置错误。注：`tab/step` 的 `name` 在 xview.xdef 中为必填（`name="!string"`），故兜底分支在合法 schema 下恒命中，throw 分支仅作防御性（future-proof）。
2. **body 渲染 = 按容器类型分派的递归渲染**：`tab/step` 的 `body` 解析为 `List<UiContainerModel>`，渲染时逐元素按 `type` 分派到与页面级相同的渲染逻辑，输出 Flux JSON 片段。body 容器类型为五种：`crud`/`simple`/`tabs`/`wizard`/`group`；`picker` 不属 body 容器类型（Flux 无页面级 picker schema），body 内配置 `picker` 落入 `otherwise` 抛 `nop.err.web.unknown-page-type`。
3. **Flux JSON 字段名契约（nop-chaos-flux 权威）**：tabs 用 `items` 数组（非 AMIS 的 `tabs` 字段）、wizard 用 `steps` 数组、group 用 `items` 数组（`GridSchema`）；`items[].body` / `steps[].body` 直接写 JSON 数组，由 Flux 编译期 `deepFields.nestedRegions` 提取为 region 渲染。
4. **`impl_GenPage.xpl` 的分派逻辑抽取为共享容器渲染标签**：新增按 `UiContainerModel.type` 分派的内部标签（`GenContainerModel`），页面级与 body 级共用同一渲染实现，避免两份实现漂移。注意 body 级与页面级的分派范围不同：body 级分派五种容器类型；页面级还含 picker（保持现状）。
5. **group 页面类型映射到 Flux `GridSchema`**：xview 的 `group`（columns/gap/autoFlow/alignItems/justifyItems/responsiveColumns）与 `GridSchema` 字段对应，body 子容器包为 `GridItemSchema`（body + colSpan/rowSpan）。**complex 页面类型映射到 Flux `PageSchema` 四槽位**（R3 后实现，详见修订记录 R3）：complex 的 `header`/`footer`/`aside`/`body` 四个子元素直接对应 `PageSchema` 的四区域，由 `flux-web/page_complex.xpl` 实现。

## 二、背景与动机

### 现状

`xview.xdef` 已支持容器化 body 配置（commit `1e094b9e0`）：

- `tab`：`page="string"`（可选）、`xdef:ref="UiContainerModel" xdef:bean-body-prop="body" xdef:body-type="list"` — tab 可内嵌任意容器子节点
- `step`：同上，page 放宽为可选
- 新增 `group`（二维网格）与 `complex`（四槽位）页面类型；`UiPageModel` 增加 `colSpan`/`rowSpan`

但 flux-web 生成器（`flux-web.xlib`）未消费这些新能力：

| 能力 | flux-web 现状 | 后果 |
|------|--------------|------|
| tab 内嵌 body | `page_tabs.xpl` 仅 `<body xpl:attrs="xpl('thisLib:LoadPage',tabModel.page \|\| tabModel.name)"/>` | body 配置被静默忽略 |
| wizard 页面类型 | `impl_GenPage.xpl` 无 wizard 分支 | 抛 `nop.err.web.unknown-page-type` |
| group 页面类型 | 无 group 分支 | 抛 `nop.err.web.unknown-page-type` |
| page_tabs 字段名 | `page_tabs.xpl` 输出 `<tabs j:list="true">` → JSON 字段 `tabs` | **Flux 读 `items`，现输出为 silent no-op** |
| form 级 tabs（GenLayoutTabs） | `flux-web.xlib:195` 已输出 `<items j:list="true">`（字段名正确），但 tab 项内容写在 `<tab j:list="true">` 子元素 → JSON 字段 `tab`，且无 `key` | Flux `nestedRegions` 只提取 `items[].body`，内容行未入 `body` → form 级 tabs 内容为 silent no-op |
| step 内嵌 body | 无 wizard 实现 | 无渲染路径 |

### 为什么必须对齐 nop-chaos-flux schema

flux-web 的输出是给 nop-chaos-flux 前端消费的 JSON schema。Flux 渲染器通过 `RendererDefinition.deepFields.nestedRegions` 在**编译期**把 `items[].body` / `steps[].body` / `items[].body`（grid）提取为 region（`bodyRegionKey`），渲染期按 `bodyRegionKey` 取 region 渲染。因此：

- JSON 中**直接写 `body: [...]` 数组**即可被渲染，不需要也不应该输出 `bodyRegionKey`（那是编译产物）
- 字段名必须严格匹配 Flux schema：`items`（tabs/grid）、`steps`（wizard）、`body`（item/step 内容）
- Flux 对未知字段不报错（编译期按定义表过滤），所以字段名错误表现为**静默空白**而非报错——这是最危险的失败模式

### 参考依据（nop-chaos-flux 源码）

- `packages/flux-renderers-basic/src/schemas.ts:141-160` — `TabsSchema.items`
- `packages/flux-renderers-basic/src/tabs.tsx:352-386` — 按 `bodyRegionKey` 渲染 item body
- `packages/flux-renderers-layout/src/schemas.ts:28-93` — `WizardSchema.steps` / `WizardStepSchema.body`
- `packages/flux-renderers-layout/src/wizard-renderer.tsx:100-135` — 按 `bodyRegionKey` 渲染 step body
- `packages/flux-renderers-layout/src/schemas.ts:98-139` — `GridSchema.items` / `GridItemSchema.body/colSpan/rowSpan`
- `packages/flux-renderers-layout/src/grid-renderer.tsx:120-135` — 按 `bodyRegionKey` 渲染 grid item body
- `packages/flux-renderers-layout/src/layout-renderer-definitions.ts:126-210`（wizard）、`:296-330`（grid）— `deepFields.nestedRegions` 编译期 body 提取
- `packages/flux-renderers-basic/src/basic-renderer-definitions.ts:459-520`（tabs）— 同上
- `nop-chaos-flux/docs/components/tabs/example.json` — `items[].body` 直接写数组的权威示例

## 三、核心设计

### 3.1 body 渲染分派

新增共享容器渲染标签（内部标签，`xlib` 内 `thisLib` 引用），接受一个 `UiContainerModel`，按 `type` 分派：

```text
GenContainerModel(containerModel):
    switch containerModel.type:
        'crud'   -> 渲染 crud 容器（复用 grid_crud.xpl 的 crud 片段，不包 page）
        'simple' -> 渲染 form 容器（复用 page_simple.xpl 的 form 片段，不包 page）
        'tabs'   -> 递归渲染 tabs（items[].body 逐项渲染）
        'wizard' -> 递归渲染 wizard（steps[].body 逐项渲染）
        'group'  -> 渲染 grid 容器（body 子容器包为 items[].body）
        otherwise -> 抛 nop.err.web.unknown-page-type
```

页面级渲染（`impl_GenPage.xpl`）与 body 渲染（tab/step 内部）共用该分派，仅外层包装不同：

- 页面级：crud/picker/simple/tabs/wizard/group 各自包 `page`/`picker` 外壳（现有 `page_crud.xpl` 等保持）
- body 级：直接输出容器 JSON 片段（无 page 外壳）

伪代码契约（body 渲染）：

```text
renderTabBody(tabModel):
    if tabModel.page 存在: return LoadPage(tabModel.page)
    if tabModel.body 非空: return [ GenContainerModel(c) for c in tabModel.body ]
    if tabModel.name 存在: return LoadPage(tabModel.name)   # 向后兼容兜底
    throw 配置错误
```

**LoadPage 的 `.page.yaml` 分支裁定**：`LoadPage`（`flux-web.xlib:883-895`）在被引用页面存在同名 `.page.yaml` 时返回 `{'x:extends': path, ...}` 而非 Flux JSON——该对象无 `type`，Flux 编译期 `extensionPassthroughPolicy: 'namespaced-only'` 丢弃 `x:` 命名空间属性后无法匹配渲染器，表现为静默空白。本设计**不改 LoadPage 行为**（其语义是服务端页面引用解析，供 .page.yaml 消费方使用）；页面级/body 级渲染的测试资源必须避免与被引用页面同名 `.page.yaml`（即用 `LoadPage` 时引用的页面不能有同名 yaml 页面定义）。`x:extends` 的 Flux 侧解析（服务端展开）为后续特性，不属本设计范围。

**xjson 输出规则**（`CollectJObjectHandler`）：子元素 tagName 在 OBJECT 上下文中作为 JSON 属性名（`<tabs j:list="true">` → `tabs: [...]`），在 LIST 上下文中成为 `type`；`j:key` 可改写属性名（`j:key="items"` 把 `<tabs>` 改写为 `items` 字段）；`<_>` 为匿名对象节点，不输出 `type` 字段。

### 3.2 tabs 输出契约（修复字段名）

Flux TabsSchema 期望：

```json
{
  "type": "tabs",
  "items": [
    { "key": "tabA", "title": "Tab A", "body": [ /* 容器 JSON */ ] }
  ]
}
```

- `key` ← tab 的 `name`；`title` ← tab 的 `title`（沿用现有 `FluxTabDefaultAttrs` 的 `_.pickNotNull` 透传——注意该标签无 i18n 包装，title 直接输出原始值；与 `FluxPageDefaultAttrs` 的 `@i18n:` 处理不同，保持现状不动）
- `body` ← `LoadPage(page)` 结果或 body 容器列表（3.1 契约）
- **`page_tabs.xpl` 必须将 `tabs` 字段改为 `items`**（当前输出 `tabs` 字段，Flux 读 `items`，silent no-op）
- **form 级 `GenLayoutTabs` 无需改字段名**（已输出 `items`），真缺陷是：tab 项内容写在 `<tab j:list="true">` 子元素生成 `tab:` 字段，Flux 只识别 `items[].body` → 内容静默丢失；需将内容移入 `body`，并补 `key` ← `name`。修正后 `items[].body` 为表单行 JSON 数组。

### 3.3 wizard 输出契约

Flux WizardSchema 期望：

```json
{
  "type": "wizard",
  "steps": [
    { "key": "stepA", "title": "Step A", "body": [ /* 容器 JSON */ ] }
  ]
}
```

- `key` ← step 的 `name`；`title` ← step 的 `title`
- `body` ← step 的内容来源，与 3.1 同优先级：`step.page` 存在（`xview.xdef:193` 已支持可选 `page="string"`，语义为"对应一个表单页面"）→ `LoadPage(step.page)`；无 `page` 但 `step.body` 非空 → body 容器列表经 `GenContainerModel` 分派；两者皆无 → `LoadPage(step.name)` 兜底（name 必填，恒命中）
- **页面级 wizard 包 `page` 外壳**（与 crud/simple/tabs 一致：`{type:'page', body:[{type:'wizard', steps:[...]}]}`），保证页面级 title/initApi 等属性可用
- wizard 容器属性映射表（xview `wizard` 属性 → Flux `WizardSchema` 字段，`xview.xdef:178-191`）：

  | xview wizard 属性 | Flux WizardSchema | 裁定 |
  |-------------------|-------------------|------|
  | `mode` | `mode`（vertical/horizontal） | 透传（值域一致） |
  | `actionPrevLabel`/`actionNextLabel`/`actionNextSaveLabel`/`actionFinishLabel` | 同名 | 透传 |
  | `startStep` | `value`（seed-only，非响应式） | **暂不映射**：Flux `wizard-renderer.tsx:191-200` 对 number 直接按 **0-based 下标**钳制（`Math.min(initial, stepCount-1)`，其 schema 注释自称 1-based——Flux 注释与实现矛盾），xview `startStep` 是 1-based 模板串；语义错位无法无损转换，映射规则留待后续 plan（见 plan Non-Blocking Follow-ups） |
  | `className`/`actionClassName` | 无对应字段 | 丢弃 |
  | `initFetch`/`initFetchOn`/`initApi` | 无对应字段 | 丢弃（Flux 数据获取走表单/组件自身 api 机制，本 plan 不实现） |
  | `reload`/`redirect`/`target` | 无对应字段（有 `onChange`/`onStepCommit`/`onComplete` 事件） | 丢弃（事件映射为后续特性） |

### 3.4 group 输出契约

xview `group` → Flux `GridSchema` 字段映射：

| xview group 属性 | Flux GridSchema | 说明 |
|------------------|-----------------|------|
| `columns` | `columns` | number → repeat(N,1fr)；string → 原始 grid-template-columns |
| `gap` | `gap` | |
| `autoFlow` | `autoFlow` | 枚举值映射：`row-dense`/`column-dense` → `row dense`/`column dense` |
| `alignItems` | `alignItems` | xview 枚举含 `normal`/`baseline`，Flux 枚举仅 `start`/`end`/`center`/`stretch`（`schemas.ts:132-134`）——**`normal` 与 `baseline` 均过滤**（不输出，不抛错；输出非法值会被编译期静默丢弃，过滤是显式化） |
| `justifyItems` | `justifyItems` | 同上 |
| `responsiveColumns` | `responsiveColumns` | **暂不输出**：xview 为 string（`xview.xdef:212`，无格式约定），Flux 为对象 `{sm,md,lg}`（`schemas.ts:126`）；透传 string 会被 Flux `isResponsiveColumnsObject` 判定失败后静默回退到基础 columns——类型不匹配，转换规则留待后续 plan 定义 |
| body 子容器 | `items[].body` | 每个子容器包为 `{key, body: [容器 JSON], colSpan, rowSpan}` |
| 子容器 `colSpan`/`rowSpan` | `items[].colSpan/rowSpan` | 来自 `UiPageModel`（`xview.xdef:43`）；仅继承 `UiPageModel` 的容器（crud/tabs）支持 span 属性，simple/wizard/group 容器可入 body 但无 span |
| 页面级外壳 | — | group 页面同样包 `page` 外壳：`{type:'page', body:[{type:'grid', items:[...]}]}` |

注：`items[].key` 未在 `GridItemSchema` 类型中声明，但 `grid-renderer.tsx:13-17` 的 `resolveItemKey` 运行时消费 `item.key` 作 React key——输出 key 属于运行时契约（有效且有用途），非 schema 声明字段。

### 3.5 边界

- **complex 已实现**（R3，取代原"不实现"裁定）：xview.xdef 的 complex schema 被重设计为 `header`/`footer`/`aside`/`body` 四槽位（对齐 Flux `PageSchema` 四区域），由 `flux-web/page_complex.xpl` 渲染。原 left/right/top/bottom 方案（§4.4）因 Flux 无四槽位容器被拒绝，新方案绕过该限制。complex 仅页面级（`pages` 下），不作为 `GenContainerModel` 的 body 子容器（`UiContainerModel` 的子元素不含 complex）
- **picker 保持现状**：Flux 无页面级 picker schema（`PickerSchema` 是表单字段类型），现有 `page_picker.xpl` 输出 `<picker>` 为 AMIS 遗留，本次不处理，后续单独评估。**body 容器类型不含 picker**（见结论 2），body 内配置 `picker` 抛 `unknown-page-type`
- **仅 flux-web 侧**：不修改 AMIS `web.xlib` / `web/` 模板；AMIS 侧 schema（xview.xdef）已同步支持 body，AMIS 消费另行评估
- **递归深度**：body 容器可嵌套（tab 内嵌 tab），无深度限制，由模型树深度的现实约束决定

## 四、拒绝了什么

### 4.1 在 `page_tabs.xpl` 中 inline 判断 body（不抽取共享标签）

`page_tabs.xpl` 直接写 `<c:choose>` 判断 `tabModel.body` 并 inline 渲染各容器类型。

**拒绝理由**：body 渲染逻辑与页面级分派高度重合（crud/simple/tabs/wizard/group 五种容器类型 + 页面级 picker），inline 会产生两份平行实现，后续 schema 演进必然漂移。共享分派标签是单一事实来源。

### 4.2 让 Flux 端兼容 `tabs` 字段（改 nop-chaos-flux 而非 flux-web）

在 nop-chaos-flux 的 TabsSchema 定义中把 `items` 别名为 `tabs`，或在 flux-web 输出 `bodyRegionKey` 手工 region。

**拒绝理由**：
- nop-chaos-flux 是独立仓库，`items` 是 Flux 全栈统一命名（tabs/grid/collapse 均用 items），为单个后端输出改前端 schema 破坏 Flux 一致性
- `bodyRegionKey` 是编译产物，手工输出 region key 与 `deepFields.nestedRegions` 机制冲突，双重 region 定义会重复渲染
- 正确方向是后端输出对齐前端权威 schema（render-mode-switch 设计已确立"view.xml → flux-web → Flux JSON"的单向翻译方向）

### 4.3 body 内容用 `LoadPage` 加载子页面（而非直接渲染容器）

`tabModel.body` 中的容器直接以 `LoadPage(name)` 方式按名称加载已定义页面。

**拒绝理由**：body 是**内嵌容器定义**（模型树上的子节点），不是页面引用；按名称加载会丢失内嵌配置（如 tab 内联的 crud 网格、行内表单），且要求每个内嵌容器都有独立 page 定义，违背"body 提供内嵌配置能力"的 schema 意图。

### 4.4 本次实现 complex 页面类型

用 Flux `container`/`flex` 拼装 left/right/top/bottom 四槽位。

**拒绝理由**：Flux 无现成四槽位容器，拼装方案需要自定义 CSS 布局语义且无 Flux 侧验证路径，属于独立特性；schema 侧已就绪（xview.xdef 已定义），可后续单独评估。

## 五、与已有设计的关系

- `render-mode-switch-design.md`：本设计是其"层1 页面模板"的 flux-web 侧能力补齐；渲染模式切换机制（`impl_flux_mode.xpl` 替换 5 个入口标签）不受影响
- `docs-for-ai/02-core-guides/xdef-and-xdsl.md` §8：`xdef:bean-body-prop` 解析语义（body 子节点解析为列表写入 bean 的 `body` 属性）是本设计 `tabModel.body` 的数据来源
- `docs-for-ai/02-core-guides/flux-rendering.md`：实现完成后同步更新 tab/wizard/group 的 Flux JSON 映射说明
- `docs-for-ai/02-core-guides/layout-syntax-reference.md`：同步更新 tabs/wizard 语法说明
