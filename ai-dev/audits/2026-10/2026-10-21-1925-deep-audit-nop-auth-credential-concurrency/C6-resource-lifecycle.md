# C6 资源生命周期与泄漏 — nop-auth-credential（WI 5.1）

- 审计日期：2026-10-21
- 审计维度：C6（资源生命周期与泄漏）
- 方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md` C6 维度正文 + 附录 A
- 审计性质：盲审初审（合并批：两批次单次派发），未阅读 `ai-dev/audits/**`、plans/bugs/lessons/backlog，未 git log

## 审计范围与口径

**全源码根覆盖声明**：按基线 `_tmp/cra-baseline-nop-auth-credential.txt` 的 14 个源码根全量覆盖——`nop-auth/nop-auth-{api,app,dao,meta,service,sso,web,codegen}`、`nop-auth/nop-oauth/nop-oauth-{api,service,web,dao}`、`nop-credential/nop-credential-{api,core}`（其中 nop-auth-meta/web/codegen、nop-oauth-web、nop-credential-core 在本仓库无 src/main/java 或为空，实测以存在文件的 9 根为准）。任务 C6 审计要点点名的 `OAuthTokenClient`、`CredentialCipher`、`VaultCredentialKeyProvider` 位于同一凭证族相邻根 `nop-credential/nop-credential-service`、`nop-credential/nop-credential-kms-vault`，按派发指令作为点名审计点一并覆盖；生命周期直接依赖的平台设施（`nop-http-api` 的 `IHttpResponse`/`HttpClientConfig`、`nop-biz-auth-core` 的 `LocalUserContextCache`）审到使用契约边界为止。

**Protected Area 声明**：本审计为只读盲审，未修改任何代码；涉及 `nop-auth`（权限/认证模型，ask-first）与凭证加密链（`nop-credential`）仅做检查，不触碰生成文件与 `_` 前缀产物。

**并发面极稀疏预校准声明（R1）**：基线 C6a（资源获取点）在 14 根仅 2 条非 javadoc 命中；C4a（线程/池）14 根全部为空；补充横扫（`FileInputStream|Files.new*Stream|getInputStream|close|addListener|removeListener|registerListener`）零命中。**范围内不存在任何 OS 级资源获取点**（无线程/池、无流/文件、无 LocalFileLock、无 listener 注册）。资源形态全部为四类：DB 行（6 族状态表）、Redis 键（putExAsync TTL 兜底）、进程内有界缓存（Caffeine/LocalCache）、HTTP 响应（全缓冲契约）。本报告据实收窄：C6 发现集中在「DB 行账本的过期回收配对」与「锁内外呼的时长上界」两族，未凑数扩面。

**降档口径声明**：按派发指令，泄漏与残留形态一律不适用启动期单线程降档。本报告全部发现的增长/残留路径均为运行期多线程请求路径（HTTP worker → biz/dao）产物，按运行期口径判级。

---

## 发现

### [C6-001] NopAuthSession 会话表全路径零回收——登录只插入、登出/过期只标记，无任何删除路径

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoUserContextCache.java:162-166`（主证）；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoLoginSessionStore.java:53-84`
- **证据片段**:
  ```java
  // DaoUserContextCache.java
  @Override
  public CompletionStage<Void> removeUserContextAsync(SessionInfo sessionInfo) {
      // 不需要删除
      return FutureHelper.success(null);
  }
  // checkExpired (L100-103)：过期亦只标记
  if (now.isAfter(timeoutTime)) {
      session.setLogoutType(AuthApiConstants.LOGOUT_TYPE_EXPIRE);
      session.setLogoutTime(CoreMetrics.currentTimestamp());
      return true;
  }
  ```
- **严重程度**: P1
- **并发场景**: HTTP worker 线程（login/logout/任意鉴权请求）并发写 `NopAuthSession` 表；无后台清理线程参与
- **触发时序**: 每次登录 `saveSession`（DaoLoginSessionStore.java:70 `saveEntity`）INSERT 一行 → 会话过期时 `checkExpired` 仅置 `logoutType=EXPIRE`，登出时 `logoutSession`（DaoLoginSessionStore.java:75-84）仅置 `logoutType/logoutTime/logoutBy` → **全仓（14 根 + 调用面全 grep）不存在任何对 `NopAuthSession` 的 DELETE 语句、`deleteEntity` 调用或定时清理** → 行永久驻留。长期运行（周/月级）下表行数随登录次数单调增长，无收敛点。
- **现状**: `removeUserContextAsync` 的「不需要删除」注释是内存缓存接口契约（`LocalUserContextCache` 依赖 LocalCache TTL 淘汰）在 Dao 实现中的直译；但 Dao 实现的被"释放"资源是 DB 行，而 DB 行在代码、配置（`NopAuthConfigs` 无任何 session 清理配置项）、仓库内脚本（`*.xlib/*.sql` grep 零命中）中均无回收路径。
- **风险**: 校准口径「长时运行必然触发的无界增长」直题。且行内持久化 `accessToken`/`refreshToken`/`cacheData`（DaoUserContextCache.java:139-141）——过期/登出会话的令牌载荷无限期留在库中，扩大敏感数据暴露面与备份面；表膨胀还拖慢 `findFirstByExample`/`findAllByExample`（getSessionInfoForUser/getActionSessions，logoutType 过滤无索引保障时全表扫描面增大）。
- **建议**: 为 `NopAuthSession` 增加过期行批量清理：复用同仓库现成先例 `NopCredentialOauthStateStore.cleanupExpired()`（create 前全表条件 DELETE）的语句形态，或接入平台 job 模块做低频定时清理（`logoutTime < now-retention` 的行删除）；清理保留期做成 `NopAuthConfigs` 配置项。
- **失效面枚举**（同族"创建/过期/清理"配对全景，F3 清扫）:
  | 存储 | 创建 | 过期清理 | 判定 |
  |---|---|---|---|
  | `NopAuthSession`（DaoLoginSessionStore/DaoUserContextCache） | INSERT | **无** | 本发现（P1） |
  | `NopAuthMfaChallenge`（DbMfaChallengeStore） | INSERT | 同键惰性 DELETE | C6-002 |
  | `NopAuthSmsCode`/`NopAuthEmailCode`（Db{Sms,Email}CodeStore） | INSERT | 同键惰性 DELETE | C6-002 |
  | `NopAuthRateLimitCounter`（DbSendCodeRateLimiter） | INSERT | 同键惰性 DELETE | C6-002 |
  | `NopAuthLoginAttempt`（DbLoginAttemptStore） | INSERT | 同键惰性 DELETE | C6-002 |
  | `NopAuthMfaTrustedDevice`（MfaTrustedDeviceManager） | INSERT | **无自动**（仅人工 removeBySid） | C6-003 |
  | `NopCredentialOauthState`（NopCredentialOauthStateStore） | INSERT | create 前全表清扫 | 对照组（唯一有批量形态） |
  | Redis 各 store | putExAsync | Redis TTL 兜底 | 配对成立 |
- **信心水平**: 确定（代码内无任何清理路径，仓库内无清理任务/脚本/配置支撑；不排除部署侧 DB 运维手段，但该前提未在代码或配置中声明）
- **误报排除**: (1) 非 audit log 类「有目的的追加保留」——session 行无审计用途声明，且含令牌载荷；(2) 「不需要删除」注释不构成已声明边界——它声明的位置（内存缓存契约）与实际资源（DB 行）错位，且系统级无任何清理实现；(3) 非 ORM dirty-flush 问题——`logoutSession`/`checkExpired` 改实体不显式 update 与 `saveUserContextAsync` 同为平台 ORM 会话惯例，有测试覆盖，不在此报。
- **复核状态**: 已保留（P1；复核员回码核实 :162-166 "不需要删除"注释、:100-103 checkExpired 仅标记、全仓 xlib/sql/job 零清理任务；**一处表述勘误（不降级）**：`NopAuthSessionBizModel` 继承 CrudBizModel 经 `_service.beans.xml:141-145` 注册 biz 代理——通用 CRUD delete/deleteByQuery 对 session 行权限门控可达，初审"不存在任何 DELETE 语句/调用"的绝对化表述不成立；但该路径为人工管理面而非自动回收，"无自动/周期清理 + 令牌载荷无限期驻留"的风险主体维持 P1）

### [C6-002] 五族 DB 状态存储只做「同键访问时惰性删除」，无批量清理——键不再被访问的过期行永久驻留（含用户可控键空间放大面）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/store/DbMfaChallengeStore.java:29-37`；`mfa/store/DbSmsCodeStore.java:115,151-158`；`mfa/store/DbEmailCodeStore.java:108,144-151`；`ratelimit/DbSendCodeRateLimiter.java:52,176-179`；`login/DbLoginAttemptStore.java:30-35,127-130`
- **证据片段**:
  ```java
  // DbMfaChallengeStore.java javadoc（L36-37，五族共同形态）
  // 原子性：单行语句天然原子；consume 用条件 DELETE + affected-row 判定实现一次性
  // （并发 consume 仅一个成功）。TTL 清理为惰性：peek/consume 时过期即删；批量清理为 Follow-up。
  // 唯一的删除触发点（L95-97）——仅当该 key 再次被访问：
  if (e.getExpireAt() != null && e.getExpireAt() <= now) {
      deleteByToken(challengeToken); // 惰性 TTL 清理
      return null;
  }
  ```
- **严重程度**: P2
- **并发场景**: HTTP worker 线程（发码/验证/登录/限流判定）并发 INSERT 与同键条件 DELETE；无清理线程
- **触发时序**: 用户发起 MFA 挑战/短信验证码/邮件验证码/登录失败计数/限流计数 → INSERT 一行（expireAt=now+TTL）→ 用户放弃流程、或该 (key, IP, username) 组合此后不再出现 → 该键的惰性删除永不触发 → 行永久驻留。以 `DbSendCodeRateLimiter` 为例：`checkIp`（L109-114）对每个新 clientIp 的**首次请求先 INSERT 行再判定限额**——攻击者以 IP 轮换打 send-code 接口即可按请求速率制造新键行（键空间用户可控，跨入 C5 放大面）。
- **现状**: 五族同构，清理配对只覆盖「同键再次访问」一条路径；javadoc 声明状态不一——`DbLoginAttemptStore` L35「plan 2275 审查 M5 已裁定为显式选择」、`DbMfaChallengeStore` L37「批量清理为 Follow-up」为**部分声明边界**；`DbSmsCodeStore`/`DbEmailCodeStore`/`DbSendCodeRateLimiter` 仅声明惰性形态、未声明无批量清理的理由。
- **风险**: 五族行数随「永不复访的键」累积无上界；RateLimitCounter/LoginAttempt 的键（IP/username）攻击者可低成本扩充。同仓库相邻凭证模块 `NopCredentialOauthStateStore`（L66-67 create 前调 `cleanupExpired()` 全表条件 DELETE，L112-117）已给出批量清扫先例，五族未对齐。
- **建议**: 复用 `NopCredentialOauthStateStore.cleanupExpired()` 语句形态，在各族 create 路径顺带全表过期清扫（`delete where expireAt <= now`，或接入 job 低频执行）；五族一处实现、统一接入。
- **失效面枚举**: `DbMfaChallengeStore`（peek/consume 惰性）、`DbSmsCodeStore.deleteByKey`、`DbEmailCodeStore.deleteByKey`、`DbSendCodeRateLimiter.deleteExpired`、`DbLoginAttemptStore.deleteExpired`——逐一核对均为同键限定（`where key = ?`），无一族有跨键批量清扫；Local/Redis 变体（Caffeine expireAfterWrite / Redis TTL）配对成立，不在缺陷面内。
- **信心水平**: 确定
- **误报排除**: 不是「有界声明存在的缓存」误报类——这是 DB 行不是缓存项，无 maximumSize 等价物；两族成员的 javadoc 声明已作为降档依据（P1→P2）计入判级，非无视声明。
- **复核状态**: 已保留（P2；复核员回码核实五族删除点全部键/token 限定——`DbMfaChallengeStore:145/170` challengeToken 条件、`DbEmailCodeStore:144/148` codeKey 条件、`DbSmsCodeStore` 同构、`DbSendCodeRateLimiter.deleteExpired(key, now)`、`DbLoginAttemptStore` 同键条件；均无跨键批量清扫；两处 javadoc 部分声明（plan 2275 / Follow-up）在场，降档口径成立）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaTrustedDeviceManager.java:198-202,214-238,252-265`
- **证据片段**:
  ```java
  /** 固定窗口终点：now + ttl-days（命中不续期——§6.4 拒绝滚动续期）。 */
  private static Timestamp fixedWindowEnd() { ... }
  /** 该用户全部行（含过期——管理端展示 + 自助清理）。 */
  public List<NopAuthMfaTrustedDevice> listForUser(String userId) { ... }
  /** 未过期行计数（max-count 口径——仅计未过期行）。 */
  public long countUnexpired(String userId) { ... filter(r -> r.getExpireAt() != null && r.getExpireAt().after(now)) ... }
  ```
- **严重程度**: P2
- **并发场景**: HTTP worker 线程（登录豁免判定 register / 自助移除）并发读写；无清理线程
- **触发时序**: 用户以新设备指纹登录 → `register`（L137-180）：同 hash 行复活刷新，新 hash 在 `countUnexpired < max-count` 时 INSERT → 设备淘汰/指纹变化后旧行过期 → 过期行不占额度（countUnexpired 只计未过期）、不阻新行、**无任何自动删除路径**（`removeBySid`/`removeBySidForAdmin` 均为人工触发）→ 每用户行数随历史设备数逐年单调增长。
- **现状**: `listForUser` javadoc「含过期——管理端展示 + 自助清理」是对「无自动清理」的注释性确认，属部分声明边界；max-count 门控的口径设计（只计未过期）客观上使过期行进入"免管理"区。
- **风险**: 增长速率低（每用户新设备频度）但确为无界；过期行保留 `deviceHash` 指纹数据，延长设备指纹的留存面。
- **建议**: 登记路径或管理端 list 路径顺带清扫该用户过期行（`delete where userId=? and expireAt < now`），与 C6-002 的批量清扫统一立项。
- **失效面枚举**: 本类三个删除点（removeBySid/removeBySidForAdmin/removeAllForUser）全部人工触发；无定时/登录时点自动清理；同族 DB store 五兄弟见 C6-002 表。
- **信心水平**: 确定
- **误报排除**: 非缓存误报类——ORM 表行无淘汰设施；「拒绝静默 LRU 淘汰」的声明（L131）只约束 max-count 门控语义，未声明过期行留存策略。
- **复核状态**: 已保留（P2；复核员回码核实 `MfaTrustedDeviceManager` 三个删除点全部人工触发、无定时/登录时点清理；C6-round2 深挖补强：唯一回收推派 = 人工逐行 removeBySid（`UserMfaSelfService:1064` javadoc 双证），连同键惰性删除都没有——六族中清理配对最弱）

### [C6-004] OAuthTokenClient 与 JWKPublicKeyLocator 的外呼无调用点请求超时——锁内外呼时长上界依赖部署级 client 默认配置

- **文件**: `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/OAuthTokenClient.java:82-96`；`nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/jwk/JWKPublicKeyLocator.java:73-84,106-113`
- **证据片段**:
  ```java
  // OAuthTokenClient.fetchToken（L83-91）：HttpRequest 未设 timeout
  HttpRequest request = new HttpRequest();
  request.setMethod(HttpApiConstants.METHOD_POST);
  request.dataType(HttpApiConstants.DATA_TYPE_FORM);
  request.body(form);
  request.url(tokenEndpoint);
  IHttpResponse response;
  try {
      response = httpClient.fetch(request, null);   // 锁内（调用链 CredentialProviderImpl 行锁）执行
  // 对照组：VaultCredentialKeyProvider.fetchMaterial（L313-315，D3-05 加固）
  //   request.header(HEADER_VAULT_TOKEN, token);
  //   request.setTimeout(effectiveRequestTimeoutMs());  // 显式 10s，"不再无限阻塞"
  ```
- **严重程度**: P2
- **并发场景**: (a) 取用 oauth2 凭证的请求线程在 `CredentialProviderImpl.engineUpdateInLock` 的 DB 事务 + SELECT FOR UPDATE 行锁内执行 token 刷新外呼（javadoc L306 自述「持锁期间含一次秒级 HTTP 外呼」）；(b) 多个校验 token 的请求线程在 `JWKPublicKeyLocator.getPublicKey` 的 `synchronized(this)` 内等待 JWKS 拉取
- **触发时序**: token endpoint / IdP jwksUrl 响应迟缓（如网络半开）→ `fetchToken`/`sendRequest` 的 HttpRequest 未调 `setTimeout` → 超时保障落到 client 实现对 `HttpClientConfig.readTimeout`（平台默认 30s，`HttpClientConfig.java:35-37`；JDK impl L218-221、Apache impl 经 `defaultRequestConfig` L96-98 均以 config 兜底）→ **默认部署下有界（约 30s），但该上界是部署配置（`nop.http.read-timeout` 可调大/置空），非调用点强制** → 行锁/类锁内 I/O 实际时长无代码级保证；期间同凭证的所有取用者（行锁）或同 realm 的所有 token 校验（类锁）串行阻塞。
- **现状**: 同族三点位超时处理不一致：`VaultCredentialKeyProvider.fetchMaterial` 已按 D3-05 显式设 10s（对照组，加固后先例）；`OAuthTokenClient.fetchToken`（authorization_code/refresh 两入口共用）与 `JWKPublicKeyLocator.sendRequest` 未设。`engineUpdateInLock` javadoc 的「秒级」假设未被调用点强制，30s 兜底成立时「已接受吞吐代价」声明大体成立，但保障链条是跨模块配置而非本调用点。
- **风险**: 部署侧调大/置空 read-timeout 后，锁内 I/O 时长失去上界：oauth2 凭证行锁被单个迟缓外呼长期占用（事务与连接同步被持有），JWKS 拉取阻塞全部 token 校验（叠加 CL-013）。
- **建议**: 按先例 D3-05 形态补齐：`OAuthTokenClient` 的 HttpRequest 设请求级超时（配置项 + 缺省 10s）；`JWKPublicKeyLocator.sendRequest` 的 HttpRequest 同样设显式 timeout。
- **失效面枚举**: `OAuthTokenClient.fetchToken`（exchangeAuthorizationCode/refresh 两入口共用，均未设）；`JWKPublicKeyLocator.sendRequest`（未设）；`VaultCredentialKeyProvider.fetchMaterial`（已设，对照组）；`OAuthLoginServiceImpl` 的三处 `httpClient.fetchAsync`（login/logout/refresh，未设，但其调用不在 DB 行锁内，影响面为请求线程自身）。
- **信心水平**: 确定（未设调用点超时为代码事实）；「默认 30s 兜底存在」为实测 `HttpClientConfig` 默认值 + 两个 client 实现的兜底分支
- **误报排除**: 不是「锁内做 I/O 一律报缺陷」——平台 javadoc 已显式声明接受该代价，本发现报的是「声明假设（秒级）与实现保障（无调用点超时，依赖部署配置兜底）之间的缺口」，且同族已有一处按同因加固的先例（D3-05），属可对照的不一致而非风格偏好。
- **复核状态**: 已保留（P2；复核员回码核实 `OAuthTokenClient.fetchToken :83-91` HttpRequest 未设 setTimeout、`JWKPublicKeyLocator.sendRequest :112` 同样未设、`VaultCredentialKeyProvider` D3-05 显式 10s 对照先例与 `HttpClientConfig :35/:37` 缺省 30s/10s 兜底全链在场）

### 已验证非问题（C6 审计要点点名项的误报排除记录）

任务 C6 审计要点点名了 4 个类的生命周期，其中以下核对结论为「配对成立/无泄漏」，显式记录以防复核重复排查：

1. **HTTP 响应资源（OAuthTokenClient/VaultCredentialKeyProvider/JWKPublicKeyLocator/OAuthLoginServiceImpl 的 fetch 使用）**：`IHttpResponse` 接口（`nop-http-api`）无 close/AutoCloseable，契约为全缓冲——`DefaultHttpResponse` 仅持 `byte[]/String`；三个出厂实现均先缓冲完再返回（JDK `BodyHandlers.ofByteArray()`、OkHttp `body().string()`、Apache SimpleHttpResponse）。响应体不持流，连接释放由 client 实现内部完成，调用侧不关闭不构成泄漏。
2. **CredentialCipher 加密流**：纯 `String` 编解码（`encrypt`/`decrypt` 全程无流/无临时资源）；`ITextCipher` 实例由 `ICredentialKeyProvider` 单例持有，生命周期即 bean 生命周期，无释放点需求。
3. **VaultCredentialKeyProvider 密钥材料生命周期**：启动期 `@PostConstruct` 一次性读取（请求超时已加固），运行期 `getKey` 纯内存查找、`keyMap` 不可变（`Collections.unmodifiableMap`）；材料驻留进程内存为 javadoc 显式设计声明（「运行期零托管端调用」「材料仅存进程内存」），已声明边界，非泄漏。
4. **JWK TTL 刷新的资源释放**：`JWKPublicKeyLocator` 刷新成功以 volatile 字段整表替换 `currentKeys`（L122），旧 map 整体可回收，无逐键残留；刷新失败时 TTL 过后 `lookupCachedKey` 返回 null → 上游 `JwtHelper.parseToken` fail-closed，无旧公钥 fail-open。行为缺陷（吞异常/锁内 I/O）移交 CL-012/CL-013。
5. **会话内存缓存**：`LocalUserContextCache.init` 三缓存均 `LocalCache` 有界（maxLoginUserCount 派生上限）+ TTL 淘汰；`LocalSendCodeRateLimiter` Caffeine `maximumSize + expireAfterWrite` 有界；`SiteMapProviderImpl` 的 `GlobalCacheRegistry.register/unregister` 以 `@PostConstruct`/`@PreDestroy` 正确配对（L73-75/89-92）。
6. **MFA 状态生命周期（内存侧）**：pending setting 按 (user, type) upsert 覆盖（有界）；恢复码 `regenerateRecoveryCodes` 先 `deleteRecoveryCodes` 后生成、配对完整（UserMfaSelfService.java:1541-1567）；WebAuthn challenge 经 TTL 型 challenge store 传递，无内存暂存 map；Redis 变体各 store 均 `putExAsync` TTL 兜底。

---

## 维度集外线索（overlay C3/C4/C5/C7/C8/C9）

> 编号续接 `nop-auth-credential-CL-012`（CL-001…011 已占用）。以下为 C6 审计过程中发现的非 C6 维度线索，仅登记事实与定位，不做该维度的完整裁定。

### [CL-012] JWK 刷新失败被吞且 lastRequestTime 无条件推进——刷新失败后强制静默窗口，无告警面（→C7，对照 F9）

- **文件**: `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/jwk/JWKPublicKeyLocator.java:73-84,106-126`
- **事实**: `sendRequest()` 内 `catch (Exception e) { log.error(...); }` 吞掉 JWKS 拉取失败；外层 `getPublicKey` 在 `sendRequest()` 返回后无条件执行 `lastRequestTime = currentTime` → 拉取失败也占满 `minTimeBetweenJwksRequests`（缺省 60s，SsoConfig.java:29）的拒绝重试窗口；窗口内所有 kid-miss 的 token 校验返回 null key 而 fail，且仅 debug 级日志可见「Won't send request」。失败既无指标/告警面，也无指数退避外的显式失败状态。
- **建议复核方向**: C7 自愈语义（失败后是否可观测、可重试）+ F9 吞异常族；顺带核对 `config.getMinTimeBetweenJwksRequests()` 部署值。

### [CL-013] JWKPublicKeyLocator 持 this 锁做 HTTP 外呼 + lastRequestTime 非 volatile 锁外读（→C2/C1/C9）

- **文件**: `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/jwk/JWKPublicKeyLocator.java:43-49,61-84`
- **事实**: `getPublicKey` 在 `synchronized(this)` 块内执行 `sendRequest()`（HTTP I/O，时长上界见 C6-004）；期间该 realm 的全部 token 校验线程串行阻塞。`lastRequestTime`（L45）为普通 long 字段：首次 `lookupCachedKey`（L67，锁外）读它、`getPublicKey` 锁外早退判定都无 happens-before 保障（可见性弱，最坏多触发一次刷新，危害有限）；`currentKeys` 为 volatile，整表替换发布安全。
- **建议复核方向**: C2 锁扩展（锁内 I/O）+ C1 可见性；注意 CL-012 的窗口语义使锁内 I/O 在失败场景下频度受控，判级时合并考量。

### [CL-014] OAuthLoginServiceImpl.doLogout 的 IdP 远程登出 fire-and-forget——thenCompose 链结果被丢弃、waitAll 未覆盖、异常无处理（→C3，对照 F9/F5）

- **文件**: `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/login/OAuthLoginServiceImpl.java:198-213`
- **证据片段**:
  ```java
  CompletionStage<IUserContext> promise = doGetUserContext(sessionInfo.getSessionId());
  promise.thenCompose(userContext -> {
      if (userContext == null) return FutureHelper.success(null);
      HttpRequest req = newLogoutRequest(userContext);
      return httpClient.fetchAsync(req, null);      // 该链的 CompletionStage 被丢弃
  });
  return FutureHelper.waitAll(Arrays.asList(future, promise));  // 只等 promise，不等登出外呼
  ```
- **事实**: `thenCompose` 派生的登出外呼 future 未被任何变量持有；`waitAll` 等待的是 `promise`（取 userContext）而非派生链 → 方法返回时 IdP 侧登出 POST 可能尚未发出/完成；外呼异常无 `exceptionally/handle`，静默丢失。SSO 登出的远端吊销语义为 best-effort 且失败不可见。
- **建议复核方向**: C3 异常传播与等待语义；与业务侧「登出是否必须成功吊销 IdP 会话」的契约对照后判级。

### [CL-015] CredentialProviderImpl.registerUsage 检查-然后-插入无互斥——并发重复 usage 行（→C1，理论风险）

- **文件**: `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java:212-228`
- **事实**: `findAllByQuery` 判空后 `saveEntityDirectly` 插入，无行锁/唯一约束兜底的证据（同凭证+consumerRef 并发首次登记可产生重复行）；`unregisterUsage` 按条件全删可自愈，`countUsage` 在重复期间返回偏大值。未查 `NopCredentialUsage` 的 ORM 模型是否有唯一键（`(credentialId, consumerRef)`），若有则自动闭环。
- **建议复核方向**: C1「检查-然后-行动」族；核对 `_NopCredentialUsage` 实体索引与orm 模型唯一约束后再定是否立案。

---

## CL 线索复核状态（复核员回码）

| CL | 结论 | 复核证据 |
|---|---|---|
| CL-012（JWK 刷新失败被吞 + lastRequestTime 无条件推进） | **确认，与 C2-002 同站点归一** | `JWKPublicKeyLocator.java:123-125` catch 吞 + `:77` 无条件推进在场；两面一体同 diff 修复 |
| CL-013（持 this 锁 HTTP + lastRequestTime 非 volatile 锁外读） | **确认，与 C2-001/C1 面归一** | `:43-45` volatile 双字段核实——`lastRequestTime` 实为 volatile（:45），CL-013"非 volatile 锁外读"表述**证伪勘误**：字段已 volatile，锁外读可见性成立；持锁 I/O 面由 C2-001 承载。本条降为 watch（并入 C2-001） |
| CL-014（doLogout fire-and-forget） | **确认（= CL-004 同站点，跨文件双登记合并）** | 与 C1 文件 CL-004 同一缺陷两面，归一单案 |
| CL-015（registerUsage 检查-然后-插入） | **重复关闭（并入 CL-003），遗留问题已裁决** | `nop-credential.orm.xml:118` `UK_NOP_CREDENTIAL_USAGE_CRED_CONSUMER (credentialId, consumerRef)` 实证存在——无双行面（CL-015 的"若有则自动闭环"条件成立一半：数据一致性闭环、输家裸异常面即 CL-003 已登记的同一站点，无新增立案面） |

## 维度复核结论

- 复核范围：C6-001…004（初审）+ C6-005（深挖，见 C6-round2.md）+ CL-012…015 + 六族清理配对表复核；复核方式 = 逐条回码（删除点限定符/TTL 附着点/超时设置/锁链路逐一比对 live code）。
- 三态判定：**保留 5 / 降级 0 / 驳回 0（1 表述勘误不降级）**。初审 4 条全部维持判级（C6-001 P1 / C6-002~004 P2）；深挖 C6-005（P2）经独立回码成立（回写 :353-360 与事务边界核实）。C6-001 的"不存在任何 DELETE"绝对化表述经 `_service.beans.xml:141-145` biz 代理注册证伪，勘误后风险主体（无自动清理）不变，维持 P1。
- 降档口径核对：C6-002 P2 计入两族 javadoc 部分声明的降档、C6-003 P2 计入 listForUser 注释性声明——口径使用一致，无以声明掩盖缺陷形态；C6-005 未按 P1"半个成功"直报、按触发窗口精确命中降 P2 并如实标注依赖 IdP 轮换契约——核对成立。
- 现状标注：全部为**新发现（未修复）**；`NopCredentialOauthStateStore.cleanupExpired()` 为同仓正面对照组（非缺陷）；plan 2275 / D3-05 / D2-03 系既有加固先例与本轮发现无重叠。
- 跨单元对照（cra-index，157 条口径）：C6 五条零同站点重复。家族关联 3 处单列：(a) C6-001/C6-002/C6-003 与 WI 1.6 `C7-001`、WI 1.7 `C7-003`（账本/状态表零保留族，F4）为跨单元同族第 4/5/6 例——Phase 8 批次对齐、零重复立项；(b) C6-004 与 WI 2.3 `C4-007`（IdP 外呼无调用点超时）同族不同站点（修复批协同）；(c) C6-005 的"外部资源已前移 × 本地回滚"与 WI 1.6 `C3-006`（簿记写覆盖业务结果）同族（半个成功），判级按各自后果独立。
- 跨维度重复：C6 文件内 CL-012↔C2-002、CL-014↔CL-004（跨文件）双登记，均按同站点归一（见上表）；CL-013 一处表述证伪勘误。

## 最终保留项（C6 维度，复核后权威清单）

| 编号 | 标题 | 判级 | 复核状态 |
|---|---|---|---|
| C6-001 | NopAuthSession 零自动回收 + 令牌载荷无限期驻留（表述勘误后维持） | P1 | 已保留 |
| C6-002 | 五族 DB 状态存储仅同键惰性删除、无批量清扫 | P2 | 已保留 |
| C6-003 | NopAuthMfaTrustedDevice 过期行无自动回收（六族清理最弱） | P2 | 已保留 |
| C6-004 | OAuthTokenClient/JWKPublicKeyLocator 外呼无调用点超时 | P2 | 已保留 |
| C6-005 | OAuth 刷新半个成功窗口丢失轮换 refresh token | P2 | 已保留 |
| CL-012…015 | CL-012/013/014 归一并入主案；CL-015 重复关闭并入 CL-003 | — | 全部落位 |

统计：**5 保留条目（P1×1 / P2×4）**；P0×0；4 条 CL 全部落位（归一 3 + 重复关闭 1）。
