# A2-followup-1 MFA 敏感数据治理族——通用 CRUD 写路径收口（A2 successor-A）

> Plan Status: completed
> Mission: nop-credential-mfa
> Work Item: A2-followup-1
> Last Reviewed: 2026-08-19
> Source: `ai-dev/audits/2026-08/2026-08-18-1244-deep-audit-nop-auth/adjudication.md`（successor-A 裁决）+ `summary.md` findings 总表 + D1/D3/D5/D6 detail 报告；roadmap `ai-dev/backlog/nop-credential-mfa-roadmap.md`（A2-followup-1 工作项）
> Related: `ai-dev/plans/2026-08-18-0904-1-mfa-phase2-security-audit-a2.md`（A2-audit，本 plan 裁决输入的产出者）

## Purpose

落地 A2-audit 裁决 **successor-A（MFA 敏感数据治理族）** 的全部 8 项 finding：把 MFA 敏感数据的写路径全部收口到"带状态机校验/权限校验/审计"的专项入口，消除"持表级 mutation 权限即可绕过专项防护"的家族性 posture 缺口，并补齐验证语义（TOTP 绑定/解绑失败上限、恢复码条件写、proof 失败审计）与敏感面脱敏。

## Current Baseline

以下锚点为 2026-08-18 live 核对产物（与 A2-audit 报告锚点同日；行号允许执行期小幅漂移，以方法名/语义定位为准）：

- **五张 MFA 敏感表的 BizModel 均为裸 CrudBizModel（15 行、零 override、已注册 bean）**，暴露继承的 save/update/delete/batchDelete/updateByQuery/deleteByQuery 等全部 mutation：
  - `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthMfaSettingBizModel.java`
  - `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthMfaCredentialBizModel.java`
  - `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthMfaTrustedDeviceBizModel.java`
  - `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthRoleMfaPolicyBizModel.java`
  - `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthMfaRecoveryCodeBizModel.java`
  - 这些 Java 文件**不是** `_` 前缀生成物，是 codegen 脚手架后即可手改的定制点（`NopAuthUserBizModel` 同目录 1400+ 行先例）；xmeta 的 `_` 前缀文件由 ORM 生成，源级定制点是同目录非 `_` 保留文件（如 `NopAuthMfaSetting.xmeta`）
- **可达后果（A2 findings）**：
  - D5-F1（P2）：`NopAuthRoleMfaPolicy__save` 绕过 `NopAuthRoleBizModel.saveMfaPolicy/removeMfaPolicy` 的 requireAdmin + minMfaLevel 1/3 校验 + 审计；`NopAuthMfaSetting__update` 绕过绑定状态机与 proof 前置；`NopAuthMfaCredential__delete` 无 last-credential 守卫
  - D6-1（P2）：`NopAuthMfaTrustedDevice__save` 可对任意 userId 插入离线可算的 deviceHash + 任意 expireAt → 注入 30 天登录级 MFA 豁免，绕过 `MfaTrustedDeviceManager` 登记不变式/满员判定/五审计事件
  - D3-F3（P2）：`NopAuthMfaRecoveryCode` 的 codeHash/used 可写（`NopAuthMfaRecoveryCodeInputBean` 有 setter，xmeta insertable/updatable=true，codeHash 仅 published=false）→ 植入自算恢复码 / 复活已用码
- **配套暴露面**：`nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/auth/_nop-auth.action-auth.xml` 已声明五表 query/mutation 资源（约 :83-136 起）；`nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/` 下五表管理页存在（含新增/编辑/删除按钮的 AMIS 页面）
- **D1-1（P2）**：`confirmMfa`/`unbindMfa` 的 TOTP 分支无失败计数上限（`MfaFactorVerifier.verify` totp 分支无计数，失败仅抛 MFA_FAIL 可无限重试）；对照面：SMS/EMAIL 码 store 内部 max-attempts=5、webauthn 有 `incrWebauthnFailCountOrDiscard`、登录级有 `incrFailCountOrDiscard`（5 次上限，`NopAuthConfigs` CFG_AUTH_MFA_MAX_ATTEMPTS）
- **D3-F2（P2）**：`LoginServiceImpl.verifyRecoveryCode`（约 :686-704）used 置位为无条件 read-modify-write（无 `WHERE USED=0` 条件写）→ 并发双 verify 同码双过；仓内条件写先例：`DbMfaChallengeStore.markVerified`（`WHERE VERIFIED_AT IS NULL` + affected-row）、signCount 条件 UPDATE（`MfaFactorVerifier`）
- **D1-3（P3）**：`LoginApiBizModel.verifyChannelProof` 两处 MISMATCH 分支（约 :418-420/:436-438）抛错前无审计事件——W13 裁定 8 声明事件面 `mfa:channel-proof-sent|verified|fail`，live 仅 `sent`/`verified` 落库，`fail` 缺失
- **D1-7（P3）**：`NopAuthMfaSetting` 通用 CRUD 查询面返回未脱敏 phone（biz 面 `getMfaStatus` 有 `maskPhone`，ORM phone 列 `nop-auth/model/nop-auth.orm.xml` 约 :1081 无 masked 标签）
- **W12 路由项 3（P2 家族）**：`NopAuthUser` 无专用 changePhone/changeEmail mutation，EMAIL/PHONE 修改走继承 CrudBizModel 通用 save/update（方法级 @MfaRequired 无法覆盖共享基类动作）；`NopAuthUserBizModel.defaultPrepareSave` 钩子在位（约 :1426）可作拦截点
- **收紧先例（仓内）**：`NopCredentialBizModel.save` override 显式拒绝（"标准 save 已禁用"强制走 `saveCredential`，`nop-credential/nop-credential-service/.../entity/NopCredentialBizModel.java` 约 :123-134）；W11 Part A 六旁路动作禁用（update/batchDelete/updateByQuery/deleteByQuery/copyForNew 等）
- **测试基线**：nop-auth-service(+sso) 363 tests 0 failures（2026-08-18 W16-impl-ext 收口口径）、nop-credential-service 209 tests 0 failures（本 plan 的回归基线）

