# C2 锁与同步原语 — nop-auth-credential（WI 5.1）

- 审计维度：C2 锁与同步原语（方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md` L26–118 + L146–170 + 附录 A）
- 审计日期：2026-10-21（盲审初审，合并批两批次单次派发）
- 审计者：C2 维度盲审初审员（未读 `ai-dev/audits/**` 历史、plans/bugs/lessons/backlog，未 git log）

## 审计范围与口径

**源码根覆盖声明**：按基线 `_tmp/cra-baseline-nop-auth-credential.txt` 的 14 个 ROOT 全量覆盖：

- `nop-auth/nop-auth-{api,app,dao,meta,service,sso,web,codegen}/src/main/java`
- `nop-auth/nop-oauth/nop-oauth-{api,service,web,dao}/src/main/java`
- `nop-credential/nop-credential-{api,core}/src/main/java`

实测口径修正（如实记录，不凑数）：14 根中 `nop-auth-meta`、`nop-auth-web`、`nop-auth-codegen`、`nop-oauth-web` 4 根无 `src/main/java`（resources-only，零 Java）；`nop-credential-core` 根在本工作区不存在（credential 家族实际 Java 在 `nop-credential-api/service/kms-vault/dao`，已一并扫描）。实际扫描 Java 文件 324 个（find 计数；相对派发口径 325f/35,514 行的测法差异不影响覆盖结论：锁原语全扫描以 grep 全树执行，credential 家族全部 Java 根锁原语命中为零）。

**稀疏性预校准声明（R1）**：对全部 Java 源执行锁原语全扫描（`synchronized` / `ReentrantLock` / `ReadWriteLock` / `Collections.synchronizedXxx` / `Semaphore` / `Atomic*` / `LongAdder` / `volatile` / `wait/notify` / `CountDownLatch` / `Thread.sleep` / `tryLock` / `computeIfAbsent` / `compute`），自管锁原语仅命中：

1. `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/jwk/JWKPublicKeyLocator.java`（volatile ×2 :43/:45 + synchronized(this) ×2 :73/:89）
2. `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/ratelimit/LocalSendCodeRateLimiter.java`（synchronized(targetTrackers) :82 + Caffeine `compute` ×2 :83/:109）
3. `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/sitemap/SiteCacheDataBuilder.java`（`computeIfAbsent` ×10——经核为 build-local 实例字段，见核清清单）

其余文件零自管锁原语。遵守"不得凑数扩面"，发现数与稀疏度相称。

**Protected Area 声明**：本审计为只读调查，未变更任何代码/模型/生成文件；`nop-auth` 认证模型属 ask-first 区，本报告仅登记发现与修复方向，不含实施。

**降档口径声明**：认证服务（login/mfaVerify/sso token 校验）为生产请求触达路径，本维度全部发现的并发面均生产可达，**启动期降档不适用**。`ormTemplate == null` 退化分支经核为手工装配/测试专用（生产 `auth-service.beans.xml:161` 装配 `nopMfaFactorVerifier` 且容器提供 `IOrmTemplate`），不计生产发现，仅在核清清单注明。

**锁序图（文本）**——全模块仅 2 条锁边，均为叶子方向，无环，无可达死锁：

```
[JWKPublicKeyLocator] monitor(this) ──→ HTTP I/O（IHttpClient.fetch，无嵌套锁获取）     [叶子]
[LocalSendCodeRateLimiter] monitor(targetTrackers) ──→ Caffeine asMap().compute 内部 per-key 锁 [叶子]
[ipTrackers] compute（不持外层 monitor，仅 Caffeine 内部锁）                        [叶子]
DB 写路径：无 JVM 锁——依赖单语句条件 UPDATE / 唯一约束 / ORM version 条件写（checkUpdateResult 兜底）
无 synchronized 集合迭代点逃逸锁外；无 wait/notify 无超时等待点；无 CountDownLatch/Thread.sleep。
```

## 发现

### [C2-001] JWKS 公钥刷新在 synchronized(this) 内做同步 HTTP——全模块唯一热路径锁内 RPC

- **文件**: `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/jwk/JWKPublicKeyLocator.java:73-83`（`:106-126` sendRequest）
- **证据片段**:
  ```java
  // Check if we are allowed to send request
  synchronized (this) {
      currentTime = CoreMetrics.currentTimeMillis();
      if (currentTime > lastRequestTime + minTimeBetweenRequests * 1000L) {
          sendRequest();                      // 内部 httpClient.fetch(...) 同步 HTTP
          lastRequestTime = currentTime;
      } else { ... }
      return lookupCachedKey(publicKeyCacheTtl, currentTime, kid);
  }
  ```
- **严重程度**: P2
- **并发场景**: HTTP worker 线程池 × N（每次 SSO access-token 校验经 `OAuthLoginServiceImpl.parseAuthToken` → `JwtHelper.parseToken` → `getPublicKey(kid)`，`OAuthLoginServiceImpl.java:243`）。当 TTL（publicKeyCacheTtl 缺省 3600s，`SsoConfig.java:30`）过期或 kid 未命中时，全部并发 miss 线程在同一 monitor 上串行化。
- **触发时序**: T1 到达 :73 获锁 → :76 sendRequest 发起对 IdP JWKS 端点的同步 fetch（阻塞中，上界受 `HttpClientConfig` 缺省 readTimeout=30s / connectTimeout=10s，`nop-http-api/.../HttpClientConfig.java:35,37`）→ T2..Tn 依次到达 :67 fast-path 未命中 → :73 阻塞于 monitor → T1 最长 30s 后释放 → T2..Tn 逐个进入、命中 60s 节流（minTimeBetweenJwksRequests 缺省 60，`SsoConfig.java:29`）后经 :82 重查返回。交叠后果：**IdP 慢/抖动期间，每个 60s 节流窗口内首次刷新可令全部并发 token 校验请求停顿至多 ~30s**；请求堆积放大为验签路径整体长尾。
- **现状**: 锁内含网络 I/O 的热路径争用热点；节流把 I/O 频率压到 ≤1 次/60s（设计意图），但未解决"刷新进行中全员互斥等待"。
- **风险**: 不修复则在 IdP 降级/网络抖动时验签路径周期性 30s 级停顿（fail-closed 方向，非数据损坏）；若部署侧调大/清零 HttpClientConfig 超时则等待无界（F5 口径：依赖平台全局缺省，本调用点无显式 per-request 超时，`HttpRequest` 未设 timeout）。
- **建议**: 将 HTTP fetch 移出临界区：`synchronized` 内只做节流判定与"刷新中"标记，fetch 放锁外执行，完成后 volatile 替换 `currentKeys`（现 :122 已是整表替换语义，迁移成本低）；等待方可返回"重试/暂时不可用"而非持锁阻塞。等待方如需兜底，可用平台 `io.nop.commons.concurrent.lock.IResourceLockManager` 按 realm 维度细粒度化。同时给 `HttpRequest` 显式设 timeout，不依赖全局缺省。
- **失效面枚举（F3）**: 本模块 synchronized 复合操作全清单 = JWKPublicKeyLocator :73（本条）、:89 `reset()`（管理端触发，同锁同 I/O——低频管理路径，按校准规则"低频路径粗锁合理"不另立案）、LocalSendCodeRateLimiter :82（临界区内无 I/O，核清）。全树 grep 确认无其他"持锁网络/磁盘 I/O"点。
- **信心水平**: 确定（时序可具体化，热路径触达实证 :243）
- **误报排除**: 非"原语使用本身"误报——问题在复合操作把 RPC 包进互斥区；非低频路径——每次 SSO token 校验触达 fast-path、TTL 过期后必经锁路径。
- **复核状态**: 已保留（P2；复核员回码逐行核实 `JWKPublicKeyLocator.java:73-83` synchronized(this) 内 :76 sendRequest 同步 fetch（:112 HttpRequest 无 setTimeout）、:67 fast-path 未命中路径、SsoConfig :29-30 缺省 60s/3600s、HttpClientConfig :35/:37 缺省 30s/10s 全部在场）

### [C2-002] JWKS 刷新失败路径仍推进 lastRequestTime：节流预算被失败消费 + TTL 锚点被失败重置

- **文件**: `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/jwk/JWKPublicKeyLocator.java:76-77`（配合 `:111-125` sendRequest catch-all）
- **证据片段**:
  ```java
  if (currentTime > lastRequestTime + minTimeBetweenRequests * 1000L) {
      sendRequest();                 // 内部 catch(Exception) 吞掉，currentKeys 不变
      lastRequestTime = currentTime; // 无论 fetch 成败都推进
  }
  ```
- **严重程度**: P3
- **并发场景**: 同 C2-001 的 token 校验 worker 线程；IdP 故障/网络抖动窗口。
- **触发时序**: T1 TTL 过期后进入 :73 → sendRequest 内 :123 catch 住 IdP 异常（仅 log，:124）→ 返回后 :77 无条件写 `lastRequestTime = currentTime` → 后续 60s 内所有 kid-miss 校验在 :75 被节流跳过、:82 重查旧表返回 null → 验签失败。交叠后果：**IdP 瞬断一次即可令新 kid（轮换场景）的验证失败再冻结 60s**；且 TTL 门（:98 `lastRequestTime + ttl*1000 > currentTime`）锚定的是"最近一次尝试"而非"最近一次成功"——持续故障期间每 60s 的失败尝试不断把 3600s TTL 窗口向后重置，旧 key 集合无限期续命。
- **现状**: synchronized 复合操作内的状态推进与 I/O 成败解耦失败：失败也占用节流预算、也重置 TTL 锚。
- **风险**: 可用性/密钥轮换时效（fail-closed：验证失败而非放行）；无数据损坏。继承自 Keycloak adapter 同款实现的上游行为。
- **建议**: 区分"成功刷新时间"与"最近尝试时间"：TTL 门锚定 lastSuccessfulRefresh；失败路径仅保留节流（或失败用更短退避），sendRequest 返回布尔成败供 :77 条件推进。
- **失效面枚举（F3）**: 同函数 :89 `reset()` 同款"失败也推进"（管理端显式刷新，语义可接受）；LocalSendCodeRateLimiter 拒绝路径在 compute 内推进计数为**已声明语义**（"被拒尝试消耗日配额"，类注释 :38）非同族缺陷；DbLoginAttemptStore 递增失败路径（duplicate-key 回退 :106-115）无预算误消费。
- **信心水平**: 确定（行为可静态推演）；影响评级保守（fail-closed 方向）
- **误报排除**: 非"吞异常"泛化报错——焦点是临界区内状态推进与失败路径的锁语义缺口；失败被 catch 后系统仍在服务旧 key，属可论证的降级而非静默不一致。
- **复核状态**: 已保留（P3；复核员回码核实 :77 无条件推进 + :123-125 catch-all 吞异常 + :98 TTL 门锚定 lastRequestTime（"最近一次尝试"）三点全部在场；与 CL-012 同站点双登记，合并裁定按同 diff 归一）

### [C2-003] NopAuthSession 无 version 列，多写者 read-check-write 靠字段分离幂等兜底——锁语义缺口登记

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoLoginSessionStore.java:75-84`；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoUserContextCache.java:109-146`（含 :87-107 checkExpired 读路径写副作用）
- **证据片段**:
  ```java
  // DaoLoginSessionStore.logoutSession
  NopAuthSession session = loadSession(sessionId);
  if (session != null) {
      if (session.getLogoutType() == null || AuthApiConstants.LOGOUT_TYPE_NONE == session.getLogoutType()) {
          session.setLogoutType(logoutType); ...
      }
  }
  // DaoUserContextCache.saveUserContextAsync
  NopAuthSession session = dao().getEntityById(userContext.getSessionId());
  if (session == null || session.getLogoutType() != AuthApiConstants.LOGOUT_TYPE_NONE) return;
  ... session.setAccessToken(...); session.setCacheData(...); session.setLastAccessTime(...);
  ```
- **严重程度**: P3
- **并发场景**: 同一 sessionId 的多个 HTTP worker 线程：登出流程（logoutSession）、每请求上下文持久化（saveUserContextAsync，@SingleSession 脏检查 flush）、会话校验路径惰性过期（getUserContextAsync → checkExpired 写 logoutType=EXPIRE）。ORM 层实证：`nop-auth/model/nop-auth.orm.xml` 中 NopAuthSession 实体**无 version 列**（实测该 entity 块 version 计数=0），`EntityPersisterImpl.checkUpdateResult`（`nop-persistence/nop-orm/.../EntityPersisterImpl.java:508`）的乐观锁兜底对无版本实体退化为 `WHERE sid=?` 的行数校验——并发写互相静默覆盖（last-write-wins）。
- **触发时序**: T1 logoutSession 读行（logoutType=NONE）→ T2 saveUserContextAsync 读行（logoutType=NONE，通过 :116 校验）→ T1 提交 logoutType=USER → T2 提交写回旧 cacheData/lastAccessTime。交叠后果：已登出行携带新刷的 cacheData——**行仍被 :58 logoutType 校验判死，fail-closed，无放行窗口**；因 T2 不写 logoutType，登出不可被回滚。同字段并发写（cacheData）时后写覆盖前写，丢失的为档案数据非安全态。
- **现状**: 结构性锁语义缺口：三组写者无互斥、无版本条件、无集中写入口；当前无害性依赖两个隐式事实——(a) 字段分离（仅 saveUserContext 写 token/cacheData/lastAccessTime，仅 logout/checkExpired 写 logoutType），(b) token 轮换并发保存因 ORM 脏字段语义通常不回写未变化的旧 token 值。
- **风险**: 现无具体化的有害交错（本条如实标注为结构性登记，不按竞态计级）；但该无害性是脆弱的隐式契约——后续任何写者在 saveUserContextAsync 中新增字段（如写会话级安全标志）即可能引入静默丢失更新。
- **建议**: 为 NopAuthSession 补 version 列（orm 模型 plan-first 变更）使 checkUpdateResult 乐观锁兜底生效；或将行级状态迁移收敛到单写入口（logoutType 变更唯一化），并在 saveUserContextAsync 中以条件 UPDATE（`WHERE logout_type=0`）替代 check-then-write。
- **失效面枚举（F3）**: NopAuthSession 全部写点 = saveSession :70（insert，PK 冲突不可达——sessionId 生成器）、logoutSession :75-84（本条）、saveUserContextAsync :111-146（本条）、checkExpired :100-103（本条，写副作用）、`MfaLoginPolicyServiceImpl.ensureSessionRow` :140-163（check-then-insert，PK 取自既有上下文 sessionId，无碰撞面，核清）。同族无 version 表 = NopAuthMfaTrustedDevice（写点见 C2-005）、NopAuthLoginAttempt（全部单语句条件 SQL，无实体 read-modify-write，核清）。带 version 实体（NopAuthMfaCredential/NopAuthMfaSetting/NopAuthMfaRecoveryCode，orm.xml 实测均含 version 列）经 checkUpdateResult 兜底，核清。
- **信心水平**: 确定（缺口存在）；无害性判断为"很可能"
- **误报排除**: 非"看到共享表就报线程不安全"——已逐一枚举全部写点并写出交错结论；无有害时序故按 P3 结构登记而非虚构竞态。
- **复核状态**: 已保留（P3；复核员回码核实 orm.xml `NopAuthSession` 实体块 :643-645 无 versionProp 属性（对照 NopAuthMfaSetting :1065 `versionProp="version"` 在场——同文件内有无并存，非全局缺列）；三组写点枚举经 C2-round2 深挖补强（checkExpired 写副作用依赖 ORM flush 平台契约核清））
- **复核注记**: 跨单元家族关联：WI 2.4 `nop-persistence-rw-C2-001`（ORM session 零互斥，平台层）为不同失效层——本条为业务实体模型缺列，非同站点，单列不重复立项。

### [C2-004] 可信设备 register 的 max-count 检查为无约束兜底的 check-then-insert——并发注册可越过上限

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaTrustedDeviceManager.java:151-167`
- **证据片段**:
  ```java
  if (countUnexpired(userId) >= CFG_AUTH_MFA_TRUSTED_DEVICE_MAX_COUNT.get()) {
      ...
      return null; // 满员：显式 false 提示（不静默 LRU 淘汰）
  }
  try {
      NopAuthMfaTrustedDevice row = dao().newEntity();
      ...
      dao().saveEntityDirectly(row);   // 无 DB 侧条件/约束可拦"不同 hash 并发超额"
  ```
- **严重程度**: P2
- **并发场景**: 同一 userId 的 mfaVerify 成功路径并发登记（同用户多设备同时完成 MFA，如 `LoginServiceImpl` mfaVerify 并发调用）。`(userId, deviceHash)` 唯一约束（`UK_NOP_AUTH_MFA_TRUSTED_DEVICE_UH`，`nop-auth/model/nop-auth.orm.xml:1563-1565`，已实证存在）只兜"同 hash"，对"不同 hash 并发插入"无约束。
- **触发时序**: 设未过期行数 = max-1。T1、T2 各持不同 deviceHash 并发进入 register → :143 双双 findByHash=null → :151 双双 countUnexpired=max-1 < max 通过 → 双双 :164 saveEntityDirectly 插入成功（hash 不同，无唯一冲突）。交叠后果：终态 max+1 行——"新行需未过期行数 < max-count"的配额语义被并发越过（超量 = 并发度-1）。
- **现状**: 配额类 check-then-insert 无锁面亦无 DB 侧防线；javadoc :131 声明满员拒绝语义，但未声明并发口径。
- **风险**: 有界——仅同一用户并发完成 n 次 MFA 可多注册 n-1 台豁免设备；每行登记本身仍需通过完整 MFA verify，不构成 MFA 绕过。影响为配额卫生弱化（豁免面扩大）。
- **建议**: DB 侧收口：登记改为"计数条件写"或对用户维度取平台 `IResourceLockManager` 用户级锁后再 count+insert；或接受现状并在 owner 文档显式声明"max-count 为尽力配额、并发下可±(n-1) 越界"（声明边界化）。
- **失效面枚举（F3）**: register 三条路径：同 hash refresh（:144-149，约束兜底核清）、并发同 hash insert（:168-179 catch 唯一约束归一 update——约束实证存在，核清）、**不同 hash 并发新行（本条，无兜底）**。同构 check-then-insert 兄弟：UserMfaSelfService bindWebauthn :458-480 / addWebauthn :1001-1036（credentialId 唯一约束 `UK_NOP_AUTH_MFA_CREDENTIAL_CRED` 兜底，orm.xml 实证，核清）、MfaLoginPolicyServiceImpl.ensureSessionRow :148-160（PK 预定，核清）。
- **信心水平**: 确定（时序完整、约束面已实证）
- **误报排除**: 非"有界缓存"误报——对象是 DB 配额语义；非约束兜底误判——已核实唯一约束列集 (userId, deviceHash) 不覆盖本竞态。
- **复核状态**: 已保留（P2；复核员回码核实 `nop-auth.orm.xml:1564` UK 列集 (userId, deviceHash) + `MfaTrustedDeviceManager.java:151-167` 满员判定与 insert 之间无锁无 DB 条件写；触发时序完整具体）
- **复核注记**: **跨维度重复登记**：C1-005 为同一缺陷（P3/P2 判级分歧）——合并裁定归一，本条为主登记（判级分歧裁定见 merge-adjudication.md §四）。

### [C2-005] 豁免判定与撤销矩阵 TOCTOU：因子撤销后在途登录仍可豁免放行；撤销并发命中 update-on-deleted 行抛 ORM 异常

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaTrustedDeviceManager.java:112-123`（isExempted）、`:241-250`（removeAllForUser）、`:189-196`（touchRegister）
- **证据片段**:
  ```java
  public boolean isExempted(String userId, String deviceHash) {
      NopAuthMfaTrustedDevice trusted = findByHash(userId, deviceHash);
      if (trusted == null) return false;
      ...
      trusted.setLastUsedAt(now);
      dao().updateEntityDirectly(trusted);   // 撤销方并发删除 → UPDATE 0 行
      ... return true;
  }
  ```
- **严重程度**: P3
- **并发场景**: T1 = 登录流（checkMfaRequired 内豁免判定 → 后续 completeLogin）；T2 = 同用户因子变更流程（unbindMfa/confirmMfa/resetUserMfa → removeAllForUser 逐行 deleteEntity）。
- **触发时序**（两个子面）:
  (a) T1 :113 findByHash 命中未过期行 → T2 removeAllForUser 提交全量删除 → T1 :117 判定未过期（读自已删除前快照）→ 返回 true → **因子已撤销的在途登录免 MFA 完成**。窗口 = 判定到 completeLogin（毫秒~秒级）。
  (b) T1 :120 updateEntityDirectly(lastUsedAt) 与 T2 deleteEntity 并发交错 → UPDATE `WHERE sid=?`（无 version 列）命中 0 行 → `EntityPersisterImpl.checkUpdateResult`（:508, count==0 分支）抛 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND` → **该登录请求 500 失败**（fail-closed；用户重试后行已不存在走正常 MFA）。
- **现状**: 撤销矩阵与豁免判定之间无任何锁/条件写协调；撤销的"立即生效"语义对在途请求不成立，且撤销并发可反向打断豁免请求。
- **风险**: (a) 为一次性的窄窗口免 MFA 登录（设备在判定时刻确为可信，攻击价值有限）；(b) 为罕见 500。均 fail-closed 或有界，无持久化状态损坏。
- **建议**: (a) 在 completeLogin 前对豁免结论做二次确认（或接受并文档声明"豁免为判定时点语义"）；(b) lastUsedAt 审计更新改用条件 UPDATE（`UPDATE ... WHERE sid=? AND expire_at>now`，受影响行数忽略）替代实体写，规避 update-on-deleted 异常。
- **失效面枚举（F3）**: 撤销入口全清单 = removeBySid :215（自助）、removeBySidForAdmin :230、removeAllForUser :241（unbindMfa/confirmMfa/resetUserMfa 调用）——三者与 isExempted 的 TOCTOU 同构（本条统一登记）；register/touchRegister :189-196 的 update 与撤销删除并发同构于子面 (b)；removeAllForUser 逐行删除非单语句批量——中途失败留下部分删除（半个成功，F2 面，量级低：重试幂等，登记为 watch 不立案）。
- **信心水平**: 确定（时序可具体化）；影响评级保守
- **误报排除**: 非"读路径写副作用"泛化——给出了两条具体交错与不同后果；后果均有界故按 P3，不虚报 P1。
- **复核状态**: 已保留（P3；复核员回码核实 :112-123 isExempted 实体写 :120、:241-250 removeAllForUser 逐行 deleteEntity、NopAuthMfaTrustedDevice 实体块无 versionProp（orm.xml :1524-1528）→ update-on-deleted 走 `WHERE sid=?` 命中 0 行抛 ERR_ORM_UPDATE_ENTITY_NOT_FOUND 链条成立）

### [C2-006] RedisLoginAttemptStore INCR 与 EXPIRE 非原子：进程崩溃窗口可留无 TTL 计数键（理论风险）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/RedisLoginAttemptStore.java:71-79`（`:81-85` setTtl）
- **证据片段**:
  ```java
  public int incrementLoginFailCount(String key) {
      INosqlCounter counter = nosql.counter(redisKey(key));
      long v = counter.increment(1);
      if (v == 1L) {
          setTtl(redisKey(key));   // INCR 与 PEXPIRE 两步，非原子
      }
      return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
  }
  ```
- **严重程度**: P3（理论风险，未具体化为常规运行时序——需恰好命中进程死亡点）
- **并发场景**: 登录失败计数路径（`LoginServiceImpl.incrementLoginFailCount` :456）；多节点共享 Redis 键。
- **触发时序**: T1 `increment(1)` 返回 1（键已创建、无 TTL）→ 进程在该点崩溃/被 kill -9 → setTtl 未执行 → 键永久驻留且**永不过期**（后续 INCR 因 v>1 不再补 TTL）。交叠后果：该用户/IP 失败计数只增不清——达到阈值后永久锁号（成功登录 reset 可清，但锁号后无法成功登录 → 自锁死循环，需运维删键）。同类：setTtl 的 Redis 调用异常经 FutureHelper.syncGet 上抛 → 本次登录失败（计数已 +1，fail-closed，可接受）。
- **现状**: 类注释 :25 已声明"后续不刷新——有界竞态对齐 RedisMfaChallengeStore.incrFailCount 先例裁定"——**已声明边界**为"不刷新"语义本身；本条登记的是该选择残留的崩溃窗口孤儿键，属声明未覆盖的残余面，故降 P3 并标注理论风险。
- **风险**: 触发需崩溃点精确命中，概率极低；后果为单账号/IP 持久锁号（可运维恢复）。
- **建议**: 低成本消除：INCR 后无条件 `SET key value EX ttl NX` 或 Lua 合并 INCR+EXPIRE；或增加兜底——`v == 1L` 之外在 TTL 缺失（PTTL=-1）时补设。
- **失效面枚举（F3）**: 同款"首次递增设 TTL"兄弟 = `RedisMfaChallengeStore.incrFailCount`（同款裁定先例，与本条同残余面，同修）；对照组 = `DbLoginAttemptStore.incrementLoginFailCount` :96-117（单语句条件 UPDATE + 固定窗口 expire_at 落行内，无 TTL 孤儿问题，核清）、`LocalLoginAttemptStore`（JVM 内存，随进程消亡，无此面）。
- **信心水平**: 确定（窗口存在）；触发概率评估为"有趣的猜测"级（依赖崩溃时机）
- **误报排除**: 未按"非原子复合操作"直接报 P1——尊重已声明先例边界，仅登记声明外残余并如实标注理论风险与 P3。
- **复核状态**: 已保留（P3；复核员回码核实 :71-79 v==1L 才 setTtl、:81-85 setTtl 经 syncGet 上抛、:25 类注释"后续不刷新——有界竞态对齐先例裁定"已声明边界在场；F3 对照组 Db/Local 实现核清维持）

## 核清清单（C2 检查项通过，非发现）

| 检查项 | 结论 | 证据 |
|---|---|---|
| LocalSendCodeRateLimiter"恰好放行 1 个"契约 | 成立：`synchronized(targetTrackers)` 覆盖 compute+间隔判定+lastSendMs 占用全序列；32 线程并发回归钉定 | `LocalSendCodeRateLimiter.java:82-96`；`TestLocalSendCodeRateLimiter.java:85-110`（assertEquals(1, passed)） |
| 同上 IP 层并发 | compute 原子递增含返回值判定，无 check-then-act 缺口，不可能超发放行 | `:109-117` |
| 限流 Map 容量/过期 | 有界声明存在（50,000 / 48h）且 @Description 显式承认驱逐=计数重置语义 | `NopAuthConfigs.java:161-169` |
| JWKPublicKeyLocator volatile CHM 整表替换 | 无撕裂；fast-path 双 volatile 读不一致的瞬态 null 只落入同步块重查，无害 | `:43,:67-70,:122` |
| webauthn signCount 单调条件写 | 主路径 `WHERE sid=? AND signCount<?` + version 自增，affected=0 按失败——正确的 DB 条件写锁语义 | `MfaFactorVerifier.java:236-248` |
| 恢复码单次消费 | 条件写 `WHERE used=0` + affected-row 判定，并发双 verify 恰一次成功 | `LoginMfaFlow.java:279-299` |
| DbLoginAttemptStore 递增原子性 | 单语句条件 UPDATE 承接 read-modify-write；duplicate-key 回退重试为先例同款 | `DbLoginAttemptStore.java:28-35,:96-125` |
| ormTemplate==null 退化路径 | 生产 beans 注入非空（auth-service.beans.xml:161 + 容器 IOrmTemplate）；测试手工装配专用；NopAuthMfaCredential 含 version 列，"实体乐观锁兜底"声明属实（注：该退化路径并发版本冲突会以 ORM 异常而非 sign-count-race 语义暴露——测试面 watch，不立案） | `MfaFactorVerifier.java:93-95,:249-260` |
| SiteCacheDataBuilder computeIfAbsent ×10 | build-local：`new SiteCacheDataBuilder()` 每次构建新实例，实例 Map 字段单线程归属 | `SiteMapProviderImpl.java:117` |
| 登录失败计数 check-then-act 超调 | check(:368)/increment(:406) 间并发超调为限流通用接受语义，increment 本身各 store 原子 | `LoginServiceImpl.java:368,:406,:455-457` |
| 全模块锁序 | 仅 2 条叶子锁边（见锁序图），无环、无持锁调外部回调、无锁对象替换（monitor 为 this/final 字段） | 见"审计范围与口径" |
| F5 等待点超时 | 无 wait/notify/sleep/CountDownLatch；synchronized 无时限为固有；JWKS fetch 依赖 HttpClientConfig 缺省 30s/10s（建议显式化，见 C2-001） | 全树 grep |

## 维度集外线索（overlay C3/C4/C5/C7/C8/C9）

（编号续接 nop-auth-credential-CL-008 起；CL-001…007 已被 C1 占用）

### [CL-008]（C9 性能面）JWKS 锁内刷新的吞吐量化与超时显式化
`JWKPublicKeyLocator` 刷新停顿对验签路径的量化影响（C2-001 的性能延伸）：建议压测口径 = IdP 延迟 p99 × 每 60s 窗口 miss 数；另 `HttpClientConfig` 全局缺省 30s/10s 被本调用点隐式依赖，宜 per-request 显式设 timeout（`nop-auth/nop-auth-sso/.../JWKPublicKeyLocator.java:112`）。

### [CL-009]（C3 面）登录失败路径上的同步阻塞封装
`RedisLoginAttemptStore.setTtl` 用 `FutureHelper.syncGet` 同步阻塞包装异步 Redis 调用（`:81-85`），登录失败路径上每失败一次多一个同步 Redis 往返；异步链改造时注意勿留在响应关键路径。

### [CL-010]（C7/C9 面）会话上下文每请求反序列化
`DaoUserContextCache.getUserContextAsync` 每请求对 `cacheData` 做 `JsonTool.parse` + `BeanTool` 反射装配（`:68-71`）——Daocache 部署下每个认证请求的热路径 CPU/分配点，建议登记 C9 分配画像与 C7 计数观测。

### [CL-011]（C8 面）会话状态写入口分散
NopAuthSession 的 logoutType 变更分散在 logoutSession 与 checkExpired（读路径写副作用，`DaoUserContextCache.java:100-103`）两处，会话状态机无集中写入口——C8 会话状态一致性视角宜评估"惰性过期"与"显式登出"的语义合并。

---
*审计完成度声明：C2 执行步骤 1–5 全覆盖（锁载体盘点/正确性/死锁锁序/锁性能/F5 等待点）；发现 6 条（P0×0 / P1×0 / P2×3 / P3×3），全部附具体触发时序或如实标注理论风险；overlay 线索 4 条（CL-008…011）。*

---

## CL 线索复核状态（复核员回码）

| CL | 结论 | 复核证据 |
|---|---|---|
| CL-008（JWKS 锁内刷新吞吐量化 + 超时显式化） | **确认**（C2-001 的性能延伸，随主案同批） | `JWKPublicKeyLocator.java:112` HttpRequest 无 per-request timeout 维持 |
| CL-009（setTtl syncGet 同步阻塞封装） | **确认**（登录失败路径同步 Redis 往返，C9/C3 面 watch） | `RedisLoginAttemptStore.java:81-85` 在场 |
| CL-010（DaoUserContextCache 每请求反序列化） | **确认**（`DaoUserContextCache.java:68-71` JsonTool.parse + BeanTool 在场；C9 分配画像 watch） | 回码核实 |
| CL-011（logoutType 写入口分散） | **确认**（与 C2-003 同站点结构性登记，随主案合并） | `DaoLoginSessionStore.logoutSession` + `DaoUserContextCache.checkExpired` 两写点维持 |

## 维度复核结论

- 复核范围：C2-001…006（初审）+ C2-007/008（深挖，见 C2-round2.md）+ CL-008…011；复核方式 = 逐条回码（锁面/行号/约束面/平台行为链逐一比对 live code，含 GenSqlHelper/EntityPersisterImpl 乐观锁链路核验）。
- 三态判定：**保留 8 / 降级 0 / 驳回 0**。初审 6 条全部维持原判级（C2-001 P2 / C2-002~006 P3）；深挖 2 条（C2-007/C2-008，均 P3）经独立回码成立。
- 降档口径核对：C2-001 P2（锁内 RPC，30s 有界 + fail-closed 方向）核对成立——判级依据为热路径争用热点而非虚报 P1；C2-006 P3 尊重 `:25` 已声明先例边界、仅登记声明外残余，口径正确；C2-004 P2 与 C1-005 P3 的同缺陷判级分歧如实登记并移交合并裁定。
- 现状标注：全部为**新发现（未修复）**。C2-008 复核中产生一项**对既有 CL-005 的时序修正**（TOTP 双签发主时序被乐观锁偶发拦截，残余面收窄为 consume-判定分离语义缺口）——已同步登记至 C1 文件 CL-005 复核行。
- 跨单元对照（cra-index，157 条口径）：C2 八条零同站点重复。家族关联 3 处单列：(a) C2-001/C2-002 与 WI 2.3 `C4-007`（SSO token 刷新无超时阻塞 IdP HTTP）为同族（IdP 外呼阻塞）不同站点（其站点在 nop-biz-auth-core 过滤器链，本单元在 nop-auth-sso JWKPublicKeyLocator）——修复批协同、零重复立项；(b) C2-003 与 WI 2.4 `C2-001`（ORM session 零互斥）为平台/业务两层互补；(c) C2-007 挤下线 TOCTOU 全台账无同型（首例）。
- 跨维度重复：**C2-004 ↔ C1-005 同一缺陷**（本条为主登记）；C2-002 ↔ CL-012 同站点双登记（吞异常 + 无条件推进一体的两面）——按同 diff 归一，不重复立项。

## 最终保留项（C2 维度，复核后权威清单）

| 编号 | 标题 | 判级 | 复核状态 |
|---|---|---|---|
| C2-001 | JWKS 刷新 synchronized(this) 内同步 HTTP——热路径锁内 RPC | P2 | 已保留 |
| C2-002 | 刷新失败仍推进 lastRequestTime（节流预算误消费 + TTL 锚重置；含 CL-012 面） | P3 | 已保留 |
| C2-003 | NopAuthSession 无 version 列多写者 last-write-wins（结构性登记） | P3 | 已保留 |
| C2-004 | 可信设备 register max-count check-then-insert 无兜底（**主登记，吸收 C1-005**） | P2 | 已保留 |
| C2-005 | 豁免判定与撤销矩阵 TOCTOU + update-on-deleted 500 | P3 | 已保留 |
| C2-006 | Redis INCR/EXPIRE 崩溃窗口孤儿键（理论风险） | P3 | 已保留 |
| C2-007 | autoLogout/revokeUserSessions 快照漏杀——单会话契约并发失效 | P3 | 已保留 |
| C2-008 | TOTP 窗口推进乐观锁败者 500（含 CL-005 时序修正面） | P3 | 已保留 |
| CL-008…011 | 4 条线索全部确认（随主案/watch） | — | 全部确认 |

统计：**8 保留条目（P2×2 / P3×6）+ 4 条 CL 确认**；P0×0 / P1×0。
