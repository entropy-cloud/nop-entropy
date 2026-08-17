# nop-auth — 认证与权限模块

## 功能概览

完整的用户认证与授权系统，无需自行实现登录功能。

- 用户管理（注册、登录、密码重置）
- RBAC 角色权限（用户→角色→资源）
- 菜单/按钮级权限控制
- 数据级权限（行级过滤）
- 部门/用户组/岗位体系
- 多租户支持
- SSO 单点登录、OAuth2
- 操作审计日志
- 外部登录方式（微信等）
- 多因子验证（登录级两阶段 + 操作级敏感操作二次验证 + 角色级强制策略与受限会话）

## 默认用户

| 配置项 | 值 |
|--------|-----|
| 默认用户名 | `nop` |
| 默认密码 | `123` |
| 自动创建条件 | 用户表为空时 |
| 控制配置 | `nop.auth.login.allow-create-default-user` |

开发模式跳过登录：`-Dnop.auth.service-public=true`

## 内置角色

| 角色 | 说明 |
|------|------|
| `admin` | 管理员，跳过所有权限检查 |
| `nop-admin` | 系统超级管理员，跳过所有权限检查 |
| `user` | 普通用户，始终通过菜单过滤 |

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopAuthUser | `nop_auth_user` | 用户账号 |
| NopAuthRole | `nop_auth_role` | 角色（支持复合角色 via childRoleIds） |
| NopAuthUserRole | `nop_auth_user_role` | 用户-角色映射 |
| NopAuthResource | `nop_auth_resource` | 菜单/按钮/功能点资源树 |
| NopAuthRoleResource | `nop_auth_role_resource` | 角色-资源映射 |
| NopAuthRoleDataAuth | `nop_auth_role_data_auth` | 角色数据权限规则 |
| NopAuthSite | `nop_auth_site` | 子站点（多站点支持） |
| NopAuthDept | `nop_auth_dept` | 部门层级 |
| NopAuthGroup | `nop_auth_group` | 用户组 |
| NopAuthPosition | `nop_auth_position` | 岗位 |
| NopAuthTenant | `nop_auth_tenant` | 多租户 |
| NopAuthSession | `nop_auth_session` | 会话日志 |
| NopAuthOpLog | `nop_auth_op_log` | 操作审计日志 |
| NopAuthExtLogin | `nop_auth_ext_login` | 外部登录方式 |
| NopAuthMfaSetting | `nop_auth_mfa_setting` | MFA 设置（userId PK / mfaType / secret 加密 / status / phone / lastVerifiedWindow） |
| NopAuthMfaRecoveryCode | `nop_auth_mfa_recovery_code` | MFA 恢复码（codeHash BCrypt 加盐 / used / expireAt） |
| NopAuthMfaChallenge | `nop_auth_mfa_challenge` | MFA 挑战令牌（challengeToken PK / userId / mfaType / expireAt / failCount / scene / payload / verifiedAt，W8 DB 存储 + W12 场景化） |
| NopAuthSmsCode | `nop_auth_sms_code` | 短信验证码（codeKey PK / phone / code / expireAt / failCount，W8 DB 存储） |
| NopAuthRoleMfaPolicy | `nop_auth_role_mfa_policy` | 角色级 MFA 强制策略（roleId PK 1:1 / minMfaLevel 1-3 / allowTrustedDevice / delFlag 软删除——无行 = 无策略，W13） |
| NopOauthAuthorization | `nop_oauth_authorization` | OAuth2 授权记录 |
| NopOauthRegisteredClient | `nop_oauth_registered_client` | OAuth2 客户端注册 |
| NopOauthAuthorizationConsent | `nop_oauth_authorization_consent` | OAuth2 用户同意（授权许可） |

> OAuth 实体类名规范为 `NopOauth*`（`Oauth` 中 a 小写，包名 `io.nop.oauth.dao.entity`）。注意 Java 区分大小写，勿写成 `NopOAuth*`。

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-auth-api` | API DTO 与接口定义 |
| `nop-auth-dao` | ORM 实体与 DAO |
| `nop-auth-service` | 业务逻辑（登录、权限校验、数据权限） |
| `nop-auth-web` | Web 层与 AMIS 页面 |
| `nop-auth-sso` | SSO 集成 |
| `nop-oauth` | OAuth2 服务端 |

## 关键配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.auth.service-public` | `false` | `true` 时跳过登录 |
| `nop.auth.enable-action-auth` | `false` | 是否启用操作权限检查 |
| `nop.auth.login.allow-create-default-user` | `true` | 用户表为空时自动创建 nop 用户 |
| `nop.auth.defaultPublic` | `true` | 所有路径默认公开，仅 authPaths 中的需认证 |

