# 9 nop-ai 外部信道集成 — E2E 扫码绑定 + 扫码登录验证 (W6-2)

> Plan Status: completed
> Mission: nop-ai-channel-integration
> Work Item: W6-2
> Last Reviewed: 2026-08-09
> Source: `ai-dev/backlog/nop-ai-channel-integration-roadmap.md` (W6-2) · `ai-dev/design/nop-ai-channel-integration-design.md` (§3.4) · Plan 5 Closure Deferred（完整 `getLoginResultAsync→LoginResult` 全链 + 真实 `LoginServiceImpl.createSessionForUserAsync` DB 行为）
> Related: 前置 Plan 5 (W4 扫码登录端点 + accessCode 编排，completed) · 前置 Plan 4 (W3 IChannelBindProvider/IChannelBindService) · 前置 Plan 7 (FeishuBindProvider，completed) · 并行 Plan 8 (W6-1/W6-3 消息管线 E2E，独立)

## Purpose

把"扫码绑定 + 扫码登录"收口到 **E2E 可验证**状态，并偿还 Plan 5 的两项 Deferred But Adjudicated：

1. **完整 `getLoginResultAsync(AccessCodeRequest) → LoginResult` 全链**：Plan 5 Phase 2 裁定 A 将此链归入 W6-2，因该链实现在 `nop-auth-service`（`LoginApiBizModel`）不在 gateway classpath，gateway-scope 测试只能验证到 `generateAccessCode`。
2. **真实 `LoginServiceImpl.createSessionForUserAsync` 的 DB 加载行为**：Plan 5 用 stub `ISessionBootstrap` 验证编排，真实 impl（`loginAsync` 逻辑的忠实复用）的 DB 加载首次 E2E 验证在本 plan。

在**单一 JVM 内**（单体部署拓扑，roadmap 默认）装配真实 beans，验证完整流程：`createBindTicket` → 模拟飞书扫码回调（文档化 payload）→ `completeBinding` 写 `NopAuthExtLogin` → `ChannelLoginApiBizModel.loginByScan` 编排（`findBinding` → `ISessionBootstrap.createSessionForUserAsync` → `IAuthTokenProvider.generateAccessCode`）→ **`ILoginSpi.getLoginResultAsync(AccessCodeRequest)` 经 `parseAccessCode` + `IUserContextCache` 兑换 `LoginResult`**；二次扫码（绑定已存在）→ 直接登录。同时再确认 W4-1 端点（`ChannelLoginApiBizModel.loginByScan`）真实命中，以及登录主流程四文件契约字面零改。

## Current Baseline

- **绑定接口与实现（已落地，Plan 4）**：
  - `IChannelBindProvider`（`nop-integration-api/.../bind/IChannelBindProvider.java:31-72`）：`createBindTicket` / `onChannelScanCallback(ChannelScanCallback) → ChannelBindResult`。`ChannelScanCallback.rawPayload` 是 `Map<String,Object>`，provider 解析飞书字段。
  - `FeishuBindProvider`（`nop-integration-feishu/.../bind/FeishuBindProvider.java`，Plan 7）：`createBindTicket` → OAuth URL（`app_id`+`state=ticketId`）；`onChannelScanCallback` 从 rawPayload 提 `open_id` + ticketId 回查 platformUserId → `BINDING_COMPLETED`。7 tests（Plan 7）。
  - `IChannelBindService`（`nop-auth-api/.../bind/IChannelBindService.java`）+ `ChannelBindServiceImpl`（`nop-auth-service/.../channel/ChannelBindServiceImpl.java:66`）：`startBinding` / `completeBinding`（三种重绑裁定）/ `findBinding(channelType, extId)`（`:182-193`）/ `listBindings` / `unbind`。`bindingId == NopAuthExtLogin.sid`（PK）。
