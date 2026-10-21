# C6 资源生命周期与泄漏 — 深挖第 2 轮（WI 5.1 nop-auth-credential）

- 审计维度：C6（资源生命周期与泄漏）深挖追加轮
- 日期：2026-10-21
- 审计员：C6 深挖子代理（盲审纪律：未读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 其余内容；未 git log；仅读本目录第 1 轮三文件 + `ai-dev/skills/concurrency-resource-audit-prompt.md`）
- 编号口径：C6 续接 C6-005；CL 续接 CL-017
- 复核状态：已复核（复核结论见文末「复核结论（C6-round2）」节：C6-005 保留 P2、CL-017 watch 确认）

## 深挖范围与覆盖声明

### 指定深挖目标逐项落位

| 指定目标 | 落位结论 |
|---|---|
| **MfaTrustedDevice 过期清理（C6-003 的资源释放面）** | 本体（无自动回收、逐用户无上界）C6-003 已登记（P2），本轮不重复。增量资源面两条：(a) **清理义务的实际推派路径核清**——`listForUser` javadoc"含过期——管理端展示 + 自助清理"与 `UserMfaSelfService:1064`（"全部行含过期标记——支持自助清理"）证实唯一回收通道 = 用户/管理员逐行人工 removeBySid，无任何批量/定时/登录时点触发，与 C6-002 五族的"同键惰性删除"相比**连惰性删除都没有**（过期行对该用户后续请求仍是 findAllByExample 全量装载对象）；(b) **CL-017**——过期行滞留直接放大 register/isExempted/listForUser/countUnexpired 四个热路径的读取成本（O(全部历史行) 装载后内存过滤），资源消耗随行数单调上升——登记为 C7/C9 面 watch。 |
| **OAuthTokenClient token 刷新资源** | 本轮新发现 **C6-005**（刷新成功与持久化之间的半个成功窗口丢失轮换 refresh token）。C6-004（无调用点超时）不变。`IHttpResponse` 全缓冲契约（第 1 轮"已验证非问题"#1）复核维持。 |
| **Redis 连接生命周期** | **核清合格（显式记录为非发现）**：六个 Redis 使用方（RedisLoginAttemptStore/RedisSendCodeRateLimiter/Redis{MfaChallenge,Sms,Email}CodeStore + mfa/ratelimit provider 装配点）全部经注入的单例 `INosqlService` 门面操作——`nosql.counter(key)`/`nosql.remove(key)`/`nosql.setTimeoutAsync(...)` 返回的 `INosqlCounter` 等为无状态键视图（`nop-nosql-core` 接口，无 close/AutoCloseable 语义），使用面**零 acquire/release 配对义务**；连接与连接池生命周期由平台设施（nop-nosql 实现）持有，按误报校准第 5 条"平台设施自身不重审，只审使用方式"裁定。使用面唯一资源缺陷 = CL-006 族 TTL 附着两步非原子（第 1 轮已登记，本轮复核其 F3 清单完整：6 递增点 + setLoginFailCount 对写，无第 7 处遗漏——`RedisSendCodeRateLimiter.check` 的 IP 层 :104-107 已在族清单内，间隔标记 :93-94 为 SETNX+PX 原子键不受影响，核清补全）。 |

### 补充横扫（C6 步骤 1–6 增量核清）

- 线程/池/流/文件锁/listener 注册：范围 14 ROOT 全零命中（第 1 轮预校准维持）。
- `AuditServiceImpl`：入队 `getQueue().send`（平台批量设施）+ `NopAuthOpLog` append-only——有目的的审计保留（第 1 轮 C6-001 误报排除 (1) 同判），非 F4 缺陷。
- `WebAuthnAuthenticator`/`MfaCodeSender`/`MfaChallengeHelper`：无资源获取点。
- `NopAuthSessionBizModel`（本轮核清增量）：继承 `CrudBizModel` 通用 mutation 面（`_service.beans.xml:141-145` 注册 biz 代理），即通用 CRUD `delete`/`deleteByQuery` 对 session 行**权限门控可达**——作为 C6-001"全仓不存在任何 DELETE 语句"表述的**事实修正**记录（自动清理缺失的结论不变，绝对化表述在复核阶段勘误）；MFA 敏感八表已被 `MfaSensitiveTableBizModel` 收口，session 表不在收口集内。

## 发现

### [C6-005] OAuth2 惰性刷新"半个成功"窗口：token endpoint 已轮换 refresh token 而本地事务回滚——新凭据丢失，旧 refresh token 可能已被 IdP 作废，凭证永久失效待人工重授权

