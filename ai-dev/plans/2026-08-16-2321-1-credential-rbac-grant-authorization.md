# W11-impl(B) 凭证 RBAC 细粒度授权（NopCredentialAuth + 判定矩阵串联 + 授权管理面 + Web）

> Plan Status: completed
> Mission: nop-credential-mfa
> Work Item: W11-impl Part B（RBAC 细粒度授权）——Part A（`2026-08-14-2342-3`）Deferred 登记的 successor；W11-impl 的 `done` 以 Part A/Part B 双 plan closure audit 通过为准
> Last Reviewed: 2026-08-17
> Source: `ai-dev/design/nop-credential/02-phase2-design.md` §六（6.0-6.5 全部）+ §八 W11-impl 映射 + §七 deferred 汇总；roadmap `ai-dev/backlog/nop-credential-mfa-roadmap.md` W11-impl 条目
> Related: Part A `2026-08-14-2342-3-credential-scope-ownership.md`（**先行硬依赖**：scope/ownerId 列、`CredentialOwnership`、六旁路收口、admin-roles 配置已落地）；W3 `2026-08-12-0615-3`（其 Deferred "RBAC Fine-Grained Authorization" 的授权面由本 plan 兑现）

## Purpose

按 W9-design §六落地凭证级 RBAC 授权：新 ORM 实体 `NopCredentialAuth`（角色 ↔ 凭证实例 二值 use 授权表，默认开放、可选收紧）；provider 明文出口（`getCredential`/`getCredentialData`）在归属校验之后、解密之前串联 §6.3 判定矩阵（授权记录收紧仅作用于 system 级 + 有用户上下文的调用方）；admin-only 授权管理面（grant/revoke/查询，幂等契约）+ Web 凭证详情页授权角色编辑。无授权记录 = 一期行为完全不变（零回归基线）。

## Current Baseline

（2026-08-16 live repo 核对，独立 explore agent 复核锚点）