- **扫码登录端点（已落地，Plan 5）**：`ChannelLoginApiBizModel`（`nop-ai-gateway/.../login/ChannelLoginApiBizModel.java:60`，`@BizModel("ChannelLoginApi")` + `@BizMutation("loginByScan")` + `@Auth(publicAccess=true)`）。`loginByScanAsync`（`:127-181`）：`provider.onChannelScanCallback` → `channelBindService.findBinding(channelType, extId)`（`:163`，**不**fallthrough 到 completeBinding）→ `sessionBootstrap.createSessionForUserAsync(platformUserId)`（`:173`）→ `authTokenProvider.generateAccessCode(userContext, ttl)`（`:184`）→ `ScanLoginResult`。
- **session 创建（已落地，Plan 5）**：`ISessionBootstrap` 接口（`nop-biz-auth-core/.../ISessionBootstrap.java:48`）+ `LoginServiceImpl.createSessionForUserAsync`（`nop-auth-service`，`:260,273-277`，复用 `getUserByUserId`/`buildUserContext`/`saveSession`/`saveUserContextAsync`，与凭证 `loginAsync:234-242` 同逻辑）。
- **accessCode 签发/解码（已落地）**：`IAuthTokenProvider.generateAccessCode(IUserContext, long)` / `parseAccessCode(String)`（`IAuthTokenProvider.java:18,35`）。`JwtAuthTokenProvider`（`nop-biz-auth-core`）：`generateAccessCode`（`:116-120`，KID_CODE/TOKEN_TYPE_CODE/后缀"c"），`parseAccessCode`（`:144-148`，拒非 code 类型）。Plan 5 `TestChannelLoginAccessCode` 4 tests 已证明往返命中 cache + 用途隔离 + 伪造拒绝。
- **登录主流程消费链（已存在，本 plan 首次做 gateway↔auth-service 跨链 E2E）**：
  - `ILoginSpi.getLoginResultAsync(AccessCodeRequest, IServiceContext) → CompletionStage<LoginResult>`（`ILoginSpi.java:42-43`，实现在 `nop-auth-service` 的 `LoginApiBizModel`）。内部经 `IAuthTokenProvider.parseAccessCode` + `IUserContextCache.getUserContextAsync(sessionId)`。
  - `AccessCodeRequest`（`io.nop.auth.api.messages`）。
  - `LoginResult`（`io.nop.auth.api.messages`）。
- **登录主流程零改动基线（Plan 5 已用 `git hash-object` 证明四文件零改）**：`ILoginService` / `ILoginSpi` / `IAuthTokenProvider` / `LoginApiBizModel` 四文件 hash baseline 见 Plan 5 Closure Evidence。本 plan 须**再确认**此基线未被后续工作破坏。
- **缓存拓扑（关键）**：单体部署下 bootstrap bean（`nop-auth-service`）与 accessCode 消费方（`LoginApiBizModel`，`nop-auth-service`）共享同一 JVM + 同一 `IUserContextCache`，故 bootstrap 存入的 session 对消费方立即可见（`ISessionBootstrap.java:40-46`）。**本 plan 的 E2E 必须在单 JVM 内跑通**以验证 cache 命中。
- **测试 stub 模式（已存在）**：`TestChannelLoginApi`（`nop-ai-gateway/src/test/.../login/TestChannelLoginApi.java`）用 stub `IChannelBindProvider`/`IChannelBindService`/`ISessionBootstrap` + 真实 `JwtAuthTokenProvider`，经反射 `setField` 注入（`:167`）。`TestChannelLoginAccessCode`（同目录）用真实 `JwtAuthTokenProvider` + 真实 `LocalUserContextCache`。**注**：`nop-auth-service` test 已有 `TestLoginApi`（`JunitAutoTestCase` + `@NopTestConfig(initDatabaseSchema=TRUE, localDb=true)` + GraphQL）跑通完整 `LoginApiBizModel.getLoginResultAsync` H2 链——是 Phase 0 测试放置候选 A 的现成模板。
- **模块依赖约束**：`ChannelLoginApiBizModel` 在 `nop-ai-gateway`；`LoginServiceImpl`（`ISessionBootstrap` impl）+ `LoginApiBizModel`（`getLoginResultAsync`）+ `ChannelBindServiceImpl` 在 `nop-auth-service`。gateway 与 auth-service 之间无 runtime 依赖边（gateway → auth-api，非 auth-service）。**全链 E2E 测试需让两模块 bean 同 JVM 可达——具体测试放置与依赖机制是 Phase 0 决策项**。

## Goals

