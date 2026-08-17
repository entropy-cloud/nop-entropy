# W15-impl 邮件验证码 + 可信设备实现（EmailCodeStore 三实现 + 指纹豁免 + 撤销矩阵）

> Plan Status: active
> Mission: nop-credential-mfa
> Work Item: W15-impl（邮件验证码 + 可信设备实现）——MFA 二期组第四个 impl 工作项
> Last Reviewed: 2026-08-17（draft review 两轮：首轮独立 fresh subagent 对抗审查 3 Major + 7 Minor（headers 穿线事实更正/MfaLoginPolicyServiceImpl 副本裁定/聚合器命令空洞绿/锚点漂移等）全部处置，复审 3/3 Major + 7/7 Minor RESOLVED 且二次核对 live 锚点无误，reviewer 终判 READY）
> Source: `ai-dev/design/nop-auth/02-mfa-phase2-design.md` §5.1 结论 3 + §5.3.3 + §5.3.5 + §六 全部（6.1-6.5）+ §八 W15-impl 映射；roadmap W15-impl 条目
> Related: W12-design `2026-08-14-2012-2-mfa-phase2-design.md`（设计收口 plan）；W8 `2026-08-13-0900-1-mfa-db-store-and-default-db.md`（store 三实现装配先例）；W13-impl `2026-08-17-0447-2-mfa-role-level-policy-engine.md`（evaluator allowTrustedDevice 复合结果 + verifyChannelProof 通道基线）；W4/W5/W6 一期 MFA plan 链
> 执行顺序：接 W14-impl（`2026-08-17-2212-1-mfa-webauthn-fido2-w14.md`）之后执行

## Purpose

按设计 §5.3.3 + §六落地两个主题：**邮件验证码**——平行 `EmailCodeStore`（SmsCodeStore 同形接口 + Local/Db/Redis 三实现 + `nop_auth_email_code` 表 + W8 装配模式复刻 `nopEmailCodeStore_` 前缀）+ `MFA_TYPE_EMAIL` 常量与白名单 email 侧变更（bindMfa(email) 发码到登记 email / `sendMfaCode` 按 mfaType 分派 / `MfaFactorVerifier` email 分支）+ `IEmailSender` 接线（复用 `nop-integration-api` 既有实现，零变更）+ 三层限流 + 登记通道 email 解锁（§4.3 W13 仅 phone 的既定扩展）；**可信设备**——新实体 `NopAuthMfaTrustedDevice`（（userId, deviceHash）唯一 + no-tenant 先例）+ 请求头指纹（device-id + UA + Accept-Language → SHA-256，拒绝 canvas/IP）+ 豁免判定（`checkMfaRequired` headers 参数 + 因子等同之后插入 + 固定窗口 30d 不滚动 + `allowTrustedDevice` AND 合并消费）+ 登记路径（mfaVerify 可选 `rememberDevice`，仅密码类 loginType）+ 撤销矩阵（到期/自助移除/因子变更/resetUserMfa/策略禁）+ `listTrustedDevices`/`removeTrustedDevice`。两主题均一期零回归（不勾选/无记录用户逐字节一致；email 因子未绑定用户无感知）。

## 规模裁定（设计 §八 拆分提示的 plan-first 裁定，起草时执行）

本 plan 触碰面估算约 25-32 文件（nop-auth 14+ 含两新表三方言 DDL、nop-biz-auth-api 消息 2、nop-biz-auth-core 接口 1、测试 ~10），超 roadmap 单 plan 参考规模（5-15 文件）。**裁定不拆分（单 plan 双主题）**：roadmap W15-impl 是设计 §八 指派给单一工作项的交付面（邮件码 + 可信设备两主题在 roadmap/设计/A2-audit 依赖链上同属一个 W 工作项）；两主题共享同一批变更面（`MfaVerifyRequest`/`LoginResult` 增量、`checkMfaRequired`/`sendMfaCode`/`MfaFactorVerifier` 白名单触点、`NopAuthUserBizModel` 管理动作）——拆成两 plan 会让同一文件的两次扩展分散在两处执行顺序协调中，且 A2-audit 依赖"W12-15 全 done"以本工作项为单位。主题内各自完整可验证（Phase 1 email 全链 / Phase 2 可信设备全链），无跨 Phase 空壳窗口。执行中若单 Phase 严重超载，按 guide 拆分规则另行 plan-first 裁定。

## Current Baseline

（2026-08-17 live repo 核对）

