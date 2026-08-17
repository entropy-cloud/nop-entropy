# C1a 凭证库二期审计加固（A1-audit successor 代码修复）

> Plan Status: completed
> Completed: 2026-08-17
> Mission: nop-credential-mfa
> Work Item: C1-hardening Part A（凭证库二期审计加固——代码修复），A1-audit successor 收口（roadmap 建议拆分 C1a/C1b 中的 C1a）
> Last Reviewed: 2026-08-17（draft review 三轮达成共识：首轮 1 Blocker/2 Major/5 Minor 全部处置；二轮 1 Major（D2-04 锁内强制）+ 2 Minor 处置；三轮 diff 级确认 N1/N2 PASS、N3 自查关闭。审查 session：ses_ff1bb0bf0ffet7hfEz0H4JuS01 / ses_ff1acc7e1ffeU6CdfC638cUBeu / ses_ff1a065cfffeaY3MqjjQ8QwGGK）
> Source: `ai-dev/audits/2026-08/2026-08-17-0518-deep-audit-nop-credential/adjudication.md`（§1.2 successor 24 行（25 个 finding ID，含两处同根因合并行）+ §二 8 项路由终局裁定 + §三 successor 登记）；`summary.md`（32 findings 总表）；`ai-dev/backlog/nop-credential-mfa-roadmap.md` C1-hardening 条目
> Related: A1-audit `2026-08-17-0447-1-credential-phase2-security-audit.md`；C1b（@MfaRequired 标注）`2026-08-17-1345-2-credential-sensitive-actions-mfa-required-c1b.md`

## Purpose

修复 A1-audit 判定 `successor → C1-hardening` 的全部代码级 findings——adjudication §1.2 全部 **24 行（25 个 finding ID，含 D1-03/D4-07、D1-02/D4-05 两处同根因合并行）+ 复核观察项 2（wiring 测试增强）**，使凭证库二期审计遗留项收口为"零 successor 未落地"状态。本计划以加固与行为收紧为主，不引入新功能面；个别对外行为变化（错误码归一、registerUsage 前置校验）以审计裁定为准并同步 owner doc 与设计标注。

## Current Baseline

（2026-08-17 live repo 逐项核对；行号为当日快照，执行时以符号定位为准）

- **A1-audit 已 done**：32 findings 处置零悬挂——P1×2 根因（D2-01/D4-01、D6-01）plan 内 fixed；§1.2 successor 24 行（25 个 finding ID：P2 五行六 ID + P3 十八行十九 ID）+ 复核观察项 2 判定 `successor → C1`（本计划标的）；D5-02/D5-04 deferred（设计取舍已声明）。审计记录：`ai-dev/audits/2026-08/2026-08-17-0518-deep-audit-nop-credential/`。
- **P2 五个执行项 live 锚点**（六 finding ID，D1-03/D4-07 同根因合并为一项）：
  - D6-02：`nop-ai/nop-ai-service/.../entity/NopAiModelBizModel.java` 仅覆盖 `save`（:72-78）；`delete`/`batchDelete`/`deleteByQuery` 未接线 `unregisterUsage` → 模型删除后凭证删除被引用计数永久拦截（运维死锁）。`_NopAiModel.view.xml` 存在 batch-delete 按钮（**batchDelete 不可禁用，需接线**）；`ICredentialProvider.unregisterUsage` 按 (credentialId, consumerRef) 删除行、天然幂等。
  - D1-01：`NopCredentialBizModel.java` `batchGet`（:785-799）做可见性过滤但未 `setData(null)`+evict；`findList`/`findFirst` 完全未覆盖（继承面）。第一层 published=false 结构性阻断仍在。
  - D1-03/D4-07：写路径越权错误码三态可区分——`saveCredential` 更新路径 `assertWriteAllowed`（:406-421）抛 `ADMIN_REQUIRED`/`OWNER_OR_ADMIN`，`entity==null` 抛 `NOT_FOUND`；`OAuthFlowService.assertBeginOwnership` 同型。读路径（`get`/`delete`）已归一"不存在"。
  - D3-01：`VaultCredentialKeyProvider` 只读 `nop.credential.vault.active-key-id`（:124-127），静默忽略共享配置 `nop.credential.active-key-id`；local→vault 迁移可无声改变 active key。
  - D4-02：action-auth delta `nop-credential-web/.../auth/nop-credential.action-auth.xml` 多处 `roles="admin"`（delete/reencryptAll/Usage/Auth/OauthState）与 `CredentialConfigs.CFG_CREDENTIAL_ADMIN_ROLES` 缺省 `admin,nop-admin` 双源漂移——`nop-admin` 在 GraphQL 静态层被拒（更严方向）。roles 属性经 xdef 解析为角色集合（多角色可用）。
