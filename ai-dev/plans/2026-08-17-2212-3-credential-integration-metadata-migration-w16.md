# W16-impl nop-integration / nop-metadata credentialId 深度迁移实现（首批：SMS 家族 + metadata 数据源）

> Plan Status: completed
> Mission: nop-credential-mfa
> Work Item: W16-impl（nop-integration / nop-metadata 深度迁移实现）——迁移二期组 impl 工作项（首批交付，批次裁定见下）
> Last Reviewed: 2026-08-17（draft review 两轮：首轮独立 fresh subagent 对抗审查 2 Blocker + 4 Major + 4 Minor（SPI 只读缺口→迁移支持 SPI 与 CredentialData.typeName 增量裁定/registerUsage live 行为冲突/name 长度唯一性/deleteByQuery/admin 审计载体等）全部处置，复审 9/10 RESOLVED + F10 部分残留引出 N1 Major（Out Of Scope 矛盾）+ N2/N3 Minor，三处一行级修复当场应用，reviewer 裁定"修完即可进入执行，无需再次全量复审"）
> Source: `ai-dev/design/nop-credential/03-integration-metadata-migration-design.md` §三（引用点基线）+ §四/§五/§六 全部 + §4.3 类型清单 + §八 W16-impl 映射（含批次收口硬约束）；roadmap W16-impl 条目
> Related: W16-design `2026-08-17-0447-3-integration-metadata-credential-migration-design.md`（设计收口 plan）；W7-successor `2026-08-13-1118-3-nop-ai-model-credential-runtime-consumption.md`（运行时消费先例链——`IAiModelCredentialResolver` fail-closed 语义模板）；W11-impl Part A/B（归属/RBAC 消费基座）；C1b（凭证库敏感动作标注先例）
> 执行顺序：接 W14/W15（`2026-08-17-2212-1`/`-2`）之后执行（组间交错策略：W16-design 依赖已满足，且 W15 落地后 email 家族扩展批次将获得真实消费链）

## Purpose

按设计 §四/§五/§六落地 credentialId 深度迁移**首批**：`nop-integration-api` 共享解析支持（优先级链 `credentialId > 静态值`、强 fail-closed 不静默回退、`@Nullable` provider、typeName 家族校验、值转换——公共语义单点）+ compile 依赖 `nop-credential-api`（api→api 零业务依赖边）；SMS 家族两发送器（`TencentSmsSender`/`YunpianSmsSender`）credentialId 可选属性 + 发送期惰性解析（整组生效：凭证字段集全量取凭证库，同名静态值忽略）+ `integration:<channelType>` 幂等登记；metadata 侧 `MetaDataSourceConnectionProcessor.buildDataSource` 单点解析（JSON 内 credentialId 键，**无 ORM/无 DDL**，14 消费点零改动）+ `bindCredential`/`unbindCredential` 管理动作对 + 批量迁移变体（幂等反查 + per-row 事务同事务清明文）+ delete 钩子 unregister；三个凭证类型实例文件（tencent-sms/yunpian-sms/jdbc-datasource）；integration 迁移 runbook 文档。既有部署零回归（credentialId 缺省空 = 现状路径）。

## 批次裁定（设计 §八 硬约束的 plan-first 裁定，起草时执行）

设计 §八："W16-impl plan 必须显式裁定交付批次范围——若仅交付首批，扩展批次（Email/Feishu/OSS/SFTP 发送器接线 + feishu/oss 配置键）必须登记为 roadmap 显式新工作项（不得静默丢失；A3 收口审计以其登记为前提）"。

**裁定：首批交付 = 共享解析支持 + SMS 家族（tencent-sms/yunpian-sms）+ metadata 数据源（jdbc-datasource）；扩展批次（Email×2/Feishu/OSS/SFTP 发送器接线 + feishu/oss credentialId 配置键 + 对应类型实例）登记为 roadmap 显式新工作项。**理由：(a) 全家族一次交付约 30 文件/600+ 行，超 roadmap 单 plan 规模两倍以上，必拆；(b) 首批有真实消费链与迁移目标——SMS 有 in-platform 消费方（`LoginServiceImpl`/`NopAuthUserBizModel` 注入 `ISmsSender`），metadata 是唯一 DB 明文迁移目标（`nop_meta_data_source.connectionConfig`）；(c) Email 家族在 W15 落地前无运行时消费方（设计 §3.2/§七#8——W15 执行后 email 消费链成立，扩展批次验证面更完整，故 W16 首批排 W15 之后执行）。**扩展批次 roadmap 登记为本 plan Phase 1 的 Decision 执行项**（登记内容：工作项名/范围/依赖；A3-audit 依赖链随之在 roadmap 补注）。

## SPI 增量裁定（draft review Blocker F1/F2 的解裁定，跨模块公共 API plan-first 由本 plan 承载）

设计 §八"不触碰"清单冻结的是 **`ICredentialProvider` 六方法签名**——该约束保持（六方法零变更）。但 draft review 核实：`nop-credential-api` 现为**纯只读 SPI**（`CredentialData` 仅 fields map，无 typeName；无凭证创建/按名查询/按 consumerRef 查询通道），而本 plan 的两类承诺在 api-only 边下无实现通道：

1. **错型校验（设计 §4.3 契约 2）**：消费方经 `getCredential(credentialId)` 拿到的 `CredentialData` 不携带凭证实例的 typeName——"yunpian 凭证用于 tencent-sms"只能靠字段缺失间接失败（对设计的静默漂移）。**裁定：`CredentialData` 增加可选 `typeName` 属性**（additive 数据类增量，向后兼容；实现侧 `CredentialProviderImpl.getCredential` 从凭证实例行填充）——错型校验精确实现，`ICredentialProvider` 签名零变更。
2. **批量迁移的创建/反查（设计 §6.4）**："缺失则创建凭证（经 saveCredential 语义）+ 按确定性名反查 + NopCredentialUsage consumerRef 反查"三能力均不在 api。**裁定：`nop-credential-api` 新增迁移支持 SPI**（接口名执行期定稿，如 `ICredentialMigrationSupport`：按名查凭证 / 按 consumerRef 查已引用 credentialId / 创建凭证（内部复用 saveCredential 语义：加密 + scope=system + 审计）；实现 bean 落 `nop-credential-service` 并经 beans 注册）——api→api 边保持干净，credential 模块获得本 plan 的真实代码增量（原"纯资源新增"表述废止）。

两项增量均属跨模块公共 API 变更（plan-first 范畴，评审门 = 本 plan 的 draft review + 设计回写）；设计 §4.1/§4.3/§6.4/§八"不触碰"清单随执行回写修正。

## Current Baseline

（2026-08-17 live repo 核对；引用点行号以设计 §三 2026-08-17 逐点复核为基线，起草时抽验）

