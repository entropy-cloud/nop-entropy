# WI6 Phase 0 裁定 —— 归一化日期时间函数族（dayofweek / weekofyear / dateAdd / dateDiff / dateTrunc + lastDay）

> Status: adjudicated（Phase 0 完成即生效，随各代码 Phase 执行落地）
> Adjudicated: 2026-10-04
> Plan: `ai-dev/plans/28-eql-wi6-datetime-functions.md`
> Sources（一手文档，取证原文存 `_tmp/wi6/docs/`，关键引文逐条摘录于本文档）：
> H2 <https://h2database.com/html/functions.html>（2.x 在线版）；DuckDB <https://duckdb.org/docs/current/sql/functions/date.html> + <https://duckdb.org/docs/current/sql/functions/timestamp.html> + <https://duckdb.org/docs/lts/sql/functions/datepart.html>；MSSQL <https://learn.microsoft.com/sql/t-sql/functions/dateadd-transact-sql> 等（datediff/datetrunc/eomonth/datepart 同目录，ver17）；MySQL <https://dev.mysql.com/doc/refman/8.0/en/date-and-time-functions.html>；Oracle 21c <https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/Format-Models.html> + TRUNC-date + LAST_DAY 同目录；PostgreSQL 15 <https://www.postgresql.org/docs/15/functions-datetime.html>。
> 证据等级：`[实测]` = 本仓库 CI 真库（H2 in-mem）或一次性 scratch 真库执行（取证记录于 §8）；`[文档]` = 一手文档引证；`[未取证]` = dm/tdengine（无一手文档红线）、db2（弱证据纪律）、es（pseudo）——不计入覆盖率。

## 0. 开工门与基础设施复核（回退防护，plan Phase 0 第 1 项）

