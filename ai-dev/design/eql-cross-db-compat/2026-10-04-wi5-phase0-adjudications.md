# WI5 Phase 0 裁定 — SQL-92 函数语法 lowering 矩阵、文法形态、遮蔽放开

> Date: 2026-10-04
> Plan: `ai-dev/plans/24-eql-wi5-sql92-function-syntax.md` Phase 0（纯裁定，不改产品代码）
> Source: roadmap `ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI5 节 + 约束 1-4 + 红线；live 代码锚点于 2026-10-04 在分支 `feat/eql-cross-db-compat` 逐文件复核
> 锚点约定：file:line 为 2026-10-04 实测值；符号定位优先于行号。

## 0. 开工门与基础设施复核（plan Phase 0 第 1 项）

| 复核项 | 结果 | 证据 |
|---|---|---|
| plan 21（WI4）Plan Status | **completed** ✅（硬前置满足，代码 Phase 可开工） | `ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md` 头部 `Plan Status: completed`（Last Reviewed 2026-10-04） |
| 矩阵快照门禁 | `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0 ✅；18 个方言 XML 全登记 | 门禁输出 "No errors found"（2026-10-04 实测） |
| 语料基线棘轮 | `TestEqlCorpusBaseline` 随模块测试通过（WI1-WI4 收口轮持续绿，本 plan 各 Phase 出口再跑） | `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlCorpusBaseline.java` |
| 文法重生成管线零 diff 基线 | `./mvnw generate-sources -pl :nop-orm-eql -am` 在未改文法状态跑通，`git status nop-persistence/nop-orm-eql/` 零 diff ✅（WI1-WI4 收口后的活管线再证） | 2026-10-04 实测（exit 0） |
| `TemplateSQLFunction` marker 加载期校验（WI0 交付 2） | 在位 ✅：越界全数字 marker 加载期抛 `ERR_DAO_FUNC_TEMPLATE_MARKER_OUT_RANGE`（`TemplateSQLFunction.java` parseMarkers 内）；元数不符抛 `ERR_DAO_FUNC_INVALID_ARG_COUNT`（buildFunctionExpr 入口） | `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/function/TemplateSQLFunction.java` |

### 漂移锚点复核（plan 点名两处 + 执行期新发现）

| 锚点 | 起草期值 | 2026-10-04 实测 | 结论 |
|---|---|---|---|
| `AstToSqlGenerator.visitSqlRegularFunction` | `:487`→`:499-501` | **`:499-503`**（`func.buildFunctionExpr(location, args, dialect)`） | 一致，无进一步漂移 |
| `AstToEqlGenerator.visitSqlRegularFunction` | `:878`→`:907` | **`:907-911`** | 一致 |
| `EqlTransformVisitor.visitSqlRegularFunction` | `:1454-1481` | **`:1454-1481`**（`ERR_EQL_UNKNOWN_FUNCTION` `:1456-1458`、window 限定 `:1460-1465`、元数 `:1467-1476`） | 一致 |
| `EqlParser.java` 行数 | plan 记 10904 | **10927**（WI2 文法提交再平移，非本 plan 范围） | 登记漂移，不影响符号定位 |
| `_EqlASTBuildVisitor.java` 行数 | 1819 | **1819** | 一致 |
| `ast/_gen/` 文件数 | 77 | **77** | 一致 |
| `BaseRule.g4` 各锚点 | `unreservedWord_ :72-96`、`sqlExpr_special :286-289`、`sqlCastExpr :291-293`、注释块 `:295-314`、`sqlRegularFunction :316-318`、`sqlIdentifier_func_ :320-323`、`sqlIntervalExpr :346-352`、`intervalUnit_ :354-356` | 全部一致（`unreservedWord_` 含 `POSITION :88`、`YEAR|MONTH :89`，不含 DAY/HOUR/MINUTE/SECOND/QUARTER） | 一致 |
| `Keyword.g4` 保留 token | YEAR:397 / QUARTER:401 / MONTH:405 / DAY:413 / HOUR:417 / MINUTE:421 / SECOND:425；POSITION:110 / TRIM:166 / SUBSTRING:170 / FROM:174 / FOR:241 / IN:285；SQL92Keyword EXTRACT:398 / LEADING:478 / BOTH:254 / TRAILING:664 | 全部一致 | 一致 |
| **方言可加载数** | plan 起草期口径 **14 可加载**（db2 不可加载） | **15 可加载**（db2 经 WI3 补 mandatory 子节点后可加载，快照 `functions` 57 项）；3 不可加载 = default/geo-support/window-expr-support；`es` 仍为 pseudo | **登记漂移**：本 plan 的函数覆盖计数以快照实测为准（15 家含 db2），正文用"15/15 可加载方言"口径；dm/tdengine 红线不变 |

