# W12-impl 操作级 MFA（会话内敏感操作二次验证：@MfaRequired + executor 拦截 + 票两段式）

> Plan Status: completed
> Mission: nop-credential-mfa
> Work Item: W12-impl（操作级 MFA）——MFA 二期组第一个 impl 工作项
> Last Reviewed: 2026-08-16
> Source: `ai-dev/design/nop-auth/02-mfa-phase2-design.md` §三（3.1-3.5 全部）+ §5.3.0 #5/#6（MfaFactorVerifier 收敛对象）+ §八 W12-impl 映射；roadmap W12-impl 条目
> Related: W12-design `2026-08-14-2012-2-mfa-phase2-design.md`（设计收口 plan）；W4/W5/W6/W8 一期 MFA plan 链（两阶段 challenge / mfaVerify / store 三实现 / 装配不变式的基线来源）

## Purpose

按 W12-design §三落地操作级 MFA：`@MfaRequired` 方法注解（nop-biz-auth-api）+ 构建期约束校验（subscription/publicAccess 组合拒绝）；注解元数据经 `ReflectionBizModelBuilder` → `GraphQLFieldDefinition` → executor 检查点的传播链（@BizMakerChecker 先例）；`IOperationMfaChecker` SPI + nop-auth-service 实现（GraphQLEngine 可选注入，未注册零介入）；`MfaChallengeStore` 场景化扩展（scene/payload/verifiedAt + create 重载 + markVerified 三实现原子性）；操作级 challenge 两段式（验证转一次性短 TTL 票，票绑定 operation+sessionId）；`MfaFactorVerifier` 共享因子校验组件抽取（登录级/绑定级收敛 + TOTP 窗口统一推进）；`mfaVerifyOperation` 端点（登录态、同会话、不签发凭证）；配置组 + 错误码 + 首批敏感操作标注 + 审计四事件。`nop.auth.operation-mfa.enabled` 缺省 false——关闭时框架零介入，一期零回归。

## 规模裁定（设计 §八 拆分提示的 plan-first 裁定，起草时执行）

本 plan 触碰面经独立 draft review 估算约 25-30 文件（graphql-core ~6 + nop-biz 1 + biz-auth-api 2-3 + biz-auth-core 3 + nop-auth 10+ + 测试 ~10），超 roadmap 单 plan 参考规模（5-15 文件）。**裁定不拆分**：W12-impl 是设计 §八 指派给单一工作项的完整交付面，Phase 1（框架接线）/Phase 2（store 场景化）/Phase 3（拦截器 + 端点 + E2E）任一单独落地都不构成用户可用功能——操作级 MFA 在 Phase 3 末端才第一次成立（guide Practical Rule：多 slice 全部完成后 feature 才成立 → 同一 owner plan）；Phase 间为严格顺序依赖（Phase 3 消费 Phase 1 元数据链与 Phase 2 store 扩展）。执行中若单 Phase 严重超载，按 guide 拆分规则另行 plan-first 裁定。

## Current Baseline

（2026-08-16 live repo 核对，独立 explore agent 复核锚点；**模块路径事实：`nop-biz-auth-api`/`nop-biz-auth-core` 在 `nop-service-framework/` 下，`nop-graphql-core` 在 `nop-service-framework/nop-graphql/` 下**）

