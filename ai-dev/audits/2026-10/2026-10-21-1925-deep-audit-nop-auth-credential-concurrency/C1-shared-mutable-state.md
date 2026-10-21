# C1 共享可变状态与可见性 — nop-auth-credential（WI 5.1）

- 审计维度：C1（共享可变状态与可见性）
- 批次：1/2 + 2/2（C1 维度盲审初审，两批次收官）
- 日期：2026-10-21
- 审计员：独立盲审子代理（未读 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons、ai-dev/backlog；未 git log）
- 复核状态：已复核（见文末「维度复核结论」与「最终保留项」：保留 5 + 重复并入 1 + C1-006 见 round2；CL-001…007 全部确认）

## 审计范围与口径

### 批次 1/2 覆盖声明

逐文件审读（src/main 手写口径），数量结论人工复现（`find ! -name "_*" | wc`）：

| 范围 | 实际落位 | 文件数/行数（人工复现） |
|---|---|---|
| nop-credential 全量 | `nop-credential-api`(9f/635) + `nop-credential-service`(20f/4093) + `nop-credential-kms-vault`(2f/497) + `nop-credential-dao`(9f/92) + `nop-credential-app`(1f/39) | **41f/5,356**（与任务口径 41f/5,356 精确对账） |
| nop-auth api | `nop-auth-api`：手写仅 `api/bind` 3 文件（BindStartResult/ChannelBindingInfo/IChannelBindService）；`beans/`+`crud/` 全部带 `//__XGEN_FORCE_OVERRIDE__` 头，为 api codegen 生成物，按盲审纪律不作为发现对象 | 3 手写文件 |
| nop-auth app | `nop-auth-app`：NopAuthApplication（1 文件） | 1f |
| nop-auth dao | 27 个实体 retention 薄子类（`_NopAuth*` 生成基类的手写扩展）+ DefaultUserIdGenerator/IUserIdGenerator + 6 个 `@SqlLibMapper` 接口 + 27 个 `INopAuth*Biz` 空接口 + DaoUserDelegateService + NopAuthDaoConstants | 全量 |
| nop-auth meta | **无 `src/main/java`**（仅 src/test），无需审读 | 0f |

**路径勘误**：任务口径中的 `nop-credential/nop-credential-core/src/main/java` 在仓库中不存在（R1 基线同名 ROOT 亦为空）。凭证加密/密钥管理三类的实际落位为：`CredentialCipher`/`DefaultCredentialKeyProvider` → `nop-credential-service/src/main/java/io/nop/credential/crypto/`，`VaultCredentialKeyProvider` → `nop-credential-kms-vault/src/main/java/io/nop/credential/kms/vault/`。本审计按实际落位全覆盖（41f/5,356 对账即为证据）。

### Protected Area 声明

nop-auth 为 ask-first Protected Area。**本审计全程只读，未修改任何代码、模型或行为**，不触发 ask-first。

### 并发面极稀疏预校准声明

R1 基线（`_tmp/cra-baseline-nop-auth-credential.txt`）批次 1 ROOT 分节实测：原语命中仅 2 文件（`NopAuthApplication.java` / `NopCredentialApplication.java` 的 `static String[] globalArgs`；dao 实体 `newPk()` 为静态工厂方法，非可变字段）。C1b（static final 可变集合）、C1c（ThreadLocal）、C2a（锁载体）、C2b（computeIfAbsent）、C4a（线程/池）、C7a（计数器递增）在批次 1 根目录**零实质命中**（C8a/C3b 命中均为 XGEN 生成 bean 样板噪声）。本审计未凑数扩面：最终发现仅 2 条 P3，与预校准一致。

### 降档口径

认证/凭证解密为生产请求触达路径，启动期降档**不适用**于请求路径判定；但主密钥材料加载（DefaultCredentialKeyProvider/VaultCredentialKeyProvider）本身为启动期一次性行为（@PostConstruct，热重载为已声明的 Non-Blocking Follow-up），按启动期初始化口径判定（见清单表 #3/#4）。

### 共享可变状态清单表（字段 × 写者 × 读者 × 同步机制 × 判定）

| # | 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| 1 | `NopAuthApplication.globalArgs`（static String[]，:22） | `main()` 一次（main 线程） | Quarkus StartupEvent observer（`start()` :27） | `Quarkus.run(args)` 内部线程启动建立 happens-before（写先于 run 调用，observer 在 run 内派生线程中执行） | 合格（启动期一次写，平台标准 launcher 惯用法） |
| 2 | `NopCredentialApplication.globalArgs`（static String[]） | 同 #1 | 同 #1 | 同 #1 | 合格（#1 的 F3 同族兄弟，同判定） |
| 3 | `DefaultCredentialKeyProvider.keyMap/keyIds/activeKeyId`（:59-60，private 非 final 非 volatile） | `@PostConstruct init()` 一次性重建（`Collections.unmodifiableMap/Set` 包装后整体替换引用；IoC 主线程） | 请求线程（CredentialCipher.encrypt/decrypt → getKey/getKeyIds/getActiveKeyId） | NopIoC 单例构造+初始化完成后发布；发布后**只读**（无任何运行期写路径，热重载未实现为已声明边界） | 合格（init-once + 安全发布依赖容器启动语义）。备注：若未来实现热重载，必须改为 volatile 引用替换或并发容器，当前无此代码 |
| 4 | `VaultCredentialKeyProvider.keyMap/keyIds/activeKeyId/address/token/keys/migrationKeys/masterKeys/requestTimeout/sharedActiveKeyId/httpClient`（:84-162） | `@InjectValue`/`@Inject` 注入 + `@PostConstruct init()` 一次性重建 keyMap/keyIds（IoC 主线程） | 请求线程（getKey/getKeyIds/getActiveKeyId） | 同 #3；材料仅存进程内存、运行期零托管端调用（类 javadoc 显式声明） | 合格（同 #3 口径） |
| 5 | `CredentialCipher.keyProvider`（@Inject protected，:78-79） | NopIoC 注入一次（含 setter） | 请求线程（encrypt/decrypt） | 容器发布；发布后只读 | 合格（无状态单例 + 只读注入依赖，设计意图） |
| 6 | `DefaultCredentialTypeRegistry.typeMap`（:57） | `@PostConstruct init()` 一次性重建（unmodifiableMap 整体替换） | 请求线程（getType/listTypes） | 同 #3 | 合格（发布后只读） |
| 7 | `NopCredentialBizModel.reencryptPageSize`（protected int，:617） | `@InjectValue` setter 启动期一次（<1 fail-closed 拒绝） | reencryptAll 运行期读 | 容器发布；发布后只读 | 合格（init-once 配置字段） |
| 8 | `NopCredentialBizModel.lastNonCv1SkippedCount`（包私有 int，:724） | `reencryptAll` 运行期写（:708，任意 admin 请求线程，非 volatile，非原子，后写覆盖先写） | 仅测试读（TestNopCredentialReencryptPagination:215/222）；生产零读点 | **无** | **P3 → C1-001** |
| 9 | `CredentialType` 实例及其 `fields`（ArrayList，:48）/`OAuth2Metadata`（registry init 期填充） | registry `init()` 加载期写（单线程） | 请求线程读 + 经 `typeList()`/`getFields()`/`getOauth2()` 逃逸到响应序列化 | 发布后无写者（全仓 grep 证实：getFields 消费方均为只读流式/迭代） | **P3 → C1-002**（逃逸载体成立，当前无写者故无竞态，防御性收口） |
| 10 | dao 实体（NopAuthUser/NopCredential 等 retention 子类） | 请求内 session 加载/修改 | 同请求同线程（ORM session 请求域） | ORM session 每请求独立；跨线程共享受管实体无使用证据（DaoUserDelegateService 等均为请求内读） | 合格（`getRoles()` 等派生集合每次调用新建，无共享） |
| 11 | `static SimpleDateFormat/Random/Calendar` | — | — | — | 无命中（grep 证实范围内零使用；state 生成走 `StringHelper.generateUUID` → `MathHelper.secureRandom()`） |
| 12 | 惰性初始化 / 检查-然后-放入缓存 | — | — | — | 范围内无双检锁/containsKey-then-put 内存缓存形态（唯一的 `if (fields == null)` 为局部 map 缺省值，非共享态） |

