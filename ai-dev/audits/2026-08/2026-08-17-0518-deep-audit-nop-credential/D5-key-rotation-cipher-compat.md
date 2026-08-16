# D5 — 密钥轮换 / 多 key 并存 / 密文格式兼容（独立安全审计）

- **审计日期**：2026-08-17
- **审计维度**：D5（keyId 篡改 / 伪造格式 / 跨 key 重放、多 key 并存窗口、`cv1:` 与 `@sec:` 交叉污染）
- **审计对象**：live code @ worktree `nop-entropy-feat-credential-mfa-phase2`
- **回归基准**：`ai-dev/design/nop-credential/02-phase2-design.md` §四（4.1/4.3/4.5）、§二 矩阵；owner doc `docs-for-ai/03-modules/nop-credential.md`
- **审计模式**：只探查不修改；报告为唯一写入物

---

## 一、对抗用例结论表

| # | 对抗用例 | 结论 | 关键证据（file:line） |
|---|---|---|---|
| 1 | `cv1:{keyId}:` 解析 fail-closed（格式错误/未知 keyId/base64 非法/分隔符缺失/空 keyId） | **探查到（防护全部成立）** | `CredentialCipher.java:91-118` 逐分支核对：null/非 cv1 前缀 `:91`、缺分隔冒号或空 keyId（`colonIdx <= 0`）`:100`、非法 keyId 字符或空 payload `:109`、未知 keyId `:114`；内层 base64 非法/截断/GCM tag 失败在 `AESTextCipher.java:399-419`（严格 `decodeBase64` @ `StringHelper.java:1515-1521`、`data.length < ivLen` 截断检查 `:405`、`doFinal` AEAD 异常 `:414`）。测试覆盖：`TestCredentialCipher.java:198-250`（6 个 fail-closed 用例，16/16 绿） |
| 2 | 多 key 解密（keyId 路由）+ active key 加密（新写入恒用 active） | **探查到（成立）** | encrypt 无参便捷法每次现取 `keyProvider.getActiveKeyId()`（`CredentialCipher.java:78-80`）；生产全部写路径均用无参 encrypt：`NopCredentialBizModel.java:260`（创建）、`:306`（更新）、`CredentialProviderImpl.java:321`（OAuth 回写）、`NopCredentialBizModel.java:606`（reencryptAll）；decrypt 按密文 keyId 路由（`getKeyIds().contains → getKey`，`CredentialCipher.java:114-122`）。测试：`TestCredentialCipher.java:157-176`（multiKeyDecryptAfterRotation）、`TestNopCredentialReencryptPagination.java:117-129`（运行时切 active 后 live 验证） |
| 3 | keyId 篡改攻击（密文 A 的 keyId 段替换为 keyId B） | **探查到（fail-closed 成立，为间接绑定）** | keyId 段被改 ⇒ 路由至 keyB 的 passphrase ⇒ PBKDF2 派生密钥不同 ⇒ GCM tag 验证必败 ⇒ `NopException`（`AESTextCipher.java:414` doFinal 抛 AEADBadTagException → `:417-419` adapt）。**无显式 AAD/HMAC 绑定 keyId**（GCM 加密输入仅明文+IV，`AESTextCipher.java:351-359`）；唯一逃逸 = 两个 keyId 配置相同 passphrase（明文不变，无保密性破坏）→ 记录为 [D5-04] |
| 4 | 跨 key 重放（同一明文经不同 keyId 加密的密文互不可替换） | **探查到（不可利用，与 #3 同根）** | 同明文经 keyA/keyB 的两条密文互换后解密仍得同一明文（keyId 路由各自成立，无语义破坏）；不同明文的密文行级置换（凭证 X 的 data 换成凭证 Y 的密文）在无行绑定 AAD 下可行，但威胁前提 = 攻击者已持 DB 写权限（等同可删改任意数据），属加密 at-rest 普遍接受边界 → 并入 [D5-04] 记录 |
| 5 | `reencryptAll` 全量迁移语义（active 重加密/幂等跳过/逐条提交可重跑/失败行处理） | **探查到（主体成立，两处偏差）** | active 重加密 ✓（`:606` 无参 encrypt）；幂等跳过 ✓（`:600` `activeKeyId.equals(currentKeyId)`）；确定性排序 + keyset 游标翻页 ✓（`:629-635` orderBy credentialId + cursor，测试 7 条/页 3 全绿）；失败 fail-closed ✓（`:610-612` REENCRYPT_FAILED 抛错中止）。偏差一：「逐条提交可重跑」在生产 GraphQL mutation 路径实为**整体单事务**（→ [D5-01]）；偏差二：软删除行与非 cv1 前缀行不在覆盖面（→ [D5-02]/[D5-03]） |
| 6 | `@sec:` 配置加密共存（与 cv1: 无交叉污染） | **探查到（隔离成立）** | `DefaultConfigValueEnhancer.java:59/67/69` 仅识别 `@switch:`/`@sec:`/`${` 三种前缀；`cv1:` 密文作为 config 值不以 `@sec:` 开头且 base64 字母表无 `$` → 按字面量通过，不触发解密/表达式；凭证 data 路径（saveCredential/decryptToData）不调用 config enhancer；`@sec:...` 值误存 data 列 → `CredentialCipher.decrypt` 抛 INVALID_CIPHERTEXT_FORMAT fail-closed（`:91`）；`@sec:cv1:...` 误配 → 严格 decodeBase64 拒含 `:` 字符 → 启动期显式报错。AESTextCipher 类复用但实例独立（config 侧 key = `nop.config.encrypt-key` @ `ConfigStarter.java:457-471`；credential 侧 = passphrase） |
| 7 | keyId 模式注入（`[A-Za-z0-9_-]+` 边界） | **探查到（防护成立）** | 全串 `matches()` 四处一致使用：`CredentialCipher.java:63`（encrypt）、`:109`（decrypt）、`DefaultCredentialKeyProvider.java:116`、`VaultCredentialKeyProvider.java:168/:197`；keyId 含 `:`/空格/正则元字符（`.`）/空串均拒（测试 `TestCredentialCipher.java:269-279`、`TestDefaultCredentialKeyProvider` initThrowsForInvalidKeyIdWithSpace/Dot）；字符类 + 简单 `+` 量词无回溯陷阱（无 ReDoS）；无长度上限——超长 keyId 经 DB 列宽溢出显式报错，注册/解析两侧对称无险。轻微观察：`VaultCredentialKeyProvider.java:62` 硬编码重复定义同串 pattern（未引用 `CredentialCipher.CV1_KEY_ID_PATTERN`），DRY 缺口 |