- **P3 十九个 finding ID live 锚点**（Phase 2 九项 + Phase 3 十项打包；复核观察项 2 见 Phase 4）：
  - `CredentialCipher.decrypt`（:90-123）：三处 `ARG_CIPHERTEXT` 全量密文入异常 param（:93/:103/:111，D1-04）；内层 `v1:` 前缀未强制（:120-122，D5-05——cv1 包装的 legacy 载荷可落入弱路径）；`CV1_KEY_ID_PATTERN` 常量在本类（service 模块）。
  - `DefaultCredentialKeyProvider`：`key-provider` 值无 trim/大小写归一（:86，D3-03）；passphrase 仅 `colonIdx == length-1` 检查、纯空白可通过（:107，D5-06）；`getMasterKeys()` public 明文返回 keyId:passphrase 列表、无生产调用点（:167，D5-07）。
  - `VaultCredentialKeyProvider`：keyId 正则硬编码复制（:62，D3-04）；`fetchMaterial` 无请求级超时（:256，D3-05）；返回材料仅 `isEmpty` 检查（:300，D5-06 覆盖点）；迁移残余条目 passphrase 同样仅 length-1 检查（:196，D5-06 覆盖点）。
  - `NopCredentialBizModel.reencryptAll`：`reencryptPageSize` 无下限校验，可配 0 → 空页死循环（:549/:621，D3-02）；非 cv1 前缀行 `continue` 静默跳过无信号（:595-597，D5-03）。
  - `OAuthFlowService`：`beginOAuthFlow` 只校验 clientSecret 非空、clientId 空则放空串（:179-183/:190，D2-02）；`buildResultPage` 以 `escapeHtml` 注入 JS 字符串上下文（:279，D2-05）。
  - `CredentialProviderImpl.engineUpdateInLock`（:298-329）：锁内 probe 只复查 delFlag 不复查 disabled（:309，D2-04）；updater 返回无变化（如惰性刷新"已被并发先行者刷新"分支）仍整行重加密回写（:321-326，D2-03——写放大 + version 漂移）。
  - `NopCredentialBizModel.saveCredential` 非 oauth2 更新路径无 delFlag fail-closed（:269 附近，D1-02/D4-05——oauth2 分支经 engineUpdateInLock probe 有检查，两分支不一致）。
  - `NopCredentialBizModel.batchGet` 缺 id 抛错 vs 不可见 id 静默剔除并存（D4-04，存在性 oracle 与文档口径不一致）。
  - `CredentialProviderImpl.registerUsage` 不校验凭证存在/未软删（D6-03——配错 credentialId 管理面静默成功）。
  - D6-04（isEmpty → isBlank，审计裁定**三个修复点**，分属两模块三文件）：`nop-ai-core/.../service/ChatServiceImpl.resolveApiKeyForRequest`（:287-292——accountKey/credApiKey 纯空白被当有效 key 使用，**核心风险点**）；`nop-ai-service/.../credential/AiModelCredentialResolverImpl`（:132——凭证 apiKey 字段纯空白通过 `ERR_AI_CREDENTIAL_FIELD_EMPTY` 防线）；`nop-ai-service/.../entity/NopAiModelBizModel.reconcileCredentialUsage`（:107-118——credentialId 纯空白走 bind 分支登记引用）。resolver 相邻的 :113/:118 空值回退分支不在审计清单，保持现状。
  - `NopCredentialUsageBizModel`：查询面已 admin-only（defaultPrepareQuery/get/batchGet），**mutation 面未收口**——7 个标准 mutation 可用，删除 usage 行可间接解锁被拦截的凭证删除（D4-06，与 D2-01 同型；delta roles="admin" 已收紧）。
