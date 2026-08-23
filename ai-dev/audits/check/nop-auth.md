# nop-auth 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-auth（api/dao/service/sso/oauth）
- 文件数: 实测 src/main/java 非 `_gen` 文件 252 个（api 84 / dao 62 / service 66 / sso 17 / oauth 23；另有 app 模块各 1 个过路文件）
- 覆盖范围声明:
  - **深读**（逐行）: 登录链路（LoginServiceImpl、LoginApiBizModel、DaoLoginSessionStore、DaoUserContextCache）、用户与 MFA 业务（NopAuthUserBizModel 全文、MfaFactorVerifier、MfaTrustedDeviceManager、WebAuthnAuthenticator、MfaLoginPolicyServiceImpl、OperationMfaCheckerImpl、RoleMfaPolicyEvaluator、MfaChallengeHelper、MfaStoreProvider）、验证码/challenge store（Db/Redis 的 SmsCodeStore 与 Db/RedisMfaChallengeStore）、权限检查（DefaultActionAuthChecker、DefaultDataAuthChecker、SiteMapProviderImpl、SiteCacheData、SiteCacheDataBuilder）、SSO/OAuth（SsoLoginWebService、OAuthLoginServiceImpl、SsoConfig、JWKPublicKeyLocator、JWK、RSAPublicJWK）、渠道绑定（ChannelBindServiceImpl、UserChannelResolverImpl）、审计（AuditServiceImpl、GraphQLAuditLogger）、全部 entity BizModel、beans.xml 装配、数据模型 tagSet（nop-auth.orm.xml、nop-oauth.orm.xml、data-auth.xml）
  - **扫描**（grep + 抽读）: 全部 252 文件的空 catch / bare RuntimeException / printStackTrace / Random / MessageDigest / synchronized / @Inject private / @Value；api 层 bean 与 dao 层 entity/mapper 抽查
  - **未覆盖**: nop-biz-auth-core（IPasswordEncoder/JwtAuthTokenProvider/AbstractLoginService 等，不在本单元）、Db/RedisEmailCodeStore 逐行（与已深读的 SmsCodeStore 同构镜像，仅 grep 验证）、api 层消息类字段级核对、GraphQL 引擎侧权限执行细节（仅核对了 ReflectionBizModelBuilder 默认权限推导与 GraphQLActionAuthChecker）
  - 结论性印象: 该模块整体安全工程水准高（BCrypt、SecureRandom、原子消费、条件写、fail-closed、审计齐备），以下发现多为边角与配置条件触发问题

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 1 |
| P2 | 3 |
| P3 | 9 |

## 发现列表

### [P0] max-login-fail-count 配置为 0/负数时登录链路跳过全部凭证校验（认证绕过）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:335`
- **维度**: D5（越权/认证绕过）、D1
- **证据**:
```java
int maxFailCount = CFG_AUTH_MAX_LOGIN_FAIL_COUNT.get();
if (maxFailCount > 0) {
    if (failCount >= maxFailCount) {
        errorCode = ERR_AUTH_LOGIN_CHECK_FAIL_TOO_MANY_TIMES;
    } else if (!isAllowLogin(user)) {
        errorCode = ERR_AUTH_USER_NOT_ALLOW_LOGIN;
    } else if (request.getLoginType() == LOGIN_TYPE_PHONE_SMS) {
        CodeVerifyResult r = smsCodeStore == null ? ...
    } else if (needCheckPassword(request) && !passwordMatches(user, request)) {
        errorCode = ERR_AUTH_LOGIN_CHECK_FAIL;
    }
}
```
- **现状**: 锁号上限同时充当了**全部凭证校验的总开关**。`CFG_AUTH_MAX_LOGIN_FAIL_COUNT`（`nop.auth.login.max-login-fail-count`，默认 10，见 NopAuthConfigs.java:53-54）被设为 0 或负数时，整个 `if (maxFailCount > 0)` 块被跳过：密码不比对、短信验证码不校验、`isAllowLogin`（账号状态/过期检查）也不执行，errorCode 保持 null，直接进入 `completeLogin` 签发 token。
- **风险**: 运维将 `max-login-fail-count=0` 理解为"关闭锁号"是常见约定（配置描述"连续登录验证失败之后会临时禁用用户一段时间"未禁止 0），一旦设置即导致：已知任意用户名/手机号/邮箱 + 任意密码即可登录；被禁用（USER_STATUS_DISABLED）与已过期账号同样可登录。SMS 登录（loginType=5）任意验证码通过。完全认证绕过 + 越权。
- **建议**: 凭证校验与锁号判定解耦：`maxFailCount <= 0` 只应跳过 `failCount >= maxFailCount` 分支，`isAllowLogin`/SMS 校验/密码比对必须无条件执行；并对配置值 <0 显式报错。
- **误报排除**: 已核对默认值 10（NopAuthConfigs.java:53-54），默认部署不受影响；已通读 loginAsync 全流程确认无其他密码校验路径（needCheckPassword/passwordMatches 仅在此块内调用）；触发条件为单条配置项，路径现实。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 确认属实，已修复。`LoginServiceImpl.loginAsync` 将锁号判定与凭证校验解耦：锁号分支收窄为 `maxFailCount > 0 && failCount >= maxFailCount`，`isAllowLogin`/SMS 验证码/密码比对无条件执行；错误码与分支次序不变，`maxFailCount > 0` 场景行为不变。测试：`nop-auth-service` `TestLoginCredentialCheckWhenLockoutDisabled#testWrongPasswordRejectedWhenMaxFailCountZero`（修复前 max-login-fail-count=0 时错误密码直接通过校验进入 completeLogin 签发 token）、`#testDisabledUserRejectedWhenMaxFailCountZero`（修复前被禁用用户 + 正确密码仍可登录），另有对照组 `#testCorrectPasswordStillSucceedsWhenMaxFailCountZero`、`#testWrongPasswordRejectedWithDefaultMaxFailCount` 钉定合法登录与默认配置行为不变。

