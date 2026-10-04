# 27 EQL WI8 — NULL 三值语义谓词（`IS [NOT] DISTINCT FROM` / `IS [NOT] TRUE/FALSE/UNKNOWN`）

> Plan Status: active
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI8 节 + 共同约束 1-4 + 门控矩阵 WI8 行 + Cross-Cutting 全部 + 关键约束与红线「`SAFE_EQ_` 必须保留」，v7.2）；Current Baseline 全部代码事实于 2026-10-04 在本工作区分支 `feat/eql-cross-db-compat` 逐文件实测核验；2026-10-04 按独立审查（3 Major + 5 Minor）修订：Major-1 展开公式钉死（含禁止形态）、Major-2 SAFE_EQ 渲染三分支升格必选件（归属 Phase 1）、Major-3 操作数类型域 + 安全词表、Minor 行号/枚举边界/载体/方言来源/`:nop-dao` 修正
> Related: `ai-dev/plans/20-eql-wi3-dialect-config-fixes.md`（硬前置，已 `completed`）、`ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md`（**硬前置——产品面已于 2026-10-04 全部落地，但 plan 21 状态仍为 `active`（独立 closure audit 待执行）、roadmap WI4 行仍为 `planned`；本 plan Phase 0 可开工，Phase 1 起的代码 Phase 在 plan 21 变为 `completed` 前不得开工，见硬前置声明**）、`ai-dev/design/eql-cross-db-compat/2026-10-04-wi4-phase0-adjudications.md`（§二 2c 槽位/方言来源契约，本 plan 直接消费）、`ai-dev/plans/24-eql-wi5-sql92-function-syntax.md`（文法备选分支 + 构建期归一化的结构先例）

## 硬前置声明（开工门，非空话）

roadmap 门控矩阵 WI8 行明文硬前置 = **WI3、WI4**，另受 **G-1** 约束。实测现状（2026-10-04）：

- **WI3 已 done**：`ai-dev/plans/20-eql-wi3-dialect-config-fixes.md` = `Plan Status: completed`；roadmap Work Item Status WI3 行 = `done`。
- **WI4 产品面已落地、形式收口未完成**：`ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md` 的 Phase 0/1/2 全部 `completed`（2026-10-04），WI8 面向的三项契约（固定环节 / 缓存安全 / 槽位+方言来源）已有证明测试与 owner doc 落档；但 plan 21 的 `Plan Status` 仍为 `active`（独立 closure audit 与两道收口门禁留白）、roadmap WI4 行仍为 `planned`。
- **G-1 已裁决**（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`）：承诺基线 = Oracle 23ai / MySQL 8.0.31 / MariaDB 10.2 / PG 15 / MSSQL 2022 / SQLite 3.39；全部 unverified 边界（含 **Oracle `IS TRUE`/`IS FALSE` 简写** 与 **DB2 `DISTINCT` 谓词证据**）维持 unverified 纪律、不计入覆盖率。

⇒ **本 plan 的 Phase 0（纯裁定、不改产品代码）可立即开工；Phase 1 及之后的所有代码 Phase 在 plan 21 的 `Plan Status` 变为 `completed` 之前不得开工**（排队先例：plan 23 对 WI10a、plan 24 对 WI4 同型）。每个代码 Phase 开工前，执行者必须复核 plan 21 状态并记录于当次 daily log。

## Purpose

收口 roadmap WI8：让 NULL 三值语义谓词 `IS [NOT] DISTINCT FROM` 与 `IS [NOT] TRUE/FALSE/UNKNOWN` 在 EQL 中可解析（今天全部解析失败——响亮但不可用），并按方言矩阵无损 lowering：原生支持的方言直接输出；MySQL/MariaDB 以保留 token `<=>`（`SAFE_EQ_`）承载 `IS [NOT] DISTINCT FROM`；仅 Oracle 需三节点展开——该展开作为 **WI4 AST 改写管线的第一个真实生产消费者**（实现 `IEqlAstTransformer` + `SessionFactoryConfig.eqlAstTransformer` 默认注入，自动获得 WI4 的缓存绕过保护）。`IS NOT TRUE`/`IS NOT FALSE` **不得降级为二元比较**（roadmap 警告：`a IS NOT TRUE` 在 `a IS NULL` 时为 TRUE 而 `a<>0` 为 NULL），必须按本 plan Phase 0 的真值表设计逐案落地与逐案测试（roadmap EC ③）。每个谓词具备：断言正确方言 SQL 文本的正向用例（红→绿）、版本门/能力缺失方言的编译期响亮报错负向用例、约束 4 要求的「一致性 + 不等价」两类用例、owner doc 覆盖表、既有 EQL 语料 BC。

## Current Baseline

> 所有锚点为**符号定位优先**；file:line 为 2026-10-04 实测参考值。生成物行数随文法 WI 并行实施漂移（历史：`EqlParser.java` roadmap 10785 → plan 24 实测 10904 → 本次实测 10927），执行时以 Phase 0 锚点复核为准。

### WI4 管线现状（本 plan 的关键新基线，2026-10-04 实测——本 plan 是该管线第一个真实生产消费者）

- **接口**：`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/IEqlAstTransformer.java:13-17`——仅两方法：`transformBeforeAnalyze`（`:14`）与 `transformAfterAnalyze`（`:16`），均接收 `(SqlProgram, name, sql, ISqlCompileContext)`。
- **固定编译环节**：`EqlCompiler.java`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java`）`:56` 从上下文取 transformer、`:58-60` null 保护 + `transformBeforeAnalyze`、`:65-67` null 保护 + `transformAfterAnalyze`；生产构造点仅 `LoadedOrmModel.java:102/:121`，即 **EQL 编译唯一生产入口是 `LoadedOrmModel.compileSql`**。
- **缓存绕过保护（WI4 裁定候选 B，已落地）**：`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:92-93`（`astTransformer == null` 回落 `env.getDefaultAstTransformer()`）+ `:99`（`useCache && astTransformer != env.getDefaultAstTransformer()` ⇒ `useCache=false`，不读不写计划缓存）。⇒ **本 plan 的生产 transformer 经默认注入即可，无需（也不得）per-call 传入**——显式传入非默认实例的路径按 WI4 契约仅限测试/工具且不参与缓存。
- **默认注入链**：`SessionFactoryConfig.java:81` 字段 `eqlAstTransformer` / `:96` getter / `:99-100` setter；`OrmSessionFactoryBean.init()` 时一次性注入 `SessionFactoryImpl.defaultAstTransformer`。**当前全仓零 `setEqlAstTransformer` 配置源 ⇒ 管线处于"已接线、未配置"合法态，WI8 落地前零生产行为变化**。
- **契约文档**：`ILoadedOrmModel.java:67-73` javadoc（新契约：缓存中只存在默认 transformer 编译的计划；生产代码不应 per-call 传 transformer）；owner doc `docs-for-ai/02-core-guides/eql-and-database-compatibility.md:326` 起「AST 改写管线（`IEqlAstTransformer`，2026-10-04 WI4 接线）」小节，其 `:345`「WI8 消费指引」：**`IS DISTINCT FROM` 类方言展开放 `transformAfterAnalyze`（只重排/包装既有操作数节点，满足参数保持条件），方言经已解析表源推导**。
- **时机槽位与方言来源裁定（本 plan 直接消费，不留白）**：`ai-dev/design/eql-cross-db-compat/2026-10-04-wi4-phase0-adjudications.md` §二 2c——需要方言判定的改写放 `transformAfterAnalyze`，方言经「已解析表源 → querySpace → `context.getDialectForQuerySpace(querySpace)`」确定（接口方法 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/ISqlCompileContext.java:24`）；「before + 默认 querySpace 猜测」= 低可信、已拒绝。`IS DISTINCT FROM` 三节点展开的**参数保持性论证 WI8 可直接引用**（不新增 `SqlParameter`、不新增实体引用、相对参数顺序不变）。
- **证明测试先例**：`nop-persistence/nop-orm/src/test/java/io/nop/orm/TestEqlAstTransformerWiring.java`——`:74` `MarkerTransformer` 测试桩、`:134` `testDefaultTransformerFromFactoryConfigEndToEnd`（factory 默认配置端到端 + 运行时调用计数断言）、`:197` `testNonDefaultTransformerBypassesQueryPlanCache`（缓存绕过三段断言）。测试注入方式 = `init()` 前 `setEqlAstTransformer(...)`（走与生产完全相同的配置路径）。

### 文法现状（2026-10-04 实测——六种谓词形态今天全部解析失败，响亮非静默）

- `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlExpr_primary`（`:142-148`）只有 `IS not=NOT? NULL`（`:143`，`# SqlIsNullExpr`）；`:144` 为**注释态** `// | booleanPrimary SAFE_EQ_ predicate`；`comparisonOperator_`（`:150-152`）= `EQ_ | GTE_ | GT_ | LTE_ | LT_ | NEQ_`，**不含 `SAFE_EQ_`**。四个 `.g4` 文件全文 grep `IS DISTINCT`/`DISTINCT FROM`/`IS TRUE`/`IS UNKNOWN` 均 **0 命中** ⇒ 六种谓词形态今天在**解析层**失败（`nop.err.antlr.not-end-properly` 类响亮失败），不存在静默错误 SQL 的既有路径。
- 所需关键字 token **全部已存在**：`TRUE`（`Keyword.g4:269`）、`FALSE`（`:273`）、`DISTINCT`（`:150`）、`UNKNOWN`（`SQL92Keyword.g4:684`）；布尔字面量规则 `sqlBooleanLiteral`（`BaseRule.g4:61-63`）= `value=(TRUE | FALSE)`。⇒ 文法改动 = parser 规则新增备选分支，**零词法器改动**。
- **红线**：`SAFE_EQ_: '<=>'`（`Symbol.g4:38`）**必须保留**——它是 WI8 的 MySQL lowering 输出目标，需要可词法化。本 plan 不删除任何 lexer token（删除会重编号全部 token 类型，roadmap 关键约束红线）。
- **`<=>` 作为输入语法今天不可解析**（`:144` 注释态）；解锁该输入形态不在本 plan 范围（见 Non-Goals 与 `Deferred But Adjudicated`）。

