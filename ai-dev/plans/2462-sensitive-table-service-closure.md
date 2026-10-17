# 2462 敏感表服务面收口（no-service / not-pub / 只读）

> Plan Status: completed
> Last Reviewed: 2026-10-17
> Source: 用户安全排查（EmailCode 等表是否应发布服务；系统中有哪些安全表不应发布）
> Related: `ai-dev/plans/2461-entity-no-service-tag.md`、`docs-for-ai/02-core-guides/model-first-development.md`

## Purpose

用 plan 2461 的 `no-service` 机制与字段级 `not-pub`，对系统内安全敏感实体做服务面收口，并补齐真实字段暴露缺口。

## Current Baseline（live 事实，执行中校正）

- `no-service` 机制已落地（plan 2461）。
- 绝大多数 MFA/凭证表**已有专用收口**：MFA 八表走 `MfaSensitiveTableBizModel`（写动作**可见但显式拒绝**，错误码 `nop.err.auth.mfa.crud-disabled`），凭证四表走带掩码/归属过滤的专用 `BizModel`（含 `saveCredential` 等合法专用动作）。对这些实体套 `no-service` 会：① 破坏既有“可见但拒绝”契约（`TestMfaCrudLockdownE2E` 断言 `unknown-operation` 必红）；② 隐藏合法专用动作（凭证 `saveCredential` 等）。**故不适用 `no-service`。**
- 真实缺口（执行中已修）：
  - `NopCredential.data`（密文 JSON）此前 `tagSet="var"` → 会进 GraphQL 输出。
  - `NopAuthExtLogin.credential`（登录密码）此前无 `not-pub` → 会进 GraphQL 输出。
- 裸 `CrudBizModel` 且**无专用动作/无人工管理必要**者：`NopAuthLoginAttempt`、`NopAuthRateLimitCounter`（删行即绕过限流）、`NopOauthAuthorization`、`NopOauthAuthorizationConsent` —— 适用 `no-service`。
- `NopAuthOpLog`（审计日志）裸 `CrudBizModel`：需禁写防篡改、保留管理员读 —— 适用只读基类。

## Goals

- `NopCredential.data`、`NopAuthExtLogin.credential` 标注 `not-pub`，不进 GraphQL 输出。
- `NopAuthLoginAttempt`、`NopAuthRateLimitCounter`、`NopOauthAuthorization`、`NopOauthAuthorizationConsent` 标注 `no-service`。
- `NopAuthOpLog` 改只读（禁写保留读）。
- 不破坏 MFA/凭证既有收口契约。
- codegen 重生成后 runtime 生效，聚焦测试钉住。

## Non-Goals

- 不把 `no-service` 套到已有专用 BizModel/收口契约的实体（MFA 八表、凭证四表、ExtLogin）。
- 不改 `not-pub`/`no-web`/`no-api` 语义；不删除已生成页面/菜单。
- `NopAuthSession` 暂不动（其管理动作 = 会话管理能力，尚无专用受控动作，另立 follow-up）。

## Scope

### In Scope

- ORM 源：`nop-auth/model/nop-auth.orm.xml`、`nop-credential/model/nop-credential.orm.xml`、`nop-auth/nop-oauth/model/nop-oauth.orm.xml`。
- `nop-biz` 通用只读基类 + `NopAuthOpLogBizModel` 改只读。
- 聚焦测试 + owner-doc + 日志。

### Out Of Scope

- 页面/菜单删除；`NopAuthSession` 只读化；MFA/凭证表的读出口进一步收口。

## Execution Plan

### Phase 1 - not-pub 缺口 + no-service（ORM 源 + regen）

Status: completed
Targets: `nop-auth/model/nop-auth.orm.xml`；`nop-credential/model/nop-credential.orm.xml`；`nop-auth/nop-oauth/model/nop-oauth.orm.xml`

- Item Types: `Fix` / `Feature`

- [x] `NopCredential.data` 加 `not-pub`；`NopAuthExtLogin.credential` 加 `not-pub`。
- [x] `NopAuthLoginAttempt`、`NopAuthRateLimitCounter` 加 `no-service`（auth）。
- [x] `NopOauthAuthorization`、`NopOauthAuthorizationConsent` 加 `no-service`（oauth）。
- [x] codegen 重生成：auth/credential/oauth 的 `_app.orm.xml` 与 `_*.xmeta` 同步。

Exit Criteria:

- [x] `_NopCredential.xmeta` 的 `data`、`_NopAuthExtLogin.xmeta` 的 `credential` 均 `published="false"`。
- [x] `_NopAuthLoginAttempt.xmeta`/`_NopAuthRateLimitCounter.xmeta`/`_NopOauthAuthorization.xmeta`/`_NopOauthAuthorizationConsent.xmeta` root tagSet 含 `no-service`。
- [x] MFA 八表 / 凭证四表 / ExtLogin 的 xmeta root tagSet **不含** `no-service`（避免破坏既有契约）。
- [x] `ai-dev/logs/` 更新。

### Phase 2 - CrudBizModel 内建只读开关（objMeta 驱动）

Status: completed
Targets: `nop-service-framework/nop-biz/.../crud/CrudBizModel.java`、`BizConstants.java`、`BizErrors.java`；`nop-auth/model/nop-auth.orm.xml`（NopAuthOpLog）；`nop-biz/src/test/`

- Item Types: `Feature`