## 认证路径规则

- 需认证路径：`/graphql*`、`/r/*`、`/p/*`、`/f/*`、`/jsonrpc`、`/px/*`
- 公开路径：`/r/LoginApi_*`、`/q/health*`、`/q/metrics*`

## 源码锚点

| 组件 | 路径 |
|------|------|
| 认证过滤器 | `nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml` |
| 操作权限检查 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultActionAuthChecker.java` |
| 数据权限检查 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultDataAuthChecker.java` |
| 站点地图 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/sitemap/SiteMapProviderImpl.java` |
| 操作级 MFA 拦截判定 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/OperationMfaCheckerImpl.java` |
| 角色级策略评估器 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/RoleMfaPolicyEvaluator.java` |
| OAuth 接入 MFA 判定 SPI | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/mfa/IMfaLoginPolicyService.java` |
| 受限会话 executor 分支 | `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java`（`checkOperationMfa`） |
| 登记通道验证端点 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java`（`verifyChannelProof`） |
| 共享因子校验组件 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaFactorVerifier.java` |
| 操作级验证端点 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java`（`mfaVerifyOperation`） |
| ORM 模型 | `nop-auth/model/nop-auth.orm.xml` |

## 多因子验证（MFA）

nop-auth 提供完整的两阶段登录（第一因子 → challenge → 第二因子）与短信验证码登录，基于 TOTP（RFC 6238）。

### 登录类型（loginType）

| 值 | 含义 |
|----|------|
| 1 | 用户名 + 密码 |
| 2 | 邮箱 + 密码 |
| 3 | 手机号 + 密码 |
| 4 | 单点登录（SSO） |
| 5 | 手机号 + 短信验证码（W5 新增） |
| 20-23 | 信道登录（飞书 / 钉钉 / 企微 / Webhook） |

字典定义：`nop-biz-auth-core/.../_vfs/dict/auth/login-type.dict.yaml`。

### 两阶段登录流程

1. **第一因子校验**：`LoginServiceImpl.loginAsync()`（`:241`）校验用户凭证（密码/SSO/信道）。
2. **MFA 拦截**：`checkMfaRequired()`（`:743`）——`nop.auth.mfa.enabled` 开关 + 角色策略评估（W13 第三态：有策略且不达标 → 受限会话签发，不建 challenge）+ 用户 MFA 设置检查（status==enabled）。SSO/信道登录同样拦截（`createSessionForUserAsync`，`:337`，loginType 参数化保证审计不失真）。
3. **创建 challenge**：`MfaChallengeStore.create()` 生成一次性 challengeToken。
4. **抛 `ERR_AUTH_MFA_REQUIRED`**（`:319`）：errorParams 携带 challengeToken / mfaType / loginType。未启用 MFA 的用户零感知（直接进 completeLogin）。
5. **第二因子验证**：客户端调 `LoginApi.mfaVerify`（`LoginApiBizModel.mfaVerifyAsync`，`:122`）→ `LoginServiceImpl.mfaVerifyAsync`（`:422`）：peek challenge → setting 复核 → TOTP / SMS / 恢复码分支 → 成功后 `consume` challenge 并 `completeMfaLogin`（`:601`）签发 token。

> 因子等同分支：PHONE_SMS 登录 + mfaType==SMS 不重复验证。恢复码分支：BCrypt 比对，成功后 consume + status=disabled 强制重绑。

### 短信验证码登录（loginType=5）

- `LoginApi.sendSmsCode`（`LoginApiBizModel.sendSmsCode`，`:102`）：向手机号发码（publicAccess）。
- 限流：60s 间隔 / 日上限 / IP 上限 / 防枚举统一响应。
- `mfaVerify` 复用同一验证路径。

### TOTP 验证器

`TOTPAuthenticator`（`nop-biz-auth-core/.../totp/TOTPAuthenticator.java`）：RFC 6238——HMAC-SHA1 / 6 位 / 30s 周期 / ±1 窗口偏差。**防重放**：通过时更新 `lastVerifiedWindow`，拒绝时不更新。secret 经 `AESTextCipher` 加密存储。

### MFA 存储

| store-type | 实现 | 激活条件 |
|------------|------|---------|
| `db`（默认） | `DbMfaChallengeStore` / `DbSmsCodeStore`（ORM 实体 `nop_auth_mfa_challenge` / `nop_auth_sms_code`） | 默认，无外部依赖 |
| `local` | JVM 内 ConcurrentHashMap（重启丢失/多实例不共享） | `nop.auth.mfa.store-type=local` |
| `redis` | 复用 `nop-nosql` | `nop.auth.mfa.store-type=redis`（需引入 nop-nosql） |

> consume 用条件 `DELETE WHERE pk=? AND expire_at>?` + affected-row 判定保证一次性；incrFailCount 用 SQL 原子递增。Redis store 经 `ioc:condition` 条件注册，classpath 无 nosql 时不加载（类加载安全）。

### 用户自助 / 管理员 MFA API

`NopAuthUserBizModel`（`nop-auth-service/.../entity/NopAuthUserBizModel.java`）：

| 方法 | 行 | 语义 |
|------|----|------|
| `bindMfa(mfaType, proof?)` | `:133` | 发起绑定（pending + bindToken，TOTP 返回 provisioning URI，SMS 发码到手机）。受限会话内必须先经登记通道 proof（`proof` 参数携带 `verifyChannelProof` 返回的票 token，见"角色级强制策略"章节） |
| `confirmMfa(bindToken, code)` | `:243` | 确认绑定（校验码 + enabled + 生成恢复码） |
| `unbindMfa(code)` | `:293` | 解绑（验证当前因子 + 作废恢复码） |
| `generateRecoveryCodes()` | `:321` | 重置恢复码（作废旧码） |
| `getMfaStatus()` | `:337` | 查询状态（不返回 secret） |
| `resetUserMfa(userId)` | `:367` | 管理员重置（admin 权限） |

### 扫码登录 MFA 适配（nop-ai-gateway）

`ChannelLoginApiBizModel.loginByScan`（`:144`）捕获 `ERR_AUTH_MFA_REQUIRED`（`:216`，按 error-code 字符串匹配，因 nop-ai-gateway 不依赖 nop-auth-service）→ 返回 `ScanLoginResult{mfaRequired=true}`（`ScanLoginResult.mfaRequired`，`:32`）携带 challenge 参数。非 MFA 异常原样上抛（不吞）。时序：手机扫码 → 输码 → `mfaVerify` → 返回 accessCode → PC 轮询 `getLoginResultAsync`（`:64`）。

### MFA / SMS 配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.auth.mfa.enabled` | `false` | 是否启用 MFA 拦截 |
| `nop.auth.mfa.store-type` | `db` | 存储后端（db / local / redis） |
| `nop.auth.mfa.challenge-expire-seconds` | — | challenge 有效期 |
| `nop.auth.mfa.max-attempts` | — | 单次 challenge 最大失败次数 |
| `nop.auth.mfa.totp-issuer` | — | TOTP provisioning URI 的 issuer |
| `nop.auth.mfa.bind-expire-seconds` | `300` | bindToken 有效期 |
| `nop.auth.operation-mfa.enabled` | `false` | 操作级 MFA 总开关（关闭时拦截器零介入） |
| `nop.auth.operation-mfa.op-ticket-expire-seconds` | `60` | 操作级票窗口（验证后允许重试原操作的时间） |
| `nop.auth.sms-code.enabled` | `false` | 是否启用短信验证码登录 |
| `nop.auth.sms-code.send-interval-seconds` | — | 发送间隔（限流） |
| `nop.auth.sms-code.daily-limit` | — | 单手机日发送上限 |
| `nop.auth.sms-code.max-attempts` | — | 单码最大验证次数 |

