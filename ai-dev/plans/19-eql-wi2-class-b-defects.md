# 19 EQL WI2 — B 类缺陷（5 项：1 回显 + 3 解析 + 1 潜在静默丢弃）

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI2 节 + 门控矩阵 WI2 行 + Cross-Cutting 全部 + 关键约束与红线节，v7.2）
> Related: `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`（硬前置，已 `completed`）、`ai-dev/plans/03-eql-wi1-class-a-defects.md`（并行实施中，行号漂移见 Current Baseline 注记）、`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`（G-1 已裁决，本 plan 仅 BUG-8 消费）

## Purpose

收口 roadmap WI2：5 个 B 类缺陷全部停止"静默产出错误 SQL / 响亮失败被 CCE 掩盖 / 潜在静默丢弃"——BUG-4（回显丢 `*` 与 `LIMIT`）、BUG-6（`full outer join` 解析失败 + `supportFullJoin` 零接线）、BUG-7（小写 `date '…'` 的 CCE 掩盖真实错误码）、BUG-8（`cast(x as boolean)` 解析失败）、BUG-15（`SqlTypeExpr.characterSet`/`collate` 的潜在静默丢弃防护）。每个缺陷都有断言具体 SQL 文本（或具体错误码）的"修复前红、修复后绿"回归测试；BUG-6 同批次落方言能力门（"只打印不设门 = 净回归"排序原则）。

## Current Baseline

以下事实全部于 2026-10-03 在本工作区分支 `feat/eql-cross-db-compat` 实测核验（含 file:line）。行号以本起草时点为准；WI1 并行实施会导致漂移，执行时以符号定位（见下方注记）。

### WI0 已落地（本 plan 的验证骨架）

- 方言能力矩阵测试：`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialectCapabilityMatrix.java`（提交 `8b41b0a776`）；快照 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`；门禁脚本 `ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs`。
- 既有 EQL 语料基线快照：`ai-dev/audits/eql-wi0/eql-corpus-baseline.md`（生成/校验测试 `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlCorpusBaseline.java`，棘轮区间 `EQL-CORPUS-BASELINE-BODY-START/END`，重新生成命令见该文件头）。快照逐语句记录 normalized source 与 echo 回显文本。**实测：34 条 echo 中零 `select *`、零 `limit`/`offset` 语句；语料含 13+ 处简单字符串字面量（`'Fall'`/`'Spring'`/`'Biology'` 等，无内嵌引号）、零布尔字面量** ⇒ BUG-4 的 `printSelect` 补齐预期不触碰任何语料 echo，但"预期零 diff"受字面量形态裁定约束（前提与挂钩见"与 roadmap 的差异登记"第 5 条与 Phase 0 第 2/6 项；执行时须复核）。
- 四门裁决：`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`——G-1 已裁决**采纳**承诺基线（Oracle 23ai 含原生 `BOOLEAN`；全部 unverified 边界维持 unverified 纪律）。⇒ **BUG-8 已不被 G-1 阻塞**：EQL 文法层接受 `BOOLEAN`，低于 Oracle 23ai 的版本不在承诺范围内，本 plan 不为任何方言做 BOOLEAN lowering（WI9a 复用 `BOOLEAN`，roadmap WI9a 节明文）。
- WI0 plan 已 `completed`（closure audit 通过，提交 `f690c765fd`）。**stream 既有失败基线已收窄为 1 项**：`nop-stream-runtime` 仅 `TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication` 失败（debezium-cdc capability 期望 SINGLE_INSTANCE 实为 PARALLEL），实测记录 `ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 头部第 2 条红线事实。

### WI1 并行实施注记（行号漂移声明）

WI1 plan（`ai-dev/plans/03-eql-wi1-class-a-defects.md`）已起草、代码尚未落地（起草时点 `git status` 仅有未追踪的 WI1 plan 文件）。WI1 将修改与本 plan 重叠的代码区域：其 BUG-3 修改 `printSelect` 的 for-update 段（本 plan BUG-4 的同一方法内）、其 BUG-10 修改 `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` 的 `joinType_` 规则块（本 plan BUG-6 的 `fullJoin_` 同块）、其 BUG-1 修改 `AstToSqlGenerator.visitSqlLikeExpr`、其 BUG-5 另有两个重叠面——修改 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlIntervalExpr`（`:346-348`，与本 plan BUG-8 的 `dataTypeName_` **同文件**，两次 `.g4` 重生成须按提交时序衔接）与 `AstToEqlGenerator.visitSqlIntervalExpr`（`:887`，与本 plan BUG-4 的字面量方法**同文件**）。⇒ **本 plan 所有 file:line 锚点在执行时可能漂移，一律以符号定位为准（方法名 / 文法规则名 / 常量名）；每个 Phase 开工前先 rebase 到当时代码基线并复核锚点符号仍存在**。

### 五个缺陷的代码事实（全部实测）

- **BUG-4 回显丢 `selectAll` 与 `LIMIT`**：`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java` 的 `printSelect`（`:437-472`）是手写遍历——依次打印 distinct/projections/from/where/groupBy/having/windowClause（`:460-462`，`add-stream-sql` 合并带入）/orderBy/forUpdate，**从不 visit `node.getLimit()`**；且 projections 为空时直接什么都不打印（`selectAll` 不回显 `*`）。⇒ `select * from t` 回显为 `select from t`、`limit 10 offset 5` 整体丢失。`visitSqlLimit`（`:598-608`）**在类内无直接调用方但并非死代码，勿删**（roadmap 明令）：`_SqlQuerySelect.java:311-312` 已把 `limit` 注册为可遍历 child、`EqlASTVisitor.java:113-115` 有 `case SqlLimit` 分派，可经遍历到达。**执行路径正常**：`EqlTransformVisitor.java:300-307` 在分析期展开 `selectAll`（`getAllProjections`），`AstToSqlGenerator.visitSqlQuerySelect`（`:91-100`）把 `limit` 交给 `dialect.getPaginationHandler().buildPageExpr(...)`（`:95-96`）⇒ 仅 `toSqlString()`/`@dump` 回显路径受影响。连带不一致两处：字符串转义 `AstToEqlGenerator.java:715` 用 `StringHelper.escapeSql(node.getValue(), false)`，而执行路径 `AstToSqlGenerator.java:410` 用 `dialect.getStringLiteral(node.getValue())`；布尔字面量 `AstToEqlGenerator.java:750-752` 硬编码 `print(node.getValue() ? " 1 " : " 0 ")`，而执行路径 `AstToSqlGenerator.java:416` 用 `dialect.getBooleanValueLiteral(node.getValue())`（accessor 已存在：`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java:198` 与 `:212`）。
- **BUG-6 `full outer join` 解析失败 + `supportFullJoin` 零接线**：文法 `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` 的 `fullJoin_: FULL JOIN`（`:181-182`）**不带 `OUTER?`**，而同块 `leftJoin_: LEFT OUTER? JOIN`（`:184-185`）、`rightJoin_: RIGHT OUTER? JOIN`（`:187-188`）都带 ⇒ `full join` 可解析、`full outer join` 解析失败。AST/生成侧已就绪：`SqlJoinType.FULL_JOIN("full join")`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/enums/SqlJoinType.java:11`）、手写映射 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java:291-299`（`:296` 返回 `FULL_JOIN`）、生成器打印 `AstToEqlGenerator.visitSqlJoinTableSource`（`:625-635`，`:630` 输出 `getJoinType().getText()`）、`EqlTransformVisitor.markMainSource`（`:523-537`）已处理 `FULL_JOIN`。**能力门缺失**：`supportFullJoin` 已声明于 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef:55`、已配值于 `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/default.dialect.xml:23`（`="true"`），冻结的生成类 `_DialectFeatures.java:290` 已有 getter——但 **`IDialect` 无 accessor、`DialectImpl` 无实现**（全仓 grep 零命中），即零消费点。接线先例（window frame 三能力位）：`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java:89-93` accessor + `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java:287-297`（`Boolean.TRUE.equals(dialectModel.getFeatures().getXxx())`）——本 flag **无需动 xdef、无需动 retention 类**（生成基类已有字段）。编译期能力门先例：`EqlTransformVisitor.checkWindowFrameFeature`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:354-383`）抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlErrors.java:195`，param `ARG_DIALECT`/`ARG_FEATURE`），feature 常量先例 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlConstants.java:26-28`。**方言值现状**：`mysql.dialect.xml:3` extends `default`（未覆写 ⇒ 继承 `true`）、`mariadb.dialect.xml:3` extends `mysql`（同样继承 `true`）；而已确认无 `FULL JOIN` 的只有 MySQL/MariaDB（TDengine 自 3.3.0.0 起支持、MSSQL 支持 `FULL OUTER JOIN`，roadmap WI7b 节）⇒ 负向测试要求 MySQL/MariaDB 编译期报错，必须先把这两方言显式配 `false`（见"与 roadmap 的差异登记"第 2 条）。
- **BUG-7 小写 `date '…'` CCE**：词法层关键字大小写不敏感（`nop-persistence/nop-orm-eql/model/antlr/Alphabet.g4:22` `fragment A: [Aa]` 等；`nop-persistence/nop-orm-eql/model/antlr/Keyword.g4:377-388` DATE/TIME/TIMESTAMP）⇒ 小写 `date` 正常词法化为 DATE token 并经 `sqlDateTimeLiteral`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:45-47`）正常进入语法。CCE 在手写访问器：`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java:184-191` 的 `SqlDateTimeLiteral_type(Token node)`——`SqlDateTimeType.fromText` 是大小写敏感的精确匹配（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/enums/SqlDateTimeType.java:32` `type.getText().equals(text)`），小写 `date` 返回 null 后进入错误分支，而 `:188` 的 `.loc(loc((ParserRuleContext) node))` 对声明为 `Token` 的 `node` 强转 ⇒ **先抛 `ClassCastException`，掩盖 `ERR_EQL_INVALID_DATETIME_TYPE`**。修法落点即此方法：值先 `toUpperCase(Locale.ROOT)` 再 `fromText`；去掉强转——`ParseTreeHelper.loc(Token)` 重载已存在（`io.nop.antlr4.common.ParseTreeHelper`，`loc(Token)` 于 `:18`）。⚠️ 同文件 `:200`（`SqlIntervalExpr_intervalUnit`）有一处**形似但类型正确**的同款强转（参数本就是 `ParseTree`），不得盲目批量"修复"。
- **BUG-8 `cast(x as boolean)` 解析失败**：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:377-382` 的 `dataTypeName_` **不含 `BOOLEAN`**（实测规则全文：CHARACTER/VARCHAR/…/DATE/TIME/TIMESTAMP/INTERVAL/带时区类型 + `sqlIdentifier_`）。`BOOLEAN` 是独立关键字 token（`Keyword.g4:353-355` ⇒ `EqlParser.java:38` `BOOLEAN=125`），**不是** `IDENTIFIER_` 也不在 `unreservedWord_`（`BaseRule.g4:69-93`）⇒ `sqlIdentifier_` 备选分支救不了它，`cast(x as boolean)` / `cast(x as BOOLEAN)` 均解析失败。语义链已就绪：手写 `SqlTypeExpr_name`（`EqlASTBuildVisitor.java:268-274`）把名字 `toUpperCase(Locale.ROOT)` 后经 `StdSqlType.fromStdName` 校验放行；`StdSqlType.BOOLEAN` 已存在（`nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdSqlType.java:22`）；生成侧 `AstToSqlGenerator.visitSqlCastExpr`（`:455-475`）经 `dialect.stdToNativeSqlType(sqlType, precision, scale)`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java:189-190` 委托 `sqlDataTypeMapping`）产方言情.native 类型。⇒ 文法加一个 `BOOLEAN` 备选即可全链打通；G-1 已裁决（见 WI0 小节），无版本门代码需要本 plan 落地。
- **BUG-15 `SqlTypeExpr` 潜在静默丢弃（今天不可达）**：写入侧存在——生成物 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/_EqlASTBuildVisitor.java:1331/1334` 调用手写 `SqlTypeExpr_characterSet`（`EqlASTBuildVisitor.java:321-324`）/`SqlTypeExpr_collate`（`:327-330`）写入字段；读取侧为零——`AstToEqlGenerator.visitSqlTypeExpr`（`AstToEqlGenerator.java:930-943`）只打印 name/precision/scale，`AstToSqlGenerator.visitSqlCastExpr`（`:455-475`）绕过 `SqlTypeExpr` 的渲染。**不可达是机器可证的**：`characterSet_` 期望 `STRING`（`BaseRule.g4:384-385`）、`collateClause_` 期望 `STRING_`（`:388-389`），而 `STRING` token 只存在于 parser 侧——`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java:80-81` token 表以 `STRING=358` 结尾，`EqlLexer.java` 同位置表到 `HEX_DIGIT_=356, BIT_NUM_=357;` 即终止（无 `STRING`）⇒ 该 token 无任何 lexer 规则可产生，两条分支今天永久不可匹配（`collateClause_` 的 `STRING_` 虽存在但期望带引号字面量，`utf8`/`xx` 词法为 `IDENTIFIER_`，同样不可达）。⇒ 尚未产生错误 SQL，但一旦有人修好文法就会退化成 BUG-1 同型静默丢弃 ⇒ 本 plan 落**显式拒绝**防护 + 守护测试。

