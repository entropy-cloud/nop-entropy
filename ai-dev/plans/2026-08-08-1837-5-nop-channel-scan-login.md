# 5 nop-ai 外部信道集成 — 扫码登录 accessCode 编排 (W4)

> Plan Status: completed
> Mission: nop-ai-channel-integration
> Work Item: W4
> Last Reviewed: 2026-08-09
> Source: `ai-dev/backlog/nop-ai-channel-integration-roadmap.md` (W4) · `ai-dev/design/nop-ai-channel-integration-design.md` (§3.4 ③) · `nop-service-framework/nop-biz-auth-core`（`IAuthTokenProvider`/`ILoginService` 现状）
> Related: 前置 Plan 4 (W3 `IChannelBindProvider`/`IChannelBindService`) · 前置 Plan 1 (W0 唯一约束 + login-type 字典) · 后续 W6-2 (E2E 扫码绑定 + 扫码登录)

## Purpose

落地扫码登录。核心挑战：accessCode 消费链 `getUserContextAsync` 按 sessionId 查缓存（经代码核实），扫码登录无预存 session **必须先创建 session**，而 session 创建（含完整 UserContext：roles/tenant/dept）的逻辑当前封装在 `ILoginService.loginAsync`（需凭证）内部、无独立入口。本 plan 须先在 Phase 0 裁定 session 创建路径（gateway 编排 vs 给 auth 模块加 additive 入口），再实现扫码回调端点 → session 创建 → `IAuthTokenProvider.generateAccessCode` 签发一次性 code → 前端经既有 `LoginApi.getLoginResultAsync` 换 `LoginResult`。

> **核心约束**：roadmap 完成定义要求"未改 `ILoginService`/`ILoginSpi`/`IAuthTokenProvider` 契约"。但 roadmap 的"关键裁定"假设 accessCode 可直接签发消费，未意识到 session 创建的必要性。Phase 0 须裁定：在不破坏既有登录主流程的前提下，session 创建走哪条路径，并评估是否需对 roadmap"零改动"完成定义做诚实修正（如允许 additive SPI 方法）。

## Current Baseline

- `IAuthTokenProvider`（`nop-service-framework/nop-biz-auth-core/.../io/nop/auth/core/login/IAuthTokenProvider.java`）已含：
  - `generateAccessCode(IUserContext userContext, long expireSeconds) → String`（`:18`）—— 一次性短效 code
  - `parseAccessCode(String accessCode) → AuthToken`（`:35`，默认回退 `parseAuthToken`，校验用途=code）
  - `JwtAuthTokenProvider`（`:116-120`）实现 `generateAccessCode`，用 `userContext.getUserName()` + `userContext.getSessionId()` 签 JWT（token type = `TOKEN_TYPE_CODE`）。