- **凭证库消费基座已就绪**（W1-W3/W11 done）：`ICredentialProvider`（`nop-credential/nop-credential-api/.../ICredentialProvider.java:20`；`getCredential(credentialId): CredentialData` :29 / `getCredentialData(credentialId, field)` :39 / testCredential/mask/registerUsage/unregisterUsage 六方法）；`CredentialData`（api 内数据类，**仅 fields map、无 typeName**——本 plan SPI 增量裁定 1 的输入）；实现 `CredentialProviderImpl`（nop-credential-service，唯一明文出口——归属 + RBAC §6.3 判定矩阵串联于出口，消费侧零授权代码）；类型实例文件 `_vfs/nop/credential/types/` 现有 3 个（generic-secret/generic-oauth2/openai-api-key——类型加载机制 = `DefaultCredentialTypeRegistry` `@PostConstruct` 枚举 VFS 目录（credential-defaults.beans.xml 注释在案），非平台 register-model xdef 机制）；**部署装配机制事实（draft review F10 更正）**：repo 内无消费模块 import `credential-defaults.beans.xml`——实际机制是模块 `app-service.beans.xml` 聚合装载（nop-ai-service 先例即此形态），runbook 措辞以此为准。
- **`registerUsage` live 行为（draft review F3 更正，与设计 §4.1 结论 8 表述冲突——设计回写项）**：`CredentialProviderImpl.registerUsage`（live:196-208，C1a D6-03 修复）**前置校验凭证存在且未软删**——配错 credentialId 抛 `ERR_CREDENTIAL_NOT_FOUND`、软删抛 `ERR_CREDENTIAL_DELETED`（设计"不校验凭证存在性"的断言已过时）。因此发送器 bean 初始化登记的 catch 必须为 **catch-all WARN**（含 provider 校验抛错，非仅 DB 异常），否则配错 credentialId 将阻断应用启动。
- **`NopCredential` name 列与唯一性事实（draft review F4）**：`name` VARCHAR(100)（orm.xml:26 `CREDENTIAL_NAME`）且**无 name 唯一约束**（唯一键仅在 `NopCredentialUsage (credentialId, consumerRef)` orm.xml:117-120 与 `NopCredentialAuth`）——迁移幂等反查**主源必须是 consumerRef**（有唯一约束），名称仅辅助展示（组合名 `jdbc-datasource:{querySpace}/{name}` 上界可超 100 字符——需截断+短哈希后缀策略；name 非唯一可接受，身份由 consumerRef 承载）。
- **nop-ai 先例链（语义模板）**：`IAiModelCredentialResolver`（nop-ai-api）→ `AiModelCredentialResolverImpl`（nop-ai-service；credentialId 非空且解析失败强 fail-closed）→ `ChatServiceImpl` 钩点；`nop-ai-service` compile 依赖 `nop-credential-api` + `@Nullable` 注入（部署无凭证库为 null）——metadata 侧同构 api-only 边。
- **integration 现状**：`nop-integration-api` pom 仅依赖 `nop-api-core`（**无 credential 依赖**——本 plan 新增边）；`IntegrationErrors` 在（`nop-integration-api/.../IntegrationErrors.java`，现有 `ERR_SEND_SMS_FAIL` 等——解析错误码追加于此）；SMS 家族：`TencentSmsSender`（`nop-integration/nop-integration-sms-tencent/src/main/java/io/nop/integration/sms/TencentSmsSender.java`，appId/appKey/sign setter，`new SmsSingleSender(appId, appKey)` 逐次发送）+ `YunpianSmsSender`（`.../sms-yunpian/.../YunpianSmsSender.java`，apiKey，`new YunpianClient(apiKey).init()` 逐次发送）；装配值全在消费方应用 beans.xml（repo 内无装配值，测试侧仅 mock ISmsSender）。
- **in-platform SMS 消费方（零改动回归面）**：`LoginServiceImpl`（`@Inject @Nullable ISmsSender` + `smsSender == null` fail-closed 检查）、`NopAuthUserBizModel`（同型注入）——**本 plan 不触碰 nop-auth 消费方代码**。
- **metadata 现状**：实体 `NopMetaDataSource`（`nop-metadata/model/nop-metadata.orm.xml:377-429`；密码在 CONNECTION_CONFIG `json-4000` tagSet="sensitive" 列内明文 JSON，无独立 PASSWORD 列；live xmeta 将 connectionConfig 设 `published=false insertable=false updatable=false queryable=false`——通用 CRUD 面无 credentialId 合法写入通道，管理动作是唯一受控入口）；**单点汇聚** `MetaDataSourceConnectionProcessor`（`nop-metadata/nop-metadata-service/.../connection/MetaDataSourceConnectionProcessor.java`：`withConnection` :123-137 与 `testConnect` :139-164 收敛 `buildDataSource` :173-197——password 提取/requireField/AR-02 校验链/SimpleDataSource 建连；**同文件内 per-row REQUIRES_NEW 事务先例**：`upsertExternalTableGuarded` :551-562）；14 读点/9 文件全部经 `connectionService.withConnection(...)` 传递 JSON 字符串（引用随配置自包含传输，零改动）；`NopMetaDataSourceBizModel`（CrudBizModel 子类，`testConnection` @BizMutation :117-118 等，**当前无 delete 覆写**）；`nop-metadata-service` pom 无 credential 依赖（本 plan 新增）；BizModel delete 钩子先例 = `NopAiModelBizModel`（live:137-159——**该先例同时覆盖 `delete` 与 `deleteByQuery`**：基类批量路径经 doDeleteByQuery → doDeleteMulti → doDelete 不经虚分派，漏覆写即 usage 引用残留）；**nop-metadata 无 requireAdmin/saveAudit 先例**（grep 核实 main 代码零命中——admin 判定与审计载体需本 plan 裁定，见 Phase 2 Decision 项）。
- **既有安全缓解（保持不动）**：`@sec:` 配置加密（DefaultConfigValueEnhancer）；metadata AR-02 全链（`validateJdbcUrl` 协议白名单/危险参数黑名单/内网主机白名单 + `validateDriverClassName`）；URL 凭据脱敏 `redactJdbcUrl` + 事件敏感列快照脱敏（MetaModelChangedEventPublisher 硬编码兜底含 connectionConfig/password）。
- **横切契约（设计 §六，直接消费）**：优先级链（credentialId 空或空白=静态值现状路径；非空→provider 解析整组，失败 fail-closed 中止不回退）；provider null + credentialId 非空 = 部署不一致 fail-closed；解析契约三条（必填字段缺失/空值 fail-closed、typeName 家族错型 fail-closed（经 SPI 增量裁定 1 的 CredentialData.typeName 实现）、值转换失败 fail-closed）；consumerRef：`integration:tencent-sms`/`integration:yunpian-sms`（bean 初始化幂等登记，**catch-all WARN 不阻断启动**——含 provider 前置校验抛错，见 Current Baseline registerUsage 行为事实）/`metadata:NopMetaDataSource:<dataSourceId>`（bind/迁移登记、unbind/换绑/行删除解除）；归属一律 system 级；拒绝全局明文降级开关；迁移 = 幂等反查（**主源 = NopCredentialUsage consumerRef（唯一约束）；名称辅助（截断+短哈希后缀，name 无唯一约束可接受）**；反查命中软删凭证 → 该行计入失败清单供人工处置，不跳过不重建（避免同 consumerRef 双凭证歧义））+ per-row 事务（credentialId 写入与明文清除同事务原子；创建/反查通道经 SPI 增量裁定 2）。
- **测试基线**：nop-metadata 既有 14 读点回归测试族（federated/aggregation/quality 等全套——消费点零改动回归的基线）；nop-integration 模块测试基建薄弱（发送器为纯 JavaBean，测试在消费方或新增模块内测试）。

## Goals

