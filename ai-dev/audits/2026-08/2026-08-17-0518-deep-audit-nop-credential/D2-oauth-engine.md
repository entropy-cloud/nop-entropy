# D2 — OAuth 引擎对抗审计报告

- **审计日期**：2026-08-17
- **审计维度**：D2 OAuth 引擎对抗（state 篡改/重放/并发双回调；回调参数注入；过期 state 复用；并发刷新竞态互踢；伪造保留字段破坏刷新状态机；disabled 凭证路径遗漏）
- **基线**：`ai-dev/design/nop-credential/02-phase2-design.md` §三/§3.5/§二；owner doc `docs-for-ai/03-modules/nop-credential.md`
- **方法**：live code 全量阅读（OAuth 引擎 4 类 + BizModel 面 + provider 引擎通道 + ORM/xmeta/action-auth/beans 装配）+ 平台机制下钻（CrudBizModel 动作面、ObjMetaBasedValidator 输入过滤、ObjMetaToGraphQLDefinition 输出过滤、SiteCacheData 权限判定）验证攻击面可达性
- **约束遵守**：只读审计，未修改任何产品代码

## 检查范围

| # | 文件 | 说明 |
|---|------|------|
| 1 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/CredentialOAuthApiBizModel.java` | API 面（beginOAuthFlow/oauthCallback） |
| 2 | `.../service/oauth/OAuthFlowService.java` | 流程引擎（发起/回调/结果页） |
| 3 | `.../service/oauth/NopCredentialOauthStateStore.java` | state 存储（create/peek/consume/清理） |
| 4 | `.../service/oauth/OAuthTokenClient.java` + `OAuthTokenResponse.java` | 令牌端点客户端 |
| 5 | `.../service/entity/NopCredentialOauthStateBizModel.java` | state 表 codegen CRUD 面（S4 探查线索） |
| 6 | `.../service/entity/NopCredentialBizModel.java` | saveCredential/test/收口面 |
| 7 | `.../service/CredentialProviderImpl.java` | 引擎通道 + 惰性刷新 |
| 8 | `.../service/CredentialOwnership.java`、`.../config/CredentialConfigs.java`、`.../registry/DefaultCredentialTypeRegistry.java` | 判定工具/配置/类型校验 |
| 9 | `nop-credential/model/nop-credential.orm.xml`（oauth_state :160-199）、`nop-credential-meta/.../NopCredentialOauthState/_NopCredentialOauthState.xmeta` | 数据模型与 GraphQL 可见性 |
| 10 | `nop-credential-web/.../auth/nop-credential.action-auth.xml` + `_nop-credential.action-auth.xml`、`_service.beans.xml`、`pages/NopCredentialOauthState/*` | 权限装配与管理页 |
| 11 | 平台机制核对：`CrudBizModel.java`、`ObjMetaBasedValidator.java`、`ObjMetaToGraphQLDefinition.java`、`OrmEntityCopier.java`、`SiteCacheData.java`、`StringHelper.generateUUID` | 攻击面可达性判定依据 |

---

## 一、对抗用例结论表

| # | 用例 | 结论 | 关键证据（file:line） |
|---|------|------|----------------------|
| 1 | state 单次消费原子性 | **探查到防护** | `NopCredentialOauthStateStore.java:99-109`：条件 UPDATE `WHERE STATE=? AND CONSUMED=0 AND EXPIRE_AT>?` + `affected > 0` 判定；单语句 DB 原子，并发双回调恰一个成功，无先读后写的双消费窗口（peek 仅取绑定数据，消费语义全部由条件 UPDATE 承载） |
| 2 | state 不可预测性 + TTL | **探查到防护** | `NopCredentialOauthStateStore.java:60-62` → `StringHelper.java:1082-1089`（`MathHelper.secureRandom().nextBytes(16)` = 128bit CSPRNG → 32 hex）；TTL：`create :78` 写 `expireAt`，回调判定 `OAuthFlowService.java:215-216`，consume 条件含 `EXPIRE_AT > now`（:105）；TTL 缺省 600s（`CredentialConfigs.java:47-48`） |
| 3 | 未命中/过期/重放统一 fail-closed | **探查到防护** | `OAuthFlowService.java:214-223`：peek null / expireAt null / 过期 / consume 失败统一抛 `ERR_CREDENTIAL_OAUTH_STATE_INVALID`，不区分细节；空参 :209-211 为独立 `PARAM_MISSING`（仅区分"缺参"与"state 无效"，不泄露 state 内部状态） |
| 4 | 回调公开面最小化 | **探查到防护** | 全模块 grep `publicAccess`：唯一注解命中 `CredentialOAuthApiBizModel.java:58`（oauthCallback）；`beginOAuthFlow` :46-47 为默认登录态。注 :25/:52 与 `CredentialProviderImpl.java:547` 为注释文本 |
| 5 | 回调响应载体不含 token 明文 | **探查到防护** | `OAuthFlowService.java:267-283`：`buildResultPage` 只输出静态 HTML + 配置的 result-page-url（`escapeHtml` 转义）；token 仅经 `engineUpdateTokenFields`（:257）进密文列，不出现在任何响应体。转义语境混用见 D2-05（P3，配置信任边界内） |
| 6 | beginOAuthFlow 归属 + 校验链完整性 | **探查到防护（一处缺口见 D2-02）** | `OAuthFlowService.java:170-186`：`requireUserContext`(:171) → `requireOauth2Credential`(:172；存在 :102 / 未删 :106 / 类型 oauth2 :111 / 未禁用 :116) → `assertBeginOwnership`(:173 → `CredentialOwnership.writeDenialReason` :107-119：system=admin、user=owner+admin) → clientSecret 非空检查 :179-183。缺口：clientId 为空不拒绝（:177-190 空则放 `""` 进授权 URL），见 D2-02 |
| 7 | disabled 凭证全路径拒绝 | **探查到防护（两处边界备注）** | 发起/回调：`requireOauth2Credential` :116-119；取用与惰性刷新入口：`CredentialProviderImpl.loadActiveCredential` :477-479 → `assertOauth2NotDisabled` :335-341；引擎通道：`engineGetDecryptedFields` :249。边界 1：`engineUpdateInLock` 锁内 probe 只查 delFlag（:309-311）不复查 disabled → TOCTOU 窗口（D2-04，P3）。边界 2：saveCredential 覆盖路径不拒 disabled 的 oauth2 凭证——设计 §3.5 显式增量只声明"发起/回调/刷新/取用"四路径，**与设计声明一致，不构成违约**（且当前管理面无独立 disable 动作，disabled 未删态仅能经手工 DB 产生） |
| 8 | saveCredential 保留字段拒绝 + 分组写 | **探查到防护** | 拒绝：`NopCredentialBizModel.java:197-208`（oauth2 类型输入含 `OAUTH_RESERVED_FIELD_NAMES` 一律 `ERR_CREDENTIAL_RESERVED_FIELD_INPUT`）；分组写 :276-304：`engineUpdateInLock` 锁下 merged = 保留字段（从 current 原样保留 :282-286）+ 人工字段整包替换（:288），元数据与 data 同一 UPDATE（customizer :291-297）；registry 层双保险：类型文件占用保留名拒绝（`DefaultCredentialTypeRegistry.java:159-169`，不分 authType 对全部类型生效） |
| 9 | 惰性刷新互斥（并发恰一次、无互踢） | **探查到防护（一处写放大见 D2-03）** | `CredentialProviderImpl.java:367-436`：非临期 fast-path 不持锁 :375-377；临期进 `engineUpdateInLock`（:380 → :298-328：REQUIRED 事务 + `dao.lockEntity`（SELECT FOR UPDATE）+ evict 后锁下重读 :314-315）→ 锁下双重检查 :381-398（已被先行者刷新则直接返回新 token，不再刷新）→ 单凭证"刷新 vs 刷新"、"刷新 vs 人工保存"共用同一行锁入口（跨副本 DB 行锁互斥成立）。语义推演：两个并发取用 → 串行进锁 → 后到者读到先行者刷新后的 `expiresAt` → `nowInLock < curExpiresAt - window` → 返回 current，无第二次 refresh 外呼、无一次性 refresh token 互踢。缺陷：无变化分支仍执行整行重加密回写（:319-327 无条件），见 D2-03 |
| 10 | NopCredentialOauthStateBizModel 暴露面 | **探查到重大缺口（D2-01，P1）** | 裸 CRUD 面（`NopCredentialOauthStateBizModel.java:9-13` 无任何动作覆盖/禁用）+ action-auth 生成基线无 roles（`_nop-credential.action-auth.xml:44-57`）且 delta 未覆盖该实体（`nop-credential.action-auth.xml` 仅覆盖 NopCredential/Usage/Auth 三实体）+ xmeta `credentialId/userId` 可读可写、`consumed/expireAt` 可写（`_NopCredentialOauthState.xmeta:22-51`）+ `updateByQuery/deleteByQuery/save` 等 @BizMutation 平台默认注册（`CrudBizModel.java:527/:1304/:1433/:1472`）。详见 D2-01 |
| 11 | 回调参数注入（code/state 特殊字符/超长/空值） | **探查到防护** | state：空值 fail-closed（:209-211 / peek isEmpty :90-91）；进入 SQL 仅经 `dao().getEntityById(state)`（ORM 参数化，`NopCredentialOauthStateStore.java:92`）与 consume 的 `?` 绑定（:105）——无拼接，超长（>VARCHAR(64)）即 PK 不命中 → STATE_INVALID fail-closed。code：仅进入 token endpoint form 表单（`OAuthTokenClient.java:60-66`，`DATA_TYPE_FORM` 由 IHttpClient 编码，无 header/URL 注入面）；响应解析 `JSON.parseToBean`（:106-107）与 error 字段 fail-closed（:117-129）。备注：code 无本地长度上限即外呼（轻微，见用例结论；IHttpClient 层有常规请求体约束，攻击者仅能自扰） |

## 二、回归锚点结论（设计 §二 矩阵 OAuth 相关行 + §3.5 兼容声明对抗复核）

| 锚点 | 结论 | 证据 |
|------|------|------|
| `cv1:` 密文格式不变（token 集为新字段） | **PASS** | token 集经 `CredentialCipher.encrypt` 写入 data JSON（`CredentialProviderImpl.java:321`、回调链 `OAuthFlowService.java:257` → `engineUpdateTokenFields` → `engineUpdateInLock`）；引擎类不持有 cipher（OAuthFlowService/OAuthTokenClient 无 CredentialCipher 依赖） |
| 明文边界不变（回调只进不出） | **PASS** | 公开回调仅接收 code/state（`CredentialOAuthApiBizModel.java:59-63`），返回体为服务端构造的静态 HTML 跳转页（`OAuthFlowService.java:267-283`），无 token 明文；token 明文仅存在于服务端引擎通道（`engineGetDecryptedFields`/`engineUpdateTokenFields` 非 SPI 面、不经 BizModel 返回） |
| 软删除 fail-closed 不变 + oauth2 disabled 显式增量（四路径） | **PASS（附 D2-04 TOCTOU 备注）** | 发起/回调：`OAuthFlowService.requireOauth2Credential:106-119`；取用/惰性刷新：`CredentialProviderImpl.loadActiveCredential:472-479` + `assertOauth2NotDisabled:335-341`（resolveType 容忍未注册类型保持一期语义 :348-354）；非 OAuth 类型仅 delFlag（一期语义零变更） |
| 引用计数不变 | **PASS（N/A）** | OAuth 引擎零触碰 `NopCredentialUsage`/registerUsage 链路 |
| §3.5 "30x 重定向"显式偏离（W9-impl 裁定：WebContentBean HTML 跳转页） | **PASS（与裁定一致）** | `OAuthFlowService.java:262-283` 实现与设计 :67 裁定标注逐条对应（200 + meta-refresh/JS location，未配置时静态完成提示） |
| "单一公开回调端点" + publicAccess 面最小化 | **PASS** | 用例 4（全模块唯一 `@Auth(publicAccess=true)`） |
| state bearer capability 模型（不可预测 + 一次性 + 短 TTL） | **PASS 于引擎侧，FAIL 于管理面** | 引擎侧三性质成立（用例 1/2/3）；但 state 表的**完整性与可用性**被未收口的 CRUD 管理面侵蚀（D2-01）——state 本体保密性仍成立（xmeta `published=false`，`_NopCredentialOauthState.xmeta:23`），credentialId 绑定关系与 consumed 标记可被普通登录用户篡改/删除 |
| saveCredential 分组写 + 保留字段拒绝（§3.3 增量） | **PASS** | 用例 8 |

---

## 三、Findings

### [D2-01] NopCredentialOauthState 裸 codegen CRUD 面未收口：普通登录用户可读/伪造/篡改/删除任意 OAuth state 绑定行（DoS 在途授权 + 条件性 token 落点劫持 + consumed 重置重放面）

**文件路径:行号**：
- `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialOauthStateBizModel.java:9-13`
- `nop-credential/nop-credential-web/src/main/resources/_vfs/nop/credential/auth/_nop-credential.action-auth.xml:44-57`
- `nop-credential/nop-credential-meta/src/main/resources/_vfs/nop/credential/model/NopCredentialOauthState/_NopCredentialOauthState.xmeta:22-51`
- `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:527,1304,1433,1472`（平台基线动作）
- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/sitemap/SiteCacheData.java:79-84`（权限语义）

**证据代码片段**：

```java
// NopCredentialOauthStateBizModel.java:9-13 —— 裸 CRUD 面，无任何动作禁用/归属过滤/admin 判定
@BizModel("NopCredentialOauthState")
public class NopCredentialOauthStateBizModel extends CrudBizModel<NopCredentialOauthState>
        implements INopCredentialOauthStateBiz {
    public NopCredentialOauthStateBizModel(){
        setEntityName(NopCredentialOauthState.class.getName());
    }
}
```

```xml
<!-- _nop-credential.action-auth.xml:47-56 —— 生成基线：query/mutation 无 roles 属性 -->
<resource id="FNPT:NopCredentialOauthState:query" displayName="查询OAuth授权State绑定"
          i18n-en:displayName="Query OAuth State" orderNo="10008" resourceType="FNPT">
    <permissions>NopCredentialOauthState:query</permissions>
</resource>
<resource id="FNPT:NopCredentialOauthState:mutation" displayName="修改OAuth授权State绑定"
          i18n-en:displayName="Modify OAuth State" orderNo="10009" resourceType="FNPT">
    <permissions>NopCredentialOauthState:mutation</permissions>
</resource>
```

```java
// SiteCacheData.java:79-84 —— 平台语义：无 roles 的 permission 对所有（登录）用户放行
public boolean isPermitted(String permission, ISecurityContext context) {
    Set<String> roles = permissionToRoles.get(permission);
    if (CollectionHelper.isEmpty(roles))
        return true;
    return context.getUserContext().isUserInAnyRole(roles);
}
```

```xml
<!-- _NopCredentialOauthState.xmeta:22-43 —— state 不可见（published=false），但
     credentialId/userId/expireAt/consumed 均可读可写（insertable/updatable=true；
     输入过滤只看 insertable/updatable：ObjMetaBasedValidator.java:498-510，
     internal=true 不参与输入与输出判定） -->
<prop name="state" ... tagSet="var,not-pub" published="false" insertable="true" updatable="false" .../>
<prop name="credentialId" ... insertable="true" updatable="true"/>
<prop name="userId" ... insertable="true" updatable="true"/>
<prop name="expireAt" ... insertable="true" updatable="true" internal="true" .../>
<prop name="consumed" ... insertable="true" updatable="true" internal="true" .../>
```

**严重程度**：P1

**现状**：设计将 `nop_credential_oauth_state` 定位为"引擎内部存储"（设计 §八 W9-impl 裁定；owner doc 核心实体表："一次性消费 + TTL 过期 + 惰性清理，**引擎内部存储**"），但 live 实现保留了 codegen 的完整管理面且四层防线全部缺席：(a) BizModel 裸（对照：`NopCredentialBizModel` 禁用六个旁路动作、`NopCredentialUsageBizModel`/`NopCredentialAuthBizModel` 运行时 admin-only，唯独 OauthState 无任何覆盖）；(b) action-auth delta（`nop-credential.action-auth.xml`）覆盖了 NopCredential/Usage/Auth 三个实体的权限条目，**未覆盖 NopCredentialOauthState**，生成基线条目无 roles = 全体登录用户可用；(c) xmeta 中 `credentialId`/`userId`/`expireAt`/`consumed` 可作为 GraphQL 查询输出与 save/update 输入（`state` 本体 published=false 不可见，但 `CrudBizModel.updateByQuery`/`deleteByQuery`（:1433/:1472）按 QueryBean 条件（credentialId/userId 均 queryable）批量定位，无需知道 state 值）；(d) web 模块生成了完整管理页（`pages/NopCredentialOauthState/main.page.yaml` + view）与菜单资源。

**风险**（普通登录用户即可执行，均无需 admin 角色）：
1. **DoS（无前提、确定性）**：`NopCredentialOauthState__deleteByQuery(query: credentialId=<目标凭证>)` 或按 userId 删除任意用户全部在途 state 行 → 受害者发起授权后回调一律 `STATE_INVALID` fail-closed，10 分钟 TTL 窗口内授权闭环被持续打断。
2. **token 落点劫持（条件性）**：`updateByQuery(query: credentialId=<受害者凭证>, data: {credentialId: <攻击者凭证>})` 篡改绑定 → 受害者在 IdP 完成授权、code 回调时 state 匹配的已是攻击者的凭证实例 → 引擎用攻击者凭证的 clientSecret 换 token 并把 token 写入**攻击者的凭证** → 攻击者经 `getCredential` 取得受害者授权产生的 token。成立条件：两个凭证实例录入同一 OAuth app（同 clientId/clientSecret——"多系统共用密钥服务"重复录入同 client 是模块核心场景）；credentialId 可经 `NopCredential__findPage` 获知（system 级对普通用户可见）。
3. **伪造绑定行**：`save` 可插入任意 `state`（insertable=true）+ credentialId + userId 的伪造行（引擎写路径走 dao 直写/jdbcTemplate，不经 xmeta，加固 xmeta 不影响引擎）。
4. **consumed 重置重放面**：`update`/`updateByQuery` 可把已消费行的 `consumed` 置 0 并延长 `expireAt`——单独利用需同时持有 code（通常不可得），但配合网络层截获回调 URL 时重放窗口被人为恢复。
5. **侦查面**：`findPage` 返回 credentialId/userId/expireAt/consumed——可观测任意用户对任意凭证的授权发起与完成时机。

**建议**（按对齐先例的最小改动）：
1. `NopCredentialOauthStateBizModel` 禁用全部标准写动作（save/update/batchDelete/updateByQuery/deleteByQuery/copyForNew/batchModify/saveOrUpdate，抛 `UnsupportedOperationException`，与 `NopCredentialBizModel` 六动作同口径）；查询动作按"引擎内部存储"语义同样禁用或收紧 admin（若保留审计查询需求）。
2. action-auth delta 为 `FNPT:NopCredentialOauthState:query`/`mutation` 补 `roles="admin"` 条目（与 NopCredentialUsage 同构双层防御）。
3. xmeta 加固：`credentialId`/`userId`/`expireAt`/`consumed` 置 `updatable="false"`（引擎写不经 xmeta，零影响）。
4. web 模块移除 `pages/NopCredentialOauthState` 管理页与菜单资源（或挂 admin）。
5. 补回归测试：普通登录用户经 GraphQL 调 `NopCredentialOauthState__deleteByQuery/updateByQuery/save` 应被拒。

**信心水平**：high（四层证据链完整：裸 BizModel 源码 + 生成基线无 roles + 平台 `isPermitted` 空角色放行语义 + xmeta 可写属性与 ObjMetaBasedValidator 过滤规则；动作可达性由 `_service.beans.xml:16-22` bean 注册 + CrudBizModel @BizMutation 注解确证）

**误报排除**：已排除以下可能：(a) "GraphQL 未发布该 bizObj"——`_service.beans.xml:18-22` 注册 `biz_NopCredentialOauthState` BizProxyFactoryBean，且 action-auth/管理页均按已发布实体生成；(b) "xmeta internal=true 会挡住输入"——`ObjMetaBasedValidator.java:498-510` 输入过滤谓词只测 `isInsertable()/isUpdatable()`，`ObjMetaToGraphQLDefinition.java:60-63` 输出只测 `isPublished()`，internal 不参与两者（grep 全 graphql/biz 模块无 isInternal 消费点）；(c) "state 列 not-pub 已足以防护"——not-pub 仅隐藏 state 值本身（保密性），不保护行的完整性与可用性，queryBy 条件列（credentialId/userId）+ 按条件批量动作绕过 PK 不可见限制；(d) Nop 平台误报校准项（BizModel 返回实体、@Inject protected、`_` 前缀生成文件）不适用——本发现指向的是生成基线与 retention 文件（BizModel/xmeta/action-auth delta 均为可编辑的非 `_` 文件或 delta 层）的**缺失收口**，属模型/装配层缺陷而非生成物内容问题。

---

### [D2-02] beginOAuthFlow 不校验 clientId 非空：校验链缺口产出必然失败的授权 URL 与无谓 state 行

**文件路径:行号**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/OAuthFlowService.java:176-199`

**证据代码片段**：

```java
Map<String, Object> fields = credentialProvider.engineGetDecryptedFields(credentialId);
String clientId = (String) fields.get(FIELD_CLIENT_ID);
String clientSecret = (String) fields.get(FIELD_CLIENT_SECRET);
if (StringHelper.isEmpty(clientSecret)) {                    // :179 只查 clientSecret
    throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_CLIENT_CREDENTIALS_MISSING)
            .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId)
            .param(CredentialErrors.ARG_FIELD_NAMES, FIELD_CLIENT_SECRET);
}
...
params.put("client_id", StringHelper.isEmpty(clientId) ? "" : clientId);   // :190 空则放 ""
```

**严重程度**：P3

**现状**：发起路径对人工字段的完备性校验只覆盖 `clientSecret`；`clientId` 为空时静默以空串构造授权 URL（IdP 必然拒绝），state 行已被创建并占用 TTL 窗口。回调侧则是两者同查（`handleOAuthCallback` :232-236，`ERR_CREDENTIAL_OAUTH_CLIENT_CREDENTIALS_MISSING` 且 ARG_FIELD_NAMES 为 `clientId,clientSecret`），两侧校验链不对称。

**风险**：配置不完整的 oauth2 凭证（录了 secret 漏了 clientId）在发起时得到"看似成功"的授权 URL，用户走完 IdP 跳转才失败或直接被 IdP 拒绝——错误暴露点后移、state 表无谓写入；与"发起时完成实例完备性校验"的设计意图（§3.3 发起 action 校验链）不符的局部实现缺陷。无安全后果（fail 方向是拒绝而非放行）。

**建议**：`beginOAuthFlow` 将 :179 的检查改为 clientId 与 clientSecret 同时非空（对齐回调侧 :232 的字段集），ARG_FIELD_NAMES 相应输出两者。

**信心水平**：high

**误报排除**：非"空 clientId 导致换 token 失败"的运行时容错问题——发起动作是显式校验链位点（设计 §3.3 步骤 1："已录入 clientSecret——发起时 biz action……完成校验"），实现只落了一半；也不是回调侧行为（回调侧校验完备）。

---

### [D2-03] 惰性刷新锁内双重检查命中"无需刷新"分支时仍执行整行重加密回写（写放大 + updateTime/version 无谓漂移）

**文件路径:行号**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java:318-327, 380-398`

**证据代码片段**：

```java
// engineUpdateInLock —— updater 返回后无条件重加密回写（:318-327）
Map<String, Object> current = decryptToData(entity).getFields();
Map<String, Object> updated = updater.apply(current);
entity.setData(credentialCipher.encrypt(JsonTool.stringify(updated)));   // 无变化也重写
entity.setUpdateTime(new Timestamp(System.currentTimeMillis()));
if (entityCustomizer != null) { entityCustomizer.accept(entity, updated); }
dao.updateEntityDirectly(entity);

// refreshIfNearingExpiry 锁内分支 —— "已被先行者刷新"/"无 expiresAt" 返回 current 原值（:381-398）
Long curExpiresAt = asEpochMillis(current.get("expiresAt"));
if (curExpiresAt == null) { return current; }
if (nowInLock < curExpiresAt - refreshWindowSeconds(type) * 1000L) {
    return current; // 已被并发先行者刷新，不再临期
}
```

**严重程度**：P3

**现状**：并发取用同一临期凭证时，N 个后到者依次进锁、依次在锁内发现"已被刷新"并返回 `current`，但 `engineUpdateInLock` 不区分"有变化/无变化"——每次都执行 decrypt → stringify → `AESTextCipher` 重加密（AES-GCM + PBKDF2，CPU 非平凡）→ 整行 UPDATE（含 updateTime 与乐观锁 version 递增）。

**风险**：正确性无虞（幂等重写，密文随 IV 变化属正常），但：(a) 高并发临期窗口内串行锁 + 每者一次重加密造成写放大与锁持有时长叠加；(b) `updateTime`/`version` 被无语义变化的写推进，污染审计信号（"token 被更新过"与"什么都没发生的重写"不可区分）。

**建议**：`engineUpdateInLock` 在 `updater` 返回引用等于入参 `current`（或引入显式"无变化"哨兵/比较 map 相等）时跳过加密回写直接返回。

**信心水平**：high

**误报排除**：不是"并发刷新互踢"（用例 9 已验证互斥与双重检查语义正确、refresh 外呼恰一次）；不是 PBKDF2 每次解密成本抱怨（那是一次性既有成本）——本发现限定在"判定无需变更后仍写"这一可避免分支。

---

### [D2-04] 回调/刷新链的 disabled 校验存在 TOCTOU 窗口：engineUpdateInLock 锁内 probe 只查 delFlag 不复查 status

**文件路径:行号**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java:304-316, 335-341`；`OAuthFlowService.java:226-257`

**证据代码片段**：

```java
// engineUpdateInLock 锁内 probe（:304-316）—— 只查存在与 delFlag，不查 disabled
NopCredential probe = dao.getEntityById(credentialId);
if (probe == null) { throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)...; }
if (isDeleted(probe)) { throw new NopException(CredentialErrors.ERR_CREDENTIAL_DELETED)...; }
session.evict(probe);
NopCredential entity = dao.loadEntityById(credentialId);
dao.lockEntity(entity);

// 回调链 disabled 检查均发生在锁外（requireOauth2Credential :116-119 →
// engineGetDecryptedFields :249 assertOauth2NotDisabled）→ 之后才 engineUpdateTokenFields :257
```

**严重程度**：P3

**现状**：回调链上 disabled 检查执行两次（`requireOauth2Credential` :116-119、`engineGetDecryptedFields` :249），但都在行锁获取之前；`engineUpdateInLock` 锁内重读 probe 后只复查 delFlag。检查与写回之间存在窗口：管理员在此窗口将凭证置 `disabled`（当前管理面仅有软删除路径会同时置 delFlag——该路径会被锁内拦截；"disabled 但未删"需手工 DB 或未来新增的禁用动作），token 回写/刷新仍会完成。

**风险**：窗口内完成的是一次多余的 token 写入（保留字段、密文落库），无明文外泄、无越权（发起时归属已校验）；下一次取用起即被 `loadActiveCredential` 拒绝。属 fail-closed 语义在极端竞态下的边缘不闭合，当前可触发性低（无独立 disable 管理动作），但若后续版本补齐 disable 功能（owner doc 已预留 status 语义），该窗口自动放大。

**建议**：`engineUpdateInLock` 锁内 probe 处追加 `assertOauth2NotDisabled(probe)`（与 delFlag 同点），一行闭合窗口。

**信心水平**：medium-high（代码路径确证；实际可利用性受"当前无 disable 管理动作"制约，判级已据此压至 P3）

**误报排除**：不是"disabled 路径遗漏"（用例 7 已确认四路径入口均有检查）；saveCredential 覆盖路径不拒 disabled 与设计 §3.5 四路径声明一致，不在本发现范围。

---

### [D2-05] 回调结果页将 resultUrl 以 HTML 转义注入 JS 字符串上下文（转义语境混用，配置信任边界内的纵深防御缺口）

**文件路径:行号**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/OAuthFlowService.java:274-281`

**证据代码片段**：

```java
html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
        + "<meta http-equiv=\"refresh\" content=\"0;url=" + StringHelper.escapeHtml(resultUrl) + "\"/>"
        + "<title>Authorization Complete</title></head>"
        + "<body><p>OAuth authorization complete. Redirecting...</p>"
        + "<script type=\"text/javascript\">window.location.replace(\""
        + StringHelper.escapeHtml(resultUrl) + "\");</script></body></html>";
```

**严重程度**：P3

**现状**：`resultUrl` 来自配置项 `nop.credential.oauth.result-page-url`（`CredentialConfigs.java:69-71`），仅经 `escapeHtml`（转义 `< > & " '` 与 nbsp，`StringHelper.java:136-142`）后同时注入 meta-refresh 属性与 `<script>` 内的 JS 双引号字符串。HTML 转义在 script 块内不解码实体，`"` → `&quot;` 恰不闭合 JS 引号、`<` → `&lt;` 恰防 `</script>` 闭合——当前字符集下不可逃逸；但反斜杠与换行未被处理（配置含换行会使该 script 语法错误、meta-refresh 仍工作——自损非攻击），且"HTML 转义用于 JS 语境"属语境混用，未来字符集或模板改动下脆弱。

**风险**：resultUrl 为管理员级配置输入（信任边界内），非用户可控，当前无现实注入路径；缺陷在于纵深防御形态不规范——若该页未来加入任何请求衍生数据或配置来源下沉，语境混用即为隐患。

**建议**：JS 上下文改用 JSON 编码（`JSON.stringify(resultUrl)` 产出带引号字面量）或对 resultUrl 加 URL 格式校验（`http/https` scheme 白名单）后再注入。

**信心水平**：high（语义分析）；影响评级 low（配置信任边界）

**误报排除**：不是已成立的 XSS（escapeHtml 字符集在两个注入点均恰好覆盖逃逸需求）；token 明文不出现在响应体（用例 5 结论不受影响）。

---

## 四、结论摘要

OAuth 引擎本体（发起/回调/state 一次性消费/惰性刷新互斥/保留字段防线）对 charter 威胁模型的实现质量高：11 项对抗用例中 10 项在引擎侧探查到设计声明的防护，回归锚点全部 PASS。唯一的结构性缺口在**引擎外圈**：state 表的 codegen CRUD 管理面四层防线（BizModel 收口/action-auth/xmeta 写权限/web 管理页）全部缺席，使"引擎内部存储"的设计定位被普通登录用户可达的读/伪造/篡改/删面侵蚀（D2-01，P1）——这是与 NopCredential/NopCredentialUsage/NopCredentialAuth 三实体已做收口的同族遗漏。其余 4 项 P3 为局部实现瑕疵（校验链不对称、无变化回写写放大、锁内 disabled 复查缺失、转义语境混用）。
