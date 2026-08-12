# MFA 存储数据库实现 + store-type 默认 db

> Plan Status: draft
> Last Reviewed: 2026-08-13
> Source: `ai-dev/design/nop-auth/00-vision.md` + `01-architecture-baseline.md`（§3.3 存储组件）；用户 2026-08-13 裁决——**缺省不使用 Redis；所有存储必须有基于数据库的实现；默认 store-type 改为 db**
> Related: W4（MFA 数据模型 + TOTP + Local/Redis 双实现, done）; W5（登录流程两阶段, done）; W6/W7（todo）

## Purpose

MFA 挑战码（`MfaChallengeStore`）与短信验证码（`SmsCodeStore`）当前仅 Local（JVM 内存，重启丢失、多实例不共享）与 Redis（依赖 `nop-nosql-core`，optional）两种实现。本 plan 新增**数据库实现**（新 ORM 实体表），并将默认 `store-type` 由 `local` 改为 `db`——生产缺省零外部依赖、多实例共享、持久化不丢失。

## Current Baseline

（已核对 live repo）

- `MfaChallengeStore` 接口（create/peek/incrFailCount/consume）+ `SmsCodeStore` 接口（send/verify/consume，三态）定义于 `nop-biz-auth-core/.../io/nop/auth/core/mfa/store/`。
- `LocalMfaChallengeStore`/`LocalSmsCodeStore`（同包）：JVM 内 `ConcurrentHashMap`，TTL 基于绝对时间戳，无外部依赖。
- `RedisMfaChallengeStore`/`RedisSmsCodeStore`（`nop-auth-service/.../io/nop/auth/service/mfa/store/`）：基于 `INosqlService`；`MfaStoreProvider`（同包）按 `nop.auth.mfa.store-type` 装配（local/redis 两态，默认 local）。
- `nop-auth-service/pom.xml` 声明 `nop-nosql-core` 为 `<optional>true</optional>`；`MfaStoreProvider.java:20` **编译期直接 import `INosqlService`** → 应用未引入 nosql 时 Quarkus 启动在 `BeanDefinitionBuilder.initFactoryBeans` 反射 `MfaStoreProvider` 方法签名处抛 `NoClassDefFoundError: io/nop/nosql/core/INosqlService`（nop-app-erp 2026-08-12 实测崩溃，根因定位）。
- ORM 源 `nop-auth/model/nop-auth.orm.xml` 已有 `NopAuthMfaSetting` + `NopAuthMfaRecoveryCode`（W4 落地）；**无 challenge/sms code 存储表**。
- `nop.auth.mfa.store-type` 配置条目已存在（设计 §3.7），默认 `local`。

## Goals

- 新增两张 ORM 实体表（model-first）：`NopAuthMfaChallenge`（challenge token 存储）+ `NopAuthSmsCode`（短信验证码存储），承载挑战码/验证码的**持久化**语义（TTL 过期 + 失败计数 + 一次性消费）。
- 新增 `DbMfaChallengeStore` / `DbSmsCodeStore` 实现 `MfaChallengeStore` / `SmsCodeStore` 接口（`nop-auth-service`，基于 `IDaoProvider`/ORM DAO）。
- `MfaStoreProvider` 扩展 `store-type=db` 分支；默认值由 `local` **改为 `db`**（设计 §3.7 配置项默认值同步）。
- **修复类加载崩溃**：`MfaStoreProvider`（及其依赖链）不得在编译期直接引用 `INosqlService`——未引入 nosql 依赖的应用必须可正常启动（Local/DB 模式下）。Redis 分支改为**延迟/隔离装配**（不在类签名中硬依赖 `INosqlService`）。
- 行为语义：DB 实现与 Local/Redis 语义一致——peek 不刷新 TTL、consume 原子一次性、incrFailCount 原子递增、verify 三态（有效/过期/不匹配）成功原子消费。

## Non-Goals

- 不改 `MfaChallengeStore`/`SmsCodeStore` 接口签名（向后兼容）。
- 不引入 Redis 之外的 nosql 后端；Redis 实现保留（store-type=redis 仍可选）。
- 不做 WebAuthn / 邮件验证码 / 操作级 MFA（二期，roadmap 既有边界）。
- 不做 challenge 表的定时清理任务（本期用惰性 TTL 清理：peek/consume 时过期即删；批量清理为 Follow-up，触发条件=表行数增长可观测）。
- 不涉及登录流程（W5 已 done，不改）。

## Scope

### In Scope

