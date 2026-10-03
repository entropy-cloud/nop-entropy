# nop 平台授权面审计：与外部 agent 平台「零归属校验」模式的对标

> Status: resolved
> Date: 2026-10-03
> Scope: nop-entropy 的数据权限应用点、REST/GraphQL 入口层、认证过滤器与租户信任边界、ORM 层租户硬隔离。基线对照物为 `ai-dev/analysis/2026-10/2026-10-03-agent-platform-docker-usage-and-auth-management-survey.md` 中审计的 10 个外部 agent 平台。
> Conclusion: nop **不存在** MagesticAI 式「REST 路由零归属校验」——`CrudBizModel` 30+ 个 CRUD 入口中 28 个有数据权限覆盖（含全部批量操作逐条校验），HTTP 层默认 fail-closed。但存在 **3 个此前未记录的真实缺陷**（EQL `UPDATE`/`DELETE` 不注入租户条件、`copyForNew` 源记录无数据权限校验、文档丢失决定租户隔离的全局开关），以及 1 个**文档回归**：2026-06-22 已补入 `tenant-model.md` 的全局开关小节在后续迁移中丢失。

## Context

- 触发：外部 agent 平台调研（见同目录 Docker/鉴权报告）发现 MagesticAI 存在「`routes/git.py` 1585 行 0 处归属校验、`terminal.py` shell 零校验」的缺陷，需要判定 nop 平台自身是否存在同类风险模式。
- 判定标准（「归属校验」在 nop 的对应物）：平台没有「记录所有者」模型，其等价物是**三层权限**——HTTP 路径认证 / 操作权限（action auth）/ 数据权限（data auth）+ ORM 层租户硬隔离。因此审计拆成四条轴逐一核实。
- 审计方式：4 个并行审计代理按统一 rubric 实读代码，产出后由本人**独立复核 6 条最关键结论**（复核结果见 §6），区分「既有审计已记录」与「新发现」。
- 只读审计，未修改任何产品代码或配置。

## 参考基线：外部平台的失败模式

| 失败模式 | 代表证据 |
|---|---|
| REST 路由零归属校验 | MagesticAI `routes/git.py` 1585 行 0 处 `owner_id`/`Depends(get_current_user)`，`path` 为客户端任意宿主机路径 |
| 授权钩子空实现 | codeforge-agent 931 行薄壳，9 项硬需求 3 项零实现 |
| 鉴权默认关闭并写成测试契约 | kandev `profiles.yaml:60-63` prod=false + `httpmw/middleware.go:34-41` 注入 RoleAdmin |
| 出厂示例关闭鉴权且无生产断言 | optio `.env.example` `OPTIO_AUTH_DISABLED=true` + `oauth/index.ts:44-46` 仅读 env |
| 「有认证无授权」 | MagesticAI JWT 实现质量高，但资源层零归属校验 → 任一登录用户 = 任意命令执行 |

---

# Part A — 数据权限（data-auth）应用点

## A1. 结论：框架基类覆盖完整，不存在平台级零校验

`CrudBizModel`（`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java`）的外部可达入口（`@BizQuery`/`@BizMutation`）覆盖情况：

| 入口 | 机制 | 判定 |
|---|---|---|
| `findCount`/`findPage`/`findFirst`/`findList`/`findRoots`/`asDict`/`deleted_*`/`findTreeEntityPage` 等 | `prepareFindPageQuery` → `AuthHelper.appendFilter` | ✅ 查询过滤 |
| `get`（:1034）/`deleted_get`（:1046） | `getEntity` → **`checkDataAuth`:954** | ✅ 逐记录 |
| `batchGet`（:1069） | `doBatchGet` 循环内 **:1093 checkDataAuth** | ✅ **逐条** |
| `save`（:552） | `checkDataAuth(METHOD_SAVE)`:572 | ✅ |
| `update`（:863） | `requireEntity`:1003 + `checkDataAuthAfterUpdate`:885 | ✅ 双重 |
| `delete`（:1106） | `doDeleteEntity` → `checkDataAuth`:1262 | ✅ 单记录校验（非只靠 WHERE） |
| `batchUpdate`/`batchDelete`/`batchModify` | 逐条委派到 update/delete | ✅ 逐条 |
| `updateByQuery`（:1513）/`deleteByQuery`（:1576） | `doFindList0`（查询过滤）+ `doUpdateMulti`/`doDeleteMulti` → 逐条 checkDataAuth | ✅ 双重 |
| `recoverDeleted`（:1489） | `checkDataAuth`:1501 | ✅ |
| `addManyToManyRelations`/`remove`/`update` | `get(id)` + 显式 `checkDataAuth` | ✅ |
| **`copyForNew`（:1802）** | 源记录**无** checkDataAuth | ❌ **新发现，见 A3** |
| `deleteEntity`（:2152，`@BizAction`） | 只有 `checkMetaFilter` | ⚠️ 内部缺陷，见 A4 |

**与 MagesticAI 的量级差异**：MagesticAI 是「路由层零校验」，nop 是「基类逐入口校验 + 业务代码偶有绕过」。

## A2. codegen 不引入绕过

