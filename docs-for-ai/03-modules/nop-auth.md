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
- 多因子验证（登录级两阶段 + 操作级敏感操作二次验证 + 角色级强制策略与受限会话 + 邮件验证码因子 + 可信设备豁免）

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
| NopAuthEmailCode | `nop_auth_email_code` | 邮件验证码（codeKey PK / email 信息性可空 / code / expireAt / failCount——结构对齐 nop_auth_sms_code，W15） |
| NopAuthRoleMfaPolicy | `nop_auth_role_mfa_policy` | 角色级 MFA 强制策略（roleId PK 1:1 / minMfaLevel 1-3 / allowTrustedDevice / delFlag 软删除——无行 = 无策略，W13） |
| NopAuthMfaCredential | `nop_auth_mfa_credential` | WebAuthn 凭证（sid seq PK / userId 索引 / credentialId 全局唯一 / publicKey COSE masked / signCount 单调递增 / transports / name / status enabled\|disabled / lastUsedAt，1:N 用户多硬件钥匙——setting 承担用户级启用状态、credential 行承担密钥材料，W14）。**因子失效边界全量物理删除**（unbindMfa/resetUserMfa/恢复码使用，A2-audit D3-F1——残留行会在重绑后复活旧钥匙；多钥匙累积的正规入口为后续 add-key 端点 successor） |
| NopAuthMfaTrustedDevice | `nop_auth_mfa_trusted_device` | MFA 可信设备（sid seq PK / (userId, deviceHash) 复合唯一 / deviceName / expireAt 固定窗口 / lastUsedAt——物理删除，到期惰性失效行保留审计，W15） |
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
| 可信设备共享组件 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaTrustedDeviceManager.java`（指纹计算 + 豁免判定 + 登记 + 撤销矩阵统一落点） |
| EmailCodeStore 接口 | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/mfa/store/EmailCodeStore.java`（SmsCodeStore 同形平行接口；Local 在 core，Db/Redis 在 nop-auth-service） |
| WebAuthn 验证器组件 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/WebAuthnAuthenticator.java`（封装 Yubico webauthn-server-core，库类型不外溢） |
| MFA challenge payload 契约辅助 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaChallengeHelper.java`（登录级 challenge 创建三触点收敛：checkMfaRequired / MfaLoginPolicyServiceImpl 副本共用 `createLoginChallenge`） |
| WebAuthn options 读取端点 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java`（`webauthnAuthOptions`） |
| 操作级验证端点 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java`（`mfaVerifyOperation`） |
| ORM 模型 | `nop-auth/model/nop-auth.orm.xml` |

## 多因子验证（MFA）

nop-auth 提供完整的两阶段登录（第一因子 → challenge → 第二因子）与短信验证码登录，因子类型：TOTP（RFC 6238）/ SMS 验证码 / WebAuthn-FIDO2 硬件钥匙（W14，见"WebAuthn/FIDO2 硬件因子"章节）/ 邮件验证码（W15，见"邮件验证码因子"章节）。

### 登录类型（loginType）

| 值 | 含义 |
|----|------|
| 1 | 用户名 + 密码 |
| 2 | 邮箱 + 密码 |
| 3 | 手机号 + 密码 |
| 4 | 单点登录（SSO） |
| 5 | 手机号 + 短信验证码（W5 新增） |
| 20-23 | 信道登录（飞书 / 钉钉 / 企微 / Webhook） |

字典定义：`nop-biz-auth-core/.../_vfs/dict/auth/login-type.dict.yaml`；MFA 因子类型显示字典 `mfa-type.dict.yaml`（totp/sms/webauthn/email——校验源在代码常量，字典仅作显示）。

### 两阶段登录流程

1. **第一因子校验**：`LoginServiceImpl.loginAsync()`（`:302`）校验用户凭证（密码/SSO/信道）。
2. **MFA 拦截**：`checkMfaRequired()`（`:1012`，W15 增 headers 参数）——`nop.auth.mfa.enabled` 开关 + 角色策略评估（W13 第三态：有策略且不达标 → 受限会话签发，不建 challenge）+ 用户 MFA 设置检查（status==enabled）。SSO/信道登录同样拦截（`createSessionForUserAsync`，`:404`，loginType 参数化保证审计不失真）。
3. **创建 challenge**：`MfaChallengeStore.create()` 生成一次性 challengeToken。
4. **抛 `ERR_AUTH_MFA_REQUIRED`**（`:386`）：errorParams 携带 challengeToken / mfaType / loginType。未启用 MFA 的用户零感知（直接进 completeLogin）。
5. **第二因子验证**：客户端调 `LoginApi.mfaVerify`（`LoginApiBizModel.mfaVerifyAsync`，`:197`）→ `LoginServiceImpl.mfaVerifyAsync`（`:548`）：peek challenge → setting 复核 → TOTP / SMS / WebAuthn 断言（`MfaVerifyRequest` 可选 `assertion` 字段）/ 恢复码分支 → 成功后 `consume` challenge 并 `completeMfaLogin` 签发 token。

