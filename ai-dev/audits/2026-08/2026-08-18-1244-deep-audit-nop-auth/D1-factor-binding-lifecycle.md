# D1 审计报告：因子强度与绑定/登记生命周期

> **Dimension**: D1（因子强度与绑定/登记生命周期）
> **Executor**: fresh 独立对抗审计子 agent（task: D1-factor-binding-lifecycle，本 session）
> **Date**: 2026-08-18
> **Charter**: `ai-dev/audits/2026-08/2026-08-18-1244-deep-audit-nop-auth/audit-charter.md` §二 D1
> **Design baseline**: `ai-dev/design/nop-auth/02-mfa-phase2-design.md`（§3.2/§4.3/§4.6/§5.3.0-5.3.7/§二 矩阵行 5）
> **Mode**: RESEARCH + REPORT ONLY（未修改任何产品/测试代码；未运行 maven 构建）
> **结论速览**: 无 P0/P1。P2 ×2（其一路由 D2 所有权）、P3 ×5。四项对抗用例（A1-A4）均**未探查到**可利用攻击路径。回归锚点（明文边界）**PASS**。

---

## 一、Findings 总表

| ID | Severity | file:line anchor | description | suggested fix direction |
|---|---|---|---|---|
| D1-1 | P2 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:628-631`（confirmMfa）/:695-698`（unbindMfa） | 绑定/解绑级 TOTP 因子验证**无尝试上限与失败计数**：SMS/EMAIL 有 store 内部 max-attempts（5）、webauthn 有 `incrWebauthnFailCountOrDiscard`（:984-991）、登录级有 `incrFailCountOrDiscard`（5 次），唯独 confirmMfa/unbindMfa 的 totp 分支（经 `MfaFactorVerifier.verify` :144-159，无计数）失败仅抛 MFA_FAIL 可无限重试。6 位 TOTP + skew=1（约 3 个有效码/窗口）在线穷举期望 ~3×10⁵ 次尝试，无速率限制下数分钟~小时级可命中。前置条件为被盗会话 token（或被盗 bindToken，后者仅能作用于本人 pending 行，自限），故为纵深防御缺陷非直接认证突破。攻击价值：会话劫持者穷举 unbindMfa 成功 → 解绑 → 正常会话 bindMfa（无 proof 前置）重绑自己的验证器 → 持久接管（绕过"换绑必经 disabled 态 + 因子验证"的防线）。 | confirmMfa/unbindMfa 失败分支引入按 setting 维度失败计数（超限作废 pending bindToken / 解绑冷却窗口），或复用 challenge-store incrFailCount 语义；与登录级 5 次上限对齐 |
| D1-2 | P2（**路由 D2 所有权**） | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:548-582` | `mfaVerifyAsync`（登录级验证端点）peek challenge 后**不校验 `scene==login`（或 null）也不校验 `verifiedAt==null`**——任意场景 challenge（operation 票/webauthn-register/webauthn-unbind）可送入登录级验证。当前实际不可利用（分因子防重放兜底成立，见 A2 分析：channel-proof 票 mfaType=null 被 :606-612 fail-closed；TOTP lastVerifiedWindow 推进 / SMS·EMAIL 原子消费 / webauthn signCount 单调推进使跨场景重放失败），但这是"依赖因子层防重放而非场景隔离"的结构性弱点，正是 charter D2 B3/B4 对抗用例的正式探查面。按章程 §四裁决三态登记为 **successor 所有权（D2）**，本维度不重复计数。 | `mfaVerifyAsync` 在 peek 后补 `scene==SCENE_LOGIN||null` + `verifiedAt==null` 显式校验（一行防御，使场景隔离不依赖因子防重放兜底） |
| D1-3 | P3 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java:418-420`/:436-438 | `verifyChannelProof` 失败路径（MISMATCH→MFA_FAIL）无审计事件——W13 裁定 8（设计 §4.6.8）声明事件面 `mfa:channel-proof-sent|verified|fail`，live 仅 `sent`（NopAuthUserBizModel:1179-1194）与 `verified`（:473）落库，`fail` 缺失。防滥用审计盲区（对 proof 码穷举不可观测）。 | 两处 MISMATCH 分支抛错前补 `auditChannelProof(..., false)` |
| D1-4 | P3 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:1015-1023`/:502-513 | 软删除 credential 的 credentialId 复用注册冲突未归一：`NopAuthMfaCredential` `useLogicalDelete=true`（`nop-auth/model/nop-auth.orm.xml:1330-1336`），removeWebauthnCredential 软删后行仍占全局唯一键 `UK_NOP_AUTH_MFA_CREDENTIAL_CRED`（:1430 附近）；`findCredentialByCredentialId` 走 ORM example 查询（默认滤 delFlag）查不到已软删行 → 重复注册预检（:492）通过 → `saveEntity` 撞 DB 唯一约束抛未归一 SQL 异常（非 MFA_FAIL 语义）。健壮性问题（用户体验/错误分类），非安全问题。 | 唯一约束违例 catch 归一为 MFA_FAIL"duplicate-credential"，或预检查询显式含软删行 |
| D1-5 | P3（记录性守护项） | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:448-522` | `confirmWebauthnRegistration` 无 `roleMfaPolicyEvaluator` 防降级校验（对照 confirmMfa :636-645）。**当前结构性安全**：setting 复核限定 pending+webauthn（:471-473），webauthn=3 为 factorLevel 表上限，不可能弱于任何 minMfaLevel（saveMfaPolicy 校验 1-3）→ 不存在可降级空间。但安全性依赖"webauthn 恒为最强因子"隐式不变式——未来引入 level-4 因子时此处即成降级缺口。 | 加与 confirmMfa 同构的策略校验（防御性一行），或在 factorLevel 表处登记"新增更强因子必须同步补 confirmWebauthnRegistration 策略校验"的守护义务 |
| D1-6 | P3 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:1350-1355` | `maskPhone` 对长度 ≤4 的手机号**原样返回**（全量暴露于 getMfaStatus :782 / proof 错误提示 :1131 / 审计 :1129 / 限流错误 :1166/:1170）。边界条件：实际 phone 域长度均 >4，触发面极窄。 | 长度 ≤4 时全掩码（或仅保留尾 2 位） |
| D1-7 | P3（一致性说明） | `nop-auth/nop-auth-api/src/main/java/io/nop/auth/api/beans/NopAuthMfaSettingOutputBean.java`（propId 6 `phone`，生成物，锚源 `nop-auth/model/nop-auth.orm.xml:1081-1083`） | biz 面 getMfaStatus 的 phone 脱敏（:782），但通用 CRUD 面（`NopAuthMfaSettingBizModel` extends CrudBizModel，permission 门禁）的生成 OutputBean 返回**未脱敏** setting.phone（ORM phone 列无 masked 标签）。该 phone 为 bindSms 时从 user.phone 拷贝的副本，与 NopAuthUser 实体管理面同级暴露（既有惯例），非新增暴露等级，登记为一致性说明。 | 如需收敛：ORM phone 列补 masked 标签（plan-first，ORM 源变更）或 biz 面出参统一脱敏 |

**无 P0/P1**：全部对抗用例（A1-A4）未发现可利用的认证突破或秘密泄漏（逐项证据见下）。

---

## 二、对抗用例逐项裁决

### A1 跨用户 bindToken 冒用 — **未探查到**

**证据链**（`NopAuthUserBizModel.java`）：

1. `confirmMfa` 以**当前会话 userId** 定位 setting 行（:607 `requireCurrentUserId(context)` → :1357-1363 从 `IUserContext` 取，非客户端参数；:613 `settingDao.getEntityById(userId)`——setting 主键即 userId）。
2. bindToken 比对对象是**本人 setting 行内**的 `bindToken`（:616 `!bindToken.equals(setting.getBindToken())`）——用户 B 提交用户 A 的 bindToken 时，比对的永远是 B 自己行内的 token（每 bindMfa 重新 `generateUUID()`，:272），不匹配即 `ERR_AUTH_MFA_BIND_EXPIRED`；B 无 setting 行则 setting==null 同样拒绝（:616-619）。
3. bindToken 仅经 `MfaBindResult`（:319/:339/:377）返回给发起绑定的本人会话；DB 侧 `not-pub`（orm.xml:1078-1080），不经任何跨用户查询面外泄。
4. 状态机门禁：confirmMfa 要求 `status==pending`（:617）——enabled/disabled 行不可确认。
5. webauthn 对称路径同样安全：`confirmWebauthnRegistration` 校验 `userId.equals(c.getUserId())`（:463，challenge 创建时钉定 `user.getUserId()` :414-415）+ `payloadSessionId.equals(uc.getSessionId())`（:465，防跨会话搬运）+ setting 复核 pending+webauthn（:470-474）。
6. 无 session 绑定（bindToken 可跨设备确认）属同用户便利性设计，非跨用户漏洞。

### A2 pending 状态旁路 / bindToken 重放 / pending 过登录检查 — **未探查到**

**证据链**：

1. **非 pending 不可 confirm**：:616-619 三重条件（行存在 + token 匹配 + status==pending）；:622-624 过期判定（`isBindExpired` :1290-1297，复用 updateTime + bind-expire-seconds 缺省 300s，`NopAuthConfigs.java:109-111`）。
2. **confirm 后重放**：成功即 `setting.setBindToken(null)` + `status=enabled`（:648-649，同事务 tracked entity flush）；再次 confirm 同 token → :616 token 不匹配（null）→ BIND_EXPIRED。重新 bindMfa 生成新 UUID 覆盖（upsertPending :570-593），旧 token 永久失效。
3. **pending 不可过登录检查**：`LoginServiceImpl.checkMfaRequired`（:1012-1055）——一期分支 :1033 `!MFA_STATUS_ENABLED.equals(setting.getStatus())` → return null（pending 不创建 challenge、不触发验证，等同"MFA 未启用"的一期语义）；策略分支 :1025-1031 把 pending 判为 `!mfaEnabled` → 受限会话（`MfaChallengeDecision.restricted()` :1096-1098，不建 challenge）。`mfaVerifyAsync` 复核 :567 同样要求 enabled，否则作废 challenge。pending 期间不存在任何"以 pending 状态通过第二因子验证"的路径。
4. **pending 不可解绑**：unbindMfa :686-688 要求 enabled。
5. **换绑必经 disabled 态**：bindMfa 对 enabled 抛 `ERR_AUTH_MFA_ALREADY_ENABLED`（:268-270）→ 旧 enabled secret 在 pending 期间不被触碰（upsertPending 仅在 status != enabled 时可达）——攻击者无法把受害者从 enabled 直接打成 pending。
6. **并发 confirmMfa 双花**：SMS/EMAIL 码 store 原子消费（`DbEmailCodeStore.verify` :96-110 条件 DELETE affected-row 判定；DbSmsCodeStore 同构）；TOTP 双并发同窗口码第二落点被 `lastVerifiedWindow` 推进拦截（MfaFactorVerifier :149-156）；webauthn 双落点被 markVerified 条件 UPDATE（`DbMfaChallengeStore.markVerified` :151-162 `VERIFIED_AT IS NULL` + affected-row）与 consume 条件 DELETE（:128-148）拦截。残余竞态（两并发 confirmMfa 均读到 pending 且均过 TOTP 验证——理论上不同窗口码可先后通过）后果仅为恢复码二次生成（旧码删除新码覆盖，后写胜出），双方均为持有效 bindToken+有效码的本人，无跨用户安全影响。
7. **受限会话无 proof 直接 bindMfa**（charter A2 关联探查）：bindMfa :253-256 `uc.isMfaRestricted()` → `requireChannelProof`（:1075-1132）——票核验四条件（scene=channel-proof :1080 + verifiedAt 非空 :1081 + **userId 绑定** :1081 + 原子 consume :1082），无有效票即发码+抛 `CHANNEL_PROOF_REQUIRED`，**不静默放行**；无效票按"无票"处理重发码（:1085）。票窗口由 store 层 peek 强制（`DbMfaChallengeStore.peek` :98-104：`verifiedAt + op-ticket-expire-seconds`（缺省 60s，NopAuthConfigs:133-135）外即删行返 null）——challenge TTL 300s 不能延长 proof 票寿命。

### A3 登记通道枚举/混淆 — **未探查到**

**证据链**：

1. **channel 参数限定已登记集合**：两侧同一规则——`LoginApiBizModel.resolveProofChannel`（:452-463）与 `NopAuthUserBizModel.resolveProofChannel`（:1139-1150）：缺省 = phone 优先、phone 缺失回退 email；显式值必须属于 `phone|email` **且已登记**，非法/未登记值显式抛 `ERR_AUTH_INVALID_LOGIN_REQUEST`（:461-462/:1148-1149），不静默回退。攻击者无法指定任意通道。
2. **proof key 双重隔离**：phone=`proof:{userId}`（SmsCodeStore）/ email=`proof-email:{userId}`（EmailCodeStore）——不同 store 实例 + 不同前缀（`NopAuthConstants.java:57-64`），与登录码 `login:{phone}`、因子码 `mfa:{userId}`/`mfa-email:{userId}` 四键互不通用。发码侧（requireChannelProof :1127/:1112）与核验侧（verifyChannelProof :431-432/:413-414）按**同一通道解析结果**选用 key——channel=phone 的码不能在 email key 上验证（反之亦然），MISMATCH 落 store 内部失败计数（DbEmailCodeStore :101-110）。
3. **通道混淆攻击面分析**：双通道用户的 proof 票不区分通道（`issueChannelProofTicket` :466-475 票 payload 不含 channel）——但两通道均为**同一用户**的登记联系方式，证明任一即证明"密码 + 登记通道"持有，语义等价无混淆风险；票 userId 绑定（challenge 创建钉定 `userContext.getUserId()` :467，核验 :1081）杜绝跨用户挪用。
4. **脱敏不泄漏完整联系方式**：`ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED` 的 `{channel}` 参数全部为脱敏值——phone 经 `maskPhone`（后 4 位，:1131/:1166/:1170）email 经 `maskEmail`（本地部分前 2 位 + `***` + @域名，:1116/:1214/:1218/:1232，形态对齐 W15 裁定 3）；审计事件 `auditChannelProofSent` 仅记 maskedTarget（:1114/:1129/:1191）；错误消息文案不含完整联系方式（"user has no phone number"/"user has no email address" 仅暴露有无性给本人，:327/:355）。边界残留见 D1-6（≤4 位号码）。
5. **发码轰炸面**：proof 码仅经受限会话 bindMfa 触发（正常会话不进 requireChannelProof :254），phone 通道 60s 间隔 + 日上限 20（checkProofRateLimit :1153-1173，复用 sms-code 配置组）、email 通道三层（间隔/email 日限/IP 日限，checkEmailRateLimit :1201-1235）+ store 层 max-attempts——受限攻击者对受害者登记通道的轰炸被限流封顶（设计声明口径内）。

### A4 敏感字段暴露 — **未探查到**

逐响应面核查（全部 DTO/masked 标签 live 核对）：

| 响应面 | 敏感字段核查 | 锚点 |
|---|---|---|
| `getMfaStatus` → `MfaStatusResult` | 仅 mfaType/status/phone（脱敏后 4 位）；**无 secret/bindToken/email** | NopAuthUserBizModel:771-784 + dto/MfaStatusResult.java（3 字段） |
| `listWebauthnCredentials` → `NopAuthMfaCredentialOutputBean` | 生成 Bean **无 credentialId/publicKey 字段**（propId 3/4 缺席，ORM not-pub/masked 标签生效）；仅 sid/userId/signCount/transports/name/status/lastUsedAt/createTime | NopAuthUserBizModel:797-813 + NopAuthMfaCredentialOutputBean.java |
| `MfaWebauthnBeginResult` | challengeToken + requestOptions（challenge=协议公开密码学挑战；allowCredentials=**本人** credentialIds） | dto/MfaWebauthnBeginResult.java + WebAuthnRequestOptions.java:24-32（6 字段无秘密） |
| `MfaBindResult` | provisioningUri **含明文 base32 secret——设计允许的一次性返回**（仅 totp 绑定发起瞬间返回给本人会话；confirmMfa 响应仅恢复码）；bindToken/challengeToken 仅本人可用（A1 已证）；creationOptions 含本人 userId/userName/excludeCredentials（本人数据） | NopAuthUserBizModel:301-321 + dto/MfaBindResult.java |
| `confirmMfa`/`confirmWebauthnRegistration` 响应 | 明文恢复码一次性返回（设计 §3.4 契约）；无 secret/publicKey | :656/:521 |
| ORM 明文边界 | setting.secret `masked,var,not-pub`（AESTextCipher 密文，orm.xml:1071-1073）/ bindToken `var,not-pub`（:1078-1080）/ credentialId `var,not-pub`（:1341-1342）/ publicKey `masked,var,not-pub`（:1345-1346）/ codeHash `masked,var,not-pub`（:1142）/ email code `masked,not-pub`（:1261） | nop-auth/model/nop-auth.orm.xml |
| 审计日志 | proof 审计仅 masked 目标；webauthn 审计记 credentialId（协议公开标识符，非秘密） | :1114/:1129/:1052 |

残留一致性说明见 D1-7（通用 CRUD OutputBean 的 phone 未脱敏，permission 门禁 + 与 NopAuthUser 管理面同级）。

---

## 三、附加裁定项（charter D1 必查）

### confirmMfa 防降级 — **成立（webauthn 路径结构性安全，见 D1-5 守护注记）**

- confirmMfa :636-645：`roleMfaPolicyEvaluator.evaluateForUser(userId)` → `factorLevel(setting.getMfaType()) < policy.getMaxLevel()` → `ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK`。evaluator 角色口径 = buildUserContext 快照 + 隐式 user 角色及继承链（RoleMfaPolicyEvaluator:116-132，"策略挂 user 角色=全员强制"显式用法）。换绑必经 disabled 态（A2.5）+ 策略校验双保险：已启用强因子用户不可能被覆盖为弱因子（bindMfa ALREADY_ENABLED 拒绝），disabled 后重绑弱因子在 confirm 时被策略拒绝。
- 解绑不受策略限制（设计 §4.1 结论 6 裁定：用户自主权 + 下次登录受限兜底）——与设计一致，非缺陷。

### factorLevel 全量表 — **完整且与设计一致**

`RoleMfaPolicyEvaluator.factorLevel`（:54-68）：

| mfaType | level | 设计基准核对 |
|---|---|---|
| sms | 1 | §4.1 结论 3 ✓ |
| email | 1 | §5.3.1"email=1" ✓（W15 常量落位注释 :59） |
| totp | 2 | ✓ |
| webauthn | 3 | ✓ |
| null/空/未知值 | 0（fail-closed） | §4.3"未知 mfaType 强度视为 0"✓（default 分支 :65-66） |

- **recovery 不在表内——正确**：恢复码不是 mfaType 取值域成员（绑定状态机/白名单/dict 均无 recovery；恢复码是因子无关的登录恢复通道，设计 §5.3.5），不存在"recovery 的 factorLevel"概念。可绑定因子恰为白名单 4 值（`SUPPORTED_MFA_TYPES` :121），与 dict 4 条目（totp/sms/webauthn/email，`nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/dict/auth/mfa-type.dict.yaml`）一一对应，与 factorLevel 表 4 分支一一对应——无孤儿值、无遗漏值。
- EMAIL level=1 与设计 §5.3.1/§4.1"OTP 拥有通道类"一致（email 与 sms 同级，非独立强度带）——**无偏差**。

---

## 四、Regression Anchor（D1 所有）：明文边界（设计 §二 矩阵行 5）— **PASS**

| 契约子项 | live 证据 | 判定 |
|---|---|---|
| TOTP secret 加密落库 | bindTotp :308-309 `totpAuthenticator.getCipher().encrypt(base32Secret)`（AESTextCipher，orm.xml:1071-1073 comment + `masked,var,not-pub`）；解密仅在 `MfaFactorVerifier`/TOTPAuthenticator 验证路径内发生 | ✓ |
| 恢复码 BCrypt 加盐 | regenerateRecoveryCodes :1303-1319 `salt:hash`（`passwordEncoder.encodePassword`）存 codeHash；明文仅 confirmMfa/generateRecoveryCodes 响应一次性返回；codeHash 列 `masked,var,not-pub`（orm.xml:1142） | ✓ |
| WebAuthn publicKey masked 不展示 | orm.xml:1345-1346 `masked,var,not-pub`；listWebauthnCredentials 不含（A4 表）；biz 面手动构造 OutputBean（:801-810）无该字段 | ✓ |
| provisioning URI 一次性明文返回 | bindTotp :312-318 仅绑定发起瞬间返回本人；getMfaStatus/confirmMfa 响应无 secret（A4 表） | ✓ |
| email/sms 码短 TTL 明文 DB（W8 裁决沿用） | `nop_auth_email_code.code` `masked,var,not-pub`（orm.xml:1261）；瞬态数据 + 一次性原子消费（DbEmailCodeStore :96-110） | ✓ |
| 通道 OTP 不落明文日志/审计 | proof 审计仅 maskedTarget（:1114/:1129）；限流错误仅脱敏值（:1166/:1214） | ✓（边界残留 D1-6/D1-7 为 P3） |

---

## 五、覆盖率声明

### 已探查（live 代码逐行核对）

- **NopAuthUserBizModel 全文**（1479 行）：bindMfa/bindTotp/bindSms/bindEmail/bindWebauthn 分派与状态机、upsertPending 覆盖写语义、confirmMfa 全校验链、unbindMfa（含 webauthn ceremony :720-743）、confirmWebauthnRegistration 全校验链、webauthnBeginVerify、getMfaStatus、listWebauthnCredentials/remove/renameWebauthnCredential、requireChannelProof/resolveProofChannel/checkProofRateLimit/checkEmailRateLimit/maskEmail/maskPhone、regenerateRecoveryCodes、resetUserMfa、isBindExpired。
- **LoginApiBizModel** :140-499：webauthnAuthOptions（公开端点的 scene/会话分权 :246-254）、mfaVerifyOperation（对照面）、verifyChannelProof/issueChannelProofTicket/resolveProofChannel、auditChannelProof。
- **LoginServiceImpl**：mfaVerifyAsync/verifySecondFactorAndComplete/verifyRecoveryCodeAndComplete/checkMfaRequired（含第三态与可信设备豁免分支的边界）。
- **MfaFactorVerifier 全文**（五分支 + webauthn 断言 + signCount 条件 UPDATE）。
- **RoleMfaPolicyEvaluator 全文**（factorLevel 表 + 角色口径 + max/AND 合并）。
- **NopAuthConstants 全文**（key 前缀/通道常量）；**NopAuthConfigs** 相关缺省值（bind-expire 300s / max-attempts 5 / op-ticket 60s / challenge 300s）。
- **DbMfaChallengeStore 全文**（peek 票窗口 :98-104 / markVerified 原子性 / consume 条件 DELETE）；**DbEmailCodeStore 全文**（三态 + 失败计数 + 原子消费）。
- **MfaChallengeHelper 全文**；**WebAuthnAuthenticator** verifyRegistration/verifyAssertion/isConfigured（origin/rpId fail-closed 配置）。
- **ORM 源**：NopAuthMfaSetting/NopAuthMfaRecoveryCode/NopAuthMfaCredential/NopAuthEmailCode 的 masked/not-pub 标签与唯一键。
- **DTO**：MfaBindResult/MfaStatusResult/MfaWebauthnBeginResult/NopAuthMfaCredentialOutputBean/NopAuthMfaSettingOutputBean/WebAuthnCreationOptions/WebAuthnRequestOptions 字段面。
- **dict**：mfa-type.dict.yaml 条目面；**OperationMfaCheckerImpl** RESTRICTED_SESSION_WHITELIST（绑定生命周期在受限会话的可达性闭环：bindMfa/confirmMfa/confirmWebauthnRegistration/unbindMfa/getMfaStatus/verifyChannelProof 全在列 :95-103）。

### 未探查 / 限定（及原因）

- **Redis/Local store 实现**（RedisMfaChallengeStore/RedisEmailCodeStore/RedisSmsCodeStore/Local 实现）：仅静态阅读 Db 实现锚定语义；Redis 派生票键 SETNX 原子性与 Local compute 原子性未逐行核对——属 D2（防重放与一次性消费）主责面，D1 仅依赖其"peek 票窗口 + consume 一次性"契约（Db 实现已证）。
- **登录级 mfaVerify 的 TOTP/SMS 码穷举计数**、challenge incrFailCount 超限语义细节、三创建触点 payload 一致性——D2 主责。
- **恢复通道语义**（恢复码重放/reset 窗口旧码复活）——D3 主责；D1 仅核对 BCrypt 存储与一次性返回。
- **受限会话拦截双触点/白名单短路、OAuth 副本、策略合并对抗（多角色 1+3）**——D4/D5 主责；D1 仅消费 whitelist 可达性事实。
- **可信设备**（指纹/豁免/撤销矩阵）——D6 主责；D1 仅核对 confirmMfa/unbindMfa/resetUserMfa 的撤销钩子存在性（:653/:706/:963）。
- **一期 E2E 测试树回归**（G 系用例）——D7 主责；本审计未运行任何 maven 构建/测试（charter 纪律：research only）。
- **GraphQL 序列化引擎对 @JsonInclude/DataBean 的实际输出**：基于字段面静态核对（OutputBean 无字段即无序列化面）；未做运行时序列化快照。
- `MfaLoginPolicyServiceImpl.checkMfaForUserName`（OAuth 同构副本）未逐行展开——D4/D6 主责（D1 关注的 factorLevel 表为其共用静态方法，已核）。