- ORM 源 `nop-credential/model/nop-credential.orm.xml`（153 行）：`NopCredential`（含 Part A 的 `scope` live:63-66 / `ownerId` live:67-70，`usageScope` live:39-42 已标注废弃）、`NopCredentialUsage`（live:83-110，**无 delFlag 物理删除**惯例、`(credentialId,consumerRef)` 唯一键 live:106-109）、`NopCredentialOauthState`（live:112-151）。`usages` to-many 关系 live:72-80（`cascadeDelete="true"`、`tagSet="cascade-delete"`——Part A 已去 `pub`）= 新授权实体的 cascade 先例。**无 `NopCredentialAuth` 实体**。
- codegen 链：`nop-credential-codegen/postcompile/gen-orm.xgen`（render `/nop/templates/orm` + `/nop/templates/orm-entity`）；生成物在 `nop-credential-dao/_gen/` + 手工 retention 子类；dao 访问先例 `daoProvider.daoFor(NopCredentialUsage.class)`（`CredentialProviderImpl.java:190/211/224`、`NopCredentialBizModel.java:687-691`）。
- `CredentialProviderImpl`（556 行）：明文出口 `getCredential` live:117-130（loadActive → `assertOwnershipForPlaintext` live:492-502 → `decryptToData`）；`getCredentialData` live:132-136 委托 getCredential；`mask`/`testCredential` 走 `assertOwnershipForMaskOrTest` live:508-519（owner+admin）；`loadActiveCredential` live:453-475（delFlag 先序 fail-closed live:465-468）。**system/NULL scope 在归属校验中直接放行**（live:493-495 早退）——授权记录检查的串联锚点。
- **live 核定新触点（设计 §6.3 未显式列出）**：`engineGetDecryptedFields` live:240-244 / `engineUpdateTokenFields` live:253-269 / `engineUpdateInLock` live:280-322 为引擎内部通道，**无归属/授权校验**；调用方不止 `service.oauth`：`engineGetDecryptedFields` 经 `OAuthFlowService`（live:176/229）被 `CredentialOAuthApiBizModel.beginOAuthFlow`（@BizMutation，登录态、用户上下文在线程上）可达；`engineUpdateInLock` 亦被 `NopCredentialBizModel.saveCredential`（live:277）调用——引擎通道**存在用户上下文可达路径**（但明文不外泄：beginOAuthFlow 只返回授权 URL、回调不回 token）——本 plan 须显式裁定其授权语义（见 Phase 1 Decision），否则构成收紧绕过歧义。
- `CredentialOwnership`（134 行）：`isAdmin` live:58-64（`IUserContext.isUserInAnyRole`）、`adminRoles()` live:69-81（`nop.credential.admin-roles` CSV，缺省 `admin,nop-admin`，`CredentialConfigs.java:81-82`）、`isUserScope` live:43-45（NULL 视同 system）、`canSee` live:94-99、`writeDenialReason` live:107-119。
- `NopCredentialBizModel`（787 行）：`reencryptAll` admin 运行时校验 live:573-579（复用模式）；六旁路收口 live:701-786（update/batchDelete/updateByQuery/deleteByQuery/copyForNew 禁用 + batchGet 过滤）；`defaultPrepareQuery` 结构性读过滤 live:384-398；单条归一 `requireVisibleForSingleAccess` live:450-457。
- `NopCredentialUsageBizModel`（81 行）：admin-only 查询面双层实现——action-auth（`nop-credential.action-auth.xml:64-73` roles=admin）+ 运行时 `requireCredentialAdmin()` live:72-80（`ERR_CREDENTIAL_ADMIN_REQUIRED` + `ARG_REQUIRED_ROLES`）= 授权管理面的直接先例。
- 错误码 `crypto/CredentialErrors.java`（213 行）：字符串码 `nop.err.credential.*`（`ErrorCode.define` 无数字段）；Part A 段 ARG/ERR 先例 live:175-212。
- DDL：`deploy/sql/{mysql,postgresql,oracle}/`——`_add_scope_owner_nop-credential.sql` 三方言手写增量先例（非 codegen 产物）；`_create_` 为 codegen 再生成产物。
- Web：`_vfs/nop/credential/pages/NopCredential/`（main.page.yaml 为 GenPage、`NopCredential.view.xml` 手工 delta 112 行、`_gen/` 生成）；**角色选择/grant 子表 AMIS 先例**：`nop-auth-web/.../NopAuthUser/NopAuthUser.view.xml:107-112`（跨 bizObj `@query:NopAuthDept__findList` 选择器）与 `:202-242`（role-users crud + picker 模式 `@mutation:NopAuthRole__addRoleUsers/removeRoleUsers`）；后端 grant/revoke 先例 `NopAuthRoleBizModel.java:84-102`（addRelations/removeRelations）。
- 测试基线：`TestCredentialProviderOwnership`（468 行，手工 `RoleUserContext` + `IUserContext.set` 模拟上下文）、`TestNopCredentialOwnershipBizModel`（667 行，`IGraphQLEngine` 引擎级 + `executeGraphQL` 先例）。
- 角色快照事实：nop-auth 登录时 `buildUserContext`（`LoginServiceImpl.java:790-834`）填充 `IUserContext.getRoles()`，含复合角色 `childRoleIds` 一级展开（live:810-815）——授权求交以该快照为准（§6.3 角色快照时效/字面 roleId 语义）。
- nop-credential 对 nop-auth 模块零依赖（各 pom 核实，Part A Closure Gate 复核过）——§6.1 结论 4 依赖边界前提继续成立。

## Goals

