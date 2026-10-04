# 38 EQL WI12 — P2 长尾（nowait 接线 / FOR UPDATE NOWAIT·SKIP LOCKED / SAVEPOINT·ROLLBACK TO 裁定 / FROM (VALUES…) / AT TIME ZONE / 窗口命名 WINDOW 回归）

> Plan Status: active
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI12 节 + 共同约束 1-4 + 门控矩阵 WI12 行 + Cross-Cutting 全部 + 关键约束与红线节，v7.2）；Current Baseline 全部代码事实于 2026-10-04 在本工作区分支 `feat/eql-cross-db-compat` 逐文件实测核验（H2 支持度：`FOR UPDATE NOWAIT/SKIP LOCKED` 经本地 m2 仓库 h2-2.4.240 jar 的 `ForUpdate.class` javap 实证；VALUES/AT TIME ZONE 以 H2 官方文档为出处、真库探针于 Phase 4/5 落地——非转引调研报告）
> Related: `ai-dev/plans/03-eql-wi1-class-a-defects.md`（硬前置，已 `completed`；BUG-3 锁模板接线 + LockOption 边界注记先例）、`ai-dev/plans/37-eql-wi7b-analytic-features.md`（硬前置，已 `completed` 2026-10-04；altLabel+构建期归一化与 ⓒ 尾部追加 ordinal 纪律先例）、`ai-dev/plans/24-eql-wi5-sql92-function-syntax.md`（结构先例：altLabel 备选 + 归一化 + 逐方言 template + 真库证据面）

## 硬前置声明（开工门，非空话）

roadmap 门控矩阵 WI12 行明文硬前置 = **WI1、WI7b**（依赖图另经 WI7b 传递依赖 WI7/WI5/WI6/WI4/WI0）。实测现状（2026-10-04）：WI1 已 `completed`（BUG-3 文法+逻辑 commit `43483a10ce`）；WI7 已 `completed`（plan 26）；**WI7b 已于 2026-10-04 由独立 closure audit 一次通过并翻 `done`**（`ai-dev/logs/2026/10-04.md` 收口条目 + plan 37 Closure 段）。**WI12 是 roadmap Work Item Status 最后一个 `todo`，无排队门** —— 本 plan 过 draft review 后 roadmap WI12 行即可 `todo` → `planned`（由执行者操作，非本文件职责）。WI12 无 G-1..G-4 直接门控（门控矩阵 WI12 行硬前置不含 G 门；若 Phase 0/4 取证引出新的版本门需求——如 MariaDB SKIP LOCKED 版本边界——按 G-1 同款纪律登记，不自行假设承诺基线）。

## Purpose

收口 roadmap 最后一个工作项 WI12（S-11 批次）的 P2 长尾五项 + 一项回归验证：

1. **接线 `SqlQuerySelect.nowait` → `AstToSqlGenerator`**（该 AST 字段零手写消费；本 WI 自带此项）。
2. **`FOR UPDATE NOWAIT` / `SKIP LOCKED` 语法**（前置 = WI1 BUG-3 已改走 `dialect.getForUpdateSql()`，**已满足**）。
3. **`SAVEPOINT` / `ROLLBACK TO`** —— Phase 0 取证后裁定**落地或移出+successor，不允许名义 owner**（本 plan Current Baseline 已给出裁定建议 = 移出，见「与 roadmap 的差异登记」第 3 条与 Phase 3）。
4. ~~窗口命名 `WINDOW`~~ —— 已由 `add-stream-sql` 建成，本 plan 只做**回归验证**（roadmap WI12 交付 4 已划除，本 plan 对应 Phase 6）。
5. **`FROM (VALUES …)`** —— Phase 0 定价后落地（预判 ⓒ 新 AST 类）或按约束 3 显式移出。
6. **`AT TIME ZONE`** —— WI5 同款 altLabel + 构建期归一化 + 逐方言 `<template>`（预判零新 AST 节点、零新 lexer token）。

每项语法具备：断言正确方言 SQL 文本的正向用例（红→绿）、不支持方言编译期响亮报错的负向用例、真库（H2）执行证据、owner doc 覆盖表；既有 EQL 语料 BC 三份必测逐字一致；独立 closure audit。

## Current Baseline

> 所有锚点为 2026-10-04 实测值；`EqlParser.java`/`_EqlASTBuildVisitor.java` 行号会随后继文法 Phase 重生成漂移，执行时以符号定位为准。⚠️ **起草评审期实测发现：本工作区存在未提交的并行文法改动**（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/_EqlASTBuildVisitor.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlErrors.java` 等处于 M 状态——WI8/WI9b 实施中），评审期内锚点已漂移两轮（如 `sqlWindowClause` 277→286→295）。⇒ 本 plan 全部 file:line 仅为**信息性快照**，Phase 0 设「全量锚点符号重定位」强制项（照 plan 37 先例），执行以符号（规则名/方法名/常量名）复核为准。

### 硬前置已落地的基础设施（本 plan 直接消费）

- **WI1 BUG-3 锁模板接线**：`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java:355-363` 的 `printForUpdate` override——执行路径消费 `dialect.getForUpdateSql(null)`（`:356`），空模板编译期抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（feature=`forUpdate`，`:358-360`）；javadoc `:349-352` 明文「LockOption 暂传 null（AST 只有 boolean forUpdate，**NOWAIT/SKIP LOCKED 归 WI12**）」。回显路径 `AstToEqlGenerator.printForUpdate`（`:480-481`、`:505`）保持硬编码不动（WI1 裁定，BC 判据）。方言配置面：`default.dialect.xml:39` `<forUpdate>for update</forUpdate>`（h2/oracle/pg/mysql 等继承）；`duckdb.dialect.xml:44` `<forUpdate x:override="replace"/>`（空 = 不支持 → 编译期报错）。测试先例：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlForUpdateDialectGate.java`（`testEmptyForUpdateTemplateThrowsAtCompileTime` `:168`、`testConfiguredTemplateIsConsumed` `:176`——**自定义模板 `" for update nowait"` 尾缀断言**、`testEchoPathStaysHardcoded` `:184`）。
- **WI1 LockOption 边界注记（WI12 必须回补的债）**：`ai-dev/plans/03-eql-wi1-class-a-defects.md:324-325` Non-Blocking Follow-ups 明文——「WI12 接 `FOR UPDATE NOWAIT`/`SKIP LOCKED` 时回头补 LockOption 传递」；`:191` 记录 `getForUpdateSql(LockOption)` 以 null 调用安全（`DialectImpl.java:522-523` 直读 `dialectModel.getSqls().getForUpdate()` 配置文本，LockOption 参数**当前被忽略**）。`LockOption`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/lock/LockOption.java`）= lockMode + timeout，静态常量 NONE/OPTIMISTIC/PESSIMISTIC_READ/PESSIMISTIC_WRITE；其真实消费方是 `GenSqlHelper.java:196`（`getLockHintSql`）+ `:202`（`getForUpdateSql`）的 ORM 实体锁路径。
- **`<sqls>` 配置承载先例（retention 手法，零生成）**：`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/DialectSqls.java`（extends 冻结的 `_DialectSqls`）已有两个手写字段先例——WI1 的 `sqlIntervalLiteral`（javadoc+字段 `:13-19`）与 WI3 的 `intersectKeyword`（`:21-28`，含 `none` 哨兵值面契约）；`dialect.xdef` 的 `<sqls>` 段在 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef:110-180`（`<forUpdate>` 在 `:161`）。
- **WI7b 的 ⓒ 纪律先例**：`EqlAST.xjava` 类定义顺序 = 生成 ordinal 顺序，**新 AST 类必须落文件尾部**（WI7b 执行期教训：`SqlGroupingOp` 初版中插致 ordinal 顺延，已移至尾部重生成；`ai-dev/logs/2026/10-04.md` Phase 2 条目）。`EqlASTKind.java` 现有 **68 项（ordinal 0-67，`SqlGroupingOp`=67 尾部）**——禁止把 ordinal 持久化或跨版本比较（roadmap 红线）。
- **WI5 altLabel + 构建期归一化先例**（AT TIME ZONE 的结构模板）：`sqlExpr_special` 备选分支 + label 前缀约束（`AstGrammarBuilder.getAstNodeName` 取 label 首段下划线前缀）+ 手写 visit 完全重写不调 super + 逐方言 `<template>` 注册（`oracle.dialect.xml:186` ilike 先例）；未注册方言 transform 层 `ERR_EQL_UNKNOWN_FUNCTION` 响亮（`EqlTransformVisitor.java:1561-1564`）。`TemplateSQLFunction` marker 越界加载期校验已由 WI0 落地（`TemplateSQLFunction.java:56-62`）。

### WI12 五项的现状（2026-10-04 逐项实测）

