# D4 审计报告：角色级策略一致性与受限会话

> **Dimension**: D4（角色级策略一致性与受限会话）
> **Executor**: fresh 独立对抗审计子 agent（task: D4-role-policy-restricted-session，本 session）
> **Date**: 2026-08-18
> **Charter**: `ai-dev/audits/2026-08/2026-08-18-1244-deep-audit-nop-auth/audit-charter.md` §二 D4
> **Design baseline**: `ai-dev/design/nop-auth/02-mfa-phase2-design.md`（§4.1-4.6 角色策略引擎 / §6.3+§6.6 allowTrustedDevice 消费 / §二 兼容性矩阵）
> **Mode**: RESEARCH + REPORT ONLY（未修改任何产品/测试代码；未运行 maven 构建）
> **结论速览**: 无 P0/P1。P3 ×6（两项为受限引导流功能性死端、方向 fail-safe；其余为 watch/cosmetic）。对抗用例 D-1~D-5 全部**未探查到**可利用的策略绕过或受限会话越权。**路由项 2（两同构副本）当前同步**：8 个逻辑块逐块等价，2 处登记在案的裁定漂移（豁免分支 / 异常抛出位置）+ 1 处未登记的 cosmetic 不对称（evaluator null 防御）。回归锚点 4 项全部 **PASS**。

---

## 一、Findings 总表

| ID | Severity | file:line anchor | description | suggested fix direction |
|---|---|---|---|---|
| D4-1 | P3（功能性死端，fail-safe 方向） | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:690-699`（unbindMfa 因子验证）；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:787-793`（sendMfaCode 需活 challenge） | **已启用 sms/email 弱因子用户在受限会话内无法自助完成"解绑→升级"**：unbindMfa 需要 `mfa:{userId}`/`mfa-email:{userId}` 码，但该 key 的全部两个生产端点在受限会话均不可达——`sendMfaCode` 要求活 challenge（受限登录**不建 challenge**，设计 §4.1 结论 9），`bindSms`/`bindEmail` 被 `ALREADY_ENABLED` 拒绝（bindMfa :268-270）；`mfaVerifyOperation` 本身是非白名单 mutation 被受限拦截拒绝。E2E 测试用 store 直调绕过（`TestMfaRestrictedSessionE2E.java:445` `smsCodeStore.send("mfa:" + userId)`），掩盖了该缺口。totp 用户不受影响（无需发码）、webauthn 用户结构性不受限（level 3=上限）。**非安全漏洞**（攻击者同样拿不到码，fail-closed），但设计 §4.3 弱因子升级路径（"受限会话内先 unbindMfa 再 bindMfa 强因子"）对 sms/email 用户断链，只能依赖管理员 `resetUserMfa`。 | 受限会话内 unbindMfa 触发因子码补发（经 channel proof 后），或新增受限态专用的因子码 resend 端点（入白名单），或在设计层显式登记"sms/email 弱因子升级走管理员重置"并同步 E2E 改走真实 API 路径 |
| D4-2 | P3（功能性死端，fail-closed 方向） | `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/login/OAuthLoginServiceImpl.java:170`（`userContext.setUserId(userName)`）；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:258-262` | **OAuth 受限会话的引导流断链**：OAuth `buildUserContext` 把 userId 设为 userName（一期口径，`IMfaLoginPolicyService.java:35` 自认），受限签发后白名单动作（bindMfa/unbindMfa/getMfaStatus/verifyChannelProof）以该伪 userId 解析本地 `NopAuthUser` → `getEntityById` 未命中 → "current user not found"（bindMfa :258-262）/proof 通道解析失败。本地 userName≠userId 的部署中，OAuth 受限用户无法走自助绑定引导（fail-closed：不越权、不绕过，仅不可用），需登出换密码入口或管理员介入。 | Phase 3 裁定：completeRestricted 内把 OAuth 上下文的 userId 归一为本地 `NopAuthUser.userId`（copy A 已解析出本地 user，信息在手上），或在设计层登记 OAuth 受限引导流的一期口径限制 |
| D4-3 | P3（watch，Phase 3 裁定项） | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/OperationMfaCheckerImpl.java:121-128` | **受限拦截分支不受 `nop.auth.mfa.enabled` 总闸门控**（分支前置于一切开关，仅读 `isMfaRestricted`）：管理员全局关闭 MFA 后，存量受限会话在重新登录前持续被拒非白名单 mutation。可辩护为 §4.3 时点语义 (b)"会话中期不回溯改写已签发会话的受限状态"的自然推论（重新登录即恢复一期放行），但设计矩阵第 1 行"总闸 off = MFA 子系统整体旁路"字面下存在解释张力。登记 watch，建议 Phase 3 裁定"受限分支是否应随总闸 off 而失效"。 | 如裁定需跟随总闸：checker 受限分支前补 `CFG_AUTH_MFA_ENABLED` 检查（一行）；如裁定维持快照语义：在设计 §4.3 时点语义补一句显式声明 |
| D4-4 | P3（cosmetic，路由项 2 输入） | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaLoginPolicyServiceImpl.java:69-70,:88` vs `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:190-192,:1020-1021` | **两副本的 evaluator null 防御不对称**（未登记的漂移）：副本 B 对 `roleMfaPolicyEvaluator` 做 `@Nullable` 注入 + `== null ? RoleMfaPolicy.NONE` 回退（:190-192/:1020-1021）；副本 A 为非空 `@Inject` 无回退（:69-70/:88）。生产等价（两 bean 同在 `auth-service.beans.xml:134` 注册，共存亡），但手工 wiring 场景（TestRoleMfaPolicy 型测试）下副本 A 会 NPE。属路由项 2 Phase 3 裁定时应登记的 cosmetic 漂移。 | Phase 3 裁定：统一为_nullable + NONE 回退_（更防御）或统一为必注入（更严格）；无论取向，回写设计 §4.6 |
| D4-5 | P3（watch，防御一致性） | `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java:214-216` vs `GraphQLActionAuthChecker.java:47` | **受限分支对顶层非 field selection（fragment spread）静默 `continue`，而同请求早先执行的 auth checker 对其无条件强转 `ClassCastException`**（`_invokeOperations` :327 同样强转）——当前组合下顶层 fragment 必然在 auth 检查处崩溃（fail-closed，无利用面），但两处防御姿态不一致：若未来 auth checker 变得 fragment-tolerant（如按 `checkSelectionSet` :65-67 展开），受限分支的 `continue` 即成为把非白名单 mutation 藏进顶层 fragment 的**静默绕过**。@MfaRequired 循环（:226-233）同型。 | 受限分支与 @MfaRequired 循环对非 field 顶层 selection 改为抛错（fail-fast 对齐 auth checker 现行为），或显式展开 fragment 后逐 field 检查 |
| D4-6 | P3（info） | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthRoleBizModel.java:164-197` | `saveMfaPolicy` 不校验 roleId 对应角色是否存在——可为任意字符串（含不存在的角色）建策略行。无安全影响（策略仅在 roleId 出现于用户角色快照时被消费；无 FK 是设计显式选择 §4.3），仅产生孤儿行/管理面噪音。 | 可选：saveMfaPolicy 前校验 `daoFor(NopAuthRole.class).getEntityById(roleId) != null`（与 checkAllowEdit 的角色存在性隐式依赖对齐） |

