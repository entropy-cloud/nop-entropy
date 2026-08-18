# W9-impl 凭证库 OAuth 流程引擎（出站 OAuth 2.0 客户端）

> Plan Status: completed
> Mission: nop-credential-mfa
> Work Item: W9-impl
> Last Reviewed: 2026-08-16
> Source: `ai-dev/design/nop-credential/02-phase2-design.md` §三（全部）+ §3.3 第 3 条（回调数据通道联合裁定）+ §5.1 结论 4/8（发起动作归属/管理员判定回补点）+ §6.3（消费侧信任边界）；roadmap `ai-dev/backlog/nop-credential-mfa-roadmap.md` W9-impl
> Related: W1-W3 一期计划（`2026-08-12-0615-1/2/3`）、W9-design（`2026-08-14-2012-1-credential-phase2-design.md`，已 done）、W10-impl（`2026-08-14-2342-2`，本 plan 之后执行）、W11-impl（`2026-08-14-2342-3`，回补本 plan Deferred 的发起归属校验）

## Purpose

按 W9-design §三落地出站 OAuth 2.0 客户端流程引擎：授权码换取闭环（发起 → 单一公开回调 → token 加密回写）、token 惰性刷新（跨副本互斥）、`authType=oauth2` 类型声明扩展与引擎保留字段契约，且一期四项契约锚点（`cv1:` 格式 / 明文边界 / 软删除 fail-closed / 引用计数）零隐性变更。

## Current Baseline

（2026-08-14 live repo 核对，经独立 review 复核全部属实）