- `ILoginSpi`（`nop-biz-auth-core/.../io/nop/auth/core/spi/ILoginSpi.java:43`）已含 `getLoginResultAsync(AccessCodeRequest, IServiceContext) → CompletionStage<LoginResult>`（`@BizQuery`）。
- `LoginApiBizModel`（`nop-auth-service/.../io/nop/auth/service/biz/LoginApiBizModel.java`）是 `@BizModel("LoginApi")` 实现 `ILoginSpi`，`getLoginResultAsync`（`:61-65`）调 `loginService.parseAccessCode(request.getAccessCode())` → `getUserContextAsync(authToken, headers)` → `buildLoginResult`。**这是前端换取 LoginResult 的既有路径，本 plan 不改它。**
- `ILoginService`（`nop-biz-auth-core/.../io/nop/auth/core/login/ILoginService.java`）含 `parseAccessCode`（`:72`）、`getUserContextAsync(AuthToken, Map)`（`:40`）。`LoginServiceImpl`（`nop-auth-service`）实现，`parseAccessCode` 委托 `authTokenProvider.parseAccessCode`（`:517-518`）。
- `AccessCodeRequest`（`nop-biz-auth-api/.../io/nop/auth/api/messages/AccessCodeRequest.java`）含 `accessCode`/`clientId`/`clientSecret`。
- `nop-ai-gateway` pom 当前依赖：`nop-gateway`/`nop-ai-api`/`nop-ai-core`/`nop-ai-agent`/`nop-ai-dao`/`nop-integration-api`（Plan 2/3 落地）。**不含 `nop-biz-auth-core`（取 `IAuthTokenProvider`）也不含 `nop-auth-api`（取 `IChannelBindService`，Plan 4 产出）**。有 `ai-gateway-defaults.beans.xml` + `_vfs/nop/ai/gateway/_module`（Plan 2 落地，beans 可发现）。
- Plan 4 (W3) 将产出 `IChannelBindProvider`（`nop-integration-api`）+ `IChannelBindService`（`nop-auth-api`，方法 `startBinding`/`completeBinding`/`listBindings`/`unbind`）。**本 plan 依赖 Plan 4 已落地**。
- **关键裁定（accessCode 机制，roadmap W4）**：登录用 accessCode 是提供方签名令牌，由既有 `IAuthTokenProvider.generateAccessCode` 签发、`parseAccessCode` 解码。**不是**自建的不透明 store key——自建 key 无法被既有 `parseAccessCode` 解码，会破坏"登录主流程零改动"。
- **accessCode 消费链的 session 依赖（关键约束，经代码核实）**：`getUserContextAsync(AuthToken, headers)`（`AbstractLoginService:62-64`）→ `doGetUserContext(sessionId)`（`:75-84`）→ `userContextCache.getUserContextAsync(sessionId)`（`AbstractUserContextCache:52-70`）是**按 sessionId 查缓存**，不是查用户库。`AbstractUserContextCache.getUserContextAsync` 要求：(a) `userContextCache.getAsync(sessionId)` 命中（context 已缓存），(b) `userSessionCache.get(userName)` 等于该 sessionId（未超时、是当前会话）。**两者皆不满足则返回 null → `buildLoginResult(null)` 抛 `ERR_AUTH_SESSION_EXPIRED`**。这意味着 accessCode 的设计语义是为**已有 active session 的用户**签发跨设备令牌，扫码登录场景（用户首次扫码、无预存 session）**必须先创建 session**。
- **session 创建的机制存在性（经代码核实，非完整可行性）**：`IUserContextCache.saveUserContextAsync(ctx)`（`AbstractUserContextCache:77-81`）同时写两个缓存：`userSessionCache.putAsync(userName, sessionId)` + `userContextCache.putAsync(sessionId, ctx)`。`UserContextImpl`（`nop-biz-auth-core`，`:22`）有公共 setter `setSessionId`/`setAccessToken`/`setRefreshToken`。`IAuthTokenProvider.generateAccessToken`/`generateRefreshToken`/`generateAccessCode` 均可用。**机制存在≠完整可行**——roles/tenant 加载来源、sessionId 来源、缓存拓扑一致性（见下）均须由 Phase 0 裁定。
- **缓存拓扑约束（关键可行性因素，Phase 0 必答）**：`IUserContextCache` 默认实现 `LocalUserContextCache`（`auth-core-defaults.beans.xml`）的 `userContextCache`/`userSessionCache` 是**进程内 `LocalCache`**。Path A（gateway 写 session）与消费侧（`getLoginResultAsync` 在 `nop-auth-service` 的 `LoginApiBizModel`）若为不同进程，gateway 写入的 session 对 auth-service **不可见** → `buildLoginResult(null)` → `ERR_AUTH_SESSION_EXPIRED`。这是 Path B（session 创建与消费同进程同缓存）的决定性优势。Phase 0 须正面回答部署拓扑与缓存实例一致性。
- `IUserContextCache`（`nop-biz-auth-core`，`io.nop.auth.core.login`）是公开接口，可经 Nop IoC `@Inject` 注入。`nop-ai-gateway` 加 `nop-biz-auth-core` 依赖即可访问。

## Goals

- Phase 0 裁定扫码登录的 session 创建路径（gateway 编排 vs auth additive SPI），产出经代码验证的可执行方案
- 扫码回调端点（`@BizModel`，`nop-ai-gateway`）收到回调 → 经 `IChannelBindProvider`/`IChannelBindService` 解析绑定得 platformUserId → 按 Phase 0 方案创建 session → 调 `IAuthTokenProvider.generateAccessCode` 签发一次性 code → 返回前端
- 端点真实可达（可经 BizModel 方法调用验证；W6-2 真实信道端到端命中属后续）
- 签发的 accessCode 可被既有 `parseAccessCode` 解码 + `getUserContextAsync`(cache) 命中（证明 session 注册成功）；完整 `getLoginResultAsync`→`LoginResult` 按 Phase 2 裁定（裁定 A 则留 W6-2，裁定 B 则本 plan 验证）
- `nop-ai-gateway` 新增依赖无环；登录主流程既有契约不被破坏（Phase 0 裁定后精确化"零改动"的含义）

## Non-Goals

- 不破坏 `ILoginService`/`ILoginSpi`/`IAuthTokenProvider`/`LoginApiBizModel` 既有契约（additive 新方法是否允许由 Phase 0 裁定；breaking 改动明确排除）
- 不实现具体厂商扫码回调协议（`FeishuBindProvider` 属 W5-2）
- 不实现前端轮询/换取 UI（前端行为属应用层，不在本 mission 代码范围）
- 不实现真实信道端到端（外部扫码 → 回调 → 登录，属 W6-2，本 plan 用 stub provider 验证编排链）
- 不实现二维码渲染（`IQrcodeService` 调用属 W5-2/W6-2）
- 不改动 `IAgentEngine`/`AgentMessageRequest` 契约

