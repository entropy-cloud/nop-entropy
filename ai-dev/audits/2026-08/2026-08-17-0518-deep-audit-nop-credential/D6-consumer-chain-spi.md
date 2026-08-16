# D6：nop-ai 消费链回归 + SPI 零变更

**审计日期**：2026-08-17
**审计维度**：nop-ai 消费链回归 + SPI 零变更（凭证库二期安全审计 charter D6）
**审计方式**：live code 只读探查 + git 历史只读 diff（`git log`/`git show`，仅用于用例 4/5 SPI 签名核对）
**基准文档**：`ai-dev/design/nop-credential/02-phase2-design.md`（§二 兼容性矩阵「一期消费链零回归」行、§4.1/§5.1 SPI 契约结论）；owner doc：`docs-for-ai/03-modules/nop-credential.md`、`docs-for-ai/03-modules/nop-ai.md`

---

## 一、对抗用例结论表

| # | 对抗用例 | 结论 | 关键证据（file:line） |
|---|---------|------|----------------------|
| 1 | `AiModelCredentialResolverImpl` 优先级链 accountKey > credentialId > resolveApiKey（含各级空值处理） | **探查到（代码层成立）**，但整链因 D6-01 在默认部署下不可达 | `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:286-297`（`resolveApiKeyForRequest`：accountKey 非空直接用 → resolver 装配且返回非空用之 → 回退 `LlmConfigHelper.resolveApiKey`）；resolver 入参空值：`AiModelCredentialResolverImpl.java:113-115`（provider/model 空 → return null 回退）、`:117-122`（credentialId 空 → return null 回退）。空值判断用 `StringHelper.isEmpty`（null/length==0，不含纯空白）——空白串行为见 D6-04。测试覆盖：`TestChatServiceImplCredentialWiring.java:94-135`（priority/fallback） |
| 2 | fail-closed：credentialId 非空但凭证缺失/软删/解密失败/字段空 → 拒绝，不静默回退 config key；逐异常分支无吞异常 | **探查到（代码层成立，全分支无 catch 回退）**；但"resolver 未装配"分支是静默跳过（属 D6-01 范畴） | resolver 侧：`AiModelCredentialResolverImpl.java:125-129`（provider 未部署 → `ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE`）、`:131`（`getCredentialData` 异常直接传播，注释明示"不吞"）、`:132-136`（字段空 → `ERR_AI_CREDENTIAL_FIELD_EMPTY`）、`:148-151`（daoProvider 未装配 → `ERR_AI_CREDENTIAL_RESOLVER_NOT_CONFIGURED`）。实现侧：`CredentialProviderImpl.java:460-482`（缺失 → `ERR_CREDENTIAL_NOT_FOUND`；软删 → `ERR_CREDENTIAL_DELETED`；oauth2 disabled → `ERR_CREDENTIAL_DISABLED`）、`:598`（解密失败由 `CredentialCipher` 抛出）。钩点侧：`ChatServiceImpl.java:286-297` 无 catch，`callAsync`/`callStream` 均不捕获 resolver 异常（流式经 `onError → closeExceptionally`）。测试：`TestAiModelCredentialResolver.java:186-223`（3 个 fail-closed 用例）、`TestChatServiceImplCredentialWiring.java:137-153` |
| 3 | 引用计数生命周期：registerUsage `ai:NopAiModel:<id>` + unregisterUsage 对称性（成功/失败路径泄漏；对凭证删除拦截的影响） | **部分探查到**：bind/换绑/解绑对称且同事务；**delete 路径泄漏**（D6-02）；registerUsage 不校验凭证存在（D6-03） | 接线：`NopAiModelBizModel.java:45`（`CONSUMER_REF_PREFIX = "ai:NopAiModel:"`）、`:72-78`（save override，事务内先读旧值）、`:99-120`（reconcile：bind/unbind/switch/noop，switch = unregister(A)+register(B) 同事务，失败整体回滚）。实现幂等：`CredentialProviderImpl.java:196-214`（查-插幂等）、`:217-228`（删全部匹配行）。删除拦截：`NopCredentialBizModel.java:691-701`（count>0 → `ERR_CREDENTIAL_HAS_ACTIVE_USAGE`）。泄漏证据：`NopAiModelBizModel` 未覆盖 delete（继承 `CrudBizModel.java:1045` 标准动作，无 usage 清理）。测试：`TestNopAiModelCredentialUsage.java`（bind/换绑/解绑/noop/consumerRef 约定） |
| 4 | `ICredentialProvider` SPI 签名零变更（git diff 一期后变更逐条列出；6 方法与设计 §4.1/§5.1 比对） | **探查到（零变更，diff 为空）** | `git log --follow` 该文件仅单 commit `1d12af9dc`（一期 plan-2026-08-12-0615），此后至 HEAD 零提交、工作区零 diff。live 6 方法（`ICredentialProvider.java:29/39/48/57/65/73`）：`getCredential`/`getCredentialData`/`testCredential`/`mask`/`registerUsage`/`unregisterUsage`——无 caller 参数（§5.1 结论 3"上下文捕获、SPI 签名零变更"成立：归属校验在 impl 内经 `IUserContext.get()`，`CredentialProviderImpl.java:499-574`）。注：nop-credential-api 模块内 `CredentialType.java` 有 +81 行一期后增量（W9 oauth2 类型元数据，registry 模型加法），非本维度两 SPI 接口 |
| 5 | `ICredentialKeyProvider` SPI 零变更（3 方法；是否声明对一期消费方零影响；nop-credential-service 之外消费方盘点） | **探查到（零变更，diff 为空）**。任务书"该接口为二期新增"与 live/git 不符：该接口为**一期**（W1）创建 | `git log --follow` 仅单 commit `dfb4f7ed7`（一期 plan-2026-08-12-0615 模块骨架与密码学层），此后零变更。live 3 方法（`ICredentialKeyProvider.java:29/38/45`）：`getActiveKeyId`/`getKey`/`getKeyIds`——与设计 §4.1 结论 1"SPI 面零变更、KMS 经新增实现类接入"一致；二期对一期消费方零影响由 §4.5"缺省部署零变化"声明并 live 佐证（`credential-defaults.beans.xml` 缺省装配未动，KMS 为条件化同名 bean 覆盖）。消费方盘点（全仓库 grep）：引用全部位于 nop-credential 模块族——`CredentialCipher.java:48`、`DefaultCredentialKeyProvider.java:36`、`NopCredentialBizModel.java:108`（reencryptAll）、二期新模块 `VaultCredentialKeyProvider.java:57`（新实现，非消费方）及测试。**nop-credential-service 之外的生产消费方 = 无**（nop-ai/nop-integration 等不引用） |
| 6 | `ChatServiceImpl` 钩点零回归（resolver 未装配回退一期 resolveApiKey；装配后优先级链；注入方式） | **探查到（零回归语义成立）** | `ChatServiceImpl.java:82`（字段声明）、`:98-101`（`@Inject` + `@Nullable` setter 注入，NopIoC optional；字段 private 但经 setter 注入，不受字段注入可见性约束——javadoc :80 自述与 private 声明有措辞不一致，无功能影响）、`:290`（`if (credentialResolver != null)` 跳过）、`:296`（回退 `resolveApiKey`）。测试：`TestChatServiceImplCredentialWiring.java:155-171`（noResolverWiredFallsBackToConfigVar）。git：`b6aebe2af`（W7-successor 交付）之后 nop-ai 消费链三文件零变更。"装配后"分支在默认部署不可达（D6-01） |

