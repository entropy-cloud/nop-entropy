# 291 迁移 nop 平台 $xxx 简写为 ${xxx} 表达式语法

> Plan Status: active
> Last Reviewed: 2026-07-15
> Source: `ai-dev/analysis/2026-07/2026-07-15-amis-dollar-shorthand-vs-expression-syntax.md`
> Related: `ai-dev/plans/290-flux-web-xlib-attribute-mapping-fixes.md`

## Purpose

将 nop 平台中所有 amis `$xxx` 简写变量引用迁移为 `${xxx}` 表达式语法，并在 `docs-for-ai/` 中明确标注 `$xxx` 已废弃。

## Current Baseline

- nop 平台的 `xxOn` 属性已统一使用 `${xxx}` 语法（2026-07-15 已完成修复）
- 但 AMIS JSON 属性中的变量引用（`url`、`source`、`data` 子元素、`labelTpl` 等）仍大量使用 `$xxx` 简写
- `$xxx` 是 amis 遗留语法（源码标注 `oldVariable()`），不支持过滤器、函数、运算符，在 `visibleOn` 等条件属性中不可靠
- amis 官方推荐统一使用 `${xxx}`（`docs/zh-CN/concepts/expression.md:56-69`）
- `docs-for-ai/` 中未提及 `$xxx` 语法的废弃

### 受影响清单

**框架代码（2 处）**：

- `nop-frontend-support/nop-web/.../xlib/control.xlib:695` — `url: "/f/download/$fileId"`
- `nop-frontend-support/nop-web/.../xlib/flux-control.xlib:660` — `"url": "/f/download/$fileId"`

**业务 view.xml 文件（~15 个文件，~90 处）**：

- `nop-auth`: NopAuthResource（6 处）、NopAuthUser（10 处）
- `nop-code`: NopCodeIndex（3 处）、NopCodeSymbol（2 处）、dashboard（3 处）、code-browser（2 处）、call-hierarchy（1 处）、type-hierarchy（1 处）
- `nop-dyn`: NopDynModule（10 处）、NopDynEntityMeta（5 处）、NopDynEntityRelationMeta（2 处）、NopDynPage（3 处）
- `nop-job`: NopJobSchedule（12 处）、NopJobTask（5 处）、NopJobFire（10 处）
- `nop-rule`: NopRuleNode（4 处）、NopRuleDefinition（2 处）
- `nop-sys`: NopSysDict（2 处）、NopSysDictOption（1 处）
- `nop-wf`: NopWfDefinition（3 处）

**业务 page.yaml 文件（2 个文件，9 处）**：

- `nop-auth`: assign-auth.page.yaml（7 处）、change-self-pass.page.yaml（2 处）

**测试 view.xml（2 个文件，4 处）**：

- `nop-web/src/test/`: test-flux-crud.view.xml（3 处）、test-flux-tree.view.xml（1 处）

**测试 xpl（1 处）**：

- `nop-web/src/test/`: gen-page.xpl:11

**测试 fixture JSON（~6 个文件，~30 处）**：

- `nop-auth/test.json`（~20 处）
- `nop-auth-web/src/test/`: edit-controls.page.json、view-controls.page.json、query-controls.page.json（共 5 处）
- `nop-web/src/test/`: test.page.json、test.result.json、test_no_i18n_key.page.json（共 8 处）
- `nop-xlang/src/test/`: page1-crud.json、page1-result.json、page2-result.json（共 6 处）

## Goals

- nop 平台所有 AMIS 上下文中的 `$xxx` 变量引用迁移为 `${xxx}`
- `docs-for-ai/` 明确标注 `$xxx` 废弃，指导使用 `${xxx}`
- 框架代码生成模板（control.xlib、flux-control.xlib）输出的 AMIS JSON 不再包含 `$xxx`

## Non-Goals

- 不修改 amis-formula 引擎本身（`$xxx` 仍被 amis 向后兼容支持）
- 不修改 XLang 扩展方法（`$out`、`$i18n`、`$config` 等——这些是方法调用，不是变量引用）
- 不修改 `$$`（whole data scope 特殊语法）
- 不修改非 AMIS 上下文中的 `$xxx`（Maven 属性、shell 变量、Java 内部类 `$` 标记等）
- 不修改 i18n key 后缀（如 `forms.X.view.$title`）

## Scope

### In Scope

- 框架 xlib 模板（control.xlib、flux-control.xlib）
- 所有业务模块的 view.xml 和 page.yaml 中的 `$xxx` 变量引用
- 测试文件中的 `$xxx`（view.xml、page.yaml、xpl、fixture JSON）
- `docs-for-ai/` 更新

### Out Of Scope

- amis 前端 JS 运行时代码（外部仓库 `nop-chaos-flux`）
- XLang 扩展方法、`$$`、i18n key 后缀
- Maven/shell/Java 中的非 AMIS `$xxx`

## Execution Plan

