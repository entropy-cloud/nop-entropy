# W6 - MFA 用户自助/管理员 API + nop-ai-gateway 扫码 MFA 适配

> Plan Status: active
> Last Reviewed: 2026-08-13
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md` (W6); `ai-dev/design/nop-auth/01-architecture-baseline.md` (§3.2 扫码时序 / §3.4 恢复码 / §3.6 API 契约 / §五 跨模块契约)；`ai-dev/design/nop-auth/00-vision.md` (Non-Goals #9 因子等同)
> Mission: nop-credential-mfa
> Work Item: W6
> Related: W4（done，MFA 实体/TOTP/stores）、W5（done，登录两阶段 + mfaVerify + createSessionForUserAsync 已抛 `ERR_AUTH_MFA_REQUIRED`，扫码结果适配显式 deferred 到本 plan）

## Purpose

把 MFA 收口到"用户可配置、管理员可重置、扫码登录可走通"。W5 已完成登录链路两阶段化（第一因子 → `ERR_AUTH_MFA_REQUIRED` → 第二因子 → 签发 token/accessCode），但用户还无法**绑定/解绑** MFA、管理员无法**重置**、扫码登录（`loginByScan`）尚未**捕获** MFA 异常。本 plan 补齐这三块，使 MFA 成为端到端可用的能力。

## Current Baseline

（已核对 live repo）

- `NopAuthUserBizModel`（`nop-auth/nop-auth-service/.../entity/NopAuthUserBizModel.java:38-40`，`@BizModel("NopAuthUser")` extends `CrudBizModel<NopAuthUser>`）现有 action：`getUserByOpenId`/`resetUserPassword`/`changeSelfPassword`/`enableUser`/`disableUser`——**无任何 MFA action**（grep `Mfa|bind|totp|recovery` 零命中）。
- `NopAuthUserMaintenanceBizModel` **不存在**（全仓库 grep 仅命中 design/backlog 文档，无源码）。design §3.6 计划 `resetUserMfa` 归属此类。
- `ScanLoginResult`（`nop-ai/nop-ai-gateway/.../login/ScanLoginResult.java`）当前**仅有 `accessCode` 一个字段**——无 `mfaRequired`/`challengeToken`/`mfaType`/`loginType`。
- `ChannelLoginApiBizModel.loginByScanAsync`（`nop-ai/nop-ai-gateway/.../login/ChannelLoginApiBizModel.java:131`，`@BizMutation @Auth(publicAccess=true)`）在 `:183` 调用 `sessionBootstrap.createSessionForUserAsync(platformUserId, resolvedLoginType)`，**无 try/catch、无 `.exceptionally(...)`**（grep `ERR_AUTH_MFA_REQUIRED|challengeToken|mfaRequired|exceptionally` 在该文件零命中）。今天启用 MFA 的用户走扫码时，`ERR_AUTH_MFA_REQUIRED` 异常**原样冒泡**到 GraphQL/REST 调用方。
- `LoginServiceImpl.createSessionForUserAsync`（`:337`）已（W5）在 `:348` 执行 `checkMfaRequired` 并在 `:350-355` **同步抛出** `ERR_AUTH_MFA_REQUIRED`，携带 `.param(challengeToken/mfaType/loginType)`——challenge 已在上游创建并存储。**本 plan 只需捕获 + 适配，不需重建 challenge。**
- PC 轮询 `getLoginResultAsync`（`LoginApiBizModel.java:64`，nop-auth-service）经 `loginService.parseAccessCode` → `getUserContextAsync` → `buildLoginResult` 签发 token/accessCode——**本 plan 不改**，扫码端点产出 accessCode 后由前端经此既有端点兑换。
- W4 产出（本 plan 前置，均已就位）：`NopAuthMfaSetting`（userId PK / mfaType / secret(enc) / status(pending|enabled|disabled) / bindToken / phone / lastVerifiedWindow / lastVerifiedAt + 审计字段，retention `nop-auth-dao/.../entity/NopAuthMfaSetting.java`）、`NopAuthMfaRecoveryCode`（sid seq / userId / codeHash(BCrypt) / used / usedAt / expireAt + 审计字段）。关系 `NopAuthMfaSetting 1:N NopAuthMfaRecoveryCode`。
- W4/W5 可复用件均就位：`TOTPAuthenticator`（RFC 6238 + provisioning URI + 防重放窗口判定面，`nop-biz-auth-core/.../totp/`）、`MfaChallengeStore`/`SmsCodeStore`（`nop-biz-auth-core/.../mfa/store/`）、`IPasswordEncoder`（BCrypt）、`ISmsSender`、`AESTextCipher`。
- `NopAuthErrors`（`nop-auth-service/.../NopAuthErrors.java:73-109`）已含（W5）全部 11 个 MFA/SMS 错误码，含本 plan 需要的 `ERR_AUTH_MFA_ALREADY_ENABLED`/`ERR_AUTH_MFA_BIND_EXPIRED`/`ERR_AUTH_MFA_NOT_ENABLED`，及参数常量 `ARG_CHALLENGE_TOKEN`/`ARG_MFA_TYPE`/`ARG_LOGIN_TYPE`/`ARG_PHONE`。
- `mfaVerify`（W5，`LoginServiceImpl`）已实现 recovery/TOTP/SMS 三分支 + consume + `completeLogin`。本 plan 的 `bindMfa`/`confirmMfa` 产出的 setting 必须与 `mfaVerify`/`loginAsync` 消费口径一致（status==enabled 判定启用、secret 加密、recovery 码 BCrypt 哈希）。

## Goals

- 用户自助 MFA 管理：`bindMfa`（pending + bindToken；totp 返回 provisioning URI、sms 发验证码）/ `confirmMfa`（校验 + enabled + 生成恢复码 + 换绑原子）/ `unbindMfa`（验证当前因子 + 解绑 + 作废恢复码）/ `generateRecoveryCodes`（重置、作废旧码）/ `getMfaStatus`（不返回 secret）。
- 管理员 `resetUserMfa`（清除 setting + 恢复码，admin 权限）。
- 扫码 MFA 适配：`loginByScanAsync` 捕获 `ERR_AUTH_MFA_REQUIRED` → 返回 `ScanLoginResult{mfaRequired, challengeToken, mfaType, loginType}`；手机端 `mfaVerify` → `accessCode`（信道类出口，W5）；PC 轮询 `getLoginResultAsync` 换 token。
- 绑定状态机正确性：pending secret 仅在 `confirmMfa` 成功后生效（换绑原子性，期间旧 secret 仍可登录验证）；恢复码 BCrypt 加盐哈希；明文边界（secret 仅经 bindMfa provisioning URI 一次性返回，`getMfaStatus` 永不返回 secret）。
- `ScanLoginResult` 扩展向后兼容（既有调用方忽略新字段不受影响）。

## Non-Goals

- WebAuthn / 邮件验证码 / 操作级 MFA / 可信设备 / 角色级强制策略（二期，roadmap 既有边界）。
- 前端登录页对 `ERR_AUTH_MFA_REQUIRED` 的 UI 适配（业务层，W5 已显式 deferred）。
- `docs-for-ai/` MFA/credential 章节同步（roadmap 显式分配给 W7）。
- W8（DB store + 默认 db，独立 plan `2026-08-13-0900-1`）。

## Scope

### In Scope

- `nop-auth-service`：`NopAuthUserBizModel` 新增 5 个 MFA action（bindMfa/confirmMfa/unbindMfa/generateRecoveryCodes/getMfaStatus）；管理员 `resetUserMfa` 归属裁决（新建 `NopAuthUserMaintenanceBizModel` 或并入 `NopAuthUserBizModel` admin action——Decision）。
- `nop-ai-gateway`（ScanLoginResult 所在包）：`ScanLoginResult` 新增 `mfaRequired`/`challengeToken`/`mfaType`/`loginType` 可选字段。
- `nop-ai-gateway`：`ChannelLoginApiBizModel.loginByScanAsync` 捕获 `ERR_AUTH_MFA_REQUIRED` 并适配为 MFA 结果（具体捕获 `ERR_AUTH_MFA_REQUIRED` 错误码，不吞其他异常）。
- 测试：绑定状态机 E2E、解绑/换绑原子性、恢复码重置、管理员重置、扫码 MFA 全链、非 MFA 用户零回归。
- `ai-dev/design/nop-auth/01-architecture-baseline.md` §3.6（API 契约落地确认）。

### Out Of Scope

- `docs-for-ai/` 同步（W7）。
- 前端 UI（业务层）。
- W8 DB store。

## Execution Plan

### Phase 1 - 用户自助绑定/解绑 API（NopAuthUserBizModel）

Status: planned
Targets: `nop-auth/nop-auth-service/.../entity/NopAuthUserBizModel.java`、`ai-dev/design/nop-auth/01-architecture-baseline.md` §3.4 / §3.6

- Item Types: `Fix | Decision`

- [ ] **bindToken TTL 机制裁决（Decision）**：`NopAuthConfigs` 当前无 bind 过期配置、`NopAuthMfaSetting` 无独立 bindToken-expiry 列（核对 live `NopAuthConfigs.java` + `_NopAuthMfaSetting.java`）。裁决过期判定机制——(a) 复用 pending 记录 `updateTime`（= 发 bind 时刻）+ 新增配置 `nop.auth.mfa.bind-expire-seconds`（默认 300），或 (b) bindToken 内嵌时间戳。**约束：`confirmMfa` 必须能区分"未找到"与"已过期"两种 bindToken 失效**（均映射 `ERR_AUTH_MFA_BIND_EXPIRED`，但实现须显式判定，不得把过期当未找到静默处理）。裁决结论写入 plan/design
- [ ] `bindMfa(mfaType)`：status 检查（`enabled` → `ERR_AUTH_MFA_ALREADY_ENABLED`）；totp 分支生成 secret + provisioning URI（issuer 由 `nop.auth.mfa.totp-issuer` 注入）+ bindToken(按上条裁决带 TTL)；sms 分支向用户手机号（`NopAuthUser.phone`）发码（`SmsCodeStore.send`，key=`mfa:{userId}`）+ bindToken；save（status=pending，secret 落库 pending）。重复 `bindMfa` 覆盖旧 pending（旧 secret/bindToken 作废）。
- [ ] `confirmMfa({bindToken, code})`：按 bindToken 定位 pending 记录（未找到/已过期 → `ERR_AUTH_MFA_BIND_EXPIRED`，按裁决区分两种失效）；用 pending secret 校验 totp（`TOTPAuthenticator.verify`）或校验 sms（`SmsCodeStore.verify`）；成功 → status=enabled + 清 bindToken + 生成恢复码（作废旧码）+ save（**换绑原子性**：仅成功才把新 secret 置生效，期间旧 secret 仍可登录验证）。
- [ ] `unbindMfa({code})`：验证当前因子（totp/sms）通过才解绑；成功 → status=disabled + 删除全部恢复码。
- [ ] `generateRecoveryCodes()`：仅 enabled 状态可调；重置 = 作废旧码 + 生成新码（10 个一次性，BCrypt 加盐哈希存 `codeHash`，明文一次性返回给用户）。
- [ ] `getMfaStatus()`：返回 mfaType/status/phone（脱敏）——**永不返回 secret**。
- [ ] 明文边界：secret 仅经 `bindMfa` 的 provisioning URI 一次性返回；`confirmMfa`/`getMfaStatus`/`unbindMfa` 响应均不含 secret。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 绑定状态机 E2E：`bindMfa(totp)` → 拿 provisioning URI + bindToken → `confirmMfa` → status=enabled + 生成恢复码；之后密码登录被拦截（`ERR_AUTH_MFA_REQUIRED`）
- [ ] 换绑原子性：pending 期间旧 secret 仍可用于登录验证；`confirmMfa` 失败不改变已 enabled 的旧 secret
- [ ] sms 绑定：`bindMfa(sms)` 发码到用户手机 → `confirmMfa` 校验 `mfa:{userId}` key → enabled
- [ ] `unbindMfa`：需验证当前因子；成功后 status=disabled + 恢复码全部作废；之后登录不再被 MFA 拦截
- [ ] `generateRecoveryCodes`：重置后旧码全部作废（不可再用）、新码可用
- [ ] **明文边界**：`getMfaStatus` 响应断言无 secret；`confirmMfa`/`unbindMfa` 响应断言无 secret；secret 仅在 `bindMfa` provisioning URI 出现一次
- [ ] **跨组件契约 — secret 加密（关键）**：`bindMfa` 落库的 `setting.secret` 必须是**经 W5 `TOTPAuthenticator` 验证时所用的同一加解密通道加密后的密文**——即 W5 `TOTPAuthenticator.verify(setting.getSecret(),...)` 内部 `cipher.decrypt(...)` 能成功解密（live `TOTPAuthenticator.java:114` 自持 `AESTextCipher`）。provisioning URI 返回的是明文 base32 secret（一次性）。**Round-trip 测试**：`bindMfa` 产出的 setting.secret 能被既有 `TOTPAuthenticator.verify` 成功解密并校验通过（跨 W6-produce ↔ W5-consume 边界断言）
- [ ] **跨组件契约 — 恢复码 codeHash 编码（关键）**：`generateRecoveryCodes`/`confirmMfa` 产出的 `codeHash` 编码必须与 W5 消费方 `LoginServiceImpl.verifyRecoveryCode`（live `:549,563-566`，按首个 `:` 拆 salt/hash）**逐字兼容**。**Round-trip 测试**：W6 生成的恢复码能经既有 `verifyRecoveryCode` 路径成功验证+消费（跨组件边界断言，防止恢复码登录静默失效）
- [ ] **新功能测试**：`TestMfaBindStateMachine`（bind→confirm→enabled→登录拦截）、`TestMfaUnbind`（验证因子+作废恢复码+登录恢复）、`TestMfaRecoveryReset`（旧码作废+新码生效）、`TestMfaStatusNoSecret`（断言无 secret）
- [ ] **接线验证**：`confirmMfa` 调用 `TOTPAuthenticator.verify`（totp）/ `SmsCodeStore.verify`（sms）；`unbindMfa`/`generateRecoveryCodes` 调用恢复码 DAO 删除；恢复码经 `IPasswordEncoder`（BCrypt）哈希；MFA 实体经 `daoFor(NopAuthMfaSetting.class)`/`daoFor(NopAuthMfaRecoveryCode.class)` 访问（模式参照 `LoginServiceImpl:552,765`），TOTP/SMS/IPasswordEncoder 组件 `@Inject` 注入且与 W5 `LoginServiceImpl` 装配一致
- [ ] **无静默跳过**：已 enabled 时 `bindMfa` 抛 `ALREADY_ENABLED`；过期/未知 bindToken 抛 `BIND_EXPIRED`；未 enabled 调 `unbindMfa`/`generateRecoveryCodes` 抛 `NOT_ENABLED`
- [ ] 若该 Phase 改变 live baseline：`01-architecture-baseline.md` §3.4/§3.6 绑定状态机落地确认（本 Phase 末执行）；`docs-for-ai/` 同步属 W7
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 管理员重置 API（resetUserMfa）

Status: planned
Targets: 新建 `NopAuthUserMaintenanceBizModel` 或 `NopAuthUserBizModel` admin action（Decision）、action-auth 配置

- Item Types: `Fix | Decision`

- [ ] **归属裁决（Decision）**：`resetUserMfa` 放置——按 design §3.6 新建 `NopAuthUserMaintenanceBizModel`（admin 专用 BizModel），或并入 `NopAuthUserBizModel` 加 admin-only auth。**各分支后果（须在裁决时确认）**：(a) 新建 BizModel → NopIoC 不做注解扫描（AGENTS.md），**必须在 `_vfs/.../auth-service.beans.xml` 显式声明 `<bean>`**，否则 bean 不装配、`resetUserMfa` 运行时不可达（空壳）；(b) 并入既有 `NopAuthUserBizModel` → 无需新 bean 注册，仅 action-auth.xml。裁决结论写入 plan/design
- [ ] `resetUserMfa(userId)`：清除/禁用 `NopAuthMfaSetting`（status=disabled 或删除）+ 删除全部恢复码；admin 权限（action-auth.xml 角色限制）
- [ ] 审计：重置动作写入 `NopAuthOpLog`（复用既有审计机制）

Exit Criteria:

- [ ] `resetUserMfa` 清除 setting + 全部恢复码；admin 权限生效（action-auth 强制角色，非 admin 被拒）；action-auth.xml 文件路径与角色/scope 在裁决时明确（live `NopAuthConfigs.PATH_MAIN_ACTION_AUTH`）
- [ ] 重置后该用户登录（`loginAsync`/`createSessionForUserAsync`）**不再被 MFA 拦截**（setting status != enabled）
- [ ] **新功能测试**：`TestAdminResetUserMfa`（重置→setting 清除→恢复码清空→登录无拦截）；非 admin 调用被拒
- [ ] **接线验证（Rule #23，防空壳）**：若选(a)新建 BizModel 分支——断言该 bean 在运行时被实例化（`resetUserMfa` 经 GraphQL/REST 实际可达，非仅类型存在）；重置后 `loginAsync` 对该用户不创建 challenge（断言 `MfaChallengeStore.create` 未被调用）
- [ ] **无静默跳过**：对不存在用户重置——显式裁决（抛错或显式 no-op 并记录，不静默成功）
- [ ] 若该 Phase 改变 live baseline：design §3.6 admin 契约落地确认；归属裁决结论记录
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 扫码登录 MFA 适配（ScanLoginResult + loginByScanAsync）

Status: planned
Targets: `ScanLoginResult.java`（nop-ai-gateway）、`ChannelLoginApiBizModel.loginByScanAsync`

- Item Types: `Fix | Decision`

- [ ] `ScanLoginResult` 新增可选字段 `mfaRequired`(boolean) + `challengeToken` + `mfaType` + `loginType`（非 MFA 时 mfaRequired=false，其余 null）
- [ ] **错误码识别裁决（Decision）**：`nop-ai-gateway` 的 pom **不依赖 `nop-auth-service`**（核对 live `nop-ai-gateway/pom.xml:54-61`，仅依赖 nop-biz-auth-core + nop-auth-api），故无法 import `NopAuthErrors.ERR_AUTH_MFA_REQUIRED` 常量。裁决识别方式——(a) 按 `NopException` 携带的 ErrorCode 值字符串匹配（`nop.err.auth.mfa-required`，核对 live `NopAuthErrors.java:79`），或 (b) 将该 ErrorCode + ARG_* 常量上提到 `nop-auth-api`/`nop-biz-auth-core`（跨模块公共 API 变更，需独立 migration note）。裁定一种并记录；优先 (a) 缩小跨模块变更面
- [ ] `loginByScanAsync` 包裹 `createSessionForUserAsync` 调用，按上条裁决识别并捕获 MFA 必要异常（**不吞其他异常**）→ 构建 `ScanLoginResult{mfaRequired=true, challengeToken, mfaType, loginType}` 返回（challenge 已由上游 createSessionForUserAsync 创建存储，本处仅传递）
- [ ] **同步抛出契约（关键约束）**：`createSessionForUserAsync` 在 `LoginServiceImpl.java:355` **同步抛出** `ERR_AUTH_MFA_REQUIRED`（裸 `throw`，非 `FutureHelper.reject` 失败 future；对比 `loginAsync:324` 用 reject）。捕获机制必须在调用表达式处**运行时确实拦截到该同步异常**——`try/catch` 包裹调用可拦截；**仅对返回的 `CompletionStage` 链 `.exceptionally()`/`.handle()` 不能拦截同步抛出**（会变成静默 no-op，违反 Rule #24）。实现须验证所选机制实际拦截
- [ ] 非 MFA 异常仍原样冒泡（捕获范围限定 MFA 必要错误码）

Exit Criteria:

- [ ] 扫码 MFA E2E：扫码确认 → `loginByScanAsync` 返回 `ScanLoginResult{mfaRequired=true, challengeToken, mfaType, loginType}` → 手机 `mfaVerify`（action 名；Java 方法 `mfaVerifyAsync`，`LoginApiBizModel.java:122`）(challengeToken) → `LoginResult{accessCode}`（信道类出口）→ PC 轮询 `getLoginResultAsync(accessCode)` → token
- [ ] **运行时拦截断言（关键，防静默 no-op）**：E2E 断言 `ScanLoginResult.mfaRequired=true` 且 challengeToken/mfaType/loginType 字段被填充（证明捕获机制在运行时确实拦截了同步抛出的 MFA 异常，而非异常原样冒泡）；并断言非 `ERR_AUTH_MFA_REQUIRED` 的异常继续冒泡（未被吞）
- [ ] **扫码零回归**：非 MFA 用户扫码 → `ScanLoginResult{accessCode, mfaRequired=false}`，行为与改造前一致
- [ ] **接线验证**：`loginByScanAsync` 捕获 MFA 必要异常且其 `.param(challengeToken/mfaType/loginType)` 流入 `ScanLoginResult` 字段（断言）；非 MFA 路径调用 `createSessionForUserAsync` 后正常 buildResult
- [ ] **无静默跳过**：非 MFA 错误码的异常继续冒泡（断言其他异常不被吞）；捕获仅限 MFA 必要错误码
- [ ] **新功能测试**：`TestScanLoginMfa`（MFA 全链 + 非 MFA 零回归 + 其他异常仍冒泡）
- [ ] **向后兼容**：`ScanLoginResult` 新增字段为可选；既有调用方忽略新字段不受影响（序列化兼容断言）
- [ ] 若该 Phase 改变 live baseline：design §3.2 扫码时序 / §五 跨模块契约确认（`ScanLoginResult` 扩展为跨模块公共 API 变更，本 plan 即 plan-first 产物）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端 + 回归

Status: planned
Targets: `nop-auth-service` / `nop-ai-gateway` 测试

- Item Types: `Proof`

- [ ] E2E：bind totp → 密码登录 → `ERR_AUTH_MFA_REQUIRED` → `mfaVerify(totp)` → accessToken
- [ ] E2E：bind sms → 密码登录 → `ERR_AUTH_MFA_REQUIRED` → `sendMfaCode` → `mfaVerify(sms)` → accessToken
- [ ] E2E：恢复码登录 → status=disabled（强制重绑）
- [ ] E2E：扫码 MFA 全链（Phase 3）
- [ ] E2E：管理员 resetUserMfa → 用户登录无拦截
- [ ] 零回归：非 MFA 用户（密码/SSO/扫码）登录行为与改造前逐项一致

Exit Criteria:

- [ ] 上述 E2E/场景测试全部通过，测试名与覆盖行为在 plan 中可对照
- [ ] **端到端验证（Rule #22）**：从用户入口点（bindMfa）经 login 拦截 → mfaVerify → token/accessCode 出口的完整路径跑通；扫码路径从 loginByScan → mfaVerify → accessCode → getLoginResultAsync 完整跑通
- [ ] **接线验证（Rule #23）**：E2E 中断言 W4/W5 组件（`TOTPAuthenticator`/`SmsCodeStore`/`MfaChallengeStore`/`IPasswordEncoder`）在绑定与验证链路上确实被调用
- [ ] **无静默跳过（Rule #24）**：负向路径（已 enabled/过期 bindToken/未 enabled 解绑/非 admin 重置/非 MFA 异常冒泡）均有显式失败用例
- [ ] 若该 Phase 改变 live baseline：`No owner-doc update required`（`docs-for-ai/` 同步属 W7）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本 plan 改动跨模块公共 API（`ScanLoginResult` 扩展）——Protected Area，plan-first。本 plan 即为 plan-first 产物；`ScanLoginResult` 新增字段为可选，向后兼容（既有调用方不受影响）。

- [ ] 四个 Phase 的 Exit Criteria 全部勾选（含绑定状态机、换绑原子性、明文边界、admin 重置、扫码 MFA 全链、零回归）
- [ ] MFA 端到端可用：用户可 bind/unbind、管理员可 reset、扫码登录可走通 MFA
- [ ] `ScanLoginResult` 扩展向后兼容（既有调用方忽略新字段不受影响）
- [ ] 明文边界成立：secret 仅经 bindMfa provisioning URI 一次性返回；`getMfaStatus`/`confirmMfa`/`unbindMfa` 响应无 secret
- [ ] 必要 focused verification（E2E 全链 + 回归）已完成
- [ ] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs 已裁定：design §3.4/§3.6 落地确认；`docs-for-ai/` 同步属 W7（显式 scope move）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 `loginByScanAsync` 在运行时确实捕获 `ERR_AUTH_MFA_REQUIRED` 并产出 `ScanLoginResult{mfaRequired}`（端到端连通）；绑定/验证链路 W4/W5 组件运行时被调用；无空方法体/静默跳过/no-op
- [ ] `./mvnw compile -pl nop-auth,nop-ai/nop-ai-gateway -am`
- [ ] `./mvnw test -pl nop-auth,nop-ai/nop-ai-gateway -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 前端登录页 MFA UI 适配

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 前端对 `ERR_AUTH_MFA_REQUIRED` 的第二因子输入 UI 属业务层，roadmap 与 W5 均显式 deferred。后端契约（errorParams 携带 challengeToken/mfaType/loginType）已就绪，前端可独立适配。
- Successor Required: no（业务层）

### 可信设备 / "记住此设备"

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: vision §三 #8 + design 收敛路径明确为二期。
- Successor Required: no（二期）

## Non-Blocking Follow-ups

- 角色级 MFA 强制策略引擎（二期，vision §三 #8）
- 操作级 MFA（敏感操作二次验证，二期）
- 绑定流程的 provisioning URI 二维码生成（前端职责）

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<待 closure audit 填写>>
- Evidence: <<待 closure audit 填写>>

Follow-up:

- <<待 closure 填写>>
