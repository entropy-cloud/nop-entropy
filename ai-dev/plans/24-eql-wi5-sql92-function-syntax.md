# 24 EQL WI5 — SQL-92 标准函数语法（POSITION/SUBSTRING/EXTRACT/TRIM）+ 关键字遮蔽放开

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI5 节 + 约束 1-4 + 前置决策门末段 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 溯源结论 + 门控矩阵 WI5 行 + Cross-Cutting 全部 + 关键约束与红线节，v7.2）；Current Baseline 全部代码事实于 2026-10-03 在本工作区分支 `feat/eql-cross-db-compat` 逐文件实测核验
> Related: `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`（硬前置，已 `completed`）、`ai-dev/plans/03-eql-wi1-class-a-defects.md`（硬前置，已 `completed`；文法分支+构建期归一化的结构先例）、`ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md`（**硬前置，Status: active，实施排队中——WI4 completed 前不得开工本 plan 的任何代码 Phase，见下方硬前置声明**）

## 硬前置声明（开工门，非空话）

roadmap 门控矩阵 WI5 行明文硬前置 = **WI1、WI4**。实测现状：WI1 已 `completed`（文法 commit `5afea1d3f3` + 逻辑 commit `6b229a2224`）；WI4 的 `ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md` 为 **Status: active、实施排队中**。⇒ **本 plan 的 Phase 0（纯裁定、不改产品代码）可立即开工；Phase 1 及之后的所有代码 Phase 在 plan 21 的 `Plan Status` 变为 `completed` 之前不得开工**（参照 WI10b 对 WI10a 定稿的排队先例：`ai-dev/plans/23-eql-wi10b-in-list-gate-and-dead-flags.md` Related 行）。每次开工一个代码 Phase 前，执行者必须先复核 plan 21 状态并记录于当次 daily log。

## Purpose

收口 roadmap WI5：让 SQL-92 标准函数语法 `POSITION(x IN y)` / `SUBSTRING(x FROM a [FOR b])` / `EXTRACT(f FROM d)` / `TRIM` 三段式在 EQL 中可解析、按方言矩阵无损 lowering 到各库函数（`instr` 换序 / `substr` 多态 / `year()` 等 / 三段式拆解），并放开 `day`/`hour`/`minute`/`second`/`quarter` 的关键字遮蔽（今天 `month()` 能解析而 `day()` 不能，纯属遮蔽不一致）。每个函数具备：断言正确方言 SQL 文本的正向用例、未注册方言编译期响亮报错的负向用例、方言能力矩阵快照增量、owner doc 覆盖表、`TestDialect.testFunctions` 真库（H2）执行证据。被注释的 4 条 SQL-92 函数规则（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` `:295-314`）经 WI0 溯源**从未在本仓库生效过**（`git log -S "positionFunction" --all` 仅命中首次引入即注释态的 `aa1b310122`）——本 plan 按**新语法设计**复活，不当作"EQL 曾正确实现过"的先例，也不需要"避免重蹈覆辙"的论证。

## Current Baseline

> 所有锚点为**符号定位优先**；file:line 为 2026-10-03 实测参考值。⚠️ **WI2（`ai-dev/plans/19-eql-wi2-class-b-defects.md`）正在并行实施、其文法改动会整体平移 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 行号与 `EqlParser.java` 行数**——执行时以 Phase 0 的锚点复核为准。

### WI0/WI1 已落地的基础设施（本 plan 直接消费）

- **方言能力矩阵棘轮**：测试 `nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialectCapabilityMatrix.java`（函数表断言 `testModelFunctionsMatchRuntimeAccessors` `:133`，快照生成 `:227-229` 把每方言函数名排序落 JSON）；快照 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（18 个方言全登记，`es` 标 `pseudo`，每方言 `functions` 为**生效函数名的排序列表**——已含继承展开）；门禁脚本 `ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs`（新增方言/函数不同步快照即转红）。
- **既有 EQL 语料基线棘轮**：快照 `ai-dev/audits/eql-wi0/eql-corpus-baseline.md`（生成/校验测试 `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlCorpusBaseline.java`，覆盖 7 个含 `<eql>` 的 `sql-lib.xml` 的回显文本，逐字节比对）；本 plan 的 BC 判据以该快照为基准。
- **四门裁决**：`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`——G-1 采纳承诺基线（Oracle 23ai / MySQL 8.0.31 / MariaDB 10.2 / PG 15 / MSSQL 2022 / SQLite 3.39），**全部 unverified 边界维持 unverified 纪律、不计入覆盖率**。G-1 与 WI5 无直接门控关系（门控矩阵 WI5 行硬前置只有 WI1、WI4）。
- **WI1 先例（文法分支+构建期归一化；机制有差异，**非直接同构**）**：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlIntervalExpr` 已有 `INTERVAL STRING_` 备选分支（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:351`，altLabel `# SqlIntervalExpr_string`），手写 `EqlASTBuildVisitor` 在 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java:223-224`（`visitSqlIntervalExpr_string`）做构建期归一化，装入既有 `SqlIntervalExpr`、**不新增 AST 节点、`ast/_gen/` 零 diff**。
- **altLabel 机制的确切语义（起草后独立审查 Major-3 补登，执行前必读）**：altLabel → AST 类名的推导规则是 `AstGrammarBuilder.getAstNodeName`（`nop-kernel/nop-antlr4/nop-antlr4-tool/src/main/java/io/nop/antlr4/tool/loader/AstGrammarBuilder.java:259-262`：取 label 首段下划线前缀并首字母大写）——`SqlIntervalExpr_string` → `SqlIntervalExpr`。而 `sqlExpr_special` 现无任何 label，推导出 `SqlExpr`（**不存在的类**），现状靠生成方法直接返回内层节点兜底（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/_EqlASTBuildVisitor.java:769-772` 的透明包装：`visitSqlExpr_special` 只是把 `sqlCastExpr` 子节点的结果原样返回）。⇒ 本 plan 向 `sqlExpr_special` 新增备选必须满足**三条约束**：
  1. 新备选的 label 必须以目标 AST 类名为前缀（如 `SqlRegularFunction_position`、`SqlRegularFunction_substring`）——否则推导出错误类名。
  2. 备选内**不写 prop 赋值**（生成体只会建空节点 + `validate()` 必填校验必失败），归一化逻辑全部放手写 visit 方法**完全重写、不调 super**（WI1 实际做法：`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java:212-221` javadoc 明言"生成方法体只建空节点 + validate()（必填校验必失败），因此完全重写、不调 super"）。
  3. 既有 `sqlCastExpr` 备选需同步补 label（如 `# SqlCastExpr_cast`）——一旦同规则内出现有 label 备选，无 label 备选的 `SqlExpr` 推导即与有 label 备选冲突，必须全部显式 label 化。
  WI1 改的是本就有两个备选的 `sqlIntervalExpr`，本 plan 改的是现只有单备选（透明包装）的 `sqlExpr_special`——**是结构先例，不是可直接照抄的同构**。
- **WI0 交付 2（`TemplateSQLFunction` marker 加载期校验）已落地**：`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/function/TemplateSQLFunction.java:56-62`（越界 marker 方言加载期抛 `ERR_DAO_FUNC_TEMPLATE_MARKER_OUT_OF_RANGE`）+ `:113`（元数不符抛 `ERR_DAO_FUNC_INVALID_ARG_COUNT`）——WI5 新增的任何 `<template>` 自动受这道闸保护。

### 关键字遮蔽的确切机制（2026-10-03 实测定论）

