# nop-auth 集群支持与结构治理设计

**日期**：2026-09-21
**范围**：`nop-auth/nop-auth-service`、`nop-service-framework/nop-biz-auth-core`、auth 相关 beans 装配
**状态**：active

---

## 一、设计结论

1. **发码限流组件化**：新增 `ISendCodeRateLimiter` 接口（发送类验证码限流器），承载"同目标间隔 + 同目标日配额 + 同 IP 日配额"三层语义；Local 实现（默认，行为等价现状）+ Redis 实现（`INosqlCounter` 原子计数 + 条件写）；独立配置 `nop.auth.rate-limit.store-type`（`local|redis`，默认 `local`）。`LoginServiceImpl` 与 `NopAuthUserBizModel` 中的全部 7 个限流 Map 与两份重复的限流方法由该组件取代。
2. **登录失败计数独立成 store**：新增 `ILoginAttemptStore` 接口（用户/IP 双维度失败计数，含**接口级原子递增方法**）；Local 实现（默认）+ Redis 实现（`INosqlCounter` INCRBY + PEXPIRE，先例 `RedisMfaChallengeStore.incrFailCount`）；独立配置 `nop.auth.login-attempt.store-type`（`local|redis`，默认 `local`）。`LoginServiceImpl.loginFailCountLock`（JVM 锁）随之消灭。
3. **`IUserContextCache` 缓存出口**：接口保持会话四方法签名不变（跨模块公共 API）；失败计数方法委托注入的 `ILoginAttemptStore`；图形验证码缓存暴露 `ICache` 注入点（默认仍 `LocalCache`，可注入 `NosqlCache` 等 `ICache` 实现获得分布式能力）。`DaoUserContextCache` 不再隐式继承"本地失败计数 + 本地验证码"行为。
4. **MFA 代码拆出大类**：登录级 MFA 验证流程、验证码发送流程从 `LoginServiceImpl` 拆为 `mfa/` 包组件；MFA 自服务从 `NopAuthUserBizModel` 拆为 Processor（BizModel 方法保留薄入口，GraphQL/RPC API 签名不变）；mask/extractClientIp/deleteWebauthnCredentials 等共享工具收敛到单一共享类。
5. **集群部署契约**：见 §五 配置矩阵；`store-type=local` 一律语义化为"单节点限定"；集群部署必须显式配置 `nop.auth.jwt.enc-key`。

## 二、背景与动机

- `LoginServiceImpl`（1556 行）与 `NopAuthUserBizModel`（2068 行）各自内嵌多组 Caffeine 本地限流 Map（4+3 个）与一把 JVM 锁，存储介质无配置出口；多节点部署下限流/锁号阈值被节点数稀释，LB 轮询下图形验证码必失败。
- 默认装配 `nopUserContextCache = LocalUserContextCache`：四个 `ICache` 字段在 `init()` 中硬编码为 `LocalCache`，无注入点；`DaoUserContextCache` 只覆写会话四方法，失败计数与验证码仍是本地内存。
- MFA 逻辑（验证流程/发码流程/自服务）堆在两个大类中，且存在 5 组跨类重复代码（newBoundedRateMap、checkEmailRateLimit、deleteWebauthnCredentials、maskPhone/maskEmail、extractClientIp）。
- 框架地基齐备：`ICache`/`NosqlCache`（Redis 后端 ICache 实现）/`INosqlCounter`（原子计数）/`MfaStoreProvider`（collect-beans + fail-closed 装配先例）均已存在，缺的只是 auth 侧接线。

## 三、核心设计

### 3.1 发码限流器（`ISendCodeRateLimiter`）

