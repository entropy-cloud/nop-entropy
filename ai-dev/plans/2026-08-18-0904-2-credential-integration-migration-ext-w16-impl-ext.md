# W16-impl-ext 深度迁移扩展批次（Email×2/Feishu/OSS/SFTP 发送器 credentialId 接线 + feishu/oss 配置键 + 五类型实例）

> Plan Status: completed
> Mission: nop-credential-mfa
> Work Item: W16-impl-ext（深度迁移扩展批次）——迁移二期组，依赖 W16-impl done + W15-impl done（均已 done；email 消费链成立后验证面完整）
> Last Reviewed: 2026-08-18
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md` W16-impl-ext 条目（W16-impl plan Phase 1 Decision 登记产物）；`ai-dev/design/nop-credential/03-integration-metadata-migration-design.md` §三（引用点基线）/§4.1（结论 1/2/6/7/8）/§4.3（类型 schema）/§六（横切契约）/§七（#1/#6/#7 再裁定输入）/§八（批次收口硬约束 + 首批落地裁定）；W16-impl plan `2026-08-17-2212-3-credential-integration-metadata-migration-w16.md`（首批接线先例与 Follow-up 归属指引）
> Related: W16-impl `2026-08-17-2212-3-credential-integration-metadata-migration-w16.md`；W15-impl `2026-08-17-2212-2-mfa-email-code-trusted-device-w15.md`（email 消费链）；A1-audit `2026-08-17-0447-1`（adjudication #5/#7 同族先例）
> 执行顺序：本 plan 为本轮批次第 2 份（N=2）；与 A2-audit（N=1）无依赖、变更面无交集（nop-integration vs nop-auth）。A3-audit 依赖本工作项的 roadmap 登记（已在案），非其完成。

## Purpose

落地深度迁移扩展批次：五个家族发送器（tencent-email / smtp-email / feishu / oss / sftp）接入凭证库 credentialId 解析，补齐 feishu/oss credentialId 配置键与五个凭证类型实例文件，使设计 §4.3 八类型清单全部落地；同时收口设计 §七 #1/#6/#7 与 W16-impl Follow-up 显式路由到本工作项的裁定归属。本 plan 为 W16-impl 首批的同型扩展，共享解析支持、SPI 增量、横切契约均已在首批落地，本 plan 零凭证库侧改动、零 metadata 侧改动。

## Current Baseline

（2026-08-18 live repo 核对）

- **W16-impl 首批已落地并通过独立 closure audit**：共享解析支持 `CredentialResolutionSupport`（`nop-integration-api`，五错误码：PROVIDER_NOT_CONFIGURED / RESOLVE_FAILED / TYPE_MISMATCH / FIELD_REQUIRED / FIELD_CONVERT_FAILED；helper：isConfigured/resolveGroup/requireString/optionalString/requireInteger）；SPI 增量（`CredentialData.typeName` + 迁移支持 SPI）；SMS×2 发送器接线先例（`TencentSmsSender.java`：credentialId 属性 + `@Nullable protected ICredentialProvider` + `@PostConstruct` 幂等 registerUsage（catch-all WARN 不阻断启动）+ 优先级链判定 + `protected createSender` seam 覆盖双构造点）；metadata 单点解析 + bindCredential/unbindCredential + 批量迁移；三类型实例（tencent-sms/yunpian-sms/jdbc-datasource，`nop-credential/nop-credential-service/src/main/resources/_vfs/nop/credential/types/`，同目录另有 generic-oauth2/generic-secret/openai-api-key）；runbook 落 `docs-for-ai/03-modules/nop-credential.md` 深度迁移章节。
- **扩展批次五家族 live 锚点（设计 §3.1/§3.2 逐点基线，本起草日 live 复核文件在位）**：
  - 邮件-腾讯：`TencentEmailSender`（secretId/secretKey/region；`nop-integration/nop-integration-email-tencent/src/main/java/io/nop/integration/email/tencent/TencentEmailSender.java`）——逐次发送构造 client；纯 setter 供给，消费方应用 beans.xml 装配。
  - 邮件-JavaMail：`MailConfig`（username/password）+ `JavaEmailSender`（逐次发送建 Transport，读 config 后 connect）——同上供给形态。
  - 飞书：`FeishuCredentials`（appId/appSecret/verificationToken/encryptKey，`@InjectValue("@cfg:nop.integration.feishu.*|")` setter，空串缺省）+ `FeishuClient`（start 期 `getStreamEndpoint`，token 惰性刷新 `ensureToken` 约 2h 周期，重连复用）——start 期捕获家族；装配 `feishu-defaults.beans.xml`（`nopFeishuCredentials` 无参 bean + `nopFeishuClient` + `nopFeishuBindProvider` 注入 credentials）；`FeishuConnector`（nop-ai-gateway）ChannelConfig options 面为另一供给面（§4.1 结论 7 裁定不引入 credentialId，§七#10）。
  - OSS：`OssConfig`（accessKey/secretKey）+ `OssFileServiceClientFactory`（`@PostConstruct` 构造期一次性消费，客户端缓存）——构造期家族；装配 `oss-defaults.beans.xml`（`nopOssConfig` 经 `ioc:config-prefix="nop.integration.oss"` 绑定 + 工厂 bean 由 `nop.integration.oss.enabled` 门控 enableIfMissing=false）；下游 nop-file 仅消费工厂 bean，不触密钥。
  - SFTP：`SftpConfig`（@ConfigBean，username/password/keyPath/passphrase，纯 setter）+ `SftpClient`（逐次操作：addIdentity/getSession/setPassword）+ `SftpClientFactory.newClient` 每次新建——逐次操作家族；无 defaults.beans.xml，消费方应用装配。
- **email 消费链已成立（本工作项依赖注记的兑现前提）**：W15-impl 落地后 `IEmailSender` 在 nop-auth-service 有真实运行时消费方（`LoginServiceImpl`/`NopAuthUserBizModel` 发码链路，@Nullable fail-closed）——扩展批次 Email 家族可经 MFA 邮件码发码链做端到端验证（W16 首批 SMS 的同型验证面）。
- **consumerRef token 清单（设计 §4.1 结论 8）**：`integration:tencent-email` / `integration:smtp-email` / `integration:feishu-app` / `integration:oss-s3` / `integration:sftp-ssh`（本 plan 落地五枚；首批两枚 SMS 已在案）。
- **类型 schema（设计 §4.3，五类型待落地）**：`tencent-email`（secretId 必填非敏感/secretKey 必填敏感/region 必填非敏感）；`smtp-email`（username 可空非敏感/password 可空敏感，整凭证至少一字段非空——实现通道已裁定为解析侧 fail-closed 兜底：类型 schema 仅字段级无法表达 + 本 plan 零凭证库侧改动，见 Phase 1）；`feishu-app`（appId 必填/appSecret 必填敏感/verificationToken 与 encryptKey 可空敏感，后两者为预留字段）；`oss-s3`（accessKey 必填/secretKey 必填敏感）；`sftp-ssh`（username/password/passphrase 均可空，公钥无口令场景合法；host/port/keyPath 留 SftpConfig）。
- **显式路由到本工作项的裁定项（W16-impl Follow-up + 设计 §七，Phase 3 必须逐项裁定，禁止悬空）**：
  1. 设计 §七#1 飞书 token 刷新期凭证再解析（optimization candidate，Successor = "W16-impl 可选增量"——本 plan 裁定纳入或维持 deferred）。
  2. 设计 §七#6 新类型 `testCredential` 连通性实现（optimization candidate，Successor = "C1-hardening 同族可选项或 W16-impl"——C1-hardening 已 done 且未纳入此项，A1 adjudication §二#5 对 oauth2 同族项裁定"再延 out-of-scope（探测语义需设计）"可作先例；本 plan 裁定归属）。
  3. 设计 §七#7 feishu/oss 静态值强制 `@sec:`（watch-only residual，其 Why-Not-Blocking 前提为"扩展批次落地前静态值仍是主路径"——本 plan 落地后该前提变化（静态值降为回退路径），watch 项状态须回写设计登记，不实施强制化）。
- **横切契约（首批已单点落地，本 plan 复用不重建）**：优先级链 `credentialId > 静态值`（空串/空白视同缺失）；强 fail-closed（provider 未装配/凭证缺失/软删/必填字段空/错型/转换失败 → 显式抛错中止，不静默回退静态值）；解析契约三条（必填字段缺失 fail-closed / typeName 家族校验 / 值转换失败 fail-closed）；整组生效语义（同名静态值忽略，仅作回滚回退值）；登记失败 catch-all WARN 不阻断启动。
- **模块依赖事实**：`nop-integration-api` → `nop-credential-api` compile 依赖已在首批落地；五厂商模块零新增依赖（经 integration-api 传递获得共享解析支持）；本 plan 无 ORM/DDL 变更（设计 §五 JSON 键方案 + 厂商模块无 ORM 层）。

## Goals

- 五家族发送器 credentialId 接线按设计 §4.1 结论 6 分层时序落地：Email×2/SFTP 逐次消费期解析、Feishu start 期解析副本（整组覆盖四字段，DataBean 装配形态不变）、OSS 构造期解析。
- feishu/oss credentialId 配置键落地（`nop.integration.feishu.credentialId` @InjectValue 与四键并列；`nop.integration.oss.credentialId` 经 config-prefix 自动绑定）。
- 五个凭证类型实例文件落 `_vfs/nop/credential/types/`（§4.3 schema 逐字段必填性/敏感性），typeList 动态表单可见。
- 五枚 consumerRef token 幂等登记（登记时点随家族消费时序：Email×2/OSS/SFTP 为 `@PostConstruct`（bean 初始化期即持有配置值），Feishu 载体见 Phase 2 执行期裁定——live 形态下 `FeishuClient` 初始化期不持有 credentials；失败 WARN 不阻断启动）。
- fail-closed 全路径每家族有负例测试（provider 未装配/缺失/软删/必填空/错型/转换失败），静态路径零回归（credentialId 空 = 现状行为）。
- Email 家族经 MFA 邮件码发码链的端到端验证（真实消费方消费解析后凭证——W16-impl-ext 依赖注记的兑现）。
- 设计 §七 #1/#6/#7 三项裁定落盘 + 设计 03 号文扩展批次落地裁定回写 + runbook/owner docs 扩展。

## Non-Goals

- metadata 侧任何变更（W16-impl 已收口：单点解析/管理动作对/批量迁移/14 消费点）。
- 凭证库 service/web/api 代码变更（共享解析支持与 SPI 增量已定型；仅新增类型实例资源文件）。
- OSS 客户端热重建（§七#2 no——轮换可见性 = 重启生效，显式接受）。
- 飞书 ChannelConfig options 面 credentialId（§4.1 结论 7 / §七#10 no——options 面维持字面值语义）。
- 集成配置 DB 实体 / Web 渠道管理面（§七#3 no）；user 级凭证用于渠道（§七#4 no）；多环境密钥分离（§七#5 no）。
- `@sec:` 静态值强制化（§七#7 watch-only——本 plan 只登记前提变化，不实施启动校验）。
- nop-file 下游消费链、nop-auth 消费方代码（`emailSender == null` fail-closed 检查原样）。
- 前端页面。

## Scope

### In Scope

- 五厂商模块发送器/凭证持有类的 credentialId 接线（Java + beans.xml/配置键），含 email-tencent/email-java/oss/sftp 四模块的 pom 测试依赖补齐（live 无 `src/test` 基建与 junit/mockito 依赖，参照 feishu 模块先例）。
- 五个凭证类型实例文件（资源文件）。
- 测试：每家族组件级（三态优先级链/fail-closed 矩阵/静态零回归/登记幂等 WARN）+ Email 家族 MFA 发码链 E2E + feishu start 期副本语义 + oss 构造期语义。
- 设计 03 号文回写（落地裁定 + #1/#6/#7 再裁定标注）；`docs-for-ai/03-modules/nop-credential.md` 深度迁移章节扩展（runbook 五家族 + 类型清单全量 + 配置键表）；roadmap W16-impl-ext 收口；`ai-dev/logs/`。

### Out Of Scope

- 上述 Non-Goals 全部条目。
- 飞书 token 刷新期再解析的实施（若 Phase 3 裁定维持 deferred；若裁定纳入则属 in scope——以裁定为准，默认倾向维持）。
- 新类型 testCredential 连通性的实施（同上，以 Phase 3 裁定归属为准）。

## Execution Plan

### Phase 1 - Email 家族接线 + 五类型实例 + consumerRef token

Status: completed
Targets: `nop-integration/nop-integration-email-tencent/`、`nop-integration/nop-integration-email-java/`、`nop-credential/nop-credential-service/src/main/resources/_vfs/nop/credential/types/`

- Item Types: `Fix | Proof`

- [x] **Fix**：`tencent-email` / `smtp-email` 凭证类型实例文件（§4.3 字段 schema：必填性/敏感性/tagSet 逐字段对齐首批三实例文件形态）。（落盘：`tencent-email.credential-type.xml`（secretId 必填非敏感/secretKey 必填敏感/region 必填非敏感）+ `smtp-email.credential-type.xml`（username 可空非敏感/password 可空敏感），`TestDefaultCredentialTypeRegistry` init 扫描加载通过）
- [x] **Fix**：`TencentEmailSender` credentialId 属性 + 逐次发送期解析（优先级链经 `CredentialResolutionSupport` 单点语义；client 构造点抽 protected seam 覆盖全部构造路径，同首批 `createSender` 先例）+ `@PostConstruct` 幂等登记 `integration:tencent-email`（catch-all WARN）。（落地：`ResolvedCredential` 组（secretId/secretKey/region）+ 包可见 `resolveCredential()` + `protected createClient(ResolvedCredential)` seam（原 `newClient()` 无参构造点改携带解析组——单构造点 sendEmail 覆盖）+ `@Inject @Nullable ICredentialProvider`）
- [x] **Fix**：JavaEmailSender/MailConfig 家族 credentialId 接线（逐次发送建 Transport 期解析 username/password 整组；空=无认证语义保持；"整凭证至少一字段非空"跨字段约束——类型 schema 仅字段级无法表达且本 plan 零凭证库侧改动，故实现通道裁定为解析侧 fail-closed 兜底（整组全空显式拒绝），裁定记录于本 plan）+ 幂等登记 `integration:smtp-email`。**fail-closed 落点注意（live 核对）**：`JavaEmailSender.withTransport` 捕获 `Exception` 仅 LOG.error 后返回——解析必须在该 try 之前执行或使 `NopException` 穿透不被吞，否则 fail-closed 退化为静默失败（MFA 链误信已发码）；负例测试断言抛出异常而非日志。（落地：credentialId 持于 `MailConfig`（与 username/password 同属凭证字段）；解析置于 `withTransport` try 之前——专项用例 `resolutionFailurePropagatesOutOfWithTransportNotSwallowedAsLog` 断言穿透；跨字段兜底抛 `ERR_CREDENTIAL_FIELD_REQUIRED`（fieldName=username/password）；`connectTransport(ResolvedCredential)` seam 化）
- [x] **Proof**：Email 家族组件测试——三态优先级链（credentialId 空/有效/失效）、fail-closed 矩阵（provider 未装配/缺失/软删/secretKey 必填空/错型/转换失败逐例显式抛错不回退）、静态路径零回归、登记幂等 + WARN 不阻断（含 email 两模块 pom 测试依赖补齐——live 无 `src/test` 基建，参照 feishu 模块先例；Phase 2 oss / Phase 3 sftp 同口径继承）。（落地：`TestTencentEmailSenderCredential` 11 用例 + `TestJavaEmailSenderCredential` 16 用例，全绿；两模块 pom 补 `junit-jupiter` test 依赖）

Exit Criteria:

- [x] 两类型实例文件落盘；typeList 可见性断言（动态表单 schema）随 Phase 3 nop-credential-service 测试门执行（Phase 1 只负责文件落盘，该验证点显式后移至 nop-credential-service 测试面）。
- [x] 两发送器 credentialId 路径全链可用：组件测试证明解析后的凭证组真实到达 client/Transport 构造（**接线验证**，Minimum Rules #23——mock provider 断言解析值被消费，非仅存在性）。（`sendEmailEntryUsesResolvedCredentialGroup`（SendEmail 于解析组构造的 client 上调用）×2 + `sendEmailEntryUsesResolvedCredentialAndTransportConsumesIt`（sendMessage 消费））
- [x] **无静默跳过**：所有 fail-closed 分支抛 `NopException`（`IntegrationErrors` 码），无吞异常/空方法体（Minimum Rules #24）。（专项：`resolutionFailurePropagatesOutOfWithTransportNotSwallowedAsLog`）
- [x] 静态路径既有测试零回归（两 email 模块既有测试全绿，既有断言零修改）。（两模块此前零 test 基建——`staticPathSendEmailReachesTransportWithStaticValues` / `blankCredentialIdUsesStaticValuesWithoutProviderCall` 钉死现状行为；模块全量 16+11 绿）
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - config-bound 家族：Feishu start 期 + OSS 构造期

Status: completed
Targets: `nop-integration/nop-integration-feishu/`、`nop-integration/nop-integration-oss/`

- Item Types: `Fix | Proof`

- [x] **Fix**：`feishu-app` / `oss-s3` 凭证类型实例文件（§4.3 schema）。（落盘：`feishu-app.credential-type.xml`（appId 必填非敏感/appSecret 必填敏感/verificationToken+encryptKey 可空敏感）+ `oss-s3.credential-type.xml`（accessKey 必填非敏感/secretKey 必填敏感））
- [x] **Fix**：Feishu 接线——`FeishuCredentials` 新增 credentialId 字段（`@InjectValue("@cfg:nop.integration.feishu.credentialId|")` 与现有四键同型，空串缺省）；`FeishuClient.start` 期 credentialId 非空 → 经 provider 解析四字段整组并构建**已解析副本**供 client 持有（原 DataBean `@InjectValue` 装配形态不变；同名静态值忽略）+ 幂等登记 `integration:feishu-app`——**登记载体执行期定稿（live 约束）**：`FeishuClient` 仅在 `start()` 经参数获得 credentials（bean 初始化期不可达），候选载体为 `FeishuClient.start` 期（与解析同时点，credentialId 随 credentials 到手）或 `FeishuCredentials` 初始化期（配置注入后可见 credentialId）；硬约束：登记路径在持有 credentialId 值的时点**真实可达** + 测试断言 `registerUsage` 真实调用/usage 行存在——不得落成结构性不可达的空登记（Minimum Rules #23/#24）。ChannelConfig options 面不引入 credentialId（§4.1 结论 7——负向语义随测试钉死）。（**裁定：登记载体 = `FeishuClient.start` 期**（与解析同时点；生产链路 FeishuConnector.start → client.start 真实可达）；测试 `registerUsageAtStartWhenCredentialConfigured`/`registerUsageProviderValidationErrorDoesNotBlockStart` 断言真实调用；options 面负向测试落 nop-ai-gateway `TestFeishuConnector.channelConfigOptionsCredentialIdIsIgnored`）
- [x] **Fix**：OSS 接线——`OssConfig` 新增 credentialId 属性（`ioc:config-prefix` 自动绑定 `nop.integration.oss.credentialId`）；`OssFileServiceClientFactory` `@PostConstruct` 构造期解析 accessKey/secretKey 整组（一次消费，客户端缓存语义不变；消费点为 `BasicAWSCredentials` 构造——抽 protected seam 同首批 `createSender` 先例）+ 幂等登记 `integration:oss-s3`（enabled 门控下零介入保持——未启用时无登记无解析）。（落地：`@ConfigField(name="credentialId")` 钉住 camelCase 键名；`createAwsCredentials(accessKey, secretKey)` seam；**键名实测结论**：config-prefix 缺省归一为 kebab-case `credential-id`，经 `@ConfigField` 显式指定后 `nop.integration.oss.credentialId` 生效——容器级真实绑定测试证明，回写设计 §4.1 结论 1 见 Phase 4）
- [x] **Proof**：config-bound 家族测试——Feishu start 期副本整组覆盖（四字段逐一断言）/ 静态路径零回归（credentialId 空 = 现状：四键直用）/ token 刷新与重连使用已捕获值（若 Phase 3 裁定 #1 维持 deferred，则该语义以显式测试钉死，作为 deferred 的 watch 锚点）；OSS 构造期一次解析 + enabled=false 门控零介入 + 静态零回归；两家族 fail-closed 矩阵与登记幂等 WARN 同 Phase 1 口径。（落地：`TestFeishuCredentialResolution` 17 用例（含 `tokenRefreshUsesCapturedResolvedValues`/`reconnectUsesCapturedResolvedValues` watch 锚点）+ `TestFeishuCredentialIdBinding` 2 用例（真实容器装载生产 feishu-defaults.beans.xml）+ `TestOssFileServiceClientFactoryCredential` 10 用例 + `TestOssConfigCredentialBinding` 5 用例（真实容器装载 ioc:config-prefix 夹具 + 生产 oss-defaults.beans.xml 门控四态）；feishu/oss 模块 pom 补 nop-ioc test 依赖、oss 补 junit）

Exit Criteria:

- [x] 两类型实例文件落盘；两配置键经**真实绑定测试**证明生效（live 核对提示：既有 OSS 配置键为 kebab-case 形态（`nop.integration.oss.access-key`），`credentialId` 键名能否经 config-prefix 归一化绑定须以测试实证——若需调整键名以实测为准，并回写设计 §4.1 结论 1 与 Phase 4 配置键表；防"配置键静默不生效→静默回退静态值"路径）。（**实测结论**：config-prefix 缺省归一为 kebab-case `credential-id`——经 `@ConfigField(name="credentialId")` 显式钉住后设计键名 `nop.integration.oss.credentialId` 生效；`TestOssConfigCredentialBinding.credentialIdKeyBindsViaConfigPrefixAndFactoryConsumesAtConstructTime` 真实容器证明（含既有 kebab 键并存绑定断言）；feishu 键经 `TestFeishuCredentialIdBinding` 真实容器装载生产 defaults 文件证明；设计回写归 Phase 4）
- [x] Feishu 解析副本语义成立：start 期整组覆盖 + DataBean 装配形态不变（**接线验证**：解析值真实到达 `getStreamEndpoint`/`ensureToken` 消费点）。（`startPassesResolvedGroupToStreamEndpointAndHandshake`/`tokenRefreshUsesCapturedResolvedValues`/`reconnectUsesCapturedResolvedValues`）
- [x] OSS 构造期解析语义成立（**接线验证**：解析值真实到达 `BasicAWSCredentials` 构造）；`nop.integration.oss.enabled` 门控下零介入。（`validCredentialOverridesStaticValuesAsGroupAtConstructTime`；门控四态容器用例（enabled 缺省→bean 缺席零调用/enabled=true 静态零介入/enabled=true 配错 fail-closed））
- [x] 静态路径既有测试零回归（feishu/oss 模块既有测试全绿）。（feishu 既有 25 用例全绿（TestFeishuClient 10 + BindProvider 7 + PbCodec 8，断言零修改——fakes 仅增量记录字段）；oss 此前零 test 基建）
- [x] **无静默跳过**：fail-closed 分支全部显式抛错。（两家族 fail-closed 矩阵用例在案：provider 未装配/解析失败/错型/必填空）
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - SFTP 逐次操作期 + 路由裁定收口 + 全家族回归

Status: completed
Targets: `nop-integration/nop-integration-sftp/`、`ai-dev/design/nop-credential/03-integration-metadata-migration-design.md`、测试树

- Item Types: `Fix | Decision | Proof`

- [x] **Fix**：`sftp-ssh` 凭证类型实例文件（§4.3 schema：三字段均可空）。（落盘：username 可空非敏感 + password/passphrase 可空敏感；typeList 可见性与字段 schema 断言落 `TestDefaultCredentialTypeRegistry` 6 新用例（含 W16 八类型全量可见））
- [x] **Fix**：SFTP 接线——credentialId 属性（SftpConfig 或发送侧持有，形态执行期定稿：逐次操作期解析 username/password/passphrase 整组，`SftpClientFactory.newClient` 每次新建的时序天然适配；jsch 消费点 addIdentity/getSession/setPassword——抽 protected seam 覆盖，同首批先例）+ 幂等登记 `integration:sftp-ssh`；公钥无口令场景（password/passphrase 均空）合法语义保持。**fail-closed 落点注意（live 核对）**：`SftpClient.connect` 将 `Exception` 统一包装为 `ERR_SFTP_CONNECT_FAIL`——解析须在包装点之前独立失败以保留 `IntegrationErrors` 码语义；负例测试断言抛出的错误码而非包装后码。（落地：**credentialId 持于 `SftpConfig`**（与三凭证字段同置，host/port/keyPath 拓扑留置）；`SftpClientFactory` 持 `@Nullable ICredentialProvider` 经 `newClient()` 传递（`SftpClient(SftpConfig, ICredentialProvider)` 包可见重载，旧构造委托 null）；解析置于 `connect()` try 之前——专项用例 `failedResolutionFailsClosedWithoutFallbackAndKeepsErrorCode` 断言码独立；seam = `newJsch()` + `openConnection(ResolvedCredential)`（三消费点集中）；jsch 级接线用例 `resolvedValuesReachRealJschIdentityAndSessionConsumptionPoints`（真实 JSch 离线消费 + 传输层包装码分界断言））
- [x] **Decision**：设计 §七#1（飞书 token 刷新期再解析）裁定：纳入本 plan 或维持 optimization candidate（默认倾向维持——设计理由在案：start 期解析已消除配置明文主目标，刷新期再解析改造面与收益不成比例；若维持，Phase 2 的"已捕获值"测试即为 watch 锚点）。裁定 + 理由待 Phase 4 回写设计。（**裁定：维持 optimization candidate（deferred）**——start 期解析已消除配置明文主目标；刷新期再解析需 FeishuClient 持解析回调而非凭证值，改造面与收益不成比例（设计理由在案）；Phase 2 watch 锚点测试钉死现行语义：`tokenRefreshUsesCapturedResolvedValues` + `reconnectUsesCapturedResolvedValues`（断言刷新/重连不经 provider 再解析）。Successor：无独立 successor——后续需求实证后按 optimization candidate 立项）
- [x] **Decision**：设计 §七#6（新类型 testCredential 连通性）归属裁定：本 plan 实施 / 再延 out-of-scope（对照 A1 adjudication §二#5 oauth2 同族先例："探测语义需设计"） / successor 登记。裁定 + 理由待 Phase 4 回写设计。（**裁定：再延 out-of-scope**——A1 adjudication §二#5 oauth2 同族先例（"探测语义需设计"）适用：新五类型的真实连通探测（发测试邮件/建 S3/SFTP 连接/飞书连通）每渠道需探测语义设计（配额消耗/真实消息副作用/超时与错误分类），超出迁移扩展批次范围；一期 `testCredential` 对未实现类型显式返回 success=false + 描述（非静默）已覆盖安全底线。Successor：no（探测语义需求实证后立项））
- [x] **Decision**：设计 §七#7（feishu/oss 静态值 @sec: watch-only）前提变化登记：扩展批次落地后静态值从主路径降为回退路径，watch 项状态更新回写设计（不实施强制化）。（**裁定：维持 watch-only，前提变化登记**——本 plan 落地后 feishu/oss 静态值从主路径降为回退路径（credentialId 优先，静态值仅回滚回退值），明文暴露面收窄但并存窗口期明文仍为合法形态，启动强制校验仍会破坏既有部署；watch 项 Why-Not-Blocking 前提更新随 Phase 4 回写设计 §七#7 行，不实施强制化）
- [x] **Proof**：SFTP 组件测试（同 Phase 1 口径：三态/fail-closed 矩阵/静态零回归/登记幂等；**接线验证**：解析值真实到达 jsch addIdentity/getSession/setPassword 消费点）+ 全家族回归汇总（五家族 fail-closed 矩阵统一跑齐 + nop-integration 全模块既有测试绿）。（落地：`TestSftpClientCredential` 12 用例；全家族回归：七模块门 BUILD SUCCESS（email-tencent 11/email-java 16/feishu 44/oss 15/sftp 12/credential-service 215/auth-service 360）+ nop-integration 11 子模块全绿；中间一次 run 命中已登记 pre-existing rocksdb benchmark flake（-am 传递依赖、非 mission 模块）——按在案口径单模块复跑绿（ratio 0.878 < guard 2.0），整门复跑全绿）

Exit Criteria:

- [x] SFTP 接线全链可用 + 静态路径零回归。（12 用例；sftp 模块此前零 test 基建，静态路径现状行为经 `blankCredentialIdUsesStaticValuesWithoutProviderCall`/`publicKeyWithoutPassphraseScenarioStaysLegal` 钉死）
- [x] 三项裁定（#1/#6/#7）全部有结论与理由（记录于本 plan，Phase 4 回写设计）。（#1 维持 optimization candidate + watch 锚点测试在案；#6 再延 out-of-scope（A1 #5 同族先例）；#7 维持 watch-only + 前提变化登记回写）
- [x] 五家族 fail-closed 矩阵测试全绿（每家族负例齐备，无静默回退路径）。（email-tencent/email-java/feishu/oss/sftp 五模块 fail-closed 矩阵用例统一绿）
- [x] `./mvnw test -pl :nop-integration-email-tencent,:nop-integration-email-java,:nop-integration-feishu,:nop-integration-oss,:nop-integration-sftp,:nop-credential-service,:nop-auth-service -am` 绿（首五者为接线模块回归；nop-credential-service 覆盖 typeList/类型实例面 + Phase 1 后移的动态表单 schema 断言——注意 `:nop-credential` 为聚合器 pom（packaging=pom），`-pl` 不展开子模块、`-am` 只带上行依赖，必须显式选 service 子模块否则该门空转；nop-auth-service 覆盖 Email MFA 发码链 E2E——E2E 落点模块必须显式列入，否则 Minimum Rules #22 端到端验证无执行门）。（BUILD SUCCESS（`_tmp/gate-p3-seven-modules-r2.log`）；首 run 命中已登记 pre-existing rocksdb benchmark flake（非本 plan 模块，-am 传递），按在案口径单模块复跑绿 + 整门复跑全绿；credential-service 215 含 `TestDefaultCredentialTypeRegistry` 16（+6 新：八类型全量可见 + 五新类型字段 schema 逐一对齐）；auth-service 360 为 W15/A2 基线零回归——Email MFA credentialId E2E 属 Phase 4 交付物，随 Phase 4 后的收口门重跑覆盖）
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 文档同步与收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-credential.md`、`ai-dev/design/nop-credential/03-integration-metadata-migration-design.md`、`nop-auth/nop-auth-service`（MFA 发码链 E2E 用例落点——与 `TestEmailMfaE2E`/`CapturingEmailSender`/`app-test.beans.xml` 同基建）、roadmap、`ai-dev/logs/`