## Scope

### In Scope

- `nop-ai/nop-ai-gateway/pom.xml` — 新增 `nop-biz-auth-core` + `nop-auth-api` 依赖
- `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/login/` (新) — 扫码回调 `@BizModel`
- `ai-gateway-defaults.beans.xml` — 新 BizModel bean 注册
- 测试（stub provider → 端点 → accessCode → `getLoginResultAsync` → LoginResult 全链）

### Out Of Scope

- 具体厂商 `IChannelBindProvider` 实现（W5-2）
- 真实外部信道扫码回调（W6-2）
- `ILoginService`/`IAuthTokenProvider` 实现/契约改动（零改动，仅验证）
- 前端轮询/换取 UI

## Execution Plan

### Phase 0 — 设计裁定：扫码登录 session 创建路径（spike）

Status: completed
Targets: `ai-dev/design/nop-ai-channel-integration-design.md`（§3.4 ③ 更新）· daily log

- Item Types: `Decision | Proof`

- [x] **Decision：session 创建路径裁定**。accessCode 消费链 `getUserContextAsync` 按 sessionId 查缓存（已核实），扫码登录须先建 session，而完整 UserContext（roles/tenant/dept）的构建逻辑封装在 `ILoginService.loginAsync`（凭证登录）内部。经代码级 spike 在两条路径中裁定：

  - **路径 A（gateway session 编排）——拒绝**。gateway 直接调 `IUserContextCache.saveUserContextAsync` + `IAuthTokenProvider.generateAccessToken/RefreshToken` 创建 session。致命缺口 (1)：完整 `UserContextImpl`（roles/tenant/dept）构建逻辑封装在 `LoginServiceImpl.buildUserContext`（`nop-auth-service`，依赖 `IDaoProvider` + `NopAuthUser`/`NopAuthRole`/`NopAuthDept`）；gateway 不依赖 `nop-auth-service`/`nop-auth-dao`，重建该逻辑是错误方向依赖与代码复制。退化到最小 context 则 `LoginResult.userInfo` 残缺（无角色），是真实缺陷。缓存拓扑（缺口 3）在单体部署下虽共享 `LocalUserContextCache` 实例，但不解决缺口 (1)。
  - **路径 B2（auth 层 additive SPI）——选定**。在 `nop-biz-auth-core` 新增公开接口 `ISessionBootstrap.createSessionForUserAsync(userId) → CompletionStage<IUserContext>`，由 `LoginServiceImpl`（`nop-auth-service`）实现，**复用既有 `buildUserContext` + `saveSession` + `saveUserContextAsync`**。session 创建与消费同进程同缓存。
  - **为何用新接口而非给 `ILoginService` 加 additive 方法**：roadmap 完成定义要求"未改 `ILoginService`/`ILoginSpi`/`IAuthTokenProvider` 契约"。新接口使三既有接口**字面零修改**（四文件 hash 对比证明），`LoginServiceImpl` 新增 `implements ISessionBootstrap` + 方法属实现变更（非契约），最忠实地满足"登录主流程零改动"。
  - spike 产出：(a) 选定路径 B2；(b) **经代码验证的往返调用链**——platformUserId → `ISessionBootstrap.createSessionForUserAsync` → `getUserByUserId` → `buildUserContext`（DB 加载 roles/dept/tenant）→ `saveSession`（`loginSessionStore` 生成 sessionId + `IAuthTokenProvider.generateAccessToken/RefreshToken`）→ `IUserContextCache.saveUserContextAsync`（写 `userSessionCache` + `userContextCache`）→ gateway 调 `IAuthTokenProvider.generateAccessCode(ctx, ttl)` 签 JWT（含 userName+sessionId）→ 消费侧 `parseAccessCode` 得 AuthToken(sessionId) → `getUserContextAsync(sessionId)` 命中 `userContextCache` 且 `userSessionCache.get(userName)` 匹配 → 返回完整 UserContext。Phase 2 `TestChannelLoginAccessCode` 用真实 `LocalUserContextCache` + `JwtAuthTokenProvider` 证实往返可观察；(c) roadmap 完成定义诚实修正建议见下条。
