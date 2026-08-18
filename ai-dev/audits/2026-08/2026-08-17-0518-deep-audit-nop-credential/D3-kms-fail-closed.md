# D3 — KMS 故障路径 fail-closed 独立审计报告

- **维度**：D3 KMS 故障路径 fail-closed（静默降级本地密钥 / 同名 bean 覆盖失效双 provider / 材料混合 / 迁移残余列表滥用）
- **审计日期**：2026-08-17
- **审计者**：独立安全审计子 agent（fresh session，只读产品代码）
- **回归基准**：`ai-dev/design/nop-credential/02-phase2-design.md` §四（外部 KMS/HSM 集成）、§4.5（兼容小节）、§二（矩阵）；owner doc `docs-for-ai/03-modules/nop-credential.md`
- **结论摘要**：fail-closed 核心契约（启动期全路径抛错、无本地降级、运行期零托管端调用、单 provider 结构性执法、残余列表只解不加密）**全部在 live code 成立**，未发现 P0/P1。发现 1 个 P2（active-key-id 跨 provider 配置命名空间静默忽略，迁移切换路径契约漂移）与 4 个 P3。

---

## 一、对抗用例结论表

| # | 对抗用例 | 结论 | 证据（file:line） |
|---|---|---|---|
| 1a | 启动期：托管端不可达 → 启动失败 | **探查到（防护成立）** | `VaultCredentialKeyProvider.java:256-261`（`catch (Exception e)` → `ERR_CREDENTIAL_VAULT_UNREACHABLE` 包装抛出，init 无吞异常）；挂起场景受 `HttpClientConfig` 缺省 read-timeout 30s / connect-timeout 10s 约束（`nop-http-api/.../HttpClientConfig.java:35-37`、`JdkHttpClient.java:207-211`），超时异常落入同一 catch → UNREACHABLE |
| 1b | 启动期：401/403 → 启动失败 | **探查到（防护成立）** | `VaultCredentialKeyProvider.java:264-267`（`ERR_CREDENTIAL_VAULT_AUTH_FAILED`） |
| 1c | 启动期：404（keyId 缺失）→ 启动失败 | **探查到（防护成立）** | `VaultCredentialKeyProvider.java:268-272`（`ERR_CREDENTIAL_VAULT_KEY_NOT_FOUND`） |
| 1d | 启动期：其他非 2xx → 启动失败 | **探查到（防护成立）** | `VaultCredentialKeyProvider.java:273-277`（`ERR_CREDENTIAL_VAULT_READ_FAILED`；3xx 亦落入 `status >= 300` 分支） |
| 1e | 启动期：材料非法（空 body/坏 JSON/缺嵌套 data/passphrase 非字符串或空）→ 启动失败 | **探查到（防护成立）** | `VaultCredentialKeyProvider.java:279-304`；`JSON.parse` 的 `catch (RuntimeException) → material = null`（:290-292）随后必抛 `ERR_CREDENTIAL_VAULT_MATERIAL_INVALID`（:294-298），吞异常不产生放行路径 |
| 1f | 启动期：master-keys 残留（来源混合）→ 启动失败 | **探查到（防护成立）** | `VaultCredentialKeyProvider.java:153-156`（`hasNonBlankEntry(masterKeys)` → `ERR_CREDENTIAL_VAULT_MASTER_KEYS_RESIDUAL`）；空白条目列表视为已清空（`hasNonBlankEntry` :308-318）——方向安全（无材料存在即无混合），与 local 实现对空条目抛 ENTRY_INVALID 有轻微不对称，不构成风险 |
| 1g | 启动期：迁移残余含 active key → 启动失败 | **探查到（防护成立）** | `VaultCredentialKeyProvider.java:205-208`（`ERR_CREDENTIAL_VAULT_MIGRATION_KEY_ACTIVE`，且注释 :203-204 说明"含 active 检查先于 keyId 冲突检查"防吞更具体错误）；残余 keyId 与 Vault key 冲突 → `:209-212` MIGRATION_KEY_INVALID；active 指向残余-only key → `:186-190` UNKNOWN_ACTIVE_KEY（active 校验发生在残余并入 `built` **之前**，残余 key 结构性不可能成为 active） |
| 1h | 启动期：init 吞异常检查（catch-continue / 引用 Default 实现回退） | **未探查到（无该缺陷）** | `VaultCredentialKeyProvider.java` 全文唯一 `catch` 是 1e 的 JSON 解析（null 化后必抛）与 1a 的传输异常（包装重抛）；无任何 `DefaultCredentialKeyProvider` 引用（测试 `TestVaultCredentialKeyProvider.java:43-46` 注记亦固化此结构事实） |
| 2 | KMS 配置下运行期故障不回退本地（getKey 路径无降级） | **探查到（防护成立）** | `VaultCredentialKeyProvider.java:229-237`：getKey 纯内存 map 查找，miss 即抛 `ERR_CREDENTIAL_VAULT_UNKNOWN_KEY_ID`，无 catch、无 fallback 构造；bean 级回退结构性不可能——default bean 的排除发生在**定义评估期**（missing-bean 条件，`BeanConditionEvaluator.java:271-278`），vault bean 已 enabled 后其 @PostConstruct 抛错只会使容器启动失败，容器不存在"构造失败后重新启用 default bean"的路径 |
| 3 | 运行期零托管端调用（getKey 仅查内存 map，材料全量缓存） | **探查到（防护成立）** | init 期 for 循环对**全部**配置映射逐 keyId `fetchMaterial`（`VaultCredentialKeyProvider.java:176-181`）+ 残余 key 并入（:213），`keyMap` 一次性构建后 unmodifiable（:219-220）；`httpClient` 字段除 `fetchMaterial`（仅被 init 调用，:179）外无任何使用点；`TestVaultKeyProviderWiring.java:159-164` 固化运行期未知 keyId 语义 |
| 4 | 同名 bean 门控覆盖优先级（无双 provider） | **探查到（防护成立）** | 门控：`app-kms-vault.beans.xml:29-33` 定义**同名** bean id `nopCredentialKeyProvider` + `<if-property name="nop.credential.key-provider" value="vault"/>`；框架侧机制锚点：(a) `BeanContainerBuilder.normalizeDefaultBean`（`nop-ioc/.../BeanContainerBuilder.java:260-277`）把 `ioc:default` bean id 改写为 `$DEFAULT$nopCredentialKeyProvider` + name `nopCredentialKeyProvider` + `missing-bean(nopCredentialKeyProvider)`；(b) `BeanConditionEvaluator.checkProperty`（`BeanConditionEvaluator.java:324-343`，:342 `var.equals(value)` **精确等值**）；(c) vault bean enabled 时 default 的 missing-bean 命中 → disabled（:271-278），反之 vault disabled 时 default 兜底 enabled；(d) 若两者同名同时 enabled，`addEnabled` 抛 `ERR_IOC_DUPLICATE_BEAN_DEFINITION`（:153-158）——双 provider 被结构性执法禁止；全仓仅存在两个 `ICredentialKeyProvider` 实现类与两个同名 bean 定义（`credential-defaults.beans.xml:15-16`、`app-kms-vault.beans.xml:29`），无第二个独立 bean id；端到端测试 `TestVaultKeyProviderWiring.java:126-135` |
| 5 | default-bean 缺失守卫（配置 vault 但模块未部署 → 启动失败；含大小写/空白） | **探查到（防护成立）** | `DefaultCredentialKeyProvider.java:86-92`：`!StringHelper.isEmpty(keyProvider) && !"local".equals(keyProvider)` → master-keys 非空先抛 `ERR_CREDENTIAL_MASTER_KEYS_RESIDUAL`（:87-89，使模块缺失场景下混合检查也可达），否则抛 `ERR_CREDENTIAL_KEY_PROVIDER_MODULE_MISSING`；与门控侧匹配逻辑一致性：if-property 用 `var.equals("vault")`（精确），守卫用 `!"local".equals(keyProvider)`（精确）——大小写（`Vault`）或空白（` vault`）不匹配时 vault bean 不注册 → default 兜底构造 → 守卫抛错，**仍 fail-closed**（仅错误信号误导，见 D3-03）；`StringHelper.isEmpty` 为 `str == null || str.length() <= 0` 不 trim（`CharSequenceHelper.java:30-32`），纯空白值也落入守卫拒绝；测试 `TestKeyProviderModuleMissingGuard.java:60-70`、`TestKeyProviderGuardMasterKeysResidual.java:50-58`；容器缺省启动模式 DEFAULT 下 singleton 全量急切构造（`BeanContainerImpl.java:81,454-459`），守卫异常即应用启动失败 |
| 6 | 迁移残余列表只解不加密 + 每次启动 WARN 审计 | **探查到（防护成立）** | 只解不加密的三重结构保证：(a) active 候选集是残余并入**前**的 `built`（仅 Vault key，`VaultCredentialKeyProvider.java:184-190`），残余 key 不进 active 候选；(b) 加密唯一入口 `CredentialCipher.encrypt()` 用 `keyProvider.getActiveKeyId()`（`CredentialCipher.java:78-80`），全仓无任何调用方以外部输入 keyId 走两参 `encrypt(plainJson, keyId)`（grep 全部调用点均为 active-key 重载：`CredentialProviderImpl.java:321`、`NopCredentialBizModel.java:260,306,606`）；(c) 解密路由用 `getKeyIds().contains(keyId)`（`CredentialCipher.java:114`）而 `keyIds` 含残余 key（`VaultCredentialKeyProvider.java:220`）→ 残余可解不可加密。WARN 审计：`VaultCredentialKeyProvider.java:214-215` 每次启动逐残余 keyId 输出 WARN，测试 `TestVaultCredentialKeyProvider.java:406-427`（含"空列表不得 WARN"反向断言） |
| 7 | reencryptAll keyset 翻页完备性（无漏行/无死循环/确定性排序/游标推进） | **探查到（防护成立，附边界注记）** | `NopCredentialBizModel.java:588-621`：强制 `orderBy credentialId asc`（`buildReencryptQuery` :632 `addOrderField("credentialId", false)`，`QueryBean.java:432-436` 第二参 desc=false → 升序，PK 确定性排序）；游标 = 本页末条 `orm_idString()`（:618-620），平台 keyset 语义经 `OrmEntityDao.findPageByQuery → findNext(query)`（`OrmEntityDao.java:614-624`）→ `IEntityDao.findNext(QueryBean)`（`IEntityDao.java:270-277`，按 orderBy 字段值构造"之后"查询）；终止条件 `page.size() == reencryptPageSize`（:621）——整除末页会多查一页空页后终止，无死循环；处理中只改 `data` 不改 id（:607-608），游标锚点稳定；并发删除游标行时 `load` 返回懒代理（`OrmSessionImpl.java:424-426`），PK 值仍可用于 keyset SQL，最坏重扫幂等跳过。边界注记：(i) `reencryptPageSize` 可配 0 → 空页死循环（D3-02）；(ii) 无漏行的前提"处理中新写入行用 active key"成立（新写入走 encrypt→active key，未被游标扫到也已是新 key） |