MFA/SMS 错误码定义在 `NopAuthErrors.java`：`ERR_AUTH_MFA_REQUIRED`（`nop.err.auth.mfa-required`，登录期）、`ERR_AUTH_OPERATION_MFA_REQUIRED`（`nop.err.auth.operation-mfa-required`，会话期——errorParams 携带 challengeToken/mfaType/operation）、`ERR_AUTH_MFA_FAIL`、`ERR_AUTH_MFA_CHALLENGE_EXPIRED`、`ERR_AUTH_SMS_CODE_INVALID`、`ERR_AUTH_SMS_RATE_LIMITED` 等。

### 操作级 MFA（会话内敏感操作二次验证）

长效会话内的高危操作（改密、解绑因子、重置恢复码等）可要求重新验证第二因子。机制：方法级 `@MfaRequired` 注解声明敏感操作 + GraphQL executor 检查点拦截 + 一次性短 TTL 票两段式重试。

**声明与约束**（注解在 `nop-biz-auth-api`，`io.nop.auth.api.mfa.MfaRequired`；存在即敏感，无属性）：

```java
@BizMutation
@MfaRequired   // 不得与 @BizSubscription 或 @Auth(publicAccess=true) 同用——构建期报错（fail-fast）
public void resetUserPassword(@Name("userId") String userId, @Name("password") String password, IServiceContext context) { ... }
```

