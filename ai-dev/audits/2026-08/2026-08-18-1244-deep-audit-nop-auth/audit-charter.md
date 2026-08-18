# A2-audit 审计章程（MFA 二期安全审计）

> Audit Status: closed
> Plan: `ai-dev/plans/2026-08-18-0904-1-mfa-phase2-security-audit-a2.md`
> Date: 2026-08-18 12:44
> Scope baseline: 2026-08-18 live repo（本 charter 全部 file:line 锚点为 live 核对产物，非旧 plan 结论转抄）

## 一、审计范围（模块/实体/端点全清单）

### 1.1 模块

| 模块 | 角色 |
|---|---|
| `nop-auth/nop-auth-core` | MFA 核心 SPI/store 契约、MfaChallenge 模型 |
| `nop-auth/nop-auth-service` | MFA 全部业务实现（biz/service/mfa/login/store） |
| `nop-auth/nop-auth-api` | IOperationMfaChecker / IMfaLoginPolicyService SPI |
| `nop-auth/nop-auth-dao` | 实体：NopAuthMfaSetting / NopAuthMfaCredential / NopAuthMfaTrustedDevice / NopAuthRoleMfaPolicy |
| `nop-credential/nop-credential-service` | C1b 跨模块 @MfaRequired 标注回归面（不重开凭证侧 finding） |
| `nop-service-framework/nop-graphql/nop-graphql-core` | @MfaRequired 元数据传播链（D5 审计面；框架侧修复须显式裁定归属） |
| `nop-service-framework/nop-biz` | BizObjectBuildHelper.mergeBizModel 元数据合并触点（D5 审计面） |

### 1.2 实体

- `NopAuthMfaSetting`（nop-auth-dao，1:1 用户 MFA 状态机：status pending/enabled/disabled + mfaType + secret + lastVerifiedWindow）
- `NopAuthMfaCredential`（W14：webauthn credential，credentialId/publicKey/signCount masked 不暴露）
- `NopAuthMfaTrustedDevice`（W15：deviceHash 复合唯一 (userId,deviceHash) + expireAt）
- `NopAuthRoleMfaPolicy`（W13：roleId 1:1 策略行，minMfaLevel/allowTrustedDevice）
- `nop_auth_email_code` / `nop_auth_mfa_challenge`（store 持久化实体）
- `NopAuthUser`（登记通道 phone/email 字段来源）

### 1.3 公开端点（live 锚点核对清单）

**LoginApiBizModel**（`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java`）：

| 端点 | 访问 | 锚点 |
|---|---|---|
| loginAsync（密码/信道/扫码登录入口） | publicAccess | :119 |
| logoutAsync | publicAccess | :125 |
| getLoginResultAsync（accessCode 兑换） | publicAccess | :132 |
| getLoginUserInfoAsync | publicAccess | :141 |
| refreshTokenAsync（受限白名单，Async 尾缀剥除注册名 `LoginApi__refreshToken`） | publicAccess | :150 |
| sendSmsCode（登录短信，phone/IP 限流） | publicAccess | :171 |
| sendMfaCode（MFA 二因子码重发，按 mfaType 分派 sms/email） | publicAccess | :181 |
| mfaVerifyAsync（登录级二因子验证） | publicAccess | :195 |
| webauthnAuthOptions（任意 scene webauthn options，非 login scene 需同会话） | publicAccess | :233 |
| mfaVerifyOperation（操作级验证，需登录态+同会话，成功不签发凭证） | permission（非 public） | :294 |
| verifyChannelProof（登记通道 proof 验证，需登录态，channel 参数限定已登记集合） | permission | :383 |

**NopAuthUserBizModel**（`.../entity/NopAuthUserBizModel.java`）：

