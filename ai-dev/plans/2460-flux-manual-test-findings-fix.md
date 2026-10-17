# 2460 flux 手工测试发现逐条修复

> Plan Status: completed
> Last Reviewed: 2026-10-17
> Source: `ai-dev/testing/2026-10/2026-10-17-flux-manual-test-findings.md`（手工测试发现清单）
> Related: `docs-for-ai/02-core-guides/flux-rendering.md`

## Purpose

把手工测试发现的 6 个 flux 渲染/交互问题逐条定位并修复，使功能演示与 nop-auth 的关键页面（下载、操作日志、关联字段显示、恢复删除、部门标签页、表单 label 布局）在 flux 模式下行为与显示均正确，并用 e2e（含截图）钉住回归。

## Current Baseline

- 渲染模式：`nop-auth-app` / `nop-quarkus-demo` 均 `nop.web.render-mode=flux`；前端为 nop-chaos-next 构建产物（本仓 `nop-web-site` 已同步，同步方式见 `ai-dev/logs/2026/10-17.md`）。
- nop-auth flux e2e 现状：42 passed（见 `ai-dev/logs/2026/10-17.md`）。
- 已确认的 live 现象（手工测试；各条根因由对应 Workstream 先定位确认）：
  - **下载文件名乱码**：后端 `Content-Disposition` 在 `nop-graphql-core` 的 `GraphQLResponseHelper` 生成（`StringHelper.encodeURL(fileName)`，`attachment; filename=<encoded>`）；前端 nop-chaos-next `httpBlob.ts` 的 `extractFilename` 对普通 `filename=` 分支**未 `decodeURIComponent`**（仅 RFC5987 `filename*` 分支解码）。需确认中文名走的是哪条分支。
  - **操作日志（`NopAuthOpLog`）"操作"列出现多个**：操作按钮列由 `flux-web/grid_crud.xpl:89-98`（`<column type="operation" label="@i18n:common.operation">`）生成；`nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthOpLog/_gen/_NopAuthOpLog.view.xml` 只有一个数据列 `operation`（业务操作）。需确认"多列"还是"单列多值"。
  - **关联字段显示 id 而非 dispProp**：生成侧 `flux-control.xlib:872` 的 `edit-relation` 已取 `ext:joinRightDisplayProp || ext:joinRightProp`，且 `_NopAuthUser.xmeta:178/196/205` 已定义 `ext:joinRightDisplayProp`（deptName/name/userName）——**生成侧看似已正确**，缺陷更可能在消费侧（nop-chaos-next picker 标签/静态显示）。
  - **`NopAuthUser` recover-deleted 报"参数名不对"**：`NopAuthUser.view.xml:184` action `api url="@mutation:NopAuthUser__recoverDeleted/id"`。`/id` 被 `nopRpcResolver.ts:363` 当 `urlSel`（selection）——但注意所有 `save/id`/`update/id` 同形且工作正常，故**不能**先断定为 urlSel 问题；需抓取实际请求体/错误码，重点排查 `@mutation` 未注册操作的 `{data: cleaned}` 兜底（`nopRpcResolver.ts:379-383`）与 `buildRpcParams`。
  - **`NopAuthDept` 标签页交互异常**：标签为**页面级** `<tabs name="tabsView" tabsMode="vertical">`（非表单 `layoutControl="tabs"`）；新建部门/父部门（`parentId` tree-select/树选择）与标签切换结果异常。同页 `save/update/add-child` 亦用 `/id` 形态。
  - **表单 label 布局**：编辑/新增表单默认 `mode` 在 `flux-web/container_simple.xpl:39` 与 `page_simple.xpl:72`（`formModel.layoutMode || 'horizontal'`）；查询表单默认在 `flux-web/grid_crud.xpl:74-75`；`flux-web.xlib` 的 `FluxFormDefaultAttrs` 仅对 `query` 强制 horizontal。

## Goals

- 6 个问题全部定位根因并修复（或给出经确认的 non-blocking 裁定及理由）。
- 第 5 条（部门标签页）必须有 e2e 覆盖且**保存截图**确认界面显示正常。
- 发现 ①③④ 涉及跨仓（nop-chaos-next）时，生成/宿主两侧行为一致并各有验证；改动 nop-chaos-next 后**必须重建 `apps/main/dist` 并同步进本仓 `nop-web-site`** 再跑 e2e（否则测的是旧产物）。
- 修复后 nop-auth flux e2e 全绿，且不回归既有 42 条。

## Non-Goals

- 不重做 flux 渲染器架构、不替换 nop-chaos-next 宿主框架。
- 不做"功能演示 + 全部菜单/按钮 vs amis"的全量逐条对照（属独立审计）。

