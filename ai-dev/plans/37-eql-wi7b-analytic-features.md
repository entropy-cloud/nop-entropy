# 37 EQL WI7b — 分析型四件套：`sqlIntervalLiteral` 模板落地 + `rollup`/`cube`/`grouping` 注册 + 窗口 OVER/frame 现状核验收口 + `count(*) FILTER` 文档引导

> Plan Status: active
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI7b 节 Exit Criteria ①-⑧ + 共同约束 1-4 + 门控矩阵 WI7b 行 + WI7 行的 2026-10-04 收口注记 + 「明确延后项」`GROUPING SETS`/`CUBE`/`ROLLUP(...)`/`GROUPING()`/`count(*) FILTER` 三行 + Cross-Cutting 全部 + 关键约束与红线节，v7.2）；Current Baseline 全部代码事实于 2026-10-03 在本工作区分支 `feat/eql-cross-db-compat` 逐文件实测核验，H2 行为经真库执行探针取证（`_tmp/h2probe`，H2 2.3.232 与 2.4.240 双版本）；行号为当日实测参考值、执行时以 Phase 0 锚点复核为准
> Related: `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`（硬前置，已 `completed`；矩阵快照棘轮 + 语料基线棘轮直接消费）、`ai-dev/plans/03-eql-wi1-class-a-defects.md`（硬前置，已 `completed`；`sqlIntervalLiteral` 骨架的移交 owner——本 plan 是"填内容"的另一半）、`ai-dev/plans/26-eql-wi7-pagination-and-nulls.md`（硬前置，**已于 2026-10-04 `completed`**——独立 closure audit 一次通过、roadmap Work Item Status 的 WI7 行已翻 `done`，排队门已满足；NULLS token 执行期修订是本 plan 文法方案的关键先例）、`ai-dev/plans/24-eql-wi5-sql92-function-syntax.md`（结构先例：altLabel 三约束 / 真库证据分派 / 矩阵快照增量模式 / 每 Phase 开工前置复核）、`ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md`（已 `completed`——路径 ⓓ 管线可用性的证据来源）

## 硬前置声明（开工门，非空话）

roadmap 门控矩阵 WI7b 行明文硬前置 = **WI1、WI7**。实测现状（2026-10-04 修订时复核）：WI1 = plan 03 **已 `completed`**；WI7 = plan 26 **已于 2026-10-04 `completed`**（独立 closure audit 一次通过，roadmap Work Item Status 的 WI7 行已翻 `done`）。⇒ **两项硬前置全部满足，排队门已打开：本 plan 的全部 Phase（含代码 Phase）均可开工**（起草轮的"Phase 1+ 排队待 plan 26"门随 plan 26 收口消解，照 plan 26 差异登记第 6 条的消解先例处理）。保留机制（回退防护）：每次开工一个 Phase 前，执行者必须复核 plan 03 与 plan 26 的 `Plan Status` 未回退（若出现回退——例如 plan 被重开或 audit 发现 only-partial landing——则恢复排队纪律并记录于当次 daily log）。

**本 plan 与 WI5/WI6/WI8 无依赖**：roadmap 依赖图中 WI7b 的上游只有 WI1（直连）与 WI7（经 `WI7 --> WI7b`），下游指向 WI12。plan 21（WI4）的 completed 状态只作为路径 ⓓ 可用性的证据，不构成本 plan 的排队门。

## Purpose

收口 roadmap WI7b 的四个结果面：

1. **`sqlIntervalLiteral` 模板落地**（BUG-5 的另一半）：WI1 已建配置通路骨架（xdef 元素 + retention + accessor + fixture 测试），逐方言模板内容、占位符形态、生成器消费点由**本 plan 唯一 owner** 收口——交付 PG/Oracle/MySQL 三形态正确输出用例（EC ③）+ H2 端到端真库执行证据，让 `interval` 字面量在已配置模板的方言上不再输出 PostgreSQL/H2 非法的裸 `interval 1 DAY` 文本。
2. **`rollup`/`cube`/`grouping` 注册**：rollup/cube 按约束 2 的路径裁定（ⓒ 新 AST 节点+文法 vs ⓓ AST 改写）落地 GROUP BY 位置语义、方言能力门（`CUBE` 在 MySQL/MariaDB 上显式报错）与 lowering；`grouping` **零文法改动**——函数表注册 lowering + `visitSqlRegularFunction` 按函数名门控（约束 2 只约束 `sqlIdentifier_agg_` 五聚合，grouping 不经该通路）。覆盖矩阵按方言名列全（EC ①），不可真库验证的方言逐个列明替代证据（EC ⑦）。
3. **窗口 OVER/frame 现状核验收口**（EC ②）：roadmap WI7b 行"窗口 `OVER` 放宽（今天 `over(order by …)` 不带 partition 就失败）+ frame（需 `SqlWindowExpr` 增字段）"的定价**已被 `add-stream-sql` 合并带入的能力整体越过**（实测 `over(order by b)` 今天可解析、`SqlWindowExpr.frame` 字段已存在且文法/门/输出/测试齐备）——本 plan 对 EC ② 做覆盖审计 + 缺口补齐 + roadmap WI7b 行定价回写（照 plan 26 Phase 4 的 roadmap 回写先例）。
4. **`count(*) FILTER` 文档引导**（EC ④）：`sum(case when p then 1 else 0 end)` 引导 + "空分组下 `sum` 返回 NULL 而 `count` 返回 0"提醒写入 owner doc——**文档引导 only，不做语法**（roadmap ledger 明文）。

每个代码结果面具备：断言正确方言 SQL 文本的正向用例（红→绿）、不支持方言编译期响亮报错的负向用例（无静默降级）、既有 EQL 语料 BC 棘轮逐字一致、owner doc 同步。

## Current Baseline

> 所有锚点为符号定位优先；file:line 为 2026-10-03 实测参考值。⚠️ 并行 plan（WI9b 等）的文法提交会整体平移 `nop-persistence/nop-orm-eql/model/antlr/*.g4` 行号与 `EqlParser.java` 行数——执行时以 Phase 0 的锚点复核为准。

### 一、`sqlIntervalLiteral` 骨架（WI1 已落地，本 plan 直接消费）

