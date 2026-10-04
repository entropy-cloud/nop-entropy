# WI7b Phase 0 裁定（plan 37 — 分析型四件套）

> Status: final（Phase 0 执行轮落档，2026-10-04）
> Source: `ai-dev/plans/37-eql-wi7b-analytic-features.md` Phase 0 checklist；`ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI7b 节 + 约束 1-4 + 红线（v7.2）
> 本文档只记录最终设计状态与裁定结论（含被拒备选及理由）；代码层面的类签名以源码为准。

## 0. 前置状态复核与漂移锚点复核（开工门记录）

**回退防护复核（2026-10-04 实测）**：

- plan 03（WI1）`Plan Status: completed` ✓（`ai-dev/plans/03-eql-wi1-class-a-defects.md` 头部）
- plan 26（WI7）`Plan Status: completed` ✓（`ai-dev/plans/26-eql-wi7-pagination-and-nulls.md` 头部，2026-10-04 独立 closure audit 通过收口）
- WI0 三件基础设施未回退：矩阵快照门禁 `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` **exit 0**（18 个 dialect XML / 18 快照登记；快照计 loadable 15 / non-loadable 3——与 roadmap v7.2 的"14/4"口径有出入，属 roadmap 记载滞后，快照与门禁为 live 对账口径，本 plan 不修 roadmap 该计数）；`TestEqlCorpusBaseline` 随模块测试运行（Phase 1/2 实跑核验）。

**差异登记第 5 条漂移锚点复核（全部符号定位命中，2026-10-04 实测）**：

| 锚点 | plan 记载 | 实测现位 |
|---|---|---|
| `sqlIdentifier_agg_` | BaseRule.g4:283-285 | `:283`（MAX/MIN/SUM/COUNT/AVG 五聚合硬编码）✓ |
| `sqlGroupByItem` | BaseRule.g4:426-428（单备选 `expr=sqlExpr`） | `:426-427` ✓ |
| `unreservedWord_` 含 NULLS | BaseRule.g4:103-105 区 | `:72` 起，NULLS 成员在块内 `| NULLS` ✓ |
| `Literals.g4` NULLS token | :29-31 | `:30`（定义于 `IDENTIFIER_` `:34` 之前）✓ |
| `checkWindowFrameFeature` | :375-397 | `:375`（三门合并方法）✓；`visitSqlWindowExpr` 的窗口名校验 `:405` |
| `visitSqlRegularFunction`（transform） | :1545-1572 | `:1545`；unknown-function 抛点 `:1548`；`onlyForWindowExpr` 放置门 `:1551-1556` ✓ |
| `EqlParser.java` 行数 | 11423 | **11423** ✓（无并行 plan 平移） |
| `ast/_gen/` 文件数 | 77 | **77** ✓ |
| `EqlASTKind` 尾部 | SqlRollback ordinal 66 | `SqlRollback // ordinal: 66` ✓（新类尾部追加 = ordinal 67） |
| `AstToEqlGenerator.visitSqlIntervalExpr` | :950-954 | `:950`（裸打 ` interval <expr> <UNIT>`）✓ |
| `AstToSqlGenerator` 无 `visitSqlIntervalExpr` override | grep 0 命中 | grep 0 命中 ✓（继承裸打印） |
| `dialect.xdef` `<sqlIntervalLiteral>` | :137 | `:137` ✓ |
| 生产方言 `sqlIntervalLiteral` 配置 | 0 命中 | 0 命中（18 个 XML）✓ |
| `rollup`/`cube`/`grouping` 保留 token | 0 | 9 个 `.g4` grep 0 命中 ✓ |
| 三份必测语料 rollup/cube/grouping | 0 命中 | 0 命中 ✓ |
| `grouping` 函数表注册 | 全 18 XML 0 注册 | 0 注册 ✓（`duckdb`/`tdengine`/`mysql` 三处 `<reservedKeywords>` 含该词样，非函数注册） |
| h2 FILTER 保留字 | h2.dialect.xml:45 | `:45` ✓ |

