# nop-auth 集群支持现状与 MFA 代码拆分分析

> Status: resolved
> Date: 2026-09-21
> Scope: `nop-auth/*`（重点 `nop-auth-service`）+ `nop-service-framework/nop-biz-auth-core`
> Conclusion: 11 项关键事实声明经独立子 agent 逐条核实全部 PASS（零行号偏差、无实质遗漏）；P0（验证码/失败计数/限流的集群不可用）与 P1（两大类多关注点 + 5 组重复代码）成立，修正方向为"限流组件化 + IUserContextCache 缓存出口 + MFA 流程拆出大类"，由 design 文档与修正计划 2274 接手。

## Context

- 用户诉求：MFA 实现堆在单个类中代码过多，应按 `docs-for-ai` 最佳实践拆分；`LoginService` 多处使用本地 Map，应改用 `ICache` 等接口以支持可选分布式缓存与集群部署。
- 本分析盘点 auth 链路中所有不支持集群的实现，评估 MFA 相关大类的拆分方案，为后续修正计划提供依据。
- 方法：逐文件阅读 live 代码（非引用旧日志），交叉核对 beans 装配默认值。

## Analysis

### 一、集群安全实现盘点

#### A. 硬编码本地状态，无任何抽象/配置出口（核心问题）

**A1. LoginServiceImpl 的 4 个限流 Map + 1 把本地锁**

`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java`（1556 行）：

| 字段 | 位置 | 用途 |
|---|---|---|
| `smsPhoneTracker` | L857 | 短信登录发码：同手机号间隔 + 日计数 |
| `smsIpTracker` | L858 | 短信登录发码：IP 日计数 |
| `emailTracker` | L1085 | MFA email 发码：同邮箱间隔 + 日计数 |
| `emailIpTracker` | L1086 | MFA email 发码：IP 日计数 |
| `loginFailCountLock` | L254 | 登录失败计数 read-modify-write 的 JVM 内 `synchronized` 锁 |

- 全部为 `Caffeine.asMap()` 视图（`newBoundedRateMap()`，L862-867），硬上限 + 过期可配置（`nop.auth.rate-limit.tracker-max-size / tracker-expire`），但**存储介质不可配置**。
- L248-253 的注释已自认问题："本地锁闭合单实例并发丢失更新；多节点共享缓存部署需缓存层原子原语"。
- 集群后果：N 节点部署时限流阈值实际放大 N 倍（每节点独立计数），间隔限制可通过 LB 轮询绕过；短信/邮件轰炸防护被稀释。

**A2. NopAuthUserBizModel 的 3 个限流 Map（与 A1 重复实现）**

`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java`（2068 行）：

| 字段 | 位置 | 用途 |
|---|---|---|
| `proofRateTracker` | L1350 | bindMfa 登记通道 proof 短信发码限流 |
| `emailRateTracker` | L1351 | bindMfa(email) + email proof 发码限流 |
| `emailIpRateTracker` | L1352 | 同上 IP 维度 |

- 自带一份 `newBoundedRateMap()` 拷贝（L1355-1360，与 LoginServiceImpl 逐字重复）。
- `checkEmailRateLimit`（L1549-1586）是 `LoginServiceImpl.checkEmailRateLimit`（L1094-1132）的近似逐字拷贝；`checkProofRateLimit`（L1472-1495）是 `checkSmsRateLimit` 手机号维度的拷贝。

**A3. LocalUserContextCache 是默认装配，且 DaoUserContextCache 只覆盖一半**

