# WI10b Phase 0 裁定 — IN 列表门禁落点 + BUG-13 死标志三档终裁

> Date: 2026-10-04
> Plan: `ai-dev/plans/23-eql-wi10b-in-list-gate-and-dead-flags.md` Phase 0（纯裁定，不改产品代码）
> Source: roadmap `ai-dev/backlog/eql-cross-db-compat-roadmap.md`「WI10a / WI10b」节 + BUG-13 三档表 + Cross-Cutting；定稿设计 `ai-dev/design/eql-cross-db-compat/2026-10-03-wi10a-in-list-length-policy.md`（active）；live 代码锚点于 2026-10-04 在分支 `feat/eql-cross-db-compat`（WI1-WI5 全部收口后）逐文件复核
> 锚点约定：file:line 为 2026-10-04 实测值；符号定位优先于行号。

## 0. 开工前置与锚点复核（plan Phase 0 最后一项，硬性）

### 0.1 前置 plan 状态核验（排队约束）

| Plan | Plan Status | 核验证据 |
|---|---|---|
| `ai-dev/plans/03-eql-wi1-class-a-defects.md`（WI1） | **completed** ✅ | 文件头 `Plan Status: completed`（Last Reviewed 2026-10-03）；closure audit 证据在其 Closure 段 |
| `ai-dev/plans/19-eql-wi2-class-b-defects.md`（WI2） | **completed** ✅ | 同上（2026-10-03） |
| `ai-dev/plans/20-eql-wi3-dialect-config-fixes.md`（WI3） | **completed** ✅ | 同上（2026-10-03） |
| `ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md`（WI4） | **completed** ✅ | 同上（2026-10-04） |

**结论：四份前置 plan 全部 `completed`，开工门满足，Phase 1 放行。** roadmap 侧 WI1-WI5 亦全部 `done`（2026-10-04 实测 Work Item Status）。

### 0.2 符号锚点 live 复核（相对 plan 起草期 commit `777e822ace` 的漂移登记）