### 生成管线事实（决定 Protected Area）

- **`nop-orm-eql` 侧生成管线是活的**：`.g4` 改动原地重生成 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java`（实测 10785 行）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/_EqlASTBuildVisitor.java`（实测 1804 行）、`ast/_gen/`（77 个文件）及 `EqlLexer*`/`Eql.interp`/`*.tokens`。**本 plan 有两处 `.g4` 改动**：`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` 的 `fullJoin_` 加 `OUTER?`（BUG-6）与 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `dataTypeName_` 加 `BOOLEAN`（BUG-8）——各自**单独提交**（roadmap Cross-Cutting 生成物变更纪律）。两处均只新增备选/可选 token，**不删除任何 lexer token**（无重编号风险；BUG-15 若未来修文法涉及 `STRING` token，不属本 plan）。
- **`nop-dao` 侧零生成**：`supportFullJoin` 的 accessor 补齐只手改 `IDialect.java` + `DialectImpl.java`（`nop-dao` 无 codegen 管线，`nop-persistence/nop-dao/pom.xml:119`；roadmap Cross-Cutting 第一行）。`dialect.xdef` 与 retention 类本 plan **均不需改动**（flag 已声明、冻结生成类已有字段，见 BUG-6 事实）。

### 既有 EQL 语料与全绿口径

- 仓库 7 个含 `<eql>` 的 `sql-lib.xml`；本 plan `-pl` 能覆盖的必测三份：`nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/sql/NopJobTask.sql-lib.xml`、`nop-auth/nop-auth-service/src/test/resources/_vfs/nop/auth/sql/test.sql-lib.xml`、`nop-persistence/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml`。`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份**不在本 plan `-pl` 内**（后者无 `src/test` 源码），由 roadmap 级 EC 第 8 条 + WI0 静态快照兜底。**禁止声称"7 个全测"**。
- 测试基建先例：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlTranslationSemantics.java`（手工 dialect 构造 `buildDialect()`）；方言加载先例 `TestDialectCapabilityMatrix.java`（`DialectManager`）；语料棘轮先例 `TestEqlCorpusBaseline.java`。测试惯例遵循 `docs-for-ai/02-core-guides/testing.md`（错误码断言具体错误码而非"抛异常即可"）。

## Goals

- 5 个 B 类缺陷（BUG-4/6/7/8/15）全部收口：BUG-4 回显路径输出完整 EQL（含 `*` 与 `LIMIT`/`OFFSET`，字面量渲染与执行路径一致）；BUG-6 `full outer join` 可解析且受 `supportFullJoin` 能力门控；BUG-7 小写日期字面量正常解析、非法类型名响亮报 `ERR_EQL_INVALID_DATETIME_TYPE` 而非 CCE；BUG-8 `cast(x as boolean)` 全链可解析可生成；BUG-15 两个零读取字段有显式拒绝 + "未来文法修好后不得静默丢弃"的守护测试。
- 每个缺陷有"修复前红、修复后绿"回归测试，断言**具体 SQL 文本**（或具体错误码），不是"没抛异常"。
- BUG-6 的门有"该方言不支持时编译期报错"负向用例（MySQL/MariaDB 载体）；`supportFullJoin` 从 XML 配置值到生成行为的运行时调用链有接线验证。
- 两处 `.g4` 改动各自单独提交，重生成可审计。
- 既有 EQL 语料 BC：`:nop-job-dao` / `:nop-auth-service` / `:nop-orm` 三份必测与 WI0 基线快照逐字一致。

## Non-Goals

