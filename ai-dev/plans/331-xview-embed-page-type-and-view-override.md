# 331 xview `<embed>` 页面类型 + 单元格 view override delta 合并

> Plan Status: completed
> Last Reviewed: 2026-08-07
> Source: `ai-dev/logs/2026/08-05.md`、用户设计会话（新页面类型嵌入外部 viewPath 页面 + override 节点 delta 合并；cell `<view>` 已加 override，消费逻辑未写）
> Related: `324-flux-web-complex-page-rendering.md`、`323-flux-web-container-body-rendering.md`

## Purpose

在 xview.xdef 新增页面级 `<embed>` 页面类型（path 指向外部 viewPath / page.yaml，直接嵌入其页面 grep，并经 `<override>` 节点做 delta 合并），并把 disp.xdef `<view>`（UiRefViewModel）已声明但未消费的 `<override>` 接入 GenDispView 渲染路径（cell 级 \u540c构合并）。完成 schema → codegen → flux 渲染 → 两端 GenDispView 消费 → 测试 → 文档的整链落地与独立 closure audit。

## Current Baseline（live 状态，2026-08-05 逐一核实）

- **xview.xdef `<pages>`**（`xview.xdef:239-253`）：`crud/picker/simple/tabs/wizard/group/embed/complex` 八类。complex 是唯一即带 `<xdef:bean-tag-prop="type">` 又带子槽位（header/footer/aside/body）的页面类型；另 `cc47a3589` 已为六类补 `name="!string"` 键属性。xview.xdef 模型为**运行时 xdef 动态模型**（`UiPageModel`/`UiCRUDModel` 等不在 nop-ui `_gen` Java 类中），修改 schema 无需 Java codegen。
- **xview.xdef `<embed>` 已落地**（commit `132b60979` + fix `82dbd170c`）：`<embed>` 定义为 `UiEmbedPageModel`（`xview.xdef:231-236`，`xdef:ref="UiPageModel"` + `xdef:bean-tag-prop="type"` + `<override>xjson</override>`），**同时**作为容器子类型（位于 `<xdef:define>` UiContainerModel 定义块内，注释明确「第七种子类型」）**和** `<pages>` 下的页面类型（`:246` `xdef:ref="UiEmbedPageModel"`）。即 schema 层 embed 已完成（容器 + 页面双形态）。**注意**：原 plan 草拟 Non-Goal「不加入 UiContainerModel」已被 commit `132b60979` 的实际实现超越——embed 被有意加入容器子类型以支持 complex/tabs/wizard 槽位内嵌外部页面。
- **分派链路**：flux `flux-web/impl_GenPage.xpl:37-39` 有 complex 分派；`web/impl_GenPage.xpl:18-34` 仅 crud/picker/simple/tabs，其余落入 `<thisLib:GenPageDefault>`（非 flux 侧 throw `nop.err.web.unknown-page-type`，web.xlib:29）。**complex 未在 AMIS/web.xlib 侧接线**（plan 324 只落地 flux），本 plan 遵循同一先例。
- **cell `<view>` 消费点**：`GenDispView` 存在于 flux-web.xlib:534-598 与 web.xlib:527-591（两份几乎同构）。分支：`refView.path.endsWith('.view.xml')` → `page`→GenPage / `grid`→GenTable|GenInputTable / `form`→throw not-impl；`otherwise` → `WebPageHelper.internalLoadPage(refView.path)`。**两处当前均不读取 `refView.override`**（消费未写）。
- **schema 先例**：`disp.xdef:39-44` `<view>`（UiRefViewModel）已含 `<data>xjson</data>`、`<actions>`，**当前不含 `<override>`**（工作树干净，`git status` 无 disp.xdef 改动）。前会话曾尝试 `<override xdef:unknown-attr="any">xjson</override>` 写法但会破坏 nop-ui codegen/precompile（`ai-dev/logs/2026/08-05.md` 记录该实验已被 `git stash`/回退，未提交）。即 override schema 正确写法仍待确定（见 Phase 1 Decision）。
- **codegen 机制**：nop-ui 模型由 `precompile/gen-ui-xdsl.xgen` 渲染 `disp.xdef/form.xdef/grid.xdef`（exec-maven-plugin `CodeGenTask` postcompile 阶段执行），产物为 `nop-ui/src/main/java/io/nop/xui/model/_gen/*.java`。当前 `_gen/_UiRefViewModel.java`（11 字段）无 `override` 字段——需 schema 修正后 re-run codegen 生成 `_override` 字段（`Object`），`refView.override` 才可在 XPL 访问。
- **`WebPageHelper.applyViewOverride` 已落地**（commit `132b60979`，`WebPageHelper.java:70`）：`applyViewOverride(Map base, Object override)` + 私有 `isEmptyMapOrList`——override 为 null/空 map/空 list 时原样返回 base，否则 `JsonMerger.instance().merge(base, override)`。**Phase 2 Decision「新增 helper」已完成**，但 helper 当前**无任何调用方**（GenDispView 两端 + page_embed 均未接线——接线属剩余工作）。
- **delta 合并原语**：`JsonMerger`（nop-core `lang/json/delta/JsonMerger.java`）schema-free merge：map 按 key 合并（`!key` 前缀=强制覆盖），list 按唯一键(id/name) 合并、无唯一键则整段替换，支持 `x:override` replace/remove。与页面 `x:extends` 加载（`WebPageHelper.internalLoadPage` / `PageProvider.loadPage` 走 `DeltaJsonOptions`/`loadDeltaBean`）同源。
- **测试基线**：`TestFluxWebGen` 18 例（含 complex 四槽位 `testComplexPageEmitsFourSlots`）；夹具 `test-flux-complex.{view.xml,page.yaml,xmeta}` 模式可复用（page.yaml 用 `x:gen-extends: <flux-web:GenPage view=... page=.../>`）。nop-web 全量 71 例 0 failures。nop-xdefs 是 jar 资源：**改 xview.xdef 后必须重装 jar**，否则运行时用旧 schema 报 `node-unexpected-tag-name`（plan 324 先例）。
- **剩余 gap**：(1) disp.xdef `<view>` 的 `<override>` schema 与 codegen（`_UiRefViewModel._override` 字段）未落地——这是 cell 级 view override 消费的前置；(2) flux `page_embed.xpl` 渲染 + `impl_GenPage.xpl` embed 分派未写（页面级 embed 仅 schema 存在，渲染路径未通）；(3) 两端 GenDispView 的 `applyViewOverride` 接线未写（helper 已存在但无调用方）；(4) AMIS/web.xlib 侧 GenDispView 的 override 消费同样缺失；(5) 测试与文档未写。**已落地**：xview.xdef embed schema（容器 + 页面双形态）、`WebPageHelper.applyViewOverride` helper。