- `AbstractUserContextCache`（nop-biz-auth-core）持有 4 个 `ICache` 字段——**抽象已存在**：`userContextCache`（会话）、`userSessionCache`（userName→sessionId）、`loginFailCache`（登录失败计数，user+IP 双维度）、`verifyCodeCache`（图形验证码）。
- `LocalUserContextCache.init()`（L19-34）将四个字段**硬编码构造为 `LocalCache.newCache(...)`**，无注入点。字段类型虽是 `ICache`，但外部无法替换为分布式实现。
- 默认装配链：`nop-biz-auth-core` 的 `auth-core-defaults.beans.xml` L46-48 注册 `nopUserContextCache = LocalUserContextCache`（`ioc:default`）；`nop-auth-service` 的 `auth-service.beans.xml` L159-163 仅在 `nop.auth.login.use-dao-user-context-cache` 开启时才被 `DaoUserContextCache` 覆盖。
- 会话查询无 DB 回退：`AbstractLoginService.doGetUserContext` 直查 `userContextCache.getUserContextAsync`。默认装配下，登录请求落在节点 A，后续请求落在节点 B 时会话不存在 → **默认部署严格单节点**。
- **DaoUserContextCache 的半本地缺陷**：它 `extends LocalUserContextCache`，只覆写 `getUserContextAsync/saveUserContextAsync/getUserSessionId/removeUserContextAsync`（会话走 DB）；`loginFailCache` 与 `verifyCodeCache` 仍继承本地 LocalCache 实现。即开启 Dao 模式后：
  - 登录失败计数仍按节点独立 → 集群下暴破锁号阈值被稀释 N 倍；
  - 图形验证码（`generateVerifyCode`/`checkVerifyCode`）仍是节点本地 → **LB 轮询下验证码必然校验失败（功能破坏，非仅安全弱化）**，除非会话粘滞。

#### B. 显式可选的本地 store（设计内选项，需文档钉死单节点限定）

- `LocalMfaChallengeStore` / `LocalSmsCodeStore` / `LocalEmailCodeStore`（nop-biz-auth-core `mfa/store/`，均基于 `ConcurrentHashMap`）。
- 经 `MfaStoreProvider` 按 `nop.auth.mfa.store-type`（`local|db|redis`，默认 `db`）选择；Redis 实现走 `INosqlService`（`RedisMfaChallengeStore` 用 `putExAsync`/`removeIfMatch`(Lua CAS)/`INosqlCounter`(INCRBY)）。
- 裁定：这不是缺陷——是带配置出口的单节点选项，与 A 类"无出口"有本质区别。但 `local` 选项的集群不可用性目前无文档声明（配置项旁无警示）。

#### C. 集群安全的实现（对照组，无需改动）

- `DaoLoginSessionStore`（默认装配）：会话行落 DB。
- `DbMfaChallengeStore` / `DbSmsCodeStore` / `DbEmailCodeStore`（默认）+ 三个 Redis 变体。
- `MfaTrustedDeviceManager`：可信设备为 DB 行。
- TOTP：无状态验证 + 失败计数落 `NopAuthMfaSetting` 字段（DB）。
- `SiteMapProviderImpl`：`IResourceLoadingCache` 本地只读缓存（DB 读穿透），集群下仅存在跨节点陈旧窗口（权限判定数据源为 DB），可接受，不在本次修正范围。
- `ChannelBindServiceImpl` / `ChannelLoginApiBizModel`（nop-ai-gateway）的 `providersByType`：不可变装配注册表，非运行时状态。
- `SiteCacheDataBuilder` 的 Map：一次性构建过程的临时状态。

### 二、MFA 代码分布与大类问题

**当前分布**（live 行数，`wc -l`）：

| 类 | 行数 | 内容 |
|---|---|---|
| `NopAuthUserBizModel` | 2068 | 用户实体 CRUD + 约 1500 行 MFA 自服务（bindMfa×2 重载、webauthn 三个 ceremony、恢复码、可信设备管理、管理员重置、channel proof、发码、限流、脱敏） |
| `LoginServiceImpl` | 1556 | 密码/短信登录 + MFA 第二因子验证（challenge peek/恢复码/条件 EQL UPDATE/credential 物理删除）+ MFA 发码（sms/email）+ 限流×2 + 审计 + 脱敏 + buildUserContext |
| `LoginApiBizModel` | 628 | 登录 API 入口编排（已较薄） |
| `mfa/` 包其余类 | 均 ≤488 | MfaFactorVerifier、MfaTrustedDeviceManager、WebAuthnAuthenticator、RoleMfaPolicyEvaluator、OperationMfaCheckerImpl、MfaStoreProvider 等（此前 workstream 已拆出的共享组件） |

此前 MFA workstream（W1-W15）已把因子校验、可信设备、WebAuthn 验证器、策略评估器拆为共享组件，方向正确；剩余问题：