- 不修 A 类 6 项（BUG-1/2/3/5/10/14，WI1）。
- 不实现 `SqlTypeExpr.characterSet`/`collate` 的真正生成语义（roadmap 未授权任何 WI 消费它们；本 plan 只落显式拒绝防护）。
- 不为 `cast(x as boolean)` 做低于 Oracle 23ai 的方言 lowering（G-1 已采纳 23ai 为承诺基线；类型别名 `NUMBER`/`TEXT`/`BOOL`/`UUID` 与必拒项收窄归 WI9a）。
- 不改任何 WI3 范围内的方言 XML 能力值（`supportReturningForUpdate` 等纠错归 WI3，G-4 授权边界）；本 plan 只**新增** `mysql`/`mariadb` 的 `supportFullJoin="false"` 显式配置（依据见差异登记第 2 条）。
- 不做 `natural join`、`CROSS JOIN`（WI1 BUG-10）、分页新形态与 `NULLS FIRST/LAST`（WI7）。
- 不引入 `:nop-orm-tdengine` / `:nop-db-migration` / `:nop-orm-demo` 进 `-pl`（roadmap 级 EC 第 8 条覆盖）。
- 不做真实多数据库 CI。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql`：`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java`（BUG-4：`printSelect` 补 `selectAll`/`limit` 回显；字面量渲染一致化）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（如一致化需双侧对齐）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`（BUG-7 `SqlDateTimeLiteral_type`、BUG-15 两个访问器）、`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（`fullJoin_`）与 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`dataTypeName_`）→ 重生成、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java`（BUG-6 能力门消费点，按 Phase 0 裁定）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlConstants.java` 与 `OrmEqlErrors.java`（如需新常量/错误码）、对应测试（含红/绿回归与守护测试）。
- `nop-persistence/nop-dao`：`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java` + `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`（`isSupportFullJoin()` accessor，照 window frame 先例）；`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/mysql.dialect.xml`（显式 `supportFullJoin="false"`；`mariadb.dialect.xml` 继承 mysql，是否显式重复配置由 Phase 0 裁定）。
- 文档：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（BUG-15 显式拒绝行为登记，与 WI1 的 ESCAPE 章节衔接；FULL JOIN 能力矩阵行标证据等级）、`ai-dev/design/eql-cross-db-compat/`（Phase 0 裁定文档）、`ai-dev/logs/`。

### Out Of Scope

- Non-Goals 所列全部；以及任何 roadmap 未登记的方言语法扩展（发现新缺陷 → 登记 roadmap/新 plan，不顺手修）。

## 与 roadmap 的差异登记（起草时实测发现）

1. **行号微漂**：roadmap 称 limit 的遍历分派在 `EqlASTVisitor.java:114`，实测 `case SqlLimit` 在 `:113-115`（分派语句 `:114`，语义一致）；`AstToEqlGenerator.visitSqlTypeExpr` 实测方法签名在 `:930`（roadmap `:929-943`，为含注记的范围，语义一致）。均为 ±1 行漂移，不构成事实偏差。
2. **BUG-6 隐含一个方言契约值变更**：roadmap WI2 EC 要求"`full outer join` 在 MySQL/MariaDB 上有编译期报错负向用例"，但实测 `default.dialect.xml:23` 配 `supportFullJoin="true"` 且 `mysql.dialect.xml`/`mariadb.dialect.xml` 未覆写——按现状接线后 MySQL/MariaDB 会被判定为"支持"，负向用例无法成立。⇒ 接线必须同批次给 `mysql.dialect.xml`（及 `mariadb.dialect.xml`，若 Phase 0 裁定显式配置）落 `supportFullJoin="false"`。G-4 的批准范围是 WI3 的 11 项，不含此值；本 plan 以 roadmap WI2 EC 原文（draft review 已通过）为该变更的授权依据，并把它列为 Phase 2 的显式 Decision 项——执行者不得静默扩改其他方言或其他标志。
3. **BUG-6 接线面的 roadmap 表述修正**：roadmap WI2 表称"需先补 accessor（**手改 retention 类**，零生成）"——实测**retention 类（`DialectFeatures.java`）不需改**：flag 已在 `dialect.xdef:55` 声明、冻结生成类 `_DialectFeatures.java:290` 已有 getter，缺的只是 `IDialect` accessor 与 `DialectImpl` 实现（"手改 retention"只适用于 `supportLateral` 这类全新 flag，即 WI1 的场景）。接线面比 roadmap 表述更小，Protected Area 定性不变（零生成）。
4. **stream 既有失败基线**：WI1 plan 的 Closure Gates 仍写"3 项既有失败"；roadmap Cross-Cutting 与 WI0 语料基线头部已按 2026-10-03 实测收窄为 **1 项**（`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`）。本 plan 采用 1 项口径。
5. **语料 BC 与 BUG-4 无冲突，但前提是两个字面量事实**：WI0 快照 34 条 echo 实测零 `select *`、零 `limit`/`offset` 语句 ⇒ BUG-4 的 `printSelect` 补齐本身不触碰任何语料回显文本。但"预期零 diff"的完整前提是：**（a）语料字符串字面量无内嵌引号、（b）语料零布尔字面量**——起草时实测两者今天均成立：语料含 **13+ 处字符串字面量**（`'Fall'`/`'Spring'`/`'Biology'` 等，normalized source 与 echo 合计 24 行命中），但 SQL 双写 `''` 与转义 `\'` 均 **0 命中**（简单字面量，`escapeSql(value,false)` 与 `dialect.getStringLiteral` 对其渲染等价）；布尔字面量 **0 条**（快照中 `TRUE`/`FALSE` 仅出现在 parse-error 的 expected token 集合，非语句内容）。⇒ 一旦 Phase 0 第 6 项的字面量形态裁定改变**简单字符串或布尔字面量**的回显渲染，零 diff 前提即失效，必须先裁定快照重生成范围再动生成器（见 Phase 0 第 2 项与 Phase 1 BC 判据的挂钩）。
6. **BUG-4 字面量一致化候选 B（注入 dialect）的 API 影响面定价**：回显路径由 `EqlASTNode.appendTo`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/EqlASTNode.java:26-29`）**硬编码** `new AstToEqlGenerator(sb)`（`:27`）驱动，`toSqlString()`（`:31-35`）与 `toSQL()`（`:37-40`）均经它——整条调用链无 dialect 可得。候选 B（回显生成器注入 dialect）意味着改动 `appendTo`/`toSqlString`/`toSQL` 的公开签名链，或引入"默认方言回退"这类语义含糊的形态，属 **AST 公共 API/语义面变更、成本显著高于候选 A**，且与 roadmap 修法原文（"也走 dialect，使回显路径与执行路径一致"）存在**实质偏离**（该表述在无 dialect 的回显路径上不可直接落地）。⇒ Phase 0 第 6 项裁定据此**倾向候选 A**（保持 EQL 语法形态、消除语义分歧），终裁权保留。
7. **（执行期增补，2026-10-03 Phase 5 实测）`collateClause_` 的 quoted 形态今天可达**：起草时"两条分支今天永久不可达"的结论对 `characterSet_`（任何形态）与 `collateClause_` 的 unquoted 形态成立（token 分叉证据不变），但 **quoted 形态 `collate 'xx'`（`STRING_` 可词法化）今天即可解析**——collate 字段被写入后被两个生成器静默丢弃，即 BUG-15 存在一条活的最小静默丢弃通路（不只是"未来文法修好后"的隐患）。BUG-15 的候选 A 守卫把该通路即刻转为响亮拒绝（`TestEqlSqlTypeClauseGuard.testQuotedCollateExplicitlyRejectedNotSilentlyDropped` 的真实 red→green）。roadmap 对 BUG-15 "尚不可达"的表述据此增补（不构成 live defect 降级——防护在 Phase 5 同批落地）。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（`fullJoin_` 加 `OUTER?`）与 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`dataTypeName_` 加 `BOOLEAN`）→ `EqlParser.java`（10785 行）等重生成 | **生成管线，plan-first**（AGENTS.md Protected Area"生成管线"；roadmap 门控矩阵 WI2 行明文"plan-first（仅 `.g4` 侧）"） | 本 plan 即 plan-first 载体；执行前须经 draft review | owner doc（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`）+ 回归测试（各 Phase 红/绿用例 + 三份必测 BC） |
| `nop-persistence/nop-dao` 的 `IDialect.java` + `DialectImpl.java`（`isSupportFullJoin`）与 `mysql.dialect.xml`/`mariadb.dialect.xml` 值变更 | **非生成管线、非 AGENTS.md 字面 Protected Area**（`nop-dao` 无 codegen 管线；`nop-dao`/`nop-xdefs` 不是 `nop-*-api`）。方言 XML 是平台对外契约，值变更按 roadmap G-4 同等谨慎处理——本 plan 的授权依据为差异登记第 2 条（roadmap WI2 EC 原文），并在 Phase 2 保留为显式 Decision 项 | 不触发 plan-first；登记 + 独立提交可 `git revert` | 方言矩阵快照门禁（`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs`）+ 负向测试 + owner doc |
| `nop-persistence/nop-orm-eql` 的 `EqlTransformVisitor.java`/`EqlASTBuildVisitor.java`/`AstToEqlGenerator.java`/`OrmEqlConstants.java`/`OrmEqlErrors.java` | 普通手写代码 | 无额外门禁 | 回归测试 |
| 生成物纪律 | 两处 `.g4` 改动**各自单独提交**（与逻辑改动分离）；不删除任何 lexer token（本 plan 无此需求） | 提交拆分在 git 历史可审计 | `git log` 可见文法提交与逻辑提交分离 |