## 1. 裁定：`sqlIntervalLiteral` 占位符形态与渲染契约（Decision）

**选定 = 候选 A：命名 marker `{value}` / `{unit}`**（WI1 fixture 先例 `test-interval-literal.dialect.xml` 的 `interval {value} {unit}` 同款）。

- `{value}` 占位符接收 **value 子表达式的渲染后文本**（实施 = 嵌套 generator 捕获：`new AstToSqlGenerator(dialect, enableFilter, 独立 builder)` 后 `visit(node.getExpr())`，再取 buffer 文本——走真实渲染路径，接线证据比读字段更硬）。
- `{unit}` 占位符接收 `SqlIntervalUnit` 规范名（`node.getIntervalUnit().name()`，如 `DAY`——与既有裸输出 `print(node.getIntervalUnit().name())` 同口径）。
- **渲染输出 = 纯文本拼接**：value 已按方言渲染，模板只做字符串替换，**不得二次转义**（value 文本里的引号是字面量渲染的合法产物）。
- **加载期校验（落点 = `DialectImpl` 构造器）**：模板中出现的 `{name}` marker 若非 `value`/`unit`，在 `DialectImpl(DialectModel)` 构造期抛错——方言加载即失败，与 WI0 `TemplateSQLFunction` marker 越界校验同一纪律（"只打印不设门 = 净回归"）。**新错误码 `nop.err.dao.dialect.invalid-interval-literal-template`**（`DaoErrors.java` 手写接口 inline `define()`，零 codegen；新起码的理由 = 现有 `ERR_DAO_FUNC_TEMPLATE_MARKER_OUT_OF_RANGE` 语义是"数字索引越界"，命名 marker 无既有码可复用——WI0 为 function template 单独立码的同款判断）。坏 `}`/未闭合 `{` 按未知 marker 同口径处理（找不到闭合即抛）。
- **非字面量 value 行为（钉死 = 处置②，编译期显式报错）**：已配置模板的方言上，value 子表达式**非 `SqlNumberLiteral`**（如参数 marker `?`、列引用、算式）时编译期抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（`ARG_FEATURE="intervalNonLiteralValue"`）。理由：
  1. 两侧都响亮，但①（非字面量回落既有裸输出）意味着**已配置模板的方言继续产出探针已证非法的 `interval ? DAY` 文本**——这正是 roadmap 排序原则点名的"只打印不设门 = 净回归"形态：配置了模板却不设非字面量的门，等于把已知非法文本静默化。
  2. 两种既有语法形态（`interval 1 day` / `interval '1 day'`）的 value 在归一化后都是 `SqlNumberLiteral`（手写 `EqlASTBuildVisitor.visitSqlIntervalExpr_string` 显式构 `SqlNumberLiteral`；unit 形态经 `sqlExpr` 解析数字字面量同为 `SqlNumberLiteral`）——判定边界与真实可达输入精确重合，无误伤。
  3. 未配置模板的方言维持既有裸输出（含非字面量 value），BC 零变化（deprecated 语义不变，owner doc 既有注记不改）。
- **被拒备选 B**：`TemplateSQLFunction` 的 `{0}`/`{1}` 序号 marker——其语义是"按参数位置索引替换"，interval value 是子表达式而非位置参数，复用序号 marker 需把子表达式文本塞进位置参数模型，语义错配；且 `{0}` 形态对 `{value}`/`{unit}` 命名可读性差（模板是方言 XML 里的对外契约文本）。

## 2. 裁定：逐方言 `sqlIntervalLiteral` 模板内容矩阵（Decision，EC ③ 输入）