1. **`NopAuthUserBizModel` 违反单一聚合根关注点**：`docs-for-ai/02-core-guides/service-layer.md`「BizModel 必须对应真实聚合根」+「何时拆 Processor」（方法多步骤编排、逻辑被多入口复用、单类难读难测）；`choose-entity-bizmodel-processor.md` 明确"BizModel 一个方法塞满全部业务流程"是常见坑。MFA 自服务操作的目标聚合根是 `NopAuthMfaSetting`/`NopAuthMfaCredential` 等，不是 `NopAuthUser`。
2. **`LoginServiceImpl` 同样违反**：MFA 验证流程、发码流程、限流是三个独立关注点，全部内联在登录服务里。
3. **跨类重复代码**（拆分的直接受益点）：
   - `newBoundedRateMap()`：LoginServiceImpl L862 / NopAuthUserBizModel L1355 两份逐字拷贝；
   - `checkEmailRateLimit`：两份近似逐字拷贝；
   - `deleteWebauthnCredentials`：LoginServiceImpl L744 / NopAuthUserBizModel L1792 两份；
   - `maskPhone`/`maskEmail`：LoginServiceImpl L1018/L1026 与 NopAuthUserBizModel L1833/L1589；
   - `extractClientIp`：NopAuthUserBizModel L1601 与 LoginApiBizModel 同型（注释自认）。

### 三、可复用的框架设施（修正方案的地基）

| 设施 | 位置 | 说明 |
|---|---|---|
| `ICache<K,V>` | `io.nop.commons.cache`（nop-kernel/nop-commons） | 用户点名的接口；`putIfAbsent`/`removeIfMatch` 等带原子语义默认方法 |
| `LocalCache` | 同上 | 本地实现 |
| `NosqlCache` | `nop-nosql-core` `cache/NosqlCache` | **已存在的 Redis 后端 `ICache` 实现**（`INosqlKeyValueOperations`） |
| `ICacheProvider` / `ICacheFactory` / `LocalCacheProvider` | `io.nop.commons.cache` | 按名取 cache 的装配抽象 |
| `INosqlService.counter(key)` → `INosqlCounter` | `nop-nosql-core` | 分布式原子计数（`RedisMfaChallengeStore.incrFailCount` 已用） |
| `MfaStoreProvider` collect-beans 装配模式 | `nop-auth-service/mfa/` | `local/db/redis` 按配置选择 + fail-closed，同模块先例（含 `ai-dev/lessons/15` 类加载安全教训） |
| `IRateLimiter` | `io.nop.commons.concurrent.ratelimit` | 仅 JVM 内令牌桶，语义不符（需要 per-key 间隔+日计数），不可直接复用 |

结论：**框架已具备全部地基（ICache 抽象、Redis 实现、原子计数、成熟装配先例），缺的只是 auth 侧把硬编码本地结构换成这些接口并给出配置出口。**

### 四、问题分级

- **P0-1（集群功能性破坏）**：图形验证码 + 登录失败计数在默认与 Dao 两种装配下均为节点本地（A3）；LB 轮询下验证码必失败。
- **P0-2（集群安全稀释）**：7 个限流 Map（A1/A2）与 `loginFailCountLock` 无分布式出口，N 节点下防护阈值 ×N。
- **P1-1（结构）**：`NopAuthUserBizModel`（2068 行）与 `LoginServiceImpl`（1556 行）承载多关注点，违反 service-layer.md 拆分指引；重复代码 5 组。
- **P2（文档）**：`store-type=local` 单节点限定未声明；`use-dao-user-context-cache` 的半本地行为未声明。

### 五、改进建议（方向层，实现细节归 plan/design）

1. **限流组件化 + 存储可配置**：新增共享限流组件（接口形态，如按 key 的间隔+日计数检查器），提供 local 默认实现与 redis 实现（`INosqlCounter` 原子计数 + 日界键），沿用 `MfaStoreProvider` 的 collect-beans 装配与 fail-closed 语义；替换两处共 7 个裸 Map，同时消灭 5 组重复代码。
2. **`IUserContextCache` 缓存出口**：`LocalUserContextCache` 的 `init()` 改为可注入/可配置构造（`ICache` 层面），使 `loginFailCache`/`verifyCodeCache`（以及会话缓存）可整体切换为 `NosqlCache`；`DaoUserContextCache` 不再隐式继承本地 fail-count/verify-code 行为。
3. **MFA 拆分**：
   - `LoginServiceImpl` → 抽出 MFA 验证流程（mfaVerify/恢复码/completeMfaLogin）与 MFA 发码流程（sendMfaCode sms/email 分支）为 `mfa/` 包组件，登录服务保留编排；
   - `NopAuthUserBizModel` → MFA 自服务抽到 Processor/组件（BizModel 方法保留为薄入口，`@BizMutation` 签名不变——它们是 GraphQL 面，不可破坏 API 兼容）。