> 因子等同分支：PHONE_SMS 登录 + mfaType==SMS 不重复验证。恢复码分支：BCrypt 比对，成功后 consume + status=disabled 强制重绑 + **物理删除 webauthn credential 行**（因子失效边界，A2-audit D3-F1）。

### 短信验证码登录（loginType=5）

- `LoginApi.sendSmsCode`（`LoginApiBizModel.sendSmsCode`，`:173`）：向手机号发码（publicAccess）。
- 限流：60s 间隔 / 日上限 / IP 上限 / 防枚举统一响应。
- `mfaVerify` 复用同一验证路径。

### TOTP 验证器

`TOTPAuthenticator`（`nop-biz-auth-core/.../totp/TOTPAuthenticator.java`）：RFC 6238——HMAC-SHA1 / 6 位 / 30s 周期 / ±1 窗口偏差。**防重放**：通过时更新 `lastVerifiedWindow`，拒绝时不更新。secret 经 `AESTextCipher` 加密存储。

### MFA 存储

| store-type | 实现 | 激活条件 |
|------------|------|---------|
| `db`（默认） | `DbMfaChallengeStore` / `DbSmsCodeStore` / `DbEmailCodeStore`（ORM 实体 `nop_auth_mfa_challenge` / `nop_auth_sms_code` / `nop_auth_email_code`） | 默认，无外部依赖 |
| `local` | JVM 内 ConcurrentHashMap（重启丢失/多实例不共享） | `nop.auth.mfa.store-type=local` |
| `redis` | 复用 `nop-nosql` | `nop.auth.mfa.store-type=redis`（需引入 nop-nosql） |

> consume 用条件 `DELETE WHERE pk=? AND expire_at>?` + affected-row 判定保证一次性；incrFailCount 用 SQL 原子递增。Redis store 经 `ioc:condition` 条件注册，classpath 无 nosql 时不加载（类加载安全）。W15 起 `EmailCodeStore` 与前两类同模式装配（collect-beans 前缀 `nopEmailCodeStore_` + 工厂 bean `nopActiveEmailCodeStore` + `MfaStoreProvider` 第三组 map——三类 store 共享同一 store-type 选择）。

### 用户自助 / 管理员 MFA API

`NopAuthUserBizModel`（`nop-auth-service/.../entity/NopAuthUserBizModel.java`）：

| 方法 | 行 | 语义 |
|------|----|------|
| `bindMfa(mfaType, proof?, channel?)` | `:237` | 发起绑定（pending；TOTP 返回 provisioning URI，SMS 发码到手机，webauthn 返回 challengeToken + creationOptions——见"WebAuthn/FIDO2"章节；email 发码到登记邮箱——见"邮件验证码因子"章节）。受限会话内必须先经登记通道 proof（`proof` 参数携带 `verifyChannelProof` 返回的票 token；`channel` 参数选择 proof 通道 phone\|email，见"角色级强制策略"章节） |
| `confirmMfa(bindToken, code)` | `:605` | 确认绑定（校验码 + enabled + 生成恢复码；totp/sms） |
| `confirmWebauthnRegistration(challengeToken, attestation)` | `:450` | 确认 WebAuthn 注册（attestation 验证 + credential 落库 + enabled + 恢复码 + consume） |
| `unbindMfa(code?, challengeToken?, assertion?)` | `:677` | 解绑（webauthn 用户凭 challengeToken+assertion 断言验证；其余类型凭 code；验证当前因子 + 作废恢复码 + **物理删除全部 webauthn credential 行**——A2-audit D3-F1 因子作废语义） |
| `webauthnBeginVerify()` | `:536` | 发起 WebAuthn 解绑验证（scene=webauthn-unbind challenge + requestOptions） |
| `listWebauthnCredentials()` | `:797` | 列出本人 WebAuthn credentials（不暴露 credentialId/publicKey） |
| `removeWebauthnCredential(sid)` | `:824` | 移除一把 credential（最后一把 enabled 拒绝 `ERR_AUTH_MFA_LAST_CREDENTIAL`；越权归一"不存在"；**物理删除**释放 credentialId 唯一键——A2-audit） |
| `renameWebauthnCredential(sid, name)` | `:842` | 重命名一把 credential（本人数据限定） |
| `generateRecoveryCodes()` | — | 重置恢复码（作废旧码） |
| `getMfaStatus()` | — | 查询状态（不返回 secret） |
| `listTrustedDevices()` | `:872` | 列出本人可信设备（全部行含过期标记，W15） |
| `removeTrustedDevice(sid)` | `:897` | 移除一把可信设备（物理删除；越权归一"不存在"，W15） |
| `resetUserMfa(userId)` | — | 管理员重置（admin 权限；同时全量撤销该用户可信设备） |

### WebAuthn/FIDO2 硬件因子（W14）