### AST / 生成器事实（决定归一化定价与 Protected Area）

- **`SqlOperator` 枚举无 `SAFE_EQ` 成员**（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/enums/SqlOperator.java:21` 现有 `IS/LT/LE/EQ/NE/GT/GE`）。该文件是**手写**（`enums/` 下、非 `ast/` 生成链），新增枚举成员零生成，但须显式裁定四处边界（审查 Minor-② 清单）：① `isCompareOp()`（`:51-53`）是否纳入 SAFE_EQ（决定比较语义判定面）；② `getLeftSqlType`/`getRightSqlType`/`getResultSqlType`（`:79-90`/`:92-104`/`:106-117`）——`EQ` 落 `StdSqlType.OTHER`（`:89`/`:103`/`:116` 默认分支），SAFE_EQ **显式确认与 EQ 一致落 OTHER**；③ `reverse()`（`:119-136`，`default` 返回 **null**）——SAFE_EQ 自反（`a<=>b` ⇔ `b<=>a`），须显式返回自身、不得静默落 null；④ 序列化担忧**关闭**：该枚举无 `valueOf` 消费、无 `ordinal` 持久化（roadmap 红线本就禁止 ordinal 持久化），查询计划缓存存的是 `CompiledSql` 而非 AST（WI4 契约）。
- `SqlBinaryExpr.operator` 字段类型即 `SqlOperator`（`ast/_gen/_SqlBinaryExpr.java:21`）⇒ 「`a IS NOT DISTINCT FROM b` 归一化为 `SqlBinaryExpr`（operator=`SAFE_EQ`）」在**不新增 AST 节点**前提下技术上成立，前提是 Phase 0 裁定补 `SqlOperator.SAFE_EQ` 成员（含优先级与 `isCompareOp()`（`:51-53`）边界裁定）；`a IS DISTINCT FROM b` 以 `SqlNotExpr` 包装。
- **`IS [NOT] UNKNOWN` 有零成本精确归一化目标**：`UNKNOWN` ≡ `NULL` 三值等价 ⇒ 归一化进既有 `SqlIsNullExpr`（含 `not` prop，`:143` 同款），全方言复用既有 `IS [NOT] NULL` 渲染，**零 lowering 工作**（`AstToEqlGenerator.visitSqlIsNullExpr:809` 回显路径既有）。
- **`IS [NOT] TRUE/FALSE 无既有节点可保身份**：`SqlIsNullExpr` 单操作数、`SqlBinaryExpr` 无 `not` prop；唯一保身份形态 = 新 AST 节点（路径 ⓒ：`EqlAST.xjava` + `EqlASTKind` ordinal 重编号红线 + `ast/_gen/` 77 文件 + `EqlParser.java` 10927 行 + `_EqlASTBuildVisitor.java` 1819 行重生成）。构建期通用展开（真值表驱动，见 Phase 0）只用既有节点，正确性无损、但放弃原生方言「直接输出」的形态偏好——两案定价由 Phase 0 裁定。
- **altLabel 三约束（WI1 interval / WI5 position 先例，执行前必读）**：altLabel → AST 类名推导规则 = 取 label 首段下划线前缀（`AstGrammarBuilder.getAstNodeName`）；新备选 label 必须以目标 AST 类名为前缀（如 `# SqlBinaryExpr_isDistinctFrom`）；归一化逻辑全部放手写 `EqlASTBuildVisitor` visit 方法**完全重写、不调 super**（WI1 `EqlASTBuildVisitor.java:212-221` javadoc 先例）；同规则既有无 label 备选需同步显式 label 化。⚠️ `SqlBinaryExpr` 无 `not` prop：`IS NOT? DISTINCT FROM` 的 `NOT` 消费方式（不带 prop label、由手写 visit 经 ANTLR ctx 访问器读取）属 Phase 0 确认项。
- **生成器持有方言**：`AstToSqlGenerator.java:77` `private final IDialect dialect`（方言条件渲染先例：`visitSqlLikeExpr` 的 ILIKE 门，`ERR_EQL_NOT_SUPPORT_ILIKE` 原则出处）。
- **operator 渲染全局无方言分支（审查 Major-2 实测确认，SAFE_EQ 渲染三分支=必选件的依据）**：`AstToEqlGenerator.printOperator`（`:202-206`）直出 `operator.getText()`，`visitSqlBinaryExpr`（`:804`，`:821` 调 printOperator）无任何方言开关，`AstToSqlGenerator` 未覆写两者 ⇒ **不新增覆写时同一 `SqlOperator.SAFE_EQ` 将在全方言输出 `<=>`**（PG 等直接产出非法 SQL = 静默错误）⇒ SAFE_EQ 的按方言渲染三分支（native → `IS [NOT] DISTINCT FROM` 文本 / mysql 族 → `<=>` / 其余 → 响亮报错）是**必选件**，归属 Phase 1 的 `AstToSqlGenerator` 覆写（回显路径无方言概念、不受影响）；transformer 只承担 Oracle 展开（见 Phase 0 载体分工）。
- **生成管线数字（2026-10-04 实测）**：`EqlParser.java` **10927 行**、`_EqlASTBuildVisitor.java` **1819 行**、`ast/_gen/` **77 个文件**；重生成命令 `./mvnw generate-sources -pl :nop-orm-eql -am`。默认形态 = 不新增 AST 节点 ⇒ `ast/_gen/` 零 diff。

### 方言矩阵与证据纪律（roadmap WI8 节 + G-1；`IS TRUE/FALSE` 的矩阵 Phase 0 补建）

- **`IS [NOT] DISTINCT FROM` 矩阵（roadmap 给定）**：`<=>` 仅 MySQL/MariaDB；**PG / H2 / DB2 / DuckDB / MSSQL-2022 / SQLite-3.39 原生支持**（H2 文法 `Distinct Predicate`："null-safe … never evaluates to UNKNOWN"）；**仅 Oracle 需三节点展开**。继承别名随父方言（`mysql5.7`←mysql、`h2gis`←h2、`postgis`←postgresql、`oracle-reverse`←oracle）；`es` 伪方言不参与矩阵。
- **`IS [NOT] TRUE/FALSE/UNKNOWN` 矩阵 roadmap 未给定**——Phase 0 必须按证据等级纪律逐方言补建（一手文档引证；MySQL 数值真值语义 vs 标准 SQL 严格布尔语义的固有分叉必须登记为约束 4「不等价」类）。
- **unverified 纪律（红线，不得计入覆盖率）**：Oracle 23ai 的 `IS TRUE`/`IS FALSE` 简写与 12.1 边界**未取得一手引证**（23ai `SELECT` 页零 `IS TRUE`；11g/12.1 文档 404）⇒ 按 G-1 同一纪律标 `unverified`，落地前须实测；**DB2 证据弱**（仅检索片段确认 `DISTINCT-predicate` 页存在，IBM 文档直连 403）⇒ 若纳入承诺范围须在 WI8 内补一手取证；`dm`/`tdengine` 无可引用一手文档，一律 `unverified`。
- **⚠️ 仓库无 `sqlite` 方言文件**：`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/` 下 18 个文件无一为 sqlite ⇒ roadmap WI8 EC ④ 的「SQLite 3.39 以下编译期报错负向用例」**没有现成方言载体**，须由 Phase 0 裁定测试载体（候选：手构 `DialectModel`，手构方言先例 = `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlLateralSubqueryTableSource.java:78` 的 `buildDialect(boolean)`；**不新建 sqlite.dialect.xml**，见 Non-Goals）。
- **`default.dialect.xml` 无任何 distinct/boolean 谓词相关能力标志**——「原生支持 `IS [NOT] DISTINCT FROM`」目前无配置通路；是否新增方言标志（`dialect.xdef` + `DialectFeatures` retention 手改 + `IDialect` accessor + 消费点 + 负向用例，零生成但四件套成本）或按 G-1 固定矩阵硬编码 + 版本门，由 Phase 0 裁定。
- 真库执行证据面：`TestDialect.testFunctions` 遍历当前数据源方言真库执行，测试基座 `JdbcTestCase` 硬编码 **H2 内存库** ⇒ CI 真库证据仅 H2；谓词类交付的执行证据按 WI5 同口径处理（H2 真执行 + 其余方言文本断言 + `[未取证]` 标注）。