- **W12-impl/W13-impl 已交付且 `completed`**（详见 W14-impl plan Current Baseline）：`MfaFactorVerifier`（`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaFactorVerifier.java:49`，totp :76-84 / sms :93-95 分支）；`MfaChallengeStore` 场景化；`RoleMfaPolicyEvaluator` 产出复合结果 `{maxLevel, allowTrustedDevice}`（AND 合并——W13 落地，**本 plan 接线消费该布尔**）；`verifyChannelProof` 端点（W13，proof 通道仅 phone——§4.3 "phone 与 email 均登记时 W15 后允许用户选择"为既定扩展位）。
- **`EmailCodeStore` 全仓库不存在（greenfield）**；`SmsCodeStore` 契约在 `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/mfa/store/SmsCodeStore.java:23-40`（send 返回明文码/verify 原子消费+失败计数/consume）；DB 实体 `NopAuthSmsCode`（`nop-auth/model/nop-auth.orm.xml:1248-1288`，表 nop_auth_sms_code）；W8 装配模式：collect-beans `nopSmsCodeStore_` 前缀 + `MfaStoreProvider` + Redis `ioc:condition` 条件激活（lessons 15 类加载安全不变式）。
- **`IEmailSender`**（`nop-integration/nop-integration-api/src/main/java/io/nop/integration/api/email/IEmailSender.java:12-17`：`sendEmail(EmailMessage)` + default 异步包装）：实现 `TencentEmailSender`/`JavaEmailSender` 已在；**仓库内 main 代码零消费方**（邮件验证码消费链即本 plan 建立）；`EmailMessage` 无模板概念（subject/text 直排）。
- **用户 email 列已存在**：`NopAuthUser.email`（orm.xml:63，domain email precision 100）。
- **白名单校验点现状（同 W14 plan 基线，本 plan 消费 email 列）**：#1 常量 `NopAuthConstants.java:42-43`；#2 `bindMfa` 白名单 `NopAuthUserBizModel.java:170-172`（"only totp/sms supported" 硬编码——若 W14 已先行执行则白名单已扩容，本 plan 以 live 状态核对增量）；#3 分派 live:198；#5 `MfaFactorVerifier.verify`；#6 登录级 fail-closed `LoginServiceImpl.java:543-545`；#8 `sendMfaCode` `LoginServiceImpl.java:696-728`（隐式 SMS——W14 重构为按 mfaType 分派后本 plan 接入 email 分支；**若 W14 未先行则本 plan 承担该重构**（执行顺序上 W14 先执行，此处预期仅接入）；#9 ORM mfaType 列 comment。
- **`checkMfaRequired`**（`LoginServiceImpl.java:796-830`）：因子等同 live:826 → challenge 创建 live:828——可信设备豁免判定插入位在两者之间；`loginAsync(LoginRequest, Map<String,Object> headers)` live:261 有完整 headers（`createSessionForUserAsync` 合成空 headers——信道路径不豁免，设计 §6.1 结论 3）。**登记入口 headers 事实（draft review 核定）**：`mfaVerifyAsync(MfaVerifyRequest, Map<String,Object> headers)` live:499 的 headers **仅在入口存在**——内部链路 `verifySecondFactorAndComplete`（live:539 起）不接收 headers，完成点 `completeMfaLogin`（live:650-659）自行 `syntheticRequest(loginType)` + **空 headers** `new HashMap<>()`；且 `completeMfaLogin` 被**恢复码分支共用**（live:588）。登记路径实现必须：(a) 给 `verifySecondFactorAndComplete` 增传 headers（与 rememberDevice 一起穿线）；(b) 登记逻辑放 **verifySecondFactorAndComplete 的非恢复码成功路径**（放 `completeMfaLogin` 内会误登记恢复码分支——设计 §6.4 拒绝项）；(c) `trustedDeviceRegistered` 布尔从服务层（返回 `CompletionStage<IUserContext>`）回填 LoginResult 的机制 = **IUserContext attr 携带**（现成先例：`ATTR_MFA_ACCESS_CODE` 经 `ctx.setAttr` 设置、`LoginApiBizModel` live:174 `ctx.getAttr` 消费构建响应）。**`checkMfaRequired` 的第三处同构实现**：W13 的 `MfaLoginPolicyServiceImpl.checkMfaForUserName`（OAuth 入口，live:86-111）——本 plan 显式裁定其**不加豁免分支**（OAuth=信道路径无 headers，豁免结构性不可达；向副本同步豁免分支 = 永不可达死代码），并补 OAuth 路径"有可信设备行仍创建 challenge"回归断言（履行 W13 登记的双方同步不变式——同步义务以"裁定 + 回归断言"形式履行）。
- **请求/响应契约**：`MfaVerifyRequest`（nop-biz-auth-api/messages）challengeToken/code/recoveryCode（W14 增 assertion 后增量核对）——**无 rememberDevice**；`LoginResult` 现有 accessCode/mfaRestricted 可选字段——**无 trustedDeviceRegistered**。
- **`NopAuthUserBizModel` 撤销钩子锚点（live 核定）**：`confirmMfa` live:293、`unbindMfa` live:352（@MfaRequired live:351）、`resetUserMfa` live:432（@MfaRequired live:431）、`generateRecoveryCodes` live:382（@MfaRequired）。
- **EmailCodeStore 装配触点（单文件可复核）**：全部在 `nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml`——`nopSmsCodeConfig` :29、三个 `nopSmsCodeStore_{local,db,redis}`、`ioc:collect-beans` 两处 :69/:72、**工厂 bean `nopActiveSmsCodeStore` :79（消费方 `@Nullable` 按类型注入的解析来源——EmailCodeStore 需平行 `nopActiveEmailCodeStore`，最易漏的一环）**；文件内嵌 W8 教训注释（config bean id 不得落入 collect 前缀——`nopEmailCodeConfig` 命名规避）。
- **`NopAuthMfaTrustedDevice` 不存在（greenfield）**；姊妹实体先例 `NopAuthMfaSetting`（TENANT_ID 数据列 + `tagSet="...,no-tenant"`）；ORM 末实体 `NopAuthRoleMfaPolicy` live:1289-1329（W14 新增 NopAuthMfaCredential 后以 live 末位为准）。
- **头读取先例**：`extractClientIp` 双大小写不敏感读头（一期）；操作级票头 `X-Nop-Op-Mfa-Token`（`OperationMfaCheckerImpl.java:70`）——自定义请求头读取契约同族。
- **配置/错误码**：`NopAuthConfigs` 无 email-code/trusted-device 组（sms-code 组 live:126-154 为限流模板先例：enabled/expire-seconds/send-interval-seconds/daily-limit/ip-daily-limit/max-attempts）；`checkSmsRateLimit`（LoginServiceImpl）为限流实现先例（phone+IP 双维度内存计数）。
- **撤销矩阵触发点现状**：`unbindMfa`（live:352，@MfaRequired live:351——W14 扩展 assertion 后增量核对）；`confirmMfa` live:293（换绑判定点 = confirmMfa 成功）；`resetUserMfa` live:432（管理员重置）。
- **测试基线**：`TestMfaLoginE2E`/`TestMfaUserSelfService`/`TestOperationMfaE2E`/`TestMfaRestricted*` + W8 store 测试家族（TestSmsCode* 三实现生命周期——EmailCodeStore 测试同型）；pre-existing flake 口径同 W12 登记。