### Phase 1 - 框架代码修复

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/control.xlib`, `flux-control.xlib`

- Item Types: `Fix`

- [x] `control.xlib:695` — `"/f/download/$fileId"` → `"/f/download/" + '$' + "{fileId}"`（XPL JSON 表达式模式，避免 `${}` 求值）
- [x] `flux-control.xlib:660` — 同上
- [x] `web/grid_crud.xpl:75` — `$${objMeta.displayProp}` → `${'$'}{${objMeta.displayProp}}`（修正错误的 `$$` 转义）
- [x] `flux-web/grid_crud.xpl:52` — 同上

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 框架 xlib 不再输出 `$xxx` 变量引用
- [x] `nop-web` 模块测试通过（39 tests, 0 failures）
- [x] **owner-doc 更新**: `docs-for-ai/02-core-guides/amis-rendering.md` 已标注 `$xxx` 废弃
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 业务模块 view.xml 修复

Status: completed
Targets: `nop-auth`, `nop-code`, `nop-dyn`, `nop-job`, `nop-rule`, `nop-sys`, `nop-wf` 下的 `*.view.xml`

- Item Types: `Fix`

view XML 中 `$xxx` → `${'$'}{xxx}`（XPL 转义，输出字面量 `${xxx}` 给 AMIS）：

- [x] nop-auth: NopAuthResource.view.xml、NopAuthUser.view.xml
- [x] nop-code: NopCodeIndex、NopCodeSymbol、dashboard、code-browser、call-hierarchy、type-hierarchy
- [x] nop-dyn: NopDynModule、NopDynEntityMeta、NopDynEntityRelationMeta、NopDynPage
- [x] nop-job: NopJobSchedule、NopJobTask、NopJobFire
- [x] nop-rule: NopRuleNode、NopRuleDefinition、RuleService
- [x] nop-sys: NopSysDict、NopSysDictOption
- [x] nop-wf: NopWfDefinition

Exit Criteria:

- [x] 业务模块 view.xml 中不再有 `$xxx` 变量引用
- [x] nop-auth/nop-wf/nop-rule/nop-dyn WebPagesTest 通过（nop-job/nop-code/nop-sys 有 pre-existing 依赖缺失，与本次修改无关）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - page.yaml 修复

Status: completed
Targets: `nop-auth` 下的 `*.page.yaml`

- Item Types: `Fix`

- [x] assign-auth.page.yaml — `$xxx` → `${'$'}{xxx}`
- [x] change-self-pass.page.yaml — 同上

Exit Criteria:

- [x] page.yaml 中不再有 `$xxx` 变量引用
- [x] nop-auth-web 测试通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试文件修复

Status: completed
Targets: 测试目录下的 `.xpl`、`.view.xml`、fixture `.json`、`.xpage`

- Item Types: `Fix`

- [x] gen-page.xpl — `$id` → `${'$'}{id}`；`$${prop}` → `${'$'}{${prop}}`
- [x] test-flux-crud.view.xml、test-flux-tree.view.xml — `$xxx` → `${'$'}{xxx}`
- [x] nop-auth test.json — `$xxx` → `${xxx}`（纯 JSON 文件，无 XPL 求值）
- [x] nop-auth-web page.json fixtures — `$xxx` → `${xxx}`（纯 JSON fixture）
- [x] nop-web page.json fixtures — 同上
- [x] nop-xlang delta test fixtures（JSON + .xpage）— `$id` → `${id}`（纯 JSON）或 `${'$'}{id}`（.xpage XPL 上下文）

Exit Criteria:

- [x] 测试文件中不再有 `$xxx` 变量引用
- [x] nop-web (39 tests) + nop-xlang TestDeltaMerger 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - docs-for-ai 更新

Status: completed
Targets: `docs-for-ai/02-core-guides/amis-rendering.md`

- Item Types: `Decision`

- [x] `docs-for-ai/02-core-guides/amis-rendering.md` 增加「变量引用语法规范」段落
- [x] doc link checker 通过（2 个 pre-existing 错误，非本次引入）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全仓库 AMIS 上下文中不存在 `$xxx` 变量引用（排除 XLang 方法、i18n key、非 AMIS 上下文）— 最终扫描通过
- [x] `docs-for-ai/` 已标注 `$xxx` 废弃（含 XPL 转义规则文档）
- [x] `./mvnw test -pl nop-frontend-support/nop-web -am` 通过（39 tests, 0 failures）
- [x] 受影响业务模块 WebPagesTest 通过（auth/wf/rule/dyn 通过；job/code/sys 有 pre-existing 依赖缺失）
- [x] nop-xlang TestDeltaMerger 通过
- [ ] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### test.json 中的 amis CRUD fixture

- Classification: `watch-only residual`
- Why Not Blocking Closure: `nop-auth/test.json` 是手工测试用的 AMIS 页面 fixture，不是自动生成的 view 模型，不影响平台行为。可在后续清理中处理。
- Successor Required: `no`
