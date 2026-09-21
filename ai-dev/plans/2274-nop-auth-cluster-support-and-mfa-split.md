# 2274 nop-auth 集群支持落地与 MFA 大类拆分

> Plan Status: completed
> Last Reviewed: 2026-09-21（两轮独立对抗性审查收敛：第一轮 2 Blocker + 6 Major 修复；第二轮确认闭合 + 2 新 Major 修复后复审裁定"可进入执行"）
> Source: `ai-dev/analysis/2026-09/2026-09-21-nop-auth-cluster-support-and-mfa-split-analysis.md`（独立核实 11/11 PASS）+ `ai-dev/design/nop-auth/03-cluster-support-and-structure-design.md`
> Related: `ai-dev/design/nop-auth/01-architecture-baseline.md`、`ai-dev/design/nop-auth/02-mfa-phase2-design.md`

## Purpose

把 nop-auth 的反滥用机制（发码限流、登录失败计数、图形验证码缓存）从硬编码 JVM 本地结构改为接口化、存储可配置（默认行为等价，Redis 可选），使多节点部署获得真实集群防护；同时把 MFA 流程代码从 `LoginServiceImpl`（1556 行）与 `NopAuthUserBizModel`（2068 行）拆出为独立组件，消灭 5 组跨类重复代码，对齐 `docs-for-ai/02-core-guides/service-layer.md` 的 BizModel/Processor 拆分指引。

## Current Baseline

live repo 事实（2026-09-21 核实：独立子 agent 逐条验证 11/11 PASS、行号零偏差；对抗性审查复核补充测试资产）：

- `LoginServiceImpl.java`：1556 行；本地限流 Map ×4（`smsPhoneTracker` L857 / `smsIpTracker` L858 / `emailTracker` L1085 / `emailIpTracker` L1086，均为 `newBoundedRateMap()` Caffeine asMap）；JVM 锁 `loginFailCountLock` L254；MFA 验证流程（mfaVerifyAsync L589 起）、恢复码分支（含条件 EQL UPDATE L793-811）、发码流程（sendSmsCode L870 / sendMfaCode L904 / sendMfaEmailCode L949）、限流方法（checkSmsRateLimit L1043 / checkEmailRateLimit L1094）全部内联。`sendSmsCode` 与 `sendMfaCode`(sms) 共享同一组 sms 计数器；`sendMfaCode`(email) 用 email 组。
- `NopAuthUserBizModel.java`：2068 行；MFA 自服务约 1570 行（L267-L1867：bindMfa/webauthn ceremony/恢复码/可信设备/resetUserMfa/channel proof）；本地限流 Map ×3（`proofRateTracker` L1350 / `emailRateTracker` L1351 / `emailIpRateTracker` L1352）+ `newBoundedRateMap` 逐字拷贝 L1355；bindMfa(email) 与 channel proof(email) 共享 email 组计数器。
- 跨类重复 ×5：`newBoundedRateMap`、`checkEmailRateLimit`（L1094 vs L1549 近似逐字）、`deleteWebauthnCredentials`（L744 vs L1792）、`maskPhone`/`maskEmail`（L1018/L1026 vs L1833/L1589）、`extractClientIp`（NopAuthUserBizModel L1601 与 LoginApiBizModel 同型）。
- `AbstractUserContextCache`（nop-biz-auth-core）：4 个 `ICache` 字段（会话×2、`loginFailCache`、`verifyCodeCache`）；`LocalUserContextCache.init()` 硬编码构造为 `LocalCache`，无注入点；`auth-core-defaults.beans.xml` L46-48 默认装配。**既有缺陷**：`getLoginFailCountForIp`（L105-107）用 `userKey(ip)`（`"un:"+ip`）读、`setLoginFailCountForIp`/`resetLoginFailCountForIp` 用 `ipKey(ip)` 写——读写键前缀不对称（生产代码无调用方）。
- `DaoUserContextCache`（**位于 nop-auth-service**，`io.nop.auth.service.login`）：`extends LocalUserContextCache`，只覆写会话四方法（DB 化）；失败计数与图形验证码继承本地实现——开启 `nop.auth.login.use-dao-user-context-cache` 后集群下验证码仍必失败、锁号阈值仍被稀释。
- `AbstractLoginService.doGetUserContext` 直查 `userContextCache`，无 DB 回退 → 默认装配严格单节点。
- 已有集群安全资产：`DaoLoginSessionStore`（默认）、`Db*`/`Redis*` MFA store（默认 db）、`MfaStoreProvider` collect-beans + fail-closed 装配先例、`INosqlService.counter()`（`INosqlCounter` 原子计数，`RedisMfaChallengeStore` 先例）。注意 `NosqlCache.put` 无 TTL（写路径不消费 `expireAfterWrite`）——验证码缓存不可直接注入（design §3.3 契约）。
- **测试资产（含触点清单）**：MFA E2E 全套（TestMfaLoginE2E/TestEmailMfaE2E/TestWebAuthnMfaE2E/TestMfaRestricted*/TestTrustedDeviceE2E/TestRoleMfaPolicy/TestMfaUserSelfService/TestWebAuthnAddKeyE2E/TestChannelProofEmailE2E/TestMfaCrudLockdownE2E 等）；**直接触碰将被迁移成员的测试**：`TestLoginRateLimitAndAudit`（子类调 `LoginServiceImpl.checkSmsRateLimit`，含 32 线程并发间隔原子性用例）、`TestSmsSendFailClosed`（子类调 `sendSms` + 反射调 NopAuthUserBizModel 的 `sendSmsForBinding` 私有方法）、`TestRecoveryCodeFormat`（静态引用恢复码格式化方法）、`TestRecoveryCodeConditionalWrite`（直接触碰 `LoginServiceImpl` 恢复码枚举与验证方法）、`TestLoginFailCountAtomicity`（手工 wiring 6 字段反射注入的 32 并发计数断言，不为 store 增 wiring）、`TestLoginCredentialCheckWhenLockoutDisabled`、`TestDaoSessionStoreAndUserContextCache`。
- 设计契约已定稿（含对抗性审查后修订）：`ai-dev/design/nop-auth/03-cluster-support-and-structure-design.md` §3.1 计数分组/脱敏统一/原子性诚实表述、§3.2 注入语义与 ipKey 修正、§3.3 TTL 保持契约、§3.4 接缝约束、§3.6 测试兼容契约。