## Goals

- **EmailCodeStore**：接口（`nop-biz-auth-core`，SmsCodeStore 同形：`send(key)` 生成 6 位码返回明文 / `verify(key, code): CodeVerifyResult` 原子消费+失败计数 / `consume(key)`）+ 三实现（Local 在 core；Db/Redis 在 nop-auth-service，Db 表 `nop_auth_email_code` 结构对齐 `nop_auth_sms_code`）+ 装配复制 W8 模式（collect-beans `nopEmailCodeStore_` 前缀 + `ioc:condition` 条件激活 + lessons 15）+ `MfaStoreProvider` 扩展（第三组 map 或平行 provider——执行期最小改动裁定，语义等等价，回写设计）。
- **邮件因子接线**：`MFA_TYPE_EMAIL` 常量 + factorLevel 核对（`"email"`→1 已在 W13 表——断言核对）；key 约定 `mfa-email:{userId}`（通道隔离）；`bindMfa(email)`（白名单 #2 扩容 + 分派 #3 email 同 sms 形——发码到服务端解析的 `NopAuthUser.email`，不接受客户端指定邮箱）+ `confirmMfa` email 分支（`MfaFactorVerifier` email 分支 `EmailCodeStore.verify("mfa-email:{userId}")`）+ 登录级 `mfaVerify` email 分支（#6 经 verifier）+ `sendMfaCode` email 分派（#8：email → EmailCodeStore.send + 邮件发送）+ `mfa-type.dict.yaml` 补 email 条目。
- **发送链路**：`EmailMessage` 文案配置化（`nop.auth.email-code.subject-template`/`text-template`，`{code}` 占位服务端替换）；`IEmailSender` `@Nullable` 注入（未装配时 email 因子绑定/发码 fail-closed，对齐 sms 无通道行为）；发送目标一律服务端从用户档案解析。
- **限流**：`nop.auth.email-code.*` 配置组（enabled 缺省 false / expire-seconds 300 / send-interval-seconds 60 / daily-limit 20 email 维度 / ip-daily-limit 50 / max-attempts 5）；复用 `checkSmsRateLimit` 模式（email+IP 双维度）；发码入口 = bindMfa(email)/sendMfaCode/登记通道（email 码无独立公开发码端点——无邮箱登录场景）。
- **登记通道 email 解锁**（§4.3 既定扩展）：受限会话 proof 发码通道从"仅 phone"扩展为"phone 缺失回退 email；双通道均登记时允许用户选择（服务端限定已登记通道集合，不接受任意指定）"——通道选择参数形态执行期定稿并回写设计；`verifyChannelProof` 按 proof 码实际通道校验；W13 proof 限流先例（sms-code 配置组）对 email 维度按 email-code 组扩展。
- **NopAuthMfaTrustedDevice 实体**：sid 主键 + userId + deviceHash（（userId, deviceHash）唯一约束）+ deviceName + expireAt + lastUsedAt + TENANT_ID 数据列与 `tagSet="...,no-tenant"`（姊妹实体先例）+ 通用审计字段；三方言 DDL。
- **指纹计算**：`X-Nop-Mfa-Device-Id` 头（前端生成持久化 UUID，非秘密）+ `User-Agent` + `Accept-Language` 三输入 SHA-256（头读取大小写不敏感）；无 device-id → null（降级正常 MFA，非错误）；拒绝 canvas/硬件/行为指纹与 IP。
- **豁免判定**：`checkMfaRequired` 增加 headers 参数（protected 单模块内签名变更——信道路径传 null 不豁免）；插入位 = 因子等同（live:826）之后、challenge 创建（live:828）之前；命中（未过期行）→ 更新 lastUsedAt（**不续 expireAt**，固定窗口）→ 放行 return null；`policy.allowTrustedDevice=false` 时跳过豁免（evaluator 复合结果 AND 合并消费——W13 产出）；仅密码类登录路径（loginType 1/2/3/5）。
- **登记路径**：`MfaVerifyRequest` 可选 `rememberDevice`（跨模块公共 API 增量 + migration note）+ `LoginResult` 可选 `trustedDeviceRegistered`（仅密码类路径返回）；mfaVerify 成功路径登记（同 hash 含过期行 upsert 覆盖刷新不受 max-count 限制；满员显式 `trustedDeviceRegistered=false` 提示不阻断登录；**恢复码分支不登记**；信道类 loginType 不登记）；登记在 completeLogin 之前纯 DB 写。
- **撤销矩阵**：expireAt 自然到期（惰性失效，行保留供审计）；`removeTrustedDevice(sid)` 物理删除（用户自助）；`unbindMfa` 成功 / 换绑（confirmMfa 成功）全量删除该用户行；`resetUserMfa` 全量删除；策略禁豁免不删行（判定跳过，放宽后恢复）。
- **管理入口**：`listTrustedDevices`（含过期标记）/`removeTrustedDevice`（`NopAuthUserBizModel` 扩展，W6 并入先例）。
- **配置组**：`nop.auth.mfa.trusted-device.ttl-days`（缺省 30）/`max-count`（缺省 5，仅计未过期行；满员新增显式拒绝，拒绝静默 LRU 淘汰）。
- **E2E**：email 因子全链（bindMfa(email) 发码→confirmMfa→登录拦截→mfaVerify(email code)→completeLogin→sendMfaCode email 分派→限流三层）；可信设备全链（密码登录→mfaVerify rememberDevice→登记→下次登录豁免→固定窗口不续期→撤销矩阵各路径→allowTrustedDevice=false 策略不豁免→信道路径不豁免不登记→恢复码不登记）；一期零回归（无 device-id/无记录/未绑 email 用户逐字节一致）。
- 文档：`docs-for-ai/03-modules/nop-auth.md` 邮件验证码 + 可信设备章节 + 设计 §5.3.3/§六回写 + roadmap。