**职责契约**：对"发送验证码"动作做三层检查——同目标（手机号/邮箱）发送间隔、同目标日配额、同 IP 日配额；超限抛既有错误码（`ERR_AUTH_SMS_RATE_LIMITED`/`ERR_AUTH_SMS_DAILY_LIMIT`/`ERR_AUTH_EMAIL_RATE_LIMITED`/`ERR_AUTH_EMAIL_DAILY_LIMIT`，错误码面不变）。**错误 param 统一**：内容统一脱敏（`maskPhone`/`maskEmail` 后的值），且 email 维度的 param key 统一为 `ARG_CHANNEL`（现状两侧不一致：LoginServiceImpl 传明文 + 字面 `"email"` key、NopAuthUserBizModel 传脱敏 + `ARG_CHANNEL` key——统一取脱敏 + `ARG_CHANNEL` 侧，sms 维度两侧本就同为 `ARG_PHONE`）；与本仓库 PII 不外露裁定一致。这是本设计**唯一一类的用户可见行为变化**（错误码不变、param 内容与 key 统一）。

**计数分组（scope 维度）**——等价基线是现状共享拓扑，接口必须带 scope 参数：

| scope | 共享的计数器 | 等价现状 |
|---|---|---|
| `login`（短信） | phone 间隔/日配额 + IP 日配额 | `smsPhoneTracker`/`smsIpTracker`（`sendSmsCode` 与 `sendMfaCode`(sms) 共享） |
| `login`（邮件） | email 间隔/日配额 + IP 日配额 | `emailTracker`/`emailIpTracker`（`sendMfaCode`(email)） |
| `bind`（短信/proof） | phone 间隔/日配额 | `proofRateTracker` |
| `bind`（邮件/proof） | email 间隔/日配额 + IP 日配额 | `emailRateTracker`/`emailIpRateTracker`（bindMfa(email) 与 proof(email) 共享） |

scope 内共享、scope 间隔离——与现状逐组对应，默认行为等价。

**语义规格**（伪代码，定义"应该发生什么"；操作顺序 = **先递增后检查**，与现状 compute 语义一致——被间隔拒绝的尝试同样消耗日配额）：

```
checkSmsAllowed(scope, phone, ip):
  # 日界 = CoreMetrics.today().toEpochDay()，键含日界后缀实现跨天自动重置
  c = 原子递增(计数键(scope, phone, 今日))
  if c > 1 且 (now - lastSendAt(scope, phone)) < interval: 拒绝 RATE_LIMITED
  if c > dailyLimit: 拒绝 DAILY_LIMIT
  if ip 非空 且 原子递增(计数键(scope, ip, 今日)) > ipDailyLimit: 拒绝 DAILY_LIMIT
  占位 lastSendAt(scope, phone) = now
checkEmailAllowed(scope, email, ip): 同构（email-code 配置组）
```

**原子性契约（诚实表述，拒绝过度承诺）**：
- 单键原子：Local 为 JVM 临界区 compute；Redis 为 `INosqlService.counter(key)`（INCRBY）+ 首次递增 `setTimeoutAsync` 设 TTL、间隔时间戳用条件写占位（TTL=interval）。
- **跨键复合不保证全局原子**（phone 计数 / IP 计数 / 时间戳三个键）：并发下 IP 配额可存在有界竞态漂移（多放过个位数请求）。这与现状 Local 有界 Map 的驱逐漂移同类，可接受；不承诺"三键同一原子区"。
- `RedisMfaChallengeStore` 先例只覆盖单键 INCR+TTL，本设计同样只对单键背书。