| 方言 | 模板 | 证据 |
|---|---|---|
| h2 | `INTERVAL '{value}' {unit}` | **[实测]** 2026-10-04 探针（`ai-dev/audits/eql-wi0/2026-10-04-h2-probe-transcript.txt`）：`INTERVAL '1' DAY` 执行成功；裸 `INTERVAL 1 DAY` 语法错（expected "-, +, string"）；`INTERVAL '1 DAY'` 数据错 22007 |
| h2gis | 继承 h2（`x:extends="h2.dialect.xml"`，`<sqls>` 子树随继承合并，零本地配置） | 继承 h2 [实测] |
| postgresql | `INTERVAL '{value}' {unit}` | [文档] PG 文档 interval 字面量接受 `INTERVAL '1' DAY`（引号 value + 裸单位关键字，SQL 标准形态） |
| postgis | 继承 postgresql | 继承 [文档] |
| duckdb | `INTERVAL {value} {unit}`（**显式覆盖**继承的 pg 模板） | [文档] DuckDB 文档首形态 `INTERVAL 1 YEAR`（value 不加引号；单复数单位均见文档）；不赌 duckdb 对引号 value+裸单位的接受度，显式落文档形态 |
| mysql | `INTERVAL {value} {unit}` | [文档] MySQL `INTERVAL expr unit`（value 不加引号——与 h2/PG 相反，plan 探针已证分叉真实存在） |
| mariadb | 继承 mysql | 继承 [文档]（MariaDB 语法同 MySQL `INTERVAL expr unit`） |
| mysql5.7 | 继承 mysql | 继承 [文档]（5.7 同为 `INTERVAL expr unit`） |
| oracle | `INTERVAL '{value}' {unit}` | [文档] Oracle 文档 `INTERVAL '1' DAY` 形态（DAY TO SECOND 单域字面量） |
| oracle-reverse | 继承 oracle | 继承 [文档] |
| mssql | **不配置** | [文档] T-SQL 无 `INTERVAL` 字面量（日期算术走 `DATEADD`）——无模板可配，维持未配置裸输出语义 |
| dm / tdengine | **不配置** | 红线 [未取证]（无可引用一手文档） |
| db2 | 不配置 | 不可加载（WI3 已收口另案），无配置面 |
| es | 不配置 | 伪方言，不入矩阵 |

- **h2 配置裁定 = 配置**（上表）：使 interval 模板消费获得 CI 真库端到端证据（探针已证形态合法）。`TestDialectSqlIntervalLiteral.testUnconfiguredProductionDialectReturnsNull` 的 null 断言**随 h2 配置同步更新为值断言**（plan 明文：这是预期更新点，不是不可触碰契约；原三测骨架不弱化）。
- 未配置方言（mssql/dm/tdengine）维持现输出 ` interval <expr> <UNIT>`（deprecated 语义不变，owner doc `:295` 注记保留），**不引入新报错**（BC）。

## 3. 裁定：rollup/cube 路径 = ⓒ-A（新增 AST 节点 + 文法备选），文法形态与备选顺序（Decision）

**选定 = 候选 ⓒ-A**（plan 推荐项，无翻案证据：`ast/_gen` 成本为既定 ⓒ 定价，无失控实测）。

### 3.1 AST/文法形态

- **新增 1 个 AST 类 `SqlGroupingOp`**（`extends SqlExpr`；`type: SqlGroupingOpType`（ROLLUP|CUBE）+ `args: List<SqlExpr>`）——GROUP BY 专用 grouping 操作节点。命名与 `grouping()` 函数区分：类名 `GroupingOp` 指 ROLLUP/CUBE 语法操作符本身。
- **新增 1 个 enums 类 `SqlGroupingOpType`**（`io.nop.orm.eql.enums`，`ROLLUP, CUBE` 两值 + `fromText` 大小写不敏感工厂——`SqlWindowFrameType` 同款先例）。
- **`Literals.g4` 新增 2 个 token**：`ROLLUP`、`CUBE`（fragment 组合大小写不敏感，NULLS 同款三件套：定义在 `IDENTIFIER_` **之前**（lexer 优先级=定义序）+ `unreservedWord_` 双登记（保住列名/别名 `rollup`/`cube` 可解析）+ 语料 0 命中 BC 论证（已实测 0 命中）。新增 2 token、其后 Literals token 序号顺延、**零删除**。
- **`BaseRule.g4` `sqlGroupByItem` 备选改造**（label 三约束按 WI5 纪律）：
  - 新备选**前置**：`ROLLUP LP_ functionArgs_? RP_ # SqlGroupByItem_rollup` 与 `CUBE LP_ functionArgs_? RP_ # SqlGroupByItem_cube`；
  - 既有备选补 label：`expr=sqlExpr # SqlGroupByItem_expr`（多备选规则全 label 是 ANTLR 硬要求；既有 `sqlExpr` 规则四 label 备选即同款活先例——label 类名可与规则名不同，生成 visit 方法返回 label 类）。
  - **生成物消费模式（实测先例）**：`# SqlGroupByItem_rollup` 生成 `_EqlASTBuildVisitor.visitSqlGroupByItem_rollup(...)`，手写 `EqlASTBuildVisitor` override 之，构建 `SqlGroupByItem`（既有类，`items` 列表类型零改动）其 `expr` = 新建 `SqlGroupingOp`（wrapper 模式——`visitSqlIntervalExpr_string` 同款：label 备选内零 prop 赋值、构建全部在手写 visit 完成）。`buildSqlGroupByItems_` 的 `visitSqlGroupByItem(elm)` 调用经 ANTLR `accept()` 多态分发到 label 方法——混合项 `GROUP BY a, ROLLUP(b,c)` 天然支持（同一 items 列表内逐项分发）。
