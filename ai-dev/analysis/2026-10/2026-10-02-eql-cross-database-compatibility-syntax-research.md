# EQL 语法跨数据库兼容性调研报告（v3）

> Status: open
> Date: 2026-10-02
> Updated: 2026-10-02（第 3 版：经两轮、共 6 个独立子 agent 交叉审查后重写）
> Scope: `nop-persistence/nop-orm-eql`（EQL ANTLR 语法 + AST + SQL 生成器）、`nop-persistence/nop-dao`（Dialect 层）、`nop-persistence/nop-orm`（DAO 侧生成器）、`nop-metadata-service`（EQL AST 外部消费方）
> Conclusion: EQL 当前是 **SQL-92 子集 + 实体属性导航**。**首先不是补语法，而是修 6 个在执行路径上静默产出错误 SQL 的缺陷**（`ESCAPE` / `LATERAL` / `for update` / `cross join` 被误解析 / `INTERVAL` 跨库非法 / 集合下标访问产出垃圾 AST）与 **7 个方言配置错误**。其次才是**接线 14 个「已预留但零消费」的扩展点**（AST 字段、方言特性标志、函数模板）。UPSERT 不建议进语法层，应做成 DAO 原语。跨库兼容性的最大风险**不在语法**而在语义层（collation 大小写敏感性改变**返回行数**、NULL 序分 4 类、Oracle `''`=NULL、`IN` 列表长度上限），这些**无法靠补语法解决**。

---

## Context

- **要回答的问题**：EQL 还缺哪些 SQL 语法特性？哪些**能且应该**为跨数据库兼容而补进 EQL？补进去之后由谁负责归一化到各数据库方言？
- **涉及模块**：`nop-orm-eql`（语法 + AST + 两个生成器）、`nop-dao`（16 个方言 XML）、`nop-orm`（并行的第二条 SQL 生成路径 `GenSqlHelper`）、`nop-metadata-service`（直接消费 `EqlASTParser`，绕过 transform）。
- **约束**：
  - EQL 定位是"标准 SQL + 实体属性导航"，**不是方言超集**。收进方言私有语法会让它退化成方言门面，`EqlTransformVisitor` 的可移植性收益归零。
  - 判断标准：**该语法在 SQL 标准中定义，或在 ≥5 个**真实覆写点**的方言中语义等价原生支持，且可无损 lowering 到 EQL 现有机制**（注意按**覆写点**计数而非方言名 —— `db2` 无 `<functions>`、mariadb/mysql5.7/duckdb/postgis/h2gis/oracle-reverse 都是继承别名）。
  - 生成目录（`_gen/`、`_*.java`）不可手改；改动必须落在 `model/antlr/*.g4`、`model/ast/.../EqlAST.xjava`、dialect XML 与非下划线 Java 源文件上。
  - 本报告为 AI 单方调研，结论**可被推翻**；落地应拆到 `ai-dev/plans/`。

---

## 修订记录（v2 → v3）

v2 经第二轮 3 个独立子 agent（代码事实 / 跨库矩阵 / 对抗性一致性）审查，发现约 58 项问题。以下为**已逐条复核并采纳**的修正：

| # | v2 的问题 | v3 的修正 | 复核依据 |
|---|-----------|-----------|---------|
| 1 | BUG-10 归入"解析失败（用户有明确信号）"，称 `cross join` 实测 FAIL | **归类错误且证据为假**。实测 `select count(*) from a cross join b` → `select count(*) from a as cross join b` —— **无错误信号，静默产出非法 SQL**。已升为 A 类并单列 | 实测 |
| 2 | 未发现 `o.roles[1].x()` 静默产出非法 SQL | **新增 A 类缺陷 BUG-14**：`select o.roles[1].x()` 回显为 `select o.roles 1 from t` | 实测 |
| 3 | Conclusion 称"3 个执行路径缺陷"，A 类实含 5 项 | 已改为 **4 个执行路径缺陷**（`ESCAPE`/`LATERAL`/`for update`/`cross join`）+ 1 个回显缺陷；A 类标题不再把 BUG-4 算作执行路径 | 内部一致性 |
| 4 | 关键宇遮蔽词数 **199** | 实测 **224**（`Keyword.g4` 136 + `SQL92Keyword.g4` 165 = 301 token，减去 `unreservedWord_` 的 **77**） | 代码实测 |
| 5 | `SQL92Keyword.g4` "约 79 个（≈47%）零引用" | 实测 **86 个（≈52%）**；"165 token"与"63 个仅供标识符"正确 | 代码实测 |
| 6 | 遮蔽词示例含 `row`、`ntile` | **错误**：`ROW`/`NTILE` 都不是词法 token，实测 `row(a)`、`ntile(a)` 均可解析。`ntile(4) over(order by x)` 失败的真正原因是**缺 `PARTITION BY`**。而 `substring(a)`、`trim(a)` 也能解析（`sqlIdentifier_func_` 已白名单） | 实测 |
| 7 | §1.2 "函数覆写数" 列 | 15 行中 **6 行错**。实测：mysql **2**（原 21）、postgresql **8**（原 14）、duckdb **35**（原 30）、h2 **5**（原 10）、dm **2**（原 0）、tdengine **2**（原 0） | 代码实测 |
| 8 | `unreservedWord_` ∩ 各方言保留字 | **DuckDB 26 正确**（`duckdb.dialect.xml:144` 有 `x:override="replace"` 的 489 词表）；实测交集：DuckDB 26、H2/h2gis 10、MSSQL 9、MariaDB 8、MySQL/mysql5.7/PG/postgis/Oracle/DM/TDengine 各 7。**`default`/`db2` 的 `<reservedKeywords>` 为空（0 词）** | 代码实测 |
| 9 | `supportNullsFirst` 配置在 3 个 XML | 实测 **4 个**（新增 `duckdb.dialect.xml:21`），且 **duckdb 自相矛盾**：`:9 defaultNullsFirst="false"` 与 `:21 supportNullsFirst="true"` 描述同一事实、取值相反 | 代码实测 |
| 10 | §1.3 表中 `lateral` "谁读 = 无人"，与 BUG-2 自述 `EqlTransformVisitor` 读它矛盾 | 已改为"`EqlTransformVisitor:486` 读（语义 scope）；两个生成器均不读 → 关键字丢失" | 内部一致性 |
| 11 | §1.2 说"H2 另有 `dateadd`/`datediff`"，G8 说 `dateDiff` "全部不存在" | 已统一：函数查找是 `CaseInsensitiveMap`（`DialectImpl.java:75`），**`dateDiff` 在 H2 上可解析**，但仅 H2 有、未做跨方言归一 | 代码实测 |
| 12 | G11 说"`Symbol.g4` 无 `:` token" | **错**：`COLON_ : ':'` 在 `Symbol.g4:30` 存在，只是零引用。措辞已改为"存在但未被任何规则引用" | 代码实测 |
| 13 | Part 6 把 `CollectionOperatorTransformer` 当作 `IEqlAstTransformer` 的"唯一先例" | **严重设计缺陷**：该类是 `public class CollectionOperatorTransformer`，**并不实现** `IEqlAstTransformer`；`IEqlAstTransformer` **零生产实现者**（唯一实现是 `TestEqlCompiler.java:76` 的测试桩），且是**按调用点 opt-in** 传入。已改写路径 ⓓ 的可行性判断与 S-9 的做法 | 代码实测 |
| 14 | Part 5 否决 `OVERLAY`，S-11 却安排 `OVERLAY` | **直接矛盾**。已从 S-11 移除 | 内部一致性 |
| 15 | BUG-11 同时归 S-3（风险低）与 S-10（高 / plan-first） | **双重归属 + 风险自相矛盾**。已把 BUG-11 整体移出 S-3，只留 S-10（plan-first） | 内部一致性 |
| 16 | BUG-13、G11、G8 的 `lastDay` 无批次归属 | 已新增 S-14 收拢 BUG-13 的死标志处置、G11 的 `IN` 列表长度上限、`lastDay`，并把 G11 的 `IN` 上限从 P2 提到 **P0** | 内部一致性 |
| 17 | S-6 的前置只写 S-1，但注册 `day`/`hour`/`quarter` 需先改 `unreservedWord_`（那是 S-5 的工作） | 前置链已修正为 S-3 → S-5 → S-6 | 内部一致性 |
| 18 | 矩阵：`INTERSECT`/`EXCEPT` 在 MySQL/MariaDB 标 ❌ | **错**：MySQL **8.0.31+** 支持；MariaDB **10.3+** 支持。Part 4.1 结论 #1、S-4 工作项、Open Question 相应重写 | 官方文档 |
| 19 | 矩阵：`INTERSECT`/`EXCEPT` 在 MSSQL 标 "2022+；更早 ❌" | **错**：SQL Server **2005 起**就有（compat 90），无版本门 | 官方文档 |
| 20 | G8 / §4.3 把 `GREATEST`/`LEAST` 称为"8/8 库原生、**零方言分歧**、性价比最高单项" | **错**：SQLite **永无**该函数（只有 `min(a,b,…)`/`max(a,b,…)` 标量形式）；MSSQL 仅 **2022+**；且 **NULL 语义分 3 类**（PG/MSSQL 忽略 NULL，Oracle/MySQL/MariaDB 任一 NULL 即 NULL）。已降级并改为"需 NULL 策略决策" | 官方文档 |
| 21 | 矩阵：PG `LISTAGG` = ✅ | **错**：上游 PostgreSQL **没有 `LISTAGG`**，是 `string_agg`（9.0+）。`LISTAGG` 只存在于 Oracle 兼容分支（openGauss / EDB PGAS） | 官方文档 |
| 22 | 矩阵：DB2 正则 = ⭕ 无正则 | **错**：DB2 LUW **11.1+ 内置 `REGEXP_LIKE`**。MSSQL 则是 2025+ 才原生（`REGEXP_LIKE` 等需 compat 170）。SQLite **永无内置正则**（`REGEXP` 是未定义 UDF 的语法糖） | 官方文档 |
| 23 | 矩阵：`<=>` 在 DB2 / DuckDB / SQLite 标 ✅ | **错**：`<=>` **只存在于 MySQL(3.23+) 与 MariaDB**。PG/H2/DB2/DuckDB/MSSQL-2022/SQLite-3.39 都用原生 `IS [NOT] DISTINCT FROM`。G3-1 的 lowering 目标因此只有 2 个方言 | 官方文档 |
| 24 | 矩阵：SQLite `WINDOW w AS(..)` = ❌ | **错**：SQLite **3.25+** 支持命名窗口（3.28+ 支持窗口链接） | 官方文档 |
| 25 | 矩阵：SQLite `AT TIME ZONE` = ✅ | **错**：SQLite **永无**该运算（时区走 `datetime()` 修饰符） | 官方文档 |
| 26 | 矩阵：MSSQL `TRIM(BOTH c FROM x)` = ⭕ | 部分错：MSSQL **2022+ 且 compat 160** 支持完整三段式；2017+ 仅两段式；≤2016 无 `TRIM` | 官方文档 |
| 27 | 矩阵：MySQL `GROUP BY CUBE`/`GROUPING SETS`/`GROUPING()` = ❌ | 部分错：MySQL **8.4.3+** 支持 `GROUP BY ROLLUP(a,b)` 等函数式写法；8.0.12–8.4.2 只有 `WITH ROLLUP` 修饰符 | 官方文档 |
| 28 | 矩阵：MSSQL `\|\|` "依赖 `CONCAT_NULL_YIELDS_NULL`" | **错**：T-SQL 里 `\|\|` **根本不是运算符**（连接是 `+`），而 `CONCAT_NULL_YIELDS_NULL` **自 2017 起恒为 ON** | 官方文档 |
| 29 | 矩阵：MSSQL `IS DISTINCT FROM` "2022+（compat 门？）" | 已精确为 **2022 引擎门，无 compat 门**（与 `TRIM`/`REGEXP` 不同） | 官方文档 |
| 30 | 矩阵：DB2 `MERGE` "三分支全" | 实为 **四臂 + `SIGNAL`**（`ELSE IGNORE` 需 11.1+），比 v2 描述更强，进一步支持 G6 的否决理由 | 官方文档 |
| 31 | §4.2 类别 C（DuckDB）表述为不变式 | 已加注：DuckDB 的 NULL 序是**可配置项** `default_null_order`（4 个取值），0.8 起默认 `NULLS_LAST`，但 `SET default_null_order=…` 可改 —— 这也意味着 EQL 显式写 NULL 序时仍需 pin 该设置 | 官方文档 |
| 32 | 矩阵 DM8 列大量 `✅⚠️`，与"覆盖数"混算 | 已统一为 `⚠️ unverified` 并注明**不得计入覆盖率**（DM8 无公开可引用一手文档；TDengine 同） | 官方文档核查 |
| 33 | §1.1 表 DML 行的 `RETURNING` 单元格未转义其中的竖线 → 表格渲染成 4 列而表头是 3 列 | 已转义 | 文档卫生 |
| 34 | Part 4 `\|\|` 行缺 SQLite 格（10 列 vs 11 列表头） | 已补齐 | 文档卫生 |
| 35 | 两处 UTF-8 替换字符（正文两处中文词被破坏成 U+FFFD） | 已修复 | 文档卫生 |
| 36 | `## 修订记录`（H2）出现在 `# Part 1`（H1）之前，标题层级断裂 | 已把 v1→v2 的对照记录降为 H3（`### 附：…`）并嵌套在 v2→v3 记录之下（仍在正文前，但层级已正确） | 文档卫生 |
| 37 | 缺 `00-analysis-writing-guide.md` 要求的 Context 节与调研目标 | 已补 `## Context` | 写作规范 |
| 38 | 声称"每条结论标注 `[实测]`/`[代码]`/`[文档]`/`[推断]`"，实际全文仅 34 处标签，`[推断]` 从未用于任何结论 | 已改为**分级统计 + 分节标注**，并明确"Part 4 矩阵全为 `[文档]` 级、未在真实引擎执行" | 内部一致性 |
| 39 | 8 个批次无测试预算（违反自己刚采纳的 AGENTS.md 规则） | 已为每个批次补测试目标（含 Part 10 点名的两个优先级专项套件） | 内部一致性 |
| 40 | 行数统计混用"总行数"与 `wc -l` 两套口径（差 1） | 已统一为"`wc -l` 口径"并在折算基准声明 | 文档卫生 |
| 41 | `EqlASTKind.java` 被描述为"含 `switch(EqlASTKind)`" | **错**：它是纯枚举（130 行、无 switch）。只有 `EqlASTVisitor/Processor/Optimizer` 三个文件含 switch | 代码实测 |
| 42 | `TestEqlTranslationSemantics` 的 javadoc/`buildDialect` 行号引用落空 | javadoc 实际 `:46-51`（论断在 `:49`），`buildDialect()` 实际 `:83-96` | 代码实测 |
| 43 | 若干 XML 行号偏差（`jsonTypeHandler` `:15`→`:13`、`avg`/`sum` returnType `:86-87`→`:85`/`:103`、`ltrim/rtrim` `:103-105`→`:109-110`、H2 `supportILike` `:17`→`:18`） | 已逐个改正 | 代码实测 |
| 44 | `trim`/`ltrim`/`rtrim` 标注"`minArgCount=maxArgCount=1`"，暗示 XML 里有该属性 | XML **没有**这两个属性；元数 1 来自 `NativeSQLFunction` 从 `argTypes.size()` 推导 | 代码实测 |
| 45 | Part 6 路径 ⓪ 的"现有成功证据"举了 `<template>` 的例子（属路径 ⓑ） | 已改为正确归类 | 内部一致性 |
| 46 | `_tmp/eqlprobe/Probe{,2..7}.java` 引用不全（实际只有 `Probe.java`） | 已改为不枚举具体文件名，并给出重建步骤 | 文档卫生 |
| 47 | 引用的 `grep -i keyset/cursor/seek/afterId` 断言「零命中」 | 不成立：`keySet()` 在多处命中（`Set.keySet`）。已改为"`IN` 列表长度上限无任何处理"这一可验证表述 | 代码实测 |
| 48 | `PART 3` 的 4 条筛选标准被自己违反（G3-1、G3-2、G1-TRIM、G8-regexpLike 均不满足"能无损 lowering"） | 已在筛选标准下**显式标注每项的例外与代价**，不再假装全部通过 | 内部一致性 |

**未被采纳**：第二轮有评审主张"DuckDB `<reservedKeywords>` 元素缺失、故交集为 0"。**该主张为假** —— `duckdb.dialect.xml:144` 存在 `<reservedKeywords x:override="replace">`（489 词），作者已按 `x:extends` + `x:override` 解析实测，交集确为 **26**，原数字正确。

---

### 附：修订记录（v1 → v2，第 2 版当时的原始对照）

v1 经 3 个独立子 agent 从 **代码事实**、**跨库支持矩阵**、**架构与成本** 三个视角交叉审查，共提出约 210 条核查项。以下为**已逐条复核并采纳**的实质性修正：