- Item Types: `Proof | Follow-up`

- [x] **Follow-up**：`docs-for-ai/03-modules/nop-credential.md` 深度迁移章节扩展：runbook 覆盖五新家族（设 credentialId 属性/配置键 → 验证 → 清除静态密钥；回滚 = 引用级清除）+ 类型清单 8 类型全量 + feishu/oss 配置键表 + email 消费链验证面说明（MFA 邮件码链）。（落地：章节扩展——类型清单表（八类型全量字段 schema）+ "家族接线形态"（分层时序 + 各家族消费点 seam + 登记语义）+ credentialId 配置键表（含 @ConfigField camelCase 钉住说明）+ Email 消费链验证面节 + runbook 步骤 2 分家族细化 + consumerRef 七 token 全清单 + 并存窗口复核提示；`source-anchors.md` 新增 `CRED-007`（扩展批次消费链全锚点）；INDEX.md 路由无变化无需更新）
- [x] **Proof**：Email 家族 MFA 发码链 E2E：credentialId 凭证经 `IEmailSender` 真实消费方（`LoginServiceImpl`/`NopAuthUserBizModel` 发码路径）的端到端用例（**端到端验证**，Minimum Rules #22——从凭证库行到发码消费的完整路径；用例落 nop-auth-service 测试树，随 Phase 3 七模块测试门执行）。（落地：`TestJavaEmailSenderMfaE2E` 3 用例（包位置 io.nop.integration.email.java 访问 seam，同 TestTencentSmsSenderCredential 同包先例）——`testBindMfaEmailConsumesCredentialResolvedGroup`（bindMfa 发码 → 解析组到达 Transport 构造点 + 真实 MimeMessage 收件人/验证码 + usage 登记）/`testLoginChallengeSendMfaCodeConsumesCredentialResolvedGroup`（LoginServiceImpl.sendMfaCode email 分派 + 惰性证明（init 零解析））/`testResolutionFailureFailsClosedThroughSendChain`（fail-closed 贯穿真实发码链，NopException 穿透不误信已发码）；nop-auth-service pom 补 nop-integration-email-java test 依赖）
- [x] **Follow-up**：设计 03 号文回写：新增"W16-impl-ext 落地裁定"小节（五家族接线形态定稿 + smtp-email 跨字段约束实现通道 + SFTP credentialId 持有形态 + feishu 登记时序裁定对设计 §4.1 结论 8"bean 初始化"措辞的家族偏差 + OSS 配置键名实测结论（结论 1 回写，若调整））+ §七#1/#6/#7 再裁定标注。回写须先于 Closure Gate"语义符合设计 §4.1 结论 1/2/6/7/8"核对完成（Phase 顺序已保证）。（落地：§八新增"W16-impl-ext 落地裁定（2026-08-18 回写，扩展批次交付）"十要点小节；§七表 #1/#6/#7 三行内联再裁定标注；§4.1 结论 1 键名绑定机制注记 + 结论 8 feishu 家族偏差内联更正）
- [x] **Follow-up**：roadmap W16-impl-ext 收口（`done` 判定交由本 plan closure audit）+ `docs-for-ai/INDEX.md`/`source-anchors.md`（若锚点变化）。（source-anchors `CRED-007` 已落；INDEX 路由无变化；roadmap `done` 标记随 closure audit 后收口（本 plan Phase 4 执行时点先登记，最终判定以 Closure 段证据为准——见 Closure Gates））