## 1. 逐函数 lowering 目标矩阵（Decision）

> 证据等级口径：`[实测]` = 本仓库 CI 真库（H2 in-mem）执行证据；`[文档]` = 一手文档引证（引文见 owner doc 覆盖表）；`[未取证]` = 无一手文档/不可真库验证，**不计入覆盖率**（dm/tdengine 按红线、db2 按与 roadmap 一致的弱证据纪律、es 为 pseudo 不参与）。

### 1.1 POSITION → `instr`（预期零注册增量）

- 归一化：`POSITION(x IN y)` → `SqlRegularFunction(name="instr", args=[y, x])`（调用点换序在构建期完成）。
- 现状快照取证：`instr` 在全部 15 个可加载方言已注册（`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`），**本 Phase 零新增注册**，矩阵增量=核实零。
- 逐方言差异（全部已有定义，仅列举，不改）：
  - h2/h2gis/mysql/mariadb/mysql5.7/oracle/oracle-reverse/dm/tdengine/es/db2/postgis/duckdb 继承或定义 `instr(str, substr)` native（duckdb `realName="strpos"`，`duckdb.dialect.xml:87`；postgresql/postgis `realName="strpos"`，`postgresql.dialect.xml:158`）——`strpos(haystack, needle)` 语义同序，无需换参。
  - mssql 模板 `charindex({1},{0})`（`mssql.dialect.xml:174-176`）——模板内已换序（`{0}`=str、`{1}`=substr → `charindex(substr, str)`），语义正确。
- 负向目标：手构无 `instr` 的 `DialectModel` 编译期 `ERR_EQL_UNKNOWN_FUNCTION`（报错名是归一化名 `instr`，非 `position`——断言这一点）。

### 1.2 SUBSTRING → `substr`（预期零注册增量）

- 归一化：`SUBSTRING(x FROM a)` → `substr(x, a)`；`SUBSTRING(x FROM a FOR b)` → `substr(x, a, b)`。复用 `default.dialect.xml:196-200` 的 `<native name="substr" ... minArgCount="2" maxArgCount="3"/>` 2/3 元多态。
- 现状快照取证：`substr` 15/15 已注册；mssql 为 `realName="substring"`（`mssql.dialect.xml:198`，MSSQL `SUBSTRING(expr, start, len)` 语义同序）。
- 本 Phase 零新增注册，矩阵增量=核实零。
- 负向目标双条：无 `substr` 方言 `ERR_EQL_UNKNOWN_FUNCTION`；`maxArgCount=2` 方言上 3 参形态 `ERR_EQL_FUNC_TOO_MANY_ARGS`。

### 1.3 EXTRACT → 逐字段函数（预期真注册增量，plan 起草期已钉死默认形态）