| # | v1 的问题 | v2 的修正 | 复核依据 |
|---|-----------|-----------|---------|
| 1 | 称 14 个 `DialectFeatures` 标志"无 accessor" | **9 个其实有 accessor**（`IDialect.java:75,77,79,81,83,85,94,96,98`）。准确表述是"**有 accessor、无生产消费方**"；只有 5 个（`supportNullsFirst`/`supportFullJoin`/`supportRightJoin`/`supportDeleteFromJoin`/`supportOutputForUpdate`）确实无 accessor | 代码复核 |
| 2 | 称 `AstToSqlGenerator` 原样输出 `LATERAL` | **错：`LATERAL` 被静默丢弃**。`AstToEqlGenerator.java:635-642` 从不读 `getLateral()`，`AstToSqlGenerator` 无覆写。实测 `lateral (sub)` 与 `(sub)` 输出字节相同 → **执行路径 P0** | 实测 + 代码 |
| 3 | 未发现 `ESCAPE` 丢失 | **新增 P0**：`AstToEqlGenerator.visitSqlLikeExpr` 从不读 `getEscape()`；全仓仅 `EqlASTVisitor:540`、`EqlASTOptimizer:1451` 读。escape 子句在 H2/DB2/DM/Oracle（默认 escape 为 NULL）会静默改变结果 | 代码 + 实测 |
| 4 | 未发现 `for update` 硬编码 | **新增**：`AstToEqlGenerator.java:458-461` 硬编码 `"for update"`，`AstToSqlGenerator` 无覆写，`IDialect.getForUpdateSql/getLockHintSql` 只被 `GenSqlHelper:196,202` 消费。DuckDB 已在 `duckdb.dialect.xml:38` 置空但 EQL 路径不读 | 代码 + 实测 |
| 5 | §1.1 声称 `SELECT *` 可用 | **错**：`select * from t` 解析出 `selectAll=true` + `projections` 为空，`toSqlString()` 产出 **`select from t`**（非法 SQL）。**但执行路径正常** —— `EqlTransformVisitor.java:272-279` 在编译期把 `*` 展开成显式投影。危害面仅限 `toSqlString()` / `@dump parsed_eql` | 实测 + 代码 |
| 6 | 建议新增 `IUpsertHandler` | **该 SPI 已于 2026-08-22 被刻意删除**（`ai-dev/audits/check/nop-dao.md:410-415`：`MysqlUpsertHandler` + `IUpsertHandler` 以"误导性死代码"为由移除，并注明"未来实现 MySQL upsert 时应连同集成测试重新引入"）。且 `_DialectModel.java:746` 本来就有 `getUpsertHandler()`（v1 自相矛盾） | 代码 |
| 7 | 称"全代码库唯一的 upsert 实现在 `JdbcCheckpointStorage.java:800-825`" | **有两个** `UpsertDialect` 私有枚举：`JdbcCheckpointStorage.java:800` 与 `nop-stream-connector-jdbc/JdbcTwoPhaseCommitSink.java:166` | 代码 |
| 8 | 称 `extract(y,d)` 会走 `year` 的 `extract(YEAR from {0})` 模板 | **错**：该模板属于 `year`（`oracle.dialect.xml:188`、`postgresql.dialect.xml:159`），不属于 `extract`；`extract` 本身在 MSSQL 被 `x:override="remove"` 删除（`mssql.dialect.xml:194`）→ MSSQL 上 `extract(...)` 直接 `unknown-function` | 代码 |
| 9 | Open Question 问"要不要给 `StdSqlType` 加 `TIMESTAMP_WITH_TZ`" | **前提有误**：`StdSqlType.java:38,42` 已有 `TIME_WITH_LOCAL_TIME_ZONE` / `TIMESTAMP_WITH_LOCAL_TIME_ZONE`，**但被注释掉了**。真实差距只是 `dataTypeName_` 拼写与枚举不一致 + 枚举被注释，属 1 行语法修复，不是跨模块公共 API 变更 | 代码 |
| 10 | Part 4 矩阵 `FULL OUTER JOIN \| MySQL 8 = ✅` | **错：MySQL 与 MariaDB 都没有 `FULL JOIN`**。BUG-2 不是"纯语法遗漏"，还需 `supportFullJoin` 接线 | 官方文档 |
| 11 | Part 4 矩阵 `RETURNING \| MySQL 8 = ✅`（正文写"MySQL 8.0.14+ 支持"） | **错：MySQL 8.0/8.4 全部没有 `RETURNING`**。**`mysql.dialect.xml:137 supportReturningForUpdate="true"` 是活的配置 bug**。反过来 `duckdb.dialect.xml:21 = false` 也错（DuckDB 支持） | 官方文档 |
| 12 | 称"Oracle 升序 NULL 在前（与 MySQL/MSSQL 同类）" | **错：Oracle 升序默认 NULLS LAST**（与 PG 同类）。NULL 排序实际分 **4 类**（见 Part 4.2） | 官方文档 |
| 13 | `TRIM(BOTH c FROM x)` 标注"PG 不接受" | **错：PG 完整支持标准三段式**（PG 文档给出 `trim(both 'xyz' from 'yxTomxx')` 示例）。G1 的 TRIM 项比 v1 说的更容易 | 官方文档 |
| 14 | `IS DISTINCT FROM` 矩阵 MSSQL = ❌ | **MSSQL 2022+ = ✅**（2019 及更早 = ❌）；**Oracle 23ai 仍无** | 官方文档 |
| 15 | `INSERT ... DEFAULT VALUES` 矩阵 Oracle = ✅ | **错：Oracle 没有该语法**（用 `VALUES (DEFAULT, ...)`）。且 Oracle 的 `RETURNING` 是 `RETURNING ... INTO`（宿主变量），**不能产出结果集**，普通 JDBC DML 用不了 → 应为 ⭕ 而非 ✅ | 官方文档 |
| 16 | `ORDER BY ... NULLS FIRST/LAST` 矩阵 DB2 = ⭕ | **DB2 11.5 原生支持** | 官方文档 |
| 17 | Part 4 无 MariaDB 列 | **新增 MariaDB 列**：它在 `RETURNING`(10.5+)、窗口函数(10.3+)、`UUID`(10.7+) 上与 MySQL 分道扬镳，且永不支持 `LATERAL` / `FULL JOIN` / `NULLS FIRST`。缺这一列至少 5 格必然错 | 官方文档 |
| 18 | Part 5 `\|\|` 否决理由"PG 的 `\|\|` 把 NULL 当空串，MySQL `CONCAT()` 遇 NULL 返回 NULL" | **事实反了**：PG 的 `\|\|` 遇 NULL 返回 NULL；忽略 NULL 的是 PG 的 `concat()` 函数。否决结论不变，理由需重写 | 官方文档 |
| 19 | Part 5 `DISTINCT ON` 否决理由"仅 PG/DuckDB" | **不完整**：H2 也支持 `DISTINCT ON` | 官方文档 |
| 20 | 未提及 `mysql5.7` 继承了窗口函数 mixin | **反向配置 bug**：`mysql.dialect.xml` 继承 `window-expr-support` → `mysql5.7` 继承到，但 **MySQL 5.7 没有窗口函数**。EQL 会编译通过、运行时被数据库拒绝 | 代码 |
| 21 | 未提及 `defaultNullsFirst` | **它在 7 个方言 XML 里被配置**（`defaultNullsFirst`），却零 Java 消费方。这比 `supportNullsFirst` 更该接线（它已经知道各方言默认值） | 代码 |
| 22 | 未提及 `SqlNativeFunctionModel.special` | **又一个死标志**（`default.dialect.xml:239` 给 `extract` 设了 `special="true"`，零逻辑消费） | 代码 |
| 23 | 未提及 `SQL92Keyword.g4` | **692 行、165 个 token**（v2 曾写"约 79 个（≈47%）"，v3 实测修正为 **86 个（≈52%）**）；63 个 token 仅为"能当标识符用"而存在。这是语法层最大的死代码面，v1 的"已预留未接线"清单漏了它 | 代码 |
| 24 | 未提及 `unreservedWord_` 与各方言保留字冲突 | 实测交集 **DuckDB 26、H2/h2gis 10、MSSQL 9、MariaDB 8、MySQL/mysql5.7/PG/postgis/Oracle/DM/TDengine 各 7**（`default`/`db2` 的保留字表为空）。`isReservedKeyword` 只在 `escapeSQLName` 里被调用，**解析期从不检查** → 同一段 EQL 在不同库生成不同转义的标识符 | 代码 |
| 25 | §1.1 声称集合访问 `col[idx].func()` 可用 | **错**：`o.roles[1].x()` 虽能解析但回显成垃圾 `[o.roles 1 ]`；`o.roles[order by a].x()` 直接解析失败 | 实测 |
| 26 | §1.1 声称 `@decorator(args)` 可用 | **只有裸 `@dump` 可用**；`decoratorArgs_: sqlLiteral`（`BaseRule.g4:291`）无法解析 `o.a` 形式的参数 | 实测 |
| 27 | BUG-1 说"`AstToSqlGenerator` 无 `visitSqlDateTimeLiteral` 覆写，DB 层完全不参与" | **错**：覆写在 `AstToSqlGenerator.java:399`，会调用 `dialect.getDateLiteral/getTimeLiteral/getTimestampLiteral`。CCE 发生在更早的解析期，症状仍与方言无关，但证据错误 | 代码 |
| 28 | 建议 BUG-8 可"把 `CROSS` 加入 `unreservedWord_`"作为备选修法 | **自相矛盾**：`CROSS` **已经在** `unreservedWord_`（`BaseRule.g4:93`），这正是它无法当 join 修饰语的原因。唯一正确修法是给 `joinType_` 加 `crossJoin_: CROSS JOIN`；若按 v1 备选方案"修复"（移出 `unreservedWord_`）会让 `cross` 无法再作列名 —— **回归** | 代码 |
| 29 | 未提及 `nop-metadata-service` 是 EQL AST 的外部消费方 | `SqlSelectFieldExtractor` / `SqlColumnLineageExtractor` / `SqlSourceEntityExtractor` 直接持有 `EqlASTParser` 解析用户 SQL，绕过 `EqlTransformVisitor`。它们已有针对 `SELECT *` 的显式失败裁决。任何语法改动都是跨模块行为变更 | 代码 |
| 30 | 未提及 `GREATEST`/`LEAST`、`LISTAGG`/`string_agg`/`GROUP_CONCAT` 缺失 | **新增建议**。这些在**任何**方言 XML 里都不存在；实测三者都能解析（普通函数调用），只缺函数注册。且 `sqlIdentifier_agg_`（`BaseRule.g4:235-237`）把聚合函数硬编码为 5 个 token，聚合**完全绕过** `<functions>` 表 | 代码 + 实测 |
| 31 | 成本估算把"lowering 到已有函数模板"当作统一低成本手段 | **机制被误判**（见 Part 6 四路机制表）：`<template>` **无** `minArgCount/maxArgCount`（固定元数）；`<native>` 才有；聚合完全绕过函数表；`TemplateSQLFunction` 对越界 `{n}` 静默产出 ` null `；`AstToSqlGenerator` 是**单遍打印器**，所有多节点改写必须走 `IEqlAstTransformer`（唯一先例 `CollectionOperatorTransformer` = 711 行） | 代码 |
| 32 | 把 `NULLS FIRST` 的方案定为"接线 `supportNullsFirst` + `CASE WHEN` 模拟" | **更优且更小**：接线已有的 `DialectModel.defaultNullsFirst`（7 个 XML 已配、零消费）+ 为已预留的 `SqlOrderByItem.nullsFirst` 加语法/打印。未指定方向的默认值也随之可移植 | 代码 |
| 33 | 建议"确认为死的从 `dialect.xdef` 移除" | **危险**：`supportNullsFirst` 在 4 个 XML（`duckdb/h2/oracle/postgresql`）里被实际配置。"零消费" ≠ "可删除"，删 xdef 属性会让现有 XML 校验失败。应接线，不应删除 | 代码 |
| 34 | 建议 G8"禁止裸 `INTERVAL` 算术" | **是破坏性变更**：现有 EQL 已能解析并生成，属回归。应改为"弃用 + 加 `sqlIntervalLiteral` 模板"，不动语法 | 代码 |
| 35 | 建议 `TRUNCATE` 进 EQL | 保留，但降级：方言层已有 `getDropTableSql`，DDL 归属应明确在 `nop-db-migration`（其 `_SqlChange` 已有 `dbSpecific` 属性集，即**逐方言 DDL 覆盖的正式机制**） | 代码 |
| 36 | 未说明 `stdNameMap` 别名会影响 DDL 生成 | `StdSqlType.fromStdName` 同时被 CAST 校验和 DDL 类型映射使用；加 `TEXT→CLOB` 别名会改变所有 ORM 模型的 `<column stdSqlType="text">` 解析结果 → 属 `nop-kernel` 跨模块公共 API（plan-first） | 代码 |
| 37 | 未提示 tokenizer 关键字遮蔽 | **224 个词**（v3 实测；v2 曾写 199）被硬词法化为 keyword、无法作标识符。直接解释了 `day()`/`hour()`/`second()`/`quarter()`/`size()`/`key()` 的解析失败，以及 `SUBSTRING`/`TRIM` 为何要在 `sqlIdentifier_func_` 里特判 | 代码 + 实测 |
| 38 | 未说明实验脚本引用不全、`.g4`/xjava 改动会重生 72 个 `_gen` 文件与 1653 行 `_EqlASTBuildVisitor.java`，且 `EqlASTKind/Visitor/Processor/Optimizer` 带 `__XGEN_FORCE_OVERRIDE__` 需手改 switch | 全部采纳，并折算进 Part 7 批次成本；新增 AST 节点的真实成本被显著低估 | 代码 |
| 39 | 未提示删除 lexer token 会重编号全部 token 类型 | 采纳为独立风险项 | 代码 |
| 40 | Part 7 未给任何批次分配测试预算 | 采纳。AGENTS.md「Bug Fix Test Coverage Rule」要求每个修复配回归测试；且 `nop-dao/TestDialect.testFunctions` 会**对每个新增函数做真实数据库执行**，函数表改动自带跨模块回归面 | 代码 + AGENTS.md |

**未被采纳的审查意见**：R3 建议把 `nop-nosql`（Redis/Lettuce，无查询 DSL）纳入 EQL 讨论范围 —— 已核查确认与 EQL 语义无重叠，v2 仅在 Part 10 记录边界，不展开。

---

## 调研方法与证据分级

EQL 语法是编译期生成的 ANTLR 解析器（`src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java`，9933 行，git 跟踪）。因此**语法行为结论全部来自实测**。

```bash
./mvnw -o -pl nop-persistence/nop-orm-eql dependency:build-classpath \
      -Dmdep.outputFile=_tmp/eqlprobe/cp.txt
CP="nop-persistence/nop-orm-eql/target/classes:$(cat _tmp/eqlprobe/cp.txt)"
javac -cp "$CP" -d _tmp/eqlprobe/out _tmp/eqlprobe/Probe.java
java  -cp "_tmp/eqlprobe/out:$CP" Probe
```

- 语句级：`new EqlASTParser().parseFromText(null, sql)`
- 表达式级：`new EqlExprASTParser().parseFromText(null, expr)`
- 回显：`SqlStatement.toSqlString()`（走 `AstToEqlGenerator`）
- 探测脚本写在 `_tmp/eqlprobe/` 下（**已被 `.gitignore:123` 忽略、不在版本库内，读者需按下方步骤自行重建**）

解析采用 SLL→LL 两阶段 + `BailErrorStrategy`（`AbstractParseTreeParser.java:84-106`）。

**证据分级**：本报告**按节**标注证据等级，而非逐条（v2 曾声称逐条标注，实际全文仅 34 处标签，已更正）：

| 等级 | 含义 | 覆盖范围 |
|------|------|---------|
| `[实测]` | 作者用 `_tmp/eqlprobe/` 下的 Java 探针实际跑过解析器/生成器 | Part 1.1 的语法可用性、Part 2 全部现象、Part 9.8 的遮蔽词统计 |
| `[代码]` | 读源码得出，未实际执行 | Part 1.2 方言归一化、Part 1.3 死代码清单、Part 6 机制表、Part 10 影响面 |
| `[文档]` | 依据各数据库官方文档，**未在真实引擎执行** | **Part 4 整张矩阵、Part 4.2/4.2b/4.2c、Part 9** |
| `⚠️ unverified` | 无可引用一手文档，**不得计入覆盖率** | **DM8 全列**（中文私有文档门户）、TDengine、DuckDB/MariaDB 的部分版本门 |

⚠️ 重建步骤（`_tmp/` 已被 `.gitignore:123` 忽略，不在版本库内）：`./mvnw -o -pl nop-persistence/nop-orm-eql dependency:build-classpath -Dmdep.outputFile=<cp>` 后用 `EqlASTParser` / `EqlExprASTParser` 的 `parseFromText(null, text)` 与 `SqlStatement.toSqlString()` 即可复现 `[实测]` 结论。

> ⚠️ `[文档]` 表示依据各数据库官方文档，**未在真实引擎上执行验证**；落地前应按项目承诺的最低版本逐格复核。

---

# Part 1 — 现状盘点

## 1.1 EQL 语法能力（实测）

### 可用且跨库正常工作

| 类别 | 已验证语法 | 备注 |
|------|-----------|------|
| 查询骨架 | `SELECT [DISTINCT] proj [AS alias] FROM … [WHERE] [GROUP BY] [HAVING] [ORDER BY asc/desc] [LIMIT n [OFFSET m]]` `[实测]` | `DMLStatement.g4:94-104` |
| 投影 | `expr AS alias`、聚合别名引用（`group by start` / `order by start`）、`o.*`、`count(*)` `[实测]` | **`SELECT *` 解析正常，仅 AST→文本回显有缺陷（见 BUG-4）** |
| 表源 | 单表、逗号连接、子查询表源、属性自动 prop-join、`LATERAL` 关键字（**解析支持，输出丢失，见 BUG-2**） | `DMLStatement.g4:144-166` |
| JOIN | `JOIN` / `INNER JOIN` / `LEFT [OUTER] JOIN` / `RIGHT [OUTER] JOIN` / `FULL JOIN`（**不含 `OUTER` 关键字**）+ `ON` `[实测]` | 见 BUG-6 |
| 集合运算 | `UNION [ALL]` / `INTERSECT [ALL]` / `EXCEPT [ALL]`，支持括号包裹分臂 `[实测]` | `DMLStatement.g4:91-92`；`INTERSECT/EXCEPT` 受库限制，见 Part 4.1 |
| CTE | `WITH [RECURSIVE] name AS (select)`；递归 CTE 自动补 H2 要求的 column list `[实测]` | `AstToSqlGenerator.java:508-540` |
| 子查询 | 标量子查询、`IN (sub)`、`EXISTS [NOT] (sub)`、相关子查询 `[实测]` | |
| 量词比较 | `= ANY/SOME (sub)`、`>= ALL (sub)` `[实测]` | `SOME`/`ANY` 由 `supportSomeSubQuery` 切换（仅 Oracle 走 SOME） |
| 谓词 | `IS [NOT] NULL`、`[NOT] IN (v1,…)`、`[NOT] BETWEEN a AND b`、`LIKE`（**`ESCAPE` 子句被丢弃，见 BUG-1**）、`[NOT] ILIKE`（**仅 Oracle 可用**） | |
| 表达式 | `NOT/AND/OR`、`&&`、`+ - * / %`、`^ & \| << >>`、一元 `+ - ~ !`、括号；优先级由 `SqlOperator.getPriority()` 驱动 | `SqlOperator.java:43` |
| 特殊表达式 | `CASE [expr] WHEN…THEN… [ELSE] END`、`CAST(x AS <StdSqlType>)`、`INTERVAL n unit`（**各方言原样输出，见 BUG-5**）、`?` 位置参数 | |
| **行值比较** | `(a,b) = (1,2)`、`(a,b) > (?,?)`、`(a,b) IN (sub)` **已可解析** `[实测]` | 走 `sqlExpr_brace`，语义在不支持行值的库上未定义 |
| DML | `INSERT INTO t(cols) VALUES(…)` / `INSERT INTO t(cols) SELECT …`、`UPDATE t SET … WHERE [RETURNING \*\|cols]`、`DELETE FROM t [alias] WHERE` | 列清单**强制** |
| 事务 | `COMMIT`、`ROLLBACK` `[实测]` | `Eql.g4:53-61` |
| 多语句 | `stmt; stmt;` 可解析（`Eql.g4:25-26`），但 `EqlCompiler` 显式拒绝 `>1` 条（`ERR_EQL_NOT_SUPPORT_MULTIPLE_STATEMENT`） | 属设计决策，非缺陷 |
| 注释 | `/* … */`、`--` 走 `Comments.g4` hidden channel | ⚠️ `Keyword.g4:22` 声明 `WS ->skip` 而 `SQL92Keyword.g4:22` 声明 `WS ->channel(HIDDEN)`，合并词法器按先声明者胜出，行为未被文档化 |
| Nop 扩展 | 裸 `@decorator`（**`@dump(args)` 形式不可用**）、集合操作符 `o.roles._some.x` / `_all.x` | `sqlDecorator` 的 `args` 为 `sqlLiteral`（`BaseRule.g4:291`），无法接受属性引用 |

### 不支持（实测 FAIL，共 120+ 条）

<details>
<summary>展开完整清单</summary>

**查询子句**：`DISTINCT ON`、`SELECT TOP n`、`FETCH FIRST n ROWS ONLY`、`FETCH … WITH TIES`、`LIMIT off,lim`、`LIMIT ALL`、`ORDER BY NULLS FIRST/LAST`、`ORDER BY <expr> COLLATE x`、`ORDER BY ALL`、`GROUP BY ALL`、`GROUPING SETS`、`GROUP BY <位置>`

**JOIN**：`CROSS JOIN`、`NATURAL JOIN`、`JOIN … USING(col)`、`OUTER APPLY`、`CROSS APPLY`、`FULL OUTER JOIN`、`LEFT JOIN FETCH`（无 fetch-join 概念）

**窗口**：`over(ORDER BY …)`（缺 PARTITION）、窗口 frame（`ROWS BETWEEN …`）、`OVER w` / `WINDOW w AS (…)`、`count(*) FILTER (WHERE …)`

**锁**：`FOR UPDATE OF t`、`FOR UPDATE NOWAIT`、`FOR UPDATE SKIP LOCKED`

**DML**：`INSERT INTO t VALUES(…)`（无列清单）、`INSERT … DEFAULT VALUES`、`ON CONFLICT`、`ON DUPLICATE KEY UPDATE`、`INSERT IGNORE`、`MERGE`、`DELETE … LIMIT`、`UPDATE … FROM`、`DELETE … USING`

**表源**：`FROM (VALUES …) AS t(a)`、`table(fn(…))`、`JSON_TABLE`

**DDL/TCL**：`CREATE TABLE/VIEW/INDEX`、`ALTER TABLE`、`DROP TABLE`、`TRUNCATE`、`SAVEPOINT`、`SET`

**表达式**：`EXTRACT(f FROM d)`、`SUBSTRING(x FROM a FOR b)`、`POSITION(x IN y)`、`TRIM([BOTH|LEADING|TRAILING] [c] FROM x)`、`a <=> b`、`a == b`、`a IS [NOT] DISTINCT FROM b`、`a IS [NOT] TRUE/FALSE/UNKNOWN`、`SIMILAR TO`、`RLIKE`、`REGEXP`、`a || b`、表达式级 `COLLATE`、`AT TIME ZONE`、`XOR`、`arr[1]`（裸下标）、`interval '1 day'`（PG 形态）

**因关键字遮蔽而无法作为函数名的词**（实测 224 个，见 Part 9.8）：`day()`、`hour()`、`second()`、`quarter()`、`week()`、`size()`、`key()` 等。⚠️ `ntile()` **不在此列**（`NTILE` 不是 token；实测 `ntile(4) over(order by x)` 失败的真实原因是**缺 `PARTITION BY`**，见 G5）

**因关键字遮蔽而无法作为函数名的 JPQL 常用词**：`size`、`key`、`first`、`last`、`day`、`hour`、`week`、`second`、`minute`、`quarter`、`precision`、`output`、`option`、`unknown`、`pad`。⚠️ `substring`/`trim` **不在此列** —— 它们被 `BaseRule.g4:283-284` 的 `sqlIdentifier_func_` 显式白名单，实测 `substring(a)`/`trim(a)` 均可解析；`row`/`ntile` 也不是 token

</details>

## 1.2 方言层（Dialect）能力盘点

方言不是 Java 类继承树，而是 **xdef 模型 + XML**：

```
DialectModel (nop-kernel/nop-xdefs/.../orm/dialect.xdef)
  → *.dialect.xml (x:extends + x:override=merge|replace|remove)
  → DialectImpl (唯一实现，nop-dao/.../dialect/impl/DialectImpl.java，765 行)
```

**16 个方言 XML**（含 `oracle-reverse`）+ 2 个 mixin，**9 个自动选择 selector**：

| 类别 | 文件 | 继承 | 函数覆写数 |
|------|------|------|-----------|
| 基线 | `default.dialect.xml` | — | **49**（ANSI）；`<reservedKeywords>` **为空（0 词）** |
| MySQL 系 | `mysql` → `mysql5.7`、`mariadb` | `default` + `geo-support` + `window-expr-support` | mysql **2** / mysql5.7 **0** / mariadb **0** |
| PG 系 | `postgresql` → `duckdb`、`postgis` | `postgresql` = `default` + `geo-support`（**无 window mixin**） | pg **8** / duckdb **35** / postgis **0** |
| Oracle 系 | `oracle` → `oracle-reverse` | `default` + `geo-support` + `window-expr-support` | oracle **12** |
| 其他 | `mssql`、`h2` → `h2gis`、`db2`（**27 行纯 stub：无 `<functions>`、无 `<reservedKeywords>`**）、`dm`、`tdengine` | 各自 `default` + `window-expr-support` | mssql **21** / h2 **5** / h2gis **0** / db2 **0** / dm **2** / tdengine **2** |
| 伪方言 | `es`（`driverClassName="xx"`，`<features/>` 为空，保留字表字面量 `xx`） | `default` | 0 |

> ⚠️ 覆写数（`<native>`/`<template>` 条目，含 `x:override="remove"`）为**作者按 XML 实测**。含义很关键：**`mariadb`/`mysql5.7`/`postgis`/`h2gis`/`db2` 的函数表条目数为 0，意味着它们的函数语义完全等于其父方言**。
| mixin | `geo-support`（27 个 `st_*` + `GeometryTypeHandler`）、`window-expr-support`（10 个窗口函数，全部 `onlyForWindowExpr="true"`） | | |

⚠️ **`db2` 与 `es` 是实质空壳**：DB2 的全部方言差异（含函数名）都靠 ANSI 兜底；`es` 不是真实 SQL 数据库，**不应出现在任何能力矩阵里**。

### 已有的方言归一化点（`AstToSqlGenerator.java` 覆写）