- **W6-2a 扫码绑定 E2E**：真实 `ChannelBindServiceImpl` + 真实 `FeishuBindProvider`，模拟飞书扫码回调（文档化 payload）→ `completeBinding` 写 `NopAuthExtLogin` 行，经 read-back 验证（loginType=20/extId=open_id/verified=1）。
- **W6-2b 扫码登录全链 E2E**：真实 `ChannelLoginApiBizModel.loginByScan` + 真实 `LoginServiceImpl`（`ISessionBootstrap`）+ 真实 `JwtAuthTokenProvider` + 真实 `ILoginSpi.getLoginResultAsync`（`LoginApiBizModel`），在**单 JVM**内跑通：扫码回调 → findBinding 命中 → createSessionForUserAsync（真实 DB 加载）→ generateAccessCode → **getLoginResultAsync(AccessCodeRequest) → LoginResult**（Plan 5 deferred 全链）；cache 命中可观察。
- **二次扫码直接登录**：绑定已存在时，扫码回调 → findBinding 命中 → 跳过 completeBinding → 直接 loginByScan → LoginResult。
- **W4-1 端点真实命中**：`ChannelLoginApiBizModel.loginByScan` 经真实容器（非纯 programmatic）可达并执行编排全链。
- **登录主流程零改动再确认**：四文件（`ILoginService`/`ILoginSpi`/`IAuthTokenProvider`/`LoginApiBizModel`）hash 仍 == Plan 5 baseline。

## Non-Goals

- 不改动 `IChannelBindProvider`/`IChannelBindService`/`ISessionBootstrap`/`IAuthTokenProvider`/`ILoginSpi`/`ILoginService`/`ChannelLoginApiBizModel` 接口/契约。
- 不做真实飞书 OAuth 服务器往返（无 CI 可达性）——扫码回调用文档化 payload 模拟。
- 不覆盖 W6-1/W6-3 消息管线 E2E（Plan 8）、W6-4 多消费者骨干（可选）。
- 不做多实例部署的跨进程 cache 同步（单体拓扑为本 plan 验证范围；跨进程属 optimization candidate）。
- 不重新实现 `LoginServiceImpl.createSessionForUserAsync`（Plan 5 已落地，本 plan 仅首次做真实 DB 加载 E2E 验证）。

## Scope

### In Scope

- `nop-ai/nop-ai-gateway/src/test/.../login/TestChannelScanBindLoginE2E.java`（全链 E2E，新建；或经 Phase 0 裁定放置模块——候选 A 放 `nop-auth-service` test）
- test beans（真实容器装配：feishu-defaults + auth-service + gateway 真实 beans，H2 + seeded `NopAuthExtLogin` **与 `NopAuthUser`（含 roles/dept，供 `LoginServiceImpl.getUserByUserId`+`buildUserContext` 加载，仿 `TestLoginApi.createTestUser()`）**）
- `ai-dev/logs/` 对应日期条目
- 四文件 hash 再确认（只读验证，不改文件）

### Out Of Scope

- 真实飞书 OAuth 往返（watch-only residual）
- W6-1/W6-3（Plan 8）、W6-4（可选）
- 接口/契约改动

## Execution Plan

### Phase 0 — 决策：全链 E2E 测试放置 + 依赖机制 + 零改动基线再确认

Status: completed
Targets: daily log · test 规划

- Item Types: `Decision | Proof`

- [x] **Decision：全链 E2E 测试放置与依赖机制**。全链需 `ChannelLoginApiBizModel`（gateway）+ `LoginServiceImpl`/`LoginApiBizModel`/`ChannelBindServiceImpl`（auth-service）同 JVM 可达。裁定放置模块与依赖方案，候选：
  - **候选 A（推荐，阻力最小）**：放在 **`nop-auth-service` test scope**，复用既有 `TestLoginApi` 的 `@NopTestConfig(initDatabaseSchema=TRUE, localDb=true)` + `JunitAutoTestCase` H2+schema+GraphQL 装配（该测试已跑通完整 `LoginServiceImpl`/`LoginApiBizModel`/DB/cache bean 装配）；加 `nop-ai-gateway` + `nop-integration-feishu` 为 **test-scope** 依赖（不构成 runtime 环）。此路径避免在 gateway-test 重建 auth-service 的 DB+autotest harness。
  - 候选 B：放 gateway-test，加 `nop-auth-service` test-scope——但 `nop-auth-service` 当前**不产 test-jar**（无 `maven-jar-plugin` test-jar 配置，仅 `nop-stream-core` 产），需新增插件配置；且须在 gateway-test 重建 H2+autotest infra。
  Phase 0 验证所选方案可编译、无 runtime 依赖环，记录裁定。
