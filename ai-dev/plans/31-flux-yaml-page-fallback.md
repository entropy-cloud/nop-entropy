# 31 Flux.yaml 页面文件回退机制

> Plan Status: completed
> Last Reviewed: 2026-08-01
> Source: 用户需求（后台增加对 `flux.yaml` 的支持，Flux 模式下优先覆盖 `page.yaml`）+ `docs-for-ai/02-core-guides/flux-rendering.md` 新增章节 + `ai-dev/analysis/2026-07-11-flux-web-xlib-design-analysis.md`（§6.3 早期构想）
> Related: `docs-for-ai/02-core-guides/flux-rendering.md`（「flux.yaml 页面文件」章节已先行落地）

## Purpose

让后端支持 `flux.yaml` 页面文件：当 `nop.web.render-mode=flux` 时，加载任何 `*.page.yaml` 之前优先检测同目录同名 `*.flux.yaml`，命中则改加载 `flux.yaml`。覆盖顶层页面加载与子页面（`tabs`/`wizard` 经 `LoadPage`）两个入口，使 `flux.yaml` 成为 `page.yaml` 的 Flux 专属覆盖。文档已先行写入 `docs-for-ai`，本计划负责把行为落地到代码并验证。

## Current Baseline

基于 live repo 核对（2026-08-01）：

- 页面加载顶层入口：`PageProvider.getPage(path, locale)`（`nop-frontend-support/nop-web/src/main/java/io/nop/web/page/PageProvider.java:175`）调用 `ResourceComponentManager.instance().loadComponentModel(locale+'|'+path)`（`:180`），由 RCM 按 `fileType` 分发到注册的 loader。
- 页面 loader：`PageModelLoaderFactory.PageModelLoader.loadObjectFromPath(path)`（`nop-frontend-support/nop-web/src/main/java/io/nop/web/page/PageModelLoaderFactory.java:23`）拆分 `locale|path`，`VirtualFileSystem.instance().getResource(resPath)`（`:34`）后调用 `getPageProvider().loadPage(resource, locale, resolveI18n)`（`:35`）。**当前没有任何按扩展名回退/兄弟文件查找逻辑。**
- 文件类型注册：`nop-frontend-support/nop-web/src/main/resources/_vfs/nop/core/registry/page.register-model.xml` 注册了 `page.xml`/`page.json`/`page.json5`/`page.yaml` 四种 `xpage` loader。`flux.yaml` **未注册**。
- 扩展名工具：`StringHelper.fileType(path)`（`nop-kernel/nop-commons/.../StringHelper.java:2800`）取最后两个 dot 段，故 `main.flux.yaml` → `"flux.yaml"`，`main.page.yaml` → `"page.yaml"`。本计划显式注册 `flux.yaml` loader，不依赖 RCM 的未注册类型 fallback。
- **缓存模型**：`ResourceComponentManager` 按 model type（页面为 `xpage`，见 `page.register-model.xml` 的 `<model name="xpage">`）缓存，键为 `locale|path`，**渲染模式不是缓存键的一部分**。`clearCache("xpage")` 清页面模型缓存，`clearCache("xlib")` 清 xlib 编译缓存（含 `impl_flux_mode.xpl` 的 post-extends 结果）。既有 `TestRenderModeSwitch` 切模式时仅 `clearCache("xlib")`。
- 白名单：`WebConstants.PAGE_FILE_TYPES`（`nop-frontend-support/nop-web/src/main/java/io/nop/web/WebConstants.java:41`）= `{page.xml, page.yaml, page.json, page.json5}`。`PageProviderBizModel.getPage`（`.../biz/PageProviderBizModel.java:40`）调用 `WebPageHelper.checkPageFile(path)`（`.../page/WebPageHelper.java:50`）按此白名单校验；`flux.yaml` 路径当前会被拒绝。
- Flux 模式开关：`WebConfigs.CFG_WEB_RENDER_MODE` = `nop.web.render-mode`（默认 `amis`，可设 `flux`）。Java 侧经 `WebPageHelper.isFluxMode()`（`WebPageHelper.java:92`）读取；XPL 侧经 `$config.var('nop.web.render-mode','amis')` 读取（如 `impl_flux_mode.xpl:3`）。
- 现有 Flux 自动切换（**与本机制互补、不冲突**）：同一个 `page.yaml` 经 `web.xlib` 的 `x:post-extends`（`impl_flux_mode.xpl`）在加载期把 `GenPage/GenForm/GenGrid` 替换为 `flux-web` 版本；`view-gen.xlib:DefaultViewPostExtends` 把 `controlLib` 换成 `flux-control.xlib`。这些都不涉及 `flux.yaml` 文件。
- 子页面加载入口：`flux-web.xlib:LoadPage`（`nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib:903`，Flux 模式下 GenPage 链路实际调用的 LoadPage）当前逻辑（`:922` 直接 `.page.yaml` 分支 / `:930` pageId 分支）只查找 `*.page.yaml`，无 flux 回退。
- **关键架构事实（经独立验证）**：XDSL 的 `x:extends`（含 `x:gen-extends` 产物中的 `x:extends`）解析子页面时**不走 RCM/loader**，而是经 `DeltaJsonLoader.loadFromPath`（`nop-core/.../json/delta/DeltaJsonLoader.java:73`）→ `XJsonLoader.loadJsonResource` 直接 raw parse，仅 `traceDepends`。因此：顶层回退只能放在 `PageModelLoader.loadObjectFromPath`（覆盖 `getPage` 顶层请求）；子页面回退必须改 `flux-web.xlib:LoadPage`（覆盖 `x:extends` 引用）。
- 缓存与依赖追踪：`PageProvider.loadPage`（`PageProvider.java:228`）对实际加载的 resource 调 `traceDepends`；子页面 `x:extends` 在 `DeltaJsonLoader.loadFromResource`（`:78`）调 `traceDepends`。回退到 `flux.yaml` 后，依赖会挂到 `flux.yaml` 路径，编辑 `flux.yaml` 可正确失效缓存。
- 现有相关测试：`TestRenderModeSwitch`（`nop-web/src/test/java/io/nop/web/page/TestRenderModeSwitch.java`）、`TestFluxPage`（`nop-auth-web`），fixture 在 `nop-web/src/test/resources/_vfs/nop/test/pages/`。