- [x] `CrudBizModel` 增加只读开关：`isReadOnly()` 显式设置优先，未设置时读 `objMeta` 的 `read-only` tag（`BizConstants.TAG_READ_ONLY`）；`setReadOnly(boolean)` 可显式覆盖；`checkReadOnly(action)` 守卫。
- [x] `CrudBizModel` 全部 15 个继承 `@BizMutation`（含 3 个 M2M）入口调用 `checkReadOnly(...)`；读动作不受影响。
- [x] ORM 源 `NopAuthOpLog` tagSet 加 `read-only` → 生成 xmeta 携带该 tag（无需改 beans.xml / Java 构造）。
- [x] `TestReadOnlyCrudBizModel`：显式 setReadOnly 与 objMeta tag 推导两种路径下 15 个写动作全抛 `nop.err.biz.read-only-object`；无 tag 为可写。

Exit Criteria:

- [x] 只读对象全部写动作抛通用错误码（15/15，含 M2M）；读动作路径不覆写。
- [x] 只读判定可由 ORM/xmeta 的 `read-only` tag 驱动（不依赖手工 `setReadOnly` / 生成 beans 属性）。
- [x] `nop-biz` 全量 131 通过；`nop-auth-service` 464 通过。
- [x] owner-doc：`model-first-development.md` 增补“安全表服务面收口策略”说明（no-service 适用边界 / 只读基类）。
- [x] `ai-dev/logs/` 更新。

### Phase 3 - 回归验证

Status: completed
Targets: 受影响模块

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-auth/nop-auth-service`：464 通过（3 skipped 既有）。
- [x] `./mvnw test -pl nop-credential/nop-credential-service`：216 通过。
- [x] `./mvnw test -pl nop-auth/nop-oauth/nop-oauth-meta,nop-auth/nop-oauth/nop-oauth-web`：4 通过。
- [x] `./mvnw test -pl nop-service-framework/nop-biz`：128 通过。

## Closure Gates

- [x] C 类两字段 not-pub 生效（GraphQL 不输出）
- [x] 计数器/OAuth 两表 no-service 生效
- [x] OpLog 只读（写拒绝、读保留）
- [x] MFA/凭证既有收口契约未破坏（回归全绿）
- [x] owner docs 同步
- [x] 独立 closure audit 证据写入
- [x] `./mvnw test`（受影响模块全绿）
- [x] checkstyle / 规范检查（随模块 test）

## Deferred But Adjudicated

### NopAuthSession 只读化

- Classification: `watch-only residual`
- Why Not Blocking Closure: Session 目前唯一管理能力是 CRUD 删改（用于会话管理）；在提供专用受控动作前改只读会移除能力，需产品裁决，不影响本计划其它收口成立。
- Successor Required: `no`
- Successor Path: —

### MFA/凭证表读出口进一步收口

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 既有设计为“写可见但拒绝 + 读经 action-auth/归属过滤”，本次不改其契约；如需收紧读需独立裁决与测试。
- Successor Required: `no`

### 页面/菜单清理

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `no-web` 不删除既有生成文件；删除页面/菜单需独立批次。
- Successor Required: `no`

## Non-Blocking Follow-ups

- `NopOauthRegisteredClient` / `NopMetaDataSource` / `NopAiModel` 保留发布，仅证书字段 not-pub（已满足）。
- ExtLogin 保留发布 + `credential` not-pub；generic update 可写 credential 的残余风险由 action-auth 承担。

## Closure

Status Note: 敏感表服务面收口完成：两处字段暴露缺口补 `not-pub`；四个裸 CrudBizModel 安全表标 `no-service`；`CrudBizModel` 内建只读开关并应用于审计日志。MFA/凭证既有收口契约保持。
Completed: 2026-10-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore）
- Audit Session: `ses_eb6caa7a9ffeb0fOt7ObQPoJ4F`
- Evidence:
  - not-pub：`_NopCredential.xmeta`(data)/`_NopAuthExtLogin.xmeta`(credential) `published="false"`（PASS）
  - no-service：`_NopAuthLoginAttempt/_NopAuthRateLimitCounter/_NopOauthAuthorization/_NopOauthAuthorizationConsent.xmeta` root tagSet 含 `no-service`；MFA/凭证/ExtLogin 不含（PASS）
  - 只读：`CrudBizModel` 内建 `isReadOnly()`（显式优先，否则读 `objMeta` 的 `read-only` tag）+ 15/15 mutation 守卫；`NopAuthOpLog` ORM tagSet 含 `read-only`，生成 `_NopAuthOpLog.xmeta` root tagSet 携带（PASS）
  - 回归：nop-biz 129、nop-auth-service 464（3 skip）、nop-credential-service 216、oauth-meta+web 4 全绿（PASS）
  - MFA 可见但拒绝契约不回归：`TestMfaCrudLockdownE2E` 4 passed（PASS）
  - Anti-Hollow：readOnly 守卫在真实 `CrudBizModel` 写路径入口，空壳检查通过；审计 R1 发现的 B1（M2M 未覆写）已修复——全部 15 mutation 均守卫，`TestReadOnlyCrudBizModel` 覆盖含 M2M（PASS）
- Follow-up: 见 Deferred（Session 只读化、MFA/凭证读出口、页面/菜单清理）。

Follow-up:

- Non-blocking：Session 只读化（需产品裁决）、MFA/凭证读出口收紧、页面/菜单清理。