## 二、回归锚点结论

### 锚点 1：「一期消费链（W7-successor 等）零回归」（设计 §二 矩阵行）—— **PASS（带保留）**

**证据**：
- git：`git log --oneline -- nop-ai` 显示消费链三文件（`IAiModelCredentialResolver.java`/`AiModelCredentialResolverImpl.java`/`ChatServiceImpl.java`/`NopAiModelBizModel.java`）自 `b6aebe2af`（2026-08-13，W7-successor 交付）后零变更；二期 commits（W9 OAuth/W10 KMS/W11 归属 RBAC/W12 MFA）不触及 nop-ai 消费链。
- 二期在 provider 实现侧新增的校验对一期场景等价放行：`CredentialProviderImpl.java:499-502`（scope=system 含 NULL 放行——一期消费链消费的凭证缺省即此）、`:555-558`（无授权记录放行，"默认开放"）、`:477-479` 注释（非 oauth2 类型维持一期语义仅 delFlag）。
- 回退路径保持：未配 credentialId / resolver 未装配 → `resolveApiKey`（用例 6）。

**保留**：零回归的字面成立，但其前提"一期消费链功能正常"不成立——W7-successor 交付的 credentialId 运行时消费因 **D6-01（bean 未注册）从未真正接通**。该缺口为一期交付自带（非二期引入），故不判 FAIL，登记为 P1 finding。