## Goals

- `flux.yaml` 成为标准页面文件类型：可被 RCM 加载、可经 `PageProvider__getPage` 直接按路径请求。
- Flux 模式（`nop.web.render-mode=flux`）下，加载 `*.page.yaml` 时：同目录存在 `*.flux.yaml` 则**改加载 `flux.yaml`**（顶层入口 + 子页面入口均生效）。
- AMIS 模式下 `flux.yaml` 不被自动使用，`page.yaml` 行为完全不变。
- 既有 Flux 自动切换机制、既有 flux/amis 页面测试不受影响（无回归）。

## Non-Goals

- 不改 `web.xlib:LoadPage`（AMIS 模式不应使用 flux 回退；Flux 模式下该 tag 不被 GenPage 链路触达）。
- **例外边界（已知受限，不修复）**：若某 `page.yaml` 的 `x:gen-extends` 显式调用 `web:LoadPage`（而非经 `flux-web` 链路），Flux 模式下该路径不走 flux 回退。属可接受的显式用法。
- `PageModelLoader.loadObjectFromResource`（`PageModelLoaderFactory.java:39`，按 resource 直载的另一入口）**不加回退**；本计划仅 `loadObjectFromPath` 覆盖 `getPage` 顶层路径。
- 不改 `getPageSource`/`savePageSource`（编辑器按显式路径读写；要编辑 `flux.yaml` 直接请求其路径）。
- 不引入"按请求参数/按用户切换渲染模式"的运行时动态切换。
- 不新增禁用回退的独立配置开关（仅由 `nop.web.render-mode=flux` 统一门控）。
- 不把 `validateAllPages`（`PageProvider.java:87`，目前只 glob `*.page.yaml`）扩展到校验 `flux.yaml`——作为可选 follow-up。