- **① `SqlQuerySelect.nowait` 字段**：AST 模型 `nop-persistence/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava:167`（`boolean forUpdate;` `:166` / `boolean nowait;` `:167`）。生成物 `_SqlQuerySelect.java:31`（字段）、`:112-119`（getter/setter）、`:275`（clone）、`:521`（isValueEquivalent）、`:597`（toJson）——**全部是生成代码；手写侧零消费（grep 全仓 0 命中）**，且文法侧**零 population**：`forUpdate_` 规则只有 `FOR UPDATE`（`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4:256-258`），`nowait` 从未被任何解析路径写入。`NOWAIT`/`SKIP`/`LOCKED` 三词在 `nop-persistence/nop-orm-eql/model/antlr/Keyword.g4`、`nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`、`nop-persistence/nop-orm-eql/model/antlr/Literals.g4` **均无 token**（词边界 grep 0 命中）——今天按 `IDENTIFIER_` 词法化。
- **② `FOR UPDATE NOWAIT`/`SKIP LOCKED`**：文法无备选（同上，`forUpdate_: FOR UPDATE`）；`FOR UPDATE` 本体已走方言锁模板（WI1）——**本项前置已满足**。新语法需要新 token（NULLS/ROLLUP/CUBE 三件套先例：`nop-persistence/nop-orm-eql/model/antlr/Literals.g4` 尾部追加、序号顺延零删除、不删任何既有 token）+ `unreservedWord_` 双登记（WI7b 守护先例：作标识符仍可解析）。
- **③ `SAVEPOINT`/`ROLLBACK TO`**：**EQL 入口文法已有事务语句层**——`nop-persistence/nop-orm-eql/model/antlr/Eql.g4`（60 行）：`sqlStatement: sqlDmlStatement | sqlTransactionStatement`（`:28-30`）；`sqlTransactionStatement: sqlCommit | sqlRollback`（`:53-55`；`sqlCommit: COMMIT` `:57-58`、`sqlRollback: ROLLBACK` `:60-61`；DDL/TCL 备选在 `sqlDmlStatement` 内为注释态 `:38-50`，TCL/DDL/DCL grammar import 亦注释 `:20`）。AST 侧 `SqlCommit`/`SqlRollback`/`SqlTransactionStatement` 类存在（`SqlRollback.getStatementKind()=ROLLBACK`），`SqlStatementKind` 枚举含 COMMIT/ROLLBACK。**但打印侧是 no-op**：`EqlASTVisitor.java:667-675` 的默认 `visitSqlCommit`/`visitSqlRollback` 只 visit decorators、无任何 print 调用——`AstToEqlGenerator`/`AstToSqlGenerator` 均无 commit/rollback override（结构推断：`commit` 经 `EqlCompiler`（`EqlCompiler.java:76-79` 走 `AstToSqlGenerator.visit`）的编译产物必然不含 COMMIT 文本；未跑执行探针，如需执行级证据在 Phase 3 分支 A 裁定时补一行探针取证）。`SAVEPOINT` token 存在于 `Keyword.g4:349` 但**零 parser 规则引用**；`ROLLBACK TO x` 今天解析失败（响亮）。方言侧 `supportSavePoint`（`IDialect.java:75`、`DialectImpl.java:262-263`）已被 **WI10b 软弃用**：xdef `@deprecated`「预留未接线、保留配置兼容、**勿新增消费依赖**」，owner doc 死标志台账锁定（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md:716`，回归载体 `TestDialectDeadFlagDisposition.testSoftDeprecatedFlagsStillBind`）。
- **⑤ `FROM (VALUES …)`**：`sqlTableSource`（`DMLStatement.g4:145-151`）只有四备选——`sqlSingleTableSource`/`sqlSubqueryTableSource`（`LATERAL? LP_ query=sqlSelect RP_`，`:164-166`）/`sqlTumbleTableSource`/join——**无 VALUES 表源分支**，`from (values (1)) t` 今天解析失败（响亮）。既有 `sqlValues_`（`DMLStatement.g4:46-48`）是 **INSERT 单行**语义（`VALUES LP_ expr (COMMA_ expr)* RP_`），AST 类 `SqlValues` 持有的是**单行扁平 `List<SqlExpr>`**（`_SqlValues.java:17-19`）——**不能直接承载多行 VALUES 表源**（行结构的行列表 ≠ 扁平列表）。
- **⑥ `AT TIME ZONE`**：无文法分支（`AT` token 存在于 `SQL92Keyword.g4:238` 但**零 parser 规则引用**；`ZONE` 已在 `unreservedWord_`（`BaseRule.g4:97`）；`TIME` 是类型名 token（`BaseRule.g4:481` 的 datatype 词表）——**`AT`/`TIME`/`ZONE` 三 token 词法层齐备，预计零新 token**）。`expr AT TIME ZONE tz` 今天解析失败（响亮）。
- **④ 窗口命名 `WINDOW`（回归验证面，全部已建成）**：文法 `sqlWindowClause`（`BaseRule.g4:295`，漂移中）+ `sqlWindowExpr` 的 `OVER windowName` 引用备选（`:256-262`，`windowName=` 在 `:259`）；AST `SqlWindowDecl`（ordinal 55）/`SqlWindowClause`（ordinal 56）；transform 层 `ERR_EQL_UNKNOWN_WINDOW_NAME` + frame 三道能力门（`EqlTransformVisitor.java:378-394`，`isSupportWindowFrameRows/Range/Groups`）；测试面四层——parse 层 `TestEqlWindowGrammarParse`（`testOverNamedWindowRef` `:67`、`testWindowClause` `:74`）、SQL 文本 golden `nop-persistence/nop-orm-eql/src/test/resources/__snapshot/{dialect}.window.sql`（10 方言：dm/duckdb/mariadb/mssql/mysql/mysql5.7/oracle/postgis/postgresql/h2gis + 5 frame 方言，载体 `TestDialectWindowSqlSnapshot.java:42-50`，含 WI7b 补的 `testOverWithoutPartitionText`）、H2 真库 `TestH2WindowFrameMatrix`（nop-dao）与 `TestH2WindowFrameCompileEndToEnd`（nop-orm-eql）。

### H2 真库支持度（本 plan 真库执行证据面的基座；2026-10-04 对本地 m2 仓库 h2-2.4.240 jar 逐 class 实证）

- **FOR UPDATE NOWAIT / SKIP LOCKED：支持**。`org.h2.command.query.ForUpdate` 含静态常量 `DEFAULT`/`NOWAIT`/`SKIP_LOCKED`（javap 实证，独立复验为真）；`getSQL()` 渲染 `" FOR UPDATE"`/`" WAIT"`/`" NOWAIT"`/`" SKIP LOCKED"`（javap 常量池）；语法形态（`[ FOR UPDATE [ NOWAIT | WAIT secondsNumeric | SKIP LOCKED ] ]`）以 H2 官方文档 SELECT 语法为准（jar 嵌套资源 `org/h2/util/data.zip!org/h2/res/help.csv` 2.4.240 同文，仅作起草期旁证——help.csv 非jar 顶层条目、须解包 data.zip 方可见）。⚠️ H2 还支持 `WAIT n`——**roadmap WI12 只点名 NOWAIT/SKIP LOCKED，`WAIT n` 不在本 plan 范围**（Non-Blocking Follow-up 登记）。
- **VALUES 表源：支持——证据来源 = H2 官方文档 + 真库探针（Phase 4 落地）**。官方文档要点：grammar「Other Grammar/Table Expression」表源含 `( query )`、「Other Grammar/Query」= `select | explicitTable | tableValue`、commands「Table value」= `VALUES rowValueExpression [,...]`（"A list of rows that can be used like a table"，列名为 C1、C2…），官方 MERGE 示例 `MERGE INTO TARGET T USING (VALUES (1, 4), (2, 15)) S(ID, V)` ⇒ `FROM (VALUES …) alias(col,…)` 预期可执行；**起草期未跑真库探针，Phase 4 交付时以 `JdbcTestCase` 真库执行为准**。
- **AT TIME ZONE：支持——证据来源 = H2 官方文档 + 真库探针（Phase 5 落地）**。官方文档 grammar「Other Grammar/Time zone」：`AT { TIME ZONE { intervalHourToMinute | intervalHourToSecond | string } | LOCAL }`（"A time zone. Converts the timestamp with or without time zone into timestamp with time zone at specified time zone"）；**起草期未跑真库探针，Phase 5 交付时真库执行 + `TestDialect.testFunctions` 为准**。
- ⚠️ **版本钉死义务**：以上 jar 证据取自 2.4.240；本仓测试基座 `JdbcTestCase`（`nop-persistence/nop-dao/src/test/java/io/nop/dao/jdbc/JdbcTestCase.java:112-113`）硬编码 H2 内存库，**Phase 0 必须钉死 CI 实际解析的 H2 版本**（`mvn dependency:list -pl :nop-dao`）并复核：ForUpdate.class 常量实证 + VALUES/AT TIME ZONE 官方文档取证在该版本成立——2.x 系均预期成立，若实际解析为 1.4.x 则 NOWAIT/SKIP LOCKED/AT TIME ZONE 的 H2 真库证据面需要重估（Phase 0 门）。

### 方言矩阵快照（调研 v3 矩阵 `ai-dev/analysis/2026-10/2026-10-02-eql-cross-database-compatibility-syntax-research.md` 抄录——NOWAIT/SKIP LOCKED 现状行 `:750`、SAVEPOINT `:808`、AT TIME ZONE `:818`、VALUES `:821`（细则行 `:740`）；列序 = MySQL8/MariaDB/PG/Oracle/MSSQL/H2/DB2/DM8/DuckDB/SQLite；⚠️ 调研矩阵非一手证据——Phase 0 逐格一手取证后方可写入 owner doc，`unverified` 格按红线不计覆盖率）

| 语法 | 关键分叉（Phase 0 取证义务的输入） |
|---|---|
| `FOR UPDATE NOWAIT`/`SKIP LOCKED` | MySQL 8 ✅ / MariaDB ✅（NOWAIT/SKIP LOCKED 各自版本边界须取证）/ PG ✅ / Oracle ✅（`NOWAIT|WAIT n|SKIP LOCKED`）/ MSSQL ❌（锁提示 `WITH(UPDLOCK,READPAST)` 体系，非 FOR UPDATE 子句变体）/ H2 ✅（ForUpdate.class javap 实证）/ DB2 未取证 / DM8、TDengine `[未取证]` 红线 / DuckDB ❌（无悲观锁，`<forUpdate>` 空模板——WI1 已置编译期报错，NOWAIT 负向用例天然复用该方言） |
| `FROM (VALUES …)` | MySQL 8.0.19+ ✅ **但行形态是 `VALUES ROW(1),ROW(2)`（硬分叉）** / MariaDB ❌ / PG ✅ / Oracle ⭕（调研称 23ai+；否则需 UNION ALL 链 lowering——一手取证）/ MSSQL ✅（须带列别名 `AS t(c1,c2)`）/ H2 ✅（官方文档+真库探针 Phase 4）/ DB2 ✅ / DuckDB ✅ / SQLite ⭕（调研称仅 compound SELECT 形态——一手取证）/ DM8 ⭕⚠️ unverified |
| `AT TIME ZONE` | PG ✅ / Oracle ✅（23ai 另有 `AT LOCAL`）/ MSSQL ✅ 2016+ / H2 ✅（官方文档+真库探针 Phase 5）/ DB2 ✅ / DuckDB ✅（调研；一手取证）/ MySQL ⭕（仅 `CAST(ts AT TIME ZONE 'UTC' AS DATETIME)`，UTC only、8.0.22+——**不无损，不注册**）/ MariaDB ⭕ / SQLite ❌ **永无** / DM8、TDengine `[未取证]` 红线 |
| `SAVEPOINT`/`ROLLBACK TO`（SQL 文本层） | 调研矩阵 10 库全 ✅ —— 但**裁定焦点不在 DB 支持，而在 EQL 层归属**（见「与 roadmap 的差异登记」第 3 条） |

### 生成管线事实与语料 BC

- `nop-persistence/nop-orm-eql` 生成管线是活的：`.g4`/`EqlAST.xjava` 改动经 `precompile/gen-eql-parser.xgen` + `gen-eql-ast.xgen` 原地重写 `EqlParser.java`（**起草复核时 11870 行，信息性**——工作区存在 WI8/WI9b 未提交文法改动，行号漂移中）、`_EqlASTBuildVisitor.java`（**1989 行，信息性**）、`ast/_gen/`（**现 78 文件**）、`EqlLexer*`/`Eql.interp`/`*.tokens`；`EqlASTKind/Visitor/Processor/Optimizer` 在生成链上（`//__XGEN_FORCE_OVERRIDE__` = 每次强制覆盖，**不得手改**）。重生成命令 `./mvnw generate-sources -pl :nop-orm-eql -am`。
- **`EqlASTKind` ordinal 红线**：新增 AST **类**才触 ordinal（既有 68 项一一对应类，给既有类**加字段**不动 ordinal）；新类必须落 `EqlAST.xjava` **尾部**（WI7b 教训）。**禁删任何 lexer token**（删除会重编号全部 token 类型）。
- **既有 EQL 语料 BC**：三份必测（`nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/sql/NopJobTask.sql-lib.xml`、`nop-auth/nop-auth-service/src/test/resources/_vfs/nop/auth/sql/test.sql-lib.xml`、`nop-persistence/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml`）的 **`<eql>` 正文**对 `savepoint`/`nowait`/`skip locked`/`at time zone`/`values` **grep 0 命中**（2026-10-04 实测）。ⓘ 精化限定：`test.sql-lib.xml:27` 的**普通 `<sql name="insert">` 模板块**（非 `<eql>`，不走 EQL 解析）含 `values (${a},${b},'a',${d})` 命中——与本 plan 新增的表源备选**无解析冲突**（该文件不经 EQL parser），回显文本仍由 `TestEqlCorpusBaseline` 棘轮逐字比对兜底。本 plan 各语法的可解析集合是纯增量，BC 风险面极小。`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份不在任何单 WI `-pl` 内，由 roadmap 级 EC 第 8 条 + WI0 静态快照覆盖；**禁止声称「7 个全测」**。
- **全绿口径**：`./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C`（roadmap WI12 EC ⑤ 命令）——含 `:nop-auth-service` 会拖入 stream/batch/job-local/task-core 闭包；豁免 = roadmap Cross-Cutting 5 项负载敏感 flake（类级排除）+ `-Dtest` 否定式排除必须连带 `!JSONPointTest_0,!JSONPointTest_1`（WI7b clean install 实跑登记）。「全绿」= 本 WI 相关模块全绿 + 既有失败与基线一致。

## Goals

- 交付 ①：`SqlQuerySelect.nowait` 获得生成器消费路径——`nowait=true` 时按方言配置渲染锁后缀文本，未配置方言**编译期响亮报错**（不静默丢弃）；正向用例证明接线真实落地（roadmap WI12 EC ② 的「独立正向用例」）。
- 交付 ②：`FOR UPDATE NOWAIT` / `FOR UPDATE SKIP LOCKED` 语法落地（文法 + token + visitor + 生成器变体），NOWAIT/SKIP LOCKED 在不支持方言上编译期报错；LockOption 引入形态由 Phase 0 裁定并回写 WI1 遗留注记（plan 03 `:325`）。
- 交付 ③：`SAVEPOINT`/`ROLLBACK TO` **逐条裁定**——落地（语法+生成器+门）或显式移出（ledger 登记 + roadmap WI12 节原地注记 + successor 路径），不允许名义 owner。
- 交付 ⑤：`FROM (VALUES …)` 按 Phase 0 定价落地（预判 ⓒ 新 AST 类 + 尾部追加 ordinal + 支持方言原生输出/不支持方言编译期报错）或按约束 3 显式移出（同上纪律）。
- 交付 ⑥：`AT TIME ZONE` 按 WI5 先例落地（归一化 + 逐方言 `<template>`；未注册方言 `ERR_EQL_UNKNOWN_FUNCTION` 响亮）；新注册模板函数在 H2 `TestDialect.testFunctions` 真库执行全绿。
- 交付 ④（回归验证）：窗口命名 `WINDOW` 四层既有覆盖（parse/golden/transform 门/真库）在本 plan 生成物重生成后**保持全绿**，并给出「已由 add-stream-sql 建成、本 plan 仅回归收口」的审计记录。
- 语料 BC：三份必测与 WI0 基线快照逐字一致；方言能力矩阵快照增量同步（`<template>` 注册 = 真增量；`<sqls>` 配置 = 零增量核实）；owner doc 同步（EC ④）。
- 独立 closure audit（不同 task_id，evidence 写入本 plan Closure 段）（EC ⑦）。

## Non-Goals

- **不做 `FOR UPDATE WAIT n`**（H2/Oracle 支持，roadmap WI12 只点名 NOWAIT/SKIP LOCKED；登记 Non-Blocking Follow-up）。
- **不动 `ROLLBACK`/`COMMIT` 既有文法与 `SqlStatementKind` 枚举**（除非 Phase 3 裁定落地 SAVEPOINT 且裁定需要；打印侧 no-op 是既有现状，本 plan 不顺手修——若 Phase 3 裁定移出，则该 no-op 面登记为观察项）。
- **不做 ORACLE 的 `FROM (VALUES …)` → UNION ALL+DUAL 链 lowering**（若 Phase 4 裁定落地，形态=支持方言原生输出 + 不支持方言响亮报错；UNION ALL 链需要 per-dialect 无 FROM 查询的 dual 表配置体系，属约束 3 的不无损面，登记 ledger）。
- **不把 `LockOption` 对象放进 AST**（预判裁定：AST 保持 EQL 自有字段，`LockOption` 是 nop-dao ORM 锁路径的类型；Phase 0 确认，见差异登记第 2 条）。
- 不做 MySQL 的 `AT TIME ZONE`（仅 UTC-only 的 CAST 形态，不无损——不注册，响亮报错）；不碰 SQLite（无方言、无 selector，roadmap 既档）。
- 不做 ledger 既有项（`greatest`/`least`、`stringAgg`、`ORDER BY … COLLATE`、`GROUPING SETS` 等）；不做 WI8/WI9b/WI11 的交付面。
- 不把 `:nop-orm-demo` / `nop-spring-simple-demo` / `:nop-orm-tdengine` / `:nop-db-migration` 拉进本 plan 的 `-pl`。
- 不改 `supportSavePoint` 的软弃用档位（除非 Phase 3 裁定落地 SAVEPOINT——那将同时构成对 WI10b「勿新增消费依赖」终裁的显式 scope change，需在裁定文档写明推翻理由）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql`：`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（`forUpdate_` 备选；视 Phase 4 裁定可能新增表源备选）、`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（AT TIME ZONE 归一化备选；视裁定 `unreservedWord_` 双登记）、`nop-persistence/nop-orm-eql/model/antlr/Literals.g4` 或 `nop-persistence/nop-orm-eql/model/antlr/Keyword.g4`（NOWAIT/SKIP/LOCKED token，**追加零删除**）、`nop-persistence/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava`（视裁定：既有类加字段 / 新类尾部追加）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`（手写 visit）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（nowait/skipLocked/VALUES/AT TIME ZONE 消费）、重生成产物、对应测试（`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/` 下）。
- `nop-persistence/nop-dao`：`dialect.xdef`（`<sqls>` 新元素或 features 新标志，视裁定）+ `DialectSqls.java`/`DialectFeatures.java`/`IDialect.java`/`DialectImpl.java`（retention 手写，零生成）+ `src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`（逐方言配置）+ 真库证据面（`TestDialect.java`/`TestSQLFunction.java`/`TestDialectCapabilityMatrix.java` 快照重生成）。
- `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（增量同步）；`ai-dev/design/eql-cross-db-compat/`（Phase 0 裁定文档）；`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（owner doc）；`ai-dev/logs/`；`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（仅限：WI12 状态行流转 + Phase 3/4 移出裁定的原地注记与 ledger 行——照 WI7b 原地注记先例）。