- 构建期约束（`ReflectionBizModelBuilder`）：`@MfaRequired` + `@BizSubscription` → 构建报错（订阅路径无请求-响应语义）；`@MfaRequired` + `@Auth(publicAccess=true)` → 构建报错（匿名方法无会话可验）。静默绕过 = fail-open，故 fail-fast。
- 元数据传播链：`ReflectionBizModelBuilder` → `GraphQLFieldDefinition.mfaRequiredMeta` → `deepClone()` / `GraphQLObjectDefinition.mergeField`（两分支）/ `BizObjectBuildHelper.mergeBizModel`（nop-biz）四触点拷贝（对齐 makerCheckerMeta 先例）。
- 拦截：`GraphQLExecutor` 两检查点（RPC 单操作 + GraphQL 文档路径，auth check 之后）对带 `mfaRequiredMeta` 的顶层 operation 调用 `IOperationMfaChecker`（`nop-biz-auth-api` SPI）；`GraphQLEngine` 可选注入（`@Inject @Nullable`）——未部署 nop-auth-service 时零介入。订阅路径不接（构建期拒绝保证）。

**两段式流程**（`OperationMfaCheckerImpl` 判定链，`nop.auth.operation-mfa.enabled` 缺省 false）：

1. 敏感操作触发：enabled → 登录用户且 MFA setting status==enabled → 无有效票 → 创建 scene=operation 的 challenge（payload={operation, sessionId}）→ 抛 `ERR_AUTH_OPERATION_MFA_REQUIRED`（errorParams：challengeToken/mfaType/operation）。
2. 验证：客户端调 `LoginApi__mfaVerifyOperation`（**需登录态且同会话**；请求 `MfaVerifyOperationRequest{challengeToken, code}`，**无 recoveryCode 通道**）→ setting 复核 → `MfaFactorVerifier` 因子校验（错码计数超限作废）→ `markVerified` 一次性转票。**成功不签发任何凭证**（无 accessToken/无 completeLogin——与登录级 `mfaVerify` 的本质区别）。
3. 重试：原操作携带请求头 `X-Nop-Op-Mfa-Token: {challengeToken}` 重发 → 票核验（scene/operation/sessionId/票窗口四条件 + 原子 consume 恰一放行）。**票一次性、绑定 operation+sessionId、短 TTL（60s 缺省）**。

判定要点：未启用 MFA 的用户不拦截（无第二因子可验；强制启用归角色级策略 W13）；store 未装配放行；批量请求含敏感操作时整批预执行中止（错误即该 operation 的错误，无部分执行副作用）。

**首批标注**（nop-auth 模块内五动作）：`NopAuthUser__resetUserMfa` / `NopAuthUser__resetUserPassword` / `NopAuthUser__changeSelfPassword` / `NopAuthUser__unbindMfa` / `NopAuthUser__generateRecoveryCodes`。凭证库模块四动作已落地（C1b，A1-audit §二#4 缩窄裁定）：`NopCredential__reencryptAll` / `NopCredential__delete` / `NopCredentialAuth__grant` / `NopCredentialAuth__revoke`（清单、生效前置与缩窄理由见 `nop-credential.md` 的"敏感操作标注"章节）；通用 CRUD 路径（如联系方式修改经 `NopAuthUser__save`）无法用方法级注解覆盖，属平台级治理。

**共享因子校验组件 `MfaFactorVerifier`**（`io.nop.auth.service.mfa`）：登录级/绑定级/操作级三处因子校验收敛；**TOTP 防重放窗口统一推进内聚组件内**（任何场景成功都更新 lastVerifiedWindow——防同一 30s 窗口码跨场景重放）；未知 mfaType fail-closed；恢复码分支不入组件（登录级专用）。新增因子（W14/W15）只改组件与白名单。

