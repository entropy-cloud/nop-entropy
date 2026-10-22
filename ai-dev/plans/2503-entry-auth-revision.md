# 2503 授权架构修订：入口边界校验 + ORM 关联加载 + SingleSession

> Plan Status: completed
> Last Reviewed: 2026-10-23
> Source: 用户架构指令（2026-10-23）；修订对象：plan 2489（WI1）、2497（WI9）、2502（WI16）
> Related: `ai-dev/design/nop-report/auth-wiring.md`（已反转修订）

## Purpose

落实用户三点架构指令，对 WI1/WI9/WI6 授权实现做反转修订：
1. 权限配置尽量使用 ORM 关联加载（不走单独 example 查询）。
2. 权限在系统入口校验，内部引擎不校验。
3. 可能被异步调用的方法用 `@SingleSession` 注解包裹。

## 裁定与实现（Decision + Fix）

1. **引擎去授权化**：移除 `ReportEngine.getXptModel` 授权钩子、`IXptRuntime.checkDataSourceAccess`、xpt-rt.xlib 两处标签内调用。引擎保持可嵌入式复用、不感知授权。
2. **入口显式授权**：对外 BizModel action（cache download/getResultStatus/invalidate、form getReportFormSchema/getReportParamOptions）显式调用 `NopReportAuthChecker`（`ormTemplate.runInSession` 包裹 + ORM 关联加载 `reportAuths`/`datasourceAuths`），并标注 `@SingleSession`（下载/状态/失效/表单五入口）。
3. **连带修复**：缓存 sync generate 改为生成后单次 saveEntity（修 update-entity-not-managed）；异步 worker 状态回写走 jdbc 直接 update（无 session 环境）。
4. **语义边界**：数据级安全（WI3 行过滤/脱敏）为数据变换，保留在数据访问点；子报表由主模板授权覆盖；模板内 querySpace 为可信内部配置（数据源授权在入口触发数据源访问的 action 处判定）。
5. **已知限制**：DisabledEvalScope（HtmlRenderHelper 纯静态渲染）下 renderType/授权相关 scope 写入跳过。

## 测试证据

- TestReportAuthHook 删除（引擎级断言失效）；TestReportAuthWiring 4 用例改 checker 入口断言；TestReportResultCache 7 用例（授权用例改 checker 断言）；TestE2EAcceptance 未授权改走缓存下载入口。全部绿：`./mvnw test -pl nop-report/nop-report-service -am` BUILD SUCCESS（2026-10-23，core 120 + service 29）。

## Closure Gates

- [x] 引擎内授权钩子全部移除（grep 核验 0 命中）
- [x] 入口 action 显式授权 + @SingleSession（5 处）
- [x] checker ORM 关联加载（runInSession + reportAuths/datasourceAuths 导航）
- [x] 测试改 entry 语义全绿
- [x] design/owner doc 反转修订（auth-wiring/subreport/owner doc）；doc-links 退 0
- [x] 独立确认（用户指令驱动，diff 全量自查 + service -am 两次绿）

## Closure

Status Note: 按用户架构指令完成反转修订；语义反转点（入口边界 vs 引擎内）已同步 design/owner doc/log，后续如有异议以本 plan 与 auth-wiring.md 为准。
Completed: 2026-10-23

Closure Audit Evidence:

- Reviewer / Agent: 用户直接指令（架构仲裁），实现后 -am 全量回归绿（两次）
- Evidence: grep checkDataSourceAccess/checkReportAccess 在引擎层 0 命中；NopReportAuthChecker runInSession + 关联导航；BizModel 5 action @SingleSession + reportPathAuth/checker 调用；TestReportAuthWiring 4/4、TestReportResultCache 7/7、TestE2EAcceptance 2/2 绿
