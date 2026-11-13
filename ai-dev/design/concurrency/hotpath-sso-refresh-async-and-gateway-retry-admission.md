# SSO 刷新异步化与网关重试准入上界设计（Hotpath SSO Refresh Async and Gateway Retry Admission）

> Status: active（plan 2508 Phase 1 产出；独立 draft review R1+R2 通过，0 Blocker/0 Major）
> Last Reviewed: 2026-10-28（行号 = live @ `523c0c3ca2` 亲读回读值；与 plan 2508 执行基线 `2afb9d0c7e` 全量核对零实质漂移，执行前如再漂移以 live 为准并登记）
> **评审**：R1 agent_969e4acc @ 2026-10-26——0 Blocker、3 Major（M1 SPI 回归面枚举漏第 9 面[`TestInjectValuePureJvmDefaults.AbstractLoginServiceForTest` 转承面]→ 9 面/6 fixture + 族规则兜底；M2 fallback 腿准入拒绝同步 throw 将复制 :354 挂死面 → 按落点钉死[主腿同步 throw / fallback 腿 failedFuture] + 验收断言；M3 next.get()/设头步执行介质未裁定[完成线程直执丢失 contextHolder 绑定 → 定夺经 `ctx.runOnContext` 再入任务队列]）+ 5 Minor（构造适配定夺保留旧两参重载、`tryAcquire(1,0)` 形态钉、@cfg: :28-31/:33-40 口径、RPC 断言降格、header 收敛）全修；R2 增量复核——M2/M3 PASS（异常收敛自洽：再入段任务体由 completeExceptionally 透传/降级映射双路覆盖，:354 无永不完成路径不变式兜底）、M1 半闭合后补齐（§3.1.1 表第 9 行 + 计数订正）、checkClosed 守护补强落文、无新 B/M。**结论：0 Blocker / 0 Major → active 进入 Phase 2**
> Source: plan 2508（`ai-dev/plans/2508-cra-phase8.14-r2-05-hotpath-sso-retry.md`）Phase 1 裁定①–④义务；发现事实源 = WI 2.3 审计目录 `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/`（C4-round2.md 的 C4-007 立案与复核勘误 9 / C4-round3.md 的修复设计核对与勘误 7 / C8-backpressure.md 的 C8-002 立案与维度复核终裁 2 / C8-round2.md 的量化收口 / merge-adjudication.md 的 G-B 网关重试链族与 G-C auth 认证链族 / C10-test-protection.md 的 P1-4→C10-004 与 P1-6→C10-006）；批次归组 = batch-proposal §二.2 R2-05
> 实现锚：`nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/`（filter/AuthHttpServerFilter + login/ILoginService + login/AbstractUserContextCache）+ `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/`（login/OAuthLoginServiceImpl + SsoConfig）+ `nop-auth/nop-auth-sso/src/main/resources/_vfs/nop/auth/beans/sso-defaults.beans.xml` + `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/`（core/executor/InvokeProcessor + core/interceptor/AiFailoverGatewayInterceptor + GatewayConfigs + GatewayErrors + impl/GatewayHandler）

## 一、设计结论

