# D2 审计报告：防重放与一次性消费（anti-replay & one-time consumption）

> Dimension: D2（防重放与一次性消费）
> Executor: fresh independent adversarial sub-agent（D2 专项，research + report only，未改任何产品/测试代码）
> Date: 2026-08-18
> Charter: `audit-charter.md` §二 D2（B1-B7 对抗用例）
> Baseline: `ai-dev/design/nop-auth/02-mfa-phase2-design.md` §3.3/§3.5/§5.3.5/§5.3.6/§5.3.7
> Scope disclaimer: 本报告为静态代码审计 + 测试树核对（未运行 maven build，按 charter 纪律探查/报告角色）；所有锚点为 2026-08-18 live 核对。

---

## 一、Findings 总表

| ID | Severity | file:line anchor | description | suggested fix direction |
|---|---|---|---|---|
| D2-F1 | **P1** | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/store/RedisSmsCodeStore.java:90-94`；`RedisEmailCodeStore.java:89-93` | **并发同码双花**：VALID 分支调用 `nosql.removeIfMatch(codeKey, entry)` 后**未检查返回值**即 `return CodeVerifyResult.VALID`。两个并发 verify 同码：均 `get` 命中同一 entry → 均 CAS（仅一方真正删除）→ **双方都返回 VALID**。`removeIfMatch` 返回 `boolean`（`IAsyncMap.java:55`，Lua CAS），返回值被丢弃。对照：`DbSmsCodeStore.java:104-106` 以 `conditionalDelete` affected-row 判定（败者 EXPIRED）；`RedisMfaChallengeStore.consume:158-164` 检查了返回值；`LocalSmsCodeStore` 用 ConcurrentHashMap compute 原子消费。仅 Redis SMS/Email 两实现违反"一次性原子消费"契约（接口 javadoc：`SmsCodeStore.verify` = "校验 + 成功原子消费"）。攻击窗口为毫秒级竞态，但可脚本化（同码两路并发提交，例如两个并发 mfaVerifyAsync 各持不同 challenge）。 | `boolean removed = nosql.removeIfMatch(...); return removed ? VALID : EXPIRED;`（对齐 Db 实现语义）；补并发对抗测试（FakeNosqlService 可注入"值已被并发删除"语义）。 |
| D2-F2 | **P2** | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:548-582`（`mfaVerifyAsync` 无 scene 校验）；`verifySecondFactorAndComplete:600-640`（无 verifiedAt 校验，成功即 `completeLogin`） | **跨场景 token 挪用（B3 探查到）**：登录级公开端点 `mfaVerifyAsync` 对 peek 到的 challenge **不校验 scene**（也不校验 verifiedAt）。scene=operation / webauthn-register / webauthn-unbind 的 challenge 携带用户真实 mfaType，凭 `challengeToken + 有效因子码` 可在**免密码**（免第一因子）情况下经 completeLogin 签发全新会话。operation challenge 的 token 经 `ERR_AUTH_OPERATION_MFA_REQUIRED` errorParams 大量下发到客户端（每次被拦截的敏感操作重试都新建一个），泄漏面（XSS/前端日志/错误上报）+ 实时钓鱼 TOTP = 无密码账户接管路径。已验证 register/unbind challenge 的 payload.cryptoChallenge 同样可驱动 webauthn 分支完成登录。**impl 侧已知并钉定**：`TestOperationMfaE2E.java:349-377`（"design-residual pinned ... 因子仍被验证，安全等价"）。**审计人不同意"安全等价"裁定**：该论证只覆盖第二因子，忽略了"challenge token 替代第一因子（密码）"的降级——登录级的信任前提（checkMfaRequired 在密码验证成功后才建 challenge）在公开端点侧未被 scene 校验固化。 | `mfaVerifyAsync` peek 后增加 `scene==login（或 null，一期存量兼容）且 verifiedAt==null` 校验，不符即 CHALLENGE_EXPIRED（对齐 `LoginApiBizModel.webauthnAuthOptions:246` / `mfaVerifyOperation:306` 的既有 scene 纪律）；一期调用点零改动红线的字面（老五参 create 委托 scene=login）不受影响——这是验证端增量，不是创建端变更。钉定测试同步改写为拒绝断言。 |
| D2-F3 | **P3** | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/store/RedisMfaChallengeStore.java:112-123`（peek 票键过期→verifiedAt=null，challenge 存活）；`:168-184`（markVerified 对已过期票键 SETNX 成功） | **三实现契约漂移：票过期后 challenge 的可重验证性**。Local/Db：票窗口过期 → peek 即删除 challenge（`LocalMfaChallengeStore.java:76-83`、`DbMfaChallengeStore.java:98-104`）→ 不可再验证。Redis：票键 TTL 到期 → peek 返回 verifiedAt=null 的存活 challenge → `mfaVerifyOperation:315` 的"已是票拒绝"被绕过 → 同会话可凭新因子码**重新验证**（markVerified SETNX 在已过期票键上成功 → 新票）。设计 §3.3 markVerified 契约"已过期返回 false，票不续命"在 Redis 下不成立。已被 `TestRedisStoreWiring.testTicketKeyExpiryDropsVerifiedAtRedis:190-204` 钉定为预期行为。安全影响有限（重验证仍需有效因子码 + 同 sessionId；与直接触发新 challenge 等能力），属契约一致性问题。 | Redis markVerified 前置检查票键曾存在痕迹不可行（无原语）——建议在 consume 语义上补齐：票键过期后 challenge 仍存活的窗口内，mfaVerifyOperation 侧对 scene=operation 且 payload 存在但票窗口已过的情形统一拒绝；或接受漂移并在 `MfaChallengeStore` 接口 javadoc 显式声明三实现差异（当前注释已部分声明）。 |
| D2-F4 | **P3** | `nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml:58-64`（`nopSmsCodeStore_redis`）、`:79-85`（`nopEmailCodeStore_redis`） | **Redis SMS/Email store 配置未接线**：两个 Redis bean 定义均未注入 `nopSmsCodeConfig`/`nopEmailCodeConfig`（类只有 `@Inject` 单参构造器 + 两参构造器，无 setter）→ 运行在默认 `SmsCodeStoreConfig`（expireSeconds=300/maxAttempts=5）上。运维调 `nop.auth.sms-code.max-attempts` / `expire-seconds` / `email-code.*` 对 redis 部署**静默不生效**（db/local 实现生效）。影响 D2 相邻面：爆破上限与 TTL 的运维收紧失效。 | 为 RedisSmsCodeStore/RedisEmailCodeStore 增加 `setConfig` 并在 bean 定义补 `<property name="config" ref="..."/>`（对齐 `nopMfaChallengeStore_redis:44-45` 先例）；或改用两参构造器 constructor-arg。 |
| D2-F5 | **P3**（观察项） | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java:466-475`（`issueChannelProofTicket` payload=null）；对照 `OperationMfaCheckerImpl.java:183-191` | **channel-proof 票不绑定 sessionId**（设计 §4.6 裁定 3 明示"challenge.userId 绑定三条件，payload 无需 userId 键"）：票可在同用户任意会话间重放消费（一次性仍成立，`requireChannelProof:1078-1086` 校验 scene+verifiedAt+userId+原子 consume）。跨会话重放前提是攻击者已持有受害者 proof token + 受害者登录态，能力等价于直接触发新 proof 流程，非实际增量。按设计裁定记 watch-only。 | 可选纵深：issueChannelProofTicket 的 payload 写入 sessionId 并在 requireChannelProof 比对（对齐 operation 票纪律）；非必须。 |