- **共享解析支持**（`nop-integration-api`，公共语义单点）：优先级链判定 + fail-closed 语义（provider null/凭证缺失/软删/必填字段空/错型/转换失败 → 抛错，不回退静态值）+ `@Nullable ICredentialProvider` 注入语义 + 家族允许集 typeName 校验（经 `CredentialData.typeName`——SPI 增量裁定 1）+ 字段值字符串归一与目标类型转换 + 空串/空白 credentialId 视同缺失；`IntegrationErrors` 追加解析错误码（部署不一致/解析失败/错型/必填缺失）。
- **SPI 增量**（`nop-credential-api` + `nop-credential-service`，见"SPI 增量裁定"节）：`CredentialData` 可选 typeName 属性 + 迁移支持 SPI（按名查/consumerRef 查/创建凭证（saveCredential 语义））+ 实现 bean 注册。
- **依赖边**：`nop-integration-api` + `nop-metadata-service` 各新增 `nop-credential-api` compile 依赖（api→api / api-only 边，version-less 同 C1b `nop-biz-auth-api` 先例；`dependency:tree` 核实无 service/dao 传递引入）。
- **SMS 家族接线**：`TencentSmsSender`/`YunpianSmsSender` 新增可选 `credentialId` 属性 + `@Nullable ICredentialProvider` setter/protected 注入（NopIoC XML bean `autowireProps` 按类型装配，`@Nullable` 即可选——W16-design review 已核实 `BeanDefinitionBuilder.autowireProps`）+ 发送期解析（**两发送器各双构造点**：tencent sendMessage :47 + sendMultiMessage :91、yunpian :40/:51——全部接线，漏一即批量路径静默用静态值）+ credentialId 非空 → 整组取 `tencent-sms`（appId/appKey/sign）或 `yunpian-sms`（apiKey）字段集，同名静态值忽略；解析失败 fail-closed 显式抛 IntegrationErrors 错误，不吞成"发送失败"日志 + bean 初始化幂等 `registerUsage("integration:tencent-sms"/"integration:yunpian-sms")`（credentialId 非空且 provider 装配时；**catch-all WARN**——含 provider 前置校验抛错（credentialId 配错/软删），防阻断启动）。
- **凭证类型实例**：`tencent-sms`（appId 必填/appKey 必填 sensitive/sign 可空）、`yunpian-sms`（apiKey 必填 sensitive）、`jdbc-datasource`（username 必填/password 可空 sensitive——必填性=消费方现状语义不收紧）三实例文件落 `_vfs/nop/credential/types/`（字段 schema 即 typeList 动态表单输入）。
- **metadata 单点解析**：`buildDataSource` JSON 解析后含非空 credentialId → provider 取 `{username, password}` 合并进 cfg map → 既有 `requireNonBlank`/`requireField`/AR-02 校验/SimpleDataSource 建连全链零变化；provider null + credentialId 非空 = 部署不一致 fail-closed；`testConnect` catch 精确化（仅凭证解析异常映射 `{connected:false, error:"credential resolution failed"}` 固定描述——不携带明文/密文细节；AR-02 与 config-invalid 异常维持上抛不吞）。
- **管理动作对**：`NopMetaDataSourceBizModel.bindCredential(dataSourceId, credentialId)`（admin；JSON 置键 + 清除 username/password 明文 + `registerUsage`，同一行级事务）+ `unbindCredential(dataSourceId)`（清键 + `unregisterUsage`；明文需另行重录，不自动复活死值）+ 换绑 A→B（bind 内 unregister 旧 + register 新）+ **行删除钩子 unregister 覆盖 `delete` 与 `deleteByQuery` 双路径**（`metadata:NopMetaDataSource:<dataSourceId>`——NopAiModelBizModel live:137-159 先例明确批量路径不经虚分派，漏覆写即引用残留）。
- **批量迁移变体**：逐行幂等反查（**主源 = NopCredentialUsage consumerRef 唯一约束**；名称辅助 = `jdbc-datasource:{querySpace}/{name}` 截断+短哈希后缀适配 VARCHAR(100)；反查命中软删凭证计入失败清单不重建）→ 缺失则经迁移支持 SPI 创建凭证（username/password 取自 JSON；scope=system）→ registerUsage → JSON 置键并清除明文——四步同一行级事务（REQUIRES_NEW 或等价——**同文件 `upsertExternalTableGuarded` :551-562 先例**）；`orderBy dataSourceId` 确定性排序 + 中断重跑经反查收敛（无孤儿/无重复凭证）；返回摘要（成功/跳过（已迁移）/失败计数）。
- **runbook + 文档**：integration 迁移 runbook（设 credentialId 属性 → 验证发送 → 清除静态密钥；回滚 = 引用级清除回静态路径，明文清除后回滚需显式再录入）+ `docs-for-ai/03-modules/nop-credential.md` 迁移章节扩展 + `nop-metadata.md` 数据源凭证节 + 设计 03 号文 impl 裁定回写 + roadmap（W16-impl done + **扩展批次新工作项登记确认**）。
- **测试面**：优先级链三态（credentialId 空=静态值零回归/有效=整组凭证/失效=fail-closed 不回退静态值）；错型/必填缺失/转换失败路径；空串 credentialId 视同缺失；metadata 兼容矩阵三行（存量明文行零回归/迁移行/过渡并存行——凭证侧整组生效 JSON 明文忽略 + 解析失败 fail-closed 不回退明文）；bind/unbind/迁移幂等（中断重跑反查复用、无重复凭证）+ 同事务明文清除原子性（中断后无"已引用但仍留明文"中间态）；14 消费点零改动回归（既有测试基线）；testConnect 结构化失败不泄漏细节。

## Non-Goals

- 扩展批次家族接线：Email×2（tencent-email/smtp-email）/Feishu（feishu-app + `nop.integration.feishu.credentialId` 配置键 + start 期解析副本）/OSS（oss-s3 + `nop.integration.oss.credentialId` + 构造期解析）/SFTP（sftp-ssh）——**登记为 roadmap 显式新工作项（Phase 1 Decision 项），非静默丢失**。
- 飞书 ChannelConfig options 面 credentialId（设计 §七#10 out-of-scope）+ token 刷新期再解析（§七#1 optimization candidate）+ OSS 热重建（§七#2）。
- 集成配置 DB 实体 + Web 渠道管理面（设计 §4.2 候选 A 拒绝，§七#3）。
- `@sec:` 静态加密机制变更（互补基线保留，§4.1 结论 3）；`@credential:` 配置 resolver（一期拒绝不翻案）。
- **`ICredentialProvider` 六方法 SPI 签名任何变更**（冻结保持——SPI 增量裁定 1/2 是 api 模块的 additive 增量（CredentialData 可选属性 + 新独立接口），不触碰既有接口签名；`cv1:` 密文格式不变）；AR-02 校验链；`nop-auth` 消费方代码（`smsSender == null` 检查原样）。
- user 级凭证用于渠道/数据源（§6.3 一律 system 级）；全局明文降级开关（§6.2 拒绝）。
- 新类型 `testCredential` 真实连通性实现（§七#6 optimization candidate）。
- A3-audit；xmeta/view 编辑面放开（§七#11）。

## Scope

### In Scope