| 动作 | 锚点 | 备注 |
|---|---|---|
| bindMfa（mfaType 白名单 + proof 前置 + webauthn 分派） | :235/:237 | 受限会话白名单 |
| confirmMfa（bindToken → enabled + 恢复码签发） | :603 | 白名单 |
| unbindMfa（@MfaRequired :676；webauthn 双断言） | :674/:677 | 白名单（短路=不做操作级二次验证） |
| confirmWebauthnRegistration（ceremony 确认） | :448/:450 | 白名单（W14 增补） |
| webauthnBeginVerify（解绑 ceremony 发起） | :534/:536 | 非白名单（裁定：不可达死代码） |
| generateRecoveryCodes（@MfaRequired :754） | :752 | |
| getMfaStatus | :770 | 白名单 |
| listWebauthnCredentials（masked） | :795 | 非白名单 |
| removeWebauthnCredential | :822 | **路由项 1 待再裁定** |
| renameWebauthnCredential | :840 | **路由项 1 待再裁定** |
| listTrustedDevices | :871 | |
| removeTrustedDevice | :895 | |
| resetUserMfa（@MfaRequired :940 + requireAdmin 运行时校验） | :938 | |
| resetUserPassword（@MfaRequired :1422） | :1420 | W12 首批 |
| changeSelfPassword（@MfaRequired :1438） | :1436 | W12 首批 |

**NopAuthRoleBizModel**（`.../entity/NopAuthRoleBizModel.java`）：

| 动作 | 锚点 |
|---|---|
| saveMfaPolicy（requireAdmin 运行时 + minMfaLevel 1/3 校验 + 审计） | :163/:164 |
| removeMfaPolicy（requireAdmin + 幂等） | :203/:204 |

### 1.4 @MfaRequired 标注分布全量（live grep 2026-08-18）

- nop-auth 5 处：`NopAuthUserBizModel.java` :676 (unbindMfa) / :754 (generateRecoveryCodes) / :940 (resetUserMfa) / :1422 (resetUserPassword) / :1438 (changeSelfPassword)
- nop-credential C1b 4 处：`NopCredentialAuthBizModel.java` :108 (grant) / :148 (revoke)；`NopCredentialBizModel.java` :650 (reencryptAll) / :771 (delete)
- 框架测试面：`nop-graphql-core` `TestMfaRequiredMetadata`、`TestOperationMfaExecutorWiring`；`nop-biz` `TestBizObjectManager`/`MyObjectBizModel`

### 1.5 元数据传播链四触点（D5）

1. `io.nop.api.core.annotations.mfa.MfaRequired`（注解定义）
2. `ReflectionBizModelBuilder.java` :348（读取 + 构建期约束校验；约束错误 `GraphQLErrors.java` :301/:306）
3. `GraphQLFieldDefinition.java` :74（mfaRequiredMeta 字段）
4. `GraphQLExecutor.java` :187-197（两检查点：受限会话拦截 + 操作级验证；query/publicAccess 放行规则）

### 1.6 store 家族（D2/D7）

`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/store/`：

- challenge：`DbMfaChallengeStore` / `RedisMfaChallengeStore`（create/peek/consume/markVerified/incrFailCount 契约）
- sms：`DbSmsCodeStore` / `RedisSmsCodeStore`（send/verify 原子消费 + 内部失败计数）
- email：`DbEmailCodeStore` / `RedisEmailCodeStore` + `EmailCodeEntry`（W15）
- 装配：`MfaStoreProvider.java`（collect-beans + 条件激活 + lessons 15 类加载安全）
- challenge 三创建触点收敛：`MfaChallengeHelper.java`（webauthnScenePayload/cryptoChallengeOf/sessionIdOf）

### 1.7 核心组件

- `LoginServiceImpl.java`（checkMfaRequired :195 附近；sendMfaCode :787；mfaVerifyAsync；ATTR_MFA_ACCESS_CODE :538；ATTR_TRUSTED_DEVICE_REGISTERED :542）
- `MfaFactorVerifier.java`（五参统一载体 :133；TOTP/SMS/EMAIL/WebAuthn/Recovery 五分支）
- `MfaLoginPolicyServiceImpl.java`（checkMfaForUserName——路由项 2 同构副本 A）
- `RoleMfaPolicyEvaluator.java` + `RoleMfaPolicy.java`（max/AND 合并）
- `OperationMfaCheckerImpl.java`（白名单 :95-103；受限分支 :121-128 前置于总开关；票核验 :148-159；challenge 创建 :170）
- `MfaTrustedDeviceManager.java`（指纹/豁免窗口/登记 :125/撤销）
- `WebAuthnAuthenticator.java`（Yubico webauthn-server-core 2.7.0 封装）
- `NopAuthConfigs.java`（`nop.auth.operation-mfa.enabled` 缺省 false :133 附近；email 开关 :173）