---

## 二、回归锚点结论

### 锚点 1：「cv1:{keyId} 密文格式兼容」（设计 §二 矩阵行 / §4.5）— **PASS**

- **一期密文在新代码下可解**：`CredentialCipher.java` 自一期提交 `dfb4f7ed7`（plan-2026-08-12-0615 新增模块骨架与 cv1 密码学层）以来**零修改**（`git log --follow` 仅该一个提交触及此文件）——解析规则、四段格式 `cv1:{keyId}:v1:{base64}`、KEY_ID_PATTERN 与一期完全同一实现；live 测试全绿（`TestCredentialCipher` 16/16，含一期格式的 round-trip/多 key 路由/篡改 fail-closed）。
- **新密文格式演进不破坏**：`cv1:` 前缀分派机制（`startsWith(CV1_MARKER)`）天然支持未来 `cv2:` 演进（`AESTextCipher` 的 `V1_MARKER` 同构先例，`AESTextCipher.java:54-59`）；本期无格式变更发生。
- **KMS 集成只换材料来源**：`VaultCredentialKeyProvider` 物化为同构 `AESTextCipher` 集合（`VaultCredentialKeyProvider.java:177-181`），密文层零感知——与设计 §4.5「keyId 语义、包装格式、解析规则零变更」一致。
- 弱注记：[D5-05]（内层 v1: 前缀未强制）是格式契约的一个宽松点，不影响一期密文可解性，不构成锚点 FAIL。

### 锚点 2：「@sec: 配置加密共存不受影响」（附加锚点）— **PASS**