- **文件**: `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java:442-468`（锁内 HTTP 刷新 + 内存合并）；`:353-361`（加密回写，同一事务）；`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/OAuthTokenClient.java:82-131`（fetchToken 成功即返回）
- **证据片段**:
  ```java
  // refreshIfNearingExpiry —— engineUpdateInLock 的 updater 回调内（DB 事务 + 行锁持有中）
  response = oauthTokenClient.refresh(type.getOauth2().getTokenEndpoint(),
          clientId, clientSecret, refreshToken);        // ① IdP 侧已消费/轮换旧 refreshToken
  ...
  merged.put("accessToken", response.getAccessToken());
  if (response.getRefreshToken() != null) {
      merged.put("refreshToken", response.getRefreshToken());  // ② 新 token 仅存在于内存 map
  }
  ...
  // :353-359（回调返回后） entity.setData(credentialCipher.encrypt(...)); dao.updateEntityDirectly(entity);
  //   —— ③ 若此处写失败 / 事务提交失败 / 进程崩溃 → 整个事务回滚
  ```
- **严重程度**: P2
- **并发场景**: 单请求线程的失败路径（非竞态）：oauth2 凭证取用触发临期刷新（`refreshIfNearingExpiry` → `engineUpdateInLock` 行锁事务）→ ① HTTP 刷新成功（IdP 侧按轮换语义作废旧 refreshToken——RFC 6749 §10.5 与主流 IdP 缺省行为）→ ③ 本地加密回写或事务提交失败（DB 抖动、version 冲突后的重试路径、部署期 kill、连接池耗尽）。另叠加跨副本面：A 副本刷新成功提交前，B 副本仍持旧 refreshToken，其后续刷新在轮换 IdP 上直接 invalid_grant（行锁互斥只串行化本库，不约束 IdP 侧失效时序）。
- **触发时序**: T1 进入 :414 锁内 → :444 刷新 HTTP 200（IdP 已作废旧 refreshToken=R_old，签发 R_new）→ :459 内存 map 合并 R_new → :359 updateEntityDirectly 抛异常（或事务提交失败/进程死亡）→ 事务回滚，DB 仍存 R_old → 交叠后果：本地持有的 R_old 在 IdP 侧已失效，下次刷新 fail-closed 抛 `ERR_CREDENTIAL_OAUTH_REFRESH_FAILED`（invalid_grant），凭证不可用直到人工重新走 authorization_code 授权。无自动恢复路径（`NopCredential` 无重授权状态机，javadoc 仅"提示重新授权"）。
- **现状**: 刷新链正确做到了"失败不静默用旧 token"（fail-closed 判级成立，非 P1 数据损坏），但"IdP 已前移、本地未跟进"的半个成功无补偿语义——既无 R_new 的持久化重试/暂存（如先落无版本约束的暂存列再原子切换），也无 invalid_grant 时的显式状态迁移（status=reauth-required）让失败可管理而非每次取用重复报错。javadoc :305-307 声明了锁内外呼的吞吐代价与回滚语义，未声明轮换语义下的此窗口。
- **风险**: 使用轮换式 IdP（多数主流实现）的部署中，一次刷新瞬间的 DB 故障/崩溃即产生一个"需人工介入"的永久失败凭证；非轮换 IdP（旧 refresh token 长期有效）下窗口无害。触发概率低（需精确窗口）但后果为持久性运维负担，且随凭证数量线性放大介入面。
- **建议**: (a) invalid_grant 归一：捕获刷新失败将 `status` 置 `reauth-required`（复用既有 status 字段），取用方得到明确"需重授权"错误而非重复刷新异常；(b) 窗口收窄/补偿：刷新成功后先以独立小事务持久化新 token 集（`engineUpdateTokenFields` 已是保留名合并写通道，可作补偿路径复用）再返回，或对回写失败重试一次；(c) javadoc 补声明轮换语义窗口（当前 :305-307 只声明吞吐代价）。
- **失效面枚举**（F3 同族清扫）: "外部资源已变更 × 本地持久化失败"清单——`refreshIfNearingExpiry`（本条，engine 惰性刷新通道）；`engineUpdateTokenFields`（引擎回调回写通道——输入来自调用方内存而非外呼，无 IdP 侧前移面，核清不适用）；`VaultCredentialKeyProvider.fetchMaterial`（启动期只读装载，无写回路径，核清）；`OAuthLoginServiceImpl` 三处 fetchAsync（用户会话 token，登录态重建成本低且无持久化义务面，核清）。同族无第二站点。
- **信心水平**: 很可能（本地回滚窗口与回写路径为代码事实；"旧 refresh token 被作废"取决于 IdP 轮换策略，代码/模型内无豁免声明——按 RFC 缺省行为判定，标注依赖外部契约）
- **误报排除**: 非"锁内做 I/O 一律报缺陷"（那是 C6-004 已登记的吞吐面）；非"失败抛错即缺陷"——fail-closed 判定正确，登记对象是"半个成功无恢复语义"（F2 定向项）：外部世界已前进、本地状态回退且无补偿/状态迁移；不按 P1 报因触发需失败窗口精确命中且后果为可恢复的运维负担（无数据损坏、无安全面）。
- **复核状态**: 已保留（P2；复核员回码核实 :414-468 锁内刷新回调、:353-361 同事务回写、TestCredentialOAuthRefresh 证实刷新通道可达、javadoc :305-307 仅声明吞吐代价未声明轮换窗口；同族无第二站点核实成立）

