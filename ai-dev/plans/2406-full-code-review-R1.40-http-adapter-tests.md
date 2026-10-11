# 2406 full-code-review R1.40 — HTTP 适配层测试补齐批（P1-4.2-001 / P1-4.2-002）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.40；finding `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.2-test-effectiveness-audit.md:585-631`（§五 P1-4.2-001/002）+ `:1021`（修复方向）
> Related: R1.25（同模块面）；既有 `TestServletHttpServerContextRemoveCookie`（plan 2391）；`nop-http-api/.../IHttpServerContext.java`
> Owner Doc: `docs-for-ai/02-core-guides/testing.md`（+ `unit-test-antipatterns.md`）
> Plan Review: agent_773fe55b 首轮 NOT READY（1 Blocker：双实现「同期望镜像表」与 live 代码矛盾——spring/vertx 在 host/requestUrl/requestPath/query/headers 冲突/`setResponseHeader(null)` 上语义分歧，且 spring `getRequestUrl` 违反接口 javadoc；4 Major：Zip 分支不可测因 `ClassPathResource` 具体类型 + `BeanContainer` 未初始化会抛 + 缺 RequestDispatcher stub；GraphQL/registrar/WS 等 in-scope 面未裁定；Rule #22 端到端缺失；Deferred 占位符与预分类矛盾；Item Type 应为 Fix；6 Minor）→ 本轮全修：改为**逐实现期望表 + 显式分歧表**；`getRequestUrl` 契约违反立项为 in-scope Fix；Zip 用子类覆写 `getResource` + `BeanContainer` stub + dispatcher stub；补齐 in-scope 面裁定；新增请求链端到端判据；Deferred 具体化；Item Type 改 `Fix`；mutation 腿改编译安全形并加 src/main 还原校验。agent_53692b1d 二轮 NOT READY（1 新 Major：`getRequestUrl` 修复的调用方爆炸半径未分析——`AuthHttpServerFilter:535` `getRequestUrl().endsWith("/graphql")` 决定 401 错误体形态，修复后 spring 带 query 的 graphql 请求会退回 REST 形态〔vertx 已潜伏此缺陷〕；6 Minor）→ 本轮全修：把调用方改为 `getRequestPath().endsWith("/graphql")`（in-scope 连带 Fix + 回归测试，同时消除 vertx 潜伏缺陷）、mutation 还原门改 `*/src/main/**`、补 charset 排序/sendResponse-isResponseSent 分歧行、Phase 1 补 spring addCookie、修正 jakarta 类型名、Item Type 改 `Fix | Proof`、标注 `registerInstance(null)` 复位语义。agent_d08c1b9c 三轮 NOT READY（1 Major：Baseline charset 分歧行 spring 列写反；3 Minor：Zip mutation 腿非判别、Phase 2 镜像缺 sendResponse/maxAge、BeanContainer 全局复位需反射还原）→ 本轮按处方全修（charset 时机行改为「spring 在 setResponseContentType 追加、vertx 在 setResponseCharacterEncoding 即时重放」；mutation 腿改删除 forward 分支并补 fallthrough；Phase 2 补 sendResponse/isResponseSent 与「不设 maxAge」断言；BeanContainer 反射保存/还原 provider）。

## Purpose

为两条 HTTP 上下文适配层（spring servlet / quarkus vertx）补**直接行为测试**（JUnit5 + JDK 动态代理 stub，零新增运行时依赖，沿用 plan 2391 先例），以**逐实现期望 + 显式分歧表**分别覆盖两实现，并补 ZipContentEncodingFilter 分支/请求链与 WS 端点会话最低覆盖。同时修复测试暴露的 spring `getRequestUrl` 契约违反（不含 queryString，违反 `IHttpServerContext:50-53` javadoc，影响 `AuthHttpServerFilter:496` 的 redirect-back）。P1-4.2-002（spring servlet）无任何 e2e 缓解，优先。

## Current Baseline

