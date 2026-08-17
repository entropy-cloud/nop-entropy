# nop-integration / nop-metadata credentialId 深度迁移设计

**日期**：2026-08-17
**状态**：active
**范围**：消费方 `nop-integration`（sms-tencent / sms-yunpian / email-tencent / email-java / feishu / oss / sftp）、`nop-metadata`（NopMetaDataSource 数据源连接）；凭证库侧零变更（消费 `01-architecture-baseline.md` / `02-phase2-design.md` 已落地能力）

---

## 一、设计结论索引

- **§四 integration 接入模型**：混合裁定——**配置声明 `credentialId` + 发送期惰性解析**（运行时经 `ICredentialProvider`）为主路径，`@sec:` 静态配置加密保留为非托管场景基线（一期已裁定不翻案）；**拒绝**新增集成配置 DB 实体。`nop-integration-api` 增加对 `nop-credential-api` 的 compile 依赖（api→api 零业务依赖边），厂商发送器经共享解析支持获得统一语义；解析时序按家族分层（逐次发送 / 客户端缓存 / start 期捕获）。
- **§五 metadata 接入模型**：`credentialId` 挂点 = **`connectionConfig` JSON 内字段**（不新增 ORM 列、无 DDL），解析落点 = **单点 `MetaDataSourceConnectionProcessor.buildDataSource`**（14 处消费点零改动）；兼容矩阵三行（存量明文行 / 迁移行 / 过渡并存行），过渡并存允许但运行时整组取凭证侧；写入/清除/换绑载体 = `bindCredential`/`unbindCredential` 管理动作对（xmeta 通用面不可写，受控唯一入口）；`testConnect` 同路径解析、仅凭证解析失败结构化返回不泄漏。
- **§六 横切契约**：优先级链 `credentialId > 既有静态值`（空串视同缺失）；**强 fail-closed**（credentialId 已配置但解析失败——缺失/软删/必填字段空/错型/转换失败 → 中止，不静默回退静态值——对齐 nop-ai 运行时消费先例）；引用计数 `metadata:NopMetaDataSource:<dataSourceId>` 与 `integration:<channelType>`（token 全集枚举）；归属一律 system 级、RBAC 走既有判定矩阵零新增语义；回滚为引用级（unbind 回静态路径），**拒绝全局明文降级开关**；迁移 = 管理动作幂等反查复用 + per-row 事务（credentialId 设置与明文清除同事务原子）。
- **§七 out-of-scope**：一期拒绝项不翻案（`@credential:` 配置 resolver）；OSS/Feishu 热轮换（客户端重建）、集成配置 DB 实体形态、user 级凭证用于渠道/数据源、多环境密钥分离、Web 管理面增强等逐项裁定。
- **§八 W16-impl 映射**：无 ORM 模型变更（JSON 键方案的结构性收益）；新增凭证类型实例文件属类型清单落实；首发范围建议 SMS 家族 + metadata（有真实消费链与 DB 明文迁移目标），Email/Feishu/OSS/SFTP 为同型扩展批次。

## 二、迁移动机与威胁陈述

本节是迁移的验收语义来源：以下缺口在迁移后必须可观察地收敛。

### 2.1 明文存储面（confidentiality at rest）

| 面 | 现状 | 泄露后果 |
|---|---|---|
| metadata `connectionConfig` JSON 列（`nop_meta_data_source` 表） | `{jdbcUrl, username, password}` 明文存 DB（仅 `tagSet="sensitive"` 展示层标记，见 §3.4） | DB 备份/dump/拖库即得业务库凭证，横向移动到业务数据库 |
| integration SMS/Email 家族 | 无 beans.xml 装配值在仓库内，但消费方应用以 beans.xml 字面量或 `@cfg:` 明文键提供（无加密保障） | 配置文件/仓库泄露即得短信与邮件通道凭证（短信通道被冒用 = 登录验证码通道被劫持） |
| integration Feishu/OSS 配置键 | `nop.integration.feishu.*` / `nop.integration.oss.*` 支持但**不强制** `@sec:` 前缀 | 同上；运维漏写 `@sec:` 无任何结构性防护 |

### 2.2 轮换能力缺口（rotation）

- 配置文件密钥轮换 = 手工改文件 + 重启应用；无版本、无生效确认。
- DB JSON 列密码轮换 = 直接 UPDATE JSON 明文（无审计语义、无 lastUsedAt、无过期）。
- 凭证库迁移后：轮换 = `saveCredential` 重写密文（`cv1:` 多 key 并存 + `reencryptAll`），凭证 `status` 可即时禁用，`expireAt`/`lastUsedAt` 可观测。

### 2.3 审计缺口（audit & lifecycle）

- 现状两侧消费均为"读配置即得密钥"：无取用记录、无引用登记、无连通性测试挂凭证维度（metadata 的 `testConnect` 只测数据源不测凭证实体）。
- 凭证库迁移后：取用走 `ICredentialProvider` 唯一明文出口（归属/授权判定串联在出口），`registerUsage` 引用计数拦截"仍被引用的凭证被删除"，`NopCredentialUsage` 给出消费方清单。

## 三、引用点基线（live 锚点，2026-08-17 逐点复核）

> 本节是 W16-impl 的变更输入清单。枚举方法：以 secret 字段名（appKey/apiKey/secretId/secretKey/appSecret/encryptKey/password/passphrase/accessKey）、`connectionConfig`、`setPassword`、配置键前缀 `nop.integration.` 为关键词全量扫描 + 逐文件读码。`nop-integration` 下无任何 `*.orm.xml`、无任何 `NopIntegration*` 实体（repo 级核实）。

### 3.1 nop-integration 渠道密钥持有类与消费点