- `DefaultConfigValueEnhancer`（`nop-core-framework/nop-config/.../DefaultConfigValueEnhancer.java:66-68`）对 `@sec:` 前缀剥离后交 `cipher.decrypt`；识别逻辑与 `cv1:` 完全隔离（互不解析对方前缀）。
- `AESTextCipher` 复用路径无交叉污染：config 侧实例经 `ConfigStarter.java:457-471` 用 `nop.config.encrypt-key`/salt 构造，credential 侧经 provider 用 passphrase 构造——不同实例、不同密钥、不同输出前缀（`@sec:v1:...` vs `cv1:{keyId}:v1:...`）。
- 双向误用失败模式均显式 fail-closed：cv1 值入 config → 字面量透传（无副作用）；`@sec:` 值入 data 列 → `ERR_CREDENTIAL_INVALID_CIPHERTEXT_FORMAT`；`@sec:cv1:...` → 严格 base64 解码失败 → 启动报错。`StartupInfoLogger.java:128-129` 仅对 `@sec:` 做日志脱敏，不涉解密。
- 实测佐证：`TestConfigValueEnhancerEncryptedValue`（nop-config）与 `TestLocalKeyProviderWithKmsModulePresent`/`TestVaultKeyProviderWiring`（credential-kms-vault）两侧测试全绿，各路径独立。

---

## 三、Findings

### [D5-01] reencryptAll「逐条提交可重跑」声明与生产路径整体单事务语义不符（长事务 + 断点续跑预期落空）

- **文件**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:571-623`（声明 @564）；事务链证据 `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLTransactionOperationInvoker.java:26-34`、`nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml:40-48`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/utils/TransactionalFunctionInvoker.java:20-23`
- **证据**（reencryptAll 循环逐条 update，javadoc 声明逐条提交）：

```java
// NopCredentialBizModel.java:563-565
 * <p>幂等：若凭证密文中的 keyId 已等于 active keyId，则跳过（避免不必要的重新加密）。
 * 逐条提交可重跑（单条失败抛错中止，重跑从断点语义继续——已处理条目幂等跳过）。
 * 失败 fail-closed（解密/加密异常抛出，不静默跳过）。

// :604-613  循环体内逐条直接 update（无独立事务边界）
    try {
        String json = credentialCipher.decrypt(data);
        String newData = credentialCipher.encrypt(json);
        entity.setData(newData);
        dao.updateEntityDirectly(entity);
        updated++;
    } catch (NopException e) { throw new NopException(ERR_CREDENTIAL_REENCRYPT_FAILED, e) ... }

// GraphQLTransactionOperationInvoker.java:29-33  mutation 根操作整体进入事务 invoker
if (context.getOperation().getOperationType() == GraphQLOperationType.mutation) {
    return transactionalInvoker.invokeAsync(task, request);   // TransactionalFunctionInvoker, propagation=REQUIRED
```

装配链闭合：`biz-defaults.beans.xml:40-48` 将 `nopGraphQLTransactionOperationInvoker` 接到 `nopTransactionalFunctionInvoker`（`orm-defaults.beans.xml:76` 无条件注册，`propagation = REQUIRED`）。生产运维通道（GraphQL/REST mutation，含 `TestNopCredentialReencryptPagination` 走的 `mutation { NopCredential__reencryptAll }`）整个翻页循环运行在**单一 REQUIRED 事务**中。
- **严重程度**：P2
- **现状**：单条失败 → 整体回滚 → 重跑从零开始。「逐条提交」仅在裸 bean 方法直调（无 GraphQL/invoker 包装）时成立；设计 §4.3 与 owner doc 均以「逐条提交可重跑」为 reencryptAll 锚点语义。
- **风险**：正确性无损（幂等保证重跑安全、回滚一致性由 DB 保证），但（1）大凭证量迁移（数十万行 × PBKDF2 双向运算）形成长事务——行锁随循环逐步累积直至提交、undo/回滚段膨胀、与并发写（saveCredential/惰性刷新）锁冲突窗口拉长；（2）「断点续跑」预期落空——失败后重跑是全量重来而非断点继续，超批量场景失败成本放大；（3）javadoc/设计/owner doc 三处声明与生产实际行为漂移。
- **建议**：二选一并落文档——(a) 显式接受整体事务语义，修订 javadoc/owner doc 为「单事务原子完成，失败整体回滚，幂等可重跑」，并在 owner doc 标注大表迁移的锁窗口运维提示；(b) 若要兑现逐条提交，将每页/每条重加密包 `REQUIRES_NEW` 独立事务（游标 + 幂等跳过已支持断点续跑），注意与 KMS 关窗判定的配合（重跑至返回 0）。
- **信心水平**：高（事务装配链三处源码 + beans 证据闭合；测试通过不能证伪——单事务下功能等价）。
- **误报排除**：已排除「注解路径无事务」的可能——`collectDecorator(IFunctionModel)` 确需 `@Transactional` 注解（`TransactionActionDecoratorCollector.java:30-36`），但 GraphQL 引擎层 invoker 链与注解无关地包裹了所有 mutation 根操作；`BizActionInvoker.invokeActionSync:48` 的非 GraphQL 直调路径同样包 REQUIRED 事务。

