# 一期功能零回归复核报告（V3，fresh 子 agent）

> Reviewer/Agent: closure-a3/phase1-regression-review（独立复核子 agent，task 独立于 executor） | Date: 2026-08-19 | Mode: read-only live + git history verification
>
> 复核对象：`ai-dev/design/nop-credential/02-phase2-design.md` §二（凭证五行矩阵 + 两附加锚点）与 `ai-dev/design/nop-auth/02-mfa-phase2-design.md` §二（MFA 六行矩阵）承诺的"一期契约零回归"，逐行对照 live 代码（HEAD b7c81136c）与 git 历史（二期窗口 = 2026-08-14 起）。未运行 maven；全部结论基于源码静态核验 + git diff 证据。

## 一、凭证五行矩阵逐行结论 (R1-R5 + 2 附加锚点)

| 行 | live file:line 证据 | 结论 |
|---|---|---|
| **R1 `cv1:` 密文格式** | `CredentialCipher.java:34`（CV1_MARKER）、`:93-101`（encrypt 输出 `cv1:{keyId}:v1:{base64data}`）、`:121-161`（decrypt：cv1 前缀检查 → keyId 解析/路由 → 内层 `v1:` 强制 `:147-150` → 未知 keyId fail-closed `:152-156`）。测试存在且断言 round-trip：`nop-credential/nop-credential-service/src/test/java/io/nop/credential/crypto/TestCredentialCipher.java:107-117`（roundTripWithExplicitKeyId）、`:123-133`（active key）、`:159-177`（多密钥轮换后旧密文可解）、`:199-267`（fail-closed 族）。注：`:147-150` 内层 `v1:` 强制为二期 C1a D5-05 加固（A1-audit successor，commit 39a48e9c6，设计/裁定记录在案）——cv1 包装格式本身零变更，属 fail-closed 收紧非格式变更，既有测试零修改仍全绿（commit 载明 service 196 全绿） | **PASS** |
| **R2 明文边界（唯一明文出口）** | `CredentialProviderImpl.java:47-49`（类 javadoc：平台内唯一凭证解密点）；解密唯一下沉点 `decryptToData :624-637`，仅被 SPI 面 `getCredential :122-138` / `getCredentialData :140-144`、管理面 `mask :164-194` / `testCredential :146-161` 与引擎内部通道 `:261-265` 使用；引擎类不直接持有 cipher（`:62-70` javadoc 不变式 + `service.oauth` 包经 engineGetDecryptedFields/engineUpdateInLock 走 provider）。`reencryptAll` 进程内重加密：`NopCredentialBizModel.java:686-690`——`credentialCipher.decrypt(data)` → `credentialCipher.encrypt(json)` 直接内存往返，无明文出口 | **PASS** |
| **R3 软删除 fail-closed（delFlag）** | `CredentialProviderImpl.java:493-515` `loadActiveCredential`：不存在→NOT_FOUND `:500-503` → **isDeleted→DELETED `:505-508`**（`isDeleted :517-520` 读 delFlag）→ 全部先于解密（`getCredential :129` 才调 decryptToData）与惰性刷新（`:131-135` refreshIfNearingExpiry 在 load 之后）。oauth2 disabled 拒绝 = 显式增量：`loadActiveCredential :510-512` 调 `assertOauth2NotDisabled :368-374`（仅 type.isOauth2Type() 且 status=disabled 才拒；非 oauth2 维持一期语义，`resolveType :381-387` 未注册类型容忍返回 null）；引擎通道锁内复查（D2-04 TOCTOU 闭合）`:333-343`；3-arg saveCredential 通道显式豁免 `:313-320`（adjudication §二#3(b)） | **PASS** |
| **R4 引用计数（registerUsage/unregisterUsage + consumerRef）** | `CredentialProviderImpl.java:196-228`（registerUsage：D6-03 前置存在/未删校验 `:201-209` + (credentialId,consumerRef) 幂等 `:213-220`）、`:230-242`（unregisterUsage 按 (credentialId,consumerRef) 删行）。NopAiModel 删除注销（C1a D6-02）：`NopAiModelBizModel.java:148-155`（覆盖 delete：删除成功后 unregisterUsageQuietly）、`:169-176`（deleteByQuery 基类路径虚分派不可达的补漏）、`:182-187`（unregisterUsageQuietly：provider 未部署/credentialId 空白静默跳过）；mock-verify 测试 `TestNopAiModelDeleteUsage.java:93`（"must really call unregisterUsage"） | **PASS** |
| **R5 一期消费链（W7-successor）零回归** | 优先级链 accountKey > credentialId > resolveApiKey：`ChatServiceImpl.java:260-261`（注释钉定）+ `:286-299` `resolveApiKeyForRequest`（isBlank accountKey 判空 D6-04 → resolver 非空用之 → 回退 `LlmConfigHelper.resolveApiKey`）。resolver：`AiModelCredentialResolverImpl.java:112-140`（credentialId 空→null 回退；非空→getCredentialData 强 fail-closed 传播 `:131`）。**beans 注册（A1 D6-01 修复仍在位）**：`nop-ai/nop-ai-service/src/main/resources/_vfs/nop/ai/beans/app-service.beans.xml:11-18`（`nopAiModelCredentialResolver`，ioc:default=true，依赖全 @Nullable 可选装配）。引用计数 on：`NopAiModelBizModel.java:81`（save 路径调 reconcile）→ `:107-128`（bind/换绑/解绑三态）。消费链其它消费方（nop-integration/nop-metadata credentialId）为二期显式增量，不属一期回归面 | **PASS** |
| **锚点 1：`ICredentialProvider` SPI 零签名漂移** | `git log --since=2026-08-14 -- .../nop-credential-api/.../ICredentialProvider.java` → **零提交**（六方法签名逐字未动，live `:29/:39/:48/:57/:65/:73`）。`CredentialData.typeName` 增量：commit `f75f3e528`（2026-08-18，+18/-0——纯 additive：新字段 + 新构造器 + getTypeName，旧单参构造器委托 `this(null, fields)` 向后兼容）——**系裁定新增**：`ai-dev/design/nop-credential/03-integration-metadata-migration-design.md:139`（§4.3 解析契约："SPI 增量 `CredentialData.typeName`……api additive 属性，`ICredentialProvider` 六方法签名零变更"）与 `:271`（"不触碰"段 W16-impl 增量裁定修正：additive 属性 + 独立新接口 `ICredentialMigrationSupport`，"既有接口签名零变更"）。判定：**裁定内 additive，非漂移** | **PASS** |
| **锚点 2：`@sec:` 配置加密零触碰** | `DefaultConfigValueEnhancer` 实际位于 `nop-core-framework/nop-config/src/main/java/io/nop/config/enhancer/DefaultConfigValueEnhancer.java:33`；`git log --since=2026-08-10` → 零提交（全历史最后两笔为 5bb9ffbcb 2026-03-04 与 148bc54a9，均远早于一期）。`grep -rn credential nop-config/src/main/java` → 零命中，凭证库与其零耦合（唯一引用处为 nop-integration `FeishuCredentials.java:12-18` javadoc，属凭证库消费 `@sec:` 语义而非反向耦合） | **PASS** |