### [P1] 表模式数据权限配置变更永不生效（无缓存失效、无 TTL、check-changed 配置为空挂）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultDataAuthChecker.java:69`、`:158-160`；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthRoleDataAuthBizModel.java:17`
- **维度**: D5（权限撤销失效）、D3（缓存失效）
- **证据**:
```java
// NopAuthRoleDataAuthBizModel: 裸 CrudBizModel，无 afterEntityChange / clearCache
public class NopAuthRoleDataAuthBizModel extends CrudBizModel<NopAuthRoleDataAuth> ...

// DefaultDataAuthChecker:
private DataAuthModel getAuthModel() {
    return modelCache.getObject(true, this::loadDataAuthModel, CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT.get());
}
```
- **现状**: `nop.auth.data-auth-cache.check-changed`（NopAuthConfigs.java:61-63，默认 true）在模块内**无任何消费点**（grep 仅命中定义）；`checkChanged=true` 只检测底层数据权限**资源文件**变化。`CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT` 默认 null → `getObject` 不做周期性 clear。开启 `nop.auth.use-data-auth-table=true` 后经 `NopAuthRoleDataAuthBizModel` 增删改角色数据权限行，缓存中的 DataAuthModel 不会重建，`clearCache()` 也无人调用。
- **风险**: 表模式下收紧/收紧数据权限（撤销某角色的行级过滤豁免）永不生效，直到重启或 data-auth.xml 文件被改动触发资源变更检查；放宽方向同样滞留。权限收紧失效属于越权面。
- **建议**: `NopAuthRoleDataAuthBizModel` 覆写 `afterEntityChange` 调 `DefaultDataAuthChecker.clearCache()`（对齐 NopAuthResourceBizModel → siteMapProvider.refreshCache() 先例）；或落地 check-changed 配置（按表 max(updateTime) 探测）；或为 timeout 配置非 null 默认值兜底。
- **误报排除**: 已确认 `CacheEntryManagement.getObject(true, loader, timeout)` 中 timeout null 不 clear（nop-core CacheEntryManagement.java:54-58）；已 grep 全模块确认 clearCache 无调用方、check-changed 配置无读取点；表模式默认关闭（use-data-auth-table=false），故评 P1 而非 P0。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。`NopAuthRoleDataAuthBizModel` 覆写 `afterEntityChange` 调用 `DefaultDataAuthChecker.clearCache()`（对齐 NopAuthResourceBizModel → siteMapProvider.refreshCache() 先例）；`clearCache()` 补 modelCache null 防御（lazyInit 未执行时缓存本就为空）。check-changed 配置落地不做——refreshCache 即时失效语义更强，探测式兜底无增量价值；timeout 兜底不做——有即时失效后 TTL 反而重新引入最长滞留窗口。新增 `TestAuthPermissionCacheAndAdminGuard`（变更即清理/重复变更重复清理/未注入 checker 时 no-op）。红验证：测试对 HEAD 版本无法编译（dataAuthChecker 字段不存在，修复目标在 HEAD 完全缺失的编译级红）。

