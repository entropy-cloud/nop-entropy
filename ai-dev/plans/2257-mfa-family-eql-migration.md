# 2257 MFA 家族 raw SQL 平移 EQL（含 credential oauth state store）

> Plan Status: completed
> Last Reviewed: 2026-08-23
> Source: `ai-dev/analysis/2026-08/2026-08-23-direct-sql-usage-survey.md`（第二轮复核勘误 1：技术障碍已清除，"必须 raw SQL"表述过强；用户指示"修正"）
> Related: `ai-dev/plans/2256-eql-arithmetic-precedence-fix.md`（EQL 算术优先级已修复，平移的前置条件）
> Draft Review: 用户明确指示执行，免子 agent 草稿审查；前置调查已完成（实现/测试/beans/tagSet 全核实）。

## Purpose

把 analysis 第二节"保留"清单中仅剩工程理由（无技术理由）的 raw SQL——MFA store 家族与 credential oauth state——全部平移到 EQL（`ormTemplate` 执行、实体短名 + 属性名），消除物理列名耦合，统一到平台规范路径。原子语义（affected-row 判定、自增、条件守卫、REQUIRES_NEW）经 2256 后的 EQL 全部可表达且已探针实证。

## Current Baseline

- 平移对象（24 条语句、7 文件——closure audit 核正：6+4+4+2+2+1+5，初版"19"漏计 UserBizModel 的 5 条）：
  - `DbMfaChallengeStore`（6）：incrFailCount 自增+条件 / consume 条件 DELETE / markVerified 条件 UPDATE（is null 守卫）/ deleteByToken / readFailCount / readVerifiedAt
  - `DbSmsCodeStore`（4）、`DbEmailCodeStore`（4）：incrFail / conditionalDelete / deleteByKey / readFailCount
  - `MfaFactorVerifier`（2）：webauthn signCount CAS（`SIGN_COUNT<?` + `VERSION=VERSION+1`）/ touchLastUsed——各带 jdbcTemplate==null 实体退化路径
  - `LoginServiceImpl.markRecoveryCodeUsed`（1）：`USED=0` 条件 UPDATE（+退化）
  - `NopAuthUserBizModel`（3 方法 5 条）：isTotpLocked COUNT 探测 / incrTotpVerifyFail（REQUIRES_NEW 内自增+回读+条件作废 BIND_TOKEN）/ resetTotpVerifyFail（不碰 VERSION 的清零）（+退化）
  - `NopCredentialOauthStateStore`（2）：consume 条件 UPDATE / cleanupExpired DELETE
- 前置事实（已核实）：实体全部 `registerShortName="true"` + `tagSet="no-tenant"`（无租户语义差异）；EQL 能力已实证（update SET 自引用算术、is null 守卫、affected-row、`findInt`）；beans.xml 只注入 config（依赖按类型自动装配，换 `IOrmTemplate` 字段同样成立）；测试环境 `@Inject IOrmTemplate` 有先例（TestBoLib 等）。
- 已知语义差异（接受且更严谨）：`MfaSetting`/`MfaCredential`/`RecoveryCode` 为 `useLogicalDelete`，EQL update 自动追加 delFlag 未删条件（raw SQL 没有）；challenge/sms/email/oauth_state 无逻辑删除，物理语义不变。EQL bulk update 不自动维护 version/审计列（与 raw SQL 相同，需要的语句显式写）。

## Goals

- 24 条 raw SQL 全部 EQL 化（实体短名 + 属性名 + `ormTemplate` 执行），jdbcTemplate 依赖从 7 个文件移除（null 退化路径保留并改判 ormTemplate）。
- 手工 wiring 测试同步（`s.jdbcTemplate = ...` → `s.ormTemplate = ...`）。
- `./mvnw test -pl nop-auth/nop-auth-service`（406 基线）与 `nop-credential/nop-credential-service` 全绿。

## Non-Goals

- Redis/Local store 兄弟实现不动。
- `deleteRecoveryCodes` 等实体路径不动（本就是规范路径）。
- datav `recordShareVisit`/`existsTable`/PanelDataBinder（analysis 另有裁定，非 MFA 家族）。