- **测试增强标的**：`TestAiModelCredentialResolverWiring` 第 3 用例（:87-93）断言偏弱——`assertNotNull(getBean(...))` 仅隐式证明可选装配（复核观察项 2）。
- **测试/构建基线**：`./mvnw test -pl nop-credential -am` 全绿（service 171 / kms-vault 32 / web 1）；`-pl nop-ai -am` 全模块 SUCCESS（nop-auth-service 既有 missing-tenant-id flake 与本计划无关，单独运行全绿）。
- **不在本计划**：@MfaRequired 标注（C1b）；PKCE 等 4 项可选项（A1 §二 已裁定 optimization candidate，见 Deferred）；D5-02/D5-04（A1 §1.3 已终局 deferred）。

## Goals

- adjudication §1.2 登记的 successor 代码项（24 行/25 个 finding ID）全部落地（fixed），每项有 focused 测试或显式声明的测试豁免理由。
- 凭证删除的运维死锁路径闭合（模型删除 → usage 注销 → 凭证可删）。
- 写路径与读路径的越权错误面口径对称（credentialId 枚举探测存在性/scope 类别的能力消失）。
- nop-credential / nop-credential-kms-vault / nop-ai 三模块既有测试零回归（受控行为变化的断言同步更新除外）。

## Non-Goals

- 不做 @MfaRequired 标注（C1b 所有）。
- 不做 PKCE、oauth2 testCredential 真实连通性、state 批量清理、KMS 关窗自动化与 reencryptAll 进度上报（A1 §二 终局裁定为可选项，本计划显式再延并记录）。
- 不做 cv2 密文格式演进（D5-04 远期方向）、不做软删行重加密 includeDeleted 模式（D5-02 运维语义边界）。
- 不改 `ICredentialProvider` SPI 六方法签名（一期契约锚点）；不改 cv1 密文格式。
- 不触碰 MFA 组（W14/W15）与迁移组（W16）工作项。

## Scope

### In Scope

- `nop-credential/nop-credential-service`（BizModel/Provider/Cipher/KeyProvider/OAuthFlowService）
- `nop-credential/nop-credential-kms-vault`（VaultCredentialKeyProvider）
- `nop-credential/nop-credential-api`（keyId 正则单源下沉，若裁定）
- `nop-credential/nop-credential-web`（action-auth delta）
- `nop-ai/nop-ai-service`（NopAiModelBizModel delete 接线 + AiModelCredentialResolverImpl + NopAiModelBizModel reconcile 判空 + wiring 测试增强）
- `nop-ai/nop-ai-core`（ChatServiceImpl 优先级链 isBlank——D6-04 核心修复点所在模块）
- 对应 owner docs（`docs-for-ai/03-modules/nop-credential.md`、`nop-ai.md`）与 `ai-dev/design/nop-credential/02-phase2-design.md` impl 裁定回写标注
- 各模块测试目录（新增强化用例 + 受影响断言更新）

### Out Of Scope

- C1b 标的（@MfaRequired 四动作标注 + nop-biz-auth-api 依赖边）
- Deferred But Adjudicated 全部条目
- Web 页面改版、nop-credential-app 装配变化

## Execution Plan

### Phase 1 - P2 五个执行项修复（安全/运维闭环优先）

Status: completed
Targets: `nop-ai-service/.../entity/NopAiModelBizModel.java`、`nop-credential-service/.../entity/NopCredentialBizModel.java`、`nop-credential-service/.../oauth/OAuthFlowService.java`、`nop-credential-kms-vault/.../VaultCredentialKeyProvider.java`、`nop-credential-web/.../auth/nop-credential.action-auth.xml`

- Item Types: `Fix | Decision`