⚠️ 本 plan 不触碰 `model/*.orm.xml`、`nop-auth` 认证模型、`nop-*-api` 公共接口、`_DialectFeatures.java`/`_gen/` 等冻结生成物。

## Execution Plan

Phase 1-5 相互独立、可分别落地与回滚；Phase 0 为前置纯裁定、不产代码（其第 2/3/4/6 项为后续 Phase 的硬前提）；每个代码 Phase 单独提交。BUG-6 与 BUG-8 的文法改动**各自单独提交**（生成物纪律）；若与 WI1 的文法提交（`crossJoin_`、`sqlIntervalExpr`）相邻落地，按提交时序 rebase，不以行号为准。

### Phase 0 - 前置核验与裁定

Status: completed（2026-10-03，裁定文档 `ai-dev/design/eql-cross-db-compat/2026-10-03-wi2-class-b-adjudications.md`）
Targets: `ai-dev/audits/eql-wi0/eql-corpus-baseline.md`（状态核验）、`ai-dev/design/eql-cross-db-compat/`（新增裁定文档）

- Item Types: `Decision`、`Proof`

- [x] 复核 WI0 两件产物状态未回退：语料基线快照（`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` + `TestEqlCorpusBaseline`，起草时已 `completed`）与方言矩阵快照（`dialect-capability-matrix.json`）——本 plan 每个 Phase 的 BC 判据与门禁脚本依赖它们存在。（实测 2026-10-03：`TestEqlCorpusBaseline` 1/1 通过，WI1 收口后快照零 drift；`check-eql-dialect-matrix-snapshot.mjs` exit 0，18 方言 14 loadable 与快照一致。）
- [x] 复核"语料零 `select *`、零 `limit`/`offset`"与**零 diff 前提的两个字面量事实**（差异登记第 5 条：语料 13+ 处简单字符串字面量无内嵌引号、零布尔字面量）在当时代码基线仍成立；若任一事实已不成立，登记受影响语料条目清单并裁定 BUG-4 的快照重生成范围。**若本 Phase 第 6 项的字面量形态裁定改变简单字符串或布尔字面量的回显渲染，必须先裁定快照重生成范围并人工审阅 diff 之后，才允许动生成器**（Phase 1 的 BC 判据以此裁定为前提）。（实测成立：0 条 `select *`、0 条 limit/offset；'Fall'×12/'Spring'×6/'Biology'×6 合计 24 处命中全部无内嵌引号（`''` 0 命中、`\'` 0 命中）；布尔字面量 0 条——TRUE/FALSE 仅出现在 parse-error expected token 集合。裁定=候选 A：字符串渲染**不改变**、布尔字面量语料零命中 ⇒ 无需重生成快照。）
- [x] 裁定 BUG-15 显式拒绝的落点并记录：候选 A = 解析期拒绝——手写 `SqlTypeExpr_characterSet`/`SqlTypeExpr_collate`（`EqlASTBuildVisitor.java:321-330`）在分支可达时抛错（离生成物的写入点 `_EqlASTBuildVisitor.java:1331/1334` 最近、零生成、守护面最窄）；候选 B = 编译期/生成期拒绝——`EqlTransformVisitor` 或生成器对 `SqlTypeExpr` 的 `characterSet`/`collate` 非空即抛。写明选择理由与被拒候选原因、错误码（复用或新增，遵循 `NopException` + `ErrorCode` + `.param(...)`、消息英文）。（**终裁=候选 A**，accessor 无条件抛错=字段结构性钉死 null；新错误码 `ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT`；守护测试形态=手构 `CharacterSet_Context`/`CollateClause_Context` 直调 accessor。理由与被拒候选见裁定文档 §六。）
- [x] 裁定 BUG-6 能力门消费点：默认照 `checkWindowFrameFeature` 先例落编译期（`EqlTransformVisitor`，`ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + `OrmEqlConstants` 新增 `FEATURE_SUPPORT_FULL_JOIN`）；备选为生成器侧消费。**裁定必须产出门的具体方法名与挂载点并写入裁定文档与本 plan**（候选：独立私有方法 `checkJoinTypeFeature(SqlJoinTableSource)`，从 join table source 的转换遍历点调用，仿 `checkWindowFrameFeature` 的调用形态；或挂载在既有 join 处理分支——由 Phase 0 定名）。记录理由。（**终裁=独立私有方法 `checkJoinTypeFeature(SqlJoinTableSource)`**，挂载 `visitTableSource` 的 SqlJoinTableSource 分支、`visitJoinLeft/visitJoinRight` 之前（先门后遍历；visitJoinLeft 递归回 visitTableSource 保证嵌套 join 全覆盖）；生成器侧与 markMainSource 挂载被拒，理由见裁定文档 §三。）
- [x] 裁定 `mariadb.dialect.xml` 是否显式重复配置 `supportFullJoin="false"`（继承 `mysql` 已可得 `false`；显式配置的自文档化价值 vs 与 WI3 第 7 项"不要顺手改 mariadb"精神的关系——本项只涉及显式与否，两方案值均为 `false`）。（**终裁=显式配置**：负向点名载体的契约自文档性；两方案运行时值相同、非语义变更，与 WI1 mariadb `supportLateral="false"` 先例一致；见裁定文档 §五。）
- [x] 裁定 BUG-4 字面量一致化的验收形态并记录：候选 A = 回显路径继续输出 EQL 语法形态但消除与执行路径的语义分歧（如 `true`/`false` 的回显可被重新解析回布尔字面量）；候选 B = 给回显生成器注入 dialect 使字面量渲染逐字对齐 `dialect.getStringLiteral`/`getBooleanValueLiteral`。**候选 B 影响面定价见差异登记第 6 条**：`EqlASTNode.appendTo`（`EqlASTNode.java:26-29`）硬编码 `new AstToEqlGenerator(sb)`，`toSqlString()`/`toSQL()` 调用链无 dialect 可得 ⇒ 候选 B = 动公开签名链或引入默认方言回退，API/语义面变更、成本高且与 roadmap 修法原文实质偏离 ⇒ **裁定倾向候选 A，终裁权保留**。断言以"同一 AST 两条路径的字面量输出无语义分歧"为准；**裁定结论须同时给出"是否改变简单字符串/布尔字面量的回显渲染"的明确回答**——若改变，Phase 1 动生成器前须先完成 Phase 0 第 2 项的快照重生成范围裁定。（**终裁=候选 A**：字符串维持 `escapeSql(value,false)` 不变（EQL 规范形、round-trip 等价）；布尔回显 `" 1 "/" 0 "` → `" true "/" false "`。明确回答：简单字符串渲染**不改变**、布尔**改变**但语料零布尔字面量 ⇒ 免重生成。执行期精确化两条：① `selectAll && projections.isEmpty()` 才打印 `*`（transform 展开后 projections 非空必须继续打印展开投影，保分页内嵌 `PrintSqlSelect` 路径）；② limit 回显经新钩子 `printLimit`，`AstToSqlGenerator` override 置空——执行路径 limit 在 `visitSqlQuerySelect` 经 pagination 消费，`printSelect` 再打印会同外层 `buildPageExpr` 文本双份 LIMIT。见裁定文档 §二。）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含 BUG-15 拒绝点选型与被拒候选、BUG-6 消费点（含门方法定名与挂载点）、`mariadb` 显式配置与否、BUG-4 字面量一致化验收形态（含候选 B 影响面定价与倾向结论、"是否改变简单字面量回显渲染"的明确回答）。（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi2-class-b-adjudications.md`，四节结论+裁决总表。）
- [x] WI0 两件产物状态核验结论已记录（`completed`/未回退，或显式声明等待并阻塞后续 Phase）。（裁定文档 §四：TestEqlCorpusBaseline 1/1、matrix 脚本 exit 0。）
- [x] 本 Phase 纯裁定，不改产品代码：`No owner-doc update required`（裁定文档本身即交付物）。
- [x] `ai-dev/logs/` 对应日期条目已更新。（`ai-dev/logs/2026/10-03.md` WI2 Phase 0 条目。）

