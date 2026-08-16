# W13-impl 角色级 MFA 强制策略引擎（策略模型 + 三层判定 + 受限会话 + 登记通道 proof + OAuth 绕过修复）

> Plan Status: active
> Mission: nop-credential-mfa
> Work Item: W13-impl（角色级 MFA 强制策略引擎）——MFA 二期组第二个 impl 工作项
> Last Reviewed: 2026-08-17（draft review 两轮：首轮 1 Blocker/2 Major/4 Minor 全部处置——OAuth substrate 预裁定/default 方法钉定/proof 票 token 往返裁定，复审 READY；锚点全量复核 PASS）
> Source: `ai-dev/design/nop-auth/02-mfa-phase2-design.md` §四 全部（4.1-4.5）+ §3.3 拦截器受限分支（W12 预留接缝）+ §八 W13-impl 映射；roadmap W13-impl 条目
> Related: W12-design `2026-08-14-2012-2-mfa-phase2-design.md`（设计收口 plan）；W12-impl `2026-08-16-2321-2-mfa-operation-level-stepup.md`（拦截器/场景化 store/MfaFactorVerifier 基线 + W13 接缝登记于其 Non-Blocking Follow-ups）；W4/W5/W6/W8 一期 MFA plan 链

## Purpose

按设计 §四落地角色级强制策略：新实体 `NopAuthRoleMfaPolicy`（1:1 角色按需建行，minMfaLevel 持有约束 + allowTrustedDevice）+ `RoleMfaPolicyEvaluator`（多角色 max 合并 + 复合结果）；`checkMfaRequired` 增量插入第三态 `MFA_RESTRICTED`（一期分支原位原序保留）；受限签发经 `completeLogin` 变体（标志先设后存）+ `IUserContext.mfaRestricted` 持久化（两处序列化白名单同步，Dao-cache 路径 fail-open 防护）；受限会话 executor 拦截分支（W12 接缝：前置于 `hasMfaRequired` 早退、对所有 operation 生效、白名单 mutation + query + publicAccess mutation 放行）；登记通道 OTP proof（防 enrollment attack，scene=channel-proof 票 + SmsCodeStore）；`confirmMfa` 策略校验防因子降级；一期 OAuth 遗留绕过（`OAuthLoginServiceImpl` 不走任何 MFA 判定）一并接入；错误码 ×4；`LoginResult`/`ScanLoginResult` 可选 `mfaRestricted` 字段。无策略行 = 一期全部行为（零回归红线）。

## 规模裁定（设计 §八 拆分提示的 plan-first 裁定，起草时执行）

本 plan 触碰面估算约 25-35 文件（nop-api-core 1 + nop-biz-auth-api 1 + nop-biz-auth-core 2 + nop-graphql-core 1 + nop-auth 12+ + nop-auth-sso 1 + nop-ai-gateway 1 + DDL 3 + 测试 ~10），超 roadmap 单 plan 参考规模（5-15 文件）。**裁定不拆分**：W13-impl 是设计 §八 指派给单一工作项的完整交付面——"受限会话"这一用户可用功能在 Phase 3 末端才第一次成立（策略模型 → 受限签发 → 拦截引导三段缺一即空壳：只有策略无签发 = 评估结果无处消费；只有签发无拦截 = 受限会话无约束力 = fail-open）；Phase 间严格顺序依赖。拆分出的任何子集都无法独立通过端到端验证（guide Practical Rule：多 slice 全部完成后 feature 才成立 → 同一 owner plan）。执行中若单 Phase 严重超载，按 guide 拆分规则另行 plan-first 裁定。

## Current Baseline

（2026-08-17 live repo 核对，独立 explore agent 复核锚点）