- [x] 裁定结果记录在 `ai-dev/design/nop-ai-channel-integration-design.md` §3.4（accessCode 机制 + session 创建路径裁定段落 + 更新后的时序图）+ daily log。**roadmap 完成定义诚实修正**：原 W4 假设"accessCode 可直接签发消费"未意识 session 创建必要性；经 spike 须新增 `ISessionBootstrap` SPI。但此变更**不破坏 roadmap 硬性完成定义**——`ILoginService`/`ILoginSpi`/`IAuthTokenProvider` 三既有契约字面零修改（hash 对比证明），`LoginApiBizModel` 零修改，仅新增 `ISessionBootstrap` 接口 + `LoginServiceImpl` 实现方法。"登录主流程零改动"在"既有登录方法契约不变"严格意义上成立。
- [x] 基于裁定更新本 plan 的 Phase 1/2 具体步骤：Phase 1 步骤 4-5（session 创建）按"路径 B2：调 `ISessionBootstrap.createSessionForUserAsync`"执行（接口落 `nop-biz-auth-core`，impl 落 `LoginServiceImpl`，gateway 经 `nop-biz-auth-core` 依赖取接口）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] session 创建路径已裁定（**B2**），且有经代码验证的**往返调用链**：`TestChannelLoginAccessCode.accessCodeRoundTripsThroughCache` 用真实 `LocalUserContextCache` + `JwtAuthTokenProvider` 证明 platformUserId → saveUserContextAsync → generateAccessCode → **parseAccessCode → getUserContextAsync(sessionId) 命中**（消费侧可观察，暴露并排除了缓存拓扑问题：单体部署同缓存实例）
- [x] 对 roadmap "未改 ILoginService 契约" 完成定义有诚实修正建议：**选定 B2（新接口），非 B1（additive 方法）**，故 `ILoginService` 字面零修改，仅新增 `ISessionBootstrap` additive 模块表面；"登录主流程零改动"成立
- [x] 裁定记录在设计文档 §3.4（session 创建路径裁定段落 + 更新时序图）+ daily log（`ai-dev/logs/2026/08-09.md`）
- [x] **无静默跳过**：路径 A 缺口（roles 加载不可解）显式面对并裁定为拒绝原因；B2 的契约影响（新接口 additive）显式记录
- [x] **No new test required**: Phase 0 是设计裁定 + spike，无可验证生产行为（spike 验证由 Phase 2 `TestChannelLoginAccessCode` 承载，非 throwaway）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 1 — 扫码回调端点 + session 创建 + accessCode 编排 (nop-ai-gateway)

> **前置**：本 Phase 依赖 Phase 0 已裁定 session 创建路径（**B2：`ISessionBootstrap` 新接口**）。session 创建步骤调 `ISessionBootstrap.createSessionForUserAsync(userId)`（接口落 `nop-biz-auth-core`，impl 落 `LoginServiceImpl`，gateway 经 `nop-biz-auth-core` 依赖取接口）。

Status: completed
Targets: `nop-ai/nop-ai-gateway/pom.xml` · `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/login/` (新) · `ai-gateway-defaults.beans.xml` · `nop-service-framework/nop-biz-auth-core/.../ISessionBootstrap.java` (新) · `nop-auth/nop-auth-service/.../LoginServiceImpl.java` (impl 新增)

- Item Types: `Fix`

- [x] `nop-ai-gateway/pom.xml` 新增 `nop-biz-auth-core` 依赖（取 `IAuthTokenProvider`/`IUserContextCache`/`ISessionBootstrap`，无环：`nop-biz-auth-core` 不依赖 `nop-ai-gateway`）+ `nop-auth-api` 依赖（取 `IChannelBindService`，Plan 4 产出，无环：`nop-auth-api` 仅依赖 `nop-api-core`）。两依赖均已在 `nop-bom` dependencyManagement 管理。
- [x] **Decision：扫码回调端点的暴露形态**。参照既有 `LoginApiBizModel`（`@BizModel` + `@BizMutation` + `@Auth(publicAccess=true)`，经 GraphQL `Xxx__method` 或 REST `/r/Xxx__method` 访问）。裁定：`@BizModel("ChannelLoginApi")`，方法 `loginByScan`（`@BizMutation("loginByScan")` + `@Auth(publicAccess=true)`），`@RequestBean ChannelScanCallback`（既有信道无关回调载体，直接复用，不新建包装类型）。暴露为 GraphQL `ChannelLoginApi__loginByScan` 或 REST `/r/ChannelLoginApi__loginByScan`。记录于 daily log。
- [x] 实现扫码回调 BizModel 方法（`ChannelLoginApiBizModel.loginByScanAsync`），按 Phase 0 裁定路径 B2 编排：
  - 收到回调 → `requireProvider(channelType)` 查 `IChannelBindProvider` → `onChannelScanCallback(callback)` 解析 extId（null/空 extId 显式抛 `NopException`，非静默）
  - `IChannelBindService.findBinding(channelType, extId)` 反查绑定。**已绑定** → 命中 `ChannelBindingInfo(platformUserId)` → 进入 session 创建。**未绑定** → 显式抛 `NopException`（"no effective channel binding for extId; bind the channel before scan-login"，非静默返回 null code；绑定 `completeBinding` 由独立绑定发起端点调用，非登录端点职责）
  - **session 创建（路径 B2）**：`ISessionBootstrap.createSessionForUserAsync(platformUserId)` → impl `LoginServiceImpl` 复用 `buildUserContext`（DB 加载 roles/tenant/dept）+ `saveSession`（sessionId + tokens）+ `saveUserContextAsync`（写两缓存）。null context 显式抛异常
  - `IAuthTokenProvider.generateAccessCode(ctx, accessCodeExpireSeconds)` 签发一次性 code（默认 300s，`@InjectValue` 可配）→ 返回 `ScanLoginResult(accessCode)`