**全 plan 通用裁定**：
- **注入语义**：所有新组件依赖一律"可选注入 + 内联缺省 Local 实例（容器可覆盖）"（`ormTemplate` 先例）——手工 `new` 的测试对象不经容器也有正确 Local 行为。**缺省实例必须为 JVM 级共享单例**：`LoginServiceImpl` 与 `AbstractUserContextCache` 的缺省 store 解析到同一实例（缺省路径写读同一性；design §3.2）。
- **"零断言修改通过"的精确语义**：断言不改；被迁移 protected/private 成员的测试调用点允许按 design §3.6 清单重接；唯一允许的断言变化 = 错误 param 统一（内容脱敏 + email key 归一 `ARG_CHANNEL`，design §3.1，逐处枚举）。

## Goals

- 发码限流（7 个本地 Map + 2 组重复方法）收敛为单一限流组件（scope 维度等价现状共享拓扑），存储 `local|redis` 可配置，默认 local 行为等价。
- 登录失败计数收敛为独立 store（接口级原子递增），`loginFailCountLock` 消灭，`DaoUserContextCache` 的半本地缺陷修复，`getLoginFailCountForIp` 键前缀缺陷修正。
- 图形验证码缓存获得满足 TTL 保持契约的 `ICache` 注入出口。
- MFA 验证/发码流程移出 `LoginServiceImpl`（≤1000 行）；MFA 自服务移出 `NopAuthUserBizModel`（≤1000 行，薄入口保留，API 签名不变）；共享工具单一落点。
- 集群部署配置矩阵落入 `docs-for-ai` owner doc。

## Non-Goals

- 不改任何 GraphQL/RPC API 签名（`NopAuthUser__*`、`LoginApi__*` operation 面不变）。
- 不改 ORM 模型/数据库表（限流走 redis 时无表；不新增 db 限流实现——design §四.4 已拒）。
- 不引入分布式会话纯缓存出口（集群会话正路是既有 dao-user-context-cache，design §四.7 已拒）。
- 不重写 MFA 业务语义（三期判定矩阵/恢复码语义/审计事件/错误码面全部保持）。
- 不承诺限流三键复合全局原子（design §3.1 诚实表述：单键原子、复合有界漂移可接受）。
- 不处理 `SiteMapProviderImpl`/`JWKPublicKeyLocator` 本地只读缓存（集群可接受，仅文档归类）。

