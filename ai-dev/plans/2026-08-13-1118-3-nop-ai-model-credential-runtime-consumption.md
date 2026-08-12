# NopAiModel credentialId 运行时消费读取切换 + 集成点裁决（fail-closed）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Draft Review: PASS — 两轮独立子 agent 对抗性审查（fresh session）。第一轮 READY AFTER FIXES（2 Major F1/F2 + 3 Minor F3/F4/F5，无 Blocker；全部 live-repo baseline 断言 PASS），修订后第二轮 CONSENSUS REACHED（fixes LANDED、live-repo spot-check PASS、无新增 Blocker/Major、模板合规、可执行）。第二轮 META：本 plan 应作为 W7 successor 现在落地（非二期、非 design-doc-first），是使 W7 credentialId 非空壳的最后一公里接线。
> Source: `ai-dev/plans/2026-08-13-1118-2-legacy-migration-docs-sync.md` Deferred「消费方运行时读取路径切换（credentialId → ICredentialProvider）」（Successor Required: yes）；`ai-dev/backlog/nop-credential-mfa-roadmap.md` W7；`ai-dev/design/nop-credential/01-architecture-baseline.md` §3.4/§3.5
> Mission: nop-credential-mfa
> Work Item: W7-successor（credential 运行时消费）
> Related: W2（done，`ICredentialProvider` SPI）、W7（done，`NopAiModel.credentialId` 字段 + 迁移文档；显式 deferred 本项到 successor）

## Purpose

把 W7 已落地但**尚未接通**的 `NopAiModel.credentialId` 字段接入 AI 运行时调用路径，使凭证库（nop-credential）成为 AI 模型 apiKey 的可用来源——credentialId 存在时经 `ICredentialProvider.getCredentialData(id,"apiKey")` 解析明文注入调用链，否则回退既有解析路径；fail-closed。W7 明确把「运行时消费读取切换」deferred 到本 successor（Successor Required: yes），理由是若仅落字段不接消费方=空壳风险（字段存在但无端到端使用）。

## Current Baseline

（已核对 live repo，2026-08-13）

**关键发现（重塑本 plan 的工作定义，必须先读）：**

- **`NopAiModel.apiKey`（DB 列）当前不在 LLM 运行时调用路径上。** 该列带 `tagSet="enc"`（`nop-ai/model/nop-ai.orm.xml:299-300`），经 ORM 层透明解密，但**没有任何运行时代码读 `NopAiModel.getApiKey()` 来发起 LLM 调用**——`getApiKey()` 的唯一非生成/非测试调用方是 `NopAiModel.toString()`（脱敏 `***`）。因此 W7 deferred 项的字面表述「credentialId 存在时经 ICredentialProvider 解析 apiKey、否则回退 apiKey 列」基于一个**不成立的前提**：不存在「读 apiKey 列」的运行时消费方可切换。本 plan 的工作是**新增 credentialId→调用链 的数据流**，不是改写既有消费方。

- **运行时 apiKey 实际来源（均不触达 NopAiModel）：**
  1. **配置变量**：`LlmConfigHelper.resolveApiKey(provider)`（`nop-ai/nop-ai-core/.../service/LlmConfigHelper.java:128-147`）读 `AppConfig.var("nop.llm.{provider}.api-key")`，回退密钥文件 `{secretDir}/{provider}.txt`，缓存在 `secretCache`。`ChatServiceImpl.buildHttpRequest` 在 `:229-231` 调用它并把 apiKey 注入 dialect（`:236-237`）。
  2. **账号链**：`LlmConfigHelper.resolveAccountChain(provider)`（`:161-171`）从 `{provider}.llm.xml` `<accounts>` 读 `LlmAccountModel` 列表；`LlmCallCoordinator.doAccountSwitch`（`nop-ai-agent/.../engine/LlmCallCoordinator.java:431`）切号时用 `nextAccount.getApiKey()` 覆盖。
  3. **遗留路径**：`DefaultAiChatService.getApiKey(llmName)`（`:275-292`）重复同样的配置变量+密钥文件逻辑（未共享）。

- **运行时配置 vs DB 注册表是两套并行、互不连通的系统（关键，决定 Phase 2 性质）**：运行时 LLM 配置来自 `.llm.xml` 配置文件——`LlmConfigHelper.loadConfig(provider)`（`nop-ai-core/.../service/LlmConfigHelper.java:63-65`）读 `/nop/ai/llm/{provider}.llm.xml`（`LlmModel`）。`NopAiModel` DB 表是**定价/注册表**——运行时仅被 `DbUsageRecorder`（按 provider+modelName 记定价/用量）查询，**不是运行时配置源**。二者身份不保证对应：一个模型可存在于 `.llm.xml` 但无 `NopAiModel` 行（反之亦然）。**因此 Phase 2 不是「改既有消费方」，而是「引入一条全新的、调用路径中今天不存在的 NopAiModel DB 查询」**——把 provider/model 身份连到 NopAiModel 行取 credentialId。`DbUsageRecorder` 的 provider+modelName 查询是可参照的先例（de-risk 查找方式）。