### 锚点 2：「引用计数（registerUsage/consumerRef）」（D6 消费视角）—— **PASS（带保留）**

**证据**：
- SPI 六方法中 `registerUsage`/`unregisterUsage` 签名与幂等语义零变更（`ICredentialProvider.java:59-73`；`CredentialProviderImpl.java:196-228` 查-插/查-删幂等）。
- consumerRef 约定不变：`ai:NopAiModel:<modelId>`（`NopAiModelBizModel.java:45`，与设计 §3.5/§5.5"约定不变"一致）。
- 凭证删除拦截语义不变：`NopCredentialBizModel.java:691-701`（count>0 fail-closed 拒绝）。

**保留**：消费方（nop-ai）侧 delete 路径不清理 usage（**D6-02**）为生命周期完整性缺口，一期交付自带，登记 P2 finding，不改判锚点（契约面"不变"成立）。

### 锚点 3：「`ICredentialProvider` SPI 契约零变更（接口签名 diff 为空）」—— **PASS**

**证据**：`git log --follow -- .../ICredentialProvider.java` = 单 commit `1d12af9dc`（一期创建）；`1d12af9dc..HEAD` 对该文件 diff 为空；工作区 `git status` 干净。live 接口 6 方法、无默认方法、无 caller 参数，与设计 §5.1 结论 3 / W11-impl"不触碰 ICredentialProvider SPI 签名"完全一致。

---

## 三、Findings

### [D6-01] `AiModelCredentialResolverImpl` 无 NopIoC bean 注册——credentialId 运行时消费链在所有部署形态下静默失效（configured-but-broken 静默回退）

**文件路径**：
- `nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/credential/AiModelCredentialResolver.java:47`（实现类，无 bean 定义）
- `nop-ai/nop-ai-core/src/main/resources/_vfs/nop/ai/beans/ai-defaults.beans.xml:8-9`（`nopChatService` bean，无 resolver 可注入来源）
- `docs-for-ai/03-modules/nop-ai.md:59,69,81`（owner doc 宣称"已接通"/"resolver bean 仍创建"）

**证据代码**（ai-defaults.beans.xml —— nop-ai-core 侧唯一 ChatService 装配，容器内不存在任何 `IAiModelCredentialResolver` 类型 bean）：
```xml
    <bean id="nopChatService" class="io.nop.ai.core.service.ChatServiceImpl" ioc:default="true"
          ioc:type="io.nop.ai.api.chat.IChatService"/>
```
（全仓库 `*.xml` grep `CredentialResolver` / `aiModelCredentialResolver` = 0 命中；`nop-ai-service` 的 `_service.beans.xml`/`app-service.beans.xml` 仅含 codegen 生成的 BizModel 条目与 import；W7-successor 交付 commit `b6aebe2af` 的文件清单不含任何 beans.xml。）

```java
// ChatServiceImpl.java:98-101 —— @Nullable 使无 bean 时静默注入 null
    @Inject
    public void setCredentialResolver(@Nullable IAiModelCredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }
```

