# 2474 xdbg item 20 跨仓交付与版本配套（flux-bundle 清单 + 产物链刷新 + 宿主接入说明）

> Plan Status: completed
> Last Reviewed: 2026-10-18
> Source: roadmap item 20（ai-dev/backlog/xlang-web-debugger-roadmap.md）；设计权威 = ai-dev/design/xlang-web-debugger/05-frontend-architecture.md §3.5；复用先例 = flux-renderers-3d renderer-definitions（createLazyRendererComponent 懒边界）+ flux-renderers-form `/definitions` 轻子路径（form 先例，bundle 入口不触重 index）
> Related: plans/2473（item 14 REPL 服务端）、flux docs/plans/621（item 19 装配）

## Purpose

把 item 15-19 交付的三个 flux 新包（flux-code-editor / flux-xlang-lang / flux-xlang-debug）接入既有跨仓产物链：bundle 门面清单纳入、宿主共享 registry 验证、版本契约裁定、nop-web-site 预编译产物刷新、宿主接入说明。完成 definition（同步）与实现（异步 chunk）分离，使 codemirror/lezer 重依赖不进宿主主 chunk。

## Current Baseline

- **bundle 门面**（flux 仓 `packages/flux-bundle`，包名 `@nop-chaos/flux`）：`src/index.tsx` `registerDefaultFluxRenderers` 注册 basic/form/form-advanced/data/content/layout/scheduling/3d 八族；3d 族为懒边界先例——`flux-renderers-3d/src/renderer-definitions.ts:7-8` 定义/字段静态、`ThreeCanvasRenderer` 经 `createLazyRendererComponent(() => import('./renderer/three-canvas.js'))` 异步 chunk（泛型显式传 `<ThreeCanvasSchema>`）。
- **轻子路径先例**（form）：`@nop-chaos/flux-renderers-form/definitions` 子路径承载注册面、重 index 与 bundle 入口分离；`vite.workspace-alias.ts` 提供子路径别名解析。**约定文档** `docs/architecture/renderer-lazy-loading.md`（2026-10-10）：包入口不得静态 re-export 重实现模块，消费方经 `registerXxxRenderers` 注册。注：`flux-xlang-debug/src/index.ts` 现状静态 re-export 重实现（616-621 交付即如此，bundle 从未消费过它）——本 plan 不扩化该现状（见 Deferred），bundle 入口走新轻子路径。
- **打包校验**（`scripts/check-flux-bundle-pack.mjs`）：断言 tgz 必含 dist/index.js+index.d.ts+style.css；异步 chunk stems `echarts-setup-`/`chart-renderer-`/`three-canvas-` 必须独立落盘；主 chunk 头部 import 守卫重库清单 `['echarts','recharts','three']`（`startsWith(lib + '/')` 对 `@scoped` 包 stem 成立）；public-types.d.ts 不得引用未声明 @nop-chaos 包（peerDeps 对账；`FluxRendererRegistry` 已 import 并别名）。
- **打包脚本**（`scripts/pack-flux-bundle.mjs`）：build 门面 → `dist-packages/*.tgz`；releaseManifest 剥离 devDependencies（三包进 devDeps 不触发 internal-leak 判红）；`prepare-flux-bundle-dist.mjs` 以手写 `types/public-types.d.ts` 为 dts-bundle-generator 入口（手写 declare function 保留）。
- **产物链**：flux dist-packages tgz → nop-chaos-next 仓 `libs/nop-chaos-flux-0.1.0.tgz`（**git-tracked**；`apps/main/package.json` `"file:"` 依赖；`pnpm-lock.yaml` 记录其 integrity——换 tgz 必动 lock）→ `env -u VITE_ENABLE_MOCK pnpm build`（生产构建，`scripts/build-nop-shared.mjs` 注入 nop-shared import map；构建末尾两道 analyze 门 `analyze-main-package-graph --check`/`analyze-main-chunk-graph --check`）→ `scripts/sync-site.sh`（守卫 import map 标记；>3KB js/css gzip；TARGET 假设 `$ROOT_DIR/../nop-entropy/...` 同级布局，本机 worktree 布局不满足——逐条复刻脚本语义执行）→ 本仓 `nop-frontend-support/nop-web-site/src/main/resources/META-INF/resources/` → `./mvnw install -pl nop-frontend-support/nop-web-site -DskipTests`（jar 重装生效）。
- **宿主 chunk 命名**：next 侧 `main-bundle-chunk-names.mjs:120-124` 把 flux 异步实现 chunk 命名为 `pkg-flux-async-<stem>-*`（既有 `pkg-flux-async-three-canvas-*` 先例）——新 xlang chunk 预期 `pkg-flux-async-xlang-debug-editor-*`（以实际产物为准）；主包仍为 `pkg-nop-chaos-flux-*`。
- **宿主 registry**：next 仓 `apps/main/src/plugins/sharedModules.ts:21,57` 以 `import * as FluxLib from '@nop-chaos/flux'` 把门面命名空间挂 `globalThis.__NOP_SHARED__['@nop-chaos/flux']`；`nop-shared/flux.mjs` 门面文件由 next 构建自动再生成（新增门面导出自动可见）。
- **版本契约**：`HOST_API_VERSION` 出自 `@nop-chaos/shared`，经 `window.__NOP_HOST_API_VERSION__` 暴露（next bootstrap.ts）；扩展按 `minHostApiVersion` 校验。本 plan 不改 shared 公共面。
- **xlang 三包现状**：门面零引用（grep 无命中）；`flux-xlang-debug/src/assembly/renderer.tsx` 静态 import `XlangDebugEditor`（CE/lezer 重图）——无懒边界；`createCompileService` 同文件导出（仅类型依赖，无重依赖）。
- **demo 宿主**：playground `#/xlang-debug`（item 19 交付，workspace 引用，不经 bundle）。
- **并行工作区披露**：next 仓工作区已有并行在途改动（`pnpm-lock.yaml`、`flux-lib/ui/*`、`packages/tailwind-preset/*`）——本 plan 领地见 Scope，lockfile 按 hunk 对账；flux 仓 `pnpm check` 的"仅既有红"清单以执行时点实测为准（10-18 快照可能过期）。

