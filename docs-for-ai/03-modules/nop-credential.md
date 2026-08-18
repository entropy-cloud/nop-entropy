# nop-credential — 加密凭证库模块

## 功能概览

为平台提供**行级敏感数据**（DB 中动态写入的密钥/连接参数）的类型化加密存储与统一解密入口，弥补 `@sec:` 配置加密只能处理"配置文件静态值"的不足。

- **类型化凭证**：声明式类型注册（`*.credential-type.xml`），定义字段结构（apiKey/secret/用户名密码等）
- **OAuth 流程引擎**（W9）：`authType=oauth2` 类型的授权码闭环（发起 → 单一公开回调 → token 加密回写）+ 取用时惰性刷新（DB 行级锁跨副本互斥）
- **加密存储**：`cv1:{keyId}:{v1密文}` 版本化密文格式，复用 `nop-commons` 的 `AESTextCipher`（AES-256-GCM + PBKDF2-SHA256）
- **多密钥与轮换**：`ICredentialKeyProvider` 支持多 key 并存，`reencryptAll` 批量重加密（确定性排序 + 游标翻页全覆盖）
- **外部 KMS/HSM 集成**（W10）：主密钥材料托管于 Vault KV v2（材料交付模式），独立可选模块 `nop-credential-kms-vault`
- **归属统一**（W11）：`scope=system|user` + `ownerId`（归属不可变；明文出口 user 级 owner 唯一、管理面 owner+管理员；BizModel 结构性读过滤 + 写分级两层防御）
- **RBAC 细粒度授权**（W11 Part B）：`NopCredentialAuth`（角色↔凭证实例二值 use 授权表）——默认开放、可选收紧，仅作用于 system 级明文出口；admin-only grant/revoke 管理面（幂等契约）
- **唯一明文出口**：`ICredentialProvider` 实现位于 service 层，是明文离开模块边界的唯一通道；`reencryptAll` 进程内重加密直接复用 `CredentialCipher`（解密→重加密不出服务进程、无明文出口，A1-audit 措辞裁定）
- **明文边界结构性强制**：xmeta `data` 列 `published=false`，BizModel 层恒置空，展示用 `maskList` 脱敏值
- **引用计数删除拦截**：删除凭证前检查 `NopCredentialUsage` 引用计数，>0 拒绝删除

## 与 `@sec:` 配置加密的边界

| | 凭证库（nop-credential） | `@sec:` 配置加密 |
|---|---|---|
| **服务对象** | DB 行级动态数据（业务实体敏感列，运行期写入） | 配置文件中的静态密钥（启动期一次解密） |
| **加解密时机** | 运行期经 `ICredentialProvider` 实时解密 | 启动期 `DefaultConfigValueEnhancer` 一次解密 |
| **管理能力** | 类型注册 / 连通性测试 / 密钥轮换 / 引用计数 / 脱敏展示 | 无（仅配置值替换） |
| **密文格式** | `cv1:{keyId}:{v1密文}`（多 key 轮换） | `v1:{密文}`（单 key） |
| **典型场景** | `NopAiModel.apiKey`、数据源密码等 DB 列 | `oss.secret-key`、`feishu.appSecret` 等配置项 |

`@sec:` 配置加密机制详见 `../02-core-guides/ioc-and-config.md`（"配置值加密 `@sec:`"章节）。

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopCredential | `nop_credential` | 加密凭证实例（data 列存 `cv1:` 密文 JSON；scope/ownerId 归属列，见下"凭证归属"章节） |
| NopCredentialUsage | `nop_credential_usage` | 凭证引用登记（credentialId + consumerRef 唯一约束；查询面限管理员） |
| NopCredentialAuth | `nop_credential_auth` | 凭证取用授权（credentialId + roleId 唯一约束；物理删除；凭证删除时物理级联清理，见下"RBAC 细粒度授权"章节） |
| NopCredentialOauthState | `nop_credential_oauth_state` | OAuth state 绑定（一次性消费 + TTL 过期 + 惰性清理，引擎内部存储；管理面 A1-audit 收口：查询面限管理员、全部标准 mutation 禁用） |

> 凭证类型（`*.credential-type.xml`）无 DB 表，经平台 register-model 机制声明式加载。

## OAuth 流程引擎（W9，authType=oauth2）

出站 OAuth 2.0 客户端：授权码换取闭环（发起 → 单一公开回调 → token 加密回写）+ 取用时惰性刷新。
协议实现仅协议级参照 `nop-auth-sso`（不引入该依赖）。

### 凭证类型声明与校验规则

- `authType` 取值域：`none | apiKey | basic | oauth2`（xdef 内联枚举 + registry 双保险，域外拒绝）。
- `authType="oauth2"` 时类型文件必须声明 `<oauth2>` 元数据：`authorizationEndpoint`/`tokenEndpoint` 必填，`scopes`/`refreshWindowSeconds` 可选；缺端点在 registry 加载期拒绝（fail-closed，无静默跳过）。
- 非 oauth2 类型声明 `<oauth2>` 元数据 → 拒绝（配置错位显式暴露）。
- **引擎保留字段名**：`accessToken`/`refreshToken`/`expiresAt`/`tokenType`/scope 五个名字归引擎独占——类型文件 fields 占用拒绝；`saveCredential` 输入出现拒绝（防伪造 token 破坏刷新状态机）；token 集只能由授权码闭环与惰性刷新写入。
- `expiresAt` 为 epoch 毫秒（服务器时钟绝对时刻 = 换取/刷新时的 now + 响应 `expires_in` 秒）。

### 发起 / 回调 / 惰性刷新语义

- **发起**（`CredentialOAuthApi__beginOAuthFlow` mutation，登录态）：校验 实例存在/未删/未禁用/类型 oauth2/**clientId 与 clientSecret 已录入（D2-02：发起侧字段集对齐回调侧，缺任一拒绝，param 标注缺失字段集）** + **归属校验（W11：system 级限管理员、user 级限 owner+管理员）** → 生成安全随机 128bit 一次性 state（DB 绑定 credentialId + 发起人 + TTL）→ 返回授权 URL（`response_type=code` + `client_id` + `redirect_uri` + `scope` + `state`）。发起时惰性清理 state 表过期行（无后台任务）。
- **回调**（`GET /r/CredentialOAuthApi__oauthCallback?code=..&state=..`，`@Auth(publicAccess=true)` 单一公开端点）：state 校验与一次性消费原子（条件 UPDATE + affected-row 判定，并发双回调恰一个成功；未命中/过期/重放统一拒绝不区分细节防探测）→ 经 provider 引擎内部通道解密 clientSecret → 令牌端点换取 token 集（form：grant_type/code/client_id/client_secret/redirect_uri）→ 只写保留字段回写 → 返回 **`WebContentBean` HTML 跳转页**（200 + meta-refresh/JS location 到配置的结果页；biz 层无 30x 原语，语义等价：浏览器落结果页、token 明文不出现在任何响应体）。
- **惰性刷新**（`getCredential` 出口，SPI 签名零变更）：oauth2 类型 accessToken 临期（now 距 expiresAt < 刷新窗口）→ DB 行级锁（SELECT FOR UPDATE）互斥下锁内双重检查 → refreshToken 刷新 → 回写 → 返回新明文。跨副本/多线程并发取用同一凭证刷新收敛为一次；刷新失败（invalid_grant 等）fail-closed；过期且无 refreshToken → fail-closed（提示重新授权）。非临期取用不持锁可并发。
- **写路径串行化**：惰性刷新与 `saveCredential` 分组写共用同一行锁入口（"刷新 vs 刷新"与"刷新 vs 人工保存"互斥，无双写丢失）。
- **saveCredential 分组写**（oauth2 类型）：人工字段整包替换（未传即删）不变 + 保留字段锁下原样保留；`typeList` 对 oauth2 类型裁剪保留字段（表单只出人工字段）。
- **disabled 全路径拒绝**（显式增量）：oauth2 类型 `status=disabled` 在发起/回调/刷新/取用全路径 fail-closed；非 OAuth 类型取用维持一期语义（仅 delFlag）。
- **引擎不持密钥不变式**：OAuth 引擎类不持有 `CredentialCipher`，clientSecret 读取与 token 回写经 `CredentialProviderImpl` 引擎内部通道（`engineGetDecryptedFields`/`engineUpdateTokenFields`/`engineUpdateInLock`）。

### OAuth 配置项

| 配置项 | 缺省 | 说明 |
|--------|------|------|
| `nop.credential.oauth.callback-base-url` | 无（必配） | 回调端点对外基础地址（部署方配置外部可达地址）；`redirect_uri = {base}/r/CredentialOAuthApi__oauthCallback`，token 交换回传同值。未配置时发起授权 fail-closed |
| `nop.credential.oauth.result-page-url` | 无 | 授权结果前端页 URL（回调跳转目标）；未配置时回调页输出内置静态完成提示 |
| `nop.credential.oauth.state-ttl-seconds` | 600 | state 绑定 TTL（秒） |
| `nop.credential.oauth.refresh-window-seconds` | 300 | 惰性刷新窗口（秒）；类型 oauth2 元数据 `refreshWindowSeconds` 可按类型覆盖 |

> 示例类型：`generic-oauth2.credential-type.xml`（clientId/clientSecret 人工字段 + 端点元数据）。运行时需在部署中引入 raw HTTP client 实现模块（如 `nop-http-client-jdk`）以装配 `IHttpClient`。

## 外部 KMS/HSM 集成（W10，主密钥材料交付模式）

主密钥材料可托管于外部系统（企业合规：材料不落盘/访问审计/独立轮换）。参照实现为
HashiCorp Vault KV v2（`nop-credential-kms-vault` 独立可选模块）；其他厂商（AWS
Secrets Manager/云 KMS/材料可交付 HSM）由使用方按 `ICredentialKeyProvider` SPI 自行扩展。

**集成模式 = 密钥材料交付（material delivery）**：启动期经 `IHttpClient` 调用
Vault KV v2 HTTP API（`GET /v1/{mount}/data/{path}` + `X-Vault-Token` 头，**零
Vault SDK 依赖**）一次性读取全部配置 keyId 的材料并物化为 `AESTextCipher` 集合；
**运行期零托管端调用**（`getKey` 纯内存查找）；材料仅存进程内存。

### 配置项

| 配置项 | 缺省 | 说明 |
|--------|------|------|
| `nop.credential.key-provider` | `local` | 主密钥来源选择：`local`（一期配置文件/环境变量路径）/ `vault` |
| `nop.credential.vault.address` | 无（vault 时必填） | Vault 服务基础地址（如 `http://127.0.0.1:8200`） |
| `nop.credential.vault.token` | 无（vault 时必填） | Vault 访问令牌（`X-Vault-Token` 头） |
| `nop.credential.vault.keys` | 无（vault 时必填） | keyId → secret 路径映射列表，每条 `keyId:{mount}/data/{path}`（路径为 `/v1/` 之后部分，如 `secret/data/credential/keyA`）；secret 内材料字段名固定 `passphrase`（与一期 passphrase 同构） |
| `nop.credential.vault.active-key-id` | 映射首项（未设时回退共享 `nop.credential.active-key-id`） | active key 的 keyId（不得指向迁移残余 key）；两处同设且不一致启动失败（D3-01 归一） |
| `nop.credential.vault.migration-keys` | 空 | 迁移残余列表（`keyId:passphrase`，与一期 master-keys 条目同构）；残余 key 只解不加密、禁含 active key、每次启动 WARN 审计 |
| `nop.credential.vault.request-timeout` | 10000（毫秒） | 启动期材料读取的请求级超时（D3-05）；显式置空/非法取值（<=0）回退缺省——不无限阻塞 |
| `nop.credential.reencrypt-page-size` | 1000 | `reencryptAll` 翻页页大小（确定性排序 + keyset 游标翻页，超批量单次执行全覆盖）；**须 >= 1，< 1 启动 fail-closed 拒绝**（D3-02：0/负值造成空页死循环） |

### 装配模型（同名 bean 覆盖 + 配置门控 + default-bean 守卫）

- KMS 模块 beans 文件 `app-kms-vault.beans.xml`（位于 KMS 模块资源的 `_vfs/nop/credential/beans/` 共享模块命名空间、app- 前缀 → `AppBeanContainerLoader` 自动装载），定义**同名 bean** `nopCredentialKeyProvider` 装配 `VaultCredentialKeyProvider`，`ioc:condition <if-property name="nop.credential.key-provider" value="vault"/>` 精确匹配门控。不新增第二个 `ICredentialKeyProvider` 独立 bean id。
- `key-provider` 未配置/`local`：门控不命中 → `credential-defaults.beans.xml` 的 default bean 生效，`DefaultCredentialKeyProvider` 行为与一期完全一致（模块在场零感知）。
- `key-provider=vault` 且模块部署：门控命中 → Vault bean 注册 → default bean 的 `missing-bean(nopCredentialKeyProvider)` 条件不满足被排除。
- **`key-provider=vault` 而模块未部署 → 启动失败**（`nop.err.credential.key-provider-module-missing`）：NopIoC 的 `ioc:default` 是无条件兜底（与配置项取值无关），模块缺失时 default bean 会照常回退 local——`DefaultCredentialKeyProvider` 的 `@PostConstruct` 守卫对一切非 local 取值显式抛错，结构性堵死"配置了 KMS 实际跑 local"的假安全。

### fail-closed 语义（全部启动期，无本地降级）

托管端不可达（`vault.unreachable`）/认证失败 401/403（`vault.auth-failed`）/配置的
keyId 在托管端缺失 404（`vault.key-not-found`）/材料非法（`vault.material-invalid`：
空 body/缺 `data.data.passphrase`/非字符串/**纯空白**（D5-06 isBlank）/坏 JSON）/
配置矛盾（`vault.master-keys-residual`、`vault.migration-key-active`、
`vault.unknown-active-key`、`vault.active-key-id-conflict` 等）→ bean 初始化抛错 →
**应用拒绝启动**。材料读取带请求级超时（`nop.credential.vault.request-timeout`
缺省 10s，D3-05——托管端挂起不再无限阻塞启动）。任何托管端
故障都不触发本地密钥回退（降级路径 = 攻击路径）。运行期不存在托管端交互；未知
keyId 密文按一期语义 fail-closed（`getKey` 抛错）。

### 本地 → Vault 迁移路径（唯一允许的材料混合窗口）

1. **切换与轮换解耦**：切换 provider 时 active keyId 及其材料不变（旧 passphrase 原样导入 Vault 同 keyId）——滚动发布窗口内新旧副本密钥能力对称。切换完成后再独立执行"Vault 新增 key + 切 active + `reencryptAll`"的轮换步骤。**active-key-id 配置归一（A1-audit D3-01 已修复）**：Vault provider 优先读 `nop.credential.vault.active-key-id`，未设时**回退共享 `nop.credential.active-key-id`**；两处同设且不一致时启动失败（`vault.active-key-id-conflict`，fail-closed）——迁移期共享配置先行调整不再被静默忽略，也排除了"双源漂移静默改变 active key"的陷阱。
2. **迁移窗口开启**：`nop.credential.vault.migration-keys` 声明残余 key（`keyId:passphrase`）——只用于解密旧密文，禁含 active key；每次启动 WARN 审计（列出残余 keyId 提示收尾）。
3. **关窗前置条件**：全部密文的 keyId 属于 Vault key 集合（执行 `reencryptAll`——分页完备性已保证单次执行全覆盖）+ 清空 `nop.credential.master-keys`。
4. **关窗后**：残余列表为空（正常态）；若存量密文仍引用残余 keyId（未完成迁移即清配置的部署失误），`getKey` 按一期"未知 keyId"fail-closed。
5. **反向回退天然可行**：材料交付模式下材料可从 Vault 导出重建 local 配置（keyId 不变 → 密文零迁移）。

### keyId → 材料不变式与配置禁区

同一 keyId 在仍有 `cv1:{keyId}` 密文存续期间材料不可变。Vault 侧轮换必须以
**新 keyId**（新 secret 路径 + 映射新增条目）进行；厂商"同名原地升版本"式轮换
（KV v2 同路径删旧写新、AWS KMS 自动轮换、Vault Transit rotate 类）会使存量
`cv1:{keyId}` 密文全部不可解，属配置禁区。密钥来源不混合：KMS 激活时
`nop.credential.master-keys` 非空 = 启动拒绝（本地材料唯一合法存在形态 = 迁移残余列表）。

## 凭证归属（W11，scope=system|user + ownerId）

凭证分两级归属：**system 级** = 共享凭证（管理员管理，跨消费方/跨系统复用，引用计数不限消费方数）；**user 级** = 个人私有凭证（仅本人消费，防服务进程/他人冒用）。消费方绑定机制（consumerRef）与归属正交，两类凭证一致。

### 字段语义与不可变规则

- `NopCredential.scope`（VARCHAR 20，取值 `system | user`）与 `ownerId`（VARCHAR 50，user 级归属用户 userId 字符串软引用，无外键；system 级恒空）。
- **存量 NULL 视同 system**：新列不设 DDL 默认值，存量行 NULL 由校验/过滤侧视同 system（语义等价）；新写入恒显式值。既有调用不传 scope = system，一期行为零迁移。
- **归属不可变**：update 路径 scope/ownerId 缺省不传 = 保持不变；显式传入与存量不符即拒（`nop.err.credential.ownership-immutable`）。"转让"语义 = 删除旧凭证 + 新建（引用计数删除拦截天然保护消费方）。
- **创建规则**（`saveCredential` 可选输入 `scope`/`ownerId`，空串视同未传）：建 system 级限管理员（无登录态的内部调用按一期行为放行）；建 user 级必填 ownerId——普通用户强制等于当前登录用户（传入他人也被强制为本人），管理员可代建指定他人 owner（审计载体 = 行内 `createdBy ≠ ownerId` 自证 + 平台 ChangeLog，注意 ChangeLog 对插入需 `audit-save` tag）；`scope=user` 无 ownerId / `scope=system` 带 ownerId / scope 域外值均显式拒绝。
- **owner 消亡为显式接受后果**：owner 用户删除/停用后其 user 级凭证明文永久不可取（fail-closed），仅管理员可见（列表）/可删。

### 消费侧归属校验（provider 层 per-method 矩阵）

调用方身份经 `IUserContext.get()`（`ContextProvider` 线程上下文，平台标准身份传播机制）捕获，**`ICredentialProvider` SPI 签名零变更**。校验在解密之前、fail-closed（不返回 null/空）、先序 delFlag（已删凭证报 `ERR_CREDENTIAL_DELETED`，不进入归属判定、不泄露归属）：

| 方法 | scope=system（含 NULL） | scope=user |
|------|------------------------|------------|
| `getCredential` / `getCredentialData`（明文出口） | 放行（一期行为不变；存在授权记录时叠加 RBAC 收紧判定，见下"RBAC 细粒度授权"矩阵） | **owner 唯一**——无用户上下文（后台任务/服务间调用）或 `userId != ownerId` 一律拒绝（`owner-only`，管理员不例外：最小权限，管理面 mask/test 足以完成管理职责） |
| `mask` / `testCredential`（管理面） | 放行 | owner 或管理员（`owner-or-admin`） |

一期消费链（用户请求线程内上下文可达）对 system 级零感知；后台批处理（job/wf）无用户上下文，天然只能消费 system 级。

### BizModel 两层防御（归属为结构性规则，不做可配置数据权限）

- **读类结构性过滤**（`defaultPrepareQuery` 覆盖，经 `invokeDefaultPrepareQuery` 统一作用于 `findPage`/`findList`/`findFirst`/`findCount`）：非管理员登录用户可见「`scope=system` ∨ `scope IS NULL` ∨（`scope=user` ∧ `ownerId=本人`）」（NULL 分支防存量行从普通用户视野消失）；管理员不加过滤；无登录态（内部调用）不过滤（一期行为，provider 层仍守住明文出口）。
- **单条越权归一"不存在"**：`get` 抛 `UnknownEntityException`（与软删除同口径）；`maskList`/`test` 在 BizModel 层先做行级可见性预检，不可见按 `ERR_CREDENTIAL_NOT_FOUND` 归一再调 provider（防 provider 归属错误码泄露归属存在性）。防 credentialId 枚举探测。
- **写路径三态归一**（A1-audit D1-03/D4-07）：`saveCredential` 更新路径的"不存在/越权（system 级非管理员、user 级非 owner）"统一抛 `UnknownEntityException`（与 `get`/`delete` 单条访问先例对称）；`beginOAuthFlow` 发起路径同口径（越权者对目标的一切信息均"不存在"）。创建路径的 `ADMIN_REQUIRED`（无 credentialId 参与，无枚举面）与 `DELETED`/`DISABLED`/`NOT_OAUTH2_TYPE` 状态类错误码保留（显式边界：归一仅越权三态）。`ARG_OWNER_ID` 等 param 不进入对外可达异常。
- **写类分级**：`saveCredential` 修改路径与 `delete`——system 级限管理员（无登录态内部调用按一期行为放行）；user 级限 owner+管理员（无登录态拒绝）。
- **发起 OAuth 授权**（`beginOAuthFlow`，W11 回补）：system 级限管理员、user 级限 owner+管理员（与写类矩阵一致；回调路径无登录态，以 state bearer capability 语义执行——归属校验已在发起时完成）。
- **provider 层为纵深防御**：BizModel 面被绕过时（如直调 provider）第二道 fail-closed。

### 继承动作面收口（六个旁路动作）

| 动作 | 处置 | 原因 |
|------|------|------|
| 标准 `update` | 禁用（`UnsupportedOperationException`，与标准 `save` 同口径） | 绕过加密、归属不可变与写分级 |
| `batchDelete` | 禁用 | 绕过逐条写分级；删除必须逐条走 `delete` |
| `updateByQuery` | 禁用 | 基类 `prepareQuery` 传 null，绕过读类结构性过滤批量改元数据 |
| `deleteByQuery` | 禁用 | 经 `doDeleteMulti → doDelete` 不经过本类 `delete` 的引用计数拦截，破坏一期契约 |
| `copyForNew` | 禁用 | 在 `saveCredential` 之外复制凭证行（含密文） |
| `batchGet` | 改走行级可见性过滤语义 | 收口裁定：过滤而非禁用（UI 批量取数合法场景保留），不可见行剔除。**两语义并存（A1-audit D4-04 澄清）**：请求中的 id 在库中不存在 → 抛错（显式错误请求——调用方数据陈旧/拼写错误应即时报错）；id 存在但调用方不可见 → 从结果静默剔除（防探测归一——与 `get` 的"归一不存在"同口径批量形式，不泄露行存在性）。两者语义正交：前者是调用方错误的显式反馈，后者是对合法批量取数的安全过滤 |

（`batchUpdate`/`batchModify`/`saveOrUpdate` 内部委托已禁用的 `update`/`save`，自动失效。）

### 管理面权限（admin-roles 配置 + action-auth）

- **管理员判定**：配置项 `nop.credential.admin-roles`（CSV，**缺省 `admin,nop-admin`**，与 nop-auth 事实管理员角色名对齐），运行时经 `IUserContext.isUserInAnyRole` 判定；凭证库不依赖 nop-auth 模块（角色名以配置自持）。
- **`reencryptAll` 限管理员**；**`NopCredentialUsage` 查询面（findPage/findList/findFirst/findCount/get/batchGet）限管理员 + 全部标准 mutation 禁用**（A1-audit D4-06 收口：usage 行由消费方经 SPI 登记/注销（(credentialId, consumerRef) 唯一约束幂等），GraphQL 面无任何合法写动作——删除 usage 行会间接解锁被引用计数拦截的凭证删除，必须堵死）；**`NopCredentialOauthState` 查询面限管理员 + 全部标准 mutation 禁用**（A1-audit D2-01/D4-01 收口：state 绑定为引擎内部存储，唯一合法写方是 `NopCredentialOauthStateStore`（dao 直写），GraphQL 面无任何合法写动作）。
- **action-auth 变更**（`nop-credential-web` delta）：`NopCredential:query`/`mutation`（含 `test`）/`saveCredential` 在 delta 层**不设角色限制**（区别于 delete/reencryptAll 的受限资源）——平台 action-auth 为按角色授予语义（未授予角色的 permission 默认拒绝），部署方经角色管理按需授予后，角色分级由 BizModel 运行时判定执行（system=管理员、user=owner+管理员；避免普通用户的 user 级凭证功能在 GraphQL 入口即 403 的空洞实现）；`delete`/`reencryptAll`/`NopCredentialUsage:query`/`mutation`/`NopCredentialAuth:query`/`mutation`/`NopCredentialOauthState:query`/`mutation` 收紧 `roles="admin,nop-admin"`（BizModel 运行时判定为第二层）。**D4-02 双源对齐**：静态 roles 与 `CredentialConfigs.CFG_CREDENTIAL_ADMIN_ROLES` 缺省值（`admin,nop-admin`）一致；静态 roles 层**不跟踪** `admin-roles` 配置变化——部署方调整该配置时须同步调整 delta 或经角色管理面授予，运行时 BizModel 判定始终以配置为准（静态层更严时运行时放行、更宽时运行时拦截，双层取交集语义）。

### usageScope 废弃说明

一期占位列 `USAGE_SCOPE`（"使用范围"）已**废弃**：无默认值、全仓库无取值消费、"使用范围"与"归属"语义错位。归属语义由 `scope`/`ownerId` 新字段承载；列保留不再赋值（ORM 注释已标注 deprecated），物理删除留后续 DDL 治理批量处理。

### DDL 迁移

新建部署由 codegen 产物 `_create_nop-credential.sql` 覆盖（含 SCOPE/OWNER_ID 列，无默认值）；存量部署执行手写增量 `deploy/sql/{mysql,postgresql,oracle}/_add_scope_owner_nop-credential.sql`（循 `_add_tenant_`/`_add_oauth_state_` 先例；scope 不加 DDL 默认值——存量行 NULL 由校验/过滤侧视同 system 的语义等价裁定）。`nop_credential_auth` 表同理由 `_create_` 覆盖 / 增量脚本 `_add_credential_auth_nop-credential.sql`（CREATE TABLE 型）。

## RBAC 细粒度授权（W11 Part B，凭证级"谁可以使用哪个凭证"）

角色 ↔ 单个凭证实例的 **use 授权**（明文取用），弥补平台 RBAC（biz 操作级 + bizObj 行级过滤）在业务数据实例级的空白。**默认开放、可选收紧**：无授权记录的凭证 = 任何服务端调用可取（一期行为完全不变）；配置了授权记录 = 收紧为"命中授权角色（有用户上下文时）"。授权粒度仅单凭证（分组授权无需求实证，deferred）。

### 授权数据模型与幂等契约

- `NopCredentialAuth`：credentialId + roleId（(credentialId, roleId) 唯一约束）+ createTime/createdBy 审计列；**物理删除、无软删除列**（revoke→re-grant 循环靠物理删除与唯一约束天然成立）；授权变更审计走平台 ChangeLog（`tagSet="audit"`）。
- **幂等契约**（对齐 `registerUsage`/`unregisterUsage` 先例）：grant 已存在的 (credentialId, roleId) = no-op 成功（唯一约束兜底并发竞态）；revoke 不存在的记录 = no-op 成功。
- **入参校验**：grant 拒绝 scope≠system 的凭证（user 级不叠加角色授权——owner 唯一明文出口，不产生永不生效的授权记录，`auth-not-system-scope`）；凭证不存在/已删归一 NOT_FOUND/DELETED；roleId 仅非空校验（**不做存在性校验**——跨模块校验破坏 nop-credential 对 nop-auth 的依赖边界；死 roleId 求交永不命中，无害，管理面原样展示）。

### 消费侧判定矩阵（provider 明文出口，解密之前）

前置（先序，任意 scope/上下文/记录）：delFlag fail-closed（一期语义）；已删凭证一律拒绝，不进入下表。授权检查串联于 `getCredential`/`getCredentialData`（归属校验之后、解密之前）；`mask`/`testCredential` 管理面动作与 OAuth 发起写动作**不做凭证级授权**（分属 §5.3 管理面/写分级矩阵）。

| # | scope | 用户上下文 | 授权记录 | 结果 |
|---|---|---|---|---|
| 2 | user | 非 owner（含 admin）/ 无上下文 | — | 拒绝（归属矩阵，`owner-only`；授权记录不参与 user 级判定） |
| 3 | user | owner | — | 放行（归属即授权） |
| 4 | system（含 NULL） | 任意 | 无 | **放行（一期行为不变——增量兼容基线）** |
| 5 | system | 无用户上下文（后台任务/服务间） | 有 | 放行（**服务级信任**：SPI 是服务端边界，收紧针对"人"的冒用，不针对服务代码） |
| 6 | system | 有 | 有 | 用户角色 ∩ 授权角色 ≠ ∅ → 放行；否则拒绝（`role-not-granted`，含 credentialId/roleIds 参数；**admin 不自动豁免**——管理员的管理权不等于取用权，需要时 grant 自己的角色） |

- **角色快照时效**：用户角色集合为登录时快照（nop-auth 填充会话上下文，含一级复合角色展开）；授权记录（grant/revoke）**即时生效**（校验为 DB 点查，唯一键最左前缀，无 join/远程调用）；用户侧角色回收对存量会话的生效时点 = 该用户下次登录。roleId 按**字面**参与求交（不展开子角色）。
- **信任边界（矩阵第 5 行推论）**：判定输入（`IUserContext` 有无）由消费链路决定——以用户名义发起的消费链必须保持上下文传播，链路丢失上下文即事实绕过第 6 行收紧（退化为第 5 行放行）；此为设计接受的信任边界（服务端代码不在防范对象内），上下文丢失告警审计归 A1-audit 评估。
- **引擎内部通道豁免**（impl 裁定）：`engineGetDecryptedFields`/`engineUpdateTokenFields`/`engineUpdateInLock` 不查授权记录——明文不外泄（`beginOAuthFlow` 只返回授权 URL、publicAccess 回调返回跳转页不含 token、`saveCredential` 有写分级门控）、入口动作已被归属/管理员判定门控、与第 5 行服务级信任边界一致。两个用户上下文可达调用点（`beginOAuthFlow`→`OAuthFlowService`、`saveCredential` oauth2 分组写）为裁定显式覆盖对象（可达性事实，非"GraphQL 不可达"）。`getCredential` 内惰性刷新发生在检查通过之后。
- **无记录路径性能语义**：与一期等价（单次按 credentialId 的索引查询）；有记录路径增加一次点查。撤销全部记录 → 回到第 4 行开放态（默认开放的对称性）。

### 授权管理面（admin-only + 幂等）

- **载体 = 独立 `NopCredentialAuthBizModel`**（impl 裁定：对齐 usage 查询面先例，避免膨胀 `NopCredentialBizModel`）；查询动作（findPage/findList/findFirst/findCount/get/batchGet）运行时 admin-only（无登录态同样拒绝）；action-auth delta `NopCredentialAuth:query`/`mutation` 收紧 `roles="admin"`（双层防御）。
- **`grant(credentialId, roleId)` / `revoke(credentialId, roleId)`**：admin-only + 幂等契约（见上）；revoke 为授权行唯一删除通道。
- **标准 mutation 旁路收口**（对齐 Part A 六动作同口径）：`save`/`update`/`delete`/`batchDelete`/`updateByQuery`/`deleteByQuery`/`copyForNew` 全部禁用（抛 `UnsupportedOperationException`；`update`/`updateByQuery` 另因授权行不可变——grant=insert、revoke=物理删除，无更新路径）。
- **级联清理**：凭证删除时其授权行**物理级联清理**（`auths` 关系 `cascadeDelete="true"`；实现于父侧删除回调内 dao 显式清理——`cascade-delete` biz 级联 tag 的平台机制经子 BizModel delete 动作逐行调用，与子 delete 禁用互斥）；删除被 usage 引用计数拦截时授权行与凭证同存属正常语义（授权与引用正交）。
- **收紧运维语义**：对已登记引用（`NopCredentialUsage`）的凭证做授权收紧会切断对应消费链路（"已绑定但不可用"）；凭证详情页同时可见授权列表与引用列表供收紧前核对（不自动告警）。
- **管理员判定复用** `nop.credential.admin-roles`（缺省 `admin,nop-admin`）——与归属分级/usage 查询面同一配置口径；凭证库不依赖 nop-auth 模块（角色经 `IUserContext` 会话上下文传播，roleId 字符串软引用）。

### Web 授权编辑（凭证详情页最小面）

`NopCredential.view.xml` 详情页（view）afterForm 挂"取用授权（RBAC 收紧）"子表：`@query:NopCredentialAuth__findPage?filter_credentialId=...` 列表 + revoke 撤销（确认文案提示收紧语义）+ grant 新增对话框（角色选择器 source `@query:NopAuthRole__findList/value:roleId,label:roleName` + 手工输入兜底）。**共部署语义**：standalone `nop-credential-app` 无 NopAuthRole GraphQL 服务时选择器为空源、手工输入 roleId 为主通道（后端仅校验非空）；详情页同屏展示 usage 引用子表（收紧前核对）。

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-credential-api` | SPI/接口/DTO（零业务依赖：`ICredentialProvider`、`ICredentialMigrationSupport`（W16 迁移支持）、`CredentialData`（含 W16 可选 `typeName`）、`CredentialLookup`、`MaskedCredential`、`TestResult`、`CredentialType`（含 OAuth 元数据与保留字段名契约）） |
| `nop-credential-dao` | ORM 实体与 DAO |
| `nop-credential-meta` | xmeta 定义（`data` 列 `published=false` 明文边界） |
| `nop-credential-service` | `ICredentialProvider` 实现（唯一明文出口）+ `NopCredentialBizModel` + 凭证类型注册表 + `CredentialCipher` + OAuth 流程引擎（`service.oauth`：`OAuthFlowService`/`OAuthTokenClient`/`NopCredentialOauthStateStore`/`CredentialOAuthApiBizModel`） |
| `nop-credential-kms-vault` | 外部 KMS 参照实现（可选部署）：`VaultCredentialKeyProvider`（Vault KV v2 材料交付，compile 依赖仅 api + `nop-http-api`，零 Vault SDK）；运行时需引入 raw HTTP client 模块（如 `nop-http-client-jdk`） |
| `nop-credential-web` | AMIS 管理页面（动态表单） |

## 加密方案

- **复用**：`AESTextCipher`（`nop-commons`）作为每个密钥的单钥加密器——AES/GCM/NoPadding、随机 12B IV、PBKDF2-SHA256(65536) 派生、`v1:` 输出。
- **凭证库层包装**：`CredentialCipher`（`nop-credential-service`）输出 `cv1:{keyId}:{v1密文}`，解析 keyId 后委托对应 `AESTextCipher` 解内层 `v1:` 密文。
- **主密钥来源**：环境变量/独立配置文件（不放入 application.yaml 明文），如 `NOP_CREDENTIAL_MASTER_KEYS` 或 `credential-keys.yaml`，格式为 `keyId:passphrase` 列表；或外部 KMS（`nop.credential.key-provider`，见上"外部 KMS/HSM 集成"章节）。
- **keyId 约束**：只允许 `[A-Za-z0-9_-]`（`cv1:` 按冒号 split 无歧义的前提）。**单源定义**在 api 模块 `ICredentialKeyProvider.KEY_ID_PATTERN`（D3-04），service/KMS 各实现统一引用。
- **passphrase 校验**：master-keys 与迁移残余条目的 passphrase 纯空白拒绝（isBlank，D5-06——空白材料形同弱密钥；含空格的非空白 passphrase 合法）；`nop.credential.key-provider` 取值 trim + 大小写归一后比较（D3-03，配置噪声准确归因）。
- **密文格式防御**：`CredentialCipher.decrypt` 强制内层 `v1:` 版本前缀（D5-05——cv1 包装的 legacy 裸载荷拒绝，不落弱路径）；格式错误异常的 `ciphertext` param 截断为 16 字符前缀 + 总长标注（D1-04——密文整串不进日志）。
- **轮换**：新增 key 后 active key 指向新 key，新写入用新 key；旧密文按 `cv1:` 中的 keyId 仍可用旧 key 解密；`reencryptAll` 批量重加密（确定性排序 orderBy credentialId + keyset 游标翻页，超批量单次执行全覆盖；页大小 `nop.credential.reencrypt-page-size` 缺省 1000、**下限 1 fail-closed**；仅 admin）。**非 cv1 前缀/空 data 行不迁移但计数上报**（D5-03：WARN 汇总含行数——关窗前计数 > 0 表示存在 legacy 残留，需先治理再退役旧 key）。执行语义（A1-audit D5-01/D5-02 标注）：GraphQL mutation 路径在单事务内整体执行（非逐条独立提交），幂等可重跑——单条失败全量回滚后重跑，无部分提交中间态，大表迁移注意长事务锁窗口（可分批执行）；仅处理 `delFlag=0` 行，**软删除行的旧密文不迁移**——旧 key 退役前需先物理清理软删行或接受其不可恢复。

## 消费 SPI（ICredentialProvider）

消费方（nop-ai、nop-integration、nop-metadata 等）通过 `ICredentialProvider`（接口在 api 层，实现在 service 层）取用解密后的凭证：

| 方法 | 语义 |
|------|------|
| `getCredential(credentialId)` | 返回全部解密字段（明文，仅服务端 Java 可调；user 级 owner 唯一、system 级可被授权记录收紧，见"凭证归属"/"RBAC 细粒度授权"矩阵） |
| `getCredentialData(credentialId, field)` | 取单个字段（如 apiKey；同上判定矩阵） |
| `testCredential(credentialId)` | 连通性测试（user 级 owner+管理员） |
| `mask(credentialId)` | 脱敏视图（REST/GraphQL 层用；user 级 owner+管理员） |
| `registerUsage(credentialId, consumerRef)` | 消费方绑定凭证时登记引用（幂等；**前置校验**（D6-03）：凭证不存在/已软删 → 即时 fail-closed 抛 `not-found`/`deleted`（与读路径同码）——配错 credentialId 在登记时暴露，不产生"已绑定但不可用"的悬空引用） |
| `unregisterUsage(credentialId, consumerRef)` | 解绑/替换时解除引用 |

> 所有 getter 方法对不存在或已软删除的凭证 **fail-closed**（抛 `NopException`，先序 delFlag），永不返回 null；user 级凭证归属不符同样 fail-closed（见"凭证归属"章节 per-method 矩阵）。`getCredential`/`getCredentialData` 不暴露为任何 BizModel/GraphQL 方法。

## 凭证类型注册

经平台标准 register-model 机制声明：

- **元模型**：`credential-type.xdef`（`nop-kernel/nop-xdefs/.../schema/credential/credential-type.xdef`）
- **实例文件**：`*.credential-type.xml` 放在 `_vfs/nop/credential/types/`（如 `openai-api-key.credential-type.xml`、`generic-secret.credential-type.xml`）
- **加载**：经 `ResourceComponentManager` 加载，自动获得 x:extends delta 定制、XML/JSON 格式切换、模型校验
- **字段类型枚举**：`string`/`password`/`number`/`select`/`boolean`/`textarea`（password 类型打码回显）

## 管理 API（NopCredentialBizModel）

GraphQL/REST 管理 CRUD，明文结构性不可达：

| 方法 | 语义 |
|------|------|
| `saveCredential(typeName, name, fields, id?, scope?, ownerId?)` | 保存凭证（**明文唯一入口**，加密后落库；oauth2 类型分组写 + 保留字段拒绝；可选归属输入与分级/不可变规则见上"凭证归属"章节） |
| `get` / `findPage` / `findList` / `findFirst` / `batchGet` | 查询（返回的 `data` 恒为 null——全部查询动作 BizModel 层强制逐行置空，D1-01；行级归属过滤 + 单条越权归一"不存在"；`batchGet` 语义见下注） |
| `maskList(ids)` | 返回脱敏视图（敏感字段替换为 ****；先做可见性预检，不可见归一 NOT_FOUND） |
| `typeList()` | 返回类型字段 schema（Web 动态表单用；oauth2 类型裁剪保留字段；与行过滤无关） |
| `test(id)` | 触发连通性测试（先做可见性预检，不可见归一 NOT_FOUND） |
| `reencryptAll()` | 主密钥轮换后批量重加密（仅 admin） |
| `delete(id)` | 删除（软删除；写分级 + 前置检查 `NopCredentialUsage` 引用计数，>0 拒绝） |

禁用动作（抛 `UnsupportedOperationException`）：标准 `save`/`update`/`batchDelete`/`updateByQuery`/`deleteByQuery`/`copyForNew`（收口原因见上"凭证归属"章节六动作表）；`batchGet` 走可见性过滤语义。`NopCredentialUsage` 面 7 个标准 mutation 同口径禁用（D4-06，见上"管理面权限"）。

授权管理 API 面（`NopCredentialAuthBizModel`，W11 Part B，全部限管理员 + 幂等契约见上"RBAC 细粒度授权"章节）：`grant(credentialId, roleId)` / `revoke(credentialId, roleId)` mutation；查询动作（findPage/findList/findFirst/findCount/get/batchGet）同 admin-only（`NopCredentialAuth` 实体管理信息）；标准 `save`/`update`/`delete`/`batchDelete`/`updateByQuery`/`deleteByQuery`/`copyForNew` 禁用（授权行经 grant/revoke 唯一通道维护）。

OAuth 流程 API 面（`CredentialOAuthApiBizModel`）：`beginOAuthFlow(credentialId)` mutation（登录态，返回授权 URL）；`oauthCallback(code, state)` query（publicAccess 单一公开回调，返回 HTML 跳转页）。

## 明文边界（结构性强制，非约定）

1. `ICredentialProvider` 接口 + DTO 在 **api 层**，实现类 `CredentialProviderImpl` 在 **service 层**。
2. `getCredential`/`getCredentialData` **不暴露为任何 BizModel/GraphQL 方法**。
3. xmeta 中 `data` 列 `published="false"`（不对外生成 GraphQL 字段）。
4. `NopCredentialBizModel` 全部查询动作（`get`/`findPage`/`findList`/`findFirst`/`batchGet`）返回前强制逐行驱逐 + 置空 `data`（A1-audit D1-01 补齐继承查询动作面——第二层防御，即使 xmeta 边界被绕过也无密文）。
5. 展示层用 `maskList` 输出脱敏值。
6. 消费方获取明文 = 依赖 `nop-credential-service` 的 bean（`@Inject ICredentialProvider`），这是"服务端代码"的显式声明。

## 关键配置

| 配置项 | 说明 |
|--------|------|
| 主密钥 | 环境变量 `NOP_CREDENTIAL_MASTER_KEYS` 或 `credential-keys.yaml`（`keyId:passphrase`，不放入 application.yaml 明文） |
| 密钥来源选择 | `nop.credential.key-provider`（`local` 缺省 / `vault`） |
| Vault KMS | `nop.credential.vault.*`（见上"外部 KMS/HSM 集成"配置项表） |
| 重加密分页 | `nop.credential.reencrypt-page-size`（缺省 1000） |
| OAuth 引擎 | `nop.credential.oauth.*`（见上"OAuth 配置项"表） |
| 凭证库管理员角色 | `nop.credential.admin-roles`（CSV，缺省 `admin,nop-admin`；归属分级/usage 查询面/reencryptAll/RBAC 授权管理面（grant/revoke/查询）的 admin 判定依据） |

## 源码锚点

| 组件 | 路径 |
|------|------|
| 消费 SPI 接口 | `nop-credential/nop-credential-api/src/main/java/io/nop/credential/api/ICredentialProvider.java` |
| 迁移支持 SPI（W16） | `nop-credential/nop-credential-api/src/main/java/io/nop/credential/api/ICredentialMigrationSupport.java`（实现 `CredentialMigrationSupportImpl`，bean 注册于 `credential-defaults.beans.xml`） |
| 共享解析支持（W16） | `nop-integration/nop-integration-api/src/main/java/io/nop/integration/api/credential/CredentialResolutionSupport.java` |
| 唯一明文出口实现 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java` |
| OAuth 流程引擎 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/OAuthFlowService.java` |
| OAuth 令牌端点客户端 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/OAuthTokenClient.java` |
| OAuth 回调 API 面 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/CredentialOAuthApiBizModel.java` |
| cv1 加解密 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/CredentialCipher.java` |
| 主密钥 SPI | `nop-credential/nop-credential-api/src/main/java/io/nop/credential/api/crypto/ICredentialKeyProvider.java` |
| 主密钥缺省实现（含 key-provider 守卫） | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/DefaultCredentialKeyProvider.java` |
| Vault KMS 参照实现 | `nop-credential/nop-credential-kms-vault/src/main/java/io/nop/credential/kms/vault/VaultCredentialKeyProvider.java`（装配 beans 文件：KMS 模块资源 `_vfs/nop/credential/beans/` 下的 `app-kms-vault.beans.xml`） |
| 管理 BizModel | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java` |
| 归属判定工具 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialOwnership.java`（scope 语义/admin 判定/可见性/写分级） |
| usage 查询面 BizModel | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialUsageBizModel.java`（admin 限定 + 七标准 mutation 禁用（D4-06）） |
| RBAC 授权管理面 BizModel | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialAuthBizModel.java`（grant/revoke 幂等 + admin-only 查询面 + 七旁路禁用；provider 侧判定矩阵在 `CredentialProviderImpl.assertRoleAuthForPlaintext`） |
| ORM 模型 | `nop-credential/model/nop-credential.orm.xml` |
| 类型元模型 | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/credential/credential-type.xdef` |
| 底层加密原语 | `nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java`（复用，不修改） |

## 敏感操作标注（@MfaRequired，C1b 已落地）

四个敏感管理动作已标注操作级 MFA（A1-audit §二#4 缩窄裁定，C1b 落地）：

| 动作 | 裁定理由 |
|------|----------|
| `NopCredential__reencryptAll` | 全量敏感（触及全部密文） |
| `NopCredential__delete` | 数据级不可逆删除 |
| `NopCredentialAuth__grant` | 权限变更（扩大凭证取用面） |
| `NopCredentialAuth__revoke` | 权限变更（与 grant 对称拦截，防劫持会话内权限操纵） |

```java
@BizMutation
@io.nop.auth.api.mfa.MfaRequired   // 不得与 @BizSubscription / @Auth(publicAccess=true) 同用（构建期报错）
public int reencryptAll() { ... }
```

- **生效前置**（三者任一不满足时四动作行为与未标注完全一致）：部署侧 `nop.auth.operation-mfa.enabled=true` + 操作用户 MFA 已启用 + checker SPI bean 装配（`IOperationMfaChecker` 实现由 nop-auth-service 提供）。验证流与一次性票语义见 `nop-auth.md` 的"操作级 MFA"章节。
- **零介入分层措辞**（对齐 `nop-auth.md` 受限会话语义）：未部署 nop-auth-service（无 checker bean）时 executor 零介入；部署后操作级挑战仍受 `nop.auth.operation-mfa.enabled` 门控，但**受限会话拦截不受该开关门控**——受限会话（角色强制策略判定）内非白名单 mutation（含本模块未标注动作如 `saveCredential`）仍会被拒（`ERR_AUTH_MFA_RESTRICTED_SESSION`），这是独立于操作级 MFA 的登录期持有约束层。
- **缩窄裁定（不标注）**：`saveCredential`（高频用户操作，强制 MFA 损害 UX；明文写入口已有写分级 + 归属校验两层防御）、`beginOAuthFlow`（state 一次性绑定已防劫持，动作仅返回 URL 不泄密）。
- **依赖边**：`nop-credential-service → nop-biz-auth-api` 为 API-only 边（该模块仅依赖 `nop-api-core`），经 `nop-biz → nop-graphql-core` 传递本已 compile 可达，pom 声明系显式化既有传递边（Maven 依赖卫生），不引入 nop-auth-service 运行时耦合。

## 深度迁移（W16：nop-integration / nop-metadata 消费方接入）

深度迁移 = 消费方以 `credentialId` 引用凭证库、发送期/建连期经 `ICredentialProvider` 惰性解析（替代配置文件/JSON 明文）。首批交付 = 共享解析支持 + SMS 家族（tencent/yunpian）+ metadata 数据源，扩展批次（Email×2/Feishu/OSS/SFTP）登记于 roadmap `W16-impl-ext`。

### 横切契约（两侧统一）

- **优先级链**：`credentialId` 空/空白 → 既有静态值现状路径（零回归）；非空 → 凭证字段集**整组**取凭证库，同名静态值忽略（不逐字段混搭）。
- **强 fail-closed 不静默回退**：credentialId 已配置但 provider 未装配（部署不一致）/凭证缺失/软删/解密失败/必填字段空/typeName 错型/转换失败 → 中止本次发送/建连，**不回退静态值**（对齐 nop-ai `IAiModelCredentialResolver` 先例）。
- **consumerRef 引用计数**：`integration:<channelType>`（发送器 bean 初始化幂等登记，catch-all WARN 不阻断启动）与 `metadata:NopMetaDataSource:<dataSourceId>`（bind/迁移登记、unbind/换绑/行删除解除）。
- **归属一律 system 级**；RBAC 走 provider 出口既有判定矩阵零新增语义；拒绝全局明文降级开关。

### SPI 增量（W16，`nop-credential-api` additive，六方法签名冻结不变）

- `CredentialData.typeName`（可选属性，`CredentialProviderImpl.getCredential` 从凭实行填充）——消费方家族错型校验的精确依据（如 yunpian-sms 凭证误用于 tencent-sms 渠道直接拒绝，不靠字段缺失间接失败）。
- `ICredentialMigrationSupport`（迁移支持 SPI，bean `nopCredentialMigrationSupport` 落 service 并经 `credential-defaults.beans.xml` 注册）：`findCredentialByName(typeName, name)`（活跃行、确定性取首条）/ `findCredentialIdByConsumerRef(consumerRef)`（迁移反查**主源**——usage 唯一约束承载身份；命中软删返回 `deleted=true` 供调用方计入失败清单人工处置）/ `createCredential(typeName, name, fields)`（复用 `saveCredential` 语义：加密 + scope=system + 分级审计）。

### 共享解析支持（`nop-integration-api`）

`CredentialResolutionSupport`（静态工具，公共语义单点——厂商模块零复制）：`isConfigured`（空串/空白视同缺失）/ `resolveGroup(provider, credentialId, allowedTypeNames)`（部署不一致 + provider 侧异常包装（cause 保留）+ typeName 家族允许集校验）/ `requireString`/`optionalString`/`requireInteger`（字符串归一 + 转换失败 fail-closed）。错误码在 `IntegrationErrors`（`nop.err.integration.credential-*` 五码）。`nop-integration-api` 对 `nop-credential-api` 为 api→api compile 依赖（无 service/dao 传递）。

### SMS 家族接线（首批）

`TencentSmsSender`/`YunpianSmsSender`：可选 `credentialId` bean 属性 + `@Nullable ICredentialProvider` setter 注入 + 发送期惰性解析（逐次发送家族——轮换/禁用下次发送即生效；sendMessage 与 sendMultiMessage 双构造点全覆盖）+ `@PostConstruct` 幂等 `registerUsage`（catch-all WARN——含 D6-03 前置校验抛错，配错 credentialId 不阻断启动）。凭证类型 `tencent-sms`（appId 必填/appKey 必填 sensitive/sign 可空）、`yunpian-sms`（apiKey 必填 sensitive）。

### integration 迁移 runbook（integration 侧唯一迁移机制）

integration 无 DB 行、无迁移工具——迁移 = 运维顺序操作：

1. **创建凭证**：经 `NopCredential__saveCredential`（如 `tencent-sms` 类型，字段 appId/appKey/sign）。
2. **设 credentialId 属性**（并存窗口）：消费方应用 beans.xml 给发送器 bean 增加 `<property name="credentialId" value="..."/>`（或 `@cfg:` 引用）；静态密钥属性**暂留**（回滚安全网）。重启后发送路径整组取凭证库（静态值被忽略）；`registerUsage("integration:tencent-sms")` 自动登记。
3. **验证发送**：经真实业务链路（如登录验证码）或渠道测试发送确认成功。
4. **清除静态密钥**：从 beans.xml 删除 appId/appKey/sign 静态属性（配置文件治理范畴）。
5. **回滚 = 引用级**：删除 `credentialId` 属性即回退静态值路径；**明文清除后回滚需显式再录入**（杜绝从死值复活明文）。
- consumerRef 清单（人工清理参照）：`integration:tencent-sms` / `integration:yunpian-sms`（扩展批次将增加 email/feishu/oss/sftp token）。渠道下线后 usage 行由管理面人工清理（陈旧引用仅影响该凭证删除拦截提示）。
- 部署前置：消费 app 的 beans 聚合需引入 `credential-defaults.beans.xml`（app-service.beans.xml 聚合装载，nop-ai-service 同型先例）；未部署凭证库时 credentialId 必须留空（配置了即 fail-closed 部署不一致）。

### metadata 数据源迁移（首批）

见 `nop-metadata.md`"数据源凭证（credentialId 深度迁移）"节：`connectionConfig` JSON 内 `credentialId` 键 + `MetaDataSourceConnectionProcessor.buildDataSource` 单点解析（14 消费点零改动）+ `bindCredential`/`unbindCredential`/`migrateDataSourcesCredential` 管理动作对。凭证类型 `jdbc-datasource`（username 必填/password 可空 sensitive）。

## 相关文档

- `../02-core-guides/ioc-and-config.md`（`@sec:` 配置加密，与凭证库互补）
- `nop-ai.md`（`NopAiModel.credentialId` 迁移路径）
- `../reusable-modules-overview.md`
