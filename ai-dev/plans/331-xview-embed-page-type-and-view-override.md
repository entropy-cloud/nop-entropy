# 331 xview `<embed>` 页面类型 + 单元格 view override delta 合并

> Plan Status: draft
> Last Reviewed: 2026-08-05
> Source: `ai-dev/logs/2026/08-05.md`（disp.xdef override 实验已破坏 nop-ui codegen）、用户设计会话（新页面类型嵌入外部 viewPath 页面 + override 节点 delta 合并；cell `<view>` 已加 override，消费逻辑未写）
> Related: `324-flux-web-complex-page-rendering.md`、`323-flux-web-container-body-rendering.md`

## Purpose

在 xview.xdef 新增页面级 `<embed>` 页面类型（path 指向外部 viewPath / page.yaml，直接嵌入其页面 grep，并经 `<override>` 节点做 delta 合并），并把 disp.xdef `<view>`（UiRefViewModel）已声明但未消费的 `<override>` 接入 GenDispView 渲染路径（cell 级 \u540c构合并）。完成 schema → codegen → flux 渲染 → 两端 GenDispView 消费 → 测试 → 文档的整链落地与独立 closure audit。

## Current Baseline（live 状态，2026-08-05 逐一核实）

- **xview.xdef `<pages>`**（`xview.xdef:219-232`）：`crud/picker/simple/tabs/wizard/group/complex` 七类。complex 是唯一即带 `<xdef:bean-tag-prop="type">` 又带子槽位（header/footer/aside/body）的页面类型；另 `cc47a3589` 已为六类补 `name="!string"` 键属性。xview.xdef 模型为**运行时 xdef 动态模型**（`UiPageModel`/`UiCRUDModel` 等不在 nop-ui `_gen` Java 类中），修改 schema 无需 Java codegen。
- **分派链路**：flux `flux-web/impl_GenPage.xpl:37-39` 有 complex 分派；`web/impl_GenPage.xpl:18-34` 仅 crud/picker/simple/tabs，其余落入 `<thisLib:GenPageDefault>`（非 flux 侧 throw `nop.err.web.unknown-page-type`，web.xlib:29）。**complex 未在 AMIS/web.xlib 侧接线**（plan 324 只落地 flux），本 plan 遵循同一先例。
- **cell `<view>` 消费点**：`GenDispView` 存在于 flux-web.xlib:534-598 与 web.xlib:527-591（两份几乎同构）。分支：`refView.path.endsWith('.view.xml')` → `page`→GenPage / `grid`→GenTable|GenInputTable / `form`→throw not-impl；`otherwise` → `WebPageHelper.internalLoadPage(refView.path)`。**两处当前均不读取 `refView.override`**（消费未写）。
- **schema 先例**：`disp.xdef:39-46` `<view>`（UiRefViewModel）已含 `<data>xjson</data>`、`<actions>`，并在**未提交工作树**中加了 `<override xdef:unknown-attr="any">xjson</override>`（:44-45 注释「内容作为json和view加载的结果做delta合并」）。**关键事实**：`ai-dev/logs/2026/08-05.md:21` 记录该未提交 disp.xdef 变更会**破坏 nop-ui codegen/precompile**（前会话 `git stash` 实证为前置失败）。即现有 override schema 写法无法直接通过 codegen。
- **codegen 机制**：nop-ui 模型由 `precompile/gen-ui-xdsl.xgen` 渲染 `disp.xdef/form.xdef/grid.xdef`（exec-maven-plugin `CodeGenTask` postcompile 阶段执行），产物为 `nop-ui/src/main/java/io/nop/xui/model/_gen/*.java`。当前 `_gen/_UiRefViewModel.java`（11 字段）无 `override` 字段——需 schema 修正后 re-run codegen 生成 `_override` 字段（`Object`），`refView.override` 才可在 XPL 访问。
- **delta 合并原语**：`JsonMerger`（nop-core `lang/json/delta/JsonMerger.java`）schema-free merge：map 按 key 合并（`!key` 前缀=强制覆盖），list 按唯一键(id/name) 合并、无唯一键则整段替换，支持 `x:override` replace/remove。与页面 `x:extends` 加载（`WebPageHelper.internalLoadPage` / `PageProvider.loadPage` 走 `DeltaJsonOptions`/`loadDeltaBean`）同源。
- **测试基线**：`TestFluxWebGen` 18 例（含 complex 四槽位 `testComplexPageEmitsFourSlots`）；夹具 `test-flux-complex.{view.xml,page.yaml,xmeta}` 模式可复用（page.yaml 用 `x:gen-extends: <flux-web:GenPage view=... page=.../>`）。nop-web 全量 71 例 0 failures。nop-xdefs 是 jar 资源：**改 xview.xdef 后必须重装 jar**，否则运行时用旧 schema 报 `node-unexpected-tag-name`（plan 324 先例）。
- **剩余 gap**：页面级无「嵌入外部 view 页面 + override delta」能力；cell `<view>` 的 override schema 与消费逻辑均未落地（且当前 schema 破坏 codegen）；AMIS/web.xlib 侧 GenDispView 的 override 消费同样缺失。

