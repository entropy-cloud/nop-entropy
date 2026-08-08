# 5 nop-ai 外部信道集成 — 扫码登录 accessCode 编排 (W4)

> Plan Status: active
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

Status: planned
Targets: `ai-dev/design/nop-ai-channel-integration-design.md`（§3.4 ③ 更新）· daily log

- Item Types: `Decision | Proof`

- [ ] **Decision：session 创建路径裁定**。accessCode 消费链 `getUserContextAsync` 按 sessionId 查缓存（已核实），扫码登录须先建 session，而完整 UserContext（roles/tenant/dept）的构建逻辑封装在 `ILoginService.loginAsync`（凭证登录）内部。须在以下两条路径中裁定一条，并用代码级 spike 验证可行性：
  - **路径 A（gateway session 编排）**：gateway 直接操作 `IUserContextCache.saveUserContextAsync` + `IAuthTokenProvider.generateAccessToken/RefreshToken` 创建 session。**已知缺口**：(1) gateway 需构建完整 `UserContextImpl`（含 roles/tenant），而用户加载逻辑在 `nop-auth-service` 的 `loginAsync` 内部——gateway 不依赖 `nop-auth-service`，须裁定 roles/tenant 从何加载；(2) sessionId 来源（`loginAsync` 经 `loginSessionStore.saveSession` 生成，gateway 未必有 `ILoginSessionStore` bean——是否用 `ISessionIdGenerator`/UUID 自生成？）；(3) **缓存拓扑一致性**：默认 `LocalUserContextCache` 是进程内缓存，gateway 写 session 而 `getLoginResultAsync` 消费在 `nop-auth-service`，若不同进程则 session 不可见——须裁定部署拓扑与是否需分布式 cache 实现。评估三缺口是否可接受。
  - **路径 B（auth additive SPI）**：在 `nop-biz-auth-core` 的 `ILoginService`（或新接口）加一个 additive 方法如 `createSessionForUser(userId)`，在 `nop-auth-service` 的 `LoginServiceImpl` 实现完整用户加载 + session 创建（复用既有 `loginAsync` 内部逻辑）。session 创建与消费同进程同缓存，天然回避 Path A 的缺口 (1)/(3)。**已知代价**：这是 `nop-biz-auth-core` 公共契约的 additive 变更，需诚实修正 roadmap "未改 `ILoginService` 契约" 的完成定义（additive 非 breaking，但严格说是契约变更）。
  - spike 须产出：(a) 选定路径；(b) **经代码验证的往返调用链**——从 platformUserId 到 `saveUserContextAsync` 到 `generateAccessCode`，**再回到 `getUserContextAsync` 能读到该 session**（不只验证写侧，须验证消费侧可观察，以暴露缓存拓扑问题）；(c) 对 roadmap 完成定义的诚实修正建议
- [ ] 裁定结果记录在 `ai-dev/design/nop-ai-channel-integration-design.md` §3.4（accessCode 机制 + session 创建路径）+ daily log
- [ ] 基于裁定更新本 plan 的 Phase 1/2 具体步骤（将 Phase 0 选定路径的调用链固化进 Phase 1 执行项）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] session 创建路径已裁定（A 或 B），且有经代码验证的**往返调用链**（platformUserId → saveUserContextAsync → generateAccessCode → **`getUserContextAsync` 能读到该 session**，验证消费侧可观察，暴露缓存拓扑问题；每步落到具体类/方法，非假设）
- [ ] 对 roadmap "未改 ILoginService 契约" 完成定义有诚实修正建议（若选 B，明确 additive 变更；若选 A，明确 gateway 编排 + roles 加载来源）
- [ ] 裁定记录在设计文档 §3.4 + daily log
- [ ] **无静默跳过**：路径缺口（A 的 roles 加载 / B 的契约变更）显式面对并裁定，不留 "TODO 待定"
- [ ] **No new test required**: Phase 0 是设计裁定 + spike，无可验证生产行为（spike 代码若是 throwaway 则不强制测试；若保留则按 Phase 1/2 要求测试）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 1 — 扫码回调端点 + session 创建 + accessCode 编排 (nop-ai-gateway)

> **前置**：本 Phase 依赖 Phase 0 已裁定 session 创建路径。以下步骤按"路径 A（gateway 编排）"为默认假设；若 Phase 0 选 B，步骤 4-5 替换为调 auth additive SPI。

Status: planned
Targets: `nop-ai/nop-ai-gateway/pom.xml` · `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/login/` (新) · `ai-gateway-defaults.beans.xml`

- Item Types: `Fix`

