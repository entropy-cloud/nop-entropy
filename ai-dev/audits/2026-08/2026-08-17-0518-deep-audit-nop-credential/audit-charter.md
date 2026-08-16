# A1-audit 审计章程（凭证库二期安全审计）

> Charter Version: 1.0（2026-08-17 live repo 核对）
> Plan: `ai-dev/plans/2026-08-17-0447-1-credential-phase2-security-audit.md`
> Regression Baseline: `ai-dev/design/nop-credential/02-phase2-design.md` §二 一期契约兼容性影响矩阵（五行）+ §x.5 各主题兼容小节 + §八 impl 映射（含 impl 裁定回写标注）；W9-impl plan `2026-08-14-2342-1`、W10-impl plan `2026-08-14-2342-2`、W11-impl Part A plan `2026-08-14-2342-3`、Part B plan `2026-08-16-2321-1`、W12-impl plan `2026-08-16-2321-2` 的 Closure 证据与 Deferred/Follow-up 登记
> 审计类型: deep-audit（对抗探查，六维度 D1-D6）
> 执行约束: 探查由 fresh 独立子 agent 分维度执行（roadmap 二期审计门禁）；探查子 agent 只探查与报告不改产品代码；P0/P1 修复由编排 session 执行并由另一 fresh 子 agent 复核

## 一、审计范围（模块/实体/端点全清单）

### 1.1 模块

| 模块 | 内容 | 审计维度 |
|---|---|---|
| `nop-credential/nop-credential-api` | SPI 接口（`ICredentialProvider`/`ICredentialKeyProvider`/`CredentialData`/`MaskedCredential`/`TestResult`/registry） | D6（SPI 零变更）、D1 |
| `nop-credential/nop-credential-dao` | 实体 `NopCredential`/`NopCredentialAuth`/`NopCredentialOauthState`/`NopCredentialUsage` + orm 源 | D1/D2/D4 |
| `nop-credential/nop-credential-meta` | xmeta（含 `NopCredential.xmeta` `data published=false`） | D1 |
| `nop-credential/nop-credential-service` | `CredentialProviderImpl`（唯一解密点）、`CredentialCipher`、`DefaultCredentialKeyProvider`、`CredentialOwnership`、四个实体 BizModel、OAuth 引擎（`OAuthFlowService`/`NopCredentialOauthStateStore`/`OAuthTokenClient`/`CredentialOAuthApiBizModel`）、`DefaultCredentialTypeRegistry` | D1-D6 全部 |
| `nop-credential/nop-credential-kms-vault` | `VaultCredentialKeyProvider` + `app-kms-vault.beans.xml` 门控装配 | D3 |
| `nop-credential/nop-credential-web` | AMIS 页面（NopCredential/NopCredentialAuth/NopCredentialUsage/NopCredentialOauthState view.xml/page.yaml）、action-auth delta | D1/D4 |
| `nop-ai/nop-ai-api` + `nop-ai/nop-ai-service` + `nop-ai/nop-ai-core` | `IAiModelCredentialResolver` + `AiModelCredentialResolverImpl` + `ChatServiceImpl` 钩点（一期消费链回归面） | D6 |

### 1.2 攻击面枚举（live file:line 锚点，2026-08-17 核对）

#### S1 凭证管理面 BizModel 动作（`NopCredentialBizModel`）

`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java`

| 动作 | 行号 | 面性质 |
|---|---|---|
| `save`（禁用，抛 UnsupportedOperationException） | :127 | 旁路禁用 |
| `saveCredential`（明文输入唯一入口，分组写/保留字段拒绝/归属分级） | :162 | 明文入 |
| `get` | :338 | 读（越权归一不存在） |
| `findPage` | :362 | 读（结构性过滤） |
| `defaultPrepareQuery`（两层防御第一层：读过滤注入） | :386 | 结构性读过滤 |
| `maskList` | :472 | 脱敏出口 |
| `typeList`（动态表单 schema，oauth2 保留字段裁剪） | :492 | 元数据 |
| `test`（连通性测试） | :526 | 进程内解密 |
| `reencryptAll`（批量重加密，keyset 翻页） | :571 | 管理员全量敏感操作 |
| `delete`（引用计数拦截 + 软删除 + 物理级联授权清理） | :668 | 写 |
| `update`（禁用） | :724 | 旁路禁用 |
| `batchDelete`（禁用） | :735 | 旁路禁用 |
| `updateByQuery`（禁用） | :746 | 旁路禁用 |
| `deleteByQuery`（禁用） | :757 | 旁路禁用 |
| `copyForNew`（禁用） | :770 | 旁路禁用 |
| `batchGet`（行级可见性过滤语义，非禁用） | :784 | 读旁路收口 |