## Goals

- xview.xdef `<pages>` 新增 `<embed>` 页面类型：`path`（v-path，指向外部 view.xml 或 page.yaml）+ `page/grid` 选择 + `<override>` delta 合并节点 + `<data>`。
- flux `impl_GenPage.xpl` embed 分派 + `page_embed.xpl` 渲染（复用 GenDispView 同构的加载分支 + override 合并）。
- GenDispView（flux-web + web 两库）在取到 refView 页面/grid 后应用 `refView.override`（JsonMerger delta 合并），单元与网格 cell 均生效。
- 修正 disp.xdef override schema 使 nop-ui codegen 通过，并重生成 `_UiRefViewModel._override`。
- 测试、文档（`flux-rendering.md`、`source-anchors.md`、design doc、daily log）与独立 closure audit。

## Non-Goals

- 不将 `<embed>` 加入 `UiContainerModel`（tabs/step/complex 槽位内嵌 embed 不在范围；embed 属页面级，与 complex 先例一致）。`<embed>` 页面可经 `<tab page="embedPageName">` / `<step page=` 由名称引用（引用 `pages` 下页面）。
- 不在 AMIS/web.xlib 侧接页面级 embed（沿用 complex 先例——AMIS 侧 `impl_GenPage` 不接 wizard/group/complex 也不会接 embed，落入 GenPageDefault throw）。
- 不实现 `refView.form` 分支（保持 throw not-impl）。
- 不改 nop-chaos-flux、不改前端运行时增量合并、不引入新的合并策略（复用 JsonMerger 既有语义，无唯一键 list 整段替换为已知约束并写入文档）。
- 不动 `LoadPage`（dialong/drawer/feedback）既有行为（其页面级 `x:extends` 已满足弹窗嵌入；如需 override 属 follow-up）。

## Scope

### In Scope

- `xview.xdef` `<pages>` 新增 `<embed>` 页面类型 schema + `UiEmbedPageModel`（运行时动态模型）。
- `disp.xdef` `<view>` override schema 修正为可通过 codegen 的写法，并确保 `_UiRefViewModel` 重生成出 `_override`（`Object`）字段。
- nop-xdefs jar 重装 + nop-ui codegen 重跑。
- flux `flux-web/impl_GenPage.xpl` embed 分派 + `flux-web/page_embed.xpl`。
- `GenDispView` 两库（flux-web.xlib:534 / web.xlib:527）override 合并消费 + 共享合并入口（`WebPageHelper` 静态 helper）。
- 测试夹具 `test-flux-embed.*` 与 cell override 夹具；`TestFluxWebGen` 用例。
- 文档：`docs-for-ai/02-core-guides/flux-rendering.md`、`docs-for-ai/04-reference/source-anchors.md`（EXT 锚点）、`ai-dev/design/xview-embed-page-type-design.md`、`ai-dev/logs/2026/08-05.md`。