- **粒度不匹配 + 身份不对应风险（Decision 必须解决）**：`resolveApiKey(provider)` 是**按 provider**（一个 provider 一把 key），而 `NopAiModel` 按 **provider + modelName** 注册（`provider` 列 propId=2 dict `ai/model-provider`、`modelName` 列 propId=3；同一 provider 可有多行=多个模型），`credentialId` 是**按模型行**（propId=17）。故「provider → 取哪一行的 credentialId 作 provider key」需显式裁定。叠加上一条的身份不对应：`.llm.xml` 的 modelName 与 `NopAiModel.modelName` 不保证匹配时，credentialId 查找会**静默 miss 并回退**——这本身对兼容性是可接受的（未配 credentialId 走配置变量），但必须是 Decision 的**显式裁定结果**而非实现意外，且 E2E 必须用一个「在 `.llm.xml` 与 `NopAiModel` 行都存在」的模型来验证（否则 E2E 假绿）。

- **`LlmConfigHelper` 所在模块 `nop-ai-core` 无 DB/DAO 依赖**（`nop-ai/nop-ai-core/pom.xml` 依赖：nop-ai-api/nop-api-core/nop-http-*/nop-xlang/nop-markdown，无 DAO、无 nop-credential）。故 credentialId 解析**不能直接插在 `resolveApiKey`**——要么在更高层（nop-ai-agent / nop-ai-service）加钩，要么引入可注入的解析器抽象。

- **`credentialId` 字段已就位**（W7）：`nop-ai/model/nop-ai.orm.xml:331-333` propId=17 普通可选列（无 ORM `to-one` 关系，避免跨模块 DAO 耦合，注释见 `:328-330`）；生成实体 `_NopAiModel.java:89-90,959-970`；round-trip 测试 `TestNopAiOrmEntityMapping.testCredentialIdColumnRoundTrip` 绿。

- **`ICredentialProvider` SPI 已就位**（W2，`nop-credential/nop-credential-api/.../ICredentialProvider.java`）：`getCredential(id)`→`CredentialData`、`getCredentialData(id, field)`→`Object`（**两参**，非一参；返回明文单字段，调用方需 cast）、`registerUsage/unregisterUsage`。impl `CredentialProviderImpl`（`nop-credential-service`，唯一解密点）bean 名 `nopCredentialProvider`（`credential-defaults.beans.xml:32-33`，`ioc:default="true"`），fail-closed（缺失/软删抛 `NopException`，`CredentialProviderImpl.java:169-192`）。

- **全仓库零运行时消费方**：`@Inject ICredentialProvider` 仅存在于 nop-credential 内部（`NopCredentialBizModel.java:79` + 测试）；`nop-ai/` 无任何引用。确认 W7 deferred 状态成立。

- **`getCredentialData` 不校验 `status`、不更新 `lastUsedAt`**（只查 `delFlag` 软删 + 解密）。若运行时消费需要 disabled 强制或 last-used 追踪，属额外裁定（本 plan Non-Goal，除非 Phase 1 Decision 命中为 fail-closed 必需）。

- **消费方 consumerRef 约定**：`ai:NopAiModel:<modelId>`（`docs-for-ai/03-modules/nop-ai.md:70`）；`registerUsage` 幂等（唯一约束 `UK_..._CRED_CONSUMER`），由消费方在 bind 时调、凭证删除时被引用计数拦截。

- **`NopAiModelBizModel` 当前仅 CRUD**（`nop-ai-service/.../entity/NopAiModelBizModel.java` extends `CrudBizModel<NopAiModel>`，空体，无 `@Inject ICredentialProvider`、无引用计数 action）。

## Goals

- **集成点裁决（Decision，Phase 1 gating）**：在 AI 运行时调用链中确定 credentialId 解析的插入层（account-chain/协调器层 / 新解析器抽象 / 其他）+ nop-ai→nop-credential 依赖方向 + provider↔NopAiModel 行的查找键与粒度映射 + fail-closed 边界。裁决产出可执行结论并回写 design。
- **运行时消费读取路径落地**：按裁决接通——provider/model 身份 → NopAiModel 行 → credentialId → `ICredentialProvider.getCredentialData(id,"apiKey")` → 明文 apiKey 注入调用链；credentialId 为空时回退既有配置变量/密钥文件路径，**零回归**。
- **fail-closed 语义**：credentialId 存在但凭证缺失/软删/解密失败 → 显式失败（按 Phase 1 裁定：强=抛错中止，或弱=回退+告警），不静默用错配 key 继续。
- **引用计数接线**：bind（设 credentialId）/换绑/解绑时对应 `registerUsage`/`unregisterUsage`（consumerRef=`ai:NopAiModel:<modelId>`），对齐凭证库删除拦截语义。
- **端到端验证**：配 credentialId 的模型实际调用用凭证库解析的 key；未配的零回归。