**无 P0/P1**：全部对抗用例（D-1~D-5）未发现可利用的策略绕过、受限会话越权或跨入口判定不一致（逐项证据见下）。

---

## 二、对抗用例逐项裁决

### D-1 受限会话白名单端点滥用 — **未探查到**（白名单 8 项逐项枚举）

白名单 live 清单（`OperationMfaCheckerImpl.java:95-103`，恰 8 项）逐项对抗评估（攻击者 = 仅持密码的受限会话）：

| # | 白名单项 | 攻击面评估 | 结论 |
|---|---|---|---|
| 1 | `NopAuthUser__bindMfa` | 受限会话前置 `requireChannelProof`（`NopAuthUserBizModel.java:253-256` → :1075-1132）：无有效 proof 票（scene=channel-proof + verifiedAt + **userId 绑定** + 原子 consume 四条件 :1078-1084）即发码到**服务端解析的登记通道**并抛 `CHANNEL_PROOF_REQUIRED`。enrollment attack 门槛提升到"密码+登记通道"（设计 §4.3 威胁模型）；发码限流 60s/日上限（:1119-1120 `checkProofRateLimit`）。 | 安全 |
| 2 | `NopAuthUser__confirmMfa` | 需本人 pending 行 + bindToken 匹配 + 因子码（:607-631）+ **策略防降级校验**（:636-645，弱因子 → `FACTOR_TOO_WEAK`，E2E `testConfirmMfaFactorTooWeakRejected` 钉定）。bindMfa 已被 #1 门禁，confirm 无独立越权面。 | 安全 |
| 3 | `NopAuthUser__confirmWebauthnRegistration` | challenge 四重绑定（scene=webauthn-register :462 + userId :463 + payload.sessionId==当前会话 :464-465 + setting 复核 pending+webauthn :471-474）；注册发起（bindWebauthn）在 bindMfa 分派**之前**继承 proof 门禁（:254-256 在 :278-280 之前，W14 专项断言）。 | 安全 |
| 4 | `NopAuthUser__unbindMfa` | **白名单短路（checker :122-124）仅跳过操作级二次验证，动作本体的因子验证完整保留**：非 webauthn 走 `MfaFactorVerifier.verify(setting, mfaType, code)`（:695-698），webauthn 走双断言 ceremony（:690-692，scene=webauthn-unbind + userId + sessionId 绑定 :726-729）。攻击者无因子即不可解绑（设计 §4.4 白名单含 unbindMfa 的显式裁定）；未 enabled 用户 :686-688 显式 `NOT_ENABLED`。E2E `testRestrictedWhitelistMutationShortCircuitsOperationMfa`（:207-236，开 operation-mfa 开关使短路可分辨）钉定短路语义 = 动作体错误（NOT_ENABLED）而非两个拦截错误码。 | 安全 |
| 5 | `NopAuthUser__getMfaStatus` | @BizQuery（:770-771）——executor 受限分支只路由 mutation（`GraphQLExecutor.java:212-213`），query 本就放行；白名单项为直调路径兜底。只读 + phone 脱敏（D1-6 已登记 ≤4 位边界）。 | 安全 |
| 6 | `LoginApi__verifyChannelProof` | 需登录态（:387-389）；码 key 通道隔离 + store 内部 max-attempts（6 位码 5 次尝试上限）；票 userId 绑定（issueChannelProofTicket :466-475 钉定 `userContext.getUserId()`）。正常会话用户调用无收益（bindMfa 正常路径不消费 proof 票）。 | 安全 |
| 7 | `LoginApi__logout` | 无状态副作用（logoutSession 置 logoutType）。 | 安全 |
| 8 | `LoginApi__refreshToken` | publicAccess（`LoginApiBizModel.java:150-157`）——executor 侧已放行（`GraphQLExecutor.java:218` `isPublicAccess`），checker 白名单为直调兜底。**刷新不丢受限标志**：refreshTokenAsync → `getUserContextAsync(token)` → `AbstractLoginService.doGetUserContext`（:62-84）→ `userContextCache.getUserContextAsync(sessionId)` 加载**同一会话**（Dao-cache 从 cacheData 反序列化，`DaoUserContextCache.java:68-71` + :124-126 序列化含 mfaRestricted；`UserContextImpl.serializeToJson:67-71` 同）→ 无重建上下文代码路径。**受限会话不能借刷新升级为完整会话**。E2E `testRestrictedPublicAccessMutationAllowed`（:238-253）钉定刷新通过且 `TestMfaRestrictedDaoCache.testRestrictedFlagSurvivesDaoCacheRoundTrip`（:120-137）钉定标志存活。 | 安全 |

