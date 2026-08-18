# D6 审计报告：可信设备

> **Dimension**: D6（可信设备）
> **Executor**: fresh 独立对抗审计子 agent（task: D6-trusted-device，本 session）
> **Date**: 2026-08-18
> **Charter**: `ai-dev/audits/2026-08/2026-08-18-1244-deep-audit-nop-auth/audit-charter.md` §二 D6
> **Design baseline**: `ai-dev/design/nop-auth/02-mfa-phase2-design.md` §六（6.1-6.5）+ §6.6 impl 裁定回写
> **Mode**: RESEARCH + REPORT ONLY（未修改任何产品/测试代码；未运行 maven 构建）
> **结论速览**: 无 P0/P1。P2 ×1（通用 CRUD 管理面伪造豁免行——权限门槛内、非默认可达）、P3 watch ×2。对抗探查 F1-F7 全部 **PASS**（威胁模型内语义成立；唯一偏离立案为 D6-1）。**回归锚点（OAuth 副本"不加豁免分支"专项回归断言）PASS**。

---

## 一、Findings 总表

| ID | Severity | file:line anchor | description | suggested fix direction |
|---|---|---|---|---|
| D6-1 | P2 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthMfaTrustedDeviceBizModel.java:10-14`；`nop-auth/nop-auth-meta/src/main/resources/_vfs/nop/auth/model/NopAuthMfaTrustedDevice/_NopAuthMfaTrustedDevice.xmeta:28-36`；`nop-auth/nop-auth-api/src/main/java/io/nop/auth/api/beans/NopAuthMfaTrustedDeviceInputBean.java:43-54`；`nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/auth/_nop-auth.action-auth.xml:129-136`；`nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/_service.beans.xml:81` | **通用 CRUD 管理面可伪造豁免行，绕过 §6.1 结论 4"登记入口=mfaVerify 成功路径"不变式**：codegen 生成的 `NopAuthMfaTrustedDeviceBizModel`（CrudBizModel，已注册 bean）暴露 `save/update/deleteByQuery/batchDelete/updateByQuery/findPage` 等全量动作；xmeta 中 `userId`(:28-31) 与 `deviceHash`(:32-36) 均 `insertable=true updatable=true`（`internal=true`/`published=false` 只影响出参发布，生成 InputBean 两字段带 setter 齐备），`expireAt`(:41-43) 亦可任意写。持 `NopAuthMfaTrustedDevice:mutation` 权限者（action-auth 资源树 FNPT，经角色-资源授权面授予；默认未授予普通角色）可直接向**任意受害者 userId** 插入**攻击者可离线计算的 deviceHash**（算法 `sha256(deviceId\|UA\|AL)` 无盐确定性，文档化于 §6.3）+ 任意 `expireAt`（越过 ttl-days 配置上界）→ 攻击者此后凭受害者密码 + 自选指纹即获登录级 MFA 豁免——**把"密码 + 管理面写入权限"组合成认证绕过原语**，且绕过 max-count/本人数据限定/`MfaTrustedDeviceManager` 全部审计事件（register/revoke 五事件均不经此路径）。**前提是 mutation 权限**（典型=admin/被误授的支持角色；生成的管理页面 `nop-auth-web/pages/NopAuthMfaTrustedDevice/main.page.yaml` 使该授权面一键可达），故非无权限用户可远程利用；且为**家族性 posture**（`NopAuthMfaSetting.secret`、`NopAuthMfaCredential` 同形敞开——setting 侧 secret 直写更敏感，路由 D1/编排 session 裁定归属），非 W15 独有回归。另注：部署未装配 `IActionAuthChecker` 时全部动作鉴权失效（`GraphQLActionAuthChecker.check:37-39` checker==null 直接放行）——框架级部署前提，D5 审计面。 | 保留 xbiz 源（`NopAuthMfaTrustedDevice.xbiz`）中显式收敛动作面：删除/禁用 `save`/`update`/`updateByQuery`/`deleteByQuery`/`batchDelete`（设计本无手工建行入口），仅留 query + `delete` 且 delete 经 `MfaTrustedDeviceManager.removeBySid` 管理端变体（带审计）；或将 `deviceHash`/`expireAt` 置 `insertable=false updatable=false` + biz:data-auth 限定本人。家族性收敛（setting/credential 同法）建议编排 session 立后继 plan 统一裁定 |
| D6-2 | P3（watch-only） | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaTrustedDeviceManager.java:112-123`（isExempted load-modify-write）；`:189-196`（touchRegister 并发对侧） | `isExempted` 读-改-写无乐观锁（实体按 §6.6 裁定 3 无 version 列）：并发场景下（豁免命中加载行 → 并发 register 复活刷新 expireAt → isExempted 落库）后写者以陈旧内存值覆盖——但 isExempted 路径**从不重算 expireAt**（仅 lastUsedAt），覆盖方向只会**缩短**窗口（fail-safe），不存在延长路径；且 nop-orm `updateDirectly` 按 dirty 列生成 UPDATE（`OrmSessionImpl.java:528-531` + `EntityPersisterImpl.queueUpdate`），expireAt 不在语句中，常态下无覆盖。影响=用户可见的"复活刷新偶发丢失"（重验一次 MFA 即恢复），无安全后果。 | watch-only 登记；如需消除：touchRegister 改条件 UPDATE 仅推进 expireAt（`WHERE expireAt<=now`），或 isExempted 改为仅 UPDATE LAST_USED_AT 列的原生条件语句 |
| D6-3 | P3（watch-only） | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaTrustedDeviceManager.java:151-167`（countUnexpired 检查后 insert 的 TOCTOU） | 满员判定与 insert 非原子：两并发**不同 hash**登记可同时通过 `countUnexpired` 检查各自 insert → 超限 +1 行（同 hash 并发已被唯一约束归一为 update，:168-179 处理正确）。后果=豁免名额超发一格，无安全影响（超额的是需完整 MFA 才能登记的行）。 | watch-only 登记；如需消除：insert 撞任何约束后重走 count 判定，或接受（设计仅承诺同 hash 并发归一） |

**无 P0/P1**：无权限用户不可达任何登记/撤销/豁免伪造路径（登记需完整因子验证、管理 API 需登录态 + 本人数据限定、CRUD 面需 mutation 权限）。

---

## 二、对抗探查逐项裁决（F1-F7）

### F1 豁免不续期（charter F1）— **PASS**

**证据链**：

1. `MfaTrustedDeviceManager.isExempted`（:112-123）：命中仅 `trusted.setLastUsedAt(now)`（:119）后 `updateEntityDirectly`——**无任何 expireAt 写点**。
2. `expireAt` 重算点全仓库唯一 = `fixedWindowEnd()`（:199-202，now + ttl-days），其调用者 `initRegister`/`touchRegister`（:182-196）仅从 `register()`（:137-180）可达；`register()` 唯一生产调用点 = `LoginServiceImpl.verifySecondFactorAndComplete:629`，位于**第二因子验证成功且 challenge consume 之后**（:614-623）——即 expireAt 只在完整 MFA 后重算（设计 §6.1 结论 2 同 hash 复活刷新语义）。
3. DB 语句级：nop-orm `updateDirectly` 对非 dirty 实体直接跳过（`OrmSessionImpl.java:528-531`），dirty 实体经 persister 按 **dirty 列**生成 SET 子句（`EntityPersisterImpl.java:470-488`）——isExempted 发出的 UPDATE 仅含 `LAST_USED_AT`（+框架自动触碰的 `UPDATE_TIME`/`UPDATED_BY`），**`EXPIRE_AT` 列不在语句中**（逐字节不可变，强于值等价）。
4. E2E 钉定断言：`TestTrustedDeviceE2E.testRegisterAndSecondLoginExempted:174-179`——豁免命中后断言 `expireAt.getTime()` 相等 + lastUsedAt 非空（charter 要求的"逐字节断言先例"以时间值等价形式落地，配合第 3 点语句级证据语义成立）。
5. ORM 注释一致：`nop-auth/model/nop-auth.orm.xml:1460-1465`（EXPIRE_AT"固定窗口…命中不续期" / LAST_USED_AT"仅更新此列"）。

### F2 撤销后残留豁免（charter F2）— **PASS**

**证据链**：

1. **DB 为唯一事实源、无缓存层**：豁免判定每次登录直达 DB（`findByHash` :253-259 经 `findAllByExample`，无 global cache 装配于该实体生产 wiring；`DaoUserContextCache` 只缓存会话上下文，不缓存可信设备行）。撤销（物理 `deleteEntity` :219/:227-229）后**下一次登录立即不可豁免**，无时间窗。
2. **四触发全落位**（`NopAuthUserBizModel`）：自助移除 `removeTrustedDevice:897-906`（→ `removeBySid` :215-222，物理删除 + `userId.equals` 本人限定 :217，越权归一"不存在"）；`unbindMfa:706`（"unbind"）；换绑判定点 `confirmMfa` 成功 :653（"factor-change"，pending 覆盖写不算——注释 :651-652 与代码位一致）；`resetUserMfa:963`（"admin-reset"，跨租户可达——实体 tagSet `no-tenant` 无租户过滤，`nop-auth/model/nop-auth.orm.xml:1445`）。E2E 钉定：`testRevocationMatrix:349-436` 四分支逐项断言删后 0 行 + 跨用户移除归一。
3. **challenge 残留不构成豁免残留**：豁免分支位于 challenge 创建**之前**（`checkMfaRequired:1041-1049`）——被豁免路径从不产生 challenge；已存在的 challenge 是完整 MFA 验证凭据，与豁免正交（撤销后旧 challenge 完成验证 = 通过完整第二因子，非残留豁免）。
4. **残余 TOCTOU**（不立案）：撤销落库前微秒级并发已过 isExempted 的在途登录——单请求窗口、不产生持久豁免，属固有语义。
5. 策略禁豁免（第五触发）不删行、判定跳过：`checkMfaRequired:1043` `policy.isAllowTrustedDevice()` 前置短路；E2E `testPolicyDisallowSkipsExemptionAndRowRetained:274-296`（含放宽恢复）。

### F3 伪造/重放 deviceHash — **按设计威胁模型 PASS（重放≡持有为显式接受项）**

**证据链**：

1. **派生**：`fingerprint`（:81-92）服务端计算，三输入 = 客户端自定义头 `X-Nop-Mfa-Device-Id`（大小写不敏感读 :95-104）+ `User-Agent` + `Accept-Language`，`|` 连接 UTF-8 字节 SHA-256（`HashHelper.sha256(input, null)`——第二参 salt=null，单迭代无盐，`HashHelper.java:86-88`），`StringHelper.bytesToHex` 64 字符小写 hex（:736-738，§6.6 裁定 2 核定一致）。device-id 缺失返回 null 降级正常 MFA（:84-86），无错误侧信道。
2. **重放 stolen hash ≡ 持有 device-id+UA+AL**：设计 §6.3 威胁模型显式声明不防"本机恶意软件/同设备攻击者"（UA/AL 可伪造、device-id 同机可读）——接受项，非缺陷。hash 本身无秘密性（不可逆但可由持输入者重算）。
3. **穷举/枚举不可行**：device-id 为前端生成持久化 UUID（~122 bit 熵）——离线穷举他人 (deviceId,UA,AL) 原像空间不可行；在线枚举无 oracle：豁免判定仅在一因子成功后可达（`loginAsync` :376-380 → `checkMfaRequired`），错误猜测的表现 = 正常 MFA challenge，与"无记录/未启用"不可区分，且受登录失败计数约束。
4. **跨用户 hash 碰撞无害**：复合唯一 (userId,deviceHash) 下同 hash 异 user 为独立合法行；豁免查找 `findByHash(userId, deviceHash)` 双键（:253-259），不产生跨用户泄漏（见 F4）。
5. **hash 不外泄**：`listTrustedDevices` 返回 `TrustedDeviceInfo` 不含 deviceHash（`NopAuthUserBizModel:866-889`，注释明示最小暴露）；CRUD 出参侧 `published=false`/`not-pub`（xmeta :32-36）。

### F4 跨用户指纹挪用 — **PASS**

**证据链**：

1. 查找双键：`findByHash(userId, deviceHash)` example 同时 set userId + deviceHash（:253-259）——用户 A 的豁免判定永不命中 B 的行（即便 hash 相同）。
2. 登记键钉定验证者：`register(user.getUserId(), headers)` 的 userId 取自 challenge 复核装载的 `NopAuthUser`（`mfaVerifyAsync` :560 → `verifySecondFactorAndComplete` :629），非客户端参数。
3. 管理面本人限定：`listTrustedDevices:872-877`（`requireCurrentUserId`）、`removeBySid:217`（`!userId.equals(row.getUserId())` → false → 归一"不存在" :900-905）。
4. E2E：`testRevocationMatrix:362-366`（移除他人 sid → INVALID_LOGIN_REQUEST + 他人行存活）。
5. `removeAllForUser` 按传入 userId 全量（撤销矩阵钩子调用点 userId 均为本人会话/管理员目标用户，:653/:706/:963）。

### F5 满员 false + 并发登记归一（charter F3）— **PASS**

**证据链**：

1. **满员显式 false 非静默**：`countUnexpired >= max-count` → return null（:151-156，仅计未过期行 :244-249）→ `registered=false`（`LoginServiceImpl:628-630`）→ attr false → `LoginResult.trustedDeviceRegistered=false`（`LoginApiBizModel:211-213`），登录不阻断。E2E：`testMaxCountFullReportsFalse:248-269`（max-count=1，第二设备 false + accessToken 正常签发 + 行数=1）。
2. **同 hash（含过期行）复活刷新无条件放行、不受 max-count 限制**：`findByHash` 不过滤过期行（:143-149 → touchRegister :189-196 重算 expireAt）——E2E `testExpiredRowNotExemptedAndReviveRefresh:231-243`（同 sid 复活 + expireAt 重算 + 豁免恢复）。
3. **并发同 hash 撞唯一约束归一为 update**：insert 异常 catch → 重查行存在 → `touchRegister` → 返回 "concurrent-normalized"（:168-179）；真实失败 log error + 审计 `register-fail` + 返回 null（不吞异常、不阻断登录）。
4. 残余：异 hash 并发容量竞态可超限 +1（D6-3 P3 watch，无安全影响）。

### F6 rememberDevice 语义 + 结构性排除（charter F4）— **PASS**

**证据链**：

1. **仅显式 true 登记**：`Boolean.TRUE.equals(request.getRememberDevice()) && isPasswordLoginType(challenge.getLoginType()) && trustedDeviceManager != null`（`LoginServiceImpl:626-627`）；false/absent → `registered=null` → attr 不设（:633 `if (registered != null)`）→ `LoginApiBizModel:211` `instanceof Boolean` 守卫 → LoginResult 字段缺省不出现。API 字段：`MfaVerifyRequest.rememberDevice:36`（Boolean 可选）/ `LoginResult.trustedDeviceRegistered:55`（nop-biz-auth-api）。
2. **headers 穿线仅经 verifySecondFactorAndComplete**：`mfaVerifyAsync(request, headers)` ← `LoginApiBizModel.mfaVerifyAsync:198` `context.getRequestHeaders()`；`verifySecondFactorAndComplete(request, challenge, user, setting, headers)` :600-604 → register :629（consume 之后、completeLogin 之前纯 DB 写，设计 §6.6 裁定 5 落位一致）。
3. **恢复码结构性排除（负例核实）**：恢复码分支 `verifyRecoveryCodeAndComplete`（:650-669）**无 register 调用**；其出口 `completeMfaLogin`（:730-739）签名**不含 headers 参数**、体内无任何设备逻辑（只有 accessCode 分支）——恢复码即使携带 rememberDevice=true + device 头也结构性不可登记（§6.4 拒绝项"恢复码登录登记可信设备"由方法分派而非条件判断保证）。E2E：`testRecoveryCodeDoesNotRegister:332-344`（rememberDevice=true + device 头 → 0 行 + 字段 absent）。
4. **信道类结构性排除**：登记条件 `isPasswordLoginType`（1/2/3/5，:1061-1066）排除信道 loginType（4/20-23）；E2E `testChannelPathNotExemptedAndNoRegistration:301-328`（信道 mfaVerify rememberDevice=true + device 头 → 行数不变 + 字段 absent）。豁免侧对称：`createSessionForUserAsync:417` 传 headers=null → `checkMfaRequired:1042` `requestHeaders != null` 短路。
5. **一期零回归**：`testZeroRegressionUserWithoutRecord:465-483`（无记录用户带 device-id 头 → challenge 路径；不勾选 rememberDevice → 无登记 + 字段缺省）。

### F7 OAuth 副本专项（路由项 2 关联面）— **PASS**

**证据链**：

1. **代码面**：`MfaLoginPolicyServiceImpl.checkMfaForUserName`（:73-115）完整控制流 = 全局开关（:75）→ store 装配（:77）→ 本地用户解析（:80-84）→ 策略第三态（:90-96）→ setting 检查（:97-101）→ 因子等同（:102-103）→ **直接 challenge 创建**（:108-113）——**无豁免分支**：类字段仅 daoProvider/mfaChallengeStore/userContextCache/roleMfaPolicyEvaluator（:58-70），无 trustedDeviceManager 注入、无 fingerprint 调用、无 isExempted 引用（全仓库 `isExempted` 消费点唯一 = `LoginServiceImpl:1046`）。结构性理由成立：`IMfaLoginPolicyService.checkMfaForUserName(userName, loginType)` SPI 无 headers 参数（§6.6 裁定 4——信道路径无 headers，同步豁免分支 = 永不可达死代码）。
2. **回归断言测试在位且语义成立**：`TestTrustedDeviceE2E.testOAuthCopyStillCreatesChallengeDespiteTrustedRow`（`nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestTrustedDeviceE2E.java:441-461`）——先经密码路径登记未过期可信设备行，再直调 `MfaLoginPolicyServiceImpl.checkMfaForUserName("td_oauth_user", LOGIN_TYPE_SSO)`，断言 `ERR_AUTH_MFA_REQUIRED` + challengeToken 参数非空（:455-460）——即"有未过期可信设备行的用户经 OAuth 入口仍创建 challenge"。
3. 通道面 reg 亦核实：`LoginServiceImpl.checkMfaRequired` 的豁免分支注释（:1008-1010）与 §6.6 裁定 4 文本一致（"本裁定 + OAuth 路径回归断言"形式履行 W13 双方同步不变式）。

---

## 三、回归锚点裁决（D6 owned）

**锚点**：OAuth 副本"不加豁免分支"专项回归断言仍在且语义成立（路由项 2 关联面）。

**裁决：PASS**

- 实现侧：`MfaLoginPolicyServiceImpl.java:73-115` 无豁免分支（逐行核实，见 F7）。
- 断言侧：`TestTrustedDeviceE2E.testOAuthCopyStillCreatesChallengeDespiteTrustedRow:441-461` 存在且断言"有未过期行仍创建 challenge"（错误码 + challengeToken 双断言）。
- 设计侧：§6.6 裁定 4（2026-08-18 回写）与 live 代码/测试三方一致。

---

## 四、覆盖率声明

**已核实（live 锚点逐个比对，非转抄）**：

- `MfaTrustedDeviceManager` 全文（指纹算法/编码/豁免固定窗口/登记复活刷新/满员/并发归一/撤销矩阵/审计事件/容量口径）。
- `LoginServiceImpl`：豁免分支插入位与前置条件（:1041-1049）、headers 增参两调用点（:380 真实 / :417 null）、登记落位（:626-639）、ATTR_TRUSTED_DEVICE_REGISTERED（:545）、completeMfaLogin 负例（:730-739 无 headers）、恢复码分支负例（:650-669）、isPasswordLoginType（:1061-1066）。
- `NopAuthUserBizModel`：listTrustedDevices（:872-889）/removeTrustedDevice（:897-906）/四撤销触发（:653/:706/:963 + 自助移除）/requireTrustedDeviceManager fail-closed（:909-914）。
- ORM 模型 + DDL：复合唯一 `UK_NOP_AUTH_MFA_TRUSTED_DEVICE_UH`（orm.xml:1483；mysql/oracle/postgresql 三方言 create + add DDL 均在）、expireAt/lastUsedAt 列语义注释、无独立 userId 索引（§6.6 裁定 3）、无 delFlag/version（物理删除）。
- `MfaLoginPolicyServiceImpl` 全文（OAuth 副本无豁免分支）+ `IMfaLoginPolicyService` SPI 签名（无 headers）。
- `RoleMfaPolicy`/`RoleMfaPolicyEvaluator`：allowTrustedDevice AND 合并（:100-101，null/0 → false fail-safe）、NONE=(0,true)；消费点唯一性（`policy.isAllowTrustedDevice()` 全仓库唯一消费 = `LoginServiceImpl:1043`）。
- `LoginApiBizModel.mfaVerifyAsync` headers 穿线 + attr 回填（:195-216，accessCode 分支早退 → trustedDeviceRegistered 仅密码类路径返回）。
- API 契约：`MfaVerifyRequest.rememberDevice`（:36）/ `LoginResult.trustedDeviceRegistered`（:55）。
- 配置：`nop.auth.mfa.trusted-device.ttl-days` 缺省 30 / `max-count` 缺省 5（`NopAuthConfigs.java:207-213`）；bean `nopMfaTrustedDeviceManager`（auth-service.beans.xml:146，ioc:default）。
- 通用 CRUD 面（D6-1）：BizModel 注册（_service.beans.xml:81）、xbiz 生成原型（biz-gen.xlib 无 auth）、默认动作鉴权语义（ReflectionBizModelBuilder:335-340 无 @Auth → permission 门禁 + GraphQLActionAuthChecker isAllowAccess）、xmeta insertable/updatable、InputBean 字段、action-auth 资源树 FNPT、生成管理页面存在性。
- ORM 更新语义：`updateDirectly` dirty-check + dirty 列 SET（OrmSessionImpl:520-534 / EntityPersisterImpl:470-488）——F1 语句级证据。
- 测试树：`TestTrustedDeviceE2E` 11 用例全文（含回归锚点）+ `TestTrustedDeviceSupport` 指纹单测（确定性/大小写不敏感/三输入区分性）。

**未执行/边界外**：

- 未运行 maven 构建/测试（charter 纪律：research-only）；运行时行为依据代码精读 + 既有测试代码精读。
- nop-auth-sso `OAuthLoginServiceImpl` 调用点细节（D4 所有权——本维度仅核实 SPI 无 headers 使豁免结构性不可达）。
- `OperationMfaCheckerImpl` 无可信设备豁免（设计 §3.1 结论 6"操作级永不豁免"）经全仓库 `isExempted` 消费点唯一性 grep 证实，未逐行复核 checker 全文（D5 所有权）。
- `IActionAuthChecker` 生产部署 wiring（D6-1 的部署前提，D5 审计面关联）。

**charter 对抗用例映射**：charter F1（豁免不续期逐字节断言）→ 本报告 F1 PASS；F2（撤销后同 hash 立即失效）→ F2 PASS；F3（满员 false）→ F5 PASS；F4（恢复码路径不登记结构性负例）→ F6 PASS。