**严重程度**：**P1**

**现状**：NopIoC 不做注解扫描（AGENTS.md：bean 必须在 beans.xml 显式定义，`@Inject` 仅是注入元数据）。`AiModelCredentialResolverImpl` 类自 W7-successor 交付以来从未在任何 `_vfs` beans.xml 中注册。因此无论部署是否含 nop-credential-service，`ChatServiceImpl.credentialResolver` 恒为 null，`NopAiModel.credentialId` 在 LLM 运行时调用链上**从不生效**，静默回退 `resolveApiKey`（config 变量/secret 文件/apiKey 列路径）。而管理链是通的：`NopAiModelBizModel` 是已注册 bean（`_service.beans.xml:22`），save 时 `registerUsage` 正常登记 `ai:NopAiModel:<id>` 引用。

**风险**：
1. 直接命中本维度威胁假设"消费链 fail-closed 退化（configured-but-broken 静默回退旧路径）"：用户按 owner doc 迁移路径（nop-ai.md:75-79：建凭证 → 设 credentialId → 引用自动登记）操作后，管理面锁定了凭证（引用计数拦截删除），运行时却仍用 config 旧 key——两条链路行为分裂且无任何告警。
2. 设计宣称的强 fail-closed（"credentialId 非空但凭证缺失/软删/解密失败 → 拒绝"，`IAiModelCredentialResolver.java:29-31`）在默认部署下整体不可达：凭证被吊销/轮换/软删后，AI 调用不会失败切换也不会报错，继续用旧 config key——凭证治理（轮换、吊销、审计）对运行时消费失效。
3. `ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE`（防"配了 credentialId 但凭证库未部署"）同样不可达——防御逻辑挂在永远不会被创建的 bean 内。
4. 现有测试全部绕过容器（`TestAiModelCredentialResolver` 直接 `new` + setter；`TestChatServiceImplCredentialWiring` 手动 `setCredentialResolver`），故测试全绿掩盖了装配缺位。

**建议**：在 nop-ai-service 增加手写 beans 文件（如 `ai-service-defaults.beans.xml`，`ioc:default="true"`）注册 `AiModelCredentialResolverImpl`，并纳入消费 app 装配链；同时补一个经 NopIoC 容器解析 `IAiModelCredentialResolver` bean 的装配级测试（防回归）；修正 owner doc nop-ai.md:81"resolver bean 仍创建"的表述直至 bean 真实存在。若判定该链路为"部署方手工接线"的有意设计，则必须在 owner doc 显式给出注册方式与装配示例，并将"自动 registerUsage（管理链已通）+ 运行链未接通"的分裂状态登记为已知限制。

**信心水平**：高（全仓库 xml 零匹配 + 交付 commit 文件清单核对 + NopIoC 无扫描机制的 AGENTS.md 明确记载 + owner doc 表述矛盾四重印证）。

**误报排除**：已排除三种可能：(a) NopIoC 注解扫描自动注册——AGENTS.md 明确否定，且 `NopAiModelBizModel` 等 `@BizModel` 类均需 codegen 生成 `_service.beans.xml` 条目才成为 bean；(b) 消费 app（nop-ai-app/nop-chaos/e2e）侧注册——全仓库 `*.xml` grep 零命中；(c) 经 `credential-defaults.beans.xml` 注册——该文件仅装配 nop-credential 侧 bean（keyProvider/cipher/provider/registry），grep 无 resolver 条目。

### [D6-02] `NopAiModelBizModel` 未覆盖 delete——删除模型行不 unregisterUsage，引用计数泄漏导致凭证删除被永久拦截（运维死锁）

**文件路径**：
- `nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/entity/NopAiModelBizModel.java:72-78`（仅覆盖 save）
- `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1045`（标准 delete 动作，NopAiModel 继承可用）
- `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:691-701`（删除拦截）

