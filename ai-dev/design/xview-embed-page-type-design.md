# xview `<embed>` 页面类型 + 单元格 view override delta 合并 — 设计

> Status: approved
> Plan: `ai-dev/plans/331-xview-embed-page-type-and-view-override.md`
> Owner: nop-web / flux 渲染管线
> Last Reviewed: 2026-08-07

## 1. 背景与目标

xview.xdef 的 `<pages>` 此前支持 `crud/picker/simple/tabs/wizard/group/complex` 七类页面类型（complex 是唯一即带 `xdef:bean-tag-prop="type"` 又带子槽位的类型）。存在两个缺口：

1. **页面级嵌入**：无法把一个独立维护的外部页面（view.xml 的某个 page/grid，或一个 page.yaml）原样嵌入到当前 view 的 `<pages>` 中并做局部定制。`LoadPage`（dialog/drawer/feedback）支持弹窗嵌入但仅限弹窗场景，且不支持 delta 合并。
2. **cell 级 `<view>` override**：`disp.xdef` 的 `UiRefViewModel`（cell 引用外部 page/grid 的配置）已声明但**未消费** `<override>`——单元格里引用的子页面/子表格无法做 delta 定制。

目标：新增 `<embed>` 页面类型（按 `path` 加载外部页面 + `<override>` delta 合并），并把 cell `<view>` 的 override 接入 `GenDispView` 渲染路径。

## 2. 关键设计决策

### 2.1 `<override>` schema 形式（Phase 1 Decision）

- **裁定**：`<override>xjson</override>`，与 `<data>xjson</data>` 一致。
- **理由**：`xjson` 内容经 `XNode.toXJson()` 解析后本身即任意结构（map/list/标量），无需 `xdef:unknown-attr="any"`。前会话曾尝试 `<override xdef:unknown-attr="any">xjson</override>`，破坏 nop-ui codegen/precompile（已回退）。`xjson` 是平台既有的「任意 JSON 内容」内容类型，codegen 生成 `Object _override` 字段，XPL 可经 `refView.override` / `pageModel.override` 访问。
- **位置**：`disp.xdef` 的 `<view>`（UiRefViewModel）新增 `<override>xjson</override>`；`xview.xdef` 的 `<embed>`（UiEmbedPageModel）同样带 `<override>xjson</override>`。

### 2.2 合并入口落点（Phase 2 Decision）

- **裁定**：`WebPageHelper.applyViewOverride(Map base, Object override)` 静态 helper。
- **语义**：override 为 null/空 map/空 list 时原样返回 base；否则 `JsonMerger.instance().merge(base, override)`（schema-free merge：map 按 key 合并、`!` 前缀强制覆盖、list 按唯一键合并、无唯一键整段替换、支持 `x:override` replace/remove）。
- **复用**：page_embed + 两端 GenDispView（flux-web.xlib + web.xlib）共用，避免在 xlib 内复制 merge 语义。
- **只读副本修正**：override 来自已冻结的组件模型（`AbstractComponentModel.freeze` 级联冻结 JObject），为只读容器。`JsonMerger.mergeMap` 会就地 `remove` `x:virtual`/`x:inherit` 标记键，对只读 JObject 触发 `nop.err.core.json.map-is-readonly`。故 helper 内先 `CloneHelper.deepClone(override)` 转可写副本（`JObject.deepClone()`/`CloneHelper.deepCloneMap` 返回非冻结 JObject/LinkedHashMap）。

### 2.3 embed 分派与 page_embed.xpl 输出机制

- **分派**：`impl_GenPage.xpl` 在 complex 分支后新增 `<when test="${pageModel.type == 'embed'}"><c:include src="page_embed.xpl"/></when>`。
- **加载分支**（`page_embed.xpl`）：
  - `path` 缺省 → `nop.err.web.embed-page-path-required`（防御性，schema 已强制 path 必填）。
  - `path` 以 `.view.xml` 结尾 + `page` → `thisLib:GenPage`（外部 view 自载 objMeta/controlLib，宿主不重复）。
  - `path` 以 `.view.xml` 结尾 + `grid` → `thisLib:GenTable`。
  - `path` 以 `.view.xml` 结尾但无 page/grid → `nop.err.web.embed-page-ref-required`（显式失败，不静默 noop）。
  - 否则（page.yaml 等）→ `WebPageHelper.internalLoadPage`；flux 模式下 page.yaml 经 `toFluxPagePath` + `ResourceHelper.resolve().exists()` 回退到同名 flux.yaml（与 `LoadPage` 一致）。
