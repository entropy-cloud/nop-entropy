# 零明文回归断言复核报告（V2，fresh 子 agent）

> Reviewer/Agent: fresh 独立验证子 agent（A3 closure audit，无先前会话上下文，read-only） | Date: 2026-08-19 | Mode: read-only live anchor verification
> 基线: HEAD `b7c81136c`（复核时 `git log` 确认）。未修改任何源文件、未运行 maven；唯一写动作 = 本报告文件。

---

## 一、锚点逐条结论

| # | 锚点 | live file:line | 决定性证据摘录 | 结论 |
|---|------|----------------|----------------|------|
| 1 | `NopCredential.xmeta` data 结构性不发布 | `nop-credential/nop-credential-meta/src/main/resources/_vfs/nop/credential/model/NopCredential/NopCredential.xmeta:6` | `<prop name="data" published="false"/>`（注释：永不发布到 GraphQL/REST schema） | **PASS** |
| 2 | `NopCredentialBizModel` 查询面清密文 + maskList 脱敏 | `nop-credential/.../entity/NopCredentialBizModel.java` — `get`:359-377（373-374 `evict(entity); entity.setData(null);`）；`findPage`:383-395；D1-01 继承面 `findList`:408-419 / `findFirst`:429-438 / `batchGet`:893-912（900-904 逐行驱逐+置空）；`maskList`:541-551（经 `credentialProvider.mask`，provider 侧 `****` 脱敏 CredentialProviderImpl.java:639-650）；`saveCredential` 返回前置空 320-324/346-350 | `orm().requireSession().evict(entity); entity.setData(null);`（get/findPage/findList/findFirst/batchGet 五面同口径）；`result.add(credentialProvider.mask(id));` | **PASS** |
| 3 | `CredentialProviderImpl` 唯一明文出口 + 归属/RBAC 前置于解密 + delFlag 先序 | `nop-credential/.../CredentialProviderImpl.java` — `getCredential`:122-138 调用序 = `loadActiveCredential`(124, delFlag 检查 505-508) → `assertOwnershipForPlaintext`(126, 实现 532-542) → `assertRoleAuthForPlaintext`(128, 实现 584-607) → `decryptToData`(129)；`getCredentialData`:141-144 委托 getCredential；全仓 `credentialCipher.decrypt` 生产代码仅 CredentialProviderImpl:631 与 BizModel reencryptAll:687（后者进程内重加密、无外泄）；引擎通道豁免为已裁定边界（javadoc 578-583 + 测试锚定） | `assertOwnershipForPlaintext(entity); assertRoleAuthForPlaintext(entity); Map<String,Object> fields = decryptToData(entity).getFields();`；owner-only：`if (!CredentialOwnership.isOwner(...)) throw ERR_CREDENTIAL_OWNER_ONLY`（管理员不豁免，536-541）；RBAC 第 6 行空集 `throw ERR_CREDENTIAL_ROLE_NOT_GRANTED`（604-606）；`if (isDeleted(entity)) throw ERR_CREDENTIAL_DELETED`（505-508，先于归属/授权判定） | **PASS** |
| 4a | `TestNopCredentialBizModel` | `nop-credential/.../entity/TestNopCredentialBizModel.java` | `endToEndPlaintextBoundary`(:126 — data 字段查询 schema 即拒 `nop.err.graphql.undefined-field`、findPage/get 无明文、SPI 可恢复)；`inheritedQueryActionsReturnEntitiesWithNullData`(:393 — batchGet/findList/findFirst `assertNull(...getData())`)；`maskListReturnsMaskedValues`(:230 — `assertEquals("****", maskedFields.get("apiKey"))`、orgId 截断)；另 `saveCredentialStoresCv1CiphertextInDb`(:179)、`standardSaveIsDisabled`(:198) | **PASS** |
| 4b | `TestCredentialProviderImpl` | 同目录 `TestCredentialProviderImpl.java` | `getCredentialRoundTrip`(:122)；`getCredentialOnDeletedThrowsFailClosed`(:161)；`maskReplacesSensitiveFieldsWithStars`(:269) | **PASS** |
| 4c | `TestCredentialProviderOwnership`（Part A deny 矩阵） | 同目录 `TestCredentialProviderOwnership.java` | `userScopeNonOwnerRejected`(:309)、`userScopeAdminNotExemptForPlaintext`(:319 — admin 仍 `owner-only` 拒)、`userScopeNoUserContextRejected`(:332)、`deletedCredentialReportsDeletedBeforeOwnership`(:415 — 非 owner 见 DELETED 不见归属)、`maskAllowsOwnerAndAdmin`/`maskRejectsOthersAndNoContext`(:367/:382) | **PASS** |
| 4d | `TestCredentialProviderRbacAuth`（Part B 矩阵） | 同目录 `TestCredentialProviderRbacAuth.java` | `row6RoleMissRejectedFailClosed`(:364 — `role-not-granted` + getCredentialData 同检)、`row6AdminNotAutoExempt`(:384)、`row6AdminWithGrantedRolePasses`(:396)、`userScopeUnaffectedByAuthRecords`(:409)、`deletedReportsDeletedBeforeAuthJudgment`(:427) | **PASS** |
| 5 | MFA secret 密文存储 + 恢复码哈希 | ORM: `nop-auth/model/nop-auth.orm.xml:1072-1074`（secret 列 comment「TOTP secret（AESTextCipher 加密密文）」+ `tagSet="masked,var,not-pub"`）；写入侧加密：`NopAuthUserBizModel.java:322-328`（`String encrypted = totpAuthenticator.getCipher().encrypt(base32Secret); ... upsertPending(..., encrypted, ...)`）；恢复码：`nop-auth.orm.xml:1150-1152`（codeHash「IPasswordEncoder 加盐哈希，不存明文」`tagSet="masked,var,not-pub"`）+ `NopAuthUserBizModel.java:1728-1732`（`String hash = passwordEncoder.encodePassword(salt, plain); ... rc.setCodeHash(salt + ":" + hash);`，平台装配 BCryptPasswordEncoder，`nop-service-framework/nop-biz-auth-core/.../BCryptPasswordEncoder.java`） | `encrypted = totpAuthenticator.getCipher().encrypt(base32Secret)`；`rc.setCodeHash(salt + ":" + hash)` | **PASS** |
| 6 | WebAuthn 凭证列表脱敏管理 | `NopAuthUserBizModel.listWebauthnCredentials`:847-863 — 仅 set sid/userId/name/transports/status/signCount/lastUsedAt/createTime；出参 Bean `nop-auth-api/.../NopAuthMfaCredentialOutputBean.java` 字段清单**无 credentialId/publicKey**；ORM 双保险：`nop-auth.orm.xml:1351`（credentialId `tagSet="var,not-pub"`）、:1355（publicKey `tagSet="masked,var,not-pub"`）。测试：`TestWebAuthnMfaE2E.java:457-463` | `assertFalse(json.contains("publicKey"), "credential list must not expose public key material"); assertFalse(json.contains(client.credentialIdB64Url()), "credential list must not expose credentialId");` | **PASS** |
| 7 | phone 结构性排除（A2-followup-1 D1-7） | ORM: `nop-auth.orm.xml:1081-1085`（phone 列 `tagSet="not-pub"`，comment 引 D1-7）；xmeta 兜底：`nop-auth/nop-auth-meta/.../NopAuthMfaSetting.xmeta:12`（`<prop name="phone" published="false"/>`，同文件 :10/:11 为 secret/bindToken）。E2E 测试：`TestMfaSensitiveSurfaceE2E.testMfaSettingGenericQueryStructurallyExcludesPhone`:82-140（findPage/get 选 phone → `ERR_GRAPHQL_UNDEFINED_FIELD`；getMfaStatus 脱敏 `******6666` 零回归）。**附注**：指定的 `TestNopAuthMfaSettingXmeta.java:54-79` 仅断言 secret/bindToken 未发布 + 正常字段可查，**不含 phone 断言**（见漂移检查 F-1） | ORM `tagSet="not-pub"` + xmeta `<prop name="phone" published="false"/>`；E2E `assertEquals(ERR_GRAPHQL_UNDEFINED_FIELD, ...)` | **PASS（附注 F-1）** |
| 8 | `TestMfaSensitiveSurfaceE2E` 敏感面收敛 | `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestMfaSensitiveSurfaceE2E.java` | 4 个测试方法：① `testMfaSettingGenericQueryStructurallyExcludesPhone`(:82) — D1-7 phone 结构性排除（schema 拒绝）+ getMfaStatus 脱敏零回归；② `testNonAdminContactChangeRejectedOnUpdateAndSave`(:145) — W12 路由项 3：非 admin 改 phone/email（update+save）→ `ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED`，行未篡改、非联系字段（nickName）不受影响；③ `testAdminContactChangeAllowedAndAudited`(:185) — admin 修改/创建含 phone 放行 + `user:contact-changed` 审计（update/create 两形态）；④ `testRestrictedSessionContactChangeChainSevered`(:216) — 受限会话组合链断链（restricted 拦截 + 非 admin guard 双门） | 见上 | **PASS** |
| 9 | getMfaStatus / bindMfa 不回 secret（A2 D1） | `NopAuthUserBizModel.getMfaStatus`:817-834 — 仅 mfaType/status/phone（`result.setPhone(maskPhone(setting.getPhone()))`:832；`MfaStatusResult` DTO 仅 3 字段，无 secret）；maskPhone:1785-1790（`repeat("*", len-4) + 后 4 位`）。bindMfa(totp)：secret **加密落库**（:323），明文 base32 仅经 provisioning URI **一次性**返回给 owner 本人（MfaBindResult javadoc 明示「明文 secret 仅经此处的 provisioning URI 一次性返回，confirmMfa 响应不含 secret」——A2 已裁定口径）。测试：`TestMfaUserSelfService.testGetMfaStatusNeverReturnsSecret`:325-338（反射断言无 secret 字段/值）+ :667（`persisted secret must be encrypted, not the plaintext base32 in the URI`） | `result.setPhone(maskPhone(setting.getPhone()));`；`assertFalse(hasSecretField(status), "MfaStatusResult must not expose a secret field")` | **PASS** |