- **备选顺序语义（Major-3 钉死）**：`ROLLUP(a,b)` 在 GROUP BY 位置同时匹配新备选与既有 `expr=sqlExpr → sqlRegularFunction` 备选，ANTLR 歧义由备选顺序决定——**新备选前置，新节点胜出**；排后即死代码。
- **守护用例（双路径胜者）**：`group by rollup(g)` 在不支持方言（h2）上必须抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（= 走了 SqlGroupingOp → 新门），**不得**抛 `ERR_EQL_UNKNOWN_FUNCTION`（= 旧 SqlRegularFunction 路径胜出的死代码症候）。一条用例同时锁顺序与接线。

### 3.2 方言能力门（新特性标志）

- **新增 2 个方言特性标志 `supportRollup` / `supportCube`**（`<features>` 属性；nop-dao 零 codegen——`dialect.xdef` 声明 + 手写 retention `DialectFeatures` + `IDialect`/`DialectImpl` accessor，roadmap Cross-Cutting 第一行口径）。分立两标志而不合一的理由：mysql 的函数式 ROLLUP（8.4.3+）与 CUBE（永无）**successor 触发条件不同**（基线上调 vs 永久否决），单标志会在基线上调时误开 CUBE。
- 默认值（`default.dialect.xml`）= `false` 显式登记（保守 fail-closed：dm/tdengine 等继承方言自动落入报错侧）。显式 `true` 落：postgresql（postgis/duckdb 继承）、oracle（oracle-reverse 继承）、mssql。显式 `false` + 注记落：h2（探针 [实测]）、mysql（8.4.3+ successor 注记）、mariadb（无函数式 ROLLUP/CUBE 注记）。
- **消费点**：`EqlTransformVisitor` 新增 `visitSqlGroupingOp(SqlGroupingOp)`——按 `type` 分派 `isSupportRollup()`/`isSupportCube()`，false 抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（`ARG_FEATURE` = `rollup`/`cube`）；true 则 `visitChildren(args)`。错误码复用族（plan 口径，不新起）。
- **打印**：`AstToEqlGenerator.visitSqlGroupingOp` 打印 `ROLLUP(a,b)` / `CUBE(a,b)` 大写规范形态；`AstToSqlGenerator` 继承（identity lowering，无方言分叉——SQL 文本由能力门保证合法性）。

### 3.3 非 GROUP BY 位置报错口径（Major-3-i 显式确认）

ⓒ-A **不获得文法级放置排他**：`select rollup(a) from t`（非 GROUP BY 位置）仍经 `unreservedWord_ → sqlIdentifier_ → sqlIdentifier_func_ → sqlRegularFunction` 解析为 `SqlRegularFunction`；`rollup`/`cube` **永不进入任何方言函数表** ⇒ transform 层 `dialect.getFunction("rollup")` 为 null → `ERR_EQL_UNKNOWN_FUNCTION` 编译期响亮。负向用例断言该错误码。机制依赖链：文法备选只决定 GROUP BY 位置的胜者；放置排他来自"GROUP BY 之外无注册"。

