# 25 EQL WI9a — CAST 类型系统·语法与别名（NUMBER/TEXT/BOOL/UUID + `dataTypeName_` 必拒项收窄）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI9a 节 `:319-325`、门控矩阵 WI9a 行 `:114`、共同约束 1-4 `:263-280`、Cross-Cutting 全部 `:378-388`、关键约束与红线节 `:456-465`、roadmap 级 Exit Criteria 第 6/7/8 条 `:442-453`）
> Related: `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`（硬前置，`completed`）、`ai-dev/plans/19-eql-wi2-class-b-defects.md`（硬前置，`completed`；本 plan 的结构先例与 BOOLEAN 基线）、`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`（G-1/G-3 已裁决 —— G-3 授权本 plan 的 `stdNameMap` 三别名与其 114 orm.xml 回归门）

## Purpose

收口 roadmap WI9a：在 WI2 已落地的 `BOOLEAN` 之上补齐 CAST 目标类型的"解析接受集 = 语义接受集"。

1. `stdNameMap` 别名：`TEXT→CLOB` / `NUMBER→NUMERIC`（或按 Phase 0 裁定的修正目标）/ `BOOL→BOOLEAN`，使 `cast(x as text/number/bool)` 从"解析过、AST 响亮拒"变为全链可用——G-3 已裁决接受（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md` §四）。
2. `UUID`：按 Phase 0 裁定的形态落地（G-3 未覆盖 UUID，授权依据见"与 roadmap 的差异登记"第 2 条）。
3. 收窄 `dataTypeName_` 的必拒项（BUG-11）：删除"解析可过、AST 校验必拒"的死文法备选，使文法接受面不再说谎，并产出 roadmap EC ③ 要求的"被移除的拒绝项 → 现在接受 → 生成什么 SQL"对照表。

一个 WI、两个 Protected Area（`.g4` 的 `dataTypeName_` → 生成管线 plan-first；`stdNameMap` 别名 → 模型层行为变更），同属"类型名解析"一个结果面，容于一个 plan（roadmap WI9a 节明文）。**WI9b（时区类型）必须独立成 plan，本 plan 不碰**（边界见 Non-Goals 与 Current Baseline 末节）。

## Current Baseline

以下事实全部于 2026-10-03 在本工作区分支 `feat/eql-cross-db-compat` 实测核验（含 file:line；WI1/WI2 已落地，行号漂移风险低于 WI2 起草时点，但执行时仍一律以符号定位为准，每个 Phase 开工前先复核锚点符号仍存在）。

### 硬前置状态（全部满足，无排队门）

- WI0 `completed`（`ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`，closure 提交 `f690c765fd`）：语料基线快照 `ai-dev/audits/eql-wi0/eql-corpus-baseline.md` + `TestEqlCorpusBaseline`、方言矩阵快照 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json` + 门禁 `ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` 均在位。
- WI2 `completed`（`ai-dev/plans/19-eql-wi2-class-b-defects.md`，closure 审计通过，收口提交 `90806e869e`）：BOOLEAN 文法备选（提交 `9a76048670`）、BUG-15 显式拒绝守卫（提交 `eadb34029d`）已落地。
- G-3 已裁决（2026-10-03）：**接受** `TEXT→CLOB` / `NUMBER→NUMERIC` / `BOOL→BOOLEAN` 三别名进 WI9a；实测全仓 114 个 orm.xml（含 19 个生成 `_app.orm.xml`）的 `stdSqlType="TEXT"/"NUMBER"/"BOOL"` 均 **0 次**（本 plan 起草时 case-insensitive 复核仍 0，`UUID` 亦 0），纯增量、零既有模型受影响；**落地门 = 114 个 orm.xml 全量回归（裁决字面 `./mvnw clean install -T 1C`；本 plan 执行形态 = 下文"既有 EQL 语料与全绿口径"节的组合判据 (a)）+ "加别名会改变哪些既有模型解析结果"的影响清单交付**（裁决 §四，本 plan Phase 1 承接）。
- G-1 已裁决（2026-10-03）：采纳已确认门为承诺基线。本 plan 的类型别名不涉及方言版本门代码（区别于 WI2 BUG-8 的 Oracle 23ai 门——`BOOLEAN` 已在 WI2 收口）。
- roadmap 门控矩阵 WI9a 行（`:114`）要求的前置 = WI0、G-3、WI2、G-1 —— 全部满足。

### WI3 并行实施注记（方言 XML 锚点漂移声明）

WI3（`ai-dev/plans/20-eql-wi3-dialect-config-fixes.md`，plan `active`、Phase 0 已 completed 且代码分批落地）与本 plan 并行实施，正在改动 h2/oracle/postgresql/duckdb/dm/mysql/mariadb/db2 等 dialect XML 与 selector——**本 plan 引用的全部方言 XML 锚点以 WI3 落地后状态为准**。起草后独立审查已实测三处漂移（WI3 向 h2 `<functions>` 插入 `ilike` 注册等使行号下移）：

| 锚点 | 初稿引用 | WI3 落地后实测 |
|---|---|---|
| h2 std DECIMAL 映射（native `NUMERIC`） | `h2.dialect.xml:211` | `:221` |
| h2 BOOLEAN 映射 | `h2.dialect.xml:217` | `:227` |
| oracle std DECIMAL 映射（native `NUMBER`） | `oracle.dialect.xml:209` | `:212` |

下文 Current Baseline 的方言 XML 引用以 WI3 落地后值为准（个别处括注初稿值，漂移对照以本表为权威）。执行时按 Phase 0 第 3 项重核：① 全部 dialect XML 锚点符号仍存在；② 可加载方言清单——**db2 若按 WI3 第 12 项修复 selector 断链将变为可加载**，本 plan"db2 为 stub、不入矩阵"的表述与 Phase 0 载体表届时重核；③ "两闸门直调 `StdSqlType.fromStdName`、无独立白名单"的结论复核（WI3 只改方言 XML 值/函数注册、不改 `StdSqlType`/`SimpleStdDomainHandlers`，预期不变；前提变化时回写裁定文档）。

### WI2 已落地的 BOOLEAN 基线（本 plan 复用与扩展的起点）

- 文法：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:381-390` 的 `dataTypeName_` 已含 `BOOLEAN` 备选（`:388`，提交 `9a76048670`；重生成仅 `EqlParser.java`/`Eql.interp`，零 token 增删、`ast/_gen` 零 diff——WI2 实测先例）。
- 全链：`cast(x as boolean)` 解析 → `SqlTypeExpr_name` 放行 → h2 上生成钉死 `cast(o.SID as BOOLEAN)`（`TestEqlCastBooleanCompile`）；无映射方言在生成期响亮报 `ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED`（手工最小方言载体，`TestEqlCastBooleanCompile.testNoBooleanMappingDialectFailsLoudlyAtGeneration`）。
- 非法类型守护：`cast(x as nosuchtype)` 仍响亮报 `ERR_EQL_INVALID_SQL_TYPE`（`TestEqlCastBooleanParse.java:71-76` 断言该错误码——**本 plan 的收窄方案不得破坏此断言**，见必拒项小节）。
- BUG-15 守卫：`SqlTypeExpr.characterSet`/`collate` 访问器无条件抛 `nop.err.eql.sql-type-clause-not-support`（提交 `eadb34029d`）；quoted collate 的活通路已转响亮拒绝。**本 plan 收窄 `dataTypeName_` 不得触碰 `characterSet_`/`collateClause_` 规则与其守卫**（`TestEqlSqlTypeClauseGuard` 的 6+1 用例是回归底线）。

### `stdNameMap` 现状（实测）

- 位置：`nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdSqlType.java`。`stdNameMap` 声明 `:146`；static 块 `:151-164` 灌值——显式 `stdNameMap.put("INT", INTEGER)`（`:152`，**别名条目的既有先例**）+ 遍历 `values()` 灌入全部枚举名（`:154-161`）+ `stdTypeMap` 两条重钉（`:162-163`）。`fromStdName`（`:188-191`）与 `getNames()`（`:180-182`）直读该 map。
- 现有键集 = `INT` + 25 个枚举名（BOOLEAN/TINYINT/SMALLINT/INTEGER/BIGINT/DECIMAL/FLOAT/REAL/DOUBLE/NUMERIC/DATE/TIME/DATETIME/TIMESTAMP/CHAR/VARCHAR/BINARY/VARBINARY/ARRAY/OTHER/JSON/CLOB/BLOB/ANY/GEOMETRY，枚举定义 `:21-108`）。**结论：`TEXT` / `NUMBER` / `BOOL` 确实缺席；`UUID` 连枚举常量都不存在**。
- 时区类型 `TIME_WITH_LOCAL_TIME_ZONE` / `TIMESTAMP_WITH_LOCAL_TIME_ZONE` 处于注释态（`:38-39` / `:42-43`）——属 **WI9b**，本 plan 不碰（见 WI9b 边界小节）。

### `stdNameMap` 的全部生产消费点（闸门 + 消费者，实测清单）

1. **闸门 A（EQL cast）**：`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java:321-326`（手写）——`SqlTypeExpr_name` 把 `dataTypeName_` 全文 `toUpperCase(Locale.ROOT)` 后 `StdSqlType.fromStdName` 判空即抛 `ERR_EQL_INVALID_SQL_TYPE`（`nop.err.eql.invalid-sql-type`，声明 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlErrors.java:116`），param `ARG_SQL_TYPE` + `ARG_ALLOWED_NAMES = StdSqlType.getNames()`。别名入表后自动放行新拼写，既有值判定路径不变。
2. **闸门 B（xdef std-sql-type 域）**：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/SimpleStdDomainHandlers.java`——`StdSqlTypeType`（类 `:1608`，拒绝点 `:1630-1634`）与 `StdSqlTypeListType`（类 `:1640`，拒绝点 `:1667-1671`），未登记 stdName 抛 `ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN`，param `ARG_ALLOWED_NAMES`。该域被 **8 个 xdef** 引用：`api.xdef`、`db-migration/migration.xdef`、`task/batch.xdef`、`orm/dialect.xdef`（方言函数的 `argTypes`/`returnType` 即 `StdSqlType` 类型化，`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/function/TemplateSQLFunction.java:35`）、`db/table-field.xdef`、`orm/entity.xdef`、`orm/sql-lib.xdef`、`orm/orm.xdef` ⇒ 别名放行的取值集合同步作用于这些模型类型（纯增量）。
3. **消费者（不拒绝，只反查）**：`AstToSqlGenerator.visitSqlCastExpr`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java:505-525`，`:508` `fromStdName(expr.getName())`）经 `dialect.stdToNativeSqlType` 产方言情 native 文本；`SqlExprTransformHelper.getSqlType`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/eval/SqlExprTransformHelper.java:77-79`）。两处都经 `stdNameMap` 反查 ⇒ **别名落地后自动工作，无需改生成器**（Minimum Rules #23 的接线验证仍须用例证明）。
4. **DDL 方向**：`stdToNativeSqlType`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/SqlDataTypeMapping.java`，`stdToNative.get` `:81`；降级链 TINYINT→SMALLINT→INTEGER `:83-88`、VARCHAR→CLOB / VARBINARY→BLOB `:101-110`；两处抛点 `:89` / `:112`；错误码声明 `nop-persistence/nop-dao/src/main/java/io/nop/dao/DaoErrors.java:113`）——`stdNameMap` 放行的新 std 取值在这里按方言映射渲染或响亮报错。