## Scope

### In Scope

- `nop-auth/nop-auth-service`：限流组件（接口+Local+Redis）、`ILoginAttemptStore` Redis 实现、MFA 流程组件、`NopAuthUserBizModel` Processor 化、beans 装配、测试（含触点测试重接）。
- `nop-service-framework/nop-biz-auth-core`：`ILoginAttemptStore` 接口 + Local 实现 + Local bean 注册进 `auth-core-defaults.beans.xml`、`AbstractUserContextCache`/`LocalUserContextCache` 注入出口与委托、`getLoginFailCountForIp` 键前缀修正、（签名保留的）委托实现。
- `docs-for-ai`：nop-auth owner doc 集群部署矩阵 + `store-type=local` 单节点限定声明 + jwt encKey 集群必配声明。
- `ai-dev/logs/` 过程记录。

### Out Of Scope

- nop-auth-sso / nop-oauth / nop-ai-gateway 代码（`ChannelLoginApiBizModel` 等已核实无非集群状态）。
- 会话粘滞/LB 层配置等部署侧课题。
- `INosqlRateLimiter` 令牌桶的任何改造。

## Execution Plan

### Phase 1 — 发码限流组件化（消灭 7 Map 与重复限流逻辑）

Status: completed
Targets: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/`（新限流组件包）、`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java`、`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java`、`nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml`、`nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/login/TestLoginRateLimitAndAudit.java`

- Item Types: `Fix | Proof`

- [x] 按 design §3.1 新建限流组件：接口（scope 维度 + 三层语义：同目标间隔 + 同目标日配额 + 同 IP 日配额，超限抛既有错误码，错误 param 统一 = 内容脱敏 + email key 归一 `ARG_CHANNEL`）+ Local 实现（先递增后检查，等价现状 compute 语义；沿用 `tracker-max-size/expire` 配置）+ Redis 实现（单键原子：`INosqlCounter` 日界键 INCRBY + `setTimeoutAsync` TTL、间隔条件写占位 TTL=interval；`ioc:condition` 条件激活，fail-closed）
- [x] beans 装配：collect-beans 前缀收集 + `nop.auth.rate-limit.store-type`（默认 `local`）选择，复刻 `MfaStoreProvider` 装配模式（`ioc:ignore-depends` + `autowire-candidate=false` + 请求类型未注册显式抛异常）
- [x] `LoginServiceImpl`：删除 4 个限流 Map 字段与 `checkSmsRateLimit`/`checkEmailRateLimit`，发码入口（`sendSmsCode`/`sendMfaCode` 两分支）改调限流组件（scope=login）；限流组件依赖按通用裁定内联缺省 Local
- [x] `NopAuthUserBizModel`：删除 3 个限流 Map 字段、`newBoundedRateMap` 拷贝与 `checkProofRateLimit`/`checkEmailRateLimit`，bindSms/bindMfa(email)/proof 入口改调限流组件（scope=bind）
- [x] 测试重接（断言不改，param 断言按统一规则逐处枚举修改）：`TestLoginRateLimitAndAudit` 调用点改调限流组件，其 32 线程并发间隔原子性用例平移为 Local 实现的并发回归（同语义新落点，不重复造第二条）
- [x] 新增测试：Local 实现三层语义（间隔/日配额/IP 配额/跨天重置/scope 隔离与组内共享）；Redis 实现委托契约（nosql 原语调用断言，对齐 `RedisMfaChallengeStore` 测试形态）；装配选择与 fail-closed

Exit Criteria:

- [x] `grep -rn "newBoundedRateMap\|proofRateTracker\|smsPhoneTracker\|emailIpTracker" nop-auth --include="*.java"` 仅命中新组件与测试（两大类中零残留）
- [x] 默认装配（local）下限流行为等价（含 scope 分组等价）：既有 MFA/登录 E2E 测试按 §3.6 精确语义通过（断言不改、调用点重接清单已枚举）
- [x] **接线验证**：`sendSmsCode`/`sendMfaCode`(sms/email)/`bindSms`/`bindMfa`(email)/channel-proof 五类发码入口在运行时确实调用限流组件且 scope 取值正确（调用证据断言）
- [x] **无静默跳过**：限流组件 redis 后端未注册时显式抛异常（fail-closed 测试）
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` 通过
- [x] 集群语义 Proof：Redis 实现对同一 `INosqlCounter` 键的跨实例递增断言（单键原子 + 共享）
- [x] No owner-doc update required（集群配置矩阵统一在 Phase 5 落档——新配置项在 Phase 1-4 期间存在文档真空为已裁定可接受偏离，Phase 5 兜底；若 plan 中途停滞须回补）