- 词法层：`DAY`/`HOUR`/`MINUTE`/`SECOND`/`QUARTER`/`MONTH`/`YEAR`/`WEEK` 全部是 `nop-persistence/nop-orm-eql/model/antlr/Keyword.g4` 的保留 token（`YEAR:397`、`QUARTER:401`、`MONTH:405`、`WEEK:409`、`DAY:413`、`HOUR:417`、`MINUTE:421`、`SECOND:425`）。词法器优先匹配具名关键字 token，它们**不会**落回 `IDENTIFIER_`。
- 语法层：函数名规则 `sqlIdentifier_func_`（`BaseRule.g4:320-323`）= `sqlIdentifier_ | IF | CURRENT_TIMESTAMP | LOCALTIME | LOCALTIMESTAMP | INTERVAL | EXTRACT | NULLIF | TRIM | SUBSTRING`；`sqlIdentifier_`（`:69-70`）= `IDENTIFIER_ | unreservedWord_`；`unreservedWord_`（`:72-96`）**含 `YEAR|MONTH`（`:89`）与 `POSITION`（`:88`），不含 `DAY/HOUR/MINUTE/SECOND/QUARTER`**。
- ⇒ **遮蔽不一致的确切原因**：`month(x)`/`year(x)`/`position(x,y)` 走 `unreservedWord_` → `sqlIdentifier_func_` → `sqlRegularFunction`（`:316-318`）解析通过；而 `day(x)`/`hour(x)`/`minute(x)`/`second(x)`/`quarter(x)` 的名字 token 既不是 `IDENTIFIER_` 也不在 `unreservedWord_`/`sqlIdentifier_func_` 显式清单里，**解析层即失败**。同理：`substring(...)`/`trim(...)`/`extract(...)` 因被显式加入 `sqlIdentifier_func_`（`:321-322`）逗号形式可解析，但 `FROM` 三段式没有文法分支；`convert(...)`（`CONVERT` 在 `SQL92Keyword.g4:302`）连函数名位置都进不去。
- ⚠️ **"month() 能解析"只是 parse 层**：transform 层对函数名做方言函数表解析（见下），`month` 在 14 个可加载方言中 **0 注册** ⇒ `month(x)` 今天解析通过但编译报 `ERR_EQL_UNKNOWN_FUNCTION`。放开遮蔽后 `day(x)` 等将从"解析失败"变为"编译期 unknown-function 响亮报错"（同为响亮，无静默回归）；`day()`/`hour()` 等的**归一化函数注册属 WI6**（roadmap WI6 明文"依赖 WI5 的遮蔽放开"）。

### 函数解析与模板机制（已接线，本 plan 复用）

- **transform 层解析**：`EqlTransformVisitor.visitSqlRegularFunction`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:1454-1481`）——`dialect.getFunction(name)` 为 null 抛 **`ERR_EQL_UNKNOWN_FUNCTION`**（`:1456-1458`，`.param` 带 ARG_FUNC_NAME/ARG_DIALECT）；窗口限定函数校验 `:1460-1465`；元数校验 `:1467-1476`（`ERR_EQL_FUNC_TOO_FEW_ARGS`/`ERR_EQL_FUNC_TOO_MANY_ARGS`）；通过后 `setResolvedFunction(fn)`（`:1477`）。**"该方言未注册此函数时编译期报错"（roadmap WI5 EC ②）的机制今天已存在**——本 plan 的工作是锁定它作为负向回归，不是新造报错路径。
- **生成层消费**：`AstToSqlGenerator.visitSqlRegularFunction`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java:499-501`；起草期首轮实测曾为 `:487-491`，WI1 提交平移所致——以符号定位为准）→ `func.buildFunctionExpr(location, args, dialect)` → 打印。函数模板先例：`oracle.dialect.xml:174` 的 `<template name="ilike" ...>`；native realName 换名先例：`duckdb.dialect.xml:87`（`instr`→`strpos`）、`postgresql.dialect.xml:158`（`instr`→`strpos`）、`mssql.dialect.xml:198`（`substr`→`substring`）。
- **`<native>` 2/3 元多态先例**（roadmap 点名的 SUBSTRING 走法）：`default.dialect.xml:196-196` 的 `<native name="substr" ... argTypes="VARCHAR,INTEGER,INTEGER" minArgCount="2" maxArgCount="3"/>`（同款：`lpad`/`rpad` `:169` 附近、`replace` `:180` 附近，均 `minArgCount="2" maxArgCount="3"`）——`SUBSTRING(x FROM a)` 与 `SUBSTRING(x FROM a FOR b)` 归一化为 2/3 参 `substr` 调用即可复用该多态。
- **函数名大小写**：手写 `EqlASTBuildVisitor.java:407-409`（`SqlRegularFunction_name`）把函数名 `toLowerCase(Locale.ROOT)`，与方言 XML 的小写注册名匹配。
- **回显路径**：`AstToEqlGenerator.visitSqlRegularFunction`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java:907` 起；起草期首轮实测曾为 `:878-883`，同因 WI1 提交平移）打印 `name(args)` 原样——特殊语法若在构建期归一化为 `SqlRegularFunction`，回显输出的是归一化后的形态，**这会进入 WI0 语料基线的逐字节比对**；本 plan 7 个语料文件均不含目标函数（见下），故无 BC 风险，但新语法自身必须落"输入→归一化回显→方言 lowering"三态断言。
- **聚合绕过（约束 2，本 plan 不触碰）**：`sqlIdentifier_agg_`（`BaseRule.g4:273-275`）硬编码 `MAX|MIN|SUM|COUNT|AVG`，`EqlTransformVisitor` 无 `visitSqlAggregateFunction` override——聚合归一化不属本 plan。

### 方言函数矩阵摘要（取自 WI0 快照 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json` 的 per-dialect `functions` 表，2026-10-03 实测）

14 个可加载方言（dm/duckdb/es/h2/h2gis/mariadb/mssql/mysql/mysql5.7/oracle/oracle-reverse/postgis/postgresql/tdengine；`db2`/`default`/两个 mixin 不可加载、无函数表）：

| 目标函数 | 已注册 | 缺口 |
|---|---|---|
| `instr`（POSITION 的 lowering 目标） | **14/14**（mssql 为 template `mssql.dialect.xml:174`；duckdb/pg 为 native `strpos`） | 无 |
| `substr`（SUBSTRING 的 lowering 目标） | **14/14**（mssql 为 native realName `substring`） | 无 |
| `year`（EXTRACT 的 year 字段目标） | **14/14** | 无 |
| `month`/`day`/`hour`/`minute`/`second`/`quarter`（EXTRACT 其余字段候选目标） | **0/14** | 全体方言——EXTRACT 非 year 字段的 lowering 必须先补注册或换目标，Phase 0 逐字段裁定 |
| `trim`/`ltrim`/`rtrim`（TRIM **无 chars 形态**的目标；均单参 `default.dialect.xml:110-112`，`argTypes="VARCHAR"`） | **14/14** | 无（但字符集语义随方言分叉，见约束 4 用例；**带 chars 形态不在此行覆盖范围内**，见下） |
| `extract`（**逗号形态**函数——本 plan 的 EXTRACT 默认形态已钉死为逐字段函数，此行仅作 Phase 0 对比候选） | **13/14**（mssql 缺） | 逗号形态 `extract(x,y)` 今天在 13 个方言上已走函数表；mssql 缺口属既有现状，本 plan 不改（逐字段形态的 `year` 在 mssql 已注册、可用） |
| `position`/`substring` 作为注册名 | 0/14（mssql 注册名是 `substr`、真名 `substring`） | `position(x,y)` 逗号形态今天解析通过但必报 `ERR_EQL_UNKNOWN_FUNCTION` |

⚠️ `instr`/`substr`/`year` 已 14/14 ⇒ **POSITION/SUBSTRING 的 lowering 预期零新增注册**（矩阵快照预期零增量）。**TRIM 的带 chars 形态走逐方言 `<template>` ⇒ 模板注册进方言函数表，属真实矩阵增量（EC ③ 预期有增量）**——`trim`/`ltrim`/`rtrim` 虽 14/14 却均为单参（`default.dialect.xml:110-112`），只覆盖无 chars 形态，**不得据此误判 TRIM 三段式零增量**（起草稿口径已修正）。EXTRACT 非 year 字段（若裁定补注册）同样触发真增量。`dm`/`tdengine` 的实际函数行为无可引用一手文档，按红线其矩阵格一律 `unverified`、不计入覆盖率。

### 真库执行证据面（roadmap WI5 EC ⑥）

- `TestDialect.testFunctions`（`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialect.java:189`）遍历**当前数据源方言**的 `dialectModel.getFunctions()`，对每个函数构造测试 SQL（有 `<testSql>` 用 `testSql`）经 `getSelectFromDualSql` 包装后**真库执行**（`:190` 起）。
- 测试基座 `JdbcTestCase`（`nop-persistence/nop-dao/src/test/java/io/nop/dao/jdbc/JdbcTestCase.java:112-113`）硬编码 **H2 内存库**（`org.h2.Driver` / `jdbc:h2:mem:`）。⇒ **CI 真库执行证据只有 H2 一家**；其余方言的函数无法在 CI 真库执行。本 plan 要求：新增注册的函数在 H2 上过 `testFunctions`；无法真库验证的方言**逐个列出**并给替代证据（一手文档引证 + 文本断言），`dm`/`tdengine` 按红线标 `[未取证]`，不得计入覆盖率。
- 参考先例：`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestSQLFunction.java`（方言函数的真库执行测试）。

