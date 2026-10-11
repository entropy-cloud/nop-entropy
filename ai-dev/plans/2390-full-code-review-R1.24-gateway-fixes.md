# 2390 full-code-review R1.24 — nop-gateway 双修复（P1-check-gw-49/86）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Completed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.24；findings `P1-check-gw-49` + `P1-check-gw-86`（check/gateway-bizauth.md :49/:86，3.9 deep-audit 标定维持 still-live），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.9-gateway-network-deep-audit.md` §B 面 #2/#4
> Related: gw-86 与 auth-core 登录链同批互引（handleUserContext 的 cookie 机制为修复基准形态）；R1.25（HTTP 错误路径批，同网关面不同模块）
> Owner Doc: `docs-for-ai/03-modules/nop-network.md`（3.9 owner doc——网关面语义注记）

## Purpose

收口网关两条 P1：①路由匹配前对所有请求全量严格 JSON 解析——非 JSON body（multipart/表单）直接异常、且异步段抛出不受 sync catch 保护；无路由命中时 body 已被消费后放行下游（审查 P1 勘正：GET 空 body 现状即 data=null 不抛——DefaultJsonTool.parseFromText 空串守卫在先，3.9 audit 该表述失准，判别见证不得用空 body）。②OAuth code+redirect 分支 sendRedirect 后不下发 auth cookie、不初始化用户上下文——SSO 服务端会话已建但浏览器零凭证，回调登录循环。

## Current Baseline

- **P1-check-gw-49**：`GatewayHttpFilter.filterAsync:120-156`——routes 非空即 `resumeRequest`+`getTextAsync`+`buildRequest:305-312`（`JSON.parse(requestText)` strictMode）先于 `handler.handle` 的路由匹配（GatewayHandler.handle:90-102 首行 `matchPath`）；无路由命中 handle 返回 null → next 放行但 body 已消费；非 JSON content-type/GET 空 body 解析抛异常且发生在 thenCompose 异步段——:149 catch 只护同步段。缓解：`nop.gateway.http-filter.enabled` opt-in 条件装配。路由匹配仅需 path（matchPath(requestPath)），为"解析后置到路由命中"提供结构条件。
- **P1-check-gw-86**：`AuthHttpServerFilter.processOAuthCode:194-215`——redirect 分支 `isAllowedRedirectUri` 校验后 `sendRedirect` 即 `next.get()`：无 addCookie（cookie 下发仅在 handleUserContext:352-361）、无 initUserContext；:197-198 解析的 accessToken/authToken 该分支未消费。SSO 回调带 redirect_uri 即登录循环。
- **修复基准形态**：handleUserContext:332-370——initUserContext + needRefresh 判定 + cookie 不一致才 addCookie（`config.getAuthCookie() != null` 守卫）+ next 链；`addCookie(name,value,context):376`、`authCookieName():107`、`initUserContext(userContext,context):544` 均为 protected 可复用。
- **测试基建**：nop-gateway 107 tests（含 GatewayHttpFilter 相关）、nop-biz-auth-core 115 tests（3.9 回归基线）；两模块测试容器可用（R1.20 起验证过 auth-core 测试依赖面）。

## Goals

- 路由预匹配后置：无 path 级路由命中时不读 body 直接放行下游（body 零消费）；content-type 分流——仅 JSON content-type（或缺省）做严格 parse（声明 JSON 的 body 保持 fail-fast），非 JSON content-type/空 body 不 parse；异步段异常统一接 exceptionally 走 writeErrorResponse（与 sync catch 对齐）。
- OAuth redirect 分支：sendRedirect 前补 initUserContext + auth cookie 下发（守卫与 handleUserContext 同款 `config.getAuthCookie() != null`）——浏览器持凭证 redirect，登录循环消除。
- 判别测试双腿 stash 实证；nop-gateway/nop-biz-auth-core 测试全绿。

## Non-Goals

- AiAuth/AiRateLimit 拦截器（3.9 已标 AF）；`nop.gateway.http-filter.enabled` 装配语义变更；OAuth 非 redirect 分支；auth-core 登录链其他面（3.10 已收口）；P2-3.9 台账行。