### Phase 1 - BUG-4 回显丢 `selectAll` 与 `LIMIT`（含字面量一致化）

Status: completed（2026-10-03）
Targets: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java`（`printSelect` `:437-472`、`visitSqlStringLiteral` `:714-717`、`visitSqlBooleanLiteral` `:750-752`）、按 Phase 0 裁定可能涉及 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`、`nop-orm-eql` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Fix`、`Decision`（字面量一致化形态，Phase 0 已裁定）、`Proof`

- [x] 修复前红：`select * from t` 的 `toSqlString()` 断言含 `*`——修复前输出 `select from t`，断言红；`select distinct * from t` 断言 `distinct *`；`select … from t limit 10 offset 5` 的回显断言含 `limit 10` 与 `offset 5`——修复前整体丢失，断言红。（红证据实测：`TestEqlSelectAllLimitEcho` 6 红 1 错——`select from t`/`select distinct from t`/limit 丢失 ×2/布尔 `a.flag = 1`/round-trip 再解析 `select [f]rom t` parse-fail。）
- [x] `printSelect` 补齐：`selectAll` 为真时回显 `*`；`limit` 非空时回显 `LIMIT`/`OFFSET`（复用既有 `visitSqlLimit` 或等价输出）。**不得删除 `visitSqlLimit`**（roadmap 明令；`_SqlQuerySelect.java:311-312` 遍历链仍依赖它）。（落地按 Phase 0 执行期精确化：`selectAll && projections.isEmpty()` 才打印 `*`（transform 展开后 projections 非空继续打印展开投影）；limit 经新钩子 `printLimit`（复用 `visitSqlLimit` 未删），`AstToSqlGenerator.printLimit` override 置空避免分页内嵌渲染双份 LIMIT。）
- [x] 字面量一致化（按 Phase 0 裁定形态）：字符串转义（`AstToEqlGenerator.java:715` 的 `StringHelper.escapeSql(value, false)`）与布尔字面量（`:750-752` 硬编码 `" 1 "`/`" 0 "`）不再与执行路径（`AstToSqlGenerator.java:410` `dialect.getStringLiteral` / `:416` `dialect.getBooleanValueLiteral`）产生语义分歧；含单引号字符串与 `true`/`false` 字面量各有断言。（布尔→`" true "/" false "`（重解析回 `SqlBooleanLiteral`）；字符串维持 `escapeSql(value,false)`（与 escapeSlash=false 方言执行渲染逐字一致）。**执行期发现并登记**：EQL parse 层 `stringLiteralValue` 不 un-double `''`，含引号值的回显重解析值含字面 `''`——parse 语义缺口非回显缺陷，入 Non-Blocking Follow-ups；测试改为锁定回显形态与执行形式一致 + 简单字符串 round-trip。既有测试 `TestCollectionOperatorTransformer` 4 处布尔回显期望文本（`<> 0`→`<> false`、`<> 1`→`<> true`）按裁定同步，transform 覆盖面不变。）
- [x] 回显 round-trip 守护：`select * from t limit 10 offset 5` 的回显文本重新解析后 `selectAll`/`limit`/`offset` 语义不丢（本缺陷的复刻即"回显文本再喂回编译器会静默变义"）。（`TestEqlSelectAllLimitEcho.testEchoRoundTripsSelectAllAndLimit`。）
- [x] 执行路径回归：`EqlTransformVisitor.java:300-307` 的 `selectAll` 展开与 `AstToSqlGenerator.java:91-100` 的 pagination 路径行为不变（BUG-4 仅回显缺陷，不波及执行）。（`TestEqlSelectAllLimitCompile`：`select * … limit 10 offset 5` 展开实体列 + LIMIT/OFFSET 各恰一次（双 LIMIT 回归锚）+ 既有 `testLimitOffsetTranslation` 同口径回归。）
- [x] owner doc：如回显契约（`toSqlString()`/`@dump`）有文档表述需同步则更新，或明确写 `No owner-doc update required`。（`eql-and-database-compatibility.md` 新增「EQL 回显路径契约」小节；同步 ESCAPE 小节的 WI2 批次表述为实际落地面（ESCAPE 回显未获授权承接，登记 follow-up）。）

Exit Criteria:

- [x] 红→绿回归测试存在且断言具体 SQL 文本（`select * from t`、`distinct *`、`limit 10 offset 5` 完整回显；修复前红一次并留痕于提交说明或日志）。
- [x] 字面量一致化断言通过（同一 AST 在回显/执行两路径的字面量输出无语义分歧）。
- [x] 回显 round-trip 守护测试通过（`selectAll`/`limit`/`offset` 经回显→重解析不丢失）。
- [x] 既有 EQL 语料 BC：`:nop-job-dao`(NopJobTask)、`:nop-auth-service`(test)、`:nop-orm`(test) 三份必测与 WI0 基线快照逐字一致——**该判据显式挂钩 Phase 0**：其成立前提是 Phase 0 第 2 项的零 diff 前提复核通过（语料简单字符串无内嵌引号 + 零布尔字面量，差异登记第 5 条）**且**第 6 项裁定不改变简单字面量的回显渲染；若裁定改变了渲染，须先按 Phase 0 裁定的快照重生成范围执行（`-Dnop.orm.eql.updateCorpusSnapshot=true` + 人工审阅 diff）并以新快照为 BC 基准，在提交说明中逐条对照变更语句。不声称 7 个全测。（无需重生成：Phase 0 裁定字符串渲染不变 + 语料零布尔；`TestEqlCorpusBaseline` 随全量运行通过，快照零 diff。）
- [x] `./mvnw test -pl :nop-orm-eql -am -T 1C` 全绿。（实测 203 tests 0 failures BUILD SUCCESS。）
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - BUG-6 `full outer join` + `supportFullJoin` 全链接线（.g4，单独提交）

Status: completed（2026-10-03）
Targets: `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（`fullJoin_` `:181-182`）、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`、`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/mysql.dialect.xml`（与 `mariadb.dialect.xml` 按 Phase 0 裁定）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java`（按 Phase 0 裁定的消费点）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlConstants.java`、`nop-orm-eql`/`nop-dao` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Fix`（`OUTER?` 解析缺口）、`Feature`（`supportFullJoin` 接线——存量死标志转活，非新增 flag）、`Decision`（消费点与 XML 值，Phase 0 已裁定）、`Proof`

