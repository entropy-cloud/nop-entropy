# 2508 CRA Phase 8.14 — R2-05 2.3 服务热路径剩余批（R05 SSO 刷新钉线 + R25 重试放大，2 项）

> Plan Status: active
> Draft Review: R1 agent_ccc9c237 @ 2026-10-26（0 Blocker、2 Major、6 Minor；基线 22 处 file:line 抽查 20 PASS/2 勘误类 ±1~2；**PA 自洽性核查 PASS**——nop-auth-sso 执行形态改造非认证语义变更、fail-closed 设为 ask-first 触发条件护栏闭合）——M1 SSO 超时键接线载体缺失[`SsoConfig` @ConfigBean 不经 config-prefix 不接线，live bean 用逐属性 `@cfg:` 引用 → 定夺 beans.xml 增补一行 `@cfg:` property 为默认分支 + Scope 增补 `sso-defaults.beans.xml` + rule-23 增补配置覆盖真实生效断言，防死配置键空心面]；M2 准入许可释放保证与准入面语义未闭合[定夺：全出口 finally/whenComplete 释放 + tryAcquire 置于同步抛出点后 + 异常路径许可归还红腿 + 准入面=url 路由腿[XPL/RPC 非 HTTP 路由不入池] + 新准入错误码钉 `AiFailoverGatewayInterceptor` 不可重试分类防 fallback 级联]；Minor×6（勘误 :15-21 订正、七例区间 :36-81、E2E 归属 nop-auth-sso 测试树 + fake IdP 无需真实网络端点、RecordingHttpClient 桩形态扩展声明、`tryFallbackUrl` req.timeout R4-16 面补登记）——修订全量应用，转 active
> Last Reviewed: 2026-10-26
> Source: roadmap 工作项 8.14（R2-05，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L156，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-05 行（L79：finding ID 清单 2 项[2.3-C4-007；2.3-C8-002] / 模块与验证命令 nop-service-framework `./mvnw test -pl nop-service-framework -am` / Owner Doc=`docs-for-ai/02-core-guides/service-layer.md` / PA=无 / 依赖「无前置（可并行）」/ exit criteria 增补：**SSO token 刷新异步化+timeout API+thenCompose 领先 next.get；429 重试准入上界/时间整形[在途×90s OOM 轨迹消除]**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 2.3 审计目录 `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/`（C4-round2.md C4-007 立案+复核勘误 9 / C4-round3.md ①修复设计核对+勘误 7 / C8-backpressure.md C8-002 立案 / C8-round2.md ②量化收口 / merge-adjudication.md G-B/G-C 族+终裁 2 / C10-test-protection.md P1-4[→C10-004] 与 P1-6[→C10-006]，逐条回读路径见 Current Baseline）；执行基线 @ `2afb9d0c7e`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.13 已 done，plan 2495–2507 均 completed；本 Baseline 全部行号为该基线 live 回读值，2026-10-26 亲读，执行前如再漂移以 live 为准并登记）
> Related: plan 2505（R2-02，completed——**服务热路径先例**：4.3-C3-002 SSE 反压 + 3.1-C7 期限两侧同批、PA 面 plan-first 批内闭环、设计文档+独立 draft review 门禁形态，本批 Phase 1 沿用其纪律）；plan 2504（R2-01，completed——**LoadWaitGraph 有界等待先例**：`LOCK-001`「默认有界 + ≤0 不设上界档」两档配置语义，本批超时键/准入键沿该语义）；plan 2498（R1-03，completed——同 owner doc 先例：service-layer.md「GraphQL WS 订阅推送的执行载体」节[`:335`]，本批新节与该节并列）；plan 2499（R1-04，completed——**零签名契约扩展先例**（default 方法纯新增 source-compat）+ service-layer.md「异步 biz action 的上下文传播契约」节[`:343`]）；**下游/并行协同**：R4-16+R4-17（roadmap 8.56，P2-G36 束 24 项——**R4-16 行明示软依赖 8.14**：本批 SSO 异步面与准入闸为其承载前提，C3-010 取消传播/CL-025 断连原语/C4-003 挂起与载体治理/C4-004 专用池/C4-008 JWKS 钉线均归其）；R2-06（roadmap 8.15，C3-003 流式缓冲批 A——网关相邻文件成员面）
> Protected Area 授权：批次 PA=无（batch-proposal §二.2 R2-05 行授权列）。live 回读勘误：C4-007 修复载体触达 **nop-auth 聚合两模块**（本体 `nop-auth/nop-auth-sso` + 对照 `nop-auth/nop-auth-service`，勘误 1）——PA 裁定：本批对 nop-auth 的触碰为**执行形态改造**（同步阻塞→异步链 + 请求超时 + 契约 default 方法纯新增），**零认证模型语义变更**（刷新失败降级保持现行 error→null→携既有 token 继续的 stale-serve 语义，认证判定/权限模型/会话语义零改动），不构成 AGENTS.md「权限/认证模型（nop-auth）」ask-first 面；**ask-first 触发条件**：若 Phase 2 实施中发现必须改变认证可见结果（如刷新超时改 fail-closed 拒绝请求）方可落地，则停止并按 ask-first 询问——该边界随裁定①④一次定夺。契约新增面 `ILoginService` 位于 nop-biz-auth-core（非 `nop-*-api` 模块）；`ILoginSpi`（:59 闲置 SPI）与 `nop-auth-api` LoginApi 零触碰。本批不触碰 `model/*.orm.xml`、不扩 nop-api-core/nop-http-api 接口签名（`HttpRequest.timeout` :123 已在位，零变更消费）、不做 `_` 前缀生成物改动。