- 归一化：`EXTRACT(f FROM d)` → `SqlRegularFunction(name=f 的小写, args=[d])`。`EXTRACT(YEAR FROM d)` → `year(d)`。
- 字段集合：`YEAR/MONTH/DAY/HOUR/MINUTE/SECOND/QUARTER` 七个（与归一化函数一一同名）。**WEEK/MICROSECOND 不入字段规则**（无注册函数，文法层即拒绝——比"可解析但响亮报错"更早失败，二者同为响亮；若 WI6 注册 `week` 族再扩字段规则）。
- 注册设计（照 `year` 既有分布镜像）：
  - `default.dialect.xml`：新增 6 个 `<native>`（`month/day/hour/minute/second/quarter`，`returnType="INTEGER" argTypes="TIMESTAMP"`，各带 `<testSql>`）——镜像既有 `year`（`default.dialect.xml:257`）的落位。15 个可加载方言（含 db2/dm/tdengine/es 继承面）函数表 +6 名。
  - `mssql.dialect.xml`：6 个 `<template>` → `datepart(field,{0})`（MSSQL 无 `MONTH(d)` 全族函数；`YEAR` 已用 `datepart(year,{0})` 先例 `mssql.dialect.xml:204`）。
  - `oracle.dialect.xml`：6 个 `<template>` → `extract(FIELD from {0})`（镜像既有 `year` 模板 `oracle.dialect.xml:191-193`）。
  - `postgresql.dialect.xml`：6 个 `<template>` → `extract(FIELD from {0})`（PG 无 `month()` 函数形态；镜像 `year` 模板 `postgresql.dialect.xml:163-165`）。duckdb/postgis 继承（DuckDB/PG 原生支持 `EXTRACT`）。
  - mysql/mariadb/mysql5.7/h2/h2gis：native 名即真名（MySQL 8.0 / H2 2.x 均有 `MONTH/DAY/HOUR/MINUTE/SECOND/QUARTER` 函数），零改动。
  - **H2 QUARTER 可用性以 `TestDialect.testFunctions` 真库执行实证兜底**（EC ⑥；若 H2 版本缺 QUARTER 则 h2.dialect.xml 换模板并登记——实测路径优先于文档断言）。
- 逗号形态 `extract(f, d)`（既有 14/15 可加载，mssql 缺）：**既有行为不改、不修 mssql 缺口**（差异登记第 7 条），单列为对比候选。mssql 上 `EXTRACT(YEAR FROM d)` 走逐字段形态的 `year`（datepart 模板）可用。
- 矩阵增量：15 方言 × +6 函数名 = **真增量**，快照同步 + 门禁 exit 0。

### 1.4 TRIM → 两档（无 chars 零增量；带 chars 真增量）

- **无 chars 档**（`TRIM(BOTH FROM y)` / `LEADING` / `TRAILING`）→ `trim(y)` / `ltrim(y)` / `rtrim(y)`。三函数单参 15/15 已注册（`default.dialect.xml:110-112`），**零新增注册**。
- **带 chars 档**（`TRIM(BOTH 'x' FROM y)` 等）→ 新规范函数名三件套，**args 顺序 [str, chars]（主串在前，对齐 `instr`/`replace`/`lpad` 惯例）**：
  - `trimChars(str, chars)`（BOTH，及省略方向的 `TRIM('x' FROM y)` 默认 BOTH）
  - `ltrimChars(str, chars)`（LEADING）
  - `rtrimChars(str, chars)`（TRAILING）
  - 规范函数名 = 构建期归一化赋予、供 `dialect.getFunction(name)` 命中的名字；模板把方向语法逐方言渲染，方向信息不进函数签名（模板是固定字符串，无法分支，故一名一方向）。
- 注册面（全部 `<template>`，照 `oracle.dialect.xml:174` ilike 先例——**不进 default**，防止向 dm/tdengine/es/db2 泄漏未取证行为）：
  - `h2.dialect.xml`（h2gis 继承）：`TRIM(BOTH {1} FROM {0})` / `TRIM(LEADING {1} FROM {0})` / `TRIM(TRAILING {1} FROM {0})`。H2 2.x `TRIM` 支持标准全形态 `[文档]`。
  - `mysql.dialect.xml`（mariadb、mysql5.7 继承）：同标准模板。MySQL 8.0 `TRIM([{BOTH|LEADING|TRAILING} [remstr] FROM] str)` `[文档]`。
  - `postgresql.dialect.xml`（postgis 继承）：同标准模板。PG 15 `TRIM([LEADING|TRAILING|BOTH] [characters] FROM string)` `[文档]`。
  - `oracle.dialect.xml`（oracle-reverse 继承）：同标准模板。Oracle `TRIM(LEADING|TRAILING|BOTH trim_character FROM trim_source)` `[文档]`。
  - `mssql.dialect.xml`：同标准模板。SQL Server 2022 起 `TRIM([LEADING|TRAILING|BOTH] [characters FROM] string)` `[文档]`（G-1 承诺基线=MSSQL 2022，恰好覆盖）。
  - `duckdb.dialect.xml`：**DuckDB 无关键字形态**，用函数形态模板 `trim({0},{1})` / `ltrim({0},{1})` / `rtrim({0},{1})`（DuckDB 三者均有 (string, characters) 签名）`[文档]`。duckdb 必须显式覆盖，否则会继承 postgresql 的关键字模板产出非法 SQL。
  - **不注册**：`default`（继承根）、`dm`、`tdengine`、`es`、`db2` → 这些方言上带 chars TRIM 编译期 `ERR_EQL_UNKNOWN_FUNCTION(trimChars)` 响亮报错（硬性禁令：不允许静默丢 chars、不允许降级单参 trim）。
