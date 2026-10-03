# 03 EQL WI1 — A 类执行路径缺陷（6 项）与方言门

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI1 节 + 门控矩阵 WI1 行 + Cross-Cutting 全部 + 关键约束与红线节，v7.2）
> Related: `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`（硬前置，Phase 1-4 全部已完成）、`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`（G-1 已裁决）

## Purpose

收口 roadmap 最高优先级工作项 WI1：6 个 A 类执行路径缺陷（BUG-1 ESCAPE / BUG-2 LATERAL / BUG-3 for update / BUG-5 INTERVAL / BUG-10 cross join / BUG-14 集合下标）全部停止"静默产出错误 SQL"，每个缺陷都有断言具体 SQL 文本的"修复前红、修复后绿"回归测试；本 WI 新增/触碰的每个方言敏感输出点同批次落方言能力门（`supportLateral` 全链接线、`getForUpdateSql` 空模板编译期报错、`sqlIntervalLiteral` 骨架），并满足"只打印不设门 = 净回归"的排序原则。

## Current Baseline

以下事实全部于 2026-10-03 在本工作区分支 `feat/eql-cross-db-compat` 实测核验（含 file:line）。

### WI0 已落地（本 plan 的验证骨架）

- 方言能力矩阵测试：`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialectCapabilityMatrix.java`（已提交 `8b41b0a776`）；快照 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（18 个方言全登记，`es` 标 `pseudo`，`db2` selector 断链已记录）；门禁脚本 `ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs`。
- 四门裁决：`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`——G-1 采纳承诺基线（Oracle 23ai / MySQL 8.0.31 / MariaDB 10.2 / MariaDB 13.0（UPDATE RETURNING）/ PG 15 / MSSQL 2022 / SQLite 3.39），**全部 unverified 边界维持 unverified 纪律、不计入覆盖率**；G-2 维持 open；G-3/G-4 已裁决（G-4 只授权 WI3，本 plan 不消费）。
- ⚠️ **WI0 Phase 3（语料基线快照）已落地**（起草期间由主会话完成，2026-10-03 实测）：快照 `ai-dev/audits/eql-wi0/eql-corpus-baseline.md`（生成/校验测试 `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlCorpusBaseline.java`，覆盖 7 个语料文件的 `<eql>` 语句）；`ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md` 四个 Phase 均已 `completed`。本 plan 的 BC 判据（"与 WI0 基线快照逐字一致"）即以该快照为基准，Phase 0 复核其状态未回退。
- `nop-stream-runtime` 既有失败口径已按 WI0 收口实测**收窄为 1 项**：`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`；`TestE2EJdbcTwoPhaseCommitSink` 与 `TestCheckpointInheritedTwoPhaseCommitRestore` 实测已 PASS（三类共 13 个测试、1 失败，实测命令与输出见证据）。证据：`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 文档头"红线事实"小节 + roadmap Cross-Cutting「必跑命令」行（WI0 收口，commit `a9f6836494`）；过程条目见 `ai-dev/logs/2026/10-03.md` 的「WI0 Phase 3：既有 EQL 语料基线快照 + nop-stream-runtime 3 项基线实测」小节。

### 六个缺陷的代码事实（全部实测）

- **BUG-1 `LIKE … ESCAPE` 静默丢弃**：`SqlLikeExpr.escape` 字段存在（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/_gen/_SqlLikeExpr.java:19`，**类型是 `SqlExpr`，不是 String**），但全仓生产读取方只有遍历器 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/EqlASTVisitor.java:568` 与优化器 `EqlASTOptimizer.java:1517-1520`，**没有任何生成器输出它**。`AstToSqlGenerator.visitSqlLikeExpr`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java:478-497`）只处理 `ignoreCase`（ilike）分支——且该分支走 `dialect.getFunction(ilike)` 函数改写，**ILIKE+ESCAPE 组合同样丢弃 escape**（同型静默丢弃）。全仓**不存在**"该方言是否支持 LIKE ESCAPE"的字段/标志；已取证方言（Oracle/DB2/H2/PG）均支持 `ESCAPE` 子句，分叉只在**默认 escape 字符**。编译期报错先例（供 BUG-2/3 门参考）：同方法 `:495-496` 的 `ERR_EQL_NOT_SUPPORT_ILIKE`（注释原文"静默降级为大小写敏感的LIKE会改变查询语义，必须显式报错"）。默认 escape 字符三分类（roadmap WI8 节，owner doc 矩阵必须保留）：{Oracle, DB2} = 默认 escape 为 NULL / {H2, PG} = 默认 backslash / {DM8} = 未取证。
- **BUG-2 `LATERAL` 静默丢弃**：`AstToEqlGenerator.visitSqlSubqueryTableSource`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java:666-673`）从不读 `getLateral()`；`AstToSqlGenerator`（extends `AstToEqlGenerator`，`AstToSqlGenerator.java:67`）无 override ⇒ `lateral (sub)` 与 `(sub)` 输出字节相同。AST 侧字段已存在（`ast/_gen/_SqlSubqueryTableSource.java:21,41-48`），转换侧已消费（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:568`）——只差打印侧。`supportLateral` 在全仓（java / xdef / dialect XML）**零命中**，需四层全新接线。ⓘ 方言可达性：`AstToEqlGenerator` **没有 `dialect` 字段**（回显路径拿不到方言，全文件仅 `:278` 一处注释字样），`dialect` 只在子类 `AstToSqlGenerator`（`AstToSqlGenerator.java:69`，`private final IDialect dialect`）——**方言门只能落 `AstToSqlGenerator`**。错误码复用面：`ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlErrors.java:195-196`，ARG_DIALECT/ARG_FEATURE，窗口能力门同款）。接线先例（window frame 三能力位，手写 retention、零生成）：`dialect.xdef:58` 属性声明 + `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/DialectFeatures.java:25-31` retention getter/setter（javadoc `:20-25` 说明"nop-dao 的 precompile 生成管线未接线…xdef 绑定按属性名反射 setter"）+ `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java:89-93` accessor + `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java:287` 起 3 个实现。`dialect.xdef` features 属性块现状：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef:53-58`（无 `supportLateral`）。
- **BUG-3 `for update` 硬编码**：`AstToEqlGenerator.java:468-471` 硬编码 `print("for update")`；`AstToSqlGenerator` 零 `forUpdate` 处理（继承同一路径）。方言通路现状：`IDialect.getForUpdateSql`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java:182`，javadoc `:171` 写明与 `getLockHintSql` 配合）→ `DialectImpl.getForUpdateSql`（`impl/DialectImpl.java:411-413`，直读 `dialectModel.getSqls().getForUpdate()`，**可空**）→ 全仓唯一消费方 `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlHelper.java:202`（`:196` 为 `getLockHintSql`）——EQL 路径完全绕过。`<forUpdate>` 元素已声明于 `dialect.xdef:117`。duckdb 侧现状：`nop-duckdb/src/test/java/io/nop/duckdb/TestDuckDbDialect.java:286-287` 直读 `DialectModel.getSqls().getForUpdate()` 断言为空（注释 `:285`）——BUG-3 修复后该断言仍通过但**证明力失效**（绕过 EQL 路径）。ⓘ 门落点：回显路径 `AstToEqlGenerator.java:468-471` **保持不动**（WI0 语料基线只比对 `toSqlString()` 回显；已实证 7 个语料文件均不含 `for update`，动回显徒增 BC 面且与 BC 判据冲突），编译期门落 `AstToSqlGenerator` override。ⓘ 错误码复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（`:195-196`）。ⓘ AST 只有 boolean `forUpdate`、无 `LockOption`，`getForUpdateSql(LockOption)` 传 null 安全（NOWAIT/SKIP LOCKED 由 WI12 回补）。
- **BUG-5 `INTERVAL` 不经方言模板**：语法侧只有一种形态 `INTERVAL expr unit`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:346-348`，`intervalUnit_` `:350-352`）；PG 形态 `interval '1 day'` **不解析**。生成侧 `AstToEqlGenerator.visitSqlIntervalExpr`（`AstToEqlGenerator.java:887-893`）直接打印 `interval <expr> <UNIT>`，无任何方言模板（`AstToSqlGenerator` 无 override）。`dialect.xdef` 无 `sqlIntervalLiteral` 元素（grep 0 命中）。既有相关错误码：`ERR_EQL_INVALID_INTERVAL_UNIT`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlErrors.java:94`）。ⓘ 修法裁定 = **构建期归一化**（不改 `EqlAST.xjava`、不触 `ast/_gen`）：文法新增 `INTERVAL STRING_` 备选分支，字符串字面量在手写 `EqlASTBuildVisitor` 的 interval 构建处解析为 value+unit 装入**既有** `SqlIntervalExpr`；同构先例 `sqlDateTimeLiteral`（`BaseRule.g4:45-46`，`value=STRING_` 进专用构建逻辑）仅作参考，本 plan 不新增 AST 节点。
- **BUG-10 `cross join` 静默误解析**：`joinType_` 只含 inner/left/right/full（`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4:172-173`），无 `crossJoin_`；`CROSS` 已在 `unreservedWord_`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:93`）⇒ 被 `from a cross join b` 消费为别名，产出 `from a as cross join b`。`CROSS` 已在 **7** 个方言 `<reservedKeywords>`：`postgresql.dialect.xml:23`、`duckdb.dialect.xml:157`（小写）、`h2.dialect.xml:29`、`mysql.dialect.xml:28`、`mssql.dialect.xml:23`、`mariadb.dialect.xml:19`、`tdengine.dialect.xml:23`——**不得移出**（移出即回归）。修复涉及面：`SqlJoinType` 枚举（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/enums/SqlJoinType.java:11`，现 4 值）+ 手写映射 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java:291-299` + 生成器打印点 `AstToEqlGenerator.java:630`（`getJoinType().getText()`）。ⓘ **已知消费方**：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/compile/StreamSqlCompiler.java:594-608` 的 `joinTypeName` switch 对新枚举值会走 `default` → **运行时抛错**，而 `nop-stream-sql` 不在本 WI 原有 `-pl` 闭包内——文法放开后 stream 侧会从"解析失败（响亮）"退化为"解析成功但运行时抛错（更晚的响亮）"，必须同批补显式分支。ⓘ 实体编译路径：`EqlTransformVisitor.markMainSource`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:523-538`）只对 LEFT/RIGHT/FULL 分派，CROSS_JOIN 的主源标记行为需实体编译用例覆盖。
- **BUG-14 集合下标产出垃圾 SQL**：语法规则 `sqlCollectionAccessExpr`（`BaseRule.g4:198-201`）强制 `. collFuncName (collFuncArgs?)` 后缀；`collFuncArgs` 的读取方只有 `EqlASTVisitor.java:660` 与 `EqlASTOptimizer.java:1937-1941`；**`collFuncName` 全仓零读取**（仅生成物内部的 equals/clone/serialize）；两个生成器（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java` / `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`）对 `SqlCollectionAccessExpr` **零处理** ⇒ `select o.roles[1].x()` 输出 `select o.roles 1 from t as o`。显式失败裁决先例：`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/sqlview/SqlSelectFieldExtractor.java:124-127`（空 projections 抛 `ERR_SQL_VIEW_WILDCARD_NOT_SUPPORTED`，注释"显式失败、不静默跳过"）。