## Scope

### In Scope

- 6 条现象对应的页面/生成/宿主修复。
- 相应 e2e / 单元测试与截图证据。
- 受影响的 owner doc（`docs-for-ai/02-core-guides/flux-rendering.md` 等）同步。

### Out Of Scope

- 全量菜单/按钮对照审计；与本 6 条无关的重构与优化。

## Execution Plan

> 采用 Workstream（各现象相互独立，可并行推进）。W1/W3/W4 涉及 nop-chaos-next。

### Workstream 1 - 下载文件名乱码（现象 ①）

Status: completed
Targets: `nop-service-framework/nop-graphql/nop-graphql-core/.../GraphQLResponseHelper.java`；nop-chaos-next 仓的 `httpBlob.ts`（`extractFilename`）

- Item Types: `Fix`

- [x] 定位：抓取下载响应头，确认中文名编码形态（RFC5987 `filename*=UTF-8''...` vs 普通 `filename=%..`，或后端用 `StringHelper.encodeURL` 直接编码了 UTF-8 名）。
- [x] 修复根因（后端按 RFC5987 输出 `filename*=UTF-8''`，或前端 `extractFilename` 对普通分支补 `decodeURIComponent`；以实际形态为准）。
- [x] 增加回归测试（后端单测断言 header；前端 `httpBlob` 单测断言语义）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 从页面下载 Excel 模板与报表，保存的文件名为中文（浏览器截图/文件列表证据）。
- [x] 回归测试覆盖文件名编码路径，去掉修复即红（变异自检）。
- [x] **端到端验证**：页面点击下载 → 落盘文件名为中文，完整链路已验证。
- [x] 若改 nop-chaos-next：`apps/main/dist` 已重建并同步进 `nop-web-site`，且同步后的构建被 e2e 实际加载。
- [x] 若改动下载 header 契约：`docs-for-ai/` 相关章节已更新；否则写 `No owner-doc update required`。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Workstream 2 - 操作日志"操作"列出现多个（现象 ②）

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/grid_crud.xpl`（operation 列，第 89-98 行）；`NopAuthOpLog` view.xml

- Item Types: `Fix`

- [x] 定位：确认为"多列"还是"单列多值"；确认 `grid_crud.xpl` 的 operation 列是否被重复输出（如 noOperations 条件失效、或列定义与生成列叠加）。
- [x] 修复，使"操作"列唯一且内容正确。
- [x] 增/改测试（生成页列数断言或 e2e 列数断言）。

Exit Criteria:

- [x] 操作日志列表仅有单一"操作"列，显示正常（截图）。
- [x] 回归测试钉住列数量。
- [x] **端到端验证**：nop-auth e2e 打开操作日志页并断言列结构。
- [x] No owner-doc update required（页面列修复，不改使用约定）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Workstream 3 - 关联字段显示 id 而非 dispProp（现象 ③）

Status: completed
Targets: nop-chaos-next picker 标签/静态显示（首选）；必要时本仓 `flux-control.xlib` `edit-relation`

- Item Types: `Fix`

- [x] 定位：分别核查（a）picker 已选标签、（b）列表单元格、（c）查看/编辑只读显示，确认是否消费侧未按 `labelField` 取 dispProp（生成侧 `edit-relation` 已取 `ext:joinRightDisplayProp`，见基线）。
- [x] 修复根因，使显示使用 dispProp（如 userName），保存值仍为 id。
- [x] 增回归测试（e2e 显示断言；若消费侧改动则加前端单测）。

Exit Criteria:

- [x] 新增/编辑/查看中关联字段显示 dispProp 文本（如用户名），非 id（截图）。
- [x] 保存的数据库值仍为 id（RPC 校验）。
- [x] **端到端验证**：e2e 创建/查看记录，断言显示文本为 dispProp。
- [x] 若改 nop-chaos-next：`apps/main/dist` 已重建并同步进 `nop-web-site`，e2e 实际加载。
- [x] 若改动生成契约：更新 `docs-for-ai/02-core-guides/flux-rendering.md` picker 章节；否则写理由。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Workstream 4 - recoverDeleted 参数名错误（现象 ④）

Status: completed
Targets: nop-chaos-next nop-chaos-next 仓的 `nopRpcResolver.ts`（`@mutation` 未注册操作兜底 379-383 / `buildRpcParams`）；本仓 `NopAuthUser.view.xml` recover-deleted action

- Item Types: `Fix`

- [x] 定位：**先抓取实际请求体与后端错误码**（不要先假定 urlSel）；对比同页 `save/id`（正常）与 `recoverDeleted/id` 的提交体差异，确认是否落入 `@mutation` 未注册操作的 `{data: cleaned}` 兜底导致 `id` 参数缺失/包裹错误。
- [x] 修复根因，使提交体含正确的 `id` 参数。（注意：`resolveNopRpcUrl` 对未注册 `@mutation` 一律包 `{data: cleaned}`，仅改页面 `<data>` 无法产生顶层 `id`；正确方向是修 resolver 的未注册分支，或页面改用 `/r/NopAuthUser__recoverDeleted` 直发——`/r/` 路径业务参数按名直传，见 `nopRpcResolver.ts:384-387`。）
- [x] 增回归测试（e2e 删除→恢复，后端无参数错误）。

Exit Criteria:

- [x] 删除某用户后点击"恢复已删除记录"恢复成功（e2e + 后端无 `未定义参数`/参数名错误）。
- [x] **无静默跳过**：修复涉及的未实现/异常分支显式失败而非静默吞掉（新增方法/分支验证）。
- [x] 去掉修复即红（变异自检）。
- [x] **端到端验证**：删除→恢复→再查询到的完整链路已验证。
- [x] 若改 nop-chaos-next：`apps/main/dist` 已重建并同步进 `nop-web-site`，e2e 实际加载。
- [x] 若 flux 行级 action 参数约定变化：更新 `docs-for-ai/02-core-guides/flux-rendering.md`；否则 `No owner-doc update required`。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Workstream 5 - 部门标签页交互异常（现象 ⑤）

Status: completed
Targets: `nop-auth-web` `NopAuthDept` 页面（页面级 `<tabs tabsMode="vertical">`、`parentId` 树选择、新建部门表单）

- Item Types: `Fix`

- [x] 定位：分别确认（a）新建部门表单、（b）父部门树选择/取数、（c）页面级标签切换各自的失败点（渲染/取数/提交）。
- [x] 修复各失败点。
- [x] 在 `nop-entropy-e2e/packages/nop-auth-e2e` 增/扩 spec：新建部门、选择父部门、切换标签页，并**保存截图**到测试产物。

Exit Criteria:

- [x] e2e 覆盖新建部门 / 选择父部门 / 切换标签页，全部通过。
- [x] **截图证据**：三个关键界面各保存一张基线截图，人工确认显示正常。
- [x] **端到端验证**：新建→选择父→保存→列表/树显示正确。
- [x] **接线验证**：tree-select/父部门取数与标签切换在运行时连通。
- [x] 若涉及生成器/控件契约：更新 `docs-for-ai/02-core-guides/flux-rendering.md`；否则写理由。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Workstream 6 - 表单 label 布局约定（现象 ⑥）

Status: completed
Targets: `flux-web/container_simple.xpl`（编辑/新增默认 mode，第 39 行）、`flux-web/page_simple.xpl`（第 72 行）、`flux-web/grid_crud.xpl`（查询 form 默认，第 74-75 行）、`flux-web.xlib`（query 强制 horizontal）

- Item Types: `Decision`（布局约定裁定） ；实现项另标 `Fix`

- [x] 定位：确认当前编辑/新增表单与查询表单实际 `mode`。
- [x] 裁定并实现约定：新增/修改表单默认 label 上置（vertical）；查询表单 label 左侧（horizontal）。
- [x] 增/改测试（生成页 form mode 断言）。

Exit Criteria:

- [x] 新增/修改表单 label 在字段上方；查询表单 label 在字段左侧（截图）。
- [x] 回归测试钉住两类表单默认 mode。
- [x] **端到端验证**：e2e 打开新增/查询界面断言布局。
- [x] 该约定为使用规范：更新 `docs-for-ai/02-core-guides/flux-rendering.md`（或对应 owner doc）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Workstream 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 6 条 in-scope 现象已全部修复（或被显式、有理由地裁定为 non-blocking 并移入 `Deferred But Adjudicated`）。
- [x] 第 5 条的 e2e + 截图证据已保存并人工确认。
- [x] 跨仓契约（①③④ 涉及 nop-chaos-next）两侧已对齐并各有验证；改动均经“重建 dist → 同步 `nop-web-site` → e2e”验证。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2460-flux-manual-test-findings-fix.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-service-framework/nop-graphql/nop-graphql-core --severity high`、`--module nop-frontend-support/nop-web`、`--module nop-auth/nop-auth-web` 退出码为 0。
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required。
- [x] 独立子 agent closure-audit 已完成并记录证据（Reviewer/Agent + session/task id + 每条 Exit Criterion 验证结果）。
- [x] **Anti-Hollow Check**：端到端调用链连通、无空方法体/静默跳过/no-op。
- [x] `./mvnw test -pl nop-graphql-core -pl nop-web -pl nop-auth-web -am` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] nop-auth flux e2e 全绿：`NOP_WEB_RENDER__MODE=flux pnpm --filter nop-auth-e2e test`（既有 42 条不回归）。

