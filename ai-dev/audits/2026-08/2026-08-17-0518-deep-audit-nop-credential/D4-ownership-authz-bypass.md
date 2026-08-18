# D4 — 归属与授权绕过对抗（Ownership & Authorization Bypass）

- **日期**：2026-08-17
- **审计人**：独立安全审计子 agent（fresh session，D4 维度）
- **仓库**：`nop-entropy-feat-credential-mfa-phase2`（live code 为准）
- **基准**：`ai-dev/design/nop-credential/02-phase2-design.md` §五/§六/§5.5/§6.5/§6.3；owner doc `docs-for-ai/03-modules/nop-credential.md`
- **方法**：只读探查 + 平台机制溯源（`GraphQLActionAuthChecker`/`SiteCacheDataBuilder`/`CrudBizModel`/`ObjMetaBasedValidator`）+ 模块测试实跑（`./mvnw test -pl nop-credential/nop-credential-service -Dtest=TestCredentialProviderRbacAuth,TestNopCredentialOwnershipBizModel,TestNopCredentialAuthBizModel,TestNopCredentialBizModel,TestCredentialProviderOwnership` — 69 tests, 0 failures）
- **结论摘要**：provider per-method 归属矩阵 / BizModel 两层防御 / RBAC 判定矩阵三态 / 6+7 旁路禁用 / 级联清理均未探查到绕过；发现 7 项问题（1×P1，2×P2，4×P3），集中在 OauthState 裸 CRUD 面与 action-auth delta 语义漂移。

---

## 一、对抗用例结论表

