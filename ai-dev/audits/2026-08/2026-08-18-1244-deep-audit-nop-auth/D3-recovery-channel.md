# D3 恢复通道审计报告（MFA 二期安全审计 A2）

> Dimension: D3 恢复通道（recovery channel）
> Executor: fresh 独立对抗审计子 agent（task: D3-recovery-channel，research + report only，未修改任何产品/测试代码，未运行 maven）
> Date: 2026-08-18
> Charter: `audit-charter.md` §二 D3（对抗用例 C1/C2/C3 + 威胁假设：恢复码穷举无失败计数分界 / 重置窗口期旧码复活 / 管理员重置后旧因子复活）
> 设计基准: `ai-dev/design/nop-auth/02-mfa-phase2-design.md` §5.3.5（恢复码一揽子语义）/ §3.4（操作级不接受恢复码）/ §6.4（恢复码登录不登记可信设备）
> 审计性质: RESEARCH + REPORT ONLY。全部 file:line 锚点为 2026-08-18 live 核对产物。

---

## 一、Findings 总表

| ID | Severity | file:line anchor | description | suggested fix direction |
|---|---|---|---|---|
| D3-F1 | **P1** | `NopAuthUserBizModel.java:941-964`（resetUserMfa）；`:677-707`（unbindMfa）；`:501-517`（confirmWebauthnRegistration）；`MfaFactorVerifier.java:256-266`（findEnabledCredential） | **管理员重置/解绑后 WebAuthn 旧因子复活**。`resetUserMfa` 仅清 setting 字段（secret/mfaType 置 null、status=disabled）+ 删恢复码 + 撤销可信设备，**不删除/禁用 `nop_auth_mfa_credential` 行**；`unbindMfa` 同样不清理 credential 行；`confirmWebauthnRegistration` 对既有 credential 行纯增量（不 purge）。credential 行无 ORM 级联（仅 setting→recoveryCodes 有 cascadeDelete，见 `nop-auth.orm.xml:1121-1127`）。后果：管理员因钥匙丢失/被窃而重置用户 MFA → 用户重新绑定 webauthn（setting 重新 enabled）→ **旧（可能已被攻击者持有的）credential 行仍为 enabled，旧硬件钥匙立即恢复登录能力**——管理员重置的"全部因子作废"语义被打破。TOTP 分支闭合（secret 置 null + status 复核，旧 TOTP 码不可用）。 | `resetUserMfa` 与 `unbindMfa`（webauthn 分支）成功路径删除该用户全部 `NopAuthMfaCredential` 行；或 `confirmWebauthnRegistration` 启用时 purge 既有行（对齐 `regenerateRecoveryCodes` "作废旧码"先例）。补回归断言（现 `TestMfaUserSelfService:426-450` 只断言恢复码删除断 setting 清空，未覆盖 credential）。 |
| D3-F2 | P2 | `LoginServiceImpl.java:686-704`（verifyRecoveryCode 读-改-写）；`NopAuthUserBizModel.java:1303-1319`（delete+insert 非原子） | **恢复码 used 标记非原子（并发双花窗口）**。`verifyRecoveryCode` 流程 = findAllByExample 读 used=0 → BCrypt 匹配 → `code.setUsed(1); dao.updateEntityDirectly(code)` —— **无条件 UPDATE**（无 `WHERE USED=0` 条件）。两个并发 mfaVerify（各自持有一个有效 challenge）提交同一恢复码：双双读到 used=0、双双匹配、双双落 VALID、各自 consume 自己的 challenge 并 completeLogin —— 一次性恢复码被使用两次。同构缺陷：`regenerateRecoveryCodes` 的"先 deleteRecoveryCodes 再 insert"与并发 `verifyRecoveryCode` 读之间存在竞态（旧代次恢复码在重置瞬间仍可被判 VALID——updateEntityDirectly 落在被删行上不影响返回值）。对照本仓库已有原子先例：`DbMfaChallengeStore.markVerified` 条件 UPDATE `WHERE VERIFIED_AT IS NULL`（:151-162）、signCount 条件 UPDATE `WHERE SIGN_COUNT < ?`（`MfaFactorVerifier.java:233-240`）——恢复码 used 恰好缺同款条件写。实际利用门槛：需已知该恢复码（通常是持有者自身），安全增量有限，但违反"一次性消费"显示契约且模式不一致。 | used 置位改条件 UPDATE `... SET USED=1, USED_AT=? WHERE SID=? AND USED=0`（affected=0 → 按 USED/MFA_FAIL 处理）；regenerate 改为事务内原子语义（或按 createTime > 世代标记判定）。 |
| D3-F3 | P2 | `NopAuthMfaRecoveryCodeInputBean.java:43-68`（codeHash/used 可写）；`_NopAuthMfaRecoveryCode.xmeta:30-46`（codeHash published=false 但 insertable/updatable=true）；`NopAuthMfaRecoveryCodeBizModel.java`（生成 CrudBizModel）；`_nop-auth.action-auth.xml:97-107`（query/mutation 权限资源） | **恢复码表暴露通用 CRUD 通道，绕过 @MfaRequired 与专项审计**。`NopAuthMfaRecoveryCodeBizModel`（collect 生成）暴露 save/update/delete/batchDelete；`codeHash`（salt:hash 可自算——算法为公开的 SHA256(salt,code)+BCrypt）、`userId`、`used` 均 insertable/updatable。持 `NopAuthMfaRecoveryCode:mutation` 权限者可：(a) 为任意 userId **植入自算 codeHash 的恢复码** → 直接经 mfaVerify 恢复码分支登录（status→disabled 强制重绑 → 绑定自己的因子完成接管）；(b) 把已用码 `used` 改回 0 **复活**消费过的码。该路径无 @MfaRequired、无 resetUserMfa 式 NopAuthOpLog 专项审计。缓解：仓库内未发现默认角色授权种子（action-auth 仅声明资源，授权在运行时数据），实际默认仅 admin/super-admin 可达——但与 `resetUserMfa`（@MfaRequired :940 + @BizAudit :939 + requireAdmin）的保护基线不一致。 | 恢复码表 CRUD 收紧：codeHash/used/usedAt 改 insertable=false/updatable=false（保留只读管理视图），或在 BizModel 层 override save/update 显式拒绝；至少为该表 mutation 增加专项审计。同族表 `NopAuthMfaSetting`/`NopAuthMfaCredential`（secret/publicKey 可写面）建议 D1/D5 顺带裁定。 |
| D3-F4 | P3 | `LoginServiceImpl.java:655-662` | **USED 与 INVALID 的失败计数分界构成行为 oracle**。两者对外统一 `ERR_AUTH_MFA_FAIL`（文案闭合），但 USED **不 incrFailCount、不消费 challenge**，INVALID 计数且达 max-attempts(5) 后消费。攻击者可对候选码连发 5 次观察 challenge 是否仍存活（第 6 次 peek 报 CHALLENGE_EXPIRED 与否）区分"该码曾有效（已用）"与"从未有效"——失败计数副作用泄漏"曾有效"信息，与设计"不暴露'曾有效'"意图相悖。利用成本高（每候选码 5 次探测）、收益低（仅确认旧码有效性），定 P3。 | USED 分支同样 incrFailCount（错误码保持统一 MFA_FAIL），消除 challenge 存活度差异。 |
| D3-F5 | P3 | `NopAuthUserBizModel.java:1331-1335` | **恢复码熵偏低：10 位纯数字**（10^10 ≈ 33.2 bits；10 码并存使单次猜测命中概率 ≈ 10^-9）。RNG 本体无虞（`MathHelper.secureRandom()` → JDK SecureRandom，`MathHelper.java:193-197`），但强度低于业界恢复码惯例（≥16 字符字母数字 / 80+ bits）。当前安全完全依赖外围门禁：challenge max-attempts=5（`NopAuthConfigs.java:93-95`）+ 新 challenge 需重过第一因子（密码）。若未来外围门禁松动（如 max-attempts 调大、challenge 复用），33 bits 不堪一击。 | 升级为 16+ 字符字母数字（Crockford base32 类编码），熵 ≥ 80 bits。 |
| D3-F6 | P3 | `nop-auth.orm.xml:1149-1150`（EXPIRE_AT 列）；`NopAuthUserBizModel.java:1312-1316`（永不写入）；`LoginServiceImpl.java:686-705`（永不校验） | **EXPIRE_AT 死列**。恢复码实体有 EXPIRE_AT 列，但 `regenerateRecoveryCodes` 从不写入、`verifyRecoveryCode` 从不检查——恢复码无任何生命周期上限（仅靠 regenerate/unbind/reset 作废）。设计-实现漂移（列暗示了未实现的生命周期语义）。 | 要么在 regenerate 时写入 `expireAt`（如 365d）并在 verifyRecoveryCode 匹配前校验（过期按 INVALID 计数），要么删列消除漂移。 |
| D3-F7 | watch-only | `LoginServiceImpl.java:566-572`（复核仅查 status，未比对 mfaType） | **登录级 mfaVerifyAsync 复核缺 `challenge.mfaType == setting.mfaType` 比对**（操作级 mfaVerifyOperation 有此比对，设计 §3.3）。unbind→300s 内换绑异型因子后，残留旧 challenge 理论上可被旧因子形态的 store 状态满足（如旧 sms challenge + 仍存活的 `mfa:{userId}` 码）。实际可利用性≈0：sms 码发到受害者手机、totp 用新 secret、webauthn 需私钥——密码-only 攻击者三路皆不可满足，且解绑本身已要求验证当前因子。登记为 watch-only（D2 防重放维度可合并裁定）。 | 低优先：登录级复核补 mfaType 一致性检查（镜像操作级语义），闭合换绑窗口。 |