1. **裁定①（C4-007 SSO 刷新异步化形态）**：契约 = `ILoginService` 新增 default 方法 `refreshTokenAsync`（入参与同步版同形、返回 `CompletionStage<String>`），默认实现为既有同步 `refreshToken` 的 `FutureHelper.success` 包装——纯新增零签名破坏（plan 2499 零签名契约扩展先例）；该 default 是**兼容回退型**而非 `revokeUserSessionsAsync`/`mfaVerifyAsync` 的快速失败型（UOE 型 default 会迫使全部既有实现同步改造，与「零改造兼容」目标相反）。SPI 回归面 live 全仓重枚举 9 面（3 main + 6 fixture——draft review Major-1 勘误：初稿漏计面 9；族规则兜底 = `AbstractLoginService` 全部子类经 default 转承，Phase 2 新增实现/fixture 同样零破坏） 9 面（3 main + 6 fixture——draft review Major-1 勘误：初稿漏计 `TestInjectValuePureJvmDefaults.AbstractLoginServiceForTest`[nop-biz-auth-core 测试树 :41 extends AbstractLoginService 转承面]；并按族规则兜底：`AbstractLoginService` 全部子类经 default 转承，Phase 2 新增实现/fixture 同样零破坏）。`OAuthLoginServiceImpl` override 为纯异步链：剥离 :258 `FutureHelper.syncGet` 外壳（内部 `fetchAsync(...).thenApply` 本为异步形状，error→null 的 stale-serve 语义逐字保持）；`newRefreshTokenRequest` :270-285 增设显式请求超时（`HttpRequest.timeout` :123-124 既有 API 零变更消费）；超时键 = `nop.auth.sso.refresh-token-timeout`，默认 10000ms，≤0 = 不设显式请求超时（回落客户端部署级默认，`HttpClientConfig` readTimeout 30s 兜底）——沿 LOCK-001「默认有界 + ≤0 豁免档」两档语义；**接线载体 = `sso-defaults.beans.xml` 的 `nopSsoConfig` bean（:23-41）增补一行 `@cfg:` property**（`SsoConfig` 为 `@ConfigBean` 无 config-prefix 直连，该 bean 关键配置走逐属性 `@cfg:` 引用（:28-31；:33-40 url 项为字面量模板——draft review Minor-7 口径订正，`|10000` 行内默认语法有 :31 先例），`SsoConfig` 字段默认值与 `@cfg:` 默认同为 10000 防双源漂移），rule-23 配置覆盖真实生效断言随批（防死配置键空心面）。过滤器 `handleUserContext`（:342-384）改异步链：刷新以 `thenCompose` **严格领先** `next.get()`（共享 `UserContextImpl` 变异可见性 + 头/cookie 新鲜度依赖该序，禁止并行）；runOnContext 任务体改为全异常包容（含 `next.get()` 同步抛出），`refreshTokenAsync` 异常在链内收敛为降级，:354 future 必经 `bindResult` 完成（挂死面闭合）；MDC 沿既有 :352 绑定/:381-383 解绑位点零改动，刷新完成回调漂移到 HTTP 客户端完成线程不新增 MDC 挂接（按 `CTX-001` 对照成文，既有缺口非新增危害类别，C4-round3 1.3 注记兑现）；**NopApplication 式上下文传导不适用**——宿主为过滤器语义，适用框架是 `CTX-001` 契约对照而非启动链传导。降级语义 = 异常/超时/刷新 error → warn 日志 + 携既有 token 继续（与现行 error→null 一致，认证可见结果零变化，PA 边界锚点）；fail-closed（刷新失败拒绝请求）为 ask-first 触发条件，本批不采。本地 JWT `LoginServiceImpl.refreshToken` :954-960 零改造（default 包装下纯 CPU 微秒级重签无阻塞形态），回归绿核验。
2. **裁定②（C8-002 准入上界与时间整形）**：许可原语 = 平台 `ISemaphore`（`DefaultSemaphore` 实现，`nop-commons/concurrent/semaphore/`）——非阻塞 `tryAcquire(1, 0)`（timeout=0 立即失败无 park——`ISemaphore` 无专用无参 tryAcquire，调用形态钉死防误用带超时阻塞语义；draft review Minor-5）、自带 acquire 成败计数（准入拒绝可观测面顺带闭合，C8-006 相邻面零成本受益）、平台既有并发上界原语不重复造轮子；显式拒绝 `IRateLimiter`（速率维度非并发维度，许可按速率回填无法表达「在途 ≤ N」不变式）、`HighWatermarkSemaphore`（迟滞双水位面向过载后恢复节流，与快速拒绝的精确上界不变式不符）、裸 JDK `Semaphore`（无计数面，平台设施在位）。**准入面 = url 路由腿**：`invokeUrl` 入口在 URL 求值与全部同步抛出点（:124-126/:130-132）之后 tryAcquire，主腿含 429/5xx 重试挂起全程持有；`tryFallbackUrl` :111-145 入口逐腿准入（当前腿释放后下一腿准入），与主腿共享同一许可池；`invokeSource`（XPL）/`invokeRpc` **不入池**——非 HTTP 上游腿，无 C8-002 的上游持有面，为显式边界声明。释放契约 = 全出口：tryAcquire 后的同步段以 catch/finally 释放（覆盖 `buildHttpRequest` :242-256 同步抛出等残余窗，防许可泄漏），异步链以 `whenComplete` 释放（完成/异常/取消三出口统一，未来断连取消经此自动触发释放，无需预挂接）。超限 = 类型化快速拒绝（`GatewayErrors` 新错误码，NopException + `.param`，英文消息），不排队不静默丢弃；新码**不可重试钉** = `AiFailoverGatewayInterceptor` :84-88 可重试分类是白名单（仅 `ERR_GATEWAY_UPSTREAM_429` :50/`ERR_GATEWAY_UPSTREAM_FAILED` :53 两码），新准入码不入白名单即天然不被 fallback 级联重试，并以回归钉固化（准入拒绝 → 零 fallback 上游调用），防未来白名单扩张引入准入拒绝级联。时间整形 = Retry-After **钳制后值**施加 equal jitter：`delay/2 + U(0, delay/2)`（保留 ≥delay/2 的上游恢复窗并破同步重试波），施加位点在 `parseRetryAfter`/`capRetryDelay` 返回之后、`delayedFuture` 之前（`parseRetryAfter` 七例反射钉零触碰）；jitter 源单一可注入（构造注入，默认语义 = 既有 `Math.random` 形态），5xx 分支既有 U(0,1000)（:163）迁移至同一注入源且分布零变化——429 分支为「尊重上游指示的有界抖动」、5xx 分支为「指数退避自带均匀抖动」，共享源而分布不同，关系成文；既有 30s 钳制（:208/:235-237）与 `parseRetryAfter` 七例语义零改动。配置键（落 `GatewayConfigs`）：`nop.gateway.upstream-max-inflight` **默认 1000**（平台并发上界惯例值——`nop.graphql.subscription.max-active` 默认 1000 同款；内存包络 = 1000×S×(3~6)，S=10KB 时 ≈36-60MB 封顶；显著高于正常网关在途并发[worker 池 max(8C,200)]，默认档不误伤合法流量），≤0 = 不设上界档（显式回退现行无界行为，供特殊部署豁免）；`nop.gateway.upstream-max-retries` 默认 3（现行为保持），≤0 = 零重试（每腿单发）——重试次数必须有限，不存在「无上界」档，与 inflight 键 ≤0 语义的差异成文。OOM 轨迹消除数学：在途 url 腿数 ≤ P（许可数），与到达率 λ 和持有时长 H 解耦——Little's law 的 C=λ·H 无界增长被许可数截断；每在途持有 ≈ S×(3~6)（body String + JSON 解析树 + context，C8 复核精化系数），稳态内存上界 = P×S×(3~6) 与 H 无关；被拒请求 O(1) 释放不持有 body，≈90s 挂起窗不再线性累积内存。装配 = `GatewayHandler` :76 构造适配传入许可池（单一共享对象，组装层创建），fallback 拦截器经 setter 注入同一实例（`gateway-defaults.beans.xml` property 接线），生产侧无绕过准入的旁路构造。
3. **裁定③（测试缝与观测缝）**：C4-007 侧 = fake `IHttpClient`（可控 future：可编程完成时刻/异常/状态码）经 `OAuthLoginServiceImpl` 的 protected `httpClient` 字段同包注入（`TestOAuthLoginServiceImplContract` Harness 直填 `keyLocator` 为既有先例，零新增 setter）+ `req.timeout` 捕获断言（修复前 timeout=0 红 → 修复后 = 配置键值 + ≤0 档回落断言）+ `handleUserContext` 刷新支路执行链测试（C10-004 缺口本体）+ 链序断言（`next.get` 严格后于刷新完成，防未来重构成并行的保持性回归钉）+ 降级断言（刷新失败 → 下游继续 + 无头/cookie 更新 + warn）。载体不钉观测 = fake future 在载体线程返回后延迟完成 + 线程身份断言；N4 注记 = fake executeBlocking 同步内联会掩蔽载体维度，夹具须显式声明载体缝（记录执行线程名 + 走真实 runOnContext 异步路径），禁以同步内联伪装绿。C8-002 侧 = **新建独立可控桩**（定夺：不扩展 `RecordingHttpClient`——其为 `AiFailoverGatewayInterceptorTest` :103 的 private static final 内部类不可继承，放宽可见性即触碰既有零回退文件；新建桩零触碰既有 5 用例）——能力 = 可控挂起 future + Atomic 在途计数 + 请求录制，新准入/挂起/在途测试全部落新桩，Phase 2 不得临场发明第三种缝；既有 `RecordingHttpClient` 与 `InvokeProcessorRetryTest` 内联 mock 原样保留（其断言即零回退门禁）；seeded jitter 源 = 裁定②注入源（首波重试时刻非单点的分布断言）；准入拒绝断言（permits 耗尽 → 零上游调用 + 类型化错误码）+ 在途计数全程 ≤ permits 断言（挂起窗内持有）+ 主腿 1+maxRetries 重试计数断言 + 4+3 加法组合级断言（补 C10-006 缺口）。E2E 归属：链路①（SSO 刷新）= **nop-auth-sso 测试树**（真实 `OAuthLoginServiceImpl` + fake IHttpClient，`filterAsync` 入口 → 半衰期判定 → 异步刷新撞 brownout → 载体不阻塞 → 降级语义 → `next.get` 下游输出，出口点 = 客户端可观测响应/头状态）；nop-biz-auth-core `TestLoginService` fixture 走 default 方法不触达真实链，不得作为链路①证据（draft review Minor-3/6 兑现）。链路②（429 风暴）= **nop-service-framework/nop-gateway 测试树**（N>permits 并发 429 → 在途有界 → 超限类型化拒绝 → `GatewayHttpFilter` 错误响应可达客户端）。
4. **裁定④（边界声明）**：R2-16（roadmap 8.25 会话/配额取消束）零承接——认证会话语义本批零改动，仅按裁定①串行序消费共享实例；G36 面全部归 R4-16+R4-17（roadmap 8.56）：C3-010（断连取消传播，前置 CL-025 平台原语缺失，`IHttpServerContext` 无断连观测 API）、C4-003（幽灵重试/挂起治理/`buildHttpRequest` :242-256 与 `tryFallbackUrl` 上游请求超时缺失面——draft review Minor-5 补登记）、C4-004（executeBlocking 载体/nop-auth 专用有界执行器）、C4-008（JWKS 定位器同型钉线）——R4-16 行明示软依赖 8.14，本批 SSO 异步面与准入闸为其承载前提，落地后作完成注记；本批许可闸与未来断连取消原语**正交组合**（准入拒绝为服务端主动快速失败，不依赖断连观测；取消传播落地后经 `whenComplete` 自动释放许可，无需本批预挂接、后续零返工）；C3-003 面归 R2-06（roadmap 8.15 流式缓冲批 A，`BufferedStreamingPublisher` 本体与本批触达文件集零交叠）；Redis MFA/SMS store syncGet 族（C4-007 F3⑦，站点在 nop-auth-service 五模块外）移交线索维持。零回退清单：`InvokeProcessorRetryTest`（parseRetryAfter 七例 :36-81 + 429/5xx 耗尽两例 :163/:182 + 成功例 :201）、`AiFailoverGatewayInterceptorTest` 全部 5 用例、`testIsNeedRefreshHalfLife` :150 三态钉、nop-auth-sso 既有 4 测试类（TestJwkKeyParsing/TestJwtHelper/TestJwkErrorNormalization/TestOAuthLoginServiceImplContract）、`TestGatewayHttpFilterErrorResponse`。