- `nop-integration/nop-integration-api`：pom（nop-credential-api compile 依赖）+ 共享解析支持（新类/类族）+ `IntegrationErrors` 解析错误码；**pom 测试依赖新增**（junit/mockito 或平台 autotest——三模块现状无 test 依赖无 src/test，Phase 1 Proof 前置）。
- `nop-integration/nop-integration-sms-tencent` / `nop-integration-sms-yunpian`：`TencentSmsSender`/`YunpianSmsSender` credentialId 属性 + provider 注入 + 发送期解析（双构造点）+ registerUsage；测试基建与用例。
- `nop-credential/nop-credential-api`：`CredentialData` 可选 typeName 属性 + 迁移支持 SPI 接口（SPI 增量裁定 1/2）。
- `nop-credential/nop-credential-service`：SPI 实现 bean + beans 注册 + `_vfs/nop/credential/types/` 三新类型实例文件。
- `nop-metadata/nop-metadata-service`：pom（nop-credential-api compile 依赖）+ `MetaDataSourceConnectionProcessor` 单点解析 + testConnect catch 精确化 + `NopMetaDataSourceBizModel` bindCredential/unbindCredential/批量迁移变体 + delete/deleteByQuery 钩子。
- roadmap：扩展批次新工作项登记（Phase 1 Decision 执行项）。
- 测试：上述全部 + 14 消费点零改动回归。
- `docs-for-ai/03-modules/nop-credential.md`、`docs-for-ai/03-modules/nop-metadata.md`、runbook（归属裁定见 Phase 3）、设计 03 号文回写、roadmap、日志。

### Out Of Scope

- 扩展批次五家族接线与配置键（新工作项）；nop-auth/nop-ai 消费方代码；**凭证库 service/web 既有代码（本 plan SPI 增量实现 bean 与类型实例文件除外——见"SPI 增量裁定"节）**；前端。

## Execution Plan

### Phase 1 - 共享解析支持 + 依赖边 + SMS 家族接线 + 批次登记

Status: completed
Targets: `nop-integration/nop-integration-api`（pom + 共享解析支持 + IntegrationErrors + 测试依赖）、`nop-integration-sms-tencent`、`nop-integration-sms-yunpian`、`nop-credential/nop-credential-api`（CredentialData typeName + 迁移 SPI 接口）、`nop-credential/nop-credential-service`（SPI 实现 + beans + 两类型实例）、roadmap

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（批次范围登记，设计 §八 硬约束）**：在 roadmap 迁移二期组登记扩展批次显式新工作项（建议名 `W16-impl-ext`：Email×2/Feishu/OSS/SFTP 发送器接线 + feishu/oss credentialId 配置键 + 对应类型实例文件；依赖 = W16-impl done + W15-impl done（email 消费链））；同步在 roadmap A3-audit 依赖注记扩展批次登记前提（A3 收口审计以其登记为前提，非其完成为前提——设计原文语义）。登记动作本身可复核（roadmap Work Items 块新增行）。（执行核验：roadmap Work Items 块 `W16-impl-ext` 行与 A3-audit 依赖注记已于 draft 轮登记在案——`ai-dev/backlog/nop-credential-mfa-roadmap.md` W16-impl-ext 行 + A3-audit 行"登记为 A3 收口前提（非其完成）"，本 Phase 核验可复核后勾选）
- [x] **Fix（SPI 增量，见"SPI 增量裁定"节）**：`CredentialData` 增加可选 `typeName` 属性（实现侧 `CredentialProviderImpl.getCredential` 从凭实行填充；既有消费方零感知——可选属性向后兼容）+ 迁移支持 SPI 接口（`findCredentialByName` / `findCredentialIdByConsumerRef` / `createCredential`（复用 saveCredential 语义：加密 + scope=system + 审计））落 `nop-credential-api`，实现 bean 落 `nop-credential-service` 并 beans 注册（NopIoC 显式 XML）。设计 §4.1/§4.3/§6.4/§八"不触碰"清单回写项随 Phase 3 执行。（落地：`CredentialData` 双构造器 + `ICredentialMigrationSupport` + `CredentialLookup` DTO（含 deleted 标记承载"软删命中供人工处置"语义）；`CredentialProviderImpl.getCredential` 填充 typeName；`CredentialMigrationSupportImpl`（createCredential 委托 `NopCredentialBizModel.saveCredential` 单点复用）注册于 `credential-defaults.beans.xml`（bean id `nopCredentialMigrationSupport`））
- [x] **Fix**：`nop-integration-api` pom 新增 `nop-credential-api` compile 依赖（api→api 零业务依赖边；`./mvnw dependency:tree -pl nop-integration/nop-integration-api` 核实无 credential-service/dao 传递引入）+ 三模块 pom 测试依赖新增（junit/mockito 或平台 autotest——现状零 test 基建）。（dependency:tree 证据：`nop-credential-api:jar:compile` 下仅 `nop-commons`，无 service/dao；测试依赖 = api 模块 junit-jupiter、tencent junit-jupiter、yunpian junit-jupiter + mockito-core，均 test scope）
- [x] **Fix**：共享解析支持（公共语义单点，命名执行期定稿如 `CredentialResolutionSupport`——静态工具或轻量 bean 形态执行期定稿，发送器可达性以 NopIoC `autowireProps` 前提成立）：(a) 优先级链判定（credentialId 空/空白 → 静态值调用方自用；非空 → 解析路径）；(b) provider null + credentialId 非空 → 部署不一致 fail-closed 错误；(c) 整组解析（`getCredential` 后按字段 schema 取字段集）+ **typeName 家族允许集校验（经 `CredentialData.typeName`**——错型 fail-closed 精确判定，设计 §4.3 契约 2 完整落地）+ 值字符串归一与目标类型转换（失败 fail-closed）；(d) 必填字段缺失/空值 fail-closed（可空字段空值合法——对齐 §4.3 必填性列）。错误全部经 `IntegrationErrors` 新码（不吞、不静默回退）。（落地：静态工具 `io.nop.integration.api.credential.CredentialResolutionSupport`——`isConfigured`/`resolveGroup`/`requireString`/`optionalString`/`requireInteger`；IntegrationErrors 新增五码：provider-not-configured / resolve-failed（包装 provider 侧 NopException 且 cause 保留）/ type-mismatch / field-required / field-convert-failed）
- [x] **Fix**：`TencentSmsSender`/`YunpianSmsSender`：可选 `credentialId` bean 属性 + `@Nullable ICredentialProvider` setter/protected 注入 + 发送路径接线（**双构造点全覆盖**：tencent sendMessage + sendMultiMessage、yunpian 两处 client 构造——credentialId 非空 → 共享支持解析 `tencent-sms`/`yunpian-sms` 字段集整组覆盖 appId/appKey/sign 或 apiKey → 现有构造 sender/client 逻辑不变；解析失败 fail-closed 抛错中止本次发送）+ bean 初始化幂等 `registerUsage`（credentialId 非空且 provider 非空；重复启动无副作用；**catch-all WARN 不阻断启动**——含 provider 前置校验抛错（credentialId 配错/软删），初始化钩子形态经 `@PostConstruct`（NopIoC 支持，DefaultBeanClassIntrospection 先例）或 ioc:method init 执行期定稿）。（落地：两发送器 `@PostConstruct init()` → `registerUsageQuietly()` catch-all；构造点抽 `protected createSender(ResolvedCredential)` / `protected createClient(String apiKey)` seam——双路径共用，sign 随凭证组传递）
- [x] **Fix**：凭证类型实例 `tencent-sms`/`yunpian-sms`.credential-type.xml（§4.3 表字段与必填性/sensitive 标记；同目录 generic-secret 实例文件为结构模板——加载机制 = `DefaultCredentialTypeRegistry` 枚举 VFS 目录，非平台 register-model xdef 机制，见 Current Baseline）。
- [x] **Proof**：单测——优先级链三态（空=静态值直用零变化/有效=整组覆盖且同名静态值忽略/失效（凭证缺失/软删/disabled）=fail-closed 中止不回退）；错型（credentialId 指向 yunpian-sms 凭证用于 tencent-sms，经 typeName 判定）拒绝；必填缺失/转换失败拒绝；空串/空白 credentialId 视同缺失；**sendMultiMessage/双构造点路径同断言**（单发 + 批发各自三态抽查）；registerUsage 幂等 + **catch-all WARN**（provider 校验抛错不阻断初始化——专项用例：配错 credentialId 的 bean 初始化存活）；nop-auth SMS 消费链回归（app-test.beans.xml mock ISmsSender 注入零变化，`TestMfaLoginE2E` 家族绿）。（测试类：`TestCredentialResolutionSupport`（integration-api，11 用例）、`TestTencentSmsSenderCredential`（11 用例）、`TestYunpianSmsSenderCredential`（12 用例）、`TestCredentialMigrationSupport`（credential-service，10 用例——typeName round-trip + 迁移 SPI 创建/反查含软删 deleted=true）；nop-auth 回归：TestMfaLoginE2E 10 + TestWebAuthnMfaE2E 12 + TestMfaUserSelfService 15 全绿）