**注册名核对**（charter 要求）：`ReflectionBizModelBuilder.getActionName`（:294-302）仅在 `funcModel.isAsync()` 时剥除 `Async` 尾缀——`refreshTokenAsync`/`logoutAsync` 返回 CompletionStage（isAsync=true）→ 注册为 `LoginApi__refreshToken`/`LoginApi__logout`，与白名单逐字一致；其余 6 项方法名无尾缀，`getMutationName`/`getQueryName` 用缺省名一致。执行期纠错（设计 §4.6 裁定 2）已固化。

**白名单外溢出检查**：executor 受限分支以 `isPublicAccess(fieldDef)`（:237-239，`auth==null || publicAccess`）判别放行——reflection 构建的 action **恒有** auth meta（`ReflectionBizModelBuilder.java:335-341`：无 @Auth 时缺省 `publicAccess=false` + permission 串），故 `saveMfaPolicy`/`removeMfaPolicy`（无 @Auth）为非 public → 路由进 checker → 非白名单 → `ERR_AUTH_MFA_RESTRICTED_SESSION`。**受限 admin 无法 removeMfaPolicy 自我解限**（需重新登录走完整判定）。`auth==null` 仅出现于非 reflection 构建字段，且与 `GraphQLActionAuthChecker.isAllowAccess`（:118-120）同语义（auth==null 视为公开）——该类字段本就对匿名开放，非 MFA 层缺口（归属 D5/auth 层口径）。`mfaVerifyOperation`/`sendMfaCode` 等 public 或非白名单端点在受限会话的可达性分析：mfaVerify 公开但需活 challenge（受限登录不建 challenge，无挪用面）；mfaVerifyOperation 非白名单 mutation → 被拒（受限会话不能铸操作级票，防引导流混乱）。