| # | 用例 | 结论 | 关键证据（file:line） |
|---|------|------|----------------------|
| 1 | provider per-method 归属矩阵（明文出口 owner 唯一；mask/test owner+admin；system 放行） | **未探查到绕过** | `CredentialProviderImpl.java:123-137`（getCredential：loadActive→:126 assertOwnershipForPlaintext→:128 assertRoleAuthForPlaintext→:129 解密）；:140-143（getCredentialData 委托 getCredential）；:146-149（testCredential 走 assertOwnershipForMaskOrTest）；:163-166（mask 同）；:499-509（user 级无上下文/非 owner 一律拒，**无 isAdmin 分支**——admin 不豁免明文出口）；:515-526（mask/test owner∨admin）；`CredentialOwnership.java:43-45`（仅显式 `user` 为 user 级，NULL 视同 system 放行） |
| 2 | `defaultPrepareQuery` 读过滤 + 写分级；filter 注入/queryBean 组合/orCondition 构造绕过 | **未探查到绕过** | `NopCredentialBizModel.java:386-399`（非 admin 登录用户注入 `(scope=system ∨ scope IS NULL) ∨ (scope=user ∧ ownerId=me)`，含 NULL 分支）；`QueryBean.java:232-246`（`addFilter` 恒 AND 合并——调用方 filter 只能收窄）；`CrudBizModel.java:277/310/475/1540`（findCount/findPage/findFirst/findList 统一经 `invokeDefaultPrepareQuery`）；写分级 `CredentialOwnership.java:107-119` + `NopCredentialBizModel.java:406-421`；创建规则 :213-249（普通用户 user 级强制 ownerId=本人 :233-236；system 级限 admin :242-247；无登录态不可建 user 级 :220-225）；归属不可变 :428-445 |
| 3 | 六旁路（NopCredential）+ 七旁路（NopCredentialAuth）禁用不可复活；其他入口（xbiz 同名方法/GraphQL 名碰撞/REST 直达） | **未探查到复活路径** | `NopCredentialBizModel.java:127/724/735/746/757/770` 与 `NopCredentialAuthBizModel.java:213/225/236/246/256/267/278` 均抛 `UnsupportedOperationException`；委托动作经虚分发命中禁用方法：`CrudBizModel.java:1298`（batchUpdate→update）、:1348-1353（batchModify→save/update/delete）、:1369-1372（saveOrUpdate→save/update）、:1381（save_update→saveOrUpdate）；`recoverDeleted`/`deleted_findPage` 被 `isAllowGetDeleted()=false` 拦（xmeta 未设 `biz:allow-get-deleted`，`CrudBizModel.java:1409-1411`）；many-to-many 关系动作在 prop-meta 检查处失败（`CrudBizModel.java:1633-1657`，`auths`/`usages` 非 m2m）；xbiz delta 全空（`NopCredential.xbiz` 等 4 文件 `<actions/>`），无 Java 方法被覆盖；REST `/r/{op}` 与 GraphQL 收敛到同一 biz 方法；`getCredential`/`getCredentialData` 不在任何 BizModel/接口面上（`INopCredentialBiz` 仅继承 `ICrudBiz`） |
| 4 | `batchGet` 过滤语义（不可见行剔除；ids 去重/大小写/空集合边界） | **语义成立，存在边界 oracle（→D4-04）** | `NopCredentialBizModel.java:785-799`（非 admin 登录用户按 `canSee` 剔除；无登录态/admin 不滤——provider 层兜底）；空集合→`Collections.emptyList()`（基类 `CrudBizModel.java:1018-1019`）；重复 id 由 DB IN 主键查询自然合拢；大小写=PK 精确匹配；测试固化 `TestNopCredentialOwnershipBizModel.batchGetAppliesVisibilityFilter` |
| 5 | §6.3 判定矩阵三态 + admin 不豁免 + 引擎通道两个可达调用点不可伪造 | **未探查到绕过** | `CredentialProviderImpl.java:551-574`：user 级跳过（:552-554）；无记录放行（:556-558，第 4 行）；有记录+无上下文放行（:559-562，第 5 行，`hasLoginUser` 要求 userId 非空）；有记录+有上下文角色求交、空集拒绝（:563-573，第 6 行，方法内**无 isAdmin**）；`getRoles()` 为 null 时跳过循环直接拒绝（fail-closed，:564-570）；角色来源=登录会话快照（`IUserContext.get()`←`ContextProvider` 线程上下文，`UserContextImpl.java:298-314` 字面 `contains`，外部输入不可伪造、无通配展开）；引擎通道豁免的两个用户上下文可达点均前置门控：`OAuthFlowService.java:170-173`（beginOAuthFlow：requireUserContext + assertBeginOwnership→`writeDenialReason`，system 级非 admin 拒）→:176 engineGetDecryptedFields；`NopCredentialBizModel.java:269`（saveCredential 更新路径先 assertWriteAllowed）→:278 engineUpdateInLock；回调通道=state bearer capability（`NopCredentialOauthStateStore`：128bit 安全随机、条件 UPDATE 一次性消费、TTL）。豁免有 impl 裁定标注（设计 §6.3 2026-08-17 回写）+ 测试固化（`engineGetDecryptedFieldsExemptFromAuthCheck`/`engineUpdateChannelsExemptFromAuthCheck`） |
| 6 | grant/revoke 幂等契约 + 拒绝 user 级 + 死 roleId 无害性（fail-closed 方向） | **未探查到违约** | `NopCredentialAuthBizModel.java:116-127`（grant 已存在 no-op，唯一约束兜底并发）；:148-151（revoke 不存在 no-op，物理删除）；:170-175（scope≠system 拒 `auth-not-system-scope`，NULL 视同 system 可 grant）；:108-111（roleId 仅非空）；死 roleId 按字面参与 `assertRoleAuthForPlaintext` 的 `contains` 求交（`CredentialProviderImpl.java:565-566`），无该角色用户永不命中→fail-closed；测试 `grantUserScopeCredentialRejected`/`adminGrantRevokeIdempotentRoundTrip` 通过 |
| 7 | 凭证删除物理级联清理授权行（拦截时同存语义） | **已实现** | `NopCredentialBizModel.java:691-701`（usage count>0 先抛 `ERR_CREDENTIAL_HAS_ACTIVE_USAGE`）→:702-709（拦截之后 `deleteEntityDirectly` 逐行物理删除授权行，同事务）；`nop-credential.orm.xml:84-90`（`auths` cascadeDelete=true 保留给 ORM flusher，tagSet=not-pub 不走 biz 级联）；拦截时授权行与凭证同存=正常语义（授权与引用正交） |
| 8 | action-auth delta 面（saveCredential/mutation 补偿控制；delete/reencryptAll roles 限制；双层关系） | **探查到漂移（→D4-02/D4-03）** | delta `nop-credential.action-auth.xml:47/54`（delete/reencryptAll roles="admin"）:66/70/84/88（Usage/Auth query+mutation roles="admin"）；query/mutation/saveCredential（:25-42）**无 roles 属性**；补偿控制=BizModel 运行时判定（写分级/创建分级/可见性预检，用例 1/2 证据）；平台语义：无 roles→permissionToRoles 空→全员拒（`SiteCacheData.java:79-85`、`SiteCacheDataBuilder.java:149-159`） |
| 9 | `nop.credential.admin-roles` 配置绕过（空值/通配/大小写/注入） | **未探查到绕过** | `CredentialOwnership.java:69-81`（CSV→trim→去空；字面 LinkedHashSet）；空配置→空集→`isAdmin` 恒 false（:58-64，fail-closed：无人可 admin，含 grant/revoke/usage 查询全拒）；`*` 等通配符不展开（`UserContextImpl` 字面 contains，需用户字面持有该角色名才命中）；无大小写归一（双向一致，无绕过方向）；缺省 `admin,nop-admin`（`CredentialConfigs.java:81-82`） |
| 10 | OauthState 裸 CRUD / Usage 面越权读写 | **探查到问题（→D4-01 主发现，D4-06 辅助）** | `NopCredentialOauthStateBizModel.java:9-14`（裸 codegen CRUD，零运行时判定、零旁路收口）；基线 `_nop-credential.action-auth.xml:44-57`（FNPT query/mutation 无 roles）；delta 未覆盖 OauthState；`_NopCredentialOauthState.xmeta:27-34`（credentialId/userId published+queryable+updatable；state published=false→save/update 无法供 PK，但 `delete(id)`/`deleteByQuery(query)`/`updateByQuery(query,data)` 入参直传可用，`CrudBizModel.java:1433/1472`）；usage query 面 admin-only 复核通过（`NopCredentialUsageBizModel.java:42-44/50-55/61-66`） |