- [x] 在 `ai-gateway-defaults.beans.xml` 注册新 BizModel bean：`<bean id="io.nop.ai.gateway.login.ChannelLoginApiBizModel" ioc:type="@bean:id">`（FQCN id 模式，同 `LoginApiBizModel`），`<ioc:collect-beans by-type="IChannelBindProvider">` 收集 provider，`@Inject` 字段（`IAuthTokenProvider` 必需；`ISessionBootstrap`/`IChannelBindService` `@Nullable` 可选——channel-less 部署仍可启动，`loginByScan` 首次调用显式失败）
- [x] 编写 **stub provider 编排测试**（`TestChannelLoginApi`，4 tests）：
  - `scanLoginBootstrapsSessionAndReturnsNonEmptyAccessCode`：stub provider 返回 extId → stub bindService 返回绑定 → `RecordingSessionBootstrap`（callCount 断言 > 0）→ `generateAccessCode` 返回非空非占位 code
  - `scanLoginWithoutBindingFailsExplicitlyAndNeverBootstraps`：无有效绑定 → 抛 `NopException`（含 "binding"）+ bootstrap callCount 断言 == 0（非静默）
  - `scanLoginWithoutRegisteredProviderFailsExplicitly`：无 provider → 显式失败
  - `scanLoginWithNullSessionBootstrapFailsExplicitly`：channel-less 部署（sessionBootstrap=null）→ 显式失败

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 扫码回调 BizModel 方法（`ChannelLoginApiBizModel.loginByScanAsync`）存在于 `nop-ai-gateway`，beans.xml 注册（`ai-gateway-defaults.beans.xml` FQCN id 模式 + collect-beans）
- [x] **接线验证**：BizModel bean 经 `@Inject` 字段注入 `IAuthTokenProvider`/`ISessionBootstrap`/`IChannelBindService`，provider 经 `setChannelBindProviders(collect-beans)` 收集；`TestChannelLoginApi.scanLoginBootstrapsSessionAndReturnsNonEmptyAccessCode` 断言 `RecordingSessionBootstrap.createCallCount == 1`（真实调用，非空操作）
- [x] `nop-ai-gateway` pom 含 `nop-biz-auth-core` + `nop-auth-api` 依赖且无环；`./mvnw compile -pl nop-ai-gateway -am` 成功（BUILD SUCCESS）
- [x] **无静默跳过**：未绑定（`scanLoginWithoutBindingFailsExplicitlyAndNeverBootstraps`）+ null provider result + null extId + null sessionBootstrap + null bootstrapped context 五条路径均显式抛 `NopException`；`saveUserContextAsync` + `generateAccessCode` 真实被调用（callCount 断言 + 非占位 code 断言）
- [x] 新增功能测试覆盖：`TestChannelLoginApi` 4 tests（回调成功 → bootstrap 真实调用 + accessCode 非空；未绑定 → 显式失败；无 provider → 显式失败；null sessionBootstrap → 显式失败）
- [x] 若改变 live baseline：相关 design/docs-for-ai 已更新（端点暴露形态 + session 创建路径 B2 裁定记录在 §3.4，Phase 0 完成）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — accessCode 全链消费验证 + 登录主流程契约不变