## Goals

1. 五张 MFA 敏感表（Setting/Credential/TrustedDevice/RoleMfaPolicy/RecoveryCode）+ 边界裁定纳入的同族瞬态码表的通用 mutation 通道全部收紧：写路径仅存专项入口（bind/confirm/unbind 族、`saveMfaPolicy/removeMfaPolicy`、`regenerateRecoveryCodes`、`MfaTrustedDeviceManager`），或表内显式保留的管理端动作（TrustedDevice delete 经 manager）
2. TrustedDevice 伪造豁免行路径结构性关闭（D6-1）；恢复码植入/复活路径关闭（D3-F3）
3. `verifyRecoveryCode` used 置位改条件写（D3-F2），并发双花恰一次成功
4. `confirmMfa`/`unbindMfa` 的 TOTP 分支引入失败计数上限（D1-1，缺省 5 次，配置化；成功清零；超限后 pending 路径作废 bindToken / enabled 路径进入冷却窗口）
5. `verifyChannelProof` 失败路径补审计事件 `mfa:channel-proof-fail`（D1-3）
6. `NopAuthMfaSetting` 通用查询面 phone 脱敏（D1-7）
7. `NopAuthUser` phone/email 经通用 CRUD 的非 admin 修改被显式拒绝（W12 路由项 3），admin 管理面保持可用
8. 一期/二期既有契约零回归（登录链、操作级 MFA、角色策略、可信设备、webauthn 全链）

## Non-Goals

- **不新增**用户自助 changePhone/changeEmail 专用正门端点（含 proof/MFA ceremony 的自助联系方式变更属新 feature，另行立项；本 plan 只关闭旁门并保留 admin 管理面）
- 不做 successor-B 范围项：removeWebauthnCredential 加 @MfaRequired（路由项 1）、mfaVerifyAsync scene 校验（D2-F2）、add-key-while-enabled 端点、@MfaRequired 误标构建期 fail-fast（D5-F3）——见 `2026-08-18-1924-2-*` plan
- 不重开 A2 adjudication.md 已终局裁定的 deferred/watch-only 项（D1-5/D1-6/D2-F3/D2-F4/D2-F5/D3-F4/D3-F5/D3-F6/D3-F7/D4-*/D5-F4/D5-F5/D6-2/D6-3 等，均带 Why-Not-Blocking，所有权在裁决表）
- 不做恢复码熵提升（D3-F5）、EXPIRE_AT 死列清理（D3-F6）——optimization deferred 维持
- 不做表结构大改；ORM 源变更仅限 D1-1 计数持久化列（若裁定增列，见 Phase 2 Decision）与 D1-7 tagSet 增量（见 Phase 3 Decision）——均为 model-first 单点增量
- 不做租户变体面（`NopAuthUser_tenant`）之外的租户能力扩展（平台无租户隔离裁定维持）

## Scope

> **规模裁定（显式）**：本 plan 触面约 25-35 文件（8 BizModel override + 验证/拦截点 + xmeta/ORM/DDL + 页面 + 测试），超出 roadmap 单 plan 常规规模（5-15 文件/200-500 行/1-4 phases）。**裁定不拆分**：8 项 finding 同属一个治理机制家族（通用 CRUD 通道对敏感数据的无差别可写性），同族边界裁定与拒绝形态裁定不可跨 plan 碎片化，逐表 override 为机械同构工作（W14 显式超规模裁定先例）。

### In Scope

- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/` 下 MFA 相关 BizModel（五表 + 边界裁定纳入的码表）的 mutation 收紧 override
- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java`（verifyRecoveryCode 条件写）
- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java`（proof 失败审计）
- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java`（confirmMfa/unbindMfa TOTP 失败计数挂点 + 联系方式拦截）
- `nop-auth/model/nop-auth.orm.xml`（仅当 Decision 裁定需要：setting 计数列 / phone tagSet `not-pub`——model-first 单点，不手改 codegen 生成物）+ 增量 DDL（按 `deploy/sql/{mysql,oracle,postgresql}/_add_*.sql` 仓内惯例，W14/W15 先例）
- `nop-auth/nop-auth-meta/src/main/resources/_vfs/nop/auth/model/NopAuthMfaSetting/NopAuthMfaSetting.xmeta` 保留文件（D1-7 `published=false` 兜底/同步——文件既有兜底先例）
- `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/` 受影响表的管理页面（mutation 按钮收敛为只读/保留白名单动作）+ `_nop-auth.action-auth.xml`（若资源声明需同步）
- `NopAuthConfigs`（新配置组：TOTP 绑定/解绑失败上限与冷却窗口）
- 测试：收紧面负例（mutation 拒绝）、条件写并发、失败计数边界、脱敏断言、联系方式拦截、既有回归
- 文档：`docs-for-ai/03-modules/nop-auth.md` 敏感数据治理章节 + 设计 `ai-dev/design/nop-auth/02-mfa-phase2-design.md` successor-A 落地裁定回写

### Out Of Scope

- successor-B 全部范围（见 Non-Goals 第二条）
- A2 deferred/watch-only 既有裁决项
- 用户新的自助联系方式变更正门设计
- nop-credential 侧代码变更（本 plan 仅回归验证其不受影响）

## Execution Plan

