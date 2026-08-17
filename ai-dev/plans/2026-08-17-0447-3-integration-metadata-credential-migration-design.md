# W16-design nop-integration / nop-metadata credentialId 深度迁移设计（设计文档产出 + 分节独立 review）

> Plan Status: completed
> Mission: nop-credential-mfa
> Work Item: W16-design（深度迁移设计）——迁移二期组设计工作项，依赖 W2/W7/W7-successor（均已 done）
> Last Reviewed: 2026-08-17（执行收口：三 Phase 全勾选 + 独立 closure audit CAN CLOSE，证据见 Closure 段；draft review 一轮 READY 记录：0 Blocker/0 Major/8 Minor，5 项当场落地）
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md` W16-design 条目 + stage 19；W7-successor 先例链（`IAiModelCredentialResolver`）；`ai-dev/design/nop-credential/01-architecture-baseline.md` §3.4/§3.5（消费侧契约）；`ai-dev/design/00-design-writing-guide.md`（设计文档规范）
> Related: W7 `2026-08-13-1118-2-legacy-migration-docs-sync.md`（一期迁移 + 运行时切换 deferred 登记——Successor Required: yes → 与本工作项合流）；W7-successor `2026-08-13-1118-3-nop-ai-model-credential-runtime-consumption.md`（先例链实现）；姊妹二期设计 `ai-dev/design/nop-credential/02-phase2-design.md`（OAuth/KMS/归属/RBAC 已落地面 = 本设计的消费基座）

## Purpose

产出一份设计文档（`ai-dev/design/nop-credential/03-integration-metadata-migration-design.md`），收敛 nop-integration 渠道密钥与 nop-metadata 数据源密码接入凭证库的迁移方案：全量引用点枚举（live 锚点）、两类消费形态的接入模型裁定（integration 为"无 DB 实体的配置装配形态"、metadata 为"DB 实体 JSON 列形态"）、优先级链与 fail-closed 语义、回滚路径、归属/授权消费点、out-of-scope 裁定。每主题小节经独立 review 门槛（同 W9-design/W12-design 先例：小节独立对抗审查回修后收口）。**本 plan 只交付设计文档，不写实现代码**——W16-impl 是 successor 工作项。

## Current Baseline

（2026-08-17 live repo 核对，独立 explore agent 复核锚点）

- **nop-integration 渠道密钥现状 = "纯 JavaBean 配置装配，无 DB 实体、无 beans.xml 装配值"**：
  - SMS/Email 发送器为普通类 + setter 注入，由消费方应用在 beans.xml 提供值：`TencentSmsSender`（appId/appKey/sign，`nop-integration/nop-integration-sms-tencent/.../TencentSmsSender.java:29-43`，secret 消费 :47/:91）、`YunpianSmsSender`（apiKey，`.../yunpian/YunpianSmsSender.java:30`，消费 :40/:51）、`TencentEmailSender`（secretId/secretKey，`.../email/tencent/TencentEmailSender.java:29-31/:47-57`，消费 :61）、`JavaEmailSender` + `MailConfig`（username/password，`MailConfig.java:40-45`，`transport.connect` 消费 `JavaEmailSender.java:104-113`）。
  - 配置注入变体：`FeishuCredentials`（`@InjectValue("@cfg:nop.integration.feishu.appSecret|")` 等，`.../feishu/client/FeishuCredentials.java:33,42,51,60`；支持 `@sec:` 前缀经 `DefaultConfigValueEnhancer` + `AESTextCipher` 解密——javadoc :9-19）、`OssConfig`（accessKey/secretKey 平凡字段 + `ioc:config-prefix`，`oss-defaults.beans.xml:25`）、`SftpConfig`（`@ConfigBean` password/passphrase，`SftpConfig.java:12-19`）。
  - **nop-integration 下无任何 `*.orm.xml` / `NopIntegration*` 实体**（repo 级核实）——渠道密钥不入 DB，迁移设计必须先裁定消费模型（这是与 W7-successor 最大的形态差异：nop-ai 有 `NopAiModel.credentialId` 列可挂引用，integration 没有现成挂点）。
  - 运行时消费方：`LoginServiceImpl`（`@Inject @Nullable ISmsSender`，`nop-auth-service/.../login/LoginServiceImpl.java:171-173`，fail-closed 发送 :660-671）与 `NopAuthUserBizModel`（同型注入 :110-114，:457-465）。
- **nop-metadata 数据源密码现状 = "DB 实体 JSON 列明文"**：
  - 实体 `NopMetaDataSource`（表 `nop_meta_data_source`，`nop-metadata/model/nop-metadata.orm.xml:377-429`）——密码在 `CONNECTION_CONFIG` 列（`json-4000`，tagSet `sensitive`，:395-397）内明文 JSON `{jdbcUrl, username, password, driverClassName}`；无独立 PASSWORD 列。
  - 消费链：`connectionConfig` JSON 从实体读出（`NopMetaDataSourceBizModel.java:126/:194/:304`、`NopMetaTableQueryAction.java:121,141`、`MetaJoinExecutor.java:364,445`、聚合处理器等 ~10 处）→ `MetaDataSourceConnectionProcessor.buildDataSource` 解析（password 提取 `:180`）→ `ds.setPassword(password)` `:192`（非池化 `SimpleDataSource`）→ `SimpleDataSource.getConnection` → `DriverManager.getConnection`（`nop-persistence/nop-dao/.../SimpleDataSource.java:45-49`）。
  - 既有安全缓解：JDBC URL 协议/危险参数/主机白名单校验（`validateJdbcUrl` :216-247）、驱动类白名单（:405-410）、错误参数脱敏（`redactJdbcUrl` :257-260）。**密码本身无 `@sec:`/`cv1:` 解密路径**（仅 ORM 展示层脱敏 tag）。
- **W7-successor 先例链（本设计的参照模板）**：`IAiModelCredentialResolver`（`nop-ai/nop-ai-api/.../credential/IAiModelCredentialResolver.java:42`，单方法 `resolveApiKeyByCredential(provider, model)`）→ `AiModelCredentialResolverImpl`（`nop-ai-service/.../credential/AiModelCredentialResolverImpl.java:47`，查 `NopAiModel.credentialId` → `ICredentialProvider.getCredentialData(credentialId, "apiKey")` :117-131，configured-but-broken fail-closed）→ 消费钩点 `ChatServiceImpl.buildHttpRequest` → `resolveApiKeyForRequest`（`nop-ai-core/.../ChatServiceImpl.java:242-293`，优先级链 accountKey > credentialId > resolveApiKey）。
- **凭证库消费基座（二期后 live 面）**：`ICredentialProvider`（`nop-credential/nop-credential-api/.../ICredentialProvider.java:20`；getCredential/getCredentialData/testCredential/mask/registerUsage/unregisterUsage）+ 归属（scope/ownerId，Part A）+ RBAC 授权判定（`NopCredentialAuth`，Part B）+ OAuth 类型/KMS（W9/W10）；引用计数消费先例 `ai:NopAiModel:<id>`。
- **一期 W7 deferred 合流点**：W7 plan Deferred 登记"消费方运行时读取路径切换"Successor Required: yes →「与 nop-integration/nop-metadata 二期迁移可合并」——nop-ai 侧已由 W7-successor 单独收口，本设计只处理 integration/metadata 两模块（该 deferred 的合并语义已兑现，无遗留）。
- **依赖已满足**：W2/W7/W7-successor 均 `done`；W16-design 为迁移组第一个工作项，`todo`。

## Goals

- 设计文档落盘 `ai-dev/design/nop-credential/03-integration-metadata-migration-design.md`，含五类内容：
  1. **引用点全量枚举基线**（live 锚点）：integration 各 sender/config 类的 secret 字段与消费点、metadata `connectionConfig` 的 ~10 处消费链——作为 W16-impl 的变更输入清单（枚举不全 = impl 漏接线，参照 §5.3.0 白名单清单先例）。
  2. **integration 接入模型裁定**：无 DB 挂点形态下的消费方式决策（候选至少含：配置声明 `credentialId` 经运行时 resolver 解析 / 新增集成配置实体 / 混合），含拒绝理由与依赖边评估（nop-integration 各子模块 → nop-credential-api 的依赖方向约束）。
  3. **metadata 接入模型裁定**：`connectionConfig` JSON 内 `credentialId` 引用 vs 独立列 vs 混合的取舍；解析层落点（`MetaDataSourceConnectionProcessor.buildDataSource` 单点 vs 各消费方）；明文 password 的兼容/迁移/回滚语义。
  4. **横切契约**：优先级链（既有配置 > credentialId，或裁定其他序）+ fail-closed（configured-but-broken 拒绝，对齐 W7-successor 语义）+ 引用计数键规范 + 归属（system 级为主）与 RBAC 授权判定消费点 + 回滚路径（凭证库故障时是否允许回退明文——须显式裁定并给出安全论证）+ 迁移工具面（存量数据迁移命令/脚本语义）。
  5. **out-of-scope / deferred 裁定表**（classification + 理由，对齐 02-phase2-design §七 先例）。
- 每主题小节经独立 subagent 对抗 review（想象性分析：W16-impl 执行者能否仅凭设计列出全部触点与语义），回修至共识。
- 与既有文档的一致性：`ai-dev/design/nop-credential/README.md` 索引更新；引用约束遵守 design guide（不引用 discussions/analysis；可引用 lessons/design/docs-for-ai/源码）。

## Non-Goals

- 任何实现代码 / ORM 变更 / DDL / beans 装配（W16-impl 范围）。
- nop-ai 消费链重构（W7-successor 已收口；仅作先例引用）。
- 新凭证类型声明的实施（若设计裁定需要 `sms-channel`/`jdbc-datasource` 等新 `*.credential-type.xml` 类型，只裁定类型清单与字段 schema，不落实例文件）。
- `@sec:` 配置加密路径改造（一期已裁定配置文件静态密钥继续走 `@sec:`，凭证库只服务 DB 行级数据——本设计不翻案；integration 配置装配形态若维持 `@sec:` 为主，属裁定输出而非实施）。
- 凭证库自身能力演进（OAuth/KMS/归属/RBAC 已由 02-phase2-design 收口）。
- Web 管理页面。

## Scope

### In Scope

- `ai-dev/design/nop-credential/03-integration-metadata-migration-design.md`（新建设计文档，四主题小节 + 裁定表）。
- `ai-dev/design/nop-credential/README.md` 与根 `ai-dev/design/README.md`（索引条目更新）。
- 设计引用的 live 锚点复核（枚举清单必须与 live code 一致）。
- 独立 review 轮次（每主题小节至少一轮 fresh subagent 对抗审查）。
- roadmap W16-design 状态更新。

### Out Of Scope

- `nop-integration/`、`nop-metadata/`、`nop-credential/` 的任何代码/模型/配置变更。
- W16-impl plan 起草（successor 工作项，由后续 DRAFT 轮产出）。
- docs-for-ai 模块文档更新（设计未实施前无 live 行为可同步）。

## Execution Plan

### Phase 1 - 引用点盘点基线落盘

Status: completed
Targets: `ai-dev/design/nop-credential/03-integration-metadata-migration-design.md`（基线小节）

- Item Types: `Proof | Decision`

- [x] **Proof**：live 复核并全量枚举（不引用旧文档结论，逐点 grep/读码验证）：integration 侧全部 secret 持有类与消费点（含 SMS/Email/Feishu/OSS/SFTP 家族）**与值供给面**（配置键 `nop.integration.feishu.*`/`nop.integration.oss.*`、`@cfg`/`@sec` 约定、`ioc:config-prefix` 绑定、下游 beans.xml 装配值——仓库内无装配值的事实本身是迁移工具语义的输入）、metadata 侧 `connectionConfig` 全部读点与最终 `DriverManager` 汇聚点、两侧既有安全缓解（`@sec:` 可用性/URL 校验/脱敏 tag）；落盘为设计文档的引用点基线小节（file:line 锚点表）。
- [x] **Decision**：威胁与需求陈述：明文存储面（DB JSON 列明文/配置文件明文）、轮换能力缺口、审计缺口——作为迁移动机与验收语义来源。

Exit Criteria:

- [x] 引用点基线小节与 live code 一致（锚点抽查可对号；`rg` 复核无遗漏消费点——以 secret 字段名/`connectionConfig`/`setPassword`/配置键前缀 `nop.integration.` 等关键词全量扫描，含值供给面）。
- [x] roadmap W16-design 行为 `planned` 且 Work Items 行引用本 plan 路径（W9-design 先例；若起草收口时已更新则视为已满足）。
- [x] No new test required: documentation-only plan（设计产出，无代码变更）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 四主题设计裁定与文档撰写

Status: completed
Targets: `ai-dev/design/nop-credential/03-integration-metadata-migration-design.md`

- Item Types: `Decision`

- [x] **Decision（integration 接入模型）**：无 DB 挂点形态的消费方式裁定——候选方案对比（运行时 credentialId 解析 / 新集成配置实体 / 维持 @sec: + 仅裁定边界）、依赖边方向与模块边界（nop-integration-* 各子模块是否允许依赖 nop-credential-api，还是 resolver 接口留在各消费模块）、发送器初始化时序（凭证惰性解析 vs 构造期解析，凭证轮换/禁用对长生命周期 sender 实例的可见性）。
- [x] **Decision（metadata 接入模型）**：`credentialId` 挂点裁定（JSON 内字段 vs 独立列 vs 混合）、解析层落点（单点 `buildDataSource` vs 多消费方）、兼容矩阵（存量明文行/新 credentialId 行/两者并存优先级）、`testConnect` 等管理面语义。
- [x] **Decision（横切契约）**：优先级链与 fail-closed 语义（对齐 W7-successor：configured-but-broken 拒绝，不允许静默回退明文——回滚路径单独裁定并论证）；引用计数键规范（consumerRef 命名，`metadata:NopMetaDataSource:<id>` 先例对齐）；归属（system 级）与 RBAC 授权在两侧消费点的判定语义（服务级信任上下文，对齐 02-phase2-design §6.3）；迁移工具语义（存量转换命令、断点续跑、明文清除时点）；回滚路径（凭证库不可用时数据源可用性的安全论证——fail-closed vs 显式降级开关）。
- [x] **Decision（out-of-scope/deferred 表）**：逐项 classification + 理由（候选：Feishu/OSS/SFTP 家族是否纳入首期 impl、邮件发送器家族、Web 管理面、多环境密钥分离等）。
- [x] **Decision（文档组织）**：遵守 design guide——决策 + 理由 + 拒绝了什么 + 约束边界；不写类签名/方法列表/实现步骤（接口名仅限契约定义）；不引用 discussions/analysis；W7-successor 先例链经**源码锚点 + `01-architecture-baseline.md` §3.4/§3.5** 进入设计文档（plan 路径仅出现在 impl 裁定回写标注语境——`02-phase2-design.md` §八 先例，不作为设计依据引用）。

Exit Criteria:

- [x] 四主题小节齐备，每个裁定点有"选了什么/为什么/拒绝了什么"。
- [x] 设计自洽性检查：与 01-architecture-baseline 消费侧契约（§3.4/§3.5）和 02-phase2-design（归属/RBAC/OAuth/KMS 已落地面）无冲突；冲突处显式标注并裁定优先级。
- [x] No new test required: documentation-only plan（设计产出，无代码变更）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 分节独立 review + 收口

Status: completed
Targets: 设计文档、`ai-dev/design/nop-credential/README.md`、roadmap

- Item Types: `Proof | Follow-up`

- [x] **Proof**：每主题小节独立 fresh subagent 对抗 review（想象性分析模板：假想自己是 W16-impl 执行者，仅凭该小节能否列出全部变更文件与语义、是否有断层/歧义/不可验证项）；发现的问题回修后复审至无 Blocker。
- [x] **Proof**：全文一致性核对（小节间交叉引用、锚点行号复核、与 guide 规范符合性）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] **Follow-up**：`ai-dev/design/nop-credential/README.md` 与根 `ai-dev/design/README.md` 索引更新（根索引 nop-credential 行现描述止于二期设计，须同步 03- 文档——W9-design 双 README 先例）；roadmap W16-design 状态更新（`done` 判定交由本 plan closure audit）。

Exit Criteria:

- [x] 全部小节通过独立 review（review 证据：task/session 标识 + 问题清单 + 回修记录，落 daily log——过程性记录不入设计文档）。
- [x] 设计文档与 live code 锚点一致（抽查复核）。
- [x] 文档链接检查通过。
- [x] 双 README（子目录 + 根索引）已更新且链接有效。
- [x] No new test required: documentation-only plan（设计产出，无代码变更）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 设计文档落盘且四主题小节齐备（引用点基线/integration 模型/metadata 模型/横切契约 + out-of-scope 表）。
- [x] 引用点枚举与 live code 一致（全量关键词扫描复核，无遗漏消费点）。
- [x] 每主题小节经独立 review 至无 Blocker（证据在案）。
- [x] 与既有设计（01-baseline/02-phase2）无未裁定冲突。
- [x] W16-impl 可凭设计独立列出变更面（想象性分析验证通过）。
- [x] **纯文档计划**：无代码变更，`./mvnw` 构建验证条目按 guide 模板豁免（Closure Gates 移除构建/测试项）。
- [x] 独立子 agent closure-audit 已完成并记录证据（设计 vs live 一致性抽查 + review 轮次完整性）。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。

## Deferred But Adjudicated

（起草时空缺——Phase 2 out-of-scope 表为设计交付物；plan 级延期项按 Anti-Slacking 规则执行中填充。）

## Non-Blocking Follow-ups

- W16-impl plan 起草归属后续 DRAFT 轮（successor 工作项登记于 roadmap，无需本 plan 携带）。

## Closure

Status Note: 纯设计工作项收口——设计文档 `ai-dev/design/nop-credential/03-integration-metadata-migration-design.md` 落盘（八节：结论索引/迁移动机与威胁/引用点基线/integration 模型/metadata 模型/横切契约/out-of-scope 表/W16-impl 映射），三 Phase 全部执行完毕；W16-impl（successor）在 roadmap 登记待后续 DRAFT 轮。
Completed: 2026-08-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh closure-audit subagent（task `ses_ff1d1b3d5ffec4TPNP8LjCioOl`）
- Evidence:
  - Phase 1 Exit Criteria 全 PASS：§三基线 25+ 锚点抽查全对号（integration 家族 7 类 + metadata 14 读点/9 文件逐点 rg 复核 + processor/汇聚链/xmeta 四禁 + 先例链 + `BeanDefinitionBuilder.autowireProps` 注入前提）；roadmap :53 `planned` 引用本 plan；daily log Phase 1+2 条目在案。
  - Phase 2 Exit Criteria 全 PASS：四主题 + §七表齐备，候选对比表均含结论+理由；与 01-baseline §3.5 迁移表、02-phase2 §4.3/§6.3 零矛盾。
  - Phase 3 Exit Criteria 全 PASS：四 review session（task id 与 findings 清单见 daily log）+ 回修复审 9 组全 RESOLVED；双 README 更新核实；auditor 自跑 `check-doc-links.mjs --strict` exit 0（2305 文件/0 错误）；设计文档无 discussions/analysis/plan 路径引用（rg 无匹配）。
  - Closure Gates 全 PASS：W16-impl 可凭 §八 独立列出变更面；纯文档豁免声明在案（无 mvn 门）。
  - Honesty 检查 PASS：§七 11 行均带 Classification+理由；诚实权衡登记在案（§5.2 列方案 xmeta 权衡、§七#7 主路径暴露面声明）；Deferred But Adjudicated 空缺为 plan 显式裁定。
  - `node ai-dev/tools/check-plan-checklist.mjs 2026-08-17-0447-3-integration-metadata-credential-migration-design.md --strict` 退出码 0。
- 审计结论：CAN CLOSE（唯一遗留 = 执行者收尾四步：plan 状态翻转/roadmap done/Closure 证据写入/checklist 复跑——本段与 roadmap 更新即该四步的执行）。

Follow-up:

- no remaining plan-owned work（W16-impl plan 起草归后续 DRAFT 轮，roadmap 已登记依赖 W16-design）