### Out Of Scope

- 页面级 embed 的 AMIS/web.xlib 接线（GenPageDefault throw，见 Non-Goals）。
- `LoadPage`/dialog 弹窗 path 的 override 支持。
- `UiContainerModel` 槽位内嵌 embed、UI 层 schema 校验增强、i18n 同步。

## Execution Plan

### Phase 1 - schema + codegen（xview.xdef / disp.xdef / nop-ui）

Status: planned
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/xview.xdef`, `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/disp.xdef`, `nop-frontend-support/nop-ui/src/main/java/io/nop/xui/model/_gen/_UiRefViewModel.java`

- Item Types: `Decision | Fix | Proof`

- [ ] Decision：确定 `<override>` schema 形式上界——先最小复现 codegen 失败（`./mvnw clean install -pl nop-frontend-support/nop-ui -am -DskipTests` 或 isolate precompile），确认 `xdef:unknown-attr="any"` 是否为主因；若否，改用与 `<data>xjson</data>` 一致的 `<override>xjson</override>`（xjson 内容经 XML→JSON 解析后本身即任意结构，无需 unknown-attr）。
  - Skill: none（平台 xdef codegen 既有机制，无对应技能）
- [ ] Fix：按 Decision 修正 `disp.xdef` `<view>` 的 `<override>` 写法，使 nop-ui precompile/codegen 通过；re-run codegen 确认 `_gen/_UiRefViewModel.java` 新增 `_override`（`Object`）字段与 `getOverride()`。
  - Skill: none
- [ ] Add：`xview.xdef` `<pages>` 新增 `<embed>`（`xdef:name="UiEmbedPageModel"`，`xdef:ref="UiPageModel"`，`xdef:bean-tag-prop="type"`，`name/path/page/grid/title` 属性 + `<data>xjson</data>` + `<override>xjson</override>`），带注释说明语义与无唯一键 list 替换约束。
  - Skill: none
- [ ] Proof：`./mvnw clean install -pl nop-kernel/nop-xdefs -am -DskipTests`（重装 jar 使运行时读到新 schema）+ nop-ui codegen 构建通过；`__isVm` 无回归——nop-xdefs 测试不含 xview 用例，用 codegen 构建全过作证。
  - Skill: none

Exit Criteria:

- [ ] `./mvnw clean install -pl nop-frontend-support/nop-ui -am -DskipTests` BUILD SUCCESS，且 `_gen/_UiRefViewModel.java` 存在 `_override`/`getOverride()`。
- [ ] `xview.xdef` `<pages>` 含 `<embed>` 元素且 xview model 可在运行时解析（临时 fixture 或 schema 加载验证）。
- [ ] 工作树 `disp.xdef` 的 override 变更不再破坏 codegen（clean 基线：本 plan 内变更 + 既有未提交 disp.xdef 变更可一起 install 成功）。
- [ ] No owner-doc update required（Pure schema/codegen，无 public doc 行为面）。
- [ ] `ai-dev/logs/2026/08-05.md` 已更新（Phase 1 节）。

### Phase 2 - flux 渲染：embed 页面类型

Status: planned
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/impl_GenPage.xpl`, `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/page_embed.xpl`（新建）, `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`

- Item Types: `Add | Fix | Proof`

- [ ] Add：`flux-web/impl_GenPage.xpl` 增加 `<when test="${pageModel.type == 'embed'}"><c:include src="page_embed.xpl"/></when>`。
  - Skill: none