模板 `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm/{appName}-service/.../service/entity/{!entityModel.notGenCode}{entityModel.shortName}BizModel.java.xgen:10-15` 生成的类为纯透传（不引用 `dao()`/`getEntityById`/`requireEntity`，全仓 `rg` 零命中），100% 走基类保护路径。

## A3. 🔴 新发现：`copyForNew` 对源记录零数据权限校验

`CrudBizModel.java:1808-1858`（`@BizMutation`，外部可达 `POST /r/Xxx__copyForNew`）：

```java
1812:  Object id = data.get(OrmConstants.PROP_ID);        // 客户端可控
1815:  T entity = dao.requireEntityById(id);              // 读任意行，无过滤
1817:  checkMetaFilter(entity, objMeta, context);        // 仅 xmeta 静态 <filter>，非行级归属
...
1851:  checkDataAuth(SELECTION_COPY_FOR_NEW, entityData.getEntity(), context);  // 校验对象已是 newEntity
```

- `checkDataAuth` 只作用于**新建行**（新建行本无归属），**源行从未被校验**
- `checkMetaFilter`（:1008-1019）跑的是 xmeta 上的静态 `<filter>`，不是按用户的行级判定
- 克隆范围是整行：`objMeta.getFieldSelection("copyForNew")` 在未声明 `<selection id="copyForNew">` 时返回 `null`（`IObjMeta.java:109-111`），走 `entity.cloneInstance()` 整行克隆；codegen 模板只生成 `pageSelection/listSelection/formSelection`，**默认即整行克隆**
- **净效果**：任意已认证用户传 `data.id = 他人行 ID` → 读到他人整行深拷贝，落成自己名下

**为何是新发现**：既有审计/roadmap 中所有 `copyForNew` 提及均为**写侧**收口（`nop-credential` 禁 `copyForNew` 绕过密文行拦截，见 `ai-dev/audits/2026-08/2026-08-17-0518-deep-audit-nop-credential/D4-ownership-authz-bypass.md:36`），**无人指出读侧归属缺口**。文档 `auth-and-permissions.md` 亦未提及。

## A4. 内部缺陷：`deleteEntity` 缺 `checkDataAuth`（注释与代码不符）

`CrudBizModel.java:2147-2177`：`:2150` 注释写「包含数据权限检查」，实际 `:2161` 只有 `checkMetaFilter`，无 `checkDataAuth`。同接口的 `saveEntity`:2104 / `updateEntity`:2138 都有。`@BizAction` 非外部入口（`ReflectionBizModelBuilder.java:168-188` 明确 `@BizAction` 不进 GraphQL operation 面），属 defense-in-depth 缺陷。

## A5. 🔴 新发现：数据权限总开关默认关闭且文档零提及

```
nop-kernel/nop-api-core/src/main/java/io/nop/api/core/ApiConfigs.java:92
    varRef(s_loc, "nop.auth.enable-data-auth", Boolean.class, false);
```

关闭时 `context.getDataAuthChecker()` 为 null → `AuthHelper.appendFilter` 原样返回（`AuthHelper.java:29-30`）、`CrudBizModel.checkDataAuth` 直接 return（`CrudBizModel.java:780-782`）。

**`rg "enable-data-auth" docs-for-ai/` → 0 命中**。而文档 `auth-and-permissions.md:9` 写的是「数据权限由 `nopDataAuthChecker` 控制，**自动附加**到查询条件中」，读者会误以为默认开启。该开关已在 `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md:187` 被认定为已知项，但**从未回写 `docs-for-ai/`**。

## A6. 正面发现（文档未写但行为正确）

`DataAuthModel.initCheckerFromFilter()`（`nop-biz-auth-core/.../model/DataAuthModel.java:18-26`）：只写 `<filter>` 未写 `<check>` 的规则会被自动派生成行级 `FilterChecker`，对 `$entity` 求值。即「只有 filter 没有 check」的规则**对按 ID 的读/写路径同样有行级保护**。文档 `auth-and-permissions.md:299` 把 `check` 描述成独立可选概念，具有误导性。

`DefaultDataAuthChecker`（`nop-auth-service/.../auth/DefaultDataAuthChecker.java`）：`:181-182` 无规则 → `isPermitted=true` / `getFilter=null`（**无规则即放行**，文档 :312 已声明）；`:187-188` / `:205-208` 有规则但角色不匹配 → 拒绝/抛异常。**有规则时是 fail-closed 的**，这点做得对。

---

# Part B — REST / GraphQL 入口层

## B1. HTTP 路径认证：默认 fail-closed（设计正确）

| 端点族 | 默认是否需认证 | 证据 |
|---|---|---|
| `/graphql*`、`/r/*`、`/p/*`、`/f/*`、`/jsonrpc`、`/px/*` | ✅ 需认证 | `nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml:262-279` |
| `/f/download/*` | ✅ 需认证（`:258` 是 feature flag opt-in，默认未设置） | 同上 |
| `/r/LoginApi_*`、`/q/health*`、`/q/metrics*` | 公开 | `:254` 等 |