## 发现

### [C1-001] 单例 BizModel 上的运行期可变实例计数器 `lastNonCv1SkippedCount`（无同步、非 volatile）

- **文件**: `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:708,717-724`
- **证据片段**:
  ```java
  // :708（reencryptAll 末尾，任意 admin 请求线程执行）
  this.lastNonCv1SkippedCount = nonCv1Skipped;
  ...
  // :723-724
  int lastNonCv1SkippedCount;
  ```
- **严重程度**: P3
- **并发场景**: NopIoC 单例 bean（`@BizModel` 默认 singleton）实例字段；写者 = 执行 `reencryptAll` GraphQL mutation 的任意 HTTP worker 线程（admin 触发，低频）；读者 = 仅测试线程（`TestNopCredentialReencryptPagination:215/222`，包私有断言暴露）；生产代码零读点（全仓 grep 证实）。
- **触发时序**: 理论风险，未具体化到生产后果——T1（admin A 的 reencryptAll 请求线程）执行到 :708 写入 `lastNonCv1SkippedCount=3`（普通 int 写，无 volatile/锁）→ T2（admin B 的并发 reencryptAll）执行到 :708 写入 `5` → 交叠后果 = 后写覆盖先写；若存在跨线程读者（当前仅测试，且测试单线程执行同一请求返回后断言，请求线程与测试线程间经事务提交/响应返回有 HB 边沿），读者可能观察到过期值。生产无读者，故无生产后果链。
- **现状**: 单例 bean 上的可变实例字段承担"最近一次执行的逃逸信号"职责，写侧无任何同步/可见性保障；字段定位为测试断言通道（注释明示"包私有可断言暴露"）。
- **风险**: 不修复的生产风险趋近于零（无生产读点）；若未来将此计数器接入监控/告警（注释暗示的"KMS 迁移关窗完备性信号"的自然延伸），并发执行或跨线程读取时可能报告过期值，误导关窗判定。
- **建议**: 改为 `volatile int`（一行变更，语义不变）；或将计数器改为方法返回值/ThreadLocal 外露；长期可复用平台 `io.nop.commons.metrics` 计量设施。不建议引入锁（低频 admin 路径，粗粒度同步反而违反误报校准第 3 条的性价比口径）。
- **失效面枚举**（F3 同族清扫）: 批次 1 范围内单例 bean 上"运行期写入的非 final 非 volatile 实例字段"完整清单——`NopCredentialBizModel.lastNonCv1SkippedCount`（本条，唯一命中）；`NopCredentialBizModel.reencryptPageSize`（init-once 配置，合格）；`OAuthFlowService`/`CredentialProviderImpl`/`DefaultCredentialTypeRegistry`/`CredentialMigrationSupportImpl`/`OAuthTokenClient`/`DaoUserDelegateService` 等仅含注入协作对象（发布后只读，合格）；两个 Application 的 `globalArgs`（启动期一次写，合格）。同族无漏修兄弟。
- **信心水平**: 确定（字段可变性、写点、读点、无同步均已逐一核实）
- **误报排除**: 非"无状态单例被并发调用"误报类——本字段是真实的运行期写入共享可变状态；未按 P1/P2 报是因为生产无读点、后果链断裂（校准允许的最低档）。
- **复核状态**: 已保留（P3；复核员回码核实 :708 写点与 :724 包私有字段声明、"包私有可断言暴露"注释原文一致；生产零读点经 grep 维持）
- **复核注记**: 行号与引文逐字核实无误；降档口径（启动期降档不适用于请求路径、本条按无读点最低档）核对成立。与 C2-004/C1-005 无交叉。

### [C1-002] registry 缓存 DTO 经 `typeList()`/getter 逃逸内部可变结构（当前无写者，防御性收口）

- **文件**: `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:562-584`；`nop-credential/nop-credential-api/src/main/java/io/nop/credential/api/registry/CredentialType.java:48,116-122`；`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/registry/DefaultCredentialTypeRegistry.java:186-200`
- **证据片段**:
  ```java
  // NopCredentialBizModel.typeList() 非 oauth2 分支 :580-581 —— 返回 registry 活对象
  } else {
      result.add(type);
  }
  // oauth2 分支虽拷贝外壳，仍共享内部元数据与字段对象
  copy.setOauth2(type.getOauth2());
  // CredentialType :116-118 —— 泄漏内部 List
  public List<CredentialField> getFields() {
      return fields;
  ```