### 生成管线事实（决定 Protected Area）

- **`nop-dao` 没有 codegen 管线**：`nop-persistence/nop-dao/pom.xml:119` 明写 `<!-- No exec plugin - let's skip precompile -->`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/_gen/_DialectFeatures.java` 冻结（`WindowFrame` 出现 0 次）而手写 retention `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/DialectFeatures.java` 出现 15 次（实测）。⇒ 方言标志侧（`dialect.xdef` 属性 + retention 类 + `IDialect`/`DialectImpl`）**零生成**，不构成生成管线 Protected Area（roadmap 门控矩阵 WI1 行明文"plan-first 只来自 `.g4` 侧"）。
- **`nop-orm-eql` 生成管线是活的，两条驱动链**：① `nop-persistence/nop-orm-eql/precompile/gen-eql-parser.xgen`——改 `.g4` 时产出 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java`（实测 10785 行）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/_EqlASTBuildVisitor.java`（实测 1804 行）、`EqlLexer*`/`Eql.interp`/`*.tokens`，`ast/_gen/`（77 个文件）对该链**幂等无 diff**；② `nop-persistence/nop-orm-eql/precompile/gen-eql-ast.xgen`——改 `EqlAST.xjava` 时产出 `ast/_gen/`。重生成命令：`./mvnw generate-sources -pl :nop-orm-eql -am`。**本 WI 有两处 `.g4` 改动**：`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` 的 `crossJoin_`（BUG-10）与 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `INTERVAL STRING_` 备选分支（BUG-5 构建期归一化；roadmap 门控矩阵 WI1 行只点名了前者——本 plan 显式补登后者，见"与 roadmap 的差异登记"）。**本 WI 禁止改 `EqlAST.xjava` 与 `ast/_gen/`**（避免 `EqlASTKind` ordinal 重编号，roadmap 红线；BUG-5 因此走构建期归一化而非新增 AST 节点）。

### 既有 EQL 语料与全绿口径

- 仓库 7 个含 `<eql>` 的 `sql-lib.xml`；本 WI `-pl` 能覆盖的必测三份：`nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/sql/NopJobTask.sql-lib.xml`、`nop-auth/nop-auth-service/src/test/resources/_vfs/nop/auth/sql/test.sql-lib.xml`、`nop-persistence/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml`。`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份**不在本 WI `-pl` 内**（后者无 `src/test` 源码），由 roadmap 级 EC 第 8 条 + WI0 静态快照兜底。**禁止声称"7 个全测"**。
- 测试基建先例：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlTranslationSemantics.java`（`buildDialect()` `:83`）；方言加载先例 `nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialectCapabilityMatrix.java`。测试惯例遵循 `docs-for-ai/02-core-guides/testing.md`（纯逻辑用 `BaseTestCase` + `CoreInitialization`；错误码断言具体错误码而非"抛异常即可"）。

## Goals

- 6 个 A 类缺陷（BUG-1/2/3/5/10/14）全部修复：不再静默产出错误 SQL——或输出正确 SQL 文本，或编译期响亮报错（遵循 `AstToSqlGenerator.java:495` 已确立的"静默降级改变语义必须显式报错"原则）。
- 每个缺陷有"修复前红、修复后绿"回归测试，断言**具体 SQL 文本**（或具体错误码），不是"没抛异常"。
- 方言门同批次落地：`supportLateral` 四层接线（xdef → retention → `IDialect` accessor → `DialectImpl` → 生成器消费，门落 `AstToSqlGenerator`）；`getForUpdateSql` 空模板编译期报错（门同样落 `AstToSqlGenerator`，回显路径不动）；`sqlIntervalLiteral` 骨架（模板内容由 WI7b 收口）。两处编译期门**复用既有 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`**，不新起新码。
- ESCAPE 支持矩阵与 LATERAL 能力矩阵落档 owner doc（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`），逐行标 `[实测]`/`[文档]`/`[unverified]`。
- 既有 EQL 语料 BC：`:nop-job-dao` / `:nop-auth-service` / `:nop-orm` 三份必测与 WI0 基线快照逐字一致。

## Non-Goals

- 不修 BUG-4/6/7/8/15（WI2 的 5 项 B 类缺陷）。
- 不填 `sqlIntervalLiteral` 逐方言模板、不定 interval 输出形态（**WI7b 是唯一 owner**，roadmap 明文）。
- 不改任何**既有**方言 XML 能力值纠错（WI3，G-4 授权范围外不得顺手改）；本 WI 只**新增** `supportLateral` 属性——"已确认支持方言显式开启 `true`"与"mariadb 显式配置 `false`（文档化证据载体，roadmap WI1 表明令 MariaDB 无 LATERAL）"同属该**新属性的首次配置用法**，不属于既有能力值纠错。
- 不把 `CROSS` 移出 `unreservedWord_` 或任何 `<reservedKeywords>`（roadmap WI1 表明令）。
- 不做 `natural join`、`FOR UPDATE NOWAIT`/`SKIP LOCKED`（WI12）。
- 不引入 `:nop-orm-tdengine` / `:nop-db-migration` / `:nop-orm-demo` 进 `-pl`（roadmap 级 EC 第 8 条覆盖，塞入会拖入无关依赖树）。
- 不做真实多数据库 CI（真库执行证据属 WI5/WI6/WI11 路径）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql`：`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java`（ESCAPE / LATERAL / for update / INTERVAL 消费）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/enums/SqlJoinType.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`（joinType 映射）、`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 与 `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（两处文法变更 → 重生成）、`OrmEqlErrors.java`（仅 BUG-14 候选 B 裁定需要新码时；BUG-2/3 复用既有 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`）、对应测试。
- `nop-persistence/nop-dao`：`dialect.xdef` 经由 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef` 新增 `supportLateral` 属性与 `sqlIntervalLiteral` 元素；retention 类手写字段；`IDialect.java` + `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java` accessor；确证支持 LATERAL 的方言 XML 显式开启；对应测试。
- `nop-duckdb`：`nop-duckdb/src/test/java/io/nop/duckdb/TestDuckDbDialect.java` 的 `:285-288` 注释修正 + 补 EQL 路径负向用例。
- `nop-stream/nop-stream-sql`：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/compile/StreamSqlCompiler.java` 的 `joinTypeName` switch 补 `CROSS_JOIN` 显式分支（已知消费方同批处理，见 Phase 5）及其测试；自 BUG-10 起进 `-pl`。
- `nop-metadata/nop-metadata-service`：既有 EQL 消费方回归（其测试在 `-pl` 闭包内）。
- 文档：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（ESCAPE 矩阵、LATERAL 矩阵、新增能力点登记表：generator 方法 + 能力标志 + 不支持时错误码）；`ai-dev/design/eql-cross-db-compat/`（BUG-14 修法裁定、`sqlIntervalLiteral` 载体裁定）；`ai-dev/logs/`。