4. **共享工具收敛**：mask/extractClientIp/deleteWebauthnCredentials 等落单一共享类。
5. **文档**：`docs-for-ai` 补充集群部署配置说明（哪些配置项组合可获得集群安全行为）。

## Conclusion

- 独立核实（2026-09-21，独立子 agent，task agent_565456cd）：11/11 关键声明 PASS，行号引用零偏差，A 项遗漏检查无实质性遗漏，报告可作为修正计划依据。
- 最终裁定：P0-1/P0-2/P1-1/P2 全部成立；修正方向采纳"改进建议"1-5，具体契约落 design 文档，执行落 plan 2274。
- 核实补强（并入修正范围）：
  - C 组补列 `JWKPublicKeyLocator`（nop-auth-sso，TTL 刷新的 IdP 公钥本地只读缓存——跨节点仅陈旧窗口，集群可接受，无需改动，文档归类用）；
  - 集群部署文档必须写明 `JwtAuthTokenProvider` 在 `encKey` 未配置时按 JVM 随机派生签名密钥——**集群部署必须显式配置 `nop.auth.jwt.enc-key`**（配置项问题，非代码缺陷）。
- Open Questions 裁定（采纳独立核实建议）：
  - Q1：限流独立配置 `nop.auth.rate-limit.store-type`（默认 `local` 保持现状行为，不隐式引入 nosql 依赖）；装配机制复用 MfaStoreProvider 的 collect-beans + fail-closed 模式。
  - Q2：拆接口——`IUserContextCache` 保留会话四方法，失败计数与验证码各自独立小接口；`IUserContextCache` 属跨模块公共 API，旧方法以委托过渡，不直接破坏签名。
  - Q3：原子递增定义为接口级原子方法（read-modify-write 语义）；local 实现内部消解锁，redis 实现用 `INosqlCounter`（`RedisMfaChallengeStore` INCRBY + PEXPIRE 先例）——纯 `ICache.compute` 默认实现跨网络不原子，不可直接承载。
- 被否决的方案：直接在 `LoginServiceImpl` 内嵌 Redis 限流——否决原因：无接口抽象，与 `MfaStoreProvider` 既有装配模式不一致，且不解决 `NopAuthUserBizModel` 侧重复。
- 后续工作：`ai-dev/design/auth/cluster-support-and-mfa-refactor.md`（契约）+ `ai-dev/plans/2274-nop-auth-cluster-support-and-mfa-split.md`（执行）。

## Open Questions

（已全部裁定，见 Conclusion；保留原始问题记录）

- [x] 限流组件与 MFA store 是否共用同一 `store-type` 配置，还是独立 `nop.auth.rate-limit.store-type`？→ 裁定：独立，默认 `local`。
- [x] `IUserContextCache` 接口是否需要拆分？→ 裁定：拆（会话/失败计数/验证码三关注点），旧接口委托过渡。
- [x] 原子递增由 `INosqlCounter` 承接还是接口扩展？→ 裁定：接口定义原子方法，`INosqlCounter` 为 redis 后端实现细节。

## Independent Verification Evidence

- Reviewer / Agent: 独立审计子 agent（task agent_565456cd-5c50-46ab-bb04-232fb4de48c5），2026-09-21。
- 结果：声明 1-11 全部 PASS（逐条附 file:line 证据）；行号偏差检查零偏差；遗漏检查（nop-auth 全部 10 子模块 + nop-biz-auth-core 字段级可变状态扫描）确认无实质遗漏，仅 2 条补强建议（已并入 Conclusion）。
- 完整审计输出摘要已包含于本节；逐条原始证据见审计会话记录。

## References

- `docs-for-ai/02-core-guides/service-layer.md`（BizModel/Processor 拆分指引）
- `docs-for-ai/03-runbooks/choose-entity-bizmodel-processor.md`
- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java`
- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java`
- `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/AbstractUserContextCache.java`
- `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/LocalUserContextCache.java`
- `nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/nop/auth/beans/auth-core-defaults.beans.xml`
- `nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml`
- `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/ICache.java`
- `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/cache/NosqlCache.java`