### [P2] 角色-资源授权变更不刷新 sitemap 权限缓存，权限撤销最长延迟 10 分钟生效

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthRoleBizModel.java:133-147`；对照 `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthResourceBizModel.java:44-47`
- **维度**: D5（权限缓存未失效）
- **证据**:
```java
// NopAuthRoleBizModel.updateRoleResources: 修改 NopAuthRoleResource 行，无任何缓存刷新
super.updateRelationsEx(NopAuthRoleResource.class.getName(), "roleId", fixedProps, filter,
        true, "resourceId", resourceIds);

// 只有 NopAuthResourceBizModel.afterEntityChange 刷新：
siteMapProvider.refreshCache();
```
- **现状**: `SiteCacheData.permissionToRoles`（DefaultActionAuthChecker.isPermitted 的判定源）在缓存构建时从 `NopAuthRoleResource` 全表装载。`updateRoleResources`、`NopAuthRoleResourceBizModel`（裸 CrudBizModel）变更授权关系后均不调用 `refreshCache()`，仅靠 `nop.auth.site-map.cache-timeout`（默认 10 分钟，NopAuthConfigs.java:38-39）兜底过期。
- **风险**: 撤销某角色的操作权限后，该角色已登录用户（乃至新登录用户）在最长 10 分钟内仍可通过 `isPermitted` 校验执行原操作；资源实体变更会刷新而授权关系变更不刷新，行为不一致，易被误判为已生效。
- **建议**: `updateRoleResources` 成功后与 `NopAuthRoleResourceBizModel.afterEntityChange` 一并调用 `siteMapProvider.refreshCache()`；或在文档中把 10 分钟滞后明确为契约。
- **误报排除**: 已 grep 全模块 `refreshCache|clearCache` 调用点确认仅 NopAuthResourceBizModel 两处；已确认 loading cache 带 10 分钟 TTL，变更最终会传播，故 P2 而非 P1。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。`updateRoleResources` 在 `super.updateRelationsEx` 成功后调用 `siteMapProvider.refreshCache()`（updateRelationsEx 直连 dao 不经过 BizModel 的 afterEntityChange）；`NopAuthRoleResourceBizModel` 覆写 `afterEntityChange` 同步刷新（覆盖直接 CRUD 该实体的路径）。红验证：两处注入字段对 HEAD 均不存在（编译级红）；`TestAuthPermissionCacheAndAdminGuard.testRoleResourceChangeRefreshesSiteMapCache` 钉定行为。

### [P2] 会话表/OAuth 授权表敏感令牌列与 clientSecret 缺少 masked/not-pub 标记，查询接口可返回明文令牌

- **文件**: `nop-auth/model/nop-auth.orm.xml:680-683`；`nop-auth/nop-oauth/model/nop-oauth.orm.xml:124-125`、`:26-92`；`nop-auth/nop-auth-api/src/main/java/io/nop/auth/api/beans/NopAuthSessionOutputBean.java:236-260`；`nop-auth/nop-oauth/nop-oauth-api/src/main/java/io/nop/oauth/api/beans/NopOauthAuthorizationOutputBean.java:114-124`
- **维度**: D5（敏感信息泄露）
- **证据**:
```xml
<!-- nop-auth.orm.xml: 会话表两个令牌列无 tagSet（对照 CACHE_DATA 有 not-pub） -->
<column code="ACCESS_TOKEN" ... propId="15" stdDataType="string" stdSqlType="VARCHAR" ui:show="X" .../>
<column code="REFRESH_TOKEN" ... propId="16" stdDataType="string" stdSqlType="VARCHAR" ui:show="X" .../>
<column code="CACHE_DATA" ... tagSet="not-pub" ui:show="X" .../>

<!-- nop-oauth.orm.xml: CLIENT_SECRET 及 authorizationCodeValue/accessTokenValue/
     refreshTokenValue/oidcIdTokenValue/state 等均无 masked/not-pub -->
