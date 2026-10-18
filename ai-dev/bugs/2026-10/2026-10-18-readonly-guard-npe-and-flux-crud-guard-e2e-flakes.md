# 2026-10-18 全量回归修复：只读守卫 NPE、flux 树形 CRUD 守卫失效、e2e 组合框归属误判

> 状态：**已修复**（2026-10-18）。全量 `./mvnw install -T 1C` 453 模块 16381 用例 0 失败；e2e 四包 auth 45 / code 9 / job 6 / web 3 全绿。
> 触发：用户要求「确保单元测试和 e2e 测试都完全通过」的全量回归。三处缺陷互相独立，其中两处是产品代码缺陷、一处是测试基础设施缺陷。

## Problem

- **(A) nop-metadata 单测红**：`TestSaveOverrideNullDataGuard` 2 例失败——`save(null)` / `save({})` 抛 `NullPointerException` 而非契约要求的 `ERR_BIZ_EMPTY_DATA_FOR_SAVE`。10 个 metadata BizModel 的 `save` override 直调全受影响。
- **(B) nop-code-app 起不来**：`quarkus:dev` 启动即 `nop.err.ioc.start-failed: NopEvalException[nop.err.xlang.exec.get-prop-on-null-obj]`，位置 `/nop/web/xlib/flux-web/grid_crud.xpl:35:84`，页面 `/nop/code/pages/code-browser/main.page.yaml`。连带 nop-code-e2e 2 例失败（后端未启动）。用 git stash 回退我的改动重跑仍复现 → 与本次改动无关，是既存回归。
- **(C) nop-job-e2e 间歇失败**：`job-flow.spec.ts` 两个用例报 `flux 组合框选择失败`，一次报选项为空 + 界面「加载选项失败。」，一次报选项 `visible:false`。间歇性，同一份代码 6 连跑中 2 次挂。

## Diagnostic Method

### A

- 先跑 `-pl nop-metadata/nop-metadata-service -Dtest=TestSaveOverrideNullDataGuard` 取完整栈，直接看到 NPE 源：`CrudBizModel.getThisObj():243` ← `getObjMeta():228` ← `isReadOnly():275` ← `checkReadOnly():287` ← `save():584`。
- 排除「metadata override 解引用 data」的假设（既有代码已在 `super.save` 前 `isEmptyMap` 提前委托）：栈里没有 metadata 任何帧，NPE 发生在基类 `save()` 的第一行 `checkReadOnly("save")`，早于 `doSave()` 的空数据守卫。
- `git log -S 'checkReadOnly("save")'` 定位到 `340b22e335 feat(biz): CrudBizModel 内建 read-only 开关（objMeta 驱动）`：守卫被插到 `doSave()` 的空数据检查**之前**，而 `getThisObj()` 在无容器（纯单测 `new` 出 BizModel 直调）时必然 NPE。

### B

- 第一个假设是「改了 jar 之后没生效」。排除：`unzip -p` 直接读 `~/.m2` 与 `target/project-local-repo` 里的 `nop-web` jar，第 35 行确实是带守卫的新代码；`_dump` 里也没有旧副本。
- 第二个假设是「三目运算符不短路」。排除：`BuildExecutableProcessor.processIfStatement` 把三元编译成 `IfExecutable.execute()`，先算 test 再分支，短路成立。
- 关键推断：`objMeta` 是 xpl unit 的 `implicit` attr，在无 entity 的 crud 上根本没绑定；`objMeta == null` 对「未绑定」不成立，于是走进 else 分支访问 `objMeta.tree` 抛 eval 错。
- 决定性证据：把守卫换成仓库既有惯用法（`flux-web.xlib:374` 的 `objMeta?.getProp(...)` 形式）后，`nop-code-app` 冷启动 5.4s 成功启动，`nop-web` 97 例仍全绿。

### C

- 先怀疑后端：curl `POST /r/DictProvider__getDict` 返回 200 + 完整 options；再用 Playwright 探针抓浏览器请求/响应，发现浏览器侧同样拿到 `{"data":{...},"status":0}`（`status` 在 JSON 末尾，最初只看前 300 字符误判为缺失）。**后端与网络链路均正常**。
- 读 flux runtime bundle（`pkg-nop-chaos-flux-Dye4xcB6.js.gz`）里 `Qv()`（dict 选项 hook）：错误文案 `flux.form.failedToLoadOptions` 只在 `loadDict` promise reject 时出现，且 effect cleanup 会 `abort()`——间歇性 abort 竞态指向客户端。
- 复现稳定后第二次失败给出了真凶：选项确实加载了（5 个值全在），但全部 `visible:false`，且值域是**上一个字段**（scheduleStatus 的 0/10/20/30/40）——即 `FluxAdapter.openCombobox()` 用页面级 `[data-slot="combobox-content"][data-open]` 判「已打开」，把上一字段关闭动画中尚未摘除 `data-open` 的弹层误当成本字段已打开。