## Scope

### In Scope

- `page.register-model.xml`：注册 `flux.yaml` loader。
- `WebConstants`：新增 `FILE_TYPE_FLUX_YAML` 常量并加入 `PAGE_FILE_TYPES`。
- `WebPageHelper`：新增路径转换 helper（`*.page.yaml` → `*.flux.yaml`）。
- `PageModelLoaderFactory.PageModelLoader.loadObjectFromPath`：Flux 模式顶层回退。
- `flux-web.xlib:LoadPage`：Flux 模式子页面回退（两个分支）。
- 单元测试 + 端到端集成测试（顶层回退、AMIS 不回退、直接加载 flux.yaml、白名单、子页面回退）。

### Out Of Scope

- 代码生成（codegen）模板不为 `flux.yaml` 自动生成骨架——`flux.yaml` 由开发者按需手工创建。
- `web.xlib:LoadPage` 改动、编辑器端改动、运行时动态切换、`validateAllPages` 扩展。

## Execution Plan

### Phase 1 — Java：注册类型 + 白名单 + helper + 顶层回退

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/core/registry/page.register-model.xml`、`nop-frontend-support/nop-web/src/main/java/io/nop/web/WebConstants.java`、`nop-frontend-support/nop-web/src/main/java/io/nop/web/page/WebPageHelper.java`、`nop-frontend-support/nop-web/src/main/java/io/nop/web/page/PageModelLoaderFactory.java`

- Item Types: `Fix`（loader 回退）/ `Decision`（命名与门控）/ `Proof`（测试）

- [x] 在 `page.register-model.xml` 增加 `<loader fileType="flux.yaml" class="io.nop.web.page.PageModelLoaderFactory"/>`，使 `*.flux.yaml` 经 RCM 可加载（`StringHelper.fileType("main.flux.yaml")="flux.yaml"` 已能正确路由）。
- [x] 在 `WebConstants` 新增 `String FILE_TYPE_FLUX_YAML = "flux.yaml";`，并将 `flux.yaml` 加入 `PAGE_FILE_TYPES` 集合，使 `WebPageHelper.checkPageFile` 放行 `flux.yaml`。
- [x] 在 `WebPageHelper` 新增 public static helper（命名 `toFluxPagePath`）：输入一个以 `.page.yaml` 结尾的路径，返回把复合扩展名 `page.yaml` 整体替换为 `flux.yaml` 后的同目录路径；输入不满足（非 `page.yaml`）则返回 `null`。复用 `StringHelper.fileType` 判定。
- [x] 在 `PageModelLoaderFactory.PageModelLoader.loadObjectFromPath` 增加顶层回退：解析出 `resPath` 后，若 `WebConfigs.CFG_WEB_RENDER_MODE.get()` 为 `"flux"` 且 `resPath` 以 `.page.yaml` 结尾，则用 helper 计算 flux 路径，经 `VirtualFileSystem.instance().getResource(fluxPath)` 取 resource 并 `.exists()` 判定；存在则把传给 `loadPage` 的 resource 换成 flux resource。（**禁止静默跳过**：flux.yaml 不存在时维持原 `page.yaml` 加载，不得吞异常或返回空。）
- [x] 新增单元测试：断言 helper 对 `/a/b/main.page.yaml` → `/a/b/main.flux.yaml`、对 `main.page.xml` / `main.page.json` / `main.yaml` → `null`。
- [x] 新增顶层回退集成测试 fixture：`nop-web/src/test/resources/_vfs/nop/test/pages/test-flux-fallback/main.page.yaml`（含 AMIS 专属标记，如 `"__marker":"amis"` 或 amis 特有字段）+ `main.flux.yaml`（含 Flux 专属标记，如 `"__marker":"flux"`）。
- [x] **测试缓存前置条件（关键）**：因页面缓存键 `locale|path` 不含渲染模式，切换 `CFG_WEB_RENDER_MODE` 后**必须** `ResourceComponentManager.instance().clearCache("xpage")`（参考 `TestRenderModeSwitch` 清 `clearCache("xlib")` 的写法），否则 flux 模式的缓存结果会污染 amis 模式断言。测试在 flux 模式下 `pageProvider.getPage("/nop/test/pages/test-flux-fallback/main.page.yaml","")` 返回 flux 标记；在 amis 模式下（清缓存后）返回 amis 标记。
- [x] 新增直接加载测试：flux 模式与 amis 模式下 `pageProvider.getPage("/nop/test/pages/test-flux-fallback/main.flux.yaml","")` 均能加载并返回 flux 标记（证明 loader 注册生效，且直接访问不受模式开关限制）。
- [x] 新增白名单测试：`WebPageHelper.checkPageFile("/x/main.flux.yaml")` 不抛异常。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `StringHelper.fileType("main.flux.yaml")` 仍为 `"flux.yaml"`；`page.register-model.xml` 含 `flux.yaml` loader 行。
- [x] `WebConstants.PAGE_FILE_TYPES` 包含 `flux.yaml`；`checkPageFile` 对 `flux.yaml` 不抛错。
- [x] helper 单元测试通过，覆盖正例与负例（非 page.yaml 返回 null）。
- [x] 顶层回退：flux 模式请求 `main.page.yaml` 实际返回 `main.flux.yaml` 内容（断言 marker）；amis 模式返回 page.yaml 内容。
- [x] 直接加载 `main.flux.yaml` 在两种模式下均成功（loader 注册生效）。
- [x] **接线验证**：集成测试通过对比 marker 证明 `PageModelLoader` 在运行时确实把 resource 换成了 flux 兄弟文件（不只是类型存在）。
- [x] **无静默跳过**：flux.yaml 不存在时回退到 page.yaml 正常加载（fixture `test-render-mode-switch.page.yaml` 无 flux 兄弟，既有 `TestRenderModeSwitch` 仍通过）。
- [x] 受影响 owner doc `docs-for-ai/02-core-guides/flux-rendering.md`（已先行更新）与本阶段行为一致；`ai-dev/logs/` 当日条目将在 Phase 3 统一更新。

### Phase 2 — XPL：flux-web LoadPage 子页面回退

Status: completed
Depends on: Phase 1（`WebPageHelper.toFluxPagePath` 必须先落地，Phase 2 复用之）
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`（`LoadPage` 标签，`:903-946`）