### 3.4 被拒备选（记录）

- **ⓓ（AST transformer 改写）**：零文法/零 `ast/_gen` 触碰，但报错时机后移（parse 后才响亮）、transformer 错误码归属牵强（`SqlRegularFunction` 名义下的 ROLLUP 语义）、语义藏在改写层不利于放校验与矩阵对账。保留为被拒备选，非 successor。
- **① 纯 `<functions>` XML 注册 rollup/cube**：约束 2 明文（放置不可约束）+ `select rollup(a) from t` 非法 SQL 推迟到执行期。
- **② grouping 走 `sqlExpr_special` altLabel**：新增第二个可解析入口违反 WI5 纪律 + 无 GROUPING token 则备选写不出（NULLS 教训）。grouping 已单独裁定（§4）。

### 3.5 成本清单（ⓒ-A 实价）

新增 1 AST 类（`EqlAST.xjava` + `ast/_gen` 77→78 文件重写 + 新类 ordinal **尾部追加 = 67**（`SqlRollback` 66 之后），既有 ordinal 零移动——落点登记于此）+ 1 新 enums 类 + 2 新 token（`ROLLUP`/`CUBE`，Literals.g4 尾部区新增、其后 token 序号顺延、零删除）+ `EqlParser.java`/`_EqlASTBuildVisitor.java`/`EqlLexer*`/`Eql.interp`/`*.tokens` 重生成 + `./mvnw clean install -T 1C` 必过门。

### 3.6 `GROUPING SETS` 否决再确认

维持 roadmap ledger 永久否决（MySQL/MariaDB 全版本均无）——不注册、不建语法位（`SqlGroupingOpType` 仅 ROLLUP|CUBE 两值，文法备选仅两条，无第三入口）。

## 4. 裁定：`grouping` 落地形态（零文法改动，Decision）

- **零文法/零 token/零 Java 门控**：`grouping` 今天已经 `IDENTIFIER_ → sqlIdentifier_ → sqlIdentifier_func_ → sqlRegularFunction` 解析为 `SqlRegularFunction`。落地 = **仅函数表注册**：
  - `<native name="grouping" realName="GROUPING" minArgCount=".." maxArgCount="..">` 落 `postgresql.dialect.xml`（postgis/duckdb 继承）、`oracle.dialect.xml`（oracle-reverse 继承）、`mssql.dialect.xml` 三处——渲染经既有 `NativeSQLFunction.buildFunctionExpr`（`realName(args)`），元数经既有 `EqlTransformVisitor.visitSqlRegularFunction` 的 min/max 校验通路（`ERR_EQL_FUNC_TOO_FEW_ARGS`/`TOO_MANY_ARGS`）。
  - **注册范围裁定 = 与 ROLLUP/CUBE 能力集重合**（pg/postgis/duckdb/oracle/oracle-reverse/mssql）：`GROUPING()` 只在含 super-aggregate 行的查询里有语义，而 EQL 恰好仅在该集合上能产出 `ROLLUP(...)`/`CUBE(...)`——在此集合之外注册是**常量 0 死能力**（mysql：函数式 ROLLUP 超基线 8.4.3+、`WITH ROLLUP` 不注册 ⇒ EQL 无法为其生成 super-aggregate 行；mariadb：连函数式 ROLLUP 都无）。mysql/mariadb 矩阵格 = "不注册（DB 事实 [文档] 有 GROUPING，但 EQL 场景死能力）"，未配置 → 既有 `ERR_EQL_UNKNOWN_FUNCTION` 响亮兜底。这也使 mysql5.7 无需 `x:override="remove"`（mysql 本就不注册，无继承污染）。
  - **真快照增量确认**：现全 18 方言 XML 函数表 `grouping` 0 注册（实测），三处注册 = 快照真实 +1×3（含 duckdb/postgis/oracle-reverse 继承展开后的函数清单增量）。