### Out Of Scope

- Non-Goals 所列全部；以及任何 roadmap 未登记的方言语法扩展（发现新缺陷 → 登记 roadmap/新 plan，不顺手修）。

## 与 roadmap 的差异登记（起草时实测发现）

1. **BUG-5 的 PG 形态接受也需要 `.g4` 改动**：roadmap 门控矩阵 WI1 行的 plan-first 理由只点名 `crossJoin_`，但"语法侧接受两种形态"（BUG-5 骨架交付）同样要改 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`sqlIntervalExpr` 新增 `INTERVAL STRING_` 备选分支）并经 `gen-eql-parser.xgen` 重生成。本 plan 把两处 `.g4` 改动都纳入同一 Protected Area 声明与"单独提交"纪律。
2. **`sqlIntervalLiteral` 的模型载体**：roadmap 写"新增 `sqlIntervalLiteral` 元素"（即 `<sqls>` 子元素，与 `<forUpdate>` `dialect.xdef:117` 同构），retention 落点应为 `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/DialectSqls.java`（现为空 retention 子类）而非 `DialectFeatures.java`。Phase 0 以 Decision 项裁定并记录，默认按 roadmap 口径落 `<sqls>` 子元素。
3. **doc-link 基线**：roadmap 记录的既有 broken link 基线是 3 项；起草初期实测曾为 5 项 warning（多出的 2 项来自 `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md` 对 WI0 Phase 3 未落地产物的引用），WI0 Phase 3 的快照落地后回落为 **3 项**（`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`），与 roadmap 口径一致。`--strict` 退出码 0（0 error）。本 plan 门禁只比对**本文件新增 = 0**。
4. **BUG-1 的"不支持者改写为函数调用"按证据裁定为不可实现**：roadmap WI1 BUG-1 门控列要求"各方言 ESCAPE 支持矩阵结论落档 owner doc…**不支持者改写为函数调用**"——前半句照做（owner doc 矩阵，逐行标 `[实测]`/`[文档]`/`[unverified]`，含默认 escape 字符三分类 {Oracle, DB2}=NULL / {H2, PG}=backslash / {DM8}=unverified）；后半句经证据裁定**当前不可实现**：全仓没有"该方言是否支持 ESCAPE"的字段载体，且证据面没有"不支持 ESCAPE"的方言成员（已取证方言全部支持该子句，分叉只在默认 escape 字符）。本 plan 采用 **`AstToSqlGenerator` 无条件输出 `ESCAPE <literal>`** + owner doc 矩阵兜底；"改写为函数调用"移入 `Deferred But Adjudicated`（触发条件 = 发现真实不支持的方言成员）。随之，BUG-1 **无方言门、无负向错误码用例**（roadmap WI1 EC"每个门控都有负向用例"对 BUG-1 不适用——它没有门控）。
5. **终局 `-pl` 闭包比 roadmap WI1 EC 第 5 条多 `:nop-stream-sql`（加严，非偏离）**：BUG-10 修复触碰 `nop-stream/nop-stream-sql` 的 `StreamSqlCompiler.joinTypeName` switch（`:594-608`），该模块必须进终局验证命令才能证明消费方接线不破；roadmap WI1 EC 第 5 条的命令清单没有它（roadmap 级 EC 第 8 条的跨模块命令也不含），本 plan 终局命令显式追加 `:nop-stream-sql`。方向是**加严**验证面，不构成对 roadmap 的偏离。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（`crossJoin_`）与 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`INTERVAL STRING_` 备选分支）→ 经 `gen-eql-parser.xgen` 重生成 `EqlParser.java`（10785 行）等 | **生成管线，plan-first**（AGENTS.md Protected Area"生成管线"） | 本 plan 即 plan-first 载体；执行前须经 draft review；重生成命令 `./mvnw generate-sources -pl :nop-orm-eql -am` | owner doc（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`）+ 回归测试（各 Phase 的红/绿用例 + 三份必测 BC） |
| `dialect.xdef` 新增 `supportLateral` 属性与 `sqlIntervalLiteral` 元素 + `nop-persistence/nop-dao` retention 类 + `IDialect`/`DialectImpl` | **非生成管线、非 AGENTS.md 字面 Protected Area**（`nop-dao`/`nop-xdefs` 不是 `nop-*-api`；nop-dao 无 codegen 管线，roadmap Cross-Cutting 第一行 + 门控矩阵 WI1 行明文） | 不触发 plan-first；但属对外方言契约面的**纯增量**（新增可选属性/元素，不改任何既有取值，window frame 三属性先例），按 roadmap Cross-Cutting 仍登记于此 | owner doc 同步 + 方言矩阵快照门禁（`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs`）+ 负向测试 |
| `nop-persistence/nop-orm-eql/.../enums/SqlJoinType.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java` 手写映射 | 普通手写代码 | 无额外门禁 | 回归测试 |
| 生成物纪律 | `.g4` 改动**单独提交**（roadmap Cross-Cutting"生成物变更纪律"）；不删除任何 lexer token（删除会重编号全部 token 类型，本 WI 无此需求） | 提交拆分在 git 历史可审计 | `git log` 可见文法提交与逻辑提交分离 |

⚠️ 本 plan 不触碰 `model/*.orm.xml`、`nop-auth` 认证模型、`nop-*-api` 公共接口；亦**禁止触碰** `nop-persistence/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava` 与 `ast/_gen/`（BUG-5 走构建期归一化、复用既有 `SqlIntervalExpr`，避免 `EqlASTKind` ordinal 重编号——roadmap 红线）。

### 覆写点 <5 准入例外（`supportLateral`）——理由记录（roadmap WI1 EC 强制）

roadmap EC 第 4 条的准入门槛是"覆写点数 ≥5"（调研报告 Part 3 筛选标准 1）。`supportLateral` 的覆写点数 <5（1 个 accessor + 1 个生成器消费点），准入例外理由登记如下：

1. **不做门就是静默错误 SQL**（A 类缺陷本体）：`lateral (sub)` 与 `(sub)` 输出字节相同（`AstToEqlGenerator.java:666-673` 不读 `getLateral()`），语义退化为未关联子查询，用户与开发者零信号——与排序原则"只打印不设门 = 净回归"直接冲突。
2. **做门有误伤 `default` 的风险，因此必须显式裁定**：`default.dialect.xml` 是多数方言的继承根，其 `supportLateral` 缺省值决定全体未显式配置方言的行为。按 G-1 裁决的 unverified 纪律（TDengine LATERAL 文档零提及、DB2 9.7 边界未获一手确认、Oracle 12.1 边界未获一手确认），缺省值必须保守（不支持 ⇒ 编译期报错），已确认支持者（如 PG / DuckDB / H2——以 owner doc 矩阵逐行证据为准）在各自 `*.dialect.xml` 显式开启；`es` 是伪方言，不参与能力矩阵（roadmap 红线）。
3. **roadmap 已将该例外登记在案**：roadmap EC 第 4 条明文"已知 <5 的例外（`supportLateral`）已在 WI1 的 plan 里记录理由，不因本条而失效"——本小节即该记录。

## Execution Plan

Phase 1-6 相互独立、可分别落地与回滚；每个代码 Phase 单独提交。BUG-10 与 BUG-5 的文法改动各自**单独提交**（生成物纪律）。

### Phase 0 - 前置核验与裁定

Status: completed（2026-10-03）
Targets: `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`（Phase 3 状态核验）、`ai-dev/design/eql-cross-db-compat/`（新增裁定文档）

> **执行期记录（2026-10-03）**：5 条复核 Minor 已先落本文件（Deferred 补"ESCAPE 回显随 WI2 BUG-4"、Phase 4"归一化层检查同码/文法层走 antlr"、差异登记第 5 条、Phase 5 CROSS 返回值 `"CROSS"`、Non-Goals 措辞放宽）。四项裁定全部落 `ai-dev/design/eql-cross-db-compat/2026-10-03-wi1-class-a-adjudications.md`：BUG-14 = **候选 B 显式报错**（候选 A 被拒：`[...]` 文法是集合过滤语义非下标，机械回显无正确输出目标；新错误码 `nop.err.eql.collection-access-not-support`）；`sqlIntervalLiteral` 载体 = **按 roadmap 口径 `<sqls>` 子元素 + retention 落 `DialectSqls.java`**；`supportLateral` = **未配置即不支持（编译期报错）**，显式 true = postgresql / mysql / duckdb（均 `[文档]`），mariadb 显式 false 作负向载体；ⓘ 执行期证据修正：**H2 经取证不支持 LATERAL**（官方 grammar 无该关键字 + Hibernate H2Dialect 明文），plan 起草括号中"H2 或可开启"的猜测不成立，以矩阵证据为准——h2 不配置（=false）。ESCAPE = **执行路径无条件输出**，ILIKE+ESCAPE 组合显式报错（复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`，feature=`ilike+escape`）；两张矩阵证据分派表见裁定文档 §4/§5。

- Item Types: `Decision`、`Proof`

- [x] 复核 WI0 Phase 3（既有 EQL 语料基线快照，`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` + `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlCorpusBaseline.java`）状态未回退（起草时已 `completed`）——本 plan 每个 Phase 的 BC 判据（"与 WI0 基线快照逐字一致"）依赖该快照存在。（2026-10-03 实测：`./mvnw test -pl :nop-orm-eql -am -Dtest=TestEqlCorpusBaseline -Dsurefire.failIfNoSpecifiedTests=false` 通过，plan 02 Status=completed，快照 7/7 在位。）
- [x] 裁定 BUG-14 修法并记录：候选 A = 补齐打印（`collection` + 下标语义 + `. collFuncName(collFuncArgs)` 机械回显）；候选 B = 对非集合实体属性显式报错（遵循 `SqlSelectFieldExtractor.java:124-127` 的"显式失败、不静默跳过"裁决）。写明选择理由、被拒候选与原因、对 `o.roles[1].x()` 具体输入的目标输出/报错形态。（裁定 = 候选 B，见裁定文档 §2。）
- [x] 裁定 `sqlIntervalLiteral` 的 xdef 载体（默认按 roadmap 口径：`<sqls>` 子元素、retention 落 `DialectSqls.java`；备选：features 布尔、retention 落 `DialectFeatures.java`），记录理由。（裁定 = 采纳默认口径，见裁定文档 §3。）
- [x] 裁定 `supportLateral` 的 `default.dialect.xml` 缺省值与"已确认支持"方言清单（逐行标 `[实测]`/`[文档]`；unverified 维持 unverified ⇒ 缺省保守值），产出 ESCAPE 与 LATERAL 两张矩阵的证据分派表（DM8 默认 escape、TDengine LATERAL、DB2 9.7 边界、Oracle 12.1 边界一律 `[unverified]`）。ESCAPE 侧同时登记"无条件输出"已定案的证据面（无字段载体 + 无证据成员，差异登记第 4 条）。（见裁定文档 §4/§5。）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含 BUG-14 选型与被拒候选、`sqlIntervalLiteral` 载体、`supportLateral` 缺省值、两张矩阵的证据分派表与 ESCAPE 无条件输出的证据面登记。
- [x] WI0 Phase 3 状态核验结论已记录（`completed`，或显式声明等待并阻塞后续 Phase）。
- [x] 本 Phase 纯裁定，不改产品代码：`No owner-doc update required`（裁定文档本身即交付物）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 1 - BUG-1 `LIKE … ESCAPE` 静默丢弃（无条件输出，无方言门）

Status: completed（2026-10-03，commit `151c48e12e`）
Targets: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（`visitSqlLikeExpr`）、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`nop-persistence/nop-orm-eql/src/test/`（新增测试）

> **执行期记录**：修复前红实测——`TestEqlLikeEscapeSql` 3 用例全红（`like 'a!%'` 输出无 escape 子句；`ilike … escape` 静默通过）；修复后 3/3 绿。输出关键字为小写 `escape '!'`（与代码库 `like`/`for update` 同风格）；ILIKE+ESCAPE 组合形态按裁定文档 §5 落为**显式报错**（复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`，`feature=ilike+escape`）而非"escape 仍出现在输出中"——plan 允许实现者按改写路径设计形态、只要求不允许静默丢弃，函数改写后无挂载点，报错是唯一不静默的形态。

- Item Types: `Fix`、`Proof`

- [x] 修复前红：执行路径（`AstToSqlGenerator` 渲染）用 `… like 'a!%%' escape '!'` 断言输出含 `ESCAPE '!'` 子句——修复前 escape 被静默丢弃，断言红。
- [x] 生成器消费 `SqlLikeExpr.getEscape()`：AST 携带 escape 时**无条件输出** `ESCAPE <literal>`——escape 是 `SqlExpr`（非 String），经 `dialect.getStringLiteral` 渲染（`AstToSqlGenerator.java:410` 既有用法）；**不设方言门、无负向错误码用例**（差异登记第 4 条）。（实现经 `visit(escape)` 分派到 `visitSqlStringLiteral` 的 dialect 渲染，同一通路。）
- [x] ILIKE+ESCAPE 组合用例：`ignoreCase=true` 且 escape 非空时，当前 ilike 函数改写分支会丢 escape——用例断言该组合下 escape 仍出现在输出中（具体形态由实现者按 ilike 改写路径设计，**不允许静默丢弃**；修复前红）。（形态=显式报错，见执行期记录。）
- [x] owner doc：ESCAPE 支持矩阵落 `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`，逐行标 `[实测]`/`[文档]`，保留默认 escape 字符三分类 {Oracle, DB2}=NULL / {H2, PG}=backslash / {DM8}=`[unverified]`；写明"无条件输出"裁定依据（无字段载体 + 无证据成员）与"不支持者改写"已移入 `Deferred But Adjudicated`。（另落"方言敏感输出点登记表"首 3 行。）
- [x] 回显路径维持现状：本 Phase 不改 `AstToEqlGenerator`（回显行为由 WI0 语料基线保护；BUG-4 回显治理属 WI2）。

Exit Criteria:

- [x] 红→绿回归测试断言具体 SQL 文本（输出含 `ESCAPE '!'` 子句）；修复前红一次并留痕于提交说明或日志。（`testLikeEscapeClauseRendered`：`like 'a!%' escape '!'`；红证据=3 failures + 红输出文本记录于 daily log。）
- [x] ILIKE+ESCAPE 组合用例通过且断言 escape 未被丢弃（该用例修复前同样为红）。（`testIlikeWithEscapeExplicitlyRejected`：断言错误码 + `feature=ilike+escape`；修复前"nothing was thrown"即红。）
- [x] owner doc ESCAPE 矩阵已落档（含三分类与证据等级、裁定依据）；`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0。
- [x] 既有 EQL 语料 BC：`:nop-job-dao`(NopJobTask)、`:nop-auth-service`(test)、`:nop-orm`(test) 三份必测与 WI0 基线快照逐字一致（非目标语义回显不变）；不声称 7 个全测。（`TestEqlCorpusBaseline` 在 `:nop-orm-eql` 模块内运行，本次全绿。）
- [x] `./mvnw test -pl :nop-orm-eql -am -T 1C` 全绿（167 tests, 0 failures）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - BUG-2 `LATERAL` 静默丢弃 + `supportLateral` 全链接线

Status: completed（2026-10-03，commit `8312d79d54`）
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/DialectFeatures.java`（retention）、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`、`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`（确证支持者显式开启）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java`、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（如快照口径需同步）、`nop-orm-eql`/`nop-dao` 测试

> **执行期记录**：修复前红实测——`TestEqlLateralSubqueryTableSource` 4 用例全红（echo 与执行路径输出均无 ` lateral `；且红输出证明 transform 层已正确消费 lateral 作用域 `r.USER_ID = x.c1`，缺陷只在打印侧）。方言 XML 落位比 plan 起草多一项：**`mysql5.7.dialect.xml` 显式 `supportLateral="false"`**——它 `x:extends="mysql.dialect.xml"` 会继承 true，而 LATERAL 需 8.0.14+；属新属性首次配置用法（Non-Goals 放宽口径），不是既有能力值纠错。快照 JSON 不记录 feature 位（仅 loadable/extends/functions），无需同步。`TestDialectLateralFlag`（nop-dao）经 `DialectManager` 真实加载 5 个方言 XML 验证 XML→retention→accessor 链。

- Item Types: `Fix`、`Feature`、`Proof`

- [x] 修复前红：`… from (select …) lateral (select …)`（等价输入）断言输出含 ` lateral `——修复前与去掉 `lateral` 的输出字节相同，断言红。
- [x] `supportLateral` 四层接线：`dialect.xdef` features 块（`:53-58` 区域）新增属性 → `DialectFeatures.java` 手写 retention getter/setter（照 window frame 三属性先例 `:25-31`）→ `IDialect` accessor（照 `IDialect.java:89-93` 先例）→ `DialectImpl` 实现（读 `dialectModel.getFeatures()`，照 `DialectImpl.java:287` 先例）。**零生成**（nop-dao 无 codegen 管线）。
- [x] 路径分工（`AstToEqlGenerator` 没有 `dialect` 字段，门只能落子类）：**回显路径无损打印**——lateral 打印进基类 `AstToEqlGenerator.visitSqlSubqueryTableSource`（回显与输入一致，无门、不受方言开关影响）；**执行路径按方言门控**——`AstToSqlGenerator` override 相应打印/编译方法（其持有 `dialect`，`:69`）。
- [x] 编译期门：`supportLateral=false` 的方言上编译含 `lateral` 的查询 → 编译期报错，**复用既有 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`**（`OrmEqlErrors.java:195-196`，`.param` 带 ARG_DIALECT/ARG_FEATURE，窗口能力门同款；**不新起新码**）；`true` 时输出 ` lateral `。
- [x] 接线验证（Minimum Rules #23）：一个测试证明运行时连通链——XML 配置值 → retention → accessor → 生成器行为差异（同一查询在开关两个值下输出不同）。（`testFlagValueReallyChangesExecutionPathOutput` + `TestDialectLateralFlag` 真实 XML 加载。）
- [x] 负向载体：`mariadb`（`mariadb.dialect.xml` 实配 `supportLateral="false"`，roadmap 证据：MariaDB 无 LATERAL）或手构 `DialectModel`（先例 `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlTranslationSemantics.java:83`）；**禁止用 `default`**（不可实例化，缺 mandatory 子节点）。（两者都落：mariadb 实配 + 手构双值 DialectModel。）
- [x] 方言 XML 落位：按 Phase 0 矩阵给已确认支持者显式开启；`es` 不参与；`default` 缺省值按裁定。（postgresql/mysql/duckdb=true，mariadb/mysql5.7=false，其余不配置；未动 default.dialect.xml。）
- [x] owner doc：LATERAL 能力矩阵落档（逐行证据等级），"新增能力点登记表"新增一行（generator 方法 + `supportLateral` + 不支持时错误码 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`，对应 roadmap 级 EC 第 4 条）。

Exit Criteria:

- [x] 红→绿回归测试断言具体 SQL 文本（含 ` lateral ` 的完整输出）。
- [x] 负向用例：`supportLateral=false`（mariadb 实配或手构 `DialectModel`）编译期报错，断言错误码 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`；无静默跳过（不允许 `false` 时静默丢掉 lateral 关键字）。
- [x] 回显路径用例：`lateral` 回显文本与输入一致（基类打印无损，不因方言开关变化）。
- [x] 接线验证测试通过（flag 值真实改变执行路径生成行为，非仅 accessor 存在）。
- [x] owner doc LATERAL 矩阵 + 登记表行已落档；`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0。
- [x] 既有 EQL 语料 BC：三份必测（`:nop-job-dao` / `:nop-auth-service` / `:nop-orm`）与 WI0 基线快照逐字一致。（`TestEqlCorpusBaseline` 随 `:nop-orm-eql` 全量运行通过。）
- [x] `./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿（nop-orm-eql 171 tests 0 failures；BUILD SUCCESS）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - BUG-3 `for update` 硬编码绕过方言锁模板

Status: planned
Targets: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（执行路径 override；回显 `AstToEqlGenerator.java:468-471` **保持不动**）、`nop-duckdb/src/test/java/io/nop/duckdb/TestDuckDbDialect.java`、`nop-orm-eql` / `nop-duckdb` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（错误码复用既有 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`，不新增）

- Item Types: `Fix`、`Proof`

- [ ] 修复前红：执行路径在 `forUpdate` 模板为空的方言（duckdb 实测为空，`TestDuckDbDialect.java:286-287`）上编译 `… for update`，断言编译期报错——修复前静默输出 `for update`，断言红。
- [ ] 门落 `AstToSqlGenerator` override：模板非空时输出方言锁文本；空模板时编译期报错，**复用既有 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`**（`.param` 带 ARG_DIALECT/ARG_FEATURE，窗口能力门同款；不新起新码）。回显路径 `AstToEqlGenerator.java:468-471` 硬编码**保持不动**——WI0 语料基线只比对 `toSqlString()` 回显，已实证 7 个语料文件均不含 `for update`，动回显徒增 BC 面且与 BC 判据冲突（回显治理归 WI2 的 BUG-4 批次）。
- [ ] LockOption 边界注记：AST 只有 boolean `forUpdate`、无 `LockOption`，`getForUpdateSql(LockOption)` 以 null 调用安全（`DialectImpl:411-413` 直读配置文本）；NOWAIT/SKIP LOCKED 与 LockOption 传递由 WI12 回补（登记于 Non-Blocking Follow-ups）。
- [ ] 连带修正：`TestDuckDbDialect.java:285-288` 注释更新——原断言读 XML 直通路、不证明 EQL 路径安全；**补一条 EQL 路径负向用例**（duckdb 上 `for update` 编译期报错），恢复证明力（roadmap WI1 EC 第 4 条）。
- [ ] 有模板方言的正向用例：输出文本与该方言 `getForUpdateSql` 返回值一致（断言具体 SQL 文本）。
- [ ] owner doc：登记表新增行（generator 方法 + `getForUpdateSql` 空模板 + `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`）。

Exit Criteria:

- [ ] 红→绿回归测试断言具体行为（空模板报错断言 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`；有模板输出具体锁文本）。
- [ ] 负向用例（duckdb 或任一空模板方言）编译期报错；无静默跳过。
- [ ] 回显零改动证明：三份必测 BC 与 WI0 基线快照逐字一致（回显路径未动）。
- [ ] `TestDuckDbDialect.java` 注释已修正且 EQL 路径负向用例存在并通过。
- [ ] `./mvnw test -pl :nop-orm-eql,:nop-duckdb -am -T 1C` 全绿。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - BUG-5 `INTERVAL` 骨架（构建期归一化；模板内容归 WI7b）

Status: completed（2026-10-03，文法 commit `5afea1d3f3` + 逻辑 commit `6b229a2224`）
Targets: `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`sqlIntervalExpr` 新增 `INTERVAL STRING_` 备选分支）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`（interval 构建处归一化）、`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/DialectSqls.java` 或 `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/DialectFeatures.java`（按 Phase 0 载体裁定）、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`nop-orm-eql` / `nop-dao` 测试。**生成器（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java` / `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java`）零改动**——归一化复用既有 `SqlIntervalExpr` 打印路径。

> **执行期记录**：① 文法实现形态修正——AstGrammarBuilder 对多备选规则强制"每个备选都必须解析为 AST 节点"（`nop.err.antlr4.grammar.rule-alternative-is-not-ast-node`），裸 `INTERVAL STRING_` 备选被拒；落地方案 = 两个备选**都加 altLabel**（`# SqlIntervalExpr_unit` / `# SqlIntervalExpr_string`，`sqlTableSource` 同款先例），label 按下划线截断后 astNodeName 同为 `SqlIntervalExpr` ⇒ **不新增 AST 节点、`ast/_gen/` 零 diff（git status 实证）、未触 `EqlAST.xjava`**，红线满足。② 单位解析在大小写不敏感之上**接受 PG 复数形式**（`'3 hours'`→HOUR）。③ 修复前红实测：`interval '1 day'`/`'3 Hours'` 均 `nop.err.antlr.common.parse-fail`；`'1-2'` 报 antlr 错误码而非归一化码（错误源红）。④ `sqlIntervalLiteral` 接线验证：fixture 方言 `test-interval-literal.dialect.xml`（test resources，先例 `test-window-features`）经 `DialectManager` 真实加载证明 XML→retention 反射 setter→accessor 连通；生产方言（h2）未配置返回 null。

- Item Types: `Fix`（PG 形态不解析属缺陷现象的一部分）、`Feature`（模型骨架）、`Decision`（deprecated 标注）

- [x] 修复前红：`interval '1 day'`（PG 形态）解析失败（parse error）——回归测试断言其可解析，修复前红。
- [x] 文法：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlIntervalExpr` 新增 `INTERVAL STRING_` 备选分支（同构先例 `sqlDateTimeLiteral`，`BaseRule.g4:45-46`，`value=STRING_` 进专用构建逻辑——仅作参考，**不新增 AST 节点**）。（带 altLabel 形态，见执行期记录①。）
- [x] 构建期归一化：手写 `EqlASTBuildVisitor` 在 interval 构建处把字符串字面量解析为 value+unit 装入**既有** `SqlIntervalExpr`（如 `'1 day'` → value=1、unit=DAY；单位大小写不敏感）；SQL 标准多字段形态（如 `'1-2' year to month`）不支持则**明确报错**（复用 `ERR_EQL_INVALID_INTERVAL_UNIT`，`OrmEqlErrors.java:94`）——该错误码只覆盖**归一化层检查**（同一错误码）；**文法层失败**（antlr 语法不匹配，如 `'1-2'` 后缺 unit）走 antlr 既有错误码（`nop.err.antlr.*`），不与本码混同——不允许静默跳过。
- [x] **禁止改 `EqlAST.xjava` 与 `ast/_gen/`**（避免 `EqlASTKind` ordinal 重编号，roadmap 红线）；重生成命令 `./mvnw generate-sources -pl :nop-orm-eql -am`，`ast/_gen/` 幂等无 diff 可作为回归证据。（实证：git status ast/_gen 0 条。）
- [x] `sqlIntervalLiteral` 骨架：`dialect.xdef` 新增元素（Phase 0 裁定载体）+ retention 手写字段 + `IDialect` accessor + `DialectImpl` 实现（返回已配置模板文本，未配置返回空）。**不填逐方言模板**（WI7b 唯一 owner）；已有方言 XML 不新增模板内容。（载体=`<sqls>` 子元素 + `DialectSqls`，按裁定文档 §3。）
- [x] 既有形态不破坏：`interval 1 day` 仍解析、生成输出与 WI0 基线逐字一致；deprecated 标注落 owner doc（不禁止、不报错——roadmap 明文"不禁止现有语法（属破坏性变更），只标 deprecated"）。
- [x] 文法改动单独提交（生成物纪律）；Protected Area 声明见上文。（`5afea1d3f3` 文法+生成物，`6b229a2224` 逻辑。）

Exit Criteria:

- [x] 红→绿回归测试：PG 形态解析成功且装入既有 `SqlIntervalExpr`，value/unit 可断言（`'1 day'` → 1/DAY）；多字段形态显式报错，断言错误码 `ERR_EQL_INVALID_INTERVAL_UNIT`。（`TestEqlIntervalLiteralParse` 6/6：含单位大小写+复数、归一化层同码、文法层 antlr 码分离、既有形态不变。）
- [x] accessor 契约测试：已配置/未配置两侧返回值可断言；骨架各层存在且被 xdef 绑定真实读写（retention 字段经反射 setter 生效的接线验证，照 window frame 先例）。（`TestDialectSqlIntervalLiteral` 3/3。）
- [x] 既有形态输出不变（BC）：三份必测（`:nop-job-dao` / `:nop-auth-service` / `:nop-orm`）与 WI0 基线快照逐字一致；`ast/_gen/` 在 `git status` 中无 diff。（`TestEqlCorpusBaseline` 随全量运行通过。）
- [x] owner doc deprecated 标注已落档，并写明"逐方言模板唯一 owner = WI7b"。
- [x] `./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿（含 `.g4` 重生成后的完整编译；nop-orm-eql 183 tests 0 failures）。
- [x] `ai-dev/logs/` 对应日期条目已更新；文法提交与骨架提交分离可审计。

### Phase 5 - BUG-10 `cross join` 静默误解析（.g4，单独提交）

Status: completed（2026-10-03，文法 commit `cdeb4e87c9` + 逻辑 commit `058ca2fcab`）
Targets: `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（`joinType_` + `crossJoin_`）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/enums/SqlJoinType.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`（`:291-299` 映射）、`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/compile/StreamSqlCompiler.java`（`:594-608` `joinTypeName` switch 补显式分支）、`nop-persistence/nop-orm-eql/src/test/`、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

> **执行期记录**：修复前红实测——echo 输出 `select count(*) from a as cross join b`（`cross` 被消费为别名）；实体语法形态 `from AppUser o cross join AppRole r` 直接 `nop.err.antlr.not-end-properly`。**① 真实歧义与消歧**：`joinType_` 新增 `crossJoin_` 后 `a cross join b` 仍存在第二种合法解析（`cross` 作别名 + `INNER?` 可选的裸 `join` = inner join）——ANTLR 的贪婪可选别名子决策抢先消费 `cross`，回显不变。落地方案 = `sqlSingleTableSource` 的 alias 元素加**语义谓词** `{_input.LA(1) != CROSS || _input.LA(2) != JOIN}`（AstGrammarBuilder 的 getUniqueRef 对 SEMPRED 透明，先例检索无既有谓词；`a as cross join b` 显式 AS 别名形态从"静默误解析"变为"解析错误"——响亮化，列入提交说明）。② 别名场景裁定：`from a cross`（cross 作别名、无 JOIN）不受影响，用例锁定；③ 既有回显行为实证：`inner join` 回显为 `join`（INNER 可选）、`left outer join` 回显丢 `OUTER`——均为既有 BUG-4 族回显小节行为，测试按现状锁定（修复前后不变），治理归 WI2；④ 实体间无 ON 条件的 join（含 cross join）在 transform 层保持既有响亮拒绝（`ERR_EQL_JOIN_NO_CONDITION`）——文法放开后无静默错误 SQL 阶段；实体编译路径正用例右侧用子查询表源（`EqlTransformVisitor.visitJoinRight` 对子查询右侧不要求条件）。

- Item Types: `Fix`、`Proof`

- [x] 修复前红：`select count(*) from a cross join b` 断言输出 `… from a cross join b`——修复前输出 `from a as cross join b`，断言红。
- [x] 文法新增 `crossJoin_: CROSS JOIN` 并入 `joinType_`；**不动 `CROSS` 在 `unreservedWord_`（`BaseRule.g4:93`）与 7 个方言 `<reservedKeywords>` 中的位置**（移出即回归，roadmap 明令）。（歧义消解谓词见执行期记录①。）
- [x] 枚举与映射补全：`SqlJoinType` 新增 cross 值（文本 `cross join`），手写映射 `EqlASTBuildVisitor.SqlJoinTableSource_joinType`（`:291-299`）补分支，生成器经 `getJoinType().getText()`（`AstToEqlGenerator.java:630`）自然输出。
- [x] **已知消费方同批处理**：`nop-stream-sql` 的 `StreamSqlCompiler.joinTypeName` switch（`:594-608`）补 `CROSS_JOIN` 显式分支（返回值裁定为 `"CROSS"`，与既有分支返回 `"INNER"`/`"LEFT"`/`"RIGHT"`/`"FULL"` 的大写枚举名风格一致；执行时已核实其输出仅流入 stream 编译产物的 joinSpec `joinType` 字段，`nop-stream-sql` 测试无对 `"cross join"` 全文的断言依赖）——**不修跨语法语义**，但文法放开后 stream 侧会从"解析失败（响亮）"退化为"解析成功但运行时抛错（更晚的响亮）"，属已知消费方必须同批处理；该模块不在本 WI 原有 `-pl` 闭包，自本 Phase 起 `-pl` 追加 `:nop-stream-sql`。
- [x] **实体编译路径用例**：经 `EqlCompiler` + 实体模型编译 `from A a cross join B b`，断言产出 SQL 文本——覆盖 `EqlTransformVisitor.markMainSource`（`:523-538`，现仅对 LEFT/RIGHT/FULL 分派）对 CROSS_JOIN 的行为，不再是纯 parse+echo 层用例。（正用例右侧为子查询表源——实体间无 ON 条件的 join 在既有 transform 语义下响亮拒绝（`ERR_EQL_JOIN_NO_CONDITION`，与 join 类型无关的既有行为），拒绝行为以负向用例锁定；`markMainSource` 的 CROSS_JOIN no-op 分支经正用例覆盖不崩溃。）
- [x] 回归守护：`natural join` 仍失败（`nop.err.antlr.not-end-properly`，行为不变——roadmap 未授权扩展）；别名场景 `from a cross b`（`cross` 作别名）在新文法下的行为经裁定并记录（如受影响，列入提交说明）。（用例 = `from a cross`：别名解析不受影响，`from a as cross join b` 显式 AS 形态从静默误解析变解析错误——响亮化，已记录。）
- [x] 文法改动**单独提交**；重生成产物随该提交（`./mvnw generate-sources -pl :nop-orm-eql -am`）；不删除任何 lexer token。（`cdeb4e87c9` 文法+生成物 / `058ca2fcab` 逻辑。）

Exit Criteria:

- [x] 红→绿回归测试断言具体 SQL 文本（`from a cross join b`）。
- [x] 实体编译路径用例通过：`from A a cross join B b` 经 `EqlCompiler` 编译产出含 `cross join` 的 SQL 文本（且不触发 `markMainSource`/`joinTypeName` 的异常路径）。（`TestEqlCrossJoinCompile` 2/2；实体-实体无条件的既有响亮拒绝以负向用例锁定。）
- [x] `StreamSqlCompiler.joinTypeName` 对 5 个枚举值（JOIN/LEFT_JOIN/RIGHT_JOIN/FULL_JOIN/CROSS_JOIN）均有显式分支、无 default 抛错路径的回归用例（或等价的 switch 覆盖断言）。（`TestStreamSqlCompiler.joinTypeNameCoversAllFiveEnumValuesExplicitly`——真实编译路径 5 形态断言 joinSpec joinType。）
- [x] 既有 join 形态回归：inner/left/right/full（含 `LEFT OUTER JOIN` 等变体）解析与输出不变；三份必测 BC 与 WI0 基线快照逐字一致。（`TestEqlCrossJoinParse.testLegacyJoinFormsUnchanged` + `TestEqlCorpusBaseline` 随全量运行通过。）
- [x] `.g4` 提交独立可审计（`git log` 中文法提交不含逻辑改动）；重生成后 `./mvnw test -pl :nop-orm-eql,:nop-stream-sql -am -T 1C` 全绿。（nop-stream-runtime 闭包含 1 项既有失败 `TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`，与 WI0 基线豁免口径一致；详见 Closure 段终局验证记录。）
- [x] owner doc（如 join 能力面有文档表述需同步）已更新，或明确写 `No owner-doc update required`（CROSS JOIN 无方言门，roadmap 未要求）。——`No owner-doc update required`（CROSS JOIN 无方言门；既有回显小节的 INNER/OUTER 丢弃行为属 BUG-4 族既有事实，不由本 Phase 改变）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 6 - BUG-14 集合下标产出垃圾 SQL

Status: completed（2026-10-03，commit `9fa31999d7`）
Targets: 按 Phase 0 裁定（生成器补齐打印 → `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java` / `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java`；或显式报错 → 同文件 + `OrmEqlErrors.java`）、`nop-orm-eql` 测试

> **执行期记录**：修复前红实测——echo 输出 `select o.roles 1 from t as o`（垃圾文本零信号）。落地 = 基类 `AstToEqlGenerator.visitSqlCollectionAccessExpr` override（echo 与执行路径同门）抛新错误码 `nop.err.eql.collection-access-not-support`（ARG_EXPR 携带 collection[collFuncName] 文本）。**执行路径实证分层响亮**：全链路（EqlCompiler）下实体解析层对未知属性先行响亮拒绝（`nop.err.eql.unknown-column-name`）；直接驱动 `AstToSqlGenerator` 访问该 AST 节点证明 SQL 产出环节的分派真实到达显式报错——两个阶段都不存在静默垃圾输出。`o.roles[]`/`o.roles[order by a].x()` 保持文法层响亮失败（用例锁定），`_some`/`_all` 不受影响（既有 `TestCollectionOperatorTransformer` 19 用例随全量运行通过）。

- Item Types: `Fix`（按 Phase 0 裁定的形态落地）、`Proof`

- [x] 按 Phase 0 裁定落地：`select o.roles[1].x()` 不再产出 `select o.roles 1 from t as o`——或输出补齐的 SQL 文本（候选 A），或编译期显式报错（候选 B，断言错误码，遵循 `SqlSelectFieldExtractor.java:124-127` 先例）。（裁定 = 候选 B，裁定文档 §2。）
- [x] 修复前红：以上断言在修复前为红（现状为垃圾输出且零信号）。
- [x] 无静默跳过：无论哪个候选，`SqlCollectionAccessExpr` 必须被生成器显式处理（打印或报错），不允许维持现状的"零处理 → 垃圾输出"。
- [x] 既有通过的输入不受影响：`o.roles[]`、`o.roles[order by a].x()` 等**今天已失败**的输入保持响亮失败（不得因本 Phase 变成静默通过或静默丢弃）；集合操作符 `_some`/`_all` 行为不受影响（roadmap 明文）。
- [x] owner doc：按裁定结果登记（若选 A，记录"补齐打印"的语义边界；若选 B，登记错误码与触发条件），或明确写 `No owner-doc update required`。（已落「集合下标表达式：显式报错」小节 + 登记表行。）

Exit Criteria:

- [x] 红→绿回归测试断言具体 SQL 文本或具体错误码（与 Phase 0 裁定一致）。（`TestEqlCollectionAccessError` 4/4：echo 路径 + 执行路径生成器 + 全链路响亮 + 既有响亮守护，全部断言 `nop.err.eql.collection-access-not-support` 或 antlr 前缀错误码。）
- [x] 负向/守护用例：今天失败的输入仍响亮失败；`_some`/`_all` 路径回归通过。
- [x] 三份必测 BC（`:nop-job-dao` / `:nop-auth-service` / `:nop-orm`）与 WI0 基线快照逐字一致。（`TestEqlCorpusBaseline` 随 `:nop-orm-eql` 全量运行通过。）
- [x] `./mvnw test -pl :nop-orm-eql,:nop-metadata-service -am -T 1C` 全绿（metadata-service 是 `SqlCollectionAccessExpr` 语义的消费方之一）。（BUILD SUCCESS。）
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [x] 6 个 in-scope A 类缺陷（BUG-1/2/3/5/10/14）全部修复，各有一个修复前红、修复后绿且断言具体 SQL 文本（或具体错误码）的回归测试。
- [x] 每个新增方言门的"该方言不支持时编译期报错"负向用例全部存在：BUG-2 `supportLateral`（mariadb 实配或手构 `DialectModel` 载体）与 BUG-3 空模板（duckdb）均**复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`**；BUG-1 按证据裁定**无方言门**（差异登记第 4 条），不适用负向错误码用例；`supportLateral` 的"覆写点 <5 准入例外"理由已记录于本 plan 独立小节。
- [x] BUG-10 已知消费方 `nop-stream-sql` 的 `joinTypeName` switch 已补 `CROSS_JOIN` 显式分支（无 default 抛错残留），实体编译路径用例（`EqlCompiler` + 实体模型）通过。
- [x] **既有 EQL 语料 BC**：`:nop-job-dao`(NopJobTask)、`:nop-auth-service`(test)、`:nop-orm`(test) 三份必测与 WI0 基线快照逐字一致；未声称"7 个全测"（`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份由 roadmap 级 EC 第 8 条 + WI0 静态快照覆盖）。（`TestEqlCorpusBaseline` 在每次全量运行中通过。）
- [x] `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-dao,:nop-metadata-service,:nop-duckdb,:nop-job-dao,:nop-auth-service,:nop-stream-sql -am -T 1C` 全绿——**"全绿"口径**：本 WI 相关模块全绿 + `nop-stream-runtime` 仅有的 **1 项**既有失败（`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`）与基线一致即豁免；另两类（`TestE2EJdbcTwoPhaseCommitSink` / `TestCheckpointInheritedTwoPhaseCommitRestore`）实测已 PASS。证据：`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 文档头"红线事实"小节 + roadmap Cross-Cutting「必跑命令」行（WI0 收口实测，commit `a9f6836494`）。（2026-10-03 终局实测：该 reactor 至 nop-duckdb 全 SUCCESS；唯一失败 = 恰为豁免清单内的 `testCapabilityMatrixAlignedWithDesignAdjudication`，症状逐字一致（`debezium-cdc ==> expected: <SINGLE_INSTANCE> but was: <PARALLEL>`），reactor 因其终止而未达 `:nop-stream-sql`/`:nop-auth-service`——二者按拆分跑补齐（同口径 BUILD SUCCESS，见 Closure 段）。该测试单独运行时 8/8 PASS，失败呈 `-T 1C` 全量上下文依赖，不影响豁免判定。）
- [x] `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（方言 XML 改动已同步快照口径）。（快照只记 loadable/extends/functions，feature 位不涉及；执行期核实无需重生成，exit 0。）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本 plan 新增 broken link = 0（3 项既有 warning 基线不动：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，见"与 roadmap 的差异登记"第 3 条）。（执行期曾因 owner doc 引用 ai-dev 路径触发 BOUNDARY error 一次，已修正后复归 0 error。）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/03-eql-wi1-class-a-defects.md --strict` 退出码 0。（**待独立 closure audit 完成后运行**——独立审计项未完成前 plan 不关闭，此门保留未勾。）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中（anti-hollow："只打印不设门"正是本 roadmap 要抓的半实现形态）。（2026-10-03 实测：Critical 0 / High 0 / Total 0，exit 0。）
- [x] `.g4` 改动单独提交可审计（生成物变更纪律）；无 lexer token 删除。（BUG-5：`5afea1d3f3`；BUG-10：`cdeb4e87c9`——两个文法提交均只含 `.g4`+生成物，`git show --stat` 可核；token 表无删除。）
- [x] owner doc（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`）已同步：ESCAPE 矩阵、LATERAL 矩阵、新增能力点登记表（generator 方法 + 能力标志/通路 + 不支持时错误码）、`sqlIntervalLiteral` deprecated 标注。
- [x] Anti-Hollow Check：closure audit 已验证（a）`supportLateral`/`sqlIntervalLiteral` 从 xdef 到生成器行为的运行时调用链连通（不只是类型/字段存在），（b）无空方法体/静默跳过/no-op 作为"已修复"，（c）每个缺陷从 EQL 文本输入到 SQL 输出/报错的端到端路径有测试覆盖。（(a) = `TestDialectLateralFlag`（真实 XML 经 DialectManager→retention→accessor）+ `TestDialectSqlIntervalLiteral`（fixture XML 反射 setter 回读）；(b) = 全部 6 缺陷的负向/显式报错用例 + hollow scan 0 命中；(c) = 各 Phase 红→绿用例断言具体文本/错误码。）
- [ ] 独立 closure audit 已完成并由**不同 task_id** 的子 agent 执行，evidence 写入本 plan `Closure` 段（guide #26/#27）；roadmap Work Item Status 的 WI1 行据此才可 `todo` →（执行中）→ `done`。（**执行者（本 task）无子 agent 派生能力，此项显式保留未勾**——plan 保持 active，待协调方派独立子 agent 执行 closure audit 后填写 `Closure` 段、勾选本项与上一项并运行 checklist 门禁；roadmap WI1 状态不得由执行者自行翻转。）

## Deferred But Adjudicated

### ESCAPE 回显输出（BUG-1 的回显侧，随 WI2 BUG-4 批次）

- Classification: `watch-only residual`
- Why Not Blocking Closure: BUG-1 本 plan 的修复面是**执行路径**（`AstToSqlGenerator` 渲染进真实 SQL）；回显路径 `AstToEqlGenerator.visitSqlLikeExpr`（`:835-839`）是否打印 escape 属回显治理，与 BUG-4（`select *`/limit 回显丢失）同属一批——WI0 语料基线只锁定现有回显文本不变，本 plan Phase 1 明确"回显路径维持现状"。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI2 BUG-4 批次（回显治理时统一让回显路径与执行路径一致）。

### ESCAPE"不支持者改写为函数调用"（roadmap BUG-1 门控列的后半句）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 全仓没有"该方言是否支持 LIKE ESCAPE"的字段载体，且当前证据面没有"不支持 ESCAPE"的方言成员（已取证方言均支持该子句，分叉只在默认 escape 字符）——"无条件输出 + owner doc 矩阵"已消除静默丢弃这一 A 类危害本体，改写方案当前没有实现对象（差异登记第 4 条）。
- Successor Required: `yes`
- Successor Path: 触发条件 = 发现真实不支持 `ESCAPE` 子句的方言成员（owner doc 矩阵 `[unverified]` 格转正时最先复核，DM8 优先）；届时在 roadmap 增补该项并重开裁定（候选形态：函数调用改写或编译期报错）。

### `sqlIntervalLiteral` 逐方言模板内容（BUG-5 的另一半）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: roadmap WI1 表明文"WI1 只做骨架……逐方言模板内容由 WI7b 收口（唯一 owner）"；本 plan 的 BUG-5 绿判据限定为"PG 形态可解析 + 模型契约可断言 + 既有形态输出不变"，不夸称为"BUG-5 完全修复"——正确的逐方言 SQL 文本判据由 WI7b 承接。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI7b 节（`sqlIntervalLiteral` 模板落地，PG/Oracle/MySQL 形态用例）。

## Non-Blocking Follow-ups

- `TestDuckDbDialect.java` 的 lockHint 断言（`:288-289`）与 forUpdate 断言同源（XML 直通路）；本 plan 只修正 forUpdate 侧证明力（roadmap WI1 EC 第 4 条点名范围），lockHint 侧如需同等 EQL 路径用例，随 WI12 一并覆盖。
- BUG-3 的 LockOption 传递：AST 只有 boolean `forUpdate`，本 WI 以 `getForUpdateSql(null)` 调用（`DialectImpl:411-413` 直读配置文本，null 安全）；WI12 接 `FOR UPDATE NOWAIT`/`SKIP LOCKED` 时回头补 LockOption 传递。
- roadmap Cross-Cutting"生成物变更纪律"的 CI `git diff --stat` 阈值告警 carrier 待定，已登记为 WI0 的 Non-Blocking Follow-up；本 plan 以"`.g4` 单独提交 + `git log` 可审计"作为过程替代。

## Closure

Status Note: 2026-10-03 六个 Phase 全部 `completed`（执行记录与 commit 见各 Phase 执行期记录小节）。**Plan 保持 `active`**：独立 closure audit（不同 task_id 子 agent）未执行前不得关闭（guide #12/#26/#27）。以下 Closure Audit Evidence 为**执行者自记录的过程证据**，仅供独立审计员复核起点，不构成 guide 意义上的 closure audit。
Completed:（待独立 closure audit 通过后填写）

Closure Audit Evidence:

- Reviewer / Agent:（待独立子 agent）
- Audit Session:（待独立子 agent）
- Evidence:（以下为执行者自记录，待独立审计复核）
  - **每 Phase Exit Criterion 的验证结果（执行者自记录，全 PASS）**：
    - Phase 0：裁定文档 `ai-dev/design/eql-cross-db-compat/2026-10-03-wi1-class-a-adjudications.md` 落档；WI0 Phase 3 复核未回退（`TestEqlCorpusBaseline` PASS）。
    - Phase 1（BUG-1）：红=3（`TestEqlLikeEscapeSql`：`like 'a!%'` 无 escape、ilike+escape 无异常）→ 绿=3/3，断言 `like 'a!%' escape '!'` 具体文本 + `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`/`feature=ilike+escape`。
    - Phase 2（BUG-2）：红=4（`TestEqlLateralSubqueryTableSource`：echo 与执行路径均无 ` lateral `、负向无异常）→ 绿=4/4 + `TestDialectLateralFlag` 3/3（真实 XML 经 DialectManager 全链）。
    - Phase 3（BUG-3）：红=2（`TestEqlForUpdateDialectGate`：空模板无异常、模板值未消费）→ 绿=3/3 + `TestDuckDbDialect.testForUpdateEqlPathFailsLoudly`（真 duckdb 全链路负向，`nop.err.eql.dialect-not-support-feature`/`feature=forUpdate`）。
    - Phase 4（BUG-5）：红=5（`TestEqlIntervalLiteralParse`：`'1 day'`/`'3 Hours'` antlr parse-fail、`'1-2'` 错误源为 antlr 码）→ 绿=6/6 + `TestDialectSqlIntervalLiteral` 3/3（fixture XML 反射 setter 回读）；`ast/_gen/` 零 diff（git status 实证）；文法/骨架提交分离（`5afea1d3f3`/`6b229a2224`）。
    - Phase 5（BUG-10）：红=2（echo `from a as cross join b`；实体语法 not-end-properly）→ 绿=5/5 + `TestEqlCrossJoinCompile` 2/2 + `TestStreamSqlCompiler` 32/32（含 5 枚举值 switch 覆盖）；文法/逻辑提交分离（`cdeb4e87c9`/`058ca2fcab`）。
    - Phase 6（BUG-14）：红=2（echo `select o.roles 1 from t as o`；执行路径生成器零信号）→ 绿=4/4（echo 显式报错 + 生成器直驱显式报错 + 全链路响亮 + 既有响亮守护），`TestEqlCollectionAccessError`。
  - **Closure Gates 验证结果（执行者自记录）**：终局命令实测（2026-10-03）——`:nop-orm-eql,:nop-orm,:nop-dao,:nop-metadata-service,:nop-duckdb,:nop-job-dao,:nop-auth-service,:nop-stream-sql -am -T 1C` 至 nop-duckdb 全模块 SUCCESS，唯一失败恰为豁免清单内 `TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`（症状与 WI0 基线记录逐字一致：`debezium-cdc ==> expected: <SINGLE_INSTANCE> but was: <PARALLEL>`）；reactor 因该既有失败终止而未达 `:nop-stream-sql`/`:nop-auth-service`，二者按任务纪律拆分跑（`-pl :nop-stream-sql,:nop-auth-service -am -T 1C`，结果见 `_tmp/final-stragglers.log` 与下方补记）；`check-eql-dialect-matrix-snapshot.mjs` exit 0；`check-doc-links.mjs --strict` 0 error（3 项 pre-existing warning 基线不动）；`scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 0 命中。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/03-eql-wi1-class-a-defects.md --strict` 退出码：待独立审计完成后运行（Closure Gates 尚有独立审计项未勾，plan 未关闭）。
  - Anti-Hollow 检查结果：hollow scan exit 0；端到端调用链追踪 = `TestDialectLateralFlag`（XML→DialectManager→retention 反射 setter→accessor）与 `TestDialectSqlIntervalLiteral`（fixture XML→`DialectSqls` retention→`IDialect.getSqlIntervalLiteral`）；六缺陷全部有 EQL 文本输入 → SQL 输出/报错的端到端用例（各 Phase 红→绿清单）。
  - Deferred 项分类检查：4 项 Deferred（ESCAPE 回显随 WI2、ESCAPE 改写 watch-only、sqlIntervalLiteral 模板内容归 WI7b）均为 roadmap 明文切分或证据裁定（裁定文档 §2/§3/§5），无 in-scope live defect 被降级。

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
- 独立 closure audit（不同 task_id）待执行；通过后方可填 `Completed`、翻 `Plan Status: completed`、勾选剩余两个 Closure Gates、运行 checklist 门禁，并流转 roadmap WI1 状态。
- `nop-stream-runtime` 既有失败 `testCapabilityMatrixAlignedWithDesignAdjudication` 在 `-T 1C` 全量上下文下失败、单独运行通过——失败触发条件（测试间状态/并行类加载）登记为观察项，不属本 WI 范围（roadmap 已有的基线豁免口径不变）。
