# nop-credential 二期设计（OAuth 流程引擎 + 外部 KMS/HSM + 凭证归属统一 + RBAC 细粒度授权）

**日期**：2026-08-14
**状态**：active
**范围**：`nop-credential`（api/service/web/model）、消费方（nop-ai / nop-integration / nop-metadata）；`nop-auth-sso` 仅作协议级先例参照（见 §三）

---

> **粒度裁决（2026-08-14）**：四主题合入本单一文件 `02-phase2-design.md`，不拆分。理由：四主题共享同一批一期契约锚点（`cv1:` 密文格式 / 明文边界 / 软删除 fail-closed / 引用计数）与同一组消费侧校验点，交叉引用密集，拆分后每个主题小节都要重复声明兼容性基线；预估总规模在单文档可控范围（约 500 行）；roadmap（W9-design/W9-impl/W10-impl/W11-impl）与后续 impl plan 链均锚定本路径，拆分将造成引用断裂。按 roadmap 约束，每主题小节独立 review 门槛。

> **范围裁决（2026-08-14 用户裁定）**：凭证模块**不做租户隔离**（租户为 Nop 平台全局可开启能力）；归属统一只覆盖"系统级（共享/管理员管理）+ 用户级（个人私有）"两种场景，服务"多个外部系统共用同一密钥管理服务"（落点见 §五）。

## 一、设计结论索引

- **§三 OAuth**：出站 OAuth 2.0 客户端引擎；不复用入站 `OAuthLoginServiceImpl`（仅协议级参照）；`authType=oauth2` 类型声明 OAuth 元数据、token 集为引擎保留字段名；先建实例再发起授权、state 一次性 bearer capability 绑定 credentialId、单一公开回调端点；自动续期 = 取用时惰性刷新（跨副本互斥），无后台任务。
- **§四 KMS/HSM**：`ICredentialKeyProvider` SPI 零变更，KMS = 新实现类（材料交付模式，启动期交付、运行期零托管端调用）；fail-closed 全在启动期、无本地降级；密钥来源不混合（迁移残余列表唯一例外）；独立可选 Maven 模块；keyId→材料不变式 + 新 keyId 轮换。
- **§五 归属统一**：`scope=system|user`（缺省 system，存量零迁移）+ `ownerId`（user 级必填、归属不可变）；`usageScope` 废弃；身份传播 = 上下文捕获（`IUserContext.get()`，SPI 签名零变更）；消费侧 per-method 归属矩阵（明文出口 owner 唯一）；BizModel 两层防御（前置过滤 + 纵深校验）；不做租户隔离。
- **§六 RBAC**：新实体 `NopCredentialAuth`（角色↔凭证 use 授权，物理删除 + 幂等 grant/revoke）；默认开放、可选收紧（一期行为零回归）；消费侧判定矩阵与归属串联；nop-credential 不依赖任何 nop-auth 模块（角色经 `IUserContext` 快照传播）；admin 不自动豁免取用。

## 二、一期契约兼容性总确认

四主题对一期四项契约锚点的影响矩阵（均"不变"或"显式增量"，无隐性变更）：

| 契约锚点 | §三 OAuth | §四 KMS | §五 归属 | §六 RBAC |
|---|---|---|---|---|
| `cv1:` 密文格式 | 不变（token 集为新字段） | 不变（只换材料来源） | 不变（明文元数据列） | 不变（元数据表） |
| 明文边界（`published=false` + BizModel 置空 + 唯一解密点） | 不变（回调只进不出） | 不变（密钥层零感知） | 不变，精确化为"明文不返回给非 owner 调用方" | 不变（校验在解密前） |
| 软删除 fail-closed（delFlag） | 不变 + oauth2 类型新增 status=disabled 拒绝（显式增量） | 不变 | 不变（delFlag 先序） | 不变（先序前置条件） |
| 引用计数（registerUsage/consumerRef） | 不变 | 不变 | 不变（与归属正交） | 不变（与授权正交） |
| 一期消费链（W7-successor 等）零回归 | 零变更 | 零变更（缺省 local） | 零迁移（scope 缺省 system） | 零回归（默认开放） |

## 三、OAuth 流程引擎（出站 OAuth 客户端）

### 3.1 设计结论

1. 凭证库内置**出站** OAuth 2.0 客户端流程引擎：授权码（authorization code）换取、token 刷新闭环、取用时**惰性**自动续期。`nop-auth-sso` 的 `OAuthLoginServiceImpl` 是**入站** SSO 登录服务（外部 IdP 登录本平台、全局单例 `SsoConfig`），与凭证库需求方向相反，**不做组件级复用**——仅作协议级先例参照（token endpoint 表单交互形态、`AccessTokenResponse` 字段解析、refresh grant 语义、`IHttpClient` 用法）。
2. 配置模型按凭证类型声明：`authType=oauth2` 的凭证类型在定义文件（`*.credential-type.xml`）中扩展 OAuth 应用元数据（授权端点、令牌端点、scopes、刷新窗口）；`clientId`/`clientSecret` 作为该类型凭证实例的敏感字段加密存储（`cv1:` 格式不变）。
3. Token 集为**引擎保留字段名**（`accessToken`/`refreshToken`/`expiresAt`/`tokenType`/`scope`，类型文件不得占用），作为流管字段（flow-managed）加密存储于凭证 data，与人工输入字段（clientId/clientSecret 等）分组并存；**自动续期 = 取用时惰性刷新**（临期即刷新并回写密文），不引入后台刷新任务。
4. 授权码闭环为**先建实例（人工录入 clientId/clientSecret）再发起授权**：发起 action 入参含目标 credentialId，一次性 `state` 令牌绑定该实例；新增**单一公开回调端点**（public REST BizModel，先例：`LoginApiBizModel` 的 `@Auth(publicAccess=true)`），`state` 为不可预测 + 一次性 + 短 TTL 的 bearer capability（CSRF/注入防护边界）。

### 3.2 背景与动机

一期边界（vision §三.3）：凭证库只加密存储 OAuth Token（由消费方自行获取后存入），不内置授权码换取/刷新闭环。二期需求：本平台作为 OAuth 客户端连接第三方系统（开放平台 API、SaaS 集成），需要完整闭环——发起授权 → 回调换 token → 刷新续期；且 access token 是短期凭证，无自动续期则凭证库中 OAuth 凭证很快失效，失去"凭证库统一托管"的价值。

`OAuthLoginServiceImpl` 能力盘点与差异（live 锚点：`nop-auth/nop-auth-sso/.../login/OAuthLoginServiceImpl.java`、`SsoConfig.java`、`AccessTokenResponse.java`）：

| 维度 | `OAuthLoginServiceImpl`（既有） | 凭证库 OAuth 引擎（本设计） |
|---|---|---|
| 方向 | **入站**：外部 IdP 身份 → 本平台登录会话 | **出站**：本平台作为 client 访问第三方资源 |
| 配置 | 全局单例 `SsoConfig`（authServerUrl/realm/clientId/clientSecret 等一组） | 按凭证类型（OAuth 应用）多套，clientId/clientSecret 逐实例加密 |
| 产物 | `IUserContext`（用户会话，进 session 缓存） | token 集（加密回写凭证 data） |
| 既有可参照能力 | 授权码/密码/client_credentials 换 token（`newLoginRequest`/`newServiceLogin`）、refresh 刷新（`refreshToken`）、登出撤销、JWT+JWKS 解析 | 参照其表单构造与 `AccessTokenResponse` 解析形态；JWT 解析不需要（token 对第三方透传使用，不在本地解身份） |

### 3.3 核心设计

**凭证类型声明扩展**：