- Item Types: `Fix` / `Proof`

- [x] 修改 `flux-web.xlib:LoadPage`：在 `<source>` 内 `import io.nop.core.resource.ResourceHelper;` 旁新增 `import io.nop.web.page.WebPageHelper;`，并 `let fluxMode = WebPageHelper.isFluxMode();`（复用 Java 配置读取，与 `$config.var` 等价且 DRY）。
  - **直接路径分支**（当前 `:922` `page.endsWith('.page.yaml')`）：若 `fluxMode`，用 `WebPageHelper.toFluxPagePath(absPath)` 计算 flux 绝对路径，`ResourceHelper.resolve(fluxPath).exists()` 为真则改用 flux 路径作为 `x:extends` 目标。
  - **pageId 分支**（当前 `:930`）：构造 `path = filePath+page+'.page.yaml'`；若 `fluxMode`，`fluxPath = WebPageHelper.toFluxPagePath(path)`，`ResourceHelper.resolve(fluxPath).exists()` 为真则返回 `{ 'x:extends': fluxPath, ...dlgProps }`；否则维持原 page.yaml 查找 / GenPage 回退。两分支统一通过 helper 计算 fluxPath（DRY）。
- [x] 新增子页面回退端到端 fixture：在 `nop-web/src/test/resources/_vfs/nop/test/pages/test-flux-subpage/` 下放一个 Flux 模式会渲染 `tabs` 的 view，含**两个 tab** 以分别覆盖两个分支：
  - tab A 的 `page="subA"`（pageId 分支）+ 同目录 `subA.page.yaml`（amis 标记）+ `subA.flux.yaml`（flux 标记）；
  - tab B 的 `page="subB.page.yaml"`（直接路径分支）+ 同目录 `subB.page.yaml`（amis 标记）+ `subB.flux.yaml`（flux 标记）；
  - 以及 `main.page.yaml`（`x:gen-extends` 调 `web:GenPage`，Flux 模式经自动切换走 flux-web）。
