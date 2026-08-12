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
| NopAuthMfaChallenge | `nop_auth_mfa_challenge` | MFA 挑战令牌（challengeToken PK / userId / mfaType / expireAt / failCount，W8 DB 存储） |
| NopAuthSmsCode | `nop_auth_sms_code` | 短信验证码（codeKey PK / phone / code / expireAt / failCount，W8 DB 存储） |
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
2. **MFA 拦截**：`checkMfaRequired()`（`:743`）——`nop.auth.mfa.enabled` 开关 + 用户 MFA 设置检查（status==enabled）。SSO/信道登录同样拦截（`createSessionForUserAsync`，`:337`，loginType 参数化保证审计不失真）。
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
| `bindMfa(mfaType)` | `:133` | 发起绑定（pending + bindToken，TOTP 返回 provisioning URI，SMS 发码到手机） |
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
| `nop.auth.sms-code.enabled` | `false` | 是否启用短信验证码登录 |
| `nop.auth.sms-code.send-interval-seconds` | — | 发送间隔（限流） |
| `nop.auth.sms-code.daily-limit` | — | 单手机日发送上限 |
| `nop.auth.sms-code.max-attempts` | — | 单码最大验证次数 |

MFA/SMS 错误码定义在 `NopAuthErrors.java`：`ERR_AUTH_MFA_REQUIRED`（`nop.err.auth.mfa-required`）、`ERR_AUTH_MFA_FAIL`、`ERR_AUTH_MFA_CHALLENGE_EXPIRED`、`ERR_AUTH_SMS_CODE_INVALID`、`ERR_AUTH_SMS_RATE_LIMITED` 等。

## 相关文档

- `../02-core-guides/auth-and-permissions.md`
- `../reusable-modules-overview.md`