- 新实体 `NopCredentialAuth`：credentialId + roleId（(credentialId, roleId) 唯一约束）、物理删除无 delFlag、`NopCredential` to-many `cascadeDelete` 关系（凭证删除物理级联清理授权行）、审计走平台 ChangeLog（`tagSet="audit"`）。
- provider 明文出口判定矩阵串联（§6.3，归属校验之后、解密之前）：system/NULL scope → 查授权记录；无记录放行（矩阵第 4 行，一期不变）；有记录 + 无用户上下文放行（第 5 行，服务级信任）；有记录 + 用户上下文 → 角色求交 ≠ ∅ 放行否则拒绝（第 6 行，**admin 不自动豁免**）。拒绝 fail-closed 抛 `nop.err.credential.*` 新错误码；`mask`/`testCredential` 不做凭证级授权。
- 授权管理面（Decision：独立 `NopCredentialAuthBizModel`，见 Phase 2）：查询 admin-only + grant/revoke 动作（幂等契约：grant 已存在 no-op 成功 / revoke 不存在 no-op 成功；grant 拒绝 scope≠system；不做 roleId 存在性校验——依赖边界）；标准 mutation 旁路收口（对齐 Part A 六动作先例）；action-auth delta 双层。
- Web：凭证详情页授权角色编辑（grant 子表 + 角色选择/手工 roleId 输入）；管理面同时可见授权列表与引用列表（收紧运维核对语义）。
- `docs-for-ai/03-modules/nop-credential.md` RBAC 章节 + 设计 §6.3 impl 裁定标注回写。

## Non-Goals

- user 级凭证共享/角色授权叠加（§6.4 拒绝 + §七#3，owner 唯一明文出口不变）。
- 凭证分组授权（§七#4）、通用权限引擎、sitemap 资源树挂载、deny-by-default（§6.4 全部拒绝项，不翻案）。
- `usageScope` 列物理删除（Part A Deferred 已裁定 optimization candidate，非本 plan 交付）。
- 消费链上下文丢失告警审计（§七#8 watch-only residual，归 A1-audit）。
- nop-credential 依赖 nop-auth 任何模块（§6.1 结论 4）；`ICredentialProvider` SPI 签名变更；`cv1:` 密文格式变更。
- A1-audit（依赖 W11-impl 双 plan 收口后启动）。

## Scope

### In Scope

- `nop-credential/model/nop-credential.orm.xml`：新实体 + `NopCredential` 侧 to-many 关系（model-first → codegen → `_create_` 再生成 + 三方言手写增量 `_add_credential_auth_nop-credential.sql`）。
- `CredentialProviderImpl`：明文出口授权记录检查（判定矩阵第 4/5/6 行）。
- 新 `NopCredentialAuthBizModel`（admin-only 查询 + grant/revoke + 旁路收口 + bean 注册）+ xmeta（codegen 产物 + 按需 retention 壳）。
- `crypto/CredentialErrors` 新错误码；`nop-credential.action-auth.xml` delta 新增 FNPT。
- Web：`NopCredential.view.xml` 授权编辑最小面（凭证详情 grant 子表）。
- `docs-for-ai/03-modules/nop-credential.md`、设计文档裁定标注回写、roadmap 状态。

### Out Of Scope

- A1-audit；W16 深度迁移；操作级 MFA 对凭证动作的标注（归 W12-impl/A1-audit 裁定链）；Web 授权筛选 UX 增强。

## Execution Plan

### Phase 1 - ORM 实体 + provider 判定矩阵串联

Status: completed
Targets: `nop-credential/model/nop-credential.orm.xml`、`deploy/sql/*`、`CredentialProviderImpl`、`crypto/CredentialErrors`

- Item Types: `Fix | Decision | Proof`