## Goals

- xlang-debug renderer 以 three-canvas 同级懒边界进入 bundle：definition/field rules 同步注册，codemirror/lezer 实现落异步 chunk，主 chunk 零增量（相对引用头部守卫）。
- 打包校验覆盖新链路（异步 chunk stem + 懒边界破坏断言）。
- 端到端产物链刷新并逐环验证（tgz → next 宿主构建 → web-site 资源 → jar；registry 与版本契约完好）。
- 宿主接入说明与 demo 宿主裁定落档。

## Non-Goals

- 生产 admin 页的 xlang 调试页面本体（裁定见 Decisions ①）。
- HOST_API_VERSION 变更（shared 公共面零改动）。
- nop-chaos-next 宿主功能开发；REPL execute 面板接线（item 21）；Playwright e2e（item 22）；docs-for-ai 注册（item 23）。

## Decisions（设计裁定，实施后回写 owner doc）

1. **demo 宿主 = playground**（flux 侧 item 19 已交付）；生产 admin 页 = out of scope，宿主侧有需求时另立工作项（bundle/registry 能力本 plan 就绪）。
2. **bundle 入口 = 轻子路径 `@nop-chaos/flux-xlang-debug/definitions`**（form `/definitions` 先例）：承载 definition/field rules/register/createCompileService；组件经 `createLazyRendererComponent<XlangDebugEditorSchema>` 异步加载重实现。包 index.ts 维持现状不动（其重导出收敛归 renderer-lazy-loading.md 约定治理，见 Deferred）。
3. **依赖分类**：flux-bundle devDependencies 仅声明 `@nop-chaos/flux-xlang-debug`（打包进 dist；code-editor/xlang-lang 为其传递依赖经 alias 解析，不单独声明——防 knip unused 红）；不进 peerDeps；public-types.d.ts 只加函数声明不重导出 xlang 包类型（防 undeclared-type-ref 守卫红）。
4. **HOST_API_VERSION 不变更**：@nop-chaos/shared 零改动；登记裁定即可。

## Scope

### In Scope

- flux 仓：`packages/flux-xlang-debug`（懒边界拆分 + `/definitions` 子路径 + 守卫测试；`vite.workspace-alias.ts` 别名；package.json exports）、`packages/flux-bundle`（清单/导出/类型声明 + 测试）、`scripts/check-flux-bundle-pack.mjs`（stem + 主 chunk 守卫扩展）、`docs/architecture/renderer-lazy-loading.md` + `packages/flux-bundle/README.md`（接入说明）、`docs/logs/` 日志
- next 仓（产物链中转，只做刷新不改功能）：`libs/nop-chaos-flux-0.1.0.tgz` + `pnpm-lock.yaml`（仅 file: 依赖 integrity 相关 hunks，与并行在途改动对账，**不触碰 `flux-lib/`、`packages/tailwind-preset/`**）
- 本仓：`nop-frontend-support/nop-web-site` 资源刷新 + jar 验证、roadmap 翻转、日志