---

## 二、漂移检查（A1 D1 / A2 D1 最终态复核 + phase-2 新增面 sweep）

### A1（credential，2026-08-17）D1 结论最终态

- **三通道不可达明文**不变：① 结构面 xmeta `published=false`（锚点 1，live）；② BizModel 面 get/findPage/findList/findFirst/batchGet 五查询面全部驱逐+`setData(null)`（D1-01 硬化仍在，锚点 2）；③ provider 面唯一明文出口 getCredential/getCredentialData 的归属（owner-only，admin 不豁免）+ RBAC（NopCredentialAuth 记录求交）均前置于解密、delFlag 先序 fail-closed（锚点 3）。四个测试文件方法与 A1 记录一致，无删改迹象（deny 矩阵 4/5/6 行、D1-01/D1-02/D1-03 断言全在）。
- 继承写面收口（update/batchDelete/updateByQuery/deleteByQuery/copyForNew 禁用，NopCredentialBizModel:829-878）与标准 save 禁用（:133-135）仍在位。

### A2（MFA，2026-08-18）D1 结论最终态

- secret 密文存储 + not-pub/masked 双层、bindToken not-pub、恢复码 salt+hash、phone not-pub（D1-7）均在 live ORM/xmeta 中保持（锚点 5/7）。
- getMfaStatus 永不回 secret、webauthn 列表面不含 credentialId/publicKey（锚点 6/9）均在 live 代码 + 测试中保持。