| 归一化项 | 实现 | 方言差异 |
|---------|------|---------|
| 标识符转义 | `escapeSQLName` `DialectImpl.java:420-445` | MySQL 反引号；其余双引号；Oracle `ROWID` 重命名；`tableNameCase`/`columnNameCase` |
| 分页 | `IPaginationHandler` × 2 | MySQL/PG/H2/DB2/DM → `LIMIT/OFFSET`；Oracle/MSSQL → `OFFSET..ROWS FETCH NEXT..ROWS ONLY` |
| 无 FROM 的 SELECT | `getSelectFromDualSql` `AstToSqlGenerator.java:114-131` | **只有 Oracle 覆写为 `from dual`**；MSSQL 需表、DB2 需 `SYSIBM.SYSDUMMY1` → 这两类库会生成非法 SQL |
| `EXCEPT` | `getExceptKeyword` `DialectImpl.java:341-344` | Oracle → `MINUS` |
| `ANY`/`SOME` | `supportSomeSubQuery` `AstToSqlGenerator.java:495-506` | Oracle → `SOME` |
| `AS` in FROM | `useAsInFrom` | Oracle 省略 `AS` |
| 布尔字面量 | `trueString/falseString` `DialectImpl.java:463-465` | PG/H2 → `true/false`；其余 → `1/0` |
| 日期时间字面量 | 模板 `{value:pattern}` `DialectImpl.java:468-527` | MSSQL → `cast('…' as datetime)`；DuckDB → `TIMESTAMP_S '…'` |
| 字符串转义 | `escapeSlash` `DialectImpl.java:540-543` | PG/H2/MSSQL/Oracle → 不转义反斜杠；MySQL/DM/TDengine → 转义 |
| `ILIKE` | `dialect.getFunction("ilike")` `AstToSqlGenerator.java:456-476` | **只有 Oracle 注册**；无注册方言抛 `nop.err.eql.not-support-ilike-operator`（**H2 在 `h2.dialect.xml:18` 设了 `supportILike="true"` 但也无注册**） |
| 函数名/模板 | `<native realName>` / `<template>{0}</template>` `DialectImpl.java:127-144, 609-612` | PG `rand→random`、`instr→strpos`；MSSQL `uuid→newid`、`instr→charindex`、`current_date→getdate`；Oracle `rand→DBMS_RANDOM.VALUE`、`year→extract(YEAR from {0})` |
| CAST 类型 | `stdToNativeSqlType` `SqlDataTypeMapping.java:80-114` | `StdSqlType` → 原生类型；VARCHAR 超长降级 CLOB |
| `RETURNING` | `supportReturningForUpdate` 编译期校验 `EqlTransformVisitor.java:1476-1484` | 见 Part 1.4 的 3 个配置错误 |
| CTE | `supportWithAsClause` 降级为派生表 `EqlTransformVisitor.java:556-561` | 全部内置方言 `true`，降级路径不可达 |
| 序列 | `sequenceNextVal` 模板 | Oracle `x.nextval from dual` / PG `nextval('…')` / H2·MSSQL·MariaDB·ES `next value for` / MySQL·DM·TDengine·DB2 **未定义** |
| JDBC 锁 | `getForUpdateSql` / `getLockHintSql` `IDialect.java:169,176` | **只被 `GenSqlHelper.java:196,202` 消费，EQL 路径完全绕过** |
| DDL | `getCreateSequenceSql` / `getDropSequenceSql` / `getDropTableSql` | `nop-db-migration` 的 `_SqlChange.dbSpecific` 是逐方言 DDL 覆盖的**正式机制** |

## 1.3 已预留但零消费的扩展点（本次调研最有价值的清单）

这一节把项目从"加特性"重新框定为"**接线 **14** 个已预留的空位**"，成本低一个数量级。

### A. AST 字段（`EqlAST.xjava` 有、语法不填、生成器不读）

| 字段 | 位置 | 谁读 | 状态 |
|------|------|------|------|
| `SqlOrderByItem.nullsFirst` | `EqlAST.xjava:234` | 无人 | **接线成本最低的入口**，且 `DialectModel.defaultNullsFirst` 已在 7 个 XML 里配好 |
| `SqlOrderByItem.collate` | `EqlAST.xjava:232` | 无人 | 需 `collateClause_` 进语法 |
| `SqlQuerySelect.nowait` | `EqlAST.xjava:163` | 无人 | `forUpdate_` 硬编码 |
| `SqlSubqueryTableSource.lateral` | `_SqlSubqueryTableSource.java:41` | `EqlTransformVisitor:486`（语义 scope）；**两个生成器都不读** | **静默丢关键字，执行路径 P0**（BUG-2） |
| `SqlLikeExpr.escape` | `EqlAST.xjava:420` | 仅 `EqlASTVisitor:540`（子树遍历）、`EqlASTOptimizer:1451`（常量折叠） | **静默丢子句，执行路径 P0**（BUG-1） |
| `SqlTypeExpr.characterSet` / `.collate` | `EqlAST.xjava:507-508` | 无人 | |
| `SqlQuerySelect.selectAll` | `EqlAST.xjava` | `EqlTransformVisitor:276`（编译期展开 `*`）、`AstToEqlGenerator:823`（仅聚合的 `count(*)`） | **`printSelect` 不读 → 回显产出非法 SQL**（BUG-4） |

### B. 方言模型字段 / 特性标志

| 字段 | 位置 | 状态 |
|------|------|------|
| `DialectModel.defaultNullsFirst` | `_DialectModel.java:283-291`；**7 个 XML 已配置** | **零 Java 消费方** ← 最该接线的一个 |
| `DialectModel.upsertHandler` | `_DialectModel.java:746`（**getter 存在**） | 零消费；对应 SPI 已于 2026-08-22 被刻意删除 |
| `DialectModel.discoverySqls` | `default.dialect.xml:261-263`（空） | 零消费 |
| `SqlNativeFunctionModel.special` | `_SqlNativeFunctionModel.java:308`；`default.dialect.xml:239` 给 `extract` 设了 `special="true"` | 零逻辑消费 |
| `DialectFeatures.supportNullsFirst` | `_DialectFeatures.java:348` | **无 accessor**；`duckdb/h2/oracle/postgresql` 4 个 XML 实际配置 |
| `DialectFeatures.supportFullJoin` / `supportRightJoin` / `supportDeleteFromJoin` / `supportOutputForUpdate` | `_DialectFeatures.java` | **无 accessor**、零消费（但 MySQL/MariaDB 无 `FULL JOIN`，`supportFullJoin` 有真实语义价值） |
| 已有 accessor 但零生产消费方（**9** 个） | `IDialect.java:75,77,79,81,83,85,94,96,98` → `isSupportSavePoint/isSupportTransaction/isSupportTruncateTable/isSupportUpdateTableAlias/isSupportDeleteTableAlias/isSupportRowValueConstructor/isSupportUpdateFromJoin/isSupportSequence/isSupportILike` | 全部零生产消费者；`isSupportSequence` 唯一调用者是 `TestDialect.java:148`（测试） |
| `<sqls>` 的 `forUpdate` / `lockHint` 模板 | `duckdb.dialect.xml:38-39` 明确置空 | EQL 路径不读（BUG-3） |

### C. 词法层死代码

| 项 | 位置 | 状态 |
|----|------|------|
| `SQL92Keyword.g4` | 692 行、165 个 token | **86 个（≈52%）零引用**；另有 **61** 个 token 仅通过 `unreservedWord_` 被引用（`unreservedWord_` 共 77 项，其中 `END`/`ZONE` 另有真实规则引用）。这是语法层最大死代码面 |
| `Symbol.g4` 的 `CONCAT_`(`\|\|`) / `DEQ_`(`==`) | `Symbol.g4:21,39` | 零引用 |
| `Symbol.g4` 的 `SAFE_EQ_`(`<=>`) | `Symbol.g4:38` | 仅 `BaseRule.g4:142` 的注释引用；是 G3-1 的 MySQL lowering 目标 |
| `BaseRule.g4:316-318` `sqlSubQueryExpr: 'Default does not match anything'` | 死规则残骸（活规则在 `DMLStatement.g4:211-213`，因 ANTLR import-override 语义覆盖前者） | 死代码 |
| `BaseRule.g4:246-276` 被注释掉的 `positionFunction` / `substringFunction` / `extractFunction` / `trimFunction` | **标准 SQL-92 正确形状** | **强先例**：说明这些决定曾做过又被回退。落地前应先查 git 历史回答"为什么被注释掉" |
| `SqlOperator.RLIKE` | `SqlOperator.java:23` | 枚举存在，无语法谓词 |
| `SqlDdlStatement` 返回 `SqlStatementKind.DDL` | `Eql.g4:37-49` DDL 全注释 | 枚举已跑在语法前面 |

### D. AGENTS.md「Do not hand-edit generated files」视角的连带面

改 `.g4` 或 `EqlAST.xjava` 会**原地重写**：`EqlParser.java`（9933 行）、`EqlLexer.java`、`EqlBaseVisitor.java`、`parse/_EqlASTBuildVisitor.java`（1653 行）、`ast/_gen/*`（**72 个文件**），全部 git 跟踪。
另注意：`ast/EqlASTKind.java`、`EqlASTVisitor.java`、`EqlASTProcessor.java`、`EqlASTOptimizer.java` 带 `//__XGEN_FORCE_OVERRIDE__`，**不会被重生成**。其中 **`EqlASTKind.java` 是纯枚举（130 行、无 switch）**，只需手工追加常量；其余三个各含一个 `switch(node.getASTKind())`（分别在 `:11` / `:10` / `:11`）必须手改。新增 AST 节点 = 手改这 4 处 + 新建 `ast/SqlXxx.java` 非下划线子类。

## 1.4 已确认的方言配置错误（活 bug）

| 位置 | 配置 | 实际 |
|------|------|------|
| `mysql.dialect.xml:137` | `supportReturningForUpdate="true"` | **MySQL 8.0/8.4 全部没有 `RETURNING`** → 会生成被数据库拒绝的 SQL |
| `duckdb.dialect.xml:21` | `supportReturningForUpdate="false"` | DuckDB 支持 `RETURNING` → 误报为不支持 |
| `dm.dialect.xml:67` | `supportReturningForUpdate="true"` | DM8 的 `RETURNING` 是 `INTO` 风格（宿主变量），普通 JDBC DML 取不到结果集 → 应为受限而非等价 |
| `mysql5.7.dialect.xml` | 继承 `mysql` → 继承 `window-expr-support` | **MySQL 5.7 没有窗口函数** → `row_number() over(…)` 编译通过、运行时被拒绝 |
| `postgresql.dialect.xml` / `duckdb.dialect.xml` | 未继承 `window-expr-support` | PG 与 DuckDB 原生完整支持窗口函数，但 `rank`/`row_number`/`lead`/`lag` 全部 `unknown-function` |
| `h2.dialect.xml:18` | `supportILike="true"` 但无 `ilike` 函数注册 | H2 上 `ILIKE` 抛 `not-support-ilike-operator`；PG 同理（PG 原生支持 `ILIKE` 却未注册） |
| `h2.dialect.xml:17` | `supportNullsFirst="true"` | 无 accessor、无模拟实现、无测试 —— 是一句无证据的断言 |

---

# Part 2 — 缺陷清单（按"危害 × 修复成本"排序）

## A 类：执行路径静默产出错误 SQL（最严重，用户无任何信号）

> 本类 **6** 项：**BUG-1**（P0）、**BUG-2**（P0）、**BUG-3**（P1）、**BUG-5**（P1）、**BUG-10**（P1）、**BUG-14**（P1）。共同特征是**用户与开发者都收不到任何错误信号**，SQL 文本却已改变语义。
> ⚠️ **BUG-4 不属于本类** —— 它的执行路径正常（见 B 类说明），仅 AST→文本回显受影响。

### BUG-1（P0）`LIKE … ESCAPE` 子句被静默丢弃

**现象** `[实测]`

```
parse("… where o.name like 'a!%%' escape '!'")
  → toSqlString(): "… where o.name like 'a!%%'"      // escape 消失
```

**根因** `[代码]`

- `BaseRule.g4:156-157` 解析 `(ESCAPE escape=sqlExpr_simple)?`；`EqlAST.xjava:420` 有 `SqlExpr escape` 字段；`_EqlASTBuildVisitor` 正常填充。
- `AstToEqlGenerator.visitSqlLikeExpr` 只打印 `not` + `expr LIKE value`，**从不读 `getEscape()`**。
- `AstToSqlGenerator.visitSqlLikeExpr`（`:456-476`）只特判 `ignoreCase`；非 ignoreCase 分支 `super` → 同样丢失。
- 全仓 `getEscape()` 读者只有 `EqlASTVisitor.java:540`（子树遍历）与 `EqlASTOptimizer.java:1451-1454`（常量折叠）。**无任何生成器读取。**

**影响**：MySQL 默认 escape 是 `\`，所以 `escape '\'` 恰好"碰巧可用"；**H2 / DB2 / DM / Oracle 的默认 escape 是 NULL**，`\_` 被当作字面反斜杠 → **静默返回错误的行集**。在安全/过滤类条件中后果尤其严重。

**修法**：在 `AstToEqlGenerator.visitSqlLikeExpr` 打印 `escape`；`AstToSqlGenerator` 覆写为方言归一（若某库不支持 `ESCAPE` 子句则改写为函数调用）。

### BUG-2（P0）`LATERAL` 关键字被静默丢弃 → 生成未关联子查询

**现象** `[实测]`

```
parse("… from AppUser o, lateral (select 1 from AppRole r where r.userId=o.id) x")
  → "… from AppUser as o, ( select 1 from AppRole as r where r.userId = o.id ) as x"

parse("… from AppUser o, (select 1 from AppRole r where r.userId=o.id) x")
  → "… from AppUser as o, ( select 1 from AppRole as r where r.userId = o.id ) as x"
                                                    ↑ 输出字节相同
```

**根因** `[代码]`

- `DMLStatement.g4:153-154` 语法支持 `lateral=LATERAL?`；`_EqlASTBuildVisitor.java:1235-1236` 写入 `setLateral(true)`。
- `AstToEqlGenerator.visitSqlSubqueryTableSource`（`:635-642`）只打印 `'(' query ')' alias`，**从不读 `getLateral()`**。
- `AstToSqlGenerator` **无** `visitSqlSubqueryTableSource` 覆写。
- `grep -i lateral` 在整个 `sql/` 包里零命中。

**影响**：比"Oracle 11g 运行时失败"更严重 —— **所有数据库**都退化成未关联子查询（通常报 `missing FROM-clause entry` 或静默返回笛卡尔积的错误结果）。而 `EqlTransformVisitor` 把 lateral 子查询当作可见 scope 编译，**语义模型与生成的 SQL 相矛盾**。

**修法**：`AstToEqlGenerator` 打印该关键字 + 真正的方言决策（MariaDB / DB2<9.7 / TDengine / es 无 lateral；Oracle 需 12.1+）。因语义确实分叉，`supportLateral` 特性标志在此**是正当的**。

### BUG-3（P1）`for update` 硬编码，绕过方言锁模板

**现象** `[代码]` `AstToEqlGenerator.java:458-461` 无条件 `print("for update")`；`AstToSqlGenerator` 无覆写。

**影响**

- `IDialect.getForUpdateSql` / `getLockHintSql`（`IDialect.java:169,176`）**只被 `nop-orm/.../sql/GenSqlHelper.java:196,202` 消费** —— 那是 JDBC 锁路径，不是 EQL 路径。
- `duckdb.dialect.xml:38-39` 明确把 `forUpdate` 和 `lockHint` 置空（注释"DuckDB 无锁支持"），`nop-duckdb/src/test/java/io/nop/duckdb/TestDuckDbDialect.java:285-287` 还断言它为空 —— **但 EQL 路径永不调用它，该测试给出虚假信心**。
- 结果：EQL 的 `select … for update` 在 DuckDB / MySQL-InnoDB（事务外）/ SQLite / MSSQL 上会被拒绝或无效。
- `default.dialect.xml:36` 的 `<forUpdate>for update</forUpdate>` 在 EQL 路径上也是死的。
- 同时 `SqlQuerySelect.nowait`（`EqlAST.xjava:163`）零消费 → `NOWAIT` / `SKIP LOCKED` 无处安放。

**修法**：`AstToSqlGenerator` 覆写 `printSelect` 的 for-update 分支，走 `dialect.getForUpdateSql(...)`；不支持时**编译期报错**（遵循 `AstToSqlGenerator.java:471-473` 对 `ILIKE` 已确立的"静默降级会改变语义，必须显式报错"原则）。

### BUG-4（P1）`SELECT *` 与 `LIMIT`/`OFFSET` 被丢弃 → AST→文本回显产出非法 SQL

> ⚠️ **本项不属于 A 类**：执行路径由 `EqlTransformVisitor` 与 `IPaginationHandler` 兜住，只有 `toSqlString()` / `@dump` 受影响。

**现象** `[实测]`

```
parse("select * from t")                             → toSqlString(): "select from t"          ← 非法 SQL
parse("select distinct * from t")                    → "select distinct from t"               ← 非法 SQL
parse("with x as (select * from t) select * from x") → "with x as ( select from t ) select from x"
parse("select o.id from t order by o.id limit 10 offset 5")
                                                     → "select o.id from t order by o.id asc"  ← LIMIT+OFFSET 全丢
```

**根因** `[代码]`

- `printSelect`（`AstToEqlGenerator.java:431-462`）按 `from → where → groupBy → having → orderBy → forUpdate` 输出，**不输出 `selectAll`、不输出 `limit`**。
- `visitSqlLimit`（`:589-598`）写法完全正确，但**全类内零调用方**。

**影响范围（重要区分）**

- **执行路径正常**：`EqlTransformVisitor.java:272-279` 在编译期把 `selectAll` 展开成显式投影；`AstToSqlGenerator.java:89-99` 通过 `IPaginationHandler` 处理分页。
- **受害的是 `toSqlString()` 与 `@dump`**：`EqlCompiler` 的 `parsed_eql` / `transformed-eql` 调试输出（`EqlCompiler.java:51,70`）、任何 AST→文本往返。
- **外部消费方已被绕过**：`nop-metadata-service` 的 `SqlSelectFieldExtractor.java:121` 与 `SqlColumnLineageExtractor.java:192` 都留有注释说明"平台 EqlASTParser 对裸 `SELECT *` 会静默丢弃通配符并返回空 projections 列表"，并为此**专门写了显式失败逻辑**。
- 连带：`AstToEqlGenerator.java:684` 用 `escapeSql(value, false)`，而 `DialectImpl.getStringLiteral` 用 `escapeSlash` 感知的版本 → 回显路径反斜杠转义不一致；`:719-721` 硬编码 ` 1 `/` 0 ` 而 `AstToSqlGenerator:393-395` 用 `dialect.getBooleanValueLiteral` → PG/H2 的布尔在 dump 里显示为 `1/0`。

**修法**：`printSelect` 补 `selectAll` 与 `visitSqlLimit`；连带把字符串转义与布尔字面量也走 dialect。

### BUG-5（P1）`INTERVAL n unit` 原样输出，跨库生成错误 SQL

`BaseRule.g4:308-314` 的 `sqlIntervalExpr` 是 **MySQL/Oracle 形态**；`AstToEqlGenerator.visitSqlIntervalExpr` 无方言分支，原样输出。实测 `o.createTime + interval 1 day` 解析 OK `[实测]`，但生成的 SQL 在 PostgreSQL 上非法（PG 要 `INTERVAL '1 day'`）。

**v1 建议"在 EQL 层禁止裸 `INTERVAL`"是破坏性变更**（现有 EQL 已依赖它），应改为：

- 加 `sqlIntervalLiteral` 模板（与既有 `dateLiteral` / `timeLiteral` / `dateTimeLiteral` 同机制，`default.dialect.xml:27-31`），逐方言定义形态；
- 语法侧**同时**接受 PG 形态 `INTERVAL '1 day'` 与 MySQL 形态 `INTERVAL n unit`，二者归一到同一模板；
- 文档标注 MySQL 形态为 deprecated。

### BUG-10（P1）`CROSS JOIN` **被静默误解析** —— 无错误信号，产出非法 SQL

**现象** `[实测]`

```
parse("select count(*) from a cross join b")
  → "select count(*) from a as cross join b"        ← 非法 SQL，无任何错误
parse("select x.a from a cross join b on a.id=b.id")
  → "select x.a from a as cross join b on a.id = b.id"
```

⚠️ **v2 把本项误判为「解析失败（用户有明确信号）」并据此给出「实测 FAIL」的证据 —— 该证据是错的**。实测显示 `cross join` **几乎总是解析成功**（无论投影是 `count(*)` 还是 `x.a`），ANTLR 把 `cross` 当成 `a` 的别名吞掉，随后的 `join` 正常绑定为内连接。**即：这不是「有时解析失败」，而是「总是静默产出错误语义」** —— 比 v2 描述的更严重。

**根因** `[代码]`

- `CROSS` 是 parser keyword（`Keyword.g4:209-211`），但**同时**在 `unreservedWord_`（`BaseRule.g4:93`）里 —— 所以它被解析成 `a` 的别名，紧接着 `join` 才作为 JOIN 关键字出现。
- `joinType_`（`DMLStatement.g4:162-163`）只有 `innerJoin_ | leftJoin_ | rightJoin_ | fullJoin_`，其中 `innerJoin_: INNER? JOIN` 无法消费已被吞掉作别名的 `CROSS`。
- `NATURAL` 行为不同：实测 `select count(*) from a natural join b` **确实失败**，因为 `NATURAL` 不在 `unreservedWord_`。

**修法**：**唯一正确修法是给 `joinType_` 增加 `crossJoin_: CROSS JOIN`**。⚠️ v1 的备选方案"把 `CROSS` 加入 `unreservedWord_`"是**空操作**（它已经在里面）；若按 v1 的反向操作"把它移出 `unreservedWord_`"，`cross` 将无法再作列名 —— 而 `CROSS` 在 h2/mssql/mariadb/mysql/postgresql/tdengine **六个方言**的保留字列表里都有，**这是回归**。

### BUG-14（P1）集合下标访问静默产出垃圾 AST

**现象** `[实测]`

```
parse("select o.roles[1].x() from t o")  → "select o.roles 1 from t as o"   ← 非法 SQL
parse("select o.roles[] from t o")       → 解析失败
parse("select o.roles[order by a].x()")  → 解析失败
```