- **plan 24 状态复核**：`ai-dev/plans/24-eql-wi5-sql92-function-syntax.md` 头部 `Plan Status: completed`（2026-10-04 收口，独立 closure audit 全 PASS）；roadmap Work Item Status WI5 行 = `done`（2026-10-04 复核实测）。**排队门满足，无回退**。
- **矩阵快照 + 门禁**：`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（18 登记 / 15 可加载 / 3 不可加载，与本 plan Current Baseline 一致）。
- **语料基线**：`TestEqlCorpusBaseline` 改动前实跑 1/1 PASS（2026-10-04 10:32，本工作区）。
- **文法零改动前提复核**：`dayofweek`/`weekofyear`/`dateadd`/`datediff`/`datetrunc`/`lastday` 在 `nop-persistence/nop-orm-eql/model/antlr/Keyword.g4`/`nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`/`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` grep **0 命中**（2026-10-04 复测）⇒ 全部走 `IDENTIFIER_` → `sqlRegularFunction` 已可解析，**本 plan 维持零 `.g4`/AST 改动形态**。

### 锚点复核记录（plan 预期必漂，以符号定位为准）

| 锚点 | plan 记载 | 2026-10-04 现位 | 备注 |
|---|---|---|---|
| `EqlTransformVisitor.visitSqlRegularFunction` | 复审实测 `:1475-1502` | **`:1545` 起**（方法签名行） | WI7 Phase 1/3 重生成平移所致；未注册抛 `ERR_EQL_UNKNOWN_FUNCTION`、元数校验逻辑符号定位确认未变 |
| `EqlASTBuildVisitor.SqlRegularFunction_name`（小写化） | 复审实测 `:577` | **`:587`** | 同上平移 |
| `BaseRule.g4 unreservedWord_` | `:72` | `:72` | 未漂移 |
| `BaseRule.g4 extractField__` | `:334` | **`:337`**（规则头） | WI7 重生成平移 |
| `BaseRule.g4 intervalUnit_` | `:401-403` | 规则体含 `WEEK`（`interval 1 week` 可解析），`sqlIntervalExpr` alt 在 `:397` | 平移登记 |
| `h2.dialect.xml` dateadd/datediff/date 模板 | `:161`/`:180`/`:195-196` | `:161`/`:180`/`:195-197` | 未漂移；`date` 模板 = `date_trunc(DAY,{0})` 裸关键字 1 参形态 |

## 1. 总裁定：单位参数采用**字符串字面量**约定；无法消化的方言 cell 响亮不落

归一化调用面（roadmap WI6 节钉死，**不得改变**——见 §5 被拒备选）：

- `dayofweek(d)` → INTEGER，**ISO-8601 计法（1=Monday..7=Sunday）**（§2）
- `weekofyear(d)` → INTEGER，**ISO-8601 周号（1-53）**（plan 起草期已钉死 ISO，维持）
- `dateTrunc(unit, d)` → timestamp/date，unit = 字符串字面量（如 `'month'`）
- `dateAdd(unit, n, d)` → 同类型，unit = 字符串字面量；n 为整数
- `dateDiff(unit, d1, d2)` → BIGINT/INTEGER，**边界跨越（crossed unit boundaries）语义**，unit = 字符串字面量（§4）
- `lastDay(d)` → DATE（§6）

**模板机制约束（裁定依据）**：`<template>` marker 按参数原样渲染 ⇒ EQL 字符串字面量实参渲染为带引号文本。字符串单位约定下：

- 字符串单位函数（pg/duckdb `date_trunc(text,...)`、h2 全族、duckdb `date_diff(part,...)`）可**直接命中**；
- **裸关键字语法位方言**（mysql `DATE_ADD` unit、mssql `DATEADD/DATEDIFF/DATETRUNC` datepart、oracle interval 单位）无法在 fixed template 中去引号 ⇒ 该 cell 不可达。

### 各方言 datepart/unit 语法位的一手取证结论

| 方言 | 语法位 | 一手文档结论 | 引文（摘录） |
|---|---|---|---|
| **mssql** `DATEDIFF` | datepart | **明文拒绝字符串**（[文档]，结论上限即 [文档]，CI 无 MSSQL） | "The datepart value **can't be specified in a variable, nor as a quoted string like 'month'**."（DATEDIFF Arguments 节） |
| **mssql** `DATEADD` | datepart | 未明文接受字符串 + 明文拒绝变量等价物 ⇒ 按证据缺失不落（[文档]） | "DATEADD **doesn't accept user-defined variable equivalents** for the datepart arguments."（DATEADD datepart 参数节；全文无引号字符串接受表述） |
| **mssql** `DATETRUNC` | datepart | 未明文接受字符串 ⇒ 证据缺失不落（[文档]）；另 weekday/tzoffset/nanosecond 不支持 | DATETRUNC Arguments 节无字符串接受表述；"The **weekday, timezoneoffset, and nanosecond** T-SQL dateparts aren't supported for DATETRUNC" |
| **mysql** `DATE_ADD` | unit | **明文 unit 是关键字**（[文档]） | "**unit is a keyword** indicating the units in which the expression should be interpreted."（8.0 手册 Temporal Intervals 节） |
| **mysql** `TIMESTAMPDIFF` | unit | 关键字（可加 `SQL_TSI_` 前缀，仍非字符串）（[文档]） | "unit... should be one of the following values: MICROSECOND, SECOND, ... or with a prefix of SQL_TSI_" |
| **h2** `DATEADD`/`DATEDIFF`/`DATE_TRUNC` | datetimeField | **裸关键字与带引号字符串均接受**（[文档]+[实测]：H2 官方示例为裸关键字 `DATE_TRUNC(DAY, ...)`；仓内既有 `dateadd` testSql 字符串单位 + 本 Phase scratch 实测字符串/裸关键字/小写字符串三形态全通过） | H2 functions.html：`DATE_TRUNC( datetimeField, dateAndTime )`，示例 `DATE_TRUNC(DAY, TIMESTAMP '2010-01-03 10:40:00')`；[实测] 见 §8 |
| **oracle** interval / `TRUNC` fmt | unit/fmt | interval 单位为裸关键字；`TRUNC` 的 fmt 词表与归一化单位词不同（'MM'/'IW'/'Q'…）且 fixed template 无法按运行时单位值翻译 ⇒ dateTrunc/dateAdd 不可达（[文档]） | Format Models：`'IW'`/'MM'/'Q' 等 fmt 词表；DS/YM interval 分属 `NUMTODSINTERVAL`/`NUMTOYMINTERVAL` 两函数（按单位域分叉，无单模板无损表达——plan 取证项④，预期成立） |
| **postgresql/duckdb** | unit/part | **字符串参数**（[文档]+[实测]） | PG：`date_trunc ( text , timestamp )`，示例 `date_trunc('hour', timestamp '...')`；DuckDB：`date_trunc(part, date)` 示例 `date_trunc('month', DATE '2016-12-08 00:00:00')` |

## 2. `dayofweek` 规范计法裁定（Decision）

**裁定：ISO-8601（Monday=1..Sunday=7）。**

裁定依据（plan 要求的三点）：

1. **无兼容包袱**：全仓 `dayofweek` grep 0 命中（含 7 个含 `<eql>` 语料、Java 面），无既有消费依赖任何计法。
2. **确定性到达成本**：ISO 计法在全部拟落方言上均有**确定性（无会话/地域依赖）** lowering——见下表。反观 ODBC（1=Sunday）：h2 唯一候选 `DAY_OF_WEEK` 官方文档明文 **"Returns the day of the week (1-7), locale-specific"**（locale 依赖=会话漂移，红线禁止）；oracle `TO_CHAR(d,'D')` 明文 "This element depends on the NLS territory of the session"（territory 依赖）；mssql `DATEPART(weekday)` 明文受 `SET DATEFIRST` 影响 ⇒ ODBC 计法在 3 个方言上无法确定性到达，被排除。
3. **ORM 侧惯例对齐**：`java.time.DayOfWeek`（MONDAY=1..SUNDAY=7）与 ISO 8601 一致；pg/duckdb 原生 `isodow` 即该值域。

**H2 既有方言内证据佐证**：H2 `DAY_OF_WEEK` 与 `ISO_DAY_OF_WEEK`/`EXTRACT(ISODOW)` 均已被官方标注 deprecated（"use EXTRACT instead of it"）；scratch 实测三者对 `DATE '2026-02-10'`（周二）分别返回 2/2/2——本机 locale 下恰好一致，但 locale 漂移风险仅存在于 `DAY_OF_WEEK`，故弃用之，采用 `ISO_DAY_OF_WEEK`（文档钉死 "1 means Monday"）。

### dayofweek 逐方言 cell（ISO 计法）

| 方言 | lowering | 证据 | 引文/依据 |
|---|---|---|---|
| h2/h2gis | `<template>ISO_DAY_OF_WEEK({0})</template>` | [文档]+[实测] | "Returns the ISO day of the week (1 means Monday)"; scratch 实测 2 |
| postgresql/postgis/duckdb（继承） | `<template>extract(isodow from {0})</template>` | [文档]+[实测-duckdb] | PG："The day of the week as Monday (1) to Sunday (7)... matches the ISO 8601 day of the week numbering"；DuckDB scratch `extract(isodow...)`=2 |
| mysql/mariadb/mysql5.7 | `<template>(1 + WEEKDAY({0}))</template>` | [文档] | "WEEKDAY(date)... (0 = Monday, 1 = Tuesday, … 6 = Sunday)" ⇒ +1 = ISO |
| oracle/oracle-reverse | `<template>TRUNC({0}) - TRUNC({0},'IW') + 1</template>` | [文档] | `'IW'`："Calendar week of year... as defined by the ISO 8601 standard. A calendar week starts on Monday"（territory 无关）；TRUNC(date) 官方示例含 `trunc(d, 'iw')`（"Start of Week"）；DATE-DATE 减法=天数。**弃用 `TO_CHAR 'D'`**："Day of week (1-7). This element depends on the NLS territory of the session" |
| mssql | `<template>((DATEDIFF(DAY, 0, {0}) % 7) + 7) % 7 + 1</template>` | [文档]（CI 无 MSSQL，上限 [文档]） | `DATEDIFF(day,...)` 计日边界跨越、**不属** DATEFIRST 影响面（文档仅 week/weekday datepart 受 SET DATEFIRST 影响）；`0` = 1900-01-01（星期一）。弃用 `DATEPART(weekday)`（DATEFIRST 依赖）。负数取模（<1900 日期）由 `+7)%7` 修正 |
| dm/tdengine/db2/es | **不注册** ⇒ 编译期 `ERR_EQL_UNKNOWN_FUNCTION` | [未取证] | 红线 |

**值域分叉登记（约束 4"不等价"）**：mysql `DAYOFWEEK`=ODBC(1=Sunday)、duckdb `dayofweek`=0=Sunday..6、pg `extract(dow)`=0=Sunday、H2 `DAY_OF_WEEK`=locale 依赖、oracle `'D'`=territory 依赖——归一化 `dayofweek` **钉死 ISO 值域后各库原生同名词≠同值**，owner doc 覆盖表附对照行。

## 3. `weekofyear`（ISO 锚点，裁定维持）逐方言 cell

| 方言 | lowering | 证据 | 引文 |
|---|---|---|---|
| mysql/mariadb/mysql5.7 | `<native name="weekofyear" returnType="INTEGER" argTypes="TIMESTAMP"/>`（原生同名） | [文档] | "WEEKOFYEAR(date)... equivalent to WEEK(date,3)"；mode 3 = "Monday, 1-53, with 4 or more days this year" = ISO 8601 |
| h2/h2gis | `<template>ISO_WEEK({0})</template>` | [文档]+[实测] | "Returns the ISO week (1-53)... uses the ISO definition when first week of year should have at least four days and week is started with Monday"；scratch 实测 7。**`WEEKOFYEAR` 在 H2 不存在**（scratch 实测 SQL 语法错误）；`WEEK(d)` 官方明文 "uses the current system locale"（locale 依赖，弃用） |
| postgresql/postgis/duckdb（继承） | `<template>extract(week from {0})</template>` | [文档]+[实测-duckdb] | PG："The number of the ISO 8601 week-numbering week of the year. By definition, ISO weeks start on Mondays and the first week of a year contains January 4"；DuckDB `weekofyear`/`week`："ISO Week"（scratch=7） |
| oracle/oracle-reverse | `<template>TO_NUMBER(TO_CHAR({0},'IW'))</template>` | [文档] | `'IW'` ISO 8601 周号（1-52/53），territory 无关（镜像既有 `quarter` 模板的 `to_number(to_char(...))` 房法） |
| mssql | `<template>DATEPART(iso_week, {0})</template>` | [文档] | `iso_week` datepart 存在（isowk/isoww 缩写）；DATEFIRST 依赖面明文仅列 week/weekday ⇒ iso_week 确定性 |
| dm/tdengine/db2/es | 不注册 ⇒ 响亮 | [未取证] | 红线 |

**非 ISO 周登记（约束 4"不等价"）**：mysql `WEEK(d)` 默认 mode 受 `default_week_format` 变量影响（会话依赖）；mssql `DATEPART(week)` 受 `SET DATEFIRST` 影响——owner doc 差异行登记"本归一化只落 ISO 周，不落 locale 周"。

## 4. `dateDiff` 语义锚点裁定（Decision）

**裁定：归一化语义 = 边界跨越（crossed unit boundaries），双参数序 `dateDiff(unit, d1, d2)`。**

依据：
- **多数可无损 cell 共有语义**：h2 官方明文 "Returns the **number of crossed unit boundaries** between two date/time values"；duckdb 明文 "The number of **part boundaries** between starttimestamp and endtimestamp"——两个可落方言同语义、同参数序（unit 在前）。
- **mssql `DATEDIFF` 同为边界跨越但 cell 不可落**（datepart 明文拒绝字符串）——语义锚点不因不可落方言的偏好而改变。
- **"完整单位数"形态**（mysql `TIMESTAMPDIFF`）在全部字符串单位方言上不可达 ⇒ 若锚定完整单位数，可落 cell 集 = 空集；锚定边界跨越，可落 cell = {duckdb} + h2/h2gis（既有）。语义中立性权衡：选边界跨越。
- plan 起草期的三候选（边界跨越/完整单位数/双形态）中"双形态"被拒：双形态意味着一个函数名两套语义（按方言漂移）= 违反归一化原则，且需要方言分支模板（fixed template 不可能）。

### dateDiff / dateAdd 逐方言 cell

| 函数 | 方言 | 结论 | 依据 |
|---|---|---|---|
| `datediff` | **duckdb** | **落**：`<native name="datediff" realName="date_diff" returnType="BIGINT" argTypes="ANY,TIMESTAMP,TIMESTAMP"/>` | [文档]+[实测-duckdb]：`date_diff(part, starttimestamp, endtimestamp)` = boundaries；`datediff` 为官方别名（scratch `datediff('month', '2026-01-31','2026-02-02')`=1）；duckdb extends postgresql，postgresql 无 datediff 注册 ⇒ 无冲突 |
| `datediff` | h2/h2gis | **既有注册保持不变**（h2 `:180`，边界跨越明文）——守护用例锁定逐字行为 | [实测]（仓内 testSql + CI） |
| `datediff` | mysql/mariadb/mysql5.7 | **不落**：`DATEDIFF` 仅天且 2 参（元数不符）；`TIMESTAMPDIFF` 语义=完整单位数且 unit 为关键字 | [文档]："DATEDIFF... returns expr1 − expr2 expressed as a value in days"（2 参）；TIMESTAMPDIFF 关键字 unit ⇒ ledger |
| `datediff` | postgresql/postgis | **不落**：`date-date`=天数、`timestamp-timestamp`=interval（均需按 unit 分支，fixed template 不可为） | [文档] ⇒ ledger |
| `datediff` | oracle/oracle-reverse | **不落**：`d1-d2` 为天数分数，非单位边界跨越；无带单位 datediff | [文档] ⇒ ledger |
| `datediff` | mssql | **不落**：语义同锚点但 datepart 明文拒绝字符串（§1 表） | [文档] ⇒ ledger |
| `datediff` | dm/tdengine/db2/es | 不注册 ⇒ 响亮 | [未取证] |
| `dateadd` | h2/h2gis | **既有注册保持不变**（h2 `:161`，字符串单位 testSql）——守护用例锁定 | [实测]（CI） |
| `dateadd` | duckdb | **不落**：唯一 `date_add(value, INTERVAL n unit)` 形态，unit 为裸关键字（无字符串单位重载） | [文档]（scratch 佐证：仅 INTERVAL 形态可执行）⇒ ledger |
| `dateadd` | postgresql/postgis | **不落**：interval 算术 `d + n * interval '1 unit'` 需运行时单位分支，fixed template 不可为；无字符串单位 date_add 函数 | [文档] ⇒ ledger |
| `dateadd` | oracle/oracle-reverse | **不落**：DS/`NUMTODSINTERVAL` 与 YM/`NUMTOYMINTERVAL` 按单位域分两函数，单模板无法无损分叉（plan 取证项④，预期成立）；且 interval 单位为裸关键字 | [文档] ⇒ ledger |
| `dateadd` | mssql | **不落**：datepart 未明文接受字符串（§1 表） | [文档] ⇒ ledger |
| `dateadd` | mysql/mariadb/mysql5.7 | **不落**：`DATE_ADD(d, INTERVAL n unit)` unit 明文关键字 | [文档] ⇒ ledger |
| `dateadd` | dm/tdengine/db2/es | 不注册 ⇒ 响亮 | [未取证] |

> `dateAdd` 的 interval 算术替代写法（`o.createTime + interval 1 day`）的逐方言渲染唯一 owner = **WI7b**（BUG-5 sqlIntervalLiteral 模板）；本 plan 不抢（plan Non-Goals 明文）。ledger 行注明该 successor。

## 5. 被拒备选对照（plan 点名必录）

1. **按单位拆函数名族**（`dateAddDays`/`dateAddMonths`/`dateTruncMonths`… 逐一注册）：**拒绝**。理由：(a) 改变 roadmap WI6 节钉死的归一化调用面 `dateAdd(unit,n,d)`/`dateDiff(unit,d1,d2)`/`dateTrunc(unit,d)`——单位是参数不是函数名组成；(b) 单位 × 函数组合爆炸（3 函数 × ~8 单位 ≈ 24 个新规范名，且各单位可落方言集不同）；(c) 把"该方言支不支持该单位"的分叉从编译期错误转移到用户知识负担。若产品层面要推翻，须先修订 roadmap WI6 节（scope change），本 plan 不得单方采纳。
2. **mysql/tdengine 等裸关键字方言走"去引号"模板预处理**（把字符串实参渲染为裸标识符）：**拒绝**。`TemplateSQLFunction` 渲染器无此能力（marker 原样替换），改造渲染器 = 动 `nop-dao` 机制代码 + 引入注入面，超出"纯方言 XML 注册"形态（Protected Areas 表：注册面纪律）。
3. **mssql `DATEADD` 以 `datepart` 位置放裸关键字常量、只支持单一单位的窄模板**（如 `dateAdd` 只支持 MONTH → `DATEADD(MONTH,{1},{2})`）：**拒绝**。"只对部分单位值正确的模板不得注册"（plan 判据红线；roadmap 排序原则 P1：静默错误 SQL > 响亮失败）——用户传 'day' 时静默产出错误结果。
4. **`dateTrunc` 在 oracle 用 `TRUNC({0}, {0上的单位翻译})`**：技术上不可行——fmt 词表映射（'month'→'MM'）需运行时 CASE 分支模板，理论可写但属"部分正确模板"风险面（归一化单位词表 ∩ oracle fmt 词表 ≠ 全集，如 'week'→'IW' 语义为 ISO 周起点 vs 归一化语义=周一截断——需逐一证明等价），且 oracle 单位串外其他方言对齐成本高 ⇒ **拒绝**，oracle dateTrunc 进 ledger。
5. **dayofweek 落 ODBC 计法**：被拒（§2——h2/oracle 无确定性到达路径）。

## 6. `lastDay(d)` 终局裁定（roadmap EC 点名）：**落地**

**判据核对（plan："若无损 cell 集 < 3 方言则改为显式移出"）**：可落方言集 = {mysql, mariadb, mysql5.7, h2, h2gis, postgresql, postgis, oracle, oracle-reverse, mssql, duckdb} = **11 个注册名覆盖（≥3 满足有余）** ⇒ 落地。

**语义钉死**：`lastDay(d)` = 返回 d 所在月份最后一天的**日期值**（DATE，时间分量归零）。

| 方言 | lowering | 证据 | 引文/依据 |
|---|---|---|---|
| mysql/mariadb/mysql5.7 | `<native name="lastday" realName="LAST_DAY" returnType="DATE" argTypes="TIMESTAMP"/>` | [文档] | "LAST_DAY(date): Takes a date or datetime value and returns the corresponding value for the last day of the month" |
| h2/h2gis | `<native name="lastday" realName="LAST_DAY" returnType="DATE" argTypes="TIMESTAMP">` + `<testSql>LAST_DAY(DATE '2026-02-10')</testSql>` | **[文档]+[实测]**（CI 真库） | "Returns the last day of the month that contains the specified date-time value. This function returns a date."（plan 起草期"待一手取证"项②已结：functions.html 明文存在）；scratch 实测 `=> 2026-02-28` |
| postgresql/postgis | `<template>CAST(date_trunc('month', {0}) + INTERVAL '1 month' - INTERVAL '1 day' AS DATE)</template>` | [文档]+[实测-duckdb 同型] | PG `date_trunc(text,...)` 精确算术（plan 矩阵"精确等价"）；DuckDB scratch 同型表达式 `=> 2026-02-28` |
| oracle/oracle-reverse | `<native name="lastday" realName="LAST_DAY" returnType="DATE" argTypes="TIMESTAMP"/>` | [文档] | "LAST_DAY returns the date of the last day of the month that contains date" |
| mssql | `<template>EOMONTH({0})</template>` | [文档] | "EOMONTH... returns the last day of the month containing a specified date" |
| duckdb | **继承 postgresql 模板**（duckdb extends postgresql；pg 模板形态在 DuckDB scratch 实测全通过）+ 亦可原生 `last_day`——**裁定走继承，零额外注册** | [文档]+[实测-duckdb] | DuckDB `last_day(date)`：scratch `=> 2026-02-28`；pg 模板同型 scratch 亦通过 |
| dm/tdengine/db2/es | 不注册 ⇒ 响亮 | [未取证] | 红线 |

**时区维度裁定（plan 点名 cell 判据）**：postgresql 的 `date_trunc` 对 `timestamptz` 输入**按会话 TimeZone 截断**（时变）——pg cell 的**输入类型域钉死为 DATE/TIMESTAMP（无时区）**；对 timestamptz 输入该 cell 与原生 `LAST_DAY` 族存在时变差异 ⇒ 约束 4"不等价"登记 + owner doc 差异行（原生 LAST_DAY 族无此时变）。h2 `date_trunc` 若干模板同理（h2 走原生 LAST_DAY，不受影响）。

**roadmap ledger 行处置**：`lastDay(d)` 行回写为"**已由 WI6 落地**（2026-10-04，plan 28 Phase 1）"，Successor 列改"无（已落地；未覆盖方言上编译期 `ERR_EQL_UNKNOWN_FUNCTION` 响亮）"。

## 7. `dateTrunc` 逐方言 cell（字符串单位约定）

| 方言 | 结论 | 依据 |
|---|---|---|
| postgresql/postgis | **落**：`<template>date_trunc({0},{1})</template>`（argTypes=VARCHAR,TIMESTAMP） | [文档]：`date_trunc ( text , timestamp )`，示例引号字符串；part 在前与归一化序一致 |
| duckdb | **继承 pg 模板**（duckdb extends postgresql；DuckDB `date_trunc(part, timestamp)` 同序同形） | [文档]+[实测-duckdb]：scratch `date_trunc('month', ...)` 通过 |
| h2/h2gis | **落**：`<template>date_trunc({0},{1})</template>` + testSql | **[实测]**（plan 起草期"待一手取证"项②已结）：H2 `DATE_TRUNC` 文档序 = `(datetimeField, dateAndTime)` 与归一化序一致；scratch 实测**带引号字符串单位被接受**（'DAY'/'day' 均通过），裸关键字与 pg 同序 ⇒ 同一模板三方言共享 |
| mssql | **不落**（`DATETRUNC` SQL Server 2022 起存在但 datepart 无字符串接受证据，§1 表） | [文档] ⇒ ledger（G-1 承诺基线 MSSQL 2022 恰好含 DATETRUNC，但语法位形态使 cell 不可达） |
| mysql/mariadb/mysql5.7 | **不落**（无原生；DATE_FORMAT/MAKEDATE 构造式非无损） | [文档]（plan 矩阵既有裁定）⇒ ledger |
| oracle/oracle-reverse | **不落**（TRUNC fmt 词表映射需运行时分支，§5-4） | [文档] ⇒ ledger |
| dm/tdengine/db2/es | 不注册 ⇒ 响亮 | [未取证] |

**规范单位词表（owner doc 登记）**：`year/quarter/month/week/day/hour/minute/second`（pg/duckdb/h2 三方共同子集；pg 另有 decade/century/millennium/millisecond 等超集——按"规范词表=交集、各方言实际支持范围逐 cell 登记"口径落档）。h2/duckdb 对词表大小写不敏感（[实测]：'day' 与 'DAY' 均通过）。

## 8. H2 / DuckDB 真库取证记录（[实测] 原始输出，scratch 测试跑完即删）

执行环境：本工作区分支 `feat/eql-cross-db-compat`，2026-10-04。输入锚点：`DATE '2026-02-10'` = 周二（ISO 2）、ISO 周 7；2 月最后一天 = 2026-02-28。

H2（nop-dao JdbcTestCase scratch，15 例 14 OK 1 ERR）：
- `DATE_TRUNC(DAY, ts)` OK；`DATE_TRUNC('DAY', ts)` OK；`DATE_TRUNC('day', ts)` OK；`DATE_TRUNC(ts,'day')`（反序）ERR 90008 —— ⇒ 字符串单位接受 + 序=(unit, value)。
- `EXTRACT(ISODOW FROM d)`=2；`ISO_DAY_OF_WEEK(d)`=2；`DAY_OF_WEEK(d)`=2（locale 依赖弃用）。
- `ISO_WEEK(d)`=7；`EXTRACT(WEEK FROM d)`=7；`WEEKOFYEAR(d)` ERR 90022（不存在）。
- `LAST_DAY(DATE '2026-02-10')`=`2026-02-28`。
- 既有 `DATEADD('MONTH',1,d)`/`DATEDIFF('MONTH',...)` 复核通过（字符串单位）。
- `DATE_TRUNC('MONTH'/'WEEK', ...)` 截断语义正确（2026-02-01 / 2026-02-09 周一）。
- `EXTRACT(ISOYEAR ...)`=2026（ISO 年跨年边界佐证）。

DuckDB 嵌入式（nop-duckdb scratch，jdbc:duckdb: in-mem，12 例全 OK）：
- `isodow(d)`=2；`extract(isodow from d)`=2；`weekofyear(d)`=7；`extract(week from d)`=7；`last_day(d)`=2026-02-28；`date_diff('month',d1,d2)`=1；`datediff('month',d1,d2)`=1（别名）；`date_trunc('month', ts)` 通过；pg 型 lastDay 模板同型表达式=2026-02-28；`extract(quarter...)`=1；`dayofweek(d)`=2（0=Sunday 计法下的周二=2）；`date_add(ts, INTERVAL 1 DAY)` 通过（仅 INTERVAL 形态）。

> scratch 测试文件（nop-dao 下 `TestWi6H2Scratch`、nop-duckdb 下 `TestWi6DuckDbScratch`，均为临时 JUnit 类）取证后删除，本节为原始输出留痕。

## 9. WEEK 两项 Deferred 复核结论（plan 24 遗留 → WI6 语境闭环）

1. **`WEEK` 关键字遮蔽**：**维持 watch-only**。`weekofyear` 是整词 `IDENTIFIER_`（无关键字冲突），本 plan 注册后可直接调用；`week(d)` 直接调用无 roadmap 需求输入，且放开 `WEEK` 需文法改动（Non-Goals 禁触）。successor = 后续真实需求出现时登记 roadmap。本 plan 不动 `unreservedWord_`。
2. **`extractField__` 扩 `WEEK`**（`EXTRACT(WEEK FROM d)` 文法层今天拒绝）：**默认不扩**。属文法改动（Non-Goals 禁触面）；`weekofyear(d)` 归一化函数已覆盖 roadmap WI6 点名的 ISO 周需求。若产品需要 `EXTRACT(WEEK FROM d)` 语法形态，走 scope change + 约束 1 路径ⓒ定价。注：H2 `extractField__` 词表（YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|QUARTER）与各方言 EXTRACT 词表（pg/h2 均支持 WEEK/DOW/ISODOW 等）的不一致是**既有现状**，owner doc 差异行登记即可，不顺手扩。

## 10. 注册面与负向错误码口径（Decision，照 plan 差异登记第 6 条 + WI5 TRIM chars 先例）

- **注册只进有证据方言 XML**（h2/mysql/oracle/postgresql/mssql/duckdb），**不进 `default`**——防泄漏 dm/tdengine/db2/es（这些方言对全部缺口函数编译期 `ERR_EQL_UNKNOWN_FUNCTION` 响亮 = 预期行为）。TRIM chars 先例：`oracle.dialect.xml:174` ilike、WI5 裁定 §1.4。字段函数进 default 的先例**不适用**（WI5 裁定 §2 行为登记：那是"EXTRACT 语法 lowering 必需组成"例外）。
- **duckdb 继承 postgresql**：pg 的 `dayofweek`/`weekofyear`/`datetrunc`/`lastday` 模板形态在 DuckDB 上均有效（一手文档 + scratch 实测）⇒ duckdb 只补 pg 没有的 `datediff` native。**postgis 继承 pg 全部**；h2gis 继承 h2 全部；mariadb/mysql5.7 继承 mysql；oracle-reverse 继承 oracle。
- **负向错误码**：复用既有 `ERR_EQL_UNKNOWN_FUNCTION`（未注册方言）+ `ERR_EQL_FUNC_TOO_FEW_ARGS`/`ERR_EQL_FUNC_TOO_MANY_ARGS`（元数），不新起新码（理由同 WI5 裁定 §6：归一化走普通 `SqlRegularFunction` 通道，新码无触发点即死码）。
- **矩阵快照**：每 Phase 落地后 `./mvnw test -pl :nop-dao -Dtest=TestDialectCapabilityMatrix -Dnop.dao.matrix.updateSnapshot=true` 重生成快照 + `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0。