## Purpose

关闭 R2-05 批的 2 项 P1 已确认发现：①**SSO 半衰期 token 刷新钉线**（2.3-C4-007，CL-016 终裁立案）——`AuthHttpServerFilter` 认证载体线程经 `FutureHelper.syncGet` 同步阻塞 IdP HTTP 且刷新请求无显式超时（客户端默认 readTimeout 30s 为唯一有界面），IdP brownout 下全局 worker 载体被按 RTT 钉满、全站确定性吞吐坍缩（≈max(8C,200)/30s 停摆）；修复 = 契约异步化 + 请求超时 + 过滤器 `thenCompose` 链序（刷新领先 `next.get()`）。②**Gateway 429 重试放大无准入**（2.3-C8-002）——主链 1+3 重试 + fallback ≤3 单发 = ×7 加法放大、单请求挂起 ≈90s+、在途并发 C=λ·H 无上界（Little's law），429 洪峰下确定性 OOM 轨迹；修复 = 在途准入上界（类型化快速拒绝）+ 时间整形（jitter 破同步重试波）+ 重试/上限配置化。批末 roadmap 8.14 → done，2 项成员台账全部收敛。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-05 = 2 项成员（P1×2，均独立不并案）；cra-index P1 台账 2 行：`nop-service-framework-hotpath-C4-007` L209、`-C8-002` L211（裁定批次均=R2-05，全部 open）；C10 验收前置 = **C10-004**（C4-007 单成员）+ **C10-006**（C8-002 单成员）均整条入批——无共享条目拆分（对照 2507 勘误 6 拆分形态）；roadmap 8.14 行 Status=todo。前置：无前置（可并行），R1-00～R1-08（plan 2495–2503）与 R2-01～R2-04（plan 2504–2507）均 completed @ `2afb9d0c7e`——基线门禁已满足。
- **G36 族边界**（merge-adjudication §2 族表 + cra-index L420）：C4-007=G-C auth 链族主案（C4-004/C4-008 相邻面归 R4-16）；C8-002=G-B 网关重试链族成员（与 C4-003 承重柱不同——「OOM 轨迹独立于线程语义」系 C8 复核终裁 2，本批只闭合准入上界/时间整形）。**前批同域产物**：plan 2498 WS 写异步化泵（service-layer.md :335 节）、plan 2499 上下文传播契约（:343 节 + `CTX-001` 锚点）——C4-007 异步化后刷新完成回调漂移到 HTTP 客户端完成线程，MDC/上下文语义按 `CTX-001` 对照核验（C4-round3 1.3 注记：现有绑定 :352/解绑 :382 本就不覆盖漂移线程，非新增危害类别，裁定①成文确认）。
- **取消传播前置缺失（G-B 家族事实，登记不承接）**：`IHttpServerContext` 无断连观测 API（grep 零命中，C4-round3 ②.2）→ CL-025 平台原语前置，C3-010 取消挂接归 R4-16；本批准入拒绝为服务端主动快速失败，不依赖断连原语，两修复面正交。