### Out Of Scope

- next 仓功能代码与其余在途改动；flux playground 页面功能改动（测试异步化适配除外）；后端仓代码

## Execution Plan

### Phase 1 - xlang-debug 懒边界拆分 + `/definitions` 轻子路径（flux 仓）

Status: completed
Targets: `packages/flux-xlang-debug/src/assembly/`、`vite.workspace-alias.ts`、`packages/flux-xlang-debug/package.json`

- Item Types: `Feature`

- [x] 迁移地图：①`createCompileService`/`CompileFetcherAdapterOptions` 从 `xlang-debug-editor.tsx` 迁至新轻模块 `assembly/compile-service.ts`（无 CM 依赖；对 `@nop-chaos/flux-xlang-lang` **仅允许 `import type`**——该包本体是重的，值形式 import 会把 lezer 图拉进主 chunk 且 R2-M1 勘正后无下游守卫可拦）；②现 `renderer.tsx:24-66` 的适配组件本体（createCompileService 调用 + env.fetcher 适配 + openSocket 提取 + getToken）整体迁入重模块 `xlang-debug-editor.tsx`，命名 `XlangDebugEditorRenderer`（重模块承接组件名；`index.ts` 的 `XlangDebugEditorRenderer` 导出源改指向重模块——不得经 renderer.tsx re-export，否则守卫红）；③'`index.ts:41-46` 的 `createCompileService`/`CompileFetcherAdapterOptions` 导出源改指 `./assembly/compile-service.js`（R2 m1 钉死：迁出即编译红，源必须改指向轻模块）；③`renderer.tsx` 变纯轻模块：definition + field rules + `registerXlangDebugEditorRenderers` + `createCompileService` 重导出 + 类型，`component` = `createLazyRendererComponent<XlangDebugEditorSchema>(() => import('./xlang-debug-editor.js').then((m) => m.XlangDebugEditorRenderer))`（泛型显式传，照 ThreeCanvasSchema 先例）
- [x] `package.json` 增 `"exports"` 子路径 `"./definitions"` → `src/assembly/renderer.ts`（轻入口；R2 m3：指向 src 非 dist 是**刻意选择**——turbo typecheck 无 build 前置，dist 指向会造出构建时序依赖；无任何运行时消费方经 exports 解析，全走 alias）；`vite.workspace-alias.ts` 增对应别名——**`'@nop-chaos/flux-xlang-debug/definitions'` 键必须排在裸键 `'@nop-chaos/flux-xlang-debug'` 之前**（前缀匹配首键生效，form 块即此排布；追加在裸键之后会使子路径解析只在 Phase 2 才爆）；vitest/vite 同源解析
- [x] chunk 边界守卫测试 `src/lazy-loading-guard.test.ts`（照 3d 同名先例的位置——`src/` 根非 `__tests__/`）：轻路径（`assembly/renderer.ts` + `assembly/compile-service.ts`）源码文本不得静态触达重模块（`./xlang-debug-editor.js`、`@codemirror`/`@lezer`/`@nop-chaos/flux-xlang-lang`/`@nop-chaos/flux-code-editor` 前缀匹配——3d 先例的 findStaticImports 收精确字面量，前缀形态需适配；豁免行内动态 `import(...)` 与 `import type`）
- [x] **测试异步化适配（预期内，非回归）**：`apps/playground/src/pages/xlang-debug-page.test.tsx` 第二用例改 `it.async` + `await screen.findByTestId('xlang-debug-editor')`（懒边界后首帧为 Spinner fallback，DOM 异步出现；用例数与断言语义不变）；第一用例（token gate，不经 renderer）不动
- [x] flux 仓 `docs/logs/` 条目更新（随 Phase 2/3 收口批次写入当日条目）

Exit Criteria:

- [x] 守卫测试绿且判别（变异探针：轻模块注入 `import { Set as CMSet } from '@codemirror/state'` → 守卫红（renderer.ts: static import of @codemirror）→ 恢复 grep 零残留 → 3/3 绿）
- [x] 功能零回归：debug 包 42/42（39+3 守卫）、playground 定向 57/57（2+3+50+2；621 时点 61=57+app-route-resilience 4 两文件合计口径）、页面用例异步化后绿（findByTestId 324ms）
- [x] debug 包 typecheck/lint/build 绿
- [x] No owner-doc update required in this phase（owner doc 回写集中在 Phase 4；renderer-lazy-loading.md 新增小节已在 Phase 4 落档）

### Phase 2 - bundle 门面清单（flux 仓）

Status: completed
Targets: `packages/flux-bundle/`、`scripts/check-flux-bundle-pack.mjs`、`vite.workspace-alias.ts`

- Item Types: `Feature`

- [x] flux-bundle devDependencies += `@nop-chaos/flux-xlang-debug`（仅此一个——R2 M2：code-editor/xlang-lang 经 workspace alias 全量传递解析，无需声明；多声明会触发 knip unused-devDependency 新红；`DIFFED_CATEGORIES` 含 devDependencies）
- [x] `src/index.tsx`：**只从轻子路径 import** `import { registerXlangDebugEditorRenderers } from '@nop-chaos/flux-xlang-debug/definitions'`，`registerDefaultFluxRenderers` 内调用；`export { registerXlangDebugEditorRenderers } from '@nop-chaos/flux-xlang-debug/definitions'`（同源，防重 index 触达）
- [x] `types/public-types.d.ts` 增 `registerXlangDebugEditorRenderers(registry: FluxRendererRegistry): void`（FluxRendererRegistry 已在该文件 import；不引入新 @nop-chaos 包引用）
- [x] 门面测试：registry 含 `'xlang-debug-editor'`、fields 面三元、同 definition 重复注册不抛（index.test.tsx 9/9）
- [x] check-flux-bundle-pack 扩展：`expectedAsyncChunkStems` += `'xlang-debug-editor-'`（chunk 名 = 模块 basename，three-canvas 先例实证）；**主 chunk 守卫断言静态头部无 `./xlang-debug-editor-` 相对引用**（R2 M1 勘正：@codemirror/@lezer 非 hostOwnedExternal、会被内联进 chunk，头部 specifier 扫描对它们恒空转无判别力；而懒边界破坏时 rolldown 会把 xlang chunk 变主 chunk 静态依赖、头部即出现 `./xlang-debug-editor-`——该断言复用 collectHeaderImportSpecifiers 机制、真判别）
- [x] 全链实测绿：门面 build/pack 9/9、check-flux-bundle-pack exit 0、`pnpm check` exit 0；tgz 实证 `dist/xlang-debug-editor-BILHb-F8.js` 独立 chunk（1.3MB，301 处 CM/lezer marker）、index.js 头部 16 条 import 零 xlang/codemirror（唯一引用 = 体内动态 import）
- [x] flux 仓日志更新（docs/logs/2026/10-18.md，随 6cdad5a92 入库）

Exit Criteria:

- [x] 打包校验脚本绿且新增断言判别（审计维度 A 实证：index.js 头部零 `./xlang-debug-editor-`、唯一引用为体内动态 import `./xlang-debug-editor-BILHb-F8.js`——边界破坏形态即头部静态化，判别面成立）
- [x] 门面 typecheck/lint 绿、test 9/9；`pnpm check` exit 0（实测）
- [x] flux 仓日志更新（docs/logs/2026/10-18.md，6cdad5a92 入库）
- [x] No owner-doc update required in this phase（接入说明已在 Phase 4 落档 renderer-lazy-loading.md + README）

### Phase 3 - 产物链端到端刷新（flux → next → 本仓）

Status: completed
Targets: next 仓 `libs/nop-chaos-flux-0.1.0.tgz` + `pnpm-lock.yaml`、本仓 `nop-frontend-support/nop-web-site/src/main/resources/META-INF/resources/`

- Item Types: `Feature`