- 矩阵增量：11 方言（h2/h2gis/mariadb/mysql/mysql5.7/mssql/oracle/oracle-reverse/postgis/postgresql/duckdb）× +3 函数名 = **真增量**。
- `TestDialect.testFunctions`（H2）证据：三个新模板函数带 `<testSql>`，随 h2 方言真库执行（EC ⑥）。

### 1.5 汇总矩阵（执行后预期）

| 函数 | 注册增量 | 执行后覆盖（15 可加载） |
|---|---|---|
| `instr` | 0 | 15/15（既有） |
| `substr` | 0 | 15/15（既有） |
| `year` | 0 | 15/15（既有） |
| `month/day/hour/minute/second/quarter` | **+6×15=90** | 15/15 |
| `trim/ltrim/rtrim`（单参） | 0 | 15/15（既有） |
| `trimChars/ltrimChars/rtrimChars` | **+3×11=33** | 11/15；dm/tdengine/es/db2 响亮报错 |
| `extract`（逗号形态） | 0（mssql 缺口维持既有现状） | 14/15（既有） |

## 2. 关键字遮蔽放开的文法形态（Decision）

- **采纳**：`DAY | HOUR | MINUTE | SECOND | QUARTER` 加入 `unreservedWord_`（`BaseRule.g4:72-96` 末段新增一行）。不动 `Keyword.g4` 词法器、不删/不重命名任何 lexer token、不动 `YEAR|MONTH` 既有成员。**被拒备选**：① 专属 token 清单规则（如 `shadowedFuncName_`）——需要新语法入口且与 `sqlIdentifier_` 双入口并存，回显/歧义面更大；② 改 Keyword.g4 把五个词降为非保留——删除/重排 token 违反 roadmap 红线（token 重编号），且影响全部保留字判定面。
- **三点论证**：
  1. **语料 BC**：7 个含 `<eql>` 的 `sql-lib.xml` 对 `day/hour/minute/second/quarter`（调用形态+裸词形态）grep=0 命中（plan Current Baseline 实测沿用），遮蔽放开对既有语料可解析集合纯增量；棘轮 `TestEqlCorpusBaseline` 逐字节兜底。
  2. **`intervalUnit_` 歧义消解**：`intervalUnit_`（`BaseRule.g4:354-356`）与 `unreservedWord_` 是两条独立 parser 规则，`interval 1 day` 中 `DAY` 在 `sqlIntervalExpr` 上下文只有 `intervalUnit_` 一个可行解析（`sqlIdentifier_` 不在该上下文可达），ANTLR 自适应预测无歧义。既有 `TestEqlIntervalLiteralParse` 全量回归 + 新增 `interval 1 day`/`interval '3 hours'` 双形态守护用例锁定。
  3. **行为登记**：放开后 `day(x)` 从"解析失败"变为——在已注册 `day` 字段函数的方言（Phase 3 之后=15 方言）正常 lowering `day(col)`；在未注册方言编译期 `ERR_EQL_UNKNOWN_FUNCTION` 响亮报错。`month(x)` 同理（`month` 经 Phase 3 已注册）。**与 plan 起草期"0/14 注册 → 响亮报错"叙述的差异**：Phase 3 裁定把 EXTRACT 字段函数（含 day/month/hour/minute/second/quarter）作为"EXTRACT 组成部分"例外注册（plan Non-Goals 明文允许该例外），故 Phase 5 落地时五词在注册方言上为可用函数——plan Phase 5 EC ② 本就写明"（或该方言已注册时的具体行为）"，两相容。`day()` 的归一化日期函数族 owner 仍是 WI6（本 plan 只消费 EXTRACT 必需的 7 个字段函数）。