裁决归属建议：D2-F1 = **successor 所有权（P1，建议本审计周期内修复）**；D2-F2 = successor 所有权（impl 侧已 watch-only 钉定，审计人提级异议，交编排 session 裁定）；D2-F3/D2-F4 = successor / 顺带修复；D2-F5 = watch-only（维持设计裁定）。

---

## 二、对抗用例逐项裁决

### B1 已消费码/challenge 重放 — **未探查到**（顺序重放全部失败；并发竞态见 B2/F1）

- challenge：`DbMfaChallengeStore.consume:128-148` 条件 DELETE（`WHERE CHALLENGE_TOKEN=? AND EXPIRE_AT>now`）+ affected 判定；`RedisMfaChallengeStore.consume:140-165` get 原对象 + `removeIfMatch` **返回值已检查**（removed 才返回）；`LocalMfaChallengeStore.consume:98-106` map.remove。重放 peek/consume 均 null。
- SMS/Email（Db）：`DbSmsCodeStore.verify:103-107` / `DbEmailCodeStore.verify:96-100` 条件 DELETE 一次性消费，重放 getEntityById=null → EXPIRED。
- SMS/Email（Redis/Local）：消费后 `get` null → EXPIRED（`RedisSmsCodeStore.verify:76-78`）；Local compute 移除后同。
- 恢复码：已用 → 统一 `MFA_FAIL` 不暴露"曾有效"（`LoginServiceImpl.verifyRecoveryCodeAndComplete:655-658`、`verifyRecoveryCode:696-703`）。
- 操作级票：消费即删（checker `consume` 于 `OperationMfaCheckerImpl.java:154`），重放 peek null → 落入新建 challenge 分支抛 REQUIRED。
- webauthn：challenge 一次性 + clientData.challenge 必须匹配服务端 payload.cryptoChallenge（`WebAuthnAuthenticator.verifyAssertion:313-390`，Yubico 库强校验），跨 challenge 断言重放失败。