### 2 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `2afb9d0c7e` 2026-10-26 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 2.3-C4-007（P1，CL-016 终裁立案；勘误 9 后 brownout 形态 P1 维持） | 2.3 / nop-service-framework（nop-biz-auth-core 契约与过滤器）+ **nop-auth（nop-auth-sso 本体 / nop-auth-service 本地 JWT 无害对照）** | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java`：`autoRefreshToken` 默认 true :70-75、`isNeedRefresh` 半衰期判定 :320-329、`handleUserContext` :342-384（`runOnContext` 内联 lambda :357-378、**:359 同步 `loginService.refreshToken`**、:362-365 设头/cookie、:374 `next.get()`、:377 `FutureHelper.bindResult`、MDC finally :381-383、:354 future 在 lambda 异常逃逸时永不完成）；`nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/login/OAuthLoginServiceImpl.java`：`refreshToken` :247-268（**:258 `FutureHelper.syncGet(httpClient.fetchAsync(req, null))`**，error→null :260-263）、`newRefreshTokenRequest` :270-285（**未设任何请求超时**）；契约 `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/ILoginService.java`：`String refreshToken(IUserContext, AuthToken)` :123（同步契约）、`getUserContext` sync 包装 :169-171；装配 `nop-auth/nop-auth-sso/src/main/resources/_vfs/nop/auth/beans/sso-defaults.beans.xml` `nopLoginService` :15-19（`nop.auth.sso.enabled` 条件）；对照 `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java` `refreshToken` :954-960（纯本地重签微秒级无害）；闲置 SPI `ILoginSpi.refreshTokenAsync` :59（错误层/错误 bean/错误语义错位，勘误 7 不可复用） | SSO 部署下半衰期刷新（默认开启、稳态约半数请求命中窗口）在认证载体线程上执行无显式超时的阻塞 IdP HTTP：brownout 下每刷新钉住载体线程 ≈30s 且失败不降频重复阻塞，并发半衰期请求在无隔离全局 worker 池上级联钉满 → 确定性服务级吞吐停摆；附带面：刷新抛异常时 :374 从未执行、:354 future 永不完成（请求挂死形态） | **契约异步化 + timeout API + thenCompose 领先 next.get**（裁定①定形态）：`ILoginService` 新增 default 方法 `refreshTokenAsync(IUserContext, AuthToken): CompletionStage<String>`（纯新增零签名破坏，默认回退 sync 包装保持既有实现兼容——2499 先例，SPI 回归面=AbstractLoginService+两实现+测试 fixture 全列重枚举）；`OAuthLoginServiceImpl` override 为纯异步链（:258 的 `fetchAsync(...).thenApply` 本就是异步形状，仅剥离 `syncGet` 外壳）+ `newRefreshTokenRequest` 单行 `req.timeout(...)`（`HttpRequest.timeout` :123-124 在位；配置键落 SsoConfig，沿 LOCK-001「默认有界 + ≤0 回落客户端默认」两档语义）；`handleUserContext` lambda 改异步链：刷新以 `thenCompose` **严格领先** `next.get()`（共享 `UserContextImpl` 变异可见性 + 头/cookie 新鲜度依赖该序，禁止并行），链尾 `bindResult` 不变；降级 = 异常/超时 → warn + 携既有 token 继续（与现行 error→null stale-serve 一致，认证结果零变化），异常不再逃逸任务体（闭合挂死面）；本地 JWT 零改造（默认方法兼容核验） |
| 2 | 2.3-C8-002（P1，与 C4-003 承重柱独立——C8 复核终裁 2） | 2.3 / nop-service-framework（nop-gateway） | `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/InvokeProcessor.java`：`invokeUrl` **maxRetries=3 局部硬编码** :135、`invokeUrlWithRetry` :139-196（429 分支 :153-159 按 Retry-After 退避 + `delayedFuture` :198-202 `CompletableFuture.delayedExecutor`（JDK Delayer 全局单线程 + 无界 DelayedWorkQueue）挂起、**`MAX_RETRY_DELAY_MS=30_000` 钳制** :208、`parseRetryAfter` :210-233 / `capRetryDelay` :235-237、5xx 分支 :162-167 指数退避已带 jitter、429/5xx 终态 :170-179）、`buildHttpRequest` :242-256（无请求超时——C4-003 F3 面，本批不动）；`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiFailoverGatewayInterceptor.java`：`maxRetries` 字段 :35、`maxFallbackAttempts` :69-71、`tryInvokeWithFallback` :73-109、`tryFallbackUrl` :111-145（**直连 `fetchAsync` 单发绕过重试链，429 :128-130 / 5xx :131-133 链到下一 fallback，腿间无延迟无 timeout**）；配置面 `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/GatewayConfigs.java`（仅 `nop.gateway.model-path` 一键，准入/重试配置零）；装配 `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/impl/GatewayHandler.java` **:76 `new InvokeProcessor(...)` 直构**（无 bean）、`nop-service-framework/nop-gateway/src/main/resources/_vfs/nop/gateway/beans/gateway-defaults.beans.xml` `nopAiFailoverGatewayInterceptor`（fallbackUrls list + maxRetries=3）；错误族 `GatewayErrors.java`（`ERR_GATEWAY_UPSTREAM_429` :50 / `ERR_GATEWAY_UPSTREAM_FAILED` :53） | 429/5xx 被实现为「代客户端重试」而非快速拒绝：每网关请求放大至 4+3=×7 次上游调用（加法，fallback 绕过 InvokeProcessor 无乘法），单请求以挂起 CompletionStage 持有完整 body String+JSON 树 ≈90s+，在途并发 C=λ·H 随到达率线性增长且无任何准入上界（grep Semaphore/RateLimiter/admission 零命中）→ 高 λ 下确定性 OOM 轨迹；同值 Retry-After 使全部被拒请求同步齐发重试（无 jitter 重试波） | **准入上界 + 时间整形**（裁定②定形态）：并发许可（tryAcquire 非阻塞）在途上界，落点 = `InvokeProcessor.invoke` 入口（主腿+重试挂起全程持有）+ `tryFallbackUrl` 入口（fallback 腿单独准入）共享同一许可池——在途上游腿数 ≤ permits、与持有时长 H 解耦（OOM 轨迹数学消除）；超限 = 类型化快速拒绝（新 GatewayErrors 错误码，GatewayHttpFilter 错误响应路径既有消费），不排队不静默丢弃；时间整形 = Retry-After 钳制后值施加 jitter（既有 30s 钳制与 parseRetryAfter 七例语义零回退），jitter 源可注入；配置化 = 准入上限键 + 主链 maxRetries 键（默认 3 现行为保持），沿 LOCK-001 两档语义 |

### 平台设施与测试资产盘点（live 实读 @ `2afb9d0c7e`）