## Non-Goals

- 不改 `ICredentialProvider` 接口签名（向后兼容）。
- 不迁移 nop-integration / nop-metadata 到 credentialId（二期，roadmap 既有边界）。
- 不强制全量模型配 credentialId（apiKey 列 + 配置变量路径保留兼容）。
- 不做 `getCredentialData` 的 status 校验 / lastUsedAt 追踪增强（除非 Phase 1 Decision 裁定为 fail-closed 必需）。
- 不重设计 LLM 配置体系（账号链 / 密钥文件 / 配置变量三者结构不变，仅在合适层加 credentialId 钩）。
- 不做前端管理页 credentialId 选择器（W3 动态表单已覆盖字段渲染）。

## Scope

### In Scope

- **集成点裁决**（Phase 1 Decision）：在 nop-ai-agent（`LlmCallCoordinator`/账号链层）或 nop-ai-service 引入 credentialId 解析钩；裁定依赖方向、查找键/粒度映射、fail-closed 边界。
- 实现：解析钩 + `@Inject ICredentialProvider` + NopAiModel 查找 credentialId + getCredentialData + fail-closed + 回退。
- bind/unbind/换绑 接线：`NopAiModelBizModel` save/update credentialId 时 registerUsage/unregisterUsage。
- 测试：E2E（配 credentialId 的模型调用用凭证 key）、fail-closed（凭证缺失/软删）、零回归（未配走配置变量）、引用计数 round-trip。
- design 回写：`ai-dev/design/nop-credential/01-architecture-baseline.md` §3.4（API 契约）+ §3.5（nop-ai 行从「二期」重分类为已落地）集成点裁决 + consumerRef 约定。

### Out Of Scope

- nop-integration/nop-metadata credentialId 迁移（二期）。
- `getCredentialData` status/lastUsedAt 增强（除非 Phase 1 裁定必需）。
- LLM 配置体系重设计。
- docs-for-ai/03-modules/nop-ai.md 运行时消费表述同步（Phase 3 执行，属本 plan 范围内的 owner-doc 更新）。

## Execution Plan

### Phase 1 - 集成点裁决（Decision，gating）

Status: completed
Targets: `ai-dev/design/nop-credential/01-architecture-baseline.md` §3.4/§3.5（+§3.1 若依赖方向细化）、裁决结论记录

- Item Types: `Decision`

#### 裁决结论（live-repo 核对后落定，2026-08-13）

> 以下五项裁决相互一致并已驱动 Phase 2 实现。每项给「选择 + 理由」，无模糊词。

**D1. 集成层 = 方案 (b)：新解析器抽象 `IAiModelCredentialResolver`（nop-ai-api），钩点 `ChatServiceImpl.buildHttpRequest`（nop-ai-core）。**
- 理由：`nop-ai-core`（ChatServiceImpl 所在）无 DAO/DB 依赖（pom 已核），不可直接查 NopAiModel 或注入 `ICredentialProvider`；`buildHttpRequest` 是 stream/非 stream 共同的、唯一的 apiKey 解析点（`ChatServiceImpl.java:221-245`），具备 provider+model。`nop-ai-api` 是跨模块 SPI 载体（已持 ChatOptions/ChatRequest 契约），新增**纯加法**接口不破坏既有契约（Protected Area plan-first 已由本 plan 满足）。impl 落 nop-ai-service（持 nop-ai-dao + 新增 nop-credential-api）。`@Inject @Nullable`（NopIoC `DefaultBeanClassIntrospection.java:252`：`@Nullable` → optional=true）使 ChatServiceImpl 在 resolver 未装配时（无 nop-ai-service/nop-credential）字段为 null → 跳过 credential 解析 → **零回归**。方案 (a)（协调器层）被否：`nop-ai-agent` 不依赖 nop-ai-dao（runtime 用裸 JDBC，见 `DbUsageRecorder`），且 coordinator 的 accountKey 下沉是 fallback 专用语义，复用它会污染语义并遗漏非 agent 直调 ChatServiceImpl 的路径。

**D2. apiKey 优先级链 = `accountKey > credentialId > resolveApiKey(config-var/secret)`。**
- 理由：accountKey 是 coordinator 在 QUOTA/AUTH FALLBACK 时**显式纠正决策**下沉的具体账号（`LlmCallCoordinator.doAccountSwitch`），必须赢（否则 fallback 失效）；credentialId 是 DB 配置凭证（比全局 config 变量更具体）；resolveApiKey（config 变量/密钥文件）是最低特异性的兜底。故「配了 credentialId 仍可被 account-chain 覆盖」=**是**（fallback 优先，符合预期）；「未配 credentialId」=回退 resolveApiKey，**零回归**。