### B2 并发同码验证 — **探查到**（Redis SMS/Email；Db/Local/challenge 三实现未探查到）

- **探查到**：`RedisSmsCodeStore.verify:90-94` / `RedisEmailCodeStore.verify:89-93` —— 两并发 verify 同码均 `get` 命中 → 均 `removeIfMatch`（Lua CAS 保证仅一方删除）→ **均返回 VALID**（CAS 返回值被丢弃）。`removeIfMatch` 签名返回 boolean（`nop-kernel/nop-commons/.../IAsyncMap.java:53-55`；Lettuce 实现 `LettuceMessageService.removeIfMatchAsync:206-210` 经 `RedisScripts.REMOVE_IF_MATCH` Lua）。即 D2-F1。
- Db：`DbSmsCodeStore.conditionalDelete:134-138` / DbEmailCodeStore 同构 —— 败者 affected=0 → EXPIRED ✓。
- Local：`LocalSmsCodeStore.verify` ConcurrentHashMap.compute 原子消费 ✓（`LocalEmailCodeStore` 同构）。
- challenge：consume 的 CAS/条件 DELETE 均检查返回值 ✓。
- 测试覆盖缺口：`TestRedisEmailCodeStore:31-32` 仅断言 removeIfMatch **被调用**，无并发双花对抗断言。

### B3 跨场景 token 挪用 — **探查到**（login←operation/register/unbind 方向；其余方向未探查到）

逐消费/验证点 scene/user/session 校验矩阵：

| 消费点 | scene 校验 | user/session 校验 | verdict |
|---|---|---|---|
| `LoginApiBizModel.mfaVerifyOperation:305-316` | `scene==operation` ✓ | payload.sessionId==当前会话 ✓（userId 经 sessionId 传递性绑定） | login token / proof 票在此**被拒** ✓（test `:337-347`） |
| `LoginServiceImpl.mfaVerifyAsync:548-582` | **无 scene 校验** | challenge.userId 定位 user/setting（无会话要求——公开端点） | **operation/register/unbind token 在此被接受并 completeLogin**（test `:349-377` 钉定为 residual）→ **D2-F2** |
| `verifySecondFactorAndComplete:606-612` | —（mfaType 白名单） | channel-proof 票 mfaType=null → fail-closed 消费作废 ✓ | proof 票不能兑登录 ✓ |
| `NopAuthUserBizModel.confirmWebauthnRegistration:459-467` | `scene==webauthn-register` ✓ | userId 绑定 + payload.sessionId==当前会话 ✓ | ✓ |
| `NopAuthUserBizModel.verifyWebauthnUnbindAssertion:723-731` | `scene==webauthn-unbind` ✓ | userId + sessionId ✓ | ✓ |
| `NopAuthUserBizModel.requireChannelProof:1078-1086` | `scene==channel-proof` + verifiedAt ✓ | userId 绑定 ✓（无 sessionId，见 D2-F5） | operation 票/登录 challenge 不能当 proof ✓ |
| `LoginApiBizModel.webauthnAuthOptions:240-254` | scene=login 公开；非 login 需登录+同会话 ✓ | ✓ | 跨会话读 options 被拒 ✓ |
| `OperationMfaCheckerImpl.check:148-159` | `scene==operation` + verifiedAt + payload.operation + payload.sessionId ✓ | ✓ | login challenge 当票被拒 ✓ |
| `LoginServiceImpl.sendMfaCode:787-803` | **无 scene 校验**（peek 任意场景 challenge 可触发给 challenge.userId 本人手机/邮箱发码） | 限流（phone/email+IP 三层） | 影响低（码只发本人登记通道、受三层限流、码本身仍需一次性消费），info 级记录 |