- **严重程度**: P3
- **并发场景**: `DefaultCredentialTypeRegistry` 单例在 `@PostConstruct` 单线程构建 `typeMap` 并以 unmodifiableMap 发布（map 层不可变，**value 层可变**）；请求线程经 `typeList()`（GraphQL 查询面，登录用户可达）与 `listTypes()` 拿到 `CredentialType` 活对象（非 oauth2 类型为 registry 内部实例本身）及其内部 `ArrayList<CredentialField>`、共享 `OAuth2Metadata`。
- **触发时序**: 理论风险，未具体化（**当前代码库不存在发布后的写者**——全仓 grep `getFields()`/`setFields()` 消费方均为只读：`CredentialProviderImpl.mask` 流式读、`DefaultCredentialTypeRegistry.validate` 初始化期读、`NopCredentialBizModel.typeList` 拷贝读）。仅当未来任一消费方对返回的 `CredentialType`/`getFields()` 列表调用 setter/add/remove 时，才构成对 registry 共享缓存对象的无同步并发写：T1 请求线程 A 遍历 `type.getFields()` → T2 请求线程 B 对同一列表 `add()` → ArrayList 并发修改异常或丢失写入，且污染全局类型元数据（所有后续 `mask` 脱敏判定失真）。
- **现状**: 逃逸载体成立（C1 执行步骤 1 第 5 类"getter 泄漏内部集合"），但缺写者，未构成可触发缺陷；`typeList()` javadoc 已自知"返回浅拷贝，不改写 registry 缓存对象"，仅 oauth2 分支做了防御拷贝，非 oauth2 分支未对齐。
- **风险**: 不修复的即时风险为零；隐患在于该 API 面向 GraphQL 公开（前端动态表单渲染），任何后续迭代在消费侧引入写操作（如本地排序 `list.sort()`——`List.sort` 原地修改，是概率最高的误用形态）都会静默变成跨请求共享缓存写。
- **建议**: 对齐 oauth2 分支先例，`typeList()` 非 oauth2 分支同样返回拷贝；或在 `DefaultCredentialTypeRegistry.init()` 构建完成后将 `fields` 置为 `Collections.unmodifiableList`（写侧一次性收口，消费侧无需逐处防御）；`TestResult.getTestedAt()` 的可变 `java.sql.Timestamp` 出参为同族防御点（可选一并收口为不可变化处理）。
- **失效面枚举**（F3 同族清扫）: 批次 1 范围"getter 泄漏内部可变结构"完整清单——`CredentialType.getFields()`（本条，活 List）；`CredentialType.getOauth2()`（活 OAuth2Metadata，typeList oauth2 分支共享，同判）；`CredentialData.getFields()`（构造方注入的 map，但每个解密调用均经 `JsonTool.parseMap` 新建，非共享，合格）；`MaskedCredential.getFields()`（每调用新建 LinkedHashMap，合格）；`DefaultCredentialTypeRegistry.listTypes()`（拷贝列表层，元素为活对象——已并入本条）；`TestResult.getTestedAt()`（可变 Timestamp，同族防御点，状态已列）。同族无已触发缺陷。
- **信心水平**: 确定（逃逸事实确定；"当前无写者"经全仓 grep 确定而非推断）
- **误报排除**: 非"原语使用误报"——登记对象是共享缓存对象的**表示暴露**契约，不是容器/锁原语本身；未升级 P2 是因为写不出当前代码下的具体交错（纪律要求：无写者即无时序，不虚报）。
- **复核状态**: 已保留（P3；复核员回码核实 `CredentialType.java:48` `fields = new ArrayList<>()` + `:116-118` getFields 返回活引用；C1-004 为同族先例互证）
- **复核注记**: 逃逸载体与"无发布后写者"双事实核实成立；防御性收口定位（非已触发缺陷）维持。

## 维度集外线索（overlay C3/C4/C5/C7/C8/C9）

### nop-auth-credential-CL-001（C9 吞异常）Vault fetchMaterial 吞 JSON 解析异常丢失 cause

- 文件：`nop-credential/nop-credential-kms-vault/src/main/java/io/nop/credential/kms/vault/VaultCredentialKeyProvider.java:344-356`。`JSON.parse(body)` 的 `RuntimeException` 被 catch 后置 `material=null`（`catch (RuntimeException e) { material = null; }`），随后统一抛 `ERR_CREDENTIAL_VAULT_MATERIAL_INVALID` 且**不带 cause**。整体语义仍 fail-closed（不会静默使用坏材料），但"Vault 返回了非 JSON 体（如网关 HTML 错误页）"与"JSON 合法但结构不符"两种故障归一，且解析异常栈丢失，加大托管端故障排查成本。建议：将 e 作为 cause 或 ARG_ERROR 传入（对齐同文件 ：321-323 `ERR_CREDENTIAL_VAULT_UNREACHABLE` 携带 `e.getMessage()` 的先例）。

### nop-auth-credential-CL-002（C4 事务语义 / 文档-实现不符）reencryptAll 声称"逐条提交/断点语义"实为单事务全量