## Non-Goals

- WebAuthn（W14——`MfaFactorVerifier` assertion 通道仅核对不扩展）。
- 信道路径（SSO 4/信道 20-23/OAuth）的可信设备豁免与登记（设计 §6.1 结论 3 设计边界 + §七.3 deferred——`createSessionForUserAsync` 无 headers，扩 `ISessionBootstrap` 属跨模块公共 API 变更收益不抵成本）。
- 操作级 MFA 的可信设备豁免（设计 §6.4 拒绝——可信设备 ≠ 当前操作者仍是本人）。
- 滚动续期（设计 §6.4 拒绝——固定窗口保证周期性完整 MFA 重新验证）。
- 静默 LRU 淘汰（设计 §6.4 拒绝——显式管理优于隐式驱逐）。
- 泛化改名 `SmsCodeStore` → `OtpCodeStore`（设计 §5.4 拒绝——改名即跨模块公共 API 变更，平行 EmailCodeStore 一期零触碰）。
- `IEmailSender` 增加模板概念（设计 §5.4 拒绝——文案配置在消费侧，API 零变更）。
- 邮箱登录 loginType（无此场景——email 码仅服务 MFA 绑定/验证/通道验证；#7 因子等同分支不动，显式声明）。
- A2-audit；前端交互（设备管理页/绑定页——设计 §七.5 deferred）；nop-integration 深度迁移的 email 家族凭证接线（W16 扩展批次工作项）。

## Scope

### In Scope