| 家族 | 持有类（secret 字段） | 值消费点 | 消费时序 |
|---|---|---|---|
| 短信-腾讯 | `TencentSmsSender`（appId/appKey/sign；`nop-integration/nop-integration-sms-tencent/.../TencentSmsSender.java:29-31`，setter :33-43） | `new SmsSingleSender(appId, appKey)` :47（sendMessage）、:91（sendMultiMessage） | **逐次发送**构造 sender |
| 短信-云片 | `YunpianSmsSender`（apiKey；`.../sms-yunpian/.../YunpianSmsSender.java:30`，setter :32-34） | `new YunpianClient(apiKey).init()` :40、:51 | **逐次发送**构造 client |
| 邮件-腾讯 | `TencentEmailSender`（secretId/secretKey/region；`.../email-tencent/.../TencentEmailSender.java:27-31`） | `new Credential(secretId, secretKey)` :61（`newClient` :59-73，sendEmail 内 :77 调用） | **逐次发送**构造 client |
| 邮件-JavaMail | `MailConfig`（username/password；`.../email-java/.../MailConfig.java:40-45`）+ `JavaEmailSender` 持 config | `transport.connect(host, port, username, password)` `JavaEmailSender.java:102-115`（读 :103-104、connect :113） | **逐次发送**建 Transport（`withTransport` 每次调用） |
| 飞书 | `FeishuCredentials`（appId/appSecret/verificationToken/encryptKey；`.../feishu/client/FeishuCredentials.java:22-27`） | `FeishuClient.start` → `getStreamEndpoint(appId, appSecret)` `FeishuClient.java:114`；token 惰性刷新 `ensureToken` → `getTenantAccessToken(appId, appSecret)` :167-177；重连 :223 | **start 期捕获凭证对象**，appSecret 在 token 过期时（约 2h）重复消费；verificationToken/encryptKey 仅有 getter（:47/:56），**仓库内无消费方**（预留字段） |
| OSS | `OssConfig`（accessKey/secretKey；`.../oss/OssConfig.java:21-22`） | `OssFileServiceClientFactory.init`（`@PostConstruct`）`new BasicAWSCredentials(accessKey, secretKey)` `OssFileServiceClientFactory.java:36-47`（:41-42） | **构造期一次性**消费（AmazonS3 客户端缓存于 :30/:44-46） |
| SFTP | `SftpConfig`（username/password/keyPath/passphrase；`.../sftp/SftpConfig.java:12-19`，`@ConfigBean`） | `SftpClient`：`jsch.addIdentity(keyPath, passphrase)` :55、`getSession(username, host, port)` :57、`session.setPassword(password)` :66 | **逐次操作**（`SftpClientFactory.newClient` 每次新建，`SftpClientFactory.java:24-26`） |

### 3.2 值供给面（migration 工具语义的输入）

| 家族 | 供给机制 | 仓库内装配值 |
|---|---|---|
| 短信-腾讯 / 短信-云片 / 邮件-腾讯 / 邮件-JavaMail / SFTP | 纯 JavaBean setter，由**消费方应用 beans.xml** 提供（`<property name="appKey" .../>` 或 `@cfg:` 引用） | **无**（repo 全量 `*.xml` 无任何 `TencentSmsSender|YunpianSmsSender|TencentEmailSender|JavaEmailSender|MailConfig` 装配；测试侧仅 mock：`nop-auth-service/src/test/resources/_vfs/nop/auth/beans/app-test.beans.xml:16` 注入测试 ISmsSender） |
| 飞书 | `@InjectValue("@cfg:nop.integration.feishu.appId|appSecret|verificationToken|encryptKey|")` setter 注入（`FeishuCredentials.java:33,42,51,60`，空串缺省）；bean 装配 `feishu-defaults.beans.xml:18-19`（无参 bean，值全来自配置键）；`FeishuBindProvider` 注入 credentials（同文件 :32-35，appId 空校验 `FeishuBindProvider.java:153-157`） | 配置键 `nop.integration.feishu.*`（值在部署侧） |
| OSS | `ioc:config-prefix="nop.integration.oss"` 绑定（`oss-defaults.beans.xml:25`），工厂 bean 由 `nop.integration.oss.enabled` 门控（enableIfMissing=false，:7-14） | 配置键 `nop.integration.oss.*`（含 access-key/secret-key，见 `docs-for-ai/03-modules/nop-file.md:209-216`）；下游还有 nop-file 消费链（`nop-file/nop-file-dao/.../app-file-dao.beans.xml:12` 引用 `nopOssResourceStore`——仅消费工厂 bean，不触密钥，W16 无需改动） |

- 飞书消费链下游：`FeishuConnector.resolveCredentials`（`nop-ai/nop-ai-gateway/.../channel/feishu/FeishuConnector.java:618-640`）优先取 `ChannelConfig` options `feishu.appId/appSecret`（:622-629），次取 `feishu.credentials` option（:633-636），否则空凭证对象（start 时显式报错）——即飞书凭证实际有**配置键 + channel options 双供给面**。
- 运行时 SMS 消费方（in-platform）：`LoginServiceImpl`（`nop-auth/nop-auth-service/.../login/LoginServiceImpl.java:181-183` `@Inject @Nullable ISmsSender`；fail-closed 发送 :729-740，null 时抛错 :730-733）、`NopAuthUserBizModel`（同型注入 :115-117；`sendSmsForBinding` :603-613，null 检查 :604-607）。
- 运行时 Email 消费方：**仓库内 main 代码无任何 `IEmailSender` 消费方**（仅接口与两实现；邮件验证码消费链属 MFA 二期 W15-impl 未来项）。

### 3.3 nop-metadata `connectionConfig` 读点与汇聚链

- 实体 `NopMetaDataSource`（表 `nop_meta_data_source`，`nop-metadata/model/nop-metadata.orm.xml:377-429`）：密码在 `CONNECTION_CONFIG` 列（`json-4000`，`tagSet="sensitive"`，:395-397）内明文 JSON `{jdbcUrl, username, password, driverClassName}`；**无独立 PASSWORD 列**；主键 `dataSourceId`（:383-385），`querySpace`/`name` 唯一（:418-423）。
- **全部读点（14 处 / 9 文件，main 代码）**，全部形如 `connectionService.withConnection(...)`（`NopMetaDataSourceBizModel.java:126` 一处为 `testConnect(...)`），汇聚于 `IMetaDataSourceConnectionProcessor`：
  - `NopMetaDataSourceBizModel.java`：testConnect :126、scanExternalTables :194、collectCatalogTables :304
  - `NopMetaTableQueryAction.java`：:121、:141
  - `MetaJoinExecutor.java`：:364、:445
  - `ExternalAggregationProcessor.java`：:66；`MixedSameDbJoinAggregationProcessor.java`：:121、:170；`ExternalExternalJoinAggregationProcessor.java`：:87
  - `SqlViewFieldTypeInferrer.java`：:125；`TableReferenceExecutor.java`：:131；`NopMetaQualityRuleBizModel.java`：:233