- 文件：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialBizModel.java:640-641`（javadoc："逐条提交可重跑（单条失败抛错中止，重跑从断点语义继续）" vs 实际）。`reencryptAll` 为 `@BizMutation`，运行在单一读写事务中（同类 `saveCredential` 注释自证"BizMutation 运行在读写事务中"），中途任一条失败 → 整个事务回滚，**不存在断点**；重跑靠"keyId==active 幂等跳过"从头收敛（正确性不受损，无数据损坏）。两个衍生问题：(a) javadoc 声明与实现不符，误导运维对失败中断点位的预期；(b) 全量重加密单事务持有时长随凭证量线性增长（缺省页大小 1000 仅控制游标翻页，不拆事务），长事务持锁/Undo 压力在凭证量大的部署被放大。建议：javadoc 改口，或按页拆事务（每页独立提交，页间以游标天然续跑）。

### nop-auth-credential-CL-003（C4 幂等契约 / 检查-然后-插入竞态）registerUsage 与 grant 的并发输家收到约束冲突而非幂等成功

- 文件：`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java:214-228`（registerUsage：`findAllByQuery` 空判定 → `saveEntityDirectly`）；`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/entity/NopCredentialAuthBizModel.java:122-133`（grant：`findGrants` 空判定 → `saveEntityDirectly`）。两处均为 DB 层"检查-然后-插入"，并发双写依赖 `(credentialId, consumerRef)` / `(credentialId, roleId)` 唯一约束兜底——数据一致性成立（不会双行），但**输家收到唯一约束冲突异常而非 javadoc 承诺的幂等 no-op 成功**（grant javadoc 自述"唯一约束兜底并发竞态"，实为兜底到报错而非兜底到幂等）。F3 同族即此两处，状态一致。触发时序：T1 与 T2 同时通过 exists 检查（均空）→ T1 insert 成功提交 → T2 insert 违反唯一约束 → T2 调用方得到异常。建议：捕获约束冲突异常转幂等成功（对齐 SPI "幂等：不重复插入" 字面契约），或调用方约定重试语义。

---

# 批次 2/2（C1 维度盲审初审）

## 批次 2/2 覆盖声明

逐文件审读（src/main 手写口径），数量结论人工复现（`find ! -name "_*" | wc`）：

| 范围 | 实际落位 | 文件数/行数（人工复现） |
|---|---|---|
| nop-auth-service 全量 | login（6f：LoginServiceImpl/DaoLoginSessionStore/DaoUserContextCache/DbLoginAttemptStore/RedisLoginAttemptStore/LoginAttemptStoreProvider）+ mfa（24f 含 store 6f）+ ratelimit（5f）+ sitemap（3f）+ auth（2f）+ audit（2f）+ channel（2f）+ biz（6f 含 dto 5f）+ entity（29f）+ configs/errors/constants（3f） | **83f/12,605** |
| nop-auth-sso 全量 | jwk（9f：JWKPublicKeyLocator/JWKSUtils/JWK 系 DTO）+ login（OAuthLoginServiceImpl）+ web（SsoLoginWebService）+ 根 DTO/config（6f：SsoConfig/Tokens/UserInfo/AccessTokenResponse/errors/constants） | 17f/1,852 |
| nop-oauth/nop-oauth-api | api/beans 6 DTO + api/crud 3 接口（全 DTO/接口，无可变共享态） | 9f/1,420 |
| nop-oauth/nop-oauth-service | 3 个空壳 CrudBizModel 子类 + configs/constants/errors | 6f/99 |
| nop-oauth/nop-oauth-dao | 3 retention 薄实体子类 + 3 biz 空接口 + constants + _gen PkBuilder | 8f/130 |
| `nop-auth/nop-oauth/nop-oauth-web/src/main/java` | **不存在**（仓库无该目录） | 勘误：0f |
| `nop-auth/nop-auth-codegen/src/main/java` | **不存在**（仓库无该目录） | 勘误：0f |

**范围外同树备注**：`nop-oauth/nop-oauth-app/src/main/java`（1f NopOauthApplication，不在本批次任务口径内）实测为批次 1 清单表 #1/#2 同款 `static String[] globalArgs` 惯用法（main 一次写 + Quarkus observer 读），批次 1 判定（合格）同判，F3 记录备查。

### Protected Area 声明

nop-auth 为 ask-first Protected Area。**本批次全程只读，未修改任何代码、模型或行为**（含 beans.xml 仅读取）。

### 指定检查点结论

- **JWKPublicKeyLocator（volatile CHM + synchronized 复合操作）**：合格。`currentKeys`/`lastRequestTime` 均 volatile（:43/:45）；刷新的 check（`currentTime > lastRequestTime + minTimeBetweenRequests*1000`）与 `sendRequest()`/`lastRequestTime` 更新同在 `synchronized(this)` 临界区（:73-83），reset() 同锁（:89-94）——无复合操作撕裂。安全发布链成立：`sendRequest` 内 volatile 写 `currentKeys`（:122）先于 volatile 写 `lastRequestTime`（:77），读者在 `lookupCachedKey` 先读 `lastRequestTime` 再读 `currentKeys`（:98-99），volatile 读建立 HB。刷新失败（:123-125 吞异常记日志）保旧键 + lastRequestTime 仍推进 = 节流重试语义（Keycloak 上游同款），非缺陷。
- **session/token 存储**：DaoLoginSessionStore.logoutSession 的 check-then-act（:78-82）幂等无害（双并发 logout 后写同值）；DaoUserContextCache.saveUserContextAsync 的 load→logoutType 检查→写（:115-141）TOCTOU 最坏后果为登出后 token 字段被覆盖（logoutType 保留，后续读取仍拒绝——getUserContextAsync :58 拦截），无安全面；DbLoginAttemptStore 条件 UPDATE + duplicate-key 重试（:96-117）与 javadoc 契约一致，末级兜底再 insert 仍可撞 PK（微秒级三重竞态，理论不可具体化，不登记）。
- **LocalSendCodeRateLimiter（Caffeine 并发）**：合格。`Cache.asMap()` + `synchronized(targetTrackers)` + `compute`（:82-96）保证间隔检查与 lastSendMs 占用同一原子区，"并发突发同目标恰好放行 1 个"契约成立（count>1 → 间隔判定拒绝）；IP 层 `compute` 依赖 Caffeine per-key 原子性（:109）；Map 硬上限 + expireAfterWrite 为已声明边界（javadoc :41-42），驱逐导致的计数重置不按缺陷报（校准第 6 条）。
- **MFA 状态面**：MfaStoreProvider/SendCodeRateLimiterProvider/LoginAttemptStoreProvider 均为 init-once 装配点（collect-beans map 启动期注入后只读，fail-closed 选择）；Db 系 store 原子性由单语句条件 UPDATE/DELETE 承接（consume/markVerified/incrFail affected-row 判定，并发恰一语义经注释与批次 1 基线双证）；Redis 系 store 原子消费 `removeIfMatch`(Lua CAS)、原子计数 INCRBY——但"首次递增 + setTimeoutAsync 附着 TTL"为两步非原子，见 **nop-auth-credential-CL-006**。
- **OAuth state 面**：本批次范围内无 OAuth `state` 参数内存缓存（授权码/state 均由 IdP 侧或 DB 承载）；OAuthLoginServiceImpl 的共享态仅 `keyLocator`（@PostConstruct :80-84 构造、容器发布）与注入 config/httpClient——合格。doLogout 异步链问题见 **nop-auth-credential-CL-004**。
- **`ioc:delay-method` 语义核实**（DefaultDataAuthChecker.lazyInit / nopLoginService.lazyInit 的单次执行保证）：BeanContainerImpl.start() 在容器启动流程内 `runDelayMethod()` 于 `started=true` 之前对全部 hasDelayMethod bean 单遍执行（nop-ioc BeanContainerImpl :565-575），非请求期惰性执行——lazyInit 为启动期单线程一次性行为，`modelCache`（DefaultDataAuthChecker :69-72）与 `addDefaultUser`（LoginServiceImpl :307-349）均合格。

### 共享可变状态清单表（批次 2）

| # | 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| 1 | `JWKPublicKeyLocator.currentKeys/lastRequestTime`（:43/:45） | 请求线程经 synchronized 临界区刷新 | 全部 JWT 验签请求线程 | volatile 双字段 + synchronized(this) 复合区 | 合格（指定检查点，见上） |
| 2 | `LoginServiceImpl.mfaFlow/mfaCodeSender/defaultRateLimiter`（:599-600/:248）+ `NopAuthUserBizModel.selfService/defaultRateLimiter`（:259/:202） | 请求线程 check-then-act 惰性赋值 | 同类请求线程 | **无**（非 volatile、非原子） | **P3 → C1-003** |
| 3 | `SiteMapProviderImpl.siteCache`（:67，IResourceLoadingCache 平台设施）→ `SiteCacheData`（siteMaps/resourceToRoles/permissionToRoles 三 HashMap + SiteMapBean 树） | loadSiteData 构建期单线程写 + 平台缓存失效重建 | 全部请求线程（isPermitted/getSiteMap 每请求） | 平台 loading cache 发布；构建后只读（filterAllowedMenu 先 deepClone） | 缓存本体合格；getter 逃逸面 **P3 → C1-004** |
| 4 | `SiteCacheDataBuilder` 7 个 HashMap（:37-50） | 单次 loadSiteData 内 builder 本地 | 同 build 调用链 | 每次 load new 实例，单线程 | 合格（C2b 基线命中全为本地 map，非共享） |
| 5 | `LocalSendCodeRateLimiter.targetTrackers/ipTrackers`（Caffeine asMap） | 请求线程（发码路径） | 同 | synchronized + compute / per-key compute | 合格（指定检查点，见上） |
| 6 | `DbLoginAttemptStore`（表 nop_auth_login_attempt） | 登录失败路径请求线程 | 登录入口锁号判定 | 条件 UPDATE 单语句原子 + duplicate-key 重试 | 合格 |
| 7 | `RedisLoginAttemptStore`（INCRBY 计数键） | 登录失败路径请求线程 | 同 | INCRBY 单键原子；TTL 附着两步非原子 | 计数原子合格；TTL 附着缺陷 → **CL-006** |
| 8 | MFA store 六实现（challenge/sms/email × db/redis） | mfa 流程请求线程 | 同 | db：条件 SQL；redis：Lua CAS + INCRBY | 原子性合格；redis TTL 附着 → CL-006 族 |
| 9 | `ChannelBindServiceImpl.providersByType`（LinkedHashMap，:71） | `setChannelBindProviders`（collect-beans 装配期） | 请求线程 requireProvider | init-once + 容器发布；registerProvider 运行期无调用方（全仓 grep） | 合格（若未来运行期动态注册须加并发容器，当前无此代码） |
| 10 | `DefaultDataAuthChecker.modelCache`（CacheEntryManagement + GlobalCacheRegistry） | ioc:delay-method 启动期一次 + clearCache 管理端 | isPermitted/getFilter 每请求 | 平台缓存设施（getObject 带 loader+timeout） | 合格 |
| 11 | `AuditServiceImpl`（AbstractBatchProcessService 队列） | saveAudit 各请求线程 | 批处理线程 | 平台批量设施（getQueue().send 阻塞入队） | 合格（设施不重审） |
| 12 | `GraphQLAuditLogger.auditMutationPatterns/auditQueryPatterns` | @InjectValue 启动期一次 | 每请求 shouldAudit | 容器发布后只读 | 合格 |
| 13 | `OAuthLoginServiceImpl.keyLocator/config/httpClient` | @PostConstruct/setter 一次 | 请求线程 | 容器发布后只读 | 合格（doLogout 异步链 → CL-004） |
| 14 | `SsoConfig`（@ConfigBean 17 字段）/`Tokens`（全 final）/UserInfo/AccessTokenResponse/oauth-api DTO/jwk DTO | IoC/反序列化构造 | 请求线程 | init-once 或不可变 | 合格 |
| 15 | `static SimpleDateFormat/Random/Calendar` | — | — | — | 无命中（Random 使用点为 `static final SecureRandom`（WebAuthnAuthenticator :81，线程安全设计意图）；`MathHelper.secureRandom()` 为平台设施） |
| 16 | ThreadLocal | — | — | — | 范围内零命中（基线 C1c 全节无本批次 ROOT 命中） |

## 发现（批次 2）

### [C1-003] 两个单例 BizModel/Service 上 5 个 check-then-act 惰性初始化字段（非原子、非 volatile）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:248-256,599-621`；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:202-210,259-270`
- **证据片段**:
  ```java
  // LoginServiceImpl :606-612（mfaFlow/mfaCodeSender 同型；defaultRateLimiter :250-256 同型）
  protected LoginMfaFlow mfaFlow() {
      if (mfaFlow == null) {
          mfaFlow = new LoginMfaFlow(mfaChallengeStore, ...);
      }
      return mfaFlow;
  }
  // NopAuthUserBizModel :262-270（selfService 同型；defaultRateLimiter :204-210 同型）
  ```
- **严重程度**: P3
- **并发场景**: NopIoC 单例 bean（`nopLoginService`/`NopAuthUserBizModel`）实例字段；写者与读者均为 HTTP worker 请求线程——loginAsync 每请求经 `checkMfaRequired → mfaFlow()`、sendSmsCode/sendMfaCode 经 `mfaCodeSender()/rateLimiter()`、NopAuthUser MFA 自助面每 mutation 经 `selfService()`。字段均非 volatile、check-then-assign 非原子。
- **触发时序**: 冷启动并发突发可具体化——T1 与 T2 同时执行 loginAsync，均到达 :607 `if (mfaFlow == null)` 且均见 null → 各自构造 LoginMfaFlow → 字段写交叉，后写覆盖先写，T1/T2 各持有不同实例完成本次请求；第三个并发线程若读字段发生在任一写可见之前则再次构造。`defaultRateLimiter` 同构竞态：双实例各自持有独立 Caffeine 计数器，同目标并发首发在两个实例上各自计数=1 → 间隔限流放行 2 个（违反 javadoc"并发突发同目标恰好放行 1 个"契约），仅冷启动首窗口。
- **现状**: 5 个惰性初始化字段构成同族：`LoginServiceImpl.mfaFlow/mfaCodeSender/defaultRateLimiter` + `NopAuthUserBizModel.selfService/defaultRateLimiter`。生产容器装配注入 `nopActiveSendCodeRateLimiter`（auth-service.beans.xml:129-130 工厂 bean）→ `defaultRateLimiter` 回退路径生产不可达（javadoc 自述手工 wiring 测试用途）；`mfaFlow/mfaCodeSender/selfService` 生产必经惰性路径。
- **风险**: 生产语义后果趋近于零——三个惰性组件均为无状态（全 final 字段注入协作对象），JMM final-freeze 保证跨线程读到的引用指向完整构造对象；残余风险 = 瞬时重复分配 + 若未来任一惰性组件引入可变实例状态（如内联缓存）则该竞态静默升级为行为分叉。
- **建议**: `@PostConstruct` 内 eager-init（三者构造均为廉价引用装配）；或字段加 `volatile`（一行变更消除重复构造与可见性延迟）；最低限度加注释声明"启动后单次写"意图。不建议引入锁（每请求热路径）。
- **失效面枚举**（F3 同族清扫）: 批次 2 范围"请求期 check-then-act 惰性初始化实例字段"完整清单——上述 5 字段（本条，全量登记）。排除项逐一核实：`DefaultDataAuthChecker.lazyInit`/`LoginServiceImpl.lazyInit`（ioc:delay-method，启动期单次，见覆盖声明）；`ChannelBindServiceImpl.setChannelBindProviders`（IoC 装配期）；`AbstractUserContextCache.init`/`LocalUserContextCache.init`（@PostConstruct，auth-core 侧）；dao/DTO 无实例惰性形态。同族无漏登兄弟。
- **信心水平**: 确定（5 字段写点/读点/无同步逐一核实；生产装配可达性经 beans.xml 核实）
- **误报排除**: 非"低频路径粗粒度 synchronized"类（此处恰是无守卫）；非"无状态单例被并发调用"误报（登记对象是惰性初始化复合操作本身，C1 执行步骤 4 定向项）；定 P3 是因生产可达路径后果良性 + defaultRateLimiter 生产不可达，非因否定竞态存在。
- **复核状态**: 已保留（P3；复核员回码核实 LoginServiceImpl :599-621 mfaFlow/mfaCodeSender 惰性构造 + :248-256 defaultRateLimiter、NopAuthUserBizModel :202-210 defaultRateLimiter + :259-270 selfService——五字段逐一在场，均非 volatile）
- **复核注记**: 降档口径核对成立（组件无状态 + final 字段 JMM freeze ⇒ 后果良性；defaultRateLimiter 生产装配不可达经 beans.xml 工厂 bean 注入核实）。

### [C1-004] sitemap 缓存对象经 `getSite()`/`getSiteMaps()` 逃逸内部可变结构（唯一消费方 deepClone 闭环，防御性收口）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/sitemap/SiteCacheData.java:28-34,44-46`；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/sitemap/SiteMapProviderImpl.java:110-114`；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/SiteMapApiBizModel.java:45-53`
- **证据片段**:
  ```java
  // SiteCacheData :44-46 —— 返回缓存内活对象
  public SiteMapBean getSite(String siteId) {
      return siteMaps.get(siteId);
  }
  // SiteMapApiBizModel :49-51 —— 唯一 GraphQL 消费链：先克隆后写（防御闭环所在）
  siteMap = filterForUser(siteMap, includeFunctionPoints);   // filterAllowedMenu :215 site.deepClone()
  siteMap.setSupportDebug(CFG_AUTH_SITE_MAP_SUPPORT_DEBUG.get());
  ```