- 一期 `credential-type.xdef` 已有 `authType` 声明位（live 为自由字符串，实例取值 `none`/`apiKey`）；二期将取值域收敛为 `none | apiKey | basic | oauth2`，并允许 `oauth2` 取值下声明 OAuth 元数据：授权端点 URL、令牌端点 URL、scopes、刷新窗口（距 expiresAt 剩余多少秒内触发惰性刷新，缺省值由 impl 统一给出并写入文档）。实例文件先例：`nop-credential/nop-credential-service/src/main/resources/_vfs/nop/credential/types/generic-secret.credential-type.xml`。
- **token 字段名为引擎保留字**：`accessToken` / `refreshToken` / `expiresAt` / `tokenType` / `scope` 五个字段名归 OAuth 引擎保留命名空间，类型文件的 fields **不得**用这些名字声明人工字段（registry 层校验拒绝）；引擎凭保留名定位与读写 token 集，类型文件无需（也不允许）重复声明 token 字段结构。
- xdef 位于 `nop-kernel/nop-xdefs`（Protected Area），扩展属 W9-impl 的 plan-first 事项（见 §八）。

**授权码闭环（语义契约）**：

1. **先建实例、再发起授权**：`authType=oauth2` 的凭证实例必须先经 `saveCredential` 人工录入 clientId/clientSecret 等人工字段（归属/命名在创建时即确定）；**发起 action 的入参必须含目标 credentialId**（校验：实例存在、未删除、未禁用、类型为 oauth2、已录入 clientSecret——发起时 biz action 有登录态，此处完成发起人与凭证归属的校验）。不提供"边授权边新建"路径。
2. **发起**：引擎生成不可预测的一次性 `state`，服务端持久化 state 绑定（目标 credentialId + 发起人（审计用）+ 过期时间，TTL 缺省 10 分钟、可配置）→ 返回授权 URL（授权端点 + client_id + redirect_uri + scope + state），由前端跳转第三方。`redirect_uri` 为本平台对外基础地址配置（需部署方配置外部可达地址）派生的单一回调端点 URL，token 交换时回传同值。
3. **回调**（单一公开端点，`@Auth(publicAccess=true)`，浏览器顶层 30x 重定向流）：接收 `code` + `state` → 校验 state（存在、未过期、未消费；校验通过**立即作废**——一次性消费）→ 以 code + **该实例 data 中解密出的 clientSecret** 在令牌端点换取 token 集 → token 集加密**回写该实例**（只写引擎保留字段）→ 30x 重定向到前端结果页（不返回 token 明文）。state 未命中/过期/重放一律 fail-closed（拒绝并作废）。
   > **W9-impl 裁定标注（2026-08-16 回写）**：**回调响应载体落地为 `WebContentBean` HTML 跳转页（HTTP 200 + meta-refresh/JS `location` 跳转到配置的前端结果页），非服务端 30x**。理由：平台 biz 层无 30x 原语（biz action 只能返回响应体；`IHttpServerContext.sendRedirect` 仅 HTTP filter 层可达，注册 filter 并处理与 auth filter 顺序的复杂度与收益不成比例）。语义等价性成立：浏览器最终落在前端结果页、token 明文不出现在任何响应体——与"30x 重定向"条文的差异为显式偏离记录，不改设计结论。
   - **回调的数据通道（与 §五 归属模型的联合裁定）**：state 绑定记录发起人 userId；回调路径无登录态，其 clientSecret 读取与 token 回写**以 state 记录的发起人身份**经 provider 的引擎内部通道执行（语义等价于 owner 本人在场发起写入）。**"唯一解密点"不变式不破**：OAuth 引擎不得直接持有密文编解码器解用户数据，一切解密经 `CredentialProviderImpl`（引擎内部通道是 provider 的非公开内部方法，不是 SPI 面扩展）。user 级凭证的授权闭环因此成立（发起=owner 登录态校验；回调=state bearer capability 携带 owner 身份）。
   - **发起授权的权限矩阵**：发起动作对凭证 data 产生写效果，属"修改"类操作——system 级凭证限管理员发起，user 级凭证限 owner 发起（与 §5.3 CRUD 矩阵一致；W9-impl 时点归属字段未落，先实现登录态 + 实例存在/未删/未禁用 + 类型校验，归属校验由 W11-impl 回补，见 §八）。
4. **state 的安全模型是 bearer capability**：回调端点无登录态，state 本身（不可预测 + 一次性 + 短 TTL）即防 CSRF/注入的边界；发起人信息记录于 state 绑定仅用于审计，回调时不做用户会话校验（公开回调无法可靠携带会话）。

**刷新闭环与自动续期（惰性刷新）**：

- 取用语义（`ICredentialProvider.getCredential` 不改签名）：凭证类型 `authType=oauth2` 且 accessToken 临期（now 距 expiresAt 小于刷新窗口；expiresAt 为服务器时钟绝对时刻 = 换取/刷新时的 now + 响应 expires_in）→ 先以 refreshToken 刷新（grant_type=refresh_token，携带该实例的 clientId/clientSecret）→ 成功：新 token 集加密回写 data 后再返回明文；失败（如 invalid_grant）：**fail-closed** 抛错，不静默使用旧 token、不静默返回空值。
- refreshToken 为空/缺失且 accessToken 已过期：fail-closed 抛错（提示重新走授权码流程）。
- 并发一致性约束（**跨副本部署必须成立**）：同一凭证实例的刷新互斥收敛为一次（部分提供方 refresh token 一次性轮换，并发刷新互踢）；互斥载体可复用平台分布式锁能力或 DB 级行锁（载体 W9-impl 裁定），语义要求多副本下同一凭证同时至多一个刷新在途。非刷新取用可并发。
- **流管字段与人工字段分组写语义**：对 `authType=oauth2` 类型，`saveCredential` 维持人工字段集合整包替换（未传即删）的既有语义，但**保留引擎保留字段不动**（service 端从当前 data 解出 token 集合并合并回写）；saveCredential 输入中出现保留字段名一律拒绝（防人工伪造 token 破坏刷新状态机）。刷新/回调回写只写保留字段集合。人工保存与刷新的写冲突由上述"同一凭证写路径串行化"约束覆盖。
- `typeList` 动态表单对 oauth2 类型不渲染引擎保留字段（表单只出人工字段）。

**复用边界与依赖方向**：

- 复用（组件级）：`IHttpClient`（`nop-http-api`）。
- 参照（同形实现，非类引用——`AccessTokenResponse`/`SsoConstants` 位于 nop-auth-sso 模块，凭证库**不引入该依赖**，按相同字段命名与协议语义自行实现）：token 响应字段解析形态（access_token/expires_in/refresh_token/token_type/scope/error）、grant-type 常量语义。
- 不复用（组件级）：`OAuthLoginServiceImpl`/`AbstractLoginService`/`SsoConfig`/`JWKPublicKeyLocator`。
- 依赖方向：`nop-credential-service` **不依赖** `nop-auth-sso`/任何 nop-auth 模块（凭证库保持独立可复用模块定位，见 vision §五.1 的依赖约束精神：api 层零业务依赖、消费方按 api+service 取用）。
- vision §三.3 规划期表述"OAuth 完整流程可复用 nop-auth-sso 的 OAuth 客户端能力"由本节**细化裁定取代**：经 §3.2 能力盘点，复用层级为协议级参照而非组件复用（理由见 §3.4 首行）。

### 3.4 拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| 组件级复用/继承 `OAuthLoginServiceImpl` | 入站 vs 出站方向相反；单例 `SsoConfig` vs 按类型多套配置；产物 `IUserContext`（登录会话）≠ token 集（凭证数据）。强行复用需把稳定登录服务改造成多配置出站引擎，破坏 nop-auth-sso 边界，收益仅是少量表单构造代码 |
| 后台定时预刷新任务 | 引入调度器依赖；刷新时机与真实消费脱节（无人消费的 token 白刷、临用前仍可能过期）；惰性刷新把续期对齐到真实取用点，无竞态窗口外的失效 |
| token 明文进程缓存层 | 违背"明文生命周期最小化"一期原则；DB 行级解密成本可接受；缓存使"何时读到新 token"的推理复杂化（多副本不一致） |
| 每凭证类型独立公开回调 URL | publicAccess 端点数随类型膨胀，公开面扩大；单回调 + state 路由攻击面最小、审计单点 |
| 允许 `saveCredential` 人工录入 token 字段 | 绕过授权码闭环的状态机（伪造 expiresAt/refreshToken 使惰性刷新逻辑失真）；token 只能由闭环写入与刷新回写；保留字段名由引擎统一保留命名空间并拒绝人工输入 |
| PKCE（机密客户端纵深防御） | 二期闭环以机密客户端（服务端持有 secret 且已加密托管）为覆盖目标，PKCE 为 OAuth 2.1 方向的增量纵深防御，非闭环成立前提；deferred 到 §七 待安全审计评估后并入 |
| 边授权边新建实例的回调路径 | clientSecret 在新建时无处可取（未先录入），且实例归属/命名未定；"先建实例再发起授权"使闭环数据流无歧义（state 绑定 credentialId） |