- **W12-impl 已交付且 `completed`**：`@MfaRequired` + 元数据四触点传播（builder/deepClone/mergeField 两分支/mergeBizModel）；`IOperationMfaChecker` SPI（`nop-biz-auth-api`，签名 `check(operationName, IUserContext, requestHeaders)`）；`GraphQLEngine` 可选注入 + `IGraphQLExecutionContext` 透出（`getOperationMfaChecker()` live:46）；`MfaChallengeStore` 场景化（scene/payload/verifiedAt + `create` 场景重载 live:51-52 + `markVerified` live:83 三实现原子性）；`MfaFactorVerifier` 组件；`mfaVerifyOperation` 端点；首批五动作标注；`nop.auth.operation-mfa.*` 配置组（`NopAuthConfigs` live:113-121）；`TestOperationMfaE2E` 等 9 个测试类基线。**W13 接缝已登记**（W12 plan Non-Blocking Follow-ups："W13 受限会话白名单前置分支，本 plan 拦截判定入口已界定接缝"）。
- **拦截链 live 结构（关键事实，与设计 §3.3 伪代码的两处落点差异）**：`GraphQLExecutor.checkOperationMfa`（live:191-208）逐 field 检查，**`mfaRequiredMeta == null` 的早退 continue 在 executor 侧（live:204-205），不在 checker 内**——受限会话分支"对所有 operation 生效"必须同时触达：① executor 侧该早退之前（或等价重构：将受限判定并入 checker 调用路径，使未标注方法也进入受限判定）；② `OperationMfaCheckerImpl.check`（live:83-131）内 enabled 门（live:86-87）之前（类自身 javadoc live:57-58 已预留该插入位）。执行点：`GraphQLExecutor.executeAsync` live:150 与 `executeOneAsync`（RPC 路径）两检查点。
- **`checkMfaRequired`**（`LoginServiceImpl` live:725-742）：全局开关 live:726-727 → store null live:728-729 → setting null/非 enabled live:730-732 → mfaType 空 live:733-735 → 因子等同（PHONE_SMS+SMS）live:737-738 → challenge 创建 live:739-741。设计 §4.3 伪代码：策略评估插入在 store null 检查**之后**、setting 装载之前；`!enabled or factorLevel < policy` → 新第三态（不建 challenge）。
- **`completeLogin`**（live:398-416）：`saveSession` live:408、`userContextCache.saveUserContextAsync` live:414——`mfaRestricted` 标志必须在 live:414 之前写入 userContext。
- **`mfaRestricted` 全仓库不存在**（源码零命中，仅 ai-dev 文档）——greenfield 核实。
- **持久化白名单两触点（fail-open 风险，live 核定且当前无测试覆盖）**：`UserContextImpl.serializeToJson`（`nop-biz-auth-core/.../login/UserContextImpl.java` live:45-66，手工白名单 17 字段）；`DaoUserContextCache`（`nop-auth-service/.../login/DaoUserContextCache.java`）——`getUserContextAsync` live:46-85（每请求从 `NopAuthSession.cacheData` 反序列化全新对象，`JsonTool.parse` live:68 + `BeanTool.setProperties` live:70）、`saveUserContextAsync` live:101-132（手工白名单 map live:111-123 + stringify live:127）。**live 无任何 Dao-cache 序列化 round-trip 测试**（既有 cache 相关测试均用 `LocalUserContextCache` 对象引用语义，不会暴露白名单缺漏）——本 plan 必须新增 Dao-cache 路径测试。
- **`IUserContext`**：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/auth/IUserContext.java`（跨模块公共 API，`getSessionId()` live:32）。
- **响应契约**：`LoginResult`（`nop-biz-auth-api/.../messages/LoginResult.java`，133 行，MFA 信号目前仅 accessCode 可选字段 + 异常 errorParams 通道，无 mfaRequired/challengeToken 字段）；`ScanLoginResult`（`nop-ai/nop-ai-gateway/.../login/ScanLoginResult.java`，已有 mfaRequired/challengeToken/mfaType/loginType 四可选字段——W6 先例，本 plan 增 mfaRestricted 与之同型）。
- **OAuth 一期遗留绕过（live 核实）**：`nop-auth-sso/.../login/OAuthLoginServiceImpl.java`（249 行）`loginAsync` live:70-79 自行 `buildUserContext`（live:136-158，roles 取 realm_access，userId=userName，OAuth token 存 attrs live:146-147）+ `userContextCache.saveUserContextAsync` live:77 直接签发——**零 MFA 逻辑、不经 completeLogin**（extends AbstractLoginService）。**依赖面事实（draft review 核定）**：nop-auth-sso classpath 不含 nop-auth-service/nop-auth-dao——`LoginServiceImpl.checkMfaRequired`/`completeLogin`（protected，nop-auth-service）与 `RoleMfaPolicyEvaluator`/`NopAuthMfaSetting`/`NopAuthRoleMfaPolicy`（nop-auth-dao/service）对该模块**不可达**；且 `DaoUserContextCache.saveUserContextAsync` 在无 `NopAuthSession` 行时静默 no-op（live:107-109）——OAuth 接入的 SPI 落点与持久化基座必须预先裁定（见 Phase 2 Decision 项）。
- **角色快照口径**：`LoginServiceImpl.buildUserContext` live:772-816——直接角色 + childRoleIds live:790-797、隐式 user 角色及其继承链合并 live:799-804、`setRoles` live:806。策略合并必须与该口径一致。
- **策略管理面先例**：`NopAuthRoleBizModel`（134 行，mutations：removeRoleUsers/addRoleUsers/updateRoleResources + `checkAllowAssignRole` live:104-111 运行时角色校验先例）；`requireAdmin` 先例在 `NopAuthUserBizModel` live:487-498（`resetUserMfa` live:373-398 调用于 live:378）。
- **绑定链锚点**（`NopAuthUserBizModel`）：`bindMfa` live:139-169（bindTotp 171-191/bindSms 193-211）、`confirmMfa` live:249-286、`unbindMfa` live:293-316（@MfaRequired live:296，@MfaRequired 五动作之一）、`getMfaStatus` live:342-357。
- **ORM**：`nop-auth/model/nop-auth.orm.xml`（1290 行，21 实体；末实体 `NopAuthSmsCode` live:1248-1288）——**`NopAuthRoleMfaPolicy` 不存在**；`NopAuthMfaSetting` live:1060-1129（status pending|enabled|disabled + mfaType 单值）。DDL 增量先例：`nop-auth/deploy/sql/{mysql,postgresql,oracle}/_add_mfa_challenge_scene_nop-auth.sql`（W12 产物）。
- **错误码/配置**：`NopAuthErrors`（122 行）MFA/SMS 块 live:76-121，末条 `ERR_AUTH_MFA_RECOVERY_CODE_USED` live:120-121——4 新错误码（`ERR_AUTH_MFA_RESTRICTED_SESSION`/`ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED`/`ERR_AUTH_MFA_NO_RECOVERY_CHANNEL`/`ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK`）追加于此。`NopAuthConfigs` MFA 组 live:79-111。
- **store 契约**：`SmsCodeStore`（`nop-biz-auth-core/.../mfa/store/SmsCodeStore.java`：send live:29 / verify live:34 / consume live:39）；`MfaChallengeStore` 场景重载与 markVerified 已在（W12）。
- **测试基线**：`TestMfaLoginE2E`/`TestMfaUserSelfService`/`TestOperationMfaE2E`/`TestScanLoginMfa` 均在 `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/`；pre-existing flake 事实见 W12 plan Non-Blocking Follow-ups（missing-tenant-id 顺序 flake，baseline 复现证据在案，单独运行全绿）。
- **审计机制**：W12 裁定 `@BizAudit` 为装饰性注解——审计统一走 `IAuditService.saveAudit(AuditRequest)`。

## Goals

- `NopAuthRoleMfaPolicy` ORM 实体（roleId 唯一 + minMfaLevel 1/2/3 + allowTrustedDevice 缺省 true + 通用审计字段；model-first → codegen → `_create_` 再生成 + 三方言 `_add_` 增量，循 W12 先例）+ `NopAuthRoleBizModel.saveMfaPolicy`/`removeMfaPolicy`（requireAdmin 运行时校验先例 + saveAudit 审计；策略是独立实体非角色字段，删行即撤策略、无 status 双态）。
- `RoleMfaPolicyEvaluator`：角色快照口径 = buildUserContext（直接角色 + childRoleIds + 隐式 user 角色继承链）；多角色 `max(minMfaLevel)` 合并 + `allowTrustedDevice` AND 合并（任一 false 即禁）；`factorLevel` 强度表全量落地（sms/email=1、totp=2、webauthn=3 常量表随本 plan 落地——W14/W15 仅核对；未知 mfaType fail-closed 视为 0）。
- `checkMfaRequired` 第三态：策略评估插入 store null 检查后（live:729 之后）、setting 装载前；`policy>0 且 (!enabled 或 factorLevel<policy)` → 返回受限决策（不建 challenge，设计结论 9）；一期六分支原位原序逐字节保留（无策略部署不可达第三态）。
- 受限签发：`completeLogin` 受限变体（`restricted=true`），标志在 `saveUserContextAsync`（live:414）之前写入；`loginAsync`/`createSessionForUserAsync` 调用侧消费第三态；一期三处调用点行为不变。
- `IUserContext.mfaRestricted`（可选属性，**default 方法实现**——缺省 false、外部实现类零破坏、老消费方零感知）+ `UserContextImpl` 字段 + `serializeToJson` 白名单（live:45-66）+ `DaoUserContextCache.saveUserContextAsync` 白名单（live:111-123）同步 + **Dao-cache round-trip 测试**（新增测试类，非 LocalUserContextCache——防第二请求起标志丢失 fail-open）。
- `LoginResult.mfaRestricted` 与 `ScanLoginResult.mfaRestricted` 可选字段（跨模块公共 API 增量，W6 先例；migration note：可选字段向后兼容）。
- 受限会话拦截：executor + checker 双触点接线（见 Current Baseline 拦截链 live 结构）；语义按设计 §4.3——受限会话仅放行白名单 mutation（绑定类 `bindMfa`/`confirmMfa`/`unbindMfa`/`getMfaStatus` + 登记通道验证端点 + 登出 + 会话基建类 publicAccess mutation 如 token 刷新）+ 全部 query；其余 mutation 抛 `ERR_AUTH_MFA_RESTRICTED_SESSION`；**该分支不受 `nop.auth.operation-mfa.enabled` 门控**（设计 §3.3 伪代码序：受限分支前置于 enabled 检查；无 nop-auth-service 部署时 checker 不存在、零介入）。
- 登记通道 proof（防 enrollment attack）：受限会话内 `bindMfa` 前置——服务端解析用户已登记通道（W13 仅 phone，不接受客户端指定；通道为空抛 `ERR_AUTH_MFA_NO_RECOVERY_CHANNEL`）→ `SmsCodeStore`（key=`proof:{userId}`）发码 → `ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED`（脱敏提示）；`verifyChannelProof` 端点（白名单、登录态）校验后创建 scene=channel-proof 已验证票（复用 §三 场景化票语义）+ bindMfa 消费一次性票。正常会话 bindMfa 不受影响。
- `confirmMfa` 策略校验：确认因子强度 < 角色策略 minMfaLevel → `ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK` 拒绝；解绑仍允许（用户自主权 + 下次登录受限兜底）；受限会话内 confirmMfa 成功后**不原位升级会话**（引导重新登录，设计 §4.1 结论 7）。
- OAuth 入口接入（**substrate 预裁定见 Phase 2 Decision 项**）：`OAuthLoginServiceImpl.loginAsync` 与 `loginAsync` 同语义判定（checkMfaRequired 第三态 + challenge 拦截 + 受限签发），一期遗留绕过修复（跨模块 plan-first 已由本 plan 承载）。
- 审计：受限签发/受限拦截拒绝/登记通道发码与验证/策略变更（saveMfaPolicy/removeMfaPolicy）事件经 `IAuditService.saveAudit` 落 NopAuthOpLog。
- E2E：三层判定矩阵全路径（无策略零回归/有策略达标一期路径/弱因子受限/未启用受限）、受限会话白名单拦截矩阵（query 放行/白名单 mutation 放行/其余拒绝/Dao-cache 第二请求标志仍在）、登记通道 proof 全链（无票发码→验证→bindMfa→confirmMfa 强度校验→重新登录完整两阶段）、confirmMfa 降级拒绝、OAuth 入口受限与挑战两路径、策略时点语义（登录时评估、会话中期变更不回溯）。
- 文档：`docs-for-ai/03-modules/nop-auth.md` 角色级策略章节 + 设计 §4.6 impl 裁定回写 + roadmap。

## Non-Goals

- WebAuthn/FIDO2、邮件验证码、可信设备（W14/W15——`factorLevel` 表为强度序占位核对锚点，不实现 webauthn/email 因子本身）。
- A2-audit；`mfa-type.dict.yaml`（W14 交付物）。
- 前端受限会话引导页交互（设计 §七.5 deferred）。
- 策略管理 Web 页面（saveMfaPolicy/removeMfaPolicy 提供 biz action 即可；AMIS 页面增强属后续优化）。
- 已启用弱因子用户"先验弱因子再受限"（设计 §4.4 明确拒绝——统一不建 challenge 直接受限）。
- 会话中期角色变更实时重评（设计 §4.4 拒绝——登录时评估 + 会话携带标志）。
- W12 已交付面的重构（MfaFactorVerifier/场景化 store/操作级票逻辑仅消费不改造；唯一例外 = 拦截入口的受限前置分支接线）。

## Scope

### In Scope

- `nop-kernel/nop-api-core`：`IUserContext.mfaRestricted`（跨模块公共 API 增量 + migration note）。
- `nop-service-framework/nop-biz-auth-api`：`LoginResult.mfaRestricted`；`verifyChannelProof` 请求消息（如需新消息类）。
- `nop-service-framework/nop-biz-auth-core`：`UserContextImpl`（字段 + serializeToJson 白名单）+ OAuth 接入判定 SPI 接口（Phase 2 Decision 项 1，接口落点；实现 bean 落 nop-auth-service）。
- `nop-service-framework/nop-graphql/nop-graphql-core`：`GraphQLExecutor.checkOperationMfa` 受限分支接线（早退 continue 前置受限判定或等价重构；query/mutation 区分）。
- `nop-auth`：`nop-auth.orm.xml` 新实体 + 三方言 DDL；`RoleMfaPolicyEvaluator`；`LoginServiceImpl`（checkMfaRequired 第三态 + completeLogin 受限变体）；`DaoUserContextCache` 白名单；`OperationMfaCheckerImpl` 受限前置分支；`NopAuthRoleBizModel.saveMfaPolicy/removeMfaPolicy`；`NopAuthUserBizModel`（bindMfa 前置 + confirmMfa 策略校验）；`LoginApiBizModel.verifyChannelProof`（或等价白名单端点归属裁定）；`NopAuthErrors` ×4；审计；beans 注册。
- `nop-auth-sso`：`OAuthLoginServiceImpl` 判定接入。
- `nop-ai/nop-ai-gateway`：`ScanLoginResult.mfaRestricted`。
- 测试：上述全部分层单测 + Dao-cache round-trip + E2E 全链。
- `docs-for-ai/03-modules/nop-auth.md`、设计 §4.6 回写、roadmap。

### Out Of Scope

- W14/W15 全部交付面；A2-audit；前端页面；可信设备（§六，含 allowTrustedDevice 的消费端——本 plan 只落库与产出复合结果，豁免判定 W15 接线）。

## Execution Plan

### Phase 1 - 策略数据模型 + 评估器 + 策略管理面

Status: planned
Targets: `nop-auth/model/nop-auth.orm.xml`、`deploy/sql/{mysql,postgresql,oracle}/`、`nop-auth-service`（新 `RoleMfaPolicyEvaluator`、`NopAuthRoleBizModel`）、beans 注册

- Item Types: `Fix | Decision | Proof`

- [ ] **Fix**：ORM 新实体 `NopAuthRoleMfaPolicy`（roleId 唯一约束 1:1、minMfaLevel、allowTrustedDevice 缺省 true、通用审计字段 + delFlag 软删除对齐 nop-auth 惯例；无外键，对齐既有关系惯例；model-first → codegen → `_create_` 再生成 + 三方言 `_add_role_mfa_policy_nop-auth.sql` 增量，循 `_add_mfa_challenge_scene_nop-auth.sql` 先例；禁止手编 `_gen/`）。
- [ ] **Fix**：`RoleMfaPolicyEvaluator`（nop-auth-service）：输入 userId → 按角色快照口径（直接角色 + childRoleIds 继承展开 + 隐式 user 角色及其继承链，与 `buildUserContext` live:772-816 一致）合并全部命中角色的策略行 → 产出复合结果 `{maxLevel, allowTrustedDevice}`（max 取最强、AND 合并、无行 = 0/true）；内含 `factorLevel` 强度表（全量 1/2/3 映射常量：sms/email=1、totp=2、webauthn=3；未知 mfaType fail-closed 视为 0）。
- [ ] **Fix**：`NopAuthRoleBizModel.saveMfaPolicy`/`removeMfaPolicy`（requireAdmin 运行时校验——`NopAuthUserBizModel.requireAdmin` live:487-498 先例模式；`IAuditService.saveAudit` 审计策略变更；删行即撤策略、无 status 双态）。
- [ ] **Proof**：单测——evaluator 合并语义（单角色/多角色 max/AND/无策略零行/隐式 user 角色挂策略 = 全员强制）、factorLevel 表映射与未知值 0、策略 CRUD 幂等与权限拒绝（非 admin）、ORM round-trip。

Exit Criteria:

- [ ] `./mvnw clean install -pl nop-auth -am -DskipTests -T 1C` 编译绿 + codegen 产物再生成（无手编生成物）；三方言 DDL 齐备。
- [ ] **新功能测试**：列出 Phase 1 测试类与用例名。
- [ ] 文档裁定：No owner-doc update required（实体/评估器/管理面属实现细节；docs-for-ai 章节统一 Phase 4）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 登录链第三态 + 受限签发 + mfaRestricted 持久化 + 响应契约 + OAuth 接入

Status: planned
Targets: `nop-api-core`（`IUserContext`）、`nop-biz-auth-core`（`UserContextImpl`）、`nop-biz-auth-api`（`LoginResult`）、`nop-auth-service`（`LoginServiceImpl`/`DaoUserContextCache`）、`nop-auth-sso`（`OAuthLoginServiceImpl`）、`nop-ai-gateway`（`ScanLoginResult`）

- Item Types: `Fix | Decision | Proof`

- [ ] **Fix**：`IUserContext.mfaRestricted` 以 **Java `default` 方法**落地（`default boolean isMfaRestricted() { return false; }`，`UserContextImpl` 覆写——接口存在仓库外/测试树多个外部实现类，如 nop-credential 测试内 5 个匿名/内部 IUserContext 实现，抽象方法会编译破坏下游；default 保证"老消费方零感知"）；migration note 登记。`UserContextImpl` 字段 + `serializeToJson` 白名单纳入（live:45-66）+ `DaoUserContextCache.saveUserContextAsync` 白名单纳入（live:111-123）。
- [ ] **Fix**：`checkMfaRequired` 第三态（live:725-742）：策略评估调用插入 store null 检查（live:728-729）之后、setting 装载之前；`policy.maxLevel > 0 且 (!enabled 或 factorLevel(mfaType) < maxLevel)` → 返回受限决策对象（不建 challenge）；一期六分支原位原序不动。
- [ ] **Fix**：`completeLogin` 受限变体（live:398-416）：`restricted=true` 时在 `saveUserContextAsync`（live:414）之前写 `mfaRestricted` 标志（`saveSession` live:408 前置写入覆盖两持久化路径）；`resetFailCount`/`notifyHook` 差异裁决照旧（第一因子成功仍属登录成功）；一期三处调用点（loginAsync/createSessionForUserAsync/mfaVerify）行为不变。`loginAsync`/`createSessionForUserAsync` 调用侧消费第三态 → 受限签发；`LoginResult.mfaRestricted`/`ScanLoginResult.mfaRestricted` 可选字段回填。**受限签发审计事件**（经 `IAuditService.saveAudit` 落 NopAuthOpLog）随本项交付（不后置到 Phase 3）。
- [ ] **Decision（OAuth 接入 substrate 预裁定，draft review F1）**：
  1. **SPI 落点**：判定能力经新接口（如 `IMfaLoginPolicyService`，命名执行期定稿）落 **nop-biz-auth-core**（nop-auth-sso classpath 可达），实现 bean 落 nop-auth-service（内部复用 `checkMfaRequired` 等价逻辑 + evaluator）；`OAuthLoginServiceImpl` `@Inject @Nullable` 可选注入（先例：`GraphQLEngine` checker / `LoginServiceImpl` 的 `@Nullable ISmsSender`）——未注册（无 nop-auth-service 部署）时零介入 = 一期行为。**禁止**为接入而在 nop-auth-sso 引入对 nop-auth-service/dao 的依赖边。
  2. **本地用户与角色语义**：OAuth 身份（userId=userName、realm roles）按 userName 解析本地 `NopAuthUser`；策略评估用**本地角色快照**（`NopAuthRoleMfaPolicy` 挂本地 roleId，realm roles 不参与策略评估——裁定回写设计 §4.6）；无本地用户映射 → 无策略可评估 → 维持一期行为（MFA 不拦截，显式裁定 + 回写；语义 = 与"无策略角色"一致的空策略）。
  3. **受限签发基座**：live 事实——`DaoUserContextCache` 无 `NopAuthSession` 行时静默 no-op（live:107-109），标志直接置上会丢（fail-open）。裁定：受限路径必须先落会话行（`saveSession` 等价）再 `saveUserContextAsync`，或整体迁移到 completeLogin 受限变体——两方案执行期按最小 diff 定稿并回写 §4.6；无论何者，**OAuth attrs（accessToken/refreshToken，live:146-147）不得丢失**（专项断言）且必须通过 Dao-cache round-trip 测试。
- [ ] **Fix**：`OAuthLoginServiceImpl.loginAsync`（live:70-79）按上述 Decision 接入同语义判定：判定结果三分支（null 放行/challenge 拦截抛 `ERR_AUTH_MFA_REQUIRED`（errorParams 携带，与一期表达一致）/受限 → 受限签发路径），替换现行的"自行 buildUserContext + saveUserContextAsync 直签"绕过（live:77）；跨模块 migration note 登记（该入口从"永不拦截"变为"与密码路径同语义"——一期遗留 gap 的修复，属预期行为变更）。
- [ ] **Proof**：单测/集成——三层判定矩阵五行的登录行为（全局开关 off=一期旁路/无策略=一期判定/达标=一期 challenge 路径逐字节一致/弱因子=受限/未启用=受限）；Dao-cache round-trip（**新增** DaoUserContextCache 路径测试：受限签发后第二请求从 cacheData 反序列化 `mfaRestricted` 仍在——Local cache 测试不能替代）；LoginResult/ScanLoginResult 字段序列化（可选、缺省不出现）；OAuth 入口三路径（放行/挑战/受限）+ 修复前绕过对照断言；无策略部署全矩阵 = 一期行为（既有 `TestMfaLoginE2E`/`TestScanLoginMfa` 断言零修改通过）。

Exit Criteria:

- [ ] `./mvnw clean install -pl nop-kernel/nop-api-core,nop-service-framework/nop-biz-auth-api,nop-service-framework/nop-biz-auth-core,nop-auth,nop-auth-sso,nop-ai/nop-ai-gateway -am -DskipTests -T 1C` 编译绿 + **下游接口实现类回归**：`./mvnw test-compile -pl :nop-credential -am`（`IUserContext` 在 nop-credential 测试树有 5 个外部实现类——default 方法落地后必须确认下游零破坏；`-am` 不含该方向，须显式验证）。
- [ ] **接线验证**：受限签发 → Dao-cache 持久化 → 第二请求标志存活，全链测试通过（非仅字段存在）。
- [ ] **无静默跳过**：策略不达标路径显式产生受限决策（非静默放行）；未知 mfaType 强度 0 落入受限。
- [ ] **新功能测试**：列出 Phase 2 测试类与用例名（含 Dao-cache round-trip）。
- [ ] 文档裁定：No owner-doc update required（章节统一 Phase 4；migration note 随设计回写登记）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 受限会话拦截 + 登记通道 proof + confirmMfa 策略校验 + 错误码 + 审计

Status: planned
Targets: `nop-graphql-core`（`GraphQLExecutor`）、`nop-auth-service`（`OperationMfaCheckerImpl`/`NopAuthUserBizModel`/`LoginApiBizModel`/`NopAuthErrors`）、`nop-biz-auth-api`（消息类如需）

- Item Types: `Fix | Decision | Proof`

- [ ] **Fix**：受限会话拦截接线（双触点，**机制族钉定——不改 `IOperationMfaChecker` SPI 签名**）：① `GraphQLExecutor.checkOperationMfa`（live:191-208）——executor 持 fieldDef 做 query/mutation/publicAccess 判别，将受限会话的非 public mutation 路由进 checker 调用（早退 continue live:204-205 之前或等价重构）；② `OperationMfaCheckerImpl.check`（live:83-131）——受限分支置于 enabled 门（live:86-87）之前（javadoc live:57-58 预留位），白名单判定在 checker 内执行。语义：`userContext.mfaRestricted` → 全部 query 放行 + 白名单 mutation 放行 + publicAccess mutation 放行（token 刷新等会话基建，防受限会话中途 token 过期死锁；`refreshTokenAsync` publicAccess 先例 `LoginApiBizModel` live:122-125）+ 其余 mutation 抛 `ERR_AUTH_MFA_RESTRICTED_SESSION`；**白名单命中即短路返回**（不再走后续 @MfaRequired 检查——`unbindMfa` 自身标注 @MfaRequired（live:296），受限引导流中白名单动作不做操作级二次验证，语义按设计 §3.3 伪代码受限分支前置序，回写 §4.6）；**不受 `nop.auth.operation-mfa.enabled` 门控**；无 checker bean（无 nop-auth-service 部署）时零介入不变。
- [ ] **Decision**：白名单清单落地（设计 §4.3）：`bindMfa`/`confirmMfa`/`unbindMfa`/`getMfaStatus` + 登记通道验证端点 + 登出 + 会话基建类 publicAccess mutation（以 live action 清单核对确定，如 token 刷新；清单在 plan 执行时以实际 publicAccess mutation 枚举固化并测试钉定）；白名单匹配口径 = operation 全名（bizObjName__action，与 §三 payload.operation 契约一致）。
- [ ] **Fix**：登记通道 proof（防 enrollment attack；**票交接机制预裁定——零 store API 变更**：`verifyChannelProof` 成功后创建 scene=channel-proof 已验证票并将 challengeToken 返回客户端，`bindMfa` 请求新增可选 proof 字段携带该 token，服务端经既有 `peek(token)`（scene/payload.userId 校验）+ `consume(token)` 一次性消费——设计 §4.3 伪代码的 `peekVerified(scene, userId)` 查找原语**不落地**（`MfaChallengeStore` 无 scene+userId 查找方法，token 往返方案避免跨模块 store 接口扩展；偏离回写 §4.6））：受限会话内 `bindMfa`（live:139-169）前置——服务端解析用户登记 phone（W13 仅 phone、不接受客户端指定；为空抛 `ERR_AUTH_MFA_NO_RECOVERY_CHANNEL`）→ 无有效 channel-proof 票时 `SmsCodeStore.send("proof:{userId}")` + 抛 `ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED`（通道脱敏提示）；`verifyChannelProof` 端点（登录态 + 白名单）校验 proof 码 → 创建 scene=channel-proof 已验证票（复用场景化 challenge + markVerified 语义，短 TTL——复用 `op-ticket-expire-seconds` 票窗口语义，执行期定值回写）；bindMfa 消费票（一次性）。正常会话 bindMfa 零改动。**proof 通道发码复用既有 sms-code 限流模式**（60s 间隔/日上限，`sendMfaCode` 调用点限流先例——防受限会话内 proof 码轰炸受害者登记手机）。
- [ ] **Fix**：`confirmMfa`（live:249-286）策略校验：确认因子 `factorLevel < maxLevel` → `ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK`；解绑不受限（攻击者无因子不可解绑 + 审计）；受限会话内 confirmMfa 成功不原位升级会话（无会话变更代码路径）。
- [ ] **Fix**：`NopAuthErrors` 追加 4 错误码（live:120-121 之后）；审计事件（受限拦截拒绝/登记通道发码与验证/策略变更（saveMfaPolicy/removeMfaPolicy）——受限签发审计已在 Phase 2 交付）经 `IAuditService.saveAudit` 落 NopAuthOpLog。
- [ ] **Proof**：E2E 全链——受限会话拦截矩阵（query 放行/白名单 mutation 放行/其余 mutation 拒绝且错误码正确/publicAccess mutation 放行/enabled=false 时受限拦截仍生效（伪代码序钉定）/无 checker 零介入回归）；登记通道全链（受限登录 → bindMfa 被阻发码 → verifyChannelProof（含 60s 间隔限流断言）→ bindMfa 携 proof 票过 → confirmMfa 强度不足拒绝 → unbindMfa（验证当前因子）→ bind 强因子 → confirm 过 → 登出 → 重新登录完整两阶段 → 完整会话）；enrollment attack 对抗断言（无 proof 票时 bindMfa 不可达 provisioning URI；proof 票一次性——重放拒绝）；confirmMfa 降级拒绝；错误码/审计落库断言；操作级 MFA 与受限分支共存路径（受限会话内白名单 mutation（如 `unbindMfa`，自身标注 @MfaRequired）被白名单短路放行——不再触发操作级 challenge，专项钉定）；OAuth 入口三路径 + attrs 保留断言。

Exit Criteria:

- [ ] **端到端验证**：从登录入口（密码/OAuth）→ 策略不达标 → 受限会话签发 → Dao-cache 第二请求仍受限 → 非白名单 mutation 拒绝 → 登记通道验证 → 绑定强因子 → 重新登录完整两阶段 → 完整会话，全路径经真实容器组件 E2E 跑通。
- [ ] **接线验证**：executor → checker 受限分支运行时连通（未标注 @MfaRequired 的 mutation 也被拦截的专项断言）；checker 不存在时零介入回归。
- [ ] **无静默跳过**：受限拒绝/proof 缺失/通道为空/因子过弱各分支显式抛错（专项测试）。
- [ ] **一期零回归**：`./mvnw test -pl nop-auth -am` 既有套件断言零修改通过（pre-existing flake 按 W12 登记口径处理：单独运行复核 + baseline 对照）；无策略部署矩阵 = 一期行为。
- [ ] **新功能测试**：列出 Phase 3 测试类与用例名。
- [ ] 文档裁定：章节与回写统一 Phase 4。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 文档同步 + 设计回写 + 收口验证

Status: planned
Targets: `docs-for-ai/03-modules/nop-auth.md`、`ai-dev/design/nop-auth/02-mfa-phase2-design.md`（§4.6 回写）、`docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md`、roadmap

- Item Types: `Follow-up | Proof`

- [ ] **Follow-up**：`docs-for-ai/03-modules/nop-auth.md` 补角色级强制策略章节（三层判定矩阵/受限会话语义与白名单/登记通道 proof/策略管理 API/4 错误码/mfaRestricted 字段与 Dao-cache 语义/OAuth 入口行为变更说明/与操作级 MFA 的分层）；功能概览、核心实体表、配置、源码锚点表同步更新。
- [ ] **Follow-up**：设计 §四回写 impl 裁定标注（新 §4.6：executor/checker 双触点接线事实与白名单短路语义、白名单终版清单、verifyChannelProof 端点归属与 proof 票 token 往返机制（`peekVerified` 原语不落地的偏离）、proof 发码限流、OAuth 接入 substrate 三裁定（SPI 落点/本地角色语义/受限签发基座）、Dao-cache 测试补充事实、**§4.3 "@BizAudit 审计"措辞按 W12 §3.6 装饰性事实修正为 saveAudit**、**§八 W13-impl Protected Area 清单补 `GraphQLExecutor`/nop-graphql-core 触点**、其余执行期偏离）；roadmap W13-impl 状态更新。
- [ ] **Proof**：受影响模块全量验证——`./mvnw test -pl nop-kernel/nop-api-core,nop-service-framework/nop-biz-auth-api,nop-service-framework/nop-biz-auth-core,nop-service-framework/nop-graphql/nop-graphql-core,nop-auth,nop-auth-sso,nop-ai/nop-ai-gateway -am -T 1C` 绿（pre-existing flake 口径同 W12 登记）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth --severity high` 0 NEW；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

