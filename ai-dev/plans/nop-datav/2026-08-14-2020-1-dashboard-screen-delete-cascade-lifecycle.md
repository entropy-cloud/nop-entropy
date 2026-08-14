# 1 看板/大屏删除生命周期级联与分享访问正确性

> Plan Status: completed
> Mission: nop-datav
> Work Item: D3-2 deferred follow-up — 删除生命周期级联
> Last Reviewed: 2026-08-14
> Source: D3-2 plan `2026-08-10-1100-2-dashboard-sharing.md` Non-Blocking Follow-ups「分享令牌的批量吊销/看板删除时级联吊销所有分享」+ 本轮 DRAFT_PLANS 对剩余 backend 项的 live-code 评估
> Related: `ai-dev/plans/nop-datav/2026-08-10-1230-1-scheduled-report-generation-and-delivery.md`（ReportTask dangling）、`ai-dev/plans/nop-datav/2026-08-10-1230-2-lightweight-alert-threshold-rearm-notification.md`（AlertRule dangling）、`ai-dev/plans/nop-datav/2026-08-10-1130-2-screen-free-canvas-layout.md`（Screen 子表）

## Purpose

把「删除看板 / 删除大屏 / 删除面板 / 删除告警规则 / 删除报告任务」从当前只删单表行的行为，收口为完整的删除生命周期：子对象级联处理、公共分享访问断绝、调度消费者（定时报告任务 / 告警规则）停用并即时注销。消除删除后残留的 dangling 后果。

## Current Baseline

以下事实均已对照 live repo 核实（2026-08-14，含独立审查轮复核）：

**删除语义（地基事实）**

- `nop-datav/model/nop-datav.orm.xml` 全部实体均未配置 `useLogicalDelete`/`deleteFlagProp`（对比 `nop-auth/model/nop-auth.orm.xml:35` 等显式配置）。delFlag 仅为普通列；nop-datav 的标准 CRUD `delete` 是**物理删除**，查询也不自动过滤 delFlag。
- `nop-datav.orm.xml` 无任何 `cascade` 配置；标准 `delete(id)` 只删主表行，子表不处理。

**已确认 live gap**

- **Gap #1（公共访问残留）**：`getSharedDashboard`（`NopDatavDashboardShareBizModel.java:146-162`）经 `readLatestSnapshot(dashboardId)`（`:232-243`）直接按 dashboardId 查快照，全程不读看板表。删除看板后，启用中的分享 token 仍可匿名读到已发布快照（share 行与 snapshot 行均未被触碰）。
- **Gap #2（告警规则/报告任务 dangling）**：无级联时删除看板，panel 行残留，AlertRule 继续按 cron「正常」评估已删看板下的面板（语义 dangling，无失败信号）；删除面板时 `AlertEvaluator` 走容错路径（panel 为 null → state 记 errorMsg、返回 error 结果，`AlertEvaluator.java:92-98`，**非抛异常**）。ReportTask 侧删除看板后任务仍触发并在 `requirePublishableDashboard` 失败（`LAST_RUN_ERROR` 噪音）。
- **Gap #3（删除规则/任务不即时注销——已确认 live defect）**：`NopDatavAlertRuleBizModel` 仅覆写 3 参 `afterEntityChange(entity, action, context)`（`:69` 起，内含 unregisterRule 联动），而标准 `delete(id)` 路径 `CrudBizModel.doDeleteEntity` 调用的是 **2 参 deprecated** `afterEntityChange(entity, context)`（`CrudBizModel.java:1211`，2 参默认空实现 `:803`）。因此删除告警规则**不会触发 unregisterRule**，cron job 在进程内残留触发直至重启（ReportTaskBizModel 同构镜像）。该类 javadoc（`NopDatavAlertRuleBizModel.java:45`「disableAlertRule/delete 调 unregisterRule」）与实际行为不符。
- 调度器具备运行时增量 API：`NopDatavAlertScheduler.registerRule/unregisterRule`、`NopDatavReportScheduler.registerTask/unregisterTask`（内部即 `scheduler.removeJob(jobName(id))`，如 `NopDatavReportScheduler.java:183-187`）；init/scan 只装载 `status=ENABLED`（alert `:123-127` / report `:127-131`）。
- 状态字典已含停用值：`datav/report-task-status` dict `DISABLED=0 / ENABLED=10`（`nop-datav.orm.xml:49-52`），AlertRule.status 复用同一 dict（`:1062-1064`）——停用无需 ORM 变更。
- 审计 pattern 现为 `NopDatavDashboard__*,NopDatavPanel__*,NopDatavFilterState__*`（`nop-datav/nop-datav-app/src/main/resources/application.yaml:24`）——`NopDatavDashboardShare__*` 分享管理操作未审计（D3-2 plan 遗留一行配置）。

## Goals

- 删除看板：子对象（Panel / Tab / DatasetRef / FilterState）按 D1 裁定级联处理；该看板全部分享吊销（`enabled=0`）；关联 ReportTask（按 dashboardId）与 AlertRule（经 panel.dashboardId）置 `status=DISABLED` 并**即时** unregister（复用既有运行时增量 API）。
- 删除大屏：ScreenWidget 按 D1 裁定处理；ScreenSnapshot 处置按 D1 裁定落地。
- 删除面板：关联 AlertRule（按 panelId）置 `status=DISABLED` 并即时 unregister。
- 删除告警规则 / 报告任务（Gap #3）：标准 delete 路径触发即时 unregister，修复 javadoc 漂移。
- `getSharedDashboard` 增加看板存活防御校验（级联吊销之外的 defense-in-depth）。
- `NopDatavDashboardShare__*` 纳入审计 mutation pattern。

## Non-Goals

- 为 nop-datav 全实体引入 `useLogicalDelete` 逻辑删除语义（ORM 契约级变更，独立评估；本计划级联沿用现状物理删除语义，见 D1）。
- 快照历史管理 / 版本保留策略（D0-2 既有发布/快照语义不变；快照处置仅随 D1 裁定）。
- DatasetRef 删除被 Panel 引用时的反向引用校验（`getPanelData` 对缺失 datasetRef 已显式报错，独立评估）。
- NopDatavExportTask 级联处理：以 `sourceType`+`sourceId` 多态引用 dashboard/screen，属一次性执行历史记录（类比 ReportDelivery），不级联、保留。
- 前端删除确认 / 级联影响预览 UI（flux 侧）。
- 图像导出 PDF/PNG、nop-retry 重试（既有 deferred 裁定不变）。

## Scope

### In Scope

- `NopDatavDashboardBizModel` / `NopDatavScreenBizModel` / `NopDatavPanelBizModel` / `NopDatavAlertRuleBizModel` / `NopDatavReportTaskBizModel` 的删除生命周期行为。
- `NopDatavDashboardShareBizModel.getSharedDashboard` 防御校验。
- AlertRule / ReportTask 停用联动与调度器即时注销接线。
- 审计 pattern 配置。
- owner docs：`ai-dev/design/nop-datav/permission-sharing-design.md` 与 `schedule-report-design.md` 增补删除生命周期章节。

### Out Of Scope

- 调度器增量 API 本身的改造（register/unregister 已存在，仅复用）。
- 分享访问速率限制（既有 deferred，运营期加固项）。

## Execution Plan

### Phase 1 - 设计裁定与 owner docs 增补

Status: completed
Targets: `ai-dev/design/nop-datav/permission-sharing-design.md`、`ai-dev/design/nop-datav/schedule-report-design.md`

- Item Types: `Decision | Fix`

- [x] D1 级联深度与删除形态裁定：现状为物理删除，子对象（Panel/Tab/DatasetRef/FilterState/ScreenWidget）默认跟随物理删除（与平台现状一致）；DashboardSnapshot / ScreenSnapshot 的处置（保留作历史审计 vs 级联删除）二选一并写明理由
- [x] D2 分享吊销形态裁定：`enabled=0` 逻辑吊销（保留记录可审计）vs 删除分享行；默认倾向逻辑吊销，如改选删除需写明理由
- [x] D3 调度消费者停用语义裁定：`status=DISABLED`（dict 已有）+ 复用 `unregisterRule`/`unregisterTask` 即时注销；确认「停用 + 注销」双动作的事务边界（注销失败是否回滚删除）
- [x] D4 面板 × 大屏 widget 边界确认：ScreenWidget 无 alert/share/report 关联实体，仅 Panel 删除联动 AlertRule
- [x] D5 级联挂接机制裁定：覆写标准 `delete`/`doDeleteEntity`（参考 `docs-for-ai/03-runbooks/extend-crud-with-hooks.md` 扩展点）vs 2 参 `afterEntityChange` vs ORM/xmeta cascade-delete（注意：dashboard/screen 主表当前无 to-many 关系定义，关系在子实体侧；且**陷阱**：标准 delete(id) 路径只调 2 参 deprecated `afterEntityChange`（`CrudBizModel.java:1211`），3 参覆写在 delete 路径不触发）。裁定需说明为何所选机制在标准 delete 路径必然执行
- [x] `permission-sharing-design.md` 增补「删除生命周期与分享吊销」章节（最终结论式，无 Proposed 残留）
- [x] `schedule-report-design.md` 增补「删除联动停用与即时注销」章节（含 Gap #3 修复结论）

Exit Criteria:

- [x] 5 项 Decision 均有明确裁定且写入对应 design doc 章节（repo-observable：章节存在、结论明确、无待定措辞）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No new test required: 纯 Decision/文档 Phase，行为测试落在 Phase 2-4

### Phase 2 - 看板删除级联 + 分享访问防御 + 删除即时注销

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavDashboardBizModel.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavDashboardShareBizModel.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavAlertRuleBizModel.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavReportTaskBizModel.java`

- Item Types: `Fix`

- [x] 看板删除路径实现级联（按 D1/D2/D3/D5 裁定）：子对象级联、全部分享吊销、关联 ReportTask/AlertRule 置 DISABLED 并即时 unregister
- [x] 修复 Gap #3：删除告警规则/报告任务的标准 delete 路径触发即时 unregister（按 D5 机制），修正 `NopDatavAlertRuleBizModel.java:45` 等 javadoc 漂移
- [x] `getSharedDashboard` 增加看板存活校验：看板已删时显式拒绝（新错误码或复用 `ERR_DATAV_SNAPSHOT_NOT_FOUND` 语义，D1/D2 裁定时定），不得静默返回旧快照
- [x] 新增行为全部有对应单元测试（见 Exit Criteria 清单）

Exit Criteria:

- [x] 测试证明：删除看板后 `getSharedDashboard(旧 token)` 显式拒绝（断言具体错误码；经 biz 层 delete action 触发删除，非直调内部方法）— `TestNopDatavDeleteLifecycle.testDeleteDashboardRevokesSharedAccess`（ERR_DATAV_SHARE_DISABLED）
- [x] 测试证明：删除看板后该看板全部分享 `enabled=0`（DAO 级断言；注意 `listShares` 在看板删除后因 ownership 校验不可用，不得作为观察通道）— `testDeleteDashboardDisablesAllShares`
- [x] 测试证明：删除看板后关联 ReportTask 与 AlertRule `status=DISABLED` 且调度器 job 已注销（经调度器注册表可观察，如 `getRegisteredJobNames()` 或等价 API）— `testDeleteDashboardDisablesSchedulersAndCascadesChildren`
- [x] 测试证明：`getSharedDashboard` 对「分享启用但看板已删」的构造场景显式拒绝（防御校验独立于级联生效）— `testSharedDashboardDefenseRejectsDeletedDashboardIndependently`（新错误码 `ERR_DATAV_SHARE_DASHBOARD_NOT_FOUND`）
- [x] 测试证明：直接删除告警规则/报告任务后 cron job 即时注销（Gap #3 回归测试，断言注册表不含该 job）— `testDeleteAlertRuleUnregistersCronJobImmediately` / `testDeleteReportTaskUnregistersCronJobImmediately`
- [x] **无静默跳过**：级联中任一子步骤失败显式抛错，不允许吞掉继续（级联实现无 try/catch，异常传播回滚删除）
- [x] **接线验证**：级联逻辑确实挂在标准 CRUD 删除路径上（测试经 biz 层 delete action 触发而非直接调内部方法；biz 调用经 ORM session 包裹镜像生产请求级 session）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 大屏删除级联 + 面板删除停用告警规则

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavScreenBizModel.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavPanelBizModel.java`

- Item Types: `Fix`

- [x] 大屏删除级联：ScreenWidget 按 D1 裁定处理；ScreenSnapshot 按 D1 裁定处理
- [x] 面板删除联动：按 panelId 停用关联 AlertRule（`status=DISABLED`）并即时 unregister
- [x] 新增行为全部有对应单元测试

Exit Criteria:

- [x] 测试证明：删除大屏后其 widget 不可再读（按 D1 裁定的可观察结果断言）— `TestNopDatavDeleteLifecycle.testDeleteScreenCascadesWidgetsAndSnapshots`（widget 行数 0）
- [x] 测试证明：删除大屏后 ScreenSnapshot 终态符合 D1 裁定（快照处置半边裁定有显式断言，非口头落地）— 同测试（snapshot 行数 0，级联删除终态）
- [x] 测试证明：删除面板后关联 AlertRule `status=DISABLED` 且调度器注册表不含该规则 job — `testDeletePanelDisablesAndUnregistersAlertRules`
- [x] **无静默跳过**：级联失败显式抛错（Screen/Panel 级联实现无 try/catch）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 审计 pattern + 端到端验证

Status: completed
Targets: `nop-datav/nop-datav-app/src/main/resources/application.yaml`、`nop-datav/nop-datav-service/src/test/`

- Item Types: `Fix | Proof`

- [x] `audit-mutation-patterns` 追加 `NopDatavDashboardShare__*`（一行配置）
- [x] 端到端测试：创建看板（含多面板）→ 发布 → 建分享 → 建告警规则 + 报告任务（ENABLED）→ 删除看板 → 断言分享访问拒绝、面板查询报 panel not found、规则/任务 DISABLED 且 job 已注销

Exit Criteria:

- [x] `application.yaml` 中 pattern 含 `NopDatavDashboardShare__*`（repo-observable）
- [x] 测试证明：分享管理 mutation 真实产生审计记录（复用 `TestNopDatavAuditLog` 的 `@NopTestProperty` 测试模式，防 pattern 拼写/顺序错误静默失效）— `TestNopDatavAuditLog.testAuditLog_shareManagementMutation`（`NopDatavDashboardShare__revokeShare` → NopAuthOpLog 记录断言）
- [x] **端到端验证**：上述完整链路测试存在且通过（从 biz 层入口到 DAO 终态与调度器注册表终态）— `TestNopDatavDeleteLifecycle.testE2eFullDeleteLifecycleChain`（面板查询经 biz 层 `getPanelData` 显式报 UnknownEntityException(NopDatavPanel, panelId)——requireEntity 对缺失实体的显式 not-found 路径）
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 全绿（443 tests, 0 failures, 0 errors）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 三类 live gap（分享残留访问 / 规则任务 dangling / 删除不即时注销）均有测试证明被消除
- [x] 5 项 Decision 裁定落地且 owner docs 同步（无 Proposed/待定残留）
- [x] 必要 focused verification 已完成（新增测试显式列于各 Phase）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：级联链路从 biz 删除入口到各子表/关联表终态与调度器注册表运行时连通（非仅类型/方法存在）
- [x] `./mvnw compile -pl nop-datav/nop-datav-service -am` 通过
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过（443 tests, 0 failures, 0 errors）
- [x] checkstyle / 代码规范检查通过（导入分组 io.nop.* → jakarta/第三方 → java.*，4 空格缩进；`scan-hollow-implementations.mjs --module nop-datav --severity high` 0 findings）

## Deferred But Adjudicated

### nop-datav 全实体引入逻辑删除（useLogicalDelete）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 平台未强制逻辑删除；nop-datav 现状即物理删除，级联沿用现状语义不引入新契约；是否迁移到逻辑删除是独立的 ORM 契约级变更，与删除完整性正交。
- Successor Required: `no`
- Successor Path: —

### 分享访问速率限制

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: token 为 SecureRandom 不可枚举，密码可过期；暴力探测属运营期加固，与删除生命周期正确性正交。
- Successor Required: `no`
- Successor Path: —

### DatasetRef 反向引用校验

- Classification: `watch-only residual`
- Why Not Blocking Closure: `getPanelData` 对缺失 datasetRef 已显式抛错（fail-fast），无静默错误结果；删除时预防性校验属体验优化。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- 前端删除确认 / 级联影响预览 UI（flux 对接后）。

## Closure

Status Note: 全部 4 个 Phase 完成——三类 live gap（分享残留访问 / 规则任务 dangling / 删除不即时注销）经 10 个新增测试证明消除；5 项裁定（D1-D5）写入两份 owner docs；Gap #3（delete 路径不触发注销）以 doDeleteEntity 覆写修复并回归测试；审计 pattern 补齐 + 审计落库证明。剩余项均为显式裁定过的 deferred（逻辑删除迁移 / 速率限制 / DatasetRef 反向校验）与前端 follow-up，无 in-scope 遗留。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session，task `ses_fff414ecaffelVFQhMgau3m5YA`）
- Evidence:
  - 11 项核查全部 PASS，verdict **CLOSABLE**（对照 live code/tests/config，非 plan 自述）
  - Phase 1 裁定章节：`permission-sharing-design.md:411` 起（D1/D2/D4/D5）+ `schedule-report-design.md:532` 起（§26 D3 + Gap #3），无待定措辞
  - 级联接线（Anti-Hollow）：`NopDatavDashboardBizModel.java:92-99`（4 参 `doDeleteEntity` @Override、super 后级联、无吞错）、`NopDatavAlertRuleBizModel.java:97-106` / `NopDatavReportTaskBizModel.java:87-96`（Gap #3）、`NopDatavScreenBizModel.java:84-93`、`NopDatavPanelBizModel.java:63-70`；调用链 `CrudBizModel.delete(id)→doDelete→4 参 doDeleteEntity`（`CrudBizModel.java:1043/1056/1066/1196`）虚分派到覆写，L1211 确认 delete 路径仅调 2 参 deprecated afterEntityChange（Gap #3 根因）
  - 分享防御：`NopDatavDashboardShareBizModel.java:163/237-245` + `NopDatavErrors.java:227-228`（`nop.err.datav.share-dashboard-not-found`）
  - 测试：`TestNopDatavDeleteLifecycle` 9/9 + `TestNopDatavAuditLog.testAuditLog_shareManagementMutation`；audit 期间 live 复跑全模块 **443 tests, 0 failures, 0 errors, BUILD SUCCESS**
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（6 warning 为无关历史 plan 338 pre-existing）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 0 findings（退出码 0）
  - `node ai-dev/tools/check-plan-checklist.mjs <this-file> --strict` 退出码 0（见当日 log）
  - Deferred 项分类检查：3 项均附 Why Not Blocking Closure；Non-Blocking Follow-ups 仅前端 UI 项，无 live defect 降级

Follow-up:

- 前端删除确认 / 级联影响预览 UI（flux 对接后）——plan Deferred/Non-Blocking 记录在案，非 plan-owned work