`AuthFilterConfig.isPublicPath()`（`AuthFilterConfig.java:167-195`）匹配顺序为 `publicPaths` → `loginUrl` → `authPaths` → `defaultPublic`。

⚠️ 需注意：class 字段默认 `false`（`AuthFilterConfig.java:20`），但 bean 装配值是 `true`（`auth-service.beans.xml:250`）。**未引入 `nop-auth-service` 的部署反而是安全的**——文档 `auth-and-permissions.md:21` 描述的是装配后的值。

`defaultPublic=true` 的真实爆炸半径：只有**未被 `authPaths` 覆盖**的路径（静态资源 + 应用自行新增的 HTTP 端点）。这是 fail-open 姿态，与 Spring `permitAll` 的默认 deny 心智模型相反，文档 `:23,87` 说明了设计意图但未标为风险。

## B2. 操作权限：默认关闭，但开启后是 deny-by-default

- `enable-action-auth` 默认 false（`ApiConfigs.java:87-88`）→ 默认部署下登录用户对所有 BizModel 方法有全量读写
- **正面发现**：即使方法没写 `@Auth`，`ReflectionBizModelBuilder.java:360-366` 也会自动生成 `ActionAuthMeta(false, emptySet, {Obj}:{opType}|{Obj}:{action})`。所以 `enable-action-auth=true` + checker 存在时是 **deny-by-default**，不是全开
- 但按 `auth-and-permissions.md:238`，权限串必须在 `action-auth.xml` 有 FNPT 声明且被角色授予，否则任何账号都走不通 → 「关掉是全开，打开易死锁」的负激励，强化了默认关闭的现状
- fail-open 点：`GraphQLActionAuthChecker.java:37-39`（checker==null 直接 return）、`:118-120`（`auth==null` 放行）、`GraphQLEngine.java:160-169`（开了 action-auth 但无 checker bean → 仅 WARN 静默 fail-open，代码注释自称 fail-open）

## B3. 字段级暴露：全仓 96.55% 字段对任意登录用户开放

- `_ObjPropMetaImpl.java:187` `published = true` 为默认；`:138` `internal = false`
- codegen 模板 `..._{entityName}.xmeta.xgen:73` 只在 `not-pub` tag 时置 false → **数据库列默认全字段暴露（opt-out）；实体关系默认隐藏（opt-in）**
- 字段级 `<auth>` 机制：全仓生产模型中**使用次数为 0**（唯一 1 处在测试 fixture）
- 实测样例：`nop-file/nop-file-meta/.../NopFileRecord/_NopFileRecord.xmeta:30` `filePath`（服务端磁盘绝对路径）、`:54` `bizObjId` 均 published 且无 auth → `/r/NopFileRecord__findPage` 可枚举全库上传文件的服务端路径

文档 `auth-and-permissions.md:240` 已警告「敏感字段默认全开，不能依赖没人查」，属**已声明的设计约束**，但未量化。

## B4. 文件上传下载：路径穿越已防御，归属校验失效

**正面**：`DaoResourceFileStore.newPath()`（`:266-289`，注释明确写「纵深防御」）校验 `bizObjName` 为合法变量名 + `fileExt` 为 `[A-Za-z0-9]+`，路径由 UUID 构造，客户端无法控制；`fileId` 是 DB 主键查询（`:164-168`），无文件系统路径拼接；`..` 被 `ResourceHelper.checkNormalVirtualPath` 拦（`DefaultVirtualFileSystem.java:164`）。

**缺口**：`NopFileStoreBizModel.loadFileRecord()`（`:135-141`）的归属校验委托给 `DefaultBizAuthChecker.checkAuth`（`nop-biz/.../impl/DefaultBizAuthChecker.java:43-66`），而 `:60` 只在「xmeta 声明了 `<auth>`」时才检查，`:65` 否则 `return true`。结合 B3 的「`<auth>` 零使用」→ **归属校验实际退化为放行**。

## B5. 源码写入端点：默认开启且命名空间可绕过（子代理结论）

- `PageProviderBizModel.java:34` `@InjectValue("@cfg:nop.web.page-provider.edit-enabled|true")`（默认 true）；`:37-39` `getPage` 是 `@Auth(publicAccess=true)`；`:56` `savePageSource` / `:66` `rollbackPageSource` **无 `@Auth`**
- `SystemJsProviderBizModel.java:33` 同样 `edit-enabled|true`；`:54` `saveJsSource` 无 `@Auth`
- `WebPageHelper.checkPageFile`（`:98-113`）**只校验扩展名不校验路径**
- `file:` 命名空间默认无路径白名单（`CoreConfigs.java:151-152` 默认 `""`；`FileNamespaceHandler.java:57-61` 空 pattern 则不限制）
- 平台自己在 `nop-lint/nop-lint-graphql/.../NopLintBizModel.java:190-196` 已写注释说明该风险并主动拒绝命名空间路径，但上述 BizModel 未做同样防御

## B6. `/px/*` 分布式代理：白名单是服务名级，且绕过 GraphQLExecutor（子代理结论）