- [x] D6-02：`NopAiModelBizModel` 覆盖 `delete` 接线 `unregisterUsage`（删除前读 credentialId，删除后注销，同事务；provider 为 null 时跳过保持可选装配语义）；`batchDelete` 同接线（Web 页有 batch-delete 按钮，禁用会破坏既有管理面）；`deleteByQuery` 一并接线（先收集命中行的 credentialId 再删除后注销）或按 live 消费面核实后禁用——**Decision：三动作默认全接线，禁用需执行时给出消费面证据**
- [x] D1-01：`NopCredentialBizModel` 补齐 `batchGet`/`findList`/`findFirst` 的 `data` 置空 + evict（与 `get`/`findPage` 同口径，逐行防御性驱逐）
- [x] D1-03/D4-07：写路径越权错误码归一——`saveCredential` 更新路径与 `beginOAuthFlow` 发起路径的越权拒绝（system 级非管理员 / user 级非 owner）与"不存在"统一为同一对外响应。**归一载体在本 plan 收敛：`UnknownEntityException`（对齐 `get`/`delete` 单条资源访问先例，entity==null 现抛 `ERR_CREDENTIAL_NOT_FOUND` 一并归一）**；创建路径的 `ADMIN_REQUIRED`（无 credentialId 参与，无枚举面）与入参校验错误码保持不变；`beginOAuthFlow` 的 `DELETED`/`DISABLED`/`NOT_OAUTH2_TYPE` 等状态类错误码保留（审计范围仅越权三态，显式声明该边界）；`ARG_OWNER_ID` param 不进入对外可达异常（audit 建议的 param 卫生同步收口）
- [x] D3-01：Vault active-key-id 配置归一——vault 专用 `nop.credential.vault.active-key-id` 优先；未设时回退共享 `nop.credential.active-key-id`；两处同设且不一致时启动失败（fail-closed）——**Decision：回退语义优先于"仅启动校验"（消除静默改变 active key 的迁移陷阱）**
- [x] D4-02：action-auth delta 的 `roles="admin"` 对齐缺省 `admin,nop-admin`（delete/reencryptAll/Usage/Auth/OauthState 全部收紧点），owner doc 声明静态 roles 层不跟踪 `admin-roles` 配置变化、运行时层为准

Exit Criteria:

- [x] 五个执行项各自有 focused 测试。**D6-02 验证口径（mock-verify 级，不做跨模块集成装配）**：对齐既有 `TestNopAiModelCredentialUsage` 先例（mock `ICredentialProvider`），参数捕获断言 delete/batchDelete/deleteByQuery 真实调用 `unregisterUsage` 且 (credentialId, consumerRef) 正确；"usage 行存在 → 凭证删除被拦截"链路由 nop-credential 侧既有引用计数拦截测试覆盖（引用而非重复实现）
- [x] 既有断言旧三态错误码的测试同步更新且语义注记来源 finding 编号（越权保存/发起与不存在目标对外响应不可区分——统一 `UnknownEntityException` 断言）
- [x] vault active-key-id 回退/冲突两分支用例；delta 解析后受限资源角色集合含 `admin` 与 `nop-admin`
- [x] 三查询动作返回实体 `data == null`（batchGet/findList/findFirst 各一用例）
- [x] **接线验证**：D6-02 用例为 mock verify（对 `unregisterUsage` 的显式调用断言，非仅类型存在）
- [x] 受行为变化影响的 owner doc（`nop-ai.md` 删除语义、`nop-credential.md` 错误面/batchGet 语义）已更新，设计 `02-phase2-design.md` 补错误码归一与 vault 归一 impl 裁定标注
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 密码学/KMS P3 卫生项打包（9 项）

Status: completed
Targets: `nop-credential-service/.../crypto/CredentialCipher.java`、`.../crypto/DefaultCredentialKeyProvider.java`、`.../entity/NopCredentialBizModel.java`（reencrypt 部分）、`nop-credential-api`（正则单源）、`nop-credential-kms-vault/.../VaultCredentialKeyProvider.java`

- Item Types: `Fix | Decision`

- [x] D1-04：`CredentialCipher.decrypt` 三处 `ARG_CIPHERTEXT` param 截断（长度上限 + 前缀保留，密文串不整段进日志）
- [x] D5-05：`decrypt` 强制内层 `v1:` 前缀（不满足按 `INVALID_CIPHERTEXT_FORMAT` 拒绝），补"cv1 包装 legacy 载荷被拒"测试空档
- [x] D5-06：`DefaultCredentialKeyProvider` master-keys 与 `VaultCredentialKeyProvider` migration-keys 的 passphrase、Vault 返回材料改 `isBlank` 校验（纯空白拒绝；含空格的非空白 passphrase 保持合法）
- [x] D5-07：`getMasterKeys()` 收窄（public → 包私有/删除）——全仓已核对**零调用点（含测试）**，收窄无既有调用需同步；如保留诊断用途则改返回脱敏结构——**Decision：优先删除/包私有**
- [x] D3-04：keyId 正则下沉 `nop-credential-api` 单源（api 模块零业务依赖约束内），`CredentialCipher`/`DefaultCredentialKeyProvider`/`VaultCredentialKeyProvider` 三处引用收敛
- [x] D3-03：`key-provider` 取值 trim + 大小写归一后再比较（fail-closed 保持，错误归因准确）
- [x] D3-05：`fetchMaterial` 显式请求级超时（新配置 `nop.credential.vault.request-timeout`，缺省值执行时裁定，显式置空不再无限阻塞）
- [x] D3-02：`reencrypt-page-size` 下限校验（< 1 启动/使用时 fail-closed 拒绝，防空页死循环）
- [x] D5-03：`reencryptAll` 非 cv1 前缀行计数上报——**Decision：不改 GraphQL 返回契约（int 保持），以 WARN 汇总日志（含行数）为关窗完备性信号；可测性要求：非 cv1 计数经包私有可注入结构暴露（或等价 list-appender 断言），不得只写不可断言的日志语句**