Exit Criteria:

- [ ] 文档与 live 实现一致（矩阵/白名单/错误码/流程可对号）。
- [ ] 验证命令通过（附输出；框架核心与公共 API 下游回归面覆盖）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 三层判定矩阵全路径测试（五行矩阵逐行断言，无策略行 = 一期行为逐字节等价）。
- [ ] 一期零回归：既有 MFA 套件断言零修改；`ERR_AUTH_MFA_REQUIRED` 表达不变；completeLogin 一期调用点行为不变。
- [ ] **mfaRestricted 持久化 Dao-cache 路径测试**（第二请求标志存活——fail-open 防护；Local cache 测试不可替代）。
- [ ] 受限会话拦截矩阵全路径（query/白名单/publicAccess/拒绝/enabled=false 不受限门控/无 checker 零介入）。
- [ ] 登记通道 proof 全链 + enrollment attack 对抗断言。
- [ ] confirmMfa 降级拒绝 + 解绑不受限。
- [ ] OAuth 入口三路径（放行/挑战/受限）+ migration note 登记。
- [ ] ORM 变更经 model-first，无手编生成物；三方言 DDL 齐备。
- [ ] 跨模块公共 API 增量（IUserContext default 方法/LoginResult/ScanLoginResult）migration note 在案；`./mvnw test-compile -pl :nop-credential -am` 确认 IUserContext 外部实现类零破坏。
- [ ] 无空壳/静默跳过（scan-hollow NEW 0 + 拒绝分支测试覆盖）。
- [ ] 受影响 owner docs 已同步 + 设计裁定标注回写。
- [ ] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow：登录入口 → 策略评估 → 受限签发 → 持久化 → executor 拦截 → proof → 重新登录全链追踪）。
- [ ] `./mvnw test -pl nop-kernel/nop-api-core,nop-service-framework/nop-biz-auth-api,nop-service-framework/nop-biz-auth-core,nop-service-framework/nop-graphql/nop-graphql-core,nop-auth,nop-auth-sso,nop-ai/nop-ai-gateway -am` 绿（pre-existing flake 按 W12 登记口径）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [ ] checkstyle / 代码规范检查通过（受影响模块 `-Pqa`）。

## Deferred But Adjudicated

（起草时空缺——执行中产生的延期项按 Anti-Slacking 规则填充；预登记倾向：策略管理 Web 页面（AMIS）与 `mfa-type.dict.yaml` 为 W14 范畴，不属本 plan deferred。）

## Non-Blocking Follow-ups

（执行后登记；一期登录级 scene 零校验残留等 W12 登记项不重复搬运。）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- no remaining plan-owned work（待收口时更新）