执行顺序：本 plan（N=1）先于 `2026-08-18-1924-2-*`（N=2）。两 plan 无硬依赖，但共同触碰 `NopAuthUserBizModel.java` 与 nop-auth 测试树，串行执行避免冲突。

### Phase 1 - 通用 CRUD 写路径收口（D5-F1 / D6-1 / D3-F3 + 同族边界裁定）

Status: completed
Targets: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuth{MfaSetting,MfaCredential,MfaTrustedDevice,RoleMfaPolicy,MfaRecoveryCode}BizModel.java`（+边界裁定纳入的 `NopAuth{MfaChallenge,SmsCode,EmailCode}BizModel.java`）、`nop-auth/nop-auth-web/.../pages/`、`_nop-auth.action-auth.xml`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（同族边界裁定）**：三张瞬态码表（`NopAuthMfaChallenge`/`NopAuthSmsCode`/`NopAuthEmailCode`）的裸 CrudBizModel 是否纳入同一收紧。背景：A2 findings 未单列它们，但其通用 mutation 通道允许持权限者直接植入验证码/challenge 行（植入已知 code 后走正常验证流 = 第一因子旁路原语），与 D5-F1/D6-1/D3-F3 同族同机制。**默认裁定：纳入**（码表行仅由 store 组件管理，无任何合法手工建行入口）；若 draft review 翻案为不纳入，必须在 plan 内记录理由并登记 roadmap 后继工作项
  - **落款（2026-08-18 执行）**：纳入。八表统一继承 `MfaSensitiveTableBizModel`；理由 = 同族同机制（码表植入 = 第一因子旁路原语）+ 无合法手工建行入口。
- [x] **Decision（拒绝形态）**：收紧动作的显式失败形态——默认 `NopException` + `NopAuthErrors` 新增/复用错误码（对齐 AGENTS.md 两级错误策略：public API 面用 ErrorCode + `.param(...)`；`NopCredentialBizModel` 的 `UnsupportedOperationException` 先例属模块内部面，此处不默认沿用）；错误码文案英文、message 指引专项入口
  - **落款（2026-08-18 执行）**：`ERR_AUTH_MFA_CRUD_DISABLED`（`nop.err.auth.mfa-crud-disabled`，英文文案 + ARG_ACTION/ARG_BIZ_OBJ_NAME，message 指引 dedicated MFA management API）。
- [x] **Fix**：五表（+裁定纳入的码表）BizModel 对继承写动作（save/update/delete/batchDelete/updateByQuery/deleteByQuery/copyForNew 等，以 CrudBizModel 实际继承面为准，执行期经容器 biz schema 枚举核对无遗漏）逐个 override 显式拒绝
  - 落地：基类 `MfaSensitiveTableBizModel` 覆写 save/update/delete/batchDelete/batchUpdate/batchModify/saveOrUpdate/updateByQuery/deleteByQuery/copyForNew/recoverDeleted（比计划列举更全——含 batchUpdate/batchModify/recoverDeleted 直写/委托旁路）；多对多三动作无需覆写（实体无多对多 prop，`requireManyToManyPropMeta` fail-closed）；E2E 枚举断言钉定。
- [x] **Fix（TrustedDevice 管理端 carve-out）**：`delete` 保留为管理端动作——经 `MfaTrustedDeviceManager` 物理删除（带审计事件，事件语义对齐既有 revoke 族）+ 运行时 admin 校验（`requireAdmin` 先例）；其余 mutation 全禁。自助移除继续走既有 `NopAuthUserBizModel.removeTrustedDevice`（本人限定），不受影响
  - 落地：`NopAuthMfaTrustedDeviceBizModel.delete` 覆写（requireAdmin + `MfaTrustedDeviceManager.removeBySidForAdmin` 新方法：物理删除 + auditRevoke(reason=admin-removed)）。
- [x] **Fix（RoleMfaPolicy）**：全 mutation 禁用——唯一写路径收敛到 `NopAuthRoleBizModel.saveMfaPolicy/removeMfaPolicy`（requireAdmin + minMfaLevel 校验 + 审计已在位，零改动）
- [x] **Fix（Setting/Credential/RecoveryCode/码表）**：全 mutation 禁用，仅留查询面（findPage/get 等读动作）
- [x] **Fix（web 页面配套）**：受影响表的管理页按最终动作面调整（移除新增/编辑按钮；TrustedDevice 页保留删除按钮对接管理端 delete）；页面不残存调用已禁用动作的入口
  - 落地：八表 retention view.xml（`x:override="remove"` 移除 listActions/rowActions/add/update simples/add/edit forms）；action-auth.xml 保持不变（mutation 资源仅控 UI 可见性，服务端拒绝为防线；TrustedDevice admin delete 仍需 mutation 权限）。
- [x] **Proof**：执行期 grep 五表（+码表）通用 mutation 的既有测试调用——若有测试依赖通用写路径，随裁定改写为专项路径或显式拒绝断言，不得静默删除
  - 结果：grep 零命中（测试树/`_cases/` 无八表通用 mutation 调用）；新增 `TestMfaCrudLockdownE2E` 4 用例显式拒绝断言。

Exit Criteria:

- [x] 容器级/E2E 测试：对每张收紧表，至少一个用例从 GraphQL mutation 入口断言写动作被显式拒绝（错误码断言，非静默失败）；TrustedDevice 管理端 delete 用例断言：经 manager 物理删除 + 审计事件落库 + 非 admin 被拒
  - `TestMfaCrudLockdownE2E.testAllGuardedTablesRejectGenericSave`（八表 save 错误码断言）+ `testSettingInheritedMutationSurfaceAllRejected`（全动作面）+ `testTrustedDeviceGenericMutationsRejectedButAdminDeleteCarveOut`（非 admin requireAdmin 拒 + GraphQL 拒 + admin 物理删除 + admin-removed 审计断言）。
- [x] 测试断言 RoleMfaPolicy 专项写路径（`NopAuthRole__saveMfaPolicy`/`removeMfaPolicy`）在收紧后行为不变（requireAdmin/校验/审计面回归）
  - `testRoleMfaPolicyDedicatedPathUnchanged`（非 admin 拒/建行/非法 minMfaLevel 拒/删行（逻辑删除）/复活路径）。
- [x] **接线验证**：收紧后 `MfaTrustedDeviceManager` 的登记/豁免/撤销链路回归全绿（既有 `TestTrustedDeviceE2E` 11 用例零修改通过）；`regenerateRecoveryCodes`/bind/confirm/unbind 族既有测试零修改通过（专项入口不受收紧影响）
  - 全量 `./mvnw test -pl nop-auth/nop-auth-service` 367/0（含 TestTrustedDeviceE2E 11 用例与 bind/confirm/unbind 族全部既有测试零修改）。
- [x] **无静默跳过**：被禁动作的失败是显式错误（有错误码/异常），不是空实现或 continue；已禁动作在 biz schema 中仍可见但调用即拒（或经裁定从 schema 移除，二选一并在测试断言钉定所选形态）
  - 形态 = schema 可见 + 调用即拒（`TestMfaCrudLockdownE2E` 从 GraphQL 入口断言错误码）。
- [x] 同族边界裁定与拒绝形态裁定的结论已写入本 plan（Decision 落款）与设计回写节
  - 落款见上；设计回写节见 Phase 4（`02-mfa-phase2-design.md` successor-A 裁定节统一回写，含两项裁定）。
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` 全绿（基线 363，新增用例计入）
  - 367/0（363 + 4 新增）。