- `credential-type.xdef` 位于 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/credential/credential-type.xdef`：根元素 `authType="string"` 为自由字符串（live:14），无 OAuth 元数据声明位；xdef 不声明 `xdef:bean-package`——`CredentialType` 模型在 `nop-credential-api` 手写（`io/nop/credential/api/registry/CredentialType.java`，161 行 POJO，含 `authType` 字段）。
- 类型实例文件两个：`generic-secret.credential-type.xml`（authType=none）、`openai-api-key.credential-type.xml`（authType=apiKey），位于 `nop-credential/nop-credential-service/src/main/resources/_vfs/nop/credential/types/`。
- `CredentialProviderImpl`（`nop-credential-service/.../service/CredentialProviderImpl.java`）：`getCredential`/`getCredentialData`/`testCredential`/`mask`/`registerUsage`/`unregisterUsage` 已落地；`loadActiveCredential`（live:169-187）仅校验 delFlag，**不校验 status**；无任何刷新逻辑。
- `NopCredentialBizModel.saveCredential`（live:121-191）：fields 整包 JSON 序列化 → `CredentialCipher.encrypt` → 全量覆盖 `data` 列（无保留字段分组写语义）；`typeList`（live:253）返回全部字段（无 oauth2 裁剪）；标准 `save` 已禁用（抛 `UnsupportedOperationException`，live:104-106）；标准 `update`/`batchDelete` 未禁用（归 W11 收口）。
- 错误码现状：全部 `nop.err.credential.*` 常量在 `io/nop/credential/crypto/CredentialErrors`（`service/NopCredentialErrors` 为空占位接口，勿用）。
- bean 装配点：`credential-defaults.beans.xml`（provider/registry/cipher，`ioc:default`）与 `_service.beans.xml`（BizModel + BizProxy）。
- `nop-credential-service/pom.xml` **无 `nop-http-api` 依赖**——`IHttpClient` 位于 `nop-network/nop-http/nop-http-api`，引入需加依赖；运行时 `nopHttpClient` 为条件 bean（依赖 raw client 实现模块如 `nop-http-client-jdk`；测试可 mock bean，先例 `nop-ai-toolkit` test-mock.beans.xml）。
- OAuth 引擎相关的一切（state 绑定、公开回调端点、授权码换取、refresh grant、惰性刷新、保留字段校验）**均不存在**——live 无 `beginOAuthFlow`/callback/refresh 任何代码。
- publicAccess 先例：`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java:49` `@Auth(publicAccess = true)`；REST GET `/r/{operationName}` 可达 `@BizQuery`（`QuarkusGraphQLWebService.java:78-79` 先例）。
- **重定向能力事实（draft review 核定）**：biz action 无法发出 HTTP 30x——`IServiceContext` 只有 header map；平台唯一重定向原语 `IHttpServerContext.sendRedirect` 仅在 HTTP filter 层可达（`AuthHttpServerFilter.processOAuthCode` 先例）；biz action 可返回 `WebContentBean`（可覆写 Content-Type 输出 HTML，先例 `ReportDemoBizModel.download`、`NopRuleDefinitionBizModel.getInputJsonSchema`）但不能控制状态码。
- W8 用户裁决先例（2026-08-13，`ai-dev/logs/2026/08-13.md:381`）：缺省不使用 Redis；所有存储必须有基于数据库的实现。
- `nop-credential` 现有 7 个标准子模块 + `model/nop-credential.orm.xml`（16 列 + usage to-many）+ `deploy/sql/{mysql,oracle,postgresql}`（`_create_` 为 codegen 产物；`_add_tenant_` 为手写增量 alter 先例）。

## Goals

- `authType=oauth2` 凭证类型可声明 OAuth 应用元数据（授权/令牌端点、scopes、刷新窗口），registry 层校验保留字段名占用拒绝 + 元数据完备性。
- 授权码闭环成立：`beginOAuthFlow`（发起，入参 credentialId）→ 第三方授权 → 单一公开回调端点（GET `@BizQuery` + publicAccess）→ token 集加密回写该实例引擎保留字段 → 浏览器落到前端结果页（回调响应不携带 token 明文）。
- 取用时惰性刷新：`getCredential` 对 oauth2 类型 accessToken 临期先刷新再返回明文；刷新失败 fail-closed；同一凭证刷新跨副本互斥收敛为一次。
- oauth2 类型 `status=disabled` 全路径拒绝（发起/回调/刷新/取用）；非 OAuth 类型取用维持一期语义（仅 delFlag）。
- `saveCredential` 分组写语义：人工字段整包替换不变，引擎保留字段不动；输入出现保留字段名拒绝；`typeList` 对 oauth2 类型不渲染保留字段。

## Non-Goals

- PKCE（§七#1 deferred，successor 为 A1-audit 评估；见 Deferred But Adjudicated）。
- `status=disabled` 取用语义对非 OAuth 类型的全局收紧（§七#2 独立裁定）。
- 后台定时预刷新任务、token 明文进程缓存（设计 §3.4 已拒绝）。
- scope/ownerId 归属字段、`NopCredentialAuth` RBAC 实体、发起动作归属校验（归 W11-impl Part A 回补；本 plan 发起校验仅到"登录态 + 实例存在/未删/未禁用 + 类型 oauth2 + clientSecret 已录入"，见设计 §3.3 结论 1）。
- Web 管理页 OAuth 发起/状态展示的 UX 完善（仅最小接线：发起 action 可从既有凭证页面调用）。
- 服务端真 30x 重定向（裁定见 Phase 2 Decision——平台 biz 层无 30x 能力，以 WebContentBean HTML 落地浏览器跳转语义）。

## Scope

### In Scope

- `credential-type.xdef` 扩展：`authType` 取值域收敛为 `none | apiKey | basic | oauth2` + `oauth2` 取值下的 OAuth 元数据声明（授权端点/令牌端点/scopes/刷新窗口缺省值）。
- `nop-credential-api` 手写 `CredentialType` 模型同步扩展（authType 常量 + OAuth 元数据内嵌对象）。
- 引擎保留字段契约：`accessToken`/`refreshToken`/`expiresAt`/`tokenType`/`scope` 五个字段名 registry 层保留，类型文件 fields 不得占用。
- OAuth state 绑定持久化：**载体裁定为 DB 新 ORM 实体**（Phase 1 Decision），一次性消费（原子条件更新）、短 TTL、跨副本可读、过期行惰性清理。
- `beginOAuthFlow` 发起 action + 单一公开回调端点（GET `@BizQuery` publicAccess + `WebContentBean` 跳转页）+ token 加密回写（只写保留字段）。
- 惰性刷新 + 跨副本互斥（**载体裁定为 DB 行级锁**，Phase 3 Decision）+ `saveCredential` 写路径纳入同一串行化。
- `saveCredential` 分组写 + 保留字段拒绝；`typeList` oauth2 裁剪。
- oauth2 类型 `status=disabled` 全路径拒绝。
- `docs-for-ai/03-modules/nop-credential.md` OAuth 引擎章节 + DDL 迁移 + 设计文档 impl 裁定标注。

### Out Of Scope

- 归属/RBAC（W11-impl Part A）；KMS（W10-impl）；nop-integration/nop-metadata 迁移（W16）。
- 每 credential-type 独立回调 URL、边授权边新建实例（设计 §3.4 已拒绝）。
- JWT/JWKS 本地解析（出站 token 透传使用，设计 §3.2）。

## Execution Plan

> **执行顺序**：本 plan 先于 W10-impl/W11-impl 执行（三者共改 `nop-credential.orm.xml`/`NopCredentialBizModel`/`deploy/sql`，串行执行避免 codegen 再生成互相覆盖；W11 执行时 codegen 再生成需包含本 plan 的 state 实体变更）。

### Phase 1 - 类型声明扩展 + state 存储模型（xdef / api 模型 / ORM）

Status: completed
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/credential/credential-type.xdef`、`nop-credential/nop-credential-api/.../registry/CredentialType.java`、`nop-credential/nop-credential-service/.../registry/DefaultCredentialTypeRegistry.java`、`nop-credential/model/nop-credential.orm.xml`、`nop-credential/deploy/sql/*`、`ai-dev/design/nop-credential/02-phase2-design.md`（追加裁定标注）