### [D5-02] 软删除行不在 reencryptAll 覆盖面：旧 key 退役后软删除凭证明文永久不可恢复（关窗完备性盲区）

- **文件**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:629-635`
- **证据**：

```java
// :629-635  分页查询强制 delFlag=0
QueryBean buildReencryptQuery() {
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.eq("delFlag", 0));
    query.addOrderField("credentialId", false);
    query.setLimit(reencryptPageSize);
    return query;
}
```

软删除行（delFlag=1）的 `data` 列仍携带 `cv1:{oldKeyId}:...` 密文，但被查询过滤排除，永远不会被重加密到 active key。
- **严重程度**：P3
- **现状**：KMS 迁移关窗判定（设计 §4.3「全部密文的 keyId 属于新 key 集合」→ 执行 reencryptAll 至完毕 → 退役旧 key）对软删除行不成立；设计文档与 owner doc 均未声明此取舍。
- **风险**：关窗退役旧 key 后，软删除行发生 undelete（或任何后续解密读取）时 `getKey(oldKeyId)` 抛 `ERR_CREDENTIAL_UNKNOWN_KEY_ID`——fail-closed 显式报错而非数据损坏，但明文**不可恢复**（除非重新导入旧 key 材料）。若"软删除 → 物理清理"周期跨越一次密钥轮换窗口，恢复诉求即触发。
- **建议**：三选一——(a) 在设计/owner doc 显式声明「软删除行不参与轮换，旧 key 退役即随密文失效」（数据生命周期取舍入档）；(b) reencryptAll 查询放宽为含软删除行（密文仍在、解密重写无归属语义障碍）；(c) 保留过滤但提供运维核查 SQL（统计 delFlag=1 且 keyId ∉ 新 key 集的行数）作为关窗前检查项。
- **信心水平**：高（过滤条件直接可见；undelete 行为依平台通用软删除机制推断，无 credential 专属拦截证据）。
- **误报排除**：已排除「软删除行密文会被其他路径重写」——saveCredential 更新路径仅作用于未删除行（可见性/存在性校验前置），惰性刷新同理；grep 全模块无其他 `setData` 生产写点（仅 :260/:306/:321/:607）。

### [D5-03] reencryptAll 对非 cv1 前缀 data 行静默跳过：与解密失败 fail-closed 双标，完备性无信号

- **文件**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:593-597`
- **证据**：

```java
// :593-597  非 cv1 前缀的 data 被静默 continue（不计数、不告警、不抛错）
for (NopCredential entity : page) {
    String data = entity.getData();
    if (StringHelper.isEmpty(data) || !data.startsWith(CredentialCipher.CV1_MARKER)) {
        continue;
    }
```