## Scope

### In Scope

上述 7 个 main 文件 + 对应测试文件（TestDbSmsCodeStore / TestDbEmailCodeStore / TestDbMfaChallengeStore / TestDbMfaChallengeStoreEntityRoundTrip / TestMfaStoreWiringDb / TestMfaStoreProvider / MfaFactorVerifier/LoginServiceImpl/UserBizModel 相关 wiring 测试）+ 文档。

### Out Of Scope

其他 raw SQL 使用点（见 analysis 保留清单）。

## Execution Plan

### Phase 1 - store 三兄弟 + credential state store（nop-auth-dao 实体域）

Status: completed
Targets: `DbMfaChallengeStore` / `DbSmsCodeStore` / `DbEmailCodeStore` / `NopCredentialOauthStateStore` + 测试

- [x] 四个 store：`@Inject IJdbcTemplate jdbcTemplate` → `@Inject IOrmTemplate ormTemplate`；24 条中 store 侧 16 条语句 EQL 化（实体短名/属性名/`?` marker 参数经 `.sql(text, args...)`）；删除 TABLE 常量与 jdbcTemplate import
- [x] 测试手工 wiring 同步（含测试脚手架断言改 EQL 标量投影，不再依赖已删 TABLE 常量）
- [x] `./mvnw test` store 批次绿：Challenge 16 / SmsCode 9 / EmailCode 7 / WiringDb 3 / StoreProvider 8 / EntityRoundTrip 3
- [x] `./mvnw test -pl nop-credential/nop-credential-service` 绿（215）

Exit Criteria:

- [x] 四文件 grep 无物理表名/jdbcTemplate（残留 2 处为 javadoc 历史说明文字，非代码）
- [x] 一次性消费/自增/条件守卫语义测试全绿（affected-row 断言用例不变）
- [x] No owner-doc update required（行为不变，载体规范化；边界规则已在 2255 落档）
- [x] 日志更新

### Phase 2 - MFA 服务路径（webauthn CAS / 恢复码 / TOTP 计数）

Status: completed
Targets: `MfaFactorVerifier` / `LoginServiceImpl` / `NopAuthUserBizModel` + 测试

- [x] 三文件：jdbcTemplate 字段删除、ormTemplate 注入、null 退化路径改判 ormTemplate==null（实体写路径原样保留）
- [x] TOTP REQUIRES_NEW 结构保留（txn 模板边界与语句载体正交；`coalesce(o.totpFailCount,0)+1` 等 EQL 形态全过）
- [x] `./mvnw test -pl nop-auth/nop-auth-service` 全绿（406/406 基线含 MFA/webauthn/TOTP 用例；3 个反射 setField 测试同步）

Exit Criteria:

- [x] 三文件 grep 无 jdbcTemplate 代码残留（SETTING_TABLE/CREDENTIAL_TABLE 常量已删；LoginServiceImpl javadoc 保留迁移说明文字）
- [x] 退化路径测试（手工 wiring null）仍绿（406 内含）
- [x] No owner-doc update required
- [x] 日志更新

### Phase 3 - 回归收口与文档 + g4 审计（追加范围）

Status: completed

- [x] auth-service 406 + credential-service 215 全量回归绿（NOT 修复后复跑仍绿）
- [x] **g4 审计（用户第二问，追加进本 plan 收口）**：临时探针验证 NOT/AND/OR/一元负号/BETWEEN/算术在比较内/NOT BETWEEN/IS NULL——除 NOT 外全部标准。**发现 NOT 优先级缺陷**：`sqlExpr` 的 NOT 备选排在 AND/OR 之后（ANTLR 前=紧语义下 NOT 最松），`not a=1 and b='x'` 解析为 `not(a=1 and b='x')`（编译文本与 H2 值双证：实测 2，标准语义 1）。修复：NOT 备选移至最前；`./mvnw install -pl nop-orm-eql` 再生成。新增 `TestEqlLogicalPrecedence`（nop-orm）红→绿（红：testNotBindsTighterThanAnd 失败；绿 3/3）。仓库内 grep 无 `not` 前缀 EQL 用法（无行为影响）。修复后回归：eql 45 / orm 179（含新测试）/ auth 406 / credential 215 / sys-dao 43 全绿
- [x] analysis 保留清单更新（MFA 家族移出"保留"）+ g4 审计结论补充
- [x] owner doc 补充（2256 优先级段追加 NOT 修复与升级注意）
- [x] `check-doc-links` / `check-plan-checklist` exit 0（closure 时复跑留档）
- [x] 独立 closure audit + 提交（audit: agent_095a57f2，evidence 见 Closure）