- [x] **测试缓存前置条件（关键）**：fixture 依赖 `impl_flux_mode.xpl` 的 xlib post-extends 自动切换，该替换在 xlib **编译期**完成且 `web.xlib` 被缓存。测试 setUp/tearDown 切换 `CFG_WEB_RENDER_MODE` 后必须 `clearCache("xlib")`（触发 web.xlib 重新编译）**并** `clearCache("xpage")`（触发页面重新生成），否则 flux 模式下仍输出 amis JSON。
- [x] 新增子页面回退测试：flux 模式下 `getPage(.../main.page.yaml)` 渲染结果**同时**含 tab A 与 tab B 的 flux 标记（证明 pageId 分支与直接路径分支都把 `x:extends` 指向了各自 `.flux.yaml`）；amis 模式下含 amis 标记。（AMIS 断言依赖未被修改的 `web.xlib:LoadPage` 两分支结构，见 `web.xlib:845/855`，本计划不改 web.xlib。）
- [x] 附带覆盖说明：`LoadPage` 的 `dialog` 属性路径同样受益于本次回退（弹窗/抽屉子页也优先 flux.yaml），风险低，作为附带覆盖不单列测试。

Exit Criteria:

- [x] `flux-web.xlib:LoadPage` 两个分支均在 `fluxMode` 时优先检测 `*.flux.yaml`；`ResourceHelper` 与 `WebPageHelper.toFluxPagePath` 调用可见于 tag source。
- [x] 子页面端到端测试：flux 模式渲染结果**同时**含 tab A（pageId 分支）与 tab B（直接路径分支）的 flux 标记；amis 模式含 amis 标记。
- [x] **端到端验证**（规则 #22）：从 `getPage(main.page.yaml)` 入口 → GenPage（自动切换）→ tabs → `LoadPage(sub)` → `x:extends sub.flux.yaml` → 最终 JSON 含 flux 子页标记，完整路径跑通。
- [x] **接线验证**（规则 #23）：测试断言证明 `flux-web:LoadPage` 两个分支在运行时都把 `x:extends` 指向了对应 `.flux.yaml`（通过 flux 标记出现判定），而非仅 tag 存在。
- [x] **无静默跳过**：无 flux 兄弟时回退到原 page.yaml/GenPage 路径；既有 `TestFluxPage`、`TestRenderModeSwitch` 不回归（两者已重跑通过）。
- [x] `ai-dev/logs/` 当日条目将在 Phase 3 统一更新。

### Phase 3 — 验证与收口

Status: completed
Targets: 全模块构建 + 文档一致性 + 日志

- Item Types: `Proof`