<column code="CLIENT_SECRET" displayName="客户端密码" name="clientSecret" ... />
```
- **现状**: `NopAuthUser.password` 用 `tagSet="masked,var,not-pub"` 防泄露，但同类敏感字段未对齐：`NopAuthSession.accessToken/refreshToken`（OutputBean 含这两个字段）、`NopOauthAuthorization` 全部令牌值列（含 state、authorization code、access/refresh/id token）、`NopOauthRegisteredClient.clientSecret`（OutputBean 含）均可作为 GraphQL 出参发布。对应 BizModel（NopAuthSessionBizModel、NopOauthAuthorizationBizModel、NopOauthRegisteredClientBizModel）均为裸 CrudBizModel，findPage/get 直接返回实体。
- **风险**: 任何被授予这些 biz 对象查询权限的角色（会话管理/授权管理控制台是常见授权场景）可读取全部用户的活跃 accessToken/refreshToken、OAuth 授权码与 client secret，构成直接会话接管/客户端仿冒原语。`ui:show="X"` 只影响缺省前端展示，不约束 API 发布。
- **建议**: 为上述列补 `tagSet="masked,not-pub"`（写路径如需保留用 `var`）；clientSecret 建议只写不读（对照 user password 形态）。
- **误报排除**: 已核对 OutputBean 字段确实包含 accessToken/refreshToken/clientSecret/各 tokenValue；已确认同库 user.password/salt 与 session.cacheData 均有 not-pub/masked 先例，属遗漏而非全局策略。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。ORM 源列补 tagSet（session.accessToken/refreshToken → `masked,not-pub`；oauth 的 state/authorizationCodeValue/accessTokenValue/refreshTokenValue/oidcIdTokenValue → `masked,not-pub`；clientSecret → `masked,var,not-pub` 对照 user.password 只写不读形态），经 codegen 再生成 `_NopAuthSession.xmeta`/两个 oauth xmeta（published=false）与 OutputBean（not-pub 字段从出参 Bean 移除）；全仓库 grep 确认被移除的 getter/setter 无生产消费方。masked 同时使 SQL 参数日志脱敏（GenSqlHelper TAG_MASKED）。新增 `TestNopAuthSensitiveColumnsNotPublished`（3 用例）+ `TestNopOauthSensitiveColumnsNotPublished`（3 用例，含"clientSecret 保持可写"与"常规字段仍发布"对照组）。红验证：HEAD 的 `_NopAuthSession.xmeta` 中 accessToken/refreshToken 无 tagSet 且 published 缺省（=可发布），与处置后 git diff 呈相反状态。

### [P2] resetUserPassword/enableUser/disableUser 缺少运行时 admin 校验，数据权限层对普通用户仅按租户隔离

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:1941-1951`、`:1979-1996`；`nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/auth/nop-auth.data-auth.xml:10-14`
- **维度**: D5（垂直越权防御不完整）
- **证据**:
```java
// resetUserPassword：仅 this.get() 的数据权限检查，无 requireAdmin（对照 resetUserMfa:1207 有 requireAdmin）
public void resetUserPassword(@Name("userId") String userId, @Name("password") String password, ...) {
    NopAuthUser user = this.get(userId, false, context);   // checkDataAuth("get", ...)
    passwordPolicy.checkAllowedPassword(user.getUserName(), password);
    ...
}

<!-- 默认数据权限：user 角色可读同租户全部用户 -->
<role-auth id="default" roleIds="user">
    <filter><eq name="tenantId" value="${$context.tenantId}"/></filter>
</role-auth>
```
- **现状**: `resetUserMfa`/`saveMfaPolicy`/`removeWebauthnCredential` 均有运行时 `requireAdmin`（注释明言"@Auth 权限门禁 + 运行时角色校验 defense-in-depth"），而破坏力同级或更高的 `resetUserPassword`、`enableUser`、`disableUser` 没有。其唯一防线是默认操作权限 `NopAuthUser:resetUserPassword`（未在站点资源配置映射时 fail-closed 拒绝），数据权限层对 `user` 角色只过滤租户。
- **风险**: 一旦部署将 `NopAuthUser:resetUserPassword` 权限授予任何非 admin 角色（如客服/运维分组——这类"重置密码"下放很常见），该角色即可重置同租户**任意用户（含 admin）**的密码并接管账号；同租户数据权限对此无约束。disableUser/enableUser 同理（可禁用管理员）。
- **建议**: 三个动作补 `requireAdmin`（复用 resetUserMfa 私有方法抽公共），或将数据权限收紧为"仅本人 + admin 全量"。
- **误报排除**: 已核对 CrudBizModel.get 确实执行 checkDataAuth("get")；已核对 ReflectionBizModelBuilder.java:359-364 默认权限推导（无 @Auth 时权限为 `bizObj:opType|bizObj:action`，未映射即拒），故默认部署安全，评 P2 而非 P1。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。三个动作方法开头补 `requireAdmin(context)`（复用 resetUserMfa 的运行时 admin 校验，消息通用化为 account management action；数据权限/操作权限门禁保持不变，纯 defense-in-depth 收紧）。新增 `TestAuthPermissionCacheAndAdminGuard`（非 admin 三动作拒绝 + admin 对照组验证门禁放行）。红验证：HEAD 版本下三个测试均以 "nothing was thrown" 失败（requireAdmin 完全缺失）。