补充核对（未列入上表的探查）：

- **vault 模块部署但无 raw HTTP client 实现模块**：`@Inject IHttpClient`（`VaultCredentialKeyProvider.java:103-104`）按类型解析，`nopHttpClient` 是条件 default bean（`http-api.beans.xml:8-18`，`on-bean nopRawHttpClient`），无实现模块时被禁用 → 类型解析抛 `ERR_IOC_UNKNOWN_BEAN_FOR_TYPE`（`BeanContainerImpl.java:310-319`）→ 启动失败（fail-closed，符合设计 §4.1 结论 5 的运行时约束，owner doc :237 已记载）。
- **app beans 自动装载路径**：`app-kms-vault.beans.xml` 位于共享模块命名空间 `/nop/credential/beans/` 且 app- 前缀 → `AppBeanContainerLoader.getModuleAppResources` + `isAppBeans`（`AppBeanContainerLoader.java:254-284`）自动装载，不依赖 import/autoconfig；`/nop/credential/_module` 模块标记存在（nop-credential-dao 提供）。注意装载受 `nop.ioc.app-beans-file.skip-pattern` / `pattern` / `nop.ioc.app-beans-file.enabled=false` 等全局开关影响（:128-142）——这些是平台级部署开关，被关闭时等价于模块未部署 → 走 default-bean 守卫 fail-closed，不产生静默 local 降级。
- **ALL_LAZY 启动模式**（`BeanContainerStartMode`）：显式配置时 bean 延迟到首次访问构造，Vault 故障从"启动失败"移到"首次取用失败"——仍 fail-closed、无本地回退，但偏离设计 §4.1 结论 3 "全部发生在启动期"的字面。属部署模式选择，非代码缺陷；生产默认（DEFAULT 模式）下急切构造成立。
- **kms-vault 模块依赖边界**：compile 依赖仅 `nop-credential-api` + `nop-http-api`（`pom.xml:19-30`），零 Vault SDK、不依赖 service——与设计 §4.1 结论 5 + W10-impl 裁定标注一致。