- `nop-biz-auth-api`（`io.nop.auth.api.*`）：`messages/MfaVerifyRequest.java`（3 字段 challengeToken/code/recoveryCode，live:21-23）；`LoginApi.java:62-63` mfaVerify 声明；nop-graphql-core 依赖该工件（pom live:50-53，`GraphQLActionAuthChecker.java:15` 引用 `AuthApiErrors` 先例）——注解与 SPI 落点前提成立。
- **@BizMakerChecker 先例链（元数据传播模板）**：注解在 nop-api-core（`BizMakerChecker.java:17-25`）；`ReflectionBizModelBuilder.java:338-341` 读取并设 `field.setMakerCheckerMeta(...)`；`GraphQLFieldDefinition.java:70/215-229` 持有字段；`GraphQLObjectDefinition.mergeField` 两分支拷贝（live:224-225 replace=true / live:260-261 replace=false）。**live 核定两处设计未列出的拷贝触点**：(a) `GraphQLFieldDefinition.deepClone()`（live:80-92）不拷贝 makerCheckerMeta——新 meta 不加拷贝会在 clone 路径丢失（`BizObjectManager.getObjDef` live:384 deep-clone 先例）；(b) `nop-biz` 模块 `BizObjectBuildHelper.mergeBizModel`（live:71-72）是 Java biz-model → BizObject 合并的实际搬运点——`mfaRequiredMeta` 必须同点拷贝（Protected Area 触点新增 nop-biz）。
- `@Auth` 元数据：`ReflectionBizModelBuilder.java:330-336`（publicAccess 读取）；`@BizSubscription` 处理 live:146-157（构建期约束校验的插入邻域）；默认非 public 动作 permission 赋值 live:334-335。
- **executor 检查点**：`GraphQLExecutor.java:64`（executeOneAsync，RPC 单操作路径）与 `:146`（executeAsync，GraphQL 文档路径）各有 `GraphQLActionAuthChecker.INSTANCE.check(context)`——操作级检查插在其后。**live 核定：auth check 共 4 处调用点**（另有 `GraphQLEngine.subscribeGraphQL:654`/`subscribeRpc:717`）——设计以构建期拒绝 `@BizSubscription` 组合覆盖，需回归断言。
- **checker 接线先例**：`GraphQLEngine` 可选注入模式 `@Inject @Nullable` setter（setActionAuthChecker live:147-150、setDataAuthChecker/FlowControlRunner/GraphQLHook 同型）；`IActionAuthChecker` 经 `IServiceContext` 传播（本设计显式不镜像——`IOperationMfaChecker` 走 GraphQLEngine 字段 + `IGraphQLExecutionContext` 透出，`makerCheckerEnabled` 字段模式 live:37-39/45/87-94）。
- `MfaChallenge`（`nop-biz-auth-core/.../mfa/store/MfaChallenge.java`）：8 字段（challengeToken/userId/mfaType/loginType/tenantId/phone/createdAt/expireAt，live:26-33），**无 scene/payload/verifiedAt**；Redis JSON 序列化契约（javadoc live:15-17）：`$d:类名\nJSON`（`PrefixEncodeHelper` live:72/95-97，`JsonTool.parseBeanFromText` Jackson）——新字段必须 nullable + 无参默认，滚动升级老进程读新 JSON 的 unknown-prop 容忍需测试验证（migration note）。
- `MfaChallengeStore` 接口（live:37-53）：create 五参 / peek / incrFailCount / consume——无场景重载与 markVerified。三实现：`LocalMfaChallengeStore`（ConcurrentHashMap compute，nop-biz-auth-core）、`DbMfaChallengeStore`（nop-auth-service，条件 UPDATE/DELETE + affected-row 原子性先例 live:103-107/126-132）、`RedisMfaChallengeStore`（nop-auth-service，putExAsync/removeIfMatch Lua CAS live:75/129；SETNX 原语 `INosqlKeyValueOperations.putIfAbsentExAsync` 存在，nosql-core live:31）。装配：`auth-service.beans.xml` collect-beans `nopMfaChallengeStore_` 前缀 + ioc:condition（live:32-46/63-74）；`nopMfaChallengeConfig`（live:24-26，仅 expireSeconds，三 store 共用）。
- ORM：`nop_auth_mfa_challenge`（`nop-auth/model/nop-auth.orm.xml:1188-1229`，tagSet no-tenant）**无 SCENE/PAYLOAD/VERIFIED_AT 列**；`NopAuthMfaSetting`（live:1060-1129）status 取值 pending|enabled|disabled（enabled 即激活）。DDL 惯例：`nop-auth/deploy/sql/{mysql,postgresql,oracle}/` 有 `_add_ext_login_unique.sql` 手写增量先例 + `_create_` codegen 产物。
- `LoginServiceImpl`（1076 行）：MFA 门 `checkMfaRequired` live:743-760（challenge 创建调用 live:757-758）；`mfaVerifyAsync` live:421-455（peek/setting 复核/recovery 分支分发）；`verifySecondFactorAndComplete` live:460-489（**MfaFactorVerifier 抽取源之一**：TOTP 分支 live:465-466 → `verifyTotp` live:494-511、SMS 分支 live:467-473、未知 mfaType fail-closed live:474-479、失败计数 live:481-485）；`verifyRecoveryCode` live:551-578（恢复码独立分支，**不在收敛范围**）；`completeLogin` live:388-406（操作级不触碰）；`auditLogFail` live:997-1029（`IAuditService.saveAudit(AuditRequest)` 显式审计先例）。
- `NopAuthUserBizModel`：`verifyFactorForBind` live:395-415（**MfaFactorVerifier 抽取源之二**：TOTP live:396-403 / SMS live:404-413）；`resetUserMfa` live:364-388（requireAdmin + @BizAudit 标注）、`unbindMfa` live:290-312、`generateRecoveryCodes` live:318-330——首批敏感操作标注候选。**live 核定：`@BizAudit` 注解全仓库无消费方（装饰性）**——审计必须走 `IAuditService.saveAudit`（`GraphQLAuditLogger` live:85-115 同先例）。
- `LoginApiBizModel`：9 个 action 全部 `@Auth(publicAccess=true)`；`mfaVerifyOperation` 将是**首个登录态 action**（省略 @Auth → 默认 permission 赋值即需登录）；`extractClientIp` helper live:141-159。
- `IUserContext.getSessionId()` 存在（nop-api-core live:29-32，登录时 `saveSession` 填充 live:861-868）；executor 侧 sessionId 取法先例 `GraphQLAuditLogger:127`。**票请求头通道已核实**：`IGraphQLExecutionContext.getRequestHeaders()` 存在（`GraphQLExecutionContext:82-83` → `IServiceContext:46`），两检查点均持有 context——`X-Nop-Op-Mfa-Token` 请求头通道成立（见 Phase 3 Fix 项）。
- 配置：`NopAuthConfigs` MFA 组 live:79-111（varRef + @Description 模式）——**无 `nop.auth.operation-mfa.*` 组**。错误码：`NopAuthErrors` MFA/SMS 块 live:73-109（`ERR_AUTH_MFA_REQUIRED` live:79-80 等）——**无 `ERR_AUTH_OPERATION_MFA_REQUIRED`**。
- `TOTPAuthenticator.verify`（live:109-122）返回命中窗口，**lastVerifiedWindow 更新在调用方**（LoginServiceImpl live:507-509 / confirmMfa live:274-281）——统一推进需移入新组件。
- 测试基线：`TestMfaLoginE2E`（真实 LoginServiceImpl 全链）、`TestMfaUserSelfService`（登录态模式 `ctx()` live:617-625 / `adminCtx()`）、`TestNopAuthUserBizModel`（`graphQLEngine.newRpcContext` 先例 live:86/99/114）、`TestDbMfaChallengeStore`、`TestLocalMfaChallengeStore`、`TestMfaStoreWiringDb`（IoC 装配接线先例）。
- **@MfaRequired / IOperationMfaChecker / mfaRequiredMeta / nop.auth.operation-mfa 全仓库不存在**（greenfield 核实）；WebAuthn 零代码（W14 范围）。

## Goals