- [x] tgz 拷贝：`cp dist-packages/nop-chaos-flux-0.1.0.tgz <next>/libs/nop-chaos-flux-0.1.0.tgz`；next `pnpm install`（lockfile integrity hunks 更新）
- [x] next 生产构建：`env -u VITE_ENABLE_MOCK pnpm build`（BUILD_EXIT=0）——dist/index.html 含 import map（grep 1）；**两道 analyze 门随构建通过（cycles none / layer violations none）**
- [x] 同步 web-site 资源（逐条复刻 sync-site.sh 语义，绝对路径）：`TARGET=<本仓绝对路径>/nop-frontend-support/nop-web-site/src/main/resources/META-INF/resources/`；`rm -rf $TARGET && cp -rf <next>/apps/main/dist/ $TARGET/`（cp 源带尾斜杠保内容语义；先 `mkdir -p $TARGET` 防目录不存在）+ `find $TARGET -type f \( -name '*.js' -o -name '*.css' \) -size +3k -exec gzip -n -k -9 {} \; -exec rm {} \;`；守卫前置：grep import map 标记通过后才执行
- [x] 本仓 jar 验证：**closure audit M-2 勘正**——初版 `install` 未 clean，jar 残留 30 个已删除陈旧资产；改 `./mvnw clean install -pl nop-frontend-support/nop-web-site -DskipTests`（exit 0）并补资产对账：jar 内 assets 清单（57）== src 清单（57，diff 空），jar 内 flux.mjs 含新导出
- [x] 契约核对（逐条记录命令+输出）：①`grep -c registerXlangDebugEditorRenderers <next>/apps/main/dist/nop-shared/flux.mjs` ≥1（新导出经 build-nop-shared 自动生成）；②HOST_API_VERSION 源串 diff 为空（事实源 `packages/shared/src/version.ts:16`；next 根 `node_modules/@nop-chaos/` 下无 shared——pnpm 嵌套于 `apps/main/node_modules/@nop-chaos/shared`，grep 路径以版本文件为准——shared 零改动裁定）；③`nop-shared/_registry.js` 机制文件未变（git diff 空）；④资产清单出现 `pkg-flux-async-xlang-debug-editor-*.js.gz`（或构建实际名）且 `pkg-nop-chaos-flux-*` 主包哈希轮换、旧哈希清理；⑤主资源无 codemirror 静态头部依赖（gzip 前 dist 的 index 头部 grep）
- [x] 本仓提交刷新后资源（6a7ebc738b，78 文件轮换）；next 仓提交限 `libs/*.tgz + pnpm-lock.yaml`——**执行勘误**：`git commit -- pathspec` 提交的是工作树而非暂存区，首次提交吞并并行 lock hunks，已 amend 修正（99eb856：tgz + 单行 integrity），工作树并行改动完好保留（~/.zcode 备份+回写）

Exit Criteria:

- [x] web-site 资源含 `pkg-flux-async-xlang-debug-editor-BILHb-F8-DpW4JWF2.js.gz` 且主资源头部无 codemirror 静态依赖（⑤ grep 零命中）
- [x] 契约核对 ①-⑤ 逐条 PASS：①`grep -c registerXlangDebugEditorRenderers dist/nop-shared/flux.mjs`=1；②HOST_API_VERSION `packages/shared/src/version.ts:16` = '0.2.0' 且 shared 目录 git status 空；③`_registry.js` 存在、机制脚本零改动；④新 chunk 落地 + `pkg-nop-chaos-flux-Ct5UmSwf` 主包哈希轮换、旧哈希清理；⑤主 chunk 头部无 codemirror/xlang 静态 import
- [x] jar install exit 0；两道 analyze 门随构建通过
- [x] 本仓日志更新（随 Phase 4 收口批次写入当日条目）
- [x] No owner-doc update required in this phase（Decisions 已随 Phase 4 提前回写 05-frontend-architecture §3.5——doc-links --strict 0）

### Phase 4 - 宿主接入说明 + closure audit + 收口

Status: completed
Targets: flux `docs/architecture/renderer-lazy-loading.md`、`packages/flux-bundle/README.md`、本仓 roadmap + design

- Item Types: `Proof`