- **无 `onlyForWindowExpr` 式新门**：`onlyForWindowExpr` 先例解决的是"函数只在特定父节点合法"的放置问题；`grouping()` 在 SELECT/HAVING/ORDER BY（及 GROUP BY 外的表达式位）语法上均合法（其参数是否引用 GROUP BY 项是语义层），方言差异已由"注册与否"表达——**不需要按函数名的新门控代码**。plan 所述"按函数名门控"在本裁定下退化为"函数表即门"（未注册方言 unknown-function 编译期响亮）。
- **语义级放置校验（仅引用 GROUP BY 项）= 不做**：理由——①未注册/不支持场景已被编译期 unknown-function + arg-count 双闸兜住，无静默风险；②"参数表达式与 GROUP BY 项精确匹配"需要表达式等价比较（别名、属性导航展开），成本高且误判面大；③防范围蠕动（plan 明文可选项）。登记于此，若未来出现真实需求先登记 roadmap。
- **元数取证（per-dialect min/max）**：

| 方言 | 元数 | 证据 |
|---|---|---|
| postgresql / duckdb | 1..64 | [文档] PG 15 文档 `GROUPING ( group_by_expression(s) )` 位掩码多参（"Bits are assigned with the rightmost argument..."）；DuckDB `grouping()` 同族位掩码语义 |
| oracle / oracle-reverse | 1..1 | [文档] Oracle 23ai 文档 `GROUPING(expr)` 单参（"The expr ... must match one of the expressions in the GROUP BY clause"） |
| mssql | 1..1 | [文档] Microsoft Learn `GROUPING ( <column_expression> )` 单参、返回 tinyint（多列用 `GROUPING_ID`——本 plan Non-Goal） |

- mysql（8.0+ `GROUPING(expr[, expr]...)`）与 mariadb（KB 记载 `GROUPING(expr)`，GitBook 改版后直接取证不可达、仅搜索引擎渲染二手）= **不注册**（见上），元数不进入配置，事实登记于 owner doc 矩阵。

## 5. 裁定：三函数方言矩阵逐格结论（Decision，EC ①⑦ 输入）

证据等级：`[实测]` = 本 workspace 真库探针（`ai-dev/audits/eql-wi0/2026-10-04-h2-probe-transcript.txt`，H2 2.3.232/2.4.240 × DEFAULT/`MODE=Oracle`/`MODE=PostgreSQL` 全矩阵）；`[文档]` = 一手厂商文档；`[未取证]` = 红线（不计覆盖率，一律编译期报错）。

| 方言 | ROLLUP(...) | CUBE(...) | GROUPING() |
|---|---|---|---|
| h2 | 显式报错（supportRollup=false；[实测] `Function "ROLLUP" not found` 90022 ×6 配置全矩阵） | 显式报错（supportCube=false；[实测] 同上） | 报错（函数表不注册；[实测] `Function "GROUPING" not found` 90022） |
| h2gis | 继承 h2 = 报错 | 继承 = 报错 | 继承 = 报错 |
| mysql | 显式报错（supportRollup=false；[文档] 函数式 ROLLUP 8.4.3 才有，超 G-1 基线 8.0.31；successor = 基线上调需求走 G-1 门再登记 roadmap） | 显式报错（supportCube=false；[文档] MySQL 全版本无 CUBE，ledger 永久口径） | 不注册 → 报错（[文档] 8.0+ 有 GROUPING；EQL 无法生成 super-aggregate 行 ⇒ 死能力不注册，§4） |
| mysql5.7 | 继承 mysql = 报错 | 继承 = 报错 | 不注册 → 报错（[文档] 5.7 无 GROUPING） |
| mariadb | 显式报错（supportRollup=false；[文档] 无函数式 ROLLUP） | 显式报错（supportCube=false；[文档] 无 CUBE，ledger 永久口径） | 不注册 → 报错（[文档-二手] KB 有 GROUPING 记载；死能力不注册，§4） |
| postgresql | 注册（supportRollup=true；[文档]） | 注册（supportCube=true；[文档]） | 注册 1..64（[文档]） |
| postgis | 继承 postgresql = 注册 | 继承 = 注册 | 继承 = 注册 |
| duckdb | 继承 postgresql = 注册（[文档] DuckDB 支持 ROLLUP/CUBE） | 继承 = 注册 | 继承 = 注册 1..64 |
| oracle | 注册（supportRollup=true；[文档] 23ai ROLLUP） | 注册（supportCube=true；[文档] 23ai `CUBE`） | 注册 1..1（[文档] 单参） |
| oracle-reverse | 继承 oracle = 注册 | 继承 = 注册 | 继承 = 注册 |
| mssql | 注册（supportRollup=true；[文档] `GROUP BY ROLLUP` learn.microsoft.com） | 注册（supportCube=true；[文档] `GROUP BY CUBE`） | 注册 1..1（[文档] `GROUPING ( <column_expression> )`，2026-09-21 页面版本一手核验） |
| dm | 报错（继承 default=false；[未取证] 红线，不计覆盖率） | 报错（同） | 报错（不注册；[未取证]） |
| tdengine | 报错（同上红线） | 报错 | 报错 |
| db2 | 不可加载（无函数表 → unknown-function） | 同 | 同 |
| es | 伪方言，不入矩阵 | — | — |