### Out Of Scope

- Non-Goals 所列全部；任何 roadmap 未登记的方言语法扩展（发现新缺陷/新需求 → 登记 roadmap，不顺手修）；`_gen`/`EqlASTKind` 等生成物的手改（红线）。

## 与 roadmap 的差异登记（起草时实测发现，执行时随裁定回写）

1. **EC ③ 前提不成立（roadmap WI12 节已自注，本 plan 落地为条件式）**：roadmap EC ③「两个新 AST 节点的生成物已重生成且 clean install 通过」——那 2 个节点（`SqlWindowClause`/`SqlWindowDecl`）已由合并带入，非本 plan 产出。本 plan 的对应门（Closure Gates 第 4 条）改写为：**若实施中新增 AST 节点（预判仅 VALUES 一项可能），按 ⓒ 定价重生成 + `./mvnw clean install -T 1C` 通过；若零新节点则核实 `ast/_gen/` 相对起点零新增（加字段只改既有 `_Sql*.java`）+ clean install 门槛按 ⓒ 触发与否执行**。
2. **LockOption 形态裁定（回补 plan 03 `:325` 遗留）**：roadmap WI12 前置注记与本 plan Phase 0 的裁定输入。**预判裁定 = 路径「复用 + 既有类加字段」，非 ⓒ 新类**：`nowait` 复用既有 boolean 字段（`EqlAST.xjava:167`，零 `_gen` 字段增量）；SKIP LOCKED 经给 `SqlQuerySelect` **加一个字段**（如 `skipLocked` boolean，或 Phase 0 裁定合并为枚举字段）承载——既有类加字段不动 `EqlASTKind` ordinal（roadmap WI7 收口注记明文），仅重生成 `_SqlQuerySelect.java` 等既有 `_gen` 文件。`LockOption`（nop-dao 类型）**不进 AST**：AST 是方言无关模型，`getForUpdateSql(LockOption)` 的 EQL 路径继续传 null（WI1 先例，`DialectImpl.java:522-523` 现状忽略该参数），`LockOption` 的 owner 仍是 `GenSqlHelper` ORM 锁路径；NOWAIT/SKIP LOCKED 的文本承载预判走 `<sqls>` 新元素（`<forUpdateNowait>`/`<forUpdateSkipLocked>`，presence = 能力 + 文本二合一，WI1 `sqlIntervalLiteral`/WI3 `intersectKeyword` retention 先例），映射工作在生成器内完成。被拒备选（AST 引用 LockOption 类型 / features 布尔门 + 后缀拼接 / 复用单 `<forUpdate>` 模板拼接）与拒绝理由在裁定文档逐条记录。⚠️ 此为 Phase 0 裁定项，Phase 1-2 按裁定执行；若裁定推翻预判，须同步回写本条。
3. **SAVEPOINT/ROLLBACK TO 预判裁定 = 移出 EQL 语法层**（roadmap WI12 交付 3 与此偏离，须在执行时以原地注记 + ledger 行回写 roadmap；Phase 3 给出最终裁定）。证据链五条：① 现状是**响亮失败**（`SAVEPOINT` 零规则引用、`ROLLBACK TO` 解析失败）——排序原则 P1「静默错误 > 响亮失败」不触发，长尾收益本身不足以立项；② 事务语句的 owner 是 **JDBC Connection API**（`Connection.setSavepoint()`/`rollback(Savepoint)`）与 Nop 事务管理器，不是 SQL 文本编译层——EQL 的执行模型（`CompiledSql` 缓存 + querySpace + PreparedStatement）不服务于事务边界控制；③ WI10b 已将 `supportSavePoint` **软弃用**并明令「勿新增消费依赖」（owner doc 死标志台账 `:716`）——落地 EQL SAVEPOINT 语法即新增消费依赖，直接冲突，构成需书面推翻的 scope change；④ EQL 既有事务语句层（`Eql.g4:28-30`/`:53-61` COMMIT/ROLLBACK）**打印侧是 no-op**（`EqlASTVisitor.java:667-675` 只 visit decorators、无 print 调用——结构推断：经 `EqlCompiler` 的编译产物必然不含语句文本；执行级证据待 Phase 3 裁定时一行探针补证）——在 no-op 层上扩展 `ROLLBACK TO` 只会产生「可解析但编译产物为空」的新 hollow 面，违反 anti-hollow 红线（Minimum Rules #24）；⑤ roadmap「明确不做的事」已把 DDL/事务边界类语句划出 EQL（`TRUNCATE` 永久否决先例）。successor 触发条件：出现「在 sql-lib 声明事务语句」的真实产品需求时，先登记 roadmap 立项（须同时解决：`SqlCommit`/`SqlRollback` 文本打印 + `supportSavePoint` 恢复接线裁定 + 事务管理器集成）。
4. **AT TIME ZONE 的 roadmap 引用行号**：任务输入称 roadmap 引用 `:373`——实测 roadmap 文件 `:373` 是 WI10b EC ③ 行、无 AT TIME ZONE 内容；AT TIME ZONE 的载体行是调研矩阵 `research:818` 与 S-11 行 `research:970`（roadmap WI12 交付 6 不带行号引用）。登记为任务输入的行号漂移，非 roadmap 缺陷。
5. **`nowait` 现状精化**：roadmap WI12 交付 1 称「只有生成的 equals 读它」——实测生成物在 clone（`_SqlQuerySelect.java:275`）/isValueEquivalent（`:521`）/toJson（`:597`）也读它，equals 之外共四处，全部是生成代码；手写侧零消费、文法侧零 population 的结论不变。
6. **H2 支持度修正认知与证据源分级**：调研矩阵把 H2 的 `AT TIME ZONE` 标 ✅ 但无版本/语法细节。本 plan 起草期的证据分级——`FOR UPDATE NOWAIT/SKIP LOCKED` = jar `ForUpdate.class` javap 实证（独立复验为真）；VALUES/AT TIME ZONE = 起草期经 jar 嵌套资源（`org/h2/util/data.zip!org/h2/res/help.csv`，2.4.240——非 jar 顶层条目）初证，**交付级证据以官方文档 + Phase 4/5 真库探针为准**。`AT LOCAL` 变体不在本 plan 范围（登记观察项）。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `nop-persistence/nop-orm-eql` 的 `.g4`（`forUpdate_`/`sqlTableSource` 等规则备选、新 token，见 Scope 的 In Scope 首条全路径清单）→ 重生成 `EqlParser.java`（起草期 11870 行，信息性）等 | **生成管线，plan-first**（AGENTS.md Protected Area；roadmap 门控矩阵 WI12 行同判） | 本 plan 即 plan-first 载体，执行前须过 draft review；`.g4` 改动**单独提交** | owner doc + 回归测试（各 Phase 红/绿用例 + 三份必测 BC） |
| `EqlAST.xjava` 既有类加字段（预判 Phase 1-2 范围）→ 重生成既有 `_Sql*.java` | 生成管线内**低风险路径**：不动 `EqlASTKind` ordinal（roadmap WI7 收口注记明文「给既有 AST 类加字段不动 EqlASTKind ordinal，只有新增 AST 类才触 ordinal 红线」） | 随 Phase 2 文法提交；`git diff` 中 `EqlASTKind.java` 零新增行作为每 Phase 回归证据 | 重生成产物 diff 审计 |
| `EqlAST.xjava` 新增类（仅 Phase 4 VALUES 若裁定落地）→ `ast/_gen/` 78→79 + ordinal 尾部追加 | **路径 ⓒ 定价**（约束 1）：重生成全链 + Protected Area plan-first | 新类落 `EqlAST.xjava` **尾部**（WI7b 教训）；`EqlASTKind.java` diff 仅尾部 +1 行 + 序号注记 | `./mvnw clean install -T 1C` 通过 + ordinal diff 审计 + 回归 |
| `dialect.xdef` + `DialectSqls`/`DialectFeatures`/`IDialect`/`DialectImpl` + `*.dialect.xml` | **零生成**（`nop-dao` 无 codegen 管线，roadmap Cross-Cutting 第一行；不构成生成管线 Protected Area）；方言情报 = 平台对外契约的**纯增量**（新增 `<sqls>` 元素/features 标志，不改既有取值） | 不触发 plan-first；逐项登记于裁定文档并同步矩阵快照 | 快照门禁 exit 0 + H2 真库证据 + 负向用例 |
| `supportSavePoint` 软弃用档位（Phase 3 若裁定落地才触碰） | WI10b 终裁的**显式 scope change** | Phase 3 裁定文档写明推翻理由；否则该面零触碰 | 裁定文档 + `TestDialectDeadFlagDisposition` 回归 |