### 测试基建与 BC 基础（WI0 遗产，本 plan 直接消费）

- 方言能力矩阵棘轮：`TestDialectCapabilityMatrix` + 快照 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json` + 门禁 `ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs`（谓词不进函数表；**若 Phase 0 裁定新增方言能力标志，快照按棘轮规则同步**）。
- 既有 EQL 语料基线棘轮：`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` + `TestEqlCorpusBaseline`。**2026-10-04 实测：7 个含 `<eql>` 的 `sql-lib.xml` 对 `is distinct from`/`is true`/`is false`/`is unknown` 全量 grep = 0 命中** ⇒ 文法新增备选对既有语料是纯解析集增量，BC 判据风险面极小（仍须棘轮实测兜底）。
- 全绿口径与 stream 豁免：含 `:nop-auth-service` 的命令会拖入 `nop-stream-runtime`，其 **2 项既有失败按基线豁免**（均与本 roadmap 零代码关联）：`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`（WI0 既档）+ `TestMessageSourceFunctionRestart`（WI1 收口轮实测，见 plan 03 Closure 段）。⇒ **"全绿" = 本 WI 相关模块全绿 + 这 2 项与基线一致**。

## Goals

- 六种谓词形态（`IS [NOT] DISTINCT FROM`、`IS [NOT] TRUE`、`IS [NOT] FALSE`、`IS [NOT] UNKNOWN`）可解析，且按 Phase 0 真值表与方言矩阵无损 lowering；每谓词有断言**正确方言 SQL 文本**的红→绿正向用例（roadmap EC ①②③）。
- Oracle 三节点展开以 **WI4 管线生产消费者**形态落地：实现 `IEqlAstTransformer`，经 `SessionFactoryConfig.eqlAstTransformer` 默认注入，走 `transformAfterAnalyze` 槽位（WI4 §二 2c 契约），自动获得缓存绕过保护（roadmap EC ②）。
- `IS NOT TRUE`/`IS NOT FALSE` 的 NULL 语义按真值表**逐案**落地与逐案测试，不得降级为二元比较、不得复用 `IS DISTINCT FROM` 展开模板（roadmap EC ③ + roadmap 警告）。
- 三节点展开 vs `<=>` 在 MySQL 三值逻辑下的**不等价**用例齐备（roadmap EC ① + 约束 4）。
- 版本门负向用例：SQLite 3.39 以下（受 G-1）等「方言矩阵判不支持」场景编译期响亮报错，断言具体错误码；无静默跳过——无 transformer 配置时规范 AST 仍必须是全方言安全形态（roadmap EC ④ + 排序原则 P1）。
- owner doc `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 新增 NULL 谓词小节（逐方言覆盖表 + 证据等级 + 能力点登记表行）；矩阵快照棘轮同步（若 Phase 0 裁定新增方言标志）。
- 既有 EQL 语料 BC：三份必测与 WI0 基线快照逐字一致（roadmap EC ⑥）；独立 closure audit（不同 task_id，roadmap EC ⑦）。

## Non-Goals

- **不解锁 `a <=> b` 作为输入语法**（`BaseRule.g4:144` 注释态维持；`SAFE_EQ_` token 保留但本 plan 仅作 MySQL lowering 输出目标——登记于 `Deferred But Adjudicated`）。
- **不新增 AST 节点**（默认形态）：不触 `EqlAST.xjava` / `ast/_gen/` / `EqlASTKind` ordinal（roadmap 红线）；若 Phase 0 裁定 `IS [NOT] TRUE/FALSE` 必须保身份走路径 ⓒ，属显式 scope change + 全量定价 + 回滚本条。
- **不新建 sqlite 方言文件**（`sqlite.dialect.xml`）；EC ④ 负向用例用手构方言模型承载。
- **不做 `IS NULL` 行为变更**：`SqlIsNullExpr` 既有解析/渲染/回显逐字不变（守护用例锁定）；`comparisonOperator_` 既有六成员不动。
- 不做 WI5/WI6/WI7/WI7b 的函数与分页面；不碰 WI3 方言配置纠错的既有结论；`greatest`/`least`（G-2）无关。
- 不把 `:nop-orm-demo` / `nop-spring-simple-demo` / `:nop-orm-tdengine` / `:nop-db-migration` 拉进本 plan 的 `-pl`（roadmap 级 EC 第 8 条 + WI0 静态快照兜底）。
- 不改 `IEqlAstTransformer` 接口、`ISqlCompileContext`、`QueryPlanCacheKey`、`LoadedOrmModel` 的 WI4 既有实现（只作消费者；若消费中发现缺陷，登记 roadmap/新 plan，不顺手修）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql`：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`sqlExpr_primary` 新增谓词备选分支）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`（构建期归一化，完全重写不调 super 的先例形态）、必要时 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/enums/SqlOperator.java`（Phase 0 裁定补 `SAFE_EQ` 成员）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（SAFE_EQ 渲染三分支覆写，**必选件**）、新生产 `IEqlAstTransformer` 实现（Oracle 三节点展开 / `IS NOT TRUE/FALSE` 补充展开，按 Phase 0 裁定的载体分工）与重生成产物；对应测试（`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/`、`compile/`、`sql/`）。
- `nop-persistence/nop-orm`：transformer 的配置消费测试（`TestEqlAstTransformerWiring` 先例同型——factory 默认注入路径 + 缓存绕过保护回归）。
- `nop-persistence/nop-dao`：仅当 Phase 0 裁定新增方言能力标志时——`dialect.xdef`（声明）+ `DialectFeatures.java`（手写 retention，零生成）+ `IDialect`/`DialectImpl`（accessor）+ 方言 XML 取值 + 矩阵快照重生成。
- 文档：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（NULL 谓词小节）；`ai-dev/design/eql-cross-db-compat/`（Phase 0 裁定文档）；`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（若裁定新增标志）；`ai-dev/logs/`。

### Out Of Scope

- Non-Goals 所列全部；执行中发现的新缺陷/新需求一律登记 roadmap，不顺手修。

## 与 roadmap 的差异登记（起草时实测发现）