**六旁路动作禁用清单**：`save`:127 / `update`:724 / `batchDelete`:735 / `updateByQuery`:746 / `deleteByQuery`:757 / `copyForNew`:770；`batchGet`:784 为过滤语义收口。

#### S2 授权管理面（`NopCredentialAuthBizModel`，W11 Part B）

`.../service/entity/NopCredentialAuthBizModel.java` — 七旁路动作禁用：`save`:213 / `update`:225 / `delete`:236 / `batchDelete`:246 / `updateByQuery`:256 / `deleteByQuery`:267 / `copyForNew`:278；保留动作：`findPage`（构造器 :66 requireCredentialAdmin）/`get`:73 /`batchGet`:84 /`grant`:103 /`revoke`:137；`requireCredentialAdmin`:193。

#### S3 usage 查询面（`NopCredentialUsageBizModel`）

`.../service/entity/NopCredentialUsageBizModel.java` — admin-only：构造器 :43、`get`:50、`batchGet`:61、`requireCredentialAdmin`:72。

#### S4 OAuth 引擎面（W9）

| 组件 | 锚点 | 面 |
|---|---|---|
| `CredentialOAuthApiBizModel.beginOAuthFlow`（@BizMutation :46，登录态） | `.../service/oauth/CredentialOAuthApiBizModel.java`:47 | 发起授权 |
| `CredentialOAuthApiBizModel.oauthCallback`（@BizQuery :57 + `@Auth(publicAccess=true)` :58） | :59 | **公开回调端点（唯一 publicAccess 面）** |
| `OAuthFlowService.beginOAuthFlow`（归属/类型/禁用校验链 + state 生成） | `.../service/oauth/OAuthFlowService.java`:170 | 引擎 |
| `OAuthFlowService.handleOAuthCallback`（state 原子一次性消费 :208-222 + token 回写） | :208 | 引擎 |
| `NopCredentialOauthStateStore`（create/peek/consume :68/:89/:99，128bit 随机 state :60，惰性清理 :114） | `.../service/oauth/NopCredentialOauthStateStore.java` | state 存储 |
| `OAuthTokenClient`（令牌端点表单交互） | `.../service/oauth/OAuthTokenClient.java` | 出站 HTTP |
| `NopCredentialOauthStateBizModel`（codegen 默认 CRUD 面，未显式禁用） | `.../service/entity/NopCredentialOauthStateBizModel.java`:10 | ⚠️ 待 D2/D4 探查（state 表管理面暴露面） |

#### S5 provider 明文出口与引擎内部通道（`CredentialProviderImpl`，唯一解密点）

`.../service/CredentialProviderImpl.java`

| 方法 | 行号 | 出口性质 |
|---|---|---|
| `getCredential` | :123 | **明文出口（归属+授权判定串联点）** |
| `getCredentialData` | :140 | **明文出口** |
| `testCredential` | :146 | 进程内解密（owner+admin） |
| `mask` | :163 | 脱敏（owner+admin） |
| `registerUsage` | :196 | 引用计数 |
| `unregisterUsage` | :217 | 引用计数 |
| `countUsage` | :230 | 计数 |
| `engineGetDecryptedFields`（引擎内部通道，豁免授权检查） | :247 | 引擎通道 |
| `engineUpdateTokenFields` | :260 | 引擎通道 |
| `engineUpdateInLock`（DB 行级锁写通道） | :287/:298 | 引擎通道（惰性刷新互斥载体） |

#### S6 密钥层（KMS/本地）