⚠️ 本 plan 不触碰 `model/*.orm.xml`、`nop-auth` 认证模型、`nop-*-api` 公共接口、`nop-core`/`nop-xlang`/`nop-xdef`；`EqlASTKind/Visitor/Processor/Optimizer` 与 `{ast.name}Node.java.xgen` 模板不手改（约束 1 红线）。

## Execution Plan

顺序执行；Phase 1-2 强依赖 Phase 0 的形态裁定，Phase 4-5 依赖 Phase 0 的定价与矩阵取证，Phase 3/6 相对独立。每个代码 Phase 的文法改动单独提交（文法组 / 逻辑组两段式，WI7b 先例）；每 Phase 收尾跑语料棘轮 + 矩阵快照门禁。

### Phase 0 - 裁定：LockOption 形态、SAVEPOINT 归属、VALUES 定价、AT TIME ZONE 文法方案 + 真库/文档取证（不改产品代码）

Status: completed
Targets: `ai-dev/design/eql-cross-db-compat/`（新增裁定文档）、`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（只读取证）

- Item Types: `Decision`、`Proof`

- [x] **钉死 H2 解析版本**：`mvn dependency:list -pl :nop-dao` 取 CI 实际 H2 版本，复核两组证据在该版本成立——① NOWAIT/SKIP LOCKED 的 `ForUpdate.class` 常量实证（DEFAULT/NOWAIT/SKIP_LOCKED + 渲染串，javap 可独立复验）；② VALUES/AT TIME ZONE 的**官方文档取证**（grammar「Table Expression/Query/Time zone」+ commands「Table value」，引文写入裁定文档）；若为 1.4.x 系则真库证据面重估并登记（阻断后续 Phase 的真库证据项，文法裁定不受阻）。真库探针的落地交付在 Phase 4/5（VALUES/AT TIME ZONE）与 Phase 2（NOWAIT/SKIP LOCKED）。
- [x] **全量锚点符号重定位**（照 plan 37/24 先例）：本 plan 全部 file:line 为起草期信息性快照，且起草评审期实测发现工作区存在未提交并行文法改动（WI8/WI9b 的 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 及生成物 M 状态，评审期内已漂移两轮）——逐条以符号定位（规则名/方法名/常量名/错误码名）复核 Current Baseline 与各 Phase 引用的全部锚点现位，把「确认成立/已漂移（原值→现值）」清单记入裁定文档；后续任何 Phase 开工前若发现锚点失准，按同款符号重定位处理，不回写本 plan 正文。
- [x] **裁定 LockOption 引入形态**（Decision，回补 plan 03 `:325`）：预判 = 「复用 `nowait` 字段 + `SqlQuerySelect` 加 `skipLocked` 字段（非 ⓒ 新类，零 ordinal 风险）+ `LockOption` 不进 AST + 文本承载走 `<sqls>` 新元素 `<forUpdateNowait>`/`<forUpdateSkipLocked>`（presence=能力+文本二合一）」。**同项裁定文法接线形态**：生成的 `SqlQuerySelect_forUpdate`（boolean 返回）无法承载 NOWAIT/SKIP LOCKED 变体区分——(a) 变体区分上移 `sqlQuerySelect` 层分离标签 vs (b) `forUpdate_` 子树备选 + 手写整体覆写 `visitSqlQuerySelect` 聚合方法（WI7b wrapper 先例），二选一并记录被拒方理由。被拒备选逐条记录：AST 引用 nop-dao `LockOption` 类型（层级耦合 + xjava 类型注册成本）、features 布尔门 + 「`<forUpdate>` 模板 + 固定后缀拼接」（后缀文本方言差异无法表达，且能力门与文本面割裂）、单模板方案。同时裁定 NOWAIT 与 SKIP LOCKED 互斥校验落点（transform/编译期报错的具体错误码——预判复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` 或既有解析期响亮，不新起新码，除非裁定必须）与 `WAIT n` 缺位的登记口径。
- [x] **裁定 SAVEPOINT/ROLLBACK TO 归属**（Decision）：以「与 roadmap 的差异登记」第 3 条五条证据链为输入，产出「移出 + ledger + successor」或「落地」的终局裁定；若裁定移出，同步产出 roadmap WI12 节与 ledger 行的注记措辞（照 WI7b/COLLATE 行原地注记先例）；若裁定落地，追加产出文法挂载点、`SqlRollback` 字段扩展定价（既有类加字段）与打印侧补文本的实现边界——**不允许「既不落地也不移出」的名义 owner 状态**。
- [x] **裁定 `FROM (VALUES …)` 定价**（Decision）：文法方案（`sqlTableSource` 新 altLabel 备选，label 前缀 = 目标 AST 类名——altLabel 三约束照 plan 24 Current Baseline）；AST 形态裁定（预判 ⓒ 新类如 `SqlValuesTableSource`（行结构的行列表，**非**复用单行扁平 `SqlValues`），尾部追加 ordinal；被拒备选「归一化进 `SqlSubqueryTableSource`」（其 query 字段是 `SqlSelect`，VALUES 非_SELECT，失真）与「UNION ALL 链 lowering」（需 per-dialect dual 表体系，不无损））；方言矩阵逐格一手取证（MySQL `ROW()` 行形态 / Oracle 23ai 边界 / SQLite compound-SELECT 边界 / MSSQL 别名必带 / H2 [文档+真库探针 Phase 4] / DuckDB [文档]）；按取证结论裁定：落地（支持方言集原生输出 + 其余方言编译期报错 + 能力门标志形态）或按约束 3 移出+ledger；**EQL transform 层对无实体 VALUES 表源的 querySpace/表作用域处理**必须在裁定中给出可行性结论（`EqlTransformVisitor` 的实体导航前提——`ERR_EQL_ONLY_SUPPORT_SINGLE_TABLE_SOURCE` 等既有约束是否阻挡），此结论是落地/移出的硬依据。
- [x] **裁定 AT TIME ZONE 文法方案**（Decision）：预判 = WI5 同款——`sqlExpr_simple`/`sqlExpr_special` 的 altLabel 备选（label 以 `SqlRegularFunction` 为前缀）+ 构建期归一化进 `SqlRegularFunction(name="atTimeZone", args=[expr, tz])`（手写 visit 完全重写不调 super）+ 逐方言 `<template name="atTimeZone"><source>{0} AT TIME ZONE {1}</source></template>`；**零新 AST 节点、零新 token**（`AT`/`TIME`/`ZONE` 三 token 已在词法层）。必须论证：① ANTLR 歧义（`AT` 当前零规则引用，冲突面小——仍须登记备选序与自适应预测行为）；② 归一化后**回显路径**输出 `atTimeZone(expr, tz)` 函数形态与 WI0 语料基线的关系（7 语料 0 命中已证无 BC 风险，但「输入→归一化回显→方言 lowering」三态断言必须落）；③ 方言矩阵逐格一手取证（PG/Oracle/MSSQL/H2[文档+真库探针 Phase 5]/DB2/DuckDB 注册 template；MySQL/MariaDB 不注册=响亮；SQLite 永无；dm/tdengine `[未取证]`）；④ 未注册方言的负向错误码 = 既有 `ERR_EQL_UNKNOWN_FUNCTION`（WI6 datediff/dateadd 不落先例）。
- [x] **裁定 nowait/SKIP LOCKED 的方言矩阵与逐方言配置值**（Decision）：h2（`for update nowait`/`for update skip locked`，[实测-jar ForUpdate.class]+[真库探针 Phase 2]）/ oracle / pg / mysql / mariadb（版本边界一手取证后定）/ duckdb（空 = 负向用例方言，WI1 空模板先例）/ mssql（不支持 = 负向）/ db2（未取证则不配置 = 响亮）/ dm/tdengine（`[未取证]` 红线，不配置不计覆盖率）。
- [x] **窗口命名 WINDOW 回归验证面清单**（Proof）：列出 Phase 6 将复核的四层测试载体（见 Current Baseline「④」小节）并确认全部存在于当前树。
- [x] 复核 WI1/WI7/WI7b 三 plan 状态未回退（记录于裁定文档）；`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0 起点核实。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含：H2 版本钉死记录、LockOption 形态裁定与被拒备选、SAVEPOINT 终局裁定（含 roadmap 回写措辞或落地方案）、VALUES 定价（AST 形态 + 方言一手矩阵 + transform 可行性结论 + 落地/移出结论）、AT TIME ZONE 文法方案与三态断言设计、逐方言配置矩阵（含证据等级 `[实测]`/`[文档]`/`[未取证]`）、窗口回归面清单、前置状态复核。
- [x] 本 Phase 纯裁定不改产品代码：`No owner-doc update required`（裁定文档即交付物；owner doc 覆盖表随各代码 Phase 落）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 1 - `SqlQuerySelect.nowait` 接线（交付 ①，零文法）

Status: completed
Targets: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`、`nop-persistence/nop-dao`（`dialect.xdef` `<sqls>` 新元素 + `DialectSqls` retention + `IDialect`/`DialectImpl` accessor 视裁定）、支持方言 `*.dialect.xml`、`nop-orm-eql` 测试