- [x] owner docs（nop-auth.md 治理章节）已更新：表写路径收口清单 + 各表唯一合法写入口
  - `docs-for-ai/03-modules/nop-auth.md` 新增"MFA 敏感数据治理（通用 CRUD 写路径收口，A2-followup-1）"章节。
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 验证语义加固（D1-1 / D3-F2 / D1-3）

Status: completed
Targets: `LoginServiceImpl.java`、`NopAuthUserBizModel.java`、`NopAuthConfigs.java`、（若裁定）`nop-auth/model/nop-auth.orm.xml` + 三方言 DDL

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（D1-1 计数载体）**：TOTP 绑定/解绑失败计数的持久化载体——**默认裁定：setting 行持久化计数**（model-first 增列 + 三方言 DDL，跨副本一致，对齐 W12 challenge 表增列先例）；备选：challenge-store incrFailCount 语义复用（若裁定备选须论证不污染 challenge 生命周期语义）。计数维度 = 按 setting（即 userId）
  - **落款（2026-08-19 执行）**：setting 行持久化列 `TOTP_FAIL_COUNT`/`TOTP_FAIL_AT`（ORM 增列 + codegen 再生 + `_add_mfa_totp_lockout_nop-auth.sql` 三方言）。执行期补充裁定：**计数递增必须在 REQUIRES_NEW 独立事务先行落库**——@BizMutation 外层事务随 MFA_FAIL 抛出回滚，否则计数永不持久（生产 GraphQL 路径空壳；`TestGraphQLTransaction.testRollback` 回滚语义为证据）。
- [x] **Decision（超限语义）**：达上限后的行为——pending 路径（confirmMfa）：作废 bindToken（后续 confirm 报 `ERR_AUTH_MFA_BIND_EXPIRED`，用户重新 bindMfa）；enabled 路径（unbindMfa）：进入冷却窗口（新配置项，缺省 300s，窗口内 unbind 的因子验证直接拒绝）。上限值进 `NopAuthConfigs` 配置组，缺省 5（对齐登录级）
  - **落款（2026-08-19 执行）**：`CFG_AUTH_MFA_TOTP_VERIFY_MAX_FAILS`=5 / `CFG_AUTH_MFA_TOTP_COOLDOWN_SECONDS`=300。执行期补充裁定：冷却窗口内**重新 bindMfa 的新 bindToken 同样被冷却门拒绝**（防 bind/confirm 循环无限获取新 5 次——上限语义按用户而非按 bindToken）。
- [x] **Fix**：`confirmMfa`/`unbindMfa` 的 TOTP 分支失败时递增计数；成功验证清零；SMS/EMAIL/webauthn 分支不引入新计数（既有 store/challenge 计数已覆盖——执行期以断言或注释钉定该边界，防误扩）
  - 边界钉定：`TestMfaVerificationHardeningE2E.testSmsFailuresDoNotTripTotpLockout`（SMS 失败不递增 TOTP 计数、不进冷却）。
- [x] **Fix（D3-F2）**：`verifyRecoveryCode` used 置位改条件写（`SET USED=1, USED_AT WHERE SID AND USED=0` 语义，affected=0 → 按已用码路径处理：对外错误码保持 `ERR_AUTH_MFA_FAIL` 统一面）；regenerate 与并发 verify 的竞态随条件写闭合（被删行条件写 affected=0 → 失败）
- [x] **Fix（D1-3）**：`LoginApiBizModel.verifyChannelProof` 两处 MISMATCH 分支抛错前补 proof 失败审计事件（事件名对齐 W13 裁定 8 声明的 `fail` 事件；审计字段含 maskedTarget/userId，不含明文联系方式）
- [x] **Proof**：同族失败分支核对——`NopAuthUserBizModel.requireChannelProof` 内 `checkProofRateLimit`/`checkEmailRateLimit` 限流拒绝分支与 channel-disabled/store-missing 拒绝分支若存在"失败但无审计"情况，一并补齐（注：`verifyChannelProof` 端点仅存在于 `LoginApiBizModel`，`NopAuthUserBizModel` 侧无同名发送/验证方法——发送与限流逻辑内联于 `requireChannelProof`；W15"双通道"指 phone/email 通道而非双验证路径）
  - 补齐：发送侧拒绝分支补 `mfa:channel-proof-send-fail` 审计（限流/store-missing × phone/email + email channel-disabled，共 5 分支——phone 无 channel-disabled 门（CFG_AUTH_EMAIL_CODE_ENABLED 仅 email），reason 区分，target 脱敏）。