- `CUBE` 在 MySQL/MariaDB 显式编译期报错用例 = EC ① 硬性，载体 = `supportCube=false` 门（两方言各一条断言 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`）。
- **真库证据分派（EC ⑦）**：h2 = 负向载体（探针 [实测] 归档 + 负向用例三函数各一）；`TestDialect.testFunctions`（真库遍历当前数据源方言函数表）不覆盖三函数——h2 未注册三函数（注册即在真库执行期炸，探针 90022 已证），该测试对三函数的缺席**正是正确行为**；oracle/postgresql/duckdb/mssql = 一手文档引证（§4/§5 表）+ 文本断言替代证据；dm/tdengine = `[未取证]`，不计覆盖率。

## 6. 探针重跑与 transcript 持久化（Proof）

- 重跑脚本 `_tmp/h2probe/run-probe.sh`（v2——**每条 SQL 与 DDL 同一 Shell 调用**：v1 教训，`org.h2.tools.Shell` 每次调用是独立 JVM，mem 库随连接关闭消亡，跨调用建表查表只会得到 `Table "T" not found`，什么也证不了）。
- 归档：`ai-dev/audits/eql-wi0/2026-10-04-h2-probe-transcript.txt`（命令行 + 完整输出，90 组）+ `ai-dev/audits/eql-wi0/2026-10-04-h2-probe-run.sh`（可复跑脚本）。
- 结论与 plan Current Baseline 一致，无翻案：三函数 × 2 版本 × 3 模式全部 `Function "ROLLUP"/"CUBE"/"GROUPING" not found`（90022）；`INTERVAL '1' DAY` 成功 / 裸 `INTERVAL 1 DAY` 语法错 / `INTERVAL '1 DAY'` 数据错 22007（2.4.240 与 2.3.232 同行为）。Closure Gates 与 owner doc 引用以该归档为准。

## 7. 裁定：窗口半区缺口判定清单（Decision，EC ② 输入）

| EC ② 要求 | 既有覆盖 | 判定 |
|---|---|---|
| `over(…)` 带 partition 正确 SQL 文本 | `TestEqlWindowGrammarParse`（parse）+ `__snapshot/{dialect}.window.sql` 10 方言 golden（含 over/window/frame/fail-fast 四组文本断言）+ `TestH2WindowFrameMatrix`/`TestH2WindowFrameCompileEndToEnd`（真库） | 待 Phase 3 逐文件核对 golden 是否含带 partition 形态；预判已满足 |
| `over(order by …)` 不带 partition | `TestEqlWindowGrammarParse.testOverOrderByOnly`（parse 层 ✓） | **文本断言缺口候选**：golden 是否有不带 partition 的文本用例待 Phase 3 核对，缺口则补 |
| frame（rows/range/groups、between/单 bound） | parse + golden + H2 真库五组（rows/range/命名窗口 rows/range/命名无 frame） | 预判已满足；Phase 3 核对"不带 partition 仅 frame"边角形态 |
| roadmap 回写措辞 | — | Phase 3 按 plan 原地注记先例执行（注记措辞：定价已被 `add-stream-sql` 合并越过，五 AST 节点 + 文法全备选 + 三道 frame 门 + 10 方言 golden + H2 真库测试；门控矩阵 Protected Area 单元格注记"窗口半区零 .g4/EqlAST.xjava 触碰，实际生成管线触碰面以 Phase 2 ⓒ-A 为准"；后继 WI12 读取口径） |

## 8. 裁定：`count(*) FILTER` 文档小节大纲（Decision，EC ④ 输入）

1. 引导写法：`sum(case when p then 1 else 0 end)`（等价 `count(*) FILTER (WHERE p)` 的计数语义）。
2. 空分组提醒：无行满足条件时 `sum(...)` 返回 **NULL** 而 `count(*) FILTER` 语义返回 **0**——需要 0 语义时套 `coalesce(sum(...), 0)`。
3. 三态矩阵（roadmap ledger + H2 探针旁证）：已确认有 = PG/DuckDB/SQLite；已确认无 = MySQL/MariaDB/MSSQL/Oracle（其 3 处 `FILTER` 均为 analytic view 的 `FILTER FACT`）；未取证 = DB2（IBM 文档 403/JS 门禁）、TDengine（红线）、H2（h2database.com 五页零 FILTER，仅 `h2.dialect.xml:45` 保留关键字 + 本 plan 探针旁证）。
4. "为什么不进语法"：约束 3 引用（无法跨多数方言无损 lowering——MySQL/MariaDB/MSSQL/Oracle 全无，做了就是把多数用户推向编译期报错的语法糖）。

## 9. 约束 4"一致性 + 不等价"两类用例清单（Decision）

| 素材 | 一致性（同语义同文本） | 不等价（能力/形态边界） |
|---|---|---|
| interval 模板 | 同一方言内 `interval 1 day` 与 `interval '1 day'` 归一化后模板渲染输出一致（Phase 1 用例） | 同一 EQL 输入在不同方言输出形态不同：h2/PG/Oracle = `INTERVAL '1' DAY`（引号），mysql/duckdb = `INTERVAL 1 DAY`（裸值）——Phase 1 三形态断言即不等价证据 |
| rollup/cube | 支持方言（pg/duckdb/oracle/mssql）输出 `ROLLUP(g)`/`CUBE(g)` 原文规范形态（Phase 2 正向用例） | 同一查询在支持方言输出 vs 不支持方言（h2/mysql/mariadb/dm）编译期报错——Phase 2 负向用例对照 |
| FILTER 引导 | 引导写法 `sum(case when p then 1 else 0 end)` 在全方言可编译可执行（普通 case 聚合，无门） | 原生 FILTER 的空分组 0 语义 vs 引导写法空分组 NULL 语义——owner doc 小节差异表行（文档形式登记） |

## 10. ordinal / enums 成本登记（ⓒ-A）

- `EqlASTKind` 新常量 `SqlGroupingOp` **尾部追加，ordinal = 67**（现 max = `SqlRollback` 66），既有 67 个 ordinal 零移动（红线"禁止持久化/比较 ordinal"原文不触）。
- `ast/_gen/` 77 → **78 文件**（新增 `_SqlGroupingOp.java` + 既有 77 文件重生成 diff）。
- `Literals.g4` +2 token（`ROLLUP`/`CUBE`，NULLS 区块后顺延新增），其后 Literals token 序号 +2 顺延；`EqlLexer`/`.tokens`/`Eql.interp` 同批重生成；零 token 删除。
- 新 enums 类 1 个（`SqlGroupingOpType`）。
- `./mvnw clean install -T 1C` 必过（codegen 全链路 + 114 个 orm.xml 回归）。
