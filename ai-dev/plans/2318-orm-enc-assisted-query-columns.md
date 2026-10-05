# 2318 nop-orm 可检索加密：enc 列的辅助查询列（assisted/like）

> Plan Status: active（2026-10-05 独立 plan audit APPROVE，4 Minor 已修正，见 Draft Review Evidence）
> Last Reviewed: 2026-10-05
> Source: `ai-dev/analysis/2026-10/2026-10-05-shardingsphere-vs-nop-orm-gap-analysis.md`（缺口清单 P2 #6）+ 用户需求：通过 ORM 元数据扩展属性声明加密列关联的确定性字段与分列字段，自动实现 ShardingSphere encrypt 的 assistedQuery/likeQuery 三列机制
> Related: `ai-dev/design/nop-orm-scope-and-non-goals.md`、`ai-dev/plans/2307-eql-live-defect-and-dead-surface-remediation.md`

> Draft Review Evidence（WI0，2026-10-05）：独立 auditor（fresh session）对 live code 抽查 30 项核心事实全部 PASS（含 GenSqlHelper 16 处 binders 逐处对账、EqlCompiler 72-105 编译序列、bulk-update 裁定五处记录一致性、Phase 0 裁定项对下游引用全覆盖、Exit Criteria 全部 repo-observable）；4 Minor 已修正：Phase 2/4 补 owner-doc 裁定行、SqlParamTypeResolver 行号校正（marker-first 实为 `:69-74`、SimpleParamBuilder 回落实为 `:124-128`）、TestColumnEnhancer 注释行号校正（`:36-38`）、CollectionOperatorTransformer 表述校正（compile 包独立类、由 visitor 调用）。裁定：APPROVE 转 active。

## Purpose

让 `tagSet="enc"` 的透明加密列在保持存储加密的同时支持等值/模糊检索：通过列上的扩展属性与角色标签声明"确定性辅助列（assisted）"与"模糊辅助列（like）"，框架自动维护辅助列值并自动改写等值/LIKE 查询，形成 nop 版的三列加密模型。Protected Area 注意：本计划涉及 ORM 模型语义扩展，按 AGENTS.md 属 plan-first，实施前须通过 plan audit。

## Current Baseline

以下事实均经 2026-10-05 live code 核实（探索代理 + 本人抽查）：