Exit Criteria:

- [x] 单元/组件测试：并发双 verify 同一恢复码恰一次成功（复刻 `TestRedisCodeStoreCasRace` 的并发断言模式）；已用码复活路径（改 used 回 0 的通用 CRUD 路径）已被 Phase 1 收紧后不可达
  - `TestRecoveryCodeConditionalWrite.testConcurrentDoubleVerifyExactlyOneSucceeds`（真实 4 线程 + H2：恰一个 VALID）+ `testSequentialVerifySemantics`/`testConditionalUpdateIsOneShot`；复活路径不可达由 Phase 1 `TestMfaCrudLockdownE2E` 钉定。
- [x] E2E 测试：TOTP confirmMfa 连续 5 次失败 → 第 6 次即使码正确也报 BIND_EXPIRED（bindToken 已作废）；unbindMfa 5 次失败 → 冷却窗口内拒绝、窗口过期后可重试；成功验证后计数清零
  - `testConfirmMfaTotpFailLimitInvalidatesBindToken`（max-fails=2 缩放：第 3 次正确码 BIND_EXPIRED + 冷却内 rebind 新 token 同拒）/ `testUnbindMfaTotpCooldownWindow`（COOLDOWN → 逻辑时钟等待窗口过期 → 重试成功）/ `testConfirmMfaSuccessResetsCounter`。
- [x] E2E 测试：channel-proof 码错误路径产生 `fail` 审计事件（断言事件名 + maskedTarget），正确路径 `verified` 事件不回归
  - `testVerifyChannelProofFailAuditedWithMaskedTarget`（fail 事件含脱敏 target、不含明文手机号；verified 不回归）。
- [x] 若走 ORM 变更（D1-1 增列或 D1-7 tagSet）：`nop-auth/model/nop-auth.orm.xml` 源编辑 + codegen 再生；不手改 codegen 生成物（`_gen/`、生成的 `_` 前缀类/xmeta）；新增增量 DDL 按 `nop-auth/deploy/sql/{mysql,oracle,postgresql}/_add_*.sql` 仓内惯例（W14/W15 先例）——仅当列结构变更，tagSet 类增量无需 DDL
  - ORM 源增列（propId 17/18）→ `./mvnw install -pl nop-auth/nop-auth-codegen` codegen 再生（`_gen/_NopAuthMfaSetting.java`、`_app.orm.xml`、`_NopAuthMfaSetting.xmeta`、`_create_*.sql` 均为生成物自动更新，未手改）；三方言 `_add_mfa_totp_lockout_nop-auth.sql` 齐备。
- [x] 一期回归锚点全绿：登录级失败计数（5 次 challenge 作废）、恢复码登录、TOTP 防重放窗口推进等既有断言零修改
  - 全量 375/0（TestOperationMfaE2E 登录级计数、TestMfaLoginE2E 恢复码登录、TOTP 防重放既有断言零修改通过）。