- [x] 修复前红：`select … from a full outer join b on …` 断言可解析——修复前解析失败，断言红；`full join`（无 OUTER）既有行为守护：仍可解析且输出不变。（红证据实测：`TestEqlFullJoinParse` 2 错（`parse-fail expected=JOIN offendingToken=outer`，实体/裸表两形态）；`full join`/natural 守护 2 绿。）
- [x] 文法补 `OUTER?`：`fullJoin_: FULL OUTER? JOIN`，与 `leftJoin_`/`rightJoin_`（`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4:184-188`）形态对齐；**不新增/删除任何 token**。文法改动**单独提交**，重生成产物随该提交。（重生成仅 `EqlParser.java`/`Eql.interp` ATN 状态，零 token 增删、`ast/_gen` 零 diff；OUTER/FULL 均关键字 token 无别名歧义。）
- [x] `supportFullJoin` 接线：`IDialect.isSupportFullJoin()`（照 `IDialect.java:89-93` 先例）+ `DialectImpl` 实现（照 `DialectImpl.java:287-297` 的 `Boolean.TRUE.equals(dialectModel.getFeatures().getSupportFullJoin())` 形态）。**零生成**：不改 `dialect.xdef`、不改 retention 类、不改冻结的 `_DialectFeatures`。（实测零生成面成立。）
- [x] 能力门消费（按 Phase 0 裁定的方法名与挂载点落地，默认照 `checkWindowFrameFeature` 先例）：`supportFullJoin=false` 的方言编译含 `full [outer] join` 的查询 → 编译期报错（`ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + `FEATURE_SUPPORT_FULL_JOIN` 常量，断言错误码与 param）；`true` 时正常输出 `full join` 文本（`SqlJoinType.FULL_JOIN.getText()` 经 `AstToEqlGenerator.java:630` 既有路径）。（门方法 `checkJoinTypeFeature(SqlJoinTableSource)` 按裁定定名；**挂载点执行期修订**=分支头部→`visitJoinLeft` 之后、`visitJoinRight` 之前：方言实例要到首个实体解析（`addResolvedEntity`）才确定，头部触发实测 NPE；保留每节点恰过门一次/先于右侧错误/方言与生成同实例三性质，已回写裁定文档 §三。）
- [x] 方言 XML 落位：`mysql.dialect.xml` 显式 `supportFullJoin="false"`（mariadb 按 Phase 0 裁定）；`default.dialect.xml:23` 的 `true` 不动（TDengine/MSSQL 等确认支持者继承它，roadmap WI7b 节证据）。（mysql+mariadb 双显式 false（裁定 §五）；XML 为独立可 revert 提交。）
- [x] 接线验证（Minimum Rules #23）：同一查询在 `supportFullJoin` 两个取值下行为不同（XML 值 → features → accessor → 门行为，一个测试证明运行时连通，非仅 accessor 存在）。（`TestEqlFullJoinDialectGate.testFlagValueReallyChangesCompileBehavior`（手构两值方言同查询分叉）+ `TestDialectFullJoinFlag`（nop-dao 真实 XML→accessor 链逐方言断言）合成完整链。）
- [x] owner doc：`eql-and-database-compatibility.md` 的 FULL JOIN 能力面登记（`supportFullJoin` 不再是零消费死标志；MySQL/MariaDB=false 的证据行标 `[文档]`），并核对 roadmap BUG-12 四处失真清单中 `:111` 行（`supportFullJoin`/`supportRightJoin` "部分数据库不支持"表述）与本 plan 改动的衔接——`:111` 行的完整纠错归 WI3，本 plan 只保证不引入新失真。（特性表两标志行拆分：`supportFullJoin` 已接线+矩阵证据，`supportRightJoin` 显式标注未接线归 WI3；新增 FULL JOIN 小节 + 登记表行。）

Exit Criteria:

- [x] 红→绿回归测试断言具体行为（`full outer join` 可解析；`full join` 输出不变；断言具体 SQL 文本）。
- [x] 负向用例：MySQL（或 mariadb）方言上 `full outer join` 编译期报错，断言具体错误码与 `ARG_FEATURE=supportFullJoin` param；无静默跳过（`false` 时不得静默改写或丢弃 join 类型）。（手构 false 方言断言错误码+feature param；mysql/mariadb XML=false 值由 nop-dao 链测试钉死，两测试合成"真 mysql 配置值→门行为"连通证明。）
- [x] 接线验证测试通过（flag 值真实改变编译行为）。
- [x] `.g4` 提交独立可审计（`git log` 中文法提交不含逻辑改动）；重生成后 `./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。（文法 `feat(nop-orm-eql)` 单独提交；实测两模块 BUILD SUCCESS——nop-orm-eql 212、nop-dao 855。）
- [x] `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（若快照口径覆盖 features 字段，同步 `mysql`/`mariadb` 的新值）。（exit 0；快照 entry 键=extends/functions/loadable/pseudo 无 features 字段，无需同步——执行期核实。）
- [x] 既有 EQL 语料 BC：三份必测与 WI0 基线快照逐字一致。（`TestEqlCorpusBaseline` 随全量运行通过。）
- [x] owner doc FULL JOIN 登记已落档。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - BUG-7 小写 `date '…'` CCE

Status: completed（2026-10-03）
Targets: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`（`SqlDateTimeLiteral_type` `:184-191`）、`nop-orm-eql` 测试

- Item Types: `Fix`、`Proof`

- [x] 修复前红：小写 `date '2020-01-01'` 断言正常解析且 AST `type=DATE`——修复前抛 `ClassCastException`（`EqlASTBuildVisitor.java:188` 的 `(ParserRuleContext) node` 强转对 `Token` 参数），断言红。（红证据实测（git stash 还原修复后以最终测试输入复跑）：7 用例 6 错 1 失败，全部 `ClassCastException: CommonToken cannot be cast to ParserRuleContext` / expected NopEvalException but was CCE。）
- [x] 修复：`SqlDateTimeLiteral_type` 把 token 文本 `toUpperCase(Locale.ROOT)` 后再 `SqlDateTimeType.fromText`；错误分支去掉 `(ParserRuleContext)` 强转，改用 `ParseTreeHelper.loc(Token)` 重载——非法类型名（如 `dat`）响亮抛 `ERR_EQL_INVALID_DATETIME_TYPE`（断言错误码 + `ARG_VALUE` param，不再被 CCE 掩盖）。（落地一致；非法类型名载体=真实 lexer 产物 token（`loc(Token)` 读 `getInputStream()`，合成 token 无流会 NPE）直调访问器。）
- [x] 大小写回归守护：`DATE`/`TIME`/`TIMESTAMP` 大写与 `date`/`time`/`timestamp` 小写解析结果一致（enum 同值）；`Date '…'` 等混合大小写行为经断言明确（词法层本就大小写不敏感，`Alphabet.g4:22-25` fragment 证据）。（`TestEqlDateTimeLiteralCase` 5 解析用例覆盖大小写/混合各形态。）
- [x] 不越界声明：`EqlASTBuildVisitor.java:200`（`SqlIntervalExpr_intervalUnit`）的同款强转**类型正确**（参数本就是 `ParseTree`），不改；interval 单位的既有行为（含 `ERR_EQL_INVALID_INTERVAL_UNIT`）回归不变（WI1 BUG-5 会另行触碰该区域，以符号定位避免冲突）。（未触碰该方法；interval 路径随全量运行通过（TestEqlIntervalLiteralParse 等）。测试文件的 interval 直调守护用例因 `ParseTree` 参数类型不匹配改为依赖既有 WI1 interval 测试覆盖。）
- [x] 修法属纯方法内修复：`No owner-doc update required`（或如 owner doc 有日期字面量表述则同步）。（owner doc 零日期字面量表述，grep 实证。）

Exit Criteria:

- [x] 红→绿回归测试：小写 `date '2020-01-01'` 解析成功且**双断言钉死**——AST 枚举值 `SqlDateTimeType.DATE` **与**回显文本 `DATE '2020-01-01'`；小写 `time '…'`/`timestamp '…'` 同理各断言枚举值（`SqlDateTimeType.TIME`/`TIMESTAMP`）与回显文本。
- [x] 负向用例：真正非法的类型名抛 `ERR_EQL_INVALID_DATETIME_TYPE`（断言错误码，非 `ClassCastException`）；无静默跳过。（+ `ARG_VALUE` param 断言。）
- [x] 大写形态回归不变；interval 单位路径回归不变。
- [x] 既有 EQL 语料 BC：三份必测与 WI0 基线快照逐字一致。（`TestEqlCorpusBaseline` 随全量运行通过。）
- [x] `./mvnw test -pl :nop-orm-eql -am -T 1C` 全绿。（实测 219 tests 0 failures BUILD SUCCESS。）
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - BUG-8 `cast(x as boolean)`（.g4，单独提交）