- **可复用平台件（修复不得重复造轮子）**：`HttpRequest.timeout(long)`/`setTimeout`（`nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/client/HttpRequest.java` :123-124/:140-141——C4-007 只需消费）；`HttpClientConfig` 默认 readTimeout/writeTimeout=30s、connectTimeout=10s（:35-37，勘误 9 有界面兜底）；`FutureHelper.success` :79 / `syncGet` :158 / `bindResult` :298（nop-api-core 零签名消费）；`IRateLimiter`/`DefaultRateLimiter`（`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/ratelimit/`——**速率限流非并发上界**，裁定②须显式声明许可原语选型）；`AbstractUserContextCache.getUserContextAsync` :103-120 返回同会话**同一** `UserContextImpl` 实例（thenCompose 串行序可见性依据，亦是会话聚合 successor 天然合并点）；新错误码落 `GatewayErrors` 既有错误族（NopException + ErrorCode + `.param`，英文消息）。
- **既有测试资产（回归门禁与测试基建）**：`InvokeProcessorRetryTest`（parseRetryAfter 七例 :36-81（第 7 例 @Test :80-81，draft review Minor-2 口径）钉 Retry-After 解析与 30s 钳制——**C8-002 零回退门禁**；`invokeUrl_429exhausted_throwsWithStatusAndBody` :162 / `invokeUrl_5xxexhausted_throwsWithStatusAndBody` :181 断言错误码+httpStatus+body 三参数；成功例 :200）；`AiFailoverGatewayInterceptorTest`（:24/:51/:154/:181 + maxRetries fallback 上界例——含 `RecordingHttpClient` 真实计数）；`TestAuthHttpServerFilterSemantics#testIsNeedRefreshHalfLife` :150（刷新触发判定三态钉——**反向验证通过**；刷新执行链零覆盖 = C10-004 缺口本体：18 个过滤器用例无一执行 handleUserContext 刷新支路，`OAuthLoginServiceImpl` 全仓无 refreshToken 执行链测试）；`TestGatewayHttpFilterErrorResponse`/`TestGatewayHttpFilterSemantics`/`GatewayContextImplTest`（错误响应路径与夹具）；nop-auth-sso 测试域仅 issuer/解析契约 4 类。
- **C10-006 无钉面（P1 承重面，Phase 2 逐项补齐）**：在途并发上界/准入闸零覆盖；主腿 1+3 重试次数无调用计数断言；InvokeProcessor×AiFailover 加法组合（4+3=7）无组合级测试。

### Baseline 勘误（输入不一致登记）

1. **模块触达勘误（batch 行模块列=nop-service-framework 不完整）**：C4-007 缺陷本体 `OAuthLoginServiceImpl.refreshToken` :247-268 位于 `nop-auth/nop-auth-sso`，本地 JWT 对照 `LoginServiceImpl` :954-960 位于 `nop-auth/nop-auth-service`——修复变更集跨 nop-service-framework + nop-auth 两聚合。门禁增补：`./mvnw test -pl nop-auth -am`（沿 2507 为 api-core 增补 nop-kernel 门禁先例）；Phase 3 缺口声明按实际 diff 核对 `docs-for-ai/03-modules/nop-auth.md`（存在）覆盖面。C8-002 全部站点位于 nop-service-framework/nop-gateway（无越界；`nop-ai-gateway.md` owner 属 nop-ai/nop-ai-gateway 不同模块，R2-06 面）。
2. **行号核对（审计→live，零实质漂移）**：handleUserContext :342-384 ✓（lambda :357-378、:359、:374、:377）；isNeedRefresh :320-329 ✓；autoRefreshToken :70-75（审计载 70-74，live 注解 :72，±1 行以 live 为准）；OAuthLoginServiceImpl :247-268/:258/:270-285 ✓；ILoginService :123/:169-171 ✓；ILoginSpi :59 ✓；InvokeProcessor :135/:153-159/:162-167/:198-202/:208/:210-237/:242-256 ✓；AiFailoverGatewayInterceptor :35/:69-71/:73-109/:111-145 ✓；GatewayHandler :76 ✓；sso-defaults.beans.xml 审计载 :15-21 → live `nopLoginService` bean 块 :15-21（零漂移；draft review Minor-1 订正勘误初稿误记的 :15-19）；HttpRequest.timeout :123-124/:140-141 ✓；HttpClientConfig :35-37 ✓。
3. **C10 条目映射与裁定范围**：C10-004（C4-007）/C10-006（C8-002）均整条入批随本体收敛；既有反向验证钉（testIsNeedRefreshHalfLife 三态、parseRetryAfter 七例、重试耗尽两例、fallback 上界例）列为零回退门禁。audit 建议④「按会话聚合刷新」与建议⑤「与 C4-004 专用执行器合并」**均不在批次裁定修复方向内**（batch-proposal L79 + cra-index L209 均为三件套）——聚合入 Deferred、专用执行器归 R4-16。断连前置缺失（CL-025）登记见批次基线第 3 条（→ R4-16，本批准入拒绝与其正交）。

## Goals