## Scope

### In Scope

- `GatewayHandler`：新增 path 级路由预匹配方法（public，委托 router.matchPath）
- `GatewayHttpFilter`：预匹配守卫 + buildRequest content-type 分流 + thenCompose 链 exceptionally 兜底
- `AuthHttpServerFilter.processOAuthCode`：redirect 分支补 initUserContext + addCookie
- 测试：GatewayHttpFilter 预匹配/分流判别；AuthHttpServerFilter OAuth redirect cookie 判别
- owner doc：nop-network.md 网关面语义注记

### Out Of Scope

- Non-Goals 全部；nop-biz-auth-core 其他 filter；路由匹配语义重构

## Execution Plan

### Phase 1 - 双修 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] GatewayHandler 增 `hasRouteFor(String path)`（matchPath != null）；GatewayHttpFilter 在 routes 非空检查后增预匹配守卫——无命中直接 next.get()（body 零消费）
- [x] GatewayHttpFilter.buildRequest 分流：空 text→data=null（现状行为，防御性保留）；content-type 经 `getRequestStringHeader(HttpApiConstants.HEADER_CONTENT_TYPE)` lowercase 后 contains("json")（或缺省）→ JSON.parse（严格 fail-fast 保持）；其他 content-type（multipart 含 boundary 形态天然不命中）→ data=null（不 parse）；类 javadoc 记录分流契约
- [x] filterAsync 的 thenCompose 返回链增 `.exceptionally`——**先查 `context.isResponseSent()`（审查 P2）**：已提交（streaming 中途错误穿透）只 LOG.error 不二次写出（VertxHttpServerContext.sendResponse 无守卫，二次写抛 IllegalStateException）；未提交才 ErrorMessageManager + writeErrorResponse——与 :149 sync catch 对齐且不引入流式错误回归
- [x] AuthHttpServerFilter.processOAuthCode redirect 分支：sendRedirect 前补 `initUserContext(userContext, routeContext)` + auth cookie 下发（`config.getAuthCookie() != null` 时 `addCookie(authCookieName(), accessToken, routeContext)`——accessToken 为新会话凭证；handleUserContext 的「不一致才加」守卫在 OAuth 新登录场景恒不满足-等价，直接下发）
- [x] 测试（**判别见证锚定非空非 JSON body——审查 P1：空 body stash 后仍 PASS 不得作见证**；复用 TestGatewayHttpFilterSemantics 的 FakeHttpContext/TestAuthHttpServerFilterSemantics 基建，禁沿用不判别的 testFilterAsyncUnmatchedPathPassesThrough 形态）：①GatewayHttpFilter——非空非 JSON content-type body（multipart 形态）不再 500；无路由命中时 body 零消费（fake 记录 requestBody 读取）；JSON content-type 非法 body 仍 fail-fast；streaming 中途失败不二次写响应（审查 P2，isResponseSent 守卫判别）；异步段异常返回 5xx。②AuthHttpServerFilter——OAuth code+redirect 场景响应含 auth cookie 且 sendRedirect 到 allowed uri；无 redirect 场景走原 handleUserContext 行为不变
- [x] **判别验证（双腿 stash）**：①stash GatewayHttpFilter+GatewayHandler 改动→预匹配/分流测试 FAIL（500 或 body 被消费）；②stash AuthHttpServerFilter 改动→redirect cookie 测试 FAIL（无 cookie）——各腿恢复后绿
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 双修全落地（预匹配+分流/redirect cookie）
- [x] 判别测试双腿绿 + 既有测试零回归
- [x] nop-gateway/nop-biz-auth-core 测试全绿
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-network.md`

- Item Types: `Fix`

- [x] nop-network.md：网关面语义注记（路由预匹配后置+content-type 分流；OAuth redirect 凭证下发；**redirect 后下游对已提交响应的写入为无害噪音——现状既有行为，短路 next 替代方案未评估留 follow-up**）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.24 翻 done；index P1-check-gw-49/86 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-check-gw-49/86）
- [x] 行为/契约结果已达成（判别双腿 + 零回归）
- [x] 必要 focused verification 已完成（nop-gateway/nop-biz-auth-core 全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-service-framework/nop-gateway,nop-service-framework/nop-biz-auth-core -am` 全绿（gateway 111/auth-core 117 + 上游链 BUILD SUCCESS）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 非 JSON content-type 的 body 透传语义（当前 data=null——若未来路由需要透传原始 body 到后端，需 body 转发通道设计；multipart 上传路由在网关侧不透传 body 内容）
- OAuth redirect 后短路 next（下游对已提交响应的写入为无害噪音——现状既有；短路属行为变更需独立评估）
- matchRoute 的 method/header 级条件与 path 级预匹配的粒度差（预匹配过宽时仍会读 body——可接受：有路由族命中即网关语义内）