1. **透明列加密已有**：列标签 `TAG_ENC="enc"`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmConstants.java:49`）→ `DefaultOrmColumnBinderEnhancer`（`orm-defaults.beans.xml:36` 缺省装配）以 `EncodedDataParameterBinder` 包装列绑定器：写时 `ITextCipher.encrypt` + `@enc:` 前缀落 VARCHAR，读时自动解密，stdSqlType 变 VARCHAR；缺省 `AESTextCipher`（AES/GCM，v1 版本化格式：每条密文随机 IV + PBKDF2 派生密钥）。
2. **等值检索空缺已被测试钉住**：`nop-orm/src/test/java/io/nop/orm/dao/TestColumnEnhancer.java:25-56`（`testEncryptedColumn`）断言同明文两次保存密文不同、等值查询返回 0 属预期（:36-38 注释明确 per-message IV 后等值检索失效）。
3. **扩展属性机制可用且有实证**：xdef 规定带命名空间的属性缺省保留为扩展属性（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef:24-26`），经 `DslBeanModelParser`（ext 属性处理约 `:87-97`）→ `SerializableExtensibleObject`（`setExtProp`/`getExtProp`，`:79-90`）到达运行期；先例：nop-sys 列上 `ext:dict="core/enabled-locale"`（`_app.orm.xml:188`）、实体上 `ext:auto-upgrade-database` 经 `prop_get` 消费（`OrmConstants.java:161`、`DataBaseSchemaInitializer.java:86`）。注意 `IColumnModel` 接口本身不暴露 `getExtProp`，读取经 `prop_get`/`IExtensibleObject`（`IColumnModel extends IEntityPropModel extends IPropGetMissingHook`）。**声明辅助列无需改 xdef、无需触碰 codegen 生成链。**
4. **派生列写值的统一挂点存在**：`EntityPersisterImpl.queueSave/queueUpdate`（`:455-469`/`:471-490`）是"实体快照 + 列模型全可见、且 INSERT/UPDATE SQL 尚未生成"的单点（`OrmTimestampHelper.onCreate/onUpdate` 即在同点自动填充审计列）；`IOrmInterceptor.preSave` 先于 persister（`OrmSessionImpl.flushSave:1020-1031`、`interceptPreSave:1111-1125`）。
5. **查询有两条产出路径，但 QueryBean 路径也编译为 EQL**（`DaoQueryHelper.java:78-93` 拼 EQL 文本），故 EQL 编译期是主改写点：
   - 逻辑列→物理列解析：`EqlTransformVisitor.resolveColName`（`:1431` 起）+ `EntityTableMeta.getFieldExprMeta/makeColumnMeta`（`:274-299`，携带该列增强后的 binder）。
   - **compile-time AST transformer 扩展点现成，但本计划是首个生产实现**：`IEqlAstTransformer`（before/after analyze，`EqlCompiler.java:72-83` 消费；注入链 `SessionFactoryConfig.eqlAstTransformer` → `OrmSessionFactoryBean` → `SessionFactoryImpl.compileSql` 三个重载 → `LoadedOrmModel` null 回退）经 SessionFactory 可注入；注意两点——全仓目前**没有**任何生产 `IEqlAstTransformer` 实现（仅测试 stub），且 Baseline 提到的 `CollectionOperatorTransformer` 是 `io.nop.orm.eql.compile` 包的独立变换器（由 `EqlTransformVisitor` 调用）、**不经**该扩展点。故接线验证（Minimum Rules #23）为本 Phase 强制项。
   - **执行期参数变换形态现成，且接入缝唯一**：`SqlParamTypeResolver` 先优先消费 `SqlParameterMarker` 携带的自定义 param builder（`visitSqlParameterMarker` 的 marker-first 分流，实际 `:69-74`，先例 `TenantParamBuilder`），仅当无自定义 builder 时回落 `SimpleParamBuilder`（实际 `:124-128`，即当前 enc 列等值参数不经加密的原因）。时序约束：参数列表在 `EqlTransformVisitor.transform`（before/after analyze 之间的语义分析）中固化，`transformAfterAnalyze` 阶段已无法影响参数——**参数变换必须经 before-analyze 阶段的 AST/SqlParameterMarker 注入实现，不得在 after-analyze 改 resolvedExprMeta 或直接改 params**。
   - `GenSqlHelper` 直拼物理 SQL 路径（按主键 load、findByExample/updateByExample、实体固定 filter）：`binders[...]` 引用共 16 处（12 处字面 `binders[col.getPropId()]` + 4 处等价变体：`:101` 租户列、`:324`/`:840` `binders[propId]`、`:377` join 列）+ `appendEq`（`:743`）/`appendEqMarker`（`:750`）；Phase 4 改写核对以 16 处完整清单为准。
   - LIKE 谓词输出无列改写钩子（`AstToSqlGenerator.java:622-642`，方言无 ILIKE 抛 `ERR_EQL_NOT_SUPPORT_ILIKE`）；`escape` 子句被两个输出器忽略（`AstToEqlGenerator.java:867-871`）。
6. **DDL 与升级已支持模型加列**：模型声明的新列自动进 CREATE TABLE（`DdlSqlCreator.java:63-77`）；auto-upgrade 自动 `ALTER TABLE ADD COLUMN`（`OrmDbDiffer.createColumnDiffDdls:376-396`、`DataBaseUpgrader`，开关 `nop.orm.db-differ.auto-upgrade-database`，`OrmConfigs.java:66`）。
7. **密码学基建**：`HashHelper.hmacSha256`（`:91-93`）为现成确定性 keyed 摘要；`ITextCipher` 唯一实现 `AESTextCipher` 非确定性（随机 IV），不可直接用作辅助列值函数。配置项 `nop.crypt.default-salt/default-iv/default-enc-key`（`CommonConfigs.java:58-64`）。
8. **测试基建**：`AbstractOrmTestCase`（H2 内存库 + `OrmSessionFactoryBean` + `setColumnBinderEnhancer` + `DdlSqlCreator.createTables` 建表）；测试模型 `SimsExam.examName tagSet="enc"`（`nop-orm/src/test/resources/_vfs/nop/test/orm/app.orm.xml:124-128`）。
9. **SS 对标（master 5.5.4-SNAPSHOT）**：三列模型 cipher/assistedQuery/likeQuery；master 内置 assisted 算法仅 MD5（`MD5AssistedEncryptAlgorithm`），**like 算法已全部删除**——like 列语义无可照抄的上游实现，须自定义契约。
10. **gap**：全仓无任何辅助列机制——无元数据声明、无派生值维护、无查询改写、无回填。