| 锚点 | live 实测（2026-10-04） | 结论 |
|---|---|---|
| `AstToEqlGenerator#visitSqlInValuesExpr` | 存在，`printList(node.getValues(), ",")`（`:847-858`） | ✅ 无界打印现状成立 |
| `AstToSqlGenerator` 无 `visitSqlInValuesExpr` 覆写 | 确认无覆写（`extends AstToEqlGenerator`，`:75`） | ✅ 执行/回显共享同段打印 |
| `EqlTransformVisitor` 无 `visitSqlInValuesExpr` 覆写 | 确认无覆写；`checkWindowFrameFeature` 先例在 `:355`、returning 门在 `visitSqlUpdate :1582`、WI2 full-join 门 `checkJoinTypeFeature` 在 `:607-612`（调用于 `:588`） | ✅ 门禁挂载先例齐备 |
| `EqlCompiler#compile` | transform 已是固定环节（`EqlTransformVisitor` 唯一实例化点 `:62`；`AstToSqlGenerator` 生产实例化点仅 `:73` + 类内 `:116/:125` 打印辅助） | ✅ WI4 落地形态与 plan 预期一致 |
| `EqlTransformVisitor` 构造器 | `EqlTransformVisitor(ISqlCompileContext context)`（`:182`），仅 `EqlCompiler:62` 一处实例化 | ✅ 可安全加参数 |
| `SQL.SqlBuilder#spreadParams` | `SQL.java:728`；上游 `paramCollection :724`、`in(Collection) :523`、`in(owner,name,value) :527`、`paramEx :446-448`；**`CollectSqlOutput.java:52` 直调确认** | ✅ plan 记录的三个消费面全部成立 |
| `FilterBeanToSQLTransformer#visitCompareOp` | `FILTER_OP_IN :167` / `FILTER_OP_NOT_IN :169` → `sb.in(owner,name,value)` | ✅ |
| `CoreConfigs` varRef 先例 | `CFG_CORE_STAT_SQL_CACHE_MAX_SIZE`（`:32-33`，默认 1000） | ✅ |
| `dialect.xdef` features javadoc 块 | `:33-43` 注释块（`@supportUpdateTableAlias` `:35` 等）；属性声明 `:45-57`；既有 `@deprecated` 用法先例 `:218` | ✅ 软弃用落点确认 |
| 14 标志 accessor 现状 | 有 accessor 10 个：plan 原 9 个 + **`isSupportFullJoin`（WI2 已补，`IDialect:110`/`DialectImpl:305`）**；无 accessor 4 个：`supportRightJoin`/`supportDeleteFromJoin`/`supportOutputForUpdate`/`supportNullsFirst`（`_DialectFeatures` Boolean getter 齐：`:424/:233/:367/:348`） | ✅ 与 plan「WI2 将补 fullJoin accessor」预期一致，其余无漂移 |
| `IDialect.isDefaultNullsFirst`（WI3 交付） | `IDialect:138` + javadoc 内书面移交 WI7 的声明 | ✅ WI7 移交链在位 |
| 全仓死标志消费方 grep | 生产消费方仅 2 个：`isSupportFullJoin`（`EqlTransformVisitor:608`，WI2）、`isSupportLateral`（`AstToSqlGenerator:271`，WI1）；`isSupportILike` 消费方仅配置断言测试 `TestIlikeFunctionRegistration:86-90`；`isSupportSequence` 唯一调用方 `TestDialect:148`；其余零消费 | ✅ plan Current Baseline 成立（ WI1/WI2 两处接线为 plan 记录过的在途改动落地） |
| 文法 `sqlInValuesExpr`/`sqlMultiValueExpr` | `BaseRule.g4:163`（`SqlInValuesExpr`，带 `not=NOT?`）/`:216-217`（`SqlMultiValueExpr`）；`sqlExpr_primary :196` 含 `sqlMultiValueExpr` | ✅ NOT IN 同节点、row value 比较可达（见 §2.6） |
| 方言 XML 现值 | `default.dialect.xml:16-23`：`supportUpdateTableAlias="false" supportDeleteTableAlias="false" supportRowValueConstructor="true" supportUpdateFromJoin="false" supportDeleteFromJoin="false" supportILike="false" supportFullJoin="true" supportRightJoin="true"`、`supportTransaction="true" supportSavePoint="true" supportSequence="false" supportTruncateTable="true"`；全 18 个 XML 无任何文件覆写上述 8 键（mssql 唯一 `supportOutputForUpdate="true"` `:104`；h2 唯一 `supportILike="true"` `:26`；tdengine `supportSequence="false"` `:170`） | ✅ |
| 矩阵快照门禁 | `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（2026-10-04 实测） | ✅ 基线绿 |
| 三份 `-pl` 可覆盖语料 | NopJobTask.sql-lib.xml（无 update/delete）；test.sql-lib.xml（`update my_entity set …`/`delete my_entity where …`，**均无别名**）；auth test.sql-lib.xml（无 update/delete） | ✅ 别名 BC 盘点输入（见 §3.2） |

## 1. 死标志 owner 核对表（plan Phase 0 第 1 项，14 个逐个）

| 标志 | 原表 owner | 执行时点落地状态（2026-10-04 实测） | 本 plan 处置 |
|---|---|---|---|
| `supportFullJoin` | WI2 | **WI2 已实施**：accessor（`IDialect:110`/`DialectImpl:305`）+ 编译期门（`EqlTransformVisitor#checkJoinTypeFeature :607`）+ `mysql`/`mariadb`/`mysql5.7` 显式 false + `TestDialectFullJoinFlag` 配置断言 | 已处置（WI2）；本 plan 仅台账登记，不重复实施 |
| `supportILike` | WI10b Phase 0 终裁 | WI3 已收口 **`ilike` 函数注册**（h2/postgresql/duckdb 模板，`TestIlikeFunctionRegistration`）；**标志本身仍零生产消费方**（真实门 = `AstToSqlGenerator#visitSqlLikeExpr` 的 `dialect.getFunction(ILIKE)`，`ERR_EQL_NOT_SUPPORT_ILIKE :566`） | **本节 §2.1 终裁：改档** |
| `supportNullsFirst` | WI7 | WI7 `planned`（未实施）；WI3 已落 `isDefaultNullsFirst` accessor 并在 javadoc 书面移交（`IDialect:127-138`） | successor-owned（WI7）；本 plan 不补 `isSupportNullsFirst`、不建消费点、不写负向用例（无双 owner）；台账记 WI3 移交链中间态 |
| `supportUpdateFromJoin` | WI10b Phase 0 裁定 | 行为不存在：`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` `sqlUpdate` 无 FROM 子句（文法级实测） | **§2.2 终裁：改档（档 2 契约登记）** |
| `supportTruncateTable` | WI10b Phase 0 裁定 | 行为不存在；roadmap「明确延后项」ledger 对 TRUNCATE 进 EQL 永久否决 | **§2.3 终裁：改档（档 2 能力声明）** |
| `supportDeleteFromJoin` | WI10b Phase 0 裁定 | 行为不存在：`sqlDelete` 单表 | **§2.4 终裁：改档（档 2 契约登记）** |
| `supportRightJoin` | WI10b（接线档） | 行为存在且无条件输出（`SqlJoinType.RIGHT_JOIN` 可解析、`AstToEqlGenerator` 打印、`EqlTransformVisitor#markMainSource :531` 已处理）；accessor 不存在；全部 XML 继承 default `true` | **§2.5 终裁：接线**（本 plan） |
| `supportUpdateTableAlias` | WI10b Phase 0 两向终裁 | 半存在：别名可解析 + transform 期真实消费（`visitSqlUpdate :1564` `makeTableAlias`）+ 打印被抑制（`AstToEqlGenerator#visitSqlUpdate :337` `ownerShouldBeIgnored`）——输出 SQL 语义无损 | **§3.1 终裁：改档（档 2）** |
| `supportDeleteTableAlias` | WI10b Phase 0 两向终裁 | 同上（`visitSqlDelete :1623` / `AstToEqlGenerator#visitSqlDelete :376`） | **§3.1 终裁：改档（档 2）** |
| `supportRowValueConstructor` | WI10b（接线档，形态 Phase 0 裁定） | 部分存在：`SqlMultiValueExpr` 三形态文法可达且无条件打印（§2.6 实测） | **§2.6 终裁：接线（EqlTransformVisitor 覆写门）** |
| `supportOutputForUpdate` | WI10b（档 2） | 无消费者；mssql 唯一 true 载体（`mssql.dialect.xml:104`）；xdef javadoc「Update语句支持output子句」 | **§2.7 终裁：候选 B** |
| `supportSavePoint` | WI10b（软弃用） | 零调用方 | 软弃用（xdef javadoc `@deprecated`） |
| `supportTransaction` | WI10b（软弃用） | 零调用方 | 软弃用（同上） |
| `supportSequence` | WI10b（软弃用） | `isSupportSequence` 唯一调用方 `TestDialect:148`（测试保留） | 软弃用（同上） |

