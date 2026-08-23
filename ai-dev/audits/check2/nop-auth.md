# nop-auth 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-auth（含 nop-auth-api / nop-auth-dao / nop-auth-service / nop-auth-sso / nop-oauth 等子模块）
- 文件数: 254（src/main/java，剔除 target/、`_gen/` 与 `_` 前缀生成文件后实测；任务描述的 287 为含生成物的口径）
- 覆盖范围声明: 深读约 48 个文件（LoginServiceImpl、LoginApiBizModel、NopAuthUserBizModel、MfaFactorVerifier、MfaChallengeHelper、MfaTrustedDeviceManager、WebAuthnAuthenticator、OperationMfaCheckerImpl、RoleMfaPolicyEvaluator、RoleMfaPolicy、MfaLoginPolicyServiceImpl、MfaStoreProvider、Db/Redis × SmsCode/MfaChallenge/EmailCode 共 6 个 store、DaoLoginSessionStore、DaoUserContextCache、DefaultActionAuthChecker、DefaultDataAuthChecker、SiteMapProviderImpl、SiteCacheDataBuilder、SiteMapApiBizModel、NopAuthRoleBizModel、MfaSensitiveTableBizModel、NopAuthMfaTrustedDeviceBizModel、ChannelBindServiceImpl、UserChannelResolverImpl、AuditServiceImpl、GraphQLAuditLogger、OAuthLoginServiceImpl、SsoLoginWebService、SsoConfig、JWKPublicKeyLocator、JWKSUtils、DefaultUserIdGenerator、DaoUserDelegateService、NopAuthResourceBizModel、nop-oauth 3 个 BizModel 及其余全部小型 entity BizModel）；模式扫描（@Inject private / Spring 注解 / SimpleDateFormat / Random / RuntimeException / printStackTrace / System.out / syncGet / catch(Exception) / 敏感日志）覆盖 100% src/main/java。未深读区域: nop-auth-api 与 nop-oauth-api 的 beans/crud 数据类（约 90 个，纯 getter/setter）、dao 实体类与 biz 接口（约 50 个，薄壳）、sso 的 AccessTokenResponse/UserInfo/JWK 等数据类（已模式扫描）。结论经跨模块交叉验证: AbstractLoginService / JwtAuthTokenProvider / JwtHelper / TOTPAuthenticator / LocalUserContextCache / AbstractUserContextCache（nop-biz-auth-core）、nop-auth.orm.xml、auth-service.beans.xml、xbiz/action-auth 配置。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 4 |
| P3 | 9 |

## 发现列表