**证据代码**：
```java
// NopAiModelBizModel —— 仅 save 接线 usage reconcile，无 delete 覆盖：
    @Override
    public NopAiModel save(Map<String, Object> data, IServiceContext context) {
        String oldCredentialId = readOldCredentialId(data);
        NopAiModel saved = super.save(data, context);
        reconcileCredentialUsage(saved, oldCredentialId);
        return saved;
    }
```
```java
// NopCredentialBizModel.prepareDeleteWithUsageCheck —— 引用计数 >0 即拒绝删除凭证：
        long count = usageDao.countByQuery(usageQuery);
        if (count > 0) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_HAS_ACTIVE_USAGE)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, entity.getCredentialId())
                    .param(CredentialErrors.ARG_USAGE_COUNT, count);
        }
```

**严重程度**：**P2**

**现状**：`NopAiModelBizModel` 的引用计数接线只覆盖 save（bind/换绑/解绑）。继承的标准 `delete` 动作（`CrudBizModel.delete`，NopAiModel 的 xmeta 未禁用）删除模型行时不调用 `unregisterUsage`，`nop_credential_usage` 中 `ai:NopAiModel:<modelId>` 行残留。`ICredentialProvider` SPI 亦无按 consumerRef 前缀/模型侧批量清理的机制（仅有精确 `(credentialId, consumerRef)` 对的 unregister）。

**风险**：删除已配 credentialId 的模型后，该凭证的引用计数永不归零，`NopCredentialBizModel.delete` 的 `ERR_CREDENTIAL_HAS_ACTIVE_USAGE` 拦截使其**永久不可删除**——而正常解绑通道（模型 save 置空 credentialId）已随模型行消失，唯一出路是手工 DB 清理 `nop_credential_usage` 残留行。威胁假设"引用计数泄漏"直接命中：泄漏方向是"计数虚高 → 拦截过严"，不产生安全弱化（fail-closed 方向），但构成真实的运维死锁与维护成本；软删的僵尸凭证行持续占用存储且在管理面可见。

**建议**：`NopAiModelBizModel` 覆盖 `delete`（及评估 `batchDelete`/`deleteByQuery` 是否同步收口，对齐 `NopCredentialBizModel` 的旁路禁用先例）：删除前读旧 credentialId，非空则在同事务内 `unregisterUsage(credentialId, "ai:NopAiModel:<id>")`；补回归测试（delete 模型 → usage 行消失 → 凭证可删除）。

**信心水平**：高（继承动作可达性：CrudBizModel.delete 标准 @BizMutation + NopAiModel xmeta 无禁用条目；无其他清理路径：全仓库 `ai:NopAiModel:` 引用仅 save 接线与测试）。

**误报排除**：已排查 (a) ORM 级联删除 usage 行——usage 表在 nop-credential 模块，`credentialId` 是逻辑外键（nop-ai.md:63 明确不声明 refEntityName），无 FK 级联；(b) 平台 ChangeLog/审计机制自动清理——无此机制；(c) `NopCredentialUsageBizModel` 提供管理面删除通道——其标准 delete 等动作被禁用（仅 admin 查询面），且即便可用也需人工知道残行的 (credentialId, consumerRef) 对。

### [D6-03] `registerUsage` 不校验凭证存在性——绑定不存在的 credentialId 时管理面静默成功，错误延迟至运行时消费才暴露

**文件路径**：
- `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java:196-214`
- `nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/entity/NopAiModelBizModel.java:107-109`

**证据代码**：
```java
// CredentialProviderImpl.registerUsage —— 查重后直接插行，不校验 credentialId 指向的凭证存在/未软删：
    public void registerUsage(String credentialId, String consumerRef) {
        IEntityDao<NopCredentialUsage> dao = daoProvider.daoFor(NopCredentialUsage.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("credentialId", credentialId));
        query.addFilter(FilterBeans.eq("consumerRef", consumerRef));
        List<NopCredentialUsage> existing = dao.findAllByQuery(query);
        if (!existing.isEmpty()) {
            return;
        }
        ... usage.setCredentialId(credentialId); ...
        dao.saveEntityDirectly(usage);
    }
```