## 二、背景与动机

R2-05 收敛 WI 2.3 两项 P1（merge-adjudication 分别为 G-C auth 认证链族主案与 G-B 网关重试链族成员），缺陷域两个：

- **SSO 半衰期刷新钉线（C4-007，CL-016 终裁立案，勘误 9 后 brownout 形态 P1 维持）**：SSO 部署（`nop.auth.sso.enabled=true` 单属性一等公民形态，sso-defaults.beans.xml :15-21 条件装配）下 `autoRefreshToken` 默认开启（:70-75），token 剩余寿命低于半衰期（:320-329）即触发刷新——稳态约半数请求落在刷新窗口、每会话半衰期一次。触发时序：认证请求 → `executeBlocking` 载体（无隔离全局 worker 池）→ `handleUserContext` :357 runOnContext 任务体 → :359 同步调 `loginService.refreshToken` → `OAuthLoginServiceImpl.refreshToken` :258 `FutureHelper.syncGet` 阻塞 IdP HTTP POST（`newRefreshTokenRequest` 无显式超时，默认部署有界 ≈30s）→ 阻塞期间该线程持有 context 任务队列 processingThread 角色，本请求后续任务全部串行化在 RTT 之后；IdP 实测失败返回 null 后不更新 cookie/不降频——故障期间刷新窗口内每请求重复阻塞，并发半衰期请求级联钉满载体池，全站吞吐坍缩至 ≈max(8C,200)/30s（确定性服务级停摆）。附带挂死面：刷新抛异常时 :374 从未执行、:354 future 永不完成（请求无响应）。契约层根因：`ILoginService.refreshToken`（:123）同步契约使过滤器只能同步调用，`ILoginSpi.refreshTokenAsync`（:59）为错误层闲置 SPI（勘误 7 不可复用，CL-023 语义错位）；本地 JWT 对照 `LoginServiceImpl.refreshToken` :954-960 纯 CPU 微秒级无害——缺陷在远程实现的 syncGet 形态而非契约存在本身。
- **Gateway 429 重试放大无准入（C8-002，与 C4-003 承重柱独立——C8 复核终裁 2）**：主链 1+3 重试（maxRetries=3 局部硬编码 :135）+ fallback ≤3 单发（`tryFallbackUrl` 直连 `fetchAsync` 绕过 InvokeProcessor 重试链）= ×7 加法放大（勘误 1 终裁，无乘法）；429 按 Retry-After 退避被 30s 钳制，单请求挂起持有完整 body String+JSON 解析树 ≈90s+；在途并发 C=λ·H 随到达率线性增长且模块内零准入上界（grep Semaphore/RateLimiter/admission 零命中，`GatewayConfigs` 仅 model-path 一键）——λ=1000/s、S=10KB、H=45s 轨迹 ≈2.7GB 且随 λ 线性无上界，高 QPS 下确定性 OOM；同值 Retry-After 使全部被拒请求同步齐发重试（无 jitter 重试波，恰好打击上游要求停止的时段，反向背压失效）。OOM 轨迹独立于线程语义（C8 复核终裁 2，C4-003 降 P2 事由「单线程占用被证伪」是另一柱），故准入上界/时间整形归本批、挂起与载体治理归 R4-16。