**档位汇总**：接线 2（`supportRightJoin`、`supportRowValueConstructor`）；补 accessor+测试证明 1（`supportOutputForUpdate`）；改档 5（`supportILike`/`supportUpdateFromJoin`/`supportTruncateTable`/`supportDeleteFromJoin`/别名两标志，均为档 2 契约或能力登记）；软弃用 3（`supportSavePoint`/`supportTransaction`/`supportSequence`）；已处置（WI2）1；successor-owned（WI7）1。与 roadmap 原文「接线 10 / 补消费者 1 / 软弃用 3」的账面差异在 Phase 3 按 plan 要求原地修订 roadmap 并留注记（EC③ 措辞 + BUG-13 表 + `:29` 行），不是静默偏差。

## 2. 终裁：5 个「行为不存在/部分存在/无消费者」标志（plan Phase 0 第 2 项）

> 硬约束自检：以下每个终裁都点名可核验的处置证据，无为「凑接线数」伪造消费者（No Silent No-Op）。

### 2.1 `supportILike` → 改档（档 2 契约登记 + xdef javadoc 注记；禁止接标志）

**终裁：不做真实接线（不新增标志消费点），改档为档 2。**

- 决定性证据（live）：`postgresql.dialect.xml` 的 `<features>` **未配 `supportILike`（继承 default 的 `false`）**，但 WI3 已在 `postgresql.dialect.xml:193-199` 注册 `ilike` 函数模板并带注释明言「`supportILike="false"` 标志不动：缺的是函数表注册，不是标志翻转，翻标志会引入假阴性。duckdb 经 extends postgresql 传递获得」。**PG/duckdb 今天 flag=false 且 ilike 翻译工作正常**——若把标志接为第二道门，立即破坏两个已收口方言（WI3 的合法交付物）。
- 语义事实：ilike 的真实门是**函数表**（`AstToSqlGenerator#visitSqlLikeExpr :554` 取不到 `getFunction(ILIKE)` 即 `ERR_EQL_NOT_SUPPORT_ILIKE`），标志与函数表是两个独立配置面，强行对齐只会制造第三种状态分叉。
- 处置落地（Phase 2）：① xdef javadoc 块为 `@supportILike` 加注记行（声明「当前无运行时消费点；ilike 的真实门是函数表 + `nop.err.eql.not-support-ilike-operator`；勿将本标志接为第二道门」）；② 契约登记测试已由 WI3 交付（`TestIlikeFunctionRegistration:86-90` 断言 h2=true / postgresql=false / duckdb=false），本 plan 补一条「标志值与函数注册互不强约束」的显式断言测试锁定该契约；③ 台账登记为「改档（契约登记），非已接线」——**台账不得出现「supportILike 已接线（WI3）」**。