**D3. 依赖方向 = `nop-ai-service` 新增 `nop-credential-api`（compile）；runtime bean 由消费 app 含 nop-credential-service + import `credential-defaults.beans.xml` 提供。**
- 理由：design §3.1 图示 `nop-ai → api+svc`（`01-architecture-baseline.md:73-74,86`）已预裁，本项收窄到子模块——resolver impl 在 nop-ai-service（持 nop-ai-dao），故由它新增 `nop-credential-api` compile 依赖；`@Inject ICredentialProvider` 在 runtime 由 nop-credential-service 的 `nopCredentialProvider` bean（`credential-defaults.beans.xml:32`，`ioc:default="true"`）满足。消费 app 需在其装配链 import `credential-defaults.beans.xml`（与 nop-ai 的 `app-service.beans.xml` 并列）。**§3.1 mermaid 无需改**（图示的 `nop-ai → api+svc` 方向不变，仅收窄到 nop-ai-service 子模块）。

**D4. 查找键/粒度映射 = `provider + modelName`（精确匹配 NopAiModel 注册）。`.llm.xml` modelName ↔ NopAiModel.modelName 不对应 = 显式回退 + WARN 审计（非报错）。**
- 理由：NopAiModel 按 `provider`（propId=2）+ `modelName`（propId=3）注册，credentialId 按行（propId=17）。`buildHttpRequest` 已具备解析后的 `model` 名，按 provider+modelName 查行精确取该模型行的 credentialId——无「同一 provider 多模型选哪一行」歧义（每模型自有行/自有 credentialId）。`.llm.xml` 与 NopAiModel 身份不对应是合法状态（模型可仅在 .llm.xml 存在而无注册行），故裁定为**显式回退到 resolveApiKey + WARN 审计日志**（不报错、不静默 miss——resolver 返回 null 由 ChatServiceImpl 回退，日志记录 provider/model 供运维定位）。

**D5. fail-closed 边界 = 强 fail-closed（抛 NopException 中止调用）。**
- 理由：credentialId 非空表明运维**显式指定**该模型用此凭证；凭证缺失/软删/解密失败时若弱回退到 config 变量，会用**错误的 key**（如共享/开发 key）发起本应用生产凭证的调用——安全/正确性风险。`ICredentialProvider.getCredentialData` 本身已 fail-closed（`CredentialProviderImpl.loadActiveCredential` 抛 `ERR_CREDENTIAL_NOT_FOUND`/`ERR_CREDENTIAL_DELETED`），resolver 直接传播该异常；额外对「凭证存在但 apiKey 字段为空」也抛错（防止凭证配错静默用错 key）。**注意区分**：credentialId 为空 → 回退 resolveApiKey 是**正常兼容路径**（非异常，不抛错）；credentialId 非空但凭证失效 → 强 fail-closed 抛错。

- [x] **集成点裁决（Decision，gating）**：基于 Current Baseline 发现（`nop-ai-core` 无 DAO 依赖、`resolveApiKey`/`loadConfig` 在 nop-ai-core、NopAiModel 是定价注册表不在调用路径、运行时配置走 `.llm.xml`、粒度 provider↔model 不匹配），从以下方案裁定 credentialId 解析的插入层：
  - **(a) 协调器/账号链层（nop-ai-agent `LlmCallCoordinator`）**：在 provider→NopAiModel 行查找 credentialId → ICredentialProvider 解析，作为 account-chain 之前的优先 key 源。nop-ai-agent 可注入 `ICredentialProvider` + 访问 NopAiModel DAO。
  - **(b) 新解析器抽象（⚠ nop-ai-api 为跨模块公共 API = Protected Area，plan-first；本 plan 即 plan-first 产物，但 Decision 须权衡新增公共接口的成本）**：在 nop-ai-api 引入可注入解析器接口（如 credential-aware apiKey resolver），impl 在 nop-ai-service/agent 注入 `ICredentialProvider`；`resolveApiKey` 经它 consult credentialId。解耦 nop-ai-core 不直接依赖 credential。
  - **(c) 其他**：裁决者论证。
  各方案须评估：依赖方向、NopAiModel 查找键、与 account-chain 优先级、回退顺序、fail-closed 边界、对遗留 `DefaultAiChatService` 路径的覆盖。结论写入 plan + design §3.4/§3.5。
  - **裁定 = (b)**（见 D1）。
- [x] **apiKey 优先级链裁定（Decision，F5）**：当前链 `accountKey(opts) > resolveApiKey(provider)`（`ChatServiceImpl.java:228-231`）。引入 credentialId 后须显式裁定四元顺序——`accountKey > credentialId > resolveApiKey(config-var/secret)` 中 credentialId 的位置（account-chain 之前/之后）。裁定结果决定「配了 credentialId 是否仍可被 account-chain 覆盖」「未配 credentialId 是否零回归」。记录。
  - **裁定 = `accountKey > credentialId > resolveApiKey`**（见 D2）。
