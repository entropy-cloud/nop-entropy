# 2275 nop-auth 限流与登录失败计数的 DB 集群后端

> Plan Status: completed
> Last Reviewed: 2026-09-21
> Source: `ai-dev/design/nop-auth/03-cluster-support-and-structure-design.md`（§3.1/§3.2 db 增补 + §四.4 用户裁定推翻原拒绝）+ `ai-dev/plans/2274-nop-auth-cluster-support-and-mfa-split.md`（既有 local|redis 出口）
> Related: `ai-dev/design/nop-auth/01-architecture-baseline.md`（Db* MFA store 先例）

## Purpose

为发码限流（`ISendCodeRateLimiter`）与登录失败计数（`ILoginAttemptStore`）增加 `db` 存储后端（新增两张数据库表，比照 session 的 `DaoUserContextCache` 与 MFA store 的 `Db*` 先例），使 Nop 在**不部署 Redis、仅使用数据库**的情况下获得集群部署能力。

## Current Baseline

- plan 2274（completed，commit 667473d）已落地 `ISendCodeRateLimiter` 与 `ILoginAttemptStore` 接口 + Local/Redis 双实现 + collect-beans 装配（`nop.auth.rate-limit.store-type` / `nop.auth.login-attempt.store-type`，默认 `local`，fail-closed）。
- 设计原拒绝 DB 后端（design §四.4）已被用户裁定推翻并回写 design（db 选项为无 Redis 部署唯一集群路径）。
- 既有先例：`DbSmsCodeStore`（IDaoProvider + IOrmTemplate 条件 SQL + `ERR_SQL_DUPLICATE_KEY` 主键冲突回退，多节点并发插入竞态已处理）、`nop_auth_sms_code` 实体（`nop-auth/model/nop-auth.orm.xml` 源模型 → nop-auth-dao codegen）、增量 DDL（`nop-auth/deploy/sql/{mysql,oracle,postgresql}/_add_*.sql`）。
- 测试先例：`@NopTestConfig(localDb=true, initDatabaseSchema=...)` autotest 形态（TestRecoveryCodeConditionalWrite / TestDbCodeStoreSendRace）。

## Goals

- 新增实体 `NopAuthRateLimitCounter`（表 `nop_auth_rate_limit_counter`：PK `counter_key` VARCHAR(150)，`counter_count` BIGINT，`expire_at` BIGINT）与 `NopAuthLoginAttempt`（表 `nop_auth_login_attempt`：PK `attempt_key` VARCHAR(150)，`fail_count` INT，`expire_at` BIGINT）。
- `DbSendCodeRateLimiter`：三类键同表（目标日计数 / IP 日计数 / 间隔门键 `...:i`）。**dup-key 分支语义钉定（审查 M3/M4——两类键相反）**：
  - 日计数键：`UPDATE count=count+1 WHERE key=? AND expire_at>now`；affected=0（不存在**或已过期**）→ 条件 DELETE 过期行 + INSERT(count=1, expire_at=now+ttl)；INSERT 撞 PK（并发复活竞态）→ 重试条件 UPDATE。禁止"回退为条件递增"（过期情形会静默丢计数）。
  - 间隔门键（恒 SETNX）：裸 INSERT 成功 = 占位放行（expire_at=now+interval）；撞 PK → **读行判定**：`expire_at>now` = 间隔内 → 拒绝 RATE_LIMITED；`expire_at<=now` = 过期行 → 条件 DELETE + 重试 INSERT（复活）。禁止无条件拒绝（过期行会永久锁死发码）。
  - TTL 刷新策略（审查 M5）：**对齐 Redis 固定窗口**——UPDATE 递增不触碰 expire_at（首次 INSERT 时定值），不做滑动窗口。
  - 过期行惰性清理：访问路径顺带条件 DELETE（`DbSmsCodeStore.verify` 先例）。