对比同函数内对「cv1: 前缀但解密失败」的行抛 `ERR_CREDENTIAL_REENCRYPT_FAILED` 中止（`:610-613`）。
- **严重程度**：P3
- **现状**：正常数据流不会产生非 cv1 的非空 data（saveCredential 恒写 cv1:），此类行只能来自外部 DB 直写/篡改/历史残留；它们被轮换过程静默忽略。
- **风险**：与 [D5-02] 叠加构成「关窗完备性」第二盲区——运维以 reencryptAll 执行完毕 + 幂等返回 0 作为关窗依据时，非 cv1 行不产生任何信号；旧 key 退役后这些行本就不可解（无新增损害），但「密文 keyId 全部属于新 key 集」的关窗前置条件对它们是未验证状态，且静默跳过违背本模块自己的 fail-closed 声明（`:565`「失败 fail-closed…不静默跳过」仅覆盖解密失败分支）。
- **建议**：最低成本——对 `data` 非空但不以 `cv1:` 开头的行计数并在返回值/日志中显式报告（如返回结构含 `skippedMalformed` 或 WARN 日志列出 credentialId）；或与解密失败同口径抛错（激进，可能阻塞含脏数据的存量库迁移——需权衡，推荐前者）。
- **信心水平**：高（分支行为直接可见，测试仅覆盖空 data 与正常 cv1 路径）。
- **误报排除**：已确认 `StringHelper.isEmpty(data)` 的空行跳过是合理语义（无密文可轮换），本 finding 仅针对**非空且非 cv1** 的行；`extractKeyId` 返回 null 的格式坏行（cv1: 前缀但无冒号）不会走此 continue——它们落入 decrypt 抛错分支，与本文不重叠。

### [D5-04] cv1 密文未将 keyId/credentialId 纳入 AEAD 绑定：keyId 段篡改防护依赖派生密钥分离的间接保证，同材料异 keyId 改写与行级密文置换无检测

- **文件**：`nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java:351-363`（v1 加密输入仅明文）；`nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/CredentialCipher.java:62-69`（包装层不附加认证数据）
- **证据**：

```java
// AESTextCipher.java:351-359  GCM 加密输入 = key + 随机IV + 明文，无 AAD
String encryptVersioned(String text) {
    byte[] iv = newRandomIv();
    SecretKeySpec key = buildV1SecretKey();
    Cipher cipher = Cipher.getInstance(cipherName);
    cipher.init(Cipher.ENCRYPT_MODE, key, getParams(iv));
    byte[] cipherBytes = cipher.doFinal(text.getBytes(StringHelper.CHARSET_UTF8));
    byte[] all = Bytes.concat(iv, cipherBytes);
    return V1_MARKER + bytesToString(all);

// CredentialCipher.java:69  包装仅做字符串拼接，keyId 与内层密文无密码学关联
return CV1_MARKER + keyId + ":" + v1Ciphertext;
```

- **严重程度**：P3
- **现状**：对抗用例 3/4 的实际防护推演——keyId 段被替换 ⇒ 路由至不同 passphrase 的 PBKDF2 派生密钥 ⇒ GCM tag 验证必败 ⇒ fail-closed（间接但有效）。例外情形：(1) 两个 keyId 配置了**相同 passphrase**（部署失误或本地→Vault 迁移复制期合法同材料），keyId 段互换可解密成功且无任何告警——明文不变故无保密性破坏，但 keyId 作为审计/路由标识可被无声改写；(2) 持 DB 写权限者可将凭证 X 的 data 整体替换为凭证 Y 的密文（解密得 Y 的明文）——无行级绑定，不可检测。
- **风险**：当前均无可利用链（前提 = 攻击者已持 DB 写权限或部署方自伤配置）；属格式演进的硬化缺口与事实性记录，非现行漏洞。若未来合规要求密文与行身份/密钥标识的可认证绑定，现格式无此能力。
- **建议**：不改动现格式（v1 内层为平台复用原语、加 AAD 即破坏全平台兼容）；记录为 cv2 演进候选——将 `keyId + credentialId` 作为 GCM AAD 传入，解密侧同参校验，同时消除同材料异 keyId 的静默改写与行级置换两类盲区。短期可在 owner doc「keyId → 材料不变式」小节补一句「不同 keyId 禁止配置相同材料」的运维约束（当前仅隐含于命名惯例）。
- **信心水平**：高（AAD 缺失由 JCA 调用形态直接确证；「同材料异 keyId 可解」由 PBKDF2 确定性推演）。
- **误报排除**：已排除「GCM tag 可跨密钥通过」的可能——128-bit tag 随机通过概率 2^-128；已排除「IV 复用导致 tag 可迁」——v1 每次加密随机 IV（`newRandomIv` @ `:250-254`，测试 randomIvProducesDistinctCiphertexts 佐证）。