---

## 二、回归锚点结论

### 锚点 1：设计 §二 矩阵行「引用计数（registerUsage/consumerRef）」——D4 侧（删除拦截语义）

**PASS**。

- 删除拦截：`NopCredentialBizModel.java:691-701` — `delete` 经 `prepareDeleteWithUsageCheck` 前置查询 `NopCredentialUsage` 计数，>0 抛 `ERR_CREDENTIAL_HAS_ACTIVE_USAGE`（fail-closed）。
- 绕过路径封死：`deleteByQuery` 禁用（:757-761，注释明示"经 doDeleteMulti→doDelete 不经过本类 delete 的引用计数拦截"）；`batchDelete` 禁用（:735）；标准 `update`/`copyForNew` 禁用使密文行不可经旁路复制/改写后绕开拦截语境。
- `registerUsage`/`unregisterUsage` 幂等（`CredentialProviderImpl.java:196-228`：先查后插/先查后删）；(credentialId, consumerRef) 唯一约束（`nop-credential.orm.xml:117-120`）。
- 测试：`TestNopCredentialBizModel`（15 tests）与 `TestNopCredentialOwnershipBizModel`（20 tests）全绿。
- Residual（不改变 PASS，登记为 D4-06）：usage 行本身可经 usage mutation 面（action-auth 静态 admin 单层）删除——删除引用行属管理员显式管理动作，非拦截契约违约。

### 锚点 2：设计 §二 矩阵行「软删除 fail-closed（delFlag 解密前先序）」——D4 独立复核

**PASS**。

- `CredentialProviderImpl.loadActiveCredential`（:460-482）：`getEntityById` → null 检查（:467-470，NOT_FOUND）→ `isDeleted` 检查（:472-475，DELETED）→ 返回。所有 getter（getCredential :124 / testCredential :147 / mask :164 / engineGetDecryptedFields :248）均先经此函数，delFlag 检查先于归属判定（:126/:149/:166）与解密（:129/:167/:250）。
- 引擎写通道 `engineUpdateInLock`（:298-329）：probe null 检查（:305-308）→ `isDeleted` 检查（:309-312）→ 锁下重读解密（:318）。
- 测试固化：`TestCredentialProviderRbacAuth.deletedReportsDeletedBeforeAuthJudgment`（通过）。
- Residual（登记为 D4-05，不改变本锚点——锚点管辖"取用/解密先序"）：BizModel `saveCredential` 非 oauth2 更新路径对已删行缺 delFlag 前置（见 finding）。

---

## 三、Findings

### [D4-01] NopCredentialOauthState 裸 codegen CRUD 面：零运行时鉴权 + 旁路动作未收口 + delta 未收紧（charter S4 确认成立）

**文件**：
- `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialOauthStateBizModel.java:9-14`
- `nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/auth/_nop-credential.action-auth.xml:44-57`
- `nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/auth/nop-credential.action-auth.xml`（全文无 OauthState 条目）
- `nop-credential/nop-credential-meta/src/main/resources/_vfs/nop/credential/model/NopCredentialOauthState/_NopCredentialOauthState.xmeta:22-43`