### `dataTypeName_` 文法现状与必拒项清单（BUG-11）

- 唯一挂载点是 `cast`：`BaseRule.g4:292`（`CAST LP_ (expr=sqlExpr | NULL) AS dataType=sqlTypeExpr RP_`）＋ `sqlTypeExpr`（`:377-379`）⇒ **收窄只影响 CAST 目标类型**；orm.xml 的 `stdSqlType=` 属性走闸门 B，不经此文法。
- 现有备选（`:381-390`）= 26 个显式备选 + `sqlIdentifier_` 兜底（`:69-71`：`IDENTIFIER_ | unreservedWord_`；`unreservedWord_` `:72-95`，**`NUMBER` 在其中 `:82`**）。
- 显式备选中通过 `fromStdName` 的 12 个：CHAR / VARCHAR / NUMERIC / DECIMAL / INTEGER / SMALLINT / FLOAT / REAL / DATE / TIME / TIMESTAMP / BOOLEAN。
- **必拒项 14 个**（今天"解析可过、`SqlTypeExpr_name` 必拒 `ERR_EQL_INVALID_SQL_TYPE`"）：`CHARACTER`、`CHARACTER VARYING`、`NATIONAL CHARACTER`、`NATIONAL CHARACTER VARYING`、`NCHAR`、`NATIONAL CHAR`、`NATIONAL CHAR VARYING`、`BIT`、`BIT VARYING`、`DEC`、`DOUBLE PRECISION`、`INTERVAL`、`TIME WITH TIME ZONE`、`TIMESTAMP WITH TIME ZONE`。
- 词法事实（决定收窄后的失败形态）：`CHARACTER`/`PRECISION`/`DOUBLE`/`INTERVAL` 是关键字 token（`Keyword.g4:365`/`:114`/`:357`/`:373`）；`BIT=233`/`DEC=254`/`NATIONAL=296`/`NCHAR=297`/`VARYING=347`/`ZONE=352` 在生成 parser 词表。`sqlIdentifier_` 是**单词级**备选（单个 `IDENTIFIER_` 或单个 `unreservedWord_`），**14 个必拒项的完整拼写无任何一项可经它匹配**——个别组成词虽在 `unreservedWord_`（如 `ZONE` `:90`、`NUMBER` `:82`、`DATE`/`TIMESTAMP` 亦在表内），但完整拼写均为多词形态；CHARACTER/NCHAR/BIT/DEC/INTERVAL 五个单词项不可匹配的真实原因是它们为 keyword token 且不在 `unreservedWord_` ⇒ 删除备选后它们从"AST 必拒（`nop.err.eql.invalid-sql-type`）"变为"解析失败（`nop.err.antlr.*`）"，失败形态变化但**同为响亮失败、无静默风险**。
- 兜底面：任何 `IDENTIFIER_`/`unreservedWord_` 拼写（`text`/`bool`/`uuid`/`number`/`money` 等——`TEXT`/`BOOL`/`UUID` 均无 lexer 关键字规则，也无独立 parser token：初稿所称 `TEXT=206` 实为 `MESSAGE_TEXT=206` 的子串误读，`EqlParser.java` 词表实测不存在独立 `TEXT` token）今天"解析 OK + AST 必拒"⇒ 四个新拼写的词法形态一致（全部词法化为 `IDENTIFIER_` 走兜底），别名入表后同名拼写自动放行。引号字符串（`STRING_`）不匹配 `sqlIdentifier_` ⇒ **字符串当类型名今天即解析失败，无须新增拒绝项**。
- **收窄 BC 风险实证（起草时全量 grep）**：① 7 个含 `<eql>` 的 `sql-lib.xml` 中 `cast(` **零命中** ⇒ 语料零影响；② 114 个 orm.xml 不经此文法 ⇒ 零影响；③ 全仓 `_vfs`/`xdef`/`xmeta` 资源中 `stdSqlType|returnType|argTypes|stdType` 属性取 `TEXT/NUMBER/BOOL/UUID` 的文件数 = **0**；④ `nop-orm-eql` 测试的 cast 用法仅 3 个 WI2 测试文件，其中 `nosuchtype`（`IDENTIFIER_` 走兜底）与 `varchar(10) character set utf8`（失败点在 `characterSet_` 的 `STRING` token 缺失，与 `dataTypeName_` 无关）是两条必须存活的回归底线。
- **收窄边界候选**（Phase 0 裁定）：候选 A = 仅删 14 个必拒显式备选、**保留 `sqlIdentifier_` 兜底**——未知拼写仍走 `ERR_EQL_INVALID_SQL_TYPE`（保留 `ARG_ALLOWED_NAMES` 的错误质量，WI2 的 `nosuchtype` 断言不破）；候选 B = 兜底也删、语义拒绝面全部前移到文法——`nosuchtype` 从带 `ARG_ALLOWED_NAMES` 的语义错误退化为解析失败，**会破坏 `TestEqlCastBooleanParse:71-76`**，须同步改测试并在对照表中逐项登记错误形态迁移。本 plan 倾向候选 A（收窄目标 = "文法不再承诺它注定拒绝的拼写"，而非"消灭兜底"），终裁权在 Phase 0。

### 本 plan 起草时的增量实测（G-3 裁决之外的新事实 —— Phase 0 的裁定输入）