### 生成管线事实（决定 Protected Area 与提交纪律）

- `nop-orm-eql` 侧生成管线是活的：`nop-persistence/nop-orm-eql/precompile/gen-eql-parser.xgen` 驱动 `.g4` → 重生成 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java`（**实测现 10904 行**——roadmap 的 10785 已因 WI1 文法提交漂移）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/_EqlASTBuildVisitor.java`（**实测现 1819 行**）、`EqlLexer*`/`Eql.interp`/`*.tokens`；重生成命令 `./mvnw generate-sources -pl :nop-orm-eql -am`。`ast/_gen/` 现 **77 个文件**（与 roadmap 一致）。
- **不新增 AST 节点 ⇒ `ast/_gen/` 零 diff** 是本 plan 的默认形态（路径 ⓐ/ⓑ，roadmap 约束 1 的最便宜两档）；`EqlASTKind` ordinal 重编号是 roadmap 红线，**禁止触 `EqlAST.xjava` 与 `ast/_gen/`**。
- **AstGrammarBuilder 两条已知约束**（WI1 Phase 4 执行期记录；工具源码出处为本 plan 补登——plan 03 只记录了 rule-alternative-is-not-ast-node 一码）：多备选规则的每个备选必须解析为 AST 节点（`nop.err.antlr4.grammar.rule-alternative-is-not-ast-node`）；同一 prop 不能出现在同一规则的不同备选（`nop.err.antlr4.grammar.duplicate-prop-label`，定义于 `nop-kernel/nop-antlr4/nop-antlr4-tool/src/main/java/io/nop/antlr4/tool/AntlrToolErrors.java:42-43`、抛出点 `nop-kernel/nop-antlr4/nop-antlr4-tool/src/main/java/io/nop/antlr4/tool/loader/AstGrammarBuilder.java:524`）。⇒ 特殊语法分支需带 altLabel（`sqlTableSource`/`sqlIntervalExpr` 同款先例），且遵守上方"altLabel 机制的确切语义"小节的三条约束。
- **不删除任何 lexer token**（删除会重编号全部 token 类型，roadmap 红线）。向 `unreservedWord_` 加词是 parser 规则改动，不动词法器。

### 既有 EQL 语料相容性（遮蔽放开的 BC 论证基础，2026-10-03 实测）

对 7 个含 `<eql>` 的 `sql-lib.xml` 全量 grep：`day`/`hour`/`minute`/`second`/`quarter`（含 `(` 调用形态与裸词形态）以及 `substring`/`position`/`extract`/`trim`/`convert` **0 命中**——三份必测（`nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/sql/NopJobTask.sql-lib.xml`、`nop-auth/nop-auth-service/src/test/resources/_vfs/nop/auth/sql/test.sql-lib.xml`、`nop-persistence/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml`）与 `nop-demo/nop-orm-demo` 的 chapter3/4/5、`nop-spring-simple-demo` 的 demo.sql-lib.xml 均不使用这些词。⇒ 遮蔽放开对既有语料的**可解析集合是纯增量**（今天因 DAY 等是保留 token 而失败的输入本来就不存在），BC 判据（棘轮快照逐字一致）风险面极小；残余风险在 ANTLR 歧义（`interval 1 day` 的 `DAY` 消费冲突），由既有 `TestEqlIntervalLiteralParse` 回归锁定。

### 全绿口径与 stream 豁免

含 `:nop-auth-service` 的命令会拖入 `nop-stream-runtime`，其 **2 项 `-T 1C` 时序 flake 按基线豁免**（均单跑 PASS，WI0/收口轮实测）：`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`（WI0 既档，`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 文档头）+ `TestMessageSourceFunctionRestart`（WI1 收口轮实测，`ai-dev/plans/03-eql-wi1-class-a-defects.md` Closure 段）。⇒ **"全绿" = 本 WI 相关模块全绿 + 这 2 项与基线一致**，不得据此判本 WI 失败。

## Goals

- 四个 SQL-92 标准函数语法落地：`POSITION(x IN y)` → `instr(y,x)`（调用点换序）；`SUBSTRING(x FROM a [FOR b])` → `substr(x,a[,b])`（复用 `<native>` 2/3 元多态）；`EXTRACT(f FROM d)` → **逐字段函数（默认形态已钉死）**：`EXTRACT(YEAR FROM d)` → `year(d)`（14/14 就绪），其余字段 Phase 0 逐个裁定；`TRIM` 标准三段式（**无 chars 形态**走既有单参 `trim`/`ltrim`/`rtrim`；**带 chars 形态**走逐方言 `<template>`，chars 参数不得静默丢弃）。每个函数有断言**正确方言 SQL 文本**的正向用例（红→绿）。
- 每个函数在"方言未注册目标函数"时有编译期响亮报错的负向回归（复用既有 `ERR_EQL_UNKNOWN_FUNCTION` / 元数错误码机制，不新起新码，除非 Phase 0 裁定必须新码）。
- 放开 `day`/`hour`/`minute`/`second`/`quarter` 关键字遮蔽：这五个词在函数名位置与标识符位置（列名/别名）可解析；`month`/`year` 行为不变；`interval 1 day` 等 interval 既有形态逐字不变。
- 方言能力矩阵快照增量同步（EC ③）、owner doc `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 的"各库覆盖情况"表同步（EC ④，逐行标 `[实测]`/`[文档]`/`[未取证]`）。
- 新注册函数在 `TestDialect.testFunctions` 真库（H2）执行路径全绿（EC ⑥）；无法真库验证的方言逐个列出 + 替代证据。
- 既有 EQL 语料 BC：三份必测与 WI0 基线快照逐字一致（EC ⑦）；约束 4 的"一致性 + 不等价"两类用例齐备。
- 独立 closure audit（不同 task_id，evidence 写入本 plan Closure 段）（EC ⑧）。

## Non-Goals

- **不注册 `day()`/`hour()`/`minute()`/`second()`/`quarter()`/`month()` 的归一化日期函数族**——那是 WI6 的交付面；本 plan 的遮蔽放开只到"可解析 + 未注册时响亮报错"。唯一的例外是 EXTRACT lowering **必需**的字段目标函数（Phase 0 裁定清单内），按"EXTRACT 的组成部分"落地。
- 不复活 `convertFunction`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 第 5 个注释块 `:295-297`）——roadmap WI5 清单只有 4 个 SQL-92 函数，`CONVERT` 不在其中（`Deferred But Adjudicated` 登记）。
- 不走路径 ⓒ（新增 AST 节点）——默认全部复用既有 `SqlRegularFunction` + 构建期归一化（`ast/_gen/` 零 diff）；若 Phase 0 裁定某函数必须新节点，须按 roadmap 约束 1 路径 ⓒ 定价（重生成 `EqlParser.java` 10904 行 + `_EqlASTBuildVisitor.java` 1819 行 + 77 个 `_gen`）并回滚本条 Non-Goal（显式 scope change）。
- 不动聚合函数路径（约束 2）与 `sqlIdentifier_agg_`。
- 不做 `greatest`/`least`/`stringAgg`/`regexpLike`/`count(*) FILTER` 等 ledger 项；不碰 WI3 的方言配置纠错。
- 不把 `:nop-orm-demo` / `nop-spring-simple-demo` / `:nop-orm-tdengine` / `:nop-db-migration` 拉进本 plan 的 `-pl`（roadmap 级 EC 第 8 条 + WI0 静态快照兜底）。
- 不做 WI6 的 `dayofweek`/`weekofyear`/`dateAdd`/`dateDiff`/`dateTrunc`。
- `WEEK` 不加入遮蔽放开清单（roadmap WI5 只点名 day/hour/minute/second/quarter 五个；`WEEK` 的需求登记为观察项）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql`：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`sqlExpr_special` 备选分支恢复设计 / `unreservedWord_` 加词）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`（特殊语法的构建期归一化）、必要时 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（若 lowering 不能完全由函数表承载）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/` 与 `_EqlASTBuildVisitor.java`（重生成产物）、对应测试（`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/`、`.../compile/`）。
- `nop-persistence/nop-dao`：`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`（EXTRACT 非 year 字段的函数注册与 **TRIM 带 chars 形态的逐方言 `<template>` 注册**，均按 Phase 0 裁定落地；已有函数的 `<testSql>` 补齐）、`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialectCapabilityMatrix.java` 快照重生成、`TestDialect.java`/`TestSQLFunction.java` 证据面。
- `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（EC ③ 增量）。
- 文档：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（POSITION/SUBSTRING/EXTRACT/TRIM 各库覆盖情况表 + 遮蔽放开说明 + 能力点登记表行）；`ai-dev/design/eql-cross-db-compat/`（Phase 0 裁定文档）；`ai-dev/logs/`。