- `nop-auth/model/nop-auth.orm.xml` 新增 `NopAuthMfaChallenge` + `NopAuthSmsCode`（→ codegen → DDL 迁移脚本）。
- `nop-auth-service` 新增 `DbMfaChallengeStore` / `DbSmsCodeStore` + `MfaStoreProvider` 扩展 db 分支 + 默认值改 db + 类加载崩溃修复。
- 测试：DB 实现语义等价性（TTL/原子消费/失败计数/三态 verify）+ 无 nosql 依赖启动测试（回归 nop-app-erp 崩溃场景）。

### Out Of Scope

- Redis 实现改动（保留现状）。
- `docs-for-ai/` MFA 章节同步（roadmap W7 既有分配，本 plan 完成后由 W7 统一覆盖；本 plan 只更新 `ai-dev/design/nop-auth/01-architecture-baseline.md` §3.3/§3.7 的存储形态与默认值）。

## Execution Plan

### Phase 1 - ORM 实体（model-first）

Status: pending

- [ ] 在 `nop-auth/model/nop-auth.orm.xml` 新增 `NopAuthMfaChallenge`：`challengeToken`（PK, UUID）/ `userId` / `mfaType` / `loginType` / `tenantId` / `phone` / `expireAt` / `failCount` + 审计字段；索引按 `(userId)`、`(expireAt)`
- [ ] 新增 `NopAuthSmsCode`：`codeKey`（PK）/ `phone` / `codeHash`（或 code，安全裁决）/ `expireAt` / `failCount` + 审计字段；索引按 `(expireAt)`
- [ ] 跑 codegen 生成实体/dao + DDL 迁移脚本；**禁止手编 `_gen/` 与 `_` 前缀文件**
- [ ] 单元测试：实体 round-trip（save/load/update）；PK 唯一约束

Exit Criteria:

- [ ] 两张实体定义存在且字段语义与设计对齐；codegen 产物 + DDL 脚本存在；无 `_` 前缀文件被手编
- [ ] 实体 round-trip 测试通过
- [ ] **新功能测试**：`TestDbMfaChallengeStoreEntityRoundTrip`（save/load/update/delete）
- [ ] 若该 Phase 改变 live baseline：`ai-dev/design/nop-auth/01-architecture-baseline.md` §3.3 增 DB 实现形态记录（本 Phase 末执行）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DbMfaChallengeStore + DbSmsCodeStore 实现

Status: pending

- [ ] `DbMfaChallengeStore`：create（INSERT + expireAt）/ peek（SELECT，过期则 DELETE 返 null）/ consume（DELETE 原子一次性，返回被删行）/ incrFailCount（原子 UPDATE `failCount = failCount + 1`，或读-写于单行并发安全裁决）
- [ ] `DbSmsCodeStore`：send（INSERT）/ verify（三态：有效=校验通过+DELETE 消费；过期=过期+DELETE；不匹配=incrFailCount）/ consume（DELETE）
- [ ] **原子性**：consume 用条件 DELETE（`WHERE challengeToken=? AND expireAt>now`）保证一次性；incrFailCount 用 SQL 原子递增（`UPDATE ... SET failCount=failCount+1 WHERE ...`），对齐 Redis INCR 语义（设计 §3.3 方案 A 思想）
- [ ] 事务边界：单行操作无需显式事务（DAO 单语句原子）；若需要多语句则声明 `@Transactional` 并说明
- [ ] 集成测试：TTL 过期清理（peek 过期返 null + 行删除）、consume 一次性（二次 consume 返 null）、incrFailCount 并发递增（多线程）、verify 三态

Exit Criteria:

- [ ] 两实现类存在，接口语义与 Local/Redis 一致（行为等价测试对照 Local）
- [ ] **新功能测试**：`TestDbMfaChallengeStore`（create/peek/incrFailCount/consume/过期/一次性）+ `TestDbSmsCodeStore`（三态 verify/消费/失败计数）
- [ ] **并发断言**：incrFailCount 多线程递增最终值 = 调用次数（无丢失）
- [ ] **无静默跳过**：DB 不可用/行不存在时显式返回 null/错误，不吞异常
- [ ] 若该 Phase 改变 live baseline：`01-architecture-baseline.md` §3.3 补 DB 实现原子性细节（本 Phase 末执行）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - MfaStoreProvider 扩展 + 默认 db + 类加载崩溃修复

Status: pending