- Item Types: `Feature`、`Proof`

- [x] **修复前红**：程序化构造 `SqlQuerySelect`（`forUpdate=true, nowait=true`）在已配置锁模板的方言上编译——断言产出含 nowait 语义的锁文本，修复前红（现状 `printForUpdate` 无视 nowait，输出裸 `<forUpdate>` 文本）。
- [x] 按 Phase 0 裁定实现：`printForUpdate` 消费 `nowait`（从 `<forUpdateNowait>` 配置取文本）；**未配置方言 + `nowait=true` → 编译期响亮报错**（断言具体错误码与 feature 参数；不允许静默降级为裸 `for update`——「只打印不设门 = 净回归」P1 禁令）。
- [x] 方言配置纯增量落地：h2 等支持方言 `<sqls>` 补 `<forUpdateNowait>`（值如 `for update nowait`）；duckdb/mssql 等不配置（负向载体）。
- [x] **接线独立正向用例**（roadmap EC ②）：`nowait=true` 的输出文本 ≠ `nowait=false` 的输出文本，且与方言配置值一致（防「恰好同形」假阳性——WI1 `testConfiguredTemplateIsConsumed` 的自定义模板先例）。
- [x] **负向用例**（roadmap EC ②）：不支持方言上 `nowait=true` 编译期报错；`forUpdate=false && nowait=true` 的矛盾输入有响亮处置（解析层到不了，程序化构造可及——裁定落点 = 编译期报错或忽略+登记，不允许静默）。
- [x] 接线验证（Minimum Rules #23）：程序化 AST → transform → 生成三层连通（h2 上端到端断言）。
- [x] 矩阵快照：`<sqls>` 配置非函数表 → **核实零增量** + 门禁 exit 0（WI7b interval 先例）；语料 BC：`TestEqlCorpusBaseline` 通过。
- [x] owner doc：能力点登记表 +1 行（generator 方法 + 触发配置 + 不支持时错误码）。
- [x] `./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿；本 Phase **零 `.g4`/`EqlAST.xjava` 改动**（`ast/_gen/` 零 diff 为验收证据）；`ai-dev/logs/` 条目。

Exit Criteria:

- [x] 红→绿回归断言具体 SQL 文本（配置方言 nowait 文本 + 未配置方言裸 `for update` 不变）。
- [x] 负向用例断言具体错误码；矛盾输入有显式处置；无静默跳过。
- [x] 接线三层连通用例通过；矩阵快照零增量核实 exit 0；`TestEqlCorpusBaseline` 通过。
- [x] owner doc 登记行已落；`ast/_gen/` 零 diff；全量命令绿；daily log 已更新。

### Phase 2 - `FOR UPDATE NOWAIT` / `SKIP LOCKED` 语法（交付 ②）

Status: completed
Targets: `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（`forUpdate_` 备选）、`nop-persistence/nop-orm-eql/model/antlr/Literals.g4` 或 `nop-persistence/nop-orm-eql/model/antlr/Keyword.g4`（NOWAIT/SKIP/LOCKED token）、`nop-persistence/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava`（`SqlQuerySelect` 加字段，视裁定）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`、重生成产物、`nop-orm-eql` 测试

- Item Types: `Feature`、`Proof`

- [x] **开工前置**：Phase 1 completed；LockOption 形态裁定已定（Phase 0）。
- [x] **修复前红**：`select … for update nowait` 与 `select … for update skip locked` 解析失败（parse error）——回归测试断言可解析且 lowering 正确，修复前红。
- [x] 文法组（**单独提交**）：`forUpdate_: FOR UPDATE ( NOWAIT | SKIP LOCKED )?` 备选；NOWAIT/SKIP/LOCKED token 追加（序号顺延**零删除**，NULLS/ROLLUP/CUBE 三件套先例）；`unreservedWord_` 双登记三词（作标识符可解析守护用例——WI7b `testRollupCubeAsIdentifiers` 先例）；`EqlAST.xjava` 按裁定加字段；全量重生成；**`EqlASTKind.java` 零新增行**（加字段不加类）。ⓘ **接线形态按 Phase 0 裁定落地**（二选一，见 Phase 0 LockOption 裁定项）：生成的 `SqlQuerySelect_forUpdate` 是 boolean 返回方法（`_EqlASTBuildVisitor` 经 `forUpdate=forUpdate_` label 调用），无法区分 NOWAIT/SKIP LOCKED 变体——(a) 变体区分上移到 `sqlQuerySelect` 层分离标签（生成 prop 赋值直达 SqlQuerySelect 字段）；或 (b) 保持 `forUpdate_` 子树备选 + **手写整体覆写聚合方法**（WI7b wrapper 先例：生成方法体只建空节点、手写完全重写不调 super），覆写体内判别变体并填充字段。
- [x] 逻辑组（单独提交）：按裁定形态填充 nowait/skipLocked 字段（生成方法体只建空节点 + validate 的先例约束）；生成器按字段取 `<forUpdateNowait>`/`<forUpdateSkipLocked>` 文本；NOWAIT 与 SKIP LOCKED 同时出现的输入在解析层天然互斥（文法备选），程序化构造的矛盾输入按 Phase 1 裁定处置。
- [x] 正向用例（EC ①）：h2 上 `for update nowait`/`for update skip locked` 各一条编译文本断言（与 h2 配置值一致；h2 原生语法 `[ FOR UPDATE [ NOWAIT | WAIT secondsNumeric | SKIP LOCKED ] ]` [实测-jar ForUpdate.class 常量/渲染串 + 真库探针]）；再取一条 pg 或 oracle 形态断言（Phase 0 矩阵内方言）。
- [x] **负向用例**（EC ②）：不支持方言（duckdb——`<forUpdate>` 空模板 + 无 NOWAIT 配置；mssql——无 FOR UPDATE 子句变体）各一条编译期报错，断言具体错误码；`nowait`/`skipLocked` 各自独立触发（不能只测组合）。
- [x] **真库证据**：h2 经 `JdbcTestCase` 真库执行 `select … for update nowait` 与 `… skip locked`（内存库 + 显式事务边界或 autocommit 探针，执行不抛错且结果集正确）；无法真库验证方言（oracle/pg/mysql/mariadb/mssql/db2/dm/tdengine）逐个列出 + 一手文档替代证据，`dm`/`tdengine` 标 `[未取证]`。
- [x] WI1 遗留回补：plan 03 `:325` LockOption 注记按 Phase 0 裁定回写结案（在本 plan Follow-up 或裁定文档注明「已回补」）。
- [x] 语料 BC：三份必测逐字一致；矩阵快照零增量核实（`<sqls>` 配置非函数表）exit 0；owner doc 覆盖表（逐方言 + 证据等级）。
- [x] `./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿；文法/逻辑两段提交分离；`ai-dev/logs/` 条目。