- `nop-service-framework/nop-biz-auth-core`：`EmailCodeStore` 接口 + Local 实现 + `_vfs/dict/auth/mfa-type.dict.yaml` email 条目；`MfaChallengeStore`/`SmsCodeStore` 既有契约零变更。
- `nop-auth/model/nop-auth.orm.xml`：新实体 `NopAuthEmailCode` + `NopAuthMfaTrustedDevice` + mfaType 列 comment 更新（email 入）；`deploy/sql/{mysql,postgresql,oracle}/` 增量 DDL。
- `nop-auth/nop-auth-service`：`EmailCodeStore` Db/Redis 实现 + 装配注册（collect-beans + 条件激活）；`MfaStoreProvider` 扩展；`LoginServiceImpl`（checkMfaRequired headers 参数 + 豁免分支 + mfaVerify 登记路径 + sendMfaCode email 分派 + email 发送链路 + 限流）；`MfaFactorVerifier` email 分支；`NopAuthUserBizModel`（bindMfa/confirmMfa email 路径 + unbindMfa/confirmMfa/resetUserMfa 撤销钩子 + listTrustedDevices/removeTrustedDevice）；`LoginApiBizModel`（登记通道 email 解锁 + proof 通道选择）；`NopAuthConstants`/`NopAuthErrors`（如需）/`NopAuthConfigs`（email-code + trusted-device 配置组）；审计。
- `nop-service-framework/nop-biz-auth-api`：`MfaVerifyRequest.rememberDevice` + `LoginResult.trustedDeviceRegistered`（跨模块公共 API 增量，migration note）。
- `nop-integration`：**零变更**（`IEmailSender`/`TencentEmailSender` 复用——消费方注入即可）。
- 测试：store 三实现生命周期（对齐 W8 TestSmsCode* 家族）+ email 因子全链 + 可信设备豁免/登记/撤销矩阵 + 限流 + 零回归。
- `docs-for-ai/03-modules/nop-auth.md`、设计 §5.3.3/§六回写、roadmap、日志。

### Out Of Scope

- W14 WebAuthn 交付面；A2-audit；前端页面；信道路径豁免；nop-integration 模块任何代码变更。

## Execution Plan

### Phase 1 - EmailCodeStore 三实现 + 邮件因子全链 + 登记通道 email 解锁

Status: planned
Targets: `nop-biz-auth-core`（接口 + Local + dict）、`nop-auth`（ORM 新表 + Db/Redis 实现 + 装配 + LoginServiceImpl/MfaFactorVerifier/NopAuthUserBizModel/LoginApiBizModel 接线）、`nop-biz-auth-api`（如需消息增量）

- Item Types: `Fix | Decision | Proof`

- [ ] **Fix**：`EmailCodeStore` 接口（nop-biz-auth-core，`SmsCodeStore` 同形三方法）+ Local 实现（core 内，同 LocalSmsCodeStore 模式）。
- [ ] **Fix**：ORM 新实体 `NopAuthEmailCode`（表 `nop_auth_email_code`，结构对齐 `nop_auth_sms_code`：key/手机位改 email 维度语义核对/code/expireAt/failCount/发送时间列族；model-first → codegen → `_create_` 再生成 + 三方言 `_add_email_code_nop-auth.sql`）。
- [ ] **Fix**：Db/Redis 实现（nop-auth-service，对齐 W8 DbSmsCodeStore/RedisSmsCodeStore 语义：verify 原子消费 + 失败内部计数 + 达 max-attempts 作废）+ 装配（**触点单文件**：`auth-service.beans.xml`——`nopEmailCodeConfig` config bean + 三个 `nopEmailCodeStore_{local,db,redis}` + collect-beans `nopEmailCodeStore` 前缀（`ioc:ignore-depends` + `autowire-candidate=false`）+ Redis `ioc:condition` 条件激活（lessons 15：classpath 无 nosql 时 Redis 实现不加载）+ **工厂 bean `nopActiveEmailCodeStore`**（平行 `nopActiveSmsCodeStore` :79 先例——消费方 `@Nullable EmailCodeStore` 按类型注入的解析来源，漏配 = 全部注入点解析失败））+ `MfaStoreProvider` 扩展（Decision：第三组 map 或平行 `EmailStoreProvider`，执行期最小改动裁定并回写设计 §5.3.3）。
- [ ] **Fix**：`MFA_TYPE_EMAIL` 常量 + factorLevel 核对断言（`"email"`→1 已在 W13 表）+ 白名单 #2/#3（bindMfa 白名单扩容 email——以 live 状态核对 W14 已否先行扩容；分派 email 同 bindSms 形：发码到服务端解析 `NopAuthUser.email`；`MfaBindResult` 增加对应响应标记（如 `emailSent`——对齐 bindSms 的 `setSmsSent(true)` 先例，执行期定稿））+ #5 `MfaFactorVerifier` email 分支（`EmailCodeStore.verify("mfa-email:"+userId)`，EXPIRED 抛 `ERR_AUTH_SMS_CODE_EXPIRED` 等价错误码通道——执行期核定是否需 email 专属错误码，最小集定稿）+ #6 登录级 mfaVerify email 分支（经 verifier；未知 mfaType fail-closed 保留）+ #9/#10 comment（"totp/sms/email/webauthn"——与 W14 执行后 live 状态合并核对）+ `mfa-type.dict.yaml` email 条目（**前提对冲**：该文件为 W14 交付物——若 W14 未执行或文件不存在则本 plan 创建含全部四因子条目的完整文件，以 live 状态核对）。
- [ ] **Fix**：发送链路——`nop.auth.email-code.subject-template`/`text-template`（`{code}` 占位）+ `IEmailSender` `@Nullable` 注入（未装配 fail-closed：email 因子 bindMfa/confirmMfa/sendMfaCode 显式报错，对齐 sms `smsSender == null` 行为）+ 发送目标服务端解析（不接受客户端指定）。
- [ ] **Fix**：限流——`nop.auth.email-code.*` 配置组六项（`enabled` 缺省 false 的**门控语义定稿：三个发码入口统一前置**——bindMfa(email)/sendMfaCode email 分支/登记通道 email 路径在 enabled=false 时拒绝 email 码操作（显式错误非静默），对齐 sms-code.enabled 门控发码入口的语义面）+ `checkEmailRateLimit`（复用 `checkSmsRateLimit` 模式，email+IP 双维度内存计数）在三个发码入口生效。
- [ ] **Fix**：`sendMfaCode` email 分派（#8：challenge.mfaType==email → 解析 user.email → 限流 → `EmailCodeStore.send("mfa-email:"+userId)` → 邮件发送；无 email 抛 CHALLENGE_EXPIRED 等价先例错误——对齐 sms "no phone number" 分支）。〔若 W14 未先行执行则本项承担 mfaType 分派重构全部——执行顺序上 W14 先执行，此处预期仅接入 email 分支；以 live 状态核对〕
- [ ] **Fix**：登记通道 email 解锁（W13 `verifyChannelProof`/bindMfa proof 前置扩展）：通道解析从"仅 phone"扩展为 phone 缺失回退 email + 双通道已登记时允许用户选择（服务端限定已登记通道集合；选择参数形态执行期定稿回写设计 §4.3/§5.3.3）；proof 码按通道入 SmsCodeStore（key=`proof:{userId}`）或 EmailCodeStore（key=`proof-email:{userId}`——key 约定执行期定稿，通道隔离原则）；`ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED` 脱敏提示按通道适配（邮箱脱敏先例对齐手机号后 4 位）；`ERR_AUTH_MFA_NO_RECOVERY_CHANNEL` 双通道皆空时语义不变。
- [ ] **Proof**：store 三实现生命周期测试（对齐 W8 TestSmsCode* 家族：send/verify 三态/失败计数超限作废/TTL/原子消费并发——FakeNosql + 真 PrefixTextCodec 路径含 @DataBean 序列化核对，W12 教训）；email 因子全链 E2E（bindMfa(email)→confirmMfa→登录→mfaVerify→sendMfaCode email→限流三层断言）；登记通道 email 路径 E2E（无 phone 有 email 受限用户 proof 全链）。