- **单点汇聚实现** `MetaDataSourceConnectionProcessor`（`nop-metadata/nop-metadata-service/.../connection/MetaDataSourceConnectionProcessor.java:42`）：`withConnection` :123-137 与 `testConnect` :139-164 均收敛到 `buildDataSource` :173-197——password 提取 :180（`requireField`，允许空串仅要求 key 存在）→ `ds.setPassword(password)` :192 → 每次调用新建非池化 `SimpleDataSource` :189。
- **最终汇聚点**：`SimpleDataSource.getConnection`（`nop-persistence/nop-dao/.../SimpleDataSource.java:36-49`）→ `DriverManager.getConnection(url, props)` :49（password 经 Properties :45-47）。
- 模块依赖事实：`nop-integration`、`nop-metadata`、`nop-auth` 当前 pom **均不依赖任何 `nop-credential` 模块**（repo 级核实）；`nop-ai/nop-ai-service` 已依赖 `nop-credential-api`（`nop-ai/nop-ai-service/pom.xml:30`，唯一在库先例）。

### 3.4 既有安全缓解盘点（迁移必须保持的能力）

| 侧 | 缓解 | 锚点 | 性质 |
|---|---|---|---|
| integration | `@sec:` 配置值加密：`DefaultConfigValueEnhancer` 识别 `@sec:` 前缀经 `AESTextCipher` 解密（`nop-core-framework/nop-config/.../DefaultConfigValueEnhancer.java:66-68`；`nop-kernel/nop-commons/.../crypto/impl/AESTextCipher.java:48`，encrypt :340 / decrypt :388） | 配置加载期一次解密，仅覆盖配置文件静态值 | 与凭证库互补（`01-architecture-baseline.md` §2.2），本设计不翻案 |
| metadata | JDBC URL 协议白名单（mysql/postgresql/h2 mem/file）+ 危险参数黑名单 + 内网主机白名单（`validateJdbcUrl` :216-247，协议表 :55-56、黑名单 :59-72、`nop.metadata.datasource.allowed-hosts` :105） | 消费期强制 | 保持 |
| metadata | driverClassName 白名单（`validateDriverClassName` :405-410，白名单 :75-79） | 消费期强制 | 保持 |
| metadata | URL 凭据脱敏 `redactJdbcUrl`（:257-260）；元数据变更事件敏感列快照脱敏（`MetaModelChangedEventPublisher.java:63-81`，`REDACTED_VALUE`/`SENSITIVE_TAG` + 硬编码兜底列名含 `connectionConfig`/`password`） | 展示/审计层 | 保持 |
| metadata | `tagSet="sensitive"`（orm.xml :397） | **仅展示层标记，无强制处理**——密码明文入库无阻断 | 迁移目标 |

### 3.5 凭证库消费基座与 nop-ai 先例链（W7 / W7-successor）

- 消费 SPI：`ICredentialProvider`（`nop-credential/nop-credential-api/.../ICredentialProvider.java:20`；`getCredential` :29 / `getCredentialData` :39 / `testCredential` :48 / `mask` :57 / `registerUsage` :65 / `unregisterUsage` :73），实现 `CredentialProviderImpl` 在 `nop-credential-service`（唯一明文出口，归属 + RBAC 判定串联于出口，见 `02-phase2-design.md` §五/§六）。
- nop-ai 先例链（本设计的语义模板）：SPI `IAiModelCredentialResolver`（`nop-ai/nop-ai-api/.../credential/IAiModelCredentialResolver.java:32-42`，javadoc 载明优先级链与 fail-closed 契约）→ impl `AiModelCredentialResolverImpl`（`nop-ai/nop-ai-service/.../credential/AiModelCredentialResolverImpl.java:47`；credentialId 非空且解析失败强 fail-closed :124-131）→ 钩点 `ChatServiceImpl`（`nop-ai/nop-ai-core/.../service/ChatServiceImpl.java`：resolver 注入 :82/:99、`buildHttpRequest` :253、`resolveApiKeyForRequest` :286；链 `accountKey > credentialId > resolveApiKey`）。
- 装配先例：`nop-ai-service` compile 依赖 `nop-credential-api`，`ICredentialProvider` 经 `@Nullable` 可选注入（部署无凭证库为 null）；消费 app 需 import `credential-defaults.beans.xml`。

## 四、integration 接入模型裁定

### 4.1 设计结论