Exit Criteria:

- [x] roadmap 扩展批次工作项已登记（Work Items 块新增行可复核，含依赖与范围描述）。
- [x] `./mvnw clean install -pl nop-integration/nop-integration-api,nop-integration/nop-integration-sms-tencent,nop-integration/nop-integration-sms-yunpian,nop-credential/nop-credential-api,nop-credential/nop-credential-service -am -DskipTests -T 1C` 编译绿；dependency:tree 无 service/dao 传递引入证据。
- [x] SPI 增量编译与装配绿（CredentialData.typeName 填充 + 迁移 SPI 实现 bean 注册可解析）；**新功能测试**：列出 Phase 1 测试类与用例名（含 SPI 增量单测——typeName 填充 round-trip + 迁移 SPI 创建/反查）。（`TestCredentialMigrationSupport.getCredentialFillsTypeName` + createCredentialEncryptsAndScopesSystem/findCredentialByName*/findCredentialIdByConsumerRef* 共 10 用例；装配经容器 `@Inject ICredentialMigrationSupport` 解析成功）
- [x] **接线验证**：发送器在 credentialId 非空路径实际调用共享解析支持与 provider（mock provider 断言调用与整组覆盖）；registerUsage 实际登记（FakeNosql/单测 dao 断言）。（FakeProvider 记录 getCredentialCalls/registerUsageCalls——sendMessageEntryUsesResolvedCredentialGroup / sendMultiMessageEntryUsesResolvedCredentialGroup / registerUsageOnInitWhenCredentialConfigured（credential-service 容器版经真实 dao 落行断言））
- [x] **无静默跳过**：解析失败/provider 缺失/错型各分支显式抛 IntegrationErrors 错误（专项用例）；无 catch-吞异常。（failedResolutionFailsClosedWithoutFallback / providerMissingWithCredentialIdFailsClosedAsDeployInconsistency / wrongCredentialTypeRejected；唯一 catch（registerUsageQuietly）为 catch-all WARN 日志显式留证，非吞）
- [x] **新功能测试**：列出 Phase 1 测试类与用例名。（见上 Proof 项）
- [x] 文档裁定：No owner-doc update required（runbook 与章节统一 Phase 3）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - metadata 单点解析 + 管理动作对 + 批量迁移 + 类型实例

Status: completed
Targets: `nop-metadata/nop-metadata-service`（pom + MetaDataSourceConnectionProcessor + NopMetaDataSourceBizModel）、`nop-credential/nop-credential-service` 的 `_vfs/nop/credential/types/`（jdbc-datasource）

- Item Types: `Fix | Decision | Proof`