## Goals

- 列模型可通过扩展属性 + 角色标签声明 assisted/like 辅助列关联；模型加载期校验，配置错误 fail-fast（错误码）。
- INSERT/UPDATE 时框架自动派生辅助列值（确定性 keyed HMAC；like 列按契约摘要），用户代码零感知；辅助列拒绝用户直接赋值。
- EQL/QueryBean 与 GenSqlHelper 两条路径：enc 主列等值（=、IN）谓词自动改写到 assisted 列并变换参数；LIKE 按契约改写；不可检索的谓词形态（范围、ORDER BY 等）显式报错，无静默错误结果。
- 存量库经既有 auto-upgrade 自动加列；提供幂等回填工具重算存量辅助列。
- 端到端：声明 → 保存 → 等值查询 → LIKE 查询 → 加列回填 → 再查询，全链路测试可用。
- owner docs（orm-model-design.md、eql-and-database-compatibility.md）与 design 契约文档同步。

## Non-Goals

- 范围谓词（`>`、`<`、`BETWEEN`）、`ORDER BY`/`GROUP BY`/`JOIN ON` 涉及 enc 列的检索下推——任何实现都意味着全表解密扫描，明确不支持（fail-fast）。
- 裸 SQL / sql-lib `<sql>` 文本的自动改写（原生 SQL 本就不经 ORM 元数据；这是与 SS JDBC 层作用面的本质差异，接受）。
- assisted 列可逆加密（采用确定性 HMAC 摘要，不提供解密）。
- 算法注册 SPI、多辅助列、每租户独立密钥（第一版固定：1 个 assisted + 1 个 like，密钥沿用 `nop.crypt.*` 配置体系）。
- 脱敏/mask 引擎（另立项）、`EncodedDataParameterBinder` 既有 v1 密文格式与兼容矩阵的任何改动。
- `updateByExample` 等 bulk UPDATE SET 涉及 enc 列时的框架级辅助列维护（2026-10-05 用户裁定：归业务层校验；代价是限制必须在 owner doc 显式声明，见 Deferred But Adjudicated）。
- ShardingSphere 式 JDBC 层通用 SQL 改写。

## Scope

### In Scope