`GraphQLWebService.runProxy`（`:177-229`）不调用 `GraphQLActionAuthChecker.check`、不做参数校验、不走 MFA。服务名白名单机制存在且默认 deny-all（`ClusterRpcServiceInvoker.java:90-101` + `rpc-cluster-defaults.beans.xml:138` 默认空），但**无方法级授权**——某服务入白名单后其所有 operation 可被任意登录用户调用。

## B7. `/jsonrpc`：与 `/r/*` 等价，无额外缺口

`JsonRpcService.java:103-118` 走 `getOperationDefinition` + `executeRpcAsync` → `GraphQLExecutor.executeOneAsync:72` 的 action auth。批量受 `CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT`（默认 10）限制。

## B8. DevTool：有 `nop.debug` 门控（设计正确）

`biz-defaults.beans.xml:130-134` 用 `<ioc:condition><if-property name="nop.debug"/></ioc:condition>` 门控 `DevToolBizModel`/`DevDocBizModel`，生产默认不注册；有回归测试 `TestDevDocDevToolAuth`。⚠️ 但文档 `auth-and-permissions.md:258` 推荐用它清缓存却未提示「生产误设 `nop.debug=true` 时 `@Auth(roles=admin)` 在 action-auth 关闭下不生效」。

---

# Part C — 认证与租户信任边界

## C1. 三个关键默认值都是安全的（与外部项目不同源）

| 配置 | 代码默认值 | 位置 |
|---|---|---|
| `nop.auth.service-public` | **false** | `auth-service.beans.xml:302` |
| `nop.auth.login.allow-create-default-user` | **false** | `NopAuthConfigs.java:49-50` |
| `nop.auth.skip-check-for-admin` | **false** | `NopAuthConfigs.java:77` |

❌ **文档 bug**：`docs-for-ai/03-modules/nop-auth.md:86` 写 `allow-create-default-user` 默认 `true` —— **与代码相反**，会误导生产部署。

## C2. 密码哈希：比文档描述更强（文档低估）

`auth-core-defaults.beans.xml:38-41` 装配 `CompositePasswordEncoder(first=SHA256, second=BCrypt)`，`CompositePasswordEncoder.java:47-50` 两级串联 → **SHA256(随机 UUID salt + pwd) → bcrypt(cost 10)**。

❌ **文档 bug（低估）**：`auth-and-permissions.md:416` 只写「默认使用 SHA256 加盐哈希」，遗漏外层 bcrypt，会让读者以为只有单轮 SHA256。

可替换（`ioc:default="true"` bean），但仓库内只有 BCrypt，**无 Argon2/scrypt/PBKDF2**。

## C3. Token 校验：全分支抛异常，fail-closed

`JwtHelper.parseToken`（`:116-155`）：`kid` 未知 / 无 kid / 签名错 / 过期 / `typ` 用途错 / `iss` / `aud` / 解析异常 → **全部抛异常，无任何返回 null 或放行路径**。access/refresh/code 三类令牌独立 KID + 独立派生密钥（`JwtAuthTokenProvider.java:87-101`）。

`legacy-token-grace-seconds=0` 确实完全拒绝旧令牌（`JwtHelper.java:157-171` + `JwtAuthTokenProvider.java:91-94` 双重拒绝）。

⚠️ 但 filter 层把异常吞成「无 token」（`AuthHttpServerFilter.java:412-421` catch → `return null`）→ 过期/伪造 token **静默降级为匿名**而非 401，且 `servicePublic` 下会获得 `sys` 主体。

## C4. 租户信任边界：受保护路径已正确闭合

两阶段写入 `IContext.tenantId`：

| 阶段 | 类 | order | 行为 |
|---|---|---|---|
| ① 先 | `ContextHttpServerFilter` | 1000 | **无条件**读 `nop-tenant` 头（`:64,75`） |
| ② 后 | `AuthHttpServerFilter` | 4900 | 用 `userContext.getTenantId()` **无条件覆盖**（`:557`） |

⇒ **受保护路径上租户恒等于会话租户，header 注入无效。** public 路径显式不信任 header（`:266-272`，注释标 `DR-1b`），仅当 `trust-forwarded-tenant=true` 时采纳 `X-Forwarded-Tenant`（默认 false，`AuthCoreConfigs.java:29-30`），有回归测试 `TestSysUserContextContract`。

❌ **文档 bug**：`tenant-model.md:131` 仍写「用户也可通过 HTTP 请求头 `nop-tenant` 传递租户」，**已过期**，与 `auth-and-permissions.md:97` 存在真实张力。

## C5. `sys` 匿名主体：隐式拥有 `user` 角色

`AuthHttpServerFilter.java:278` 确实 `setRoles(emptySet())`，但 `UserContextImpl.java:327-328` 对 `ROLE_USER` 无条件返回 true → **`sys` 隐式有 `user` 角色**，可通过所有 `user` 级权限（`SiteMapProviderImpl.java:276-277`）。

❌ **文档不准确**：`auth-and-permissions.md:97` 称 `sys`「无法满足管理员/权限受限操作」——应为「无法满足管理员操作，但自动满足 `user` 级权限」。

## C6. 子代理提出的会话层缺陷（未独立复核）