- [x] 运行 `./mvnw test -pl nop-frontend-support/nop-web -am`，全部通过（含本计划新增测试与 `nop-web` 既有 flux/page 测试 `TestRenderModeSwitch`）。
- [x] 运行 `./mvnw test -pl nop-auth/nop-auth-web -am`，全部通过（覆盖下游消费 `flux-web.xlib` 的 `TestFluxPage`，验证无回归）。
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`，退出码 0。
- [x] 运行 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high`，退出码 0（无 high/critical 空壳实现发现）。
- [x] 核对 `docs-for-ai/02-core-guides/flux-rendering.md`「flux.yaml 页面文件」章节描述与实现行为一致（命名约定、门控、两入口、AMIS 不回退、直接访问）——逐条对照章节中的"覆盖规则"表与"实现锚点"列表。
- [x] 在 `ai-dev/logs/2026/08-01.md` 追加本特性落地记录（见 `00-log-writing-guide.md`）。

Exit Criteria:

- [x] `./mvnw test -pl nop-frontend-support/nop-web -am` 退出码 0。
- [x] `./mvnw test -pl nop-auth/nop-auth-web -am` 退出码 0（`TestFluxPage` 无回归）。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high` 退出码 0。
- [x] doc-link checker 退出码 0。
- [x] 文档与实现一致性核对完成（逐条对照章节，无 drift）。
- [x] `ai-dev/logs/2026/08-01.md` 已更新。
- [x] No owner-doc update required 之外的项均已落地（本特性 owner doc 为 `flux-rendering.md`，已更新）。

## Closure Gates

> 纯代码计划：构建/测试条目为硬门禁。

- [x] `flux.yaml` 已注册为 `xpage` 文件类型，且在 `PAGE_FILE_TYPES` 白名单内。
- [x] Flux 模式下顶层入口与子页面入口均优先使用 `flux.yaml`（各有端到端测试证明）。
- [x] AMIS 模式下 `page.yaml` 行为不变；`flux.yaml` 不被自动使用。
- [x] 既有 flux/page 测试（`TestRenderModeSwitch` in nop-web、`TestFluxPage` in nop-auth-web）无回归。
- [x] 新增功能均有对应测试（helper 单测、顶层回退、直接加载、白名单、子页面两个分支回退）。
- [x] 无空壳实现 / 无静默跳过（flux.yaml 缺失时正常回退 page.yaml，不吞异常）。
- [x] `./mvnw test -pl nop-frontend-support/nop-web -am` 通过。
- [x] `./mvnw test -pl nop-auth/nop-auth-web -am` 通过（`TestFluxPage` 无回归）。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high` 退出码 0。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] owner doc `flux-rendering.md` 与 live 行为一致。
- [x] 独立子 agent closure-audit 已完成并记录证据。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/31-flux-yaml-page-fallback.md --strict` 退出码 0。

## Deferred But Adjudicated

### validateAllPages 覆盖 flux.yaml

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `PageProvider.validateAllPages`（`PageProvider.java:87`）当前仅 glob `*.page.yaml`，不校验 `flux.yaml`。本特性核心是"加载时回退"，校验覆盖是独立的健壮性增强；`flux.yaml` 已注册 loader，直接 `getPage` 可发现其错误，不阻塞当前 contract。
- Successor Required: no

## Non-Blocking Follow-ups

- 可选：让 codegen 模板支持按 `ext:web-renderer="flux"` 自动生成 `flux.yaml` 骨架（当前需手工创建）。
- 可选：`validateAllPages` 扩展为同时校验 `*.flux.yaml`。

## Closure

Status Note: flux.yaml 页面回退机制已在顶层入口（`PageModelLoader.loadObjectFromPath`）与子页面入口（`flux-web.xlib:LoadPage` 两分支）完整落地，loader 注册/白名单/helper 齐备；flux 模式优先 flux.yaml、amis 模式不回退、直接访问不受模式限制的行为经独立审计重跑测试验证。所有硬门禁通过，无空壳实现，无静默跳过。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent（fresh session `ses_044fd86caffeVapoEK1dyZ8nQG`，非实现者）
- Audit Session: closure-audit-task-31
- Evidence:
  - Phase 1 Exit Criteria（8/8 PASS）：
    - `page.register-model.xml:8` 含 `flux.yaml` loader 行；`StringHelper.fileType("main.flux.yaml")` 仍为 `flux.yaml`。
    - `WebConstants.java:41,43-44` `FILE_TYPE_FLUX_YAML` 定义且在 `PAGE_FILE_TYPES` 内；`checkPageFile` 对 `flux.yaml` 不抛错。
    - helper 单测 `TestFluxPageFallback.testToFluxPagePath` 覆盖正例（`main.page.yaml`→`main.flux.yaml`）与负例（page.xml/page.json/main.yaml/null→null），通过。
    - 顶层回退：flux 模式 `getPage(main.page.yaml)` 返回 `__RENDERED_BY_FLUX__`；amis 模式返回 `__RENDERED_BY_AMIS__`。
    - 直接加载：`testDirectLoadFluxYaml` 两种模式下加载 `main.flux.yaml` 均成功。
    - 接线验证：`PageModelLoaderFactory.java:38-46` 运行时确实 swap resource（marker 对比断言证明）。
    - 无静默跳过：flux.yaml 缺失回退 page.yaml，`TestRenderModeSwitch`（无 flux 兄弟）3/3 通过。
  - Phase 2 Exit Criteria（6/6 PASS）：
    - `flux-web.xlib:911` import WebPageHelper；`:913` `fluxMode=WebPageHelper.isFluxMode()`；直接分支 `:928-932`、pageId 分支 `:942-946` 均在 fluxMode 时优先 flux.yaml。
    - 子页面端到端：`testSubpageFallbackFluxMode` 同时断言 `__SUB_A_FLUX__`（pageId 分支）与 `__SUB_B_FLUX__`（直接分支）；amis 模式断言 amis 标记。
    - 端到端路径 `getPage(main.page.yaml)`→GenPage(自动切换)→tabs→LoadPage→`x:extends sub.flux.yaml` 跑通。
    - 接线验证：两分支 `x:extends` 运行时指向 flux.yaml（flux 标记出现判定）。
    - 无静默跳过：无 flux 兄弟回退原 page.yaml/GenPage；`TestFluxPage` 12/12 无回归。
  - Phase 3 Exit Criteria（7/7 PASS）：测试独立重跑见下方命令退出码；doc 一致性核对无 drift；`ai-dev/logs/2026/08-01.md` 已含本特性条目。
  - Closure Gates（12/12 PASS）：逐条对照 live code/测试满足。
  - 命令退出码：
    - `node ai-dev/tools/check-doc-links.mjs --strict` → 0（0 errors）
    - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support/nop-web --severity high` → 0（0 findings）
    - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/31-flux-yaml-page-fallback.md --strict` → 0
    - `./mvnw test -pl nop-frontend-support/nop-web -am`（含 TestFluxPageFallback 5/5、TestFluxSubpageFallback 2/2、TestRenderModeSwitch 3/3）→ BUILD SUCCESS，0 failures
    - `./mvnw test -pl nop-auth/nop-auth-web -am -Dtest=TestFluxPage` → BUILD SUCCESS，12 run 0 failures（1 pre-existing skip）
  - Anti-Hollow 检查：顶层与子页面端到端调用链经代码追踪 + marker-对比测试证明运行时连通；`scan-hollow-implementations.mjs` 退出码 0；flux.yaml 缺失显式回退 page.yaml 而非吞异常。
  - Deferred 项分类检查：仅 `validateAllPages`（`PageProvider.java:95` 仍只 glob `*.page.yaml`）作为 `out-of-scope improvement`，理由成立。无 in-scope live defect 被降级。

Follow-up:

- 可选：codegen 模板按 `ext:web-renderer="flux"` 自动生成 `flux.yaml` 骨架。
- 可选：`validateAllPages` 扩展为同时校验 `*.flux.yaml`。