**证据**：
```java
// NopCredentialOauthStateBizModel.java:9-14 —— 全部继承裸 CrudBizModel
@BizModel("NopCredentialOauthState")
public class NopCredentialOauthStateBizModel extends CrudBizModel<NopCredentialOauthState>
        implements INopCredentialOauthStateBiz {
    public NopCredentialOauthStateBizModel(){
        setEntityName(NopCredentialOauthState.class.getName());
    }
}
```
```xml
<!-- _nop-credential.action-auth.xml:48-55 —— 生成的 FNPT 资源无 roles；delta（nop-credential.action-auth.xml）
     对 NopCredential/NopCredentialUsage/NopCredentialAuth 三面逐一收紧，唯独没有 OauthState 小节 -->
<resource id="FNPT:NopCredentialOauthState:query" ... resourceType="FNPT">
    <permissions>NopCredentialOauthState:query</permissions>
</resource>
<resource id="FNPT:NopCredentialOauthState:mutation" ... resourceType="FNPT">
    <permissions>NopCredentialOauthState:mutation</permissions>
</resource>
```
```xml
<!-- _NopCredentialOauthState.xmeta:27-34 —— credentialId/userId 对 GraphQL 发布且可查询/可更新；
     state（PK）published=false → 标准 save/update 无法供主键，但 delete(id)/deleteByQuery/updateByQuery
     的 id/query 是直接方法参数，不经 published 过滤 -->
<prop name="credentialId" ... queryable="true" sortable="true" insertable="true" updatable="true">
<prop name="userId" ... queryable="true" sortable="true" insertable="true" updatable="true">
```

**严重程度**：P1（条件触发的授权绕过面；后果含 OAuth 流程劫持；charter S4 已预警，本审计确认为 live 事实）

**现状**：与同模块其他三个实体面形成防御不对称——NopCredential 有归属两层防御 + 六旁路收口；NopCredentialUsage/NopCredentialAuth 有 admin-only 运行时判定（无登录态同样拒绝）+ 七旁路收口 + delta 收紧；OauthState 三者皆无。攻击面可达性取决于部署形态：(a) 部署将 `FNPT:NopCredentialOauthState:query/mutation` 资源绑定到任意非 admin 角色（基线把该资源+管理页"OAuth授权State绑定"注册进 admin sitemap，绑定是被 UI 引导的正常操作）；(b) 无 `IActionAuthChecker` 的部署（`nop-credential-app` 的 pom 不含 nop-auth-service——standalone 场景真实存在；`GraphQLActionAuthChecker.java:118-124`：checker==null 时 `isAllowAccess` 恒 true，action 层完全失效），此时唯一防线只剩 HTTP 登录过滤器。

**风险**：持有该 mutation 权限的普通登录用户可：(1) `updateByQuery(query={credentialId:受害者凭证, consumed:0}, data={credentialId:攻击者凭证})` 把受害者的 pending state 绑定重指到攻击者自己的同 clientId oauth2 凭证——受害者完成第三方授权后，回调将用攻击者凭证的 clientSecret 换取**受害者资源账号的 token 并写入攻击者凭证**（owner 可经 getCredential 读明文），构成 OAuth token 捕获；(2) `deleteByQuery` 批量清除 pending state（授权流 DoS）；(3) `findPage` 按 credentialId/userId 过滤查询，枚举"谁正在对哪个凭证发起授权"（发起时机元数据泄露，为 (1) 提供情报）。state 令牌本身 not-pub 不可读，但 (1)/(2) 均不需要读 state 值。

**建议**：对齐 `NopCredentialUsageBizModel` 先例：(1) `defaultPrepareQuery`/`get`/`batchGet` 加 `requireCredentialAdmin()` 运行时判定；(2) 禁用全部标准 mutation（该表唯一合法写方是引擎内部的 `NopCredentialOauthStateStore`，GraphQL 面本不应有任何写动作）；(3) delta 补 `NopCredentialOauthState:query/mutation` 收紧 admin；(4) `credentialId`/`userId` 列补 `not-pub`/`internal`（管理页如需展示另走受控视图）。

**信心水平**：高（代码事实与平台权限链路均经 live 溯源；攻击路径 (1) 的成立前提"攻击者可建同 clientId 的 oauth2 凭证"经 `saveCredential` 创建规则核实为可行——普通用户可自建 user 级 oauth2 凭证；唯一不确定性是部署是否实际绑定该资源/是否含 checker，故定 P1 而非 P0）。

**误报排除**：已排除"save 可伪造 state 行"——state 为 PK 且 `published=false`，GraphQL 标准 save 无法供主键（`ObjMetaBasedValidator.validateForSave` 的 selection/insertable 过滤）；已排除"回调直接伪造 state"——128bit 安全随机 + 条件 UPDATE 一次性消费（D3 侧亦有覆盖）；裸 CRUD 不是 `_gen/` 生成物问题（retention 骨架本身允许覆盖，Auth/Usage 面即为先例）。

---

### [D4-02] action-auth delta 静态 `roles="admin"` 与 `nop.credential.admin-roles` 配置形成双源真相（设计 §6.3 明文拒绝的模式）

**文件**：`nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/auth/nop-credential.action-auth.xml:45-56, 62-93`；`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialOwnership.java:69-81`

