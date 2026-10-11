# 2404 full-code-review R1.38 — PageProvider transformPermissions 缓存污染/竞态修复（P1-4.5-B1）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.38；finding `ai-dev/audits/check/frontend-benchmark.md:47-71`（P1）+ `4.5-frontend-devtools-migration-runner-utils-audit.md:175`
> Related: `nop-frontend-support/nop-web`；`io.nop.web.page.PageProviderBizModel.getPage`（@Auth publicAccess）
> Owner Doc: `docs-for-ai/01-repo-map/module-groups.md`（归属；行为修复无契约面）
> Plan Review: agent_60133455 首轮 NOT READY（0 Blocker 于核心机制 + 1 Blocker B1「匹配 map 下嵌套 xui:permissions 永不转换」+ 4 Major：M1 Phase 次序/M2 并发判据不确定/M3 none/merge 无测试/M4 缓存隔离措辞过度 + 8 Minor）→ 本轮全修：copy-on-write 改为**匹配节点内复制并递归转换子节点**（闭合嵌套权限面）、单测移入 Phase 1、Exit Criteria 锚定确定性判据、补 none/merge 用例、明确 residual（mapping==null 返回活缓存 map + 未匹配子树浅共享）并在 Purpose/Closure 收窄措辞、governance 文件入 Scope、locale 固定 zh-CN、BizModel 入口裁定、item 类型修正；待聚焦复审。

## Purpose

修复 `PageProvider.getPage` 的 permissions→roles 转换对 **缓存的 PageModel 嵌套 map 就地改写**：`JsonTransformHelper.transform` 在谓词命中的 map 处停止递归并原样传给 `transformPermissions`，后者 `remove`/`put` 就地修改——该 map 由 `ResourceComponentManager` 按 locale+path 缓存共享。并发首访会观察到「permissions 已删、roles 未写」的中间态（前端跳过权限过滤），并有 HashMap 并发写损坏缓存的永久污染风险。修复后：**permissions→roles 转换不再修改缓存对象**（copy-on-write），且**匹配节点下的嵌套 `xui:permissions` 也被转换**（否则子节点无 permissions/roles，同样越权展示）。

## Current Baseline

- **P1**（live）：`PageProvider.java:190-207 getPage` 取 `ResourceComponentManager.instance().loadComponentModel(localeAndPath)` 缓存 `PageModel`，`data = pageModel.getData()`，`rolePermissionMapping != null` 时 `JsonTransformHelper.transform(data, this::transformPermissions, this::hasXuiAuth)`。`renderPage:166-188` 亦调用转换，但其 `loadPage:177` 为**非缓存**实例（`:177` 直接 loadPage），故缓存竞态只在 `getPage` 路径。
- **根因**（live）：`JsonTransformHelper.transform:30-43` 对**命中谓词的 Map 直接 `transformer.apply(value)` 并短路递归**；`transformMap:57-68`/`transformList` 对未命中层已新建容器，故唯一共享-就地写入点就是命中 map。`transformPermissions:209-228` 对该 map `remove(ATTR_XUI_PERMISSIONS)` + `put(ATTR_XUI_ROLES, mergedRoles)`，无同步。
- **嵌套语义缺口**：命中 map 的**子 map** 若也带 `xui:permissions`，因短路而永不进入 transformer——修复就地改写后仍然如此（原缺陷下缓存被污染后根节点不再命中，反而"偶然"转换了子节点；修复后必须显式递归，否则子节点无 permissions/roles）。该面属本行同一权限展示缺陷的组成部分，必须闭合。
- **风险面**：`PageProviderBizModel.getPage:37-44` 为 `@Auth(publicAccess=true)` 每请求调用。
- **既有测试**：`TestPageProvider`（`@Inject PageProvider`，`JunitBaseTestCase`）无 permissions 转换用例；`nop-web/src/test/resources/_vfs/nop/test/pages/` 有 fixture（`test.page.yaml` 最小形如 `page: x`），**无含 `xui:permissions` 的页面**。
- **residual（显式登记，不在本行缺陷面）**：`getPage:196-201` 在 `rolePermissionMapping == null` 时**按引用返回缓存 data**（本行不改）；`getPage` 结果的最外层容器为 transform 新建，但未命中子树为浅共享。故本行 closure 措辞为「permissions 转换不再改写缓存」，非泛化「缓存完全隔离」。（`JsonTransformHelper.transformMap`/`transformList` 对未命中层本就新建容器，仅叶子标量为共享引用——residual 主要是 `mapping==null` 直接返回缓存 map 的既有行为。）