**根因** `[代码]` `BaseRule.g4:196-199` 的 `sqlCollectionAccessExpr` 规则存在且 `_EqlASTBuildVisitor` 会填 `collection`/`where`/`orderBy`/`collFuncName`/`collFuncArgs`，但 `AstToEqlGenerator.visitSqlCollectionAccessExpr` 的打印路径产出 `collection <index>` 而**丢弃**了 `.` 与集合函数调用 —— 对普通实体集合属性（本该走 prop-join 的路径）而言这是静默损坏。

**影响面**：`SqlCollectionAccessExpr` 是 `EqlAST.xjava` 声明的正式 AST 节点，也是 v2 §1.1 曾误列为"可用"的 Nop 扩展之一。集合操作符 `o.roles._some.x` / `_all.x`（走 `CollectionOperatorTransformer`）不受影响。

**修法**：或补齐打印（`.` + `collFuncName(args)`），或在 `EqlTransformVisitor` 阶段对非集合实体属性显式报错（遵循"显式失败、不静默跳过"的既定裁决，见 `nop-metadata-service/SqlSelectFieldExtractor.java:121-126`）。

## B 类：解析失败或仅影响回显（用户有明确信号，改动风险低）

### BUG-6（P1）`FULL OUTER JOIN` 解析失败 —— 且修语法不够

`DMLStatement.g4:171-172` 的 `fullJoin_: FULL JOIN;` 漏了 `(OUTER)?`，而 `leftJoin_`/`rightJoin_`（`:174-178`）都有。实测 `full join` OK、`full outer join` FAIL `[实测]`。

⚠️ **v1 说它是"全库通用语法"是错的**：**MySQL 与 MariaDB 完全没有 `FULL JOIN`**（官方文档的 `joined_table` 产生式只有 `INNER|CROSS` / `STRAIGHT_JOIN` / `LEFT|RIGHT [OUTER]` / `NATURAL`）。所以修语法**不够**，还必须接线 `supportFullJoin`（当前无 accessor、零消费），对 MySQL/MariaDB 编译期报错或改写为 `LEFT JOIN + UNION` 反连接。

### BUG-7（P1）`date`/`time`/`timestamp` 字面量小写时抛 `ClassCastException`

```
expr("o.createTime > DATE '2020-01-01'")  → OK
expr("o.createTime > date '2020-01-01'")  → ClassCastException: CommonToken cannot be cast to ParserRuleContext
expr("o.createTime > Date '2020-01-01'")  → 同上
expr("o.createTime > Time '10:00:00'")    → 同上
```
`[实测]`

**根因链** `[代码]`

1. `Alphabet.g4:22-47` 的 `A: [Aa]` 等 fragment 大小写不敏感 → 小写 `date` 也命中 `DATE` token，token 文本为 `"date"`。
2. `EqlASTBuildVisitor.SqlDateTimeLiteral_type`（`:177-184`）交给 `SqlDateTimeType.fromText(value)`。
3. `SqlDateTimeType.fromText`（`:32-40`）与**大写** `"DATE"/"TIME"/"TIMESTAMP"` 做 `equals` → 返回 `null`。
4. 错误分支 `.loc(loc((ParserRuleContext) node))` → `node` 实际是 `Token`（`_EqlASTBuildVisitor.java:585` 传 `ctx.type`，`EqlParser.java:4411` 声明 `public Token type`）→ **CCE**。

小写是 PostgreSQL 文档与 SQL 生态的标准书写方式。修法：`toUpperCase(Locale.ROOT)` 后匹配，并修掉错误分支的强转。

⚠️ v1 称"DB 层完全不参与"不准确 —— `AstToSqlGenerator.java:399` 有 `visitSqlDateTimeLiteral` 覆写并调用 `dialect.getDateLiteral/getTimeLiteral/getTimestampLiteral`。CCE 发生在更早的解析期，症状仍与方言无关，但证据要改。

### BUG-8（P1）`cast(x as boolean)` 解析失败

实测小写与大写 `boolean` 均 FAIL `[实测]`。`BOOLEAN` 是 parser keyword（`Keyword.g4:353`），但既不在 `dataTypeName_`（`BaseRule.g4:340-343`）也不在 `unreservedWord_`（`:74-93`）。修法：`dataTypeName_` 加 `BOOLEAN`（它是标准保留字，不该当普通标识符用）。

### BUG-9（P1）`ORDER BY <序号>` 被当作常量表达式 —— 静默语义错误

`sqlOrderByItem: expr=sqlExpr asc=(ASC|DESC)?`（`BaseRule.g4:327-329`），`sqlExpr` 可达 `sqlNumberLiteral`。实测 `order by 1 desc` 解析并回显为 `order by 1 desc` `[实测]`。

**影响**：MySQL / PostgreSQL / Oracle / MSSQL / H2 / DB2 **全部**把 `ORDER BY 1` 解释为"按第 1 个投影列排序"。EQL 会静默接受并产出语义错误的 SQL。

v1 推荐"收紧"是对的，但需注意三点：

- `ORDER BY ?`（参数标记）同样能解析且同样错误，只挡 `SqlNumberLiteral` 不够；
- 需要新增 `OrmEqlErrors` 错误码；
- **这是产品决策而非 bug 修复**，不应与纯修复同批次（见 Part 7）。

### BUG-11（P2）语法接受、AST 校验必拒的 `CAST` 目标类型

`dataTypeName_`（`BaseRule.g4:340-343`）显式列出 `CHARACTER VARYING` / `NATIONAL CHAR` / `BIT VARYING` / `DOUBLE PRECISION` / `TIME WITH TIME ZONE` / `TIMESTAMP WITH TIME ZONE`，但 `EqlASTBuildVisitor.SqlTypeExpr_name`（`:261-267`）只接受 `StdSqlType.fromStdName(name)`。实测 6 个（含 v1 漏掉的 `timestamp with time zone`）全部 `invalid-sql-type` `[实测]`。

同时实测被拒：`text`、`number(10,2)`、`bool`、`uuid`、`money`、`timestamptz`；被接受：`int`/`numeric`/`decimal`/`char`/`clob`/`varbinary`/`json`。

**关于时区类型的重要修正**：`StdSqlType.java:38,42` **已经有** `TIME_WITH_LOCAL_TIME_ZONE` / `TIMESTAMP_WITH_LOCAL_TIME_ZONE`，**但被注释掉了**。所以差距只是 `dataTypeName_` 拼写与枚举不一致 + 枚举被注释，**不是跨模块公共 API 新增**（v1 把它列为 plan-first 风险并列为 Open Question 是高估了成本）。

**修法**

1. **收窄语法**到 `StdSqlType` 名字 + 显式别名规则，别名映射只写一处（`StdSqlType.fromStdName` 的 `stdNameMap`，`StdSqlType.java:146,152-161`）；
2. ⚠️ 注意 `stdNameMap` **同时被 DDL 类型映射使用**，加 `TEXT→CLOB` 别名会改变所有 ORM 模型 `<column stdSqlType="text">` 的解析结果 → 属 `nop-kernel` 跨模块公共 API（plan-first）；
3. 时区类型若要落地，先取消 `StdSqlType.java:38,42` 的注释并同步 `dataTypeName_` 拼写。

## C 类：文档 / 配置失真

### BUG-12（P2）`docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 三处失真

| 文档位置 | 文档说法 | 实测 |
|---------|---------|------|
| `:91` | 「`supportNullsFirst`：不支持时 ORM 用 `IF(ISNULL(f),0,1)` 模拟」 | ❌ 无 accessor、无任何模拟实现。`grep -i isnull` 全库只命中 xdef 的 javadoc 复制 |
| `:94` | 「`supportILike`：PostgreSQL 支持」 | ❌ PG 未注册 `ilike` 函数 → `nop.err.eql.not-support-ilike-operator`。**H2 也一样** |
| `:97` | 「`supportFullJoin`/`supportRightJoin`：部分数据库不支持」 | ❌ 标志无 accessor、零消费 |

### BUG-13（P2）14 个特性标志"零消费"的表述需修正

v1 说"14 个标志无 accessor"，实际 **9 个有 accessor**（`IDialect.java:75,77,79,81,83,85,94,96,98`）。准确表述与处理：

| 处理 | 标志 |
|------|------|
| **接线**（有真实方言语义） | `supportFullJoin`（MySQL/MariaDB 无）、`supportNullsFirst`（或直接用 `defaultNullsFirst`）、`supportUpdateFromJoin`、`supportTruncateTable`、`supportDeleteFromJoin`、`supportRightJoin`、`supportUpdateTableAlias` / `supportDeleteTableAlias`、`supportRowValueConstructor`、`supportILike`（或统一走函数表） |
| **先补消费者再定** | `supportOutputForUpdate`（MSSQL `OUTPUT`） |
| **确认无消费者后从 xdef 移除** | `supportSavePoint`、`supportTransaction`、`supportSequence`（ORM 本身不用序列；ID 生成走 `OrmEntityIdGenerator`） |
| **不要删除** | `supportNullsFirst` —— `duckdb/h2/oracle/postgresql` 四个 XML 实际配置了它。"零消费" ≠ "可删除"，删 xdef 属性会让现有 XML 校验失败 |

---

# Part 3 — 建议补充的 EQL 语法

## 筛选标准（比 v1 更严）

一条语法进入 EQL，必须同时满足：

1. **有标准或准标准定义**，**或** 在 ≥5 个**真实配置的**方言中语义等价原生支持 —— ⚠️ 按**覆写点**计数而非方言名（`db2` 是纯 stub、`es` 是伪方言、`mariadb→mysql`、`duckdb→postgresql` 会让"5 个方言"变成"1 个基线 + 1 个覆写 + 5 个别名"）；
2. **能无损 lowering 到 EQL 现有机制**（详见 Part 6 的四路机制表）；
3. 归一化差异可用**一个既有特性标志**或**一个函数模板**表达，而不是需要一套新的 SQL 改写引擎；
4. 不与 Nop 现有已归一化的机制重复（尤其 UPSERT —— 见 G6）。

## G1（P0）SQL-92 标准函数式语法 —— 语法里已有被注释掉的正确先例

`BaseRule.g4:246-276` 保留着**被注释掉的、形状正确的** `positionFunction` / `substringFunction` / `extractFunction` / `trimFunction`。这比任何成本估算都更能说明问题：**这些规则曾被写出来然后被回退**。落地前必须先查 git 历史回答"为什么被注释掉" —— 这是 v1 遗漏的关键 Open Question。

| 建议 EQL 语法 | Lowering 目标 | 现有机制 | 跨库覆盖 |
|---|---|---|---|
| `EXTRACT(<field> FROM <expr>)` | `year()` / `month()` / … 函数 | ✅ `year` 已有（`default.dialect.xml:255`；PG/Oracle 覆写为 `extract(YEAR from {0})`，MSSQL 覆写为 `datepart(year,{0})`） | MySQL8/PG/Oracle/H2/DB2/DM/DuckDB/MariaDB 原生；MSSQL ⭕；SQLite ⭕ |
| `SUBSTRING(x FROM a [FOR b])` | `substr(x, a[, b])` | ✅ `substr` 是 `<native argTypes="VARCHAR,INTEGER,INTEGER" minArgCount="2" maxArgCount="3"/>`（`default.dialect.xml:194-195`），2/3 元多态已在 `<native>` 上支持 | 全库 |
| `POSITION(x IN y)` | `instr(y, x)`（**调用点换序，不在模板里换序**） | ✅ `TemplateSQLFunction` 按位置解析 `{n}`，**可换序**；活证据：`mssql.dialect.xml:174-176` `charindex({1},{0})`。PG→`strpos`、DuckDB→`strpos` | 全库 |
| `TRIM(BOTH c FROM x)` | ⚠️ **不是纯模板可解** | `trim`/`ltrim`/`rtrim` 是 `<native argTypes="VARCHAR"/>`（`default.dialect.xml:108-110`，`ltrim`/`rtrim` 在 `:109-110`）；⚠️ XML **没有** `minArgCount`/`maxArgCount` 属性，元数 1 来自 `NativeSQLFunction` 从 `argTypes.size()` 推导。`TRIM(BOTH ' ' FROM x)` → `trim(x)`（丢参，OK）；`TRIM('#' FROM x)` → 需 2 元 `trim(x,'#')`，**且 PostgreSQL 没有 2 元 `trim`** | 各库均支持标准三段式 `trim(both c from x)` |
| `TRIM(LEADING [c] FROM x)` / `TRIM(TRAILING …)` | `ltrim` / `rtrim` | 同上，`default.dialect.xml:109-110` | 全库 |

**结论**：G1 应拆成两档 —— `POSITION` / `SUBSTRING` / `EXTRACT`（`year` 及已有函数）是**纯语法改动**；`TRIM(… c FROM x)` 需要 `<native>` 放宽元数 **或** 逐方言定义标准三段式模板（`trim(both {1} from {0})`），后者更稳（PG 无 2 元 `trim`）。

**配套必需**：`<functions>` 表**完全没有** `month` / `day` / `hour` / `minute` / `second` / `quarter` / `dayofweek` / `weekofyear`。而且实测显示 **`day()` / `hour()` / `second()` / `quarter()` 连解析都失败**（这些是 keyword token 但不在 `unreservedWord_`），`month()` / `year()` 因在 `unreservedWord_` 里才能解析 —— 所以这 5 个还需改 `unreservedWord_`，不是纯 XML 改动。

**另一处 v1 误判**：`extract(y, d)`（逗号形式）实测能解析，但它落到 `default` 的普通 `extract` native（**所有库语义都错**），而 `mssql.dialect.xml:194` 把 `extract` `x:override="remove"` 了 → MSSQL 上直接 `unknown-function`。`extract(YEAR from {0})` 那个模板属于 **`year`**，不属于 `extract`。

## G2（P0/P1）分页、排序与 NULL 序

方言层分页归一化**已完整实现**，但语法只认 `LIMIT n [OFFSET m]`，造成双向不对称：

| 方向 | 状态 |
|------|------|
| EQL 输入 → 方言输出 | ✅ `limit 5 offset 2` → MySQL `LIMIT ? OFFSET ?` / Oracle `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY` |
| 方言输出 → EQL 重新解析 | ❌ `OFFSET n ROWS FETCH NEXT m ROWS ONLY` **解析失败** `[实测]` |

| 建议 EQL 语法 | Lowering | 说明 |
|---|---|---|
| `FETCH FIRST n ROWS ONLY` | 走 `IPaginationHandler` | SQL:2008 标准。⚠️ `SqlLimit` 只存 `limit`/`offset` 两个表达式，**必须给 `SqlLimit` 增加一个"语法形态"字段**，否则 `LIMIT n` 与 `FETCH FIRST n` 在 AST 里不可区分 → 这是 AST 改动而非纯语法改动 |
| `OFFSET n ROWS FETCH NEXT m ROWS ONLY` | 同上 | Oracle 官方风格 |
| `ORDER BY <expr> NULLS FIRST\|LAST` | 见下 | `SqlOrderByItem.nullsFirst` 已预留 |
| `ORDER BY <expr> COLLATE <name>` | 逐方言 | ⚠️ MySQL 的 `x COLLATE y` 与 PG/Oracle 的 `ORDER BY x COLLATE y` **子句位置不同**；MSSQL 的 collation 名是库级作用域，不可移植 → **降级到 P3，本轮不给批次**（`SqlOrderByItem.collate` 字段保持预留，等最低支持版本确定后再评估） |
| `SELECT TOP n` / `LIMIT off,lim` / `ORDER BY ALL` | — | **不采纳**（见 Part 5） |

### `NULLS FIRST/LAST` —— 修正 v1 的方案

v1 建议"接线 `supportNullsFirst` + `CASE WHEN` 模拟"。**更优且更小的方案**：

- **`DialectModel.defaultNullsFirst` 已经在 7 个方言 XML 里配好了**（`mysql/mssql/dm = true`；`oracle/postgresql/duckdb/tdengine = false`），只是**零 Java 消费方**；
- 加上 `NULLS FIRST/LAST` 语法 + 为已预留的 `SqlOrderByItem.nullsFirst` 补打印，就能让**"未指定方向"这一情形本身也变得可移植**（EQL 在生成时按方言默认值显式写出 NULL 序），而不仅是让显式指定可用；
- 不需要新增第二个描述同一事实的标志。

`AstToEqlGenerator.java:579-585` 已经在打印 `nulls first`，但语法解析不进来、特性标志无 accessor —— 整条路径是死的。

**为什么这是最高风险项**：NULL 排序默认值**分 4 类**（见 Part 4.2），同一条 EQL 在不同库上**行数一样、返回顺序不同**。这是"能跑但结果错"的典型跨库陷阱，比解析失败危害大。

## G3（P0）NULL 三值语义谓词

### G3-1 `IS [NOT] DISTINCT FROM`

| EQL | MySQL8 | MariaDB | PG | Oracle（含 23ai） | MSSQL | H2 | DB2 | DM8 | DuckDB |
|-----|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `IS [NOT] DISTINCT FROM` | ⭕ `<=>` | ⭕ `<=>` | ✅ | ❌ | ✅ **2022+** / ❌ 2019 | ✅ | ✅ | ⭕ | ✅ |

**lowering**：支持的库原样；MySQL/MariaDB → `a <=> b`；Oracle/MSSQL 2019 → `(a = b OR (a IS NULL AND b IS NULL))`。

⚠️ **两处实现约束**（v1 未识别）：

1. Oracle/MSSQL 的 lowering 是**三节点表达式树改写**，`AstToSqlGenerator` 是单遍打印器，**没有改写钩子**。必须走 `IEqlAstTransformer`（`EqlCompiler.java:56-65` 在 `EqlTransformVisitor` **之前**运行 transformer —— 这正是正确的插入点）。唯一先例是 `CollectionOperatorTransformer`（**711 行**）。
2. `<=>` 必须作为**原始 SQL 文本**输出，但 `AstToSqlGenerator` 没有 raw-SQL 逃生口：`SqlRegularFunction("<=>", [a,b])` 会走 `dialect.getFunction("<=>")` → `null` → `unknown-function`。可行做法是在 `default` 里注册 `<native name="safe_eq" realName="<=>">` 并在其它方言 `remove`，复用既有 `realName` 机制（`DialectImpl.java:127-144`）—— **v1 说"复用 `SAFE_EQ_` token"是行不通的**。

### G3-2 `IS [NOT] TRUE / FALSE / UNKNOWN`

现状 `IS` 分支只接 `NULL`（`BaseRule.g4:141`），实测 5 种形式全 FAIL `[实测]`。

| EQL | MySQL8 | MariaDB | PG | Oracle | MSSQL | H2 | DuckDB |
|-----|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `IS [NOT] TRUE/FALSE/UNKNOWN` | ✅ | ✅ | ✅ | ✅ **23ai+** / ❌ ≤19c | ⭕ | ✅ | ✅ |

⚠️ **只有一半可 lowering**：`IS TRUE` → `a = 1` 语义一致；但 **`IS NOT TRUE` 在 `a IS NULL` 时为 TRUE，而 `a <> 0` 为 NULL** —— `IS NOT TRUE` / `IS NOT FALSE` **无法**降到二元比较。v1 说"成本极低：语法 5 行 + generator 10 行"低估了一半变体的成本。

### G3-3 `<=>` / `==` 处置

- `DEQ_`(`==`)：**建议从 `Symbol.g4` 删除**，但见 Part 8 的 token 重编号风险。
- `SAFE_EQ_`(`<=>`)：**保留 token**（G3-1 的 MySQL lowering 目标需要它可词法化），但语法层不暴露。
- `CONCAT_`(`||`)：**建议删除 token**（见 Part 5）。

## G4（P1 → 批次 S-2/S-3/S-4）JOIN 家族

| 建议 | 处理 |
|------|------|
| `FULL OUTER JOIN` | 修语法（BUG-6）+ **接线 `supportFullJoin`**，MySQL/MariaDB 编译期报错或改写 |
| `CROSS JOIN` | 修语法（BUG-10），映射到 `SqlJoinType.JOIN` |
| `LATERAL` | **修输出（BUG-2）+ 接线 `supportLateral`**（MariaDB / DB2<9.7 / TDengine / es 无；Oracle 需 12.1+） |
| `NATURAL JOIN` / `USING(col)` / `OUTER APPLY` | **不采纳**（见 Part 5） |

## G5（P1/P2）分析型语法

| 建议 | 现状 | Lowering | 覆盖（关键差异） |
|------|------|---------|------------------|
| `GROUP BY ROLLUP(a,b)` | **已能解析**（实测，视为普通函数调用），编译期 `unknown-function` | **只需注册函数** | PG/Oracle/MSSQL/H2/DB2/DM/DuckDB 原生 `ROLLUP`；**MySQL/MariaDB 只有 `WITH ROLLUP` 修饰符形式（8.0.12+）** |
| `GROUP BY CUBE(a,b)` | 同上 | 注册函数 | 同上；**MySQL/MariaDB 无** |
| `GROUP BY GROUPING SETS(...)` | 解析失败 `[实测]` | 注册 + 语法 | 同上；**MySQL/MariaDB 无** |
| `GROUPING(a)` | 解析成功（普通函数），编译期 `unknown-function` | 注册函数 | 同上 |
| `over([PARTITION BY] [ORDER BY] [frame])` | **`OVER (ORDER BY …)` 缺 partition 解析失败** `[实测]`（`BaseRule.g4:221-225` 强制两个子句）；frame 失败 | 放宽语法 + **AST 增字段** | MySQL8/PG/Oracle/MSSQL/H2/DB2/DM/DuckDB/MariaDB10.3+ ✅；**MySQL 5.7 无窗口函数** |
| `WINDOW w AS (…)` + `OVER w` | 解析失败 | AST 增字段 | 同上，**MSSQL 无** |
| `count(*) FILTER (WHERE p)` | 解析失败 | ⚠️ 见下 | PG9.4+/DuckDB/H2/SQLite3.30+ ✅ |
| `QUALIFY` / `DISTINCT ON` | — | — | **不采纳**（见 Part 5） |

⚠️ **`count(*) FILTER` 不是"lowering 简单"**（v1 判断有误），有三个独立障碍：