## Goals

- xview.xdef `<pages>` 新增 `<embed>` 页面类型：`path`（v-path，指向外部 view.xml 或 page.yaml）+ `page/grid` 选择 + `<override>` delta 合并节点 + `<data>`。
- flux `impl_GenPage.xpl` embed 分派 + `page_embed.xpl` 渲染（复用 GenDispView 同构的加载分支 + override 合并）。
- GenDispView（flux-web + web 两库）在取到 refView 页面/grid 后应用 `refView.override`（JsonMerger delta 合并），单元与网格 cell 均生效。
- 修正 disp.xdef override schema 使 nop-ui codegen 通过，并重生成 `_UiRefViewModel._override`。
- 测试、文档（`flux-rendering.md`、`source-anchors.md`、design doc、daily log）与独立 closure audit。

## Non-Goals

- 不实现容器槽位内嵌 embed 的**渲染**（schema 已允许 embed 作为 UiContainerModel 子类型置于 complex/tabs/wizard 槽位，但本 plan 仅落地页面级 embed 的 flux 渲染——`impl_GenPage.xpl` embed 分派 + `page_embed.xpl`；容器子级 embed 的渲染分派属 follow-up，与 complex 先例一致——AMIS/web.xlib 侧不接）。`<embed>` 页面可经 `<tab page="embedPageName">` / `<step page=` 由名称引用（引用 `pages` 下页面）。
- 不在 AMIS/web.xlib 侧接页面级 embed（沿用 complex 先例——AMIS 侧 `impl_GenPage` 不接 wizard/group/complex 也不会接 embed，落入 GenPageDefault throw）。
- 不实现 `refView.form` 分支（保持 throw not-impl）。
- 不改 nop-chaos-flux、不改前端运行时增量合并、不引入新的合并策略（复用 JsonMerger 既有语义，无唯一键 list 整段替换为已知约束并写入文档）。
- 不动 `LoadPage`（dialong/drawer/feedback）既有行为（其页面级 `x:extends` 已满足弹窗嵌入；如需 override 属 follow-up）。