- [x] **Fix**：`nop-metadata-service` pom 新增 `nop-credential-api` compile 依赖（与 `nop-ai-service` 同构 api-only 边；dependency:tree 核实）。（证据：`nop-credential-api:jar:compile` 单行，无 service/dao 传递）
- [x] **Decision（admin 判定与审计载体，draft review F6——nop-metadata 无 requireAdmin/saveAudit 先例，grep 核实零命中）**：admin 判定 = 运行时角色校验（`IUserContext`（nop-api-core 可达）roles 比对——角色集来源复用 `nop.credential.admin-roles` 配置惯例或等价最小实现，执行期定稿回写设计 §5.1.5；**禁止**为 admin 判定引入 nop-auth 依赖边）；**审计载体按设计 §6.4 原文**：实体变更事件（`MetaModelChangedEventPublisher`——bind/unbind 经 ORM 行更新自动触发，敏感列快照脱敏覆盖 connectionConfig）+ 凭证侧审计（registerUsage/unregisterUsage/lastUsedAt），**不引入 IAuditService**（nop-metadata 无该基建；设计审计行未要求）。（执行期定稿：(a) 配置键 = `nop.metadata.credential-admin-roles`（缺省 `admin,nop-admin`，等价最小实现）；无登录态（内部调用）放行——与 nop-credential 写分级"无登录态内部调用放行"同口径，生产 GraphQL 入口另由 action-auth 管角色（第二层）；(b) 审计事件经 `publishCredentialEvent` 手写发布（changeSource=`credential-bind`，快照脱敏自动覆盖 connectionConfig——live 事实：MetaModelChangedEventPublisher 为手动调用型发布器、无 ORM 实体监听器自动触发，设计"自动触发"表述按 live 机制以等价手动发布兑现，Phase 3 回写设计）
- [x] **Fix**：`MetaDataSourceConnectionProcessor.buildDataSource`（live:173-197）单点解析：JSON 解析后含非空 credentialId（空串/空白视同缺失）→ `@Nullable ICredentialProvider` 解析 `jdbc-datasource` 字段集（username/password）合并进 cfg map → 既有 requireField/AR-02 校验/SimpleDataSource 建连零变化；provider null + credentialId 非空 = 部署不一致 fail-closed；解析失败（缺失/软删/必填空/错型/归属或授权拒绝）fail-closed 不回退 JSON 明文（过渡并存行语义）；**14 消费点零改动**（引用随 getConnectionConfig() 字符串自包含传输——以 git diff 核实仅 processor/bizmodel/pom 变更）。（落地：`mergeCredentialConfig(cfg, datasourceType)`——typeName 错型（含 null）拒绝、username 必填（对齐 requireNonBlank）、password 键恒存在（空值合法对齐 requireField）；git diff 证据：nop-metadata 变更文件 = pom + DataSourceErrors + NopMetadataArgs + processor + bizmodel 五文件，消费点文件零触碰）
- [x] **Fix**：`testConnect` catch 精确化：**窄域 try 仅包住 provider 解析调用**（provider 侧任何 `NopException`——缺失/软删/解密失败/归属授权拒绝——包装为 metadata 自身 `NopMetadataErrors` 凭证解析失败码后映射 `{connected:false, error:"credential resolution failed"}` 结构化返回（固定描述，不携带明文/密文/credentialId 以外细节；白名单 = 自身包装码集，不跨模块比对 credential 错误码常量）；AR-02 与 config-invalid 异常**不在 try 域内**、维持上抛（不吞成结构化 false）。（实现形态：testConnect 对 buildDataSource 调用的 catch 按错误码白名单精确过滤——仅 `ERR_DATASOURCE_CREDENTIAL_RESOLVE_FAILED` 映射结构化返回，provider-not-available/type-mismatch/field-required/AR-02/config-invalid 全部上抛；专项用例 testConnectStillThrowsAr02Violations/testConnectStillThrowsConfigInvalid/testConnectThrowsProviderNotAvailableInsteadOfStructuredFalse/testConnectMapsCredentialFailureToStructuredResultWithoutDetailLeak）
- [x] **Fix**：`NopMetaDataSourceBizModel.bindCredential(dataSourceId, credentialId)` / `unbindCredential(dataSourceId)`（admin 判定按上方 Decision 项；bind = 置 JSON 键 + 清除 username/password 明文 + registerUsage **同一行级事务**；unbind = 清键 + unregister；换绑 = bind 内 unregister 旧 + register 新；unbind 不自动复活明文死值）+ **行删除钩子 unregister 覆盖 `delete` 与 `deleteByQuery` 双路径**（`metadata:NopMetaDataSource:<dataSourceId>`——NopAiModelBizModel live:137-159 先例：批量路径 doDeleteByQuery → doDeleteMulti → doDelete 不经虚分派，双覆写防 usage 引用残留）+ 审计按 Decision 项（实体变更事件自动覆盖 + 凭证侧审计）。（bind 顺序裁定：registerUsage 先行——D6-03 前置校验凭证存在/未软删失败即中止、行未被触碰，无中间态；软删凭证 bind 失败专项用例 bindCredentialFailureLeavesRowUntouched 断言明文存活）
- [x] **Fix**：批量迁移变体（管理员 mutation）：逐行幂等反查（**主源 = `findCredentialIdByConsumerRef`（NopCredentialUsage 唯一约束）**；名称辅助 = `jdbc-datasource:{querySpace}/{name}` 截断+短哈希后缀适配 VARCHAR(100)（name 无唯一约束，身份由 consumerRef 承载）；反查命中软删凭证 → 该行计入失败清单供人工处置，不跳过不重建）→ 缺失则经迁移支持 SPI `createCredential`（username/password 取自 JSON；scope=system；加密复用 saveCredential 语义）→ registerUsage → JSON 置键清明文——四步同一行级事务（REQUIRES_NEW 或等价——同文件 `upsertExternalTableGuarded` :551-562 先例）；`orderBy dataSourceId` + 中断重跑收敛；返回摘要（成功/跳过（已迁移）/失败（含软删命中）计数）。（落地：`migrateDataSourcesCredential`（Map 摘要 migrated/skipped/failed + failures 清单）+ `migrateOneRow`（行级事务内重读行——外层实体 attached 外层会话，重读副本随 REQUIRES_NEW 提交，upsertExternalTableGuarded 先例语义；username 缺失行计入失败（对齐 requireNonBlank 现状）；单行失败收集不中断整批）
- [x] **Fix**：凭证类型实例 `jdbc-datasource`.credential-type.xml（username 必填/password 可空 sensitive——必填性对齐 requireField 现状不收紧）。
- [x] **Proof**：测试——**测试装配声明（Phase 2 前置）**：nop-metadata 测试环境经 test-scope 装配凭证 SPI（test 依赖 nop-credential-service 或 fake 迁移 SPI + mock provider 组合，执行期按最小装配定稿——api-only 门仅约束 compile scope，test-scope 有既有先例）。（执行期定稿：**手写 fake 双 SPI**（`FakeCredentialVault` 同时实现 ICredentialProvider + ICredentialMigrationSupport，内存凭证/usage 模型对齐生产语义——registerUsage 前置校验+幂等、软删 deleted=true、consumerRef 反查）经 setter 注入容器 BizModel——最小装配、零新 test 依赖；processor 测试 plain JUnit5 直构注入 fake provider，沿 TestMetaDataSourceConnectionSecurity 形态）兼容矩阵三行（存量明文行（无键）走现状路径零回归/迁移行（明文已清）整组取凭证/过渡并存行（明文+键并存）凭证侧生效 JSON 明文忽略 + 解析失败 fail-closed 不回退）；bind/unbind 幂等与换绑；批量迁移幂等（预置 3 行迁移 → 中断（第 2 行注入失败）→ 重跑 → 无重复凭证（consumerRef 反查复用断言）+ 全部行明文已清 + credentialId 键在）；**长名数据源（querySpace/name 拼接超 100 字符）迁移成功**（截断+哈希策略用例）；同事务原子性（bind 失败时明文不清除——无中间落盘态断言）；**delete 与 deleteByQuery 双路径 unregister 断言**；14 消费点零改动回归（nop-metadata 既有测试族全绿 + git diff 消费点文件为零）；testConnect 结构化失败（不泄漏细节，授权拒绝同映射）+ AR-02 异常仍上抛。（测试类：`TestMetaDataSourceConnectionProcessorCredential`（13 用例：legacyPlaintextRowWithoutCredentialIdUsesStaticPath / blankCredentialIdTreatedAsMissing / migratedRowResolvesCredentialEndToEnd / transitionRowCredentialWinsOverJsonPlaintext / transitionRowFailClosedWithoutFallbackToPlaintext（error 描述判别回退 vs fail-closed）/ providerFailureFailsClosedViaWithConnection / providerMissingWithCredentialIdFailsClosedAsDeployInconsistency / typeMismatchFailsClosed / requiredUsernameMissingFailsClosed / testConnectMapsCredentialFailureToStructuredResultWithoutDetailLeak / testConnectStillThrowsAr02Violations / testConnectStillThrowsConfigInvalid / testConnectThrowsProviderNotAvailableInsteadOfStructuredFalse）+ `TestNopMetaDataSourceBizModelCredential`（16 用例：bind 三态/幂等/换绑/软删失败原子性/admin 正负例/unbind 幂等不复活明文/delete 与 deleteByQuery 双路径/迁移全量+重跑幂等+中断收敛（3 用例）/软删命中失败清单/长名截断哈希（2 用例））；既有测试族回归：nop-metadata-service 全量 **1077 tests 0 failures**）

Exit Criteria:

- [x] **端到端验证**：创建数据源（明文）→ 批量迁移（或单行 bindCredential）→ 明文清除 + credentialId 键写入 → `withConnection` 建连消费走凭证解析路径（mock/嵌入式 DB 全链）→ testConnect 同路径验证 → unbindCredential 回滚静态路径（明文需重录语义断言）。（跨层组合：`migrateAllDataSourcesCreatesCredentialsAndClearsPlaintext`（明文行 → 迁移 → DB 行状态：键在/明文清/usage 登记）+ `migratedRowResolvesCredentialEndToEnd`（credentialId 行 → buildDataSource → provider 解析 → 真实 H2 建连 connected=true）+ `transitionRowCredentialWinsOverJsonPlaintext`（并存行凭证侧生效——预建库用户判别取值来源）+ `unbindCredentialClearsKeyAndUsageWithoutRestoringPlaintext`（回滚静态路径 + 明文不复活死值断言））
- [x] **接线验证**：`buildDataSource` 在 credentialId 行实际调用 provider（mock 断言）；迁移创建的凭证经 `ICredentialProvider.getCredentialData` 可读回（round-trip）。（processor 测试 FakeProvider.getCredentialCalls 计数断言；round-trip = Phase 1 `TestCredentialMigrationSupport.createCredentialEncryptsAndScopesSystem` 经真实 provider getCredentialData 断言（真实 impl 全链））
- [x] **无静默跳过**：解析失败/部署不一致/错型显式抛错（专项用例）；testConnect 不吞 AR-02/config-invalid（专项用例）。
- [x] **新功能测试**：列出 Phase 2 测试类与用例名（含迁移幂等与原子性）。（见 Proof 项 29 用例）
- [x] 14 消费点零改动证据（git diff 文件清单）+ 既有 metadata 测试族全绿。（git diff：nop-metadata 仅 pom/DataSourceErrors/NopMetadataArgs/processor/bizmodel 五文件；全量 1077 tests 0 failures）
- [x] 文档裁定：No owner-doc update required（章节统一 Phase 3）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - runbook + 文档同步 + 设计回写 + 收口验证

Status: completed
Targets: `docs-for-ai/03-modules/nop-credential.md`、`docs-for-ai/03-modules/nop-metadata.md`、runbook 归属裁定、`ai-dev/design/nop-credential/03-integration-metadata-migration-design.md`（回写）、roadmap、日志

- Item Types: `Follow-up | Proof`