### 2.2 `supportUpdateFromJoin` → 改档（档 2 契约登记）

**终裁：不接线（今天不存在可接线行为），改档为档 2 契约登记。**

- 事实：`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` 的 `sqlUpdate` 无 FROM 子句，`UPDATE t SET … FROM …` 解析失败（**响亮失败**，非静默丢 FROM）——无静默错误风险，接线无对象。
- 不转软弃用的理由：该标志描述**真实的方言能力差异**（MySQL/MSSQL 支持 UPDATE..FROM），未来文法 WI（路径 ⓒ，plan-first）立项时必须接线它——软弃用语义（「预留未接线、勿新增消费依赖」）与之不符，契约登记语义（「能力声明 + 触发条件」）与之相符。
- 处置落地（Phase 2）：① 一条文法级契约测试：`update MyEntity set … from …` 解析失败（锁定「语法不存在=响亮」的当前契约，防未来静默退化）；② xdef javadoc 注记（「EQL 文法暂无 UPDATE..FROM；标志为方言能力声明；文法扩展立项（plan-first）时必须接线本标志并补 visitSqlUpdate 消费点与负向用例」）；③ 配置断言测试（default false）。

### 2.3 `supportTruncateTable` → 改档（档 2 能力声明）

**终裁：不接线，改档为档 2 能力声明。**

- 事实：EQL 无 TRUNCATE 语法且 roadmap ledger 永久否决「TRUNCATE 进 EQL」⇒ EQL 内永不会有消费点，接线档永久不成立。
- 不转软弃用的理由：`getDropTableSql`/`getCreateSequenceSql` 先例表明 IDialect 承载非 EQL 的 JDBC 层 SQL 供给；「该库是否支持 TRUNCATE TABLE」对 DAO/工具层是有效能力声明，非废弃项。
- 处置落地（Phase 2）：① 配置断言测试（default true / tdengine false）；② xdef javadoc 注记（「EQL 永不消费本标志（ledger 永久否决 TRUNCATE 进 EQL）；保留为 JDBC/DAO 层能力声明，勿在 EQL 内新增消费」）。

### 2.4 `supportDeleteFromJoin` → 改档（档 2 契约登记）+ 补 accessor

**终裁：不接线，改档为档 2 契约登记；补 `IDialect.isSupportDeleteFromJoin` + `DialectImpl`。**

- 事实：`sqlDelete` 单表，`DELETE t1 FROM t1 JOIN t2 …`（MySQL）/`DELETE FROM t USING t2`（PG）均不可解析——响亮失败，无静默风险。
- 补 accessor 理由：本标志与 `supportUpdateFromJoin` 同族（多表 DML 能力声明），roadmap 原档位表把它列在「接线（10）」且 rebase 规则明写「`isSupportDeleteFromJoin`（若裁定补）」；accessor 齐备后方言能力矩阵可统一从 `IDialect` 读取（`_DialectFeatures` Boolean getter 是冻结内部面）。零生成（手写 retention 链，Cross-Cutting 裁定）。
- 处置落地（Phase 2）：① accessor + `DialectImpl`（照 `isSupportUpdateFromJoin` 同款 `Boolean.TRUE.equals(...)` 读法）；② 文法级契约测试（delete..from/usings 解析失败锁定）；③ xdef javadoc 注记（触发条件=未来 DELETE 多表文法 WI 立项必须接线）；④ 配置断言测试（default false）。

### 2.5 `supportRightJoin` → 接线（EqlTransformVisitor join 门 + 补 accessor）

**终裁：接线档成立，本 plan 实施。**