mfaType 第三取值 `webauthn`（多 credential 模型——用户级仍是单值 mfaType，密钥材料在 `NopAuthMfaCredential` 行 1:N）。协议库为 Yubico `webauthn-server-core` 2.7.0（Apache-2.0，**依赖只在 nop-auth-service**，经 `WebAuthnAuthenticator` 封装、库类型不外溢到方法签名）；attestation 策略 `none`（直接信任 + origin/rpId 强校验——完整信任链 deferred）。RP 配置缺省未配，webauthn 绑定/验证显式报错 fail-closed。

**三个 ceremony**（挑战复用 `MfaChallengeStore` 场景化扩展——payload 携带 32B 随机 cryptoChallenge，**create 时一次写入、只读复用**，无后置更新原语）：

1. **注册（绑定）**：`bindMfa("webauthn", proof?)` → pending setting（secret=null）+ scene=webauthn-register challenge（payload={sessionId, cryptoChallenge}）→ 返回 challengeToken + creationOptions（excludeCredentials=既有 credential 防重复注册）→ 客户端 `navigator.credentials.create()` → `confirmWebauthnRegistration(challengeToken, attestation)`（需登录态 + 同会话；验证失败 incrFailCount（超限作废）+ MFA_FAIL 不消费；credentialId 全局唯一冲突=重复注册拒绝；成功 credential 落库 + setting enabled + 恢复码生成 + consume）。
2. **认证（登录第二因子）**：登录 challenge 创建处（webauthn 类型）payload 含 cryptoChallenge——三触点同步（`LoginServiceImpl.checkMfaRequired` / `OperationMfaCheckerImpl` / `MfaLoginPolicyServiceImpl.checkMfaForUserName` OAuth 副本，前两者经 `MfaChallengeHelper` 收敛）→ 客户端 `LoginApi__webauthnAuthOptions(challengeToken)` 取 requestOptions（scene=login 公开访问；非 login 需登录态 + payload.sessionId==当前会话；challenge 只读复用）→ `mfaVerify`（`MfaVerifyRequest` 可选 `assertion` 字段）→ `MfaFactorVerifier` 统一 webauthn 分支 → consume → completeLogin（一期出口不变）。
3. **解绑**：`webauthnBeginVerify()`（需登录态；scene=webauthn-unbind challenge + requestOptions）→ `unbindMfa(null, challengeToken, assertion)`（同会话校验 + 断言验证等价保持"验证当前因子"语义；失败计数 + MFA_FAIL；成功 consume + status=disabled + 删除恢复码 + **物理删除全部 credential 行**（A2-audit D3-F1——被窃钥匙不随重绑复活，credentialId 唯一键同步释放允许同钥匙复注册））。`operation-mfa.enabled=true` 时解绑为**双 ceremony**（操作级票一次断言 + unbind challenge 一次断言，与 totp 用户"输两次码"同构，非缺陷）。

**断言验证语义**（`MfaFactorVerifier.verify(setting, mfaType, code, assertion, challenge)` 五参统一载体——登录级/操作级/解绑级共用）：cryptoChallenge 取自服务端 challenge payload（防客户端自造挑战）；按 assertion.credentialId 查本人 enabled credential；COSE 公钥验签 + challenge/origin/rpId 校验（库 Step6 强校验 userHandle==服务端 userId 句柄）；**signCount 单调递增写内聚组件**——条件 `UPDATE ... WHERE SIGN_COUNT < ?`，并发竞态方 affected=0 按验证失败处理（不覆盖更大计数）；count=0 认证器（协议允许的无计数实现）跳过单调校验、仅记审计。userHandle 以服务端 userId 字节为权威值，assertion 携带句柄时被强校验一致（句柄漂移防护）。

**防重放**：challenge 一次性（consume）+ options 多次读取幂等（只读复用同一 cryptoChallenge）+ signCount 单调递增（克隆检测，跨 challenge 持久）。

**操作级联动**：`MfaVerifyOperationRequest` 可选 `assertion` 字段；拦截器创建的 scene=operation challenge payload 含 cryptoChallenge → 客户端 `webauthnAuthOptions` 取 options → `mfaVerifyOperation(challengeToken, assertion)` → `MfaFactorVerifier` → markVerified 转票（操作级链路零结构变更）。

**受限会话联动**：`confirmWebauthnRegistration` 在受限会话白名单内（minMfaLevel=3 用户的升级路径 = 受限会话内 proof → bindMfa(webauthn) → confirm）；`webauthnBeginVerify` 与 credential 管理三 API **不在**白名单（受限用户 setting.mfaType 不可能为 webauthn——webauthn=3 已达 factorLevel 表上限，入白名单为不可达死代码）。webauthn 绑定同样继承登记通道 proof 前置（W13 bindMfa 前置在类型分派之前）。

**审计**：注册成功/失败、断言成功/失败（含 count=0 标记）、解绑、credential 移除经 `IAuditService.saveAudit` 落 NopAuthOpLog（userName 非空列必须设置）。

**其他因子不受影响**：`sendMfaCode` 按 challenge.mfaType 分派——sms 现行为原样；email 走 EmailCodeStore（见"邮件验证码因子"章节）；totp/webauthn 显式抛 `ERR_AUTH_MFA_CODE_UNSUPPORTED`。未绑定 webauthn 的用户全流程无感知（一期零回归）。