### [P1] DaoLoginSessionStore.getSessionInfoForUser 误将 LOGOUT_TYPE_NONE 设到 loginType 字段，公开契约 killLoginAsync/getLoginUserContextAsync 对普通登录会话恒失效

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoLoginSessionStore.java:40-48`
- **维度**: D1（复制粘贴错误）+ D8（公开接口与实现契约不匹配）
- **证据**:
```java
public SessionInfo getSessionInfoForUser(String userName) {
    Guard.notEmpty(userName, "userName");

    NopAuthSession example = new NopAuthSession();
    example.setUserName(userName);
    example.setLoginType(AuthApiConstants.LOGOUT_TYPE_NONE);   // <-- 应为 setLogoutType

    NopAuthSession session = daoProvider.daoFor(NopAuthSession.class).findFirstByExample(example);
    if (session == null)
        return null;
    return new SessionInfo(userName, session.getSessionId());
}
```
- **现状**: 对照同类方法 `getActionSessions`（本类 line 89-102）正确使用 `example.setLogoutType(AuthApiConstants.LOGOUT_TYPE_NONE)`，本方法把"未登出"过滤条件误设到 `loginType` 字段。已核对常量：`AuthApiConstants.LOGOUT_TYPE_NONE = 0`，而合法会话的 loginType 为 1/2/3/4/5/20+（`DaoLoginSessionStore.saveSession` line 60 写入 `request.getLoginType()`，登录请求永不为 0）。因此 `userName=? AND loginType=0` 的 example 查询对密码/短信/SSO 登录的真实会话行**恒不命中**，仅可能命中 `MfaLoginPolicyServiceImpl.ensureSessionRow` 为 OAuth 上下文补建的 loginType=0 行（该路径 `getLoginType()` 缺省 0）——且这些行中已登出的也会被返回（本方法同时缺失 logoutType 过滤）。
- **风险**: `ILoginService.killLoginAsync(userName)`（管理员强制下线）与 `getLoginUserContextAsync(userName)`（AbstractLoginService line 68-73/87-92 均经 `getSessionInfoForUser` 取会话）在默认装配下静默 no-op 或取错行：安全事件中"踢人下线"失效且无任何报错。`nopLoginSessionStore` 在 auth-service.beans.xml line 149 无条件注册为默认实现，属默认路径。缓解: 仓库内无生产代码调用这两个方法（全仓 grep 仅测试桩 TestLoginApiTokenWiring 覆盖），触发依赖下游应用调用 ILoginService 公共 SPI。
- **建议**: 改为 `example.setLogoutType(AuthApiConstants.LOGOUT_TYPE_NONE)`；补一条 DaoLoginSessionStore 单测断言 killLoginAsync 能命中真实登录会话。
- **误报排除**: 已读 `getActionSessions`（同文件正确写法对照）、`AbstractLoginService.getSessionInfoForUser/killLoginAsync/getLoginUserContextAsync`（调用链确认）、`AuthApiConstants`（常量值确认 loginType 与 logoutType 值域不相交）、`DaoLoginSessionStore.saveSession` 与 `MfaLoginPolicyServiceImpl.ensureSessionRow`（真实行的 loginType 取值确认）、auth-service.beans.xml（DaoLoginSessionStore 为默认装配确认）。

### [P1] 登录失败计数为非原子 read-modify-write，并发暴破可绕过 max-login-fail-count 账号锁定

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:338,373-377`
- **维度**: D3（共享可变状态竞争）+ D5（认证锁定绕过）
- **证据**:
```java
user = getAuthUser(request);
if (user != null) {
    failCount = userContextCache.getLoginFailCountForUser(user.getUserName());
    int maxFailCount = CFG_AUTH_MAX_LOGIN_FAIL_COUNT.get();
    if (maxFailCount > 0 && failCount >= maxFailCount) {
        errorCode = ERR_AUTH_LOGIN_CHECK_FAIL_TOO_MANY_TIMES;
    ...
// 失败路径
if (!smsCodeFail) {
    failCount++;
    if (user != null)
        userContextCache.setLoginFailCountForUser(user.getUserName(), failCount);
}
```
- **现状**: `IUserContextCache.getLoginFailCountForUser` 为 `loginFailCache.get(...)`、`setLoginFailCountForUser` 为 `loginFailCache.put(...)`（已核对 nop-biz-auth-core `AbstractUserContextCache` line 90-97，底层为 Caffeine LocalCache），两步之间无原子性。N 个并发失败请求同时读到同一旧值 `n`，各自写回 `n+1`，计数只前进 1。默认 `max-login-fail-count=10`，且 `verify-code.enabled` 默认 false、无 IP 维度失败限流参与该路径（`getLoginFailCountForIp` 存在但本模块未使用）。
- **风险**: 攻击者对已知用户名以高并发（如 50 线程）持续提交错误密码，丢失更新使计数远慢于实际尝试次数，账号锁定阈值形同虚设，暴力破解空间被放大一个数量级以上。登录失败审计（auditLogFail）同样依赖该 failCount 值。
- **建议**: 缓存层提供原子 `incrementLoginFailCount(userName)`（Caffeine `asMap().compute` 或 AtomicLong 值）；或 LoginServiceImpl 改用带返回值的原子上递增 API 后再判断阈值。
- **误报排除**: 已读 `AbstractUserContextCache`/`LocalUserContextCache` 全文确认 get/put 两步无 CAS；确认 LoginServiceImpl 全路径无锁包裹；确认 `NopAuthConfigs` 中 verify-code 默认 false、无其他限流兜底参与密码失败路径；对照本模块内 `checkSmsRateLimit`（LoginServiceImpl line 999-1019）已用 `synchronized + compute` 原子模式，说明该平台具备修正手段，此处为遗漏。