- `@MfaRequired` 注解（nop-biz-auth-api）+ 构建期约束校验：标注于 `@BizSubscription` 方法 → 构建报错；与 `@Auth(publicAccess=true)` 同用 → 构建报错（fail-fast，静默绕过 = fail-open）。
- 元数据传播链完整：ReflectionBizModelBuilder 读取 → `GraphQLFieldDefinition.mfaRequiredMeta` → `deepClone()` 拷贝 + `GraphQLObjectDefinition.mergeField` 两分支拷贝 + `BizObjectBuildHelper.mergeBizModel` 拷贝（nop-biz 新触点）。
- executor 拦截：`GraphQLExecutor` 两检查点（live:64/146，auth check 之后）对声明方法调用 `IOperationMfaChecker`；`GraphQLEngine` `@Inject @Nullable` 注入 + `IGraphQLExecutionContext` 透出；无实现 bean 时零介入（框架独立可用性不变）。
- `MfaChallengeStore` 场景化：`MfaChallenge` 增 scene（缺省 login）/payload（JSON 字符串，一次写入只读）/verifiedAt；`create` 场景重载（老五参委托 scene=login, payload=null——一期调用点零改动）；`markVerified(token)` 一次性状态迁移（Local：JVM 原子 compute；DB：条件 UPDATE verified_at + affected-row；Redis：派生票键 SETNX `putIfAbsentExAsync`，peek 对调用方语义一致）。
- `MfaFactorVerifier` 共享组件（nop-auth-service）：收敛登录级 TOTP/SMS 分支 + 绑定级 `verifyFactorForBind`；**TOTP 防重放窗口统一推进内聚组件内**（任何场景成功更新 lastVerifiedWindow，防跨场景窗口码重放）；恢复码分支不入组件（登录级专用）；W14/W15 新因子只改组件。
- `mfaVerifyOperation` 端点（LoginApiBizModel，登录态）：scene==operation + 同会话 + 已验证票拒绝重复 + setting 复核（runWithTenant）+ 因子校验 + 失败计数超限作废 + markVerified 一次性；**成功不签发任何凭证**（不触碰 completeLogin）。
- 拦截判定（§3.3 伪代码）：enabled 总开关（缺省 false）→ 未启用 MFA 用户不拦截 → 票核验（scene/payload.operation/payload.sessionId/票窗口 + 原子 consume 一次性）→ 创建 challenge（payload={operation, sessionId}）→ 抛 `ERR_AUTH_OPERATION_MFA_REQUIRED`（errorParams 携带 challengeToken/mfaType/operation）。
- 配置组 `nop.auth.operation-mfa.enabled`（false）/`op-ticket-expire-seconds`（60）；新错误码 + 复用裁定（CHALLENGE_EXPIRED/MFA_FAIL 沿用既有码）；审计四事件（发起/验证成功/失败/票消费）经 `IAuditService.saveAudit`。
- 首批敏感操作标注（Decision，Phase 3）：nop-auth 模块内五动作（`resetUserMfa`/`resetUserPassword`/`changeSelfPassword`/`unbindMfa`/`generateRecoveryCodes`）；凭证库模块标注显式 deferred（见 Deferred）。
- ORM：`nop_auth_mfa_challenge` 加 SCENE/PAYLOAD/VERIFIED_AT 三列（model-first → codegen → `_create_` 再生成 + 三方言手写增量，循 `_add_ext_login_unique.sql` 先例）；Redis 滚动升级兼容 note + 测试。
- `docs-for-ai/03-modules/nop-auth.md` 操作级 MFA 章节 + 设计回写标注（nop-biz/deepClone 拷贝触点、@BizAudit 装饰性事实的审计落点、请求头通道裁定）。

## Non-Goals

- 角色级强制策略引擎 / 受限会话 / mfaRestricted（W13-impl——本 plan 拦截器不留半成品分支，W13 接缝 = 拦截判定入口的前置点，设计 §3.3 注释已界定）。
- WebAuthn/FIDO2、邮件验证码、可信设备、EmailCodeStore（W14/W15）；`MfaVerifyRequest.assertion` 字段（W14）。
- 凭证库模块（nop-credential）敏感动作标注（Deferred 裁定，见下）。
- 前端交互设计（绑定页/操作级弹窗——设计 §七.5 deferred）；`@MfaRequired` 标注分布治理工具（§七.6）；时间窗免验证方案（§3.4 拒绝，不翻案）。
- `IServiceContext`/nop-core 任何变更（§3.4 拒绝：checker 不经 IServiceContext 接线）；`IUserContext` 加"已验证"标记（§3.4 拒绝）。
- 登录级 `mfaVerify` 端点改造（§3.4 拒绝复用端点混入操作分支）。

## Scope

### In Scope

- `nop-service-framework/nop-biz-auth-api`：`@MfaRequired` 注解 + `IOperationMfaChecker` SPI + 操作级验证请求消息（challengeToken + code 两字段；不暴露 recoveryCode）。
- `nop-service-framework/nop-graphql/nop-graphql-core`：`ReflectionBizModelBuilder`（读取 + 构建期约束）、`GraphQLFieldDefinition`（mfaRequiredMeta + deepClone 拷贝）、`GraphQLObjectDefinition.mergeField`（两分支拷贝）、`GraphQLEngine`（可选注入 + 透出）、`IGraphQLExecutionContext`/`GraphQLExecutionContext`（checker 可达性）、`GraphQLExecutor`（两检查点调用）。
- `nop-service-framework/nop-biz`：`BizObjectBuildHelper.mergeBizModel` 元数据拷贝（live 核定新触点）。
- `nop-service-framework/nop-biz-auth-core`：`MfaChallenge` 三新字段 + `MfaChallengeStore` 两方法 + `LocalMfaChallengeStore` 适配。
- `nop-auth`：`nop-auth.orm.xml` 加列 + DDL；`DbMfaChallengeStore`/`RedisMfaChallengeStore` 适配（markVerified 原子性）；`MfaFactorVerifier` 抽取；`OperationMfaCheckerImpl` + bean 注册（ioc:default）+ 装配测试；`LoginApiBizModel.mfaVerifyOperation`；首批标注；`NopAuthConfigs`/`NopAuthErrors`；审计。
- 测试：上述全部分层的单测 + GraphQL 引擎级 + E2E 全链。
- `docs-for-ai/03-modules/nop-auth.md`、设计裁定标注回写、roadmap。

