# D1 明文边界回归（对抗审计报告）

> Executor: fresh subagent（D1 维度，只读探查）
> Date: 2026-08-17
> Charter: ai-dev/audits/2026-08/2026-08-17-0518-deep-audit-nop-credential/audit-charter.md
> 基准：ai-dev/design/nop-credential/02-phase2-design.md（§二 矩阵、§3.5/§4.5/§5.5/§6.5）；docs-for-ai/03-modules/nop-credential.md

## 对抗用例结论表

| # | 用例 | 结论 | 证据锚点 |
|---|---|---|---|
| 1 | xmeta `published=false` 结构性 + GraphQL schema 中 data 不可达 | 未探查到问题 | `nop-credential/nop-credential-meta/src/main/resources/_vfs/nop/credential/model/NopCredential/NopCredential.xmeta:6`；框架机制：`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/schema/meta/ObjMetaToGraphQLDefinition.java:61-63`（`if (!published) continue;`——不生成 GraphQL 字段）；缺省值 `_ObjPropMetaImpl.java:187`（`_published = true`，唯一排除途径是显式 false）；retention delta 经 `x:extends="_NopCredential.xmeta"` 合并生效（生成基件 `_NopCredential.xmeta:34` 无 published 属性，缺省 true，被 delta 覆盖为 false）；测试固化 `TestNopCredentialBizModel.java:129-133`（selection 含 `data` → `nop.err.graphql.undefined-field`） |
| 2 | `findPage`/`get`/`batchGet` 返回体 data 恒置空 | **探查到问题（batchGet/findList/findFirst 缺置空）** | findPage ✓（`NopCredentialBizModel.java:365-373`，逐条 evict + `setData(null)`）；get ✓（`:341-356`，归一 + evict + `setData(null)`）；saveCredential 返回置空 ✓（`:300-303` oauth2 分支 / `:325-329` 常规）；batchGet ✗（`:785-799` 仅可见性过滤，无置空——见 D1-01）；另 findList/findFirst 继承动作同样无置空（`CrudBizModel.java:1540-1543` / `:475-479`，子类未覆盖）——见 D1-01 |
| 3 | `maskList` 输出仅 `****`/截断值，无明文泄漏 | 未探查到问题 | `CredentialProviderImpl.java:163-193`（mask：解密后逐字段脱敏）；`maskValue` `:606-617`（sensitive → `"****"`；非 sensitive → ≤8 全文 / >8 前 8 字符 + `"..."`）；未知类型保守全敏感 `:181-186`（`findFirst().orElse(true)`）；引擎保留字段（accessToken/refreshToken 等）不在类型 fields 声明中 → 默认 sensitive=true → `****`；三份内置类型文件敏感标注正确（`generic-secret.credential-type.xml` secret=true；`openai-api-key.credential-type.xml` apiKey=true、orgId=false（低敏感标识）；`generic-oauth2.credential-type.xml` clientSecret=true）；BizModel 层先可见性预检 `NopCredentialBizModel.java:477-480`。非 sensitive 字段前 8 字符截断为设计内行为（owner doc「敏感字段→****，非敏感字段截断」） |
| 4 | provider `getCredential`/`getCredentialData`：user 级非 owner（含 admin 非owner）/无上下文拒绝；伪造 consumerRef 无关性 | 未探查到问题 | `CredentialProviderImpl.java:123-137`：loadActiveCredential → `assertOwnershipForPlaintext`（`:499-509`：user 级 `isOwner` 唯一放行，`CredentialOwnership.isOwner:86-88` 要求 hasLoginUser——无上下文/非 owner 一律 `ERR_CREDENTIAL_OWNER_ONLY`，**admin 不例外**）→ `assertRoleAuthForPlaintext`（`:551-574`）→ 解密；`getCredentialData` 委托 `getCredential`（`:140-143`）；取用判定与 consumerRef 完全无关（`getCredential` 全链无 consumerRef 输入）；`registerUsage`/`unregisterUsage`（`:196-228`）仅引用登记非明文出口，GraphQL usage 面 admin-only（`nop-credential.action-auth.xml:62-74` + `NopCredentialUsageBizModel.java:42-43` defaultPrepareQuery → requireCredentialAdmin）；engine 通道（`engineGetDecryptedFields` 等）不查归属/RBAC 为设计 §6.3 Part B 显式裁定豁免，入口门控在位（`OAuthFlowService.beginOAuthFlow:170-173` 登录+归属写分级；回调 state capability `:208-260`） |
| 5 | delFlag fail-closed 先序（解密前），已软删凭证全出口拒绝 | 未探查到问题（解密出口全通过） | `getCredential`：`loadActiveCredential` `:466-475`（null→NOT_FOUND；`isDeleted`→DELETED）先于归属 `:126`、RBAC `:128`、解密 `:129`；`getCredentialData` 委托 ✓；`testCredential` `:147-149`（先序且不解密 data，返回占位 TestResult）；`mask` `:164-166`（先序）；engine 通道：`engineGetDecryptedFields` `:248`（loadActiveCredential）、`engineUpdateInLock` `:304-318`（probe null/isDeleted 检查先于 `:318` decryptToData）、`refreshIfNearingExpiry` → engineUpdateInLock（入口已先序）；OAuth 引擎：`OAuthFlowService.requireOauth2Credential:100-121`（null/delFlag 先序）。BizModel 面归一：get → `CrudBizModel.getEntity:905-910`（`orm_logicalDeleted` → UnknownEntityException，先于子类归属检查）；maskList/test 预检不可见 NOT_FOUND、可见者由 provider DELETED 兜底。附注：saveCredential **加密写路径**（非解密出口）对已删凭证无先序拒绝——见 D1-02（D4 关联） |
| 6 | 越权单条访问归一"不存在"（防 credentialId 枚举探测归属） | **探查到问题（saveCredential/beginOAuthFlow 未归一）** | get ✓（`NopCredentialBizModel.java:343-350` UnknownEntityException）；delete ✓（`:673-677`）；maskList/test ✓（`requireVisibleForSingleAccess:451-458` NOT_FOUND 归一）；batchGet ✓（`:796-798` 不可见行剔除）；saveCredential 更新路径 ✗（`:262-266` NOT_FOUND 后，`:269` assertWriteAllowed 对越权目标抛 `ERR_CREDENTIAL_ADMIN_REQUIRED`（system 级存在）或 `ERR_CREDENTIAL_OWNER_OR_ADMIN`（user 级存在）——错误码可区分存在性与 scope 类别）；beginOAuthFlow 同理（`OAuthFlowService.java:170-173`）——见 D1-03 |
| 7 | 字段投影攻击：任意 selection set 组合不含明文（relation/`__typename`/alias/fragment） | 未探查到问题 | schema 无 data 字段（用例 1 机制）；alias/fragment 引用不存在字段在解析期抛 `ERR_GRAPHQL_UNDEFINED_FIELD`（`GraphQLSelectionResolver.java:317,323`；测试 `TestNopCredentialBizModel.java:129-133` 固化）；`__typename` 仅返回类型名；relation 展开面：`nop-credential.orm.xml:108-115/:144-151` usage/auth → credential（tagSet="pub,ref-pub"）——展开的 NopCredential 类型字段集同样无 data（published 过滤作用于类型定义而非查询入口）；跨模块 relation：nop-ai `credentialId` 为逻辑外键**无 ORM relation**（`nop-ai/model/nop-ai.orm.xml:328-332` 注释明确「运行时消费经 ICredentialProvider」）；REST/RPC 无显式 selection 时缺省全字段只迭代 schema 字段集（`RpcSelectionSetBuilder.java:149`）+ 显式未知字段抛错（`:126-129`） |
| 8 | `_dump`/日志/错误消息无明文泄漏 | 未探查到问题（主判定）+ P3 附注 | credential-service 主代码 0 个 LOG 语句（rg 全模块无命中）；saveCredential 的 fields 明文不进任何异常 param（错误 param 仅 typeName/fieldNames 等名称类）；GraphQLEngine 服务端日志（`GraphQLEngine.java:604`）记录的异常 params 无明文字段；异常 params 不外发客户端：GraphQL 错误面转 `GraphQLErrorBean`（`GraphQLResponseBean.java:136-145`，仅 message+locations，**无 params 字段**——`GraphQLErrorBean.java:21-23`）；附注：`CredentialCipher.decrypt` 三处把完整**密文**（非明文）放 ARG_CIPHERTEXT param（见 D1-04） |