- `DbLoginAttemptStore`：`incrementLoginFailCount` = 条件 UPDATE（`fail_count=fail_count+1 WHERE attempt_key=? AND expire_at>now`；affected=0 → 条件 DELETE + INSERT(fail_count=1, expire_at=now+ttl)，INSERT 撞 PK → 重试条件 UPDATE）；返回值经递增后 SELECT（审计用途，并发允许略新）；TTL 来源 = 注入 `UserContextConfig.loginFailTimeout`（对齐 AbstractUserContextCache 配置链，审查 M5-b）；刷新策略 = 首次 INSERT 定值不刷新（对齐 `RedisLoginAttemptStore` 固定窗口）；get（过期读 0 + 惰性删除）/set（覆盖 upsert）/reset（DELETE）语义对齐。
- beans 注册 `nopAuthRateLimiter_db` / `nopLoginAttemptStore_db`（store-type 取值扩为 `local|db|redis`），provider/装配零改动（collect-beans 自动收集）。
- owner doc 集群矩阵更新：无 Redis 部署取 `db`。

## Non-Goals

- 不改接口签名（`ISendCodeRateLimiter`/`ILoginAttemptStore` 冻结）。
- 不改 Local/Redis 实现与默认值（默认仍 `local`，行为等价 plan 2274）。
- 不做批量过期清理任务（惰性删除先例延续，`01-architecture-baseline` 既有 Follow-up 同类）。
- 不手写 BizModel/GraphQL 定制面（codegen 会自动生成标准 CRUD 面——BizModel/xbiz/xmeta 与 `NopAuthSmsCode` 同款，本 plan 接受该生成物不消费；审查 M2）。

## Scope

### In Scope

- `nop-auth/model/nop-auth.orm.xml`：新增 2 实体（codegen 生成 dao 实体）。
- `nop-auth/deploy/sql/{mysql,oracle,postgresql}/_add_rate_limit_counter_nop-auth.sql`、`_add_login_attempt_nop-auth.sql`。
- `nop-auth/nop-auth-service`：`DbSendCodeRateLimiter`（ratelimit 包）、`DbLoginAttemptStore`（login 包）、beans 注册、测试。
- `docs-for-ai/03-modules/nop-auth.md` 矩阵与配置项行更新。

### Out Of Scope

- Redis/Local 实现改动、provider 选择逻辑改动。
- MFA store 体系（`nop.auth.mfa.store-type` 已有 db）。

## Execution Plan

### Phase 1 — ORM 实体与 DDL

Status: completed
Targets: `nop-auth/model/nop-auth.orm.xml`、`nop-auth/deploy/sql/{mysql,oracle,postgresql}/`

- Item Types: `Fix`

- [x] `nop-auth/model/nop-auth.orm.xml` 新增 `NopAuthRateLimitCounter`（tableName=nop_auth_rate_limit_counter，tagSet=no-tenant，列：counterKey PK VARCHAR(150) var/not-pub、counterCount BIGINT、expireAt BIGINT、审计四列对齐 sms_code）与 `NopAuthLoginAttempt`（tableName=nop_auth_login_attempt，attemptKey PK VARCHAR(150)、failCount INTEGER default 0、expireAt BIGINT、审计四列）
- [x] 三库增量 DDL（mysql/oracle/postgresql 各 2 文件，对齐 `_add_email_code_nop-auth.sql` 形态）
- [x] 三库全量 DDL 同步：`_create_nop-auth.sql` 追加两表、`_drop_nop-auth.sql` 追加两表（全新部署含新表——审查 M1）
- [x] codegen 通过：`./mvnw -pl nop-auth/nop-auth-codegen -am test -DskipTests`（codegen 绑定 generate-test-resources 阶段——compile 生命周期不触发）生成实体族

Exit Criteria:

- [x] codegen 产物齐备且编译通过：`nop-auth-dao/.../entity/{_NopAuthRateLimitCounter,NopAuthRateLimitCounter,_NopAuthLoginAttempt,NopAuthLoginAttempt}.java`、`nop-auth-service/.../entity/{...}BizModel.java`、`nop-auth-meta/.../{...}.xmeta`、`nop-auth-service/_vfs/.../{...}.xbiz`（codegen 自动面，审查 M2 清单）
- [x] 三库增量 DDL（**3 个合并文件**——plan 原写 6 文件为文本滞后，审查 Minor-4a）+ 全量 `_create/_drop` 更新存在且列/主键与 ORM 模型一致（逐列核对）
- [x] orm.xml 实体属性对照 `NopAuthSmsCode` 先例核对：registerShortName=true、displayName/i18n-en:displayName、expireAt 索引（先例 `IX_NOP_AUTH_SMS_CODE_EXPIRE` 同款）——**偏差注记（审查 Minor-3）**：propId 1-6 后跳 9（updateTime 占 9），间隙合法（既有 NopAuthMfaSetting/MfaChallenge 同款），保持生成物不动
- [x] No new test required: 纯模型/DDL 变更，行为验证在 Phase 2（Rule 25 注记）
- [x] `ai-dev/logs/` 执行条目已更新

### Phase 2 — Db 双实现与装配

Status: completed
Targets: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/{ratelimit,login}/`、`auth-service.beans.xml`

- Item Types: `Fix | Proof`

- [x] `ratelimit/DbSendCodeRateLimiter.java`：checkSmsAllowed/checkEmailAllowed 三层语义（先递增后检查跨后端一致；间隔门恒 INSERT-SETNX；惰性过期清理）；@Inject IDaoProvider + IOrmTemplate
- [x] `login/DbLoginAttemptStore.java`：`incrementLoginFailCount` 条件 UPDATE + 过期行删重插 + 冲突重试；TTL 注入 `UserContextConfig.loginFailTimeout`（首次定值不刷新，对齐 Redis 固定窗口）
- [x] beans：`nopAuthRateLimiter_db` / `nopLoginAttemptStore_db`（lazy-init + autowire-candidate=false，无 condition——同 Db MFA store 先例；注意 collect-beans 解析会在容器启动时实例化 db bean，lazy-init 仅为延迟而非隔离，构造不触库故无害——审查 m2）
- [x] 测试（**执行期偏差注记**：手工 H2 栈替代 autotest 注解形态——Db bean autowire-candidate=false 不可容器注入；并发 Proof 16 线程（原写 32，规避 H2 锁超时抖动——审查 m3 允许）；含 db 日配额用例——审查 Minor-6）：Db 限流（顺序间隔拒绝 / 间隔门 SETNX + 过期行复活 / 日配额 / scope 隔离 / 并发同键计数恰为 N / 过期惰性删除）；Db 失败计数（并发递增无丢失更新 / 过期读 0 / reset 删除 / user-ip 键维度隔离 / TTL 固定窗口）；provider db 选择与 fail-closed 不变
- [x] 既有测试零断言修改通过

Exit Criteria:

- [x] **接线验证**：store-type=db 时 provider 返回 Db 实现且发码入口/失败计数路径运行时走通（autotest 全链路）
- [x] `ai-dev/logs/` 执行条目已更新
- [x] **并发原子性 Proof**：32 线程 H2 并发递增计数恰为 N（SQL 原子递增证据）
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` 通过（0 failures）
- [x] No owner-doc update required（矩阵归 Phase 3）

### Phase 3 — 文档与收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-auth.md`、`ai-dev/logs/`

- Item Types: `Fix | Proof`

- [x] owner doc：集群矩阵限流/失败计数两行的集群取值更新为 `redis` 或 `db`；两张新表纳入存储说明；store-type 取值列表更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 执行记录

Exit Criteria:

- [x] owner doc 与 design §3.5 一致（逐行核对）
- [x] doc link checker 退出码 0
- [x] `ai-dev/logs/2026/09-21.md` 已更新

## Closure Gates

