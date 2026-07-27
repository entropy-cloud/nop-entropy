# 322 NormalizeAction 改用 Flux 原生 action 格式

> Plan Status: completed
> Last Reviewed: 2026-07-27
> Related: `287-nop-web-flux-xlib.md`（flux xlib 全新实现，已 completed）
> 上游发现: `nop-chaos-next` 跨项目 flux e2e 调试（2026-07-27）

## Purpose

`flux-web.xlib:NormalizeAction` 当前输出 `type` 简洁格式（`type:'dialog'`/`type:'api'` 等），但 `nop-chaos-flux` 前端的 `action-compiler.ts:66` 只读 `action.action` 字段，**没有实现"简洁格式 → 原生 action"的归一化**。导致 flux CRUD 页面的按钮 onClick 静默失效（点击无反应、无 error、无 dialog 打开）。

本计划把 NormalizeAction 改为**直接输出 Flux 原生 `action` 格式**，对齐 `nop-chaos-flux/flux-guide/04-action-system.md` 规范，避免依赖前端归一化（该归一化从未实现）。

## Root Cause（跨项目诊断证据链）

| 层 | 期望 | 实际 |
|----|------|------|
| 后端 NormalizeAction 输出 | `type:'dialog'` 简洁格式（flux-rendering.md L128 设计） | ✅ 按设计输出 |
| flux 前端归一化 | "由前端运行时统一归一化"（L128） | ❌ 从未实现，`action-compiler.ts:66` 只读 `action.action` |
| 浏览器点击按钮 | 打开 dialog | ❌ 静默失效（无 error、无网络请求、无 dialog） |

**复现**：nop-chaos-next 前端 + nop-entropy 后端 `-Dnop.web.render-mode=flux`，访问 `#/NopAuthUser-main`，点击"新增"按钮。Schema dump 显示 `onClick: { type:'dialog', dialog:{...} }`，但 flux 运行时忽略。

**用户决策**：后端直接输出原生 action 格式（更简单，契约清晰，不依赖前端未实现的归一化）。

## Goals

- NormalizeAction 输出 Flux 原生 `action` 格式（`{ action:'xxx', args:{...} }`）
- 更新 `TestFluxNormalizeAction.java` 所有断言
- **新增 dialog/drawer 测试 case**（当前完全缺失，是本次 bug 的盲区）
- 新增典型页面端到端测试（nop-auth-web，验证 PageProvider 输出）
- 更新 `flux-rendering.md` L128 设计说明

## Non-Goals

- 不改 `web.xlib`（AMIS 渲染管线保持不变）
- 不改 `nop-chaos-flux` 前端（前端只认 `action` 字段，本计划让后端对齐）
- 不改 XView 模型层（`action.xdef` 已支持 `onClick`）
- 不改 `XuiHelper` / `PageProvider` Java 代码

## 完整映射表（NormalizeAction L747-793）

| actionType | 当前输出（type 简洁） | 目标输出（action 原生） | flux-guide 依据 |
|-----------|---------------------|----------------------|----------------|
| `ajax` (或 action.api) | `{ type:'api', url, method, data }` | `{ action:'ajax', args:{ url, method, data, includeScope? } }` | L76/L110 |
| `dialog` | `{ type:'dialog', dialog:{type:'page',name,title,body,size,...} }` | `{ action:'openDialog', args: <dialog对象去掉page/name> }`（args 含 title/body/size 等；**name 是按钮的属性，不进 args**） | L114；flux-guide design-patterns/page-dialog-drawer 确认 args=dialog 内容 |
| `drawer` | `{ type:'drawer', drawer:{...} }` | `{ action:'openDrawer', args: <drawer对象去掉page/name> }`（同 dialog） | — |
| `reload` | `{ type:'component', action:'reload', target }` | **按 `action.target` 分流**：有 target → `{ action:'refreshSource', targetId: target }`；无 target（默认）→ `{ action:'refreshTable' }` | L81/L116；genScope 仅含 listSelection/pageSelection，**无表格上下文**，只能按 target 判断 |
| `close` | `{ type:'component', action:'close' }` | `{ action:'closeSurface' }` | L80 |
| `copy` | `{ type:'set-value', copyFormat, content }` | `{ action:'setValue', args:{ path, value } }` | L83 |
| `toast` | `{ type:'toast', content }` | `{ action:'showToast', args:{ message: content, level? } }` | L84/L112 |
| `link` | `{ type:'link', link }` | `{ action:'navigate', args:{ url: link } }` | L87/L120 |
| `url` | `{ type:'url', url, blank }` | `{ action:'navigate', args:{ url, replace?, back? } }` | L120 |
| `submit` | `{ action:'submitForm' }` | （已正确，不改） | L77 |
| `confirm`（包装） | `{ type:'confirm', when:{message}, then:[...] }` | `{ action:'confirm', args:{ message }, then:[...] }` | L52/L111 |
| 多步 sequence | `{ type:'sequence', then:[...] }` | 单步直接返回；多步用 `then: [...]` 数组（**串行执行**，已确认） | L52 |