## 二、MFA 六行矩阵逐行结论 (M1-M6)

| 行 | live file:line 证据 | 结论 |
|---|---|---|
| **M1 两阶段登录 challenge（scene=login 缺省 + null 兼容）** | `LoginServiceImpl.mfaVerifyAsync` `:563-611`；scene/verifiedAt 纪律 `:572-584`（A2-followup-2 D2-F2 / 设计 §3.5 再裁定）：`challenge.getScene() != null && !SCENE_LOGIN.equals(scene)` **或** `verifiedAt != null` → 抛 `ERR_AUTH_MFA_CHALLENGE_EXPIRED`——该分支**无 consume 调用**（不消费；对照 :591/:598/:638 合法失败路径均显式 consume），与他场景 token 纪律对齐。login 级 scene=login 缺省：`MfaChallengeHelper.java:51-58`（createLoginChallenge → `store.create(MfaChallenge.SCENE_LOGIN, ...)`，一期五参语义收敛路径）；老五参 create 委托 scene=login（设计 §3.5） | **PASS** |
| **M2 `ERR_AUTH_MFA_REQUIRED` 异常表达** | 登录级编码定义 `NopAuthErrors.java:94`（`nop.err.auth.mfa-required`）——窗口内 5 笔 NopAuthErrors 提交均为新增编码，`git diff <窗口首笔>^..HEAD` 无任何 hunk 触及该定义（编码/状态/参数零变更）。使用点：`LoginServiceImpl.java:401`（loginAsync）、`:438`（createSessionForUserAsync）、`MfaLoginPolicyServiceImpl.java:110`（OAuth 同构副本）。操作级独立编码：`NopAuthErrors.java:105` `nop.err.auth.operation-mfa-required`（`:102` 注释"与一期编码区分"），唯一使用点 `OperationMfaCheckerImpl.java:176`——两码互不混用 | **PASS** |
| **M3 `completeLogin` 分界裁决** | 一期方法原位：`LoginServiceImpl.java:476-480`（5 参 completeLogin 委托 6 参 restricted=false，三处一期调用点 loginAsync `:409` / createSessionForUserAsync `:446` / mfaVerify 经 completeMfaLogin `:810` 行为不变）。受限变体 = 显式增量：`:492+`（6 参重载，restricted=true 时标志在 saveSession/saveUserContextAsync **之前**写入，`:483-490` javadoc 钉定"先设后存"+ 一期三调用点不变声明）。可信设备登记在 completeLogin 前纯 DB 写：`:652`（consume）→ `:654-659`（`trustedDeviceManager.register`，仅密码类 loginType + rememberDevice，失败不阻断）→ `:661`（completeMfaLogin→completeLogin）；恢复码分支不登记（`:604-608`，§6.4） | **PASS** |
| **M4 store 装配（collect-beans + 条件激活 + lessons 15）** | `MfaStoreProvider.java:21-24`（三前缀 `nopMfaChallengeStore_`/`nopSmsCodeStore_`/`nopEmailCodeStore_` 命名型扩展点 + collect-beans 收集不加载类）、`:29-31`（类加载安全不变式）、`:62-69`（请求类型未注册 fail-closed）。装配源 `auth-service.beans.xml:21-23`（config/active bean id 不落 collect 前缀命名纪律）、`:34-64`（challenge/sms 三实现，redis 经 `ioc:condition`（if-property store-type=redis + on-class INosqlService）条件激活 `:45-48`/`:60-63`）、`:87-98`（nopMfaStoreProvider 三 collect-beans map）、`:100-107`（active factory bean）。EmailCodeStore 复刻 W8 模式 = 显式增量：`:65-66` 注释（"W15-impl……复制 W8 装配模式"）+ `:67-85`（nopEmailCodeConfig 不落前缀 + local/db 无条件 + redis 条件激活 `:81-84`）；W15 裁定"同 provider 第三组 map 不另立平行 provider"（`MfaStoreProvider.java:33-35`） | **PASS** |
| **M5 明文边界（secret 加密 / codeHash BCrypt）** | secret 落库加密：`NopAuthUserBizModel.java:321-328`（bindTotp：`generateSecret()` 明文 → `totpAuthenticator.getCipher().encrypt(base32Secret)` → `upsertPending(..., encrypted, ...)`；provisioning URI 一次性明文返回 `:326`）。恢复码 BCrypt：`NopAuthUserBizModel.java:795-797`（"BCrypt 加盐哈希存 codeHash，明文一次性返回"）+ `:1718`（regenerateRecoveryCodes，salt:hash 口径）；验证侧 BCrypt 比对 `LoginServiceImpl.java:726-745`（codeHash=salt:hash 格式）。webauthn masked 面归 V2 子 agent，本报告仅确认上述两个决定点 | **PASS** |
| **M6 一期零回归 defaults** | 操作级总开关缺省 false：`NopAuthConfigs.java:139-141`（`nop.auth.operation-mfa.enabled`，`Boolean.class, false`，@Description"缺省 false：关闭时拦截器零介入，一期零回归"）。无策略行 = 一期行为：`LoginServiceImpl.checkMfaRequired :1090-1133`——evaluator 未装配/无行 → `RoleMfaPolicy.NONE`（`:1098-1099`），第三态仅 `policy.getMaxLevel() > 0` 可达（`:1103-1108`）；一期分支原位原序：全局开关 `:1092-1093` → store null `:1094-1095` → setting 检查 `:1111-1115` → 因子等同 `:1117-1118` → challenge 创建 `:1130-1131`；可信设备豁免命中 = 返回 null 与一期"放行"同路径（`:1119-1127`）。新因子不绑定零感知：无 setting → `:1111-1112` 直接 return null | **PASS** |