- [x] 接入说明（指名落档）：`docs/architecture/renderer-lazy-loading.md` 追加"Bundle-entry subpath"小节（inventory 行 + 子路径机制 + pack 守卫对账——审计维度 F 逐点核验一致）；`packages/flux-bundle/README.md` 增消费面小节（schema/后端前提/token 单注入点——审计核验与 debug-client.ts:402-407 行为一致/demo 宿主裁定）
- [x] Decisions 四条回写本仓 `ai-dev/design/xlang-web-debugger/05-frontend-architecture.md`（§3.5 交付链裁定——审计维度 F/H 核验四条俱全；附带勘正 81 行 .tsx 残留与 101 行 "bundle 现不含 editor" 过时表述）
- [x] 独立子代理 closure audit（fresh session）：懒边界实通（tgz chunk + 主 chunk 守卫 tar/grep 实证）、链路逐环证据、Decisions 落档、deferred 诚实性、文本一致性——agent_5b3996c5 九维度 7 PASS + B/G 两维发现（0B/4M/3m 全修复）
- [x] 修复审计发现（全数落位，见 flux cd29f8ee1 + next 57cac2c + 本仓收口提交）；roadmap item 20 → done；三仓各自领地分离提交
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit 0（收口提交前实测）；hollow 扫描覆盖边界如实披露：本仓 affected 面 = web-site 纯资源刷新（无代码面，hollow 扫描不适用），flux 侧代码由 flux 自有门禁承载（knip/test/guard 实测绿）
- [x] 本仓日志更新（ai-dev/logs/2026/10-18.md item 20 条目）

Exit Criteria:

- [x] audit verdict issues（0B/4M/3m）→ 四 Major 全修复落位（修复均为证据/卫生类，Anti-Hollow 实质 PASS；审计明示修复即闭合口径）
- [x] roadmap item 20 `done`
- [x] check-doc-links / check-plan-checklist 均 exit 0
- [x] 本仓日志更新（ai-dev/logs/2026/10-18.md item 20 条目）

## Closure Gates

- [x] 所有 in-scope 行为实现且有测试（守卫 43/43 含判别探针；门面 9/9；页面用例异步化；打包校验扩展）
- [x] Anti-Hollow（审计维度 A 实证）：tgz 内 `dist/xlang-debug-editor-BILHb-F8.js` 独立 chunk 1.3MB（301 处 CM/lezer marker）；index.js 头部 16 import 零 xlang、唯一引用为体内动态 import；web-site `pkg-flux-async-xlang-debug-editor-*.js.gz` 与 next dist 字节一致（cmp clean）
- [x] owner docs：05-frontend-architecture §3.5 交付链裁定（含勘正）；renderer-lazy-loading.md 小节+inventory 行；flux-bundle README 消费面；三仓日志
- [x] 独立子 agent closure-audit 完成并记录证据（agent_5b3996c5，0B/4M/3m 全修复）
- [x] flux：debug 43/43、3d 200/200、门面 9/9、typecheck/lint 绿、`pnpm check` exit 0（实测）
- [x] 本仓：`./mvnw clean install -pl nop-frontend-support/nop-web-site -DskipTests` exit 0（jar==src 资产 57 对账）；check-doc-links --strict 0；check-plan-checklist --strict 0
- [x] next 仓：生产构建 import map 守卫过 + 两道 analyze 门通过（cycles none/layer none）；提交限 tgz + 单行 lock integrity（57cac2c）

## Deferred But Adjudicated

### 生产 admin 页 xlang 调试入口

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 裁定 demo 宿主 = playground（item 19）；生产页面属宿主侧功能开发，当前无具体需求方；registry/交付能力本 plan 就绪后宿主可自助接入
- Successor Required: `no`（宿主侧需求出现时另立工作项）

### flux-xlang-debug 包 index.ts 重导出收敛

- Classification: `optimization candidate`
- Why Not Blocking Closure: index.ts 重导出为 616-621 交付时的既有状态（先于本 plan 存在）；本 plan 的 bundle 入口已改走 `/definitions` 轻子路径，重 index 不在 bundle 消费链上，对交付目标零影响；全面收敛（照 3d 裁 index）涉及其它直接消费方的 API 迁移，归 renderer-lazy-loading.md 约定治理线
- Successor Required: `no`（约定治理统一处理）

## Non-Blocking Follow-ups

- bundle 门面 size 基线记录（主 chunk 前后对比）入 next 构建日志（观测性，非门禁）
- check-flux-bundle-pack 的重库头部守卫可后续泛化为"配置化重库清单"（当前两行扩展足够）

## Closure