### 邮件验证码因子（W15）

mfaType 第四取值 `email`（OTP 拥有通道类，factorLevel=1）。平行 `EmailCodeStore`（`nop-biz-auth-core` 接口，`SmsCodeStore` 同形三方法：`send(key)` 返回明文码 / `verify(key, code)` 三态原子消费 / `consume(key)`）+ 三实现（Local 在 core；Db 表 `nop_auth_email_code` / Redis 在 nop-auth-service，W8 装配模式复刻）。**不泛化改名** SmsCodeStore（平行接口，设计裁定）。

- **key 通道隔离**：MFA 码 `mfa-email:{userId}`、登记通道 proof 码 `proof-email:{userId}`——与 sms 侧（`mfa:` / `proof:`）互不通用。
- **绑定/确认**：`bindMfa("email")` → 发码到**服务端解析**的 `NopAuthUser.email`（不接受客户端指定邮箱，防枚举/骚扰——sms bindMfa 先例；响应 `emailSent=true`）→ `confirmMfa(bindToken, code)`（经 `MfaFactorVerifier` email 分支，EXPIRED 抛 `ERR_AUTH_EMAIL_CODE_EXPIRED`）。
- **登录**：challenge mfaType=email → `mfaVerify` email 分支（经 verifier）；`sendMfaCode` 按 mfaType 分派 email → 解析 user.email → 限流 → 发码 + 邮件发送（无 email 抛 CHALLENGE_EXPIRED 等价先例错误）。
- **发送链路**：`IEmailSender`（`nop-integration-api` 既有实现复用，零变更）`@Nullable` 注入——未装配时 email 因子绑定/发码 fail-closed 显式报错；文案配置化 `nop.auth.email-code.subject-template` / `text-template`（`{code}` 占位服务端替换）。
- **门控与限流**（`nop.auth.email-code.*`，enabled 缺省 **false**——三个发码入口统一前置显式拒绝：bindMfa(email) / sendMfaCode email 分支 / 登记通道 email proof）：60s 间隔 / email 日上限 / IP 日上限（email+IP 双维度内存计数，`checkSmsRateLimit` 同模式）。
- **无独立公开发码端点**（无邮箱登录场景）。
- **登记通道 email 解锁**：受限会话 proof 通道解析扩展为"phone 优先、phone 缺失回退 email；双通道均登记时可选 `channel` 参数（bindMfa/verifyChannelProof，取值 phone|email，服务端限定已登记集合，任意指定显式拒绝）"——`ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED` 提示按通道脱敏（邮箱：本地部分前 2 位 + `***` + @域名）。

### 可信设备（记住此设备，W15）

已通过完整 MFA 的设备可在固定窗口（缺省 30 天）内豁免登录级第二因子 challenge；操作级 MFA **永不豁免**（可信设备 ≠ 当前操作者仍是本人）；恢复码登录不登记（应急通道不产生长期豁免）。

**指纹算法**（`MfaTrustedDeviceManager.fingerprint`，威胁模型边界见下）：请求头 `X-Nop-Mfa-Device-Id`（前端生成持久化的 UUID，非秘密，大小写不敏感读头）+ `User-Agent` + `Accept-Language` 三输入 `|` 连接 SHA-256 hex。device-id 缺失 → 不豁免（降级正常 MFA，非错误）。**拒绝** canvas/硬件/行为指纹（隐私合规）与 IP（移动网络 IP 飘移误伤）。

**豁免判定**（`checkMfaRequired` 增 headers 参数——`loginAsync` 传真实值、信道路径 `createSessionForUserAsync` 传 null 结构性跳过；插入位 = 因子等同之后、challenge 创建之前）：进入条件 = headers 非空 ∧ 密码类 loginType（1/2/3/5）∧ `policy.allowTrustedDevice`（W13 复合结果 AND 合并——任一策略行 false 即跳过，行保留待放宽恢复）∧ 指纹非空 → 查 (userId, deviceHash) 未过期行 → 命中更新 lastUsedAt（**不续 expireAt**——固定窗口保证周期性完整 MFA 重新验证）→ 放行（与一期"放行"同路径）。**OAuth 副本裁定**：`MfaLoginPolicyServiceImpl.checkMfaForUserName` 不加豁免分支（信道路径无 headers 结构性不可达——有未过期可信设备行的用户经 OAuth 入口登录仍创建 challenge，专项回归断言钉定）。

**登记**（`mfaVerify` 请求可选 `rememberDevice=true`；仅密码类 challenge.loginType 生效）：headers 穿线 `verifySecondFactorAndComplete`——登记在因子验证成功路径（completeLogin 之前纯 DB 写，**不在 completeMfaLogin**——该方法被恢复码分支共用，结构性排除恢复码登记）。同 hash（含过期行）upsert 覆盖刷新（expireAt=now+ttl-days，不受 max-count 限制，sid 不变复活）；新行需未过期行数 < `max-count`（缺省 5，仅计未过期行）否则不登记。结果经 IUserContext attr 回填 `LoginResult.trustedDeviceRegistered`（仅密码类路径，显式 true/false——满员/无 device-id 亦为 false 提示非静默；未请求登记时字段缺省不出现）。登记失败不阻断登录（log+audit 不吞异常）；并发同 hash 撞唯一约束归一为 update。