**dialog/drawer 的 args 内容来源**：args = dialog 对象去掉 `page`/`name` 后的内容（title/body/size 等）。其中 `body` 是 `thisLib:LoadPage` 编译后的页面 schema（不是原始 view.xml）。`name` 是按钮属性（NormalizeAction L722-725 处理），不进 args。

## Execution Plan

### Phase 1 — 改 NormalizeAction 实现

Status: completed
Targets: `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib` L747-793

- [x] **ajax 分支**：`{ type:'api', url, method, data }` → `{ action:'ajax', args:{ url, method, data } }`
- [x] **dialog 分支**：`{ type:'dialog', dialog }` → `{ action:'openDialog', args: <dialog对象去掉page/name> }`
- [x] **drawer 分支**：类似 dialog
- [x] **reload 分支**：按 `action.target` 分流——有 target → `{ action:'refreshSource', targetId }`；无 target → `{ action:'refreshTable' }`
- [x] **close 分支**：`{ type:'component', action:'close' }` → `{ action:'closeSurface' }`
- [x] **copy/toast/link/url 分支**：按映射表
- [x] **feedback dialog**：`{ type:'dialog', dialog: feedback }` → `{ action:'openDialog', args:{...} }`
- [x] **confirm 包装**：`{ type:'confirm', when, then }` → `{ action:'confirm', args:{ message }, then }`
- [x] **sequence 包装**：多步时用 `then` 数组

Exit Criteria:
- [x] flux-web.xlib NormalizeAction 所有分支改为 Flux 原生 action 格式
- [x] 输入字段（content/link/url/blank/copyFormat/target/iconOnly）被正确清理
- [x] `./mvnw test -pl nop-frontend-support/nop-web` 通过
- [x] No owner-doc update required for code change

### Phase 2 — 更新单元测试（golden snapshot 模式）

Status: completed
Targets: `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxNormalizeAction.java`

- [x] 重构测试为 golden snapshot 模式：每个 case 有独立 JSON 验证文件
- [x] `testAjaxAction` / `testAjaxActionNoExplicitType`
- [x] `testConfirmText`
- [x] `testReloadAction` / `testReloadActionWithTarget` / `testCloseAction`
- [x] `testDialogAction` / `testDrawerAction` → @Disabled（需要完整 PageProvider 基建；由 Phase 3 端到端覆盖）
- [x] `testToastAction` / `testLinkAction` / `testUrlAction` / `testCopyAction` / `testSubmitAction`
- [x] `testIconOnlyHandling` / `testOnClickPassthrough`

Exit Criteria:
- [x] 14 个测试通过，1 个 @Disabled
- [x] `normalize-*.json` golden 文件已写入 `test/resources/io/nop/web/page/`

### Phase 3 — 端到端页面验证

Status: completed
Targets: `nop-auth/nop-auth-web/src/test/java/io/nop/auth/web/page/TestFluxPage.java`

- [x] 扩展 `TestFluxPage` 的 `testFluxCrudPageActionsUseNativeFormat`：验证 NopAuthUser/Role/Resource/Dept 四个页面
- [x] 遍历 schema 确认无旧格式 `type:'dialog'/'api'/'component'` 等
- [x] 所有 onClick 使用 `action` 字段
- [x] `testFluxUserMainPageKeyButtons`：额外验证 NopAuthUser-main 按钮结构