### phase-2 新增面 adversarial sweep（有界）

- **credential 侧**：grep 生产代码 `credentialCipher.decrypt` 仅两处 — CredentialProviderImpl:631（唯一解密点，出口有归属+RBAC 前置）与 NopCredentialBizModel.reencryptAll:687（进程内解密→重加密，返回 int 计数，无明文外泄）。OAuth 新面 `CredentialOAuthApiBizModel`：`beginOAuthFlow`:47-49 仅返回授权 URL 字符串；`oauthCallback`:56-59（publicAccess）返回 WebContentBean 跳转页，javadoc 明示「token 集加密回写后返回 HTML 跳转页（不携带 token 明文）」。`typeList` 裁剪引擎保留字段。未发现新增明文路径。
- **MFA 侧**：`NopAuthUserBizModel` 中 `getPhone()` 仅 3 处——:339 bindSms（服务端发码，不出参）、:832 getMfaStatus（maskPhone 后出参）、:1359 登记通道 proof（服务端发码；异常 param 均 `maskPhone(phone)` :1405-1420）。`getSecret()`/`getPublicKey()` 无任何出参使用。
- **A2-followup-2 新端点**：`webauthnBeginAddKey`:955-1003 返回双 challengeToken + assertionOptions/creationOptions——`allowCredentials`/`excludeCredentials` 含 credentialId 属 WebAuthn 协议必需（浏览器需定位认证器/防重复注册），仅 owner 活跃会话可达（:963-977 三重前置），与既有已裁定 `webauthnBeginVerify` 同构（TestWebAuthnMfaE2E:408 先例），不属管理面泄露。`confirmWebauthnAddKey`:1035-1109 返回 void，credentialId/publicKey 仅落库（:1097-1098），无出参。`TestWebAuthnAddKeyE2E` 断言形态与实现一致。**未发现 masked 违规**。
- 恢复码编码器注记：ORM comment 称「BCrypt 加盐」；平台装配为 `CompositePasswordEncoder`（SHA256 first + BCrypt second，`nop-biz-auth-core`）——核心不变式「加盐单向哈希、不存明文」成立（:1728-1732），编码器命名口径与实际装配的精确差异属 A2 既有状态、非本次漂移。