- [x] in-scope 工作全部落地（两实体 + 三库 DDL + 两 Db 实现 + beans + 测试）
- [x] 并发原子性 Proof（32 线程恰为 N）与接线验证完成
- [x] 既有 Local/Redis 行为与默认值不变（plan 2274 测试零断言修改通过）
- [x] 无静默跳过：Db 实现无空方法/TODO（scan-hollow --severity high exit 0）
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并写入 Closure 段落
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2275-nop-auth-db-rate-limit-backend.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth/nop-auth-service --severity high` 退出码 0

## Non-Blocking Follow-ups

- 两张新表的批量过期清理（惰性删除先例延续；与 MFA challenge 清理同类既有 follow-up）。
- （已裁定，非 follow-up）`counter_key`/`attempt_key` 定长 VARCHAR(150)：email 目标键最长（`login:email:{email}:d{day}`），150 字符覆盖 RFC 限长 email（64+@+255 超界情形按 StringHelper 截断保护写入或 fail-closed，执行期以编译常量校验落定）；Phase 1 实施时核对。

## Closure

Status Note: 三个 Phase 全部完成并经独立 closure audit（APPROVE-WITH-NOTES，关门必修项已修复）：DB 后端使 Nop 在无 Redis、仅数据库部署下获得集群限流与集群锁号；460 tests 0 failures 0 errors；全部门槛工具退出码 0。
Completed: 2026-09-22

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子 agent（fresh session，task agent_4378fd9d-e04e-48a2-ac33-9e23ba4fd1cd，2026-09-22）
- Audit Session: agent_4378fd9d（逐条核对 Phase 1-3 Exit Criteria + Closure Gates + Anti-Hollow + 接口契约）
- Evidence:
  - Phase 1：codegen 产物族齐备（entity/_gen/BizModel/xmeta/xbiz）；三库增量 DDL（3 合并文件）+ _create/_drop 全量同步逐列一致；orm 对照先例（propId 间隙注记——审查 Minor-3）
  - Phase 2：dup-key 双语义与 plan 钉定逐句一致（M3 间隔门复活/M4 计数键删重插/M5 固定窗口）；beans 两 bean collect 兼容；既有测试零断言修改（git diff 667473d HEAD --stat 为空）
  - Phase 3：owner doc 矩阵/配置项更新为 "redis 或 db"；doc-links exit 0
  - Anti-Hollow：两实现无空壳，dup-key 判定全部 DaoErrors.ERR_SQL_DUPLICATE_KEY + 异常原样重抛；scan-hollow exit 0
  - 关门必修修复（审计后）：Major-1 全部产物路径限定提交（不裹挟 nop-lint 改动）；Major-2 补 provider db 选择断言（TestSendCodeRateLimiterProvider.testDbWhenRegistered + TestLoginAttemptWiring.testProviderSelectsDbWhenRegistered）
  - Minor 修复：Minor-3 propId 间隙注记；Minor-4 四项文本偏差注记（DDL 3 合并文件/16 线程/手工 H2 栈/日志 09-22）；Minor-5 beans 注释 local|db|redis；Minor-6 补 Db 日配额用例（testTargetDailyLimitRejected，interval=0 覆盖 + 每 send 独立 ORM 会话——同会话一级缓存会读到递增前旧值，生产每请求一会话无此问题）
  - 验证：`mvnq --no-lock -- -Dmaven.repo.local=.m2-repo-2275 -pl nop-auth/nop-auth-service clean test` → 460 tests, 0 failures, 0 errors, 3 skipped（_tmp/p2275-test23.log）；check-plan-checklist --strict exit 0（Completion 前复跑）
- 环境注记：共享 .m2-repo 被并行会话踩踏 → 私有仓库 .m2-repo-2275（clonefile 播种）+ mvnq --no-lock 显式 -D 隔离解决

Follow-up:

- 两张新表批量过期清理任务（惰性删除先例延续，与 MFA challenge 清理同类）
- counter_key VARCHAR(150) 对超长 email 的截断保护（执行期以编译常量核对——plan In-Scope 已裁定）