**成员台账与前置事实**：cra-index P1 台账 2 行（`nop-service-framework-hotpath-C4-007` L209 / `-C8-002` L211，裁定批次均 R2-05，全部 open）；C10 验收前置 C10-004/C10-006 均整条入批随本体收敛（无共享条目拆分）；G36 族归属（merge-adjudication §2）：C4-007 = G-C auth 链族主案（C4-004/C4-008 相邻面归 R4-16），C8-002 = G-B 网关重试链族成员（与 C4-003 承重柱不同）；G-B 家族登记事实 = `IHttpServerContext` 无断连观测 API（CL-025），取消挂接归 R4-16，本批准入拒绝不依赖断连原语、两修复面正交。前批同域产物：plan 2498 WS 写异步化泵（service-layer.md :335 节）、plan 2499 上下文传播契约（:343 节 + `CTX-001` 锚点）为过滤器异步化后的对照框架。

前批边界：plan 2505（R2-02）确立服务热路径「设计文档 + 独立 draft review 门禁」形态，本批沿用其纪律；plan 2504 的 LOCK-001 两档配置语义（默认有界 + ≤0 豁免档）为本批新配置键的语义模板。

## 三、核心设计

### 3.1 裁定①：SSO 刷新异步化（C4-007）

#### 3.1.1 契约形态与 SPI 回归面全列

契约落点为 `ILoginService` default 方法（纯新增，接口位于 nop-biz-auth-core 非 `nop-*-api` 模块，跨模块公共 API PA 零触碰）；default 实现回退既有同步方法，语义为「未 override 的实现保持现行同步行为」——过滤器侧异步化不依赖全部实现同步升级。SPI 回归面 live 全仓重枚举 8 面（3 main + 5 fixture：4 个直接实现 + 1 个 extends LoginServiceImpl 继承转承面，较批次输入「4 测试 fixture」口径增补 1 个继承面，勘误性质登记）：

| # | 面 | 模块 | 兼容形态 |
|---|---|---|---|
| 1 | `AbstractLoginService`（抽象基类） | nop-biz-auth-core main | 不 override，经 default 回退 |
| 2 | `LoginServiceImpl`（本地 JWT） | nop-auth-service main | 零改造，回归绿核验 |
| 3 | `OAuthLoginServiceImpl`（SSO） | nop-auth-sso main | override 纯异步链（本案本体） |
| 4 | `TestAuthHttpServerFilterSemantics.TestLoginService`（:419，:478 refreshToken） | nop-biz-auth-core test | 走 default，不触达真实链 |
| 5 | `TestAbstractLoginServiceSemantics.TestLoginService`（:275/:297） | nop-biz-auth-core test | 同上 |
| 6 | `TestRevokeUserSessions.MinimalLoginService`（:127/:159） | nop-auth-service test | 同上 |
| 7 | `TestLoginApiTokenWiring.RecordingLoginService`（:101/:186） | nop-auth-service test | 同上（其既有 `refreshTokenAsyncUsesParseRefreshToken` 用例测的是 `ILoginSpi`/`LoginApiBizModel` 面，与本 default 同名异物，防混淆注记） |
| 8 | `TestLoginRateLimitAndAudit.TestableLoginService`（:49，extends LoginServiceImpl） | nop-auth-service test | 继承面，经 2 转承 |
| 9 | `TestInjectValuePureJvmDefaults.AbstractLoginServiceForTest`（:41，refreshToken :89-91） | nop-biz-auth-core test | 走 default（draft review Major-1 补行） |

`ILoginSpi`（:59 闲置 SPI，勘误 7 终裁不改造）、`nop-auth-api` LoginApi、nop-api-core/nop-http-api 接口签名零触碰；`LoginApi__refreshToken` 无轮换语义（CL-023）不得作为刷新通道复用。

#### 3.1.2 超时键与接线（draft review M1 闭合）

