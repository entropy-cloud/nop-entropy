# 2391 full-code-review R1.25 — HTTP 错误路径修复批（P1-6.8-001 + P1-check-netrpc-24）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.25；findings `P1-6.8-001`（6.8-check-p1-batch-d.md）+ `P1-check-netrpc-24`（承接 3.9 §2.5 表#1 标定维持），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.8-check-p1-batch-d.md` 与 `3.9-gateway-network-deep-audit.md`
> Related: R1.24（同网关面已收口）；3.9 audit :846 三实现行为分歧证据（仅 JDK 把 503 映射为连接失败，Apache/OkHttp 按正常响应返回）
> Owner Doc: `docs-for-ai/03-modules/nop-network.md`（JDK client 503 语义）；nop-spring 面如有 cookie 契约描述同步（实施时核对）

## Purpose

收口 HTTP 错误路径两条 P1：①ServletHttpServerContext.removeCookie 对 `request.getCookies()` 无 null 判——无 Cookie 的登出请求（过期会话/扫描器/curl 高频）NPE 500，认证登出链功能失效；②JdkHttpClient.toHttpResponse 把 503 硬编码映射为 NopConnectException（连接失败）——丢 body/cause，与 Apache/OkHttp（按正常响应返回）行为分歧，真实 503 全部被误判。

## Current Baseline

- **P1-6.8-001**：`nop-spring/nop-spring-web-starter/.../ServletHttpServerContext.java:146-160` removeCookie 首行 `for (Cookie cookie : request.getCookies())` 无 null 判；对照同类 getCookie:119-126 **有** `cookies == null` 守卫（契约形态已在场）。zero-test 模块（无 src/test）——修复须新建最小测试树 + pom 增 junit test 依赖（R1.16 nop-batch-orm 先例）；HttpServletRequest/Response 为 jakarta 接口，JDK 动态代理 stub 可行（IUserContext 代理先例），无新依赖。
- **P1-check-netrpc-24**：`nop-network/nop-http/nop-http-client-jdk/.../JdkHttpClient.java:320-323`——`toHttpResponse` 首行 `if (response.statusCode() == SC_SERVICE_UNAVAILABLE) throw new NopConnectException(ERR_HTTP_CONNECT_FAIL)`（"某些MAC系统"注释未变）。3.9 §2.5#1 标定 still-live：全套件无 503 用例锚定；:846 亲证 Apache/OkHttp 按正常响应返回——三实现行为分歧，真实 503（网关宕机等常见态）在 JDK 路径全部误判为连接失败且丢 body。toHttpResponse 为 package-private——同包测试可直调；HttpResponse 为 JDK 接口可匿名 stub。ERR_HTTP_CONNECT_FAIL 错误码本身保留（连接失败路径仍用）。
- **测试基建**：nop-http-client-jdk 有成熟测试树（TestJdkHttpClient 等 8 文件）；nop-spring-web-starter 零测试。

## Goals

- removeCookie null 守卫：cookies==null 时跳过匹配循环、落兜底过期 Cookie 分支（行为对齐 getCookie 形态与三参重载）。
- toHttpResponse 删除 503 特判：503 按正常响应返回（status/body/headers 完整送达），与 Apache/OkHttp 对齐；javadoc 记录行为变更（旧 workaround 移除——现代 JDK 连接失败正确抛 ConnectException，503 属服务端响应语义）。
- 判别测试双腿 stash 实证；nop-http-client-jdk 测试全绿 + nop-spring-web-starter 新测试树绿。

## Non-Goals

- JdkHttpClientHelper.wrapException 丢 cause 的通用修复（3.9 :846 关联面，属异常归一化契约批）；Apache/OkHttp 行为；removeCookie 三参重载；ERR_HTTP_CONNECT_FAIL 错误码清理。

## Scope

### In Scope

- `ServletHttpServerContext.removeCookie` null 守卫 + 新建测试树（pom junit test 依赖 + Proxy stub 测试）
- `JdkHttpClient.toHttpResponse` 删 503 特判 + javadoc 行为变更注记 + 同包判别测试
- owner doc：nop-network.md JDK client 503 语义注记（行为变更声明）

### Out Of Scope

- Non-Goals 全部；nop-spring 其他 filter；503 重试语义（调用方域）

## Execution Plan