## 维度集外线索（overlay C7/C9）

### nop-auth-credential-CL-017（C7/C9 面，watch）可信设备全行装载放大：register/isExempted/listForUser/countUnexpired 每请求 O(全部历史行) 装载

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaTrustedDeviceManager.java:253-265`（listForUser/countUnexpired findAllByExample 全量）；`:269-275`（findByHash 亦全量后取首）
- **事实**: 与 C6-003 的过期行无回收叠加：`register` 每次登记调 findByHash（全行）+ countUnexpired（再全行）共两次全量装载，`isExempted` 每次豁免判定一次全量装载；行数随用户历史设备数单调增长（C6-003 本体），四个热路径的 DB 读取量随之上浮。当前 max-count=5 量级下单用户行数缓慢增长，放大可量化但温和——登记 C7/C9 watch，修复与 C6-003 清理立项同批（加 `expireAt > now` 过滤或 count 下推 SQL 后自动消除放大面）。与 C6-002 五族对照：五族至少有同键惰性删除托底，本类连惰性删除也无，放大面为六族中最重。

### 核清清单（本轮检查通过，非发现）

| 检查项 | 结论 | 证据 |
|---|---|---|
| Redis 连接生命周期（指定目标） | 使用面零 acquire/release 义务；连接归平台 nop-nosql 设施（校准第 5 条不重审） | `INosqlService.java:12-34`；六个 store 注入用法逐一核清 |
| CL-006 族 F3 完整性复核 | 6 递增点 + setLoginFailCount 对写全列；IP 层计数键已在族内；间隔标记键为 SETNX+PX 原子无此面——族清单无遗漏 | `RedisSendCodeRateLimiter.java:86-111` 对照 CL-006 清单 |
| `IHttpResponse` 资源面（指定目标复核） | 全缓冲契约维持，无 close 义务 | 第 1 轮"已验证非问题" #1 维持 |
| `OAuthTokenClient` 分支覆盖 | exchangeAuthorizationCode/refresh 共用 fetchToken，超时面已由 C6-004 承载；响应解析无中间资源 | `OAuthTokenClient.java:58-131` |
| MfaTrustedDeviceManager 清理义务推派 | 唯一回收 = 人工逐行 removeBySid（listForUser javadoc + UserMfaSelfService:1064 双证），无批量/定时——C6-003 证据补强 | `MfaTrustedDeviceManager.java:252-257` |
| NopAuthSession 通用 CRUD 删除面 | biz 代理注册实证（`_service.beans.xml:141-145`）——C6-001 绝对化表述的事实修正记录（自动清理缺失结论不变） | `_service.beans.xml:141-145` |
| `regenerateRecoveryCodes` 删-插配对 | 同 @BizMutation 事务内，失败整体回滚，无半个成功 | `UserMfaSelfService.java:1541-1567` |
| 审计表 append-only | 有目的保留，非 F4 | `AuditServiceImpl.java:44-51` |

## 维度继续判定

**判定：C6 维度深挖收敛，建议终止追加轮。** 依据：(a) 三个指定目标全部落位——trusted-device 清理面确认为 C6-003 本体 + CL-017 放大面、token 刷新面补出 C6-005 半个成功、Redis 连接生命周期核清为非发现；(b) 范围内资源形态四类（DB 行 / Redis 键 / 进程内缓存 / HTTP 响应）两轮全配对核查，本轮唯一新增缺陷（C6-005）属"外部资源 × 本地持久化"的跨边界窗口，四类形态内已无未核查获取点；(c) 残余面（C6-001 的通用 CRUD 删除表述勘误、C6-002/003 的批量清理统一立项）均为既有发现的修复口径问题，非新缺陷。修复批次落地后按 F3 对六族存储的清理配对重开一次抽查即可。

---
*（本文件为 C6 维度深挖第 2 轮产出；发现与 CL 均待独立复核。全程未读 ai-dev/audits、plans、bugs、lessons、backlog 其余内容，未 git log。）*

---

## 复核结论（C6-round2）

- C6-005：**保留 P2**。半个成功窗口与"依赖 IdP 轮换契约"标注经复核员独立核实；建议 (a) invalid_grant 归一与 (b) 补偿持久化方向与既有 engineUpdateTokenFields 通道可行性核对一致。
- 指定深挖目标落位复核认可：trusted-device 清理面 = C6-003 本体 + CL-017 放大面；Redis 连接生命周期核清为非发现（INosqlService 门面无状态视图 + 校准第 5 条）成立；CL-006 族 F3 清单补核无第 8 处遗漏。
- C6-001 表述勘误（通用 CRUD 删除面可达）经 `_service.beans.xml:141-145` 独立证实，已同步登记至 C6 主文件复核行——勘误不降级裁定复核认可。
- 维度继续判定（终止追加轮）复核认可。