- [x] **Fix**：ORM——新实体 `NopCredentialAuth`（credentialId + roleId + 审计字段；**surrogate PK 对齐 `usageId` 惯例** + (credentialId, roleId) 唯一约束；无 delFlag 物理删除，对齐 `NopCredentialUsage` 惯例；`tagSet="audit"` 走 ChangeLog）+ `NopCredential` 新 to-many 关系（`cascadeDelete="true"`、`tagSet="cascade-delete"`，**不加 `pub`**——对齐 Part A 后 `usages` 关系口径）。model-first：源 → codegen 重生成（禁手编 `_gen`/`_` 产物；**regen 自动产出** dao retention 实体/`INopCredentialAuthBiz` 接口/裸 `NopCredentialAuthBizModel` 骨架/xmeta retention 壳/`_service.beans.xml` bean 条目——`NopCredentialOauthState` 全链先例，**Phase 1 构建即依赖这些产物存在**（生成的 beans 条目引用类缺失则 install 红），Phase 2 在骨架上定制）→ `_create_` 再生成 + 三方言手写增量建表脚本（循 `_add_oauth_state_nop-credential.sql` CREATE TABLE 三方言先例，非 ALTER 型 `_add_scope_owner_`）。
- [x] **Fix**：`CredentialProviderImpl` 明文出口授权检查（`getCredential`/`getCredentialData`，在归属校验之后、`decryptToData` 之前）：system/NULL scope → 按 credentialId 查授权表——无记录放行（第 4 行）；有记录且无用户上下文放行（第 5 行）；有记录且有用户上下文 → `IUserContext.get().getRoles()` 与授权 roleId 集合求交，非空放行、空集拒绝（第 6 行，admin 角色不豁免）。拒绝 fail-closed 抛新错误码（含 credentialId/所需角色集参数），不返回 null/空。无记录路径保持与一期等价的单次索引存在性查询（不引入 join/远程调用）。`mask`/`testCredential` 不加授权检查（§6.3）。
- [x] **Decision**：**引擎内部通道授权语义裁定**——`engineGetDecryptedFields`/`engineUpdateTokenFields`/`engineUpdateInLock` **豁免授权记录检查**，理由：(a) 明文不外泄——`beginOAuthFlow` 只返回授权 URL（clientId 无 secret）、publicAccess 回调返回跳转页不含 token、`saveCredential` 路径有写分级门控；(b) 入口动作已被 Part A 归属/管理员判定门控（写类矩阵 + W9 发起动作回补）；(c) 与矩阵第 5 行服务级信任边界一致（收紧针对"人"的冒用，不针对服务代码与已门控入口）；`getCredential` 内惰性刷新发生在检查通过之后。**显式登记两个用户上下文可达调用点为裁定覆盖对象**（`CredentialOAuthApiBizModel.beginOAuthFlow` → `OAuthFlowService:176/229`、`NopCredentialBizModel.saveCredential:277`）。裁定 + 测试固化（引擎通道不查授权记录）+ 回写设计 §6.3 标注（措辞用上述可达性事实，**不得声称"GraphQL 不可达"**）。
- [x] **Proof**：单测（seed 授权行经 dao 直插，模拟 `TestCredentialProviderOwnership` 模式）——矩阵第 4 行（无记录 + 用户上下文放行，一期回归）/ 第 5 行（有记录 + 无上下文放行）/ 第 6 行三格（授权角色命中放行、未命中拒绝、admin 未授权拒绝）/ user 级凭证不受授权影响（归属矩阵回归）/ delFlag 先序（已删凭证报 DELETED 不进授权判定）/ mask/test 不查授权。

Exit Criteria:

- [x] `./mvnw clean install -pl nop-credential -am -T 1C` 绿；`_create_` 再生成含新表 + 三方言增量脚本齐备。
- [x] **端到端验证**：模拟用户请求上下文从 `ICredentialProvider.getCredential` 入口到放行/拒绝出口全路径测试（含授权记录存在/不存在两态）。
- [x] **接线验证**：新实体 dao 可经 `daoProvider.daoFor` 访问（codegen 产物存在 + provider 检查真实查询该表——测试断言查询生效而非空实现）。
- [x] **无静默跳过**：第 6 行拒绝分支显式抛错（专项测试）；无记录路径不吞异常。
- [x] **新功能测试**：列出矩阵测试类与用例名。
- [x] 设计 §6.3 回写：引擎通道授权语义裁定标注已落（Phase 1 Decision 项产物）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 授权管理面（BizModel + action-auth）+ Web 授权编辑