- [ ] `nop-ai-gateway/pom.xml` 新增 `nop-biz-auth-core` 依赖（取 `IAuthTokenProvider`/`IUserContextCache`，无环：`nop-biz-auth-core` 不依赖 `nop-ai-gateway`；`AccessCodeRequest` 经 `nop-biz-auth-core`→`nop-biz-auth-api` 传递可用）+ `nop-auth-api` 依赖（取 `IChannelBindService`，Plan 4 产出，无环：`nop-auth-api` 仅依赖 `nop-api-core`）
- [ ] **Decision：扫码回调端点的暴露形态**。参照既有 `LoginApiBizModel`（`@BizModel` + `@BizMutation`/`@BizQuery` + `@Auth(publicAccess=true)`，经 GraphQL `Xxx__method` 或 REST `/r/Xxx__method` 访问）。裁定回调端点的 `@BizModel` 名（如 `ChannelLoginApi`）、方法名、`@RequestBean` 载体。记录于 daily log。
- [ ] 实现扫码回调 BizModel 方法，按 Phase 0 裁定路径编排：
  - 收到回调 → 经 `IChannelBindProvider.onChannelScanCallback(ChannelScanCallback)` 解析 extId（Plan 4 产出）→ `IChannelBindService.findBinding(channelType, extId)` 反查绑定。**已绑定** → 命中返回含 platformUserId 的 `ChannelBindingInfo` → 进入 session 创建。**未绑定** → 这是绑定流程而非登录流程（用户尚未绑定该信道，不能扫码登录），返回明确"未绑定"状态/错误（非静默继续；绑定的 `completeBinding` 由独立的绑定发起端点调用，非登录端点职责）
  - **session 创建**（路径 A）：userId → 加载用户概要（userName/roles/tenant，按 Phase 0 裁定的加载来源）→ 构造 `UserContextImpl`（设 sessionId + `IAuthTokenProvider.generateAccessToken`/`generateRefreshToken`）→ `IUserContextCache.saveUserContextAsync(ctx)` 注册两个缓存
  - `IAuthTokenProvider.generateAccessCode(ctx, ttl)` 签发一次性 code → 返回前端
- [ ] 在 `ai-gateway-defaults.beans.xml` 注册新 BizModel bean（注入 `IChannelBindProvider`（stub 或 SPI 收集）+ `IChannelBindService` + `IAuthTokenProvider` + `IUserContextCache`）
- [ ] 编写 **stub provider 编排测试**：
  - 回调到达 → stub provider 返回 extId → findBinding/completeBinding → 取 platformUserId → session 创建（`saveUserContextAsync` 被调用）→ `generateAccessCode` 被调用并返回非空 code
  - 未绑定且回调未完成绑定 → 显式失败（抛异常，非静默返回 null code）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 扫码回调 BizModel 方法存在于 `nop-ai-gateway`，beans.xml 注册
- [ ] **接线验证**：BizModel bean 在 IoC 容器可解析，`IAuthTokenProvider`/`IUserContextCache`/`IChannelBindService`/`IChannelBindProvider` 被注入（见 Minimum Rules #23）
- [ ] `nop-ai-gateway` pom 含 `nop-biz-auth-core` + `nop-auth-api` 依赖且无环；`./mvnw compile -pl nop-ai-gateway -am` 成功
- [ ] **无静默跳过**：未绑定且回调未完成绑定时显式失败（非返回 null code / 非静默 `continue`）；`saveUserContextAsync` + `generateAccessCode` 真实被调用（非返回硬编码常量 / 非空 session 操作）
- [ ] 新增功能测试覆盖：回调成功 → session 真实创建（`saveUserContextAsync` 被调用）+ accessCode 非空且经 `generateAccessCode` 签发；未绑定 → 显式失败（见 Minimum Rules #25）
- [ ] 若改变 live baseline：相关 design/docs-for-ai 已更新（端点暴露形态 + session 创建路径裁定记录在 §3.4）；否则写 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — accessCode 全链消费验证 + 登录主流程契约不变

> **前置**：本 Phase 依赖 Phase 0 已裁定路径 + Phase 1 端点已实现。

Status: planned
Targets: `nop-ai/nop-ai-gateway/src/test/` · 既有 `IAuthTokenProvider`/`IUserContextCache`/`ILoginService` 契约

- Item Types: `Proof`

- [ ] 编写 **accessCode 消费链集成测试**（gateway scope 内可验证部分）：
  - 调 Phase 1 端点（stub provider）取得 accessCode
  - 用既有 `IAuthTokenProvider.parseAccessCode(code)` 解析成功（用途=code 校验通过，返回 AuthToken 含 userName + sessionId）
  - 用注入的 `IUserContextCache.getUserContextAsync(sessionId)` 验证 Phase 1 创建的 session 命中（返回完整 UserContext，userName 与绑定用户一致）——证明 cache 注册成功