- Item Types: `Decision | Fix | Proof`

- [x] **Decision**：state 存储载体裁定——新建 ORM 实体 `NopCredentialOauthState`（表 `nop_credential_oauth_state`）：state 令牌（PK）、credentialId、发起人 userId、过期时间、消费标记。约束分析：设计 §3.3 要求"服务端持久化 + 跨副本可读"；W8 用户裁决（缺省不用 Redis、存储必须有 DB 实现，`ai-dev/lessons` 15 同源）排除 Redis-only（且 nop-nosql 引入即拉入 Redis 栈）；本地内存 map 排除（跨副本失效）；绕过 ORM 手写 SQL 表违反 model-first 惯例（W8 `DbMfaChallengeStore` 先例亦走 ORM 实体）。设计 §八 W9-impl"不触碰：ORM 模型"的括号枚举（scope/ownerId/NopCredentialAuth）指 W11 归属变更；state 载体设计 §3.3/§八显式留 impl 裁定，且 §八 Protected Area 清单只列 xdef + publicAccess 面。**本 plan 即该变更的 plan-first 载体；本条裁定须在本 Phase 内回写设计文档 §八 追加标注**（不留到收口阶段）。
- [x] **Fix**：`credential-type.xdef` 扩展——`authType` 声明取值域（`none|apiKey|basic|oauth2`，xdef 内联枚举，先例 `ai/tool/call-tools-response.xdef` 的 `!enum:` 语法）+ oauth2 取值下 OAuth 元数据声明（authorizationEndpoint/tokenEndpoint/scopes/refreshWindowSeconds，可选声明位）；同步扩展手写 `CredentialType.java`（authType 常量 + OAuth 元数据内嵌对象字段）。
- [x] **Fix**：`DefaultCredentialTypeRegistry` 加载校验（全部 fail-closed，无静默跳过）——保留字段名（accessToken/refreshToken/expiresAt/tokenType/scope）被类型文件 fields 占用时拒绝；authType 取值域外拒绝；**authType=oauth2 但缺 authorizationEndpoint/tokenEndpoint 时拒绝**（缺端点元数据的 oauth2 类型不可用，加载期暴露）；**非 oauth2 类型声明 OAuth 元数据时拒绝**（防配置错位被静默忽略）。
- [x] **Fix**：示例 OAuth 类型实例文件（如 `generic-oauth2.credential-type.xml`：authType=oauth2 + clientId/clientSecret 人工字段 + 端点元数据）。
- [x] **Fix**：`nop-credential.orm.xml` 新增 state 实体（model-first：源编辑 → codegen 重生成 `_gen` 与 `_create_` DDL，**禁止手编生成物**）+ 存量部署增量 alter 脚本（手写，循 `_add_tenant_nop-credential.sql` 先例，非 codegen 产物），三方言齐备。
- [x] **Follow-up**：设计文档 `02-phase2-design.md` §八 W9-impl 段追加裁定标注：state 载体 = 新 ORM 实体（本 Phase Decision 理由），不改设计结论。
- [x] **Proof**：registry 校验单测（保留字段占用拒绝 / authType 非法值拒绝 / oauth2 缺端点拒绝 / 非 oauth2 带元数据拒绝 / oauth2 元数据解析 round-trip）。