## 回归锚点结论

- **明文边界（`published=false` + BizModel 置空 + 唯一解密点）: PASS**
  - `published=false`：`NopCredential.xmeta:6` 显式声明；框架侧 `ObjMetaToGraphQLDefinition.java:61-63` 结构性跳过字段生成（GraphQL/REST/RPC 三通道共享该 schema）；解析期未知字段拒绝（`GraphQLSelectionResolver.java:317`）；测试固化（`TestNopCredentialBizModel.java:126-150`：parse 拒绝 + 响应无 data 无明文）。
  - BizModel 置空：get/findPage/saveCredential 返回前置空在位（`NopCredentialBizModel.java:300-303/:325-329/:341-356/:365-373`）；batchGet/findList/findFirst 未置空为**一期遗留的纵深防御不完备**（非二期回归：一期 batchGet 即为继承原样；W11 收口时新增了可见性过滤但未同步置空），登记为 D1-01（P2），不构成矩阵行回归。
  - 唯一解密点（明文出口）：`CredentialProviderImpl` 为唯一 SPI 明文出口，归属（owner 唯一）+ RBAC 收紧在解密之前；OAuth 引擎类不持有 `CredentialCipher`（`OAuthFlowService` 注入 `CredentialProviderImpl` 走 engine 通道，import 无 CredentialCipher）；`reencryptAll` 的进程内解密-再加密为设计明示允许（设计 §5.3，明文不出方法栈）。