### [D5-05] CredentialCipher.decrypt 未强制内层 v1: 前缀：cv1: 包装的 legacy 载荷落入弱路径（MD5 派生 + 静态 IV），与文档格式契约不一致

- **文件**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/CredentialCipher.java:120-122`；分派点 `nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java:387-393`
- **证据**：

```java
// CredentialCipher.java:120-122  注释自认依赖内层自分派，未校验 v1: 前缀
// v1Payload 保留 "v1:" 前缀，AESTextCipher.decrypt() 依赖它进行格式分派
ITextCipher cipher = keyProvider.getKey(keyId);
return cipher.decrypt(v1Payload);

// AESTextCipher.java:387-393  非 v1: 载荷自动落入 legacy 路径
public String decrypt(String text) {
    if (text != null && text.startsWith(V1_MARKER)) {
        return decryptVersioned(text);
    }
    return decryptLegacy(text);
}
```

owner doc 契约为「解析 keyId 后委托对应 AESTextCipher **解内层 `v1:` 密文**」（`docs-for-ai/03-modules/nop-credential.md:243`），但实现接受 `cv1:{keyId}:{legacy base64}` 形态并走 `decryptLegacy`（MD5 派生密钥 `buildSecretKey:202-217` + 实例静态 IV）。
- **严重程度**：P3
- **现状**：正常数据流不产生此形态（`CredentialCipher.encrypt` 恒输出 v1: 内层）；仅 DB 注入/历史残留可达。完整性仍受 GCM tag 保护（legacy 路径同为 GCM，攻击者无 passphrase 不可伪造），fail-closed 语义保持。
- **风险**：契约漂移（文档声明 vs 实现宽容）+ 密码学质量降级面——若此类行真实存在且未来 key 材料泄露，legacy 载荷的 MD5 派生（无盐、一次哈希）远弱于 v1 的 PBKDF2-SHA256(65536)，离线字典攻击成本悬殊；同时 legacy 静态 IV 下同 key 密文的确定性泄露（同明文 → 同密文前缀）在理论上复活。
- **建议**：`decrypt` 在委托前显式校验 `v1Payload.startsWith(AESTextCipher.V1_MARKER)`，否则抛 `ERR_CREDENTIAL_INVALID_CIPHERTEXT_FORMAT`（一行收紧，正常路径零影响，测试可补一个 `cv1:keyA:{legacy}` 拒绝用例）；若刻意保留宽容请在 javadoc/owner doc 记录理由。
- **信心水平**：高（分派逻辑直接可见；「正常流不产生 legacy 内层」由 encrypt 路径唯一性确证）。
- **误报排除**：已排除「`v1:` 前缀碰撞」——base64 字母表无冒号，legacy 载荷永不以 `v1:` 开头（`AESTextCipher.java:55-58` 自述同理）；已验证 `decryptRejectsEmptyV1Payload`（`TestCredentialCipher.java:221-226`）只覆盖空 payload，不覆盖非空非 v1 payload——测试确有空档。

### [D5-06] master-keys / Vault 迁移残余条目允许空白 passphrase 通过校验（弱密钥配置缺口）

- **文件**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/DefaultCredentialKeyProvider.java:106-111`；`nop-credential/nop-credential-kms-vault/src/main/java/io/nop/credential/kms/vault/VaultCredentialKeyProvider.java:195-202`
- **证据**：