**实现契约**：
- Local：JVM 内原子结构（先递增后检查，等价现状语义）；沿用 `nop.auth.rate-limit.tracker-max-size / tracker-expire` 防键空间无界增长。
- **DB（plan 2275 增补，无 Redis 部署的集群选项）**：`DbSendCodeRateLimiter`，表 `nop_auth_rate_limit_counter`（PK `counter_key`，`counter_count`，`expire_at`）。三类键同一张表：目标日计数键（`{scope}:{channel}:{target}:d{day}`）、IP 日计数键（同形 `:ip:` 段）、间隔门键（`...:i`，仅占用 expire_at）。原语映射：日计数 = `UPDATE count=count+1 WHERE key=? AND expire_at>now`（affected=0 → INSERT，主键冲突回退 UPDATE——`DbSmsCodeStore.send` 同款竞态处理）；间隔门 = 裸 INSERT（主键唯一 = SETNX 语义，冲突即拒绝，恒 SETNX 与 Redis 实现一致）；过期行惰性删除（访问时清理，`DbSmsCodeStore.verify` 先例）。原子性同 Redis 裁定：单键 SQL 原子，跨键复合有界漂移。
- 装配：collect-beans `nopAuthRateLimiter_` 前缀 + `ioc:ignore-depends` + `autowire-candidate=false` + Redis bean `ioc:condition`（`store-type=redis` + `on-class INosqlService`）——完整复刻 `MfaStoreProvider` 模式（含 fail-closed：请求类型未注册显式抛异常，不静默回退）。
- 消费点：短信/邮件/MFA/proof 全部发码入口（`sendSmsCode`、`sendMfaCode`、`bindMfa`、登记通道 proof）经同一组件，按上表传 scope。

### 3.2 登录失败计数（`ILoginAttemptStore`）

**职责契约**：用户名维度 + IP 维度的登录失败计数，语义方法：读取、设置、清零、**原子递增并返回新值**。

```
incrementAndGet(key):  # 原子
  local: 临界区内 read-modify-write（消灭 LoginServiceImpl.loginFailCountLock——锁内聚到实现）
  redis: INosqlCounter.increment + 首次递增时 setTimeoutAsync(loginFailTimeout)
  db:    UPDATE fail_count=fail_count+1 WHERE key=? AND expire_at>now（affected=0 → 重置插入，
         主键冲突回退 UPDATE——DbSmsCodeStore 同款）；返回值经递增后 SELECT（审计用途，
         并发下允许读到略新值）
```

- 接口落 nop-biz-auth-core（登录核心的一部分，零第三方依赖不变式保持）；Local 实现落 nop-biz-auth-core 且 **Local bean 注册进 `auth-core-defaults.beans.xml`**（auth-core-only 部署不经 nop-auth-service 也能装配）；Redis 实现落 nop-auth-service（同 `MfaStoreProvider` 的模块归属先例）；**DB 实现（plan 2275 增补）落 nop-auth-service**：`DbLoginAttemptStore`，表 `nop_auth_login_attempt`（PK `attempt_key` = `un:`/`ip:` 前缀键，`fail_count`，`expire_at`=loginFailTimeout TTL，过期惰性删除）——无 Redis 部署经 `store-type=db` 获得集群锁号。
- **注入语义（手工 wiring 兼容裁定）**：`AbstractUserContextCache` 持有 store 的注入点为可选注入 + **字段内联缺省 Local 实例（共享缺省单例）**（容器可覆盖，对齐本仓库 `ormTemplate`"可选注入 + 缺省退化路径"惯例）——手工 `new` 出来的测试对象不经容器也有正确（Local）行为，`TestLoginFailCountAtomicity` 的并发原子性断言在缺省实例下成立。**缺省实例必须共享**：`LoginServiceImpl` 自持的 store 缺省值与 `AbstractUserContextCache` 的缺省值解析到同一 JVM 级共享缺省实例（否则缺省路径下"写进 A 读到 B"，计数不可见）；容器路径下同 bean 注入两侧天然一致，缺省路径以共享单例保证同一性。
- `IUserContextCache` 现有 6 个失败计数方法**签名保留**（跨模块公共 API），实现改为委托注入的 store。
- Local store 订阅 `UserContextConfig` 的 `loginFailTimeout` 刷新（保持 `refreshConfig` 语义；`AbstractUserContextCache` 原 `loginFailCache` 字段的职责随迁）。
- **既有缺陷修正**：`AbstractUserContextCache.getLoginFailCountForIp` 现用 `userKey(ip)`（`"un:"+ip`）读、而 `setLoginFailCountForIp`/`resetLoginFailCountForIp` 用 `ipKey(ip)`（`"ip:"+ip`）写——读写键前缀不对称的既有缺陷（生产代码无调用方）。委托 store 时**修正为统一 `ip:` 维度**，随本设计一并落地，不留双前缀。
- 成功登录清零、IP 维度方法语义不变。