### Out Of Scope

- Non-Goals 所列全部；以及任何 roadmap 未登记的方言语法扩展（发现新缺陷/新需求 → 登记 roadmap，不顺手修）。

## 与 roadmap 的差异登记（起草时实测发现）

1. **行号漂移（非实质偏离）**：roadmap 门控矩阵 WI5 行的 `EqlParser.java` 10785 行 → 实测 **10904 行**；`_EqlASTBuildVisitor.java` 1804 → **1819**（WI1 文法提交所致）。`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的注释块行号（`sqlExpr_special` `:286-289`、convert/position/substring/extract/trim `:295-314`）实测**与 roadmap v5 修订后的行号一致**，未漂移。
2. **`DialectImpl.getFunction` 行号**：roadmap 写 `DialectImpl.java:133 → getFunction()（:626）`；实测 `getFunction` 现位于 `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java:637-639`（`initFunctions` `:128` 起、函数名即 XML 注册名原样入 map）。
3. **EC ② 的机制今天已存在**：roadmap WI5 EC ② 要求"该方言未注册此函数时编译期报错"的负向用例——该行为由 `EqlTransformVisitor.java:1456-1458` 的既有 `ERR_EQL_UNKNOWN_FUNCTION` 提供，非本 plan 新建。本 plan 的对应工作是**回归锁定**（防止归一化路径绕过它）+ 元数负向用例（`ERR_EQL_FUNC_TOO_FEW_ARGS`/`TOO_MANY_ARGS`，`:1467-1476`）。方向是复用既有响亮机制，不构成偏离。
4. **"month() 能解析"需限定为 parse 层**：roadmap WI5 节的表述属实（parse 层），但 `month` 在 14 个可加载方言 **0 注册**，`month(x)` 今天在 transform 层即报 `ERR_EQL_UNKNOWN_FUNCTION`。本 plan 的遮蔽放开 Phase 据此把判据定为"可解析 + 未注册时编译期响亮报错"，而**不是**"day() 可用"——`day()` 等的可用性归 WI6（roadmap WI6 依赖关系明文）。
5. **矩阵快照增量预期（修订稿修正起草稿的"TRIM 零增量"口径）**：`instr`/`substr`/`year` 已 14/14 注册 ⇒ POSITION/SUBSTRING 预期零新增注册（EC ③ 表现为"核实零增量 + 快照门禁 exit 0"）；但 **TRIM 带 chars 形态走逐方言 `<template>` 注册，属真实矩阵增量**（`trim`/`ltrim`/`rtrim` 虽 14/14 却均为单参 `default.dialect.xml:110-112`，只覆盖无 chars 形态）；EXTRACT 非 year 字段（若裁定补注册）亦为真增量。
6. **`es` 伪方言在快照内**：roadmap 红线说 `es` 不应出现在能力矩阵里，但 WI0 快照实际把它标 `pseudo` 收录（loadable，47 函数，继承 default）。本 plan 沿用 WI0 快照现状（不删 `es` 行），新函数经 default 继承会自然出现在其函数表——增量口径以快照门禁为准。
7. **EXTRACT 的历史注释线索与默认形态钉死**：`BaseRule.g4:284-285` 注释明写"EXTRACT(unit FROM date) extract 函数在 mysql 中可以用 date(value), time(value) 等函数代替"，且 `extract` 已作为**逗号形态**函数名注册于 13/14 方言（mssql 缺）——**本 plan 的 EXTRACT 默认归一化形态已钉死为逐字段函数**（`EXTRACT(YEAR FROM d)` → `year(d)`，Major-1），逗号形态仅作 Phase 0 对比候选；Phase 0 裁定时必须把"已有 13/14 的 `extract` 逗号函数"作为既有事实消费，不得破坏其现有行为（BC）。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`sqlExpr_special` 备选分支、`unreservedWord_` 加词）→ 经 `gen-eql-parser.xgen` 重生成 `EqlParser.java`（10904 行）/`_EqlASTBuildVisitor.java`（1819 行）等 | **生成管线，plan-first**（AGENTS.md Protected Area"生成管线"；roadmap 门控矩阵 WI5 行同判） | 本 plan 即 plan-first 载体，执行前须过 draft review；重生成命令 `./mvnw generate-sources -pl :nop-orm-eql -am` | owner doc（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`）+ 回归测试（各 Phase 红/绿用例 + 三份必测 BC） |
| `EqlAST.xjava` / `ast/_gen/` | **禁止触碰**（默认形态；避免 `EqlASTKind` ordinal 重编号，roadmap 红线） | 若 Phase 0 裁定必须新节点 → 显式 scope change + 路径 ⓒ 定价 + 回滚本声明 | `git status` 中 `ast/_gen/` 零 diff 作为每 Phase 回归证据 |
| `nop-persistence/nop-dao` 的 `*.dialect.xml` 函数注册（如需） | 平台对外方言契约的**纯增量**（新增 `<native>`/`<template>` 函数，不改既有取值；`nop-dao` 无 codegen 管线，不构成生成管线 Protected Area——roadmap Cross-Cutting 第一行） | 不触发 plan-first；逐项登记于此并同步矩阵快照 | 快照门禁 exit 0 + `TestDialect.testFunctions` H2 真库证据 + 负向用例 |
| 生成物纪律 | `.g4` 改动**单独提交**（roadmap Cross-Cutting"生成物变更纪律"）；不删除任何 lexer token | 提交拆分 git 可审计 | `git log` 文法提交与逻辑提交分离 |

⚠️ 本 plan 不触碰 `model/*.orm.xml`、`nop-auth` 认证模型、`nop-*-api` 公共接口、`nop-core`/`nop-xlang`/`nop-xdef`。

## Execution Plan

Phase 1-5 每函数（+遮蔽放开）一个 Phase，相互独立、可分别落地与回滚；顺序建议 POSITION → SUBSTRING → EXTRACT → TRIM → 遮蔽放开（EXTRACT 与遮蔽放开解耦：EXTRACT 的字段名走独立 token 规则还是 `unreservedWord_` 由 Phase 0 裁定）。每个代码 Phase 的 `.g4` 改动单独提交。

### Phase 0 - 裁定：lowering 目标矩阵、文法形态、遮蔽放开论证（不改产品代码）