超时键 `nop.auth.sso.refresh-token-timeout` 默认 10000ms（audit 建议 5-10s 档取上界：IdP token 刷新为轻量 POST，正常 RTT 50-500ms，10s = 20-100× 正常 RTT 余量且低于客户端默认 30s；异步化后其角色为限制挂起时长与 stale-serve 恢复延迟）；≤0 = 不调用 `req.timeout`（保持 `HttpRequest` 字段默认 0 = 客户端部署级默认，readTimeout 30s 兜底），落点 = `newRefreshTokenRequest` 单行设置。接线 = `sso-defaults.beans.xml` `nopSsoConfig` bean 增补一行 `@cfg:` property（默认分支随行内默认值落地），`SsoConfig` 增设同名字段默认 10000（与 `@cfg:` 默认一致防双源漂移）；rule-23 配置覆盖真实生效断言 = 捕获 `newRefreshTokenRequest` 产出的请求超时值等于配置覆盖值（防死配置键空心面）。

#### 3.1.3 过滤器链序契约（thenCompose 严格领先）

修复后 `handleUserContext` 的执行契约：runOnContext 任务体发起异步刷新链 → 刷新终态（成功/error→null/异常→降级三路一致）→ **终态续段经 `ctx.runOnContext` 再入上下文任务队列执行**（再入调用自身受链内 try/catch → completeExceptionally 守护，含 `BaseContext.checkClosed()` :229 链内同步抛出——draft review R2 补强）（draft review Major-3 执行介质契约：设头/cookie 步与 `next.get()` 若在刷新完成线程[HTTP 客户端回调线程]直执，脱离 `BaseContext.runOnContext` :228-245 的 contextHolder 绑定，下游 biz/graphql 的 `ContextProvider.currentContext()` ThreadLocal 读丢失上下文——功能回归；再入队列保持现行执行介质零迁移，且规避 routeContext setResponseHeader/addCookie 跨线程写未论证问题）→ 设头/cookie 步（仅刷新产出非 null 时，:362-365 语义不变）→ `thenCompose` 串行接入 `next.get()` → 链尾 `bindResult`（:377 位点不变）+ 既有 `flushUserContextAsync` whenComplete（:374-376 保留）。序约束依据：同会话并发请求经 `AbstractUserContextCache.getUserContextAsync` :103-120 取得**同一** `UserContextImpl` 实例，刷新对其的变异必须对下游可见（串行组合路径内变异，禁止并行）——该共享实例亦是按会话聚合 successor（Deferred）的天然合并点。异常包​​容契约：任务体内全部同步与异步异常收敛于 future 终态（降级映射或 completeExceptionally 透传下游失败），:354 future 无「永不完成」路径；既有 else 分支的 cookie 一致性补写（:367-371）保持同步语义不变。

#### 3.1.4 降级语义与 PA 边界

降级 = 异常/超时/刷新 error → warn 日志 + 携既有 token 继续（stale-serve）：与现行 error→null 行为一致，认证可见结果零变化——这是 PA 裁定「执行形态改造非认证语义变更」的锚点分支。fail-closed（刷新失败拒绝请求）= ask-first 触发条件，Phase 2 实施中若发现必须采该分支方可落地，停止并按 ask-first 询问（本设计一次定夺为不采，正常路径无此需要）。

### 3.2 裁定②：准入上界与时间整形（C8-002）

#### 3.2.1 准入契约（双落点共享池）

| 落点 | 准入时机 | 持有窗 | 释放 |
|---|---|---|---|
| `InvokeProcessor.invokeUrl` 入口 | URL 求值与同步抛出点之后（httpClient 缺失/URL 为 null 两同步拒绝路径不沾许可） | 主腿全程：首发 + 429/5xx 重试挂起窗 + 终态响应组装，单许可 | 同步段 catch/finally + 异步链 whenComplete |
| `AiFailoverGatewayInterceptor.tryFallbackUrl` 入口 | 每条 fallback 腿发起前 | 单腿单发时长 | 同步段 catch/finally + whenComplete；链推进时当前腿释放后下一腿准入 |

**许可池装配与既有测试构造适配（draft review Minor-4）**：InvokeProcessor 构造必须携带许可池、无旁路构造；`InvokeProcessorRetryTest` 4 处 `new InvokeProcessor(null, …)`（:30/:164/:183/:202）随许可池参数编译破裂——**定夺 = 保留旧两参签名重载构造**（仅测试语义、内部以无上界池装配并 javadoc 标注非生产路径），既有测试断言与钉零触碰面最小。

在途不变式：url 路由腿数（主腿含挂起）+ 活跃 fallback 腿数 ≤ P。挂起不释放的依据：挂起窗（`delayedFuture` 等待）正是 body 解析树的内存持有窗，释放许可即重开 OOM 轨迹。准入拒绝优先于重试发起：腿入口无许可即类型化拒绝，不发起任何上游调用。**拒绝传播形态按落点分别钉死（draft review Major-2）**：主腿（`invokeUrl` 入口）沿既有同步 throw 通道（`RouteExecutor` :160 → `processRoute` try/catch → failedFuture 已核实安全）；**fallback 腿拒绝必须以 `CompletableFuture.failedFuture` 返回**（tryFallbackUrl 的 tryAcquire 位于 `tryInvokeWithFallback` 内层 whenComplete 回调体中——回调体内同步 throw 只完成被丢弃的 stage、外层 :75 future 永不完成，即与本批 C4-007 :354 同型的请求永久挂死；InvokeProcessor :124-126/:130-132 同步 throw 先例不适用于回调体语境）；验收断言含「fallback 腿拒绝经 failedFuture 传播至外层 future 终态」；重试复用主腿已持许可，不二次准入。超限拒绝的响应路径复用 `GatewayHttpFilter` 既有错误响应消费面，出口点 = 客户端可观测类型化错误。