### 3.3 `IUserContextCache` 会话与验证码缓存出口

- `AbstractUserContextCache` 的 `verifyCodeCache`（`ICache<String,String>`）暴露可选注入点：缺省行为等价现状（`LocalCache` + `verifyCodeTimeout` TTL + md5 key 混淆）。
- **TTL 保持契约（硬约束）**：注入的实现必须保证**每次写入带 `verifyCodeTimeout` TTL**（put-with-TTL 语义）。注意：直接注入 `NosqlCache` **不满足契约**——其 `put` 走无 TTL 的 `ops.put`，`expireAfterWrite` 配置在写路径不消费，注入后验证码将永不过期（安全回归），且泛型为 `ICache<String,Object>`。分布式部署的正确形态是基于 `INosqlKeyValueOperations.putExAsync` 的 TTL 保持适配实现（写路径带 TTL，读路径普通 get）。缺省 `LocalCache` 天然满足。
- `userContextCache`/`userSessionCache` 不新增配置出口——集群会话的正路是既有 `nop.auth.login.use-dao-user-context-cache=true`（DB 持久化）；纯缓存分布式会话不在本设计范围（拒绝理由见 §四）。
- `LocalUserContextCache.init()` 硬编码构造保留为缺省分支，不再是无出口的唯一路径。

### 3.4 MFA 结构拆分

**组件边界**（拓扑）：

```
LoginServiceImpl（登录编排，保留）
  ├─ 登录级 MFA 流程组件（mfa/ 包）：checkMfaRequired 三层判定、mfaVerify 第二因子验证、
  │   恢复码分支（含条件写置 used）、challenge 失败计数/作废、completeMfaLogin 出口
  ├─ 验证码发送组件（mfa/ 包）：sendSmsCode/sendMfaCode 分派（sms/email/不支持类型）、
  │   发送器 fail-closed、脱敏日志
  │     └─ ISendCodeRateLimiter（§3.1）
  └─ 会话签发/审计/用户装载（保留在 LoginServiceImpl）

NopAuthUserBizModel（用户聚合根 CRUD + 薄入口，保留）
  └─ MFA 自服务 Processor：bindMfa 双通道、webauthn 三 ceremony、恢复码管理、
      可信设备管理、管理员重置、channel proof 门槛
        └─ ISendCodeRateLimiter（§3.1）/ MfaFactorVerifier / MfaTrustedDeviceManager（既有组件）

共享工具类（mfa/ 包）：maskPhone/maskEmail/extractClientIp/deleteWebauthnCredentials 单一落点
```

**约束**：
- `NopAuthUserBizModel` 的全部 `@BizMutation`/`@BizQuery` 方法签名不变（GraphQL 面 API 兼容，禁止把方法迁走到新 BizObj——那会改变 `NopAuthUser__bindMfa` 等 operation 名）。
- `LoginServiceImpl` 的 public/protected 兼容面（`ILoginService`/`ISessionBootstrap` 实现 + 既有测试子类触点）签名不变。
- 拆分是行为等价重构：既有 E2E 测试不改断言即须通过（调用点允许按 §3.6 清单重接）。

**接缝约束（拆分的结构裁定）**：
- 登录级 MFA 流程组件需要回到"会话签发"出口（`completeLogin` 及其受限变体、`ATTR_*` 回填）。接缝形态：流程组件不持有 LoginService 引用，经**完成回调端口**（构造/调用时传入的函数式出口，携带 resetFailCount/notifyHook/restricted 语义参数）回到宿主——回调语义与既有 completeLogin 裁决逐项对应，宿主仍是唯一签发点。
- MFA 自服务 Processor 不继承 `CrudBizModel`：DAO 访问经注入 `IDaoProvider`（对齐 `MfaTrustedDeviceManager` 等既有组件形态），事务边界经 `ITransactionTemplate`/BizModel 管道透传——迁移时把 `daoFor()`/`txn()` 调用点逐一改写，语义不变。
- `requireAdmin`/`requireCurrentUserId` 被"留下的用户管理方法"与"迁走的 MFA 方法"共用——共享落点为单一共享类（与 mask 工具同处），两类与 Processor 共用，不复制。
- 静态工具（`maskPhone`/`maskEmail`/`extractClientIp`/恢复码格式化等）单一落点；既有测试对旧静态入口的引用按 §3.6 清单重接。