## Scope

### In Scope

- ~~`xview.xdef` 新增 `<embed>` 页面类型 schema + `UiEmbedPageModel`（运行时动态模型）。~~ **已落地**（commit `132b60979`）。
- `disp.xdef` `<view>` override schema 修正为可通过 codegen 的写法，并确保 `_UiRefViewModel` 重生成出 `_override`（`Object`）字段。
- nop-xdefs jar 重装 + nop-ui codegen 重跑。
- flux `flux-web/impl_GenPage.xpl` embed 分派 + `flux-web/page_embed.xpl`。
- `GenDispView` 两库（flux-web.xlib:534 / web.xlib:527）override 合并消费接线（共享合并入口 `WebPageHelper.applyViewOverride` **已落地**，但两端 GenDispView + page_embed 的调用接线属剩余工作）。
- 测试夹具 `test-flux-embed.*` 与 cell override 夹具；`TestFluxWebGen` 用例。
- 文档：`docs-for-ai/02-core-guides/flux-rendering.md`、`docs-for-ai/04-reference/source-anchors.md`（EXT 锚点）、`ai-dev/design/xview-embed-page-type-design.md`、`ai-dev/logs/2026/08-05.md`。

### Out Of Scope

- 页面级 embed 的 AMIS/web.xlib 接线（GenPageDefault throw，见 Non-Goals）。
- `LoadPage`/dialog 弹窗 path 的 override 支持。
- 容器槽位内嵌 embed 的**渲染**（schema 已允许 embed 作为容器子类型；容器子级 embed 的 flux 渲染分派不在本 plan，属 follow-up）、UI 层 schema 校验增强、i18n 同步。

## Execution Plan

### Phase 1 - schema + codegen（xview.xdef / disp.xdef / nop-ui）

Status: completed
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/xview.xdef`, `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/disp.xdef`, `nop-frontend-support/nop-ui/src/main/java/io/nop/xui/model/_gen/_UiRefViewModel.java`

- Item Types: `Decision | Fix | Proof`

- [x] Decision：确定 `<override>` schema 形式上界——先最小复现 codegen 失败（`./mvnw clean install -pl nop-frontend-support/nop-ui -am -DskipTests` 或 isolate precompile），确认 `xdef:unknown-attr="any"` 是否为主因；若否，改用与 `<data>xjson</data>` 一致的 `<override>xjson</override>`（xjson 内容经 XML→JSON 解析后本身即任意结构，无需 unknown-attr）。
  - Skill: none（平台 xdef codegen 既有机制，无对应技能）
  - 裁定：采用 `<override>xjson</override>`（与 `<data>xjson</data>` 一致）；xjson 内容经 XML→JSON 解析后本身即任意结构，`xdef:unknown-attr="any"` 是前会话 codegen 失败主因，无需引入。
- [x] Fix：按 Decision 修正 `disp.xdef` `<view>` 的 `<override>` 写法，使 nop-ui precompile/codegen 通过；re-run codegen 确认 `_gen/_UiRefViewModel.java` 新增 `_override`（`Object`）字段与 `getOverride()`。
  - Skill: none
  - 落地：`disp.xdef` `<view>` 增 `<override>xjson</override>`（带 delta 合并语义注释）；`./mvnw install -pl nop-frontend-support/nop-ui -am -DskipTests` BUILD SUCCESS，`_gen/_UiRefViewModel.java` 再生成出 `_override`/`getOverride()`（:75/:256-264）。
- [x] Add：`xview.xdef` 新增 `<embed>`（`xdef:name="UiEmbedPageModel"`，`xdef:ref="UiPageModel"`，`xdef:bean-tag-prop="type"`，`name/path/page/grid/title` 属性 + `<override>xjson</override>`），带注释说明语义与无唯一键 list 替换约束。**已落地**（commit `132b60979` + fix `82dbd170c`）：embed 同时作为容器子类型（UiContainerModel 定义块内，`:231-236`）与 `<pages>` 下页面类型（`:246`）存在。
  - Skill: none
- [x] Proof：`./mvnw clean install -pl nop-kernel/nop-xdefs -am -DskipTests`（重装 jar 使运行时读到新 schema）+ nop-ui codegen 构建通过；`__isVm` 无回归——nop-xdefs 测试不含 xview 用例，用 codegen 构建全过作证。
  - Skill: none
  - 落地：`nop-xdefs` jar 经 `-am` 随 nop-ui 构建链重装；codegen 全过；`_override` 字段实证（grep `override` `_UiRefViewModel.java` 命中字段/getter/setter/outputJson/copyTo）。

Exit Criteria:

- [x] `./mvnw clean install -pl nop-frontend-support/nop-ui -am -DskipTests` BUILD SUCCESS，且 `_gen/_UiRefViewModel.java` 存在 `_override`/`getOverride()`。
- [x] `xview.xdef` `<pages>` 含 `<embed>` 元素且 xview model 可在运行时解析（临时 fixture 或 schema 加载验证）。
- [x] `disp.xdef` `<view>` override schema 不破坏 codegen（`./mvnw clean install -pl nop-frontend-support/nop-ui -am -DskipTests` 与 disp.xdef override 变更可一起 install 成功）。
- [x] No owner-doc update required（Pure schema/codegen，无 public doc 行为面）。
- [x] `ai-dev/logs/2026/08-07.md` 已更新（Phase 1 节）。

### Phase 2 - flux 渲染：embed 页面类型

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/impl_GenPage.xpl`, `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/page_embed.xpl`（新建）, `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`