- 门形态：扩展 `EqlTransformVisitor#checkJoinTypeFeature`（WI2 已建方法，`:607-612`）增加 `RIGHT_JOIN` 分支：`source.getJoinType() == SqlJoinType.RIGHT_JOIN && !dialect.isSupportRightJoin()` → `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + `FEATURE_SUPPORT_RIGHT_JOIN`（`OrmEqlConstants` 新常量）。调用点不动（`:588` 已在 join 表源转换遍历点）。不打印器、不回显。
- accessor：补 `IDialect.isSupportRightJoin()` + `DialectImpl`（照 `isSupportFullJoin` 同款读法，javadoc 写明报错契约，先例 `isSupportLateral`/`isSupportFullJoin`）。
- 负向载体裁定：**合成方言**（`TestEqlTranslationSemantics#buildDialect` 先例——模块测试环境手工 DialectModel 置 `features.setSupportRightJoin(false)`）。理由：全部 18 个 XML 现值继承 default `true`，而 RIGHT JOIN 是 MySQL 8.0.31/PG/H2/MSSQL/Oracle/DuckDB 全支持的标准能力（一手文档共识），**无任何真实方言应为 false**——给某 XML 配 false 需要一手证据，没有；按 plan Phase 0 该项的裁定规则（「无一手证据则用合成方言」）取合成方言。正向载体 = 任一真实方言编译 `a right join b` 照常。
- BC 影响为零：所有真实方言 true ⇒ 门只对显式配 false 的部署生效。

### 2.6 `supportRowValueConstructor` → 接线（EqlTransformVisitor#visitSqlMultiValueExpr 覆写门）

**终裁：接线档成立，门落 `EqlTransformVisitor#visitSqlMultiValueExpr` 新覆写。**

- 可达性实测（文法分析 + Phase 2 测试证明）：`sqlExpr_primary :196` 含 `sqlMultiValueExpr` ⇒ 三形态全部可达且 `AstToEqlGenerator#visitSqlMultiValueExpr :915-918` 无条件打印：① `(a,b)` 独立值表达式；② `(a,b) = (1,2)` 真行值构造器比较（`SqlBinaryExpr_compare` 两侧各一个 `SqlMultiValueExpr`）；③ `(a,b) IN ((1,2))`（`SqlInValuesExpr` 的元素本身是 `SqlMultiValueExpr`）。
- 门形态：`EqlTransformVisitor` 新覆写 `visitSqlMultiValueExpr`：`!dialect.isSupportRowValueConstructor()` → `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + `FEATURE_SUPPORT_ROW_VALUE_CONSTRUCTOR`，然后 `super` 继续（门在编译管线，覆盖三形态全集——不需要按「比较上下文」收窄，三形态在该方言上同样是非法输出）。`AstToSqlGenerator` 零改动（不在打印器设门）。
- BC 影响为零：全部 XML 继承 default `true`；负向载体 = 合成方言 false（同 §2.5 理由：无真实方言应配 false——全支持方言的 row value constructor 是标准能力，无一手证据支持任何 false 配置）。
- 与 IN 门禁的交互（plan 点名核对）：本门与 IN 长度门同在 EqlTransformVisitor，**计数与门禁互不干涉**——`SqlMultiValueExpr` 不是 `SqlInValuesExpr`，IN 门只数 `SqlInValuesExpr#getValues()`；`(a,b) IN ((1,2))` 的 IN 门计数对象是外层列表（1 个元素），内层行组归本门管，两门正交。

### 2.7 `supportOutputForUpdate` → 候选 B（补 accessor + 测试证明），v1 不做 OUTPUT lowering

**终裁：候选 B。** 与 roadmap 档 2 原文「补一个真实消费点**或**补测试证明」一致。