### 3.5 与一期契约兼容性

- **`cv1:` 密文格式不变**：token 集只是 data JSON 中的新字段，加密路径复用 `CredentialCipher`（active key 加密、keyId 路由解密、reencryptAll 覆盖）。
- **明文边界不变**：公开回调端点只进（code/state）不出（30x 重定向，不返回 token 明文）；token 明文仅经服务端 SPI 取用；`mask` 对 token 字段输出 `****`；管理面查询 data 恒置空。
- **软删除 fail-closed 不变**：已删除（delFlag）凭证不参与发起授权、回调写入、刷新与取用（fail-closed）。
- **显式增量声明**：一期取用路径仅对 `delFlag` fail-closed、不校验 `status`；二期对 `authType=oauth2` 类型**新增** `status=disabled` fail-closed（发起/回调/刷新/取用全路径拒绝禁用凭证）；非 OAuth 类型取用维持一期语义（仅 delFlag），status 语义是否全局收紧为独立裁定（§七 deferred，避免隐性变更一期消费方行为）。
- **引用计数不变**：OAuth 凭证被消费方绑定仍经 registerUsage/unregisterUsage（consumerRef 约定不变，如 `ai:NopAiModel:<id>`）。
- **增量声明**：新增管理面 action（发起授权）与单一公开回调端点；既有 API 方法签名零变更；saveCredential 对 oauth2 类型新增"保留字段拒绝 + 分组写"语义（人工字段整包替换语义不变）。

## 四、外部 KMS/HSM 集成（主密钥托管来源扩展）

### 4.1 设计结论

1. **`ICredentialKeyProvider` SPI 面零变更**（`getActiveKeyId`/`getKey`/`getKeyIds` 不动）：外部 KMS/HSM 通过**新增实现类**接入，`DefaultCredentialKeyProvider`（环境变量/配置文件 passphrase 来源）保留为缺省实现。KMS 是密钥材料来源的实现细节，不是接口责任。
2. **集成模式 = 密钥材料交付模式（material delivery）**：托管端负责保管密钥材料并在**启动期**将其交付给本地方，本地方按一期不变的方式用材料构造单钥加密器（`AESTextCipher`）完成全部加解密；运行期**零托管端调用**。适用的托管形态：材料可交付类——secret manager（如 Vault KV 引擎、AWS Secrets Manager）、可导出密钥的云 KMS、材料可交付的企业 HSM。**不适用**：运算不出托管端的 proxy 形态（Vault Transit 引擎、云厂商非导出 CMK——其"密钥"只接受远程加解密请求，从不交付材料），见 §4.4。
3. **fail-closed = 全部发生在启动期**：托管端不可达/认证失败/材料缺失/配置矛盾 → 实现 bean 初始化抛错 → **应用拒绝启动**。运行期不存在托管端交互，`getKey` 失败仅剩一期"未知 keyId"语义。**绝不回退本地密钥**、不存在降级启动。
4. **密钥来源不混合**：同一部署的全部密钥材料必须经由**同一个** key provider bean（全本地或全某 KMS）；本地材料的唯一合法存在形态是 KMS 实现配置中的**迁移残余列表**（见 §4.3 迁移路径，禁止含 active key），任何其他本地材料痕迹（如 local 配置未清空）= 启动拒绝。
5. KMS 适配实现为**独立可选 Maven 模块**（含对应厂商 SDK 依赖，按需部署），`nop-credential-service` 主模块零第三方 KMS SDK 依赖（模块命名与拆分粒度 W10-impl 裁定）。
6. **keyId → 材料不变式**：同一 keyId 在仍有 `cv1:{keyId}` 密文存续期间材料不可变。KMS 侧轮换必须以**新 keyId**（新资源/新映射）进行；厂商"同名原地升版本"式轮换（AWS KMS 自动轮换、Vault Transit rotate）与 `cv1:` keyId 路由不兼容，属配置禁区。

### 4.2 背景与动机

一期主密钥为 passphrase 落盘形态（配置项 `nop.credential.master-keys`，`keyId:passphrase` 列表）——材料以可读形式存在于部署环境（环境变量/配置文件）。企业合规场景（金融/政企）要求主密钥托管于外部系统：材料不落盘、访问审计、硬件保护、独立轮换。roadmap W10-impl 需要明确"Vault/云 KMS 作为主密钥来源"的接口契约、装配方式、轮换语义与故障语义，本节即该契约。

### 4.3 核心设计

**SPI 契约与实现类职责**：

- KMS 实现类实现 `ICredentialKeyProvider`（接口不变），职责：**启动期**从托管端读取/解包全部已配置 keyId 的密钥材料，按一期相同方式构造单钥加密器集合；实现类配置声明 keyId → 托管端资源（路径/别名/secret 名等）的映射与 active key。材料形态必须与一期 passphrase 同构（可直接用于构造单钥加密器）；材料仅在进程内存中存在，不落本地存储。
- keyId 命名空间与本地一致（`[A-Za-z0-9_-]+`，`cv1:` 密文中 keyId 语义不变）；托管端资源命名由实现类配置映射，`cv1:` 密文不感知托管端存在。

**装配模型（同名 bean 覆盖 + 配置门控）**：

- 配置项 `nop.credential.key-provider`：取值 `local`（缺省）或 KMS 实现标识（如 `vault`）。
- local 缺省路径：`credential-defaults.beans.xml` 中 `nopCredentialKeyProvider` bean（`ioc:default="true"`）装配 `DefaultCredentialKeyProvider`，一期不变。
- KMS 路径：KMS 模块的 beans.xml 定义**同名 bean** `nopCredentialKeyProvider`（覆盖缺省 bean），并以其配置门控（仅当 `nop.credential.key-provider` 等于该实现标识时注册）。约定：KMS 实现 bean **必须复用同一 bean id**、禁止新增第二个 `ICredentialKeyProvider` 类型的独立 bean（同名覆盖 + 单值配置项即"只能装配一个 provider"的结构性执法）。
- 配置指向某 KMS 实现但对应模块未部署 → 无 bean 满足 → 启动失败（fail-closed，防"配置了 KMS 实际跑本地"的假安全）。未引入 KMS 模块的部署零感知（local 默认路径完全不变）。

**多 key 并存与轮换的托管映射**：

- 轮换流程与一期本地轮换同构：托管端新增 key（**新 keyId/新资源**，遵守结论 6 不变式）→ 实现类配置新增 keyId 映射并切换 active → 新写入用新 key；旧密文按 `cv1:` 中 keyId 路由，旧 key 在托管端保持可解密期 = 多 key 并存窗口 → `reencryptAll` 批量重加密 → 托管端退役旧 key。
- `reencryptAll` 语义不变（active key 重加密、幂等跳过、逐条提交可重跑）。**一期已知限制**：live 实现单批查询存在分页上限（单页 1000 条、无翻页循环），凭证量超出时单次执行不保证全覆盖——KMS 迁移关窗与旧 key 退役所依赖的"密文 keyId 全部属于新 key 集合"完备性验证是 W10-impl 的显式交付物（分页完备性修复或密文 keyId 分布查询，二选一由 impl 裁定），本设计不以其单次执行为关窗担保。

**fail-closed 细则（全部启动期）**：