- `nop-persistence/nop-orm-model`：声明解析与模型校验。
- `nop-persistence/nop-orm`：flush 派生值维护、GenSqlHelper 路径改写、回填工具。
- `nop-persistence/nop-orm-eql`：EQL 编译期谓词改写与参数变换。
- `nop-kernel/nop-commons`：如需新增确定性摘要工具方法（复用 `HashHelper.hmacSha256` 优先）。
- 测试：`nop-orm`/`nop-orm-eql` 测试模型、focused tests、端到端测试。
- 文档：`ai-dev/design/`、`docs-for-ai/02-core-guides/orm-model-design.md`、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`。

### Out Of Scope

- xdef schema 变更与 codegen 生成链改动（扩展属性机制已足够，见 Baseline #3；若实施中发现必须改 xdef，先回到 plan 层重新裁定）。
- nop-metadata / nop-graphql 层的联动脱敏或加密。

## Execution Plan

### Phase 0 - 契约与设计决策

Status: completed（2026-10-05，契约落 `ai-dev/design/nop-orm-enc-assisted-columns.md`）
Targets: `ai-dev/design/nop-orm-enc-assisted-columns.md`（新）

- Item Types: `Decision`

- [x] 裁定元数据声明形态：主列上的扩展属性指向辅助列属性名 + 辅助列上的角色标签（对齐 `ext:dict`/`ext:auto-upgrade-database` 先例），并裁定辅助列在模型中的声明要求（VARCHAR、非主键、建议 `not-pub`）
- [x] 裁定 assisted 列值契约：keyed HMAC-SHA256 的密钥来源（`nop.crypt.*` 体系或新配置）、明文规范化规则（trim/大小写折叠与 `ignoreCase` 的对齐）、编码与定长输出
- [x] 裁定 like 列值契约：LIKE 谓词的最小可检索语义（全模糊 `%x%` 的子串摘要规则、前缀/后缀模式、`ILIKE`/`escape`/`NOT LIKE` 的支持边界），及该语义的泄露面说明
- [x] 裁定查询改写语义矩阵：等值/IN → assisted 列 + 参数摘要；LIKE/ILIKE → like 列；范围/ORDER BY/GROUP BY/JOIN ON 涉及 enc 列时的错误码清单；辅助列是否允许出现在用户查询中
- [x] 裁定存量回填契约：解密 cipher 列 → 重算辅助列的批处理入口、幂等性、批大小配置
- [x] 裁定辅助列用户直接赋值的检测时机与语义（如 flush 期检测/设值期拒绝），保证任何绕过派生逻辑的赋值路径非静默
- [x] 记录既定裁定（2026-10-05 用户）：`updateByExample` 等 bulk UPDATE SET 涉及 enc 主列的场景**不在框架层处理**（不重算、不报错），由业务层校验负责；框架侧义务收窄为 owner doc 显式声明该限制（Phase 5 强制项）
- [x] 预声明 like 契约允许的收缩边界与回写规则：若裁定收缩 LIKE 支持面（如仅支持特定通配符形态），必须同步回写本 plan 的 Goals 与 Closure Gates 文本（留痕），禁止执行期静默放宽或让 Closure Gate 不可达成
- [x] 每项裁定记录被拒绝的替代方案及理由（design 文档要求）

Exit Criteria:

- [x] design 文档按 `ai-dev/design/00-design-writing-guide.md` 落盘，全部契约裁定项（含既定裁定的记录）均有明确理由与被拒替代方案
- [x] design 文档只含行为语义与契约，不含类签名/实现伪代码（Minimum Rules #14）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（2026-10-05 实测）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 1 - 元数据解析与模型校验

Status: completed（2026-10-05）
Targets: `nop-persistence/nop-orm-model`、`nop-persistence/nop-orm`（session factory 加载路径）

- Item Types: `Feature`

- [x] 加载期解析主列扩展属性与辅助列角色标签，建立关联关系（经 `prop_get`/`IExtensibleObject` 读取，对齐 Baseline #3 读取先例）
- [x] 模型校验 fail-fast：辅助列必须存在于同一实体且为 VARCHAR 单列；主列必须含 `enc` 标签；辅助列不得再声明辅助关联；重复声明报错；错误码登记进模块错误体系（英文文案）
- [x] 校验发生在 session factory 初始化路径（启动即报错，不得推迟到首次查询）
- [x] 测试：合法声明通过；每类非法声明命中对应错误码（不少于 4 类）
- [x] `app.orm.xml` 测试模型增加 assisted/like 声明样例
- [x] `docs-for-ai/02-core-guides/orm-model-design.md` 新增"可检索加密声明"小节（声明语法 + 校验规则）

Exit Criteria:

- [x] 上述校验项各有 focused JUnit 测试且通过（Minimum Rules #25）——TestEncAssistedModelValidation 10 测试（合法 + 8 类非法错误码逐断言），nop-orm-model 56 全绿
- [x] 无静默跳过：所有非法配置显式抛错（Minimum Rules #24）
- [x] owner doc 更新已完成；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0——orm-model-design.md 新增"可检索加密声明"小节（含 bulk-update 限制声明）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 写路径派生值

Status: planned
Targets: `nop-persistence/nop-orm` 持久化 flush 链路（Baseline #4 挂点）

- Item Types: `Feature`

- [ ] 在 Baseline #4 的统一挂点按 Phase 0 契约为辅助列赋值：主列有值 → 派生；主列置 null → 辅助列 null；主列更新 → 辅助列同步重算
- [ ] 辅助列用户直接赋值时按 design 裁定显式失败（错误码），不允许静默覆盖或静默忽略
- [ ] 测试：insert/update/置 null/清空 四路径下，裸 SQL 读 DB 辅助列值与契约摘要一致（扩展 `TestColumnEnhancer` 或新增测试类）
- [ ] 加密兼容回归：enc 主列读写与 v1 密文格式行为不变（`TestColumnEnhancer` 全部既有断言保持通过）

Exit Criteria:

- [ ] H2 上 save → 裸 SQL 断言辅助列 DB 值 = 契约摘要；update/null 路径同
- [ ] **接线验证**（Minimum Rules #23）：端到端测试断言派生逻辑在 flush 链路被真实调用（辅助列 DB 值变化），而非仅方法存在
- [ ] 无静默跳过（Minimum Rules #24）
- [ ] owner-doc 裁定：写路径派生语义并入 `docs-for-ai/02-core-guides/orm-model-design.md` 的"可检索加密声明"小节，归 Phase 5 收口统一核对（本 Phase 无独立文档交付）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 查询改写：EQL/QueryBean 路径

Status: planned
Targets: `nop-persistence/nop-orm-eql`（compile-time AST transformer，经 `IEqlAstTransformer` 注入）、`nop-persistence/nop-orm`（注入与执行接线）

- Item Types: `Feature` + `Decision`

- [ ] 等值/IN 谓词改写：enc 主列上的 `=`/`IN` → assisted 列 + 参数按契约摘要变换。**接入缝约束（Baseline #5 时序）**：改写须经 before-analyze 阶段完成（AST 列替换 + `SqlParameterMarker` 注入自定义 param builder，执行期由参数解析优先消费）；`transformAfterAnalyze` 阶段参数列表已固化，不得作为接入点
- [ ] LIKE/ILIKE 谓词改写按 Phase 0 契约；`NOT LIKE`/`escape`/不支持的谓词形态按 Phase 0 矩阵显式失败。注意 H2 内存库无 ILIKE 函数（编译期即抛 `ERR_EQL_NOT_SUPPORT_ILIKE`）：若契约含 ILIKE，其命中行为以编译级改写断言 + 方言语义等价测试覆盖，e2e 命中断言限于 H2 可执行的形态
- [ ] **Decision**：改写与 EQL 查询计划缓存的交互——验证改写只依赖静态元数据、不受参数影响。验收形式：同一 EQL 文本两次编译（第二次命中缓存）后，不同参数值的摘要变换仍各自正确（缓存命中路径的参数变换断言）
- [ ] QueryBean 过滤路径回归验证（该路径编译为 EQL，应自动继承改写）
- [ ] 查询结果实体加载的解密路径回归（`transformRow` 解密仍正确）

Exit Criteria:

- [ ] EQL 编译级测试：`o.examName = ?` 与 `LIKE` 谓词编译产物含辅助列、参数为契约摘要
- [ ] **端到端验证**（Minimum Rules #22）：`AbstractOrmTestCase` H2 上 保存 → EQL 等值查询命中 → LIKE 查询命中 → 不支持的谓词抛对应错误码，全链路单测通过
- [ ] **接线验证**（Minimum Rules #23）：测试证明 transformer 经编译链真实消费（如注入标志/计数断言），非仅注册存在
- [ ] 既有 EQL 回归（含 plan 2307 固化的跨模块回归清单中 persistence 相关模块）全部通过
- [ ] `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 补充改写语义与限制清单
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 查询改写：GenSqlHelper 路径