- [x] 设计回写节含 D1-1 计数载体/超限语义、D3-F2 条件写两项裁定；owner docs 同步（nop-auth.md 绑定/解绑失败上限说明）
  - 设计 §10.1 C/D 项；nop-auth.md 三段（TOTP 失败上限/恢复码条件写/proof 三事件面）。
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` 全绿
  - 375/0（+ sso 5/0）。
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 敏感面收敛（D1-7 + W12 路由项 3）

Status: completed
Targets: `nop-auth/model/nop-auth.orm.xml`（D1-7 主变更点：PHONE tagSet；D1-1 增列若裁定）+ `NopAuthMfaSetting.xmeta` 保留文件（兜底同步）、`NopAuthUserBizModel.java`、`NopAuthErrors.java`（若新错误码）、（视裁定）`NopAuthUser.xmeta` 保留文件

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（D1-7 脱敏载体）**：**默认裁定 = 结构性排除**——ORM 源 `NopAuthMfaSetting` PHONE 列 tagSet 增 `not-pub`（model-first 单点），保留 xmeta `NopAuthMfaSetting.xmeta` 同步/兜底 `published=false`（文件既有兜底先例）；通用查询面（findPage/get）输出**不含** phone 字段。⚠️ 防再犯注记：ORM `masked` 标签在本仓**仅作用于 SQL 日志参数脱敏**（消费面 `GenSqlHelper`/`SqlColumnName`），**不构成出参脱敏机制**——出参可见性由 `not-pub`→codegen `published=false` 链承载；secret 列先例为 `masked,var,not-pub` 组合。备选 = BizModel 层输出脱敏（`maskPhone` 模式扩展到 CRUD 读路径——字段保留、值为脱敏形态）。裁定标准：(a) biz 面 `getMfaStatus` 既有 maskPhone 行为零冲突、不双写漂移；(b) admin 管理面 phone 不可见可接受——默认可接受（MfaSetting 管理页 Phase 1 后为只读监控面；管理员经 `NopAuthUser` 管理面可见用户手机号）；(c) 服务内部 dao 实体读取不受影响（`published=false` 仅作用于 API 出参 schema，登录/发码链内部读路径零改动——执行期以 sendMfaCode 链核对）
  - **落款（2026-08-19 执行）**：结构性排除落地（ORM tagSet + xmeta 兜底双保险）；(a)/(b)/(c) 三标准逐项核对成立（getMfaStatus E2E 断言零回归；管理面只读 + admin 经 NopAuthUser 面可见；TestMfaLoginE2E 等 sendMfaCode 链全绿）。
- [x] **Fix（D1-7）**：按裁定落地；通用 CRUD 查询面（findPage/get）输出不含 phone 字段（结构性排除；若裁定备选形态则改为脱敏值输出）
- [x] **Decision（路由项 3 拦截形态）**：NopAuthUser phone/email 通用 CRUD 修改的拦截点与分级——默认裁定：在 `NopAuthUserBizModel` 的 save/update preparer 路径（`defaultPrepareSave` 钩子先例）检测 phone/email 字段值变更：**非 admin 调用方（含本人行修改）→ 显式拒绝**（新错误码或归一 `ERR_AUTH_INVALID_LOGIN_REQUEST`，英文文案指引"联系方式变更需管理员或专用流程"）；admin 调用方 → 保持可用（创建/修改用户管理面不破坏），并补联系方式变更审计事件（admin 改人 phone/email 留痕）。裁定标准补：与 D1-7 裁定形态不冲突（结构性排除后通用面写 phone 的拦截仍需独立成立——排除是读面、拦截是写面，正交）；备选形态（敏感列不可写/权限面收窄）若 review 翻案须记录取舍理由
  - **落款（2026-08-19 执行）**：preparer 双钩子（defaultPrepareSave + defaultPrepareUpdate）+ ORM 脏属性判定（同值提交不算变更）；非 admin（含本人）→ 新错误码 `ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED`；admin → 放行 + `user:contact-changed` 审计（targetUserId/字段清单/create|update 形态，不落明文新旧值）；**无登录态内部调用 → 放行**（W11 凭证归属两层防御先例延伸：批量导入/autotest 数据准备；本类无内部 save/update 调用方）。租户变体面 `NopAuthUser_tenant` 复用同 BizModel 同口径。
- [x] **Fix（路由项 3）**：按裁定落地拦截；执行期 grep `NopAuthUser` 通用 save/update 的全部既有调用方（含租户变体面与测试），确认 admin 管理流零破坏
  - grep 结果：主代码零内部调用方（登录/注册链均走 dao 直写）；测试调用面（TestXMetaPropDefaultValue/TestGraphQLLogger/TestLoginApi 等 autotest 用例）全绿——无登录态/无 contact 字段路径不受影响；admin 管理流 E2E 断言放行 + 审计。
- [x] **Proof**：受限会话 enrollment attack 链闭合验证——受限用户（非 admin）试图经通用 CRUD 改 phone 后再 bindSms 的组合路径在收紧后断链（E2E 断言拦截点在前）
  - `testRestrictedSessionContactChangeChainSevered`（受限门 RESTRICTED_SESSION 断链 + 非受限非 admin guard CONTACT_CHANGE_NOT_ALLOWED 断链 + 行未篡改）。

Exit Criteria:

- [x] 测试：通用查询面输出不含 phone 字段（schema/响应结构性断言，`TestNopAuthMfaSettingXmeta` 既有模式；若裁定备选形态则改为脱敏值逐字段断言）；`getMfaStatus` 既有脱敏输出零回归
  - `testMfaSettingGenericQueryStructurallyExcludesPhone`（findPage/get 选择 phone → `ERR_GRAPHQL_UNDEFINED_FIELD` schema 校验失败 + published 字段可查 + getMfaStatus maskPhone 输出零回归、不泄漏明文）。
- [x] E2E 测试：非 admin 经 `NopAuthUser__save`/`__update` 修改 phone/email（含本人行）被显式拒绝（错误码断言）；admin 修改成功且产生审计事件；admin 创建用户（含 phone/email）零回归；非联系字段的通用修改（如 nickname 类）行为不变
  - `testNonAdminContactChangeRejectedOnUpdateAndSave`（update phone/email + save 携 phone 三拒绝 + nickname 放行）+ `testAdminContactChangeAllowedAndAudited`（admin 改他人 phone + 创建含 phone 用户 + update/create 双形态审计断言）。
- [x] 若触碰 ORM 源：源编辑 + codegen 再生 + 增量 DDL（仅列结构变更时需要；tagSet 类增量无需）齐备；未手改 codegen 生成物
  - ORM tagSet 单点编辑 → dao/meta 两级 codegen 再生（`_app.orm.xml`/`_NopAuthMfaSetting.xmeta` 自动更新）；tagSet 类增量无 DDL（D1-1 增列 DDL 已在 Phase 2 齐备）。
- [x] 设计回写节含 D1-7 与路由项 3 两项裁定；owner docs（nop-auth.md "联系方式变更"治理说明）
  - 设计 §10.1 F/G 项；nop-auth.md D1-7 脱敏段 + 联系方式变更治理段。
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` 全绿
  - 379/0（+ sso 5/0）。
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 全量回归 + 文档收口 + closure

Status: completed
Targets: 全模块测试、`docs-for-ai/03-modules/nop-auth.md`、`ai-dev/design/nop-auth/02-mfa-phase2-design.md`（§十 扩展 successor-A 落地裁定节）、roadmap、daily log

- Item Types: `Fix | Proof | Follow-up`

- [x] `./mvnw test -pl nop-auth/nop-auth-service -am`（含 sso）全量回归；`./mvnw test -pl nop-credential/nop-credential-service -am` 回归（C1b 容器元数据断言等跨模块面不受影响）
  - nop-auth-service **379/0**（363 基线 + 16 新增）+ nop-auth-sso 5/0 + nop-credential-service **215/0**；另 mission 门 `./mvnw test -pl :nop-auth,:nop-ai-gateway,:nop-nosql -am -T 1C` 全绿（含 nop-auth-web 页面 delta 测试——view.xml 收敛只读的解析验证）。