- [ ] Add：新建 `flux-web/page_embed.xpl`——按 `pageModel.path` 分派（`.view.xml` → `GenPage`/`GenTable`/`GenInputTable`；否则 `WebPageHelper.internalLoadPage`，含 page.yaml→flux.yaml 回退若适用），得到 base 页面 JSON 后若 `pageModel.override` 非空则 `JsonMerger.instance().merge(base, override)` 输出；未配置 path/page/grid 时显式抛错（不静默 noop）。宿主 view 不重复 objMeta/controlLib（由 path 指向的外部 view 自载）。
  - Skill: none
- [ ] Decision：合并入口落点——新增 `WebPageHelper.applyViewOverride(Map base, Object override)` 静态 helper（`null`/empty override 原样返回，否则 `JsonMerger.instance().merge`），Phase 2 与 Phase 3 两端 GenDispView、page_embed 共用，避免 xlib 内复制 merge 语义。
  - Skill: none
- [ ] Proof：`page_embed.xpl` 对 `path` 指向 view.xml（page/grid 分支）与 page.yaml 两种形态分别覆盖；override 合并结果可见；未配置引用时显式失败。
  - Skill: none

Exit Criteria:

- [ ] 新增 `WebPageHelper.applyViewOverride`（空 override 幂等返回 base；非空走 JsonMerger，`x:override` replace/remove、`!key`、无唯一键 list 替换语义均生效）。
- [ ] `page_embed.xpl` 存在且被 `impl_GenPage.xpl` 的 embed 分派命中；view.xml 与 page.yaml 两形态的 embed 页面均可加载并返回合并后 JSON。
- [ ] 未知/未配置引用显式抛 `nop.err.web.*`，非 silent no-op（对应 Minimum Rule #24）。
- [ ] **端到端验证**：`PageProvider.getPage` 经 page.yaml → GenPage → page_embed 从入口到输出去 override 合并结果完整跑通（用 Phase 4 夹具）。
- [ ] No owner-doc update required（渲染行为文档随 Phase 4 一次对齐）。
- [ ] `ai-dev/logs/2026/08-05.md` 已更新（Phase 2 节）。

### Phase 3 - 两端 GenDispView override 消费（cell 级）