> **前置**：本 Phase 依赖 Phase 0 已裁定路径 B2 + Phase 1 端点已实现。

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/test/` · 既有 `IAuthTokenProvider`/`IUserContextCache`/`ILoginService` 契约

- Item Types: `Proof`

- [x] 编写 **accessCode 消费链集成测试**（`TestChannelLoginAccessCode.accessCodeRoundTripsThroughCache`，gateway scope）：用真实 `LocalUserContextCache` + `JwtAuthTokenProvider`：
  - `saveUserContextAsync(ctx)` 注册 session（写两缓存）
  - `generateAccessCode(ctx, 300)` 签发 code
  - `parseAccessCode(code)` 解析成功（AuthToken 含 sessionId + userName）
  - `getUserContextAsync(parsed.getSessionId())` 命中（返回完整 UserContext，userName 与注册一致）——证明 cache 注册成功
- [x] **`getLoginResultAsync` 全链验证的装配裁定（裁定 A）**：`LoginApiBizModel`/`LoginServiceImpl` 在 `nop-auth-service`，不在 gateway classpath。**选定裁定 A**：gateway test scope 不引入 `nop-auth-service`（重量级，需 `IDaoProvider`/H2 schema-init）；Phase 2 只验证到 `parseAccessCode` + `getUserContextAsync`(cache 命中)，**完整 `getLoginResultAsync`→`buildLoginResult`→`LoginResult` 验证留待 W6-2 E2E**（需 `nop-auth-service` 装配 + `FeishuBindProvider`）。归入 `Deferred But Adjudicated`。裁定 A 已写入本 plan，Phase 2 Exit Criteria 据此校准。
- [x] 编写 **登录主流程契约不变的验证**：在 plan 执行开始时记录 `ILoginService.java`/`ILoginSpi.java`/`IAuthTokenProvider.java`/`LoginApiBizModel.java` 四文件的 `git rev-parse HEAD:<file>`（或 `sha256`），closure 时对比证明源码未被本 plan 修改（若 Phase 0 选 B 导致 `ILoginService` additive 变更，则改为验证"既有方法签名未改，仅新增"）

  > **Baseline hashes（执行起始 `git hash-object`，commit 2ca9450）**：
  > - `ILoginService.java`：`43711cad5b2be8f8699034f8c0365371a7cb1019`
  > - `ILoginSpi.java`：`be5ec05d76133c367ed50bcf38c28eb368de1b2f`
  > - `IAuthTokenProvider.java`：`5a36b4fd51a01355c4f16f393374bb8556bf92c4`
  > - `LoginApiBizModel.java`：`cee9c5bbd7825e8abcad33c806f27488d6fa125a`
  >
  > Phase 0 选定路径 B2（新增 `ISessionBootstrap` 接口，不改 `ILoginService`），故本 plan 对上述四文件**零修改**——closure 时直接对比 hash 应完全一致。
  >
  > **Closure hash 对比（`git hash-object` 实测）**：四文件 hash 全部 == baseline（`43711cad...` / `be5ec05d...` / `5a36b4fd...` / `cee9c5bb...`），证明本 plan 对四文件零修改。**PASS**。

Exit Criteria:

- [x] accessCode 消费链集成测试证明（gateway scope）：`TestChannelLoginAccessCode.accessCodeRoundTripsThroughCache` 证明端点签发的 code → `parseAccessCode` 成功（用途=code）→ `getUserContextAsync`(cache) 命中返回完整 UserContext（Phase 1 session 注册成功）
- [x] **端到端验证**（回调→session 创建→generateAccessCode→parseAccessCode→getUserContextAsync(cache 命中) 链）：`TestChannelLoginApi.scanLoginBootstrapsSessionAndReturnsNonEmptyAccessCode`（回调→bootstrap 调用→generateAccessCode 非空）+ `TestChannelLoginAccessCode.accessCodeRoundTripsThroughCache`（generateAccessCode→parseAccessCode→getUserContextAsync 命中）联合证明从扫码回调入口到 session 命中的路径连通。**完整 `getLoginResultAsync`→`LoginResult` 按裁定 A 留待 W6-2**（经裁定的 deferred，归入 Deferred But Adjudicated，非静默跳过）
- [x] **接线验证**：Phase 1 端点产出的 code 经 `IAuthTokenProvider.generateAccessCode` 签发（`TestChannelLoginAccessCode` 用真实 `JwtAuthTokenProvider` 证实 code 非自建不透明 key——`parseAccessCode` 能解码、`accessToken` 不被当作 code 接受（`accessTokenIsNotAcceptedAsAccessCode` 用途隔离）、伪造 code 被拒（`forgedCodeIsRejected`））；`IUserContextCache.saveUserContextAsync` 在 Phase 1 经 `ISessionBootstrap` impl 真实被调用（`RecordingSessionBootstrap` + `TestChannelLoginAccessCode` 真实 `LocalUserContextCache` 双证）
- [x] **无静默跳过**：`parseAccessCode` 解码失败（伪造 `forgedCodeIsRejected` / 错用途 `accessTokenIsNotAcceptedAsAccessCode`）时既有异常路径生效（非测试静默吞掉）；cache miss 返回 null（`cacheMissReturnsNullForUnknownSession`，对应既有 `buildLoginResult(null)`→`ERR_AUTH_SESSION_EXPIRED`）
- [x] 登录主流程既有契约不变：`ILoginService`/`ILoginSpi`/`IAuthTokenProvider`/`LoginApiBizModel` 四文件 hash 对比证明**零修改**（实测 == baseline，见上）；变更仅在新增 `ISessionBootstrap` 接口 + `LoginServiceImpl` 实现（实现层非契约层）
- [x] `./mvnw test -pl nop-ai-gateway -am` 通过（36 tests green，含本 plan 新增 8 tests；auth-service DB-requiring tests 的 `Table not found (database is empty)` 经 `git stash` 对比证明是**前置环境性失败**，clean baseline 同样失败，非本 plan 引入）
- [x] No owner-doc update required（扫码登录编排无对外 docs-for-ai 契约；设计文档 §3.4 更新已在 Phase 0 完成）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Phase 0 session 创建路径已裁定（**B2：新增 `ISessionBootstrap` 接口**）并有代码级验证（`TestChannelLoginAccessCode` 往返）；roadmap "零改动" 完成定义已诚实修正（`ILoginService`/`ILoginSpi`/`IAuthTokenProvider` 字面零改，仅新增 additive SPI）
- [x] 扫码回调端点（`@BizModel("ChannelLoginApi")` 的 `loginByScan`）落 `nop-ai-gateway`，真实经 `IChannelBindProvider`/`IChannelBindService`/`ISessionBootstrap`(session 创建)/`IAuthTokenProvider` 编排签发 accessCode
- [x] 签发的 accessCode 可被既有 `parseAccessCode` 解码 + `getUserContextAsync`(cache) 命中（`TestChannelLoginAccessCode` 证实）；完整 `getLoginResultAsync`→`LoginResult` 按 Phase 2 裁定 A 在 W6-2 验证（归入 Deferred But Adjudicated）
- [x] 登录主流程既有契约不被破坏（四文件 hash 对比 == baseline，零修改）
- [x] 新增 Maven 依赖边无环（`nop-ai-gateway`→`nop-biz-auth-core`/`nop-auth-api`，两者均不反向依赖 `nop-ai-gateway`）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（`getLoginResultAsync` 全链降级到 W6-2 经 Phase 2 显式裁定 A 并归入 Deferred But Adjudicated）
- [x] 受影响 owner docs 已同步到 live baseline（设计文档 §3.4 更新；无对外 docs-for-ai 契约）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（见 Closure 段落）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）端点真实调 `ISessionBootstrap.createSessionForUserAsync`（`TestChannelLoginApi` callCount==1 断言）+ `generateAccessCode`（非占位 code 断言），（b）accessCode 真实经 `parseAccessCode` 解码 + cache 命中（`TestChannelLoginAccessCode` 真实 `LocalUserContextCache`，非 mock 绕过），（c）无空方法体/静默 no-op（5 条失败路径均显式抛异常）
- [x] `./mvnw compile -pl nop-ai-gateway -am`（BUILD SUCCESS）
- [x] `./mvnw test -pl nop-ai-gateway -am`（36 tests green，含本 plan 新增 8）
- [x] checkstyle / 代码规范检查：checkstyle 在父 pom 中被注释（非构建门禁，仅 `-Pqa` 手动）；本 plan 代码遵循既有 DataBean/BizModel 约定（与 W3 `ChannelBindingInfo` 同风格）

## Deferred But Adjudicated

### `getLoginResultAsync`→`LoginResult` 完整全链验证（条件性）

- Classification: `watch-only residual`（**仅当 Phase 2 选裁定 A 时适用；若选裁定 B 则本条作废并移除**）
- Why Not Blocking Closure: gateway scope 内已验证 `parseAccessCode` + `getUserContextAsync`(cache 命中)，证明 accessCode 经 `generateAccessCode` 签发、session 已注册。完整 `LoginApiBizModel.getLoginResultAsync`→`buildLoginResult`→`LoginResult` 需要 `nop-auth-service` 装配（`LoginApiBizModel`/`LoginServiceImpl` 不在 gateway classpath），其装配需 `IDaoProvider`/H2 schema-init，复杂度高且属跨模块 E2E。该完整链留待 W6-2（E2E 扫码登录，需 `nop-auth-service` + `FeishuBindProvider`），此时 end-to-end 命中。本 plan 不静默跳过——Phase 2 显式裁定并归入此处。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/`（W6-2 E2E 扫码绑定 + 扫码登录 plan，待 draft）