Status: completed
Targets: `ai-dev/design/eql-cross-db-compat/`（新增裁定文档）、`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（只读取证）

> **硬前置复核**：本 Phase 开工前复核 `ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md` 状态并记录；**Phase 1 开工前再次复核且必须为 `completed`**。

- Item Types: `Decision`、`Proof`

- [x] 复核 WI0 三件基础设施状态未回退：矩阵快照 + 门禁脚本（`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0）、语料基线（`TestEqlCorpusBaseline` 通过）、plan 21（WI4）状态（记录于裁定文档；若非 completed 则在文档中写明 Phase 1 排队）。**并复核两处已知漂移锚点的现位**（起草期两轮实测分别为 `AstToSqlGenerator.visitSqlRegularFunction` `:487`→`:499-501`、`AstToEqlGenerator` 同名方法 `:878`→`:907`，WI1 提交平移所致——执行时以符号定位为准，把复核结果记入裁定文档）。
- [x] **裁定每个函数的 lowering 目标与方言矩阵**（Decision）：从快照函数表取证——POSITION→`instr`（14/14，含 duckdb/pg `strpos`、mssql template 的 realName/模板差异逐方言列全）；SUBSTRING→`substr`（14/14，`minArgCount="2" maxArgCount="3"` 多态）；TRIM **分两档**：无 chars 形态→`trim`/`ltrim`/`rtrim`（单参、14/14，`default.dialect.xml:110-112`），带 chars 形态→逐方言 `<template>`（**真实矩阵增量**，逐方言列全模板与证据等级）；EXTRACT **按已钉死的逐字段默认形态**裁定（`year`=14/14 就绪；month/day/hour/minute/second/quarter=0/14——逐字段裁定：补注册（哪些方言、什么证据等级，如 quarter 的候选目标 MySQL `QUARTER(d)` / MSSQL `DATEPART` / Oracle `TO_CHAR(d,'Q')`）或显式报错（错误码形态）或移交 WI6；逗号形态 `extract(f,d)`（13/14、mssql 缺）单列为对比候选，不作为默认交付）。`dm`/`tdengine` 一律 `[未取证]`，不计覆盖率。
- [x] **裁定关键字遮蔽放开的文法形态**（Decision）：默认形态 = `DAY/HOUR/MINUTE/SECOND/QUARTER` 加入 `unreservedWord_`（`BaseRule.g4:72-96`）；必须论证三点——① 与 WI0 语料基线的相容性（7 个语料文件 grep=0 命中的实测证据引用）；② 与 `intervalUnit_`（`:354-356`）的 ANTLR 歧义消解论证 + 既有 `TestEqlIntervalLiteralParse` 回归锁定；③ 放开后 `day(x)` 从解析失败变 `ERR_EQL_UNKNOWN_FUNCTION`（同为响亮）的行为登记。被拒备选（如专属 token 清单规则）与拒绝理由一并记录。
- [x] **裁定四个特殊语法的 AST 映射形态**（Decision）：默认 = altLabel 备选分支 + 手写 `EqlASTBuildVisitor` 构建期归一化进既有 `SqlRegularFunction`；**WI1 是结构先例而非直接同构**（`sqlExpr_special` 现为单备选透明包装、推导类名 `SqlExpr` 不存在，见 Current Baseline"altLabel 机制的确切语义"小节），裁定文档必须逐条确认三条约束的落实方式：① 新备选 label 以目标 AST 类名为前缀（`SqlRegularFunction_position` 等）；② 备选内不写 prop 赋值、手写 visit 完全重写（WI1 `EqlASTBuildVisitor.java:212-221` javadoc 同款）；③ 既有 `sqlCastExpr` 备选同步补 label（如 `# SqlCastExpr_cast`）。POSITION 的调用点换序（`(x IN y)` → args `[y,x]`）在构建期完成；`ast/_gen/` 零 diff 为验收项；duplicate-prop-label 的工具源码出处见 Current Baseline（`AntlrToolErrors.java:42-43` / `AstGrammarBuilder.java:524`）。**歧义登记（必须含 POSITION 竞争）**：`POSITION( x` 之后的 `IN` 既是特殊语法的分隔符、也可能是 `sqlExpr_predicate` 的 `IN` 谓词起点（`x IN (SELECT…)`、`x IN (a,b)`）——ANTLR 备选序/自适应预测取 `sqlExpr_special`，行为确定但必须在裁定文档登记，并落"POSITION 内嵌 IN 谓词"类竞争用例锁定（如 `position(a in (b,c) in d)` 形态的解析结果固定）。
- [x] **裁定 TRIM 三段式语法设计**（Decision，Major-2）：分**两档**设计——**无 chars 形态**（`TRIM(BOTH FROM y)` → `trim(y)`、LEADING → `ltrim(y)`、TRAILING → `rtrim(y)`，目标函数单参、14/14 已注册）；**带 chars 形态**（`TRIM(BOTH 'x' FROM y)`）→ 逐方言 `<template>`（多数库原生支持 `TRIM(BOTH ? FROM ?)` 语法，照 `oracle.dialect.xml:174` ilike 模板先例）。**硬性禁令：带 chars 形态不得静默丢 chars 参数**（roadmap 排序原则"只打印不设门 = 净回归"的 P1 头号禁令）——无模板方言必须编译期显式报错，不允许降级为单参 trim。文法形态裁定（Minor-②）：特殊备选分支**强制要求** `LEADING/BOTH/TRAILING` spec 关键字或 `FROM` 方可进入；**单参 `trim(x)` 留在既有 `sqlRegularFunction` 路径、不新增第二个可解析入口**（避免双可解析 + 回显漂移风险）。`LEADING/BOTH/TRAILING` token（`SQL92Keyword.g4:478/:254/:664`）的文法挂载与"语义分叉登记"（标准 SQL 字符集合修剪 vs 部分方言只修剪空格）一并落档。
- [x] **裁定 EXTRACT 字段名规则与落地范围**（Decision，Major-1）：**默认归一化形态已钉死为逐字段函数**——`EXTRACT(YEAR FROM d)` → `year(d)`（14/14 就绪），`extract(month from d)` → `month(d)` 形态；**逗号形态 `extract(f,d)`（13/14、mssql 缺）单列为对比候选**（仅用于评估 quarter 等无现成逐字段函数的字段——其补注册成本=逐方言证据：MySQL `QUARTER(d)` / MSSQL `DATEPART` / Oracle `TO_CHAR(d,'Q')`，dm/tdengine 按 unverified 纪律）。字段名规则裁定：独立 token 规则（`intervalUnit_` 同款）vs 复用 `unreservedWord_`；与遮蔽放开 Phase 的先后依赖消解；已有 13/14 方言的 `extract` 逗号函数行为不变的 BC 论证（本 plan 不改逗号形态行为，mssql 缺口属既有现状）。
- [x] **裁定负向错误码口径**（Decision）：默认复用既有 `ERR_EQL_UNKNOWN_FUNCTION` / `ERR_EQL_FUNC_TOO_FEW_ARGS` / `ERR_EQL_FUNC_TOO_MANY_ARGS`，不新起新码；列出 Phase 0 认为必须新码的场景（如需区分"语法支持但方言未注册 lowering 目标"与"完全未知函数"）及理由。
- [x] WI5 四个函数的"一致性 + 不等价"两类用例清单（约束 4）落裁定文档：如 SUBSTRING 负起点索引语义分叉、TRIM 字符集 vs 空格语义分叉、EXTRACT 时区字段语义分叉——每项至少一个"各库不等价"的登记（作为 owner doc 差异表素材）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含：逐函数 lowering 矩阵（per-dialect 证据等级；TRIM 无 chars/带 chars 两档、EXTRACT 逐字段默认形态 + 逗号对比候选）、遮蔽放开文法形态与 BC 论证、AST 映射形态（三条 altLabel 约束逐条落实 + POSITION `IN` 竞争登记）、TRIM/EXTRACT 语法设计、负向错误码口径、两类用例清单、WI4 状态复核记录、两处漂移锚点复核记录。
- [x] 本 Phase 纯裁定，不改产品代码：`No owner-doc update required`（裁定文档本身即交付物；owner doc 的覆盖表随各代码 Phase 落）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 1 - `POSITION(x IN y)` → `instr(y,x)`（调用点换序）

Status: completed
Targets: `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`sqlExpr_special` 新增 position 备选分支）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`、重生成产物、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`nop-orm-eql` 测试

- Item Types: `Feature`、`Proof`

- [x] **开工前置复核**：`ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md` 的 Plan Status = `completed`（硬前置，见顶部声明）——未完成则本 Phase 不得开工、保持 `planned` 并在 daily log 记录排队。
- [x] 修复前红：`position(x in y)` 解析失败（parse error）——回归测试断言其可解析且 lowering 正确，修复前红。
- [x] 文法与归一化（按 Phase 0 裁定）：`POSITION LP_ expr IN expr RP_` 备选分支 + 构建期换序装入既有 `SqlRegularFunction`（name=`instr`，args=[y,x]）；`.g4` 改动单独提交，`ast/_gen/` 零 diff。
- [x] 正向用例断言具体方言 SQL 文本（EC ①）：h2 上 `position('b' in 'abc')` → `instr('abc', 'b')`（以 h2 实际注册的 instr 定义输出为准）；再取一个 realName 方言（duckdb `strpos` 或 pg）断言换名输出。
- [x] 负向用例（EC ②）：手构无 `instr` 的 `DialectModel`（先例 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/` 既有测试的 `buildDialect` 模式）或不可用方言，断言编译期报 `ERR_EQL_UNKNOWN_FUNCTION`（或 Phase 0 裁定的新码）；不允许静默按原文输出 `position(...)`。
- [x] 接线验证（Minimum Rules #23）：解析 → transform 解析函数 → 生成 lowering 的运行时连通测试（一个端到端用例覆盖三层，而非只测 parse 层）。
- [x] `POSITION` 既有逗号形态行为登记：`position(x,y)` 今天解析通过、编译报 unknown-function——放开后行为不变的守护用例。
- [x] 矩阵快照：核实 `instr` 14/14 零新增注册，`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（EC ③，零增量也要核实）。
- [x] owner doc（EC ④）：`eql-and-database-compatibility.md` 新增 POSITION 小节——per-dialect lowering 表（含 mssql template、duckdb/pg `strpos`）、`dm`/`tdengine` 标 `[未取证]`、能力点登记表行。
- [x] 真库证据（EC ⑥）：h2 经 `TestDialect.testFunctions` 对 `instr` 的既有执行保持全绿（本 Phase 预期零新注册；若 h2 的 instr 无 `<testSql>` 且执行覆盖不足，补 `<testSql>` 并以 `TestSQLFunction` 验证）；无法真库验证方言清单（mysql/mariadb/mssql/oracle/postgresql/duckdb/dm/tdengine/db2）+ 替代证据写入 owner doc。
- [x] 语料 BC（EC ⑦）：`:nop-job-dao`/`:nop-auth-service`/`:nop-orm` 三份必测与 WI0 基线快照逐字一致（`TestEqlCorpusBaseline` 随全量运行通过）；不声称 7 个全测。