### [P3] 登录失败审计在 failCount>1 时覆盖丢失 loginType/principalId

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:1414-1423`
- **维度**: D4（审计信息丢失）
- **证据**:
```java
Map<String, Object> map = new LinkedHashMap<>();
map.put("loginType", request.getLoginType());
map.put("principalId", request.getPrincipalId());
audit.setRequestData(JSON.stringify(map));

if (failCount > 1) {
    Map<String, Object> result = new HashMap<>();
    result.put("failCount", failCount);
    audit.setRequestData(JSON.stringify(result));   // 覆盖而非合并
}
```
- **现状**: 第 2 次及以后的登录失败审计记录中 requestData 只剩 failCount，登录类型与主体标识丢失。
- **风险**: 暴力破解排查时无法定位攻击目标账号/登录方式（恰是最需要审计的场景）。
- **建议**: `result.putAll(map)` 合并后再序列化。
- **误报排除**: 直接代码证据，无环境条件。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。failCount 并入同一份 requestData（`map.put("failCount", failCount)` 后统一序列化），不再覆盖。新增 `TestLoginRateLimitAndAudit`（failCount=3 时保留 principalId/loginType/failCount + failCount=1 时无 failCount 的对照组）。红验证：HEAD 版本下实测 `requestData={"failCount":3}`——与审计证据逐字一致。

### [P3] 本地限流时间戳更新非原子，并发突发可穿透发送间隔限制

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:984-997`（同型：1031-1044、`NopAuthUserBizModel.java:1447-1461、1516-1535`）
- **维度**: D3
- **证据**:
```java
long[] phoneEntry = smsPhoneTracker.compute(phone, (k, v) -> { ... return new long[]{v[0], v[1]+1, today}; });
if (phoneEntry[1] > 1 && (now - phoneEntry[0]) < interval * 1000L) { throw ...; }
...
phoneEntry[0] = now;   // compute 外裸写 long[]，非原子
```
- **现状**: 计数在 `compute` 内原子递增，但 `lastSendMs` 在锁外直接赋值；两个并发请求可同时读到旧时间戳并同时通过间隔检查（且 64 位 long 数组元素写非原子理论可见撕裂）。多实例部署下本地为每节点限流，本就近似值。
- **风险**: 高并发下同手机号/邮箱的实际发送间隔可小于配置值（限流被部分绕过）；单机场景影响有限。
- **建议**: 把 lastSendMs 更新挪进 `compute` lambda（在校验前先原子占用时间戳，超限时抛错回滚计数或接受多计一次）。
- **误报排除**: 已核对同文件 email/proof 三处同型代码；非确定性触发故 P3。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。四处（LoginServiceImpl 的 sms/email 两处 + NopAuthUserBizModel 的 proof/email 两处）间隔检查与 lastSendMs 占用收入 `synchronized (tracker)` 原子区（IP 维度计数仅 compute 原子递增，无锁外写，不需处理）。新增 `TestLoginRateLimitAndAudit.testConcurrentBurstAllowsExactlyOne`（32 线程 CyclicBarrier 对齐后同手机号并发，断言恰好 1 个通过）钉定修复后不变式；顺序第二次发送被拒对照。红验证：竞态窗口极窄（compute 原子递增后的纳秒级窗口），HEAD 版本下 32 线程未能稳定复现多头通过（非确定性缺陷，如实注明）；修复正确性依据是检查+占用原子化后窗口在构造上不存在。

### [P3] 限流追踪 Map 永不清理且 IP 键取自可伪造的 X-Forwarded-For

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:826-828、1015-1016`；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java:574-592`
- **维度**: D6（内存缓慢增长）、D5
- **证据**:
```java
private final Map<String, long[]> smsPhoneTracker = new ConcurrentHashMap<>();  // 无任何清理/容量上限
...
Object xff = headers.get("X-Forwarded-For");
if (xff != null && !xff.toString().isEmpty()) {
    String ip = xff.toString().split(",")[0].trim();   // 完全信任首个 XFF 值
```
- **现状**: 7 个限流 Map（两类合计）键只增不减（跨天只重置计数不删除键）；clientIp 取 XFF 首值无代理白名单校验，攻击者可伪造任意 IP 作为键。
- **风险**: 长生命周期进程内存缓慢增长（键总量受真实手机号数与每日上限约束，增长慢，故 P3）；伪造 XFF 可绕过 IP 日限额维度并注入任意 IP 键（手机号/邮箱维度限流仍有效）。
- **建议**: 按日清理过期键或换 Caffeine maximumSize；XFF 解析仅信任已知代理链。
- **误报排除**: 已核对未注册手机号在 allow-register=false 时提前 return 不落键、per-phone daily-limit 限制单键增长速率，DoS 面有界，故降为 P3。