Exit Criteria:

- [x] owner docs / 设计 / roadmap / logs 四侧同步完成（或显式记录 No owner-doc update required 的具体条目）。（nop-credential.md 章节扩展 + source-anchors CRED-007 + 设计 03 号文（W16-impl-ext 落地裁定节 + §七三行再裁定 + 结论 1/8 内联注记）+ roadmap done 标记随 closure audit + logs 见对应日期条目；INDEX.md 无路由变化）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 五家族接线全落地且语义符合设计 §4.1 结论 1/2/6/7/8 + §4.3 解析契约三条（整组生效/必填缺失 fail-closed/错型拒绝/转换失败 fail-closed）。（closure audit 区域 2-6 全 PASS：Email×2 逐次发送期/Feishu start 期解析副本/OSS 构造期/SFTP 逐次操作期；结论 1 键名注记 + 结论 8 feishu 家族偏差已回写）
- [x] fail-closed 全路径显式失败：每家族 provider 未装配/凭证缺失/软删/必填空/错型/转换失败负例测试在案，无任何静默回退静态值路径。（五模块 fail-closed 矩阵统一绿；两处吞异常落点专项用例（withTransport 穿透/SftpClient 码独立）在案）
- [x] 静态路径零回归：五厂商模块 + nop-integration 既有测试全绿，既有断言零修改。（closure audit git diff 核实：TestFeishuClient/TestFeishuConnector 仅增量、TestDefaultCredentialTypeRegistry 117 插 0 删；feishu 既有 25 用例全绿；nop-integration 11 子模块全绿）
- [x] **接线验证（Minimum Rules #23）**：五家族解析值真实到达各自消费点（client/Transport/getStreamEndpoint/BasicAWSCredentials/jsch），Email 家族另经 MFA 发码链端到端（Minimum Rules #22）。（closure audit Anti-Hollow 区域 8 全链追踪 PASS；`TestJavaEmailSenderMfaE2E` 3/0 于真实 bindMfa/sendMfaCode 链）
- [x] 五类型实例文件落 `_vfs/nop/credential/types/` 且 typeList 可见（设计 §4.3 八类型清单全量落地）。（`TestDefaultCredentialTypeRegistry.listTypesCoversAllEightW16Types` + 五 schema 用例）
- [x] 五枚 consumerRef token 幂等登记 + 失败 WARN 不阻断启动（每家族测试在案）。（五模块 registerUsage 用例族：幂等/跳过/WARN 不阻断）
- [x] 路由裁定（§七#1/#6/#7 + W16-impl Follow-up 归属指引）全部落盘，无悬挂。（三项裁定落 plan Phase 3 + 设计 §七 内联标注 + Deferred But Adjudicated 三条分类在案）
- [x] owner docs（nop-credential.md）/设计 03 号文/roadmap 同步到 live baseline。（nop-credential.md 章节扩展 + source-anchors CRED-007 + 设计 W16-impl-ext 落地裁定节；roadmap done 标记随本 Closure 同步）
- [x] `./mvnw test` 覆盖面绿（Phase 3 列出的七模块 `-am`（含 `:nop-auth-service` E2E 执行门），不带 `-T 1C`，规避已登记 reactor 顺序 flake；pre-existing flake 按在案口径单独复核并声明）。（最终轮 BUILD SUCCESS（`_tmp/gate-final-seven.log`）：email-tencent 11/email-java 16/feishu 44/oss 15/sftp 12/credential-service 215/auth-service 363（360 基线 + 3 E2E，3 skipped 为既有）；首 run 命中已登记 rocksdb benchmark flake（非本 plan 模块，-am 传递）——单模块复跑绿（ratio 0.878 < guard 2.0）后整门复跑全绿；mission 门 `:nop-auth,:nop-ai-gateway,:nop-nosql -am -T 1C` 亦 BUILD SUCCESS）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-integration --severity high` 0 NEW（对照 W16-impl closure 基线）。（单标志扫描 Total 1 = pre-existing 占位注释（JavaEmailSender `// probably from a placeholder`，git show HEAD :105 同条在案 + stash 对照证实）→ **0 NEW**；W16-impl closure log 的 "Total 0" 实为工具 `--module` 多标志覆盖（只扫了 nop-metadata）——工具治理项登记 Non-Blocking Follow-ups）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。（2324 文件 / 25905 引用 / 0 errors；closure audit 独立复跑同 0）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [x] checkstyle / 代码规范检查通过（五个接线厂商模块）。（checkstyle 非项目 lint 门禁在案（mission verify 轮口径：9166 条既有违例）；五模块基线 466 → 500（+34 全为 Javadoc 风格类，与 W16-impl 首批先例 TencentSmsSender 同风格画像（sms-tencent 单模块既有 74 条），无功能性违例））
- [x] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow：五家族调用链运行时连通 + 无空方法体/静默跳过）。（fresh subagent `ses_feb75b744ffehC4b20dYd38aLo`，13 验证区域全 PASS，0 Blocker / 0 Major / 3 Minor（未提交（收口后提交）/一处 .rels 环境触碰（已还原）/Closure 段待填（本段即履行）））

