# nop-credential — 加密凭证库模块

## 功能概览

为平台提供**行级敏感数据**（DB 中动态写入的密钥/连接参数）的类型化加密存储与统一解密入口，弥补 `@sec:` 配置加密只能处理"配置文件静态值"的不足。

- **类型化凭证**：声明式类型注册（`*.credential-type.xml`），定义字段结构（apiKey/secret/用户名密码等）
- **OAuth 流程引擎**（W9）：`authType=oauth2` 类型的授权码闭环（发起 → 单一公开回调 → token 加密回写）+ 取用时惰性刷新（DB 行级锁跨副本互斥）
- **加密存储**：`cv1:{keyId}:{v1密文}` 版本化密文格式，复用 `nop-commons` 的 `AESTextCipher`（AES-256-GCM + PBKDF2-SHA256）
- **多密钥与轮换**：`ICredentialKeyProvider` 支持多 key 并存，`reencryptAll` 批量重加密
- **唯一解密点**：`ICredentialProvider` 实现位于 service 层，明文不跨出服务进程
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
| NopCredential | `nop_credential` | 加密凭证实例（data 列存 `cv1:` 密文 JSON） |
| NopCredentialUsage | `nop_credential_usage` | 凭证引用登记（credentialId + consumerRef 唯一约束） |
| NopCredentialOauthState | `nop_credential_oauth_state` | OAuth state 绑定（一次性消费 + TTL 过期 + 惰性清理，引擎内部存储） |

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

- **发起**（`CredentialOAuthApi__beginOAuthFlow` mutation，登录态）：校验 实例存在/未删/未禁用/类型 oauth2/clientSecret 已录入 → 生成安全随机 128bit 一次性 state（DB 绑定 credentialId + 发起人 + TTL）→ 返回授权 URL（`response_type=code` + `client_id` + `redirect_uri` + `scope` + `state`）。发起时惰性清理 state 表过期行（无后台任务）。
- **回调**（`GET /r/CredentialOAuthApi__oauthCallback?code=..&state=..`，`@Auth(publicAccess=true)` 单一公开端点）：state 校验与一次性消费原子（条件 UPDATE + affected-row 判定，并发双回调恰一个成功；未命中/过期/重放统一拒绝不区分细节防探测）→ 经 provider 引擎内部通道解密 clientSecret → 令牌端点换取 token 集（form：grant_type/code/client_id/client_secret/redirect_uri）→ 只写保留字段回写 → 返回 **`WebContentBean` HTML 跳转页**（200 + meta-refresh/JS location 到配置的结果页；biz 层无 30x 原语，语义等价：浏览器落结果页、token 明文不出现在任何响应体）。
- **惰性刷新**（`getCredential` 出口，SPI 签名零变更）：oauth2 类型 accessToken 临期（now 距 expiresAt < 刷新窗口）→ DB 行级锁（SELECT FOR UPDATE）互斥下锁内双重检查 → refreshToken 刷新 → 回写 → 返回新明文。跨副本/多线程并发取用同一凭证刷新收敛为一次；刷新失败（invalid_grant 等）fail-closed；过期且无 refreshToken → fail-closed（提示重新授权）。非临期取用不持锁可并发。
- **写路径串行化**：惰性刷新与 `saveCredential` 分组写共用同一行锁入口（"刷新 vs 刷新"与"刷新 vs 人工保存"互斥，无双写丢失）。
- **saveCredential 分组写**（oauth2 类型）：人工字段整包替换（未传即删）不变 + 保留字段锁下原样保留；`typeList` 对 oauth2 类型裁剪保留字段（表单只出人工字段）。
- **disabled 全路径拒绝**（显式增量）：oauth2 类型 `status=disabled` 在发起/回调/刷新/取用全路径 fail-closed；非 OAuth 类型取用维持一期语义（仅 delFlag）。
- **唯一解密点不变式**：OAuth 引擎类不持有 `CredentialCipher`，clientSecret 读取与 token 回写经 `CredentialProviderImpl` 引擎内部通道（`engineGetDecryptedFields`/`engineUpdateTokenFields`/`engineUpdateInLock`）。

### OAuth 配置项

| 配置项 | 缺省 | 说明 |
|--------|------|------|
| `nop.credential.oauth.callback-base-url` | 无（必配） | 回调端点对外基础地址（部署方配置外部可达地址）；`redirect_uri = {base}/r/CredentialOAuthApi__oauthCallback`，token 交换回传同值。未配置时发起授权 fail-closed |
| `nop.credential.oauth.result-page-url` | 无 | 授权结果前端页 URL（回调跳转目标）；未配置时回调页输出内置静态完成提示 |
| `nop.credential.oauth.state-ttl-seconds` | 600 | state 绑定 TTL（秒） |
| `nop.credential.oauth.refresh-window-seconds` | 300 | 惰性刷新窗口（秒）；类型 oauth2 元数据 `refreshWindowSeconds` 可按类型覆盖 |