- W4 锚点：`convert(...)` 不动（Non-Goal），`WEEK` 不放开（Deferred 登记在 plan）。

## 3. 四个特殊语法的 AST 映射形态（Decision）

**默认形态采纳**：`sqlExpr_special` 新增 altLabel 备选分支 + 手写 `EqlASTBuildVisitor` 构建期归一化进既有 `SqlRegularFunction`；不新增 AST 节点，`ast/_gen/` 零 diff。

### 3.1 工具机制实测补充（起草后对 `AstGrammarBuilder` 源码的二次核验，比 plan 记载多一条硬约束）

`nop-kernel/nop-antlr4/nop-antlr4-tool/src/main/java/io/nop/antlr4/tool/loader/AstGrammarBuilder.java`：

- `getAstNodeName`（`:259-262`）：label 首段下划线前缀首字母大写——`SqlRegularFunction_position` → `SqlRegularFunction`（约束 1 的推导依据）。
- **带 prop 赋值的备选，prop 名必须命中目标 AST 类的真实属性**：`AstGrammar.initRuleReturnType`（`AstGrammar.java:186-200`）对每个 prop 调 `fn.apply(astNodeName, propName)` 反查 `EqlAST.xjava` 属性表，查不到抛 `ERR_GRAMMAR_INVALID_AST_PROP`。⇒ **备选内根本不能发明中间 prop 名**（如 `posBegin=sqlExpr_bit`），这与约束 2"备选内不写 prop 赋值"互相印证且更硬：不是"写了也要手写重写"，而是"写了非属性名直接构建失败"。
- 规则内全部备选都有 label 时，规则方法生成 `return (${rule.returnType})ctx.accept(this)`（模板 `_ASTBuildVisitor.java.xgen` 的 group+altLabel 分支）；规则返回类型取规则名推导（`sqlExpr_special` → firstPart(`_`)=`sqlExpr` → `SqlExpr`，**SqlExpr 类真实存在**于 `ast/`）。
- 子元素不加 prop 赋值时，生成 context 提供 `ctx.sqlExpr_bit()`/`ctx.sqlExpr()`/`ctx.FOR()` 等按规则名/token 名的访问器，手写 visit 方法用它们做归一化。
- **【执行期新增实测，第二条工具硬约束】`_`-后缀辅助规则名会被 `getInnerRule` 误选**：零 prop 备选里出现单下划线规则引用（如 `extractField_`）时，`AstGrammarBuilder.getInnerRule`（`notInAst()` 只查双下划线 `__`）会把它选为备选 innerRule，而其规则无 astNodeName ⇒ 生成 `public null buildXxx(...)` 编译错。**修正 = 辅助规则名取双下划线后缀**（`extractField__`，"不属抽象语法树"约定被 `getInnerRule` 显式跳过，innerRule 落到 `sqlExpr`，生成体被手写 visit 完全重写、永不执行）。
- duplicate-prop-label（`AstGrammarBuilder.java:524` 抛出点）是**同一备选内** prop 查重；跨备选同 prop 名会撞"NodeName_PropName"转换函数名。本裁定所有备选**零 prop 赋值**，该问题面整体不存在。

### 3.2 三条约束逐条落实确认

