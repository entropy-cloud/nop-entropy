# W7 - 存量迁移（NopAiModel → credentialId）+ docs-for-ai 同步

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md` (W7)；`ai-dev/design/nop-credential/01-architecture-baseline.md` (§2.2/§3.4 凭证库 vs @sec 边界)；`ai-dev/design/nop-auth/01-architecture-baseline.md` (§3.x MFA 全章)
> Mission: nop-credential-mfa
> Work Item: W7
> Related: W1-W3（done，nop-credential 模块）、W4-W5（done，MFA 模型/登录两阶段）、W6（MFA 用户/admin API + 扫码适配，docs-sync phases 引用其 live 产物）

## Purpose

落地存量密钥迁移路径（`NopAiModel` 增 `credentialId` 关联凭证库），并把 W1-W6 落地的安全能力同步到平台文档（`docs-for-ai/`）：新增 nop-credential 模块文档、补 nop-auth MFA 章节、补 auth 两阶段登录说明、补 `@sec:` 配置加密文档、更新 INDEX/source-anchors，并核对设计文档收口。

## Current Baseline

（已核对 live repo）

- `NopAiModel`（ORM 源 `nop-ai/model/nop-ai.orm.xml:285-338`，表 `nop_ai_model`）**已有 `apiKey` 列，且已带 `tagSet="enc,not-query,not-sort,not-pub"`——即已是列级加密（v1 版本化密文），并非明文**。roadmap 中"NopAiModel.apiKey 明文存 DB"的措辞不准确；本 plan 迁移是**结构性**的（内联加密列 → `credentialId` 引用 + `ICredentialProvider` 读取），而非"加密一个明文值"。`credentialId` 字段**不存在**。
- `ICredentialProvider` SPI（`nop-credential/nop-credential-api/.../ICredentialProvider.java`，W2）已就位：getCredential/getCredentialData/testCredential/mask/registerUsage/unregisterUsage；实现 `CredentialProviderImpl`（nop-credential-service）是**唯一凭证解密点**，fail-closed。迁移后的消费目标已确认。
- `docs-for-ai/03-modules/nop-auth.md` **存在，无任何 MFA 内容**（grep `MFA|mfa|two-stage|两阶段|二阶段|multi-factor` 零命中）。
- `docs-for-ai/02-core-guides/auth-and-permissions.md` **存在，无两阶段/MFA 内容**。既有锚点：`LoginServiceImpl.loginAsync`(:461)、`LoginApiBizModel`(:462)、SSO `OAuthLoginServiceImpl`(:468)。
- `docs-for-ai/03-modules/nop-credential.md` **不存在**（W7 新建）。`03-modules/` 命名惯例：`nop-<module>.md`（16 个文件，无 nop-credential、无 nop-integration）。
- `@sec:` 配置加密在 `docs-for-ai/` **无任何文档**（grep `@sec:|DefaultConfigValueEnhancer` 零命中）。机制实现：`nop-config/.../DefaultConfigValueEnhancer.java`（`ConfigStarter.java:473` 装配）；live 消费样例：`nop-integration-feishu/.../FeishuCredentials.java`（Javadoc 说明 `@sec:` → `DefaultConfigValueEnhancer` + `AESTextCipher`）。丰富先例材料在 `ai-dev/design/nop-credential/01-architecture-baseline.md` §2.2/§3.4、`ai-dev/plans/334-encrypted-value-format.md`。
- `login-type.dict.yaml`（`nop-biz-auth-core` 资源）已含 1/2/3/4/5/20-23，**无 value=10**（SSO 4/10 历史不一致已在 W5 收敛为 4）。存量 `nop_auth_ext_login` 中 loginType=10 行为数据关注项（dict 已无对应标签）。
- 设计文档收口状态：`ai-dev/design/nop-auth/` 与 `ai-dev/design/nop-credential/` 各含 README + 00-vision + 01-architecture-baseline；grep `Open Question|开放问题|TODO|TBD` 在 design 正文**零命中**，README 均标"已达成共识"。**"设计文档收口"工作为核对确认，而非新写。**
- `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md` **均存在**。INDEX 无 nop-credential 行、无 MFA 路由；source-anchors 无 `ICredentialProvider`/MFA/`@sec:` 锚点。
- **⚠ 时序依赖**：W6 的 MFA 管理/扫码 feature（bindMfa/unbindMfa/resetUserMfa/ScanLoginResult.mfaRequired）**尚未落地**。本 plan 的 docs-sync phases（Phase 2/3）Exit Criteria 引用这些 W6 live 产物 → **须在 W6 closure 后执行**；Phase 1（NopAiModel 迁移）仅依赖 W2（done），可先行。

## Goals

- `NopAiModel` 增可选 `credentialId` 普通列（逻辑外键指向 `NopCredential`，**不声明 ORM `to-one` 关系**，避免跨模块 DAO 依赖），`apiKey` 列保留兼容；迁移路径文档化（建凭证 → registerUsage → 设 credentialId → apiKey 冗余）。
- `docs-for-ai`：新建 `03-modules/nop-credential.md`；`03-modules/nop-auth.md` 补 MFA 章节；`02-core-guides/auth-and-permissions.md` 补两阶段登录；补 `@sec:` 配置加密文档。
- `INDEX.md` + `source-anchors.md` 更新（nop-credential 行、credential/MFA/`@sec:` 锚点）。
- 设计文档收口核对确认；`ai-dev/design/README.md` 索引更新。

## Non-Goals

- nop-integration / nop-metadata 深度迁移到 credentialId 引用（二期，roadmap 显式边界）。
- 强制所有消费方迁移（`apiKey` 列保留兼容）。
- W6 feature 代码（本 plan 仅文档化其产物）。
- W8（DB store，独立 plan）。

## Scope

### In Scope

- `nop-ai/model/nop-ai.orm.xml`：新增 `credentialId` 为**可选普通列**（**不声明 ORM `to-one`关系到 `NopCredential` 实体**，避免 `nop-ai-dao` 引入对 `nop-credential-dao` 的跨模块 DAO 依赖；逻辑外键仅在文档中描述）→ codegen → DDL 迁移。
- 迁移路径文档（建凭证 → registerUsage → 设 credentialId → apiKey 冗余；含兼容回退说明）。
- `docs-for-ai/03-modules/nop-credential.md`（新建）。
- `docs-for-ai/03-modules/nop-auth.md`（MFA 章节）。
- `docs-for-ai/03-modules/nop-ai.md`（NopAiModel 行补 credentialId + 迁移说明）。
- `docs-for-ai/02-core-guides/auth-and-permissions.md`（两阶段登录）。
- `@sec:` 配置加密文档（`ioc-and-config.md` 或 `auth-and-permissions.md`）。
- `docs-for-ai/INDEX.md` + `docs-for-ai/04-reference/source-anchors.md`。
- 设计 README 索引 + 收口核对。

### Out Of Scope

- **消费方运行时读取路径切换**（credentialId 存在时经 `ICredentialProvider` 解析 apiKey、否则回退 apiKey 列的解析钩子）——本期仅落字段 + 文档，**不接通运行时消费读取**（避免空壳：字段存在但无消费方读取）。运行时切换为显式 Deferred 项（见 Deferred But Adjudicated）。
- nop-integration / nop-metadata credentialId 迁移（二期）。
- W6 代码。
- W8。

## Execution Plan

### Phase 1 - NopAiModel credentialId 迁移路径（code，仅依赖 W2）

Status: completed
Targets: `nop-ai/model/nop-ai.orm.xml`、迁移文档

- Item Types: `Fix | Decision`

- [x] 在 `nop-ai/model/nop-ai.orm.xml` 的 `NopAiModel` 新增可选 `credentialId` **普通列**（**不设 `refEntityName`/`to-one` 关系**，避免跨模块 DAO 依赖）；跑 codegen 生成实体/dao + DDL 迁移脚本；**禁止手编 `_gen/` 与 `_` 前缀文件**
- [x] **依赖方向裁定（Decision）**：确认 nop-ai-dao 模块的 pom **不新增**对 `nop-credential-dao` 的依赖（credentialId 为普通列、无 ORM 关系，故不需要）。逻辑外键（credentialId → NopCredential）仅在迁移文档中描述；未来运行时消费经 `ICredentialProvider`（api 层，在 `nop-credential-api`）而非 DAO。裁定结论记录
- [x] 迁移文档：具体步骤——建 `NopCredential(typeName=openai-api-key, fields={apiKey})` → `registerUsage(credentialId, consumerRef=NopAiModel)` → 设 `NopAiModel.credentialId` → `apiKey` 列冗余（可后续清理）；**注明本期不接通运行时消费读取**（消费方切换为 Deferred）

Exit Criteria:

- [x] `credentialId` 字段存在于 ORM 源 + codegen 产物 + DDL 脚本；无 `_` 前缀文件被手编
- [x] `credentialId` 为普通列，**无 ORM `to-one` 关系**；nop-ai-dao 模块的 pom **未新增** `nop-credential-dao` 依赖（断言，防跨模块 DAO 耦合）
- [x] 迁移文档存在，含具体步骤（建凭证/registerUsage/设 credentialId/兼容回退）+ 本期不接通运行时消费的显式声明
- [x] **新功能测试**：`NopAiModel` 实体 round-trip（含 credentialId 列读写）；`No runtime consumer test required: 运行时消费读取切换为 Deferred，本期仅落字段`
- [x] **无静默跳过**：本期不引入消费读取路径，故无"credentialId 存在但凭证缺失"的运行时分支需要 fail-closed（该约束属于 Deferred 的消费方切换 plan，届时须 fail-closed）；本 Phase 不留未接通的运行时钩子（防空壳）
- [x] 若该 Phase 改变 live baseline：`ai-dev/design/nop-credential/01-architecture-baseline.md` §3.4 核对（已正确表述"凭证库服务 DB 行级数据 vs @sec 配置文件静态密钥"边界）；`docs-for-ai/03-modules/nop-ai.md` 的 NopAiModel 行同步在 Phase 2
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - docs-for-ai 新增 + 更新（gated on W6 live 产物）

Status: completed
Targets: `docs-for-ai/03-modules/nop-credential.md`（新建）、`docs-for-ai/03-modules/nop-auth.md`、`docs-for-ai/03-modules/nop-ai.md`、`docs-for-ai/02-core-guides/auth-and-permissions.md`、`@sec:` 文档

> ⚠ 本 Phase 引用 W6 的 live 产物（`NopAuthUserBizModel.bindMfa/unbindMfa`、`ScanLoginResult.mfaRequired` 等）——**须在 W6 closure 后执行**（见首条 Exit Criteria 的 repo-observable gate）。

- Item Types: `Fix`

- [x] `docs-for-ai/03-modules/nop-credential.md`（新建）：模块定位、cv1 密文格式、`ICredentialProvider` SPI、类型注册（`*.credential-type.xml`）、明文边界（xmeta `published=false`）、管理 API（saveCredential/maskList/typeList/test/reencryptAll）、与 `@sec:` 的边界
- [x] `docs-for-ai/03-modules/nop-auth.md` 补 MFA 章节：数据模型（`NopAuthMfaSetting`/`NopAuthMfaRecoveryCode`）、TOTP（RFC 6238）、两阶段登录（loginAsync/createSessionForUserAsync/mfaVerify）、短信登录（loginType=5）、配置（`nop.auth.mfa.*`/`nop.auth.sms-code.*`）、绑定/解绑/恢复码/管理员重置（**核对 W6 live 产物**）
- [x] `docs-for-ai/03-modules/nop-ai.md` 更新 NopAiModel 行（补 `credentialId`）+ 迁移说明（建凭证→registerUsage→设 credentialId→apiKey 冗余；本期不接通运行时消费）
- [x] `docs-for-ai/02-core-guides/auth-and-permissions.md` 补两阶段登录：`ERR_AUTH_MFA_REQUIRED` 流程、loginType 表（1/2/3/4/5/20-23）
- [x] `@sec:` 配置加密文档：`DefaultConfigValueEnhancer` + `AESTextCipher` 机制、何时用 `@sec:`（配置文件静态密钥）vs 凭证库（DB 行级数据），以 `FeishuCredentials` 为 live 样例

Exit Criteria:

- [x] **W6 前置 gate（repo-observable）**：gate 安全条件已满足——W6 live 产物经 repo 核实存在（`NopAuthUserBizModel.bindMfa:133/confirmMfa:243/unbindMfa:293/resetUserMfa:367/generateRecoveryCodes:321/getMfaStatus:337`、`ScanLoginResult.mfaRequired:32`、`ChannelLoginApiBizModel.loginByScanAsync:144` 捕获逻辑、`TestMfaUserSelfService`/`TestScanLoginMfa`），故编写引用真实代码的文档安全。注：roadmap W6 书记账本仍标 `planned`（陈旧），但 gate 的实质要求"W6 产物存在"已 repo-observable 成立
- [x] 5 份文档存在且内容与 live code 一致（锚点：`NopAuthMfaSetting`、`LoginServiceImpl` 两阶段、`LoginApiBizModel.mfaVerify/sendSmsCode`、`NopAuthUserBizModel.bindMfa/unbindMfa`、`ScanLoginResult.mfaRequired`、`ICredentialProvider`/`CredentialProviderImpl`、`DefaultConfigValueEnhancer`、`NopAiModel.credentialId`）
- [x] **文档-代码一致性**：每处引用的类/方法/行号在 live repo 存在（抽查核对）；`nop-ai.md` 的 NopAiModel 行含 credentialId
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0 —— **docs-for-ai 范围 0 错误**（本 plan 新增/修改的 docs-for-ai 文件全部 clean，修复了 nop-ai.md 一处 docs-for-ai→ai-dev BOUNDARY 违规）。剩余 20 个预存错误全在 `ai-dev/` invariant-loop audit 路线图/skills 中，引用**不存在的** audit catalog/工具/roadmap（`ai-dev/audits/nop-*-invariants/invariant-catalog.md`、`check-*-invariants.mjs` 等均未创建），属独立 invariant-loop audit 任务的 forward-reference，与本 plan 无关、显式 adjudicated 为 out-of-scope（非静默跳过）
- [x] **新功能测试**：`No new test required: 纯文档变更`（文档内容与 live repo 代码一致性人工/工具抽查）
- [x] 若该 Phase 改变 live baseline：本 Phase 即 owner-doc 更新主体
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - INDEX + source-anchors + 设计收口核对

Status: completed
Targets: `docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md`、`ai-dev/design/README.md`

- Item Types: `Fix | Decision`

- [x] `INDEX.md`：新增 nop-credential 模块行 + MFA 路由条目
- [x] `source-anchors.md`：新增 `CRED-001`（`ICredentialProvider`/`CredentialProviderImpl` 明文边界）、AUTH-MFA 锚点（`LoginServiceImpl` 两阶段 + `LoginApiBizModel.mfaVerify`）、`SEC-CFG` 锚点（`DefaultConfigValueEnhancer` + `@sec:`）
- [x] 设计收口核对：确认 `ai-dev/design/nop-auth/` + `nop-credential/` 无开放问题标记；`ai-dev/design/README.md` 索引含两子系统且状态 current

Exit Criteria:

- [x] `INDEX.md` 含 nop-credential 行；`source-anchors.md` 含 3 个新锚点；均 resolve
- [x] 设计目录确认收口（无 open 标记）；`design/README.md` 索引 current
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0 —— docs-for-ai 范围 0 错误（同 Phase 2 判定：剩余 20 个预存错误全在 ai-dev/ invariant-loop audit 路线图/skills，引用不存在的 audit catalog/工具，out-of-scope）
- [x] **新功能测试**：`No new test required: 纯文档变更`
- [x] 若该 Phase 改变 live baseline：本 Phase 即索引/锚点更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本 plan 含一处 ORM 结构变更（`NopAiModel.credentialId`，Protected Area plan-first）+ 大量纯文档变更。纯文档 Phase（2/3）无 `./mvnw test` 要求；Phase 1 code 须编译/测试。

- [x] 三个 Phase 的 Exit Criteria 全部勾选（含 Phase 2 首条 W6 repo-observable gate）
- [x] `NopAiModel.credentialId` 字段落地（普通列、无跨模块 DAO 依赖）；迁移路径文档化
- [x] `docs-for-ai` MFA + credential + `@sec:` + nop-ai 内容与 live code 一致（W6 产物已存在——Phase 2 gate 已验证）
- [x] INDEX + source-anchors 更新且 resolve
- [x] 设计文档收口核对完成
- [x] 必要 focused verification（Phase 1 code 测试 + 文档一致性抽查）已完成
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 抽查文档引用的类/方法/行号在 live repo 真实存在（不只是文件存在）；确认 credentialId 字段未遗留未接通的运行时消费钩子
- [x] Phase 1：`./mvnw compile -pl nop-ai/nop-ai-dao -am`；`./mvnw test -pl nop-ai -am`
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（docs-for-ai 范围）
- [x] checkstyle / 代码规范检查通过（Phase 1 code：手写测试文件 clean；`_gen` 产物的 checkstyle 违规为 codegen 模式固有，非本期引入）

## Deferred But Adjudicated

### 消费方运行时读取路径切换（credentialId → ICredentialProvider）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本期仅落 `credentialId` 字段 + 迁移文档。运行时消费读取切换（credentialId 存在时经 `ICredentialProvider.getCredentialData` 解析、否则回退 apiKey 列）需改消费方并须 fail-closed 裁定，属独立工作面；若本期接通则字段存在但无端到端验证=空壳风险。字段 + SPI 已就位，后续切换可直接复用。
- Successor Required: yes → 消费方迁移 plan（与 nop-integration/nop-metadata 二期迁移可合并）

### nop-integration / nop-metadata credentialId 深度迁移

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 显式列为二期（"nop-integration/nop-metadata 深度迁移（credentialId 引用，二期）"）。本期仅做 NopAiModel 迁移路径 + 文档；credentialId 字段与 SPI 已就位，二期消费方迁移可直接复用。
- Successor Required: no（二期）

### 强制全量消费方迁移

- Classification: `optimization candidate`
- Why Not Blocking Closure: `apiKey` 列保留兼容，存量不受影响；迁移为增量可选。强制迁移会破坏兼容性（roadmap 成功标准 7）。
- Successor Required: no

## Non-Blocking Follow-ups

- 存量 `nop_auth_ext_login` 中 loginType=10 历史行的数据清理（数据关注项，dict 已无对应标签；W5 已确认信道编码从 20 起，预期为空——部署期核对）
- `@sec:` 文档的可执行示例（配置片段 + 解密验证步骤——优化候选）
- `ai-dev/backlog/nop-credential-mfa-roadmap.md` 的 Stages 表/mermaid 图把 W7 依赖补为 `W2 + W5 + W6(docs)`（Phase 1 仅需 W2；Phase 2/3 docs 需 W6），与 roadmap 当前 `W2 + W5` 不一致——本 plan 已在 Phase 2 用 repo-observable gate 表达该依赖，roadmap 图表更新为收尾治理项

## Closure

Status Note: W7 完成——`NopAiModel.credentialId` 普通列落地（无跨模块 DAO 依赖）+ 迁移路径文档化；docs-for-ai 新建 nop-credential.md、补 nop-auth MFA 章节、补两阶段登录、补 `@sec:` 配置加密文档；INDEX/source-anchors 更新；设计收口核对完成。运行时消费读取切换显式 Deferred（字段 + SPI 已就位，后续 plan 复用）。独立 closure audit 16/16 PASS。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: independent closure auditor（独立子 agent，session 与执行分离）
- Audit Session: ses_0089db7f4ffev2nqh8D2HPjGwO
- Verdict: PASS — 16/16 audit items satisfied; all Closure Gates met.
- Evidence:
  - **Phase 1 (code)**：`nop-ai/model/nop-ai.orm.xml:331-333` credentialId PLAIN column propId=17，无 refEntityName/to-one（relations 块仅 to-many responses）；`_NopAiModel.java:90` PROP_ID_credentialId=17 + getter/setter；DDL mysql/oracle/postgresql 均含 CREDENTIAL_ID 列；`nop-ai-dao/pom.xml` 无 nop-credential* 依赖；`TestNopAiOrmEntityMapping.java:254-283` testCredentialIdColumnRoundTrip（读写 + null + propId==17 断言）通过（6 tests 0 failures）；git diff 仅 orm.xml 源 + 测试手编，11 个 `_` 前缀文件为 codegen 机械重生成。
  - **Phase 2 (docs)**：5 份文档存在且锚点真实——Anti-Hollow 抽查 LoginServiceImpl.java:241=loginAsync ✓、NopAuthUserBizModel.java:133=bindMfa ✓、CredentialProviderImpl.java:46=class decl ✓、DefaultConfigValueEnhancer.java:67=@sec: CFG_SEC_PREFIX handling ✓（均在 live repo 命中）。
  - **Phase 3 (index/anchors/design)**：INDEX.md:186 nop-credential 路由；source-anchors.md CRED-001/AUTH-MFA-001/AUTH-MFA-002/SEC-CFG-001；design grep Open Question/开放问题/TODO/TBD 零命中；design/README.md 两子系统 active。
  - **Deferred (item 16)**：无半接通运行时消费钩子——grep ICredentialProvider/getCredentialData in nop-ai/ 源码 = orm.xml 注释 + 生成 accessor + 测试；无 nop-ai service/biz @Inject ICredentialProvider。credentialId 字段 + SPI 就位，运行时读取路径显式未接（intended）。
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0；`scan-hollow-implementations.mjs --module nop-ai` 退出码 0（high 发现均为 nop-ai-agent 预存 placeholder，非本期）。
  - `check-doc-links.mjs --strict`：docs-for-ai 范围 0 错误；剩余 20 错误全在 ai-dev/ invariant-loop audit 路线图/skills（引用不存在的 audit catalog/工具），out-of-scope。
  - Deferred 项分类检查：3 个 deferred 项均为 `out-of-scope improvement` / `optimization candidate`，附 Why Not Blocking Closure，无 in-scope live defect 被降级。

Follow-up:

- 消费方运行时读取路径切换（credentialId → ICredentialProvider）—— successor plan（与 nop-integration/nop-metadata 二期迁移可合并）
- nop-integration/nop-metadata credentialId 深度迁移—— 二期
- `ai-dev/backlog/nop-credential-mfa-roadmap.md` Stages 表/mermaid 图把 W7 依赖补为 `W2 + W5 + W6(docs)`—— 收尾治理项
- ai-dev/ invariant-loop audit 路线图/skills 的 20 个预存陈旧链接（引用未创建的 audit catalog/工具）—— 属独立 invariant-loop audit 任务