### [P2] DaoUserContextCache.checkExpired 对 lastAccessTime 为 null 的会话行直接 NPE

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoUserContextCache.java:87-89`
- **维度**: D1（NPE / 边界条件）
- **证据**:
```java
protected boolean checkExpired(NopAuthSession session) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime timeoutTime = session.getLastAccessTime().plus(config.getSessionTimeout());
```
- **现状**: ORM 模型中 `LAST_ACCESS_TIME` 非 mandatory（nop-auth.orm.xml nop_auth_session 实体，propId=14 无 mandatory="true"）。平台自身写路径（`DaoLoginSessionStore.saveSession` line 67、`MfaLoginPolicyServiceImpl.ensureSessionRow` line 159）都会设置该字段，但 `NopAuthSessionBizModel` 是未做写收口的裸 CrudBizModel，`NopAuthSession__save` 可创建无 lastAccessTime 的行；历史遗留行同理。
- **风险**: `nop.auth.login.use-dao-user-context-cache=true` 的部署（demo 应用均启用）下，任何指向该行的 sessionId 令牌请求在 `getUserContextAsync` 内 NPE（500），且发生在 `@SingleSession` 会话内，错误不可恢复。
- **建议**: `session.getLastAccessTime() == null` 时按"已过期/非法行"处理（返回 null 并告警），或建行约束补齐该列。
- **误报排除**: 已读本类全文（调用顺序: getEntityById → logoutType 检查 → checkExpired，NPE 前无兜底）；已核对 orm.xml 该列非 mandatory；已核对 NopAuthSessionBizModel（20 行裸 CrudBizModel，mutation 未收口）与 DaoLoginSessionStore/MfaLoginPolicyServiceImpl（正常写路径必设值），确认触发依赖异常来源行。

### [P2] DbSmsCodeStore/DbEmailCodeStore.send 首发插入为 read-then-insert，多节点并发首发撞主键抛未归一异常

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/store/DbSmsCodeStore.java:66-84`（DbEmailCodeStore.java:60-78 同型）
- **维度**: D3（竞态）+ D1
- **证据**:
```java
public String send(String key) {
    long now = System.currentTimeMillis();
    String code = String.format("%06d", MathHelper.secureRandom().nextInt(1_000_000));
    NopAuthSmsCode existing = dao().getEntityById(key);
    if (existing != null) {
        existing.setCode(code); ... dao().updateEntityDirectly(existing);
    } else {
        NopAuthSmsCode e = dao().newEntity();
        e.setCodeKey(key); ... dao().saveEntityDirectly(e);
    }
    return code;
}
```
- **现状**: `codeKey` 为主键（orm.xml 确认）。get 与 insert 之间无原子性，也没有数据库层 upsert。单实例下由本地限流器（`checkSmsRateLimit` 的 60s 间隔）近似串行化保护；但限流追踪是 JVM 本地 Caffeine（`smsPhoneTracker` 等字段为实例字段），**多节点部署时无跨节点互斥**。
- **风险**: 集群部署（或任何绕过本地限流的路径）下，两节点同时对同一 key 首发（如同一手机号首次请求登录验证码）→ 双 INSERT → 一方主键冲突抛 NopException/SQL 异常，用户侧 500。频率低但真实存在，且错误未归一为"请稍后重试"类语义。
- **建议**: 改为数据库原生 upsert（merge / on duplicate key），或 insert 冲突后回退为 update；至少 catch 主键冲突归一为重试语义。
- **误报排除**: 已读两文件全文确认无 upsert/冲突处理；已核对 orm.xml 主键定义；已核对 `LoginServiceImpl.checkSmsRateLimit`/`NopAuthUserBizModel.checkProofRateLimit` 均为实例级本地 Map（无分布式协调），确认多节点下竞态窗口敞开；对照 `DbMfaChallengeStore.create`（UUID 主键，无此问题）。