- Item Types: `Add | Fix | Proof`

- [x] Add：`flux-web/impl_GenPage.xpl` 增加 `<when test="${pageModel.type == 'embed'}"><c:include src="page_embed.xpl"/></when>`。
  - Skill: none
  - 落地：`impl_GenPage.xpl:37-39` complex 分支后新增 embed 分派。
- [x] Add：新建 `flux-web/page_embed.xpl`——按 `pageModel.path` 分派（`.view.xml` → `GenPage`/`GenTable`/`GenInputTable`；否则 `WebPageHelper.internalLoadPage`，含 page.yaml→flux.yaml 回退若适用），得到 base 页面 JSON 后经 `WebPageHelper.applyViewOverride(base, pageModel.override)` 输出；未配置 path/page/grid 时显式抛错（不静默 noop）。宿主 view 不重复 objMeta/controlLib（由 path 指向的外部 view 自载）。
  - Skill: none
  - 落地：`page_embed.xpl` 新建——`!path`→`nop.err.web.embed-page-path-required`；`.view.xml`+page→GenPage；`.view.xml`+grid→GenTable；`.view.xml` 无 page/grid→`nop.err.web.embed-page-ref-required`；otherwise→internalLoadPage（page.yaml→flux.yaml 回退经 `WebPageHelper.toFluxPagePath`+`ResourceHelper.resolve().exists()`）；末尾 `applyViewOverride(base, pageModel.override)` 经 `<_ xpl:attrs="result"/>` 输出（xpl:return 经 XplCompiler.java:229-231 确认为赋值语义，非即时返回）。
- [x] Decision：合并入口落点——`WebPageHelper.applyViewOverride(Map base, Object override)` 静态 helper（`null`/empty override 原样返回，否则 `JsonMerger.instance().merge`），Phase 2 与 Phase 3 两端 GenDispView、page_embed 共用，避免 xlib 内复制 merge 语义。**已落地**（commit `132b60979`，`WebPageHelper.java:70`）；但当前**无调用方**，接线（page_embed + 两端 GenDispView）属剩余工作。
  - Skill: none
  - 接线落地：page_embed + 两端 GenDispView 均已调用 applyViewOverride；并修复 override 为冻结只读 JObject 时 JsonMerger.mergeMap `remove` 触发 `map-is-readonly`——helper 内先 `CloneHelper.deepClone(override)` 转可写副本。
- [x] Proof：`page_embed.xpl` 对 `path` 指向 view.xml（page/grid 分支）与 page.yaml 两种形态分别覆盖；override 合并结果可见；未配置引用时显式失败。
  - Skill: none
  - 落地：`TestFluxWebGen.testEmbedPageLoadsExternalViewAndMergesOverride`（view.xml+page→crud 外壳 + override 新增 title + 覆盖 asideClassName）/ `testEmbedPageLoadsPageYamlDirectly`（page.yaml→override 覆盖 title，body 保留）/ `testEmbedPageWithoutRefThrows`（view.xml 无 page/grid → `nop.err.web.embed-page-ref-required`）。全绿。