Exit Criteria:

- [x] 红→绿回归断言具体 SQL 文本（NOWAIT/SKIP LOCKED 两形态 × 两方言族）。
- [x] 负向用例断言具体错误码（至少 duckdb/mssql 两个不支持方言）；无静默降级（不支持时**不**回退裸 `for update`）。
- [x] h2 真库执行证据落地；`[未取证]` 方言未计入覆盖率。
- [x] token 追加零删除；`EqlASTKind.java` 零新增行；`TestEqlCorpusBaseline` 通过；全量命令绿；daily log 已更新。

### Phase 3 - `SAVEPOINT` / `ROLLBACK TO` 终局裁定落地（交付 ③）

Status: completed
Targets: `ai-dev/design/eql-cross-db-compat/`（裁定文档 Phase 3 节）、`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（仅当裁定=移出：WI12 节原地注记 + ledger 行；**本 Phase 不改 roadmap 状态区**）

- Item Types: `Decision`（+ 若裁定落地则含 `Feature`）

- [x] 依 Phase 0 终局裁定执行。**分支 A（预判，移出）**：① roadmap WI12 交付 3 加原地执行注记（不删原文，注明裁定依据 = 裁定文档五条证据链）+ 「明确延后项」ledger 新增一行（理由 / 重新评估触发条件 / successor = 单独 roadmap 立项）；② 本 plan Deferred But Adjudicated 段落登记；③ `supportSavePoint` 软弃用档位**零触碰**（`TestDialectDeadFlagDisposition` 回归确认未受扰）；④ 既有 `SqlCommit`/`SqlRollback` 打印 no-op 面登记为 watch-only 观察项（本 plan 不修）。**分支 B（落地）**：文法（`sqlRollback: ROLLBACK (TO savepointName=sqlIdentifier_)?` + `sqlSavepoint` 语句——`SAVEPOINT` token 已在 `Keyword.g4:349`）+ `SqlRollback` 既有类加字段（零 ordinal）或新类尾部追加（ⓒ 定价）+ 打印侧补文本（打破 no-op 需同步裁定 `SqlCommit`/`SqlRollback` 既有语句的打印补齐，否则 `SAVEPOINT` 有文本而 `COMMIT` 没有 = 新的不一致面）+ `supportSavePoint` 软弃用档位推翻（WI10b scope change 书面理由）+ 各方言响亮负向（JDBC API 场景为主的一手取证登记）。两分支都以裁定文档终局结论为准。
- [x] 分支 A 的裁定文档必须显式回答「为什么不是名义 owner」：ledger 行的触发条件、successor 路径、WI12 交付 3 的注记位置三处对齐。
- [x] 若分支 A：零产品代码改动——`No owner-doc update required`（owner doc 的锁/事务段若无 SAVEPOINT 内容则不新增；`eql-and-database-compatibility.md` 仅在裁定需要说明「事务语句为何不在 EQL」时加一小节，随本 Phase 落）。
- [x] `ai-dev/logs/` 条目（含裁定终局结论与 roadmap 回写位置）。

Exit Criteria:

- [x] SAVEPOINT/ROLLBACK TO 落到「落地」或「移出+successor」两者之一，裁定证据链与 roadmap 回写措辞齐备；无第三状态。
- [x] 分支 A：roadmap 注记 + ledger 行已按先例格式落（执行者提交）；`TestDialectDeadFlagDisposition` 回归绿；产品代码零 diff。
- [x] ~~分支 B：按 Phase 2 同款红→绿 + 负向 + 真库证据纪律执行完毕（含 clean install 若触 ⓒ）。~~（**不适用**——Phase 3 走分支 A 移出，2026-10-04 落档；本项随分支互斥选择失效，处置已闭非未完成项）
- [ ] `ai-dev/logs/` 条目已更新。

### Phase 4 - `FROM (VALUES …)`（交付 ⑤，按 Phase 0 定价）

Status: completed
Targets: 视裁定——落地时：`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（`sqlTableSource` 备选）、`nop-persistence/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava`（**新类尾部追加**）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`、重生成产物、`*.dialect.xml`（能力门）、测试；移出时：roadmap 注记 + ledger

- Item Types: `Feature`（或 `Decision` 若移出）、`Proof`

- [x] **开工前置**：Phase 0 的 transform 层可行性结论在手（无实体 VALUES 表源的 querySpace/表作用域处理）；若结论为「transform 层阻挡且无低成本解法」→ 直接走移出分支。
- [x] **分支 A（落地）**：① 修复前红：`from (values (1),(2)) t(x)` 解析失败——回归断言可解析与编译文本，修复前红；② 文法组单独提交：`sqlTableSource` 新 altLabel 备选（label 三约束）+ 新 AST 类落 `EqlAST.xjava` **尾部** + 全量重生成；③ 逻辑组单独提交：手写 visit 构建行结构 + 生成器输出 + 能力门（支持方言原生输出；不支持方言编译期报错——错误码与 feature 参数按裁定）；④ 正向：h2 [官方文档语法+真库探针] + pg（或 Phase 0 矩阵内另一方言）编译文本断言；MySQL 若因 `ROW()` 行形态不无损 → 按裁定不落（响亮）并登记；⑤ 负向：至少一个不支持方言编译期报错（断言错误码）；行内元素数不一致（ragged rows）的响亮处置（裁定落点：解析期/编译期报错，不允许静默）；⑥ **transform 层端到端用例**：含 VALUES 表源的 EQL 从 parse→transform→compile 完整走通（Anti-Hollow：无实体表源不被 `ERR_EQL_ONLY_SUPPORT_SINGLE_TABLE_SOURCE` 等既有约束静默挡下）；⑦ h2 真库执行证据（`select * from (values (1),(2)) as t(x)`——官方文档语法，真库执行落地即 Primary 证据）；⑧ ⓒ 门：`./mvnw clean install -T 1C` 通过（codegen 全链路 + 114 个 orm.xml 回归）；⑨ `EqlASTKind.java` diff = 尾部 +1 行；⑩ 矩阵快照与 owner doc。
- [x] **分支 B（移出）**：裁定文档终局结论 + roadmap WI12 交付 5 原地注记 + ledger 行（触发条件 = MySQL `ROW()` 形态或 Oracle 缺口的归一化方案出现 / transform 层改造立项时）+ Deferred 段登记；产品代码零 diff。
- [x] 语料 BC 三份必测逐字一致（分支 A 的文法改动会平移 `EqlParser` 行号与 parse-error 元数据——棘轮按 WI7b「文法 token 集合变化如实重生成」口径处理，diff 逐行核验）；`ai-dev/logs/` 条目。

Exit Criteria:

- [x] 分支 A：红→绿（两方言族文本断言）+ 负向（错误码断言）+ transform 端到端 + h2 真库 + clean install 通过 + `EqlASTKind` 尾部追加 diff 审计 + 快照/owner doc 齐。
- [x] 分支 B：裁定 + roadmap 注记 + ledger 三处对齐，零产品代码。
- [x] `TestEqlCorpusBaseline` 通过（分支 A 允许 parse-error 元数据行如实更新，语句本体零漂移）；daily log 已更新。

### Phase 5 - `AT TIME ZONE`（交付 ⑥，WI5 先例形态）

Status: planned
Targets: `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（归一化备选）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`、`*.dialect.xml`（`<template>` 注册）、重生成产物、`nop-orm-eql`/`nop-dao` 测试

- Item Types: `Feature`、`Proof`

- [ ] **修复前红**：`ts at time zone 'UTC'` 解析失败——回归断言可解析与 lowering 正确，修复前红。
- [ ] 文法组（单独提交）：altLabel 备选（label 以 `SqlRegularFunction` 为前缀，plan 24 三约束逐条落实）+ 手写 visit 归一化 `SqlRegularFunction(name="atTimeZone", args=[expr, tz])`（完全重写不调 super）；**零新 token、零 `EqlAST.xjava` 改动**（`ast/_gen/` 零 diff）。
- [ ] 逻辑组（单独提交）：如归一化已完全由构建期承载则本组可能为空——若裁定需要元数校验/类型检查则按裁定落；**`{0}`/`{1}` marker 与实参顺序**经 `TemplateSQLFunction` 加载期校验兜底（WI0 交付 2）。
- [ ] 正向用例（EC ①）：h2 `{0} AT TIME ZONE {1}` 渲染文本断言 + pg/oracle（Phase 0 矩阵内）至少一族；**三态断言**：输入文本 → 归一化回显（`atTimeZone(…)` 函数形态）→ 方言 lowering 文本。
- [ ] 负向用例（EC ② 同款精神）：MySQL/MariaDB（不注册）编译期 `ERR_EQL_UNKNOWN_FUNCTION(atTimeZone)` 响亮断言；元数负向（TOO_FEW/TOO_MANY 既有通路）。
- [ ] **真库证据**（WI5 EC ⑥ 同款）：`atTimeZone` 在 h2 经 `TestDialect.testFunctions` 真库执行全绿（补 `<testSql>`）；无法真库验证方言逐个列出 + 一手文档证据；`dm`/`tdengine` 标 `[未取证]`。
- [ ] **矩阵快照真增量**：`<template>` 注册进方言函数表 → 同步 `dialect-capability-matrix.json`（重生成快照测试）+ 门禁 exit 0；owner doc「各库覆盖情况」表（MySQL UTC-only CAST 形态、SQLite 永无等差异登记 = 约束 4 的不等价类）。
- [ ] 语料 BC 三份必测逐字一致；`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿；文法/逻辑提交分离；daily log 条目。