1. **消费模型 = 配置/装配声明 `credentialId` + 发送期经 `ICredentialProvider` 惰性解析**。每个发送器家族新增可选 `credentialId` 属性（bean property；config-bound 家族对应新增配置键，如 `nop.integration.feishu.credentialId`、`nop.integration.oss.credentialId`；SMS/Email 家族由消费方应用 beans.xml 以 `@cfg:` 引用或字面量提供）。`credentialId` 非空时，该家族的**凭证字段集整组**取自凭证库；同名字段静态值被忽略（不逐字段混搭）。`credentialId` 为空时维持现状（静态值直用），**既有部署零回归**。
2. **凭证字段集 = 认证身份对与应用级参数；连接拓扑不入凭证**。拓扑类字段（endpoint、host、port、bucket、keyPath、protocol、MailConfig 扩展 properties）留在配置；凭证类型字段集见 §4.3 表。理由：拓扑是部署资产（版本化配置文件持有），密钥是安全资产（凭证库持有）；轮换密钥不动拓扑、改拓扑不碰密文；加密面最小化。
3. **`@sec:` 静态加密保留为非托管场景的基线**（`01-architecture-baseline.md` §3.5 既有裁定）：部署级静态密钥、无运行期管理诉求的场景继续 `@sec:`；`credentialId` 路径服务轮换/审计/禁用/连通性诉求。两者非互斥（`credentialId` 缺省即回退静态值路径）。
4. **拒绝新增集成配置 DB 实体**（候选 A，见 §4.2）。
5. **依赖边**：`nop-integration-api` 新增对 `nop-credential-api` 的 compile 依赖，并在其中承载**共享解析支持**（统一的优先级链、fail-closed 判定、`@Nullable` provider 语义——公共语义单点定义，厂商模块不各自复制）；厂商模块无需新增任何直接依赖（现状 oss 另有 nop-core/file-local、sftp 另有 nop-api-core 边，维持不动）。发送器经 **setter/protected 注入**持有 `@Nullable ICredentialProvider`（NopIoC 对 XML 声明 bean 同样执行 `@Inject` 注解注入——`BeanDefinitionBuilder.autowireProps` 按类型装配，`@Nullable` 即可选，显式 `<property>` 优先；NopIoC 不注入 private 字段——平台硬约束），由消费方应用 beans.xml 装配或容器按类型注入。
6. **解析时序按家族分层**：
   - **逐次发送家族**（sms-tencent / sms-yunpian / email-tencent / email-java / sftp）：每次发送/建连时解析——与现状"逐次构造 sender/client/transport"的时序天然同构（§3.1），凭证轮换与禁用对长生命周期 bean **下次发送即生效**。
   - **客户端缓存家族**（oss）：`@PostConstruct` 构造期解析一次；轮换可见性 = bean 重建（重启）——显式接受的限制（§七 deferred 热重建）。
   - **start 期捕获家族**（feishu）：`FeishuClient.start` 时解析一次并持有；token 惰性刷新（`ensureToken`）继续使用已捕获值——轮换可见性 = connector 重启；刷新期再解析列 deferred（§七）。
7. **飞书接线裁定（双供给面的入口收敛）**：`credentialId` 落在 `FeishuCredentials` 新增字段（经新配置键 `nop.integration.feishu.credentialId` `@InjectValue` 注入，与现有四键并列）；`FeishuClient.start` 时若 `credentialId` 非空 → 经 provider 解析凭证字段集并构建**已解析副本**（整组覆盖 appId/appSecret/verificationToken/encryptKey 四字段）供 client 持有——原 DataBean 保持 `@InjectValue` 装配形态不变，解析副本仅在 start 期产生。`FeishuConnector.resolveCredentials` 的 **ChannelConfig options 面（`feishu.appId/appSecret`、`feishu.credentials`，`FeishuConnector.java:622-636`）首期不引入 credentialId 支持**（options 面维持字面值语义；配置键面为 credentialId 唯一入口）——理由：options 面是 nop-ai-gateway 的 ChannelConfig 扩展点，为其引入凭证引用语义需要网关侧配合改造，超出本迁移范围（§七#10 deferred）。
8. **引用计数**：`consumerRef = integration:<channelType>`（部署级单渠道，每渠道类型一条稳定引用；token 清单：`integration:tencent-sms` / `integration:yunpian-sms` / `integration:tencent-email` / `integration:smtp-email` / `integration:feishu-app` / `integration:oss-s3` / `integration:sftp-ssh`），credentialId 非空且 provider 装配时在 bean 初始化幂等登记（`registerUsage` 幂等且不校验凭证存在性，重复启动无副作用）；**登记失败（DB 异常等）记 WARN 不阻断启动**——登记是治理辅助而非安全边界（安全边界 = 运行时解析 fail-closed），审计行写入失败不应杀死应用；渠道移除后的陈旧引用行由管理面人工清理（引用拦截按"该凭证仍被 config 侧引用"理解，可接受）。

### 4.2 候选对比与拒绝了什么

| 候选 | 结论 | 理由 |
|---|---|---|
| A. 新增集成配置实体（每渠道/通用渠道配置表，行持 credentialId） | **拒绝** | nop-integration 定位纯配置 Bean 模块（无 ORM 层，repo 级核实）；渠道配置是部署级静态资产，无运行期 CRUD 需求实证；为挂 credentialId 引入 ORM 层 = 模块边界破坏 + DDL/迁移成本，收益仅为"Web 管理渠道配置"（未证实需求，列 §七）。`01-architecture-baseline.md` §4 拒绝"凭证库并入 nop-integration"的理由（纯配置 Bean 模块）对称适用 |
| B1. 配置值魔法前缀（`appSecret = credential:123` 字符串内嵌引用） | **拒绝** | 隐式约定藏进字符串值，解析层分裂（与 `@sec:`/`@cfg:` 前缀集合交互）；W7 先例（`NopAiModel.credentialId` 独立字段）与平台惯例均为显式引用字段；一期已拒绝 `@credential:` 配置 resolver（配置加载期启动顺序问题），字符串内嵌变体同病 |
| B2. 独立 `credentialId` 属性/配置键 + 运行时解析 | **选定** | 显式、零魔法、对齐 W7 先例形态；缺省空 = 零回归；fail-closed 语义可单点定义（共享解析支持） |
| C. 维持 `@sec:` + 仅裁定边界（不接凭证库） | 保留为**基线而非替代** | `@sec:` 解决 at-rest 静态值，不解决轮换/审计/禁用/引用拦截（§2.2/§2.3）；roadmap W16 的"深度迁移"目标即运行期管理场景接入凭证库；C 单独不交付 |
| 发送器经独立 resolver SPI（W7-successor 同构：SPI 在消费方 api、impl 桥接） | **拒绝** | W7-successor 的 SPI 中介存在是因为 `nop-ai-core` 钩点不能依赖 DAO/credential；integration 的钩点就是发送器自身（叶组件），直接持有 `@Nullable ICredentialProvider` 少一层间接；且 integration 无"消费方 service 模块"可承载桥接 impl（消费方是各应用） |
| 厂商模块各自依赖 `nop-credential-api` 并复制解析语义 | **拒绝** | 语义多点复制 = fail-closed 行为漂移温床；共享支持单点在 `nop-integration-api`，厂商模块零感知 |