- 理由：候选 A 需要先设计 MSSQL `OUTPUT inserted./deleted.` 形态（与 returning 投影的 lowering 分叉、`mssql` 未配 `supportReturningForUpdate` ⇒ returning 在 mssql 被拒的既有行为交叉），v1 无消费需求（EQL 现无任何语句产出 OUTPUT 子句）；候选 B 以 accessor + mssql 配置断言 + owner doc 语义登记满足档 2 证据要求。
- 落地（Phase 2）：① 补 `IDialect.isSupportOutputForUpdate()` + `DialectImpl`；② `TestDialectOutputForUpdateFlag` 配置断言（mssql true、其余继承 false）+ 行为断言（mssql 上 `update..returning` 仍被 `supportReturningForUpdate=false` 拒绝——锁定「OUTPUT 标志当前不改变任何行为」的契约）；③ owner doc 语义登记（Phase 3）。
- **显式豁免记录（硬性）**：候选 B 无生产消费点，`IDialect.isSupportOutputForUpdate` 的「接线验证（Minimum Rules #23）运行时消费」验证**显式豁免**。理由：消费点的存在前提（MSSQL OUTPUT lowering）被 v1 裁定不做；`scan-hollow-implementations.mjs` 的 P5「unused public methods」未实现（实测规则面 P1/P2a/P2b/P3/P3b/P4/P6/P6b/P8），探不到「有 accessor 无消费者」形态，本豁免由 Phase 2 Exit Criteria 的候选 B 手工 gate 把关。升级触发条件：EQL 立项 MSSQL OUTPUT 语义（文法 WI，plan-first）时必须消费本标志。

## 3. 别名两标志两向终裁（plan Phase 0 第 4 项，审查 Major-4）

### 3.1 `supportUpdateTableAlias` / `supportDeleteTableAlias` → 改档（档 2：行为契约登记 + 配置断言 + xdef 行为注记），**不建编译期门**

**两向裁定的选择：改档。编译期门被否决。**

- 否决编译期门的完整理由：全部 XML 现值 `false` ⇒ 门 = **全方言拒绝今天可解析的 `update tbl a set …` / `delete tbl a where …` 写法**。而该写法今天的实际行为是**语义无损的归一化**：transform 期别名注册进 table scope（`o.col` 可解析，`:1564`），打印期抑制别名（`AstToEqlGenerator` `ownerShouldBeIgnored`，`:337/:376`）——单表 UPDATE/DELETE 输出 SQL 与手写无别名形式完全等价，**没有静默错误**（不是 A 类：输出语义正确）。设门 = 纯 BC 破坏、零可移植性收益、零缺陷修复，且与 roadmap「P1 静默错误 > 响亮失败」的排序原则不符（被拒的不是错误 SQL，是合法输入的无害糖衣）。打印器按 flag 输出的形态已被审查 Major-4 否决（静默抑制违反无静默跳过），两向中只剩改档。
- 改档后的诚实定位：EQL **接受**别名（文法 + transform 都消费它）、输出**统一抑制**别名——这是「接受并归一化」，与标志字面义「是否允许」在 EQL 语境下的有效语义一致（EQL 层面确实允许，只是不回显）。两个 accessor 保留为能力声明。
- 处置落地（Phase 2）：① 配置断言测试（全部 XML 显式或继承 false，含 `default.dialect.xml:19` 的显式 false）；② 正向行为测试（`update MyEntity o set o.status=? where o.id=?` 在 false 方言编译通过、输出无别名、语义正确——锁定「接受并归一化」契约）；③ xdef javadoc 注记（行为契约 + 「勿按 flag 在打印器分支输出——静默抑制形态已裁定不成立」）。
- **BC 影响清单（plan 硬性要求，2026-10-04 实测）**：三份 `-pl` 可覆盖语料均**不含** update/delete 别名写法——NopJobTask.sql-lib.xml 无 update/delete 语句；test.sql-lib.xml 的 `update my_entity set a=…`/`delete my_entity where …` 无别名；auth test.sql-lib.xml 无 update/delete。⇒ 改档处置对语料零影响；编译期门方案若被采纳也零语料影响（但用户面 BC 仍在，此为否决门方案的独立理由之一）。
- 未来触发条件：若文法 WI 使 update/delete 别名语义承载化（多表 UPDATE 等），标志必须接线（门落 `EqlTransformVisitor#visitSqlUpdate/#visitSqlDelete` 编译期，非打印器）。

## 4. IN 列表门禁挂载点终裁

### 4.1 EQL 路径 → `EqlTransformVisitor#visitSqlInValuesExpr` 新覆写（plan 候选 A）