- [x] **Follow-up**：integration 迁移 runbook 落盘（§6.4 唯一迁移机制——运维顺序：先设 credentialId（并存窗口验证）→ 验证发送 → 后删静态密钥；回滚 = 引用级清除回静态路径，明文清除后回滚需显式再录入；consumerRef 清单与人工清理说明）。归属裁定（Decision）：落 `docs-for-ai/03-modules/nop-credential.md` 迁移章节扩展（integration 无独立 owner doc；nop-integration 现有文档面核实后定稿）。（核实：nop-integration 无独立 owner doc（docs-for-ai/03-modules 无该文件）——runbook 落 `nop-credential.md` 新增"深度迁移（W16）"章节的 integration 迁移 runbook 小节：五步运维顺序 + consumerRef 清单 + 部署前置（credential-defaults.beans.xml 聚合装载）+ 回滚语义）
- [x] **Follow-up**：`docs-for-ai/03-modules/nop-credential.md`（深度迁移章节：integration 首批 + metadata 动作对与批量迁移 + 类型清单三新增 + **SPI 增量（CredentialData.typeName + 迁移支持 SPI）**）+ `docs-for-ai/03-modules/nop-metadata.md`（数据源 credentialId 语义/兼容矩阵/管理动作）同步 + 设计 03 号文回写 impl 裁定标注（共享支持命名/迁移事务边界实现/admin 与审计 Decision 结论/**SPI 增量裁定与 §4.1 结论 8 registerUsage 行为更正/§4.3 契约 2 实现通道/§6.4 反查主源更正/§八"不触碰"清单修正**/执行期偏离）+ roadmap（W16-impl done + 扩展批次登记确认）+ `docs-for-ai/INDEX.md`/`source-anchors.md`（若锚点变化）。（落盘：nop-credential.md 新增"深度迁移（W16）"章节（横切契约/SPI 增量/共享解析支持/SMS 接线/runbook/metadata 交叉引用）+ 子模块表 api 行更新 + 源码锚点表两行新增；nop-metadata.md 新增"数据源凭证（credentialId 深度迁移，W16）"章节（兼容矩阵三行/解析语义/管理动作对表/admin 配置/审计载体）；设计回写五处（§4.1 结论 8 registerUsage 前置校验更正 + catch-all WARN 扩域、§4.3 契约 2 typeName 实现通道 + null 拒绝、§5.1.5 admin/审计落地裁定（含"自动触发"→手动发布的 live 事实更正 + delete/deleteByQuery 双覆写）、§6.4 反查主源更正 + 行级事务重读实现、§八 不触碰清单 additive 修正）+ 新增"W16-impl 落地裁定（2026-08-18 回写）"小节；roadmap W16-impl `planned`→`done` + Last updated + W16-impl-ext 登记确认；`source-anchors.md` 新增 `CRED-006`（深度迁移消费链全锚点）；INDEX 路由行无变化（模块 doc 路径不变，未新增路由项））
- [x] **Proof**：全量验证——`./mvnw test -pl nop-integration/nop-integration-api,nop-integration/nop-integration-sms-tencent,nop-integration/nop-integration-sms-yunpian,nop-metadata/nop-metadata-service,nop-credential/nop-credential-service,nop-auth/nop-auth-service -am -T 1C` 绿（**显式子模块路径**——`nop-integration`/`nop-metadata`/`nop-credential`/`nop-auth` 均为聚合器 pom；消费链全回归；pre-existing flake 按 W12 登记口径）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-integration --module nop-metadata --severity high` 0 NEW；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。（输出存 `_tmp/`：`test-w16-phase3.log`（BUILD SUCCESS，全模块含 nop-auth-service SUCCESS）、`scan-hollow-w16.log`（Total 0，退出码 0）、`check-doc-links-w16.log`（0 errors 0 warnings，退出码 0——修正一处 docs-for-ai→ai-dev 边界引用与一处 plan 相对路径 warning））

Exit Criteria:

- [x] runbook 与文档与 live 实现一致（配置键/动作签名/类型清单/迁移步骤可对号）。（runbook 五步对号 `TencentSmsSender.credentialId` 属性/`registerUsage` 登记；管理动作签名 `bindCredential(dataSourceId, credentialId)`/`unbindCredential(dataSourceId)`/`migrateDataSourcesCredential()` 与 nop-metadata.md 表一致；三类型实例文件名与 §4.3 一致）
- [x] 验证命令通过（附输出存 `_tmp/`）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 批次裁定落地：扩展批次工作项已在 roadmap 登记（Phase 1 Decision 项可复核）；本 plan 范围 = 首批（SMS×2 + metadata + 共享支持 + 三类型实例 + runbook）。（roadmap :55 W16-impl-ext 行 + :56 A3-audit 依赖注记，closure audit Area 5 PASS）
- [x] 优先级链三态 + fail-closed 不回退语义双侧（integration/metadata）有专项测试且通过。（TestCredentialResolutionSupport + 两发送器三态用例；TestMetaDataSourceConnectionProcessorCredential 兼容矩阵三行 + fail-closed 全分支，closure audit Area 1a-d/7 PASS）
- [x] 兼容矩阵三行（存量/迁移/过渡并存）测试通过；空串 credentialId 视同缺失有专项用例。（blankCredentialIdTreatedAsMissing 两侧（integration/metadata）+ legacyPlaintextRowWithoutCredentialIdUsesStaticPath）
- [x] 批量迁移幂等（中断重跑反查复用、无重复凭证）+ per-row 事务同事务清明文（无中间落盘态）有专项测试。（migrateInterruptedRerunConvergesWithoutDuplicates / migrateRerunIdempotentReusesCredentialsViaConsumerRef / bindCredentialFailureLeavesRowUntouched）
- [x] 14 消费点零改动（git diff 证据）+ nop-metadata 既有测试族全绿 + nop-auth SMS 消费链零回归。（git diff 消费点文件为零（closure audit Area 2）；metadata 全量 1077 tests 0 failures；nop-auth-service -am 全绿 + TestMfa 家族专项绿）
- [x] 依赖边 api-only（两处 dependency:tree 证据——无 credential-service/dao 传递引入）。（integration-api 仅 nop-commons 传递；metadata-service 仅 nop-credential-api:compile 单行）
- [x] SPI 增量落地与边界：`CredentialData.typeName` 可选属性（向后兼容，既有消费方零感知）+ 迁移支持 SPI（`ICredentialProvider` 六方法签名零变更——git diff 核实）；两项增量经本 plan draft review 评审门并回写设计。（git diff ICredentialProvider.java 空（closure audit Area 2）；设计 §4.3/§八 回写）
- [x] registerUsage bean 初始化登记 catch-all WARN（含 provider 前置校验抛错——配错 credentialId 不阻断启动专项用例）；设计 §4.1 结论 8 行为更正已回写。（registerUsageProviderValidationErrorDoesNotBlockInit ×2；设计 §4.1 结论 8 更正在案）
- [x] registerUsage/unregisterUsage 生命周期（bind/unbind/换绑/行删除/bean 初始化幂等）有断言。（bind 三态/换绑/unbind 幂等/delete 与 deleteByQuery 双路径/初始化幂等用例齐备）
- [x] 无 ORM/DDL 变更（JSON 键方案——git diff 核实两侧 orm.xml 零触碰）；凭证类型实例为纯资源新增；迁移反查主源 = consumerRef 唯一约束（长名截断+哈希策略有用例）。（git diff orm.xml 零触碰；deterministicCredentialName 双用例 + 长名迁移成功用例）
- [x] delete 与 deleteByQuery 双路径 unregister 有断言（NopAiModelBizModel 先例批量路径坑位钉定）。（deleteUnregistersCredentialUsage + deleteByQueryUnregistersCredentialUsageForAllHits）
- [x] `ICredentialProvider` 六方法签名/`cv1:` 格式/AR-02 链/`@sec:` 机制/nop-auth 消费方零变更（git diff 核实）。（closure audit Area 2 git diff 全量核对）
- [x] 无空壳/静默跳过（scan-hollow NEW 0 + 全部 fail-closed 分支专项用例；无 catch-吞异常）。（scan-hollow nop-integration+nop-metadata Total 0 退出码 0）
- [x] runbook 落盘 + 受影响 owner docs 已同步 + 设计裁定标注回写。（nop-credential.md 深度迁移章节（含 runbook）/nop-metadata.md 数据源凭证节/设计五处更正 + 落地裁定节，closure audit Area 6 PASS）
- [x] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow：发送器 credentialId → 共享支持 → provider → 发送全链；metadata credentialId 键 → buildDataSource → provider → 建连全链追踪）。（fresh subagent task ses_feda9f831ffedpL6W612JmTIw，9 区域全 PASS、0 Blocker/0 Major/2 Minor（.rels 环境产物已还原 + Closure 段待填——本段落即为该动作），裁定 CAN CLOSE；证据见 Closure 段）
- [x] `./mvnw test -pl nop-integration/nop-integration-api,nop-integration/nop-integration-sms-tencent,nop-integration/nop-integration-sms-yunpian,nop-metadata/nop-metadata-service,nop-credential/nop-credential-service,nop-auth/nop-auth-service -am` 绿（显式子模块路径）。（`_tmp/test-w16-phase3.log` BUILD SUCCESS）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [x] checkstyle / 代码规范检查通过（受影响模块 `-Pqa`）。（build 全链含 checkstyle 阶段通过；import 分组/4 空格/命名遵循模块既有风格）

## Deferred But Adjudicated

（起草时空缺——执行中产生的延期项按 Anti-Slacking 规则填充。预登记倾向：设计 §七 表 11 项均为设计层裁定（classification + 理由在案），其中 #1/#6 为 "W16-impl 可选增量"——本 plan 未纳入，若执行中不做则随扩展批次工作项或按 optimization candidate 登记归属，不悬挂。）

## Non-Blocking Follow-ups

（执行后登记。）

- 过渡并存行的 JSON 明文死值清理提示：迁移完成后存量并存行（如有）由 runbook 指引人工复核（运行时不可达死值，无安全暴露；属运维治理）。

## Closure

Status Note: 首批深度迁移全量落地且经独立 closure audit 核验通过——共享解析支持（integration-api 单点）+ SPI 增量（CredentialData.typeName + 迁移支持 SPI，六方法签名零变更 git diff 核实）+ SMS×2 发送器接线（双构造点 + catch-all WARN 登记）+ metadata 单点解析/管理动作对/批量迁移（per-row REQUIRES_NEW 幂等反查）+ 三类型实例 + runbook；73 新测试用例 + 三侧全量回归绿（metadata 1077/credential 209/nop-auth 消费链）；14 消费点零改动（git diff 证据）；扩展批次 W16-impl-ext 已登记 roadmap（A3 依赖其登记而非完成）。执行期偏离均为形态定稿（plan 各 Phase 标注），无语义偏离。
Completed: 2026-08-18

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit fresh subagent（task ses_feda9f831ffedpL6W612JmTIw，非执行 session）
- Audit Session: ses_feda9f831ffedpL6W612JmTIw
- Evidence:
  - 9 审计区域全 PASS（Anti-Hollow 接线双侧全链（TencentSmsSender :121-131/:142-145/:185-191、YunpianSmsSender :97-112/:115-135、MetaDataSourceConnectionProcessor :216-300、NopMetaDataSourceBizModel :271-545 含 migrateOneRow REQUIRES_NEW :444 与 consumerRef 主源反查 :470-479）/ SPI freeze（ICredentialProvider.java git diff 空）/ 变更文件清单（无 orm.xml、无 nop-auth 消费方、无 14 消费点文件）/ 三类型实例契约 / 依赖边 version-less api-only / roadmap 登记 / owner docs + 设计回写 / 测试真实性抽查（并存行 H2 用户判别 / 迁移中断收敛无重复 / 初始化存活） / deferred 诚实性）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（审计时点 Passed: 1, Failed: 0）
  - Anti-Hollow 检查结果：调用链运行时连通（credentialId → CredentialResolutionSupport → provider → 发送/建连）；`scan-hollow-implementations --module nop-integration --module nop-metadata --severity high` Total 0（`_tmp/scan-hollow-w16.log`）
  - 全量验证：`./mvnw test -pl <六显式子模块> -am -T 1C` BUILD SUCCESS（`_tmp/test-w16-phase3.log`）；`check-doc-links --strict` 0 errors（`_tmp/check-doc-links-w16.log`）
  - Deferred 项分类检查：Deferred But Adjudicated 无已确认 live defect（起草时缺省 + §七 #1/#6 归属指引）；Non-Blocking Follow-ups 仅运维治理项（并存行死值人工复核）
- Findings: 0 Blocker / 0 Major / 2 Minor（`nop-format/.../.rels` CRLF 环境产物——已 git checkout 还原，与本 plan 无关；Closure 段待填——本段落即履行）

Follow-up:

- 过渡并存行 JSON 明文死值迁移完成后由 runbook 指引人工复核（Non-Blocking Follow-ups 在案）
- 设计 §七 #1/#6（token 刷新期再解析 / 新类型 testCredential 连通性）按登记归属扩展批次或 optimization candidate，不悬挂