```java
// DefaultCredentialKeyProvider.java:106-111  仅拒绝"冒号后为空"，空白串可过
int colonIdx = entry.indexOf(':');
if (colonIdx <= 0 || colonIdx == entry.length() - 1) {
    // 缺少分隔冒号或 passphrase 为空
    throw new NopException(CredentialErrors.ERR_CREDENTIAL_MASTER_KEY_ENTRY_INVALID) ...
}

// VaultCredentialKeyProvider.java:299-304  材料侧已用 isEmpty 拒空串，但 Vault 侧
// migration-keys 的 "keyId: " 空白串同样经 length 检查放入（:196）
```

`"keyA: "`（冒号后一个空格）在两处均通过并构造 `new AESTextCipher().encKey(" ")`。
- **严重程度**：P3
- **现状**：fail-open 的只有「密钥强度」这一维——解析、路由、加解密行为全部正常，应用可启动可运行。
- **风险**：空白/极短 passphrase 直接削弱 PBKDF2 输入熵（KDF 无法弥补低熵口令），全部 `cv1:keyA:` 密文的保密性退化为主密钥配置质量；与模块「fail-closed 配置质量文化」（keyId 非法/重复/缺 active 均启动拒绝）不一致，且 `@sec:` 侧同类场景平台已有 `StringHelper.isBlank` 惯例可循。Vault 托管材料的 `passphrase` 字段已用 `StringHelper.isEmpty` + `instanceof String` 拒空（`:300`），残余列表与本地 master-keys 未对齐。
- **建议**：两处将 `colonIdx == entry.length() - 1` 收紧为 `StringHelper.isBlank(entry.substring(colonIdx + 1))` 即抛对应 INVALID 错误码；补测试（`"keyA: "`、`"keyA:\t"` 各一例）。属行为收紧，部署存量若真有空白 passphrase 将显式暴露（符合本模块 fail-closed 基调）。
- **信心水平**：高（两处检查逻辑直接可见；未发现其他 passphrase 强度校验点）。
- **误报排除**：已排除「环境变量传递会剥离尾随空白使该场景不可达」——`@cfg:` 列表条目经 YAML/属性文件加载，`"keyA: "` 字面空白原样保留；测试 `passphraseMayContainColons` 表明条目解析按首个冒号切分、剩余原样作 passphrase。

### [D5-07] DefaultCredentialKeyProvider.getMasterKeys() 公开返回 keyId:passphrase 明文列表（主密钥泄露面反模式）

- **文件**：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/DefaultCredentialKeyProvider.java:164-172`
- **证据**：

```java
/**
 * 供测试与诊断使用：返回主密钥条目的只读副本。
 */