### 发现（非阻断）

- **F-1（P3，测试锚点描述漂移）**：任务书/先前审计记录称 `TestNopAuthMfaSettingXmeta.java` 断言 phone 不发布，live 该测试（:54-79）仅覆盖 secret/bindToken，**无 phone 断言**。phone 排除的有效测试覆盖由 `TestMfaSensitiveSurfaceE2E#testMfaSettingGenericQueryStructurallyExcludesPhone`（真实 merged schema 路径）承担，安全属性本身无缺口。建议（非必须）：在 `TestNopAuthMfaSettingXmeta` 补一条 `phone` prop `published=false` 断言，与 secret/bindToken 保持 xmeta 单元层对称，并修正审计记录中的锚点描述。

---

## 三、总结论

**ALL-PASS（9/9 锚点 PASS，其中锚点 7 附 P3 附注）**。

- CREDENTIAL 侧：三通道（结构面 xmeta / BizModel 查询面 / provider 唯一出口）不可达明文的 A1 D1 结论在最终态 `b7c81136c` 完整成立；归属矩阵（Part A owner-only）与 RBAC 矩阵（Part B 授权记录求交、admin 不自动豁免）均在解密之前、delFlag 先序 fail-closed 执行，且有完整 deny-matrix 测试锚定。
- MFA 侧：secret 密文存储、恢复码哈希、webauthn 凭证列表脱敏、phone 结构性排除、getMfaStatus 无 secret 的 A2 D1 结论全部保持；phase-2 新增端点（webauthnBeginAddKey/confirmWebauthnAddKey）无明文/脱敏违规。
- 唯一发现 F-1（P3）：`TestNopAuthMfaSettingXmeta` 缺 phone 断言的**锚点描述漂移**（安全属性由 E2E 测试有效覆盖，无实际缺陷）。

无 P0-P2 发现。零明文边界回归断言复核通过。