Exit Criteria:

- [x] 每项有 focused 测试或显式 `No new test required: <reason>`（预期仅 D5-07 可豁免——全仓零调用点，删除/收窄无需新测试）
- [x] 密文格式兼容锚点不破坏：`TestCredentialCipher` 全绿 + 新增 v1 强制/param 截断用例
- [x] Vault fail-closed 校验组用例全绿（不可达/401/403/404/材料非法/来源混合/残余含 active），新增超时与 blank 材料用例
- [x] reencrypt 分页既有用例全绿 + 新增 page-size 下限与非 cv1 计数信号用例
- [x] owner doc 同步（vault 配置项新增/归一语义、keyId 单源、page-size 下限）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - OAuth 引擎/Provider/usage 面 P3 打包（10 项）

Status: completed
Targets: `nop-credential-service/.../oauth/OAuthFlowService.java`、`.../CredentialProviderImpl.java`、`.../entity/NopCredentialBizModel.java`、`.../entity/NopCredentialUsageBizModel.java`、`nop-ai-core/.../service/ChatServiceImpl.java`、`nop-ai-service/.../credential/AiModelCredentialResolverImpl.java`、`nop-ai-service/.../entity/NopAiModelBizModel.java`

- Item Types: `Fix | Decision`

- [x] D2-02：`beginOAuthFlow` 补 clientId 非空校验（对齐回调侧字段集，空/空白拒绝；错误码复用 `ERR_CREDENTIAL_OAUTH_CLIENT_CREDENTIALS_MISSING` 并在 param 标注缺失字段集）
- [x] D2-03：`engineUpdateInLock` 无变化分支跳过回写（updater 返回与当前一致时不重加密不 UPDATE；返回值语义不变）——**Decision：相等判定口径（引用相等 vs equals）执行时裁定并保持对外行为不变**（落定：引用相等——updater 返回同实例即显式"未变化"信号）
- [x] D2-04：锁内 probe 补 `assertOauth2NotDisabled`（TOCTOU 闭合）——**Decision（边界约束，两项均强制）**：(a) 不得改变 adjudication §二#3(b) 已裁定的"saveCredential 覆盖路径不拒 disabled 属显式声明边界"——`engineUpdateInLock` 为共享锁入口（saveCredential oauth2 分组写同样经过），disabled 复查只作用于引擎通道（惰性刷新/回调 token 回写路径），saveCredential 通道显式豁免；(b) disabled 断言**必须发生在 `lockEntity` 之后的锁内**（通道参数化传入锁内 probe 或等价锁内机制；调用侧锁外复查不闭合 TOCTOU 窗口，不作为实现形态）。实现形态执行时裁定并回写设计 §3.5 标注（落定：2-arg/3-arg 重载分层通道参数化，`engineChannel = entityCustomizer == null`）
- [x] D2-05：`buildResultPage` JS 字符串上下文改 JSON 编码（meta-refresh 属性上下文保持 HTML 转义——两语境分别处理）
- [x] D1-02/D4-05：`saveCredential` 非 oauth2 更新路径补 delFlag fail-closed（与 oauth2 分支 probe 同口径，墓碑行拒绝改写）
- [x] D4-04：`batchGet` 存在性 oracle——**Decision：优先文档澄清**（缺 id 抛错 = 显式错误请求；不可见剔除 = 防探测归一，两语义并存的理由写入 owner doc）；若执行时裁定行为归一，须给出 Web 消费面证据
- [x] D6-03：`registerUsage` 前置校验凭证存在且未软删（fail-closed；错误码复用 `ERR_CREDENTIAL_NOT_FOUND`/`ERR_CREDENTIAL_DELETED`，与 provider 读路径同码），owner doc 声明行为收紧（配错 credentialId 即时报错而非运行时延迟暴露）
- [x] D6-04：三个修复点统一 `isBlank`——`ChatServiceImpl.resolveApiKeyForRequest`（accountKey/credApiKey 判空）、`AiModelCredentialResolverImpl`（:132 凭证 apiKey 字段空校验）、`NopAiModelBizModel.reconcileCredentialUsage`（credentialId 判空）；平台 `ApiStringHelper` 不改（用 `StringHelper.isBlank` 替换调用点）；resolver 相邻 :113/:118 回退分支保持现状（不在审计清单）；补空白值边界测试（空白 accountKey 走回退、空白 apiKey 字段 fail-closed、空白 credentialId 不登记引用）
- [x] D4-06：`NopCredentialUsageBizModel` mutation 面收口（对齐 D2-01 同型先例 `NopCredentialOauthStateBizModel`：7 个标准 mutation 禁用抛 `UnsupportedOperationException`；查询面 admin-only 已有）
- [x] D2-03/D2-04 影响面核对：`TestCredentialOAuthRefresh` 既有并发/刷新用例零回归

