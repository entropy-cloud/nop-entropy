# nop-credential — 加密凭证库模块

## 功能概览

为平台提供**行级敏感数据**（DB 中动态写入的密钥/连接参数）的类型化加密存储与统一解密入口，弥补 `@sec:` 配置加密只能处理"配置文件静态值"的不足。

- **类型化凭证**：声明式类型注册（`*.credential-type.xml`），定义字段结构（apiKey/secret/用户名密码等）
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

> 凭证类型（`*.credential-type.xml`）无 DB 表，经平台 register-model 机制声明式加载。

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-credential-api` | SPI/接口/DTO（零业务依赖：`ICredentialProvider`、`CredentialData`、`MaskedCredential`、`TestResult`） |
| `nop-credential-dao` | ORM 实体与 DAO |
| `nop-credential-meta` | xmeta 定义（`data` 列 `published=false` 明文边界） |
| `nop-credential-service` | `ICredentialProvider` 实现（唯一解密点）+ `NopCredentialBizModel` + 凭证类型注册表 + `CredentialCipher` |
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
| `saveCredential(typeName, name, fields, ...)` | 保存凭证（**明文唯一入口**，加密后落库） |
| `get` / `findPage` | 查询（返回的 `data` 恒为 null——BizModel 层强制置空） |
| `maskList(ids)` | 返回脱敏视图（敏感字段替换为 ****） |
| `typeList()` | 返回类型字段 schema（Web 动态表单用） |
| `test(id)` | 触发连通性测试 |
| `reencryptAll()` | 主密钥轮换后批量重加密（仅 admin） |
| `delete(id)` | 删除（软删除；前置检查 `NopCredentialUsage` 引用计数，>0 拒绝） |

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

## 源码锚点

| 组件 | 路径 |
|------|------|
| 消费 SPI 接口 | `nop-credential/nop-credential-api/src/main/java/io/nop/credential/api/ICredentialProvider.java` |
| 唯一解密点实现 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java` |
| cv1 加解密 | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/CredentialCipher.java` |
| 管理 BizModel | `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java` |
| ORM 模型 | `nop-credential/model/nop-credential.orm.xml` |
| 类型元模型 | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/credential/credential-type.xdef` |
| 底层加密原语 | `nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java`（复用，不修改） |

## 相关文档

- `../02-core-guides/ioc-and-config.md`（`@sec:` 配置加密，与凭证库互补）
- `nop-ai.md`（`NopAiModel.credentialId` 迁移路径）
- `../reusable-modules-overview.md`