- **软删除 fail-closed（delFlag 解密前先序）（D1 侧）: PASS**
  - 全部解密出口先序核对通过（对抗用例 5 证据）：getCredential/getCredentialData/testCredential/mask/engineGetDecryptedFields/engineUpdateInLock/refreshIfNearingExpiry/OAuthFlowService.requireOauth2Credential 均在解密前显式拒绝已删凭证（`ERR_CREDENTIAL_DELETED`），且 delFlag 先于归属判定（不泄露归属）。
  - 附注：saveCredential **加密写路径**对已删凭证无先序拒绝（非解密出口、一期遗留、设计 §3.5 清单外），登记为 D1-02（P3，D4 独立复核范围）。

## Findings

### [D1-01] batchGet / findList / findFirst 返回实体未置空 data——BizModel 第二层防御在继承查询动作面不完整

- 文件：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:785-799`
- 证据：
```java
@BizQuery
@Override
@GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
public List<NopCredential> batchGet(@Name("ids") Collection<String> ids,
                                    @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                                    IServiceContext context) {
    List<NopCredential> list = super.batchGet(ids, ignoreUnknown, context);
    if (list.isEmpty()) {
        return list;
    }
    IUserContext userContext = IUserContext.get();
    if (!CredentialOwnership.hasLoginUser(userContext) || CredentialOwnership.isAdmin(userContext)) {
        return list;   // <-- admin/无登录态分支：实体原样返回，data 仍为密文
    }
    return list.stream()
            .filter(entity -> CredentialOwnership.canSee(userContext, entity.getScope(), entity.getOwnerId()))
            .collect(Collectors.toList());   // <-- 过滤后同样无 evict + setData(null)
}
```
  对照 `get`（`:352-353` `orm().requireSession().evict(entity); entity.setData(null);`）与 `findPage`（`:368-371` 同）。基类 `CrudBizModel.batchGet`（`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1015-1035`）经 `dao.batchRequireEntitiesByIds` 返回 ORM 全列加载实体（data 列有值）。同样未置空的继承查询动作：`findList`（`CrudBizModel.java:1540-1543`）、`findFirst`（`:475-479`）——二者经 `invokeDefaultPrepareQuery` 享受归属过滤（`NopCredentialBizModel.defaultPrepareQuery:386-399`）但无置空钩子，子类未覆盖。
- 严重程度：**P2**
- 现状：越权/授权调用方经 GraphQL/REST 调 `batchGet`/`findList`/`findFirst` 时，返回的 `NopCredential` Java 实体对象携带完整 `data` 密文；`get`/`findPage`/`saveCredential` 三个入口则有 evict + `setData(null)` 双保险。行为不一致，防御深度不完整。
- 风险：当前无直接泄漏路径——GraphQL 响应序列化按 selection set 逐字段取值，`data` 不在 schema（published=false，用例 1 机制 + 测试固化），alias/fragment 无法引用不存在字段；REST/RPC 缺省 selection 也只迭代 schema 字段集（`RpcSelectionSetBuilder.java:149`）。但第二层防御的意义恰是兜底第一层（schema 边界）失效的场景：未来任何将 BizModel 返回实体直接 JSON 序列化的路径（调试端点、新增 RPC 序列化形式、`toString`/dump 日志）都会带出密文；且实体滞留 ORM session 携带密文扩大内存暴露面。owner doc 明文边界章节表述「BizModel 层恒置空」，本三入口与该表述漂移。
- 建议：在 `batchGet`/`findList`/`findFirst` 覆盖中复用与 `get`/`findPage` 相同的 evict + `setData(null)` 收尾（可提取私有 `clearCiphertext(entity)` 工具方法统一五处调用）；补一条与 `TestNopCredentialBizModel.java:126-150` 同构的 batchGet/findList 断言测试。
- 信心水平：高（覆盖实现、基类行为、框架序列化路径、测试四重证据）
- 误报排除：已排除「GraphQL 面当前可取到 data」——schema 结构性排除且测试固化 parse 期拒绝；已排除「admin 分支有意为之」——admin 与普通用户分支均无置空，且 get/findPage 对 admin 同样置空，无角色化设计痕迹；已排除「ORM 惰性加载不取 data 列」——`batchRequireEntitiesByIds` 全列加载，且 get/findPage 专门做 evict 防 flush 污染恰恰证明实体携带 data。

### [D1-02] saveCredential 非 oauth2 更新路径对已软删凭证无 delFlag 拒绝——已删凭证密文/元数据可被改写（墓碑篡改）

- 文件：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:261-320`
- 证据：
```java
} else {
    entity = dao.getEntityById(id);          // session.get 按主键加载，不过滤 delFlag
    if (entity == null) {
        throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)...;
    }
    // W11 写分级：system 级限管理员...、user 级限 owner+管理员
    assertWriteAllowed(entity);              // 无 delFlag 检查
    // W11 归属不可变...
    applyImmutableOwnershipInput(entity, scope, ownerId);
    if (type.isOauth2Type()) {
        ... credentialProviderImpl.engineUpdateInLock(id, ...) // 内部 probe isDeleted → DELETED ✓（CredentialProviderImpl.java:309-312）
        ...
    }
    entity.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));  // <-- 非 oauth2：直接改写已删行的密文
}
...
dao.updateEntityDirectly(entity);            // delFlag 保持 1（墓碑态不变），但 data/name 已被改写
```
  对照：provider 全部解密出口与 oauth2 分组写路径均先序拒绝已删凭证（`CredentialProviderImpl.loadActiveCredential:472-475`、`engineUpdateInLock:309-312`）；仅非 oauth2 直更分支缺失。