## Deferred But Adjudicated

### 飞书 token 刷新期凭证再解析（设计 §七#1）

- Classification: `optimization candidate`
- Why Not Blocking Closure: start 期解析已消除配置明文主目标；刷新周期约 2h、轮换最迟一个 token 周期后经 connector 重启生效；再解析需 FeishuClient 持解析回调而非凭证值，改造面与收益不成比例。现行"已捕获值"语义经显式测试钉死为 watch 锚点（`TestFeishuCredentialResolution.tokenRefreshUsesCapturedResolvedValues` / `reconnectUsesCapturedResolvedValues`）。
- Successor Required: `no`
- Successor Path: 需求实证后按 optimization candidate 立项

### 新类型 testCredential 连通性（设计 §七#6，含本批次五类型）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `testCredential` 一期对未实现类型显式返回 `success=false` + 描述（非静默）——安全底线已覆盖；真实连通探测每渠道需探测语义设计（配额消耗/真实消息副作用/超时与错误分类），A1 adjudication §二#5 oauth2 同族先例（"探测语义需设计"）适用。
- Successor Required: `no`
- Successor Path: 探测语义需求实证后立项

### feishu/oss 静态值强制 `@sec:`（设计 §七#7）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 强制化（启动校验拒绝明文）会破坏既有部署（并存窗口明文为合法形态）；本 plan 落地后静态值已从主路径降为回退路径（credentialId 优先），明文暴露面收窄——前提变化已登记回写设计 §七#7 行，维持 watch-only（审计部署配置），不实施强制化。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- 过渡并存配置（credentialId 与静态密钥并存窗口期）的运维复核提示已落 runbook（`docs-for-ai/03-modules/nop-credential.md` 深度迁移章节步骤 2/4 + 并存窗口复核条目）——运行时静态值不可达死值，无安全暴露，属运维治理。
- `scan-hollow-implementations.mjs` 的 `--module` 多标志互相覆盖（第二个覆盖第一个——W16-impl closure log 的 "Total 0" 实为仅扫描了 nop-metadata；单标志扫描 nop-integration 在 HEAD 基线即有 1 条 pre-existing 占位注释发现（JavaEmailSender `// probably from a placeholder`，stash 对照证实），本批次 0 NEW）。建议工具修复为多模块累积；与本 plan 交付无涉。
- checkstyle 非项目 lint 门禁在案（mission verify 轮口径：9166 条违例全在既有框架代码）；本批次新增代码与 W16-impl 首批先例（TencentSmsSender 同为 JavadocVariable/JavadocStyle 风格画像）一致。

