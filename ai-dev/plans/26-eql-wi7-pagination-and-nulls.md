# 26 EQL WI7 — 分页两种语法形态（`FETCH FIRST` / `OFFSET..FETCH NEXT`）+ `NULLS FIRST/LAST` 接线（`nullsFirst` + `defaultNullsFirst` + `supportNullsFirst`）+ `ORDER BY … COLLATE` ledger 裁定

> Plan Status: active
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI7 节 Exit Criteria ①-⑨ + 约束 1-4 + 门控矩阵 WI7 行 + 「明确延后项」`ORDER BY … COLLATE` 行 + Cross-Cutting 全部 + 关键约束与红线节，v7.2）；Current Baseline 全部代码事实于 2026-10-03 在本工作区分支 `feat/eql-cross-db-compat`（WI3 19 个提交落地后）逐文件实测核验，行号为当日实测参考值、执行时以 Phase 0 锚点复核为准
> Related: `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`（硬前置，已 `completed`）、`ai-dev/plans/03-eql-wi1-class-a-defects.md`（硬前置，已 `completed`；altLabel 备选分支 + 构建期归一化的结构先例）、`ai-dev/plans/19-eql-wi2-class-b-defects.md`（硬前置，已 `completed`；BUG-4 LIMIT 回显与 `TestEqlSelectAllLimitEcho`/`TestEqlSelectAllLimitCompile` 回归先例）、`ai-dev/plans/20-eql-wi3-dialect-config-fixes.md`（硬前置，已 `completed`——独立 closure audit 于 2026-10-04 通过、roadmap WI3 行已翻 `done`，排队门已满足，见下方硬前置声明；`defaultNullsFirst` accessor + 8 方言断言测试 + 移交链 javadoc 是本 plan 直接消费的移交物）、`ai-dev/plans/24-eql-wi5-sql92-function-syntax.md`（结构先例：altLabel 三约束/每 Phase 开工前置复核/全绿口径直接复用）

## 硬前置声明（开工门，非空话）

roadmap 门控矩阵 WI7 行明文硬前置 = **WI1、WI2、WI3、G-1**。实测现状（2026-10-04 修订时复核）：WI0/WI1/WI2/WI3 **全部已 `completed`**（plans 02/03/19/20；WI3 于 2026-10-04 经独立 closure audit 收口，commit `c54fcb5f63`，roadmap Work Item Status 的 WI3 行已翻 `done`）；G-1 已裁决（2026-10-03，`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`，承诺基线 Oracle 23ai / MySQL 8.0.31 / MariaDB 10.2 / PG 15 / MSSQL 2022 / SQLite 3.39）。⇒ **四项硬前置全部满足，排队门已打开：本 plan 的全部 Phase（含 Phase 0 与代码 Phase）均可开工**。保留机制：每次开工一个 Phase 前，执行者必须复核四个前置 plan 的 `Plan Status` 未回退（若出现回退——例如 plan 被重开——则恢复排队纪律并记录于当次 daily log）。

**本 plan 与 WI4 无依赖**：roadmap 依赖图中 WI4 只指向 WI5 与 WI8；WI7 的上游只有 WI1/WI2/WI3/G-1。⇒ plan 21（WI4）的排队状态**不阻塞**本 plan；对称地，**plan 24（WI5）对 plan 21（WI4）的硬门不受本 plan 任何影响**——本 plan 不得动 `IEqlAstTransformer`/`EqlCompiler` 接线面。

## Purpose

收口 roadmap WI7 的三个结果面：

1. **分页**：让 `FETCH FIRST n ROWS ONLY` 与 `OFFSET n ROWS FETCH NEXT m ROWS ONLY` 两种 SQL 标准分页语法在 EQL 中可解析（今天两者都解析失败——文法缺口见 Current Baseline），归一化装入既有 `SqlLimit{limit, offset}`，经既有 `IPaginationHandler` 双实现 lowering 到各库形态，回显路径输出规范化文本。
2. **NULL 序**：让 `ORDER BY … NULLS FIRST/LAST` 可解析并赋值给已预留的 `SqlOrderByItem.nullsFirst`（Boolean 三态）；`AstToSqlGenerator` 侧建立**方言门**（`supportNullsFirst`：是否原生接受该子句）与**默认序消费**（`defaultNullsFirst`：缺省 NULL 序事实）两个消费点——补齐 `IDialect.isSupportNullsFirst()` accessor（WI3 移交链的另一半），并落实 roadmap WI3 对 `defaultNullsFirst` 消费点的显式移交（owner=本 plan）。
3. **ledger 裁定**：对「明确延后项」的 `ORDER BY … COLLATE` 行做出**二选一裁定**（落地 or 显式移出 + successor + 触发条件）并落档——roadmap 要求"不允许既挂 ledger 又无人提及"，且其重新评估触发条件（G-1 裁决）已于 2026-10-03 发生。

每个结果面具备：断言正确方言 SQL 文本的正向用例（红→绿）、方言不支持时编译期响亮报错的负向用例（无静默降级）、既有 EQL 语料 BC 棘轮逐字一致、owner doc 同步。

## Current Baseline

> 所有锚点为符号定位优先；file:line 为 2026-10-03（WI3 落地后）实测参考值。执行时以 Phase 0 锚点复核为准（WI4/WI5 等并行 plan 的文法提交会平移 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`/`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java` 行号）。

### 分页：文法缺口与既有 lowering 管线（2026-10-03 实测）

- **AST**：`SqlLimit` 仅有两个字段——`limit: SqlExpr` 与 `offset: SqlExpr`（`nop-persistence/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava:240-243`；生成物 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/_gen/_SqlLimit.java` 两字段 + `validate()` 无必填校验——**空 `SqlLimit` 节点可通过 validate，手写 visit 必须完全重写、逐字段赋值**）。手写壳类 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/SqlLimit.java` 为空扩展。
- **文法**：`sqlLimit`（`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4:217-219`）只有单一备选 `LIMIT limit=sqlExpr_limitRowCount (OFFSET offset=sqlExpr_limitOffset)?`；`sqlExpr_limitRowCount`/`sqlExpr_limitOffset`（`DMLStatement.g4:221-227`，同文件）= `sqlNumberLiteral | sqlParameterMarker`（`?` 参数 marker 天然可用于新形态）。`sqlSelect` 的挂载点在 `DMLStatement.g4:103`（`orderBy` 在 `:102`）。**没有任何 `FETCH`/`ROWS` 备选 ⇒ 两种标准形态今天解析失败**（roadmap 断言经机制核验成立）。
- **token 面零缺口（关键：分页半区零 lexer 改动）**：`LIMIT`（`Keyword.g4:329`）、`OFFSET`（`Keyword.g4:333`）、`NEXT`（`Keyword.g4:514`）、`FETCH`（`SQL92Keyword.g4:402`）、`FIRST`（`SQL92Keyword.g4:406`）、`ONLY`（`SQL92Keyword.g4:526`）、`ROW`（`SQL92Keyword.g4:590`）、`ROWS`（`SQL92Keyword.g4:594`）**全部已是保留 token**——分页文法扩展只需改 parser 规则，**不触 lexer、不触 token 重编号红线**。
- **lowering 输出侧今天已双形态就绪（roadmap 定价前提的重要修正）**：`IPaginationHandler.buildPageExpr(limitExpr, offsetExpr, sqlExpr)`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/pagination/IPaginationHandler.java`）**只吃 limit/offset 两个表达式、无任何"形态"参数**。两个实现都已存在：`LimitOffsetPaginationHandler`（输出 `LIMIT n OFFSET m`；`default.dialect.xml:11` 显式配置、`es.dialect.xml:14` 同）与 `OffsetFetchPaginationHandler`（输出 `OFFSET n ROWS FETCH NEXT m ROWS ONLY`，且 offset 缺省时自动补 `OFFSET 0 ROWS` 前置桥接；`mssql.dialect.xml:11` 与 `oracle.dialect.xml:12` 显式配置）。**h2 的 OffsetFetch 配置处于注释态**（`h2.dialect.xml:20`）→ h2 走 default 继承的 LimitOffset。
- **执行路径消费点**：`AstToSqlGenerator.visitSqlQuerySelect`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java:95-105`，`buildPageExpr` 调用在 `:101`）——limit 非空时整体走分页 handler、`printLimit` 钩子刻意不输出（WI2 BUG-4 注释 `:144-152` 在案，行为由 `TestEqlSelectAllLimitCompile` 钉死）。⇒ **`select … limit 5` 在 oracle 上今天就已输出 `OFFSET 0 ROWS FETCH FIRST 5 ROWS ONLY`**——WI7 新增的只是**输入侧文法接受**，不是输出侧能力。
- **回显路径**：`AstToEqlGenerator.visitSqlLimit`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java:635-644`）打印 `limit x offset y`（规范化形态）；`printSelect` 在 `:487-496` 经既有 `visitSqlLimit` 钩子回显（WI2 BUG-4 修复）。既有回归：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlSelectAllLimitEcho.java`（断言 `limit 10 offset 5` 回显不丢）与 `.../compile/TestEqlSelectAllLimitCompile.java`——本 plan 不得回退其行为。
- **transform 层**：`EqlTransformVisitor.visitSqlQuerySelect` 对 `orderBy`/`limit` 做普通子节点遍历（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:345-350`）——归一化装入既有字段后 transform 零改动。