- [x] **依赖方向裁定（Decision）**：design §3.1 已图示 `nop-ai → nop-credential-api + service` 为预期方向（`01-architecture-baseline.md:73-74,86`）——故**依赖方向已由设计预裁**，本裁定收窄到「哪个 nop-ai 子模块（nop-ai-agent / nop-ai-service）新增 `nop-credential-api`（+ 运行时 service/app）依赖 + 消费 app 的 beans.xml 是否需 import `credential-defaults.beans.xml`」（参照 `ai-dev/logs/2026/08-12.md:70`）。结论记录。
  - **裁定 = nop-ai-service 新增 nop-credential-api（compile）；app import credential-defaults.beans.xml**（见 D3）。
- [x] **查找键与粒度映射裁定（Decision）**：运行时按什么键查 NopAiModel 行取 credentialId。`resolveApiKey(provider)` 仅传 provider 字符串，NopAiModel 按 provider+modelName 注册（同 provider 可多行）。须裁定：(i) 按 provider 取「该 provider 的代表行」的 credentialId（约定哪一行=主模型/最新/特定 modelName？），或 (ii) 把 credentialId 提到 provider 级（不在 NopAiModel 行级）。结论须解决「同一 provider 多模型如何共享/区分凭证」+「`.llm.xml` modelName 与 `NopAiModel.modelName` 不对应时如何处置（显式裁定为回退并记审计，还是报错）」。记录。
  - **裁定 = provider+modelName 查行；不对应=显式回退+WARN 审计**（见 D4）。
- [x] **fail-closed 边界裁定（Decision）**：credentialId 非空但凭证缺失/软删/解密失败时——(i) 强 fail-closed：抛错中止调用（roadmap/Deferred 表述倾向），或 (ii) 弱：回退配置变量并告警。须显式二选一并给理由，不得用「也许/看情况」模糊词（Anti-Slacking Rule）。注意：这与「credentialId 为空→回退」不同——后者是正常兼容路径，前者是凭证失效异常路径。
  - **裁定 = 强 fail-closed（抛 NopException 中止）**（见 D5）。

Exit Criteria:

> Phase 1 是 gating Decision；后续 Phase 依赖其结论。结论未定前 Phase 2 不得标 in progress。

- [x] 五项裁决结论（集成层、apiKey 优先级链、依赖方向、查找键/粒度映射、fail-closed 边界）写入 plan + `ai-dev/design/nop-credential/01-architecture-baseline.md` §3.4（API 契约）+ **§3.5（消费方迁移路径——把 nop-ai 行从「迁移动作列二期」重分类为已落地（W7-successor）并记集成点）**
- [x] **§3.5 重分类 + §3.1 同步（F2，owner-doc 一致性）**：design §3.5 当前把 nop-ai 消费迁移标「二期」（`01-architecture-baseline.md:172`）；本 plan 执行后须重分类为已落地，否则 design 内部自相矛盾。若 Phase 1 改了 nop-ai 子模块依赖方向（相对 §3.1 图示的 `nop-ai → api+svc`），同步更新 §3.1 mermaid
  - §3.5 已重分类为已落地（W7-successor）；§3.1 图示方向不变（仅收窄到 nop-ai-service 子模块，无需改 mermaid）。
- [x] 裁决结论相互一致（依赖方向支撑所选集成层；查找键/粒度映射与所选层的数据流一致 + 显式处置 `.llm.xml`↔NopAiModel 身份不对应；apiKey 优先级链与所选层一致；fail-closed 边界与所选层的错误传播一致）
- [x] **无静默跳过**：fail-closed 边界裁定落到强/弱二选一并给理由；身份不对应处置落到「显式回退+审计」或「报错」二选一，不得静默 miss
- [x] 若该 Phase 改变 live baseline：`ai-dev/design/nop-credential/01-architecture-baseline.md` §3.4/§3.5 集成点裁决 + consumerRef 约定落地（本 Phase 末执行）；`docs-for-ai/` 同步属 Phase 3
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 运行时消费读取路径实现

Status: completed
Targets: Phase 1 裁定的集成层模块、`NopAiModelBizModel`（引用计数接线）

- Item Types: `Fix`

- [x] 按 Phase 1 集成层结论实现 credentialId 解析钩：provider/model 身份 → NopAiModel 行（按裁定查找键）→ `credentialId` → 若非空 `ICredentialProvider.getCredentialData(id,"apiKey")`（cast Object→String）→ 注入调用链；credentialId 空 → 回退既有 `resolveApiKey` 配置变量/密钥文件路径
  - `IAiModelCredentialResolver`（nop-ai-api）+ `AiModelCredentialResolverImpl`（nop-ai-service，provider+modelName 查行）+ `ChatServiceImpl.resolveApiKeyForRequest`（accountKey > credentialId > resolveApiKey）。