1. **聚合完全绕过 `<functions>` 表** —— `sqlIdentifier_agg_`（`BaseRule.g4:235-237`）把聚合名硬编码为 `MAX|MIN|SUM|COUNT|AVG`；`EqlTransformVisitor` 只覆写了 `visitSqlRegularFunction`（`:1371`），**没有** `visitSqlAggregateFunction`；`AstToEqlGenerator.visitSqlAggregateFunction` 原样打印 `name(args)`；
2. `SqlAggregateFunction`（`EqlAST.xjava:431-440`）**没有 `filter` 字段** → 需改 xjava → 重生成 72 个 `_gen` 文件 + 1653 行 `_EqlASTBuildVisitor`；
3. **语义不等价**：`sum(CASE WHEN p THEN 1 ELSE 0 END)` 在空分组上返回 `NULL`，而 `count(*) FILTER (WHERE p)` 返回 `0`。对 `count` 而言这不是忠实 lowering。

**建议**：**改为文档引导** —— 告诉用户直接写 `sum(case when p then 1 else 0 end)`（对 `sum` 场景完全够用，零机制成本），放弃 `FILTER`。

**同时修方言配置**：`postgresql.dialect.xml` / `duckdb.dialect.xml` 加继承 `window-expr-support.dialect.xml`（**两行**）；`mysql5.7.dialect.xml` 需**移除**该继承（MySQL 5.7 无窗口函数）—— 所以这是"3 处编辑 + 1 个测试"，不是 v1 说的 1 行。

## G6（P1，但建议**不做语法**，改做 DAO 原语）—— UPSERT

这是本次调研中方言层**完全空白、但业务上绕不开**的能力。当前状态：

- EQL 语法没有任何 upsert 规则（`sqlInsert` 只有 `VALUES` / `SELECT`）；
- `dialect.xdef:32` 有 `upsertHandler` 字段，`_DialectModel.java:746` 有 `getUpsertHandler()`，但**零消费**；
- ⚠️ **对应 SPI 已于 2026-08-22 被刻意删除**：`ai-dev/audits/check/nop-dao.md:410-415` 记录删除 `MysqlUpsertHandler` + `IUpsertHandler`，理由是"唯一实现恒返回 null，全仓库零调用方、零 xml/xdef 引用，属误导性死代码"，并注明"**未来实现 MySQL upsert 时应连同集成测试重新引入**"；
- 仓库里有**两个**模块私有 upsert 枚举：`nop-stream-runtime/.../JdbcCheckpointStorage.java:800`（POSTGRESQL/MYSQL/H2/GENERIC）与 `nop-stream-connector-jdbc/.../JdbcTwoPhaseCommitSink.java:166`（MYSQL/POSTGRESQL/H2 + `DmlMode.{UPSERT, UPSERT_DELETE}`）；
- ⚠️ **冲突检测机制其实早已存在且被广泛使用**：`DaoErrors.java:124` `ERR_SQL_DUPLICATE_KEY` 已按方言在 `<errorCodes>` 里归一（PG `23505`、DB2 `-803`、DM `-6013/-6015`、TDengine `1062`），由 `DialectSQLExceptionTranslator.java:70,105-160` 翻译，并被**至少 9 个生产调用点**消费以实现"插入、遇重复键则回退更新"：`DbSmsCodeStore.java:92`、`DbEmailCodeStore.java:85`、`DbSendCodeRateLimiter.java:127,149,162`、`DbLoginAttemptStore.java:74,107`、`SysDaoResourceLockManager.java:197`、`SysSequenceGenerator.java:173`、`DaoBatchStateStore.java:295`、`RetryEngineImpl.java:240`。

**v1 的建议（标准 `MERGE` + 新增 `IUpsertHandler`）被判定为不合适**，理由：

1. `MERGE` 的**分支语义并不一致**（Oracle 无 `WHEN MATCHED THEN DELETE` 分支、PG 的 `BY SOURCE` 需 17），"一个模板覆盖 5~7 个库"的说法不成立；
2. `postgresql.dialect.xml` **没有版本门** —— 要么新增 `postgresql15` 方言 + selector YAML，要么加运行时版本检查，v1 成本表里都没有；
3. MSSQL 的 `MERGE` 需要语句终止 `;`，而 `Eql.g4:26` 让 `SEMI_` 可选 → 生成器要按方言补；
4. `MERGE` 会成为**第 4 套 upsert 机制**（除上述 9 个应用层调用点、`OrmSessionImpl.saveOrUpdate` 的脏检查路径、以及两个 nop-stream 私有枚举之外），且没有任何迁移路径；
5. 与 Nop「模型优先 / 可逆计算」立场冲突：`MERGE` 是一种**不可从 ORM 模型推导**的语句形式。正确归属是 `SqlLibManager` / `SqlItemModel`（`<sql>` 片段），或 DAO 层的批量原语。

**建议**：把 UPSERT 做成 **DAO 层批量 upsert 原语**，直接复用已归一化的 `ERR_SQL_DUPLICATE_KEY`，以 `JdbcCheckpointStorage` 为参考实现，并把两个 nop-stream 私有枚举迁移过去。这以约 1/4 的成本、零语法/codegen 抖动交付业务价值。若后续确实需要语句形态，`MERGE` 是正确的 SQL 文本，但应进 `<sql>` 片段而非 `sqlStatement`。

## G7（P1）类型系统与 `CAST` 目标类型

EQL 现在把 CAST 目标限制在 `StdSqlType` 闭集内（`EqlASTBuildVisitor.java:261-267`），由 `dialect.stdToNativeSqlType` 映射 —— **这个方向是对的，不要改**。要做的是补齐闭集 + 对齐语法（BUG-8 / BUG-11）：

| 需求 | 现状 | 建议 |
|------|------|------|
| `BOOLEAN` | ❌ 解析失败（BUG-8） | 加进 `dataTypeName_` |
| `NUMBER(p,s)` | ❌ | 别名 → `NUMERIC` |
| `TEXT` | ❌ | 别名 → `CLOB`（⚠️ 见下方 plan-first 提示） |
| `BOOL` | ❌ | 别名 → `BOOLEAN` |
| `UUID` | ❌ | 新增 `StdSqlType.UUID` + 各方言映射（PG/DuckDB/H2/MariaDB10.7+ 原生；MySQL→`CHAR(36)`；Oracle→`RAW(16)`；MSSQL→`uniqueidentifier`；DB2→`CHAR(16) FOR BIT DATA`） |
| `DOUBLE PRECISION` / `CHARACTER VARYING` / `NATIONAL CHAR` / `BIT VARYING` | ❌ 语法接受、校验必拒 | 从 `dataTypeName_` 移除（`FLOAT`/`VARCHAR`/`CHAR` 已覆盖） |
| `TIME/TIMESTAMP WITH TIME ZONE` | ❌ 校验必拒 | **`StdSqlType.java:38,42` 已有被注释的对应成员** —— 取消注释 + 同步语法拼写即可，**不是跨模块 API 新增** |
| `MONEY` | ❌ | **不收**（各方言语义不一致） |
| `SERIAL` / `AUTO_INCREMENT` / `IDENTITY` | ❌ 且**不该收** | 自增是 DDL/插入语义，走 ORM 模型 + 方言 DDL 模板 |

⚠️ **plan-first 提示**：别名写入 `StdSqlType.fromStdName` 的 `stdNameMap`（`StdSqlType.java:146,152-161`），而这张表**同时被 DDL 类型映射使用**。加 `TEXT→CLOB` 别名会改变所有现有与未来 ORM 模型 `<column stdSqlType="text">` 的解析结果 → 属 `nop-kernel/nop-commons` 跨模块公共 API（AGENTS.md Protected Areas），需 owner doc + 迁移方案。

## G8（P0）归一化日期时间函数族

这是跨库差异最大、当前覆盖最差的领域。现状：

- `<functions>` 里时间相关的只有 `current_date` / `current_timestamp` / `now` / `date` / `year` / `extract`（H2 另有 `dateadd`/`datediff`），**`month`/`day`/`hour`/`minute`/`second`/`quarter`/`dayofweek`/`weekofyear`/`date_add`/`dateDiff`/`date_trunc`/`datepart`/`last_day`/`regexp_like`/`rollup`/`cube`/`grouping`/`greatest`/`least`/`listagg`/`group_concat`/`string_agg`/`atan` 全部不存在**；
- `day()`/`hour()`/`second()`/`quarter()` **连解析都失败**（关键字遮蔽）；
- 裸 `INTERVAL` 原样输出（BUG-5）。

**建议（P0，最低成本的正确做法）**：提供一组归一化函数，让用户不必触碰方言语法：

```
dateAdd(date, n, unit)        dateDiff(d1, d2, unit)      dateTrunc(date, unit)
month(d)  day(d)  quarter(d)  hour(d)  minute(d)  second(d)
dayOfWeek(d)   weekOfYear(d)   lastDay(d)
greatest(a,b,…)   least(a,b,…)                ← ⚠️ 见 4.2b：SQLite 永无；MSSQL 仅 2022+；NULL 语义分 3 类
stringAgg(expr, sep)                          ← MySQL/MariaDB GROUP_CONCAT(4.1+) / PG string_agg(9.0+，无 LISTAGG)
                                                Oracle LISTAGG(11gR2+) / H2 LISTAGG / DB2 LISTAGG(11.1+)
                                                DuckDB listagg 别名 / MSSQL STRING_AGG(2012+, compat≥110)
regexpLike(str, pattern)                      ← MySQL·MariaDB·PG·Oracle·H2·DB2(11.1+ 内置 REGEXP_LIKE)·DuckDB
                                                / MSSQL 2022 及更早降级为 LIKE/PATINDEX（精度损失需文档标注），2025+ 原生
lastDay(d)
```

⚠️ **`greatest` / `least`：语法已通，但**不是**零方言分歧**。实测 `greatest(a,b)` **已能解析**（普通函数调用），只缺函数注册 —— 这一半成立。但另一半不成立：SQLite **永无**该函数（只有 `min(a,b,…)`/`max(a,b,…)` 标量形式）；MSSQL 仅 **2022+**；**NULL 语义分 3 类**（见 4.2b），需要一次产品决策。`sqlIdentifier_agg_`（`BaseRule.g4:235-237`）把聚合函数硬编码为 5 个 token，所以"伪装成 `max(a,b)`"不可行（`max` 多参在标准里也不是聚合语义）。**v2 把本项列为"性价比最高的单项"是错的，v3 已降级。**

⚠️ **`stringAgg` / `regexpLike` 有真实语义缺口**（详见 4.2c）：`GROUP_CONCAT` / `LISTAGG` / `STRING_AGG` 在**默认分隔符、长度上限、排序语义**上均不同；`regexpLike` 在 MSSQL ≤2022 只能降级为 `LIKE`/`PATINDEX`（正则语义大幅损失，2025+ 才有原生 `REGEXP_LIKE`）。两者都应在文档里明确标注降级边界。

## G9（P2 → 批次 S-11）表源扩展

| 建议 | 处置 |
|------|------|
| `FROM (VALUES (..),(..)) AS t(a)` | Oracle 需 23ai+（否则 `SELECT … UNION ALL` 改写）；MySQL 需 8.0.19+ 的 `VALUES ROW(..)`；**MariaDB 完全无表值构造器**。且**必须区分** SQL 标准的 `<table value constructor>`（作为独立查询）与 `<row value constructor>`（`(a,b)` 用于比较）—— 后者**已可解析**（实测 `(a,b) > (?,?)` OK），缺的只是 `supportRowValueConstructor` 的能力门控 |
| `JSON_TABLE` / `json_each` / `OPENJSON` | **不采纳**（各库形态完全不同，无公共子集）。但注意 `StdSqlType.JSON` 已存在且 `default.dialect.xml:13` 已接 `jsonTypeHandler` —— **类型侧已可移植（`default.dialect.xml:13` 已接 `jsonTypeHandler`），函数侧完全空白**，这是另一个值得单独立项的缺口 |
| `FROM table(fn(…))` / `TABLESAMPLE` | **不采纳** |
| `MATCH_RECOGNIZE` | **不采纳**（仅 Oracle/DuckDB） |

## G10（P2 → 批次 S-11）锁

| 建议 | 现状 | 处置 |
|------|------|------|
| `FOR UPDATE OF <table>` | 解析失败 | PG/MySQL/Oracle/MSSQL 支持度不一 |
| `FOR UPDATE NOWAIT` / `SKIP LOCKED` | 解析失败 | `SqlQuerySelect.nowait` 已预留；PG/Oracle/MSSQL/DB2 支持 |
| `LOCK IN SHARE MODE` / `FOR SHARE` | 解析失败 | MySQL/PG/Oracle 分歧大 |

**前置**：必须先修 BUG-3（走 `dialect.getForUpdateSql`），否则新增语法只会让硬编码路径更长。

## G11（P0，因 `IN` 列表上限）批量/分页抽象（超越语法本身的可移植性抽象）

v1 完全没覆盖这块，而它们对跨库兼容性的影响大于多数语法项：

| 缺口 | 证据 | 风险 |
|------|------|------|
| **`IN` 列表长度上限无处理（P0）** | `supportRowValueConstructor` 是死标志；实测 `SqlInValuesExpr` 会把 10000 个字面量全部吐进 SQL（约 6.9 万字符），无任何截断或改写 | **Oracle `IN` 硬上限 1000**、**MSSQL 参数上限 2100** → 运行时直接失败。全库 `maxInList`/`inListLimit` 零命中。**这是本报告里唯一「日常 ORM 查询就会踩到」的硬失败类别** —— v2 把它列为 P2 且未给任何批次，v3 提到 **P0** 并归 S-14 |
| **Keyset / seek 分页无抽象** | 在 `nop-persistence` 下检索 `keyset`/`seek`/`afterId` 无任何**分页**相关命中（`keySet()` 的命中都是 `Set.keySet`） | `(a,b) > (?,?)` 语法已可解析，但没有一等公民抽象 |
| **批量 DML 标志未盘点** | `supportBatchUpdate` / `supportBatchUpdateCount`（`default.dialect.xml:18`）未出现在 v1 的方言清单里 | 需补入能力矩阵 |
| **ID 生成策略未讨论** | `OrmConstants.TAG_SEQ_DEFAULT` → `OrmEntityIdGenerator.java:49` → `dialect.sequenceNextVal` | 序列名引用各库不同（`oracle.dialect.xml:114` `{sequenceName}.nextval` 不加引号 vs `postgresql.dialect.xml:101` `nextval('{sequenceName}')` 加引号），与 `escapeSQLName` / `tableNameCase` 直接交互 |
| **命名参数缺失** | `SqlParameterMarker` 只有 `?`；`Symbol.g4:30` 虽有 `COLON_ : ':'` token，但**未被任何语法规则引用** | 而 `sql-lib.xml` 的 `<arg>` 是另一套命名绑定 —— 两套参数模型从未被调和 |

---

# Part 4 — 候选语法 × 目标库支持矩阵（重建）

图例：✅ 原生 · ⭕ 需改写 · ❌ 不支持 · ⚠️ 厂商文档未声明，须实测

版本基线（用于解释 ⭕/❌ 分界）：MySQL **8.0/8.4**、MariaDB **10.11**、PostgreSQL **14/15/16/17**、Oracle **11gR2 / 12.1 / 19c / 23ai**、SQL Server **2019/2022**、H2 **2.x**、DB2 **11.5 LUW**、DM8 **V8.1**、DuckDB **1.x**、SQLite **3.45+**

> ⚠️ 全部为 `[文档]` 级证据，**未在真实引擎上执行验证**。

> ⚠️ **必须注意 3 个结构性事实**：
> 1. **`db2.dialect.xml` 是 27 行纯 stub**（只有 `dbProductNames` + `errorCodes`），`es.dialect.xml` 是 `driverClassName="xx"` 的伪方言 → 二者不应计入"覆盖数"；
> 2. 覆盖数按**覆写点**计（`mariadb→mysql`、`mysql5.7→mysql`、`duckdb→postgresql`、`postgis→postgresql`、`h2gis→h2`、`oracle-reverse→oracle`）；
> 3. **SQLite 既没有方言也没有 selector** —— 要么新增 `sqlite.dialect.xml` + selector，要么明确声明不在支持范围（它在 6+ 行上行为不同：无 `DEFAULT VALUES`、无 `TRUNCATE`、无 `FETCH FIRST`、无 `LATERAL`、无 `CUBE`、`RIGHT/FULL JOIN` 需 3.39+）。

| 候选语法 | MySQL8 | MariaDB | PG | Oracle | MSSQL | H2 | DB2 | DM8 | DuckDB | SQLite |
|---|---|---|---|---|---|---|---|---|---|---|
| `EXTRACT(f FROM d)` | ✅ | ✅ | ✅ | ✅ | ⭕ `DATEPART` | ✅ | ✅ | ✅⚠️ | ✅ | ⭕ |
| `SUBSTRING(x FROM a FOR b)` | ✅ | ✅ | ✅ | ✅ | ⭕ | ✅ | ✅ | ✅⚠️ | ✅ | ⭕ |
| `POSITION(x IN y)` | ⭕ `LOCATE`/`INSTR` | ⭕ | ⭕ `strpos` | ⭕ **仅 `INSTR`（无 `POSITION`）** | ⭕ `CHARINDEX` | ⭕ | ⭕ `LOCATE` | ⚠️ | ⭕ | ⭕ `instr()` |
| `TRIM(BOTH c FROM x)` | ✅ | ✅ | ✅ **完整支持标准三段式** | ✅ | ✅ **2022+ 且 compat 160**；⭕ 2017+ 仅两段式；❌ ≤2016 无 `TRIM` | ✅ | ✅ | ⚠️ unverified | ✅ | ⭕ 仅 1 元 |
| `FETCH FIRST n ROWS ONLY` | ❌ | ❌ | ✅ | ✅ **12.1+**；❌ 11g | ✅ 2012+ | ✅ | ✅ | ✅⚠️ | ✅ | ❌ |
| `OFFSET n ROWS FETCH NEXT m ROWS ONLY` | ❌ | ❌ | ✅ | ✅ 12.1+ | ✅ 2012+ | ✅ | ✅ | ✅⚠️ | ✅ | ❌ |
| `ORDER BY e NULLS FIRST/LAST` | ⭕ | ⭕ **明确不支持** | ✅ | ✅ | ⭕ | ✅ | ✅ **11.5 原生** | ✅⚠️ | ✅ | ✅ 3.30+ |
| `ORDER BY e COLLATE n` | ⭕ 位置不同 | ⭕ | ✅ | ✅ | ✅ | ✅ | ⭕ | ⭕⚠️ | ✅ | ❌ |
| `a IS DISTINCT FROM b` | ⭕ `<=>` | ⭕ `<=>` | ✅ | ❌ **含 23ai** | ✅ **2022+ 引擎门，无 compat 门**；❌ 2019 | ✅ | ✅ 11.1+ | ⚠️ | ✅ | ✅ 3.39+ |
| `a IS [NOT] TRUE/FALSE/UNKNOWN` | ✅ | ✅ | ✅ | ✅ 23ai+；❌ ≤19c | ⭕ | ✅ | ⭕ 无 bool 类型 | ⭕ | ✅ | ✅ |
| `FULL OUTER JOIN` | ❌ **完全没有** | ❌ **完全没有** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅⚠️ | ✅ | ✅ 3.39+ |
| `CROSS JOIN` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅⚠️ | ✅ | ✅ |
| `JOIN .. USING(c)` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅⚠️ | ✅ | ✅ |
| `LATERAL` | ✅ 8.0.14+ | ❌ **明确不支持** | ✅ 9.3+ | ✅ 12.1+；❌ 11g | ⭕ `CROSS/OUTER APPLY` | ⭕ 无关键字 | ✅ 9.7+ | ⭕⚠️ | ✅ | ❌ |
| `GROUP BY ROLLUP` | ⭕ 8.4.3+ 原生 `ROLLUP(...)`；⭕ 8.0.12–8.4.2 仅 `WITH ROLLUP` 修饰符 | ⭕ 仅 `WITH ROLLUP` | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ | ❌ |
| `GROUP BY CUBE` / `GROUPING SETS` / `GROUPING()` | ⭕ **8.4.3+**；❌ 更早 | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ | ❌ |
| 窗口 `OVER(...)` + frame | ✅ 8.0.2+；❌ 5.7 | ✅ 10.3+ | ✅ | ✅ | ✅ 2005+ | ✅ | ✅ | ✅⚠️ | ✅ | ✅ 3.25+ |
| `WINDOW w AS(..)` + `OVER w` | ✅ | ❌ | ✅ | ✅ | ❌ | ✅ | ✅ | ⚠️ unverified | ✅ | ✅ **3.25+**（窗口链接 3.28+） |
| `count(*) FILTER (WHERE p)` | ⭕ | ⭕ | ✅ 9.4+ | ⭕ | ⭕ | ✅ | ⭕ | ⭕ | ✅ | ✅ 3.30+ |
| `DISTINCT ON (a)` | ❌ | ❌ | ✅ | ❌ 23ai 前 | ❌ | ✅ **也支持** | ❌ | ❌ | ✅ | ❌ |
| `MERGE INTO` | ⭕ `ON DUPLICATE KEY` | ⭕ `REPLACE` | ✅ **15+**；`BY SOURCE` 需 17 | ✅ 9i+ **无 `WHEN MATCHED THEN DELETE` 分支** | ✅ 2008+ **需终止 `;`** | ✅ UPDATE+INSERT 分支 | ✅ **四臂 + `SIGNAL`**（`ELSE IGNORE` 需 11.1+） | ⚠️ | ✅ **1.4+** | ❌ |
| `INSERT .. DEFAULT VALUES` | ❌ | ⭕ `VALUES ()` | ✅ | ❌ **无此语法** | ✅ | ✅ | ✅ | ⭕⚠️ | ✅ | ❌ |
| `INSERT/DELETE .. RETURNING`（结果集） | ❌ **所有版本都没有** | ✅ 10.5+（UPDATE 需 13.0，尚 RC） | ✅ | ⭕ **仅 `RETURNING .. INTO` 宿主变量**（23ai 起可用于静态 DML，仍是 INTO） | ⭕ `OUTPUT` | ✅ | ⭕ `SELECT .. FROM FINAL TABLE(..)` | ⚠️ | ✅ | ✅ 3.35+ |
| `TRUNCATE TABLE` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `SAVEPOINT` / `ROLLBACK TO SAVEPOINT` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ 3.6+ |
| `UPDATE t SET .. FROM` | ❌ 需多表 `UPDATE t1,t2 SET` | ⭕ 同 | ✅ | ✅ 23ai+；❌ ≤19c | ✅ | ✅ | ✅ | ⭕⚠️ | ✅ | ✅ 3.33+ |
| `CAST(x AS BOOLEAN)` | ❌ 无此目标类型 | ⭕ | ✅ | ✅ 23ai+；❌ ≤19c | ⭕ `bit` | ✅ | ⭕ | ⭕ | ✅ | ⭕ |
| 原生 `UUID` 类型 | ⭕ `CHAR(36)` | ✅ **10.7+**（独立二进制类型） | ✅ | ⭕ `RAW(16)` | ✅ `uniqueidentifier` | ✅ | ⭕ `CHAR(16) FOR BIT DATA` | ⭕ | ✅ | ❌ |
| `GREATEST` / `LEAST` | ✅ | ✅ | ✅ 8.1+ | ✅ | ✅ **仅 2022+** | ✅ | ✅ 11.1+ | ⚠️ unverified | ✅ 版本未公开 | ❌ **永无**（只有 `min(a,b,…)`/`max(a,b,…)` 标量形式，3.5.7+） |
| `LISTAGG` / `string_agg` | ⭕ `GROUP_CONCAT`（4.1+） | ⭕ `GROUP_CONCAT` | ⭕ **`string_agg`（9.0+，无 `LISTAGG`）** | ✅ 11gR2+ | ⭕ `STRING_AGG .. WITHIN GROUP`（2012+，compat ≥110） | ✅ | ✅ 11.1+ | ⚠️ unverified | ✅（`listagg` 别名，版本未公开） | ⭕ `group_concat` |
| 正则匹配 | ✅ `REGEXP`/`RLIKE` | ✅ | ✅ `~`/`~*` | ✅ `REGEXP_LIKE` | ⭕ ≤2022 仅 `LIKE`/`PATINDEX`；✅ **2025+ 原生**（`REGEXP_LIKE` 等需 compat 170） | ✅ `REGEXP` 谓词 + `REGEXP_LIKE` 函数 | ✅ **11.1+ 内置 `REGEXP_LIKE`** | ⚠️ unverified | ✅ 版本未公开 | ❌ **永无内置**（`REGEXP` 是未定义 UDF 的语法糖） |
| `\|\|` = 字符串连接 | ⭕ **默认是 `OR`**（8.0.17 起弃用；`PIPES_AS_CONCAT` 改语义） | ⭕ 同 MySQL | ✅ | ✅ | ❌ **T-SQL 里它根本不是运算符**（连接是 `+`；`CONCAT_NULL_YIELDS_NULL` 自 2017 起恒为 ON） | ✅ | ⚠️ | ✅ | ✅ | ✅ |
| `<=>` 字面运算符 | ✅ 3.23+ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| ↳ 替代：原生 `IS [NOT] DISTINCT FROM` | ✅ | ✅ | ✅ | ❌ **永无**（需三节点展开） | ✅ 2022+（引擎门，无 compat 门） | ✅ | ✅ 11.1+ | ⚠️ | ✅ | ✅ 3.39+ |
| `AT TIME ZONE` | ⭕ 仅 `CAST(ts AT TIME ZONE 'UTC' AS DATETIME)`（UTC only, 8.0.22+） | ⭕ | ✅ | ✅ | ✅ | ✅ | ⭕ | ⚠️ | ✅ | ❌ **永无** |
| `SELECT` 无 `FROM` | ✅（`FROM DUAL` 亦可） | ✅ | ✅ | ✅ **23ai+ 免 FROM**；⭕ 11g/19c 需 `FROM DUAL` | ⭕ 需表 | ✅（文法推断，无文档示例） | ⭕ 需 `SYSIBM.SYSDUMMY1`/`SYSIBM.DUAL` | ⭕ `FROM DUAL` | ✅ | ✅ |
| `INTERSECT` / `EXCEPT` | ✅ **8.0.31+**；❌ ≤8.0.30 | ✅ **10.3+** | ✅ | ✅（`EXCEPT`→`MINUS`） | ✅ **2005 起即有**（compat 90，无版本门） | ✅ | ✅ | ⚠️ unverified | ✅ | ✅ 3.39+ |
| `FROM (VALUES(..)) AS t(a)` | ✅ 8.0.19+ | ❌ | ✅ | ⭕ 23ai+；⭕ 否则 UNION ALL | ✅ | ✅ | ✅ | ⭕⚠️ | ✅ | ⭕ compound SELECT |