- **严重程度**: P3
- **并发场景**: `siteCache`（平台 IResourceLoadingCache，按 locale 键）缓存的 `SiteCacheData` 为全部请求线程共享；`getSite`/`getSiteMaps`/`getResourceToRoles`/`getPermissionToRoles` 四个 getter 均返回内部 HashMap/SiteMapBean/ArrayList 活引用。写者仅存在于 `loadSiteData` 构建期（单线程，发布前）与 `refreshCache`（整体 clear，不触碰旧对象）。
- **触发时序**: 理论风险，未具体化（**当前代码库不存在发布后写者**——唯一 mutation 消费链 `SiteMapApiBizModel.getSiteMap` 经 `filterAllowedMenu :215 deepClone()` 后才 `removePermissions/applyAuthFilter/setSupportDebug`，全部作用于克隆；`isPermitted`/`getRolesWithPermission`/`applyAuthFilter(克隆)` 只读）。仅当未来新增消费方对逃逸对象原地写（如对 `getSite(...).getResources()` 返回的 List `sort()` 或对 SiteMapBean set 属性）时：T1 请求线程遍历该 List → T2 原地修改 → CME 或污染全局菜单缓存（全部后续用户 getSiteMap 响应失真，含权限过滤前的资源树泄露面）。
- **现状**: 逃逸载体成立（C1 步骤 1 第 5 类），与批次 1 C1-002（`CredentialType.getFields`）同族同判；消费侧已有单点防御但缓存对象本体未不可变化，防御依赖每个未来消费方自觉克隆。
- **风险**: 即时风险为零；该 API 为 `@Auth(publicAccess = true)` 公开 GraphQL 面，迭代概率高；一旦失守，污染的是权限判定数据源本身（`isPermitted`/`getRolesWithPermission` 读同一缓存对象），属权限面放大。
- **建议**: `loadSiteData` 构建完成后对 `SiteCacheData` 深度不可变化（`siteMaps`/`resourceToRoles`/`permissionToRoles` 包 unmodifiableMap，SiteResourceBean children 包 unmodifiableList），写侧一次性收口；或 `getSite` 返回克隆。对齐批次 1 C1-002 建议口径。
- **失效面枚举**（F3 同族清扫）: 批次 2"getter 泄漏内部可变结构"完整清单——`SiteCacheData.getSite/getSiteMaps/getResourceToRoles/getPermissionToRoles`（本条 4 getter 全列）；`SiteMapProviderImpl.filterAllowedMenu`（deepClone 防御，合格）；`normalizeI18n`（发布前构建期写，合格）；`ChannelBindingInfo`/`TrustedDeviceInfo` 等 DTO 为每请求新建（合格）。批次 1 同族先例 C1-002 状态已列。同族无已触发缺陷。
- **信心水平**: 确定（逃逸事实与"唯一消费方先克隆"均逐一核实）
- **误报排除**: 非"原语使用"误报——登记对象是共享权限缓存对象的表示暴露契约；不升 P2 因写不出当前代码下的具体交错（无发布后写者，不虚报时序）。
- **复核状态**: 已保留（P3；复核员回码核实 `SiteMapApiBizModel.getSiteMap` :49-53 先 filterForUser 后原地 setSupportDebug、`SiteMapProviderImpl:215` deepClone 防御点在场；SiteCacheData 四 getter 返回内部活引用维持）
- **复核注记**: 与 C1-002 同族同判互证成立；`@Auth(publicAccess=true)` 公开面放大论述维持。