> 示例类型：`generic-oauth2.credential-type.xml`（clientId/clientSecret 人工字段 + 端点元数据）。运行时需在部署中引入 raw HTTP client 实现模块（如 `nop-http-client-jdk`）以装配 `IHttpClient`。

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-credential-api` | SPI/接口/DTO（零业务依赖：`ICredentialProvider`、`CredentialData`、`MaskedCredential`、`TestResult`、`CredentialType`（含 OAuth 元数据与保留字段名契约）） |
| `nop-credential-dao` | ORM 实体与 DAO |
| `nop-credential-meta` | xmeta 定义（`data` 列 `published=false` 明文边界） |
| `nop-credential-service` | `ICredentialProvider` 实现（唯一解密点）+ `NopCredentialBizModel` + 凭证类型注册表 + `CredentialCipher` + OAuth 流程引擎（`service.oauth`：`OAuthFlowService`/`OAuthTokenClient`/`NopCredentialOauthStateStore`/`CredentialOAuthApiBizModel`） |
| `nop-credential-web` | AMIS 管理页面（动态表单） |

## 加密方案

- **复用**：`AESTextCipher`（`nop-commons`）作为每个密钥的单钥加密器——AES/GCM/NoPadding、随机 12B IV、PBKDF2-SHA256(65536) 派生、`v1:` 输出。
- **凭证库层包装**：`CredentialCipher`（`nop-credential-service`）输出 `cv1:{keyId}:{v1密文}`，解析 keyId 后委托对应 `AESTextCipher` 解内层 `v1:` 密文。
- **主密钥来源**：环境变量/独立配置文件（不放入 application.yaml 明文），如 `NOP_CREDENTIAL_MASTER_KEYS` 或 `credential-keys.yaml`，格式为 `keyId:passphrase` 列表。
- **keyId 约束**：只允许 `[A-Za-z0-9_-]`（`cv1:` 按冒号 split 无歧义的前提）。
- **轮换**：新增 key 后 active key 指向新 key，新写入用新 key；旧密文按 `cv1:` 中的 keyId 仍可用旧 key 解密；`reencryptAll` 批量重加密（断点续跑，仅 admin）。

## 消费 SPI（ICredentialProvider）

消费方（nop-ai、nop-integration、nop-metadata 等）通过 `ICredentialProvider`（接口在 api 层，实现在 service 层）取用解密后的凭证：

| 方法 | 语义 |
|------|------|
| `getCredential(credentialId)` | 返回全部解密字段（明文，仅服务端 Java 可调） |
| `getCredentialData(credentialId, field)` | 取单个字段（如 apiKey） |
| `testCredential(credentialId)` | 连通性测试 |
| `mask(credentialId)` | 脱敏视图（REST/GraphQL 层用） |
| `registerUsage(credentialId, consumerRef)` | 消费方绑定凭证时登记引用（幂等） |
| `unregisterUsage(credentialId, consumerRef)` | 解绑/替换时解除引用 |

> 所有 getter 方法对不存在或已软删除的凭证 **fail-closed**（抛 `NopException`），永不返回 null。`getCredential`/`getCredentialData` 不暴露为任何 BizModel/GraphQL 方法。

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
| `saveCredential(typeName, name, fields, ...)` | 保存凭证（**明文唯一入口**，加密后落库；oauth2 类型分组写 + 保留字段拒绝） |
| `get` / `findPage` | 查询（返回的 `data` 恒为 null——BizModel 层强制置空） |
| `maskList(ids)` | 返回脱敏视图（敏感字段替换为 ****） |
| `typeList()` | 返回类型字段 schema（Web 动态表单用；oauth2 类型裁剪保留字段） |
| `test(id)` | 触发连通性测试 |
| `reencryptAll()` | 主密钥轮换后批量重加密（仅 admin） |
| `delete(id)` | 删除（软删除；前置检查 `NopCredentialUsage` 引用计数，>0 拒绝） |

OAuth 流程 API 面（`CredentialOAuthApiBizModel`）：`beginOAuthFlow(credentialId)` mutation（登录态，返回授权 URL）；`oauthCallback(code, state)` query（publicAccess 单一公开回调，返回 HTML 跳转页）。

## 明文边界（结构性强制，非约定）

1. `ICredentialProvider` 接口 + DTO 在 **api 层**，实现类 `CredentialProviderImpl` 在 **service 层**。
2. `getCredential`/`getCredentialData` **不暴露为任何 BizModel/GraphQL 方法**。
3. xmeta 中 `data` 列 `published="false"`（不对外生成 GraphQL 字段）。
4. `NopCredentialBizModel.get`/`findPage` 返回前强制置空 `data`。
5. 展示层用 `maskList` 输出脱敏值。
6. 消费方获取明文 = 依赖 `nop-credential-service` 的 bean（`@Inject ICredentialProvider`），这是"服务端代码"的显式声明。

## 关键配置

| 配置项 | 说明 |
|--------|------|
| 主密钥 | 环境变量 `NOP_CREDENTIAL_MASTER_KEYS` 或 `credential-keys.yaml`（`keyId:passphrase`，不放入 application.yaml 明文） |
| OAuth 引擎 | `nop.credential.oauth.*`（见上"OAuth 配置项"表） |

## 源码锚点

| 组件 | 路径 |
|------|------|
| 消费 SPI 接口 | `nop-credential/nop-credential-api/src/main/java/io/nop/credential/api/ICredentialProvider.java` |
| 唯一解密点实现 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java` |
| OAuth 流程引擎 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/OAuthFlowService.java` |
| OAuth 令牌端点客户端 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/OAuthTokenClient.java` |
| OAuth 回调 API 面 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/CredentialOAuthApiBizModel.java` |
| cv1 加解密 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/CredentialCipher.java` |
| 管理 BizModel | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java` |
| ORM 模型 | `nop-credential/model/nop-credential.orm.xml` |
| 类型元模型 | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/credential/credential-type.xdef` |
| 底层加密原语 | `nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java`（复用，不修改） |

## 相关文档

- `../02-core-guides/ioc-and-config.md`（`@sec:` 配置加密，与凭证库互补）
- `nop-ai.md`（`NopAiModel.credentialId` 迁移路径）
- `../reusable-modules-overview.md`