- 裁定：候选 A（transform 覆写），否决 `EqlCompiler#compile` 后置遍历（候选 B）。理由：① `checkWindowFrameFeature`/`checkJoinTypeFeature`/returning 门三代先例同层，风格一致；② transform 期有 AST 节点（`.source(node)` 定位）与 dialect 上下文，错误信息可定位；③ 后置遍历需二次 visit 全树，纯增成本无收益；④ WI4 后 transform 是固定环节，缓存路径（`useCache=true`）下随首次编译执行一次、非缓存路径逐次确定性重查，两形态行为一致（设计 §3.4 约束满足）。
- 实现约束：门在 `super.visitSqlInValuesExpr(node)` 语义前执行 check-first；计数 = `node.getValues().size()`（字面量与 marker 统一，不逐元素分型）；**不区分方言**（不读任何 dialect 能力）；不触碰 `AstToEqlGenerator`/`AstToSqlGenerator` 任何打印方法（回显零变化）。
- 语句名 param：`EqlTransformVisitor` 构造器追加 `name` 参数（`EqlCompiler:62` 传入 `compile` 的 `name`）——该类全仓唯一实例化点已实测（§0.2），签名变更零外溢。错误 param 携带 dialect（EQL 路径可提供，`dialect.getName()`）。

### 4.2 `SqlBuilder` 路径 → `SQL.SqlBuilder#spreadParams` 全局拦（设计 §3.4 候选一）

- 裁定：`spreadParams` 全局拦，否决 `FilterBeanToSQLTransformer` 仅 filter 路径拦，**`paramCollection`-only 亦否决**。完备性事实（2026-10-04 全仓 grep 实测，§0.2）：`spreadParams` 的调用面 = `paramCollection :725`（← `in(Collection) :524`、`in(owner,name,value) :527`、`paramEx :446-448`）+ **`CollectSqlOutput.java:52` 直调**。filter-only 挂载漏掉直接调用 `sb.in(...)` 的用户代码与 `CollectSqlOutput` 路径；paramCollection-only 漏掉 `CollectSqlOutput:52` 直调（plan 已点名该事实）。`spreadParams` 是唯一全覆盖单点。
- **计数口径（显式定义，plan 硬性要求）**：计 `spreadParams` 收到的 **Collection 顶层元素个数**（`c.size()`）。理由与边界：
  - 标量集合（filter `IN` 的实际形态）：顶层元素数 == 展开后 `?` 数，口径与 EQL 侧「IN 列表表达式个数」严格一致；
  - `Object[]`/嵌套 `Collection` 元素（行组 `(a,b),(c,d)` 形态）：按 **1 个元素**计——行组是 `spreadParams` 的既有输出形态（INSERT VALUES / 行值构造器入参），其「每个行组的元数」不属 IN 列表长度契约；语句级参数总数（行组展开后的 `?` 合计）已裁定为 Deferred（设计 §3.3 unverified，本 plan Non-Goal）；
  - **与 `supportRowValueConstructor` 处置的交互核对**：行组门是 EQL 侧 `SqlMultiValueExpr` 门（§2.6），模块、节点、语言面均不同；`spreadParams` 不新增行组语义判断（不读方言、不改写输出形态），两处置互不矛盾——IN 门只数不改，行组形态原样输出。
- 空集合行为不变：`c == null || c.isEmpty()` → `append("(NULL)")` 先于门禁（既有 `(NULL)` 语义保留，设计 §3.4）。
- 报错契约：`CoreErrors` 新码，param = 顶层元素数 + 阈值（`SqlBuilder.name` 若已设置则附 `name`——不强制，见 §5）；不静默、不改写。

## 5. 错误码归属与命名终裁（plan Phase 0 第 7 项）

| 项 | EQL 路径 | SqlBuilder 路径 |
|---|---|---|
| 归属 | `OrmEqlErrors`（`nop.err.eql.*`） | `CoreErrors`（`nop.err.core.*`）——两码不共用（设计 §3.4） |
| 码名 | `nop.err.eql.in-list-too-large`（常量 `ERR_EQL_IN_LIST_TOO_LARGE`） | `nop.err.core.sql.in-list-too-large`（常量 `ERR_CORE_SQL_IN_LIST_TOO_LARGE`） |
| param | `itemCount`（实际元素数）、`maxItems`（阈值）、`stmtName`（语句名，新增 `ARG_STMT_NAME`）、`dialect`（`ARG_DIALECT` 复用） | `itemCount`（新增 `ARG_ITEM_COUNT`）、`maxItems`（**复用既有** `ARG_MAX_ITEMS`，`CoreErrors:164`）、`name`（**复用既有** `ARG_NAME`，`CoreErrors:30`；builder 未命名时不带该 param） |
| 消息语言 | **中文** | **中文** |