- 托管端不可达/认证失败/配置的 keyId 在托管端缺失/材料非法 → 实现 bean 初始化抛错，应用启动失败。
- KMS 激活（`nop.credential.key-provider != local`）时 `nop.credential.master-keys` 非空 → 启动拒绝（本地材料只允许存在于迁移残余列表，见下）。
- 迁移残余列表包含 active keyId → 启动拒绝。
- 运行期：无托管端调用；未知 keyId 密文按一期语义 fail-closed（`getKey` 抛错 → 上层消费按既有 fail-closed 语义中止）。
- **无本地降级**：任何托管端故障都不触发本地密钥回退（回退 = 攻击者只需打挂托管端即可用弱本地材料解密全部凭证）。

**迁移路径（本地 → 托管端，唯一允许的材料混合窗口）**：

1. **切换与轮换解耦**：切换 provider 时 active keyId **及其材料不变**（旧 passphrase 原样导入托管端同 keyId）——滚动发布窗口内新旧副本密钥能力对称，不存在"新副本写旧副本解不开"的密文。切换完成（全副本稳定运行于 KMS provider）后，再独立执行"托管端生成新 key + 切 active + reencryptAll"的轮换步骤。
2. **迁移窗口开启**：KMS 实现配置声明迁移残余列表（`keyId:passphrase`，与一期同构格式，命名空间如 `nop.credential.<impl>.migration-keys`）——残余 key 只用于解密旧密文，禁止含 active key；每次启动对残余列表输出 WARN 审计（列出残余 keyId，提示收尾）。
3. **关窗前置条件**：全部密文的 keyId 属于 KMS key 集合（验证载体为 W10-impl 交付的完备性查询/统计，见轮换段已知限制）；同时清空 `nop.credential.master-keys`。
4. **关窗后**：残余列表为空（正常态）；若存量密文仍引用残余 keyId（未完成迁移即清配置的部署失误），`getKey` 按一期"未知 keyId"fail-closed——部署失误显式暴露而非静默降级。
5. **反向回退天然可行**：材料交付模式下材料可从托管端导出重建 local 配置（keyId 不变 → 密文零迁移）；托管端 A → 托管端 B 的厂商间迁移不在此设计范围（§七 deferred）。

**HSM 语义**：HSM 视为托管端的实现形态之一——前提是材料可交付（材料在硬件内生成但可安全导出/经安全通道交付的形态）；要求密钥运算永不出硬件的 HSM 使用场景属 proxy 形态，超出本设计集成模式（§4.4 / §七）。

### 4.4 拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| 修改 `ICredentialKeyProvider` 接口（新增 KMS 专属方法） | 破坏一期 SPI 稳定性（`DefaultCredentialKeyProvider` 与既有装配无需任何变更）；KMS 是材料来源的实现细节，不是接口能力 |
| 代理模式（每次加解密远程调用托管端 API；含 Vault Transit 引擎、云厂商非导出 CMK） | 每行凭证读写都引入远程调用：性能（凭证取用是高频路径）与可用性强耦合托管端；密文丧失"本地自包含"的既有事实（无托管端即不可解）；一期无此需求。proxy 形态场景（运算不出硬件的强合规）见 §七 deferred |
| 本地 + KMS 密钥材料常态并存 | 安全边界 = 两者中较弱者（本地 passphrase 泄露即全破）；仅迁移残余列表显式例外（只解不加密、禁止含 active key、WARN 审计、窗口外本地材料痕迹 = 启动拒绝） |
| 托管端故障降级（回退本地密钥/只读模式/跳过加密/降级启动） | 违背一期 fail-closed 语义；降级路径即攻击路径（打挂托管端绕过托管） |
| 选择器类 + collect-beans 装配（W8 `MfaStoreProvider` 同构机制） | store 场景需运行期多实现收集（local/redis 按 store-type 选择不同**能力**实现）；key provider 是**单活**替换语义（同一密钥职责换来源），同名 bean 覆盖 + 单值配置门控即最小机制，引入选择器类是多余中介 |
| KMS SDK 依赖进 `nop-credential-service` 主模块 | 污染可复用模块依赖树（消费方被迫传递依赖特定厂商 SDK）；KMS 适配为独立可选模块，按部署引入 |
| 凭证库内置多厂商 KMS 实现全家桶 | 无需求实证的维护负担；二期只定契约 + 参照实现策略（W10-impl 裁定选型），未覆盖厂商由使用方按 SPI 自行扩展 |
| 热重载密钥配置（密钥集合运行期动态变更） | 一期即明确"启动期一次性加载，热重载为 Non-Blocking Follow-up"；轮换经 active-key-id 切换 + 重启即可完成；热重载的多副本密钥一致性风险大于收益 |
| 厂商同名原地版本轮换（AWS KMS 自动轮换/Vault Transit rotate 类） | 违反 keyId → 材料不变式（结论 6）：同名材料被替换后存量 `cv1:{keyId}` 密文全部不可解；KMS 侧轮换必须以新 keyId/新资源进行 |

### 4.5 与一期契约兼容性

- **`cv1:` 密文格式不变**：keyId 语义、包装格式、解析规则零变更——KMS 只改变"keyId → 密钥材料"的来源；密文自包含性不变（本地材料始终可解，反向回退零迁移）。
- **明文边界不变**：KMS 集成只动密钥层，`ICredentialProvider`/BizModel/xmeta 明文边界机制零感知。
- **软删除/引用计数不变**：与密钥来源无关。
- **缺省部署零变化**：不引入 KMS 模块、`nop.credential.key-provider` 缺省 `local` → `DefaultCredentialKeyProvider` 行为与一期完全一致（`credential-defaults.beans.xml` 不动）。
- **增量声明**：新增配置项（key-provider 选择 + KMS 实现配置 + 迁移残余列表）、可选模块、覆盖 bean——全部为加法；运行期行为（加解密路径、失败语义）与一期完全一致。

## 五、凭证归属统一（scope=system|user + ownerId）

### 5.1 设计结论

1. **归属字段**：`NopCredential` 新增 `scope`（取值 `system | user`，**缺省 `system`**——存量凭证语义零迁移）与 `ownerId`（scope=user 时为归属用户 userId，字符串软引用、无外键；scope=system 时强制为空）。归属一经创建**不可变更**（不含 user↔system 转换与 owner 转让；需要"换人"= 删旧建新，引用计数机制自然约束）。
2. **`usageScope` 一期占位语义定稿：废弃（deprecated）**。live 事实：列已存在但无默认值、全仓库无任何取值消费（"占位（`instance`）"措辞与 live 不符，roadmap 已登记 drift）；二期归属语义由 `scope`/`ownerId` 新字段承载而非复用 `usageScope`（"使用范围"与"归属"语义错位）；列保留不再赋值，物理删除列留后续 DDL 治理（§七）。
3. **调用方身份传播 = 上下文捕获**：消费侧校验经 `IUserContext.get()`（nop-api-core，`ContextProvider` 线程上下文——平台标准身份传播机制）获取当前用户；**`ICredentialProvider` SPI 签名零变更**（不加 caller 参数）。
4. **消费侧归属校验规则（per-method 矩阵）**：scope=system → 服务端调用均可取（一期行为不变，授权收紧归 §六）；scope=user → `getCredential`/`getCredentialData`（明文出口）仅 owner 本人；`mask`（脱敏视图）与 `testCredential`（连通性测试，进程内解密、只回结果）= owner + 管理员。无用户上下文（后台任务/服务间调用）**不可**取用户级凭证（防服务进程冒用个人凭证）。
5. **BizModel 按归属过滤 CRUD（读/写分级）**：读类（get/findPage/findList/findFirst/maskList/typeList/test）——非管理员登录用户可见「全部 system 级 + 自己的 user 级」，管理员可见全部；写类（saveCredential 修改路径/delete/发起 OAuth 授权）——system 级限管理员，user 级限 owner + 管理员。归属过滤为**结构性规则**（硬编码语义），不做成可配置数据权限。
6. **多系统共用密钥服务的归属语义**：system 级 = 共享凭证（管理员管理，跨消费方/跨系统复用，引用计数不限消费方数）；user 级 = 个人私有凭证（仅本人消费）。消费方绑定两类凭证的机制一致（consumerRef 约定不变，与归属正交）。
7. **不做租户隔离**（用户裁决落点）：scope/ownerId 均无租户维度；平台开启租户能力时凭证库为平台全局资源（同 `nop_auth_session` 等 no-tenant 表语义）；租户维度隔离如未来需要，属平台级能力演进，非凭证库职责。
8. **管理员判定**：配置项 `nop.credential.admin-roles`（CSV，**缺省 `admin,nop-admin`**——与 nop-auth 事实管理员角色名对齐），经 `IUserContext.isUserInAnyRole` 判定；凭证库不依赖 nop-auth 模块（角色名以配置自持，见 §六 依赖边界）。