## Closure

Status Note: 双修在 live 代码逐行落地：预匹配守卫位于任何 body 消费之前（stash 见证 bodyReadCount 0↔1）、分流契约与 exceptionally isResponseSent 守卫齐备、OAuth redirect 凭证先于 sendRedirect 交付。判别双腿精确（gw-49 恰 4 新用例 FAIL/ERROR 旧 10 不受扰；gw-86 恰 redirect 用例 1 FAIL 无 redirect 用例不受扰）。gateway 111（107+4）/auth-core 117（115+2）+ 上游 -am 链全绿。附带修复 testBuildRequestParsesJsonAndCarriesHttpMeta 预存顺序依赖（HEAD 单跑即挂——依赖同类泄漏的 CoreInitialization 状态）。审计 Minor 两项（孤立 javadoc/IContext 效果断言）已处理前者，后者随后续批次。

Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）agent_5a9850f5-b827-48ba-8290-2343550371e8
- Evidence:
  - P1-check-gw-49 PASS：GatewayHandler:96-98 hasRouteFor（与 handle :102 同 router 同调用）；GatewayHttpFilter:140-142 预匹配守卫先于 resumeRequest/getRequestBody；:347-354 分流+javadoc；:155-167 exceptionally isResponseSent 守卫
  - P1-check-gw-86 PASS：AuthHttpServerFilter:212-215 initUserContext+addCookie 均在 :216 sendRedirect 之前；addCookie:386-394 真实构造 HttpCookie；diff 仅 +10 行无越界
  - 测试证据：TestGatewayHttpFilterSemantics 14（+4）/TestAuthHttpServerFilterSemantics 17（+2）surefire 全绿；_tmp/gw-tests.log gateway 111/auth-core 117 -am 链 BUILD SUCCESS（log 时序晚于判别 stash 日志=最终态）
  - 判别证据：_tmp/disc-gw49.log（14 run 1F+3E——恰 4 新用例：bodyReadCount 0→1/multipart parse 异常/malformed 穿透/二次写 IllegalStateException；旧 10 零受扰）+ _tmp/disc-gw86.log（17 run 1F——恰 redirect cookie 断言；其余 16 零受扰）
  - Anti-Hollow：hasRouteFor 活调用+initUserContext/addCookie 真实执行（cookie 值断言）+未提交分支真实写错误响应
  - 收口完整性：roadmap :155 done；index :138-139 fixed；daily log R1.24 条目；owner doc nop-network.md :57-59 逐句一致
  - 工具门禁：doc-links --strict 0（审计独立复跑）；checklist --strict 0（completed 态复跑）；hollow 扫描（nop-gateway/nop-biz-auth-core）退出码均 0
  - Follow-ups 诚实性：三条与 live 行为精确对应（透传缺口 parseRequestData data=null 实证/redirect 后 next.get() 噪音 :217 实证/path 级粒度差声明）

Follow-up:

- 非 JSON body 透传通道设计（data=null 缺口）；OAuth redirect 后短路 next（行为变更需独立评估）；IContext 效果直接断言（audit Minor-2）；孤立 javadoc 已在本轮清理（audit Minor-1）
