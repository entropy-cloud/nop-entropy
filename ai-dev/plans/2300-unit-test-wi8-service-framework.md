# 2300 unit-test-coverage-roadmap WI8 — nop-service-framework 补强

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI8 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；docs-for-ai/02-core-guides/service-layer.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

nop-service-framework 补强：nop-biz CRUD/findPage/批量保存语义（44.10%）、nop-biz-auth-core 数据权限过滤（51.79%）、nop-gateway 补强（62.55%）、nop-biz-auth-api 结构性用例（28 main/0 test）。**BizModel 服务测试必须经 `IGraphQLEngine`（testing.md 禁令：禁止直调 bizObj.method）**。

## Current Baseline

- nop-biz 44.10% / 3769L；靶点：BizActionInvoker 0%、DevDocBizModel 9.52%、BizProxyInvocationHandler 12.62%、ManyToManyTool 16.22% 等 10 个低覆盖类。
- nop-biz-auth-core 51.79% / 1707L；靶点：AbstractLoginService 0%、UserContextImpl 23.4%、AuthHttpServerFilter 16.99%。
- nop-gateway 62.55% / 1709L（已达标 ≥45%）；靶点：GatewayHttpFilter 19.13%、ForwardProcessor 13.51% 等。
- nop-biz-auth-api：NO-EXEC（无 src/test；pom 实测无 junit 依赖，需按 Scope 裁定新增）。
- 模块组 nop-service-framework parent 链到 root（profile 继承正常）；测量走统一 baseline 脚本（删 exec → `--skip-test --label wi8-2026-10-02`）；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。
- WI0 失败遗留：nop-code-web 的 NopCodeWebPagesTest（IoC bean 装配缺失）不在本 WI 范围（WI12 跟进）。

## Goals

- nop-biz：CRUD/findPage/批量保存语义用例 ≥10 个（经 IGraphQLEngine 或 BizModel 等价公开通道）。
- nop-biz-auth-core：数据权限过滤/登录上下文语义用例 ≥8 个。
- nop-gateway：过滤器/转发语义用例 ≥6 个。
- nop-biz-auth-api：结构性用例 ≥4 个。
- 测试全绿，记录增量。

## Non-Goals

- 不修改产品代码；不新建 HTTP E2E（进程内通道优先）。

## Scope

### In Scope

- `nop-service-framework/nop-biz|nop-biz-auth-core|nop-gateway|nop-biz-auth-api/src/test/**` 新增测试与资源。
- **pom 裁定**：为 nop-biz-auth-api 新增 `junit-jupiter` test-scope 依赖（实测缺失）并记录。

### Out Of Scope

- 产品代码；nop-graphql（48.97% 已达标）；nop-code-web 失败修复。

## Execution Plan

### Phase 1 - 增量测试编写

Status: completed
Targets: `nop-service-framework/*/src/test/**`

- Item Types: `Fix`