| 组件 | 锚点 | 面 |
|---|---|---|
| `CredentialCipher`（cv1: 解析/加密/解密 fail-closed） | `.../crypto/CredentialCipher.java`:28（CV1_MARKER :34、KEY_ID_PATTERN :40、encrypt :62/:78、decrypt :90） | 密文格式 |
| `DefaultCredentialKeyProvider`（local 缺省 + 非 local 守卫） | `.../crypto/DefaultCredentialKeyProvider.java`:36（init 守卫 :77-95：master-keys 残留 :88 / 模块缺失 :90） | 密钥来源 |
| `VaultCredentialKeyProvider`（Vault KV v2 材料交付，启动期 GET） | `nop-credential-kms-vault/.../VaultCredentialKeyProvider.java`:57（init :144、fetchMaterial :248） | KMS |
| 同名 bean 门控覆盖装配 | `nop-credential-kms-vault/src/main/resources/_vfs/nop/credential/beans/app-kms-vault.beans.xml`（if-property nop.credential.key-provider=vault） | 装配 |
| local 缺省装配 | `nop-credential-service/src/main/resources/_vfs/nop/credential/beans/credential-defaults.beans.xml`（nopCredentialKeyProvider ioc:default） | 装配 |
| 配置项 | `.../config/CredentialConfigs.java`（master-keys :31 / active-key-id :38 / oauth-state-ttl :47 / refresh-window :55 / callback-base-url :63 / result-page-url :70 / admin-roles :81） | 配置 |

#### S7 GraphQL/REST/Web 数据通道

| 通道 | 锚点 | 边界机制 |
|---|---|---|
| xmeta `data published=false` | `nop-credential-meta/src/main/resources/_vfs/nop/credential/model/NopCredential/NopCredential.xmeta`:6 | 结构性明文边界 |
| BizModel 层置空（findPage/get data 恒空） | `NopCredentialBizModel`（见 S1） | 第二层 |
| maskList 输出 | `NopCredentialBizModel.java`:472 | 脱敏 |
| Web view（列表无 data 列/表单无 data 字段/saveCredential 提交契约） | `nop-credential-web/src/main/resources/_vfs/nop/credential/pages/NopCredential/NopCredential.view.xml`:5-16,:99-103 | 前端 |
| action-auth delta（saveCredential/mutation 对登录用户开放；delete/reencryptAll admin；Usage/Auth 面 admin） | `nop-credential-web/src/main/resources/_vfs/nop/credential/auth/nop-credential.action-auth.xml` | 授权层 |
| oauth_state 实体列 `state` tagSet `var,not-pub` | `nop-credential/model/nop-credential.orm.xml`:168 | state 不发布 |
| `NopCredentialAuth.auths` 关系 `cascadeDelete=true` + tagSet `not-pub` | `nop-credential/model/nop-credential.orm.xml`:84-88 | 级联/不可选 |

#### S8 消费链（nop-ai，一期回归面）