Status: completed
Targets: 新 `NopCredentialAuthBizModel`、`nop-credential.action-auth.xml`、beans 注册、`NopCredential.view.xml`、`_vfs/nop/credential/model/NopCredentialAuth/`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision**：授权管理面载体 = **独立 `NopCredentialAuthBizModel`**（设计 §6.3 留白的 impl 裁定）：对齐 `NopCredentialUsageBizModel` 先例（实体自有查询面 + admin-only 双层），避免继续膨胀 787 行的 `NopCredentialBizModel`；grant/revoke 挂该 BizModel。裁定记录回写设计 §6.3。
- [x] **Fix**：`NopCredentialAuthBizModel`（在 codegen 裸骨架上定制）——查询动作（findPage/findList/findFirst/findCount/get）运行时 admin-only（`requireCredentialAdmin` 模式：无登录态同样拒绝，对齐 usage 查询面）；**grant(credentialId, roleId)**：admin-only + 凭证存在且未删（否则 NOT_FOUND/DELETED 归一）+ 拒绝 scope≠system（user 级不叠加角色授权）+ roleId 仅非空校验（**不做存在性校验**——跨模块校验破坏依赖边界；死 roleId 无害，管理面原样展示）+ 幂等（已存在 no-op 成功，唯一约束兜底并发）；**revoke(credentialId, roleId)**：admin-only + 幂等（不存在 no-op 成功，物理删除）；标准 `save`/`update`/`delete`/`batchDelete`/`updateByQuery`/`deleteByQuery`/`copyForNew` 禁用（抛 `UnsupportedOperationException`，与 Part A 六动作同口径——绕过 grant 校验的旁路；`delete` 亦禁用：revoke 为唯一删除通道、幂等契约所在）、`batchGet` 同 admin-only 语义（usage 先例 `requireCredentialAdmin`，非行过滤）。**bean 注册零手工动作**——实体 BizModel 条目由 codegen `_service.beans.xml` 自动生成（生成物禁手编；W9 Blocker 先例仅适用于非 ORM 实体的 Api 型 BizModel），动作在 retention 骨架上实现即被 GraphQL 发现；容器接线测试验证 bean 解析与动作可达。〔执行期裁定补充：`cascade-delete` biz 级联 tag 的平台机制经子 BizModel delete 动作逐行调用，与子 delete 禁用互斥——授权行级联清理改由父侧 `prepareDeleteWithUsageCheck` 内 dao 显式物理清理（usage 拦截之后），`auths` 关系 tagSet 改 `not-pub`、保留 `cascadeDelete="true"`；见设计 §6.3 裁定标注〕
- [x] **Fix**：action-auth delta——`NopCredentialAuth:query`/`mutation` FNPT `roles="admin"`（对齐 usage 先例）。
- [x] **Fix**：Web——`NopCredential.view.xml` 凭证详情页新增授权子表（AMIS crud：`@query:NopCredentialAuth__findPage?filter_credentialId=...` + revoke 移除 + grant 新增对话框）；grant 对话框支持角色选择器（source `@query:NopAuthRole__findList`，value:roleId,label:roleName——`NopAuthUser.view.xml:107-112` 跨 bizObj 先例）或手工输入 roleId（后端只校验非空；standalone `nop-credential-app` 无 NopAuthRole GraphQL 服务时选择器为空、手工输入为主通道——docs 注明共部署语义）；详情页授权列表与 usage 引用关系共可见（收紧前核对语义）。
- [x] **Proof**：GraphQL 引擎级测试（`executeGraphQL` 先例）——admin grant/revoke 幂等往返 / 非 admin（含无登录态）grant/查询拒绝 / grant user 级凭证被拒 / grant 不存在凭证归一 NOT_FOUND / 非授予角色用户绕过 BizModel 直调 provider `getCredential` 被拒（两层防御实证）/ 凭证删除成功路径物理级联清理授权行（删除被 usage 拦截时授权行与凭证同存——正常语义测试固化）。