Exit Criteria:

- [ ] 红→绿（h2 + 至少一族方言文本断言）+ 三态断言齐。
- [ ] 负向断言具体错误码（不注册方言 unknown-function）；无静默降级。
- [ ] h2 `TestDialect.testFunctions` 真库全绿；`[未取证]` 未计覆盖率。
- [ ] 矩阵快照增量已同步 + 门禁 exit 0；owner doc 覆盖表含不等价差异行；`ast/_gen/` 零 diff；全量命令绿；daily log 已更新。

### Phase 6 - 窗口命名 WINDOW 回归验证 + 全量收口（交付 ④ + roadmap EC ⑤⑥）

Status: planned
Targets: 既有测试面复核（零产品代码）、owner doc 终局对账、`ai-dev/logs/`

- Item Types: `Proof`

- [ ] **窗口回归验证**（roadmap WI12 交付 4 注记的落地证据）：`TestEqlWindowGrammarParse`（含 `testOverNamedWindowRef`/`testWindowClause`）、`TestDialectWindowSqlSnapshot`（10+5 方言 golden + `testOverWithoutPartitionText`）、`TestH2WindowFrameMatrix`、`TestH2WindowFrameCompileEndToEnd` 在本 plan 全部生成物重生成完成后**全量重跑全绿**；记录「已由 add-stream-sql 建成、本 plan 仅回归收口」的结论于 daily log（roadmap 回写素材）。
- [ ] **全量测试门**（roadmap EC ⑤）：`./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C`——按 Current Baseline 全绿口径执行（5 项负载敏感 flake 类级排除 + `!JSONPointTest_0,!JSONPointTest_1` 连带排除；若有 clean install 触发点则该门在 Phase 2/4 已跑，此处核实未回退）。
- [ ] **语料 BC 终局**（roadmap EC ⑥）：三份必测逐字一致；**不声称 7 个全测**。
- [ ] **owner doc 终局对账**：`eql-and-database-compatibility.md` 与实际 dialect XML 逐项对账无差异（本 plan 新增面 + 既有面）；能力点登记表含本 plan 全部新输出点行。
- [ ] **四门禁**：矩阵快照 exit 0；`node ai-dev/tools/check-doc-links.mjs --strict` 新增 broken link = 0（既有基线 3 项 warning 不顺手修）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/38-eql-wi12-p2-longtail.md --strict` 退出码 0（收口轮）。
- [ ] `ai-dev/logs/` 终局条目。

Exit Criteria:

- [ ] 窗口四层测试全绿且记录在案；全量测试门 BUILD SUCCESS（豁免口径如实记录）。
- [ ] 三份必测 BC 逐字一致；owner doc 对账无差异。
- [ ] 四门禁全过（doc-links 新增 0）。
- [ ] daily log 终局条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [ ] 交付 ①：`SqlQuerySelect.nowait` 接线落地——独立正向用例证明（roadmap WI12 EC ② 后半），不支持方言编译期报错负向用例存在（EC ② 前半）。
- [ ] 交付 ②：`FOR UPDATE NOWAIT`/`SKIP LOCKED` 语法落地——每个语法正确 SQL 文本用例（EC ①）+ 不支持方言负向 + h2 真库证据；WI1 LockOption 遗留按裁定回补结案。
- [ ] 交付 ③：SAVEPOINT/ROLLBACK TO 已裁定为「落地」或「移出+successor」之一，roadmap 回写完成（不允许名义 owner——Anti-Slacking Rule）。
- [ ] 交付 ⑤：`FROM (VALUES …)` 已按裁定落地（含 ⓒ 定价门）或移出+ledger；两分支均有可审计证据。
- [ ] 交付 ⑥：`AT TIME ZONE` 落地——归一化三态断言 + 逐方言 template + h2 真库（`TestDialect.testFunctions`）+ 未注册方言响亮负向。
- [ ] 交付 ④：窗口命名 WINDOW 四层既有覆盖回归全绿并记录（roadmap WI12 交付 4 的「改为回归验证」落地）。
- [ ] 若实施中新增 AST 节点（roadmap EC ③ 的条件式改写）：生成物已重生成且 `./mvnw clean install -T 1C` 通过、`EqlASTKind` ordinal diff 审计通过；若零新节点：`ast/_gen/` 相对起点零新增（加字段只改既有 `_Sql*.java`）已核实。
- [ ] `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 已同步（roadmap EC ④）：新增能力点登记表行 + 各语法覆盖表（逐行 `[实测]`/`[文档]`/`[未取证]`）+ 移出项（如有）的归属说明。
- [ ] `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（roadmap EC ⑤；豁免口径 = Current Baseline「全绿口径」小节，不得据此判本 plan 失败）。
- [ ] **既有 EQL 语料 BC（roadmap EC ⑥）**：`:nop-job-dao`(NopJobTask)、`:nop-auth-service`(test)、`:nop-orm`(test) 三份必测与 WI0 基线快照逐字一致；未声称 7 个全测。
- [ ] 方言能力矩阵快照增量已同步（`<template>` 真增量 / `<sqls>` 零增量核实）且 `check-eql-dialect-matrix-snapshot.mjs` exit 0。
- [ ] 约束 4 的「一致性 + 不等价」登记齐备：AT TIME ZONE（MySQL UTC-only / SQLite 永无）、VALUES（MySQL `ROW()` 形态 / Oracle 缺口）、NOWAIT/SKIP LOCKED（MSSQL 锁提示体系差异）至少各一项差异行落 owner doc。
- [ ] 生成物纪律：每个 Phase 的 `.g4`/`EqlAST.xjava` 改动单独提交（文法/逻辑分离可审计）；无 lexer token 删除；`EqlASTKind/Visitor/Processor/Optimizer` 零手改。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0（既有基线 3 项 warning：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，不顺手修、只比对新增量）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/38-eql-wi12-p2-longtail.md --strict` 退出码 0。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中（anti-hollow：「只解析不 lowering/只打印不设门」正是半实现形态——Phase 3 分支 A 的「不触碰 no-op 层」裁定不得被误判为 hollow：该层是既有现状，本 plan 未在其上新增可解析面）。
- [ ] owner doc 已同步到 live baseline；`docs-for-ai/INDEX.md` 与 `04-reference/source-anchors.md` 若路由/锚点变化已更新。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）nowait/SKIP LOCKED/VALUES/AT TIME ZONE 从 EQL 文本（或程序化 AST）→ parse/transform → 方言 SQL 输出/报错的端到端调用链真实连通（不只是文法分支存在）；（b）无空方法体/静默跳过/no-op 作为**新增**实现（不支持路径全部响亮报错）；（c）Phase 3/4 若走移出分支，移出面无「新增的可解析但无产物」入口。
- [ ] **独立 closure audit 已完成并由不同 `task_id` 的子 agent 执行**，evidence 写入本 plan `Closure` 段（guide #26/#27；roadmap WI12 EC ⑦）；roadmap Work Item Status 的 WI12 行据此才可 `planned` → `done`。