## 三、既有断言零修改 git 抽查

二期窗口（`--since=2026-08-14`）逐文件核验，每笔触碰 commit 的 diff 已逐一检视：

| 文件 | 窗口内 commits | 分类 |
|---|---|---|
| `nop-auth/.../TestMfaLoginE2E.java` | 6047ee495（08-14，+38 行——新增 `testPasswordLoginMfaSmsFullChain`，纯新增方法）；daec06190（W12-impl，+6 行——setup 装配 `MfaFactorVerifier` wiring，标注"断言零修改"）；92c8dc392（flake 根治——移除 `@BeforeAll` 中 `nop.orm.enable-tenant-by-default` 全局 toggle，**仅 setup 基建、零断言触碰**，commit 引用 plan `2026-08-17-0447-1`） | **additions-only + 裁定内 setup 基建变更；既有断言零修改** |
| `nop-auth/.../TestMfaUserSelfService.java` | daec06190（+8 行 wiring，断言零修改）；92c8dc392（同上 setup-only）；8f7bc2373（flake 根治：bind 过期分支由 `bind-expire-seconds=0` 配置法改为 `-600s` 直连 SQL 回写 UPDATE_TIME 的确定性法——**断言面保持**：仍为 `assertThrows(NopException)` + `assertEquals(ERR_AUTH_MFA_BIND_EXPIRED)`，所验证生产行为（过期 bind → BIND_EXPIRED）不变；commit 引用 plan `2026-08-18-0904-1-mfa-phase2-security-audit-a2`） | **裁定内修改（arrangement 变更、断言面保持、非弱化）** |
| `nop-auth/.../TestScanLoginMfa.java` | daec06190（+6 行 wiring，断言零修改）；92c8dc392（setup-only tenant toggle 移除） | **additions-only（wiring）+ 裁定内 setup 基建变更** |
| `nop-credential/.../crypto/TestCredentialCipher.java` | 39a48e9c6（C1a 加固，+78 行/-1 行——删除仅 1 行 import 区外零删除：4 个新测试 decryptRejectsCv1WrappedLegacyPayloadWithoutV1Marker / decryptAcceptsWellFormedV1Payload / decryptTruncatesCiphertextParamInFormatExceptions / allFormatExceptionBranchesUseTruncatedParam + 1 个 assertFalse import；**既有断言零修改**，commit 载明"一期契约锚点零回归（service 196/kms-vault 40/web 2 全绿）"） | **additions-only** |
| `nop-credential/.../entity/TestNopCredentialBizModel.java` | 39a48e9c6（+83 行/-0 行，纯新增用例） | **additions-only** |
| `nop-auth-meta/.../TestNopAuthMfaSettingXmeta.java` | 窗口内零提交 | **untouched** |
| `TestSmsLogin*.java` | — | **仓库中不存在**（SMS 登录链路由 TestMfaLoginE2E 内 `testPasswordLoginMfaSmsFullChain` 等覆盖） |