- **xdef**：`dialect.xdef` 的 `<sqls>` 元素（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef:103`）下已有 `<sqlIntervalLiteral>string</sqlIntervalLiteral>` 子元素（`:137`，与 `<dateLiteral>`/`<timeLiteral>`/`<timestampLiteral>` 同构注释风格）。
- **retention**：`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/DialectSqls.java`（手写 retention 子类，`nop-dao` 无 codegen 管线故 `_gen` 冻结；`sqlIntervalLiteral` 字段 `:19`、getter/setter `:34-40`；javadoc 明文"逐方言模板内容归 roadmap WI7b（唯一 owner）"）。
- **accessor**：`IDialect.getSqlIntervalLiteral()`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java:293-303`，javadoc 明文"占位符形态与逐方言模板内容由 WI7b 收口（唯一 owner）；WI1 只承接配置通路，不消费模板改写 interval 输出"）+ `DialectImpl.getSqlIntervalLiteral()`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java:329-333`，直读 `<sqls>` 配置文本、未配置返回 null）。
- **fixture 测试**：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestDialectSqlIntervalLiteral.java` 三测——fixture 方言 `test-interval-literal`（`nop-persistence/nop-orm-eql/src/test/resources/_vfs/nop/dao/dialect/test-interval-literal.dialect.xml:8`，值 `interval {value} {unit}`）经 xdef 反射绑定往返（`:44-51`）；**生产方言 h2 未配置返回 null（`:53-58`，断言名 `testUnconfiguredProductionDialectReturnsNull`——这是 WI7b 给 h2 配模板后的预期更新点，不是不可触碰的契约）**；手构模型双证（`:60-79`）。
- **生产配置现状**：18 个 `*.dialect.xml` 中 `sqlIntervalLiteral` **0 配置**（grep 0 命中）——模板内容是真缺口。
- **语法侧（WI1 已交付）**：`interval 1 day` 既有形态 + `interval '1 day'` PG 形态（`sqlIntervalExpr` 的 `INTERVAL STRING_` 备选，构建期归一化 `EqlASTBuildVisitor.java:379` 起、单位解析 `:223-227`）都归一化装入既有 `SqlIntervalExpr`（value + intervalUnit），`ast/_gen/` 零 diff。既有回归：`TestEqlIntervalLiteralParse`。
- **输出侧现状（BUG-5 未修的另一半 = 本 plan 的消费点）**：`AstToEqlGenerator.visitSqlIntervalExpr`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java:950-954`）裸打 ` interval <expr> <UNIT>`；**`AstToSqlGenerator` 无 `visitSqlIntervalExpr` override**（grep 0 命中，继承同一打印）——即执行 SQL 对所有方言都输出 `interval 1 DAY` 形态。⚠️ 实测该形态在 **PG 上非法**（BUG-5 原判）且在 **H2 上同样非法**（见下条探针）——不只 PG 一家受损。
- **TUMBLE 路径隔离（scope guard）**：`AstToSqlGenerator.visitSqlTumbleTableSource`（`AstToSqlGenerator.java:253-261`）对 stream-only 的 TUMBLE 伪表函数**刻意逐字渲染** `INTERVAL <expr> <unit>`（javadoc 明言"deliberately NOT valid RDBMS SQL"）——本 plan 的模板消费**不得触碰 TUMBLE 渲染路径**（其 owner 是 nop-stream-sql roadmap 的 WI20）。

### 二、H2 行为真库探针（2026-10-03，`_tmp/h2probe`，`java -cp` 直跑 `org.h2.tools.Shell`，无 maven）

- **INTERVAL 形态**（H2 2.4.240）：`TIMESTAMP '…' + INTERVAL '1' DAY` → **成功**；`+ INTERVAL 1 DAY` → **语法错**（"expected '-, +, string'"——H2 要求 INTERVAL 后跟字符串）；`+ INTERVAL '1 DAY'` → **数据错**（"Cannot parse INTERVAL constant"）。⇒ H2 的正确模板形态是 `INTERVAL '{value}' {unit}` 一类（value 加引号、unit 裸关键字）；与 MySQL 的 `INTERVAL 1 DAY`（value 不加引号）相反——逐方言模板内容不可一概而论，EC ③ 的"三形态"有真实分叉。H2 2.3.232 同行为。
- **ROLLUP/CUBE/GROUPING**（H2 2.3.232 与 2.4.240 双版本 + `MODE=Oracle`/`MODE=PostgreSQL` 兼容模式）：`GROUP BY ROLLUP(g)` / `GROUP BY CUBE(g)` / `GROUPING(g)` 全部 **`Function "ROLLUP"/"CUBE"/"GROUPING" not found`（错误 90022）**。⇒ **H2 无三函数**（jar 内 class 全量扫描亦无这些字样——H2 关键字以常量存储，字符串扫描仅作旁证，真库执行错误为准）。这是对起草输入"rollup H2 有——取证"的**实测否证**：H2 在三函数矩阵中属"确认无"而非"支持"，直接决定 EC ⑦ 的真库证据分派（见差异登记第 2 条）。
- **探针 transcript 持久化要求（独立审查 Minor-①）**：上述结论的命令行与完整输出由起草轮独立复跑留痕，行内仅为摘要——**三函数 × H2 2.3.232/2.4.240 × `MODE=Oracle`/`MODE=PostgreSQL` 全部 `Function "ROLLUP"/"CUBE"/"GROUPING" not found`（90022）；`INTERVAL '1' DAY` 执行成功 / 裸 `INTERVAL 1 DAY` 语法错（expected "-, +, string"）/ `INTERVAL '1 DAY'` 数据错（22007）**。**Phase 0 重跑探针并把 transcript（命令行 + 完整输出）持久化到 `ai-dev/audits/eql-wi0/`**（新归档文件）；Closure Gates 与 owner doc 引用以该归档为准，不以本 plan 行内摘要为准。

### 三、窗口 OVER/frame 现状（关键：roadmap WI7b 行定价已被合并越过，2026-10-03 实测）

- **五 AST 节点全部已存在**（`add-stream-sql` 合并带入，roadmap WI12 第 4 项同源）：`SqlWindowClause`/`SqlWindowDecl`/`SqlWindowExpr`/`SqlWindowFrame`/`SqlWindowFrameBound`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/`）。**`SqlWindowExpr` 已有 `frame` 字段**（`ast/_gen/_SqlWindowExpr.java:19-27`：`frame`/`function`/`orderBy`/`partitionBy`/`windowName`）——roadmap"需 `SqlWindowExpr` 增字段"的前提前置**已不成立**。
- **文法**：`sqlWindowExpr`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:233-238`）= `function=sqlWindowFunction_ OVER ( LP_ partitionBy=sqlPartitionBy? orderBy=sqlOrderBy? frame=sqlWindowFrame? RP_ | windowName=sqlIdentifier_ )`——**partitionBy/orderBy/frame 全部可选**；`sqlWindowFunction_: sqlAggregateFunction|sqlRegularFunction`（`:240`）；frame 三单位 `ROWS|RANGE|GROUPS`（`:248-267`，含 `BETWEEN`、单 bound 简写、`UNBOUNDED PRECEDING|FOLLOWING`、`CURRENT ROW`、`expr PRECEDING|FOLLOWING`）；命名窗口 `sqlWindowClause`/`sqlWindowDecl`（`:272-281`）。
- **`over(order by …)` 不带 partition 今天可解析**：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlWindowGrammarParse.java:50-53`（`testOverOrderByOnly` 断言 `select sum(a) over (order by b) from t` 解析通过）——roadmap"今天 `over(order by …)` 不带 partition 就失败"的表述对 live repo **已失效**；同文件还有 `over ()` 空、双子句、命名窗口、frame 各形态、`testFieldAssertions*` 字段断言共 15+ 用例。
- **transform 三道 frame 能力门**：`EqlTransformVisitor.checkWindowFrameFeature`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:375-397`）按 `isSupportWindowFrameRows/Range/Groups` 三 accessor（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java:89/91/93`）抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`；命名窗口声明的 frame 校验与遍历在 `visitSqlQuerySelect` 内（`:349-360` 区，`:351` 注释）；`over <name>` 引用校验 `visitSqlWindowExpr`（`:401-412`，`ERR_EQL_UNKNOWN_WINDOW_NAME`）。⚠️ roadmap 记的 `:357/:363/:369` 三门行号已漂移至上述 `:375-397` 区（门体合并为 `checkWindowFrameFeature` 一个方法内的三个 case）。
- **输出侧**：`AstToEqlGenerator.visitSqlWindowExpr`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java:1008-1021`）打印 ` over( partitionBy orderBy frame )` 与 ` over <name>`；`AstToSqlGenerator` 无 override（继承）。
- **测试面（EC ② 的既有覆盖）**：parse 层 `TestEqlWindowGrammarParse`；SQL 文本快照 `TestDialectWindowSqlSnapshot`（golden `nop-persistence/nop-orm-eql/src/test/resources/__snapshot/{dialect}.window.sql`，现存 **10 个方言**：dm/duckdb/h2gis/mariadb/mssql/mysql/mysql5.7/oracle/postgis/postgresql，含 over/window/frame/fail-fast 四组断言）；**H2 真库执行** `TestH2WindowFrameMatrix`（`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestH2WindowFrameMatrix.java:68` 起，rows/range/命名窗口 rows/range/命名无 frame 五组）与 `TestH2WindowFrameCompileEndToEnd`（nop-orm-eql compile 测试）。
- **能力位现状**（owner doc `docs-for-ai/02-core-guides/eql-and-database-compatibility.md:52`，2026-10-02 实跑矩阵）：h2/postgresql 三 frame 能力位全开（h2gis/postgis/duckdb 经继承同开）；dm/mariadb/mssql/mysql/mysql5.7/oracle 六方言关闭（frame 查询翻译期 fail-fast，断言覆盖）。
- **窗口函数登记**：`window-expr-support.dialect.xml` mixin 注册 10 个 `onlyForWindowExpr="true"` 窗口函数（rank/dense_rank/row_number/lead/lag/first_value/last_value/nth_value/percent_rank/cume_dist）；`EqlTransformVisitor.visitSqlRegularFunction:1551-1556` 的 `ERR_EQL_FUNC_ONLY_ALLOW_IN_WINDOW_EXPR` 是**放置校验的既有先例**。owner doc `:38-52` 已有「分析窗口语法」段（⚠️ 该段 WI 编号指 **nop-stream-sql roadmap**，与本 roadmap 编号空间不同，owner doc `:40` 已有防混用注记）。

### 四、聚合路径约束（roadmap 约束 2 实测，rollup/cube/grouping 的解法空间）