### D-2 策略继承合并绕过 — **未探查到**

1. **max 合并**：`RoleMfaPolicyEvaluator.evaluateForRoles`（:87-104）——`maxLevel = Math.max(maxLevel, row.getMinMfaLevel())`（:99），minMfaLevel 1+3 合并必为 3。E2E 钉定：`TestRoleMfaPolicy.testMultiRoleMaxAndAndMerge`（:136-145，"max(minMfaLevel) 合并——最严格角色胜"）。
2. **AND 合并**：`row.getAllowTrustedDevice()==null || ==0 → allowTrustedDevice=false`（:100-101），null 视为禁（fail-safe）；无行/无策略 = `RoleMfaPolicy.NONE(0,true)`（:21，:103）。钉定 :144。
3. **delFlag 陈旧行**：evaluator 逐 roleId `getEntityById`（:95，主键装载不滤 delFlag）后**显式跳过逻辑删除行**（:96-97 `isLogicallyDeleted`，:107-109 `delFlag != 0`）；`removeMfaPolicy` 走 `dao.deleteEntity`（:209）在 `useLogicalDelete=true` 实体（orm.xml:1292）上落 delFlag=1；`saveMfaPolicy` 复活归零（:182-185）。钉定：`testRemoveMfaPolicyDeletesRowAndIsIdempotent`（:214-242，删后评估=0 + 幂等二次删除不抛）。
4. **角色继承展开**：直接角色 + childRoleIds 展开 + 隐式 user 角色及其继承链（:116-132），与 `buildUserContext` 口径一致（LoginServiceImpl:1134-1140 同构展开）；"策略挂 user 角色=全员强制" 钉定 `testImplicitUserRolePolicyAppliesToAll`（:160-168）与 `testChildRoleIdsInheritanceExpansion`（:148-157）。
5. **factorLevel 全表一致性**（charter 要求）：`factorLevel`（:54-68）sms/email=1、totp=2、webauthn=3、未知/null/空=0（fail-closed）——与设计 §4.3 强度序 + §5.3.1 常量表逐项一致；钉定 `testFactorLevelTable`（:101-109）。两副本共用同一静态方法（copy A :93 / copy B :1028 均调 `RoleMfaPolicyEvaluator.factorLevel`），无分叉点。
6. **角色删除残留**：角色行逻辑删除后 policy 行残留（无 FK，设计显式）——但评估入口是用户角色快照 `user.getRoles()`，与权限快照同源同漂移（§4.3 时点语义），失败方向为"多限"（fail-safe）或与权限模型一致收窄，无独立绕过面。

### D-3 Dao-cache 与 DB 不一致窗口 — **未探查到**（无未失效缓存；陈旧窗口为设计快照语义）

1. **策略读路径无应用级缓存**：登录评估经 `daoProvider.daoFor(NopAuthRoleMfaPolicy.class).getEntityById`（evaluator :95）直读 DB；`saveMfaPolicy`/`removeMfaPolicy` 经同一 DAO 写（NopAuthRoleBizModel :175-192/:206-212）——同 JVM 同 ORM 会话工厂，无中间缓存层需要失效。策略实体 no-tenant（orm.xml:1291），读写无租户过滤差异。
2. **变更生效时点 = 下次登录**（设计 §4.3 时点语义 (a)）：进行中 challenge/已签发会话不回溯——`removeMfaPolicy` 后存量受限会话保持受限、`saveMfaPolicy` 后存量完整会话保持完整，均为登记在案的设计行为（§4.4 拒绝实时重评），非不一致缺陷。
3. **mfaRestricted 会话持久化双触点核对**（charter 要求）：`UserContextImpl.serializeToJson:67-71`（仅受限时写 `mfaRestricted` 键）+ `DaoUserContextCache.saveUserContextAsync:124-126`（同语义，正常会话 cacheData 形态零变化）——两触点齐备且"仅受限写入"；反序列化 `BeanTool.setProperties`（:68-71）命中 `setMfaRestricted`（UserContextImpl:314-317）。round-trip 钉定：`TestMfaRestrictedDaoCache.testRestrictedFlagSurvivesDaoCacheRoundTrip`（:120-137）/`testNormalLoginHasNoFlagInDaoCacheRoundTrip`（:139-152）。
4. **Dao-cache 静默 no-op 陷阱已被两处入口补偿**：密码/信道路径 `completeLogin` 先 `saveSession`（落 NopAuthSession 行，LoginServiceImpl:494）后 `saveUserContextAsync`（:504）；OAuth 路径 `completeRestricted` 先 `ensureSessionRow`（幂等直插，保留 IdP sessionId，MfaLoginPolicyServiceImpl:133,:140-163）再持久化。钉定：`testOAuthRestrictedPathPreservesAttrsAndSurvivesDaoCache`（:177-210，含 attrs 保留专项断言）与 `testCompleteRestrictedWithoutSessionIdIsSafe`（:226-235）。