### Out Of Scope

- W13/W14/W15 全部交付面；A2-audit；nop-credential 模块标注；前端页面。

## Execution Plan

### Phase 1 - 框架接线：注解 + 元数据传播 + executor 检查点 + SPI 装配

Status: completed
Targets: `nop-biz-auth-api`、`nop-graphql-core`（`ReflectionBizModelBuilder`/`GraphQLFieldDefinition`/`GraphQLObjectDefinition`/`GraphQLEngine`/`IGraphQLExecutionContext`/`GraphQLExecutionContext`/`GraphQLExecutor`）、`nop-biz`（`BizObjectBuildHelper`）

- Item Types: `Fix | Proof`

- [x] **Fix**：`@MfaRequired`（RUNTIME/METHOD/无必需属性）+ `IOperationMfaChecker` SPI 落 nop-biz-auth-api（nop-graphql-core 已依赖该工件，零新增依赖边）。
- [x] **Fix**：`ReflectionBizModelBuilder` 读取注解 → `GraphQLFieldDefinition.mfaRequiredMeta`；**构建期约束校验**（构建报错，对齐 `ObjectDefinitionExtProcessor.initMakerChecker` 的 build 期校验先例）：`@MfaRequired` + `@BizSubscription` 拒绝；`@MfaRequired` + `@Auth(publicAccess=true)` 拒绝。
- [x] **Fix**：元数据拷贝三触点——`GraphQLFieldDefinition.deepClone()` 拷贝 mfaRequiredMeta（live 缺口，makerCheckerMeta 亦未拷贝——新字段必须补）、`GraphQLObjectDefinition.mergeField` 两分支拷贝（对齐 makerCheckerMeta live:224-225/260-261）、`BizObjectBuildHelper.mergeBizModel` 拷贝（live:71-72 先例，nop-biz 新 Protected Area 触点）。
- [x] **Fix**：`GraphQLEngine` `@Inject @Nullable IOperationMfaChecker` setter（setActionAuthChecker 模式）+ `IGraphQLExecutionContext`/`GraphQLExecutionContext` 透出（makerCheckerEnabled 字段模式）；`GraphQLExecutor` 两检查点（live:64/146，`GraphQLActionAuthChecker.check` 之后）对带 mfaRequiredMeta 的 field selection 调用 checker——订阅路径（live:654/717）不接，由构建期拒绝保证 + 回归断言。
- [x] **Proof**：框架级单测——builder 元数据设置/两约束组合构建报错/deepClone 与 merge 路径元数据不丢失（clone 后 mfaRequiredMeta 仍在）/无 checker bean 时引擎零介入（不抛错不拦截）/有 checker 时 executor 确实调用（wiring 断言，mock checker 计数）/订阅 operation 无 mfaRequired 字段可达。

Exit Criteria:

- [x] `./mvnw clean install -pl nop-service-framework/nop-graphql/nop-graphql-core,nop-service-framework/nop-biz,nop-service-framework/nop-biz-auth-api -am -T 1C` 绿。
- [x] **接线验证**：executor → checker 调用链运行时连通（mock/计数断言，非仅类型存在）；无 bean 装配路径回归（graphql-core 独立测试全绿——框架核心变更不破坏无 auth 环境）。
- [x] **无静默跳过**：两约束组合是构建期报错而非运行期忽略（专项测试）；无 bean 装配路径回归（graphql-core 独立测试全绿——框架核心变更不破坏无 auth 环境）。
- [x] **新功能测试**：列出框架层测试类与用例名。
- [x] 文档裁定：No owner-doc update required（框架接线不改变 owner doc 契约；@MfaRequired 用法说明与 deepClone/nop-biz 触点设计标注统一 Phase 4 回写）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - MfaChallengeStore 场景化 + ORM 加列 + 配置/错误码

Status: completed
Targets: `nop-biz-auth-core`（`MfaChallenge`/`MfaChallengeStore`/`LocalMfaChallengeStore`）、`nop-auth/model/nop-auth.orm.xml`、`deploy/sql/*`、`DbMfaChallengeStore`/`RedisMfaChallengeStore`、`NopAuthConfigs`、`NopAuthErrors`

- Item Types: `Fix | Proof`