裁决建议（charter §四三态）：D3-F1 → **successor 所有权**（修复面小但涉 webauthn 生命周期语义，建议并入 W14 后续或独立 hotfix plan，需补回归测试）；D3-F2 → successor（原子写模式有仓内先例，可随 F1 顺带）；D3-F3 → successor（权限面裁定涉 D4/D5）；D3-F4/F5/F6 → watch-only/低优先 successor；D3-F7 → watch-only（Why Not Blocking：可利用性≈0，见上）。

---

## 二、对抗探针逐项裁决

### C1 恢复码穷举与失败计数分界 — **部分探查到**

**证据**：
- 错误分支（`LoginServiceImpl.java:650-662`）：INVALID → `incrFailCountOrDiscard(challengeToken)` + `ERR_AUTH_MFA_FAIL`；USED → 仅 `ERR_AUTH_MFA_FAIL`（不计数）。恢复码错误尝试与普通因子**共享同一 challenge 失败计数**（`incrFailCountOrDiscard` :712-720，达 `CFG_AUTH_MFA_MAX_ATTEMPTS`（缺省 5，`NopAuthConfigs.java:93-95`）后 `consume` 作废 challenge）。无恢复码专属独立计数。
- 穷举上界推导：单 challenge 5 次错误尝试；challenge TTL 300s（`NopAuthConfigs.java:89-91`）；新 challenge 须重过第一因子（密码登录）。10 位数字码 × 10 码并存 → 单次命中概率 10^-9，5 次/challenge → 期望需 ~2×10^8 个密码认证会话。**穷举在现门禁下不可行**，但界完全依赖外围（见 D3-F5 熵注记）。
- 错误码区分面：MFA_FAIL vs EXPIRED 分界只反映 challenge 存活（与因子类型无关），未区分恢复码/其他因子的失败文案——无因子类型泄漏。计时面：`verifyRecoveryCode` 每行独立 BCrypt 比对（cost 统一，默认 strength 10），VALID 早退、INVALID 全扫——行位次计时侧信道理论存在但网络噪声下不可分辨，不另立 finding。
- **探查到（D3-F4）**：USED/INVALID 失败计数分界本身构成 challenge 存活度 oracle（见 findings）。