1. **`NUMBER→NUMERIC` 的"支持"方言今天为空集**：全仓 dialect XML 无任何 `stdSqlType="NUMERIC"` 的 `<sqlDataType>` 条目（default/h2/mysql/mariadb/oracle/postgresql/mssql/duckdb/db2/dm 实测全 0）。native 侧 `NUMERIC` 已被指向 std DECIMAL（`h2.dialect.xml:221`、`mssql.dialect.xml:227`，行号为 WI3 落地后值），oracle 的 native `NUMBER` 也指向 std DECIMAL（`oracle.dialect.xml:212`，WI3 落地后值）⇒ G-3 批准的字面目标落地后，`cast(x as number)` 在**所有**方言都是"解析过、生成期响亮报 `ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED`"，roadmap EC ① 的"支持"类用例**无载体**；且补映射的成本被键控机制实证放大：`dialect.xdef:211` 以 `xdef:key-attr="name"` 把 native 名作为 `<sqlDataTypes>` 列表主键，`SqlDataTypeMapping.register`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/SqlDataTypeMapping.java:49` 起；`nativeTypes.put` `:55`、alias put `:59`）对同名条目 **last-wins 静默覆盖**而非加载期报错——给已占用名（h2/mssql 的 `NUMERIC`、oracle 的 `NUMBER`）挂第二个 std 目标，会把 native→std 方向的既有解析（如 native `NUMERIC`→DECIMAL）无信号地改写为新条目 ⇒ 候选 (b) 不只是"加条目"，而是静默改写 reverse 映射，成本与风险显著更高 ⇒ Phase 0 必须裁定（候选与授权依据见"与 roadmap 的差异登记"第 1 条）。
2. **`TEXT→CLOB` 映射覆盖两类齐全**：CLOB 在 h2/mysql/oracle/postgresql/dm 有 `<sqlDataType>`（mariadb 继承 mysql、duckdb 继承 postgresql）；mssql 无 CLOB（其 native `TEXT`→std VARCHAR，`mssql.dialect.xml:228`）、default 无 `<sqlDataTypes>` 段、db2 为 stub ⇒ "支持"载体（h2，WI2 先例）/ "不支持"载体（mssql 真方言或手工最小方言）都真实存在。CLOB 无降级路径（`SqlDataTypeMapping.java` 的降级只覆盖 TINYINT/SMALLINT/VARCHAR/VARBINARY）。
3. **`BOOL→BOOLEAN` 映射覆盖**（WI2 Phase 4 已实测，本 plan 沿用）：可加载方言继承链均有 BOOLEAN 映射（h2 `:227`（WI3 落地后；初稿 ：217）、mysql、oracle、postgresql、mssql native `BIT`→BOOLEAN `:223`、duckdb、dm；mariadb/duckdb 继承）；"不支持"载体沿用手工最小方言先例（`TestEqlTranslationSemantics.buildDialect()`）。
4. **UUID 的三个空白**：`StdSqlType` 无枚举常量、`StdDataType` 无 UUID（`nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdDataType.java` 实测 0 命中）、全仓 dialect XML 无 UUID 的 `<sqlDataType>`（0 命中）⇒ UUID **不是"别名入表"能完成的**。候选形态见"与 roadmap 的差异登记"第 2 条。
5. mssql 的 native `TEXT`→std `VARCHAR`（`:228`）与别名 `TEXT→CLOB` 分属两张独立映射表（native 名→std 的输入解析 vs `stdNameMap` 的 std 名→枚举），**互不冲突**；但意味着"TEXT"在 mssql 方言语境下历史上指 VARCHAR 家族——Phase 0 的对照表须登记此不对称，避免误读。

### 生成管线事实（Protected Area 定性）

- `nop-orm-eql` 侧生成管线是活的（roadmap Cross-Cutting 第一行、WI2 实测）：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `dataTypeName_` 改动原地重写 `EqlParser.java`（10785 行量级）、`_EqlASTBuildVisitor.java`、`EqlLexer*`/`Eql.interp`/`*.tokens`（删备选**不删 token** ⇒ 无重编号风险；`EqlAST.xjava`/`ast/_gen` 无涉——本 plan 不新增 AST 节点、不改字段）。
- 本 plan 仅一处 `.g4` 改动（`dataTypeName_`），**单独提交**（roadmap 生成物变更纪律）；`nop-kernel/nop-commons` 的 `StdSqlType.java` 改动**单独提交**（G-3 回滚单位 = "stdNameMap 别名单独提交，git revert"，裁决 §一总表）。
- 约束 1（路径定价）：本 plan 全部走最便宜的路径 ⓪/模型层——不加 AST 字段、不加生成器分支；若实施中发现需要动 `EqlAST.xjava` 或生成模板，立即停下按生成管线 plan-first 重新裁定。

### 既有 EQL 语料与全绿口径

- 7 个含 `<eql>` 的 `sql-lib.xml`；本 plan `-pl` 能覆盖的必测三份：`nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/sql/NopJobTask.sql-lib.xml`、`nop-auth/nop-auth-service/src/test/resources/_vfs/nop/auth/sql/test.sql-lib.xml`、`nop-persistence/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml`。`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份**不在 `-pl` 内**（后者无 `src/test` 源码），由 roadmap 级 EC 第 8 条 + WI0 静态快照兜底。**禁止声称"7 个全测"**。起草时实测：语料 `cast(` 零命中 ⇒ 别名与收窄都无语料回显触碰面，预期快照零 diff（执行时复核）。
- stream 既有失败基线 1 项：`nop-stream-runtime` 的 `TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`（`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 头部第 2 条红线事实）——"全绿"口径 = 本 plan 相关模块全绿 + 该既有失败与基线一致，不据此判失败。
- **G-3 落地门（114 个 orm.xml 全量回归）的执行形态（预写，消除字面不可达）**：`./mvnw clean install -T 1C`（带测）字面不可达——全仓闭包含 `nop-stream-runtime` 的上述既有失败。本 plan 采用**组合等效判据 (a)**：
  1. `./mvnw clean install -DskipTests -T 1C` —— 验证安装包与 codegen 重生成全链（G-3/roadmap EC 8 中 `clean install` 所承载的"重生成链路 + 114 个 orm.xml 模型进包"面）；
  2. `./mvnw test -pl :nop-orm,:nop-orm-eql,:nop-dao,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am`（串行）—— orm 全闭包带测（orm.xml 解析/加载回归的实际执行面）。**"全绿"沿用 Current Baseline 既有口径：`nop-stream-runtime` 既有 1 项失败与基线一致不判失败——该模块经 `nop-sys-dao` 的 test-scoped 依赖（`nop-sys/nop-sys-dao/pom.xml:49-58`）进入 `-am` 闭包（roadmap Cross-Cutting「必跑命令」行同源）。**

  两条命令全绿 = G-3 落地门通过，Phase 1 EC 与 Closure Gate 以此组合为准；不采用"`-Dtest='!TestStreamConnectorRegistryDiscovery'` 排除单测"的豁免口径（那是 WI2 closure 时的补跑取证手段，不作为本 plan 的门定义），也不得以"带测 clean install 跑不完"为由跳过门。

### WI9b 边界声明（防 scope creep）

`StdSqlType.java:38-39`/`:42-43` 注释态的 `TIME_WITH_LOCAL_TIME_ZONE` / `TIMESTAMP_WITH_LOCAL_TIME_ZONE`（取消注释 + `dataTypeName_` 同步拼写 + DDL 映射迁移评估）**全部属 WI9b，必须独立成 plan**（roadmap `:327-332` 明文：取消注释会改变既有部署的 DDL 类型映射，需独立迁移评估；依赖图 WI9a → WI9b）。本 plan 删除的 `TIME WITH TIME ZONE` / `TIMESTAMP WITH TIME ZONE`（非 LOCAL）备选与 WI9b 将引入的 `… WITH LOCAL TIME ZONE` 拼写是**两个名字集**，互不替——若 Phase 0 裁定涉及时区拼写，一律移出本 plan 登记 successor。

## Goals

- `TEXT`/`BOOL`（及 Phase 0 裁定后的 `NUMBER`）别名入 `stdNameMap`：`cast(x as text/number/bool)` 全链可用（解析 → AST → 方言 native 文本），断言具体 SQL 文本；无映射方言保持生成期响亮报错，不降低既有拒绝面。
- `UUID` 按 Phase 0 裁定形态落地或显式移出（移出须有书面裁定与 successor，不允许名义 owner）。
- `dataTypeName_` 必拒项收窄（14 个死备选，按 Phase 0 裁定边界）：文法接受面不再包含注定被 AST 拒绝的拼写；产出 roadmap EC ③ 的对照表（"被移除的拒绝项 → 现在接受 → 生成什么 SQL"，含错误形态迁移列）。
- 114 个 orm.xml 全量回归门通过 + "加别名会改变哪些既有模型解析结果"影响清单交付（G-3 落地门；起草时实证预期 = 空清单，落地时重跑统计归档）。
- 每个 alias 在"支持"与"不支持"两类方言上各有用例（roadmap EC ①；约束 4 的一致性/不等价两类用例：同义拼写同方言输出一致 + 跨方言支持面差异各有断言）。
- owner doc 同步：别名表进 `docs-for-ai/02-core-guides/orm-model-design.md`（roadmap EC ④；该文档已承载 stdSqlType/stdDataType 分离与默认映射表，`:7`/`:32-54`）；EQL 侧 cast 类型面登记进 `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`。
- 三份必测语料 BC：与 WI0 基线快照逐字一致。

## Non-Goals

- **不碰 WI9b**：不取消 `StdSqlType.java` 时区类型注释、不引入 `TIME/TIMESTAMP WITH LOCAL TIME ZONE` 拼写、不做时区 DDL 迁移评估。
- 不新增除别名/UUID 之外的 `StdSqlType` 枚举成员，不改动既有枚举成员的 `jdbcType`/`stdDataType`。
- 不实现 `CAST … CHARACTER SET/COLLATE` 语义（WI2 已显式拒绝防护，Deferred 记录在案）；不触碰 `characterSet_`/`collateClause_` 规则。
- 不为任何方言做类型 lowering/双档方言拆分（别名落既有闭集成员，映射缺失 = 响亮报错，与 WI2 BUG-8 同纪律）。
- 不改方言 XML 既有能力值；若 Phase 0 裁定**新增** `<sqlDataType>` 条目，仅限裁定清单所列、独立提交、逐条可 revert（授权依据见差异登记第 1/2 条）。
- 不做 `INTERVAL` 语义、时区函数、`AT TIME ZONE`（WI12）等任何 roadmap 其他 WI 范围项。
- 不引入 `:nop-orm-tdengine` / `:nop-db-migration` / `:nop-orm-demo` 进 `-pl`（roadmap 级 EC 第 8 条覆盖）。
- 不做真实多数据库 CI。

## Scope

### In Scope

- `nop-kernel/nop-commons`：`nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdSqlType.java`（`stdNameMap` 别名 put；UUID 若裁定为新增枚举常量则在枚举 + static 块落位，并核验 `jdbcTypeMap` 先到先得 `:157-158` 与 `stdTypeMap` 重钉 `:162-163` 的既有不变量不被破坏）。**单独提交**。
- `nop-persistence/nop-orm-eql`：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `dataTypeName_`（删 14 个必拒不选，按 Phase 0 裁定边界）→ 重生成产物随文法提交。**单独提交**。测试：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/` 与 `compile/` 下新增/扩展用例（正向文本断言 + 负向错误码断言 + 收窄对照表守护用例）。
- 方言 XML（仅当 Phase 0 裁定补映射）：`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml` 的新增 `<sqlDataType>` 条目，逐条独立提交。不触碰 `dialect.xdef`（std-sql-type 域已存在，零声明改动）。
- 文档：`docs-for-ai/02-core-guides/orm-model-design.md`（别名表 + UUID 形态，若落地）、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（cast 类型接受面/拒绝面登记，与 WI2 的 BUG-15 小节衔接）、`ai-dev/design/eql-cross-db-compat/`（Phase 0 裁定文档；若偏离 G-3 字面别名表，在该裁决文档追加带日期的增补小节，不重写原文）、`ai-dev/logs/`。