---

## 二、回归锚点结论（§4.5 兼容小节对抗复核）

| §4.5 条目 | 结论 | 证据 |
|---|---|---|
| 缺省部署零变化（未引入 KMS 模块 / key-provider 缺省 local → `DefaultCredentialKeyProvider` 行为与一期完全一致，`credential-defaults.beans.xml` 不动） | **成立** | `credential-defaults.beans.xml:15-16` 未改动（default bean 定义与一期同构）；守卫在 `keyProvider` 为空或 `"local"` 时零触发（`DefaultCredentialKeyProvider.java:86` 条件短路）；if-property 未命中时 vault bean 不注册（`checkProperty` 空值 → false，`BeanConditionEvaluator.java:331-337`）；KMS 模块在 classpath 而 key-provider 未配置时 local 行为不变有专项测试（`TestLocalKeyProviderWithKmsModulePresent.java:38-55`，含 cv1 前缀断言） |
| `cv1:` 密文格式不变（归 D5，此处给 KMS 装配面结论） | **KMS 装配面无触碰** | Vault 实现按一期同构方式构造 `AESTextCipher`（`VaultCredentialKeyProvider.java:180,213`），keyId 命名空间与 keyId→材料路由复用 `CredentialCipher` 原逻辑（`CredentialCipher.java:62-123` 未改）；材料逐字等价有容器级 round-trip 证明（`TestVaultKeyProviderWiring.java:138-156`） |
| 增量声明（新增配置项/可选模块/覆盖 bean 全为加法） | **成立** | 新增 `nop.credential.vault.*` 配置注入（:109-136）、独立模块、同名条件 bean；唯一对一期文件的语义增量是 `DefaultCredentialKeyProvider` 的 init 守卫（:76-92）——local/未配置路径零行为差（条件不触发），非 local 路径为二期新场景，不构成一期回归 |
| 一期消费链零回归（KMS 列） | **成立（装配面）** | keyProvider 经 `NopCredentialBizModel:108` `@Inject ICredentialKeyProvider` 按类型解析，SPI 消费面零变更；密文格式兼容归 D5 |