- 严重程度：**P3**（D4 独立复核关联项）
- 现状：对已软删凭证（delFlag=1），有写权限的调用方（owner/admin，经 `assertWriteAllowed`）调 `saveCredential(id, ...)` 非 oauth2 路径会成功改写其 data 密文与 name/typeName/updateTime，动作返回成功（实体置空 data 后返回），调用方无从得知目标是已删凭证。
- 风险：无明文泄漏（写入的是新加密结果，取用仍被 provider 侧 delFlag fail-closed 拒绝）；无权限提升（写权限与活凭证一致）；实质危害为数据完整性/审计混淆——软删除墓碑内容可被改写，若未来存在恢复通道（`recoverDeleted` 类）将恢复出被篡改的凭证；与设计 §3.5「已删除凭证不参与（发起授权、回调写入、刷新与取用）」的 fail-closed 精神不一致（该清单未显式列 saveCredential 修改路径，属一期遗留 + 设计清单外缝隙）；oauth2 与非 oauth2 两分支行为不一致（前者拒绝后者放行）。
- 建议：在 `assertWriteAllowed` 之前（NOT_FOUND 检查后）统一加 `if (isDeleted) throw ERR_CREDENTIAL_DELETED`（复用 provider 口径，顺带使 saveCredential 与全出口语义一致）；补越权+已删路径的回归测试。
- 信心水平：高（`dao.getEntityById` → `orm().get` 按主键 SELECT 不过滤 delFlag 的语义由 provider 显式 isDeleted 检查模式与 `CrudBizModel.getEntity:905-910` 的 BizModel 层删除检查双重旁证）
- 误报排除：已排除「ORM 自动过滤已删行」——若过滤则 provider 的显式 `isDeleted` 检查（`CredentialProviderImpl.java:472-475/:484-487`）与 `CrudBizModel.getEntity` 的 `orm_logicalDeleted` 检查将冗余无意义；已排除「updateEntityDirectly 会重置 delFlag」——delFlag 为已加载值 1 原样写回，delete 路径（`:710-711`）依赖 ORM logical delete 机制而非本路径。