Status Note: item 20（跨仓交付与版本配套）全链收口——三包经 `/definitions` 轻子路径进 bundle（懒边界 three-canvas 同型，CE/lezer 落 `xlang-debug-editor-*` 异步 chunk），产物链端到端刷新并逐环实证（tgz → next 构建 → web-site 轮换 → jar 资产对账），四条设计裁定落档。closure audit 0B/4M/3m 全数修复后闭合（修复均为证据/卫生类，Anti-Hollow 实质维度审计 PASS）。
Completed: 2026-10-18

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_5b3996c5（fresh session）
- Audit Session: agent_5b3996c5-9558-450c-9927-285ee8c0fcc0
- Evidence:
  - A 懒边界（PASS）：tar 实证 `dist/xlang-debug-editor-BILHb-F8.js` 独立 chunk；index.js 头部 16 条 import 零 xlang、唯一引用为体内动态 import；web-site .gz 与 next dist cmp 字节一致
  - B 守卫（PASS with M-1→已修）：正则活体探针（单行/裸/export/子路径捕获、动态与 import type 豁免、多行漏检）→ 修复为有界跨行 + 多行探针用例（43/43；3d 镜像 200/200）
  - C 门面（PASS）：仅 `/definitions` import、默认栈注册、public-types 声明、devDeps 仅一包（knip 安全）
  - D 链路（PASS）：99eb856→57cac2c 恰两文件（tgz + 单行 lock integrity）；worktree 并行改动零丢失
  - E 契约（PASS with M-2→已修）：五契约复核过；jar 未 clean 残留 30 陈旧资产 → clean install + jar==src 57 对账 + jar 内 flux.mjs 新导出
  - F/H 文档与裁定（PASS）：renderer-lazy-loading.md/README/design §3.5 与 live code 逐点一致（m1 两处过时表述已勘正）
  - G 文本一致性（M-3→已修）：Phase 2/4 滞后补勾 + 证据
  - 门禁亲测：debug 42/42（修复后 43/43）、facade 9/9、check-flux-bundle-pack exit 0、doc-links 0
  - Deferred 分类检查：admin 页/包 index 收敛两条均 out-of-scope/optimization 且理由成立——无 in-scope live defect 被降级

Follow-up:

- 包 index.ts 重导出收敛（Deferred：约定治理线）
- 生产 admin 页 xlang 调试入口（Deferred：宿主侧需求出现时另立）
- bundle 主 chunk size 基线观测（Non-Blocking）

## Draft Review Record

- Reviewer / Agent: 独立子代理 agent_10a09790（fresh session，R2 聚焦 Phase 1-2）
- Verdict: revised（0B/2M/6m；明示"两个 Major 修掉即无需第三轮"——已修，plan 升 active）
- Findings addressed: M1 主 chunk @codemirror/@lezer 头部守卫无判别力（内联进 chunk 无 specifier）→ 改断言静态头部无 `./xlang-debug-editor-` 相对引用；M2 三包 devDeps 有 knip unused 红风险 → 仅声明 flux-xlang-debug；m1 index.ts createCompileService 导出源钉死改指轻模块；m2 alias 子路径键序（先于裸键）；m3 exports→src 为刻意选择（免 dist 时序依赖）；m4 compile-service 对 lang 仅 import type + 守卫清单值形式禁 lang + 前缀匹配适配；m5 契约核对②路径勘正（packages/shared/src/version.ts）；m6 守卫测试位置 src/ 根 + 豁免规则写明
- Reviewer / Agent: 独立子代理 agent_0b5e1b69（fresh session，R1）
- Verdict: revised（0→1 Blocker / 3 Major / 7 Minor，全数修订）
- Findings addressed: B1 bundle 入口拓扑裁定为方案 B（form `/definitions` 轻子路径先例；重 index 现状不入 bundle 链，收敛登记 Deferred）；M1 "零回归"改"功能零回归 + 页面用例异步化适配（预期内改造）"；M2 next 领地补 pnpm-lock.yaml（hunk 级对账）+ 并行在途协调注记（不触 flux-lib/tailwind-preset）；M3 接入说明指名 renderer-lazy-loading.md + flux-bundle README；m1 守卫清单补 `@lezer`；m2 flux 日志路径勘正 docs/logs/；m3 lazy 泛型显式传 `<XlangDebugEditorSchema>`；m4 迁移地图三步钉死（含 index 导出源改向）；m5 Phase 3 契约核对五条具体命令 + `pkg-flux-async-*` 宿主侧命名 + 绝对路径 + cp 语义；m6 Phase 1-3 补 "No owner-doc update required in this phase" + Closure Gates 补 check-plan-checklist/scan-hollow 覆盖边界披露；m7 Phase 3 显式验证两道 analyze 门