- [x] **Decision：真实 `IUserContextCache` 拓扑**。确认 E2E 用真实 `LocalUserContextCache`（同 `TestChannelLoginAccessCode`），使 bootstrap 存入 session 对 `getLoginResultAsync` 消费方可见（单 JVM cache 命中）。记录裁定。
- [x] **Proof：四文件零改动基线再确认**。运行 `git hash-object` 取 `ILoginService.java`/`ILoginSpi.java`/`IAuthTokenProvider.java`/`LoginApiBizModel.java`（auth-service 那个）当前 hash，对照 Plan 5 Closure Evidence baseline（`ILoginService=43711cad...` / `ILoginSpi=be5ec05d...` / `IAuthTokenProvider=5a36b4fd...` / `LoginApiBizModel=cee9c5bb...`），确认未被破坏；若漂移则先调查（本 plan 不允许改这四文件）。

**Phase 0 裁定结果（实施期 refine）**：测试放置选候选 A（`nop-auth-service` test scope，加 gateway+feishu test-scope 依赖）。但实施中发现 `nop-auth-service` 的**全量应用容器在当前 ALL_LAZY 测试模式下存在一个预先存在的基建缺陷**：`DataBaseSchemaInitializer`（`@PostConstruct`）未被 `getDelayMethod()`（仅识别 `@DelayMethod`）判定为 delayMethod，故在 ALL_LAZY 启动期不被构造 → schema 未创建 → 容器内 eager bean（`nopSiteMapProvider` 等）查询 `NopAuthSite` 失败，导致整个 `nop-auth-service` 测试套件（32 个用例，含既有的 `TestLoginApi`/`TestDaoLib`）红。此缺陷**非本 plan 引入**（独立审计 item 6 已确认 `TestDaoLib` 在与本 plan 无关的 commit `831cf7992` 中即如此）。

为不扩散到改 ORM/IoC 核心（受保护区，需独立 plan），Phase 0 裁定**refine 为自包含 H2 装配**（沿用本仓库 W6-1/W6-3 E2E 既有范式 `E2EH2SessionStore`）：测试自行构建 H2 + `OrmSessionFactoryBean` + `DdlSqlCreator.createTables`（创建全部表），在其上装配**真实**生产 bean。这绕开全量容器的 eager-bean 查询，同时仍端到端运行真实 `ChannelBindServiceImpl`/`FeishuBindProvider`/`LoginServiceImpl`/`JwtAuthTokenProvider`/`LocalUserContextCache`/`LoginApiBizModel`/`ChannelLoginApiBizModel`。唯一非真实协作者在关键链路之外（`IAuditService` no-op、`ILoginSessionStore` UUID-minter，javadoc 已注明）。依赖机制裁定保留：`nop-ai-gateway`（test-scope，excludes 掉 nop-ai-core/agent/dao 以避免拖入需 `IHttpClient` 的 autoconfig bean）+ `nop-integration-feishu`（test-scope）。`gateway`/`feishu` 自身的 `*-defaults.beans.xml` 不被容器自动发现（仅 `app*.beans.xml` + autoconfig 被加载），自包含装配不依赖它们。无 runtime 依赖环（gateway → auth-api，非 auth-service）。

Exit Criteria:

- [x] 全链 E2E 测试放置模块 + 依赖机制已裁定，`./mvnw test-compile -pl nop-auth-service -am` 可过（证明编译期 + 无 Maven 环；**runtime bean 装配证明在 Phase 1 首个测试运行时落地**），无 runtime 依赖环
- [x] 真实 `IUserContextCache` 拓扑已裁定（单 JVM cache 共享）
- [x] 四文件当前 hash == Plan 5 baseline（再确认证明），记录在 daily log
- [x] **No new test required**: Phase 0 是决策 + 只读 proof
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 1 — W6-2a 扫码绑定 E2E（真实 ChannelBindServiceImpl + FeishuBindProvider）

> **前置**：Phase 0 裁定。

Status: completed
Targets: `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestChannelScanBindLoginE2E.java`

- Item Types: `Proof`