owner doc `docs-for-ai/03-modules/nop-credential.md` KMS 章节（:90-119, :237, :323-324）与 live code 逐条一致（配置项表、装配语义、守卫错误码、迁移步骤、分页完备性注记），未发现文档漂移。

---

## 三、Findings

### [D3-01] Vault provider 静默忽略 `nop.credential.active-key-id`，迁移切换路径可无声改变 active key

**文件路径：行号**：`nop-credential/nop-credential-kms-vault/src/main/java/io/nop/credential/kms/vault/VaultCredentialKeyProvider.java:124-127`（对照 `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/DefaultCredentialKeyProvider.java:66-69`）

**证据代码片段**：

```java
// VaultCredentialKeyProvider.java:124-127 —— vault 只读自有命名空间
@InjectValue("@cfg:nop.credential.vault.active-key-id|")
public void setActiveKeyId(String activeKeyId) {
    this.activeKeyId = activeKeyId;
}

// DefaultCredentialKeyProvider.java:66-69 —— local 读共享命名空间
@InjectValue("@cfg:nop.credential.active-key-id|")
public void setActiveKeyId(String activeKeyId) {
    this.activeKeyId = activeKeyId;
}
```

**严重程度**：P2

**现状**：active key 配置存在两个命名空间：一期共享的 `nop.credential.active-key-id`（local 实现读取）与二期的 `nop.credential.vault.active-key-id`（vault 实现读取）。vault 模式下前者被完全静默忽略，无校验、无 WARN。active key 缺省解析规则两者相同（取列表/映射首项，`VaultCredentialKeyProvider.java:184-185`）。