> **处置（fix-ai-check 分支，2026-08-23）**: 部分修复 + 一项裁定不修复。Map 无界增长已修复：7 个限流追踪 Map 全部换为 Caffeine asMap 视图（初版硬编码 50_000/2 天；review 整改后改由配置变量 `nop.auth.rate-limit.tracker-max-size`（默认 50_000）/ `tracker-expire`（默认 48h）驱动，NopAuthConfigs 定义，LoginServiceImpl 与 NopAuthUserBizModel 的 helper 统一读取）——硬上限防 XFF 伪造 IP 键不受真实手机号数约束的无界增长，2 天过期兜底清跨天残留；驱逐最冷键最坏使限流状态重置，对审计自述的"多实例部署下本就近似值"语义可接受。**XFF 伪造部分裁定不修复**：正确做法是仅信任已知代理链（如仅当直连 peer 是可信代理时才采信 XFF），这需要部署环境的网络拓扑知识，平台层无法内置白名单；伪造 XFF 只影响 IP 维度限流（手机号/邮箱维度不受影响），且引入 Caffeine 上限后伪造键的内存面已封顶。后续可在部署文档中说明反向代理场景的 XFF 处理约定。

### [P3] changeSelfPassword/resetUserPassword 后不失效既有会话与已签发 token

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:1941-1974`
- **维度**: D5
- **证据**:
```java
public void changeSelfPassword(...) {
    ...
    if (!passwordEncoder.passwordMatches(user.getSalt(), oldPassword, user.getPassword())) { throw ...; }
    String salt = passwordEncoder.generateSalt();
    user.setPassword(passwordEncoder.encodePassword(salt, newPassword));
    // 无 autoLogout / 无会话与 token 吊销
}
```
- **现状**: 修改/重置密码后，既有 accessToken/refreshToken 与活动会话全部保持有效直至自然过期；也未触发其他设备的 autoLogout（LoginServiceImpl 已有 `autoLogout` 基建可复用）。
- **风险**: 凭证疑泄露后改密不能立即踢出已持有 token 的攻击者；管理员重置密码同样不吊销旧会话。
- **建议**: 密码变更成功后对目标用户执行 autoLogout（LOGOUT_TYPE_RELOGIN）并作废缓存 userContext。
- **误报排除**: 已确认 NopAuthUserBizModel 内无任何登出调用；token 为 JWT 自包含、服务端无吊销名单（logoutAsync 仅标记 session 行），旧 token 校验依赖 session 存续。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。`ILoginService` 新增 `revokeUserSessionsAsync(userName, exceptSessionId)`（default 抛 UnsupportedOperationException，接口既有先例形态），`AbstractLoginService` 实现为遍历 `getActionSessions` 逐会话 `doLogout(KILL)`（含 onLogout 钩子与缓存上下文失效，旧 token 校验依赖 session 存续故随之失效）；`changeSelfPassword` 吊销除当前会话外的全部会话（本人不被踢出当前设备），`resetUserPassword` 吊销目标用户全部会话。新增 `TestRevokeUserSessions`（admin 重置全吊销 + 本人改密保留当前会话两用例）。红验证：HEAD 无该方法（编译级红，NopAuthUserBizModel 对 HEAD 无法编译）。

### [P3] bindMfa(sms) 发送验证码无速率限制（bindEmail/sendMfaCode 均有）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:337-355`
- **维度**: D5（骚扰/费用滥用）
- **证据**:
```java
private MfaBindResult bindSms(...) {
    ...
    String code = smsCodeStore == null ? null : smsCodeStore.send(SMS_KEY_MFA + user.getUserId());
    sendSmsForBinding(phone, code);     // 无 checkSmsRateLimit / checkProofRateLimit
```
- **现状**: `bindEmail`（同文件 :381 有 `checkEmailRateLimit`）、`sendMfaCode`（LoginServiceImpl:898 有 `checkSmsRateLimit`）、`requireChannelProof`（:1401 有 `checkProofRateLimit`）都限流，唯独 `bindSms` 直接发码，仅受登录态保护。
- **风险**: 已登录用户可对本人手机号无限触发短信（运营商费用滥用）；也可被用于对他人账号绑定流程的干扰面有限（目标为本人手机）。
- **建议**: bindSms 前置 `checkProofRateLimit(phone)`（key 维度 phone，与 proof 路径共享或独立均可）。
- **误报排除**: 已对照三处有限流的同型发送点；确认 bindSms 路径无其他节流。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。`bindSms` 在发码前调用 `checkProofRateLimit(phone)`（phone 维度 send-interval + daily-limit，与登记通道 proof 路径共享同一限流器与配置）。免单独红测试：复用的 checkProofRateLimit 行为由 `TestLoginRateLimitAndAudit` 覆盖，接线为一行前置调用且与 bindEmail/sendMfaCode 同型先例一致（全量 406 tests 回归含 MFA 绑定路径）。