**撤销矩阵**：

| 触发 | 动作 |
|------|------|
| `expireAt` 自然到期 | 惰性失效（判定时不豁免；行保留供审计，清理为运维优化） |
| `removeTrustedDevice(sid)`（用户自助，物理删除） | 删除行（本人数据限定，越权归一"不存在"） |
| `unbindMfa` 成功 / `confirmMfa` 成功（换绑判定点） | 全量删除该用户行（因子变更 = 信任前提失效） |
| `resetUserMfa`（管理员重置） | 全量删除该用户行 |
| 策略 `allowTrustedDevice=false` | 不删行，判定跳过（策略放宽后恢复生效） |

**管理 API**（`NopAuthUserBizModel`）：`listTrustedDevices()`（全部行含 expired 标记——`TrustedDeviceInfo` DTO 不含 deviceHash）/ `removeTrustedDevice(sid)`。

**威胁模型（安全边界）**：豁免防"异地攻击者使用盗取的密码"（无受害者设备指纹即退回完整 MFA）；**不防**本机恶意软件/同设备攻击者（UA/Accept-Language 可伪造、device-id 可被同机读取）。高敏角色应以策略 `allowTrustedDevice=false` 关闭豁免。


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
| `nop.auth.mfa.webauthn.rp-id` | —（未配） | WebAuthn Relying Party ID（如 `example.com`）。webauthn 因子使用时必配（缺配显式报错 fail-closed） |
| `nop.auth.mfa.webauthn.rp-name` | —（未配） | WebAuthn RP 显示名（creationOptions.rp.name）。必配 |
| `nop.auth.mfa.webauthn.origins` | —（未配） | 允许的 origin 列表（逗号分隔，如 `https://a.com,https://b.com`）。验证时精确匹配，不匹配即拒绝（fail-closed 防钓鱼域）。必配 |
| `nop.auth.email-code.enabled` | `false` | 邮件验证码开关（bindMfa(email)/sendMfaCode email/登记通道 email proof 三个发码入口统一前置显式拒绝，W15） |
| `nop.auth.email-code.expire-seconds` | `300` | 邮件验证码有效期 |
| `nop.auth.email-code.send-interval-seconds` | `60` | 同邮箱发送最小间隔（限流） |
| `nop.auth.email-code.daily-limit` | `20` | 单邮箱日发送上限 |
| `nop.auth.email-code.ip-daily-limit` | `50` | 同 IP 日发送上限 |
| `nop.auth.email-code.max-attempts` | `5` | 单码最大验证次数 |
| `nop.auth.email-code.subject-template` | `Verification Code` | 邮件主题模板（`{code}` 占位服务端替换） |
| `nop.auth.email-code.text-template` | `Your verification code is {code}...` | 邮件正文模板（`{code}` 占位服务端替换） |
| `nop.auth.mfa.trusted-device.ttl-days` | `30` | 可信设备豁免固定窗口天数（命中不续期，W15） |
| `nop.auth.mfa.trusted-device.max-count` | `5` | 每用户可信设备上限（仅计未过期行；同 hash 覆盖刷新不受限；满员新增显式提示不阻断登录） |

MFA/SMS 错误码定义在 `NopAuthErrors.java`：`ERR_AUTH_MFA_REQUIRED`（`nop.err.auth.mfa-required`，登录期）、`ERR_AUTH_OPERATION_MFA_REQUIRED`（`nop.err.auth.operation-mfa-required`，会话期——errorParams 携带 challengeToken/mfaType/operation）、`ERR_AUTH_MFA_FAIL`、`ERR_AUTH_MFA_CHALLENGE_EXPIRED`、`ERR_AUTH_SMS_CODE_INVALID`、`ERR_AUTH_SMS_RATE_LIMITED`、`ERR_AUTH_MFA_CODE_UNSUPPORTED`（`nop.err.auth.mfa-code-unsupported`——sendMfaCode 对无验证码可发的因子类型 totp/webauthn 显式拒绝，W14）、`ERR_AUTH_MFA_LAST_CREDENTIAL`（`nop.err.auth.mfa-last-credential`——移除最后一把 enabled WebAuthn credential 拒绝，整体解绑走 unbindMfa ceremony，W14）、`ERR_AUTH_EMAIL_CODE_EXPIRED` / `ERR_AUTH_EMAIL_RATE_LIMITED` / `ERR_AUTH_EMAIL_DAILY_LIMIT`（`nop.err.auth.email-*`——email 码专属三码，不复用 sms 编码，W15）等。

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
2. 验证：客户端调 `LoginApi__mfaVerifyOperation`（**需登录态且同会话**；请求 `MfaVerifyOperationRequest{challengeToken, code?, assertion?}`——`code`（totp/sms）与 `assertion`（webauthn，W14）可选共存，**无 recoveryCode 通道**）→ setting 复核 → `MfaFactorVerifier` 因子校验（错码计数超限作废）→ `markVerified` 一次性转票。**成功不签发任何凭证**（无 accessToken/无 completeLogin——与登录级 `mfaVerify` 的本质区别）。
3. 重试：原操作携带请求头 `X-Nop-Op-Mfa-Token: {challengeToken}` 重发 → 票核验（scene/operation/sessionId/票窗口四条件 + 原子 consume 恰一放行）。**票一次性、绑定 operation+sessionId、短 TTL（60s 缺省）**。

