# 2504 docs-for-ai 去除 amis 文档，收敛为 flux 单框架口径

> Plan Status: active
> Last Reviewed: 2026-10-23
> Source: 用户需求（2026-10-23）：nop-entropy 整体去除 amis 相关文档，docs-for-ai 只保留 flux 框架文档；view.xml 只保留 flux 支持部分；单独一个文档说明 amis 相关文件已 deprecated、使用需引入 legacy jar
> Related: `ai-dev/plans/288-docs-restructure-frontend-flux-amis.md`（上一轮 flux/amis 双轨文档重组）

## Purpose

把 `docs-for-ai/` 从「amis + flux 双轨」口径收敛为「flux 唯一支持口径」：amis 内容从规范性文档中移除，amis 相关文件/模块在专门的 deprecation 文档中登记（含 legacy jar 引入方式），view.xml 相关文档只描述 flux 支持面。代码行为不变。

## Current Baseline

代码事实（2026-10-23 实测）：

- 运行时渲染默认已是 flux：`nop-frontend-support/nop-web/src/main/java/io/nop/web/WebConfigs.java:54-56`，`nop.web.render-mode` 默认 `"flux"`；切换机制在 `xlib/web/impl_flux_mode.xpl` + `view-gen.xlib:17-18`（flux 模式下 `control.xlib` 重写为 `flux-control.xlib`）。
- flux 支持面全部在 `nop-frontend-support/nop-web/`：`_vfs/nop/web/xlib/flux-web.xlib`（1081 行）+ `xlib/flux-web/`（10 个 xpl）、`flux-control.xlib`（1203 行，约 78 个控件标签）。新前端站点 `nop-web-site` 已是 flux 版。
- amis 遗留面：
  - 静态资源模块 `nop-frontend-support/nop-web-site-legacy`（旧 amis 站点，含 `js/amis-*.js.gz`，63 文件）与 `nop-frontend-support/nop-web-amis-editor`（amis 编辑器资源，47 文件）；BOM 条目在 `nop-bom/pom.xml:1042,1048`；被约 25 个 app pom 依赖。
  - Java 逻辑内嵌于 `io.nop.web.page`：`nop-frontend-support/nop-web/src/main/java/io/nop/web/page/WebPageHelper.java:113,144,156-164,206-222`（amis scope className、dialog/drawer 处理）。
  - xlib 管线：`web.xlib`（938 行）+ `xlib/web/`、`control.xlib`（1239 行）。
  - xdef 层：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/action.xdef` 中 `<onEvent>`（:72，注释「AMIS框架专用，Flux不使用」）为 amis 专用元素；`<onClick>`（:75）为 flux 专用；grid/form/forms/simple-component/store 等 xdef 无 amis 引用（框架中立）。
  - 页面文件存量：约 438 个 `*.view.xml`（约 220 个引用 `control.xlib`）、约 491 个 `*.page.yaml`（约 470 个引用 `web.xlib`，靠 render-mode 自动切换到 flux）、47 个 `*.flux.yaml`。

文档事实：

- 原 `02-core-guides` 下的 AMIS 专属渲染文档（已删除） 头部已自声明「本文档内容专属 AMIS 框架，随着 Flux 逐步替代将被删除」（:2-3）。
- docs-for-ai 中含 "amis" 引用的文件以执行时 `grep -ril "amis" docs-for-ai/` 结果为准（2026-10-23 实测 41 个，含原 AMIS 专属渲染文档）。其中：
  - 全篇 amis 专属：`02-core-guides 下的 amis-rendering（已删除，不带路径引用以免死链）`（仅 1 篇）。
  - 夹带 amis 表述的规范性文档：INDEX.md、`00-required-reading-frontend.md`、`00-required-reading-e2e-testing.md`、`00-start-here/application-project-pitfalls-frontend.md`、`02-core-guides/` 下 17 篇（frontend-rendering-pipeline、flux-rendering、view-and-page-customization、page-dsl-pattern-catalog、layout-syntax-reference、xdef-and-xdsl、xpl-escaping-reference、api-and-graphql、api-model-and-codegen、auth-and-permissions、cross-module-entity-reference、debugging-and-diagnostics、e2e-testing、e2e-testing-troubleshooting、orm-model-design、rpc-and-distributed-rpc 等）、`03-modules/` 下 14 篇、`03-runbooks/` 3 篇、`04-reference/glossary.md`、`04-reference/source-anchors.md`、`05-examples/application.yaml`。
  - 已知 drift（「amis 为默认」表述，与代码默认 `render-mode=flux` 不一致，live defect，必须在本计划内收敛为 Fix）：`02-core-guides/frontend-rendering-pipeline.md:4`、`02-core-guides/flux-rendering.md:85`（「render-mode=amis，默认」）、`05-examples/application.yaml:100`（「或 amis（默认）」）、`00-required-reading-frontend.md:65`（「AMIS 渲染管线（默认）」）。另有 `02-core-guides/e2e-testing.md:3,8,45,56` 仍把 AMIS 前端测试当现状描述（e2e 代码与 README 已无 amis）。
- `ai-dev/analysis/` 下已有 5 篇 amis 专题分析（2026-06-19、2026-06-28×2、2026-07/15、2026-07/22）——时效性文档，按 SSOT 规则不改写，只在需要时由新 deprecation 文档链接。
- `docs/`、`docs-en/`、`ai-dev/` 历史文档不在本计划范围（见 Non-Goals）。

## Goals

- `docs-for-ai/` 中不再存在把 amis 当作受支持前端方案的规范性内容；前端渲染唯一口径为 flux。
- 删除 `02-core-guides 下的 amis-rendering（已删除，不带路径引用以免死链）`；其仍有效的运行时事实（render-mode 切换、legacy 引入方式）由新的 deprecation 文档承接。
- 新建一个专门的 amis deprecation 文档：登记 amis 相关文件/模块/配置清单，声明 deprecated 状态，说明继续使用需引入的 legacy jar（`nop-web-site-legacy`、`nop-web-amis-editor`）与 `nop.web.render-mode=amis` 开关。
- view.xml 相关文档（view-and-page-customization、page-dsl-pattern-catalog、layout-syntax-reference 等）只描述 flux 支持的部分；amis 专用模型元素（如 `<onEvent>`）标注 deprecated，不描述用法。
- 修复 `frontend-rendering-pipeline.md` 的「AMIS 默认」drift。
- `node ai-dev/tools/check-doc-links.mjs --strict` 通过；INDEX.md 与 source-anchors.md 路由同步。

## Non-Goals

- 不修改任何 Java 代码、xlib 管线、xdef 校验语义；不物理删除 `<onEvent>` 等 amis 专用 xdef 元素或 `web.xlib`/`control.xlib`（legacy jar 用户与存量 438 个 view.xml 仍依赖运行时兼容；物理删除属后续独立裁定，见 Deferred）。
- 不处理 `docs/`、`docs-en/`、`deepwiki/`、`ai-tools/`、`nop-ai/model/input-requirements.md`、`nop-entropy-e2e/docs/` 下的 amis 内容（`docs/` 已是遗留目录，另见 SSOT roadmap）。
- 不改写 `ai-dev/` 下时效性文档（analysis/logs/plans/audits）中的历史 amis 记录。
- 不删除或迁移存量 `*.view.xml` / `*.page.yaml` 页面文件。
- 不变更 `nop-web-site-legacy`、`nop-web-amis-editor` 模块的构建与依赖关系。

## Scope

### In Scope

- `docs-for-ai/` 全目录（44 个含 amis 引用文件 + 新增 deprecation 文档）。
- `nop-kernel/nop-xdefs/.../xui/action.xdef` 的注释级 deprecated 标注（仅注释文案，不改 schema 结构，见 Phase 3 裁定）。
- `docs-for-ai/INDEX.md`、`04-reference/source-anchors.md` 路由同步。

### Out Of Scope

- 见 Non-Goals。

## Execution Plan

### Phase 1 - 新建 amis deprecation 文档

Status: completed
Targets: `docs-for-ai/04-reference/amis-legacy-deprecation.md`（新文件）

- Item Types: `Feature`

- [x] 新建 `docs-for-ai/04-reference/amis-legacy-deprecation.md`，内容包含：
  - Status 声明：amis 前端支持已整体进入 deprecated，当前默认且唯一受支持的渲染框架为 flux（`nop.web.render-mode` 默认 `flux`，锚点 `WebConfigs.java`）。
  - amis 相关文件清单（分三类，带路径）：
    a) 文档类：已删除的 `02-core-guides 下的 amis-rendering（已删除，不带路径引用以免死链）`（指明已删除、去向为本文档；**不得以 markdown 链接指向该文件**，只能以纯文字路径提及，避免 Phase 2 删除后断链）、`docs/theory/amis/`、`docs/dev-guide/xui/amis.md`（指向遗留目录，不再维护）。
    b) 代码/资源类：`nop-web-site-legacy`（旧 amis 站点静态资源）、`nop-web-amis-editor`（amis 编辑器资源）、`nop-web` 内 `web.xlib`/`control.xlib`/`xlib/web/` 管线、`WebPageHelper` 中 amis 处理逻辑、`action.xdef` 的 `<onEvent>` 元素。
    c) 模型文件类：存量 `*.view.xml` 中 `control.xlib` 引用、`*.page.yaml` 中 `web.xlib` 引用（flux 模式下由 `impl_flux_mode.xpl`/`view-gen.xlib` 自动切换，无需手工改造）。
  - legacy 使用方式：继续使用 amis 需引入 `nop-web-site-legacy` 与 `nop-web-amis-editor` 依赖（BOM 坐标 `io.github.entropy-cloud:nop-bom` 中已登记），并将 `nop.web.render-mode` 设为 `amis`；明确无官方迁移支持、仅存量兼容。
  - 指向 flux 文档的路由：`02-core-guides/flux-rendering.md`、`02-core-guides/frontend-rendering-pipeline.md`。
- [x] 文档遵守规范性文档要求（不写日期演化叙事，deprecated 状态即当前结论；引用 `ai-dev/analysis/` 历史分析时以链接 + ≤3 行摘要形式）。

Exit Criteria:

- [x] `docs-for-ai/04-reference/amis-legacy-deprecation.md` 存在，覆盖上述四块内容且每条清单带真实路径（路径经 grep 验证存在于仓库）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 对该文件通过。
- [x] No owner-doc update required（本文件即为新增 owner doc）。
- [x] `ai-dev/logs/2026/10-23.md` 已更新。

### Phase 2 - 删除原 AMIS 专属渲染文档并全局清理 docs-for-ai 中的 amis 引用

Status: completed
Targets: `docs-for-ai/`（44 个文件清单见 Current Baseline）

- Item Types: `Fix`（含 frontend-rendering-pipeline.md 的「AMIS 默认」drift）、`Follow-up`（纯表述清理）

- [x] 原 `02-core-guides` 下的 AMIS 专属渲染文档已删除；其仍有效的运行时事实（render-mode 配置、切换机制说明）核对是否已被 `flux-rendering.md` 与 Phase 1 文档覆盖，缺失内容先迁移再删除。
- [x] 清理该已删除文档的全部入链（2026-10-23 实测 ≥12 处，以执行时 grep `amis-rendering` 为准）：`00-required-reading-frontend.md:65`、`04-reference/source-anchors.md:116`（**DOC-002 文档锚点登记行**，须改指 Phase 1 文档或删除登记，不得残留死锚）、`03-runbooks/bootstrap-new-domain-module.md:105`、`frontend-rendering-pipeline.md:4,322`、`flux-rendering.md:624`、`xpl-escaping-reference.md:277`、`page-dsl-pattern-catalog.md:234`、`layout-syntax-reference.md:491`、`view-and-page-customization.md:4,326` 等。
- [x] `02-core-guides/frontend-rendering-pipeline.md`：移除「AMIS（默认）」表述（:4、:121、:322 附近），改写为 flux 唯一默认口径；amis 历史仅保留一句指向 Phase 1 文档的链接。
- [x] `02-core-guides/flux-rendering.md`：保留为 flux 唯一 owner doc；其 NormalizeAction/映射章节中「AMIS 形态 → Flux Schema」的转换机制描述**保留**（存量页面靠该链路跑在 flux 下，属仍有效的 flux 知识），仅移除把 amis 当独立可选框架的表述，改为 legacy 文档链接。
- [x] 逐文件清理其余文件中的 amis 引用（清单以执行时 grep 结果为准；删除该文档与入链后约 40 个）：
  - 教学示例、默认值描述、控件/布局语法中混排的 amis 写法（如 `$xxx` 简写、`visibleOn`、amis 组件名）：改为 flux 对应写法或删除；flux 侧对应映射关系（level→variant、visibleOn→visible 等，见 `flux-web.xlib`）如尚未记录，迁入 `flux-rendering.md`。
  - 「AMIS 或 Flux 双轨」选择类表述：收敛为 flux 单轨。
  - 无法简单替换的 amis 深度内容（如 `xdef-and-xdsl.md` 中「无 xdef 的 JSON/YAML（amis 页面）差量合并」一节）：保留技术机制描述，但示例与措辞去 amis 化，或链接 Phase 1 文档。
  - `03-modules/*.md`、`03-runbooks/*.md` 中的页面示例与步骤：改为 flux 口径。
  - `04-reference/glossary.md`：无独立 amis 词条；改写 `:41` 附近 DeltaJsonLoader 条目中「适用于…amis 页面」的内联提及（改为 legacy 文档链接或去 amis 化措辞）。
  - `04-reference/source-anchors.md`：amis 锚点保留（代码仍存在），标注 deprecated 归属 Phase 1 文档。
  - `05-examples/application.yaml`：示例配置如含 render-mode/amis 项，改为 flux 默认口径。
- [x] `docs-for-ai/INDEX.md`：移除 amis-rendering（已删除） 路由行（:48、:118），新增 Phase 1 文档的路由行（任务：「了解 amis 遗留状态 / 继续 legacy amis」→ `04-reference/amis-legacy-deprecation.md`）。

Exit Criteria:

- [x] `grep -ril "amis" docs-for-ai/` 结果中，每个残留文件均属于以下三类之一：(a) `04-reference/amis-legacy-deprecation.md`；(b) 明确标注 deprecated 的锚点/词条（source-anchors.md、glossary.md）；(c) legacy jar/模块名或 flux 转换机制描述中不可回避的「AMIS 形态→Flux」映射措辞。任何把 amis 当作受支持方案或默认方案的表述不得残留。
- [x] `grep -rin "amis" docs-for-ai/ | grep -i "默认\|(default)"` 无「amis 为默认/受支持」表述命中（4 处已知 drift 全部收敛；机制描述中「AMIS 形态」字样不算命中）。
- [x] `frontend-rendering-pipeline.md` 中不存在「AMIS 默认」表述（live drift 已收敛）。
- [x] INDEX.md 无指向已删除文件的链接；check-doc-links --strict 全目录退出码 0。
- [x] 抽查 3 个 runbook（build-related-drawer-page / build-tabs-workspace-page / replace-field-with-complex-control）示例与 `flux-control.xlib` 实际控件标签一致。
- [x] Owner docs 更新：`02-core-guides/flux-rendering.md`、`frontend-rendering-pipeline.md` 已原地更新。
- [x] `ai-dev/logs/2026/10-23.md` 已更新。

### Phase 3 - view.xml 口径收敛为 flux 支持面

Status: completed
Targets: `docs-for-ai/02-core-guides/view-and-page-customization.md`、`page-dsl-pattern-catalog.md`、`layout-syntax-reference.md`、`xdef-and-xdsl.md`、`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/action.xdef`

- Item Types: `Fix`、`Decision`

- [x] Decision（本计划内裁定）：`action.xdef` 的 `<onEvent>` 保留 schema 结构（存量 legacy 运行时依赖），仅在节点注释中追加 deprecated 标注（「Deprecated: amis 专用，flux 使用 onClick；见 docs-for-ai/04-reference/amis-legacy-deprecation.md」）；`link` actionType 描述（:3）措辞去 amis 化。属注释级变更，不改校验行为，不触碰 Protected Area 的 schema 语义。
- [x] `view-and-page-customization.md` / `page-dsl-pattern-catalog.md` / `layout-syntax-reference.md`：删除 amis 专用元素/属性的用法描述（`onEvent`、amis 组件 type、`$xxx` 简写等）；view.xml 文档口径只覆盖 flux 支持面（`onClick`、`controlLib` 切换、`embed` 的 `.flux.yaml` 优先、group/embed 容器的既有说明）。
- [x] xview/grid/form 等 xdef 的事实性描述与文档口径对齐（模型框架中立 + flux 渲染默认）。
- [x] 若 Phase 2 的 41 文件清理已覆盖其中 view.xml 相关文件，本 Phase 只做交叉检查不重复改动。

Exit Criteria:

- [x] `action.xdef` 中 `<onEvent>` 注释含 deprecated 标注与 flux 对应元素名；schema 结构与校验行为 diff 为零（仅注释变更，`mvn -pl nop-kernel/nop-xdefs` 相关测试无需新增）。
- [x] view.xml 三篇指南中无 amis 专用用法示例；flux 支持面描述与 `flux-control.xlib`/`flux-web.xlib` 实际标签一致（抽查 ≥5 个标签）。
- [x] No new test required: 注释级 xdef 变更 + 纯文档变更，无行为变更。
- [x] `ai-dev/logs/2026/10-23.md` 已更新。

### Phase 4 - 全局一致性收尾

Status: completed
Targets: `docs-for-ai/`、`ai-dev/logs/`

- Item Types: `Proof`

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] `grep -ri "amis" docs-for-ai/` 复核：所有残留命中均为 (a) Phase 1 deprecation 文档内容、(b) 明确标注 deprecated 的锚点/词条、(c) legacy jar 名称；无残留的 amis 用法教学。
- [x] AGENTS.md 路由表复核：前端相关路由行不指向已删除文件（AGENTS.md 本身无 amis 硬引用，确认即可）。
- [x] `ai-dev/logs/2026/10-23.md` 收口条目已写。

Exit Criteria:

- [x] 上述三条验证命令全部通过并记录输出摘要。
- [x] `ai-dev/logs/2026/10-23.md` 已更新。

## Closure Gates

> 纯文档计划（含 1 处 xdef 注释变更，无 Java/行为变更）：构建验证按 guide 豁免，但保留 xdef 所在模块的最小编译验证。

- [x] `grep -ril "amis" docs-for-ai/` 残留面收敛为 Phase 4 Exit Criteria 允许的三类。
- [x] `frontend-rendering-pipeline.md` AMIS-default drift 已修复（live defect 收敛）。
- [x] INDEX.md / source-anchors.md / AGENTS.md 无断链、无指向已删除文档的路由。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] `./mvnw compile -pl nop-kernel/nop-xdefs -am` 通过（xdef 注释变更回归面）。
- [x] No new test required: 纯文档 + 注释变更（各 Phase 已注明）。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [ ] 独立子 agent closure-audit 已完成并记录证据（含「文档内容与 live repo 一致性」抽查）。**Pending：配额窗口未开启子 agent，待补做**
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。**Pending：随 closure audit 一并执行**

## Deferred But Adjudicated

### 物理删除 amis 专用代码面（`<onEvent>` xdef 元素、`web.xlib`/`control.xlib` 管线、`WebPageHelper` amis 分支）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 存量 438 个 `*.view.xml` / 491 个 `*.page.yaml` 与约 25 个 app pom 仍依赖 legacy 运行时兼容；物理删除属破坏性代码变更，需独立的迁移 plan（Protected Area：生成管线/框架核心 plan-first）。
- Successor Required: `yes`
- Successor Path: 待立项（建议命名方向：amis runtime removal plan；先完成存量页面文件与 app pom 迁移盘点）

### `docs/`、`docs-en/`、`deepwiki/`、`ai-tools/` 下的 amis 内容清理

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `docs/` 已被 SSOT roadmap 裁定为历史遗留目录、不接收新内容且非开发 AI 默认入口；`docs-en/` 为英文镜像、维护独立推进。
- Successor Required: `no`（由 `ai-dev/backlog/doc-system-ssot-roadmap.md` 既有路线覆盖）

## Non-Blocking Follow-ups

- `ai-dev/analysis/` 下 5 篇 amis 专题分析保持原样（时效性文档）；如后续需要可在 deprecation 文档中补充链接。

## Closure

Status Note: 执行面已全部落地（4 个 Phase completed，7/9 Closure Gates 已验证），check-doc-links --strict=0、xdefs compile 通过、grep 复核通过。Plan 未标 completed：独立 closure audit 与 check-plan-checklist 因 AI 子 agent 5 小时配额窗口耗尽无法在本窗口启动（两个清理子 agent 同因失败，已由主 session 接管完成全部清理）。待配额重置后补做 audit，再关闭。
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:
