# 2489 WI1 报表/数据源权限接线（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI1；`ai-dev/design/nop-report/auth-wiring.md`
> Related: 2488-wi0-report-capability-contract-baseline.md（契约草案已落地）

## Purpose

让「配置了报表/数据源权限」真正生效：渲染入口与数据集取数链路强制授权检查，未授权 `nop.err.report.auth.*` 快速失败。

## Current Baseline

- `ReportEngine.getRenderer(path, renderType)`（接口 default）→ `getRendererForXptModel(model, renderType)`：全链路无授权检查；`getXptModel(path)` 仅校验文件类型。
- `xpt-rt.xlib` 四取数标签（`JdbcDataSet`/`UseJdbcDataSet`/`UseOrmDataSet`/`UseQueryDataSet`）直接 `JdbcDataSetHelper.getDataSource(querySpace)` / `nopOrmTemplate`，无数据源权限检查。
- 权限实体齐备：`NopReportDefinitionAuth`（`rptId`+`roleId`）、`NopReportDatasourceAuth`（`dsId`+`roleId`）；仅 CRUD BizModel（nop-report-service）。
- nop-report-core 仅依赖 nop-ooxml-xlsx，不能依赖 dao/auth——SPI 需定义在 core，实现落 nop-report-service。
- 平台既有模式：`BeanContainer.tryGetBean(beanName)` 可选 bean 查找（bean 未注册/容器未初始化均返回 null）；当前用户角色 `IUserContext.get().getRoles()`（nop-api-core，非 Web 线程返回 null）。
- 测试基座：nop-report-core 测试（BaseTestCase，无 dao 依赖）不能跑 xpt-rt 取数标签（标签脚本 import io.nop.dao.jdbc.*，core classpath 无该类）；全仓取数标签 e2e 参考 `nop-report-demo/src/test/.../TestReportDataSet.java`（JunitAutoTestCase + h2 全套 IoC）。nop-report-service 当前无 src/test，AutoTest 基座需从零搭建。
- nop-report-service 的 `_service.beans.xml` 是生成文件（禁手改）；`app-service.beans.xml` 为手写入口（仅 import _service）。
- `rptText` 字段（NopReportDefinition，comment「文件路径、SQL语句等」）当前无渲染链路消费。

## Goals

- core 新增 SPI `IReportAuthChecker`（`checkReportAccess(reportPath)` / `checkDataSourceAccess(querySpace)`）+ 错误码族 `nop.err.report.auth.*`（消息英文，遵循 AGENTS 两档惯例）；**报表级检查挂 `ReportEngine.getXptModel(path)`**——它是 `getRenderer` 与「先 getXptModel 再 getRendererForXptModel」两种现实调用模式共有的唯一路径入口，单一挂点即闭合绕过口；`getRendererForXptModel(ExcelWorkbook,…)` 定位为程序化内部 API（模型已在内存、无平台报表身份，不单独设检查，落 owner doc 口径）；xpt-rt 取数标签前置数据源级检查。
- service 新增 `NopReportAuthChecker` 实现（查询 auth 实体 + `IUserContext.get().getRoles()`），bean `nopReportAuthChecker` 注册于手写的 `report-auth.beans.xml`（app-service.beans.xml 追加 import）；引擎经 `tryGetBean` 可选消费。
- 授权语义（本计划裁定，收口回 owner doc）：
  - checker bean 未注册（嵌入式库用）→ 放行；
  - 报表定义/数据源无 auth 记录 → 不限制；有记录 → 用户角色须命中任一；
  - checker 已注册但 `IUserContext.get()` 为 null（调度/嵌入式线程）且资源有 auth 记录 → **fail-closed 拒绝**；调度等系统调用方必须显式建立系统用户上下文（与 auth-wiring.md「显式豁免」一致），不做隐式放行；
  - 管理角色豁免：配置项 `nop.report.auth.exempt-roles`（逗号分隔角色，默认空=无隐式豁免）；
  - 路径→定义以 `rptText` = 模板 VFS 全路径精确匹配（rptText 从此为约定字段；其「文件路径 or SQL」语义歧义的处置一并落 owner doc）。

## Non-Goals

- 行/列级数据过滤与脱敏（WI3）；CRUD BizModel 通用鉴权（平台已有）；缓存越权联动（WI6 落地时按 auth-wiring.md 缓存协同）。
- 不改 nop-auth 权限判定语义；不建第二套角色模型。

## Scope

### In Scope

- `nop-report/nop-report-core`：SPI、错误码、`ReportEngine` 挂接、`IXptRuntime` 新增 `checkDataSourceAccess` default 方法、`xpt-rt.xlib` 标签挂接、core 测试
- `nop-report/nop-report-service`：checker 实现 + beans 注册 + service 测试
- `docs-for-ai/03-modules/nop-report.md`：权限「预留未接线」勘误为已接线 + rptText 约定 + 授权语义
- roadmap WI1 勾选、`ai-dev/logs` 记录

### Out Of Scope

- `nop-report-web` 前端页面、`NopReportDatasource` 管理页联动
- PDF/docx 渲染路径（同一 `getRenderer` 入口，自动覆盖）

## Execution Plan

### Phase 1 - core SPI 与渲染/取数挂接

Status: completed
Targets: `nop-report/nop-report-core`

- Item Types: `Feature`

- [x] `IReportAuthChecker` SPI + `ReportAuthErrors`（`nop.err.report.auth.report-denied` / `nop.err.report.auth.datasource-denied`，带 path/dsName/roles 参数与配置指引消息）
- [x] `ReportEngine.getXptModel(path)`：checker 存在时先 `checkReportAccess(path)`（单一挂点覆盖 getRenderer 与 demo 两类调用模式）
- [x] `IXptRuntime.checkDataSourceAccess(String querySpace)` default 方法（转发 checker，bean 缺省放行）+ `xpt-rt.xlib` `JdbcDataSet`/`UseJdbcDataSet` source 脚本首行调用；`UseOrmDataSet`/`UseQueryDataSet` 走默认数据源，由报表级检查覆盖（文档化该裁定）
- [x] core 单测：fake checker 覆盖——无 bean 放行、有 bean 放行（计数器证明 getXptModel 运行时调用）、deny 报指定错误码与参数、`checkDataSourceAccess` default 方法转发语义；xpt-rt 标签 e2e（真实标签脚本→checker）不在 core（classpath 无 dao），落 Phase 2 service 测试基座

Exit Criteria:

- [x] 新增 SPI 方法在未实现（无 bean）时放行且在 deny 时快速抛错——无静默跳过
- [x] 接线验证：core 测试证明 `getXptModel` 运行时确实调用 checker（fake 计数器）
- [x] `./mvnw test -pl nop-report/nop-report-core` 绿
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - service 实现、取数标签 e2e 与文档收口

Status: completed
Targets: `nop-report/nop-report-service`、`docs-for-ai/03-modules/nop-report.md`、`ai-dev/design/nop-report/auth-wiring.md`、roadmap

- Item Types: `Feature + Decision`

- [x] `NopReportAuthChecker`（orm 查询 auth 实体）+ 新建手写 `report-auth.beans.xml` 注册 `nopReportAuthChecker`，在 `app-service.beans.xml` 追加 import；禁碰 `_service.beans.xml`
- [x] 从零搭 service AutoTest 测试基座（JunitAutoTestCase + h2 + `@NopTestConfig`；测试内 `IUserContext.set(...)` 构造用户上下文）
- [x] service 测试：有记录命中/不命中角色、无记录放行、无用户上下文 fail-closed、exempt-roles 豁免、**xpt-rt 取数标签 e2e**（模板 beforeExpand 用 `UseJdbcDataSet` + querySpace → 标签脚本调用 `checkDataSourceAccess` → checker deny/allow 两态）
- [x] owner doc 勘误（具体对照点：`03-modules/nop-report.md` 功能概览与实体表两处「预留未接线」改为已接线及语义、rptText 约定段、授权语义四条、`getRendererForXptModel` 内部 API 口径）；auth-wiring.md 设计文档同步检查点描述（getXptModel 单一挂点）与两项新裁定；roadmap WI1 勾选；logs 收口

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿（含 core）
- [x] owner doc 对照点全部落地（两处「预留未接线」消除、rptText/授权语义/内部 API 口径段存在）；auth-wiring.md 与实现一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 渲染入口 + 取数标签授权链路端到端连通（core 测试从 getXptModel 入口到 fake checker；service e2e 从 xpt-rt 取数标签脚本到 checker）
- [x] 未授权不可渲染/不可取数、授权后正常，均有测试
- [x] 无静默跳过（deny 必抛 `nop.err.report.auth.*`）
- [x] owner doc 已同步（含授权语义四条裁定 + 内部 API 口径）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- checker 查询结果缓存策略（请求级 vs 全局 TTL）在 WI6 缓存落地时统一裁定

## Closure

Status Note: 渲染入口与取数标签授权链路已落地并经端到端测试验证；core/service 两 Phase Exit Criteria 与全部 Closure Gates 经独立子 agent closure audit 逐条 PASS（2026-10-22）。
Completed: 2026-10-22

Closure Audit Evidence:

- Reviewer / Agent: 独立 general-purpose 子 agent closure auditor（新会话，agent_419c8c88-b4a8-4ac9-afa0-bb2754e3ec1f，2026-10-22）
- Evidence:
  - core：`IReportAuthChecker.java`（BEAN_NAME=nopReportAuthChecker，`BeanContainer.tryGetBean` 可选查找）、`ReportAuthErrors.java`（`nop.err.report.auth.report-denied`/`datasource-denied`，英文消息带配置指引与 reportPath/dsName/roles 参数）、`ReportEngine.getXptModel` 挂 checkReportAccess、`IXptRuntime.checkDataSourceAccess` default（checker null 放行）、`xpt-rt.xlib` 的 `JdbcDataSet`/`UseJdbcDataSet` source 首行 `xptRt.checkDataSourceAccess(querySpace)` 均核对通过
  - core 测试 `TestReportAuthHook`：5 用例（无 bean 放行 / fake 计数器断言 `reportChecks` 证明 getXptModel 运行时调用 / deny 报指定错误码+path 参数 / checkDataSourceAccess 转发与 deny）；surefire 5/5 全绿
  - service：`NopReportAuthChecker`（无记录放行、有记录命中角色、无用户上下文 roles=空集→fail-closed、exempt-roles 并入允许集、rptText 经 `findFirstByExample` 精确匹配）；手写 `report-auth.beans.xml` 注册 `nopReportAuthChecker`，`app-service.beans.xml` 追加 import；`_service.beans.xml` 无改动（git status 核对）
  - service 测试 `TestReportAuthWiring`：5 用例真端到端——`getRenderer(TEMPLATE,"html")`（IReportEngine default → getXptModel → checker）报表级 deny/allow；`test-auth-jdbc-denied.xpt.xlsx` sharedStrings 含 `<xpt-rt:UseJdbcDataSet querySpace="securedDs">`（beforeExpand），渲染时标签脚本→checkDataSourceAccess→JDBC 查找前 deny；无上下文 fail-closed；exempt-roles 豁免；无 auth 记录放行。auditor 独立重跑 `./mvnw test -pl nop-report/nop-report-service -Dtest=TestReportAuthWiring`：Tests run 5, Failures 0, Errors 0
  - 文档：owner doc `03-modules/nop-report.md` 两处「预留未接线」已勘误（能力概览缺失清单移除 + 实体表改「已接线」），rptText 约定、授权语义四条、`getRendererForXptModel` 内部 API 口径、嵌入式无 bean 放行均在；`auth-wiring.md` 状态 active、getXptModel 单一挂点/rptText 匹配/UseOrm·UseQuery 报表级覆盖裁定同步，与实现无矛盾；roadmap WI1 已勾选；`ai-dev/logs/2026/10-22.md` WI1 条目含裁定、坑账、doc-sync
  - `node ai-dev/tools/check-doc-links.mjs --strict`：exit 0（0 errors, 0 warnings）
  - 结论：全部 Exit Criteria 与 Closure Gates PASS，无 Blocker/Major，可标 completed