1. **label 以目标 AST 类名为前缀** ✅：`SqlCastExpr_cast`、`SqlRegularFunction_position`、`SqlRegularFunction_substring`、`SqlRegularFunction_extract`、`SqlRegularFunction_trimDirection`、`SqlRegularFunction_trimDirectionChars`、`SqlRegularFunction_trimChars`——全部推导至既有类 `SqlCastExpr`/`SqlRegularFunction`。
2. **备选内不写 prop 赋值，手写 visit 完全重写、不调 super** ✅：文法备选内所有子元素均不带 `x=` 赋值；生成的备选方法体只建空 `SqlRegularFunction` + `normalize()/validate()`（name 必填校验必失败，`_SqlRegularFunction.validate()` → `checkMandatory("name",...)`），手写 `EqlASTBuildVisitor` 逐一完全重写 `visitSqlRegularFunction_position/substring/extract/trimDirection/trimDirectionChars/trimChars`（WI1 `visitSqlIntervalExpr_string` 同款、javadoc 同款声明）。
3. **既有 `sqlCastExpr` 备选同步补 label** ✅：`sqlExpr_special` 首备选补 `# SqlCastExpr_cast`——规则内出现 label 备选后，规则级走 `ctx.accept(this)` 分派，无 label 备选会推导出规则名类 `SqlExpr` 与实际子节点 `SqlCastExpr` 冲突；补 label 后 cast 路径生成 `visitSqlCastExpr(ctx.sqlCastExpr())` 委托，与现状等价。

### 3.3 文法形态（定稿）

```
sqlExpr_special
    : sqlCastExpr # SqlCastExpr_cast
    | POSITION LP_ sqlExpr_bit IN sqlExpr RP_                        # SqlRegularFunction_position
    | SUBSTRING LP_ sqlExpr_bit FROM sqlExpr_bit (FOR sqlExpr_bit)?  # SqlRegularFunction_substring
    | EXTRACT LP_ extractField__ FROM sqlExpr RP_                    # SqlRegularFunction_extract
    | TRIM LP_ trimDirection__ FROM sqlExpr RP_                      # SqlRegularFunction_trimDirection
    | TRIM LP_ trimDirection__ sqlExpr_simple FROM sqlExpr RP_       # SqlRegularFunction_trimDirectionChars
    | TRIM LP_ sqlExpr_simple FROM sqlExpr RP_                       # SqlRegularFunction_trimChars
    ;

// 双下划线后缀 = AstGrammarBuilder notInAst 约定（见 §3.1 执行期新增硬约束）
extractField__: YEAR | MONTH | DAY | HOUR | MINUTE | SECOND | QUARTER;
trimDirection__: LEADING | BOTH | TRAILING;
```

- 首 arg 用 `sqlExpr_bit`（POSITION/SUBSTRING）：`IN`/`FROM` 是 `sqlExpr_predicate` 以上才出现的算子，`sqlExpr_bit` 保证首参不会吞掉分隔关键字——**文法层即无歧义**。
- `EXTRACT` 字段用独立 `extractField_` 规则（`intervalUnit_` 同款先例）：拒绝 WEEK/MICROSECOND 等无 lowering 目标的字段（解析层响亮），且不受 Phase 5 遮蔽放开影响（字段名位置只认这 7 个 token）。**被拒备选**：复用 `unreservedWord_`——会放行 CATALOG_NAME 等几十个非法字段名，把错误推迟到归一化层且错误信息变差。
- `TRIM` 三备选覆盖：方向无 chars / 方向+chars / chars 无方向（标准默认 BOTH）。**单参 `trim(x)` 不新增可解析入口**（Minor-②）：`TRIM(x)` 走既有 `sqlRegularFunction`（备选序在 sqlExpr_functionCall 中 sqlExpr_special 先行，但 `trim(x)` 对三个 TRIM 备选均在 `RP_` 前失配，回落 regular 路径——守护用例锁定单参行为与回显逐字不变）。
- 非法组合（`trim(leading from 'x' from y)`、`extract(foo from d)` 等）文法层失败，走 antlr 既有错误码。

### 3.4 POSITION `IN` 竞争登记（plan 点名必登）