1. **WI4 状态与任务口径**：roadmap WI8 行硬前置写 "WI3、WI4"，roadmap Work Item Status 中 WI3=`done` 而 **WI4=`planned`**（plan 21 `active`、closure audit 待执行）；但 WI4 **产品面已于 2026-10-04 全部落地且经证明测试**（固定环节/缓存绕过/槽位契约 + owner doc + `TestEqlAstTransformerWiring`）。本 plan 按实测基线书写：代码 Phase 以 plan 21 `completed` 为开工门（硬前置声明），不等待 roadmap 行翻转（roadmap 行翻转由 plan 21 自身的独立 audit 触发，与本 plan 无关）。
2. **生成物行号漂移（非实质偏离）**：`EqlParser.java` roadmap 10785 → plan 24 实测 10904 → **本次实测 10927**；`_EqlASTBuildVisitor.java` 1804 → 1819（稳定）；`ast/_gen/` 77（稳定）。owner doc「AST 改写管线」小节实测位于 `eql-and-database-compatibility.md:326` 起（任务口径 `:328` 起，「WI8 消费指引」实测 `:345`）——以符号定位为准。
3. **EC ④ 无现成载体**：roadmap WI8 EC ④ 要求「SQLite 3.39 以下方言的编译期报错负向用例」，但仓库 18 个 `*.dialect.xml` 中**无 sqlite**（且 `db2` 在 WI3 后是否可加载以执行时实测为准）⇒ 负向用例载体须为手构 `DialectModel`（先例：各方言门测试 + `buildDialect` 模式），Phase 0 裁定并记录。
4. **`IS [NOT] TRUE/FALSE/UNKNOWN` 方言矩阵缺失**：roadmap WI8 节只给了 `IS [NOT] DISTINCT FROM` 的矩阵与 Oracle `IS TRUE/FALSE` unverified 标注，未给 TRUE/FALSE/UNKNOWN 的逐方言矩阵 ⇒ Phase 0 必须补建（含证据等级栏），并登记 MySQL 数值真值 vs 标准 SQL 严格布尔的语义分叉（约束 4）。
5. **`SqlOperator` 无 `SAFE_EQ` 成员 + operator 渲染无方言分支**：roadmap 红线只说 `SAFE_EQ_` token 必须保留；归一化到 `SqlBinaryExpr` 还需手写枚举补成员（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/enums/SqlOperator.java`，零生成，四处边界清单见 Current Baseline），且 `printOperator`/`visitSqlBinaryExpr` 全局无方言分支（实测 `AstToEqlGenerator.java:202-206`/`:804`）⇒ **渲染三分支覆写是必选件**（roadmap 未登记的第二前提），归属 Phase 1。
6. **`<=>` 输入语法不可解析是既有现状**：`BaseRule.g4:144` 注释态意味着 `a <=> b` 今天解析失败（响亮）；本 plan 不改变该现状（Non-Goals），roadmap 亦未要求解锁。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 新增备选 → 经 `gen-eql-parser.xgen` 重生成 `EqlParser.java`（10927 行）等 | **生成管线，plan-first**（AGENTS.md Protected Area；本 plan 即载体，执行前须过 draft review） | 重生成命令 `./mvnw generate-sources -pl :nop-orm-eql -am`；`.g4` 改动**单独提交**（roadmap Cross-Cutting 生成物变更纪律）；不删除任何 lexer token（`SAFE_EQ_` 保留红线） | owner doc + 各 Phase 红/绿用例 + 三份必测 BC |
| `EqlAST.xjava` / `ast/_gen/` / `EqlASTKind` | **禁止触碰**（默认形态；`EqlASTKind` ordinal 重编号为 roadmap 红线） | 若 Phase 0 裁定 `IS [NOT] TRUE/FALSE` 走路径 ⓒ → 显式 scope change + 路径 ⓒ 全量定价（77 `_gen` + parser 10927 行 + visitor 1819 行）+ 回滚本声明 | 每代码 Phase `git status` 中 `ast/_gen/` 零 diff |
| `nop-dao` 的 `dialect.xdef` + `DialectFeatures.java`（手写 retention）+ `IDialect`/`DialectImpl` + `*.dialect.xml`（仅当裁定新增标志） | **零生成、非 AGENTS.md 字面 Protected Area**（roadmap Cross-Cutting 第一行；但属平台对外方言契约） | 按「AI Must Ask Before」第 1 条处理（比 plan-first 更严的自定门禁）；逐项登记 + 矩阵快照棘轮同步 | 快照门禁 exit 0 + 负向用例；**不做旧值改写，纯增量** |
| `IEqlAstTransformer` 生产实现（nop-orm-eql 或 nop-orm 侧新增类） | 普通手写代码 + factory 配置注入 | 不改接口本身（WI4 Non-Goal 延续）；beans.xml 配置按 Nop IoC 规则（显式 `<bean>` 定义，无注解扫描） | 注入路径端到端测试（`TestEqlAstTransformerWiring` 同型） |

⚠️ 本 plan 不触碰 `model/*.orm.xml`、`nop-auth` 认证模型、`nop-*-api` 公共接口、`nop-core`/`nop-xlang`/`nop-xdef`。

## 执行摘要：每谓词路径定价框架（Phase 0 的裁定对象，非最终结论）

| 谓词 | 解析（文法直通 ⓐ） | 规范 AST 形态候选 | 方言 lowering | 相对成本 |
|---|---|---|---|---|
| `IS [NOT] UNKNOWN` | `sqlExpr_primary` 新备选 | 归一化既有 `SqlIsNullExpr`（直用其自带 `not` prop，不另包 `SqlNotExpr`）——三值精确等价、全方言通用 | 复用既有 `IS [NOT] NULL` 渲染，**零 lowering 工作** | **最低** |
| `IS [NOT] DISTINCT FROM` | `sqlExpr_primary` 新备选 | 归一化 `SqlBinaryExpr`（operator=`SAFE_EQ`，需补手写枚举成员；`IS DISTINCT FROM` 方向以 `SqlNotExpr` 包装）vs 新谓词节点（ⓒ 高价） | **渲染三分支（`AstToSqlGenerator` 覆写，必选件）**：MySQL/MariaDB=`<=>`；PG/H2/DB2/DuckDB/MSSQL-2022/SQLite-3.39=`IS [NOT] DISTINCT FROM` 文本；其余=响亮报错 `nop.err.eql.dialect-not-support-feature`；**Oracle=三节点展开（WI4 管线消费者，正反公式钉死见 Phase 0）** | 中 |
| `IS [NOT] TRUE` / `IS [NOT] FALSE` | `sqlExpr_primary` 新备选 | **无既有节点可保身份**：构建期通用展开（真值表驱动 + **安全词表钉死**，只用既有节点、正确性无损、放弃原生直出）vs 新 AST 节点（ⓒ：`EqlASTKind` ordinal 重编号 + 77 `_gen` + parser/visitor 重生成） | 展开文本（安全词表式，操作数类型域按 Phase 0 裁定）；Oracle=`unverified` ⇒ 展开或显式报错（G-1）；原生方言直出仅 ⓒ 案可得 | 两案分别为 中 / 高 |

`IS [NOT] TRUE/FALSE/UNKNOWN` 真值表设计规格（Phase 0 落档，标准 SQL 严格三值语义；`a<>TRUE` 列为**禁止的错误降级对照**）：

| 操作数 a | `a IS TRUE` | `a IS NOT TRUE` | `a IS FALSE` | `a IS NOT FALSE` | `a IS UNKNOWN` | `a IS NOT UNKNOWN` | `a<>TRUE`（错误降级） |
|---|---|---|---|---|---|---|---|
| TRUE | TRUE | FALSE | FALSE | TRUE | FALSE | TRUE | FALSE |
| FALSE | FALSE | TRUE | TRUE | FALSE | FALSE | TRUE | TRUE |
| NULL | FALSE | **TRUE** | FALSE | TRUE | TRUE | FALSE | **NULL** ← roadmap 警告的失配行 |

**非布尔操作数行（审查 Major-3 追加）**——钉死严格三值语义下非布尔字面量 `2` 的期望值：

| 操作数 a=2 | `a IS TRUE` | `a IS NOT TRUE` | `a IS FALSE` | `a IS NOT FALSE` | `a IS UNKNOWN` | `a IS NOT UNKNOWN` | `a<>TRUE`（错误降级） |
|---|---|---|---|---|---|---|---|
| 2 | **FALSE** | TRUE | FALSE | TRUE | FALSE | TRUE | TRUE |

⚠️ MySQL 原生 `IS TRUE` 为数值真值语义（`2 IS TRUE`=TRUE），与钉死语义（FALSE）存在固有分叉——该行是用例矩阵的必备行（见 Phase 0 裁定项与 Phase 2 用例）。

**Oracle 三节点展开公式钉死（审查 Major-1；以下公式已经 3VL 逐行验算，Phase 0 落档为唯一裁定基准）**：

- `a IS DISTINCT FROM b`（正向）≡ **主钉死公式 = 全二值 guard 三支式**（安全词表内）`(a IS NOT NULL AND b IS NOT NULL AND a<>b) OR (a IS NULL AND b IS NOT NULL) OR (a IS NOT NULL AND b IS NULL)`——**全部行（含单/双 NULL）二值、逐行精确**，满足验收不变式。裸三支式 `(a<>b) OR (a IS NULL AND b IS NOT NULL) OR (a IS NOT NULL AND b IS NULL)` 为**被拒候选/差异示例**：WHERE 精确但投影值在双 NULL 行为 NULL（原生 FALSE）——违反验收不变式，不得作为实现形态。
- `a IS NOT DISTINCT FROM b`（否定方向）≡ **主钉死公式 = 全二值 guard 两支式** `(a IS NULL AND b IS NULL) OR (a IS NOT NULL AND b IS NOT NULL AND a=b)`——**全部行二值、逐行精确**。裸两支式 `(a=b) OR (a IS NULL AND b IS NULL)` 为**被拒候选/差异示例**：WHERE 精确但投影值在单操作数 NULL 行为 NULL（原生 FALSE）——违反验收不变式，不得作为实现形态。
- **禁止形态（P1 静默错误：展开式产生 UNKNOWN → WHERE 丢行）**：① 对 `IS DISTINCT FROM` 用 `NOT((a=b) OR (a IS NULL AND b IS NULL))`（单操作数 NULL 行 `NOT(NULL)`=NULL，原生 TRUE 的行被丢）；② 对 `IS NOT DISTINCT FROM` 用三支式的 `NOT(...)` 包装（双 NULL 行 `NOT(NULL)`=NULL，原生 TRUE 的行被丢）。**验收不变式：任一方向的展开式在单 NULL 与双 NULL 两行都不得产生 UNKNOWN（WHERE 语义与原生逐行一致）。**
- ⚠️ **WI4 裁定文档 §二 的 provisional 展开式 `NOT ((a = b) OR (a IS NULL AND b IS NULL))` 属禁止形态 ①**——该文档已注明"方言展开形态按 WI8 裁定"，本 plan Phase 0 的钉死公式**取代**之；Phase 3 owner doc 同步该更正（并回链 WI4 文档留白处）。审查 Major-1 的"正向=WI4 模板"指派经 3VL 验算为方向互换：三支式恰是 `IS DISTINCT FROM` 的 WHERE 精确形态，两支式恰是 `IS NOT DISTINCT FROM` 的 WHERE 精确形态——本 plan 按验算结果绑定方向，两个禁止形态均已列明。

## Execution Plan

Phase 0 → Phase 1 → Phase 2 → Phase 3 顺序执行；Phase 1/2 各自的 `.g4` 改动单独提交；Phase 3 依赖 Phase 1/2 的规范 AST 形态。

### Phase 0 - 裁定：路径定价、方言矩阵取证、真值表、展开载体、负向载体与错误码（不改产品代码）

Status: completed（2026-10-04，裁定文档 `ai-dev/design/eql-cross-db-compat/2026-10-04-wi8-phase0-adjudications.md`；开工前置复核记录在其 §〇——plan 21 实测已 `completed`，代码 Phase 开工门已开）
Targets: `ai-dev/design/eql-cross-db-compat/`（新增裁定文档）、`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（只读取证）

> **硬前置复核**：本 Phase 开工前复核 plan 20（WI3，应已 `completed`）与 plan 21（WI4）状态并记录；**Phase 1 开工前再次复核且 plan 21 必须为 `completed`**。

- Item Types: `Decision`、`Proof`

- [x] **裁定每谓词路径定价**（Decision，按「执行摘要」框架逐行落结论）：`IS [NOT] UNKNOWN` 归一化 `SqlIsNullExpr`（直用自带 `not` prop，预期零争议）；`IS [NOT] DISTINCT FROM` 的 `SqlBinaryExpr(SAFE_EQ)` 归一化（`IS DISTINCT FROM` 方向 `SqlNotExpr` 包装）vs 新谓词节点——**默认 = 归一化**（`SqlOperator` 补 `SAFE_EQ` 成员的四处边界清单见 Current Baseline：优先级、`isCompareOp()`（`:51-53`）纳入与否、`getLeftSqlType`/`getRightSqlType`/`getResultSqlType` 显式落 `OTHER`（与 `EQ` 一致）、`reverse()`（`:119-136`）显式自反不落 null；`EqlASTOptimizer` 对新 operator 的遍历安全性确认）；`IS [NOT] TRUE/FALSE` 的构建期通用展开 vs 新 AST 节点（ⓒ）——默认 = **通用展开**（正确性优先、`ast/_gen/` 零 diff、放弃原生直出形态），ⓒ 案记录为被拒候选与重新评估触发条件。被拒备选与理由逐一落档。
- [x] **钉死 `IS [NOT] DISTINCT FROM` 正反两方向展开公式**（Decision，审查 Major-1；以 Current Baseline「Oracle 三节点展开公式钉死」小节为底稿，经 3VL 逐行验算后落档为唯一裁定基准）：正向（`IS DISTINCT FROM`）= 三支 null-safe 式、否定方向（`IS NOT DISTINCT FROM`）= 两支式（或各自的等价全二值 guard 式）；**两个禁止形态（naive NOT 包装 → UNKNOWN → WHERE 丢行 = P1）显式列明**并附验算行；验收不变式 = 单 NULL 与双 NULL 行均不得产生 UNKNOWN；WI4 裁定文档 provisional 式属禁止形态 ① 的更正与取代记录在案（该文档留白"方言展开形态按 WI8 裁定"）。
- [x] **裁定展开载体分工**（Decision）：**SAFE_EQ 渲染三分支 = 必选件、归属 Phase 1**（`AstToSqlGenerator` 覆写 `visitSqlBinaryExpr`/`printOperator`：native 方言 → `IS [NOT] DISTINCT FROM` 文本、mysql 族 → `<=>`、其余方言 → 响亮报错 `nop.err.eql.dialect-not-support-feature`；实测依据 = operator 渲染全局无方言分支，见 Current Baseline——不覆写则全方言输出 `<=>`）；**transformer 仅承担 Oracle 三节点展开（正反两方向按钉死公式）与按裁定的 `IS TRUE/FALSE` 补充展开**，放 `transformAfterAnalyze` 槽位；**方言来源钉死 = 与渲染侧同源的语句级 querySpace（主表源/写实体）→ `context.getDialectForQuerySpace(querySpace)`**（WI4 §二 2c 契约 + 参数保持性论证直接引用）；联邦（多 querySpace）场景显式登记（响亮报错或逐案裁定，不得静默取主表方言）。"生成器分支作为 Oracle 展开载体"记录为被拒候选与理由（渲染三分支必选 ≠ 展开载体选生成器）。**必须同时裁定"无 transformer 配置时的默认正确性"**：规范 AST 本身必须是全方言安全形态（展开式只含安全词表构造），或无 transformer 的矩阵不支持方言必须编译期/生成期响亮报错（SAFE_EQ 三分支的"其余"报错分支即兜底）——**不允许静默输出 `<=>` 或 `IS [NOT] DISTINCT FROM` 到不支持的方言**（排序原则 P1 + Minimum Rules #24）。
- [x] **裁定 `IS [NOT] TRUE/FALSE/UNKNOWN` 方言矩阵**（Decision）：逐方言一手文档取证（MySQL/PG/H2/DuckDB/MSSQL-2022/SQLite-3.39/Oracle/DB2/dm/tdengine），逐格标 `[实测]`/`[文档]`/`[未取证]`；**EQL 语义钉死为标准 SQL 严格三值**（真值表规格见上，含非布尔操作数行），MySQL 数值真值语义（非零即真）对非布尔操作数的分叉登记为约束 4「不等价」类；Oracle `IS TRUE/FALSE` 与 DB2 全部按 unverified 纪律处理（不计覆盖率、落地前须实测或显式报错）。
- [x] **裁定 `IS TRUE/FALSE` 操作数类型域与展开式词表**（Decision，审查 Major-3）：① 操作数类型域二选一并落档——仅布尔域（域外解析期/编译期响亮报错）或任意表达式（域外不报错、按钉死语义求值）；② **展开式钉死在安全词表内**（`=`/`<>`/`IS NULL`/`IS NOT NULL`/`AND`/`OR`/`NOT`——该词表已可表达全部六式严格语义，如 `a IS NOT TRUE ≡ NOT(a IS NOT NULL AND a=TRUE)`、`a IS TRUE ≡ a IS NOT NULL AND a=TRUE`），**禁止 truthy 裸条件形态**（如裸 `a`/`NOT a` 作布尔条件——否则 MySQL 上 `2 IS TRUE` 静默得 TRUE，与钉死语义不一致）；③ 每式展开文本按真值表（含非布尔操作数行）逐行验算后落档。
- [x] **裁定方言能力标志形态**（Decision）：「原生支持 `IS [NOT] DISTINCT FROM`/`IS [NOT] TRUE`」是否新增方言标志（四件套：`dialect.xdef` + `DialectFeatures` retention + `IDialect` accessor + 消费点 + 负向用例）vs 按 G-1 固定矩阵 + 版本门；两案的证据面与维护成本对比落档。若新增标志 ⇒ 矩阵快照棘轮同步责任落本 plan。
- [x] **裁定负向错误码口径与 EC ④ 载体**（Decision）：优先复用既有 `nop.err.eql.dialect-not-support-feature`（能力点登记表既有形态）；EC ④ 的 SQLite <3.39 负向用手构 `DialectModel`（手构方言先例 = `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlLateralSubqueryTableSource.java:78` 的 `buildDialect(boolean)`），不新建 sqlite 方言文件；版本门形态与 roadmap G-1 结论一致。
- [x] **裁定文法备选分支形态**（Decision）：`sqlExpr_primary` 新备选的 altLabel 命名（三约束逐条落实——label 前缀=目标 AST 类名、备选内 prop 赋值边界、同规则既有 label 全显式化）；`SqlBinaryExpr` 无 `not` prop 时 `NOT` 的消费方式；`IS DISTINCT FROM` 右操作数挂 `sqlExpr_predicate`（与 `:145` 比较右臂同级）的 ANTLR 歧义消解论证（`DISTINCT`/`TRUE`/`FALSE`/`UNKNOWN` 均为具名保留 token，不与 `IDENTIFIER_` 竞争——登记论证）。
- [x] **约束 4 用例清单落裁定文档**：「一致性」类（同一 EQL 在各支持方言语义一致）+「不等价」类至少四项：① MySQL 三值逻辑下三节点展开 vs `<=>` 的差异（单操作数 NULL 时 `NOT(展开)` 为 NULL vs `NOT(a<=>b)` 为 TRUE）；② MySQL 数值真值 vs 严格布尔（`2 IS TRUE` 为 TRUE vs `2=TRUE` 为 FALSE）；③ `IS NOT TRUE` NULL 行（展开式 vs 二元比较降级）；④ **禁止形态的负向对照**（审查 Major-1 改写）：实现正确时 `IS [NOT] DISTINCT FROM` 两方向的展开式在单 NULL/双 NULL 行均为**二值**（无 UNKNOWN、WHERE 不丢行）——用例断言实现产出的展开文本**不是**两个禁止形态（naive NOT 包装），naive 形态是静默错误而非"方言差异"，本项作负向守护、不作差异登记。每项给出用例形态（真库执行 / 文本断言 / owner doc 差异行）。
- [x] 复核 WI0 基线未回退：`ai-dev/audits/eql-wi0/eql-corpus-baseline.md`、`dialect-capability-matrix.json`、`TestEqlCorpusBaseline` 存在且可跑；复核两处漂移锚点现位（`EqlParser.java` 行数、owner doc 小节行号），结果记入裁定文档。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含：每谓词路径定价结论与被拒备选、展开载体分工（含无 transformer 默认正确性方案）、`IS [NOT] TRUE/FALSE/UNKNOWN` 逐方言矩阵（证据等级栏）、能力标志形态、负向错误码与 EC ④ 载体、文法备选形态与歧义论证、约束 4 用例清单、WI3/WI4 状态复核记录、漂移锚点复核记录。（`2026-10-04-wi8-phase0-adjudications.md` §一~§九 + §〇复核记录）
- [x] 本 Phase 纯裁定不改产品代码：`No owner-doc update required`（裁定文档本身即交付物；owner doc 覆盖表随代码 Phase 落）。
- [x] `ai-dev/logs/` 对应日期条目已更新。（`ai-dev/logs/2026/10-04.md` WI8 Phase 0 条目）

### Phase 1 - 文法：`IS [NOT] DISTINCT FROM` 解析与规范 AST（红→绿）

Status: completed（2026-10-04；开工前置复核=plan 21 实测 `completed`（当日 head 确认），记录于 daily log；红证据 `_tmp/wi8/red-probe.log` 8/8 parse-fail、绿 `_tmp/wi8/phase1-run4.log` 26/26 + 全模块 519|0）
Targets: `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（SAFE_EQ 渲染三分支覆写，必选件）、（按裁定）`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/enums/SqlOperator.java`、重生成产物、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`nop-orm-eql` 测试

- Item Types: `Feature`、`Proof`

- [x] **开工前置复核**：plan 21（WI4）`Plan Status` = `completed`——未完成则本 Phase 不得开工、保持 `planned` 并在 daily log 记录排队。（2026-10-04 实测 completed，roadmap WI4=done）
- [x] 修复前红：`a is not distinct from b` 与 `a is distinct from b` 解析失败——回归测试断言可解析且规范 AST 形态正确（按 Phase 0 裁定：如 `SqlBinaryExpr(operator=SAFE_EQ)` 左右操作数保序 + `SqlNotExpr` 包装），修复前红留痕于日志/提交说明。（`_tmp/wi8/red-probe.log`：8 形态全 `nop.err.antlr.common.parse-fail`（expected=NULL, offendingToken=distinct/true/false/unknown）；回归测试=`TestEqlNullSafeDistinctParse` 7 测）
- [x] 文法与归一化（按 Phase 0 裁定）：`sqlExpr_primary` 新增备选 + 手写 visit 完全重写（不调 super）；`.g4` 改动**单独提交**；重生成后 `git status` 中 `ast/_gen/` 零 diff；无 token 删除（`Symbol.g4:38` `SAFE_EQ_` 原样保留）。（commit `777e2ec494` 文法组：EqlParser 11558→11611、visitor 1936→1939、ast/_gen 78 文件零 diff、Eql.tokens 零 diff；⚠️ 执行期机制发现：codegen prop 校验只对带 label 元素做 ast.define 映射——操作数用无 label 规则引用（SqlGroupByItem_rollup 先例），裁定文档 §九已记）
- [x] **SAFE_EQ 渲染三分支落地**（审查 Major-2，必选件）：`AstToSqlGenerator` 覆写 `visitSqlBinaryExpr`/`printOperator`，对 operator=`SAFE_EQ` 按方言三分支渲染——native 方言（PG/H2/DB2/DuckDB/MSSQL-2022/SQLite-3.39）→ `IS [NOT] DISTINCT FROM` 文本（`IS DISTINCT FROM` 方向按钉死式直出方向文本、不得输出 `not (a is not distinct from b)` 包装）；mysql 族 → `<=>`；**其余方言 → 响亮报错 `nop.err.eql.dialect-not-support-feature`**（无 transformer 时 oracle 亦落此分支 = 响亮，不静默）；回显路径不受影响的守护用例（`toSqlString()` 仍为归一化形态）。（落地=覆写 `visitSqlBinaryExpr`/`visitSqlNotExpr` + `printSafeEq`；native 集合按 Phase 0 裁定 §三=pg 系/h2 系/duckdb/mssql，db2/dm/tdengine/sqlite 落报错分支（unverified 纪律）；`SqlNotExpr(SAFE_EQ)` 识别使方向文本直出）
- [x] 正向用例（roadmap EC ①的文本面 + EC ②的基础，**依赖上一项覆写方可达**）：h2 上断言 `IS NOT DISTINCT FROM` 与 `IS DISTINCT FROM` 两个方向的原生文本输出（方向文本正确、无 NOT 包装）；mysql 上断言 `<=>` 映射文本（`a IS NOT DISTINCT FROM b` → 含 `a <=> b` 的输出、NOT 变体的括号/取反形态按裁定断言）。（`TestEqlNullSafeDistinctDialect`：h2/postgresql/duckdb/mssql 双方向 8 测 + mysql/mysql5.7/mariadb 双方向 6 测）
- [x] 守护用例：既有 `IS [NOT] NULL`（`SqlIsNullExpr` 路径）解析/渲染/回显逐字不变；`comparisonOperator_` 六成员行为不变。（`testExistingIsNullBehaviorUnchanged` + `testSafeEqBindsTighterThanOr`（EQ 对照）+ 全模块 519|0 回归）
- [x] 回显路径断言：`toSqlString()` 输出规范 AST 的确定形态（归一化回显如 `a <=> b` 属预期，登记进三态断言「输入→规范回显→方言输出」）；7 个语料文件 0 命中已证（Current Baseline），`TestEqlCorpusBaseline` 实测兜底。（`testEchoCanonicalForm`/`testCanonicalEchoIndependentOfDialect` 断言 `a <=> b`/`not ( a <=> b )`；CorpusBaseline 1/1）
- [x] 接线验证（Minimum Rules #23）：parse → 构建期归一化 → （无 transformer）方言渲染的运行时连通测试，一条用例覆盖全链。（`testH2NativeTextFullChain`：EqlCompiler 全链 h2 `u.SID is not distinct from u.NAME`）
- [x] `./mvnw test -pl :nop-orm-eql -am -T 1C` 全绿；owner doc 小节骨架（`IS [NOT] DISTINCT FROM` 覆盖表，`[未取证]` 标注）已落。（519|0 BUILD SUCCESS；owner doc 新增「NULL 三值语义谓词」小节 + 登记表行，db2/sqlite `[未取证]` 标注）

Exit Criteria:

- [x] 红→绿回归测试断言具体规范 AST 形态与 h2/mysql 具体方言 SQL 文本；修复前红留痕。（`_tmp/wi8/red-probe.log` + `TestEqlNullSafeDistinctParse` 7 测 + `TestEqlNullSafeDistinctDialect` 19 测）
- [x] SAFE_EQ 渲染三分支用例通过：h2 双方向原生文本、mysql `<=>`、其余方言（无 transformer，如手构模型）断言 `nop.err.eql.dialect-not-support-feature` 响亮报错——全仓不存在向不支持方言静默输出 `<=>` 的路径。（`testUnsupportedConstructedDialectFailsLoud`（手构方言，断言 feature=`is[not] distinct from`）+ `testOracleWithoutTransformerFailsLoud`）
- [x] `.g4` 单独提交；`ast/_gen/` 零 diff；`SAFE_EQ_` token 未删（`git diff Symbol.g4` 为空）。（commit `777e2ec494` 文法组 / 逻辑组随本提交；`git status` `ast/` 零 diff、`Symbol.g4` 未触碰）
- [x] 既有 `IS NULL` / 比较算子行为逐字不变的守护用例通过；`TestEqlCorpusBaseline` 通过。（1/1）
- [x] 接线验证用例通过；owner doc 覆盖表已落档；`ai-dev/logs/` 对应日期条目已更新。（`testH2NativeTextFullChain` + owner doc「NULL 三值语义谓词」小节 + daily log）

### Phase 2 - 文法：`IS [NOT] TRUE/FALSE/UNKNOWN` 解析与规范 AST（真值表驱动，红→绿）

Status: planned
Targets: 同 Phase 1 的文法/归一化/文档/测试面

- Item Types: `Feature`、`Proof`

- [ ] **开工前置复核**：plan 21（WI4）`Plan Status` = `completed`（同 Phase 1）。
- [ ] 修复前红：`a is not true`/`a is true`/`a is false`/`a is unknown`（含 NOT 变体）解析失败——回归测试断言可解析，修复前红留痕。
- [ ] 文法与归一化（按 Phase 0 裁定）：默认 = 构建期通用展开进既有节点，**逐谓词展开式必须满足 Current Baseline 真值表逐行等价且钉死在安全词表内**（禁止 truthy 裸条件形态，真值表即验收规格）；`IS [NOT] UNKNOWN` 归一化既有 `SqlIsNullExpr`（直用自带 `not` prop，预期精确等价）；`.g4` 改动**单独提交**（与 Phase 1 分开）；`ast/_gen/` 零 diff。
- [ ] **NULL 语义逐案用例（roadmap EC ③，不得复用 DISTINCT FROM 展开模板）**：真值表 6 谓词 × 4 操作数行（TRUE/FALSE/NULL/**非布尔操作数 `2`**——审查 Major-3 追加行，各谓词期望值按钉死语义断言，MySQL 原生 truthy 分叉登记）逐行断言 lowering 文本或（H2 真库可执行时）执行结果；**错误降级负向对照**：断言 `IS NOT TRUE` 的展开**不是** `a<>0`/`NOT a`/truthy 裸条件形态（NULL 行语义守护，roadmap 警告的回归锁）。
- [ ] 正向用例断言具体方言 SQL 文本：h2 展开/直出形态各一条；按 Phase 0 矩阵对原生方言（如 mysql `IS NOT TRUE` 原生算子）至少一条直出或展开对照断言。
- [ ] 接线验证（#23）+ 「不等价」登记（约束 4 ②③项：MySQL 数值真值分叉、`IS NOT TRUE` NULL 行——用例或 owner doc 差异行）。
- [ ] owner doc：`IS [NOT] TRUE/FALSE/UNKNOWN` 逐方言覆盖表（含 Phase 0 矩阵与 `[未取证]` 标注）；真值表规格一并落档。
- [ ] 语料 BC（roadmap EC ⑥ 基础）：`TestEqlCorpusBaseline` 通过；`./mvnw test -pl :nop-orm-eql -am -T 1C` 全绿；`.g4` 单独提交、`ast/_gen/` 零 diff；`ai-dev/logs/` 已更新。

Exit Criteria:

- [ ] 红→绿回归测试：六种形态可解析；真值表 6×4 逐案断言通过（含非布尔操作数行）；错误降级负向对照通过。
- [ ] `IS [NOT] UNKNOWN` 复用 `IS [NOT] NULL` 渲染的等价断言通过（零新渲染路径）。
- [ ] `.g4` 单独提交；`ast/_gen/` 零 diff；无 token 删除。
- [ ] `TestEqlCorpusBaseline` 通过；owner doc 覆盖表 + 真值表已落档；`ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - lowering：WI4 管线消费者（方言展开/映射）与全方言用例矩阵

Status: planned
Targets: `nop-persistence/nop-orm-eql`（生产 `IEqlAstTransformer` 实现 + 按裁定的生成器渲染分支）、`nop-persistence/nop-orm`（配置注入测试）、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、全量回归

- Item Types: `Feature`、`Proof`

- [ ] **开工前置复核**：plan 21（WI4）`Plan Status` = `completed`（同 Phase 1）。
- [ ] 生产 `IEqlAstTransformer` 落地（按 Phase 0 裁定；**职责边界 = 仅方言展开，`<=>`/native 文本渲染归 Phase 1 的生成器覆写**）：Oracle `IS [NOT] DISTINCT FROM` 三节点展开——正反两方向按 Phase 0 钉死公式（正向三支式 / 否定方向两支式或等价全二值式，**禁止两个 naive NOT 包装形态**），展开式只重排/包装既有操作数 + 新增对同一操作数的 null 谓词（参数保持性论证引用 WI4 §二 2c）+ `IS NOT TRUE/FALSE` 在无原生支持方言上的补充展开（若 Phase 2 通用展开已覆盖则本项按裁定收敛）+ 原生方言直通不改写。放 `transformAfterAnalyze` 槽位，方言来源 = 与渲染侧同源的语句级 querySpace（主表源/写实体）→ `getDialectForQuerySpace`；联邦（多 querySpace）场景按 Phase 0 登记处置（响亮报错或逐案裁定，不静默）。
- [ ] **默认注入链落地**：transformer 经 `SessionFactoryConfig.eqlAstTransformer` 配置（beans.xml 显式 `<bean>`，Nop IoC 规则）；**禁止 per-call 传入路径**（WI4 契约）；缓存绕过保护自动生效——回归断言：默认注入的 transformer 编译结果**参与**计划缓存且同键可复用（与 `TestEqlAstTransformerWiring` 的非默认绕过案例互为对照）。
- [ ] **接线验证（Minimum Rules #23，roadmap WI8 对 WI4 的存在理由）**：端到端测试（#22）——factory 默认配置注入 → 不带 transformer 参数的编译入口 → 生成 SQL 含方言展开/映射结果（断言具体文本）+ 运行时调用断言（计数器/标志）；无 transformer 配置的对照路径断言输出规范安全形态（全方言可执行）且**不**含不支持方言的裸语法（无静默跳过，#24）。
- [ ] **roadmap EC ①（不等价类）**：MySQL 三值逻辑下三节点展开 vs `<=>` 的差异用例落地（单操作数 NULL 时 `NOT(展开)` = NULL vs `NOT(a<=>b)` = TRUE；H2 真库可执行则执行断言，否则文本 + 真值表推导断言）。
- [ ] **roadmap EC ②（Oracle 展开文本，按审查 Major-1 强化）**：`IS DISTINCT FROM` 与 `IS NOT DISTINCT FROM` **正反两方向**的 Oracle 三节点展开正确 SQL 文本用例，**按 Phase 0 钉死公式逐字断言**；并断言两方向在**单操作数 NULL 与双 NULL 行的展开式均为二值**（禁止形态负向对照：展开文本不属 naive NOT 包装形态、语义不产生 WHERE 丢行）；`IS [NOT] TRUE/FALSE` 的 Oracle 形态按 unverified 纪律：展开或显式报错（按 Phase 0 裁定，不允许既无展开也无报错的第三态）。
- [ ] **roadmap EC ④（版本门负向）**：SQLite <3.39（手构 `DialectModel` 载体）编译期响亮报错，断言具体错误码；连同 Phase 0 矩阵中「判不支持」的其余方言负向用例（每格一个）。
- [ ] 三份必测语料 BC（roadmap EC ⑥）：`:nop-job-dao`(NopJobTask)、`:nop-auth-service`(test)、`:nop-orm`(test) 与 WI0 基线快照逐字一致；**不声称"7 个全测"**（`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份由 roadmap 级 EC 第 8 条 + WI0 静态快照覆盖）。
- [ ] owner doc 收口：NULL 谓词小节完整版（逐方言覆盖表 + 能力点登记表行：generator/transformer 方法 + 触发标志 + 不支持时错误码 + 对应负向测试名）+ `IS NOT TRUE/FALSE` 禁止降级警告 + 三节点展开 vs `<=>` 差异说明；`docs-for-ai/INDEX.md` 与 `04-reference/source-anchors.md` 若路由/锚点变化同步。
- [ ] `./mvnw test -pl :nop-orm-eql,:nop-orm -am -T 1C` 全绿；`ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [ ] 端到端（#22）+ 接线（#23）测试通过：默认注入 → 无参编译入口 → 方言展开/映射文本 + 运行时调用断言；无 transformer 对照路径安全形态断言通过。
- [ ] roadmap EC ①②③④ 用例全部落地且断言具体文本/错误码（①不等价差异、②Oracle 展开文本、③TRUE/FALSE NULL 逐案、④SQLite 版本门报错）。
- [ ] 缓存行为回归：默认注入参与缓存、非默认绕过缓存（既有 `TestEqlAstTransformerWiring` 全绿不回退）。
- [ ] 三份必测语料 BC 逐字一致；未声称"7 个全测"。
- [ ] owner doc 小节 + 能力点登记表行 + INDEX/source-anchors 裁定已落；`ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [ ] 六种谓词形态（`IS [NOT] DISTINCT FROM`、`IS [NOT] TRUE/FALSE/UNKNOWN` 及其 NOT 变体）全部落地：各有断言正确方言 SQL 文本的红→绿正向用例（roadmap WI8 EC ①②③ 的正向面）。
- [ ] **不等价类用例（EC ① + 约束 4）**：三节点展开 vs `<=>` 在 MySQL 三值逻辑下的差异用例、MySQL 数值真值分叉、`IS NOT TRUE` NULL 行——三项齐备（用例或 owner doc 差异行）；**外加禁止形态负向对照（审查 Major-1）**：`IS [NOT] DISTINCT FROM` 两方向展开式在单 NULL/双 NULL 行均为二值、实现文本不属 naive NOT 包装禁止形态（该项是负向守护，不作为"差异"登记）。
- [ ] **`IS NOT TRUE`/`IS NOT FALSE` NULL 语义逐案用例（EC ③）**：真值表 6×4 逐行覆盖（含非布尔操作数行），含"不得降级二元比较/truthy 裸条件"的负向对照；未复用 `IS DISTINCT FROM` 展开模板。
- [ ] **Oracle 展开文本用例（EC ②）** 与 **SQLite <3.39 编译期报错负向用例（EC ④，受 G-1）** 落地；unverified 格（Oracle `IS TRUE/FALSE`、DB2、dm、tdengine）未计入覆盖率且已标注。
- [ ] 生成物纪律：Phase 1/2 的 `.g4` 改动各自单独提交（`git log` 文法提交与逻辑提交分离可审计）；`ast/_gen/` 零 diff（若 Phase 0 未改裁 ⓒ 案）；无任何 lexer token 删除；**`Symbol.g4:38` `SAFE_EQ_` 保留**（WI8 MySQL lowering 目标红线）。
- [ ] WI4 管线消费契约成立：生产 transformer 经 `SessionFactoryConfig.eqlAstTransformer` 默认注入、走 `transformAfterAnalyze` 槽位 + 已解析表源方言推导；默认注入参与缓存、非默认绕过缓存两向回归全绿；未 per-call 传入（WI4 旧参数使用边界未破坏）。
- [ ] 无 transformer 配置的默认路径全方言安全（规范形态可执行或响亮报错），无静默输出不支持方言裸语法的路径（Minimum Rules #24 + roadmap 排序原则 P1）。
- [ ] 既有 EQL 语料 BC（EC ⑥）：三份必测与 WI0 基线快照逐字一致；未声称"7 个全测"。
- [ ] owner doc `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` NULL 谓词小节已同步 live baseline（逐方言覆盖表 + 证据等级 + 能力点登记表行 + 禁止降级警告）；`docs-for-ai/INDEX.md` 与 `04-reference/source-anchors.md` 已按需更新（或明确记录 `No update required` 及理由）。
- [ ] `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-dao,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（roadmap WI8 EC ⑤ 命令 + 审查 Minor-⑤ 显式补 `:nop-dao`——Phase 0 若裁定新增方言标志/retention 类则该模块必测，未裁定新增时其作为 dialect.xml 消费方亦须保持绿）——**"全绿"口径**：本 WI 相关模块全绿 + `nop-stream-runtime` 的 **2 项既有失败**（`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`、`TestMessageSourceFunctionRestart`，均单跑 PASS、与本 roadmap 零代码关联）与基线一致即豁免，不得据此判本 WI 失败。
- [ ] **四门禁**：① `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/27-eql-wi8-null-safe-predicates.md --strict` 退出码 0；② `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high`（及 `--module nop-orm`，若 transformer 落 nop-orm 侧）零命中（anti-hollow："只解析不 lowering/只打印不设门/展开式静默丢弃"正是半实现形态）；③ `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0（既有基线 3 项 warning：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，不顺手修、只比对新增量）；④ 方言能力矩阵快照：若 Phase 0 裁定新增方言标志/取值 → `ai-dev/audits/eql-wi0/dialect-capability-matrix.json` 增量已同步且 `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0，否则核实零增量 + exit 0。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）从 EQL 文本 → parse 归一化 → transformer/生成器方言 lowering → SQL 输出/报错的端到端调用链真实连通（运行时断言证据，不只是文法分支存在）；（b）无空方法体/静默跳过/no-op（lowering 不可用时响亮报错，不允许回退原文或静默丢谓词）；（c）真值表语义与实现一致（逐案抽查 `IS NOT TRUE` NULL 行 **+ `IS [NOT] DISTINCT FROM` 单操作数 NULL 行与双 NULL 行均为二值、展开非禁止形态**——审查 Major-1 增补）。
- [ ] **独立 closure audit 已完成并由不同 `task_id` 的子 agent 执行**，evidence 写入本 plan `Closure` 段（guide #26/#27；roadmap WI8 EC ⑦）；roadmap Work Item Status 的 WI8 行据此才可 `todo` → `planned`（draft review 通过时）→ `done`（audit 通过后，见 roadmap Status values）。

## Deferred But Adjudicated

### `a <=> b` 作为输入语法（解锁 `BaseRule.g4:144` 注释态备选）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap WI8 清单只有六种 `IS` 谓词形态，未要求 `<=>` 输入解析；`SAFE_EQ_` token 保留红线已满足（MySQL lowering 输出可词法化）。解锁会新增第二可解析入口（双入口回显漂移风险，同 WI5 对单参 `trim` 的拒绝先例）。
- Successor Required: `no`（若出现真实需求，先登记 roadmap 再立项）。
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（新增登记行）。

### Oracle `IS TRUE`/`IS FALSE` 与 DB2 `DISTINCT` 谓词的一手取证

- Classification: `watch-only residual`
- Why Not Blocking Closure: G-1 裁决明确 unverified 边界维持 unverified 纪律、不计入覆盖率（红线"结论只能按已登记的证据等级书写"）；本 plan 按矩阵对这些格显式报错或展开，不产生静默错误 SQL。
- Successor Required: `no`（取得一手引证后按 roadmap 变更登记更新矩阵与 owner doc）。
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI8 节 + G-1 边界登记。

### 原生方言 `IS [NOT] TRUE/FALSE` 直接输出形态（若 Phase 0 裁定构建期通用展开案）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 通用展开案在原生方言上输出语义等价的展开文本（真值表逐行等价是验收规格），仅损失"原生算子直出"的形态偏好；保身份的唯一路径 ⓒ（新 AST 节点）成本为 `EqlASTKind` ordinal 重编号 + 77 `_gen` + parser 10927 行重生成，收益不成比例（roadmap 约束 1 定价）。
- Successor Required: `no`（重新评估触发条件 = 出现"必须输出原生 `IS TRUE` 文本"的真实消费场景）。
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（新登记行，注明 ⓒ 全量定价）。

## Non-Blocking Follow-ups

- `db2`/`sqlite` 方言文件的可加载性与 SQLite 版本门落地面：本 plan EC ④ 用手构模型承载；若后续 roadmap 引入真实 sqlite/db2 方言文件，负向用例可迁移至真实方言载体（登记迁移点，不阻塞）。
- `EqlASTOptimizer`/`EqlASTProcessor` 对归一化产物（`SqlBinaryExpr(operator=SAFE_EQ)`、展开式）的优化遍历行为快照（若 Phase 0 确认存在非常规路径），随 owner doc 落档。
- CI 真库验证面扩展（mysql/oracle docker 化）超出本 roadmap；若立项，`[未取证]` 格在其上转 `[实测]`。

## Closure

Status Note: （完成或关闭时填写：为什么这个 plan 可以关闭）
Completed: （YYYY-MM-DD）

Closure Audit Evidence:

- Reviewer / Agent: （独立审阅者或独立子 agent，须与执行者不同 task_id）
- Audit Session: （session/task ID）
- Evidence:
  - （每条 Exit Criterion 与 Closure Gate 的验证结果 PASS/FAIL + live code path / test name）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/27-eql-wi8-null-safe-predicates.md --strict` 退出码
  - Anti-Hollow 检查结果（端到端调用链追踪 + `scan-hollow-implementations.mjs` 退出码）
  - Deferred 项分类检查（确认无 in-scope live defect 被降级）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