Exit Criteria:

- [x] 每项有 focused 测试：clientId 空白拒绝；无变化跳过回写（UPDATE 不发生——以 dao 交互或 version 不变断言）；锁内 disabled 拒绝（仅引擎通道，saveCredential oauth2 更新 disabled 凭证仍放行的既有边界用例保持）；墓碑行改写拒绝；registerUsage 对不存在/已删凭证报错；D6-04 三点空白值边界（空白 accountKey 回退/空白 apiKey 字段 fail-closed/空白 credentialId 不 bind）；D2-05 结果页 URL 含引号/反斜杠时 JS 上下文安全编码断言（JSON 编码生效，escapeHtml 残留可检测）；usage 七 mutation 全部禁用（对齐 `TestNopCredentialOauthStateBizModel` 先例新建用例）
- [x] **无静默跳过**：新增拒绝路径全部显式抛错（错误码断言），无空返回/吞异常
- [x] OAuth 引擎一期锚点零回归：`TestOAuthFlowService`/`TestCredentialOAuthRefresh` 全绿（断言随 D2-02/D2-03/D2-05 行为更新处须注记来源 finding）
- [x] owner doc 更新（registerUsage 收紧、batchGet 语义澄清、usage mutation 禁用）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - wiring 测试增强 + 全量回归 + 收口

Status: completed
Targets: `nop-ai-service/src/test/.../credential/TestAiModelCredentialResolverWiring.java`、docs、roadmap

- Item Types: `Fix | Proof | Follow-up`