Exit Criteria:

- [ ] **端到端验证**：email 因子从绑定到登录完成全链经真实容器组件跑通（发码经注入的 IEmailSender 测试实现断言收件目标与文案模板替换）。
- [ ] **接线验证**：`MfaFactorVerifier` email 分支被登录级与绑定级两调用点实际命中；`EmailCodeStore` 三实现经 store-type 配置切换各自被装配消费（三实现装配测试）。
- [ ] **无静默跳过**：IEmailSender 未装配 fail-closed 显式报错；email 维度限流三层各有断言；未知 mfaType fail-closed 保留。
- [ ] **新功能测试**：列出 Phase 1 测试类与用例名。
- [ ] 一期零回归：未绑 email 用户全流程无感知；既有套件断言零修改（sms 路径行为不变）。
- [ ] 文档裁定：No owner-doc update required（章节统一 Phase 3）；跨模块公共 API 增量如有（消息类）migration note 在案。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 可信设备：实体 + 指纹 + 豁免判定 + 登记路径 + 撤销矩阵

Status: planned
Targets: `nop-auth/model/nop-auth.orm.xml`（新实体）、`nop-auth-service`（LoginServiceImpl/NopAuthUserBizModel/NopAuthConfigs）、`nop-biz-auth-api`（MfaVerifyRequest/LoginResult）

- Item Types: `Fix | Proof`