## 4.1 由矩阵得出的 3 条关键结论

1. **`INTERSECT` / `EXCEPT` 缺的是版本门，不是能力门**。EQL 的 `unionType_`（`DMLStatement.g4:91-92`）无条件接受它们、`AstToEqlGenerator` 原样输出、**无任何 `supportIntersect`/`supportExcept` 标志** —— 但 **MySQL 8.0.31+ 与 MariaDB 10.3+ 已原生支持**（v2 说"完全不支持"是错的）。真正的漏洞只在 **MySQL ≤8.0.30**：由于 `mysql5.7.dialect.xml` 继承自 `mysql.dialect.xml`，`mysql5.7` 与 `mysql` 共用一个方言文件，因此**一个版本门无法区分二者**，需要先拆方言（对照仓库已有的 `mysql` / `mysql5.7` 拆分先例）。
2. **`RETURNING` 的方言配置三处错误**（见 Part 1.4）源于"以为 MySQL 8.0.14+ 支持 `RETURNING`"这一误解。
3. **`selectFromDual` 只覆写了 Oracle**：MSSQL（需表）与 DB2（需 `SYSIBM.SYSDUMMY1`）的无 FROM 查询会生成非法 SQL。v1 的 §1.2 归一化表漏了这一行。

## 4.2 NULL 排序默认值 —— 4 类（v1 说 2 类且把 Oracle 归错）

| 类别 | 语义 | 库 |
|------|------|----|
| A | ASC → NULLs **LAST**，DESC → NULLs **FIRST**（NULL 视为最大） | **PostgreSQL**、**Oracle**（11g→23ai）、**DB2 11.5**（"The null value is higher than all other values"） |
| B | ASC → NULLs **FIRST**，DESC → NULLs **LAST**（NULL 视为最小） | **MySQL 8**、**MariaDB**、**SQL Server**、**SQLite** |
| C | 两个方向都 **NULLS LAST** | **DuckDB ≥0.8**（0.8.0 起 `ASC NULLS LAST` 是破坏性变更）。⚠️ **但这是可配置的默认值，不是不变式**：`default_null_order` 有 4 个取值（`NULLS_FIRST` / `NULLS_LAST` / `NULLS_FIRST_ON_ASC_LAST_ON_DESC` / `NULLS_LAST_ON_ASC_FIRST_ON_DESC`），`SET default_null_order=…` 即可改。**含义：EQL 若要真正可移植，只写死 NULL 序还不够，还必须 pin 住该会话设置** |
| D | **未知**（厂商文档未声明，必须实测） | **H2**、**DM8** |

⚠️ `h2.dialect.xml:17` 的 `supportNullsFirst="true"` 是一句**无 accessor、无实现、无测试**的断言，与 D 类现状矛盾。
⚠️ G2 的价值因此比 v1 判断的更高：EQL 若能在生成时**按方言默认值显式写出 NULL 序**，就能同时消掉 A/B/C 的差异并把 D 变成"已知待实测"。

### 4.2b `GREATEST`/`LEAST` 的 NULL 语义 —— 3 类（这是 v2"零方言分歧"说法的直接反例）

| 类别 | 语义 | 库 |
|------|------|----|
| N1 | **任一参数为 NULL 即返回 NULL**（多数 SQL 标准的读法） | Oracle、MySQL、DB2（H2 的 Oracle/MySQL 兼容模式亦然） |
| N2 | **忽略 NULL**，全部为 NULL 才返回 NULL | PostgreSQL（官方文档明确标注"**这是对 SQL 标准的偏离**"）、SQL Server 2022+ |
| N3 | 随**兼容模式**变化 | H2（Oracle/SQLServer/MySQL/MariaDB 模式下忽略 NULL） |
| ⚠️ | 函数存在但 NULL 行为**未被该函数页明文**（只能按通用 NULL 规则推断） | MariaDB、DuckDB、TDengine、DM8 |

**结论**：`GREATEST`/`LEAST` **不是"零方言分歧"的可移植函数**。落地前需要一次**产品决策**：EQL 采纳 N1（NULL 传播）还是 N2（忽略 NULL）？
- 选 N1 → PG / MSSQL 2022 / H2 必须包一层 `NULL` 显式处理，或拒绝；
- 选 N2 → Oracle / MySQL / MariaDB / DB2 必须包一层。
再加上 SQLite **永无**该函数、DM8 无法核实，**它不应排在 v2 给出的"最高 ROI"位置**。

## 4.3 由矩阵得出的"高价值低成本"排序

| 优先级 | 项 | 理由 |
|---|---|---|
| 1 | `POSITION` / `SUBSTRING` / `EXTRACT`（`year`） | 已有函数模板 + 换序机制已被 in-repo 证明（`mssql.dialect.xml:174-176` 的 `charindex({1},{0})`）；零方言分歧 |
| 2 | `IS [NOT] DISTINCT FROM` | **SQL 标准**；PG/H2/DB2/DuckDB/MSSQL-2022/SQLite-3.39 **6 个引擎原生**，只有 Oracle 需要 AST 树改写。⚠️ v2 曾把 `GREATEST`/`LEAST` 排在这之前，那是错的 |
| 3 | `FULL OUTER JOIN` 语法 + `supportFullJoin` | 语法遗漏极少；标志已有但无 accessor |
| 4 | `INTERSECT`/`EXCEPT` **版本门** | ⚠️ v2 说"MySQL/MariaDB 完全不支持"是错的 —— MySQL 8.0.31+ / MariaDB 10.3+ 已原生。真正需要门的是 **MySQL ≤8.0.30**（而 `mysql5.7` 方言继承自 `mysql`，必须一并覆盖） |
| 5 | `ROLLUP`/`CUBE`/`GROUPING SETS`/`GROUPING()` 注册 | 语法已通；PG/Oracle/MSSQL/H2/DB2/DuckDB 原生，MySQL 8.4.3+ 原生 / 8.0–8.4.2 需 `WITH ROLLUP` 特化，MariaDB 仅 `WITH ROLLUP` |
| 6 | 窗口 `OVER` 放宽 + frame | 语法 + AST 增字段；PG/DuckDB 的 mixin 配置是 2 行 |
| ⚠️ 降级 | `GREATEST` / `LEAST` | **v2 曾列为"性价比最高"，v3 降级**：SQLite **永无**该函数；MSSQL 仅 2022+；**NULL 语义分 3 类**（见 4.2b）。它不是"只缺注册"，而需要一次 NULL 策略产品决策 |
| ⚠️ 降级 | `stringAgg` | 各库的 `GROUP_CONCAT`/`LISTAGG`/`STRING_AGG` 在**分隔符默认值、长度上限、排序语义**上均不同（见 4.2c），是"有损归一"而非"无损归一" |

### 4.2c 字符串聚合的归一化缺口

| 库 | 原生 | 与 EQL 规范形态的差异 |
|----|------|---------------------|
| MySQL / MariaDB | `GROUP_CONCAT(x)` / `GROUP_CONCAT(x SEPARATOR s)` | 默认分隔符 `,`；受 `group_concat_max_len` 截断（默认 1024）；**不支持 `WITHIN GROUP` 排序子句**（需 `ORDER BY` 在函数内，形态不同） |
| PostgreSQL | `string_agg(x, s)`（9.0+） | **没有 `LISTAGG`**；排序靠 `ORDER BY` 在聚合内 |
| Oracle | `LISTAGG(x, s) WITHIN GROUP (ORDER BY …)`（11gR2+） | 必须带 `WITHIN GROUP`；`ON OVERFLOW TRUNCATE` 需 12.2+ |
| DB2 | `LISTAGG(x, s)`（11.1+） | — |
| MSSQL | `STRING_AGG(x, s) WITHIN GROUP (ORDER BY …)`（2012+，compat ≥110） | `WITHIN GROUP` 在 2017+ 可选 |
| DuckDB | `string_agg` / `listagg` 别名 | — |

⇒ **不存在一个覆盖全部库的 `stringAgg(expr, sep)` 模板**：排序语义、`sep` 是否必填、`WITHIN GROUP` 是否可用三者都分叉。落地时应把它当成**有损归一**并在文档标注，或拆成 `stringAgg(expr, sep)` + `stringAggOrdered(expr, sep, orderBy)` 两个形态。

---

# Part 5 — 明确否决项

| 候选 | 否决理由 |
|------|---------|
| `DISTINCT ON (a)` | 覆盖不足：**PG / DuckDB / H2**（H2 也支持，v1 漏了）。要表达"每组取一行"应改写为 `ROW_NUMBER() OVER (PARTITION BY a ORDER BY ..) = 1`（全库可用）。收进 EQL 会让同一逻辑在不同库走两条路径 |
| `SELECT TOP n` | T-SQL 私有。`LIMIT` / `FETCH FIRST` 已覆盖 |
| `LIMIT <off>, <lim>` | MySQL 私有，且**参数顺序与 SQL 标准相反**。收进 EQL 是主动制造误读陷阱 |
| `NATURAL JOIN` | 语义依赖"同名列"的隐式契约，schema 一变结果就变；各库对同名列大小写/类型的隐式 join 行为不完全一致。EQL 有显式 prop-join，不需要这个隐式魔法 |
| `JOIN .. USING (col)` | **MSSQL 不支持**。且 `USING` → `ON` 的 lowering 需要知道公共列名，语法层给不出这个信息。**同族证据**：MSSQL 也不支持 `LATERAL`（要用 `CROSS APPLY`）→ 说明这类语法只能算"多数库方言"，不是可无损归一化的标准 |
| `OUTER APPLY` / `CROSS APPLY` | MSSQL 私有。`LATERAL` 已覆盖 PG/MySQL8/MSSQL 语义 |
| `QUALIFY` | PG / DuckDB / Snowflake 独有 |
| `JSON_TABLE` / `json_each` / `OPENJSON` | 各库形态完全不同，无公共子集。**但** `json_extract`/`json_value` 等标量函数有公共子集，值得单独立项（`StdSqlType.JSON` 与 `jsonTypeHandler` 已就绪，函数侧完全空白） |
| `FROM table(fn(..))` / `TABLESAMPLE` | 各库形态与函数白名单完全不同 |
| `MATCH_RECOGNIZE` | 仅 Oracle / DuckDB |
| `OVERLAY`（SQL:2008） | PG/MySQL 缺；Oracle/MSSQL/H2/DB2 有。覆盖仅 4~5 库 |
| `DELETE .. WHERE .. LIMIT n` | PG/Oracle 不支持（需 `ctid`/`ROWNUM` 改写），且"删前 N 条"在并发下语义本身不确定。正确写法 `DELETE FROM t WHERE pk IN (SELECT pk FROM t WHERE .. ORDER BY pk FETCH FIRST n ROWS ONLY)` 已能表达 |
| `INSERT INTO t VALUES(..)`（无列清单） | 全库支持，但 EQL **强制列清单是正确的设计**：列顺序依赖 DDL 生成顺序，跨库/跨 schema 变更时静默错位 |
| `INSERT IGNORE` | 语义是"忽略任何错误"，与 `ON CONFLICT DO NOTHING`（只忽略唯一冲突）不同 |
| `MONEY` 类型 | 各库精度/范围/币种处理不一致，无法归一 |
| DDL（`CREATE`/`ALTER`/`DROP`/`TRUNCATE` 除外） | DDL 应继续由 ORM 模型（`model/*.orm.xml`）+ `nop-db-migration` + 各方言 DDL 模板驱动。`Eql.g4:37-49` 注释掉 DDL 的决定**正确**。**注**：`getCreateSequenceSql`/`getDropSequenceSql`/`getDropTableSql` 在 `IDialect`（同一层），而 `nop-db-migration` 的 `_SqlChange.dbSpecific` 属性集是逐方言 DDL 覆盖的**正式机制** —— 落地前需明确这条分工线 |
| `SERIAL` / `AUTO_INCREMENT` / `IDENTITY` | 自增是 DDL + 插入语义，不是类型。走 ORM 模型 + 方言 DDL 模板 |
| 裸 `INTERVAL` **删除** | **v1 说"禁止"是破坏性变更**（现有 EQL 已依赖它）。正确做法：加 `sqlIntervalLiteral` 模板逐方言定义形态 + 文档标注 MySQL 形态为 deprecated |
| `a \|\| b` | **两个问题**：(a) MySQL/MariaDB 默认把 `\|\|` 当 `OR`（8.0.17 起弃用），静默改变语义；(b) NULL 语义 —— v1 的理由写反了：**PG 的 `\|\|` 遇 NULL 返回 NULL；忽略 NULL 的是 PG 的 `concat()` 函数**。无论如何，`\|\|` 与 `concat()` 之间不存在无损映射。EQL 已有 `concat()` 作为唯一入口，保持唯一；`CONCAT_` token 建议删除 |
| `<=>` 作为 EQL 语法 | MySQL 私有。标准形式是 G3-1。**但 token 必须保留**（它是 G3-1 的 lowering 目标）；`DEQ_`(`==`) 建议删除 |

---

# Part 6 — 落地机制（四路机制决策表，取代 v1 的"三层"故事）

v1 把归一化简化为"三层：语法 → AST → 生成器"。读完 `TemplateSQLFunction` / `NativeSQLFunction` / `EqlTransformVisitor` 后，这个模型**不足以定价**。真实的机制有四路，能力与成本差异很大：

| 路径 | 能表达什么 | 不能表达什么 | 典型成本 | 现有成功证据 |
|------|-----------|-------------|---------|---------|
| **⓪ 复用已有 AST 节点 + 已有 `<native>` 函数** | 参数不变或仅 1→N 元变化的函数调用。方言差异由 `realName` 改名解决 | 不能改参数**顺序**；不能做表达式树改写 | 语法 ~15 行 + visitor ~15 行 | `year` → PG/Oracle `extract(YEAR from {0})`、MSSQL `datepart(year,{0})` |
| **ⓑ 复用已有 `<template>` 函数** | 参数**可换序**（`{1}` 在 `{0}` 前）；可把多参数压进一条方言原生串 | **没有 `minArgCount`/`maxArgCount`** —— 元数固定；不能新增元数 | 语法 ~15 行 + 复用已有模板 | `mssql.dialect.xml:174-176` `charindex({1},{0})`；PG `instr→strpos` |
| **ⓒ 新增 AST 节点** | 结构化的新语法（`NULLS FIRST`、窗口 frame、`MERGE` 的分支、`ORDER BY` 形态） | — | **高**：改 `EqlAST.xjava` → 重生成 `EqlParser.java`(9933) + `_EqlASTBuildVisitor.java`(1653) + **72 个** `ast/_gen/*`；**外加**手改 4 个带 `__XGEN_FORCE_OVERRIDE__` 的 `EqlAST*` 文件的 `switch`，再建非下划线 `ast/SqlXxx.java` 子类 | `SqlOrderByItem.nullsFirst`、`SqlWindowExpr` 缺 frame 字段 |
| **ⓓ AST 树改写** | 任意表达式/语句树重写（`IS DISTINCT FROM` → 三节点、聚合 `FILTER` → `sum(CASE …)`） | — | **最高，且 ⚠️ 本路径尚无生产先例**（见下） | `EqlTransformVisitor` 内部直接调用 `CollectionOperatorTransformer`（`:168`）做 `_some`/`_all` → `EXISTS` 重写 |

⚠️ **路径 ⓓ 的可行性警告（v2 严重误判，v3 更正）**：

- **v2 把 `CollectionOperatorTransformer` 当作 `IEqlAstTransformer` 的"唯一先例"是错的** —— 该类是 `public class CollectionOperatorTransformer`（`CollectionOperatorTransformer.java:57`），**并不实现** `IEqlAstTransformer`，它只有 `transform(SqlWhere)` 一个方法，由 `EqlTransformVisitor.java:168` 内部直接调用。
- `IEqlAstTransformer` **零生产实现者**：全仓唯一实现是 `nop-orm/src/test/java/io/nop/orm/eql/TestEqlCompiler.java:76` 的测试桩。
- 该接口是**按调用点 opt-in** 的（`IOrmSessionFactory.compileSql(..., IEqlAstTransformer, ...)` 或 `env.getDefaultAstTransformer()`）。⇒ **若按 v2 的写法实现 `IS DISTINCT FROM`，对所有未显式传入 transformer 的既有调用方将完全不产生 lowering**（静默失效）。
- `IEqlAstTransformer` 有**两个**钩子：`transformBeforeAnalyze`（`EqlCompiler.java:59`）与 `transformAfterAnalyze`（`:66`）。需要实体解析结果的改写应走 **after**；v2 只引了 before。
- `ILoadedOrmModel.java:69` 明确警告："**astTransformer 不参与缓存键**，调用方必须保证同一 session factory 内使用语义一致的 transformer" —— 语义级 transformer 会静默破坏编译缓存。
- ⇒ **落地前置**：需先把 `IEqlAstTransformer` 从"调用方 opt-in"提升为"EQL 编译管线默认环节"（`EqlCompiler` 内部组合，而非由调用方传入），否则路径 ⓓ 的成本估计（`transformer ~200 行`）严重偏低 —— 真正的成本是**接线成本 + 缓存键处理**。

**另外两条硬约束**（v1 完全没提）：

- **聚合完全绕过 `<functions>` 表**。`sqlIdentifier_agg_`（`BaseRule.g4:235-237`）把聚合名硬编码为 `MAX|MIN|SUM|COUNT|AVG` token；`EqlTransformVisitor` 只覆写 `visitSqlRegularFunction`（`:1371`），**没有** `visitSqlAggregateFunction`；`AstToEqlGenerator.visitSqlAggregateFunction` 原样打印。所以任何"聚合函数归一化"都必须走路径 ⓓ，**不能靠加 XML**。
- **`TemplateSQLFunction` 没有 marker 范围校验**。`TemplateSQLFunction.java:119-121`：`if (index >= argExprs.size()) ret.add(StringSqlExpr.makeExpr(" null "))` —— 模板里写错 `{3}` 会**静默产出 `null`** 混进 SQL。`:52-54` 还只替换全数字 marker 名（`{a:1}` 原样保留，`TestSQLFunction.java:27` 有断言）。G8 计划新增约 100 行手写模板而**零 Java 改动** —— 这是错的。**建议在 S-1 之内就补 `TemplateSQLFunction` 的构造期校验（dialect 加载时抛，而非查询时抛）+ 一个覆盖全部方言的参数化单测。**