判定要点：未启用 MFA 的用户不拦截（无第二因子可验；强制启用归角色级策略 W13）；store 未装配放行；批量请求含敏感操作时整批预执行中止（错误即该 operation 的错误，无部分执行副作用）。

**首批标注**（nop-auth 模块内五动作）：`NopAuthUser__resetUserMfa` / `NopAuthUser__resetUserPassword` / `NopAuthUser__changeSelfPassword` / `NopAuthUser__unbindMfa` / `NopAuthUser__generateRecoveryCodes`。凭证库模块四动作已落地（C1b，A1-audit §二#4 缩窄裁定）：`NopCredential__reencryptAll` / `NopCredential__delete` / `NopCredentialAuth__grant` / `NopCredentialAuth__revoke`（清单、生效前置与缩窄理由见 `nop-credential.md` 的"敏感操作标注"章节）；通用 CRUD 路径（如联系方式修改经 `NopAuthUser__save`）无法用方法级注解覆盖，属平台级治理。

**共享因子校验组件 `MfaFactorVerifier`**（`io.nop.auth.service.mfa`）：登录级/绑定级/操作级三处因子校验收敛；**TOTP 防重放窗口统一推进内聚组件内**（任何场景成功都更新 lastVerifiedWindow——防同一 30s 窗口码跨场景重放）；未知 mfaType fail-closed；恢复码分支不入组件（登录级专用）。新增因子（W14/W15）只改组件与白名单。

**审计**：四事件（challenge 发起/验证成功/验证失败/票消费）经 `IAuditService.saveAudit` 落 `NopAuthOpLog`（记录 operation 与 sessionId；`@BizAudit` 为装饰性注解，不作落点）。

**store 场景化**：`MfaChallengeStore` 提供 `create(scene, ..., payload)` 场景重载与 `markVerified(token)`（一次性迁移，Local=JVM compute / DB=条件 UPDATE+affected-row / Redis=派生票键 SETNX 三实现原子性）；登录级调用点（老五参 create）零改动。Redis 滚动升级注意：老进程读新 JSON（含 scene/payload/verifiedAt 增量键）需 `nop.core.json.parse-ignore-unknown-prop=true`（平台缺省 false）或预留 5 分钟 challenge 排空窗口（TTL 300s）。

### 角色级强制策略与受限会话（W13）

管理员可按角色强制 MFA：策略 = 用户因子**持有约束**（不改变验证所用因子）。无策略行时三层判定退化为一期行为（零回归基线）。

**策略模型与管理 API**：`NopAuthRoleMfaPolicy`（roleId PK 1:1 按需建行，`minMfaLevel` 因子强度下限 1/2/3，`allowTrustedDevice` 缺省 true（W15 可信设备豁免消费），delFlag 软删除）。管理入口 `NopAuthRoleBizModel`：

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

1. `bindMfa`（受限会话）：服务端解析用户登记通道（W15 扩展：**phone 优先、phone 缺失回退 email；双通道均登记时可选 `channel` 参数（phone|email）选择——服务端限定已登记集合，任意指定显式拒绝**；两通道皆空抛 `ERR_AUTH_MFA_NO_RECOVERY_CHANNEL`）→ 无有效票时按通道发码（phone：`SmsCodeStore.send("proof:{userId}")`；email：`EmailCodeStore.send("proof-email:{userId}")`，复用各自 sms-code/email-code 限流配置）→ 抛 `ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED`（channel 脱敏提示：手机号尾 4 位 / 邮箱本地部分前 2 位 + @域名）。
2. `LoginApi__verifyChannelProof(code, channel?)`（登录态；channel 参数与发码通道一致）：按通道校验 proof 码 → 创建 scene=channel-proof 已验证票（`markVerified` 转票，票窗口 = `op-ticket-expire-seconds` 语义）→ 返回票 token。
3. `bindMfa(mfaType, proof: 票token)`：票核验（scene + verifiedAt + userId 绑定）+ 原子 `consume` 一次性消费 → 绑定流程放行。正常会话 `bindMfa` 零改动（无 proof 参数即原行为）。

**confirmMfa 策略校验（防因子降级）**：确认因子强度 < 角色策略 minMfaLevel → `ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK`（errorParams mfaType/mfaLevel）。解绑不受限（`unbindMfa` 本身要求验证当前因子——攻击者无因子不可解绑）；受限会话内 confirmMfa 成功**不原位升级会话**（引导登出后重新登录走完整两阶段）。