- [ ] **Fix**：ORM 新实体 `NopAuthMfaTrustedDevice`（sid seq 主键 + userId 索引 + deviceHash + （userId, deviceHash）唯一约束 + deviceName + expireAt + lastUsedAt + TENANT_ID 数据列 + `tagSet="...,no-tenant"` 姊妹先例 + createdBy 等通用审计字段；三方言 DDL；model-first 全链）。
- [ ] **Fix**：指纹计算组件（`fingerprint(requestHeaders)`：`X-Nop-Mfa-Device-Id`（大小写不敏感读头）+ `User-Agent` + `Accept-Language` 三输入 `|` 连接 SHA-256 hex；device-id 缺失 → null 不豁免非错误）。
- [ ] **Fix**：`checkMfaRequired` headers 参数（protected 单模块签名变更；`loginAsync` 传真实 headers、`createSessionForUserAsync` 传 null）+ 豁免判定分支（因子等同之后、challenge 创建之前：**进入条件 = headers 非空 且 `policy.allowTrustedDevice == true`**（AND 合并——任一策略行 false 即跳过豁免）且 deviceHash 非空 → 查未过期行 → 命中更新 lastUsedAt（不续 expireAt）+ return null 放行；仅密码类 loginType 1/2/3/5 生效——信道路径 headers=null 结构性跳过）+ evaluator 复合结果 `allowTrustedDevice` 消费接线（W13 已产出，本项为首个消费点）。**同构副本裁定**：`MfaLoginPolicyServiceImpl.checkMfaForUserName`（OAuth 入口）**不加豁免分支**（信道路径无 headers 结构性不可达；同步义务以本裁定 + Phase 2 Proof 的 OAuth 回归断言履行）；**副本同步不变式维护**：本 plan 对 `checkMfaRequired` 的改动（加参 + 插入豁免分支）不改变副本可等价推导的行为面（副本无 headers 即无豁免），设计 §6 回写时登记该裁定。
- [ ] **Fix**：登记路径——`MfaVerifyRequest` 可选 `rememberDevice` + `LoginResult` 可选 `trustedDeviceRegistered`（跨模块公共 API 增量 + migration note）；**headers 穿线**：`mfaVerifyAsync` 入口 headers 增传 `verifySecondFactorAndComplete`（与 request.rememberDevice 一起）——登记逻辑放 **verifySecondFactorAndComplete 的因子验证成功路径（completeLogin 之前纯 DB 写）**，**禁止放 `completeMfaLogin`**（该方法被恢复码分支共用（live:588），放入即恢复码也登记——设计 §6.4 拒绝项）；**结果回填**：`trustedDeviceRegistered` 经 IUserContext attr 携带（`ATTR_MFA_ACCESS_CODE` 先例：服务层 `ctx.setAttr` → `LoginApiBizModel` live:174 `getAttr` 消费构建 LoginResult），仅密码类路径返回。登记语义：rememberDevice=true 且 challenge.loginType ∈ 密码类 → 指纹计算 → null 则响应 false（无 device-id）；同 hash（含过期行）upsert 覆盖刷新（expireAt=now+ttl-days，不受 max-count 限制）；新行需未过期行数 < max-count 否则响应 false（满员提示）；恢复码分支不登记（结构性排除 + 专项断言）；登记失败不阻断登录。**并发裁定**：并发登记可短暂超 max-count 一行（豁免是优化非安全边界，容忍）；同 hash 并发 upsert 撞 (userId, deviceHash) 唯一约束时捕获冲突归一为 update（不外抛异常）。
- [ ] **Fix**：撤销矩阵——`unbindMfa` 成功 + `confirmMfa` 成功（换绑判定点）+ `resetUserMfa` → 删除该用户全部可信设备行；`removeTrustedDevice(sid)` 物理删除（本人数据限定，越权归一"不存在"）；到期惰性失效（判定时不豁免，行保留）；策略禁豁免不删行。
- [ ] **Fix**：`listTrustedDevices`（展示全部行含过期标记，支持自助清理）/`removeTrustedDevice` 管理入口 + `nop.auth.mfa.trusted-device.ttl-days`/`max-count` 配置组 + 审计事件（登记/移除/因子变更全量撤销经 `IAuditService.saveAudit`，userName 非空列设置——W13 教训）。
- [ ] **Proof**：豁免/登记/撤销矩阵测试——豁免命中（二次登录无 challenge）/固定窗口不续期（命中后再过期不再豁免）/同 hash 复活刷新/max-count 满员显式 false/allowTrustedDevice=false 策略跳过豁免（行保留，策略放宽恢复）/信道路径不豁免不登记（headers=null 结构性）/**OAuth 路径回归断言（副本裁定钉定）：有未过期可信设备行的用户经 OAuth 入口登录仍创建 challenge（副本无豁免分支）**/恢复码不登记/无 device-id 降级正常 MFA/撤销四触发（unbind/换绑/reset/自助移除）/登录零回归（无记录用户 checkMfaRequired 逐字节一致——豁免分支短路条件不改变无记录路径）。

Exit Criteria:

- [ ] **端到端验证**：密码登录 → mfaVerify(rememberDevice) → 登记 → 二次登录豁免（无 challenge 直接 completeLogin）→ 30d 后过期 → 重新完整 MFA，全链经真实容器组件跑通。
- [ ] **接线验证**：`checkMfaRequired` headers 参数被 loginAsync 路径传入真实值（信道路径传 null 专项断言）；`allowTrustedDevice` 复合结果在豁免分支被实际消费（策略 false 时未豁免专项用例）。
- [ ] **无静默跳过**：满员/无 device-id 显式响应 `trustedDeviceRegistered=false`（非静默失败非错误）；登记失败不阻断登录但不吞异常（审计/日志）。
- [ ] **新功能测试**：列出 Phase 2 测试类与用例名。
- [ ] 一期零回归：无 device-id/无记录用户登录行为与一期逐字节一致（既有套件断言零修改）。
- [ ] 文档裁定：No owner-doc update required（章节统一 Phase 3）；跨模块公共 API 增量（MfaVerifyRequest.rememberDevice/LoginResult.trustedDeviceRegistered）migration note 在案。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 文档同步 + 设计回写 + 收口验证