**严重程度**：**P3**

**现状**：`NopAiModelBizModel.save` 绑定 credentialId（bind/换绑）时调 `registerUsage`，后者对目标凭证零校验（不存在、已软删均可登记成功）。模型保存静默成功 + 引用计数登记成功，配错仅在运行时消费（`getCredentialData` → `ERR_CREDENTIAL_NOT_FOUND`）才 fail-closed 暴露。

**风险**：(a) 配错 ID（含已软删凭证的 ID）在管理面无即时反馈，故障定位延迟到第一次 LLM 调用失败；(b) 产生孤儿 usage 行（指向不存在凭证），若日后恰好以相同 id 新建凭证（UUID 场景概率极低但理论存在）会被误拦截删除。fail-closed 最终方向正确（运行时拒绝），属错误暴露延迟与数据卫生问题，非安全弱化。

**建议**：`registerUsage` 在插入前按一期 `loadActiveCredential` 同口径校验目标凭证存在且未软删（不存在 → 抛 `ERR_CREDENTIAL_NOT_FOUND`，使 save 在同事务内回滚）；或在 `NopAiModelBizModel` save 前置校验 credentialId 有效性。属行为收紧（原来"成功"的配错路径将报错），建议随 owner doc 显式声明。

**信心水平**：高（实现代码直读；无其他校验层——`NopAiModelBizModel.save` 与 xmeta 均无 credentialId 存在性校验）。

**误报排除**：已排查 save 路径是否有前置校验（`NopAiModelBizModel.java:72-93` 无）；已排查 xmeta 级外键校验（credentialId 为普通列无 refEntityName，nop-ai.md:63 明确）。

### [D6-04] 优先级链与 apiKey 非空校验使用 `isEmpty`（不含纯空白）——空白串值被当有效 key 使用

**文件路径**：
- `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:287-292`
- `nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/credential/AiModelCredentialResolverImpl.java:132`
- `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/ApiStringHelper.java:49-53`

**证据代码**：
```java
// ApiStringHelper.isEmpty —— 仅判 null / length==0，不 trim：
    public static boolean isEmpty(CharSequence o) {
        if (o == null)
            return true;
        return o.length() == 0;
    }
```
```java
// ChatServiceImpl.resolveApiKeyForRequest:287-292
        if (!StringHelper.isEmpty(accountKey)) {
            return accountKey;                      // accountKey = " " 时被当有效 key 直接使用
        }
        if (credentialResolver != null) {
            String credApiKey = credentialResolver.resolveApiKeyByCredential(provider, model);
            if (!StringHelper.isEmpty(credApiKey)) { // " " 同样通过
                return credApiKey;
            }
        }
```
```java
// AiModelCredentialResolverImpl:132 —— 凭证 apiKey 字段为 " " 时通过 fail-closed 非空校验：
        if (apiKeyValue == null || StringHelper.isEmpty(apiKeyValue.toString())) {
            throw new NopException(ERR_AI_CREDENTIAL_FIELD_EMPTY) ...
```

**严重程度**：**P3**

**现状**：三级 key 判空与 fail-closed 字段空校验均用 `isEmpty`（null/"" 捕获，纯空白 " "/"\t" 不捕获）。空白 accountKey 会以最高优先级覆盖 credentialId/config key；凭证库 apiKey 字段存了空白串时通过 `ERR_AI_CREDENTIAL_FIELD_EMPTY` 防线，把空白 key 注入 Authorization header（401 失败，错误信息误导为"key 无效"而非"凭证配错"）。`NopAiModelBizModel.reconcileCredentialUsage`（:107-118）同口径：credentialId 存空白串会当作"已配置"走 bind 分支登记引用。

**风险**：低概率（需上游产生空白值：表单录入/凭证字段粘贴事故），后果为调用失败或优先级链行为异常，无越权/泄密面；但违背"字段空 → 强 fail-closed 防凭证配错静默用错 key"的自洽意图（空白本质也是配错）。