**风险**：设计 §4.3 迁移路径第 1 步的硬契约是"切换 provider 时 active keyId **及其材料不变**"（切换与轮换解耦，保证滚动发布窗口密钥能力对称）。迁移场景：一期 local 配置了 `nop.credential.active-key-id=keyB`（非首项）+ `master-keys=keyA:...,keyB:...`；切 vault 时操作员导入全部 key 并配置 `vault.keys=keyA:路径A,keyB:路径B`，却未意识到需在 vault 命名空间**重新**设置 active-key-id → vault active 静默变为 `keyA`（映射首项）。后果：(a) 违反"切换时 active 不变"的解耦不变式——滚动窗口内新旧副本写出的密文 keyId 不一致（均可解，无数据损坏，但关窗统计与运维预期漂移）；(b) 违背 fail-closed 设计哲学中的"配置矛盾显式暴露"原则——这是一条**静默**的配置语义分叉，操作员得不到任何信号。`nop.credential.active-key-id` 残留值亦无任何检查（对比 master-keys 残留有显式拒绝）。

**建议**：在 `VaultCredentialKeyProvider.init` 的 active key 解析处增加交叉校验：读取 `nop.credential.active-key-id`，若非空且与最终解析的 active keyId 不一致 → 抛 `NopException`（fail-closed，错误信息提示两处配置冲突）；或至少输出 WARN 审计。同时在 owner doc 迁移步骤中显式列出"重设 active-key-id 到 vault 命名空间"检查项。

**信心水平**：high（live code 双命名空间注入不对称是结构事实；风险场景依赖运维沿一期配置习惯迁移，属高概率真实路径）。

**误报排除**：已核对 `CredentialConfigs.java` 无 `nop.credential.active-key-id` 的 vault 侧别名/映射机制；已核对 `@cfg:` 无大小写或前缀归一化逻辑使两命名空间等价；非 Nop 平台误报（不是生成代码、不是 IoC 惯例差异，而是模块自身配置面设计缺口）。

---

### [D3-02] `reencryptPageSize` 可配置为 0 导致 reencryptAll 空页死循环