目标形态：两大类中不再存在限流 Map 字段、限流方法、发码私有流程、mask/extractClientIp 私有副本；行数显著收缩（验收时以 `wc -l` 记录前后值）。

### 3.6 测试兼容契约（"零断言修改"的精确语义）

- **断言不改，调用点允许重接**：拆分/组件化会移动 protected/private 方法，直接引用它们的既有测试按下列清单重接到新落点，断言语义保持不变。"零断言修改通过"一律指这一精确语义。
- 已知触碰清单（执行时以编译错误为准全量核对，不限于以下）：
  - `TestLoginRateLimitAndAudit`（子类调用 `LoginServiceImpl.checkSmsRateLimit`）→ Phase 1 重接限流组件；其并发间隔原子性用例平移为限流组件 Local 实现的并发回归。
  - `TestSmsSendFailClosed`（子类调 `LoginServiceImpl.sendSms` + 反射调 `NopAuthUserBizModel.sendSmsForBinding`）→ `sendSms` 部分随 Phase 3 重接到发码流程组件；`sendSmsForBinding` 部分随 Phase 4 重接（该方法属 NopAuthUserBizModel 迁移面）。
  - `TestRecoveryCodeFormat`（静态引用 `NopAuthUserBizModel` 恢复码格式化方法）→ Phase 4 重接到新落点。
  - `TestRecoveryCodeConditionalWrite`（直接触碰 `LoginServiceImpl` 恢复码枚举与验证方法）→ Phase 3 重接到 MFA 流程组件。
  - `TestLoginFailCountAtomicity`（手工 wiring，6 字段反射注入；**不为 store 增 wiring**）→ 依赖 §3.2 共享缺省单例裁定，现有 wiring 不变即通过。
- **唯一允许的断言变化**：错误 param 统一（§3.1：内容脱敏 + email key 归一 `ARG_CHANNEL`）涉及的 param 断言，逐处枚举修改。

### 3.5 集群部署契约（使用契约）

| 关注点 | 配置/装配 | 集群取值 | 未配置时集群后果 |
|---|---|---|---|
| 会话持久化 | `nop.auth.login.use-dao-user-context-cache` | `true` | 会话仅存单节点内存，跨节点请求 401 |
| MFA/验证码 store | `nop.auth.mfa.store-type` | `redis`（性能优选） | 默认 `db` 已集群安全；误配 `local` 时挑战/验证码跨节点不可见 |
| 发码限流 | `nop.auth.rate-limit.store-type` | `redis` 或 `db`（无 Redis 部署取 `db`，plan 2275） | 阈值被节点数稀释（默认 `local`） |
| 登录失败计数 | `nop.auth.login-attempt.store-type` | `redis` 或 `db`（无 Redis 部署取 `db`，plan 2275） | 锁号阈值被节点数稀释（默认 `local`） |
| 图形验证码 | `verifyCodeCache` 注入满足 TTL 保持契约（§3.3）的分布式 `ICache` 适配实现（可选） | 基于 `putExAsync` 的 TTL 适配实现（**不可直接注入 `NosqlCache`**，见 §3.3） | LB 轮询下验证码必失败（或用粘滞会话） |
| JWT 签名 | `nop.auth.jwt.enc-key` | **必须显式配置** | 未配置时按 JVM 随机派生密钥，token 跨节点/重启不可验 |

- `store-type=local`（新旧各处）一律文档化为**单节点限定**，集群部署禁止取值。
- 限流/失败计数后端不可用时 fail-closed（拒发码/拒登录尝试），不静默放行——与 `MfaStoreProvider` 语义一致。