补充：mfaVerifyAsync 同时**不校验 verifiedAt** —— 已转票（60s 窗口内）的 operation token 也可经登录级兑换会话，同属 D2-F2 修复面。

### B4 票跨 operation 挪用 — **未探查到**

- `OperationMfaCheckerImpl.isTicketFor:183-191`：`payload.operation` 与当前 operationName 全名字符串相等 **且** `payload.sessionId` 与当前会话相等，二者同时成立才进入原子 consume。为 operation X 签发的票重放到 Y → 匹配失败 → 新建 challenge 抛 REQUIRED（E4 语义）。
- 同会话不同用户：sessionId 为每次登录唯一（`sessionId != null` 显式判空，payload 缺 sessionId 键即 false）；challenge.userId 未显式比对，但 sessionId 相等传递性蕴含同用户上下文。观察项：补一行 `c.getUserId()==userContext.getUserId()` 属廉价纵深（记入 D2-F5 备注）。
- 并发双花：peek 通过后 `consume` 原子（条件 DELETE / CAS / map.remove），仅一方放行（`:154` affected 判定）✓。

### B5 TOTP 同窗口码重用 — **未探查到**（顺序与并发均防住；并发防住依赖 ORM 乐观锁，附带错误码观感问题）

- **顺序重放**：`TOTPAuthenticator.verifyRaw:144-153` —— `floor = max(currentWindow - skew, lastVerifiedWindow + 1)`，候选窗口**严格大于**历史窗口；同码二次验证 lastVerifiedWindow 已=W → floor=W+1 → 该码窗口 W 不可再命中。跨场景（先操作级后登录级）由组件统一推进阻断（test `TestOperationMfaE2E.testCrossSceneTotpReplayRejected:381-403`）。
- **仅在成功时推进**：`MfaFactorVerifier.verify:150-157` 仅 `window >= 0` 才 set；失败路径（含未知 mfaType、authenticator 缺失、secret 空）不动 lastVerifiedWindow ✓。
- **推进单点收敛**：全仓 main 代码 `setLastVerifiedWindow(数值)` 唯一落点 = `MfaFactorVerifier.java:155`（另两处为 `NopAuthUserBizModel.upsertPending:590` 置 null 清窗 + 生成物 setter）——三调用方（mfaVerifyAsync/mfaVerifyOperation/unbindMfa·confirmMfa 经组件）收敛成立 ✓。
- **并发竞态**：setting 读取→校验→`updateEntityDirectly` 写为 check-then-act，非条件 UPDATE；但 `NopAuthMfaSetting` 带 `versionProp="version"`（`nop-auth.orm.xml:1060-1063`），ORM 生成 `UPDATE ... SET VERSION=VERSION+1,... WHERE pk=? AND VERSION=?`（`GenSqlHelper.genUpdateSql:295-327`），affected=0 抛 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND`（`EntityPersisterImpl.checkUpdateResult:504-517`）——并发第二验证者**异常失败（fail-closed）**，不产生双接受。注：败者得到 ORM 内部错误码而非 MFA_FAIL、不进失败计数（可计入 UX/一致性 follow-up，非安全缺口）。
- 持久化时序：窗口推进（setting update）发生在 challenge consume **之前**（`verifySecondFactorAndComplete:614-623`：verify→consume；`mfaVerifyOperation:335-350`：verify→markVerified）——即使后续 consume/markVerified 失败，窗口已被推进（安全侧失效：宁可用户重取新码，不放行重放）✓。

### B6 challenge peek 不刷新 TTL / incrFailCount 超限作废 — **未探查到**（两不变式在 Db/Redis/Local 三实现 + 全部调用方成立）

- **peek 不刷新 TTL**：Db `DbMfaChallengeStore.peek:86-108` 纯 SELECT（注释明示；expireAt create 时固化）；Redis `RedisMfaChallengeStore.peek:97-123` 普通 `get`（非 GETEX；`TestRedisStoreWiring` 钉定 get 而非 getExAsync 委托）；Local 结构性（`LocalMfaChallengeStore.java:84` 注释 + `peekExpireAtMillis` 测试钩子）。
- **incrFailCount 超限作废（调用方消费）**——四个调用点全部核对：
  - `LoginServiceImpl.incrFailCountOrDiscard:712-720`（登录级 totp/sms/email/webauthn 分支）✓
  - `LoginApiBizModel.mfaVerifyOperation:341-344` ✓
  - `NopAuthUserBizModel.incrWebauthnFailCountOrDiscard:984-991`（register/unbind ceremony）✓
  - `verifyChannelProof` 路径无 challenge —— proof 码失败计数在 store 内部（`DbSmsCodeStore.verify:108-117` 超限删行 / `RedisSmsCodeStore.verify:97-108` 超限 remove）✓
- store 侧 incrFailCount 原子性：Db SQL `FAIL_COUNT=FAIL_COUNT+1 WHERE ... EXPIRE_AT>now`（`DbMfaChallengeStore:111-125`）；Redis INCRBY（`:126-137`）；Local AtomicInteger ✓。

### B7 signCount 并发 — **未探查到**（条件 UPDATE 拒绝败者）

- `MfaFactorVerifier.verifyWebauthn:230-245`：`UPDATE nop_auth_mfa_credential SET SIGN_COUNT=?, LAST_USED_AT=?, UPDATE_TIME=?, VERSION=VERSION+1 WHERE SID=? AND SIGN_COUNT<?`，`affected==0` → 审计 `sign-count-race` → 返回 false（MFA_FAIL）。两并发同 signCount 断言：先到者 affected=1；后到者 `SIGN_COUNT < new` 不成立（相等）→ affected=0 → 拒绝 ✓。
- 前置：库内单调查定（Yubico `finishAssertion`，新/旧计数任一非零即要求严格递增——`WebAuthnAuthenticator.verifyAssertion:296-311` javadoc + 库调用 `:372-378`）；count=0 认证器跳过单调写仅 touch lastUsedAt + 审计标记（§7.7 watch-only，防御依赖 challenge 一次性，B1 已核）。
- 该副作用内聚组件（调用方不可选），三调用点（登录/操作级/解绑）自动一致 ✓。

---

## 三、回归锚点裁决（D2 所有权）

### Anchor 1：两阶段 login challenge（设计 §二 matrix row 1）— **PASS**

- 创建侧收敛：①`LoginServiceImpl.checkMfaRequired:1052` 与 ③`MfaLoginPolicyServiceImpl.checkMfaForUserName:108` 均经 `MfaChallengeHelper.createLoginChallenge`（非 webauthn 逐字节等价老五参委托 `MfaChallengeStore.java:38-40`）；②`OperationMfaCheckerImpl:170` 按 `PAYLOAD_CRYPTO_CHALLENGE` 同 key 增量（`MfaChallengeHelper` 常量 + `randomCryptoChallenge`）。grep 全 nop-auth main 代码 challenge 创建仅 5 处（helper×2 入口、checker、channel-proof 票、register/unbind 经 `webauthnScenePayload`）——无绕过 helper 的散点创建。
- 两阶段语义：checkMfaRequired 建 challenge（TTL create 时固化）→ `ERR_AUTH_MFA_REQUIRED` 携 token → `mfaVerifyAsync` peek→复核→因子→consume→completeLogin；失败计数先于消费（peek 阶段）保证 max-attempts 可达。
- 偏差登记：一期"调用点零改动"的验证端未随 scene 扩展加校验 → D2-F2（一期行为字面不变=PASS，扩展面安全后果单列 finding，不掩盖）。

### Anchor 4：store 装配 collect-beans + 条件激活 + lessons 15 类加载安全（EmailCodeStore 三实现复刻完整性）— **PASS（带 1 处 P3 偏差 D2-F4）**

- `auth-service.beans.xml`：三组 collect-beans（`nopMfaChallengeStore`/`nopSmsCodeStore`/`nopEmailCodeStore` 前缀，`as-map` + `ioc:ignore-depends`，:89-97）；store 实现 bean `autowire-candidate=false`；Redis 三 bean 均 `ioc:condition`（`if-property store-type=redis` + `on-class io.nop.nosql.core.INosqlService`，:45-48/:60-63/:81-84）——lessons 15 类加载安全不变式成立（classpath 无 nosql 时 Redis 实现类不加载）；`MfaStoreProvider` 零 `INosqlService` 类型引用（`MfaStoreProvider.java:29-31`）；config bean id 不落 collect 前缀（W8 命名教训，:22-23/:65-66 注释）；工厂 bean `nopActive*Store` 经 provider 方法（:100-107）；请求类型未注册显式 fail-closed（`select:62-69`）。
- EmailCodeStore 三实现（Local/Db/Redis）装配模式与 sms/challenge 逐条同构（W15 §5.3.7 裁定 1：同 provider 第三组 map）✓。
- 偏差：Redis sms/email bean 未接 config（D2-F4，P3）——装配结构 PASS，配置传播缺口单列。

---

## 四、覆盖率声明

- **已覆盖（main 代码逐行阅读）**：`MfaFactorVerifier`（全）、`MfaChallengeHelper`（全）、`OperationMfaCheckerImpl`（全）、`LoginApiBizModel`（全）、`LoginServiceImpl`（MFA 相关段 :440-1080：completeLogin/mfaVerify/verifySecondFactor/verifyRecovery/incrFailCountOrDiscard/sendSmsCode/sendMfaCode/checkMfaRequired/限流）、`NopAuthUserBizModel`（bindWebauthn/confirmWebauthnRegistration/webauthnBeginVerify/upsertPending/confirmMfa/unbindMfa/verifyWebauthnUnbindAssertion/requireChannelProof/限流段）、`MfaLoginPolicyServiceImpl`（全）、store 家族 8 个 main 类（Db/Redis challenge、Db/Redis SMS、Db/Redis Email、Local challenge/Local SMS 经全文、LocalEmailCodeStore 同构核验）、`MfaStoreProvider`、`auth-service.beans.xml`、`TOTPAuthenticator`（verify/verifyRaw）、`WebAuthnAuthenticator`（verifyAssertion/AssertionCheck 段）、`MfaChallengeStore` 接口契约、`SmsCodeEntry`/`EmailCodeEntry`、`MfaVerifyOperationRequest`、`NopAuthConstants` key 前缀、`nop_auth_mfa_setting` ORM 段。
- **框架原语下沉验证**：`removeIfMatch` 返回值语义（IAsyncMap/LettuceMessageService Lua）、`genUpdateSql` 乐观锁 WHERE 子句、`checkUpdateResult` affected=0 抛错链路。
- **key 隔离核对（B3 支撑）**：`NopAuthConstants:58-64` —— `login:{phone}`（SMS_KEY_LOGIN）/`mfa:{userId}`（SMS_KEY_MFA）/`proof:{userId}`（SMS_KEY_PROOF）/`mfa-email:{userId}`（EMAIL_KEY_MFA）/`proof-email:{userId}`（EMAIL_KEY_PROOF）；消费点 key 构造逐一比对（sendSmsCode:771 / sendMfaCode:823 / verifyChannelProof:413,431 / requireChannelProof:1112,1127 / MfaFactorVerifier:164,177 / NopAuthUserBizModel bindSms 路径经 mfa:{userId}）——前缀+主体维度（phone vs userId）双重隔离，无跨 key 可达路径；Redis 物理键再叠 `sms:code:`/`email:code:` 通道前缀（RedisSmsCodeStore:33/RedisEmailCodeStore:32）。scene 绑定与 key 绑定正交成立。
- **测试树核对（未运行）**：TestOperationMfaE2E（scene/票/窗口重放/审计断言锚点）、TestDbMfaChallengeStore、TestRedisStoreWiring（markVerified/票键过期/CAS/consume 一次性钉定）、TestRedisEmailCodeStore、FakeNosqlService（removeIfMatch 语义）。
- **未覆盖/明示边界**：未做运行时并发压测（静态语义判定 + 框架原语下沉验证替代）；`LocalEmailCodeStore` 仅同构抽样（与 LocalSmsCodeStore 逐字节同构性未全文比对）；D3（恢复码全矩阵）、D5（executor 两检查点/批量语义）、D6（可信设备）归各自维度审计，本报告仅触及交叉锚点。