- 竞争面：`POSITION(` 之后的 `IN`，既是特殊语法分隔符，也可能被既有 `sqlRegularFunction` 路径当作 `sqlExpr_predicate` 的 IN 谓词起点（`position(a in (b,c))` 两路都可完成解析：special → `instr((b,c), a)`；regular → args=[SqlInValuesExpr(a in (b,c))]）。
- 消解：ANTLR 自适应预测在两路均可行时取 `sqlExpr_functionCall` 内**备选序号最小者**，`sqlExpr_special`（第 2 备选）先于 `sqlRegularFunction`（第 3 备选）⇒ 恒取 special。行为确定，锁定用例：`position(a in (b,c))` 固定解析为 `SqlRegularFunction(name=instr, 2 args)`。
- `position(a in (b,c) in d)` 两路皆败 → antlr 解析错误（响亮），登记为已知边界（SQL 标准也不允许该嵌套）。
- 回显（`AstToEqlGenerator.visitSqlRegularFunction`）输出归一化后形态 `instr(...)`——四个函数的语料均不在 WI0 基线 7 文件中（grep=0），无 BC 风险；新语法自身落"输入→归一化回显→方言 lowering"三态断言。

## 4. TRIM 三段式语法设计（Decision，Major-2）

- 两档设计与注册面见 §1.4；方向语义映射：`BOTH`→`trim`/`trimChars`、`LEADING`→`ltrim`/`ltrimChars`、`TRAILING`→`rtrim`/`rtrimChars`。
- `LEADING/BOTH/TRAILING` token（SQL92Keyword.g4:478/:254/:664）以 `trimDirection_` 规则挂载，仅 TRIM 备选可达，不进入通用标识符空间（不与 Phase 5 遮蔽放开交叉）。
- **硬性禁令**：带 chars 形态在无模板方言上编译期显式报错（`ERR_EQL_UNKNOWN_FUNCTION`），不允许静默丢 chars、不允许降级单参 trim（roadmap 排序原则"只打印不设门=净回归"P1 头号禁令）。负向用例以 dm（或手构无注册方言）载体断言。
- **语义分叉登记**（约束 4"不等价"类）：标准 SQL 的 chars 参数是**字符集合**语义（H2/MySQL/PG/Oracle/DuckDB 均为集合语义）；部分库 `trim(s)` 无 chars 时只修剪空格——两件事分别在 owner doc 差异表落行。EQL 不做语义合并，原样 lowering。

## 5. EXTRACT 字段名规则与落地范围（Decision，Major-1）

- 默认归一化形态 = 逐字段函数（钉死不变）：7 字段 × §1.3 注册设计；字段规则 = 独立 `extractField_`（§3.3）。
- 与遮蔽放开 Phase 的依赖消解：`extractField_` 独立于 `unreservedWord_`，Phase 3（EXTRACT）与 Phase 5（遮蔽放开）**完全解耦**，先后不影响彼此行为；顺序按 plan 建议（EXTRACT 在前）。
- 逗号形态 `extract(f,d)`（14/15、mssql 缺）行为不变的守护用例：h2 上 `extract(o.name, 2)`... 不——逗号形态用 `extract(o.createTime, 'year')` 类输入不在本 plan 交付面，仅守护"h2 上逗号形态仍解析为 name=extract 的 SqlRegularFunction 且 lowering 输出不变"。mssql 缺口不修（登记）。
- 时区字段（TIMEZONE_HOUR/TIMEZONE_MINUTE）：无 token 无函数，不落（约束 4 分叉登记改为记录"SECOND 分数位差异"：oracle `extract(SECOND from ts)` 含小数 vs mysql `second(ts)` 0-59——owner doc 差异行）。

## 6. 负向错误码口径（Decision）

- **全部复用既有三码，不新起新码**：`ERR_EQL_UNKNOWN_FUNCTION`（方言未注册 lowering 目标——报错 funcName 是**归一化名**，天然区分"语法支持但方言未注册"与"完全未知函数"：前者报 `instr`/`trimChars` 等已知族名，后者报用户原文函数名）、`ERR_EQL_FUNC_TOO_FEW_ARGS`、`ERR_EQL_FUNC_TOO_MANY_ARGS`（`EqlTransformVisitor.java:1467-1476` 既有路径）。
- Phase 0 认为无需新码的理由：归一化后走的就是普通 `SqlRegularFunction` 通道，新码只能挂在 AST 形态上（如 `ERR_EQL_UNSUPPORTED_SPECIAL_SYNTAX`），但该形态在编译期已不可达（构建期必然归一化成功或 parse 失败）——新码无触发点，造出来就是死码。
- 解析期失败（非法组合/不支持字段）走 antlr 既有错误码（`nop.err.antlr.*`），同 WI1 先例（`TestEqlIntervalLiteralParse.testGrammarLevelFailureKeepsAntlrErrorCode`）。