Status: planned
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`, `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web.xlib`, `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/WebPageHelper.java`

- Item Types: `Fix | Proof`

- [ ] Fix：`flux-web.xlib` GenDispView（:534-598）各分支取得 base 页面/grid JSON 后，统一经 `WebPageHelper.applyViewOverride(base, refView.override)` 返回；`refView.override` 为 null 时不改变既有输出。
  - Skill: none
- [ ] Fix：`web.xlib` GenDispView（:527-591）同构补 applyViewOverride（AMIS 侧 cell 级 view override 与 flux 侧一致，避免两端语义漂移）。
  - Skill: none
- [ ] Proof：cell 级 view override 在 grid 列（GenInputTable 分支）与 view 页面（GenPage 分支）两场景均生效；无 override 的既有 fixture 输出保持不变（无回归）。
  - Skill: none

Exit Criteria:

- [ ] 两端 GenDispView 均消费 `refView.override`；既有 71 例测试全绿证明无 override 时输出不变（无回归）。
- [ ] **接线验证**：确认 `applyViewOverride` 在运行时确实由两端 GenDispView 调用（Phase 4 测试断言合并结果可见，而非仅 helper 存在）。
- [ ] No owner-doc update required（行为文档随 Phase 4 一次对齐）。
- [ ] `ai-dev/logs/2026/08-05.md` 已更新（Phase 3 节）。

### Phase 4 - 测试 + 文档 + 验证

Status: planned
Targets: `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxWebGen.java`, `nop-frontend-support/nop-web/src/test/resources/_vfs/nop/test/pages/`（夹具）, `docs-for-ai/...`, `ai-dev/design/`, `ai-dev/logs/2026/08-05.md`

- Item Types: `Add | Proof`

- [ ] Add：夹具 `test-flux-embed.{view.xml,page.yaml,xmeta}`——embed 页面引用已有外部 fixture view 的 page（如 test-flux-simple/test-flux-crud）并配置 `<override>`（含 map key 覆盖、`!key` 强制覆盖、带 name 的 list 元素合并、无唯一键 list 替换各至少一例）。
  - Skill: none
- [ ] Add：cell override 夹具或扩展现有 `test-flux-form-cell-attrs`——某 cell 经 `disp/ xview cell` 带 `<view path="..."><override>...</override></view>`，断言结果页面出现 merge 后内容。
  - Skill: none
- [ ] Proof：`TestFluxWebGen` 新增（a）`testEmbedPageLoadsExternalViewAndMergesOverride`（入口 page.yaml→embed→合并后 JSON 断言）、（b）`testEmbedPageLoadsPageYamlDirectly`（path 直指 page.yaml）、（c）cell view override 用例；（d）未配置引用的显式抛错用例。每个断言指向具体输出字段。
  - Skill: none
- [ ] Proof：文档对齐——`docs-for-ai/02-core-guides/flux-rendering.md` 增 embed 页面类型 + override 语义段；`docs-for-ai/04-reference/source-anchors.md` 增 EXT 锚点（对齐 plan 324 先例）；`ai-dev/design/xview-embed-page-type-design.md` 记录最终设计与被拒替代方案。
  - Skill: none
- [ ] Proof：Closure 前运行完整验证并按 Result 修整（见 Closure Gates）。

Exit Criteria:

- [ ] 新增测试全绿；nop-web 全量（`./mvnw test -pl nop-frontend-support/nop-web -am`）无回归。
- [ ] 三处文档（flux-rendering.md、source-anchors.md、design doc）与 live 代码一致，Daily log 收口。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high` exit 0。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/331-xview-embed-page-type-and-view-override.md --strict` exit 0（closure 前）。

## Draft Review Record

- Independent draft review iteration 1: pending

## Closure Gates

- [ ] 所有 in-scope items（含 cell override 消费、codegen 修复）无未勾选状态。
- [ ] 行为/契约结果达成：embed 页面类型、cell override、codegen 三面均可 repo-observable 验证（Phase 4 测试断言）。
- [ ] focused verification 完成：`./mvnw test -pl nop-frontend-support/nop-web -am` 全绿。
- [ ] 无 in-scope live defect / contract drift 被静默降级到 deferred / follow-up。
- [ ] 受影响的 owner docs（flux-rendering.md、source-anchors.md、design doc）已同步 live baseline。
- [ ] `./mvnw clean install -pl nop-kernel/nop-xdefs -am -DskipTests` 通过（xview.xdef/disp.xdef 变更进 jar）。
- [ ] `./mvnw test -pl nop-frontend-support/nop-web -am` 通过（含 CLI 指定全量 nop-web 测试）。
- [ ] checkstyle / 代码规范检查通过（nop-web/nop-ui 变更模块）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit 0。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high` exit 0。
- [ ] 独立子 agent closure audit 完成并写入 `Closure` Evidence；执行者未自我审计。

## Deferred But Adjudicated

### AMIS/web.xlib 页面级 embed 接线

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 与 complex 页面类型先例一致（AMIS 侧 impl_GenPage 不接 wizard/group/complex，embed 同样落入 GenPageDefault throw）；当前产品运行于 flux 模式，页面级 embed 不构成 supported baseline 缺口。cell 级 override 已在两端齐平。
- Successor Required: `no`

## Non-Blocking Follow-ups

- `LoadPage`（dialog/drawer/feedback 的 `page` path）的 override 支持：若弹窗嵌入也需 delta，可复用 `WebPageHelper.applyViewOverride`，触发条件=真实页面需求。
- `UiContainerModel` 槽位内嵌 embed：若 tabs/step/complex 槽位需要整页嵌入外部 view，需另设计容器包裹语义。

## Closure

Status Note:

Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- 见 Non-Blocking Follow-ups；no remaining plan-owned work.