Status: planned
Targets: `nop-persistence/nop-orm` `GenSqlHelper` 及其调用方（findByExample/updateByExample/实体固定 filter）

- Item Types: `Feature`

- [ ] 该路径的等值过滤在 enc 主列上改写到 assisted 列（当前 eq 参数走增强 binder 的隐式行为由显式改写取代，语义与 Phase 3 矩阵一致）；核对以 Baseline #5 的 16 处 `binders[...]` 完整清单为准（含租户列、join 列变体）
- [ ] 不支持的过滤形态按同一错误码矩阵显式失败
- [ ] 测试：example 查询/固定 filter 按 assisted 列命中（裸 SQL 断言生成 SQL + 结果断言）

Exit Criteria:

- [ ] 覆盖 findByExample 与实体固定 filter 各至少一个 focused 测试
- [ ] 无静默跳过（Minimum Rules #24）
- [ ] owner-doc 裁定：GenSqlHelper 路径改写语义并入 `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 的限制清单，归 Phase 5 收口统一核对（本 Phase 无独立文档交付）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - DDL、回填与文档收口

Status: planned
Targets: `nop-persistence/nop-orm`（回填工具）、`nop-dbtool-core`（验证面）、owner docs

- Item Types: `Feature` + `Proof`

- [ ] 验证 auto-upgrade：模型新增辅助列后 `DataBaseUpgrader` 自动 ADD COLUMN（focused 测试证明）
- [ ] 回填工具：存量行解密 cipher 列 → 按契约重算辅助列，幂等可重复执行，批大小可配
- [ ] 回填端到端：预置存量密文行 → 回填 → 等值/LIKE 查询命中
- [ ] owner docs 收口：`orm-model-design.md`、`eql-and-database-compatibility.md` 与最终行为一致性核对；design 文档从 draft 转终稿（去掉演进叙事，Minimum Rules #14）
- [ ] owner doc 显式声明 bulk update 限制：enc 列**不支持**经 `updateByExample` 等 bulk UPDATE 更新（辅助列不维护，归业务层校验）——2026-10-05 用户裁定的文档化义务
- [ ] 全量回归：`./mvnw test -pl nop-persistence/nop-orm-eql,nop-persistence/nop-orm,nop-persistence/nop-orm-model -am`，并按 plan 2307 固化的跨模块回归清单覆盖 EQL 下游消费模块

Exit Criteria:

- [ ] auto-upgrade 与回填各有 focused 测试；回填幂等（重复执行结果一致）有断言
- [ ] **端到端验证**（Minimum Rules #22）：存量密文 → 回填 → 查询命中全链路通过
- [ ] owner docs 更新完成且链接检查退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭前逐条勾选；流程见 guide 的 When Closing The Plan 与 Closure Audit Rule。

- [ ] 三列机制端到端可用：声明 → 保存（辅助列自动派生）→ EQL/QueryBean 等值查询 → LIKE 查询 → 加列回填 → 再查询
- [ ] 所有 in-scope 配置错误与不可检索谓词均显式失败，无静默跳过/静默错误结果（scope 边界：bulk UPDATE SET 限制见 Deferred But Adjudicated 裁定与 owner doc 声明）
- [ ] 既有 enc 行为（v1 密文格式、读写解密、`TestColumnEnhancer` 既有断言）无回归
- [ ] owner docs（orm-model-design.md、eql-and-database-compatibility.md）与 live baseline 一致
- [ ] 独立子 agent closure audit 已完成并记录证据（含 Anti-Hollow 检查：派生挂点与查询改写在运行链路真实连通）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2318-orm-enc-assisted-query-columns.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-persistence/nop-orm --severity high` 退出码 0
- [ ] `./mvnw test -pl nop-persistence/nop-orm-eql,nop-persistence/nop-orm,nop-persistence/nop-orm-model -am` 通过
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### bulk UPDATE SET 涉及 enc 列的辅助列维护（`updateByExample` 等）

- Classification: `removed from scope through a recorded scope change`（2026-10-05 用户裁定：归业务层校验，框架层不处理）
- Why Not Blocking Closure: 这是显式裁定而非遗漏——平台对模型契约限制的既有处理方式即 owner doc 声明 + 业务层把关；该限制已列为 Phase 5 强制文档项，静默面收敛为"未读文档的误用"，与其他模型级契约限制（如 EQL 不支持的语法显式失败清单中的裁剪项）同级
- Successor Required: `no`

（草稿期无其他延期项；执行中如出现延期项须按 guide 补 Classification 与 Why Not Blocking Closure）

## Non-Blocking Follow-ups

- 辅助列值函数的算法注册 SPI 化、多辅助列支持（第一版固定契约之外的泛化）
- 脱敏/mask 引擎（另立项，见 gap analysis P1 #3）

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