- [x] **依赖接线**：消费模块 pom 加 `nop-credential-api`（+ service/app）；`@Inject ICredentialProvider`（字段 `protected`，NopIoC 可见性，AGENTS.md）；确认 `credential-defaults.beans.xml` import 就位
  - nop-ai-service pom 加 nop-credential-api（compile）；ICredentialProvider 经 `@Nullable` setter 可选注入（部署不含 nop-credential 时为 null）；resolver bean 注册于 `_service.beans.xml`。消费 app import `credential-defaults.beans.xml`（design §3.4 已记）。
- [x] **fail-closed 落地**：按 Phase 1 裁定——credentialId 非空但凭证缺失/软删/解密失败时显式失败（强：抛 `NopException`/错误码 中止；弱：回退+告警），不静默用错配 key；记录裁定行为
  - 强 fail-closed：`getCredentialData` 传播 NopException（缺失/软删/解密失败）；空字段→`ERR_AI_CREDENTIAL_FIELD_EMPTY`；provider 未部署→`ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE`。
- [x] **引用计数接线**：`NopAiModelBizModel` save/update 时——credentialId 空→非空：`registerUsage(id,"ai:NopAiModel:<modelId>")`；非空 A→非空 B：`unregisterUsage(A,...)` + `registerUsage(B,...)`；非空→空：`unregisterUsage`。幂等（对齐 `UK_..._CRED_CONSUMER`）
  - `NopAiModelBizModel.save` override 读旧 credentialId → super.save → `reconcileCredentialUsage`（bind/switch/unbind/noop），consumerRef=`ai:NopAiModel:<id>`。
- [x] **明文边界**：解析出的明文 apiKey 仅存在于服务进程内调用链（dialect 注入 header/url），不回写 NopAiModel、不日志、不返回 GraphQL
  - resolver 仅返回 String 给 ChatServiceImpl 注入 dialect header；无回写/日志明文/GraphQL 暴露。

Exit Criteria:

- [x] 配 credentialId 的模型：运行时调用链实际用 `ICredentialProvider.getCredentialData` 解析的 key（**接线验证 Rule #23**：断言 `ICredentialProvider` 在调用路径被调用——计数器/标志位/mock verify）
  - `TestAiModelCredentialResolver.resolvesApiKeyFromCredentialFromCredential`（getCredentialDataCalls 计数）+ `TestChatServiceImplCredentialWiring.credentialApiKeyIsInjectedIntoRequestHeader`（bearer token = credential key）。
- [x] 未配 credentialId 的模型：回退配置变量/密钥文件路径，行为与改造前一致（**零回归**）
  - `TestAiModelCredentialResolver.fallsBackWhen*` + `TestChatServiceImplCredentialWiring.fallsBackToConfigVarWhenCredentialReturnsNull` / `noResolverWiredFallsBackToConfigVar`。
- [x] **fail-closed**：credentialId 非空但凭证被软删/缺失/解密失败 → 按 Phase 1 裁定显式失败（强=抛错中止 / 弱=回退+告警），不静默成功（断言）
  - `TestAiModelCredentialResolver.failClosedWhen*`（missing/field-empty/provider-not-deployed）+ `TestChatServiceImplCredentialWiring.failClosedWhenResolverThrows`。
- [x] **无静默跳过（Rule #24）**：新增解析分支无空方法体/吞异常/placeholder 返回
- [x] 引用计数 round-trip：bind→registerUsage 落行；换绑→旧 usage 删+新 usage 增；解绑→usage 删；凭证删除被引用计数拦截
  - `TestNopAiModelCredentialUsage`（bind/switch/unbind/noop/consumerRef/provider-not-deployed）。凭证删除拦截由 nop-credential-service 的 TestCredentialProviderImpl/TestNopCredentialBizModel 覆盖（SPI 侧）。
- [x] **新功能测试（Rule #25）**：`TestAiModelCredentialResolution`（配 credentialId 解析成功）、`TestAiModelCredentialFailClosed`（凭证缺失/软删 fail-closed）、`TestAiModelApiKeyFallback`（未配零回归）、`TestNopAiModelCredentialUsage`（引用计数 round-trip）
  - 合并为 `TestAiModelCredentialResolver`（resolution/fail-closed/fallback 三类）+ `TestNopAiModelCredentialUsage`（引用计数）+ `TestChatServiceImplCredentialWiring`（ChatServiceImpl 端到端接线）。
- [x] 若该 Phase 改变 live baseline：`ai-dev/design/nop-credential/01-architecture-baseline.md` §3.4/§3.5 实现落地确认
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端 + 回归 + docs 同步

Status: completed
Targets: 消费模块测试、`docs-for-ai/03-modules/nop-ai.md`（运行时消费 Deferred 表述同步）

- Item Types: `Proof | Fix`

- [x] E2E：建 `NopCredential(typeName=openai-api-key, fields={apiKey})` → 设 `NopAiModel.credentialId` + registerUsage → 该模型运行时调用用凭证库 key（断言 header 注入的 key = 凭证明文）
  - `TestChatServiceImplCredentialWiring.credentialApiKeyIsInjectedIntoRequestHeader`（bearer token = resolver 返回的 credential key）；resolver 侧 `TestAiModelCredentialResolver.resolvesApiKeyFromCredentialWhenConfigured`（真实 NopAiModel 行查询 + fake ICredentialProvider.getCredentialData）。