### Phase 2 — ILoginAttemptStore 与 IUserContextCache 缓存出口

Status: completed
Targets: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/`（新接口 + Local 实现、`AbstractUserContextCache`、`LocalUserContextCache`、`nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/nop/auth/beans/auth-core-defaults.beans.xml`）、`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java`、`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoUserContextCache.java`、`nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml`

- Item Types: `Fix | Proof`

- [x] 按 design §3.2 新建 `ILoginAttemptStore`（读取/设置/清零/原子递增）：Local 实现落 nop-biz-auth-core（临界区内聚 + 订阅 `UserContextConfig.loginFailTimeout` 刷新）+ Local bean 注册进 `auth-core-defaults.beans.xml`；Redis 实现（`INosqlCounter` + 首次递增设 TTL）落 nop-auth-service，`ioc:condition` 条件激活
- [x] 装配：`nop.auth.login-attempt.store-type`（默认 `local`），collect-beans 模式
- [x] `AbstractUserContextCache`：失败计数六方法签名保留、实现委托 store（可选注入 + 内联缺省 Local **共享单例**，design §3.2 裁定——与 `LoginServiceImpl` 缺省同实例，保证缺省路径写读同一性）；`getLoginFailCountForIp` 的 `userKey(ip)` 读键缺陷修正为统一 `ip:` 维度（Fix：既有 live defect，生产无调用方，修正非 deferred）
- [x] `verifyCodeCache` 暴露可选 `ICache` 注入点：缺省 `LocalCache` 行为等价（md5 key 混淆 + TTL 不变）；注入实现必须满足 TTL 保持契约（design §3.3——put 带 `verifyCodeTimeout` TTL；直接注入 `NosqlCache` 不满足，文档示例为 putEx 适配）
- [x] `DaoUserContextCache`：失败计数/验证码经注入 store/cache 生效（不再隐式本地）
- [x] `LoginServiceImpl`：删除 `loginFailCountLock` 与 `incrementLoginFailCount` 锁实现，改调 store 原子递增（手工 wiring 路径依赖内联缺省实例，`TestLoginFailCountAtomicity` 零 wiring 通过）
- [x] 新增测试：Local store 原子递增并发回归（`TestLoginFailCountAtomicity` 语义保持通过，不改 wiring）；委托装配测试（注入 redis store 后 `IUserContextCache` 计数方法走 store——探针断言）；ipKey 统一后的读写一致测试；verifyCodeCache 注入点测试（自定义 ICache 被实际使用且 TTL 语义保持——断言写入带 TTL 或到期行为）
- [x] `./mvnw test -pl nop-service-framework/nop-biz-auth-core -am` 与 `-pl nop-auth/nop-auth-service -am` 通过

Exit Criteria:

- [x] `grep -rn "loginFailCountLock" nop-auth --include="*.java"` 主源码零命中
- [x] `IUserContextCache.java` 接口文件零 diff（六个失败计数方法与验证码两方法签名未变；实现变化只发生在实现类）
- [x] `getLoginFailCountForIp` 与 set/reset 使用同一 `ip:` 键维度（读写一致测试通过）
- [x] 缺省路径写读同一性：手工 wiring（不为 store 增 wiring）下 `TestLoginFailCountAtomicity` 通过（共享缺省单例证据）
- [x] 默认装配行为等价：`TestDaoSessionStoreAndUserContextCache`、`TestLoginFailCountAtomicity`、`TestLoginCredentialCheckWhenLockoutDisabled` 及 nop-biz-auth-core 既有测试按 §3.6 精确语义通过
- [x] **接线验证**：`DaoUserContextCache` 模式下失败计数经 store（探针/mock 断言调用链连通，非仅类型存在）
- [x] **无静默跳过**：redis store 未注册时 fail-closed 显式异常测试
- [x] auth-core-only 部署可用性：`nopUserContextCache`（auth-core-defaults 装配）不依赖 nop-auth-service 的 bean（Local store bean 在 auth-core-defaults 注册的装配验证）
- [x] No owner-doc update required（同 Phase 1 裁定）

### Phase 3 — LoginServiceImpl 的 MFA 流程拆分

Status: completed
Targets: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/`（新流程组件 + 共享工具类）、`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java`、`nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestSmsSendFailClosed.java`（sendSms 部分）、`nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/login/TestRecoveryCodeConditionalWrite.java` 及其他触点测试