public List<String> getMasterKeys() {
    if (masterKeys == null) {
        return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(masterKeys));
}
```

条目格式为 `keyId:passphrase`——返回值即全部主密钥明文材料。方法为 `public`（非 SPI `ICredentialKeyProvider` 成员，具体类方法）。
- **严重程度**：P3
- **现状**：注释声明「测试与诊断使用」，但无任何访问控制或审计门禁；unmodifiable 仅防改列表内容，不防读取。
- **风险**：任何持有 `DefaultCredentialKeyProvider` 具体类型引用的代码（同进程 `@Inject` 具体类、反射诊断工具、未来误将其 toString/内容写入日志的诊断代码）可直接枚举全部 passphrase——一次误用即全部 `cv1:` 密文可离线解密。与模块「材料仅存进程内存、不落任何输出」的安全叙事（设计 §4.3「材料仅在进程内存中存在」）存在张力。当前 live 代码未发现生产调用点（仅测试），风险为暴露面而非已发生的泄露。
- **建议**：降级可见性（包私有 `getMasterKeysForTest()` 或挪至测试fixtures）；若确需运行期诊断，返回脱敏形态（仅 keyId 列表 + passphrase 指纹如 SHA-256 前 8 hex）；Vault 实现无对应 getter（已核对），保持两侧对称收紧。
- **信心水平**：高（方法签名与返回内容直接可见；grep 未发现生产调用点）。
- **误报排除**：已排除「SPI 面可达」——`ICredentialKeyProvider` 仅三方法（getActiveKeyId/getKey/getKeyIds），按接口注入的消费方不可达；已排除「测试必需 public」——同包测试（`io.nop.credential.crypto.TestDefaultCredentialKeyProvider`）包私有即够。

---

## 四、检查范围（含零发现面）

**已探查文件**（live 复核全部锚点）：

| 文件 | 关注点 |
|---|---|
| `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/CredentialCipher.java` | CV1_MARKER :34 / KEY_ID_PATTERN :40 / encrypt :62/:78 / decrypt :90 全分支 |
| `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/DefaultCredentialKeyProvider.java` | 多 key 加载/active 解析/W10 守卫/getKey fail-closed/getMasterKeys |
| `nop-credential/nop-credential-kms-vault/src/main/java/io/nop/credential/kms/vault/VaultCredentialKeyProvider.java` | 材料交付/迁移残余 key 只解不加密/禁含 active/重复 keyId/getKey fail-closed |
| `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java` | reencryptAll :571-647 / buildReencryptQuery / extractKeyId / saveCredential 加密点 :260/:306 |
| `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java` | decryptToData :592-604 / OAuth 回写加密点 :321 |
| `nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java` | v1/legacy 分派 :387-393 / GCM 参数 / PBKDF2 / 随机 IV |
| `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/StringHelper.java:1515-1521` | decodeBase64 严格性 |
| `nop-core-framework/nop-config/src/main/java/io/nop/config/enhancer/DefaultConfigValueEnhancer.java` | `@sec:`/`@switch:`/`${` 识别隔离 |
| `nop-core-framework/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java:449-475` | config cipher 独立构造 |
| `nop-core-framework/nop-config/src/main/java/io/nop/config/ConfigConstants.java:104` + `nop-kernel/nop-commons/.../CommonConstants.java:33` | `@sec:` 常量唯一来源 |
| `nop-service-framework/nop-graphql/.../GraphQLTransactionOperationInvoker.java` + `DefaultExecutionFunctionInvoker.java` + `nop-biz/.../TransactionActionDecorator(Collector).java` + `biz-defaults.beans.xml` + `orm-defaults.beans.xml` + `nop-dao/.../TransactionalFunctionInvoker.java` | mutation 事务边界链（D5-01 证据） |
| `nop-credential/.../beans/credential-defaults.beans.xml` / `app-kms-vault.beans.xml` | 同名 bean 覆盖 + 条件门控（多 key 单活 provider 结构执法） |
| 测试（已覆盖面参考）：`TestCredentialCipher`（16）、`TestDefaultCredentialKeyProvider`（19）、`TestNopCredentialReencryptPagination`（2）、`TestVaultCredentialKeyProvider`（24）、`TestVaultKeyProviderWiring`（4）、`TestLocalKeyProviderWithKmsModulePresent`（2）、守卫测试（2） | 见对抗用例表引用 |

**实测执行**：`./mvnw test -pl nop-credential/nop-credential-service`（全模块 166 用例绿）+ `-pl nop-credential/nop-credential-kms-vault`（32 用例绿；日志中 `init-bean-fail` ERROR 为守卫测试预期场景输出）。

**零发现面**（已核查、未发现问题）：cv1 解析全异常分支 fail-closed；active key 加密路径唯一性（4 个生产写点均无参 encrypt）；keyId 全串 pattern 匹配（4 处一致）；`@sec:`/cv1 双向误用失败模式；Vault 迁移残余 key 的 active 隔离（active 解析先于残余并入 + 显式校验 + 测试覆盖）；keyMap 不可变性与启动期一次性加载（无 TOCTOU/热重载面）；同名 bean 单活 provider 结构（禁止第二 ICredentialKeyProvider bean）。

---

## 五、结论汇总

- **Findings**：7 项 — P0×0、P1×0、P2×1（D5-01）、P3×6（D5-02 ~ D5-07）。
- **回归锚点**：`cv1:{keyId}` 密文格式兼容 **PASS**；`@sec:` 配置加密共存 **PASS**。
- D5 维度核心防线（cv1 解析 fail-closed、keyId 路由、多 key 并存窗口语义、两格式隔离）经逐分支核对与全量测试验证成立；发现集中在 reencryptAll 迁移完备性的边缘盲区与密码学绑定/配置质量的硬化空间，无现行可利用漏洞。