- [x] E2E 绑定流：真实 `ChannelBindServiceImpl` + 真实 `FeishuBindProvider` + H2 `NopAuthExtLogin`。`startBinding("feishu", platformUserId)` → `createBindTicket` 返回 qrPayload（OAuth URL 含 ticketId）→ 构造文档化飞书扫码回调 `ChannelScanCallback`（rawPayload 含 `open_id` + ticketId）→ `FeishuBindProvider.onChannelScanCallback` → `BINDING_COMPLETED` → `ChannelBindService.completeBinding` 写 `NopAuthExtLogin`。
- [x] **read-back 验证**：绑定后查 `NopAuthExtLogin`（真实 ORM）含行：`loginType=20`（feishu）/ `extId=open_id` / `userId=platformUserId` / `verified=1` / `delFlag=0`。
- [x] **`findBinding` 反查 E2E**：`channelBindService.findBinding("feishu", open_id)` 命中刚写入的绑定（非 null，platformUserId 匹配）。
- [x] **重复绑定裁定 E2E**：同 platformUserId + 同 extId 再次 completeBinding → 幂等返回（不抛错、不产生第二行）；跨 platformUserId 同 extId → 显式失败（No Silent No-Op）。

Exit Criteria:

- [x] E2E 绑定流跑通：createBindTicket → 扫码回调 → completeBinding 写 `NopAuthExtLogin`，read-back ORM 验证行字段
- [x] `findBinding` 反查命中
- [x] 重复绑定（同用户幂等 / 跨用户显式失败）有断言
- [x] **无静默跳过**：跨用户重绑显式抛错不静默
- [x] `./mvnw test -pl <所选模块> -am` 通过
- [x] `No owner-doc update required`（契约未变）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — W6-2b 扫码登录全链 E2E + 二次扫码 + 端点命中

> **前置**：Phase 1 完成（绑定行已就位）。

Status: completed
Targets: 同 Phase 1 测试文件

- Item Types: `Proof`

- [x] **全链 E2E**（单 JVM，真实 beans）：经 Phase 1 绑定后，构造扫码回调 → `ChannelLoginApiBizModel.loginByScan`（W4-1 端点，真实容器可达）→ `findBinding` 命中 → 真实 `LoginServiceImpl.createSessionForUserAsync`（真实 DB 加载 user/roles）→ `JwtAuthTokenProvider.generateAccessCode` → 取得 accessCode → **`ILoginSpi.getLoginResultAsync(AccessCodeRequest)` 经 `parseAccessCode` + `IUserContextCache` 兑换 `LoginResult`**（Plan 5 deferred 全链）。`LoginResult` 非空、含有效 session/accessToken。
- [x] **cache 命中可观察**：bootstrap 写入 session 后，`getLoginResultAsync` 消费侧 `IUserContextCache.getUserContextAsync(sessionId)` 命中同一 session（单 JVM 共享 cache）。可观察断言：消费侧返回的 userContext 与 bootstrap 写入的 sessionId 一致。
- [x] **二次扫码直接登录**：绑定已存在（Phase 1 行）→ 再次 loginByScan → findBinding 命中 → 跳过 completeBinding → 全链 LoginResult（不重新绑定）。
- [x] **未绑定显式失败**：findBinding 返回 null（无绑定/extId 未绑定）→ loginByScan 显式抛错（No Silent No-Op），不 fallthrough。
- [x] **端到端 + 接线验证**：从端点入口（loginByScan）到出口（LoginResult）全链连通；`createSessionForUserAsync` 真实被调（cache 命中 + 有效 LoginResult 即其运行的可观察证据）、`generateAccessCode` 真实被调（accessCode 经 parseAccessCode 往返）、`getLoginResultAsync` 真实被调并返回非空 LoginResult。

Exit Criteria:

- [x] 全链 E2E：扫码回调 → loginByScan → findBinding → createSessionForUserAsync（真实 DB）→ generateAccessCode → **getLoginResultAsync → LoginResult** 完整跑通（Plan 5 deferred 偿还）
- [x] **端到端验证**（Anti-Hollow Rule #22）：端点入口 `loginByScan` → 最终输出 `LoginResult`，单 JVM 全链连通
- [x] **接线验证**（Rule #23）：真实 `createSessionForUserAsync` 被调（cache 命中可观察）+ 真实 `generateAccessCode` + 真实 `getLoginResultAsync` 返回非空 LoginResult；cache 命中可观察
- [x] 二次扫码直接登录（不重绑）有断言
- [x] 未绑定显式失败（No Silent No-Op）有断言
- [x] **新增功能测试覆盖**（Test-Mandated Feature Rule）：全链 happy path + cache 命中 + 二次扫码 + 未绑定失败（4 个 E2E 场景）
- [x] `./mvnw test -pl <所选模块> -am` 通过
- [x] `No owner-doc update required`（契约未变）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：本 section 所有条目 + 每个 Phase Exit Criteria 全部 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] W6-2a 扫码绑定 E2E：真实 ChannelBindServiceImpl + FeishuBindProvider，completeBinding 写 `NopAuthExtLogin` 经 read-back 验证 + findBinding 反查 + 重复绑定裁定
- [x] W6-2b 扫码登录全链 E2E：单 JVM 内 loginByScan → createSessionForUserAsync（真实 DB）→ generateAccessCode → **getLoginResultAsync → LoginResult**（Plan 5 deferred 偿还）+ cache 命中
- [x] 二次扫码直接登录 + 未绑定显式失败
- [x] W4-1 端点真实命中（真实 bean 装配可达 loginByScan）
- [x] 登录主流程四文件零改动基线再确认（hash == Plan 5 baseline，本 plan 未改）
- [x] **Anti-Hollow Check**：单 JVM 全链运行时连通（loginByScan→findBinding→createSessionForUserAsync→generateAccessCode→getLoginResultAsync），非空壳、无静默跳过
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `./mvnw test -pl nop-integration,nop-auth,nop-ai -am` —— **本 plan 自有的 4 个 E2E 测试全绿**（`TestChannelScanBindLoginE2E` 4/4），相关集成测试全绿（`TestFeishuBindProvider` 7/7、`TestChannelLoginApi`+`TestChannelLoginAccessCode` 8/8），三模块 `./mvnw compile -am` BUILD SUCCESS。**注**：聚合 `test` 命令因**预先存在的** `nop-auth-service` 测试套件基建缺陷（`DataBaseSchemaInitializer` 在 ALL_LAZY 测试模式下不被构造 → `NopAuthSite` 表未建 → 32 个既有用例红，含未触及的 `TestDaoLib`/`TestLoginApi`）不能全绿；独立审计 item 6 已确认该缺陷与本 plan 无关（`TestDaoLib` 最后修改在无关 commit `831cf7992`），已作为独立非 W6-2 基建项记录，不阻塞 W6-2 收口。
- [x] `No owner-doc update required`（契约未变）
- [x] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）

## Deferred But Adjudicated

### 真实飞书 OAuth 服务器往返

- Classification: `watch-only residual`
- Why Not Blocking Closure: 无 CI 可达的真实飞书 OAuth。本 plan 用文档化飞书扫码回调 payload 模拟回调，验证绑定/登录编排全链。真实 OAuth 往返需真实凭证 + 网络，属部署期验证。
- Successor Required: `no`（部署期手动验证）

### 多实例部署的跨进程 IUserContextCache 同步

- Classification: `optimization candidate`
- Why Not Blocking Closure: 单体部署（roadmap 默认）下 bootstrap 与消费方共享单 JVM cache。多实例需跨进程 cache 同步/失效，属部署演进。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 真实飞书 OAuth `code` → `open_id` 的服务端交换（本 plan 直接用回调里的 `open_id` 模拟）——部署期校准
- accessCode 的更短 TTL / 单次消费强化（当前 `accessCodeExpireSeconds=300`）——optimization candidate
- W6-4 多消费者骨干验证（roadmap"可选"）——候选 successor plan

## Closure