- [x] 复核观察项 2：`TestAiModelCredentialResolverWiring` 第 3 用例断言改写——显式断言 resolver 构造不依赖 `ICredentialProvider`/dao（如断言可选依赖字段为 null 或构造路径零解析），替代当前 `assertNotNull` 隐式证明（落定形态：显式 `assertNull(impl.credentialProvider)`——本容器无 nop-credential-service 部署时凭证库可选依赖字段保持 null；daoProvider 在本容器有 bean（OrmDaoProvider）非 null，其可选性证明载体为 `TestAiModelCredentialResolver` 自建 ORM 栈单测，注记在测试注释）
- [x] `./mvnw test -pl nop-credential -am` 全绿；`./mvnw test -pl nop-ai -am` 全绿（nop-auth-service 既有 flake 按 A1-audit 口径单独运行核实——两 reactor BUILD SUCCESS，flake 未触发）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-credential --severity high` 退出码 0（覆盖 nop-credential-kms-vault 子模块；nop-ai 同跑）——**执行期口径修正（closure audit Major #2 采纳）**：工具对本仓既有获批 mutation-closure 模式（`UnsupportedOperationException`）恒退 1（A1-audit 收口后即如此，先于本计划存在）；实际判据按 W13 先例为 **baseline 对照 0 新增非批准模式**——stash 基线 20 条 + 本计划 +7 条全部为 D4-06 计划指定动作（"禁用抛 UnsupportedOperationException"本身，逐动作有 GraphQL 面测试断言），无其他模式发现；nop-ai 2 条为本计划未触碰文件的一期存量
- [x] roadmap C1-hardening 状态更新（与 C1b 合并判定 done 的登记归 EXECUTE 轮，本 plan 只登记自身收口）（落定：`planned`→`in progress`，登记 C1a 收口 + C1b 待执行 + 合并 done 判定归 C1b 收口轮）
- [x] 独立 closure audit（fresh subagent）+ Closure Evidence 写入（`ses_ff0fd2767ffeqaVItGJFmC2eqd`，verdict CAN CLOSE，0 Blocking）

Exit Criteria:

- [x] Phase 1-3 全部 Exit Criteria 勾选
- [x] 全部新增强化用例列表化登记（测试类/方法 ↔ finding 编号映射）入 daily log（`ai-dev/logs/2026/08-17.md` Phase 4 条目，25 finding + 观察 2 全量映射）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 独立 closure audit 通过（0 Blocking）且证据写入本 plan Closure 段

## Closure Gates

- [x] adjudication §1.2 的 24 行（25 个 finding ID）+ 复核观察项 2 全部落地或显式移入 Deferred But Adjudicated（后者仅限执行期发现的新事实，不得为原判 P2 项）
- [x] 所有 in-scope confirmed live defects 已修复（P2 五行六 ID + P3 十八行十九 ID + 复核观察项 2，按 finding ID 清点共 25 + 观察 1）
- [x] 一期契约锚点零回归：cv1 密文格式、`ICredentialProvider` SPI 签名、明文边界（published=false + 置空）、软删除先序、引用计数拦截语义
- [x] 必要 focused verification 已完成（每 finding 至少一条测试或显式豁免理由——仅 D5-07 豁免：全仓零调用点的可见性收窄）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect
- [x] 受影响 owner docs（nop-credential.md / nop-ai.md / 02-phase2-design.md 标注）已同步
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：调用链运行时连通（尤其 D6-02 三删除动作真实注销 usage、D4-06 mutation 收口生效），无空方法体/静默跳过
- [x] `./mvnw test -pl nop-credential -am` 通过（BUILD SUCCESS：service 196 / kms-vault 40 / web 2 及上游）
- [x] `./mvnw test -pl nop-ai -am` 通过（BUILD SUCCESS：nop-ai-core 218 / nop-ai-service 36 等全模块）
- [x] checkstyle / 代码规范检查通过（mission 命令语义 `lint not configured`——项目 pom 未接线 checkstyle；直跑插件为全仓既有 baseline 违规，与 W13 收口口径一致；本次改动遵循既有代码风格）

## Deferred But Adjudicated

### PKCE（OAuth 2.1 纵深加固）

- Classification: `optimization candidate`
- Why Not Blocking: A1-audit §二#1 终局裁定——服务端机密客户端的授权码截获面已被 state 一次性消费 + 128bit CSPRNG + TTL + 回调参数注入 fail-closed 闭环防护；PKCE 属纵深加固非缺口修补，按 IdP 要求启用
- Successor Required: no
- Successor Path: 无（按 IdP 需求重新触发时立独立小 plan）

### oauth2 凭证 testCredential 真实连通性

- Classification: `out-of-scope improvement`
- Why Not Blocking: A1-audit §二#5——需先裁 IdP 侧探测语义且可能消耗 refresh_token（rotation 副作用破坏刷新状态机）；当前 test 语义 = 配置完备性校验，消费侧错误运行时 fail-closed 暴露
- Successor Required: no
- Successor Path: 无（设计先行，需求出现时重新触发）

### nop_credential_oauth_state 过期行批量清理

- Classification: `optimization candidate`
- Why Not Blocking: A1-audit §二#6——惰性清理 + TTL 600s + 行体量微小，属容量卫生非安全属性
- Successor Required: no
- Successor Path: 无（极端存储压力出现时按 nop-job 定时清理重开）

### KMS 迁移关窗自动化 + reencryptAll 进度上报

- Classification: `optimization candidate`
- Why Not Blocking: A1-audit §二#7——手动 runbook 闭环有信号（幂等可重跑 + 残余列表 WARN 审计）；自动化与进度上报为大表迁移 UX 增强
- Successor Required: no
- Successor Path: 无（本 plan Phase 2 D5-03 已补非 cv1 计数信号，进一步自动化按需求重开）

### D5-02 软删行重加密 / D5-04 cv2 AEAD 绑定

- Classification: `watch-only residual`
- Why Not Blocking: A1-audit §1.3 已终局 deferred（运维语义边界已声明 / cv2 为远期演进方向，派生密钥分离间接防护成立）
- Successor Required: no
- Successor Path: 无

## Non-Blocking Follow-ups

- 消费链无用户上下文 WARN 观测（A1 §二#2 裁定 watch-only：合法热路径噪音，若需取用随 getCredential 审计日志增强统一设计）
- W16-impl 起草归后续 DRAFT 轮（roadmap 已登记依赖 W16-design done）

## Closure

Status Note: adjudication §1.2 全部 24 行（25 个 finding ID）+ 复核观察项 2 在 live repo 逐项落地（每项 focused 测试或显式豁免），一期契约锚点零回归（cv1 格式/SPI 签名/明文边界/软删先序/引用计数拦截），Deferred 段仅含 A1-audit 终局裁定的可选项与 watch-only residual（无 in-scope 项降级）。执行期决策三条已回写：D2-03 引用相等口径、D2-04 通道分层形态（§3.5 C1a 标注）、D4-04 文档澄清、D4-02/D3-01 双源语义（owner doc 声明）。
Completed: 2026-08-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit subagent（fresh session，task id `ses_ff0fd2767ffeqaVItGJFmC2eqd`，read-only，含两测试门独立重跑）
- Evidence:
  - Phase 1-4 全部 Exit Criteria：PASS——逐项 live 锚点复核（D6-02 `NopAiModelBizModel.java:148-155/:169-176` + batchDelete 虚分派经基类 `CrudBizModel.java:1318` 独立核实；D1-01 `:407-418/:428-437/:889-893`；D1-03/D4-07 `:268-280` + `OAuthFlowService:144-150/:184-188`；D3-01 `VaultCredentialKeyProvider:228-237`；D4-02 delta 8 受限资源；D1-04 截断为审计三处的超集（4 throw 点含新 D5-05 分支）；D5-05 `:147-150`；D5-06×3；D5-07 零调用点复核；D3-04 api 单源（api diff = 仅 +9 行常量，`ICredentialProvider` SPI 零触碰）；D3-03/D3-05/D3-02/D5-03；D2-02 `:197-208`；D2-03 `:351` 引用相等守卫；D2-04 `:333/:337/:341`（lockEntity 后锁内、3-arg 豁免）；D2-05 `:307-313`；D1-02/D4-05 `:286-289`（越权归一之后）；D6-03 `:200-208`；D6-04×3；D4-06 七真实 throw `:101-165`；观察项 2 `:103` 显式 assertNull）
  - 测试门独立重跑：`./mvnw test -pl nop-credential -am` BUILD SUCCESS（196/40/2 零失败）；`./mvnw test -pl nop-ai -am` BUILD SUCCESS（nop-auth-service 既有 flake 未触发）；新增测试类确认在 surefire 报告中执行
  - Closure Gates：全部 PASS——契约锚点（cv1/SPI/published=false/软删先序/引用计数）逐项核实；Deferred 段与 A1-audit 终局裁定一一对应，无 in-scope live defect 降级
  - Anti-Hollow：D6-02 链路真实（delete → unregisterUsageQuietly → provider.unregisterUsage，异常传播不吞、skip 仅限既裁定的可选装配空值边界且有边界测试）；D4-06 为真实 throw 非空方法体；reencryptAll 仅有的 continue 为既有幂等跳过 + D5-03 计数跳过（计划 Decision 指定），无新增静默路径；scan-hollow baseline 对照 0 新增非批准模式（20 基线 + 7 = D4-06 计划指定动作，nop-ai 2 条为未触碰一期存量）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（勾选后复跑确认）
  - Deferred 项分类检查：仅 4 项 §二 optimization candidate + D5-02/D5-04 watch-only，均为 A1-audit 终局裁定原文，无新增/改写
  - 执行期采纳的审计 Major：#1 closure 簿记（本段 + Phase 4/Gate 勾选即其完成）；#2 scan 退出码口径修正（Phase 4 条目内联记录 baseline 判据）
- 审计结论：**CAN CLOSE**（0 Blocking / 2 Major 均为计划文件簿记项、已于关闭动作中完成 / 2 Minor 免动作）

Follow-up:

- no remaining plan-owned work（C1b `2026-08-17-1345-2` 为独立后继计划，归其自身 EXECUTE 轮；C1-hardening 整体 `done` 判定待 C1b 收口后合并登记）