### 5.2 背景与动机

一期凭证全部为事实上的系统级共享（任何服务端代码可取，管理面无归属区分）——服务"多个外部系统共用同一密钥管理服务"时无法表达"这是某个用户的个人凭证"（如用户自己的第三方 API Key，不应被其他用户/无名后台进程取用）。n8n 凭证系统有 owner 概念（个人凭证 + 共享），属真实差距。roadmap 二期以"归属统一（系统级/用户级）"替代租户隔离（用户 2026-08-14 裁决：租户为平台全局能力），本节定义归属字段语义、消费侧校验与管理面过滤。

### 5.3 核心设计

**字段语义与默认值**：

- `scope`：`system | user`，缺省 `system`。缺省值保证存量数据与既有调用（不传 scope 的 saveCredential）行为零迁移——一期凭证天然是系统级共享。**存量行的 NULL 语义**：新列对存量行为 NULL，NULL 视同 `system`（校验侧 NULL 容忍）；新写入恒写显式值；不做存量回填 DDL（DDL 层加列默认值由 W11-impl 裁定，语义等价）。
- `ownerId`：scope=user 时必填（saveCredential 层校验），scope=system 时强制空（传入即拒绝）。字符串 userId，与 nop-auth 用户标识同构，不做外键/跨模块 join（凭证库不依赖 nop-auth，见 §六 依赖边界）。**owner 消亡为显式接受后果**：owner 用户被删除/停用后其 user 级凭证永久不可取用（fail-closed），仅管理员可见/可删；不做"owner 不存在则放行"类补丁。
- 归属不可变：update 路径不接受 scope/ownerId 变更（传入与存量不符即拒绝）；"转让"语义 = 删除旧凭证 + 新建（引用计数删除拦截天然保护消费方）。
- **管理员代建**：允许管理员创建 user 级凭证并指定他人为 owner（运维代录入场景）；创建者与 owner 不一致记入审计。

**调用方身份传播（上下文捕获）**：

- 校验点在 `CredentialProviderImpl` 各方法入口（按 §5.1 结论 4 的 per-method 矩阵，非所有方法一刀切）、解密之前：经 `IUserContext.get()` 取当前用户上下文（nop-kernel/nop-api-core，`ContextProvider` 线程上下文；GraphQL/biz 请求链中由框架注入；无登录态/后台任务/异步自建线程 → 无用户上下文）。唯一解密点不变式不受影响（mask/test 的进程内解密属服务端内部动作，**明文契约精确化为"明文不返回给非 owner 调用方"**）。
- 校验语义（fail-closed，拒绝抛归属不符错误——错误码归属 nop-credential-service 模块自有错误类，前缀延续 `nop.err.credential.*`；不返回 null/空）：
  - scope=system：放行（授权维度归 §六）。
  - scope=user：`getCredential`/`getCredentialData` 无用户上下文或 `userId != ownerId` → 拒绝（**管理员亦不例外**——明文取用权 owner 唯一）；`mask`/`testCredential` owner 或管理员放行。
- 传播机制的既定事实：一期消费链（W7-successor：`ChatServiceImpl` → `IAiModelCredentialResolver` → `ICredentialProvider`）运行于用户请求线程内，上下文天然可达；后台批处理（job/wf）无用户上下文，天然只能消费 system 级——与"个人凭证不被无名进程冒用"的目标一致。

**BizModel 按归属过滤 CRUD（两层防御）**：

- **BizModel 层前置过滤**（实体明文元数据列，无需解密）：读类动作查询注入结构性条件——非管理员限定「scope=system ∪ (scope=user ∧ ownerId=本人)」；写类动作前置校验（system=管理员，user=owner+管理员）。单条访问（get/maskList/test）对越权目标**归一为"不存在"语义**（与软删除同口径，防 credentialId 枚举探测归属）。
- **provider 层归属校验为纵深防御**（见上）：BizModel 面被绕过时第二道 fail-closed。
- `saveCredential`：新增可选输入 scope/ownerId；普通用户创建 user 级凭证时 ownerId **强制等于当前登录用户**（不可指定他人）；创建 system 级凭证限管理员；缺省不传 = system（一期行为不变）。
- **继承动作面收口**：标准 `update`/`batchDelete` 禁用（与标准 `save` 同理——绕过加密、归属不可变与引用拦截的旁路）；`findList`/`findFirst` 等继承查询动作应用与 `findPage` 相同的结构性过滤；`reencryptAll` 限管理员（进程内重加密不出明文，但属全量敏感操作）；usage 记录查询面（`NopCredentialUsageBizModel`）限管理员（引用关系属管理信息；消费方登记走 SPI `registerUsage`/`unregisterUsage` 不受影响）。
- 拒绝把归属过滤建模为 `NopAuthRoleDataAuth` 类可配置数据权限规则：归属是凭证模型的结构语义（创建时确定、不可变更），做成可配置规则会引入"配置错误即越权/丢访问"的治理面，收益为零。

**与一期管理 API 的兼容输入**：既有调用（不传 scope/ownerId）= 创建/查询 system 级，与一期完全一致。

### 5.4 拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| 复用 `usageScope` 列承载归属 | 语义错位（使用范围 ≠ 归属）；live 列无默认值、无任何取值消费，复用需同时补语义与默认值迁移，不如废弃+新字段干净；新字段 `scope`/`ownerId` 语义自描述 |
| `ICredentialProvider` SPI 加 caller 参数（如 `getCredential(credentialId, callerUserId)`） | 破坏一期接口兼容：全部消费方与实现类签名变更，W7-successor 已落地链路需返工；异步/reactor 链路手动透传 caller 繁琐且易漏（漏传即安全漏洞）；`ContextProvider` 是平台标准身份传播机制，请求链内天然可达 |
| scope/ownerId 可变更（转让/提升） | 归属可变使"谁在用这个凭证"的推理复杂化（引用计数登记的 consumerRef 与 owner 交叉错乱）；删旧建新 + 引用拦截的等价路径已存在 |
| 管理员可取用他人 user 级凭证**明文**（getCredential 出口） | 最小权限：管理面（脱敏视图 mask/连通性 test）足以完成管理职责；明文出口（getCredential/getCredentialData）owner 唯一，避免 admin 权限滥用面 |
| 后台任务/服务身份可代取 user 级凭证 | 服务进程冒用个人凭证的攻击路径（拿到代码执行即拿到全部个人凭证）；fail-closed 拒绝 |
| 归属过滤做成可配置数据权限（RoleDataAuth 类规则） | 归属是结构语义不是授权策略；可配置化引入配置错误面且无对应需求 |
| 租户字段（tenantId） | 用户裁决不做（租户为平台全局可开启能力，非凭证库职责） |

### 5.5 与一期契约兼容性

- **`cv1:` 密文格式不变**：归属是明文列级元数据，不触碰密文。
- **明文边界不变（契约精确化）**：明文不返回给非 owner 调用方——明文出口（getCredential/getCredentialData）owner 唯一；mask/test 的进程内解密属服务端内部动作，返回物为脱敏视图/测试结果；管理面查询 data 恒置空/mask 机制不变。
- **软删除 fail-closed 不变**：delFlag 检查先于归属检查（都不通过时按"不存在/已删除"语义，不泄露归属信息）。
- **引用计数不变**：registerUsage/unregisterUsage 与 consumerRef 约定（如 `ai:NopAiModel:<id>`）零变更，与归属正交。
- **存量零迁移**：scope 缺省 system + ownerId 空 = 一期行为完全一致；既有消费链（W7-successor）对 system 级凭证零感知。
- **增量声明**：ORM 新增列（scope/ownerId，W11-impl 的 Protected Area 变更，见 §八）；saveCredential 新增可选输入；查询新增结构性过滤；消费侧新增归属校验（system 级放行 = 一期行为不变）。