⚠️ **执行期偏差登记（对 plan 原文的显式偏离）**：plan Phase 0 写「错误消息英文（AGENTS.md 错误处理规约）」。owner doc `docs-for-ai/02-core-guides/error-handling.md:120` 的现行裁定是：**存量中文模块的新增业务错误码延续中文描述、不经 i18n 断裂，且不要在同文件内中英混排**；`OrmEqlErrors`/`CoreErrors` 均为存量中文模块，WI1/WI2/WI3 在本系列新增的码（`ERR_EQL_COLLECTION_ACCESS_NOT_SUPPORT`/`ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT` 等）全部中文。按「docs-for-ai 为 source of truth + 与同文件既有码一致」裁定取**中文描述**，AGENTS.md 的英文要求适用于不走 `ErrorCode` 的异常字符串消息（本 plan 不产生此类消息）。此偏离记入 daily log。

## 6. 配置载体终裁（plan Phase 0 第 8 项）

- 键名：`nop.core.sql.in-list-max-size`；常量名 `CFG_CORE_SQL_IN_LIST_MAX_SIZE`；载体 `nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java`，`varRef(s_loc, "nop.core.sql.in-list-max-size", Integer.class, 1000)` 模式（照 `CFG_CORE_STAT_SQL_CACHE_MAX_SIZE :32-33` 先例）。
- 默认 1000；**无关闭取值**：配置为整数、无哨兵语义；且算术上任何 ≤0 取值的效果是「拒绝全部 IN 列表」（元素数恒 ≥1 > 阈值）而非「关闭检查」——结构上不存在关闭取值。`@Description` 写明「最小有效值为 1，小于 1 将拒绝所有 IN 列表」。
- 两条检查路径同读这一个键（`IConfigReference.get()`：未配置回落默认 1000）——契约「默认阈值同一配置项」满足。不再新增任何其他配置键（一个键的授权记录 = WI10a 设计 2026-10-03 定稿 §3.1/§3.5）。

## 7. 各 Phase 执行要点汇总（本裁定文档的「照此可直接写代码」自检）

1. Phase 1：`CoreConfigs` +1 配置项（§6）；`OrmEqlErrors` +1 码 +2 param 常量（§5）；`CoreErrors` +1 码 +1 param 常量（§5）；`OrmEqlConstants` +`FEATURE_SUPPORT_RIGHT_JOIN`/`FEATURE_SUPPORT_ROW_VALUE_CONSTRUCTOR` 不在 Phase 1（属 Phase 2 门，Phase 1 只需 IN 相关）——**勘误**：IN 门不使用 feature 常量（门禁方言无关，无 feature 名），`OrmEqlConstants` Phase 1 零改动，两 feature 常量随 Phase 2 门一并落。EQL 门挂 `EqlTransformVisitor#visitSqlInValuesExpr`（构造器 +name）；SqlBuilder 门挂 `spreadParams`（顶层元素计数，空集合先行）。五条测试 + 配置调高接线验证 + filter IN 端到端。
2. Phase 2：`supportRightJoin`（§2.5：常量 + accessor + checkJoinTypeFeature 扩展 + 合成方言负向 + 真实方言正向）；`supportRowValueConstructor`（§2.6：常量 + visitSqlMultiValueExpr 门 + 合成方言负向）；`supportOutputForUpdate`（§2.7：accessor + mssql 断言，豁免已记录）；`isSupportDeleteFromJoin` accessor（§2.4）；改档四标志的契约/断言测试 + xdef javadoc 注记（§2.1/2.2/2.3/2.4/§3.1）；软弃用 3 个 xdef `@deprecated` 行 + 配置加载回归。方言 XML 零改值 ⇒ 矩阵快照登记「无需更新」。
3. Phase 3：owner doc IN 契约表 + 迁移说明（§5/§6 的键名码名逐一 grep 可对账）+ 能力门登记表（`checkJoinTypeFeature` 扩展行、`visitSqlMultiValueExpr` 门行、IN 两门行）+ 死标志台账（§1 汇总档位）；roadmap 四处修订（WI10b EC③ 措辞改「接线 2 / 补 accessor+测试证明 1 / 改档登记 5 / 软弃用 3 / WI2 已处置 1 / WI7 successor-owned 1」口径、BUG-13 表、`:29` Current Baseline 行、`:94` 状态行 planned 不动——本 plan 不翻 done；EC④ 软弃用数仍 3，无需改）。