**归一化原则（在四路机制下的表述）**：

1. 能走 ⓪ 或 ⓑ 的，绝不走 ⓒ 或 ⓓ；
2. 语义一致（所有目标库同名同义）→ 注册成**函数**，零方言分歧（`greatest`/`least` 是典型）；
3. 语义分叉但**方言已有既有标志** → **接线既有标志**（`defaultNullsFirst` 优于新增 `supportNullsFirst`）；
4. 语义分叉且无既有标志 → 新增 `DialectFeatures` 布尔，并**同时**做三件事：`IDialect` 加 accessor、`AstToSqlGenerator` 对应 `visitXxx` 消费它、按"覆写点数 ≥ 5"决定是否进 EQL；
5. **无法归一 → 编译期抛可读错误，绝不静默降级**。这是代码里已确立的原则（`AstToSqlGenerator.java:471-473` 对 `ILIKE` 的注释：*"静默降级为大小写敏感的LIKE会改变查询语义，必须显式报错"*）。

**新增特性标志的自检清单**（每加一个都要回答）：

- [ ] `IDialect` 上有 accessor？
- [ ] `AstToSqlGenerator` 的哪个 `visitXxx` 消费它？
- [ ] 标 `true` 的**覆写点**数 ≥ 5 吗？（不是方言名数）
- [ ] 是否已有描述同一事实的既有标志/字段？（`defaultNullsFirst` vs `supportNullsFirst`）
- [ ] 不可归一时是抛错还是降级？（必须抛错）

---

# Part 7 — 建议的批次拆分与成本

> 折算基准（**统一按 `wc -l` 口径**；v2 曾混用「总行数」与 `wc -l` 两套口径导致差 1）：`CollectionOperatorTransformer` = **711 行**（唯一 AST 改写先例）；`AstToEqlGenerator` = 929 行；`AstToSqlGenerator` = 544 行；`EqlTransformVisitor` = 1591 行；`default.dialect.xml` = 264 行 / 49 函数；`EqlParser.java` = 9933 行；`_EqlASTBuildVisitor.java` = 1653 行；`ast/_gen/*` = **72 个文件**。

| 批次 | 内容 | 规模 | 风险（含 AGENTS.md plan-first 判定） | 前置 |
|------|------|------|------|------|
| **S-1 · 多方言能力测试骨架** | 在 `nop-dao` 建"遍历全部方言模型 × 全部注册函数，断言 `getFunction(name) != null`"的矩阵测试（**不需真实数据库**）。补 `TemplateSQLFunction` marker 范围校验 + 参数化单测 | ~120 行 | 低 | 无 |
| **S-2 · 接入型修复（含方言门控）** | BUG-1 `ESCAPE`、BUG-2 `LATERAL`、BUG-3 `for update`、BUG-4 `selectAll`+`LIMIT`。⚠️ **每项都必须同时接线对应方言能力门**：`supportLateral`（BUG-2 修法明写需要，但 v2 漏排）、`getForUpdateSql` 为空 → 编译期报错（BUG-3）、`ESCAPE` 子句不支持时的改写（BUG-1）。**只打印不设门 = 把静默错误 SQL 换成硬运行时失败，是净回归** | ~200 行（80 行打印 + ~120 行 xdef/accessor/XML 门控）；测试：`TestEqlParser` 新增 `ESCAPE`/`LATERAL`/`for update` 回显断言 + 新增「无能力门方言编译期报错」断言 | **中**（新增 `DialectFeatures` 属性 = **AGENTS.md 生成管线 plan-first**：`dialect.xdef` + `_gen/_DialectFeatures.java` 重生成） | 无 |
| **S-3 · 解析修复（含 `supportFullJoin` 门控）** | BUG-6 `FULL OUTER`、BUG-7 小写字面量 CCE、BUG-8 `cast boolean`、BUG-10 `cross join` 误解析、BUG-14 集合下标访问静默损坏。⚠️ **`supportFullJoin` 门控已并入本批次**：MySQL/MariaDB 完全无 `FULL JOIN`，只补语法不加门就是把「响亮的解析失败」换成「静默的非法 SQL」。**注**：BUG-11 已移出本批次（涉及 `StdSqlType` = plan-first，见 S-10），避免风险评级自相矛盾 | ~250 行（180 行语法 + ~70 行 `supportFullJoin` accessor + xdef + 4 个 XML）；测试：`TestEqlParser` 新增 `cross join`/`natural join`/`roles[1]`/`full outer join` 用例、`TestEqlAstParser` 新增 `roles[1]` AST 断言 | **中**（`IDialect` 新增 public 方法 + xdef 属性 = **生成管线 plan-first**） | S-2 |
| **S-4 · 方言配置纠错 + 文档** | Part 1.4 的 **7** 项（mysql RETURNING、duckdb RETURNING、dm RETURNING、mysql5.7 移除 window mixin、PG/DuckDB 加上 window mixin、H2 `supportILike`、**H2 `supportNullsFirst` 的无证据断言**）；接线 `supportFullJoin`；`INTERSECT/EXCEPT` 改**版本门**（需先拆 `mysql` / `mysql5.7` 方言，见 4.1 结论 1）；接线 `defaultNullsFirst` 并**消除 duckdb 的自相矛盾**（`:9` 与 `:21` 取值相反）；修正 `docs-for-ai/.../eql-and-database-compatibility.md` 三处失真；跑 `check-doc-links.mjs --strict` 退出 0 | ~150 行 XML/Java + 1 篇文档；测试：新增方言能力矩阵断言（承 S-1）+ `TestDialect.testFunctions` 全绿 | **中**（方言配置是平台契约变更，AGENTS.md「变更产品范围」需确认） | S-1 |
| **S-5 · 标准函数语法（G1）** | `POSITION` / `SUBSTRING` / `EXTRACT`（先只支持 `year`/`month`）；同步放开 `day`/`hour`/`minute`/`second`/`quarter` 的 `unreservedWord_` 遮蔽；`TRIM` 的 `c FROM x` 变体按"逐方言标准三段式模板"实现。**先查 git 历史**：`BaseRule.g4:246-276` 为何被注释 | 语法 ~40 + visitor ~60 + XML ~60 | 低 | S-3 |
| **S-6 · 高价值函数注册（G8 前半）** | `month`/`day`/`hour`/`minute`/`second`/`quarter`/`dayofweek`/`weekofyear`、`dateAdd`/`dateDiff`/`dateTrunc`。⚠️ **`greatest`/`least` 已移出本批次** —— 需先做 NULL 策略产品决策（4.2b）；**`stringAgg` 也已移出** —— 各库排序/分隔符/`WITHIN GROUP` 语义分叉（4.2c），属有损归一需产品决策 | XML ~120（按**覆写点**计，非方言名）；测试：`TestDialect.testFunctions` 对每个新函数做真实执行 | 中（每库语义需核对） | **S-3 → S-5**（`day`/`hour` 等需先由 S-5 放开 `unreservedWord_` 遮蔽） |
| **S-7 · 分页与 NULL 序（G2）** | `FETCH FIRST` / `OFFSET..ROWS`（**需 `SqlLimit` 加"语法形态"字段** → 路径 ⓒ）；`NULLS FIRST/LAST`（语法 + 打印，复用已预留的 `nullsFirst`，接线 `defaultNullsFirst`）；`sqlIntervalLiteral` 模板（修 BUG-5，**不动语法**） | 语法 ~50 + AST ~30 + generator ~60 + XML ~30 | **中**（若按接线 `defaultNullsFirst` 的方案则风险显著降低） | S-3、S-4 |
| **S-8 · 分析型（G5）** | 注册 `rollup`/`cube`/`grouping`/`grouping_sets`；窗口 `OVER` 放宽 + frame（**需 `SqlWindowExpr` 增字段** → 路径 ⓒ）；**放弃** `count(*) FILTER`（改文档引导 `sum(case when …)`） | 语法 ~40 + AST ~40 + generator ~60 + XML ~30 | 中（DM8/DB2 的 frame 行为需验证） | S-4 |
| **S-9 · NULL 三值谓词（G3）** | `IS [NOT] DISTINCT FROM`、`IS [NOT] TRUE/FALSE/UNKNOWN`。⚠️ **必须先完成 S-3.5**，否则对未显式传 transformer 的既有调用方完全不产生 lowering（见 Part 6 路径 ⓓ 警告）。`<=>` 通过 `default` 注册 `<native name="safe_eq" realName="<=>">` + 其它方言 `remove` —— 但 **`<=>` 只存在于 MySQL/MariaDB**（见 Part 4）；其余引擎中 PG/H2/DB2/DuckDB/MSSQL-2022/SQLite-3.39 **原生支持 `IS NOT DISTINCT FROM`，可直接输出**，只有 Oracle 需三节点展开 | 接线 ~80 + AST ~60 + transformer ~200 + `SqlOperator` ~10 + 错误码 ~5；测试：必跑 `TestEqlArithmeticPrecedence` / `TestEqlLogicalPrecedence`（**Part 10 点名的两个最脆弱套件**）+ 新增逐方言用例 | **高**（路径 ⓓ 无生产先例 + 语义等价性需逐库验证 + 缓存键问题） | S-4 + **S-3.5** |
| **S-10 · CAST 类型（G7）+ BUG-11**（BUG-11 从 S-3 移入此处，以消除风险评级矛盾） | `BOOLEAN`；别名 `NUMBER`/`TEXT`/`BOOL`；`UUID`；时区类型（取消 `StdSqlType.java:38,42` 注释 + 同步语法）；收窄 `dataTypeName_` | `StdSqlType` ~20 + 语法 ~15 + 9 方言类型映射 ~60 | **高（plan-first）** —— `stdNameMap` 同时驱动 DDL 生成 | 需 plan audit |
| **S-3.5 · 接线 `IEqlAstTransformer`** | 把 `IEqlAstTransformer` 从「调用方 opt-in」提升为 `EqlCompiler` 内部默认环节；决定用 `transformBeforeAnalyze` 还是 `transformAfterAnalyze`；解决 `ILoadedOrmModel.java:69` 的「transformer 不参与缓存键」问题。**无前置**（与 bug 修复无逻辑依赖） | ~80 行 + 测试 | **中**（影响所有 `compileSql` 调用方；`IOrmSessionFactory`/`ISqlCompileTool`/`SessionFactoryConfig`/`IPersistEnv` 都透传它 = **跨模块公共 API，plan-first**） | 无 |
| **S-11 · P2 项** | `SAVEPOINT`/`ROLLBACK TO`；窗口命名 `WINDOW`（**需新 AST 节点** → 路径 ⓒ，非纯语法）；`FOR UPDATE NOWAIT`/`SKIP LOCKED`；`FROM (VALUES …)`；`AT TIME ZONE`。⚠️ **`OVERLAY` 已移除**（Part 5 已否决，v2 的 S-11 与 Part 5 直接矛盾）、**`regexpLike` 已移至 S-14** | ~300 行；测试：`TestEqlParser` 新增语法用例 + 现有 15 个 EQL 测试类全绿 | **中**（含 2 个新 AST 节点 → 触发 72 文件 `_gen` 重生成；按 Part 6 路径 ⓒ 定价，**非 v2 所称的「低」**） | S-7 |
| **S-12 · DAO 层批量 upsert（不进语法）** | 基于已归一化的 `ERR_SQL_DUPLICATE_KEY` 做批量 upsert 原语；把两个 nop-stream 私有 `UpsertDialect` 枚举迁过去 | ~200 行 | **中偏高**（新增 `nop-dao` public 接口 —— 2026-08-22 删除的 `IUpsertHandler` 正是同类先例，说明这是**跨模块公共 API，plan-first**；10 个现有调用点需评估是否迁移） | 无 |
| **S-13 · 产品决策（单独走）** | BUG-9 `ORDER BY` 序号收紧；4.2b `GREATEST`/`LEAST` 的 NULL 策略（选 N1 还是 N2）；4.2c `stringAgg` 是否接受有损归一；BUG-11 的 `TEXT→CLOB` 等类型别名 | 决策 + 实施 | **高** —— 会改变现有 EQL 的行为 | 需 product decision |
| **S-14 · 收拢孤儿项** | BUG-13 的死标志处置（接线 9 个 / 先补消费者 1 个 / **可移除 3 个 —— 但删 xdef 属性会破坏现有 XML 校验，plan-first**）；**G11 的 `IN` 列表长度上限**（Oracle 硬上限 1000 / MSSQL 参数上限 2100 —— v2 未给它任何批次，v3 提到 **P0**；**需先有「超阈值改写为临时表 JOIN」的策略设计，故排在本表最后是刻意的**）；G8 的 `lastDay(d)`；`stringAgg` / `regexpLike` 的降级文档；G11 的 keyset 分页与命名参数（**本轮明确延后**，见 Part 11） | ~200 行；测试：构造超长 `IN` 列表的回归用例 | **中偏高**（xdef 删属性 = 生成管线 plan-first） | S-1 |

**与 v1 排序的差异及理由**：

1. **插入 S-1（多方言能力测试骨架）到最前**。当前 `TestEqlTranslationSemantics.java` 用的是**手工构造的最小 DialectModel**（其自身 javadoc `:46-51`，论断在 `:49`，承认「模块测试环境没有 dialect.xml 组件加载器」；`buildDialect()` 实际 `:83-96`，只注册 5 个函数）—— **v1 推荐的"同一 EQL 对多个方言断言输出"验证范式目前不存在**。而 `nop-dao/TestDialect.java:189 testFunctions` 会**对每个注册函数做真实数据库执行**，即函数表改动自带跨模块回归面。没有 S-1，S-6/S-11 无法验证，且 v1 Part 8 担心的"`unknown-function` 误诊"风险无缓解。
2. **BUG-9（`ORDER BY` 序号）从 S0 移到 S-13**。它是对合法输入的**行为移除** + 需要新错误码 + v1 自己也把它列为 Open Question —— 是产品决策，不是 bug 修复。
3. **BUG-6 不再是"1 行语法"**：必须连带接线 `supportFullJoin`（MySQL/MariaDB 无 `FULL JOIN`）。
4. **G6/MERGE 从语法层移除**（S-12 改为 DAO 原语），理由见 Part 3 G6。
5. **G5 的 `count(*) FILTER` 移出**，改为文档引导 `sum(case when …)`。

**每个批次的强制收尾动作**（AGENTS.md「Mandatory Updates」，v2/v3 前只有 S-4 遵守）：

- 更新当日开发日志 `ai-dev/logs/{year}/{month}-{day}.md`
- 更新 owner doc：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（S-2/S-4/S-5/S-6/S-7/S-8/S-11/S-14 **全部改变方言能力语义**，都落在这篇）；涉及类型时另更新 `orm-model-design.md` / `model-first-development.md`
- 路由或实现锚点变化时更新 `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`
- 跑 `node ai-dev/tools/check-doc-links.mjs --strict`，**退出码必须为 0**

**触发 plan-first 的批次汇总**（AGENTS.md Protected Areas）：

| 批次 | 触发的保护区域 | 具体对象 |
|---|---|---|
| S-2、S-3 | 生成管线 | `dialect.xdef`（`nop-kernel/nop-xdefs`）新增属性 + `_gen/_DialectFeatures.java` 重生成；`IDialect` 新增 public 方法 |
| S-3.5、S-12 | 跨模块公共 API | `IOrmSessionFactory`/`ISqlCompileTool`/`SessionFactoryConfig`/`IPersistEnv` 的 transformer 透传；新增 `nop-dao` public 接口 |
| S-10 | 跨模块公共 API + 模型层行为变更 | `StdSqlType`（`nop-kernel/nop-commons`）；⚠️ `stdNameMap` 加别名会改变所有 ORM 模型的 DDL 类型解析 |
| S-14 | 生成管线 | 删除 `dialect.xdef` 的死属性会破坏现有 XML 校验 |
| S-4 | 产品契约 | 方言 XML 是平台对外声明的能力，改动需确认（非 AGENTS.md 定义的 plan-first，属「AI Must Ask」第 1 条） |

> ⚠️ **v2/v3 前只把 S-10 标为 plan-first 是低估的** —— 实施者按 S-1→S-4「风险低」推进，第一周就会撞上生成管线与跨模块 API。

**总体顺序建议**：`S-1 → S-2 → S-3 → S-4` 先跑完（约 200+250+120+150 ≈ **720 行**，含 15+ 个测试，覆盖 6 个 A 类执行路径缺陷与 7 个方言配置错误；其中 S-2/S-3 已并入门控以免引入净回归），再按 ROI 决定是否继续 S-5/S-6。

---

# Part 8 — 风险

| 风险 | 说明 | 缓解 |
|------|------|------|
| **生成物 diff 失控** | 改 `.g4`/`EqlAST.xjava` 会原地重写 `EqlParser.java`(9933) + `_EqlASTBuildVisitor.java`(1653) + **72 个** `ast/_gen/*` + `EqlLexer*`/`Eql.interp`/`*.tokens` | 每组改动单独提交；CI 加 `git diff --stat` 阈值告警 |
| **删除 lexer token 会重编号全部 token 类型** | `EqlLexer.java:33` / `EqlParser.java:33` 是 git 跟踪的生成物，删一条规则会移动其后所有 token 号，并使 `.interp`/`.tokens` 失效。**v1 建议删除 `DEQ_`/`CONCAT_`，但没提这个后果** | 删除 token 必须作为**独立提交** + 完整重建验证；或干脆保留 token（死 token 的成本远低于重编号风险） |
| **归一化语义静默改变** | `NULLS FIRST` 的模拟在窗口/`DISTINCT` 组合下可能不等价；`is distinct from` 的三节点展开在 MySQL 三值逻辑下可能与 `<=>` 不同 | 逐方言对照测试（依赖 S-1）；每个 lowering 都要有"一致性"与"不等价"两类用例 |
| **"能解析"被误认为"能跨库跑"** | `LATERAL`（BUG-2）与 `INTERSECT/EXCEPT`（Part 4.1）都是这样：语法支持、方言无兜底 | 每个新语法**必须**在 `AstToSqlGenerator` 里加方言分支或特性标志；无兜底能力就编译期报错 |
| **方言配置错误被语法问题掩盖** | PG/DuckDB 缺 window mixin、mysql5.7 多 window mixin、mysql RETURNING 配置错误 —— 症状都是 `unknown-function` 或运行时错误，极易误诊为"EQL 没实现" | S-1 的方言能力矩阵测试；S-4 逐项修正 |
| **`nop-metadata-service` 无验证覆盖** | `SqlSelectFieldExtractor` / `SqlColumnLineageExtractor` / `SqlSourceEntityExtractor` 直接持 `EqlASTParser` 解析用户 SQL（**绕过 `EqlTransformVisitor`**），并已针对 `SELECT *` 写了显式失败裁决（`SqlSelectFieldExtractor.java:121`、`SqlColumnLineageExtractor.java:192`）。任何语法改动都是跨模块行为变更，而 v1 的批次表里**没有它的依赖项** | 每个批次加 `./mvnw test -pl nop-metadata-service -am`；把"直接消费 AST 的模块"列入每批次验证清单 |
| **路径 ⓓ transformer 的缓存键问题** | `ILoadedOrmModel.java:69` 明确警告「astTransformer 不参与缓存键」，调用方必须保证同一 session factory 内使用语义一致的 transformer。若 S-9 把语义改写放进 transformer，等于让编译缓存在不同 transformer 下返回不同结果而缓存键不变 —— **静默返回过期 SQL** | **S-3.5 必须同时处理缓存键**（或把 transformer 提升为编译管线固定环节，使「调用方可选」这一变量消失） |
| **`IN` 列表长度上限无任何处理** | Oracle `IN` 硬上限 **1000**、MSSQL 参数上限 **2100**；`SqlInValuesExpr` 会直接吐出全部字面量。全库 `maxInList`/`inListLimit` 零命中 | S-14；或引入"超阈值自动改写为临时表 JOIN"的策略 |
| **`GREATEST`/`LEAST` 等函数的 NULL 语义无产品决策** | 4.2b 的 3 类语义意味着 EQL 必须显式选定一种，否则同一段 EQL 在不同库返回不同结果 | S-13 产品决策先行，S-6 才可注册 |
| **EQL 语义边界被侵蚀** | 为迁就某库收进私有语法 → EQL 退化成方言门面，`EqlTransformVisitor` 的可移植性收益归零 | 严格执行 Part 5 否决清单；新增语法须在 PR 说明"标准来源 + 覆写点数" |
| **plan-first 区域被当成普通批次** | `StdSqlType`（`nop-kernel/nop-commons`，跨模块公共 API）、`dialect.xdef`（`nop-kernel/nop-xdefs`，生成管线）、新增 `nop-dao` 公共接口 —— 三者都触发 AGENTS.md Protected Areas。⚠️ **`stdNameMap` 加别名会改变所有 ORM 模型的 DDL 类型解析**，属模型层行为变更 | **不止 S-10**：S-2/S-3（xdef + `IDialect` 新方法）、S-3.5/S-12（跨模块公共 API）、S-14（xdef 删属性）同样触发 plan-first。完整对照见 Part 7 的「触发 plan-first 的批次汇总」表 |
| **测试预算缺失** | v1 的 8 个缺陷、10 组建议，**零测试预算**。AGENTS.md「Bug Fix Test Coverage Rule」要求每个修复配回归测试 | 已折算进 Part 7 |
| **sql-lib 语料影响未验证** | `sql-lib` 存的是**原始 SQL 不是 EQL**，所以语法改动对它安全 —— 但 v1 从未验证这一点。已核查：全库**没有任何 `.eql` 文件**；`sql-lib.xml` 的 `<sqls>` 走 `<sql>`/`<eql>` 标签，`<sql>` 不经过 EQL 解析 | 在 S-2/S-3 批次里加"跑一遍现有 sql-lib 语料"的回归 |