**建议**：三处（ChatServiceImpl 判空、resolver 字段空校验、reconcile 判空）统一改用 `StringHelper.isBlank`（或等效 trim 判断），使空白与空串同路径 fail-closed/回退；补空白值边界测试。

**信心水平**：高（`StringHelper extends ApiStringHelper`，isEmpty 语义直读确认；平台另有 `isBlank` 可用，`ApiStringHelper.java:143`）。

**误报排除**：已确认平台存在 `isBlank` 且本项目其他安全路径（如 `CredentialProviderImpl.refreshIfNearingExpiry:391` 用 `StringHelper.isEmpty(refreshToken)`）同样口径——这属于全链一致的口径选择，非单点笔误；本 finding 按边缘输入完整性登记为 P3。

---

## 四、检查范围清单（零发现面亦列示）

| 检查项 | 结论 |
|--------|------|
| `IAiModelCredentialResolver.java`（SPI javadoc 契约 vs 实现） | 一致（返回 null/抛错语义与 javadoc :23-31 逐条对应） |
| `AiModelCredentialResolverImpl` 全部分支（:112-170） | 已逐行核（用例 1/2） |
| `ChatServiceImpl` 钩点与优先级链（:82/:98-101/:253-297） | 已核（用例 6）；javadoc :80"非 private"与实际 private 字段措辞不一致（无功能影响，setter 注入不受限） |
| `ICredentialProvider` 6 方法 git diff（一期后） | 空（锚点 3 PASS） |
| `ICredentialKeyProvider` 3 方法 git diff（一期后） | 空（用例 5 PASS）；任务书"二期新增"表述与 live 不符（一期 dfb4f7ed7 创建） |
| `ICredentialKeyProvider` 模块外消费方盘点 | 无（全仓库 grep） |
| nop-ai 消费链文件二期变更（git log nop-ai） | `b6aebe2af` 后仅 `df2b0fb55`（gateway 测试/stream beans 修复，不触及消费链）与 `68a321f85`（ai-agent spill，无关） |
| resolver bean 注册（全仓库 `*.xml`） | 零命中（D6-01） |
| `NopAiModelBizModel` save/delete/batchDelete 覆盖面 | save 已接线；delete/batchDelete 未覆盖（D6-02） |
| `CredentialProviderImpl` registerUsage/unregisterUsage 幂等与对称性 | 幂等成立、同事务一致（用例 3）；存在性校验缺失（D6-03） |
| `NopCredentialBizModel.delete` 引用计数拦截 + 旁路动作禁用（update/batchDelete/updateByQuery/deleteByQuery/copyForNew） | 拦截与禁用均在（:652-761），与设计 §5.3 收口一致 |
| 消费链测试覆盖面（`nop-ai-service/src/test` + `nop-ai-core/src/test`） | `TestAiModelCredentialResolver`（6 用例：resolution/fallback×2/fail-closed×3）、`TestChatServiceImplCredentialWiring`（5 用例：resolution/priority/fallback/fail-closed/no-resolver）、`TestNopAiModelCredentialUsage`（bind/换绑/解绑/noop/consumerRef）——单测/接线级完备，**容器装配级缺失**（D6-01 成因之一） |
| 工作区状态（`git status` nop-credential-api/、nop-ai/） | 干净，无未提交变更 |

## 五、结论摘要

- **6 个对抗用例全部执行**：代码层 fail-closed/优先级链/引用计数接线/SPI 零变更均成立；核心缺口在**装配层**（D6-01）与**生命周期完整性**（D6-02）。
- **3 个回归锚点**：全部 PASS（D6-01/D6-02 为一期交付自带缺口，非二期回归，以 finding 形式登记；二期四主题对一期消费链与两 SPI 接口的"零变更/零回归"承诺 live 成立）。
- **Findings**：P1×1（D6-01）、P2×1（D6-02）、P3×2（D6-03、D6-04）。