**升级引导流（状态机）**：受限登录 → （弱因子用户：unbindMfa 验当前因子解绑）→ 登记通道 proof → bind 强因子 → confirmMfa → 登出 → 重新登录（完整两阶段）→ 完整会话。

**OAuth 入口行为变更（一期遗留 gap 修复，migration note）**：`OAuthLoginServiceImpl.loginAsync`（nop-auth-sso）经 `IMfaLoginPolicyService` SPI（nop-biz-auth-core 接口 / nop-auth-service 实现 bean `nopMfaLoginPolicyService`，`@Inject @Nullable` 可选注入）接入与密码路径同语义判定——从"永不拦截"变为"与密码路径同语义"（null 放行/challenge `ERR_AUTH_MFA_REQUIRED`/受限签发三分支）。策略评估用**本地角色快照**（realm roles 不参与）；无本地用户映射 = 无策略 = 维持一期行为。未部署 nop-auth-service 时零介入。

**审计事件**（`IAuditService.saveAudit` 落 `NopAuthOpLog`）：`mfa-restricted-login`（受限签发）/ `mfa-restricted-rejected`（受限拦截拒绝）/ `mfa:channel-proof-sent`（proof 发码，phone 脱敏）/ `mfa:channel-proof-verified|fail`（proof 验证）/ 策略变更三事件（saveMfaPolicy/removeMfaPolicy）。注意 `NopAuthOpLog.userName` 为非空列——审计请求必须设置 userName，否则批处理整批回滚（W13 E2E 钉定）。

**与操作级 MFA 的分层**：角色策略是登录期持有约束（评估点唯一 = `checkMfaRequired`）；操作级是会话期敏感操作保护。受限分支前置于操作级判定（含 enabled 门）且短路白名单动作的操作级检查；组合仅经 `allowTrustedDevice` 单点（W15 可信设备豁免判定消费——见"可信设备"章节）。

**错误码**（`NopAuthErrors`，不触碰一期编码）：`ERR_AUTH_MFA_RESTRICTED_SESSION`（`nop.err.auth.mfa-restricted-session`，ARG_OPERATION）、`ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED`（`nop.err.auth.mfa-channel-proof-required`，ARG_CHANNEL 脱敏）、`ERR_AUTH_MFA_NO_RECOVERY_CHANNEL`（`nop.err.auth.mfa-no-recovery-channel`）、`ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK`（`nop.err.auth.mfa-policy-factor-too-weak`，ARG_MFA_TYPE/ARG_MFA_LEVEL）。

### MFA 敏感数据治理（通用 CRUD 写路径收口，A2-followup-1）

MFA 敏感表**禁止通用 CRUD 写**：八张表的 BizModel（共同基类 `MfaSensitiveTableBizModel`，`io.nop.auth.service.entity`）对继承的写动作（save/update/delete/batchDelete/batchUpdate/batchModify/saveOrUpdate/updateByQuery/deleteByQuery/copyForNew/recoverDeleted）逐个覆写为显式拒绝——`ERR_AUTH_MFA_CRUD_DISABLED`（`nop.err.auth.mfa-crud-disabled`，ARG_ACTION/ARG_BIZ_OBJ_NAME，文案指引专项入口）。被禁动作在 biz schema 中仍可见但调用即拒（非静默失败）；读类动作（findPage/get/batchGet 等）不受影响。多对多关联三动作无需覆写（这些实体无多对多 prop，基类 fail-closed）。

**各表唯一合法写入口**：

| 表 | 唯一写入口（专项动作/组件） | 被关闭的攻击面（A2 finding） |
|---|---|---|
| `NopAuthMfaSetting` | `NopAuthUserBizModel` bindMfa/confirmMfa/unbindMfa 族、`generateRecoveryCodes`、`resetUserMfa`（admin） | D5-F1：绕过绑定状态机与 proof 前置直写 status/secret |
| `NopAuthMfaCredential` | WebAuthn 注册/解绑/移除 ceremony + 因子失效边界物理删除（unbindMfa/resetUserMfa/恢复码使用） | D5-F1：无 last-credential 守卫的 delete |
| `NopAuthMfaTrustedDevice` | `MfaTrustedDeviceManager`（豁免登记/撤销矩阵）；管理端 `NopAuthMfaTrustedDevice__delete`（admin 校验 + manager 物理删除 + revoke 族审计 reason=admin-removed）；自助 `NopAuthUser__removeTrustedDevice`（本人限定） | D6-1：伪造 deviceHash/expireAt 注入 30 天登录级豁免 |
| `NopAuthRoleMfaPolicy` | `NopAuthRole__saveMfaPolicy` / `NopAuthRole__removeMfaPolicy`（requireAdmin + minMfaLevel 1/3 校验 + 审计） | D5-F1：绕过 requireAdmin 与强度校验 |
| `NopAuthMfaRecoveryCode` | `regenerateRecoveryCodes`（生成/作废）+ `LoginServiceImpl.verifyRecoveryCode` 的条件写置 used | D3-F3：植入自算恢复码 / 复活已用码 |
| `NopAuthMfaChallenge` | `MfaChallengeStore` 组件（create/incrFailCount/consume/markVerified） | 同族边界裁定（码表植入 = 第一因子旁路原语） |
| `NopAuthSmsCode` | `SmsCodeStore` 组件（send/verify） | 同上 |
| `NopAuthEmailCode` | `EmailCodeStore` 组件（send/verify） | 同上 |