**证据**：
```xml
<!-- nop-credential.action-auth.xml:45-47 / 52-54 / 64-70 / 82-90 —— 字面 "admin" 单值 -->
<resource id="FNPT:NopCredential:delete" ... resourceType="FNPT" roles="admin">
    <permissions>NopCredential:delete</permissions>
</resource>
<resource id="FNPT:NopCredentialUsage:query" ... resourceType="FNPT" roles="admin">
```
```java
// CredentialOwnership.java:69-81 —— 运行时判定源：可配置 CSV，缺省 admin,nop-admin
public static Set<String> adminRoles() {
    String csv = CredentialConfigs.CFG_CREDENTIAL_ADMIN_ROLES.get();
    Set<String> roles = new LinkedHashSet<>();
    ... // CSV → trim → 去空
}
```
设计 §6.3（02-phase2-design.md:315）：「不用编译期静态 `@Auth(roles)` CSV，避免与配置项形成双源真相」。

**严重程度**：P2（核心契约漂移方向：admin 判定出现两个不一致的真相源；fail-closed 方向的功能缺损 + 配置陷阱）

**现状**：GraphQL 入口层（action-auth 静态 roles）只认字面角色 `admin`；BizModel 运行时层认可配置集合（缺省 `admin,nop-admin`）。两层取交集。平台语义经 `SiteCacheDataBuilder.java:149-159` + `GraphQLActionAuthChecker.isAllowAccess:134-137` 溯源确认。

**风险**：(1) 持有 `nop-admin`（nop-auth 事实管理员角色，缺省配置内）的用户在运行时层是 credential-admin，却在 GraphQL 层被 `roles="admin"` 拒绝——无法 delete/reencryptAll/查 usage/管理授权，功能静默失效且错误信息指向 auth 层 403，排障成本高；(2) 部署将 `nop.credential.admin-roles` 改为自定义角色集（如 `security-team`）时，`admin` 角色用户仍过第一层却在运行时层被拒，反之 security-team 成员被第一层全拒——配置调整"看起来生效（运行时）实际不生效（入口）"；(3) 与设计 §6.3 为 AuthBizModel 明确裁定的"运行时判定、避免双源"原则相悖（该原则只落实在 BizModel 层，delta 又引入了静态源）。

**建议**：二选一并写入 owner doc：(a) delta 的 roles 属性删除，入口层保持"部署绑定资源→角色"的通用机制，admin 语义完全收敛到 `nop.credential.admin-roles` 运行时判定（与设计 §6.3 原则一致）；或 (b) 保留静态层但把 roles 值与缺省配置对齐（`admin,nop-admin`），并在 owner doc 显式声明"静态层为部署期粗粒度预筛，运行时层为真相源"。

**信心水平**：高（两层语义与交集行为均经平台代码溯源）。

**误报排除**：不是"平台惯例即如此"的误报——nop-auth 自身的 delta（`nop-auth.action-auth.xml`）同样以资源声明为主，但 nop-credential 的特殊性在于它**自带**独立可配置的 admin 判定源（`nop.credential.admin-roles`），双源漂移是该模块特有问题；亦非生成文件问题（delta 是手写 retention 文件）。

---

### [D4-03] delta 注释宣称 query/mutation/saveCredential「对所有登录用户开放」，平台语义下实为「默认无人可访问」

**文件**：`nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/auth/nop-credential.action-auth.xml:5-10, 24-42`；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/sitemap/SiteCacheData.java:79-85`

**证据**：
```xml
<!-- nop-credential.action-auth.xml:30-34 —— 注释宣称"所有登录用户"，资源本身无 roles 属性 -->
<!-- 修改权限（含 test）：所有登录用户——角色分级由 BizModel 运行时判定执行（W11） -->
<resource id="FNPT:NopCredential:mutation" displayName="修改加密凭证" ... resourceType="FNPT">
    <permissions>NopCredential:mutation</permissions>