- **输出**：得到 base JSON 后 `applyViewOverride(base, pageModel.override)`，经 `<_ xpl:attrs="result"/>` 输出（`_` 为占位标签，xpl:attrs 展开 map 各键为 JSON 对象，不额外添加 type；与 `container_tabs.xpl` 的 LoadPage 用法同构）。
- **xpl:return 语义**：`xpl:return="var"` 经 `XplCompiler.java:229-231` 编译为 `varDecl(var, expr)`——仅赋值，不即时返回。故 `<c:collect xpl:return="base">` 捕获 base 后可继续执行 applyViewOverride（与 GenDispView 既有用法一致）。

### 2.4 两端 GenDispView 接线（Phase 3）

- `flux-web.xlib` 与 `web.xlib` 的 `GenDispView`（outputMode="none"，三处 return）统一改为：base 经 `applyViewOverride(base, refView.override)` 返回。refView.override 为 null 时 applyViewOverride 原样返回 base，既有 75 例输出不变（无回归）。AMIS/web.xlib 侧 cell 级 override 与 flux 侧齐平，避免两端语义漂移。

## 3. 范围边界

### In Scope
- `xview.xdef` `<embed>` 页面类型 schema（commit `132b60979` + fix `82dbd170c`，容器 + 页面双形态）。
- `disp.xdef` `<view>` 的 `<override>` schema + nop-ui codegen（`_UiRefViewModel._override`）。
- flux `page_embed.xpl` + `impl_GenPage.xpl` embed 分派。
- 两端 GenDispView override 接线。
- `WebPageHelper.applyViewOverride`（含只读副本修正）。
- 测试夹具 + `TestFluxWebGen` 用例 + 文档。

### Out of Scope（已裁定 Deferred / Follow-up）
- **AMIS/web.xlib 侧页面级 embed 接线**：与 complex 先例一致（AMIS 侧 `impl_GenPage` 不接 wizard/group/complex/embed，落入 `GenPageDefault` throw）。`out-of-scope improvement`，Successor Required = no。cell 级 override 已在两端齐平。
- **容器槽位内嵌 embed 的渲染**：schema 已允许 embed 作为 `UiContainerModel` 第七种子类型（置于 complex/tabs/wizard/group 槽位），但 flux 渲染分派（`GenContainerModel`）尚未接 embed 子级。属 follow-up。
- **`refView.form` 分支**：保持 throw not-impl。
- **`LoadPage`（dialog/drawer/feedback）的 override 支持**：弹窗嵌入的 `x:extends` 已满足；如需 delta 可复用 applyViewOverride，触发条件 = 真实需求。

## 4. 被拒替代方案

| 方案 | 否决理由 |
|------|----------|
| `<override xdef:unknown-attr="any">xjson</override>` | 破坏 nop-ui codegen/precompile（前会话实测已回退）；`xjson` 已是任意结构内容类型，unknown-attr 多余。 |
| 在 xlib 内联复制 JsonMerger 语义 | 两端 GenDispView + page_embed 三处复制 merge 逻辑，维护成本高且易漂移；统一到 `WebPageHelper.applyViewOverride` 静态 helper。 |
| embed 直接 emit `<page>` 外壳包裹外部页面 | 外部页面已自带 `type`（page/table 等），再包一层产生嵌套 page；用 `<_ xpl:attrs>` 纯展开外部页面 JSON，保留其原始 type。 |
| page_embed 用 outputMode="none" + return（像 LoadPage） | page_embed 是 `<c:include>` 进 `impl_GenPage.xpl`（GenPage source，outputMode="xjson"），需向输出缓冲区 emit；`<_ xpl:attrs="result"/>` 是 xjson 上下文emit 计算后 map 的既定模式（container_tabs LoadPage 先例）。 |

## 5. 验证

- `./mvnw clean install -pl nop-frontend-support/nop-ui -am -DskipTests` BUILD SUCCESS，`nop-frontend-support/nop-ui/src/main/java/io/nop/xui/model/_gen/_UiRefViewModel.java` 含 `_override`/`getOverride()`。
- `./mvnw test -pl nop-frontend-support/nop-web -am` → 75 tests / 0 failures（含新增 4 例 embed/cell-override）。
- `./mvnw test -pl nop-kernel/nop-xlang` → 456 tests / 0 failures（disp.xdef 变更无回归）。
- 测试用例：`testEmbedPageLoadsExternalViewAndMergesOverride` / `testEmbedPageLoadsPageYamlDirectly` / `testCellViewOverrideMerges` / `testEmbedPageWithoutRefThrows`。

## 6. 参考锚点

- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/xview.xdef:231-246`（embed schema，容器 + 页面双形态）
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/disp.xdef:39-48`（view override schema）
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/page_embed.xpl`
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib:534-599`（GenDispView）
- `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/WebPageHelper.java:70`（applyViewOverride）
- `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/delta/JsonMerger.java`（delta 合并原语）
- `docs-for-ai/02-core-guides/flux-rendering.md`（embed 页面 + override 语义段）