### [P3] SSO 刷新令牌写入 debug 日志

- **文件**: `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/login/OAuthLoginServiceImpl.java:245`
- **维度**: D5（敏感信息日志）
- **证据**:
```java
LOG.debug("nop.auth.sso.refresh-token:refreshToken={}", refreshToken);
```
- **现状**: 长效 refresh token 以明文落 debug 日志。
- **风险**: 生产误开 debug 级日志（或日志采集聚合后降级/误配）时泄露可换发 accessToken 的凭证。
- **建议**: 删除该行或仅记录 token 摘要（前 8 位 + 长度）。
- **误报排除**: 直接代码证据；debug 级别降低了现实触达概率，故 P3。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。改为仅记录 token 长度 + 前 8 字符前缀摘要（定位用途足够，不泄露可换发凭证主体）。免测试：纯日志脱敏，无数值语义。

### [P3] vendored JWK 类使用 bare RuntimeException 且 null keyType 会 NPE

- **文件**: `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/jwk/JWK.java:122-131、150、164、176`；`RSAPublicJWK.java:76-78`
- **维度**: D4、D7
- **证据**:
```java
public PublicKey toPublicKey() {
    String keyType = getKeyType();
    if (keyType.equals(KeyType.RSA)) { ... }        // keyType == null 时 NPE
    ...
    throw new RuntimeException("Unsupported keyType " + keyType);
```
- **现状**: 从 Keycloak 移植的 JWK 解析类违反平台"禁止 bare RuntimeException"约定（未用 NopException + ErrorCode），且 `kty` 缺失时 `keyType.equals(...)` 直接 NPE（JWKS 端点返回畸形数据时）。
- **风险**: IdP JWKS 数据异常时抛出未归一异常（500 而非归一错误码）；NPE 无上下文。调用链为 `JWKPublicKeyLocator.sendRequest`（其 catch Exception 兜底记录 error 日志），故不会崩溃进程。
- **建议**: 包一层 NopException（ERR_AUTH_SSO_ACCESS_FAIL 族）；`StringHelper.isEmpty(keyType)` 显式拒绝。
- **误报排除**: 已确认 JWKPublicKeyLocator.sendRequest catch(Exception) 兜底，进程不崩；因是 vendored 第三方形态代码，评 P3。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。`JWK.toPublicKey` 对 kty 缺失显式抛 `NopException(ERR_AUTH_SSO_ACCESS_FAIL)`（原 NPE）、不支持的 kty 同错误码（原 bare RuntimeException，判空后 `KeyType.RSA.equals(keyType)` 常量前置防 NPE）；`createECPublicKey` 不支持曲线、EC/RSA 密钥构造失败与 `RSAPublicJWK.setX509CertificateChain` 的 thumbprint 失败全部转 NopException 带 cause。新增 `TestJwkErrorNormalization`。红验证：HEAD 版本下 kty 缺失抛 `NullPointerException`、不支持 kty 抛 `java.lang.RuntimeException`——与审计描述逐字一致。

### [P3] MfaFactorVerifier 对 @Nullable jdbcTemplate 无空判（手工装配路径 NPE）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaFactorVerifier.java:240、275`
- **维度**: D1（NPE）
- **证据**:
```java
@Inject
@Nullable
protected IJdbcTemplate jdbcTemplate;
...
long affected = jdbcTemplate.executeUpdate(upd);   // verifyWebauthn / touchLastUsed 无 null 检查
```
- **现状**: `jdbcTemplate` 声明为 @Nullable，但 webauthn 断言验证的 signCount 条件 UPDATE 与 touchLastUsed 直接调用。对照 `LoginServiceImpl.markRecoveryCodeUsed`（:768-785）对 null 有实体写退化路径。
- **风险**: 手工 wiring（注释多次提及的测试/无装饰器直调路径）缺 jdbcTemplate 时，webauthn 第二因子验证成功路径抛 NPE 而非返回结果；生产 beans.xml 按类型注入恒有值。
- **建议**: 补 null 退化（实体写或直接跳过单调写并审计），或去掉 @Nullable 强制装配。
- **误报排除**: 已核对 beans.xml 环境按类型可注入；仅非标准装配可触发，评 P3。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。`verifyWebauthn` 的 signCount 推进与 `touchLastUsed` 对 `jdbcTemplate == null` 退化实体写（读-判-写 + 实体乐观锁兜底，对齐 LoginServiceImpl.markRecoveryCodeUsed 的 null 退化先例）；生产 beans.xml 按类型注入恒有值，行为不变。免单独红测试：退化路径仅在手工装配触发，实体写语义由同模式先例钉定；HEAD 版本该路径直接 NPE（审计原文），修复后走实体写（全量 406 tests 回归绿）。