### C2 重置窗口期旧码复活 — **未探查到（核心闭合；残留两条次级窗口）**

**证据**：
- challenge 与 setting 状态解耦、但**验证时复核**：`mfaVerifyAsync` 在 peek 后复核 `setting == null || status != enabled` → `consume(challengeToken)` + CHALLENGE_EXPIRED（`LoginServiceImpl.java:566-572`，注释明确"管理员重置/用户解绑后 challenge 作废"）。reset 后（setting 字段清空+disabled）或 unbind 后（disabled），残留 challenge 在下一次使用尝试时被消费，**无法完成登录**。Db store 的 consume 为条件 DELETE 原子（`DbMfaChallengeStore.java:128-148`）。
- 旧恢复码：`resetUserMfa` → `deleteRecoveryCodes`（`NopAuthUserBizModel.java:961`）；`unbindMfa` → `deleteRecoveryCodes`（:703）；`generateRecoveryCodes`/`confirmMfa` → `regenerateRecoveryCodes` 先删后建（:1304）。测试钉定：`TestMfaUserSelfService.java:295-317`（reset 后旧码 INVALID）、`:277-285`（unbind 删码）、`:446-450`（admin reset 删码）。
- 残留窗口（不构成主路径复活，但登记）：D3-F2 的 regenerate 竞态（旧代次码在重置瞬间可被判 VALID 一次）；D3-F7 的换绑 mfaType 复核缺口（watch-only）。