## 二、七维度探查清单（目标锚点/威胁假设/对抗用例）

### D1 因子强度与绑定/登记生命周期

- 目标锚点：`NopAuthUserBizModel` bindMfa/confirmMfa/unbindMfa/getMfaStatus；`MfaFactorVerifier` factorLevel 表；`NopAuthMfaSetting` 状态机；登记通道 proof（`LoginApiBizModel.verifyChannelProof` :383-475 + `NopAuthConstants` SMS_KEY_PROOF/EMAIL_KEY_PROOF）；明文边界（secret 加密/恢复码 BCrypt/publicKey masked）。
- 威胁假设：跨用户 bindToken 冒用；pending 状态旁路 confirm；登记通道枚举/混淆（channel 参数绕过已登记集合限定）；secret/恢复码明文泄漏。
- 对抗用例：A1-跨用户 bindToken；A2-受限会话无 proof 直接 bindMfa；A3-channel=phone 但仅登记 email；A4-getMfaStatus/credential 列表响应含敏感字段。

### D2 防重放与一次性消费

- 目标锚点：TOTP lastVerifiedWindow（`MfaFactorVerifier`）；WebAuthn signCount 条件 UPDATE（`WebAuthnAuthenticator`/credential dao）+ challenge 单次消费（store consume）；SMS/email 三态与 key 隔离（`NopAuthConstants` login:{phone}/mfa:{userId}/email 变体）；challenge peek 不刷新 TTL / incrFailCount 超限作废；操作级票（markVerified + consume 原子双花防护 `OperationMfaCheckerImpl` :148-159）；`MfaChallengeHelper` 三创建触点。
- 威胁假设：已消费码/challenge 重放；并发同码验证双花；跨场景 token 挪用（login challenge 用于操作级 / proof ticket 用于登录 / operation 票跨 operation）。
- 对抗用例：B1-consume 后重放；B2-并发双 verify；B3-loginToken 调 mfaVerifyOperation（TestOperationMfaE2E 已有 :344）；B4-票跨 operation 挪用；B5-TOTP 同窗口码重用。

### D3 恢复通道

- 目标锚点：`LoginServiceImpl.mfaVerifyAsync` recovery 分支；`generateRecoveryCodes`/`unbindMfa`/`resetUserMfa` 作废语义；BCrypt 存储。
- 威胁假设：恢复码穷举无失败计数分界；重置窗口期旧码复活；管理员重置后旧因子复活。
- 对抗用例：C1-已用恢复码重放统一 MFA_FAIL；C2-reset 后旧 TOTP 码仍可用；C3-unbind 后恢复码仍可登录。

### D4 角色级策略一致性与受限会话

- 目标锚点：`RoleMfaPolicyEvaluator`（max/AND）；`MfaLoginPolicyServiceImpl.checkMfaForUserName`（路由项 2 副本 A）↔ `LoginServiceImpl.checkMfaRequired`（副本 B）；MFA_RESTRICTED 第三态与 completeLogin 受限变体；受限拦截 executor/checker 双触点 + 白名单短路（`OperationMfaCheckerImpl` :95-128）；Dao-cache 白名单双触点（`DaoUserContextCache`/`DaoLoginSessionStore`）；OAuth/SSO 三分支（nop-auth-sso 内 MfaLoginPolicyServiceImpl 调用面）；saveMfaPolicy/removeMfaPolicy 权限。
- 威胁假设：受限会话白名单端点滥用（白名单动作自身越权面）；策略合并绕过（多角色 max/AND）；Dao-cache 与 DB 不一致窗口；跨入口判定不一致。
- 对抗用例：D-1-受限会话调非白名单 mutation；D-2-多角色 1+3 合并=3；D-3-无策略行=一期行为；D-4-OAuth 绕过三分支。

### D5 操作级 MFA 全路径覆盖