## 11. 落地后预期矩阵增量汇总

| 规范名 | h2 | h2gis | mysql | mysql5.7 | mariadb | postgresql | postgis | oracle | oracle-reverse | mssql | duckdb | dm/tdengine/db2/es |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `lastday` | native+testSql | 继承 | native | 继承 | 继承 | template | 继承 | native | 继承 | template | 继承(pg) | 响亮 |
| `weekofyear` | template | 继承 | native | 继承 | 继承 | template | 继承 | template | 继承 | template | 继承(pg) | 响亮 |
| `dayofweek` | template | 继承 | template | 继承 | 继承 | template | 继承 | template | 继承 | template | 继承(pg) | 响亮 |
| `datetrunc` | template | 继承 | — | — | — | template | 继承 | — | — | — | 继承(pg) | 响亮 |
| `datediff` | 既有 | 既有 | — | — | — | — | — | — | — | — | **native(新增)** | 响亮 |
| `dateadd` | 既有 | 既有 | — | — | — | — | — | — | — | — | — | 响亮 |

（"—" = 不落 cell，编译期响亮 + ledger/差异登记；行内"既有" = h2 `:161`/`:180` 既有注册零改动。）

## 12. 约束 4"一致性 + 不等价"两类用例清单（随代码 Phase 落）