### D-4 跨入口判定不一致 — **未探查到**（同一用户同一策略 → 同一裁定）

| 入口 | 判定载体 | 证据 |
|---|---|---|
| 密码登录（loginAsync） | 副本 B `checkMfaRequired(user, loginType, headers)` | LoginServiceImpl:380（headers=真实值） |
| 信道/扫码（createSessionForUserAsync） | 副本 B `checkMfaRequired(user, loginType, null)` | LoginServiceImpl:417（headers=null——信道结构性不豁免，§6.1 结论 3；受限分支 :419-421 与 challenge 分支 :423-428 均接入） |
| OAuth/SSO | 副本 A `checkMfaForUserName(userName, loginType)` 经 `IMfaLoginPolicyService` SPI | OAuthLoginServiceImpl:96-102（唯一调用点；三分支：null→直存 / restricted→completeRestricted / challenge→实现方抛 ERR_AUTH_MFA_REQUIRED） |

一致性论证：(a) 三入口共用 `RoleMfaPolicyEvaluator.evaluateForUser` + `RoleMfaPolicyEvaluator.factorLevel` + `MfaChallengeHelper.createLoginChallenge`（三个收敛点无分叉）；(b) 副本 A/B 六分支条件逐字等价（见 §三 同构表）；(c) 唯一语义差 = 可信设备豁免分支仅副本 B 有——信道/OAuth 无 headers 结构性不可达，设计 §6.6 裁定 4 显式不同步 + 回归断言 `TestTrustedDeviceE2E.testOAuthCopyStillCreatesChallengeDespiteTrustedRow`（:441）钉定"有未过期可信行的用户经 OAuth 仍建 challenge"；(d) 租户上下文差（A 包 `runWithTenant(user.tenantId)` :86，B 用环境上下文）——涉及的三个实体（user/setting/policy）均 no-tenant（orm.xml:34/:1062/:1291），读取无租户语义，等价；(e) 无本地用户映射 = 空策略放行（A :82-84）与密码路径"认证成功必有用户"（B 侧 :316/:345 保证）语义对齐。钉定测试：`TestMfaRestrictedDaoCache` OAuth 三路径（:156-224：no-policy 放行 / 达标抛 REQUIRED 含三 errorParams / 不达标 restricted）+ `TestMfaRestrictedLoginE2E`（:186-212 密码路径同矩阵）+ `TestWebAuthnMfaAdvancedE2E:326-365`（level-3 策略双态）。mfaVerify 场景校验归 D2（charter 声明），本维度不重复计数。

### D-5（charter D-4 关联面）saveMfaPolicy/removeMfaPolicy 权限 — **未探查到**非 admin 可变更面