- Phase 1 设计裁定文档一次裁决四条裁定（独立 draft review 通过），无临场发明空间：①C4-007 异步化形态；②C8-002 准入上界/时间整形；③测试缝与观测缝；④边界声明（R2-16 归 8.25、G36 归 8.56）。
- 两项成员全部修复并各带回归测试（rule 25）：C4-007（异步化+超时+链序+降级）、C8-002（准入上界+jitter+配置化）；C10-004/C10-006 验收前置随本体落地（E2 同 diff：过滤器刷新执行链覆盖 + 准入闸/重试计数/组合级断言）。
- 关键工单先红后绿或变异/回退论证留痕（E2）：C4-007（brownout 载体钉线红/请求挂死红/无超时红）、C8-002（在途无界红/同步重试波红）。
- Owner doc 随批更新：`docs-for-ai/02-core-guides/service-layer.md`（新建「认证过滤器 token 刷新与网关重试准入并发契约」节，与 :335/:343 节并列）+ `docs-for-ai/04-reference/source-anchors.md`（AUTH-003 扩展 + 网关准入锚点，按实际）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-16/R4-17（roadmap 8.56，P2-G36 束 24 项）的任何工单**：C3-010（断连取消）、CL-025（断连原语）、C4-003（幽灵重试/挂起/载体治理——含 `buildHttpRequest` :242-256 上游请求超时缺失面）、C4-004（executeBlocking 载体/nop-auth 专用有界执行器）、C4-008（JWKS 同型钉线）、C4-002/C4-005、WS 设施族与 G-D 流式面——本批不建专用执行器、不动取消传播。
- **不做 R2-06（roadmap 8.15）成员**（C3-003 等流式缓冲 11 项）；**不做 R2-16（roadmap 8.25）成员**（3.1 会话/配额取消束 10 项——认证会话语义本批零改动，仅按裁定①串行序消费）。
- 不触碰 `ILoginSpi`（:59 闲置 SPI 不改造——错误层错位，勘误 7 终裁，CL-023 维持）、不触碰 `nop-auth-api`（LoginApi 零变更）、不扩 nop-api-core/nop-http-api 接口签名、不改 `parseRetryAfter`/`MAX_RETRY_DELAY_MS` 既有钳制语义、不改本地 JWT `LoginServiceImpl.refreshToken`（默认方法兼容核验除外）；不做 Redis MFA/SMS store syncGet 族（C4-007 F3⑦ 移交线索）；不触碰 `model/*.orm.xml`、`_` 前缀生成物、nop-plugin；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-service-framework/nop-biz-auth-core`：`ILoginService`（default 方法纯新增）、`AuthHttpServerFilter.handleUserContext`（异步链化）+ 测试。
- `nop-auth/nop-auth-sso`：`OAuthLoginServiceImpl`（override 异步链 + 请求超时）、`SsoConfig`（超时配置键）+ 测试；`nop-auth/nop-auth-service`：`LoginServiceImpl` 兼容回归（默认零改造）。
- `nop-service-framework/nop-gateway`：`InvokeProcessor`（准入 + jitter + maxRetries 配置化）、`AiFailoverGatewayInterceptor`（fallback 腿准入）、`GatewayHandler`（构造适配）、`GatewayConfigs`（新配置键）、`GatewayErrors`（新错误码）+ 测试。
- 设计文档：`ai-dev/design/concurrency/hotpath-sso-refresh-async-and-gateway-retry-admission.md`（裁定①–④，最终状态写法）；Owner doc：`docs-for-ai/02-core-guides/service-layer.md`（新建节）、`docs-for-ai/04-reference/source-anchors.md`（锚点扩展）。

### Out Of Scope

- Non-Goals 所列全部项；R4-16/R4-17、R2-06、R2-16 及后续批次工单；C3-010/C4-003/C4-004/C4-008/CL-025/C3-003 等相邻缺陷本体。

## Execution Plan

### Phase 1 - SSO 刷新异步化与网关重试准入设计裁定文档 + 独立评审

Status: completed
Targets: `ai-dev/design/concurrency/hotpath-sso-refresh-async-and-gateway-retry-admission.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下四项裁定，每条唯一预设默认分支：
  - [x] **裁定① C4-007 异步化形态**：(a) 契约 API 形态——预设默认分支 = `ILoginService` 新增 default 方法 `refreshTokenAsync(IUserContext, AuthToken): CompletionStage<String>`，默认实现 `FutureHelper.success(refreshToken(...))`（纯新增零签名破坏，SPI 回归面 live 重枚举登记，2499 先例）；备选「过滤器侧对无异步能力实现降级包装」须论证为何契约层不足后方可采（`ILoginSpi` 错误层先例为拒绝理由输入）。(b) timeout 键——预设默认分支 = SsoConfig 新键（预设名 `nop.auth.sso.refresh-token-timeout`，默认 10s[audit 建议 5-10s 档]、≤0 = 不设显式请求超时回落客户端默认 30s[LOCK-001 两档语义]，落点 `newRefreshTokenRequest` 单行 `req.timeout`）。(c) thenCompose 链序——刷新严格领先 `next.get()`（共享实例变异可见性 + 头/cookie 新鲜度；禁止并行），完成线程漂移与 MDC 窗口语义按 `CTX-001` 对照成文。(d) 降级语义——预设默认分支 = 异常/超时 → warn + 携既有 token 继续（stale-serve，与现行 error→null 一致、认证结果零变化，PA 边界锚点）+ 异常不再逃逸 runOnContext 任务体（闭合 :354 挂死面）；备选 fail-closed = 触发头部 ask-first 条件，须用户询问后方可采。(e) 本地 JWT 零改造兼容核验声明。
  - [x] **裁定② C8-002 准入上界与时间整形**：(a) 准入形态——预设默认分支 = 并发许可 tryAcquire（非阻塞）+ 超限类型化快速拒绝（不排队不静默丢弃；新 GatewayErrors 错误码），落点 = `InvokeProcessor.invoke` 入口（主腿+重试挂起全程持有）+ `tryFallbackUrl` 入口（fallback 腿准入）共享许可池，在途 ≤ permits 与 H 解耦（OOM 轨迹消除数学陈述成文）；备选（有界排队/丢弃策略）须论证为何快速拒绝不满足网关语义后方可采；许可原语选型（`Semaphore` vs `IRateLimiter`——后者为速率限流非并发上界）一次声明。(b) 时间整形——预设默认分支 = Retry-After 钳制后值施加 equal jitter（delay/2 + U(0, delay/2)，保留 ≥delay/2 上游恢复窗并破同步重试波），jitter 源可注入（测试缝），配置开关/比率与 5xx 既有 jitter 分支（:162-167）关系成文；既有 30s 钳制与 parseRetryAfter 语义零改动。(c) 配置键——`GatewayConfigs` 新增准入上限键（预设名 `nop.gateway.upstream-max-inflight`，默认有界值设计文档定夺、≤0 = 不设上界档）+ 主链重试上限键（预设名 `nop.gateway.upstream-max-retries`，默认 3 现行为保持）。(d) 与 Retry-After 的关系——尊重语义不变、许可覆盖重试挂起窗（挂起不释放）、准入拒绝优先于重试发起。
  - [x] **裁定③ 测试缝与观测缝**：C4-007 = fake `IHttpClient`（可控 future）+ `req.timeout` 捕获断言 + `handleUserContext` 刷新支路执行链测试（C10-004 缺口本体；N4 注记——fake executeBlocking 同步内联掩蔽载体维度，夹具须显式声明载体缝）+ 不钉线观测（载体线程返回后再完成 fake future）+ 链序断言（`next.get` 严格后于刷新完成）+ 降级断言（刷新失败→下游继续+无头/cookie 更新+warn）；C8-002 = `RecordingHttpClient` 计数[**桩形态扩展声明（draft review Minor-4）**：既有 RecordingHttpClient[ArrayList 立即完成型]无 pending-future 控制与并发在途计数能力——「第 N+1 个挂起」与在途 ≤ permits 断言需扩展为可控 future + Atomic 计数桩或新建子类，Phase 2 不得临场发明其他缝]+ 准入拒绝断言（permits 耗尽→零上游调用+类型化错误）+ 在途计数上界断言 + jitter seeded 分布断言 + 主腿重试计数断言（补 C10-006 缺口）。
  - [x] **裁定④ 边界声明**：R2-16（8.25）会话/配额取消束零承接；G36 面（C3-010/CL-025/C4-003/C4-004/C4-008）归 8.56 并核对无重叠（R4-16 软依赖本批的承载前提注记）；C3-003 面=8.15；Redis 移交线索；本批许可闸与未来断连取消原语的正交组合声明。
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–④全部项且每条有唯一预设默认分支，独立 draft review 记录在案
- [x] 裁定①含 SPI 回归面重枚举清单与 ask-first 触发边界成文；裁定②含双落点对照、OOM 轨迹消除数学陈述、IRateLimiter 选型声明；裁定④含 R2-16/R4-16/R2-06 三向边界核对结论
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