## Closure

Status Note: 扩展批次全量落地且经独立 closure audit 核验通过——五家族 credentialId 接线（Email×2/SFTP 逐次消费期、Feishu start 期解析副本、OSS 构造期，分层时序按设计 §4.1 结论 6）+ feishu/oss 配置键（含 OSS @ConfigField camelCase 键名实测裁定）+ 五类型实例（设计 §4.3 八类型全量）+ 五枚 consumerRef token + 三项路由裁定（§七#1 维持 optimization candidate（watch 锚点测试在案）/#6 再延 out-of-scope（A1 #5 先例）/#7 维持 watch-only + 前提变化登记）+ Email MFA 发码链 E2E + owner docs/设计/roadmap 同步。76 新测试用例（11+16+17+2+10+5+12+3 五家族及绑定/E2E + registry 6）+ 七模块门与 mission 门全绿。执行期偏离均为形态定稿（plan 各 Phase 标注），无语义偏离。
Completed: 2026-08-18

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit fresh subagent（task ses_feb75b744ffehC4b20dYd38aLo，非执行 session）
- Audit Session: ses_feb75b744ffehC4b20dYd38aLo
- Evidence:
  - 13 验证区域全 PASS（五类型实例逐字段/五家族接线逐点（TencentEmailSender :87-112/:134-150、JavaEmailSender withTransport :152 先于 try :154、FeishuClient 副本 :132/:189-192 + ensureToken/attemptReconnect 读捕获值、OssFactory :71/:83-88/:113-114、SftpClient :108 先于包装 :110 + 三消费点 seam）/ 测试真实接线断言抽查（穿透/码独立/真实容器绑定/真实 defaults 装载/E2E 真实 bindMfa 链）/ Anti-Hollow 五家族 + E2E 全链运行时追踪 / 静态零回归 git diff（测试文件仅增量）/ 文档四侧 / 验证日志复核（含独立复跑两工具均退出码 0）/ Deferred 诚实性 / 文本一致性）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（审计时点 Passed: 1, Failed: 0；收口后复跑同 0）
  - Anti-Hollow 检查结果：调用链运行时连通（credentialId → CredentialResolutionSupport → provider → 各家族消费点；E2E bindMfa → 发码 → 解析 → Transport）；`scan-hollow-implementations --module nop-integration --severity high` 单标志 Total 1 = pre-existing（HEAD :105 同条，stash 对照）= **0 NEW**
  - 全量验证：七模块 `-am` 门 BUILD SUCCESS（`_tmp/gate-final-seven.log`，auth-service 363 = 360 基线 + 3 E2E）；mission 门 `-T 1C` BUILD SUCCESS（`_tmp/mission-gate-final.log`）；`check-doc-links --strict` 0 errors（2324 文件/25905 引用）；首 run rocksdb benchmark flake 按在案口径单模块复跑绿（ratio 0.878）+ 整门复跑全绿
  - Deferred 项分类检查：三条（§七#1 optimization candidate / §七#6 out-of-scope improvement / §七#7 watch-only residual）均带 Why Not Blocking + Successor no；Non-Blocking Follow-ups 无已确认 live defect（scan-hollow 多模块标志覆盖为工具治理项，已诚实披露）
- Findings: 0 Blocker / 0 Major / 3 Minor（未提交（收口后即提交）/一处 .rels 环境触碰（已 git checkout 还原）/Closure 段待填（本段落即履行））

Follow-up:

- 过渡并存窗口运维复核（runbook 指引在案，Non-Blocking Follow-ups 登记）
- scan-hollow 工具 `--module` 多标志覆盖治理项（Non-Blocking Follow-ups 登记）
- 设计 §七#1（飞书刷新期再解析）按 optimization candidate 待需求实证；§七#6（testCredential 探测语义）待需求实证——均无悬挂
- no remaining plan-owned work beyond the above registered non-blocking follow-ups