### C3 管理员重置后旧因子复活 — **探查到（webauthn 分支；TOTP/SMS/EMAIL 分支闭合）**

**证据**：
- TOTP：`resetUserMfa` 置 `secret=null`（:955）+ status=disabled（:954）；重绑经 `upsertPending` 覆盖写新 secret（:570-592）。旧 TOTP 码路径：`MfaFactorVerifier.verify` totp 分支在 secret 空时返回 false（`MfaFactorVerifier.java:144-146`）；challenge 复核在 status 非 enabled 时消费 challenge。**旧码不可复活**。
- SMS/EMAIL：验证状态在 store（`mfa:{userId}` / `mfa-email:{userId}`），TTL 300s 瞬态；setting disabled 后无 challenge 可达，重绑产生新 pending（通道 key 复用但码一次性原子消费）。闭合。
- **WebAuthn：探查到（D3-F1）**。`resetUserMfa`/`unbindMfa` 均不删 `nop_auth_mfa_credential` 行；`findEnabledCredential`（`MfaFactorVerifier.java:256-266`）仅按 credentialId+userId+status=enabled 匹配，setting 重启 enabled 后旧行立即复效；`confirmWebauthnRegistration` 不 purge 旧行（:501-513 仅增量 insert）。立即后果有限（reset 后 status=disabled，旧 key 暂不可用），但**重绑 webauthn 后旧 key 复活**——对"管理员重置=全因子作废"的恢复通道语义是实质破坏（典型场景：因硬件钥匙被窃而 reset）。
- requireAdmin 运行时校验本体闭合：`NopAuthUserBizModel.java:1368-1379`（ROLE_ADMIN/ROLE_NOP_ADMIN，null 会话拒绝）；`resetUserMfa` 具 `@MfaRequired`（:940）+ `@BizAudit(logRequestFields="userId")`（:939）审计链。绕过面在 D3-F3（CRUD 通道无此保护）。

### C4 恢复码 used 幂等 — **错误统一面闭合；原子性未闭合 → 探查到（并发双花）**

**证据**：
- 错误统一：USED 与 INVALID 对外均 `ERR_AUTH_MFA_FAIL`（`LoginServiceImpl.java:655-661`），无"已使用"专属文案/错误码——直接 oracle 闭合。测试钉定：`TestMfaLoginE2E.java:297-330`（恢复码登录 → accessToken + status=disabled + used 标记 + 已用码再验 → MFA_FAIL）。
- 结构性加固：恢复码使用后 status=disabled → 后续 challenge 创建与验证全被复核挡住（C2 同一机制）。
- **原子性缺口（D3-F2）**：used 置位为无条件 read-modify-write（:700-702），并发双 verify 同码双过；无 `WHERE USED=0` 条件写。对照仓内原子先例（markVerified / signCount），属模式遗漏而非平台能力缺失。

### C5 BCrypt 存储核实 — **未探查到违规（附带熵注记 D3-F5）**

**证据**：
- 生成（`NopAuthUserBizModel.java:1303-1319`）：`salt = passwordEncoder.generateSalt()`（UUID，`SHA256PasswordEncoder.java:15-17`）→ `hash = encodePassword(salt, plain)`。实际编码器为 `nopPasswordEncoder` = CompositePasswordEncoder（`auth-core-defaults.beans.xml:32-41`）：**SHA256(salt, code) 再 BCrypt**（`CompositePasswordEncoder.java:46-50`；BCrypt 默认 strength 10，`BCryptPasswordEncoder.java:66-78`），BCrypt 内部再产独立盐。存储形态 `salt:hash`（:1314），ORM 注释与列标签一致（`nop-auth.orm.xml:1141-1143` `masked,var,not-pub`）。
- 验证（`LoginServiceImpl.java:692-695`）：split(":",2) 后 `passwordMatches(salt, input, hash)` —— 同一复合编码器逆路径（:53-56），与生成侧逐字兼容。
- 明文边界：明文仅 `confirmMfa`/`confirmWebauthnRegistration`/`generateRecoveryCodes` 响应一次性返回（:656/:517/:763）；落库仅 codeHash。输出面：`_NopAuthMfaRecoveryCode.xmeta:30-34` codeHash `published="false"`（GraphQL 输出排除）+ masked（UI）。日志/审计面：恢复路径 `LOG.info` 仅 userId/userName（`LoginServiceImpl.java:667`）；`@BizAudit` logRequestFields 限 mfaType/userId/challengeToken（:236/:449/:939），无 recoveryCode 字段；`mfaVerifyAsync`（LoginApiBizModel :195-197）无 @BizAudit。
- 生成熵：`MathHelper.secureRandom()` → JDK SecureRandom（`MathHelper.java:190-197`）——RNG 无虞；位数偏短另立 D3-F5。
- 旁注：操作级 `mfaVerifyOperation` 结构性拒绝恢复码（`MfaVerifyOperationRequest` 无 recoveryCode 字段，`LoginApiBizModel.java:291-295`；测试 `TestOperationMfaE2E.java:317-331`）——设计 §3.4"操作级不接受恢复码"闭合。