Exit Criteria:

- [x] 两层防御：BizModel admin 面被绕过时 provider 层授权矩阵仍拦截（测试直调 provider）。
- [x] **端到端验证**：admin 经 GraphQL grant → 非授予用户绕过 BizModel 直调 provider 被拒 → 授予角色用户放行 → revoke 后再拒，全链经 GraphQL 引擎级 + provider 级测试跑通。〔测试语义修正：单条授权 revoke 全部撤销后按 §6.3 回到第 4 行开放态（设计语义），"revoke 后再拒"以双授权场景固化 + 全撤销对称性断言〕
- [x] **接线验证**：`NopCredentialAuthBizModel` bean 经 NopIoC 容器解析、grant/revoke 动作 GraphQL 可达（容器/引擎接线测试；bean 条目为 codegen 产物禁手编）。
- [x] Web 页面文件落地（grant 子表 + 选择器/输入）且 `nop-credential-web` 模块构建绿。
- [x] 一期零回归：无授权记录时全部既有测试（provider/BizModel/W9 oauth/W7-successor `./mvnw test -pl nop-ai -am`）保持绿。
- [x] **新功能测试**：列出管理面测试类与用例名（含 GraphQL 级）。
- [x] 设计 §6.3 回写：管理面载体（独立 BizModel）裁定标注已落；其余 owner-doc 更新统一在 Phase 3（docs-for-ai 章节）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 文档同步 + 收口验证

Status: completed
Targets: `docs-for-ai/03-modules/nop-credential.md`、`ai-dev/design/nop-credential/02-phase2-design.md`（裁定标注回写）、roadmap

- Item Types: `Follow-up | Proof`