- 目标锚点：§1.5 四触点 + executor 两检查点 + `mfaVerifyOperation`；`IOperationMfaChecker` SPI 可选注入；`nop.auth.operation-mfa.enabled` 缺省 false；标注分布 §1.4；批量请求整批预执行中止语义（GraphQLExecutor）。
- 威胁假设：绕过注解传播的调用路径；直接 GraphQL 字段选择绕过 executor；同会话票跨 operation 挪用；C1b 跨模块标注元数据断言漂移。
- 对抗用例：E1-nop-auth 五动作+凭证四动作容器级元数据断言；E2-enabled=false 零介入；E3-批量含敏感操作中止；E4-票跨 operation 拒绝。
- **路由项 1 探查输入**：removeWebauthnCredential/renameWebauthnCredential 破坏性评估（对照 C1b 缩窄先例：管理面非破坏性动作不标注）。

### D6 可信设备

- 目标锚点：`MfaTrustedDeviceManager`（指纹哈希/固定窗口/登记/撤销四触发）；`NopAuthUserBizModel.removeTrustedDevice`；headers 穿线仅经 verifySecondFactorAndComplete；`policy.allowTrustedDevice` 消费点；**OAuth 副本"不加豁免分支"专项断言**（`MfaLoginPolicyServiceImpl`——路由项 2 关联面）。
- 威胁假设：伪造/重放 deviceHash；豁免窗口无限续期（lastUsedAt 续 expireAt）；跨用户指纹挪用；撤销后残留豁免。
- 对抗用例：F1-豁免不续期逐字节断言（TestTrustedDeviceE2E 先例）；F2-撤销后同 hash 立即失效；F3-满员 false；F4-恢复码路径不登记（结构性排除负例）。

### D7 登录级链路一期零回归

- 目标锚点：设计 §二 矩阵六行（两阶段 challenge / ERR_AUTH_MFA_REQUIRED 异常表达 / completeLogin 分界 / store 装配 / 明文边界 / 一期零回归）；一期测试树全家族（TestMfaLoginE2E/TestScanLoginMfa/TestMfaUserSelfService 等）；git 层面核对 W12-W15 未改动一期断言。
- 附加回归项：既有 MFA 套件断言零修改（git diff 核对）；双零介入（enabled=false + 无策略行）；C1b 容器级元数据断言仍绿（nop-credential 测试树重跑）。
- 对抗用例：G1-矩阵六行逐行；G2-git log 核对一期断言文件；G3-full-module 测试重跑。

## 三、回归基准声明

1. **设计基准**：`ai-dev/design/nop-auth/02-mfa-phase2-design.md` §二 一期契约兼容性矩阵（六行）+ §3.6/§4.6/§5.3.6/§5.3.7/§6.6 impl 裁定回写标注。
2. **impl Closure 基准**：W12 `2026-08-16-2321-2` / W13 `2026-08-17-0447-2` / W14 `2026-08-17-2212-1` / W15 `2026-08-17-2212-2` 四份 plan 的 Closure 段证据。
3. **C1b 跨模块标注面**：`2026-08-17-1345-2-credential-sensitive-actions-mfa-required-c1b.md`（凭证库四动作标注 + 容器级断言在 nop-credential 侧）。
4. **一期契约**：两阶段 challenge、ERR_AUTH_MFA_REQUIRED errorParams 契约、completeLogin 分界、store collect-beans 装配 + lessons 15、明文边界、未启用 MFA 用户零感知。
5. **测试基线**：W15 closure 记录全模块 355 tests 0 failures——本审计 live 重跑核实（missing-tenant-id flake 已 2026-08-17 根因修复退役，任何新失败按实际缺陷处置）。

## 四、执行纪律

- 每维度由 fresh 独立子 agent 执行（探查/报告，不改产品代码）；P0/P1 修复由编排 session 执行，修复后另一 fresh 子 agent 复核。
- 每条 finding 标注 P0-P3 + file:line live 锚点 + 建议修复方向。
- 探查测试（若落盘）标注审计来源，作为可回归对抗断言。
- finding 裁决三态：fixed in this plan / successor 所有权 / watch-only（含 Why Not Blocking）；P0/P1 不静默降级。