### [D1-03] saveCredential / beginOAuthFlow 越权错误码未归一"不存在"——credentialId 枚举可探测存在性与 scope 归属类别

- 文件：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:406-421`；`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/OAuthFlowService.java:128-142`
- 证据：
```java
// NopCredentialBizModel.java:406-421（saveCredential 更新路径，assertWriteAllowed）
private void assertWriteAllowed(NopCredential entity) {
    IUserContext userContext = IUserContext.get();
    String denial = CredentialOwnership.writeDenialReason(userContext, entity.getScope(), entity.getOwnerId());
    if (denial == null) {
        return;
    }
    if ("admin-required".equals(denial)) {
        throw new NopException(CredentialErrors.ERR_CREDENTIAL_ADMIN_REQUIRED)      // system 级且存在
                ...
    }
    throw new NopException(CredentialErrors.ERR_CREDENTIAL_OWNER_OR_ADMIN)          // user 级且存在
            .param(CredentialErrors.ARG_CREDENTIAL_ID, entity.getCredentialId())
            .param(CredentialErrors.ARG_OWNER_ID, entity.getOwnerId());             // param 携带 ownerId
}
```
```java
// OAuthFlowService.java:128-142（beginOAuthFlow，assertBeginOwnership，同形错误码）
String denial = CredentialOwnership.writeDenialReason(userContext, entity.getScope(), entity.getOwnerId());
...
throw new NopException(CredentialErrors.ERR_CREDENTIAL_OWNER_OR_ADMIN)
        .param(CredentialErrors.ARG_OWNER_ID, entity.getOwnerId());
```
  对照已归一入口：get（`UnknownEntityException`，`NopCredentialBizModel.java:349`）、delete（`:676`）、maskList/test（`ERR_CREDENTIAL_NOT_FOUND`，`:455`）、batchGet（静默剔除，`:797`）。
- 严重程度：**P2**
- 现状：持有任意 credentialId 的登录用户（saveCredential 对所有登录用户开放：`nop-credential.action-auth.xml:36-42`）可经错误码三态区分目标：`not-found`（不存在/已删归一）vs `admin-required`（存在且 system 级）vs `owner-or-admin`（存在且 user 级）。
- 风险：credentialId 枚举探测归属——设计 §5.3 明确「单条访问对越权目标归一为『不存在』语义（与软删除同口径，防 credentialId 枚举探测归属）」，get/maskList/test/delete 均已落实，saveCredential 更新路径与 beginOAuthFlow（错误码经 REST `/r/` 亦可达）遗漏，破坏归一闭环的完整性：攻击者可批量探测 credentialId 的存在性与 scope 类别（user 级凭证的存在本身是归属信息）。缓解事实（已核实）：`ERR_CREDENTIAL_OWNER_OR_ADMIN` 的 `.param(ARG_OWNER_ID, ...)` 虽把归属用户 ID 放入异常 params，但 GraphQL/REST 错误面统一转 `GraphQLErrorBean`（仅 message+locations，`GraphQLResponseBean.java:136-145`、`GraphQLErrorBean.java:21-23`），params 不外发客户端，故未升级为归属者身份直接泄露；ownerId 仅进服务端日志。
- 建议：两处越权拒绝改为 `UnknownEntityException`（与 get/delete 同口径）或 NOT_FOUND 错误码（与 maskList/test 同口径），并将 `.param(ARG_OWNER_ID, ...)` 从对外可达异常中移除（保留在服务端日志通道）；补「越权 saveCredential/beginOAuthFlow 归一不存在」回归测试。
- 信心水平：高（错误码差异链、action-auth 开放面、params 不外发的序列化路径均已 live 核实）
- 误报排除：已排除「ownerId 经错误响应外发」——GraphQLErrorBean 无 params 字段且 addError 不复制（`:136-145`）；已排除「这是有意的写路径区分设计」——设计归一清单虽只列 get/maskList/test，但同属写分级的 delete 已归一（UnknownEntityException），同类动作处理不一致为遗漏痕迹而非设计区分。

### [D1-04] CredentialCipher 将完整密文串放入异常 param（ARG_CIPHERTEXT）

- 文件：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/CredentialCipher.java:91-112`
- 证据：
```java
public String decrypt(String cv1Text) {
    if (cv1Text == null || !cv1Text.startsWith(CV1_MARKER)) {
        throw new NopException(CredentialErrors.ERR_CREDENTIAL_INVALID_CIPHERTEXT_FORMAT)
                .param(CredentialErrors.ARG_CIPHERTEXT, cv1Text);      // :93 完整密文入参
    }
    ...
    if (colonIdx <= 0) {
        throw new NopException(CredentialErrors.ERR_CREDENTIAL_INVALID_CIPHERTEXT_FORMAT)
                .param(CredentialErrors.ARG_CIPHERTEXT, cv1Text);      // :103
    }
    ...
    if (!KEY_ID_PATTERN.matcher(keyId).matches() || v1Payload.isEmpty()) {
        throw new NopException(CredentialErrors.ERR_CREDENTIAL_INVALID_CIPHERTEXT_FORMAT)
                .param(CredentialErrors.ARG_CIPHERTEXT, cv1Text);      // :111
    }
```
- 严重程度：**P3**
- 现状：格式非法路径（非 `cv1:` 前缀 / 缺 keyId / keyId 域外 / 空 payload）抛出的 NopException 携带最多 4000 字符的完整密文串作为 param。
- 风险：param 值为密文而非明文（AES-256-GCM 下 ciphertext-only 无攻击面），且 GraphQL 错误面不外发 params（见 D1-03 误报排除）；实际暴露面为服务端异常日志（`GraphQLEngine.java:604` LOG.error 会打印异常 toString 含 params）与任何直接消费 NopException 的 Java 调用方。属日志卫生/防御深度问题：若未来任何路径将 ErrorBean.params 序列化进响应（非 GraphQL 通道），密文将外发；密文进日志也放大日志文件的敏感度等级。
- 建议：三处 `.param(ARG_CIPHERTEXT, cv1Text)` 改为长度/前缀摘要（如 `cv1Text.length()` 与前 8 字符）或删除该 param（错误描述已足够定位格式问题）。
- 信心水平：高
- 误报排除：已排除「这是明文泄漏」——param 是密文；已排除「GraphQL 当前外发」——GraphQLErrorBean 无 params；已排除「高危害」——仅格式非法路径触发（正常凭证密文不会走到这三处）。