- [ ] **`getLoginResultAsync` 全链验证的装配裁定**（Blocker 复核）：`LoginApiBizModel`/`LoginServiceImpl` 在 `nop-auth-service`，不在 gateway classpath。裁定一种：
  - **裁定 A**：gateway test scope 不引入 `nop-auth-service`（重量级）；Phase 2 只验证到 `parseAccessCode` + `getUserContextAsync`(cache 命中)，**完整 `getLoginResultAsync`→`buildLoginResult`→`LoginResult` 验证留待 W6-2 E2E**（需 `nop-auth-service` 装配 + `FeishuBindProvider`）。本 plan 在 Closure 显式标注此降级。
  - **裁定 B**：gateway test scope 引入 `nop-auth-service`，全链验证。评估装配复杂度（需 `IDaoProvider`/H2 schema-init）。
  - 选定裁定须写入 plan（不留模糊），并据此校准下面的 Exit Criteria
- [ ] 编写 **登录主流程契约不变的验证**：在 plan 执行开始时记录 `ILoginService.java`/`ILoginSpi.java`/`IAuthTokenProvider.java`/`LoginApiBizModel.java` 四文件的 `git rev-parse HEAD:<file>`（或 `sha256`），closure 时对比证明源码未被本 plan 修改（若 Phase 0 选 B 导致 `ILoginService` additive 变更，则改为验证"既有方法签名未改，仅新增"）

Exit Criteria:

- [ ] accessCode 消费链集成测试证明（gateway scope）：端点签发的 code → `parseAccessCode` 成功（用途=code）→ `getUserContextAsync`(cache) 命中返回完整 UserContext（Phase 1 session 注册成功）
- [ ] **端到端验证**（回调→session 创建→generateAccessCode→parseAccessCode→getUserContextAsync(cache 命中) 链）：测试证明从扫码回调入口到 session 命中的路径连通（见 Minimum Rules #22）。**完整 `getLoginResultAsync`→`LoginResult` 若按裁定 A 留待 W6-2，此处显式标注**（非静默跳过，是经裁定的 deferred，归入 Deferred But Adjudicated）
- [ ] **接线验证**：Phase 1 端点产出的 code 确实经 `IAuthTokenProvider.generateAccessCode` 签发、被既有 `parseAccessCode` 解码（非自建不透明 key）；`IUserContextCache.saveUserContextAsync` 在 Phase 1 真实被调用（非空操作）
- [ ] **无静默跳过**：`parseAccessCode` 解码失败（伪造/过期 code）时既有异常路径生效（非测试静默吞掉）
- [ ] 登录主流程既有契约不变：`ILoginService`/`ILoginSpi`/`IAuthTokenProvider`/`LoginApiBizModel` 四文件 hash 对比证明未被本 plan 修改（或仅 additive 新增，Phase 0 选 B 时）
- [ ] `./mvnw test -pl nop-ai-gateway -am` 通过（本 plan 新增测试全绿；环境性 H2 schema-init 既有失败若复现须证明非本 plan 引入）
- [ ] No owner-doc update required（扫码登录编排无对外 docs-for-ai 契约；设计文档 §3.4 更新已在 Phase 0 完成）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] Phase 0 session 创建路径已裁定并有代码级验证；roadmap "零改动" 完成定义已诚实修正（如适用）
- [ ] 扫码回调端点（`@BizModel`）落 `nop-ai-gateway`，真实经 `IChannelBindProvider`/`IChannelBindService`/session 创建/`IAuthTokenProvider` 编排签发 accessCode
- [ ] 签发的 accessCode 可被既有 `parseAccessCode` 解码 + `getUserContextAsync`(cache) 命中（完整 `getLoginResultAsync`→`LoginResult` 按 Phase 2 裁定在 W6-2 或本 plan 验证）
- [ ] 登录主流程既有契约不被破坏（四文件 hash 对比或仅 additive 新增）
- [ ] 新增 Maven 依赖边无环
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（`getLoginResultAsync` 全链降级到 W6-2 须经 Phase 2 显式裁定并归入 Deferred But Adjudicated）
- [ ] 受影响 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）端点真实调 `saveUserContextAsync` + `generateAccessCode`（非返回常量/非空 session），（b）accessCode 真实经 `parseAccessCode` 解码 + cache 命中（非 mock 绕过），（c）无空方法体/静默 no-op
- [ ] `./mvnw compile -pl nop-ai-gateway -am`
- [ ] `./mvnw test -pl nop-ai-gateway -am`
- [ ] checkstyle / 代码规范检查通过

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

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<如用子 agent，记录 session ID>>
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 live code path 或 test name）
  - 每条 Closure Gate 的验证结果（PASS/FAIL + evidence 来源）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
  - Anti-Hollow 检查结果：<<端到端调用链追踪结果>>；`scan-hollow-implementations.mjs` 退出码为 0
  - Deferred 项分类检查：<<确认无 in-scope live defect 被降级>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