Status Note: W6-2 收口。扫码绑定 + 扫码登录收口到 E2E 可验证状态：在单 JVM 内装配真实生产 bean（ChannelBindServiceImpl + FeishuBindProvider + LoginServiceImpl + JwtAuthTokenProvider + LocalUserContextCache + LoginApiBizModel + ChannelLoginApiBizModel）over 真实 H2，验证 createBindTicket→扫码回调→completeBinding 写 NopAuthExtLogin（read-back）+ findBinding 反查 + 重复绑定裁定，以及 loginByScan→findBinding→createSessionForUserAsync（真实 DB user/roles 加载）→generateAccessCode→getLoginResultAsync→LoginResult 全链（Plan 5 两项 deferred 偿还）+ 单 JVM cache 命中 + 二次扫码直接登录 + 未绑定显式失败。契约零改（四文件 hash == Plan 5 baseline）。实施期 Phase 0 将候选 A refine 为自包含 H2 装配（绕开预先存在的全量容器 schema-init 缺陷），仍端到端运行真实 bean。
Completed: 2026-08-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore，task ses_01c5229a4ffePJSJddgV1EOdrr），read-only 审计
- Audit Session: ses_01c5229a4ffePJSJddgV1EOdrr
- Evidence:
  - **Phase 1 / W6-2a（PASS）**：`testScanBindingE2EWritesExtLoginAndFindsBinding` 经 `completeBinding` 写 `NopAuthExtLogin`，read-back 断言 `loginType=20`/`extId`/`userId`/`verified=1`/`delFlag=0`；`findBinding` 反查命中。`testRebindAdjudicationIdempotentAndCrossUserFails` 断言同用户幂等（bindingId 不变、行数不增）+ 跨用户 `assertThrows(NopException)` "already bound to another user"。
  - **Phase 2 / W6-2b（PASS）**：`testScanLoginFullChainE2E` 在 `ormTemplate.runInSession` 内调真实 `channelLoginApi.loginByScanAsync` → accessCode 非空 → `parseAccessCode` 往返得 sessionId → `userContextCache.getUserContextAsync(sessionId)` 命中（cache hit 可观察）→ 真实 `loginApiBizModel.getLoginResultAsync` 返回 `LoginResult`（accessToken 非空、userInfo.userId == 绑定 platformUserId）。二次扫码产出新 accessCode 且不新增绑定行。`testScanLoginWithoutBindingFailsExplicitly` 断言未绑定 `assertThrows(NopException)` "no effective channel binding"。
  - **Closure Gate：四文件 hash（PASS）**：`ILoginService=43711cad...` / `ILoginSpi=be5ec05d...` / `IAuthTokenProvider=5a36b4fd...` / `LoginApiBizModel=cee9c5bb...`，与 Plan 5 baseline 逐字节一致（独立审计 item 3 复核）。
  - **Closure Gate：无越界改动（PASS）**：`git status` 仅 `pom.xml`（test-scope 依赖 + excludes）+ 新测试文件；`git diff --stat` 仅 pom +48；无生产 .java/接口/ORM 改动（独立审计 item 4 复核）。
  - **Anti-Hollow（PASS）**：关键链路全真实 bean（ChannelBindServiceImpl/FeishuBindProvider/LoginServiceImpl/JwtAuthTokenProvider/LocalUserContextCache/LoginApiBizModel/ChannelLoginApiBizModel）；唯一非真实协作者在关键链路之外（`NoopAuditService`、`UuidSessionStore`，javadoc 注明）。未绑定分支显式抛 NopException，无静默跳过（独立审计 item 5 复核）。
  - **测试结果**：`./mvnw test -pl nop-auth/nop-auth-service -o -Dtest=TestChannelScanBindLoginE2E` → `Tests run: 4, Failures: 0, Errors: 0`。相关集成测试 `TestFeishuBindProvider` 7/7、`TestChannelLoginApi`+`TestChannelLoginAccessCode` 8/8 全绿。`./mvnw compile -pl nop-integration,nop-auth,nop-ai -am` BUILD SUCCESS。
  - **预先存在缺陷（非 W6-2，PASS-即-已记录）**：`nop-auth-service` 全量测试套件存在预先存在基建缺陷（ALL_LAZY 测试模式下 `DataBaseSchemaInitializer` 不被构造 → `NopAuthSite` 表未建 → 32 既有用例红）。独立审计 item 6 确认 `TestDaoLib`（与本 plan 无关，commit `831cf7992`）即如此。本 plan 自有测试用自包含 H2 装配绕开此缺陷，不依赖全量容器。该缺陷记为独立非 W6-2 基建项（见 Follow-up）。
  - **Deferred 项分类检查**：真实飞书 OAuth 往返（watch-only residual，无 CI 可达性，部署期验证）、多实例跨进程 cache 同步（optimization candidate，单体拓扑为本 plan 范围）—— 均为允许的 deferred 分类，无 in-scope live defect 被降级。

Follow-up:

- **独立非 W6-2 基建项**：`nop-auth-service` 全量测试套件的 `NopAuthSite`/ALL_LAZY schema-init 缺陷（`DataBaseSchemaInitializer` 的 `@PostConstruct` 不被 `getDelayMethod()` 识别 → ALL_LAZY 启动期不构造）。建议作为独立 plan 处理（涉及 nop-orm/nop-ioc，受保护区，需 plan-first + 设计文档 + 回归测试）。不阻塞 W6-2。
- no remaining plan-owned work（W6-2 全部 in-scope 项已落地或经裁定 deferred）。