## Deferred But Adjudicated

> 预登记 Phase 0/3/4 的两类可能裁定结果；执行时按实际结论补全并勾选。

### SAVEPOINT / ROLLBACK TO（Phase 3 终局裁定 = 移出，2026-10-04 落地）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 现状响亮失败（无静默风险）；事务语句 owner 是 JDBC Connection API 与 Nop 事务管理器而非 SQL 文本编译层；`supportSavePoint` 已被 WI10b 软弃用（勿新增消费依赖）；既有事务语句层打印侧 no-op（`EqlASTVisitor` visitSqlCommit/visitSqlRollback 方法体仅 visitDecorators、双 generator 零 override——结构封闭探针，plan 预留的执行级一行探针判定不必要），扩展只会造新 hollow 面。五条证据链全文见裁定文档 §3。
- Successor Required: `yes`
- Successor Path: roadmap「明确延后项」ledger 新增行 **已落**（2026-10-04，触发条件 = 出现「sql-lib 声明事务语句」的产品需求；须同时解决打印侧、flag 恢复接线、事务管理器集成）+ WI12 交付 3 原地注记 **已落**（两处对齐裁定文档 §3.1 措辞）。`supportSavePoint` 软弃用档位零触碰（`TestDialectDeadFlagDisposition` 回归绿，Phase 3 实跑）。

### `FROM (VALUES …)`（仅当 Phase 4 裁定移出时适用；预判为落地分支，本节仅在移出时激活）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure:（Phase 4 实际取证后填写——预判触发因素：MySQL `ROW()` 行形态/Oracle 缺口使无损归一化集合收窄，或 transform 层对无实体表源的约束无低成本解法）
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`「明确延后项」新增行（触发条件 = 归一化方案立项或 transform 层改造立项）。

## Non-Blocking Follow-ups

- `FOR UPDATE WAIT n`（H2 `[ FOR UPDATE [ NOWAIT | WAIT secondsNumeric | SKIP LOCKED ] ]` 与 Oracle `WAIT n` 均支持）：roadmap WI12 未点名，登记为观察项；若产品出现锁等待超时需求，先登记 roadmap 再立项（与 LockOption.timeout 的关系一并在新 WI 裁定）。
- `AT LOCAL`（H2/Oracle 23ai 的会话时区变体）：本 plan 裁定为不落（roadmap 未点名）；观察项。
- 既有 `SqlCommit`/`SqlRollback` 打印 no-op 面（`EqlASTVisitor.java:667-675`）：本 plan 不修（Non-Goals）；作为 watch-only 观察项随 Phase 3 裁定登记——若未来任何 WI 使事务语句可经 EQL 编译执行，此面立即升级为 live defect。
- `TestDuckDbDialect` lockHint 断言的 EQL 路径同等用例（plan 03 `:324` 遗留）：lockHint 与本 plan 的锁文本面同源，若 Phase 2 实施中顺路可补则补，否则保持 WI1 处置现状（登记不构成 closure 缺口——plan 03 已裁定其不属该 plan EC 点名范围）。
- CI 真库验证面扩展（mysql/oracle 等 docker 化）：超出本 roadmap；本 plan 的 `[未取证]`/`[文档]` 格在其上转 `[实测]`。

## Closure

Status Note: （完成或关闭时填写：为什么这个 plan 可以关闭）
Completed:

Closure Audit Evidence:

- Reviewer / Agent: （独立审阅者或独立子 agent 的 session ID / task ID）
- Audit Session: （如用子 agent，记录 session ID）
- Evidence:
  - （每条 Exit Criterion 与 Closure Gate 的验证结果 PASS/FAIL + live code path / test name）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/38-eql-wi12-p2-longtail.md --strict` 退出码
  - Anti-Hollow 检查结果（端到端调用链追踪 + `scan-hollow-implementations.mjs` 退出码）
  - Deferred 项分类检查（确认无 in-scope live defect 被降级）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
- （或明确写 no remaining plan-owned work）