1. **运行时 requireAdmin 双保险**：两动作首行 `requireAdmin(context)`（NopAuthRoleBizModel:168/:205 → :216-227）——null 上下文抛 `ERR_AUTH_USER_NOT_LOGIN`，roles 需含 `ROLE_ADMIN` 或 `ROLE_NOP_ADMIN`。钉定 `testNonAdminSaveAndRemoveRejected`（:245-264，行未被触碰）。
2. **GraphQL 权限层同向门禁**：两动作无 @Auth → reflection 构建缺省 `publicAccess=false` + permission `NopAuthRole:mutation|NopAuthRole:saveMfaPolicy`（ReflectionBizModelBuilder:338-341）→ 非登录/无权限在 `GraphQLActionAuthChecker.checkAuth`（:90-115）即被拒——运行时 requireAdmin 为第二道（与 `resetUserMfa` 先例模式一致，设计 §4.3 管理入口裁定）。
3. **requireAdmin 双份实现的同步状态**（charter 提问 shared? duplicated?）：**duplicated**——`NopAuthRoleBizModel:216-227` 与 `NopAuthUserBizModel:1368-1379` 逻辑逐字等价（同两角色常量、同消息模式），漂移点仅 not-login 错误码类（`AuthCoreErrors.ERR_AUTH_USER_NOT_LOGIN` vs `NopAuthErrors.ERR_AUTH_USER_NOT_LOGIN`）与消息文案（"manage role MFA policies" vs "reset user MFA"）——无安全差异，登记 cosmetic。
4. **受限 admin 不能借策略面自我解限**：见 D-1 白名单外溢检查——两动作非白名单 mutation，受限会话被 `ERR_AUTH_MFA_RESTRICTED_SESSION` 拒（审计 `mfa-restricted-rejected` 落 NopAuthOpLog，checker :126）。
5. **审计齐备**：save（created/updated 分事件）/remove 经 `IAuditService.saveAudit`（:194-196/:210-211 → :230-252，userName 非空列设置——W13 教训履行）。

---

## 三、路由项 2：同构副本逐块核对表（copy A vs copy B）

> Copy A = `MfaLoginPolicyServiceImpl.checkMfaForUserName`（nop-auth-service/mfa/MfaLoginPolicyServiceImpl.java:73-115）
> Copy B = `LoginServiceImpl.checkMfaRequired`（nop-auth-service/login/LoginServiceImpl.java:1012-1055）
> 判定口径：**同步** = 条件/顺序/错误码逐字等价；**裁定漂移** = 设计登记在案的显式不同步；**漂移** = 未登记差异。

| # | 逻辑块 | Copy A 锚点 | Copy B 锚点 | 判定 |
|---|---|---|---|---|
| 0 | 方法载体/入参 | `(userName, loginType)`，内部解析本地用户 :82-84（`findFirstByExample`；USER_NAME 全局唯一键 orm.xml:176 无歧义） | `(NopAuthUser user, loginType, requestHeaders)`，调用方已认证 | 同步（substrate 裁定：OAuth 零依赖边，设计 §4.6 裁定 5） |
| 1 | 分支 1 全局开关 | :75-76 `!CFG_AUTH_MFA_ENABLED.get() → null` | :1014-1015 同 | **同步**（逐字） |
| 2 | 分支 1b store 未装配 | :77-78 `mfaChallengeStore == null → null` | :1016-1017 同 | **同步**（逐字） |
| 3 | 策略评估 | :88 `evaluateForUser(user.getUserId())`（evaluator 非 @Nullable 注入 :69-70，无回退） | :1020-1021 `evaluator == null ? NONE : evaluateForUser(...)`（@Nullable 注入 :190-192 + NONE 回退） | **漂移（cosmetic，未登记）** → D4-4；生产 bean 共存（auth-service.beans.xml:134）部署面等价 |
| 4 | setting 装载 | :89 `loadMfaSetting`（:171-175 getEntityById） | :1022 + :1068-1072 同实现 | **同步** |
| 5 | 第三态（矩阵第 4/5 行） | :90-96 `policy>0 && (!mfaEnabled \|\| factorLevel < maxLevel) → MfaLoginDecision.restricted()`（mfaEnabled 三条件 :91-92 与 B 逐字同） | :1025-1031 `MfaChallengeDecision.restricted()`（条件文本逐字同） | **同步**（语义逐字等价；决策载体类不同 = SPI 契约） |
| 6 | 一期分支 2（setting/mfaType） | :97-98（null/!enabled→null）+ :100-101（mfaType 空→null） | :1033-1034 + :1036-1037 | **同步**（逐字） |
| 7 | 因子等同（PHONE_SMS+sms） | :102-103 | :1039-1040 | **同步**（逐字；OAuth loginType=SSO 结构性不可达该分支，两副本一致保留） |
| 8 | 可信设备豁免 | **无分支** | :1042-1049（headers 非空 && 密码类 && `policy.isAllowTrustedDevice()` && manager 非空 → 指纹 → isExempted → null） | **裁定漂移（登记在案）**——设计 §6.6 裁定 4：信道路径无 headers 结构性不可达，同步 = 永不可达死代码；回归断言 `TestTrustedDeviceE2E:441` 履行同步义务 |
| 9 | challenge 创建 | :108-109 `MfaChallengeHelper.createLoginChallenge(store, userId, mfaType, loginType, tenantId, setting.getPhone())` | :1052-1053 同 helper 同参序 | **同步**（W14 触点①③收敛履行，设计 §5.3.6 裁定 6） |
| 10 | challenge 异常表达 | :110-113 实现内 `throw ERR_AUTH_MFA_REQUIRED`（ARG_CHALLENGE_TOKEN/ARG_MFA_TYPE/ARG_LOGIN_TYPE 三参） | 调用方抛：loginAsync :386-391 / createSessionForUserAsync :423-428（同码同三参） | **裁定漂移（SPI 契约）**——实现方抛出使 nop-auth-sso 零依赖边（§4.6 裁定 5）；错误码与 errorParams 逐字一致，`TestMfaRestrictedDaoCache:168-173` 钉定 |
| 11 | 租户上下文 | :86 `runWithTenant(user.getTenantId())` 包全部 | 无 wrap（loginAsync 环境上下文） | 同步（等价）——涉及实体均 no-tenant（user :34 / setting :1062 / policy :1291），租户上下文不影响任何读 |