## 7. 约束 4"一致性 + 不等价"两类用例清单

| # | 场景 | 一致性用例（各库同语义） | 不等价登记（owner doc 差异行素材） |
|---|---|---|---|
| 1 | SUBSTRING 负起点 | h2 2/3 参文本断言 | `default.dialect.xml` 自述"start 为负则相对末尾"（H2/MySQL 同）；MSSQL `SUBSTRING` 负 start 按 1 处理语义不同——落 owner doc 差异行 |
| 2 | TRIM chars 集合语义 | h2 模板渲染文本断言 | 标准=字符集合修剪；`trim(s)` 无 chars 时各库默认空格——落差异行（H2 明文 chars 为字符集；DuckDB `trim(string, characters)` 同集合语义） |
| 3 | EXTRACT 时区/秒字段 | h2 `year(d)` 文本断言 | oracle `extract(SECOND from ts)` 含小数秒 vs mysql `second(ts)` 0-59——落差异行；TIMEZONE_* 字段不落（§5） |
| 4 | POSITION 无此类分叉 | h2 instr 换序 + duckdb strpos 换名 | instr 起始下标各库均 1-based（oracle/h2/mysql 同），无需差异行 |

## 8. 真库执行证据面（EC ⑥ 落地口径）

- **CI 真库=H2**（`JdbcTestCase` 硬编码 `jdbc:h2:mem:`，`TestDialect.testFunctions` 遍历当前数据源方言函数表真库执行）。本 plan 新注册且会在 H2 执行的函数：`month/day/hour/minute/second/quarter`（default natives，h2 继承）+ `trimChars/ltrimChars/rtrimChars`（h2 模板）——各带 `<testSql>`，在 `TestDialect.testFunctions` 真库路径全绿即证据。
- **`[未取证]` 清单（不计覆盖率）**：`dm`、`tdengine`（红线：无可引用一手文档）；`db2`（IBM 文档 403/JS 门禁，与 roadmap 同纪律）；`es`（pseudo，非真库）。这四家：EXTRACT 字段函数经 default 继承获得注册（与既有 `year` 同等待遇、同样 unverified），TRIM chars 三函数**不注册**→响亮报错。
- **`[文档]` 清单（有第一手文档、无 CI 真库）**：mysql/mariadb/mssql/oracle/postgresql/duckdb（及继承者 mysql5.7/h2gis/postgis/oracle-reverse）——逐函数引证落 owner doc 覆盖表。
- instr/substr 既有注册在 15 方言的执行是 WI0 快照既有事实；本 plan 新增的是**解析归一化**，其方言文本输出由编译测试断言（h2/duckdb/mssql 三方言逐字文本）。

## 9. 与 plan 文本的差异登记（执行裁定对 plan 叙述的三处修订）

1. **可加载数 14→15**：WI3 已修复 db2（补 mandatory 子节点），快照/门禁实测 15 可加载（含 es pseudo）。覆盖计数口径随快照。
2. **Phase 5 的 day(x) 行为**：plan 起草期预期"五词 0/14 注册→响亮报错"；因 Phase 3 把 6 个字段函数作为 EXTRACT 组成部分注册（Non-Goals 明文例外），Phase 5 时五词在 15 方言注册、正常 lowering；未注册方言仍响亮。plan Phase 5 EC ② 括号条款已授权该口径。
3. **`EqlParser.java` 10904→10927 行**（WI2 平移）；其余行号锚点全部复核一致（§0 表）。

## 10. Phase 执行顺序与矩阵快照联动

Phase 1（POSITION，零增量）→ Phase 2（SUBSTRING，零增量）→ Phase 3（EXTRACT，+6×15 增量，同步快照）→ Phase 4（TRIM，+3×11 增量，同步快照）→ Phase 5（遮蔽放开，零注册增量）。每 Phase：红→绿测试 + `.g4` 单独提交 + 重生成 + `ast/_gen` 零 diff + 快照门禁 exit 0（有增量则先重生成快照）。