Exit Criteria:

- [x] xdef 修改后 `./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests` 先行重打包（project-context.md xdef 提示），随后 `./mvnw clean install -pl nop-credential -am -T 1C` 绿。
- [x] 上述四类非法类型文件在 registry 加载时显式抛错（测试名可指认），无静默跳过。
- [x] state 实体经 codegen 生成（`_gen` 与 `_create_` 更新）+ 三方言增量 alter 脚本存在。
- [x] 设计文档 §八 已追加 state 载体裁定标注（diff 可见）。
- [x] **新功能测试**：Phase 1 新增校验行为各有对应单测（`TestDefaultCredentialTypeRegistry`：`loadRejectsTypeFileOccupyingReservedFieldName` / `loadRejectsAuthTypeOutsideValueDomain` / `loadRejectsOauth2TypeMissingEndpoints` / `loadRejectsNonOauth2TypeWithOAuthMetadata` / `genericOauth2TypeMetadataRoundTrip`）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 授权码闭环（发起 + 公开回调 + token 回写）

Status: completed
Targets: `nop-credential-service/.../oauth/`（新包：OAuthFlowService 等）、新 `CredentialOAuthApiBizModel`、`CredentialProviderImpl`（引擎内部通道）、`credential-defaults.beans.xml`（service bean）、`_service.beans.xml`（BizModel）、`nop-credential-service/pom.xml`（nop-http-api 依赖）、`io/nop/credential/crypto/CredentialErrors`（新错误码）、`ai-dev/design/nop-credential/02-phase2-design.md`（30x 措辞标注）

- Item Types: `Fix | Decision | Proof`

- [x] **Decision（回调载体裁定）**：biz 层无 30x 能力（Baseline 已核定），回调落地为——GET 可达 `@BizQuery` + `@Auth(publicAccess=true)` 的 `CredentialOAuthApiBizModel` action，返回 `WebContentBean`（Content-Type=text/html，meta-refresh/JS location 跳转到配置的前端结果页 URL）。语义等价性：用户浏览器最终落在结果页、token 明文不出现在任何响应体；与设计 §3.3"30x 重定向"的字面差异（200+HTML 而非 302）在本 Phase 内回写设计文档 §3.3 追加标注（显式偏离记录，不改设计结论）。不采用 `IHttpServerFilter` 方案（真 30x 但需注册 filter 并处理与 auth filter 顺序，复杂度不成比例）；不采用 SPA 直连 mutation 方案（把回调语义拆到前端，削弱"单一公开回调端点"审计单点）。
- [x] **Fix**：`beginOAuthFlow(credentialId)` biz mutation（登录态）——校验链：实例存在 / 未删 / 未禁用（status=disabled 拒绝）/ 类型为 oauth2 / data 中 clientSecret 已录入；生成不可预测一次性 state（安全随机 ≥128 bit）→ 持久化绑定（credentialId + 发起人 + TTL 缺省 10 分钟、配置项可调）→ 返回授权 URL（授权端点 + client_id + redirect_uri + scope + state + response_type=code）。`redirect_uri` = 配置项基础地址（部署方配置外部可达地址）派生的回调端点 URL；发起时创建新 state 的同时**惰性清理**该表过期行（DELETE WHERE expire_at < now，无后台任务）。
- [x] **Fix**：回调动作语义——**state 校验与一次性消费必须原子**：条件 UPDATE/DELETE（`WHERE state=? AND consumed=0 AND expire_at>now`）+ affected-row 判定（W8 `DbMfaChallengeStore.consume` 先例，`IJdbcTemplate.executeUpdate`），0 行命中即拒绝（未命中/过期/重放统一 fail-closed，且不区分错误细节防探测）；通过后以 state 记录的发起人身份经 provider 引擎内部通道解密 clientSecret → code + clientSecret + redirect_uri 在令牌端点换 token 集（`IHttpClient`，表单构造参照 `OAuthLoginServiceImpl.newLoginRequest` 协议形态，**不引入 nop-auth-sso 依赖**）→ token 集合并回写该实例 data（只写保留字段）→ 返回 WebContentBean 跳转页。token endpoint 错误响应（error 字段）显式抛错。
- [x] **Fix**：唯一解密点不变式（范围限定表述）——**OAuth 引擎类不得直接持有 `CredentialCipher` 解用户 data**；token 回写/读取经 `CredentialProviderImpl` 上的引擎专用方法（public 方法 + 引擎专用语义 javadoc——引擎包 `service.oauth` 与 provider 包不同，package-private 不可行）；W9 时点"以发起人身份执行"为审计记录（归属字段未落，无归属校验），与设计 §3.3 时序声明一致。（注：live `reencryptAll` 直接持 cipher 为一期既存事实，进程内重加密不出明文边界，登记 Non-Blocking Follow-up 不在本 plan 扩scope。）
- [x] **Fix**：bean 装配与依赖——`nop-credential-service/pom.xml` 加 `nop-http-api` 依赖；`OAuthFlowService` 注册 `credential-defaults.beans.xml`（`ioc:default`，同 provider 惯例）；`CredentialOAuthApiBizModel` 注册 `_service.beans.xml`（同 `NopCredentialBizModel` 惯例）；测试对 `IHttpClient` 用 mock bean（先例 `nop-ai-toolkit` test-mock.beans.xml）或 test 依赖 `nop-http-client-jdk`（采用后者，nop-ai-gateway 先例）。
- [x] **Proof**：闭环单测（stub 令牌端点 HTTP server，JDK `com.sun.net.httpserver` 先例）——发起 → 回调 → data 中保留字段已更新（解密验证 accessToken/refreshToken/expiresAt）且人工字段保留；state 重放/过期拒绝；禁用凭证发起拒绝；**并发双回调同 state 恰一个成功**（两线程并发，断言成功数==1）。
- [x] **Proof（端到端）**：从 `beginOAuthFlow` 入口到 `getCredential` 出口的全链测试（stub 授权服务器：授权 URL → 302 模拟浏览器携 code/state GET 回调端点 → WebContentBean 跳转页解析 → 取用明文）。