**审计**：四事件（challenge 发起/验证成功/验证失败/票消费）经 `IAuditService.saveAudit` 落 `NopAuthOpLog`（记录 operation 与 sessionId；`@BizAudit` 为装饰性注解，不作落点）。

**store 场景化**：`MfaChallengeStore` 提供 `create(scene, ..., payload)` 场景重载与 `markVerified(token)`（一次性迁移，Local=JVM compute / DB=条件 UPDATE+affected-row / Redis=派生票键 SETNX 三实现原子性）；登录级调用点（老五参 create）零改动。Redis 滚动升级注意：老进程读新 JSON（含 scene/payload/verifiedAt 增量键）需 `nop.core.json.parse-ignore-unknown-prop=true`（平台缺省 false）或预留 5 分钟 challenge 排空窗口（TTL 300s）。

### 角色级强制策略与受限会话（W13）

管理员可按角色强制 MFA：策略 = 用户因子**持有约束**（不改变验证所用因子）。无策略行时三层判定退化为一期行为（零回归基线）。

**策略模型与管理 API**：`NopAuthRoleMfaPolicy`（roleId PK 1:1 按需建行，`minMfaLevel` 因子强度下限 1/2/3，`allowTrustedDevice` 缺省 true（W15 消费），delFlag 软删除）。管理入口 `NopAuthRoleBizModel`：

| 方法 | 语义 |
|------|------|
| `NopAuthRole__saveMfaPolicy(roleId, minMfaLevel, allowTrustedDevice?)` | 建行/覆盖（admin 运行时校验 + 幂等 + 审计） |
| `NopAuthRole__removeMfaPolicy(roleId)` | 删行即撤策略（无 status 双态；幂等 + 审计） |

**因子强度表**（`RoleMfaPolicyEvaluator.factorLevel`，W14/W15 新常量仅核对入表）：sms/email=1（OTP 拥有通道）、totp=2、webauthn=3；未知 mfaType fail-closed 视为 0。多角色合并 = `max(minMfaLevel)`（最严格胜）+ `allowTrustedDevice` AND（任一 false 即禁）。角色快照口径 = `buildUserContext`（直接角色 + childRoleIds 继承展开 + 隐式 user 角色及其继承链——策略挂 `user` 角色 = 全员强制）。策略在**登录时评估**（角色/策略变更下次登录生效，会话中期不回溯）。

**三层判定矩阵**（`LoginServiceImpl.checkMfaRequired` 第三态，策略评估插入 store 装配检查之后、setting 装载之前）：

| 全局开关 | 角色策略（合并 maxLevel） | 用户 setting | 行为 |
|---|---|---|---|
| off | 任意 | 任意 | 一期行为：直接放行 |
| on | 无策略 | 任意 | 一期行为：现行判定不变 |
| on | 有策略（L） | enabled 且 level(因子) ≥ L | 一期行为：正常 challenge 两阶段 |
| on | 有策略（L） | enabled 且 level(因子) < L | **直接受限**（不建 challenge）：受限会话 |
| on | 有策略（L） | 未启用 | **直接受限**：受限会话 |

**受限会话（restricted session）**：`completeLogin` 受限变体签发——`mfaRestricted` 标志在会话持久化**之前**写入（`UserContextImpl.serializeToJson` 与 `DaoUserContextCache` 序列化白名单两触点同步，仅受限会话写入该键）；`LoginResult.mfaRestricted` / `ScanLoginResult.mfaRestricted` 可选字段回填（正常登录缺省不出现）。`IUserContext.isMfaRestricted()` 为 Java default 方法（缺省 false，外部实现类零破坏）。

**受限会话拦截**（executor + checker 双触点，与操作级 MFA 同一拦截点分层正交）：

- `GraphQLExecutor.checkOperationMfa` 受限分支（前置于 `mfaRequiredMeta` 早退，对**所有** operation 生效）：query 放行 + publicAccess mutation 放行（token 刷新等会话基建，防中途 token 过期死锁）+ 其余 mutation 路由进 checker。
- `OperationMfaCheckerImpl` 受限分支（前置于 `operation-mfa.enabled` 门——**不受操作级开关门控**）：白名单 mutation 放行且**短路返回**（不再叠加 @MfaRequired 操作级检查），其余抛 `ERR_AUTH_MFA_RESTRICTED_SESSION`（errorParams 携带 operation 全名）。
- 白名单终版（operation 全名 `bizObjName__action` 口径；注意 schema 注册名剥除方法名 `Async` 尾缀）：`NopAuthUser__bindMfa` / `NopAuthUser__confirmMfa` / `NopAuthUser__unbindMfa` / `NopAuthUser__getMfaStatus` / `LoginApi__verifyChannelProof` / `LoginApi__logout` / `LoginApi__refreshToken`。
- 无 checker bean（未部署 nop-auth-service）时 executor 零介入不变。