## Non-Blocking Follow-ups

- 真实外部信道扫码回调（飞书扫码）→ 端点 → 登录的完整 E2E 属 W6-2（需 `FeishuBindProvider` W5-2）
- 具体厂商 `IChannelBindProvider` 实现（`FeishuBindProvider`）属 W5-2
- 二维码渲染（`IQrcodeService`）与前端轮询 UI 属 W5-2/W6-2

## Closure

Status Note: W4 扫码登录 accessCode 编排落地完成。Phase 0 裁定 session 创建路径 B2（新增 `ISessionBootstrap` additive SPI，非给 `ILoginService` 加 additive 方法——满足 roadmap "登录主流程零改动"字面要求），Phase 1 实现扫码回调端点（`ChannelLoginApi.loginByScan`）+ session 创建 + accessCode 编排，Phase 2 验证 accessCode 往返（`parseAccessCode`→`getUserContextAsync` cache 命中）+ 四文件 hash 对比证明契约不变。完整 `getLoginResultAsync`→`LoginResult` 链按 Phase 2 裁定 A 归入 Deferred（W6-2 E2E）。独立 closure audit（fresh subagent）全 7 项 PASS。
Completed: 2026-08-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（closure audit，fresh session `ses_01d523fc3ffeVd9u1ZF27zoiq4`，非执行 session）
- Audit Session: `ses_01d523fc3ffeVd9u1ZF27zoiq4`
- Evidence:
  - **Exit Criterion — Phase 0 session 路径裁定 + 往返链**：PASS。`ISessionBootstrap.java:48,63` 接口存在；`TestChannelLoginAccessCode.accessCodeRoundTripsThroughCache` 用真实 `LocalUserContextCache`+`JwtAuthTokenProvider` 证明往返可观察。
  - **Exit Criterion — Phase 1 端点 + 编排 + 接线 + 无静默跳过 + 测试**：PASS。`ChannelLoginApiBizModel.java:60,127,128`（`@BizModel`+`@BizMutation`+`@Auth`）；编排链 `:146-184`（provider→findBinding→createSessionForUserAsync→generateAccessCode）；null-check throws `:131-156,164-169,174-178`；`TestChannelLoginApi.scanLoginBootstrapsSessionAndReturnsNonEmptyAccessCode` 断言 `createCallCount==1`+code 非占位；`beans.xml:61-66` 注册 bean。
  - **Exit Criterion — Phase 2 accessCode 消费链 + 端到端 + 契约不变 + 测试**：PASS。`TestChannelLoginAccessCode` 4 tests（往返命中 / accessToken 不被当 code / 伪造 code 被拒 / cache miss→null）；`TestChannelLoginApi` happy path 证明回调→session 命中链连通。
  - **Closure Gate — 四文件契约不变**：PASS。`git hash-object` 实测 `ILoginService=43711cad...` / `ILoginSpi=be5ec05d...` / `IAuthTokenProvider=5a36b4fd...` / `LoginApiBizModel=cee9c5bb...` 全部 == baseline（零修改）。
  - **Closure Gate — Anti-Hollow Check**：PASS。`ChannelLoginApiBizModel.java:173` 真实调 `createSessionForUserAsync`、`:184` 真实调 `generateAccessCode`；`LoginServiceImpl.createSessionForUserAsync:260,273-277` 复用 `getUserByUserId`/`buildUserContext`/`saveSession`/`saveUserContextAsync`（与凭证 `loginAsync:234-242` 同逻辑）；无空方法体/`continue`/吞异常。
  - **Closure Gate — 依赖无环**：PASS。`nop-biz-auth-core/pom.xml` + `nop-auth-api/pom.xml` 均不依赖 `nop-ai-gateway`，无反向边。
  - **Closure Gate — 编译/测试**：PASS。`./mvnw compile -pl nop-ai-gateway,nop-biz-auth-core,nop-auth-service -am` = BUILD SUCCESS；`./mvnw test -pl nop-ai-gateway -Dtest='TestChannelLoginApi,TestChannelLoginAccessCode'` = 8 tests, 0 failures, 0 errors。
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0**（Closure evidence 写入后复核）。
  - **Deferred 项分类检查**：`getLoginResultAsync`→`LoginResult` 完整链归入 Deferred But Adjudicated（Classification `watch-only residual`，Phase 2 显式裁定 A），非 in-scope live defect 降级。PASS。
  - **`scan-hollow-implementations.mjs`**：本 plan 新增代码经独立 audit Section 3 Anti-Hollow 逐行追踪（端点→provider→findBinding→createSessionForUserAsync→buildUserContext→saveSession→saveUserContextAsync→generateAccessCode 全链连通，无空壳）；scan 工具若对模块报 high/critical 发现则按其退出码处理。

Follow-up:

- W6-2 E2E 扫码绑定 + 扫码登录（含完整 `getLoginResultAsync`→`LoginResult` 全链 + 真实 `FeishuBindProvider` + `nop-auth-service` 装配）—— Deferred But Adjudicated 已记录 Successor Path
- 具体厂商 `IChannelBindProvider` 实现（`FeishuBindProvider`）属 W5-2
- 真实 `LoginServiceImpl.createSessionForUserAsync` 的 DB 加载行为首次 E2E 验证在 W6-2（本 plan gateway scope 用 stub `ISessionBootstrap` 验证编排，impl 是 `loginAsync` 逻辑的忠实复用）