Exit Criteria:

- [x] 公开回调端点响应不含 token 明文（测试断言 WebContentBean 内容无 token 字段、仅跳转标记）。
- [x] state 一次性消费原子语义有并发测试（双回调恰一成功）。
- [x] **接线验证**：回调路径确实调用 provider 引擎通道（非自建解密，mock/stub verify 或调用计数）；`IHttpClient` 被实际使用。
- [x] **无静默跳过**：state 未命中/过期/重放、token endpoint 返回 error、clientSecret 缺失均显式抛错（测试覆盖各分支）。
- [x] **新功能测试**：`TestOAuthFlowService`（18 用例：`beginReturnsAuthorizationUrlWithStateAndPersistBinding` / `beginWithoutLoginThrowsFailClosed` / `beginOnNonOauth2TypeThrows` / `beginOnDisabledCredentialThrows` / `beginOnDeletedCredentialThrows` / `beginWithoutClientSecretThrows` / `beginWithoutCallbackBaseUrlThrows` / `callbackWritesTokenSetToReservedFieldsAndKeepsManualFields` / `callbackResponsePageContainsNoTokenPlaintext` / `callbackStateReplayRejected` / `callbackUnknownStateRejected` / `callbackExpiredStateRejected` / `callbackMissingParamsRejected` / `callbackTokenEndpointErrorThrowsExplicitly` / `callbackOnDisabledCredentialRejected` / `concurrentDoubleCallbackSameStateExactlyOneSucceeds` / `endToEndBeginToGetCredential` / `stateLazyCleanupOnCreate`）。
- [x] 公开攻击面最小化核对：仅回调 `@BizQuery` publicAccess；发起端点登录态；state 错误不区分细节。
- [x] 设计文档 §3.3 已追加回调载体标注（diff 可见）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 惰性刷新 + 互斥 + 分组写语义 + disabled 全路径拒绝