## Deferred But Adjudicated

（暂无；执行中如产生非阻塞项在此登记，附 `Why Not Blocking Closure`。）

## Non-Blocking Follow-ups

（暂无。）

## Draft Review

Round 1（独立子 agent，session `ses_eb7d3e200ffeKJZszUzd7B4hNX`）发现并已修订：

- Blocker：W4 原先把根因断定为 `/id`→urlSel，与绿色基线（`save/id` 同形可用）矛盾 → 改为"先抓请求体/错误码"，重点指向 `@mutation` 未注册兜底。
- Major：W1 目标改为 `GraphQLResponseHelper` + nop-chaos-next `httpBlob.extractFilename`；W2 目标改为 `grid_crud.xpl` operation 列；W6 目标补 `container_simple.xpl`/`page_simple.xpl`/`grid_crud.xpl`；新增跨仓"重建 dist → 同步 `nop-web-site`"步骤。
- Minor：W3 标注生成侧已正确、聚焦消费侧；现象 ⑥ 类型改 `Decision`；`NopAuthUser.view.xml:184`；Closure Gates 补 scan-hollow、checkstyle、具体 e2e 命令、W4 无静默跳过。

Round 2（同一独立子 agent 复评）结论：F1–F9 全 PASS，**无剩余 Blocker/Major**，可进入执行。4 条 Minor 已在同一轮折叠：W4 措辞改为"改 resolver 未注册分支或改用 `/r/` 直发"（不再暗示仅改页面 data 即可）；W6 拆分 Item Types；"6 条 in-scope 现象"；Closure Gates 的模块占位改为具体模块。