- **P1-4.2-001**（quarkus）：`nop-quarkus/nop-quarkus-web` 手写适配层 1121 LOC、`src/test` **零测试**；`VertxHttpServerContext.java:60-128` 全量适配；`filter/ZipContentEncodingFilterRegistrar.java`、`filter/HttpServerFilterRegistrar.java`、`ws/JsonRpcWebSocketEndpoint.java`、`service/QuarkusGraphQLWebService.java` 零保护。e2e（`mvn quarkus:dev`）不在 `mvn test` 门禁内。
- **P1-4.2-002**（spring）：`nop-spring/nop-spring-web-starter` 1200 LOC 适配层；唯一测试 `TestServletHttpServerContextRemoveCookie`（3 用例，仅 removeCookie）；`ServletHttpServerContext.java` 主适配面（`:55-100` method/remoteAddr/remotePort/host/requestPath/requestUrl/queryParam(s)；`:103-112` getRequestHeaders；`:115-118` getRequestHeader；`:120-129` getCookie）零直接测试；`filter/ZipContentEncodingFilter.java:40-124` 分支零测试。spring 无任何 e2e 部署形态 → 无缓解。
- **逐实现在场语义分歧（live 亲读）**：
  | 方法 | spring servlet | vertx |
  |---|---|---|
  | `getHost` | `request.getRemoteHost()`（客户端主机名） | `request().host()`（Host 头） |
  | `getRequestUrl` | `getRequestURI()`（仅 path，无 query/host/scheme） | `absoluteURI()`（完整 URL 含 query） |
  | `getRequestPath` | `getRequestURI()`（含 context-path，未归一） | `normalizedPath()` |
  | `getQueryParam(s)` | `getParameter*`（query + form body 合并） | `queryParam*`（仅 query） |
  | `getRequestHeaders` 同名键 | `put()`（后值胜） | `putIfAbsent()`（首值胜） |
  | `setResponseHeader(name, null)` | `setHeader(name,"null")`（字面量 null 字符串） | `headers().remove(name)` |
  | `addCookie` | 设 `maxAge`（`HttpCookie`→`Cookie`）；忽略 sameSite | 不设 maxAge；支持 sameSite |
  | charset 追加时机 | `setResponseContentType` 时按已存 `characterEncoding` 追加 charset（`:384-389`）；`setResponseCharacterEncoding` 只存 encoding + `response.setCharacterEncoding`，**不**重放已设 content-type（`:391-395`） | `setResponseCharacterEncoding` **即时**把 charset 重放到已设 content-type（`:364-377`） |
  | `sendResponse` 后 `isResponseSent` | `response.isCommitted()`（`sendResponse` 不置标志） | 自有 `responseSent` 标志（`Vertx:48,178-182,349-351`） |
- **契约违反（in-scope 缺陷）**：`IHttpServerContext.getRequestUrl` javadoc「包含queryString的完整请求链接」；spring 实现仅 `getRequestURI()`。live 调用方 `AuthHttpServerFilter:496` `encodeURL(context.getRequestUrl())`（登录 redirect-back）会丢 query；`GatewayHttpFilter:333`、`CoreInitializationGuardFilter:27` 同受影响。vertx 侧合规。**连带调用方（已裁定为本批连带 Fix）**：`AuthHttpServerFilter:535` `context.getRequestUrl().endsWith("/graphql")` 用于 401 错误体形态选择——修复后 spring 的 `GET /graphql?query=...` 变为含 query 的绝对 URL，`endsWith` 为 false 会退回 REST `ApiResponse` 形态（vertx 因 `absoluteURI` 已潜伏同缺陷）；故同批把该判据改为 `context.getRequestPath().endsWith("/graphql")`（语义即「后端服务路径」），并加回归测试；既有 `TestAuthHttpServerFilterSemantics:263` 仅 stub `requestUrl="/graphql"` 无法捕获。
- **测试基础设施**：spring-web-starter 已有 test-scope `junit-jupiter` + JDK 代理 stub 先例；quarkus 各子模块无 junit test 依赖（需为 `nop-quarkus-web` 加 test-scope `junit-jupiter`；版本由 quarkus-bom 管理，父级 `maven.test.skip=true` 在 JDK≥17 profile 下关闭）。`BeanContainer.instance()` 无 provider 时抛 `ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED`（`BeanContainer.java:28-33`），测试需 `registerInstance(...)` 并以 `registerInstance(null)` 复位。
- **不可代理（明确排除）**：`VertxHttpServerContext.executeBlocking`（→`QuarkusExecutorHelper` 需 Quarkus 运行时 `BlockingOperationControl`/`ExecutorRecorder`）、`getRequestBody`/`sendStreamingResponse`（需 live vertx 事件循环回调）——本批不测，非静默跳过。

