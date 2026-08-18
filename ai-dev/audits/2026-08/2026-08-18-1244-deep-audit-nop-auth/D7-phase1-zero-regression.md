# D7 审计报告：登录级链路一期零回归（MFA 二期安全审计）

> Dimension: D7 登录级链路一期零回归
> Executor: fresh independent adversarial auditor (task D7, separate session)
> Date: 2026-08-18
> Charter: `ai-dev/audits/2026-08/2026-08-18-1244-deep-audit-nop-auth/audit-charter.md` §二 D7
> 基准: `ai-dev/design/nop-auth/02-mfa-phase2-design.md` §二 一期契约兼容性矩阵（六行）+ §3.5/§4.5/§5.5/§6.5
> 方法约束: RESEARCH + REPORT ONLY。零代码修改；未运行 maven（静态代码路径核验 + git 只读核验；live 全量重跑归编排 session / 已有 W15 closure 记录 355 tests 0 failures）。

---

## 0. Findings 总表

| ID | Severity | file:line anchor | description | suggested fix direction |
|---|---|---|---|---|
| F1 | P3 (designed deviation, 非回归) | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:799-803` | `sendMfaCode` 对 totp 类型 challenge 的行为相对一期发生变化：一期隐式假设 SMS（不检查 challenge.mfaType，totp challenge 也会发短信）；W14 重构为按 mfaType 分派，totp/webauthn 显式抛 `ERR_AUTH_MFA_CODE_UNSUPPORTED`。这是设计 §5.3.0 #8 的**显式裁定变更**（修复一期隐式缺陷），且一期测试从未断言旧行为（`testPasswordLoginMfaSmsFullChain` 仅覆盖 sms challenge）。sms 分支行为逐字节保留（:804-824）。 | 无需修复。登记为设计内偏离；消费方文档（owner doc）已覆盖。watch-only。 |
| F2 | P3 (test-infra, 断言无影响) | `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestMfaLoginE2E.java:134-144`（同形态见 TestScanLoginMfa:127、TestMfaUserSelfService:127） | 92c8dc392（W13 期间 flake 根治）从三个一期 plain E2E 移除 `nop.orm.enable-tenant-by-default=true` 全局 toggle（租户配置跨测试类泄漏修复）。属测试**环境 setup** 变更非断言变更；commit 记录实测全绿（forkCount=1 ×2 + 1C ×2 exit 0）。 | 无需修复。已由 bug note + commit 记录闭环。 |
| F3 | P3 (test-infra) | `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/mfa/store/FakeNosqlService.java:184-196` | W12 为 Redis 派生票键（SETNX+PX）在 FakeNosqlService 实现了此前 `throw UnsupportedOperationException` 的 `putIfAbsentExAsync`。测试基建扩展，无断言修改。 | 无需修复。 |
| F4 | P3 (informational) | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:1012-1013` | `checkMfaRequired` 签名增加第三参 `requestHeaders`（W15）。设计 §6.1 结论 7 已前瞻声明使 §4.3 "一期方法签名不变"注记自 W15 起失效；protected 单模块内变更，全部调用点（loginAsync:380 真实 headers / createSessionForUserAsync:417 null）在模块内同步。行为面：无 device-id 用户该分支短路不查库（:1044-1046），未启用用户在 :1033 早退不可达豁免分支。 | 无需修复。签名变更已按设计手续（§6.5 兼容性声明）登记。 |

**无 P0/P1/P2 finding。** 六行矩阵全 PASS；一期断言零修改成立（G2）；G3 八项全 PASS。

---

## 1. G1 矩阵六行逐行核验

（行 1/4 与 D2 交叉、行 2/3 与 D4 交叉、行 5 与 D1 交叉——本节从**一期行为保持**角度独立复核，锚点为 live 核对。）

### Row 1 两阶段 challenge — **PASS**