### 4.3 凭证类型清单与字段 schema（裁定，实例文件落实属 W16-impl）

| typeName | 字段（sensitive / 必填性） | 对应家族/注释 |
|---|---|---|
| `tencent-sms` | appId（false / 必填）、appKey（true / 必填）、sign（false / 可空） | 腾讯短信应用级参数整组（sign 为应用级签名非机密，发送侧现状直传允许 null） |
| `yunpian-sms` | apiKey（true / 必填） | 云片单密钥 |
| `tencent-email` | secretId（false / 必填）、secretKey（true / 必填）、region（false / 必填） | region 随凭证（SesClient 构造必需）；endpoint 为代码常量 |
| `smtp-email` | username（false / 可空）、password（true / 可空） | host/port/protocol/properties 留 `MailConfig`；两者均可空对齐 `JavaEmailSender` 空=无认证语义（:105-110），但**整凭证至少一字段非空**（全空凭证无意义，保存/解析拒绝） |
| `feishu-app` | appId（false / 必填）、appSecret（true / 必填）、verificationToken（true / 可空）、encryptKey（true / 可空） | 飞书应用四元组整组；后两者为预留字段（§3.1 核实无消费方） |
| `oss-s3` | accessKey（false / 必填）、secretKey（true / 必填） | endpoint/region/bucket/pathStyle 留 `OssConfig` |
| `sftp-ssh` | username（false / 可空）、password（true / 可空）、passphrase（true / 可空） | host/port/keyPath 留 `SftpConfig`（keyPath 为文件路径拓扑）；公钥无口令场景两者均空合法 |
| `jdbc-datasource` | username（false / 必填）、password（true / 可空） | jdbcUrl/driverClassName 留 `connectionConfig`；password 可空对齐 `requireField` 现状（H2 空密码，§3.3）——**必填性=消费方现状语义，不收紧** |

- 类型经平台标准 register-model 机制声明（`*.credential-type.xml`，`authType` 按 xdef 语义由 impl 裁定）；字段 schema 即 `typeList` 动态表单的输入。
- **整组生效语义**：`credentialId` 非空 → 凭证字段集全量取自凭证库；发送器同名静态字段（如 beans.xml 里的 appKey）被忽略，仅作 `credentialId` 清除后的回退值（回滚路径，§六）。
- **解析契约三条**（共享解析支持统一承载）：
  1. **必填字段缺失/空值 = fail-closed**（§6.1 精确化为"必填字段"而非一切字段——可空字段空值合法，如 jdbc 空密码、sftp 公钥无口令）；
  2. **类型校验**：凭证 `typeName` 不在该家族允许集（如 tencent-sms 的 credentialId 指向 `yunpian-sms` 凭证）→ fail-closed 明确错型错误（不允许靠"字段恰好同名"继续）；
  3. **值转换**：字段值经字符串归一（如 `tencent-sms.appId` 为 Integer）后按目标类型转换，转换失败 = fail-closed。
- **错误码归属**：integration 侧解析错误入 `IntegrationErrors`（`nop-integration-api` 既有错误类家族）；metadata 侧入 `NopMetadataErrors`。

### 4.4 与既有设计的兼容性

- `01-architecture-baseline.md` §3.1 依赖图预留的 `integ → api/svc` 边在本设计兑现（api 边为 compile、service 边为 runtime bean）。
- `@sec:` 与 `cv1:` 互补关系不变；`@credential:` 配置 resolver 维持一期拒绝（`02-phase2-design.md` §七 同款不翻案）。
- 消费走 `ICredentialProvider` 唯一明文出口：归属（§五 02-phase2）与 RBAC（§六 02-phase2）判定自动串联，无需 integration 侧任何授权代码。

## 五、metadata 接入模型裁定

### 5.1 设计结论

1. **`credentialId` 挂点 = `connectionConfig` JSON 内字段**：迁移后 JSON 形如 `{"jdbcUrl": "...", "credentialId": "..."}`——username/password 整组入凭证（类型 `jdbc-datasource`，§4.3），jdbcUrl/driverClassName 留 JSON（拓扑 + AR-02 校验锚点不动）。**不新增 ORM 列、无 DDL**。
2. **解析落点 = 单点 `MetaDataSourceConnectionProcessor.buildDataSource`**：JSON 解析（`parseConnectionConfig`）后若含 `credentialId` → 经 `@Nullable ICredentialProvider` 取 `{username, password}` 合并进 cfg map → 走既有 `requireNonBlank`/`requireField`/AR-02 校验/`SimpleDataSource` 建连，全链零变化。**14 处消费点（§3.3）零改动**——它们传递的 `getConnectionConfig()` 字符串天然携带 credentialId。`nop-metadata-service` 新增 `nop-credential-api` compile 依赖（与 `nop-ai-service` 同构的 api-only 边）。
3. **兼容矩阵**：

| 行形态 | JSON 内容 | credentialId | 运行时行为 |
|---|---|---|---|
| 存量明文行 | password 在 JSON | 无键 | 现状路径（username/password 取 JSON）——零回归 |
| 迁移行 | password 已清除 | 有 | username/password 整组取凭证库；JSON 内 jdbcUrl/driverClassName 照旧 |
| 过渡并存行 | password 仍在 JSON | 有 | **凭证侧整组生效，JSON 内 username/password 忽略**；credentialId 解析失败 → **fail-closed 抛错，不回退 JSON 明文**（见 §六） |

   允许并存（迁移窗口期安全网），迁移动作在切换时同事务清除明文（§6.4）；并存行的 JSON 明文密码是死值（运行时不可达）。**credentialId 空串/空白值视同缺失**（走静态值路径，不触发解析）。