附注：
- 主代码侧两处二期行为触碰一期能观测面，均有裁定记录：(1) `CredentialCipher.decrypt` 内层 `v1:` 强制（D5-05，commit 39a48e9c6，A1-audit successor）；(2) `checkMfaRequired` 增 protected 参数 `requestHeaders`（W15 可信设备，`LoginServiceImpl.java:1078-1079` 注释钉定"protected 单模块内签名变更"，设计 §六 裁定）——均为 fail-closed 收紧/单模块内增量，非弱化。
- 任务书提及的 TestWebAuthnMfaE2E 家族"3 个既有测试重排（断言面保持）"（A2-audit D3-F1）不在一期文件清单内（该文件本身为 W14 二期产物），未纳入本表范围。

## 四、总结论

**ALL-PASS（11/11 矩阵行 + 断言抽查全部合格，无 FAIL finding）。**

- 凭证五行矩阵 R1-R5 全部 PASS：cv1 格式与 round-trip 完好、唯一明文出口与进程内 reencryptAll 完好、delFlag 先序 fail-closed 完好（oauth2 disabled 为裁定内显式增量）、引用计数完好且 D6-02 删除注销闭合、W7-successor 消费链（优先级链 + beans 装配 + 引用计数）完好。
- 两附加锚点 PASS：`ICredentialProvider` 窗口内零提交（零漂移）；`CredentialData.typeName` 为 03-design §4.1/§4.3 裁定内纯 additive（+18/-0）；`@sec:` enhancer 自 2026-03 后零提交、零凭证库耦合。
- MFA 六行矩阵 M1-M6 全部 PASS：mfaVerifyAsync scene 纪律（{login,null} + 不消费拒绝）落地、登录级/操作级错误码双轨隔离（一期编码定义窗口内零 diff）、completeLogin 原位 + 受限变体显式增量 + 可信设备登记时序正确、store 装配模式原样 + EmailCodeStore 复刻 W8、secret 加密/codeHash BCrypt 两个决定点在位、操作级缺省 false + 无策略行一期逐字节等价路径在位。
- 断言零修改抽查：6 个一期测试文件中 1 个 untouched、4 个 additions-only（含 wiring-only）、1 个（TestMfaUserSelfService）含**裁定内** arrangement 修改（断言面保持、plan 引用在 commit message 中可溯：2026-08-17-0447-1、2026-08-18-0904-1）。**未发现任何未裁定的断言弱化（P1 违约：0 项）。**

零 P1/P2 finding；无需进一步行动项。