准入面边界声明（持久契约而非实现省略）：

- `invokeSource`（XPL 进程内求值）与 `invokeRpc`（经 `IRpcServiceInvoker`，RPC 层流控归其自有设施——断言性声明，锚点待 RPC 域批次补证）不入池——两者无 C8-002 的上游 HTTP 持有面。
- 流式路由腿不入池：`RouteExecutor` 对流式路由分发至 `StreamingProcessor`（:127，独立于 InvokeProcessor 的执行路径），其缓冲/重试预算面归 R2-06（C3-003 族）/R4-16；fallback 拦截器对流式模式本就显式拒绝（:61-64），fallback 腿天然全为非流式。
- 直连 `fetchAsync` 的其他消费方（本批触达集之外）不在该许可池治理范围；池的所有权边界 = InvokeProcessor url 腿 + fallback 腿两落点，后续新增上游腿消费方须显式接入并复核在途不变式。

#### 3.2.2 时间整形契约（equal jitter 与零回退）

429 分支：`parseRetryAfter`（含 30s 钳制）返回确定值 D 后，实际挂起 = D/2 + U(0, D/2)（equal jitter，源可注入）——同值 Retry-After 的 N 个被拒请求首波重试时刻散布于 [D/2, D) 而非单点，上游恢复窗 ≥D/2 保持尊重语义；D=0 时整形值恒 0（既有 `Retry-After: 0` 耗尽测试零变化）。5xx 分支：既有 U(0,1000) 迁移至同一注入源、分布零变化。两分支不合并公式：429 是对上游显式指示的保留性抖动，5xx 是自选退避的自带抖动——共享注入源而分布不同，迁移使全分支可 seed 测试且不构成行为变更。

#### 3.2.3 配置键契约

| 键 | 默认 | ≤0 档 | 语义 |
|---|---|---|---|
| `nop.gateway.upstream-max-inflight` | 1000 | 不设上界（显式回退现行无界行为，特殊部署豁免档） | 主腿+fallback 共享许可池容量；默认值理由：平台并发上界惯例值（`nop.graphql.subscription.max-active` 默认 1000 同款）+ 内存包络封顶（1000×S×(3~6)，S=10KB ≈36-60MB）+ 显著高于正常在途并发（worker 池 max(8C,200)）不误伤 |
| `nop.gateway.upstream-max-retries` | 3 | 零重试（每腿单发） | 主链重试上限，替换 :135 局部硬编码；重试次数必须有限，不存在「无上界」档——与 inflight 键 ≤0 语义的差异成文 |

#### 3.2.4 OOM 轨迹消除数学陈述

缺陷形态：在途 C = λ·H（Little's law），brownout 下 H→90s 且 λ 无平台约束 → C 无界，稳态内存 ≈ C×S×(3~6) 随 λ 线性无上界。修复形态：准入闸截断 C ≤ P（url 腿数上界，与 λ、H 双双解耦）；每在途持有 ≈ S×(3~6)（body String + JSON 解析树 + context，C8 复核精化系数）；稳态内存上界 = P×S×(3~6) 与 H 无关——挂起再久不再累积内存；被拒请求 O(1) 释放不持有 body（拒绝发生在 InvokeProcessor 层，响应即达后 filter lambda 作用域退出）。示例：P=1000、S=10KB → 上界 ≈36-60MB 恒定，brownout 时长不再进入内存公式。

#### 3.2.5 与 Retry-After / 取消原语的关系

Retry-After 语义尊重不变（钳制后仍按上游指示量级延迟，仅施加有界抖动）；准入许可覆盖重试挂起窗；与未来断连取消（C3-010/CL-025，归 R4-16）正交——许可释放挂在 `whenComplete` 全出口上，取消引发的异常完成自动触发释放，本批零预挂接、后续零返工。

### 3.3 裁定③：测试缝与观测缝（C10-004/C10-006 承载）

| 成员 | 测试缝（Phase 2 唯一许可缝，不得临场发明） | 观测方式 |
|---|---|---|
| C4-007 | fake `IHttpClient` 可控 future（protected 字段同包注入）+ seeded 无需（时序由 future 控制点承载）+ 载体缝显式声明（N4：禁同步内联伪装） | `req.timeout` 捕获断言、链序断言（`next.get` 严格后于刷新完成）、降级断言（warn + 下游继续 + 无头/cookie 更新）、线程身份断言（fake future 延迟完成，载体不钉） |
| C8-002 | 新建独立可控桩（可控挂起 future + Atomic 在途计数 + 请求录制）+ seeded jitter 注入源 + 既有 `RecordingHttpClient`/内联 mock 原样保留 | 准入拒绝断言（零上游调用 + 类型化码）、在途 ≤ permits 全程断言、主腿 1+maxRetries 计数、4+3 组合断言、首波重试时刻分布断言 |

E2E 链路归属与证据约束见一、设计结论第 3 条末（链路① = nop-auth-sso 测试树真实 `OAuthLoginServiceImpl` 链；链路② = nop-gateway 测试树）。

### 3.4 裁定④：边界声明与零回退清单