4. **`testConnect` 管理面语义**：与运行时同路径解析（`testConnect` 也经 `buildDataSource`，:143）——测试结果真实覆盖凭证解析 + 建连全链；**catch 范围精确化**：仅凭证解析异常（provider 抛出的 `NopException`）映射进既有 `{connected:false, error}` 结构化返回（error 为固定描述如 "credential resolution failed"，不携带凭证明文/密文/credentialId 以外细节）；AR-02 与 config-invalid 异常维持现状上抛（不吞成结构化 false，防校验失败被静默降级）。
5. **credentialId 的写入/清除载体 = BizModel 管理动作对**（bind / unbind，管理员）：`bindCredential(dataSourceId, credentialId)` = 置 JSON 键 + 清除 username/password 明文 + `registerUsage`，同一行级事务；`unbindCredential(dataSourceId)` = 清除 JSON 键（回滚 §6.2）+ `unregisterUsage`，明文需另行重录（unbind 不自动复活死值）；换绑 A→B = bind 内 unregister 旧 + register 新。**理由**：live xmeta 将 `connectionConfig` 设为 `published=false insertable=false updatable=false queryable=false`（`NopMetaDataSource.xmeta`，GraphQL 面整体不可读写），行编辑 UI form 为空——通用 CRUD 面不存在 credentialId 的合法写入通道，管理动作是唯一受控入口（对齐迁移工具语义 §6.4；xmeta/view 放开编辑属 UI 增强，deferred §七#11）。保存路径对含 credentialId 的 JSON 仅做结构校验（jdbcUrl 必填不变），不做凭证存在性校验（运行时 fail-closed 兜底）。

### 5.2 候选对比与拒绝了什么

| 候选 | 结论 | 理由 |
|---|---|---|
| 独立 `CREDENTIAL_ID` 列（W7 `NopAiModel.credentialId` 同构） | **拒绝** | 需要 ORM 结构变更（Protected Area plan-first）+ 三方言 DDL + 实体/xmeta/codegen 全链改动；且 credentialId 在列上而配置在 JSON 内 → `buildDataSource(datasourceType, connectionConfig)` 签名需传染 14 消费点（每处多传一参，新增消费点漏传 = 隐性丢凭证）。查询/索引收益无实证——引用追踪已由 `NopCredentialUsage` 从凭证侧承担。**诚实权衡登记**：独立列天然获得独立 xmeta 发布位（可读写编辑面），JSON 键方案下该面不存在——但 live 的 `connectionConfig` 整列本就 `published=false` 且编辑表单为空（§5.1.5），通用编辑面不是现网能力；受控管理动作对（bind/unbind）承担写入/清除/换绑语义，取舍成立 |
| JSON 内 `credentialId` 字段 | **选定** | 零 DDL、零消费点改动（引用随配置字符串自包含传输）；配置文档（JSON）与凭证引用同生命周期，导入/导出/复制数据源行天然携带 |
| 混合（列 + JSON 双写） | **拒绝** | 双源真相，一致性问题无对应收益 |
| 各消费方各自解析（非单点） | **拒绝** | 14 处复制解析与 fail-closed 语义 = 行为漂移温床；现状已单点汇聚（§3.3 核实），单点解析是保持既有结构的最小改动 |
| 凭证持完整连接档案（jdbcUrl/username/password 整组入凭证） | **拒绝** | jdbcUrl 是拓扑 + AR-02 安全校验锚点（协议白名单/危险参数/主机白名单在消费期执行）；入凭证后校验点被迫挪到凭证解析侧或重复实现，且同一 DB 多数据源共享凭证的能力受损（凭证=身份对，jdbcUrl 各行异） |

### 5.3 与既有安全缓解的关系

- AR-02 全链（URL 校验/驱动白名单/主机白名单/超时）**原位不动**：jdbcUrl 留 JSON，校验仍在 `buildDataSource`。
- 事件脱敏（`MetaModelChangedEventPublisher` 硬编码兜底含 `connectionConfig`/`password`）原位不动；credentialId 键位于 sensitive 列内，快照脱敏随整列覆盖。
- `tagSet="sensitive"` 语义不变；迁移后该列敏感面收窄（仅 jdbcUrl + credentialId 引用）。

## 六、横切契约

### 6.1 优先级链与 fail-closed（两侧统一）

```
取值判定（发送期/建连期，每次消费执行）:
  if credentialId 为空或空白:
      使用既有静态值（JSON 明文字段 / 配置键 / setter 值）   # 现状路径，零回归
  else:  # credentialId 非空
      if provider 未装配 (@Nullable 注入为 null):
          fail-closed: 抛部署不一致错误                      # 对齐 nop-ai 先例 :124-131
      else:
          try 整组取凭证字段集（含 typeName 家族校验与值转换，§4.3 解析契约）
          凭证缺失/软删/解密失败/必填字段缺失或空/错型/转换失败/归属或授权拒绝:
              fail-closed: 抛错中止本次发送/建连              # 不回退静态值
          成功 → 使用凭证值（同名静态值忽略；可空字段空值合法）
```

- **不允许静默回退**：credentialId 已配置即宣告"该渠道/数据源的密钥由凭证库治理"，解析失败回退明文/旧值 = 掩盖凭证库故障 + 已轮换密钥继续使用（旧密钥可能已泄露——这正是轮换场景）。此语义与 nop-ai 运行时消费先例（`IAiModelCredentialResolver` javadoc 契约 + impl :124-131）逐字对齐。
- credentialId 为空的回退不是"降级"而是**兼容路径**（部署未启用凭证库的合法形态）。
- SMS 家族的 fail-closed 消费点位于发送器内部：`LoginServiceImpl`/`NopAuthUserBizModel` 既有 `smsSender == null` fail-closed 检查（§3.2）不动，新增的是发送器内部凭证解析失败同样显式抛错（不吞成"发送失败"日志）。

### 6.2 回滚路径（安全论证）

- **回滚单元 = 引用级**：清除该渠道/数据源的 credentialId（integration：配置键置空；metadata：`unbindCredential` 动作，§5.1.5）→ 立即回退静态值路径。过渡并存行（§5.1）因静态值仍在，回退零成本；迁移完成行（明文已清除）回退需重新录入静态值——**这是特性而非缺陷**：明文一旦清除，回滚必须走显式再录入，杜绝"从死值复活明文"。
- **拒绝全局明文降级开关**（"凭证库故障时允许全部回退明文"类配置）：降级路径即攻击路径（打挂凭证库 = 全部渠道回退明文/旧密钥），与 KMS 设计"无本地降级"（`02-phase2-design.md` §4.3）同一原则。
- **可用性论证**（凭证库不可用时数据源是否可用）：`nop_credential` 表与 `nop_meta_data_source` 同库——凭证库"不可用"（DB 故障）时数据源实体本身即不可加载，可用性耦合是同库事实而非凭证库引入；密钥材料为启动期交付、运行期零托管端调用（KMS 材料交付模式），KMS 故障不影响运行期解密。故 fail-closed 不产生新的可用性风险面。