- **约束 2 原文**：`sqlIdentifier_agg_`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:283-285`）硬编码 `MAX|MIN|SUM|COUNT|AVG`，`EqlTransformVisitor` **无** `visitSqlAggregateFunction` override（grep 0 命中；打印侧仅 `AstToEqlGenerator.visitSqlAggregateFunction:892`）——聚合归一化不能靠加 XML `<functions>`，必须走路径 ⓒ（新 AST 节点+文法）或 ⓓ（AST 改写）。
- **实测补充（roadmap 未登记的事实）**：`rollup`/`cube`/`grouping` 三词**无保留 token**（9 个 `.g4` 零命中，词法落 `IDENTIFIER_`），经 `sqlIdentifier_ → sqlIdentifier_func_ → sqlRegularFunction` **今天已可解析**（`group by rollup(a,b)` 落入 `sqlGroupByItem`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:426-428`，`expr=sqlExpr` 单备选）内的 `SqlRegularFunction`）；transform 层 `visitSqlRegularFunction`（`EqlTransformVisitor.java:1545-1572`）查函数表为 null 即抛 `ERR_EQL_UNKNOWN_FUNCTION`（`:1547-1549`）——**三函数今天的失败是响亮的编译期报错，不是静默错误 SQL**；本 plan 的工作是"注册可用的 lowering + 方言门"，不是"消灭静默缺陷"。
- **`onlyForWindowExpr` 放置校验先例**（`:1551-1556`）：函数表注册 + ASTParent kind 校验的机制今天存在——**`grouping` 的落地正解正是复用它做按函数名门控**（无需新放置标志，见第五节）；对 rollup/cube，纯函数表路径（ⓐ）被拒（约束 2 明文 + "仅 GROUP BY"放置标志是新特性面 + 非法位置 SQL 推迟到执行期才响亮），ⓒ/ⓓ 路径二选一只适用于这两条（Phase 0 裁定，差异登记第 7 条）。
- **函数表元数机制**（若任何候选需引用）：`NativeSQLFunction`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/function/NativeSQLFunction.java:38-39`）`minArgCount/maxArgCount` 未配置时回落 `argTypes.size()`，显式可设大值表达多参——无"无界"哨兵，variadic 需显式上限。`TemplateSQLFunction` 的越界 marker 校验（WI0 交付）已在加载期闸住模板坏值。
- **WI4 管线（ⓓ 可用性）**：`EqlCompiler.java:56-67` 已把 `IEqlAstTransformer.transformBeforeAnalyze/transformAfterAnalyze` 作为固定环节（plan 21 已 `completed`）；缓存键问题已按"非默认 transformer 绕过缓存"收口（owner doc `:424`）。⇒ ⓓ 不再有"无生产先例"障碍，但 transformer 形态的放置校验发生在 `EqlTransformVisitor` 之前/之后的分析层，报错时机与错误码归属需 Phase 0 裁定。

### 五、关键字 token 先例（WI7 执行期修订，rollup/cube 文法方案的关键输入）

- **plan 26 起草时设想的"`NULLS` 走 `unreservedWord_` 加词 = 零 lexer 改动"被执行期实测推翻**：无 lexer 规则的隐式 token 无产生通路、永不可达（BUG-15 `characterSet_` 的 `STRING` 同形态）——**显式 token 是关键字文本在文法备选中唯一可匹配通路**。实际落地：`nop-persistence/nop-orm-eql/model/antlr/Literals.g4:29-31` 新增 `NULLS` token（fragment 组合大小写不敏感、**定义在 `IDENTIFIER_` 之前**——lexer 优先级=定义序），新增 1 个 token、其后 5 个 Literals token 序号顺延 +1、**零删除**（"不删除 lexer token"红线不触；token 均按名引用，`.tokens`/`EqlLexer` 同批重生成）；同时 `unreservedWord_` 加 `NULLS`（`BaseRule.g4:103-105`）保住标识符位置兼容（列名/别名 `nulls` 仍可解析）。
- ⇒ **对 rollup/cube 的直接推论**：若 Phase 0 裁定文法备选需要匹配 `ROLLUP`/`CUBE` 字样（ⓒ 路径的 `sqlGroupByItem` 新备选），必须走 `nop-persistence/nop-orm-eql/model/antlr/Literals.g4` 显式 token + `unreservedWord_` 双登记（NULLS 同款三件套：新 token 定义在 `IDENTIFIER_` 之前 + `unreservedWord_` 成员 + 语料 0 命中 BC 论证）。⚠️ **双登记 ≠ 文法级放置排他**：非 GROUP BY 位置的 `rollup(x)`/`cube(x)` 仍经 `unreservedWord_ → sqlIdentifier_ → sqlIdentifier_func_` 解析为 `SqlRegularFunction`——ⓒ-A 的文法备选只决定 **GROUP BY 位置**的双路径胜者，非 GROUP BY 位置的报错依赖函数表 unknown-function 兜底（机制依赖链见第四节与 Phase 0 裁定项）。
- **`grouping` 零文法/token 改动（独立审查 Major-1）**：今天已经 `IDENTIFIER_ → sqlIdentifier_ → sqlIdentifier_func_ → sqlRegularFunction` 解析成 `SqlRegularFunction`——任何"再加文法备选"的方案（如 `sqlExpr_special` altLabel）都是**新增第二个可解析入口**（违反 WI5 纪律，`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlExpr_special` 段注释同款），且不建 GROUPING token 则该备选根本写不出来（NULLS 教训：显式 token 是关键字文本唯一可匹配通路）。正确形态 = **函数表注册 lowering + `EqlTransformVisitor.visitSqlRegularFunction` 按函数名门控**（`onlyForWindowExpr` 放置校验先例 `:1551-1556` 同款）；grouping 现全 18 个方言 XML **函数表 0 注册**（仅 mysql/duckdb/tdengine 的 `<reservedKeywords>` 列表含该词样、非函数注册，2026-10-04 grep 实测）⇒ Phase 2 注册即真快照增量。
- **语料相容性**：三份必测语料（`NopJobTask.sql-lib.xml`/`nop-auth` test/`test.sql-lib.xml`）对 `rollup|cube|grouping` **0 命中**（2026-10-03 grep 实测）——新增关键字接受对既有语料是纯增量。

### 六、方言矩阵事实（roadmap ledger + 本 plan 实测，EC ① 的输入）

| 函数 | h2 | mysql / mariadb | postgresql | duckdb | oracle 23ai | mssql | dm / tdengine | db2 |
|---|---|---|---|---|---|---|---|---|
| `ROLLUP(...)`（函数式，GROUP BY 内） | **无**（本 plan 实测） | 无函数式@承诺基线 8.0.31（roadmap 取证：8.4.3 才有）；MariaDB 无 | 有 | 有 | 有 | 待 Phase 0 一手取证（ISO `GROUP BY ROLLUP` 预期有） | 无一手文档 → `unverified` | 不可加载（无函数表） |
| `CUBE(...)` | **无**（本 plan 实测） | **无（永久，roadmap ledger 明文）** | 有 | 有 | 有（`CUBE` ×14） | 待 Phase 0 一手取证 | `unverified` | 不可加载 |
| `GROUPING()` | **无**（本 plan 实测） | MySQL 8.0+ 有（承诺基线内）；MariaDB 待 Phase 0 取证 | 有 | 有 | 有 | 待 Phase 0 一手取证 | `unverified` | 不可加载 |

- `GROUPING SETS` = **永久否决**（roadmap ledger：MySQL/MariaDB 全版本均无），本 plan 不注册（Non-Goals）。
- `dm`/`tdengine` 按红线一律 `unverified`、不计入覆盖率、落地形态一律编译期报错。
- 矩阵快照载体：`ai-dev/audits/eql-wi0/dialect-capability-matrix.json` + 门禁 `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs`——三函数注册是**真快照增量**（不同于 plan 26 的零增量口径）。

### 七、真库执行证据面（EC ⑦ 的载体约束）

- CI 真库只有 H2：`JdbcTestCase.createDataSource()`（`nop-persistence/nop-dao/src/test/java/io/nop/dao/jdbc/JdbcTestCase.java:111-118`）硬编码 `org.h2.Driver`/`jdbc:h2:mem:`；`TestDialect.testFunctions`（`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialect.java:186` 起）遍历**当前数据源方言**（=H2）的函数表逐个真库执行。
- **推论（差异登记第 2 条的落点）**：H2 无三函数 ⇒ 三函数**不会**注册进 `h2.dialect.xml`（注册了 `testFunctions` 反而在执行期炸）⇒ `TestDialect.testFunctions` 的"三函数真库执行全绿"**不可能以正向形态成立**；EC ⑦ 按 roadmap 自带的"不可真库验证的方言逐个列出并给替代证据"分支执行：h2 = **负向载体**（未注册 → 编译期报错用例），oracle/postgresql/duckdb/mysql(grouping) = 一手文档引证 + 文本断言替代证据，`dm`/`tdengine` = `[未取证]`。interval 半区相反——h2 **可**配模板（探针已证 `INTERVAL '{value}' {unit}` 形态合法）⇒ interval 模板消费有 h2 端到端真库执行证据（Phase 1）。

### 八、生成管线事实（决定 Protected Area 与提交纪律）

- `nop-orm-eql` 侧生成管线是活的，两条驱动链：`precompile/gen-eql-parser.xgen`（`.g4` → `EqlParser.java` **实测现 11423 行**——roadmap 10785 → plan 24 记 10904 → plan 26 记 10927 → WI9a/WI9b 落地后再漂移；`_EqlASTBuildVisitor.java` 1819 行；`EqlLexer*`/`Eql.interp`/`*.tokens`）与 `precompile/gen-eql-ast.xgen`（`EqlAST.xjava` → `ast/_gen/`）。重生成命令 `./mvnw generate-sources -pl :nop-orm-eql -am`。`ast/_gen/` 实测 **77 个文件**（与 roadmap 一致）。
- **路径定价**：ⓐ/ⓑ（复用既有节点/函数表）与 ⓓ（transformer 改写）默认 `ast/_gen/` 零 diff；**ⓒ 新增 AST 类**才触 `EqlASTKind` ordinal 变化（现 67 常量一一对应 AST 类；`SqlGroupBy` ordinal 22、`SqlGroupByItem` ordinal 23——`EqlASTKind.java:50-52`）+ `ast/_gen/` 重写。⚠️ **ordinal 影响精确口径（独立审查 Minor-④）**：**尾部追加只 +1**（新 ordinal = max+1，既有 ordinal 不动）；**插中段才把其后 ordinal 整体顺延**——新类落点（尾部 vs 插入点）随路径裁定记录。`type: ROLLUP|CUBE` 枚举字段若按 `SqlWindowFrameType`/`SqlWindowFrameBoundType` 先例落 `io.nop.orm.eql.enums` 包，还需 **1 个新 enums 类**（计入 Phase 0 成本清单）。roadmap WI7 收口注记（`:302`）明文：**后继若需新增 AST 节点/字段仍按约束 1 路径 ⓒ 原定价执行**；给既有类加字段不动 ordinal（红线 = 禁止把 `EqlASTKind.ordinal()` 持久化或跨版本比较，原文不变）。
- altLabel 工具约束（WI1/WI5 两轮执行期记录，plan 24 Current Baseline）：多备选规则每备选必须解析为 AST 节点；同一 prop 不能出现在同一规则的不同备选（`nop.err.antlr4.grammar.duplicate-prop-label`）；altLabel → AST 类名取 label 首段下划线前缀。

### 九、全绿口径与 stream 豁免

含 `:nop-auth-service` 的命令会拖入 `nop-stream-runtime`。plan 24/26 记录的既有 flake 为 2 项（`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication` + `TestMessageSourceFunctionRestart`，均单跑 PASS）；**本 plan 以收口日实跑为准**：收口轮实跑 `-pl` 命令，凡与当日基线一致、且单跑可复现 PASS 的 `nop-stream-runtime` 时序 flake 按基线豁免（豁免清单记入 Closure Evidence），不预设名单、不据此判本 plan 失败。

## Goals

- **`sqlIntervalLiteral` 模板落地**（EC ③）：占位符形态裁定（fixture 的 `{value}`/`{unit}` 命名 marker vs `TemplateSQLFunction` 的 `{0}`/`{1}` 序号 marker）+ 生成器消费点 `AstToSqlGenerator.visitSqlIntervalExpr` override（模板未配置 → 委托 super 逐字节不变；已配置 → 模板渲染；TUMBLE 路径不触碰）+ PG/Oracle/MySQL 三方言**正确输出文本用例**（探针/文档双证形态分叉：MySQL value 不加引号、PG/Oracle/H2 value 加引号）+ h2 配置模板并落**端到端真库执行**用例（parse → transform → generate → H2 执行）；既有 `interval 1 day`/`interval '1 day'` 两语法形态行为、回显路径、`TestEqlIntervalLiteralParse` 全量不回退；`TestDialectSqlIntervalLiteral` 的 h2-null 断言随 h2 配置同步更新（原三测骨架不弱化）。
- **`rollup`/`cube`/`grouping` 注册**（EC ①⑦）：rollup/cube 路径 ⓒ vs ⓓ 裁定落地；`GROUP BY ROLLUP(a,b)`/`GROUP BY CUBE(a,b)`（含混合项 `GROUP BY a, ROLLUP(b,c)` 的裁定）可解析、有 lowering 与方言能力门；**`CUBE` 在 MySQL/MariaDB 上显式编译期报错用例**（EC ① 硬性）；`grouping` 零文法改动——函数表注册 lowering + `visitSqlRegularFunction` 门控，`GROUPING(a)`/多参 `GROUPING(a,b)` 元数按方言裁定；`GROUPING SETS` 不注册（永久否决）；矩阵按方言名列全 + 真快照增量同步（grouping 现函数表 0 注册，注册即真增量）；不可真库验证方言逐个列明替代证据（h2 = 负向载体用例 + 探针归档）。
- **窗口 OVER/frame 现状核验收口**（EC ②）：对既有覆盖（`TestEqlWindowGrammarParse` 两种形态 + golden 快照 + H2 真库执行）做逐项审计，判定"带/不带 partition 两种形态 + frame 的正确 SQL 文本用例"是否已齐，缺口补齐；roadmap WI7b 行的 stale 定价（"OVER 放宽" + "SqlWindowExpr 增字段"）与门控矩阵 Protected Area 单元格按实测回写（原地注记先例）。
- **`count(*) FILTER` 文档引导**（EC ④）：owner doc 新增小节——`sum(case when p then 1 else 0 end)` 引导 + "空分组下 `sum` 返回 NULL 而 `count` 返回 0"提醒 + 已确认有/无/未取证矩阵（PG/DuckDB/SQLite 有；MySQL/MariaDB/MSSQL/Oracle 无；DB2/TDengine/H2 未取证，H2 附本 plan 探针与 `h2.dialect.xml:45` 保留字旁证）；roadmap ledger 行收口标注。
- **既有 EQL 语料 BC**（EC ⑥）：三份必测与 WI0 基线快照逐字一致；约束 4 的"一致性 + 不等价"两类用例齐备（interval 逐方言输出形态差异、rollup 在支持/不支持方言的报错差异即两类素材）。
- **独立 closure audit**（EC ⑧，不同 task_id，evidence 写入本 plan Closure 段）。

## Non-Goals

- **不注册 `GROUPING SETS`**——roadmap ledger 永久否决项（MySQL/MariaDB 全版本均无）。
- **不做 `count(*) FILTER` 语法**——本 plan 对它是文档引导 only（roadmap WI7b EC ④ 明文）；语法落地若未来立项须先登记 roadmap。
- **不做 MySQL/MariaDB 的 `WITH ROLLUP`/`WITH CUBE` 旧修饰符形态**与 **`GROUPING_ID()`**——roadmap WI7b 只点名函数式 `ROLLUP(...)`/`CUBE(...)`/`GROUPING()`（登记 Deferred）。
- **不为 MySQL 注册超出 G-1 承诺基线（8.0.31）的函数式 `ROLLUP`**（roadmap 取证 8.4.3 才有——高于承诺基线的版本特性不默认开启，Phase 0 裁定 mysql 的 rollup 格 = 报错/不注册并登记 successor 触发条件）。
- **不解决窗口 frame 能力位在六方言（dm/mariadb/mssql/mysql/mysql5.7/oracle）的开启与否**——那是 nop-stream-sql roadmap WI3 实跑矩阵的已收口事实（owner doc `:52`），本 plan 只消费不翻案；不动 `window-expr-support.dialect.xml` mixin。
- **不动 TUMBLE 渲染路径**（`AstToSqlGenerator.visitSqlTumbleTableSource:253-261` 刻意逐字输出，owner = nop-stream-sql WI20）；不动 stream-only 语义。
- 不做 `interval '1-2' year to month` 多字段 qualifier 语法（WI1 已登记文法层失败走 antlr 错误码，owner doc `:296`；登记 Deferred）。
- 不做 WI12 的 `SAVEPOINT`/`FROM (VALUES …)`/`AT TIME ZONE` 等长尾项。
- 不把 `:nop-orm-demo` / `nop-spring-simple-demo` / `:nop-orm-tdengine` / `:nop-db-migration` 拉进本 plan 的 `-pl`（roadmap 级 EC 第 8 条 + WI0 静态快照兜底）。
- 不修复 `db2` 可加载性（WI3 已收口的另案）；`db2` 不进本 plan 矩阵覆盖率（不可加载、无函数表）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql`：`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（`visitSqlIntervalExpr` override 消费 `sqlIntervalLiteral` 模板；rollup/cube/grouping 的输出侧按 Phase 0 裁定）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java`（三函数能力门/放置校验，按裁定路径）、若裁定 ⓒ：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`sqlGroupByItem` 备选 / ⓒ-A 下 `sqlExpr_special` 零触碰（grouping 路线已改函数表注册））、`nop-persistence/nop-orm-eql/model/antlr/Literals.g4`（`ROLLUP`/`CUBE` token，NULLS 同款）、`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `unreservedWord_`、`nop-persistence/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava`（新 AST 类）+ `ast/_gen/` 重生成 + 手写 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`；若裁定 ⓓ：transformer 实现（不动 `IEqlAstTransformer` 签名）；重生成产物（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java` 等）；对应测试（`.../parse/`、`.../compile/`）。
- `nop-persistence/nop-dao`：`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`（h2 等方言的 `sqlIntervalLiteral` 配置 + 三函数的注册/不注册落点，均按 Phase 0 矩阵裁定）、`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialectCapabilityMatrix.java` 快照重生成、必要时 `dialect.xdef` 的 javadoc 注释更新（占位符形态说明；元素结构零改动、零生成）。
- `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（EC ③/⑦ 增量）。
- 文档：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（INTERVAL 段 `:291-298` 收口更新、rollup/cube/grouping 覆盖矩阵段、`count(*) FILTER` 引导小节、窗口段交叉引用、能力点登记表行）；`ai-dev/design/eql-cross-db-compat/`（Phase 0 裁定文档）；`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI7b 行状态流转 + WI7b 行 stale 定价回写 + ledger `count(*) FILTER` 行收口，按 roadmap Status values 纪律）；`ai-dev/logs/`。

### Out Of Scope

- Non-Goals 所列全部；任何 roadmap 未登记的方言语法扩展（发现新缺陷/新需求 → 登记 roadmap，不顺手修）；`nop-auth`、`model/*.orm.xml`、`nop-*-api`、`nop-core`/`nop-xlang`/`nop-xdef` 内部（`dialect.xdef` 仅 javadoc 注释级更新、元素与属性结构零改动）。

## 与 roadmap 的差异登记（起草时实测发现）

1. **窗口半区的定价前提已被合并越过（非实质偏离，回写见 Phase 3）**：roadmap WI7b 节"窗口 `OVER` 放宽（今天 `over(order by …)` 不带 partition 就失败）"与门控矩阵 Protected Area 单元格"生成管线（`SqlWindowExpr` 增字段）"两项前提实测均已不成立——`over(order by b)` 今天可解析（`TestEqlWindowGrammarParse.testOverOrderByOnly:50-53`）、`SqlWindowExpr` 五字段齐备（`_SqlWindowExpr.java:19-27`）、frame 能力门/输出/快照/真库测试齐备（Current Baseline 第三节）。EC ② 的"两种形态 + frame 各有正确 SQL 文本用例"**大部分已由既有测试满足**，本 plan 做覆盖审计 + 缺口补齐 + roadmap 行回写（照 plan 26 Phase 4 的原地注记先例，不删原文、注明修订依据）。
2. **H2 无 ROLLUP/CUBE/GROUPING（实测否证起草输入）**：起草输入称"rollup H2 支持?SELECT GROUP BY ROLLUP H2 有——取证"；真库探针（H2 2.3.232 与 2.4.240、含 Oracle/PostgreSQL 兼容模式）实测**三函数全部 `Function not found`（90022）**。⇒ EC ⑦"三个新函数在 `TestDialect.testFunctions` 真库执行路径全绿"按 roadmap 自带条款的"不可真库验证的方言逐个列出并给替代证据"分支执行：h2 = 负向载体（未注册 → 编译期报错用例 + 探针记录），正向执行证据不可得。这不是对 EC ⑦ 的降级——roadmap 条款原文已预留该分支，本 plan 是把它显式化并给出逐方言清单（h2/mysql/oracle/postgresql/duckdb/dm/tdengine + mssql 待 Phase 0 取证）。
3. **三函数今天已可解析且响亮报错（roadmap 未登记）**：`rollup(a,b)`/`cube(a,b)`/`grouping(a)` 无保留 token、经 `IDENTIFIER_` 走 `SqlRegularFunction`，transform 层 `ERR_EQL_UNKNOWN_FUNCTION` 响亮失败（`EqlTransformVisitor.java:1547-1549`）。本 plan 的"注册"落点在 lowering + 方言门，不在"让语法被接受"（接受已发生）；**rollup/cube 若走 ⓒ，新文法备选只接管 GROUP BY 位置的解析胜者**——`sqlGroupByItem` 内 `ROLLUP(a,b)` 同时匹配既有 `expr=sqlExpr` 备选与新备选，ANTLR 歧义由**备选顺序**决定（新备选必须前置，排后即死代码；双路径胜者钉死），**非 GROUP BY 位置仍落 `SqlRegularFunction`、报错依赖函数表 unknown-function**（机制依赖链点破，见第五节）；**`grouping` 零文法改动**，注册落点 = 函数表 + `visitSqlRegularFunction` 按函数名门控。
4. **`NULLS` token 执行期修订改变文法方案输入**：plan 26 的"unreservedWord_ 加词 = 零 lexer 改动"设想不成立（`Literals.g4:22-31` 执行期修订注记），显式 token 是关键字文本唯一可匹配通路。⇒ rollup/cube 若走 ⓒ 文法备选，按 NULLS 三件套先例（新 token 定义在 `IDENTIFIER_` 之前 + `unreservedWord_` 双登记 + 语料 0 命中）落地；新增 token 序号顺延、零删除，不触红线原文。
5. **行号漂移（非实质偏离）**：`sqlIdentifier_agg_` roadmap 记 `:273-275` → 实测 `:283-285`；窗口三门 roadmap 记 `:357/:363/:369` → 实测合并为 `checkWindowFrameFeature`（`:375-397`）+ `visitSqlWindowExpr`（`:401`）；`visitSqlRegularFunction` roadmap 记 `:1454` → 实测 `:1545`；`EqlParser.java` 10785 → 实测 11423；`h2.dialect.xml` FILTER 关键字 roadmap 记 `:37` → 实测 `:45`（WI3 补配漂移）。执行时以符号定位为准，Phase 0 复核记录进裁定文档。
6. **`sqlIntervalLiteral` 的生成器消费点 roadmap 未点名**：roadmap WI1/WI7b 行说"骨架/模板落地"但未指明消费点；实测 `AstToSqlGenerator` 无 `visitSqlIntervalExpr` override、当前输出 `interval 1 DAY` 在 **H2 与 PG 上都非法**（探针证据）——消费点 = `AstToSqlGenerator` 新增 override，本 plan 补登（属实现方案细化，不构成范围偏离）。
7. **约束 2 对三函数的适用口径（修订稿按独立审查收窄）**：约束 2 原文针对 `sqlIdentifier_agg_` 硬编码的五个聚合（`MAX|MIN|SUM|COUNT|AVG`）——**`grouping` 不经该通路解析（见第 3 条），函数表注册 + `visitSqlRegularFunction` 按函数名门控即其正解**（不触约束 2、不新增可解析入口）；ⓒ/ⓓ 的路径二选一**只适用于 rollup/cube**（ⓒ 下由 `sqlGroupByItem` 新备选承载 GROUP BY 位置语义）。"函数表 + `onlyInGroupBy` 式放置标志"对 rollup/cube 仍是被拒备选（约束 2 明文 + 放置标志是新特性面 + `select rollup(a) from t` 会把非法 SQL 推迟到执行期才响亮）；若 Phase 0 取证推翻 ⓒ（例如 `ast/_gen` 成本实测失控），翻案须显式 scope change 并回滚对应 Non-Goals/条目。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `nop-persistence/nop-orm-eql/model/antlr/*.g4`（若裁定 ⓒ：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlGroupByItem`/`unreservedWord_`、`nop-persistence/nop-orm-eql/model/antlr/Literals.g4` 新 token）→ 经 `gen-eql-parser.xgen` 重生成 `EqlParser.java`（11423 行）/`_EqlASTBuildVisitor.java` 等 | **生成管线，plan-first**（AGENTS.md Protected Area"生成管线"；roadmap 门控矩阵 WI7b 行 + WI7 收口注记同判） | 本 plan 即 plan-first 载体，执行前须过 draft review；重生成命令 `./mvnw generate-sources -pl :nop-orm-eql -am` | owner doc（`eql-and-database-compatibility.md`）+ 回归测试（各 Phase 红/绿用例 + 三份必测 BC + interval/窗口既有守护） |
| `EqlAST.xjava` / `ast/_gen/` / `EqlASTKind`（若裁定 ⓒ 新增 AST 类） | **ⓒ 才触碰**；新增类触 ordinal 变化（尾部追加仅 +1、插中段才顺延——落点登记）（红线 = 禁止持久化/比较 ordinal，原文不变）；ⓐ/ⓑ/ⓓ 默认零 diff | 若裁定 ⓒ → 按 roadmap 约束 1 路径 ⓒ 原定价执行（`ast/_gen/` 77 文件重写 + WI7 收口注记口径的 `clean install` 必过）并在裁定文档登记；若裁定 ⓓ → 本行整行零触碰 | `git status` 中 `ast/_gen/` 的 diff 与裁定文档一致（ⓓ 下零 diff 作为每 Phase 回归证据） |
| `*.dialect.xml`（h2 等的 `sqlIntervalLiteral` 配置、三函数注册/不注册） | 平台对外方言契约的**纯增量/显式裁定**（不改既有取值；`nop-dao` 无 codegen 管线，不构成生成管线 Protected Area——roadmap Cross-Cutting 第一行） | 不触发 plan-first；逐项登记于此并同步矩阵快照 | 快照门禁 exit 0 + 负向用例 + 替代证据清单 |
| `dialect.xdef`（仅 javadoc 注释级更新，元素/属性结构零改动）+ `DialectSqls`/`IDialect`/`DialectImpl`（**已有骨架，本 plan 预期零改动或仅注释**） | 非生成管线、非 AGENTS.md 字面 Protected Area | 不触发 plan-first；若 Phase 0 裁定必须动结构（预期不需要）→ 显式 scope change | `git diff` 显示仅注释级变化（或零变化） |
| 生成物变更纪律 | `.g4`/`EqlAST.xjava` 改动**单独提交**；重生成后 `git diff` 逐一检查；无 lexer token **删除**（新增 token 序号顺延可接受，NULLS 先例） | 提交拆分 git 可审计（`nop-git-master` skill） | `git log` 文法提交与逻辑提交分离 |

⚠️ 本 plan 不触碰 `model/*.orm.xml`、`nop-auth`、`nop-*-api` 公共接口、`nop-core`/`nop-xlang`/`nop-xdef` 内部。

## Execution Plan

Phase 1（interval 模板）/ Phase 2（三函数）/ Phase 3（窗口核验+回写）/ Phase 4（FILTER 文档+收口）相互独立、可分别落地与回滚；硬前置已全部满足（plan 03/plan 26 均 `completed`），各 Phase 可开工——每次开工前执行回退防护复核（见硬前置声明）；Phase 3 的 roadmap 回写以 Phase 0/1/2 的实测结论为输入。每个代码 Phase 的 `.g4`/`EqlAST.xjava` 改动单独提交。

### Phase 0 - 裁定：模板占位符与逐方言矩阵、三函数路径与方言排除、窗口缺口判定、FILTER 小节大纲（不改产品代码）

Status: completed（2026-10-04）
Targets: `ai-dev/design/eql-cross-db-compat/`（新增裁定文档）、`ai-dev/audits/eql-wi0/`（只读取证）、`_tmp/h2probe`（探针留痕，gitignore 范围）

> **前置状态复核（回退防护）**：本 Phase 开工前复核 plan 03（WI1）与 plan 26（WI7）的 `Plan Status` 未回退并记录（现均 `completed`，硬前置已满足）。并复核"与 roadmap 的差异登记"全部漂移锚点的现位（`sqlIdentifier_agg_`/窗口门/`visitSqlRegularFunction`/`EqlParser.java` 行数/h2 FILTER 行——以符号定位为准，结果记入裁定文档）。

- Item Types: `Decision`、`Proof`

- [x] 复核 WI0 三件基础设施状态未回退：矩阵快照 + 门禁脚本（`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0）、语料基线（`TestEqlCorpusBaseline` 通过）、plan 03/plan 26 状态（记录于裁定文档）。并复核差异登记第 5 条全部漂移锚点现位。
- [x] **裁定 `sqlIntervalLiteral` 占位符形态与渲染契约**（Decision）：候选 A（推荐）= fixture 先例的命名 marker `{value}`/`{unit}`——value 占位符接收**渲染后的 value 表达式文本**（`visit` 子表达式后捕获），unit 占位符接收 `intervalUnit` 规范名；候选 B（被拒备选，记录理由）= `TemplateSQLFunction` 的 `{0}`/`{1}` 序号 marker（复用 WI0 越界校验，但 interval 值可能是子表达式而非字面量，语义不匹配）。**加载期校验**：模板出现未知 marker 必须在方言加载期抛错（WI0 `TemplateSQLFunction` marker 校验同纪律——"只打印不设门 = 净回归"），校验落点（`DialectImpl` 构造期 vs 首次消费期）裁定并记录。渲染输出类型裁定：模板渲染是**纯文本拼接**（value 已渲染），不得二次转义。**非字面量 value 的行为必须显式钉死（独立审查 Minor-②）**：value 为参数 marker `?` 或列引用（非字符串字面量）时，带引号模板（如 h2/PG/Oracle 的 `INTERVAL '{value}' {unit}`）会产生 `INTERVAL '?' DAY` 类非法文本——候选处置：① 仅字面量 value 走模板、非字面量维持既有裸输出（DB 执行期响亮）；② 非字面量 value 编译期显式报错。两侧都响亮、不触"静默"红线，但必须二选一钉死并各落一条对应用例。
- [x] **裁定逐方言模板内容矩阵**（Decision，EC ③ 的输入）：至少覆盖 PG/Oracle/MySQL（EC ③ 硬性三方言）+ h2（CI 真库执行载体）；每行给形态依据（本 plan 探针记录 / 一手文档引证，逐行标 `[实测]`/`[文档]`；探针已证 MySQL 与 PG/H2 的 value 引号分叉）。**h2 配置裁定**：推荐给 `h2.dialect.xml` 配置模板（`INTERVAL '{value}' {unit}` 形态，探针 `[实测]`），使 interval 模板消费获得 CI 真库端到端证据；同步更新 `TestDialectSqlIntervalLiteral` 的 h2-null 断言（原三测不弱化，只改断言值）。`dm`/`tdengine` 一律不配置（红线 `[未取证]`）。既有裸形态的处置裁定：模板未配置方言维持现输出（deprecated 标注不变，owner doc `:295`），不引入新报错（BC）。
- [x] **裁定 rollup/cube 的路径（ⓒ vs ⓓ）与文法/AST 形态**（Decision，本 plan 最大 Protected Area 裁决；**`grouping` 不在本裁定范围**——其零文法改动 + 函数表正解单独裁定，见下一 item）。**候选 ⓒ-A（推荐）**：新增 1 个 AST 类（GROUP BY 专用 grouping 操作节点：`type: ROLLUP|CUBE` + `args`；按 `SqlWindowFrameType` 先例还需 1 个新 enums 类）挂 `sqlGroupByItem` 新备选（altLabel 三约束）。**备选顺序语义必须落档（Major-3）**：`ROLLUP(a,b)` 在 `sqlGroupByItem` 内**同时匹配**既有 `expr=sqlExpr` 备选与新备选，ANTLR 歧义由**备选顺序**决定——新备选必须**前置**（排后即死代码），双路径胜者钉死并落守护用例（`group by rollup(g)` 产出新节点而非 `SqlRegularFunction`）。**ⓒ-A 不获得文法级放置排他（必须点破，Major-3-i）**：`ROLLUP`/`CUBE` 走 `nop-persistence/nop-orm-eql/model/antlr/Literals.g4` 显式 token + `unreservedWord_` 双登记（NULLS 三件套先例，差异登记第 4 条）后，**非 GROUP BY 位置的 `rollup(x)`/`cube(x)` 仍经 `unreservedWord_ → sqlIdentifier_ → sqlIdentifier_func_` 解析为 `SqlRegularFunction`**——非 GROUP BY 位置的报错口径 = 函数表未注册 → `ERR_EQL_UNKNOWN_FUNCTION`（或裁定加显式门），必须显式确认并落负向用例；机制依赖链 = 文法备选只决定 GROUP BY 位置的胜者，放置排他来自"GROUP BY 之外无注册"。**候选 ⓓ（备选）**：零文法改动，parse 维持 `SqlRegularFunction` 兜底，WI4 管线 transformer 做放置校验 + 归一化改写；代价 = 报错时机后移、transformer 语义进默认链路的错误码归属、"放置错误在 parse 后才响亮"的体验损失；收益 = 零 `ast/_gen` 触碰。**被拒备选（记录）**：① 纯 `<functions>` XML 注册 rollup/cube（约束 2 明文 + 放置不可约束 + `select rollup(a) from t` 非法 SQL 推迟到执行期，差异登记第 7 条）；② `grouping` 走 `sqlExpr_special` altLabel 备选（新增第二个可解析入口违反 WI5 纪律 + 无 GROUPING token 则备选写不出——NULLS 教训，Major-1）。**成本清单（ⓒ-A）**：新增 1 AST 类（`EqlAST.xjava` + `ast/_gen` 77 文件重写 + `clean install` 必过；ordinal **尾部追加仅 +1、插中段才顺延**——落点登记）+ 1 新 enums 类（`ROLLUP|CUBE`）+ 2 新 token（仅 ROLLUP/CUBE）+ parser 重生成。**裁定必须产出**：选定路径 + 备选顺序与胜者钉死结论 + 非 GROUP BY 位置报错口径 + 文法形态（`GROUP BY ROLLUP(a,b)`、混合项 `GROUP BY a, ROLLUP(b,c)` 支持/不支持）+ 错误码口径（默认复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`/`ERR_EQL_UNKNOWN_FUNCTION` 族，不新起新码除非裁定必须）+ `GROUPING SETS` 不注册的否决再确认。
- [x] **裁定 `grouping` 的落地形态（零文法改动，独立审查 Major-1）**（Decision）：`grouping` 今天已经 `IDENTIFIER_ → sqlIdentifier_ → sqlIdentifier_func_ → sqlRegularFunction` 解析为 `SqlRegularFunction`（零文法改动）——落地 = ① `*.dialect.xml` 函数表注册 `<native name="grouping">` 形态（支持方言按 Phase 0 矩阵落；现全 18 个方言 XML **函数表 0 注册** ⇒ 注册即真快照增量）；② `EqlTransformVisitor.visitSqlRegularFunction` 按函数名门控（`onlyForWindowExpr` 放置校验先例 `:1551-1556` 同款：方言矩阵判定 + 元数走既有 minArgCount/maxArgCount 通路）；③ 语义级放置校验（仅引用 GROUP BY 项）为**可选裁定项**——未注册方言已被 unknown-function 编译期报错兜底，防范围蠕动（裁定不做须登记理由）。`GROUPING(a)` 与多参 `GROUPING(a,b)` 的元数按方言裁定（PG 多参位掩码 vs Oracle 单参——一手取证落档）。
- [x] **重跑 H2 真库探针并持久化 transcript**（Proof，独立审查 Minor-①）：`_tmp/h2probe` 同款 `java -cp` 直跑（H2 2.3.232 与 2.4.240 双版本），覆盖三函数（`GROUP BY ROLLUP(g)`/`GROUP BY CUBE(g)`/`GROUPING(g)`）× `MODE=Oracle`/`MODE=PostgreSQL` + INTERVAL 三形态（`INTERVAL '1' DAY`/`INTERVAL 1 DAY`/`INTERVAL '1 DAY'`）；transcript（命令行 + 完整输出）落 `ai-dev/audits/eql-wi0/`（新归档文件）。当前基线预期与起草轮一致（三函数全 90022；`INTERVAL '1' DAY` 成功/裸形态语法错/`'1 DAY'` 数据错）——若不一致，以新证据更新本 plan Current Baseline 与裁定文档。
- [x] **裁定三函数方言矩阵逐格结论**（Decision，EC ① 的输入）：按 Current Baseline 第六节表格**按方言名列全**（不用比例表述）——每格给 `注册并 lowering`/`显式报错`/`不注册（未配置 → 既有 unknown-function 响亮）` 三态之一 + 证据等级（`[实测]`/`[文档]`/`[未取证]`）；Phase 0 须补 mssql 的一手取证（Microsoft 文档 `GROUP BY ROLLUP/CUBE` + `GROUPING`）与 MariaDB `GROUPING` 取证；mysql 的函数式 `ROLLUP` 按承诺基线 8.0.31 判"超出基线 → 报错 + 登记 successor 触发条件"；`CUBE` 在 MySQL/MariaDB 显式报错用例的载体方言落格（EC ① 硬性）；`dm`/`tdengine` 全格 `[未取证]` → 一律报错；h2 三格 = 探针 `[实测]` 无 → 负向载体。
- [x] **裁定窗口半区的缺口判定清单**（Decision，EC ② 的输入）：对 `TestEqlWindowGrammarParse`（两形态 parse）/`__snapshot/*.window.sql` 10 方言 golden（SQL 文本）/`TestH2WindowFrameMatrix`+`TestH2WindowFrameCompileEndToEnd`（真库）逐项对照 EC ②"窗口 `OVER` 两种形态（带/不带 partition）+ frame 各有正确 SQL 文本用例"，产出"已满足/缺口"清单（预判：文本用例已齐，缺口若在"不带 partition + frame 组合"等边角形态则 Phase 3 补）；同时裁定 roadmap WI7b 行回写的注记措辞（照 plan 26 Phase 4 对 WI7 行的原地注记先例）。
- [x] **裁定 `count(*) FILTER` 文档小节大纲**（Decision，EC ④ 的输入）：小节结构 = 引导写法 `sum(case when p then 1 else 0 end)` + 空分组 NULL-vs-0 提醒 + 有/无/未取证矩阵（roadmap ledger 三态 + H2 探针旁证）+ "为什么不进语法"的约束 3 引用。
- [x] 约束 4 的"一致性 + 不等价"两类用例清单落裁定文档：interval 模板"同输入不同方言输出形态不同"（不等价类，EC ③ 三形态即素材）；rollup/cube"支持方言输出 vs 不支持方言报错"（一致性 = 同语义同文本，不等价 = 能力边界）；FILTER 引导写法与原生 FILTER 在空分组下的语义差异（NULL vs 0）——每项至少一条用例或 owner doc 差异表行素材。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含：占位符形态与渲染契约（含加载期校验落点 + 非字面量 value 行为钉死）、逐方言模板内容矩阵（PG/Oracle/MySQL/h2 至少四行，逐行证据等级 + h2 配置裁定 + h2-null 断言更新说明）、rollup/cube 路径裁定（ⓒ-A 推荐 + ⓓ 备选 + 被拒备选理由 + 文法形态 + **备选顺序语义与胜者钉死** + **非 GROUP BY 位置报错口径** + 错误码口径 + 成本清单含 enums 类与 ordinal 落点）、grouping 零文法落地形态裁定（函数表注册 + 门控 + 可选放置校验 + 元数取证）、方言矩阵逐格三态结论（含 mssql/MariaDB 补充取证、mysql rollup 超基线处置、`GROUPING SETS` 否决再确认）、**探针 transcript 已持久化 `ai-dev/audits/eql-wi0/`**、窗口缺口判定清单与 roadmap 回写措辞、FILTER 小节大纲、两类用例清单、前置状态复核与漂移锚点复核记录。（落档：`ai-dev/design/eql-cross-db-compat/2026-10-04-wi7b-phase0-adjudications.md` §0-§10 + `ai-dev/audits/eql-wi0/2026-10-04-h2-probe-transcript.txt`）
- [x] 本 Phase 纯裁定，不改产品代码：`No owner-doc update required`（裁定文档本身即交付物；owner doc 的矩阵/小节随各代码 Phase 落）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 1 - `sqlIntervalLiteral` 模板消费 + 逐方言配置 + h2 端到端（EC ③⑦）

Status: completed（2026-10-04）
Targets: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`、`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`、`TestDialectSqlIntervalLiteral`、`nop-orm-eql` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Feature`、`Proof`

- [x] **开工前置复核（回退防护）**：plan 03 与 plan 26 的 `Plan Status` 未回退（现均 `completed`，硬前置已满足；照 plan 26 对 WI0-WI3 的复核先例降级）——若发现回退则本 Phase 不得开工、保持 `planned` 并在 daily log 记录排队。
- [x] **修复前红**：h2 上 `select timestamp '2020-01-01' + interval '1 day'`（或等价 interval 查询）经 `AstToSqlGenerator` 输出 `interval 1 DAY` 形态、H2 真库执行报语法错（探针已证）——回归测试断言修复后输出模板渲染文本且 H2 执行通过，修复前红留痕于提交说明或日志。
- [x] 生成器消费点（按 Phase 0 裁定）：`AstToSqlGenerator` 新增 `visitSqlIntervalExpr` override——`dialect.getSqlIntervalLiteral()` 非 null → 模板渲染（value 子表达式先 visit 捕获文本、unit 取规范名）；null → 委托 super（既有裸输出逐字节不变，deprecated 语义与 owner doc `:295` 一致）；**TUMBLE 路径零触碰**（`visitSqlTumbleTableSource` 现状不动，守护用例锁定）。
- [x] 模板加载期校验（按 Phase 0 裁定）：未知 marker 方言加载期抛错 + 负向用例（WI0 marker 校验同纪律；无静默跳过——不允许坏 marker 静默按原文输出）。
- [x] 逐方言模板配置（按 Phase 0 矩阵）：PG/Oracle/MySQL 三方言 `*.dialect.xml` 配置（EC ③ 载体）+ h2 配置（CI 真库载体）；`dm`/`tdengine` 零配置（红线）。
- [x] 正向用例断言具体方言 SQL 文本（EC ③）：**PG/Oracle/MySQL 三形态各一条**输出断言（MySQL value 不加引号、PG/Oracle value 加引号——以各方言模板实际配置与探针/文档为准）；h2 一条编译输出断言 + **一条 H2 真库执行通过用例**（`JdbcTestCase` 基座，端到端：EQL 文本 → parse 归一化 → transform → 模板渲染 → H2 执行返回）。
- [x] 负向用例：模板未配置方言维持裸输出（BC 守护断言，不是报错——Phase 0 裁定登记）；坏 marker 方言加载期报错断言具体错误码；`dm`/`tdengine` 不配置的守护断言（零配置）。
- [x] 接线验证（Minimum Rules #23）：EQL interval 文本 → `SqlIntervalExpr` → `visitSqlIntervalExpr` override → `getSqlIntervalLiteral()` 模板的运行时连通（h2 端到端用例即载体，证明 accessor 从"有配置无消费"转为真实消费）。
- [x] 既有行为守护：`interval 1 day`/`interval '3 hours'` 两语法形态的解析与归一化不变（`TestEqlIntervalLiteralParse` 全量回归）；回显路径（`AstToEqlGenerator.visitSqlIntervalExpr:950-954`）输出逐字不变；未配置方言编译输出逐字节不变；TUMBLE 渲染不变。
- [x] 矩阵快照核实：`sqlIntervalLiteral` 是 `<sqls>` 配置非函数表成员——核实快照**零增量**（函数表视角）+ 门禁 exit 0；若 Phase 0 裁定改动影响函数表则同步快照。
- [x] owner doc（EC ③ 文档面）：INTERVAL 段（`:291-298`）更新——模板占位符契约、逐方言模板矩阵（`[实测]`/`[文档]`/`[未取证]`）、H2/PG 裸输出非法的探针结论、能力点登记表行（generator 方法 + 触发配置 + 未配置时行为）。
- [x] 语料 BC（EC ⑥）：`:nop-job-dao`/`:nop-auth-service`/`:nop-orm` 三份必测与 WI0 基线快照逐字一致（`TestEqlCorpusBaseline` 随全量运行通过）；不声称"7 个全测"。
- [x] `./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。

Exit Criteria:

- [x] 红→绿回归测试：h2 interval 查询修复前真库报错、修复后模板渲染文本 + 真库执行通过；PG/Oracle/MySQL 三形态文本断言齐（EC ③）；修复前红留痕。
- [x] 模板加载期校验负向用例断言具体错误码；未配置方言 BC 守护通过；TUMBLE 路径不变守护通过；无静默跳过。
- [x] 端到端接线用例通过（parse → transform → 模板渲染 → H2 执行）。
- [x] `TestDialectSqlIntervalLiteral` 已按 h2 配置更新（原三测骨架不弱化）；owner doc INTERVAL 段更新落档；矩阵快照门禁 exit 0。
- [x] `TestEqlCorpusBaseline` 通过（三份必测逐字一致）；`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。
- [x] 本 Phase 无 `.g4`/`EqlAST.xjava` 改动（`ast/_gen/` 零 diff）——若实施中发现必须动文法，停下按 scope change 处理并记录。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - `rollup`/`cube`/`grouping` 注册（路径按 Phase 0 裁定 + 方言排除）（EC ①⑦）

Status: completed（2026-10-04；clean install 门以 run3 双旗标排除记录为准）
Targets: 按 Phase 0 裁定路径——ⓒ-A：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`sqlGroupByItem` 备选 + `unreservedWord_`）、`nop-persistence/nop-orm-eql/model/antlr/Literals.g4`（`ROLLUP`/`CUBE` token）、`EqlAST.xjava`（1 个新 AST 类）+ `ast/_gen/` 重生成、`EqlASTBuildVisitor.java`、`EqlTransformVisitor.java`（能力门）、`AstToSqlGenerator.java`/`AstToEqlGenerator.java`（打印）；ⓓ：`EqlTransformVisitor.java` 或 transformer；`*.dialect.xml`（注册落点）；`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`；`nop-orm-eql`/`nop-dao` 测试

- Item Types: `Feature`、`Decision`（若实施中需偏离 Phase 0 裁定的局部形态）、`Proof`

- [x] **开工前置复核（回退防护）**：plan 03 与 plan 26 的 `Plan Status` 未回退（现均 `completed`，硬前置已满足）——若发现回退则本 Phase 不得开工、保持 `planned` 并在 daily log 记录排队。
- [x] **修复前红**：`select sum(v) from t group by rollup(g)` 解析通过但编译报 `ERR_EQL_UNKNOWN_FUNCTION`（现状实测）——回归测试断言修复后 lowering/门控行为，修复前红留痕；`cube`/`grouping` 同。
- [x] 文法与 AST（按 Phase 0 裁定路径）：ⓒ-A 则 `ROLLUP`/`CUBE` token（`nop-persistence/nop-orm-eql/model/antlr/Literals.g4`，定义在 `IDENTIFIER_` 之前，NULLS 先例）+ `unreservedWord_` 双登记 + `sqlGroupByItem` 新备选**前置**（altLabel 三约束；备选顺序与胜者钉死按 Phase 0 裁定，排后即死代码）+ 新 AST 类（`EqlAST.xjava` → `ast/_gen` 重生成；ordinal 尾部追加/插中段落点登记）+ 新 enums 类（`ROLLUP|CUBE`）+ 手写 visit；**`grouping` 零文法改动**——`*.dialect.xml` 函数表注册（`<native name="grouping">` 形态，现全方言函数表 0 注册）+ `EqlTransformVisitor.visitSqlRegularFunction` 按函数名门控（`onlyForWindowExpr` 先例 `:1551-1556` 同款）；ⓓ 则 transformer 路线（零文法、零 `ast/_gen`）。`.g4`/`EqlAST.xjava` 改动单独提交。
- [x] **transform 能力门与放置校验**（按 Phase 0 裁定）：rollup/cube 仅 GROUP BY 位置合法——**ⓒ-A 下非 GROUP BY 位置的报错机制 = 落 `SqlRegularFunction` → 函数表未注册 → `ERR_EQL_UNKNOWN_FUNCTION`（或 Phase 0 裁定的显式门），断言值随裁定口径**；grouping 的语义级放置校验（仅引用 GROUP BY 项）为**可选裁定项**（未注册方言已被 unknown-function 编译期报错兜底——Phase 0 裁定不做须登记理由，防范围蠕动）；方言矩阵逐格执行（Phase 0 结论）：`CUBE` 在 **MySQL/MariaDB 显式编译期报错**（EC ① 硬性用例）、mysql 函数式 `rollup` 超承诺基线报错（successor 触发条件登记）、`dm`/`tdengine` 一律报错（红线）、h2 三函数报错（探针 `[实测]` 无 → 负向载体用例，断言具体错误码并注明 `ai-dev/audits/eql-wi0/` 探针归档依据）。
- [x] 正向用例断言具体方言 SQL 文本（EC ①）：每个"注册并 lowering"格至少一条文本断言（如 postgresql/duckdb/oracle 的 `group by rollup(g)` → `group by ROLLUP(g)` 形态、`group by cube(a,b)`、`select grouping(g) … having grouping(g) = 0` 形态——以各方言实际 lowering 输出为准；identity 输出也要显式断言）；混合项形态（若裁定支持）至少一条。
- [x] 负向用例（EC ①⑦）：`CUBE` 在 mysql 与 mariadb 编译期报错（EC ① 硬性，两方言各一条）；h2 三函数各一条报错（含 `ai-dev/audits/eql-wi0/` 探针归档依据引注）；`grouping(a)` 在未注册方言报错（既有 `ERR_EQL_UNKNOWN_FUNCTION` 路径不回退的守护）；放置错误（`select rollup(a) from t` 非 GROUP BY 位置）编译期报错（机制随 Phase 0 口径——ⓒ-A 下默认函数表未注册 → unknown-function）——**无静默降级、无静默跳过**（不允许 rollup 不支持时静默按普通函数输出原文）。
- [x] 接线验证（Minimum Rules #23）：`GROUP BY ROLLUP(...)` 文本 → parse（新备选/transformer）→ transform 门 → 打印的端到端用例（至少一条覆盖 postgresql 或 duckdb 文本输出 + 一条 mysql/mariadb 报错分支）。
- [x] 约束 4 用例：同一 `rollup` 查询在支持方言（输出原文形态）与不支持方言（编译期报错）的"一致性/不等价"对照（Phase 0 清单落实）。
- [x] 矩阵快照**预期有增量**：grouping 函数表注册是**确定的真实增量**（现全 18 个方言 XML 函数表 0 注册，2026-10-04 实测）；rollup/cube 的快照口径随 Phase 0 裁定（ⓒ-A 下 GROUP BY 语义走新 AST 门控、可能不进函数表——登记口径以裁定为准）。重生成 `TestDialectCapabilityMatrix` 快照 + `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（EC ③ 同款机制）；增量内容与 Phase 0 矩阵逐格一致。
- [x] 真库证据分派（EC ⑦，差异登记第 2 条口径）：h2 = 负向载体（探针 `[实测]` 记录归档）；oracle/postgresql/duckdb/mysql(grouping)/mssql（若取证支持）= 一手文档引证 + 文本断言替代证据，逐方言列清单；`dm`/`tdengine` = `[未取证]`，不计覆盖率。
- [x] owner doc（EC ① 文档面）：`eql-and-database-compatibility.md` 新增 rollup/cube/grouping 小节——逐方言覆盖矩阵（三态 + 证据等级）、放置规则、错误码、`GROUPING SETS` 永久否决引用、能力点登记表行；roadmap ledger `CUBE/ROLLUP(...)/GROUPING()` 行收口标注。
- [x] 语料 BC（EC ⑥）：三份必测逐字一致（Phase 0 已证 0 命中，棘轮实测兜底）；`sqlGroupByItem` 既有形态 `group by a, b` 输出逐字不变守护用例。
- [x] 若 ⓒ-A：`./mvnw clean install -T 1C` 通过（`ast/_gen` 重写后 codegen 全链路 + 114 个 orm.xml 回归，roadmap WI7 收口注记口径）；若 ⓓ：`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。

Exit Criteria:

- [x] 红→绿回归测试断言具体 SQL 文本/错误码：每"注册并 lowering"格正向文本用例 + `CUBE` 在 mysql/mariadb 显式报错（EC ①）+ h2 三函数负向 + 放置错误报错；修复前红留痕。
- [x] 无静默跳过：不支持方言一律编译期响亮报错，不允许回退原文输出或空实现（Minimum Rules #24）。
- [x] 端到端接线用例通过（parse → 门 → 打印/报错）；`group by a, b` 既有形态逐字不变。
- [x] 矩阵快照增量与 Phase 0 矩阵逐格一致 + 门禁 exit 0；真库证据分派清单已落 owner doc（含 `[未取证]` 标注，未计入覆盖率）。
- [x] `TestEqlCorpusBaseline` 通过（三份必测逐字一致）。
- [x] 生成物纪律：`.g4`/`EqlAST.xjava` 改动单独提交；重生成后 `git diff` 逐一检查且与裁定路径一致（ⓓ 下 `ast/_gen/` 零 diff）；无 lexer token 删除；若 ⓒ-A 则 `./mvnw clean install -T 1C` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 窗口 OVER/frame 现状核验收口 + roadmap WI7b 行回写（EC ②）

Status: completed（2026-10-04）
Targets: `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlWindowGrammarParse.java`（补缺口用例，若有）、`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI7b 行 + 门控矩阵单元格）、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（窗口段交叉引用）、`ai-dev/logs/`

- Item Types: `Proof`、`Follow-up`（回写为文档对齐，非缺陷修复）

- [x] **开工前置复核（回退防护）**：plan 03 与 plan 26 的 `Plan Status` 未回退（现均 `completed`；本 Phase 含 roadmap 文件改动，以前置 closure 状态稳定为前提，避免状态交叉漂移）。
- [x] **EC ② 逐项对照审计**：窗口 `OVER` 两种形态（带/不带 partition）+ frame 的"正确 SQL 文本用例"——逐项核对 `TestEqlWindowGrammarParse`（parse 断言）、`__snapshot/{dialect}.window.sql`（10 方言文本 golden）、`TestH2WindowFrameMatrix`/`TestH2WindowFrameCompileEndToEnd`（真库）现状，产出"已满足/缺口"结论并记录；缺口（预判在边角形态，如"不带 partition 仅 frame""不带 partition 仅 order by 的文本断言"）在本 Phase 补齐用例（**不改动产品代码**——若审计发现产品行为缺口则停下登记 scope change）。
- [x] **roadmap WI7b 行定价回写**（照 plan 26 Phase 4 对 WI7 行的原地注记先例，不删原文、注明修订依据 = 本 plan Phase 0 实测）：① WI7b 节"窗口 `OVER` 放宽（今天 `over(order by …)` 不带 partition 就失败）+ frame（需 `SqlWindowExpr` 增字段）"加执行终态注记——实测已由 `add-stream-sql` 带入（五 AST 节点 + 文法全备选 + 三道 frame 门 + 10 方言 golden + H2 真库测试），本 plan 只做核验收口；② 门控矩阵 WI7b 行 Protected Area 单元格"生成管线（`SqlWindowExpr` 增字段）"加注记——窗口半区零 `.g4`/`EqlAST.xjava` 触碰，实际生成管线触碰面以 Phase 2 裁定路径为准（ⓒ 才触）；③ 写明后继（WI12）读取口径。
- [x] owner doc 交叉引用：`eql-and-database-compatibility.md` 窗口段（`:38-52`）补一行与本 plan 裁定文档的互链（防两 roadmap 编号空间混用的既有注记 `:40` 保持不变）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0（既有基线 3 项 warning：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，不顺手修、只比对新增量）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [x] EC ② 逐项对照结论已记录（已满足项给出现有测试名/golden 文件；缺口项已补齐用例）；无产品代码改动（或已按 scope change 处理并登记）。
- [x] roadmap WI7b 行与门控矩阵单元格回写完成（原地注记先例、后继读取口径写明）。
- [x] owner doc 交叉引用落档；`check-doc-links --strict` 新增 broken link = 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - `count(*) FILTER` 文档引导 + roadmap ledger 收口（EC ④）

Status: completed（2026-10-04）
Targets: `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（FILTER 引导小节）、`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（ledger `count(*) FILTER` 行收口 + WI7b 行状态流转）、`ai-dev/logs/`

> 本 Phase 纯文档：`./mvnw test` 等构建验证不适用（按 guide"纯文档计划"条款在本 Phase 内豁免，Closure Gates 的构建门禁由 Phase 1/2 的命令承担）。

- Item Types: `Feature`（文档交付）、`Proof`

- [x] **开工前置复核（回退防护）**：plan 03 与 plan 26 的 `Plan Status` 未回退（现均 `completed`，同 Phase 3）。
- [x] owner doc 新增 `count(*) FILTER` 引导小节（EC ④，按 Phase 0 大纲）：① 引导写法 `sum(case when p then 1 else 0 end)`；② **空分组提醒**：无行满足条件时 `sum(...)` 返回 NULL 而 `count(*) FILTER` 语义返回 0——需要 0 语义时套 `coalesce(sum(...), 0)`；③ 有/无/未取证矩阵——已确认有：PG/DuckDB/SQLite；已确认无：MySQL/MariaDB/MSSQL/Oracle（其 3 处 `FILTER` 均为 analytic view 的 `FILTER FACT`）；未取证：DB2（IBM 文档 403/JS 门禁）、TDengine（红线无一手文档）、H2（h2database.com 五页零 `FILTER`，仅 `h2.dialect.xml:45` 保留 FILTER 关键字 + 本 plan 探针旁证记录）；④ "为什么不进语法"的约束 3 引用（无法跨多数方言无损 lowering）。
- [x] roadmap ledger `count(*) FILTER` 行收口：标注"文档引导已由 WI7b 交付"并指向 owner doc 小节（照 ledger 其他行收口先例，不删原文）。
- [x] roadmap WI7b 行状态流转：本 plan 独立 closure audit 通过后，按 roadmap Status values 纪律执行 `todo` → `planned` → `done`（不得提前；`planned` 的翻转可在本 plan 转入执行时做）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0（既有基线 3 项 warning 不顺手修、只比对新增量）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [x] owner doc FILTER 引导小节已落档：引导写法 + 空分组 NULL-vs-0 提醒 + 三态矩阵（EC ④ 全要素）。
- [x] roadmap ledger 行已收口标注；WI7b 行状态与 plan 状态一致。
- [x] `check-doc-links --strict` 新增 broken link = 0；`ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [ ] `sqlIntervalLiteral` 模板落地（EC ③）：占位符契约 + 加载期校验 + PG/Oracle/MySQL 三形态正确输出用例 + h2 端到端真库执行 + 未配置方言 BC 守护——全部有断言具体文本/错误码的红→绿用例。
- [ ] `rollup`/`cube`/`grouping` 注册（EC ①）：覆盖矩阵**按方言名列全**（三态 + 证据等级，无比例表述）；`CUBE` 在 MySQL/MariaDB 显式报错用例存在；`GROUPING SETS` 未注册（永久否决维持）。
- [ ] 窗口 `OVER` 两种形态（带/不带 partition）+ frame 各有正确 SQL 文本用例（EC ②）——既有测试对照审计结论 + 缺口补齐记录在案。
- [ ] `count(*) FILTER` 文档引导小节已写入 `eql-and-database-compatibility.md`，含"空分组下 `sum` 返回 NULL 而 `count` 返回 0"提醒（EC ④）。
- [ ] **真库证据分派（EC ⑦，差异登记第 2 条口径）**：**h2 探针 transcript 已持久化于 `ai-dev/audits/eql-wi0/`**（摘要：三函数 × H2 2.3.232/2.4.240 × `MODE=Oracle`/`MODE=PostgreSQL` 全部 `Function "ROLLUP"/"CUBE"/"GROUPING" not found`（90022）；`INTERVAL '1' DAY` 执行成功 / 裸 `INTERVAL 1 DAY` 语法错（expected "-, +, string"）/ `INTERVAL '1 DAY'` 数据错（22007）——以 Phase 0 重跑的归档为准）+ h2 负向载体用例 + 不可真库验证方言逐个列出并给替代证据（`[实测]`/`[文档]`/`[未取证]` 逐行标注）；`dm`/`tdengine` 未计入覆盖率；interval 半区有 h2 端到端执行证据。
- [ ] **既有 EQL 语料 BC（EC ⑥）**：`:nop-job-dao`(NopJobTask)、`:nop-auth-service`(test)、`:nop-orm`(test) 三份必测与 WI0 基线快照逐字一致；未声称"7 个全测"（`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份由 roadmap 级 EC 第 8 条 + WI0 静态快照覆盖）；interval 语法/回显、`group by a, b`、既有窗口查询形态逐字不变守护用例通过。
- [ ] 约束 4 的"一致性 + 不等价"两类用例/差异登记齐备（interval 逐方言形态、rollup 支持/报错对照、FILTER 引导语义差异至少各一项）。
- [ ] `./mvnw test -pl :nop-orm-eql,:nop-dao,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（roadmap WI7b EC ⑤ 命令）——**"全绿"口径**：本 plan 相关模块全绿 + `nop-stream-runtime` 既有 flake 与收口日实跑基线一致即豁免（单跑 PASS 复现，豁免清单记入 Closure Evidence），不得据此判本 plan 失败。
- [ ] 若 Phase 2 裁定 ⓒ（触 `ast/_gen`）：`./mvnw clean install -T 1C` 通过（codegen 全链路 + 114 个 orm.xml 回归，roadmap WI7 收口注记口径）；若 ⓓ：本门以 Phase 2 的 `-pl` 全绿替代并记录。
- [ ] 生成物纪律：每个 Phase 的 `.g4`/`EqlAST.xjava` 改动单独提交（`git log` 文法提交与逻辑提交分离可审计）；每步重生成后 `git diff` 逐一检查；`ast/_gen/` 的 diff 与裁定路径一致（ⓐ/ⓑ/ⓓ 下零 diff）；无 lexer token 删除、`EqlASTKind` ordinal 未被持久化或比较。
- [ ] `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（Phase 2 预期真增量已同步；Phase 1 零增量已核实）。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0（既有基线 3 项 warning：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，不顺手修、只比对新增量）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/37-eql-wi7b-analytic-features.md --strict` 退出码 0。（终门留白：随独立 closure audit 后的 Closure Evidence 写入一并复跑）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中（anti-hollow："只解析不门控/只打印不消费模板/放置不校验"正是半实现形态）。
- [ ] owner doc 已同步到 live baseline（INTERVAL 段收口、rollup/cube/grouping 矩阵段、FILTER 引导小节、窗口段交叉引用、能力点登记表行），`docs-for-ai/INDEX.md` 与 `04-reference/source-anchors.md` 若路由/锚点变化已更新。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）两条端到端调用链真实连通：interval 文本 → parse 归一化 → `visitSqlIntervalExpr` → `getSqlIntervalLiteral()` 模板渲染 → h2 真库执行；`GROUP BY ROLLUP(...)`/`GROUPING()` 文本 → parse（rollup/cube 新备选或 transformer；grouping 既有 `SqlRegularFunction` 通路）→ 函数表/放置/方言门 → 方言 SQL 输出或响亮报错（不只是文法分支/模板配置存在）；（b）无空方法体/静默跳过/no-op（模板坏 marker 加载期抛错、不支持方言编译期报错、放置错误响亮失败）；（c）未配置模板方言与既有 `group by`/窗口查询输出逐字节不变。
- [ ] **独立 closure audit 已完成并由不同 `task_id` 的子 agent 执行**，evidence 写入本 plan `Closure` 段（guide #26/#27；roadmap WI7b EC ⑧）；roadmap Work Item Status 的 WI7b 行据此才可 `planned` → `done`（见 roadmap Status values）。

## Deferred But Adjudicated

### `GROUPING SETS`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap ledger **永久否决**（MySQL/MariaDB 全版本均无，MySQL 8.4 `SELECT` 文法无 `GROUPING SETS`）——不注册是 roadmap 明文裁定，非本 plan 新裁量。
- Successor Required: `no`
- Successor Path: 无（roadmap ledger 行已标永久否决）。

### MySQL/MariaDB 的函数式 `ROLLUP`（8.4.3+，超出 G-1 承诺基线 8.0.31）

- Classification: `watch-only residual`
- Why Not Blocking Closure: G-1 承诺基线为 MySQL 8.0.31（2026-10-03 门禁裁决采纳），高于承诺基线的版本特性不默认开启（G-1 纪律）；mysql 的 rollup 格在本 plan 内显式报错，语义无损（响亮失败）。
- Successor Required: `no`（若产品出现"承诺基线上调至 8.4.3+"的需求，先走 G-1 门再登记 roadmap）。
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（G-1 门 + 新登记行）。

### `WITH ROLLUP`/`WITH CUBE` 旧修饰符形态 与 `GROUPING_ID()`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap WI7b 只点名函数式 `ROLLUP(...)`/`CUBE(...)`/`GROUPING()`；旧修饰符与 `GROUPING_ID` 无 roadmap 登记即无 owner 授权（AGENTS.md「AI Must Ask Before」第 1 条）。
- Successor Required: `no`（出现需求先登记 roadmap 再立项）。
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（新增登记行）。

### `interval '1-2' year to month` 多字段 qualifier 语法

- Classification: `watch-only residual`
- Why Not Blocking Closure: WI1 已收口的现状——多字段值在归一化层显式报错、qualifier 形态文法层失败走 antlr 错误码（owner doc `:296`），响亮且无静默风险；roadmap WI7b 未点名该形态。
- Successor Required: `no`（出现真实需求先登记 roadmap）。
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（观察项）。

## Non-Blocking Follow-ups

- `h2.dialect.xml` 的 FILTER 保留关键字（`:45`）与"H2 实际无 FILTER 语法"的现状差异——随 FILTER 文档小节登记，不删关键字（方言契约变更需 G-4 类确认，收益为零）。
- CI 真库验证面扩展（mysql/oracle/pg docker 化或 testcontainers 实跑）超出本 roadmap——若立项，rollup/cube/grouping 的 `[文档]` 格在其上转 `[实测]`。
- 窗口 golden 快照（`__snapshot/*.window.sql`）当前不含 h2/postgresql 的独立说明——两者由 `TestH2WindowFrame*` 真库测试覆盖，快照缺方言属既有覆盖面选择，非缺口（Phase 3 审计记录在案）。
- AstGrammarBuilder 的 altLabel/prop 约束与"隐式 token 不可达"模式文档化（现散在 WI1/WI5/WI7/本 plan 的执行期记录中）——若 `docs-for-ai/02-core-guides/` 后续增设 EQL 文法指南，随迁。

## Closure

Status Note: （完成或关闭时填写：为什么这个 plan 可以关闭）
Completed: （YYYY-MM-DD）

Closure Audit Evidence:

- Reviewer / Agent: （独立审阅者或独立子 agent，须与执行者不同 task_id）
- Audit Session: （session/task ID）
- Evidence:
  - （每条 Exit Criterion 与 Closure Gate 的验证结果 PASS/FAIL + live code path / test name）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/37-eql-wi7b-analytic-features.md --strict` 退出码
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 退出码
  - Anti-Hollow 检查结果（两条端到端调用链追踪）
  - Deferred 项分类检查（确认无 in-scope live defect 被降级）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
- （或者明确写 no remaining plan-owned work）