Exit Criteria:

- [x] 两模块全绿
- [x] g4 审计结论落档（1 缺陷已修，其余形态标准）
- [x] 工具 exit 0
- [x] 日志收口条目

## Closure Gates

- [x] 24 条语句全部 EQL 化且语义测试绿（affected-row/自增/条件守卫断言不变；条数经 closure audit 核正 19→24）
- [x] 7 个 main 文件无 jdbcTemplate/物理表名代码残留（javadoc 历史说明 2 处可接受）
- [x] `./mvnw test -pl nop-auth/nop-auth-service` 通过（406/406）
- [x] `./mvnw test -pl nop-credential/nop-credential-service` 通过（215/215）
- [x] 独立子 agent closure-audit + evidence（agent_095a57f2，见 Closure）
- [x] checkstyle/imports 通过

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- datav `recordShareVisit` 等其余保留项维持 analysis 裁定。
- g4 其余审计（NOT 优先级等）由同日独立工作覆盖（见日志）。

## Closure

Status Note: MFA 家族（challenge/sms/email store、webauthn CAS、恢复码、TOTP 计数）与 credential oauth state 共 24 条 raw SQL 全部平移 EQL，原子语义与退化路径保持，auth 406 + credential 215 全绿；同 plan 内 g4 审计发现并修复 NOT 优先级缺陷（标准 NOT > AND > OR），仓库内零行为影响。独立审计从严复核通过。
Completed: 2026-08-23

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子 agent（agent_095a57f2，fresh session，2026-08-23）
- Evidence:
  - 平移本体 PASS：7 文件 jdbcTemplate 代码残留 0（javadoc 历史说明仅 2 处）、语句全部实体短名+camelCase 属性、ormTemplate 注入 7 处、null 实体退化路径全部保留（MfaFactorVerifier:244-255/288-291、LoginServiceImpl:781-789、UserBizModel:1670-1676/1717-1725/1742-1747）
  - 关键语义抽查 PASS：incrFailCount 自增+条件、markVerified is-null 恰一次守卫、incrTotpVerifyFail REQUIRES_NEW+coalesce 自增+bindToken 条件作废、webauthn CAS signCount< 守卫+version+1
  - g4 NOT 修复 PASS：`BaseRule.g4:132-137` NOT 备选最前；生成物 precpred NOT(4)>AND(3)>OR(2)；`TestEqlLogicalPrecedence` 3/3（testNotBindsTighterThanAnd 为真判别器，旧解析 count=2）
  - 实跑 exit 0：`-Dtest=TestEqlLogicalPrecedence,TestEqlArithmeticPrecedence`（3/3+6/6）；store 三测试（16/9/7）；surefire 全量 auth 406 / credential 215 / orm 179 / eql 45 / sys-dao 43 全 0 失败
  - Anti-Hollow PASS：`TestRecoveryCodeConditionalWrite` 4 线程并发恰一 VALID（EQL CAS 实证）；webauthn affected=0 失败路径测试在位（AdvancedE2E:225-267）
  - 风险 PASS：useLogicalDelete 追加 delFlag 条件为已裁定更严谨语义（全绿即证）；NOT 仓库内 not 前缀 EQL 用法 0；`.tokens` 未变合理
  - audit 两个 nit（条数 19→24、DbMfaChallengeStore:99 过时注释）已修正；`check-doc-links --strict` exit 0、`check-plan-checklist` 本 plan exit 0

Follow-up:

- 见 Non-Blocking Follow-ups；无 confirmed live defect 遗留