| 边界项 | 处置 | 依据 |
|---|---|---|
| R2-16（roadmap 8.25 会话/配额取消束） | 零承接：认证会话语义本批零改动，仅按裁定①串行序消费共享实例 | plan Non-Goals |
| C3-010（断连取消传播）+ CL-025（断连观测原语） | 归 R4-16；本批准入拒绝为服务端主动快速失败，与断连原语正交；取消落地后经 whenComplete 自动释放许可，零预挂接 | G-B 家族事实登记（C4-round3 ②.2：`IHttpServerContext` 断连 API grep 零命中） |
| C4-003（幽灵重试/挂起治理/上游请求超时缺失面：`buildHttpRequest` :242-256、`tryFallbackUrl` req.timeout） | 归 R4-16；本批超时键两档语义可作其配置键设计输入；C4-003 挂起治理以本批许可闸后的 live 形态为基线 | draft review Minor-5 补登记 |
| C4-004（executeBlocking 载体/nop-auth 专用有界执行器）、C4-008（JWKS 定位器同型钉线） | 归 R4-16（C4-008 与 C4-007 同批规划的 audit 建议按批次安排裁断为 G-C 族后续批） | merge-adjudication G-C 族表 |
| C3-003（流式缓冲） | 归 R2-06（roadmap 8.15）；`BufferedStreamingPublisher` 本体与本批触达文件集零交叠 | plan Related 行 |
| Redis MFA/SMS store syncGet 族（C4-007 F3⑦） | 移交线索维持（站点在五模块外 nop-auth-service Redis store 8 处） | 审计移交轨 |
| 按会话聚合刷新去抖 | Deferred（optimization candidate，无 successor 必备）：三件套已闭合 P1 承重柱；共享实例天然合并点事实为 successor 设计输入 | plan Deferred 节 |

零回退清单五组钉原样保留：`InvokeProcessorRetryTest`（parseRetryAfter 七例 :36-81 + 429/5xx 耗尽两例 :163/:182 + 成功例 :201）、`AiFailoverGatewayInterceptorTest` 全部 5 用例、`testIsNeedRefreshHalfLife` :150 三态钉、nop-auth-sso 既有 4 测试类（TestJwkKeyParsing/TestJwtHelper/TestJwkErrorNormalization/TestOAuthLoginServiceImplContract）、`TestGatewayHttpFilterErrorResponse`。钉语义相关的零改项（`parseRetryAfter`/`MAX_RETRY_DELAY_MS`/耗尽断言参数/fallback 上界逻辑/半衰期判定）以 git diff 为空核验。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| 过滤器侧对无异步能力实现做降级包装（备选①a） | 过滤器无法感知实现的真实异步能力：SSO 实现的异步形状会被再包回同步、syncGet 面原样保留；在错误层补异步能力已有 `ILoginSpi` 错误层先例（勘误 7/CL-023 三重错位），契约层 default 方法是唯一正确层 |
| fail-closed（刷新超时/失败拒绝请求）（备选①d） | 认证可见结果变更 = PA 边界 ask-first 触发条件；stale-serve 与现行 error→null 语义一致、认证结果零变化，降级是唯一零语义迁移分支 |
| 复用 `ILoginSpi.refreshTokenAsync` 作刷新通道 | 错误层（对外 API 面）/错误 bean（LoginApiBizModel）/错误语义（无令牌轮换）三重错位，勘误 7 终裁 + CL-023 移交维持 |
| `IRateLimiter` 作准入原语 | 速率维度非并发维度：Guava 形态许可按速率回填，表达不了「在途 ≤ N」不变式；准入需要的是许可计数非吞吐配额 |
| `HighWatermarkSemaphore` 作准入原语 | 迟滞双水位语义面向「过载后恢复节流」，会在负载回落后仍持续拒绝至低水位——人为延长拒绝期，与快速拒绝的精确上界不变式不符 |
| 裸 JDK `Semaphore` 作准入原语 | 无 acquire 成败计数面，准入拒绝可观测性需另建；平台 `DefaultSemaphore` 已在位（不重复造轮子） |
| 有界排队替代快速拒绝 | 排队即内存持有（每排队请求持有 body 解析树），OOM 从在途维转移到队列维；快速拒绝 O(1) 释放，内存上界确定性成立 |
| 429 改零重试（audit 建议「不做服务端重试」） | 超出批次裁定修复方向（batch-proposal L79 = 准入上界/时间整形/配置化三件）；重试保留为可配置能力（默认 3 现行为保持），放大危害由准入闸截断——行为变更面最小 |
| jitter 施加进 `parseRetryAfter` 内部 | 七例反射钉的断言对象即该方法返回值，内部施加即破坏零回退门禁；施加点上移至调用侧使「钳制语义钉」与「整形行为」解耦 |
| 扩展 `RecordingHttpClient`（继承或放宽可见性） | private static final 内部类不可继承，放宽可见性即触碰零回退文件；新建可控桩对既有 5 用例零触碰 |
| 按会话聚合刷新去抖（C4-round2 建议④） | Deferred（plan 已裁定）：三件套已闭合 P1 承重柱，聚合为 P2 流量优化；共享实例合并点事实已登记为 successor 设计输入 |
| 与 C4-004 专用执行器合并（C4-round2 建议⑤） | 归 R4-16（batch-proposal 显式批次安排）；本批契约异步化后刷新不再占用执行载体，专用执行器是正交的载体治理面 |

## 五、与已有设计的关系