---

# Part 9 — 跨库语义陷阱（超出语法本身，但比多数语法项危害更大）

v1 完全未覆盖这一节。它们是"能跑但结果不同"的一类，且**无法靠补语法解决**。

| # | 陷阱 | 各库差异 | 证据/备注 |
|---|------|---------|-----------|
| 9.1 | **Collation 影响 `=` / `ORDER BY` / `GROUP BY` / `DISTINCT` / `LIKE` 的大小写敏感性** | MySQL 默认 `utf8mb4_general_ci` → `WHERE name='ABC'` 匹配 `'abc'`；PG 默认**大小写敏感**；MSSQL 默认 `SQL_Latin1_General_CP1_CI_AS` 大小写不敏感；Oracle 默认二进制 | **危害大于 NULL 排序**：同一段 EQL 在不同库**返回行数都不同**。且 `LIKE` 的大小写敏感性是 collation 属性 —— 这意味着 `ILIKE` 的支持度是 **collation 相关而非 dialect 相关**，单个布尔标志无法建模 |
| 9.2 | **NULL 排序默认值** | 4 类（见 Part 4.2） | v1 把 Oracle 归错类 |
| 9.3 | **Oracle 空字符串即 NULL** | `'' = NULL` | 会破坏所有基于 `coalesce` 的 lowering（包括 `NULLS FIRST` 的 `CASE` 模拟、`FILTER`→`sum(CASE)`） |
| 9.4 | **隐式类型转换** | `where intCol = '1'`：MySQL 数值上下文胜；PG 对 `integer = text` 报错；Oracle 隐式 `TO_NUMBER`；MSSQL 有成文的 conversion hierarchy | EQL 用 `?` 位置参数 + Java 侧 `SqlParamTypeResolver` → `String` 参数遇到数值列是真实风险 |
| 9.5 | **日期范围边界** | MySQL `DATE` = `1000-01-01..9999-12-31`；Oracle `DATE` 含时间且跨 `4713 BC..9999 AD` | `BETWEEN '1900-01-01' AND …` 在 Oracle/PG 正常，**MySQL 失败** |
| 9.6 | **TIMESTAMP vs TIMESTAMPTZ 语义** | PG `timestamp without time zone` 静默丢偏移；MySQL `DATETIME` 无时区而 `TIMESTAMP` 按 `time_zone` 换算；Oracle `WITH TIME ZONE` 归一到 UTC、`WITH LOCAL TIME ZONE` 保留；MSSQL `datetimeoffset` vs `datetime2` | `default.dialect.xml:29-31` 的字面量模板**完全没有时区分量** |
| 9.7 | **标识符长度上限** | MySQL 64（表和列都是）；Oracle 128（12.2+）/**30**（更早）；MSSQL 128；DB2 128；PG 63 | `escapeSQLName` 无长度检查；`getMaxStringSize` 是给数据用的、不是给标识符。Nop 生成的实体/属性名是拼接的（`T` + entity + prop），长属性名会静默超限 |
| 9.8 | **关键字遮蔽 / 保留字冲突（双向）** | 一方面 **224 个词**被硬词法化为 keyword 无法作标识符（`day`/`hour`/`minute`/`second`/`quarter`/`week`/`size`/`key`/`first`/`last`/`precision`/`output`/`option`/`unknown`/`pad`；⚠️ `ROW`/`NTILE` 不是 token）—— 直接解释了 `day()`/`size()` 的解析失败；另一方面 `unreservedWord_`（`BaseRule.g4:74-93`）把 `DATA`/`NAME`/`TYPE`/`VALUE`/`LEVEL`/`SESSION`/`USER`/`NUMBER`/`GROUP`/`ORDER`/`POSITION`/`CROSS` 等放行作标识符，与各方言保留字冲突：**DuckDB 26 个、MySQL/MariaDB 7~8 个、MSSQL 9 个、H2 10 个** | `isReservedKeyword` 只在 `escapeSQLName` 里被调用，**解析期从不检查** → 同一段 EQL 在不同库生成不同转义的标识符。而 `TestDialect.testKeywords`（`TestDialect.java:59-83`）**只测生成侧，从不用 EQL 解析器跑这些关键字** |
| 9.9 | `CHAR` 尾部空格补齐语义 | Oracle/MySQL 的 `CHAR(n)` 空白补齐，比较时忽略尾部空格；`VARCHAR` 不补 | `stdToNativeSqlType` 从同一对 `StdSqlType` 映射 `CHAR`/`VARCHAR`，无 `PAD SPACE`/`NO PAD` 区分 |
| 9.10 | 整除与取模符号 | `5 % -2`：MySQL 是 C 风格截断（`-1`），PG 是 floor 风格（`1`） | 低危但真实 |
| 9.11 | 聚合溢出与返回类型 | `SUM(int)` 在 PG 报错、MySQL 回绕；`AVG(int)` 在 MySQL 返回 `int`、PG 返回 `numeric` | `default.dialect.xml:85` 声明 `avg returnType="DOUBLE"`、`default.dialect.xml:103` 声明 `sum returnType="NUMERIC"` —— 对 MySQL `AVG(int)` 的声明本就不准 |
| 9.12 | 布尔↔整数转换与 `CHECK` 约束 | `WHERE boolCol = 1` 在 MySQL 合法、**在 PG 非法** | EQL 静默接受手写的 `= 1`；G3-2 的 lowering 正确使用模板，但这个推论没被写出 |
| 9.13 | `IN` 列表长度上限 | Oracle 硬上限 1000；MSSQL 参数上限 2100 | 无任何处理（全库 `maxInList` 零命中），`SqlInValuesExpr` 会直接吐 10000 个字面量 |
| 9.14 | `UNION` 的类型归并与列数 | 各库对 unioned 列的类型归并与隐式截断规则不同 | `SqlUnionSelect`（`DMLStatement.g4:82`）绑定期不做任何类型检查 |
| 9.15 | 字符串/Unicode 长度语义 | Oracle `VARCHAR2(n)` 默认按**字节**（除非 `NLS_LENGTH_SEMANTICS=CHAR`）；MSSQL `nvarchar` 按 UTF-16 单元（emoji 代理对算 2） | 中文/emoji 列会在 Oracle/MSSQL 上静默截断 |
| 9.16 | 字符串字面量反斜杠转义 | `escapeSlash` 各库不同（MySQL/DM/TDengine 转义；PG/H2/MSSQL/Oracle 不转义） | MySQL 的 `NO_BACKSLASH_ESCAPES` sql_mode 会再改一次；`WHERE name='O\'Brien'` 语义逐库不同 |
| 9.17 | 递归 CTE 的递归深度 | MySQL `cte_max_recursion_depth` 默认 1000；MSSQL 需 `OPTION(MAXRECURSION n)`（CTE 内不可表达）；PG 无默认限制；Oracle 有 `ORA-32044` 循环检测 | `DMLStatement.g4:70-72` 也不支持 `CYCLE`/`SEARCH DEPTH FIRST`/`column list` |

---

# Part 10 — 影响面（语法改动的 blast radius）

v1 只声明了 `nop-orm-eql` / `nop-dao` / `nop-orm` 三个模块。实际消费方更广：

| 模块 | 耦合方式 | 风险 |
|------|---------|------|
| `nop-orm-eql` | 语法 + AST + 两个生成器 | 直接 |
| `nop-dao` | 16 方言 XML、`TestDialect.testFunctions`（**真实执行每个注册函数**）、`TestDialect.testKeywords`（只测生成） | 函数表改动自带跨模块回归面 |
| `nop-orm` | `GenSqlHelper`（与 EQL 并行的第二条 SQL 生成路径）、`FilterSqlHelper`、`SqlLibManager` | 两条路径的方言行为可能不一致 |
| **`nop-metadata-service`** | `SqlSelectFieldExtractor` / `SqlColumnLineageExtractor` / `SqlSourceEntityExtractor` **直接持 `EqlASTParser` 解析用户 SQL**，绕过 `EqlTransformVisitor`；已针对 `SELECT *` 写了显式失败裁决（`SqlSelectFieldExtractor.java:121`、`SqlColumnLineageExtractor.java:192`） | **v1 完全遗漏**。任何语法改动都是跨模块行为变更 |
| `nop-orm-drivers/nop-orm-tdengine` | `TdSqlHelper` → `io.nop.orm.eql.utils.EqlHelper.appendCol`；`TdEntityPersistDriver` → `io.nop.orm.eql.binder.OrmBinderHelper` | TDengine 有基于 EQL 工具的自有 SQL helper，不在 16 方言审计内 |
| `nop-stream` | 两个私有 upsert 枚举 | 与 S-12 决策相关 |
| `nop-kernel/nop-core` | `io.nop.core.lang.sql`（`ISqlExpr`/`SqlExprList`/`StringSqlExpr`/`CollectSqlOutput`）—— **与 EQL AST 命名冲突的独立模型** | 新增 `Sql*Expr` 节点会加剧阅读混淆；`<sql>` 片段走的是 `io.nop.core.lang.sql`，与 EQL AST 是两套东西 |
| `nop-duckdb` | `TestDuckDbDialect`（290+ 行活方言断言，含 `forUpdate` 必须为空的断言 —— **但 EQL 路径永不调用它，给出虚假信心**） | 修 BUG-3 后该断言语义会变 |
| `nop-db-migration` | `_SqlChange.dbSpecific`（逐方言 DDL 覆盖的正式机制） | 若 `TRUNCATE` 落地，需明确与它的分工 |
| 已核查**干净**的模块 | `nop-cdc`（仅 `CdcConstants.java`）、`nop-dbtool`（`OrmDbDiffer`/`JdbcMetaDiscovery`，纯元数据）、`nop-graphql`、`nop-biz`、`nop-report` | — |
| `nop-nosql` | Redis/Lettuce 专用（`INosqlService`/`INosqlHashOperations`/…），**无查询 DSL**，与 EQL 语义无重叠 | 仅需在文档里说明边界：平台持久化面是 SQL + Redis KV；若将来加文档库查询层（`es.dialect.xml` 已存在但无 ES 查询编译器），它需要自己的归一化方案 |

**EQL 测试语料盘点**（语法改动会波及的）：
`nop-orm/src/test/java/io/nop/orm/eql/` 下 `TestCollectionOperator`、`TestCollectionTableSource`、`TestEqlArithmeticPrecedence`、`TestEqlLogicalPrecedence`、`TestEqlAstParser`、`TestEqlCompiler`、`TestEqlQuery`；`nop-orm-eql/src/test/java/io/nop/orm/eql/` 下 `TestEqlParser`、`parse/TestSqlExprToFilterBean`、`parse/TestSqlExprTransformHelper`、`compile/{TestEqlCompileSql,TestEqlTranslationSemantics,TestCollectionOperatorTransformer,CollectionScopeTest,TestEqlTenantPropJoin}`、`meta/TestEntityMetaBuildValue`；语料 `nop-orm-eql/src/test/resources/io/nop/orm/eql/parse/{bb002,formatted}.sql`。
其中**两个算术/逻辑优先级专项套件**（`TestEqlArithmeticPrecedence` / `TestEqlLogicalPrecedence`）是 S-9/G3 改表达式层时最容易被打破的，v1 未列出。
**全库没有任何 `.eql` 文件**；`sql-lib` 存原始 SQL，所以语法改动对 sql-lib 语料是安全的 —— 这一点 v1 未验证。

---

# Part 11 — 本轮明确延后的项（有理由、无批次）

> 这些项不是遗漏，而是**评估后判定不应在本轮落地**。列出理由，避免下一轮重新把它们当作「新发现」。

| 项 | 出处 | 延后理由 | 重新评估的触发条件 |
|----|------|---------|------------------|
| `ORDER BY … COLLATE` | G2 | MySQL 的 `x COLLATE y` 与 PG/Oracle 的 `ORDER BY x COLLATE y` **子句位置不同**；MSSQL 的 collation 名是库级作用域、不可移植。归一化后并不比各方言原生写法更易读 | 最低支持版本确定，且出现真实的跨库排序规则冲突 |
| G9 的 JSON 标量函数（`json_extract`/`json_value`/`json_unquote`） | G9 | 类型侧已就绪（`StdSqlType.JSON` + `default.dialect.xml:13` 的 `jsonTypeHandler`），函数侧空白确实值得补；但各方库函数名与路径语法（`->` vs `$.a`）分叉，需先单独调研一轮 | 与 G9 的 `JSON_TABLE` 否决决策一并复议 |
| G11 的 keyset / seek 分页抽象 | G11 | `(a,b) > (?,?)` **语法已可解析**，缺的是分页 API 抽象，属于 ORM API 设计而非 EQL 语法问题，应单独立项 | 分页 API 重构立项时 |
| G11 的命名参数（`:name`） | G11 | 属交互式查询 API 范畴；且 `sql-lib.xml` 的 `<arg>` 已是另一套命名绑定，两套模型的调和是独立设计问题 | 与上一项一并 |
| G11 的批量 DML 抽象 / ID 生成策略 | G11 | `supportBatchUpdate`/`supportBatchUpdateCount` 是既有能力（只是 v2 的方言清单漏了），ID 生成走 `OrmEntityIdGenerator` + `sequenceNextVal`，两者都属 DAO 层而非语法层 | DAO 层能力盘点时 |
| Part 9 的全部 17 条语义陷阱（collation 大小写敏感性、隐式类型转换、日期范围边界、标识符长度、聚合溢出、整数取模符号等） | Part 9 | **它们大多无法靠补语法解决**（collation 语义是数据库配置、隐式转换是 SQL 标准行为）。应转为文档 + 配置建议，而非语法变更 | 收到真实的跨库 bug 报告时逐条验证 |
| `TRUNCATE` 进 EQL | Part 5 | 已否决（DDL 归 `nop-db-migration`，其 `_SqlChange.dbSpecific` 是逐方言 DDL 覆盖的正式机制） | 无 —— 这是**永久否决**，仅记录以免重复讨论 |

---

# Open Questions

- [ ] **`BaseRule.g4:246-276` 的标准函数式语法规则（`positionFunction`/`substringFunction`/`extractFunction`/`trimFunction`）当初为什么被注释掉？** 这是 G1 落地前必须先回答的问题（查 git blame / 历史审计），否则可能重蹈覆辙。
- [ ] 项目承诺支持的**最低数据库版本**是什么？这直接决定 Part 4 里所有版本门的兜底策略：`Oracle 12.1`（`LATERAL`/`FETCH FIRST`）、`Oracle 23ai`（`BOOLEAN`/`IS TRUE`/`UPDATE..FROM`/无-`FROM` `SELECT`）、`MySQL 8.0.31`（`INTERSECT`）、`MySQL 8.4.3`（`CUBE`/`GROUPING SETS`）、`PG 15`（`MERGE`）、`MSSQL 2022`（`GREATEST`/`IS DISTINCT FROM`）、`DuckDB 1.4`（`MERGE`）。**若最低版本低于这些门，需评估是双档方言还是编译期报错**
- [ ] **SQLite 是否要成为支持目标？** 目前既无 `sqlite.dialect.xml` 也无 selector，但在 6+ 行上行为不同。
- [ ] `INTERSECT` / `EXCEPT` 的**版本门怎么落**？MySQL 8.0.31+ / MariaDB 10.3+ 已原生（v2 说"完全不支持"是错的），真正的缺口只有 **MySQL ≤8.0.30**；而 `mysql5.7` 与 `mysql` 共用一个方言文件，**一个布尔标志无法区分二者** —— 是否按仓库既有的 `mysql`/`mysql5.7` 拆分先例再拆一个 `mysql8.0.31` 方言？还是改为运行时版本检查？
- [ ] `ORDER BY <序号>`（BUG-9）最终处置：收紧 / 支持 / 先 warning 后收紧？
- [ ] **`GREATEST`/`LEAST` 的 NULL 语义选 N1 还是 N2？**（见 4.2b）这是 S-6 能否包含这两个函数的前置条件。
- [ ] **`stringAgg` 是否接受有损归一**（见 4.2c）？`GROUP_CONCAT`/`LISTAGG`/`STRING_AGG` 在排序语义、`WITHIN GROUP` 可用性、`sep` 是否必填上都分叉。
- [ ] `stdNameMap` 加类型别名是否可接受？它会改变所有 ORM 模型的 DDL 类型解析（属模型层行为变更，plan-first）。
- [ ] 14 个死特性标志的清理范围？三档建议见 Part 2 BUG-13。**注意 `supportNullsFirst` 不可删**（4 个 XML 在用）。
- [ ] 是否需要在 `EqlCompiler` 加"方言能力预检"步骤（编译期一次性报告所有不可移植特性），而不是像现在这样逐个抛错（`EqlTransformVisitor.java:1476-1484` 一次只能报一个）？
- [ ] UPSERT 走 DAO 原语（S-12）是否被接受？如果业务方确实需要 EQL 里的语句形态，`MERGE` 应落在 `<sql>` 片段而非 `sqlStatement` —— 需确认这条边界。
- [ ] `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 目前仅 110 行且已失真 3 处。是否需要补一份**完整 EQL 语法参考 + 方言差异矩阵**？本报告的 Part 1/4 可作为素材。

---

# References

## 源码

| 内容 | 路径 |
|------|------|
| EQL 根语法 | `nop-persistence/nop-orm-eql/model/antlr/Eql.g4` |
| DML 子句 | `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` |
| 表达式规则（含被注释的 SQL-92 标准语法） | `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` |
| 关键字 token | `nop-persistence/nop-orm-eql/model/antlr/Keyword.g4` |
| SQL92 保留字（165 token，86 个零引用 ≈52%；61 个仅供标识符） | `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4` |
| 符号 token（含 3 个死 token） | `nop-persistence/nop-orm-eql/model/antlr/Symbol.g4` |
| 字面量/注释/大小写不敏感 fragment | `nop-persistence/nop-orm-eql/model/antlr/{Literals,Comments,Alphabet}.g4` |
| AST schema（唯一 AST 真源） | `nop-persistence/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava` |
| codegen 配置 | `nop-persistence/nop-orm-eql/model/AntlrParserConfig.json` |
| parse tree → AST visitor（生成，1653 行） | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/_EqlASTBuildVisitor.java` |
| parse tree → AST visitor（手写钩子；小写日期字面量 bug `:177-184`；CAST 白名单 `:261-267`） | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java` |
| AST → EQL 文本生成器（`printSelect` `:431-462`；`visitSqlLimit` 死代码 `:589-598`；`visitSqlSubqueryTableSource` `:635-642`） | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java` |
| AST → 方言 SQL 生成器（分页 `:89-99`；ILIKE `:456-476`；ANY/SOME `:495-506`） | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java` |
| EQL → SQL 编译入口 | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java` |
| 语义绑定/prop-join/特性校验（未知函数 `:1371-1399`；RETURNING 校验 `:1476-1484`；CTE 降级 `:556-561`；`select *` 展开 `:272-279`） | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java` |
| AST 树改写唯一先例（711 行） | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/CollectionOperatorTransformer.java` |
| Dialect 接口 | `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java` |
| Dialect 唯一实现（765 行） | `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java` |
| 特性标志（24 项） | `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/_gen/_DialectFeatures.java` |
| `defaultNullsFirst` / `upsertHandler` / `discoverySqls` | `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/_gen/_DialectModel.java` |
| 分页处理器 | `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/pagination/{LimitOffset,OffsetFetch}PaginationHandler.java` |
| 函数模板机制（marker 越界静默 null） | `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/function/TemplateSQLFunction.java` |
| 原生函数机制（元数范围） | `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/function/NativeSQLFunction.java` |
| 方言模型 schema | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef` |
| 方言 XML（16 个 + 2 mixin） | `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml` |
| 方言自动选择 | `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/DialectManager.java` |
| 标准类型系统（时区类型被注释 `:38,42`；`stdNameMap` `:146,152-161`） | `nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdSqlType.java` |
| `SqlDateTimeType`（大小写 bug 根因） | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/enums/SqlDateTimeType.java` |
| `IUpsertHandler` 删除记录 | `ai-dev/audits/check/nop-dao.md:410-415` |
| nop-stream 两处私有 upsert | `nop-stream/nop-stream-runtime/.../JdbcCheckpointStorage.java:800`、`nop-stream/nop-stream-connector-jdbc/.../JdbcTwoPhaseCommitSink.java:166` |
| EQL AST 外部消费方 | `nop-metadata/nop-metadata-service/.../sqlview/SqlSelectFieldExtractor.java:121`、`.../lineage/SqlColumnLineageExtractor.java:192` |

## 测试

- `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlParser.java` — 语法正向用例
- `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlTranslationSemantics.java` — **多方言对照测试的最佳范式，但当前只有单一手工 dialect**
- `nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialect.java` — `testFunctions:189` 真实执行每个注册函数；`testKeywords:59-83` 只测生成侧
- `nop-duckdb/src/test/java/io/nop/duckdb/TestDuckDbDialect.java` — 活方言契约断言
- 语料：`nop-persistence/nop-orm-eql/src/test/resources/io/nop/orm/eql/parse/{bb002,formatted}.sql`

## 文档

- `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` — 现有对外文档（**含 BUG-12 指出的 3 处失真**）
- `docs-for-ai/02-core-guides/orm-model-design.md`、`model-first-development.md` — 类型系统与 DDL 归属
- `AGENTS.md` — Protected Areas、生成物禁改规则、Bug Fix Test Coverage Rule
- `docs/dev-guide/orm/eql.md` 等历史遗留文档（按 AGENTS.md 规定不在常规开发中读 `docs/`）

## 外部参考

SQL:2008 / SQL:2016 标准中的 `EXTRACT` / `POSITION` / `SUBSTRING` / `TRIM` / `OVER` frame / `FETCH FIRST` / `MERGE` / `GREATEST` 定义；各数据库官方文档（MySQL 8.0/8.4、PostgreSQL 14~17、Oracle 11gR2/12.1/19c/23ai、SQL Server 2019/2022、H2 2.x、DB2 11.5、DM8、DuckDB 1.x、SQLite 3.45+、MariaDB 10.11）—— Part 4 矩阵的逐格验证依据。