**"一致性"类（同输入同规范值/同文本形态）：**
1. `dayofweek`：同一日期在 h2（ISO_DAY_OF_WEEK 模板）与 mysql 模板表达式（1+WEEKDAY）渲染 SQL 各自合法且值域同为 ISO 1-7——跨方言值等价断言（h2 真库执行 + mysql/oracle/mssql/pg 渲染文本断言）。
2. `weekofyear`：h2 真库执行跨年边界输入（`DATE '2025-12-29'` → ISO 周 1；`DATE '2026-01-01'` → ISO 周 1；`DATE '2026-02-10'` → 7）。
3. `datetrunc`：h2 真库执行 `dateTrunc('month', ts)` 截断语义 + pg/duckdb/h2 同模板形态文本一致。
4. `lastDay`：h2 真库 `lastDay('2026-02-10')` → `2026-02-28`；模板方言（pg/mssql）渲染文本断言。
5. `dateadd`/`datediff`：h2 既有注册守护用例（`dateAdd('MONTH',-1,d)`/`dateDiff('MONTH',d1,d2)` 与基线逐字一致）。

**"不等价"类（差异登记，owner doc 落表）：**
1. `dayofweek` 计法分叉：各库原生同名词值域对照（mysql DAYOFWEEK=ODBC、duckdb dayofweek=0=Sun、pg extract(dow)=0=Sun、H2 DAY_OF_WEEK=locale、oracle 'D'=territory）。
2. `dateDiff` 语义分叉：边界跨越（h2/duckdb/mssql）vs 完整单位数（mysql TIMESTAMPDIFF）vs 仅天（mysql DATEDIFF / pg date-date）——`dateDiff('MONTH', '2026-01-31', '2026-02-02')` 边界跨越=1、完整单位数=0 的判别例登记。
3. `dateTrunc` 单位词表分叉：规范词表=交集（§7），pg 超集词（decade 等）不在规范词表；mssql DATETRUNC 不支持 weekday/tzoffset。
4. `weekofyear`：非 ISO 周各库差异（mysql WEEK() 默认 mode 变量依赖、mssql DATEPART(week) DATEFIRST 依赖）——只落 ISO。
5. `lastDay`：pg 模板对 timestamptz 的会话 TimeZone 截断（时变）vs 原生 LAST_DAY 族无此时变——输入类型域钉死（DATE/TIMESTAMP 无时区）。
6. `second` 等既有字段函数的 EXTRACT(SECOND) 含小数秒差异已在 WI5 登记，本 plan 不重复。

