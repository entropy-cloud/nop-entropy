# AMIS 遗留状态说明（Deprecated）

> AMIS 前端支持已整体进入 **deprecated** 状态。Nop 平台当前默认且唯一受支持的页面渲染框架是 **Flux**。本文档只服务一个场景：存量项目仍在使用 amis，需要知道相关文件在哪里、如何以 legacy 方式继续运行。新项目一律使用 Flux，见 `02-core-guides/flux-rendering.md` 与 `02-core-guides/frontend-rendering-pipeline.md`。

## 当前口径

- 前端渲染由配置 `nop.web.render-mode` 控制，默认值 `flux`（定义于 `nop-frontend-support/nop-web/src/main/java/io/nop/web/WebConfigs.java` 的 `CFG_WEB_RENDER_MODE`）。
- Flux 管线可自动消费存量页面模型：flux 模式下加载 `page.yaml` 时优先使用同目录 `flux.yaml` 变体，无变体的页面经 `xlib/web/impl_flux_mode.xpl` 与 `view-gen.xlib` 切换到 `flux-web.xlib`/`flux-control.xlib` 管线，并把 AMIS 形态的模型属性（`visibleOn`、`actionType` 等）规范化为 Flux Schema。因此存量 `*.view.xml` / `*.page.yaml` 无需手工改造即可运行在 Flux 下。
- 平台文档不再维护 amis 的用法教学；原 `02-core-guides/` 下的 AMIS 专属渲染文档已删除，其中仍有效的运行时机制已并入 `02-core-guides/flux-rendering.md` 与本文档。

## amis 相关文件清单

### 文档类（已废弃，不再维护）

- 原平台文档中的 AMIS 专属渲染指南（位于 `docs-for-ai/02-core-guides/`，已随本次 deprecation 删除）— 去向为本文档与 `flux-rendering.md`。
- `docs/theory/amis/`、`docs/dev-guide/xui/amis.md`、`docs-en/` 对应镜像 — 位于历史遗留文档目录 `docs/`，不再接收维护，不作为开发依据。

### 代码 / 资源类（保留作 legacy 运行时，不再演进）

- `nop-frontend-support/nop-web-site-legacy` — 旧版 amis 前端站点静态资源（含 amis 运行时 js）。仅存量项目使用。
- `nop-frontend-support/nop-web-amis-editor` — amis 官方编辑器的静态资源壳。仅存量项目使用。
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web.xlib` 及 `xlib/web/` — AMIS 页面生成库。
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/control.xlib` — AMIS 控件映射库。
- `io.nop.web.page.WebPageHelper`（`nop-web` 模块）中的 amis scope className、dialog/drawer 处理逻辑。
- XDef 模型层：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/action.xdef` 的 `<onEvent>` 元素为 AMIS 专用（已标注 deprecated）；Flux 使用 `<onClick>`。

### 模型文件类（存量页面）

- 存量 `*.view.xml` 中引用 `control.xlib` 的页面、`*.page.yaml` 中引用 `web.xlib` 的页面：均为 AMIS 形态模型，由 Flux 管线自动规范化，无需改造；新增页面应直接使用 Flux 口径（`flux-control.xlib` / `flux-web.xlib` 或 `*.flux.yaml`）。

## 继续使用 amis（legacy 方式）

仅当存量项目无法立即迁移时使用，无官方迁移支持，仅作存量兼容：

1. 引入 legacy 依赖（BOM `io.github.entropy-cloud:nop-bom` 已登记版本管理）：
   - `io.github.entropy-cloud:nop-web-site-legacy` — 旧版 amis 前端站点；
   - `io.github.entropy-cloud:nop-web-amis-editor` — amis 编辑器（如需）。
2. 配置渲染模式回退：`nop.web.render-mode: amis`。
3. 该组合仅供过渡；平台新能力（新增控件、新交互特性）只保证在 Flux 侧提供。

## 相关文档

- `02-core-guides/flux-rendering.md` — Flux 渲染 owner 文档（含 AMIS 形态 → Flux Schema 的 NormalizeAction 转换机制）。
- `02-core-guides/frontend-rendering-pipeline.md` — 框架无关的页面生成管线。