Status: planned
Targets: `docs-for-ai/03-modules/nop-auth.md`、`ai-dev/design/nop-auth/02-mfa-phase2-design.md`（§5.3.3/§六回写）、`docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md`、roadmap

- Item Types: `Follow-up | Proof`

- [ ] **Follow-up**：`docs-for-ai/03-modules/nop-auth.md` 补邮件验证码 + 可信设备章节（EmailCodeStore 三实现与装配/email 因子全链/限流/可信设备指纹算法与威胁模型边界/豁免判定与固定窗口/撤销矩阵/管理 API/配置组/登记通道 email 解锁）；功能概览、核心实体表（×2）、配置、源码锚点表同步。
- [ ] **Follow-up**：设计 §5.3.3/§六回写 impl 裁定标注（MfaStoreProvider 扩展形态/proof 通道选择参数形态/错误码定稿/执行期偏离）+ roadmap W15-impl 状态更新。
- [ ] **Proof**：全量验证——`./mvnw test -pl nop-auth/nop-auth-service,nop-service-framework/nop-biz-auth-api,nop-service-framework/nop-biz-auth-core,nop-integration/nop-integration-api -am -T 1C` 绿（**显式子模块路径**——`nop-auth`/`nop-integration` 均为聚合器 pom，`-pl <聚合器>` 不进子模块测试；nop-integration 零变更回归）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth --severity high` 0 NEW；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

Exit Criteria:

- [ ] 文档与 live 实现一致（端点/配置/实体/撤销矩阵可对号）。
- [ ] 验证命令通过（附输出存 `_tmp/`）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] EmailCodeStore 三实现生命周期测试通过（含 Redis 真序列化路径 @DataBean 核对——W12 教训）+ 三实现装配切换测试。
- [ ] email 因子端到端全链（绑定→登录→重发→限流三层）通过；`IEmailSender` 未装配 fail-closed 有专项用例。
- [ ] 登记通道 email 解锁全链（无 phone 有 email 受限用户 proof→bindMfa→重新登录）通过；通道选择限定已登记通道集合有负例（任意指定拒绝）。
- [ ] 可信设备端到端全链（登记→豁免→固定窗口过期→撤销四触发）通过。
- [ ] `checkMfaRequired` headers 参数迁移安全：信道路径传 null 不豁免有结构性断言；无记录用户路径逐字节一致（零回归红线）；`MfaLoginPolicyServiceImpl` 副本"不加豁免分支"裁定在案 + OAuth 路径回归断言通过（W13 同步不变式履行）。
- [ ] mfaVerify 登记路径：headers 穿线（verifySecondFactorAndComplete 增参）+ `trustedDeviceRegistered` attr 回填（ATTR_MFA_ACCESS_CODE 先例）+ 恢复码结构性排除（登记逻辑不在 completeMfaLogin）三事实经测试钉定。
- [ ] 撤销矩阵全触发有专项用例；恢复码不登记有专项断言。
- [ ] `allowTrustedDevice` AND 合并消费（策略 false 跳过豁免、行保留）有专项用例。
- [ ] ORM 变更经 model-first（两新表），无手编生成物；三方言 DDL 齐备。
- [ ] 跨模块公共 API 增量（MfaVerifyRequest.rememberDevice/LoginResult.trustedDeviceRegistered/EmailCodeStore 接口）migration note 在案；`nop-integration` 零变更（git diff 核实）。
- [ ] 一期零回归：既有 MFA 套件断言零修改；sms 路径行为不变；未知 mfaType fail-closed 保留。
- [ ] 无空壳/静默跳过（scan-hollow NEW 0 + fail-closed/满员/false 响应分支各有专项用例）。
- [ ] 受影响 owner docs 已同步 + 设计裁定标注回写。
- [ ] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow：store 装配→发送链→验证链→豁免链→登记链→撤销链全链追踪）。
- [ ] `./mvnw test -pl nop-auth/nop-auth-service,nop-service-framework/nop-biz-auth-api,nop-service-framework/nop-biz-auth-core,nop-integration/nop-integration-api -am` 绿（显式子模块路径；pre-existing flake 按 W12 登记口径）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [ ] checkstyle / 代码规范检查通过（受影响模块 `-Pqa`）。

## Deferred But Adjudicated

（起草时空缺——执行中产生的延期项按 Anti-Slacking 规则填充。预登记倾向：信道路径豁免（§七.3）/前端设备管理页（§七.5）为设计层 deferred，不属本 plan deferred 搬运项。）

## Non-Blocking Follow-ups

（执行后登记；W12/W13 既有登记项不重复搬运。）

- 过期可信设备行的批量清理任务（设计 §七.4 optimization candidate——惰性失效已保证安全，清理属运维优化，可随任一后续 plan 顺带）。

## Closure

Status Note: （收口时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （独立 closure-audit fresh subagent）
- Evidence: （收口时填写）

Follow-up:

- （收口时填写；confirmed live defect 不得出现在这里）