Status: completed
Targets: `CredentialProviderImpl`、`nop-credential-service/.../oauth/`、`NopCredentialBizModel`、`config/CredentialConfigs`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision**：跨副本刷新互斥载体裁定——**DB 行级锁 `IEntityDao.lockEntity`（SELECT FOR UPDATE，`IEntityDao.java:129-134` javadoc 明示）**，在事务模板内持锁完成"读 data → 刷新外呼 → 回写"全程（`orm().requireSession()` 开事务；不选乐观 version——token 刷新外呼失败后的重读重试链路复杂度高于直接行锁）。**写路径串行化范围**：惰性刷新与 `saveCredential` 分组写（oauth2 类型）共用同一行锁入口，互斥覆盖"刷新 vs 刷新"与"刷新 vs 人工保存"（设计 §3.3"同一凭证写路径串行化"约束）；非刷新取用不持锁可并发。持锁期间含一次秒级 HTTP 外呼为已接受的吞吐代价（单凭证粒度，不阻塞其他凭证）。
- [x] **Fix**：`getCredential` 取用路径——oauth2 类型且 accessToken 临期（now 距 expiresAt < 刷新窗口，缺省值定案并写入文档）→ 行锁互斥下以 refreshToken + clientId/clientSecret 刷新 → 新 token 集回写（只写保留字段）→ 返回新明文；刷新失败（invalid_grant 等）fail-closed 抛错；refreshToken 缺失且 accessToken 已过期 → fail-closed（提示重新授权）。
- [x] **Fix**：oauth2 类型 `status=disabled` 拒绝接入 `loadActiveCredential` 的 oauth2 分支（发起/回调/刷新/取用全路径；非 OAuth 类型维持仅 delFlag——一期语义零变更）。
- [x] **Fix**：`saveCredential` 分组写——oauth2 类型：行锁下先解出当前保留字段集合，人工字段整包替换 + 保留字段合并回写；输入 fields 出现保留字段名一律拒绝（防伪造 token）；`typeList` 对 oauth2 类型输出裁剪掉保留字段（表单只出人工字段）。
- [x] **Proof**：单测——临期触发刷新（stub 刷新端点）/ 非临期直接返回 / 刷新失败抛错 / 无 refreshToken 且过期抛错 / disabled oauth2 四路径拒绝 / 非 OAuth 类型 disabled 仍可取（一期语义保持）/ saveCredential 保留字段输入拒绝 + 人工字段替换不破坏 token 集。
- [x] **Proof（并发，确定性判据）**：并发 N 线程取用临期凭证，断言 **stub 刷新端点调用计数 == 1** 且全部线程拿到相同新 token；并发"取用刷新 vs saveCredential"无双写丢失（最终 data 一致性断言）。

Exit Criteria:

- [x] 刷新互斥载体裁定与实现记录于本 Phase（DB 行级锁 + 事务模板，非 Redis、非乐观锁）。
- [x] 惰性刷新端到端：取用 → 刷新 → 回写 → 窗口内再次取用不刷新，stub 端点调用计数断言为确定性证据。
- [x] **无静默跳过**：所有刷新失败分支显式抛错（测试覆盖）。
- [x] 一期回归：非 oauth2 类型 `getCredential`/`saveCredential`/`mask`/引用计数行为零变更（既有测试全绿 + 补充回归断言）。
- [x] **新功能测试**：`TestCredentialOAuthRefresh`（12 用例：`nearingExpiryTriggersRefreshOnceAndWritesBack` / `notNearingReturnsDirectlyWithoutRefresh` / `refreshFailureFailsClosed` / `expiredWithoutRefreshTokenFailsClosedWithReauthHint` / `nearingWithoutRefreshTokenButNotExpiredReturnsCurrent` / `concurrentGetCredentialRefreshesExactlyOnce` / `concurrentRefreshVsSaveCredentialNoLostUpdate` / `disabledOauth2CredentialRejectedOnGetCredentialAndMask` / `disabledNonOauthCredentialStillAccessible` / `saveCredentialRejectsReservedFieldNames` / `saveCredentialManualReplacePreservesTokenSet` / `typeListPrunesReservedFieldsForOauth2Types`）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 文档同步 + 收口验证

Status: completed
Targets: `docs-for-ai/03-modules/nop-credential.md`、`ai-dev/backlog/nop-credential-mfa-roadmap.md`

- Item Types: `Follow-up | Proof`