### Out Of Scope

- Non-Goals 所列全部；以及任何 roadmap 未登记的文法/类型扩展（发现新缺陷 → 登记 roadmap / 新 plan，不顺手修）。

## 与 roadmap 的差异登记（起草时实测发现）

1. **`NUMBER→NUMERIC` 在 G-3 裁决后暴露"支持方言空集"事实**：G-3 裁决（§四）论证的是"别名落既有闭集成员、零既有模型受影响"，未核验 NUMERIC 的方言映射覆盖。本 plan 起草实测：**没有任何方言 XML 为 std NUMERIC 提供映射**，且 h2/mssql/oracle 的对应 native 名已被 DECIMAL 占用——占用机制不是"不能配"而是 `dialect.xdef:211` key-attr + `register` last-wins **静默覆盖**（见 Current Baseline 增量实测第 1 条）⇒ 字面落地则 roadmap EC ① 的"支持"类用例对 NUMBER 无载体。候选：(a) 别名目标改 `NUMBER→DECIMAL`（DECIMAL 在 7+ 方言有映射、oracle native `NUMBER`→DECIMAL 语义直观；**偏离 G-3 字面表**，须在裁决文档追加带日期的增补小节并回写 roadmap G-3 行注记）；(b) 保持 `NUMBER→NUMERIC` 且为有空位的方言补 `<sqlDataType>`（方言情 XML 契约新增，G-4 批准范围仅 WI3 的 11 项**不含此**——授权依据 = roadmap WI9a EC ① 原文，WI2 差异登记 #2 的形态先例；⚠️ 已占用的 native 名不可复用——同名条目会被 `register` 静默覆盖、改写既有 reverse 映射，只能新增未占用名，映射覆盖面可能因此受限）；(c) 保持 `NUMBER→NUMERIC` 零映射落地，登记"全方言生成期报错"为已知形态——**与 roadmap EC ① 字面冲突，须同步登记 roadmap 适用口径修正**。Phase 0 三选一并记录理由。
2. **UUID 未被 G-3 覆盖**：G-3 裁决授权的是 TEXT/NUMBER/BOOL 三别名；UUID 在 roadmap WI9a 节范围内但三个空白实测（无枚举/无 StdDataType/无方言映射）使其实施形态是"新闭集成员 + 方言映射"而非"别名"。授权依据 = roadmap WI9a 节原文（WI2 差异登记 #2 的授权形态先例）+ Phase 0 书面裁定；候选：(a) 新增 `StdSqlType.UUID` 枚举常量（`StdDataType.STRING` 家族；为有原生 UUID 的方言补映射）；(b) `UUID→VARCHAR` 纯别名（零生成面改动，但丢失类型身份且不在 G-3 授权表内，采纳须同条记录）；(c) 移出 WI9a（roadmap 范围变更，须显式裁决 + successor）。
3. **EC ① 措辞的载体事实**：roadmap EC ① 要求"每个新增/别名类型在'支持'与'不支持'两类方言上各有用例"。实测载体现状：TEXT/BOOL 两类齐全（h2 vs mssql/最小方言；BOOLEAN 已有 WI2 用例可扩展）；NUMBER 受差异登记第 1 条制约；UUID 受第 2 条制约。Phase 0 裁定后，Phase 1 的测试矩阵按裁定结果逐类型列载体，不夸大、不虚设"支持"类。
4. **roadmap WI9a 节未点名 `getNames()` 的连带效应**：`ARG_ALLOWED_NAMES = StdSqlType.getNames()` 出现在两处闸门错误消息中，别名/UUID 入表后错误提示集合同步变大——属预期行为，登记为对照表的一列（"闸门 B 错误消息键集变化"），非缺陷。
5. **行号基线**：roadmap 引用的 `StdSqlType.java:146,152-161` 与本 plan 实测（`:146`/`:151-164`，INT put 在 `:152`）一致；`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `dataTypeName_` 在 WI1（`5afea1d3f3`，`sqlIntervalExpr`）/WI2（`9a76048670`，`BOOLEAN`）两次文法提交后稳定于 `:381-390`。**方言 XML 锚点的漂移（h2/oracle）以"WI3 并行实施注记"为准**（h2 `:211`→`:221`、`:217`→`:227`、oracle `:209`→`:212`）。执行时一律以符号定位为准。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`dataTypeName_` 收窄）→ `EqlParser.java` 等重生成 | **生成管线，plan-first**（AGENTS.md Protected Area"生成管线"；roadmap 门控矩阵 WI9a 行明文） | 本 plan 即 plan-first 载体，执行前须通过 draft review（独立子 agent 对抗性审查） | owner doc（`orm-model-design.md` + `eql-and-database-compatibility.md`）+ 回归测试（红/绿用例 + 三份必测 BC） |
| `nop-kernel/nop-commons` 的 `StdSqlType.java`（`stdNameMap` 别名；UUID 若新增枚举） | **模型层行为变更**（`stdNameMap` 同时驱动 DDL 类型映射与合法 `stdSqlType=` 取值闸门，影响 8 个 xdef 关联模型的解析接受度）。⚠️ `nop-commons` **不是** `nop-*-api`，非 AGENTS.md 字面 Protected Area；按 roadmap Cross-Cutting 走「AI Must Ask Before」同级别的书面授权 | **G-3 裁决已覆盖三别名的授权**（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md` §四，含 114 orm.xml 全量回归门）；UUID 形态的授权依据见差异登记第 2 条 | G-3 裁决文档引用 + 影响清单（落地时重跑 0 命中统计归档）+ 全量回归 |
| 方言 XML 新增 `<sqlDataType>` 条目（仅当 Phase 0 裁定） | 平台对外契约新增（非改值）；G-4 批准范围外 | roadmap WI9a EC ① 原文为授权依据（WI2 先例）+ Phase 0 显式 Decision 项；逐条独立提交可 `git revert` | 方言矩阵快照门禁 + 正/负向用例 + owner doc |
| `nop-persistence/nop-orm-eql` 手写测试与（如需）`OrmEqlErrors.java` | 普通手写代码 | 无额外门禁 | 回归测试 |

⚠️ 本 plan 不触碰 `model/*.orm.xml`、`nop-auth` 认证模型、`nop-*-api` 公共接口、`_gen/` 等冻结生成物的手改（重生成产物由文法提交携带，不手改）；不删除任何 lexer token（无重编号风险）；不改 `EqlAST.xjava`/`ast/_gen`（无新增 AST 节点/字段）。

## Execution Plan

Phase 0 为前置纯裁定、不产产品代码（其各项是 Phase 1/2 的硬前提）；Phase 1（模型层别名）与 Phase 2（文法收窄）相互独立、可分别落地与回滚，各自单独提交；顺序建议 Phase 1 → Phase 2（收窄裁定的"现在接受"清单以别名落位后的 `stdNameMap` 终态为准）。每个代码 Phase 开工前 rebase 到当时代码基线并复核锚点符号。

### Phase 0 - 前置裁定