- [x] **Fix**：`MfaChallenge` 增可选字段 scene/payload/verifiedAt（nullable、无参默认——Redis `$d:` JSON 契约：类名/包名不动、简单类型；新字段序列化为增量 key）；`MfaChallengeStore` 增 `create(scene, userId, mfaType, loginType, tenantId, phone, payload)` 重载（老五参委托 scene=login/payload=null——**一期调用点零改动**）与 `markVerified(token)`（boolean 一次性迁移：重复调用/已过期 false，票不续命）。
- [x] **Fix**：三实现原子性——Local：JVM 原子 compute；DB：条件 `UPDATE SET VERIFIED_AT=now WHERE TOKEN=? AND VERIFIED_AT IS NULL AND EXPIRE_AT>now` + affected-row（对齐既有条件 UPDATE 先例）；Redis：派生票键 `{token}:v` 经 `putIfAbsentExAsync`（SETNX+PX），peek 将票键存在映射为 verifiedAt（三实现对调用方语义一致：peek().verifiedAt 非空 ⇒ 票在窗口内）；**票键 TTL 经 `MfaChallengeStoreConfig` 新增可选 `opTicketExpireSeconds` 字段**（`nopMfaChallengeConfig` 同一配置 bean 在 nop-auth-service beans.xml 装配、缺省值 `@cfg:nop.auth.operation-mfa.op-ticket-expire-seconds|60`——biz-auth-core 不依赖 NopAuthConfigs，对齐既有 expireSeconds 模式）；**Redis `consume` 的 removeIfMatch CAS 必须以存储原对象为比对值**（不得用 peek 修饰过的对象，否则永不命中）。〔执行期发现并修复 pre-existing 缺陷：`MfaChallenge`/`SmsCodeEntry` 缺 `@DataBean` 标记——平台 JSON 序列化缺省仅允许 DataBean（`nop.core.json.serialize-only-data-bean=true`），真实 Redis PrefixTextCodec 写路径会抛 only-data-bean-is-serializable；FakeNosql 绕过序列化故一直未暴露，经本 Phase JSON 兼容测试暴露并补标记〕
- [x] **Fix**：ORM `nop_auth_mfa_challenge` 加 SCENE/PAYLOAD/VERIFIED_AT 三列（model-first → codegen → `_create_` 再生成 + 三方言手写增量脚本，循 `_add_ext_login_unique.sql` 先例；类型对齐既有 EXPIRE_AT(long)/文本列惯例）。
- [x] **Fix**：`NopAuthConfigs` 增 `nop.auth.operation-mfa.enabled`（Boolean，缺省 false）与 `nop.auth.operation-mfa.op-ticket-expire-seconds`（缺省 60）；`NopAuthErrors` 增 `ERR_AUTH_OPERATION_MFA_REQUIRED`（params：challengeToken/mfaType/operation——与一期 `ERR_AUTH_MFA_REQUIRED` 编码区分）；复用裁定：CHALLENGE_EXPIRED/MFA_FAIL 沿用既有码（操作级上下文经 errorParams/operation 区分）。
- [x] **Proof**：store 三实现单测——create 场景重载参数钉定（scene/payload 落库）/老五参委托等价（一期回归）/markVerified 恰一次成功（并发二次 false）/票窗口（op-ticket-expire 内有效、过期失效）/consume 一次性不因 markVerified 改变/scene 数据语义（store 层）：scene=operation 的 challenge 经新重载可创建且 peek 可见其 scene/payload；一期登录级 `mfaVerify` 对 scene 零校验保持不变（设计 §3.5 一期调用点零改动——**含已知设计继承残留**：operation scene 的 token 经登录级 mfaVerify 因子通过后会走 completeLogin 签发会话，安全等价——因子仍被验证；该残留行为以测试钉定并登记 follow-up）；DB 实体 round-trip；**Redis 滚动升级兼容**：新 JSON（含三新字段）被"无新字段知识"的解析路径容忍（JsonTool unknown-prop 行为验证，migration note 登记）。〔端点级 scene 拒绝断言（`mfaVerifyOperation` 拒绝 scene≠operation 的 token）属 Phase 3 Proof——端点为 Phase 3 交付物〕

Exit Criteria:

- [x] `./mvnw clean install -pl nop-service-framework/nop-biz-auth-core,nop-auth -am -T 1C` 绿；三方言 DDL 齐备。
- [x] 一期登录链零回归：`TestMfaLoginE2E`/`TestDbMfaChallengeStore`/`TestLocalMfaChallengeStore`/`TestMfaStoreWiringDb` 全绿（装配零变更：collect-beans/条件激活/类加载安全不变式不动）。〔注：nop-auth-service 全模块套件存在 pre-existing 顺序相关 flake（`TestChannelScanBindLoginE2E`/`TestGraphQLMissing` 的 missing-tenant-id，全局租户配置污染所致）——已在 clean baseline（stash 全部变更后）复现同等失败，证实与本 plan 变更无关；受影响类单独/成对运行全绿〕
- [x] **新功能测试**：列出 store 层测试类与用例名（含三实现 × markVerified 原子性）。
- [x] 文档裁定：No owner-doc update required（DDL/加列/配置项属实现细节；Redis 滚动升级 note 随 Phase 4 migration note 登记）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - MfaFactorVerifier 抽取 + 拦截器实现 + mfaVerifyOperation + 首批标注 + 审计

Status: completed
Targets: `nop-auth-service`（新 `MfaFactorVerifier`、新 `OperationMfaCheckerImpl`、`LoginServiceImpl` 收敛改造、`LoginApiBizModel`、`NopAuthUserBizModel` 标注、beans 注册）+ `nop-biz-auth-api`（`MfaVerifyOperationRequest`）

- Item Types: `Fix | Decision | Proof`