- [x] **Follow-up**：`docs-for-ai/03-modules/nop-credential.md` 补 RBAC 章节（默认开放可选收紧、判定矩阵含第 5 行信任边界与引擎通道豁免裁定、幂等契约、admin-only 管理面、角色快照时效、Web 共部署语义、`nop.credential.admin-roles` 复用）。
- [x] **Follow-up**：设计 §6.3 回写——核对 Phase 1/2 已落的裁定标注齐全（引擎通道豁免、独立 BizModel 载体），补充 roleId 不校验存在性的测试固化标注；roadmap W11-impl 条目更新（Part B 收口登记——`done` 判定说明：双 plan closure audit 通过后方可标 done，本 plan 不代劳）。
- [x] **Proof**：`./mvnw test -pl nop-credential -am` + `./mvnw test -pl nop-ai -am` 绿；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-credential --severity high` **除本 plan 预裁定禁用动作**（`NopCredentialAuthBizModel` 标准 mutation 禁用 = Part A 同模式有意设计）**外 0 条 NEW high/critical**（附 baseline vs post 输出对照；live 基线已有 6 条 Part A 预裁定发现，非本 plan 引入）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。〔实测：scan 全量 13 条 high = 基线 6 条 Part A（NopCredentialBizModel 标准 save/update/batchDelete/updateByQuery/deleteByQuery/copyForNew）+ 本 plan 7 条（NopCredentialAuthBizModel 七禁用动作，plan 预裁定同模式：显式消息 + javadoc + 收口测试断言路径 fail-closed），0 条非预裁定 NEW；check-doc-links exit 0（0 errors，2 warnings 为两份 plan 反引号相对路径提示，W9/W10/Part A 先例同口径）〕

Exit Criteria:

- [x] 文档矩阵/契约与 live 实现一致（可对号）。
- [x] 验证命令通过（附输出）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 判定矩阵（§6.3）全格测试通过（第 4/5/6 行本 plan 新增；第 2/3 行归属回归；delFlag 先序）。
- [x] 一期零回归：无授权记录的全部既有行为（provider/BizModel/W9/W7-successor 消费链）不变。
- [x] 幂等契约成立（grant 已存在 / revoke 不存在均 no-op 成功，并发经唯一约束兜底）。
- [x] 依赖边界守住：nop-credential 全模块无 nop-auth 依赖引入（`mvn dependency:tree` 复核 + import 零匹配）。
- [x] ORM 变更经 model-first，无手编生成物；三方言 DDL 齐备。
- [x] GraphQL 入口级端到端验证通过（非仅 BizModel 直调单测）。
- [x] 无空壳/静默跳过（scan-hollow 除预裁定禁用动作模式外 NEW 发现 0 + 拒绝分支测试覆盖，附 baseline 对照）。
- [x] 受影响 owner docs 已同步（docs-for-ai + 设计裁定标注）。
- [x] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow：拦截→授权→解密调用链追踪）。
- [x] `./mvnw clean install -pl nop-credential -am -T 1C` 绿。
- [x] `./mvnw test -pl nop-credential -am` + `./mvnw test -pl nop-ai -am` 绿。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [x] checkstyle / 代码规范检查通过（`./mvnw checkstyle:check -pl nop-credential -Pqa` exit 0；上游 pre-existing 违规不算）。

## Deferred But Adjudicated

### 凭证分组授权（一次 grant 一组凭证）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §七#4：单凭证粒度已满足"受限共享"场景；分组是管理便利性而非能力缺口。
- Successor Required: no（需求实证后并入 §六）

### user 级凭证共享（n8n sharing 模式）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §七#3 + §6.4：与"明文出口 owner 唯一"归属模型冲突；需求未实证。
- Successor Required: no（需求实证后重开设计）

### 消费链上下文丢失告警审计

- Classification: `watch-only residual`
- Why Not Blocking Closure: 设计 §七#8：信任边界（服务端代码不受收紧约束）为有意设计；观测增强归 A1-audit 评估。
- Successor Required: no（A1-audit 评估）

## Non-Blocking Follow-ups

- Web 授权筛选/批量 grant UX 增强（本 plan 仅详情页最小面）。
- standalone `nop-credential-app` 的角色选择器空源语义已在 docs 注明（共部署为主部署形态）。

## Closure

Status Note: 三 Phase 全部完成：Phase 1（ORM 实体 + provider 判定矩阵串联 + 引擎通道豁免裁定）、Phase 2（独立 NopCredentialAuthBizModel 管理面 + action-auth + Web 授权编辑 + GraphQL 引擎级测试）、Phase 3（docs-for-ai RBAC 章节 + 设计双裁定标注 + roadmap done 判定 + 收口验证）。执行期两处实现裁定（级联清理父侧化、revoke-all 开放态测试语义）均已回写设计 §6.3 并记录于 plan 对应 item。一期零回归基线成立（无授权记录 = 矩阵第 4 行 = 一期行为，全量既有测试 + nop-ai 消费链绿）。
Completed: 2026-08-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session，task id `ses_ff41ea54cffeaFEZY7XvSt5wlv`，general agent，2026-08-17）
- Evidence:
  - **逐项 Exit Criteria / Gates live 核查 10/10 PASS**（审计报告全文见 audit session）：
    1. ORM model-first：`nop-credential.orm.xml:123-158`（NopCredentialAuth 实体 + auths 关系 cascadeDelete/not-pub）；生成物同构齐备（`_gen/_NopCredentialAuth.java` 411 行标准 codegen 形态 + retention 双件 + `_service.beans.xml:32-37`）；三方言 `_add_credential_auth_` + `_create_` 均含新表与 UK 约束——PASS
    2. Provider 矩阵接线（Anti-Hollow）：`CredentialProviderImpl` getCredential 顺序 loadActive→归属→**授权**→解密；user 级早退、单条件索引点查无 join、空集/无登录/交集命中放行、空交抛 `role-not-granted`（credentialId+roleIds 参数）；**方法体无 isAdmin 调用 = 无 admin 旁路**（row6AdminNotAutoExempt 实证）；getCredentialData 委托共享；mask/test/三个引擎通道均无授权检查——PASS
    3. 管理面：BizModel admin 双层（defaultPrepareQuery/get/batchGet）+ grant 五重预检（admin/非空/存在/未删/system）+ 幂等 insert + revoke 幂等物理删 + **7 个 UnsupportedOperationException**（save:214/update:226/delete:237/batchDelete:247/updateByQuery:258/deleteByQuery:268/copyForNew:279 显式消息）+ action-auth `roles="admin"`——PASS
    4. 级联清理：`NopCredentialBizModel:691-712` usage 拦截先序 → auth 行物理删 → status=disabled——PASS
    5. Web：view 页 afterForm 授权子表 + revoke + grant 对话框（NopAuthRole 选择器 + creatable）+ usage 引用子表——PASS
    6. 测试真实性：22 个新测试断言逐条核实（twoLayer 五段完整/级联 0 行/拦截共存断言）；**审计本机实跑 11/11 + 11/11 全绿、全模块 166/0/0**——PASS
    7. 文档：module doc RBAC 章节矩阵与 live 语义逐条对号（含 revoke-all→第 4 行）；设计 §6.3 **两个** Part B 裁定标注（引擎豁免显式写明非"GraphQL 不可达"；独立 BizModel + 级联实现 + roleId 测试固化）；source-anchors CRED-005；roadmap Part B 收口——PASS
    8. 依赖边界：`import io.nop.auth` 主源码 **0 匹配**、pom 无 nop-auth 构件——PASS
    9. Deferred 诚实性：三项与设计 §七 #3/#4/#8 对应，非缺陷掩盖——PASS
    10. 静默空实现：新代码无空方法体/吞异常/null 占位；7 禁用为预裁定模式——PASS
  - **构建/测试门禁（executor 实跑）**：`./mvnw clean install -pl nop-credential -am -T 1C` BUILD SUCCESS；`./mvnw test -pl nop-credential -am` 绿（service 166 含 22 新增 / kms-vault 32 / web 1）；`./mvnw test -pl nop-ai -am` BUILD SUCCESS（W7-successor 消费链）；`./mvnw checkstyle:check -pl nop-credential -Pqa` exit 0；`node ai-dev/tools/check-doc-links.mjs --strict` exit 0（0 errors）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-credential --severity high` 全量 13 = 基线 6（Part A）+ 本 plan 7（预裁定禁用同模式），0 条非预裁定 NEW；`node ai-dev/tools/check-orm-icons.mjs` exit 0
  - **Anti-Hollow 检查**：拦截→授权→解密调用链追踪（audit check 2）+ GraphQL 入口级端到端（twoLayerDefenseEndToEndViaGraphQLAndProvider：grant→provider 直调拒→授予放行→revoke 再拒→全撤销开放）+ 容器接线（TestNopCredentialAuthBizModel 经 IGraphQLEngine 驱动容器 BizModel）
  - **Deferred 项分类检查**：无 in-scope live defect 被降级（三项均为设计裁定 out-of-scope/watch-only，见 Deferred But Adjudicated）
  - 审计结论：**YES 可关闭**（2 Minor 均为收口文本未补齐的机械性问题——本段即其补齐；roadmap 声明时序随本审计通过成立）

Follow-up:

- Web 授权筛选/批量 grant UX 增强（Non-Blocking Follow-ups 登记，非 defect）
- standalone `nop-credential-app` 角色选择器空源语义已在 docs-for-ai 注明（共部署为主部署形态）
- no remaining plan-owned work