---

## 三、覆盖率声明

**已核实（live 读取全文或目标区段）**：
- `LoginServiceImpl.java` :500-760（mfaVerifyAsync / verifySecondFactorAndComplete / verifyRecoveryCodeAndComplete / verifyRecoveryCode / incrFailCountOrDiscard / completeMfaLogin）
- `MfaFactorVerifier.java` 全文（五分支 + webauthn 原子写先例 + 恢复码不入组件裁定）
- `NopAuthUserBizModel.java` :225-354（bindMfa 族）、:395-560（webauthn bind/confirm）、:560-760（confirmMfa/unbindMfa/generateRecoveryCodes/getMfaStatus）、:755-764、:920-1049（resetUserMfa/requireAdmin/辅助）、:1280-1419（regenerate/deleteRecoveryCodes/generateRecoveryCode/requireAdmin）
- `nop-auth.orm.xml` :1080-1200（NopAuthMfaSetting 关系 + NopAuthMfaRecoveryCode 全列 + NopAuthMfaChallenge 头部）
- `_NopAuthMfaRecoveryCode.xmeta` 全文；`NopAuthMfaRecoveryCodeInputBean.java` 全文
- `BCryptPasswordEncoder` / `SHA256PasswordEncoder` / `CompositePasswordEncoder` 全文；`auth-core-defaults.beans.xml` 全文（编码器装配）
- `DbMfaChallengeStore.java` :95-200（peek/incrFailCount/consume/markVerified 原子性）；`MathHelper.secureRandom`
- `LoginApiBizModel.java` :180-310（mfaVerifyAsync/webauthnAuthOptions/mfaVerifyOperation）
- `NopAuthConfigs.java` :85-114（challenge-expire/max-attempts 缺省）
- 权限资源声明 `_nop-auth.action-auth.xml`（MFA 五表 query/mutation 资源）
- 测试覆盖面核对（未运行，仅读断言）：`TestMfaLoginE2E`（:294-330 恢复码登录族）、`TestMfaUserSelfService`（:240-317 round-trip/unbind/regenerate、:426-450 admin reset）、`TestOperationMfaE2E`（:317-331 操作级拒绝）、`TestTrustedDeviceE2E`（:333-342 恢复码不登记可信设备）、`TestMfaRestrictedSessionE2E`/`TestChannelProofEmailE2E`/`TestEmailMfaE2E`/`TestWebAuthnMfaE2E`（恢复码签发计数断言）

**未覆盖/限定**：
- 未运行任何构建/测试（章程纪律：research only）；行为结论基于代码静态推演 + 既有测试断言交叉。
- `RedisMfaChallengeStore`/`LocalMfaChallengeStore` 仅经 grep 抽查 expireAt 处理（:108/:153/:175），未逐行复核——challenge 原子性结论以 Db 实现为准。
- CRUD 授权的**运行时角色授予数据**不在仓库种子中（action-auth 仅声明资源），D3-F3 的"默认 admin-only"为基于 nop-auth 权限模型的推断，部署侧实际授权需运维核对。
- `NopAuthMfaSetting`/`NopAuthMfaCredential` CRUD 写面的完整裁定归属 D1/D5（本报告仅在 D3-F3 中登记同族风险）。
- GraphQL 请求层通用日志是否序列化 MfaVerifyRequest 明文体未深查（@BizAudit 面已核，无恢复码明文字段登记）。

**探针完成度**：C1-C5 全部执行并给出显式裁决（C1 部分探查到 / C2 未探查到（主路径）+2 残留 / C3 探查到（webauthn）/ C4 统一面闭合+原子性探查到 / C5 未探查到违规）。
