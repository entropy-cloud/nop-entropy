# W5 - MFA 登录流程两阶段改造 + 短信验证码登录

> Plan Status: active
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md` (W5); `ai-dev/design/nop-auth/01-architecture-baseline.md` (§3.1 / §3.2 / §3.3 / §3.6 / §3.7 / §3.8 / §五)
> Related: W4（MFA 模型 + TOTP + 存储，本 plan 的前置依赖，需先 done）；W6（绑定/解绑/扫码适配，依赖本 plan）；W7（迁移 + docs-for-ai 同步）

## Purpose

把登录流程收口为"第一因子 + 第二因子"两阶段，并落地 `loginType=5` 短信验证码登录。本 plan 直接消费 W4 产出的 ORM 实体、TOTP 验证器、`MfaChallengeStore`/`SmsCodeStore`，使 MFA 在登录链路上真正可用（用户可被拦截、可被验证、可拿到 token/accessCode）。绑定/解绑/管理员重置属于 W6；扫码路径的 MFA *异常捕获与结果适配*（`ScanLoginResult.mfaRequired`）属于 W6。

## Current Baseline

（已核对 live repo）

- 登录主入口 `LoginServiceImpl.loginAsync`（`nop-auth/nop-auth-service/.../login/LoginServiceImpl.java`）现签名仅密码类路径（loginType 1/2/3），成功路径：`runWithTenant → resetLoginFailCountForUser → buildUserContext(user,request) → autoLogout → saveSession(context,request,headers) → userContextHook.onLoginSuccess → saveUserContextAsync → 签发 accessToken`。
- SSO/信道登录走 `ISessionBootstrap.createSessionForUserAsync`（**不走 loginAsync**）。当前签名为**单参数** `createSessionForUserAsync(String userId)`，实现内部**硬编码 `LOGIN_TYPE_SSO=4`**；loginType 4(SSO)/20-23(飞书/钉钉/企微/Webhook)。现有调用方 `ChannelLoginApiBizModel.createSessionForUserAsync`（nop-ai-gateway）以单参数调用。
- `LoginResult` 字段：accessToken/expiresIn/refreshToken/refreshExpiresIn/scope/tokenType/sessionState/userInfo/attrs —— **无 accessCode 字段**。
- `AuthApiConstants`（`nop-biz-auth-api`）loginType 仅 1/2/3/4，**无 5**。
- `loginAsync` 内三处支撑方法：`getAuthUser`（switch 仅 case 1/2/3，default→null）、`needCheckPassword`（1/2/3→true，余→false）、`isValidLoginMethod`（默认仅接受 1，受 `allowedLoginMethods` 配置约束）。
- `login-type.dict.yaml`（`nop-biz-auth-core`）SSO=**10**（与代码 4 不一致），**缺 2/3/5** 条目。
- `NopAuthErrors`（`nop-auth-service`）**无 MFA/SMS 系列错误码**。
- 图形验证码：`IVerifyCodeGenerator` + `IUserContextCache.setVerifyCode/getVerifyCode`，`verifyCodeCache` 默认 60s、仅 Local、无 remove —— 设计 §3.3 已裁定**不复用**该通道（W4 已新增独立 store）。
- accessCode 生成链存在（`generateAccessCode`，TTL 配置 `nop.ai.channel.login.access-code-expire-seconds`，当前仅 nop-ai-gateway 使用）。
- 现有测试涉及签名：`TestChannelLoginApi`（单参数 `ISessionBootstrap` 测试桩）、`TestChannelScanBindLoginE2E`。
- W4 产出（本 plan 前置）：`NopAuthMfaSetting`/`NopAuthMfaRecoveryCode` 实体、`TOTPAuthenticator`、`MfaChallengeStore`/`SmsCodeStore`（Local + Redis）。本 plan 开始时 W4 须为 done。
- 可复用：`ISmsSender`（腾讯/云片）、`IPasswordEncoder`（恢复码 BCrypt）、`AESTextCipher`、`IUserContextCache`（失败计数/会话）、`ContextProvider.runWithTenant`。

## Goals

- 密码类登录（loginType 1/2/3/5）与 SSO/信道登录（4/20-23）均改造为两阶段：第一因子通过 → 若用户启用 MFA（`status==enabled`）则 `MfaChallengeStore.create` 并抛 `ERR_AUTH_MFA_REQUIRED`（errorParams 携带 challengeToken/mfaType/loginType），不签发 token/accessCode。
- 第二因子验证 `mfaVerify`（公开访问）：peek → setting 复核 → recovery 分支（BCrypt 比对/used 统一 MFA_FAIL/成功 consume + status=disabled 强制重绑）/ TOTP 分支（含防重放）/ SMS 分支（独立 key）→ 成功 consume + `completeLogin`，失败 `incrFailCount` 超限作废。
- `loginType=5` 短信验证码登录：`sendSmsCode`/`sendMfaCode`/`mfaVerify`（LoginApi，公开），含手机号/IP 双维度限流 + 防枚举统一响应。
- 抽取 `completeLogin` 公共方法（loginAsync / createSessionForUserAsync / mfaVerify 三处复用），并**裁决三处调用点的行为差异**（`resetLoginFailCountForUser`、`userContextHook`、`LoginRequest`/headers 来源）——裁决记录在 design §3.2。
- `createSessionForUserAsync` 参数化 loginType（新增重载，单参数版本保留向后兼容），并更新 nop-ai-gateway 调用点传入真实信道 loginType，使审计 loginType 不失真。
- `mfaVerify` 成功出口：密码类 loginType 签发 accessToken；信道类 loginType 签发 accessCode（`LoginResult` 新增可选 `accessCode` 字段承载）。
- 修复 `login-type.dict.yaml`（补 2/3/5，SSO 统一为 4，以 `AuthApiConstants` 为准）。
- 配置项（`nop.auth.mfa.*` / `nop.auth.sms-code.*`）+ 错误码（`NopAuthErrors` MFA/SMS 系列）落地。
- 因子等同：短信登录（loginType=5）且 MFA 类型为 sms 时不重复验证（Vision Non-Goals #9）。
- 未启用 MFA 的用户登录流程**零回归**。

## Non-Goals

- 绑定/解绑/恢复码生成/管理员重置 BizModel action（W6）。
- `nop-ai-gateway` 扫码登录 MFA **异常捕获与结果适配**（`ChannelLoginApiBizModel.loginByScan` 捕获 `ERR_AUTH_MFA_REQUIRED` → `ScanLoginResult.mfaRequired`、手机输码→mfaVerify→accessCode→PC 轮询全链）（W6，跨模块公共 API plan-first）。本 plan 仅更新 `createSessionForUserAsync` 的调用点以传入信道 loginType（参数化），不实现扫码结果适配。
- WebAuthn / 邮件验证码 / 操作级 MFA / 角色级强制策略 / 可信设备（二期）。
- `docs-for-ai/` MFA 章节同步（roadmap 显式分配给 W7）。
- 存量 NopAiModel.apiKey 迁移（W7）。

## Scope

### In Scope

- `nop-biz-auth-api`：`AuthApiConstants.LOGIN_TYPE_PHONE_SMS=5`；`LoginApi` 新增 `sendSmsCode`/`sendMfaCode`/`mfaVerify` 方法声明；`MfaVerifyRequest` 请求模型；`LoginResult` 新增可选 `accessCode` 字段（承载信道类 mfaVerify 成功出口）。
- `nop-biz-auth-core`：`ISessionBootstrap` 新增 `createSessionForUserAsync(String userId, int loginType)` 重载（单参数版本 `@Deprecated` 委托到 `LOGIN_TYPE_SSO=4`，向后兼容）；`login-type.dict.yaml` 修复（补 2/3/5、SSO=4）。
- `nop-auth-service`：`LoginServiceImpl.loginAsync` + `createSessionForUserAsync` 两阶段改造；`completeLogin` 抽取与行为差异裁决；`mfaVerify` 实现；短信发送限流；`LoginApiBizModel` 接线（`@BizMutation`/`@Auth(publicAccess=true)`）；支撑方法 `getAuthUser`/`needCheckPassword` 适配 loginType=5。
- `nop-ai-gateway`：**仅**更新 `ChannelLoginApiBizModel` 对 `createSessionForUserAsync` 的调用点，传入其已持有的信道 loginType（参数化，审计不失真）。**不**实现扫码 MFA 结果适配（W6）。
- `NopAuthConfigs`（或等价配置类）：`nop.auth.mfa.*` / `nop.auth.sms-code.*`；`NopAuthErrors`：MFA/SMS 系列错误码。
- 端到端测试：两阶段登录、短信登录、恢复码登录、信道两阶段、失败计数分界、未启用 MFA 零回归。

### Out Of Scope

- 扫码路径的 `ScanLoginResult.mfaRequired` 扩展与 `loginByScan` 异常捕获适配（W6）。
- 用户自助/管理员 MFA 管理 API（W6）。
- 前端登录页 UI 适配（业务层）。
- `docs-for-ai/` 同步（W7）。

## Execution Plan

### Phase 1 - 常量 / 字典 / 配置 / 错误码 / 请求模型

Status: planned
Targets: `nop-biz-auth-api/.../AuthApiConstants.java` + `LoginApi.java` + `LoginResult.java`、`nop-biz-auth-core/.../_vfs/dict/auth/login-type.dict.yaml`、`nop-auth-service` 配置类与 `NopAuthErrors`

- Item Types: `Fix | Decision`

- [ ] `AuthApiConstants` 新增 `LOGIN_TYPE_PHONE_SMS = 5`
- [ ] 修复 `login-type.dict.yaml`：补 2(EMAIL_PASSWORD)/3(PHONE_PASSWORD)/5(PHONE_SMS) 条目，SSO 统一为 **4**（以 `AuthApiConstants` 为准），保留 20-23
- [ ] `NopAuthConfigs`（或等价）新增 `nop.auth.mfa.*`（6 项：enabled/store-type/challenge-expire-seconds/max-attempts/totp-issuer/totp-window-skew）与 `nop.auth.sms-code.*`（8 项：enabled/expire-seconds/send-interval-seconds/daily-limit/ip-daily-limit/max-attempts/template-id/allow-register），共 **14 项**（与设计 §3.7 一致；Phase 3 的 accessCode TTL 裁决若选"新增 MFA 自有键"将追加第 15 项 `nop.auth.mfa.access-code-expire-seconds`），默认值与设计 §3.7 一致
- [ ] `NopAuthErrors` 新增 MFA/SMS 系列（11 个：`ERR_AUTH_MFA_REQUIRED`/`ERR_AUTH_MFA_FAIL`/`ERR_AUTH_MFA_CHALLENGE_EXPIRED`/`ERR_AUTH_MFA_NOT_ENABLED`/`ERR_AUTH_MFA_ALREADY_ENABLED`/`ERR_AUTH_MFA_BIND_EXPIRED`/`ERR_AUTH_SMS_CODE_INVALID`/`ERR_AUTH_SMS_CODE_EXPIRED`/`ERR_AUTH_SMS_RATE_LIMITED`/`ERR_AUTH_SMS_DAILY_LIMIT`/`ERR_AUTH_MFA_RECOVERY_CODE_USED`），`ERR_AUTH_MFA_REQUIRED` 支持 `.param(challengeToken/mfaType/loginType)`
- [ ] 新增 `MfaVerifyRequest`（`nop-biz-auth-api`）请求模型，字段 `{challengeToken, code, recoveryCode?}`（设计 §3.6）
- [ ] `LoginResult` 新增可选 `accessCode` 字段（承载信道类 mfaVerify 成功出口；密码类路径为 null）——公共契约扩展，本 plan 即 plan-first 产物

Exit Criteria:

- [ ] `AuthApiConstants.LOGIN_TYPE_PHONE_SMS` 存在且 = 5
- [ ] `login-type.dict.yaml` 含 1/2/3/4/5/20-23，SSO value=4；与 `AuthApiConstants` 一致（有一致性测试断言，防再次漂移）
- [ ] 全部 **14** 个配置项以设计 §3.7 默认值存在并可注入
- [ ] 全部 **11** 个错误码存在；`ERR_AUTH_MFA_REQUIRED` 可携带 `.param(challengeToken/mfaType/loginType)`
- [ ] `MfaVerifyRequest` 存在且字段为 `{challengeToken, code, recoveryCode?}`
- [ ] `LoginResult.accessCode` 可选字段存在（密码类为 null，向后兼容）
- [ ] **新功能测试**：配置默认值绑定测试、dict 与常量一致性测试、`LoginResult.accessCode` 序列化兼容测试（既有无 accessCode 响应仍可解析）
- [ ] **无静默跳过**：配置缺失时使用文档化默认值并记录，不静默关闭功能
- [ ] 若该 Phase 改变 live baseline：`No owner-doc update required`（`docs-for-ai/` 同步属 W7；公共契约变更已在本 plan 与设计 §五 记录为 plan-first）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - loginAsync + createSessionForUserAsync 两阶段改造 + completeLogin 抽取与行为裁决

Status: planned
Targets: `nop-auth-service/.../login/LoginServiceImpl.java`、`nop-biz-auth-core/.../ISessionBootstrap.java`、`nop-ai-gateway/.../ChannelLoginApiBizModel.java`（仅调用点）、`ai-dev/design/nop-auth/01-architecture-baseline.md` §3.2

- Item Types: `Fix | Decision`

- [ ] **completeLogin 行为裁决（Decision）**：裁决三处调用点的行为差异并回写 design §3.2：(1) `resetLoginFailCountForUser`——loginAsync 成功后调用，createSessionForUserAsync/mfaVerify 不调用（第二因子失败不触发用户锁账号）；(2) `userContextHook.onLoginSuccess`——loginAsync 调用，信道/mfaVerify 路径的归属需裁决；(3) `LoginRequest`/headers 来源——loginAsync 有完整 request+headers，createSessionForUserAsync/mfaVerify 无，需裁决如何为 `buildUserContext`/`saveSession` 提供等价输入（从用户档案/challenge 重建或接受必要输入参数）
- [ ] 抽取 `completeLogin` 公共方法，其行为契约遵循上一条裁决；三处（loginAsync/createSessionForUserAsync/mfaVerify）复用，无重复会话签发逻辑
- [ ] `ISessionBootstrap` 新增重载 `createSessionForUserAsync(String userId, int loginType)`；单参数版本 `@Deprecated` 委托到 `LOGIN_TYPE_SSO=4`（向后兼容外部调用方）
- [ ] `createSessionForUserAsync(userId, loginType)` 两阶段改造：账号检查 → MFA 门禁 → status==enabled 则 `MfaChallengeStore.create` + 抛 `ERR_AUTH_MFA_REQUIRED(challengeToken,mfaType,loginType)`（loginType 为真实信道值，审计不失真）；未启用走 `completeLogin`
- [ ] `ChannelLoginApiBizModel` 调用点改为新重载，经 `ChannelTypeCodes.loginType(channelType)` 派生信道 loginType（20-23）传入（BizModel 当前持有 `channelType` 字符串，无 int loginType）——**仅参数化，不实现扫码结果适配**
- [ ] `loginAsync` 改造：凭证校验 → `mfaConfig.enabled` 门禁 → 查 `NopAuthMfaSetting`（status==enabled 口径）→ 因子等同分支（PHONE_SMS + mfaType==sms 不重复验证）→ `MfaChallengeStore.create` → 抛 `ERR_AUTH_MFA_REQUIRED(challengeToken,mfaType,loginType)`；未启用 MFA 走 `completeLogin`（零回归）
- [ ] `getAuthUser` 适配 loginType=5（按 phone 查找用户）；`needCheckPassword` 适配（loginType=5 不走密码路径，走 SmsCodeStore.verify 分支）；`isValidLoginMethod` 对 loginType=5 的准入（受 `sms-code.enabled` 门禁）
- [ ] 第一因子失败沿用 `setLoginFailCountForUser`（锁账号）；第二因子失败只计 challenge（不锁账号）
- [ ] 更新涉及签名变更的既有测试（`TestChannelLoginApi` 单参数桩、`TestChannelScanBindLoginE2E`）至新重载

Exit Criteria:

- [ ] design §3.2 已记录 completeLogin 行为裁决（三项差异结论）
- [ ] `completeLogin` 被三处复用（代码追踪可证，无重复会话签发逻辑）
- [ ] 启用 MFA 的密码用户：第一因子通过后**不签发 token**，响应携带 `ERR_AUTH_MFA_REQUIRED` + challengeToken/mfaType/loginType
- [ ] SSO/信道用户启用 MFA 同样被拦截（createSessionForUserAsync 路径），challenge.loginType 为真实信道值（20-23），审计不失真（有断言对照）
- [ ] 单参数 `createSessionForUserAsync` 仍可调用（向后兼容，委托 SSO=4）
- [ ] 未启用 MFA 的用户：登录行为与改造前**逐项一致**（签发 token/accessCode、失败计数、审计 loginType、userContextHook 调用）——零回归断言
- [ ] 因子等同：loginType=5 且 mfaType=sms 的用户直接 `completeLogin`，不抛 MFA_REQUIRED（断言）
- [ ] loginType=5 用户查找/方法校验通过（`getAuthUser` 按 phone 命中、`isValidLoginMethod` 准入）
- [ ] 第二因子失败不调用 `setLoginFailCountForUser`（断言）
- [ ] **接线验证**：改造后 `loginAsync`/`createSessionForUserAsync` 确实调用 `MfaChallengeStore.create`（运行时可观测，E2E 中以计数器/mock/日志断言）
- [ ] **无静默跳过**：全局开关关闭时不强制 MFA 是**显式配置门禁**（非静默跳过）；setting 查询异常显式抛出
- [ ] 若该 Phase 改变 live baseline：design §3.2 已更新（completeLogin 裁决）；`docs-for-ai/` 同步属 W7
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - mfaVerify + 短信发送端点 + LoginApi 接线 + 限流

Status: planned
Targets: `nop-auth-service`（`LoginServiceImpl`/`LoginApiBizModel`）、`nop-biz-auth-api`（`LoginApi`）

- Item Types: `Fix | Decision`

- [ ] `LoginApi` 接口新增 `sendSmsCode(String phone)` / `sendMfaCode(String challengeToken)` / `mfaVerify(MfaVerifyRequest request)` 方法声明
- [ ] `LoginApiBizModel` 接线：上述三方法加 `@BizMutation` + `@Auth(publicAccess=true)`；响应形状裁决（sendSmsCode/sendMfaCode 返回确认/Void、mfaVerify 返回 `LoginResult`）
- [ ] `mfaVerify(request)`：peek challenge（null→`CHALLENGE_EXPIRED`）→ loadUser（`runWithTenant(challenge.tenantId)`）→ setting 复核（null/!enabled 作废 challenge）→ recovery 分支（BCrypt 比对/`RECOVERY_USED` 统一 `MFA_FAIL` 不计数不消费/成功 consume + status=disabled 强制重绑 + 审计）→ TOTP 分支（verify + 防重放：当前窗口 ≤ lastVerifiedWindow 拒绝）→ SMS 分支（`SmsCodeStore.verify("mfa:"+userId,code)` 三态）→ 成功 consume + `completeLogin(user, challenge.loginType)`；失败 `incrFailCount` ≥ max-attempts 作废
- [ ] `mfaVerify` 成功出口：按 challenge.loginType 决定（密码类签发 accessToken 填入 LoginResult；信道类签发 accessCode 填入 `LoginResult.accessCode`）；accessCode TTL 裁决（见下条）
- [ ] **accessCode TTL 跨模块裁决（Decision）**：`nop.auth.mfa.access-code-expire-seconds` 配置归属——裁定为在 `NopAuthConfigs` 新增 MFA 自有键（默认值与 `nop.ai.channel.login.access-code-expire-seconds` 一致），避免 nop-auth-service 反向依赖 nop-ai-gateway 命名空间的配置；或显式记录跨模块重用理由。裁决结论写入 plan/design
- [ ] `sendSmsCode(phone)`（公开）：防枚举（未注册且 !allow-register 统一返回"已发送"）→ 手机号 60s 间隔 + 日上限 + IP 日上限限流 → `SmsCodeStore.send` → `ISmsSender.sendMessage`
- [ ] `sendMfaCode(challengeToken)`（公开）：peek 校验 challenge 未消费/作废（否则 `CHALLENGE_EXPIRED`）→ 服务端取号 → 限流同 sendSmsCode
- [ ] MFA 短信路径双计数保留（`SmsCodeStore` 内部失败计数 + challenge `incrAndCheck`），两者独立生效

Exit Criteria:

- [ ] `LoginApi` 三个新方法声明存在且为 publicAccess；可通过 GraphQL/REST 访问（schema 中可见）
- [ ] mfaVerify 成功路径：密码类签发 accessToken、信道类签发 `LoginResult.accessCode`（有断言区分两种出口）；challenge 被一次性 consume（再验同一 token 返 `CHALLENGE_EXPIRED`）
- [ ] recovery 分支：已用恢复码统一 `MFA_FAIL`（不计数、不消费、不暴露"曾有效"）；成功恢复码登录后 status=disabled（强制重绑）+ 恢复码作废
- [ ] TOTP 防重放：同窗口码二次提交被拒（依赖 W4 的 lastVerifiedWindow 判定面，本 phase 串联）
- [ ] SMS 三态错误码与 loginAsync 口径一致（EXPIRED→`ERR_AUTH_SMS_CODE_EXPIRED`/INVALID→`ERR_AUTH_SMS_CODE_INVALID` 双错误码）
- [ ] 限流：同手机号 60s 内二次发码被拒（`RATE_LIMITED`）；日上限/IP 上限生效；防枚举：未注册手机号响应与已注册一致
- [ ] accessCode TTL 裁决已记录，实现与裁决一致
- [ ] **接线验证**：mfaVerify 确实调用 `MfaChallengeStore.peek/incrFailCount/consume`、`TOTPAuthenticator.verify`、`SmsCodeStore.verify`、`IPasswordEncoder`（recovery）、`ISmsSender`（sendSmsCode/sendMfaCode）——E2E 中可观测
- [ ] **无静默跳过**：challenge 不存在、setting 复核失败等分支显式作废并返回错误码，不静默放行
- [ ] 若该 Phase 改变 live baseline：`No owner-doc update required`（`docs-for-ai/` 同步属 W7）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端测试 + 回归

Status: planned
Targets: `nop-auth-service` 测试 + `nop-ai-gateway` 既有回归（扫码链仅调用点变更，验证不回归）

- Item Types: `Proof`

- [ ] E2E：密码登录→MFA_REQUIRED→mfaVerify(TOTP)→accessToken 全链
- [ ] E2E：短信验证码登录（sendSmsCode→login(loginType=5)→accessToken），含因子等同（mfaType=sms 不重复验证）
- [ ] E2E：恢复码登录（mfaVerify(recoveryCode)→accessCode/accessToken + status=disabled + 恢复码作废）
- [ ] E2E：SSO/信道（createSessionForUserAsync(userId,loginType)）启用 MFA 用户被拦截→mfaVerify→accessCode（loginType 不失真断言）
- [ ] 失败计数分界：第二因子连续失败 max-attempts 后 challenge 作废、且不触发用户锁账号
- [ ] 零回归：未启用 MFA 用户登录（密码/SSO/信道）行为与改造前逐项一致；全局开关关闭时所有 MFA 路径不触发
- [ ] dict 记录核对：`nop_auth_ext_login` 中 loginType=10 的存量记录检查结果已记录（预期为空——信道编码从 20 起）

Exit Criteria:

- [ ] 上述 7 条 E2E/场景测试全部通过，且测试名与覆盖行为在 plan 中可对照
- [ ] **端到端验证（Anti-Hollow Rule #22）**：从用户入口点（login/sendSmsCode）经第一因子→challenge→mfaVerify→token/accessCode 出口的完整路径跑通。**Local store-type 的 E2E 完整跑通**；Redis store-type 的连通性经 W4 已建立的原语映射/代码追踪覆盖（仓库内无嵌入式 Redis 测试设施，本 plan 不新增——与 W4 Phase 3 三证合一基线一致）
- [ ] **接线验证（Wiring Rule #23）**：E2E 中断言 W4 组件（`MfaChallengeStore`/`SmsCodeStore`/`TOTPAuthenticator`）在登录链路上确实被调用（计数器/标志/mock verify）
- [ ] **无静默跳过（Rule #24）**：新增分支在异常输入下显式失败（有对应负向用例）
- [ ] 若该 Phase 改变 live baseline：`No owner-doc update required`（`docs-for-ai/` 同步属 W7）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本 plan 改动跨模块公共 API（`AuthApiConstants`/`LoginApi`/`LoginResult.accessCode`/`ISessionBootstrap.createSessionForUserAsync` 重载）——Protected Area，plan-first。本 plan 即为 plan-first 产物；`createSessionForUserAsync` 采用**向后兼容重载**策略（单参数版本保留），migration 影响面 = 更新 nop-ai-gateway 调用点 + 既有测试桩。

- [ ] 两阶段登录在密码/SSO/信道三类入口均成立；未启用 MFA 用户零回归
- [ ] loginType=5 短信登录端到端可用（含限流/防枚举）
- [ ] mfaVerify 覆盖 TOTP/SMS/recovery 三分支，密码类→accessToken / 信道类→accessCode 出口正确，challenge 一次性 + 失败计数分界正确
- [ ] completeLogin 行为裁决已写入 design §3.2，三处复用且行为差异处理一致
- [ ] `createSessionForUserAsync` 重载向后兼容；nop-ai-gateway 调用点已参数化；既有测试已适配
- [ ] dict 修复与 `AuthApiConstants` 一致；§3.7 的 14 配置项 / 11 错误码齐全（Phase 3 accessCode TTL 裁决若选自有键则含第 15 项）
- [ ] 必要 focused verification（E2E 全链 + 回归）已完成
- [ ] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs 已裁定：design §3.2 已更新（completeLogin 裁决）；`docs-for-ai/` 同步属 W7（显式 scope move）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证登录链路在运行时确实调用 W4 组件（端到端连通），无空方法体/静默跳过/no-op
- [ ] `./mvnw compile -pl nop-auth,nop-service-framework/nop-biz-auth-core,nop-service-framework/nop-biz-auth-api,nop-ai-gateway -am`
- [ ] `./mvnw test -pl nop-auth,nop-service-framework/nop-biz-auth-core,nop-ai-gateway -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### nop-ai-gateway 扫码 MFA 异常捕获与结果适配（ScanLoginResult.mfaRequired）

- Classification: `out-of-scope improvement`（移入 W6）
- Why Not Blocking Closure: roadmap 显式将扫码 *结果适配*（`loginByScan` 捕获 `ERR_AUTH_MFA_REQUIRED` → `ScanLoginResult.mfaRequired`、手机→mfaVerify→accessCode→PC 轮询）划入 W6，属跨模块公共 API plan-first。W5 已完成 `createSessionForUserAsync` 的 loginType 参数化与调用点更新（审计不失真前置条件），使 W6 可直接串联。W5 须保证既有扫码链（除调用点参数化外）不回归。
- Successor Required: yes → W6

## Non-Blocking Follow-ups

- 前端登录页对 `ERR_AUTH_MFA_REQUIRED` 的 UI 适配（业务层）
- 限流策略的精细化（按业务调参，部署期治理）
- 设计文档勘误：`LoginApi` 下列出的 `generateVerifyCode` 实际位于 `LoginApiBizModel`（非接口），W7 docs-for-ai 同步时一并修正

## Closure

Status Note: (待 closure audit 填写)
Completed: (待填写)

Closure Audit Evidence:

- Reviewer / Agent: (待独立 closure audit 填写)
- Evidence: (待填写)

Follow-up:

- (待填写)