> **Phase 1 执行记录（2026-10-26）**：设计文档 `ai-dev/design/concurrency/hotpath-sso-refresh-async-and-gateway-retry-admission.md`（~168 行）落盘并登记 README（层级表 + 阅读顺序第 10 位 + 实现锚）。四裁定唯一结论：①`ILoginService` default `refreshTokenAsync`（**SPI 回归面 9 面[3 main + 6 fixture]全列 + `AbstractLoginService` 族规则兜底**）+ `OAuthLoginServiceImpl` 剥 syncGet 纯异步链（error→null stale-serve 保持）+ 超时键 `nop.auth.sso.refresh-token-timeout` 10s/≤0 回落 30s + **接线 = `sso-defaults.beans.xml` `@cfg:` property 行** + `thenCompose` 链序**经 `ctx.runOnContext` 再入任务队列**执行设头/next.get（contextHolder 绑定丢失规避 + 再入调用 checkClosed 守护）；②平台 `ISemaphore`（`tryAcquire(1,0)` 形态钉）双落点共享池（invokeUrl 主腿含挂起窗 + tryFallbackUrl 逐腿；XPL/RPC/流式不入池）+ 全出口 finally/whenComplete 释放 + **拒绝传播按落点钉死[主腿同步 throw / fallback 腿 failedFuture]** + 新码不可重试钉 + equal jitter 后置（七例钉零触碰）+ `upstream-max-inflight` 默认 1000 / `upstream-max-retries` 默认 3 + OOM 数学（P 与 λ/H 解耦）；③fake IHttpClient protected 字段注入 + **新建独立可控桩**（RecordingHttpClient private 不可继承）+ E2E 链路①归 nop-auth-sso / 链路②归 nop-gateway；④边界（R2-16 零承接/G36→8.56/C3-003→8.15）+ 五组零回退钉。
>
> 独立 draft review R1（agent_969e4acc，fresh session，30+ 处抽查零漂移）：0 Blocker / **3 Major**（SPI 面 9 vs 8 穷举性失实；fallback 拒绝同步 throw 复制挂死面；next.get 执行介质丢失 contextHolder）+ 5 Minor 全修；R2 增量复核 M2/M3 PASS（异常收敛自洽）+ M1 半闭合后补齐 + checkClosed 补强。实施期亲读 4 发现（SPI fixture 实为 5→6、RecordingHttpClient private、InvokeProcessor 构造适配定夺保留旧两参重载、流式路由不入池）均已入文。设计文档 Status → active。零产品代码改动。`check-doc-links --strict` 退出码 0。

### Phase 2 - 两成员实现 + 回归测试（auth 组 + gateway 组）

Status: planned
Targets: `nop-service-framework/nop-biz-auth-core/src/`（login/filter）与 `nop-auth/nop-auth-sso/src/`、`nop-auth/nop-auth-service/`（回归）、`nop-service-framework/nop-gateway/src/`（core/executor、core/interceptor、impl、GatewayConfigs、GatewayErrors）——各自 main + test 树

- Item Types: `Fix`（两成员均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10-004/C10-006 验收前置）