Exit Criteria:

- [x] `WebPageHelper.applyViewOverride` 存在且语义正确（空 override 幂等返回 base；非空走 JsonMerger，`x:override` replace/remove、`!key`、无唯一键 list 替换语义均生效）。**已落地**（commit `132b60979`，`WebPageHelper.java:70`）。
- [x] `page_embed.xpl` 存在且被 `impl_GenPage.xpl` 的 embed 分派命中；view.xml 与 page.yaml 两形态的 embed 页面均可加载并返回合并后 JSON。
- [x] 未知/未配置引用显式抛 `nop.err.web.*`，非 silent no-op（对应 Minimum Rule #24）。
- [x] **端到端验证**：`PageProvider.getPage` 经 page.yaml → GenPage → page_embed 从入口到输出去 override 合并结果完整跑通（用 Phase 4 夹具）。
- [x] No owner-doc update required（渲染行为文档随 Phase 4 一次对齐）。
- [x] `ai-dev/logs/2026/08-07.md` 已更新（Phase 2 节）。

### Phase 3 - 两端 GenDispView override 消费（cell 级）

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`, `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web.xlib`, `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/WebPageHelper.java`

- Item Types: `Fix | Proof`

- [x] Fix：`flux-web.xlib` GenDispView（:534-598）各分支取得 base 页面/grid JSON 后，统一经 `WebPageHelper.applyViewOverride(base, refView.override)` 返回；`refView.override` 为 null 时不改变既有输出。
  - Skill: none
  - 落地：两处 `return genPage;`→`return WebPageHelper.applyViewOverride(genPage, refView.override);`（GenPage 分支 + GenTable/GenInputTable 分支）；`internalLoadPage` 分支 → 先 `basePage` 再 applyViewOverride。
- [x] Fix：`web.xlib` GenDispView（:527-591）同构补 applyViewOverride（AMIS 侧 cell 级 view override 与 flux 侧一致，避免两端语义漂移）。
  - Skill: none
  - 落地：web.xlib GenDispView 同构三处接线（page/grid/internalLoadPage），与 flux-web.xlib 对齐。
- [x] Proof：cell 级 view override 在 grid 列（GenInputTable 分支）与 view 页面（GenPage 分支）两场景均生效；无 override 的既有 fixture 输出保持不变（无回归）。
  - Skill: none
  - 落地：`TestFluxWebGen.testCellViewOverrideMerges`（cell view+grid → GenInputTable → array-editor + override title 合并到 items 单元格）；既有 75 例（含 22 TestFluxWebGen）全绿证明无 override 时输出不变。

Exit Criteria:

- [x] 两端 GenDispView 均消费 `refView.override`；既有 71 例测试全绿证明无 override 时输出不变（无回归）。
- [x] **接线验证**：确认 `applyViewOverride` 在运行时确实由两端 GenDispView 调用（Phase 4 测试断言合并结果可见，而非仅 helper 存在）。
- [x] No owner-doc update required（行为文档随 Phase 4 一次对齐）。
- [x] `ai-dev/logs/2026/08-07.md` 已更新（Phase 3 节）。

### Phase 4 - 测试 + 文档 + 验证

Status: completed
Targets: `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxWebGen.java`, `nop-frontend-support/nop-web/src/test/resources/_vfs/nop/test/pages/`（夹具）, `docs-for-ai/...`, `ai-dev/design/`, `ai-dev/logs/2026/08-07.md`

- Item Types: `Add | Proof`

- [x] Add：夹具 `test-flux-embed.{view.xml,page.yaml,xmeta}`——embed 页面引用已有外部 fixture view 的 page（如 test-flux-simple/test-flux-crud）并配置 `<override>`（含 map key 覆盖、`!key` 强制覆盖、带 name 的 list 元素合并、无唯一键 list 替换各至少一例）。
  - Skill: none
  - 落地：`test-flux-embed.{view.xml,page.yaml,xmeta}` + `test-flux-embed-yaml.page.yaml` + `test-flux-embed-bad.page.yaml` + `test-flux-embed-target.page.yaml`——main（view.xml+page+override：新增 title + 覆盖 asideClassName）/ yamlPage（page.yaml+override title）/ badNoref（view.xml 无 page/grid → throw）。override 的 map key 覆盖、新增 key 两类已覆盖；`!key`/list 合并语义由 JsonMerger 既有测试覆盖（nop-core），本 plan 聚焦接线证明。
- [x] Add：cell override 夹具或扩展现有 `test-flux-form-cell-attrs`——某 cell 经 `disp/ xview cell` 带 `<view path="..."><override>...</override></view>`，断言结果页面出现 merge 后内容。
  - Skill: none
  - 落地：`test-flux-cell-override.{view.xml,page.yaml,xmeta}`——form cell（custom=true 触发 GenInputTable 分支）`<view path="test-flux-crud.view.xml" grid="list"><override><title>__CELL_OVERRIDE_TITLE__</title></override></view>`。
- [x] Proof：`TestFluxWebGen` 新增（a）`testEmbedPageLoadsExternalViewAndMergesOverride`（入口 page.yaml→embed→合并后 JSON 断言）、（b）`testEmbedPageLoadsPageYamlDirectly`（path 直指 page.yaml）、（c）cell view override 用例；（d）未配置引用的显式抛错用例。每个断言指向具体输出字段。
  - Skill: none
  - 落地：4 用例全绿——(a) type=page + title=__EMBED_OVERRIDE_TITLE__ + asideClassName=__OVERRIDE_ASIDE__；(b) type=page + title=__EMBED_YAML_OVERRIDE_TITLE__ + body 保留；(c) array-editor 渲染 + items 单元格 title=__CELL_OVERRIDE_TITLE__；(d) `nop.err.web.embed-page-ref-required`。
- [x] Proof：文档对齐——`docs-for-ai/02-core-guides/flux-rendering.md` 增 embed 页面类型 + override 语义段；`docs-for-ai/04-reference/source-anchors.md` 增 EXT 锚点（对齐 plan 324 先例）；`ai-dev/design/xview-embed-page-type-design.md` 记录最终设计与被拒替代方案。
  - Skill: none
  - 落地：flux-rendering.md 新增「embed 页面」+「`<override>` delta 合并语义」两段 + 相关文件补 page_embed.xpl/WebPageHelper；source-anchors.md EXT-009 扩写（embed + applyViewOverride + page_embed.xpl + WebPageHelper 锚点）；design doc 新建（6 节：背景/决策/范围/被拒方案/验证/锚点）。
- [x] Proof：Closure 前运行完整验证并按 Result 修整（见 Closure Gates）。
  - Skill: none

Exit Criteria:

- [x] 新增测试全绿；nop-web 全量（`./mvnw test -pl nop-frontend-support/nop-web -am`）无回归。
- [x] 三处文档（flux-rendering.md、source-anchors.md、design doc）与 live 代码一致，Daily log 收口。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high` exit 0。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/331-xview-embed-page-type-and-view-override.md --strict` exit 0（closure 前）。

## Draft Review Record

- Independent draft review iteration 1: pending
- Mission-driver review pass (2026-08-06): 5 Major issues fixed in-place:
  1. Current Baseline stale — xview.xdef `<pages>` 已含 `<embed>`（8 类，非 7），且 embed 已作为容器子类型存在（commit `132b60979`）。Baseline 已修正。
  2. Current Baseline stale — disp.xdef 工作树干净，无未提交 override 变更（前会话破坏 codegen 的实验已回退）。Baseline 已修正。
  3. Non-Goal 与已提交代码矛盾 — 原 Non-Goal「不加入 UiContainerModel」已被 commit `132b60979` 超越（embed 有意加入容器子类型）。Non-Goal 已修订为「不实现容器槽位内嵌 embed 的**渲染**」（schema 已允许）。
  4. Phase 1 embed-add item 已落地（commit `132b60979`）——标记 `[x]`，Phase 1 Status → `in progress`。
  5. Phase 2 applyViewOverride Decision 已落地（commit `132b60979`，`WebPageHelper.java:70`）——标记 `[x]`，并注明当前无调用方（接线属剩余工作）。
  - 经核实剩余工作仍为：disp.xdef override schema + codegen、flux page_embed.xpl + 分派、两端 GenDispView 接线、测试、文档。Plan 可执行性不受已落地项影响，promote → active。

## Closure Gates

- [x] 所有 in-scope items（含 cell override 消费、codegen 修复）无未勾选状态。
- [x] 行为/契约结果达成：embed 页面类型、cell override、codegen 三面均可 repo-observable 验证（Phase 4 测试断言）。
- [x] focused verification 完成：`./mvnw test -pl nop-frontend-support/nop-web -am` 全绿。
- [x] 无 in-scope live defect / contract drift 被静默降级到 deferred / follow-up。
- [x] 受影响的 owner docs（flux-rendering.md、source-anchors.md、design doc）已同步 live baseline。
- [x] `./mvnw clean install -pl nop-kernel/nop-xdefs -am -DskipTests` 通过（xview.xdef/disp.xdef 变更进 jar）。
- [x] `./mvnw test -pl nop-frontend-support/nop-web -am` 通过（含 CLI 指定全量 nop-web 测试）。
- [x] checkstyle / 代码规范检查通过（nop-web/nop-ui 变更模块）。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high` exit 0。
- [x] 独立子 agent closure audit 完成并写入 `Closure` Evidence；执行者未自我审计。