## 六、RBAC 细粒度授权（凭证级"谁可以使用哪个凭证"）

### 6.0 输入基线：nop-auth RBAC 能力盘点（live 锚点）

**实体模型**（`nop-auth/model/nop-auth.orm.xml`）：

| 实体 | 表 | 关键列/语义 | 行号锚点 |
|---|---|---|---|
| `NopAuthRole` | `nop_auth_role` | `roleId` PK、`roleName` 唯一、`childRoleIds`（复合角色=一组子角色）、`isPrimary`（用户多角色时的主角色标记）、软删除 | orm.xml:237 |
| `NopAuthUserRole` | `nop_auth_user_role` | `userId`+`roleId` 复合 PK（用户↔角色多对多映射） | orm.xml:293 |
| `NopAuthRoleResource` | `nop_auth_role_resource` | `roleId`+`resourceId`——"角色可访问的资源，包括模块菜单和操作按钮"（资源树/操作按钮粒度的操作授权） | orm.xml:552 |
| `NopAuthRoleDataAuth` | `nop_auth_role_data_auth` | `roleIds`+`bizObj`+`priority`+`filterConfig`/`whenConfig`——按业务对象配置行级数据过滤规则 | orm.xml:602 |
| `NopAuthResource` | `nop_auth_resource` | site map 资源树（菜单/按钮），`NopAuthRoleResource.resourceId` 的引用目标 | — |

**运行时校验机制**（作用层级与 live 锚点）：

1. **用户上下文（角色来源）**：`IUserContext`（`nop-kernel/nop-api-core/.../auth/IUserContext.java`）——`getRoles()`/`isUserInRole`/`isUserInAnyRole`/`getUserId()`；静态 `IUserContext.get()` 经 `ContextProvider` 从线程上下文取当前登录用户（服务端代码内任意位置可得）。登录时由 nop-auth 从 `NopAuthUserRole`（含复合角色展开）填充进会话上下文。
2. **biz 操作/字段级**：`@Auth` 注解（`nop-api-core/.../annotations/directive/Auth.java`：`publicAccess`/`skipWhenNoAuth`/`roles`（任一命中即过）/`permissions`（全部满足才过））→ `GraphQLActionAuthChecker`（`nop-graphql-core`）在 GraphQL 执行前按 biz 字段的 `ActionAuthMeta` 校验：action 级不通过抛 `ERR_AUTH_NO_PERMISSION`，字段级不通过按 `skipWhenNoAuth` 移除字段或抛错。
3. **permission → 角色 映射**：`IActionAuthChecker`（`nop-api-core` SPI）→ `DefaultActionAuthChecker`（`nop-auth-service`）→ `SiteMapProviderImpl`/`SiteCacheData`：site map + `NopAuthRoleResource` 构造 permission→roles 缓存，`isUserInAnyRole` 判定。`nop.auth.skip-check-for-admin` 配置（默认 false）可让 admin/nop-admin 角色跳过。
4. **行级数据权限**：`IDataAuthChecker`（`nop-api-core` SPI）→ `DefaultDataAuthChecker`（`nop-auth-service`）：按 `bizObj` 取 `NopAuthRoleDataAuth` 的 `filterConfig` 生成查询过滤（CrudBizModel 查询链自动应用）。

**盘点结论（gap）**：nop-auth RBAC 的授权粒度是 **biz 操作（菜单/按钮/GraphQL action）** 与 **bizObj 行级过滤**，不存在**单条业务数据实例级**（"哪个角色可以用哪一行"）的授权模型；且 permission→roles 校验链依赖 `nop-auth-service` 的 sitemap 缓存。凭证库需要的是"角色 ↔ 单个凭证实例"的取用授权，属实例级空白，§6.1 在此基线上裁定建模方式与依赖边界。

### 6.1 设计结论

1. **授权模型 = 新 ORM 实体 `NopCredentialAuth`（角色 ↔ 凭证 二值授权表）**：主体=角色（roleId，字符串软引用 nop-auth 角色）、客体=单个凭证实例（credentialId）、动作=**use**（明文取用，即 `getCredential`/`getCredentialData` 出口）。凭证级授权只管"用"；"管"（创建/修改/删除/授权本身）走归属规则（§五）+ 管理员判定，不做凭证级授权。
2. **默认开放、可选收紧**：无授权记录的凭证 = 任何服务端调用可取（一期行为完全不变）；配置了授权记录 = 收紧为"命中授权角色（有用户上下文时）"。拒绝 deny-by-default（会破坏一期消费链回归，见 §6.4）。
3. **消费侧校验点 = provider 明文出口、解密之前**，与归属校验同层串联（完整判定矩阵见 §6.3）；校验数据 = 自有授权表 + `IUserContext` 角色集合，**不调用** nop-auth 的 permission→roles 校验链。
4. **依赖方向：nop-credential 不依赖任何 nop-auth 模块**（api/service/dao 均不依赖 nop-auth-api/nop-auth-service/nop-auth-sso）；角色数据经 `IUserContext`（nop-api-core，登录时由 nop-auth 填充进会话上下文）传播到校验点，roleId 为字符串软引用（无外键，与平台 `childRoleIds` 等软引用惯例一致）；nop-auth 对凭证库零感知。唯一共享点是 kernel 层 `nop-api-core`（凭证 api 层本就依赖）。
5. **管理员不自动豁免取用**：授权记录收紧后，admin 角色不因管理员身份自动获得取用权（与 `nop.auth.skip-check-for-admin` 默认 false 的平台先例一致）；管理员需经授权管理面 grant 自己的角色。授权管理动作（grant/revoke/查询）本身限管理员。

### 6.2 背景与动机

一期凭证授权模型 = "管理员管理 + 服务端 SPI 消费"（vision §三.1 明示 RBAC 细粒度授权留二期）。§6.0 盘点结论：nop-auth RBAC 的授权粒度是 biz 操作级（菜单/按钮/GraphQL action，经 sitemap permission→roles）与 bizObj 行级（RoleDataAuth 过滤规则），**不存在业务数据实例级授权模型**（"哪个角色可以用哪一行"），且 permission→roles 校验链的实现载体在 nop-auth-service。"多个外部系统共用同一密钥管理服务"场景下，需要表达"这批凭证只允许使用方 X 的运维角色取用"——即凭证实例级 use 授权，属平台既有 RBAC 的空白，本节裁定建模方式、校验点与依赖边界。

### 6.3 核心设计

**授权数据模型**：`NopCredentialAuth`（新实体，表归 `nop-credential.orm.xml`）：credentialId + roleId + 审计字段，(credentialId, roleId) 唯一约束；**物理删除、无软删除列**（对齐 `NopCredentialUsage` 惯例——revoke→re-grant 循环靠物理删除与唯一约束天然成立），授权变更审计走平台 ChangeLog（`tagSet="audit"`）。授权粒度仅单凭证（凭证分组授权无需求实证，§七 deferred）。**幂等契约**（对齐 `registerUsage`/`unregisterUsage` 先例）：grant 已存在的 (credentialId, roleId) = no-op 成功；revoke 不存在的记录 = no-op 成功。**入参校验**：grant 拒绝 scope≠system 的凭证（user 级不叠加角色授权，管理面不产生永不生效的授权记录）；不做 roleId 存在性校验（跨模块校验将破坏依赖边界）——死 roleId 记录无害（求交永不命中），管理面原样展示。

**消费侧判定矩阵**（`getCredential`/`getCredentialData` 明文出口；"—" = 该列不影响判定）：

前置条件（先序，任意 scope/上下文/记录）：`delFlag` fail-closed（一期语义）；已删凭证一律拒绝，不进入下表。

| # | scope | 用户上下文 | 授权记录 | 结果 |
|---|---|---|---|---|
| 2 | user | 非 owner（含 admin）/ 无上下文 | — | 拒绝（§五 归属，owner 唯一明文出口） |
| 3 | user | owner | — | 放行（归属即授权，user 级**不叠加**角色授权） |
| 4 | system | 任意 | 无 | 放行（一期行为不变——增量兼容基线） |
| 5 | system | 无用户上下文（后台任务/服务间） | 有 | 放行（服务级信任：SPI 是服务端边界，收紧针对"人"的冒用，不针对服务代码——**信任边界与消费方义务见下**） |
| 6 | system | 用户 | 有 | 用户角色 ∩ 授权角色 ≠ ∅ → 放行；否则拒绝（admin 角色不自动豁免，见 §6.1 结论 5） |

- **角色快照时效**：用户角色集合为登录时快照（nop-auth 填充会话上下文，含一级 `childRoleIds` 展开、非递归）；`nop_auth_user_role` 变更对已存在会话不生效直至重新登录。因此：凭证侧授权（grant/revoke 记录）**即时生效**（校验为 DB 点查）；用户侧角色回收对存量会话的生效时点 = 该用户下次登录。
- **角色层级语义**：授权记录 roleId 按**字面 roleId** 参与求交（不展开其子角色）；grant 父角色只覆盖直接持有该父角色的用户（用户侧快照的单级展开语义一致）。
- **信任边界与消费方义务（矩阵第 5 行的显式推论）**：判定输入（`IUserContext` 有无）由消费链路决定——以用户名义发起的消费链**必须保持上下文传播**，链路丢失上下文（异步化改造/消费方缺陷）即事实绕过第 6 行收紧（退化为第 5 行放行）。此为设计接受的信任边界（服务端代码不在防范对象内），上下文丢失的告警审计登记 §七 watch-only residual。
- 拒绝 = fail-closed 抛授权不符错误（错误码归 nop-credential-service，前缀延续 `nop.err.credential.*`）；明文出口不经 BizModel，无对外归一问题。
- **`mask`/`testCredential` 不做凭证级授权**：管理面动作按 §5.3 规则（system 级脱敏视图/测试对所有登录用户开放——读类可见即 `mask` 可得脱敏值；user 级 owner+admin）。凭证级授权只作用于明文出口。
- **发起 OAuth 授权（写动作）不做凭证级授权**：写权限归归属规则（system=管理员，user=owner，§3.3）。

**校验实现语义**：校验在 `CredentialProviderImpl` 明文出口处（与归属校验同点串联）：加载凭证 → 归属判定（§五矩阵）→ system 级再查授权记录（按 credentialId 查 `NopCredentialAuth` 的 roleId 集合）→ 与 `IUserContext.get().getRoles()` 求交。授权记录查询的性能语义：无记录路径（绝大多数凭证，一期兼容基线）必须与一期等价（一次存在性查询，无额外远程/复杂 join）；有记录路径增加一次按 credentialId 的索引查询。

**管理面**：授权管理 action（列出某凭证授权 / grant / revoke）挂 `NopCredentialBizModel`（或独立授权 BizModel，W11-impl 裁定），限管理员——**运行时角色判定**（取 §5.1 结论 8 的 `nop.credential.admin-roles` 口径；不用编译期静态 `@Auth(roles)` CSV，避免与配置项形成双源真相）；Web 管理页在凭证详情页扩展授权角色编辑（AMIS，W11-impl 范围）。**收紧运维语义**：对已登记引用（`NopCredentialUsage`）的凭证做授权收紧会切断对应消费链路（"已绑定但不可用"），管理面同时可见授权列表与引用列表供收紧前核对（授权与引用正交，不自动告警）。

**与归属模型的组合规则**：归属（§五）决定"谁管、谁能出口明文（user 级 owner 唯一）"；凭证级授权（本节）只对 system 级的明文出口做可选收紧。两模型串联不交叉：user 级不叠加角色授权（owner 已是唯一出口，叠加徒增复杂度）；system 级不受归属限制（管理员管理 + 授权收紧）。

**与 nop-auth RBAC 的复用边界**（输入 = §6.0 盘点）：

| 复用项 | 层级 | 方式 |
|---|---|---|
| 角色/用户-角色映射 | 数据 | 不复制——经 `IUserContext.getRoles()`（nop-auth 登录时填充，含复合角色展开）消费 |
| 角色标识 | 数据 | roleId 字符串软引用（无外键）；角色管理界面仍在 nop-auth |
| `@Auth`/`GraphQLActionAuthChecker` biz 级校验 | 机制 | 不用于凭证级（那是 action 粒度）；凭证 BizModel 自身 action 的管理员限制可用 `@Auth(roles)` 或运行时判定（W11-impl 裁定，角色名取 `nop.credential.admin-roles` 口径） |
| `IActionAuthChecker`/sitemap permission→roles | 机制 | **不复用**——实现在 nop-auth-service（引入反向依赖），且 sitemap 静态缓存不适合动态凭证实例 |
| `IDataAuthChecker`/RoleDataAuth 行级过滤 | 机制 | **不复用**——归属过滤是结构语义（§5.4 已拒绝可配置化）；凭证级授权是实例级点查不是 bizObj 级过滤规则 |

### 6.4 拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| 通用权限引擎（主体-客体-动作三元组 + 通配符 + 继承） | 过度设计：凭证场景只需"角色↔凭证"二值 use 授权；三元组引擎的查询/继承/通配复杂度无对应需求 |
| 授权挂 sitemap 资源树（`NopAuthRoleResource` + 凭证动态注册为资源） | 需要 nop-credential 反向依赖 nop-auth-service 的 sitemap 机制 + `SiteCacheData` 静态缓存动态化改造；业务运行期数据（凭证 CRUD）进权限配置树属数据归属错位 |
| 默认 deny-by-default（无授权记录 = 仅 admin 可取） | 破坏一期消费链回归：W7-successor 链上普通用户发起 AI 聊天经凭证取 apiKey，默认收紧将使全部存量消费失败；"默认开放 + 可选收紧"才符合增量兼容原则 |
| admin 角色自动豁免授权收紧 | 与平台先例（`skip-check-for-admin` 默认 false）一致：凭证是敏感资源，管理员的管理权不等于取用权；需要时 grant 自己的角色，动作留审计 |
| user 级凭证叠加角色授权（owner 授权他人/角色取用） | user 级明文出口 owner 唯一（§五），叠加授权与归属模型冲突；共享需求（n8n sharing 模式）未证实，deferred（§七） |
| nop-credential 依赖 nop-auth（api 或 service） | 违反 vision §五.1 依赖约束精神（凭证库独立可复用，消费方不应被迫连带 auth 模块）；角色经会话上下文传播即够 |
| 授权记录进 `NopAuthRoleResource` 的 resourceId 约定（不建新表） | resourceId 语义是 sitemap 资源树外键（ref NopAuthResource），塞业务实例 ID 破坏引用完整性且被 sitemap 缓存语义误读 |

### 6.5 与一期契约兼容性

- **`cv1:` 密文格式不变**：授权是元数据表，不触碰密文。
- **明文边界不变**：授权校验在解密之前（拒绝时密文不解）；明文出口语义（唯一解密点、SPI 服务端边界）不变。
- **软删除/引用计数不变**：授权记录随凭证删除**物理级联清理**（to-many cascade 声明，同 usage 表惯例；凭证删除被引用计数拦截时凭证与授权同存属正常语义，W11-impl 验证逻辑删除路径上的清理行为）；引用计数机制与授权正交。
- **一期行为零回归**：无授权记录 = 矩阵第 4 行 = 一期行为（放行）；一期存量凭证与消费链（W7-successor）零感知。
- **增量声明**：新 ORM 实体（`NopCredentialAuth`，W11-impl 的 Protected Area 变更，见 §八）+ provider 明文出口新增 system 级可选收紧 + 授权管理面 action——全部为加法。

## 七、跨主题裁定：out-of-scope / deferred 汇总

**维持一期拒绝（不翻案）**：`@credential:` 配置 resolver——四主题均未产生对其的新需求（OAuth/KMS/归属/RBAC 都不动配置加载链），一期 baseline §4 的拒绝理由（无通用 value resolver 链 + 启动顺序问题）继续成立。