## 检查范围（零发现用例的检查点清单）

- 读过的关键文件：`ai-dev/design/nop-credential/02-phase2-design.md`（全文）、`docs-for-ai/03-modules/nop-credential.md`（全文）、`NopCredential.xmeta` + `_NopCredential.xmeta`、`NopCredentialBizModel.java`（全文 800 行）、`CredentialProviderImpl.java`（全文 626 行）、`CredentialOwnership.java`（全文）、`CredentialErrors.java`、`CredentialCipher.java`（decrypt 段）、`nop-credential.orm.xml`（全文）、`NopCredential.view.xml` + `_gen/_NopCredential.view.xml`（grid 列清单无 data）、`nop-credential.action-auth.xml`、`CredentialOAuthApiBizModel.java`、`OAuthFlowService.java`（全文）、`NopCredentialOauthStateStore.java`（全文）、`NopCredentialAuthBizModel.java` / `NopCredentialUsageBizModel.java`（admin 收口抽查）、三份 `*.credential-type.xml`、`MaskedCredential.java` / `CredentialData.java` / `ICredentialProvider.java`（无 @Biz 注解）、`credential-defaults.beans.xml`（装配）。
- 框架侧机制锚点：`ObjMetaToGraphQLDefinition.java:61-63`（published 跳过）、`_ObjPropMetaImpl.java:187`（published 缺省 true）、`RpcSelectionSetBuilder.java:126-129/:149`（RPC/REST selection 构造）、`GraphQLSelectionResolver.java:317/:323`（未知字段解析期拒绝）、`CrudBizModel.java:894-916/:1012-1035/:1540-1600`（get/batchGet/findList 继承行为与 logicalDeleted 归一）、`GraphQLResponseBean.java:127-146` + `GraphQLErrorBean.java`（错误 params 不外发）、`ErrorMessageManager.java:217-355`（错误映射与 params 剥离路径）、`OrmEntityDao.java:269-274`（getEntityById 按主键加载不过滤 delFlag）。
- 测试证据：`TestNopCredentialBizModel.java:126-150`（published/data 边界 parse 拒绝 + 响应无明文断言）。
- 全模块 grep：credential-service 主代码 LOG 语句（0 命中）、`ARG_CIPHERTEXT` 使用点、跨模块到 NopCredential 的 ORM relation（0 命中，nop-ai 为逻辑外键）。