- [ ] C4-007 实现（C10-004）：按裁定①实施契约 default 方法 + OAuthLoginServiceImpl 异步链 override（剥离 syncGet :258）+ 请求超时键 + handleUserContext 异步链化（thenCompose 领先 next.get）+ 降级语义闭合挂死面
- [ ] C8-002 实现（C10-006）：按裁定②实施许可准入（invoke + tryFallbackUrl 双落点共享池）+ 类型化快速拒绝错误码 + jitter 整形 + maxRetries/准入上限配置化 + GatewayHandler 构造适配
- [ ] 回归测试（rule 25 显式覆盖 + C10 验收前置同 diff，先红后绿或变异/回退论证留痕）：
  - [ ] C4-007 红①（钉线+挂死）：fake IdP 挂起下（修复前）载体线程被钉住、:354 future 不完成、请求无响应——修复后载体线程即时返回、刷新完成回调驱动下游（线程身份断言）；异常注入（修复前任务体逃逸挂死红）→ 修复后 warn + 下游继续 + 无头/cookie 更新（stale-serve 语义钉）
  - [ ] C4-007 红②：修复前 `newRefreshTokenRequest` 构造的 HttpRequest timeout=0（捕获断言红）→ 修复后 = 配置键值 + ≤0 档回落断言；链序钉：`next.get` 严格后于刷新完成（保持性回归钉，防后续重构成并行）；`testIsNeedRefreshHalfLife` 三态钉零回退；本地 JWT 路径回归绿
  - [ ] C8-002 红①：permits=N 耗尽后第 N+1 个并发上游腿（修复前）照常发起并挂起（在途无界红）→ 修复后零上游调用 + 类型化拒绝错误码；在途计数全程 ≤ permits 断言（挂起窗内持有）
  - [ ] C8-002 红②：主腿 1+3 重试调用计数断言（修复前无此钉/不可配）→ 默认 3 + 配置覆盖生效 + 4+3=7 加法组合级断言；同值 Retry-After × N（修复前）齐发重试红 → seeded jitter 分布断言（首波重试时刻非单点）；parseRetryAfter 七例 + 30s 钳制 + 耗尽两例 + fallback 上界例零回退