**路由项 2 结论**：**当前同步**。8 个共享逻辑块（#1/2/4/5/6/7/9 及 #11 等价）无未登记漂移；#0/#8/#10 为设计登记在案的裁定漂移（substrate/豁免/异常位置）；**唯一未登记差异 = #3 evaluator null 防御不对称（D4-4，cosmetic）**，建议 Phase 3 裁定时与"受控重复"永久化/收敛方案一并处置（连同 requireAdmin 双份实现的错误码类漂移）。

---

## 四、回归锚点裁决（D4 所有权）

| 锚点 | 判定 | 证据 |
|---|---|---|
| **ERR_AUTH_MFA_REQUIRED 异常表达**（设计 §二 矩阵行 2，D4/D7 共享） | **PASS** | 错误码定义未触碰（NopAuthErrors:88-89，三 errorParams 契约原样）；受限路径是成功登录非异常（completeLogin restricted 变体）；三处抛点（loginAsync :386-389 / createSessionForUserAsync :423-426 / copy A :110-113）同码同参；新错误码 4 个独立（:135-157）不混用一期编码 |
| **completeLogin 分界**（矩阵行 3，D4/D7 共享） | **PASS** | 受限签发经 completeLogin 六参变体（:477-506）：标志在 `saveSession`（:494）与 `saveUserContextAsync`（:504）**之前**写入（:487-492"先设后存"）；resetFailCount/notifyHook 差异裁决保持（loginAsync 受限=true/true :384；信道受限=false/false :421；一期三调用点经五参重载 :461-465 零变化）；受限签发审计 `mfa-restricted-login`（:508-526，operation=LoginApi__login 与 `@BizMutation("login")` :119 注册名一致） |
| **双零介入**（nop.auth.operation-mfa.enabled 缺省 false + 无策略行 = 一期行为；D4/D5 共享） | **PASS** | 开关缺省 false（NopAuthConfigs:130-131）；无策略行 → evaluator NONE（maxLevel=0）→ 第三态不可达（:1025 要求 `>0`）→ 六分支退化为逐字节一期路径；@MfaRequired 循环被 enabled=false 短路（checker :130-131）；受限分支仅在存量受限会话出现而无策略部署不可产生。钉定：`testUnrestrictedSessionNotAffected`（:275-282）+ `TestRoleMfaPolicy.testNoPolicyRowsYieldsNone`（:114-122）+ `TestMfaRestrictedDaoCache.testOAuthGlobalSwitchOffBypass`（:212-224，总闸 off = 一期旁路） |
| **路由项 2 evidence（两副本当前同步状态）** | **PASS（当前同步）** | 见 §三 逐块表：8 共享块等价、3 处登记裁定漂移、1 处 cosmetic 未登记漂移（D4-4，供 Phase 3 裁定输入） |

---

## 五、覆盖率声明