Status: completed（2026-10-03）
Targets: `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`dataTypeName_` `:377-382`）、`nop-orm-eql` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（如 cast 类型面有文档表述）

- Item Types: `Fix`、`Proof`

- [x] 修复前红：`cast(x as boolean)` 与 `cast(x as BOOLEAN)` 断言可解析——修复前均解析失败（`BOOLEAN` 是独立关键字 token `EqlParser.java:38` `BOOLEAN=125`，不在 `dataTypeName_` 备选也不在 `unreservedWord_`），断言红。（红证据实测：`TestEqlCastBooleanParse` 3 错，`parse-fail offendingToken=boolean/BOOLEAN`；非法类型拒绝守护用例同批绿。）
- [x] 文法补 `BOOLEAN` 备选：`dataTypeName_` 增加 `BOOLEAN`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:377-382`）；文法改动**单独提交**，重生成产物随该提交；不新增/删除任何 token。（重生成仅 `EqlParser.java`/`Eql.interp`，零 token 增删、`ast/_gen` 零 diff。）
- [x] 全链验证：`SqlTypeExpr_name`（`EqlASTBuildVisitor.java:268-274`，`toUpperCase` + `StdSqlType.fromStdName` 校验）放行 `BOOLEAN` → `StdSqlType.BOOLEAN`（`StdSqlType.java:22`）→ `AstToSqlGenerator.visitSqlCastExpr`（`:455-475`）经 `dialect.stdToNativeSqlType`（`DialectImpl.java:189-190`）产出方言映射文本；正向文本断言钉死在 **h2**（已配映射：`h2.dialect.xml:217` `<sqlDataType name="BOOLEAN" stdSqlType="BOOLEAN"/>`）：`cast(x as boolean)` → 生成 **`cast(x as BOOLEAN)`**。（`TestEqlCastBooleanCompile.testH2BooleanCastGeneratesNativeText`：真 h2 方言（`DialectManager` 经 `CoreInitialization` 加载，`TestH2WindowFrameCompileEndToEnd` 先例）断言 `cast(o.SID as BOOLEAN)`。）
- [x] 方言边界声明：无 `BOOLEAN` 映射的方言（如 `default` 继承链未配该 `sqlDataType`）在 `stdToNativeSqlType` 处**响亮报错** `ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/DaoErrors.java:113`；抛出点 `SqlDataTypeMapping.java:89/:112`）——此类方言的 `cast(x as boolean)` 状态是"解析已过、生成期显式报错"，**不属"可生成"缺陷回归**，不需要也不得为它们在本 plan 内补映射或 lowering。（`testNoBooleanMappingDialectFailsLoudlyAtGeneration`：手工最小 dialect（空 dataTypes，`TestEqlTranslationSemantics.buildDialect()` 先例）断言错误码；对照组 `testMinimalDialectStillCastsMappedTypes` 证明失败点确为 BOOLEAN 映射缺失。未用 es/db2（红线）。）
- [x] G-1 落实记录：Oracle 原生 `BOOLEAN` 门 = 23ai（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md` 裁决总表已采纳）；本 plan 不做低于 23ai 的 lowering、不加版本门代码；unverified 边界不涉及本项。（无版本门代码落地，裁定文档 §一总表引用。）
- [x] 非法类型名守护：`cast(x as nosuchtype)` 仍抛 `ERR_EQL_INVALID_SQL_TYPE`（`EqlASTBuildVisitor.java:270-272` 既有路径回归，`ARG_ALLOWED_NAMES` 含 `BOOLEAN` 后不弱化拒绝面）。（`testIllegalTypeStillRejectedLoudly` 红→绿同批。）
- [x] owner doc：`No owner-doc update required`（或如 cast 类型支持清单有文档表述则同步登记 `BOOLEAN`）。（owner doc 仅一处 CAST 文法挂载点旁注、无类型支持清单——grep 实证。）

Exit Criteria:

- [x] 红→绿回归测试：`cast(x as boolean)`/`cast(x as BOOLEAN)` 可解析；h2 上生成 SQL 文本断言钉死为 `cast(x as BOOLEAN)`；无 BOOLEAN 映射的载体断言 `ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED` 响亮报错——可加载方言继承链均解析出 BOOLEAN 映射，负向载体用手工最小 dialect（`TestEqlTranslationSemantics.buildDialect()` 先例、空 dataTypes 映射），**不得**用 `es`（伪方言红线）/`db2`（不可实例化）。
- [x] 非法类型名仍响亮报错（断言 `ERR_EQL_INVALID_SQL_TYPE`）；无静默跳过。
- [x] `.g4` 提交独立可审计；重生成后 `./mvnw test -pl :nop-orm-eql -am -T 1C` 全绿。（`feat(nop-orm-eql)` 文法单独提交 `9a76048670`；实测 227 tests 0 failures BUILD SUCCESS。）
- [x] 既有 EQL 语料 BC：三份必测与 WI0 基线快照逐字一致。（`TestEqlCorpusBaseline` 随全量运行通过。）
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 5 - BUG-15 `SqlTypeExpr.characterSet`/`collate` 显式拒绝防护（守护测试）

Status: completed（2026-10-03）
Targets: 按 Phase 0 裁定（候选 A：`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java:321-330`；候选 B：`EqlTransformVisitor.java` 或生成器）、`OrmEqlErrors.java`（如需新错误码）、`nop-orm-eql` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Feature`（防患性防护——BUG-15 尚不可达、还不是 live defect，故不是 `Fix`）、`Proof`

- [x] 按 Phase 0 裁定落显式拒绝：`SqlTypeExpr` 的 `characterSet`/`collate` 一旦有值即抛错（断言具体错误码），**不允许静默丢弃**（杜绝"未来文法修好后退化成 BUG-1 同型静默丢弃"）。（候选 A 落地：两个手写访问器改为无条件抛 `ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT`（`nop.err.eql.sql-type-clause-not-support`，param `value`）——访问器被调用即分支可达，字段结构性钉死 null。）
- [x] 不可达性守护测试（今天的现状固化）：`cast(a as varchar(10) character set utf8)` 与 `cast(a as varchar(10) collate xx)` 解析失败——固化"`STRING` token 无 lexer 规则（`EqlLexer.java` token 表无 `STRING`，`EqlParser.java:81` 有 `STRING=358`）⇒ 分支不可达"的现状；若未来文法修复使这两条可解析，守护测试失败并强制实现者走显式拒绝路径。（3 例不可达锁定：character set unquoted/quoted + collate unquoted，均断言 antlr 错误码前缀。）
- [x] 拒绝路径测试（**测试形态与 Phase 0 裁定候选绑定**——按裁定落点构造对应缝隙的输入）：候选 A（解析期守卫）= 手构 `CharacterSet_Context`/`CollateClause_Context`（携带 `STRING` token 文本）直调 `SqlTypeExpr_characterSet`/`SqlTypeExpr_collate` 访问器，断言抛具体错误码；候选 B（编译/生成期守卫）= 构造 `SqlTypeExpr` 的 `characterSet`/`collate` 字段非空的 AST 并触发编译/生成路径，断言抛具体错误码。无论哪个候选，都证明"一旦可达即失败"——这是 roadmap 所称"未来文法修好后不得静默丢弃"的守护测试本体。（候选 A 形态落地 3 例：两个手构 context 直调 + 拒绝无条件性（空串文本同样拒绝）。手构 context 需同时注入 `start` token（`loc()` 读取）与字段 token。**执行期事实核实**：quoted `collate 'xx'`（STRING_ 可词法化）今天即可解析、字段被写入后静默丢弃——活的最小静默丢弃通路，修复前红="nothing was thrown"+echo 无痕迹，修复后同输入响亮拒绝。）
- [x] 无静默跳过：拒绝逻辑不得写成吞掉字段继续编译（那正是本防护要杜绝的形态）。（无条件抛错、不依赖字段值，`testGuardIsUnconditionalNotValueDependent` 锁定。）
- [x] owner doc：`eql-and-database-compatibility.md` 登记 `SqlTypeExpr.characterSet`/`collate` 的"显式拒绝 + 不可达原因（token 分叉证据）"小节，与 WI1 的 ESCAPE 矩阵章节衔接（不重复矩阵内容）。（新增「SqlTypeExpr.characterSet/collate：显式拒绝防护」小节（token 分叉证据 + quoted collate 可达面 + 与 ESCAPE 治理面衔接）+ 敏感输出点登记表新行。）

Exit Criteria:

- [x] 显式拒绝落点与 Phase 0 裁定一致，拒绝路径测试断言具体错误码。
- [x] 不可达性守护测试存在（两条解析失败输入）；文法若被第三方修复，守护测试能转红。（3 条输入；另以 quoted collate 的真实可达通路验证了"可达即拒绝"行为本体。）
- [x] 三份必测 BC 与 WI0 基线快照逐字一致。（`TestEqlCorpusBaseline` 随全量运行通过，语料零 characterSet/collate。）
- [x] `./mvnw test -pl :nop-orm-eql -am -T 1C` 全绿。（实测 234 tests 0 failures BUILD SUCCESS。）
- [x] owner doc 小节已落档。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [x] 5 个 in-scope B 类缺陷（BUG-4/6/7/8/15）全部收口，各有一个修复前红、修复后绿且断言具体 SQL 文本（或具体错误码）的回归测试；BUG-15 另有"未来文法修好后不得静默丢弃"守护测试。（红证据：BUG-4=`TestEqlSelectAllLimitEcho` 6红1错（`select from t`/limit 丢失/布尔回显 1）；BUG-6=`TestEqlFullJoinParse` 2错（`expected=JOIN offendingToken=outer`）；BUG-7=`TestEqlDateTimeLiteralCase` 6错1败（全为 CCE，git stash 复跑取证）；BUG-8=`TestEqlCastBooleanParse` 3错（`offendingToken=boolean`）；BUG-15=`TestEqlSqlTypeClauseGuard` 4败（quoted collate 静默丢弃"nothing thrown"+访问器 3 例）。）
- [x] BUG-6 的负向用例存在：`full outer join` 在 MySQL/MariaDB 上编译期报错（断言错误码）；`supportFullJoin` 接线验证（flag 两值行为不同）通过。（`TestEqlFullJoinDialectGate`（手构 false 方言错误码+feature param；`testFlagValueReallyChangesCompileBehavior`）+ `TestDialectFullJoinFlag`（真 mysql/mariadb XML=false accessor 断言）合成完整链。）
- [x] **既有 EQL 语料 BC**：`:nop-job-dao`(NopJobTask)、`:nop-auth-service`(test)、`:nop-orm`(test) 三份必测与 WI0 基线快照逐字一致；未声称"7 个全测"（`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份由 roadmap 级 EC 第 8 条 + WI0 静态快照覆盖）。（`TestEqlCorpusBaseline` 在每个 Phase 的全量运行中通过，快照零 diff、未重生成——Phase 0 裁定的免重生成前提成立。）
- [x] `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿——**"全绿"口径**：本 plan 相关模块全绿 + `nop-stream-runtime` 的 1 项既有失败（`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`，与基线一致即豁免，不据此判本 plan 失败；来源 `ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 头部第 2 条红线事实）。（⚠️ 执行期实测两段：① `-T 1C` 下 reactor 在 `nop-batch` 因 `TestBatchTaskCheckpoint.testFailFastStopsSiblingThreads` 并发时序 flake 中断（expected IllegalStateException was BatchCancelException，与本 plan 零代码关联；单独复跑 8/8 PASS 在 surefire 报告）；② 改串行复跑全量闭包：**唯一失败 = 恰为豁免清单内的 `testCapabilityMatrixAlignedWithDesignAdjudication`，症状逐字一致**（`debezium-cdc ==> expected: <SINGLE_INSTANCE> but was: <PARALLEL>`），reactor 因其终止致 `:nop-auth-service` SKIPPED；③ 按任务预案以 `-Dtest='!TestStreamConnectorRegistryDiscovery' -Dsurefire.failIfNoSpecifiedTests=false` 补跑：**BUILD SUCCESS**，五个目标模块（nop-orm-eql/nop-orm/nop-metadata-service/nop-job-dao/nop-auth-service）全 SUCCESS，`TestEqlCorpusBaseline` 1/1（快照零 diff）。判定=与豁免基线一致，不判失败。）
- [x] `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（方言 XML 值变更已同步快照口径）。（exit 0；快照 entry 键=extends/functions/loadable/pseudo 无 features 字段，值变更不触口径——执行期核实，无需同步。）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本 plan 新增 broken link = 0（3 项既有 warning 基线不动：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，见 roadmap EC 第 6 条口径）。（每个 Phase 提交前实测 exit 0 / No errors found。）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/19-eql-wi2-class-b-defects.md --strict` 退出码 0。（执行者实测 exit 0（Plan Status=active 下通过）；**留白待独立审计终门复跑**——执行者不得勾。）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中（anti-hollow 对本 roadmap 尤其关键——"只打印不设门"正是它要抓的半实现形态）。（2026-10-03 实测 exit 0 / 0 命中，含 `printLimit` 置空 override 与 BUG-15 无条件抛错守卫的审查通过。）
- [x] 两处 `.g4` 改动各自单独提交可审计（生成物变更纪律）；无 lexer token 删除。（BUG-6 文法=`a3de1c9564`、BUG-8 文法=`9a76048670`——两个文法提交均只含 `.g4`+生成物（EqlParser.java/Eql.interp），`git show --stat` 可核；两次重生成 `ast/_gen` 零 diff、token 表零增删。）
- [x] owner doc（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`）已同步：FULL JOIN 能力面与 `supportFullJoin` 登记、BUG-15 拒绝行为小节；Phase 0 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`。（另含 WI2 BUG-4「EQL 回显路径契约」小节与敏感输出点登记表 3 个新行（FULL JOIN/CAST 子句）；裁定文档=`2026-10-03-wi2-class-b-adjudications.md`。）
- [ ] Anti-Hollow Check：closure audit 已验证（a）`supportFullJoin` 从 `mysql.dialect.xml` 配置值到编译期门行为的运行时调用链连通（不只是 accessor/字段存在），（b）BUG-4 从 EQL 文本到回显文本的端到端路径有测试，（c）无空方法体/静默跳过/no-op 作为"已修复"（BUG-15 拒绝路径尤其不得是吞字段）。（执行者侧自证材料：(a)=`TestDialectFullJoinFlag`（XML→accessor）+`TestEqlFullJoinDialectGate`（flag 值分叉门行为）；(b)=`TestEqlSelectAllLimitEcho` round-trip+`TestEqlSelectAllLimitCompile`；(c)=BUG-15 无条件抛错+`testGuardIsUnconditionalNotValueDependent`、hollow scan 0 命中。**留白待独立审计复核**——执行者不得勾。）
- [ ] 独立 closure audit 已完成并由**不同 task_id** 的子 agent 执行，evidence 写入本 plan `Closure` 段（guide #26/#27）；roadmap Work Item Status 的 WI2 行据此才可 `todo` →（执行中）→ `done`。（**留白给主会话+独立审计**——执行者不得自行翻转 roadmap 状态。）

## Deferred But Adjudicated

### `SqlTypeExpr.characterSet`/`collate` 的真正生成语义

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 这两个字段当前**不可达**（`STRING` token 无 lexer 规则，机器可证），不构成 live defect；roadmap 未授权任何 WI 实现其生成语义，本 plan 的显式拒绝防护 + 守护测试已把"未来可达时的静默丢弃"风险收口。
- Successor Required: `no`（如未来立项实现，须先修文法并按生成管线 plan-first 走新 plan）
- Successor Path: 无既有 plan；触发条件 = 有人提出 `CAST … CHARACTER SET`/`COLLATE` 的产品需求并修复文法。

## Non-Blocking Follow-ups

- **ESCAPE 回显未随 BUG-4 承接**：WI1 plan 曾把 `LIKE … ESCAPE` 的回显缺失（基类 `AstToEqlGenerator.visitSqlLikeExpr` 不打印 escape）预期为"随 WI2 BUG-4 回显治理批次"，但本 plan 起草与 draft review 收口的 BUG-4 范围为 selectAll/LIMIT/字面量三项、EC 不含 ESCAPE 回显。回显 escape 属新增回显形态变更，待 roadmap 立项授权；owner doc ESCAPE 小节已同步实际落地面（watch-only）。
- **EQL parse 层 `''` 双写不 un-double 的既有缺口**：lexer `STRING_` 规则（`Literals.g4`）接受 `'a''b'` 为单 token，但 `EqlParseHelper.stringLiteralValue` 只 unescape 反斜杠形态 ⇒ 含引号值的回显文本（`''` 双写形）重解析后值含字面 `''`。属 parse 语义缺口非回显缺陷（回显文本与执行路径 `escapeSlash=false` 渲染逐字一致），修复会改变既有 parse 语义，需另行立项（owner=`parse 层`，非本 roadmap 缺陷清单条目）。
- `EqlASTBuildVisitor.java:200`（`SqlIntervalExpr_intervalUnit`）的同款 `(ParserRuleContext)` 强转类型正确、非缺陷，不动；WI1 BUG-5 触碰该区域时如顺手统一错误定位形态，须以符号定位并独立说明。
- roadmap Cross-Cutting"生成物变更纪律"的 CI `git diff --stat` 阈值告警 carrier 待定（WI0 Non-Blocking Follow-up）；本 plan 以"两处 `.g4` 各自单独提交 + `git log` 可审计"作为过程替代。
- WI1 plan Closure Gates 中的"3 项既有失败"表述滞后于实测 1 项基线（差异登记第 4 条）；WI1 收口时按其自身流程更正，本 plan 不代改。

## Closure

Status Note: （closure audit 通过后填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:
  - （每条 Exit Criterion 的验证结果 PASS/FAIL + 对应 live code path 或 test name）
  - （每条 Closure Gate 的验证结果 PASS/FAIL + evidence 来源）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/19-eql-wi2-class-b-defects.md --strict` 退出码
  - Anti-Hollow 检查结果：`scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 退出码 + 端到端调用链追踪结论
  - Deferred 项分类检查：确认无 in-scope live defect 被降级（BUG-15 的"尚不可达"以 token 分叉证据为准，不属 live defect 降级）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