</resource>
```
```java
// SiteCacheData.java:79-85 —— permission 无 roles 映射 → 恒拒绝（无"登录即可用"语义）
public boolean isPermitted(String permission, ISecurityContext context) {
    Set<String> roles = permissionToRoles.get(permission);
    if (CollectionHelper.isEmpty(roles))
        return false;                      // ← 无 roles = deny-by-default
    return context.getUserContext().isUserInAnyRole(roles);
}
```

**严重程度**：P2（设计意图与实现机制漂移；fail-closed 方向的功能空洞）

**现状**：设计 §5.3/owner doc「NopCredential:query/mutation（含 test）/saveCredential 对所有登录用户开放——避免普通用户的 user 级凭证功能在 GraphQL 入口即 403 的空洞实现」。但 sitemap 的 `roles` 是 csv-set 白名单，无"所有登录用户"通配（xdef `site.xdef` `roles="csv-set"`；`SiteCacheDataBuilder` 只并集静态 roles + 动态 NopAuthRoleResource）。当前 delta 发布后，这些 permission 的 roles 映射为空 → 任何用户（含 admin，除非 `nop.auth.skip-check-for-admin=true` 或动态绑定）在 GraphQL 层被拒，user 级凭证功能开箱不可达，直到部署手工为各角色绑定 `FNPT:NopCredential:query/mutation/saveCredential` 资源。

**风险**：(1) 设计所依赖的"补偿控制声明"（入口放行 + 运行时分级）只有后半句成立——安全上 fail-closed 无害，但功能上普通用户的 user 级凭证全链路 403，会驱动部署者用"把资源绑给 Everyone 角色或滥用 skip-check-for-admin"等方式修复，后者（skip-check-for-admin=true）会把 admin 全局提权到所有被拒面（包括 D4-01 的 OauthState 面），形成诱导性配置恶化；(2) 注释与实际语义相悖误导后续维护者与审计者。

**建议**：(a) 在 nop-auth 中引入或利用现有"全员角色"（若平台存在 all-users 占位角色则显式写进 roles；不存在则在 owner doc + delta 注释中改为"需部署绑定到全员角色"），或 (b) 至少修正注释并 owner doc 登记"开箱需部署期资源绑定"这一事实，把绑定步骤写进部署清单。

**信心水平**：高（权限链路四段——ReflectionBizModelBuilder:339-340 权限注册 → GraphQLActionAuthChecker → DefaultActionAuthChecker → SiteCacheData——全部 live 溯源）。

**误报排除**：不是"部署反正会绑定"的误报——设计文本明确以"入口不 403"为补偿控制论据，该论据当前不成立；生成基线 `_nop-credential.action-auth.xml` 同样无 roles，非 delta 合并丢失（delta 覆盖同 id 资源未加 roles 属性，合并结果仍无 roles）。

---

### [D4-04] `batchGet` 存在性 oracle：缺 id 抛 UnknownEntityException 而不可见 id 静默剔除，与自称的"与 get 同口径归一"不符

**文件**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:785-799`；`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1015-1035`

**证据**：
```java
// NopCredentialBizModel.java:788-798
List<NopCredential> list = super.batchGet(ids, ignoreUnknown, context);  // ← 缺 id 时 batchRequire 抛错
...
return list.stream()
        .filter(entity -> CredentialOwnership.canSee(userContext, entity.getScope(), entity.getOwnerId()))
        .collect(Collectors.toList());                                     // ← 不可见行静默剔除
```
```java
// CrudBizModel.java:1022 —— ignoreUnknown=false 时缺 id 直接抛 UnknownEntityException
List<T> list = ignoreUnknown ? dao.tryBatchGetEntitiesByIds(ids) : dao.batchRequireEntitiesByIds(ids);
```

**严重程度**：P3（低危信息泄露：凭证 ID 存在性枚举；PK 为 UUID 不可猜测，实际可利用性低）

**现状**：`get()` 对不可见目标归一为 UnknownEntityException（与不存在同口径，:343-350）；`batchGet(ignoreUnknown=false)` 对**不存在**的 id 抛错、对**存在但不可见**的 id 静默剔除——两类目标产生可区分响应。注释自称"与 get 的'归一不存在'同口径的批量形式"，实际不同口径。逻辑删除行同样在基类抛错（:1027-1029，`orm_logicalDeleted` → UnknownEntityException），进一步区分"已删除"状态。

**风险**：能通过 credentialId 枚举探测凭证是否存在/是否已删除（不泄露 scope/owner/内容）；对拿到部分 ID 泄露（日志、外链引用）的攻击者是前置情报。UUID 空间使盲枚举不可行，故降为 P3。

**建议**：过滤阶段收集被剔除的 id，若其中有基类未抛错者且 `ignoreUnknown=false`，统一抛 `UnknownEntityException`（真同口径）；或文档化放弃归一并删除"同口径"注释。

**信心水平**：高（`OrmEntityDao.checkAllEntityValid` 抛错行为 + 基类代码直读）。

**误报排除**：非 Nop 平台误报——基类行为是平台语义，但子类覆盖引入的"静默剔除 vs 抛错"分叉是该模块自己的归一化承诺（注释明示）未兑现。

---

### [D4-05] `saveCredential` 非 oauth2 更新路径对已软删除凭证缺 delFlag fail-closed（与 oauth2 分支行为不一致）