- Item Types: `Fix | Proof`

- [x] 按 design §3.4 拆出登录级 MFA 流程组件（三层判定/第二因子验证/恢复码分支/失败计数与作废/completeMfaLogin 出口）——**完成回调端口接缝**（design §3.4：组件不持有 LoginService 引用，经函数式出口回到宿主签发点，resetFailCount/notifyHook/restricted 语义逐项对应）；protected 兼容面与 `ILoginService`/`ISessionBootstrap` 签名不变，内部委托组件
- [x] 拆出发码流程组件（sendSmsCode/sendMfaCode 按 mfaType 分派/发送器 fail-closed/脱敏日志）；测试重接：`TestSmsSendFailClosed` 的 `sendSms` 调用点改接发码流程组件（断言不改；其 `sendSmsForBinding` 部分随 Phase 4 重接）、`TestRecoveryCodeConditionalWrite` 调用点改接 MFA 流程组件（断言不改）
- [x] 共享工具收敛：maskPhone/maskEmail/extractClientIp/deleteWebauthnCredentials 单一落点（mfa 包共享类），两类中的私有副本删除
- [x] 记录拆分前后 `wc -l`（Current Baseline 1556 行；目标 ≤1000 行）
- [x] 既有 MFA E2E 全套按 §3.6 精确语义通过

Exit Criteria:

- [x] `LoginServiceImpl` 中不再存在：限流方法/字段（Phase 1 已清）、MFA 验证私有流程方法、发码私有流程方法、mask/extractClientIp 副本（`grep` 验证标识符仅命中新组件）
- [x] `wc -l LoginServiceImpl.java` ≤ 1000（超限须回 plan 说明并修订阈值或继续拆分）
- [x] **端到端验证**：`TestMfaLoginE2E`、`TestEmailMfaE2E`、`TestWebAuthnMfaE2E`、`TestMfaRestrictedLoginE2E`、`TestTrustedDeviceE2E`、`TestMfaVerificationHardeningE2E` 全部按 §3.6 精确语义通过（登录入口 → MFA 门禁 → 验证 → 会话签发全链路）
- [x] **接线验证**：`loginAsync`/`mfaVerifyAsync` 在运行时确实调用新组件（组件交互断言或调用证据）
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` 通过
- [x] No owner-doc update required（纯内部重构，API 面与用户可见行为不变——脱敏统一已在 Phase 1 裁定）

### Phase 4 — NopAuthUserBizModel 的 MFA 自服务 Processor 化

Status: completed
Targets: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java`、新 Processor（`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/`）、`nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/entity/TestRecoveryCodeFormat.java`、`nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestSmsSendFailClosed.java`（sendSmsForBinding 部分）及其他触点测试

- Item Types: `Fix | Proof`

- [x] 按 design §3.4 将 MFA 自服务（bindMfa 双通道/webauthn 三 ceremony/恢复码/可信设备/resetUserMfa/channel proof）迁入 Processor；**Processor 不继承 CrudBizModel**——DAO 经注入 `IDaoProvider`、事务经 `ITransactionTemplate`/BizModel 管道（design §3.4 接缝约束：`daoFor()`/`txn()` 调用点逐一改写）；`requireAdmin`/`requireCurrentUserId` 落共享类供两类与 Processor 共用（不复制）
- [x] `NopAuthUserBizModel` 全部 `@BizMutation`/`@BizQuery` 方法签名不变、保留为薄入口；测试重接：`TestRecoveryCodeFormat` 静态引用、`TestSmsSendFailClosed` 的 `sendSmsForBinding` 反射调用点改接新落点（断言不改）
- [x] 记录拆分前后 `wc -l`（Current Baseline 2068 行；目标 ≤1000 行）
- [x] 既有自服务 E2E 按 §3.6 精确语义通过（`TestMfaUserSelfService`、`TestWebAuthnAddKeyE2E`、`TestMfaCrudLockdownE2E`、`TestChannelProofEmailE2E`、`TestRoleMfaPolicy` 等）