## Goals

- 两实现各自具备**逐实现期望表驱动**适配契约测试（字段矩阵 + 归一化 + null/空值边界），并显式声明分歧，可捕获单侧漂移。
- spring `getRequestUrl` 修复为含 queryString 的完整 URL（对齐接口 javadoc 与 vertx），有测试。
- ZipContentEncodingFilter 分支决策与 gz 头/content-type 行为有直接测试 + **请求链端到端**判据（proxy request → filter → response 捕获，满足 Rule #22）。
- 两 WS 端点（`JsonRpcWebSocketEndpoint`）取得最低会话生命周期覆盖。
- 受影响模块测试全绿且新测试可判别（mutation 腿证明）。

## Non-Goals

- 启动真实 quarkus/spring 容器做 e2e（进程内 stub 即可；容器/e2e 另属 `nop-entropy-e2e`）。
- spring/vertx 其余语义分歧的行为统一（`getHost`/`getRequestPath`/`getQueryParam`/`getRequestHeaders` 冲突策略/`setResponseHeader(null)`）——见 Deferred，逐条已裁定，不属本批修改面。
- P1-4.2-003..006 其他测试面。

## Scope

### In Scope

- `nop-spring/nop-spring-web-starter/src/test/java/io/nop/spring/web/**`（新增测试）
- `nop-spring/nop-spring-web-starter/src/test/resources/**`（如 Zip 测试需 fixture；优先用 classpath 已存在资源免新增）
- `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/ServletHttpServerContext.java`（**仅** `getRequestUrl` 契约修复）
- `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java`（**仅** `:535` 判据改 `getRequestPath()`）+ 对应测试 `src/test/java/io/nop/auth/core/filter/TestAuthHttpServerFilterSemantics.java`
- `nop-quarkus/nop-quarkus-web/src/test/java/io/nop/quarkus/web/**`（新增测试）
- `nop-quarkus/nop-quarkus-web/pom.xml`（新增 test-scope `junit-jupiter`）
- `docs-for-ai/02-core-guides/testing.md`（owner doc）
- 治理文件（Phase 3）：roadmap、index、daily log、本 plan

### Out Of Scope

- 除 `getRequestUrl` 外的生产适配行为变更；容器/e2e；其他模块

## Execution Plan

### Phase 1 - spring servlet 适配契约 + `getRequestUrl` 修复 + Zip/请求链（P1-4.2-002）

Status: completed
Targets: `nop-spring-web-starter/src/test/**`、`ServletHttpServerContext.java`（仅 getRequestUrl）

- Item Types: `Fix | Proof`