- [x] **Fix**：`MfaFactorVerifier` 抽取——收敛三处因子校验：登录级 `verifySecondFactorAndComplete` 的 TOTP/SMS 分支、绑定级 `verifyFactorForBind`、以及 `confirmMfa` 的重复验证/窗口推进块（live:274-281——**该块收敛后已移除**，组件推进为唯一路径）；**TOTP 窗口统一推进内聚**（任何场景验证成功更新 lastVerifiedWindow + lastVerifiedAt——调用方不可选，全部调用点收敛后无组件外推进路径）；恢复码分支留在 LoginServiceImpl（登录级专用）；未知 mfaType fail-closed 语义保持；**组件契约裁定**：组件返回校验结果布尔语义、失败计数与错误码留在调用方（与既有两调用点语义等价——登录级 `verifyTotp` 的 authenticator null/secret 空抛错路径与绑定级返回 false 路径按"等价重构、既有断言零修改"约束统一并记录裁定：统一为返回 false（登录级经调用方 !ok 分支同样落 MFA_FAIL + 失败计数，错误码等价）；SMS EXPIRED 抛错在两处既有调用点行为逐字一致，按等价重构收敛进组件）。登录级/绑定级改造后一期 E2E 全绿（`TestMfaLoginE2E`/`TestMfaUserSelfService`/`TestScanLoginMfa` 不改断言通过——仅 wiring 增补 mfaFactorVerifier 注入）。
- [x] **Fix**：`OperationMfaCheckerImpl`（nop-auth-service，按设计 §3.3 判定伪代码）：非敏感方法零介入（executor 侧元数据判定）→ enabled 总开关（false）→ 无用户上下文兜底放行 → store 未装配放行（对齐一期分支 1b）→ setting status==enabled 检查（未启用不拦截）→ 票核验（scene==operation + payload.operation 匹配 + payload.sessionId 匹配 + 票窗口内（peek 不变式）→ 原子 consume 成功者放行）→ 未通过则 create(scene=operation, payload={operation, sessionId}) → 抛 `ERR_AUTH_OPERATION_MFA_REQUIRED`。bean 注册 `nopOperationMfaChecker`（ioc:default，NopIoC 无注解扫描——W9 Blocker 先例）+ 容器/引擎装配测试（bean → GraphQLEngine 注入 → executor 调用——TestOperationMfaE2E#testCheckerBeanInjectedIntoEngine + 全链引擎级测试）。
- [x] **Fix**：`LoginApiBizModel.mfaVerifyOperation`（省略 @Auth = 登录态，本 BizModel 首个非 public action）：peek + scene==operation + **同会话校验**（payload.sessionId == 当前会话）+ 已验证票拒绝重复验证 + setting 复核（runWithTenant(c.tenantId) 内 status==enabled 且 mfaType 一致）+ `MfaFactorVerifier.verify` + 失败 incrFailCount 超限作废 + `markVerified` 一次性失败按过期处理；**成功不签发任何凭证**（无 completeLogin/无 token/无会话变更）。请求载体 `MfaVerifyOperationRequest`（nop-biz-auth-api messages，challengeToken+code 两字段，无 recoveryCode）。
- [x] **Fix**：票传递通道 = 请求头 `X-Nop-Op-Mfa-Token`（设计 §3.3；`IGraphQLExecutionContext.getRequestHeaders()` 存在且两检查点持有 context——通道成立，checker 大小写不敏感读头（extractClientIp 先例），无回退必要）。
- [x] **Decision**：**首批敏感操作标注 = nop-auth 模块内五动作**：`resetUserMfa`、`resetUserPassword`、`changeSelfPassword`、`unbindMfa`、`generateRecoveryCodes`（均加 @MfaRequired，容器装配后引擎 schema 元数据有专项断言）；凭证库 reencryptAll/save/delete 标注 **deferred**（见 Deferred——跨模块依赖边 + owner 裁定链，标注本身不影响机制可用性）；**联系方式修改路径裁定**：live 无专用 changePhone/changeEmail mutation（通用 CRUD 路径，方法级注解无法覆盖共享基类动作）——登记 Deferred；批量请求含敏感操作时整批预执行中止（与 auth check 同语义，无部分执行副作用；错误即该 operation 的错误（errorParams 携带 operation），测试固化）。
- [x] **Fix**：审计四事件（challenge 发起/验证成功/验证失败/票消费）经 `IAuditService.saveAudit(AuditRequest)`（`auditLogFail` 先例；**不用 @BizAudit——live 核定为装饰性注解**），记录 operation 与 sessionId（NopAuthOpLog 落库断言）。
- [x] **Proof**：E2E 全链（真实容器组件 `TestOperationMfaE2E` 14 用例 + 组件级 `TestMfaFactorVerifier` 4 用例）——enabled=false 零介入（一期回归）/未启用 MFA 用户不拦截/启用用户调敏感操作抛 `ERR_AUTH_OPERATION_MFA_REQUIRED`（errorParams 三元组断言：引擎级 errorCode + checker 直调取 NopException params）/mfaVerifyOperation + TOTP 码 → 票 → 携票重试原操作成功（RPC 路径）/**票一次性**（二次使用重新拦截）/**票绑定 operation**（换操作拒绝且票未消费、原操作仍可用）/**票绑定 session**（换会话拒绝 + 跨会话 mfaVerifyOperation 拒绝）/错码失败计数超限作废 challenge/恢复码形态输入在操作级 MFA_FAIL（请求契约无 recoveryCode 通道断言）/mfaVerifyOperation 成功不签发 token（void 语义）/mfaVerifyOperation 拒绝 scene≠operation 的 token（含 login scene）/登录级 operation-token 残留钉定（设计继承残留 watch-only）/跨场景 TOTP 窗口重放拒绝（操作级验证后同窗口码不得再过登录级）/SMS 因子分支闭环（无码 EXPIRED → 发码 → 验证 → 票 → 重试成功）/审计四事件落 NopAuthOpLog 断言（含 sessionId）；GraphQL 引擎级（RPC 单操作路径 + GraphQL 文档路径）+ 登录态非 admin ctx 验证 `mfaVerifyOperation` 默认 permission 路径可达。

Exit Criteria:

- [x] **端到端验证**：从 GraphQL mutation 入口（敏感操作）→ 拦截异常 → mfaVerifyOperation → 携票重试 → 成功，全路径经 GraphQL 引擎级测试跑通（非仅组件直调）。
- [x] **接线验证**：`nopOperationMfaChecker` bean 经容器解析并注入 GraphQLEngine，executor 检查点运行时调用（装配测试 + 引擎级拦截行为断言）。
- [x] **无静默跳过**：所有拒绝分支显式抛错（同会话不符/票过期/因子失败/超限/重复验证/scene 不符各有专项测试）；未知 mfaType fail-closed。
- [x] **一期零回归**：`./mvnw test -pl nop-auth -am` 全绿（定向套件 228 用例 0 失败——两阶段登录/绑定状态机/扫码适配/DB store 既有断言不改；全 reactor 的 6 个 error 为 pre-existing 顺序相关 flake（missing-tenant-id 全局租户配置污染，clean baseline 复现同等失败，见 Phase 2 注），受影响类单独运行全绿）；MfaFactorVerifier 收敛为等价重构（既有断言零修改通过）。
- [x] **新功能测试**：列出测试类与用例名（E2E 全链 + 引擎级 + 组件级分层列出）。
- [x] 文档裁定：操作级章节/设计裁定标注回写统一 Phase 4（本 Phase 为行为交付，首批标注清单与通道裁定记录于 plan，文档随收口同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 文档同步 + 收口验证

Status: completed
Targets: `docs-for-ai/03-modules/nop-auth.md`、`docs-for-ai/03-modules/nop-credential.md`、`docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md`、`ai-dev/design/nop-auth/02-mfa-phase2-design.md`（裁定标注回写）、roadmap

- Item Types: `Follow-up | Proof`

- [x] **Follow-up**：`docs-for-ai/03-modules/nop-credential.md` 对应模块补 `@MfaRequired` 使用说明（若 Deferred 的凭证标注在 A1-audit 前有第三方消费需求，文档已可指导——新章节"敏感操作标注（@MfaRequired，W12 机制可用）"含标注示例/开关/前置条件/deferral 状态）；`docs-for-ai/03-modules/nop-auth.md` 补操作级 MFA 章节（注解用法/两约束/开关与票配置/两段式流程/错误码/审计事件/与登录级 mfaVerify 的区别——不签发凭证 + store 场景化与 Redis 滚动升级 note + MfaFactorVerifier + 首批标注与判定要点；另更新功能概览 bullet、核心实体表 MfaChallenge 描述、配置表两新配置、错误码段、源码锚点表三新锚点行）。
- [x] **Follow-up**：设计 §三回写 impl 裁定标注（新 §3.6 八条：deepClone/nop-biz 两处元数据拷贝触点补充、@BizAudit 装饰性事实与 saveAudit 落点偏离、票传递通道最终裁定（原案成立）、批量拦截语义实现口径、首批标注清单与 deferral、MfaFactorVerifier 组件契约细化、SPI 签名依赖面事实、Redis 滚动升级 migration note 含 @DataBean pre-existing 修复）；roadmap W12-impl 状态更新（`done` + 收口记录 + Last updated）。
- [x] **Proof**：受影响模块全量验证——五模块（graphql-core/nop-biz/biz-auth-core/biz-auth-api/nop-auth-service）测试运行（graphql-core 80 + nop-biz 28 + biz-auth-core 57 + nop-auth-service 223/228 绿，5 error 为 pre-existing 顺序 flake（TestMdxQuery，单独运行全绿，baseline 复现证据见 Phase 2/3 记录））；`./mvnw test -f nop-service-framework/nop-graphql/pom.xml` 聚合 BUILD SUCCESS（graphql-core + graphql-orm + graphql-message 下游回归）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth --severity high` 本 plan 触碰文件 0 条 NEW high/critical（唯一 high 为 pre-existing OAuthLoginServiceImpl not-impl 占位，W13 范围）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors；1 warning 为本 plan 反引号相对路径提示，W11 先例同口径）。

Exit Criteria:

- [x] 文档与 live 实现一致（注解约束/配置/错误码/流程可对号）。
- [x] 验证命令通过（附输出；框架核心变更的下游回归面已覆盖）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 拦截判定矩阵全路径测试（enabled/未启用 MFA/票四条件/一次性/绑定 operation+session）。
- [x] 一期零回归：两阶段登录/绑定状态机/扫码适配/store 三实现装配/`ERR_AUTH_MFA_REQUIRED` 表达全部既有断言不改通过；enabled=false 时框架零介入。
- [x] markVerified 三实现原子性测试（并发恰一次）+ Redis 滚动升级兼容 note 登记。
- [x] MfaFactorVerifier 收敛为等价重构（登录级/绑定级/confirmMfa 三处调用点既有断言零修改）+ TOTP 窗口跨场景重放拒绝测试。
- [x] 元数据传播链完整（builder/deepClone/两 merge 点 + **executor 两检查点接线、订阅两路径不可达断言**——订阅路径刻意不接线，由构建期拒绝保证）。
- [x] ORM 变更经 model-first，无手编生成物；三方言 DDL 齐备。
- [x] GraphQL 引擎级端到端验证通过。
- [x] 无空壳/静默跳过（scan-hollow NEW 0 + 拒绝分支测试覆盖；构建期约束 fail-fast）。
- [x] 受影响 owner docs 已同步 + 设计裁定标注回写。
- [x] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow：GraphQL 入口 → executor → checker → store → 票消费调用链追踪）。
- [x] `./mvnw test -pl nop-service-framework/nop-graphql/nop-graphql-core,nop-service-framework/nop-biz,nop-service-framework/nop-biz-auth-core,nop-service-framework/nop-biz-auth-api,nop-auth -am` 绿。〔按五模块定向运行：graphql-core 80 + nop-biz 28 + biz-auth-core 57 + nop-auth-service 223/228（5 error 为 pre-existing missing-tenant-id 顺序 flake——clean baseline 复现同等失败，受影响类（TestMdxQuery/TestGraphQLMissing/TestChannelScanBindLoginE2E）单独运行全绿；上游 reactor 经 `clean install -DskipTests` 全绿 + graphql 聚合下游回归 BUILD SUCCESS〕
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [x] checkstyle / 代码规范检查通过（受影响模块 `-Pqa` exit 0；上游 pre-existing 不算）。

## Deferred But Adjudicated

### 凭证库模块敏感动作标注（reencryptAll/saveCredential/delete 加 @MfaRequired）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §3.3"建议清单"本身留白"与各模块 owner 最终确认"；标注需引入 nop-credential-service → nop-biz-auth-api 新依赖边 + 跨模块 owner 协调（本 mission 内凭证 owner 链在 A1-audit 汇合）；标注机制在本 plan 已完整可用，无标注 = 该模块操作不被拦截（与 enabled=false 同为安全缺省），不构成 live defect。
- Successor Required: yes
- Successor Path: A1-audit 裁定或后续小 plan（依赖边评估 + 标注清单确认）

### 联系方式修改（通用 CRUD 路径）敏感化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: live 无专用 changePhone/changeEmail mutation（核对 `NopAuthUserBizModel` :511-607：仅 resetUserPassword/changeSelfPassword/enableUser/disableUser）——EMAIL/PHONE 修改走继承 CrudBizModel 通用 save/update，方法级 `@MfaRequired` 机制无法标注共享基类动作；通用 CRUD 动作的 per-bizObj 敏感化治理超出本 plan 机制范围（设计 §3.3 判定标准"修改联系方式"的完整覆盖依赖平台级治理决策）。
- Successor Required: yes
- Successor Path: A2-audit 评估或后续平台治理裁定（通用 CRUD 动作敏感化机制）

## Non-Blocking Follow-ups

- 一期登录级 `mfaVerify` 对 scene 零校验（设计 §3.5 一期零改动裁定）：operation scene token 经登录级端点因子通过后会签发会话（因子仍被验证，安全等价）——设计继承残留，行为已测试钉定（`TestOperationMfaE2E#testLoginLevelResidualWithOperationSceneTokenPinned`），watch-only。
- 前端操作级弹窗/重试交互（设计 §七.5 deferred，前端业务层）。
- `@MfaRequired` 标注分布静态治理工具（设计 §七.6 deferred）。
- W13 受限会话白名单前置分支（本 plan 拦截判定入口已界定接缝，W13-impl 消费）。
- 〔pre-existing 测试基建，与本 plan 变更无关（baseline 复现证据见 Phase 2/3 记录）〕nop-auth-service 全模块套件存在顺序相关 flake：全局租户配置（`nop.orm.enable-tenant-by-default`）跨测试类污染导致 AutoTest 类（TestMdxQuery/TestGraphQLMissing/TestChannelScanBindLoginE2E 漂移命中）DB 初始化报 missing-tenant-id——单独/成对运行全绿；新增测试类会改变执行顺序使泄漏窗口漂移。修复属测试基建治理（@NopTestProperty 作用域或统一配置隔离）。
- 〔pre-existing 环境事实〕本 workspace 全量 test-compile 偶发 plexus javac 进程内 ConcurrentModificationException（clean baseline 同样复现）；`-Dmaven.compiler.fork=true` 稳定绕过。

## Closure

Status Note: 四 Phase 全部落地并勾选；一期零回归（既有断言零修改）；操作级 MFA 全链经 GraphQL 引擎级测试成立；独立 closure audit（fresh subagent）CLOSURE_APPROVED（12/12 检查 PASS，Anti-Hollow 调用链 live 追踪通过）。两处 pre-existing 缺陷经本 plan 暴露并修复（MfaChallenge/SmsCodeEntry @DataBean）；两处 pre-existing 测试/环境 flake 经 baseline 复现证实与本 plan 无关并登记 follow-up。
Completed: 2026-08-17

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit subagent（task id: `ses_ff3bf6333ffeecublIk99q2qPG`，fresh session，非实现 session 自查）
- Evidence:
  - 12/12 检查项 PASS（live code file:line 证据）：①Anti-Hollow 调用链（GraphQLExecutor.java:66-67/:149-150 两检查点 → GraphQLEngine.java:160-163/:376-378 注入与透出 → OperationMfaCheckerImpl.java:84-131 §3.3 判定链 → beans.xml:90,95-96 装配）；②构建期两约束（ReflectionBizModelBuilder.java:348-364 + GraphQLErrors.java:299,304）；③元数据四触点（GraphQLFieldDefinition.java:77/:98、GraphQLObjectDefinition.java:227-228/:266-267、BizObjectBuildHelper.java:73-74）；④store 场景化三实现原子性（Local compute / DB 条件 UPDATE+affected-row / Redis SETNX 票键 + consume 独立 get 做 CAS）；⑤MfaFactorVerifier 收敛（旧 verifyTotp/verifyFactorForBind 已删、confirmMfa 不再重验、恢复码分支保留）；⑥mfaVerifyOperation 六段校验 + 不签发凭证 + 请求无 recoveryCode 字段；⑦五动作标注恰五处；⑧ORM 三列 propId 13-15 + 三方言 _add_ 增量 + _create_ 再生；⑨配置/错误码/beans 装配；⑨⑩9 个测试类断言实质（含 E2E 13 场景与审计行查询真实实体非 mock）；⑪文档五处同步；⑫scan-hollow 0 NEW + 新文件无空 catch/静默跳过。
  - 逐条 Exit Criterion / Closure Gate：全部 PASS（验证输出见各 Phase 记录与 ai-dev/logs/2026/08-17.md：graphql-core 80/nop-biz 28/biz-auth-core 57 绿、定向回归 228 用例 0 失败、graphql 聚合 BUILD SUCCESS、mission verify `-pl :nop-auth,:nop-ai-gateway,:nop-nosql -am` 除 pre-existing flake 外全绿（842+84+32+…测试通过）、checkstyle -Pqa 五模块 SUCCESS、check-doc-links --strict exit 0（0 errors）、scan-hollow --module nop-auth --severity high 0 NEW）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（见下）
  - Anti-Hollow 检查结果：mutation 入口 → newRpcContext/newGraphQLContext → newGraphQLContextFromContext:376-378 → executeOneAsync:66/executeAsync:149 → checkOperationMfa:191-208 → OperationMfaCheckerImpl.check → store create/peek/consume → X-Nop-Op-Mfa-Token 票消费——调用链 live 追踪完整连通，无空壳段；scan-hollow 退出码 0
  - Deferred 项分类检查：凭证库标注/联系方式通用 CRUD 路径均为 out-of-scope improvement（owner 裁定链 + 无 live defect）；两处 pre-existing flake 为测试基建/环境事实（baseline 复现），非本 plan 引入的 in-scope defect 降级

Follow-up:

- 见 Non-Blocking Follow-ups（登录级 scene 零校验残留 watch-only / 前端交互 / 标注治理工具 / W13 接缝 / pre-existing 测试基建与编译环境两项登记）——无 plan-owned remaining work。