**登记通道 proof（防 enrollment attack）**：受限会话内 `bindMfa` 前置门槛——仅持有密码的攻击者不得绑定自己的验证器接管账户（门槛提升到"密码 + 登记通道"）。

1. `bindMfa`（受限会话）：服务端解析用户登记 phone（W13 仅 phone，不接受客户端指定；为空抛 `ERR_AUTH_MFA_NO_RECOVERY_CHANNEL`）→ 无有效票时 `SmsCodeStore.send("proof:{userId}")` 发码（复用 sms-code 限流配置：60s 间隔/日上限）→ 抛 `ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED`（channel 脱敏提示，尾 4 位）。
2. `LoginApi__verifyChannelProof(code)`（登录态）：校验 proof 码 → 创建 scene=channel-proof 已验证票（`markVerified` 转票，票窗口 = `op-ticket-expire-seconds` 语义）→ 返回票 token。
3. `bindMfa(mfaType, proof: 票token)`：票核验（scene + verifiedAt + userId 绑定）+ 原子 `consume` 一次性消费 → 绑定流程放行。正常会话 `bindMfa` 零改动（无 proof 参数即原行为）。

**confirmMfa 策略校验（防因子降级）**：确认因子强度 < 角色策略 minMfaLevel → `ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK`（errorParams mfaType/mfaLevel）。解绑不受限（`unbindMfa` 本身要求验证当前因子——攻击者无因子不可解绑）；受限会话内 confirmMfa 成功**不原位升级会话**（引导登出后重新登录走完整两阶段）。

**升级引导流（状态机）**：受限登录 → （弱因子用户：unbindMfa 验当前因子解绑）→ 登记通道 proof → bind 强因子 → confirmMfa → 登出 → 重新登录（完整两阶段）→ 完整会话。

**OAuth 入口行为变更（一期遗留 gap 修复，migration note）**：`OAuthLoginServiceImpl.loginAsync`（nop-auth-sso）经 `IMfaLoginPolicyService` SPI（nop-biz-auth-core 接口 / nop-auth-service 实现 bean `nopMfaLoginPolicyService`，`@Inject @Nullable` 可选注入）接入与密码路径同语义判定——从"永不拦截"变为"与密码路径同语义"（null 放行/challenge `ERR_AUTH_MFA_REQUIRED`/受限签发三分支）。策略评估用**本地角色快照**（realm roles 不参与）；无本地用户映射 = 无策略 = 维持一期行为。未部署 nop-auth-service 时零介入。

**审计事件**（`IAuditService.saveAudit` 落 `NopAuthOpLog`）：`mfa-restricted-login`（受限签发）/ `mfa-restricted-rejected`（受限拦截拒绝）/ `mfa:channel-proof-sent`（proof 发码，phone 脱敏）/ `mfa:channel-proof-verified|fail`（proof 验证）/ 策略变更三事件（saveMfaPolicy/removeMfaPolicy）。注意 `NopAuthOpLog.userName` 为非空列——审计请求必须设置 userName，否则批处理整批回滚（W13 E2E 钉定）。

**与操作级 MFA 的分层**：角色策略是登录期持有约束（评估点唯一 = `checkMfaRequired`）；操作级是会话期敏感操作保护。受限分支前置于操作级判定（含 enabled 门）且短路白名单动作的操作级检查；组合仅经 `allowTrustedDevice` 单点（W15）。

**错误码**（`NopAuthErrors`，不触碰一期编码）：`ERR_AUTH_MFA_RESTRICTED_SESSION`（`nop.err.auth.mfa-restricted-session`，ARG_OPERATION）、`ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED`（`nop.err.auth.mfa-channel-proof-required`，ARG_CHANNEL 脱敏）、`ERR_AUTH_MFA_NO_RECOVERY_CHANNEL`（`nop.err.auth.mfa-no-recovery-channel`）、`ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK`（`nop.err.auth.mfa-policy-factor-too-weak`，ARG_MFA_TYPE/ARG_MFA_LEVEL）。

## 相关文档

- `../02-core-guides/auth-and-permissions.md`
- `../reusable-modules-overview.md`