**文件路径：行号**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:549-552, 586-621`

**证据代码片段**：

```java
@InjectValue("@cfg:nop.credential.reencrypt-page-size|1000")
public void setReencryptPageSize(int reencryptPageSize) {
    this.reencryptPageSize = reencryptPageSize;
}
...
String cursor = null;
List<NopCredential> page;
do {
    QueryBean query = buildReencryptQuery();   // :633 query.setLimit(reencryptPageSize)
    query.setCursor(cursor);
    page = dao.findPageByQuery(query);
    ...
} while (page.size() == reencryptPageSize);    // :621
```

**严重程度**：P3

**现状**：页大小直接取配置注入值，无下界校验。配置 `nop.credential.reencrypt-page-size=0` 时 `limit=0` → 每页查询返回空列表 → `0 == 0` 恒真 → 无限循环（每轮一次 DB 查询的忙循环），reencryptAll 永不返回。

**风险**：KMS 迁移关窗依赖 reencryptAll 执行完毕（设计 §4.3 关窗前置条件）；死循环使其无法完成且无明显报错（可用性问题，非数据安全问题——不产生错误密文、不降级）。负值配置会因非法 LIMIT SQL 报错终止（fail-fast），仅 0 值落入静默死循环。属运维误配置触发（配置项无文档化的合法值域）。

**建议**：注入处钳制下界（`Math.max(1, reencryptPageSize)`）或 init 期校验 `> 0` 抛配置错误；owner doc 补充值域说明。

**信心水平**：high（循环与注入逻辑可直接推出；`limit=0` 返回空页为 SQL LIMIT 语义）。

**误报排除**：非 Nop 平台自动分页机制（是本类手写 do-while 循环）；设计文档的"页大小缺省 1000"未禁止自定义值，0 是可注入的合法 int。

---

### [D3-03] key-provider 值大小写/空白不匹配时错误信号误导（报"模块缺失"而非配置值错误）

**文件路径：行号**：`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanConditionEvaluator.java:339-342`；`nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/DefaultCredentialKeyProvider.java:86-92`

**证据代码片段**：

```java
// BeanConditionEvaluator.java:342 —— 门控精确等值，不 trim、不忽略大小写
return var.equals(value);

// DefaultCredentialKeyProvider.java:86-91 —— 守卫同为精确等值
if (!StringHelper.isEmpty(keyProvider) && !"local".equals(keyProvider)) {
    ...
    throw new NopException(CredentialErrors.ERR_CREDENTIAL_KEY_PROVIDER_MODULE_MISSING)
            .param(CredentialErrors.ARG_KEY_PROVIDER, keyProvider);
}
```

**严重程度**：P3

**现状**：配置 `nop.credential.key-provider=Vault` / `" vault"` / `"vault "` 时：if-property 精确匹配失败 → vault bean 不注册 → default bean 兜底 → 守卫抛 `key-provider-module-missing`。**fail-closed 语义保持**（不会静默跑 local），但错误归因错误：模块实际已部署，操作员按错误提示去排查部署/依赖不会解决问题。`StringHelper.isEmpty` 不 trim（`CharSequenceHelper.java:30-32`），纯空白值同样落入此路径。

**风险**：诊断成本与误处置（反复检查模块部署）。安全面无缺口（所有不匹配值一律拒绝启动）。两侧匹配逻辑（`equals("vault")` 与 `!"local".equals(...)`）语义一致，不存在一侧 trim 一侧不 trim 的分裂放行窗口。

**建议**：守卫抛错时区分场景（如附 `key-provider` 原始值 + 提示"检查取值拼写/大小写/空白与模块标识是否一致"）；或在注入处统一 trim + 小写归一（需两侧同步改，保持一致性）。最低成本：owner doc 配置表标注取值大小写敏感。

**信心水平**：high（匹配与守卫逻辑均直读源码验证）。

**误报排除**：守卫行为本身是设计裁定的 W10 落地（非缺陷），本 finding 仅针对错误信息质量的运维成本，非 fail-closed 缺口。

---

### [D3-04] VaultCredentialKeyProvider 硬编码复制 KEY_ID_PATTERN，与 CredentialCipher.CV1_KEY_ID_PATTERN 双源维护

**文件路径：行号**：`nop-credential/nop-credential-kms-vault/src/main/java/io/nop/credential/kms/vault/VaultCredentialKeyProvider.java:61-62`（对照 `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/CredentialCipher.java:40`、`DefaultCredentialKeyProvider.java:38`）

**证据代码片段**：

```java
// VaultCredentialKeyProvider.java:61-62
/** 与一期 local 实现一致的 keyId 字符集约束（cv1: 按冒号 split 无歧义的前提）。 */
private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