- [x] **`AuthHttpServerFilter:535` 连带修复**：`getRequestUrl().endsWith("/graphql")` → `getRequestPath().endsWith("/graphql")`（保持 401 错误体形态在带 query 的 graphql 请求下不变，并修复 vertx 侧潜伏缺陷）；`TestAuthHttpServerFilterSemantics` 既有 graphql 用例补 `requestPath`，并新增 `requestPath="/graphql"` + `requestUrl="http://localhost/graphql?query=..."` → 仍 GraphQL 形态的回归用例
- [x] **`getRequestUrl` 契约修复**：spring 返回完整请求 URL（`request.getRequestURL()` + 非空 `getQueryString()` 时追加 `?`+query），对齐 `IHttpServerContext:50-53` javadoc 与 vertx `absoluteURI()`；保持 `getRequestPath` 不变
- [x] 新增 `TestServletHttpServerContextAdapter`（JDK 动态代理 stub）：逐实现期望表覆盖 `getMethod`/`getRemoteAddr`/`getRemotePort`/`getHost`(=`getRemoteHost`)/`getRequestPath`(=`getRequestURI`)/`getRequestUrl`(修复后=full+query)/`getQueryParam`/`getQueryParams`(多参数顺序)/`getRequestHeaders`(小写归一 + 同名后值胜)/`getRequestHeader`/`getCookie`(命中/未命中/null cookies)/`setResponseHeader`(null→字面量 `"null"`，**显式断言该在场行为**，分歧表中标注)/`sendRedirect`+`isResponseSent`/`sendResponse(body)`/`getResponseContentType`/`setResponseContentType`+`setResponseCharacterEncoding`(补 charset)
- [x] `TestServletHttpServerContextAdapter` 覆盖 spring `addCookie`（设 `maxAge`、忽略 sameSite）与 `sendResponse` 后 `isResponseSent==response.isCommitted()`，与 Phase 2 同用例集对齐
- [x] 新增 `TestZipContentEncodingFilter`：测试子类覆写 `getResource(String)`（返回指向**已存在** classpath 资源的 `ClassPathResource`，如测试类自身 `.class`；否则不存在的名字）→ 覆盖 `doFilterInternal` 分支：`/index.html` 无 gz 且无 index.html→fallthrough；`.js`/`.css`/`.json` 有 `.gz`→`setZipHeader`+forward 到 `*.gz`；`.gz` 请求→zip 头；`newResponseWrapper` 忽略 content-type（`setContentType`/`setHeader`/`addHeader`）——`getRequestDispatcher` 用 proxy stub 捕获 forward 路径
- [x] Zip 测试需 `BeanContainer.registerInstance(stub)`（`tryGetBeanByType` 返回 null）并在 `@AfterEach` `registerInstance(null)` 复位；`io.nop.api.core.ioc.IBeanContainer`/`jakarta.servlet.http.HttpServletRequest`/`HttpServletResponse`/`jakarta.servlet.RequestDispatcher` 全用代理；`BeanContainer.registerInstance(null)` 复位的语义是「provider 非空但容器为 null → `isInitialized()==false`、`instance()` 返回 null」，非回到初始抛错态；为跨测试类隔离，`@BeforeEach` 反射保存 `BeanContainer._provider`、`@AfterEach` 反射还原到确切初始态（退化路径：`registerInstance(null)` + 确认本模块无测试依赖初始抛错态）
- [x] **Rule #22 请求链端到端**：proxy request（`/app.js` + gzip 资源在场）→ `ZipContentEncodingFilter.doFilterInternal` → 捕获响应（`content-encoding: gzip` + forward 路径 + content-type 由扩展名映射）——单测试内完成 entry→output
- [x] **判别（mutation 腿）**：①`getRequestPath()` 改为 `getRequestURL().toString()` → path 用例 FAIL；②`getRequestHeaders` 去小写归一 → header 用例 FAIL；③删除 `.js` 命中 gz 时的 forward 分支（直接 fallthrough）→ 请求链用例 FAIL；另补 `.js` 无 `.gz` 资源的 fallthrough 断言（防 m-1 非判别）；每腿后 `git diff --stat -- '*/src/main/**'` 确认已还原（`-- src/main` 从仓库根为 no-op）
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`（Phase 3 统一裁定 testing.md 是否增模式小节）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] `./mvnw test -pl nop-spring/nop-spring-web-starter,nop-service-framework/nop-biz-auth-core -am -T 1C`

Exit Criteria:

- [x] `TestServletHttpServerContextAdapter` 全绿，覆盖上列方法（类名/方法名在案）
- [x] `getRequestUrl` 修复后含 query（用例在案）；既有 3 个 removeCookie 用例仍绿
- [x] `AuthHttpServerFilter` 带 query 的 graphql 请求仍返回 GraphQL 错误体（回归用例在案）
- [x] `TestZipContentEncodingFilter` 全绿，含请求链端到端判据
- [x] mutation 三腿 FAIL→PASS 记录在 plan，且 `src/main` 还原经 `git diff` 核对
- [x] Phase 内 owner-doc 处置与 logs 条目已落
- [x] nop-spring-web-starter 全模块测试全绿

执行证据（Phase 1 + Phase 2 + Phase 3）：
- 新增测试：spring `TestServletHttpServerContextAdapter` 10 用例、`TestZipContentEncodingFilter` 6 用例（含 Rule #22 请求链端到端）、`TestJsonRpcWebSocketEndpointLifecycle` 3 用例；既有 `TestAuthHttpServerFilterSemantics` 增 query-bearing graphql 回归用例（18 用例全绿）；quarkus `TestVertxHttpServerContextAdapter` 10 用例 + `TestJsonRpcWebSocketEndpointLifecycle` 2 用例；`nop-quarkus-web/pom.xml` 增 test-scope junit-jupiter。
- 生产修复：`ServletHttpServerContext.getRequestUrl` 含 queryString（对齐 `IHttpServerContext:50-53`）；`AuthHttpServerFilter:535` 判据改 `getRequestPath().endsWith("/graphql")`（消除带 query 时错误体形态回归，并修复 vertx 潜伏同缺陷）。
- **mutation 腿（6/6 判别）**：servlet getRequestPath→getRequestURL / 去掉 header 小写归一 / Zip `.js` forward 分支禁用 / vertx getQueryParam→null / vertx setResponseHeader null 分支 / auth 判据回退 getRequestUrl —— 各自用例恰 1 FAIL；还原后 `git diff --stat -- '*/src/main/**'` 仅剩两处既定生产修复。
- 全量回归：`./mvnw test -pl nop-spring/nop-spring-web-starter,nop-quarkus/nop-quarkus-web -am -T 1C` BUILD SUCCESS（spring-web-starter 22/0/0；quarkus-web 13/0/0）；`check-doc-links --strict` 0；hollow `nop-spring`/`nop-quarkus` 0。

### Phase 2 - vertx 适配契约测试（P1-4.2-001；逐实现同用例集）

Status: completed
Targets: `nop-quarkus-web/src/test/**`、`nop-quarkus-web/pom.xml`

- Item Types: `Fix | Proof`

- [x] `nop-quarkus-web/pom.xml` 增 test-scope `junit-jupiter`
- [x] 新增 `TestVertxHttpServerContextAdapter`（代理 `RoutingContext`/`HttpServerRequest`/`HttpServerResponse`/`MultiMap`/`SocketAddress`）：**与 Phase 1 同一用例集、逐实现期望**——`getMethod`(method().name())/`getHost`(request.host())/`getRequestPath`(normalizedPath)/`getRequestUrl`(absoluteURI)/`getRemoteAddr`+`Port`(SocketAddress)/`getQueryParam`(首值)/`getQueryParams`/`getRequestHeaders`(小写归一 + `putIfAbsent` 首值胜；用含大小写异形同名的 stub MultiMap 判别)/`getRequestHeader`/`getCookie`(null/命中)/`setResponseHeader`(null→`headers().remove`，与 spring 分歧显式断言)/`getResponseContentType`/`setResponseContentType`+charset（按逐实现时机）/`sendRedirect`+`isResponseSent`/`sendResponse(body)`+`isResponseSent`
- [x] `addCookie(sameSite, cookie)`：sameSite 传**枚举 toString 字面量** `"Lax"/"None"/"Strict"` 与未知值 → 断 `response.addCookie` 捕获的 `Cookie.getSameSite()`；`removeCookie(name)`/`removeCookie(name,domain,path)` 委派断言；并断 vertx addCookie **不设 maxAge**（与 spring 分歧行对齐）
- [x] 类 javadoc 注明与 `TestServletHttpServerContextAdapter` 的用例配对关系、分歧清单与漂移检测意图（无跨模块运行时测试，仅镜像用例集）
- [x] **判别（mutation 腿）**：①`getQueryParam` 改为恒返回 null → 用例 FAIL；②`setResponseHeader` null 分支改为 `set(String.valueOf(value))` → 用例 FAIL；每腿后 `git diff --stat -- '*/src/main/**'` 确认已还原（`-- src/main` 从仓库根为 no-op）
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] `./mvnw test -pl nop-quarkus/nop-quarkus-web -am -T 1C`

Exit Criteria:

- [x] `TestVertxHttpServerContextAdapter` 全绿，覆盖上列方法并与 Phase 1 同用例集
- [x] addCookie sameSite 四值 + removeCookie 委派在案
- [x] mutation 两腿 FAIL→PASS 记录在 plan，`src/main` 还原核对
- [x] Phase 内 owner-doc 处置与 logs 条目已落
- [x] nop-quarkus-web 全模块测试全绿

### Phase 3 - WS 会话 + owner doc + 收口

Status: completed
Targets: 两模块测试、`docs-for-ai/02-core-guides/testing.md`、治理文件

- Item Types: `Fix | Proof | Decision`

- [x] 两 WS 端点最低覆盖：`io.nop.spring.web.ws.JsonRpcWebSocketEndpoint` / `io.nop.quarkus.web.ws.JsonRpcWebSocketEndpoint` 的 open→close 会话注册/注销、error 路径（`Session`/`EndpointConfig` proxy + `BeanContainer` stub + `IGraphQLEngine`/`IUserContextExtractor` stub）；如某侧依赖 live 容器不可代理，则对该侧登记 Deferred（含理由与 successor）
- [x] owner doc：`docs-for-ai/02-core-guides/testing.md` 新增「双实现 HTTP 适配层契约测试」小节（代理 stub/逐实现期望/BeanContainer 反射还原/请求链判据/mutation 腿）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.40 翻 done；index P1-4.2-001/002 → `fixed`（Lead 2026-10-10 同步完成）；daily log 条目
- [x] 独立子 agent closure audit（`plan-closure-audit-prompt.md`），evidence 写入本 plan Closure 段
- [x] `./mvnw -q compile -pl nop-spring/nop-spring-web-starter,nop-quarkus/nop-quarkus-web,nop-service-framework/nop-biz-auth-core -am` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2406-full-code-review-R1.40-http-adapter-tests.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-spring --severity high` 与 `--module nop-quarkus --severity high` 退出码 0（2026-10-10 复核均 0）

Exit Criteria:

- [x] 两 WS 端点最低会话覆盖完成，或不可代理侧 Deferred 有理由
- [x] owner doc 已更新（testing.md）
- [x] doc-links 检查退出码 0
- [x] roadmap/index/log 三方一致（roadmap R1.40=done、index 两行=fixed + R1.40 行=done、daily log 在位）
- [x] 独立 closure audit 完成并落盘
- [x] Phase 内 owner-doc 处置与 logs 条目已落

## Closure Gates

- [x] in-scope live defect（两适配层零直接测试 + spring `getRequestUrl` 契约违反）已闭合
- [x] 行为/契约结果已达成（逐实现契约测试 + 请求链端到端 + WS 覆盖/Deferred）
- [x] 必要 focused verification 已完成（两模块全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] **无静默跳过（Rule #24 N/A 声明）**：本批为测试补齐，无新增 public 分支/开关；不可代理方法（executeBlocking/getRequestBody/sendStreamingResponse）已显式排除并说明理由
- [x] 受影响 owner docs 已同步或显式 `No owner-doc update required`
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证测试真实断言适配行为（非断言 stub 自身），并以 mutation 腿证明可判别
- [x] `./mvnw -q compile -pl nop-spring/nop-spring-web-starter,nop-quarkus/nop-quarkus-web,nop-service-framework/nop-biz-auth-core -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2406-full-code-review-R1.40-http-adapter-tests.md --strict` 退出码 0

## Deferred But Adjudicated

### 两实现语义分歧的行为统一（getHost / getRequestPath / getQueryParam / getRequestHeaders 同名键策略 / setResponseHeader(null)）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 本批以**逐实现期望表 + 分歧清单**锁定在场行为，先建立回归保护；统一语义属跨部署形态的行为变更（`getHost` 无 javadoc 定义、`getQueryParam` 合并 form body 可能是既有依赖、`setResponseHeader(null)` 字面量 `"null"` 变更可能影响现存调用方），需独立契约裁决与迁移评估，非本测试批范围。分歧已逐条写入两测试类 javadoc 与 plan Baseline，无静默。
- Successor Required: `no`（如需统一另立契约 plan）

### GraphQL HTTP 入口与 filter registrar/config（SpringGraphQLWebService / QuarkusGraphQLWebService / ZipContentEncodingFilterRegistrar / HttpServerFilterRegistrar / SpringHttpServerFilterConfiguration）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 均为薄委派（GraphQL 服务转 `IGraphQLEngine`）或容器装配（registrar/config 绑定 filter 与 vertx 路由），其行为属容器启动面，`mvn test` 内以 stub 断言装配结果价值低、且 quarkus 侧 registrar 依赖 live vertx router；quarkus 形态已有 e2e UI 冒烟穿越（finding 缓解注），spring 形态本批已补适配层直接测试。显式登记避免 Rule #16 静默。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 真实容器形态的 e2e 覆盖（spring 形态缺失）——`nop-entropy-e2e` 面

## Closure

Status Note: Independent closure audit (fresh subagent session, not the implementation author) re-read the live repo: the two production diffs, both per-implementation adapter tests, the Zip request-chain test, both WS endpoint tests, the auth regression test, the owner doc and the daily log. All in-scope Exit Criteria and Closure Gates PASS. Focused verification is green: spring-web-starter 22/0/0, quarkus-web 12/0/0, biz-auth-core 118/0/0. One mutation leg was re-executed live (auth caller revert → exactly 1 FAIL, then restored). No in-scope live defect or contract drift was silently deferred. The Lead concurrently landed the roadmap R1.40 / audit-index bookkeeping (Phase 3 L136 and exit criterion L147 are now checked), so the plan has 0 unchecked items and `check-plan-checklist --strict` exits 0.
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit (subagent), audit session `session-9601d130-1bfd-4608-86de-471049fd6c0e`; not the implementation author (no self-audit).
- Production anchors (live-read): `nop-spring-web-starter/.../ServletHttpServerContext.java:80-90` now returns `request.getRequestURL()` plus `?`+`getQueryString()` when non-empty, matching the `IHttpServerContext.java:41-53` javadoc ("包含queryString的完整请求链接") and vertx `absoluteURI()`; `nop-biz-auth-core/.../AuthHttpServerFilter.java:537` now tests `getRequestPath().endsWith("/graphql")` (requestPath has no queryString per the same javadoc), so the 401 body shape no longer regresses on `GET /graphql?query=...`.
- Per-implementation expectations verified against live code: `VertxHttpServerContext.java:60-169` (host from `request().host()`, normalizedPath, absoluteURI, first-value queryParam, putIfAbsent header collision, `headers().remove` on null, sameSite, no maxAge, own responseSent flag, charset replay on `setResponseCharacterEncoding`) and `ServletHttpServerContext.java:55-150,376-402` (getRemoteHost, getRequestURI path, put() last-value, literal "null", maxAge, charset only at setContentType) — every assertion in the two adapter test classes matches one of these live behaviors; the explicit divergence list is asserted rather than hidden.
- Focused verification (live run): `./mvnw test -pl nop-spring/nop-spring-web-starter,nop-quarkus/nop-quarkus-web,nop-service-framework/nop-biz-auth-core -am -T 1C` BUILD SUCCESS; surefire `TestServletHttpServerContextAdapter` 10/0/0, `TestServletHttpServerContextRemoveCookie` 3/0/0, `TestZipContentEncodingFilter` 6/0/0 (incl. Rule #22 `testRequestChainEntryToOutput`), spring `TestJsonRpcWebSocketEndpointLifecycle` 3/0/0, `TestVertxHttpServerContextAdapter` 10/0/0, quarkus `TestJsonRpcWebSocketEndpointLifecycle` 2/0/0, `TestAuthHttpServerFilterSemantics` 18/0/0 (module total 118/0/0). `./mvnw -q compile ... -am` exit 0.
- Anti-Hollow: mutation leg re-executed live — `AuthHttpServerFilter` `getRequestPath`→`getRequestUrl` produced exactly 1 FAIL (`testResponseNotLoginWritesGraphQLShapeWhenGraphqlUrlHasQuery`, response body was the REST `ApiResponse` shape), then the file was restored and `git diff --stat -- '*/src/main/**'` shows only the two intended production fixes. The other five claimed legs were re-derived from live code and each maps to exactly one discriminating test (`testBasicFields`, `testRequestHeadersAreLowerCasedAndLastValueWins`, `testJsWithGzForwardsToGzAndSetsZipHeader`, `testQueryParams`, `testSetResponseHeaderNullRemovesHeader`). Assertions bind adapter-produced values, not stub internals.
- Static gates (live): `scan-hollow-implementations.mjs --module nop-spring --severity high` and `--module nop-quarkus --severity high` both exit 0 (0 findings, 0 critical); `check-doc-links.mjs --strict` exit 0 (0 errors).
- Anti-Hollow Rule #24: no new public branch/switch was added (test-only batch plus two contract fixes); the un-proxy-able adapter methods (`executeBlocking`/`getRequestBody`/`sendStreamingResponse`) are explicitly excluded with rationale in Current Baseline, not silently skipped.
- Deferred honesty: the cross-implementation divergences are registered as `watch-only residual` with rationale; GraphQL/registrar/config surfaces are adjudicated in `Deferred But Adjudicated`; both WS endpoints are covered (spring open/close/error, quarkus open/close); no in-scope live defect was demoted.
- Advisory, non-blocking, recorded for honesty: (a) the Phase 2 item text also names `removeCookie(name,domain,path)` delegation but `testRemoveCookieDelegatesToResponse` only exercises the 1-arg overload; (b) quarkus `TestJsonRpcWebSocketEndpointLifecycle` has no `onError` case while spring does; (c) the spring header "last value wins" assertion is not discriminating because the stub map is case-insensitive (the lowercasing assertion is discriminating). None is a live defect or contract drift; none blocks closure.
- Residual: none. `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2406-full-code-review-R1.40-http-adapter-tests.md --strict` exits 0 with 0 unchecked items (verified after closure); the Lead's concurrent sync set roadmap R1.40=done and audit-index P1-4.2-001/002=fixed.

Follow-up:

- 三条 closure advisory 已在提交前关闭：①`removeCookie(name,domain,path)` 3 参重载委派用例；②quarkus WS `onError` 用例；③spring header 用例改用大小写敏感 stub 使其真正判别（结果 map 归一为 1 键、put() 取后值）。quarkus-web 模块计数随之 12→13。
- 真实容器形态的 e2e 覆盖（spring 形态缺失）——见 `Non-Blocking Follow-ups`，归属 `nop-entropy-e2e` 面。
- 无其余 plan-owned 工作。