### [P2] DbMfaChallengeStore 仅惰性 TTL 清理、无批量回收，废弃 challenge 行永久累积

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/store/DbMfaChallengeStore.java:29-36,86-108`
- **维度**: D6（资源耗尽隐患）+ D2
- **证据**:
```java
// peek（line 94-97）：
if (e.getExpireAt() != null && e.getExpireAt() <= now) {
    deleteByToken(challengeToken); // 惰性 TTL 清理
    return null;
}
// 类 javadoc（line 36-37）：
//   TTL 清理为惰性：peek/consume 时过期即删；批量清理为 Follow-up。
```
- **现状**: 过期行只有当**该特定 token 再次被 peek/consume** 时才删除。实际流量中大量 challenge 被放弃（用户在 MFA 一步关页/换号重登；`OperationMfaCheckerImpl.check` 每次拦截都新建 challenge 并抛错，客户端重试又新建）。这些行永远不会被再次访问 → 永不删除。`nop_auth_mfa_challenge` 为 no-tenant 全局表（Redis 实现有 TTL 兜底，DB 实现为默认 `store-type=db`）。
- **风险**: 表无界增长：每次 MFA 拦截登录、每次受限操作级拦截各留一行，长期运行后拖慢该表查询（peek 按主键，影响可控）并占用存储；配合上一条的公开重发端点 `sendMfaCode`（仅 peek 不建行，不放大本问题）评估，主要来源是登录/操作流量本身。
- **建议**: 落实 javadoc 承认的 Follow-up: 定时任务按 `expireAt` 索引批量删除（表已有 expireAt 列，建议加索引）；或部署文档明确推荐 redis store-type。
- **误报排除**: 已读本类全文（create/peek/consume/markVerified 四个入口均无批量清理）；确认 RedisMfaChallengeStore 走 putExAsync TTL（无此问题）而 Db 实现为 beans.xml 默认选择；确认 `LoginServiceImpl.checkMfaRequired`（每次 MFA 登录必建行）与 `OperationMfaCheckerImpl.check`（每次拦截必建行）的建行频率。

### [P2] 敏感表写路径收口未覆盖 NopAuthExtLogin / NopAuthSession，与 MFA 表收口（MfaSensitiveTableBizModel）安全标准不一致

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthExtLoginBizModel.java:11-19`、`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthSessionBizModel.java:10-19`
- **维度**: D5（授权设计缺口）
- **证据**:
```java
@BizModel("NopAuthExtLogin")
public class NopAuthExtLoginBizModel extends CrudBizModel<NopAuthExtLogin> implements INopAuthExtLoginBiz{
    public NopAuthExtLoginBizModel(){
        setEntityName(NopAuthExtLogin.class.getName());
    }
}
```
（NopAuthSessionBizModel 同为裸 CrudBizModel；二者 xbiz 均为空 `<actions/>` 继承全部 mutation。）
- **现状**: 本模块为八张 MFA 相关表专门建立了 `MfaSensitiveTableBizModel` 写收口，理由是"持表级 mutation 权限即可绕过专项防护直接写敏感行"（D5-F1/D6-1 等）。但同属登录能力的两张表未纳入：a) `NopAuthExtLogin` 行是信道登录的身份映射（`UserChannelResolverImpl`/信道登录经 extId 定位 userId），`NopAuthExtLogin__save` 可直接插入 `verified=true, delFlag=0` 的任意 (loginType, extId, userId) 行 = 信道登录第一因子旁路原语（与 Mfa 表被封禁的"植入旁路原语"同族）；b) `NopAuthSession__update` 可改 logoutType/lastAccessTime 复活已登出会话。两表 mutation 暴露为可授权 function point（`_nop-auth.action-auth.xml` line 41-52 登记 `NopAuthExtLogin:mutation`），运行时无 requireAdmin 双重校验（对照 resetUserMfa/resetUserPassword 均有）。
- **风险**: 前提是管理员把对应 mutation 权限授予了不可信角色（默认仅 admin 持有），故非默认可达；但一旦授予即等于交出"任意账号信道登录映射"能力，与该代码库自我设定的安全基线（MFA 表同理由收口）不一致，属纵深防御缺口。
- **建议**: 将两表纳入 `MfaSensitiveTableBizModel` 式收口或加 requireAdmin 运行时校验；ExtLogin 写路径唯一入口收敛到 `ChannelBindServiceImpl`（其三态重绑裁决逻辑已内聚）。
- **误报排除**: 已读两 BizModel 与对应 xbiz（空 actions 继承全量 mutation）确认 mutation 可达；已读 `ChannelBindServiceImpl`（正经绑定入口含唯一索引裁决）与 `_nop-auth.action-auth.xml`（mutation 为可授权功能点）确认旁路面；已读 `MfaSensitiveTableBizModel` javadoc 确认平台自身的收口标准陈述。