- 证据链：`LoginServiceImpl.loginAsync:302` → 第一因子通过 → `checkMfaRequired(user, loginType, headers):380` → challenge 决策 → `ERR_AUTH_MFA_REQUIRED` + challengeToken/mfaType/loginType（:386-391）。challenge 创建经 `MfaChallengeHelper.createLoginChallenge:1052-1053`。
- `MfaChallengeHelper.createLoginChallenge`（`mfa/MfaChallengeHelper.java:51-60`）：**非 webauthn 类型走一期五参 `store.create(userId, mfaType, loginType, tenantId, phone)` 逐字节等价**；webauthn 才增量 scene/payload（W14 触点①）。
- 一期分支原位原序保留（`checkMfaRequired:1012-1054`）：全局开关 :1014 → store 装配 :1016 → 【W13 策略评估增量 :1018-1031，插入位=store null 之后、setting 装载之前，无策略行（maxLevel=0）不可达第三态】→ setting null/非 enabled :1033 → mfaType 空 :1036 → 因子等同 :1039 → 【W15 豁免分支 :1041-1049，插入位=因子等同之后、challenge 创建之前；无 headers/无 device-id 短路不查库】→ challenge 创建 :1052。与设计 §4.3 伪代码"原样"标注逐行对齐。
- E2E 钉定：`TestMfaLoginE2E.testPasswordLoginMfaTotpFullChain:181-215`（MFA_REQUIRED + challengeToken + peek 存在 + 消费一次性）、`testPasswordLoginMfaSmsFullChain:259-292`、`testChannelLoginMfaFullChainYieldsAccessCode:333-368` 均为 live 一期断言（git 核对未被 W12-W15 触碰，见 G2）。

### Row 2 ERR_AUTH_MFA_REQUIRED 异常表达 — **PASS**