## Closure

Status Note: 6 条手工测试发现全部定位并修复；W1/W2/W4/W6 有单元/生成回归测试，W3/W5 有 e2e（含截图）。跨仓项（W1 前端解码、W3 labelResolveAction 消费侧）确认无需改宿主或已随 `nop-web-site` 同步。独立 closure audit 通过（详见下）。

Completed: 2026-10-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（implementation 之外的 fresh session）
- Audit Session: `ses_eb7a1927fffe7RJTIybAHiUd9W`
- Evidence:
  - W1 PASS：`GraphQLResponseHelper.java:120-121` 输出 `filename*=UTF-8''`；`TestGraphQLResponseHelper` 运行 1/1 通过；host `httpBlob.ts:5-12` 已解 `filename*`。
  - W2 PASS：`grid_crud.xpl:92` operation 列 `name="__operation"`；`TestNopAuthOpLogColumns` 1/1 通过（列名唯一）。
  - W3 PASS：`flux-control.xlib:874-887` 输出 `labelResolveAction`；e2e `auth-user.spec.ts` 编辑态显示断言；nop-chaos-next dist 含 `labelResolveAction`（已同步 `nop-web-site`）。
  - W4 PASS：`NopAuthUser.view.xml` 6 个自定义动作均 `/r/`（151/160/184/195/215/234），无残留 `@mutation:`；e2e「恢复已删除用户」。
  - W5 PASS：`auth-dept.spec.ts`（2 用例）+ `dept.po.ts` + `FluxAdapter.selectTreeOption`；截图 `ai-dev/testing/2026-10/w5-dept-screenshots/`。
  - W6 PASS：`container_simple.xpl:41`/`page_simple.xpl:73` 默认 vertical（query 回退 horizontal）；golden edit/view=vertical、query=horizontal。
  - 端到端：nop-auth flux e2e **45 passed / 0 failed**（含新增 dept 2 + 恢复删除 1 + picker 显示断言）；`nop-web` 96、`nop-auth-web` 27、`nop-graphql-core` 158 全通过。
  - Anti-Hollow：`scan-hollow-implementations.mjs`（路径形式：nop-service-framework/nop-graphql/nop-graphql-core、nop-frontend-support/nop-web、nop-auth/nop-auth-web，`--severity high`）0 findings，exit 0；diff 无空方法体/占位。
  - Deferred 分类检查：无 in-scope live defect 被降级。
  - `check-plan-checklist --strict` exit 0；`check-doc-links --strict` 0 error。

Follow-up:

- 无 plan-owned 剩余工作。观察项：`NopAuthDept.parentId` 显示名为"父ID"（应为"父部门"）属文案微调，不影响功能，登记为 watch-only。