### 6.3 归属与 RBAC 消费点

- **归属一律 system 级**：渠道密钥与数据源凭证均为部署级平台资产（非个人凭证）；迁移工具与管理员创建时固定 `scope=system`。user 级凭证用于渠道/数据源被拒绝（§七#4）。
- **RBAC 走既有判定矩阵零新增**：消费经 `ICredentialProvider` 明文出口，`02-phase2-design.md` §6.3 矩阵原样适用——无授权记录（默认）= 放行；有记录 + 用户上下文 = 角色求交；有记录 + 无上下文（后台消费）= 放行（服务级信任）。metadata 查询运行于用户请求线程（上下文在场），收紧配置对"人"生效；integration 发送（MFA 验证码）同样在请求线程。两侧**不写任何授权代码**——判定在 provider 出口已串联。
- **user 级不可用于本迁移**的另一面：后台批处理（质量规则/定时同步）无用户上下文，只能消费 system 级凭证——与数据源资产属性一致。

### 6.4 迁移工具面

| 维度 | metadata | integration |
|---|---|---|
| 存量对象 | `nop_meta_data_source` 行（JSON 含 password） | 无 DB 行——配置侧选择加入 |
| 工具形态 | `NopMetaDataSourceBizModel` 管理动作：`bindCredential`（单行，§5.1.5）+ 批量迁移变体（管理员） | 无迁移工具，**迁移 runbook 文档**（设 credentialId 键/属性 → 验证发送 → 清除静态密钥）为显式交付物（§八） |
| 步骤语义 | 逐行：**幂等反查**（按确定性凭证名 `<typeName>:<querySpace>/<name>` 与 `NopCredentialUsage` consumerRef 双源定位既有凭证，命中即复用不重建）→ 缺失则创建凭证（username/password 取自 JSON）→ `registerUsage("metadata:NopMetaDataSource:<dataSourceId>")` → JSON 置 credentialId 并清除 username/password 明文——**四步同一行级事务**（biz action 内显式 per-row 事务边界，REQUIRES_NEW 或等价；整行原子，中断重试经反查收敛无孤儿/无重复凭证） | 运维顺序：先加 credentialId（并存窗口验证）→ 后删静态密钥（§6.2 回滚语义） |
| 断点续跑 | 逐行独立事务 + `orderBy dataSourceId` 确定性排序 + 幂等重跑（反查复用；对齐 `reencryptAll` 修复后的确定性排序与幂等重跑语义——事务粒度不同：reencryptAll 为 GraphQL 引擎单事务整体，此处显式 per-row） | N/A |
| 明文清除时点 | **切换同事务**（credentialId 写入与明文清除原子），不存在"已引用凭证但仍留明文"的中间落盘态 | 运维显式删除配置键（存在窗口期明文，属配置文件治理范畴） |
| 审计 | 实体变更事件（`MetaModelChangedEventPublisher`，敏感列快照脱敏自动覆盖）+ 凭证侧 `tagSet="audit"` | 凭证侧审计（`registerUsage`/`lastUsedAt`） |

### 6.5 引用计数键规范（consumerRef）

| 消费方 | consumerRef | 登记时机 | 解除时机 |
|---|---|---|---|
| metadata 数据源 | `metadata:NopMetaDataSource:<dataSourceId>` | `bindCredential`/迁移批量变体（幂等，§6.4 反查复用） | `unbindCredential`（清除/回滚）与换绑 A→B（bind 内 unregister 旧 + register 新）及行删除（BizModel delete 钩子，对齐 `ai:NopAiModel:<id>` 在 NopAiModelBizModel 的先例） |
| integration 渠道 | `integration:<channelType>`（token 清单见 §4.1 结论 8） | bean 初始化且 credentialId 非空且 provider 装配（幂等；失败记 WARN 不阻断启动） | 无自动解除（配置移除后人工清理；陈旧引用仅影响该凭证的删除拦截提示，语义可接受） |

## 七、out-of-scope / deferred 裁定表

**维持一期拒绝（不翻案）**：`@credential:` 配置 resolver——`nop-integration-api` 共享解析支持是**发送器属性级**解析（发送期执行），不触碰配置加载链，与一期拒绝理由（`IConfigValueEnhancer` 启动顺序）无冲突，不构成翻案。