### [C1-005] 可信设备登记 max-count 判定 check-then-act 无原子性、无约束兜底

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaTrustedDeviceManager.java:137-180,260-265`
- **证据片段**:
  ```java
  // :151-156 满员判定
  if (countUnexpired(userId) >= CFG_AUTH_MFA_TRUSTED_DEVICE_MAX_COUNT.get()) {
      ...
      return null; // 满员：显式 false 提示（不静默 LRU 淘汰）
  }
  // :158-164 判定与插入之间无锁/无约束兜底（(userId, deviceHash) 唯一约束仅拦同 hash）
  NopAuthMfaTrustedDevice row = dao().newEntity();
  ...
  dao().saveEntityDirectly(row);
  ```
- **严重程度**: P3
- **并发场景**: 无状态单例组件；DB 状态上的 check-then-act。写者 = mfaVerify 成功路径请求线程（`LoginMfaFlow.verifySecondFactorAndComplete :208 trustedDeviceManager.register(...)`，rememberDevice=true）；竞争维度 = 同一 userId 的**不同** deviceHash（同 hash 竞态已由 (userId, deviceHash) 唯一约束 + catch 归一覆盖，:168-175）。
- **触发时序**: T1/T2 = 同一用户两台新设备并发提交 mfaVerify(rememberDevice=true)（如换机期间双端同时登录）→ 均执行 `countUnexpired(userId)` 读得 1（max-count=2）→ 均 :151 通过 → 均 :164 insert 各自不同 hash → 终态 3 行 > max-count=2。无唯一约束/行锁兜底，超限行持久至 TTL-days（固定窗口 30 天）。
- **现状**: javadoc 承诺"新行需未过期行数 < max-count 否则返回 false（满员提示，拒绝静默 LRU 淘汰）"——该契约在并发下非原子，且不像同文件同 hash 路径那样有唯一约束兜底。
- **风险**: 每次竞态至多超出（并发度−1）行，行随固定窗口过期——不构成 F4 无界增长；无安全后果（登记仍需持有该用户有效 challenge + 第二因子凭证）。实际影响 = 豁免名额契约轻微失真 + 管理端展示行数与配置不一致。
- **建议**: 计数与插入放入同一事务并以用户维度行锁串行化（如 `SELECT ... FOR UPDATE` 该用户 trusted-device 集合或独立门闩行）；或接受为已声明边界，javadoc 明示"max-count 为尽力语义，并发窗口可短暂超限"。
- **失效面枚举**（F3 同族清扫）: `MfaTrustedDeviceManager` 内全部 DB check-then-write 点——register 满员路径（本条）；register 同 hash upsert 路径（唯一约束 + catch 归一 :168-175，合格）；isExempted 的 lastUsedAt 命中更新（幂等 last-write-wins，无害）；removeBySid/removeBySidForAdmin/removeAllForUser（删除幂等）。批次 2 其余 check-then-insert 的兜底状态：DbSmsCodeStore.send/DbEmailCodeStore.send/DbLoginAttemptStore.increment/DbSendCodeRateLimiter.incrementCounter（duplicate-key 归一重试，合格）；`UserMfaSelfService.upsertPending`/`confirmWebauthnRegistration`（无归一，输家收约束冲突 → **CL-007**）。
- **信心水平**: 确定（判定与插入之间无同步手段、无兜底约束均经代码与 ORM 模型命名核实）
- **误报排除**: 非已声明边界——javadoc 未声明并发超限语义，守卫缺失正是登记对象；不升 P2 因后果有界（计数上限+TTL 双重封顶）且不在安全判定路径。
- **复核状态**: 已保留（P3，合并裁定并入 C2-004 主登记；复核员回码核实 :151 满员判定与 :164 insert 之间无锁无约束、UK 仅 (userId, deviceHash)（`nop-auth.orm.xml:1564` 实证）——不同 hash 并发超额无兜底成立）
- **复核注记**: **跨维度重复登记**：本条与 C2-004 为同一缺陷（register max-count check-then-act），C2-004 判 P2、本条判 P3，判级分歧按合并裁定归一（见 merge-adjudication.md §四）；F3 清扫中"UserMfaSelfService upsertPending/confirmWebauthnRegistration → CL-007"指针核实成立。

## 维度集外线索（批次 2，overlay C3/C4/C8/C9）

### nop-auth-credential-CL-004（C3 异步链逃逸聚合 + C9 静默吞异常）SSO doLogout 的 IdP 反向登出不在 waitAll 聚合内且异常无处理

- 文件：`nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/login/OAuthLoginServiceImpl.java:198-213`。`promise = doGetUserContext(sessionId)` 后调用 `promise.thenCompose(userContext -> httpClient.fetchAsync(...))`，**thenCompose 返回的新阶段被丢弃**；`FutureHelper.waitAll(Arrays.asList(future, promise))` 只等待本地登出与 userContext 装载，IdP 登出 HTTP 调用可能尚未发起即返回；且该逃逸阶段无 whenComplete/exceptionally——网络失败静默丢失。触发时序：T1 ssoLogoutAsync → 本地 session 置 logout → doGetUserContext 完成 → waitAll 返回 → 后台 IdP logout 失败 → 无日志无重试 → SSO 单点登出半成功（本地已登出、IdP 会话仍活）。建议：`CompletionStage<Void> idpLogout = promise.thenCompose(...)` 并纳入 waitAll + 失败记 ERROR 日志。F3：全文件唯一 doLogout 覆写；`logoutAsync` 直通无同族。

### nop-auth-credential-CL-005（C8 一次性票消费语义不对称）登录级 mfaVerify 的 consume 返回值未判定，TOTP 并发双验证可双签发会话

- 文件：`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/LoginMfaFlow.java:202`。`mfaChallengeStore.consume(request.getChallengeToken())` 返回值被忽略——对照同仓先例：操作级 `OperationMfaCheckerImpl:154` 以 `consume(token) != null` 判定原子消费成功者才放行、`LoginApiBizModel:349` 以 `markVerified` affected 判定并发败者按过期处理。TOTP 场景（30s 窗口码对并发请求复用有效，`MfaFactorVerifier:157-159` 窗口推进为无条件实体写、无双花拦截）下具体时序：T1/T2 并发 mfaVerify 同 challengeToken 同码 → 均过因子验证 → 均调 consume，败者返回 null 被忽略 → **双方均 completeMfaLogin → 一个 challenge 签发两个会话**。SMS/EMAIL 场景被 store CAS/条件 DELETE 在因子层拦截（败者 MISMATCH/EXPIRED），不受影响。风险有限（攻击者需先持有 challenge + 有效第二因子）。建议：consume 返回 null 即抛 CHALLENGE_EXPIRED（对齐操作级裁定）。

### nop-auth-credential-CL-006（C9 半个成功 / F4）Redis 组件"首次递增 + setTimeoutAsync 附着 TTL"两步非原子，TTL 附着失败键永不过期

- 文件族（F3 全列，6 处递增点 + 1 对写）：`RedisLoginAttemptStore.java:71-79`（`v==1L` 才设 TTL；**后果最重**——锁号计数键无 TTL 后永不清理，`resetLoginFailCount` 仅成功登录触发，被锁即永久锁号，需人工 DEL）；`RedisSendCodeRateLimiter.java:87-89,105-106`（日计数键泄漏，按天换键后旧行累积——F4 有界增速无界存量）；`RedisMfaChallengeStore.java:149-153`、`RedisSmsCodeStore.java:105-107`、`RedisEmailCodeStore.java:104-106`（fail 计数键泄漏）；另 `RedisLoginAttemptStore.setLoginFailCount:59-63`（reset 与 setTtl 两步，兼容保留面）。触发链具体：首次递增 INCRBY 成功 → PEXPIRE 因瞬时网络故障抛异常（`FutureHelper.syncGet` 上抛）→ 后续递增 `v>1` 永不再设 TTL，无补偿/修复路径。建议：INCR+PEXPIRE 收敛为单 Lua 原子操作，或 TTL 设置失败重试/后台修复；最低限度对 TTL 设置失败记 ERROR 并补偿删除。与 Local/Db 实现对照：Local 靠 Caffeine expireAfterWrite、Db 靠行内 expire_at 条件写，均无此缺陷——三后端故障模型不对齐。

### nop-auth-credential-CL-007（C4 检查-然后-插入输家无归一，批次 1 CL-003 同族扩展）MFA 绑定面两处并发输家收到约束冲突而非幂等/归一错误

- 文件：`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/UserMfaSelfService.java:538-547`（upsertPending：`setting==null` 判定 → `settingDao.saveEntity(setting)`，PK=userId，无 duplicate-key 归一）；`UserMfaSelfService.java:475-486`（confirmWebauthnRegistration：`findCredentialByCredentialId` 预检后 `credDao.saveEntity(credential)` :480，credentialId 全局唯一冲突未捕获归一）。触发：同一用户双端并发首绑 bindMfa / 同 challengeToken 并发 confirm（败者本应被 consume 拦截，但预检-插入窗口内双双通过）。后果与批次 1 CL-003 同型：数据一致性成立（约束兜底无双行），输家收到未归一约束异常而非幂等成功——对照 `DbSmsCodeStore.send:86-101` 的 duplicate-key 归一先例。登记为 CL-003 同族扩展（F3 清扫补全：MFA 绑定面此前未列），建议统一捕获 `ERR_SQL_DUPLICATE_KEY` 转幂等/归一语义。

---

### C1 维度两批次收官说明

- 批次 1（nop-credential 全量 + nop-auth api/app/dao/meta，41f+31f）与批次 2（nop-auth-service/sso + nop-oauth api/service/dao + api/dao 子模块，123f/17,606 行，其中 oauth-api 1,420 行为纯 DTO/接口、oauth-service/dao 229 行为空壳/retention 薄层）合计完成 C1 维度对任务口径 ROOT 的全覆盖；两处任务口径路径不存在（`nop-credential-core`、`nop-oauth-web`/`nop-auth-codegen`）均已勘误并按实际落位对账。
- C1 维度发现合计：**5 条，全部 P3**（C1-001/002 批次 1，C1-003/004/005 批次 2），无 P0/P1/P2——与两批次 R1 基线"并发原语命中稀疏"预校准一致；认证请求路径的实质并发防护（条件 SQL、Lua CAS、affected-row 判定、unique-constraint 兜底、volatile+临界区复合操作）经逐点核验成立。
- CL 线索合计：**7 条**（CL-001/002/003 批次 1；CL-004~007 批次 2，涉及 C3/C4/C8/C9 overlay），其中 CL-004/005/006 建议复核阶段优先评估（涉及 SSO 登出半成功、票双消费、永久锁号三个具体故障链）。
- 批次 2 全程未读取 ai-dev/audits、plans、bugs、lessons、backlog 历史记录，未 git log；`_tmp/cra-baseline-nop-auth-credential.txt` 仅用于圈定重点。

---
*（本文件为 C1 维度盲审初审产出，含批次 1/2 与 2/2；发现与 CL 均待独立复核。批次 2 审计过程未读取任何 ai-dev/audits、plans、bugs、lessons、backlog 历史记录。）*

---

## CL 线索复核状态（复核员回码）

| CL | 结论 | 复核证据 |
|---|---|---|
| CL-001（Vault 吞 JSON 解析异常丢 cause） | **确认**（可观测性改进，非并发缺陷） | `VaultCredentialKeyProvider.java:344-356` catch 后置 null 统一抛错不带 cause；fail-closed 语义维持 |
| CL-002（reencryptAll javadoc"断点语义"与单事务实现不符） | **确认**（文档-实现不符 + 长事务持有时长面） | `@BizMutation` 单事务语义与 javadoc :640-641 表述对照成立 |
| CL-003（registerUsage/grant 并发输家收约束冲突） | **确认**（与 CL-007/CL-016 同族统一立项） | `CredentialProviderImpl.java:214-228` + `NopCredentialAuthBizModel.java:122-133` check-then-insert 在场 |
| CL-004（SSO doLogout IdP 登出逃逸 waitAll + 异常无处理） | **确认**（复核优先项；回码逐行核实） | `OAuthLoginServiceImpl.java:198-213`——thenCompose 派生阶段被丢弃、waitAll 仅含 [future, promise]、外呼无 exceptionally |
| CL-005（登录级 mfaVerify consume 返回值未判定，TOTP 并发双签发） | **确认，但时序需按 C2-008 修正**（复核优先项） | `LoginMfaFlow.java:202` consume 返回值忽略在场；**修正**：TOTP 路径窗口推进的乐观锁使 T2 在窄带交错下先行失败（见 C2-008），"双方均 completeLogin"仅在 T2 实体装载落后于 T1 提交时仍可达（T2 verify 因窗口已推进而归一失败）——双签发主时序被 C2-008 的版本冲突拦截，本案残余面收窄为 SMS/EMAIL 与"consume-判定分离"的语义缺口，判级建议 P3（原 CL-005 未判级） |
| CL-006（Redis 首次递增 + TTL 附着两步非原子） | **确认**（复核优先项；后果最重的锁号键无 TTL 面成立） | `RedisLoginAttemptStore.java:71-79` v==1 才 setTtl 在场；F3 清单 6+1 处经 C6-round2 补核无第 8 处遗漏 |
| CL-007（MFA 绑定面两处输家无归一） | **确认**（与 CL-003/CL-016 同族） | `UserMfaSelfService.java:538-547` upsertPending 无归一、`:475-486` credentialId 唯一冲突未捕获在场 |

## 维度复核结论

- 复核范围：C1-001…005（初审）+ C1-006（深挖，见 C1-round2.md）+ CL-001…007；复核方式 = 逐条回码（行号/引文/约束面/装配可达性逐一比对 live code），跨维度对照 C2-004/C2-008/C6-002 与 CL 家族。
- 三态判定：**保留 6 / 降级 0 / 驳回 0**。初审 5 条全部维持原判级；深挖新增 C1-006（P3）经独立回码（TOTPAuthenticator:45-65 非 volatile 字段 + MfaFactorVerifier:144-150 请求期写点）成立。
- 降档口径核对：全部 6 条维持"启动期降档不适用请求路径"口径，无滥用降档；无写者/无读点的三条（C1-001/002/004）均如实标注理论风险并落 P3，符合"写不出时序降 P2/更低"纪律（本轮按最低档登记，未虚报 P2）。
- 现状标注：全部为**新发现（未修复）**，仓库内无既有修复/声明边界覆盖（`check2`/`W13`/`W15` 系列注释属既有加固先例，与本轮发现无重叠）。
- 跨单元对照（cra-index，157 条口径）：C1 六条在 cra-index P0/P1/P2 台账中**零同站点重复**；同族形态关联 2 处单列——getter 逃逸族（C1-002/C1-004）与 WI 2.1 `ResourceCacheEntry`/平台缓存族无本体重叠（平台侧为缓存实现，本单元为业务 DTO 表示契约）；C1-006 的"运行期改单例可变字段"与 WI 4.2 `nop-cluster-C1-001`（Nacos 事件线程原地改写共享实例）为跨单元同型（写者不同/字段不同），非同站点，家族关联单列不重复立项。
- 跨维度重复：**C1-005 ↔ C2-004 同一缺陷**（判级 P3/P2 分歧）——移交合并裁定 §四归一，建议 C2-004 为主登记。

## 最终保留项（C1 维度，复核后权威清单）

| 编号 | 标题 | 判级 | 复核状态 |
|---|---|---|---|
| C1-001 | 单例 BizModel 运行期可变计数器 lastNonCv1SkippedCount | P3 | 已保留 |
| C1-002 | registry 缓存 DTO 经 getter 逃逸内部可变结构（无写者，防御性收口） | P3 | 已保留 |
| C1-003 | 两个单例上 5 个 check-then-act 惰性初始化字段 | P3 | 已保留 |
| C1-004 | sitemap 缓存对象 getter 逃逸（唯一消费方 deepClone 闭环，防御性收口） | P3 | 已保留 |
| C1-005 | 可信设备 register max-count check-then-act（**并入 C2-004**） | P3→合并 | 已保留（重复并入） |
| C1-006 | 请求线程运行期写单例 TOTPAuthenticator.skew（非 volatile） | P3 | 已保留 |
| CL-001…007 | 7 条线索（CL-004/005/006 复核优先项全部确认；CL-005 时序按 C2-008 修正） | — | 全部确认 |

统计：**5 保留条目 + 1 条重复并入（C1-005→C2-004）+ 7 条 CL 确认**；P0×0 / P1×0 / P2×0（并入项除外）/ P3×5。