**已覆盖（live 逐行核对）**：
- `RoleMfaPolicyEvaluator.java` 全文（factorLevel 表 :54-68 / evaluateForUser :74-81 / evaluateForRoles max+AND :87-104 / delFlag :96-97,:107-109 / 角色快照 :116-132）+ `RoleMfaPolicy.java` 全文（NONE 语义）。
- `MfaLoginPolicyServiceImpl.java` 全文（checkMfaForUserName :73-115 / completeRestricted :118-137 / ensureSessionRow :140-163 / getUserByUserName :165-169）与 `LoginServiceImpl` 判定链全段（loginAsync :302-396 / createSessionForUserAsync :403-443 / completeLogin 双变体 :461-534 / checkMfaRequired :983-1103 / MfaChallengeDecision :1078-1103 / buildUserContext :1116+ / sendMfaCode :786-859）——逐块对照。
- `OperationMfaCheckerImpl.java` 全文（白名单 :95-103 八项 / 受限分支前置 :121-128 / 票核验 :147-159 / challenge 创建 :161-179）。
- 受限拦截 executor 触点：`GraphQLExecutor.checkOperationMfa`（:186-239，受限分支 :210-224 + isPublicAccess :237-239）；元数据缺省 auth 构建 `ReflectionBizModelBuilder:335-341` + getActionName :294-302（Async 剥缀核对）；`GraphQLActionAuthChecker` 全文（isAllowAccess :118-145）。
- Dao-cache 双触点：`DaoUserContextCache` 全文（读 :48-85 / 写 :103-138）+ `DaoLoginSessionStore` 全文（saveSession :51-70）+ `UserContextImpl.serializeToJson:48-74`/`setMfaRestricted:314-317` + `AbstractLoginService.getUserContextAsync/refresh 链 :48-84`。
- 白名单 8 项动作本体：bindMfa（含 requireChannelProof :1075-1132 + 通道解析 :1139-1150）/confirmMfa（策略校验 :636-645）/confirmWebauthnRegistration（:450-522）/unbindMfa（:677-707 + webauthn ceremony :720-729）/getMfaStatus(:770)/verifyChannelProof（LoginApiBizModel :383-475）/logout/refreshToken（:150-157 + buildLoginResult :544-578 受限标志 :567-571）。
- `NopAuthRoleBizModel` saveMfaPolicy/removeMfaPolicy/requireAdmin/auditPolicyChange（:149-253）+ `NopAuthUserBizModel.requireAdmin`（:1365-1379）对照。
- nop-auth-sso 全模块调用面 grep（`checkMfaForUserName`/`IMfaLoginPolicyService` 唯一调用点 OAuthLoginServiceImpl:96-102；SsoLoginWebService 仅 ssoLogoutAsync 无登录面）。
- allowTrustedDevice 消费点全仓 grep：唯一生产消费 = LoginServiceImpl:1043（豁免分支 AND 合并结果）；evaluator 产出 :100-101；测试消费 TestRoleMfaPolicy:120-144。
- ORM 源：NopAuthRoleMfaPolicy 实体（orm.xml:1289-1328，no-tenant/useLogicalDelete/delFlag/roleId PK）、NopAuthUser USER_NAME 唯一键（:176）、setting/user/role no-tenant 标签。
- beans 装配：auth-service.beans.xml:125-141（nopOperationMfaChecker/nopRoleMfaPolicyEvaluator/nopMfaLoginPolicyService，ioc:default）。
- 测试证据（读源核对，未运行）：TestRoleMfaPolicy / TestMfaRestrictedSessionE2E / TestMfaRestrictedDaoCache / TestMfaRestrictedLoginE2E / TestChannelProofEmailE2E / TestWebAuthnMfaAdvancedE2E / TestTrustedDeviceE2E（OAuth 副本回归断言 :441）。

**未覆盖/明示边界**：
- 未运行任何 maven 构建/测试（charter 纪律：research only；G3 全量重跑归 D7 所有权）。
- mfaVerify 场景校验（B3/B4 票挪用）归 D2 所有权（charter §二 D2 显式划分），本报告仅覆盖策略判定一致性。
- `nop-credential` C1b 标注面、@MfaRequired 元数据传播链四触点深查归 D5；store 家族（challenge/sms/email 三实现原子性）归 D2/D7。
- 一期登录级 E2E 全家族行为重跑核对归 D7（本维度以 git-untracked 的 live 源码 + 既有测试断言文本为证据）。
- GraphQL fragment 顶层选择的执行器崩溃路径（D4-5）仅静态核对 parser（GraphQLDocumentParser:716-723 允许）与三处强转点，未构造运行时复现（无构建纪律约束下的合理边界；结论不依赖运行时行为——崩溃即 fail-closed）。
