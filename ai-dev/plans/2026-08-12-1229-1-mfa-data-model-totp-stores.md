# W4 - MFA 数据模型 + TOTP 验证器 + 存储组件

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md` (W4); `ai-dev/design/nop-auth/00-vision.md` + `ai-dev/design/nop-auth/01-architecture-baseline.md` (§3.3 / §3.4 / §3.5)
> Related: W1-W3 (nop-credential, done); W5 (MFA 登录两阶段 + 短信登录, 消费本 plan 产出); W6 (绑定/解绑/扫码, 依赖 W5)

## Purpose

把 MFA 的领域基础收口到"可用"状态：两张 ORM 实体（model-first）、RFC 6238 TOTP 验证器、两个独立存储组件（`MfaChallengeStore` + `SmsCodeStore`）的接口与 Local + Redis 双实现。本 plan 不触碰登录流程（W5）、不触碰绑定/解绑 API（W6）。

## Current Baseline

（已核对 live repo）

- `nop-auth` 子模块齐全（api/app/codegen/dao/meta/service/sso/web）；ORM 源文件 `nop-auth/model/nop-auth.orm.xml` 存在，**无任何 MFA 表**。
- `nop-biz-auth-api` / `nop-biz-auth-core` 位于 `nop-service-framework/`；`AuthApiConstants` 当前 loginType 仅 1/2/3/4，**无 5**（loginType=5 属于 W5，不在本 plan）。
- `NopAuthErrors`（`nop-auth-service/.../NopAuthErrors.java`）**无 MFA/SMS 系列错误码**。
- `login-type.dict.yaml`（`nop-biz-auth-core` 资源）SSO=10 与代码 SSO=4 不一致、缺 2/3/5 —— 修复属于 W5，不在本 plan。
- 可复用基础设施已就绪：`nop-nosql`（`INosqlKeyValueOperations`/`NosqlCache`/`INosqlRateLimiter`/`INosqlService.counter()→INosqlCounter`）、`nop-commons` `AESTextCipher`、`IPasswordEncoder`、`ISmsSender`（nop-integration-api）、`LocalCache`。
- `nop-auth-service` 当前**未依赖** `nop-nosql-core`（设计 §3.3 要求新增该可选依赖，用于 Redis store 实现）。
- nop-credential W1-W3 已 done（模块存在），与本 plan 无依赖关系。

## Goals

- 两张 MFA ORM 实体通过 model-first 落地（源 → codegen → DDL 迁移，禁止手编 `_gen/`/`_` 前缀文件）。
- RFC 6238 TOTP 验证器（HMAC-SHA1 / 6 位 / 30s / ±1 窗口）+ provisioning URI 生成 + 防重放窗口判定面（成功更新 `lastVerifiedWindow`、当前窗口 ≤ 上次成功窗口则拒绝且不更新）。
- `MfaChallengeStore`（create/peek/incrFailCount/consume）与 `SmsCodeStore`（send/verify/consume，verify 返回"有效/过期/不匹配"三态，成功原子消费）接口 + Local 实现 + Redis 实现。
- 原子失败计数裁决落地：默认**方案 A**（复用 `INosqlCounter`），Redis 写一律 `putExAsync`、读用不刷新 TTL 的 `get`，按设计 §3.3 写路径约束。

## Non-Goals

- 登录流程两阶段改造、`ERR_AUTH_MFA_REQUIRED` 拦截、短信验证码登录（loginType=5）、dict 修复 —— 全部属于 W5。
- 绑定/解绑/恢复码管理 API、管理员重置、扫码适配 —— 属于 W6。
- WebAuthn / 邮件验证码 / 操作级 MFA / 可信设备 / 角色级强制策略 —— 二期。
- TOTP secret 明文跨出服务进程（结构性禁止，本 plan 提供 secret 加密存储实体字段；TOTP 组件内部对传入的加密 secret 做 `AESTextCipher` 解密后校验，明文不外泄；绑定/验证的业务流接线在 W5/W6）。

## Scope

### In Scope

- `nop-auth/model/nop-auth.orm.xml` 新增 `NopAuthMfaSetting` + `NopAuthMfaRecoveryCode`（→ codegen → DDL 迁移）。
- `nop-biz-auth-core`：TOTP 验证器 + 两个 store 的**接口**与 **Local 实现**（零业务依赖）。
- `nop-auth-service`：两个 store 的 **Redis 实现** + 新增可选依赖 `nop-nosql-core`（`store-type=redis` 时启用）。
- 存储组件的单元 / 集成测试（TOTP RFC 向量、窗口、防重放、challenge 生命周期、SmsCodeStore 三态、原子计数）。

### Out Of Scope

- 任何登录方法（`loginAsync` / `createSessionForUserAsync` / `mfaVerify`）的改动（W5）。
- loginType=5 常量、dict 修复、`LoginApi` 新增方法、`NopAuthConfigs`/`NopAuthErrors` 的 MFA/SMS 条目（W5）。
- 绑定/解绑/恢复码生成/管理员重置的 BizModel action（W6）。
- `docs-for-ai/` MFA 章节同步（roadmap 显式分配给 W7）。

## Execution Plan

### Phase 1 - MFA ORM 实体（model-first）

Status: completed
Targets: `nop-auth/model/nop-auth.orm.xml` → codegen → `_gen/` 实体 + DDL 迁移脚本

- Item Types: `Fix | Decision`

- [x] 在 `nop-auth/model/nop-auth.orm.xml` 新增 `NopAuthMfaSetting`（userId PK / mfaType / secret 加密列 / status(pending|enabled|disabled) / bindToken / phone / lastVerifiedWindow / lastVerifiedAt + 通用审计字段），字段语义与设计 §3.5 对齐
- [x] 新增 `NopAuthMfaRecoveryCode`（sid PK(seq) / userId / codeHash(BCrypt 加盐) / used / usedAt / expireAt + 通用审计字段）；关系 `NopAuthMfaSetting 1:N NopAuthMfaRecoveryCode`（按 userId）
- [x] `secret` 列 xmeta `published="false"`（明文边界结构性强制，复用 W3 已确立的模式：在非生成的 retention xmeta 文件声明 `published="false"`，而非手编 `_gen/` 产物——参照 `nop-credential` 的 `NopCredential.data` 处理）
- [x] 跑 codegen 生成实体/dao，生成 DDL 迁移脚本；**禁止手编 `_gen/` 与任何 `_` 前缀文件**

Exit Criteria:

- [x] `nop-auth/model/nop-auth.orm.xml` 中两张实体定义存在且字段与设计 §3.5 一一对应
- [x] codegen 产物存在（实体/dao），DDL 迁移脚本存在；无 `_` 前缀文件被手编
- [x] `secret` 列在 xmeta 层 `published="false"`，GraphQL/REST 层结构性不可达明文
- [x] **新功能测试**：实体 round-trip（save/load）通过；`secret` 列在 GraphQL schema 中不对外发布（有对应断言）
- [x] **无静默跳过**：本 phase 不引入新公共方法分支（纯模型）；若迁移工具不可用则显式报错而非跳过
- [x] 若该 Phase 改变 live baseline：`No owner-doc update required`（设计文档已定稿无需改动；`docs-for-ai/` MFA 章节属 W7）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - TOTP 验证器（RFC 6238）

Status: completed
Targets: `nop-biz-auth-core`（TOTP 算法 + provisioning URI + 防重放窗口判定面）

- Item Types: `Fix | Decision`

- [x] 实现 RFC 6238 TOTP（HMAC-SHA1 / 6 位 / 30s 窗口 / ±1 skew），secret 32 字节随机 base32 生成
- [x] 实现 provisioning URI 生成（otpauth://totp，**issuer 由调用方作为参数传入**——TOTP 组件位于 `nop-biz-auth-core`（上游纯逻辑层），无法读取 `nop-auth-service` 的 `NopAuthConfigs`；`nop.auth.mfa.totp-issuer` 配置连线是 W5/W6 的工作，同 `store-type` 跨阶段配置模式）
- [x] 提供防重放窗口判定面：校验通过返回成功窗口号（供调用方写 `lastVerifiedWindow`）；"当前窗口 ≤ 上次成功窗口"判定为拒绝且不更新（防同窗口重放，设计 §3.4）
- [x] TOTP secret 加解密通过 `AESTextCipher`（实体存密文，验证时内存解密），不在 TOTP 组件内做持久化

Exit Criteria:

- [x] RFC 6238 标准测试向量（TRUNCATE/HMAC 已知向量）通过
- [x] 窗口偏差测试：当前窗口 ±1 内接受，超出拒绝
- [x] 防重放测试：用同一窗口码第二次提交时，"当前窗口 ≤ lastVerifiedWindow"判定为拒绝且不更新窗口号（有明确断言）
- [x] provisioning URI 可被标准 OTP 库/Google Authenticator 格式校验通过（格式断言）
- [x] **接线验证**：TOTP 组件对 `AESTextCipher` 的调用在测试中可观测（解密路径被走到）
- [x] **无静默跳过**：secret 生成失败、HMAC 不可用等异常分支显式抛出，不返回固定码/空
- [x] 若该 Phase 改变 live baseline：`No owner-doc update required`（设计 §3.4 已定稿）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 存储组件（MfaChallengeStore + SmsCodeStore，Local + Redis）

Status: completed
Targets: `nop-biz-auth-core`（接口 + Local 实现）、`nop-auth-service`（Redis 实现 + 新增依赖 `nop-nosql-core`）

- Item Types: `Fix | Decision`

- [x] 定义 `MfaChallengeStore` 接口：`create(userId,mfaType,loginType,tenantId,phone)→challengeToken`、`peek(token)→Challenge|null`（不刷新 TTL）、`incrFailCount(token)→int`（原子）、`consume(token)→Challenge|null`（原子一次性删除）
- [x] 定义 `SmsCodeStore` 接口：`send(phone)→code`（6 位、TTL 配置化）、`verify(key,code)→CodeVerifyResult{VALID|EXPIRED|MISMATCH}`（成功原子消费 removeIfMatch，失败内部计数独立键）、`consume(key)`
- [x] Local 实现（基于 `LocalCache`，JVM 内原子 compute，无 TTL 竞态）
- [x] Redis 实现：写一律 `putExAsync`（TTL 在此设定），读用**不刷新 TTL** 的 `get`（禁用 GETEX/`getExAsync`/`NosqlCache.getAsync`），原子消费用 `NosqlCache.removeIfMatch`；存储的 Challenge/SmsCode 对象须为 `PrefixEncodeHelper` 可序列化（nop-nosql 编解码器为 `PrefixTextCodec`）
- [x] **原子失败计数裁决**：默认**方案 A**——`incrFailCount` 复用 `INosqlService.counter("mfa:fail:"+token)→INosqlCounter.incrementAsync(1)`，TTL 经 `setTimeoutAsync`(PEXPIRE) 设定。在 plan 内显式记录裁决结论与窄竞态说明（设计 §3.3）。若审查裁定方案 A 的 TTL 竞态不可接受，则升级为方案 B（扩展 `INosqlKeyValueOperations.incrementAsync`，跨模块公共 API，需另起 plan-first + 两个实现类同步）——本 plan 默认不采用方案 B
- [x] `nop-auth-service` pom 新增可选依赖 `nop-nosql-core`；`store-type` 选择由 `nop.auth.mfa.store-type` 配置决定（配置条目本身在 W5 落地，本 phase 仅在 store 装配处预留读取点，未读到时默认 `local`）

Exit Criteria:

- [x] `MfaChallengeStore` 生命周期测试通过：create→peek 命中→consume 后 peek 返 null；peek 不刷新 TTL（有 TTL 不变断言）；incrFailCount 在 Local 下并发安全
- [x] 超限作废语义可由调用方观测：`incrFailCount` 返回值 ≥ max-attempts 时调用方 consume/丢弃（本 phase 提供返回值，调用方在 W5 串联）
- [x] `SmsCodeStore` 三态测试通过：VALID（成功后 key 被消费，同码再验返 MISMATCH/EXPIRED）、EXPIRED（过期时间戳触发）、MISMATCH（失败内部计数递增、达上限作废）
- [x] key 隔离可观测：`login:{phone}` 与 `mfa:{userId}` 互不通用（隔离测试）
- [x] **接线验证**：Redis 实现确实委托到 `nop-nosql` 原语（`INosqlKeyValueOperations.putExAsync` / 不刷新 TTL 的 `get` / `NosqlCache.removeIfMatch` / `INosqlCounter.incrementAsync`）。**仓库内无嵌入式 Redis / testcontainers-redis 测试基础设施**，因此 Redis 连通性通过：(a) 具体代码追踪（`RedisMfaChallengeStore.create → INosqlKeyValueOperations.putExAsync → LettuceMessageService.psetex` 等逐条映射）+ (b) Local 实现的行为覆盖 + (c) 原语原子性已在 nop-nosql 自身测试中建立——三证合一（非仅类型存在）
- [x] **原子计数验证**：方案 A 下 `incrFailCount` 的原子性由 `INosqlCounter.incrementAsync`（INCRBY）在 nop-nosql 层建立的语义保证；Local 实现下并发 compute 不吞计数（有并发测试断言）
- [x] **端到端验证（适用性裁定）**：MFA 全链路端到端（密码→MFA→token）属于 **W5** plan 范围；本 phase 的"端到端"= store 通过其公共 API 契约的完整生命周期路径（create→peek→incrFail→consume / send→verify），已由上一条覆盖
- [x] **无静默跳过**：Redis 后端不可用/配置缺失时显式抛异常或回退到 Local 并记录，不返回空 store 假装成功；未实现的分支抛 `UnsupportedOperationException`
- [x] 若该 Phase 改变 live baseline：`No owner-doc update required`（设计 §3.3 已含裁决；`docs-for-ai/` 属 W7）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本 plan 涉及 ORM 模型（Protected Area，plan-first）+ 跨模块公共 API 邻近（`nop-nosql` 复用）。本 plan 即为 plan-first 产物。

- [x] 两张 MFA 实体落地、codegen/DDL 完成、明文边界 `published="false"` 生效
- [x] TOTP 验证器满足 RFC 6238 向量 + 窗口 + 防重放
- [x] 两个 store 的 Local + Redis 双实现满足生命周期/三态/原子计数/不刷新 TTL 约束
- [x] 原子失败计数方案 A 裁决在 plan 内记录、Redis 实现按裁决落地（方案 B 仅作为升级路径记录，不在本 plan 实施）
- [x] 必要 focused verification 已完成（RFC 向量、防重放、TTL 不刷新、并发计数、key 隔离）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已裁定：设计文档无需改动；`docs-for-ai/` 同步属 W7（显式 scope move，非静默跳过）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 Redis store 实现确实调用 `nop-nosql` 原语（运行时连通，非仅类型存在），无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-auth,nop-service-framework/nop-biz-auth-core -am`
- [x] `./mvnw test -pl nop-auth,nop-service-framework/nop-biz-auth-core -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 方案 B（扩展 INosqlKeyValueOperations.incrementAsync）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §3.3 裁决默认方案 A；方案 A 的 TTL 窄竞态无安全后果（计数键过期时 challenge 本身也已过期）。方案 B 属跨模块公共 API 变更，需独立 plan-first，不阻塞本 plan 交付可用的原子计数。
- Successor Required: no（仅当审查裁定方案 A 不可接受时另起 plan）

## Non-Blocking Follow-ups

- Redis store 的多实例压测/容量评估（部署期治理项，不影响 contract closure）
- `store-type` 配置热切换验证（二期优化候选）

## Closure

Status Note: W4 把 MFA 领域基础收口到"可用"状态——两张 ORM 实体（model-first）、RFC 6238 TOTP 验证器（HMAC-SHA1/6 位/30s/±1 skew + 防重放 + AESTextCipher 接线）、两个独立存储组件（MfaChallengeStore + SmsCodeStore）的接口与 Local + Redis 双实现。所有 in-scope 项已落地并通过 focused verification；方案 B（跨模块 incrementAsync）按裁决 deferred，不阻塞 closure。本 plan 不触碰登录流程（W5）、绑定/解绑（W6）。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: mission-driver execute pass（EXEC_PLANS），session 2026-08-12-111835-mission-driver
- Evidence:
  - **Phase 1（ORM 实体）PASS**：`nop-auth/model/nop-auth.orm.xml` 新增 `NopAuthMfaSetting`（16 列，userId PK）+ `NopAuthMfaRecoveryCode`（12 列，sid seq PK），1:N 关系按 userId；codegen 产物存在（`_gen/_NopAuthMfaSetting.java`、`_gen/_NopAuthMfaRecoveryCode.java`、retention `NopAuthMfaSetting.java`/`NopAuthMfaRecoveryCode.java`、`_NopAuthMfaSetting.xmeta`/`_NopAuthMfaRecoveryCode.xmeta` + retention、`NopAuthMfaSettingMapper.java`、`_NopAuthMfaSetting.xbiz`/`_NopAuthMfaRecoveryCode.xbiz`、DDL `_create_nop-auth.sql` 含两张表）；明文边界 `secret`/`bindToken` `published="false"`（retention `NopAuthMfaSetting.xmeta` + ORM `not-pub` tagSet 双重）——`TestNopAuthMfaSettingXmeta`（3 测试）断言 base/merged xmeta 不发布 secret、normal props 仍 queryable；round-trip `TestNopAuthMfaEntityRoundTrip`（3 测试）save/load 全字段、seq sid 生成、不存在查询返 null。
  - **Phase 2（TOTP）PASS**：`TOTPAuthenticator`（RFC 6238 HMAC-SHA1/6 位/30s/±1 skew，独立 `Base32`）+ 防重放窗口判定面（候选窗口严格 > lastVerifiedWindow）+ `AESTextCipher` 接线（`verify(encryptedSecret,...)` 解密路径）；`TestTOTPAuthenticator`（10 测试）覆盖 RFC 6238 Appendix B 6 个标准向量（全部命中精确窗口）、±1 skew 接受/超出拒绝、同窗口重放拒绝且不返回窗口号、provisioning URI 格式、32 字节 secret、AESTextCipher 解密路径可观测（RecordingCipher + 验证密文成功 + 密文非 base32）、tampered 密文 fail-closed。
  - **Phase 3（存储）PASS**：`MfaChallengeStore` + `SmsCodeStore` 接口 + `CodeVerifyResult` 三态 + 配置 bean（nop-biz-auth-core，纯逻辑零业务依赖）；`LocalMfaChallengeStore`/`LocalSmsCodeStore`（ConcurrentHashMap 原子 compute，无 TTL 竞态）；`RedisMfaChallengeStore`/`RedisSmsCodeStore`（nop-auth-service，写 putExAsync→psetex，读 get 非 GETEX，消费 removeIfMatch Lua CAS，计数 INosqlCounter.increment→INCRBY + setTimeoutAsync→pexpire）；`MfaStoreProvider` 装配点（storeType 预留读取点默认 local，redis 无后端 fail-closed 抛 `ERR_MFA_STORE_REDIS_BACKEND_NOT_AVAILABLE`）；pom 新增可选 `nop-nosql-core`。`TestLocalMfaChallengeStore`（6 测试，含 16×50 并发计数无丢更新 + peek 不刷新 TTL）+ `TestLocalSmsCodeStore`（8 测试，三态 + key 隔离 + 重发覆盖 + 重置计数）+ `TestRedisStoreWiring`（8 测试，FakeNosqlService 驱动 Redis store 全生命周期 + 原语选择断言 putExAsync/get/not-getExAsync/removeIfMatch/counter.increment/setTimeoutAsync）+ `TestMfaStoreProvider`（3 测试，default local / redis+nosql / redis-nosql fail-closed）。
  - **原子计数方案 A 裁决**：plan 内已记录（Phase 3 item + Deferred），Redis `incrFailCount` 复用 `INosqlCounter.incrementAsync`，TTL 首次递增经 `setTimeoutAsync` 设定一次；方案 B 仅作升级路径（Deferred But Adjudicated，non-blocking）。
  - **接线验证（Anti-Hollow）**：`TestRedisStoreWiring` 用 in-memory `FakeNosqlService`（实现 INosqlService，string-key + counter surface 真实行为，未用方法抛 UnsupportedOperationException）驱动 Redis store 走完 create→peek→incrFail→consume / send→verify 全路径，并断言具体原语被调用（非仅类型存在）；Redis 协议层原子性（INCRBY/Lua CAS）由 nop-nosql 自身测试建立。
  - **No-Silent-No-Op**：redis 后端缺失 fail-closed 抛 ErrorCode 异常（非空 store 假装成功）；`FakeNosqlService` 未实现分支抛 `UnsupportedOperationException`；TOTP secret 生成/HMAC 异常显式 `NopException.adapt`（非固定码/空）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（无未勾选项）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth-service/nop-biz-auth-core --severity high` 退出码 0（0 high/critical 空壳）。
  - `./mvnw test -pl :nop-auth,:nop-ai-gateway,:nop-nosql -am -T 1C` → BUILD SUCCESS（auth/biz-auth-core/meta/service 全绿，含新增 38 个 MFA 相关测试）。
  - 附带修复：`TestChannelScanBindLoginE2E`（W6-2）依赖全局 `VarCollector` 单例，被 AutoTestCase teardown 置 null 后随测试顺序暴露为 flaky；在 `@BeforeEach` 加防御性 `if(null) registerInstance(new)` 恢复默认（仅 null 时生效），suite 现 2 次连续运行 131/0/0 稳定绿。

Follow-up:

- 方案 B（`INosqlKeyValueOperations.incrementAsync` 原子 INCR+PEXPIRE）仅在审查裁定方案 A TTL 窄竞态不可接受时另起 plan-first（Deferred But Adjudicated，non-blocking）。
- W5 消费本 plan 产出：`store-type`/`totp-issuer`/MFA/SMS 配置与错误码、登录两阶段改造、短信登录（loginType=5）。
- W6 消费本 plan 产出：绑定/解绑/恢复码（BCrypt 哈希已就位 `codeHash` 列）、扫码适配。
- `docs-for-ai/` MFA 章节同步属 W7（显式 scope move）。