以下 3 条来自认证层审计，**本人未逐条复核**，标注为待验证：

| # | 主张 | 位置 |
|---|---|---|
| S1 | `GET /r/LoginApi__logout` 只删 cookie 不吊销服务端 session | `AuthHttpServerFilter.java:297-308`（尤其 `:302`） |
| S2 | `refreshTokenAsync` 是 `@Auth(publicAccess=true)` 且返回已轮转、带满额新过期的 refreshToken → 配合 `expireAfterAccess` 滑动 = **无绝对会话上限** | `LoginApiBizModel.java:150-157,608-609`；`LoginServiceImpl.java:954-959`；`AbstractUserContextCache.java:68-69` |
| S3 | refresh token 无轮换/重放检测，合法刷新不使已窃取 token 失效 | `JwtAuthTokenProvider.java:137-141` |

若成立，S1+S2 属「会话无法被强制终止」，是 nop 认证层最值得关注的真实缺陷。

---

# Part D — ORM 层租户硬隔离

## D1. 结论：机制是强制的，但默认不生效；且有一类语句永久漏网

需要区分两件事：

1. **「ORM 底层自己处理租户」在开启后成立**——`GenSqlHelper` 与 `EqlTransformVisitor` 共 6 个门控点全部按 `entityModel.isUseTenant()` 注入 `tenantCol = ?`，缓存键含租户维度，上下文丢失一律 fail-closed。这部分实现是完整的（见 D4/D5/D6）。
2. **但触发条件默认关闭**（每实体 `entity.xdef:50` 默认 false + 全局 `DaoConfigs.java:59` 默认 false），源模型 199 个实体 `useTenant="true"` 的有 0 个 → 默认部署下 ORM 不做任何租户过滤（见 D3）。
3. **即使开启，`UPDATE`/`DELETE` 仍漏网**（见 D2）——这是「语句类型漏网」，与开关状态无关。

以下两条独立理由，任一即可推翻文档「硬隔离」的宣称。

### D2. 🔴 新发现（严重）：EQL `UPDATE` / `DELETE` 完全不注入租户条件

本人已独立复核：

```
collectDefaultEntityFilter   定义于 EqlTransformVisitor.java:396
  └ 唯一调用链 addEntityFilter:453-465 ← addTableFilter:392 ← 仅 visitSqlQuerySelect:261
  └ 另有 join 路径 :609 / :901
visitSqlUpdate  :1541  → :1556 visitChild(node.getWhere())   ← 不调 addTableFilter
visitSqlDelete  :1600  → :1614 visitChild(node.getWhere())   ← 同上
```

**EQL `update X set ...` / `delete from X where ...` 编译出的 SQL 永远不含 `tenantCol = ?`。**

框架级受影响 API：`OrmEntityDao.deleteByQuery`（`:565-576`）/ `updateByQuery`（`:674-685`）经 `DaoQueryHelper.queryToDeleteSql:257` / `queryToUpdateSql:265` 生成 EQL。

**重要缓解事实**：平台自身 GraphQL CRUD 入口 `CrudBizModel.deleteByQuery:1576` / `updateByQuery:1513` **不直接调 dao**，而是先 `doFindList0`（带租户过滤）查出实体列表再逐实体 delete/update → **标准 CRUD API 安全**。风险集中在业务代码直接使用 `IEntityDao.deleteByQuery/updateByQuery` 与 `OrmTemplateImpl.tryDelete`（`:338-352`，公开 API 当前无调用方）。

**文档 `tenant-model.md` 完全未声明此缺口。**

### D3. 🔴 文档回归：决定全局隔离的开关在 `docs-for-ai` 中丢失

**先明确机制本身是完整且强制执行的**——「ORM 底层自己处理租户」在**开启后**成立：

| 门控点 | 位置 |
|---|---|
| 批量加载 | `GenSqlHelper.java:93` |
| findLatest | `GenSqlHelper.java:238` |
| 集合加载 | `GenSqlHelper.java:463` |
| 实体 ID 条件 | `GenSqlHelper.java:653` |
| example 过滤 | `GenSqlHelper.java:812` |
| EQL 缺省过滤 | `EqlTransformVisitor.java:404` |

全部为 `if (entityModel.isUseTenant())`。**问题在触发条件默认关闭，而不是机制缺失。**

触发条件的默认值：