- 同目录 [bindcancel-context-unification.md](bindcancel-context-unification.md)：`CTX-001` 契约为裁定①完成线程漂移语义的对照框架（MDC/上下文缺口按其口径定性为既有非新增危害类别）；R1-01/R1-02 改造面零触碰。
- 同目录其余九篇与本案零共享代码位（本批触达 nop-biz-auth-core/nop-auth-sso/nop-gateway，均无既有并发设计覆盖）；测试纪律契约（§七）沿用 kernel 先例的夹具纪律。
- plan 2504 LOCK-001 两档配置语义为三个新配置键的模板（`refresh-token-timeout`/`upstream-max-inflight` 沿「默认有界 + ≤0 豁免档」；`upstream-max-retries` 的 ≤0 = 零重试，差异已声明）。
- plan 2498/2499 先例：service-layer.md :335（WS 写异步化）/:343（上下文传播契约）两节与本批 Phase 3 新节并列，default 方法零签名扩展沿 2499 形态。
- **下游协同**：R4-16/R4-17（8.56）为 G36 面唯一 successor，本批产物（SSO 异步面 + 准入闸）为其软依赖承载前提，落地后作完成注记，C4-003 挂起治理以本批许可闸后的 live 形态为基线；R2-06（8.15）相邻批次文件集零交叠；R2-16（8.25）零承接。
- **owner doc 去向预登记（Phase 3 落账）**：`docs-for-ai/02-core-guides/service-layer.md` 新建「认证过滤器 token 刷新与网关重试准入并发契约」节（内容源 = 本文档，派生摘要标注「以设计文档为准」）；`docs-for-ai/04-reference/source-anchors.md` 扩展 AUTH-003 + 新增网关准入锚点（按实际）。

## 六、成员 → 裁定映射

| 成员（判级/台账） | 面 | 裁定 | 修复位（live : 行） | 回归测试（预定名） |
|---|---|---|---|---|
| 2.3-C4-007（P1，CL-016 立案） | SSO 半衰期刷新载体钉线 + 无显式超时 + :354 挂死面 | ①③ | AuthHttpServerFilter :342-384（:359/:374/:354）；OAuthLoginServiceImpl :247-285（:258 syncGet 剥离/:270-285 timeout）；ILoginService :123（default 新增）；sso-defaults.beans.xml :15-21/:23-41（@cfg: 行） | TestSsoRefreshAsyncChain（nop-auth-sso，E2E 链路①）+ TestAuthHttpServerFilterRefreshChain（nop-biz-auth-core，链序/降级/载体缝） |
| 2.3-C8-002（P1） | 429/5xx 重试放大 ×7 + 在途无界 OOM 轨迹 + 同步重试波 | ②③ | InvokeProcessor :135-167/:198-237；AiFailoverGatewayInterceptor :73-145（:84-88 白名单钉）；GatewayConfigs/GatewayErrors/GatewayHandler :76 | TestGatewayUpstreamAdmission（nop-gateway，准入拒绝/在途上界/重试计数/4+3 组合/jitter 分布/E2E 链路②） |

并案归属：C10-004（C4-007 单成员）随裁定①测试同 diff；C10-006（C8-002 单成员）随裁定②测试同 diff——均整条入批无拆分（对照 2507 勘误 6 拆分形态，本批无需拆分）；勘误 1 门禁 `./mvnw test -pl nop-auth -am` 随 C4-007 变更集执行；既有反向验证钉（testIsNeedRefreshHalfLife 三态/parseRetryAfter 七例/重试耗尽两例/fallback 上界例）列为零回退门禁随两组测试守护。

## 七、测试纪律契约

夹具纪律沿用 kernel 先例 §七：daemon 夹具线程 + latch 对齐 + 有界观察窗 + @Timeout（防挂死形态测试自身挂死构建）；禁仅类型/修饰符存在断言（「方法已 default」「已传入许可池」不构成通过形态），每件必含修复后可观测行为断言。

| 成员 | 修复前红形态（可观测） | 修复后绿形态（可观测） |
|---|---|---|
| C4-007（红①钉线/挂死/无超时 + 红②链序/超时档） | fake IdP 挂起下：载体线程被钉住（线程身份滞留断言）、:354 future 超窗不完成（请求挂死）、捕获刷新请求 timeout==0；异常注入下任务体逃逸（下游永不执行） | 载体线程即时返回、刷新完成回调在非载体线程驱动下游（线程身份断言）、timeout==配置值（≤0 档回落断言）、异常注入 → warn + 下游继续 + 无头/cookie 更新（stale-serve 钉）、`next.get` 严格后于刷新完成（链序钉） |
| C8-002（红①在途无界 + 红②同步重试波） | permits=N 耗尽后第 N+1 并发腿照常发起并挂起（在途计数无界）；同值 Retry-After ×N 首波重试时刻单点（齐发）；主腿重试次数无计数钉 | 第 N+1 腿零上游调用 + 类型化拒绝错误码；在途计数全程 ≤ permits（挂起窗内持有）；seeded jitter 首波时刻非单点；主腿恰 1+maxRetries 次 + 配置覆盖生效 + 4+3 加法组合断言 |

端到端两链路（rule 22）：①SSO 刷新链——`filterAsync` 入口 → 半衰期判定 → 异步刷新撞 brownout（fake IHttpClient 可控 future 构造，无需真实网络端点）→ 载体线程不阻塞 → 降级语义 → `next.get` 下游输出，出口点 = 客户端可观测响应/头状态（归属 nop-auth-sso 测试树；nop-biz-auth-core `TestLoginService` fixture 走 default 方法不触达真实链，不得作为链路①证据）；②429 风暴链——N>permits 并发 429 → 在途有界 → 超限类型化拒绝路径 → 出口点 = `GatewayHttpFilter` 错误响应可达客户端（归属 nop-gateway 测试树）。接线验证（rule 23）：`GatewayHandler` :76 直构的 InvokeProcessor 真实携带许可设施（非测试直构自证）；`nopLoginService` 装配的 `OAuthLoginServiceImpl.refreshTokenAsync` 真实被过滤器调用（链路标志位/mock verify）；`nopAiFailoverGatewayInterceptor` fallback 腿准入接线断言；三个新配置键覆盖真实生效断言（含 sso 超时键经 beans.xml `@cfg:` 行的真实生效）。无静默跳过（rule 24）：准入拒绝类型化抛出而非静默吞/排队挂起；刷新降级 warn 日志 + 显式 stale-serve 而非空 catch；三键 ≤0 档语义显式成文而非 magic number；新增公共分支无空方法体/TODO。