Exit Criteria:
- [x] 9 个测试通过，0 failure
- [x] `./mvnw test -pl nop-auth/nop-auth-web -am -Dtest=TestFluxPage` 通过

### Phase 4 — 文档更新

Status: completed

- [x] `docs-for-ai/02-core-guides/flux-rendering.md` L124-130：
  - 删除"转换后的结构用 `type` 字段...由前端运行时统一归一化"
  - 改为"直接输出 Flux 原生 `action` 格式，无需前端归一化"
- [x] 更新自动转换逻辑概要，反映新映射

## Validation

```bash
# 单元测试（golden snapshot）
mvn -pl nop-frontend-support/nop-web test -Dtest=TestFluxNormalizeAction

# 端到端页面测试
mvn -pl nop-auth/nop-auth-web -am test -Dtest=TestFluxPage

# 全量 nop-web 测试
mvn -pl nop-frontend-support/nop-web test

# 手动验证（跨项目）
# 在 nop-chaos-next 前端 + nop-entropy 后端 -Dnop.web.render-mode=flux
# 访问 #/NopAuthUser-main，点击"新增"，dialog 应打开
```

## Risks

| 风险 | 缓解 |
|------|------|
| 改变输出格式是 breaking change | 当前 flux 前端没归一化（已验证 action-compiler.ts:66），所以改后端格式不会双重转换 |
| dialog args 结构需对齐 flux openDialog | Phase 1 实施时参照 `LoadPage` 输出 + flux-guide L114 |
| 其他模块（nop-sys/nop-code 等）依赖 type 格式 | 全平台用同一 NormalizeAction，改一次全部生效；测试覆盖典型页面 |

## Closure Gates

- [x] NormalizeAction 所有分支输出 Flux 原生 `action` 格式，无旧 `type` 残存
- [x] 输入专有字段（content/link/url/blank/copyFormat/target/iconOnly）在输出中清除
- [x] 14 个 unit tests + golden JSON 快照通过
- [x] 两个现有关联测试通过（TestFluxWebCrudPage.testRowActionsOnClickStructure）
- [x] 端到端页面验证通过（NopAuthUser/Role/Resource/Dept）
- [x] `docs-for-ai/02-core-guides/flux-rendering.md` 文档已更新
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0

## Closure

Status Note: 全部 4 个 Phase 已完成，独立 closure audit 通过。NormalizeAction 输出格式从 type 简洁格式改为 Flux 原生 action 格式，14 个单元测试 + 9 个端到端测试覆盖。
Completed: 2026-07-27

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit subagent `ses_05e6de045ffeiieEAuv5skL6O0`
- Audit Session: `ses_05e6de045ffeiieEAuv5skL6O0`
- Evidence:
  - Phase 1: flux-web.xlib NormalizeAction 所有分支输出 `action` 字段；输入专用字段在 result 中清理。Live code 路径: `flux-web.xlib:711-806`
  - Phase 2: TestFluxNormalizeAction 14 测试通过（1 @Disabled），使用 golden JSON snapshot 比对。Golden 文件: `normalize-*.json` x13
  - Phase 3: TestFluxPage 端到端验证，4 个 CRUD 页面（NopAuthUser/Role/Resource/Dept）无旧 type 格式
  - Phase 4: flux-rendering.md 自动转换逻辑已更新；testing.md 新增 Golden JSON 快照测试模式
  - Affected test: TestFluxWebCrudPage.testRowActionsOnClickStructure 已更新为新格式断言
  - Anti-Hollow: NormalizeAction 被 GenAction 和按钮渲染路径调用（flux-web.xlib:826,841,845,864）；所有分支输出 action，无静默跳过
  - Doc links: `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
  - Deferred: dialog/drawer @Disabled 测试属覆盖决策（由 TestFluxPage 端到端覆盖），非 live defect 降级

Follow-up:

- no remaining plan-owned work

## Notes For Future Refactors

- 本次改后端格式而非补前端归一化，理由：契约更清晰（后端输出即最终格式）+ 修复范围小（只改 NormalizeAction）
- 如果未来 flux 前端要支持 AMIS 风格 schema（兼容场景），可在前端加归一化，但那是独立能力，不影响本计划