| 组件 | 锚点 |
|---|---|
| `IAiModelCredentialResolver` SPI | `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/credential/IAiModelCredentialResolver.java` |
| `AiModelCredentialResolverImpl`（优先级链 + fail-closed） | `nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/credential/AiModelCredentialResolverImpl.java`:47（fail-closed 语义 :62-99、resolveApiKeyByCredential :112） |
| `ChatServiceImpl` 钩点 | `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java`:82（priority chain :239-245） |
| 引用计数 consumerRef `ai:NopAiModel:<id>` | `AiModelCredentialResolverImpl`（registerUsage/unregisterUsage） |
| `ICredentialProvider` SPI 契约 | `nop-credential/nop-credential-api/src/main/java/io/nop/credential/api/ICredentialProvider.java`（6 方法：getCredential/getCredentialData/testCredential/mask/registerUsage/unregisterUsage） |
| `ICredentialKeyProvider` SPI 契约 | `nop-credential/nop-credential-api/src/main/java/io/nop/credential/api/crypto/ICredentialKeyProvider.java`（3 方法：getActiveKeyId/getKey/getKeyIds） |
| `@sec:` 配置加密（共存回归） | `DefaultConfigValueEnhancer` + `AESTextCipher`（nop-config/nop-commons，不修改仅探查交叉污染） |
| 类型声明 xdef | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/credential/credential-type.xdef`（authType 取值域 + oauth2 元数据） |

### 1.3 回归基准声明

- 设计 §二 影响矩阵五行 + 两附加锚点（`ICredentialProvider` SPI 零变更 → D6；`@sec:` 配置加密共存 → D5）为 PASS/FAIL 判定输入，每个锚点有归属维度（Goals 段映射）。
- impl plan Closure 段声明的"一期契约锚点零回归"为本审计的**被验证对象**（对抗复核），不是免检凭据。

## 二、六维度探查清单（威胁假设 + 对抗用例 + 目标锚点）

### D1 明文边界回归（归属维度锚点：明文边界 + 软删除 fail-closed）

**威胁假设**：越权身份经 GraphQL 字段投影/REST/Web 三通道取得 `data` 明文或密文；provider 明文出口被非 owner 调用；已软删凭证仍可解密。

对抗用例：
1. xmeta `published=false` 结构性验证（`NopCredential.xmeta`:6）+ GraphQL schema 中 data 字段不可达。
2. `findPage`/`get`/`batchGet` 返回体 data 恒置空（BizModel 第二层）。
3. `maskList` 输出仅 `****`/截断值，无明文。
4. provider `getCredential`/`getCredentialData`：user 级非 owner（含 admin）/无上下文拒绝；伪造 consumerRef 无关性（取用与 consumerRef 无关）。
5. delFlag fail-closed 先序（解密前）：已软删凭证全出口拒绝。
6. 越权单条访问归一"不存在"（防 credentialId 枚举探测归属）。
7. 字段投影攻击：GraphQL query 对 NopCredential 实体任意 selection set 组合不含明文。
8. `_dump`/日志/错误消息中无明文泄漏。

### D2 OAuth 引擎对抗（归属维度锚点：无直接锚点，显式增量项 status=disabled）

**威胁假设**：state 篡改/重放/并发双回调；回调参数注入（code/state 注入换 token 写入他人凭证）；过期 state 复用；并发刷新竞态互踢；伪造保留字段破坏刷新状态机。

对抗用例：
1. state 单次消费原子性（`NopCredentialOauthStateStore.consume`:99 条件 UPDATE + affected-row）。
2. state 不可预测性（128bit 安全随机 :60）+ TTL（expireAt）。
3. state 未命中/过期/重放统一 fail-closed（`OAuthFlowService.handleOAuthCallback`:208-222，不区分细节防探测）。
4. 回调公开面最小化：`@Auth(publicAccess=true)` 仅 `oauthCallback`（:58），无其他 publicAccess。
5. 回调响应载体（WebContentBean HTML 跳转页）不含 token 明文。
6. `beginOAuthFlow` 归属校验（system=admin / user=owner+admin，W11 回补）+ disabled/delFlag/类型校验链。
7. disabled 凭证全路径拒绝（发起/回调/刷新/取用）。
8. `saveCredential` 保留字段拒绝 + 分组写（人工字段整包替换语义保持、token 集不动）。
9. 惰性刷新互斥（DB 行级锁 engineUpdateInLock:287）：并发刷新恰一次、无互踢。
10. `NopCredentialOauthStateBizModel` 暴露面探查（codegen CRUD 面对 state 行的可达性——state 列 not-pub，但动作面本身是否需要收口）。
11. 回调参数注入：code/state 特殊字符、超长、空值 fail-closed。

### D3 KMS 故障路径 fail-closed（归属维度锚点：cv1 密文格式 → D5；本维度专责启动期校验组）

**威胁假设**：KMS 故障时静默降级本地密钥；同名 bean 覆盖失效导致双 provider；材料混合；迁移残余列表被滥用。

对抗用例：
1. 启动期校验组全路径：不可达/401/403/404/材料非法/来源混合（master-keys 残留）/迁移残余含 active key → 启动失败。
2. 无本地降级：KMS 配置下任何运行期故障不回退本地。
3. 运行期零托管端调用（`getKey` 仅查内存 map）。
4. 同名 bean 门控覆盖优先级（`app-kms-vault.beans.xml` if-property 精确匹配 + default bean 排除）。
5. default-bean 缺失守卫（配置指向 vault 但模块未部署 → `DefaultCredentialKeyProvider.init`:88-90 抛错）。
6. 迁移残余列表只解不加密 + 每次启动 WARN 审计。
7. `reencryptAll` keyset 翻页完备性（无漏行/无死循环/orderBy credentialId 确定性）。

### D4 归属与授权绕过对抗（归属维度锚点：引用计数 → D4/D6）

**威胁假设**：绕过 BizModel 两层防御；复活被禁用旁路动作；RBAC 判定矩阵被上下文伪造绕过；admin 豁免面过大；授权行旁路写入/删除；级联清理缺失。

对抗用例：
1. provider per-method 归属矩阵（明文出口 owner 唯一；mask/test owner+admin）逐方法验证。
2. BizModel `defaultPrepareQuery` 读过滤（非 admin：scope=system ∪ 自己的 user 级）+ 写分级。
3. 六旁路动作（S1 清单）+ 七旁路动作（S2 清单）禁用不可复活（GraphQL/REST 直达均抛错）。
4. `batchGet` 过滤语义（不可见行剔除，非 admin 全量返回）。
5. `NopCredentialAuth` §6.3 判定矩阵三态：无记录=放行 / 有记录+无上下文=放行（服务级信任）/ 有记录+有上下文=角色求交；admin 不豁免；引擎内部通道豁免的两个可达调用点（beginOAuthFlow→engineGetDecryptedFields、saveCredential→engineUpdateInLock）。
6. grant/revoke 幂等契约 + grant 拒绝 user 级凭证 + roleId 不存在性校验缺失的死记录无害性。
7. 凭证删除物理级联清理授权行（delete 拦截时同存语义）。
8. action-auth delta 面（saveCredential/mutation 对登录用户开放的补偿控制）。
9. `nop.credential.admin-roles` 配置绕过探查（空值/通配/大小写/注入）。
10. `NopCredentialOauthState`/`NopCredentialUsage` 面的越权读写。

### D5 密钥轮换/多 key 并存/密文格式兼容（归属维度锚点：cv1 密文格式 → D5；@sec: 共存附加锚点 → D5）

**威胁假设**：keyId 篡改/伪造格式/跨 key 重放使密文被错误解密；多 key 并存窗口期旧 key 退役过早；`@sec:` 与 cv1: 交叉污染。

对抗用例：
1. `cv1:{keyId}:` 解析（`CredentialCipher`:90-115）：格式错误/未知 keyId/篡改密文 fail-closed。
2. 多 key 解密（keyId 路由）+ active key 加密（新写入用 active）。
3. keyId 篡改攻击（密文 A 的 keyId 段替换为 keyId B → 解密失败 fail-closed，不误解）。
4. 跨 key 重放（同一明文经不同 keyId 加密的密文互不可替换）。
5. `reencryptAll` 全量迁移语义（active key 重加密、幂等跳过、逐条提交可重跑）。
6. `@sec:` 配置加密共存：`DefaultConfigValueEnhancer` 路径与 cv1: 无交叉（代码审查 + 两者格式互不解析验证）。
7. keyId 模式注入（`[A-Za-z0-9_-]+` :40——keyId 含 `:`/空格/超长被拒）。

### D6 nop-ai 消费链回归 + SPI 零变更（归属维度锚点：一期消费链零回归 + 引用计数 + SPI 零变更附加锚点）

**威胁假设**：消费链 fail-closed 退化（configured-but-broken 静默回退旧路径）；引用计数泄漏/误删；SPI 契约隐性变更破坏一期消费方。

对抗用例：
1. `AiModelCredentialResolverImpl` 优先级链：accountKey > credentialId > resolveApiKey 逐级验证。
2. fail-closed：credentialId 非空但凭证缺失/软删/解密失败/字段空 → 拒绝（不静默用 config key）。
3. 引用计数生命周期：registerUsage `ai:NopAiModel:<id>` + unregisterUsage 对称。
4. `ICredentialProvider` SPI 签名 diff 为空（与一期比对：git log/diff 或接口内容审查）。
5. `ICredentialKeyProvider` SPI 签名 diff 为空。
6. `ChatServiceImpl` 钩点零回归（resolver 未装配时回退 resolveApiKey 一期行为）。

## 三、执行与证据约定

- 每维度 detail 报告落盘本目录（`D1-plaintext-boundary.md` ... `D6-consumer-chain-spi.md`），findings 标 P0-P3 + file:line 锚点 + 探查证据。
- 探查性安全测试（若落盘）进 nop-credential/nop-ai 测试树，类名/注释标注「A1-audit 对抗探查」来源。
- summary.md 汇总 findings 总表 + 独立子 agent 执行证据（task/session 标识）。
- adjudication.md 裁决表（Phase 3）：每条 finding → fixed / successor / deferred-with-reason，零悬挂；8 项路由 deferred/follow-up 逐项再裁定。