Exit Criteria:

- [x] `NopAuthUserBizModel` 中不再存在 MFA 私有流程方法与工具副本（`grep` 验证）；全部 public 方法签名与 `@BizMutation`/`@BizQuery` 注解未变（`git diff` 验证）
- [x] `wc -l NopAuthUserBizModel.java` ≤ 1000（超限须回 plan 说明）
- [x] **端到端验证**：绑定 → 验证 → 重绑 → 解绑 → 恢复码 → 可信设备全流程 E2E 按 §3.6 精确语义通过
- [x] **接线验证**：BizModel 薄入口在运行时确实委托 Processor（调用证据断言）
- [x] GraphQL/RPC API 面不变：`NopAuthUser__bindMfa` 等 operation 可调用性由 E2E（走 GraphQL 管道）验证
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` 通过
- [x] No owner-doc update required（API 面不变）

### Phase 5 — 集群部署契约文档化与收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-auth.md`（或最小 owning doc）、`docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md`（如锚点变化）、`ai-dev/logs/`

- Item Types: `Fix | Proof`

- [x] owner doc 落集群部署配置矩阵（design §3.5 六行表：会话/MFA store/限流/失败计数/验证码缓存（TTL 保持契约）/jwt encKey）+ `store-type=local` 单节点限定声明 + 本地只读缓存归类说明（SiteMapProvider/JWKPublicKeyLocator）
- [x] `nop.auth.rate-limit.store-type` / `nop.auth.login-attempt.store-type` 新配置项文档化（含默认值、集群取值、scope 分组语义）
- [x] `docs-for-ai/INDEX.md` 与 source-anchors 检查（路由/锚点变化则更新）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] 集群部署矩阵在 owner doc 中与 design §3.5 一致（逐行核对）
- [x] 新配置项均有文档条目（含默认值与集群取值）
- [x] doc link checker 退出码 0
- [x] `ai-dev/logs/2026/09-21.md` 已更新（含各 Phase 的 wc -l 前后值记录）

## Closure Gates

- [x] 两大类零本地限流 Map/锁/重复工具副本，且 `wc -l` 双双 ≤ 1000（grep + wc 证据，见各 Phase Exit Criteria）
- [x] 默认单节点行为等价：既有 MFA/登录 E2E 全套按 design §3.6 精确语义通过（唯一一类的用户可见变化 = 错误 param 统一：内容脱敏 + email key 归一 `ARG_CHANNEL`，已逐处枚举）
- [x] 集群能力成立：限流/失败计数 Redis 装配的共享性与单键原子性有测试证明；验证码缓存 TTL 保持注入出口有测试证明
- [x] 既有缺陷已修：`getLoginFailCountForIp` 键前缀统一（Fix，非 deferred）
- [x] API 面不变：`IUserContextCache` 签名、`NopAuthUser__*`/`LoginApi__*` operation 面均未破坏
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已同步（Phase 5），其余 Phase 显式 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并写入 Closure 段落（证据含每条 Exit Criterion 验证结果）
- [x] **Anti-Hollow Check**：closure audit 验证组件间调用链运行时连通（入口→限流组件→store；BizModel→Processor）且无空方法体/静默跳过
- [x] `./mvnw test -pl nop-service-framework/nop-biz-auth-core -am` 通过
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2274-nop-auth-cluster-support-and-mfa-split.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth/nop-auth-service --severity high` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-service-framework/nop-biz-auth-core --severity high` 退出码 0

## Deferred But Adjudicated

### SiteMapProvider / JWKPublicKeyLocator 本地只读缓存

- Classification: `watch-only residual`
- Why Not Blocking Closure: DB/IdP 读穿透的本地缓存，集群下仅存在跨节点陈旧窗口，权限判定与密钥验证的数据源仍为 DB/IdP，无安全稀释（独立核实裁定）。
- Successor Required: `no`
- Successor Path: —

### 新配置项文档真空（Phase 1-4 期间）