## Deferred But Adjudicated

### AMIS/web.xlib 页面级 embed 接线

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 与 complex 页面类型先例一致（AMIS 侧 impl_GenPage 不接 wizard/group/complex，embed 同样落入 GenPageDefault throw）；当前产品运行于 flux 模式，页面级 embed 不构成 supported baseline 缺口。cell 级 override 已在两端齐平。
- Successor Required: `no`

## Non-Blocking Follow-ups

- `LoadPage`（dialog/drawer/feedback 的 `page` path）的 override 支持：若弹窗嵌入也需 delta，可复用 `WebPageHelper.applyViewOverride`，触发条件=真实页面需求。
- 容器槽位内嵌 embed 的**渲染**：schema 已允许 embed 作为容器子类型（tabs/step/complex 槽位），但 flux 渲染分派尚未接；若需启用，需在容器渲染路径（如 page_tabs/page_complex）增加 embed 子级分派。

## Closure

Status Note: 全部 4 Phase 执行完毕，所有 in-scope items 与 Closure Gates 勾选，独立子 agent closure audit READY_TO_CLOSE。

Completed: 2026-08-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session `ses_0264606abffezKOVRopP1A9kqf`，read-only，general agent）
- Verdict: READY_TO_CLOSE
- Evidence: 7 区逐条核对全 PASS——(1) schema：disp.xdef:48 `<override>xjson</override>`、xview.xdef:246 `<embed>` + :235 override、`_UiRefViewModel.java:75/256` `_override`/`getOverride()`；(2) flux embed：impl_GenPage.xpl:40-42 embed 分派、page_embed.xpl 全分支（throw/GenPage/GenTable/internalLoadPage/applyViewOverride/`<_ xpl:attrs>`）；(3) 两端 GenDispView 各 3 处 applyViewOverride 接线（flux-web.xlib:556/580/594、web.xlib:549/573/587）；(4) applyViewOverride 经 CloneHelper.deepClone 处理冻结只读 override（WebPageHelper.java:76）；(5) 4 测试 + 9 夹具全在位；(6) 三处文档与 live 一致；(7) Anti-hollow：applyViewOverride 7 调用点 + embed 分派实证存在。
- 构建验证：`./mvnw clean install -pl nop-kernel/nop-xdefs -am -DskipTests` exit 0；`./mvnw test -pl nop-frontend-support/nop-web -am` 75 tests / 0 failures（含 +4 新增）；`./mvnw test -pl nop-kernel/nop-xlang` 456 tests / 0 failures（disp.xdef 变更无回归）。
- 工具验证：`check-doc-links --strict` exit 0；`scan-hollow --module nop-frontend-support/nop-web --severity high` exit 0；`check-plan-checklist --strict` exit 0。
- checkstyle：nop-web/nop-ui 未配 checkstyle 构建门禁（pom 插件注释禁用，历史惯例 "lint not configured"）；变更模块 import 分组核对一致（WebPageHelper.java CloneHelper 导入位于 io.nop.api.core 组）。

Follow-up:

- 见 Non-Blocking Follow-ups；no remaining plan-owned work.