- [ ] `MfaStoreProvider` 新增 `STORE_TYPE_DB = "db"`；store 实现改为**声明式装配**——消费者经 `ioc:collect-beans name-prefix="nopMfaChallengeStore"/"nopSmsCodeStore" as-map="true"` 按前缀收集已注册实现（平台命名型扩展点标准模式，对齐 `nopJobInvoker_`/`nopCodeRuleVariable_` 先例，见 `docs-for-ai/02-core-guides/code-style.md:110-114` + `03-runbooks/generate-business-code.md:104`），按 store-type 选择对应实现
- [ ] 默认 `storeType` 常量由 `STORE_TYPE_LOCAL` 改为 `STORE_TYPE_DB`；设计 §3.7 配置默认值同步（`nop.auth.mfa.store-type` 默认 `db`）
- [ ] **类加载崩溃修复**：`MfaStoreProvider` 及 Redis 实现类不得在编译期类签名中直接引用 `INosqlService`——Redis store 的 bean 定义移入独立 beans.xml（或 `ioc:if` 条件），仅在 `store-type=redis` 且 nosql 依赖存在时激活；无 nosql 的应用根本不加载该 bean（裁决：优先前缀收集 + 条件注册，`by-type` 因需 `loadBeanClass` 不采用）
- [ ] 回归验证：**无 `nop-nosql-core` 依赖的应用可正常启动**（复现 nop-app-erp 崩溃场景 → 修复后启动成功）；`store-type=local` 与 `store-type=db` 均正常
- [ ] Redis 路径回归：`store-type=redis` 时行为不变（如可测）

Exit Criteria:

- [ ] `MfaStoreProvider` 三态分派（local/db/redis）工作；默认 `db`；store 实现经 collect-beans 前缀收集装配（**接线验证**：`store-type=db` 时实际返回 `DbMfaChallengeStore`，实例类型断言）
- [ ] **无 nosql 依赖启动测试**：新增/复用集成测试——classpath 不含 `nop-nosql-core` 时 IoC 容器初始化成功（捕获 `NoClassDefFoundError` 场景）
- [ ] **接线验证**：`store-type=db` 时 `getMfaChallengeStore()` 返回 `DbMfaChallengeStore`（实例类型断言）；Redis bean 在无 nosql 依赖时不被注册/加载
- [ ] 既有 MFA 测试（W4/W5 产出的 Local/Redis 测试）零回归
- [ ] 若该 Phase 改变 live baseline：`01-architecture-baseline.md` §3.3/§3.7 同步（DB 实现形态 + store-type 默认 db + 类加载隔离裁决 + collect-beans 前缀装配）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 三个 Phase 的 Exit Criteria 全部勾选（含新功能测试、并发断言、无静默跳过、无 nosql 启动回归）
- [ ] `nop-auth` 相关模块测试全绿（`mvn test -pl nop-auth/nop-auth-service,nop-biz-auth-core -am` 或等价命令，以 live repo 为准）
- [ ] `nop-app-erp` 应用侧验证：不引入 `nop-nosql-core` 时应用启动成功（原崩溃场景闭环）；如 ERP 侧有 MFA 相关 E2E 则抽样回归
- [ ] 独立子 agent closure audit 通过（evidence 写入本 plan Closure 段）
- [ ] `ai-dev/backlog/nop-credential-mfa-roadmap.md` 登记新工作项（如 W8）并置 `done`；设计文档 §3.3/§3.7 同步
- [ ] `ai-dev/logs/` 收口记录

## Deferred But Adjudicated

- **challenge 表批量清理任务**（定时删除过期行）：本期惰性清理已满足语义；触发条件=表行数增长可观测（`NopAuthMfaChallenge` 行数 > 阈值）时补 nop-job 定时清理。
- **SMS code 存储明文 vs 哈希**：本期按 `codeHash`（BCrypt，对齐恢复码先例）或明文+短 TTL 裁决（实施 Phase 1 先行裁决并回写设计文档）；短信码 5 分钟 TTL 短，哈希成本可接受则哈希。

## Risks

- **接口语义漂移**：DB 实现与 Local/Redis 的 TTL/原子消费语义不一致 → 以行为等价测试对照 Local 为守门。
- **类加载修复引入反射/隔离复杂** → 优先隔离装配（独立 beans.xml），反射为后备方案；修复必须不破坏 Redis 路径。
- **默认 db 的部署影响**：默认改 db 后，部署方必须有 MFA 两张表的 DDL；W4 已有 DDL 迁移管线，新表随 codegen 自动生成，风险可控。