### [P3] ERR_AUTH_LOGIN_CHECK_FAIL_TOO_MANY_TIMES 与通用失败错误码可区分，泄露"账号存在且已锁定"信号

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:343-344,388-390`
- **维度**: D5（用户枚举）
- **证据**:
```java
if (maxFailCount > 0 && failCount >= maxFailCount) {
    errorCode = ERR_AUTH_LOGIN_CHECK_FAIL_TOO_MANY_TIMES;
...
// 用户名错误对外也只显示用户名或者密码错误
if (errorCode == ERR_AUTH_LOGIN_WITH_UNKNOWN_USER)
    errorCode = ERR_AUTH_LOGIN_CHECK_FAIL;
```
- **现状**: 未知用户被刻意映射为通用 CHECK_FAIL（防枚举意图明确），但已锁定账号返回独立的 TOO_MANY_TIMES 错误码，攻击者可据此确认"该用户名存在且处于锁定态"。
- **风险**: 轻度用户名枚举 + 锁定状态探测；与同方法内防枚举设计意图相悖。
- **建议**: 若产品可接受，将锁定态也归并为 CHECK_FAIL（仅服务端/审计区分）；或配合验证码门槛后再细化错误。
- **误报排除**: 已读 loginAsync 全流程确认三种错误码的返回路径与映射逻辑；确认 TOO_MANY_TIMES 分支要求 user != null（存在用户）且 failCount 达阈值。

### [P3] MfaFactorVerifier 每次验证对共享 TOTPAuthenticator 单例调用 setSkew 变异共享可变状态

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaFactorVerifier.java:145`
- **维度**: D3（共享可变状态 / volatile 缺失）
- **证据**:
```java
if (MFA_TYPE_TOTP.equals(mfaType)) {
    if (totpAuthenticator == null || StringHelper.isEmpty(setting.getSecret()))
        return false;
    // 确保 skew 与配置一致（一期 verifyTotp 口径）
    totpAuthenticator.setSkew(CFG_AUTH_MFA_TOTP_WINDOW_SKEW.get());
```
- **现状**: `nopTotpAuthenticator` 为容器单例（beans.xml line 110-112），`TOTPAuthenticator.skew` 为普通 int 字段（非 volatile，已核对 nop-biz-auth-core line 46）。每次请求写入同一配置值，正常情况下无可见性问题；仅配置热更瞬间可能出现请求间读到新旧值的极窄窗口。
- **风险**: 当前写值恒定，属良性；但该模式（每请求变异单例字段）是并发隐患模板，未来若 setSkew 语义复杂化（如校验失败抛错）或值随请求变化即成缺陷。
- **建议**: 移除 per-call setSkew，构造/配置变更时一次性设置；或将 skew 作为 verify 参数传入（verifyRaw 已支持参数化 skew）。
- **误报排除**: 已读 TOTPAuthenticator（skew 非 volatile、verifyRaw 支持参数）与 beans.xml（单例装配 + 初始化时 set skew）确认当前值恒定、无现实竞态后果，故定级 P3 而非更高。

### [P3] smsCodeStore 为 null 时仍以 null code 调用发送器，未 fail-closed（与 email 侧判空不一致）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:867-868,919-920`；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:364-365`
- **维度**: D1（错误的空值处理）+ D4
- **证据**:
```java
// LoginServiceImpl.sendSmsCode：
String code = smsCodeStore == null ? null : smsCodeStore.send(SMS_KEY_LOGIN + phone);
sendSms(phone, code);   // sendSms 仅判 smsSender==null，不判 code
```
- **现状**: `sendSms` 只校验 `smsSender == null`。若 smsCodeStore 未装配而 smsSender 已装配（仅手工 wiring / 部分装配场景；标准装配经 MfaStoreProvider 工厂 bean，缺配置会在注入期失败），短信会以 `params=[null]` 发出，用户收到无效验证码且无错误日志。对照 email 侧 `sendMfaEmailCode`（line 934-938）对 `emailCodeStore == null` 显式 fail-closed，行为不一致。
- **风险**: 仅非标准装配可达；后果是无效短信而非安全问题。属空值处理不规范。
- **建议**: `sendSms`/`sendSmsForBinding` 增加 `code` 判空 fail-closed，与 email 路径对齐。
- **误报排除**: 已读 `sendSms`/`sendMfaEmailCode`/`bindSms`/`requireChannelProof` 四处对照；已核对 beans.xml 的 nopActiveSmsCodeStore 工厂装配（生产路径 store 非空）确认现实触发面仅限手工装配。

### [P3] 恢复码生成 Math.abs(secureRandom().nextLong()) 存在 Long.MIN_VALUE 理论负数分支

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:1800-1803`
- **维度**: D1（边界条件）
- **证据**:
```java
private static String generateRecoveryCode() {
    long n = Math.abs(MathHelper.secureRandom().nextLong()) % 10_000_000_000L;
    return StringHelper.leftPad(String.valueOf(n), RECOVERY_CODE_DIGITS, '0');
}
```
- **现状**: `Math.abs(Long.MIN_VALUE)` 返回自身（负数），`% 10^10` 仍为负 → `String.valueOf` 产生带 `-` 前缀的短串，leftPad 后形如 `-000000123`（非 10 位数字），落库 hash 后永不匹配任何用户输入（不可利用，仅该码作废）。概率 2^-64，实际不可触发。
- **风险**: 理论性边界缺陷，无现实触发路径。
- **建议**: 使用 `secureRandom().nextInt(10_000_000_000)` 不存在（超 int 范围），可改为 `Long.remainderUnsigned` 或对 `nextLong() & Long.MAX_VALUE` 取模。
- **误报排除**: 已读 SecureRandom/Math.abs 语义与 leftPad 调用确认行为；确认概率 2^-64 不足以升级严重度。

### [P3] debug/info 级日志输出验证码明文与完整手机号/邮箱（PII）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:972,988,1338,1477`
- **维度**: D5（敏感信息日志泄漏）
- **证据**:
```java
LOG.info("nop.auth.email-code-sent:email={}", email);          // line 972
LOG.info("nop.auth.sms-code-sent:phone={}", phone);            // line 988
LOG.debug("nop.auth.verify-code-mismatch:code={},cached={}", request.getVerifyCode(), cachedCode); // line 1338
LOG.debug("nop.login.generate-verify-code:{}", verifyCode.getCode()); // line 1477
```
- **现状**: 验证码明文仅在 debug 级输出；手机号/邮箱为 info 级全量输出（同库其他位置已建立 maskPhone/maskEmail 脱敏先例，如 NopAuthUserBizModel.maskPhone 与限流错误参数均脱敏）。
- **风险**: 生产误开 debug 级或日志聚合降级时验证码入日志；info 级 PII 长期沉淀于日志系统（合规视角）。
- **建议**: 发送成功日志改用 maskPhone/maskEmail；验证码日志移除或仅记长度。
- **误报排除**: 已核对本模块内既有脱敏工具（maskPhone/maskEmail static 方法）与 OAuthLoginServiceImpl.refreshToken 的"只记长度+前缀"先例（line 246-247），确认平台有既定脱敏标准而此处未沿用。

### [P3] OAuthLoginServiceImpl.generateVerifyCode 抛裸 UnsupportedOperationException 且消息为未解析的 error-code 字符串

- **文件**: `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/login/OAuthLoginServiceImpl.java:230-232`
- **维度**: D4（bare RuntimeException / 错误信息）
- **证据**:
```java
@Override
public String generateVerifyCode(String verifySecret) {
    throw new UnsupportedOperationException("nop.err.auth.not-impl");
}
```
- **现状**: 未使用 NopException + ErrorCode 体系（AGENTS.md 两级错误策略），消息串 `nop.err.auth.not-impl` 是 error-code 形态的裸字符串，客户端将收到未本地化的 500。
- **风险**: 无直接运行时危害；违反模块错误处理规范，异常语义/国际化缺失。
- **建议**: 改为 `throw new NopException(ERR_...)`（SsoErrors 增补对应码）。
- **误报排除**: 已确认 SsoErrors 存在可扩展错误码体系；确认 ILoginSpi 该方法在 SSO 登录服务上下文无合法调用方（LoginApiBizModel.generateVerifyCode 注入的是主登录服务），仅契约占位。

### [P3] extractClientIp 完全信任 X-Forwarded-For / X-Real-IP 可伪造头

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java:574-592`（NopAuthUserBizModel.java:1601-1619 同型副本）
- **维度**: D5（限流绕过）
- **证据**:
```java
Object xff = headers.get("X-Forwarded-For");
...
if (xff != null && !xff.toString().isEmpty()) {
    String ip = xff.toString().split(",")[0].trim();
    return ip.isEmpty() ? null : ip;
}
```
- **现状**: 取 XFF 首段作为客户端 IP。代码注释已自我声明"IP 限流为次要防线"；且 Caffeine tracker 上限注释亦承认"XFF可伪造IP键"（LoginServiceImpl line 833-834）。攻击者轮换伪造 XFF 即可绕过 IP 日上限维度，但手机号/邮箱维度的主限流不受影响。
- **风险**: 已被主维度限流缓解，且缓存键硬上限防资源耗尽；属已知的次要防线弱点记录。
- **建议**: 若部署于可信代理后，从最后一跳可信代理追加的 XFF 段取值；两处同型实现建议收敛为单一工具方法。
- **误报排除**: 已读两处实现与限流消费点（checkSmsRateLimit/checkEmailRateLimit），确认手机号/邮箱维度为主约束、IP 仅辅助；确认 Caffeine maximumSize 防键空间爆炸。

### [P3] NopAuthResourceBizModel.refreshSiteMapCache 为无鉴权约束的 @BizQuery，可被反复触发全量缓存重建

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthResourceBizModel.java:57-59`
- **维度**: D6（性能隐患）
- **证据**:
```java
@BizQuery
public void refreshSiteMapCache() {
    siteMapProvider.refreshCache();
}
```
- **现状**: 无 requireAdmin/操作语义约束，持 NopAuthResource 查询权限的任意用户可高频调用；每次 clear 后的 get 会触发 `loadSiteData`（全表读 nop_auth_site/resource/role_resource + i18n 规整 + debug 模式 dump）。
- **风险**: 缓存击穿式 DB 压力（DoS 面窄：需持查询权限）；对照同类 `updateRoleResources` 在变更后主动刷新的语义，此查询形态暴露面不必要。
- **建议**: 加 admin 校验或改为 @BizMutation + 权限点；至少加简单频控。
- **误报排除**: 已读 `SiteMapProviderImpl.loadSiteData`/`getSites`/`getResources`/`getRoleResources` 确认重建成本（三次全表 findAll + 逐 locale 缓存）；已核对 xbiz 未对该 action 附加约束。

### [P3] enableActionAuth=false（默认）时已登录用户可获取完整菜单结构（含无权访问项）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/sitemap/SiteMapProviderImpl.java:222-237`
- **维度**: D5（信息暴露，配置依赖）
- **证据**:
```java
if (enableActionAuth) {
    ...
} else {
    // enableActionAuth=false时跳过权限过滤，但匿名请求不能借此获取完整菜单结构
    // （路由/组件/显示名是侦察输入）：按空角色集应用auth过滤，仅noAuth项保持可用
    if (cache != null && StringHelper.isEmpty(userId) && roleIds.isEmpty())
        applyAuthFilter(site.getResources(), cache.getResourceToRoles(), roleIds);
    ...
}
```
- **现状**: `nop.auth.enable-action-auth` 默认 false（beans.xml line 11）。代码只对**匿名**请求做了防侦察过滤，任何已登录用户（哪怕仅持有最小角色）在该默认配置下拿到全量菜单树（路由、组件、显示名）。
- **风险**: 菜单/功能点名称与路由构成侦察输入；实际操作仍受 API 层权限拦截，故为信息暴露而非越权。属默认配置下的设计取舍，非实现 bug。
- **建议**: 文档明示该默认行为的风险；或默认对登录用户也按角色过滤（仅 noAuth + 已授权项）。
- **误报排除**: 已读 `filterAllowedMenu` 全文与 beans.xml 默认值、`containsRole`/`applyAuthFilter` 语义，确认登录态（userId 非空）时 else 分支完全跳过过滤；确认为配置依赖行为而非编码错误，故定 P3。

---

## 补充说明（已排查未列入的疑点）

以下疑点经交叉验证后排除，列出以防后续重复排查：
1. **checkMfaRequired 未包 runWithTenant 的租户过滤疑虑**: 已核对 orm.xml——nop_auth_user / nop_auth_mfa_setting / nop_auth_mfa_challenge / nop_auth_role_mfa_policy 等均带 `no-tenant` 标记，登录期无租户上下文的 DAO 读取不受租户过滤影响，非缺陷。
2. **LoginApiBizModel.getLoginResultAsync 等公开端点对非法 token 的 NPE 疑虑**: JwtHelper.parseToken 对非法/空 token 抛 NopException 而非返回 null（logoutAsync 中的 `authToken == null` 判断为死代码但无害），getUserContextAsync 不会收到 null token。
3. **verifySecondFactorAndComplete 对 @Nullable mfaFactorVerifier 的 NPE 疑虑**: beans.xml 注册 nopMfaFactorVerifier（ioc:default），生产装配非空；@Nullable 仅为测试 wiring 预留。
4. **webauthnAuthOptions publicAccess 跨场景读取疑虑**: 非 login 场景（operation/register/unbind/add）均已校验 payload.sessionId 与当前会话一致；scene=null 仅一期存量兼容。
5. **DbMfaChallengeStore/DbSmsCodeStore 的 EQL 条件写（markVerified/条件 DELETE/incrFail）**: 原子性设计正确，affected-row 判定完备。
6. **Redis store 家族**: putExAsync(psetex)/get(不刷 TTL)/removeIfMatch(Lua CAS)/counter.increment(INCRBY) 组合语义正确，CAS 失败返回 EXPIRED 的双花防御已闭合。
7. **WebAuthn 验证**: challenge/origin/rpId 校验链完整，credentialId 取自 attestedCredentialData（防客户端伪造），signCount 条件 UPDATE 单调写并发安全。
8. **NopAuthUserBizModel 全链路**: 恢复码条件置位、TOTP 窗口推进、webauthn 三 ceremony、受限会话 enrollment-attack 防线（requireChannelProof/verifyChannelProof 票机制）、联系方式变更拦截（guardContactChange）均已按防绕过语义实现，未发现可利用缺口。