## 13. 与 plan 文本的差异登记（Phase 0 实测修订）

1. **h2 `dateTrunc` cell 可达（plan 起草期预期"可能不可达"）**：plan Current Baseline 写"h2 date_trunc 的仓内证据为裸关键字单位…带引号字符串是否同被接受待 Phase 0 取证（⇒ 该 cell 在字符串单位约定下可能不可达）"。实测：H2 接受带引号字符串单位且参数序与 pg 一致 ⇒ **h2/duckdb/pg 三方言共享同一模板形态**，h2 cell 升 [实测]。这是**利好偏差**（可落 cell +1），plan 结构不变。
2. **duckdb 证据升级**：plan 把 duckdb 列入"无法真库验证的方言"。实测发现 `nop-duckdb` 模块自带 `org.duckdb:duckdb_jdbc` 依赖且测试面已有 `jdbc:duckdb:` 嵌入式连接先例 ⇒ 本 Phase 以一次性 scratch 完成真库取证（§8）。**正式门禁口径不变**（EC ⑥ 的真库执行面仍以 H2 CI 为准，duckdb cell 在 owner doc 仍标 [文档] 级并注明 scratch 佐证）——不因此扩 plan 的 -pl 集。
3. **plan 矩阵中"`dateTrunc` mssql=G-1 承诺基线（SQL Server 2022 起）"的可达性预期被推翻**：mssql `DATEDIFF` 明文拒绝字符串 datepart（§1 引文），`DATEADD`/`DATETRUNC` 无字符串接受证据 ⇒ mssql 三函数 cell 全部不落（原预期"是否接受字符串需取证"，结论=不接受/无证据）。ledger 登记。