Exit Criteria:

- [x] 红→绿回归测试断言具体 SQL 文本（h2 `instr` 换序输出 + 一个 realName 方言输出）；修复前红留痕于提交说明或日志。
- [x] 负向用例断言具体错误码（未注册 lowering 目标方言）；无静默跳过（不允许 lowering 失败时回退原文输出）。
- [x] 接线验证用例通过（parse→transform→generate 三层连通）。
- [x] 矩阵快照门禁 exit 0；owner doc 覆盖表已落档（含 `[未取证]` 标注）。
- [x] `TestEqlCorpusBaseline` 通过（三份必测逐字一致）；`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。
- [x] `.g4` 改动单独提交；重生成后 `ast/_gen/` 在 `git status` 中零 diff。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - `SUBSTRING(x FROM a [FOR b])` → `substr(x,a[,b])`（2/3 元多态）

Status: completed
Targets: 同 Phase 1 的文法/归一化/文档/测试面

- Item Types: `Feature`、`Proof`

- [x] **开工前置复核**：`ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md` 的 Plan Status = `completed`（硬前置，见顶部声明）——未完成则本 Phase 不得开工、保持 `planned` 并在 daily log 记录排队。
- [x] 修复前红：`substring(x from 2)` 与 `substring(x from 2 for 3)` 解析失败——回归测试断言可解析，修复前红。
- [x] 文法与归一化（按 Phase 0 裁定）：`SUBSTRING LP_ expr FROM expr (FOR expr)? RP_` 备选分支 → `SqlRegularFunction`（name=`substr`，2 或 3 参）；`FOR` 缺省时 2 参（走 default.dialect.xml `:196` 的 `minArgCount="2" maxArgCount="3"` 多态）。
- [x] 正向用例断言具体方言 SQL 文本（EC ①）：h2 的 2 参与 3 参两种输出；mssql 的 `substring` realName 输出（或按 Phase 0 矩阵选替代方言）。
- [x] 负向用例（EC ②）双条：未注册方言报 `ERR_EQL_UNKNOWN_FUNCTION`；元数边界（方言把 maxArgCount 压到 2 时的 3 参报错 `ERR_EQL_FUNC_TOO_MANY_ARGS`，或等价机制化用例）。
- [x] 接线验证（#23）：三层连通端到端用例。
- [x] 既有逗号形态 `substring(x,a,b)` 行为不变守护用例。
- [x] "不等价"类用例（约束 4）：负起点索引语义分叉至少一项登记用例或 owner doc 差异行（default.dialect.xml 描述明言"start 为负则相对末尾"——并非所有方言同语义）。
- [x] 矩阵快照核实零增量 + 门禁 exit 0（EC ③）；owner doc SUBSTRING 覆盖表（EC ④，含 `[未取证]`）；真库证据（EC ⑥，同 Phase 1 口径）；语料 BC（EC ⑦）。

Exit Criteria:

- [x] 红→绿回归测试断言具体 SQL 文本（2 参与 3 参两种形态 + 一个 realName 方言）。
- [x] 负向用例断言具体错误码（unknown-function + 元数）；无静默跳过。
- [x] 接线验证与"不等价"登记完成。
- [x] 矩阵快照门禁 exit 0；owner doc 覆盖表已落档。
- [x] `TestEqlCorpusBaseline` 通过；`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。
- [x] `.g4` 改动单独提交；`ast/_gen/` 零 diff。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - `EXTRACT(f FROM d)` → 逐字段方言 lowering

Status: completed
Targets: 同 Phase 1 + `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`（若 Phase 0 裁定补注册字段目标函数）

- Item Types: `Feature`、`Decision`（若裁定补注册）、`Proof`

- [x] **开工前置复核**：`ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md` 的 Plan Status = `completed`（硬前置，见顶部声明）——未完成则本 Phase 不得开工、保持 `planned` 并在 daily log 记录排队。
- [x] 修复前红：`extract(year from d)` 解析失败——回归测试断言可解析，修复前红。
- [x] 文法与归一化（按 Phase 0 裁定；**默认归一化形态已钉死为逐字段函数**）：`EXTRACT LP_ <字段> FROM expr RP_` 备选分支，`EXTRACT(YEAR FROM d)` → `SqlRegularFunction`（name=`year`，args=[d]）；字段名规则按 Phase 0 裁定（独立 token 规则或 `unreservedWord_`）。逗号形态 `extract(f,d)` 是既有行为，本 Phase 不改其文法与 lowering。
- [x] **字段落地范围与 Phase 0 裁定一致**：`year` 字段必须有正向 lowering（14/14 就绪）；其余字段的"落地 / 显式报错 / 移交 WI6"三分处置逐字段落实并记录——**不允许名义 owner**（裁定为移交的字段须写入 Deferred But Adjudicated + successor）。
- [x] 正向用例断言具体方言 SQL 文本（EC ①，**按逐字段形态书写**）：`extract(year from d)` 在 h2 断言输出为 `year(d)` 形态文本（以 h2 实际注册的 `year` 定义输出为准）；若裁定补注册 `month`/`quarter` 等字段，则每个新注册字段在 h2 各一条输出用例（`extract(month from d)` → `month(d)` 形态）。
- [x] 负向用例（EC ②，**按逐字段形态书写**）：无目标字段函数的方言编译期报错（断言具体错误码）——`year` 已 14/14 注册，用例以手构无 `year` 的 `DialectModel` 载体实现（先例 `buildDialect` 模式）；裁定补注册的字段各一条"方言未注册该字段函数"负向用例。ⓘ **mssql 叙述拆开**：逐字段形态的 `year` 在 mssql **已注册（14/14 之一）、`EXTRACT(YEAR FROM d)` 在 mssql 可用**；**逗号形态** `extract(x,y)` 在 mssql 无注册（13/14 的唯一缺口）——该缺口是既有现状、逗号形态非本 plan 交付面，本 Phase 不修（如需覆盖属后续裁定并登记）。
- [x] 接线验证（#23）+ 既有逗号形态 `extract(x,y)` 在 h2 行为不变的守护用例（BC，差异登记第 7 条）。
- [x] 矩阵快照增量（EC ③）：本 Phase 与 Phase 4（TRIM 模板）并列**预期产生快照增量**的两个 Phase——新注册字段函数必须同步 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（重生成 `TestDialectCapabilityMatrix` 快照测试），门禁 exit 0；若裁定 EXTRACT 只落地 year 字段则核实零增量。
- [x] owner doc（EC ④）：EXTRACT 逐字段 × 逐方言覆盖表（`[实测]`/`[文档]`/`[未取证]`）；时区字段语义分叉登记（约束 4"不等价"类）。
- [x] 真库证据（EC ⑥）：**每个新注册字段函数**在 h2 经 `TestDialect.testFunctions` 真库执行全绿（有 `<testSql>` 用 `testSql`）；无法真库验证的方言（mysql/mariadb/mssql/oracle/postgresql/duckdb/dm/tdengine/db2）逐个列出 + 替代证据；`dm`/`tdengine` 标 `[未取证]`。
- [x] 语料 BC（EC ⑦）：三份必测逐字一致。

Exit Criteria:

- [x] 红→绿回归测试断言具体 SQL 文本（`extract(year from d)` + 裁定落地的每个字段）。
- [x] 负向用例断言具体错误码；未落地字段均为显式报错或已登记 successor（无静默跳过、无名义 owner）。
- [x] 矩阵快照增量已同步（或核实零增量）+ 门禁 exit 0。
- [x] 新注册函数（若有）在 h2 `TestDialect.testFunctions` 真库执行全绿；无法真库验证方言清单 + 替代证据已落 owner doc。
- [x] `TestEqlCorpusBaseline` 通过；`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。
- [x] `.g4` 改动单独提交；`ast/_gen/` 零 diff。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - `TRIM` 标准三段式

Status: completed
Targets: 同 Phase 1

- Item Types: `Feature`、`Proof`

- [x] **开工前置复核**：`ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md` 的 Plan Status = `completed`（硬前置，见顶部声明）——未完成则本 Phase 不得开工、保持 `planned` 并在 daily log 记录排队。
- [x] 修复前红：`trim(both from x)` / `trim(leading 'x' from y)` 解析失败——回归测试断言可解析，修复前红。
- [x] 文法与归一化（按 Phase 0 裁定，Major-2/Minor-②）：特殊备选分支**强制要求** `LEADING|BOTH|TRAILING` spec 关键字或 `FROM` 方可进入（即 `TRIM LP_ (LEADING|BOTH|TRAILING) …` / `TRIM LP_ expr FROM expr RP_` 形态）；**单参 `trim(x)` 留在既有 `sqlRegularFunction` 路径、不新增第二个可解析入口**（避免双可解析 + 回显漂移风险）。归一化分两档：**无 chars 形态**（`TRIM(BOTH FROM y)` → `trim(y)`、LEADING → `ltrim(y)`、TRAILING → `rtrim(y)`——三个目标函数均单参、14/14 已注册，`default.dialect.xml:110-112`）；**带 chars 形态**（`TRIM(BOTH 'x' FROM y)`）→ 逐方言 `<template>`（多数库原生支持 `TRIM(BOTH ? FROM ?)` 语法，照 `oracle.dialect.xml:174` ilike 模板先例）——**chars 参数不得静默丢弃**（roadmap 排序原则"只打印不设门 = 净回归"的 P1 头号禁令），无模板方言必须编译期显式报错、不允许降级为单参 trim。既有单参 `trim(str)` 行为逐字不变（守护用例）。
- [x] 正向用例断言具体方言 SQL 文本（EC ①）：无 chars 形态 `both`/`leading`/`trailing` 各一条 h2 输出（`trim(y)`/`ltrim(y)`/`rtrim(y)` 形态）；带 chars 形态至少一条 h2 模板输出（`TRIM(BOTH 'x' FROM y)` 的模板渲染文本）。
- [x] 负向用例（EC ②）：**带 chars 形态在无模板方言上编译期显式报错**（断言具体错误码——不允许静默丢 chars、不允许静默降级单参 trim）；无 chars 形态目标函数缺失方言的编译期报错；`LEADING/BOTH/TRAILING` 非法组合的解析期/构建期响亮失败。
- [x] 接线验证（#23）+ "不等价"类用例（约束 4）：**带 chars 的 TRIM 语义分叉**（标准 SQL 是字符集合修剪；部分方言 `trim(s)` 只修剪空格）至少一项差异用例或 owner doc 差异行。
- [x] 矩阵快照**预期有增量**（带 chars 的 `<template>` 注册会进入方言函数表）：同步 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（重生成 `TestDialectCapabilityMatrix` 快照测试）+ 门禁 exit 0（EC ③）；owner doc TRIM 覆盖表与语义分叉登记（EC ④）；真库证据（EC ⑥，**新增模板函数**在 h2 经 `TestDialect.testFunctions` 真库执行全绿 + `trim`/`ltrim`/`rtrim` 既有执行保持全绿；无法真库验证方言逐个列出）；语料 BC（EC ⑦）。

Exit Criteria:

- [x] 红→绿回归测试断言具体 SQL 文本（无 chars 三方向形态 + 带 chars 模板形态 + 单参不变）。
- [x] 带 chars 形态在无模板方言上编译期显式报错且断言具体错误码（无静默丢参/无静默降级）；其余负向用例断言具体错误码。
- [x] TRIM 语义分叉差异已登记（用例或 owner doc 差异行）。
- [x] 矩阵快照增量已同步 + 门禁 exit 0；owner doc 覆盖表已落档。
- [x] `TestEqlCorpusBaseline` 通过；`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。
- [x] `.g4` 改动单独提交；`ast/_gen/` 零 diff。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 5 - 放开 `day`/`hour`/`minute`/`second`/`quarter` 关键字遮蔽

Status: completed
Targets: `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`unreservedWord_`）、重生成产物、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`nop-orm-eql` 测试

> 本 Phase 是**文法放开**：五个词进入函数名/标识符位置后，`day(x)` 等的预期行为 = 可解析 + transform 层报 `ERR_EQL_UNKNOWN_FUNCTION`（0/14 注册，响亮）——归一化日期函数注册是 WI6 的交付面（roadmap WI6 依赖关系明文）。本 Phase 的可用性判据**不含**"day() 返回正确结果"。

- Item Types: `Feature`、`Proof`

- [x] **开工前置复核**：`ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md` 的 Plan Status = `completed`（硬前置，见顶部声明）——未完成则本 Phase 不得开工、保持 `planned` 并在 daily log 记录排队。
- [x] 修复前红：`select day(col) from t` 与 `select o.day from t`（列名位置）解析失败——回归测试断言可解析，修复前红。
- [x] 文法（按 Phase 0 裁定）：`DAY/HOUR/MINUTE/SECOND/QUARTER` 加入 `unreservedWord_`（`BaseRule.g4:72-96`）；**不动** `nop-persistence/nop-orm-eql/model/antlr/Keyword.g4` 词法器、不删任何 token、不动 `YEAR|MONTH` 既有成员。
- [x] 正向用例（EC ① 的遮蔽面）：五个词各一条"函数名位置可解析"用例（断言产出 `SqlRegularFunction` 且 name 小写）+ 至少一条"标识符位置可解析"用例（`select o.day from t` 的回显/方言输出）。**断言到 parse/echo 层为止**——transform 层断言为具体错误码 `ERR_EQL_UNKNOWN_FUNCTION`（未注册的响亮行为锁定），不得断言执行结果。
- [x] **month()/year() 行为不变**：`month(x)`/`year(x)` 解析与 transform 行为与放开前一致（守护用例——`month` 仍 0 注册、`year` 在注册方言上行为不变）。
- [x] **interval 共存回归**：`interval 1 day`、`interval '3 hours'`（WI1 形态）解析与输出逐字不变（`TestEqlIntervalLiteralParse` 全量回归 + 新增 `day`/`hour` 在 intervalUnit_ 与 unreservedWord_ 双重身份下的歧义消解用例）。
- [x] 既有语料 BC（EC ⑦）：三份必测逐字一致（Phase 0 已证 7 文件 0 命中，本 Phase 用棘轮实测兜底）。
- [x] owner doc（EC ④）：遮蔽放开说明——五个词的"可解析但未注册 → 响亮报错"现状、`month`/`year` 的对照差异、WI6 是注册 owner 的指引。
- [x] `./mvnw test -pl :nop-orm-eql -am -T 1C` 全绿；`.g4` 单独提交；`ast/_gen/` 零 diff。

Exit Criteria:

- [x] 红→绿回归测试：五个词在函数名位置可解析（name 小写断言）；标识符位置至少一条用例。
- [x] transform 层响亮行为锁定：未注册的 `day(x)` 断言 `ERR_EQL_UNKNOWN_FUNCTION`（或该方言已注册时的具体行为）；无静默跳过。
- [x] `month()`/`year()`/`interval 1 day`/`interval '3 hours'` 行为逐字不变的守护用例通过；`TestEqlIntervalLiteralParse` 全绿。
- [x] `TestEqlCorpusBaseline` 通过（三份必测逐字一致）。
- [x] owner doc 遮蔽放开说明已落档（含 WI6 指引）。
- [x] `./mvnw test -pl :nop-orm-eql -am -T 1C` 全绿；`.g4` 单独提交；`ast/_gen/` 零 diff。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [x] 四个 SQL-92 函数语法（POSITION/SUBSTRING/EXTRACT/TRIM）全部落地：各有断言正确方言 SQL 文本的红→绿正向用例（EC ①）。
- [x] 每个函数的"方言未注册目标函数时编译期报错"负向用例存在且断言具体错误码（EC ②；机制复用既有 `ERR_EQL_UNKNOWN_FUNCTION` 族，差异登记第 3 条）。
- [x] 方言能力矩阵快照的覆盖增量已更新并核实：POSITION/SUBSTRING **预期零增量**（目标函数已 14/14 注册，仅核实）；**TRIM 带 chars 的 `<template>` 注册与 EXTRACT 非 year 字段注册预期为真增量**（必须同步快照）；`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（EC ③）。
- [x] `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 已同步：四个函数的"各库覆盖情况"表（逐行 `[实测]`/`[文档]`/`[未取证]`）+ 能力点登记表行 + 遮蔽放开说明（EC ④）。
- [x] **真库执行证据（EC ⑥）**：本 plan 新注册的每个函数在 `nop-dao/TestDialect.testFunctions` 真库（H2）执行路径下全绿；无法真库验证的方言（mysql/mariadb/mssql/oracle/postgresql/duckdb/dm/tdengine/db2）已逐个列出并给替代证据，`dm`/`tdengine` 标 `[未取证]` 且未计入覆盖率。
- [x] **既有 EQL 语料 BC（EC ⑦）**：`:nop-job-dao`(NopJobTask)、`:nop-auth-service`(test)、`:nop-orm`(test) 三份必测与 WI0 基线快照逐字一致；未声称"7 个全测"（`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份由 roadmap 级 EC 第 8 条 + WI0 静态快照覆盖）。
- [x] 约束 4 的"一致性 + 不等价"两类用例/差异登记齐备（SUBSTRING 负起点、TRIM 字符集语义、EXTRACT 时区字段至少各一项）。
- [x] 遮蔽放开完成且零回归：`day`/`hour`/`minute`/`second`/`quarter` 可解析、`month()`/`year()`/interval 形态逐字不变。
- [x] `./mvnw test -pl :nop-orm-eql,:nop-dao,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（roadmap WI5 EC ⑤ 命令）——**"全绿"口径**：本 WI 相关模块全绿 + `nop-stream-runtime` 的 **2 项既有 flake**（`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`、`TestMessageSourceFunctionRestart`，均单跑 PASS）与基线一致即豁免，不得据此判本 WI 失败。
- [x] 生成物纪律：每个 Phase 的 `.g4` 改动单独提交（`git log` 文法提交与逻辑提交分离可审计）；重生成后 `ast/_gen/` 零 diff；无 lexer token 删除。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0（既有基线 3 项 warning：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，不顺手修、只比对新增量）。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/24-eql-wi5-sql92-function-syntax.md --strict` 退出码 0。（closure audit 后主会话复跑 exit 0）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中（anti-hollow："只解析不 lowering/只打印不设门"正是半实现形态）。
- [x] owner doc 已同步到 live baseline（见 EC ④ 条目），`docs-for-ai/INDEX.md` 与 `04-reference/source-anchors.md` 若路由/锚点变化已更新。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）四个函数从 EQL 文本 → parse 归一化 → transform 函数解析 → 方言 SQL 输出/报错的端到端调用链真实连通（不只是文法分支存在）；（b）无空方法体/静默跳过/no-op（lowering 不可用时必须响亮报错，不允许回退原文输出）；（c）遮蔽放开未引入任何静默行为变化。（独立审计 Anti-Hollow 三链路经端到端编译测试与 H2 真库执行双重实证）
- [x] **独立 closure audit 已完成并由不同 `task_id` 的子 agent 执行**，evidence 写入本 plan `Closure` 段（guide #26/#27；roadmap WI5 EC ⑧）；roadmap Work Item Status 的 WI5 行据此才可 `planned` → `done`。