Status: completed
Targets: `ai-dev/design/eql-cross-db-compat/`（新增裁定文档）、`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 与 `dialect-capability-matrix.json`（状态核验，只读）

- Item Types: `Decision`、`Proof`

- [x] 复核 WI0 两件产物状态未回退：语料基线快照（`TestEqlCorpusBaseline` 通过）与方言矩阵快照（`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0）——Phase 1/2 的 BC 判据与门禁依赖它们存在。（2026-10-04 实测：CorpusBaseline 1/1 PASS、mtime 晚于 `949531d93e`；矩阵门禁 exit 0，15 可加载含 db2——WI3 第 12 项已修复，见裁定文档 §2.1）
- [x] 重跑 G-3 的 0 命中统计（`git ls-files '*.orm.xml'` 口径，`stdSqlType="TEXT|NUMBER|BOOL|UUID"` 大小写不敏感）并把结果登记进裁定文档；同时按本 plan Current Baseline 第 3 项的口径扩到全仓 `_vfs`/`xdef`/`xmeta` 的 `stdSqlType|returnType|argTypes|stdType` 属性——**这是"加别名会改变哪些既有模型解析结果"影响清单的落地时重跑**（G-3 落地门要求；起草时实证 = 0，执行时以重跑结果为准，若非 0 须逐文件列清单并回到 ask-first）。（重跑双口径均 0 命中，影响清单=空清单，裁定文档 §一）
- [x] 按 WI3 落地后状态重核方言锚点与可加载方言清单（漂移清单见 Current Baseline"WI3 并行实施注记"）：① 本 plan 引用的全部 dialect XML 锚点符号仍存在；② 可加载方言清单——**db2 若按 WI3 第 12 项修复 selector 断链将变为可加载**，重核本 plan"db2 为 stub、不入矩阵"的表述与测试载体表；③ "两闸门（闸门 A/B）直调 `StdSqlType.fromStdName`、无独立白名单"的结论复核（WI3 只改方言 XML 值/函数注册、不改 `StdSqlType`/`SimpleStdDomainHandlers`，预期不变；前提变化时回写裁定文档）。（裁定文档 §二：符号全部在位、行号漂移登记；15 可加载含 db2、载体表仍不用 db2；两闸门结论不变 `:470-474`/`:1630`/`:1667`）
- [x] 裁定 `NUMBER` 别名目标：差异登记第 1 条的候选 (a)/(b)/(c) 三选一，记录理由与被拒候选；若偏离 G-3 字面表（候选 a）或引入方言 XML 新增（候选 b），在 `2026-10-03-wi0-gate-adjudications.md` 追加带日期的增补小节（不重写原文）并同步 roadmap G-3 行注记。产出：NUMBER 的映射目标、'支持/不支持'载体方言名。（**候选 (a) `NUMBER→DECIMAL`**；G-3 增补裁决落裁定文档 §三、roadmap G-3 行原地注记已加；载体 h2/oracle 等 9 方言 / 手工最小方言）
- [x] 裁定 UUID 形态：差异登记第 2 条的候选 (a)/(b)/(c) 三选一，记录理由；候选 (a) 须同时列出拟补映射的方言清单与 `StdSqlType.java` static 块三个 map（`stdNameMap`/`jdbcTypeMap` 先到先得/`stdTypeMap` 重钉）的不变量核验结论；候选 (c) 须写 successor 与触发条件。**书面裁定的形态同 G-3 模式**：平台负责人经执行指令授权、AI 代拟、人可推翻，落裁定文档"裁决人注记"小节——`StdSqlType` 位于 `nop-kernel/nop-commons` 属模型层变更（ask-first 级），不得以 plan-first 之名替代询问。产出：UUID 是否在本 plan 落地及其形态。（**候选 (a) 新增枚举 + h2/postgresql/duckdb/mssql 四方言映射逐条独立提交**；三 map 不变量核验结论在裁定文档 §四；裁决人注记已落）
- [x] 裁定 `dataTypeName_` 收窄边界：候选 A（删 14 个必拒显式备选、保留 `sqlIdentifier_` 兜底，`nosuchtype` 语义拒绝面不变）vs 候选 B（兜底也删，全部前移到文法，`TestEqlCastBooleanParse:71-76` 断言随裁定迁移）。产出：收窄清单（14 项逐项列"收窄前行为 / 收窄后行为 / 等价接受拼写 / 生成 SQL"——即 roadmap EC ③ 对照表的定稿列结构与草表）、错误形态迁移的登记口径。**行为列必须写错误码字面值**：收窄前 = `nop.err.eql.invalid-sql-type`（`ERR_EQL_INVALID_SQL_TYPE`，`OrmEqlErrors.java:116`）；收窄后（候选 A）= `nop.err.antlr` 前缀的解析失败错误码（`TestEqlSqlTypeClauseGuard` 同款断言形态）；不接受"响亮失败"之类定性词替代字面值。（**候选 A**；14 行对照表草表落裁定文档 §五，收窄前 `nop.err.eql.invalid-sql-type`/收窄后 `nop.err.antlr.common.parse-fail`/`not-end-properly`（`AntlrErrors.java:22/25`），逐项以 Phase 2 实测终稿；DOUBLE 拼写缺口新发现登记 follow-up）
- [x] 裁定测试矩阵载体：逐 alias 列"支持"载体（真方言，`DialectManager` + `CoreInitialization`，`TestH2WindowFrameCompileEndToEnd`/WI2 `TestEqlCastBooleanCompile` 先例）与"不支持"载体（真方言无映射者如 mssql-TEXT，或手工最小方言 `TestEqlTranslationSemantics.buildDialect()` 先例）；**不得用 `es`（伪方言红线）**、`db2` 按 stub 事实暂不入矩阵（若 WI3 修复 selector 使其可加载，按本 Phase 第 3 项重核）；约束 4 的一致性/不等价两类用例的落点（同义拼写同方言输出一致；跨方言支持面差异）在本项定稿。**载体表逐行含"载体方言 + 期望渲染文本"，期望文本按 `stdToNativeSqlType` 实际链路推导**（std 键 → 该方言条目列表中首个非 deprecated 且 `allowPrecision` 匹配项），不得按别名/方言名直觉填——实证：h2 的 std DECIMAL 唯一映射条目是 native `NUMERIC`（`h2.dialect.xml:221`，WI3 落地后行号）⇒ `cast(x as number)`（若裁定目标为 DECIMAL）在 h2 渲染 `NUMERIC`，oracle（唯一条目 native `NUMBER`，`:212`）才渲染 `NUMBER`。（裁定文档 §六载体表：text→h2 CLOB/pg TEXT、number→h2 NUMERIC/oracle NUMBER、bool→h2 BOOLEAN、uuid→h2/pg/duckdb UUID+mssql UNIQUEIDENTIFIER；负向 mssql/minimal/mysql/oracle）
- [x] 裁定各 Phase 的提交切分：`StdSqlType.java` 单独提交（G-3 回滚单位）；`.g4` + 重生成产物单独提交；方言 XML（若有）逐条独立提交——与本 plan Protected Areas 表一致，无新增裁定则记录"按默认"。（按默认 + 顺序细化：StdSqlType 提交先于方言 XML（XML 的 stdSqlType="UUID" 依赖闸门 B 放行）；方言 XML 各携该方言正向用例自洽可绿；裁定文档 §七）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含：0 命中统计重跑结果、WI3 落地后方言锚点与可加载方言清单重核结论（含 db2 可加载与否对载体表的影响）、NUMBER 目标三选一结论及（如适用）G-3 增补小节、UUID 形态三选一结论（含 static 块不变量核验或 successor）、收窄边界 A/B 结论与 EC ③ 对照表定稿列结构（含错误码字面值口径）、测试矩阵载体表（含逐行期望渲染文本）、提交切分结论。（`2026-10-04-wi9a-phase0-adjudications.md` §一-§七 + 裁决人注记）
- [x] WI0 两件产物状态核验结论已记录（`completed`/未回退，或显式声明等待并阻塞后续 Phase）。（裁定文档 §〇：均未回退）
- [x] 本 Phase 纯裁定，不改产品代码：`No owner-doc update required`（裁定文档本身即交付物；G-3 增补小节若产生，按其小节内说明登记）。（G-3 增补小节已落裁定文档 §三 + roadmap G-3 行注记；owner docs 归 Phase 1/2）
- [x] `ai-dev/logs/` 对应日期条目已更新。（`ai-dev/logs/2026/10-04.md` WI9a Phase 0 置顶条目）

### Phase 1 - `stdNameMap` 别名 + UUID 形态落地（模型层，单独提交）