## Goals

- `transformPermissions` 改为 **copy-on-write + 匹配节点内递归**：命中时构造新 `LinkedHashMap`（复制全部字段）→ 在副本上 remove/put → 对副本各 value 递归 `JsonTransformHelper.transform(..., 本转换器, hasXuiAuth)` 以转换嵌套命中节点 → 返回副本；**绝不修改入参 map**；未命中返回原值。
- roles 合并与 `none` 兜底语义**逐字保持**（含空 roles→"none"、与既有 `xui:roles` 合并两分支）。
- 回归（确定性判据）证明：①转换不污染源 map（含嵌套）；②嵌套 `xui:permissions` 也被转换；③经 `getPage` 后缓存 `PageModel.getData()` 仍含 `xui:permissions`；④并发 `getPage` 无异常且缓存不被改写（并发结果一致性为 best-effort 附加，不作主判据）。
- 受影响模块 `nop-web` 测试全绿。

## Non-Goals

- `JsonTransformHelper` 公共语义（谓词命中短路）不改；`loadPage`/`getPageSource` 行为不改。
- `rolePermissionMapping == null` 时按引用返回缓存 data 的既有行为（登记 residual，不属本行缺陷面）。
- `ResourceWithHistoryProvider` 死装配/`java.security.Timestamp` 误 import/rollback null（4.5 附带核验，另项）。
- 其余 frontend-benchmark P2/P3。

## Scope

### In Scope

- `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/PageProvider.java`（`transformPermissions` copy-on-write + 嵌套递归）
- `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/**`（新增回归测试）
- `nop-frontend-support/nop-web/src/test/resources/_vfs/nop/test/pages/`（新增 permissions fixture）
- 治理文件（Phase 3）：`ai-dev/backlog/full-code-review-roadmap.md`、`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/index.md`、`ai-dev/logs/`、本 plan

### Out Of Scope

- JsonTransformHelper 变更；其他模块

## Execution Plan

### Phase 1 - transformPermissions copy-on-write + 嵌套递归（含单测）

Status: completed
Targets: `PageProvider.java`、`nop-web/src/test/java/io/nop/web/page/**`

- Item Types: `Fix | Proof`