## 四、拒绝了什么

1. **直接在 `LoginServiceImpl` 内嵌 Redis 限流**：无接口抽象、与既有 store 装配模式不一致、不解决 `NopAuthUserBizModel` 侧重复。
2. **复用 `INosqlRateLimiter`（令牌桶）作为限流原语**：语义不符——需求是"固定间隔 + 日历日配额"，令牌桶是"速率+容量"；且日配额需日界重置语义。Redis 实现改用 `INosqlCounter` + 条件写组合。
3. **用 `ICache.compute`/get+put 承接原子递增**：`ICache` 默认实现跨网络不原子；原子性必须是接口级契约（`ILoginAttemptStore.incrementAndGet`），由各后端用原生原子原语实现。
4. ~~限流/失败计数落 DB~~（**plan 2275 推翻**）：原拒绝理由是写入压力与瞬态数据无持久化价值。用户裁定推翻：DB 是无 Redis 部署的唯一集群后端选项（比照 session 的 `DaoUserContextCache` 与 MFA store 的 `Db*` 先例），且发码/失败本就是低频事件，一次 UPDATE 的代价可接受；写入的临时性由 `expire_at` TTL + 惰性删除承载，不构成持久化负担。
5. **破坏 `IUserContextCache` 方法签名**：跨模块公共 API（plan-first 区域）；用"签名保留 + 内部委托"过渡。
6. **MFA 自服务方法迁到新 BizObj**：改变 GraphQL operation 名（`NopAuthUser__bindMfa` → 其他），破坏前端兼容；保留方法在 `NopAuthUser` 上作为薄入口。
7. **为会话缓存新增分布式纯缓存出口**：与既有 Dao 会话模式重复且引入缓存失效复杂性；集群会话正路是 DB 持久化（既有配置）。
8. **限流与 MFA store 共用一个 `store-type`**：故障域不同（限流后端故障应只拒发码，不应拖垮 MFA/登录）、演进节奏不同；各自独立配置。

## 五、与已有设计的关系

- `01-architecture-baseline.md` §3.3：**维持**"`IUserContextCache` 验证码通道不复用于 MFA 码"裁决（MFA 用独立 `SmsCodeStore`/`EmailCodeStore`）。本设计只动 `IUserContextCache` 的原始职责（登录失败计数 + 图形验证码）的**存储出口**，不改 MFA store 体系。
- `02-mfa-phase2-design.md` §5.3.7 结论 5（"分组件内存计数"）：本设计取代其**存储介质**部分——per-入口三层限流语义、入口隔离、错误码面全部保持；内存计数降级为 Local 实现（默认行为等价）。
- ai-dev/lessons/15（类加载安全）：Redis 实现一律 `ioc:condition`（`on-class INosqlService`）条件激活，classpath 无 nosql 不注册不加载。
- `docs-for-ai/02-core-guides/service-layer.md`「复杂流程 = BizModel 入口 + Processor」「何时拆 Processor」：§3.4 拆分的直接依据。

## 六、验收契约（设计层）

- 单节点默认行为等价（既有测试按 §3.6 精确语义通过；唯一用户可见变化 = 错误 param 统一脱敏）。
- 计数分组等价：四个 scope 组的共享/隔离拓扑与现状逐组对应（§3.1 表）。
- Redis 装配下：单键原子性（INCR/SETNX+PX）与跨实例共享有测试证明；跨键复合不承诺全局原子（有界竞态漂移可接受）。
- 两大类无本地限流 Map/锁/重复工具副本（`grep` 可验证：`newBoundedRateMap`、`proofRateTracker` 等标识符仅存在于新组件与测试）。
- `getLoginFailCountForIp` 键前缀缺陷修正（统一 `ip:` 维度）。
- 集群部署配置矩阵进入 `docs-for-ai`（03-modules/nop-auth.md 或 02-core-guides 相应 owner doc）。