// DefaultCredentialKeyProvider.java:38 —— 复用单一真相源
private static final Pattern KEY_ID_PATTERN = Pattern.compile(CredentialCipher.CV1_KEY_ID_PATTERN);
```

**严重程度**：P3

**现状**：keyId 字符集约束 `[A-Za-z0-9_-]+` 在 kms-vault 模块以字符串字面量复制，未复用 `CredentialCipher.CV1_KEY_ID_PATTERN` 常量。成因是依赖边界：kms-vault 刻意只依赖 `nop-credential-api`（`pom.xml:19-30`），而该常量定义在 service 模块的 `CredentialCipher`。

**风险**：`cv1:` 按冒号 split 的无歧义前提依赖该字符集不含冒号。若未来字符集演进（如一期侧放宽/收紧），vault 侧副本漂移 → KMS keyId 与 local keyId 校验规则分叉：vault 放行 local 拒绝（或反之）的 keyId 进入密文路由层产生解析不一致。当前两处字面量一致，无现行缺陷。

**建议**：把 `CV1_KEY_ID_PATTERN`（及 `MATERIAL_FIELD` 等跨模块契约常量）下沉到 `nop-credential-api`（如 `ICredentialKeyProvider` 伴生常量类），两侧统一引用；kms-vault 对 api 已有 compile 依赖，零新增依赖。

**信心水平**：high（双源字面量为结构事实）。

**误报排除**：非生成代码；非故意设计（注释自述"与一期 local 实现一致"表明意图是同一约束，复制只是依赖边界的权宜）。

---

### [D3-05] Vault 启动期读取未设置请求级超时，故障时限依赖全局 HTTP 客户端配置

**文件路径：行号**：`nop-credential/nop-credential-kms-vault/src/main/java/io/nop/credential/kms/vault/VaultCredentialKeyProvider.java:248-256`（对照 `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:207-211`）

**证据代码片段**：

```java
// VaultCredentialKeyProvider.java:249-256 —— 未设 request.setTimeout
HttpRequest request = new HttpRequest();
request.setMethod(HttpApiConstants.METHOD_GET);
request.url(address + "/v1/" + path);
request.header(HEADER_VAULT_TOKEN, token);