### [P3] getSiteMap 公开接口在 enable-action-auth 默认关闭时向匿名用户返回完整菜单结构

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/SiteMapApiBizModel.java:36-53`；`nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml:10-12`
- **维度**: D5（信息暴露）
- **证据**:
```java
@BizQuery
@Auth(publicAccess = true)
public SiteMapBean getSiteMap(...) { ... }

<bean id="nopSiteMapProvider" class="io.nop.auth.service.sitemap.SiteMapProviderImpl">
    <property name="enableActionAuth" value="@cfg:nop.auth.enable-action-auth|false" />
```
- **现状**: `filterAllowedMenu` 中 `enableActionAuth=false`（装配缺省值）时完全跳过 `applyAuthFilter`，`removePermissions` 仍执行、function points 默认剔除；匿名用户可获取全部站点资源树（路由、组件、显示名、隐藏项）。
- **风险**: 向未认证者暴露系统功能面（菜单/路由清单是常见侦察输入，hidden 项也一并返回）；不含权限标识（permissions 已移除）。
- **建议**: 匿名请求按空角色集过滤（enableActionAuth=false 时对未登录用户也应用 auth 过滤或仅返回 noAuth 项）。
- **误报排除**: 已核对 `filterAllowedMenu`（SiteMapProviderImpl:222-232）else 分支不做角色过滤；已确认 `removePermissions()` 无条件调用，泄露面限于菜单元数据，评 P3。

> **处置（fix-ai-check 分支，2026-08-23）**: 已修复。`filterAllowedMenu` 的 else 分支（enableActionAuth=false）对匿名请求（userId 空 + 角色空）按空角色集应用 `applyAuthFilter`——非 noAuth 项全部标记 DISABLED，仅公开项保持可用；已登录用户行为不变（内部部署的既有语义保留）。免单独红测试：SiteMapProviderImpl 依赖 daoProvider/siteCache 全量装配，单测环境无法轻量构造；变更逻辑为单分支条件 + 复用既有 applyAuthFilter（其行为已被 enableActionAuth=true 路径长期覆盖），靠全量回归（406 tests 绿，含 introspection/sitemap 相关用例）与代码审读验证。

## 检查过且未发现问题的重点面（负面结论）

- **密码处理**: 全链路经 IPasswordEncoder（BCrypt 实现，nop-auth-core）；恢复码为 `salt:hash` BCrypt 存储 + 条件 UPDATE 一次性置 used（LoginServiceImpl.verifyRecoveryCode/markRecoveryCodeUsed），无明文比较。
- **随机性**: 验证码/challenge/恢复码/webauthn challenge 全部 `MathHelper.secureRandom()` 或 `SecureRandom`/UUID，无 `new Random()`。
- **验证码/challenge store**: Db/Redis 实现均有原子一次性消费（条件 DELETE / Lua CAS）、失败计数上限、TTL；DB 存明文码有设计裁决注释且 TTL 短。
- **登录防枚举**: 未知用户错误码对外统一为 ERR_AUTH_LOGIN_CHECK_FAIL（LoginServiceImpl:383-385）；sendSmsCode 对未注册手机统一 no-op 响应。
- **WebAuthn**: Yubico 库封装规范——服务端 cryptoChallenge、origin/rpId 精确匹配、signCount 单调条件 UPDATE、ceremony 会话绑定、credentialId 全局唯一查重。
- **MFA 敏感表写路径收口**: MfaSensitiveTableBizModel 显式禁用全部通用 mutation（含 updateByQuery/deleteByQuery/recoverDeleted），消除了绕过状态机的 CRUD 旁路。
- **树结构**: SiteCacheDataBuilder.buildEntryChild 有 visited 循环检测并告警；部门为裸 CrudBizModel（树校验依赖框架层）。
- **D7 平台规范**: 全模块无 `@Inject private` 字段、无 Spring `@Value`（均 @InjectValue）、无空 catch、无 printStackTrace；beans.xml 显式装配齐全、collect-beans 扩展点用法正确；错误处理统一 NopException + ErrorCode + .param。
- **流/资源**: 未发现流泄漏（模块基本不直接操作流）。