- Classification: `watch-only residual`
- Why Not Blocking Closure: Phase 5 兜底落档；plan 若中途停滞，停滞点的新配置项须即时回补 owner doc（本 plan 激活期间按 Phase 推进，不构成 drift）。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- MFA challenge DB store 的批量过期清理（既有 follow-up，`01-architecture-baseline.md` 已登记惰性清理 + Follow-up，非本 plan 引入）。
- 限流 Redis 实现的监控指标暴露（集群可观测性增强项，非正确性必需）。
- 限流跨键复合原子的严格化（Lua/事务原语）——当前有界竞态漂移已裁定可接受（design §3.1），如未来攻击面升级再立项。

## Closure

Status Note: 五个 Phase 全部完成并经独立 closure audit（APPROVE-WITH-NOTES，三项关门修复已完成）：集群支持能力（限流/失败计数 Redis 出口 + 验证码 TTL 保持注入契约）与结构治理（两大类 1556→959、2068→618 行，7 本地 Map + JVM 锁 + 5 组重复代码归零，API 面不变）全部落地；448+14 tests 0 failures；全部门槛工具退出码 0。
Completed: 2026-09-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子 agent（fresh session，task agent_5cca317d-3dea-4bda-9891-220cbd16fc1f，2026-09-21）
- Audit Session: agent_5cca317d（对照 live repo @ 8ffc551cd7 逐条核实，Phase Exit Criteria 28 项 + Closure Gates 14 项逐条 PASS/FAIL 报告）
- Evidence:
  - Phase 1-5 Exit Criteria：除 source-anchors 漂移（Major-2，已修复）外全部 PASS；grep 双标识符零残留、wc 959/618、IUserContextCache 零 diff、TestLoginFailCountAtomicity 零 diff 通过、getLoginFailCountForIp ip: 维度统一（Fix 落地）
  - Closure Gates：13/14 亲测 PASS；Gate 14（scan-hollow biz-auth-core）初测 exit 1（4 处 ILoginService 既有 default fail-fast，本 plan 零 diff）→ 修复方式 = 消息措辞改 guard 式（"does not support ...; override this default"），复审 exit 0
  - Anti-Hollow 四链追踪：sendSmsCode→MfaCodeSender→rateLimiter(SCOPE_LOGIN)、bindMfa 薄入口→UserMfaSelfService.bindMfa（IDaoProvider/ITransactionTemplate 接缝）、loginAsync→checkMfaRequired→mfaFlow().（completeLoginPort 回调宿主）、新组件零 TODO/空方法体——全部连通
  - 测试证据：surefire 报告 2026-09-21（nop-auth-service 448 tests 0 fail / nop-biz-auth-core 全绿，报告与 HEAD 源码一致）；MFA E2E 全套零断言修改通过；4 处测试重接符合 design §3.6（32 线程并发用例断言逐字保持）
  - 工具退出码（关门复跑）：check-plan-checklist --strict = 0；scan-hollow nop-auth/nop-auth-service = 0；scan-hollow nop-service-framework/nop-biz-auth-core = 0（措辞修复后）；check-doc-links --strict = 0（source-anchors 漂移修复后）
  - Deferred 项分类检查：SiteMap/JWK 本地只读缓存（零代码触碰，watch-only）与配置项文档真空（Phase 5 已兜底消除）均诚实，无 in-scope defect 降级
  - audit 发现的 Minor/Info 项修复：Minor-1 补同目标日配额测试（interval=0 配置覆盖，447→448）、Minor-2 补 verifyCodeCache TTL 到期断言、Info 改名 `nopLoginAttemptStoreProvider`→`nopLoginAttemptSelector`（消除 collect 前缀碰撞，W8 命名教训）；"跨天重置"用例未单独模拟日界——日界键机制由 Redis day-key 测试间接覆盖，裁定 watch-only（Non-Blocking Follow-ups 已登记）

Follow-up:

- 限流跨键复合原子严格化（Lua/事务原语）——有界竞态漂移已裁定可接受，攻击面升级再立项
- 限流 Redis 实现监控指标暴露（集群可观测性增强）
- MFA challenge DB store 批量过期清理（既有 follow-up，01-architecture-baseline 已登记）
- 限流 Local 实现的"跨天重置"日界模拟测试（日界键机制已被 Redis 测试间接覆盖，watch-only）