## Root Cause

- **A**：`CrudBizModel.save()` 的 `checkReadOnly()` 早于 `doSave()` 的空数据守卫；`isReadOnly()` → `getObjMeta()` → `getThisObj()` 在 `bizObjectManager` 未注入时直接 NPE，绕过了本该先落地的 typed 错误，破坏「null/empty data 直落 `ERR_BIZ_EMPTY_DATA_FOR_SAVE`」的既有契约。
- **B**：flux 树形 CRUD 的空 objMeta 守卫写成了 `objMeta == null ? ... : objMeta.tree`，对 xpl `implicit` attr **未绑定**（而非绑定为 null）的情形不成立——显式 null 与未绑定在该引擎里不是一回事，必须用 null-safe 访问符 `?.`。
- **C**：e2e 适配层把「页面存在任一打开的组合框弹层」当作「本字段弹层已打开」，连续填多个下拉时命中上一字段的残留弹层，点选落在隐藏 DOM 上。

## Fix

- **A**：`CrudBizModel.getThisObj()` 在 `bizObjectManager == null` 时返回 null，`getObjMeta()` 相应返回 null（与 `IBizObject.requireObjMeta()` 允许 `getObjMeta()` 为 null 的既有契约一致）；`isReadOnly()` 逻辑不变，容器内行为零变化。
- **B**：`grid_crud.xpl` 改为 `const childrenProp = objMeta?.tree?.childrenProp ?? null;`，与 `flux-web.xlib` 其余位置的可选链惯用法统一，`?? null` 保证下游 `!= null` 判断拿到确定的 null。
- **C**：`FluxAdapter.openCombobox()` 先检测并关掉任何残留打开弹层（Escape + 等待 `hidden`），确认全局无弹层打开后再点本字段触发器；`CrudListPage.waitForRow()` 由「找到才断言、找不到静默放过」改为轮询到行出现、超时抛错，消除静默 no-op。

## Tests

- `nop-ai/nop-ai-agent/.../TestBackgroundRegistryJobSink.java` — submit 后立即断言 RUNNING 属竞态，改为 latch 闸住任务体，断言变确定。
- `nop-ai/nop-ai-toolkit/.../TestBashExecutorBackgroundMode.java` — 同类竞态，`StubRegistry` 增加 `startGate`，闸住 fork 后再断言 RUNNING。
- `nop-service-framework/nop-biz/.../TestReadOnlyCrudBizModel.java` — 新增 `testNoContainerIsWritableAndEmptyDataStillFallsToBaseError`：无容器时 `isReadOnly()` 不得 NPE，且 `save(null)` 仍落 `nop.err.biz.empty-data-for-save`。
- `nop-metadata/.../TestSaveOverrideNullDataGuard.java` — 未改动，作为 A 的既有回归面（10 个 BizModel × null/empty）。
- `nop-frontend-support/nop-web/.../TestFluxWebCrudPage.java` — 11 例（树形 rowChildrenField + 非树负例）保护 B 的改写。
- `nop-entropy-e2e/packages/e2e-shared/src/FluxAdapter.ts`、`nop-entropy-e2e/packages/e2e-shared/src/CrudListPage.ts` + `nop-entropy-e2e/packages/nop-auth-e2e/tests/auth-user.spec.ts` — 保护 C；auth 的「恢复已删除用户」补 `waitForRow` 前置等待，消除表格已渲染但数据未到导致的行查找竞态。

## Affected Files

- `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java`
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/grid_crud.xpl`
- `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/tool/TestBackgroundRegistryJobSink.java`
- `nop-ai/nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/TestBashExecutorBackgroundMode.java`
- `nop-service-framework/nop-biz/src/test/java/io/nop/biz/crud/TestReadOnlyCrudBizModel.java`
- `nop-entropy-e2e/packages/e2e-shared/src/FluxAdapter.ts`
- `nop-entropy-e2e/packages/e2e-shared/src/CrudListPage.ts`
- `nop-entropy-e2e/packages/nop-auth-e2e/tests/auth-user.spec.ts`

## Notes For Future Refactors

- xpl 里访问 `implicit` attr（`objMeta`/`dispMeta`/`propMeta` 等）一律用 `?.` + `?? 默认值`，不要写 `x == null ? 默认 : x.y`——**未绑定 ≠ null**，该写法只在显式传 null 时成立（这也是 nop-web 单测覆盖不到、只有真实应用冷启动才暴露的原因）。
- `CrudBizModel` 的写动作守卫顺序（read-only 先于空数据）是刻意设计，不要为了「看起来更早失败」调整；容器外直调的可空处理留在 `getThisObj()/getObjMeta()` 一层。
- 后台任务类测试断言「刚 submit 就是 RUNNING」必须先给任务体加闸门，否则并行构建下必然出现假红。
- e2e 里任何「页面级」状态判据（`[data-open]` 之类）都要先问一句：是不是别的组件也可能置位。