### Phase 1 - 双修 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] ServletHttpServerContext.removeCookie：`Cookie[] cookies = request.getCookies(); if (cookies != null) { for ... }`——null 落兜底过期 Cookie 分支（对齐 getCookie :119-126 形态）
- [x] nop-spring-web-starter pom 增 junit-jupiter test 依赖；新建 src/test 树：Proxy stub HttpServletRequest/Response——①getCookies→null 时 removeCookie 不 NPE 且 response.addCookie 收到过期 Cookie（name/空值/maxAge=0）；②cookies 在场含目标 name 时复用原 Cookie 置 maxAge=0
- [x] JdkHttpClient.toHttpResponse：删除 503→NopConnectException 特判（javadoc 记录行为变更与三实现对齐裁定）；同包新增测试：HttpResponse stub（503 + body bytes + headers）→ toHttpResponse 返回 status=503 且 body 完整，不抛 NopConnectException
- [x] **判别验证（双腿 stash）**：①stash removeCookie 守卫→null 场景测试 ERROR(NPE)；②stash 503 特判删除→503 测试 FAIL（抛 NopConnectException）——各腿恢复后绿
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 双修全落地（null 守卫/503 对齐）
- [x] 判别测试双腿绿 + nop-http-client-jdk 既有测试零回归
- [x] nop-spring-web-starter 新测试树绿 + nop-http-client-jdk 全绿
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-network.md`

- Item Types: `Fix`

- [x] nop-network.md：JDK client 503 语义注记（行为变更：503 按正常响应送达——三实现对齐；移除旧 MAC/JDK workaround 裁定）
- [x] nop-spring 面契约描述核对（docs-for-ai/03-modules/ 有无 nop-spring 文档，有则同步 removeCookie null 语义）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.25 翻 done；index P1-6.8-001、P1-check-netrpc-24 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-6.8-001 + P1-check-netrpc-24）
- [x] 行为/契约结果已达成（判别双腿 + 零回归）
- [x] 必要 focused verification 已完成（两模块测试全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-spring/nop-spring-web-starter,nop-network/nop-http/nop-http-client-jdk` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- JdkHttpClientHelper.wrapException 丢 cause 通用修复（异常归一化契约批——3.9 :844 建议长线）
- 旧 MAC/JDK workaround 移除的部署影响观察（现代 JDK 连接失败语义已正确；如遇旧 JDK 反馈再议）

## Closure

Status Note: 两条 in-scope P1 均已在 live 代码中确认修复：①`ServletHttpServerContext.removeCookie`（:146-166）对 `request.getCookies()` 加 null 守卫，cookies==null 时落兜底过期 Cookie 分支（对齐 `getCookie` :119-129 形态），零测试模块测试树首建（pom junit test 依赖 + JDK 动态代理 stub）；②`JdkHttpClient.toHttpResponse`（:317-323）已删除 503→NopConnectException 特判，503 按正常响应返回 status/body/headers，与 Apache/OkHttp 对齐，`ERR_HTTP_CONNECT_FAIL` 仍保留于真连接失败路径。owner doc `docs-for-ai/03-modules/nop-network.md:74-76` 已记录 503 语义；doc-links --strict 退出码 0。无空壳实现，无 in-scope live defect 被降级。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: subagent closure-audit backfill（独立审计子会话；未参与实现）
- Evidence:
  - **Exit Criteria Phase 1**：PASS——live `ServletHttpServerContext.java:146-166` null 守卫在位；`JdkHttpClient.java:317-323` 503 特判已删（grep `SERVICE_UNAVAILABLE`/`NopConnectException` 在 toHttpResponse 无残留，未用 import 已清理）。
  - **Exit Criteria Phase 2**：PASS——`docs-for-ai/03-modules/nop-network.md:74-76`「JDK client 503 响应语义」节在位且与 live 行为一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
  - **指认测试在位且断言目标行为**：`nop-network/nop-http/nop-http-client-jdk/src/test/java/io/nop/http/client/jdk/TestToHttpResponseStatus.java`（503 stub + body → status=503/body 完整，`assertDoesNotThrow`；200 回归）；`nop-spring/nop-spring-web-starter/src/test/java/io/nop/spring/web/filter/TestServletHttpServerContextRemoveCookie.java`（null/匹配/无匹配三场景）。
  - **focused verification**：`./mvnw test -pl nop-spring/nop-spring-web-starter,nop-network/nop-http/nop-http-client-jdk -am -DskipTests=false` → BUILD SUCCESS（脚本退出码 0）。nop-http-client-jdk `Tests run: 20, Failures: 0, Errors: 0, Skipped: 3`（含 `TestToHttpResponseStatus` 2/2）；nop-spring-web-starter `Tests run: 3, Failures: 0, Errors: 0`（`TestServletHttpServerContextRemoveCookie` 3/3）。
  - **Anti-Hollow**：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-spring-web-starter --severity high` 与 `--module nop-http-client-jdk` 均 0 findings；调用链 `HttpClient.sendAsync → thenApply(toHttpResponse)`（:173/:543/:580）连通，503 分支不再抛出。
  - **Closure Gate 全项**：全部 in-scope defects 修复 / 判别测试绿 / 零回归 / 无静默降级 / owner doc 同步 / 独立 audit / anti-hollow —— 均 PASS。
  - **Deferred 分类检查**：`Deferred But Adjudicated` 为空；`Non-Blocking Follow-ups` 两项（wrapException 丢 cause 通用修复属异常归一化契约批；旧 workaround 部署影响观察）确为 non-blocking，未含 in-scope live defect。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2391-full-code-review-R1.25-http-error-paths.md --strict` 退出码 0。

Follow-up:

- 无 remaining plan-owned work（Non-Blocking Follow-ups 见上节）。