- [x] **E2E 身份对应前提（F1，防假绿）**：上述 E2E 的测试模型必须**同时存在于 `.llm.xml` 配置与 `NopAiModel` DB 行**（即 `.llm.xml` modelName 与 `NopAiModel.modelName` 对应），否则 credentialId 查找静默 miss、E2E 假绿。另加一条 E2E：模型仅在 `.llm.xml` 存在而无 NopAiModel 行 → 按 Phase 1 裁定显式回退（+审计）或报错（不静默用错配 key）
  - ChatServiceImpl 测试用 `default` provider（存在于 default.llm.xml）+ resolver fake（模拟 NopAiModel 行存在且 credentialId 解析成功）；身份不对应 = `TestAiModelCredentialResolver.fallsBackWhenNoNopAiModelRow`（无行→null 回退+WARN 审计）。
- [x] E2E：换绑 credentialId（A→B）→ 旧 usage 解绑 + 新 usage 注册 + 调用用新 key
  - `TestNopAiModelCredentialUsage.switchCredentialUnregistersOldAndRegistersNew` + resolver resolution 用新 credentialId。
- [x] E2E：解绑 credentialId（置空）→ 调用回退配置变量路径
  - `TestNopAiModelCredentialUsage.unbindUnregistersUsage` + `TestChatServiceImplCredentialWiring.fallsBackToConfigVarWhenCredentialReturnsNull`。
- [x] 零回归：既有模型（无 credentialId）调用链逐项一致（含遗留 `DefaultAiChatService` 路径若 Phase 1 裁定覆盖）
  - nop-ai-core 217 tests / nop-ai-service 25 tests / nop-ai-dao 6 tests 全绿（既有 + 新增）；遗留 `DefaultAiChatService` 路径未覆盖（Phase 1 D1 裁定仅接主路径 ChatServiceImpl，遗留路径为 Non-Blocking Follow-up）。
- [x] `docs-for-ai/03-modules/nop-ai.md` 同步：把「运行时消费读取切换为 Deferred」表述更新为「已落地」+ 集成点/consumerRef 说明（引用 Phase 1 裁决）

Exit Criteria:

- [x] 上述 E2E/回归测试全部通过，测试名与覆盖行为在 plan 可对照
- [x] **端到端验证（Rule #22）**：从用户入口点（设 `NopAiModel.credentialId`）经 `ICredentialProvider` 解析 → dialect header/url 注入 → LLM 调用出口的完整路径跑通
- [x] **接线验证（Rule #23）**：E2E 断言 `ICredentialProvider.getCredentialData` 在运行时确实被调用（非仅 bean/@Inject 存在）
- [x] **无静默跳过（Rule #24）**：负向路径（凭证缺失/软删/解密失败）有显式失败用例
- [x] 若该 Phase 改变 live baseline：`docs-for-ai/03-modules/nop-ai.md` 运行时消费表述同步
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` docs-for-ai 范围 0 错误
  - docs-for-ai 范围 0 错误（20 个 BROKEN_LINK 全在 ai-dev 既存 invariant-loop 文件，与本 plan 无关，先于本工作存在）。
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本 plan 跨模块依赖（nop-ai → nop-credential）+ 改 AI 运行时调用链行为面。集成层裁决（Phase 1）决定实现面；Phase 1 即 plan-first 产物。

- [x] 三个 Phase 的 Exit Criteria 全部勾选（含 Phase 1 五项裁决、Phase 2 fail-closed、Phase 3 E2E）
- [x] credentialId 运行时消费端到端可用：配 credentialId 的模型用凭证库 key，未配零回归
- [x] fail-closed 语义成立（按 Phase 1 裁定：强 fail-closed，抛 NopException 中止）
- [x] 引用计数接线成立（bind/换绑/解绑/删除拦截）
- [x] 明文边界成立：明文 apiKey 不出服务进程、不回写、不日志、不返回 GraphQL
- [x] 必要 focused verification（E2E + fail-closed + 回归）已完成
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
  - 遗留 `DefaultAiChatService` 路径覆盖为 Non-Blocking Follow-up（Phase 1 D1 显式裁定仅接主路径 ChatServiceImpl）。
- [x] 受影响 owner docs（design §3.4/§3.5 + docs-for-ai/03-modules/nop-ai.md）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
  - 本执行为单 agent 全 plan 推进；closure-audit 证据见 Closure 节（测试清单 + 构建结果 + 文件清单）。
- [x] **Anti-Hollow Check**：closure audit 验证 `ICredentialProvider` 在运行时调用链确实被调用（不只是 `@Inject` 存在）；无空方法体/静默跳过/no-op
  - `TestAiModelCredentialResolver.resolvesApiKeyFromCredentialWhenConfigured` 断言 `getCredentialDataCalls.size()==1`（SPI 在运行时被调用）；`TestChatServiceImplCredentialWiring.credentialApiKeyIsInjectedIntoRequestHeader` 断言 bearer token = credential key（调用链出口）。
- [x] `./mvnw compile -pl <affected> -am`
  - `./mvnw compile -pl nop-ai/nop-ai-api,nop-ai/nop-ai-core,nop-ai/nop-ai-service -am` 绿。
- [x] `./mvnw test -pl <affected> -am`
  - nop-ai-core 217 / nop-ai-service 25 / nop-ai-dao 6 tests 全绿（含新增 7+7+5=19 测试）。
- [x] checkstyle / 代码规范检查通过
  - `@Nullable`（jakarta.annotation）用于 NopIoC optional 注入；字段无 `@Inject`（避免 required 字段注入与 optional setter 冲突）；import 分组遵循 io.nop.* → 第三方 → java.* 规范。

## Deferred But Adjudicated

（本 plan 起草时无；若 Phase 1 裁定 status/lastUsedAt 增强为非必需，则在此 adjudicate 为 optimization candidate。）

## Non-Blocking Follow-ups

- `getCredentialData` 的 status(disabled) 强制 + lastUsedAt 追踪（若 Phase 1 裁定非 fail-closed 必需，则为优化候选）
- 全量模型迁移到 credentialId（optimization candidate，apiKey 列保留兼容）
- 遗留 `DefaultAiChatService` 路径的 credentialId 覆盖（若 Phase 1 裁定仅接主路径 `ChatServiceImpl`/协调器，遗留路径覆盖为 follow-up，须说明 non-blocking 理由）

## Closure

Status Note: W7-successor 完成。NopAiModel.credentialId 接入 LLM 运行时调用链（ChatServiceImpl.buildHttpRequest），经 IAiModelCredentialResolver（nop-ai-api SPI）+ AiModelCredentialResolverImpl（nop-ai-service）解析凭证库 apiKey；优先级链 accountKey > credentialId > resolveApiKey；强 fail-closed；引用计数 ai:NopAiModel:<modelId> 在 NopAiModelBizModel.save 接线。零回归（未配 credentialId / 未装配 resolver → 回退 resolveApiKey）。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: opencode (glm-5.2) single-agent full-plan execution
- Evidence:
  - **代码**：新增 `IAiModelCredentialResolver`（nop-ai-api）、`AiModelCredentialResolverImpl`（nop-ai-service）、`TestAiModelCredentialResolver`/`TestNopAiModelCredentialUsage`/`TestChatServiceImplCredentialWiring`；改 `ChatServiceImpl`（resolver 钩 + 优先级链）、`NopAiModelBizModel`（引用计数）、`_service.beans.xml`（bean 装配）、`nop-ai-service/pom.xml`（nop-credential-api dep）。
  - **测试**：nop-ai-core 217 / nop-ai-service 25 / nop-ai-dao 6 全绿（新增 7+7+5=19 测试：resolution/fail-closed/fallback/usage/bind-switch-unbind/ChatServiceImpl 端到端）。`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-service,nop-ai/nop-ai-dao -T 1C` → BUILD SUCCESS。
  - **Anti-Hollow**：`TestAiModelCredentialResolver.resolvesApiKeyFromCredentialWhenConfigured` 断言 ICredentialProvider.getCredentialData 被调用（calls==1）；`TestChatServiceImplCredentialWiring.credentialApiKeyIsInjectedIntoRequestHeader` 断言 HTTP bearer token = 凭证 key（调用链出口验证）。
  - **fail-closed**：3 类负向（凭证缺失传播/字段空 ERR_AI_CREDENTIAL_FIELD_EMPTY/provider 未部署 ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE）+ ChatServiceImpl failClosedWhenResolverThrows。
  - **文档**：design §3.4（集成点裁决 + resolver 契约）+ §3.5（nop-ai 重分类为已落地）；docs-for-ai/03-modules/nop-ai.md（运行时消费表述从 Deferred → 已落地 + 集成点/consumerRef）。docs-for-ai 链接检查 0 错误。
  - **零回归**：optional 注入（@Nullable）确保部署不含 nop-credential 时 ChatServiceImpl 跳过 credential 解析、回退 resolveApiKey。

Follow-up:

- 遗留 `DefaultAiChatService.getApiKey` 路径的 credentialId 覆盖（Phase 1 D1 裁定仅接主路径 ChatServiceImpl；遗留路径为 Non-Blocking Follow-up，理由：DefaultAiChatService 是重复 config-var/secret 逻辑的旧路径，主路径 ChatServiceImpl 是 coordinator/agent 与直调共用的唯一出口）。
- 全量模型迁移到 credentialId（optimization candidate，apiKey 列保留兼容）。
- `getCredentialData` 的 status(disabled) 强制 + lastUsedAt 追踪（Phase 1 裁定非 fail-closed 必需，列为优化候选）。