- [ ] **端到端验证**（rule 22）：≥1 条 SSO 刷新链路测试（`filterAsync` 入口 → 半衰期判定 → 异步刷新撞 brownout[**fake `IHttpClient` 可控 future 构造，无需真实网络端点**；**E2E 归属 = nop-auth-sso 测试树**——真实 OAuthLoginServiceImpl+fake IHttpClient 链；nop-biz-auth-core `TestLoginService` fixture 走 default 方法不触达真实链，不得作为链路①证据——draft review Minor-3/6] → 载体线程不阻塞 → 降级语义 → `next.get()` 下游输出，出口点=客户端可观测响应/头状态）；≥1 条 429 风暴链路测试（N>permits 并发 429 → 在途有界 → 超限快速类型化拒绝路径 → 出口点=GatewayHttpFilter 错误响应可达客户端）
- [ ] **接线验证**（rule 23）：`GatewayHandler` :76 直构的 `InvokeProcessor` 真实携带许可设施断言（非测试直接 new 的自证）；`nopLoginService` 装配的 `OAuthLoginServiceImpl.refreshTokenAsync` 真实被过滤器调用断言（mock verify 或链路标志位）；`nopAiFailoverGatewayInterceptor` fallback 腿准入接线断言
- [ ] **无静默跳过**（rule 24）：准入拒绝类型化抛出而非静默吞/排队挂起；刷新降级 warn 日志 + 显式 stale-serve 而非空 catch；超时/准入 ≤0 档语义显式成文而非 magic number；新增公共分支无空方法体/TODO
- [ ] **提交纪律**：设计文档先行单独提交（Phase 1 → 独立评审 → 实现）；两工单分组提交——**auth 组**（C4-007：nop-biz-auth-core + nop-auth-sso 两聚合同变更集 + LoginServiceImpl 回归）、**gateway 组**（C8-002：nop-gateway），C10-004/C10-006 测试与对应本体**同一 commit**（E2 同 diff 核验依据）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 两成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10-004/C10-006 同 diff 落地
- [ ] 必红证据（E2）：C4-007 钉线红/挂死红/无超时红、C8-002 在途无界红/同步波红（先红后绿或变异/回退论证），快照落 `_tmp/` 并登记
- [ ] 零回退实证：`InvokeProcessorRetryTest`（七例+两耗尽+成功）、`AiFailoverGatewayInterceptorTest` 全部、`testIsNeedRefreshHalfLife`、`TestGatewayHttpFilterErrorResponse` 全绿（钉零回改项逐条 git diff 为空核对）
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [ ] `./mvnw test -pl nop-service-framework -am` BUILD SUCCESS；`./mvnw test -pl nop-auth -am` BUILD SUCCESS（勘误 1 增补门禁）；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: 受影响聚合模块全量测试（nop-service-framework / nop-auth）；`docs-for-ai/02-core-guides/service-layer.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2507 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-service-framework -am` + `./mvnw test -pl nop-auth -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `model/*.orm.xml`、`_` 前缀生成物、nop-plugin，`ILoginSpi`/`nop-auth-api`（LoginApi）/nop-api-core/nop-http-api 接口签名零变更（`git diff --name-only` 核对登记）
- [ ] Owner doc 更新：`service-layer.md` **新建**「认证过滤器 token 刷新与网关重试准入并发契约」节（刷新异步化/超时档/thenCompose 链序/stale-serve 降级 + 准入上界/时间整形/配置键契约——内容源 = Phase 1 设计文档，派生摘要标注「以设计文档为准」）；`source-anchors.md` 扩展 `AUTH-003` + 新增网关准入锚点（按实际）；缺口声明：按实际 diff 核对触达模块 03-modules 覆盖面（nop-auth.md 核对 sso 刷新契约承载面；nop-gateway 无独立 03-modules 条目——按 R-G 治理轨先例登记，不在本批新建 owner doc）
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-service-framework --severity high`、`--module nop-auth --severity high` 退出码 0（批前既有发现逐条裁定登记）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] service-layer.md 新建节 + source-anchors.md 锚点扩展落地 + 缺口声明登记；check-doc-links --strict 退出码 0；scan-hollow 两聚合模块逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（orm.xml/生成物/nop-plugin/ILoginSpi/LoginApi/api-core/http-api 签名）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.14 行、cra-index 台账

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（异步链运行时连通、准入闸运行时生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：`nop-service-framework-hotpath-C4-007` L209、`-C8-002` L211 状态收敛（open → fixed，注记 plan 2508）+ C10-004/C10-006 整条收敛；roadmap 8.14 行 Status → done
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2508-cra-phase8.14-r2-05-hotpath-sso-retry.md --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 2 项成员（L209/L211）状态全部收敛 + C10-004/C10-006 收敛注记、roadmap 8.14 = done
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 2 项成员全部修复：C4-007（异步化+超时+链序+降级）、C8-002（准入上界+时间整形+配置化）
- [ ] batch-proposal R2-05 行 exit criteria 增补两条逐条达成并留证：①**SSO token 刷新异步化 + timeout API + thenCompose 领先 next.get**（契约 default 方法 + override 异步链 + 请求超时键 + 过滤器链序钉落地）；②**429 重试准入上界/时间整形[在途×90s OOM 轨迹消除]**（在途 ≤ permits 与持有时长解耦的断言留证 + jitter/配置化落地）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；两成员均有先红后绿或变异/回退论证（E2）；C10-004/C10-006 同 diff 落地
- [ ] 设计文档裁定①–④成文且经独立 draft review；既有钉（parseRetryAfter 七例/30s 钳制/重试耗尽两例/fallback 上界/testIsNeedRefreshHalfLife 三态）零回退实证（既有回归全绿 + diff 抽验）
- [ ] R4-16/R2-06/R2-16 边界守约：断连原语/取消传播/载体治理/流式缓冲/会话取消束零越界修复、移交注记在案（裁定④）；PA ask-first 触发条件未被触发（或已按规询问并记录）
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-service-framework -am`、`./mvnw test -pl nop-auth -am` BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：service-layer.md（新建契约节）、source-anchors.md（锚点扩展）；缺口声明登记（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）刷新异步链/许可准入/降级路径运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（受影响聚合模块）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2508-cra-phase8.14-r2-05-hotpath-sso-retry.md --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 2 成员收敛 + roadmap 8.14 → done（E6）

## Deferred But Adjudicated

### 按会话聚合刷新去抖（C4-round2 建议④）

- Classification: `optimization candidate`；Successor Required: `no`（R-G 治理轨或后续优化批登记）
- Why Not Blocking Closure: 批次裁定修复方向（batch-proposal L79 + cra-index L209）为异步化+timeout+thenCompose 三件套，聚合不在其列；异步化+超时已闭合 P1 承重柱（载体钉线与吞吐坍缩），聚合仅降低 IdP 侧重复刷新流量（P2 级优化）；共享实例天然合并点事实（`AbstractUserContextCache` :103-120）已登记为 successor 设计输入。

### G36 族面（C3-010/CL-025/C4-003/C4-004/C4-008 + C4-002/C4-005 等相邻成员）

- Classification: `out-of-scope improvement`（batch-proposal P2-G36 行显式批次安排，非本批降级）；Successor Required: `yes`；Successor Path: R4-16/R4-17 批 plan（尚未立项；roadmap 8.56）
- Why Not Blocking Closure: 全部成员 ∈ P2-G36（cra-index L420）→ R4-16+R4-17（roadmap 8.56）承接；R4-16 行明示软依赖 8.14——本批 SSO 异步面与准入闸为其承载前提，移交注记在裁定④成文。

### Redis MFA/SMS store syncGet 族（C4-007 F3⑦ 移交线索）

- Classification: `out-of-scope improvement`；Successor Required: `no`（按审计移交轨登记）
- Why Not Blocking Closure: 站点位于五模块外（nop-auth-service Redis store 8 处 syncGet），审计已定性为「同族线索移交相应审计单元」，非 R2-05 批次成员。

## Non-Blocking Follow-ups

- **R4-16 对接注记**：本批落地后向 R4-16 作「SSO 异步面 + 准入闸就绪」完成注记（R4-16 行软依赖 8.14 的落点）；C4-003 挂起治理实施时以本批许可闸后的 live 形态为基线；上游 fetch 请求超时（`buildHttpRequest` :242-256 无 timeout，C4-003 F3 面；`tryFallbackUrl` :113-145 `req.timeout` 同属 R4-16 面——draft review Minor-5 补登记）随其同批评估，本批超时键两档语义可作配置键设计输入。
- no remaining plan-owned work（占位，closure 时按实际复核重写）

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<如用子 agent，记录 session ID>>
- Evidence: <<每条 Exit Criterion 与 Closure Gate 的验证结果；check-plan-checklist --strict 退出码；Anti-Hollow 检查结果>>

Follow-up:

- <<只记录 non-blocking follow-up 或明确写 no remaining plan-owned work；confirmed live defect 不得出现在这里>>