- [x] 设计文档回写：`02-mfa-phase2-design.md` 新增 successor-A 落地裁定小节（CRUD 收口清单与 carve-outs / TOTP 失败计数语义 / 恢复码条件写 / 脱敏与联系方式裁定 / 同族边界裁定结论）
  - §10.1 A-G 七项（A 收口清单与 carve-outs / B 同族边界 / C TOTP 失败计数 / D 恢复码条件写 / E proof 审计 / F D1-7 脱敏 / G 联系方式拦截）。
- [x] `docs-for-ai/03-modules/nop-auth.md` 敏感数据治理章节收口（含"哪些表禁止通用写、唯一写入口"清单）
  - "MFA 敏感数据治理（通用 CRUD 写路径收口，A2-followup-1）"章节：八表唯一写入口清单表 + TOTP 失败上限/恢复码条件写/proof 三事件面/D1-7 脱敏/联系方式变更治理各段。
- [x] roadmap `ai-dev/backlog/nop-credential-mfa-roadmap.md` A2-followup-1 状态与 plan 链接更新
  - `planned` → `done`（收口摘要 + 回归数据）；Last updated 头部同步。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth --severity high` 退出码 0（无 NEW 高危空壳）
  - **0 NEW**：单条 high = `OAuthLoginServiceImpl:231`（nop-auth-sso，`UnsupportedOperationException` 占位）为 pre-existing——git stash HEAD 对照扫描证实同一 finding 存在于基线（W16-impl-ext closure 同款判定法）；本 plan 未触碰该文件。
- [x] 独立 fresh 子 agent closure audit（证据写入本 plan Closure 段）
  - fresh subagent `ses_fea35e0feffe5LCv7kKxu764rs`（2026-08-19）：7 区域全 PASS、0 Blocker / 0 Major / 4 Minor（未提交→收口即提交；roadmap/log 先行措辞→随本 Closure 落款同步为真；auditContactChange operation 硬编码→已修复为 create/update 区分；发送侧分支数措辞→已修正为 5 分支）；Anti-Hollow 结论 = Not hollow（详见 Closure 段）。

Exit Criteria:

- [x] 上述命令全部退出码 0 / 全绿
  - 测试命令全绿；scan-hollow 工具退出码 1 但 **0 NEW**（单条 high 为 pre-existing，git stash HEAD 对照扫描证实——W16-impl-ext closure 同款判定法）；check-doc-links --strict 0 errors。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] Closure 段已记录独立 audit 证据（Reviewer/task id + 逐 Gate 验证结果 + Anti-Hollow 结论）
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：本 section 与各 Phase Exit Criteria 全部 `[x]` 后才能将 Plan Status 改为 `completed`。关闭流程见 guide `When Closing The Plan`。

- [x] successor-A 全部 8 项 finding（D5-F1/D6-1/D3-F3/D1-7/D1-1/D3-F2/D1-3/W12 路由项 3）落地且有 focused 测试，或经显式裁定移出 scope（裁定记录在案）
  - D5-F1/D6-1/D3-F3：`MfaSensitiveTableBizModel` 收口 + `TestMfaCrudLockdownE2E`；D1-1：TOTP 失败计数 + `TestMfaVerificationHardeningE2E`；D3-F2：条件写 + `TestRecoveryCodeConditionalWrite`；D1-3：fail 审计 + 同测试断言；D1-7：结构性排除 + `TestMfaSensitiveSurfaceE2E`；W12 路由项 3：guardContactChange + 同测试。closure audit 逐项核对 PASS。
- [x] 同族边界裁定（瞬态码表）已落结论且无悬挂
  - 裁定 = 纳入（Phase 1 Decision 落款 + 设计 §10.1 B）；closure audit 确认八表全覆盖、继承动作面无未守卫写路径（save_update 经 saveOrUpdate 委托链命中覆写；多对三动作 fail-closed）。
- [x] 一期/二期契约零回归：nop-auth-service(+sso) 全量 0 failures、nop-credential-service 回归 0 failures
  - 379/0 + 5/0 + 215/0；mission 门 `:nop-auth,:nop-ai-gateway,:nop-nosql -am -T 1C` 全绿。
- [x] 无 in-scope live defect 被降级到 deferred / follow-up
  - Deferred But Adjudicated 节为空（执行期零延期项）；Non-Blocking Follow-ups 仅登记用户自助正门端点（Non-Goal 明示）与 A2 既有裁决项维持。
- [x] owner docs（nop-auth.md）与设计文档（02-mfa-phase2-design.md successor-A 裁定节）与 live 行为一致
  - closure audit Area 4 PASS（章节/清单/裁定 A-G 与 live 代码逐项一致）。
- [x] Anti-Hollow：收紧动作的"拒绝"在运行时真实可达（E2E 从 GraphQL 入口断言）；无空方法体/静默跳过作为实现
  - closure audit Area 5 PASS：REQUIRES_NEW 计数持久化经行为学证明（BIND_EXPIRED 断言依赖计数跨回滚存活）；4 线程并发双花恰一次；全部守卫 = 显式 throw 且 GraphQL 入口错误码断言。
- [x] 独立 fresh 子 agent closure audit 完成且证据写入 Closure 段
- [x] `./mvnw compile` + `./mvnw test -pl nop-auth/nop-auth-service -am` + `-pl nop-credential/nop-credential-service -am` 通过
- [x] checkstyle / 代码规范检查通过（import 分组 io.nop.* → 三方 → java.*）
  - 新增/修改文件 import 均按组序排列（静态导入最后）；checkstyle 非本仓模块级门禁（W16-impl-ext closure 同口径在案）。

## Deferred But Adjudicated

无预置项。执行期若出现需延期项，必须按 guide Anti-Slacking 规则落到本节并写明 Classification / Why Not Blocking Closure / Successor Required，否则视为未完成。

## Non-Blocking Follow-ups

- A2 adjudication.md deferred 表中与本 plan 无 ownership 关系的项（D1-6/D3-F5/D3-F6 等）维持原裁定，不在本 plan 重开
- 用户自助 changePhone/changeEmail 正门端点（含 proof ceremony）——若立项，roadmap 新工作项登记（本 plan 只关旁门）

## Closure

Status Note: successor-A 全部 8 项 finding（D5-F1/D6-1/D3-F3/D1-7/D1-1/D3-F2/D1-3/W12 路由项 3）落地并有 focused 测试；同族边界（三张瞬态码表）纳入收紧且无悬挂；四 Phase 全 completed；独立 fresh 子 agent closure audit 判定 CAN CLOSE（0 Blocker/0 Major）；一期/二期契约零回归（nop-auth-service 379/0 + sso 5/0 + nop-credential-service 215/0 + mission 门全绿）；owner docs/设计 §10.1/roadmap/daily log 四侧同步。plan 可关闭。
Completed: 2026-08-19

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh subagent（task `ses_fea35e0feffe5LCv7kKxu764rs`，general agent，2026-08-19；实现者未参与本轮审计）
- Evidence:
  - **Area 1 Phase 1（CRUD 收口）PASS**：`MfaSensitiveTableBizModel.java:55-153` 全 11 覆写（throw `ERR_AUTH_MFA_CRUD_DISABLED`，错误码 `NopAuthErrors.java:200`）；八 BizModel 继承核对；TrustedDevice carve-out（`NopAuthMfaTrustedDeviceBizModel.java:53-56` requireAdmin + `MfaTrustedDeviceManager.java:230-238` 物理删除 + auditRevoke("admin-removed")）；八 retention view.xml `x:override="remove"`（TrustedDevice 保留 row-delete 对接管理端）；`TestMfaCrudLockdownE2E` 4 用例 GraphQL 入口错误码断言。对抗面枚举：`save_update` 委托链命中覆写、多对三动作 fail-closed——无未守卫写路径。
  - **Area 2 Phase 2（验证语义）PASS**：ORM TOTP_FAIL_COUNT/TOTP_FAIL_AT（propId 17/18）+ 三方言 DDL + 双配置（5/300 缺省）；confirmMfa/unbindMfa TOTP 分支三钩子接线（:642/:652/:659/:726/:735/:741）；`incrTotpVerifyFail` REQUIRES_NEW（:1446-1470，fallback 为真实实体写非 no-op）；`verifyRecoveryCode` 条件写（:720-771，affected=0 → USED）；verifyChannelProof 两 MISMATCH 分支 fail 审计（:418-423/:439-443）+ 发送侧 5 分支 send-fail；`TestMfaVerificationHardeningE2E` 5 用例 + `TestRecoveryCodeConditionalWrite` 3 用例（4 线程并发恰一 VALID）。
  - **Area 3 Phase 3（敏感面）PASS**：ORM PHONE not-pub + retention/generated xmeta 双 published=false；`defaultPrepareUpdate` 覆写（:1652-1659）+ `guardContactChange`（:1674-1697，非 admin 拒/admin 审计/无登录态放行）；`TestMfaSensitiveSurfaceE2E` 4 用例（schema 结构断言 + getMfaStatus 零回归 + 双向拦截/放行 + 受限链断链）。
  - **Area 4 文档四侧 PASS**：nop-auth.md 治理章节（八表写入口清单）；设计 §10.1 A-G；roadmap `done`；daily log 四条目。
  - **Area 5 Anti-Hollow PASS（结论 Not hollow）**：(a) 覆写方法带 @BizMutation @Override，schema 可见调用即 throw，E2E 自 GraphQL RPC 入口断言错误码 + "无行落库"断言；(b) REQUIRES_NEW 计数持久化经行为学证明——BIND_EXPIRED 断言依赖计数与 bindToken 作废跨外层事务回滚存活；(c) 4 线程真实并发双花恰一次、败者 USED、终态 USED=1；(d) 触碰文件无空方法体/吞异常/占位返回（guardContactChange 早退 = 裁定行为分支，代码 + 设计 §10.1 G 双记录）。hollow scan 单条 high（OAuthLoginServiceImpl:231）独立确认为 pre-existing，0 NEW。
  - **Area 6 文本一致性 PASS（预期收口前态）**：Phase 1-3 completed 全 [x]；Phase 4 仅收口四项待落（即本段落款）。
  - **Area 7 测试抽跑 PASS**：新 16 用例全绿（16/0/0，BUILD SUCCESS）。
  - `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0（本 Closure 段落款后复跑确认，见下）。
  - Deferred 项分类检查：Deferred But Adjudicated 为空；无 in-scope live defect 降级。
  - 审计 Minor 4 项处置：①未提交→收口后即提交（本 plan 最后动作）；②roadmap/log 先行措辞→随本段落款成立；③auditContactChange operation 硬编码→已修复（create/update 区分）并复跑测试 4/0；④发送侧分支数措辞→已修正（5 分支，plan + 设计同步）。

Follow-up:

- 用户自助 changePhone/changeEmail 正门端点（含 proof/MFA ceremony）——Non-Goal 明示，若立项登记 roadmap 新工作项
- A2 adjudication deferred/watch-only 既有项维持原裁定（无重开）
- successor-B（`2026-08-18-1924-2-*`，N=2）按执行序独立推进，与本 plan 无未决依赖