**同族边界裁定**：三张瞬态码表（MfaChallenge/SmsCode/EmailCode）虽未被 A2 findings 单列，但其通用 mutation 通道与 D5-F1/D6-1/D3-F3 同族同机制（持权限者直接植入验证码/challenge 行后走正常验证流 = 第一因子旁路原语），已一并纳入收紧（码表行仅由 store 组件管理，无任何合法手工建行入口）。

**管理页配套**：八张表的管理页（`nop-auth-web/.../pages/`）收敛为只读监控面（移除新增/编辑/批量删除按钮与 add/update 提交页，`x:override="remove"` delta）；TrustedDevice 页保留行删除按钮（对接管理端 delete carve-out）。`_nop-auth.action-auth.xml` 的 query/mutation 资源声明保持不变（mutation 资源仅控制菜单/按钮可见性，服务端拒绝才是防线；TrustedDevice 管理端 delete 仍需 mutation 权限）。

**验证**：容器级 E2E `TestMfaCrudLockdownE2E`（每张收紧表至少一个 mutation 经 GraphQL 入口断言错误码；Setting 全继承动作面枚举拒绝；TrustedDevice carve-out 非 admin 拒 + admin 物理删除 + 审计；RoleMfaPolicy 专项路径回归）。

**TOTP 绑定/解绑失败上限（A2-followup-1 D1-1）**：`confirmMfa`/`unbindMfa` 的 TOTP 分支失败计数持久化在 setting 行（`TOTP_FAIL_COUNT`/`TOTP_FAIL_AT` 列，`UPDATE ... SET TOTP_FAIL_COUNT = COALESCE(...)+1` 原子递增、**REQUIRES_NEW 独立事务**先行落库——外层 mutation 事务随后抛错回滚不影响计数）。达上限（`nop.auth.mfa.totp-verify-max-fails`，缺省 5）后：pending 路径（confirmMfa）作废 bindToken → `ERR_AUTH_MFA_BIND_EXPIRED`（重新 bindMfa 的新 token 在冷却窗口内同样被拒——防 bind/confirm 循环绕过）；enabled 路径（unbindMfa）进入冷却窗口（`nop.auth.mfa.totp-cooldown-seconds`，缺省 300s）→ `ERR_AUTH_MFA_COOLDOWN`，窗口过期后可重试。成功验证清零。SMS/EMAIL 分支不引入本计数（store 内部 max-attempts 已覆盖）。

**恢复码条件写（A2-followup-1 D3-F2）**：`verifyRecoveryCode` 的 used 置位为条件写（`WHERE SID=? AND USED=0` + affected-row 判定）——并发双 verify 同码恰一次成功；regenerate 与并发 verify 竞态随条件写闭合；对外错误码保持 `ERR_AUTH_MFA_FAIL` 统一面。

**channel-proof 三事件面补全（A2-followup-1 D1-3）**：`mfa:channel-proof-sent|verified|fail` 三事件齐全——fail 为验证侧 MISMATCH 补齐（含 userId + 脱敏 target）；发送侧拒绝（限流/channel-disabled/store-missing）补 `mfa:channel-proof-send-fail`（reason 区分，target 脱敏）。审计字段永不包含明文联系方式。

**setting.phone 出参脱敏（A2-followup-1 D1-7，结构性排除）**：`NopAuthMfaSetting` 的 phone 列 ORM tagSet `not-pub` + xmeta 保留文件兜底 `published=false`——通用查询面（findPage/get）输出**不含** phone 字段（GraphQL schema 无该 field，选择即校验失败）；biz 面 `getMfaStatus` 的 maskPhone 脱敏输出不变；服务内部 dao 读取（登录/发码链）不受影响。注意 ORM `masked` 标签仅作用于 SQL 日志参数脱敏，不构成出参脱敏机制——出参可见性由 `not-pub` → `published=false` 链承载。

**联系方式变更治理（A2-followup-1 W12 路由项 3）**：`NopAuthUser` 的 phone/email 经通用 CRUD（save/update preparer 路径，ORM 脏属性判定）修改被分级：非 admin 登录调用方（含修改本人行）→ `ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED`（联系方式变更需管理员或专用流程）；admin → 放行 + `user:contact-changed` 审计事件（改人留痕，不落明文新旧值）；无登录态内部调用（批量导入/数据准备）→ 放行。非联系字段（nickname 类）通用修改行为不变。用户自助 changePhone/changeEmail 专用正门（含 proof/MFA ceremony）未提供——需变更联系方式的用户联系管理员。租户变体面 `NopAuthUser_tenant` 复用同一 BizModel，同口径覆盖。

## 相关文档

- `../02-core-guides/auth-and-permissions.md`
- `../reusable-modules-overview.md`