### NULLS FIRST/LAST：字段已预留、文法不接受、SQL 侧无门无消费（2026-10-03 实测）

- **字段**：`SqlOrderByItem.nullsFirst`（`java.lang.Boolean` 三态）与 `collate`（`java.lang.String`）均已在生成类中（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/_gen/_SqlOrderByItem.java`；`EqlAST.xjava:232-238`：`expr`（mandatory）+ `collate` + `asc` + `nullsFirst`）。`EqlASTKind` 中 `SqlOrderByItem` ordinal 24、`SqlLimit` ordinal 25（`EqlASTKind.java:54-56`），共 **67** 个常量。
- **文法**：`sqlOrderByItem`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:369-371`）= `expr=sqlExpr asc=(ASC | DESC)?`——**不接受 `NULLS`**（roadmap WI3 差异登记的实测确认；roadmap 所记 `:365-367` 已漂移至 `:369-371`）。生成的 `visitSqlOrderByItem`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/_EqlASTBuildVisitor.java:983-984` 附近）对 `asc=(ASC|DESC)` 走抽象转换方法 `SqlOrderByItem_asc(Token)` → 手写实现转 boolean——**这是 `nullsFirst=(FIRST | LAST)` 赋值的直接同构先例**（同一备选内加 prop 赋值，无需 altLabel、无 duplicate-prop-label 问题）。
- **token 面**：`NULLS` 在全部 9 个 `.g4` 文件中**零 token**（`NULL` 是保留 token `Keyword.g4:265`，`NULLABLE`/`NULLIF` 另有其 token；`NULLS` 词法落 `IDENTIFIER_`）⇒ `NULLS` 进入文法必须走 `unreservedWord_`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:72-96`）加词——**WI5 遮蔽放开的同款 parser 规则改动，零 lexer 改动**。`FIRST`/`LAST` 已是保留 token（`nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4:406/:474`），可直接用于 parser 规则赋值。
- **回显侧已消费（无门）**：`AstToEqlGenerator.visitSqlOrderByItem`（`AstToEqlGenerator.java:618-633`）在 `nullsFirst != null` 时打印 `nulls first `/`nulls last `——**无任何方言能力判断**（roadmap 所记 `:589-595` 已漂移；"无条件打印"指无门，不是不判空）。
- **⚠️ 执行 SQL 侧今天同样会（无门）打印**：`AstToSqlGenerator extends AstToEqlGenerator`（`AstToSqlGenerator.java:75`）——继承的 `visitSqlOrderByItem` 使执行路径在字段非 null 时也直接输出子句。今天不可达仅因**全仓无任何赋值方**（`setNullsFirst` 的调用方：grep 全 `nop-orm-eql` main = 0 处；文法不赋值、transform 不赋值）。⇒ **本 plan 若只加文法而不同批加门，就是把"字段不可达"变成"MySQL 等不支持子句的方言收到非法 SQL"——排序原则"只打印不设门 = 净回归"的直接命中面**。文法、门、默认序消费必须同批落地（见 Phase 编排）。
- **支持矩阵（XML 现值，2026-10-03 实测）**：`supportNullsFirst="true"` 恰 4 处——`duckdb.dialect.xml:21`、`h2.dialect.xml:25`、`oracle.dialect.xml:102`、`postgresql.dialect.xml:94`（roadmap 所记 h2:17/oracle:99 已因 WI3 注释块/补配漂移）。`defaultNullsFirst`（dialect 根属性，非 features）现 **8** 个 XML：`mysql:7`/`mssql:5`/`dm:8`/`h2:7`（WI3 第 11 项补配）= `true`；`tdengine:5`/`duckdb:9`/`postgresql:5`/`oracle:5` = `false`；未配置（`db2`）= 模型 null → accessor false（保守缺省）。
- **accessor 现状**：`supportNullsFirst` **连 `IDialect` accessor 都没有**——只有冻结生成类 `_DialectFeatures.getSupportNullsFirst()`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/_gen/_DialectFeatures.java:348-350`）的模型层 Boolean getter；`IDialect.java:130` 的 javadoc `{@link #isSupportNullsFirst()}` 是 WI3 预写的**前向引用**（方法尚不存在，javadoc link 不阻断编译）。模型字段在 xdef 已声明（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef:54` features 属性；`:15` 为 `defaultNullsFirst` 根属性）⇒ accessor 补齐 = `IDialect` + `DialectImpl` 两处手写，**零 xdef 改动、零 retention 改动、零生成**（`_DialectModel.getDefaultNullsFirst()` 亦已在冻结生成类中，`_DialectModel.java:283-285`）。

### WI3 移交链（本 plan 是被移交的 owner，2026-10-03 实测）

- **accessor**：`IDialect.isDefaultNullsFirst()`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java:138`）+ `DialectImpl.isDefaultNullsFirst()`（`DialectImpl.java:356-358`，`Boolean.TRUE.equals(dialectModel.getDefaultNullsFirst())`，读 dialect 根属性）。
- **移交标注原文**（`IDialect.java:124-137` javadoc）："⚠️ 消费点唯一 owner = roadmap WI7（NULLS FIRST/LAST 文法接受 + 门控 + 默认序行为）；WI3 交付止于 accessor + 配置正确性断言，生成器消费不得在 WI3 声称完成"；`DialectImpl.java:351-355` 同款："暂无生成器消费方——消费点唯一 owner = roadmap WI7……本 accessor + 配置断言即 WI3 交付面"。
- **既有测试**：`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDefaultNullsFirstFlag.java`（commit `42703f21e2`）——`testEightDialectAccessorContract`（8 方言：mysql/mssql/dm/h2=true，tdengine/duckdb/postgresql/oracle=false）、`testInheritanceAndUnconfiguredDefault`（mysql5.7 继承 true / db2 未配置→false）、`testSupportNullsFirstUntouched`（4 方言经模型层锁 `supportNullsFirst=true` 未被触碰，javadoc 明言"accessor 归 WI7"）。**本 plan 落地 accessor 后应把该测试的模型层断言升级为 accessor 断言（保持原三测不弱化）**。
- **owner doc 移交记录**：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md:126`（supportNullsFirst 行："尚未接线……accessor 补齐与门控归 EQL 兼容 roadmap WI7"）与 `:246-249`（"defaultNullsFirst 与 supportNullsFirst：正交两标志"小节，明言"生成器消费唯一 owner = EQL 兼容 roadmap WI7"）——落地后须回写。

### 方言默认 NULL 序的事实与能力上限（roadmap EC ④⑤ 的证据面）

- 两类默认值（roadmap EC ④）：**默认 NULLS LAST**（ASC 下 null 在后）——oracle（`oracle:5 defaultNullsFirst="false"`）、postgresql、duckdb、tdengine；**默认 NULLS FIRST**（ASC 下 null 在前）——mysql、mssql、dm、h2（WI3 补配）。`dm`/`tdengine` 无一手文档，其 DESC 方向行为按红线标 `[未取证]`、不得计入覆盖率。
- **单个布尔无法表达方向相关 NULL 序**（roadmap EC ⑤ 明文的能力上限，本 plan 不解决、只锁定证据与文档）：DuckDB 官方文档明写 "DuckDB keeps `NULLS LAST` even for `DESC` ordering, whereas PostgreSQL places `NULL`s first on `DESC`"（duckdb.dialect.xml 既有注释同引）；H2 的 `LOW`/`HIGH` 是方向相对的（"NULL values are considered as smaller/larger … **during sorting**"，H2 另有方向无关的 `FIRST`/`LAST` 设置——WI3 表第 11 行登记的"单布尔不足"最强证据）。
- 正交性：`defaultNullsFirst`（缺省 NULL 序**事实**）与 `supportNullsFirst`（是否原生接受子句）正交——duckdb `false`+`true` 同立且都正确（`duckdb.dialect.xml:9` 与 `:21`）。

### 生成管线事实（决定 Protected Area 与提交纪律）

- `nop-orm-eql` 侧生成管线是活的：`nop-persistence/nop-orm-eql/precompile/gen-eql-parser.xgen`（渲染 `nop-persistence/nop-orm-eql/model/AntlrParserConfig.json` → `.g4` 产物）与 `precompile/gen-eql-ast.xgen`（渲染 `nop-persistence/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava` → `/nop/templates/ast` 全量生成）；重生成命令 `./mvnw generate-sources -pl :nop-orm-eql -am`。实测现 `EqlParser.java` **10927 行**（roadmap 10785 → plan 24 记 10904 → WI2/WI3 文法提交再漂移）、`_EqlASTBuildVisitor.java` **1819 行**、`ast/_gen/` **77 个文件**（与 roadmap 一致）。
- **本 plan 默认形态（候选 A，见 Phase 0）不触 `EqlAST.xjava`/`ast/_gen/`**：`.g4` 的 `sqlLimit`/`sqlOrderByItem` 改动只重生成 `EqlParser.java`/`_EqlASTBuildVisitor.java`/`EqlLexer*`/`Eql.interp`/`*.tokens`；`git status` 中 `ast/_gen/` 零 diff 是每 Phase 回归证据。**`EqlASTKind` ordinal 重编号只在新增 AST 类时发生；给既有类加字段不改 Kind 常量**（`EqlASTKind` 67 常量一一对应 AST 类）——候选 B（新增 `SqlLimit` 字段）的真实成本是 `EqlAST.xjava` 改动 + `ast/_gen` 重生成 + `_SqlLimit.java` 内容变化，ordinal 不动；新增 AST **类**才触 ordinal 红线。roadmap 红线原文不变：**禁止把 `EqlASTKind.ordinal()` 持久化或跨版本比较**。
- **altLabel/prop 工具约束**（WI1/WI5 两轮执行期记录，`ai-dev/plans/24-eql-wi5-sql92-function-syntax.md` Current Baseline）：多备选规则每备选必须解析为 AST 节点；同一 prop 不能出现在同一规则的不同备选（`nop.err.antlr4.grammar.duplicate-prop-label`）⇒ `sqlLimit` 新备选**不得**复用 `limit=`/`offset=` prop 名，用独立 prop 名 + altLabel（`# SqlLimit_…` 后缀截断后同为既有类 `SqlLimit`，`sqlIntervalExpr` 双备选同构先例 `BaseRule.g4:346-352`，即 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:346-352`），手写 `EqlASTBuildVisitor` 完全重写 visit（生成体只建空节点，而 `SqlLimit` 无必填校验 ⇒ 空节点会**静默通过 validate**——比 WI1 的 interval 更危险，手写 visit 必须逐字段赋值并显式拒绝空归一化结果）。
- **不删除任何 lexer token**（roadmap 红线）；本 plan 两个文法半区均无需 lexer 改动（见上）。

### 既有 EQL 语料相容性（BC 论证基础，2026-10-03 实测）

对三份必测语料逐文件 grep（`-i`）：`limit|offset|fetch|rows|nulls|collate` **0 命中**——`nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/sql/NopJobTask.sql-lib.xml`、`nop-auth/nop-auth-service/src/test/resources/_vfs/nop/auth/sql/test.sql-lib.xml`、`nop-persistence/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml` 均不使用分页/NULL 序/COLLATE 文本。⇒ 新语法接受对既有语料是**纯增量**（今天解析失败的输入在语料中不存在）；BC 棘轮判据（`TestEqlCorpusBaseline` 覆盖 7 文件回显逐字比对，快照 `ai-dev/audits/eql-wi0/eql-corpus-baseline.md`）风险面极小；真正的既有行为守护面是 `TestEqlSelectAllLimitEcho`/`TestEqlSelectAllLimitCompile`（LIMIT 形态逐字不变）。

### 全绿口径与 stream 豁免（沿用 WI5 plan 口径）

含 `:nop-auth-service` 的命令会拖入 `nop-stream-runtime`，其 **2 项 `-T 1C` 时序 flake 按基线豁免**（均单跑 PASS）：`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`（WI0 既档，`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 文档头）+ `TestMessageSourceFunctionRestart`（WI1 收口轮实测，`ai-dev/plans/03-eql-wi1-class-a-defects.md` Closure 段）。⇒ **"全绿" = 本 WI 相关模块全绿 + 这 2 项与基线一致**，不得据此判本 WI 失败。

## Goals

- **分页两形态可解析**（EC ①）：`FETCH FIRST n ROWS ONLY` 与 `OFFSET n ROWS FETCH NEXT m ROWS ONLY` 解析失败 → 归一化装入既有 `SqlLimit{limit, offset}` → 经既有 `IPaginationHandler` 双族 lowering 输出正确方言 SQL 文本（红→绿用例，覆盖 LimitOffset 族与 OffsetFetch 族各至少一方言）；`LIMIT n [OFFSET m]` 既有形态逐字不变。
- **`ORDER BY … NULLS FIRST/LAST` 可解析且有方言门**（EC ②）：文法赋值 `nullsFirst`；`supportNullsFirst=true` 方言（duckdb/h2/oracle/postgresql）输出子句；不支持方言上显式请求与缺省序一致时省略子句（缺省序行为等价）、不一致时**编译期响亮报错**（断言具体错误码；`dm`/`tdengine` 按红线 `[未取证]` 一律报错）——不允许任何静默降级。
- **`defaultNullsFirst` 消费点落地**（EC ③，WI3 移交链收口）：8 个 XML（mysql/mssql/dm/h2=true，tdengine/duckdb/postgresql/oracle=false）全部有消费路径断言（接线验证 #23：翻转 flag 行为随之翻转）；`IDialect.isDefaultNullsFirst()` 从"有 accessor 无消费"转为真实消费。
- **`supportNullsFirst` 接线**（EC ③b）：`IDialect.isSupportNullsFirst()` accessor（手写零生成）+ `AstToSqlGenerator` 消费点（子句输出的唯一门）+ 4 个 XML（duckdb/h2/oracle/postgresql）行为断言。
- **两类默认值对应用例**（EC ④）：oracle（默认 NULLS LAST）与 mysql/mssql（默认 NULLS FIRST）两类各有"显式请求一致 → 省略 / 不一致 → 报错"的对应用例。
- **能力上限锁定**（EC ⑤）：DuckDB-vs-PG 在 `DESC` 下的缺省 NULL 序差异**既**有差异用例**又**有 owner doc 差异表行（roadmap 原文为并列要求："至少要有差异用例，**并**把该限制作为文档项写进 owner doc"；注：缺省输出文本的编译期差异用例不可能存在——两者 `defaultNullsFirst` 均 `false` 且本 plan 对 `nullsFirst == null` 不补子句，差异用例以"显式子句 parity"形态落，见 Phase 0 裁定文档登记项）；"单布尔无法表达方向相关 NULL 序"作为文档项写入 owner doc（不要求本 plan 解决）。
- **`ORDER BY … COLLATE` ledger 裁定落档**（EC ⑦）：落地 or 显式移出 + successor + 触发条件，二选一必须落（Phase 0 裁定、Phase 4 收口）。
- **既有 EQL 语料 BC**（EC ⑧）：三份必测与 WI0 基线快照逐字一致；`LIMIT` 形态行为逐字不变；约束 4 的"一致性 + 不等价"两类用例齐备（分页形态归一 vs 原样输出、NULL 序省略 vs 报错即两类）。
- **独立 closure audit**（EC ⑨，不同 task_id，evidence 写入本 plan Closure 段）。

## Non-Goals

- **不新增 `SqlLimit` "语法形态"字段（默认形态，Phase 0 裁定候选 A）**——输出形态由方言 `paginationHandler` 决定、`IPaginationHandler.buildPageExpr` 无形态参数，形态字段将零消费（roadmap 自己登记的"零生成器消费 AST 字段"缺陷类的扩员）；若 Phase 0 裁定推翻候选 A，须按 roadmap 约束 1 路径 ⓒ 定价回滚本条（显式 scope change）。
- **不解决方向相关 NULL 序的表达力缺口**（EC ⑤ 明文"不要求本 WI 解决"；DuckDB `default_null_order` / H2 `FIRST`/`LAST` 方向无关设置的表达不进本 plan，登记 watch-only）。
- **不做 NULLS FIRST/LAST 的表达式改写 lowering**（如 MySQL 的 `order by f is null, f` 模拟——xdef javadoc 的设想，全仓无实现；本 plan 对不支持方言一律"一致省略 / 冲突报错"，不引入表达式注入）。**不新增方言能力标志**（不新造 `supportNullsOrderRewrite` 之类）。
- **不落地 `ORDER BY … COLLATE` 语法**（Phase 0 推荐裁定为显式移出——约束 3"能无损 lowering 才做"；MySQL 子句位置差异 + MSSQL collation 库级作用域 ⇒ 无损归一不成立；被拒备选在裁定文档记录）。
- 不做 `OFFSET n ROWS`（无 FETCH 的独立 offset 形态，roadmap 只点名两种形态——登记 Deferred）、`FETCH FIRST … WITH TIES`、MySQL 私有 `LIMIT off,lim`（roadmap"明确不做"清单）、keyset 分页（ledger 归单独 roadmap）。
- 不改 `IPaginationHandler` 接口签名（`nop-dao` public API；buildPageExpr 的 limit/offset 二参形态对两族实现已充分）。
- 不动 `EqlTransformVisitor` 的 limit/orderBy 遍历、不动 WI2 的 `visitSqlLimit` 回显钩子结构（只允许在新形态归一化上扩展）。
- 不做 WI7b 的 `sqlIntervalLiteral` 模板 / `rollup`/`cube`/`grouping` / 窗口 frame（WI1 骨架的模板内容唯一 owner=WI7b）。
- 不把 `:nop-orm-demo` / `nop-spring-simple-demo` / `:nop-orm-tdengine` / `:nop-db-migration` 拉进本 plan 的 `-pl`（roadmap 级 EC 第 8 条 + WI0 静态快照兜底）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql`：`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（`sqlLimit` 多备选化）、`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`sqlOrderByItem` 加 `nullsFirst=(FIRST|LAST)?` 赋值、`unreservedWord_` 加 `NULLS`）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`（新备选手写 visit + token→Boolean 转换方法）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（`visitSqlOrderByItem` override：`supportNullsFirst` 门 + `defaultNullsFirst` 默认序消费）、重生成产物（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/_EqlASTBuildVisitor.java`、`EqlLexer*`、`Eql.interp`、`*.tokens`）、对应测试（`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/`、`.../compile/`）。
- `nop-persistence/nop-dao`：`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java` + `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`（`isSupportNullsFirst()` 手写 accessor，零生成）、`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDefaultNullsFirstFlag.java`（accessor 升级断言，原三测不弱化）、必要时新增 NULLS 行为测试类。
- 文档：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（supportNullsFirst 行翻转、defaultNullsFirst 小节消费更新、NULLS 语法小节、DuckDB-vs-PG DESC 差异文档项、COLLATE 裁定小节、能力点登记表行）；`ai-dev/design/eql-cross-db-compat/`（Phase 0 裁定文档）；`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI7 行状态流转 + COLLATE ledger 行收口，按 roadmap Status values 纪律）；`ai-dev/logs/`。

### Out Of Scope

- Non-Goals 所列全部；任何 roadmap 未登记的方言语法扩展（发现新缺陷/新需求 → 登记 roadmap，不顺手修）；`nop-auth`、`model/*.orm.xml`、`nop-*-api`、`nop-core`/`nop-xlang`/`nop-xdef` 内部。

## 与 roadmap 的差异登记（起草时实测发现）

1. **行号漂移（非实质偏离）**：BaseRule.g4（路径见 Current Baseline 所引 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`）的 `sqlOrderByItem` roadmap 记 `:365-367` → 实测 `:369-371`；`collateClause_` roadmap 记 `:388-389` → 实测 `:396-397`；`AstToEqlGenerator` nulls 打印 roadmap 记 `:589-595` → 实测 `visitSqlOrderByItem` `:618-633`；`EqlParser.java` 10785 → 实测 10927；`_EqlASTBuildVisitor.java` 1804 → 1819（WI1/WI2/WI3 文法与逻辑提交累积平移）。EC ③b 的 4 XML 行号：`duckdb:21`/`postgresql:94` 未漂移，`h2:17` → 实测 `:25`、`oracle:99` → 实测 `:102`（WI3 补配与注释块所致）。
2. **EC ③ 计数口径**：roadmap 写"`defaultNullsFirst` 的 7 个 XML 全部有消费路径断言"——WI3 第 11 项给 h2 补配后实测为 **8 个 XML**（mysql/mssql/dm/tdengine/duckdb/postgresql/oracle/h2）。本 plan 按 8 执行（7+WI3 补配 1），不算偏离。
3. **"需给 SqlLimit 加语法形态字段"的定价前提已被实测削弱（Phase 0 裁定候选 A 的核心依据）**：roadmap WI7 节与门控矩阵 Protected Area 均按"路径 ⓒ、77 文件 `_gen` 重写"定价；实测 `IPaginationHandler.buildPageExpr(limitExpr, offsetExpr, sqlExpr)` 无形态参数、`OffsetFetchPaginationHandler`（mssql:11/oracle:12 已配）与 `LimitOffsetPaginationHandler`（default:11）双实现已就绪——**输出形态由方言配置决定，形态字段无输出侧消费点**。候选 A（归一化进既有 `limit`/`offset` 字段，`ast/_gen` 零 diff）成立则 roadmap 的 WI7 行 Protected Area 定价与"需加字段"表述在 Phase 0 完成后回写——**回写交付物由 Phase 4 显式认领**（见 Phase 4 首个 checklist 之后的"roadmap WI7 行定价回写"条目：门控矩阵单元格 + EC ⑥ 措辞原地修订注记 + WI7b/WI12 后继读取口径）。
4. **"只需让 AstToSqlGenerator 侧也产出"表述不精确**：`AstToSqlGenerator extends AstToEqlGenerator`（`AstToSqlGenerator.java:75`），继承的 `visitSqlOrderByItem` 在字段非 null 时**已经会**输出子句（无门）。真实缺口 = 文法不赋值 + 无方言门 + 无默认序策略；且"只加文法不同批加门"是净回归（见 Current Baseline）⇒ Phase 编排把门与消费前置到文法之前落地。
5. **`NULLS` 无 token**：`NULLS` 在全部 `.g4` 零 token（词法落 `IDENTIFIER_`）⇒ 必须进 `unreservedWord_`（WI5 遮蔽放开同款）；`FIRST`/`LAST` 已是保留 token。分页半区 `FETCH`/`FIRST`/`ROWS`/`ONLY`/`NEXT`/`LIMIT`/`OFFSET` 全部已有保留 token ⇒ **本 plan 零 lexer 改动、不触 token 重编号红线**（roadmap 红线中"删除 lexer token"风险本 plan 不涉及）。
6. **WI3 状态与硬前置（已消解）**：起草时（2026-10-03）plan 20 实测 Status: active（实现已落地、closure audit 待启动），本 plan 据此为代码 Phase 设了排队门；**WI3 已于 2026-10-04 收口——plan 20 `completed`、roadmap WI3 行 `done`（closure audit commit `c54fcb5f63`），排队门已满足**，该差异不复存在。各 Phase 的"开工前置复核"bullet 保留，改作前置状态回退防护（见硬前置声明）。
7. **矩阵快照预期零增量**：本 plan 不新增/不删除方言函数（accessor 不是函数表成员、快照只登记 functions）⇒ 每代码 Phase 核实 `ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0 且快照零增量（loadable 15 口径，WI3 db2 修复后）。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` 的 `sqlLimit`（`:217-219`）与 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlOrderByItem`/`unreservedWord_` → 经 `gen-eql-parser.xgen` 重生成 `EqlParser.java`（10927 行）等 | **生成管线，plan-first**（AGENTS.md Protected Area"生成管线"；roadmap 门控矩阵 WI7 行同判——候选 A 下范围收窄为 parser 侧，不再是"77 文件 `_gen` 重写"） | 本 plan 即 plan-first 载体，执行前须过 draft review；重生成命令 `./mvnw generate-sources -pl :nop-orm-eql -am` | owner doc（`eql-and-database-compatibility.md`）+ 回归测试（各 Phase 红/绿用例 + 三份必测 BC + LIMIT 形态守护） |
| `EqlAST.xjava` / `ast/_gen/` / `EqlASTKind` | **禁止触碰**（候选 A 默认形态；候选 B 才触发，且仅加字段不加类、不触 ordinal） | 若 Phase 0 裁定候选 B → 显式 scope change + roadmap 约束 1 定价入档 + 回滚本声明；`EqlASTKind` ordinal 红线原文不变（禁持久化/跨版本比较） | `git status` 中 `ast/_gen/` 零 diff 作为每 Phase 回归证据 |
| `nop-persistence/nop-dao` 的 `IDialect`/`DialectImpl`（`isSupportNullsFirst()` accessor） | **零生成、非 AGENTS.md 字面 Protected Area**（`nop-dao` 非 `nop-*-api`；roadmap Cross-Cutting 第一行明判；且 roadmap WI7 EC ③b + WI3 移交链已预授权该 accessor） | 不触发 plan-first；改动登记于本表 + owner doc 标志表同步 | `TestDefaultNullsFirstFlag` 升级断言 + 4 XML 行为断言 + 负向用例 |
| 生成物变更纪律 | `.g4` 改动**单独提交**；重生成后 `git diff` 逐一检查（本 plan 预期：`EqlParser.java`/`_EqlASTBuildVisitor.java`/`EqlLexer*`/`Eql.interp`/`*.tokens` 有 diff，`ast/_gen/` 零 diff）；无 lexer token 删除 | 提交拆分 git 可审计（`nop-git-master` skill） | `git log` 文法提交与逻辑提交分离 |

⚠️ 本 plan 不触碰 `model/*.orm.xml`、`nop-auth`、`nop-*-api` 公共接口、`nop-core`/`nop-xlang`/`nop-xdef`（`dialect.xdef` 零改动——`supportNullsFirst`/`defaultNullsFirst` 均已声明）。

## Execution Plan

Phase 1（分页）与 Phase 2/3（NULL 序）相互独立、可分别落地与回滚；**Phase 2 必须先于 Phase 3 落地**（门与消费先就位、文法后激活——排序原则"只打印不设门 = 净回归"的编排化）。Phase 4 依赖 Phase 0 的 COLLATE 裁定结论。每个代码 Phase 的 `.g4` 改动单独提交。

### Phase 0 - 裁定：SqlLimit 形态方案、NULLS 门与默认序消费形态、COLLATE ledger 二选一（不改产品代码）

Status: completed（2026-10-04；裁定文档 `ai-dev/design/eql-cross-db-compat/2026-10-04-wi7-phase0-adjudications.md`）
Targets: `ai-dev/design/eql-cross-db-compat/`（新增裁定文档）、`ai-dev/audits/eql-wi0/`（只读取证）、`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（只读取证；回写在 Phase 4）

> **前置状态复核**：本 Phase 开工前复核 WI0-WI3 四个前置 plan 的 `Plan Status` 未回退（现均为 `completed`，硬前置已满足）并记录；后续各代码 Phase 开工前同样复核。

- Item Types: `Decision`、`Proof`

- [x] 复核 WI0 三件基础设施状态未回退：矩阵快照 + 门禁脚本（`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0）、语料基线（`TestEqlCorpusBaseline` 通过）、WI0-WI3 四个前置 plan 的 `Plan Status`（现均 `completed`，记录于裁定文档；若发现回退则写明对应 Phase 排队）。**并复核"与 roadmap 的差异登记"第 1 条全部漂移锚点的现位**（`sqlOrderByItem`/`collateClause_`/`visitSqlOrderByItem`/`EqlParser.java` 行数/4 XML supportNullsFirst 行号——执行时以符号定位为准，把复核结果记入裁定文档）。（2026-10-04 实测：三件未回退；锚点漂移 13 项登记于裁定文档 §〇——`sqlOrderByItem`→`:416-418`、`EqlParser.java`→11260 行等，WI9a 落地所致；4 XML/8 XML 行号与 plan 一致）
- [x] **裁定 SqlLimit 语法形态方案**（Decision，本 plan 最大 Protected Area 裁决）。**候选 A（推荐）：归一化进既有字段**——`sqlLimit` 多备选化（altLabel 后缀均截断为既有类 `SqlLimit`，`sqlIntervalExpr` 双备选先例）+ 手写 visit 装入既有 `limit`/`offset`；依据：① 输出形态由方言 `paginationHandler` 决定、`IPaginationHandler.buildPageExpr` 无形态参数 ⇒ 形态字段零输出侧消费点（`OffsetFetchPaginationHandler` mssql:11/oracle:12 与 `LimitOffsetPaginationHandler` default:11 双实现已就绪）；② 两形态无损分解（`FETCH FIRST n ROWS ONLY` → limit=n/offset=null；`OFFSET n ROWS FETCH NEXT m ROWS ONLY` → offset=n/limit=m）；③ 回显规范化到 EQL 既有正则形态 `limit x offset y`（`TestEqlSelectAllLimitEcho` 同一口径），`ast/_gen` 零 diff、`EqlAST.xjava`/`EqlASTKind` 零触碰；④ 加形态字段等于给 roadmap 自己登记的"零生成器消费 AST 字段"清单（现 7 项）扩员。**候选 B（被拒备选，记录在案）**：按 roadmap 原文给 `SqlLimit` 加"语法形态"字段——唯一获益是 FETCH 形态输入的回显保真（源代码生成场景），代价是 `EqlAST.xjava` 改动 + `ast/_gen` 重生成 + Protected Area 扩大，且仍无 SQL 输出侧消费点；仅当 Phase 0 发现真实形态消费需求（如产品要求源码往返保真）时翻案，翻案须按约束 1 定价并回滚 Non-Goals。（裁定文档 §一：候选 A 落档，翻案条件=真实形态消费需求）
- [x] **裁定 `sqlLimit` 文法形态细节**（Decision）：三个备选（既有 LIMIT 形态 + 两个 FETCH 形态）是否全部显式 altLabel（推荐全部 label 化，`sqlIntervalExpr` 先例）；新备选 prop 命名避开 `limit=`/`offset=`（duplicate-prop-label 约束）；手写 visit 完全重写不调 super（`SqlLimit` 无必填校验 ⇒ 空节点静默通过 validate 的风险比 WI1 interval 更高，必须显式拒绝空归一化结果）；`OFFSET n ROWS`（无 FETCH）**不进本 plan**（roadmap 只点名两形态，登记 Deferred）；三备选**无 ANTLR LL(*) 冲突**：备选 1 首符集 = {`LIMIT`}、备选 2 = {`FETCH`}、备选 3 = {`OFFSET`}——**FIRST 集两两不相交**（备选 1 的 `OFFSET` 仅出现在 `LIMIT expr` 之后，属备选内可选子句，不参与备选间预测竞争），裁定文档以"无冲突"结论落档；仍保留 `limit 10 offset 5` 守护用例锁定既有路径（防备选序调整引入回归）。
- [x] **裁定 NULLS 的门与默认序消费形态**（Decision）：消费点位置 = `AstToSqlGenerator` 新增 `visitSqlOrderByItem` override（`nullsFirst != null` 时接管，替代继承的无门打印）；形态 = ① `isSupportNullsFirst()=true` → 按请求打印 `nulls first `/`nulls last `；② false → 显式请求与该方向缺省序**一致则省略子句**（缺省行为等价、无损）、**不一致则编译期报错**（复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`，`AstToSqlGenerator.java:272/:289/:538` 先例；不新起新码除非裁定必须）；③ 缺省序推导：ASC 方向 = `isDefaultNullsFirst()`；DESC 方向按"nulls 最小/最大"镜像模型推导，但 `dm`/`tdengine` 无一手文档 ⇒ 一律报错（红线），DESC 镜像对 mysql/mssql 需一手文档引证入档（MySQL 文档"NULLs sort first in ASC / last（DESC 时）"类原文），不可引证的方向按报错处理；**`db2` 必须在裁定文档中显式列名**——其未配置 `defaultNullsFirst`（→ 模型 null → accessor `false`），DESC 显式请求大概率走向报错分支，按 ASC=false 的保守语义处置，**不得在执行期临时裁量给 db2 放行或补配置**；④ `nullsFirst == null`（未指定）⇒ 输出与今天逐字节一致（**缺省不主动补子句**——BC 棘轮硬约束）。被拒备选（一律报错不做一致省略 / 一律打印含缺省补全）与拒绝理由（误伤 mysql 合法请求 / 打破既有语料逐字一致）一并记录。
- [x] **裁定 `supportNullsFirst` accessor 落地形态**（Decision）：`IDialect.isSupportNullsFirst()` + `DialectImpl` 实现（读 `dialectModel.getFeatures().getSupportNullsFirst()`，`Boolean.TRUE.equals` 惯例同 `isDefaultNullsFirst`）——手写、零生成、零 xdef 改动；`IDialect.java:130` 的前向 javadoc link 随 accessor 落地转正；`TestDefaultNullsFirstFlag.testSupportNullsFirstUntouched` 从模型层断言升级为 accessor 断言（原三测不弱化）。
- [x] **裁定 `ORDER BY … COLLATE` ledger 二选一**（Decision，EC ⑦；Phase 4 收口）。裁定输入（2026-10-03 实测）：`SqlOrderByItem.collate` 字段已在 `EqlAST.xjava:235`/`_SqlOrderByItem` 但**全仓零读取**（`getCollate()` 生产读取方 0 处，roadmap"零生成器消费 AST 字段 7 项"之一）；`sqlOrderByItem` 文法无 collate 赋值；`collateClause_`（`BaseRule.g4:396-397`）只被 `sqlTypeExpr`（`:378`）引用且是 BUG-15 登记的**永久不可达**分支（期望 `STRING_` 隐式 token）；`COLLATE` 保留 token 存在（`SQL92Keyword.g4:282`）；ledger 行自身记录"MySQL 子句位置不同 + MSSQL collation 是库级作用域而非表达式属性"。**推荐：显式移出**——依据约束 3（不满足"能无损 lowering"的项一律不做：MSSQL 无表达式级 COLLATE、Oracle 走 `NLSSORT` 体系、MySQL 子句位置语义不同 ⇒ 无法跨 mysql/mssql/oracle/pg 无损归一）+ 排序原则（P1 静默错误 > 响亮失败：接受语法却无法可靠 lowering 就是制造 BUG-1 同型风险）；successor = 单独调研 roadmap 项（触发条件 = 出现"按表达式 collation 排序"的真实产品需求），owner doc 落"各库 collation 作用域差异"小节，roadmap ledger 行在本 plan 收口时改写为已裁定。**备选（落地）**仅在 Phase 0 能给出逐方言无损 lowering 矩阵时成立（当前证据面不支持）；若裁定落地，须同步回滚 Non-Goals 并按文法 + `SqlOrderByItem.collate` 接线 + 各库作用域文档三件套重排 Phase。**裁定结论回写 roadmap**（`SqlOrderByItem` 字段保留不动——冻结生成模型，删除属 `EqlAST.xjava` 变更且无行为收益；字段继续留在 7 项零消费清单，不由本 plan 清理）。
- [x] 约束 4 的"一致性 + 不等价"两类用例清单落裁定文档：分页"归一 vs 原样"（同 LIMIT 语义在两族 handler 下输出形态不同 = 不等价登记）、NULL 序"省略 vs 报错"（一致省略 = 一致性；DuckDB-vs-PG DESC 缺省差异 = 不等价登记）——每项至少一条用例或 owner doc 差异表行素材。**EC ⑤ 满足形态登记**：DuckDB 与 PG 的 `defaultNullsFirst` 均 `false`，且本 plan 对 `nullsFirst == null` 缺省不补子句（BC 硬约束）⇒ "缺省输出文本差异"的编译期用例**不可能存在**；EC ⑤ 的"差异用例"以**显式子句 parity 用例**（`order by … desc nulls last` 在 duckdb 与 pg 上都输出 `nulls last` 子句——证明两库均可经显式子句表达）**加** owner doc 差异表行（"缺省时两库行为不同且单布尔不可表达"）两件满足，对齐 roadmap 原文的并列要求。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含：SqlLimit 形态方案（候选 A 推荐 + 候选 B 被拒理由 + 翻案条件）、`sqlLimit` 文法细节（altLabel/prop 命名/空节点风险/歧义登记）、NULLS 门与默认序消费形态（含 DESC 证据等级与 dm/tdengine 报错纪律）、supportNullsFirst accessor 形态、COLLATE 二选一结论（推荐移出 + successor + 触发条件，或落地的逐方言矩阵）、两类用例清单、WI3 状态复核记录、漂移锚点复核记录。
- [x] 本 Phase 纯裁定，不改产品代码：`No owner-doc update required`（裁定文档本身即交付物；owner doc 的标志表/覆盖表随各代码 Phase 落）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 1 - 分页两形态文法 + 归一化 + 双族 lowering（EC ①）

Status: completed（2026-10-04）
Targets: `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（`sqlLimit`）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`、重生成产物、`nop-persistence/nop-orm-eql` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Feature`、`Proof`

- [x] **开工前置复核**：WI0-WI3 四个前置 plan 的 `Plan Status` 仍为 `completed`（硬前置已满足，见顶部声明；复核为回退防护）——若发现回退则本 Phase 不得开工、保持 `planned` 并在 daily log 记录排队。
- [x] 修复前红：`select * from t fetch first 5 rows only` 与 `select * from t offset 3 rows fetch next 10 rows only` 解析失败（parse error）——回归测试断言其可解析且 lowering 正确，修复前红留痕于提交说明或日志。
- [x] 文法与归一化（按 Phase 0 裁定）：`sqlLimit` 三备选化；`FETCH FIRST` 备选 → 手写 visit 装入 `limit=n`、`offset=null`；`OFFSET..FETCH NEXT` 备选 → `offset=n`、`limit=m`；`.g4` 改动单独提交，重生成后 `git diff` 检查（预期 `ast/_gen/` 零 diff、`EqlParser.java`/`_EqlASTBuildVisitor.java` 等有 diff）。
- [x] 正向用例断言具体方言 SQL 文本（EC ①，两族 handler 各至少一方言）：h2（LimitOffset 族）`fetch first 5 rows only` → `LIMIT 5`、`offset 3 rows fetch next 10 rows only` → `LIMIT 10 OFFSET 3`；oracle 或 mssql（OffsetFetch 族，`oracle.dialect.xml:12`/`mssql.dialect.xml:11`）同输入 → `OFFSET 0 ROWS FETCH FIRST 5 ROWS ONLY` / `OFFSET 3 ROWS FETCH NEXT 10 ROWS ONLY`（以 handler 实际输出文本为准）；参数 marker 形态（`fetch first ? rows only`）至少一条。
- [x] 既有 LIMIT 形态逐字不变守护用例：`limit 10 offset 5`/`limit 3` 的解析、lowering、回显与改动前一致（`TestEqlSelectAllLimitEcho` + `TestEqlSelectAllLimitCompile` 全量回归 + 新增两族 handler 下的 LIMIT 输出断言）。
- [x] 回显规范化断言：两 FETCH 形态的 `toSqlString()` 输出为规范化 `limit x [offset y]` 文本（Phase 0 裁定的口径），且回显文本再喂回编译器语义不变（往返用例）。
- [x] 接线验证（Minimum Rules #23）：EQL 文本 → parse 归一化 → transform 遍历 → `visitSqlQuerySelect` 经 `dialect.getPaginationHandler().buildPageExpr` 输出的**端到端**用例（至少覆盖 OffsetFetch 族一条，证明 `:101` 消费链真实连通，而非只测 parse 层）。
- [x] owner doc（EC ④ 文档面）：`eql-and-database-compatibility.md` 新增分页小节——两种 EQL 形态 × 双族 handler 输出矩阵、h2 的 OffsetFetch 注释态现状（`h2.dialect.xml:20`）、`dm`/`tdengine` 分页形态 `[未取证]` 标注、能力点登记表行。
- [x] 矩阵快照核实零增量 + `check-eql-dialect-matrix-snapshot.mjs` exit 0（差异登记第 7 条）。
- [x] 语料 BC（EC ⑧）：`:nop-job-dao`/`:nop-auth-service`/`:nop-orm` 三份必测与 WI0 基线快照逐字一致（`TestEqlCorpusBaseline` 随全量运行通过）；不声称"7 个全测"。
- [x] `./mvnw test -pl :nop-orm-eql -am -T 1C` 全绿。

Exit Criteria:

- [x] 红→绿回归测试断言具体 SQL 文本（两形态 × 双族 handler + marker 形态）；修复前红留痕。
- [x] 既有 LIMIT 形态逐字不变守护用例通过；无静默跳过（归一化失败必须响亮，不允许空 `SqlLimit` 静默通过）。
- [x] 端到端接线用例通过（parse→transform→buildPageExpr 三层连通）。
- [x] owner doc 分页矩阵已落档（含 `[未取证]` 标注）；矩阵快照门禁 exit 0。
- [x] `TestEqlCorpusBaseline` 通过（三份必测逐字一致）；`./mvnw test -pl :nop-orm-eql -am -T 1C` 全绿。
- [x] `.g4` 改动单独提交；重生成后 `git diff` 已逐一检查且 `ast/_gen/` 零 diff。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - `supportNullsFirst` accessor + 生成器 NULLS 门与 `defaultNullsFirst` 消费（门先于文法落地，EC ③/③b）

Status: planned
Targets: `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`、`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDefaultNullsFirstFlag.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`、`nop-orm-eql` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

> 本 Phase 落地时**尚无文法赋值路径**（Phase 3 才激活）：`nullsFirst` 只能程序化构造，因此行为断言用程序化 AST（`SqlOrderByItem.setNullsFirst(...)`）+ 手构/加载方言驱动；对既有查询（`nullsFirst == null`）输出逐字节不变（BC 硬约束）。门与消费先行，保证 Phase 3 文法落地当天即有门，不产生"无门打印"窗口。

- Item Types: `Feature`、`Proof`

- [ ] **开工前置复核**：WI0-WI3 四个前置 plan 的 `Plan Status` 仍为 `completed`（硬前置已满足；复核为回退防护，同 Phase 1）。
- [ ] `IDialect.isSupportNullsFirst()` accessor + `DialectImpl` 实现（按 Phase 0 裁定；手写零生成，`IDialect.java:130` 前向 javadoc 转正）；不动 `dialect.xdef`、不动 `_DialectFeatures`（冻结生成类已有 getter）。
- [ ] `AstToSqlGenerator` 新增 `visitSqlOrderByItem` override（按 Phase 0 裁定的三分形态）：`supportNullsFirst=true` → 打印请求子句；false → 一致省略 / 冲突报错；`nullsFirst == null` → 委托 super（逐字节不变）。错误复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（或 Phase 0 裁定的新码），`.param` 带方言名与请求方向。
- [ ] 接线验证（#23，EC ③ 的"消费路径断言"）：**8 方言 `defaultNullsFirst` 全部有消费路径断言**——同一程序化 `nullsFirst` 请求在"缺省序一致"与"不一致"两类方言上行为不同（省略 vs 报错），且翻转模型 flag 后行为随之翻转（手构 `DialectModel` 先例：`TestEqlTranslationSemantics.buildDialect` 模式或 `DialectManager` 加载方言）；**4 方言 `supportNullsFirst` 行为断言**（EC ③b）：duckdb/h2/oracle/postgresql（均 true）打印子句，mysql/tdengine（无此 features 配置 → false）走省略/报错分支。
- [ ] 负向用例（EC ② 的报错半边 + 约束 4）：`dm`/`tdengine` 上显式 `nullsFirst` 请求**一律编译期报错**（红线 `[未取证]` 纪律），断言具体错误码；无静默跳过（不允许不支持方言静默丢请求）。
- [ ] BC 守护：`nullsFirst == null` 的既有 `order by` 查询在 mysql/oracle 等代表方言上输出与改动前逐字节一致（守护用例 + `TestEqlCorpusBaseline` 兜底）。
- [ ] `TestDefaultNullsFirstFlag` 升级：`testSupportNullsFirstUntouched` 改走新 accessor（原三测断言值不变、不弱化）；新增 accessor 契约断言**双侧**——true 侧：duckdb/h2/oracle/postgresql（4 方言显式 `true`）；**false 侧：mysql/tdengine（features 未配置 `supportNullsFirst` → 模型 null → accessor `false`，`Boolean.TRUE.equals` 保守缺省语义钉死）**，防止"缺配置被读成 true"或"缺配置走不进门控报错分支"的回归。
- [ ] owner doc（EC ④⑤ 文档面）：标志表 `supportNullsFirst` 行翻转（`:126`"尚未接线"→ 已接线 + 消费点 + 错误码）；`defaultNullsFirst` 小节（`:246-249`）消费更新；**"单布尔无法表达方向相关 NULL 序"能力上限小节**（EC ⑤：DuckDB `NULLS LAST` even for DESC vs PG DESC nulls first 的引文 + H2 `LOW`/`HIGH` 方向相对 + dm/tdengine `[未取证]`）；能力点登记表行（generator 方法 + 触发标志 + 不支持时错误码，roadmap 级 EC 第 4 条格式）。
- [ ] 矩阵快照核实零增量 + 门禁 exit 0；`./mvnw test -pl :nop-dao,:nop-orm-eql -am -T 1C` 全绿。

Exit Criteria:

- [ ] `IDialect.isSupportNullsFirst()` accessor 存在且有**双侧**契约断言：true 侧 4 方言（duckdb/h2/oracle/postgresql）+ false 侧 mysql/tdengine（未配置 → null → false 保守语义）（EC ③b accessor 半边）。
- [ ] `AstToSqlGenerator` 消费点存在且被运行时验证（EC ③b 消费点半边 + EC ③ 8 方言消费路径断言；flag 翻转行为翻转的接线用例通过）。
- [ ] `dm`/`tdengine` 一律报错负向用例断言具体错误码；无静默降级、无静默打印。
- [ ] `nullsFirst == null` 逐字节 BC 守护用例 + `TestEqlCorpusBaseline` 通过。
- [ ] owner doc 标志表/正交小节/能力上限小节/能力点登记表已落档。
- [ ] `./mvnw test -pl :nop-dao,:nop-orm-eql -am -T 1C` 全绿；矩阵快照门禁 exit 0。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - `ORDER BY … NULLS FIRST/LAST` 文法 + 构建赋值 + 回显（激活 Phase 2 管线，EC ②/④/⑤ 行为面）

Status: planned
Targets: `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`sqlOrderByItem`、`unreservedWord_`）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`、重生成产物、`nop-orm-eql` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Feature`、`Proof`

- [ ] **开工前置复核**：WI0-WI3 四个前置 plan 的 `Plan Status` 仍为 `completed`（硬前置已满足；复核为回退防护，同 Phase 1）；**并复核 Phase 2 已 `completed`**（门必须先于本 Phase 的文法落地）。
- [ ] 修复前红：`select a from t order by a desc nulls last` 与 `order by a nulls first` 解析失败——回归测试断言可解析，修复前红留痕。
- [ ] 文法（按 Phase 0 裁定）：`sqlOrderByItem` 单备选内扩展为 `expr=sqlExpr asc=(ASC | DESC)? (NULLS nullsFirst=(FIRST | LAST))?`（同构 `asc=` 的 token→Boolean 抽象转换方法先例，`_EqlASTBuildVisitor.java` 的 `SqlOrderByItem_asc` 同款手写转换）；`NULLS` 加入 `unreservedWord_`（`BaseRule.g4:72-96`，WI5 遮蔽放开同款，零 lexer 改动）；不删除任何 token。`.g4` 改动单独提交，重生成后 `git diff` 检查。
- [ ] 正向用例断言具体方言 SQL 文本（EC ②，`defaultNullsFirst` true/false 两类方言各至少一例）：postgresql/duckdb/h2/oracle（`supportNullsFirst=true`）显式请求 → 输出 `nulls first`/`nulls last` 文本；mysql（true、无子句支持）`asc nulls first` → 一致省略（输出无子句、行为等价）（EC ④ 的 mysql 侧）。
- [ ] EC ④ 另一半：oracle（默认 NULLS LAST，`oracle:5`）`asc nulls first` → 打印子句（与缺省不一致但有子句支持）；oracle `asc nulls last` 与 mysql `desc nulls last`（若 DESC 镜像经一手文档证实）→ 一致省略——两类默认值的"一致省略/打印/报错"三态对应用例齐备。
- [ ] 负向用例（EC ②④）：mysql `asc nulls last`（无子句支持 + 与缺省冲突）编译期报错断言具体错误码；`dm`/`tdengine` 显式请求报错；无静默降级。
- [ ] EC ⑤ 差异用例（两件，roadmap 并列要求）：**DuckDB-vs-PG 在 `DESC` 下的缺省 NULL 序**——① 显式子句 parity 用例：`order by … desc nulls last` 在 duckdb 与 pg 上**都**输出 `nulls last` 子句（两者 `defaultNullsFirst` 均 `false` ⇒ 缺省文本差异用例不可能，以显式子句形态满足，见 Phase 0 登记）；② owner doc 差异表行：记录"缺省时两库行为不同（DuckDB keeps NULLS LAST even for DESC vs PG places NULLs first on DESC）且单布尔不可表达"。
- [ ] 回显断言：`order by a desc nulls first` 的 `toSqlString()` 含 `nulls first`（继承打印路径首次被文法激活的守护）；`order by a`（无 NULLS）回显逐字不变。
- [ ] 接线验证（#23）：EQL 文本 → parse 赋值 `nullsFirst` → transform → 生成侧门/省略/打印决策的端到端用例（mysql 冲突报错一条 + postgresql 打印一条）。
- [ ] 既有行为守护：`order by a asc`/`order by a desc` 输出逐字不变；`unreservedWord_` 加 `NULLS` 不影响标识符/别名使用 `nulls` 的场景（若语料或测试存在）与 interval 形态（`TestEqlIntervalLiteralParse` 全量回归兜底）。
- [ ] owner doc（EC ②④⑤ 收口）：NULLS 语法小节（支持矩阵 + 省略/报错决策表 + dm/tdengine `[未取证]`）并入 Phase 2 小节；矩阵快照核实零增量 + 门禁 exit 0；语料 BC（EC ⑧）三份必测逐字一致。
- [ ] `./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。

Exit Criteria:

- [ ] 红→绿回归测试断言具体 SQL 文本（true/false 两类方言 + EC ④ 三态对应 + EC ⑤ DuckDB-vs-PG DESC 登记）。
- [ ] 负向用例断言具体错误码（冲突报错 + dm/tdengine 一律报错）；无静默跳过。
- [ ] 端到端接线用例通过（parse 赋值 → 门/省略/打印决策）。
- [ ] 既有 `order by` 形态与回显逐字不变；`TestEqlCorpusBaseline` 通过（三份必测逐字一致）。
- [ ] owner doc NULLS 小节收口；矩阵快照门禁 exit 0。
- [ ] `.g4` 改动单独提交；重生成后 `git diff` 已逐一检查且 `ast/_gen/` 零 diff；`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - `ORDER BY … COLLATE` ledger 裁定收口 + roadmap 回写（EC ⑦）

Status: planned
Targets: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（ledger 行 + WI7 行状态）、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（COLLATE 小节）、`ai-dev/logs/`

> 本 Phase 落实 Phase 0 的裁定结论（推荐：显式移出 + successor）；不写产品代码。若 Phase 0 裁定为"落地"，本 Phase 改为文法 + `SqlOrderByItem.collate` 接线 + 各库作用域文档三件套（Phase 0 裁定文档必须已含逐方言无损矩阵），并回滚 Non-Goals 相应条目。

- Item Types: `Decision`、`Proof`

- [ ] **开工前置复核**：WI0-WI3 四个前置 plan 的 `Plan Status` 仍为 `completed`（硬前置已满足；复核为回退防护——收口类操作同样以前置 closure 状态稳定为前提，避免 roadmap 状态交叉漂移）。
- [ ] roadmap「明确延后项」`ORDER BY … COLLATE` 行收口：按裁定结论改写为"已裁定"（移出 ⇒ 写明 successor = 单独调研项 + 触发条件 = 表达式级 collation 排序的真实产品需求；落地 ⇒ 指向落地提交与文档小节）——ledger 上不再存在"挂账无人认领"状态。
- [ ] roadmap WI7 行状态流转：本 plan 独立 closure audit 通过后，按 roadmap Status values 纪律执行 `todo` → `planned` → `done`（不得提前；`planned` 的翻转可在本 plan 转入执行时做）。
- [ ] owner doc COLLATE 小节：各库 collation 作用域差异表（MySQL 表达式/子句位置、MSSQL 库级/列级、Oracle `NLSSORT`、PG `COLLATE`；逐行标 `[文档]`/`[未取证]`）+ "为什么不做"的裁定引用；`SqlOrderByItem.collate` 字段的零消费现状与去向说明（保留在冻结模型、继续计入 roadmap 7 项零消费清单）。
- [ ] **roadmap WI7 行定价回写（候选 A 落地后的文档对齐，独立交付物，不得遗漏）**：① 修订 roadmap 门控矩阵 WI7 行的 Protected Area 单元格——"生成管线（`SqlLimit` 增"语法形态"字段 → 77 文件 `_gen` 重写）"按候选 A 实况改写为 parser 侧 `.g4` 重生成（`EqlParser.java`/`_EqlASTBuildVisitor.java`/`EqlLexer*` 等）、`ast/_gen` 零 diff、`EqlAST.xjava`/`EqlASTKind` 零触碰；② 修订 roadmap WI7 EC ⑥ 的"WI7 要重生成 77 个 `_gen` 文件，`clean install` 必须过"措辞——`clean install` 门槛保留（codegen 全链路 + 114 个 orm.xml 回归仍须完整重建验证），"77 个 `_gen`"按实况注记；③ 两处均**原地留修订注记**（照 WI10a 对 EC ② / WI10b 对 EC ③ 的先例：不删除原文，注记"修订依据 = plan 26 Phase 0 裁定候选 A + 落地实况"），保证历史可追溯；④ 写明 **WI7b/WI12 等后继读取 WI7 行时的口径**："77 文件重写"定价对本 WI7 不再适用；后继若需**新增 AST 节点**仍按 roadmap 约束 1 路径 ⓒ 原定价执行，不受本次注记影响。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0（既有基线 3 项 warning：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，不顺手修、只比对新增量）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [ ] roadmap ledger `ORDER BY … COLLATE` 行已收口（移出 + successor + 触发条件，或落地三件套齐备）；`SqlOrderByItem.collate` 字段处置有明示（保留 + 归属登记）。
- [ ] **roadmap WI7 行定价回写已完成**：门控矩阵 WI7 行 Protected Area 单元格与 EC ⑥ "77 个 `_gen`"措辞已按候选 A 实况修订（原地留修订注记先例，不删原文、注明修订依据），后继（WI7b/WI12）读取口径已写明。
- [ ] owner doc COLLATE 小节已落档（含证据等级标注）；roadmap WI7 行状态与 plan 状态一致。
- [ ] `check-doc-links --strict` 新增 broken link = 0；`ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [ ] 分页两种形态（`FETCH FIRST n ROWS ONLY` / `OFFSET n ROWS FETCH NEXT m ROWS ONLY`）落地：各有断言正确方言 SQL 文本的红→绿正向用例，覆盖 LimitOffset 与 OffsetFetch 双族（EC ①）。
- [ ] `NULLS FIRST/LAST` 在 `defaultNullsFirst` true/false 两类方言上各有用例（EC ②）；oracle（默认 NULLS LAST）与 mysql/mssql（默认 NULLS FIRST）两类默认值的三态对应（一致省略/打印/冲突报错）用例齐备（EC ④）。
- [ ] `defaultNullsFirst` 的 **8 个 XML**（mysql/mssql/dm/h2=true，tdengine/duckdb/postgresql/oracle=false）全部有消费路径断言（EC ③；"7 个"按差异登记第 2 条扩为 8）；`IDialect.isDefaultNullsFirst()` 从无消费转为真实消费（WI3 移交链收口）。
- [ ] `supportNullsFirst` 已有 `IDialect` accessor + `AstToSqlGenerator` 消费点 + 4 个 XML（duckdb/h2/oracle/postgresql）行为断言（EC ③b）。
- [ ] DuckDB-vs-PG `DESC` 缺省 NULL 序差异**既**有显式子句 parity 用例**又**有 owner doc 差异表行（EC ⑤ 并列要求；缺省文本差异用例不可能——两者 `defaultNullsFirst` 均 `false`，见 Phase 0 登记项）；"单布尔不可表达方向相关 NULL 序"能力上限已作为文档项落 owner doc（本 plan 不解决只锁定）。
- [ ] `ORDER BY … COLLATE` ledger 行已裁定收口：落地或显式移出 + successor + 触发条件，二选一已落 roadmap 与 owner doc（EC ⑦）。
- [ ] 负向用例齐备且断言具体错误码：无子句支持且与缺省冲突、`dm`/`tdengine` 显式请求等——无静默降级、无静默打印、无"只打印不设门"半实现（排序原则）。
- [ ] **既有 EQL 语料 BC（EC ⑧）**：`:nop-job-dao`(NopJobTask)、`:nop-auth-service`(test)、`:nop-orm`(test) 三份必测与 WI0 基线快照逐字一致；未声称"7 个全测"（`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份由 roadmap 级 EC 第 8 条 + WI0 静态快照覆盖）；既有 `LIMIT`/`order by` 形态逐字不变（`TestEqlSelectAllLimitEcho`/`TestEqlSelectAllLimitCompile` 全量回归）。
- [ ] 约束 4 的"一致性 + 不等价"两类用例/差异登记齐备（分页两族形态差异、NULL 序省略-vs-报错、DuckDB-vs-PG DESC）。
- [ ] `./mvnw test -pl :nop-orm-eql,:nop-dao,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（roadmap WI7 EC ⑥ 命令）——**"全绿"口径**：本 WI 相关模块全绿 + `nop-stream-runtime` 的 **2 项既有 flake**（`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`、`TestMessageSourceFunctionRestart`，均单跑 PASS）与基线一致即豁免，不得据此判本 WI 失败。
- [ ] `./mvnw clean install -T 1C` 通过（roadmap WI7 EC ⑥ 明言"WI7 要重生成 77 个 `_gen` 文件，`clean install` 必须过"；候选 A 下 `ast/_gen/` 零 diff，但 codegen 全链路与 114 个 orm.xml 回归仍须完整重建验证）。
- [ ] 生成物纪律：每个 Phase 的 `.g4` 改动单独提交（`git log` 文法提交与逻辑提交分离可审计）；每步重生成后 `git diff` 逐一检查；`ast/_gen/` 零 diff（候选 A 下）；无 lexer token 删除、`EqlASTKind` ordinal 未被持久化或比较。
- [ ] `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（预期零增量，差异登记第 7 条）。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0（既有基线 3 项 warning：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，不顺手修、只比对新增量）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/26-eql-wi7-pagination-and-nulls.md --strict` 退出码 0。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中（anti-hollow："只解析不门控/只打印不设门"正是半实现形态）。
- [ ] owner doc 已同步到 live baseline（`eql-and-database-compatibility.md` 标志表/分页小节/NULLS 小节/能力上限小节/COLLATE 小节/能力点登记表），`docs-for-ai/INDEX.md` 与 `04-reference/source-anchors.md` 若路由/锚点变化已更新。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）两条端到端调用链真实连通：EQL 分页文本 → parse 归一化 → `visitSqlQuerySelect` → `paginationHandler.buildPageExpr` → 方言 SQL；`ORDER BY … NULLS` 文本 → parse 赋值 → transform → `visitSqlOrderByItem` 门/省略/打印决策 → 方言 SQL 或响亮报错（不只是文法分支/方法存在）；（b）无空方法体/静默跳过/no-op（不支持方言一律响亮报错或可证等价的省略，不允许回退原文静默输出）；（c）`nullsFirst == null` 路径输出逐字节不变。
- [ ] **独立 closure audit 已完成并由不同 `task_id` 的子 agent 执行**，evidence 写入本 plan `Closure` 段（guide #26/#27；roadmap WI7 EC ⑨）；roadmap Work Item Status 的 WI7 行据此才可 `planned` → `done`（见 roadmap Status values）。

## Deferred But Adjudicated

### `OFFSET n ROWS`（无 FETCH 的独立 offset 形态）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap WI7 只点名两种分页形态（EC ① 同口径）；独立 offset 形态无已登记的用户需求输入，且其 AST 表示（limit=null, offset=n）虽天然可行，接受与否属文法面扩权，应先登记 roadmap。
- Successor Required: `no`（出现需求先登记 roadmap 再立项）。
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（新增登记行）。

### 方向相关缺省 NULL 序的表达力缺口（DuckDB `NULLS LAST` even for DESC / H2 `FIRST`/`LAST` 方向无关设置）

- Classification: `watch-only residual`
- Why Not Blocking Closure: roadmap WI7 EC ⑤ 明文"把该限制作为文档项写进 owner doc（**不要求本 WI 解决**）"——本 plan 落差异用例与文档项；表达力扩展（如 per-dialect 方向模型字段）是方言模型 schema 变更，远超本 plan 结果面。
- Successor Required: `no`（若产品出现 DESC 缺省序敏感的跨库一致性需求，先登记 roadmap）。
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（观察项）。

### `ORDER BY … COLLATE`（若 Phase 0 裁定为显式移出）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 约束 3（不满足"能无损 lowering"的项一律不做）——MySQL 子句位置差异 + MSSQL collation 库级作用域 + Oracle `NLSSORT` 体系 ⇒ 跨库无损归一不成立；roadmap ledger 行在本 plan 收口时改为已裁定状态，不存在"挂账无人认领"（EC ⑦ 的两种合法结局之一）。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（单独调研项；触发条件 = 表达式级 collation 排序的真实产品需求）；各库 collation 作用域差异文档落 `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`。

## Non-Blocking Follow-ups

- h2 的 `OffsetFetchPaginationHandler` 配置处于注释态（`h2.dialect.xml:20`）——H2 在 REGULAR 模式同时支持 `LIMIT … OFFSET` 与 `OFFSET … ROWS FETCH …`，是否切换属方言契约变更（G-4 类确认），本 plan 沿用现状（LimitOffset）并在 owner doc 分页小节登记。
- `IDialect.java` 的 `isSupportNullsFirst` javadoc 在 Phase 2 落地前是前向引用（`{@link}` 指向不存在的方法）——随 accessor 落地转正，无需单独处理；若 Phase 2 排期延后，保持现状不构成 broken build。
- AstGrammarBuilder 的 altLabel/prop 约束与"空节点静默通过 validate"风险模式文档化（现散在 WI1/WI5/本 plan 三个 plan 的执行期记录中）——若 `docs-for-ai/02-core-guides/` 后续增设 EQL 文法指南，随迁。
- `TestDefaultNullsFirstFlag` 的 javadoc 中"accessor 归 WI7"等移交措辞在 WI7 落地后已过时——随 Phase 2 测试升级顺带更新措辞（非独立工作项）。

## Closure

Status Note: （完成或关闭时填写：为什么这个 plan 可以关闭）
Completed: （YYYY-MM-DD）

Closure Audit Evidence:

- Reviewer / Agent: （独立审阅者或独立子 agent，须与执行者不同 task_id）
- Audit Session: （session/task ID）
- Evidence:
  - （每条 Exit Criterion 与 Closure Gate 的验证结果 PASS/FAIL + live code path / test name）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/26-eql-wi7-pagination-and-nulls.md --strict` 退出码
  - Anti-Hollow 检查结果（两条端到端调用链追踪 + `scan-hollow-implementations.mjs` 退出码）
  - Deferred 项分类检查（确认无 in-scope live defect 被降级）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
- （或者明确写 no remaining plan-owned work）