**文件**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:261-307`；`CredentialProviderImpl.java:304-312`

**证据**：
```java
// NopCredentialBizModel.java:262-269 —— getEntityById 不滤 delFlag（IEntityDao.java:142 按 PK 直取）
entity = dao.getEntityById(id);
if (entity == null) {
    throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)...;
}
// W11 写分级（无 delFlag 检查）：
assertWriteAllowed(entity);
...
// 非 oauth2 路径 :306 —— 直接整包重加密回写已删行：
entity.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));
```
```java
// CredentialProviderImpl.java:309-312 —— oauth2 分支经 engineUpdateInLock 有 isDeleted 拒绝（不一致点）
if (isDeleted(probe)) {
    throw new NopException(CredentialErrors.ERR_CREDENTIAL_DELETED)...;
}
```

**严重程度**：P3（删除终局性/审计一致性缺陷；不产生明文或越权——已删行取用仍全路径 fail-closed，delFlag 不被清除）

**现状**：对已软删除凭证调用 `saveCredential(id=已删id, ...)`：非 oauth2 类型会通过写分级后改写该行 data/name/typeName 并提交；oauth2 类型经 engineUpdateInLock 抛 `ERR_CREDENTIAL_DELETED`。两分支行为分叉。取用路径（get/mask/test/provider 全出口）对已删行保持拒绝，无复活效果。

**风险**：通过写分级的调用方（system 级=admin、user 级=owner+admin）可改写已删凭证的密文与元数据——破坏删除的审计终局性（delete 时刻的密文快照被覆盖，事后取证失真）；owner 可"腾挪"已删 user 级凭证内容。同时 `delete` 被引用计数拦截的凭证与已删凭证在 saveCredential 下行为不可区分。

**建议**：更新路径在读到 entity 后、`assertWriteAllowed` 前统一加 `isDeleted` 检查（抛 `ERR_CREDENTIAL_DELETED`，与 oauth2 分支、与 provider 语义对齐）。

**信心水平**：高（两分支代码直读；`getEntityById` 无逻辑删除过滤为平台既定语义）。

**误报排除**：非设计豁免——设计 §3.5 列举的"不参与"清单未含 saveCredential，但 oauth2 分支已实现拒绝（impl 自加），证明意图是拒绝，非 oauth2 分支为实现遗漏。

---

### [D4-06] NopCredentialUsageBizModel mutation 面单层防护：无运行时 admin 判定、旁路动作未收口（与 Auth 面七动作收口不对称）

**文件**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialUsageBizModel.java:33-81`；`nop-credential.action-auth.xml:62-75`

**证据**：
```java
// NopCredentialUsageBizModel.java:41-66 —— 仅查询面（defaultPrepareQuery/get/batchGet）有 admin 判定；
// save/update/delete/batchDelete/updateByQuery/deleteByQuery/copyForNew 全部继承裸实现
@Override
protected void defaultPrepareQuery(QueryBean query, IServiceContext context) {
    requireCredentialAdmin();
}
```
```xml
<!-- nop-credential.action-auth.xml:69-73 —— mutation 面唯一防线：静态 roles="admin"（单层） -->
<resource id="FNPT:NopCredentialUsage:mutation" ... resourceType="FNPT" roles="admin">
    <permissions>NopCredentialUsage:mutation</permissions>
</resource>
```

**严重程度**：P3（纵深防御缺口；突破前提=部署期资源重绑定或 checker 缺失，与 D4-02/D4-01 同类条件）

**现状**：usage 查询面双层（静态 admin + 运行时 requireCredentialAdmin，无登录态同样拒绝）；mutation 面仅 action-auth 静态单层。NopCredentialAuthBizModel 对全部七个标准 mutation 收口禁用（"对齐 Part A 先例"），usage 面未做——而 usage 行是引用计数拦截的数据基础。

**风险**：若部署将 `FNPT:NopCredentialUsage:mutation` 绑定到非 credential-admin 角色（或在无 checker 部署中），该角色可 delete/update usage 行——注销消费方引用后，原本被 `ERR_CREDENTIAL_HAS_ACTIVE_USAGE` 拦截的凭证删除即被解锁，间接削弱一期引用计数契约。另：usage 行的合法写方只有 SPI registerUsage/unregisterUsage（服务端），GraphQL mutation 面本无正当用户场景。

**建议**：对齐 Auth 面先例——usage 的标准 mutation 全部禁用（`UnsupportedOperationException`，理由"登记/注销走 SPI 唯一通道"）；至少为 mutation 入口补 `requireCredentialAdmin()`。

**信心水平**：高（类全文直读，无其他覆盖）。

**误报排除**：非设计有意为之——owner doc 表述"usage 查询面限管理员"未提 mutation，但 Auth 面的同场合已选择"查询 admin-only + 七 mutation 禁用"双层口径，usage 面单层属遗漏而非裁定（设计/owner doc 均无"usage mutation 保留"的裁定记录）。

---

### [D4-07] `saveCredential` 更新路径错误码构成存在性/scope 探测 oracle（读路径已归一、写路径未归一）