- [x] `transformPermissions`（语义）：命中时构造入参 map 的**浅拷贝**，在副本上移除 `xui:permissions`、写入合并后的 `xui:roles`；随后对副本的每个 entry 值递归执行同一转换（`transform`+本转换器+`hasXuiAuth`），并**以递归结果替换该 entry 的 value**（`entry.setValue`），使匹配节点下的嵌套命中也转换；返回副本，未命中返回原值。javadoc 说明「返回可安全共享的新结构、不改写入参、且转换嵌套命中节点」。**不在 plan 内写实现代码片段**（Rule #10）
- [x] roles 合并/`none` 兜底逐字保持（不重构表达式）
- [x] 单测（Phase 1 内，`io.nop.web.page` 包）：`new PageProvider()` + `setRolePermissionMapping(stub)`（显式设置，控制 (a)/(b)）；构建源：顶层节点带 `xui:permissions` 且**同时含一个不带 permissions 的同级子节点与一个带 permissions 的嵌套孙节点**（覆盖：命中层、嵌套命中、未命中同级）；经 `JsonTransformHelper.transform(root, provider::transformPermissions, provider::hasXuiAuth)` → 结果各命中层均无 `xui:permissions` 且有 `xui:roles`；**源 map（含嵌套命中层）仍含 `xui:permissions` 且无 `xui:roles`**；另覆盖 (a) 映射返回空 roles → `"none"`、(b) 既有 `xui:roles` 与映射 roles 合并
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`（内部实现修复）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] `./mvnw test -pl nop-frontend-support/nop-web -am -T 1C`

Exit Criteria:

- [x] 命中路径返回新 map、入参（含嵌套命中层）map 不变（单测断言在案）
- [x] 嵌套 `xui:permissions` 被转换（单测断言在案）
- [x] `none` 兜底与 roles 合并分支有用例（单测在案）
- [x] nop-web 全模块测试全绿
- [x] Phase 内 owner-doc 处置与 logs 条目已落

执行证据（Phase 1 + Phase 2）：
- `TestPageProviderPermissionsTransform` 5/5 绿：copy-on-write（源含嵌套命中层不变）+ 嵌套命中也转换 + `none` 兜底 + roles 合并 + `getPage` 后缓存 `PageModel.getData()` 仍含 `xui:permissions` + 8 线程并发无异常且缓存未被改写。
- **判别 stash 腿**：`git show HEAD:PageProvider.java`（就地改写版）跑该类 → **恰 5/5 FAIL**，含确定性判据 `cached PageModel data must not be mutated by getPage: expected not <null>` 与 `concurrent getPage must not mutate the cached PageModel`（`_tmp/r138-stash.log`）；恢复后 5/5 绿。
- 全量回归：`./mvnw test -pl nop-frontend-support/nop-web -am -T 1C` BUILD SUCCESS（nop-web 96/0/0 skipped 1；nop-web-page 3/0/0）。〔closure audit 勘正：原记 `nop-web-page 34/0/0` 系 `-T 1C` 交错输出误读；live 该模块仅 `TestConditionExprHelper`(1)+`TestVueTemplateParser`(2)=3 个 `@Test`，重跑与作者 `_tmp/r138-full.log` 中该模块 Results 行均为 3/0/0。〕

### Phase 2 - 缓存隔离 + 并发回归测试

Status: completed
Targets: `nop-web/src/test/**`、测试 fixture

- Item Types: `Proof`

- [x] 集成测（`JunitBaseTestCase`，`new PageProvider()` + stub `IRolePermissionMapping` 返回固定 roles）：新增含 `xui:permissions` 的页面 fixture（如 `test-permissions.page.yaml`，形如 `page: x` + `xui:permissions` + 带 permissions 的子节点），`getPage(path, "zh-CN")` 两次——结果 roles 正确；再经 `ResourceComponentManager.instance().loadComponentModel("zh-CN|"+path)` 取回缓存 `PageModel`，断言其 `getData()` **仍含 `xui:permissions`**（确定性判据；修复前 FAIL）
- [x] 并发测：N 线程（≥8）barrier 同调 `getPage` 同 path——无异常、缓存 data 仍含 `xui:permissions`（确定性判据）；「每结果均含 `xui:roles`」作为 best-effort 附加断言（非主判据，注释说明时序性）
- [x] 用 `new PageProvider()` 避免污染注入的全局 bean；如注入则 `@AfterEach` 复位 `rolePermissionMapping=null`；locale 固定启用值 `zh-CN`（缓存键与 `getPage` 归一化一致）
- [x] **BizModel 入口裁定**：`PageProviderBizModel.getPage`（publicAccess）核心为其一行 `pageProvider.getPage(path, locale)` 委托（另有 locale/文件校验前置），故 provider 方法为可接受的端到端入口；若需真实 BizModel 级测试则超出本行（Rationale 记录在 plan）
- [x] **判别验证（stash 腿）**：临时还原 `transformPermissions` 就地写法 → 单测（源被删键）+ 集成测（缓存 data 丢 `xui:permissions`）FAIL，恢复后 PASS
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] `./mvnw test -pl nop-frontend-support/nop-web -am -T 1C`

Exit Criteria:

- [x] 源/缓存 map 不被转换修改（集成测断言在案）
- [x] 并发调用无异常且缓存未被改写（断言在案；结果一致性为附加）
- [x] 判别 stash 腿 FAIL→PASS 记录在 plan
- [x] nop-web 全模块测试全绿
- [x] Phase 内 owner-doc 处置与 logs 条目已落

### Phase 3 - owner doc + 收口

Status: completed
Targets: 治理文件（roadmap/index/logs/本 plan）

- Item Types: `Proof | Decision`

- [x] owner doc 裁定：行为修复不改变模块归属/公共契约 → 显式记录 `No owner-doc update required`
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.38 翻 done；index P1-4.5-B1 → `fixed`（Lead 2026-10-10 同步完成）
- [x] 独立子 agent closure audit（`plan-closure-audit-prompt.md`），evidence 写入本 plan Closure 段
- [x] `./mvnw -q compile -pl nop-frontend-support/nop-web -am` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2404-full-code-review-R1.38-pageprovider-permissions-cache-fix.md --strict` 退出码 0（Lead 同步后重跑通过）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-frontend-support --severity high` 退出码 0

Exit Criteria:

- [x] owner doc 处置裁定记录
- [x] doc-links 检查退出码 0
- [x] roadmap/index/log 三方一致（roadmap R1.38=done、index P1-4.5-B1=fixed + R1.38 行=done、daily log 在位）
- [x] 独立 closure audit 完成并落盘

## Closure Gates

- [x] in-scope live defect（缓存就地改写/竞态 + 嵌套权限未转换）已修复
- [x] 行为/契约结果已达成（缓存隔离 + 嵌套转换 + none/merge 测试在案）
- [x] 必要 focused verification 已完成（nop-web 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步或显式 `No owner-doc update required`
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 `getPage` 真实走转换链且缓存 PageModel 不被修改（非只测 helper 存在）
- [x] `./mvnw -q compile -pl nop-frontend-support/nop-web -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2404-full-code-review-R1.38-pageprovider-permissions-cache-fix.md --strict` 退出码 0（Lead 同步后重跑通过）

## Deferred But Adjudicated

### 「loadPage 完成后一次性转换（加载期单线程）」备选方案

- Classification: `optimization candidate`
- Why Not Blocking Closure: copy-on-write + 嵌套递归已在请求期完整转换且不改写缓存，缺陷面闭合；加载期转换会改变缓存对象形态（缓存已转换结果）并影响 `getPageSource`/导出只读面，范围更大。
- Successor Required: `no`

### `rolePermissionMapping == null` 时 `getPage` 按引用返回缓存 data

- Classification: `watch-only residual`
- Why Not Blocking Closure: 既有行为（无角色映射即无转换需求），本行不改变；风险仅在「调用方自行修改返回值」时才会污染缓存，当前调用方 `PageProviderBizModel.getPage` 只序列化输出。显式登记避免泛化「缓存完全隔离」的表述。
- Successor Required: `no`

## Non-Blocking Follow-ups

- `ResourceWithHistoryProvider` 死装配与 `java.security.Timestamp` 误 import（4.5 附带核验，另项）

## Closure

Status Note: plan 2404 R1.38 可关闭：`transformPermissions` 已为 copy-on-write + 匹配节点内递归，`getPage` 返回新结构且缓存 PageModel 不被改写，嵌套 `xui:permissions` 亦被转换；独立 closure audit 亲读 live 代码与测试并独立复现判别 stash 腿（5/5 FAIL → 5/5 PASS），nop-web 全绿；roadmap R1.38=done、index P1-4.5-B1=fixed 已由 Lead 同步，`check-plan-checklist --strict` 退出码 0，无剩余 plan-owned work。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit (subagent); dispatched by session-9601d130-1bfd-4608-86de-471049fd6c0e
- Evidence:
  - E1 命中路径返回新 map、入参（含嵌套命中层）不变：PASS。live `PageProvider.java:210-245` 先 `get` 权限、`new LinkedHashMap<>(map)` 浅拷贝、只在副本 `remove`/`put`，`entry.setValue` 只作用于副本；`transformIsCopyOnWriteAndConvertsNestedMatches`（`TestPageProviderPermissionsTransform`）断言源根/命中子/嵌套孙节点仍含 `xui:permissions` 且无 `xui:roles`。
  - E2 嵌套 `xui:permissions` 被转换：PASS。`PageProvider.java:237-243` 对副本各 value 以同一 `transform`+`hasXuiAuth` 递归并回写；同一测试断言孙节点 permissions=null、roles=roleA。`JsonTransformHelper.java:30-43` 命中即短路，故必须在副本内补转；递归在命中层已完成整棵子树，外层不再二次递归，恰转换一次。
  - E3 none 兜底与 roles 合并：PASS。`noMatchingRoleFallsBackToNone`（空 roles → "none"）、`existingRolesAreMergedWithMappedRoles`（existingRole + roleA 合并）；`PageProvider.java:225-235` 分支相对 HEAD 逐字保持。
  - E4 缓存隔离（确定性判据）：PASS。`getPageDoesNotMutateCachedPageModel` 经真实 `getPage` 后以 `ResourceComponentManager.loadComponentModel("zh-CN|"+path)` 取回缓存 PageModel，断言仍含 `xui:permissions` 且无 `xui:roles`（`TestPageProviderPermissionsTransform.java:111-129`）；fixture `_vfs/nop/test/pages/test-permissions.page.yaml`。
  - E5 并发确定性判据：PASS。`concurrentGetPageNeverMutatesCachedPageModel` 8 线程 barrier 同调 `getPage`，主判据为缓存 data 仍含 `xui:permissions`（`:161-165`），结果 roles 断言标注 best-effort（`:152-155`）。
  - E6 判别 stash 腿独立复现：PASS。审计侧 `git show HEAD:PageProvider.java`（`map.remove(...)` 就地版）patch→run→restore，`-Dtest=TestPageProviderPermissionsTransform` 得 Tests run: 5, Failures: 5，含 `cached PageModel data must not be mutated by getPage`(:123) 与 `concurrent getPage must not mutate the cached PageModel`(:163)；restore 后与备份 `diff` identical（`_tmp/r138-closure-stash.log`、`_tmp/R138-closure-PageProvider.bak`）。
  - E7 focused verification：PASS。`./mvnw test -pl nop-frontend-support/nop-web -am -T 1C` BUILD SUCCESS；surefire：nop-web 96/0/0 skipped 1（`TestPageProviderPermissionsTransform` 5/5、`TestPageProvider` 9/9）；nop-web-page 3/0/0（原记 34/0/0 系 `-T 1C` 交错输出误读，已在执行证据段勘正）。
  - E8 Anti-Hollow：PASS。`getPage`（`PageProvider.java:198-201`）在 `rolePermissionMapping!=null` 时确以 `this::transformPermissions` 走 `JsonTransformHelper.transform`；`PageProviderBizModel.getPage`（@Auth publicAccess）在运行时委托 provider；`scan-hollow-implementations.mjs --module nop-frontend-support --severity high` 退出码 0（0 findings）。
  - E9 无静默跳过 / 无残留 in-scope defect：PASS。无空实现、无吞异常；`rolePermissionMapping==null` 按引用返回缓存 data 的既有行为已诚实登记为 watch-only residual（`Deferred But Adjudicated`）。
  - E10 owner-doc：PASS。`No owner-doc update required`（内部实现修复，无契约面）。
  - E11 治理：`check-doc-links.mjs --strict` 退出码 0（0 errors，6 条为无关历史计划既有 warning）；`./mvnw -q compile -pl nop-frontend-support/nop-web -am` 退出码 0。
  - E12 `check-plan-checklist.mjs --strict` 退出码 0（Lead 同步 roadmap R1.38=done / index P1-4.5-B1=fixed 后重跑：Passed 1, Failed 0；Closure Evidence 满足工具校验）。

Follow-up:

- 无剩余 plan-owned work（roadmap/index/daily log 收口已由 Lead 完成）。
- 勘正项（已在本 plan 执行证据段修正；daily log 同源数字待 Lead 一并勘正）：daily log 2026-10-10 R1.38 条目中的 `nop-web-page 34/0/0` 应为 `3/0/0`。