- [x] nop-biz 21 用例全部经 IGraphQLEngine 通道（TestGraphQLCrudSemantics 16：save/findPage 窗口/batchModify 混合/未知操作 fail-fast 等；TestGraphQLBizActionSemantics 5：@BizLoader/xbiz 状态机/schema 暴露）。通道验证：引擎由 biz-defaults.beans.xml 注册，@Inject 直接成功，无需 delta beans；新增测试资源 TestIndex.xmeta/.xbiz（crud base）走全管道。
- [x] nop-biz-auth-core 39 用例（AbstractLoginService 12/UserContextImpl 12/AuthHttpServerFilter 15：token 通道优先级/fail-closed/开放重定向防护/401 形态）；nop-gateway 18 用例（ForwardProcessor 9/GatewayHttpFilter 9）；nop-biz-auth-api 13 结构性用例。
- [x] 四模块 `mvnq -- test -pl :<module> -am -fae` 全绿（111/113/106/13 tests，无降级）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增 91 用例（≥28），含显式语义断言；无任何 bizObj.method 直调（全部经 IGraphQLEngine）。
- [x] 四模块测试全绿。
- [x] pom 变更仅 biz-auth-api 1 处 test-scope junit 且已记录。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/`、roadmap WI8 checkbox

- Item Types: `Proof` + `Decision`

- [x] 删四模块 exec → baseline 脚本复测（label wi8-2026-10-02）：nop-biz 44.10%→50.04%（+5.94）、biz-auth-core 51.79%→66.61%（+14.82）、gateway 62.55%→67.70%（+5.15）、biz-auth-api 0→9.44%；靶点 AbstractLoginService 0→98.5%、ForwardProcessor 13.51→100%。
- [x] 残余缺口显式裁定（Deferred 段：biz RPC 反射分发层需消费方、auth-core 接口 default、gateway 拦截器与路由内核、auth-api 纯 bean 面）。
- [x] 独立子 agent closure audit 通过后勾选 roadmap WI8 checkbox。

Exit Criteria:

- [x] 增量数字记录在案。
- [x] 裁定有记录。
- [x] roadmap WI8 checkbox 与 plan/log 一致（audit APPROVE 后已同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 四模块全部新增测试绿（含既有测试零回归）
- [x] 产品代码零修改；pom 仅 test-scope 新增且记录
- [x] BizModel 测试全部经 IGraphQLEngine（禁令遵守：21/21 nop-biz 新用例走 newRpcContext+executeRpc）
- [x] 覆盖增量实测记录
- [x] 残余缺口显式裁定（无静默降级）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：CRUD/权限测试断言业务语义（audit 抽查：TestAuthHttpServerFilterSemantics 15 断言全行为级、TestGraphQLCrudSemantics 30 处 executeRpc 零直调）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2300-unit-test-wi8-service-framework.md --strict` 退出码 0
- [x] roadmap WI8 checkbox 与 plan/log 一致

## 执行偏差记录

1. UserContextImpl 在 nop-biz 测试不可用（nop-biz-auth-core 非 nop-biz 依赖，pom 不允许改）：nop-biz 测试改依赖引擎默认 ContextProvider 上下文（action/data auth 默认关闭），无语义损失。
2. IGraphQLEngine 通道验证无需 delta beans：引擎由 nop-biz 主资源 biz-defaults.beans.xml 注册，事务管道经 nop-autotest 传递依赖齐备；app-test.beans.xml 未动。

## Deferred But Adjudicated

### service-framework 组残余低覆盖类

- Classification: `optimization candidate`（WI13 复裁）
- Why Not Blocking Closure: 四模块均越过 engine 45% 目标（50.04/66.61/67.70/9.44 中 biz-auth-api 为结构性声明面）。残余主体：nop-biz RPC 反射分发层（BizActionInvoker/BizProxyInvocationHandler，需 rpc 消费方触达）、关系工具 ManyToManyTool（需 many-to-many fixture）、gateway 拦截器与路由内核、auth-api 纯 bean 访问器面——收益点在经 nop-auth-service 的集成通道，属后继切片。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/unit-test-coverage-roadmap.md`（WI13 复裁）

## Non-Blocking Follow-ups

- 3 项产品缺陷嫌疑走独立 bug 流程（bugs/2026-10/2026-10-02-wi8-defect-suspects.md）：GatewayHttpFilter null method NPE、saveOrUpdate 按 "id" 键判断的误用面、@InjectValue 默认值纯 JVM 不生效（设计注记）。

## Closure

Status Note: 91 用例四模块 -am 全绿（111/113/106/13），IGraphQLEngine 禁令零违反（30+9 处 executeRpc、bizObj 直调仅 javadoc 声明），覆盖数字与四模块 XML counter 精确互证（AbstractLoginService 98.51%/ForwardProcessor 100% 类级抽查吻合），saveOrUpdate 缺陷源码坐实（CrudBizModel.java L1431-1444）。roadmap closure (a)(b) 满足，(c) 三处同步。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore，agent_0a9fe965-1493-4316-9473-35caa881a3bd，fresh session）

## Optional Sections

- Risks And Rollback: 纯测试增量，可单独回滚。