IHttpResponse response;
try {
    response = httpClient.fetch(request, null);

// JdkHttpClient.java:207-211 —— 超时回退链：request 级 → 全局 readTimeout
} else if (config.getReadTimeout() != null) {
    builder.timeout(config.getReadTimeout());
}
```

**严重程度**：P3

**现状**：启动期材料读取不设请求级 timeout，超时行为继承 `nop.http.client.read-timeout`（缺省 30s，`HttpClientConfig.java:35`）。若部署方将全局 read-timeout 置空（`nop.http.client.read-timeout` 显式配空 → `Duration` 转换为 null → JDK client 不设 request timeout），Vault 半开挂起（TCP 建立 but 无响应）时启动线程无限阻塞——不是启动失败，而是永不完成的启动。

**风险**：违反设计 §4.3 fail-closed 细则"托管端不可达 → 实现 bean 初始化抛错，应用启动失败"的**有界性**预期：挂起既不失败也不降级，K8s 类编排可经 liveness 探针兜底回收，但裸进程部署会无期限停在启动中。缺省配置（30s）下不触发；需要显式全局配置才暴露，且该配置影响平台全部出站 HTTP，非 vault 专属面。

**建议**：`fetchMaterial` 对 request 设置显式超时（如 `request.setTimeout(30_000)` 或引入 `nop.credential.vault.timeout` 配置，缺省 30s），使 KMS 启动期 fail-closed 的时间界限自持、不随全局客户端配置漂移。

**信心水平**：medium-high（超时回退链与缺省值已核实；"置空全局 read-timeout"是否为受支持的配置形态未逐一验证 Duration 转换路径，故不满 high）。

**误报排除**：缺省 30s 已覆盖绝大多数部署，非现行默认路径缺陷；属加固建议 + 边界配置下的可用性缺口，定级 P3 恰当。

---

## 四、检查范围（零发现区亦已覆盖）

**已读并逐行核对**：
- `nop-credential/nop-credential-kms-vault/src/main/java/io/nop/credential/kms/vault/VaultCredentialKeyProvider.java`（全文 319 行）
- `nop-credential/nop-credential-kms-vault/src/main/java/io/nop/credential/kms/vault/VaultCredentialErrors.java`（全文）
- `nop-credential/nop-credential-kms-vault/src/main/resources/_vfs/nop/credential/beans/app-kms-vault.beans.xml`（全文）
- `nop-credential/nop-credential-kms-vault/pom.xml`
- `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/DefaultCredentialKeyProvider.java`（全文 177 行）
- `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/CredentialCipher.java`（全文）
- `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/CredentialErrors.java`（错误码存在性核对）
- `nop-credential/nop-credential-service/src/main/java/io/nop/credential/config/CredentialConfigs.java`（全文）
- `nop-credential/nop-credential-service/src/main/resources/_vfs/nop/credential/beans/credential-defaults.beans.xml`、`app-service.beans.xml`（import 链核对）
- `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java`（reencryptAll 区段 :540-647 + 注入声明 :108）
- `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java`（encrypt 调用点 :321）
- 框架侧机制锚点：`nop-ioc/.../BeanConditionEvaluator.java`（全文）、`BeanContainerBuilder.java`（normalizeDefaultBean :257-296）、`AppBeanContainerLoader.java`（装载 + isAppBeans :76-142, 254-284）、`BeanContainerImpl.java`（getBeanByType/start 模式 :310-319, 454-459, :81）、`IocConstants.java`（DEFAULT_ID_PREFIX）、`nop-api-core/.../BeanContainerStartMode.java`、`CharSequenceHelper.java`（isEmpty 语义）
- HTTP 栈：`nop-http-api/.../IHttpClient.java`、`http-api.beans.xml`、`http-client-jdk.beans.xml`、`JdkHttpClient.java`（超时区段）、`HttpClientConfig.java`（缺省超时）
- 分页栈：`QueryBean.java`（addOrderField/setCursor）、`OrmEntityDao.java`（findPageByQuery/findNext/loadEntityById :257-261, 578-688）、`IEntityDao.java`（findNext(QueryBean) :270-277）、`OrmSessionImpl.java`（load 懒代理 :424-426）
- kms-vault 测试树（探查参考，非历史记录）：`TestVaultCredentialKeyProvider.java`（fail-closed 逐分支 + WARN 审计）、`TestVaultKeyProviderWiring.java`（端到端装配）、`TestKeyProviderModuleMissingGuard.java`、`TestKeyProviderGuardMasterKeysResidual.java`、`TestLocalKeyProviderWithKmsModulePresent.java`、`src/test/resources/application.yaml`
- 设计文档 `ai-dev/design/nop-credential/02-phase2-design.md`（§四 全部 + §4.5 + §二矩阵 + §八 W10 映射）；owner doc `docs-for-ai/03-modules/nop-credential.md`（KMS 相关区段一致性抽查）

**结论**：D3 维度 fail-closed 契约成立，无 P0/P1 发现；P2 × 1（D3-01）、P3 × 4（D3-02 ~ D3-05）。