**文件**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:262-266, 406-421`

**证据**：
```java
// NopCredentialBizModel.java:263-266 vs 412-420 —— 三种可区分错误
if (entity == null) {
    throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)...;      // 不存在
}
...
if ("admin-required".equals(denial)) {
    throw new NopException(CredentialErrors.ERR_CREDENTIAL_ADMIN_REQUIRED)...; // 存在且 system 级
}
throw new NopException(CredentialErrors.ERR_CREDENTIAL_OWNER_OR_ADMIN)...;     // 存在且 user 级(非本人)
```
对照 `get()`（:343-350）：不可见目标统一 `UnknownEntityException`（防 credentialId 枚举探测归属，设计 §5.3 明文）。

**严重程度**：P3（信息泄露量小：ID 存在性 + 三态 scope 归类；需通过写分级前的错误响应）

**现状**：设计只要求 get/maskList/test（及 delete 的可见性）归一"不存在"；saveCredential 更新路径在 NOT_FOUND / ADMIN_REQUIRED / OWNER_OR_ADMIN 间区分，越权调用方可借此推断目标 ID 是否存在、是 system 级还是他人 user 级。delete 也先经 canSee 归一再分级（:673-677），saveCredential 是唯一未归一的写入口。

**风险**：与 D4-04 同类的存在性 oracle，附加 scope 归类信息；为定向攻击（如 D4-01 的 state 重指攻击挑选目标）提供侦察通道。设计未要求写路径归一（明文错误利于合法调用方排障），故仅记一致性建议。

**建议**：评估将更新路径的越权分支（ADMIN_REQUIRED/OWNER_OR_ADMIN 场景中调用方本不可见的目标）归一为 NOT_FOUND；或 owner doc 显式声明"写路径不归一"为接受的残余。

**信心水平**：中高（行为确定；是否算缺陷取决于对设计归一条文范围的解读——条文只列读单条动作，故 P3）。

**误报排除**：非误报于"错误码不外泄"——Nop 的 `.param()` 与 errorCode 会进入 GraphQL/REST 错误响应（平台标准行为）。

---

## 四、检查范围（零遗漏声明）

**主审文件（live 全文阅读）**：
- `CredentialProviderImpl.java`（626 行，含 engine 通道/惰性刷新/归属与 RBAC 断言）
- `CredentialOwnership.java`（134 行）
- `NopCredentialBizModel.java`（800 行）
- `NopCredentialAuthBizModel.java`（281 行）
- `NopCredentialUsageBizModel.java`（81 行）
- `NopCredentialOauthStateBizModel.java`（14 行）
- `NopCredentialOauthStateStore.java`、`OAuthFlowService.java`、`CredentialOAuthApiBizModel.java`
- `CredentialConfigs.java`
- `nop-credential.orm.xml`（NopCredentialAuth 实体 + auths cascadeDelete :84-88 + oauth_state 表）
- xmeta 全套（`NopCredential.xmeta`/`_NopCredential.xmeta`/`_NopCredentialOauthState.xmeta`/`NopCredentialAuth.xmeta`）与 xbiz 全套（4 对，均空 delta）
- action-auth 三文件（delta、生成基线、app 聚合）+ `nop-credential.data-auth.xml`（空）
- `_service.beans.xml`（bean 注册确认）

**平台机制溯源（判定依据）**：`GraphQLActionAuthChecker`、`DefaultActionAuthChecker`、`SiteMapProviderImpl`/`SiteCacheData`/`SiteCacheDataBuilder`、`ReflectionBizModelBuilder:321-341`（权限注册）、`IActionAuthChecker`（MultiCsvSet OR 语义）、`CrudBizModel`（findPage/findList/findFirst/findCount/batchGet/delete/updateByQuery/deleteByQuery/batchUpdate/batchModify/saveOrUpdate/recoverDeleted/deleted_findPage/addManyToManyRelations）、`QueryBean.addFilter`（AND 合并）、`IEntityDao.getEntityById`（无逻辑删除过滤）、`ObjMetaBasedValidator`（published/insertable/updatable 过滤）、`IUserContext`/`UserContextImpl`（角色字面 contains）、`action-auth.xdef`/`site.xdef`（roles=csv-set 白名单语义）。

**测试实跑**：`TestCredentialProviderRbacAuth`(11)、`TestCredentialProviderOwnership`(12)、`TestNopCredentialOwnershipBizModel`(20)、`TestNopCredentialAuthBizModel`(11)、`TestNopCredentialBizModel`(15) — 全部通过。

**未深入（归其他维度）**：KMS 启动链路（D3）、密文格式与轮换（D5）、nop-ai 消费侧上下文传播（D6）、OAuth 协议细节与 state 存储原子性（D2；本维度仅复核其与授权的接缝）。