## Deferred But Adjudicated

### `convertFunction`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 第 5 个注释块）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap WI5 清单只有 4 个 SQL-92 标准函数（POSITION/SUBSTRING/EXTRACT/TRIM），`CONVERT` 是方言私有转换函数形态（`CONVERT(expr USING identifier)`），无跨方言无损 lowering 目标（约束 3）——维持注释态。
- Successor Required: `no`（若产品出现真实需求，先登记 roadmap 再立项）。
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（新增登记行）。

### EXTRACT 非 year 字段的归一化日期函数注册（若 Phase 0 裁定为移交）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: roadmap WI6 明文是归一化日期时间函数族的唯一 owner（`month`/`day`/`hour`/`minute`/`second`/`quarter` 全族注册），且 WI6 依赖本 plan 的遮蔽放开——EXTRACT 只落地 year 字段不构成 WI5 收口缺口（roadmap WI5 文本 "EXTRACT(f FROM d) → year()/month()等" 的"等"字由 Phase 0 裁定切分）。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI6 节。

### `WEEK` 关键字遮蔽

- Classification: `watch-only residual`
- Why Not Blocking Closure: roadmap WI5 只点名 day/hour/minute/second/quarter 五个词；`WEEK`（`Keyword.g4:409`，同样不在 `unreservedWord_`）无已知的用户需求输入。
- Successor Required: `no`（WI6 若出现 `weekofyear` 归一化需求时一并复核）。
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI6 节（观察项）。

## Non-Blocking Follow-ups

- `es` 伪方言在 WI0 快照中以 `pseudo` 标记收录与 roadmap 红线"不参与能力矩阵"的口径差异，由 WI0 快照 owner（roadmap）统一裁定；本 plan 只沿用现状（差异登记第 6 条）。
- CI 真库验证面扩展（mysql/oracle 等 docker 化执行）超出本 roadmap；若立项，四个函数的 `[未取证]` 格在其上转 `[实测]`。
- AstGrammarBuilder 的 altLabel/prop 约束文档化（目前散在 WI1/WI5 两个 plan 的执行期记录中）——若 `docs-for-ai/02-core-guides/` 后续增设 EQL 文法指南，随迁。

## Closure

Status Note: 6 个 Phase 全部 `completed`；独立 closure audit（fresh-session 子 agent）一次通过——A①-⑧/B1-B5/C/D 全 PASS，**0 Blocker / 0 Major / 2 Minor**（①"新增六词"系误计，实际五词——QUARTER/DAY/HOUR/MINUTE/SECOND，MONTH 既有；已随收口勘误 daily log+owner doc；② P3 文法 commit 含手写 visit 方法——逻辑/文法 commit 仍分离且 message 如实披露，记录不构成违规）。这是本系列首个**零计数误差**的审计（surefire 来源纪律见效）；Anti-Hollow 三链路经端到端编译测试与 H2 真库执行双重实证。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: fresh-session 独立子 agent（与执行者不同 task_id），2026-10-04
- Audit Session: WI5 closure audit（一次通过，结论"WI5 通过 closure audit"）
- Evidence:
  - A① PASS：13 个测试类 62/62 独立复跑全绿；四函数方言文本逐 commit 核断言（h2/duckdb/mssql/oracle 多族）
  - A② PASS：负向逐函数断言 ERR_EQL_UNKNOWN_FUNCTION+归一化名；dm trimChars 硬性禁令锁定；TOO_MANY_ARGS 与逗号形态守护
  - A③ PASS：矩阵快照 +123 程序化核验（+6×15 可加载、+3×11 trimChars、dm/tdengine/db2/es 零注册=禁令落实；POSITION/SUBSTRING 零增量）；门禁 exit 0
  - A④ PASS：owner doc 覆盖表（:348-417）逐行 [实测]/[文档]/[未取证]
  - A⑥ PASS：审计员本人复跑 TestDialect 9/9 绿，surefire XML 实证 61 次真库函数执行；[未取证] 清单未计覆盖率
  - A⑦ PASS：棘轮 diff 恰 2 行均为 ANTLR 诊断元数据漂移（added tokens 程序化对比=5 词、removed=∅、source/offendingToken 逐字不变），CorpusBaseline 1/1
  - B1-B5 PASS：altLabel 三约束 7 label 全合规+工具硬约束回写裁定文档 §3.1；遮蔽放开=纯 parser 规则（lexer/tokens 零触碰）；5 文法+5 逻辑 commit 逐 Phase 分离；归一化解析链走既有 visitSqlRegularFunction 通路未新造；无手改生成物
  - C PASS：四门禁+checklist exit 0（doc-links 0 error、3 基线 warning 恰为登记项）
  - D PASS：声称计数五模块逐一全符（303/205|46skip/1340/87/461|4skip，stream 目录 1238|1 豁免项症状逐字一致、排除后 1230|0）——首次零计数误差
  - Deferred 项分类检查：无 in-scope live defect 被降级；WEEK 不在放开清单为 roadmap 明文（观察项已记 owner doc）
- Audit Session: （session/task ID）
- Evidence:
  - （每条 Exit Criterion 与 Closure Gate 的验证结果 PASS/FAIL + live code path / test name）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/24-eql-wi5-sql92-function-syntax.md --strict` 退出码
  - Anti-Hollow 检查结果（端到端调用链追踪 + `scan-hollow-implementations.mjs` 退出码）
  - Deferred 项分类检查（确认无 in-scope live defect 被降级）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