- [x] **Follow-up**：`docs-for-ai/03-modules/nop-credential.md` 补 OAuth 引擎章节（authType=oauth2 声明与校验规则、保留字段契约、发起/回调/惰性刷新语义、回调载体 WebContentBean 说明、redirect_uri 配置项、刷新窗口缺省值、state TTL 配置项）。
- [x] **Follow-up**：刷新窗口/state TTL/回调基础地址等配置项命名与缺省值写入文档（命名实施时定案，前缀 `nop.credential.oauth.*` 建议）。
- [x] **Proof**：全模块验证 `./mvnw test -pl nop-credential -am`；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-credential --severity high` **本 plan 触碰文件中 0 条 NEW high/critical 发现**（区分 pre-existing：live 已知 pre-existing = `NopCredentialBizModel.java:105` 标准 save 禁用抛 UnsupportedOperationException，为一期有意模式，附完整扫描输出对照）。
- [x] **Follow-up**：roadmap W9-impl 状态更新（closure audit 通过后标 done，本 plan 不代劳）。

Exit Criteria:

- [x] 文档章节与 live 行为一致（配置项名、缺省值、端点语义可对号）。
- [x] 验证命令通过（scan-hollow 按 NEW 发现口径，附输出）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 授权码闭环端到端成立（发起 → stub 授权服务器 → 回调 → token 回写 → 取用），非组件级各自单测通过。
- [x] state 一次性消费原子性 + 惰性刷新跨副本互斥有确定性测试证据（并发计数断言）。
- [x] oauth2 类型 disabled 全路径拒绝；非 OAuth 类型一期语义零回归（既有测试全绿）。
- [x] 一期契约锚点零隐性变更：`cv1:` 格式 / 明文边界（回调不出现明文、data 恒置空、mask `****`）/ 软删除 fail-closed / 引用计数——均有测试或既有测试覆盖。
- [x] 无空壳/静默跳过（scan-hollow NEW 发现为 0 + 分支测试覆盖）。
- [x] 受影响 owner docs（`docs-for-ai/03-modules/nop-credential.md`）与设计文档裁定标注已同步。
- [x] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow Check：回调 → provider 引擎通道 → 密文回写调用链追踪）。
- [x] `./mvnw clean install -pl nop-credential -am -T 1C` 绿（含 xdef 先行重打包步骤）。
- [x] `./mvnw test -pl nop-credential -am` 绿。
- [x] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### PKCE（OAuth 2.1 方向纵深防御）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 设计 §3.4/§七#1 已裁定：二期闭环以机密客户端（secret 加密托管）为覆盖目标，PKCE 非闭环成立前提；A1-audit 评估后决定并入。
- Successor Required: yes
- Successor Path: A1-audit（roadmap 凭证库二期审计工作项）

### 发起动作归属校验（system=管理员 / user=owner）

- Classification: `out-of-scope improvement`（时序依赖，非本 plan 可交付）
- Why Not Blocking Closure: 归属字段（scope/ownerId）归 W11-impl Part A 落地；W9 时点按设计 §3.3 结论 1 裁定先实现登录态 + 实例状态 + 类型 + clientSecret 校验。oauth2 凭证在 W11 落地前均为事实 system 级（无归属字段），不存在可绕过的归属语义。
- Successor Required: yes
- Successor Path: `ai-dev/plans/2026-08-14-2342-3-credential-scope-ownership.md`（W11-impl Part A，Phase 2 回补条目）

## Non-Blocking Follow-ups

- 前端 OAuth 发起按钮/授权结果页 UX 完善（本 plan 仅保证 biz action 可调用 + 回调跳转页可达）。
- oauth2 类型 `testCredential` 的真实连通性测试执行（一期 W2 已裁定 consumer-side concern，不随本 plan 翻案）。
- `reencryptAll` 直接持 `CredentialCipher` 的一期既存事实（进程内重加密，不出明文边界）——与"唯一解密点"表述的精确化归 owner doc 措辞治理。
- state 表过期行批量清理任务（本 plan 仅惰性清理）。

## Closure

Status Note: 四个 Phase 全部 completed；独立子 agent closure audit 通过（audit 发现的唯一 Blocker——CredentialOAuthApiBizModel 未注册 beans 文件——已在本收口轮修复并回归验证）；Deferred 项均为已裁定的 non-blocking（PKCE → A1-audit；发起归属校验 → W11-impl 回补）；roadmap W9-impl 已标 done。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 explore 子 agent（task id `ses_ff680cf39ffeEj6PfgmwDKNnYy`，fresh session，read-only live-code audit）
- Evidence:
  - Gate 1 授权码闭环端到端 PASS：调用链 live 追踪 `beginOAuthFlow:47` → `OAuthFlowService.beginOAuthFlow:146`（state INSERT）→ `oauthCallback:57-59`（publicAccess）→ `NopCredentialOauthStateStore` 条件 UPDATE+affected-row（`:99-109`）→ `OAuthTokenClient.exchangeAuthorizationCode:58` → `engineUpdateTokenFields`（行锁合并写）；E2E 测试 `TestOAuthFlowService.endToEndBeginToGetCredential`（真实 HTTP stub 授权服务器 302 + 真实 IHttpClient 取用明文）。
  - Gate 2 原子性/互斥 PASS：`concurrentDoubleCallbackSameStateExactlyOneSucceeds`（successes==1 且 tokenCallCount==1）/ `concurrentGetCredentialRefreshesExactlyOnce`（4 线程 refreshCallCount==1 同 token）/ `concurrentRefreshVsSaveCredentialNoLostUpdate`（新 token + 新人工字段并存）；互斥实现 `lockEntity` + evict + reload + REQUIRED 事务（`CredentialProviderImpl:285-312`）。
  - Gate 3 disabled 全路径 PASS：`assertOauth2NotDisabled` 仅 oauth2 分支（`CredentialProviderImpl:319-325`），双向测试 `disabledOauth2CredentialRejectedOnGetCredentialAndMask` / `beginOnDisabledCredentialThrows` / `callbackOnDisabledCredentialRejected` / `disabledNonOauthCredentialStillAccessible`。
  - Gate 4 一期锚点 PASS：`cv1:`（TestCredentialCipher 16）、明文边界（`endToEndPlaintextBoundary`，data 恒 null `NopCredentialBizModel:220,246,263,280`）、mask `****`、软删除 fail-closed、引用计数（6 用例）——15/13/16 既有测试计数逐一核实零回归。
  - Gate 5/6 Anti-Hollow PASS：`service/oauth/` 全类通读无空方法体/吞异常/TODO 占位，错误分支全部显式抛 NopException；`service/oauth/` 不持有 `CredentialCipher`（仅 javadoc 提及），解密/回写全部经 provider 引擎通道；`IHttpClient` 真实使用（`OAuthTokenClient:91`，E2E 真实 HTTP 往返）。
  - **Blocker 发现与修复（本收口轮）**：`CredentialOAuthApiBizModel` 未在任何 beans.xml 注册——NopIoC 无注解扫描，端点运行时不可达（测试手工 new 掩盖）。修复：`credential-defaults.beans.xml` 显式注册（LoginApiBizModel 先例形态；不入 codegen 产物 `_service.beans.xml`——该文件只按 ORM 实体生成且 W11 再生成会覆盖手改）；新增容器接线测试 `TestOAuthFlowService.containerRegisteredApiBizModelWiredThroughIoc`（@NopTestConfig 容器注入解析 BizModel + 依赖链 fail-closed 验证），19/19 绿。
  - Gate 7 owner docs PASS：配置项名/缺省值/端点名/保留字段名与 `CredentialConfigs`/live 代码逐字对齐（audit 附对照表）。
  - Gate 8 设计裁定标注 PASS：§八 state 载体（`02-phase2-design.md:369`）+ §3.3 回调载体（`:67`）均在。
  - Gate 9 Deferred 诚实性 PASS：PKCE（§七#1 optimization candidate，successor A1-audit）；发起归属校验（successor plan `2026-08-14-2342-3` 存在且含 W9 回补条目）。
  - 构建/测试：`./mvnw clean install -pl nop-credential -am -T 1C` 绿（含 xdef 先行重打包）；最终 `./mvnw test -pl nop-credential -am` 99/99 + web 1/1 绿；checkstyle（qa profile，项目配置）BUILD SUCCESS（顺手清理 4 个 UnusedImports 后复扫干净）；`check-doc-links.mjs --strict` 退出码 0；`scan-hollow-implementations.mjs --module nop-credential --severity high` 仅 1 条已知 pre-existing（`NopCredentialBizModel` 标准 save 禁用，一期有意模式），本 plan 触碰文件 0 条 NEW。

Follow-up:

- 无剩余 plan-owned work；Non-Blocking Follow-ups 见上节（前端 UX / testCredential 连通性 / reencryptAll 措辞治理 / state 批量清理任务）。