| # | 条目 | Classification | Why Not Blocking | Successor |
|---|---|---|---|---|
| 1 | 飞书 token 刷新期凭证再解析（`ensureToken` 每次取新 appSecret，彻底轮换可见） | optimization candidate | start 期解析已消除配置明文（主目标）；token 刷新周期约 2h，轮换最迟一个 token 周期后经 connector 重启生效；刷新期再解析需 `FeishuClient` 持解析回调而非凭证值，改造面与收益不成比例 | W16-impl 可选增量 |
| 2 | OSS 客户端热重建（凭证轮换免重启） | optimization candidate | 存储密钥轮换低频且通常计划性停机执行；`@PostConstruct` 构造期解析 + 重启生效语义显式清晰 | no（痛点实证后并入） |
| 3 | 集成配置 DB 实体 + Web 渠道管理面（候选 A 的完整形态） | out-of-scope improvement | 渠道配置运行期 CRUD 无需求实证；`credentialId` 配置路径已交付轮换/审计/禁用能力 | no（需求实证后新开设计） |
| 4 | user 级凭证用于渠道/数据源（个人外部账号） | out-of-scope improvement | 渠道/数据源为部署级共享资产，消费含无用户上下文的后台链路（质量规则/定时同步），user 级 owner-唯一出口语义与之冲突 | no（需求实证后重开归属语义） |
| 5 | 多环境密钥分离（dev/staging/prod 凭证命名空间） | out-of-scope improvement | 凭证库按部署实例隔离，环境分离=各环境各自凭证实例（现 naming 约定可承担）；平台级多环境编排非凭证库职责 | no |
| 6 | 新类型的 `testCredential` 连通性实现（tencent-sms 真实发一条测试短信等） | optimization candidate | `testCredential` 一期对未实现类型显式返回 `success=false` + 描述（非静默）；metadata 侧 `testConnect` 已覆盖 jdbc 全链真实测试 | C1-hardening 同族可选项或 W16-impl |
| 7 | `nop.integration.feishu.*` / `nop.integration.oss.*` 静态值强制 `@sec:`（启动校验拒绝明文） | watch-only residual | 强制化会破坏既有部署（明文合法形态）；feishu/oss 发送器接线在扩展批次落地前，静态值仍是**主路径**（非仅回退，#9 联动），明文暴露面在扩展批次前不因本设计收窄——诚实登记为观测项：审计部署配置 | no |
| 8 | 邮件家族首期接入 | 首期范围裁定（非缺陷） | 仓库内无 `IEmailSender` 运行时消费方（§3.2 核实；W15-impl 前无调用链可验证）；设计契约（类型 schema + 解析语义）已覆盖，接入为纯增量 | W15-impl 或 W16-impl 扩展批次 |
| 9 | Feishu/OSS/SFTP 家族首期接入 | 首期范围裁定（非缺陷） | 三家族为部署级静态密钥、`@sec:` 基线保护在场；设计契约已覆盖全部家族，实现为同型扩展。**批次收口约束见 §八**（不得静默丢失） | W16-impl 扩展批次或 roadmap 显式新工作项 |
| 10 | 飞书 ChannelConfig options 面的 credentialId 支持（`feishu.credentialId` option，经 `FeishuConnector.resolveCredentials` 生效） | out-of-scope improvement | options 面是 nop-ai-gateway 的 ChannelConfig 扩展点，引入凭证引用需网关侧解析配合（超出 integration 模块边界）；配置键面已提供 credentialId 唯一入口（§4.1 结论 7） | no（网关侧需求实证后并入） |
| 11 | 数据源 credentialId 的 xmeta/view 编辑面放开（GraphQL 可读写 credentialId 键） | optimization candidate | bind/unbind 管理动作已是受控唯一入口（§5.1.5）；xmeta 放开扩大攻击面与校验复杂度，UI 便利性收益未证实 | no |

## 八、设计 → W16-impl 映射

> Protected Area 判定依据 AGENTS.md：本设计**无 ORM 模型结构变更**（JSON 键方案的结构性收益，§5.2）；变更面为 Java 代码 + beans.xml + 新增凭证类型资源文件 + 文档。**`nop-integration-api` 新增共享解析支持与依赖属跨模块公共 API（`nop-*-api`）变更——plan-first 范畴**（W16-impl plan 内声明评审门）。

- **消费小节**：§三（引用点基线=变更输入清单）+ §四/§五/§六 全部 + §4.3 类型清单。
- **交付面**：
  - `nop-integration-api`：`nop-credential-api` compile 依赖 + 共享解析支持（优先级链/fail-closed/`@Nullable` provider/typeName 校验/值转换公共语义）+ `IntegrationErrors` 解析错误码。
  - 厂商发送器：`credentialId` 可选属性 + 解析接线（SMS×2 逐次发送期；Email×2 同型；SFTP 逐次操作期；Feishu start 期 + `FeishuCredentials` credentialId 字段 + 解析副本；OSS 构造期）+ `integration:<channelType>` 装配期登记（失败 WARN 不阻断）。
  - 配置键：config-bound 家族新增 `credentialId` 键（feishu/oss）。
  - `nop-metadata-service`：`nop-credential-api` compile 依赖 + `MetaDataSourceConnectionProcessor.buildDataSource` 单点解析（含空串视同缺失、必填字段对齐 §4.3）+ `testConnect` 结构化失败映射（仅凭证解析异常，§5.1.4）+ `bindCredential`/`unbindCredential` 管理动作对 + 批量迁移变体（§6.4 幂等反查 + per-row 事务）+ BizModel delete 钩子 unregister。
  - 凭证类型实例文件：§4.3 表 8 类型（`_vfs/nop/credential/types/`）。
  - **integration 迁移 runbook 文档**（§6.4——integration 侧唯一迁移机制，显式交付物）。
  - 测试面：优先级链三态（credentialId 空/有效/失效）、fail-closed 不回退、并存行忽略明文、空串 credentialId 视同缺失、必填性/错型/转换失败路径、bind/unbind/迁移幂等（中断重跑反查复用、无重复凭证）、14 消费点零改动回归（既有测试基线）。
- **Protected Area（plan-first）**：无 ORM/`nop-xdefs` 变更；`nop-integration-api` 跨模块公共 API 变更（共享解析支持 + 依赖，如上）；`nop-integration-api`/`nop-metadata-service` 模块 pom 变更（plan 内声明）；发送器新增属性为纯加法；新增 BizModel 管理动作属行为变更（plan 内声明回归影响）。
- **首发范围建议与批次收口约束**：第一批 = SMS 家族（tencent/yunpian——有 in-platform 消费链 `LoginServiceImpl`/`NopAuthUserBizModel` 可验证）+ metadata 数据源（DB 明文迁移目标）；Email/Feishu/OSS/SFTP 为同型扩展批次（§七#8/#9）。**批次收口硬约束**：W16-impl plan 必须显式裁定交付批次范围——若仅交付首批，扩展批次（Email/Feishu/OSS/SFTP 发送器接线 + feishu/oss 配置键）必须登记为 roadmap 显式新工作项（不得静默丢失；A3 收口审计以其登记为前提），全家族一次交付则无此需要。
- **不触碰**：`ICredentialProvider` SPI 签名；`cv1:` 密文格式；AR-02 校验链；`@sec:` 配置加密机制；`nop-auth` 消费方代码（`smsSender == null` 检查原样）。