| 项 | 默认值 | 位置 |
|---|---|---|
| 每实体 `useTenant` | `false` | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/entity.xdef:50` |
| 全局 `nop.orm.enable-tenant-by-default` | `false` | `nop-persistence/nop-dao/src/main/java/io/nop/dao/DaoConfigs.java:59` |

全局开关的消费方在 XPL 元编程层：`nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/orm-gen.xlib:18` `<thisLib:TenantSupport feature:on="nop.orm.enable-tenant-by-default"/>`，模板体 `:391-424`：

```
if (xpl('thisLib:NotAutoTenant', entityNode)) continue;   // tagSet 含 no-tenant 则跳过
if (entityNode.getAttr('useTenant') == null) entityNode.setAttr('useTenant', true);
```

挂载于每个 `_app.orm.xml` 的 `<x:post-extends x:override="replace">`（如 `nop-auth/nop-auth-dao/src/main/resources/_vfs/nop/auth/orm/_app.orm.xml:9-10`）。

**实测覆盖率**：源模型 `*/model/*.orm.xml` 共 **199 个实体**（nop-ai 22 / nop-auth 27 / nop-metadata 38 / nop-sys 19 / nop-datav 17 / nop-dyn 15 / nop-wf 13 / nop-code 13 / nop-report 8 / nop-batch·job·task·rule·retry 各 4 / nop-credential 4 / nop-tcc 2 / nop-file 1），其中 `useTenant="true"` 的 **0 个**。

**⇒ 默认部署下所有实体都不是租户表，`ContextProvider.currentTenantId()` 返回什么都无关紧要。**

平台自带示例的取值不一致（说明这确实是被忽略的配置项）：

| 应用 | `enable-tenant-by-default` |
|---|---|
| `nop-demo/nop-quarkus-demo/src/main/resources/application.yaml:31` | **true** |
| `nop-demo/nop-spring-demo/src/main/resources/application.yaml:27` | **true** |
| `nop-dyn/nop-dyn-app/src/main/resources/application.yaml:23` | **true** |
| `nop-demo/nop-spring-gateway/src/main/resources/application.yaml:24` | false |
| `nop-demo/nop-spring-gateway-demo-service/src/main/resources/application.yaml:14` | false |

**文档侧**：`rg "enable-tenant-by-default|全局开关|TenantSupport|no-tenant" docs-for-ai/02-core-guides/tenant-model.md` → **0 命中**。而 `ai-dev/logs/2026/06-22.md:1-12` 明确记录当时已按使用方反馈补入「全局开关：缺省启用租户」小节（含 `enable-tenant-by-default` 与 `auto-add-tenant-col`）。**该补充在后续 `docs-for-ai-old` → `docs-for-ai` 迁移中丢失**——这是一次文档回归，而非从未写过。

附带风险——**装饰性租户列在源模型中显式声明**（不在生成物里）：`nop-auth/model/nop-auth.orm.xml:108` 显式声明 `tenantId` 列（`code="TENANT_ID" mandatory="true" propId="31"`），共 8 个实体如此（`:108/655/1032/1106/1216/1392/1447/1547`）。同时 `NopAuthUser` 实体标签为 `tagSet="mapper,no-tenant"`（`:34`），即**列存在但被显式排除在全局开关之外**——有字段、能在 Java 里 `getTenantId()`，但 ORM 既不过滤也不自动填充，制造「已做租户隔离」的错觉，无任何 lint 或启动期告警。

### D4. `GenSqlHelper` 层无缺口（正面）

逐路径核实 14 个 SQL 生成入口（`genLoadSql`/`genDeleteSql`/`genLockSql`/`genUpdateSql`/`genFindLatestSql`/`genInsertSql`/批量加载/集合加载/`genCountByExample`/`genDeleteByExample`/`genFindByExample`/`genUpdateByExample`）**全部**经 `genEntityFilter`/`appendBatchLoadEq`/`appendExampleFilter`/`genCollectionFilterEx` 注入租户条件。`appendExampleFilter`（`:812-820`）仅当 example 未自带租户条件时才追加，避免两个租户值 AND 成恒假——值得肯定。

count / group by / exists / join / propJoin / 批量 update / findFirstByExample / 排序分页 count / 游标分页 全部有租户条件。join 路径有回归测试 `TestEqlTenantPropJoin` 钉定。

❌ **文档 bug**：`tenant-model.md:63-71` 列出的 `genDeleteForUpdateSql()` 与 `addDefaultFilter()` **两个方法名在代码中不存在**（全仓 0 命中）；同时遗漏了实际存在的 `genCollectionFilterEx()`。

### D5. 上下文丢失一律 fail-closed（关键安全底线）

| 检查点 | 行为 |
|---|---|
| EQL 参数构建 | `TenantParamBuilder.java:21-24` 抛 `ERR_CORE_NO_TENANT_ID` |
| 实体 load/save/update/delete | `EntityPersisterImpl.java:440-444,293,307,327` 抛 `ERR_ORM_MISSING_TENANT_ID` |
| 主键生成 | `OrmEntityIdGenerator.java:107-110` 抛错 |
| Session 缓存读/写 | `TenantOrmSessionEntityCache.java:106-110` / `:74-81` 抛错 |
| Example 查询 / 实体 ID 条件 | `GenSqlHelper.java:818` / `:651-661` → `tenantCol = null` → 匹配 0 行 |

⇒ **线程池丢上下文的最坏结果是报错或查不到数据，不会越权。** 唯一例外是配置了 `nop.default-tenant-id`（`BaseContextProvider.java:47-50`）时会退化到该默认租户。

`ContextProvider.runWithoutTenantId()` 全仓仅 4 处主干调用（`DynOrmModelProvider:64,70`、`DynCodeGen:83-84`、`ResourceComponentManager:114`），全部是「加载共享资源/模型」场景，**无用户可触达的业务调用点**。

⚠️ 但 `tenant-model.md:95` 把 `runWithoutTenantId()` 列为「租户过滤的跳过方式」，未说明它只用于模型/资源加载，会误导业务代码用它看全量数据。

### D6. 缓存键均含租户维度（正面）

`EntityPersisterImpl.getCacheKey()`（`:568-583`）用 `tenantId:id`；`loadAsync:225` 先 `processTenantId` 再算 key；`TenantOrmSessionEntityCache` 按 tenantId 分区；`CollectionPersisterImpl.getCacheKey()`（`:228-249`）用 refEntity 的租户列，owner 非租户 + refEntity 租户的关系已由 `OrmModelInitializer.isDynamicRelation:486-493` 标记为 dynamic（禁用全局缓存）。**无串租户风险。**

## D7. 子代理提出的其他租户缺口（未独立复核）

| # | 主张 | 位置 |
|---|---|---|
| T1 | WebSocket 路径租户既不来自 header 也不来自认证用户（`bindUserFromContext` 不传播 `tenantId`），配了 `nop.default-tenant-id` 时全部 WS 订阅退化到默认租户 | `JsonRpcWebSocketHandler.java:315-320` |
| T2 | `EntityPersisterImpl.java:447` 在 `currentTenant==null` 时跳过跨租户校验（与同文件 `:443` 及 `OrmEntityIdGenerator:108` 策略不一致） | ⚠️ 已在 `ai-dev/audits/security-audit/2026-09-18-core-audit-CORE-05.md:67` 记为 watch-only residual |
| T3 | `tenant-model.md:146` 把 `TenantCachedQueryPlan` 描述为「EQL 查询计划按租户缓存」——实为死代码（`setTenantModel` 无调用方）且服务 dyn 动态模型场景，与行级租户过滤无关 | |

---

# 综合结论

## 与 MagesticAI 模式的对比裁定

| MagesticAI 缺陷 | nop 对应面 | 裁定 |
|---|---|---|
| REST 路由零归属校验 | `/r/*`、`/graphql`、`/p/*`、`/jsonrpc` 三层权限齐备，`CrudBizModel` 28/30 入口有覆盖 | **不成立** |
| shell 参数零校验 | `/f/*` 有明确标注的纵深防御；`fileId` 是 DB 主键 | **不成立** |
| — | `SearchEngineBizModel.checkAllowAccess` 空实现 + `indexDir` 接受任意服务器路径 | **成立，但既有审计已记录**（`ai-dev/audits/check/nop-search.md:141`，P1，2018-09 审计） |
| — | `copyForNew` 源记录零数据权限校验 | **成立，新发现** |
| — | `enable-data-auth` 默认 false 且文档零提及 | **成立，新发现（文档 bug）** |
| — | EQL `UPDATE`/`DELETE` 不注入租户条件 | **成立，新发现（严重）** |
| — | 租户全局开关在 docs-for-ai 丢失（2026-06-22 补过，迁移中丢失） | **成立，文档回归** |

**核心差异**：nop 的问题不在「没有校验」，而在**关键开关默认关闭/宽松（action auth、data auth、edit-enabled、租户全局开关、file 命名空间白名单）且文档未量化这些默认值的后果**，导致「配齐所有安全开关」成本极高，实际部署普遍处于「只有登录检查」的状态。

## 缺陷清单（按严重度）

| # | 严重度 | 类型 | 缺陷 | 是否新发现 |
|---|---|---|---|---|
| 1 | 🔴 高 | 真实缺陷 | EQL `UPDATE`/`DELETE` 不注入租户条件，击穿 `IEntityDao.deleteByQuery/updateByQuery` | ✅ 新 |
| 2 | 🔴 高 | 文档回归 | `nop.orm.enable-tenant-by-default` 在 `tenant-model.md` 丢失（2026-06-22 补过）；默认部署租户覆盖率为 0 | ✅ 回归 |
| 3 | 🔴 高 | 真实缺陷 | `CrudBizModel.doCopyForNew:1815` 源记录无数据权限校验，整行克隆 | ✅ 新 |
| 4 | 🟠 中 | 文档 bug | `enable-data-auth` 默认 false 未入文档，文档表述暗示默认开启 | ✅ 新 |
| 5 | 🟠 中 | 文档 bug | `03-modules/nop-auth.md:86` `allow-create-default-user` 默认值写反（true→实为 false） | ✅ 新 |
| 6 | 🟠 中 | 真实缺陷（既有） | `SearchEngineBizModel.checkAllowAccess` 空实现 + 任意路径索引 | ❌ `audits/check/nop-search.md:141` 已记录 P1 |
| 7 | 🟠 中 | 文档 bug | `auth-and-permissions.md:416` 低估密码哈希（漏 bcrypt 层） | ✅ 新 |
| 8 | 🟠 中 | 文档 bug | `tenant-model.md:131` 称可用 `nop-tenant` 头传租户（已被 `AuthHttpServerFilter` 覆盖） | ✅ 新 |
| 9 | 🟡 低 | 真实缺陷 | `deleteEntity:2152` 缺 `checkDataAuth`，注释与代码不符 | ✅ 新 |
| 10 | 🟡 低 | 文档不准确 | `sys` 隐式拥有 `user` 角色；`auth-and-permissions.md:97` 表述过头 | ✅ 新 |
| 11 | 🟡 低 | 文档 bug | `tenant-model.md:63-71` 两个方法名不存在、遗漏 `genCollectionFilterEx` | ✅ 新 |
| 12 | 🟡 低 | 文档误导 | `tenant-model.md:95` 把 `runWithoutTenantId()` 列为「跳过方式」，未限定模型加载用途 | ✅ 新 |
| 13 | 待验证 | 真实缺陷? | 会话无法强制终止（S1 logout 只清 cookie / S2 无绝对会话上限 / S3 refresh 不可撤销） | ⚠️ 子代理结论未复核 |
| 14 | 待验证 | 真实缺陷? | WebSocket 路径租户来源缺失（T1） | ⚠️ 子代理结论未复核 |

## 被否决的判断

- **「nop 存在 MagesticAI 式零归属校验」** —— 否。框架基类逐入口校验 + codegen 纯透传 + HTTP 层 fail-closed，量级差异明显。
- **「`AuthFilterConfig.defaultPublic` 类默认是 true（不装 auth-service 更危险）」** —— 否。class 字段默认 `false`（`AuthFilterConfig.java:20`），装配值 `true` 只在引入 `nop-auth-service` 时生效。
- **「`useTenant=false` 的实体在多租户部署下有泄漏」** —— 当前为否，因为默认部署下**没有任何实体是租户表**；风险在开启全局开关后立即显现。
- **「线程池丢上下文会导致跨租户」** —— 否。除 `nop.default-tenant-id` 配置外全部 fail-closed。

## 后续工作

均落在 AGENTS.md 标记的 Protected Area（`nop-core`/`nop-biz`/`nop-orm` 框架核心 = plan-first），按项目规约需先出计划并经独立审计：

1. **文档类（低风险，可先行）**：修 #2 #4 #5 #7 #8 #11 #12 —— 均为 `docs-for-ai` 与代码不符，补齐后跑 `node ai-dev/tools/check-doc-links.mjs --strict`
2. **缺陷类（需 plan-first）**：#1 需在 `EqlTransformVisitor.visitSqlUpdate/visitSqlDelete` 补 `addTableFilter` 等价逻辑 + 回归测试；#3 需把 `doCopyForNew` 源记录改为走 `requireEntity`；#9 补 `checkDataAuth` 并修正注释
3. **既有项跟进**：`audits/check/nop-search.md:141` 的 P1 建议是否已修复需确认（本轮只复核了缺陷仍然存在）
4. **待验证项**：#13 #14 需独立复核后决定是否升级为缺陷

## Open Questions

- [ ] `enable-tenant-by-default` 的文档回归是 `docs-for-ai-old` → `docs-for-ai` 迁移丢失，还是被后续某次改写覆盖？需查 git log 定位丢失点，避免再次回归
- [ ] 多租户部署是否有统一的启动期校验（如「已配置租户但无实体启用租户」告警）？当前 8 个装饰性 `tenantId` 列无任何提示
- [ ] `nop.default-tenant-id` 是否应被文档标注为「配置后即成为越权放大器」？
- [ ] #13（会话无法强制终止）若成立，是否需要引入 refresh token 轮换 + 绝对会话上限？
- [ ] `enable-action-auth` / `enable-data-auth` 是否应改为「生产 profile 下默认 true，或未开启时拒绝启动」？这会改变默认行为，属 ask-first
- [ ] EQL `UPDATE`/`DELETE` 补租户条件是否属 breaking change？需评估现有 12 处用户可触达 EQL 写（子代理称全部落在 `no-tenant` 白名单内，属巧合性安全）

## References

### 本报告引用的既有文档

- `docs-for-ai/02-core-guides/auth-and-permissions.md`
- `docs-for-ai/02-core-guides/tenant-model.md`
- `docs-for-ai/03-modules/nop-auth.md`
- `ai-dev/analysis/00-analysis-writing-guide.md`
- `ai-dev/analysis/2026-10/2026-10-03-agent-platform-docker-usage-and-auth-management-survey.md`（本轮外部平台调研）

### 既有审计/记录（去重依据）

- `ai-dev/audits/check/nop-search.md:141`（P1，SearchEngineBizModel）
- `ai-dev/audits/check/nop-orm-eql.md`、`ai-dev/audits/check2/nop-orm-eql.md`（visitSqlUpdate/Delete 的其他缺陷，不含租户）
- `ai-dev/audits/security-audit/2026-09-18-core-audit-CORE-05.md:67`（`EntityPersisterImpl:447` watch-only residual）
- `ai-dev/audits/2026-08/2026-08-17-0518-deep-audit-nop-credential/D4-ownership-authz-bypass.md:36`（copyForNew 写侧收口）
- `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md:187`（`enable-data-auth` 默认 false 已知项）
- `ai-dev/logs/2026/06-22.md:1-12`（tenant-model.md 全局开关补记，判定为本次回归的源头）
- `ai-dev/lessons/14-conditional-activation-bypass.md`