| # | 条目 | Classification | Why Not Blocking | Successor Required |
|---|---|---|---|---|
| 1 | PKCE（机密客户端纵深防御，OAuth 2.1 方向） | optimization candidate | 二期闭环以机密客户端为覆盖目标（secret 已加密托管），PKCE 非闭环成立前提；A1-audit 评估后决定是否并入 | yes（A1-audit 或 W9-impl 增强） |
| 2 | `status=disabled` 取用语义全局收紧（非 OAuth 类型取用路径拒绝 disabled 凭证） | out-of-scope improvement | 一期 live 取用只 fail-closed 于 delFlag；全局收紧是存量行为变更（可能切断依赖 disabled 凭证的既有消费方），须独立裁定 + 迁移评估，不随二期设计静默带入；OAuth 类型已收紧（§3.5 显式增量） | yes（独立裁定，或 A1-audit 发现项） |
| 3 | user 级凭证共享给其他用户/角色（n8n sharing 模式） | out-of-scope improvement | 需求未实证（owner-only 已满足二期目标）；与"明文出口 owner 唯一"的归属模型冲突，引入需重开归属语义 | no（需求实证后重开设计） |
| 4 | 凭证分组授权（一次 grant 一组凭证） | out-of-scope improvement | 单凭证粒度已满足"受限共享"场景；分组是管理便利性而非能力缺口 | no（需求实证后并入 §六） |
| 5 | KMS proxy 模式（运算不出托管端：Vault Transit/非导出 CMK/强合规 HSM 场景） | out-of-scope improvement | 材料交付模式已覆盖可交付形态；proxy 模式需要新接口形态（远程加解密契约）与性能/可用性论证，一期无此需求 | no（强合规需求出现时新开设计） |
| 6 | KMS 厂商间迁移（KMS A → KMS B） | out-of-scope improvement | 可经"导出材料 → 本地中转/直接重建 + keyId 不更名（密文零迁移）"手工路径完成；产品化迁移编排无需求实证 | no |
| 7 | `usageScope` 列物理删除（DDL 清理） | optimization candidate | 语义已废弃（§5.1 结论 2）、列不再赋值、无消费方；DDL 治理动作，与功能正确性无关 | yes（W11-impl 顺带或独立 DDL 治理） |
| 8 | 消费链上下文丢失告警审计（§6.3 信任边界的观测手段） | watch-only residual | 信任边界（服务端代码不受收紧约束）为有意设计且已显式声明；告警审计是可观测性增强，不影响契约成立 | no（A1-audit 评估） |
| 9 | 类型级 OAuth 应用凭据共享（同类型多实例共用一套 clientId/clientSecret 的管理便利） | optimization candidate | 现设计 per-instance 加密存储（安全成立，重复录入是管理成本而非缺陷）；类型级共享需引入"类型级机密"存储语义 | no（管理痛点实证后并入 §三） |

## 八、设计 → impl 映射（W9-impl / W10-impl / W11-impl）

> Protected Area 判定依据 AGENTS.md：ORM 模型结构（`model/*.orm.xml`）与跨模块公共 API 变更需 **plan-first**；`nop-xdefs`（kernel）内 xdef 变更同属 plan-first 范畴。

### W9-impl（OAuth 流程引擎）

- **消费小节**：§三 全部 + §5.1 结论 4/8（发起动作归属/管理员判定的回补点）+ §6.3 信任边界（回调通道语义）。
- **交付面**：`credential-type.xdef` 扩展（`authType` 取值域收敛 + `oauth2` 元数据声明位）+ 类型实例校验（保留字段名占用拒绝）；发起 action（`beginOAuthFlow` 语义）+ 单一公开回调端点；state 绑定持久化（跨副本可读，载体 impl 裁定）+ 一次性消费；token 集加密回写（引擎保留字段）+ `saveCredential` 分组写/保留字段拒绝 + `typeList` 表单裁剪；惰性刷新（跨副本互斥载体 impl 裁定）；oauth2 类型 `status=disabled` 全路径拒绝。
- **Protected Area（plan-first）**：`nop-kernel/nop-xdefs` 的 `credential-type.xdef` 变更；新增 publicAccess REST 面（公开攻击面，plan 内需安全审查项）。
- **不触碰**：ORM 模型（scope/ownerId/NopCredentialAuth 均归 W11）；`ICredentialProvider` SPI 签名。

> **W9-impl 裁定标注（2026-08-16 回写）**：**state 存储载体 = 新 ORM 实体 `NopCredentialOauthState`（表 `nop_credential_oauth_state`）**，本设计 §3.3"服务端持久化 + 跨副本可读"的 impl 落点。理由：W8 用户裁决（缺省不用 Redis、存储必须有 DB 实现）排除 Redis-only；本地内存 map 跨副本失效排除；绕过 ORM 手写 SQL 表违反 model-first 惯例（W8 `DbMfaChallengeStore` 先例亦走 ORM 实体）。"不触碰 ORM 模型"的括号枚举（scope/ownerId/NopCredentialAuth）指 W11 归属变更；state 载体为 §3.3/§八显式留白的 impl 裁定。plan-first 载体：`ai-dev/plans/2026-08-14-2342-1-credential-oauth-flow-engine.md` Phase 1。

### W10-impl（外部 KMS/HSM 集成）

- **消费小节**：§四 全部。
- **交付面**：`nop.credential.key-provider` 配置项；KMS 参照实现模块（独立可选 Maven 模块，命名 impl 裁定）+ 同名 bean 覆盖装配（`ioc:default` 覆盖 + 配置门控）；实现类配置命名空间（keyId→资源映射、active key、迁移残余列表）；启动期 fail-closed 校验组（材料缺失/来源混合/残余含 active key/master-keys 残留）；**完备性验证交付物**（reencryptAll 分页完备性修复 或 密文 keyId 分布查询，二选一——§4.3 登记的一期已知限制）。
- **Protected Area（plan-first）**：无 ORM/跨模块公共 API 变更；新增 Maven 模块涉根 pom 注册（模块结构变更，plan 内声明）。
- **不触碰**：`ICredentialKeyProvider` 接口；`credential-defaults.beans.xml` 缺省装配；`cv1:` 密文格式。

### W11-impl（凭证归属统一 + RBAC 细粒度授权）

- **消费小节**：§五 + §六 全部；§3.3 发起动作归属规则（W9 落地后的回补）；§6.0 盘点（输入基线，已自包含）。
- **交付面**：`NopCredential` 新增 `scope`/`ownerId` 列（model-first：`nop-credential.orm.xml` 源 → codegen → DDL 迁移，**禁止手编 `_gen/` 与 `_` 前缀文件**）；新实体 `NopCredentialAuth`（物理删除 + (credentialId, roleId) 唯一约束 + to-many cascade）；`nop.credential.admin-roles` 配置；provider 明文出口归属 + 授权校验串联（判定矩阵 §6.3）；BizModel 读/写分级过滤 + 单条越权归一"不存在" + 标准 `update`/`batchDelete` 禁用 + 继承查询动作过滤；`mask`/`testCredential` per-method 矩阵；授权管理 action（grant/revoke/listGrants，运行时管理员判定）；`usageScope` 废弃标注（列物理删除按 §七#7 裁定顺带或独立处理）。
- **Protected Area（plan-first）**：`nop-credential/model/nop-credential.orm.xml` 结构变更（加列 + 新实体）——ORM 模型结构变更属 plan-first；标准 `update`/`batchDelete` 禁用属 BizModel 行为变更（plan 内声明回归影响）。
- **不触碰**：`ICredentialProvider` SPI 签名（上下文捕获裁决保证）；`cv1:` 密文格式；nop-auth 任何模块（依赖方向裁定）。
- **规模提示**：若交付面超单 plan 规模（5-15 文件/200-500 行/1-4 phases），按 roadmap 粒度约束先行拆分裁定（如"ORM 模型 + 归属"与"RBAC 授权"两份 plan），不硬撑单文件。