Status: completed
Targets: `nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdSqlType.java`、按 Phase 0 裁定可能涉及 `*.dialect.xml` 新增 `<sqlDataType>`、`nop-orm-eql`/`nop-dao` 测试、`docs-for-ai/02-core-guides/orm-model-design.md`、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Feature`（别名扩接受集——G-3 裁决的新增能力，非缺陷修复）、`Decision`（NUMBER/UUID 形态，Phase 0 已裁定）、`Proof`

- [x] 按 Phase 0 裁定落别名：`stdNameMap` 在 static 块加 `TEXT`/`NUMBER`（按裁定目标）别名 put（先例 `:152` 的 `INT`）；UUID 按裁定形态（新枚举常量或别名 put）。**不修改任何既有映射条目**（G-3 机制层面第 1 点）；`getNames()` 键集变化登记进裁定文档附录（差异登记第 4 条）。（commit `5154661e75`：3 别名 put + UUID 枚举（Types.VARCHAR+STRING，GEOMETRY 后追加）；键集 30→34 登记裁定文档 §九）
- [x] 修复前红（每 alias 一组）：`cast(x as text)` / `cast(x as number)` / `cast(x as bool)`（UUID 若落地同）——修复前在 `SqlTypeExpr_name` 抛 `ERR_EQL_INVALID_SQL_TYPE`（断言红并留痕于提交说明或日志）；修复后同输入解析通过。（`_tmp/wi9a/red-phase1.log`：15 红全部 `nop.err.eql.invalid-sql-type`（TEXT/NUMBER/BOOL/UUID 四拼写 + allowedNames 键集断言），绿 = `TestEqlCastTypeAliasParse` 4|0 + `TestEqlCastTypeAliasCompile` 12|0 + `TestEqlCastUuidDialectCompile` 负向 4|0）
- [x] "支持"方言正向文本断言（按 Phase 0 裁定载体）：每 alias 一条钉死方言情 native 文本的用例（TEXT→h2 CLOB 文本；BOOL 沿用/扩展 WI2 的 h2 BOOLEAN 用例口径；NUMBER/UUID 按裁定）；同义拼写一致性用例（如 `cast(x as text)` 与 `cast(x as clob)` 在同一方言生成相同 native 文本——约束 4"一致性"类）。⚠️ **断言的期望文本按 Phase 0 载体表取值（`stdToNativeSqlType` 链路推导），不得按直觉猜**：h2 的 std DECIMAL 唯一映射条目为 native `NUMERIC`（`h2.dialect.xml:221`，WI3 落地后行号）⇒ `cast(x as number)` 在 h2 渲染 `NUMERIC`，oracle（唯一条目 native `NUMBER`，`:212`）才渲染 `NUMBER`。（h2 text→`cast(o.SID as CLOB)`、number(10,2)→`cast(o.SID as NUMERIC(10,2))`、bool→BOOLEAN；pg text→TEXT、oracle number→NUMBER；一致性 text≡clob/number≡decimal/bool≡boolean + bare 形态逐字一致用例。**执行偏差登记**：带 precision 形态用于精确文本断言——bare decimal 渲染 `NUMERIC(-1,0)` 系既有 `SqlTypeExpr.getScale()` 缺省 0 行为（`number`≡`decimal` 逐字一致，非本 WI 引入）；pg/oracle 标识符小写渲染需键部分小写比对）
- [x] "不支持"方言负向断言（按 Phase 0 裁定载体）：无映射方言上解析过、生成期响亮报 `ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED`（断言错误码；不得静默降级/改写）；跨方言支持面差异用例（约束 4"不等价"类，如 TEXT 在 h2 可生成 vs mssql 报错）。（mssql text + minimal number/bool/uuid + mysql/oracle uuid 全部断言 `nop.err.dao.dialect.std-data-type-not-supported`；uuid 错误 param dataType=UUID 断言）
- [x] 接线验证（Minimum Rules #23）：一个测试证明别名经 `SqlTypeExpr_name`（闸门 A）与 `AstToSqlGenerator.visitSqlCastExpr` 反查（消费点）真实连通——不是只有 `fromStdName` 单元返回值；闸门 B 用一条 orm/xdef 域校验用例证明新拼写被接受（或以 0 命中统计 + 闸门 A/B 共用 `fromStdName` 的代码事实登记替代，按裁定文档口径）。（全部正向用例经 `EqlCompiler.compile` 端到端（EQL 文本→闸门 A→生成器→方言情文本）；闸门 B 按裁定文档 §六替代口径：`fromStdName` 代码事实（`:470-474`/`:1630`/`:1667`）+ 0 命中统计（§一））
- [x] 非法类型守护：`cast(x as nosuchtype)` 仍响亮报 `ERR_EQL_INVALID_SQL_TYPE` 且 `ARG_ALLOWED_NAMES` 含新别名（WI2 `TestEqlCastBooleanParse:71-76` 存活，拒绝面不弱化）。（`TestEqlCastTypeAliasParse.testIllegalTypeStillRejectedAndAllowedNamesContainNewKeys` + WI2 底线 `TestEqlCastBooleanParse` 4|0、`TestEqlSqlTypeClauseGuard` 7|0 全存活）
- [x] **G-3 落地门**（执行形态 = Current Baseline"G-3 落地门（114 个 orm.xml 全量回归）的执行形态"专节的组合判据 (a)）：`./mvnw clean install -DskipTests -T 1C` 通过（安装包 + codegen 重生成全链）**且** `./mvnw test -pl :nop-orm,:nop-orm-eql,:nop-dao,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am` 全绿（114 个 orm.xml 全量回归的带测执行面）；"加别名会改变哪些既有模型解析结果"影响清单以 Phase 0 第 2 项重跑结果归档（预期空清单；非空则回到 ask-first）。（①`clean install -DskipTests -T 1C` exit 0（`_tmp/wi9a/` 记录，07:0x 完成）；②串行带测 run1 中断于 `nop-stream-runtime` 既有豁免项 `TestStreamConnectorRegistryDiscovery`（1233|1，症状与 WI0 基线逐字一致）→ run2 中断于第二个已知负载敏感 flake `TestBatchTaskCheckpoint#testFailFastStopsSiblingThreads`（单跑取证 8/8 PASS 0.255s）→ run3 方法级排除两 flake 后 **BUILD SUCCESS**（08:09）：nop-orm 212|0 / nop-orm-eql 344|0 / nop-dao 213|0 / nop-metadata-service 1340|0 / nop-job-dao 87|0 / nop-auth-service 461|0（surefire 汇总，mtime 全部晚于 Phase 1 最后 commit `0ca16cb4d0`，六模块 fresh=total）；影响清单=空清单（裁定文档 §一））
- [x] owner doc：别名表写入 `orm-model-design.md`（roadmap EC ④；与 `:32-54` 的默认映射表衔接——别名名 → 目标枚举 → 默认 stdDataType，注明"别名仅扩展解析接受集，不改既有映射"）；cast 类型接受面登记进 `eql-and-database-compatibility.md`。（orm-model-design.md 新节「stdSqlType 别名与 UUID」；eql-and-database-compatibility.md 新节「CAST 目标类型：别名与 UUID」+ 能力门登记表 +1 行）
- [x] `ai-dev/logs/` 对应日期条目已更新。（`ai-dev/logs/2026/10-04.md` WI9a Phase 1 条目）

Exit Criteria:

- [x] 每 alias 的红→绿回归测试存在，正向断言具体 SQL 文本、负向断言具体错误码；无静默跳过（无映射 = 抛错，非空返回/跳过）。
- [x] 约束 4 两类用例齐备：一致性（同义拼写同方言同输出）+ 不等价（跨方言支持面差异）各有断言。
- [x] 接线验证测试通过（闸门 A→消费点运行时连通，非仅 `fromStdName` 存在）。
- [x] G-3 落地门组合判据 (a) 两命令通过且影响清单归档：`./mvnw clean install -DskipTests -T 1C` + `./mvnw test -pl :nop-orm,:nop-orm-eql,:nop-dao,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am`（执行形态定义见 Current Baseline"G-3 落地门（114 个 orm.xml 全量回归）的执行形态"专节）。（证据见上方 checklist 项；stream/batch 已知 flake 按指令豁免口径方法级排除+单跑取证+如实记录）
- [x] `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿——"全绿"口径见 Current Baseline"既有 EQL 语料与全绿口径"节（含 stream 1 项既有失败豁免）。（终局执行 `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` BUILD SUCCESS（`_tmp/wi9a/final-t1c-rerun.log`，04:41）：run1 中断于 nop-task-core `TestExecutorTaskStepWrapperAsyncBranch`（-am 闭包上游模块，异步负载敏感 flake，单跑取证 2/2 PASS 0.141s，与本 WI 零代码关联）→ 方法级排除后 SUCCESS；五模块 surefire 汇总 2451|0（nop-orm 212 / nop-orm-eql 351 / nop-metadata-service 1340 / nop-job-dao 87 / nop-auth-service 461，mtime 全部晚于 `5f9bc6a5ba` fresh=total；已知 3 flake + 1 新证 flake 方法级排除如实记录）
- [x] 既有 EQL 语料 BC：`:nop-job-dao`(NopJobTask)、`:nop-auth-service`(test)、`:nop-orm`(test) 三份必测与 WI0 基线快照逐字一致（`TestEqlCorpusBaseline` 通过）；不声称"7 个全测"。（gate2 run3 内 `TestEqlCorpusBaseline` 1/1 PASS，mtime 07:22 新鲜）
- [x] owner doc（`orm-model-design.md` 别名表 + `eql-and-database-compatibility.md` 登记行）已落档。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - `dataTypeName_` 必拒项收窄（.g4，单独提交）

Status: completed
Targets: `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`dataTypeName_` `:381-390`）→ 重生成产物、`nop-orm-eql` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Fix`（BUG-11：文法接受面与语义接受面不一致的既登记缺陷——`ai-dev/analysis/2026-10/2026-10-02-eql-cross-database-compatibility-syntax-research.md:513` 实测 6+ 个"解析过、AST 必拒"）、`Proof`

- [x] 修复前红（按 Phase 0 收窄清单抽样）：必拒项拼写（如 `cast(x as double precision)` / `cast(x as dec)` / `cast(x as bit)`）在候选 A 下断言其行为——修复前为"解析过 + `ERR_EQL_INVALID_SQL_TYPE`"，若收窄改变失败形态，红→绿断言锚定**新形态**（解析失败或语义拒绝，以裁定为准）并登记迁移。（候选 A 下 `IDENTIFIER_` 可词法化的拼写行为不变，用例登记为"不变组"。）（红：`_tmp/wi9a/red-phase2-full.log` 14 项逐项"解析过 + `nop.err.eql.invalid-sql-type`"全量捕获；绿：`TestEqlCastTypeNarrowingParse` 6|0 锚定新形态 `nop.err.antlr.common.parse-fail`）
- [x] 按 Phase 0 裁定收窄 `dataTypeName_`：删除 14 个必拒显式备选（候选 A）或按候选 B 追加兜底删除；**不删除任何 lexer token**、不触碰 `characterSet_`/`collateClause_`、不触碰 `sqlDateTimeLiteral`/`sqlIntervalExpr` 等共用 token 的规则；文法改动**单独提交**，重生成产物随该提交。（commit `41bfbcba56`：候选 A，14 备选删除，token 零删除）
- [x] 重生成 diff 审计：确认仅 `EqlParser.java`/`Eql.interp` ATN 变化、token 表零增删、`ast/_gen` 零 diff（WI2 两次文法提交的同口径先例）；parser 词表中变为无引用的 token（如 `VARYING`/`PRECISION`/`NATIONAL`/`NCHAR`/`DEC`/`BIT`——词表保留、无重编号）登记进提交说明。（`git status` 仅 BaseRule.g4/EqlParser.java/Eql.interp 三文件；token/rule 常量表 grep 零 diff；EqlLexer.java/Eql.tokens/_EqlASTBuildVisitor.java/ast/_gen 零 diff；失引用 token 已登记提交说明，`CharacterSet_Context` 的 CHARACTER accessor 复核在位）
- [x] roadmap EC ③ 对照表定稿并落档：14 项逐行"被移除的拒绝项 → 收窄前行为 / 收窄后行为（**错误码字面值级**：收窄前 `nop.err.eql.invalid-sql-type` / 收窄后 `nop.err.antlr.*` 解析失败码，口径见 Phase 0 第 6 项）/ 现在接受的等价拼写 → 生成什么 SQL（载体方言文本）"；含 `TIME WITH TIME ZONE`/`TIMESTAMP WITH TIME ZONE` 两行的 WI9b 边界注记（LOCAL 拼写归 WI9b）。落 `eql-and-database-compatibility.md`（或裁定文档附录 + owner doc 引用，按 Phase 0 裁定口径）。（裁定文档 §五定稿（实测终稿：14 项统一 `nop.err.antlr.common.parse-fail`，offending token 逐项登记）+ `eql-and-database-compatibility.md`「文法收窄」对照表落地）
- [x] WI2 回归底线存活的显式断言：`nosuchtype` 语义拒绝（候选 A）/按候选 B 迁移后的新断言；`TestEqlSqlTypeClauseGuard` 全部用例不改动且通过；`cast(x as boolean)` 与 h2 文本断言不受影响。（`TestEqlCastTypeNarrowingParse.testUnknownSpellingStillSemanticRejection` + `TestEqlCastBooleanParse` 4|0 + `TestEqlSqlTypeClauseGuard` 7|0 零改动全过 + `TestEqlCastBooleanCompile` h2 文本 4|0）
- [x] WI9b 边界确认：本次改动未引入任何时区类型拼写/枚举变化（对照 Current Baseline"WI9b 边界声明"逐项自查，结论写进提交说明或日志）。（结论在 commit `41bfbcba56` 说明 + `TestEqlCastTypeNarrowingParse` javadoc；`StdSqlType.java` 时区注释未触碰、无 LOCAL 拼写引入；方言 XML native 名 `TIMESTAMP WITH TIME ZONE` 属另一张表不触碰）
- [x] owner doc：`eql-and-database-compatibility.md` 的 cast 拒绝面/接受面小节按对照表同步（与 Phase 1 的登记行合并为一张表）。（CAST 目标类型节内新增「文法收窄」对照表小节，与别名表同节）

Exit Criteria:

- [x] 收窄后文法接受集 = `stdNameMap` 键集 ∪（候选 A 时的兜底集），一个穷举式用例（或生成断言表）证明"文法放行 ⇒ 语义必过或语义响亮拒"，无"文法放行 + 静默错误 SQL"路径。（`TestEqlCastTypeNarrowingParse.testEveryRegisteredStdNameGrammarAcceptableOrExplicitlyUnspellable`：34 键穷举，仅 DOUBLE/ARRAY/ANY 为已知不可拼写组（解析失败），其余全部解析通过=闸门 A 必过）
- [x] 红→绿/形态迁移断言按裁定落地；错误形态迁移逐项在对照表登记。
- [x] WI2 底线（`nosuchtype`、`TestEqlSqlTypeClauseGuard`、h2 BOOLEAN 文本）全部存活。
- [x] `.g4` 提交独立可审计（`git log` 中文法提交不含逻辑改动）；重生成 diff 符合本 Phase 第 3 项口径。（commit `41bfbcba56` 仅 .g4+重生成产物+收窄测试）
- [x] 对照表（EC ③）定稿落档，含 WI9b 边界注记。
- [x] `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（口径同 Phase 1）。（终局执行 BUILD SUCCESS，证据同 Phase 1 EC⑤：五模块 2451|0，mtime 审计 fresh=total，4 项方法级排除如实记录）
- [x] 既有 EQL 语料 BC：三份必测与 WI0 基线快照逐字一致；不声称"7 个全测"。（终局 run 内 `TestEqlCorpusBaseline` 1/1 PASS（mtime 07:53 新鲜，4.835s 全量比对）；三份必测载体 `:nop-job-dao`/`:nop-auth-service`/`:nop-orm` 在 `-pl` 闭包内）
- [x] `ai-dev/logs/` 对应日期条目已更新。（`ai-dev/logs/2026/10-04.md` WI9a Phase 2 + 终局条目）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [x] roadmap WI9a EC ①：每个落地类型（别名 + UUID 若落地）在"支持"与"不支持"两类方言上各有用例（按 Phase 0 裁定的载体表逐项核对；NUMBER/UUID 若裁定为特殊形态，按裁定登记 EC ① 的适用口径）。（TEXT/NUMBER/BOOL：h2+pg/oracle 正向 vs mssql/minimal 负向；UUID（枚举形态）：h2/pg/duckdb/mssql 正向 vs mysql/oracle/minimal 负向——`TestEqlCastTypeAliasCompile` 12|0 + `TestEqlCastUuidDialectCompile` 8|0）
- [x] roadmap WI9a EC ②：114 个 orm.xml 全量回归通过且影响清单已归档——执行形态 = Current Baseline"G-3 落地门（114 个 orm.xml 全量回归）的执行形态"专节的**组合判据 (a)**（`clean install -DskipTests -T 1C` + orm 全闭包 `-pl` 带测；带测 `clean install -T 1C` 因 stream 既有失败字面不可达，不采用单命令 `-Dtest` 豁免口径）。（两命令通过，证据见 Phase 1 EC；影响清单=空清单归档裁定文档 §一，落地时重跑 0 命中）
- [x] roadmap WI9a EC ③：`dataTypeName_` 必拒项收窄对照表已定稿落档（14 项逐行，含错误形态迁移与 WI9b 边界注记）。（裁定文档 §五实测终稿 + `eql-and-database-compatibility.md` 对照表）
- [x] roadmap WI9a EC ④：`orm-model-design.md` 已同步别名表（+ UUID 形态，若落地）；`eql-and-database-compatibility.md` 登记行已落。
- [x] roadmap WI9a EC ⑤：`./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（含 stream 1 项既有失败豁免口径）。（终局 BUILD SUCCESS + 2451|0 + mtime 审计）
- [x] roadmap WI9a EC ⑥：既有 EQL 语料 BC 三份必测与 WI0 基线快照逐字一致；未声称"7 个全测"。（终局 run `TestEqlCorpusBaseline` 1/1）
- [x] roadmap 约束 4：一致性 + 不等价两类用例齐备（Phase 1 EC 的对应项）。
- [x] 两个 Protected Area 的提交纪律可审计：`StdSqlType.java` 提交与 `.g4` 提交分离、方言 XML（若有）逐条独立提交；无 lexer token 删除；无手改生成物。（`5154661e75`（StdSqlType+测试）/ `8574a33371`+`462d60c459`+`e7d8ddeac9`+`0ca16cb4d0`（4 方言 XML 逐条，各携用例）/ `41bfbcba56`（.g4+重生成+测试）；重生成 diff 审计零 token 变化）
- [x] `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（若方言 XML 有新增条目，快照口径同步）。（exit 0；快照只记可加载性+functions，UUID 条目无需同步——裁定文档 §四.3）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本 plan 新增 broken link = 0（3 项既有 warning 基线不动：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，roadmap EC 第 6 条口径）。（exit 0、0 error、3 warning 为既有基线）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/25-eql-wi9a-cast-type-aliases.md --strict` 退出码 0。（closure audit 后主会话复跑 exit 0）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中（别名/收窄都是"改接受面"，anti-hollow 抓的是"只放行不设门/只删文法不留证据"的半实现形态；`nop-commons` 侧改动随 G-3 落地门（组合判据 (a)）与接线验证用例覆盖，并在 closure audit 中抽查）。（exit 0）
- [x] Anti-Hollow Check：closure audit 已验证（a）别名从 EQL 文本 → 闸门 A → `AstToSqlGenerator` → 方言文本的端到端路径有测试；（b）收窄后"文法放行 ⇒ 语义必过或语义响亮拒"无静默路径；（c）无空方法体/静默跳过/no-op 作为"已落地"。（独立审计复跑 8 测试类全绿含 34 键穷举 `TestEqlCastTypeNarrowingParse` 6|0 与 `nosuchtype` 守护 4|0）
- [x] 独立 closure audit 已完成并由**不同 task_id** 的子 agent 执行，evidence 写入本 plan `Closure` 段（guide #26/#27）；roadmap Work Item Status 的 WI9a 行据此才可 `planned` → `done`。

## Deferred But Adjudicated

### `TIME/TIMESTAMP WITH LOCAL TIME ZONE`（时区类型全部门面）

- Classification: `out-of-scope improvement`（roadmap 已显式划归 WI9b，非本 plan 延期）
- Why Not Blocking Closure: roadmap `:327-332` 明文 WI9b 必须独立成 plan（取消注释改变既有部署 DDL 类型映射，需独立迁移评估）；依赖图 WI9a → WI9b 单向。
- Successor Required: `yes`
- Successor Path: WI9b 独立 plan（本 plan 收口时若 WI9b plan 尚未起草，在 roadmap WI9b 行登记"待起草"状态，不允许名义 owner）。

## Non-Blocking Follow-ups

- `DOUBLE` 拼写缺口（WI9a 执行期新发现）：`DOUBLE` 是 `StdSqlType` 枚举成员但为关键字 token 且不在 `unreservedWord_`、无显式文法备选——`cast(x as double)` 今天即解析失败；其唯一可解析拼写 `DOUBLE PRECISION` 已随 BUG-11 收窄删除，`DOUBLE` 家族现无任何拼写入口（`ARRAY`/`ANY` 同族，收窄前已存在）。属 roadmap 未登记的文法扩展，按 Out Of Scope 不顺手修；successor = roadmap 登记后入后续批次（裁定文档 §五登记）。
- `TestEqlCastBooleanCompile` javadoc 中"db2 不可实例化"注释随 WI3 第 12 项落地过时（db2 现可加载）——注释性文档噪声，未随本 plan 改动以避免 diff 噪声；WI9b 或下次触碰该文件时顺带更正（裁定文档 §2.1 登记）。
- `TEXT` 在 mssql 方言语境的 native 语义（native TEXT→std VARCHAR）与 std 别名 `TEXT→CLOB` 的术语不对称：文档对照表登记即可，无需代码动作（两张映射表独立，无冲突）。
- `STRING=358` 类"parser 词表有、lexer 无规则"的隐式 token 现象（与 WI2 登记的 `STRING=358` 同族）：本 plan 不扩大该治理面，仅在新registrations 出现时按 WI2 的 token 分叉证据纪律登记。
- roadmap Cross-Cutting 的 CI `git diff --stat` 阈值告警 carrier 待定（WI0 Non-Blocking Follow-up 延续）：本 plan 以"提交切分 + 重生成 diff 审计口径"作为过程替代。
- `nop-task-core` `TestExecutorTaskStepWrapperAsyncBranch`（WI9a 终局 run1 新发现的异步负载敏感 flake：`-T 1C` 并行下 `asyncSubStepFailure_propagatesException` 偶发 uncaught async 异常，单跑 2/2 PASS 0.141s，与本 WI 零代码关联）——移交 nop-task owner 评估加并发隔离/重试（同 `TestBatchTaskCheckpoint` 移交先例）。

## Closure

Status Note: Phase 0/1/2 全部 `completed`；独立 closure audit（fresh-session 子 agent）一次通过——A①-⑦/B1-B5/C/D 全 PASS，**0 Blocker / 0 Major / 2 Minor**（① clean install 门在 `_tmp/wi9a/` 无对应日志——`.nop/repository` 安装时间戳 07:05:55-07:06:50 晚于末次产品提交 + jar 内容含 WI9a 代码构成充分间接实证，不拦截；② G-3 增补裁决落新文档 §三而非 2026-10-03 原文追加——与该文档自带修订模式一致、原文本零改动，可接受载体偏差）。**mtime 审计（连续四次拦截过期报告后的关键复核点）由审计员独立抽查属实**：六模块 fresh=total（阈值 07:05:37）。G-3 增补裁决（NUMBER→DECIMAL）授权链完整：原裁决文本零改动、增补落新文档含人可推翻声明、roadmap 原地注记。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: fresh-session 独立子 agent（与执行者不同 task_id），2026-10-04
- Audit Session: WI9a closure audit（一次通过，结论"WI9a 通过 closure audit——roadmap WI9a 可以翻 done"）
- Evidence:
  - A① PASS：4 组正/负载体齐全（TEXT/NUMBER/BOOL/UUID × h2/pg/duckdb/mssql 正向 vs mssql/minimal/mysql/oracle 负向）；`TestEqlCastTypeAliasCompile` 12|0 + `TestEqlCastUuidDialectCompile` 8|0；审计员复跑 8 测试类全绿（mtime 08:01 全新鲜）
  - A② PASS：组合判据 (a) 两命令过（`.nop/repository` install 时间戳间接实证 + run3 六模块 2657|0 逐字一致）；mtime 审计 fresh=total（56/56、54/54、44/44、158/158、6/6、92/92）、孤儿报告 0；影响清单=空（双口径 0 命中归档）
  - A③ PASS：收窄对照表 14 行终稿+owner doc 对照表；红证据单条汇总一次列全 14 项收窄前错误码；`TestEqlCastTypeNarrowingParse` 34 键穷举 6|0；WI2 nosuchtype 断言原文在位 4|0
  - A④ PASS：`orm-model-design.md:56-70` 新节（别名表+G-3 增补注记+UUID 映射）；`eql-and-database-compatibility.md:236-272` 渲染表/收窄表/守护锚+能力门登记 :393
  - A⑤ PASS：终局 `-T 1C` BUILD SUCCESS + 2451|0（212/351/1340/87/461）mtime 全晚于 5f9bc6a5ba
  - A⑥ PASS：`TestEqlCorpusBaseline` 1|0（审计员独立复跑确认）
  - B1 PASS：`5154661e75` 3 别名 put+UUID 枚举（GEOMETRY 后追加不动 ordinal）+三 map 不变量；WI9b 边界=时区区域 0 触碰（git diff 实证）
  - B2 PASS：G-3 增补裁决授权链（原文零改动+增补小节四要素+roadmap 原地注记）；UUID ask-first 级裁决人注记单独声明
  - B3 PASS：4 方言 UUID 独立提交逐条可 revert；mysql/oracle/minimal 负向真实报 `nop.err.dao.dialect.std-data-type-not-supported`
  - B4 PASS：`41bfbcba56` 仅 4 文件；文法现态 12 活备选+BOOLEAN+兜底（候选 A）；`ast/_gen`/Lexer/tokens/_EqlASTBuildVisitor 零 diff
  - B5 PASS：执行期 6 偏差全部落档
  - C PASS：matrix 0 / doc-links 0 error 新增 0 / scan-hollow 0 命中 / checklist exit 0
  - D PASS：3 Phase+55 勾 3 留白+daily log 三条目+8 commit 一一对应
  - Deferred 项分类检查：无 in-scope live defect 被降级；DOUBLE 拼写缺口与 task-core 异步 flake 均 Non-Blocking Follow-ups（执行前已存在/零代码关联）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