- `NopAuthErrors.java:88-89`：`nop.err.auth.mfa-required`，argNames `ARG_CHALLENGE_TOKEN/ARG_MFA_TYPE/ARG_LOGIN_TYPE`（:38-41）——一期三元组 errorParams 契约不变。
- W12 新码独立：`ERR_AUTH_OPERATION_MFA_REQUIRED`（:99-100，`nop.err.auth.operation-mfa-required`，args challengeToken/mfaType/**operation**）——编码与参数集均与一期区分（前端可区分登录期/会话期弹窗），无混用。
- 抛出点核对：登录级仅 `loginAsync:386` 与 `createSessionForUserAsync:423`（+OAuth 同构副本 `MfaLoginPolicyServiceImpl`，W13 新入口非一期面）；操作级抛出在 `OperationMfaCheckerImpl`（scene=operation，独立链路）。
- E2E 钉定：`TestMfaLoginE2E:189-194/267-272`（errorParams 断言）、`TestScanLoginMfa`（扫码捕获路径）；W14 增补断言 `TestMfaConfigAndErrors.webauthnConfigDefaultsUnsetAndNewErrorCodesDistinct` 反向钉定新码不与一期编码冲突。

### Row 3 completeLogin 分界 — **PASS**

- 五参重载委托六参（`LoginServiceImpl:461-465`，restricted=false）——一期三处调用点（loginAsync:394 `true,true` / createSessionForUserAsync:431 `false,false` / mfaVerify 经 completeMfaLogin:732 `false,false`）参数语义逐字节保持；resetFailCount/notifyHook 差异裁决照旧（javadoc :446-459）。
- 密码类 mfaVerify → accessToken；信道类（loginType≥20 或 4）→ accessCode 经 `ATTR_MFA_ACCESS_CODE`（completeMfaLogin:730-739；`LoginApiBizModel.mfaVerifyAsync:197-215` 消费，accessCode 分支不签发 accessToken）。E2E 断言 `TestMfaLoginE2E:204-205/286-287/363-364`（accessToken/accessCode 互斥）live。
- `getLoginResultAsync` 兑换路径不变：`LoginApiBizModel:135-139`（parseAccessCode → getUserContextAsync → buildLoginResult），无 MFA 门禁介入。
- W15 `trustedDeviceRegistered` 回填（:200/211-213）为可选增量，仅在密码类路径 + 用户显式勾选 rememberDevice 时出现（`LoginResult` 可选 Boolean），老客户端零感知。

### Row 4 store 装配 — **PASS**

- `MfaStoreProvider.java:37-91`：第三组 map `emailCodeStores`（:47/:55-56/:60）与 challenge/sms 同 `select()` fail-closed 语义；**本类零 `INosqlService` 类型引用**（lessons 15 类加载安全不变式保持，:29-31 javadoc 显式声明）。
- `auth-service.beans.xml`：`nopEmailCodeStore_local/db`（:71-78，`autowire-candidate=false` + `lazy-init`）+ `nopEmailCodeStore_redis`（:79-85，`ioc:condition` = `if-property store-type=redis` + `on-class io.nop.nosql.core.INosqlService`）——与一期 `nopSmsCodeStore_*`（:50-64）/`nopMfaChallengeStore_*`（:34-49）逐属性同构；collect-beans 装配（:95-97，`name-prefix` + `as-map` + `ioc:ignore-depends`）同形；config bean `nopEmailCodeConfig`（:67）不落 collect 前缀（W8 命名教训复刻）；工厂 bean `nopActiveEmailCodeStore`（:106-107）镜像 `nopActiveSmsCodeStore`。
- 一期 challenge/sms store bean 定义（:34-64）git 核对未被 W12-W15 改动语义（W12 仅加 opTicketExpireSeconds 配置属性 :27，为数据结构增量）。
- 测试钉定：`TestMfaStoreProvider`（含 `testEmailStoresIndependentlySelectable`——email 侧未注册显式失败不静默回退 sms）+ `TestMfaStoreWiringDb`（容器级装配）。

### Row 5 明文边界（一期面不变，交叉核对 D1 主责）— **PASS**

- secret 加密：绑定侧 `NopAuthUserBizModel.bindTotp:309-314`（`totpAuthenticator.getCipher().encrypt` 后入库）；E2E 断言 `TestMfaUserSelfService:654`（"persisted secret must be encrypted, not the plaintext base32 in the URI"）为 live 一期断言。验证侧 `MfaFactorVerifier.verify:150` 经 `TOTPAuthenticator.verify` 内部解密（`TestMfaUserSelfService:229-234` W6-produce↔W5-consume round-trip 断言钉定）。
- 恢复码 BCrypt（salt:hash）：生成 `NopAuthUserBizModel.regenerateRecoveryCodes:1303-1311`（generateSalt + encodePassword）；消费 `LoginServiceImpl.verifyRecoveryCode:686-707`（passwordMatches）；明文一次性返回、codeHash 入库——一期语义零变更。
- 新因子未放松边界：webauthn publicKey 为公开材料按 masked 不展示（D1 主责面）；email/sms 码短 TTL 明文沿一期 W8 裁决。

### Row 6 一期零回归 — **PASS**

- 未启用 MFA 用户：`checkMfaRequired:1033-1034` 早退 return null（setting null/非 enabled）→ 直接 completeLogin；W13 策略分支要求 `policy.getMaxLevel() > 0`（:1025）才可达——**无策略行部署（evaluator 返回 NONE/maxLevel=0）第三态结构性不可达**；W15 豁免分支位于 setting 检查之后，未启用用户不可达。
- login/refresh/logout 三面：`loginAsync` 公开入口（LoginApiBizModel:119-123）、`refreshTokenAsync:150-157`（parseRefreshToken → getUserContextAsync，无 MFA 门禁）、`logoutAsync:125-130`——签名/权限（publicAccess）/路径均不变。
- 双零介入：`nop.auth.operation-mfa.enabled` 缺省 false（NopAuthConfigs:129-131）+ 无策略行 = 一期全部行为。
- E2E 钉定：`testZeroRegressionMfaDisabledUser:405-423`（全局开关关）、`testZeroRegressionNoMfaSettingUser:426-434`（无 setting）、`testSmsCodeLoginWithoutMfaUserSucceeds:245-254`——均 live 一期断言。

---

## 2. G2 一期 MFA 测试套件 git 层面核对

**一期测试文件判定口径**：W12 plan `2026-08-16-2321-2`（2026-08-16 23:21 起草，首个 impl commit 8b5b45223/dc98045da/daec06190 均为 2026-08-17）；一期测试 = 该时点前已存在的 MFA 家族测试文件。

### Per-file 表

| file（nop-auth-service test tree） | created (commit/date) | modified since W12-start | nature of modification | verdict |
|---|---|---|---|---|
| `TestMfaLoginE2E.java` | 18c0ac7f4 / 08-12 | **yes** ×2 | daec06190(W12)：+6 行纯增量 wiring（MfaFactorVerifier 字段装配，0 删除断言）；92c8dc392：移除 tenant-by-default toggle（环境 setup，F2） | **PASS**（additive + infra；断言零触碰） |
| `TestScanLoginMfa.java` | f855297de / 08-13 | **yes** ×2 | 同上形态（+6 wiring / toggle 移除） | **PASS** |
| `TestMfaUserSelfService.java` | f855297de / 08-13 | **yes** ×2 | 同上形态（+8 wiring 含 userBizModel 注入 / toggle 移除） | **PASS** |
| `TestChannelScanBindLoginE2E.java` | 522440323 / 08-09 | no | — | **PASS** |
| `TestMfaConfigAndErrors.java` | 18c0ac7f4 / 08-12 | **yes** ×1 | 6d978da56(W14)：追加新测试方法 `webauthnConfigDefaultsUnsetAndNewErrorCodesDistinct`（+20 行纯新增） | **PASS**（additive） |
| `TestNopAuthMfaEntityRoundTrip.java` | b9c60d57f / 08-12 | **yes** ×1 | 6d978da56(W14)：追加新测试方法 `testWebauthnCredentialRoundTrip`（+46 行纯新增） | **PASS**（additive） |
| `AuthTestHelper.java` | 674dad453 / 2024 | no | — | **PASS** |
| `mfa/TestMfaStoreProvider.java` | b9c60d57f / 08-12 | **yes** ×1 | 779d260e4(W15)：追加 email 断言行 + 私有 helper `providerWith` 增第三参（签名适配 2 行，既有 challenge/sms 断言全部保留）+ 新方法 `testEmailStoresIndependentlySelectable` | **PASS**（additive + helper 签名适配） |
| `mfa/TestMfaStoreWiringDb.java` | f85f19d1e / 08-13 | **yes** ×1 | 779d260e4(W15)：既有方法内追加 email 断言（插入式增行，无删除） | **PASS**（additive） |
| `mfa/store/TestDbMfaChallengeStore.java` | f85f19d1e / 08-13 | **yes** ×1 | dc98045da(W12)：追加 7 个新测试方法（场景化/markVerified/票窗口，+127 行） | **PASS**（additive） |
| `mfa/store/TestDbSmsCodeStore.java` | f85f19d1e / 08-13 | no | — | **PASS** |
| `mfa/store/TestRedisStoreWiring.java` | b9c60d57f / 08-12 | **yes** ×1 | dc98045da(W12)：追加 7 个新测试方法（+105 行） | **PASS**（additive） |
| `mfa/store/TestDbMfaChallengeStoreEntityRoundTrip.java` | f85f19d1e / 08-13 | no | — | **PASS** |
| `mfa/store/FakeNosqlService.java`（基建） | b9c60d57f / 08-12 | **yes** ×1 | dc98045da(W12)：实现 `putIfAbsentExAsync`（原 throw UnsupportedOperationException）——测试基建扩展（F3） | **PASS**（infra） |
| （非 MFA 家族旁证）`TestBeanLoader`/`TestIntrospectionQuery`/`TestSiteMapApi`/`TestTenant` | 早期 | yes | 6d2e37adb/92c8dc392：仅注解级 infra（`initDatabaseSchema=TRUE` / `@AfterAll` 恢复），无断言变更 | **PASS**（infra） |

### 结论

**零修改成立（严格口径：一期断言行零删除/零改写/零弱化）。** 全部 8 个触碰点分三类：(a) 纯新增文件（W12-W15 各自新测试类，合规）；(b) 纯增量追加（新测试方法/新断言行/等价重构 wiring）；(c) 测试基建（FakeNosqlService 方法实现、tenant toggle 移除、注解补充）——均有 commit 级验证记录，且 (c) 类不触碰任何一期断言。W12/W13/W14/W15 四个 commit message 各自声明的"既有断言零修改"与 git diff 实证一致。

---

## 3. G3 一期交付面回归（静态代码路径核验，未运行 mvn）

| # | item | verdict | evidence |
|---|---|---|---|
| 1 | 两阶段登录 E2E code path（SMS/TOTP）intact | **PASS** | `verifySecondFactorAndComplete:600-640`：未知 mfaType fail-closed 作废 challenge（:606-612，一期兜底保留并扩展白名单）；TOTP/SMS 经 `MfaFactorVerifier.verify` 三参重载（:122-124 委托五参，老调用点零感知）；失败 `incrFailCountOrDiscard:619` + MFA_FAIL；成功 consume:623 → completeMfaLogin。E2E：`testPasswordLoginMfaTotpFullChain:181` / `testPasswordLoginMfaSmsFullChain:259` |
| 2 | loginType=5 短信登录路径不变 | **PASS** | `loginAsync:326-337`：`smsCodeStore.verify(SMS_KEY_LOGIN+phone)`、EXPIRED/INVALID→专属错误码且 `smsCodeFail` 不进用户锁账号计数（:308/352-357）；因子等同 :1039。E2E：`testSmsCodeLoginFullChainWithFactorEquivalence:220` / `testSmsCodeLoginWithoutMfaUserSucceeds:245` |
| 3 | 恢复码登录路径不变 | **PASS** | `verifyRecoveryCodeAndComplete:650-668`：USED→MFA_FAIL 不计数、INVALID→计数、VALID→consume+status=disabled 强制重绑+审计；BCrypt 遍历比对 :686-707。E2E：`testRecoveryCodeLoginDisablesSetting:297` |
| 4 | 因子等同分支（PHONE_SMS + mfaType==SMS 不重复验证） | **PASS** | `checkMfaRequired:1039-1040` 原位原序；且先于 W15 豁免分支（§6.6 裁定 7）。E2E 断言见 item 2 |
| 5 | 失败计数分界（二因子失败不锁账号）仍成立 | **PASS** | `completeMfaLogin:732` → `completeLogin(..., resetFailCount=false, notifyHook=false)`；二因子失败仅 challenge 维度 `incrFailCount`（:712-720）。E2E：`testSecondFactorFailCountBoundaryDiscardsChallengeNoUserLock:373-400`（max-attempts 后 challenge 作废 + 用户级 failCount==0 断言 live） |
| 6 | 未启用 MFA 用户零感知（checkMfaRequired 早退） | **PASS** | `checkMfaRequired:1033-1034`；策略第三态需 maxLevel>0（:1025）无策略行不可达；豁免分支在 setting 检查后不可达。E2E：`testZeroRegressionMfaDisabledUser:405` / `testZeroRegressionNoMfaSettingUser:426` |
| 7 | 扫码 MFA 链路：ScanLoginResult 可选字段向后兼容 | **PASS** | `nop-ai/nop-ai-gateway/.../ScanLoginResult.java:27-51`：一期字段（accessCode/mfaRequired/challengeToken/mfaType/loginType）原样；W13 `mfaRestricted`（primitive boolean 缺省 false）为尾部追加可选增量（W6 先例形态）。E2E：`TestScanLoginMfa`（git 零断言触碰） |
| 8 | W6 closure 补的 SMS 全链用例仍在 | **PASS** | `TestMfaLoginE2E.testSmsCodeLoginFullChainWithFactorEquivalence:220-242`（sendSmsCode→ISmsSender→loginType=5 登录→等同等价直接 accessToken）+ `testPasswordLoginMfaSmsFullChain:259-292`（密码→MFA_REQUIRED(sms)→sendMfaCode→mfaVerify→accessToken→consume）live 且断言未被触碰 |

附加项（charter §D7 附加回归）：

- **既有 MFA 套件断言零修改（git diff）**：见 §2，成立。
- **双零介入**（operation-mfa.enabled=false + 无策略行）：NopAuthConfigs:130-131 缺省 false；`RoleMfaPolicy.NONE`（evaluator null 或无行）→ maxLevel=0 → 第三态不可达；`OperationMfaCheckerImpl` 受限分支虽不受 enabled 门控，但受限会话本身仅由策略第三态产生——无策略行时零介入。成立。
- **C1b 容器级元数据断言仍绿（nop-credential 侧）**：`TestCredentialMfaRequiredAnnotations.java` 存在（创建于 d71b5b87c 2026-08-17，此后**零修改**——W14/W15/W16 commit 未触碰）。静态存在性与零触碰成立；**live 重跑未执行**（本审计纪律禁止 mvn），测试绿性沿用 W15 closure 记录（全模块 355 tests 0 failures）+ 编排 session 统一重跑。

---

## 4. G4 测试基线声明（live glob vs plan Current Baseline）

Plan 基准：`ai-dev/plans/2026-08-18-0904-1-mfa-phase2-security-audit-a2.md` Current Baseline（2026-08-18 live 核实版）。

### E2E 家族（plan 13 个）

| plan baseline | live | 状态 |
|---|---|---|
| TestMfaLoginE2E | ✅ | 一期（08-12） |
| TestMfaUserSelfService | ✅ | 一期（08-13） |
| TestScanLoginMfa | ✅ | 一期（08-13） |
| TestOperationMfaE2E | ✅ | W12 新增 |
| TestMfaRestrictedLoginE2E / TestMfaRestrictedSessionE2E / TestMfaRestrictedDaoCache / TestRoleMfaPolicy | ✅×4 | W13 新增 |
| TestWebAuthnMfaE2E / TestWebAuthnMfaAdvancedE2E | ✅×2 | W14 新增 |
| TestEmailMfaE2E / TestChannelProofEmailE2E / TestTrustedDeviceE2E | ✅×3 | W15 新增 |

### 组件/store 家族（plan 14 个）

| plan baseline | live | 状态 |
|---|---|---|
| TestMfaFactorVerifier | ✅ | W12 |
| TestMfaConfigAndErrors / TestMfaStoreProvider / TestMfaStoreWiringDb | ✅×3 | 一期（W14/W15 增量） |
| TestTrustedDeviceSupport / TestWebAuthnAuthenticator | ✅×2 | W15 / W14 |
| store/: TestDbMfaChallengeStore / TestDbSmsCodeStore / TestDbMfaChallengeStoreEntityRoundTrip / TestRedisStoreWiring | ✅×4 | 一期（W12 对前二者中 challenge 家族增量） |
| store/: TestMfaChallengeJsonCompat | ✅ | W12 |
| store/: TestDbEmailCodeStore / TestRedisEmailCodeStore / TestEmailCodeEntrySerialization | ✅×3 | W15 |

### meta 侧（plan 2 个）

`TestNopAuthMfaSettingXmeta`（nop-auth-meta）✅；`TestNopAuthMfaEntityRoundTrip` ✅（一期，W14 增量）。

### 差异声明

- **Removals vs plan baseline：无。**
- **Additions vs plan baseline：无**（plan baseline 为 2026-08-18 W15 后核实版，与 live 一致）。
- 旁证文件（不在 plan MFA 家族清单、live 存在）：`TestChannelScanBindLoginE2E`（2026-08-09 一期前通道族，零触碰）、mock 家族 `CapturingSmsSender`（W13）/`CapturingEmailSender`（W15）/`WebAuthnTestClient`（W14）/`FakeNosqlService`（一期基建，W12 扩展）、`app-test.beans.xml`（W15 测试 wiring +4 行）。
- 测试计数：本审计未重跑（纪律约束）；W15 closure 记录全模块 355 tests 0 failures 作为基线沿用，编排 session 负责live 复核。

---

## 5. 覆盖率声明

| 探查面 | 覆盖方式 | 覆盖率 |
|---|---|---|
| G1 矩阵六行 | live 源码逐行核验（LoginServiceImpl 全量 MFA 段 / MfaStoreProvider / MfaChallengeHelper / MfaFactorVerifier / beans.xml / NopAuthErrors / NopAuthConfigs / LoginResult / ScanLoginResult）+ E2E 断言 live 抽查 | 6/6 行，100% |
| G2 一期测试 git 核对 | `git log --diff-filter=A`（创建）+ `--since=2026-08-16 23:00`（修改）+ 逐 commit `git show` diff 逐 hunk 分类；辅以全 test 树 since-W12 文件清单反查漏 | 一期 MFA 家族 14 文件全核 + 4 个非 MFA 旁证文件；100% |
| G3 静态路径 | 8/8 item + 3 附加项（零修改/双零介入/C1b 静态存在性） | 100%（静态口径） |
| G4 基线对比 | live glob vs plan Current Baseline（E2E 13 + 组件/store 14 + meta 2） | 100% |
| **未覆盖（显式声明）** | (a) mvn live 全量重跑（本子 agent 纪律禁止；归编排 session——charter 回归基准 §三.5 的"live 重跑核实"由其执行）；(b) nop-auth-sso OAuth 三分支运行时行为（D4 主责，本审计仅核 `MfaLoginPolicyServiceImpl` 存在性与 checkMfaForUserName 不加豁免分支的裁定登记）；(c) WebAuthn/Email/可信设备/操作级新链路自身的安全质量（D1/D2/D5/D6 主责，本审计仅核其**不回归**一期面） | — |

**总裁决：D7 一期零回归 PASS。** 六行矩阵全 PASS，G2 零断言修改成立，G3 八项全 PASS，G4 无基线漂移。4 条 P3 finding 均为设计内偏离/测试基建/信息登记，无阻断项。
